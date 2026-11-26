package gambi.global.os26launcher.domain.model

/** G2b — kiểu icon người dùng chọn trong Cài đặt. Một lựa chọn duy nhất (Tô màu và 3D loại trừ nhau). */
enum class IconStyle {
    DEFAULT, TINTED, THREE_D,

    /** GI — Kính: tấm trắng mờ + hình trắng; không dùng màu tô. */
    GLASS;

    companion object {
        /** Tên lạ (bản cũ đổi tên) hoặc null -> DEFAULT, không crash Flow settings. */
        fun fromName(name: String?): IconStyle = entries.find { it.name == name } ?: DEFAULT
    }
}

/** G2b — màu tô của kiểu [IconStyle.TINTED]. [hue] theo độ (0..360), [saturation] 0..1; GRAPHITE không có sắc độ. */
enum class IconTint(val hue: Float, val saturation: Float) {
    BLUE(211f, 0.90f),
    PINK(345f, 0.80f),
    GREEN(135f, 0.70f),
    ORANGE(28f, 0.90f),
    GRAPHITE(0f, 0f);

    companion object {
        val DEFAULT = BLUE

        fun fromName(name: String?): IconTint = entries.find { it.name == name } ?: DEFAULT
    }
}

/** G2b — cặp kiểu + màu tô, dùng để so sánh "đã áp" với "hiện tại" (hook làm mới Home ở pha B). */
data class IconStyleKey(
    val style: IconStyle = IconStyle.DEFAULT,
    val tint: IconTint = IconTint.DEFAULT,
    /** IP: gói biểu tượng đang áp, dạng `package@versionCode`; rỗng = không dùng gói (khóa cache cũ giữ nguyên). */
    val pack: String = "",
    /**
     * 2026-11-23 (chủ dự án): thanh "Bo góc" của kính ([GlassLook.corner], 0..100, 50 = hình icon gốc) cũng bo icon app. Nằm trong khóa nên
     * đổi bo góc thì cache RAM/đĩa tách bản mới và Home dựng lại (RecreateGuard so khóa).
     */
    val corner: Int = GlassLook.DEFAULT.corner
)
