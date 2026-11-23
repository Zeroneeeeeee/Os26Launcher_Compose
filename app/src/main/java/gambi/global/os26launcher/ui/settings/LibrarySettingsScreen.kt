package gambi.global.os26launcher.ui.settings

import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import gambi.global.os26launcher.domain.config.LibraryStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * LO — màn "Thư viện ứng dụng" (3.2, Figma `302:2334`): công tắc bật thư viện; kiểu hiển thị (Thư mục danh mục / Danh sách A–Z);
 * Tự phân loại và Gợi ý & Mới thêm (chỉ áp cho kiểu thư mục). Nhóm "Ứng dụng mới cài" không làm (plan LO, D1).
 *
 * Thư viện tắt: ẩn mọi phần dưới công tắc (D2). Kiểu A–Z: ẩn hai công tắc chỉ dành cho kiểu thư mục (D4). Ghi qua [HomeTogglesViewModel];
 * Home áp ngay (`HomeTogglesController`, `GetCategoryGroupsUseCase`), không cần dựng lại.
 */
class LibrarySettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val toggles = ViewModelProvider(activity)[HomeTogglesViewModel::class.java]
    private var current = HomeScreenConfig()

    override val titleRes: Int = R.string.settings_hub_library

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>()
        rows.add(
            SettingsRow.Switch(
                SettingsRow.ID_LO_ENABLED,
                R.string.lo_enable,
                current.libraryEnabled,
                R.string.lo_enable_description,
                tile = SettingsTile(R.drawable.ds_ic_app_library, DsTileColor.BLUE) // Figma 3.2: ô icon thư viện
            )
        )
        if (!current.libraryEnabled) return rows
        rows.add(SettingsRow.Header(ID_STYLE_HEADER, R.string.lo_style_header))
        rows.add(
            SettingsRow.LibraryStyle(
                SettingsRow.ID_LO_STYLE,
                if (current.libraryStyle == LibraryStyle.ALPHABETICAL) 1 else 0
            )
        )
        // LO D9 (chủ dự án 2026-11-18): "Tự phân loại" và "Gợi ý & Mới thêm" tạm ẩn (chưa ổn); bật lại = đổi cờ dưới.
        if (SHOW_FOLDER_SWITCHES && current.libraryStyle == LibraryStyle.FOLDERS) {
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_LO_AUTO_CATEGORIZE,
                    R.string.lo_auto_categorize,
                    current.libraryAutoCategorize,
                    R.string.lo_auto_categorize_description
                )
            )
            rows.add(
                SettingsRow.Switch(
                    SettingsRow.ID_LO_SUGGESTIONS,
                    R.string.lo_suggestions,
                    current.librarySuggestions,
                    R.string.lo_suggestions_description
                )
            )
        }
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) {}

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        when (row.id) {
            SettingsRow.ID_LO_ENABLED -> toggles.setLibraryEnabled(checked)
            SettingsRow.ID_LO_AUTO_CATEGORIZE -> toggles.setLibraryAutoCategorize(checked)
            SettingsRow.ID_LO_SUGGESTIONS -> toggles.setLibrarySuggestions(checked)
        }
    }

    override fun onLibraryStyleClicked(row: SettingsRow.LibraryStyle, index: Int) {
        toggles.setLibraryStyle(if (index == 1) LibraryStyle.ALPHABETICAL else LibraryStyle.FOLDERS)
    }

    override suspend fun observe() {
        coroutineScope {
            launch { // lưu thất bại -> Toast và dựng lại hàng từ trạng thái thật
                toggles.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
            launch {
                toggles.uiState.collect {
                    current = it
                    ctx.rerender(false)
                }
            }
        }
    }

    private companion object {
        const val ID_STYLE_HEADER = "lo_style_header"

        /** LO D9: cùng cờ với `GetCategoryGroupsUseCase.FOLDER_SWITCHES_ENABLED`, bật lại thì đổi cả hai. */
        const val SHOW_FOLDER_SWITCHES = false
    }
}
