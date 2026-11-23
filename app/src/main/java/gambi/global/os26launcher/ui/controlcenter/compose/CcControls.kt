package gambi.global.os26launcher.ui.controlcenter.compose

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.drawDrawable
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterState
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import gambi.global.os26launcher.ui.controlcenter.CcGlassDrawable
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

/**
 * JS7b: thanh trượt kiểu iOS 26 (thay `ControlSliderView`): viên thuốc kính bo bằng nửa cạnh ngắn. Dọc: phần trắng tô từ đáy lên theo
 * giá trị (0..1), icon nằm trong phần tô đổi sang [iconOnFillColor]; [horizontal]: tô từ trái sang (âm lượng của trình phát lớn).
 * Kéo đổi giá trị theo QUÃNG kéo (không nhảy tới chỗ chạm); chạm không kéo thì không đổi gì. Nhấn giữ không di chuyển thì gọi
 * [onLongPress] (RE mục 5.2). Giữ cú chạm ngay từ lúc chạm xuống: panel không hiểu là vuốt đổi trang.
 */
@Composable
internal fun CcSlider(
    panel: ControlCenterPanel,
    model: CcSliderModel,
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    @ColorRes iconOnFillColor: Int = R.color.cc_slider_icon_on_fill,
    onLongPress: (() -> Unit)? = null,
    onValueChanged: (Float, Boolean) -> Unit
) {
    val glass = rememberCcGlass(CcGlassDrawable.Shape.PILL)
    val fill = colorResource(R.color.cc_slider_fill)
    val iconOnFill = colorResource(iconOnFillColor)
    val iconOnBg = colorResource(R.color.cc_icon)
    val painter = painterResource(icon)
    val iconSize = with(LocalDensity.current) { dimensionResource(R.dimen.cc_icon_size).roundToPx() }
    val haptic = LocalHapticFeedback.current
    val change by rememberUpdatedState(onValueChanged)
    val longPress by rememberUpdatedState(onLongPress)
    val hasLongPress = onLongPress != null
    Box(
        modifier
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(model.value, 0f..1f, steps = A11Y_STEPS)
                setProgress { v ->
                    model.value = v.coerceIn(0f, 1f)
                    change(model.value, true)
                    true
                }
                if (hasLongPress) onLongClick {
                    longPress?.invoke()
                    true
                }
            }
            .pointerInput(model, horizontal) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    panel.childTouched = true
                    panel.disallowIntercept = true // không để panel hiểu là vuốt đổi trang
                    model.dragging = true
                    try {
                        val slop = viewConfiguration.touchSlop
                        var last = if (horizontal) down.position.x else down.position.y
                        fun apply(c: androidx.compose.ui.input.pointer.PointerInputChange) {
                            val pos = if (horizontal) c.position.x else c.position.y
                            val delta = if (horizontal) pos - last else last - pos
                            last = pos
                            val span = if (horizontal) size.width else size.height
                            if (span > 0 && delta != 0f) {
                                model.value = (model.value + delta / span).coerceIn(0f, 1f)
                                change(model.value, false)
                            }
                            c.consume()
                        }
                        var moved = false
                        var up = false
                        val timeout = if (longPress != null) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE
                        val timedOut = withTimeoutOrNull(timeout) {
                            while (!moved && !up) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed) {
                                    up = true
                                } else if (abs(c.position.x - down.position.x) > slop || abs(c.position.y - down.position.y) > slop) {
                                    moved = true
                                    apply(c)
                                }
                            }
                            false
                        } ?: true
                        if (timedOut) {
                            model.dragging = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress?.invoke()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        if (up) return@awaitEachGesture
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (c == null || !c.pressed) {
                                change(model.value, true) // nhả tay: người nghe ghi lần cuối, không throttle
                                break
                            }
                            apply(c)
                        }
                    } finally {
                        model.dragging = false
                    }
                }
            }
            .drawWithCache {
                val w = size.width.toInt()
                val h = size.height.toInt()
                val r = glass.radiusFor(size.width, size.height)
                val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                val s = if (horizontal) min(iconSize, (h * 0.6f).toInt()) else min(iconSize, (w * 0.42f).toInt())
                onDrawBehind {
                    drawDrawable(glass)
                    val v = model.value
                    val l: Int
                    val t: Int
                    val covered: Boolean
                    if (horizontal) {
                        val fillRight = w * v
                        clipPath(clip) { drawRect(fill, size = Size(fillRight, h.toFloat())) }
                        l = (h - s) / 2
                        t = (h - s) / 2
                        covered = fillRight > l + s
                    } else {
                        val fillTop = h * (1f - v)
                        clipPath(clip) { drawRect(fill, topLeft = Offset(0f, fillTop), size = Size(w.toFloat(), h - fillTop)) }
                        l = (w - s) / 2
                        t = h - s - (w * 0.32f).toInt()
                        covered = fillTop < t
                    }
                    // Icon nằm trong phần đã tô thì đổi màu xám để còn thấy (iOS).
                    drawIconAt(painter, l, t, s, if (covered) iconOnFill else iconOnBg)
                }
            }
    )
}

