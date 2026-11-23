package gambi.global.os26launcher.ui.home.compose.today

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.designsystem.compose.wallpaperSoftBlur
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.today.TodayWidget
import gambi.global.os26launcher.ui.home.compose.HomeIcons
import gambi.global.os26launcher.ui.home.compose.HomeRemoveBadge
import gambi.global.os26launcher.ui.home.compose.HomeWidgetCell
import gambi.global.os26launcher.ui.home.compose.MINUS_BADGE_RATIO
import gambi.global.os26launcher.ui.home.compose.WIDGET_JIGGLE_SCALE
import gambi.global.os26launcher.ui.home.compose.homeJiggle
import gambi.global.os26launcher.ui.home.compose.library.SCRIM_FLAT_DARK
import gambi.global.os26launcher.ui.home.compose.library.SCRIM_FLAT_LIGHT
import gambi.global.os26launcher.ui.home.compose.library.SCRIM_TINT_DARK
import gambi.global.os26launcher.ui.home.compose.library.SCRIM_TINT_LIGHT
import gambi.global.os26launcher.ui.home.compose.library.shrinkForBlur
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * TV-2/TV-3 — Today View (vuốt phải từ trang đầu của Home), bản Compose của `TodayView` + `TodayGridLayout` (REF 3ab9c20). Vẽ ở Activity phía
 * trên `HomeScreen`, cùng tầng với App Library; cử chỉ mở/đóng là `LibraryOverlayController` ngược chiều trong [HomeTodayState].
 *
 * Lớp, từ dưới lên:
 * 1. Nền: ảnh nền làm mờ + lớp tối, hiện dần theo tiến độ; ghi làm lớp backdrop riêng cho kính của widget (tối đa [GLASS_BUDGET] widget đầu là kính
 *    thật, JD D9).
 * 2. Trang: trượt vào từ TRÁI. Trên cùng (cố định): thanh tìm kiếm (nút mở lớp tìm kiếm); ở chế độ sửa thay bằng hai viên "+" (mở bảng chọn widget)
 *    và "Xong". Dưới: danh sách widget cuộn dọc trên lưới 4 cột cỡ ô của Home ([cells], ép 4 cột, đo trên kích thước pager); khung cuộn phủ cả màn,
 *    nội dung bắt đầu dưới thanh, cuộn lên quá đáy thanh thì widget mờ dần (hết hẳn khi đã qua [FADE_FRACTION] chiều cao của nó).
 *
 * Xếp ô ([todaySlots], như `TodayGridLayout.slots`): widget 2 × 2 lấp nửa trái rồi nửa phải của một hàng; widget 4 × 2 chiếm cả hàng (nửa phải còn
 * trống thì để trống). Chế độ sửa (như Home): nhấn giữ widget hay chỗ trống thì vào; widget rung, có dấu "−"; giữ đủ lâu rồi kéo một widget thì đổi
 * chỗ (widget khác dời bằng lò xo REORDER), kéo sát mép trên/dưới thì tự cuộn; chạm chỗ trống thì thoát.
 */
@Composable
internal fun HomeToday(
    state: HomeTodayState,
    model: TodayGridModel,
    apps: List<AppItem>?,
    editing: Boolean,
    backdrop: Bitmap?,
    /** Cỡ ô của lưới Home ép 4 cột; null = chưa đo được (để trống). Gọi lúc màn hiện. */
    cells: GridMetrics.Cells?,
    icons: HomeIcons,
    actions: TodayActions,
    modifier: Modifier = Modifier
) {
    val t = state.swipeProgress.floatValue
    if (t <= 0f) return
    val night = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val blurred = remember(backdrop) { backdrop?.let(::shrinkForBlur) }
    val todayBackdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxSize().blockTouches()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = t.coerceIn(0f, 1f) }
                .layerBackdrop(todayBackdrop)
                .background(Color(if (night) SCRIM_FLAT_DARK else SCRIM_FLAT_LIGHT))
        ) {
            if (blurred != null) {
                Image(
                    blurred.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().wallpaperSoftBlur()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (night) SCRIM_TINT_DARK else SCRIM_TINT_LIGHT)))
        }
        CompositionLocalProvider(LocalDsBackdrop provides todayBackdrop) {
            if (cells != null) {
                TodayPage(
                    state = state,
                    model = model,
                    apps = apps,
                    editing = editing,
                    cells = cells,
                    icons = icons,
                    actions = actions,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -size.width * (1f - t) }
                )
            }
        }
    }
}

