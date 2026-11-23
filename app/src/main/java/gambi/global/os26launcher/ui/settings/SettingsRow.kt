package gambi.global.os26launcher.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.core.wallpaper.WallpaperPreset

/** RS: ô icon đầu hàng (trang chủ Cài đặt, Figma 302:1492): icon DS3 `ds_ic_*` trên nền màu [color]. */
data class SettingsTile(@DrawableRes val icon: Int, val color: DsTileColor) {
    companion object {
        /** Màu truyền cho `DsRowView.setTile(null, …)` khi hàng không có ô (ô ẩn, màu không dùng). */
        val NONE_COLOR = DsTileColor.SLATE
    }
}

/**
 * M8a Task 3 — một hàng của màn Cài đặt. Adapter nhóm các hàng liên tiếp giữa hai [Header] thành một khối bo góc
 * kiểu iOS (nền single/top/middle/bottom theo vị trí trong nhóm).
 */
sealed interface SettingsRow {
    /** Khóa ổn định cho DiffUtil và để Activity biết hàng nào được bấm (không so chuỗi đã dịch). */
    val id: String

    data class Header(override val id: String, @StringRes val title: Int) : SettingsRow

    data class Value(
        override val id: String,
        @StringRes val title: Int,
        val value: String,
        val enabled: Boolean = true,
        /** WX: tên động (vd tên thành phố); khác null thì dùng thay [title] (khi đó [title] = 0). Mặc định null: các màn khác không đổi. */
        val titleText: String? = null,
        /** RS: ô icon đầu hàng; null = không có (mọi màn trừ trang chủ). */
        val tile: SettingsTile? = null
    ) : SettingsRow

    data class Switch(
        override val id: String,
        @StringRes val title: Int,
        val checked: Boolean,
        /** G3a: dòng mô tả dưới tiêu đề (0 = không có). */
        @StringRes val description: Int = 0,
        /** RS: ô icon đầu hàng; null = không có. */
        val tile: SettingsTile? = null
    ) : SettingsRow

    /** RS Task 3: một lựa chọn trong nhóm chọn-một hiện thẳng trên màn (Figma 1.5). Chạm hàng chưa chọn thì báo màn; đã chọn thì thôi. */
    data class Radio(
        override val id: String,
        @StringRes val title: Int,
        val checked: Boolean,
        @StringRes val description: Int = 0,
        /** LG: chữ động (vd tên bản ngữ của ngôn ngữ); khác null thì dùng thay [title]/[description]. Mặc định null: màn khác không đổi. */
        val titleText: String? = null,
        val descriptionText: String? = null
    ) : SettingsRow

    /**
     * LG: ô tìm đầu màn (Figma 1.3 "Tìm ngôn ngữ"), ngoài card. Dữ liệu không chứa chữ đang gõ: hàng không đổi nên DiffUtil không bind lại
     * khi danh sách lọc, ô giữ con trỏ và bàn phím. Chữ gõ báo về màn qua `SettingsScreen.onSearchChanged`.
     */
    data class Search(override val id: String, @StringRes val hint: Int) : SettingsRow

    /** RS Task 3: tiêu đề + segmented bên phải (Figma 1.4 "Đơn vị nhiệt độ"). [selected] là chỉ số trong [options]. */
    data class Segmented(
        override val id: String,
        @StringRes val title: Int,
        val options: List<String>,
        val selected: Int
    ) : SettingsRow

    /** RS Task 7: card xem trước thời tiết đầu màn 1.4 (dữ liệu mẫu, xem [WeatherPreviewMock]). Chuỗi đã định dạng sẵn. */
    data class WeatherPreview(
        override val id: String,
        val city: String,
        val temperature: String,
        val condition: String,
        val highLow: String,
        /** (nhãn giờ, nhiệt độ) */
        val hourly: List<Pair<String, String>>
    ) : SettingsRow

    /** RS Task 8 + WI: lưới ô ảnh cài sẵn ở màn 2.2 (`WallpaperPresets`). [chosen] = chỉ số ô đang là nền, -1 nếu không ô nào. */
    data class WallpaperGrid(
        override val id: String,
        val tiles: List<WallpaperPreset>,
        val chosen: Int
    ) : SettingsRow

