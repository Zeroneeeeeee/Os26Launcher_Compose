package gambi.global.os26launcher.ui.settings.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import gambi.global.os26launcher.core.designsystem.compose.DsBadge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsCorners
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsIconTile
import gambi.global.os26launcher.core.designsystem.compose.DsRow
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsSearchField
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.ui.settings.SettingsRow
import gambi.global.os26launcher.ui.settings.SettingsScreen

/**
 * JS1 — các lệnh của danh sách Cài đặt (thay tham số của `SettingsAdapter`). Mặc định không làm gì, nên màn chỉ cần khai cái nó dùng.
 * [loadIcon] null = hàng app không có icon (RN/BG).
 */
class SettingsListActions(
    val onValueClicked: (SettingsRow.Value) -> Unit = {},
    val onSwitchChanged: (SettingsRow.Switch, Boolean) -> Unit = { _, _ -> },
    val onAppNameClicked: (SettingsRow.AppName) -> Unit = {},
    val onSliderCommitted: (SettingsRow.Slider, Int) -> Unit = { _, _ -> },
    val onSliderMoved: (SettingsRow.Slider, Int) -> Unit = { _, _ -> },
    val onLauncherCardClicked: () -> Unit = {},
    val onRadioClicked: (SettingsRow.Radio) -> Unit = {},
    val onSegmentChanged: (SettingsRow.Segmented, Int) -> Unit = { _, _ -> },
    val onWallpaperTileClicked: (Int) -> Unit = {},
    val onSearchChanged: (SettingsRow.Search, String) -> Unit = { _, _ -> },
    val onPermissionCardClicked: (SettingsRow.PermissionCard) -> Unit = {},
    val onBadgeStyleClicked: (SettingsRow.BadgeStyle, Int) -> Unit = { _, _ -> },
    val onLibraryStyleClicked: (SettingsRow.LibraryStyle, Int) -> Unit = { _, _ -> },
    val onAppSwitchChanged: (SettingsRow.AppSwitch, Boolean) -> Unit = { _, _ -> },
    val loadIcon: (suspend (ComponentKey, Int) -> Bitmap?)? = null
)

/** Mọi lệnh của danh sách nối thẳng vào [SettingsScreen] (khung Cài đặt). */
fun SettingsScreen.listActions(): SettingsListActions = SettingsListActions(
    onValueClicked = ::onValueClicked,
    onSwitchChanged = ::onSwitchChanged,
    onAppNameClicked = ::onAppNameClicked,
    onLauncherCardClicked = ::onLauncherCardClicked,
    onRadioClicked = ::onRadioClicked,
    onSegmentChanged = ::onSegmentChanged,
    onWallpaperTileClicked = ::onWallpaperTileClicked,
    onSearchChanged = ::onSearchChanged,
    onPermissionCardClicked = ::onPermissionCardClicked,
    onBadgeStyleClicked = ::onBadgeStyleClicked,
    onLibraryStyleClicked = ::onLibraryStyleClicked,
    onAppSwitchChanged = ::onAppSwitchChanged,
    loadIcon = appIconLoader
)

/** Vai của một hàng với card nhóm (như `CardKind` của `SettingsCardDecoration`). */
internal enum class RowKind { HEADER, FOOTER, CARD, PLAIN, BREAK }

internal fun kindOf(row: SettingsRow): RowKind = when (row) {
    is SettingsRow.Header, is SettingsRow.HeaderBadge -> RowKind.HEADER // HL-1
    is SettingsRow.Intro -> RowKind.PLAIN // HL-1
    is SettingsRow.Footer -> RowKind.FOOTER
    is SettingsRow.CardBreak -> RowKind.BREAK
    is SettingsRow.IconStylePreview, // P3-3: tự vẽ ảnh nền bo góc
    is SettingsRow.TransitionPreview,
    is SettingsRow.LauncherCard,
    is SettingsRow.WeatherPreview,
    is SettingsRow.WallpaperGrid,
    is SettingsRow.PermissionCard,
    is SettingsRow.BadgeStyle,
    is SettingsRow.LibraryStyle,
    is SettingsRow.Search -> RowKind.PLAIN
    else -> RowKind.CARD
}

