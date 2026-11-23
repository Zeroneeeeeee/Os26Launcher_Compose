package gambi.global.os26launcher.ui.settings

import androidx.annotation.StringRes
import gambi.global.os26launcher.R

/**
 * AB 6.2 — "Tải ứng dụng khác": danh sách tĩnh các app của nhà phát triển, mỗi hàng mở trang Play của gói tương ứng. Tên, gói và tên
 * nhà phát triển là chỗ giữ chỗ ở `about_config.xml` (FD5). Giữ kiểu hàng hiện tại (FD2): không biểu tượng, mô tả, điểm sao.
 * Khi `about_more_apps_enabled` = false hub ẩn hẳn mục này nên màn không bao giờ được mở.
 */
class MoreAppsSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private class MoreApp(val id: String, @StringRes val name: Int, @StringRes val packageName: Int)

    private val activity = ctx.activity

    override val titleRes: Int = R.string.settings_more_apps_title // HC Task 3: Figma 6.2 "Ứng dụng khác"

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val download = activity.getString(R.string.about_more_apps_download)
        val rows = ArrayList<SettingsRow>()
        rows.add(SettingsRow.Header("about_more_apps_featured_header", R.string.about_more_apps_featured))
        rows.add(SettingsRow.Value(FEATURED.id, FEATURED.name, download))
        rows.add(SettingsRow.Header("about_more_apps_others_header", R.string.about_more_apps_others))
        for (app in OTHERS) rows.add(SettingsRow.Value(app.id, app.name, download))
        rows.add(
            SettingsRow.Footer(
                "about_more_apps_footer",
                activity.getString(R.string.about_more_apps_footer, activity.getString(R.string.about_developer_name))
            )
        )
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) {
        val app = (OTHERS + FEATURED).firstOrNull { it.id == row.id } ?: return
        AboutLinks.openPlay(activity, activity.getString(app.packageName))
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    private companion object {
        val FEATURED = MoreApp("more_app_0", R.string.about_more_app_0_name, R.string.about_more_app_0_package)
        val OTHERS = listOf(
            MoreApp("more_app_1", R.string.about_more_app_1_name, R.string.about_more_app_1_package),
            MoreApp("more_app_2", R.string.about_more_app_2_name, R.string.about_more_app_2_package),
            MoreApp("more_app_3", R.string.about_more_app_3_name, R.string.about_more_app_3_package),
            MoreApp("more_app_4", R.string.about_more_app_4_name, R.string.about_more_app_4_package),
            MoreApp("more_app_5", R.string.about_more_app_5_name, R.string.about_more_app_5_package)
        )
    }
}
