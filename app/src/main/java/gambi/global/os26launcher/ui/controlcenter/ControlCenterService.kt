package gambi.global.os26launcher.ui.controlcenter

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.core.coroutines.DefaultDispatcher
import gambi.global.os26launcher.data.notification.NotificationBadgeStore
import gambi.global.os26launcher.core.locale.AppLocaleContext
import gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.controlcenter.MediaControlsPort
import gambi.global.os26launcher.domain.controlcenter.SystemControlsPort
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.BackdropRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.domain.usecase.GetHiddenAppsUseCase
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * G3d: service chủ của Trung tâm điều khiển toàn hệ thống.
 *
 * Là [NotificationListenerService] để hệ thống tự bind (và bind lại sau khi khởi động máy hoặc process bị giết) ngay khi người dùng
 * cấp "Truy cập thông báo", giống `LauncherNotificationService` của Launcher OS (RE mục 2). Quyền này còn là `ComponentName` để
 * đọc phiên media (`MediaSessionSource`). BG: thông báo chỉ được ĐẾM cho huy hiệu icon ([NotificationBadgeStore]), không lưu nội dung.
 *
 * Vòng đời: [onListenerConnected] theo dõi cài đặt; bật + có quyền vẽ đè thì hiện dải mép, và lớp Night Shift khi đến giờ (RE mục
 * 5.4). [onListenerDisconnected] / [onDestroy] gỡ mọi cửa sổ. Màn cài đặt gọi [refresh] sau khi người dùng đổi quyền (không có
 * callback hệ thống cho quyền vẽ đè).
 */
@AndroidEntryPoint
class ControlCenterService : NotificationListenerService() {

    @Inject
    lateinit var prefs: ControlCenterPrefsRepository

    @Inject
    lateinit var permissions: ControlPermissionsPort

    @Inject
    lateinit var controls: SystemControlsPort

    @Inject
    lateinit var media: MediaControlsPort

    @Inject
    lateinit var backdropRepository: BackdropRepository

    @Inject
    lateinit var customBackground: ControlCenterBackgroundStore

    @Inject
    lateinit var apps: AppRepository

    /** P3-7: ô app trỏ tới app đã ẩn không hiện trên panel (cấu hình giữ nguyên, bỏ ẩn thì ô về đúng chỗ cũ). */
    @Inject
    lateinit var getHiddenApps: GetHiddenAppsUseCase

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Inject
    lateinit var badges: NotificationBadgeStore // BG: số huy hiệu cho Home

    /** BG: yêu cầu đếm lại huy hiệu; CONFLATED gộp loạt thông báo dồn dập thành một lần đọc. */
    private var badgeRefresh: Channel<Unit>? = null

    private var scope: CoroutineScope? = null
    private var presenter: ControlCenterPresenter? = null
    private var presenterLocale: String? = null // LG: ngôn ngữ chữ của panel đã dựng
    private var nightShift: NightShiftOverlay? = null
    private var config = ControlCenterConfig()
    private var connected = false
    private val handler = Handler(Looper.getMainLooper())

    /** Lịch Night Shift: kiểm lại mỗi phút khi đang bật hẹn giờ (rẻ, không cần AlarmManager khi service còn sống). */
    private val scheduleTick = object : Runnable {
        override fun run() {
            applyState()
            if (config.nightShift.scheduled) handler.postDelayed(this, msToNextMinute())
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        teardown() // kết nối lại mà không qua disconnect: bỏ presenter giữ scope cũ đã huỷ
        connected = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            // P3-7: lọc ô app của app đã ẩn CHỈ khi hiển thị; các ô sau dồn lên (ô trống cũng là dấu vết). Không ghi lại cấu hình.
            combine(prefs.config, getHiddenApps().map { list -> list.mapTo(HashSet()) { a -> a.key } }) { c, hidden ->
                if (hidden.isEmpty()) c else c.copy(tiles = c.tiles.filterNot { t ->
                    t is CustomTile.App && ComponentKey(t.packageName, t.className, t.userSerial) in hidden
                })
            }.collect {
                config = it
                applyState()
                handler.removeCallbacks(scheduleTick)
                if (it.nightShift.scheduled) handler.postDelayed(scheduleTick, msToNextMinute())
            }
        }
        s.launch { // P3-7: chạy trên main (scope Main.immediate), như mọi lời gọi tới media
            hiddenOnlyPackages().collect { set -> media.setExcludedPackages(set) }
        }
        val refresh = Channel<Unit>(Channel.CONFLATED)
        badgeRefresh = refresh
        s.launch(defaultDispatcher) {
            refresh.consumeAsFlow().collect { publishBadges() }
        }
        refresh.trySend(Unit)
    }

