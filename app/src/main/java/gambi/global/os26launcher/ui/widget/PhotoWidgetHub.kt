package gambi.global.os26launcher.ui.widget

import gambi.global.os26launcher.domain.model.photo.PhotoRef
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * G3b Task 2 (D6) — cầu nối giữa [PhotoWidgetRepository] và các ô widget Ảnh (view), cùng mẫu `WeatherWidgetHub`: `object` sống xuyên Activity, KHÔNG
 * giữ `View`. Kho ảnh đổi (Activity riêng vừa thêm/cắt/xóa ảnh) -> [WidgetTicker.tickNow] để widget đang HIỆN vẽ lại; widget ẩn tự đọc lại khi hiện
 * (`WidgetTicker.register`). Không `Handler`, không receiver riêng, không việc nền.
 */
internal object PhotoWidgetHub {
    private var repository: PhotoWidgetRepository? = null

    /** false cho tới khi kho ảnh đọc xong: trước đó chưa biết widget nào có ảnh nên renderer vẽ trạng thái trống thay vì giải mã. */
    @Volatile
    private var loaded = false

    /** Cỡ px thật gần nhất của từng widget (renderer báo ở `prepare`); màn cắt (Task 4) lấy tỉ lệ từ đây. */
    private val sizes = ConcurrentHashMap<Long, Pair<Int, Int>>()

    fun init(repo: PhotoWidgetRepository, layout: LayoutRepository, scope: CoroutineScope) {
        if (repository != null) return
        repository = repo
        // Quét mồ côi (Task 6, plan D8): MỘT lần mỗi lần khởi động tiến trình, SAU khi bố cục đã nạp (lần phát đầu của `pages` là dữ liệu thật, không có giá trị khởi
        // tạo giả). Truyền mọi `itemId` đang có trong bố cục; thư mục của id còn tồn tại không bao giờ bị chạm. `sweep` chạy I/O ở IO dispatcher, không ở luồng chính.
        scope.launch {
            val existing = layout.pages.first().flatMap { page -> page.items.map { it.itemId } }.toSet()
            repo.sweep(existing) // TV-3: id của widget Today (không có hàng bố cục) được sweep tự bỏ qua
        }
        scope.launch {
            repo.awaitLoaded()
            loaded = true
            WidgetTicker.tickNow()
        }
        scope.launch {
            repo.widgets.collect { all ->
                // Bỏ bitmap của ảnh không còn (đã xóa / vùng cắt đổi `version`); cho phép thử lại khóa từng lỗi.
                PhotoBitmapCache.onConfigChanged { key ->
                    all[key.itemId]?.photos?.any { it.id == key.photoId && it.version == key.version } == true
                }
                WidgetTicker.tickNow()
            }
        }
    }

    val isLoaded: Boolean get() = loaded

    /** Cấu hình của widget [itemId] (rỗng nếu chưa có / chưa nạp). Đọc rẻ, gọi mỗi `prepare`. */
    fun configOf(itemId: Long): PhotoWidgetConfig = repository?.config(itemId) ?: PhotoWidgetConfig()

    fun fileOf(itemId: Long, ref: PhotoRef) = repository?.photoFile(itemId, ref.id)

    fun reportSize(itemId: Long, width: Int, height: Int) {
        if (itemId > 0L && width > 0 && height > 0) sizes[itemId] = width to height
    }

    /** Cỡ px thật gần nhất của widget [itemId], null nếu chưa vẽ lần nào (màn cắt dùng tỉ lệ danh nghĩa). */
    fun sizeOf(itemId: Long): Pair<Int, Int>? = sizes[itemId]
}
