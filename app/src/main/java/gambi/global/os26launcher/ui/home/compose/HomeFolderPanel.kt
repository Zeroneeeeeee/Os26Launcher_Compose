package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import com.kyant.backdrop.Backdrop
import androidx.lifecycle.compose.LifecycleEventEffect
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.core.ui.TransitionMotion
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.ScreenRect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * JH3b Task 2 — bảng thư mục (bản Compose của `FolderPanelView`): nền tối toàn màn (chạm → đóng), tiêu đề sửa tại chỗ phía trên bảng, bảng
 * kính vuông (bo 36 dp) giữa màn. Mở/đóng: lò xo 400/0.85 phóng từ ô thư mục tới bảng (kiểu SMOOTH của `PanelMotion`, plan D6). Bấm app → mở
 * rồi đóng thư mục. Edit mode: rung + dấu "−" → [onRemoveApp].
 *
 * Hình học như `FolderPanelView.onMeasure`: cạnh lưới = min(trần, bề ngang − 2 × lề − 2 × đệm, chiều cao − 2 × (tiêu đề + khe) − 2 × đệm);
 * khung = lưới + đệm hai bên; lưới 3 × 3 tự đo bằng `GridMetrics(3, 3, showLabels)` (icon của bảng, không lấy cỡ icon Home).
 *
 * FM-2 — nhiều trang (tới 15 trang × 9 = 135 app): các trang đặt cạnh nhau theo chiều ngang trong CÙNG khung, cuộn bằng độ dời và bám trang
 * ([SNAP_MS] theo tốc độ chuyển cảnh, giảm tốc [SNAP_DECELERATE]); khung đứng yên nên kính và hoạt ảnh mở/đóng giữ nguyên. Vẽ cắt theo khung;
 * chấm trang (chỉ khi ≥ 2 trang) trong đệm dưới. Vuốt ngang = đổi trang (nhanh hơn [FLING_DP_PER_S] thì sang trang theo hướng vuốt; quá trang
 * đầu/cuối thì nén lại). Mở thư mục luôn vào trang 1 (Q3); bớt app làm mất trang đang xem → về trang cuối còn lại. Chỉ dựng ô của trang đích
 * và hai trang kề (thư mục 135 app không nạp hết icon một lượt).
 *
 * FM-3 — sắp xếp lại: trong lúc kéo app con, ô dời (lò xo REORDER) theo [HomeDragState.folderPreview]; cử chỉ và hẹn giờ ở [HomeDragState].
 *
 * Đổi tên (D5): ô tiêu đề lấy chữ từ state chỉ khi KHÔNG có focus; lưu khi bấm Xong trên bàn phím, mất focus, đóng bảng, hoặc Activity dừng.
 *
 * Ghi hình học bảng + lưới vào [HomeDragState.folderPanel] để cử chỉ gốc nhấn giữ / kéo / sắp xếp được (D7).
 */
