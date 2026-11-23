package gambi.global.os26launcher.domain.model

/**
 * Mức kính. AUTO là giá trị mặc định. FORCE_LOW/FORCE_HIGH ép tier (bỏ qua tier tự nhận ra từ effectiveApiLevel).
 * GS: CUSTOM (thêm cuối, giá trị cũ vẫn đọc được) = từng phần tử chọn kính thật/mờ bằng [GlassCustom]; chỉ ở mức này công tắc mới có nghĩa.
 */
enum class GlassLevelOverride { AUTO, FORCE_LOW, FORCE_HIGH, CUSTOM }
