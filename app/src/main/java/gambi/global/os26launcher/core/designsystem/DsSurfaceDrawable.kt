package gambi.global.os26launcher.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.glass.GlassTuning
import gambi.global.os26launcher.domain.model.GlassLook
import kotlin.math.roundToInt

/**
 * DS3 Task 2: bề mặt kính v3 vẽ bằng các lớp gradient chồng nhau (quyết định 3: không blur nền, không drop shadow đen; inner
 * shadow của Figma xấp xỉ bằng gradient dọc ở mép trên/dưới). Bảng lớp: plan DS3 Task 2 Step 1.
 *
 * Shader dựng lại ở [onBoundsChange] và [refresh] (đổi bo góc, đổi độ bóng); [draw] không cấp phát, không bitmap.
 * Bo góc lấy từ [DsCorners] (Task 1b); độ bóng từ `GlassEnvironment.look` nếu [DsSurface.followsGloss] (quyết định 8).
 * Thường không tạo trực tiếp mà qua `View.applyDsSurface`, hàm đó lo listener và outline.
 *
 * [followGlassSettings] = false: bỏ qua thanh "Bo góc"/"Độ bóng" của kính, luôn vẽ ở bo gốc và độ bóng mặc định. Chỉ để catalog
 * debug đặt khối mẫu cạnh khối thật (không nhìn được hai màn cùng lúc trên một máy).
 *
 * [topCornersOnly] = true: chỉ bo hai góc trên, hai góc dưới vuông (bottom sheet, Task 7). Vẽ hình bo kéo dài xuống dưới khung một
 * đoạn bằng bán kính rồi cắt theo khung; outline cũng kéo dài như vậy nên clip của view vuông ở đáy.
 */
