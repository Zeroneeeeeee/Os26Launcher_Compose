package gambi.global.os26launcher.core.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import java.lang.ref.WeakReference
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * Kính "tại chỗ" (GL): vẽ lát cắt của ảnh nền đã làm mờ ĐÚNG với vị trí hiện tại của [owner] trên màn hình, tính lại
 * MỖI LẦN vẽ (khác `StaticBlurSurface.computeCrop()` chỉ cắt một lần). Nhờ vậy phần tử nằm trong cha đang cuộn/trượt
 * (ô thư mục trong ViewPager2, ô nhóm trong ScrollView) vẫn bám nền, miễn là cha báo [owner] `invalidate()` khi cuộn.
 *
 * Không thêm View: dùng làm `background` của owner hoặc gọi [draw] ngay trong `onDraw` của owner (xoay do Jiggler và
 * ảnh chụp lúc kéo vì thế đều có kính). Không cấp phát trong [draw]. Thiếu ảnh nền thì vẽ màu phẳng [GlassStyle.flat].
 *
 * Cách vẽ: `BitmapShader` của bitmap mờ nhỏ ([BlurredBackdropCache.small]) + `drawRoundRect` (không `clipPath`).
 * GL Task 6a: lớp tint gộp vào chính lệnh tô qua `PorterDuffColorFilter(tint, SRC_OVER)` và viền bỏ khi owner đang xoay
 * (Jiggler), nên ô đang rung chỉ tốn MỘT lệnh vẽ thay vì ba (phép đo Live 4: chi phí rung nằm ở RenderThread, không phải ghi
 * display list; xem plans/GL-glass-pass.md, kết quả Task 5).
 * Shader: toạ độ bitmap nhỏ -> toạ độ drawable -> toạ độ view ảnh nền bằng chính `imageMatrix` của ImageView (FX8: không còn giả định
 * bitmap khớp tỉ lệ view). GL Task 6c: ma trận shader này **cố định** (chỉ đổi khi ảnh nền, cỡ bản nhỏ hoặc cỡ
 * view ảnh nền đổi), còn vị trí lát cắt đặt bằng `canvas.translate` + vẽ hình chữ nhật dịch tương ứng (toạ độ hình = toạ độ
 * ảnh nền, vị trí trên màn hình = trừ chênh lệch). Nhờ vậy mỗi khung KHÔNG gọi `setLocalMatrix` (không tạo lại shader gốc) và
 * không cấp phát; nhớ danh tính `Drawable` của ảnh nền để bỏ qua ép kiểu và tra bộ đệm khi không đổi.
 *
 * GI Task 2: đặt [shapePath] thì phần tô và viền theo path đó (icon app) thay vì hình chữ nhật bo góc; xem [shapePath].
 *
 * GT: [look] là bộ thông số kính dùng chung, đọc MỘT lần lúc dựng (đổi thông số thì Home dựng lại). Độ bóng pha vào tint và viền
 * ([GlassStyle.tuned]); bo góc nhân vào [cornerRadiusPx] lúc vẽ; khúc xạ = phóng nền quanh tâm phần tử bằng `canvas.scale` (hình vẽ co
 * lại đúng 1/hệ số quanh cùng tâm nên trên màn vẫn đúng cỡ, shader vẫn đứng yên, không `setLocalMatrix`); độ mờ nằm ở bản nhỏ của
 * [cache]. [shapePath] (icon app) nhận khúc xạ, bóng, mờ nhưng KHÔNG nhận bo góc: hình icon đã nướng trong bitmap.
 *
 * @param backdrop trả về ImageView ảnh nền (null nếu chưa tìm thấy); gọi mỗi lần vẽ nên phải rẻ.
 */