/** Lớp phủ toàn màn là đích chạm (không tiêu thụ), nên Home bên dưới không nhận chạm; con vẫn nhận bình thường. Như `blockTouches` của Library. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
        } while (event.changes.any { it.pressed })
    }
}

/** TV-3 — việc của Today View, nối vào `HomeViewModel` / Activity (như các callback của `TodayView`). */
internal class TodayActions(
    val onSearchClick: () -> Unit,
    val onWidgetClick: (WidgetKind, Long) -> Unit,
    val onAppClick: (AppItem, ScreenRect?) -> Unit,
    val onEnterEditMode: () -> Unit,
    val onDone: () -> Unit,
    /** Viên "+": mở bảng chọn widget của Today. */
    val onAdd: () -> Unit,
    /** Dấu "−": hỏi xác nhận rồi xóa. */
    val onRemove: (Long) -> Unit,
    /** Thả sau khi kéo đổi chỗ: thứ tự id mới. */
    val onReorder: (List<Long>) -> Unit
)

/** Khung (trái, trên, rộng) của từng widget theo thứ tự [list] — cùng phép xếp cho bố cục và cho dò ô đích khi kéo (`TodayGridLayout.slots`). */
internal fun todaySlots(list: List<TodayWidget>, c: GridMetrics.Cells, top: Int): List<IntArray> {
    val step = 2 * (c.cellHeight + c.gutterY)
    val half = 2 * c.cellWidth + c.gutterX
    val full = 4 * c.cellWidth + 3 * c.gutterX
    var row = 0
    var leftUsed = false
    return list.map { w ->
        if (w.wide) {
            if (leftUsed) {
                row++
                leftUsed = false
            }
            intArrayOf(c.paddingLeft, top + row++ * step, full)
        } else if (!leftUsed) {
            leftUsed = true
            intArrayOf(c.paddingLeft, top + row * step, half)
        } else {
            leftUsed = false
            intArrayOf(c.paddingLeft + half + c.gutterX, top + row++ * step, half)
        }
    }
}

private fun blockHeight(c: GridMetrics.Cells): Int = 2 * c.cellHeight + c.gutterY