/**
 * JS7b: thanh trạng thái riêng ở đỉnh panel (RE mục 5.1, `gvd`): tên nhà mạng, icon Wi-Fi, khoảng trống, icon khóa xoay, icon
 * Không làm phiền, % pin và icon pin. TalkBack bỏ qua (như `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`).
 */
@Composable
internal fun CcStatusStrip(state: ControlCenterState, modifier: Modifier = Modifier) {
    val s = dimensionResource(R.dimen.cc_status_icon)
    val m = s / 3
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        CcText(state.carrier, 17f, bold = true)
        if (state.wifiOn && !state.airplaneOn) CcIcon(R.drawable.ic_cc_wifi, s, Modifier.padding(start = m))
        Spacer(Modifier.weight(1f))
        if (state.rotationLocked) CcIcon(R.drawable.ic_cc_rotation_lock, s, Modifier.padding(end = m))
        if (state.dndOn) CcIcon(R.drawable.ic_cc_dnd, s, Modifier.padding(end = m))
        val percent = if (state.batteryLevel >= 0) stringResource(R.string.cc_battery_percent, state.batteryLevel) else ""
        CcText(percent, 17f, Modifier.padding(end = m), bold = true)
        CcBatteryGlyph(state.batteryLevel, state.charging, state.powerSave, Modifier.size(s * 1.7f, s * 0.85f))
    }
}

/** Icon pin vẽ tay theo mức (app mẫu dùng `bqd`; bản View là `BatteryGlyph`). */
@Composable
private fun CcBatteryGlyph(level: Int, charging: Boolean, powerSave: Boolean, modifier: Modifier) {
    val icon = colorResource(R.color.cc_icon)
    val levelColor = colorResource(
        when {
            charging -> R.color.cc_green
            powerSave -> R.color.cc_yellow
            level <= BATTERY_LOW -> R.color.cc_red
            else -> R.color.cc_icon
        }
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val nub = w * 0.08f
        val sw = h * 0.1f
        var left = sw / 2
        var top = sw / 2
        var right = w - nub - sw / 2
        var bottom = h - sw / 2
        drawRoundRect(
            icon.copy(alpha = icon.alpha * STROKE_ALPHA / 255f), Offset(left, top), Size(right - left, bottom - top),
            CornerRadius(h * 0.25f), style = Stroke(sw)
        )
        drawRect(icon, Offset(w - nub, h * 0.32f), Size(nub, h * 0.36f))
        if (level < 0) return@Canvas
        val inset = sw * 1.6f
        val full = (right - left) - 2 * inset + sw
        left = left + inset - sw / 2
        top = top + inset - sw / 2
        right = left + full * level / 100f
        bottom = bottom - inset + sw / 2
        drawRoundRect(levelColor, Offset(left, top), Size(right - left, bottom - top), CornerRadius(h * 0.12f))
    }
}

