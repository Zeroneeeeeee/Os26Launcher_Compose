package gambi.global.os26launcher.data.search

import androidx.annotation.StringRes
import gambi.global.os26launcher.R

/**
 * M8d — danh mục cố định các mục Cài đặt hệ thống. [action] là chuỗi của `Settings.ACTION_*` (viết thẳng để không phụ thuộc
 * mức API của từng hằng; máy không có trình xử lý thì mục tự bị loại lúc dựng). Không có action cần extras hay quyền đặc biệt.
 */
internal object SystemSettingsCatalog {
    class Item(
        val id: String,
        val action: String,
        @StringRes val label: Int,
        @StringRes val keywords: Int
    )

    private fun item(id: String, action: String, @StringRes label: Int, @StringRes keywords: Int) =
        Item(id, "android.settings.$action", label, keywords)

    val items: List<Item> = listOf(
        item(
            "wifi",
            "WIFI_SETTINGS",
            R.string.search_extras_setting_wifi,
            R.string.search_extras_setting_wifi_kw
        ),
        item(
            "bluetooth",
            "BLUETOOTH_SETTINGS",
            R.string.search_extras_setting_bluetooth,
            R.string.search_extras_setting_bluetooth_kw
        ),
        item(
            "airplane",
            "AIRPLANE_MODE_SETTINGS",
            R.string.search_extras_setting_airplane,
            R.string.search_extras_setting_airplane_kw
        ),
        item(
            "mobile",
            "DATA_ROAMING_SETTINGS",
            R.string.search_extras_setting_mobile,
            R.string.search_extras_setting_mobile_kw
        ),
        item(
            "data_usage",
            "DATA_USAGE_SETTINGS",
            R.string.search_extras_setting_data_usage,
            R.string.search_extras_setting_data_usage_kw
        ),
        item(
            "display",
            "DISPLAY_SETTINGS",
            R.string.search_extras_setting_display,
            R.string.search_extras_setting_display_kw
        ),
        item(
            "sound",
            "SOUND_SETTINGS",
            R.string.search_extras_setting_sound,
            R.string.search_extras_setting_sound_kw
        ),
        item(
            "notification",
            "NOTIFICATION_SETTINGS",
            R.string.search_extras_setting_notification,
            R.string.search_extras_setting_notification_kw
        ),
        item(
            "dnd",
            "ZEN_MODE_SETTINGS",
            R.string.search_extras_setting_dnd,
            R.string.search_extras_setting_dnd_kw
        ),
        item(
            "battery",
            "BATTERY_SAVER_SETTINGS",
            R.string.search_extras_setting_battery,
            R.string.search_extras_setting_battery_kw
        ),
        item(
            "location",
            "LOCATION_SOURCE_SETTINGS",
            R.string.search_extras_setting_location,
            R.string.search_extras_setting_location_kw
        ),
        item(
            "datetime",
            "DATE_SETTINGS",
            R.string.search_extras_setting_datetime,
            R.string.search_extras_setting_datetime_kw
        ),
        item(
            "language",
            "LOCALE_SETTINGS",
            R.string.search_extras_setting_language,
            R.string.search_extras_setting_language_kw
        ),
        item(
            "storage",
            "INTERNAL_STORAGE_SETTINGS",
            R.string.search_extras_setting_storage,
            R.string.search_extras_setting_storage_kw
        ),
        item(
            "security",
            "SECURITY_SETTINGS",
            R.string.search_extras_setting_security,
            R.string.search_extras_setting_security_kw
        ),
        item(
            "privacy",
            "PRIVACY_SETTINGS",
            R.string.search_extras_setting_privacy,
            R.string.search_extras_setting_privacy_kw
        ),
        item(
            "accessibility",
            "ACCESSIBILITY_SETTINGS",
            R.string.search_extras_setting_accessibility,
            R.string.search_extras_setting_accessibility_kw
        ),
        item(
            "apps",
            "APPLICATION_SETTINGS",
            R.string.search_extras_setting_apps,
            R.string.search_extras_setting_apps_kw
        ),
        item(
            "default_apps",
            "MANAGE_DEFAULT_APPS_SETTINGS",
            R.string.search_extras_setting_default_apps,
            R.string.search_extras_setting_default_apps_kw
        ),
        item(
            "keyboard",
            "INPUT_METHOD_SETTINGS",
            R.string.search_extras_setting_keyboard,
            R.string.search_extras_setting_keyboard_kw
        ),
        item(
            "cast",
            "CAST_SETTINGS",
            R.string.search_extras_setting_cast,
            R.string.search_extras_setting_cast_kw
        ),
        item(
            "nfc",
            "NFC_SETTINGS",
            R.string.search_extras_setting_nfc,
            R.string.search_extras_setting_nfc_kw
        ),
        item(
            "vpn",
            "VPN_SETTINGS",
            R.string.search_extras_setting_vpn,
            R.string.search_extras_setting_vpn_kw
        ),
        item(
            "about",
            "DEVICE_INFO_SETTINGS",
            R.string.search_extras_setting_about,
            R.string.search_extras_setting_about_kw
        ),
        item(
            "home",
            "SETTINGS",
            R.string.search_extras_setting_home,
            R.string.search_extras_setting_home_kw
        )
    )
}
