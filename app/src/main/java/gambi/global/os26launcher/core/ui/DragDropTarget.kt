package gambi.global.os26launcher.core.ui

import gambi.global.os26launcher.domain.model.ScreenRect

/**
 * AN Task 9 — nơi ảnh bay "hạ cánh" sau khi thả được nhận: [rect] là khung ô đích trên màn hình (tâm của nó là nơi ảnh dừng);
 * [endScale]/[endAlpha] khác 1/1 khi icon biến mất vào thư mục (thu nhỏ rồi mờ).
 */
/**
 * P2-6: [iconAligned] = [rect] là hình vuông ICON đích (không phải cả ô): [DragHelper] dời tâm hạ cánh xuống theo độ lệch icon trong ảnh kéo
 * (ảnh = cả ô, icon ở đầu ô, nhãn bên dưới) để icon của ảnh rơi đúng lên icon đích, kể cả khi ô nguồn và ô đích cao khác nhau (lưới có nhãn, dock không).
 */
data class DropSettle(val rect: ScreenRect, val endScale: Float = 1f, val endAlpha: Float = 1f, val iconAligned: Boolean = false)

/**
 * M7 — mục tiêu có thể nhận thả icon đang kéo (`IconGridView`, `DockView`). [DragHelper] gọi các hàm
 * này theo tọa độ MÀN HÌNH tuyệt đối; mỗi target tự quy đổi về tọa độ của chính nó trước khi gọi
 * `GridMetrics.Cells.cellAt`.
 */
interface DragDropTarget {
    /**
     * Toạ độ MÀN HÌNH tuyệt đối ([x], [y]) có nằm trong vùng của target này không (M7 Task 6). [DragHelper] dùng để
     * tự chọn target dưới ngón tay (lưới hay dock), thay cho việc nguồn kéo tự truyền danh sách target.
     */
    fun containsScreenPoint(x: Float, y: Float): Boolean

    /** Điểm kéo vừa đi vào vùng của target này (chưa thả). */
    fun onDragEnter()

    /** Điểm kéo đang ở trong vùng của target này, toạ độ màn hình tuyệt đối — dùng để hiện preview đẩy-dồn. */
    fun onDragMove(x: Float, y: Float)

    /** Điểm kéo vừa ra khỏi vùng của target này (chưa thả) — trả icon preview về vị trí gốc. */
    fun onDragExit()

    /**
     * G2a A3b: true = khi điểm kéo nằm trong vùng của target này thì CHỈ target này nhận hover/thả, các target khác bên dưới
     * bị bỏ qua (panel thư mục phủ lên lưới và dock). Mặc định false.
     */
    val blocksOtherTargets: Boolean get() = false

    /**
     * AN Task 9: [DragHelper] gọi MỘT lần ngay sau [onDrop] trả `true`: đích hạ cánh của ảnh bay, hoặc null (mặc định) = tắt ảnh bay
     * ngay như trước.
     */
    fun dropSettle(): DropSettle? = null

    /** AN Task 9: ảnh bay đã hạ cánh xong (hoặc bị ngắt): ô của item vừa thả hiện lại theo dữ liệu. */
    fun onDragSettled() {}

    /** Thả tại toạ độ màn hình tuyệt đối. Trả `false` nếu target từ chối (ví dụ dock đã đầy) — không ghi gì. */
    fun onDrop(x: Float, y: Float): Boolean
}
