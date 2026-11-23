package gambi.global.os26launcher.core.designsystem.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsCorners
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.core.designsystem.DsTileDrawable
import gambi.global.os26launcher.core.glass.compose.dsGlass

/**
 * JD Task 3 — chip DS3 (bản View: `DsChipView`, Figma Chip 302:3339): cao nhìn thấy 34 trong vùng chạm 48, đệm ngang 14, bo PILL.
 * Chọn: bề mặt `CONTROL_SELECTED`, chữ `ds_on_light` 600; thường: kính `CONTROL`, chữ primary 400. Chip không tự đổi chọn: chủ sở hữu
 * đổi [selected] trong [onClick]. TalkBack: có thể chọn / đã chọn như bản View.
 */
@Composable
fun DsChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val touch = dimensionResource(R.dimen.ds_touch_min)
    val inset = ((touch - CHIP_VISIBLE) / 2).coerceAtLeast(0.dp)
    Box(
        modifier
            .dsPressable(interaction)
            .toggleable(value = selected, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(vertical = inset)
            .height(CHIP_VISIBLE)
            .dsGlass(if (selected) DsSurface.CONTROL_SELECTED else DsSurface.CONTROL, DsCorner.Pill)
            .dsPressHighlight(interaction, darkPress = selected)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = DsTextStyles.body.copy(
                color = colorResource(if (selected) R.color.ds_on_light else R.color.ds_text_primary),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Loại badge, như `DsBadgeView.Type`. */
enum class DsBadgeType(val surface: DsSurface, val textColor: Int) {
    Warning(DsSurface.BADGE_WARNING, R.color.ds_status_warning_text),
    Danger(DsSurface.BADGE_DANGER, R.color.ds_status_danger_text),
    New(DsSurface.BADGE_NEW, R.color.ds_text_primary)
}

/** JD Task 3 — badge DS3 (bản View: `DsBadgeView`): bo PILL, đệm 8/3, cao tối thiểu 20, chữ `badge`. Chỉ hiển thị, không nhận chạm. */
@Composable
fun DsBadge(text: String, modifier: Modifier = Modifier, type: DsBadgeType = DsBadgeType.New) {
    Box(
        modifier
            .defaultMinSize(minHeight = 20.dp)
            .dsGlass(type.surface, DsCorner.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = text, style = DsTextStyles.badge.copy(color = colorResource(type.textColor)), maxLines = 1)
    }
}

/**
 * JD Task 3 — ô icon DS3 (bản View: `DsIconTileView`): `ds_tile_size` (32 dp), nền `DsTileDrawable`, icon 24 dp tô màu nét, bóng đen
 * cao 4 dp trên hình bo của ô. Chỉ trang trí: ẩn khỏi TalkBack (hàng chứa nó đọc tiêu đề).
 */
@Composable
fun DsIconTile(@DrawableRes icon: Int, color: DsTileColor, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val size = dimensionResource(R.dimen.ds_tile_size)
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val tile = remember(context) { DsTileDrawable(context, color) }
    tile.color = color
    val radiusPx = DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.TILE), sizePx, sizePx)
    val shape = RoundedCornerShape(with(LocalDensity.current) { radiusPx.toDp() })
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .shadow(TILE_ELEVATION, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .drawBehind {
                tile.setBounds(0, 0, this.size.width.toInt(), this.size.height.toInt())
                drawIntoCanvas { tile.draw(it.nativeCanvas) }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorResource(color.glyph)),
            modifier = Modifier.size(24.dp)
        )
    }
}

private val CHIP_VISIBLE = 34.dp
private val TILE_ELEVATION = 4.dp
