package gambi.global.os26launcher.ui.controlcenter.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.wallpaperSoftBlur
import gambi.global.os26launcher.domain.controlcenter.SystemControlsPort
import gambi.global.os26launcher.domain.model.controlcenter.ControlTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTileType
import gambi.global.os26launcher.ui.controlcenter.iconRes
import gambi.global.os26launcher.ui.controlcenter.isToggle
import gambi.global.os26launcher.ui.controlcenter.labelRes
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.BlurredEdgeTreatment

/**
 * JS7b: gốc panel Compose. Lớp từ dưới lên: ảnh nền (mờ thêm 24 dp lúc vẽ ở API 31+), lớp tối theo chế độ nền (CG), ba trang dọc,
 * chỉ báo trang ở mép phải, bảng chi tiết. Ảnh nền, lớp tối và chỉ báo trang mờ theo [ControlCenterPanel.bgAlpha]; trang dịch
 * theo [ControlCenterPanel.page] + [ControlCenterPanel.dragOffset]. Cả hai đọc trong `graphicsLayer` nên kéo không recompose.
 */
@Composable
internal fun ControlCenterScreen(panel: ControlCenterPanel) {
    val gap = dimensionResource(R.dimen.cc_gap)
    val density = LocalDensity.current
    val maxWidth = with(density) { dimensionResource(R.dimen.cc_max_width).roundToPx() }
    val padPx = with(density) { dimensionResource(R.dimen.cc_pad).roundToPx() }
    val gapPx = with(density) { gap.roundToPx() }
    val stripHeight = with(density) { dimensionResource(R.dimen.cc_status_height).roundToPx() }
    BoxWithConstraints(Modifier.fillMaxSize().ccRootGestures(panel)) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val m = CcMetrics(w, h, panel.topInset, padPx, gapPx, maxWidth, stripHeight)
        // P2-8: vùng trang nằm dưới đầu trang cố định, cắt theo mép đó khi trượt đổi trang / cuộn.
        val pagesH = (h - m.headerBottom).coerceAtLeast(0)
        SideEffect { panel.pagesHeight = pagesH }
        panel.backdrop?.let { image ->
            Image(
                image, null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = panel.bgAlpha }
                    // JS7b (chủ dự án 2026-11-20: "trong suốt quá, thêm mờ"): mờ 48 dp thay 24 dp của bản View (API 31+; dưới đó ảnh đã mờ sẵn).
                    .then(if (panel.backdropBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Modifier.blur(BACKDROP_BLUR, BlurredEdgeTreatment.Rectangle) else Modifier),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = panel.bgAlpha }
                .background(colorResource(panel.scrimColorRes))
        )
        Box(
            Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(Constraints.fixed(w, pagesH))
                    layout(w, h) { p.place(0, m.headerBottom) }
                }
                .clipToBounds()
        ) {
            Box(Modifier.fillMaxSize().ccPage(panel, ControlCenterPanel.PAGE_MAIN)) { CcMainPage(panel, m) }
            CcMediaPage(panel, m, w, pagesH, padPx, gapPx, maxWidth, Modifier.fillMaxSize().ccPage(panel, ControlCenterPanel.PAGE_MEDIA))
            CcNetworkPage(panel, panel.state, m.land, Modifier.fillMaxSize().ccPage(panel, ControlCenterPanel.PAGE_NETWORK))
        }
        CcHeader(panel, m, stripHeight)
        CcPageDots(
            panel,
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = gap / 2)
                .graphicsLayer { alpha = panel.bgAlpha }
        )
        if (panel.detail != null) CcDetailLayer(panel)
    }
}

/**
 * P2-8: số đo chung của trang chính và đầu trang cố định (như `ControlCenterView` của REF HEAD). Màn ngang (W > H): lưới 8 cột liền
 * mạch, bề rộng nội dung gấp đôi. Cạnh ô chia đều bề rộng nội dung, không thu theo chiều cao nữa (dài hơn chỗ thì trang chính cuộn).
 */
