package gambi.global.os26launcher.ui.home.compose

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import gambi.global.os26launcher.core.glass.GlassRole
import gambi.global.os26launcher.core.glass.compose.dsFrosted
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * JH1 Task 4 — pill dưới lưới (bản Compose của `SearchPillView`): đứng yên là kính lúp + "Tìm kiếm", đang lướt trang thì làm mờ chéo sang
 * hàng chấm kiểu ổ quay số ([PageDotsLayout]). Dải chạm cao 48 dp, viên nang cao 26 dp vẽ giữa. Nền phẳng `search_pill_bg` (JD D9: không
 * kính thật cho thành phần nhỏ).
 *
 * Polish 2026-11-19 (lần 2): pill ÔM VỪA kính lúp + chữ + lề [PADDING_DP] (chữ cố định [LABEL_SP]), không hẹp hơn cửa sổ 5 chấm + [DOTS_PADDING_DP]
 * mỗi bên, không rộng quá [MAX_PILL_DP] (chữ dài thì cắt "…"). Vùng chạm rộng bằng pill.
 *
 * [position] = trang hiện tại + phần lệch của pager (liên tục, H3). [active] = đang lướt. Hai công tắc Cài đặt ([showSearch], [showDots])
 * theo đúng bốn tổ hợp của `SearchPillView.setOptions`: tắt nút tìm kiếm thì chấm trần luôn hiện, không viên nang.
 *
 * P2-11: pill đang hiện chấm (và có hơn 1 trang) nhận cử chỉ chấm ([dotsGestures]): chạm nửa phải/trái = sang 1 trang; giữ rồi kéo = tới trang
 * của chấm dưới ngón tay; giữ yên ngoài cụm chấm = lặp ±1 trang. [onPageRequest] xin trang, [onDotsTouch] báo ngón tay đang/thôi đặt trên pill
 * (HomeScreen giữ pill dạng chấm suốt lúc chạm). [onDotsInteractive] báo pill có đang nhận cử chỉ chấm không (cử chỉ gốc / App Library bỏ qua
 * chạm bắt đầu trên pill lúc đó).
 */
@Composable
fun HomeSearchPill(
    pageCount: Int,
    position: () -> Float,
    active: Boolean,
    showSearch: Boolean,
    showDots: Boolean,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPageRequest: (Int) -> Unit = {},
    onDotsTouch: (Boolean) -> Unit = {},
    onDotsInteractive: (Boolean) -> Unit = {}
) {
    val target = when {
        showSearch && showDots -> if (active) 1f else 0f
        !showSearch && showDots -> 1f
        else -> 0f
    }
    val progress by animateFloatAsState(target, tween(MORPH_MS, easing = Decelerate), label = "pillMorph")
    val clickable = showSearch && !active
    val interaction = remember { MutableInteractionSource() }
    val searchLabel = stringResource(R.string.search_pill_label)
    val pageDescription = if (pageCount > 0) {
        stringResource(R.string.search_pill_page_description, position().roundToInt().coerceIn(0, pageCount - 1) + 1, pageCount)
    } else {
        searchLabel
    }
    val pillWidth = pillWidth(searchLabel)
    val dotsInteractive = showDots && target == 1f && pageCount > 1
    SideEffect { onDotsInteractive(dotsInteractive) }
    Box(
        modifier
            .height(dimensionResource(R.dimen.search_pill_touch_height))
            .width(pillWidth)
            .then(
                if (showSearch || showDots) {
                    Modifier.semantics { contentDescription = if (target == 1f) pageDescription else searchLabel }
                } else {
                    Modifier
                }
            )
            .dotsGestures(dotsInteractive, pageCount, position, onPageRequest, onDotsTouch)
            .then(
                if (clickable) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onSearchClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showSearch) {
            Box(
                Modifier
                    .size(pillWidth, dimensionResource(R.dimen.search_pill_height))
                    // PG3 (G4): kính mờ tĩnh như SearchPillView (GlassRole.SEARCH_PILL); chưa có bản mờ / ảnh nền màu thì phẳng `search_pill_bg` như cũ.
                    .dsFrosted(GlassRole.SEARCH_PILL, PillShape)
                    .dsPressHighlight(interaction, enabled = clickable)
            )
            if (progress < 1f) SearchContent(pillWidth, Modifier.alpha(1f - progress))
        }
        if (showDots && progress > 0f) PageDots(pageCount, position, haloed = !showSearch, Modifier.alpha(progress))
    }
}