class BackdropBlurDrawable(
    private val owner: View,
    private val backdrop: () -> ImageView?,
    private val cache: BlurredBackdropCache,
    baseStyle: GlassStyle,
    private val look: GlassLook
) : Drawable(), BlurredBackdropCache.SmallListener {

    private val glassStyle = baseStyle.tuned(look)
    private val zoom = GlassTuning.zoom(look)

    /** Bán kính bo góc GỐC của phần tử (px), chưa nhân hệ số bo góc của [look]. Đổi được giữa các lần vẽ (ô thư mục tính theo cỡ icon). */
    var cornerRadiusPx = 0f

    /**
     * GI Task 2: hình tuỳ ý theo toạ độ drawable (gốc ở `bounds.left/top`, cỡ = cỡ bounds). Khác null thì tô và viền theo path
     * trong cả [draw] lẫn [drawScaled], [cornerRadiusPx] bị bỏ qua. Viền: path không inset được nên nét vẽ trong `clipPath`
     * (chỉ nửa trong hiện ra). Null = hành vi cũ từng lệnh.
     */
    var shapePath: Path? = null

    private val workPath = Path()
    private val pathMatrix = Matrix()
    private val rect = RectF()
    private val matrix = Matrix()
    private val ownerLoc = IntArray(2)
    private val backdropLoc = IntArray(2)

    // Trạng thái bộ đệm của shader (Task 6c).
    // FX1 (C3): tham chiếu YẾU tới drawable ảnh nền (BitmapDrawable giữ bitmap đầy đủ): ô chưa vẽ lại (trang đệm) không được giữ ảnh nền cũ.
    private var lastDrawableRef: WeakReference<Drawable>? = null
    private var smallBitmap: Bitmap? =
        null // null = chưa dựng xong (vẽ phẳng), xem BlurredBackdropCache.smallOrRequest
    private var shaderViewW = -1
    private var shaderViewH = -1

    // FX8: cỡ intrinsic của drawable và cỡ bản nhỏ mà ma trận shader được tính cho (ma trận phụ thuộc cả hai, cùng cỡ view).
    private var shaderDrawableW = -1
    private var shaderDrawableH = -1
    private var shaderSmallW = -1
    private var shaderSmallH = -1
    private var tx =
        0f // dịch toạ độ ảnh nền -> toạ độ owner: owner = ảnh nền - (backdropLoc - ownerLoc)
    private var ty = 0f

    // Tint = lớp phủ SRC_OVER một màu lên kết quả của shader (tương đương vẽ thêm một rrect tint, nhưng cùng một lệnh).
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(glassStyle.tint, PorterDuff.Mode.SRC_OVER)
    }
    private val flatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glassStyle.flat }
    private val baseBorderWidth = owner.resources.displayMetrics.density * BORDER_DP
    private val scaledRect = RectF()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = baseBorderWidth
        color = glassStyle.border
    }

    // FX8 (D2): ô đã vẽ phẳng (bản mờ chưa tới) thì khi bản mờ tới, kính hiện mờ dần trên nền phẳng trong FADE_MS thay vì đổi hẳn trong một khung.

    /** TR: đổi thời lượng chuyển pha phẳng -> mờ (nhận FADE_MS, trả ms), hỏi mỗi lần vẽ; nền màn nhóm Library dùng `TransitionMotion::duration`. 0 = đổi ngay. */
    var blurFadeMs: (Long) -> Long = { it }

    private var drewFlat = false
    private var fadeStartMs = 0L

    private var shader: BitmapShader? = null
    private val hasBorder =
        (glassStyle.border ushr 24) != 0 // GF: viền trong suốt (nền màn nhóm) thì bỏ lệnh vẽ

    init {
        cache.register(this) // AN Task 10: được báo khi ảnh nền đổi để owner vẽ lại
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        val r = GlassTuning.cornerRadius(look, cornerRadiusPx, rect.width(), rect.height())

        val shape = shapePath
        if (!prepareShader()) {
            drewFlat = true
            fadeStartMs = 0L
            drawFlat(canvas, r, shape)
            return
        }
        var layered = false
        if (drewFlat) {
            val now = SystemClock.uptimeMillis()
            if (fadeStartMs == 0L) fadeStartMs = now
            val fadeMs = blurFadeMs(FADE_MS)
            val a = if (fadeMs <= 0L) 255 else ((now - fadeStartMs) * 255 / fadeMs).toInt()
            if (a >= 255) {
                drewFlat = false
                fadeStartMs = 0L
            } else {
                drawFlat(canvas, r, shape)
                canvas.saveLayerAlpha(b.left - 2f, b.top - 2f, b.right + 2f, b.bottom + 2f, a.coerceAtLeast(0))
                layered = true
                owner.invalidate() // vẽ lại khung sau cho tới khi hết chuyển pha
            }
        }
        drawGlass(canvas, r, shape)
        if (layered) canvas.restore()
    }

    /** Nền phẳng khi shader chưa sẵn sàng (và lớp dưới của pha mờ dần). [rect] lúc này = bounds. */
    private fun drawFlat(canvas: Canvas, r: Float, shape: Path?) {
        if (shape != null) {
            shape.offset(rect.left, rect.top, workPath)
            canvas.drawPath(workPath, flatPaint)
        } else {
            canvas.drawRoundRect(rect, r, r, flatPaint)
        }
    }

    /** Phần vẽ kính (shader đã sẵn sàng). [rect] lúc vào = bounds. */
    private fun drawGlass(canvas: Canvas, r: Float, shape: Path?) {
        val left = rect.left
        val top = rect.top
        // Canvas phần mềm = ảnh chụp ô (snapshotWithoutBadge): ở đó view KHÔNG bị co (owner.draw không áp transform của chính nó) nên
        // vẽ như scale = 1.
        val sx = if (canvas.isHardwareAccelerated) owner.scaleX else 1f
        val sy = if (canvas.isHardwareAccelerated) owner.scaleY else 1f
        if (sx != 1f || sy != 1f) {
            drawScaled(canvas, r, sx, sy, shape)
            return
        }
        // Toạ độ hình = toạ độ ảnh nền: dịch hình chữ nhật +t rồi dịch canvas -t để nó hiện đúng chỗ của owner (shader đứng yên).
        canvas.save()
        canvas.translate(-tx, -ty)
        // GT khúc xạ: phóng mọi thứ quanh tâm phần tử (toạ độ ảnh nền) rồi vẽ hình co 1/zoom quanh cùng tâm.
        val inv = 1f / zoom
        val cx = rect.centerX() + tx
        val cy = rect.centerY() + ty
        if (zoom != 1f) canvas.scale(zoom, zoom, cx, cy)
        if (shape != null) {
            pathMatrix.setTranslate(left + tx, top + ty)
            if (zoom != 1f) pathMatrix.postScale(inv, inv, cx, cy)
            shape.transform(pathMatrix, workPath)
            canvas.drawPath(workPath, fillPaint)
            // Viền trong suốt (icon kiểu Kính đã nướng viền vào bitmap) thì bỏ hẳn clipPath + nét.
            if (owner.rotation == 0f && borderPaint.alpha != 0) {
                borderPaint.strokeWidth = baseBorderWidth * inv
                canvas.save()
                canvas.clipPath(workPath)
                canvas.drawPath(workPath, borderPaint)
                canvas.restore()
                borderPaint.strokeWidth = baseBorderWidth
            }
            canvas.restore()
            return
        }
        rect.offset(tx, ty)
        if (zoom != 1f) shrink(rect, cx, cy, inv)
        val rr = r * inv
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        // Viền chỉ khi owner đứng thẳng: ô đang rung (xoay) bỏ viền để giảm số lệnh vẽ trên RenderThread.
        if (owner.rotation == 0f && hasBorder) {
            val stroke = baseBorderWidth * inv
            borderPaint.strokeWidth = stroke
            val half = stroke / 2f
            rect.inset(half, half)
            val br = (rr - half).coerceAtLeast(0f)
            canvas.drawRoundRect(rect, br, br, borderPaint)
            borderPaint.strokeWidth = baseBorderWidth
        }
        canvas.restore()
    }

    /** GT: co [r] quanh ([cx], [cy]) theo hệ số [k]; không cấp phát. */
    private fun shrink(r: RectF, cx: Float, cy: Float, k: Float) {
        r.set(cx + (r.left - cx) * k, cy + (r.top - cy) * k, cx + (r.right - cx) * k, cy + (r.bottom - cy) * k)
    }

    /**
     * AN Task 2: owner đang bị scale (spring mở panel, phóng thư mục khi hover, nhấn co). `getLocationOnScreen` trả góc trên-trái
     * của hình ĐÃ CO (L'), còn drawable vẽ theo toạ độ cục bộ chưa co rồi RenderNode co lại: điểm cục bộ p hiện ở L' + s·p nhưng lát
     * cắt nền lấy ở L' + p (lệch (1 - s)·p). Sửa: vẽ trong "không gian nền" (shader vẫn đứng yên): `canvas.scale(1/s)`,
     * `translate(-t)`, hình = s·rect + t, bán kính và độ dày viền nhân s. Khi đó điểm cục bộ p lấy đúng nền ở L' + s·p, tức đúng
     * chỗ nó đang hiện trên màn hình. Không `setLocalMatrix`, không cấp phát. Chỉ đúng khi owner co thuần (không xoay đồng thời);
     * owner phải `invalidate()` mỗi khung của spring vì thay đổi scale không ghi lại display list.
     */
    private fun drawScaled(canvas: Canvas, r: Float, sx: Float, sy: Float, shape: Path?) {
        canvas.save()
        canvas.scale(1f / sx, 1f / sy)
        canvas.translate(-tx, -ty)
        // GT khúc xạ: như [draw], tâm là tâm của hình ĐÃ CO trong toạ độ ảnh nền.
        val inv = 1f / zoom
        val cx = rect.centerX() * sx + tx
        val cy = rect.centerY() * sy + ty
        if (zoom != 1f) canvas.scale(zoom, zoom, cx, cy)
        if (shape != null) {
            // GI Task 2: điểm cục bộ p -> ((left + p.x)·sx + tx, (top + p.y)·sy + ty), cùng công thức với scaledRect bên dưới.
            pathMatrix.setTranslate(rect.left, rect.top)
            pathMatrix.postScale(sx, sy)
            pathMatrix.postTranslate(tx, ty)
            if (zoom != 1f) pathMatrix.postScale(inv, inv, cx, cy)
            shape.transform(pathMatrix, workPath)
            canvas.drawPath(workPath, fillPaint)
            if (owner.rotation == 0f && borderPaint.alpha != 0) {
                borderPaint.strokeWidth = baseBorderWidth * sx * inv
                canvas.save()
                canvas.clipPath(workPath)
                canvas.drawPath(workPath, borderPaint)
                canvas.restore()
                borderPaint.strokeWidth = baseBorderWidth
            }
            canvas.restore()
            return
        }
        scaledRect.set(
            rect.left * sx + tx,
            rect.top * sy + ty,
            rect.right * sx + tx,
            rect.bottom * sy + ty
        )
        if (zoom != 1f) shrink(scaledRect, cx, cy, inv)
        val rx = r * sx * inv
        val ry = r * sy * inv
        canvas.drawRoundRect(scaledRect, rx, ry, fillPaint)
        if (owner.rotation == 0f && hasBorder) {
            val stroke = baseBorderWidth * sx * inv
            borderPaint.strokeWidth = stroke
            val half = stroke / 2f
            scaledRect.inset(half, half)
            canvas.drawRoundRect(
                scaledRect,
                (rx - half).coerceAtLeast(0f),
                (ry - half).coerceAtLeast(0f),
                borderPaint
            )
            borderPaint.strokeWidth = baseBorderWidth
        }
        canvas.restore()
    }

    /**
     * Chuẩn bị shader cố định và độ dịch [tx]/[ty] theo vị trí hiện tại; false nếu chưa có ảnh nền để lấy (vẽ phẳng).
     * Chỉ dựng lại shader khi ảnh nền (danh tính [Drawable]) hoặc cỡ view ảnh nền đổi.
     */
    private fun prepareShader(): Boolean {
        val view = backdrop() ?: return false
        val drawable = view.drawable
        if (drawable == null) {
            // Ảnh nền bị gỡ (nạp lỗi/hết bộ nhớ -> setImageDrawable(null)): bỏ bản nhỏ và shader cũ, nếu không kính vẽ blur của ảnh cũ
            // trên nền đen. (So `drawable !== ref.get()` không đủ: ref yếu đã bị GC thì `null !== null` là false.)
            if (smallBitmap != null || shader != null || lastDrawableRef != null) {
                lastDrawableRef = null
                smallBitmap = null
                shader = null
                fillPaint.shader = null
            }
            return false
        }
        if (drawable !== lastDrawableRef?.get()) {
            lastDrawableRef = WeakReference(drawable)
            smallBitmap = null
            shader = null // ảnh nền đổi -> bỏ shader cũ
        }
        if (smallBitmap == null && view.width > 0 && view.height > 0) { // FX8: cỡ view <= 0 thì chưa xin (cỡ đích tính theo view)
            // Chưa dựng xong thì xin lại mỗi lần vẽ (rẻ: cache chỉ dựng MỘT lần); xong thì [onSmallReady] vẽ lại owner.
            smallBitmap = (drawable as? BitmapDrawable)?.bitmap?.let { cache.smallOrRequest(it, view.width, view.height, this) }
            if (smallBitmap != null) shader = null
        }
        val small = smallBitmap ?: return false
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0 || !owner.isAttachedToWindow) return false

        // FX8: bản nhỏ phủ TOÀN BỘ bitmap của ImageView nên toạ độ bản nhỏ -> toạ độ drawable (nhân cỡ intrinsic / cỡ bản nhỏ) -> toạ độ
        // view bằng CHÍNH ma trận mà ImageView dùng khi vẽ (centerCrop: scale đồng đều + dịch làm tròn). Không giả định bitmap cùng tỉ lệ view
        // (trước đây `setScale(viewW/small.w, viewH/small.h)` kéo giãn cả bitmap vừa view: sai khi bitmap khác tỉ lệ, D1).
        val dw = drawable.intrinsicWidth.takeIf { it > 0 } ?: small.width
        val dh = drawable.intrinsicHeight.takeIf { it > 0 } ?: small.height
        var s = shader
        if (s == null || w != shaderViewW || h != shaderViewH || dw != shaderDrawableW || dh != shaderDrawableH ||
            small.width != shaderSmallW || small.height != shaderSmallH
        ) {
            if (s == null) {
                s = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader = s
                fillPaint.shader = s
            }
            matrix.set(view.imageMatrix)
            matrix.preScale(dw.toFloat() / small.width, dh.toFloat() / small.height)
            s.setLocalMatrix(matrix)
            shaderViewW = w
            shaderViewH = h
            shaderDrawableW = dw
            shaderDrawableH = dh
            shaderSmallW = small.width
            shaderSmallH = small.height
        }

        GlassLocation.locationOnScreen(
            owner,
            ownerLoc
        ) // GF: bỏ phép dịch của tổ tiên trượt (home_content)
        view.getLocationOnScreen(backdropLoc)
        tx = (ownerLoc[0] - backdropLoc[0]).toFloat()
        ty = (ownerLoc[1] - backdropLoc[1]).toFloat()
        return true
    }

    /** Bản nhỏ dựng xong (main thread): vẽ lại owner để chuyển từ nền phẳng sang kính. */
    override fun onSmallReady() {
        if (owner.isAttachedToWindow) owner.invalidate()
    }

    /** FX1 (C3): cache vừa bỏ bản nhỏ (hệ thống sắp tắt app): nhả bản nhỏ và shader, không vẽ lại (đang ở nền); lần vẽ sau xin lại. */
    override fun onSmallDropped() {
        smallBitmap = null
        shader = null
        fillPaint.shader = null
    }

    /** Owner tách khỏi cửa sổ: bỏ lời xin đang chờ (cache chỉ giữ tham chiếu yếu, nhưng không báo view đã tách). */
    fun cancelPending() {
        cache.cancel(this)
    }

    private var lastAlpha = 255

    override fun setAlpha(alpha: Int) {
        if (alpha == lastAlpha) return
        lastAlpha = alpha
        fillPaint.alpha = alpha
        // GF: alpha của paint chỉ nhân màu shader, còn bộ lọc tint chạy SAU nên ở alpha 0 vẫn còn nguyên lớp tint. Nhân alpha vào tint để mờ vào/ra đúng.
        val t = glassStyle.tint
        val a = (t ushr 24) * alpha / 255
        fillPaint.colorFilter =
            PorterDuffColorFilter((a shl 24) or (t and 0xFFFFFF), PorterDuff.Mode.SRC_OVER)
        flatPaint.alpha = (glassStyle.flat ushr 24) * alpha / 255
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Bộ lọc của fillPaint đã dùng cho tint (Task 6a); drawable này không nhận bộ lọc từ ngoài.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val BORDER_DP = 1f
        const val FADE_MS = 150L
    }
}
