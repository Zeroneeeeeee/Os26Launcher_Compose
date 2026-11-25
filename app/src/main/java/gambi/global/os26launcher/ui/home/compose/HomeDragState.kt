package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerId
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.layout.GridInsertPlanner
import gambi.global.os26launcher.domain.layout.PageGeometry
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.ContainerId
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.model.PlacedItem
import gambi.global.os26launcher.domain.model.PlacedWidgetBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.model.WidgetSize

/** Việc ghi của kéo-thả, nối thẳng vào `HomeViewModel` (JH2 D4). */
interface HomeDragActions {
    fun enterEditMode()
    fun exitEditMode()
    fun onDragStarted(itemId: Long)
    fun onDragCancelled()
    fun onItemDropped(itemId: Long, targetContainer: Long, targetPageId: Long?, cell: GridPosition)
    fun onFolderIntent(draggedId: Long, targetId: Long)
    fun onAddToFolder(itemId: Long, folderId: Long)
    fun onDragEdgeBeyondLastPage(): Boolean
    fun onGhostPageLeft()

    /** JH3b D1: nhấn giữ app ngoài edit mode → menu. [anchor] là vùng icon (toạ độ gốc = cửa sổ, tràn viền). */
    fun onIconLongPressed(itemId: Long, container: Long, key: ComponentKey, anchor: Rect)

    /** JH3b D2: nhấn giữ icon nổi khi menu đang mở = "Sửa Màn hình chính" rồi kéo. */
    fun onMenuEditHome()

    /** JH3b D7: kéo app ra khỏi bảng thư mục. */
    fun closeFolder()

    /** FM-3: thả app con vào chỗ mới trong bảng thư mục: [orderedChildIds] là thứ tự mới của các app nhìn thấy. */
    fun onReorderFolder(folderId: Long, orderedChildIds: List<Long>)

    /**
     * PG2 (G2c Task 13): thả widget tự vẽ kéo từ bảng chọn (CHƯA có hàng DB) xuống [cell] của trang [targetPageId] (`PageId.NEW` = trang tạm);
     * [tempItemId] là id tạm của phiên kéo. Nối tới `HomeViewModel.onNewWidgetDropped`.
     */
    fun onNewWidgetDropped(kindId: String, spanX: Int, spanY: Int, tempItemId: Long, targetPageId: Long, cell: GridPosition)

    /** LD Task 4: thả app kéo từ App Library (chưa có hàng DB) xuống lưới ([ContainerId.HOME], trang [targetPageId]) hoặc dock (slot `cell.x`). */
    fun onLibraryAppDropped(app: AppItem, targetContainer: Long, targetPageId: Long?, cell: GridPosition)
}

/** Thứ đang được kéo. App và thư mục có thể đến từ lưới hoặc dock; widget chỉ từ lưới. */
sealed interface DragItem {
    val itemId: Long

    data class App(override val itemId: Long, val app: AppItem) : DragItem
    data class Folder(val folder: PlacedFolder) : DragItem {
        override val itemId: Long get() = folder.itemId
    }

    data class Widget(val widget: PlacedWidgetBase) : DragItem {
        override val itemId: Long get() = widget.itemId
    }
}

/** Kết quả chạm xuống: một item (lưới, dock hoặc bảng thư mục), vùng trống (nhấn giữ = edit mode), hay vùng không thuộc Home. */
sealed interface HomeHit {
    data class Item(val item: DragItem, val container: Long, val pageId: Long?, val cell: GridPosition, val rect: Rect) : HomeHit
    data object Gap : HomeHit
    data object Outside : HomeHit
}

class HomeDragSession(
    val item: DragItem,
    val sourceContainer: Long,
    val sourcePageId: Long?,
    val sourceCell: GridPosition,
    val sourceRect: Rect,
    val spanX: Int,
    val spanY: Int,
    val pointerId: PointerId,
    /**
     * P2-7: tâm ảnh kéo trừ ngón tay lúc bắt đầu. Khác 0 (widget) = ảnh giữ nguyên chỗ so với ngón tay (widget nhấn giữ ở góc vẫn nằm dưới
     * ngón ở góc đó); đích được tính tại "điểm ảnh" = ngón tay + độ lệch. 0 = ảnh căn giữa ngón tay (icon, thư mục).
     */
    val grabOffset: Offset = Offset.Zero,
    /** PG2: phiên kéo widget tự vẽ mới từ bảng chọn (item là `PlacedWidget` tạm, id âm, chưa có hàng DB); null = item có sẵn. */
    val newWidget: WidgetKind? = null,
    /**
     * LD Task 4: app kéo từ App Library ([item] là [DragItem.App] với id [LIBRARY_DRAG_ID], chưa có hàng DB). Không gộp thư mục (chỉ đẩy-dồn), thả =
     * [HomeDragActions.onLibraryAppDropped], thả hỏng = mờ dần tại chỗ (không có ô nguồn để bay về).
     */
    val fromLibrary: Boolean = false
) {
    val isWidget: Boolean get() = item is DragItem.Widget
}

/** LD Task 4: id giả của app đang kéo từ App Library (id hàng thật luôn > 0; khác `PageId.NEW` / id tạm của planner). */
const val LIBRARY_DRAG_ID = -7_000_001L

/**
 * JH2 — trạng thái kéo-thả của Home Compose. Giữ phiên kéo, ảnh nổi (vị trí, tỉ lệ, độ trong), xem trước đẩy-dồn trên lưới và trong dock, mục
 * tiêu gộp thư mục, hẹn giờ lật trang ở mép. Là bản Compose của `DragHelper` + máy trạng thái hover của `IconGridView` / `DockView` (NONE /
 * MERGE / PUSH) + sắp xếp trong `FolderPanelView`, cùng hằng số (plan D3).
 *
 * Hover (AN Task 9b/9c/9e, P2-6 "grid-consistent dock drag"): lưới và dock dùng CHUNG một máy trạng thái. Ngón tay (điểm ảnh) ở VÙNG GIỮA icon
 * gộp được → sau [HOVER_ARM_MS] gộp (MERGE); ngoài vùng giữa trên một ô có icon → chỉ khi ngón tay ĐÃ DỪNG (lệch ≤ [PUSH_SETTLE_DP]) đủ
 * [PUSH_ARM_MS] mới xem trước đẩy-dồn (PUSH). PUSH đang hiện thì ở lại chừng nào ngón tay còn trong ô / slot neo; đang PUSH mà ngón tay tới giữa
 * một icon ĐANG BỊ ĐẨY thì giữ hàng.
 *
 * Hình học (toạ độ gốc của `HomeScreen`) do `HomeScreen` cập nhật mỗi lần bố cục: [pagerRect], [dockRect], [cells], [pages], [dock]. Không
 * lưu toạ độ từng ô: ô suy ra từ [cells] như `IconGridView.onLayout`, nên trang vừa bị pager gỡ khỏi composition vẫn tính được.
 */
@Stable
class HomeDragState(private val scope: CoroutineScope, private val density: Float) {
    lateinit var actions: HomeDragActions
    lateinit var pagerState: PagerState

    var pagerRect = Rect.Zero
    var dockRect = Rect.Zero
    var rootWidth = 0f
    var rootHeight = 0f
    var cells: GridMetrics.Cells? = null
    var pages: List<HomePage> = emptyList()
    var realPageCount = 0
    var dock: List<DockApp> = emptyList()

    // P2-5: vùng ngoài lưới nhấn giữ được (SystemBarsController.isHomeLongPressArea): bỏ dải thanh trạng thái và dải thanh điều hướng (trừ khi
    // đã ẩn thanh điều hướng). Pill và các nút edit mode tự nhận chạm nên không phải vùng trống.
    var statusBarTop = 0f
    var navBarBottom = 0f
    var navBarHidden: () -> Boolean = { false }
    var pillRect = Rect.Zero
    var pillVisible = false

