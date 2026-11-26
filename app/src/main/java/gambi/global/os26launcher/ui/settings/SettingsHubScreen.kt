package gambi.global.os26launcher.ui.settings

import android.content.Intent
import android.widget.Toast
import gambi.global.os26launcher.ui.home.HomeEntry
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import gambi.global.os26launcher.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.ui.controlcenter.ControlCenterSettingsActivity
import gambi.global.os26launcher.ui.language.AppLanguages

/** Đích của một mục trên trang chủ: màn trong khung này, hoặc một Activity ngoài. */
sealed interface HubTarget {
    data class Screen(val id: SettingsScreenId) : HubTarget
    data class External(val activity: Class<*>) : HubTarget
}

/** Một mục (hàng) của trang chủ. [id] là khóa hàng, duy nhất. */
data class HubEntry(val id: String, @StringRes val title: Int, val target: HubTarget, val tile: SettingsTile) // RS: ô icon

data class HubGroup(val id: String, @StringRes val title: Int, val entries: List<HubEntry>)

/**
 * G2d — bảng nhóm và mục của trang chủ, theo đúng thứ tự bản nháp Figma. Mỗi mục một dòng. Mục có đích là màn chưa đăng ký trong
 * [SettingsScreens] bị ẩn (PB, 2026-11-18; trước đó hiện mờ "Sắp có"); nhánh làm màn đó chỉ cần đăng ký màn, không sửa bảng này.
 */
object SettingsHubLayout {
    private fun screen(id: SettingsScreenId, @StringRes title: Int, tile: SettingsTile) =
        HubEntry("hub_" + id.name.lowercase(), title, HubTarget.Screen(id), tile)

    // RS: màu ô icon từng hàng đọc từ Figma Home cài đặt 302:1492 (plan RS mục "Ô icon trang chủ").
    private fun tile(@DrawableRes icon: Int, color: DsTileColor) = SettingsTile(icon, color)

    val groups: List<HubGroup> = listOf(
        HubGroup(
            "general", R.string.settings_hub_group_general, listOf(
                HubEntry( // RS Task 6: màn 1.1 là Activity riêng (lưới thẻ + nút cố định), không qua khung danh sách
                    "hub_theme",
                    R.string.settings_hub_theme,
                    HubTarget.External(ThemeSettingsActivity::class.java),
                    tile(R.drawable.ds_ic_home, DsTileColor.BLUE)
                ),
                // HF: nhãn hàng "Chung" như Figma 302:1492 (dùng chung string tên nhóm); màn 1.2 vẫn dùng settings_hub_general
                screen(SettingsScreenId.GENERAL, R.string.settings_hub_group_general, tile(R.drawable.ds_ic_general, DsTileColor.SLATE)),
                screen(SettingsScreenId.LANGUAGE, R.string.settings_hub_language, tile(R.drawable.ds_ic_language, DsTileColor.SKY)),
                screen(SettingsScreenId.WEATHER, R.string.settings_hub_weather, tile(R.drawable.ds_ic_weather, DsTileColor.AMBER)),
                screen(SettingsScreenId.LOCATION, R.string.settings_hub_location, tile(R.drawable.ds_ic_location, DsTileColor.GREEN)),
                HubEntry( // G3d: mở màn cài đặt riêng của Trung tâm điều khiển
                    SettingsHubScreen.CONTROL_CENTER_ID,
                    R.string.cc_title,
                    HubTarget.External(ControlCenterSettingsActivity::class.java),
                    tile(R.drawable.ds_ic_page_scroll, DsTileColor.TEAL) // RS Q2: ô tạm, chưa có trong Figma; đổi cùng CC
                )
            )
        ),
        HubGroup(
            "appearance", R.string.settings_hub_group_appearance, listOf(
                // PG1 F4: "Mức kính" và công tắc kính từng phần tử không còn tác dụng ở bản Compose (JX Q3) nên đã ẩn; màn 2.1 chỉ còn một hàng
                // "Tinh chỉnh kính", nên hàng này mở thẳng màn đó. Giữ id "hub_glass" như `screen(SettingsScreenId.GLASS, ...)` tạo ra.
                HubEntry(
                    "hub_glass",
                    R.string.settings_hub_glass,
                    HubTarget.External(GlassTuningActivity::class.java),
                    tile(R.drawable.ds_ic_liquid_glass, DsTileColor.VIOLET)
                ),
                screen(SettingsScreenId.WALLPAPER, R.string.settings_hub_wallpaper, tile(R.drawable.ds_ic_wallpaper, DsTileColor.PINK)),
                screen(SettingsScreenId.ICON_SET, R.string.settings_hub_icon_set, tile(R.drawable.ds_ic_icon_pack, DsTileColor.ORANGE))
            )
        ),
        HubGroup(
            "home_screen", R.string.settings_hub_group_home_screen, listOf(
                screen(SettingsScreenId.SWIPE, R.string.settings_hub_swipe, tile(R.drawable.ds_ic_page_scroll, DsTileColor.TEAL)),
                screen(SettingsScreenId.LIBRARY, R.string.settings_hub_library, tile(R.drawable.ds_ic_app_library, DsTileColor.BLUE)),
                screen(SettingsScreenId.SEARCH, R.string.settings_hub_search, tile(R.drawable.ds_ic_search, DsTileColor.SLATE)),
                screen(SettingsScreenId.BADGES, R.string.settings_hub_badges, tile(R.drawable.ds_ic_badge, DsTileColor.RED))
            )
        ),
        HubGroup(
            "transitions", R.string.settings_hub_group_transitions, listOf(
                // P3-1 (2026-11-20): bỏ hàng "Hiệu ứng ứng dụng" (4.2), chủ dự án quyết định không làm.
                screen(SettingsScreenId.TRANSITIONS, R.string.settings_hub_transitions, tile(R.drawable.ds_ic_transition, DsTileColor.VIOLET))
            )
        ),
        HubGroup(
            "icons", R.string.settings_hub_group_icons, listOf(
                // HL-1: 5.1 là màn Cài đặt (id "hub_hidden_apps" = HIDDEN_APPS_ID), thay AppLibraryActivity.
                screen(SettingsScreenId.HIDDEN_APPS, R.string.settings_hidden_apps, tile(R.drawable.ds_ic_hidden_apps, DsTileColor.SLATE)),
                screen(SettingsScreenId.CHANGE_ICON, R.string.settings_hub_change_icon, tile(R.drawable.ds_ic_change_icon, DsTileColor.PINK)),
                screen(SettingsScreenId.RENAME_APPS, R.string.settings_hub_rename_apps, tile(R.drawable.ds_ic_rename, DsTileColor.ORANGE))
            )
        ),
        HubGroup(
            CONTACT_GROUP_ID, R.string.settings_hub_group_contact, listOf(
                screen(SettingsScreenId.RATE, R.string.settings_hub_rate, tile(R.drawable.ds_ic_rate, DsTileColor.AMBER)),
//                screen(SettingsScreenId.MORE_APPS, R.string.settings_hub_more_apps, tile(R.drawable.ds_ic_more_apps, DsTileColor.BLUE)),
                screen(SettingsScreenId.PRIVACY, R.string.settings_hub_privacy, tile(R.drawable.ds_ic_privacy, DsTileColor.GREEN))
            )
        )
    )

