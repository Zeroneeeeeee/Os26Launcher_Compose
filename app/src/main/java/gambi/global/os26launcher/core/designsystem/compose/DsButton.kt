package gambi.global.os26launcher.core.designsystem.compose

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass

/** Kiểu nút: [Primary] = bề mặt `PRIMARY` (gradient tím + quầng sáng), [Secondary] = kính `CONTROL`. */
enum class DsButtonVariant { Primary, Secondary }

/** Cỡ nút: cao nhìn thấy [visibleDp] và đệm ngang [paddingHDp]; vùng chạm luôn ≥ `ds_touch_min` (48 dp). */
enum class DsButtonSize(val visibleDp: Int, val paddingHDp: Int) { L(48, 20), M(40, 16), S(34, 14) }

/**
 * JD Task 3 — nút DS3 bằng Compose (bản View: `core/designsystem/DsButton`, Figma Button 302:3350). Bo PILL, chữ `bodyStrong` một dòng
 * cắt "…", tắt thì mờ 40%. Phần cao chênh giữa vùng chạm 48 dp và cỡ nhìn thấy là đệm dọc nằm NGOÀI bề mặt, nên chạm vào đó vẫn ăn.
 * Nhấn: co + phủ kiểu iOS (F2). Rộng theo [modifier] (vd `fillMaxWidth()`), mặc định ôm chữ.
 */
@Composable
fun DsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DsButtonVariant = DsButtonVariant.Primary,
    size: DsButtonSize = DsButtonSize.L,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val touch = dimensionResource(R.dimen.ds_touch_min)
    val visible = size.visibleDp.dp
    val inset: Dp = ((touch - visible) / 2).coerceAtLeast(0.dp)
    val pill = RoundedCornerShape(percent = 50)
    val primary = variant == DsButtonVariant.Primary
    val glow = colorResource(R.color.ds_primary_glow).copy(alpha = GLOW_ALPHA)

    Box(
        modifier
            .dsPressable(interaction, enabled)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = inset)
            .height(visible)
            .then(
                // Quầng sáng của nút chính: API 26–27 không đổi được màu bóng, bỏ hẳn thay vì để bóng đen (như bản View).
                if (primary && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Modifier.shadow(GLOW_ELEVATION, pill, clip = false, ambientColor = glow, spotColor = glow)
                } else {
                    Modifier
                }
            )
            .dsGlass(if (primary) DsSurface.PRIMARY else DsSurface.CONTROL, DsCorner.Pill)
            .dsPressHighlight(interaction, darkPress = primary, enabled = enabled)
            .padding(horizontal = size.paddingHDp.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = DsTextStyles.bodyStrong.copy(color = colorResource(R.color.ds_text_primary), textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val DISABLED_ALPHA = 0.4f
private const val GLOW_ALPHA = 0.32f // `v3/Button/Glow` #525A4BF5, như GLOW_ALPHA của DsSurfaceViews
private val GLOW_ELEVATION = 8.dp