    /** P2-11: pill đang hiện chấm và nhận cử chỉ chấm (chạm bắt đầu ở pill không mở tìm kiếm / Library, không vào edit mode). */
    var pillDotsInteractive = false

    /** Vùng các nút của thanh edit mode (khóa = thứ tự nút); nút tự nhận chạm nên không phải vùng trống. */
    val toolbarRects = HashMap<Int, Rect>()

    /** P2-9e: menu "Sửa" đang mở: lớp phủ của nó nhận mọi chạm (chạm ra ngoài chỉ đóng menu), cử chỉ gốc coi mọi chỗ là ngoài lưới. */
    var editMenuOpen = false

    /** JH3b: bảng thư mục đang mở (khung + lưới, toạ độ gốc); null = đóng. Bảng tự cập nhật khi bố cục. */
    var folderPanel: FolderPanelGeometry? = null

    /** JH3b D2: menu đang mở cho item nào (itemId, container); null = không có menu. */
    var menuOwner: Pair<Long, Long>? = null

    var session by mutableStateOf<HomeDragSession?>(null)
        private set

    /** Item vừa thả, đang chờ ảnh nổi bay về chỗ: ô của nó vẫn ẩn tới khi bay xong (như `DragHelper.settlingItemId`). */
    var settlingItem by mutableStateOf<DragItem?>(null)
        private set
    var settlingId by mutableStateOf<Long?>(null)
        private set

    /** Xem trước đẩy-dồn: itemId → ô mới trên trang [previewPageId]; [previewHidden] = item bị đẩy sang trang sau (ẩn, như View). */
    var preview by mutableStateOf<Map<Long, GridPosition>>(emptyMap())
        private set
    var previewHidden by mutableStateOf<Set<Long>>(emptySet())
        private set
    var previewPageId by mutableStateOf<Long?>(null)
        private set

    /** Đích gộp đang hiện (MERGE): app → tấm kính sau icon, thư mục chưa đầy → phóng thư mục. Thả = tạo thư mục / thêm vào thư mục. */
    var mergeTargetId by mutableStateOf<Long?>(null)
        private set

    /** Thứ tự dock đang xem trước, [DOCK_GAP] là chỗ trống cho item đang kéo; null = không xem trước. */
    var dockPreview by mutableStateOf<List<Long>?>(null)
        private set

    /** FM-2: trang ĐÍCH của bảng thư mục (đã chọn khi thả tay / lật mép), không phải trang đang nhìn thấy giữa lúc trượt. */
    var folderPage by mutableIntStateOf(0)

    /** FM-3: thứ tự childId đang xem trước khi kéo app trong bảng thư mục; null = thứ tự của dữ liệu. */
    var folderPreview by mutableStateOf<List<Long>?>(null)
        private set

    val floatCenter = Animatable(Offset.Zero, Offset.VectorConverter)
    val floatScale = Animatable(1f)
    val floatAlpha = Animatable(1f)
    var floatSize by mutableStateOf(Size.Zero)
        private set

    /**
     * Nền kính thật của Home (lớp ảnh nền), do `HomeScreen` gán. Ảnh nổi vẽ ở tầng Activity (ngoài `LocalDsBackdrop` của HomeScreen) đọc giá trị
     * này để thẻ widget / thư mục đang kéo vẫn là kính như trên lưới (trước: không có nền nên thẻ gần như trong suốt, trông như biến dạng).
     */
    var glassBackdrop by mutableStateOf<com.kyant.backdrop.Backdrop?>(null)

    private var finger = Offset.Zero
    private var edgeDir = 0
    private var edgeJob: Job? = null
    private var lastIdleAt = 0L
    private var flipping = false

    fun isHidden(itemId: Long): Boolean =
        session?.item?.itemId == itemId || settlingId == itemId || itemId in previewHidden

    /** P2-11: chạm bắt đầu ở [pos] là cử chỉ của pill dạng chấm. */
    fun pillClaims(pos: Offset): Boolean = pillVisible && pillDotsInteractive && pillRect.contains(pos)

    /** Điểm ảnh kéo hiện tại (ngón tay + độ lệch neo P2-7). */
    private fun point(s: HomeDragSession): Offset = finger + s.grabOffset

    // ---------------------------------------------------------------- hit-test

    fun hitTest(pos: Offset): HomeHit {
        if (editMenuOpen) return HomeHit.Outside
        folderPanel?.let { panel ->
            // Bảng thư mục đang mở che Home: chỉ ô con (trang đang xem) là item; còn lại (nền tối, khoảng trống trong bảng) không phải vùng trống.
            for (i in panel.childIds.indices) {
                val slot = folderSlotOf(panel, i)
                if (slot / FolderPlanner.PAGE_SIZE != folderPage) continue
                val rect = panel.slotIconRect(slot, folderPage)
                if (rect.contains(pos)) {
                    return HomeHit.Item(DragItem.App(panel.childIds[i], panel.apps[i]), panel.folderId, null, GridPosition(i, 0), rect)
                }
            }
            return HomeHit.Outside
        }
        val c = cells ?: return HomeHit.Outside
        if (dockRect.contains(pos)) {
            val index = dockSlotRects(dock.size).indexOfFirst { it.contains(pos) }
            val item = dock.getOrNull(index) ?: return HomeHit.Gap // P2-5: quanh/giữa các ô dock là vùng trống
            val folder = item.folder
            val app = item.app
            val dragItem = when {
                folder != null -> DragItem.Folder(folder)
                app != null -> DragItem.App(item.itemId, app)
                else -> return HomeHit.Gap
            }
            return HomeHit.Item(dragItem, ContainerId.DOCK, null, GridPosition(index, 0), dockIconRect(index, dock.size))
        }
        if (pagerRect.contains(pos)) {
            val page = currentPage() ?: return HomeHit.Gap
            val cell = c.cellAt(pos.x - pagerRect.left, pos.y - pagerRect.top) ?: return HomeHit.Gap
            val placed = page.items.firstOrNull { covers(it.position, cell) } ?: return HomeHit.Gap
            val item = when (placed) {
                is PlacedApp -> DragItem.App(placed.itemId, placed.app)
                is PlacedFolder -> DragItem.Folder(placed)
                is PlacedWidgetBase -> DragItem.Widget(placed)
            }
            return HomeHit.Item(item, ContainerId.HOME, page.pageId, placed.position, visualRect(placed.position, item is DragItem.Widget))
        }
        // P2-5: phần ngoài lưới (hai bên pill, dải trên lưới, dải đáy khi ẩn thanh điều hướng) cũng là vùng trống.
        if (pillVisible && pillRect.contains(pos)) return HomeHit.Outside
        if (toolbarRects.values.any { it.contains(pos) }) return HomeHit.Outside
        if (pos.y < statusBarTop) return HomeHit.Outside
        if (!navBarHidden() && pos.y > rootHeight - navBarBottom) return HomeHit.Outside
        return HomeHit.Gap
    }

    // ---------------------------------------------------------------- session

