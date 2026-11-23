package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.ui.home.IconMenuAction
import gambi.global.os26launcher.ui.home.IconMenuState
import com.kyant.backdrop.Backdrop
import gambi.global.os26launcher.ui.home.controller.IconMenuPlacement
import gambi.global.os26launcher.ui.home.controller.IconMenuWidth

/**
 * JH3b Task 1 — menu nhấn giữ icon (bản Compose của `IconMenuController` + `IconActionMenu`). Lớp phủ toàn màn: nền tối (chạm → đóng), icon
 * nổi ở đúng chỗ icon thật (không bị làm mờ cùng Home), thẻ kính có shortcut (tối đa 4), vạch ngăn, rồi các mục cố định
 * (Thông tin, Ẩn, Sửa Màn hình chính — không có trong thư mục —, Gỡ cài đặt màu đỏ). Đặt chỗ bằng `IconMenuPlacement.place` (thuần, dùng lại
 * của View); menu ở trên icon thì đảo thứ tự mục. Mở: lò xo 700/0.9, phóng 0,6 → 1 từ góc gần icon nhất; đóng 1400/1.0 (plan D3, D4).
 *
 * Menu chỉ hiện khi đã có shortcut (`shortcuts != null`), như View: tránh nhảy cỡ khi danh sách về muộn.
 */
@Composable
internal fun HomeIconMenu(
    menu: IconMenuState?,
    app: AppItem?,
    icons: HomeIcons,
    iconSizePx: Int,
    progress: Animatable<Float, *>,
    /** Lớp Home chưa tối (ảnh nền + lưới + dock): kính menu lấy mẫu lớp này. null = ảnh nền (`LocalDsBackdrop`). */
    backdrop: Backdrop?,
    onAction: (IconMenuAction) -> Unit,
    onShortcut: (AppShortcutItem) -> Unit,
    onDismiss: () -> Unit
) {
    // Giữ menu cuối cùng để chạy hoạt ảnh đóng sau khi VM đã đặt iconMenu = null.
    var shown by remember { mutableStateOf(menu) }
    var shownApp by remember { mutableStateOf(app) }
    LaunchedEffect(menu) {
        if (menu != null) {
            shown = menu
            shownApp = app
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 700f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1400f))
            shown = null
        }
    }
    val m = shown ?: return
    val p = progress.value
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
    ) {
        // Icon nổi tại chỗ icon thật.
        val a = m.anchor
        shownApp?.let { item ->
            rememberHomeIcon(icons, item, iconSizePx)?.let { bmp ->
                Image(
                    bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = (a.left + a.right) / 2f - iconSizePx / 2f
                            translationY = (a.top + a.bottom) / 2f - iconSizePx / 2f
                        }
                        .size(with(density) { iconSizePx.toDp() })
                )
            }
        }
        if (m.shortcuts != null) {
            MenuPlacer(m, p) { above ->
                MenuCard(m, above, backdrop, onAction, onShortcut)
            }
        }
    }
}

/** Đo menu rồi đặt bằng `IconMenuPlacement.place` (cùng luật với View); [content] nhận `above` để đảo thứ tự mục. */
@Composable
private fun MenuPlacer(m: IconMenuState, p: Float, content: @Composable (Boolean) -> Unit) {
    val gap = with(LocalDensity.current) { dimensionResource(R.dimen.shortcut_menu_gap).roundToPx() }
    val margin = with(LocalDensity.current) { dimensionResource(R.dimen.shortcut_menu_margin).roundToPx() }
    var above by remember { mutableStateOf(false) }
    Layout(content = { content(above) }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val a = m.anchor
        val r = IconMenuPlacement.place(
            IconMenuPlacement.Input(
                rootWidth = constraints.maxWidth, rootHeight = constraints.maxHeight,
                insetLeft = 0, insetTop = 0, insetRight = 0, insetBottom = 0,
                iconLeft = a.left, iconTop = a.top, iconRight = a.right, contentBottom = a.bottom,
                menuWidth = placeable.width, menuHeight = placeable.height, gap = gap, margin = margin
            )
        )
        above = r.above
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(r.left, r.top) {
                val s = 0.6f + 0.4f * p
                scaleX = s
                scaleY = s
                alpha = (p * 2.5f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(if (r.alignEnd) 1f else 0f, if (r.above) 1f else 0f)
            }
        }
    }
}