@Composable
private fun TodayPage(
    state: HomeTodayState,
    model: TodayGridModel,
    apps: List<AppItem>?,
    editing: Boolean,
    cells: GridMetrics.Cells,
    icons: HomeIcons,
    actions: TodayActions,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val topInset = insets.getTop(density)
    val bottomInset = insets.getBottom(density)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Cử chỉ đọc chế độ sửa lúc chạy (không làm khóa của pointerInput): nhấn giữ vào chế độ sửa rồi kéo tiếp trong CÙNG chuỗi chạm.
    val editingNow = androidx.compose.runtime.rememberUpdatedState(editing)
    fun px(dp: Float): Int = (dp * density.density).toInt()

    // Như TodayView.place: thanh trên cùng cách thanh trạng thái search_field_margin_top; viên "+" / "Xong" cao PILL_TOUCH_DP căn giữa theo thanh.
    val barTop = topInset + with(density) { dimensionResource(R.dimen.search_field_margin_top).roundToPx() }
    val barH = with(density) { dimensionResource(R.dimen.search_field_height).roundToPx() }
    val pillH = px(PILL_TOUCH_DP)
    val barBottom = barTop + maxOf(barH, pillH)
    val scrollTop = barBottom + px(SCROLL_GAP_DP)
    val gridTop = scrollTop + px(GRID_TOP_DP) // đệm trên: chỗ cho dấu "−" của hàng đầu nhô lên
    val spanW = 4 * cells.cellWidth + 3 * cells.gutterX
    val bh = blockHeight(cells)

    val entries = model.entries.toList()
    val slots = todaySlots(entries, cells, gridTop)
    val slotById = HashMap<Long, IntArray>(slots.size * 2).also { m -> entries.forEachIndexed { i, w -> m[w.id] = slots[i] } }
    SideEffect {
        model.layout = TodayGridModel.Layout(entries.map { it.id }, slots, bh, scrollTop)
    }
    val glassIds = remember(entries) { entries.take(GLASS_BUDGET).mapTo(HashSet()) { it.id } }
    val backdrop = LocalDsBackdrop.current

    BoxWithConstraints(modifier) {
        val viewportH = constraints.maxHeight
        val contentBottom = if (slots.isEmpty()) 0 else slots.maxOf { it[1] } + bh
        val contentH = maxOf(viewportH, contentBottom + bottomInset + px(GRID_BOTTOM_DP))
        // Kéo: tự cuộn khi ngón tay ở sát mép trên (đáy thanh trên cùng) / mép dưới của khung.
        val dragging = model.dragId.value != null
        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            val edge = AUTO_SCROLL_EDGE_DP * density.density
            val step = AUTO_SCROLL_STEP_DP * density.density
            while (isActive && model.dragId.value != null) {
                androidx.compose.runtime.withFrameNanos { }
                val y = model.fingerViewportY
                val dy = when {
                    y < scrollTop + edge && scroll.value > 0 -> -step
                    y > viewportH - edge && scroll.value < scroll.maxValue -> step
                    else -> 0f
                }
                if (dy != 0f) {
                    val consumed = scroll.scrollBy(dy)
                    model.onDragMove(Offset(model.dragFinger.x, model.dragFinger.y + consumed), scroll.value)
                }
            }
        }
        // Widget vừa thêm (sau lần nạp đầu): cuộn cho nó lọt vào khung (đáy widget + một khoảng, sát đáy khung).
        val added = model.lastAdded.value
        LaunchedEffect(added) {
            val id = added ?: return@LaunchedEffect
            val s = slotById[id] ?: return@LaunchedEffect
            val target = s[1] + bh + px(GRID_BOTTOM_DP) - viewportH
            if (target > scroll.value) scroll.animateScrollTo(target)
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !dragging)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { contentH.toDp() })
                    .todayGestures(model, state, scroll, { editingNow.value }, actions) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ) {
                // Widget đang rời (vừa xóa): thu nhỏ + mờ tại chỗ rồi mới gỡ.
                model.leaving.forEach { leaving ->
                    key("leaving-${leaving.widget.id}") {
                        TodayLeaving(model, leaving, cells, bh, apps, icons)
                    }
                }
                entries.forEach { w ->
                    key(w.id) {
                        val s = slotById.getValue(w.id)
                        CompositionLocalProvider(LocalDsBackdrop provides if (w.id in glassIds) backdrop else null) {
                            TodayEntry(
                                model = model,
                                widget = w,
                                slot = s,
                                blockH = bh,
                                cells = cells,
                                editing = editing,
                                apps = apps,
                                icons = icons,
                                actions = actions,
                                scroll = scroll,
                                fadeLine = barBottom.toFloat()
                            )
                        }
                    }
                }
            }
        }

        // Thanh trên cùng (cố định trên khung cuộn).
        val left = cells.paddingLeft
        if (!editing) {
            TodaySearchBar(
                actions.onSearchClick,
                Modifier
                    .offset { IntOffset(left, barTop) }
                    .size(with(density) { spanW.toDp() }, with(density) { barH.toDp() })
            )
        } else {
            val pillTop = barTop + (maxOf(barH, pillH) - pillH) / 2
            TodayPill(
                "+", TITLE_PLUS_SP, padH = 0f,
                description = stringResource(R.string.wp_add_widget),
                onClick = actions.onAdd,
                modifier = Modifier.offset { IntOffset(left, pillTop) }
            )
            Box(
                Modifier
                    .offset { IntOffset(left, pillTop) }
                    .width(with(density) { spanW.toDp() }),
                contentAlignment = Alignment.TopEnd
            ) {
                TodayPill(stringResource(R.string.home_done), TITLE_DONE_SP, padH = PILL_PADDING_H_DP, onClick = actions.onDone)
            }
        }
    }
}