    fun start(hit: HomeHit.Item, pos: Offset, pointerId: PointerId) {
        val spanX = (hit.item as? DragItem.Widget)?.widget?.position?.spanX ?: 1
        val spanY = (hit.item as? DragItem.Widget)?.widget?.position?.spanY ?: 1
        settlingId = null // C14: lần bay trước bị cắt ngang thì coi như xong
        settlingItem = null
        // P2-7: widget giữ nguyên chỗ so với ngón tay (độ lệch = tâm ô - ngón tay); icon/thư mục vẫn căn giữa ngón tay như cũ.
        val grab = if (hit.item is DragItem.Widget) hit.rect.center - pos else Offset.Zero
        session = HomeDragSession(hit.item, hit.container, hit.pageId, hit.cell, hit.rect, spanX, spanY, pointerId, grab)
        floatSize = hit.rect.size
        finger = pos
        scope.launch {
            floatCenter.snapTo(hit.rect.center)
            floatAlpha.snapTo(1f)
            floatScale.snapTo(1f)
            launch { floatCenter.animateTo(pos + grab, SETTLE_O) }
            floatScale.animateTo(LIFT_SCALE, SETTLE_F) // H4
        }
        actions.onDragStarted(hit.item.itemId)
    }

    /**
     * PG2 — mở phiên kéo widget tự vẽ [kind] cỡ [size] CHƯA có hàng DB (từ bảng chọn, REF `WidgetPickerController.beginNewWidgetDrag`): ảnh là
     * widget đúng cỡ ô lưới, tâm ở ngón tay [pos] (toạ độ gốc); thả qua [drop] như kéo widget có sẵn (xem trước đẩy-dồn, chặn chồng widget, lật mép,
     * trang tạm), rẽ nhánh ở chỗ ghi. Trả false nếu đang có phiên khác hoặc chưa đo được lưới.
     */
    fun startNewWidget(kind: WidgetKind, size: WidgetSize, tempId: Long, pos: Offset): Boolean {
        if (session != null) return false
        val c = cells ?: return false
        val w = (size.spanX * c.cellWidth + (size.spanX - 1) * c.gutterX).toFloat()
        val h = (size.spanY * c.cellHeight + (size.spanY - 1) * c.gutterY).toFloat()
        if (w <= 0f || h <= 0f) return false
        settlingId = null // C14
        settlingItem = null
        val rect = Rect(pos.x - w / 2f, pos.y - h / 2f, pos.x + w / 2f, pos.y + h / 2f)
        val widget = PlacedWidget(tempId, GridPosition(0, 0, size.spanX, size.spanY), kind)
        session = HomeDragSession(
            DragItem.Widget(widget), NEW_WIDGET_CONTAINER, null, GridPosition(0, 0), rect, size.spanX, size.spanY,
            pointerId = PointerId(-1L), newWidget = kind
        )
        floatSize = rect.size
        finger = pos
        scope.launch {
            floatCenter.snapTo(pos)
            floatAlpha.snapTo(1f)
            floatScale.snapTo(1f)
            floatScale.animateTo(LIFT_SCALE, SETTLE_F) // H4
        }
        actions.onDragStarted(tempId)
        return true
    }

    /**
     * LD Task 4 — bắt đầu kéo [app] từ App Library: ngón tay ở [pos] (toạ độ gốc = cửa sổ), ảnh nổi bắt đầu từ khung icon [iconRect] trong Library rồi
     * về dưới ngón tay, cỡ icon của lưới Home. Không có ô nguồn. `onDragStarted` bật `draggingItemId` + edit mode: Library tự đóng (render).
     */
    fun startExternal(app: AppItem, pos: Offset, iconRect: Rect) {
        val icon = cells?.iconSize?.toFloat() ?: iconRect.width
        val source = Rect(pos.x - icon / 2f, pos.y - icon / 2f, pos.x + icon / 2f, pos.y + icon / 2f)
        settlingId = null
        settlingItem = null
        session = HomeDragSession(
            DragItem.App(LIBRARY_DRAG_ID, app), 0L /* không thuộc lưới, dock hay thư mục nào */, null, GridPosition(0, 0), source, 1, 1, PointerId(-1L), fromLibrary = true
        )
        floatSize = source.size
        finger = pos
        scope.launch {
            floatCenter.snapTo(iconRect.center)
            floatAlpha.snapTo(1f)
            floatScale.snapTo(iconRect.width / icon)
            launch { floatCenter.animateTo(pos, SETTLE_O) }
            floatScale.animateTo(LIFT_SCALE, SETTLE_F)
        }
        actions.onDragStarted(LIBRARY_DRAG_ID)
    }

    /** LD Task 4: chỉ báo thả thật (lưới / dock); app từ Library đi đường riêng vì chưa có hàng DB. PG2: widget mới từ bảng chọn cũng vậy. */
    private fun commitDrop(s: HomeDragSession, container: Long, pageId: Long?, cell: GridPosition) {
        val app = (s.item as? DragItem.App)?.app
        val newKind = s.newWidget
        when {
            // PG2: widget mới từ bảng chọn chưa có hàng DB: VM thêm lạc quan với id tạm rồi ghi bằng AddWidgetUseCase (không qua onItemDropped).
            // Chỉ tới được nhánh lưới (widget không vào dock) nên luôn có trang; phòng hờ thì huỷ để không ghi id tạm.
            newKind != null -> actions.onNewWidgetDropped(newKind.id, s.spanX, s.spanY, s.item.itemId, pageId ?: return actions.onDragCancelled(), cell)
            s.fromLibrary && app != null -> actions.onLibraryAppDropped(app, container, pageId, cell)
            else -> actions.onItemDropped(s.item.itemId, container, pageId, cell)
        }
    }

    fun move(pos: Offset) {
        val s = session ?: return
        finger = pos
        val pt = point(s)
        scope.launch { floatCenter.snapTo(pt) }
        // JH3b D7 + FM-3: kéo app con trong bảng thư mục: trong khung = sắp xếp lại (và lật trang của bảng ở mép); rời khung = đóng thư mục,
        // kéo tiếp trên lưới.
        val panel = folderPanel
        if (panel != null && s.sourceContainer == panel.folderId) {
            if (panel.rect.contains(pt)) {
                updateFolderReorder(s, panel, pt, immediate = false)
                trackFolderEdge(panel, pt.x, repeat = false)
                return
            }
            stopFolderEdge()
            folderPreview = null
            actions.closeFolder()
        }
        updateEdge(s, pos.x, pt.x)
        if (flipping) return
        when {
            !s.isWidget && dockRect.contains(pt) -> evaluate(desiredDock(s, pt), pt)
            pagerRect.contains(pt) -> evaluate(desiredGrid(s, pt), pt)
            else -> evaluate(NO_HOVER, pt)
        }
    }

