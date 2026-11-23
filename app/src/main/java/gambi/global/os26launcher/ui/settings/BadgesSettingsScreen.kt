package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.domain.config.BadgeStyle
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission
import gambi.global.os26launcher.ui.controlcenter.SystemIntents
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * BG — màn "Huy hiệu thông báo" (3.4, Figma 302:2459). Lưu ở `HomeScreenConfig` (badgesEnabled, badgeStyle, badgeDisabledApps); Home
 * vẽ huy hiệu theo số thông báo `ControlCenterService` đếm được.
 *
 * Card xin quyền chỉ hiện khi chưa cấp "Truy cập thông báo"; nút mở màn quyền của hệ thống, quay lại thì [onResume] đọc lại.
 * "Theo ứng dụng" là mọi app thật (mỗi gói + hồ sơ một hàng), mặc định bật.
 */
class BadgesSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[BadgesSettingsViewModel::class.java]
    private var config = HomeScreenConfig()
    private var apps: List<AppItem> = emptyList()
    private var granted = viewModel.isListenerGranted()

    override val titleRes: Int = R.string.settings_hub_badges

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>(apps.size + 6)
        if (!granted) {
            rows.add(
                SettingsRow.PermissionCard(
                    ID_PERMISSION,
                    R.drawable.ds_ic_badge,
                    R.string.badges_perm_title,
                    R.string.badges_perm_desc,
                    R.string.badges_perm_action
                )
            )
        }
        rows.add(
            SettingsRow.Switch(
                ID_SHOW,
                R.string.badges_show,
                config.badgesEnabled,
                tile = SettingsTile(R.drawable.ds_ic_badge, DsTileColor.RED)
            )
        )
        rows.add(SettingsRow.Header(ID_STYLE_HEADER, R.string.badges_style_header))
        rows.add(SettingsRow.BadgeStyle(ID_STYLE, if (config.badgeStyle == BadgeStyle.DOT) STYLE_DOT else STYLE_NUMBER))
        if (apps.isNotEmpty()) {
            rows.add(SettingsRow.Header(ID_PER_APP_HEADER, R.string.badges_per_app_header))
            for (app in apps) {
                val target = BadgesSettingsViewModel.targetOf(app)
                rows.add(SettingsRow.AppSwitch(ID_APP_PREFIX + target.storageKey, app, target.storageKey !in config.badgeDisabledApps))
            }
        }
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        if (row.id == ID_SHOW) viewModel.setEnabled(checked)
    }

    override fun onAppSwitchChanged(row: SettingsRow.AppSwitch, checked: Boolean) = viewModel.setAppEnabled(row.app, checked)

    override fun onBadgeStyleClicked(row: SettingsRow.BadgeStyle, index: Int) =
        viewModel.setStyle(if (index == STYLE_DOT) BadgeStyle.DOT else BadgeStyle.NUMBER)

    override fun onPermissionCardClicked(row: SettingsRow.PermissionCard) {
        val intents = SystemIntents.forPermission(activity, ControlPermission.NOTIFICATION_LISTENER)
        if (!SystemIntents.startFirst(activity, intents, newTask = false)) {
            Toast.makeText(activity, R.string.badges_perm_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    override val appIconLoader: (suspend (ComponentKey, Int) -> Bitmap?)? = viewModel::loadIcon

    override fun onResume() {
        granted = viewModel.isListenerGranted() // người dùng có thể vừa cấp/thu hồi ở Cài đặt hệ thống
    }

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.config.collect {
                    config = it
                    ctx.rerender(false)
                }
            }
            launch {
                viewModel.apps.collect { list ->
                    if (list != null) {
                        apps = list
                        ctx.rerender(false)
                    }
                }
            }
            launch { // lưu thất bại -> Toast và dựng lại hàng từ trạng thái thật
                viewModel.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
        }
    }

    private companion object {
        const val STYLE_NUMBER = 0
        const val STYLE_DOT = 1
        const val ID_PERMISSION = "badges_permission"
        const val ID_SHOW = "badges_show"
        const val ID_STYLE_HEADER = "badges_style_header"
        const val ID_STYLE = "badges_style"
        const val ID_PER_APP_HEADER = "badges_per_app_header"
        const val ID_APP_PREFIX = "badges_app:"
    }
}
