package gambi.global.os26launcher.core.glass.compose

import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface

/**
 * SW Task 3 (C', chủ dự án 2026-11-25) — kính THẬT vẽ MỘT lần rồi giữ thành ảnh, cho bề mặt đứng yên trên một nền đứng yên (dock Home):
 * kính thật (`dsGlass`, API ≥ 31: vibrancy + blur + lens + viền sáng) chạy RenderEffect mỗi lần lớp của nó được vẽ lại; dock không đổi gì
 * khi lướt trang nhưng đo được vẫn tốn (PG4 / SW Task 2). Ở đây kính vẽ "sống" vài khung, chụp lớp thành ảnh (`GraphicsLayer.toImageBitmap`),
 * rồi bỏ hẳn kính sống khỏi composition và chỉ vẽ ảnh.
 *
 * Kính sống trở lại (rồi chụp lại) khi [cacheKey] đổi (người gọi đưa danh tính ảnh nền), thông số kính đổi, cỡ đổi, hoặc nền sau kính đổi
 * ([LocalDsBackdrop]). Trong lúc [live] trả true (vd Home đang trượt theo Today / Thư viện: bề mặt dịch so với ảnh nền nên ảnh chụp sai chỗ)
 * thì vẽ kính sống, ảnh chụp giữ lại để dùng khi dừng.
 *
 * Giới hạn: nền ĐỘNG (hình nền video / GIF) sẽ đứng ở khung lúc chụp; hiện app chỉ có ảnh nền tĩnh (hình nền hệ thống không lấy mẫu được).
 * Dưới API 31 không có kính thật: vẽ thẳng `dsGlass` (kính mờ tĩnh của PG3 đã rẻ).
 *
 * [onLiveDraw]: gọi mỗi lần kính sống được vẽ (log đo của Home, `HomePerfLog`), để chứng minh kính không vẽ lại khi lướt trang.
 */
@Composable
fun CachedGlass(
    surface: DsSurface,
    corner: DsCorner,
    cacheKey: Any?,
    modifier: Modifier = Modifier,
    live: () -> Boolean = { false },
    enabled: Boolean = true,
    onLiveDraw: (() -> Unit)? = null,
    onCaptured: ((IntSize) -> Unit)? = null
) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        // Không chụp: kính vẽ thẳng như trước. Vẫn đếm lần vẽ (API ≥ 31) để so với khi có ảnh chụp.
        val count = if (onLiveDraw != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.drawWithContent {
                onLiveDraw()
                drawContent()
            }
        } else {
            Modifier
        }
        Box(modifier.then(count).dsGlass(surface, corner))
        return
    }
    val look by rememberGlassLook()
    val backdrop = LocalDsBackdrop.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val key = CacheKey(cacheKey, look, size, backdrop)
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var imageKey by remember { mutableStateOf<CacheKey?>(null) }
    val forceLive by rememberUpdatedState(live)
    val layer = rememberGraphicsLayer()
    val stale = image == null || imageKey != key
    // Đọc [live] trong pha dựng: người gọi nên đưa lambda đọc state đã gộp (derivedStateOf) để chỉ dựng lại ở lúc bật / tắt.
    val showLive = stale || forceLive()
    Box(modifier.onSizeChanged { size = it }) {
        if (showLive) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        onLiveDraw?.invoke()
                        // Ghi chính bề mặt kính (gồm lớp Backdrop con) vào [layer] để chụp, đồng thời vẽ ra màn.
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .dsGlass(surface, corner)
            )
            if (stale && backdrop != null && size != IntSize.Zero) {
                LaunchedEffect(key) {
                    // Chờ kính sống vẽ xong (lớp nền đã ghi, RenderEffect đã gắn) rồi mới chụp.
                    withFrameNanos {}
                    withFrameNanos {}
                    withFrameNanos {}
                    val shot = try {
                        layer.toImageBitmap()
                    } catch (e: Throwable) {
                        Log.w(TAG, "capture failed", e)
                        null
                    }
                    if (shot != null) {
                        image = shot
                        imageKey = key
                        onCaptured?.invoke(size)
                    }
                }
            }
        } else {
            val img = image
            Box(Modifier.matchParentSize().drawBehind { if (img != null) drawImage(img) })
        }
    }
}

/** Mọi thứ làm ảnh chụp kính hết đúng. [backdrop] so theo danh tính (cùng lớp nền = cùng nguồn). */
private data class CacheKey(val user: Any?, val look: Any, val size: IntSize, val backdrop: Any?)

private const val TAG = "CachedGlass"
