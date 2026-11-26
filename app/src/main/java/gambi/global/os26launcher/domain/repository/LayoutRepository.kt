package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.GridPosition
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.WidgetKind
import kotlinx.coroutines.flow.Flow

/**
 * Bố cục màn hình chính đã lưu (M4). DB chỉ lưu VỊTRÍ; nhãn app lấy sống từ [AppRepository] và ghép vào lúc đọc.
 *
 * Cả hai flow KHÔNG có giá trị khởi tạo giả: lần phát đầu tiên đã là dữ liệu thật (sau khi seed lần đầu nếu cần).
 * Chưa phát gì nghĩa là đang nạp. Phát lại khi DB hoặc danh sách app đổi.
 */
interface LayoutRepository {
    /** Các trang lưới theo thứ tự trang. Item trỏ tới app không còn khả dụng bị bỏ khỏi kết quả (không xóa khỏi DB). */
    val pages: Flow<List<HomePage>>

    /** Dock theo `rank`. */
    val dock: Flow<List<DockApp>>

    /** M7 — app đang bị ẩn (có trong `hidden_apps`), dùng cho màn Thư viện ứng dụng. */
    val hiddenApps: Flow<List<AppItem>>

    /**
     * M7 — di chuyển 1 item đã có trong bố cục tới [targetContainer] tại [targetCell].
     * [targetPageId] bắt buộc khi [targetContainer] là lưới chính, bỏ qua khi là dock.
     * Trả `false` nếu không thể (ví dụ dock đã đầy) — UI tự animate icon bay về vị trí cũ.
     */
    suspend fun moveItem(
        itemId: Long,
        targetContainer: Long,
        targetPageId: Long?,
        targetCell: GridPosition
    ): Boolean

    /**
     * G2c — thêm widget tự vẽ [kind] cỡ [spanX] x [spanY] vào hình chữ nhật trống đầu tiên, bắt đầu tìm từ trang [preferredPageId]
     * (null = trang đầu); không đẩy icon, hết chỗ thì tạo trang cuối. Trả id trang đã đặt, null nếu cỡ không hợp lệ.
     */
    suspend fun addWidget(kind: WidgetKind, spanX: Int, spanY: Int, preferredPageId: Long?): Long?

    /**
     * G3c — ghi hàng `itemType = 3` cho widget của app khác đã cấp id và gắn xong ([appWidgetId]), cỡ [spanX] x [spanY] (trần `AppWidgetLimits`): hình chữ nhật
     * trống đầu tiên từ trang [preferredPageId], không đẩy icon, hết chỗ thì tạo trang cuối. Trả id trang đã đặt, null nếu cỡ không hợp lệ (không ghi gì; người gọi
     * phải giải phóng [appWidgetId]).
     */
    suspend fun addAppWidget(
        provider: gambi.global.os26launcher.domain.model.AppWidgetRef,
        appWidgetId: Int,
        spanX: Int,
        spanY: Int,
        preferredPageId: Long?
    ): Long?

    /**
     * G2c Task 13 — tạo widget [kind] cỡ [spanX] x [spanY] đúng chỗ người dùng thả: neo [cell] của trang [targetPageId] (`PageId.NEW` = trang
     * tạm ở cuối). Khác [addWidget]: icon bị phủ ĐƯỢC đẩy (đúng luật kéo widget có sẵn, `GridInsertPlanner`), tạo hàng và dời icon trong CÙNG
     * một transaction. false (không ghi gì) nếu chồng widget khác hoặc cỡ không hợp lệ.
     */
    suspend fun addWidgetAt(
        kind: WidgetKind,
        spanX: Int,
        spanY: Int,
        targetPageId: Long,
        cell: GridPosition
    ): Boolean

    /** M7 — xóa 1 item khỏi bố cục theo id (không dồn ô, không xóa trang trống). */
    suspend fun removeItem(itemId: Long)

    /** EP — xóa mọi trang màn hình chính không còn item nào (cả trang ở giữa), luôn giữ ≥ 1 trang. Không dời item nào. */
    suspend fun pruneEmptyPages()

    /**
     * M8a + FX3 — đổi cỡ lưới thành [target]: xếp lại toàn bộ icon lưới chính (thứ tự đọc, nén hết khe, tạo/xóa trang) VÀ ghi cài
     * đặt cỡ lưới, trong một vùng khóa của reconciler để không việc ghi bố cục nào (cài app mới...) chen vào giữa. Thứ tự ghi
     * cài đặt/DB nằm ở `LayoutReconciler.changeGridPreset`. [target] bằng cỡ hiện hành thì không làm gì.
     */
    suspend fun changeGridPreset(target: GridPreset)

    /**
     * G2a A1a — gộp app [draggedItemId] và app [targetItemId] thành một thư mục MỚI tại ô của đích (đích rank 0, nguồn rank 1,
     * tên chưa đặt). Trả `false` nếu từ chối (xem `FolderPlanner.canCreate`) hoặc không tìm thấy item; khi đó không ghi gì.
     */
    suspend fun createFolder(draggedItemId: Long, targetItemId: Long): Boolean

    /**
     * AG — gom các app [itemIds] (đang nằm phẳng trên lưới chính) vào thư mục [folderId] (null = tạo thư mục [title] tại ô của app đầu,
     * cần ≥ 2 app), rồi dồn các trang có app rời đi và gỡ trang vừa hết item. Trả số app đã chuyển; 0 = không ghi gì.
     */
    suspend fun groupIntoFolder(title: String, itemIds: List<Long>, folderId: Long?): Int

    /** G2a A3a — thêm app vào cuối thư mục. `false` nếu bị từ chối (thư mục đầy / không hợp lệ), không ghi gì. */
    suspend fun addToFolder(itemId: Long, folderId: Long): Boolean

    /** FM-3 — đổi thứ tự app trong thư mục theo [orderedChildIds] (thứ tự nhìn thấy). `false` nếu danh sách không hợp lệ, không ghi gì. */
    suspend fun reorderFolder(folderId: Long, orderedChildIds: List<Long>): Boolean

    /** G2a A2 — đặt tên thư mục. [title] đã chuẩn hóa (`FolderPlanner.normalizeTitle`); rỗng = bỏ tên. Không có thư mục đó thì bỏ qua. */
    suspend fun renameFolder(folderId: Long, title: String)

    /** M7 — ẩn/bỏ ẩn app theo [key]. Ẩn thì gỡ khỏi bố cục; bỏ ẩn thì đặt lại vào ô trống đầu tiên. */
    suspend fun setHidden(key: ComponentKey, userSerial: Long, hidden: Boolean)

    /** TF — gỡ app khỏi Home (lưới, thư mục, dock) mà KHÔNG ẩn: app vẫn ở Library/tìm kiếm, không tự quay lại Home (kéo từ Library ra lại: [addAppAt]). */
    suspend fun removeFromHome(key: ComponentKey, userSerial: Long)

    /**
     * LD — tạo hàng app MỚI cho [key] đúng chỗ thả khi kéo từ App Library: lưới ([targetContainer] = HOME, neo [cell] của [targetPageId], `PageId.NEW` =
     * trang tạm, icon bị phủ được đẩy) hoặc dock (slot `cell.x`). Cho phép bản trùng; bỏ cờ `offHome`. false (không ghi gì) nếu bị từ chối.
     */
    suspend fun addAppAt(key: ComponentKey, targetContainer: Long, targetPageId: Long?, cell: GridPosition): Boolean
}
