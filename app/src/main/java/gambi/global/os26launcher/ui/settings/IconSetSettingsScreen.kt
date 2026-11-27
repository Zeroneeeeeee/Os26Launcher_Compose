package gambi.global.os26launcher.ui.settings

import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import androidx.lifecycle.lifecycleScope
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.ui.settings.compose.ICON_STYLE_PREVIEW_HEIGHT
import gambi.global.os26launcher.ui.settings.compose.ICON_STYLE_PREVIEW_ICON_DP
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * G2d Task 4 — màn "Bộ biểu tượng" (2.3): kiểu icon, màu tô (chỉ khi Tô màu) và gói icon. Chuyển nguyên từ màn tạm, không đổi hành vi.
 */
class IconSetSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ctx.viewModel

    // G2b: một chỗ giữ cho cả ba hộp thoại (kiểu, màu tô, gói icon): không mở hai hộp cùng lúc.
    private var iconStyleDialog: DsDialogHandle? = null

    override val titleRes: Int = R.string.settings_hub_icon_set

    /** P3-3: ảnh nền + icon mẫu cho khung xem trước (ViewModel riêng, vẽ icon theo kiểu vừa chọn). */
    private val preview = ViewModelProvider(activity)[IconStylePreviewViewModel::class.java].also { vm ->
        val m = activity.resources.displayMetrics
        vm.start(
            iconPx = (ICON_STYLE_PREVIEW_ICON_DP * m.density).roundToInt(),
            bgWidth = m.widthPixels / 2,
            bgHeight = (ICON_STYLE_PREVIEW_HEIGHT.value * m.density).roundToInt()
        )
    }

    override suspend fun observe() {
        coroutineScope {
            launch { preview.background.collect { ctx.rerender(false) } }
            launch { preview.icons.collect { ctx.rerender(false) } }
        }
    }

    override fun rows(state: SettingsUiState): List<SettingsRow> = listOf<SettingsRow>(
        SettingsRow.IconStylePreview(ID_PREVIEW, preview.background.value, preview.icons.value)
    ) + listOf(
        // G2b: kiểu icon; hàng màu tô chỉ có khi đang ở kiểu Tô màu
        SettingsRow.Value(
            SettingsRow.ID_ICON_STYLE,
            R.string.settings_icon_style,
            activity.getString(state.iconStyle.labelRes())
        )
    ) + (if (state.iconStyle == IconStyle.TINTED) {
        listOf(
            SettingsRow.Value(
                SettingsRow.ID_ICON_TINT,
                R.string.settings_icon_tint,
                activity.getString(state.iconTint.labelRes())
            )
        )
    } else {
        emptyList()
    }) + SettingsRow.Value( // IP: gói biểu tượng của bên thứ ba
        SettingsRow.ID_ICON_PACK,
        R.string.settings_icon_pack,
        state.iconPackLabel ?: activity.getString(R.string.settings_icon_pack_none)
    )

    override fun onValueClicked(row: SettingsRow.Value) {
        when (row.id) {
            SettingsRow.ID_ICON_STYLE -> showIconStyleDialog()
            SettingsRow.ID_ICON_TINT -> showIconTintDialog()
            SettingsRow.ID_ICON_PACK -> showIconPackDialog()
        }
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    override fun onDestroy() {
        iconStyleDialog?.setOnDismissListener(null)
        iconStyleDialog?.dismiss()
        iconStyleDialog = null
    }

    private fun IconTint.labelRes(): Int = when (this) {
        IconTint.BLUE -> R.string.settings_icon_tint_blue
        IconTint.PINK -> R.string.settings_icon_tint_pink
        IconTint.GREEN -> R.string.settings_icon_tint_green
        IconTint.ORANGE -> R.string.settings_icon_tint_orange
        IconTint.GRAPHITE -> R.string.settings_icon_tint_graphite
    }

    private fun showIconStyleDialog() {
        if (iconStyleDialog != null) return
        val options = IconStyle.entries // 2026-11-26 (chủ dự án): kiểu Trong suốt đã bỏ (icon lỗi, lẫn vào nền)
        val labels = options.map { activity.getString(it.labelRes()) }
        val current = viewModel.uiState.value.iconStyle
        iconStyleDialog = SettingsDialogs.singleChoice(
            activity, activity.getString(R.string.settings_icon_style), labels, options.indexOf(current)
        ) { which ->
            viewModel.onIconStyleChosen(options[which]) // chọn đúng kiểu đang dùng thì ViewModel không ghi
        }.also { d -> d.setOnDismissListener { iconStyleDialog = null } }
    }

    /** IP: liệt kê gói đã cài (nạp nền) rồi hiện hộp chọn một: "Không dùng" + từng gói. Không có gói nào thì hiện hướng dẫn. */
    private fun showIconPackDialog() {
        if (iconStyleDialog != null) return
        activity.lifecycleScope.launch {
            val packs = viewModel.installedIconPacks()
            if (iconStyleDialog != null || activity.isFinishing || activity.isDestroyed) return@launch
            if (packs.isEmpty()) {
                iconStyleDialog = SettingsDialogs.confirm(
                    activity,
                    activity.getString(R.string.settings_icon_pack),
                    activity.getString(R.string.settings_icon_pack_empty),
                    activity.getString(R.string.settings_icon_pack_ok),
                    onPositive = {},
                    negative = null
                ).also { d -> d.setOnDismissListener { iconStyleDialog = null } }
                return@launch
            }
            val labels: List<CharSequence> =
                listOf(activity.getString(R.string.settings_icon_pack_none)) + packs.map { it.label }
            val current = viewModel.uiState.value.iconPack
            val checked =
                if (current == null) 0 else packs.indexOfFirst { it.packageName == current } + 1
            iconStyleDialog = SettingsDialogs.singleChoice(
                activity, activity.getString(R.string.settings_icon_pack), labels, checked
            ) { which ->
                viewModel.onIconPackChosen(if (which == 0) null else packs[which - 1].packageName)
            }.also { d -> d.setOnDismissListener { iconStyleDialog = null } }
        }
    }

    private fun showIconTintDialog() {
        if (iconStyleDialog != null) return
        val options = IconTint.entries
        val labels = options.map { activity.getString(it.labelRes()) }
        val current = viewModel.uiState.value.iconTint
        iconStyleDialog = SettingsDialogs.singleChoice(
            activity, activity.getString(R.string.settings_icon_tint), labels, options.indexOf(current)
        ) { which ->
            viewModel.onIconTintChosen(options[which])
        }.also { d -> d.setOnDismissListener { iconStyleDialog = null } }
    }

    private companion object {
        const val ID_PREVIEW = "icon_style_preview" // P3-3
    }
}
