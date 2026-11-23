package gambi.global.os26launcher.data.layout

import android.util.Log
import androidx.room.withTransaction
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.data.layout.db.HiddenAppDao
import gambi.global.os26launcher.data.layout.db.HiddenAppEntity
import gambi.global.os26launcher.data.layout.db.HomePageDao
import gambi.global.os26launcher.data.layout.db.HomePageEntity
import gambi.global.os26launcher.data.layout.db.LauncherDatabase
import gambi.global.os26launcher.data.layout.db.LauncherItemDao
import gambi.global.os26launcher.data.layout.db.LauncherItemEntity
import gambi.global.os26launcher.domain.layout.AppWidgetLimits
import gambi.global.os26launcher.domain.layout.DockInsertPlanner
import gambi.global.os26launcher.domain.layout.EmptyPagePrunePlanner
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.layout.GridInsertPlanner
import gambi.global.os26launcher.domain.layout.GridReflowPlanner
import gambi.global.os26launcher.domain.layout.GridRemovePlanner
import gambi.global.os26launcher.domain.layout.NewAppPlacementPlanner
import gambi.global.os26launcher.domain.layout.WidgetPlacementPlanner
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.PageId
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import gambi.global.os26launcher.domain.today.TodayWidgetsRepository
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.usecase.BuildDefaultLayoutUseCase
import gambi.global.os26launcher.domain.usecase.PickDefaultDockUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nơi duy nhất chứa thuật toán seed / thêm / xóa / dồn ô của bố cục.
 *
 * Mọi hàm ghi đều đi qua [mutex]. Transaction của Room KHÔNG đủ: hai lần [placeNewApps] chạy song song đều
 * có thể đọc cùng một "ô trống đầu tiên" trước khi lần nào ghi xong, kết quả là hai app chồng một ô
 * (Review Focus 5). Mutex xếp các lần reconcile thành hàng; mỗi lần chỉ vài chục hàng nên vẫn nhanh.
 * Các hàm công khai KHÔNG gọi lẫn nhau (Mutex không tái nhập): phần việc dùng chung nằm ở các hàm private
 * `...InTransaction` / [deleteAndCompact], không tự khóa, chỉ gọi từ trong [mutex] và transaction.
 *
 * M7 (kéo-thả, ẩn icon) thêm [moveItem]/[removeItem]/[setHidden], đi qua CÙNG [mutex] để không đụng race
 * với [removePackages]/[catchUp] chạy nền khi người dùng đang kéo tay (Review Focus M7 #2).
 */
@Singleton
class LayoutReconciler @Inject constructor(
    private val db: LauncherDatabase,
    private val itemDao: LauncherItemDao,
    private val pageDao: HomePageDao,
    private val hiddenAppDao: HiddenAppDao,
    private val buildDefaultLayout: BuildDefaultLayoutUseCase,
    private val pickDefaultDock: PickDefaultDockUseCase,
    private val appRepository: AppRepository,
    private val generalConfig: GeneralConfigRepository,
    private val appWidgets: AppWidgetRepository, // G3c: giải phóng id widget app sau khi xóa hàng, quét id mồ côi
    private val appState: AppStateRepository, // TV: cờ `today_seeded`
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val mutex = Mutex()

    /**
     * M8a Task 2: cỡ lưới hiện hành (cài đặt). Gọi NGAY SAU KHI lấy [mutex] và TRƯỚC `db.withTransaction`, để không giữ
     * khóa ghi của DB trong lúc chờ DataStore; mỗi hàm công khai đọc đúng một lần rồi truyền xuống các hàm `...InTransaction`.
     */
    private suspend fun currentGrid(): GridPreset = generalConfig.config.first().gridPreset

    /**
     * Dựng bố cục nếu DB chưa có item nào. Idempotent, chạy trong một transaction.
     *
     * Điều kiện seed là SỐ HÀNG TRONG DB, không phải cờ `first_run_done` của DataStore: cờ đó có thể `true`
     * trong khi DB rỗng (xóa dữ liệu app, seed bị ngắt giữa chừng). LƯU Ý CHO M7: khi người dùng xóa được hết
     * icon, "0 hàng" không còn nghĩa là máy mới — phải đổi sang dấu đã-seed nằm TRONG DB.
     *
     * [apps] rỗng thì không làm gì: `AppRepository.apps` luôn phát danh sách rỗng trước khi có dữ liệu thật,
     * seed bằng danh sách đó sẽ đẻ trang rỗng thừa.
     */
    suspend fun seedIfEmpty(allApps: List<AppItem>) {
        if (allApps.isEmpty()) return
        mutex.withLock {
            val grid = currentGrid()
            // G3c L9: DB không có hàng nào (máy mới, hoặc xóa dữ liệu: đo được trên Live 4 là `pm clear` KHÔNG xóa id của host) thì mọi id host giữ là mồ côi.
            if (itemDao.count() == 0) appWidgets.resetHost()
            db.withTransaction {
                if (itemDao.count() > 0) return@withTransaction

                // M7 Task 11: từ M7 "0 hàng" không còn đồng nghĩa "máy mới" -- người dùng có thể đã ẩn HẾT app. Dựng bố cục
                // chỉ từ app KHÔNG bị ẩn; không còn app nào thì không dựng gì (bỏ ẩn sau đó sẽ tự tạo trang, xem
                // placeNewInTransaction). Máy mới: `hidden_apps` rỗng nên hành vi y như cũ.
                val hidden = notOnHomeRows() // TF: cả app "xóa khỏi MH chính"
                    .mapTo(HashSet()) { ComponentKey(it.packageName, it.className, it.userSerial) }
                val apps = allApps.filter { it.key !in hidden }
                if (apps.isEmpty()) return@withTransaction

                // Dọn trang mồ côi còn sót (DB không có item mà vẫn có trang) để thứ tự `rank` không bị trùng.
                pageDao.deleteByIds(pageDao.getAll().map { it.id })

                val dock = pickDefaultDock(apps, GridMetrics.DOCK_SLOTS)
                itemDao.insertAll(
                    dock.mapIndexed { i, app ->
                        app.toEntity(container = LauncherItemEntity.CONTAINER_DOCK, rank = i)
                    }
                )

                val pages = buildDefaultLayout(apps, dock, grid.columns, grid.rows)
                if (pages.isEmpty()) {
                    // Mọi app đã nằm trong dock: vẫn giữ một trang rỗng để app cài sau có chỗ đặt.
                    pageDao.insert(HomePageEntity(rank = 0))
                }
                pages.forEach { page ->
                    val pageId = pageDao.insert(HomePageEntity(rank = page.index))
                    itemDao.insertAll(
                        page.items.filterIsInstance<PlacedApp>().map { placed ->
                            placed.app.toEntity(
                                container = LauncherItemEntity.CONTAINER_HOME,
                                pageId = pageId,
                                gridX = placed.position.x,
                                gridY = placed.position.y,
                                spanX = placed.position.spanX,
                                spanY = placed.position.spanY
                            )
                        }
                    )
                }
            }
        }
    }

    /**
     * Đặt các app CHƯA có hàng nào (bất kể ở container nào) SAU icon cuối cùng của trang cuối (FX3 C20, chủ dự án chốt
     * 2026-11-11, thay "ô trống đầu tiên" của Q7); hết chỗ thì tạo trang mới, không bao giờ lấp ô trống giữa trang (xem
     * [NewAppPlacementPlanner]). Chỉ đặt vào lưới chính: dock chỉ được chọn lúc seed, thư mục chỉ nhận app người dùng kéo vào.
     * App mới xếp theo thứ tự nhãn giữa chúng ([apps] đã sắp theo nhãn). Idempotent: gọi lại với cùng danh sách không làm gì.
     *
     * Quyết định cho M7: "chưa có hàng" hiện đồng nghĩa "app mới". Khi có ẩn app / xóa icon khỏi màn hình,
     * hàm này phải bỏ qua app trong `hidden_apps` và app người dùng chủ động gỡ khỏi bố cục.
     */
    suspend fun placeNewApps(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        mutex.withLock {
            val grid = currentGrid()
            // FX3 (B4): chọn app mới và hỏi hệ thống ("còn cài không") NGOÀI transaction, rồi mới ghi.
            val fresh = selectFresh(apps)
            if (fresh.isEmpty()) return@withLock
            db.withTransaction { placeNewInTransaction(apps, fresh, grid, allowRepoint = true) }
        }
    }

    /**
     * Xóa hàng của các package đã GỠ THẬT (mọi container, đúng [userSerial]) rồi dồn ô (Q8) trên màn hình chính.
     * Chỉ gọi cho `PackageEvent.Type.REMOVED`: app tạm không khả dụng KHÔNG đi qua đây (giữ nguyên hàng).
     *
     * Dock chỉ bị xóa hàng, KHÔNG dồn và KHÔNG tự lấp (Q10). Dồn ô chỉ áp cho lưới và chỉ khi mọi item 1x1;
     * item nhiều ô (widget, G3) làm phép dồn tuần tự sai nên bỏ qua dồn kèm cảnh báo.
     */
    suspend fun removePackages(packageNames: List<String>, userSerial: Long) {
        if (packageNames.isEmpty()) return
        mutex.withLock {
            val grid = currentGrid()
            val releasedWidgetIds = ArrayList<Int>()
            db.withTransaction {
                val names = packageNames.toHashSet()
                // M7 Task 11: app đã ẩn không còn hàng placement nên phải dọn `hidden_apps` TRƯỚC khi return sớm bên dưới.
                // Không dọn thì cài lại đúng app đó vẫn bị coi là "đã ẩn" (placeNewInTransaction bỏ qua nó).
                names.forEach { hiddenAppDao.deleteByPackage(it, userSerial) }
                val rows = itemDao.getAll()
                // G3c L5: widget của app bị gỡ bị xóa phẳng (không dồn ô, như dấu "−"); id giải phóng sau commit.
                val widgets = rows.filter {
                    it.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET &&
                            it.packageName in names && it.userSerial == userSerial
                }
                deleteWidgetRows(widgets, releasedWidgetIds)
                val doomed = rows.filter {
                    it.itemType == LauncherItemEntity.ITEM_TYPE_APP &&
                            it.packageName in names && it.userSerial == userSerial
                }
                if (doomed.isEmpty()) return@withTransaction

                deleteAppsAndFixFolders(rows.filter { it !in widgets }, doomed, grid)
            }
            appWidgets.release(releasedWidgetIds)
        }
    }

    /**
     * FX5 (C10): hồ sơ [userSerial] (công việc...) đã bị bỏ: xóa MỌI hàng của user đó (không phụ thuộc danh sách package đã biết) và dọn
     * `hidden_apps` của nó, rồi dồn ô và dọn thư mục như [removePackages]. User chính (serial 0) không bao giờ bị xóa theo đường này.
     */
    suspend fun removeUser(userSerial: Long) {
        if (userSerial == 0L) return
        mutex.withLock {
            val grid = currentGrid()
            val releasedWidgetIds = ArrayList<Int>()
            db.withTransaction {
                hiddenAppDao.deleteByUser(userSerial)
                val rows = itemDao.getAll()
                // G3c L7: widget app của hồ sơ bị bỏ xóa phẳng (không đi qua dồn ô quanh widget), id giải phóng sau commit.
                val widgets = rows.filter {
                    it.userSerial == userSerial && it.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET
                }
                deleteWidgetRows(widgets, releasedWidgetIds)
                val doomed = rows.filter {
                    it.userSerial == userSerial && it.itemType != LauncherItemEntity.ITEM_TYPE_WIDGET
                }
                if (doomed.isEmpty()) return@withTransaction
                deleteAppsAndFixFolders(rows.filter { it !in widgets }, doomed, grid)
            }
            appWidgets.release(releasedWidgetIds)
        }
    }

    /**
     * Bắt kịp những thay đổi xảy ra lúc launcher không chạy (M4 Task 4), gọi một lần mỗi lần nạp bố cục.
     *
     * Thứ tự BẮT BUỘC: xóa hàng của package KHÔNG còn được cài, dồn ô, rồi mới đặt app mới. Làm ngược lại thì
     * app mới có thể bị đặt vào ô mà bước dồn sẽ chiếm lại.
     *
     * Vắng mặt khỏi [apps] KHÔNG đồng nghĩa đã gỡ: app bị tắt, tạm khóa hay nằm trên thẻ nhớ đã tháo đều vắng
     * mặt mà vẫn còn cài (Review Focus 2) — giữ nguyên hàng, bật lại thì về đúng ô cũ. Vì vậy chỉ hỏi hệ thống
     * ([AppRepository.isPackageInstalled]) cho những package có hàng mà không có app sống, không hỏi cả 87 hay
     * 344 hàng. Các lệnh hỏi (chặn) nằm NGOÀI transaction để không giữ khóa ghi của DB trong lúc chờ hệ thống;
     * vẫn trong [mutex], mà mọi hàm ghi đều đi qua [mutex] nên danh sách hàng đọc trước đó không đổi giữa chừng.
     *
     * FX3 (A3): hàng MỒ CÔI của package còn cài (app đổi tên activity khởi động khi cập nhật) KHÔNG bị xóa ở đây mà được re-point
     * sang activity mới trong [placeNewInTransaction] (có app sống chưa đặt cùng package là bằng chứng). Q11 không đổi: không có app
     * sống mới thì hàng vẫn nằm yên.
     */
    suspend fun catchUp(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        mutex.withLock {
            val grid = currentGrid()
            val live = apps.mapTo(HashSet()) { it.key }
            val before = itemDao.getAll()

            val candidates = LinkedHashSet<Pair<String, Long>>()
            for (row in before) {
                val key = row.componentKeyOrNull() ?: continue
                if (key !in live) candidates.add(key.packageName to key.userSerial)
            }
            // G3c L6: provider widget có thể không có activity khởi động (không nằm trong [apps]) nên mọi package có hàng widget đều là ứng viên (hỏi hệ thống).
            for (row in before) {
                val pkg = row.packageName
                if (row.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET && pkg != null) candidates.add(pkg to row.userSerial)
            }
            // M7 Task 11: hàng `hidden_apps` của app đã gỡ lúc launcher không chạy cũng là ứng viên (cùng phép hỏi hệ thống).
            val hiddenBefore = notOnHomeRows() // TF: cả cờ offHome của app đã gỡ (cài lại thì về Home như app mới)
            for (h in hiddenBefore) {
                if (ComponentKey(h.packageName, h.className, h.userSerial) !in live) candidates.add(
                    h.packageName to h.userSerial
                )
            }
            val gone = candidates.filterTo(HashSet()) { (pkg, serial) ->
                !appRepository.isPackageInstalled(
                    pkg,
                    serial
                )
            }
            // FX3 (B4): app mới cũng được chọn và lọc "còn cài" ở đây, ngoài transaction, cùng chỗ với phép hỏi `gone`.
            val fresh = selectFresh(apps)

            val releasedWidgetIds = ArrayList<Int>()
            db.withTransaction {
                if (gone.isNotEmpty()) {
                    val widgetsGone = before.filter { row ->
                        val pkg = row.packageName
                        row.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET && pkg != null && (pkg to row.userSerial) in gone
                    }
                    deleteWidgetRows(widgetsGone, releasedWidgetIds)
                    hiddenBefore.filter { (it.packageName to it.userSerial) in gone }
                        .forEach {
                            hiddenAppDao.delete(
                                it.packageName,
                                it.className,
                                it.userSerial
                            )
                            hiddenAppDao.setOffHome(it.packageName, it.className, it.userSerial, false)
                        }
                    val doomed = before.filter { row ->
                        val pkg = row.packageName
                        row.itemType == LauncherItemEntity.ITEM_TYPE_APP && pkg != null &&
                                (pkg to row.userSerial) in gone
                    }
                    if (doomed.isNotEmpty()) {
                        deleteAppsAndFixFolders(before.filter { it !in widgetsGone }, doomed, grid)
                    }
                }
                if (fresh.isNotEmpty()) placeNewInTransaction(apps, fresh, grid, allowRepoint = true)
            }
            appWidgets.release(releasedWidgetIds)
            // G3c L8: lưới an toàn: id host giữ mà không hàng nào (còn trong DB) dùng thì giải phóng (crash giữa chừng, xóa dữ liệu, luồng thêm bị giết).
            appWidgets.sweep(
                itemDao.getAll().mapNotNullTo(HashSet()) {
                    if (it.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET) it.appWidgetId else null
                }
            )
        }
    }

    /** G3c: xóa phẳng các hàng widget app (không dồn ô, không xóa trang trống: cùng luật dấu "−"), ghi lại id để giải phóng SAU commit. PHẢI gọi trong transaction. */
    private suspend fun deleteWidgetRows(widgets: List<LauncherItemEntity>, releasedIds: MutableList<Int>) {
        if (widgets.isEmpty()) return
        itemDao.deleteByIds(widgets.map { it.id })
        widgets.mapNotNullTo(releasedIds) { it.appWidgetId }
    }

    /**
     * M7 — di chuyển 1 item đã có sẵn trong bố cục tới [targetContainer] tại [targetCell].
     *
     * Dock ([LauncherItemEntity.CONTAINER_DOCK]): giới hạn cứng [GridMetrics.DOCK_SLOTS]. Nếu dock đã đủ
     * (không tính chính item đang di chuyển) thì KHÔNG ghi gì, trả `false` — người gọi (UI) tự animate icon
     * bay về vị trí cũ. Ngược lại chèn vào đúng `targetCell.x` (dock chỉ có 1 hàng), các item dock còn lại
     * dồn `rank` để không trùng (Q10: dock được phép còn trống, không tự lấp, nhưng ở đây là người dùng chủ
     * động kéo nên vẫn chèn đúng chỗ họ thả).
     *
     * Lưới chính ([LauncherItemEntity.CONTAINER_HOME], cần [targetPageId]): KHÔNG có khái niệm "đầy không cho
     * thả" (khác thiết kế M7 đời đầu — xem docs/plans/M7-edit-mode.md Ruling Task 1). Lưới chấp nhận có
     * khoảng trống (gaps) giữa các item sau khi kéo-thả/ẩn icon; "trang đầy" CHỈ có nghĩa khi đúng 24/24 ô bị
     * chiếm — khi đó mới cần đẩy 1 item sang trang kế / tạo trang mới. Luôn trả `true`.
     */
    suspend fun moveItem(
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        targetCell: GridPosition
    ): Boolean = mutex.withLock {
        val grid = currentGrid()
        db.withTransaction {
            val rows = itemDao.getAll()
            val moving = rows.firstOrNull { it.id == itemId } ?: return@withTransaction true
            val others = rows.filter { it.id != itemId }
            val fromFolder =
                moving.container.takeIf { it > 0 } // G2a A3b: app đang được kéo RA khỏi thư mục

            val ok = if (targetContainer == LauncherItemEntity.CONTAINER_DOCK) {
                moveToDockInTransaction(moving, others, targetCell.x)
            } else {
                checkNotNull(targetPageId) { "targetPageId là bắt buộc khi targetContainer = CONTAINER_HOME" }
                // G2c: false = widget bị từ chối (chồng widget khác); UI đưa item về chỗ cũ.
                moveToHomeInTransaction(moving, rows, targetPageId, targetCell, grid)
            }
            if (ok && fromFolder != null) {
                dissolveFolderIfNeeded(fromFolder) // D9, cùng transaction
            }
            ok
        }
    }

    /**
     * G2a A1a — gộp [draggedItemId] và [targetItemId] thành một thư mục mới tại ô của đích. Quy tắc từ chối ở
     * [FolderPlanner.canCreate]. Trong [mutex] + một transaction: chèn hàng `FOLDER` (container = lưới chính, cùng trang và ô
     * với đích, tên null = chưa đặt), rồi đổi `container` của đích (rank 0) và nguồn (rank 1) sang id thư mục với
     * `pageId`/`gridX`/`gridY` về 0 (bất biến của `LauncherItemEntity`). KHÔNG đụng ô của icon nào khác: ô cũ của nguồn
     * để lại một khe trống (luật M7). Trả `false` và không ghi gì nếu bị từ chối.
     */
    suspend fun createFolder(draggedItemId: Long, targetItemId: Long): Boolean = mutex.withLock {
        db.withTransaction {
            if (draggedItemId == targetItemId) return@withTransaction false
            val rows = itemDao.getAll()
            val dragged =
                rows.firstOrNull { it.id == draggedItemId } ?: return@withTransaction false
            val target = rows.firstOrNull { it.id == targetItemId } ?: return@withTransaction false
            val fromFolder =
                dragged.container.takeIf { it > 0 } // G2a A3b: nguồn là con của một thư mục khác
            if (!FolderPlanner.canCreate(dragged.toParticipant(), target.toParticipant())) {
                return@withTransaction false
            }
            // Thư mục thế chỗ đích: cùng container (lưới chính hoặc dock), trang, ô và rank (dock đọc theo rank).
            val folderId = itemDao.insertAll(
                listOf(
                    LauncherItemEntity(
                        container = target.container,
                        pageId = target.pageId,
                        gridX = target.gridX,
                        gridY = target.gridY,
                        rank = target.rank,
                        itemType = LauncherItemEntity.ITEM_TYPE_FOLDER
                    )
                )
            ).first()
            itemDao.updateAll(
                listOf(
                    target.copy(container = folderId, pageId = 0, gridX = 0, gridY = 0, rank = 0),
                    dragged.copy(container = folderId, pageId = 0, gridX = 0, gridY = 0, rank = 1)
                )
            )
            if (fromFolder != null) dissolveFolderIfNeeded(fromFolder) // D9 cho thư mục nguồn
            true
        }
    }

    /**
     * G2a A3a — thêm app [itemId] (lưới chính hoặc dock) vào CUỐI thư mục [folderId] (`rank` kế tiếp). Từ chối (false, không ghi
     * gì) nếu thư mục không tồn tại, đã đầy ([FolderPlanner.MAX_ITEMS]) hoặc item không hợp lệ ([FolderPlanner.canAdd]).
     * Ô cũ của app để trống (luật M7), dock bỏ app và căn giữa lại.
     */
    suspend fun addToFolder(itemId: Long, folderId: Long): Boolean = mutex.withLock {
        db.withTransaction {
            if (itemId == folderId) return@withTransaction false
            val rows = itemDao.getAll()
            val item = rows.firstOrNull { it.id == itemId } ?: return@withTransaction false
            val folder = rows.firstOrNull {
                it.id == folderId && it.itemType == LauncherItemEntity.ITEM_TYPE_FOLDER &&
                        (it.container == LauncherItemEntity.CONTAINER_HOME || it.container == LauncherItemEntity.CONTAINER_DOCK)
            } ?: return@withTransaction false
            val children = rows.filter { it.container == folderId }
            val fromFolder =
                item.container.takeIf { it > 0 } // G2a A3b: nguồn là con của thư mục khác
            if (fromFolder == folderId || !FolderPlanner.canAdd(
                    item.toParticipant(),
                    children.size
                )
            ) {
                return@withTransaction false
            }
            val rank = (children.maxOfOrNull { it.rank } ?: -1) + 1
            itemDao.updateAll(
                listOf(
                    item.copy(
                        container = folderId,
                        pageId = 0,
                        gridX = 0,
                        gridY = 0,
                        rank = rank
                    )
                )
            )
            if (fromFolder != null) dissolveFolderIfNeeded(fromFolder) // D9 cho thư mục nguồn
            true
        }
    }

    /**
     * G2a A3a (D9) — gọi TRONG transaction sau khi một thao tác làm giảm số con của [folderId]: còn 1 app thì app về đúng ô của
     * thư mục rồi xóa thư mục, hết app thì xóa thư mục, còn từ 2 thì giữ. Đếm hàng con trong DB (app tạm không khả dụng vẫn là con).
     */
    private suspend fun dissolveFolderIfNeeded(folderId: Long) {
        val rows = itemDao.getAll()
        val folder =
            rows.firstOrNull { it.id == folderId && it.itemType == LauncherItemEntity.ITEM_TYPE_FOLDER }
                ?: return
        val children = rows.filter { it.container == folderId }.sortedBy { it.rank }
        val action = FolderPlanner.dissolve(children.size)
        when (action) {
            FolderPlanner.Dissolve.KEEP -> Unit
            FolderPlanner.Dissolve.DELETE -> itemDao.deleteByIds(listOf(folderId))
            FolderPlanner.Dissolve.REPLACE_WITH_SINGLE -> {
                itemDao.updateAll(
                    listOf(
                        children.first().copy(
                            container = folder.container,
                            pageId = folder.pageId,
                            gridX = folder.gridX,
                            gridY = folder.gridY,
                            rank = if (folder.container == LauncherItemEntity.CONTAINER_DOCK) folder.rank else 0
                        )
                    )
                )
                itemDao.deleteByIds(listOf(folderId))
            }
        }
    }

    /** G2a A2 — đặt tên thư mục [folderId]; tên rỗng lưu null (chưa đặt). Không phải thư mục thì bỏ qua. */
    suspend fun renameFolder(folderId: Long, title: String) {
        mutex.withLock {
            db.withTransaction {
                val row = itemDao.getAll()
                    .firstOrNull { it.id == folderId && it.itemType == LauncherItemEntity.ITEM_TYPE_FOLDER }
                    ?: return@withTransaction
                itemDao.updateAll(
                    listOf(
                        row.copy(
                            title = FolderPlanner.normalizeTitle(title).ifBlank { null })
                    )
                )
            }
        }
    }

    /**
     * FM-3 — đổi thứ tự app trong thư mục [folderId] theo [orderedChildIds] (thứ tự UI nhìn thấy, chỉ app khả dụng). Hàng con không có
     * trong danh sách (app đã gỡ / tạm khóa, UI không vẽ) giữ nguyên chỗ: các chỗ của hàng nhìn thấy được điền lại theo thứ tự mới, rồi
     * đánh lại `rank` 0..n-1. Từ chối (false, không ghi gì) nếu danh sách có id trùng hoặc không phải con của thư mục.
     */
    suspend fun reorderFolder(folderId: Long, orderedChildIds: List<Long>): Boolean = mutex.withLock {
        db.withTransaction {
            val children = itemDao.getAll().filter { it.container == folderId }.sortedBy { it.rank } // cùng thứ tự với LayoutRepositoryImpl
            val wanted = orderedChildIds.toSet()
            if (wanted.size != orderedChildIds.size || !children.map { it.id }.containsAll(wanted)) return@withTransaction false
            val byId = children.associateBy { it.id }
            val queue = ArrayDeque(orderedChildIds)
            val changed = children
                .map { if (it.id in wanted) byId.getValue(queue.removeFirst()) else it }
                .mapIndexedNotNull { rank, row -> if (row.rank != rank) row.copy(rank = rank) else null }
            if (changed.isNotEmpty()) itemDao.updateAll(changed)
            true
        }
    }

    /**
     * M7 — xóa 1 hàng khỏi bố cục theo [itemId] (dùng khi ẩn icon qua dấu "−", hoặc khi [setHidden] cần gỡ
     * placement cũ). KHÔNG dồn ô, KHÔNG xóa trang trống: ở M7 lưới đã chấp nhận có khoảng trống (xem [moveItem]), nên xóa
     * 1 icon chỉ để lại 1 ô trống. (Gỡ app thật đi đường [deleteAndCompact]: dồn trong cùng trang.) Trang trống do thao tác này
     * được dọn sau bằng [pruneEmptyPages] (EP).
     */
    suspend fun removeItem(itemId: Long) {
        mutex.withLock {
            // G3c L4: hàng widget app thì đọc id hệ thống TRƯỚC khi xóa, giải phóng SAU commit (lệnh binder không nằm trong transaction SQLite).
            val appWidgetId = db.withTransaction {
                val id = itemDao.appWidgetIdOf(itemId)
                itemDao.deleteByIds(listOf(itemId))
                id
            }
            if (appWidgetId != null) appWidgets.release(listOf(appWidgetId))
        }
    }

    /**
     * M7 — ẩn/bỏ ẩn 1 app theo [key]. Ẩn: ghi `hidden_apps`, xóa hàng placement hiện có của app đó nếu có và
     * DỒN các icon sau nó cùng trang lên 1 bậc (Task 8 Ruling G2, [GridRemovePlanner]);
     * việc xóa dùng thẳng logic của [removeItem] --
     * (dùng thẳng logic xóa của [removeItem], không gọi lại hàm đó để tránh lấy khóa [mutex] hai lần —
     * [mutex] không tái nhập). Bỏ ẩn: xóa khỏi `hidden_apps`, rồi đặt lại SAU icon cuối cùng (FX3 C20) bằng đúng
     * [placeNewInTransaction] đã dùng cho app mới cài (coi app bỏ ẩn như app "chưa có hàng nào").
     */
    suspend fun setHidden(key: ComponentKey, userSerial: Long, hidden: Boolean) {
        mutex.withLock {
            val grid = currentGrid()
            if (hidden) {
                db.withTransaction {
                    hiddenAppDao.insert(HiddenAppEntity(key.packageName, key.className, userSerial))
                    removePlacementInTransaction(key, userSerial, grid)
                }
            } else {
                hiddenAppDao.delete(key.packageName, key.className, userSerial)
                placeAfterLastIcon(key, grid)
            }
        }
    }

    /**
     * TF — "Xóa khỏi Màn hình chính" (không ẩn): ghi cờ `offHome`, gỡ hàng placement (dồn ô, giải tán thư mục như ẩn). App vẫn hiện ở Library và
     * tìm kiếm; bộ đặt app mới bỏ qua nó nên không "sống lại" trên Home.
     */
    suspend fun removeFromHome(key: ComponentKey, userSerial: Long) {
        mutex.withLock {
            val grid = currentGrid()
            db.withTransaction {
                hiddenAppDao.setOffHome(key.packageName, key.className, userSerial, true)
                removePlacementInTransaction(key, userSerial, grid)
            }
        }
    }

    /** Thân chung của ẩn và [removeFromHome]: xóa hàng app của [key], dồn các icon sau nó cùng trang, giải tán thư mục còn ≤ 1 app. PHẢI gọi trong transaction và dưới [mutex]. */
    private suspend fun removePlacementInTransaction(key: ComponentKey, userSerial: Long, grid: GridPreset) {
        val all = itemDao.getAll()
        val placed = all.firstOrNull {
            it.itemType == LauncherItemEntity.ITEM_TYPE_APP &&
                    it.packageName == key.packageName && it.className == key.className &&
                    it.userSerial == userSerial
        } ?: return
        itemDao.deleteByIds(listOf(placed.id))
        // M7 Task 8 (Ruling G2): ẩn icon trong lưới thì các icon SAU nó cùng trang dồn lên 1 bậc (dock thì
        // không cần: dock đọc theo rank rồi căn giữa). Cùng transaction/mutex với việc xóa.
        if (placed.container == LauncherItemEntity.CONTAINER_HOME) compactPageAfterRemoval(all, placed, grid)
        // G2a A3a (D9): app nằm trong thư mục -> kiểm tra giải tán thư mục đó.
        if (placed.container > 0) dissolveFolderIfNeeded(placed.container)
    }

    /** Thân chung của bỏ ẩn và [addToHome]: đặt app [key] SAU icon cuối (FX3 C20) như app mới. PHẢI gọi dưới [mutex], ngoài transaction. */
    private suspend fun placeAfterLastIcon(key: ComponentKey, grid: GridPreset) {
        val app = appRepository.apps.first { it.isNotEmpty() }.firstOrNull { it.key == key } ?: return
        val fresh = selectFresh(listOf(app))
        // Do người dùng chủ động: không re-point hàng mồ côi của app khác vào đây (D4).
        if (fresh.isNotEmpty()) db.withTransaction {
            placeNewInTransaction(listOf(app), fresh, grid, allowRepoint = false)
        }
    }

    /** TF: khóa của app KHÔNG được tự đặt lên Home: đã ẩn (`hidden`) hoặc đã "xóa khỏi MH chính" (`offHome`). */
    private suspend fun notOnHomeRows(): List<HiddenAppEntity> = (hiddenAppDao.getAll() + hiddenAppDao.getOffHome()).distinct()

    /**
     * Thân của [moveItem] nhánh lưới chính (M7 Task 5 Step 5): tính kế hoạch bằng [GridInsertPlanner] (hàm
     * thuần, cũng là thứ `HomeViewModel` dùng để cập nhật lạc quan trên UI) rồi chỉ việc ghi DB
     * theo kế hoạch — tạo trang mới nếu kế hoạch đòi, mọi item đổi chỗ ghi trong cùng 1 `updateAll`.
     * [rows] là TOÀN BỘ hàng hiện có (kể cả item đang di chuyển, để kế hoạch biết chỗ cũ mà bỏ qua thao tác
     * không đổi gì). PHẢI gọi trong transaction và dưới [mutex].
     */
    private suspend fun moveToHomeInTransaction(
        moving: LauncherItemEntity,
        rows: List<LauncherItemEntity>,
        targetPageId: Long,
        targetCell: GridPosition,
        grid: GridPreset
    ): Boolean {
        val pages = pageDao.getAll().sortedBy { it.rank }.toMutableList()
        // M7 Task 7: PageId.NEW = thả lên trang tạm (ghost page) ở cuối — chỉ lúc này hàng `home_pages` mới được tạo,
        // cùng transaction với việc đặt item (kéo ngược về trang thật thì không có gì được ghi).
        val effectiveTargetId = if (targetPageId == PageId.NEW) {
            val rank = (pages.lastOrNull()?.rank ?: -1) + 1
            val id = pageDao.insert(HomePageEntity(rank = rank))
            pages.add(HomePageEntity(id = id, rank = rank))
            id
        } else {
            targetPageId
        }
        val targetPageIndex = pages.indexOfFirst { it.id == effectiveTargetId }
        if (targetPageIndex < 0) {
            Log.w(TAG, "moveItem: target page $targetPageId not found, ignored")
            return true
        }
        val snapshot = pages.map { page ->
            GridInsertPlanner.PageSlots(
                page.id,
                rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME && it.pageId == page.id }
                    .map { it.toSlot() }
            )
        }
        val plan = GridInsertPlanner.plan(
            snapshot, moving.id, targetPageIndex, targetCell.x, targetCell.y,
            grid.columns, grid.rows, moving.spanX, moving.spanY
        )
        if (plan.rejected) {
            Log.w(
                TAG,
                "moveItem: widget ${moving.id} rejected at page=$targetPageId cell=${targetCell.x},${targetCell.y}"
            )
            return false
        }
        if (plan.placements.isEmpty()) return true

        val pageIds = pages.mapTo(ArrayList()) { it.id }
        var nextRank = (pages.lastOrNull()?.rank ?: -1) + 1
        repeat(plan.newPages) { pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++))) }

        val byId = rows.associateBy { it.id }
        itemDao.updateAll(
            plan.placements.map { pl ->
                byId.getValue(pl.itemId).copy(
                    container = LauncherItemEntity.CONTAINER_HOME,
                    pageId = pageIds[pl.pageIndex],
                    gridX = pl.x,
                    gridY = pl.y
                )
            }
        )
        return true
    }

    /**
     * M8a Task 6 + FX3 (B3) — đổi cỡ lưới: xếp lại toàn bộ icon HOME theo [target] (thứ tự đọc, nén hết khe), tạo/xóa trang cho
     * khớp, VÀ ghi cài đặt cỡ lưới, tất cả trong MỘT lần giữ [mutex]. Trước FX3 hai bước này là hai lời gọi riêng của
     * `ChangeGridPresetUseCase`, mutex được nhả giữa chúng nên một `placeNewApps` chen vào đọc cỡ cũ từ DataStore và đặt
     * app ngoài lưới mới (B3).
     *
     * Hai nơi ghi (DataStore, Room) không chung transaction, nên thứ tự ghi được chọn để ở mọi thời điểm giữa hai bước icon đều
     * nằm trong lưới hợp lệ của CẢ cỡ cũ lẫn cỡ mới (các preset lồng nhau: 4x5 ⊂ 4x6 ⊂ 5x6, xem `GridPreset`):
     * - **Thu nhỏ:** xếp lại DB cho vừa cỡ mới TRƯỚC, rồi mới ghi cài đặt.
     * - **Mở rộng:** ghi cài đặt TRƯỚC (lưới rộng hơn vẫn chứa bố cục cũ), rồi mới xếp lại DB.
     * Bị ngắt giữa chừng thì [ensureFitsGrid] lúc khởi động bắt kịp. Đọc cỡ hiện hành TRONG vùng khóa để so sánh đúng.
     */
    suspend fun changeGridPreset(target: GridPreset) {
        mutex.withLock {
            val current = currentGrid()
            if (target == current) return@withLock
            if (target.capacity < current.capacity) {
                db.withTransaction { reflowInTransaction(target) }
                generalConfig.update { it.copy(gridPreset = target) }
            } else {
                generalConfig.update { it.copy(gridPreset = target) }
                db.withTransaction { reflowInTransaction(target) }
            }
            Log.d(TAG, "changeGrid $current->$target done")
        }
    }

    /**
     * M8a Task 6 — lớp bảo hiểm lúc khởi động: nếu có icon HOME nằm ngoài lưới của cỡ hiện hành (DataStore và DB lệch
     * nhau: khôi phục sao lưu, sửa tay) thì xếp lại cho vừa. Không có icon ngoài lưới thì không làm gì.
     */
    suspend fun ensureFitsGrid() {
        mutex.withLock {
            val grid = currentGrid()
            db.withTransaction {
                val outside = itemDao.getAll().any {
                    // G2c: tính cả span (widget x=1, spanX=4 ở lưới 5 cột không còn vừa lưới 4 cột).
                    it.container == LauncherItemEntity.CONTAINER_HOME &&
                            (it.gridX + it.spanX > grid.columns || it.gridY + it.spanY > grid.rows)
                }
                if (outside) {
                    Log.w(
                        TAG,
                        "ensureFitsGrid: icon outside ${grid.columns}x${grid.rows}, reflowing"
                    )
                    reflowInTransaction(grid)
                }
            }
        }
    }

    /** Thân chung của [changeGridPreset] và [ensureFitsGrid]. PHẢI gọi trong transaction và dưới [mutex]. Không đụng dock/hidden_apps. */
    private suspend fun reflowInTransaction(grid: GridPreset) {
        val pages = pageDao.getAll().sortedBy { it.rank }
        if (pages.isEmpty()) return
        val home = itemDao.getAll().filter { it.container == LauncherItemEntity.CONTAINER_HOME }
        // G2c: item nhiều ô (widget) được xếp cùng icon bởi GridReflowPlanner (đóng gói vị trí đầu tiên còn vừa).
        val snapshot = pages.map { page ->
            GridInsertPlanner.PageSlots(
                page.id,
                home.filter { it.pageId == page.id }.map { it.toSlot() }
            )
        }
        val plan = GridReflowPlanner.plan(snapshot, grid.columns, grid.rows)

        val pageIds = pages.mapTo(ArrayList()) { it.id }
        var nextRank = pages.last().rank + 1
        repeat(plan.totalPages - pages.size) { pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++))) }

        if (plan.placements.isNotEmpty() || plan.spanChanges.isNotEmpty()) {
            val byId = home.associateBy { it.id }
            val updated = LinkedHashMap<Long, LauncherItemEntity>()
            for (pl in plan.placements) {
                updated[pl.itemId] = byId.getValue(pl.itemId)
                    .copy(pageId = pageIds[pl.pageIndex], gridX = pl.x, gridY = pl.y)
            }
            // Span lớn hơn lưới (dữ liệu lỗi / cỡ tương lai) bị kẹp: ghi lại để lần mở sau không reflow lại.
            for (sc in plan.spanChanges) {
                Log.w(TAG, "reflow: item ${sc.itemId} span clamped to ${sc.spanX}x${sc.spanY}")
                val base = updated[sc.itemId] ?: byId.getValue(sc.itemId)
                updated[sc.itemId] = base.copy(spanX = sc.spanX, spanY = sc.spanY)
            }
            itemDao.updateAll(updated.values.toList())
        }

        // Sau khi nén, mọi icon nằm trong [0, totalPages): các trang từ totalPages trở đi đều trống. Luôn giữ >= 1 trang.
        val surplus = pageIds.drop(plan.totalPages)
        if (surplus.isNotEmpty()) pageDao.deleteByIds(surplus)
        Log.d(
            TAG,
            "reflow to ${grid.columns}x${grid.rows}: moved=${plan.placements.size} pages ${pages.size}->${plan.totalPages}"
        )
    }

    /** M7 Task 8 — dồn trang [removed].pageId sau khi hàng [removed] đã bị xóa. [allBefore] = mọi hàng TRƯỚC khi xóa. */
    private suspend fun compactPageAfterRemoval(
        allBefore: List<LauncherItemEntity>,
        removed: LauncherItemEntity,
        grid: GridPreset
    ) {
        val pageRows = allBefore.filter {
            it.container == LauncherItemEntity.CONTAINER_HOME && it.pageId == removed.pageId
        }
        val moves = GridRemovePlanner.plan(
            pageRows.map { it.toSlot() },
            removed.id,
            grid.columns,
            grid.rows
        )
        if (moves.isEmpty()) return
        val byId = pageRows.associateBy { it.id }
        itemDao.updateAll(moves.map { byId.getValue(it.itemId).copy(gridX = it.x, gridY = it.y) })
    }

    /**
     * FX3 (A3) — gom các `className` MỒ CÔI (hàng app và hàng `hidden_apps` có khóa không thuộc [live]) theo (package, user), ghép
     * tuần tự với app mới [fresh] cùng (package, user) (thứ tự: hàng theo `id`, rồi `hidden_apps`; app theo thứ tự [fresh]), và đổi
     * `className` của hàng cũ sang app mới. Trả phần [fresh] còn lại (chưa được ghép) để đặt như app mới.
     *
     * An toàn (Q11): điều kiện là có app SỐNG chưa đặt cùng (package, user), chứng tỏ package đang bật; package bị tắt, khóa hay trên thẻ
     * nhớ tháo không có app sống nên không bao giờ vào đây. Không xóa hàng nào. Ghép chéo giữa hai activity của cùng một app là vô hại;
     * dư mồ côi thì để nguyên, dư app mới thì đặt như app mới. PHẢI gọi trong transaction và dưới [mutex].
     */
    private suspend fun repointOrphans(
        rows: List<LauncherItemEntity>,
        hiddenRows: List<HiddenAppEntity>,
        live: List<AppItem>,
        fresh: List<AppItem>
    ): List<AppItem> {
        val liveKeys = live.mapTo(HashSet()) { it.key }
        val orphans = LinkedHashMap<Pair<String, Long>, LinkedHashSet<String>>()
        for (row in rows.sortedBy { it.id }) {
            val key = row.componentKeyOrNull() ?: continue
            if (key !in liveKeys) {
                orphans.getOrPut(key.packageName to key.userSerial) { LinkedHashSet() }.add(key.className)
            }
        }
        for (h in hiddenRows) {
            if (ComponentKey(h.packageName, h.className, h.userSerial) !in liveKeys) {
                orphans.getOrPut(h.packageName to h.userSerial) { LinkedHashSet() }.add(h.className)
            }
        }
        if (orphans.isEmpty()) return fresh

        val freshByPackage = fresh.groupBy { it.key.packageName to it.key.userSerial }
        val consumed = HashSet<ComponentKey>()
        for ((packageUser, oldClasses) in orphans) {
            val newApps = freshByPackage[packageUser] ?: continue
            oldClasses.zip(newApps).forEach { (oldClass, app) ->
                val (pkg, serial) = packageUser
                val items = itemDao.renameComponent(pkg, oldClass, app.key.className, serial)
                val hiddenCount = hiddenAppDao.renameComponent(pkg, oldClass, app.key.className, serial)
                consumed.add(app.key)
                Log.i(TAG, "repoint $pkg $oldClass -> ${app.key.className} items=$items hidden=$hiddenCount")
            }
        }
        return if (consumed.isEmpty()) fresh else fresh.filter { it.key !in consumed }
    }

    /** Thân của [moveItem] nhánh dock. PHẢI gọi trong transaction và dưới [mutex]. */
    private suspend fun moveToDockInTransaction(
        moving: LauncherItemEntity,
        others: List<LauncherItemEntity>,
        targetSlot: Int
    ): Boolean {
        if (moving.itemType != LauncherItemEntity.ITEM_TYPE_APP && moving.itemType != LauncherItemEntity.ITEM_TYPE_FOLDER) return false // widget không vào dock
        val dockItems =
            others.filter { it.container == LauncherItemEntity.CONTAINER_DOCK }.sortedBy { it.rank }
        // Thuật toán chèn dock nằm ở DockInsertPlanner (hàm thuần) — HomeViewModel dùng đúng hàm đó cho cập nhật lạc quan.
        val order = DockInsertPlanner.plan(
            dockItems.map { it.id },
            moving.id,
            targetSlot,
            GridMetrics.DOCK_SLOTS
        )
            ?: return false

        val byId = (dockItems + moving).associateBy { it.id }
        itemDao.updateAll(
            order.mapIndexed { i, id ->
                byId.getValue(id).copy(
                    container = LauncherItemEntity.CONTAINER_DOCK,
                    pageId = 0,
                    gridX = i,
                    gridY = 0,
                    rank = i
                )
            }
        )
        return true
    }

    /**
     * FX3 (B4) — chọn app MỚI (chưa có hàng nào, không ẩn) trong [apps] rồi bỏ app của package mà hệ thống nói đã gỡ.
     * Lý do: `AppRepository.apps` có thể còn một app vừa bị gỡ (danh sách đọc trước khi gỡ xong) trong khi sự kiện REMOVED đã xóa
     * hàng của nó; không lọc thì app đó bị coi là "mới" và đặt lại thành ô ma. Mỗi (package, user) chỉ hỏi một lần (thường 0-1 app).
     *
     * Chỉ ĐỌC và hỏi hệ thống (chặn), nên gọi dưới [mutex] nhưng NGOÀI `db.withTransaction` (không giữ khóa ghi DB trong lúc chờ hệ
     * thống). Mọi hàm ghi đều đi qua [mutex] nên ảnh chụp hàng đọc ở đây không đổi cho tới khi transaction ghi; dù vậy
     * [placeNewInTransaction] vẫn lọc lại bằng dữ liệu trong transaction.
     */
    private suspend fun selectFresh(apps: List<AppItem>): List<AppItem> {
        val known = itemDao.getAll().mapNotNullTo(HashSet()) { it.componentKeyOrNull() }
        // M7 Task 8: app đang ẩn không có hàng placement nhưng KHÔNG phải app mới -- bỏ qua, nếu không lần quét kế tiếp
        // (danh sách app nạp lại, catchUp lúc mở app) sẽ coi nó là app mới và đặt lại vào bố cục = "sống lại".
        val hidden = notOnHomeRows() // TF: cả app "xóa khỏi MH chính" (offHome) không phải app mới
            .mapTo(HashSet()) { ComponentKey(it.packageName, it.className, it.userSerial) }
        val candidates = apps.filter { it.key !in known && it.key !in hidden }
        if (candidates.isEmpty()) return candidates
        // `isPackageInstalled` chặn (IPC): chạy trên luồng IO dù người gọi (vd setHidden từ ViewModel) đang ở luồng khác.
        val fresh = withContext(io) {
            val installed = HashMap<Pair<String, Long>, Boolean>()
            candidates.filter { app ->
                installed.getOrPut(app.key.packageName to app.key.userSerial) {
                    appRepository.isPackageInstalled(app.key.packageName, app.key.userSerial)
                }
            }
        }
        if (fresh.size != candidates.size) {
            Log.d(TAG, "placeNew: skipped ${candidates.size - fresh.size} not installed")
        }
        return fresh
    }

    /**
     * Thân của [placeNewApps]. PHẢI gọi trong transaction và dưới [mutex]. [live] = mọi app sống hiện có (để biết hàng nào mồ côi,
     * chỉ dùng khi [allowRepoint]); [candidates] = app đã qua [selectFresh].
     *
     * FX3 (A3): với [allowRepoint], app mới cùng (package, user) với một hàng/`hidden_apps` MỒ CÔI (khóa không còn app sống) được hiểu
     * là activity khởi động đã đổi tên khi app cập nhật: hàng cũ được đổi `className` ([repointOrphans]) thay vì chèn hàng mới.
     */
    private suspend fun placeNewInTransaction(
        live: List<AppItem>,
        candidates: List<AppItem>,
        grid: GridPreset,
        allowRepoint: Boolean
    ) {
        val rows = itemDao.getAll()
        val known = rows.mapNotNullTo(HashSet()) { it.componentKeyOrNull() }
        val hiddenRows = notOnHomeRows() // TF: ẩn + offHome (repoint đổi className của cả hai)
        val hidden = hiddenRows.mapTo(HashSet()) { ComponentKey(it.packageName, it.className, it.userSerial) }
        // Lọc lại trong transaction (idempotent): chắc chắn không chèn trùng dù trạng thái đổi sau selectFresh.
        var fresh = candidates.filter { it.key !in known && it.key !in hidden }
        if (fresh.isNotEmpty() && allowRepoint) fresh = repointOrphans(rows, hiddenRows, live, fresh)
        if (fresh.isEmpty()) return
        Log.d(TAG, "placeNew: fresh=${fresh.size} grid=${grid.columns}x${grid.rows}")

        // FX3 (C20): app mới đặt SAU icon cuối cùng của trang cuối (không lấp ô trống giữa trang), hết chỗ thì trang mới.
        // Quy tắc nằm ở NewAppPlacementPlanner (hàm thuần); ở đây chỉ dựng ảnh chụp trang, tạo trang mới và ghi.
        val pages = pageDao.getAll().sortedBy { it.rank }
        val homeRows = rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME }
        val snapshot = pages.map { page ->
            GridInsertPlanner.PageSlots(
                page.id,
                homeRows.filter { it.pageId == page.id }.map { it.toSlot() }
            )
        }
        val spots = NewAppPlacementPlanner.plan(snapshot, fresh.size, grid.columns, grid.rows)

        val pageIds = pages.mapTo(ArrayList()) { it.id }
        var nextRank = (pages.lastOrNull()?.rank ?: -1) + 1
        val lastPageIndex = spots.maxOf { it.pageIndex }
        while (pageIds.size <= lastPageIndex) pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++)))

        itemDao.insertAll(
            fresh.zip(spots).map { (app, spot) ->
                app.toEntity(
                    container = LauncherItemEntity.CONTAINER_HOME,
                    pageId = pageIds[spot.pageIndex],
                    gridX = spot.x,
                    gridY = spot.y
                )
            }
        )
    }

    /**
     * G2a A4 — gỡ app thật ([doomed] có thể gồm cả con của thư mục) rồi áp D9. Thư mục mà MỌI con đều bị gỡ được đưa vào
     * danh sách xóa để ô của nó cũng được dồn như icon thường; thư mục còn lại ít con hơn thì [dissolveFolderIfNeeded]
     * (còn 1 con → con về ô thư mục, còn ≥ 2 → giữ). PHẢI gọi trong transaction và dưới [mutex].
     */
    private suspend fun deleteAppsAndFixFolders(
        rows: List<LauncherItemEntity>,
        doomed: List<LauncherItemEntity>,
        grid: GridPreset
    ) {
        val doomedIds = doomed.mapTo(HashSet()) { it.id }
        val touched = doomed.mapNotNullTo(LinkedHashSet()) { it.container.takeIf { c -> c > 0 } }
        val emptied = rows.filter { f ->
            f.itemType == LauncherItemEntity.ITEM_TYPE_FOLDER && f.id in touched &&
                    rows.filter { it.container == f.id }.all { it.id in doomedIds }
        }
        deleteAndCompact(rows, doomed + emptied, grid)
        val emptiedIds = emptied.mapTo(HashSet()) { it.id }
        touched.filter { it !in emptiedIds }.forEach { dissolveFolderIfNeeded(it) }
    }

    /**
     * Xóa [doomed] (tập con của [rows]) rồi dồn ô trên màn hình chính. Trả về (số hàng đã dời, số trang đã xóa).
     * PHẢI gọi trong transaction và dưới [mutex].
     *
     * Từ 2026-10-30: dồn TRONG TỪNG TRANG như thao tác ẩn của M7 ([GridRemovePlanner]) — icon sau ô bị xóa CÙNG TRANG dịch
     * lên, KHÔNG kéo icon của trang khác sang (bố cục "mỗi app một trang" của người dùng được giữ). Bản cũ (M4) dồn toàn bộ
     * màn hình từ trang đầu làm mất bố cục đã xếp tay.
     * EP (2026-11-17, Q4): mọi trang không còn item (cả trang ở giữa) bị xóa, luôn giữ ≥ 1 trang ([deleteEmptyPages]).
     * Trước EP chỉ xóa các trang CUỐI.
     */
    private suspend fun deleteAndCompact(
        rows: List<LauncherItemEntity>,
        doomed: List<LauncherItemEntity>,
        grid: GridPreset
    ): Pair<Int, Int> {
        itemDao.deleteByIds(doomed.map { it.id })
        val doomedIds = doomed.mapTo(HashSet()) { it.id }
        val pages = pageDao.getAll().sortedBy { it.rank }
        if (pages.isEmpty()) return 0 to 0

        val homeBefore = rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME }
        // G2c: dồn theo từng trang bằng GridRemovePlanner có biết widget (trang không widget cho kết quả cũ); không còn chốt chặn toàn lưới.
        val moved = ArrayList<LauncherItemEntity>()
        homeBefore.filter { it.id in doomedIds }.map { it.pageId }.toSet().forEach { pageId ->
            val pageRows = homeBefore.filter { it.pageId == pageId }
            val byId = pageRows.associateBy { it.id }
            // Xóa lần lượt theo thứ tự đọc, mỗi lần dùng đúng GridRemovePlanner trên vị trí đã cập nhật.
            val slots = pageRows.map { it.toSlot() }.toMutableList()
            pageRows.filter { it.id in doomedIds }.sortedWith(compareBy({ it.gridY }, { it.gridX }))
                .forEach { gone ->
                    val current = slots.first { it.itemId == gone.id }
                    val moves =
                        GridRemovePlanner.plan(slots, current.itemId, grid.columns, grid.rows)
                    for (m in moves) {
                        val i = slots.indexOfFirst { it.itemId == m.itemId }
                        slots[i] = GridInsertPlanner.Slot(m.itemId, m.x, m.y)
                    }
                    slots.removeAll { it.itemId == gone.id }
                }
            for (slot in slots) {
                val original = byId.getValue(slot.itemId)
                if (original.gridX != slot.x || original.gridY != slot.y) {
                    moved.add(original.copy(gridX = slot.x, gridY = slot.y))
                }
            }
        }
        if (moved.isNotEmpty()) itemDao.updateAll(moved)

        // EP Q4: gỡ app làm trống một trang (cả trang ở giữa) thì xóa trang đó luôn, cùng luật với lúc thoát edit mode.
        val removedPages =
            deleteEmptyPages(pages, homeBefore.filter { it.id !in doomedIds })
        return moved.size to removedPages
    }

    /**
     * EP — xóa MỌI trang không còn item màn hình chính (cả trang ở giữa), luôn giữ ≥ 1 trang. Gọi khi thoát edit mode, sau khi ẩn
     * app / xóa widget ngoài edit mode (`PruneEmptyPagesUseCase`). Không dời item nào, `rank` các trang còn lại giữ nguyên.
     */
    suspend fun pruneEmptyPages() {
        mutex.withLock {
            db.withTransaction {
                val home = itemDao.getAll().filter { it.container == LauncherItemEntity.CONTAINER_HOME }
                deleteEmptyPages(pageDao.getAll(), home)
            }
        }
    }

    /**
     * Xóa các trang không có hàng nào trong [remaining] (hàng lưới chính còn lại) theo [EmptyPagePrunePlanner], luôn giữ ≥ 1 trang.
     * Trả số trang đã xóa. PHẢI gọi trong transaction và dưới [mutex].
     */
    private suspend fun deleteEmptyPages(
        pages: List<HomePageEntity>,
        remaining: List<LauncherItemEntity>
    ): Int {
        val doomed = EmptyPagePrunePlanner.plan(
            pages.map { EmptyPagePrunePlanner.Page(it.id, it.rank) },
            remaining.mapTo(HashSet()) { it.pageId }
        )
        if (doomed.isNotEmpty()) pageDao.deleteByIds(doomed)
        return doomed.size
    }

    /**
     * G2c — thêm widget tự vẽ [kind] cỡ [spanX] x [spanY]: đặt vào hình chữ nhật trống đầu tiên (từ trang [preferredPageId], rồi các
     * trang sau, rồi các trang trước), KHÔNG đẩy icon; không có chỗ thì tạo trang mới ở cuối và đặt tại (0,0). Trả id trang đã đặt,
     * hoặc null nếu cỡ không hợp lệ cho [kind] / lớn hơn lưới (không ghi gì).
     */
    suspend fun addWidget(kind: WidgetKind, spanX: Int, spanY: Int, preferredPageId: Long?): Long? =
        addFirstFit(spanX, spanY, preferredPageId, kind.allows(spanX, spanY)) { pageId, x, y ->
            LauncherItemEntity(
                container = LauncherItemEntity.CONTAINER_HOME,
                pageId = pageId,
                gridX = x,
                gridY = y,
                spanX = spanX,
                spanY = spanY,
                itemType = LauncherItemEntity.ITEM_TYPE_BUILTIN_WIDGET,
                className = kind.id
            )
        }

    /**
     * G3c — ghi hàng `itemType = 3` cho widget của app khác đã cấp id và gắn xong ([appWidgetId]): cùng luật [addWidget] (chỗ trống đầu tiên, không đẩy icon,
     * trang mới nếu hết chỗ) với cỡ ≤ trần [AppWidgetLimits]. null (không ghi gì) nếu cỡ không hợp lệ hoặc lớn hơn lưới: người gọi giải phóng [appWidgetId].
     */
    suspend fun addAppWidget(provider: AppWidgetRef, appWidgetId: Int, spanX: Int, spanY: Int, preferredPageId: Long?): Long? =
        addFirstFit(spanX, spanY, preferredPageId, AppWidgetLimits.allows(spanX, spanY)) { pageId, x, y ->
            LauncherItemEntity(
                container = LauncherItemEntity.CONTAINER_HOME,
                pageId = pageId,
                gridX = x,
                gridY = y,
                spanX = spanX,
                spanY = spanY,
                itemType = LauncherItemEntity.ITEM_TYPE_WIDGET,
                packageName = provider.packageName,
                className = provider.className,
                userSerial = provider.userSerial,
                appWidgetId = appWidgetId
            )
        }

    /** Thân chung của [addWidget] và [addAppWidget]: [allowed] = loại/cỡ hợp lệ; [entity] dựng hàng cho trang [pageId] ô ([x], [y]). */
    private suspend fun addFirstFit(
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?,
        allowed: Boolean,
        entity: (pageId: Long, x: Int, y: Int) -> LauncherItemEntity
    ): Long? =
        mutex.withLock {
            val grid = currentGrid()
            db.withTransaction {
                if (!allowed || spanX > grid.columns || spanY > grid.rows) return@withTransaction null
                val rows = itemDao.getAll()
                val pages = pageDao.getAll().sortedBy { it.rank }.toMutableList()
                if (pages.isEmpty()) {
                    val id = pageDao.insert(HomePageEntity(rank = 0))
                    pages.add(HomePageEntity(id = id, rank = 0))
                }
                val snapshot = pages.map { page ->
                    GridInsertPlanner.PageSlots(
                        page.id,
                        rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME && it.pageId == page.id }
                            .map { it.toSlot() }
                    )
                }
                val start = pages.indexOfFirst { it.id == preferredPageId }.coerceAtLeast(0)
                val spot = WidgetPlacementPlanner.firstFit(
                    snapshot,
                    spanX,
                    spanY,
                    start,
                    grid.columns,
                    grid.rows
                )
                val pageId: Long
                val x: Int
                val y: Int
                if (spot != null) {
                    pageId = pages[spot.pageIndex].id
                    x = spot.x
                    y = spot.y
                } else {
                    pageId = pageDao.insert(HomePageEntity(rank = pages.last().rank + 1))
                    x = 0
                    y = 0
                }
                itemDao.insertAll(listOf(entity(pageId, x, y)))
                pageId
            }
        }

    /**
     * G2c Task 13 — tạo widget [kind] cỡ [spanX] x [spanY] đúng chỗ người dùng thả (xem `LayoutRepository.addWidgetAt`). Mọi thứ trong [mutex]
     * và MỘT transaction: tạo trang tạm nếu [targetPageId] là `PageId.NEW`, lập kế hoạch bằng [GridInsertPlanner] (widget chưa có hàng nên
     * dùng id tạm [NEW_WIDGET_PLAN_ID] không trùng hàng nào), tạo trang tràn nếu kế hoạch đòi, chèn hàng widget ở chỗ kế hoạch ghi, dời các
     * icon bị đẩy. Từ chối (chồng widget khác) thì không ghi gì: trang tạm chưa được tạo vì kiểm tra nằm trước.
     */
    suspend fun addWidgetAt(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        targetPageId: Long,
        cell: GridPosition
    ): Boolean = mutex.withLock {
        val grid = currentGrid()
        db.withTransaction {
            if (!kind.allows(
                    spanX,
                    spanY
                ) || spanX > grid.columns || spanY > grid.rows
            ) return@withTransaction false
            val rows = itemDao.getAll()
            val pages = pageDao.getAll().sortedBy { it.rank }.toMutableList()
            val isNewPage = targetPageId == PageId.NEW
            val targetPageIndex =
                if (isNewPage) pages.size else pages.indexOfFirst { it.id == targetPageId }
            if (targetPageIndex < 0) return@withTransaction false
            val snapshot = pages.map { page ->
                GridInsertPlanner.PageSlots(
                    page.id,
                    rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME && it.pageId == page.id }
                        .map { it.toSlot() }
                )
            }.toMutableList()
            // Trang tạm: chưa có hàng `home_pages`; lập kế hoạch trên một trang trống ở cuối, chỉ tạo hàng khi kế hoạch được nhận.
            if (isNewPage) snapshot.add(GridInsertPlanner.PageSlots(PageId.NEW, emptyList()))
            val plan = GridInsertPlanner.plan(
                snapshot,
                NEW_WIDGET_PLAN_ID,
                targetPageIndex,
                cell.x,
                cell.y,
                grid.columns,
                grid.rows,
                spanX,
                spanY
            )
            if (plan.rejected) return@withTransaction false

            val pageIds = pages.mapTo(ArrayList()) { it.id }
            var nextRank = (pages.lastOrNull()?.rank ?: -1) + 1
            if (isNewPage) pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++)))
            repeat(plan.newPages) { pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++))) }

            val own = plan.placements.firstOrNull { it.itemId == NEW_WIDGET_PLAN_ID }
                ?: return@withTransaction false
            itemDao.insertAll(
                listOf(
                    LauncherItemEntity(
                        container = LauncherItemEntity.CONTAINER_HOME,
                        pageId = pageIds[own.pageIndex],
                        gridX = own.x,
                        gridY = own.y,
                        spanX = spanX,
                        spanY = spanY,
                        itemType = LauncherItemEntity.ITEM_TYPE_BUILTIN_WIDGET,
                        className = kind.id
                    )
                )
            )
            val byId = rows.associateBy { it.id }
            val moved = plan.placements.filter { it.itemId != NEW_WIDGET_PLAN_ID }
            if (moved.isNotEmpty()) {
                itemDao.updateAll(moved.map { pl ->
                    byId.getValue(pl.itemId)
                        .copy(pageId = pageIds[pl.pageIndex], gridX = pl.x, gridY = pl.y)
                })
            }
            true
        }
    }

    /**
     * LD Task 1 — tạo hàng app MỚI cho [key] đúng chỗ người dùng thả khi kéo từ App Library. Cùng khuôn [addWidgetAt]: mọi thứ trong [mutex] và MỘT
     * transaction, kế hoạch dùng id tạm [NEW_APP_PLAN_ID].
     * - Lưới ([targetContainer] = HOME): [GridInsertPlanner] neo [cell] của trang [targetPageId] (`PageId.NEW` = trang tạm ở cuối), icon bị phủ được
     *   đẩy, tạo trang tràn nếu kế hoạch đòi.
     * - Dock ([targetContainer] = DOCK): chèn vào slot `cell.x` bằng [DockInsertPlanner] (cùng luật kéo thường), dock đầy thì từ chối.
     * App đã có trên Home vẫn được thêm (bản trùng, chủ dự án chốt 2026-11-23). Cờ `offHome` (TF) của app được bỏ. false = không ghi gì.
     */
    suspend fun addAppAt(key: ComponentKey, targetContainer: Long, targetPageId: Long?, cell: GridPosition): Boolean = mutex.withLock {
        val grid = currentGrid()
        db.withTransaction {
            val rows = itemDao.getAll()
            val placed = when (targetContainer) {
                LauncherItemEntity.CONTAINER_DOCK -> {
                    val dockItems = rows.filter { it.container == LauncherItemEntity.CONTAINER_DOCK }.sortedBy { it.rank }
                    val order = DockInsertPlanner.plan(dockItems.map { it.id }, NEW_APP_PLAN_ID, cell.x, GridMetrics.DOCK_SLOTS)
                        ?: return@withTransaction false
                    val byId = dockItems.associateBy { it.id }
                    val slot = order.indexOf(NEW_APP_PLAN_ID)
                    itemDao.insertAll(listOf(appRow(key, LauncherItemEntity.CONTAINER_DOCK, 0, slot, 0, rank = slot)))
                    itemDao.updateAll(order.mapIndexedNotNull { i, id ->
                        byId[id]?.takeIf { it.rank != i || it.gridX != i }?.copy(gridX = i, gridY = 0, rank = i)
                    })
                    true
                }

                LauncherItemEntity.CONTAINER_HOME -> {
                    val pageId = targetPageId ?: return@withTransaction false
                    val pages = pageDao.getAll().sortedBy { it.rank }
                    val isNewPage = pageId == PageId.NEW
                    val targetPageIndex = if (isNewPage) pages.size else pages.indexOfFirst { it.id == pageId }
                    if (targetPageIndex < 0) return@withTransaction false
                    val snapshot = pages.map { page ->
                        GridInsertPlanner.PageSlots(
                            page.id,
                            rows.filter { it.container == LauncherItemEntity.CONTAINER_HOME && it.pageId == page.id }.map { it.toSlot() }
                        )
                    }.toMutableList()
                    if (isNewPage) snapshot.add(GridInsertPlanner.PageSlots(PageId.NEW, emptyList()))
                    val plan = GridInsertPlanner.plan(snapshot, NEW_APP_PLAN_ID, targetPageIndex, cell.x, cell.y, grid.columns, grid.rows, 1, 1)
                    if (plan.rejected) return@withTransaction false
                    val own = plan.placements.firstOrNull { it.itemId == NEW_APP_PLAN_ID } ?: return@withTransaction false

                    val pageIds = pages.mapTo(ArrayList()) { it.id }
                    var nextRank = (pages.lastOrNull()?.rank ?: -1) + 1
                    if (isNewPage) pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++)))
                    repeat(plan.newPages) { pageIds.add(pageDao.insert(HomePageEntity(rank = nextRank++))) }

                    itemDao.insertAll(listOf(appRow(key, LauncherItemEntity.CONTAINER_HOME, pageIds[own.pageIndex], own.x, own.y)))
                    val byId = rows.associateBy { it.id }
                    val moved = plan.placements.filter { it.itemId != NEW_APP_PLAN_ID }
                    if (moved.isNotEmpty()) {
                        itemDao.updateAll(moved.map { pl -> byId.getValue(pl.itemId).copy(pageId = pageIds[pl.pageIndex], gridX = pl.x, gridY = pl.y) })
                    }
                    true
                }

                else -> false // thư mục: ngoài phạm vi LD
            }
            if (placed) hiddenAppDao.setOffHome(key.packageName, key.className, key.userSerial, false)
            placed
        }
    }

    private fun appRow(key: ComponentKey, container: Long, pageId: Long, x: Int, y: Int, rank: Int = 0) = LauncherItemEntity(
        container = container,
        pageId = pageId,
        gridX = x,
        gridY = y,
        rank = rank,
        itemType = LauncherItemEntity.ITEM_TYPE_APP,
        packageName = key.packageName,
        className = key.className,
        userSerial = key.userSerial
    )

    /**
     * Bản Compose (2026-11-21) — chuyển dữ liệu Today cũ sang kho của REF (`TodayWidgetsStore`, DataStore `today_widgets`). Bản Compose trước
     * lưu widget Today thành hàng `container = TODAY` (-102) trong `launcher_items`; REF lưu danh sách riêng (id trong dải `TodayWidget.ID_BASE`,
     * có widget "App gần đây", không có widget app). Một lần, idempotent:
     * - kho mới chưa từng ghi VÀ (còn hàng Today cũ HOẶC cờ `today_seeded` đã bật): ghi widget tự vẽ của hàng cũ theo `rank` (rộng = spanX >= 4);
     *   cờ bật mà không còn hàng nào (người dùng đã gỡ hết) thì ghi danh sách RỖNG để không hiện lại bộ mặc định;
     * - rồi xóa mọi hàng Today cũ (ghi kho TRƯỚC, xóa SAU: lỗi giữa chừng thì lần sau kho đã có dữ liệu, chỉ còn bước xóa).
     * Mất: widget app trên Today cũ (REF không cho widget app ở Today; id host được giải phóng), địa điểm thời tiết / ảnh riêng của widget cũ
     * (khóa theo id hàng cũ; quét mồ côi dọn sau).
     */
    suspend fun migrateLegacyToday(today: TodayWidgetsRepository) {
        mutex.withLock {
            val rows = itemDao.getAll().filter { it.container == LauncherItemEntity.CONTAINER_TODAY }
            if (rows.isEmpty() && !appState.state.first().todaySeeded) return
            val kinds = rows.sortedBy { it.rank }.mapNotNull { e ->
                if (e.itemType != LauncherItemEntity.ITEM_TYPE_BUILTIN_WIDGET) return@mapNotNull null
                val kind = WidgetKind.fromId(e.className)?.takeIf { it.allows(e.spanX, e.spanY) } ?: return@mapNotNull null
                kind.id to (e.spanX >= 4)
            }
            val imported = today.importLegacy(kinds)
            if (rows.isEmpty()) return
            Log.i(TAG, "migrateLegacyToday rows=${rows.size} imported=$imported kinds=${kinds.size}")
            db.withTransaction { itemDao.deleteByIds(rows.map { it.id }) }
            appWidgets.release(rows.mapNotNull { if (it.itemType == LauncherItemEntity.ITEM_TYPE_WIDGET) it.appWidgetId else null })
        }
    }

    private fun LauncherItemEntity.toSlot() = GridInsertPlanner.Slot(id, gridX, gridY, spanX, spanY)

    private fun LauncherItemEntity.toParticipant() = FolderPlanner.Participant(
        container = container,
        isApp = itemType == LauncherItemEntity.ITEM_TYPE_APP,
        spanX = spanX,
        spanY = spanY
    )

    private fun LauncherItemEntity.componentKeyOrNull(): ComponentKey? {
        if (itemType != LauncherItemEntity.ITEM_TYPE_APP) return null
        val pkg = packageName ?: return null
        val cls = className ?: return null
        return ComponentKey(pkg, cls, userSerial)
    }

    private fun AppItem.toEntity(
        container: Long,
        pageId: Long = 0,
        gridX: Int = 0,
        gridY: Int = 0,
        spanX: Int = 1,
        spanY: Int = 1,
        rank: Int = 0
    ) = LauncherItemEntity(
        container = container,
        pageId = pageId,
        gridX = gridX,
        gridY = gridY,
        spanX = spanX,
        spanY = spanY,
        rank = rank,
        itemType = LauncherItemEntity.ITEM_TYPE_APP,
        packageName = key.packageName,
        className = key.className,
        userSerial = key.userSerial
    )

    private companion object {
        const val TAG = "LayoutReconciler"

        /** G2c Task 13: id tạm của widget chưa có hàng khi lập kế hoạch ([GridInsertPlanner]); id hàng thật luôn > 0. */
        const val NEW_WIDGET_PLAN_ID = Long.MIN_VALUE

        /** LD Task 1: id tạm của app kéo từ Library (chưa có hàng) khi lập kế hoạch; khác [NEW_WIDGET_PLAN_ID], id hàng thật luôn > 0. */
        const val NEW_APP_PLAN_ID = Long.MIN_VALUE + 1
    }
}
