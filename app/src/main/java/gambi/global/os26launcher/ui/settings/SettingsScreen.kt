package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import gambi.global.os26launcher.domain.model.ComponentKey

/**
 * G2d — một màn của khung Cài đặt. [SettingsActivity] chỉ là khung (toolbar, inset, danh sách, nạp trạng thái); mỗi màn tự dựng
 * hàng, tự xử lý bấm, tự giữ hộp thoại và `ActivityResult` launcher của nó. Màn được dựng trong `onCreate` của khung nên launcher
 * đăng ký đúng thứ tự mỗi lần Activity dựng lại (màn xác định bởi extra của intent).
 */
interface SettingsScreen {
    @get:StringRes
    val titleRes: Int

    fun rows(state: SettingsUiState): List<SettingsRow>

    fun onValueClicked(row: SettingsRow.Value)

    fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean)

    /** RN: bấm một hàng [SettingsRow.AppName] (chỉ màn có hàng loại này dùng). */
    fun onAppNameClicked(row: SettingsRow.AppName) {}

    /** RS Task 3: người dùng chọn hàng radio chưa chọn. */
    fun onRadioClicked(row: SettingsRow.Radio) {}

    /** LG: chữ trong ô tìm [row] đổi. */
    fun onSearchChanged(row: SettingsRow.Search, query: String) {}

    /** RS Task 3: người dùng đổi lựa chọn segmented sang [index]. */
    fun onSegmentChanged(row: SettingsRow.Segmented, index: Int) {}

    /** RS Task 8: chạm ô ảnh nền thứ [index] của `SettingsRow.WallpaperGrid`. */
    fun onWallpaperTileClicked(index: Int) {}

    /** RS Task 2: bấm nút "Đặt làm mặc định" trên card Launcher hiện tại (chỉ trang chủ có card). */
    fun onLauncherCardClicked() {}

    /** BG: bấm nút của card xin quyền [row]. */
    fun onPermissionCardClicked(row: SettingsRow.PermissionCard) {}

    /** LO: chạm ô kiểu hiển thị thư viện [index] (0 = Thư mục danh mục, 1 = Danh sách A–Z) của [row]. */
    fun onLibraryStyleClicked(row: SettingsRow.LibraryStyle, index: Int) {}

    /** BG: chạm ô kiểu huy hiệu [index] (0 = Số, 1 = Chấm) của [row]. */
    fun onBadgeStyleClicked(row: SettingsRow.BadgeStyle, index: Int) {}

    /** BG: người dùng lật công tắc của hàng app [row] sang [checked]. */
    fun onAppSwitchChanged(row: SettingsRow.AppSwitch, checked: Boolean) {}

    /** RN: bộ nạp icon cho hàng [SettingsRow.AppName] (cạnh vuông px); null = không hiện icon. Màn khác không đổi. */
    val appIconLoader: (suspend (ComponentKey, Int) -> Bitmap?)? get() = null

    /** Gọi ở `onResume` của khung, trước khi dựng lại danh sách (vd đọc lại quyền hệ thống). */
    fun onResume() {}

    /** Sự kiện một lần của ViewModel mà khung không tự xử lý (khung lo `Message`). Trả true nếu màn đã xử lý. */
    fun onEvent(event: SettingsEvent): Boolean = false

    /** Chạy trong `repeatOnLifecycle(STARTED)` của khung: màn thu flow riêng của nó ở đây. */
    suspend fun observe() {}

    /** HL-2: gọi ở `onStop` của khung (vd khóa lại màn Ứng dụng ẩn khi rời màn). */
    fun onStop() {}

    /** Gọi ở `onDestroy`: dismiss hộp thoại (gỡ listener trước). */
    fun onDestroy() {}
}

/**
 * AB — hành động không có màn (vd 6.1 Đánh giá: một hộp thoại trên trang chủ). Hub chạy nó tại chỗ thay vì mở Activity; hành động
 * tự giữ hộp thoại và dismiss ở [onDestroy] (hub gọi ở `onDestroy` của khung).
 */
interface SettingsAction {
    fun run()

    fun onDestroy() {}
}

/** Những thứ khung đưa cho màn. */
class SettingsScreenContext(
    val activity: AppCompatActivity,
    val viewModel: SettingsViewModel,
    val defaultLauncher: DefaultLauncherAction,
    private val onRerender: (Boolean) -> Unit,
    private val onOpen: (SettingsScreenId) -> Unit
) {
    /** Dựng lại danh sách từ trạng thái mới nhất; [force] ép bind lại mọi hàng (khi công tắc đã lật nhưng dữ liệu không đổi). */
    fun rerender(force: Boolean = false) = onRerender(force)

    /** Mở một màn khác (Activity mới trên cùng task, Back quay về màn này). */
    fun open(id: SettingsScreenId) = onOpen(id)
}