internal class CcMetrics(w: Int, h: Int, topInset: Int, pad: Int, val gap: Int, maxWidth: Int, val stripHeight: Int) {
    val land = w > h
    val columns = if (land) LAND_COLUMNS else COLUMNS

    /** Bề rộng nội dung (đầu trang và lưới dùng chung để thẳng mép). */
    val contentW = min(w - 2 * pad, if (land) maxWidth * 2 + gap else maxWidth)
    val cell = (contentW - (columns - 1) * gap) / columns.toFloat()

    /** Cạnh nút cài đặt (nửa ô), không nhỏ hơn thanh trạng thái. */
    val corner = maxOf((cell / 2).toInt(), stripHeight)
    val headerTop = topInset + gap / 2

    /** Đáy đầu trang (px từ đỉnh panel): dọc = hàng nút góc rồi thanh trạng thái; ngang = cả hai cùng một hàng. */
    val headerBottom = headerTop + (if (land) maxOf(stripHeight, corner) else corner + stripHeight) + gap
}

/**
 * P2-8: đầu trang cố định (nút cài đặt + thanh trạng thái), nằm ngoài các trang nên không trượt khi đổi trang / cuộn. Dọc: hàng nút
 * góc trên cùng rồi thanh trạng thái (iOS 26); ngang: cùng một hàng, thanh trạng thái bên trái, nút cài đặt bên phải.
 */
@Composable
private fun CcHeader(panel: ControlCenterPanel, m: CcMetrics, stripHeight: Int) {
    val listener = { panel.listener }
    Layout(
        content = {
            CcTile(
                panel, R.drawable.ic_cc_settings, stringResource(R.string.cc_open_settings), false, CC_NO_COLOR, CC_NO_COLOR,
                Modifier.ccBlock(panel.settingsAnim), checkable = false, glass = false
            ) { listener()?.onOpenSettings() }
            CcStatusStrip(panel.state, Modifier.ccBlock(panel.stripAnim).ccClaimTouch(panel))
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val cs = m.corner
        val stripW = (if (m.land) m.contentW - cs - m.gap else m.contentW).coerceAtLeast(0)
        val corner = measurables[0].measure(Constraints.fixed(cs, cs))
        val strip = measurables[1].measure(Constraints.fixed(stripW, stripHeight))
        layout(w, constraints.maxHeight) {
            val x0 = (w - m.contentW) / 2
            val y = m.headerTop
            if (m.land) {
                val rowH = maxOf(stripHeight, cs)
                strip.place(x0, y + (rowH - stripHeight) / 2)
                corner.place(x0 + m.contentW - cs, y + (rowH - cs) / 2)
            } else {
                corner.place(x0 + m.contentW - cs, y)
                strip.place(x0, y + cs)
            }
        }
    }
}

/**
 * P2-8: trang Nhạc. Dọc: thẻ rộng `min(W, maxWidth)·0,84`; ngang: thẻ nằm ngang rộng bằng nội dung lưới, ảnh bìa vuông cao gần hết vùng
 * trang (tối đa 40% bề ngang thẻ). Thẻ đo theo cao TỰ NHIÊN rồi thu đều (scale quanh tâm, 0,5..1) cho vừa vùng trang trừ thanh điều
 * hướng và một khe mỗi phía; dời lên nửa phần đáy bị thanh điều hướng che.
 */
@Composable
private fun CcMediaPage(
    panel: ControlCenterPanel, m: CcMetrics, w: Int, pagesH: Int, pad: Int, gap: Int, maxWidth: Int, modifier: Modifier
) {
    val bottomInset = panel.bottomInset
    val playerW = if (m.land) m.contentW else (min(w, maxWidth) * PLAYER_WIDTH).toInt()
    val artSize = if (m.land) {
        val pageH = pagesH - bottomInset - pad
        min(pageH - 2 * pad, (playerW * LAND_ART_FRACTION).toInt()).coerceAtLeast(0)
    } else 0
    Layout(
        content = { CcMediaPlayer(panel, panel.media, playerW, landscape = m.land, artSizePx = artSize) },
        modifier = modifier
    ) { measurables, constraints ->
        val p = measurables[0].measure(Constraints(maxWidth = playerW.coerceAtLeast(0)))
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight
        val availH = (pagesH - bottomInset - 2 * gap).toFloat()
        val availW = (cw - 2 * pad).toFloat()
        val scale = if (p.width <= 0 || p.height <= 0) 1f
        else minOf(1f, availH / p.height, availW / p.width).coerceAtLeast(MIN_PLAYER_SCALE)
        layout(cw, ch) {
            p.placeWithLayer((cw - p.width) / 2, (ch - p.height) / 2) {
                scaleX = scale
                scaleY = scale
                translationY = -bottomInset / 2f
            }
        }
    }
}

/** Dịch trang [index] theo trang đang xem và độ kéo; `size.height` là chiều cao vùng trang (dưới đầu trang, P2-8). */
private fun Modifier.ccPage(panel: ControlCenterPanel, index: Int): Modifier = graphicsLayer {
    translationY = (index - panel.page) * size.height + panel.dragOffset
}

/**
 * JS7b: cử chỉ của gốc panel (thay `onInterceptTouchEvent` + `onTouchEvent` của `ControlCenterView`). Đọc ở pass Initial nên thấy
 * chạm trước mọi phần tử con; vượt slop theo chiều dọc thì giành (tiêu thụ) để ô con huỷ nhấn, như View cũ chặn chuỗi chạm. Thanh
 * trượt đặt [ControlCenterPanel.disallowIntercept] thì gốc không giành. Logic quyết định nằm ở [ControlCenterPanel].
 */
private fun Modifier.ccRootGestures(panel: ControlCenterPanel): Modifier = pointerInput(panel) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!panel.gesturesEnabled) {
            panel.childTouched = false // bảng chi tiết tự xử lý (xem CcDetailLayer)
            return@awaitEachGesture
        }
        panel.onRootDown(down.position.x, down.position.y)
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, down.position)
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocity.addPosition(change.uptimeMillis, change.position)
                if (change.changedToUpIgnoreConsumed()) {
                    ended = true
                    panel.onRootUp(change.position.x, change.position.y, velocity.calculateVelocity().y)
                    break
                }
                if (panel.onRootMove(change.position.x, change.position.y)) change.consume()
            }
        } finally {
            if (!ended) panel.onRootCancel()
        }
    }
}