/**
 * JS7b: ô "Thời gian chờ màn hình" 2×1 (RE mục 5.1, `yeb`), dáng ô Focus của iOS 26: viên thuốc kính, bên trái là vòng tròn kính
 * sáng hơn (đường kính 70% chiều cao) chứa mức hiện tại, bên phải là nhãn. Chạm mở bảng chọn.
 */
@Composable
internal fun CcTimeoutTile(panel: ControlCenterPanel, timeoutMs: Int, modifier: Modifier = Modifier) {
    val pill = rememberCcGlass(CcGlassDrawable.Shape.PILL)
    val circle = rememberCcGlass(CcGlassDrawable.Shape.CIRCLE, fill = R.color.cc_glass_inner)
    val label = stringResource(R.string.cc_screen_timeout)
    val value = ccTimeoutLabel(timeoutMs)
    val description = "$label, $value"
    Layout(
        content = {
            Box(Modifier.drawBehind { drawDrawable(circle) }, contentAlignment = Alignment.Center) {
                CcText(value, 14f, Modifier.clearAndSetSemantics {}, bold = true, align = TextAlign.Center)
            }
            CcText(label, 15f, Modifier.clearAndSetSemantics {}, bold = true, maxLines = 2)
        },
        modifier = modifier
            .ccGlass(pill)
            .ccClaimTouch(panel)
            .semantics { contentDescription = description }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                panel.showTimeoutPicker()
            }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val d = (h * 0.7f).toInt()
        val m = (h - d) / 2
        val circleP = measurables[0].measure(Constraints.fixed(d, d))
        val labelP = measurables[1].measure(Constraints(maxWidth = (w - d - 2 * m).coerceAtLeast(0), maxHeight = h))
        layout(w, h) {
            circleP.place(m, (h - d) / 2)
            labelP.place(d + 2 * m, (h - labelP.height) / 2)
        }
    }
}

/**
 * JS7b: khối kết nối 2×2 kiểu iOS: Máy bay, Dữ liệu di động (hàng trên), Wi-Fi, Bluetooth (hàng dưới), mỗi nút tròn cạnh 36% cạnh
 * ngắn, tâm ở 27% / 73% (RE mục 5.1, `zqd`). Nhấn giữ bất kỳ đâu trên khối sang trang Mạng (RE mục 5.2).
 */
@Composable
internal fun CcConnectivityBlock(panel: ControlCenterPanel, state: ControlCenterState, modifier: Modifier = Modifier) {
    val glass = rememberCcGlass()
    val label = stringResource(R.string.cc_block_connectivity)
    val toNetwork = { panel.showPage(ControlCenterPanel.PAGE_NETWORK) }
    val white = colorResource(R.color.cc_icon)
    Layout(
        content = {
            CcTile(
                panel, R.drawable.ic_cc_airplane, stringResource(R.string.cc_tile_airplane), state.airplaneOn,
                colorResource(R.color.cc_orange), white
            ) { panel.listener?.onTileClick(ControlTile.AIRPLANE) }
            CcTile(
                panel, R.drawable.ic_cc_cellular, stringResource(R.string.cc_tile_cellular), false,
                colorResource(R.color.cc_green), white, checkable = false
            ) { panel.listener?.onTileClick(ControlTile.CELLULAR) }
            CcTile(
                panel, R.drawable.ic_cc_wifi, stringResource(R.string.cc_tile_wifi), state.wifiOn,
                colorResource(R.color.cc_toggle_blue), white
            ) { panel.listener?.onTileClick(ControlTile.WIFI) }
            CcTile(
                panel, R.drawable.ic_cc_bluetooth, stringResource(R.string.cc_tile_bluetooth), state.bluetoothOn,
                colorResource(R.color.cc_toggle_blue), white, enabled = state.bluetoothAvailable
            ) { panel.listener?.onTileClick(ControlTile.BLUETOOTH) }
        },
        modifier = modifier
            .ccGlass(glass)
            .ccLongPressGroup(panel, toNetwork)
            .ccLongClickSemantics(label, toNetwork)
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val d = (min(w, h) * 0.36f).toInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(d, d)) }
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                // Tâm nút nằm giữa từng phần tư của khối.
                val cx = w * (if (i % 2 == 0) 0.27f else 0.73f)
                val cy = h * (if (i < 2) 0.27f else 0.73f)
                p.place((cx - d / 2f).toInt(), (cy - d / 2f).toInt())
            }
        }
    }
}

