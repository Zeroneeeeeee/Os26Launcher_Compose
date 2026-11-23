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

/** JH1 D7 — nguồn icon của Home Compose: cùng nguồn với Home View (`HomeViewModel.loadIcon`). */
fun interface HomeIconLoader {
    suspend fun load(key: ComponentKey, sizePx: Int): Bitmap?
}

/**
 * JH1 D7 — icon đã nạp, giữ qua các lần quay lại trang để ô không nháy trống (kịch bản (b) của JC). Khóa gồm [AppIconKey] (đổi khi app cập nhật
 * hoặc đổi gói icon) và cỡ px (đổi cỡ lưới thì nạp lại đúng cỡ). Chỉ đọc/ghi trên main thread (composition).
 */
class HomeIcons(private val loader: HomeIconLoader) {
    private val cache = HashMap<Pair<AppIconKey, Int>, ImageBitmap>()

    fun cached(app: AppItem, sizePx: Int): ImageBitmap? = cache[app.iconKey to sizePx]

    suspend fun load(app: AppItem, sizePx: Int): ImageBitmap? =
        cache[app.iconKey to sizePx] ?: loader.load(app.key, sizePx)?.asImageBitmap()?.also { cache[app.iconKey to sizePx] = it }
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
