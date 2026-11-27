package gambi.global.os26launcher.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.SearchConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import gambi.global.os26launcher.core.permission.PermissionKind
import gambi.global.os26launcher.core.permission.PermissionRequester

/**
 * G2d Task 5 — màn "Cài đặt tìm kiếm" (3.3): cả khối [SearchSettingsBlock] của M8d/G3a. Có ViewModel riêng
 * ([SearchSettingsViewModel]), đọc lại quyền Danh bạ ở `onResume`. PB: bật Lối tắt mở thẳng luồng [DefaultLauncherAction] của khung,
 * bật Danh bạ mở thẳng hộp quyền (thay hai hàng gợi ý cũ dưới công tắc).
 */
class SearchSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val searchSettings = ViewModelProvider(activity)[SearchSettingsViewModel::class.java] // M8d Task 6
    private val searchBlock = SearchSettingsBlock()
    private val homeToggles = ViewModelProvider(activity)[HomeTogglesViewModel::class.java] // HT
    private var toggles = HomeScreenConfig() // HT
    private var searchPrefs = SearchConfig()
    private var contactsGranted =
        false // G3a: đọc lại ở onResume (người dùng có thể vừa cấp/thu hồi quyền ở Cài đặt hệ thống)

    // PB D2: vừa bật Lối tắt và mở bộ chọn launcher (hoặc vừa mở Thông tin ứng dụng cho Danh bạ): lần resume tới bind lại hàng
    // (công tắc đã lật trên màn mà dữ liệu có thể không đổi) và báo khi vẫn chưa đủ điều kiện.
    private var awaitingDefault = false
    private var awaitingContactsSettings = false

    // PB D2: hộp quyền Danh bạ mở thẳng khi bật công tắc. PM (2026-11-26): luật xin quyền chung — bị chặn thì hộp "Cấp quyền" (thay mở thẳng
    // Thông tin ứng dụng). Dựng lúc dựng màn (công thức G2d, trước STARTED).
    private val contactsPermission = PermissionRequester(activity, PermissionKind.CONTACTS)

    override val titleRes: Int = R.string.settings_hub_search

    override fun rows(state: SettingsUiState): List<SettingsRow> =
        openRows() + SearchSettingsBlock.rows(searchPrefs, state.isDefaultLauncher, contactsGranted)

    /** HT: nhóm "Cách mở" (hai công tắc) + Header của nhóm "Tìm trong" đứng sau nó (khối cũ không còn Header đầu từ G2d). */
    private fun openRows(): List<SettingsRow> = listOf(
        SettingsRow.Header(SettingsRow.ID_HT_OPEN_HEADER, R.string.ht_search_open_header),
        SettingsRow.Switch(
            SettingsRow.ID_HT_SWIPE_DOWN,
            R.string.ht_search_swipe_down,
            toggles.swipeDownSearch,
            R.string.ht_search_swipe_down_description
        ),
        SettingsRow.Switch(
            SettingsRow.ID_HT_SEARCH_PILL,
            R.string.ht_search_pill,
            toggles.searchPill,
            R.string.ht_search_pill_description
        ),
        SettingsRow.Header(SettingsRow.ID_HT_SEARCH_IN_HEADER, R.string.ht_search_in_header)
    )

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        when (row.id) { // HT
            SettingsRow.ID_HT_SWIPE_DOWN -> homeToggles.setSwipeDownSearch(checked)
            SettingsRow.ID_HT_SEARCH_PILL -> homeToggles.setSearchPill(checked)
        }
        when (row.id) { // PB D2: bật là hỏi ngay
            // PG (2026-11-27): chỉ ghi bật khi đã đủ điều kiện; không cấp thì pref về tắt (không âm thầm bật lại khi sau này được cấp ở chỗ khác).
            SettingsRow.ID_SEARCH_SHORTCUTS -> {
                searchSettings.setShortcuts(checked)
                if (checked && !ctx.viewModel.isDefaultLauncher()) {
                    awaitingDefault = true
                    ctx.defaultLauncher.request()
                }
            }
            SettingsRow.ID_SEARCH_CONTACTS -> {
                if (!checked || contactsGranted) {
                    searchSettings.setContacts(checked)
                } else {
                    contactsPermission.request(onOpenSettings = { awaitingContactsSettings = true }) { granted ->
                        contactsGranted = granted
                        // Bị chặn: kết quả false đến ngay khi hiện hộp "Cấp quyền"; cấp ở Thông tin ứng dụng thì onResume ghi bật.
                        searchSettings.setContacts(granted, asked = true)
                        ctx.rerender(true)
                    }
                }
            }
        }
        if (SearchSettingsBlock.handlesSwitch(row.id)) { // M8d Task 6
            searchBlock.onSwitch(searchSettings, row, checked)
        }
    }

    override fun onValueClicked(row: SettingsRow.Value) {
        if (SearchSettingsBlock.handlesValue(row.id)) { // M8d Task 6
            searchBlock.onValueClicked(activity, searchSettings, row)
        }
    }

    override fun onResume() {
        contactsGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (awaitingDefault || awaitingContactsSettings) {
            val stillNotDefault = awaitingDefault && !ctx.viewModel.isDefaultLauncher()
            if (awaitingContactsSettings && contactsGranted) searchSettings.setContacts(true, asked = true) // PG: vừa cấp ở Thông tin ứng dụng
            awaitingDefault = false
            awaitingContactsSettings = false
            if (stillNotDefault) {
                searchSettings.setShortcuts(false) // PG: không đặt làm launcher mặc định thì Lối tắt không bật
                Toast.makeText(activity, R.string.settings_search_shortcuts_need_default, Toast.LENGTH_LONG).show()
            }
            // Khung dựng lại ngay sau onResume nhưng không ép bind: hoãn một nhịp để công tắc đã lật về đúng trạng thái thật.
            activity.window.decorView.post { ctx.rerender(true) }
        }
    }

    override suspend fun observe() {
        coroutineScope {
            launch { // M8d Task 6: lưu thất bại -> Toast và dựng lại hàng từ trạng thái thật
                searchSettings.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
            launch { // HT: lưu công tắc thất bại -> Toast và dựng lại hàng từ trạng thái thật
                homeToggles.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
            launch { // HT: công tắc Cách mở đổi -> dựng lại danh sách
                homeToggles.uiState.collect {
                    toggles = it
                    ctx.rerender(false)
                }
            }
            launch { // M8d Task 6: khối Tìm kiếm có trạng thái riêng, dựng lại danh sách khi nó đổi
                searchSettings.uiState.collect { prefs ->
                    searchPrefs = prefs
                    ctx.rerender(false)
                }
            }
        }
    }

    override fun onDestroy() {
        searchBlock.dismiss() // M8d Task 6
        contactsPermission.dismissDialog() // PM
    }
}
