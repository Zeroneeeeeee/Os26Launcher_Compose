package gambi.global.os26launcher.ui.controlcenter

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.ui.settings.SettingsDialogs
import gambi.global.os26launcher.ui.settings.compose.SettingsList
import gambi.global.os26launcher.ui.settings.compose.SettingsListActions
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.controlcenter.BackgroundMode
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.usecase.GetHiddenAppsUseCase
import gambi.global.os26launcher.ui.settings.SettingsRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * G3d: màn cài đặt Trung tâm điều khiển, theo `ControlMainActivity` của app mẫu (RE mục 5.4): bật, tùy chỉnh ô, dải mép, nền
 * (ảnh nền / ảnh riêng / không, làm mờ), Night Shift, app ghi màn hình, rung khi mở, và các quyền. Dùng lại khung màn Cài đặt
 * (JS1: `SettingsList` Compose).
 *
 * Mở từ Cài đặt của launcher, từ nút cài đặt trên panel, hoặc từ một ô thiếu quyền (extra [EXTRA_PERMISSION]: cuộn tới đúng hàng;
 * [EXTRA_RECORDER]: mở luôn hộp chọn app ghi màn hình). Phần lớn quyền không có callback hệ thống, nên đọc lại ở [onResume] và báo
 * service ([ControlCenterService.refresh]). Bật switch thì hỏi lần lượt các quyền còn thiếu ([onEnableChanged]); không cấp đủ quyền bắt buộc thì switch tự tắt.
 */
