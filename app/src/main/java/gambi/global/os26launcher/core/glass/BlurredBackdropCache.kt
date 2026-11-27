package gambi.global.os26launcher.core.glass

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.scale
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Làm mờ bitmap nền MỘT LẦN, ngoài main thread, thành một bản NHỎ ([smallOrRequest]): thu bằng các bước chia đôi (trung bình
 * thật) rồi box blur, không cần RenderEffect (chỉ có từ API 31), chạy được từ API 26 (B7). Người dùng bản nhỏ ([BackdropBlurDrawable],
 * [StaticBlurSurface]) để GPU phóng lên bằng lọc bilinear khi vẽ. Cache theo danh tính (===) bitmap nguồn: backdrop không đổi thì
 * không dựng lại. (Hàm `blur()` cũ thu thẳng xuống 8% bằng một lần `scale` rồi phóng lại cỡ nguồn ngay trên main thread: răng
 * cưa lộ thành ô vuông sau dock tĩnh; đã gỡ ở GL Task 9, dock cũng dùng bản nhỏ.)
 */
class BlurredBackdropCache {
    // FX1 (C3): nguồn giữ bằng tham chiếu YẾU: cache chỉ cần so danh tính, không được giữ ảnh nền đầy đủ cũ (tới hàng chục MB) sống sau
    // khi đổi ảnh hoặc dựng lỗi.
    private var smallSource: WeakReference<Bitmap>? = null
    private var smallBitmap: Bitmap? = null

    // FX8: cỡ đích (theo cỡ VIEW ảnh nền, không theo cỡ bitmap) của bản nhỏ đang giữ và của lần dựng đang chạy: độ mờ tính bằng px màn hình
    // phải như nhau với mọi ảnh (trước đây bản nhỏ = 25% cỡ BITMAP nên ảnh nhỏ bị mờ gấp ~2 lần, D1-2).
    private var smallTargetW = 0
    private var smallTargetH = 0
    private var pendingTargetW = 0
    private var pendingTargetH = 0

