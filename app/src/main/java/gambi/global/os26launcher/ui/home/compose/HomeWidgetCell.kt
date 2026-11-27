package gambi.global.os26launcher.ui.home.compose

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.min
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.ui.widget.BatteryWidgetHub
import gambi.global.os26launcher.ui.widget.WidgetRenderers
import gambi.global.os26launcher.ui.widget.WidgetTicker
import java.time.ZonedDateTime
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.ui.widget.WeatherWidgetHub

/**
 * JH1 Task 3 — widget tự vẽ (đồng hồ, lịch, thời tiết, ảnh) trên Home Compose. Nội dung do CHÍNH `WidgetRenderer` của Home View vẽ lên canvas
 * của Compose (JH1 phương án A), nên hai Home trùng điểm ảnh. Thẻ nền là kính thật CARD (JH1 D4), bo `min(rộng, cao) × 0.12` như
 * `IconCellView.WIDGET_CORNER_RATIO`.
 *
 * Nhịp phút lấy từ `WidgetTicker` (cùng nguồn với ô View): mỗi nhịp tăng [tick], composition gọi lại `prepare` (dựng chuỗi, bố cục — không
 * cấp phát trong lúc vẽ, luật GL Task 6) rồi `drawBehind` đọc giá trị mới nên vẽ lại.
 */
@Composable
internal fun HomeWidgetCell(widget: PlacedWidget, onClick: (() -> Unit)? = null, gapXPx: Int? = null) {
    if (gapXPx == null) {
        WidgetCardContent(widget, onClick)
        return
    }
    // PW (chủ dự án 2026-11-25): thẻ widget luôn vuông (2 × 2) / cao bằng thẻ 2 × 2 (4 × 2) trong vùng ô của nó, xem [widgetCardFrame].
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = widgetCardFrame(constraints.maxWidth, constraints.maxHeight, widget.position.spanX, gapXPx)
        val density = LocalDensity.current
        Box(
            Modifier
                .offset { IntOffset(frame.left, frame.top) }
                .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
        ) { WidgetCardContent(widget, onClick) }
    }
}

@Composable
private fun WidgetCardContent(widget: PlacedWidget, onClick: (() -> Unit)?) {
    // WF Task 1: "App gần đây" vẽ bằng Compose; [onClick] null (edit mode, ảnh kéo) = ô app không bấm được.
    if (widget.kind == WidgetKind.RECENTS) HomeRecentsWidget(widget, interactive = onClick != null)
    else RendererWidgetCell(widget, onClick)
}

/**
 * PW (chủ dự án 2026-11-25) — khung thẻ của widget tự vẽ trong vùng ô [widthPx] × [heightPx] (spanX × 2 ô của lưới Home), kiểu iOS: thẻ 2 × 2
 * luôn VUÔNG, thẻ 4 × 2 cao bằng thẻ 2 × 2 (rộng = 2 thẻ vuông + khe [gapXPx]). Lưới app không đổi: một hàng icon cao hơn một cột (có nhãn
 * tên app) nên vùng 2 × 2 thường cao hơn rộng → thẻ neo TRÊN, phần thừa ở đáy (chỗ nhãn hàng dưới, sau này đặt nhãn tên widget như iOS); tắt
 * nhãn thì vùng rộng hơn cao → thẻ vuông theo chiều cao, căn giữa ngang. Dùng chung cho lưới Home, ảnh kéo và dấu "−".
 */
internal fun widgetCardFrame(widthPx: Int, heightPx: Int, spanX: Int, gapXPx: Int): IntRect {
    val wide = spanX >= 4
    val unit = if (wide) (widthPx - gapXPx) / 2 else widthPx
    val side = minOf(unit, heightPx).coerceAtLeast(0)
    val w = if (wide) 2 * side + gapXPx else side
    val left = ((widthPx - w) / 2).coerceAtLeast(0)
    return IntRect(left, 0, left + w, side)
}