@Composable
internal fun HomeFolderPanel(
    backdrop: Backdrop,
    folder: PlacedFolder?,
    originIcon: Rect?,
    showLabels: Boolean,
    icons: HomeIcons,
    edit: HomeEditContext,
    rootWidth: Float,
    rootHeight: Float,
    progress: Animatable<Float, *>,
    onAppClick: (AppItem, ScreenRect?) -> Unit,
    onRemoveApp: (ComponentKey) -> Unit,
    onRename: (Long, String) -> Unit,
    onClose: () -> Unit,
    /** PG3 Task 2 Step 3: nền mờ tĩnh của bảng dưới API 31 (ảnh chụp lớp Home đã mờ, hoặc ảnh nền mờ khi ảnh chụp chưa xong). */
    frostedBackdrop: Backdrop? = null
) {
    val drag = edit.drag
    var shown by remember { mutableStateOf(folder) }
    var shownOrigin by remember { mutableStateOf(originIcon) }
    // PG1 F3: kiểu chuyển cảnh (màn 4.1) chốt lúc bắt đầu mỗi lần mở/đóng; tốc độ đổi độ cứng spring; "Không có" thì nhảy thẳng.
    var style by remember { mutableStateOf(TransitionMotion.style) }
    LaunchedEffect(folder?.itemId) {
        style = TransitionMotion.style
        val target = if (folder != null) 1f else 0f
        if (folder != null) {
            shown = folder
            shownOrigin = originIcon
            drag.resetFolder() // FM-2 (Q3): mở thư mục luôn vào trang 1
        }
        if (TransitionMotion.isInstant) progress.snapTo(target)
        else progress.animateTo(target, PanelStyleMotion.scaledSpring(dampingRatio = 0.85f, stiffness = 400f))
        if (folder == null) shown = null
    }
    if (folder != null && folder.itemId == shown?.itemId) shown = folder // nội dung đổi khi đang mở (ẩn app, kéo ra, sắp xếp)
    val f = shown
    if (f == null || rootWidth <= 0f || rootHeight <= 0f) {
        SideEffect { drag.folderPanel = null }
        return
    }
    val density = LocalDensity.current
    val p = progress.value
    val margin = with(density) { dimensionResource(R.dimen.folder_panel_margin).toPx() }
    val padding = with(density) { dimensionResource(R.dimen.folder_panel_padding).roundToPx() }
    val maxSide = with(density) { dimensionResource(R.dimen.folder_panel_max_size).roundToPx() }
    val titleHeight = with(density) { dimensionResource(R.dimen.folder_title_height).toPx() }
    val titleGap = with(density) { dimensionResource(R.dimen.folder_title_gap).toPx() }
    // FP: khung căn giữa dọc nên chừa chỗ tiêu đề (titleHeight + titleGap) ở CẢ hai phía để tiêu đề không ra ngoài màn.
    val maxByWidth = (rootWidth - 2 * margin - 2 * padding).toInt()
    val maxByHeight = (rootHeight - 2 * (titleHeight + titleGap) - 2 * padding).toInt()
    val side = max(0, min(maxSide, min(maxByWidth, maxByHeight)))
    val panelSide = side + 2 * padding
    val cells = remember(side, showLabels, density.density) {
        GridMetrics(FolderPanelGeometry.COLUMNS, FolderPanelGeometry.COLUMNS, showLabels).measure(side, side, density.density)
    }
    if (cells == null) {
        SideEffect { drag.folderPanel = null }
        return
    }
    val left = (rootWidth - panelSide) / 2f
    val top = (rootHeight - panelSide) / 2f
    val panelRect = Rect(left, top, left + panelSide, top + panelSide)
    val gridOrigin = Offset(left + padding, top + padding)
    val geometry = FolderPanelGeometry(f.itemId, panelRect, gridOrigin, side.toFloat(), cells, f.childIds, f.previewApps)
    val pageCount = geometry.pageCount
    SideEffect {
        drag.folderPanel = if (folder != null) geometry else null
        // FM-2: bớt app làm mất trang đang xem -> về trang cuối còn lại.
        if (drag.folderPage > pageCount - 1) drag.folderPage = pageCount - 1
    }

    // FM-2: độ cuộn (px, trang i ở i × side). Bám trang đích khi trang đổi (thả tay, lật mép khi kéo, bớt trang); vuốt tay thì theo ngón.
    val scroll = remember { Animatable(0f) }
    var swiping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(drag.folderPage, side, f.itemId) {
        if (swiping) return@LaunchedEffect
        val target = drag.folderPage * side.toFloat()
        if (scroll.value == target) return@LaunchedEffect
        if (TransitionMotion.isInstant || progress.value < OPEN_EPS) {
            scroll.snapTo(target)
        } else {
            scroll.animateTo(target, tween(TransitionMotion.duration(SNAP_MS).toInt(), easing = SnapDecelerate))
        }
    }

    // Phóng từ ô thư mục: tỉ lệ ban đầu = cạnh icon / cạnh bảng, tâm đi từ tâm icon tới tâm màn.
    val origin = shownOrigin
    val s0 = if (origin != null) (origin.width / panelSide).coerceIn(0.05f, 1f) else 0.85f
    val smooth = PanelTransform(
        scale = s0 + (1f - s0) * p,
        translationX = if (origin != null) (origin.center.x - panelRect.center.x) * (1f - p) else 0f,
        translationY = if (origin != null) (origin.center.y - panelRect.center.y) * (1f - p) else 0f,
        alpha = (p / 0.4f).coerceIn(0f, 1f)
    )
    val motion = PanelStyleMotion.transform(style, p, smooth, slideFromPx = rootHeight - top) // PG1 F3
    val focus = LocalFocusManager.current

    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null, onClick = onClose)
    ) {
        val iconInsetLeft = cells.paddingLeft + (cells.cellWidth - cells.iconSize) / 2f
        FolderTitle(f, iconLeft = left + padding + iconInsetLeft, panelTop = top, panelRight = left + panelSide, p = p, onRename = onRename)
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { panelSide.toDp() })
                .graphicsLayer {
                    translationX = motion.translationX
                    translationY = motion.translationY
                    scaleX = motion.scale
                    scaleY = motion.scale
                    alpha = motion.alpha
                    transformOrigin = TransformOrigin.Center
                }
                // Kính sáng như FolderPanelView (GlassRole.FOLDER_PANEL, tint folder_panel_glass_tint); nền SHEET của DS quá tối cho thư mục.
                // Lớp Home (lưới + dock), không chỉ ảnh nền. Như iOS, panel luôn mờ đục: cố định 6 dp (fixedBlurDp, bỏ qua "Độ mờ" kính để luôn khớp nền) kể cả khi "Độ mờ" kính đặt rất thấp (không
                // thì icon/nhãn Home sắc nét lọt qua sau app của thư mục). surfaceFill = false: bỏ nền tối ds_surface_fill của CARD (panel xám
                // đục), chỉ còn kính + tint trắng như ảnh iOS 26 chủ dự án gửi 2026-11-23.
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(PANEL_RADIUS_DP, glass = true), backdrop, fixedBlurDp = FOLDER_MIN_BLUR_DP, frosted = frostedBackdrop, surfaceFill = false)
                .background(colorResource(R.color.folder_panel_glass_tint))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.clearFocus() } // chạm trong bảng không đóng
                .then(
                    if (pageCount <= 1) {
                        Modifier
                    } else {
                        Modifier.folderSwipe(
                            pageWidth = side.toFloat(),
                            pageCount = pageCount,
                            scroll = { scroll.value },
                            currentPage = { drag.folderPage },
                            onSwiping = { swiping = it },
                            onScroll = { v -> scope.launch { scroll.snapTo(v) } },
                            onSettle = { page ->
                                drag.folderPage = page
                                scope.launch {
                                    scroll.animateTo(page * side.toFloat(), tween(TransitionMotion.duration(SNAP_MS).toInt(), easing = SnapDecelerate))
                                }
                            }
                        )
                    }
                )
                .graphicsLayer { clip = true } // FM-2: trang bên cạnh không lộ ra ngoài khung
        ) {
            val page = drag.folderPage
            val previewing = drag.folderPreview != null
            // PG1 A1: app bị bỏ khỏi thư mục không do kéo (dấu "−") thu về 0 tại ô cũ (AN Task 9d, FolderPanelView); các app sau trượt như cũ.
            val tracker = remember(f.itemId) { EnterExitTracker<FolderChild>(idOf = { it.childId }, slotOf = { it.index }) }
            val lastPos = remember(f.itemId) { HashMap<Long, IntOffset>() }
            tracker.update(
                f.previewApps.indices.map { FolderChild(f.childIds[it], f.previewApps[it], it) },
                animate = drag.session == null && drag.settlingId == null
            )
            f.previewApps.indices.forEach { i ->
                val childId = f.childIds[i]
                val app = f.previewApps[i]
                val slot = drag.folderSlotOf(geometry, i)
                val slotPage = slot / FolderPlanner.PAGE_SIZE
                if (abs(slotPage - page) > 1 && !drag.isHidden(childId)) return@forEach // chỉ trang đích và hai trang kề
                key(childId) {
                    val within = slot % FolderPlanner.PAGE_SIZE
                    val x = padding + slotPage * side + cells.paddingLeft + (within % FolderPanelGeometry.COLUMNS) * (cells.cellWidth + cells.gutterX)
                    val y = padding + cells.paddingTop + (within / FolderPanelGeometry.COLUMNS) * (cells.cellHeight + cells.gutterY)
                    val target = IntOffset(x, y)
                    lastPos[childId] = target
                    // FM-3: dời bằng lò xo REORDER khi xem trước sắp xếp; còn lại SLIDE như khi ẩn app.
                    val animated by animateIntOffsetAsState(target, if (previewing) ReorderSpring else SlideSpring, label = "folderSlot")
                    val hidden = drag.isHidden(childId)
                    HomeAppCell(
                        app = app,
                        cells = cells,
                        icons = icons,
                        modifier = Modifier
                            .offset { IntOffset(animated.x - scroll.value.roundToInt(), animated.y) }
                            .width(with(density) { cells.cellWidth.toDp() })
                            .graphicsLayer { alpha = if (hidden) 0f else 1f }
                            .homeJiggle(edit.editMode && !hidden),
                        editMode = edit.editMode,
                        badges = edit.badges,
                        onRemove = { onRemoveApp(app.key) },
                        onClick = { item, rect ->
                            onAppClick(item, rect)
                            onClose()
                        }
                    )
                }
            }
            tracker.leaving.forEach { child ->
                key("leaving", child.childId) {
                    val pos = lastPos[child.childId] ?: IntOffset.Zero
                    HomeAppCell(
                        app = child.app,
                        cells = cells,
                        icons = icons,
                        modifier = Modifier
                            .offset { IntOffset(pos.x - scroll.value.roundToInt(), pos.y) }
                            .width(with(density) { cells.cellWidth.toDp() })
                            .popScale(1f, (cells.iconSize / 2f / cells.cellHeight.coerceAtLeast(1)).coerceIn(0f, 1f)) {
                                tracker.leaveDone(child)
                                lastPos.remove(child.childId)
                            },
                        editMode = edit.editMode,
                        badges = edit.badges,
                        onRemove = {},
                        onClick = { _, _ -> }
                    )
                }
            }
            if (pageCount > 1) FolderPageDots(pageCount, { scroll.value / side.coerceAtLeast(1) }, padding, side)
        }
    }
}

