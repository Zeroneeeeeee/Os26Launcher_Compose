package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassLook
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * GT: chỗ DUY NHẤT quy đổi [GlassLook] (0..100) sang số thật cho cả hai cách vẽ: kính mờ tại chỗ ([BackdropBlurDrawable],
 * [StaticBlurSurface]) và kính thật ([RealGlassSurface]). Hàm thuần, không cấp phát; gọi lúc dựng bề mặt (đổi thông số thì Home
 * dựng lại, xem `DockController.glassOverrideChanged`). Mọi hàm trả đúng giá trị cũ khi thông số ở mặc định.
 */
object GlassTuning {

    // ----- Độ khúc xạ -----

    /** Kính mờ: hệ số phóng nền quanh tâm phần tử (1 = không phóng). */
    fun zoom(look: GlassLook): Float = 1f + MAX_EXTRA_ZOOM * look.refraction / 100f

    /** Kính thật: `refractionHeight` của thư viện, từ giá trị gốc [base] của view lên [REAL_REFRACTION_MAX]. */
    fun refractionHeight(look: GlassLook, base: Float): Float =
        base + (REAL_REFRACTION_MAX - base).coerceAtLeast(0f) * look.refraction / 100f

    // ----- Độ mờ -----

    /** Kính mờ: bán kính box blur (px của bản nhỏ 25%) trong [BlurredBackdropCache]. 50 -> [BASE_BLUR_RADIUS_PX]. */
    fun blurRadiusPx(look: GlassLook): Int =
        if (look.blur <= 50) (BASE_BLUR_RADIUS_PX * look.blur / 50f).roundToInt()
        else BASE_BLUR_RADIUS_PX + ((MAX_BLUR_RADIUS_PX - BASE_BLUR_RADIUS_PX) * (look.blur - 50) / 50f).roundToInt()

    /** Kính thật: `blurAmount` của thư viện, 0 .. [base] (ở 50) .. [REAL_BLUR_MAX]. */
    fun blurAmount(look: GlassLook, base: Float): Float =
        if (look.blur <= 50) base * look.blur / 50f
        else base + (REAL_BLUR_MAX - base).coerceAtLeast(0f) * (look.blur - 50) / 50f

    // ----- Bo góc -----

    /** Hệ số nhân bán kính: 0 .. 1 (ở 50) .. 2. */
    fun cornerScale(look: GlassLook): Float = look.corner / 50f

    /** Bán kính sau khi nhân, không quá nửa cạnh ngắn của khung [width] x [height] (khung chưa biết cỡ thì truyền số <= 0). */
    fun cornerRadius(look: GlassLook, baseRadiusPx: Float, width: Float, height: Float): Float {
        val scaled = baseRadiusPx * cornerScale(look)
        return if (width > 0f && height > 0f) min(scaled, min(width, height) / 2f) else scaled
    }

    // ----- Độ bóng -----

    /**
     * Tint của kính sau khi phủ thêm một lớp trắng (bóng > 50) hoặc đen (bóng < 50), gộp thành MỘT màu ARGB tương đương hai lớp
     * SRC_OVER chồng nhau, nên số lệnh vẽ không đổi (bài học GL Task 6a).
     */
    fun tint(look: GlassLook, base: Int): Int {
        val k = (look.gloss - 50) / 50f
        if (k == 0f) return base
        val overA = MAX_GLOSS_ALPHA * (if (k > 0f) k else -k)
        val over = if (k > 0f) 255 else 0
        val baseA = (base ushr 24) / 255f
        val outA = overA + baseA * (1f - overA)
        if (outA <= 0f) return 0
        fun ch(shift: Int): Int {
            val c = (base ushr shift) and 0xFF
            return ((over * overA + c * baseA * (1f - overA)) / outA).roundToInt().coerceIn(0, 255)
        }
        return ((outA * 255f).roundToInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Viền sáng: alpha nhân theo độ bóng (0 = mất viền, 50 = giữ nguyên, 100 = gấp đôi). */
    fun border(look: GlassLook, base: Int): Int {
        val a = ((base ushr 24) * look.gloss / 50f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (base and 0x00FFFFFF)
    }

    // ----- Kính Compose Backdrop (JD Task 2) -----
    // Ở GlassLook.DEFAULT (refraction 0, gloss 50, blur 50) trả đúng số JC đã kiểm: blur 16 dp, lens 12/24 dp, viền sáng mặc định của
    // thư viện (Highlight.Default, alpha 1).

    /** Backdrop: bán kính `blur` (dp). 0 -> 0, 50 -> 16, 100 -> 32. */
    fun backdropBlurDp(look: GlassLook): Float = BACKDROP_BLUR_DP * look.blur / 50f

    /** Backdrop: `lens(refractionHeight, refractionAmount)` (dp). Khúc xạ 0 -> 12/24 (số JC), 100 -> 24/64. Nơi gọi kẹp chiều cao ≤ bán kính góc. */
    fun backdropLensDp(look: GlassLook): Pair<Float, Float> {
        val t = look.refraction / 100f
        return Pair(
            LENS_HEIGHT_DP + (LENS_HEIGHT_MAX_DP - LENS_HEIGHT_DP) * t,
            LENS_AMOUNT_DP + (LENS_AMOUNT_MAX_DP - LENS_AMOUNT_DP) * t
        )
    }

    /** Backdrop: alpha viền sáng (`Highlight.alpha`). Bóng 0 -> 0, 50 trở lên -> 1 (mặc định thư viện). */
    fun backdropHighlightAlpha(look: GlassLook): Float = (look.gloss / 50f).coerceIn(0f, 1f)

    /** Backdrop: hệ số độ dày viền sáng so với mặc định thư viện (0,5 dp). Bóng ≤ 50 -> 1, 100 -> 2. */
    fun backdropHighlightWidthScale(look: GlassLook): Float = (look.gloss / 50f).coerceAtLeast(1f)

    // CHỈNH TAY (GT): các mức trần, chưa kiểm bằng mắt trên máy.
    private const val MAX_EXTRA_ZOOM = 0.5f // khúc xạ 100 = nền phóng 1,5 lần
    private const val REAL_REFRACTION_MAX = 300f // trần `refractionHeight` theo tài liệu thư viện
    private const val BASE_BLUR_RADIUS_PX = BlurredBackdropCache.DEFAULT_BLUR_RADIUS_PX
    private const val MAX_BLUR_RADIUS_PX = 8
    private const val REAL_BLUR_MAX = 0.25f
    private const val MAX_GLOSS_ALPHA = 0.35f // lớp trắng/đen dày nhất phủ lên tint

    // JD: kính Backdrop. Mức đầu (blur 16, lens 12/24) là số JC; mức trần chỉnh tay, kiểm bằng mắt trên Oppo (Task 2 Step 6).
    private const val BACKDROP_BLUR_DP = 16f
    private const val LENS_HEIGHT_DP = 12f
    private const val LENS_HEIGHT_MAX_DP = 24f
    private const val LENS_AMOUNT_DP = 24f
    private const val LENS_AMOUNT_MAX_DP = 64f
}