/** Khoảng trên của hàng [i] (dp), đúng các luật của `SettingsCardDecoration.getItemOffsets` (Figma: tiêu đề cách card 12, nhóm cách 20). */
private fun topGapDp(kinds: List<RowKind>, i: Int): Int {
    val prev = kinds.getOrNull(i - 1)
    return when (kinds[i]) {
        RowKind.HEADER -> if (i == 0) 0 else SECTION_GAP
        RowKind.PLAIN -> if (prev == RowKind.CARD || prev == RowKind.PLAIN) SECTION_GAP else 0
        RowKind.FOOTER -> if (prev == RowKind.CARD) FOOTER_GAP else if (i == 0) 0 else SECTION_GAP
        RowKind.CARD -> if (prev == RowKind.FOOTER || prev == RowKind.PLAIN || prev == RowKind.BREAK) SECTION_GAP else 0
        RowKind.BREAK -> 0
    }
}

/**
 * JS1 — danh sách Cài đặt bằng Compose (thay `SettingsAdapter` + `SettingsCardDecoration`). Phải nằm trong `DsScreen` (kính lấy nền
 * từ `LocalDsBackdrop`).
 *
 * Card nhóm: mỗi nhóm hàng `CARD` liền nhau có MỘT bề mặt kính (vẽ ở lớp dưới danh sách, đặt theo vị trí các hàng đang hiện), không ghép
 * từng hàng: kính không lộ vết nối (bài học RS) và số bề mặt kính thật trên màn chỉ bằng số nhóm đang hiện, không bằng số hàng (JD D9:
 * số bề mặt kính là chi phí chính trên máy yếu). Lớp kính đọc `layoutInfo` trong pha layout nên cuộn chỉ đặt lại chỗ, không dựng lại.
 * Nhóm bị cắt ở mép danh sách thì kéo dài card ra ngoài 2 × bán kính để góc bo không hiện giữa chừng.
 *
 * [resetKey] đổi = bỏ trạng thái tạm của các hàng (công tắc đã lật nhưng ghi lỗi): như `notifyDataSetChanged` của bản View.
 */
@Composable
fun SettingsList(
    rows: List<SettingsRow>,
    actions: SettingsListActions,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    resetKey: Int = 0
) {
    val kinds = remember(rows) { rows.map(::kindOf) }
    val groups = remember(kinds) { cardGroups(kinds) }
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    val density = LocalDensity.current
    val topGapsPx = remember(kinds, density) { IntArray(kinds.size) { with(density) { topGapDp(kinds, it).dp.roundToPx() } } }

    Box(modifier.fillMaxSize()) {
        CardGlassLayer(groups, topGapsPx, state)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimensionResource(R.dimen.ds_screen_content_top),
                bottom = dimensionResource(R.dimen.ds_screen_content_bottom)
            )
        ) {
            itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, row -> row::class }) { i, row ->
                val kind = kinds[i]
                val first = kinds.getOrNull(i - 1) != RowKind.CARD
                val last = kinds.getOrNull(i + 1) != RowKind.CARD
                val gap = with(density) { topGapsPx[i].toDp() }
                val bottom = if (kind == RowKind.HEADER) dimensionResource(R.dimen.ds_section_header_gap) else 0.dp
                Box(Modifier.fillMaxWidth().padding(start = margin, end = margin, top = gap, bottom = bottom)) {
                    if (kind == RowKind.CARD) {
                        Box(Modifier.clipToGroup(first, last)) { CardRow(row, last, actions, resetKey) }
                    } else {
                        PlainRow(row, actions)
                    }
                }
            }
        }
    }
}

/** Các nhóm hàng CARD liền nhau, theo chỉ số trong danh sách. */
private fun cardGroups(kinds: List<RowKind>): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    for (i in kinds.indices) {
        if (kinds[i] == RowKind.CARD) {
            if (start < 0) start = i
        } else if (start >= 0) {
            out += start until i
            start = -1
        }
    }
    if (start >= 0) out += start until kinds.size
    return out
}

/**
 * Lớp card kính dưới danh sách: một khối `dsGlass(CARD)` cho mỗi nhóm, đo và đặt trong pha layout theo `state.layoutInfo` (cuộn không
 * dựng lại composition). Nhóm không có hàng nào đang hiện thì không đặt (không vẽ).
 */