/**
 * FM-2 (Q4) — chấm trang trong khung, giữa đệm dưới; một chấm mỗi trang (tối đa 15), chấm sáng = trang gần độ cuộn nhất. Cùng cỡ với chấm của
 * pill tìm kiếm.
 */
@Composable
private fun FolderPageDots(pageCount: Int, position: () -> Float, padding: Int, side: Int) {
    val active = colorResource(R.color.search_pill_content)
    val inactive = colorResource(R.color.search_pill_dot_inactive)
    Canvas(Modifier.fillMaxSize()) {
        val radius = DOT_RADIUS_DP * density
        val step = radius * 2 + DOT_SPACING_DP * density
        val current = position().roundToInt().coerceIn(0, pageCount - 1)
        val total = (pageCount - 1) * step
        val startX = padding + side / 2f - total / 2f
        val cy = padding + side + padding / 2f
        for (i in 0 until pageCount) {
            drawCircle(if (i == current) active else inactive, radius, Offset(startX + i * step, cy))
        }
    }
}

/**
 * FM-2 — vuốt ngang đổi trang (`FolderPanelView.AppsGrid` onTouchEvent): qua touch slop thì cuộn theo ngón (quá trang đầu/cuối chỉ đi
 * [OVERSCROLL_RESISTANCE] quãng tay, tối đa [OVERSCROLL_LIMIT] bề ngang); thả tay: nhanh hơn [FLING_DP_PER_S] thì sang trang theo hướng vuốt,
 * không thì trang gần nhất. Nhấn giữ (kéo app) do cử chỉ gốc nhận trước nên không đụng tới đây.
 */
