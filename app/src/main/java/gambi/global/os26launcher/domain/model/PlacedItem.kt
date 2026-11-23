package gambi.global.os26launcher.domain.model

/**
 * G2a A1a — một thứ đang chiếm một ô trong trang lưới: app hoặc thư mục. [itemId] là id hàng `launcher_items` thật
 * (`0L` chỉ dùng cho layout NHÁP chưa ghi DB, xem `BuildDefaultLayoutUseCase`). Các planner của lưới chỉ cần vị trí
 * nên coi cả hai như nhau; [withPosition] để đổi vị trí mà không cần biết loại.
 */
sealed interface PlacedItem {
    val itemId: Long
    val position: GridPosition
    fun withPosition(position: GridPosition): PlacedItem
}

/**
 * Một app đã được đặt vào một ô cụ thể trong lưới. [itemId] là id hàng `launcher_items` thật (M7 Task 5
 * thêm, để UI gọi được `MoveItemUseCase`/`HideAppUseCase` theo đúng hàng) -- `0L` chỉ dùng cho layout
 * NHÁP chưa ghi DB (xem [gambi.global.os26launcher.domain.usecase.BuildDefaultLayoutUseCase], giá trị
 * này không bao giờ được đọc lại ở đó).
 */
data class PlacedApp(
    override val itemId: Long,
    override val position: GridPosition,
    val app: AppItem
) : PlacedItem {
    override fun withPosition(position: GridPosition): PlacedApp = copy(position = position)
}

/**
 * Một thư mục trên lưới chính (1x1). [title] rỗng = chưa đặt tên, UI hiển thị tên mặc định (chữ nằm ở strings.xml, domain
 * không giữ chữ hiển thị). [previewApps] là MỌI app con theo `rank` (FM-1, trước đây chỉ 9 app đầu) và đã bỏ app không
 * còn khả dụng; icon thư mục chỉ vẽ `FolderPlanner.PAGE_SIZE` app đầu. [appCount] là số app khả dụng trong thư mục.
 */
data class PlacedFolder(
    override val itemId: Long,
    override val position: GridPosition,
    val title: String,
    val previewApps: List<AppItem>,
    /** Id hàng `launcher_items` của từng app trong [previewApps], cùng thứ tự (để kéo app ra khỏi thư mục). */
    val childIds: List<Long>,
    val appCount: Int
) : PlacedItem {
    override fun withPosition(position: GridPosition): PlacedFolder = copy(position = position)
}

/**
 * G3c — họ widget chiếm nhiều ô: widget tự vẽ ([PlacedWidget]) và widget của app khác ([PlacedAppWidget]). Mọi luật HÌNH HỌC của widget (không gộp thư mục,
 * không vào dock, không vào thư mục, không chồng widget khác, trừ ngân sách kính của trang) kiểm theo họ này; chỉ chỗ cần biết đó là widget tự vẽ
 * (renderer, mở app Đồng hồ/Lịch, bảng thời tiết) mới kiểm [PlacedWidget].
 */
sealed interface PlacedWidgetBase : PlacedItem

/**
 * G2c — widget tự vẽ trên lưới chính: chiếm hình chữ nhật `position.spanX x position.spanY` ô từ góc trên-trái `position`.
 * Không phải đích gộp thư mục, không vào dock, không vào thư mục.
 */
data class PlacedWidget(
    override val itemId: Long,
    override val position: GridPosition,
    val kind: WidgetKind
) : PlacedWidgetBase {
    override fun withPosition(position: GridPosition): PlacedWidget = copy(position = position)
}

/**
 * G3c — widget thật của app khác (`launcher_items.itemType = 3`): [appWidgetId] do `AppWidgetHost` cấp, [provider] là provider đã gắn. Cỡ ô ở
 * `position.spanX/spanY`. Nội dung là một `AppWidgetHostView` (Task 4); hàng có thể còn khi provider tạm vắng (không xóa hàng, plan D14).
 */
data class PlacedAppWidget(
    override val itemId: Long,
    override val position: GridPosition,
    val appWidgetId: Int,
    val provider: AppWidgetRef
) : PlacedWidgetBase {
    override fun withPosition(position: GridPosition): PlacedAppWidget = copy(position = position)
}