@Composable
private fun CardGlassLayer(groups: List<IntRange>, topGapsPx: IntArray, state: LazyListState) {
    val marginPx = with(LocalDensity.current) { dimensionResource(R.dimen.ds_screen_margin_h).roundToPx() }
    val overflowPx = with(LocalDensity.current) { dimensionResource(R.dimen.ds_radius_card).roundToPx() * 2 }
    Layout(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        content = { groups.forEach { _ -> Box(Modifier.dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))) } }
    ) { measurables, constraints ->
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        // Toạ độ trong Box = offset của mục - viewportStartOffset (âm khi có contentPadding trên).
        val shift = -info.viewportStartOffset
        val viewportEnd = constraints.maxHeight
        val width = (constraints.maxWidth - marginPx * 2).coerceAtLeast(0)
        val placed = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()
        groups.forEachIndexed { g, range ->
            val items = visible.filter { it.index in range }
            if (items.isEmpty()) return@forEachIndexed
            val firstItem = items.minBy { it.index }
            val lastItem = items.maxBy { it.index }
            val top = if (firstItem.index == range.first) firstItem.offset + shift + topGapsPx[range.first] else -overflowPx
            val bottom = if (lastItem.index == range.last) lastItem.offset + shift + lastItem.size else viewportEnd + overflowPx
            val h = (bottom - top).coerceAtLeast(0)
            if (h == 0 || width == 0) return@forEachIndexed
            placed += Triple(measurables[g].measure(Constraints.fixed(width, h)), marginPx, top)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

/** Hàng đầu/cuối nhóm cắt theo góc bo của card để lớp nhấn không tràn góc (như `SettingsCardDecoration.clipToGroup`). */
private fun Modifier.clipToGroup(first: Boolean, last: Boolean): Modifier {
    if (!first && !last) return this
    return this.then(Modifier.clipGroupCorners(first, last))
}

@Composable
private fun cardRadiusDp(size: IntSize): androidx.compose.ui.unit.Dp {
    val context = LocalContext.current
    val px = if (size.width > 0) DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.CARD), size.width, size.height) else 0f
    return with(LocalDensity.current) { px.toDp() }
}

private fun Modifier.clipGroupCorners(first: Boolean, last: Boolean): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val r = cardRadiusDp(size)
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp
    )
    onSizeChanged { size = it }.clip(shape)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Hàng trong card

