package gambi.global.os26launcher.ui.settings.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import gambi.global.os26launcher.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** P3-2: chế độ của khung xem trước trong sheet "Đặt hình nền cho". */
enum class WallpaperPreviewMode { HOME, LOCK }

/**
 * P3-2 — khung điện thoại thu nhỏ trong bottom sheet "Đặt hình nền cho" (Figma 2.2 popup `302:2090`), giả lập kiểu iOS (bản View:
 * `WallpaperPreviewView` của REF):
 * - [WallpaperPreviewMode.HOME]: ảnh nền + lưới ô icon mờ + dock kính.
 * - [WallpaperPreviewMode.LOCK]: ảnh nền + thứ ngày tháng và đồng hồ lớn + hai nút tròn (đèn pin, máy ảnh) ở đáy.
 *
 * Vẽ bằng canvas gốc (centerCrop qua [BitmapShader]); chưa có ảnh thì nền tối. Cao = rộng × [PREVIEW_ASPECT]. Ảnh do người gọi giữ.
 */
@Composable
fun WallpaperPreviewFrame(bitmap: Bitmap?, mode: WallpaperPreviewMode, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = remember { WallpaperPreviewPainter(context) }
    Canvas(modifier.aspectRatio(1f / PREVIEW_ASPECT)) {
        drawIntoCanvas { painter.draw(it.nativeCanvas, size.width, size.height, bitmap, mode) }
    }
}

/** Phần vẽ của [WallpaperPreviewFrame] (giữ Paint giữa các lần vẽ). */
private class WallpaperPreviewPainter(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C24.toInt() }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
    private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(90, 255, 255, 255)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val font: Typeface = try {
        ResourcesCompat.getFont(context, R.font.be_vietnam_pro_semibold)
    } catch (e: android.content.res.Resources.NotFoundException) {
        null
    } ?: Typeface.DEFAULT_BOLD
    private val frame = RectF()
    private val clip = Path()
    private val tmp = RectF()
    private val shaderMatrix = Matrix()
    private var shaderOf: Bitmap? = null

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, bitmap: Bitmap?, mode: WallpaperPreviewMode) {
        if (w <= 0f || h <= 0f) return
        val stroke = 1f * density
        frame.set(stroke, stroke, w - stroke, h - stroke)
        val r = w * CORNER_RATIO
        clip.reset()
        clip.addRoundRect(frame, r, r, Path.Direction.CW)
        strokePaint.strokeWidth = stroke
        if (bitmap !== shaderOf) {
            shaderOf = bitmap
            bgPaint.shader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        if (bitmap != null) { // centerCrop: phủ kín khung, cắt phần thừa đều hai bên
            val scale = max(frame.width() / bitmap.width, frame.height() / bitmap.height)
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate(
                frame.left + (frame.width() - bitmap.width * scale) / 2f,
                frame.top + (frame.height() - bitmap.height * scale) / 2f
            )
            bgPaint.shader?.setLocalMatrix(shaderMatrix)
        }
        val save = canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(frame, if (bitmap != null) bgPaint else placeholderPaint)
        when (mode) {
            WallpaperPreviewMode.HOME -> drawHome(canvas)
            WallpaperPreviewMode.LOCK -> drawLock(canvas)
        }
        canvas.restoreToCount(save)
        canvas.drawRoundRect(frame, r, r, strokePaint)
    }

    /** Lưới 4 cột x 5 hàng ô icon bo góc + dock kính 4 ô, tỉ lệ theo chiều rộng khung. */
    private fun drawHome(canvas: android.graphics.Canvas) {
        val w = frame.width()
        val cols = 4
        val icon = w * 0.15f
        val gapX = (w - cols * icon) / (cols + 1)
        val corner = icon * 0.28f
        val top = frame.top + frame.height() * 0.09f
        val rowStep = icon + icon * 0.62f
        for (row in 0 until HOME_ROWS) {
            for (col in 0 until cols) {
                val l = frame.left + gapX + col * (icon + gapX)
                val t = top + row * rowStep
                tmp.set(l, t, l + icon, t + icon)
                canvas.drawRoundRect(tmp, corner, corner, iconPaint)
            }
        }
        // Dock: viên kính ở đáy, 4 ô icon bên trong.
        val dockH = icon * 1.55f
        val inset = w * 0.04f
        val dockBottom = frame.bottom - inset
        tmp.set(frame.left + inset, dockBottom - dockH, frame.right - inset, dockBottom)
        val dockR = dockH * 0.36f
        canvas.drawRoundRect(tmp, dockR, dockR, dockPaint)
        val dockTop = tmp.top
        val dockGap = (tmp.width() - cols * icon) / (cols + 1)
        val dockLeft = tmp.left
        for (col in 0 until cols) {
            val l = dockLeft + dockGap + col * (icon + dockGap)
            val t = dockTop + (dockH - icon) / 2f
            tmp.set(l, t, l + icon, t + icon)
            canvas.drawRoundRect(tmp, corner, corner, iconPaint)
        }
    }

    /** Thứ ngày tháng nhỏ, đồng hồ lớn, hai nút tròn ở đáy (như màn khóa iOS). */
    private fun drawLock(canvas: android.graphics.Canvas) {
        val w = frame.width()
        val cx = frame.centerX()
        val now = Date()
        textPaint.typeface = font
        textPaint.textSize = w * 0.075f
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, DATE_SKELETON)
        canvas.drawText(SimpleDateFormat(pattern, locale).format(now), cx, frame.top + frame.height() * 0.13f, textPaint)
        textPaint.textSize = w * 0.30f
        val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now)
            .replace(Regex("\\s?[APap][Mm]$"), "") // iOS bỏ SA/CH trên màn khóa
        canvas.drawText(timeText, cx, frame.top + frame.height() * 0.30f, textPaint)
        // Hai nút tròn đèn pin (trái) và máy ảnh (phải).
        val r = w * 0.075f
        val y = frame.bottom - frame.height() * 0.08f
        val margin = w * 0.17f
        canvas.drawCircle(frame.left + margin, y, r, buttonPaint)
        canvas.drawCircle(frame.right - margin, y, r, buttonPaint)
        // Thanh home ở đáy.
        val barW = w * 0.32f
        val barH = max(1.5f * density, w * 0.012f)
        val barY = frame.bottom - frame.height() * 0.022f
        tmp.set(cx - barW / 2f, barY - barH, cx + barW / 2f, barY)
        canvas.drawRoundRect(tmp, barH, barH, textPaint)
    }

    private companion object {
        const val CORNER_RATIO = 0.16f
        const val HOME_ROWS = 5

        /** Bộ khung ngày "thứ, ngày tháng"; mẫu thật lấy theo ngôn ngữ máy (getBestDateTimePattern). */
        const val DATE_SKELETON = "EEEdMMMM"
    }
}

