package gambi.global.os26launcher.domain.model.controlcenter

/**
 * G3d: toàn bộ cài đặt của Trung tâm điều khiển (RE mục 5.4). Bất biến; `ControlCenterPrefsRepository` lưu ở DataStore riêng.
 */
data class ControlCenterConfig(
    val enabled: Boolean = false,
    val edge: EdgeConfig = EdgeConfig(),
    val background: BackgroundMode = BackgroundMode.NONE,
    val blurBackground: Boolean = true,
    val vibrateOnOpen: Boolean = true,
    val tiles: List<CustomTile> = DEFAULT_TILES,
    /** Package app ghi màn hình cho ô [CustomTileType.SCREEN_RECORD]; null = chưa chọn. */
    val recorderPackage: String? = null,
    val nightShift: NightShiftConfig = NightShiftConfig()
) {
    companion object {
        /** Tối đa 12 ô, 4 ô một hàng (RE mục 5.1). */
        const val MAX_TILES = 12

        /** Mặc định của app mẫu (RE mục 5.3), bỏ Ghi màn hình vì chưa có app được chọn. */
        val DEFAULT_TILES: List<CustomTile> = listOf(
            CustomTile.Builtin(CustomTileType.FLASHLIGHT),
            CustomTile.Builtin(CustomTileType.TIMER),
            CustomTile.Builtin(CustomTileType.CALCULATOR),
            CustomTile.Builtin(CustomTileType.CAMERA),
            CustomTile.Builtin(CustomTileType.DARK_MODE),
            CustomTile.Builtin(CustomTileType.BATTERY),
            CustomTile.Builtin(CustomTileType.VOICE_MEMO),
            CustomTile.Builtin(CustomTileType.SETTINGS),
            // TF (chủ dự án 2026-11-23, "giống iPhone"): thêm Ghi chú và Phản chiếu màn hình. Chỉ áp cho người chưa tự sắp ô (khóa vắng).
            CustomTile.Builtin(CustomTileType.NOTES),
            CustomTile.Builtin(CustomTileType.SCREEN_MIRROR)
        )
    }
}

/** G3d: vị trí dải mép (RE mục 5.4, bốn vị trí của app mẫu). */
enum class EdgePosition {
    /** Mép trên, góc phải (mặc định, gần iOS nhất). */
    TOP_RIGHT,

    /** Mép trên, góc trái. */
    TOP_LEFT,

    /** Mép phải, dải dọc gần đỉnh màn hình. */
    RIGHT_SIDE,

    /** Mép trái, dải dọc gần đỉnh màn hình. */
    LEFT_SIDE
}

/**
 * G3d: hình dạng dải mép. [lengthFraction]: độ dài theo cạnh màn hình (0,15..0,6). [thicknessDp]: độ dày vùng chạm.
 * [offsetFraction]: với dải dọc, khoảng cách từ đỉnh màn hình theo chiều cao (0..0,6). [color]: màu vạch nhìn thấy (ARGB).
 */
data class EdgeConfig(
    val position: EdgePosition = EdgePosition.TOP_RIGHT,
    val lengthFraction: Float = 0.35f,
    val thicknessDp: Int = 22,
    val offsetFraction: Float = 0.1f,
    val color: Int = DEFAULT_COLOR
) {
    companion object {
        const val DEFAULT_COLOR = 0x59FFFFFF
        const val MIN_LENGTH = 0.15f
        const val MAX_LENGTH = 0.6f
        const val MIN_THICKNESS_DP = 12
        const val MAX_THICKNESS_DP = 40
        const val MAX_OFFSET = 0.6f
    }
}

/** G3d: nền panel (RE mục 5.4: `wallpaper_ct` / `state_wall`). */
enum class BackgroundMode {
    /** Ảnh nền của launcher. */
    WALLPAPER,

    /** Ảnh người dùng chọn riêng cho Trung tâm điều khiển. */
    CUSTOM_IMAGE,

    /** Không ảnh: chỉ lớp tối, thấy app phía sau. */
    NONE
}

/**
 * G3d: Night Shift = lớp phủ màu ấm trên toàn màn hình (RE mục 5.4). [warmth] 0..1. Giờ tính bằng phút từ 0:00.
 */
data class NightShiftConfig(
    val enabled: Boolean = false,
    val warmth: Float = 0.5f,
    val scheduled: Boolean = false,
    val fromMinutes: Int = 22 * 60,
    val toMinutes: Int = 7 * 60
) {
    /** Lớp phủ có nên hiện vào phút [nowMinutes] trong ngày không. Lịch qua nửa đêm (22:00 → 7:00) tính đúng. */
    fun activeAt(nowMinutes: Int): Boolean {
        if (enabled) return true
        if (!scheduled || fromMinutes == toMinutes) return false
        return if (fromMinutes < toMinutes) nowMinutes in fromMinutes until toMinutes
        else nowMinutes >= fromMinutes || nowMinutes < toMinutes
    }
}

/** G3d: các loại ô tùy chỉnh dựng sẵn (RE mục 5.3; bỏ "Chụp màn hình" vì app thường không chụp được). */
enum class CustomTileType {
    FLASHLIGHT,
    TIMER,
    CALCULATOR,
    CAMERA,
    SCREEN_RECORD,
    BATTERY,
    VOICE_MEMO,
    SETTINGS,
    DARK_MODE,
    NOTES,
    NIGHT_SHIFT,

    /** TF: phản chiếu màn hình (Smart View / Cast); app thường không tự bật được nên mở màn truyền của hệ thống. */
    SCREEN_MIRROR
}

/** G3d: một ô tùy chỉnh: loại dựng sẵn, hoặc lối tắt mở một app. */
sealed interface CustomTile {
    data class Builtin(val type: CustomTileType) : CustomTile

    /** App bất kỳ; [userSerial] để mở đúng user profile. */
    data class App(val packageName: String, val className: String, val userSerial: Long) : CustomTile
}
