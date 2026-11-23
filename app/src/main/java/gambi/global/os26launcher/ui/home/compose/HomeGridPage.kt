package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import gambi.global.os26launcher.domain.model.PlacedWidgetBase

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.glass.GlassRole
import gambi.global.os26launcher.core.glass.compose.dsFrosted
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedAppWidget
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.PlacedItem
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.domain.model.WidgetKind
import kotlin.math.roundToInt

/**
 * JH2 — những gì edit mode và kéo-thả cần ở mỗi ô: đang ở edit mode không (rung, dấu "−", tắt bấm mở app), trạng thái kéo (ẩn item đang kéo,
 * dời item theo xem trước, mục tiêu gộp thư mục) và hai việc của dấu "−".
 */
internal class HomeEditContext(
    val editMode: Boolean,
    val drag: HomeDragState,
    val onRemoveApp: (Long) -> Unit,
    val onRemoveWidget: (Long) -> Unit,
    /** JH3a: huy hiệu thông báo (cài đặt + số đếm), cùng nguồn với Home View. */
    val badges: NotificationBadges.State = NotificationBadges.State(),
    /** JH3b: bấm ô thư mục → mở bảng thư mục. */
    val onOpenFolder: (Long) -> Unit = {},
    /** JH3d D4: bấm widget tự vẽ ngoài edit mode. */
    val onWidgetClick: (WidgetKind, Long) -> Unit = { _, _ -> }
,
    /**
     * P3-7: gói (packageName, userSerial) chỉ còn app đã ẩn. Widget app của gói đó trên Home bị ẩn TẠM (ô vô hình, không bấm được, không có dấu
     * "−"), vẫn giữ chỗ trong lưới (icon khác không dồn vào, hàng CSDL giữ nguyên) nên bỏ ẩn app là widget hiện lại đúng chỗ, đúng cấu hình.
     */
    val hiddenWidgetPackages: Set<Pair<String, Long>> = emptySet()
)

/**
 * JH1 Task 3 — một trang lưới của Home Compose. Vị trí và cỡ mỗi item lấy từ [cells] (`GridMetrics.measure` của vùng pager, cùng phép
 * tính với `IconGridView.onLayout`): góc trên-trái `padding + pos × (ô + khe)`, cỡ `span × ô + (span − 1) × khe`. Nhờ vậy Home Compose và
 * Home View trùng điểm ảnh, so cạnh nhau được.
 *
 * JH2: mỗi item được dời tới ô xem trước của [HomeDragState.preview] mà không đổi bố cục thật, và ẩn khi đang kéo/đang bay về/bị đẩy sang trang sau.
 *
 * PG1 A1 (AN Task 8, 9d của repo XML): vị trí có hoạt ảnh theo `itemId` ([rememberSlideOffset]) nên item trượt cả khi bố cục đổi do dữ liệu (thả
 * nhanh lên ô có icon, gỡ/ẩn rồi dồn ô, bỏ ẩn, dọn trang trống); item biến mất không do kéo thì thu về 0 tại chỗ, item mới thì phình từ 0
 * ([EnterExitTracker]). Đổi cỡ lưới hoặc đổi trang: đặt thẳng, không trượt.
 */
