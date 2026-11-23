package gambi.global.os26launcher.core.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import gambi.global.os26launcher.domain.model.ScreenRect

/**
 * M7 — coordinator Kotlin thuần (`object`, không Hilt vì không có state cần inject) theo dõi 1 phiên
 * kéo tay đang diễn ra. Không tự vẽ gì — chuyển tiếp xuống [DragVisual] (DragLayer thật) để vẽ icon
 * đang kéo, và báo `onDragEnter`/`onDragMove`/`onDragExit`/`onDrop` cho các [DragDropTarget] đang hover.
 *
 * Ruling (Task 3): `core/ui` không nên import ngược vào `ui/home/view.DragLayer` (ngược hướng phụ thuộc
 * thông thường core → feature). Vì vậy [DragHelper] chỉ biết tới [DragVisual] — interface tối giản mà
 * `DragLayer` implement — chứ không phải class `DragLayer` cụ thể. `HomeActivity` gọi
 * [attachDragVisual]/[detachDragVisual] ở `onCreate`/`onDestroy`.
 */
object DragHelper {

    /** Trừu tượng hoá `DragLayer` (vẽ icon đang kéo) để [DragHelper] không phải biết class View cụ thể. */
    interface DragVisual {
        /** [iconOnly]: ảnh cùng cỡ [bitmap] nhưng không nhãn (P2-6), dùng khi [setLabelHidden]; null = không có bản không nhãn. */
        fun startDrag(bitmap: Bitmap, startX: Float, startY: Float, iconOnly: Bitmap? = null)
        fun moveTo(x: Float, y: Float)

        /** P2-6: ẩn/hiện nhãn của ảnh kéo (đổi sang/về bản [startDrag] `iconOnly`). */
        fun setLabelHidden(hidden: Boolean)
        fun endDrag()

        /**
         * AN Task 9: thay [endDrag] khi thả được nhận: ảnh bay trượt từ chỗ ngón tay tới tâm ([centerX], [centerY]) (toạ độ màn hình) bằng
         * spring, đồng thời co về [endScale] và mờ về [endAlpha], rồi tự tắt và gọi [onEnd]. [startDrag]/[endDrag] gọi giữa chừng thì huỷ
         * hạ cánh nhưng VẪN gọi [onEnd] đúng một lần (C14; [onEnd] của [DragHelper] là `finishSettle()`, idempotent).
         */
        fun settleTo(
            centerX: Float,
            centerY: Float,
            endScale: Float,
            endAlpha: Float,
            onEnd: () -> Unit
        )
    }

    data class DragSession(
        val itemId: Long,
        val sourceContainer: Long,
        val sourcePageId: Long?,
        /** Nơi bắt đầu kéo (chưa nơi nào đọc). G2c Task 13: null cho widget kéo từ bảng chọn (chưa có nguồn trong lưới). */
        val source: DragDropTarget?,
        /** G2a A1b: item đang kéo là thư mục (không gộp được vào thư mục khác, không thả được vào dock). */
        val isFolder: Boolean = false,
        /** G2c Task 8: item đang kéo là widget (không gộp thư mục, không vào dock); [spanX] x [spanY] là cỡ ô của nó (icon, thư mục: 1 x 1). */
        val isWidget: Boolean = false,
        val spanX: Int = 1,
        val spanY: Int = 1,
        /**
         * G2c Task 13: khác null = widget CHƯA CÓ hàng DB, kéo ra từ bảng chọn; giá trị là `WidgetKind.id`. [itemId] khi đó là id TẠM âm, chỉ dùng
         * để ẩn/nhận biết trong phiên và làm id lạc quan; hàng thật chỉ được tạo lúc thả (`LayoutReconciler.addWidgetAt`).
         */
        val newWidgetKind: String? = null,
        /**
         * G2c Task 13: tọa độ màn hình của chỗ cũ của item (null nếu không nhìn thấy). Thả không được nhận (hoặc thả về đúng chỗ cũ) thì
         * ảnh kéo bay về đó bằng đúng spring hạ cánh của AN thay vì biến mất tức thì.
         */
        val returnRect: (() -> ScreenRect?)? = null,
        /**
         * P2-7: tâm ảnh kéo trừ ngón tay lúc bắt đầu (px màn hình). Khác 0 = ảnh giữ nguyên chỗ so với ngón tay (widget nhấn giữ ở góc vẫn
         * nằm dưới ngón ở góc đó). Ảnh được vẽ và đích được báo tại [DragHelper] "điểm ảnh" = ngón tay + độ lệch. 0 = ảnh căn giữa ngón tay.
         */
        val grabOffsetX: Float = 0f,
        val grabOffsetY: Float = 0f,
        /**
         * Gọi MỘT lần khi phiên kết thúc (thả/huỷ), [accepted] = target có nhận thả không. Do `HomeActivity` gọi (Task 7:
         * phiên kéo do Activity nhận chạm, không phụ thuộc view nguồn còn sống). Phải chỉ dùng dữ liệu/callback, không
         * giả định view nguồn còn gắn trong cây — ViewPager2 có thể đã tháo nó khi lật trang.
         */
        val onFinished: (accepted: Boolean) -> Unit
    )

