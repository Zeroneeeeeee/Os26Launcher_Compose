package gambi.global.os26launcher.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppGroupKind
import gambi.global.os26launcher.domain.model.GridPreset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * G2d Task 3 — màn "Chung" (1.2): cỡ lưới, hiện nhãn ứng dụng; PB Task 4: ẩn thanh điều hướng.
 * AG (2026-11-26): mục "Gom nhóm ứng dụng", mỗi [AppGroupKind] một hàng (tên + số app); bấm mở [AppGroupSheet] xem trước rồi gom.
 */
class GeneralSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ctx.viewModel

    private var gridDialog: DsDialogHandle? =
        null // hộp thoại chọn preset HOẶC hộp thoại xác nhận (lần lượt, không cùng lúc)

    // AG: tên nhóm đọc bằng Activity (đúng ngôn ngữ app) rồi đưa ViewModel để so tên thư mục đã có.
    private val groupsViewModel = ViewModelProvider(activity)[AppGroupsViewModel::class.java].also { vm ->
        vm.setTitles(AppGroupKind.entries.associateWith { folderTitle(it) })
    }
    private var groups: List<AppGroupsViewModel.AppGroupState>? = null
    private var groupSheet: AppGroupSheet? = null

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
    ) + groupRows()

    override fun onValueClicked(row: SettingsRow.Value) {
        if (row.id == SettingsRow.ID_GRID_PRESET) showGridPresetDialog()
        if (row.id.startsWith(ID_GROUP_PREFIX)) openGroupSheet(row.id.removePrefix(ID_GROUP_PREFIX))
    }

    override suspend fun observe() {
        coroutineScope {
            launch {
                groupsViewModel.groups.collect { list ->
                    if (list != null) {
                        groups = list
                        ctx.rerender(false)
                    }
                }
            }
            launch {
                groupsViewModel.results.collect { r ->
                    val text = when (r) {
                        is AppGroupsViewModel.GroupResult.Done ->
                            activity.resources.getQuantityString(R.plurals.app_groups_done, r.count, r.count, r.title)
                        AppGroupsViewModel.GroupResult.Failed -> activity.getString(R.string.app_groups_failed)
                    }
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** AG: tiêu đề mục + một hàng mỗi nhóm + dòng mô tả. Chưa nạp xong bố cục thì chưa có mục này. */
    private fun groupRows(): List<SettingsRow> {
        val list = groups ?: return emptyList()
        val rows = ArrayList<SettingsRow>(list.size + 2)
        rows.add(SettingsRow.Header(ID_GROUP_HEADER, R.string.app_groups_header))
        // Máy Google (Pixel): app của hãng chính là nhóm "Google" (cùng tên thư mục), bỏ hàng thứ hai.
        val shown = if (brandName().equals(activity.getString(R.string.app_group_google), ignoreCase = true)) {
            list.filter { it.kind != AppGroupKind.MANUFACTURER }
        } else {
            list
        }
        shown.mapTo(rows) { g ->
            val value = if (!g.enabled && g.folderId != null) {
                activity.getString(R.string.app_groups_already)
            } else {
                activity.resources.getQuantityString(R.plurals.app_groups_count, g.apps.size, g.apps.size)
            }
            SettingsRow.Value(ID_GROUP_PREFIX + g.kind.name, 0, value, enabled = g.enabled, titleText = rowTitle(g))
        }
        rows.add(SettingsRow.Footer(ID_GROUP_FOOTER, activity.getString(R.string.app_groups_footer)))
        return rows
    }

    private fun openGroupSheet(kindName: String) {
        if (groupSheet != null) return // một sheet mỗi lúc (chống chạm đúp)
        val state = groups?.firstOrNull { it.kind.name == kindName }?.takeIf { it.enabled } ?: return
        groupSheet = AppGroupSheet(activity, groupsViewModel, state, onDismissed = { groupSheet = null }).show()
    }

    /** Tên thư mục sẽ tạo / so khớp. "Ứng dụng của hãng" lấy tên hãng của máy (vd "OPPO", "Samsung"). */
    private fun folderTitle(kind: AppGroupKind): String = when (kind) {
        AppGroupKind.GOOGLE -> activity.getString(R.string.app_group_google)
        AppGroupKind.SOCIAL -> activity.getString(R.string.app_group_social)
        AppGroupKind.GAMES -> activity.getString(R.string.app_group_games)
        AppGroupKind.ENTERTAINMENT -> activity.getString(R.string.app_group_entertainment)
        AppGroupKind.UTILITIES -> activity.getString(R.string.app_group_utilities)
        AppGroupKind.MANUFACTURER -> brandName()
    }

    private fun rowTitle(g: AppGroupsViewModel.AppGroupState): String = when {
        g.kind != AppGroupKind.MANUFACTURER -> g.title
        // Không đọc được tên hãng: tên thư mục đã là "Ứng dụng của hãng", không lồng thêm vào mẫu "Ứng dụng của %1$s".
        Build.MANUFACTURER.isNullOrBlank() -> g.title
        else -> activity.getString(R.string.app_group_manufacturer_row, g.title)
    }

    /** `Build.MANUFACTURER` như máy ghi; toàn chữ thường (vd "samsung") thì viết hoa chữ đầu. Rỗng thì dùng chữ chung. */
    private fun brandName(): String {
        val raw = Build.MANUFACTURER.orEmpty().trim()
        if (raw.isEmpty()) return activity.getString(R.string.app_group_manufacturer_fallback)
        return if (raw == raw.lowercase()) raw.replaceFirstChar { it.uppercase() } else raw
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
        groupSheet?.dialog?.setOnDismissListener(null)
        groupSheet?.dialog?.dismiss()
        groupSheet = null
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

    private companion object {
        const val ID_GROUP_HEADER = "ag_header"
        const val ID_GROUP_PREFIX = "ag_group:"
        const val ID_GROUP_FOOTER = "ag_footer"
    }
}