/** Mã màn, đi qua extra [SettingsActivity.EXTRA_SCREEN]. Thêm giá trị mới ở CUỐI. */
enum class SettingsScreenId {
    HUB,
    THEME, GENERAL, LANGUAGE, WEATHER, LOCATION, // nhóm Chung (1.1-1.5)
    GLASS, WALLPAPER, ICON_SET, // nhóm Giao diện (2.1-2.3)
    SWIPE, LIBRARY, SEARCH, BADGES, // nhóm Màn hình nền (3.1-3.4)
    TRANSITIONS, // nhóm Chuyển cảnh (4.1; 4.2 Hiệu ứng ứng dụng đã bỏ ở P3-1)
    HIDDEN_APPS, CHANGE_ICON, RENAME_APPS, // nhóm Tùy chỉnh biểu tượng (5.1-5.3; 5.1 từ HL-1 là màn Cài đặt, không còn AppLibraryActivity)
    RATE, MORE_APPS, PRIVACY; // nhóm Liên hệ (6.1-6.3)

    companion object {
        /** Thiếu hoặc lạ thì về trang chủ. */
        fun fromExtra(name: String?): SettingsScreenId =
            entries.firstOrNull { it.name == name } ?: HUB
    }
}

/**
 * Bảng đăng ký màn. Nhánh thêm màn: thay MỘT dòng giữ chỗ bên dưới bằng `SettingsScreenId.X to ::XxxSettingsScreen`; mục tương ứng
 * ở trang chủ tự bật. Các ô cách nhau một dòng trống để hai nhánh sửa hai ô khác nhau không xung đột khi rebase.
 */
object SettingsScreens {
    private val factories: Map<SettingsScreenId, (SettingsScreenContext) -> SettingsScreen> = mapOf(
        SettingsScreenId.HUB to ::SettingsHubScreen,

        // 1.1 THEME (TH)

        SettingsScreenId.GENERAL to ::GeneralSettingsScreen,

        SettingsScreenId.LANGUAGE to ::LanguageSettingsScreen, // LG

        SettingsScreenId.WEATHER to ::WeatherSettingsScreen, // WU; WX thêm hàng ở WeatherSettingsScreen

        SettingsScreenId.LOCATION to ::LocationSettingsScreen, // WX

        SettingsScreenId.GLASS to ::LiquidGlassSettingsScreen,

        SettingsScreenId.WALLPAPER to ::WallpaperSettingsScreen,

        SettingsScreenId.ICON_SET to ::IconSetSettingsScreen,

        SettingsScreenId.SWIPE to ::SwipeSettingsScreen, // 3.1 (HT; PT thêm hiệu ứng trang bằng cách sửa SwipeSettingsScreen)

        SettingsScreenId.LIBRARY to ::LibrarySettingsScreen, // 3.2 (LO)

        SettingsScreenId.SEARCH to ::SearchSettingsScreen,

        SettingsScreenId.BADGES to ::BadgesSettingsScreen, // BG

        SettingsScreenId.TRANSITIONS to ::TransitionsSettingsScreen, // TR

        SettingsScreenId.HIDDEN_APPS to ::HiddenAppsSettingsScreen, // HL-1 (5.1)
        SettingsScreenId.CHANGE_ICON to ::ChangeIconSettingsScreen, // P3-4 (CI)

        SettingsScreenId.RENAME_APPS to ::RenameAppsSettingsScreen,

        // 6.1 RATE (AB): là hành động không màn, đăng ký ở `actions` bên dưới, không phải ở đây

        SettingsScreenId.MORE_APPS to ::MoreAppsSettingsScreen,

        SettingsScreenId.PRIVACY to ::PrivacySettingsScreen
    )

    /**
     * AB — hành động không màn: mục ở trang chủ bật khi id có ở đây; bấm thì hub chạy hành động tại chỗ (không `ctx.open`).
     * Mỗi id một dòng.
     */
    private val actions: Map<SettingsScreenId, (SettingsScreenContext) -> SettingsAction> = mapOf(
        SettingsScreenId.RATE to ::RateAction
    )

    fun isRegistered(id: SettingsScreenId): Boolean = id in factories || id in actions

    fun isAction(id: SettingsScreenId): Boolean = id in actions

    /** Hành động của [id], hoặc null nếu [id] không phải hành động. */
    fun createAction(id: SettingsScreenId, context: SettingsScreenContext): SettingsAction? =
        actions[id]?.invoke(context)

    /** Màn của [id], hoặc null nếu chưa đăng ký. */
    fun create(id: SettingsScreenId, context: SettingsScreenContext): SettingsScreen? =
        factories[id]?.invoke(context)
}
