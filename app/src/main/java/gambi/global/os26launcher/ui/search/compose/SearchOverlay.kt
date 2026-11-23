package gambi.global.os26launcher.ui.search.compose

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DrawableBox
import gambi.global.os26launcher.core.designsystem.compose.DsSpinner
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.domain.config.TransitionStyle
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.search.SearchCapacity
import gambi.global.os26launcher.domain.search.SearchMode
import gambi.global.os26launcher.domain.search.SearchResult
import gambi.global.os26launcher.ui.search.SearchStatus
import gambi.global.os26launcher.ui.search.SearchUiState
import kotlin.math.roundToInt

/** JS2 — các lệnh lớp tìm kiếm (như tham số của `SearchResultAdapter` + callback của `SearchOverlayView`). */
internal class SearchActions(
    val onQueryChanged: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onCapacityChanged: (columns: Int, appRows: Int, webRows: Int) -> Unit,
    val onTapped: (SearchResult, ScreenRect?) -> Unit,
    val onContactCall: (SearchResult.Contact) -> Unit,
    val onContactMessage: (SearchResult.Contact) -> Unit,
    val onPromptDismiss: () -> Unit,
    val onHeaderToggle: (SearchResult.Header) -> Unit,
    val canCallAndMessage: Boolean,
    val loadIcon: suspend (SearchResult, Int) -> Bitmap?
)

/**
 * JS2 — lớp tìm kiếm bằng Compose (thay `SearchOverlayView` + `SearchResultAdapter` + `SpotlightCardDecoration`, JH3a thêm bằng
 * `addContentView`). Vẽ trên cùng của Home (trên App Library). Hai chế độ như bản View:
 * - Spotlight: kết quả phía trên, hàng ô nhập + "Hủy" ở cuối ngay trên bàn phím; ô trống = "Gần đây" (lưới trần) + thẻ thịnh hành; lúc gõ = thẻ
 *   app + thẻ gợi ý web; không cuộn (ViewModel giới hạn số hàng theo [SearchActions.onCapacityChanged]); vuốt lên quá 300 px = đóng.
 * - Library: ô nhập ở đầu, danh sách hàng cuộn được (cuộn thì ẩn bàn phím).
 *
 * Nền: Home phía sau đã nhoè (Activity đọc [onProgress]; API < 31 vẽ ảnh nền mờ [fallbackBackdrop]) + lớp tối `search_scrim` × 0,4. Chạm nền KHÔNG
 * đóng (đóng bằng Hủy / Back / Home / vuốt lên). Mở/đóng: mờ dần 150 ms theo kiểu chuyển cảnh (Trượt / Phóng to), nội dung lúc đóng giữ nguyên.
 */
