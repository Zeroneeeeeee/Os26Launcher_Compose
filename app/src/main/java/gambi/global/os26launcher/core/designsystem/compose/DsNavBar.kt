package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass

/**
 * JD Task 4 — thanh điều hướng DS3 bằng Compose (bản View: `DsNavBar` + `ds_nav_bar.xml`). Dọc, đệm 16/4/16/8:
 * - nút quay lại (khi có [onBack]): kính `CONTROL` bo PILL cao nhìn thấy 32 trong vùng chạm 48, mũi tên 24 + [backLabel] chữ `body`,
 *   cách tiêu đề 10; nhấn co + phủ kiểu iOS (F2); TalkBack đọc "Quay lại, <nhãn>" là một nút;
 * - tiêu đề [title] (khi có) chữ `title`, tối đa 2 dòng cắt "…", đánh dấu là tiêu đề cho TalkBack.
 * Không tự đệm inset thanh trạng thái: màn chứa (`DsScreen`) lo.
 */
@Composable
fun DsNavBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backLabel: String = stringResource(R.string.ds_nav_back_default)
) {
    val primary = colorResource(R.color.ds_text_primary)
    Column(
        modifier.padding(
            start = dimensionResource(R.dimen.ds_space_16),
            top = dimensionResource(R.dimen.ds_space_4),
            end = dimensionResource(R.dimen.ds_space_16),
            bottom = dimensionResource(R.dimen.ds_space_8)
        )
    ) {
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            val desc = stringResource(R.string.ds_nav_back_desc, backLabel)
            Row(
                Modifier
                    .padding(bottom = 10.dp)
                    .dsPressable(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onBack)
                    .clearAndSetSemantics {
                        contentDescription = desc
                        role = Role.Button
                        onClick { onBack(); true }
                    }
                    .padding(vertical = BACK_INSET)
                    .height(BACK_VISIBLE)
                    .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
                    .dsPressHighlight(interaction)
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ds_ic_chevron_left),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(primary),
                    modifier = Modifier.size(dimensionResource(R.dimen.ds_icon_size))
                )
                Spacer(Modifier.width(2.dp))
                BasicText(backLabel, style = DsTextStyles.body.copy(color = primary), maxLines = 1)
            }
        }
        if (title != null) {
            BasicText(
                text = title,
                style = DsTextStyles.title.copy(color = primary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        }
    }
}

private val BACK_VISIBLE = 32.dp
private val BACK_INSET = 8.dp // (48 vùng chạm − 32 nhìn thấy) / 2
