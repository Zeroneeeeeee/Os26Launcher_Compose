package gambi.global.os26launcher.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import gambi.global.os26launcher.R
import kotlin.math.roundToInt

/**
 * DS3 Task 2: đường kẻ giữa hàng (Figma `v3 · Divider`): cao 1 dp, hai đầu mờ về 0, đoạn giữa 12%–88% đậm `ds_divider`.
 * Làm bằng code vì `<gradient>` XML chỉ có 3 điểm (không có đoạn bằng ở giữa).
 */
class DsDividerDrawable(context: Context) : Drawable() {

    private val color = context.getColor(R.color.ds_divider)
    private val heightPx = context.resources.getDimension(R.dimen.ds_divider).roundToInt().coerceAtLeast(1)
    private val paint = Paint()

    override fun onBoundsChange(bounds: Rect) {
        paint.shader = LinearGradient(
            bounds.left.toFloat(), 0f, bounds.right.toFloat(), 0f,
            intArrayOf(color and 0x00FFFFFF, color, color, color and 0x00FFFFFF),
            floatArrayOf(0f, 0.12f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun getIntrinsicHeight(): Int = heightPx

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