private fun Modifier.folderSwipe(
    pageWidth: Float,
    pageCount: Int,
    scroll: () -> Float,
    currentPage: () -> Int,
    onSwiping: (Boolean) -> Unit,
    onScroll: (Float) -> Unit,
    onSettle: (Int) -> Unit
): Modifier = pointerInput(pageWidth, pageCount) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var overSlop = 0f
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        } ?: return@awaitEachGesture
        onSwiping(true)
        val tracker = VelocityTracker()
        tracker.addPosition(start.uptimeMillis, start.position)
        var pos = scroll()
        val max = (pageCount - 1) * pageWidth
        fun dragBy(d: Float) {
            val outside = pos + d < 0f || pos + d > max
            pos = (pos + if (outside) d * OVERSCROLL_RESISTANCE else d).coerceIn(-pageWidth * OVERSCROLL_LIMIT, max + pageWidth * OVERSCROLL_LIMIT)
            onScroll(pos)
        }
        dragBy(-overSlop)
        var completed = false
        try {
            completed = horizontalDrag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                dragBy(-(change.position.x - change.previousPosition.x))
                change.consume()
            }
        } finally {
            onSwiping(false)
            val vx = tracker.calculateVelocity().x
            val base = if (pageWidth <= 0f) currentPage().toFloat() else pos / pageWidth
            val page = when {
                !completed -> base.roundToInt() // bị hủy: về trang gần nhất
                vx < -FLING_DP_PER_S * density -> floor(base).toInt() + 1
                vx > FLING_DP_PER_S * density -> ceil(base).toInt() - 1
                else -> base.roundToInt()
            }.coerceIn(0, pageCount - 1)
            onSettle(page)
        }
    }
}