/** Bề rộng pill: kính lúp + khe + chữ + lề hai bên, không hẹp hơn cửa sổ 5 chấm; không rộng quá [MAX_PILL_DP] (như `SearchPillView.pillWidth`). */
@Composable
private fun pillWidth(label: String): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textPx = remember(label, density) {
        measurer.measure(label, TextStyle(fontSize = LABEL_SP.sp, fontWeight = FontWeight.Bold), maxLines = 1).size.width
    }
    val text = with(density) { textPx.toDp() }
    val dots = DOT_STEP_DP.dp * (PageDotsLayout.SLOTS - 1) + DOT_RADIUS_DP.dp * 2 + DOTS_PADDING_DP.dp * 2
    val content = (ICON_DP.dp + ICON_GAP_DP.dp + text + PADDING_DP.dp * 2).coerceAtMost(MAX_PILL_DP.dp)
    return maxOf(dots, content)
}

private enum class DotsGesture { TAP, HELD, REPEAT, SCRUB, CANCELLED }

/**
 * P2-11 — cử chỉ trên pill dạng chấm (theo app mẫu, bản Compose của `SearchPillView.onTouchEvent`). Chạm nửa phải / trái → sang phải / trái 1
 * trang. Giữ đủ lâu (long-press timeout) thì CHƯA làm gì (HELD); sau đó: dời tay quá touch slop → kéo theo vị trí (SCRUB); đứng yên ở phần pill
 * NGOÀI cụm chấm thêm [EDGE_ARM_MS] → lặp ±1 trang mỗi [HOLD_REPEAT_MS] (REPEAT; đang lặp mà dời tay → kéo); đứng yên ở cụm chấm → không làm
 * gì. Kéo theo vị trí → tới trang của chấm dưới ngón tay, rung nhẹ mỗi lần đổi trang; ngón tay ở chấm mép cửa sổ 5 chấm mà phía đó còn trang →
 * cửa sổ trượt từng trang mỗi [SCRUB_ROLL_MS]. Mọi sự kiện của cử chỉ bị tiêu thụ (không rơi xuống nền, không vào edit mode). Hẹn giờ chạy
 * bằng `withTimeoutOrNull` quanh lần chờ sự kiện kế tiếp (một vòng lặp, không coroutine phụ).
 */