    /** Nhấc ngón kéo: thả vào đích đang trỏ, hoặc bay về (JH2 D4). */
    fun drop() {
        val s = session ?: return
        stopTimers()
        val pt = point(s)
        val panel = folderPanel
        if (panel != null && panel.rect.contains(pt)) return dropInFolder(s, panel)
        val c = cells
        val merge = shown.takeIf { it.mode == HoverMode.MERGE }?.targetId
        val page = currentPage()
        when {
            c != null && !s.isWidget && dockRect.contains(pt) -> {
                if (merge != null && isDockItem(merge)) {
                    dropMerge(s, merge, dock.firstOrNull { it.itemId == merge }?.folder, dockDisplayedRect(merge) ?: s.sourceRect)
                    return
                }
                val slot = slotFor(pt.x, s.item.itemId) ?: return cancelDrop(s) // dock đầy, item từ ngoài dock: từ chối (D2)
                if (s.sourceContainer == ContainerId.DOCK && slot == dock.indexOfFirst { it.itemId == s.item.itemId }) return cancelDrop(s)
                val remaining = dock.count { it.itemId != s.item.itemId }
                commitDrop(s, ContainerId.DOCK, null, GridPosition(slot, 0))
                settle(s, dockIconRect(slot, remaining + 1), 1f, 1f)
            }

            page != null && c != null && pagerRect.contains(pt) -> {
                if (merge != null) {
                    val target = page.items.firstOrNull { it.itemId == merge }
                    if (target != null) {
                        // Đích có thể đang bị đẩy: hạ cánh vào chỗ NHÌN THẤY của nó.
                        dropMerge(s, merge, target as? PlacedFolder, visualRect(visiblePosition(target), isWidget = false))
                        return
                    }
                }
                // AN Task 9e: đang đẩy-dồn thì ô thả là ô NEO (khe vừa mở); G2c Task 7: ô bị widget phủ → ô tự do kế sau.
                val cell = shown.takeIf { it.mode == HoverMode.PUSH && it.area == HoverArea.GRID }?.cell
                    ?: if (s.isWidget) widgetAnchor(s, pt) else c.cellAt(pt.x - pagerRect.left, pt.y - pagerRect.top)?.let { resolveInsertCell(page, it) }
                if (cell == null) return cancelDrop(s)
                if (s.isWidget && overlapsOtherWidget(page, cell, s)) return cancelDrop(s) // G2c Task 8: widget không chồng widget
                if (s.sourceContainer == ContainerId.HOME && s.sourcePageId == page.pageId && cell == GridPosition(s.sourceCell.x, s.sourceCell.y)) {
                    return cancelDrop(s)
                }
                commitDrop(s, ContainerId.HOME, page.pageId, cell)
                settle(s, visualRect(GridPosition(cell.x, cell.y, s.spanX, s.spanY), s.isWidget), 1f, 1f)
            }

            else -> cancelDrop(s)
        }
    }

    /** Thả khi đang gộp: đích là thư mục → thêm (từ chối nếu là thư mục nguồn hoặc đã đầy); đích là app → tạo thư mục. */
    private fun dropMerge(s: HomeDragSession, targetId: Long, targetFolder: PlacedFolder?, rect: Rect) {
        if (targetFolder != null) {
            // Thả lại thư mục nguồn: không đổi gì; thư mục đầy: icon bay về chỗ cũ.
            if (targetId == s.sourceContainer || targetFolder.appCount >= FolderPlanner.MAX_ITEMS) return cancelDrop(s)
            actions.onAddToFolder(s.item.itemId, targetId)
        } else {
            actions.onFolderIntent(s.item.itemId, targetId)
        }
        // PG1 A1 (QĐ 7, chủ dự án 2026-11-23): app kéo vào thư mục co vào ĐÚNG ô nhỏ của nó trong icon thư mục (giữ rõ, không mờ biến mất):
        // thư mục có sẵn thì app nối vào cuối (ô = số app hiện có), tạo thư mục mới thì app đích ô 0, app kéo ô 1. Ô ngoài 9 ô xem trước của
        // icon thư mục thì giữ cách cũ (co + mờ).
        val slot = targetFolder?.appCount ?: 1
        val mini = folderMiniRect(rect, slot)
        if (mini != null && floatSize.width > 0f) settle(s, mini, mini.width / floatSize.width, 1f)
        else settle(s, rect, MERGE_END_SCALE, 0f)
    }

    /**
     * PG1 A1: vùng ô nhỏ thứ [slot] (0..8) trong icon thư mục [iconRect], cùng hình học với `HomeFolderPlate` (đệm 12 %, khe 5 %, lưới 3 × 3);
     * null khi ô nằm ngoài 9 ô xem trước.
     */
    private fun folderMiniRect(iconRect: Rect, slot: Int): Rect? {
        if (slot !in 0 until FolderPlanner.PAGE_SIZE || iconRect.width <= 0f) return null
        val size = iconRect.width
        val pad = size * FOLDER_PLATE_PADDING_RATIO
        val gap = size * FOLDER_PLATE_GAP_RATIO
        val mini = (size - 2 * pad - 2 * gap) / FOLDER_PLATE_GRID
        val left = iconRect.left + pad + (slot % FOLDER_PLATE_GRID) * (mini + gap)
        val top = iconRect.top + pad + (slot / FOLDER_PLATE_GRID) * (mini + gap)
        return Rect(left, top, left + mini, top + mini)
    }

    /**
     * FM-3: thả trong khung bảng thư mục = nhận thả. Thứ tự đổi thì báo [HomeDragActions.onReorderFolder] (ViewModel cập nhật lạc quan và bỏ
     * `draggingItemId`), không đổi thì báo hủy kéo; cả hai ảnh bay hạ cánh vào ô (xem trước) của app. Kéo item khác vào bảng: từ chối.
     */
    private fun dropInFolder(s: HomeDragSession, panel: FolderPanelGeometry) {
        stopFolderEdge()
        val index = panel.childIds.indexOf(s.item.itemId)
        if (s.sourceContainer != panel.folderId || index < 0) {
            folderPreview = null
            return cancelDrop(s)
        }
        val rect = panel.slotIconRect(folderSlotOf(panel, index), folderPage)
        val order = folderPreview
        if (order != null && order != panel.childIds) actions.onReorderFolder(panel.folderId, order) else actions.onDragCancelled()
        folderPreview = null // ViewModel vừa cập nhật lạc quan (hoặc hủy): bỏ xem trước, dữ liệu đã đúng thứ tự
        settle(s, rect, 1f, 1f)
    }

    /** CANCEL của hệ thống (G2c13: huỷ, không bao giờ thả). */
    fun cancel() {
        val s = session ?: return
        stopTimers()
        stopFolderEdge()
        folderPreview = null
        cancelDrop(s)
    }

    private fun cancelDrop(s: HomeDragSession) {
        actions.onDragCancelled()
        if (s.newWidget != null) {
            // PG2 (Q3, như XML returnRect = null): widget mới chưa có chỗ cũ để bay về: ảnh co + mờ tại chỗ rồi mất, không thêm gì.
            val pt = point(s)
            settle(s, Rect(pt.x - floatSize.width / 2f, pt.y - floatSize.height / 2f, pt.x + floatSize.width / 2f, pt.y + floatSize.height / 2f), MERGE_END_SCALE, 0f)
            return
        }
        if (s.fromLibrary) { // LD Task 4: không có ô nguồn: mờ dần và co nhẹ tại chỗ
            val c = floatCenter.value
            settle(s, Rect(c, c), MERGE_END_SCALE, 0f)
            return
        }
        // Bay về ô nguồn nếu nó còn ở trang đang xem (View: returnRect null khi trang nguồn lệch > 1/4 màn); dock bay về ô dock.
        val panel = folderPanel
        if (panel != null && panel.folderId == s.sourceContainer) {
            val index = panel.childIds.indexOf(s.item.itemId)
            val slot = if (index >= 0) folderSlotOf(panel, index) else -1
            if (slot >= 0 && slot / FolderPlanner.PAGE_SIZE == folderPage) settle(s, panel.slotIconRect(slot, folderPage), 1f, 1f) else finish()
            return
        }
        val back = s.sourceContainer == ContainerId.DOCK || currentPage()?.pageId == s.sourcePageId
        if (back) settle(s, s.sourceRect, 1f, 1f) else finish()
    }

    private fun settle(s: HomeDragSession, rect: Rect, endScale: Float, endAlpha: Float) {
        settlingId = s.item.itemId
        settlingItem = s.item
        session = null
        clearHover()
        scope.launch {
            launch { floatScale.animateTo(endScale, SETTLE_F) }
            launch { floatAlpha.animateTo(endAlpha, SETTLE_F) }
            floatCenter.animateTo(rect.center, SETTLE_O)
            // Phiên kéo mới có thể đã bắt đầu trong lúc bay (start() đặt lại ảnh nổi): chỉ dọn phần "đang bay" của chính item này.
            if (session == null && settlingId == s.item.itemId) {
                settlingId = null
                settlingItem = null
            }
        }
    }