    /** M7 Task 7 — hướng ngón tay đang giữ sát mép màn hình. */
    enum class EdgeDirection { LEFT, RIGHT }

    var currentSession: DragSession? = null
        private set

    /**
     * AN Task 9: item vừa thả được nhận, ảnh bay của nó đang hạ cánh. Ô của item này phải giữ `alpha = 0` cho tới khi hạ cánh xong.
     * Nằm ở object này (KHÔNG ở view): view của lưới bị tái dùng/thay bất cứ lúc nào (bài học M7); view chỉ ĐỌC giá trị này khi bind.
     */
    var settlingItemId: Long? = null
        private set

    private var dragVisual: DragVisual? = null

    // M7 Task 7: nhớ điểm cuối để [refresh] tính lại target dưới ngón tay khi đứng yên mà trang vừa lật xong
    // (không có ACTION_MOVE nào để tự cập nhật).
    private var lastX = 0f
    private var lastY = 0f

    // Theo dõi mép (E1): giữ trong vùng mép > [EDGE_HOLD_MS] thì báo [edgeListener], vẫn giữ thì lặp lại mỗi
    // [EDGE_HOLD_MS]. HomeActivity đặt [edgeLeftLimit]/[edgeRightLimit] (toạ độ màn hình) và [edgeListener].
    var edgeListener: ((EdgeDirection) -> Unit)? = null
    var edgeLeftLimit = 0f
    var edgeRightLimit = Float.MAX_VALUE

    /**
     * P2-7: mép trang (toạ độ màn hình). Widget (phiên có độ lệch neo) dùng luật "tâm ảnh ra ngoài mép trang" = hơn nửa bề ngang widget
     * đã ra ngoài, thay cho dải mép theo ngón tay của icon. Vẫn chờ [EDGE_HOLD_MS] như icon.
     */
    var edgePageLeft = 0f
    var edgePageRight = Float.MAX_VALUE
    private var currentEdge: EdgeDirection? = null
    private val handler = Handler(Looper.getMainLooper())
    private val edgeFire = object : Runnable {
        override fun run() {
            val edge = currentEdge ?: return
            if (currentSession == null) return
            edgeListener?.invoke(edge)
            handler.postDelayed(this, EDGE_HOLD_MS)
        }
    }

    private const val EDGE_HOLD_MS = 500L
    private var hoveredTargets: List<DragDropTarget> = emptyList()

    // M7 Task 6: các target đang gắn vào cửa sổ (IconGridView của từng trang, DockView) tự đăng ký khi attach và
    // huỷ khi detach, nên [DragHelper] (object sống xuyên Activity) không giữ view của Activity đã hủy.
    private val registeredTargets = LinkedHashSet<DragDropTarget>()

    fun registerTarget(target: DragDropTarget) {
        registeredTargets.add(target)
    }

    fun unregisterTarget(target: DragDropTarget) {
        registeredTargets.remove(target)
        hoveredTargets = hoveredTargets.filter { it !== target }
    }

    private fun targetsAt(x: Float, y: Float): List<DragDropTarget> {
        val hits = registeredTargets.filter { it.containsScreenPoint(x, y) }
        val blocking = hits.filter { it.blocksOtherTargets }
        return if (blocking.isNotEmpty()) blocking else hits
    }

    /** `HomeActivity` gọi ở `onCreate`, trước khi có phiên kéo nào xảy ra. */
    fun attachDragVisual(visual: DragVisual) {
        dragVisual = visual
    }

    /**
     * `HomeActivity` gọi ở `onDestroy` — [DragHelper] là `object` sống xuyên Activity, không được giữ
     * tham chiếu tới View của Activity đã bị hủy (rò nhớ). Dọn luôn phiên kéo dở nếu Activity bị hủy
     * giữa lúc đang kéo (xoay máy, chia đôi màn hình...).
     */
    fun detachDragVisual() {
        val abandoned = currentSession
        settlingItemId = null
        stopEdgeTracking()
        edgeListener = null
        dragVisual = null
        currentSession = null
        hoveredTargets = emptyList()
        // G2c Task 13 (review): phiên bị bỏ dở (Activity bị hủy giữa lúc kéo) phải được báo hủy, nếu không `draggingItemId` (và ghostPage) trong
        // ViewModel, vốn sống qua recreate, kẹt lại và chặn mở thư mục, dấu "−", menu nhấn giữ tới khi một phiên kéo khác kết thúc; ô nguồn cũng kẹt ẩn.
        // Gọi SAU khi đã dọn trạng thái (an toàn nếu callback chạm lại DragHelper); không để lỗi trong callback làm hỏng việc dọn của onDestroy.
        if (abandoned != null) {
            try {
                abandoned.onFinished(false)
            } catch (e: Exception) {
                android.util.Log.w("DragHelper", "onFinished(false) failed while detaching", e)
            }
        }
    }