@Composable
internal fun HomeGridPage(
    page: HomePage,
    cells: GridMetrics.Cells,
    icons: HomeIcons,
    edit: HomeEditContext,
    onAppClick: (AppItem, ScreenRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val drag = edit.drag
    // PG1 A1: thu/phình chỉ khi bố cục đổi KHÔNG do kéo-thả (đang kéo hoặc ảnh kéo đang hạ cánh thì chính ảnh kéo là hoạt ảnh) và cùng cỡ lưới.
    val tracker = remember(page.pageId) {
        EnterExitTracker<PlacedItem>(idOf = { it.itemId }, slotOf = { it.position.x to it.position.y }, isWidget = { it is PlacedWidgetBase })
    }
    val geometry = remember(page.pageId) { GeometryHolder() }
    val sameGeometry = geometry.cells == null || geometry.cells == cells
    geometry.cells = cells
    tracker.update(page.items, animate = sameGeometry && drag.session == null && drag.settlingId == null)
    val leaving = tracker.leaving
    val all = if (leaving.isEmpty()) page.items else page.items + leaving
    Layout(
        modifier = modifier,
        content = {
            page.items.forEach { item ->
                key(item.itemId) {
                    val base = cellOffset(item.position, cells)
                    val previewPos = drag.preview[item.itemId]
                    val slide = rememberSlideOffset(
                        base = base,
                        target = if (previewPos != null) cellOffset(previewPos, cells) else base,
                        geometryKey = cells,
                        snap = drag.isHidden(item.itemId),
                        previewing = previewPos != null,
                        delayUntil = { tracker.slideDelayUntil }
                    )
                    val grow = item.itemId in tracker.growing
                    // propagateMinConstraints: ô app/thư mục luôn rộng ĐÚNG bằng ô lưới (nhãn chỉ rộng ~icon nên tự co thì hẹp hơn), để
                    // icon căn giữa như IconCellView và badge / nút "−" (tính theo cellWidth) nằm đúng góc icon.
                    Box(
                        Modifier
                            .then(if (slide != null) Modifier.offset { slide() } else Modifier)
                            .then(if (grow) Modifier.popScale(0f, pivotYOf(item, cells)) { tracker.growDone(item.itemId) } else Modifier)
                            .homeItemDecor(item, edit),
                        propagateMinConstraints = true
                    ) {
                        HomeGridItem(item, cells, icons, edit, onAppClick)
                    }
                }
            }
            // PG1 A1: item vừa biến mất (gỡ, ẩn, bỏ khỏi Home) vẽ tiếp ở chỗ cũ và thu về 0 quanh tâm icon (LeavingIcons của repo XML).
            leaving.forEach { item ->
                key("leaving", item.itemId) {
                    Box(Modifier.popScale(1f, pivotYOf(item, cells)) { tracker.leaveDone(item) }, propagateMinConstraints = true) {
                        HomeGridItem(item, cells, icons, edit, onAppClick = { _, _ -> })
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { i, m ->
            val p = all[i].position
            m.measure(Constraints.fixed(spanPx(p.spanX, cells.cellWidth, cells.gutterX), spanPx(p.spanY, cells.cellHeight, cells.gutterY)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val p = all[i].position
                placeable.place(
                    cells.paddingLeft + p.x * (cells.cellWidth + cells.gutterX),
                    cells.paddingTop + p.y * (cells.cellHeight + cells.gutterY)
                )
            }
        }
    }
}

private fun spanPx(span: Int, cell: Int, gutter: Int): Int = span * cell + (span - 1) * gutter

/** PG1 A1: góc trên-trái của ô [pos] trong trang (cùng phép tính với pha layout ở trên). */
private fun cellOffset(pos: GridPosition, cells: GridMetrics.Cells): IntOffset = IntOffset(
    cells.paddingLeft + pos.x * (cells.cellWidth + cells.gutterX),
    cells.paddingTop + pos.y * (cells.cellHeight + cells.gutterY)
)

/** PG1 A1: tâm thu/phình theo chiều dọc (phần của chiều cao ô): giữa icon cho app/thư mục (icon ở đỉnh ô), giữa widget cho widget. */
private fun pivotYOf(item: PlacedItem, cells: GridMetrics.Cells): Float =
    if (item is PlacedWidgetBase || cells.cellHeight <= 0) 0.5f else (cells.iconSize / 2f / cells.cellHeight).coerceIn(0f, 1f)

/** PG1 A1: cỡ lưới của lần dựng trước (đổi cỡ lưới thì không thu/phình, không trượt). */
private class GeometryHolder {
    var cells: GridMetrics.Cells? = null
}

@Composable
private fun HomeGridItem(
    item: PlacedItem,
    cells: GridMetrics.Cells,
    icons: HomeIcons,
    edit: HomeEditContext,
    onAppClick: (AppItem, ScreenRect?) -> Unit
) {
    val merge = edit.drag.mergeTargetId == item.itemId
    when (item) {
        is PlacedApp -> HomeAppCell(
            item.app, cells, icons,
            editMode = edit.editMode,
            badges = edit.badges,
            hoverPlate = merge,
            onRemove = { edit.onRemoveApp(item.itemId) },
            onClick = onAppClick
        )

        is PlacedFolder -> HomeFolderCell(
            item, cells, icons,
            zoomed = merge && item.appCount < FolderPlanner.MAX_ITEMS, // thư mục đầy: vẫn gộp (thả bị từ chối) nhưng không phóng
            badges = edit.badges,
            hiddenChildId = edit.drag.session?.item?.itemId ?: edit.drag.settlingId,
            onOpen = { edit.onOpenFolder(item.itemId) }
        )
        is PlacedWidgetBase -> {
            val privacyHidden = item is PlacedAppWidget && (item.provider.packageName to item.provider.userSerial) in edit.hiddenWidgetPackages
            if (item is PlacedWidget) {
                HomeWidgetCell(item, onClick = if (edit.editMode) null else ({ edit.onWidgetClick(item.kind, item.itemId) }))
            } else if (item is PlacedAppWidget) {
                if (privacyHidden) return // P3-7: ẩn tạm, giữ chỗ
                HomeAppWidgetCell(item)
            }
            if (edit.editMode) {
                val r = cells.iconSize * MINUS_BADGE_RATIO / 2f
                HomeRemoveBadge(cells.iconSize, r / 2f, r / 2f, stringResource(R.string.widget_remove_title)) { edit.onRemoveWidget(item.itemId) }
            }
        }
    }
}

/**
 * JH2 — trang trí chung của item trên lưới: ẩn (đang kéo / đang bay về / bị đẩy sang trang sau), dời tới ô xem trước bằng lò xo SLIDE, rung khi
 * ở edit mode (widget nửa biên độ).
 */
private fun Modifier.homeItemDecor(item: PlacedItem, edit: HomeEditContext): Modifier {
    val hidden = edit.drag.isHidden(item.itemId)
    // Không edit mode, không ẩn: item tĩnh, bỏ composed{} để tránh overhead mỗi item mỗi lần grid recompose (scroll).
    // PG1 A1: độ dời tới ô xem trước chuyển sang [rememberSlideOffset] (cùng cơ chế với trượt khi dữ liệu đổi).
    if (!hidden && !edit.editMode) return this
    return composed {
        this
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .homeJiggle(edit.editMode && !hidden, if (item is PlacedWidgetBase) WIDGET_JIGGLE_SCALE else 1f)
    }
}

/**
 * Ô app: icon `iconSize` căn giữa ở đỉnh ô, nhãn một dòng bên dưới (như `IconCellView.onDraw`/`drawLabel`). Dock dùng lại với `cells.showLabels
 * = false`. Bấm → [onClick] kèm vùng icon trên màn hình (animation mở app xuất phát từ đó).
 */
@Composable
internal fun HomeAppCell(
    app: AppItem,
    cells: GridMetrics.Cells,
    icons: HomeIcons,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    hoverPlate: Boolean = false,
    badges: NotificationBadges.State? = null,
    onRemove: (() -> Unit)? = null,
    onClick: (AppItem, ScreenRect?) -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val iconBounds = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val iconDp = with(density) { cells.iconSize.toDp() }
    // MERGE (IGV 1065-1079): tấm kính 1,25 × icon hiện sau app đích, lớn dần từ 0,6 bằng lò xo HOVER (900/0.6).
    val plate by animateFloatAsState(if (hoverPlate) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "hoverPlate")
    Box(modifier, contentAlignment = Alignment.TopCenter) { // dock không nhãn: Column chỉ rộng bằng icon, phải căn giữa trong ô
        Column(
            Modifier
                .semantics(mergeDescendants = true) {}
                .dsPressable(interaction, enabled = !editMode)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = !editMode) {
                    onClick(app, iconBounds[0]?.let { screenRect(it, view) })
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = rememberHomeIcon(icons, app, cells.iconSize)
            Box(
                Modifier
                    .requiredSize(iconDp)
                    .onGloballyPositioned { iconBounds[0] = it }
                    .semantics { contentDescription = app.a11yLabel.ifEmpty { app.label } }
            ) {
                if (plate > 0f) {
                    Box(
                        Modifier
                            .requiredSize(iconDp * HOVER_GLASS_SCALE)
                            .graphicsLayer {
                                val s = 0.6f + 0.4f * plate
                                scaleX = s
                                scaleY = s
                                alpha = plate.coerceIn(0f, 1f)
                            }
                            // PG3 (G4): tấm kính khi hover gộp thư mục là kính mờ tĩnh như ô thư mục.
                            .dsFrosted(GlassRole.FOLDER_CELL, RoundedCornerShape(cells.iconSize * HOVER_GLASS_SCALE * FOLDER_CORNER_RATIO))
                    )
                }
                if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            if (cells.showLabels) HomeLabel(app.label, cells)
        }
        if (badges != null) HomeNotificationBadge(badges.countFor(listOf(app.key)), badges.style, cells.cellWidth, cells.iconSize, Modifier.matchParentSize())
        if (editMode && onRemove != null) {
            val iconLeft = (cells.cellWidth - cells.iconSize) / 2f
            val c = badgeCenterForIcon(iconLeft, 0f, cells.iconSize)
            HomeRemoveBadge(cells.iconSize, c.x, c.y, stringResource(R.string.remove_app_title, app.label), Modifier.align(Alignment.TopStart), onRemove)
        }
    }
}

/**
 * Nhãn dưới icon: trắng, bóng đen ~60 %, một dòng có "…". Polish 2026-11-19: cỡ chữ = `cells.labelTextSp` (GridMetrics.IconSizing) × labelScale
 * (GL Task 11) và rộng bằng cạnh ICON (trước: cả bề ngang ô), như `IconCellView.labelWidthPx`.
 */
@Composable
private fun HomeLabel(text: String, cells: GridMetrics.Cells, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shadowRadius = with(density) { dimensionResource(R.dimen.icon_label_shadow_radius).toPx() }
    val style = remember(cells.labelScale, cells.labelTextSp, shadowRadius) {
        TextStyle(
            color = Color.White,
            fontSize = (cells.labelTextSp * cells.labelScale).sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(LABEL_SHADOW, Offset.Zero, maxOf(1f, shadowRadius * cells.labelScale))
        )
    }
    Spacer(Modifier.height(with(density) { cells.innerGapPx.toDp() }))
    BasicText(
        text = text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.width(with(density) { minOf(cells.iconSize * LABEL_WIDTH_RATIO, cells.cellWidth.toFloat()).toDp() })
    )
}

/**
 * Thư mục đóng: nền bo `iconSize × 0.225`, lưới 3 × 3 icon thu nhỏ (đệm 12 %, khe 5 % của icon), như `IconCellView.drawFolder*`. Nền phẳng
 * `folder_background` (JH1 D4: không kính thật cho thư mục). Bấm mở thư mục thuộc JH3.
 */
@Composable
internal fun HomeFolderCell(
    folder: PlacedFolder,
    cells: GridMetrics.Cells,
    icons: HomeIcons,
    zoomed: Boolean = false,
    badges: NotificationBadges.State = NotificationBadges.State(),
    /** App con đang bị kéo ra khỏi bảng thư mục: không vẽ trong icon thư mục (như `IconGridView.visiblePreview`). */
    hiddenChildId: Long? = null,
    onOpen: () -> Unit = {}
) {
    val title = folder.title.ifEmpty { stringResource(R.string.folder_default_title) }
    // MERGE vào thư mục: phóng 1,12 lần bằng lò xo HOVER (ICV 374-393); thư mục đầy thì HomeDragState không đặt mergeTargetId.
    val zoom by animateFloatAsState(if (zoomed) HOVER_ZOOM else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "folderZoom")
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .dsPressable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = title }, horizontalAlignment = Alignment.CenterHorizontally) {
            HomeFolderPlate(folder, cells.iconSize, icons, Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom }, hiddenChildId)
            if (cells.showLabels) HomeLabel(title, cells)
        }
        HomeNotificationBadge(badges.countFor(folder.previewApps.map { it.key }), badges.style, cells.cellWidth, cells.iconSize, Modifier.matchParentSize())
    }
}

/**
 * Nền thư mục + lưới 3 × 3 icon thu nhỏ, cỡ [sizePx]. Dùng cho ô thư mục và ảnh nổi lúc kéo thư mục. FM-1: thư mục nhiều trang chỉ vẽ
 * [FolderPlanner.PAGE_SIZE] app đầu (bỏ app con [hiddenChildId] đang bị kéo ra).
 */
@Composable
internal fun HomeFolderPlate(folder: PlacedFolder, sizePx: Int, icons: HomeIcons, modifier: Modifier = Modifier, hiddenChildId: Long? = null) {
    val shown = if (hiddenChildId == null) folder.previewApps else folder.previewApps.filterIndexed { i, _ -> folder.childIds.getOrNull(i) != hiddenChildId }
    val density = LocalDensity.current
    val size = sizePx.toFloat()
    val pad = size * FOLDER_PADDING_RATIO
    val gap = size * FOLDER_GAP_RATIO
    val mini = ((size - 2 * pad - 2 * gap) / FOLDER_GRID)
    val miniPx = mini.roundToInt()
    Box(
        modifier
            .requiredSize(with(density) { size.toDp() })
            // PG3 (G4): kính mờ tĩnh như ô thư mục bản XML (GlassRole.FOLDER_CELL); chưa có bản mờ / ảnh nền màu thì phẳng `folder_background` như cũ.
            .dsFrosted(GlassRole.FOLDER_CELL, RoundedCornerShape(size * FOLDER_CORNER_RATIO))
    ) {
        shown.take(FolderPlanner.PAGE_SIZE).forEachIndexed { i, app ->
            val bitmap = rememberHomeIcon(icons, app, miniPx)
            if (bitmap != null) {
                val x = pad + (i % FOLDER_GRID) * (mini + gap)
                val y = pad + (i / FOLDER_GRID) * (mini + gap)
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(with(density) { mini.toDp() })
                )
            }
        }
    }
}


private fun screenRect(coordinates: LayoutCoordinates, view: android.view.View): ScreenRect? {
    if (!coordinates.isAttached) return null
    val b = coordinates.boundsInWindow()
    val winLoc = IntArray(2)
    view.rootView.getLocationOnScreen(winLoc)
    val dx = winLoc[0]
    val dy = winLoc[1]
    return ScreenRect(b.left.roundToInt() + dx, b.top.roundToInt() + dy, b.right.roundToInt() + dx, b.bottom.roundToInt() + dy)
}

private val LABEL_SHADOW = Color(0x99000000) // như IconCellView.LABEL_SHADOW_COLOR
private const val FOLDER_GRID = 3
private const val FOLDER_CORNER_RATIO = 0.225f
private const val FOLDER_PADDING_RATIO = 0.12f
private const val FOLDER_GAP_RATIO = 0.05f
private const val HOVER_GLASS_SCALE = 1.25f
private const val HOVER_ZOOM = 1.12f

/** Polish 2026-11-19 (IconCellView.LABEL_WIDTH_RATIO): bề rộng nhãn / cạnh icon. */
private const val LABEL_WIDTH_RATIO = 1.3f // 2026-11-21: 1 -> 1.3 (nhãn iOS rộng hơn icon), kẹp ≤ cellWidth

/** FM-3 — SpringProgress.Spec.REORDER (300 / 0,85): icon dời chỗ để chừa ô cho icon đang kéo, dùng chung cho lưới, dock và bảng thư mục. */
internal val ReorderSpring = spring(dampingRatio = 0.85f, stiffness = 300f, visibilityThreshold = IntOffset(1, 1))

/** SpringProgress.Spec.SLIDE (800 / 0,8): ô trượt về chỗ khi dữ liệu đổi (ẩn app, thêm app). PG1 A1: dùng chung cho lưới, dock và bảng thư mục. */
internal val SlideSpring = spring(dampingRatio = 0.8f, stiffness = 800f, visibilityThreshold = IntOffset(1, 1))
