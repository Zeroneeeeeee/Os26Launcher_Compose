package gambi.global.os26launcher.core.designsystem

/**
 * DS3 Task 1b: vai bo góc của thành phần v3. Bán kính gốc của từng vai nằm ở `ds_radius_*` (`ds_dimens.xml`).
 *
 * [isGlass] = vai là bề mặt kính: bán kính thật = gốc nhân theo thanh "Bo góc" của kính (GT, `GlassTuning.cornerRadius`), giống
 * mọi bề mặt kính khác của launcher. [TILE] (ô icon trong hàng) không phải kính: giữ cố định tới khi có cơ chế bo góc cho icon
 * (plan IS). Chủ dự án chốt 2026-11-16: bo góc sẵn có dành cho kính, cơ chế bo góc cho icon là việc riêng.
 */
enum class DsCornerRole(val isGlass: Boolean) {
    CARD(true),
    CARD_LARGE(true),
    SHEET(true),
    TILE(false)
}

/** DS3 Task 1b: cách một thành phần v3 bo góc. Thành phần luôn nhận bo qua kiểu này, không đọc thẳng `ds_radius_*`. */
sealed interface DsCorner {
    /** Nửa cạnh ngắn (nút, chip, segmented, ô tìm, nút tròn). Hình học, không theo cài đặt. */
    data object Pill : DsCorner

    /** Theo vai: xem [DsCornerRole]. */
    data class Role(val role: DsCornerRole) : DsCorner

    /**
     * Số cố định, chỉ cho bo nội bộ nhỏ của thành phần (công tắc 16, tay nắm 3).
     *
     * [glass] = bề mặt kính có bán kính gốc riêng (dock, widget, panel thư mục... của Home): bán kính thật = [dp] nhân theo thanh
     * "Bo góc" của kính (`GlassTuning.cornerRadius`), như vai kính của [Role].
     */
    data class Fixed(val dp: Float, val glass: Boolean = false) : DsCorner
}