/** Một nút của trang Mạng (RE mục 5.1, `brd`/`psd`): nút tròn, dưới là nhãn và chữ Bật/Tắt. */
private class NetworkTile(
    @DrawableRes val icon: Int,
    val label: Int,
    @ColorRes val onColor: Int,
    val tile: ControlTile,
    val showState: Boolean = true
)

private val NETWORK_TILES = listOf(
    NetworkTile(R.drawable.ic_cc_airplane, R.string.cc_tile_airplane, R.color.cc_orange, ControlTile.AIRPLANE),
    NetworkTile(R.drawable.ic_cc_cellular, R.string.cc_tile_cellular, R.color.cc_green, ControlTile.CELLULAR, showState = false),
    NetworkTile(R.drawable.ic_cc_wifi, R.string.cc_tile_wifi, R.color.cc_toggle_blue, ControlTile.WIFI),
    NetworkTile(R.drawable.ic_cc_bluetooth, R.string.cc_tile_bluetooth, R.color.cc_toggle_blue, ControlTile.BLUETOOTH),
    NetworkTile(R.drawable.ic_cc_sync, R.string.cc_tile_sync, R.color.cc_toggle_blue, ControlTile.SYNC),
    NetworkTile(R.drawable.ic_cc_hotspot, R.string.cc_tile_hotspot, R.color.cc_green, ControlTile.HOTSPOT, showState = false)
)

private fun ControlCenterState.isOn(tile: ControlTile): Boolean = when (tile) {
    ControlTile.AIRPLANE -> airplaneOn
    ControlTile.WIFI -> wifiOn
    ControlTile.BLUETOOTH -> bluetoothOn
    ControlTile.SYNC -> syncOn
    else -> false
}

/**
 * JS7b: trang Mạng (RE mục 5.1, trang 3): 6 nút, 2 cột × 3 hàng trên một tấm kính bo góc, rộng `min(W, maxWidth)·0,84`, nút cạnh 42%
 * nửa tấm. Chạm trên tấm (nhãn, lề) không đóng panel; ngoài tấm thì đóng.
 */