@Composable
internal fun SearchOverlay(
    state: SearchUiState,
    actions: SearchActions,
    fallbackBackdrop: Bitmap?,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    // Lúc đóng: giữ nội dung đang hiện tới khi mờ hết (close() xóa truy vấn ngay, ViewModel đẩy ô trống; vẽ nó lúc đang mờ là giật).
    var shown by remember { mutableStateOf(state) }
    if (state.isOpen) shown = state
    val style = remember(state.isOpen) { TransitionMotion.style }
    LaunchedEffect(state.isOpen) {
        progress.animateTo(if (state.isOpen) 1f else 0f, tween(TransitionMotion.duration(FADE_MS).toInt()))
    }
    LaunchedEffect(Unit) { snapshotFlow { progress.value }.collect(onProgress) }
    val p = progress.value
    if (p <= 0f && !state.isOpen) return

    val s = shown
    val spotlight = s.mode == SearchMode.SPOTLIGHT
    val currentDismiss by rememberUpdatedState(actions.onDismiss)
    val dismissPx = 300f
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = p
                if (p < 1f) when (style) {
                    TransitionStyle.SLIDE -> translationY = size.height * SLIDE_FRACTION * (1f - p)
                    TransitionStyle.ZOOM -> {
                        val sc = ZOOM_START + (1f - ZOOM_START) * p
                        scaleX = sc
                        scaleY = sc
                    }
                    else -> Unit
                }
            }
            // Chặn chạm xuống Home; Spotlight: vuốt lên quá ngưỡng = đóng (đọc ở pass Initial, không tiêu thụ, như SearchDismissGesture).
            .pointerInput(spotlight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var tracking = spotlight
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (tracking && change.position.y - down.position.y < -dismissPx) {
                            tracking = false
                            // Như SearchDismissGesture trả true: nuốt sự kiện kích hoạt để ô/hàng đang nhấn hủy cú bấm, không mở app lúc đang đóng.
                            change.consume()
                            currentDismiss()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && fallbackBackdrop != null) {
            val small = remember(fallbackBackdrop) { shrink(fallbackBackdrop) }
            Image(small.asImageBitmap(), null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(colorResource(R.color.search_scrim).copy(alpha = 0.8f * DIM_WITH_BLUR)))
        // Truyền isOpen THẬT (không phải của [shown], vốn luôn là true): đóng thì ẩn bàn phím ngay, mở lại giữa lúc mờ dần thì đặt lại ô nhập.
        SearchContent(s, state.isOpen, spotlight, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchContent(state: SearchUiState, isOpen: Boolean, spotlight: Boolean, actions: SearchActions) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    // Mở lại với truy vấn khác (vd giữ truy vấn cũ, hoặc mở lại giữa lúc mờ dần khi close() đã xóa truy vấn): đặt chữ ô nhập theo
    // trạng thái, không báo lại ViewModel. Đóng: ẩn bàn phím + bỏ focus ngay (như SearchOverlayView.hide), chữ giữ tới khi mờ hết.
    LaunchedEffect(isOpen) {
        if (isOpen) {
            if (field.text != state.query) field = TextFieldValue(state.query, TextRange(state.query.length))
            focus.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val bars = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    val fieldRow: @Composable () -> Unit = {
        SearchFieldRow(
            value = field,
            onValueChange = {
                val changed = it.text != field.text
                field = it
                if (changed) actions.onQueryChanged(it.text)
            },
            onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
            },
            onCancel = actions.onDismiss,
            focus = focus
        )
    }
    if (spotlight) {
        // Số hàng vừa khít vùng danh sách (như SearchOverlayView.calculateAndReportCapacity): cả lớp - thanh trạng thái - hàng ô nhập
        // (kèm lề) - max(bàn phím, thanh điều hướng). Dùng ĐÍCH của bàn phím (imeAnimationTarget), không phải chiều cao đang trượt:
        // bản View nhận inset cuối (DISPATCH_MODE_STOP), nên lúc bàn phím trượt lên không báo dồn dập, kết quả không nhảy số hàng.
        val density = LocalDensity.current
        var totalH by remember { mutableIntStateOf(0) }
        var fieldRowH by remember { mutableIntStateOf(0) }
        val gapPx = with(density) { dimensionResource(R.dimen.search_field_margin_top).roundToPx() }
        val keyboardOrNav = maxOf(WindowInsets.imeAnimationTarget.getBottom(density), WindowInsets.navigationBars.getBottom(density))
        val listHeight = totalH - bars.getTop(density) - fieldRowH - gapPx - keyboardOrNav
        val headerH = with(density) { dimensionResource(R.dimen.search_si_header_height).roundToPx() }
        val cellH = with(density) { dimensionResource(R.dimen.search_si_grid_cell_height).roundToPx() }
        val webH = with(density) { dimensionResource(R.dimen.search_si_web_row_height).roundToPx() }
        val cardGapPx = with(density) { CARD_GAP.roundToPx() }
        val listTopPx = with(density) { LIST_TOP.roundToPx() }
        // Cùng điều kiện với bản View (chữ trong ô nhập): chỉ khoảng trắng vẫn là ô trống.
        val typing = field.text.isNotBlank()
        LaunchedEffect(listHeight, typing, state.recentCollapsed) {
            if (totalH <= 0 || fieldRowH <= 0) return@LaunchedEffect
            val capacity = if (!typing) {
                SearchCapacity.calculateEmptyCapacity(
                    listHeight, listTopPx, headerH, cellH, webH, COLUMNS,
                    recentRows = if (state.recentCollapsed) 1 else SearchCapacity.EMPTY_RECENT_ROWS
                )
            } else {
                SearchCapacity.calculateTypingCapacity(listHeight, cardGapPx, cellH, webH, cardGapPx, COLUMNS)
            }
            actions.onCapacityChanged(COLUMNS, capacity.appRows, capacity.webRows)
        }
        Column(Modifier.fillMaxSize().onSizeChanged { totalH = it.height }.windowInsetsPadding(bars).windowInsetsPadding(bottomInsets)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SpotlightResults(state, actions)
            }
            Box(Modifier.onSizeChanged { fieldRowH = it.height }) { fieldRow() }
            Spacer(Modifier.height(dimensionResource(R.dimen.search_field_margin_top)))
        }
    } else {
        Column(Modifier.fillMaxSize().windowInsetsPadding(bars)) {
            fieldRow()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LibraryResults(state, isOpen, actions, bottomInsets)
            }
        }
    }
}

@Composable
private fun SearchFieldRow(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    focus: FocusRequester
) {
    val hint = colorResource(R.color.search_hint_text)
    val margin = dimensionResource(R.dimen.search_field_margin_horizontal)
    Row(
        Modifier.fillMaxWidth().padding(start = margin, end = margin, top = dimensionResource(R.dimen.search_field_margin_top)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(colorResource(R.color.ds_accent)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.search_field_height))
                        .dsGlass(DsSurface.CONTROL, DsCorner.Fixed(FIELD_RADIUS_DP)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) BasicText(stringResource(R.string.search_field_hint), style = TextStyle(color = hint, fontSize = 17.sp), maxLines = 1)
                        inner()
                    }
                    if (value.text.isNotEmpty()) {
                        val clear = stringResource(R.string.search_clear)
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = clear }
                                .clickable(interactionSource = null, indication = null, role = Role.Button) { onValueChange(TextFieldValue("")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(painterResource(R.drawable.ic_search_clear), null)
                        }
                    }
                }
            }
        )
        val interaction = remember { MutableInteractionSource() }
        BasicText(
            stringResource(R.string.search_cancel),
            style = TextStyle(color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .padding(start = 8.dp)
                .heightIn(min = dimensionResource(R.dimen.search_field_height))
                .clip(RoundedCornerShape(12.dp))
                .clickable(interaction, indication = null, role = Role.Button, onClick = onCancel)
                .dsPressHighlight(interaction)
                .padding(horizontal = 8.dp, vertical = 13.dp)
        )
    }
}