/** Một widget của danh sách: vị trí lò xo (FLIP khi đổi thứ tự), phình ra khi vừa thêm, bám ngón tay khi kéo, rung + dấu "−" ở chế độ sửa. */
@Composable
private fun TodayEntry(
    model: TodayGridModel,
    widget: TodayWidget,
    slot: IntArray,
    blockH: Int,
    cells: GridMetrics.Cells,
    editing: Boolean,
    apps: List<AppItem>?,
    icons: HomeIcons,
    actions: TodayActions,
    scroll: ScrollState,
    fadeLine: Float
) {
    val density = LocalDensity.current
    val target = Offset(slot[0].toFloat(), slot[1].toFloat())
    val pos = model.positionOf(widget.id, target)
    val dragged = model.dragId.value == widget.id
    LaunchedEffect(target, dragged) {
        if (dragged) return@LaunchedEffect
        if (model.dropId == widget.id) {
            pos.snapTo(model.dropAt) // vừa thả: từ chỗ ngón tay về đúng ô mới bằng lò xo REORDER
            model.dropId = null
        } else {
            val wait = model.slideDelayUntil - android.os.SystemClock.uptimeMillis()
            if (wait > 0) delay(wait) // widget bị xóa: các widget sau dời lên SAU một nhịp
        }
        pos.animateTo(target, spring(dampingRatio = REORDER_DAMPING, stiffness = REORDER_STIFFNESS))
    }
    val appear = remember { Animatable(if (model.consumePopIn(widget.id)) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) appear.animateTo(1f, tween(ENTER_MS, easing = OvershootEasing))
    }
    val lift = remember { Animatable(1f) }
    LaunchedEffect(dragged) { lift.animateTo(if (dragged) DRAG_SCALE else 1f, tween(DRAG_SCALE_MS)) }
    val widthDp = with(density) { slot[2].toDp() }
    val heightDp = with(density) { blockH.toDp() }
    Box(
        Modifier
            .zIndex(if (dragged) 1f else 0f)
            .offset {
                val p = if (dragged) model.dragTopLeft else pos.value
                IntOffset(p.x.toInt(), p.y.toInt())
            }
            .requiredSize(widthDp, heightDp)
            .graphicsLayer {
                // Mờ theo phần widget đã cuộn lên quá đáy thanh trên cùng (không mờ widget đang kéo).
                val top = (if (dragged) model.dragTopLeft.y else pos.value.y) - scroll.value
                val passed = fadeLine - top
                alpha = if (dragged || passed <= 0f) 1f else (1f - passed / (size.height * FADE_FRACTION)).coerceIn(0f, 1f)
            }
            .graphicsLayer {
                val a = appear.value
                val s = (ENTER_SCALE + (1f - ENTER_SCALE) * a) * lift.value
                scaleX = s
                scaleY = s
                alpha = a.coerceIn(0f, 1f)
            }
            .homeJiggle(editing && !dragged, WIDGET_JIGGLE_SCALE)
    ) {
        TodayWidgetContent(widget, cells, editing, apps, icons, actions)
    }
}