    /**
     * PB D3: nhóm Liên hệ (6.1–6.3) ẩn cả nhóm tới khi có dữ liệu thật (link cửa hàng, ứng dụng khác, chính sách). Bật lại = true.
     * HF (2026-11-18): bật lại để trang chủ đủ như Figma 302:1492; chức năng từng mục làm sau.
     */
    const val CONTACT_GROUP_ENABLED = true

    /**
     * Nhóm hiện trên trang chủ: bỏ nhóm bị tắt bằng cờ và mục bị ẩn do cấu hình; nhóm không còn mục nào thì bỏ cả nhóm.
     * HF (2026-11-18): mục chưa có màn vẫn hiện như Figma (thay PB D3 ẩn hẳn); chạm vào không làm gì ([SettingsHubScreen.onValueClicked]).
     */
    fun visibleGroups(isHidden: (HubEntry) -> Boolean): List<HubGroup> = groups
        .filter { it.id != CONTACT_GROUP_ID || CONTACT_GROUP_ENABLED }
        .map { g -> g.copy(entries = g.entries.filter { !isHidden(it) }) }
        .filter { it.entries.isNotEmpty() }

    private const val CONTACT_GROUP_ID = "contact"

    fun isAvailable(entry: HubEntry): Boolean = when (val t = entry.target) {
        is HubTarget.Screen -> SettingsScreens.isRegistered(t.id)
        is HubTarget.External -> true
    }
}

/**
 * G2d — trang chủ Cài đặt: hàng launcher mặc định, các nhóm của [SettingsHubLayout], chân trang phiên bản. Giữ kiểu hàng cũ
 * ([SettingsRow.Value]), chỉ đổi cấu trúc.
 */
class SettingsHubScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity

    // HC Task 3 (Q1): Figma ghi tên sản phẩm "Launcher OS"; dùng tên app để đổi tên chỉ sửa một chỗ.
    override val titleRes: Int = R.string.app_name

    // AB: hành động không màn đã dựng (vd hộp thoại Đánh giá), theo id; dismiss ở onDestroy.
    private val actions = HashMap<SettingsScreenId, SettingsAction>()

    // G3d: công tắc Trung tâm điều khiển nằm ở DataStore riêng, không qua SettingsViewModel; chỉ để hiện Bật/Tắt trên hàng.
    private val controlCenterPrefs: ControlCenterPrefsRepository = EntryPointAccessors.fromApplication(
        activity.applicationContext,
        HubEntryPoint::class.java
    ).controlCenterPrefs()
    private var controlCenterEnabled = false

    // HC Task 1: trạng thái mặc định lần dựng trước (null = chưa dựng) để biết lúc Chưa → Đã.
    private var lastDefault: Boolean? = null

    /** AB4: mục "Tải ứng dụng khác" ẩn hẳn (không mờ) khi `about_more_apps_enabled` = false. */
    private fun isHiddenByConfig(entry: HubEntry): Boolean =
        entry.target == HubTarget.Screen(SettingsScreenId.MORE_APPS) &&
            !activity.resources.getBoolean(R.bool.about_more_apps_enabled)

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>()
        // HC Task 1: vừa chuyển Chưa → Đã mặc định lúc màn đang mở (vd quay về từ bộ chọn launcher): toast một lần (ghi chú Figma 464:2648).
        if (lastDefault == false && state.isDefaultLauncher) {
            Toast.makeText(activity, R.string.hero_default_set_toast, Toast.LENGTH_SHORT).show()
        }
        lastDefault = state.isDefaultLauncher
        // RS Task 2 / HC Task 1: Hero Card 2 trạng thái thay hàng "Launcher mặc định".
        rows.add(
            SettingsRow.LauncherCard(
                SettingsRow.ID_DEFAULT_LAUNCHER,
                themeId = state.themeId,
                isDefault = state.isDefaultLauncher,
                canRequest = state.canRequestDefault,
                versionName = BuildConfig.VERSION_NAME
            )
        )
        // PB D3: mục chưa có màn và nhóm Liên hệ ẩn hẳn (trước PB: mờ "Sắp có").
        for (group in SettingsHubLayout.visibleGroups(::isHiddenByConfig)) {
            rows.add(SettingsRow.Header("hub_group_" + group.id, group.title))
            for (entry in group.entries) {
                val value = when {
                    // P3-7: KHÔNG hiện số app đã ẩn (dấu vết của app ẩn; sau này mục này có khóa vân tay / PIN).
                    entry.id == CONTROL_CENTER_ID ->
                        activity.getString(if (controlCenterEnabled) R.string.cc_state_on else R.string.cc_state_off)
                    entry.id == LANGUAGE_ID -> // LG, L7: tên bản ngữ của ngôn ngữ đã chọn, hoặc "Theo hệ thống"
                        AppLanguages.current()?.nativeName ?: activity.getString(R.string.lang_system)
                    // HF: chữ cố định như Figma 302:1492; chưa đọc đơn vị nhiệt độ / chế độ vị trí đang lưu
                    entry.id == WEATHER_ID -> activity.getString(R.string.settings_weather_unit_value_c)
                    entry.id == LOCATION_ID -> activity.getString(R.string.wx_loc_mode_auto)
                    else -> ""
                }
                rows.add(SettingsRow.Value(entry.id, entry.title, value, tile = entry.tile))
            }
        }
        rows.add(
            SettingsRow.Footer(
                SettingsRow.ID_HUB_FOOTER,
                activity.getString(R.string.settings_hub_version, BuildConfig.VERSION_NAME)
            )
        )
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) {
        if (row.id == SettingsRow.ID_DEFAULT_LAUNCHER) {
            ctx.defaultLauncher.request()
            return
        }
        val entry = SettingsHubLayout.groups.flatMap { it.entries }.firstOrNull { it.id == row.id } ?: return
        if (!SettingsHubLayout.isAvailable(entry)) return // HF: mục chưa có màn hiện như Figma nhưng chạm không làm gì
        when (val t = entry.target) {
            is HubTarget.Screen -> if (SettingsScreens.isAction(t.id)) {
                // Hành động không màn (6.1): dựng một lần, giữ để dismiss ở onDestroy, chạy lại mỗi lần chạm;
                // hành động tự bỏ qua lần chạm khi hộp thoại của nó còn mở.
                (actions[t.id] ?: SettingsScreens.createAction(t.id, ctx)?.also { actions[t.id] = it })?.run()
            } else {
                ctx.open(t.id)
            }
            is HubTarget.External -> activity.startActivity(Intent(activity, t.activity))
        }
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    /** HC Task 1 (chủ dự án 2026-11-18): "Trải nghiệm" / "Khám phá ›" mở lưới Home (thay mở bộ chọn launcher của RS). */
    override fun onLauncherCardClicked() = HomeEntry.open(activity)

    override suspend fun observe() {
        controlCenterPrefs.enabled.collect { on -> // G3d
            controlCenterEnabled = on
            ctx.rerender()
        }
    }

    override fun onDestroy() {
        actions.values.forEach { it.onDestroy() }
        actions.clear()
    }

    companion object {
        const val HIDDEN_APPS_ID = "hub_hidden_apps"
        const val CONTROL_CENTER_ID = "hub_control_center" // G3d
        val LANGUAGE_ID = "hub_" + SettingsScreenId.LANGUAGE.name.lowercase() // LG: id do SettingsHubLayout.screen() dựng
        val WEATHER_ID = "hub_" + SettingsScreenId.WEATHER.name.lowercase() // HF
        val LOCATION_ID = "hub_" + SettingsScreenId.LOCATION.name.lowercase() // HF
    }

    /** G3d: hub không do Hilt dựng nên lấy repository qua entry point (cùng cách với GlassEnvironment). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HubEntryPoint {
        fun controlCenterPrefs(): ControlCenterPrefsRepository
    }
}
