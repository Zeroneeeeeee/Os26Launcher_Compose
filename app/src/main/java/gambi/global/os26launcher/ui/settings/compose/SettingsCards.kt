package gambi.global.os26launcher.ui.settings.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsCorners
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.core.designsystem.DsTileDrawable
import gambi.global.os26launcher.core.designsystem.compose.DrawableBox
import gambi.global.os26launcher.core.designsystem.compose.DsBadge
import gambi.global.os26launcher.core.designsystem.compose.DsBadgeType
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsSegmented
import gambi.global.os26launcher.core.designsystem.compose.DsSliderRow
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.drawDrawable
import gambi.global.os26launcher.core.designsystem.compose.drawableBackground
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.ui.onboarding.ThemeCatalog
import gambi.global.os26launcher.ui.settings.SettingsRow
import gambi.global.os26launcher.ui.settings.ThemeBadge
import gambi.global.os26launcher.ui.wallpaper.compose.WallpaperTile

// ---------------------------------------------------------------------------------------------------------------------------------
// Đường kẻ hàng tự dựng (hàng không phải DsRow)

/** Đường kẻ đáy hàng như `DsRow`: bắt đầu từ đệm đầu hàng, mờ dần hai đầu. */
private fun Modifier.rowDivider(show: Boolean, color: androidx.compose.ui.graphics.Color, startPx: Float): Modifier =
    if (!show) this else drawBehind {
        val h = 1.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to color.copy(alpha = 0f), 0.12f to color, 0.88f to color, 1f to color.copy(alpha = 0f),
                startX = startPx, endX = size.width
            ),
            topLeft = Offset(startPx, size.height - h),
            size = androidx.compose.ui.geometry.Size(size.width - startPx, h)
        )
    }

/** RS Task 3: tiêu đề + segmented bên phải (Figma 1.4 "Đơn vị nhiệt độ"). */
@Composable
internal fun SegmentedRow(row: SettingsRow.Segmented, last: Boolean, actions: SettingsListActions) {
    val padStart = dimensionResource(R.dimen.ds_row_padding_start)
    val startPx = with(LocalDensity.current) { padStart.toPx() }
    val title = stringResource(row.title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimensionResource(R.dimen.ds_row_min_height))
            .rowDivider(!last, colorResource(R.color.ds_divider), startPx)
            .padding(start = padStart, end = dimensionResource(R.dimen.ds_row_padding_end)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            title,
            style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_primary)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.ds_row_gap)))
        DsSegmented(
            options = row.options,
            selectedIndex = row.selected,
            onSelect = { actions.onSegmentChanged(row, it) },
            modifier = Modifier.semantics { contentDescription = title },
            fill = false
        )
    }
}

