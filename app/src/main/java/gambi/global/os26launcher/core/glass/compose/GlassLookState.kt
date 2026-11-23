package gambi.global.os26launcher.core.glass.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.glass.GlassTuning
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * JD Task 2 — thông số kính hiện tại ([GlassLook]) dưới dạng state Compose. Nghe [GlassEnvironment.addListener] (gọi trên main thread
 * khi cấu hình kính đổi), nên kéo thanh trong màn "Liquid Glass" thì kính của màn Compose đang mở đổi ngay, không cần mở lại màn.
 */
@Composable
fun rememberGlassLook(): State<GlassLook> {
    val env = GlassEnvironment.from(LocalContext.current)
    val state = remember(env) { mutableStateOf(env.look) }
    DisposableEffect(env) {
        val listener: () -> Unit = { state.value = env.look }
        env.addListener(listener)
        state.value = env.look // bắt kịp nếu cấu hình đổi giữa lúc remember và lúc gắn listener
        onDispose { env.removeListener(listener) }
    }
    return state
}

/**
 * 2026-11-23: hệ số nhân bán kính của thanh "Bo góc" (`GlassTuning.cornerScale`, 1 ở mặc định) cho hình Compose vẽ tay quanh icon (nền thư mục
 * kính mờ, tấm hover, ảnh kéo), để chúng bo cùng icon và kính. Đọc thông số xem trước nếu có ([LocalGlassLookOverride]).
 */
@Composable
fun glassCornerScale(): Float = GlassTuning.cornerScale((LocalGlassLookOverride.current ?: rememberGlassLook()).value)
