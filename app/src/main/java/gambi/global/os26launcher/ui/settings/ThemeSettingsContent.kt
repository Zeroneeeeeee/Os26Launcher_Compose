package gambi.global.os26launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.core.designsystem.compose.DsClockText
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsBadge
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.ui.onboarding.ThemeCatalog
import gambi.global.os26launcher.ui.onboarding.ThemeInfo

/**
 * Giao diện Compose của màn 1.1 "Màn hình chính mặc định" (Figma 302:1738): thanh nav, phụ đề, lưới 2 × 2 thẻ theme có kính Backdrop,
 * phần dưới cố định gồm nút chính và chú thích. Không giữ trạng thái: mọi giá trị đến từ [ThemeSettingsActivity].
 *
 * JD Task 9: dựng trên design system Compose ([DsScreen] lo nền aurora + lớp backdrop + inset + thanh nav, [DsButton], [DsBadge]); không
 * còn view cũ nhúng qua `AndroidView` (trừ đồng hồ `TextClock` của ảnh xem trước). Cải tiến kiểu iOS đã duyệt: viền sáng kính (F1),
 * thẻ và nút co + phủ khi nhấn (F2), line height Figma (F7).
 */
@Composable
fun ThemeSettingsContent(
    chosen: ThemeId,
    applying: Boolean,
    isDefault: Boolean,
    onChoose: (ThemeId) -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit
) {
    val marginH = dimensionResource(R.dimen.ds_screen_margin_h)
    val space12 = dimensionResource(R.dimen.ds_space_12)

    DsScreen(title = stringResource(R.string.settings_hub_theme), onBack = onBack) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = marginH,
                    top = dimensionResource(R.dimen.ds_space_8),
                    end = marginH,
                    bottom = dimensionResource(R.dimen.ds_space_16)
                )
        ) {
            BasicText(
                text = stringResource(R.string.settings_theme_subtitle),
                style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)),
                modifier = Modifier.fillMaxWidth()
            )
            ThemeCatalog.list.chunked(2).forEachIndexed { row, pair ->
                Spacer(Modifier.height(if (row == 0) dimensionResource(R.dimen.ds_space_20) else space12))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space12)) {
                    pair.forEach { info ->
                        ThemeCard(
                            info = info,
                            chosen = info.id == chosen,
                            onClick = { onChoose(info.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = marginH, vertical = dimensionResource(R.dimen.ds_space_16))
        ) {
            DsButton(
                text = stringResource(if (isDefault) R.string.settings_theme_apply else R.string.settings_theme_apply_default),
                onClick = onApply,
                enabled = !applying,
                modifier = Modifier.fillMaxWidth()
            )
            if (!isDefault) {
                BasicText(
                    text = stringResource(R.string.settings_theme_note),
                    style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary), textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.ds_space_8))
                )
            }
        }
    }
}

/** Thẻ theme, khớp `view_onboarding_theme_card.xml` (OB 0.2, Figma `302:3705`) nhưng là Compose để áp được kính Backdrop (JC D3). */
@Composable
internal fun ThemeCard(info: ThemeInfo, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val name = stringResource(info.name)
    val description = stringResource(info.description)
    val space4 = dimensionResource(R.dimen.ds_space_4)
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            .dsPressable(interaction)
            .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))
            .dsPressHighlight(interaction)
            // selectable gộp chữ con: TalkBack đọc "<tên>, <mô tả>" kèm trạng thái chọn, như contentDescription của ThemeCardView.
            .selectable(selected = chosen, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_W / PREVIEW_H)
                .clip(RoundedCornerShape(PREVIEW_RADIUS))
        ) {
            Image(
                painter = painterResource(info.preview),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            PreviewClock(Modifier.align(Alignment.TopCenter).padding(top = 21.dp))
            if (chosen) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = dimensionResource(R.dimen.ds_space_8), end = dimensionResource(R.dimen.ds_space_8))
                        .size(24.dp)
                        .background(colorResource(R.color.ds_accent), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.onb_ic_check), contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = space4, end = space4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(text = name, style = DsTextStyles.bodyStrong.copy(color = colorResource(R.color.ds_text_primary)))
            if (info.isNew) {
                Spacer(Modifier.width(6.dp))
                DsBadge(stringResource(R.string.onboarding_badge_new))
            }
        }
        BasicText(
            text = description,
            style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = space4, end = space4)
        )
    }
}

/** Đồng hồ thật trên ảnh xem trước (ảnh xuất đã bỏ giờ "9:41"). JS5: Compose (`DsClockText`) thay `TextClock` nhúng bằng AndroidView. */
@Composable
internal fun PreviewClock(modifier: Modifier) {
    DsClockText(
        style = TextStyle(
            color = Color(CLOCK_COLOR),
            fontSize = 28.sp,
            letterSpacing = (-0.02).em,
            fontFamily = FontFamily(Font(R.font.be_vietnam_pro_light, FontWeight.Light)),
            fontWeight = FontWeight.Light
        ),
        modifier = modifier
    )
}

private const val PREVIEW_W = 142f
private const val PREVIEW_H = 150f
private val PREVIEW_RADIUS = 12.dp
private const val CLOCK_COLOR = 0xEBF5F7FF.toInt()

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun ThemeSettingsContentPreview() {
    ThemeSettingsContent(
        chosen = ThemeId.LIQUID_GLASS_27,
        applying = false,
        isDefault = false,
        onChoose = {},
        onApply = {},
        onBack = {}
    )
}
