package gambi.global.os26launcher.data.layout

import android.util.Log
import androidx.room.withTransaction
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.data.layout.db.HiddenAppDao
import gambi.global.os26launcher.data.layout.db.HomePageDao
import gambi.global.os26launcher.data.layout.db.HomePageEntity
import gambi.global.os26launcher.data.layout.db.LauncherDatabase
import gambi.global.os26launcher.data.layout.db.LauncherItemDao
import gambi.global.os26launcher.data.layout.db.LauncherItemEntity
import gambi.global.os26launcher.domain.layout.AppWidgetLimits
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppWidgetRef
import gambi.global.os26launcher.domain.model.PlacedAppWidget
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PackageEvent
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.PlacedItem
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.PlacedWidgetBase
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LayoutRepositoryImpl @Inject constructor(
    private val appRepository: AppRepository,
    private val db: LauncherDatabase,
    private val itemDao: LauncherItemDao,
    private val pageDao: HomePageDao,
    private val hiddenAppDao: HiddenAppDao,
    private val reconciler: LayoutReconciler,
    private val todayWidgets: gambi.global.os26launcher.domain.today.TodayWidgetsRepository, // TV-3: đích của migrateLegacyToday
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope
) : LayoutRepository {

    private class Layout(val pages: List<HomePage>, val dock: List<DockApp>)

    // Seed và ghép nằm ở đây (data), không ở ViewModel: ui không được import data.
    // `.filter { isNotEmpty() }`: AppRepository.apps luôn phát danh sách rỗng trước giá trị thật; bỏ nó để
    // không (a) seed bằng danh sách rỗng, (b) nháy trạng thái "trống" khi mọi item bị lọc vì chưa có app sống.
    // shareIn không có giá trị khởi tạo → lần phát đầu của pages/dock đã là dữ liệu thật.
    //
    // FX3 (B1): lỗi Room (vd SQLiteFullException) lọt ra khỏi flow này sẽ làm sập process vì ApplicationScope không có handler.
    // - `ensureFitsGrid`, `catchUp`, `placeNewApps` được bọc ([runReconcile]): lỗi thì ghi log và vẫn phát bố cục đã assemble.
    // - `seedIfEmpty` KHÔNG bọc: nếu seed lỗi mà vẫn chạy `catchUp`/`placeNewApps` thì mọi app bị đặt vào DB rỗng theo thứ tự nhãn,
    //   dock trống, và seed không chạy lại nữa (`count() > 0`). Cho lỗi đi lên [retryWithBackoff] bên dưới: cả flow chạy lại từ seed.
    private val layout: SharedFlow<Layout> = flow {
        val firstApps = appRepository.apps.first { it.isNotEmpty() }
        reconciler.seedIfEmpty(firstApps)
        // M8a Task 6: lớp bảo hiểm cho ca DataStore và DB lệch cỡ lưới; trước catchUp để dồn/đặt ô theo cỡ đúng.
        runReconcile("ensureFitsGrid") { reconciler.ensureFitsGrid() }
        // Bắt kịp thay đổi lúc launcher không chạy (gỡ app, cài app). Tuần tự SAU seed, không song song (M4 Task 4).
        runReconcile("catchUp") { reconciler.catchUp(firstApps) }
        // TV-3 (bản Compose 2026-11-21): widget Today nằm ở kho riêng của REF (TodayWidgetsStore); hàng Room `container = TODAY` của bản cũ
        // chuyển sang đó một lần rồi xóa. Lỗi chỉ ghi log: hàng cũ không lên UI (bị bỏ qua khi ghép), lần khởi động sau làm lại.
        runReconcile("migrateLegacyToday") { reconciler.migrateLegacyToday(todayWidgets) }
        emitAll(
            combine(
                // FX3 (B5/C17): MỘT nguồn cho cả hai bảng. Trước đây combine(pageDao.observeAll(), itemDao.observeAll(), ...)
                // truy vấn lại hai bảng độc lập nên một transaction chạm cả hai phát (trang mới, item cũ) hoặc ngược lại
                // trước trạng thái đúng, và mỗi lần ghi assemble hai lượt.
                dbSnapshots(),
                // Mỗi lần danh sách app đổi: app chưa có hàng nào được đặt vào ô trống đầu tiên (idempotent).
                // Việc này cũng bắt kịp app cài lúc launcher không chạy. Chạy trước khi combine nhận danh sách.
                appRepository.apps.filter { it.isNotEmpty() }
                    .onEach { runReconcile("placeNewApps") { reconciler.placeNewApps(it) } }
            ) { snapshot, apps -> assemble(snapshot.pages, snapshot.items, apps) }
        )
    }
        .flowOn(io)
        // Lưới an toàn cuối: lỗi thì chạy lại flow (không phát gì trong lúc chờ), process không sập (handler của ApplicationScope
        // thuộc FX6). KHÔNG dùng `.catch` cuối: HomeViewModel thu `pages`/`dock` suốt đời process nên `WhileSubscribed` không bao giờ
        // khởi động lại upstream, flow chết là Home đứng hình vĩnh viễn.
        .retryWithBackoff("layout flow")
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Chạy lại flow sau lỗi (trừ hủy coroutine) với độ trễ tăng dần 500 ms, 1 s, 2 s, 4 s rồi giữ 5 s; mỗi lần thử lại có log.
     */
    private fun <T> Flow<T>.retryWithBackoff(name: String): Flow<T> = retryWhen { cause, attempt ->
        if (cause is CancellationException) {
            false
        } else {
            Log.e(TAG, "$name failed, retry #${attempt + 1}", cause)
            delay(minOf(RETRY_BASE_MS shl attempt.toInt().coerceAtMost(4), RETRY_MAX_MS))
            true
        }
    }

    /** Chạy một bước reconcile; lỗi (trừ hủy coroutine) chỉ ghi log để flow bố cục vẫn tiếp tục. */
    private suspend fun runReconcile(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$name failed", e)
        }
    }

    /** Ảnh chụp nhất quán của hai bảng, đọc trong MỘT transaction. */
    private class DbSnapshot(val pages: List<HomePageEntity>, val items: List<LauncherItemEntity>)

    /**
     * FX3 (B5/C17): một flow vô hiệu hóa cho CẢ `launcher_items` và `home_pages` (Room gom tên các bảng đổi trong cùng một
     * lần làm mới vào một lần phát), mỗi lần phát đọc cả hai bảng trong một transaction. `emitInitialState = true` (mặc định)
     * phát một lần ngay khi thu.
     *
     * Thứ tự item giữ đúng như `itemDao.observeAll()` cũ (`ORDER BY pageId, gridY, gridX`) vì `assemble` giữ thứ tự item
     * của DB khi dựng từng trang. Lỗi đọc: [retryWithBackoff] dựng lại flow (phát lại trạng thái đầu) thay vì chờ lần ghi sau, để
     * lần đọc đầu hỏng không làm Home đứng ở trạng thái đang nạp.
     */
    private fun dbSnapshots(): Flow<DbSnapshot> =
        db.invalidationTracker.createFlow("launcher_items", "home_pages")
            .map {
                db.withTransaction {
                    DbSnapshot(
                        pageDao.getAll(),
                        itemDao.getAll().sortedWith(compareBy<LauncherItemEntity>({ it.pageId }, { it.gridY }, { it.gridX }))
                    )
                }
            }
            .retryWithBackoff("read snapshot")

    init {
        // Gỡ THẬT (onPackageRemoved) → xóa hàng và dồn ô. Thu trên ApplicationScope để không phụ thuộc UI còn
        // thu `layout` hay không. Các loại sự kiện khác (tạm không khả dụng, bật/tắt, cập nhật) KHÔNG đụng DB.
        // Bắt lỗi từng sự kiện: scope không có handler, một ngoại lệ lọt ra sẽ làm sập process.
        scope.launch(io) {
            appRepository.packageEvents
                .filter { it.type == PackageEvent.Type.REMOVED || it.type == PackageEvent.Type.USER_REMOVED }
                .collect { event ->
                    try {
                        // FX5 (C10): hồ sơ bị bỏ thì xóa theo user, không theo danh sách package
                        if (event.type == PackageEvent.Type.USER_REMOVED) reconciler.removeUser(event.userSerial)
                        else reconciler.removePackages(event.packageNames, event.userSerial)
                    } catch (e: Exception) {
                        Log.e(TAG, "removePackages failed for ${event.packageNames} user=${event.userSerial}", e)
                    }
                }
        }
    }

    override val pages: Flow<List<HomePage>> = layout.map { it.pages }.distinctUntilChanged()

    override val dock: Flow<List<DockApp>> = layout.map { it.dock }.distinctUntilChanged()

    // M7 — app bị ẩn còn khả dụng. App ẩn nhưng đã gỡ cài thật khỏi máy bị lọc ra (không resolve được qua
    // AppRepository.apps), chỉ cảnh báo log, không coi là lỗi: hàng hidden_apps mồ côi này vô hại.
    override val hiddenApps: Flow<List<AppItem>> =
        combine(hiddenAppDao.observeAll(), appRepository.apps) { hidden, apps ->
            val byKey = apps.associateBy { it.key }
            hidden.mapNotNull { e ->
                byKey[ComponentKey(e.packageName, e.className, e.userSerial)] ?: run {
                    Log.w(
                        TAG,
                        "hidden app not resolvable, skipped: ${e.packageName}/${e.className}"
                    )
                    null
                }
            }
        }.flowOn(io)

    override suspend fun moveItem(
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        targetCell: GridPosition
    ): Boolean = reconciler.moveItem(itemId, targetContainer, targetPageId, targetCell)

    override suspend fun addWidget(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?
    ): Long? =
        reconciler.addWidget(kind, spanX, spanY, preferredPageId)

    override suspend fun addAppWidget(
        provider: gambi.global.os26launcher.domain.model.AppWidgetRef,
        appWidgetId: Int,
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?
    ): Long? = reconciler.addAppWidget(provider, appWidgetId, spanX, spanY, preferredPageId)

    override suspend fun addWidgetAt(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        targetPageId: Long,
        cell: GridPosition
    ): Boolean =
        reconciler.addWidgetAt(kind, spanX, spanY, targetPageId, cell)

    override suspend fun removeItem(itemId: Long) = reconciler.removeItem(itemId)

    override suspend fun pruneEmptyPages() = reconciler.pruneEmptyPages()

    override suspend fun changeGridPreset(target: GridPreset) = reconciler.changeGridPreset(target)

    override suspend fun setHidden(key: ComponentKey, userSerial: Long, hidden: Boolean) =
        reconciler.setHidden(key, userSerial, hidden)

    // TF
    override suspend fun removeFromHome(key: ComponentKey, userSerial: Long) = reconciler.removeFromHome(key, userSerial)

    // LD
    override suspend fun addAppAt(key: ComponentKey, targetContainer: Long, targetPageId: Long?, cell: GridPosition): Boolean =
        reconciler.addAppAt(key, targetContainer, targetPageId, cell)

    // AG
    override suspend fun groupIntoFolder(title: String, itemIds: List<Long>, folderId: Long?): Int =
        reconciler.groupIntoFolder(title, itemIds, folderId)

    override suspend fun addToFolder(itemId: Long, folderId: Long): Boolean =
        reconciler.addToFolder(itemId, folderId)

    override suspend fun reorderFolder(folderId: Long, orderedChildIds: List<Long>): Boolean =
        reconciler.reorderFolder(folderId, orderedChildIds)

    override suspend fun renameFolder(folderId: Long, title: String) =
        reconciler.renameFolder(folderId, title)

    override suspend fun createFolder(draggedItemId: Long, targetItemId: Long): Boolean =
        reconciler.createFolder(draggedItemId, targetItemId)

    private fun assemble(
        pages: List<HomePageEntity>,
        items: List<LauncherItemEntity>,
        apps: List<AppItem>
    ): Layout {
        val byKey = apps.associateBy { it.key }
        val folderIds = items.filter { it.itemType == LauncherItemEntity.ITEM_TYPE_FOLDER }
            .mapTo(HashSet()) { it.id }

        // G2a A1a: app trong từng thư mục theo `rank`. App không còn khả dụng (đã gỡ, tạm khóa) chỉ không được liệt kê,
        // KHÔNG xóa hàng ở đây (cùng nguyên tắc với app ở lưới).
        val childrenByFolder = HashMap<Long, MutableList<Pair<Long, AppItem>>>()
        items.filter { it.container > 0 && it.itemType == LauncherItemEntity.ITEM_TYPE_APP }
            .sortedBy { it.rank }
            .forEach { e ->
                val pkg = e.packageName
                val cls = e.className
                if (pkg == null || cls == null) return@forEach
                val app = byKey[ComponentKey(pkg, cls, e.userSerial)] ?: return@forEach
                childrenByFolder.getOrPut(e.container) { ArrayList() }.add(e.id to app)
            }

        val homeByPage = HashMap<Long, MutableList<PlacedItem>>()
        val dockItems = ArrayList<Pair<Int, DockApp>>()

        for (e in items) {
            // TV-3: hàng Today cũ (chờ migrateLegacyToday chuyển sang TodayWidgetsStore) không thuộc lưới hay dock: bỏ qua.
            if (e.container == LauncherItemEntity.CONTAINER_TODAY) continue
            // Hàng mồ côi: nằm trong thư mục không tồn tại. DB không có FK nên trạng thái này có thể xảy ra.
            if (e.container > 0 && e.container !in folderIds) {
                Log.w(TAG, "orphan item id=${e.id} container=${e.container}, skipped")
                continue
            }
            if (e.container > 0) continue // con của thư mục: đã gom vào childrenByFolder

            val position = GridPosition(e.gridX, e.gridY, e.spanX, e.spanY)
            when (e.itemType) {
                LauncherItemEntity.ITEM_TYPE_FOLDER -> {
                    if (e.container != LauncherItemEntity.CONTAINER_HOME && e.container != LauncherItemEntity.CONTAINER_DOCK) {
                        Log.w(
                            TAG,
                            "folder id=${e.id} outside home grid and dock (container=${e.container}), skipped"
                        )
                        continue
                    }
                    val children = childrenByFolder[e.id].orEmpty()
                    if (children.isEmpty()) {
                        // Thư mục không còn app khả dụng: không vẽ ô trống. Việc xóa/giải tán thật nằm ở A3-A4.
                        Log.w(TAG, "folder id=${e.id} has no available app, skipped")
                        continue
                    }
                    val folder = PlacedFolder(
                        itemId = e.id,
                        position = position,
                        title = e.title.orEmpty(),
                        // FM-1: đủ danh sách app con (panel nhiều trang cần hết); icon thư mục tự lấy 9 app đầu.
                        previewApps = children.map { it.second },
                        childIds = children.map { it.first },
                        appCount = children.size
                    )
                    if (e.container == LauncherItemEntity.CONTAINER_DOCK) {
                        dockItems.add(e.rank to DockApp(e.id, null, folder))
                    } else {
                        homeByPage.getOrPut(e.pageId) { ArrayList() }.add(folder)
                    }
                }

                LauncherItemEntity.ITEM_TYPE_APP -> {
                    val pkg = e.packageName
                    val cls = e.className
                    if (pkg == null || cls == null) {
                        Log.w(TAG, "app item id=${e.id} missing component, skipped")
                        continue
                    }
                    // Không tìm thấy app sống (đã gỡ hoặc tạm không khả dụng): chỉ không vẽ, KHÔNG xóa hàng ở đây.
                    val app = byKey[ComponentKey(pkg, cls, e.userSerial)] ?: continue

                    when (e.container) {
                        LauncherItemEntity.CONTAINER_HOME ->
                            homeByPage.getOrPut(e.pageId) { ArrayList() }
                                .add(PlacedApp(e.id, position, app))

                        LauncherItemEntity.CONTAINER_DOCK -> dockItems.add(
                            e.rank to DockApp(
                                e.id,
                                app
                            )
                        )
                    }
                }

                LauncherItemEntity.ITEM_TYPE_BUILTIN_WIDGET -> {
                    // G2c: widget tự vẽ. Mã loại hoặc cỡ lạ (bản mới hơn / dữ liệu lỗi): bỏ qua, KHÔNG xóa hàng.
                    val kind = WidgetKind.fromId(e.className)
                    if (e.container != LauncherItemEntity.CONTAINER_HOME || kind == null || !kind.allows(
                            e.spanX,
                            e.spanY
                        )
                    ) {
                        Log.w(
                            TAG,
                            "widget id=${e.id} className=${e.className} ${e.spanX}x${e.spanY} container=${e.container}, skipped"
                        )
                        continue
                    }
                    homeByPage.getOrPut(e.pageId) { ArrayList() }
                        .add(PlacedWidget(e.id, position, kind))
                }

                LauncherItemEntity.ITEM_TYPE_WIDGET -> {
                    // G3c: widget của app khác. Hàng lỗi (thiếu package/id, cỡ quá trần, ngoài lưới chính) bị bỏ qua, KHÔNG xóa hàng. Provider vắng thì hàng vẫn lên UI
                    // (ô vẽ placeholder, plan D14); `APP_WIDGETS_IN_UI` là công tắc tắt khẩn cấp.
                    val pkg = e.packageName
                    val id = e.appWidgetId
                    if (!APP_WIDGETS_IN_UI) continue
                    if (e.container != LauncherItemEntity.CONTAINER_HOME || pkg == null || id == null ||
                        !AppWidgetLimits.allows(e.spanX, e.spanY)
                    ) {
                        Log.w(TAG, "app widget row id=${e.id} invalid (container=${e.container} ${e.spanX}x${e.spanY}), skipped")
                        continue
                    }
                    homeByPage.getOrPut(e.pageId) { ArrayList() }
                        .add(PlacedAppWidget(e.id, position, id, AppWidgetRef(pkg, e.className.orEmpty(), e.userSerial)))
                }

                else -> Unit // shortcut (itemType 2): chưa hỗ trợ
            }
        }

        val pageList = pages.sortedBy { it.rank }
            .mapIndexed { index, p -> HomePage(index, p.id, homeByPage[p.id].orEmpty()) }
        val dock = dockItems.sortedBy { it.first }.map { it.second }
        return Layout(pageList, dock)
    }

    private companion object {
        const val TAG = "LayoutRepository"
        const val RETRY_BASE_MS = 500L
        const val RETRY_MAX_MS = 5_000L

        /** G3c: bật từ Task 4 (ô lưới vẽ host view). false thì hàng widget app không lên UI (chỉ còn là ô trống). */
        const val APP_WIDGETS_IN_UI = true
    }
}
