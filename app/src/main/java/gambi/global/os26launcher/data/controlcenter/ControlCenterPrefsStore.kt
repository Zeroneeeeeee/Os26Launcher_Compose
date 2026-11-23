package gambi.global.os26launcher.data.controlcenter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTileType
import gambi.global.os26launcher.domain.model.controlcenter.EdgeConfig
import gambi.global.os26launcher.domain.model.controlcenter.NightShiftConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Delegate ở CẤP FILE (bài học DataStoreModule): trong object/class sẽ tạo nhiều instance cho cùng file và DataStore ném lỗi.
// File riêng `control_center` để G3d không đụng khóa cài đặt chung của launcher.
private val Context.controlCenterDataStore: DataStore<Preferences> by preferencesDataStore(name = "control_center")

/**
 * G3d: lưu [ControlCenterConfig]. Enum lưu bằng tên và đọc lại có dự phòng (bài học GS: thêm giá trị mới không làm hỏng dữ liệu
 * cũ). Danh sách ô lưu thành một chuỗi `b:FLASHLIGHT;a:pkg/cls/user;...`.
 */
@Singleton
class ControlCenterPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ControlCenterPrefsRepository {

    override val config: Flow<ControlCenterConfig> = context.controlCenterDataStore.data
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (ControlCenterConfig) -> ControlCenterConfig) {
        context.controlCenterDataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): ControlCenterConfig {
        val d = ControlCenterConfig()
        val de = d.edge
        val dn = d.nightShift
        return ControlCenterConfig(
            enabled = p[ENABLED] ?: d.enabled,
            edge = EdgeConfig(
                position = enumOr(p[EDGE_POSITION], de.position),
                lengthFraction = (p[EDGE_LENGTH] ?: de.lengthFraction).coerceIn(EdgeConfig.MIN_LENGTH, EdgeConfig.MAX_LENGTH),
                thicknessDp = (p[EDGE_THICKNESS] ?: de.thicknessDp)
                    .coerceIn(EdgeConfig.MIN_THICKNESS_DP, EdgeConfig.MAX_THICKNESS_DP),
                offsetFraction = (p[EDGE_OFFSET] ?: de.offsetFraction).coerceIn(0f, EdgeConfig.MAX_OFFSET),
                color = p[EDGE_COLOR] ?: de.color
            ),
            background = enumOr(p[BACKGROUND], d.background),
            blurBackground = p[BLUR] ?: d.blurBackground,
            vibrateOnOpen = p[VIBRATE] ?: d.vibrateOnOpen,
            tiles = p[TILES]?.let(::decodeTiles) ?: d.tiles,
            recorderPackage = p[RECORDER]?.takeIf { it.isNotEmpty() },
            nightShift = NightShiftConfig(
                enabled = p[NS_ENABLED] ?: dn.enabled,
                warmth = (p[NS_WARMTH] ?: dn.warmth).coerceIn(0f, 1f),
                scheduled = p[NS_SCHEDULED] ?: dn.scheduled,
                fromMinutes = (p[NS_FROM] ?: dn.fromMinutes).coerceIn(0, MINUTES_PER_DAY - 1),
                toMinutes = (p[NS_TO] ?: dn.toMinutes).coerceIn(0, MINUTES_PER_DAY - 1)
            )
        )
    }

    private fun write(p: MutablePreferences, c: ControlCenterConfig) {
        p[ENABLED] = c.enabled
        p[EDGE_POSITION] = c.edge.position.name
        p[EDGE_LENGTH] = c.edge.lengthFraction
        p[EDGE_THICKNESS] = c.edge.thicknessDp
        p[EDGE_OFFSET] = c.edge.offsetFraction
        p[EDGE_COLOR] = c.edge.color
        p[BACKGROUND] = c.background.name
        p[BLUR] = c.blurBackground
        p[VIBRATE] = c.vibrateOnOpen
        p[TILES] = encodeTiles(c.tiles)
        p[RECORDER] = c.recorderPackage.orEmpty()
        p[NS_ENABLED] = c.nightShift.enabled
        p[NS_WARMTH] = c.nightShift.warmth
        p[NS_SCHEDULED] = c.nightShift.scheduled
        p[NS_FROM] = c.nightShift.fromMinutes
        p[NS_TO] = c.nightShift.toMinutes
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun encodeTiles(tiles: List<CustomTile>): String = tiles.joinToString(TILE_SEP) {
        when (it) {
            is CustomTile.Builtin -> "b:${it.type.name}"
            is CustomTile.App -> "a:${it.packageName}/${it.className}/${it.userSerial}"
        }
    }

    /** Mục hỏng hoặc loại đã bỏ thì bỏ qua (không làm mất cả danh sách). Chuỗi rỗng = người dùng bỏ hết ô. */
    private fun decodeTiles(raw: String): List<CustomTile> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(TILE_SEP).mapNotNull { item ->
            when {
                item.startsWith("b:") -> CustomTileType.entries.firstOrNull { it.name == item.substring(2) }
                    ?.let { CustomTile.Builtin(it) }

                item.startsWith("a:") -> {
                    val parts = item.substring(2).split("/")
                    val user = parts.getOrNull(2)?.toLongOrNull()
                    if (parts.size == 3 && user != null) CustomTile.App(parts[0], parts[1], user) else null
                }

                else -> null
            }
        }.distinct().take(ControlCenterConfig.MAX_TILES)
    }

    private companion object {
        const val TILE_SEP = ";"
        const val MINUTES_PER_DAY = 24 * 60
        val ENABLED = booleanPreferencesKey("enabled")
        val EDGE_POSITION = stringPreferencesKey("edge_position")
        val EDGE_LENGTH = floatPreferencesKey("edge_length")
        val EDGE_THICKNESS = intPreferencesKey("edge_thickness_dp")
        val EDGE_OFFSET = floatPreferencesKey("edge_offset")
        val EDGE_COLOR = intPreferencesKey("edge_color")
        val BACKGROUND = stringPreferencesKey("background")
        val BLUR = booleanPreferencesKey("blur")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val TILES = stringPreferencesKey("tiles")
        val RECORDER = stringPreferencesKey("recorder_package")
        val NS_ENABLED = booleanPreferencesKey("night_shift")
        val NS_WARMTH = floatPreferencesKey("night_shift_warmth")
        val NS_SCHEDULED = booleanPreferencesKey("night_shift_scheduled")
        val NS_FROM = intPreferencesKey("night_shift_from")
        val NS_TO = intPreferencesKey("night_shift_to")
    }
}
