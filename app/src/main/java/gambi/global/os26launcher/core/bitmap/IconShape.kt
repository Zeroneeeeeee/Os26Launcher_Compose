package gambi.global.os26launcher.core.bitmap

import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Hình icon kiểu iOS: hình vuông bo góc "liên tục" (độ cong tăng dần từ cạnh thẳng vào cung, không nhảy bậc như
 * bo tròn thường).
 *
 * Mỗi góc gồm: Bézier bậc 3 (cạnh thẳng -> cung) + một cung tròn ngắn ở giữa + Bézier bậc 3 (cung -> cạnh kế).
 * Dựng theo công thức "corner smoothing" công khai của Figma (bài viết "Desperately seeking squircles" và thư viện
 * figma-squircle); đây là bản tự viết lại, không dùng thư viện. [smoothing] = 0 suy biến thành bo tròn thường
 * (cung tròn bán kính [cornerRatio] x cạnh), 1 = trơn tối đa.
 *
 * Thuần `android.graphics`, an toàn gọi từ mọi luồng: [path] tạo [Path] mới mỗi lần, không giữ trạng thái.
 */
class IconShape(val cornerRatio: Float, val smoothing: Float) {

    /** Đường kín trong hộp `0..sizePx` (cả hai trục). */
    fun path(sizePx: Int): Path {
        val size = sizePx.toDouble()
        val radius = (cornerRatio * size).coerceIn(0.0, size / 2)
        val path = Path()
        if (radius <= 0.0) {
            path.addRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), Path.Direction.CW)
            return path
        }
        // Độ dài cạnh bị cong chiếm mỗi góc: p = (1 + smoothing) x R, không vượt nửa cạnh.
        val xi = smoothing.toDouble().coerceIn(0.0, 1.0).coerceAtMost(size / 2 / radius - 1)
        val p = (1 + xi) * radius

        val arcMeasure = Math.toRadians(90.0 * (1 - xi))
        val arcSection = sin(arcMeasure / 2) * radius * sqrt(2.0)
        val angleAlpha = (Math.PI / 2 - arcMeasure) / 2
        val p3ToP4 = radius * tan(angleAlpha / 2)
        val angleBeta = Math.PI / 4 * xi
        val c = p3ToP4 * cos(angleBeta)
        val d = c * tan(angleBeta)
        val b = (p - arcSection - c - d) / 3
        val a = 2 * b
        // Hệ số điều khiển của Bézier xấp xỉ cung tròn góc arcMeasure.
        val k = 4.0 / 3.0 * tan(arcMeasure / 4) * radius

        val corner = Corner(path, a, b, c, d, arcSection, angleBeta, k)

        // Đi theo chiều kim đồng hồ, bắt đầu cuối góc trên-trái.
        path.moveTo(p.toFloat(), 0f)
        path.lineTo((size - p).toFloat(), 0f)
        corner.append(size - p, 0.0, 1.0, 0.0, 0.0, 1.0)           // trên-phải: đi +x rồi rẽ +y
        path.lineTo(size.toFloat(), (size - p).toFloat())
        corner.append(size, size - p, 0.0, 1.0, -1.0, 0.0)         // dưới-phải: đi +y rồi rẽ -x
        path.lineTo(p.toFloat(), size.toFloat())
        corner.append(p, size, -1.0, 0.0, 0.0, -1.0)               // dưới-trái: đi -x rồi rẽ -y
        path.lineTo(0f, p.toFloat())
        corner.append(0.0, p, 0.0, -1.0, 1.0, 0.0)                 // trên-trái: đi -y rồi rẽ +x
        path.close()
        return path
    }

    /**
     * Một góc trong hệ tọa độ cục bộ: gốc tại điểm bắt đầu cong trên cạnh, trục u = hướng đi dọc cạnh, trục v =
     * hướng sau khi rẽ. Cùng một dãy lệnh cho cả 4 góc, chỉ khác (gốc, u, v).
     */
    private class Corner(
        private val path: Path,
        private val a: Double, private val b: Double, private val c: Double, private val d: Double,
        private val arcSection: Double, private val beta: Double, private val k: Double
    ) {
        fun append(ox: Double, oy: Double, ux: Double, uy: Double, vx: Double, vy: Double) {
            fun x(lu: Double, lv: Double) = (ox + lu * ux + lv * vx).toFloat()
            fun y(lu: Double, lv: Double) = (oy + lu * uy + lv * vy).toFloat()

            // 1) Bézier: cạnh thẳng -> đầu cung. Kết thúc tại (a+b+c, d) với tiếp tuyến góc beta.
            val e1u = a + b + c
            val e1v = d
            path.cubicTo(
                x(a, 0.0),
                y(a, 0.0),
                x(a + b, 0.0),
                y(a + b, 0.0),
                x(e1u, e1v),
                y(e1u, e1v)
            )

            // 2) Cung tròn ngắn, xấp xỉ bằng một Bézier: tiếp tuyến vào (cos b, sin b), ra (sin b, cos b).
            val e2u = e1u + arcSection
            val e2v = e1v + arcSection
            path.cubicTo(
                x(e1u + k * cos(beta), e1v + k * sin(beta)),
                y(e1u + k * cos(beta), e1v + k * sin(beta)),
                x(e2u - k * sin(beta), e2v - k * cos(beta)),
                y(e2u - k * sin(beta), e2v - k * cos(beta)),
                x(e2u, e2v),
                y(e2u, e2v)
            )

            // 3) Bézier: cuối cung -> cạnh kế (đối xứng với bước 1 qua đường chéo u = v).
            path.cubicTo(
                x(e2u + d, e2v + c), y(e2u + d, e2v + c),
                x(e2u + d, e2v + b + c), y(e2u + d, e2v + b + c),
                x(e2u + d, e2v + a + b + c), y(e2u + d, e2v + a + b + c)
            )
        }
    }

    companion object {
        /** CHỈNH TAY: bán kính / cạnh. 0,2237 là tỉ lệ thường trích từ lưới icon iOS 1024px, chưa đối chiếu tài liệu Apple. */
        const val CORNER_RATIO = 0.2237f

        /** CHỈNH TAY: 0 = bo tròn thường, 0,6 = mức mặc định của corner smoothing trong Figma. */
        const val SMOOTHING = 0.6f

        val DEFAULT = IconShape(CORNER_RATIO, SMOOTHING)

        /** Giá trị "Bo góc" (0..100) giữ nguyên hình [DEFAULT]. */
        const val DEFAULT_CORNER = 50

        /**
         * 2026-11-23: hình icon theo thanh "Bo góc" của kính ([corner] 0..100): bán kính = [CORNER_RATIO] × corner / 50 (0 = vuông, 50 = [DEFAULT],
         * 100 ≈ gần tròn), kẹp ≤ nửa cạnh. Cùng hệ số nhân với `GlassTuning.cornerScale`.
         */
        fun forCorner(corner: Int): IconShape =
            if (corner == DEFAULT_CORNER) DEFAULT
            else IconShape((CORNER_RATIO * corner.coerceIn(0, 100) / DEFAULT_CORNER).coerceAtMost(0.5f), SMOOTHING)
    }
}
