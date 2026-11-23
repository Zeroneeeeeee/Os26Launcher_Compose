package gambi.global.os26launcher.domain.model

/** G2b — kiểu icon người dùng chọn trong Cài đặt. Một lựa chọn duy nhất (Tô màu và 3D loại trừ nhau). */
enum class IconStyle {
    DEFAULT, TINTED, THREE_D,

    /** GI — Kính: tấm trắng mờ + hình trắng; không dùng màu tô. */
    GLASS,

    /**
     * CF — Trong suốt (theme MINIMAL): chỉ vẽ hình (lớp foreground của adaptive icon) giữ màu gốc, không ô nền, không kính.
     * Icon không tách được hình (legacy, foreground phủ kín) vẽ như [DEFAULT]. Màn 2.3 không liệt kê.
     */
    TRANSPARENT;

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
    /** CF Step 7: lớp nổi 3D chồng lên [style]. Đã chuẩn hóa: luôn false khi [style] là THREE_D (vốn nổi) để không tách cache vô ích. */
    val bevel: Boolean = false,
    /**
     * 2026-11-23 (chủ dự án): thanh "Bo góc" của kính ([GlassLook.corner], 0..100, 50 = hình icon gốc) cũng bo icon app. Nằm trong khóa nên
     * đổi bo góc thì cache RAM/đĩa tách bản mới và Home dựng lại (RecreateGuard so khóa).
     */
    val corner: Int = GlassLook.DEFAULT.corner
) {
    companion object {
        /** Chuẩn hóa cờ nổi theo kiểu: THREE_D tự nổi nên cờ không có nghĩa. */
        fun bevelFor(style: IconStyle, bevel: Boolean): Boolean = bevel && style != IconStyle.THREE_D
    }
}
