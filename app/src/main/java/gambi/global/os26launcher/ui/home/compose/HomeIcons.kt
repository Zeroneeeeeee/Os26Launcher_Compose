package gambi.global.os26launcher.ui.home.compose

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import gambi.global.os26launcher.domain.model.AppIconKey
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** JH1 D7 — nguồn icon của Home Compose: cùng nguồn với Home View (`HomeViewModel.loadIcon`). */
fun interface HomeIconLoader {
    suspend fun load(key: ComponentKey, sizePx: Int): Bitmap?

    /** HW: icon đã nằm sẵn trong cache RAM của process (đồng bộ, không đọc đĩa), null nếu chưa. Mặc định không có. */
    fun peek(key: ComponentKey, sizePx: Int): Bitmap? = null
}

/**
 * JH1 D7 — icon đã nạp, giữ qua các lần quay lại trang để ô không nháy trống (kịch bản (b) của JC). Khóa gồm [AppIconKey] (đổi khi app cập nhật
 * hoặc đổi gói icon) và cỡ px (đổi cỡ lưới thì nạp lại đúng cỡ). Chỉ đọc/ghi trên main thread (composition).
 *
 * HW: [cached] tra thêm cache RAM của process ([HomeIconLoader.peek]) nên Home dựng mới (mở từ Cài đặt, Activity dựng lại) có ngay icon đã nạp
 * ở lần trước / đã làm nóng trong khung đầu. Có [scope] thì các lần [load] cùng khóa đang chạy dùng chung một lần nạp (lưới và lượt nạp sẵn màn
 * đầu của HomeScreen hỏi cùng icon cùng lúc); lần nạp chạy trong [scope] nên người hỏi bị hủy không hủy nó.
 */
class HomeIcons(private val loader: HomeIconLoader, private val scope: CoroutineScope? = null) {
    private val cache = HashMap<Pair<AppIconKey, Int>, ImageBitmap>()
    private val inFlight = HashMap<Pair<AppIconKey, Int>, Deferred<ImageBitmap?>>()

    fun cached(app: AppItem, sizePx: Int): ImageBitmap? {
        val k = app.iconKey to sizePx
        cache[k]?.let { return it }
        if (sizePx <= 0) return null
        return loader.peek(app.key, sizePx)?.asImageBitmap()?.also { cache[k] = it }
    }

    suspend fun load(app: AppItem, sizePx: Int): ImageBitmap? {
        cached(app, sizePx)?.let { return it }
        val k = app.iconKey to sizePx
        val s = scope ?: return loader.load(app.key, sizePx)?.asImageBitmap()?.also { cache[k] = it }
        // LAZY: ghi vào inFlight trước rồi mới chạy, để `finally` (có thể chạy ngay khi Main.immediate) luôn gỡ được đúng mục.
        val job = inFlight[k] ?: s.async(start = CoroutineStart.LAZY) {
            try {
                loader.load(app.key, sizePx)?.asImageBitmap()?.also { cache[k] = it }
            } finally {
                inFlight.remove(k)
            }
        }.also { inFlight[k] = it }
        return job.await()
    }
}

/** Icon của [app] ở cỡ [sizePx]; `null` khi đang nạp hoặc app không có icon. */
@Composable
fun rememberHomeIcon(icons: HomeIcons, app: AppItem, sizePx: Int): ImageBitmap? {
    val bitmap by produceState(icons.cached(app, sizePx), icons, app.iconKey, sizePx) {
        // produceState giữ state qua các lần đổi khóa (state không gắn khóa): luôn gán lại, không dựa vào giá trị cũ.
        value = icons.cached(app, sizePx)
        if (value == null && sizePx > 0) value = icons.load(app, sizePx)
    }
    return bitmap
}
