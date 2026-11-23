package gambi.global.os26launcher.domain.repository

import android.graphics.Bitmap
import gambi.global.os26launcher.domain.model.AppShortcutItem
import gambi.global.os26launcher.domain.model.ComponentKey

interface ShortcutRepository {
    /**
     * Shortcut của activity [key] (động + khai báo trong manifest, đã bật), tối đa [MAX_SHORTCUTS], theo rank.
     * Icon vẽ sẵn thành bitmap vuông [iconSizePx]. Mọi lỗi (không phải launcher mặc định -> SecurityException,
     * app vừa bị gỡ, profile không còn...) trả [emptyList]: menu vẫn hiện được các mục cố định. Chặn, chạy trên luồng nền.
     */
    suspend fun getShortcuts(key: ComponentKey, iconSizePx: Int): List<AppShortcutItem>

    /**
     * C8: icon của ĐÚNG MỘT shortcut ([shortcutId] của app [key]), vẽ thành bitmap vuông [iconSizePx]; có cache ngắn hạn.
     * Dùng cho hàng shortcut trong tìm kiếm (thay vì [getShortcuts] nạp tới [MAX_SHORTCUTS] icon để dùng 1). Mọi lỗi trả `null`.
     */
    suspend fun shortcutIcon(key: ComponentKey, shortcutId: String, iconSizePx: Int): Bitmap?

    /** M8d: launcher có quyền đọc shortcut của app khác không (chỉ khi là launcher mặc định). Không ném lỗi. */
    fun canReadShortcuts(): Boolean

    /** Mở shortcut [shortcutId] của app [key]. false nếu hệ thống từ chối hoặc shortcut không còn. */
    fun startShortcut(key: ComponentKey, shortcutId: String): Boolean

    companion object {
        const val MAX_SHORTCUTS = 4
    }
}