    /**
     * [iconOnly] (P2-6): bản không nhãn của [bitmap], cùng cỡ; [iconSizePx]: cạnh icon trong ảnh (icon ở đầu ảnh, căn giữa ngang) để hạ cánh
     * [DropSettle.iconAligned]. 0 = không phải icon (widget): hạ cánh theo tâm ảnh như cũ.
     */
    fun beginDrag(session: DragSession, bitmap: Bitmap, x: Float, y: Float, iconOnly: Bitmap? = null, iconSizePx: Int = 0) {
        finishSettle() // phiên kéo mới trong lúc ảnh cũ còn đang hạ cánh: kết thúc hạ cánh ngay
        currentSession = session
        hoveredTargets = emptyList()
        stopEdgeTracking()
        labelHidden = false
        iconCenterOffsetY = if (iconSizePx > 0) bitmap.height / 2f - iconSizePx / 2f else 0f
        dragVisual?.startDrag(bitmap, x + session.grabOffsetX, y + session.grabOffsetY, iconOnly) // P2-7: ảnh giữ chỗ so với ngón tay
    }

    /** P2-6: tâm ảnh kéo - tâm icon trong ảnh (px, cỡ gốc). */
    private var iconCenterOffsetY = 0f

    /** P2-6: nhãn của ảnh kéo đang ẩn (đích gộp thư mục / dock không nhãn). Giữ nguyên trạng thái này suốt lúc hạ cánh sau khi thả được nhận. */
    private var labelHidden = false

    /**
     * P2-6: đích gọi khi vào/ra trạng thái mà nhãn của ảnh kéo thành dư ảnh: gộp thư mục (ảnh co vào icon đích) hoặc ở trên dock (ô dock không
     * nhãn). Chỉ có tác dụng trong phiên kéo.
     */
    fun setLabelHidden(hidden: Boolean) {
        if (currentSession == null || labelHidden == hidden) return
        labelHidden = hidden
        dragVisual?.setLabelHidden(hidden)
    }

    /**
     * Cập nhật vị trí ngón tay ([x], [y], toạ độ màn hình). Target dưới điểm (0 hoặc 1 — trang lưới đang hiện hoặc
     * dock) do [DragHelper] tự tìm trong số target đã đăng ký; so với lần gọi trước để phát `onDragEnter`/
     * `onDragExit` đúng 1 lần mỗi lượt đổi trạng thái hover.
     */
    fun updatePosition(x: Float, y: Float) {
        val session = currentSession ?: return
        lastX = x
        lastY = y
        // P2-7: điểm ảnh (tâm ảnh kéo) = ngón tay + độ lệch neo; vẽ và báo đích tại điểm này.
        val px = x + session.grabOffsetX
        val py = y + session.grabOffsetY
        dragVisual?.moveTo(px, py)
        trackEdge(x, px, session)

        val targetsUnderPoint = targetsAt(px, py)
        val entered = targetsUnderPoint.filter { it !in hoveredTargets }
        val exited = hoveredTargets.filter { it !in targetsUnderPoint }
        exited.forEach { it.onDragExit() }
        entered.forEach { it.onDragEnter() }
        targetsUnderPoint.forEach { it.onDragMove(px, py) }
        hoveredTargets = targetsUnderPoint
    }

    /** Tính lại target dưới ngón tay tại điểm cuối đã nhớ — gọi sau khi trang lật xong (ngón tay có thể đứng yên). */
    fun refresh() {
        if (currentSession != null) updatePosition(lastX, lastY)
    }