    private fun finish() {
        session = null
        settlingId = null
        settlingItem = null
        clearHover()
    }

    // ---------------------------------------------------------------- hover: máy trạng thái chung (IconGridView / DockView)

    private enum class HoverMode { NONE, MERGE, PUSH }
    private enum class HoverArea { GRID, DOCK }

    /** [cell] = ô neo của PUSH trên lưới; [slot] = slot neo của PUSH trong dock; [targetId] = đích gộp; [pageId] = trang của hover lưới. */
    private data class Hover(
        val mode: HoverMode,
        val area: HoverArea? = null,
        val cell: GridPosition? = null,
        val slot: Int? = null,
        val targetId: Long? = null,
        val pageId: Long? = null
    )

    private var shown = NO_HOVER
    private var pending = NO_HOVER
    private var pendingJob: Job? = null
    private var pushStart = Offset.Zero

    private fun evaluate(desired: Hover, pt: Offset) {
        if (desired == shown) {
            pendingJob?.cancel()
            pending = shown
            return
        }
        if (desired.mode != HoverMode.NONE && desired == pending) {
            // Đang chờ đúng thứ này. Đẩy-dồn chỉ bắt đầu khi ngón tay ĐÃ DỪNG: dời quá PUSH_SETTLE_DP kể từ lúc bắt đầu đếm thì đếm lại.
            if (desired.mode == HoverMode.PUSH && (pt - pushStart).getDistance() > PUSH_SETTLE_DP * density) {
                pushStart = pt
                arm(desired, PUSH_ARM_MS)
            }
            return
        }
        pendingJob?.cancel()
        pending = desired
        // Thứ đang hiện không còn đúng: bỏ NGAY. Ngoại lệ (AN Task 9e): đang đẩy-dồn mà ngón tay tới giữa một icon ĐANG BỊ ĐẨY thì giữ nguyên
        // hàng (icon đích đứng yên ở chỗ mới, không bị kéo ngược khỏi ngón tay); hàng về khi thứ khác được chọn.
        val keepRow = shown.mode == HoverMode.PUSH && desired.mode == HoverMode.MERGE && desired.targetId?.let { isDisplaced(it) } == true
        if (keepRow) {
            mergeTargetId = null
            shown = NO_HOVER
        } else {
            clearShown()
        }
        if (desired.mode != HoverMode.NONE) {
            if (desired.mode == HoverMode.PUSH) pushStart = pt
            arm(desired, if (desired.mode == HoverMode.MERGE) HOVER_ARM_MS else PUSH_ARM_MS)
        }
    }