@Composable
private fun StatusMessage(state: SearchUiState, modifier: Modifier): Boolean {
    val top = dimensionResource(R.dimen.search_idle_margin_top)
    when (state.status) {
        SearchStatus.LOADING -> {
            Box(modifier.fillMaxWidth().padding(top = top), contentAlignment = Alignment.TopCenter) {
                DsSpinner(stringResource(R.string.settings_loading), size = 32.dp)
            }
            return true
        }
        SearchStatus.IDLE, SearchStatus.NO_RESULTS -> {
            BasicText(
                stringResource(if (state.status == SearchStatus.IDLE) R.string.search_idle_hint else R.string.search_no_results),
                style = TextStyle(color = colorResource(R.color.search_hint_text), fontSize = 16.sp, textAlign = TextAlign.Center),
                modifier = modifier.fillMaxWidth().padding(top = top)
            )
            return true
        }
        SearchStatus.RESULTS -> return false
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Spotlight

/** Một đoạn liền nhau của kết quả Spotlight: tiêu đề, khối ô app, hoặc khối hàng web. */
private sealed interface Segment {
    data class Header(val header: SearchResult.Header) : Segment
    data class Apps(val apps: List<SearchResult>) : Segment
    data class Web(val rows: List<SearchResult.WebSuggestion>) : Segment
    data class Other(val result: SearchResult) : Segment
}

private fun segments(results: List<SearchResult>): List<Segment> {
    val out = ArrayList<Segment>()
    for (r in results) {
        val last = out.lastOrNull()
        when (r) {
            is SearchResult.Header -> out += Segment.Header(r)
            is SearchResult.App -> if (last is Segment.Apps) out[out.lastIndex] = Segment.Apps(last.apps + r) else out += Segment.Apps(listOf(r))
            is SearchResult.WebSuggestion -> if (last is Segment.Web) out[out.lastIndex] = Segment.Web(last.rows + r) else out += Segment.Web(listOf(r))
            else -> out += Segment.Other(r)
        }
    }
    return out
}

@Composable
private fun SpotlightResults(state: SearchUiState, actions: SearchActions) {
    val typing = state.query.isNotBlank()
    // Sức chứa (số hàng) tính ở SearchContent theo chiều cao cả lớp + đích bàn phím.
    val side = dimensionResource(R.dimen.search_field_margin_horizontal)
    Box(Modifier.fillMaxSize()) {
        if (StatusMessage(state, Modifier)) return@Box
        val segs = remember(state.results) { segments(state.results) }
        Column(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (typing) CARD_GAP else LIST_TOP)) {
            segs.forEachIndexed { i, seg ->
                val prev = segs.getOrNull(i - 1)
                if (prev != null && prev !is Segment.Header && seg !is Segment.Header) Spacer(Modifier.height(CARD_GAP))
                when (seg) {
                    is Segment.Header -> HeaderRow(seg.header, inset = HEADER_SPOTLIGHT_INSET, onToggle = actions.onHeaderToggle)
                    is Segment.Apps -> Card(enabled = typing) { AppGrid(seg.apps, actions) }
                    is Segment.Web -> Card(enabled = true) {
                        seg.rows.forEachIndexed { j, r -> WebRow(r, actions, divider = j < seg.rows.lastIndex) }
                    }
                    is Segment.Other -> ResultRow(seg.result, actions)
                }
            }
        }
    }
}

/** Thẻ mờ kiểu iOS (bo 16, `search_card_bg`); [enabled] = false: không vẽ thẻ (lưới "Gần đây" ở ô trống để trần). */
@Composable
private fun Card(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        Column { content() }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CARD_RADIUS)).background(colorResource(R.color.search_card_bg))) { content() }
}