    /** [x] ngón tay, [px] tâm ảnh. Widget: tâm ảnh ra ngoài mép trang (P2-7); còn lại: ngón tay trong dải mép (M7 Task 7). */
    private fun trackEdge(x: Float, px: Float, session: DragSession) {
        val edge = if (session.isWidget) {
            // P2-7: widget lật khi tâm ảnh ra ngoài mép trang (quá nửa widget) HOẶC ngón tay vào dải mép như icon
            // (điểm giữ sát mép phải/trái widget thì tâm ảnh không thể ra ngoài mép cùng phía).
            when {
                px <= edgePageLeft || x <= edgeLeftLimit -> EdgeDirection.LEFT
                px >= edgePageRight || x >= edgeRightLimit -> EdgeDirection.RIGHT
                else -> null
            }
        } else {
            when {
                x <= edgeLeftLimit -> EdgeDirection.LEFT
                x >= edgeRightLimit -> EdgeDirection.RIGHT
                else -> null
            }
        }
        if (edge == currentEdge) return
        handler.removeCallbacks(edgeFire)
        currentEdge = edge
        if (edge != null) handler.postDelayed(edgeFire, EDGE_HOLD_MS)
    }

    private fun stopEdgeTracking() {
        handler.removeCallbacks(edgeFire)
        currentEdge = null
    }

    /** Thả tại ngón tay ([x], [y]): target nhận thả là target đã đăng ký đầu tiên chứa điểm ảnh; không có thì coi như hủy kéo. */
    fun dropAt(x: Float, y: Float): Boolean {
        val s = currentSession
        return drop(x, y, targetsAt(x + (s?.grabOffsetX ?: 0f), y + (s?.grabOffsetY ?: 0f)).firstOrNull())
    }

    /** Thả tại ngón tay ([x], [y]). [target] là nơi nhận thả, `null` nếu không có target hợp lệ (coi như hủy kéo). P2-7: đích nhận điểm ảnh. */
    fun drop(x: Float, y: Float, target: DragDropTarget?): Boolean {
        val session = currentSession
        // DA (2026-11-20): đặt cờ hạ cánh TRƯỚC onDrop. HomeActivity thu uiState trên Main.immediate nên trạng thái lạc quan mà onDrop
        // phát (item ở chỗ mới, draggingItemId = null) được render NGAY trong onDrop; nếu cờ đặt sau, ô đích (dock, panel thư mục) hiện
        // alpha 1 kèm dấu "−" trong lúc ảnh kéo còn bay tới -> hai icon. Không có hạ cánh thì xóa cờ ở dưới.
        settlingItemId = session?.itemId
        val accepted = target?.onDrop(x + (session?.grabOffsetX ?: 0f), y + (session?.grabOffsetY ?: 0f)) ?: false
        val settle = if (accepted) target?.dropSettle() else null
        // P2-6: nhãn theo đích nhận thả (onDrop vừa đặt); onDragExit bên dưới không được làm nhãn hiện lại giữa lúc hạ cánh.
        // Không nhận thả: ảnh bay về chỗ cũ -> hiện nhãn lại như ô nguồn.
        val keepLabelHidden = settle != null && labelHidden
        hoveredTargets.forEach { it.onDragExit() }
        dragVisual?.setLabelHidden(keepLabelHidden)
        labelHidden = false
        hoveredTargets = emptyList()
        currentSession = null
        stopEdgeTracking()
        val visual = dragVisual
        // G2c Task 13: thả không được nhận (hoặc được nhận nhưng không đổi gì, không có hạ cánh): ảnh bay về chỗ cũ nếu nhìn thấy được.
        val rect = settle?.rect ?: session?.returnRect?.invoke()
        if (rect != null && session != null && visual != null) {
            settlingItemId = session.itemId
            val endScale = settle?.endScale ?: 1f
            // P2-6: đích đưa hình vuông icon -> tâm ảnh = tâm icon đích + độ lệch icon trong ảnh (co theo endScale).
            val dy = if (settle?.iconAligned == true) iconCenterOffsetY * endScale else 0f
            visual.settleTo(
                (rect.left + rect.right) / 2f,
                (rect.top + rect.bottom) / 2f + dy,
                endScale,
                settle?.endAlpha ?: 1f
            ) { finishSettle() }
        } else {
            settlingItemId = null // DA: đặt trước onDrop nhưng không có hạ cánh
            visual?.endDrag()
            // Không có hạ cánh: ô nguồn (đang ẩn) hiện lại theo dữ liệu ngay. DA: báo cho MỌI phiên (không chỉ khi có returnRect), vì
            // trong onDrop target đã thấy settlingItemId (vd IconGridView.endDragVisualState bỏ qua restoreDraggedAlpha) và giữ ô ẩn.
            if (session != null) registeredTargets.toList()
                .forEach { it.onDragSettled() }
        }
        return accepted
    }

    /** Kết thúc hạ cánh (xong tự nhiên hoặc bị ngắt): bỏ cờ và báo các target để ô hiện lại theo dữ liệu. */
    private fun finishSettle() {
        if (settlingItemId == null) return
        settlingItemId = null
        registeredTargets.toList().forEach { it.onDragSettled() }
    }
}