    // GL Task 8 Step 1b: `small()` đo 78.8 ms trên main thread (Oppo, 1224x1632 -> 306x408) nên dựng ngoài main thread. Mọi trạng thái
    // dưới đây chỉ đụng từ main thread; luồng dựng chỉ ĐỌC bitmap nguồn (ảnh nền không bao giờ bị recycle sau khi vào state).
    private val main = Handler(Looper.getMainLooper())
    private val executor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "GlassBlur").apply {
                isDaemon = true; priority = Thread.NORM_PRIORITY - 1
            }
        }
    }
    private var pendingSource: WeakReference<Bitmap>? = null
    private var failedSource: WeakReference<Bitmap>? = null
    private val waiters = ArrayList<WeakReference<SmallListener>>()

    /**
     * GT: bán kính box blur của bản nhỏ (độ mờ của `GlassLook`, qua `GlassTuning.blurRadiusPx`). Đổi thì bỏ bản đang giữ và mọi lần
     * dựng đang chạy ([generation]); bản mới dựng ở lần xin kế tiếp. Người đang giữ bản cũ (drawable của Home) không được báo: Home
     * dựng lại khi thông số kính đổi. Main thread.
     */
    var blurRadiusPx: Int = DEFAULT_BLUR_RADIUS_PX
        set(value) {
            val v = value.coerceAtLeast(0)
            if (v == field) return
            field = v
            generation++
            smallSource = null
            smallBitmap = null
            smallTargetW = 0
            smallTargetH = 0
            pendingSource = null
            pendingTargetW = 0
            pendingTargetH = 0
            failedSource = null
        }
    private var generation = 0

    // AN Task 10: người dùng bản mờ (BackdropBlurDrawable) đăng ký SUỐT ĐỜI (tham chiếu yếu), khác [waiters] chỉ tồn tại lúc chờ dựng.
    private val registered = ArrayList<WeakReference<SmallListener>>()

    /** Được báo (trên main thread) khi bản nhỏ đã dựng xong. Cache chỉ giữ tham chiếu YẾU tới listener. */
    fun interface SmallListener {
        fun onSmallReady()

        /** FX1 (C3): [trim] vừa bỏ bản nhỏ; người giữ bản nhỏ phải bỏ tham chiếu của mình (không cần vẽ lại, lần vẽ sau sẽ xin lại). */
        fun onSmallDropped() {}
    }

    /**
     * GL: bản mờ nhỏ của [source], KHÔNG phóng lại. Kính tại chỗ ([BackdropBlurDrawable]) để GPU phóng lên bằng lọc bilinear khi
     * vẽ nên không cần bitmap cỡ màn hình như [blur]. Cache riêng theo danh tính bitmap nguồn; không đụng tới [blur] (dock giữ
     * nguyên).
     *
     * Nguyên nhân vỡ ô trên panel lớn (Oppo, 2026-11-04): bản cũ thu thẳng xuống 8% bằng MỘT lần `scale` (bilinear, không
     * mipmap) nên mỗi điểm ảnh nhỏ chỉ lấy 4 điểm ảnh nguồn trong 156 (răng cưa, không phải trung bình), rồi GPU phóng ~12x bằng
     * bilinear nên lộ từng ô vuông; ô thư mục nhỏ che được, panel ~300 dp thì không. Sửa: (1) thu bằng các bước chia đôi (mỗi bước
     * bilinear đúng bằng trung bình 2x2) xuống [SMALL_FACTOR] (25%, trần [MAX_SMALL_SIDE] px cạnh dài); (2) làm mờ thật một lần ở
     * độ phân giải đó ([boxBlur] 3 lượt, bán kính [BLUR_RADIUS_PX]) -> phóng 4x bằng bilinear ra ảnh mượt, độ mờ tương đương bản
     * cũ (~12 px ở cỡ nguồn). Chi phí MỘT LẦN mỗi ảnh nền; mỗi khung vẽ không đổi (cùng một BitmapShader, lấy mẫu bilinear).
     *
     * Bất đồng bộ (Task 8 Step 1b): trả bản nhỏ nếu đã có cho đúng [source]; chưa có thì trả null (người gọi vẽ phẳng), dựng MỘT
     * lần trên luồng riêng và báo [listener] (tham chiếu yếu) trên main thread khi xong. Gọi từ main thread.
     *
     * FX8: [viewW] x [viewH] là cỡ view ảnh nền; bản nhỏ có cỡ = 25% cỡ view (trần [MAX_SMALL_SIDE]) bất kể cỡ bitmap, nên độ mờ như nhau với mọi
     * ảnh. Bản nhỏ luôn phủ TOÀN BỘ bitmap nguồn (người dùng ánh xạ bằng cỡ của chính nó), nên cỡ view đổi thì bản cũ vẫn đúng hình học: trả bản
     * cũ trong lúc dựng lại.
     */
    fun smallOrRequest(source: Bitmap, viewW: Int, viewH: Int, listener: SmallListener): Bitmap? {
        val (tw, th) = targetSize(viewW, viewH)
        val have = smallBitmap?.takeIf { source === smallSource?.get() }
        if (have != null && tw == smallTargetW && th == smallTargetH) return have
        if (source === failedSource?.get()) return have // dựng lỗi một lần thì thôi: vẽ phẳng (hoặc bản cũ), không thử lại mỗi khung
        if (waiters.none { it.get() === listener }) waiters.add(WeakReference(listener))
        request(source, tw, th)
        return have
    }

    /**
     * FX8: làm nóng bản nhỏ của [source] ngay khi ảnh nền nạp xong (không cần ô kính nào vẽ trước), để kính hiện ra đã có nền mờ thay vì
     * vẽ phẳng rồi đổi đột ngột. Không có người nghe riêng: người dùng đã đăng ký ([register]) được báo khi dựng xong. Gọi từ main thread.
     */
    fun prewarm(source: Bitmap, viewW: Int, viewH: Int) {
        val (tw, th) = targetSize(viewW, viewH)
        if (smallBitmap != null && source === smallSource?.get() && tw == smallTargetW && th == smallTargetH) return
        if (source === failedSource?.get()) return
        request(source, tw, th)
    }

    /** Dựng MỘT lần cho mỗi (bitmap nguồn, cỡ đích) dù nhiều ô cùng xin. */
    private fun request(source: Bitmap, tw: Int, th: Int) {
        if (pendingSource?.get() === source && pendingTargetW == tw && pendingTargetH == th) return
        pendingSource = WeakReference(source)
        pendingTargetW = tw
        pendingTargetH = th
        val gen = generation // GT: độ mờ đổi trong lúc dựng thì bỏ kết quả
        val radius = blurRadiusPx
        executor.execute {
            val built = try {
                build(source, tw, th, radius)
            } catch (e: Throwable) { // vd. bitmap nguồn bị recycle giữa chừng, hoặc OutOfMemoryError (FX1: Error cũng không được giết process)
                Log.w(TAG, "small() build failed", e)
                null
            }
            main.post { onBuilt(source, tw, th, built, gen) }
        }
    }

    /** FX8: cỡ bản nhỏ = 25% cỡ view, kẹp [MAX_SMALL_SIDE] theo cạnh dài; người gọi không xin khi cỡ view <= 0. */
    private fun targetSize(viewW: Int, viewH: Int): Pair<Int, Int> {
        val scale = min(SMALL_FACTOR, MAX_SMALL_SIDE.toFloat() / max(max(viewW, viewH), 1))
        return Pair((viewW * scale).toInt().coerceAtLeast(1), (viewH * scale).toInt().coerceAtLeast(1))
    }

    /** AN Task 10: đăng ký nhận [notifyBackdropChanged]. Giữ tham chiếu yếu nên không cần gỡ; view tách khỏi cửa sổ thì bỏ qua khi được báo. */
    fun register(listener: SmallListener) {
        if (registered.none { it.get() === listener }) registered.add(WeakReference(listener))
    }

    /**
     * AN Task 10: ảnh nền của Home vừa đổi (nạp đầu, thay, xoá). Kính tại chỗ chỉ thấy ảnh mới ở lần vẽ kế tiếp (so danh tính bitmap lúc vẽ) nên
     * phải được vẽ lại: báo mọi người dùng đã đăng ký (drawable vẽ lại owner nếu còn gắn vào cửa sổ). Gọi từ main thread.
     */
    fun notifyBackdropChanged() {
        registered.removeAll { it.get() == null }
        registered.mapNotNull { it.get() }.forEach { it.onSmallReady() }
    }

    /** Huỷ lời xin của [listener] (view tách khỏi cửa sổ): không giữ và không báo nó nữa. */
    fun cancel(listener: SmallListener) {
        waiters.removeAll { val l = it.get(); l == null || l === listener }
    }

    /**
     * FX1 (C3): hệ thống sắp tắt app (`TRIM_MEMORY_COMPLETE`, xem [GlassEnvironment]): bỏ bản nhỏ đã dựng và dấu "dựng lỗi" (để thử lại khi
     * bộ nhớ khá hơn), báo người giữ bản nhỏ nhả tham chiếu. KHÔNG đụng lần dựng đang chạy. Lần vẽ sau (khi quay về) xin dựng lại, nên có thể
     * vẽ phẳng một-hai khung trước khi kính hiện lại. Bản nhỏ chỉ ~0,57 MB nên số bộ nhớ trả về nhỏ. Gọi từ main thread.
     */
    fun trim() {
        smallBitmap = null
        smallSource = null
        smallTargetW = 0
        smallTargetH = 0
        failedSource = null
        registered.removeAll { it.get() == null }
        registered.mapNotNull { it.get() }.forEach { it.onSmallDropped() }
    }

    private fun onBuilt(source: Bitmap, tw: Int, th: Int, built: Bitmap?, gen: Int) {
        // Ảnh nền, cỡ đích hoặc độ mờ (GT) đã đổi trong lúc dựng: bỏ kết quả cũ
        if (gen != generation || pendingSource?.get() !== source || pendingTargetW != tw || pendingTargetH != th) {
            built?.recycle()
            return
        }
        pendingSource = null
        if (built == null) {
            failedSource = WeakReference(source)
            waiters.clear()
            return
        }
        smallSource = WeakReference(source)
        smallBitmap = built // bản cũ không recycle: shader của ô đang vẽ có thể còn dùng, để GC lo
        smallTargetW = tw
        smallTargetH = th
        // FX8: báo cả người đang chờ lẫn người đã đăng ký (prewarm không có người chờ; ô đã vẽ phẳng cần vẽ lại). Trùng nhau thì báo một lần.
        val toNotify = LinkedHashSet<SmallListener>()
        waiters.mapNotNullTo(toNotify) { it.get() }
        registered.mapNotNullTo(toNotify) { it.get() }
        waiters.clear()
        toNotify.forEach { it.onSmallReady() }
    }

    private fun build(source: Bitmap, w: Int, h: Int, radius: Int): Bitmap = smoothBlurred(source, w, h, radius)

    companion object {
        /** Bán kính trước GT; `GlassTuning.blurRadiusPx` trả đúng số này ở độ mờ mặc định. */
        const val DEFAULT_BLUR_RADIUS_PX = 2
        private const val TAG = "BlurredBackdropCache"
        private const val SMALL_FACTOR = 0.25f
        private const val MAX_SMALL_SIDE = 640
    }
}

