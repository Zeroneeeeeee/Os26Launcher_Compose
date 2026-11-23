package gambi.global.os26launcher.domain.model

/** Khóa của một activity mở được. Ba trường này cũng là khóa dùng trong Room ở M4. */
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long
)

data class AppItem(
    val key: ComponentKey,
    val label: String,
    /** G2a B1: loại app cho App Library; mặc định để chỗ dựng cũ không vỡ. */
    val category: AppCategory = AppCategory.UNDEFINED,
    /** G2a B1: app hệ thống (`FLAG_SYSTEM`), dùng cho luật dự phòng "Tiện ích". */
    val isSystem: Boolean = false,
    /**
     * A4/B7: dấu vân tay APK (`sourceDir`), đổi mỗi lần app cài/cập nhật. Có trong [AppItem] để app cập nhật thì `AppItem`
     * mới khác `AppItem` cũ (mọi `DiffUtil` và phép so `==` tự thấy) và để khóa cache icon ở mọi tầng mang dấu này. Rỗng nếu không biết.
     */
    val versionStamp: String = "",
    /**
     * C10: nhãn dành cho trợ năng (`contentDescription`) của app hồ sơ công việc, vd "Chrome (Công việc)" do hệ thống dịch; rỗng thì dùng
     * [label]. Không dùng để hiển thị và không vào cache nhãn (chủ dự án 2026-11-11: chỉ huy hiệu trên icon).
     */
    val a11yLabel: String = "",
    /**
     * RN: tên gốc do hệ thống đặt (hoặc lấy từ cache LC). [label] là TÊN HIỂN THỊ: bằng [originalLabel] trừ khi người dùng đã đặt tên riêng
     * (lớp phủ trong `AppRepositoryImpl.apps`). Mặc định = [label] nên chỗ dựng cũ không đổi.
     */
    val originalLabel: String = label,
    /** LO D6: `LauncherActivityInfo.getFirstInstallTime()` (ms), 0 = không biết. Nguồn của nhóm "Mới thêm" trong App Library. */
    val firstInstallMillis: Long = 0L
) {
    /** A4: khóa cho map bitmap của view: cùng app nhưng APK mới là khóa mới. Thuộc tính thân nên không vào `equals`. */
    val iconKey: AppIconKey = AppIconKey(key, versionStamp)
}

/** A4: khóa icon theo APK; map bitmap của view dùng khóa này thay cho [ComponentKey] trần. */
data class AppIconKey(val key: ComponentKey, val stamp: String)
