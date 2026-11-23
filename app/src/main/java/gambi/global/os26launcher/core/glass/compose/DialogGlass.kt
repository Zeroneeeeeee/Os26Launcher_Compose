package gambi.global.os26launcher.core.glass.compose

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import gambi.global.os26launcher.core.glass.GlassTuning

/**
 * 2026-11-23 (chủ dự án: "các dialog chưa có nền liquid") — kính cho hộp thoại nằm ở CỬA SỔ RIÊNG (`Dialog`). Kính Backdrop không lấy mẫu
 * được cửa sổ Home (khác cửa sổ, khác toạ độ), nên dùng blur nền cửa sổ của hệ thống (`Window.setBackgroundBlurRadius`, API 31+): hệ thống
 * làm mờ phần màn hình phía sau đúng vùng cửa sổ, bo theo hình của nền cửa sổ ([GradientDrawable] bo [cornerPx]). Cửa sổ hộp thoại của Compose
 * với `usePlatformDefaultWidth = false` ôm vừa nội dung, nên vùng mờ trùng thẻ.
 *
 * Đọc thông số kính: độ mờ ("Độ mờ", sàn [MIN_BLUR_DP] để chữ đọc được), độ bóng (tint qua [GlassTuning.tint]), bo góc do caller truyền
 * (đã nhân "Bo góc"). Máy không có blur xuyên cửa sổ (API < 31, tiết kiệm pin, ROM tắt) thì nền đặc [opaqueColor] như trước.
 *
 * [progress] 0..1: độ hiện của hộp thoại (nền và vùng mờ hiện dần theo). Trả `true` khi đang có kính (nội dung nên vẽ trong suốt).
 */
@Composable
fun DialogWindowGlass(window: Window?, cornerPx: Float, glassTint: Int, opaqueColor: Int, progress: () -> Float = { 1f }): Boolean {
    val context = LocalContext.current
    val density = LocalDensity.current
    val look by rememberGlassLook()
    val glass = remember(window) {
        mutableStateOf(
            window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        )
    }
    val blurPx = with(density) { maxOf(GlassTuning.backdropBlurDp(look), MIN_BLUR_DP).dp.roundToPx() }
    val color = if (glass.value) GlassTuning.tint(look, glassTint) else opaqueColor
    val drawable = remember(window) { GradientDrawable() }
    DisposableEffect(window, cornerPx, color) {
        if (window != null) {
            drawable.cornerRadius = cornerPx
            drawable.setColor(color)
            window.setBackgroundDrawable(drawable)
        }
        onDispose {}
    }
    LaunchedEffect(window, blurPx, glass.value) {
        if (window == null) return@LaunchedEffect
        snapshotFlow { progress().coerceIn(0f, 1f) }.collect { p ->
            drawable.alpha = (p * 255).toInt()
            if (glass.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius((blurPx * p).toInt())
        }
    }
    return glass.value
}

/** Màu [color] với alpha [alpha] (0..1), giữ RGB: tint kính của hộp thoại suy từ màu nền đặc cũ. */
fun glassTintOf(color: Int, alpha: Float): Int = ColorUtils.setAlphaComponent(color, (alpha * 255).toInt().coerceIn(0, 255))

private const val MIN_BLUR_DP = 24f

