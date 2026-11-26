package gambi.global.os26launcher.ui.settings

import android.net.Uri
import gambi.global.os26launcher.R
import androidx.core.net.toUri

/**
 * AB 6.3 — "Chính sách bảo mật". Chỉ mở trang chính sách bằng trình duyệt của máy (quyết định AB5): không WebView, không thư viện,
 * trình duyệt tự lo mạng và offline. Nội dung chính sách do trang web quyết định, đổi chính sách không phải build lại app.
 */
class PrivacySettingsScreen(ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity

    override val titleRes: Int = R.string.settings_hub_privacy

    override fun rows(state: SettingsUiState): List<SettingsRow> = listOf(
        SettingsRow.Value(
            ID_OPEN,
            R.string.about_privacy_open,
	        activity.getString(R.string.about_privacy_url).toUri().host.orEmpty()
        ),
        SettingsRow.Footer(ID_FOOTER, activity.getString(R.string.about_privacy_footer))
    )

    override fun onValueClicked(row: SettingsRow.Value) {
        if (row.id == ID_OPEN) AboutLinks.openUrl(activity, activity.getString(R.string.about_privacy_url))
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    private companion object {
        const val ID_OPEN = "about_privacy_open"
        const val ID_FOOTER = "about_privacy_footer"
    }
}