@Composable
internal fun CcNetworkPage(panel: ControlCenterPanel, state: ControlCenterState, landscape: Boolean, modifier: Modifier = Modifier) {
    if (landscape) {
        CcNetworkPageLandscape(panel, state, modifier)
        return
    }
    val density = LocalDensity.current
    val glass = rememberCcGlass(cornerRadius = with(density) { dimensionResource(R.dimen.cc_block_radius).toPx() })
    val maxWidth = with(density) { dimensionResource(R.dimen.cc_max_width).toPx() }
    val gap = with(density) { dimensionResource(R.dimen.cc_gap).roundToPx() }
    val white = colorResource(R.color.cc_icon)
    val on = stringResource(R.string.cc_state_on)
    val off = stringResource(R.string.cc_state_off)
    Layout(
        content = {
            Box(Modifier.ccGlass(glass).ccClaimTouch(panel))
            NETWORK_TILES.forEach { t ->
                val isOn = state.isOn(t.tile)
                CcNetworkCell(
                    button = {
                        CcTile(
                            panel, t.icon, stringResource(t.label), isOn, colorResource(t.onColor), white, checkable = t.showState
                        ) { panel.listener?.onTileClick(t.tile) }
                    },
                    name = { CcText(stringResource(t.label), 13f, Modifier.clearAndSetSemantics {}, align = TextAlign.Center) },
                    stateText = {
                        // Ô không có trạng thái vẫn giữ chỗ dòng chữ (INVISIBLE) để các hàng thẳng nhau.
                        CcText(
                            if (isOn) on else off, 12f,
                            Modifier.graphicsLayer { alpha = if (t.showState) 1f else 0f }.clearAndSetSemantics {},
                            alpha = 0.6f, align = TextAlign.Center
                        )
                    }
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cardWf = min(w * 0.84f, maxWidth * 0.84f)
        val cellW = (cardWf / 2).toInt()
        val tiles = measurables.drop(1).map { it.measure(Constraints(minWidth = cellW, maxWidth = cellW, maxHeight = h)) }
        val cellH = (tiles.maxOfOrNull { it.height } ?: 0) + gap
        val cardW = cellW * 2
        val cardH = cellH * 3 + gap
        val card = measurables[0].measure(Constraints.fixed(cardW, cardH))
        val x0 = (w - cardW) / 2
        val y0 = (h - cardH) / 2
        layout(w, h) {
            card.place(x0, y0)
            tiles.forEachIndexed { i, p -> p.place(x0 + (i % 2) * cellW, y0 + gap + (i / 2) * cellH) }
        }
    }
}

/** Một ô của trang Mạng: nút tròn cạnh 42% bề rộng ô, canh giữa; nhãn cách 8 px (View cũ đặt px, không phải dp); dòng Bật/Tắt. */
@Composable
private fun CcNetworkCell(button: @Composable () -> Unit, name: @Composable () -> Unit, stateText: @Composable () -> Unit) {
    Layout(content = {
        button()
        name()
        stateText()
    }) { measurables, constraints ->
        val w = constraints.maxWidth
        val b = (w * 0.42f).toInt()
        val buttonP = measurables[0].measure(Constraints.fixed(b, b))
        val textC = Constraints(minWidth = w, maxWidth = w)
        val nameP = measurables[1].measure(textC)
        val stateP = measurables[2].measure(textC)
        val height = (b + NAME_TOP_PX + nameP.height + stateP.height).coerceAtMost(constraints.maxHeight)
        layout(w, height) {
            buttonP.place((w - b) / 2, 0)
            nameP.place(0, b + NAME_TOP_PX)
            stateP.place(0, b + NAME_TOP_PX + nameP.height)
        }
    }
}

private const val NAME_TOP_PX = 8

/**
 * P2-8: trang Mạng khi màn ngang: mỗi nút là một thẻ ngang kính riêng (viên nang), 2 cột × 3 hàng như iOS 26; nút tròn bên trái, tên
 * và trạng thái bên phải căn trái. Cỡ thẻ: 2 cột trong bề rộng nội dung `min(W − 2·pad, 2·maxWidth + gap)`, 3 hàng vừa chiều cao trang,
 * cao không quá 1/4 bề ngang thẻ. Thứ tự gần ảnh iOS 26: cột trái máy bay, WLAN, dữ liệu; cột phải Bluetooth, điểm truy cập, đồng bộ.
 */
@Composable
private fun CcNetworkPageLandscape(panel: ControlCenterPanel, state: ControlCenterState, modifier: Modifier) {
    val density = LocalDensity.current
    val pillGlass = rememberCcGlass(CcGlassDrawable.Shape.PILL)
    val maxWidth = with(density) { dimensionResource(R.dimen.cc_max_width).roundToPx() }
    val gap = with(density) { dimensionResource(R.dimen.cc_gap).roundToPx() }
    val pad = with(density) { dimensionResource(R.dimen.cc_pad).roundToPx() }
    val white = colorResource(R.color.cc_icon)
    val on = stringResource(R.string.cc_state_on)
    val off = stringResource(R.string.cc_state_off)
    val ordered = LAND_NETWORK_ORDER.map { NETWORK_TILES[it] }
    Layout(
        content = {
            ordered.forEach { t ->
                val isOn = state.isOn(t.tile)
                Box(Modifier.ccGlass(pillGlass).ccClaimTouch(panel))
                CcTile(panel, t.icon, stringResource(t.label), isOn, colorResource(t.onColor), white, checkable = t.showState) {
                    panel.listener?.onTileClick(t.tile)
                }
                Column(Modifier.clearAndSetSemantics {}) {
                    CcText(stringResource(t.label), 13f)
                    CcText(
                        if (isOn) on else off, 12f,
                        Modifier.graphicsLayer { alpha = if (t.showState) 1f else 0f }, alpha = 0.6f
                    )
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val contentW = min(w - 2 * pad, maxWidth * 2 + gap)
        val pillW = ((contentW - gap) / 2).coerceAtLeast(0)
        val pillH = min((h - 2 * pad - 2 * gap) / 3, (pillW * 0.25f).toInt()).coerceAtLeast(0)
        val inner = (pillH * 0.16f).toInt() // lề trong thẻ quanh nút tròn
        val b = (pillH - 2 * inner).coerceAtLeast(0)
        val textW = (pillW - inner - b - gap - inner * 2).coerceAtLeast(0)
        val parts = ordered.indices.map { i ->
            Triple(
                measurables[i * 3].measure(Constraints.fixed(pillW, pillH)),
                measurables[i * 3 + 1].measure(Constraints.fixed(b, b)),
                measurables[i * 3 + 2].measure(Constraints(minWidth = textW, maxWidth = textW, maxHeight = pillH))
            )
        }
        val gridW = pillW * 2 + gap
        val gridH = pillH * 3 + gap * 2
        val x0 = (w - gridW) / 2
        val y0 = ((h - gridH) / 2).coerceAtLeast(0)
        layout(w, h) {
            parts.forEachIndexed { i, (pill, button, texts) ->
                val left = x0 + (i / 3) * (pillW + gap)
                val top = y0 + (i % 3) * (pillH + gap)
                pill.place(left, top)
                button.place(left + inner, top + inner)
                texts.place(left + inner + b + gap, top + (pillH - texts.height) / 2)
            }
        }
    }
}

/** Chỉ số trong [NETWORK_TILES] theo thứ tự cột của trang Mạng ngang. */
private val LAND_NETWORK_ORDER = listOf(0, 2, 1, 3, 5, 4) // máy bay, WLAN, dữ liệu | Bluetooth, điểm truy cập, đồng bộ

/**
 * JS7b: chỉ báo trang dọc ở mép phải (RE mục 5.1, `VerticalPageIndicator`; bản View là `PageDotView`): trang đang xem có nền tròn
 * kính, các trang khác mờ 45%. Chạm thì sang trang đó.
 */
@Composable
internal fun CcPageDots(panel: ControlCenterPanel, modifier: Modifier = Modifier) {
    val s = dimensionResource(R.dimen.cc_page_dot)
    val sizePx = with(LocalDensity.current) { s.roundToPx() }
    val bg = colorResource(R.color.cc_tile_bg)
    val tint = colorResource(R.color.cc_icon)
    val dots = listOf(
        R.drawable.ic_cc_page_main to R.string.cc_page_main,
        R.drawable.ic_cc_music to R.string.cc_page_media,
        R.drawable.ic_cc_wifi to R.string.cc_page_network
    )
    Column(modifier, verticalArrangement = Arrangement.Top) {
        dots.forEachIndexed { i, (icon, label) ->
            val selected = panel.page == i
            val painter = painterResource(icon)
            val description = stringResource(label)
            Box(
                Modifier
                    .padding(top = with(LocalDensity.current) { (sizePx / 4).toDp() })
                    .size(s)
                    .graphicsLayer { alpha = if (selected) 1f else DOT_IDLE_ALPHA }
                    .ccClaimTouch(panel)
                    .semantics { contentDescription = description }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        panel.showPage(i)
                    }
                    .drawBehind {
                        if (selected) drawOval(bg)
                        val pad = (size.width * 0.22f).toInt()
                        drawIconAt(painter, pad, pad, size.width.toInt() - 2 * pad, tint)
                    }
            )
        }
    }
}

/** Màu "bật" của nút không bao giờ bật (nút cài đặt ở góc, nút thiết bị phát ra): View cũ truyền 0 (trong suốt). */
internal val CC_NO_COLOR = Color.Transparent

private const val A11Y_STEPS = 9 // TalkBack bước 10% như ControlSliderView (A11Y_STEP = 0,1)
private const val BATTERY_LOW = 20
private const val STROKE_ALPHA = 140
private const val DOT_IDLE_ALPHA = 0.45f