    private fun arm(state: Hover, delayMs: Long) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(delayMs)
            applyHover(state)
        }
    }

    private fun applyHover(state: Hover) {
        val s = session ?: return
        shown = state
        pending = state
        when (state.mode) {
            HoverMode.MERGE -> {
                // Thả bây giờ là gộp, không phải chèn (đích đang bị đẩy: giữ hàng, xem evaluate).
                if (state.targetId?.let { isDisplaced(it) } != true) clearPreviews()
                mergeTargetId = state.targetId
            }

            HoverMode.PUSH -> {
                clearPreviews()
                mergeTargetId = null
                when (state.area) {
                    HoverArea.GRID -> {
                        val page = pages.firstOrNull { it.pageId == state.pageId } ?: return
                        state.cell?.let { applyGridPreview(s, page, it) }
                    }

                    HoverArea.DOCK -> state.slot?.let { slot ->
                        dockPreview = dock.map { it.itemId }.filter { it != s.item.itemId }.toMutableList().apply { add(slot, DOCK_GAP) }
                    }

                    null -> Unit
                }
            }

            HoverMode.NONE -> Unit
        }
    }

    private fun clearShown() {
        mergeTargetId = null
        clearPreviews()
        shown = NO_HOVER
    }

    private fun clearPreviews() {
        preview = emptyMap()
        previewHidden = emptySet()
        previewPageId = null
        dockPreview = null
    }

    private fun clearHover() {
        pendingJob?.cancel()
        pending = NO_HOVER
        clearShown()
    }

    /** Item [itemId] đang bị xem trước đẩy-dồn dịch khỏi chỗ (nhìn thấy ở ô / slot khác chỗ của nó). */
    private fun isDisplaced(itemId: Long): Boolean {
        preview[itemId]?.let { p ->
            val placed = pages.firstOrNull { it.pageId == previewPageId }?.items?.firstOrNull { it.itemId == itemId }
            return placed != null && (placed.position.x != p.x || placed.position.y != p.y)
        }
        val order = dockPreview ?: return false
        val now = order.indexOf(itemId)
        return now >= 0 && now != dock.indexOfFirst { it.itemId == itemId }
    }

    // ---------------------------------------------------------------- grid hover (IconGridView.desiredHover)

    private fun desiredGrid(s: HomeDragSession, pt: Offset): Hover {
        val c = cells ?: return NO_HOVER
        val page = currentPage() ?: return NO_HOVER
        // AN Task 9e: PUSH đang hiện thì Ở LẠI chừng nào điểm kéo còn trong ô neo (+ 8 dp), kể cả khi vào giữa ô.
        val anchor = shown.takeIf { it.mode == HoverMode.PUSH && it.area == HoverArea.GRID && it.pageId == page.pageId }?.cell
        if (anchor != null && cellRect(anchor).inflate(PUSH_ANCHOR_HYST_DP * density).contains(pt)) return shown
        val targetCell = targetCell(s, pt)
        if (s.isWidget) {
            // G2c Task 8: widget không bao giờ gộp. Chỉ đẩy-dồn khi hình chữ nhật của nó phủ ít nhất một icon và KHÔNG chồng widget khác.
            val at = targetCell ?: return NO_HOVER
            if (overlapsOtherWidget(page, at, s)) return NO_HOVER
            val coversIcon = page.items.any {
                it !is PlacedWidgetBase && it.itemId != s.item.itemId &&
                    it.position.x in at.x until at.x + s.spanX && it.position.y in at.y until at.y + s.spanY
            }
            return if (coversIcon) Hover(HoverMode.PUSH, HoverArea.GRID, cell = at, pageId = page.pageId) else NO_HOVER
        }
        // Gộp: icon gộp được mà điểm kéo ở VÙNG GIỮA của nó, theo vị trí NHÌN THẤY (icon bị đẩy vẫn là đích tại chỗ mới). Đích: app 1x1 hoặc thư
        // mục (kể cả CHÍNH thư mục nguồn: thả lại = hủy kéo ra). Thư mục đang kéo không gộp.
        if (s.item !is DragItem.Folder && !s.fromLibrary) { // LD Task 4: app từ Library không gộp thư mục (ngoài phạm vi)
            var best: PlacedItem? = null
            var bestDist = Float.MAX_VALUE
            page.items.forEach { placed ->
                if (placed.itemId == s.item.itemId || placed.itemId in previewHidden) return@forEach
                val mergeable = (placed is PlacedApp && placed.position.spanX == 1 && placed.position.spanY == 1) || placed is PlacedFolder
                if (!mergeable) return@forEach
                val center = iconCenter(visibleCell(placed))
                val was = (shown.mode == HoverMode.MERGE && shown.targetId == placed.itemId) ||
                    (pending.mode == HoverMode.MERGE && pending.targetId == placed.itemId)
                if (FolderPlanner.isInCenterZone(pt.x - center.x, pt.y - center.y, c.iconSize, was)) {
                    val d = hypot(pt.x - center.x, pt.y - center.y)
                    if (d < bestDist) {
                        bestDist = d
                        best = placed
                    }
                }
            }
            best?.let { return Hover(HoverMode.MERGE, HoverArea.GRID, targetId = it.itemId, pageId = page.pageId) }
        }
        val cell = targetCell ?: return NO_HOVER
        // G2c Task 7: ô bị widget phủ được đổi thành ô tự do kế sau; chỉ đẩy-dồn khi ô đã ánh xạ đang có icon (không phải widget).
        val resolved = resolveInsertCell(page, cell)
        page.items.firstOrNull {
            it !is PlacedWidgetBase && it.position.x == resolved.x && it.position.y == resolved.y && it.itemId != s.item.itemId
        } ?: return NO_HOVER // ô trống: không có gì để đẩy
        return Hover(HoverMode.PUSH, HoverArea.GRID, cell = resolved, pageId = page.pageId)
    }

    private fun applyGridPreview(s: HomeDragSession, page: HomePage, cell: GridPosition) {
        val c = cells ?: return
        val slots = page.items.map { GridInsertPlanner.Slot(it.itemId, it.position.x, it.position.y, it.position.spanX, it.position.spanY) }
        val plan = GridInsertPlanner.plan(
            listOf(GridInsertPlanner.PageSlots(page.pageId, slots)), s.item.itemId, 0, cell.x, cell.y, c.columns, c.rows, s.spanX, s.spanY
        )
        if (plan.rejected) return
        val byId = page.items.associateBy { it.itemId }
        preview = plan.placements
            .filter { it.pageIndex == 0 && it.itemId != s.item.itemId }
            .associate { p -> p.itemId to GridPosition(p.x, p.y, byId[p.itemId]?.position?.spanX ?: 1, byId[p.itemId]?.position?.spanY ?: 1) }
        previewHidden = plan.placements.filter { it.pageIndex > 0 }.map { it.itemId }.toSet()
        previewPageId = page.pageId
    }

    /** G2c Task 7 (IconGridView.resolveInsertCell): ô bị widget phủ → ô tự do kế sau theo thứ tự đọc; cùng hàm với DB và ViewModel. */
    private fun resolveInsertCell(page: HomePage, cell: GridPosition): GridPosition {
        val c = cells ?: return cell
        val widgets = page.items.filter { it is PlacedWidgetBase }
            .map { GridInsertPlanner.Slot(it.itemId, it.position.x, it.position.y, it.position.spanX, it.position.spanY) }
        if (widgets.isEmpty()) return cell
        val free = PageGeometry.freeIndices(widgets, c.columns, c.rows)
        val k = PageGeometry.resolveInsertSeq(free, cell.y * c.columns + cell.x)
        if (k < 0) return cell
        return GridPosition(free[k] % c.columns, free[k] / c.columns)
    }

    private fun overlapsOtherWidget(page: HomePage, pos: GridPosition, s: HomeDragSession): Boolean =
        page.items.any {
            it is PlacedWidgetBase && it.itemId != s.item.itemId &&
                pos.x < it.position.x + it.position.spanX && it.position.x < pos.x + s.spanX &&
                pos.y < it.position.y + it.position.spanY && it.position.y < pos.y + s.spanY
        }

    // ---------------------------------------------------------------- dock hover (DockView P2-6)

    private fun isDockItem(itemId: Long?): Boolean = itemId != null && dock.any { it.itemId == itemId }

    /** Vùng icon ĐANG HIỆN của ô dock [itemId] (theo thứ tự xem trước nếu có, như `HomeDock`); null nếu không hiện. */
    private fun dockDisplayedRect(itemId: Long): Rect? {
        val order = dockPreview ?: dock.map { it.itemId }
        val index = order.indexOf(itemId)
        return if (index < 0) null else dockIconRect(index, order.size)
    }

    /**
     * Điểm kéo trong dock: PUSH đang hiện mà vẫn ở slot neo → ở lại; app vào vùng giữa icon NHÌN THẤY của ô dock khác → gộp; còn lại → đẩy-dồn
     * tại slot dưới điểm kéo (dock đầy và item từ ngoài: không có gì; kéo trong dock về đúng slot cũ: không có gì).
     */
    private fun desiredDock(s: HomeDragSession, pt: Offset): Hover {
        val c = cells ?: return NO_HOVER
        val slot = slotFor(pt.x, s.item.itemId)
        if (shown.mode == HoverMode.PUSH && shown.area == HoverArea.DOCK && slot == shown.slot) return shown
        if (s.item is DragItem.App && !s.fromLibrary) { // LD Task 4: không gộp
            var best: Long? = null
            var bestDist = Float.MAX_VALUE
            dock.forEach { d ->
                if (d.itemId == s.item.itemId) return@forEach
                val rect = dockDisplayedRect(d.itemId) ?: return@forEach
                val was = (shown.mode == HoverMode.MERGE && shown.targetId == d.itemId) ||
                    (pending.mode == HoverMode.MERGE && pending.targetId == d.itemId)
                if (FolderPlanner.isInCenterZone(pt.x - rect.center.x, pt.y - rect.center.y, c.iconSize, was)) {
                    val dist = hypot(pt.x - rect.center.x, pt.y - rect.center.y)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = d.itemId
                    }
                }
            }
            best?.let { return Hover(HoverMode.MERGE, HoverArea.DOCK, targetId = it) }
        }
        if (slot == null) return NO_HOVER
        if (s.sourceContainer == ContainerId.DOCK && slot == dock.indexOfFirst { it.itemId == s.item.itemId }) return NO_HOVER
        return Hover(HoverMode.PUSH, HoverArea.DOCK, slot = slot)
    }

    /**
     * DockView.slotFor: slot (0..còn lại) dưới [x]; ranh giới giữa hai slot = giữa khoảng trống. null = dock đầy và item từ ngoài dock.
     * 2026-11-23 (chuẩn iOS, đồng bộ với lưới): pitch cố định icon+X (X = cells.iconSpacingX của LƯỚI, không tự chia theo [dockRect].width
     * nữa), khối [m] icon được CĂN GIỮA — y hệt [HomeDock].
     */
    private fun slotFor(x: Float, draggingId: Long): Int? {
        val c = cells ?: return null
        val remaining = dock.count { it.itemId != draggingId }
        if (remaining >= GridMetrics.DOCK_SLOTS) return null
        val m = remaining + 1
        val pitch = c.iconSize + c.iconSpacingX
        if (pitch <= 0) return null
        val groupWidth = m * c.iconSize + (m - 1).coerceAtLeast(0) * c.iconSpacingX
        val startX = (dockRect.width - groupWidth) / 2f
        val index = floor((x - dockRect.left - startX) / pitch).toInt()
        return index.coerceIn(0, m - 1)
    }

    /** Vùng icon (toạ độ gốc) của ô dock [itemId]: điểm xuất phát của bảng thư mục trong dock. */
    fun dockIconRectOf(itemId: Long): Rect? {
        val index = dock.indexOfFirst { it.itemId == itemId }
        return if (index < 0) null else dockIconRect(index, dock.size)
    }

    /**
     * Ô thứ [index] trong dock có [count] ô, cao hết dock. 2026-11-23 (chuẩn iOS, đồng bộ với lưới): pitch cố định icon+X (X =
     * cells.iconSpacingX của LƯỚI), khối [count] icon CĂN GIỮA trong dock — y hệt [HomeDock].
     */
    fun dockSlotRects(count: Int): List<Rect> {
        val c = cells ?: return emptyList()
        if (count <= 0) return emptyList()
        val pitch = c.iconSize + c.iconSpacingX
        val groupWidth = count * c.iconSize + (count - 1) * c.iconSpacingX
        val startX = dockRect.left + (dockRect.width - groupWidth) / 2f
        return List(count) { i ->
            val left = startX + i * pitch - (c.cellWidth - c.iconSize) / 2f
            Rect(left, dockRect.top, left + c.cellWidth, dockRect.bottom)
        }
    }

    private fun dockIconRect(index: Int, count: Int): Rect {
        val c = cells ?: return Rect.Zero
        val slot = dockSlotRects(count).getOrNull(index) ?: return Rect.Zero
        val left = slot.center.x - c.iconSize / 2f
        val top = slot.center.y - c.iconSize / 2f
        return Rect(left, top, left + c.iconSize, top + c.iconSize)
    }

    // ---------------------------------------------------------------- folder panel (FolderPanelView FM-2 / FM-3)

    private var armedSlot = -1
    private var armJob: Job? = null
    private var folderEdgeDir = 0
    private var folderEdgeJob: Job? = null

    /** Ô đang hiện của app thứ [index] (theo xem trước nếu có). */
    fun folderSlotOf(panel: FolderPanelGeometry, index: Int): Int {
        val order = folderPreview ?: return index
        val at = order.indexOf(panel.childIds.getOrNull(index) ?: return index)
        return if (at < 0) index else at
    }

    /** FM-2: bảng thư mục vừa mở (hoặc đổi thư mục): luôn vào trang 1 (Q3), bỏ xem trước. */
    fun resetFolder() {
        folderPage = 0
        folderPreview = null
        stopFolderEdge()
    }

    /**
     * FM-3: dời các ô để chừa chỗ cho app đang kéo ở ô dưới điểm kéo (trang đích hiện tại). Điểm kéo phải ở yên trên MỘT ô [REORDER_ARM_MS]
     * rồi mới dời (kéo lướt qua nhiều ô không làm cả lưới nhảy theo); [immediate] bỏ chờ (ngay sau khi lật trang).
     */
    private fun updateFolderReorder(s: HomeDragSession, panel: FolderPanelGeometry, pt: Offset, immediate: Boolean) {
        val index = panel.childIds.indexOf(s.item.itemId)
        if (index < 0) return
        val slot = panel.slotAt(pt, folderPage) ?: return
        if (immediate || folderSlotOf(panel, index) == slot) {
            cancelReorderArm()
            previewMove(panel, index, slot) // đã ở đúng ô thì không làm gì
            return
        }
        if (slot == armedSlot) return // đang chờ đúng ô này
        armJob?.cancel()
        armedSlot = slot
        armJob = scope.launch {
            delay(REORDER_ARM_MS)
            armedSlot = -1
            val p = folderPanel ?: return@launch
            val i = p.childIds.indexOf(s.item.itemId)
            if (session === s && i >= 0) previewMove(p, i, slot)
        }
    }

    /** Xem trước: app [fromIndex] nằm ở ô [toSlot], các app khác dồn theo thứ tự. */
    private fun previewMove(panel: FolderPanelGeometry, fromIndex: Int, toSlot: Int) {
        if (fromIndex !in panel.childIds.indices || folderSlotOf(panel, fromIndex) == toSlot) return
        val order = panel.childIds.indices.filter { it != fromIndex }.toMutableList()
        order.add(toSlot.coerceIn(0, order.size), fromIndex)
        folderPreview = order.map { panel.childIds[it] }
    }

    private fun cancelReorderArm() {
        armJob?.cancel()
        armedSlot = -1
    }

    /**
     * FM-3: giữ điểm kéo trong dải [FOLDER_EDGE_BAND_DP] sát mép trái/phải khung quá [EDGE_HOLD_MS] thì lật sang trang bên cạnh (nếu còn trang),
     * còn giữ thì lật tiếp. [repeat] = gọi lại ngay sau một lần lật (hẹn lại dù hướng không đổi).
     */
    private fun trackFolderEdge(panel: FolderPanelGeometry, x: Float, repeat: Boolean) {
        val band = FOLDER_EDGE_BAND_DP * density
        val dir = when {
            x < panel.rect.left + band && folderPage > 0 -> -1
            x > panel.rect.right - band && folderPage < panel.pageCount - 1 -> 1
            else -> 0
        }
        if (dir == folderEdgeDir && !repeat) return
        folderEdgeJob?.cancel()
        folderEdgeDir = dir
        if (dir == 0) return
        folderEdgeJob = scope.launch {
            delay(EDGE_HOLD_MS)
            val s = session ?: return@launch
            val p = folderPanel ?: return@launch
            folderPage = (folderPage + dir).coerceIn(0, p.pageCount - 1)
            val pt = point(s)
            updateFolderReorder(s, p, pt, immediate = true) // vừa lật trang: dời ngay, không chờ
            folderEdgeDir = 0
            trackFolderEdge(p, pt.x, repeat = true) // còn giữ ở mép và còn trang: lật tiếp
        }
    }

    private fun stopFolderEdge() {
        folderEdgeJob?.cancel()
        folderEdgeDir = 0
        cancelReorderArm() // mọi nơi dừng theo dõi mép (rời khung, thả, đóng) cũng bỏ lượt dời đang chờ
    }

    // ---------------------------------------------------------------- edge flip (DragHelper + HomePagerController.onDragEdge)

    /** [x] ngón tay, [px] điểm ảnh. Widget (P2-7): lật khi tâm ảnh ra ngoài mép trang HOẶC ngón tay vào dải mép như icon. */
    private fun updateEdge(s: HomeDragSession, x: Float, px: Float) {
        if (folderPanel != null) { // FM-3: kéo trong bảng thư mục: bảng tự lật trang của nó, Home phía sau không lật
            edgeJob?.cancel()
            edgeDir = 0
            return
        }
        val zone = DRAG_EDGE_ZONE_DP * density
        val dir = when {
            x < zone || (s.isWidget && px <= pagerRect.left) -> -1
            x > rootWidth - zone || (s.isWidget && px >= pagerRect.right) -> 1
            else -> 0
        }
        if (dir == edgeDir) return
        edgeDir = dir
        edgeJob?.cancel()
        if (dir == 0) return
        edgeJob = scope.launch {
            while (true) {
                delay(EDGE_HOLD_MS)
                flip(dir)
            }
        }
    }

    fun onPagerIdle(now: Long) {
        lastIdleAt = now
    }

    private suspend fun flip(dir: Int) {
        if (flipping || pagerState.isScrollInProgress || System.currentTimeMillis() - lastIdleAt < EDGE_FLIP_REST_MS) return
        val current = pagerState.currentPage
        if (dir > 0 && current >= realPageCount - 1 && pages.size == realPageCount) {
            // Trang thật cuối: xin trang tạm; HomeScreen lật sang khi danh sách trang có nó (ghostFlipPending).
            if (actions.onDragEdgeBeyondLastPage()) ghostFlipPending = true
            return
        }
        val target = current + dir
        if (target !in 0 until pages.size) return
        // Animation lật chạy trong job riêng của scope: đổi hướng / rời mép / thả giữa lúc lật chỉ huỷ edgeJob (vòng chờ),
        // không huỷ animateScrollToPage — nếu huỷ, pager dừng ở offset lẻ và trang bị lệch.
        scope.launch { flipTo(target) }.join()
    }

    var ghostFlipPending = false

    suspend fun flipTo(target: Int) {
        flipping = true
        clearHover()
        try {
            pagerState.animateScrollToPage(target, animationSpec = tween(FLIP_MS, easing = EaseOutQuint))
        } finally {
            flipping = false
            lastIdleAt = System.currentTimeMillis()
        }
        if (session != null) move(finger) // DragHelper.refresh(): ngón đứng yên, đích mới
    }

    private fun stopTimers() {
        edgeJob?.cancel()
        edgeDir = 0
        pendingJob?.cancel()
    }

    // ---------------------------------------------------------------- geometry

    private fun currentPage(): HomePage? = pages.getOrNull(pagerState.currentPage)

    private fun visiblePosition(item: PlacedItem): GridPosition = preview[item.itemId] ?: item.position
    private fun visibleCell(item: PlacedItem): GridPosition = visiblePosition(item).let { GridPosition(it.x, it.y) }

    private fun covers(p: GridPosition, cell: GridPosition): Boolean =
        cell.x in p.x until p.x + p.spanX && cell.y in p.y until p.y + p.spanY

    /** Ô đích: icon theo ô dưới điểm kéo; widget theo [widgetAnchor]. */
    private fun targetCell(s: HomeDragSession, pt: Offset): GridPosition? {
        val c = cells ?: return null
        if (!s.isWidget) return c.cellAt(pt.x - pagerRect.left, pt.y - pagerRect.top)
        return widgetAnchor(s, pt)
    }

    /** G2c Task 8: neo của widget = ô gần góc trên-trái của ảnh kéo nhất (IGV 824-839), kẹp vào lưới. [pt] là TÂM ẢNH (P2-7). */
    private fun widgetAnchor(s: HomeDragSession, pt: Offset): GridPosition? {
        val c = cells ?: return null
        val left = pt.x - floatSize.width / 2f - pagerRect.left
        val top = pt.y - floatSize.height / 2f - pagerRect.top
        val ax = floor((left - c.paddingLeft) / (c.cellWidth + c.gutterX) + 0.5f).toInt().coerceIn(0, max(0, c.columns - s.spanX))
        val ay = floor((top - c.paddingTop) / (c.cellHeight + c.gutterY) + 0.5f).toInt().coerceIn(0, max(0, c.rows - s.spanY))
        return GridPosition(ax, ay)
    }

    fun cellRect(p: GridPosition): Rect {
        val c = cells ?: return Rect.Zero
        val left = pagerRect.left + c.paddingLeft + p.x * (c.cellWidth + c.gutterX)
        val top = pagerRect.top + c.paddingTop + p.y * (c.cellHeight + c.gutterY)
        return Rect(
            left, top,
            left + p.spanX * c.cellWidth + (p.spanX - 1) * c.gutterX,
            top + p.spanY * c.cellHeight + (p.spanY - 1) * c.gutterY
        )
    }

    /** Vùng nhìn thấy của item: widget = cả khung; app/thư mục = icon căn giữa ở đỉnh ô (P2-6: hạ cánh theo hình vuông icon). */
    private fun visualRect(p: GridPosition, isWidget: Boolean): Rect {
        val c = cells ?: return Rect.Zero
        val cell = cellRect(p)
        if (isWidget) return cell
        val left = cell.center.x - c.iconSize / 2f
        return Rect(left, cell.top, left + c.iconSize, cell.top + c.iconSize)
    }

    private fun iconCenter(p: GridPosition): Offset = visualRect(p, isWidget = false).center

    /** Vùng icon (toạ độ gốc) của ô [p] trên trang đang xem: điểm xuất phát của bảng thư mục (JH3b D6). */
    fun iconRectOf(p: GridPosition): Rect = visualRect(p, isWidget = false)

    companion object {
        const val DOCK_GAP = Long.MIN_VALUE + 1
        private val NO_HOVER = Hover(HoverMode.NONE)

        // Cùng số với IconGridView / DockView / DragHelper / HomeTouchRouter / HomePagerController / SpringProgress (plan D3).
        private const val HOVER_ARM_MS = 100L
        private const val PUSH_ARM_MS = 500L
        private const val PUSH_SETTLE_DP = 8f
        private const val PUSH_ANCHOR_HYST_DP = 8f
        private const val DRAG_EDGE_ZONE_DP = 24f
        private const val EDGE_HOLD_MS = 500L
        private const val EDGE_FLIP_REST_MS = 500L
        private const val FLIP_MS = 450
        private const val MERGE_END_SCALE = 0.4f

        /** PG2: "container nguồn" của widget mới kéo từ bảng chọn (không phải HOME/DOCK/TODAY, không phải id thư mục). */
        private const val NEW_WIDGET_CONTAINER = Long.MIN_VALUE + 2

        // PG1 A1: cùng số với HomeGridPage.kt (FOLDER_PADDING_RATIO, FOLDER_GAP_RATIO, FOLDER_GRID) — hình icon thư mục 3 × 3.
        private const val FOLDER_PLATE_PADDING_RATIO = 0.12f
        private const val FOLDER_PLATE_GAP_RATIO = 0.05f
        private const val FOLDER_PLATE_GRID = 3
        private const val LIFT_SCALE = 1.1f
        private val SETTLE_F = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)
        private val SETTLE_O = spring<Offset>(dampingRatio = 0.8f, stiffness = 600f)

        // FM-3 (FolderPanelView): dải mép khung để lật trang khi kéo app; điểm kéo ở yên trên một ô chừng này ms thì các icon mới dời.
        private const val FOLDER_EDGE_BAND_DP = 36f
        private const val REORDER_ARM_MS = 250L
    }
}

