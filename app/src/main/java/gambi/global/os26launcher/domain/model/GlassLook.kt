package gambi.global.os26launcher.domain.model

/** GT: bốn thông số chỉnh kính trong Cài đặt. Thứ tự enum = thứ tự hàng trên màn. */
enum class GlassLookParam { REFRACTION, GLOSS, BLUR, CORNER }

/**
 * GT: bộ thông số kính DÙNG CHUNG cho mọi bề mặt kính (dock, panel, thanh tìm kiếm, pill, menu icon, ô thư mục, ô nhóm, widget,
 * kính hover, nền icon app). Mỗi giá trị 0..100.
 * 2026-11-25 (chủ dự án): [DEFAULT] là độ mờ 0, bo góc 57 (trước đây 50/50 = giao diện trước GT). Mức "không đổi gì" của bo góc vẫn là
 * [NEUTRAL_CORNER] = 50, dùng làm mốc cho khóa cache icon.
 * Quy đổi sang số thật (hệ số phóng, bán kính mờ, màu) nằm ở `core/glass/GlassTuning`, để kính mờ và kính thật đọc cùng một chỗ.
 *
 * - [refraction] độ khúc xạ: nền phía sau được phóng to khi đi qua kính (0 = không phóng).
 * - [gloss] độ bóng: kính sáng lên (trên 50) hoặc tối đi (dưới 50); viền sáng mạnh/yếu theo.
 * - [blur] độ mờ của nền sau kính (50 = mức cũ).
 * - [corner] bo góc: hệ số nhân lên bán kính riêng của từng phần tử (50 = giữ nguyên, 0 = vuông, 100 = gấp đôi, không quá nửa cạnh ngắn).
 */
data class GlassLook(
    val refraction: Int = 0,
    val gloss: Int = 50,
    val blur: Int = 0,
    val corner: Int = 57
) {
    operator fun get(param: GlassLookParam): Int = when (param) {
        GlassLookParam.REFRACTION -> refraction
        GlassLookParam.GLOSS -> gloss
        GlassLookParam.BLUR -> blur
        GlassLookParam.CORNER -> corner
    }

    fun with(param: GlassLookParam, value: Int): GlassLook {
        val v = value.coerceIn(MIN, MAX)
        return when (param) {
            GlassLookParam.REFRACTION -> copy(refraction = v)
            GlassLookParam.GLOSS -> copy(gloss = v)
            GlassLookParam.BLUR -> copy(blur = v)
            GlassLookParam.CORNER -> copy(corner = v)
        }
    }

    companion object {
        const val MIN = 0
        const val MAX = 100
        val DEFAULT = GlassLook()

        /** Bo góc giữ nguyên bán kính / hình icon gốc (hệ số 1). Không đổi theo [DEFAULT]: khóa cache icon trên đĩa dựa vào mốc này. */
        const val NEUTRAL_CORNER = 50
    }
}
