package gambi.global.os26launcher.ui.controlcenter

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.widget.Toast
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.controlcenter.MediaControlsPort
import gambi.global.os26launcher.domain.controlcenter.SystemControlsPort
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.ControlResult
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTileType
import gambi.global.os26launcher.domain.model.controlcenter.SystemPanel
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.ui.controlcenter.compose.CcCustomSlot
import gambi.global.os26launcher.ui.controlcenter.compose.ControlCenterPanel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** G3d: các phụ thuộc của presenter (gom lại cho gọn chữ ký). */
internal class ControlCenterDeps(
    val controls: SystemControlsPort,
    val media: MediaControlsPort,
    val prefs: ControlCenterPrefsRepository,
    val backdrop: BackdropRepository,
    val customBackground: ControlCenterBackgroundStore,
    val apps: AppRepository,
    val defaultDispatcher: CoroutineDispatcher
)

/**
 * G3d: nối panel với hệ thống. Sống trong [ControlCenterService] (không có ViewModel ở service), [scope] chạy trên main thread.
 * Chỉ theo dõi trạng thái hệ thống và media khi panel đang mở (Global Constraints của plan).
 */
internal class ControlCenterPresenter(
    private val context: Context,
    private val deps: ControlCenterDeps,
    private val scope: CoroutineScope
) : ControlCenterOverlay.Host, ControlCenterPanel.Listener {

    val overlay = ControlCenterOverlay(context, this)
    private val panel get() = overlay.panel
    private val backdrop = PanelBackdrop(deps.backdrop, deps.customBackground)
    private var config = ControlCenterConfig()
    private var appliedTiles: List<CustomTile>? = null
    private var stateJob: Job? = null
    private var mediaJob: Job? = null
    private var backdropJob: Job? = null
    private var iconJob: Job? = null
    private var lastSliderWrite = 0L
    private var mediaPackage: String? = null

    init {
        panel.listener = this
    }

    /** Service gọi mỗi khi cài đặt đổi. */
    fun applyConfig(c: ControlCenterConfig, nightShiftActive: Boolean) {
        config = c
        overlay.edgeConfig = c.edge
        overlay.vibrateOnOpen = c.vibrateOnOpen
        panel.setNightShift(nightShiftActive)
        if (c.tiles != appliedTiles) {
            appliedTiles = c.tiles
            loadAppIcons(panel.setCustomTiles(c.tiles))
        }
    }

    // JS7b: icon và tên app ghi vào trạng thái Compose của ô ([CcCustomSlot]) thay vì ControlTileView.
    private fun loadAppIcons(apps: List<Pair<CustomTile.App, CcCustomSlot>>) {
        iconJob?.cancel()
        if (apps.isEmpty()) return
        val size = context.resources.getDimensionPixelSize(R.dimen.cc_app_icon)
        iconJob = scope.launch {
            for ((tile, slot) in apps) {
                deps.apps.icon(ComponentKey(tile.packageName, tile.className, tile.userSerial), size)?.let { slot.appIcon = it }
                slot.contentDescription = labelOf(tile)
            }
        }
    }

    private suspend fun labelOf(tile: CustomTile.App): String = withContext(deps.defaultDispatcher) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(tile.packageName, 0)).toString()
        } catch (e: Exception) {
            tile.packageName
        }
    }

    override fun onPanelOpening() {
        // Đọc lại trạng thái mỗi lần bắt đầu kéo mở (như app mẫu, RE mục 2.5).
        panel.render(deps.controls.snapshot())
        stateJob?.cancel()
        stateJob = scope.launch { deps.controls.observe().collect { panel.render(it) } }
        mediaJob?.cancel()
        mediaJob = scope.launch {
            deps.media.observe().collect {
                mediaPackage = it?.packageName
                panel.renderMedia(it)
            }
        }
        backdropJob?.cancel()
        backdropJob = scope.launch {
            val dm = context.resources.displayMetrics
            val bmp = withContext(deps.defaultDispatcher) {
                backdrop.load(config.background, config.blurBackground, dm.widthPixels, dm.heightPixels)
            }
            panel.setBackdrop(bmp, config.blurBackground)
        }
    }

    override fun onPanelClosed() {
        stateJob?.cancel()
        stateJob = null
        mediaJob?.cancel()
        mediaJob = null
        backdropJob?.cancel()
        backdropJob = null
    }

    override fun onTileClick(tile: ControlTile) {
        panel.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        handle(deps.controls.toggle(tile))
    }

    /** RE mục 5.3: hành động của từng loại ô; ô mở màn khác thì đóng panel trước (`cn8.h()`). */
    override fun onCustomTileClick(tile: CustomTile) {
        panel.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (tile) {
            is CustomTile.App -> overlay.closeNow {
                if (!deps.apps.launch(ComponentKey(tile.packageName, tile.className, tile.userSerial), null)) toast(R.string.cc_no_app)
            }

            is CustomTile.Builtin -> when (tile.type) {
                CustomTileType.FLASHLIGHT -> handle(deps.controls.toggle(ControlTile.FLASHLIGHT))
                CustomTileType.NIGHT_SHIFT -> toggleNightShift()

                CustomTileType.SCREEN_RECORD -> openRecorder()
                CustomTileType.TIMER -> open(SystemPanel.TIMER)
                CustomTileType.CALCULATOR -> open(SystemPanel.CALCULATOR)
                CustomTileType.CAMERA -> open(SystemPanel.CAMERA)
                CustomTileType.BATTERY -> open(SystemPanel.BATTERY_SAVER)
                CustomTileType.VOICE_MEMO -> open(SystemPanel.VOICE_MEMO)
                CustomTileType.SETTINGS -> open(SystemPanel.SETTINGS)
                CustomTileType.DARK_MODE -> open(SystemPanel.DARK_MODE)
                CustomTileType.NOTES -> open(SystemPanel.NOTES)
            }
        }
    }

    /**
     * Ô Night Shift. Đang bật theo lịch (không phải bật tay) thì công tắc tay không tắt được lớp phủ, nên mở màn Night Shift để
     * người dùng đổi lịch, thay vì chạm mà không thấy gì đổi.
     */
    private fun toggleNightShift() {
        val ns = config.nightShift
        val now = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        if (!ns.enabled && ns.activeAt(now)) {
            overlay.closeNow {
                SystemIntents.startFirst(context, listOf(Intent(context, NightShiftSettingsActivity::class.java)), newTask = true)
            }
            return
        }
        scope.launch { deps.prefs.update { it.copy(nightShift = it.nightShift.copy(enabled = !it.nightShift.enabled)) } }
    }

    /** RE mục 5.3: ô Ghi màn hình mở app ghi màn hình người dùng đã chọn; chưa chọn thì mở màn cài đặt để chọn. */
    private fun openRecorder() = overlay.closeNow {
        val pkg = config.recorderPackage
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            SystemIntents.startFirst(context, listOf(ControlCenterSettingsActivity.intent(context, focusRecorder = true)), newTask = true)
            return@closeNow
        }
        SystemIntents.startFirst(context, listOf(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), newTask = true)
    }

    private fun open(target: SystemPanel) = handle(ControlResult.OpenSystemPanel(target))

    override fun onBrightness(value: Float, done: Boolean) {
        if (!throttle(done)) return
        val r = deps.controls.setBrightness(value)
        // Thiếu quyền: chỉ chuyển màn khi nhả tay, không đóng panel giữa lúc đang kéo.
        if (r !is ControlResult.NeedsPermission || done) handle(r)
    }

    override fun onVolume(value: Float, done: Boolean) {
        if (!throttle(done)) return
        val r = deps.controls.setVolume(value)
        if (r is ControlResult.Done || done) handle(r) // lỗi chỉ báo một lần khi nhả tay, không mỗi 50 ms
    }

    override fun onAutoBrightness() = handle(deps.controls.toggleAutoBrightness())

    override fun onScreenTimeout(millis: Int) = handle(deps.controls.setScreenTimeout(millis))

    override fun onOpenSettings() = overlay.closeNow {
        SystemIntents.startFirst(context, listOf(ControlCenterSettingsActivity.intent(context)), newTask = true)
    }

    override fun onDismiss() = overlay.close()

    override fun onPrevious() = deps.media.previous()

    override fun onPlayPause() = deps.media.playPause()

    override fun onNext() = deps.media.next()

    override fun onSeek(positionMs: Long) = deps.media.seekTo(positionMs)

    override fun onOpenApp() {
        val pkg = mediaPackage ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        overlay.closeNow { SystemIntents.startFirst(context, listOf(intent), newTask = true) }
    }

    override fun onOutput() = open(SystemPanel.SOUND_OUTPUT)

    private fun handle(result: ControlResult) {
        when (result) {
            ControlResult.Done -> Unit
            is ControlResult.OpenSystemPanel -> overlay.closeNow {
                if (!SystemIntents.startFirst(context, SystemIntents.forPanel(context, result.panel), newTask = true)) {
                    toast(R.string.cc_no_app)
                }
            }

            is ControlResult.NeedsPermission -> overlay.closeNow {
                SystemIntents.startFirst(
                    context,
                    listOf(ControlCenterSettingsActivity.intent(context, result.permission)),
                    newTask = true
                )
            }

            ControlResult.Unavailable -> toast(R.string.cc_unavailable)
        }
    }

    /** Ghi thanh trượt tối đa mỗi [SLIDER_THROTTLE_MS]; lần nhả tay luôn ghi. */
    private fun throttle(done: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        if (!done && now - lastSliderWrite < SLIDER_THROTTLE_MS) return false
        lastSliderWrite = now
        return true
    }

    private fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private companion object {
        const val SLIDER_THROTTLE_MS = 50L
    }
}
