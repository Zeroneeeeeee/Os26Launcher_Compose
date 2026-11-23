package gambi.global.os26launcher.data.apps

import android.graphics.Bitmap
import android.graphics.Rect
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.PackageEvent
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.repository.AppLabelRepository
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val dataSource: LauncherAppsDataSource,
    private val iconLoader: IconLoader,
    private val iconCache: IconCache,
    private val iconDiskCache: IconDiskCache,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
    private val recentApps: RecentAppsRepository, // M8d: ghi lịch sử mở app
    private val labelRepository: AppLabelRepository, // RN: tên riêng, phủ lên nhãn gốc
    private val customIcons: CustomIconStore // P3-4: icon riêng -> versionStamp đổi để mọi danh sách nạp lại icon
) : AppRepository {

    // Xóa icon cũ ở đây chứ không ở data source: repository là chỗ duy nhất biết cả hai (nguồn thay đổi và cache),
    // data source không cần biết cache tồn tại. Xóa TRƯỚC debounce để không mất tên package bị gộp.
    // onStart sau debounce: lần nạp đầu (và nạp lại sau khi WhileSubscribed ngừng) chạy ngay, không chờ 200 ms.
    // WhileSubscribed(5_000): rời launcher 5 giây thì ngừng và hủy đăng ký callback, quay lại trong 5 giây thì không nạp lại.
    // RN: [loaded] là nguồn từ hệ thống (nhãn GỐC, chưa sắp); [apps] phủ tên riêng lên rồi sắp theo tên hiển thị. Đổi tên riêng chỉ chạy lại
    // phần phủ (combine), không đọc lại nhãn từ hệ thống. combine chờ cả hai nguồn có giá trị đầu nên `apps` phát đúng một lần ở lần nạp đầu
    // (không phát nhãn gốc rồi nhãn riêng -> Home không nháy). Lỗi đọc file tên đã thành bản đồ rỗng ở repository, không giết flow này.
    @OptIn(FlowPreview::class)
    private val loaded: Flow<List<AppItem>> = dataSource.observeChanges()
        .onEach { event ->
            event.packageNames.forEach(iconCache::evict)
            // IC: đĩa chỉ dọn khi gỡ thật (giải phóng chỗ). CHANGED/cập nhật không cần: sourceDir đổi nên tên file đổi.
            if (event.type == PackageEvent.Type.REMOVED) event.packageNames.forEach(iconDiskCache::evict)
        }
        .map { Unit }
        // B9/C10: đổi ngôn ngữ và sự kiện hồ sơ công việc cũng buộc nạp lại (qua cùng debounce)
        .let { merge(it, dataSource.observeReloadTriggers()) }
        .debounce(RELOAD_DEBOUNCE_MS) // cài một app bắn nhiều callback liền nhau, chỉ cần nạp lại một lần
        .onStart { emit(Unit) }
        .map { loadBase() }
        .flowOn(io)

    override val apps: Flow<List<AppItem>> = combine(loaded, labelRepository.customLabels, customIcons.stamps) { base, labels, icons ->
        overlayAndSort(base, labels, icons)
    }
        .flowOn(io)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // C10: hồ sơ công việc bị bỏ không bắn onPackageRemoved cho từng app, nên loadSorted phát USER_REMOVED giả; LayoutRepositoryImpl.init đã
    // nghe packageEvents và gọi reconciler.removeUser (xóa MỌI hàng của user, dọn hidden_apps, dồn ô). Cần khai báo TRƯỚC packageEvents.
    private val syntheticRemovals = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 8)

    override val packageEvents: Flow<PackageEvent> = merge(dataSource.observeChanges(), syntheticRemovals)

    override fun isPackageInstalled(packageName: String, userSerial: Long): Boolean =
        dataSource.isPackageInstalled(packageName, userSerial)

    override suspend fun icon(key: ComponentKey, sizePx: Int): Bitmap? =
        withContext(io) { iconLoader.load(key, sizePx) }

    override suspend fun iconWithStyle(key: ComponentKey, sizePx: Int, style: IconStyleKey): Bitmap? =
        withContext(io) { iconLoader.render(key, sizePx, style) }

    override suspend fun iconOnColor(key: ComponentKey, sizePx: Int, color: Int): Bitmap? =
        withContext(io) { iconLoader.renderOnColor(key, sizePx, color) }

    override suspend fun shapeIcon(square: Bitmap, sizePx: Int): Bitmap = withContext(io) { iconLoader.shape(square, sizePx) }

    override suspend fun systemIcon(key: ComponentKey, sizePx: Int): Bitmap? = withContext(io) { iconLoader.loadSystem(key, sizePx) }

    override fun launch(key: ComponentKey, source: ScreenRect?): Boolean {
        val ok = dataSource.startMainActivity(
            key,
            source?.let { Rect(it.left, it.top, it.right, it.bottom) })
        if (ok) recentApps.record(key) // M8d Task 5: chỉ ghi khi mở thành công
        return ok
    }

    override fun openAppInfo(key: ComponentKey): Boolean = dataSource.startAppDetails(key)

    override fun requestUninstall(key: ComponentKey): Boolean = dataSource.requestUninstall(key)

    override fun openClock(): Boolean = dataSource.openClock()

    override fun openCalendar(): Boolean = dataSource.openCalendar()

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 200L
    }

    /** RN: nạp từ hệ thống, nhãn GỐC, CHƯA sắp (phép sắp nằm ở [overlayAndSort], sau khi phủ tên riêng). */
    private suspend fun loadBase(): List<AppItem> {
        val apps = dataSource.loadApps()
        // A4/B7: package có stamp đổi (cập nhật APK) thì bỏ icon RAM của nó, kể cả bản cũ mà một lượt nạp trong khoảng giữa sự kiện
        // và lần nạp lại này đã cất dưới stamp cũ. Chạy TRƯỚC khi danh sách mới phát. Lần nạp đầu của process không có gì để so.
        val previous = lastStamps.getAndSet(apps.associate { it.key to it.versionStamp })
        if (previous.isNotEmpty()) {
            val changed = apps.filter { val old = previous[it.key]; !old.isNullOrEmpty() && old != it.versionStamp }
                .mapTo(HashSet()) { it.key.packageName }
            for (pkg in changed) {
                iconCache.evict(pkg)
                // C2: file đĩa của stamp cũ cũng bỏ. Lệnh xóa vào hàng đợi luồng ghi TRƯỚC khi danh sách mới phát, nên mọi file ghi theo stamp mới nằm sau nó.
                iconDiskCache.evict(pkg)
            }
        }
        // C10: user có trong lần nạp trước mà vắng lần này VÀ đã bị hệ thống bỏ thật thì dọn hàng của nó. Không đụng RAM/đĩa icon:
        // cùng tên package còn dùng cho user chính; file của hồ sơ nằm lại tới khi LRU đĩa dọn.
        val previousPackages = lastPackagesByUser.get()
        val current = apps.groupBy({ it.key.userSerial }, { it.key.packageName }).mapValues { it.value.toSet() }
        val remembered = HashMap(current)
        for ((serial, packages) in previousPackages) {
            if (serial in current) continue
            if (dataSource.isUserGone(serial)) {
                syntheticRemovals.tryEmit(PackageEvent(PackageEvent.Type.USER_REMOVED, emptyList(), serial))
            } else {
                remembered[serial] = packages // chưa kết luận được (vd đang gỡ dở): hỏi lại ở lần nạp sau
            }
        }
        lastPackagesByUser.set(remembered)
        // RN: quét tên riêng mồ côi MỘT lần mỗi tiến trình, nền, sau khi đã có danh sách (không chặn lần phát đầu).
        if (apps.isNotEmpty() && sweepPending.compareAndSet(true, false)) scope.launch(io) { sweepOrphanLabels(apps) }
        return apps
    }

    /**
     * RN: thay [AppItem.label] bằng tên riêng (giữ [AppItem.originalLabel] là tên gốc) rồi sắp theo tên HIỂN THỊ. App hồ sơ công việc có
     * `a11yLabel` do hệ thống dịch từ nhãn gốc ("Chrome (Công việc)") thì tính lại từ tên riêng. Không đụng `LabelCache`: nó đã ghi nhãn gốc
     * trong `loadApps`.
     */
    private fun overlayAndSort(
        base: List<AppItem>,
        labels: Map<ComponentKey, String>,
        icons: Map<ComponentKey, String> = emptyMap()
    ): List<AppItem> {
        // Collator KHÔNG thread-safe → tạo mới mỗi lần sắp, không giữ làm field.
        // Không dùng sortedBy { label }: so theo code point nên Đ, Ă, Ư rơi xuống sau Z.
        val collator =
            Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val shown = if (labels.isEmpty()) base else base.map { app ->
            val custom = labels[app.key]
            when {
                custom == null || custom == app.label -> app
                else -> app.copy(
                    label = custom,
                    a11yLabel = if (app.a11yLabel.isEmpty()) "" else dataSource.badgedLabelFor(custom, app.key.userSerial)
                )
            }
        }
        // P3-4: app có icon riêng mang thêm tên file icon trong versionStamp -> AppItem khác -> view nạp lại icon (khóa cache cũng đổi).
        val stamped = if (icons.isEmpty()) shown else shown.map { app ->
            icons[app.key]?.let { app.copy(versionStamp = app.versionStamp + "#ci:" + it) } ?: app
        }
        return stamped.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    // C10: package theo user của lần nạp trước (kèm user chưa kết luận được là đã bỏ).
    private val lastPackagesByUser = AtomicReference<Map<Long, Set<String>>>(emptyMap())

    // A4/B7: stamp của lần nạp trước, chỉ đọc/ghi trong loadSorted (tuần tự trong flow); AtomicReference vì luồng IO có thể đổi giữa hai lần.
    private val lastStamps = AtomicReference<Map<ComponentKey, String>>(emptyMap())

    // RN: gỡ app thật thì xóa tên riêng của nó (cài lại là app mới, Q5). Mỗi sự kiện bọc try/catch: ApplicationScope không có handler.
    // Chốt an toàn `isPackageInstalled == false`: app bị tắt/khóa/thẻ nhớ tháo vẫn còn cài nên giữ tên (nguyên tắc Q11 của FX3). Không bao
    // giờ xóa vì "vắng khỏi danh sách".
    init {
        scope.launch(io) {
            packageEvents
                .filter { it.type == PackageEvent.Type.REMOVED || it.type == PackageEvent.Type.USER_REMOVED }
                .collect { event ->
                    try {
                        if (event.type == PackageEvent.Type.USER_REMOVED) {
                            if (dataSource.isWorkUser(event.userSerial)) { // không bao giờ user chính
                                labelRepository.removeUser(event.userSerial)
                                customIcons.deleteUserFiles(event.userSerial) // P3-4
                            }
                        } else {
                            for (pkg in event.packageNames) {
                                if (!dataSource.isPackageInstalled(pkg, event.userSerial)) {
                                    labelRepository.removePackage(pkg, event.userSerial)
                                    customIcons.deletePackageFiles(pkg, event.userSerial) // P3-4: hàng CSDL do LayoutReconciler xóa
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("AppRepository", "drop custom labels failed, user=${event.userSerial}", e)
                    }
                }
        }
    }

    // RN: true cho tới lần quét đầu của tiến trình.
    private val sweepPending = AtomicBoolean(true)

    /**
     * RN: bắt kịp gỡ app lúc launcher không chạy. Khóa tên riêng không còn trong [live] thì gom theo (package, user) và chỉ xóa nhóm mà
     * hệ thống nói đã gỡ. Khóa còn cài nhưng vắng (app tắt, khóa, đổi tên activity A3) giữ nguyên. [live] rỗng (hệ thống từ chối) thì không kết luận.
     */
    private suspend fun sweepOrphanLabels(live: List<AppItem>) {
        try {
            val liveKeys = live.mapTo(HashSet()) { it.key }
            val orphans = labelRepository.customLabels.first().keys
                .filter { it !in liveKeys }
                .mapTo(LinkedHashSet()) { it.packageName to it.userSerial }
            for ((pkg, serial) in orphans) {
                if (!dataSource.isPackageInstalled(pkg, serial)) labelRepository.removePackage(pkg, serial)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AppRepository", "sweep custom labels failed", e)
        }
    }
}