/** GT: thanh trượt; phần trăm chạy theo ngón; chỉ ghi khi nhả tay (hoặc đổi bằng trợ năng, không có cử chỉ kéo). */
@Composable
internal fun SliderRow(row: SettingsRow.Slider, last: Boolean, actions: SettingsListActions, resetKey: Int) {
    var value by remember(row.value, resetKey) { mutableIntStateOf(row.value) }
    var tracking by remember { mutableStateOf(false) }
    val padStart = dimensionResource(R.dimen.ds_row_padding_start)
    val startPx = with(LocalDensity.current) { padStart.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .rowDivider(!last, colorResource(R.color.ds_divider), startPx)
            .padding(start = padStart, end = dimensionResource(R.dimen.ds_row_padding_end), top = 4.dp, bottom = 8.dp)
    ) {
        DsSliderRow(
            label = stringResource(row.title),
            value = value,
            onValueChange = {
                value = it
                actions.onSliderMoved(row, it)
                if (!tracking) actions.onSliderCommitted(row, it)
            },
            onTrackingChange = { now ->
                tracking = now
                if (!now) actions.onSliderCommitted(row, value)
            }
        )
        if (row.description != 0) {
            BasicText(
                stringResource(row.description),
                style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary))
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Khối ngoài card

/**
 * RS Task 2 + HF + HC: Hero Card (Figma 464:2199), cao tối thiểu 140; hai trạng thái chồng nhau và luôn được đo (card cao bằng khối cao
 * hơn, không xô danh sách). Chưa mặc định: ảnh + tên theme + badge + nút "Trải nghiệm". Đã mặc định: lời chào + phiên bản + "Khám phá"
 * + huy hiệu số của giao diện. Đổi Chưa → Đã khi card đang hiện: cross-fade 300 ms (ghi chú 464:2648 mục 3).
 */
@Composable
internal fun LauncherCard(row: SettingsRow.LauncherCard, onClick: () -> Unit) {
    val theme = row.themeId?.let(ThemeCatalog::of)
    // Như bản View: chỉ cross-fade chiều Chưa → Đã; chiều ngược lại (hiếm, đổi launcher ở ngoài) đặt thẳng.
    val shownDefault by animateFloatAsState(
        if (row.isDefault) 1f else 0f,
        animationSpec = if (row.isDefault) tween(CROSS_FADE_MS, easing = FastOutSlowInEasing) else snap(),
        label = "heroCard"
    )
    val primary = colorResource(R.color.ds_text_primary)
    val secondary = colorResource(R.color.ds_text_secondary)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD_LARGE))
    ) {
        // Chưa mặc định (464:2158). Khối đang hiện luôn nằm trên (zIndex) để nhận chạm trước: khối ẩn chồng lên (bản View là INVISIBLE,
        // không nhận chạm) không được chặn nút "Trải nghiệm" / "Khám phá" của khối đang hiện.
        Row(
            Modifier
                .zIndex(if (row.isDefault) 0f else 1f)
                .fillMaxWidth()
                .alpha(1f - shownDefault)
                .then(if (row.isDefault) Modifier.clearAndSetSemantics { } else Modifier)
                .padding(12.dp)
        ) {
            if (theme != null) {
                Image(
                    painterResource(theme.preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 93.dp, height = 116.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                BasicText(stringResource(R.string.settings_launcher_card_current), style = DsTextStyles.caption.copy(color = secondary))
                BasicText(
                    stringResource(theme?.name ?: R.string.settings_launcher_card_custom),
                    style = DsTextStyles.card.copy(color = primary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!row.isDefault) {
                    DsBadge(stringResource(R.string.settings_launcher_card_not_default), Modifier.padding(top = 4.dp), DsBadgeType.Warning)
                }
                DsButton(
                    stringResource(R.string.hero_try),
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    size = DsButtonSize.M,
                    enabled = !row.isDefault
                )
            }
        }
        // Đã mặc định (464:2198)
        Row(
            Modifier
                .zIndex(if (row.isDefault) 1f else 0f)
                .matchParentSize()
                .alpha(shownDefault)
                .then(if (!row.isDefault) Modifier.clearAndSetSemantics { } else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(stringResource(R.string.settings_launcher_card_welcome), style = DsTextStyles.card.copy(color = primary), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    // HF: chip phiên bản 464:2187 (nền trắng 10%, viền 0.5 trắng 18%, bo tròn).
                    BasicText(
                        stringResource(R.string.settings_launcher_card_version, row.versionName),
                        style = DsTextStyles.badge.copy(color = colorResource(R.color.ds_hero_version_chip_text)),
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .drawBehind { drawRect(androidx.compose.ui.graphics.Color(0x1AFFFFFF)) }
                            .border(0.5.dp, colorResource(R.color.ds_hero_version_chip_border), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                BasicText(
                    stringResource(R.string.settings_launcher_card_welcome_desc),
                    style = DsTextStyles.caption.copy(color = secondary),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                ExploreLink(Modifier.align(Alignment.End).padding(top = 8.dp), enabled = row.isDefault, onClick = onClick)
            }
            Spacer(Modifier.width(14.dp))
            ThemeBadgeCircle(row)
        }
    }
}

@Composable
private fun ExploreLink(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val link = colorResource(R.color.ds_hero_link)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .dsPressHighlight(interaction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(stringResource(R.string.settings_launcher_card_explore), style = DsTextStyles.bodyStrong.copy(color = link))
        Spacer(Modifier.width(2.dp))
        Image(painterResource(R.drawable.ds_ic_chevron_right), null, colorFilter = ColorFilter.tint(link), modifier = Modifier.size(18.dp))
    }
}

/** Huy hiệu tròn 72 (464:2196, kính như card): số của giao diện, hoặc ảnh xem trước thu nhỏ khi giao diện không có số. */
@Composable
private fun ThemeBadgeCircle(row: SettingsRow.LauncherCard) {
    val number = ThemeBadge.numberOf(row.themeId)
    val theme = row.themeId?.let(ThemeCatalog::of)
    Box(Modifier.size(72.dp).dsGlass(DsSurface.CARD, DsCorner.Pill), contentAlignment = Alignment.Center) {
        if (number != null) {
            BasicText(
                number,
                style = DsTextStyles.title.copy(
                    color = colorResource(R.color.ds_text_primary),
                    fontSize = 30.sp,
                    letterSpacing = (-0.03).em,
                    fontFamily = FontFamily(Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold)),
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
        } else if (theme != null) {
            Image(painterResource(theme.preview), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** RS Task 7: card xem trước thời tiết (dữ liệu mẫu). Cả card là một mục TalkBack. */
@Composable
internal fun WeatherPreviewCard(row: SettingsRow.WeatherPreview) {
    val primary = colorResource(R.color.ds_text_primary)
    val secondary = colorResource(R.color.ds_text_secondary)
    Column(
        Modifier
            .fillMaxWidth()
            .drawableBackground(R.drawable.bg_weather_preview)
            .clearAndSetSemantics { contentDescription = "${row.city}, ${row.temperature}, ${row.condition}, ${row.highLow}" }
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                BasicText(row.city, style = DsTextStyles.card.copy(color = primary), maxLines = 1)
                BasicText(row.temperature, style = DsTextStyles.display.copy(color = primary))
            }
            Column(horizontalAlignment = Alignment.End) {
                Image(painterResource(R.drawable.ds_ic_weather), null, colorFilter = ColorFilter.tint(primary), modifier = Modifier.size(32.dp))
                BasicText(row.condition, style = DsTextStyles.caption.copy(color = primary), modifier = Modifier.padding(top = 4.dp))
                BasicText(row.highLow, style = DsTextStyles.caption.copy(color = primary))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            for ((label, temp) in row.hourly) {
                Column(Modifier.weight(1f)) {
                    BasicText(label, style = DsTextStyles.caption.copy(color = secondary), maxLines = 1)
                    BasicText(temp, style = DsTextStyles.bodyStrong.copy(color = primary), maxLines = 1)
                }
            }
        }
    }
}

/** RS Task 8 + WI: lưới 3 cột ô ảnh cài sẵn (Figma 2.2), khe 12. Chạm ô báo màn (màn hỏi xác nhận rồi mới ghi). */
@Composable
internal fun WallpaperGrid(row: SettingsRow.WallpaperGrid, onClick: (Int) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        row.tiles.indices.chunked(COLUMNS).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until COLUMNS) {
                    val i = chunk.getOrNull(c)
                    if (i == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val p = row.tiles[i]
                        WallpaperTile(
                            thumb = p.thumb,
                            label = stringResource(p.name),
                            isNew = p.isNew,
                            chosen = i == row.chosen,
                            onClick = { onClick(i) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * BG: card xin quyền (Figma 3.4 302:2488). Bề mặt CARD + lớp phủ gradient cam (cam 10% → 3% từ trên xuống) và viền 0.5 trắng 19%; ô
 * icon cam 36 bo 10.
 */
@Composable
internal fun PermissionCard(row: SettingsRow.PermissionCard, onClick: () -> Unit) {
    val context = LocalContext.current
    val warning = colorResource(R.color.ds_status_warning_text)
    val top = colorResource(R.color.ds_card_warning_top)
    val bottom = colorResource(R.color.ds_card_warning_bottom)
    val border = colorResource(R.color.ds_card_warning_border)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val radius = with(LocalDensity.current) {
        (if (size.width > 0) DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.CARD), size.width, size.height) else 0f).toDp()
    }
    val shape = RoundedCornerShape(radius)
    val tile = remember(context) { DsTileDrawable(context, DsTileColor.AMBER) }
    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { size = it }
            .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))
            .drawBehind { drawRect(Brush.verticalGradient(listOf(top, bottom))) }
            .border(0.5.dp, border, shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).drawBehind { drawDrawable(tile) }.padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(row.icon),
                    null,
                    colorFilter = ColorFilter.tint(colorResource(DsTileColor.AMBER.glyph)),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText(stringResource(row.title), style = DsTextStyles.bodyStrong.copy(color = warning))
                BasicText(stringResource(row.description), style = DsTextStyles.caption.copy(color = warning), modifier = Modifier.padding(top = 2.dp))
            }
        }
        DsButton(stringResource(row.action), onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), size = DsButtonSize.M)
    }
}

/** Một ô chọn kiểu (huy hiệu, thư viện): bề mặt CARD, ô đang chọn có viền `ds_accent` 1.5 (tạm, chờ thiết kế). */
@Composable
private fun StyleOption(chosen: Boolean, label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val radius = with(LocalDensity.current) {
        (if (size.width > 0) DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.CARD), size.width, size.height) else 0f).toDp()
    }
    val accent = colorResource(R.color.ds_accent)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .dsPressable(interaction)
            .onSizeChanged { size = it }
            .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))
            .then(if (chosen) Modifier.border(1.5.dp, accent, RoundedCornerShape(radius)) else Modifier)
            .semantics(mergeDescendants = true) { selected = chosen }
            .clickable(interaction, indication = null, role = Role.RadioButton) { if (!chosen) onClick() }
            .dsPressHighlight(interaction)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
        BasicText(
            label,
            style = DsTextStyles.bodyStrong.copy(color = colorResource(R.color.ds_text_primary)),
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** BG: hai ô "Kiểu huy hiệu" (Figma 3.4 302:2500): ô mẫu xanh lá 52 bo 15 với huy hiệu số "12" hoặc chấm đỏ. */
@Composable
internal fun BadgeStyleOptions(selected: Int, onChoose: (Int) -> Unit) {
    val context = LocalContext.current
    val danger = colorResource(R.color.ds_status_danger)
    val edge = colorResource(R.color.ds_on_light)
    val tile = remember(context) { DsTileDrawable(context, DsTileColor.GREEN) }
    @Composable
    fun SampleTile() {
        Box(
            Modifier.offset(x = 6.dp, y = 10.dp).size(52.dp).clip(RoundedCornerShape(15.dp)).drawBehind { drawDrawable(tile) }.padding(10.5.dp)
        ) {
            Image(painterResource(R.drawable.ds_ic_download), null, colorFilter = ColorFilter.tint(colorResource(DsTileColor.GREEN.glyph)))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StyleOption(selected == 0, stringResource(R.string.badges_style_number), { onChoose(0) }, Modifier.weight(1f)) {
            Box(Modifier.size(width = 70.dp, height = 66.dp)) {
                SampleTile()
                BasicText(
                    stringResource(R.string.badges_sample_count),
                    style = DsTextStyles.badge.copy(color = edge),
                    modifier = Modifier
                        .offset(x = 44.dp)
                        .heightIn(min = 22.dp)
                        .widthIn(min = 22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .drawBehind { drawRect(danger) }
                        .border(2.dp, edge, RoundedCornerShape(11.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }
        StyleOption(selected == 1, stringResource(R.string.badges_style_dot), { onChoose(1) }, Modifier.weight(1f)) {
            Box(Modifier.size(width = 70.dp, height = 66.dp)) {
                SampleTile()
                Box(
                    Modifier
                        .offset(x = 50.dp, y = 4.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .drawBehind { drawRect(danger) }
                        .border(2.dp, edge, CircleShape)
                )
            }
        }
    }
}

/** LO: hai ô chọn kiểu hiển thị thư viện (Figma 3.2): Thư mục danh mục / Danh sách A–Z. */
@Composable
internal fun LibraryStyleOptions(selected: Int, onChoose: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StyleOption(selected == 0, stringResource(R.string.lo_style_folders), { onChoose(0) }, Modifier.weight(1f)) {
            DrawableBox(R.drawable.lib_style_folders, Modifier.size(104.dp))
        }
        StyleOption(selected == 1, stringResource(R.string.lo_style_list), { onChoose(1) }, Modifier.weight(1f)) {
            DrawableBox(R.drawable.lib_style_list, Modifier.size(104.dp))
        }
    }
}

/** TR: dòng giới thiệu + card xem trước tĩnh màn 4.1 (Figma 302:2552): card CARD bo 24, hai khung ảnh nền bo 14. */
@Composable
internal fun TransitionPreview() {
    val description = stringResource(R.string.tr_preview_description)
    Column {
        BasicText(stringResource(R.string.tr_intro), style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)))
        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(176.dp)
                .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD_LARGE))
                .clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewFrame {
                for ((x, y, lit) in listOf(Triple(9, 14, false), Triple(31, 14, true), Triple(53, 14, false), Triple(9, 36, false), Triple(31, 36, false), Triple(53, 36, false))) {
                    DrawableBox(
                        if (lit) R.drawable.tr_preview_icon_lit else R.drawable.tr_preview_icon_dim,
                        Modifier.offset(x.dp, y.dp).size(16.dp)
                    )
                }
            }
            Image(painterResource(R.drawable.tr_ic_arrow_right), null, modifier = Modifier.padding(horizontal = 18.dp).size(28.dp))
            PreviewFrame {
                Box(Modifier.offset(8.dp, 30.dp).size(width = 62.dp, height = 70.dp).drawableBackground(R.drawable.tr_preview_folder)) {
                    DrawableBox(R.drawable.tr_preview_app_violet, Modifier.offset(8.dp, 10.dp).size(20.dp))
                    DrawableBox(R.drawable.tr_preview_app_green, Modifier.offset(34.dp, 10.dp).size(20.dp))
                    DrawableBox(R.drawable.tr_preview_app_orange, Modifier.offset(8.dp, 38.dp).size(20.dp))
                    DrawableBox(R.drawable.tr_preview_app_pink, Modifier.offset(34.dp, 38.dp).size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    Box(Modifier.size(width = 78.dp, height = 140.dp).clip(RoundedCornerShape(14.dp))) {
        Image(painterResource(R.drawable.tr_preview_wallpaper), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        content()
    }
}

private const val CROSS_FADE_MS = 300
private const val COLUMNS = 3