@Composable
private fun AppGrid(apps: List<SearchResult>, actions: SearchActions) {
    apps.chunked(COLUMNS).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until COLUMNS) {
                val r = row.getOrNull(c)
                Box(Modifier.weight(1f)) { if (r is SearchResult.App) GridCell(r, actions) }
            }
        }
    }
}

@Composable
private fun GridCell(app: SearchResult.App, actions: SearchActions) {
    val sizePx = with(LocalDensity.current) { dimensionResource(R.dimen.search_result_icon_size).roundToPx() }
    val icon = rememberResultIcon(app, sizePx, actions.loadIcon, app.item.versionStamp)
    var rect by remember { mutableStateOf<ScreenRect?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.search_si_grid_cell_height))
            .clickable(interactionSource = null, indication = null, role = Role.Button) { actions.onTapped(app, rect) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).onGloballyPositioned { rect = it.screenRect() }) {
            if (icon != null) Image(icon, null, modifier = Modifier.fillMaxSize())
        }
        BasicText(
            app.item.label,
            style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
        )
    }
}

/** Hàng gợi ý web: kính lúp trên nền tròn xám + ">" (gợi ý), mũi tên đi lên (thịnh hành), la bàn không ">" (chữ đã gõ). */
@Composable
private fun WebRow(web: SearchResult.WebSuggestion, actions: SearchActions, divider: Boolean) {
    val typed = web.kind == SearchResult.WebSuggestion.SuggestionKind.TYPED
    val dividerColor = colorResource(R.color.search_card_divider)
    val insetPx = with(LocalDensity.current) { WEB_DIVIDER_INSET.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.search_si_web_row_height))
            .drawBehind {
                if (divider) {
                    val h = maxOf(1f, 0.5f * density)
                    drawRect(dividerColor, Offset(insetPx, size.height - h), androidx.compose.ui.geometry.Size(size.width - insetPx, h))
                }
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button) { actions.onTapped(web, null) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (typed) {
            Image(painterResource(R.drawable.ic_search_web_compass), null, modifier = Modifier.size(28.dp))
        } else {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                DrawableBox(R.drawable.bg_search_web_icon, Modifier.fillMaxSize())
                Image(
                    painterResource(if (web.kind == SearchResult.WebSuggestion.SuggestionKind.TRENDING) R.drawable.ic_search_trending else R.drawable.ic_search_pill_magnifier),
                    null,
                    modifier = Modifier.padding(5.dp).fillMaxSize()
                )
            }
        }
        BasicText(
            web.query,
            style = TextStyle(color = Color.White, fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
        if (!typed) Image(painterResource(R.drawable.ic_search_chevron), null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HeaderRow(header: SearchResult.Header, inset: androidx.compose.ui.unit.Dp, onToggle: (SearchResult.Header) -> Unit) {
    val title = stringResource(
        when (header.section) {
            SearchResult.Section.RECENT -> R.string.search_extras_header_recent
            SearchResult.Section.FREQUENT -> R.string.search_extras_header_frequent
            SearchResult.Section.TRENDING -> R.string.search_extras_header_trending
        }
    )
    Row(
        Modifier.fillMaxWidth().height(dimensionResource(R.dimen.search_si_header_height)).padding(horizontal = inset),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicText(
            title,
            style = TextStyle(color = colorResource(R.color.search_hint_text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(bottom = 8.dp).semantics { heading() }
        )
        val collapsed = header.collapsed
        if (collapsed != null) {
            BasicText(
                stringResource(if (collapsed) R.string.search_recent_expand else R.string.search_recent_collapse),
                style = TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) { onToggle(header) }.padding(start = 12.dp, bottom = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Library

@Composable
private fun LibraryResults(state: SearchUiState, isOpen: Boolean, actions: SearchActions, bottomInsets: WindowInsets) {
    if (StatusMessage(state, Modifier)) return
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    // Mở / đổi truy vấn: ghim đầu danh sách CHO TỚI KHI người dùng tự kéo (như keepAtTop của SearchOverlayView). Lúc mở, danh sách mới
    // (tính ở luồng nền) tới SAU; LazyColumn có key giữ mốc ở hàng đầu cũ (vd app gần đây, giờ nằm giữa A-Z) nên phải cuộn lại về 0
    // mỗi lần danh sách đổi khi còn ghim, không chỉ lúc đổi truy vấn.
    var keepAtTop by remember { mutableStateOf(true) }
    LaunchedEffect(state.query, isOpen) {
        if (!isOpen) return@LaunchedEffect // đang mờ dần khi đóng: giữ nguyên chỗ cuộn, không nhảy về đầu
        keepAtTop = true
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.results) { if (keepAtTop) listState.scrollToItem(0) }
    // Chỉ cú kéo của người dùng ẩn bàn phím + thôi ghim (scrollToItem lúc mở cũng bật isScrollInProgress: đo được 2026-11-20, bàn phím không hiện).
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) {
                keepAtTop = false
                keyboard?.hide()
            }
        }
    }
    val density = LocalDensity.current
    val bottom = with(density) { bottomInsets.getBottom(this).toDp() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = LIST_TOP, bottom = bottom)) {
        items(state.results, key = { it.stableId }) { r ->
            when (r) {
                is SearchResult.Header -> HeaderRow(r, inset = dimensionResource(R.dimen.search_field_margin_horizontal), onToggle = actions.onHeaderToggle)
                is SearchResult.WebSuggestion -> WebRow(r, actions, divider = false)
                else -> ResultRow(r, actions)
            }
        }
    }
}

/** Hàng kết quả (app, cài đặt, lối tắt, người liên hệ, lời mời Danh bạ): icon 48, tên 18sp, dòng phụ 13sp, nút gọi/nhắn/bỏ. */
@Composable
private fun ResultRow(result: SearchResult, actions: SearchActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sizePx = with(LocalDensity.current) { dimensionResource(R.dimen.search_result_icon_size).roundToPx() }
    val stamp = (result as? SearchResult.App)?.item?.versionStamp
    val fixedIcon = when (result) {
        is SearchResult.Setting -> R.drawable.ic_search_setting
        is SearchResult.ContactsPermissionPrompt -> R.drawable.ic_search_contacts_prompt
        else -> null
    }
    val icon = if (fixedIcon == null) rememberResultIcon(result, sizePx, actions.loadIcon, stamp) else null
    var rect by remember { mutableStateOf<ScreenRect?>(null) }
    val label = when (result) {
        is SearchResult.App -> result.item.label
        is SearchResult.Setting -> result.entry.label
        is SearchResult.Shortcut -> result.entry.label
        is SearchResult.Contact -> result.entry.name
        is SearchResult.ContactsPermissionPrompt -> stringResource(R.string.search_contacts_prompt_title)
        is SearchResult.WebSuggestion -> result.query
        is SearchResult.Header -> ""
    }
    val subtitle: String? = when (result) {
        is SearchResult.Contact -> result.phone ?: stringResource(R.string.search_contacts_kind)
        is SearchResult.ContactsPermissionPrompt -> stringResource(
            if (result.openSettings) R.string.search_contacts_prompt_settings_subtitle else R.string.search_contacts_prompt_subtitle
        )
        is SearchResult.Shortcut -> result.entry.appLabel
        is SearchResult.Setting -> stringResource(R.string.search_extras_kind_setting)
        else -> null
    }
    val description: String? = when (result) {
        is SearchResult.Shortcut -> stringResource(R.string.search_extras_shortcut_description, result.entry.label, result.entry.appLabel)
        is SearchResult.Setting -> stringResource(R.string.search_extras_setting_description, result.entry.label)
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimensionResource(R.dimen.search_result_row_height))
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .clickable(interaction, indication = null, role = Role.Button) { actions.onTapped(result, rect) }
            .dsPressHighlight(interaction)
            .padding(horizontal = dimensionResource(R.dimen.search_field_margin_horizontal), vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(dimensionResource(R.dimen.search_result_icon_size)).onGloballyPositioned { rect = it.screenRect() }) {
            if (fixedIcon != null) Image(painterResource(fixedIcon), null, modifier = Modifier.fillMaxSize())
            else if (icon != null) Image(icon, null, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            BasicText(label, style = TextStyle(color = Color.White, fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    style = TextStyle(color = colorResource(R.color.search_hint_text), fontSize = 13.sp),
                    maxLines = if (result is SearchResult.ContactsPermissionPrompt) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (result is SearchResult.Contact && result.phone != null && actions.canCallAndMessage) {
            RowButton(R.drawable.ic_contact_call, stringResource(R.string.search_contacts_call, result.entry.name)) { actions.onContactCall(result) }
            RowButton(R.drawable.ic_contact_message, stringResource(R.string.search_contacts_message, result.entry.name)) { actions.onContactMessage(result) }
        }
        if (result is SearchResult.ContactsPermissionPrompt) {
            RowButton(R.drawable.ic_search_dismiss, stringResource(R.string.search_contacts_prompt_dismiss), actions.onPromptDismiss)
        }
    }
}

@Composable
private fun RowButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(dimensionResource(R.dimen.search_contact_button_size))
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(12.dp)
    ) {
        Image(painterResource(icon), null, colorFilter = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun rememberResultIcon(result: SearchResult, sizePx: Int, loader: suspend (SearchResult, Int) -> Bitmap?, stamp: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, result.stableId, stamp, sizePx) {
        value = null // produceState không gắn giá trị với khóa (bài học JH1)
        value = loader(result, sizePx)?.asImageBitmap()
    }
    return bitmap
}

private fun androidx.compose.ui.layout.LayoutCoordinates.screenRect(): ScreenRect {
    val b = boundsInWindow() // như HomeGridPage: lớp phủ đầy cửa sổ toàn màn hình nên trùng tọa độ màn hình của bản View
    return ScreenRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
}

private fun shrink(src: Bitmap): Bitmap {
    val f = 96f / maxOf(src.width, src.height).coerceAtLeast(1)
    if (f >= 1f) return src
    return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true)
}

private const val FADE_MS = 150L
private const val SLIDE_FRACTION = 0.12f
private const val ZOOM_START = 0.92f
private const val DIM_WITH_BLUR = 0.4f
private const val FIELD_RADIUS_DP = 24f
private val CARD_RADIUS = 16.dp
private val CARD_GAP = 12.dp
private val LIST_TOP = 8.dp
private val WEB_DIVIDER_INSET = 56.dp
private val HEADER_SPOTLIGHT_INSET = 4.dp
private const val COLUMNS = 4