@Composable
private fun RendererWidgetCell(widget: PlacedWidget, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT // đọc theo cấu hình (quan sát được); ROOT chỉ là dự phòng
    val spanX = widget.position.spanX
    val spanY = widget.position.spanY
    val renderer = remember(widget.kind, spanX, spanY) { WidgetRenderers.create(context, widget.kind, spanX, spanY) }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(renderer) {
        val tickable = object : WidgetTicker.Tickable {
            override fun tickWidget() {
                tick++
            }
        }
        WidgetTicker.register(context, tickable)
        // BW: widget Pin đang hiện thì hub nghe pin (gỡ khi không còn widget Pin nào hiện).
        val battery = widget.kind == WidgetKind.BATTERY
        if (battery) BatteryWidgetHub.acquire(context)
        onDispose {
            WidgetTicker.unregister(tickable)
            if (battery) BatteryWidgetHub.release()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val is24h = DateFormat.is24HourFormat(context)
        // Dựng lại khi cỡ, nhịp phút, ngôn ngữ hoặc kiểu giờ đổi; bindItem trước prepare như IconCellView.bindWidget (WX Task 5).
        val description = remember(renderer, widget.itemId, widthPx, heightPx, tick, locale, is24h) {
            renderer.bindItem(widget.itemId)
            renderer.prepare(widthPx, heightPx, density.density, ZonedDateTime.now(), locale, is24h)
            renderer.contentDescription()
        }
        Spacer(
            Modifier
                .fillMaxSize()
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(widgetRadius(maxWidth, maxHeight).value, glass = true))
                .semantics { contentDescription = description }
                .then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier) // JH3d D4
                .drawBehind {
                    tick // đọc để vẽ lại theo nhịp (prepare đã chạy ở composition)
                    drawIntoCanvas { renderer.draw(it.nativeCanvas) }
                }
        )
        // WR (chủ dự án 2026-11-27): nút làm mới siêu nhỏ góc dưới phải; không có ở edit mode / ảnh kéo ([onClick] null).
        if (widget.kind == WidgetKind.WEATHER && onClick != null) WeatherRefreshButton(widget.itemId, Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * WR — nút làm mới của widget thời tiết: icon [REFRESH_ICON_DP] trong vùng chạm [REFRESH_TOUCH_DP] (QĐ 5), đè lên thẻ nên chạm vào đây không mở bảng.
 * Bấm: xoay ít nhất một vòng (kể cả khi đang bị chặn 1 phút), rồi xoay tiếp chừng nào địa điểm của widget còn trong
 * [WeatherWidgetHub.refreshing]; việc làm mới do Home quyết ([WeatherWidgetHub.onRefreshTapped]).
 */
@Composable
private fun WeatherRefreshButton(itemId: Long, modifier: Modifier) {
    val refreshing by WeatherWidgetHub.refreshing.collectAsState()
    val busy = WeatherWidgetHub.placeIdOf(itemId) in refreshing
    val latestBusy by rememberUpdatedState(busy)
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) spinning = true }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        do {
            rotation.animateTo(rotation.value + 360f, tween(REFRESH_SPIN_MS, easing = LinearEasing))
        } while (latestBusy)
        rotation.snapTo(0f)
        spinning = false
    }
    val label = stringResource(R.string.wx_refresh_button)
    Box(
        modifier
            .size(REFRESH_TOUCH_DP.dp)
            .semantics { contentDescription = label }
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                spinning = true
                WeatherWidgetHub.onRefreshTapped?.invoke(itemId)
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.ic_widget_refresh),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorResource(R.color.widget_text_dim)),
            modifier = Modifier.size(REFRESH_ICON_DP.dp).graphicsLayer { rotationZ = rotation.value }
        )
    }
}

private const val REFRESH_ICON_DP = 12
private const val REFRESH_TOUCH_DP = 32
private const val REFRESH_SPIN_MS = 700

/** Bán kính thẻ widget: `min(rộng, cao) × 0.12` (`IconCellView.WIDGET_CORNER_RATIO`, companion private). */
internal fun widgetRadius(width: Dp, height: Dp): Dp = min(width, height) * WIDGET_CORNER_RATIO

private const val WIDGET_CORNER_RATIO = 0.12f