/**
 * JS7b: trang chính: lưới 4 cột (thay lớp `Grid` của View cũ, cùng công thức RE mục 5.1: ô nhỏ 18%·W, khối 40%·W, khe W/25). Cạnh ô
 * `(W − 3·gap)/4` trên bề rộng nội dung `min(W − 2·pad, maxWidth)` ([CcMetrics]). P2-8: không thu theo chiều cao nữa — lưới dài hơn
 * vùng trang thì cuộn ([ControlCenterPanel.mainScroll]); màn ngang là lưới 8 cột ([LAND_FIXED_CELLS]); đầu trang ở [CcHeader].
 * ```
 * [ kết nối 2×2 ][ nhạc 2×2   ]
 * [xoay][DND ][sáng][âm ]
 * [ chờ 2×1   ][ 1×2][1×2]
 * [ô tùy chỉnh, 4 ô/hàng, tối đa 12]
 * ```
 */
@Composable
private fun CcMainPage(panel: ControlCenterPanel, m: CcMetrics) {
    val pad = with(LocalDensity.current) { dimensionResource(R.dimen.cc_pad).roundToPx() }
    val state = panel.state
    val slots = panel.customSlots
    val land = m.land
    val cells = remember(slots, land) {
        val fixed = if (land) LAND_FIXED_CELLS else FIXED_CELLS
        val cols = if (land) LAND_COLUMNS else COLUMNS
        val firstRow = if (land) LAND_FIRST_CUSTOM_ROW else FIRST_CUSTOM_ROW
        fixed + slots.indices.map { i -> GridCell(i % cols, firstRow + i / cols) }
    }
    val listener = { panel.listener }
    Layout(
        // P2-8: cuộn bằng dịch lớp (không đo/đặt lại khi cuộn); vùng trang cắt phần ra ngoài.
        modifier = Modifier.graphicsLayer { translationY = -panel.mainScroll },
        content = {
            CcConnectivityBlock(panel, state, Modifier.ccBlock(panel.connectivityAnim))
            CcMediaTile(panel, panel.media, Modifier.ccBlock(panel.mediaAnim))
            CcSquareTile(
                panel, R.drawable.ic_cc_rotation_lock, stringResource(R.string.cc_tile_rotation), state.rotationLocked, R.color.cc_red,
                Modifier.ccBlock(panel.rotationAnim)
            ) { listener()?.onTileClick(ControlTile.ROTATION_LOCK) }
            CcSquareTile(
                panel, R.drawable.ic_cc_dnd, stringResource(R.string.cc_tile_dnd), state.dndOn, R.color.cc_indigo,
                Modifier.ccBlock(panel.dndAnim)
            ) { listener()?.onTileClick(ControlTile.DND) }
            CcSlider(
                panel, panel.brightness, R.drawable.ic_cc_brightness, stringResource(R.string.cc_brightness),
                Modifier.ccBlock(panel.brightnessAnim), iconOnFillColor = R.color.cc_sun,
                onLongPress = { panel.showBrightnessDetail() }
            ) { v, done -> listener()?.onBrightness(v, done) }
            CcSlider(
                panel, panel.volume, R.drawable.ic_cc_volume, stringResource(R.string.cc_volume),
                Modifier.ccBlock(panel.volumeAnim), onLongPress = { panel.showVolumeDetail() }
            ) { v, done -> panel.onMainVolume(v, done) }
            CcTimeoutTile(panel, state.screenTimeoutMs, Modifier.ccBlock(panel.timeoutAnim))
            slots.forEach { slot -> CcCustomTile(panel, slot, panel.nightShiftOn) }
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gap = m.gap
        val c = m.cell
        val rows = cells.maxOf { it.row + it.rowSpan }
        val placeables = cells.mapIndexed { i, cell ->
            val cw = (c * cell.colSpan + gap * (cell.colSpan - 1)).toInt()
            val ch = (c * cell.rowSpan + gap * (cell.rowSpan - 1)).toInt()
            measurables[i].measure(Constraints.fixed(cw, ch))
        }
        // P2-8: lưới dài hơn vùng trang thì cuộn; chừa lề dưới và thanh điều hướng.
        val contentHeight = (rows * c + (rows - 1).coerceAtLeast(0) * gap).toInt() + pad + panel.bottomInset
        panel.updateMainMaxScroll((contentHeight - h).toFloat())
        layout(w, h) {
            val x0 = (w - m.contentW) / 2f
            placeables.forEachIndexed { i, p ->
                val cell = cells[i]
                p.place((x0 + cell.col * (c + gap)).toInt(), (cell.row * (c + gap)).toInt())
            }
        }
    }
}

/** Một ô tùy chỉnh (RE mục 5.3): ô dựng sẵn đọc trạng thái từ hệ thống; ô app có icon app và tên app (presenter nạp). */
@Composable
private fun CcCustomTile(panel: ControlCenterPanel, slot: CcCustomSlot, nightShiftOn: Boolean) {
    val state = panel.state
    val modifier = Modifier.ccBlock(slot.anim)
    val onClick: () -> Unit = { panel.listener?.onCustomTileClick(slot.tile) }
    when (val tile = slot.tile) {
        is CustomTile.Builtin -> {
            val on = when (tile.type) {
                CustomTileType.FLASHLIGHT -> state.torchOn
                CustomTileType.BATTERY -> state.powerSave
                CustomTileType.DARK_MODE -> state.darkMode
                CustomTileType.NIGHT_SHIFT -> nightShiftOn
                else -> false
            }
            val available = tile.type != CustomTileType.FLASHLIGHT || state.torchAvailable
            CcSquareTile(
                panel, tile.type.iconRes(), stringResource(tile.type.labelRes()), on, R.color.cc_icon_on_white,
                // Đèn pin không có: mờ 40% (nhân thêm vào alpha của hoạt ảnh mở, không ghi đè nó).
                modifier.graphicsLayer { alpha = if (available) 1f else DISABLED_ALPHA },
                checkable = tile.type.isToggle(), enabled = available, onClick = onClick
            )
        }

        is CustomTile.App -> CcSquareTile(
            panel, R.drawable.ic_cc_app, slot.contentDescription ?: stringResource(R.string.cc_tile_app), false,
            R.color.cc_icon_on_white, modifier, checkable = false, appIcon = slot.appIcon, onClick = onClick
        )
    }
}

/**
 * JS7b: lớp bảng chi tiết (thay `DetailLayer`, RE mục 5.2 `mrd.m()`/`n()`): nền tối `cc_detail_scrim`, mờ vào/ra 300 ms, nội dung ở
 * giữa. Chạm ngoài nội dung thì đóng bảng.
 */
@Composable
private fun CcDetailLayer(panel: ControlCenterPanel) {
    val detail = panel.detail ?: return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = panel.detailAlpha }
            .background(colorResource(R.color.cc_detail_scrim))
            .pointerInput(panel) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var up = false
                    while (!up) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        up = !c.pressed
                    }
                    if (up && !panel.childTouched) panel.hideDetail()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (detail) {
            CcDetail.BRIGHTNESS -> CcBigSliderPanel(
                panel, R.drawable.ic_cc_brightness, R.string.cc_brightness, R.color.cc_sun, withAutoToggle = true
            )

            CcDetail.VOLUME -> CcBigSliderPanel(
                panel, R.drawable.ic_cc_volume, R.string.cc_volume, R.color.cc_slider_icon_on_fill, withAutoToggle = false
            )

            CcDetail.TIMEOUT -> CcTimeoutPicker(panel)
        }
    }
}

