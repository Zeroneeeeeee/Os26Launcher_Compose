package gambi.global.os26launcher.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import gambi.global.os26launcher.domain.config.AppearanceConfig
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.SurfaceTone
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.config.enumFromName
import gambi.global.os26launcher.domain.model.GlassCustom
import gambi.global.os26launcher.domain.model.GlassElement
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconTint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import gambi.global.os26launcher.data.config.AppearanceKeys as K

/** CF — nhóm Giao diện trên `launcher_settings` (thay phần giao diện của `SettingsRepositoryImpl`). */
@Singleton
class AppearanceRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AppearanceRepository {

    override val config: Flow<AppearanceConfig> = dataStore.groupFlow(TAG, ::read)

    override val glassCustomSaved: Flow<Boolean> =
        dataStore.groupFlow(TAG) { prefs -> K.GLASS_CUSTOM_SAVED_KEYS.any { prefs.contains(it) } }

    override suspend fun update(transform: (AppearanceConfig) -> AppearanceConfig) =
        dataStore.updateGroup(::read, transform) { old, new -> write(old, new) }

    override suspend fun seedGlassCustom(custom: GlassCustom) {
        dataStore.edit { prefs -> GlassElement.entries.forEach { prefs[keyOf(it)] = custom[it] } }
    }

    private fun read(p: Preferences): AppearanceConfig {
        val d = AppearanceConfig()
        val dl = GlassLook.DEFAULT
        return AppearanceConfig(
            themeId = p[K.THEME_ID]?.let { name -> ThemeId.entries.firstOrNull { it.name == name } },
            wallpaperKind = enumFromName(p[K.WALLPAPER_KIND], d.wallpaperKind),
            wallpaperColor = p[K.WALLPAPER_COLOR] ?: d.wallpaperColor,
            wallpaperPreset = p[K.WALLPAPER_PRESET]?.takeIf { it.isNotBlank() }, // WI
            pickedImageName = p[K.PICKED_IMAGE_NAME]?.takeIf { it.isNotBlank() }, // P3-2
            // Không valueOf: tên lạ (bản cũ đổi tên) rơi về mặc định thay vì crash cả Flow.
            iconStyle = IconStyle.fromName(p[K.ICON_STYLE]),
            iconTint = IconTint.fromName(p[K.ICON_TINT]),
            iconBevel = p[K.ICON_BEVEL] ?: d.iconBevel,
            iconPack = p[K.ICON_PACK]?.takeIf { it.isNotBlank() },
            iconShape = enumFromName(p[K.ICON_SHAPE], d.iconShape),
            iconCorner = p.percent(K.ICON_CORNER, d.iconCorner),
            iconSize = p.percent(K.ICON_SIZE, d.iconSize),
            showLabels = p[K.SHOW_LABELS] ?: d.showLabels,
            glassLevel = enumFromName(p[K.GLASS_LEVEL], GlassLevelOverride.AUTO),
            glassCustom = GlassCustom(
                dock = p[K.GLASS_CUSTOM_DOCK] ?: false,
                panels = p[K.GLASS_CUSTOM_PANELS] ?: false,
                searchBar = p[K.GLASS_CUSTOM_SEARCH_BAR] ?: false,
                pill = p[K.GLASS_CUSTOM_PILL] ?: false,
                iconMenu = p[K.GLASS_CUSTOM_ICON_MENU] ?: false // SM
            ),
            // GT: khóa vắng -> mặc định; ngoài khoảng -> kẹp 0..100.
            glassLook = GlassLook(
                refraction = p.percent(K.GLASS_LOOK_REFRACTION, dl.refraction, GlassLook.MIN, GlassLook.MAX),
                gloss = p.percent(K.GLASS_LOOK_GLOSS, dl.gloss, GlassLook.MIN, GlassLook.MAX),
                blur = p.percent(K.GLASS_LOOK_BLUR, dl.blur, GlassLook.MIN, GlassLook.MAX),
                corner = p.percent(K.GLASS_LOOK_CORNER, dl.corner, GlassLook.MIN, GlassLook.MAX)
            ),
            glassTransparency = p.percent(K.GLASS_TRANSPARENCY, d.glassTransparency),
            glassTint = p[K.GLASS_TINT] ?: d.glassTint,
            surfaceTone = enumFromName(p[K.SURFACE_TONE], SurfaceTone.LIGHT)
        )
    }

    private fun MutablePreferences.write(old: AppearanceConfig, new: AppearanceConfig) {
        putNullableEnumIfChanged(K.THEME_ID, old.themeId, new.themeId)
        putEnumIfChanged(K.WALLPAPER_KIND, old.wallpaperKind, new.wallpaperKind)
        putIfChanged(K.WALLPAPER_COLOR, old.wallpaperColor, new.wallpaperColor)
        putNullableIfChanged(K.WALLPAPER_PRESET, old.wallpaperPreset, new.wallpaperPreset) // WI
        putNullableIfChanged(K.PICKED_IMAGE_NAME, old.pickedImageName, new.pickedImageName) // P3-2
        putEnumIfChanged(K.ICON_STYLE, old.iconStyle, new.iconStyle)
        putEnumIfChanged(K.ICON_TINT, old.iconTint, new.iconTint)
        putIfChanged(K.ICON_BEVEL, old.iconBevel, new.iconBevel)
        putNullableIfChanged(K.ICON_PACK, old.iconPack, new.iconPack)
        putEnumIfChanged(K.ICON_SHAPE, old.iconShape, new.iconShape)
        putIfChanged(K.ICON_CORNER, old.iconCorner, new.iconCorner)
        putIfChanged(K.ICON_SIZE, old.iconSize, new.iconSize)
        putIfChanged(K.SHOW_LABELS, old.showLabels, new.showLabels)
        putEnumIfChanged(K.GLASS_LEVEL, old.glassLevel, new.glassLevel)
        GlassElement.entries.forEach { putIfChanged(keyOf(it), old.glassCustom[it], new.glassCustom[it]) }
        putIfChanged(K.GLASS_LOOK_REFRACTION, old.glassLook.refraction, new.glassLook.refraction)
        putIfChanged(K.GLASS_LOOK_GLOSS, old.glassLook.gloss, new.glassLook.gloss)
        putIfChanged(K.GLASS_LOOK_BLUR, old.glassLook.blur, new.glassLook.blur)
        putIfChanged(K.GLASS_LOOK_CORNER, old.glassLook.corner, new.glassLook.corner)
        putIfChanged(K.GLASS_TRANSPARENCY, old.glassTransparency, new.glassTransparency)
        putIfChanged(K.GLASS_TINT, old.glassTint, new.glassTint)
        putEnumIfChanged(K.SURFACE_TONE, old.surfaceTone, new.surfaceTone)
    }

    private fun keyOf(element: GlassElement) = when (element) {
        GlassElement.DOCK -> K.GLASS_CUSTOM_DOCK
        GlassElement.PANELS -> K.GLASS_CUSTOM_PANELS
        GlassElement.SEARCH_BAR -> K.GLASS_CUSTOM_SEARCH_BAR
        GlassElement.PILL -> K.GLASS_CUSTOM_PILL
        GlassElement.ICON_MENU -> K.GLASS_CUSTOM_ICON_MENU
    }

    private companion object {
        const val TAG = "AppearanceRepo"
    }
}
