package gambi.global.os26launcher.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * HC Task 2 — điểm vào app từ icon (MAIN/LAUNCHER). Activity rỗng mang theme khởi động (splash như Home), mở [SettingsActivity] ở cùng
 * task rồi đóng ngay. Tách khỏi `SettingsActivity` để màn Cài đặt mở từ Home (edit mode) không mang theme khởi động (lóe splash khi chuyển).
 * Task riêng (`taskAffinity` trong manifest) để không lẫn với task Home.
 */
class LauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FROM_LAUNCHER, true))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