/** Widget vừa bị xóa: thu về [EXIT_SCALE] và mờ trong [EXIT_MS] tại chỗ cũ rồi gỡ khỏi danh sách rời. */
@Composable
private fun TodayLeaving(
    model: TodayGridModel,
    leaving: TodayGridModel.Leaving,
    cells: GridMetrics.Cells,
    blockH: Int,
    apps: List<AppItem>?,
    icons: HomeIcons
) {
    val density = LocalDensity.current
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        exit.animateTo(1f, tween(EXIT_MS, easing = AccelerateEasing))
        model.leaving.remove(leaving)
    }
    val w = if (leaving.widget.wide) 4 * cells.cellWidth + 3 * cells.gutterX else 2 * cells.cellWidth + cells.gutterX
    Box(
        Modifier
            .offset { IntOffset(leaving.at.x.toInt(), leaving.at.y.toInt()) }
            .requiredSize(with(density) { w.toDp() }, with(density) { blockH.toDp() })
            .graphicsLayer {
                val s = 1f - (1f - EXIT_SCALE) * exit.value
                scaleX = s
                scaleY = s
                alpha = 1f - exit.value
            }
    ) {
        TodayWidgetContent(leaving.widget, cells, editing = false, apps = apps, icons = icons, actions = null)
    }
}

/** Nội dung một widget: ô widget tự vẽ (cùng ô với Home) hoặc thẻ "App gần đây"; dấu "−" ở chế độ sửa (hỏi xác nhận rồi mới xóa). */
@Composable
private fun TodayWidgetContent(
    widget: TodayWidget,
    cells: GridMetrics.Cells,
    editing: Boolean,
    apps: List<AppItem>?,
    icons: HomeIcons,
    actions: TodayActions?
) {
    val kind = widget.builtin
    if (widget.isRecents) {
        TodayRecentsCard(
            columns = if (widget.wide) 4 else 2,
            apps = apps,
            icons = icons,
            showLabels = cells.showLabels,
            interactive = !editing && actions != null,
            onAppClick = { app, rect -> actions?.onAppClick?.invoke(app, rect) }
        )
    } else if (kind != null) {
        val placed = remember(widget) { PlacedWidget(widget.id, GridPosition(0, 0, widget.spanX, widget.spanY), kind) }
        HomeWidgetCell(placed, onClick = if (editing || actions == null) null else ({ actions.onWidgetClick(kind, widget.id) }))
    }
    if (editing && actions != null) {
        val r = cells.iconSize * MINUS_BADGE_RATIO / 2f // tâm dấu ở (r/2, r/2) của thẻ, như widget trên lưới Home
        HomeRemoveBadge(cells.iconSize, r / 2f, r / 2f, stringResource(R.string.widget_remove_title)) { actions.onRemove(widget.id) }
    }
}

/**
 * Cử chỉ của lưới (như `TodayGridLayout.dispatchTouchEvent`), xét ở pass Initial nên thấy cả chạm trên widget lẫn chỗ trống:
 * - nhấn giữ đủ lâu (không trôi): rung, vào chế độ sửa; từ đó nuốt mọi sự kiện (widget không nhận bấm, khung cuộn không cuộn);
 * - nhấn giữ trên một widget rồi trôi quá slop: kéo đổi chỗ, thả thì ghi thứ tự mới;
 * - chạm nhanh vào chỗ trống ở chế độ sửa: thoát chế độ sửa. Trôi trước khi giữ đủ lâu: nhường cho khung cuộn / widget.
 */