/**
 * JS7b: bảng độ sáng / âm lượng lớn (thay `BigSliderPanel`, RE mục 5.2 `fqd`): icon ở trên, thanh dọc cao tỉ lệ 18:47, nút phụ ở dưới
 * (độ sáng tự động). Chiều cao thanh = `min(min(W, H)·0,8, H·0,5)` tính lúc mở.
 */
@Composable
private fun CcBigSliderPanel(panel: ControlCenterPanel, icon: Int, label: Int, iconOnFill: Int, withAutoToggle: Boolean) {
    val density = LocalDensity.current
    val s = dimensionResource(R.dimen.cc_detail_icon)
    val sPx = with(density) { s.roundToPx() }
    val heightPx = panel.bigSliderHeight()
    val description = stringResource(label)
    Column(
        Modifier.ccClaimTouch(panel).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CcIcon(icon, s)
        CcSlider(
            panel, panel.detailSlider, icon, description,
            Modifier
                .padding(top = with(density) { (sPx / 2).toDp() })
                .size(with(density) { (heightPx * 18 / 47).toDp() }, with(density) { heightPx.toDp() }),
            iconOnFillColor = iconOnFill
        ) { v, done -> panel.onDetailSlider(v, done) }
        if (withAutoToggle) {
            val ts = (sPx * 1.6f).toInt()
            CcSquareTile(
                panel, R.drawable.ic_cc_brightness, stringResource(R.string.cc_auto_brightness), panel.state.autoBrightness,
                R.color.cc_icon_on_white,
                Modifier.padding(top = with(density) { (sPx / 2).toDp() }).size(with(density) { ts.toDp() })
            ) { panel.listener?.onAutoBrightness() }
            CcText(stringResource(R.string.cc_auto_brightness), 12f, Modifier.clearAndSetSemantics {}, align = TextAlign.Center)
        }
    }
}

