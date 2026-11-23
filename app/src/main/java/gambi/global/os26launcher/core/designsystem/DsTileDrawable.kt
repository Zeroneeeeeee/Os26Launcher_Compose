package gambi.global.os26launcher.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import gambi.global.os26launcher.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * DS3 Task 3: nền ô icon v3 (Figma `v2/Tile/Fill/<màu>`): đáy `ds_tile_base`, gradient chéo màu 14% → 3%, đốm màu 20% ở mép trên,
 * đốm trắng 12% góc dưới phải, viền sáng mép trên 1 dp và tối mép dưới 4 dp. Bóng ngoài (HC Task 4, Figma 2026-11-18: đen 0.45 y 4
 * mờ 10 + đen 0.30 y 1 mờ 3) không vẽ ở đây mà bằng elevation của [DsIconTileView], dựa trên [getOutline].
 * Bo theo vai [DsCornerRole.TILE] (cố định 8 dp tới khi có cơ chế bo góc icon, quyết định 7). Shader dựng ở [onBoundsChange].
 */
class DsTileDrawable(private val context: Context, color: DsTileColor) : Drawable() {

    var color: DsTileColor = color
        set(value) {
            field = value
            rebuild(bounds)
            invalidateSelf()
        }

    private val density = context.resources.displayMetrics.density
    private val rect = RectF()
    private var radius = 0f
    private val layers = ArrayList<Paint>(6)

    override fun onBoundsChange(bounds: Rect) {
        rebuild(bounds)
    }

    private fun rebuild(b: Rect) {
        layers.clear()
        rect.set(b)
        if (b.isEmpty) return
        radius = min(DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.TILE), b.width(), b.height()), min(b.width(), b.height()) / 2f)
        val tint = context.getColor(color.tint)
        val side = min(b.width(), b.height()).toFloat()
        val l = b.left.toFloat()
        val t = b.top.toFloat()
        val w = b.width().toFloat()
        val h = b.height().toFloat()

        layers += Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = context.getColor(R.color.ds_tile_base) }
        layers += paint(LinearGradient(l, t, l + w, t + h, alpha(tint, 0.14f), alpha(tint, 0.03f), Shader.TileMode.CLAMP))
        layers += paint(RadialGradient(l + w * 0.5f, t + h * 0.08f, side * 0.83f, alpha(tint, 0.20f), tint and 0x00FFFFFF, Shader.TileMode.CLAMP))
        layers += paint(RadialGradient(l + w * 0.7f, t + h * 0.88f, side, alpha(WHITE, 0.12f), 0x00FFFFFF, Shader.TileMode.CLAMP))
        layers += paint(LinearGradient(0f, t, 0f, t + density, alpha(WHITE, 0.30f), 0x00FFFFFF, Shader.TileMode.CLAMP))
        val bottom = b.bottom.toFloat()
        layers += paint(LinearGradient(0f, bottom, 0f, bottom - 4f * density, alpha(BLACK, 0.25f), 0, Shader.TileMode.CLAMP))
    }

    private fun paint(s: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = s }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        for (p in layers) canvas.drawRoundRect(rect, radius, radius, p)
    }

    override fun getOutline(outline: Outline) {
        if (!rect.isEmpty) outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        fun alpha(c: Int, fraction: Float): Int = ColorUtils.setAlphaComponent(c, (fraction * 255f).roundToInt())
    }
}
