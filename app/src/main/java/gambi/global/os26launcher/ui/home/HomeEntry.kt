package gambi.global.os26launcher.ui.home

import android.content.Context
import android.content.Intent

/**
 * HC Task 2 — mở lưới Home từ nơi khác trong app (Hero Card "Trải nghiệm"/"Khám phá ›", Onboarding xong). Từ HC, mở app bằng icon vào
 * Cài đặt (task riêng); `HomeActivity` (singleTask) chạy ở task của nó: là launcher mặc định thì đây là màn Home của máy, chưa thì là
 * một task thường (Back đóng, về Cài đặt).
 */
object HomeEntry {
    fun open(context: Context) {
        context.startActivity(Intent(context, HomeActivity::class.java))
    }
}
