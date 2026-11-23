package gambi.global.os26launcher.core.ui

import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat

/**
 * EF (2026-11-18, đo trên Tecno Spark 40 KM5n, Android 15): đặt lại trạng thái tràn viền của cửa sổ sau `setContentView`.
 *
 * Lỗi: `recreate()` (đổi mức kính/kiểu icon ở Cài đặt, đổi ngôn ngữ) giữ lại DecorView cũ, và trên máy này khung nội dung mất trạng
 * thái tràn viền: các view "fitsSystemWindows tùy chọn" của khung (LinearLayout gốc của framework, `action_bar_root` của AppCompat)
 * tự đệm theo inset thanh hệ thống (80/80 px), ăn inset nên view của app không nhận được, cả màn lệch xuống và hụt đáy.
 * `enableEdgeToEdge()`/`setDecorFitsSystemWindows(false)` gọi lại không đủ; chặn đệm ở từng view thì đệm dời xuống view kế.
 *
 * Cách sửa: các view đó chỉ đệm khi cửa sổ KHÔNG có cờ layout tràn viền kiểu cũ, nên đặt lại 3 cờ `SYSTEM_UI_FLAG_LAYOUT_*` cho cả
 * cửa sổ rồi xin phát lại inset. Cờ chỉ đổi cách layout, không ẩn thanh hệ thống (không xung đột với `WindowInsetsController` ẩn
 * thanh trạng thái ở edit mode). API < 30: `setDecorFitsSystemWindows(false)` vốn đặt đúng 3 cờ này nên không đổi gì.
 *
 * Gọi ngay sau `setContentView` ở mọi Activity tự vẽ tràn viền (đã gọi `enableEdgeToEdge`/`setDecorFitsSystemWindows(false)`).
 */
@Suppress("DEPRECATION")
fun reassertEdgeToEdge(window: Window) {
    val decor = window.decorView
    decor.systemUiVisibility = decor.systemUiVisibility or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    ViewCompat.requestApplyInsets(decor)
}