    /**
     * RS Task 2: card "Launcher hiện tại" đầu trang chủ (Figma 302:1522). [themeId] null = chưa chọn theme (ẩn ảnh, tên "Tùy chỉnh").
     * Badge + nút "Đặt làm mặc định" chỉ hiện khi ![isDefault]; nút chỉ hiện khi [canRequest]. Không thuộc card nhóm nào.
     */
    data class LauncherCard(
        override val id: String,
        val themeId: ThemeId?,
        val isDefault: Boolean,
        val canRequest: Boolean,
        /** HC: versionName cho chip "v1.0.0" của trạng thái "Đã mặc định" (ghi chú Figma 464:2648 mục 8: không có số build). */
        val versionName: String = ""
    ) : SettingsRow

    /** G2d: dòng chữ nhỏ ở cuối danh sách (phiên bản). Không thuộc nhóm nào. */
    data class Footer(override val id: String, val text: String) : SettingsRow

    /** HL-1: dòng mô tả đầu màn, canh trái, ngoài card (Figma 5.1). */
    data class Intro(override val id: String, val text: String) : SettingsRow

    /** HL-1: tiêu đề nhóm kèm nhãn viên thuốc bên phải ([badge] null = không nhãn). */
    data class HeaderBadge(override val id: String, @StringRes val title: Int, val badge: String?) : SettingsRow

    /**
     * RN: hàng một app (màn "Thay đổi tên ứng dụng"). Tên hiển thị là chuỗi động nên không dùng được [Value] (tiêu đề là `@StringRes`).
     * Dòng chính = [AppItem.label]; dòng phụ = "Tên gốc: …" khi đã đổi tên.
     */
    data class AppName(
        override val id: String,
        val app: AppItem,
        /** P3-4: chữ bên phải trước chevron (vd "Đã đổi"); null = không có. */
        val value: String? = null
    ) : SettingsRow

    /**
     * BG: card xin quyền ngoài nhóm (Figma 3.4 302:2488): ô icon cam + tiêu đề/mô tả màu cảnh báo + nút chính [action].
     * Bấm nút báo màn qua `SettingsScreen.onPermissionCardClicked`.
     */
    data class PermissionCard(
        override val id: String,
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val description: Int,
        @StringRes val action: Int
    ) : SettingsRow

    /** BG: hàng một app có công tắc (màn 3.4 "Theo ứng dụng"). Bấm cả hàng báo màn qua `SettingsScreen.onAppSwitchChanged`. */
    data class AppSwitch(override val id: String, val app: AppItem, val checked: Boolean) : SettingsRow

    /** LO: hai ô chọn kiểu hiển thị thư viện (Figma 3.2), ngoài nhóm. [selected]: 0 = Thư mục danh mục, 1 = Danh sách A–Z. */
    data class LibraryStyle(override val id: String, val selected: Int) : SettingsRow

    /** BG: hai ô chọn kiểu huy hiệu (Figma 3.4 302:2500), ngoài nhóm. [selected]: 0 = Số, 1 = Chấm. */
    data class BadgeStyle(override val id: String, val selected: Int) : SettingsRow

    /** TR: dòng giới thiệu + card xem trước tĩnh đầu màn 4.1 (Figma 302:2550), ngoài nhóm. */
    data class TransitionPreview(override val id: String) : SettingsRow

    /** P3-3: khung xem trước đầu màn Bộ biểu tượng (ảnh nền + 4 icon mẫu theo kiểu đang chọn). Bitmap so sánh theo tham chiếu. */
    data class IconStylePreview(
        override val id: String,
        val background: android.graphics.Bitmap?,
        val icons: List<android.graphics.Bitmap>
    ) : SettingsRow

    /** TR: ngắt hai card liền nhau không có tiêu đề nhóm giữa (Figma 4.1: card kiểu chuyển cảnh, card tốc độ cách nhau 20). Không vẽ gì. */
    data class CardBreak(override val id: String) : SettingsRow

    /** GT: thanh trượt 0..100; giá trị chỉ được ghi khi người dùng nhả tay. */
    data class Slider(
        override val id: String,
        @StringRes val title: Int,
        val value: Int,
        @StringRes val description: Int = 0
    ) : SettingsRow

