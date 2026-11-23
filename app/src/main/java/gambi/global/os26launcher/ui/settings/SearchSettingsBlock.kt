package gambi.global.os26launcher.ui.settings

import androidx.activity.ComponentActivity
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.SearchConfig

/**
 * M8d Task 6 — khối "Tìm kiếm" ở CUỐI màn Cài đặt. Tự đứng được: [rows] nối vào danh sách, [handlesValue]/[handlesSwitch] cho Activity
 * biết hàng nào của khối, và một thể hiện của lớp này giữ hộp thoại đang mở (tối đa một) để Activity gọi [dismiss] ở `onDestroy`
 * (không rò cửa sổ khi xoay màn hình, chạm đúp không chồng hai hộp). Khi G2d dời Cài đặt sang dashboard, dời nguyên file này.
 */
class SearchSettingsBlock {

    private var dialog: DsDialogHandle? = null

    /** Gọi ở `onDestroy` của Activity. */
    fun dismiss() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    fun onValueClicked(
        context: ComponentActivity,
        viewModel: SearchSettingsViewModel,
        row: SettingsRow.Value
    ) {
        when (row.id) {
            SettingsRow.ID_SEARCH_CLEAR -> show {
                SettingsDialogs.confirm(
                    context,
                    context.getString(R.string.settings_search_clear),
                    context.getString(R.string.settings_search_clear_message),
                    context.getString(R.string.settings_search_clear_confirm),
                    onPositive = { viewModel.clearHistory() }
                )
            }
        }
    }

    /** P3-5: các công tắc của khối này ghi thẳng, không còn hộp xác nhận (trước đây tắt "Gợi ý" thì hỏi vì xóa lịch sử). */
    fun onSwitch(
        viewModel: SearchSettingsViewModel,
        row: SettingsRow.Switch,
        checked: Boolean
    ) {
        when (row.id) {
            SettingsRow.ID_SEARCH_SETTINGS -> viewModel.setSystemSettings(checked)
            // PB: Lối tắt và Danh bạ cần hỏi quyền nên do SearchSettingsScreen xử lý (có Activity, launcher quyền).
            // P3-5: tắt không còn xóa gì nên không cần hộp xác nhận.
            SettingsRow.ID_SEARCH_RECENT_APPS -> viewModel.setRecentApps(checked)
            SettingsRow.ID_SEARCH_SUGGESTIONS -> viewModel.setSuggestions(checked)
        }
    }

    private fun show(create: () -> DsDialogHandle) {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss() // chạm đúp: chỉ giữ một hộp thoại
        dialog = create().also { d -> d.setOnDismissListener { dialog = null } }
    }

    companion object {
        fun rows(
            prefs: SearchConfig,
            isDefaultLauncher: Boolean,
            contactsGranted: Boolean
        ): List<SettingsRow> {
            val rows = ArrayList<SettingsRow>()
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_SEARCH_SETTINGS,
                    R.string.settings_search_system,
                    prefs.systemSettings
                )
            )
            // PB D2: Lối tắt chỉ đọc được khi là launcher mặc định, danh bạ cần quyền READ_CONTACTS. Công tắc hiện bật khi đã bật VÀ đủ
            // điều kiện; bật lên thì màn hỏi ngay (không còn hàng gợi ý bên dưới), người dùng từ chối thì công tắc tự về tắt.
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_SEARCH_SHORTCUTS,
                    R.string.settings_search_shortcuts,
                    prefs.shortcuts && isDefaultLauncher
                )
            )
            // G3a: tìm danh bạ. Tắt công tắc chỉ ngừng đọc, không thu hồi quyền hệ thống.
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_SEARCH_CONTACTS,
                    R.string.settings_search_contacts,
                    prefs.contacts && contactsGranted,
                    R.string.settings_search_contacts_description
                )
            )
            // P3-5: tách "Gợi ý và app gần đây" thành hai công tắc độc lập.
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_SEARCH_RECENT_APPS,
                    R.string.settings_search_recent_apps,
                    prefs.recentApps,
                    R.string.settings_search_recent_apps_description
                )
            )
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_SEARCH_SUGGESTIONS,
                    R.string.settings_search_suggestions,
                    prefs.suggestions,
                    R.string.settings_search_suggestions_description
                )
            )
            rows.add(
                SettingsRow.Value(
                    SettingsRow.ID_SEARCH_CLEAR,
                    R.string.settings_search_clear,
                    ""
                )
            )
            return rows
        }

        fun handlesValue(id: String): Boolean = id == SettingsRow.ID_SEARCH_CLEAR

        /** PB: không gồm Lối tắt và Danh bạ (SearchSettingsScreen tự xử lý vì phải hỏi quyền). */
        fun handlesSwitch(id: String): Boolean =
            id == SettingsRow.ID_SEARCH_SETTINGS || id == SettingsRow.ID_SEARCH_SUGGESTIONS || id == SettingsRow.ID_SEARCH_RECENT_APPS
    }
}
