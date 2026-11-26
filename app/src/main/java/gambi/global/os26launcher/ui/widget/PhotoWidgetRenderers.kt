package gambi.global.os26launcher.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.min

/**
 * G3b Task 2 — widget Ảnh 2x2 / 4x2 (cùng một lớp, cỡ theo `prepare`). Hai trạng thái:
 *  - **chưa có ảnh** (chưa nạp kho, widget chưa có ảnh, bản xem trước [sample], ảnh kéo từ bảng chọn `itemId = 0`): biểu tượng ảnh + "Chạm để chọn ảnh";
 *  - **có ảnh**: ảnh thứ `floorMod(giờ treo tường, n)` (đổi đầu giờ, không trạng thái, không việc nền: `WidgetTicker` gọi `prepare` mỗi phút khi Home hiện) vẽ phủ
 *    kín widget bằng [BitmapShader] + bo góc. Bitmap giải mã NGOÀI luồng chính ở đúng cỡ widget ([PhotoBitmapCache]); trong lúc chờ vẫn vẽ ảnh cũ của widget.
 *
 * `prepare` không chặn, không I/O; `draw` không cấp phát (GL Task 6). [bindItem] đặt lại trạng thái ở MỖI lần bind (ô tái dùng, bài học A1a).
 */
internal class PhotoWidgetRenderer(
    private val context: Context,
    private val sample: Boolean
) : WidgetRenderer {
    private val white = context.getColor(R.color.widget_text)
    private val dim = context.getColor(R.color.widget_text_dim)
    private val titlePaint: TextPaint = newWidgetTextPaint(context, white, WidgetFont.SEMIBOLD)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dim
        style = Paint.Style.STROKE
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dim
        style = Paint.Style.FILL
    }
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()
    private val rect = RectF()
    private val frame = RectF()
    private val mountain = Path()

    private var itemId = 0L
    private var w = 0f
    private var h = 0f
    private var radius = 0f

    private var shownBitmap: Bitmap? = null
    private var shownKey: PhotoKey? = null
    private var shaderW = 0
    private var shaderH = 0
    private var photoCount = 0

    // Trạng thái trống
    private var emptyTitle = ""
    private var titleBase = 0f
    private var sunCx = 0f
    private var sunCy = 0f
    private var sunR = 0f

    override fun bindItem(itemId: Long) {
        if (this.itemId != itemId) {
            this.itemId = itemId
            shownBitmap = null
            shownKey = null
            photoPaint.shader = null
            photoCount = 0
        }
    }

    override fun prepare(
        width: Int,
        height: Int,
        density: Float,
        now: ZonedDateTime,
        locale: Locale,
        is24h: Boolean
    ) {
        w = width.toFloat()
        h = height.toFloat()
        radius = min(w, h) * CORNER_RATIO
        rect.set(0f, 0f, w, h)
        PhotoWidgetHub.reportSize(itemId, width, height)

        val photos = if (sample || itemId <= 0L || !PhotoWidgetHub.isLoaded) emptyList() else PhotoWidgetHub.configOf(itemId).photos
        photoCount = photos.size

        // Ảnh theo giờ treo tường; ảnh mà file mất/hỏng (giải mã từng lỗi, hoặc không có file) bị bỏ qua, thử ảnh kế; hỏng hết thì về trạng thái "chưa có ảnh".
        val hour = Math.floorDiv(now.toEpochSecond() + now.offset.totalSeconds, 3600L)
        val start = if (photos.isEmpty()) 0 else Math.floorMod(hour, photos.size.toLong()).toInt()
        var chosenRef: PhotoRef? = null
        var chosenKey: PhotoKey? = null
        for (k in photos.indices) {
            val candidate = photos[(start + k) % photos.size]
            val candidateKey = PhotoKey(itemId, candidate.id, candidate.version, width, height)
            if (PhotoBitmapCache.isFailed(candidateKey)) continue
            if (PhotoBitmapCache.get(candidateKey) == null && PhotoWidgetHub.fileOf(itemId, candidate) == null) continue
            chosenRef = candidate
            chosenKey = candidateKey
            break
        }
        if (chosenRef == null || chosenKey == null) {
            if (photos.isNotEmpty()) photoCount = 0 // mọi ảnh đều hỏng: coi như chưa có ảnh (bấm vào để sửa)
            shownBitmap = null
            shownKey = null
            photoPaint.shader = null
            prepareEmpty(density)
            return
        }
        val ref: PhotoRef = chosenRef
        val key: PhotoKey = chosenKey
        val ready = PhotoBitmapCache.get(key)
        if (ready != null) {
            if (ready !== shownBitmap || shaderW != width || shaderH != height) {
                shownBitmap = ready
                shownKey = key
                shaderW = width
                shaderH = height
                photoPaint.shader = BitmapShader(ready, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
                    shaderMatrix.setScale(w / ready.width, h / ready.height)
                    shader.setLocalMatrix(shaderMatrix)
                }
            }
        } else {
            val file = PhotoWidgetHub.fileOf(itemId, ref)
            if (file != null) PhotoBitmapCache.request(key, ref, file)
            // Ảnh cũ (nếu có) vẫn được vẽ tới khi ảnh mới giải mã xong; cỡ đổi thì kéo giãn tạm bằng ma trận.
            val old = shownBitmap
            if (old != null && (shaderW != width || shaderH != height)) {
                shaderW = width
                shaderH = height
                shaderMatrix.setScale(w / old.width, h / old.height)
                (photoPaint.shader as? BitmapShader)?.setLocalMatrix(shaderMatrix)
            }
        }
    }

    private fun prepareEmpty(density: Float) {
        val side = min(w, h)
        emptyTitle = context.getString(R.string.ph_empty_title)
        titlePaint.fitWidth(emptyTitle, w * 0.84f, side * 0.115f)
        val iconSize = side * 0.34f
        val groupH = iconSize + side * 0.07f + titlePaint.textSize
        val top = (h - groupH) / 2f
        val cx = w / 2f
        frame.set(cx - iconSize / 2f, top, cx + iconSize / 2f, top + iconSize * 0.8f)
        iconPaint.strokeWidth = maxOf(1.5f * density, iconSize * 0.07f)
        sunR = iconSize * 0.09f
        sunCx = frame.left + frame.width() * 0.3f
        sunCy = frame.top + frame.height() * 0.32f
        mountain.rewind()
        mountain.moveTo(frame.left + iconSize * 0.06f, frame.bottom - iconSize * 0.06f)
        mountain.lineTo(frame.left + frame.width() * 0.42f, frame.top + frame.height() * 0.52f)
        mountain.lineTo(frame.left + frame.width() * 0.62f, frame.top + frame.height() * 0.74f)
        mountain.lineTo(frame.left + frame.width() * 0.76f, frame.top + frame.height() * 0.58f)
        mountain.lineTo(frame.right - iconSize * 0.06f, frame.bottom - iconSize * 0.06f)
        mountain.close()
        titleBase = titlePaint.baselineAround(top + iconSize * 0.8f + side * 0.07f + titlePaint.textSize / 2f)
    }

    override fun draw(canvas: Canvas) {
        if (photoCount > 0) {
            if (photoPaint.shader != null) canvas.drawRoundRect(rect, radius, radius, photoPaint)
            return
        }
        val r = frame.width() * 0.12f
        canvas.drawRoundRect(frame, r, r, iconPaint)
        canvas.drawCircle(sunCx, sunCy, sunR, iconFill)
        canvas.drawPath(mountain, iconFill)
        canvas.drawText(emptyTitle, w / 2f, titleBase, titlePaint)
    }

    override fun contentDescription(): String =
        if (photoCount > 0) context.getString(R.string.ph_desc_photos, photoCount)
        else context.getString(R.string.ph_desc_empty)

    private companion object {
        /** Cùng tỉ lệ `IconCellView.WIDGET_CORNER_RATIO` (0.12): ảnh phủ kín nền kính nên phải bo đúng góc của nó. */
        const val CORNER_RATIO = 0.12f
    }
}
