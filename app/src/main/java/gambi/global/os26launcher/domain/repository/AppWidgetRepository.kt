package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.AppWidgetAppEntry

/**
 * G3c — id widget của host (`AppWidgetHost`), phần domain thấy được. Chỉ lo VÒNG ĐỜI id; liệt kê provider, gắn, hộp thoại quyền thuộc các Task sau.
 *
 * Id là tài nguyên hệ thống: cấp bằng `allocateAppWidgetId`, giữ tới khi `deleteAppWidgetId`. Mọi hàm ở đây an toàn khi gọi nhiều lần và không
 * ném (lỗi hệ thống chỉ ghi log tên lớp ngoại lệ). Xem docs/plans/G3c-appwidget-host.md, bảng "Rò id".
 */
interface AppWidgetRepository {

    /**
     * Giải phóng các id vừa mất hàng (gọi SAU khi transaction xóa hàng đã commit, trong cùng vùng khóa của `LayoutReconciler`). Id đang trong luồng
     * thêm ([markInFlight]) không bị giải phóng ở đây.
     */
    fun release(appWidgetIds: Collection<Int>)

    /**
     * Quét lúc khởi động (`LayoutReconciler.catchUp`): mọi id host đang giữ mà KHÔNG nằm trong [keepIds] (id của hàng `itemType = 3` còn trong DB) và
     * không đang trong luồng thêm bị giải phóng. Phủ crash giữa chừng, tiến trình bị giết giữa luồng thêm, xóa rồi chưa kịp giải phóng.
     */
    fun sweep(keepIds: Set<Int>)

    /**
     * DB bố cục rỗng (xóa dữ liệu app: đo được trên Live 4 là `pm clear` KHÔNG xóa id của host): xóa mọi bản ghi của host. Không làm gì nếu có id
     * đang trong luồng thêm.
     */
    fun resetHost()

    /** Luồng thêm widget vừa cấp [appWidgetId] (chưa có hàng): [sweep] và [resetHost] không được đụng tới nó. */
    fun markInFlight(appWidgetId: Int)

    /** Luồng thêm kết thúc (đã ghi hàng, hoặc đã giải phóng id). */
    fun clearInFlight(appWidgetId: Int)

    /** Máy có tính năng widget app (`FEATURE_APP_WIDGETS`) không; không thì ẩn phần "Tiện ích của ứng dụng" (D8). */
    val isSupported: Boolean

    /**
     * G3c Task 6 — danh mục cho bảng "Tiện ích": mọi provider `WIDGET_CATEGORY_HOME_SCREEN` không `HIDE_FROM_PICKER`, gom theo app, sắp theo tên app.
     * Chạy trên IO (đọc tên app); rỗng khi máy không hỗ trợ.
     */
    suspend fun catalog(): List<AppWidgetAppEntry>
}