private fun Modifier.dotsGestures(
    enabled: Boolean,
    pageCount: Int,
    position: () -> Float,
    onPageRequest: (Int) -> Unit,
    onDotsTouch: (Boolean) -> Unit
): Modifier = if (!enabled) this else composed {
    val view = LocalView.current
    val count by rememberUpdatedState(pageCount)
    val pos by rememberUpdatedState(position)
    val request by rememberUpdatedState(onPageRequest)
    val touch by rememberUpdatedState(onDotsTouch)
    pointerInput(Unit) {
        val dotStep = DOT_STEP_DP.dp.toPx()
        val slop = viewConfiguration.touchSlop
        val longPress = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val w = size.width.toFloat()
            var gesture = DotsGesture.TAP
            var targetPage = pos().roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)) // trang đang nhắm (đi trước position khi đang trượt)
            var last = down.position
            var hold = down.position // chỗ ngón tay lúc giữ đủ lâu: dời quá slop kể từ đây = kéo
            var repeatDir = 0
            var rollDir = 0
            var now = down.uptimeMillis
            var deadline = now + longPress // hẹn giờ kế tiếp (ms, uptime); Long.MAX_VALUE = không hẹn
            touch(true)

            fun requestPage(page: Int) {
                val p = page.coerceIn(0, count - 1)
                if (p == targetPage) return
                targetPage = p
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                request(p)
            }

            // -1 / 1: x nằm ngoài cụm chấm đang hiện (bên trái / phải, chừa nửa bước chấm); 0: trên cụm chấm.
            fun edgeDir(x: Float): Int {
                val visible = minOf(count, PageDotsLayout.SLOTS)
                val half = (visible - 1) / 2f * dotStep + dotStep / 2f
                return when {
                    x < w / 2f - half -> -1
                    x > w / 2f + half -> 1
                    else -> 0
                }
            }

            // Chấm dưới ngón tay tính theo cửa sổ chấm của trang ĐÍCH (không theo position đang trượt, để không "đuổi theo" chính mình).
            fun scrubTo(x: Float, fromTimer: Boolean) {
                val ws = PageDotsLayout.windowStart(targetPage.toFloat(), count)
                val centerSlot = (PageDotsLayout.SLOTS - 1) / 2f
                val slot = ((x - w / 2f) / dotStep + centerSlot).roundToInt()
                val lastSlot = PageDotsLayout.SLOTS - 1
                val dir = when {
                    count > PageDotsLayout.SLOTS && slot >= lastSlot && ws < count - PageDotsLayout.SLOTS -> 1
                    count > PageDotsLayout.SLOTS && slot <= 0 && ws > 0f -> -1
                    else -> 0
                }
                if (dir != 0) {
                    // Lần đầu chạm chấm mép: trượt ngay; ngón tay đứng yên ở đó: lặp theo nhịp.
                    if (fromTimer || rollDir != dir) requestPage(targetPage + dir)
                    rollDir = dir
                    deadline = now + SCRUB_ROLL_MS
                    return
                }
                rollDir = 0
                deadline = Long.MAX_VALUE
                requestPage((ws + slot - PageDotsLayout.centerShift(count)).roundToInt())
            }

            try {
                while (true) {
                    val event: PointerEvent? = if (deadline == Long.MAX_VALUE) {
                        awaitPointerEvent()
                    } else {
                        withTimeoutOrNull((deadline - now).coerceAtLeast(1L)) { awaitPointerEvent() }
                    }
                    if (event == null) { // hẹn giờ tới
                        now = deadline
                        when (gesture) {
                            DotsGesture.TAP -> {
                                gesture = DotsGesture.HELD
                                hold = last
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) // báo đã vào chế độ giữ
                                deadline = if (edgeDir(last.x) != 0) now + EDGE_ARM_MS else Long.MAX_VALUE
                            }

                            DotsGesture.HELD -> {
                                val dir = edgeDir(last.x)
                                if (dir == 0) {
                                    deadline = Long.MAX_VALUE
                                } else {
                                    gesture = DotsGesture.REPEAT
                                    repeatDir = dir
                                    requestPage(targetPage + repeatDir)
                                    deadline = now + HOLD_REPEAT_MS
                                }
                            }

                            DotsGesture.REPEAT -> {
                                requestPage(targetPage + repeatDir)
                                deadline = now + HOLD_REPEAT_MS
                            }

                            DotsGesture.SCRUB -> scrubTo(last.x, fromTimer = true)
                            DotsGesture.CANCELLED -> deadline = Long.MAX_VALUE
                        }
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    now = change.uptimeMillis
                    event.changes.forEach { it.consume() }
                    last = change.position
                    if (!change.pressed) {
                        if (gesture == DotsGesture.TAP) requestPage(targetPage + if (last.x >= w / 2f) 1 else -1)
                        break
                    }
                    when (gesture) {
                        DotsGesture.TAP -> if (abs(last.x - down.position.x) > slop || abs(last.y - down.position.y) > slop) {
                            gesture = DotsGesture.CANCELLED // dời tay trước khi giữ đủ lâu: không phải chạm, không phải giữ
                            deadline = Long.MAX_VALUE
                        }

                        DotsGesture.HELD, DotsGesture.REPEAT -> if (abs(last.x - hold.x) > slop || abs(last.y - hold.y) > slop) {
                            gesture = DotsGesture.SCRUB
                            scrubTo(last.x, fromTimer = false)
                        }

                        DotsGesture.SCRUB -> scrubTo(last.x, fromTimer = false)
                        DotsGesture.CANCELLED -> Unit
                    }
                }
            } finally {
                touch(false)
            }
        }
    }
}