/** Cao / rộng của khung xem trước, gần tỉ lệ màn điện thoại dài (~19.5:9). Khung trong Figma là 72 x 128 dp. CHỈNH TAY. */
private const val PREVIEW_ASPECT = 1.9f

/** P3-3: cao khung xem trước Bộ biểu tượng (REF `icon_style_preview_height`). */
internal val ICON_STYLE_PREVIEW_HEIGHT = 140.dp

/** P3-3: cỡ icon mẫu trong khung xem trước (REF `icon_style_preview_icon`). */
const val ICON_STYLE_PREVIEW_ICON_DP = 56

/**
 * P3-3 — khung xem trước đầu màn "Bộ biểu tượng": ảnh nền đang dùng (centerCrop, bo góc card) với một hàng icon mẫu ở giữa, vẽ theo kiểu
 * icon đang chọn (bản View: `IconStylePreviewView` của REF). Bitmap do `IconStylePreviewViewModel` giữ.
 */
@Composable
internal fun IconStylePreviewCard(background: Bitmap?, icons: List<Bitmap>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = remember { IconStylePreviewPainter(context) }
    val description = stringResource(R.string.icon_style_preview_description)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ICON_STYLE_PREVIEW_HEIGHT)
            .semantics { contentDescription = description }
    ) {
        drawIntoCanvas { painter.draw(it.nativeCanvas, size.width, size.height, background, icons) }
    }
}

private class IconStylePreviewPainter(context: Context) {
    private val res = context.resources
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.ds_surface_control) }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frame = RectF()
    private val clip = Path()
    private val dst = RectF()
    private val shaderMatrix = Matrix()
    private val corner = res.getDimension(R.dimen.ds_radius_card)
    private val iconSize = ICON_STYLE_PREVIEW_ICON_DP * res.displayMetrics.density
    private var shaderOf: Bitmap? = null

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, background: Bitmap?, icons: List<Bitmap>) {
        if (w <= 0f || h <= 0f) return
        frame.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(frame, corner, corner, Path.Direction.CW)
        if (background !== shaderOf) {
            shaderOf = background
            bgPaint.shader = background?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        if (background != null) {
            val scale = max(w / background.width, h / background.height)
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate((w - background.width * scale) / 2f, (h - background.height * scale) / 2f)
            bgPaint.shader?.setLocalMatrix(shaderMatrix)
        }
        val save = canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(frame, if (background != null) bgPaint else placeholderPaint)
        canvas.restoreToCount(save)
        if (icons.isEmpty()) return
        // Một hàng, các icon cách đều nhau theo chiều ngang (khe = khoảng trống chia đều cho n + 1 chỗ), giữa theo chiều dọc.
        val n = icons.size
        val gap = (w - n * iconSize) / (n + 1)
        val top = (h - iconSize) / 2f
        icons.forEachIndexed { i, bmp ->
            if (bmp.isRecycled) return@forEachIndexed
            val left = gap + i * (iconSize + gap)
            dst.set(left, top, left + iconSize, top + iconSize)
            canvas.drawBitmap(bmp, null, dst, iconPaint)
        }
    }
}