    companion object {
        const val ID_DEFAULT_LAUNCHER = "default_launcher"
        const val ID_HIDDEN_APPS = "hidden_apps"
        const val ID_CONTROL_CENTER = "control_center" // G3d
        const val ID_SHOW_LABELS = "show_labels"
        const val ID_NAV_BAR_HIDDEN = "nav_bar_hidden" // PB Task 4
        const val ID_LO_ENABLED = "lo_enabled" // LO
        const val ID_LO_STYLE = "lo_style"
        const val ID_LO_AUTO_CATEGORIZE = "lo_auto_categorize"
        const val ID_LO_SUGGESTIONS = "lo_suggestions"
        const val ID_GRID_PRESET = "grid_preset"
        const val ID_BACKDROP = "backdrop"
        const val ID_GLASS_LEVEL = "glass_level"
        const val ID_ICON_STYLE = "icon_style" // G2b
        const val ID_ICON_TINT = "icon_tint" // G2b
        const val ID_ICON_PACK = "icon_pack" // IP
        const val ID_GLASS_CUSTOM_DOCK = "glass_custom_dock" // GS
        const val ID_GLASS_CUSTOM_PANELS = "glass_custom_panels"
        const val ID_GLASS_CUSTOM_SEARCH_BAR = "glass_custom_search_bar"
        const val ID_GLASS_CUSTOM_PILL = "glass_custom_pill"
        const val ID_GLASS_CUSTOM_ICON_MENU = "glass_custom_icon_menu" // SM
        const val ID_GLASS_LOOK = "glass_look" // GT: hàng mở màn Tinh chỉnh kính
        const val ID_GLASS_LOOK_REFRACTION = "glass_look_refraction" // GT: các hàng của màn đó
        const val ID_GLASS_LOOK_GLOSS = "glass_look_gloss"
        const val ID_GLASS_LOOK_BLUR = "glass_look_blur"
        const val ID_GLASS_LOOK_CORNER = "glass_look_corner"
        const val ID_GLASS_LOOK_RESET = "glass_look_reset"

        // M8d Task 6: khối "Tìm kiếm" (xem SearchSettingsBlock)
        const val ID_SEARCH_SETTINGS = "search_settings"
        const val ID_SEARCH_SHORTCUTS = "search_shortcuts"
        const val ID_SEARCH_SUGGESTIONS = "search_suggestions"
        const val ID_SEARCH_RECENT_APPS = "search_recent_apps" // P3-5
        const val ID_SEARCH_CLEAR = "search_clear"
        const val ID_SEARCH_CONTACTS = "search_contacts" // G3a

        const val ID_HUB_FOOTER = "hub_footer" // G2d

        const val ID_WEATHER_UNIT = "weather_unit" // WU
        // HT: công tắc Màn hình chính
        const val ID_HT_OPEN_HEADER = "ht_open_header"
        const val ID_HT_SEARCH_IN_HEADER = "ht_search_in_header"
        const val ID_HT_SWIPE_DOWN = "ht_swipe_down"
        const val ID_HT_SEARCH_PILL = "ht_search_pill"
        const val ID_HT_PAGE_DOTS = "ht_page_dots"
        const val ID_TV_TODAY = "tv_today" // TV: màn widget bên trái trang 1
        const val ID_WEATHER_CURRENT = "weather_current" // WX
        const val ID_WEATHER_ADD = "weather_add" // WX
        const val ID_WEATHER_NOTIFY_DAILY = "weather_notify_daily" // WN
        const val ID_WEATHER_NOTIFY_TOMORROW = "weather_notify_tomorrow" // WN

        /** WX: tiền tố id của hàng một thành phố đã thêm; id đầy đủ = tiền tố + mã địa điểm. */
        const val ID_WEATHER_PLACE_PREFIX = "weather_place:"

        // WX Task 4: màn Vị trí (1.5)
        const val ID_LOCATION_PERMISSION = "location_permission"
        const val ID_LOCATION_MODE = "location_mode"
        const val ID_LOCATION_UPDATE = "location_update"
        const val ID_LOCATION_CITY = "location_city"
    }
}