/** JS7b: bảng chọn thời gian chờ màn hình (thay `TimeoutPickerPanel`, RE mục 5.2 `uvd`). Mức gần nhất với mức đang dùng tô xanh. */
@Composable
private fun CcTimeoutPicker(panel: ControlCenterPanel) {
    val density = LocalDensity.current
    val glass = rememberCcGlass(cornerRadius = with(density) { dimensionResource(R.dimen.cc_block_radius).toPx() })
    val pad = dimensionResource(R.dimen.cc_gap)
    val current = panel.state.screenTimeoutMs
    val nearest = SystemControlsPort.SCREEN_TIMEOUTS_MS.minBy { abs(it - current) }
    val blue = colorResource(R.color.cc_blue)
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .ccGlass(glass)
            .ccClaimTouch(panel)
            .padding(vertical = pad / 2)
    ) {
        CcText(
            stringResource(R.string.cc_screen_timeout), 13f,
            Modifier.padding(horizontal = pad * 2, vertical = pad / 2), alpha = 0.6f
        )
        for (ms in SystemControlsPort.SCREEN_TIMEOUTS_MS) {
            val text = ccTimeoutLabel(ms)
            CcText(
                text, 16f,
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        panel.onPickTimeout(ms)
                    }
                    .padding(start = pad * 2, top = pad, end = pad * 6, bottom = pad),
                color = if (ms == nearest) blue else colorResource(R.color.cc_icon)
            )
        }
    }
}