private fun Modifier.todayGestures(
    model: TodayGridModel,
    state: HomeTodayState,
    scroll: ScrollState,
    isEditing: () -> Boolean,
    actions: TodayActions,
    haptic: () -> Unit
): Modifier = pointerInput(model) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val badgeReach = BADGE_REACH_DP * density
        val pressed = model.entryAt(down.position, badgeReach)
        val slop = viewConfiguration.touchSlop
        var moved = false
        var lifted = false
        val waited = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    lifted = true
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
        }
        if (waited != null) {
            if (lifted && !moved && isEditing() && pressed == null) actions.onDone() // chạm chỗ trống thì thoát chế độ sửa
            return@awaitEachGesture
        }
        // Nhấn giữ: cuộn không được cướp chuỗi chạm này, Today không đóng bằng vuốt ngang.
        state.isHolding = true
        haptic()
        if (!isEditing()) actions.onEnterEditMode()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                event.changes.forEach { it.consume() }
                if (change == null || !change.pressed) break
                if (model.dragId.value == null) {
                    if (pressed != null && (change.position - down.position).getDistance() > slop) {
                        model.startDrag(pressed, down.position, change.position, scroll.value)
                    }
                } else {
                    model.onDragMove(change.position, scroll.value)
                }
            }
        } finally {
            state.isHolding = false
            model.endDrag()?.let(actions.onReorder)
        }
    }
}

/**
 * TV-3 — thẻ widget "App gần đây" (bản Compose của `RecentsWidgetView`): thẻ kính CARD bo `min(rộng, cao) × 0,12` chứa lưới [columns] × 2 app mở
 * gần đây (lưới riêng của thẻ, `GridMetrics` tự chừa lề). [apps] null = đang tải (thẻ trống), rỗng = chưa mở app nào (dòng chữ), có app = tối đa
 * [columns] × 2 ô. [interactive] false (chế độ sửa, bản xem trước): ô app không bấm được.
 */
@Composable
internal fun TodayRecentsCard(
    columns: Int,
    apps: List<AppItem>?,
    icons: HomeIcons,
    showLabels: Boolean,
    interactive: Boolean,
    flatColor: Color? = null,
    onAppClick: (AppItem, ScreenRect?) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = if (constraints.hasBoundedHeight) constraints.maxHeight else wPx * RECENTS_ROWS / columns
        val radius = with(density) { minOf(wPx, hPx).toDp() } * RECENTS_CORNER_RATIO
        val card = if (flatColor != null) {
            Modifier.clip(RoundedCornerShape(radius)).background(flatColor)
        } else {
            Modifier.dsGlass(DsSurface.CARD, DsCorner.Fixed(radius.value))
        }
        val inner = remember(columns, showLabels, wPx, hPx) { GridMetrics(columns, RECENTS_ROWS, showLabels).measure(wPx, hPx, density.density) }
        Box(Modifier.fillMaxSize().then(card)) {
            val shown = apps?.take(columns * RECENTS_ROWS)
            if (shown != null && shown.isEmpty()) {
                BasicText(
                    stringResource(R.string.today_recents_empty),
                    style = TextStyle(
                        color = Color.White.copy(alpha = RECENTS_EMPTY_ALPHA),
                        fontSize = if (columns >= 4) 15.sp else 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp)
                )
            }
            if (shown != null && inner != null) {
                shown.forEachIndexed { i, app ->
                    key(app.key) {
                        val x = inner.paddingLeft + (i % columns) * (inner.cellWidth + inner.gutterX)
                        val y = inner.paddingTop + (i / columns) * (inner.cellHeight + inner.gutterY)
                        gambi.global.os26launcher.ui.home.compose.HomeAppCell(
                            app, inner, icons,
                            modifier = Modifier
                                .offset { IntOffset(x, y) }
                                .requiredSize(with(density) { inner.cellWidth.toDp() }, with(density) { inner.cellHeight.toDp() }),
                            editMode = !interactive, // tắt bấm (chế độ sửa / xem trước); không có dấu "−" vì onRemove = null
                            onClick = onAppClick
                        )
                    }
                }
            }
        }
    }
}

