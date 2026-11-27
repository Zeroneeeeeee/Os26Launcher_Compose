package gambi.global.os26launcher.data.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * CF — mọi khóa của DataStore `launcher_settings`, mỗi nhóm một object. **Chuỗi khóa là dữ liệu người dùng: không đổi tên khóa đã có**
 * (khóa trước CF giữ nguyên chuỗi cũ). Thêm/đổi khóa phải sửa `docs/config-schema.md` cùng commit.
 */
internal object AppearanceKeys {
    val WALLPAPER_KIND = stringPreferencesKey("appearance_wallpaper_kind")
    val WALLPAPER_COLOR = intPreferencesKey("appearance_wallpaper_color")
    val WALLPAPER_PRESET = stringPreferencesKey("appearance_wallpaper_preset") // WI
    val PICKED_IMAGE_NAME = stringPreferencesKey("appearance_picked_image_name") // P3-2
    val ICON_STYLE = stringPreferencesKey("icon_style") // G2b
    val ICON_TINT = stringPreferencesKey("icon_tint") // G2b
    val ICON_PACK = stringPreferencesKey("icon_pack") // IP
    val ICON_SHAPE = stringPreferencesKey("appearance_icon_shape")
    val ICON_CORNER = intPreferencesKey("appearance_icon_corner")
    val ICON_SIZE = intPreferencesKey("appearance_icon_size")
    val SHOW_LABELS = booleanPreferencesKey("show_labels") // M8a
    val GLASS_LEVEL = stringPreferencesKey("glass_level_override")

    // GS; SM thêm ICON_MENU (không tính vào glassCustomSaved).
    val GLASS_CUSTOM_DOCK = booleanPreferencesKey("glass_custom_dock")
    val GLASS_CUSTOM_PANELS = booleanPreferencesKey("glass_custom_panels")
    val GLASS_CUSTOM_SEARCH_BAR = booleanPreferencesKey("glass_custom_search_bar")
    val GLASS_CUSTOM_PILL = booleanPreferencesKey("glass_custom_pill")
    val GLASS_CUSTOM_ICON_MENU = booleanPreferencesKey("glass_custom_icon_menu")

    // GT
    val GLASS_LOOK_REFRACTION = intPreferencesKey("glass_look_refraction")
    val GLASS_LOOK_GLOSS = intPreferencesKey("glass_look_gloss")
    val GLASS_LOOK_BLUR = intPreferencesKey("glass_look_blur")
    val GLASS_LOOK_CORNER = intPreferencesKey("glass_look_corner")

    val GLASS_TRANSPARENCY = intPreferencesKey("appearance_glass_transparency")
    val GLASS_TINT = intPreferencesKey("appearance_glass_tint")
    val SURFACE_TONE = stringPreferencesKey("appearance_surface_tone")

    /** GS D8: có ít nhất một khóa này = đã từng lưu công tắc. */
    val GLASS_CUSTOM_SAVED_KEYS = listOf(GLASS_CUSTOM_DOCK, GLASS_CUSTOM_PANELS, GLASS_CUSTOM_SEARCH_BAR, GLASS_CUSTOM_PILL)
}

internal object GeneralKeys {
    val GRID_PRESET = stringPreferencesKey("grid_preset") // M8a
    val NAV_BAR_HIDDEN = booleanPreferencesKey("general_nav_bar_hidden")
    val AUTO_ARRANGE = booleanPreferencesKey("general_auto_arrange")
    val BLUR_EFFECT = booleanPreferencesKey("general_blur_effect")
    val TEXT_FOLLOW_SYSTEM = booleanPreferencesKey("general_text_follow_system")
    val TEXT_SCALE = intPreferencesKey("general_text_scale")
    val HIDDEN_APPS_LOCK = booleanPreferencesKey("apps_hidden_lock")
}

internal object HomeScreenKeys {
    val PAGE_DOTS = booleanPreferencesKey("home_toggle_page_dots") // HT
    val TODAY_ENABLED = booleanPreferencesKey("home_today_enabled") // TV
    val SWIPE_DOWN_SEARCH = booleanPreferencesKey("home_toggle_swipe_down_search") // HT
    val SEARCH_PILL = booleanPreferencesKey("home_toggle_search_pill") // HT
    val PAGE_TRANSITION = stringPreferencesKey("home_page_transition")
    val LIBRARY_ENABLED = booleanPreferencesKey("home_library_enabled")
    val LIBRARY_STYLE = stringPreferencesKey("home_library_style")
    val LIBRARY_AUTO_CATEGORIZE = booleanPreferencesKey("home_library_auto_categorize")
    val LIBRARY_SUGGESTIONS = booleanPreferencesKey("home_library_suggestions")
    val NEW_APP_PLACEMENT = stringPreferencesKey("home_new_app_placement")
    val BADGES_ENABLED = booleanPreferencesKey("home_badges_enabled")
    val BADGE_STYLE = stringPreferencesKey("home_badge_style")
    val BADGE_DISABLED_APPS = stringSetPreferencesKey("home_badge_disabled_apps") // BG
}

internal object SearchKeys {
    val SYSTEM_SETTINGS = booleanPreferencesKey("search_system_settings") // M8d
    val SHORTCUTS = booleanPreferencesKey("search_shortcuts") // M8d
    /** P3-5: từ 2026-11-20 chỉ còn là "Gợi ý tìm kiếm" (tìm kiếm thịnh hành). Trước đó gồm cả app gần đây. */
    val SUGGESTIONS = booleanPreferencesKey("search_suggestions") // M8d
    /** P3-5: "App gần đây" tách khỏi [SUGGESTIONS]. Chưa ghi lần nào thì lấy theo [SUGGESTIONS] (người đã tắt vẫn thấy tắt). */
    val RECENT_APPS = booleanPreferencesKey("search_recent_apps_enabled")
    val CONTACTS = booleanPreferencesKey("search_contacts") // G3a
    val CONTACTS_ASKED = booleanPreferencesKey("search_contacts_asked") // G3a: chỉ là cờ, không phải dữ liệu danh bạ
    val FILES = booleanPreferencesKey("search_files")
    val WEB_SUGGESTIONS = booleanPreferencesKey("search_web_suggestions")
    val WEB_ENGINE = stringPreferencesKey("search_web_engine")

    /** Chỉ để dọn: bản đầu lưu lịch sử ở DataStore (bị Auto Backup sao lưu); giờ ở file noBackup. */
    val LEGACY_RECENT_APPS = stringPreferencesKey("search_recent_apps")
}

internal object MotionKeys {
    val TRANSITION_STYLE = stringPreferencesKey("motion_transition_style")
    val TRANSITION_SPEED = stringPreferencesKey("motion_transition_speed")
    val REDUCE = booleanPreferencesKey("motion_reduce")
    val APP_OPEN_EFFECT = stringPreferencesKey("motion_app_open_effect")
    val APP_OPEN_SPEED = intPreferencesKey("motion_app_open_speed")
    val PRESS_SHRINK = booleanPreferencesKey("motion_press_shrink")
    val HAPTIC_ON_OPEN = booleanPreferencesKey("motion_haptic_on_open")
}

internal object AppStateKeys {
    val ONBOARDING_DONE = booleanPreferencesKey("first_run_done")
    val TODAY_SEEDED = booleanPreferencesKey("today_seeded")
}