/** Vị trí của một khối trong lưới 4 cột. */
private class GridCell(val col: Int, val row: Int, val colSpan: Int = 1, val rowSpan: Int = 1)

// Thứ tự khớp với content của CcMainPage (P2-8: nút góc và thanh trạng thái đã ra đầu trang cố định, CcHeader).
private val FIXED_CELLS = listOf(
    GridCell(0, 0, 2, 2), // kết nối
    GridCell(2, 0, 2, 2), // nhạc
    GridCell(0, 2), // xoay
    GridCell(1, 2), // DND
    GridCell(2, 2, 1, 2), // độ sáng
    GridCell(3, 2, 1, 2), // âm lượng
    GridCell(0, 3, 2, 1) // thời gian chờ
)

/**
 * P2-8: màn ngang, lưới 8 cột liền mạch như iOS 18 (cùng thứ tự với [FIXED_CELLS]):
 * ```
 * [kết nối 2×2][nhạc 2×2][xoay][DND][sáng][âm]
 * [           ][        ][ chờ 2×1 ][1×2 ][1×2]
 * [ô tùy chỉnh, 8 ô/hàng ...]
 * ```
 */
private val LAND_FIXED_CELLS = listOf(
    GridCell(0, 0, 2, 2), // kết nối
    GridCell(2, 0, 2, 2), // nhạc
    GridCell(4, 0), // xoay
    GridCell(5, 0), // DND
    GridCell(6, 0, 1, 2), // độ sáng
    GridCell(7, 0, 1, 2), // âm lượng
    GridCell(4, 1, 2, 1) // thời gian chờ
)

private const val COLUMNS = 4
private const val FIRST_CUSTOM_ROW = 4
private const val LAND_COLUMNS = 8 // P2-8
private const val LAND_FIRST_CUSTOM_ROW = 2 // P2-8: ngang, ô tùy chỉnh bắt đầu từ hàng này
private const val DISABLED_ALPHA = 0.4f
private const val PLAYER_WIDTH = 0.84f
private const val MIN_PLAYER_SCALE = 0.5f // P2-8: không co thẻ nhạc nhỏ hơn nửa
private const val LAND_ART_FRACTION = 0.4f // P2-8: ảnh bìa chiếm tối đa 40% bề ngang thẻ nhạc khi màn ngang

/** JS7b: bán kính mờ ảnh nền panel (chỉnh tay theo chủ dự án). */
private val BACKDROP_BLUR = 48.dp