/**
 * FX (2026-11-27): hàm cấp file (không dùng trạng thái của cache) để ảnh chụp lớp Home sau bảng thư mục làm mờ thẳng trên luồng nền, không qua cache.
 * Thu (hoặc phóng, khi bitmap nhỏ hơn đích) [source] về [w] x [h]: các bước chia đôi (trung bình thật) khi thu, rồi box blur 3 lượt
 * bán kính [radius] (GT; 0 = không mờ thêm).
 * FX8: ảnh có alpha được nhân alpha vào RGB (như ImageView vẽ lên nền đen của home_root) trước khi mờ.
 */
internal fun smoothBlurred(source: Bitmap, w: Int, h: Int, radius: Int): Bitmap {
    var cur = source
    while (cur.width / 2 >= w && cur.height / 2 >= h) {
        val half = cur.scale(cur.width / 2, cur.height / 2)
        if (cur !== source) cur.recycle()
        cur = half
    }
    val sized = if (cur.width != w || cur.height != h) cur.scale(w, h) else cur
    if (cur !== source && cur !== sized) cur.recycle()
    // `scale` trả cùng bitmap khi cỡ không đổi và bitmap nguồn không sửa được: luôn làm việc trên bản sao ARGB_8888 sửa được.
    val out =
        if (sized.isMutable && sized !== source && sized.config == Bitmap.Config.ARGB_8888) {
            sized
        } else {
            sized.copy(Bitmap.Config.ARGB_8888, true)
                .also { if (sized !== source) sized.recycle() }
        }
    if (radius <= 0) return out
    val px = IntArray(w * h)
    out.getPixels(px, 0, w, 0, 0, w, h)
    if (source.hasAlpha()) premultiplyOverBlack(px)
    val tmp = IntArray(w * h)
    repeat(BLUR_PASSES) {
        boxBlur(px, tmp, w, h, radius, horizontal = true)
        boxBlur(tmp, px, w, h, radius, horizontal = false)
    }
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

/** FX8: `getPixels` trả màu KHÔNG premultiply; ImageView vẽ lên nền đen nên màu hiển thị là RGB·α/255. */
private fun premultiplyOverBlack(px: IntArray) {
    for (i in px.indices) {
        val c = px[i]
        val a = c ushr 24
        if (a == 0xFF) continue
        val r = ((c shr 16) and 0xFF) * a / 255
        val g = ((c shr 8) and 0xFF) * a / 255
        val b = (c and 0xFF) * a / 255
        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** Box blur một chiều, bán kính [r], biên kẹp; ảnh nền đục nên alpha giữ 0xFF. */
private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val window = 2 * r + 1
    for (line in 0 until lines) {
        val base = if (horizontal) line * w else line
        val step = if (horizontal) 1 else w
        var sr = 0
        var sg = 0
        var sb = 0
        for (j in -r..r) {
            val c = src[base + j.coerceIn(0, len - 1) * step]
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
        }
        for (i in 0 until len) {
            dst[base + i * step] =
                (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            val add = src[base + min(i + r + 1, len - 1) * step]
            val sub = src[base + max(i - r, 0) * step]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

private const val BLUR_PASSES = 3
