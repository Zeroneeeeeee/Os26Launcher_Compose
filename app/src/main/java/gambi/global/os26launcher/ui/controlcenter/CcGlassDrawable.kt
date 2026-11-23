package gambi.global.os26launcher.ui.controlcenter

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import gambi.global.os26launcher.R
import kotlin.math.min

/**
 * G3d: nền "kính" của mọi khối trong Trung tâm điều khiển kiểu iOS 26: lớp tô trong mờ cộng viền sáng mảnh, sáng nhất ở góc
 * trên trái và dưới phải, tối ở giữa (giả ánh sáng chiếu chéo lên mép kính).
 *
 * Hình dạng: [Shape.CIRCLE] (ô 1×1, nút), [Shape.PILL] (bo bằng nửa cạnh ngắn: thanh trượt, ô 2×1), [Shape.ROUNDED]
 * (bo [cornerRadius]; nếu âm thì bo theo tỉ lệ [CORNER_FRACTION] của cạnh ngắn — khối 2×2).
 */
class CcGlassDrawable(
    context: Context,
    var shape: Shape = Shape.ROUNDED,
    private val cornerRadius: Float = -1f,
    @ColorInt fill: Int = ContextCompat.getColor(context, R.color.cc_tile_bg),
    private val rim: Boolean = true
) : Drawable() {

    enum class Shape { CIRCLE, PILL, ROUNDED }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.cc_glass_rim)
    }
    private val rimColors = intArrayOf(
        ContextCompat.getColor(context, R.color.cc_glass_rim_strong),
        ContextCompat.getColor(context, R.color.cc_glass_rim_weak),
        ContextCompat.getColor(context, R.color.cc_glass_rim_weak),
        ContextCompat.getColor(context, R.color.cc_glass_rim_mid)
    )
    // CG: mặt kính cong bắt sáng ở đỉnh, dày hơn ở đáy. Hai gradient dọc vẽ trong cùng hình với lớp tô.
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightColor = ContextCompat.getColor(context, R.color.cc_glass_highlight)
    private val shadeColor = ContextCompat.getColor(context, R.color.cc_glass_shade)
    private val rect = RectF()

    @get:ColorInt
    var fillColor: Int
        get() = fillPaint.color
        set(value) {
            if (fillPaint.color == value) return
            fillPaint.color = value
            invalidateSelf()
        }

    /** Bán kính bo đang dùng với khung hiện tại. */
    fun radiusFor(w: Float, h: Float): Float = when (shape) {
        Shape.CIRCLE, Shape.PILL -> min(w, h) / 2f
        Shape.ROUNDED -> if (cornerRadius >= 0f) cornerRadius else min(w, h) * CORNER_FRACTION
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rimPaint.shader = LinearGradient(
            bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
            rimColors, RIM_STOPS, Shader.TileMode.CLAMP
        )
        val top = bounds.top.toFloat()
        val h = bounds.height().toFloat()
        highlightPaint.shader = LinearGradient(
            0f, top, 0f, top + h * HIGHLIGHT_FRACTION, highlightColor, highlightColor and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        shadePaint.shader = LinearGradient(
            0f, top + h * (1f - SHADE_FRACTION), 0f, top + h, shadeColor and 0x00FFFFFF, shadeColor, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (shape == Shape.CIRCLE) {
            val r = min(w, h) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, fillPaint)
            if (rim) {
                canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, highlightPaint)
                canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, shadePaint)
                canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r - rimPaint.strokeWidth / 2f, rimPaint)
            }
            return
        }
        val r = radiusFor(w, h)
        rect.set(b)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        if (rim) {
            canvas.drawRoundRect(rect, r, r, highlightPaint)
            canvas.drawRoundRect(rect, r, r, shadePaint)
            val inset = rimPaint.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, r - inset, r - inset, rimPaint)
        }
    }

    /** Không dùng: độ mờ của khối đi qua `View.alpha` (hoạt ảnh mở panel); đặt alpha paint sẽ mất độ trong của màu tô. */
    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        highlightPaint.colorFilter = colorFilter
        shadePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /**
         * CG: khối 2×2 bo 1/4 cạnh, xấp xỉ nửa cạnh ô 1×1 (bán kính ô tròn), nên các góc đồng tâm như iOS 26. Thẻ lớn (trang Nhạc,
         * bảng chi tiết) dùng `cc_block_radius` 38 dp, bằng nửa cạnh ô trên màn rộng 360–420 dp.
         */
        const val CORNER_FRACTION = 0.25f
        private val RIM_STOPS = floatArrayOf(0f, 0.35f, 0.65f, 1f)

        /** CG: ánh sáng đỉnh phủ 45% trên, bóng trong phủ 25% đáy. */
        private const val HIGHLIGHT_FRACTION = 0.45f
        private const val SHADE_FRACTION = 0.25f
    }
}