@Composable
private fun FolderTitle(f: PlacedFolder, iconLeft: Float, panelTop: Float, panelRight: Float, p: Float, onRename: (Long, String) -> Unit) {
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val default = stringResource(R.string.folder_default_title)
    var text by remember(f.itemId) { mutableStateOf(f.title) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && text != f.title) text = f.title // D5: không ghi đè khi đang sửa
    val latest by rememberUpdatedState(text)
    val commit = { if (latest.trim() != f.title) onRename(f.itemId, latest) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commit() }
    DisposableEffect(f.itemId) { onDispose { commit() } }

    val titleHeight = with(density) { dimensionResource(R.dimen.folder_title_height).toPx() }
    val gap = with(density) { dimensionResource(R.dimen.folder_title_gap).toPx() }
    // Căn trái theo mép icon cột đầu (FolderPanelView.titleInset): chữ (sau phần đệm) thẳng mép icon cột đầu.
    val x = iconLeft - with(density) { dimensionResource(R.dimen.folder_title_padding_h).toPx() }
    val y = max(0f, panelTop - gap - titleHeight)
    val size = dimensionResource(R.dimen.folder_title_text_size)
    val shadowRadius = with(density) { dimensionResource(R.dimen.folder_title_shadow_radius).toPx() }
    BasicTextField(
        value = text,
        onValueChange = { text = it.take(TITLE_MAX) },
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = with(density) { size.toSp() },
            fontWeight = FontWeight.Bold,
            shadow = Shadow(colorResource(R.color.folder_title_shadow), Offset.Zero, shadowRadius)
        ),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focus.clearFocus()
        }),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    // Như iOS: tên mặc định hiện TRẮNG như tên thật; chỉ khi đang sửa mới là chữ gợi ý mờ.
                    androidx.compose.foundation.text.BasicText(
                        default,
                        style = TextStyle(
                            color = if (focused) colorResource(R.color.folder_title_hint) else Color.White,
                            fontSize = with(density) { size.toSp() },
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(colorResource(R.color.folder_title_shadow), Offset.Zero, shadowRadius)
                        )
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .widthIn(
                min = dimensionResource(R.dimen.folder_title_min_width),
                max = with(density) { (panelRight - x).coerceAtLeast(0f).toDp() } // FP: tiêu đề không vượt mép phải khung
            )
            .padding(horizontal = dimensionResource(R.dimen.folder_title_padding_h))
            .graphicsLayer { alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f) }
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            }
    )
}

private const val PANEL_RADIUS_DP = 36f
private const val FOLDER_MIN_BLUR_DP = 6f // độ mờ CỐ ĐỊNH kính panel thư mục, bằng FOLDER_BLUR_DP của HomeScreen (mờ liền với nền, như iOS 26)
private const val TITLE_MAX = 30 // FolderPlanner.normalizeTitle: trim + take(30)
private const val OPEN_EPS = 0.001f

// FM-2 — HẰNG CHỈNH TAY (cùng số với FolderPanelView.AppsGrid).
private const val DOT_RADIUS_DP = 3f
private const val DOT_SPACING_DP = 5f
private const val SNAP_MS = 320L
private const val SNAP_DECELERATE = 1.6f
private const val FLING_DP_PER_S = 400f
private const val OVERSCROLL_RESISTANCE = 0.35f
private const val OVERSCROLL_LIMIT = 0.25f

/** Như `DecelerateInterpolator(SNAP_DECELERATE)`: 1 − (1 − t)^(2 × hệ số). */
private val SnapDecelerate = Easing { t -> 1f - Math.pow((1f - t).toDouble(), (2f * SNAP_DECELERATE).toDouble()).toFloat() }

// PG1 A1: `SlideSpring` chuyển sang HomeGridPage.kt (internal), dùng chung cho lưới, dock và bảng thư mục.

/** PG1 A1: một app con của thư mục kèm chỉ số (so "thay tại chỗ" theo ô). */
private data class FolderChild(val childId: Long, val app: AppItem, val index: Int)
