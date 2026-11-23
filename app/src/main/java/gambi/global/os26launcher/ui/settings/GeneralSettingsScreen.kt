package gambi.global.os26launcher.ui.settings

import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.GridPreset

/** G2d Task 3 — màn "Chung" (1.2): cỡ lưới, hiện nhãn ứng dụng; PB Task 4: ẩn thanh điều hướng. */
class GeneralSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ctx.viewModel

    private var gridDialog: DsDialogHandle? =
        null // hộp thoại chọn preset HOẶC hộp thoại xác nhận (lần lượt, không cùng lúc)

    override val titleRes: Int = R.string.settings_hub_general

    override fun rows(state: SettingsUiState): List<SettingsRow> = listOf(
        SettingsRow.Value(
            SettingsRow.ID_GRID_PRESET,
            R.string.settings_grid_preset,
            state.gridPreset.shortLabel(),
            enabled = !state.applyingGrid
        ),
        SettingsRow.Switch(
            SettingsRow.ID_SHOW_LABELS,
            R.string.settings_show_labels,
            state.showLabels
        ),
        SettingsRow.Switch(
            SettingsRow.ID_NAV_BAR_HIDDEN,
            R.string.settings_nav_bar_hidden,
            state.navBarHidden,
            R.string.settings_nav_bar_hidden_description
        )
    )

    override fun onValueClicked(row: SettingsRow.Value) {
        if (row.id == SettingsRow.ID_GRID_PRESET) showGridPresetDialog()
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        when (row.id) {
            SettingsRow.ID_SHOW_LABELS -> viewModel.onShowLabelsChanged(checked)
            SettingsRow.ID_NAV_BAR_HIDDEN -> viewModel.onNavBarHiddenChanged(checked) // PB Task 4
        }
    }

    override fun onDestroy() {
        gridDialog?.setOnDismissListener(null)
        gridDialog?.dismiss()
        gridDialog = null
    }

    private fun GridPreset.shortLabel(): String =
        activity.getString(R.string.settings_grid_value, columns, rows)

    private fun showGridPresetDialog() {
        if (gridDialog != null) return
        val options = GridPreset.entries
        val labels = options.map {
            activity.getString(
                R.string.settings_grid_option,
                it.columns,
                it.rows,
                it.capacity
            )
        }
        val current = viewModel.uiState.value.gridPreset
        gridDialog = SettingsDialogs.singleChoice(
            activity, activity.getString(R.string.settings_grid_preset), labels, options.indexOf(current)
        ) { which ->
            val chosen = options[which]
            // Dismiss listener chạy sau (qua Handler): gỡ trước để nó không xóa tham chiếu hộp xác nhận vừa gán.
            gridDialog?.setOnDismissListener(null)
            gridDialog = null
            if (chosen != current) showGridConfirmDialog(chosen)
        }.also { d -> d.setOnDismissListener { gridDialog = null } }
    }

    /** Đổi cỡ lưới xếp lại icon và không hoàn tác được (quyết định D3): luôn hỏi xác nhận. */
    private fun showGridConfirmDialog(preset: GridPreset) {
        gridDialog = SettingsDialogs.confirm(
            activity,
            activity.getString(R.string.settings_grid_confirm_title, preset.columns, preset.rows),
            activity.getString(R.string.settings_grid_confirm_message),
            activity.getString(R.string.settings_grid_confirm_apply),
            onPositive = { viewModel.onGridPresetConfirmed(preset) }
        ).also { d -> d.setOnDismissListener { gridDialog = null } }
    }
}