@Composable
private fun SearchContent(width: Dp, modifier: Modifier) {
    val color = colorResource(R.color.search_pill_content)
    Row(
        modifier.width(width),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painterResource(R.drawable.ic_search_pill_magnifier),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(ICON_DP.dp)
        )
        Spacer(Modifier.width(ICON_GAP_DP.dp))
        BasicText(
            stringResource(R.string.search_pill_label),
            style = TextStyle(color = color, fontSize = LABEL_SP.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Chữ dài hơn chỗ còn lại của pill (đã chạm trần) thì cắt "…".
            modifier = Modifier.widthIn(max = (width - PADDING_DP.dp * 2 - ICON_DP.dp - ICON_GAP_DP.dp).coerceAtLeast(0.dp))
        )
    }
}

/** Hàng chấm: cùng phép tính và màu với `SearchPillView.drawDots` (độ sáng theo khoảng cách tới [position], chấm mép thu nhỏ). */
@Composable
private fun PageDots(pageCount: Int, position: () -> Float, haloed: Boolean, modifier: Modifier) {
    val active = colorResource(R.color.search_pill_content)
    val inactive = colorResource(R.color.search_pill_dot_inactive)
    val halo = colorResource(R.color.ht_dot_halo)
    Canvas(modifier.fillMaxSize()) {
        if (pageCount <= 0) return@Canvas
        val pos = position()
        val radius = DOT_RADIUS_DP.dp.toPx()
        val step = DOT_STEP_DP.dp.toPx()
        val haloExtra = DOT_HALO_EXTRA_DP.dp.toPx()
        val centerSlot = (PageDotsLayout.SLOTS - 1) / 2f
        val cy = size.height / 2f
        for (i in 0 until pageCount) {
            val slot = PageDotsLayout.slotOf(i, pos, pageCount)
            if (slot <= -1f || slot >= PageDotsLayout.SLOTS) continue
            val scale = PageDotsLayout.scaleAt(slot, pos, pageCount)
            if (scale <= 0f) continue
            val center = Offset(size.width / 2f + (slot - centerSlot) * step, cy)
            if (haloed) drawCircle(halo, (radius + haloExtra) * scale, center)
            drawCircle(lerp(inactive, active, PageDotsLayout.weightOf(i, pos)), radius * scale, center)
        }
    }
}

// Cùng số với companion (private) của SearchPillView.
private const val MORPH_MS = 250

/** Như `DecelerateInterpolator()` của View: 1 − (1 − t)². */
private val Decelerate = Easing { 1f - (1f - it) * (1f - it) }
private const val DOT_RADIUS_DP = 3f
private const val DOT_SPACING_DP = 5f // polish 2026-11-19: 8 -> 5 (chấm sát nhau hơn)
private const val DOT_STEP_DP = DOT_RADIUS_DP * 2 + DOT_SPACING_DP
private const val DOT_HALO_EXTRA_DP = 1.5f
private const val DOTS_PADDING_DP = 10f // polish: lề hai bên cửa sổ 5 chấm trong pill
private const val MAX_PILL_DP = 160f // polish: trần bề rộng pill (ngôn ngữ chữ dài)
private const val ICON_DP = 14f
private const val ICON_GAP_DP = 5f
private const val PADDING_DP = 12f // lề trái/phải của kính lúp + chữ trong pill
private const val LABEL_SP = 10f // polish 2026-11-19: 13 -> 10sp, cỡ cố định; pill ôm theo chữ
private val PillShape = RoundedCornerShape(50) // PG3: hằng để kính mờ của pill không dựng lại hình mỗi lần dựng

// P2-11: nhịp lặp giữ rìa và nhịp trượt cửa sổ khi kéo tới chấm mép (như SearchPillView).
private const val EDGE_ARM_MS = 300L // giữ yên ngoài cụm chấm thêm chừng này (sau long-press) mới bắt đầu lặp
private const val HOLD_REPEAT_MS = 500L
private const val SCRUB_ROLL_MS = 500L