@AndroidEntryPoint
class ControlCenterSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: ControlCenterPrefsRepository

    @Inject
    lateinit var permissions: ControlPermissionsPort

    @Inject
    lateinit var backgroundStore: ControlCenterBackgroundStore

    @Inject
    lateinit var apps: AppRepository

    /** P3-7: lọc app đã ẩn khỏi danh sách chọn app. */
    @Inject
    lateinit var getHiddenApps: GetHiddenAppsUseCase

    private var config: ControlCenterConfig? = null
    private var focusPending: ControlPermission? = null
    private var recorderPending = false
    private var dialog: DsDialogHandle? = null

    /** Hộp thoại xin quyền đang mở (tách khỏi [dialog] để không đóng nhầm hộp chọn nền / app ghi màn hình). */
    private var permissionDialog: DsDialogHandle? = null

    /** Quyền đã hỏi trong lượt bật này: mỗi quyền hỏi tối đa một lần, quay lại từ màn hệ thống mà chưa cấp thì không hỏi lại. */
    private val askedPermissions = HashSet<ControlPermission>()

    /** Đang trong lượt hỏi quyền (bắt đầu khi người dùng bật switch); mỗi lần quay lại màn thì hỏi quyền kế tiếp. */
    private var promptActive = false

    /**
     * Người dùng vừa bật switch nhưng còn thiếu quyền bắt buộc: switch hiện bật, pref `enabled` CHƯA ghi. Đủ quyền bắt buộc thì ghi bật;
     * từ chối / hết quyền để hỏi mà vẫn thiếu thì bỏ, switch tự tắt.
     */
    private var enablePending by mutableStateOf(false)

    /** RST: mở màn Truy cập thông báo; máy chặn "Cài đặt bị hạn chế" thì hướng dẫn mở khóa khi quay lại. */
    private val listenerFlow = NotificationListenerFlow(
        this,
        isGranted = { permissions.isGranted(ControlPermission.NOTIFICATION_LISTENER) },
        onGuideDeclined = ::onPromptDeclined
    )

    // JS1: trạng thái Compose của khung.
    private var rows by mutableStateOf<List<SettingsRow>>(emptyList())
    private var loaded by mutableStateOf(false)
    private val listState = LazyListState()

    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Từ chối hai lần thì hệ thống không hỏi nữa: mở màn chi tiết app để người dùng tự bật.
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
            openSystemScreen(ControlPermission.BLUETOOTH_CONNECT)
        }
        render()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            if (backgroundStore.importImage(uri.toString())) {
                prefs.update { it.copy(background = BackgroundMode.CUSTOM_IMAGE) }
            } else {
                Toast.makeText(this@ControlCenterSettingsActivity, R.string.cc_background_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // JS1: nền aurora tràn dưới thanh hệ thống (DsScreen lo inset)
        readExtras(intent)
        savedInstanceState?.let { state ->
            promptActive = state.getBoolean(STATE_PROMPT_ACTIVE)
            enablePending = state.getBoolean(STATE_ENABLE_PENDING)
            state.getStringArrayList(STATE_ASKED)?.forEach { name ->
                ControlPermission.entries.firstOrNull { it.name == name }?.let(askedPermissions::add)
            }
        }

        val actions = SettingsListActions(
            onValueClicked = ::onValueClicked,
            onSwitchChanged = { row, checked ->
                if (row.id == ID_ENABLED) {
                    onEnableChanged(checked)
                } else {
                    lifecycleScope.launch {
                        when (row.id) {
                            ID_BLUR -> prefs.update { it.copy(blurBackground = checked) }
                            ID_VIBRATE -> prefs.update { it.copy(vibrateOnOpen = checked) }
                        }
                        ControlCenterService.refresh(this@ControlCenterSettingsActivity)
                    }
                }
            }
        )
        // RS: dùng chung khung Cài đặt v3 (luôn tối). JS1: Compose (DsScreen + SettingsList).
        setContent {
            DsScreen(title = stringResource(R.string.cc_title), onBack = ::finish) {
                if (loaded) SettingsList(rows, actions, Modifier.weight(1f), state = listState)
            }
            dsDialogs.Render()
        }
        reassertEdgeToEdge(window)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.config.collect {
                    config = it
                    syncEnabledWithPermissions()
                    render()
                    if (recorderPending) {
                        recorderPending = false
                        showRecorderDialog()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readExtras(intent)
        render()
        if (recorderPending && config != null) {
            recorderPending = false
            showRecorderDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        syncEnabledWithPermissions()
        render()
        ControlCenterService.refresh(this) // người dùng có thể vừa cấp quyền vẽ đè ở màn hệ thống
        if (listenerFlow.onResume()) return // đang hướng dẫn mở khóa / vừa mở lại màn quyền: chưa hỏi quyền khác
        if (promptActive) promptNextPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PROMPT_ACTIVE, promptActive)
        outState.putBoolean(STATE_ENABLE_PENDING, enablePending)
        outState.putStringArrayList(STATE_ASKED, ArrayList(askedPermissions.map { it.name }))
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        permissionDialog?.setOnDismissListener(null)
        permissionDialog?.dismiss()
        permissionDialog = null
        listenerFlow.onDestroy()
        super.onDestroy()
    }

    /**
     * Switch "Bật Trung tâm điều khiển". Tắt: ghi tắt, dừng hỏi quyền. Bật: bắt đầu lượt hỏi quyền ([promptNextPermission]); đủ quyền bắt
     * buộc thì ghi bật ngay, thiếu thì chờ ([enablePending]) tới khi người dùng cấp đủ.
     */
    private fun onEnableChanged(checked: Boolean) {
        if (!checked) {
            stopPrompt()
            lifecycleScope.launch {
                prefs.setEnabled(false)
                ControlCenterService.refresh(this@ControlCenterSettingsActivity)
            }
            return
        }
        askedPermissions.clear()
        promptActive = true
        if (permissions.missingRequired().isEmpty()) {
            commitEnabled()
        } else {
            enablePending = true
            render()
        }
        promptNextPermission()
    }

    /**
     * Hỏi quyền kế tiếp trong lượt bật: còn chờ quyền bắt buộc thì chỉ hỏi quyền bắt buộc chưa hỏi; hỏi hết mà vẫn thiếu thì switch tự tắt.
     * Đủ quyền bắt buộc thì ghi bật rồi hỏi tiếp quyền tùy chọn chưa cấp. "Để sau" / Back kết thúc lượt ([onPromptDeclined]). Không chen
     * ngang khi đang mở hộp chọn nền / app ghi màn hình / hướng dẫn mở khóa.
     */
    private fun promptNextPermission() {
        if (!promptActive || permissionDialog?.isShowing == true || dialog?.isShowing == true || listenerFlow.isShowingGuide) return
        val missingRequired = permissions.missingRequired()
        if (enablePending && missingRequired.isEmpty()) commitEnabled()
        val candidates = if (enablePending) missingRequired else permissions.applicable().filter { !permissions.isGranted(it) }
        val permission = candidates.firstOrNull { it !in askedPermissions }
        if (permission == null) {
            stopPrompt()
            return
        }
        askedPermissions += permission
        permissionDialog = SettingsDialogs.confirm(
            activity = this,
            title = getString(permission.titleRes()),
            message = getString(permission.reasonRes()),
            positive = getString(R.string.cc_perm_grant),
            onPositive = { requestPermission(permission) },
            negative = getString(R.string.cc_perm_later),
            onNegative = ::onPromptDeclined,
            onCancel = ::onPromptDeclined
        ).also { handle -> handle.setOnDismissListener { if (permissionDialog === handle) permissionDialog = null } }
    }

    /** "Để sau" / Back ở hộp xin quyền hoặc hộp hướng dẫn mở khóa: kết thúc lượt hỏi; còn thiếu quyền bắt buộc thì switch tự tắt. */
    private fun onPromptDeclined() {
        stopPrompt()
    }

    /** Kết thúc lượt hỏi quyền. Còn chờ quyền bắt buộc (chưa cấp đủ) thì bỏ: switch về tắt, pref vẫn tắt. */
    private fun stopPrompt() {
        promptActive = false
        permissionDialog?.setOnDismissListener(null)
        permissionDialog?.dismiss()
        permissionDialog = null
        if (enablePending) {
            enablePending = false
            render()
        }
    }

    private fun commitEnabled() {
        enablePending = false
        lifecycleScope.launch {
            prefs.setEnabled(true)
            ControlCenterService.refresh(this@ControlCenterSettingsActivity)
        }
    }

    /** Đang bật mà thiếu quyền bắt buộc (vd người dùng thu hồi quyền ở màn hệ thống) thì tự tắt, để switch khớp thực tế. */
    private fun syncEnabledWithPermissions() {
        val c = config ?: return
        if (!c.enabled || enablePending || permissions.missingRequired().isEmpty()) return
        lifecycleScope.launch {
            prefs.setEnabled(false)
            ControlCenterService.refresh(this@ControlCenterSettingsActivity)
        }
    }

    private fun readExtras(intent: Intent?) {
        focusPending = permissionFrom(intent)
        recorderPending = intent?.getBooleanExtra(EXTRA_RECORDER, false) == true
    }

    private fun render() {
        val c = config ?: return
        loaded = true
        val missingRequired = permissions.missingRequired()
        val rows = buildList {
            add(SettingsRow.Header("cc_header_main", R.string.cc_title))
            add(
                SettingsRow.Switch(
                    ID_ENABLED, R.string.cc_enable, c.enabled || enablePending,
                    if (missingRequired.isEmpty()) R.string.cc_enable_desc else R.string.cc_enable_needs_permissions
                )
            )
            add(SettingsRow.Value(ID_TILES, R.string.cc_customize_tiles, getString(R.string.cc_tiles_count, c.tiles.size)))
            add(SettingsRow.Value(ID_EDGE, R.string.cc_edge_settings, getString(c.edge.position.labelRes())))
            add(SettingsRow.Switch(ID_VIBRATE, R.string.cc_vibrate, c.vibrateOnOpen))
            add(SettingsRow.Header("cc_header_look", R.string.cc_header_look))
            add(SettingsRow.Value(ID_BACKGROUND, R.string.cc_background, getString(c.background.labelRes())))
            add(SettingsRow.Switch(ID_BLUR, R.string.cc_blur, c.blurBackground))
            add(SettingsRow.Header("cc_header_features", R.string.cc_header_features))
            add(SettingsRow.Value(ID_NIGHT_SHIFT, R.string.cc_tile_night_shift, nightShiftSummary(c)))
            add(SettingsRow.Value(ID_RECORDER, R.string.cc_recorder_app, recorderLabel(c.recorderPackage)))
            add(SettingsRow.Header("cc_header_permissions", R.string.cc_header_permissions))
            permissions.applicable().forEach { p ->
                add(
                    SettingsRow.Value(
                        rowId(p),
                        p.titleRes(),
                        getString(if (permissions.isGranted(p)) R.string.cc_perm_granted else R.string.cc_perm_missing)
                    )
                )
            }
        }
        this.rows = rows
        val focus = focusPending ?: return
        focusPending = null
        val index = rows.indexOfFirst { it.id == rowId(focus) }
        if (index >= 0) lifecycleScope.launch { listState.animateScrollToItem(index) }
    }

    private fun nightShiftSummary(c: ControlCenterConfig): String = when {
        c.nightShift.enabled -> getString(R.string.cc_state_on)
        c.nightShift.scheduled -> getString(
            R.string.cc_night_shift_scheduled_summary,
            formatMinutes(c.nightShift.fromMinutes),
            formatMinutes(c.nightShift.toMinutes)
        )

        else -> getString(R.string.cc_state_off)
    }

    private fun recorderLabel(pkg: String?): String {
        if (pkg == null) return getString(R.string.cc_recorder_none)
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            getString(R.string.cc_recorder_none)
        }
    }

    private fun onValueClicked(row: SettingsRow.Value) {
        when (row.id) {
            ID_TILES -> startActivity(Intent(this, CustomizeTilesActivity::class.java))
            ID_EDGE -> startActivity(Intent(this, EdgeSettingsActivity::class.java))
            ID_NIGHT_SHIFT -> startActivity(Intent(this, NightShiftSettingsActivity::class.java))
            ID_BACKGROUND -> showBackgroundDialog()
            ID_RECORDER -> showRecorderDialog()
            else -> onPermissionClicked(row)
        }
    }

    private fun onPermissionClicked(row: SettingsRow.Value) {
        val permission = permissions.applicable().firstOrNull { rowId(it) == row.id } ?: return
        requestPermission(permission)
    }

    /** Bluetooth (API 31+) xin quyền runtime; còn lại mở màn hệ thống. */
    private fun requestPermission(permission: ControlPermission) {
        if (permission == ControlPermission.BLUETOOTH_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !permissions.isGranted(permission)
        ) {
            requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        openSystemScreen(permission)
    }

    /** RE mục 5.4 (`BackgroundActivity`): ảnh nền của launcher, ảnh tự chọn, hoặc không ảnh. */
    private fun showBackgroundDialog() {
        val c = config ?: return
        // 2026-11-26: tạm ẩn "Không ảnh" (máy không hỗ trợ blur xuyên cửa sổ thì nền không mờ). Mở lại: dùng BackgroundMode.entries.
        val modes = BackgroundMode.entries.filter { it != BackgroundMode.NONE }
        dialog?.dismiss()
        dialog = SettingsDialogs.singleChoice(
            this, getString(R.string.cc_background), modes.map { getString(it.labelRes()) }, modes.indexOf(c.background)
        ) { which ->
            val mode = modes[which]
            if (mode == BackgroundMode.CUSTOM_IMAGE) {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                lifecycleScope.launch { prefs.update { it.copy(background = mode) } }
            }
        }
    }

    /** RE mục 5.4 (`RecorderActivity`): chọn app ghi màn hình cho ô Ghi màn hình. */
    private fun showRecorderDialog() {
        lifecycleScope.launch {
            // P3-7: danh sách chọn app không được lộ app đã ẩn.
            val hidden = getHiddenApps().first().mapTo(HashSet()) { it.key }
            val list: List<AppItem> = apps.apps.first().filter { it.key !in hidden }.distinctBy { it.key.packageName }
            val current = config?.recorderPackage
            val labels = listOf(getString(R.string.cc_recorder_none)) + list.map { it.label }
            val checked = list.indexOfFirst { it.key.packageName == current }.let { if (it < 0) 0 else it + 1 }
            dialog?.dismiss()
            dialog = SettingsDialogs.singleChoice(
                this@ControlCenterSettingsActivity, getString(R.string.cc_recorder_app), labels, checked
            ) { which ->
                val pkg = if (which == 0) null else list[which - 1].key.packageName
                lifecycleScope.launch { prefs.update { it.copy(recorderPackage = pkg) } }
            }
        }
    }

    private fun openSystemScreen(permission: ControlPermission) {
        val opened = if (permission == ControlPermission.NOTIFICATION_LISTENER) {
            listenerFlow.open()
        } else {
            SystemIntents.startFirst(this, SystemIntents.forPermission(this, permission), newTask = false)
        }
        if (!opened) {
            Toast.makeText(this, R.string.cc_perm_open_failed, Toast.LENGTH_LONG).show()
            if (promptActive) stopPrompt() // không có màn hệ thống để quay lại: dừng lượt hỏi, khỏi kẹt switch ở trạng thái chờ
        }
    }

    private fun ControlPermission.titleRes(): Int = when (this) {
        ControlPermission.OVERLAY -> R.string.cc_perm_overlay
        ControlPermission.NOTIFICATION_LISTENER -> R.string.cc_perm_listener
        ControlPermission.BATTERY_OPTIMIZATION -> R.string.cc_perm_battery
        ControlPermission.WRITE_SETTINGS -> R.string.cc_perm_write_settings
        ControlPermission.DND_ACCESS -> R.string.cc_perm_dnd
        ControlPermission.BLUETOOTH_CONNECT -> R.string.cc_perm_bluetooth
    }

    /** Lý do cần quyền, cho hộp thoại xin quyền ([promptNextPermission]). */
    private fun ControlPermission.reasonRes(): Int = when (this) {
        ControlPermission.OVERLAY -> R.string.cc_perm_reason_overlay
        ControlPermission.NOTIFICATION_LISTENER -> R.string.cc_perm_reason_listener
        ControlPermission.BATTERY_OPTIMIZATION -> R.string.cc_perm_reason_battery
        ControlPermission.WRITE_SETTINGS -> R.string.cc_perm_reason_write_settings
        ControlPermission.DND_ACCESS -> R.string.cc_perm_reason_dnd
        ControlPermission.BLUETOOTH_CONNECT -> R.string.cc_perm_reason_bluetooth
    }

    companion object {
        private const val ID_ENABLED = "cc_enabled"
        private const val ID_TILES = "cc_tiles"
        private const val ID_EDGE = "cc_edge"
        private const val ID_VIBRATE = "cc_vibrate"
        private const val ID_BACKGROUND = "cc_background"
        private const val ID_BLUR = "cc_blur"
        private const val ID_NIGHT_SHIFT = "cc_night_shift"
        private const val ID_RECORDER = "cc_recorder"
        private const val EXTRA_PERMISSION = "gambi.global.os26launcher.controlcenter.PERMISSION"
        private const val EXTRA_RECORDER = "gambi.global.os26launcher.controlcenter.RECORDER"
        private const val STATE_PROMPT_ACTIVE = "cc_perm_prompt_active"
        private const val STATE_ENABLE_PENDING = "cc_enable_pending"
        private const val STATE_ASKED = "cc_perm_asked"

        private fun rowId(p: ControlPermission) = "cc_perm_${p.name.lowercase()}"

        private fun permissionFrom(intent: Intent?): ControlPermission? =
            intent?.getStringExtra(EXTRA_PERMISSION)?.let { name -> ControlPermission.entries.firstOrNull { it.name == name } }

        fun intent(context: Context, focus: ControlPermission? = null, focusRecorder: Boolean = false): Intent =
            Intent(context, ControlCenterSettingsActivity::class.java).apply {
                if (focus != null) putExtra(EXTRA_PERMISSION, focus.name)
                if (focusRecorder) putExtra(EXTRA_RECORDER, true)
            }
    }
}

/** "8:05" từ số phút trong ngày. */
internal fun formatMinutes(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

internal fun BackgroundMode.labelRes(): Int = when (this) {
    BackgroundMode.WALLPAPER -> R.string.cc_background_wallpaper
    BackgroundMode.CUSTOM_IMAGE -> R.string.cc_background_custom
    BackgroundMode.NONE -> R.string.cc_background_none
}

internal fun gambi.global.os26launcher.domain.model.controlcenter.EdgePosition.labelRes(): Int = when (this) {
    gambi.global.os26launcher.domain.model.controlcenter.EdgePosition.TOP_RIGHT -> R.string.cc_edge_top_right
    gambi.global.os26launcher.domain.model.controlcenter.EdgePosition.TOP_LEFT -> R.string.cc_edge_top_left
    gambi.global.os26launcher.domain.model.controlcenter.EdgePosition.RIGHT_SIDE -> R.string.cc_edge_right_side
    gambi.global.os26launcher.domain.model.controlcenter.EdgePosition.LEFT_SIDE -> R.string.cc_edge_left_side
}
