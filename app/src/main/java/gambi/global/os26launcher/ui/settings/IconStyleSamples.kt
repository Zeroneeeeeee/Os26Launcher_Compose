package gambi.global.os26launcher.ui.settings

import androidx.annotation.StringRes
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.IconStyle

/**
 * P3-3 → TR (2026-11-26): chọn 4 app mẫu để vẽ thử kiểu icon. Dùng chung cho khung xem trước của Cài đặt → Bộ biểu tượng
 * ([IconStylePreviewViewModel]) và bước chọn kiểu icon của Onboarding (`IconStyleChoicesViewModel`). Danh sách vào phải là app ĐƯỢC HIỆN
 * (P3-7: app ẩn không bao giờ làm mẫu).
 */
object IconStyleSamples {

    const val COUNT = 4

    /** Đoạn tên gói của app mẫu theo thứ tự ưu tiên (chủ dự án: icon thời tiết hoặc app phổ thông). */
    private val HINTS = listOf("weather", "camera", "android.settings", "calendar")

    /**
     * 4 app mẫu: Thời tiết, Máy ảnh, Cài đặt, Lịch (khớp theo tên gói, như app phổ thông trên máy), thiếu thì bù app hệ thống rồi app bất
     * kỳ. Mỗi gói một lần.
     */
    fun pick(apps: List<AppItem>): List<AppItem> {
        val out = ArrayList<AppItem>(COUNT)
        for (hint in HINTS) {
            apps.firstOrNull { a -> a.key.packageName.contains(hint, ignoreCase = true) && out.none { it.key.packageName == a.key.packageName } }
                ?.let(out::add)
        }
        for (a in apps.filter { it.isSystem } + apps) {
            if (out.size >= COUNT) break
            if (out.none { it.key.packageName == a.key.packageName }) out.add(a)
        }
        return out.take(COUNT)
    }
}

/** Tên hiển thị của kiểu icon (Cài đặt → Bộ biểu tượng, bước chọn kiểu icon của Onboarding). */
@StringRes
internal fun IconStyle.labelRes(): Int = when (this) {
    IconStyle.DEFAULT -> R.string.settings_icon_style_default
    IconStyle.TINTED -> R.string.settings_icon_style_tinted
    IconStyle.THREE_D -> R.string.settings_icon_style_3d
    IconStyle.GLASS -> R.string.settings_icon_style_glass
}
