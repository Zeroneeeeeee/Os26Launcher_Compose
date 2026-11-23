package gambi.global.os26launcher.ui.photo.compose

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.domain.model.photo.CropRect
import kotlin.math.max
import kotlin.math.min

/**
 * JS6 — trạng thái màn cắt ảnh (bản Compose của `PhotoCropView`, G3b Task 4 D4): KHUNG cố định giữa vùng vẽ (tỉ lệ widget thật), ảnh nằm dưới
 * được kéo (một ngón) / phóng (hai ngón). Ràng buộc: khung luôn nằm trong ảnh; phóng tối thiểu = vừa phủ khung, tối đa [MAX_ZOOM] lần mức đó.
 * Ma trận là nguồn thật; [currentCrop] suy ra vùng cắt CHUẨN HÓA theo ảnh (đúng dạng `CropRect` của kho). Phép tính chép nguyên từ bản View.
 */
internal class PhotoCropState(private val marginPx: Float) {
    private var bitmap: Bitmap? = null
    private var aspect = 1f
    private var pendingCrop: CropRect? = null
    internal val matrix = Matrix()
    private val inverse = Matrix()
    private val values = FloatArray(9)
    internal val frame = RectF()
    private val imageRect = RectF()
    private val tmp = RectF()
    internal var cornerRadius = 0f
    private var minScale = 1f
    private var maxScale = 1f
    private var width = 0
    private var height = 0

    /** Tăng mỗi lần ma trận đổi để Compose vẽ lại. */
    var version by mutableIntStateOf(0)
        private set

    val image: Bitmap? get() = bitmap

    fun setImage(bmp: Bitmap, frameAspect: Float, initial: CropRect) {
        bitmap = bmp
        aspect = if (frameAspect.isFinite() && frameAspect > 0f) frameAspect else 1f
        pendingCrop = initial
        if (width > 0 && height > 0) layoutFrameAndApply()
        version++
    }

    fun clear() {
        bitmap = null
        pendingCrop = null
        frame.setEmpty()
        version++
    }

    fun onSize(w: Int, h: Int) {
        if (w == width && h == height) return
        val hadFrame = !frame.isEmpty
        width = w
        height = h
        if (w <= 0 || h <= 0 || bitmap == null) return
        // Đổi cỡ (xoay, chia đôi màn hình): giữ vùng cắt đang chọn, dựng lại khung và ma trận từ nó.
        if (pendingCrop == null && hadFrame) pendingCrop = currentCrop()
        layoutFrameAndApply()
        version++
    }

