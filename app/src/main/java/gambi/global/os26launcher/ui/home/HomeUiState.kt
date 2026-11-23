package gambi.global.os26launcher.ui.home

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.GridPreset
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.ScreenRect

/** Trạng thái ảnh nền của màn hình chính (bảng trạng thái màn hình ở mục 7 của project_structure.md). */
enum class BackdropStatus { Loading, Empty, Ready, Error }

/**
 * Trạng thái lưới icon (M3 Task 3). Loading = chưa có lần phát dữ liệu nào từ `AppRepository.apps`
 * (bao gồm giá trị khởi tạo của `stateIn`) — khác Empty (đã có dữ liệu thật, nhưng 0 app sau khi trừ dock).
 */
enum class GridStatus { Loading, Empty, Ready }

data class HomeUiState(
    val backdropStatus: BackdropStatus = BackdropStatus.Loading,
    val backdrop: Bitmap? = null,
    val backdropIsDefault: Boolean = false, // WP: ảnh đang hiện là ảnh mặc định (chưa chọn ảnh)
    val isDefaultLauncher: Boolean = false,   // M1 Task 3 ghi vào
    val pages: List<HomePage> = emptyList(), // M3 Task 3
    val gridStatus: GridStatus = GridStatus.Loading, // M3 Task 3
    val dock: List<DockApp> = emptyList(), // M3 Task 4
    val editMode: Boolean = false, // M7 Task 4
    // M7 Task 5 Step 4: icon đang bị kéo (ẩn khỏi lưới). Là DỮ LIỆU nên view nào được bind cũng ra đúng alpha.
    val draggingItemId: Long? = null,
    // M7 Task 7 (E2): trang trống TẠM ở cuối, chỉ có trong lúc đang kéo và chỉ ở UI — KHÔNG có hàng `home_pages`
    // tương ứng cho tới khi người dùng thả icon LÊN trang đó (xem LayoutReconciler.moveToHomeInTransaction).
    val ghostPage: Boolean = false,
    // M7 Task 10 (Ruling G1): trạng thái "Shortcut" -- menu nhấn giữ đang mở. null = không ở Shortcut. Loại trừ với editMode.
    val iconMenu: IconMenuState? = null,
    // M8a Task 2: cỡ lưới + nhãn đến từ cài đặt (DataStore). gridStatus chỉ Ready sau khi CẢ cài đặt LẪN bố cục đã phát
    // lần đầu, để không nháy 4×6 rồi mới ra cỡ đã lưu.
    val grid: GridPreset = GridPreset.DEFAULT,
    val showLabels: Boolean = true,
    // G2a A2: thư mục đang mở panel (id hàng thật > 0). Nằm ở ViewModel để sống qua recreate().
    val openFolderId: Long? = null,
    // G2a B2 (D17): App Library đang mở hẳn. Nằm ở ViewModel để sống qua recreate(); tiến độ giữa chừng chỉ ở view.
    val libraryOpen: Boolean = false,
    // G2a B3: nhóm đang mở panel trong Library. Chỉ có nghĩa khi libraryOpen; nằm ở ViewModel để sống qua recreate().
    val openCategoryGroup: CategoryGroup? = null,
    // TV: màn Today đang mở hẳn. Như libraryOpen: ở ViewModel để sống qua recreate(); tiến độ giữa chừng chỉ ở controller.
    val todayOpen: Boolean = false,
    // TV-3: chế độ sửa của Today View (rung, dấu "−", thanh "+" / "Xong"). Riêng với editMode của Home; chỉ có nghĩa khi todayOpen.
    val todayEditMode: Boolean = false
)

/** Icon đang được mở menu: [container] = ContainerId.HOME/DOCK, [anchor] = ô icon trên màn hình lúc nhấn giữ. */
data class IconMenuState(
    val itemId: Long,
    val container: Long,
    val key: ComponentKey,
    val anchor: ScreenRect,
    // null = đang nạp shortcut (scrim + icon nổi hiện ngay, menu chờ để khỏi nhảy vị trí); rỗng = app không có shortcut.
    val shortcuts: List<AppShortcutItem>? = null
)
