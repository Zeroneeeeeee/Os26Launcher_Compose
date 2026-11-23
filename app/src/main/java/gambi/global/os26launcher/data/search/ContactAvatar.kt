package gambi.global.os26launcher.data.search

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale

/** G3a Task 3 — vẽ avatar người liên hệ: ảnh cắt tròn, hoặc vòng tròn xám kiểu iOS với chữ cái đầu. Thuần đồ họa, không đọc dữ liệu. */
internal object ContactAvatar {

    fun circleCrop(source: Bitmap, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val clip =
            Path().apply { addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawBitmap(
            source,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, sizePx, sizePx),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        return out
    }

    fun initialsCircle(name: String, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                sizePx.toFloat(),
                0xFFA5A9B2.toInt(),
                0xFF858993.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bg)
        val text = initials(name)
        if (text.isNotEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textAlign = Paint.Align.CENTER
                textSize = sizePx * if (text.length > 1) 0.38f else 0.46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val y = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, sizePx / 2f, y, paint)
        }
        return out
    }

    /** Chữ cái đầu của từ đầu và từ cuối (in hoa, tối đa 2 ký tự); tên một từ thì một ký tự. */
    fun initials(name: String): String {
        val chars = name.trim().split(Regex("\\s+"))
            .mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
        if (chars.isEmpty()) return ""
        val picked =
            if (chars.size == 1) chars.first().toString() else "${chars.first()}${chars.last()}"
        return picked.uppercase(Locale.getDefault())
    }
}