    fun currentCrop(): CropRect {
        val bmp = bitmap ?: return CropRect.FULL
        if (frame.isEmpty) return pendingCrop ?: CropRect.FULL
        matrix.invert(inverse)
        tmp.set(frame)
        inverse.mapRect(tmp)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        var l = (tmp.left / bw).coerceIn(0f, 1f)
        var t = (tmp.top / bh).coerceIn(0f, 1f)
        var r = (tmp.right / bw).coerceIn(0f, 1f)
        var b = (tmp.bottom / bh).coerceIn(0f, 1f)
        if (r - l < MIN_SPAN) r = min(1f, l + MIN_SPAN).also { l = max(0f, it - MIN_SPAN) }
        if (b - t < MIN_SPAN) b = min(1f, t + MIN_SPAN).also { t = max(0f, it - MIN_SPAN) }
        return CropRect(l, t, r, b)
    }

    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        if (bitmap == null) return
        val current = currentScale()
        val target = (current * factor).coerceIn(minScale, maxScale)
        val f = target / current
        matrix.postScale(f, f, focusX, focusY)
        clampTranslation()
        version++
    }

    fun pan(dx: Float, dy: Float) {
        if (bitmap == null) return
        matrix.postTranslate(dx, dy)
        clampTranslation()
        version++
    }

    private fun layoutFrameAndApply() {
        val bmp = bitmap ?: return
        val availW = width - 2 * marginPx
        val availH = height - 2 * marginPx
        if (availW <= 0f || availH <= 0f) return
        var fw = availW
        var fh = fw / aspect
        if (fh > availH) {
            fh = availH
            fw = fh * aspect
        }
        frame.set((width - fw) / 2f, (height - fh) / 2f, (width + fw) / 2f, (height + fh) / 2f)
        cornerRadius = min(fw, fh) * CORNER_RATIO
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        minScale = max(fw / bw, fh / bh)
        maxScale = minScale * MAX_ZOOM
        val crop = pendingCrop ?: CropRect.FULL
        pendingCrop = null
        val cw = (crop.right - crop.left) * bw
        val ch = (crop.bottom - crop.top) * bh
        val scale = max(fw / cw, fh / ch).coerceIn(minScale, maxScale)
        val cx = (crop.left + crop.right) / 2f * bw
        val cy = (crop.top + crop.bottom) / 2f * bh
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(frame.centerX() - cx * scale, frame.centerY() - cy * scale)
        clampTranslation()
    }

    private fun currentScale(): Float {
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** Ảnh phải phủ kín khung: không để khoảng trống giữa mép ảnh và mép khung. */
    private fun clampTranslation() {
        val bmp = bitmap ?: return
        imageRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        matrix.mapRect(imageRect)
        var dx = 0f
        var dy = 0f
        if (imageRect.left > frame.left) dx = frame.left - imageRect.left
        if (imageRect.right < frame.right) dx = frame.right - imageRect.right
        if (imageRect.top > frame.top) dy = frame.top - imageRect.top
        if (imageRect.bottom < frame.bottom) dy = frame.bottom - imageRect.bottom
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    companion object {
        /** CHỈNH TAY: lề quanh khung (dp). */
        const val MARGIN_DP = 24f
        const val MAX_ZOOM = 4f
        /** Cùng tỉ lệ bo góc của widget trên lưới. */
        const val CORNER_RATIO = 0.12f
        const val MIN_SPAN = 0.001f
    }
}

/**
 * JS6 — vùng vẽ của màn cắt: ảnh theo ma trận, lớp tối ngoài khung (lỗ bo góc), viền trắng 2 dp. Kéo một ngón = dời, hai ngón = phóng quanh tâm.
 * [onCropChanged] chỉ gọi khi nhấc tay (không mỗi khung hình).
 */
@Composable
internal fun PhotoCropper(state: PhotoCropState, description: String, onCropChanged: (CropRect) -> Unit, modifier: Modifier = Modifier) {
    val changed by rememberUpdatedState(onCropChanged)
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .onSizeChanged { state.onSize(it.width, it.height) }
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Như bản View: chưa nạp ảnh thì không nhận chạm và không báo vùng cắt (tránh ghi đè vùng đã lưu bằng FULL lúc đang giải mã).
                    if (state.image == null) return@awaitEachGesture
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (zoom != 1f && centroid != Offset.Unspecified) state.zoom(zoom, centroid.x, centroid.y)
                        // Như bản View (onScroll bỏ qua khi đang phóng): chỉ dời ảnh khi một ngón.
                        if (event.changes.count { it.pressed } == 1) {
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) state.pan(pan.x, pan.y)
                        }
                        event.changes.forEach { if (it.pressed) it.consume() }
                    } while (event.changes.any { it.pressed })
                    changed(state.currentCrop())
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE") val v = state.version // vẽ lại khi ma trận đổi
        val bmp = state.image ?: return@Canvas
        drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, state.matrix, paint) }
        val f = state.frame
        if (f.isEmpty) return@Canvas
        val r = state.cornerRadius
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(f.left, f.top, f.right, f.bottom, CornerRadius(r)))
        }
        drawPath(hole, DIM)
        drawRoundRect(
            Color.White,
            topLeft = Offset(f.left, f.top),
            size = Size(f.width(), f.height()),
            cornerRadius = CornerRadius(r),
            style = Stroke(2.dp.toPx())
        )
    }
}

private val DIM = Color(0xAA000000)