    // BG: mọi thay đổi thông báo -> đếm lại từ đầu (rẻ, không phải tự giữ trạng thái từng thông báo).
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        badgeRefresh?.trySend(Unit)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        badgeRefresh?.trySend(Unit)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        badgeRefresh?.trySend(Unit) // người dùng tắt/bật huy hiệu của một kênh
    }

    private fun publishBadges() {
        try {
            badges.publish(activeNotifications ?: emptyArray(), currentRanking)
        } catch (e: RuntimeException) {
            // SecurityException khi listener vừa mất kết nối: lần nối lại sẽ đếm lại
            Log.w(TAG, "publishBadges failed", e)
        }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH && scope != null) applyState()
        // Chỉ là lệnh làm mới: không giữ trạng thái "started". Service vẫn sống nhờ hệ thống bind làm listener.
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        presenter?.overlay?.relayout()
        applyState() // LG: API 33+ đổi ngôn ngữ app đi qua cấu hình: dựng lại panel nếu ngôn ngữ đổi
    }

    private fun applyState() {
        val s = scope ?: return
        if (!connected) return
        val canDraw = permissions.isGranted(ControlPermission.OVERLAY)
        val nowMinutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val nightShiftActive = canDraw && config.nightShift.activeAt(nowMinutes)
        // Night Shift chạy cả khi dải mép tắt (giống app mẫu: công tắc riêng).
        if (nightShiftActive) {
            (nightShift ?: NightShiftOverlay(this).also { nightShift = it }).show(config.nightShift.warmth)
        } else {
            nightShift?.hide()
        }
        val show = config.enabled && canDraw
        // LG: panel dựng chữ một lần; ngôn ngữ app đổi (Cài đặt -> Ngôn ngữ gọi refresh) thì bỏ panel cũ, dựng lại theo ngôn ngữ mới.
        // API < 33 context của service không theo ngôn ngữ app nên dựng bằng context đã bọc.
        val locale = AppLocaleContext.localeKey(this)
        if (presenter != null && presenterLocale != locale) {
            presenter?.overlay?.destroy() // JS7b: gỡ cửa sổ + huỷ composition của panel cũ
            presenter?.onPanelClosed()
            presenter = null
        }
        if (show) {
            val p = presenter ?: ControlCenterPresenter(
                AppLocaleContext.wrap(this),
                ControlCenterDeps(controls, media, prefs, backdropRepository, customBackground, apps, defaultDispatcher),
                s
            ).also { presenter = it; presenterLocale = locale }
            p.applyConfig(config, nightShiftActive)
            p.overlay.showEdge()
        } else {
            presenter?.overlay?.hideAll()
        }
    }

    private fun teardown() {
        connected = false
        badgeRefresh?.close()
        badgeRefresh = null
        badges.clear() // BG: không còn đọc được thông báo -> Home bỏ hết huy hiệu
        handler.removeCallbacks(scheduleTick)
        presenter?.overlay?.destroy() // JS7b: gỡ cửa sổ + huỷ composition của panel
        presenter?.onPanelClosed()
        presenter = null
        nightShift?.hide()
        nightShift = null
        scope?.cancel()
        scope = null
    }

    /**
     * P3-7: các gói chỉ còn app ĐÃ ẨN (mọi activity khởi động của gói đều ẩn): trình phát nhạc bỏ qua phiên của các gói này.
     * REF dùng `GetHiddenOnlyPackagesUseCase` (chưa có ở bản này): tính tại chỗ từ app đã ẩn trừ gói còn app đang hiện.
     */
    private fun hiddenOnlyPackages(): Flow<Set<String>> =
        combine(apps.apps, getHiddenApps()) { all, hidden ->
            if (hidden.isEmpty()) {
                emptySet()
            } else {
                val hiddenKeys = hidden.mapTo(HashSet()) { it.key }
                val shown = all.filter { it.key !in hiddenKeys }.mapTo(HashSet()) { it.key.packageName }
                hidden.mapTo(HashSet()) { it.key.packageName } - shown
            }
        }.distinctUntilChanged()

    private fun msToNextMinute(): Long {
        val now = System.currentTimeMillis()
        return MINUTE_MS - now % MINUTE_MS + 500
    }

    companion object {
        private const val TAG = "ControlCenterService"
        private const val ACTION_REFRESH = "gambi.global.os26launcher.controlcenter.REFRESH"
        private const val MINUTE_MS = 60_000L

        /**
         * Báo service đọc lại công tắc và quyền. Chỉ gửi khi app đang là listener (nếu không, startService sẽ tạo một service
         * thường không được bind, vô ích). Gọi từ Activity đang hiện (startService từ nền bị chặn từ API 26).
         */
        fun refresh(context: Context) {
            if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) return
            try {
                context.startService(Intent(context, ControlCenterService::class.java).setAction(ACTION_REFRESH))
            } catch (e: RuntimeException) {
                // IllegalStateException (app đang ở nền) hoặc SecurityException: lần bind kế sẽ tự áp trạng thái
            }
        }
    }
}
