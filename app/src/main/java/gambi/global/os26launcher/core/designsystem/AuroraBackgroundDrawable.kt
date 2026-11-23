package gambi.global.os26launcher.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import gambi.global.os26launcher.R
import kotlin.math.roundToInt

/**
 * DS3 Task 2: nền tối "aurora" của màn v3: `ds_bg_base` cộng 4 đốm màu loang (radial màu → trong suốt). Tâm và bán kính tính
 * theo khung Figma 360 × 800 rồi quy theo bề rộng/bề cao thật. Số độ mờ và bán kính là điểm xuất phát (Figma: ellipse 168 × 268
 * blur 170), CHỈNH THEO MẮT ở Task 2 Step 9, ghi số cuối vào Nhật ký plan DS3.
 *
 * OB: bộ đốm truyền vào được ([AuroraSpots]); mặc định giữ y hệt DS3 (catalog, các màn v3 khác không đổi).
 */
class AuroraBackgroundDrawable(
    private val context: Context,
    private val spots: List<AuroraSpot> = AuroraSpots.DEFAULT
) : Drawable() {

    private val base = Paint().apply { color = context.getColor(R.color.ds_bg_base) }
    private val paints = spots.map { Paint(Paint.ANTI_ALIAS_FLAG) }

    override fun onBoundsChange(bounds: Rect) {
        if (bounds.isEmpty) return
        val sx = bounds.width() / FRAME_W
        val sy = bounds.height() / FRAME_H
        val r = SPOT_RADIUS * sx
        spots.forEachIndexed { i, s ->
            val c = context.getColor(s.colorRes)
            paints[i].shader = RadialGradient(
                bounds.left + s.x * sx, bounds.top + s.y * sy, r,
                ColorUtils.setAlphaComponent(c, (s.alpha * 255f).roundToInt()), c and 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, base)
        for (p in paints) if (p.shader != null) canvas.drawRect(b, p)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val FRAME_W = 360f
        const val FRAME_H = 800f
        const val SPOT_RADIUS = 300f
    }
}

/** Một đốm aurora: tâm ([x], [y]) theo khung Figma 360 × 800, độ đục [alpha] ở tâm. */
class AuroraSpot(@ColorRes val colorRes: Int, val x: Float, val y: Float, val alpha: Float)

object AuroraSpots {
    /** DS3: 4 đốm của nền v3 chung. */
    val DEFAULT: List<AuroraSpot> = listOf(
        AuroraSpot(R.color.ds_aurora_blue, 0f, 100f, 0.48f),
        AuroraSpot(R.color.ds_aurora_purple, 360f, 300f, 0.42f),
        AuroraSpot(R.color.ds_aurora_cyan, 0f, 500f, 0.28f),
        AuroraSpot(R.color.ds_aurora_pink, 360f, 700f, 0.24f)
    )

    /**
     * OB: bộ đốm của từng màn Onboarding 0.1–0.5 (thứ tự = `OnboardingStep`). Tâm = tâm ellipse Figma (`302:3677`, `302:3695`,
     * `302:3737`, `302:3772`, `302:3796`); độ đục theo từng màu như Figma. 0.5 trùng [DEFAULT].
     */
    val ONBOARDING: List<List<AuroraSpot>> = listOf(
        listOf(
            AuroraSpot(R.color.ds_aurora_blue, 0f, 133f, 0.48f),
            AuroraSpot(R.color.ds_aurora_purple, 360f, 400f, 0.42f),
            AuroraSpot(R.color.ds_aurora_cyan, 0f, 667f, 0.28f)
        ),
        listOf(
            AuroraSpot(R.color.ds_aurora_purple, 0f, 133f, 0.42f),
            AuroraSpot(R.color.ds_aurora_cyan, 360f, 400f, 0.28f),
            AuroraSpot(R.color.ds_aurora_pink, 0f, 667f, 0.24f)
        ),
        listOf(
            AuroraSpot(R.color.ds_aurora_cyan, 0f, 133f, 0.28f),
            AuroraSpot(R.color.ds_aurora_pink, 360f, 400f, 0.24f),
            AuroraSpot(R.color.ds_aurora_blue, 0f, 667f, 0.48f)
        ),
        listOf(
            AuroraSpot(R.color.ds_aurora_pink, 0f, 133f, 0.24f),
            AuroraSpot(R.color.ds_aurora_blue, 360f, 400f, 0.48f),
            AuroraSpot(R.color.ds_aurora_purple, 0f, 667f, 0.42f)
        ),
        DEFAULT
    )
}