@Composable
private fun MenuCard(m: IconMenuState, above: Boolean, backdrop: Backdrop?, onAction: (IconMenuAction) -> Unit, onShortcut: (AppShortcutItem) -> Unit) {
    val rows = buildList<@Composable () -> Unit> {
        m.shortcuts.orEmpty().forEach { s -> add { MenuRow(s.shortLabel, icon = s.icon, destructive = false) { onShortcut(s) } } }
        if (m.shortcuts.orEmpty().isNotEmpty()) add { MenuDivider() }
        // Như iOS (và REF `IconActionMenu.iconFor`): mục cố định cũng có icon.
        add { MenuRow(stringResource(R.string.menu_app_info), iconRes = R.drawable.ic_shortcut_menu_info) { onAction(IconMenuAction.AppInfo) } }
        add { MenuRow(stringResource(R.string.menu_hide_app), iconRes = R.drawable.ic_shortcut_menu_hide) { onAction(IconMenuAction.HideApp) } }
        if (m.container <= 0L) add {
            MenuRow(stringResource(R.string.menu_edit_home), iconRes = R.drawable.ic_shortcut_menu_edit_home) { onAction(IconMenuAction.EditHome) }
        }
        add {
            MenuRow(stringResource(R.string.menu_uninstall), iconRes = R.drawable.ic_shortcut_menu_uninstall, destructive = true) {
                onAction(IconMenuAction.Uninstall)
            }
        }
    }
    Column(
        Modifier
            .widthIn(dimensionResource(R.dimen.shortcut_menu_min_width), dimensionResource(R.dimen.shortcut_menu_max_width))
            .width(IntrinsicSize.Max)
            .dsGlass(DsSurface.CARD, DsCorner.Fixed(MENU_RADIUS_DP, glass = true), backdrop, fixedBlurDp = MENU_BLUR_DP, surfaceFill = false)
            .background(colorResource(R.color.shortcut_menu_glass_tint)) // menu sáng như IconActionMenu (chữ tối)
            .padding(
                horizontal = dimensionResource(R.dimen.shortcut_menu_padding_horizontal),
                vertical = dimensionResource(R.dimen.shortcut_menu_padding_vertical)
            )
    ) {
        (if (above) rows.asReversed() else rows).forEach { it() }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: android.graphics.Bitmap? = null,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val color = colorResource(if (destructive) R.color.shortcut_menu_destructive else R.color.shortcut_menu_text)
    val iconSize = dimensionResource(R.dimen.shortcut_menu_icon_size)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.shortcut_menu_row_inset))
            .height(dimensionResource(R.dimen.shortcut_menu_row_height))
            .clip(RoundedCornerShape(dimensionResource(R.dimen.shortcut_menu_row_radius)))
            .dsPressHighlight(interaction, darkPress = true)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(
                start = dimensionResource(R.dimen.shortcut_menu_row_padding_start),
                end = dimensionResource(R.dimen.shortcut_menu_row_padding_end)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Như menu ngữ cảnh iOS: chữ bên trái, icon bên PHẢI.
        BasicText(
            text,
            style = TextStyle(color = color, fontSize = with(LocalDensity.current) { dimensionResource(R.dimen.shortcut_menu_text_size).toSp() }),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (icon != null || iconRes != null) {
            Spacer(Modifier.width(dimensionResource(R.dimen.shortcut_menu_icon_padding)))
            if (icon != null) {
                val bmp = remember(icon) { icon.asImageBitmap() }
                Image(bmp, contentDescription = null, modifier = Modifier.size(iconSize))
            } else if (iconRes != null) {
                Image(
                    androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.shortcut_menu_divider_inset),
                vertical = dimensionResource(R.dimen.shortcut_menu_divider_margin)
            )
            .height(dimensionResource(R.dimen.shortcut_menu_divider))
            .background(colorResource(R.color.shortcut_menu_divider))
    )
}

private const val MENU_RADIUS_DP = 24f
private const val MENU_BLUR_DP = 6f // độ mờ CỐ ĐỊNH (không theo thanh "Độ mờ"): thấy app bị menu đè phía sau, như iOS 26 (chủ dự án 2026-11-23)