/** Viên "+" / "Xong" của chế độ sửa: cùng kiểu viên nang của thanh edit mode Home, nhỏ hơn (vùng chạm [PILL_TOUCH_DP], chủ dự án 2026-11-20). */
@Composable
private fun TodayPill(
    text: String,
    sizeSp: Float,
    padH: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(PILL_TOUCH_DP.dp)
            .widthIn(min = PILL_MIN_WIDTH_DP.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .height(dimensionResource(R.dimen.home_edit_pill_height))
                .widthIn(min = PILL_MIN_WIDTH_DP.dp)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.home_edit_pill_corner)))
                .background(colorResource(R.color.search_scrim))
                .padding(horizontal = padH.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(text, style = TextStyle(color = Color.White, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** Thanh tìm kiếm trên cùng (cùng kiểu thanh của App Library: bề mặt `CONTROL`, co nhẹ khi giữ); chỉ là nút mở lớp tìm kiếm. */
@Composable
private fun TodaySearchBar(onClick: () -> Unit, modifier: Modifier) {
    val hint = colorResource(R.color.search_hint_text)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .dsPressable(interaction)
            .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .dsPressHighlight(interaction)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_search_pill_magnifier), null, colorFilter = ColorFilter.tint(hint), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.search_field_hint), style = TextStyle(color = hint, fontSize = 17.sp), maxLines = 1)
    }
}

private val OvershootEasing = Easing { OvershootInterpolator(ENTER_OVERSHOOT).getInterpolation(it) }
private val AccelerateEasing = Easing { AccelerateInterpolator().getInterpolation(it) }

/** CHỈNH TAY (chủ dự án 2026-11-20 "co lại"): vùng chạm cao 44dp -> hình viên 28dp, chữ nhỏ hơn Home. */
private const val PILL_TOUCH_DP = 44f
private const val PILL_MIN_WIDTH_DP = 44f
private const val PILL_PADDING_H_DP = 14f
private const val TITLE_PLUS_SP = 18f
private const val TITLE_DONE_SP = 13f

/** CHỈNH TAY: khoảng từ thanh trên cùng tới khung cuộn, đệm trên (chỗ cho dấu "−") và đệm dưới của danh sách widget. */
private const val SCROLL_GAP_DP = 4f
private const val GRID_TOP_DP = 12f
private const val GRID_BOTTOM_DP = 24f

private const val DRAG_SCALE = 1.04f
private const val DRAG_SCALE_MS = 150

/** CHỈNH TAY: widget bị xóa thu về [EXIT_SCALE] và mờ trong [EXIT_MS]; các widget sau bắt đầu dời sau `TodayGridModel.EXIT_SLIDE_DELAY_MS`. */
private const val EXIT_SCALE = 0.3f
private const val EXIT_MS = 220

/** CHỈNH TAY: widget mới phình từ [ENTER_SCALE] trong [ENTER_MS], nảy nhẹ. */
private const val ENTER_SCALE = 0.4f
private const val ENTER_MS = 320
private const val ENTER_OVERSHOOT = 1.2f

/** CHỈNH TAY: dải sát mép trên/dưới khung cuộn để tự cuộn khi kéo, và quãng cuộn mỗi khung hình. */
private const val AUTO_SCROLL_EDGE_DP = 56f
private const val AUTO_SCROLL_STEP_DP = 6f

/** CHỈNH TAY: widget mờ hết khi đã cuộn qua đáy thanh tìm kiếm chừng này phần chiều cao của nó. */
private const val FADE_FRACTION = 0.5f

/** Lò xo REORDER (`SpringProgress.Spec.REORDER` 300/0.85), dùng chung với Home. */
private const val REORDER_STIFFNESS = 300f
private const val REORDER_DAMPING = 0.85f

/** Dấu "−" nhô ra ngoài góc trên-trái widget: chạm trong khoảng này quanh góc vẫn tính là chạm vào widget (không thoát chế độ sửa). */
private const val BADGE_REACH_DP = 12f

private const val GLASS_BUDGET = 4
private const val RECENTS_ROWS = 2
private const val RECENTS_CORNER_RATIO = 0.12f
private const val RECENTS_EMPTY_ALPHA = 0.8f
