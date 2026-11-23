package gambi.global.os26launcher.domain.repository

import android.content.Intent

/**
 * Trạng thái "app này có đang là launcher mặc định không" và đường để người dùng đặt nó.
 *
 * `Intent` là ngoại lệ có chủ ý với luật "domain không import android.*": kết quả cần trao cho Activity
 * để `startActivity`, không có kiểu thuần Kotlin nào thay được.
 */
interface DefaultLauncherRepository {

    fun isDefaultLauncher(): Boolean

    /** Intent mở màn chọn launcher mặc định; null nếu máy này không có đường nào mở được. */
    fun createSetDefaultIntent(): Intent?

    /** Chỉ đường xin vai trò Home (hộp thoại của hệ thống); null nếu ROM không có (B12). */
    fun createRoleRequestIntent(): Intent?

    /** Các màn hệ thống dự phòng theo thứ tự thử: màn Ứng dụng màn hình chính, rồi Cài đặt chung; chỉ những màn mở được (B12). */
    fun createFallbackIntents(): List<Intent>
}
