package gambi.global.os26launcher.domain.model

import android.graphics.Bitmap

/**
 * M7 Task 10 — một shortcut (động hoặc khai báo trong manifest) của app, hiện trong menu nhấn giữ icon.
 * [icon] có thể null (shortcut không có icon hoặc nạp lỗi): UI vẫn hiện dòng chữ.
 * (Bitmap là ngoại lệ có chủ ý của luật phụ thuộc, giống [gambi.global.os26launcher.domain.repository.AppRepository.icon].)
 */
data class AppShortcutItem(
    val id: String,
    val shortLabel: String,
    val icon: Bitmap?
)