@Composable
private fun CardRow(row: SettingsRow, last: Boolean, actions: SettingsListActions, resetKey: Int) {
    val context = LocalContext.current
    when (row) {
        is SettingsRow.Value -> {
            val title = row.titleText ?: stringResource(row.title)
            DsRow(
                title = title,
                onClick = if (row.enabled) ({ actions.onValueClicked(row) }) else null,
                modifier = if (row.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
                leading = row.tile?.let { tile -> { DsIconTile(tile.icon, tile.color) } },
                trailing = if (row.value.isEmpty()) DsRowTrailing.Chevron else DsRowTrailing.Value(row.value),
                showDivider = !last
            )
        }

        is SettingsRow.Switch -> {
            // Lật ngay khi chạm (như SwitchCompat của bản View); dữ liệu mới (hoặc resetKey) đặt lại về trạng thái thật.
            var checked by remember(row.checked, resetKey) { mutableStateOf(row.checked) }
            DsRow(
                title = stringResource(row.title),
                onClick = {
                    checked = !checked
                    actions.onSwitchChanged(row, checked)
                },
                description = if (row.description != 0) stringResource(row.description) else null,
                leading = row.tile?.let { tile -> { DsIconTile(tile.icon, tile.color) } },
                trailing = DsRowTrailing.Toggle(checked),
                showDivider = !last
            )
        }

        is SettingsRow.Radio -> DsRow(
            title = row.titleText ?: stringResource(row.title),
            // DsRowView chỉ báo khi chọn hàng CHƯA chọn; bỏ chọn hàng khác do màn dựng lại danh sách.
            onClick = { if (!row.checked) actions.onRadioClicked(row) },
            description = row.descriptionText ?: if (row.description != 0) stringResource(row.description) else null,
            trailing = DsRowTrailing.Radio(row.checked),
            showDivider = !last
        )

        is SettingsRow.Segmented -> SegmentedRow(row, last, actions)
        is SettingsRow.Slider -> SliderRow(row, last, actions, resetKey)
        is SettingsRow.AppName -> {
            val app = row.app
            val subtitle = when {
                app.label != app.originalLabel -> stringResource(R.string.settings_rename_original, app.originalLabel)
                app.a11yLabel.isNotEmpty() -> app.a11yLabel
                else -> null
            }
            DsRow(
                title = app.label,
                onClick = { actions.onAppNameClicked(row) },
                description = subtitle,
                leading = actions.loadIcon?.let { loader -> { AppIcon(app, loader) } },
                trailing = row.value?.let { DsRowTrailing.Value(it) } ?: DsRowTrailing.Chevron, // P3-4: "Đã đổi" trước chevron
                showDivider = !last
            )
        }

        is SettingsRow.AppSwitch -> {
            val app = row.app
            var checked by remember(row.checked, resetKey) { mutableStateOf(row.checked) }
            DsRow(
                title = app.label,
                onClick = {
                    checked = !checked
                    actions.onAppSwitchChanged(row, checked)
                },
                description = app.a11yLabel.ifEmpty { null },
                leading = actions.loadIcon?.let { loader -> { AppIcon(app, loader) } },
                trailing = DsRowTrailing.Toggle(checked),
                showDivider = !last
            )
        }

        else -> PlainRow(row, actions) // không xảy ra: kindOf chỉ cho các loại trên vào card
    }
}

/** RN/BG: icon app đầu hàng (32 dp như ô DS). Nạp lại khi app hoặc APK (versionStamp) đổi. */
@Composable
private fun AppIcon(app: AppItem, loader: suspend (ComponentKey, Int) -> Bitmap?) {
    val sizePx = with(LocalDensity.current) { dimensionResource(R.dimen.library_icon_size).roundToPx() }
    // produceState không gắn giá trị với khóa: gán null trước để không hiện icon của app cũ (bài học JH1).
    val bitmap by produceState<Bitmap?>(null, app.key, app.versionStamp) {
        value = null
        value = loader(app.key, sizePx)
    }
    val size = dimensionResource(R.dimen.ds_tile_size)
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Hàng ngoài card

@Composable
private fun PlainRow(row: SettingsRow, actions: SettingsListActions) {
    when (row) {
        is SettingsRow.Header -> BasicText(
            stringResource(row.title),
            style = DsTextStyles.sectionHeader.copy(color = colorResource(R.color.ds_text_secondary)),
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )

        is SettingsRow.Footer -> BasicText(
            row.text,
            style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_tertiary), textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )

        // HL-1: dòng mô tả đầu màn, canh trái, ngoài card (Figma 5.1).
        is SettingsRow.Intro -> BasicText(
            row.text,
            style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)),
            modifier = Modifier.fillMaxWidth()
        )

        // HL-1: tiêu đề nhóm + nhãn viên thuốc bên phải ("Tất cả ứng dụng" + "2 đang ẩn").
        is SettingsRow.HeaderBadge -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                stringResource(row.title),
                style = DsTextStyles.sectionHeader.copy(color = colorResource(R.color.ds_text_secondary)),
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            row.badge?.let { DsBadge(it) }
        }

        is SettingsRow.IconStylePreview -> IconStylePreviewCard(row.background, row.icons) // P3-3

        is SettingsRow.Search -> {
            // LG: chữ gõ chỉ sống ở đây (hàng không đổi khi danh sách lọc nên ô giữ con trỏ và bàn phím).
            var query by remember(row.id) { mutableStateOf("") }
            DsSearchField(
                value = query,
                onValueChange = {
                    query = it
                    actions.onSearchChanged(row, it)
                },
                hint = stringResource(row.hint),
                modifier = Modifier.fillMaxWidth()
            )
        }

        is SettingsRow.LauncherCard -> LauncherCard(row, actions.onLauncherCardClicked)
        is SettingsRow.WeatherPreview -> WeatherPreviewCard(row)
        is SettingsRow.WallpaperGrid -> WallpaperGrid(row, actions.onWallpaperTileClicked)
        is SettingsRow.PermissionCard -> PermissionCard(row) { actions.onPermissionCardClicked(row) }
        is SettingsRow.BadgeStyle -> BadgeStyleOptions(row.selected) { actions.onBadgeStyleClicked(row, it) }
        is SettingsRow.LibraryStyle -> LibraryStyleOptions(row.selected) { actions.onLibraryStyleClicked(row, it) }
        is SettingsRow.TransitionPreview -> TransitionPreview()
        is SettingsRow.CardBreak -> Unit
        else -> Column {} // hàng card lọt ra ngoài (không xảy ra)
    }
}

private const val SECTION_GAP = 20 // ds_space_20
private const val FOOTER_GAP = 12 // ds_space_12
private const val DISABLED_ALPHA = 0.5f