class DsSurfaceDrawable(
    private val context: Context,
    val surface: DsSurface,
    corner: DsCorner = DsCorner.Pill,
    private val followGlassSettings: Boolean = true,
    private val topCornersOnly: Boolean = false
) : Drawable() {

    var corner: DsCorner = corner
        private set

    private val density = context.resources.displayMetrics.density
    private val rect = RectF()
    private val strokeRect = RectF()

    /** Khung vẽ thật: bằng [rect], hoặc kéo dài xuống dưới một bán kính khi [topCornersOnly]. */
    private val shapeRect = RectF()
    private var radius = 0f
    private val layers = ArrayList<Paint>(6)
    private var stroke: Paint? = null
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (surface.darkPress) PRESS_DARK else PRESS_LIGHT
    }
    private var pressed = false

    /** Đổi cách bo rồi vẽ lại. */
    fun setCorner(corner: DsCorner) {
        this.corner = corner
        refresh()
    }

    /** Đọc lại bo góc và độ bóng hiện tại (listener của `applyDsSurface` gọi khi cấu hình kính đổi). Main thread. */
    fun refresh() {
        rebuild(bounds)
        invalidateSelf()
    }

    /** Bán kính bo đang dùng (px), cho outline và thành phần con cần khớp góc. */
    fun currentRadiusPx(): Float = radius

    override fun onBoundsChange(bounds: Rect) {
        rebuild(bounds)
    }

    private fun rebuild(b: Rect) {
        layers.clear()
        stroke = null
        rect.set(b)
        if (b.isEmpty) {
            radius = 0f
            return
        }
        val c = corner
        radius = if (!followGlassSettings && c is DsCorner.Role) {
            minOf(DsCorners.basePx(context, c.role), minOf(b.width(), b.height()) / 2f)
        } else {
            DsCorners.radiusPx(context, c, b.width(), b.height())
        }
        shapeRect.set(rect)
        if (topCornersOnly) shapeRect.bottom += radius
        val look = if (surface.followsGloss && followGlassSettings) GlassEnvironment.from(context).look else GlassLook.DEFAULT
        when (surface) {
            DsSurface.CARD -> {
                glassFill(b, GlassTuning.tint(look, color(R.color.ds_surface_fill)))
                edgeTop(b, GlassTuning.border(look, alpha(WHITE, 0.12f)), 3f)
                edgeBottom(b, alpha(BLACK, 0.08f), 18f)
            }

            DsSurface.CONTROL -> {
                glassFill(b, GlassTuning.tint(look, color(R.color.ds_surface_fill)))
                edgeTop(b, GlassTuning.border(look, alpha(WHITE, 0.22f)), 2f)
            }

            DsSurface.CONTROL_SELECTED -> {
                solid(color(R.color.ds_text_primary))
                edgeTop(b, alpha(WHITE, 0.22f), 2f)
            }

            DsSurface.SHEET -> {
                solid(GlassTuning.tint(look, color(R.color.ds_surface_sheet)))
                edgeTop(b, GlassTuning.border(look, alpha(WHITE, 0.12f)), 3f)
                edgeBottom(b, alpha(BLACK, 0.08f), 18f)
            }

            DsSurface.PRIMARY -> {
                layers += paint(
                    LinearGradient(
                        b.left.toFloat(), 0f, b.right.toFloat(), 0f,
                        color(R.color.ds_primary_start), color(R.color.ds_primary_end), Shader.TileMode.CLAMP
                    )
                )
                vertical(
                    b,
                    intArrayOf(alpha(WHITE, 0.18f), alpha(WHITE, 0.05f), alpha(WHITE, 0f), alpha(BLACK, 0.10f)),
                    floatArrayOf(0f, 0.3f, 0.55f, 1f)
                )
                edgeTop(b, alpha(WHITE, 0.30f), 3f)
                edgeBottom(b, alpha(BLACK, 0.12f), 8f)
            }

            DsSurface.BADGE_WARNING -> badge(b, color(R.color.ds_status_warning))
            DsSurface.BADGE_DANGER -> badge(b, color(R.color.ds_status_danger))
            DsSurface.BADGE_NEW -> badge(b, color(R.color.ds_accent))
        }
    }

    /** Nền CARD/CONTROL: màu đặc, radial tâm giữa trắng 0 → 1% (0,65) → 5% (1), dọc trắng 3,5% → 0 (0,4). */
    private fun glassFill(b: Rect, base: Int) {
        solid(base)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val rx = b.width() / 2f
        val radial = RadialGradient(
            cx, cy, rx,
            intArrayOf(alpha(WHITE, 0f), alpha(WHITE, 0.01f), alpha(WHITE, 0.05f)),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        // Figma vẽ radial khớp khung (elip): nén trục dọc theo tỉ lệ cao/rộng.
        radial.setLocalMatrix(Matrix().apply { setScale(1f, b.height() / b.width().toFloat(), cx, cy) })
        layers += paint(radial)
        vertical(b, intArrayOf(alpha(WHITE, 0.035f), alpha(WHITE, 0f), alpha(WHITE, 0f)), floatArrayOf(0f, 0.4f, 1f))
    }

    private fun badge(b: Rect, status: Int) {
        solid(color(R.color.ds_badge_base))
        vertical(b, intArrayOf(alpha(status, 0.26f), alpha(status, 0.12f)), floatArrayOf(0f, 1f))
        edgeTop(b, alpha(WHITE, 0.22f), 2f)
        val w = 0.5f * density
        strokeRect.set(rect)
        strokeRect.inset(w / 2f, w / 2f)
        stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            shader = LinearGradient(
                0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
                intArrayOf(alpha(WHITE, 0.19f), alpha(status, 0.07f), alpha(status, 0.21f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun solid(c: Int) {
        layers += Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
    }

    private fun vertical(b: Rect, colors: IntArray, positions: FloatArray) {
        layers += paint(LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), colors, positions, Shader.TileMode.CLAMP))
    }

    /** Xấp xỉ inner shadow mép trên: [c] ở mép, mờ về 0 sau [dp]. */
    private fun edgeTop(b: Rect, c: Int, dp: Float) {
        val top = b.top.toFloat()
        layers += paint(LinearGradient(0f, top, 0f, top + dp * density, c, c and 0x00FFFFFF, Shader.TileMode.CLAMP))
    }

    /** Xấp xỉ inner shadow mép dưới: [c] ở mép, mờ về 0 sau [dp]. */
    private fun edgeBottom(b: Rect, c: Int, dp: Float) {
        val bottom = b.bottom.toFloat()
        layers += paint(LinearGradient(0f, bottom, 0f, bottom - dp * density, c, c and 0x00FFFFFF, Shader.TileMode.CLAMP))
    }

    private fun paint(s: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = s }

    private fun color(id: Int) = context.getColor(id)

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val save = if (topCornersOnly) canvas.save().also { canvas.clipRect(rect) } else -1
        for (p in layers) canvas.drawRoundRect(shapeRect, radius, radius, p)
        stroke?.let {
            val r = (radius - it.strokeWidth / 2f).coerceAtLeast(0f)
            canvas.drawRoundRect(strokeRect, r, r, it)
        }
        if (pressed) canvas.drawRoundRect(shapeRect, radius, radius, pressPaint)
        if (save >= 0) canvas.restoreToCount(save)
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (topCornersOnly) {
            outline.setRoundRect(b.left, b.top, b.right, b.bottom + radius.toInt() + 1, radius)
        } else {
            outline.setRoundRect(b, radius)
        }
        // Bề mặt mờ một phần nhưng outline phải đặc để bóng glow của PRIMARY (elevation) hiện đủ; các bề mặt khác elevation 0.
        outline.alpha = 1f
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val now = state.contains(android.R.attr.state_pressed)
        if (now == pressed) return false
        pressed = now
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        // Disabled do view đặt alpha 0,4 (plan Task 2 Step 3); drawable không tự đổi độ mờ.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        /** Phủ lúc nhấn, 8% (quyết định 5, suy ra). */
        const val PRESS_LIGHT = 0x14FFFFFF
        const val PRESS_DARK = 0x14000000

        fun alpha(c: Int, fraction: Float): Int = ColorUtils.setAlphaComponent(c, (fraction * 255f).roundToInt())
    }
}
