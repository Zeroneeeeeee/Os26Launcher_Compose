package gambi.global.os26launcher.ui.settings

import android.content.Intent
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.GlassElement
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * G2d Task 3 — màn "Liquid Glass" (2.1): mức kính + khối kính tùy chỉnh (chỉ khi mức = Tùy chỉnh) + hộp cảnh báo kính thật trên
 * máy API < 33. Chuyển nguyên từ màn tạm, không đổi hành vi.
 */
class LiquidGlassSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ctx.viewModel

    private var glassDialog: DsDialogHandle? = null
    private var glassWarnDialog: DsDialogHandle? = null // GS: cảnh báo kính thật trên máy API < 33

    override val titleRes: Int = R.string.settings_hub_glass

    // PG1 F4 (chủ dự án 2026-11-23): "Mức kính" và khối công tắc kính từng phần tử (GS) chỉ điều khiển kính View cũ; kính Compose không đọc
    // (JX Q3, không chỗ nào ngoài ui/settings gọi `GlassEnvironment.modeFor`), nên ẩn khỏi danh sách để không có nút vô tác dụng. Khóa config và
    // code đọc/ghi giữ nguyên (`config-schema.md`). Trang chủ Cài đặt mở thẳng `GlassTuningActivity`; màn này chỉ còn để không gãy đăng ký.
    override fun rows(state: SettingsUiState): List<SettingsRow> = listOf(
        // GT: mở màn Tinh chỉnh kính (thanh trượt + xem trước); giá trị bên phải cho biết đang ở mặc định hay đã chỉnh
        SettingsRow.Value(
            SettingsRow.ID_GLASS_LOOK,
            R.string.gt_title,
            activity.getString(if (state.glassLook == GlassLook.DEFAULT) R.string.gt_state_default else R.string.gt_state_custom)
        )
    )

    override fun onValueClicked(row: SettingsRow.Value) {
        when (row.id) {
            SettingsRow.ID_GLASS_LEVEL -> showGlassDialog()
            SettingsRow.ID_GLASS_LOOK -> activity.startActivity(Intent(activity, GlassTuningActivity::class.java)) // GT
        }
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        glassElementOf(row.id)?.let { onGlassCustomSwitched(it, checked) } // GS
    }

    override fun onDestroy() {
        // Gỡ listener trước: dismiss() sẽ gọi onDismiss, không cần làm gì thêm lúc Activity đang hủy.
        glassDialog?.setOnDismissListener(null)
        glassDialog?.dismiss() // tránh rò cửa sổ khi xoay màn hình
        glassDialog = null
        glassWarnDialog?.setOnDismissListener(null) // GS
        glassWarnDialog?.dismiss()
        glassWarnDialog = null
    }

    // GS: khối "Kính tùy chỉnh" nằm sau hàng mức kính, chỉ hiện khi Mức kính = Tùy chỉnh.
    private fun buildGlassCustomRows(state: SettingsUiState): List<SettingsRow> {
        if (state.glassOverride != GlassLevelOverride.CUSTOM) return emptyList()
        val c = state.glassCustom
        return listOf(
            SettingsRow.Header("header_glass_custom", R.string.gs_custom_header),
            SettingsRow.Switch(SettingsRow.ID_GLASS_CUSTOM_DOCK, R.string.gs_custom_dock, c.dock),
            SettingsRow.Switch(
                SettingsRow.ID_GLASS_CUSTOM_PANELS,
                R.string.gs_custom_panels,
                c.panels
            ),
            SettingsRow.Switch(
                SettingsRow.ID_GLASS_CUSTOM_SEARCH_BAR,
                R.string.gs_custom_search_bar,
                c.searchBar
            ),
            SettingsRow.Switch(SettingsRow.ID_GLASS_CUSTOM_PILL, R.string.gs_custom_pill, c.pill),
            // SM: khối riêng cho menu nhấn giữ icon, cùng nghĩa (bật = kính thật, tắt = kính mờ).
            SettingsRow.Header("header_glass_custom_menu", R.string.gs_custom_menu_header),
            SettingsRow.Switch(
                SettingsRow.ID_GLASS_CUSTOM_ICON_MENU,
                R.string.gs_custom_icon_menu,
                c.iconMenu
            )
        )
    }

    private fun glassElementOf(rowId: String): GlassElement? = when (rowId) {
        SettingsRow.ID_GLASS_CUSTOM_DOCK -> GlassElement.DOCK
        SettingsRow.ID_GLASS_CUSTOM_PANELS -> GlassElement.PANELS
        SettingsRow.ID_GLASS_CUSTOM_SEARCH_BAR -> GlassElement.SEARCH_BAR
        SettingsRow.ID_GLASS_CUSTOM_PILL -> GlassElement.PILL
        SettingsRow.ID_GLASS_CUSTOM_ICON_MENU -> GlassElement.ICON_MENU
        else -> null
    }

    /** Bật khi máy không có đường kính thật (API < 33): hỏi trước; Hủy/đóng thì công tắc về tắt (dựng lại hàng từ trạng thái thật). */
    private fun onGlassCustomSwitched(element: GlassElement, on: Boolean) {
        if (on && !viewModel.uiState.value.realGlassCapable) {
            showGlassWarnDialog(element)
        } else {
            viewModel.onGlassCustomToggled(element, on)
        }
    }

    private fun showGlassWarnDialog(element: GlassElement) {
        if (glassWarnDialog != null) return
        var confirmed = false
        glassWarnDialog = SettingsDialogs.confirm(
            activity,
            activity.getString(R.string.gs_warn_title),
            activity.getString(R.string.gs_warn_message),
            activity.getString(R.string.gs_warn_enable),
            onPositive = {
                confirmed = true
                viewModel.onGlassCustomToggled(element, true)
            }
        ).also { d ->
            d.setOnDismissListener {
                glassWarnDialog = null
                if (!confirmed) ctx.rerender(true)
            }
        }
    }

    private fun GlassLevelOverride.shortLabelRes(): Int = when (this) {
        GlassLevelOverride.AUTO -> R.string.settings_glass_auto
        GlassLevelOverride.FORCE_LOW -> R.string.settings_glass_low
        GlassLevelOverride.FORCE_HIGH -> R.string.settings_glass_high
        GlassLevelOverride.CUSTOM -> R.string.gs_level_custom // GS
    }

    private fun showGlassDialog() {
        if (glassDialog != null) return
        val options = GlassLevelOverride.entries
        val labels = listOf<CharSequence>(
            activity.getString(R.string.settings_glass_auto_desc),
            activity.getString(R.string.settings_glass_low_desc),
            activity.getString(R.string.settings_glass_high_desc),
            activity.getString(R.string.gs_level_custom_desc) // GS
        )
        val current = viewModel.uiState.value.glassOverride
        // RS: Figma 2.1 không vẽ chọn mức kính, giữ hộp chọn (kiểu v3).
        glassDialog = SettingsDialogs.singleChoice(
            activity, activity.getString(R.string.settings_glass_level), labels, options.indexOf(current)
        ) { which ->
            // Chọn đúng mức đang dùng thì không ghi (Home cũng không bị tạo lại vô ích).
            if (options[which] != current) {
                if (options[which] == GlassLevelOverride.CUSTOM) viewModel.onGlassCustomChosen() // GS: gieo công tắc nếu chưa có
                else viewModel.onGlassOverrideChosen(options[which])
            }
        }.also { d -> d.setOnDismissListener { glassDialog = null } }
    }
}