/**
 * JH3b + FM-2 — hình học bảng thư mục đang mở (toạ độ gốc): khung bảng [rect], góc trên-trái lưới [gridOrigin] (trang 0), bề ngang một trang
 * [pageWidth] (= cạnh lưới), [cells] của lưới 3 × 3 của bảng, và app con theo thứ tự dữ liệu ([childIds] / [apps]).
 */
data class FolderPanelGeometry(
    val folderId: Long,
    val rect: Rect,
    val gridOrigin: Offset,
    val pageWidth: Float,
    val cells: GridMetrics.Cells,
    val childIds: List<Long>,
    val apps: List<AppItem>
) {
    val pageCount: Int get() = FolderPlanner.pageCount(childIds.size)

    /** Khung icon (hình vuông, đầu ô) của ô [slot] khi bảng đang ở trang [page]. */
    fun slotIconRect(slot: Int, page: Int): Rect {
        val i = slot % FolderPlanner.PAGE_SIZE
        val p = slot / FolderPlanner.PAGE_SIZE
        val left = gridOrigin.x + (p - page) * pageWidth + cells.paddingLeft + (i % COLUMNS) * (cells.cellWidth + cells.gutterX) +
            (cells.cellWidth - cells.iconSize) / 2f
        val top = gridOrigin.y + cells.paddingTop + (i / COLUMNS) * (cells.cellHeight + cells.gutterY)
        return Rect(left, top, left + cells.iconSize, top + cells.iconSize)
    }

    /** Ô dưới điểm [pt] ở trang [page]; tối đa ô cuối cùng có app (FolderPanelView.slotAt). */
    fun slotAt(pt: Offset, page: Int): Int? {
        if (childIds.isEmpty()) return null
        val lx = pt.x - gridOrigin.x
        val ly = pt.y - gridOrigin.y
        val col = ((lx - cells.paddingLeft) / (cells.cellWidth + cells.gutterX)).toInt().coerceIn(0, COLUMNS - 1)
        val row = ((ly - cells.paddingTop) / (cells.cellHeight + cells.gutterY)).toInt().coerceIn(0, COLUMNS - 1)
        return (page * FolderPlanner.PAGE_SIZE + row * COLUMNS + col).coerceAtMost(childIds.size - 1)
    }

    companion object {
        const val COLUMNS = 3
    }
}
