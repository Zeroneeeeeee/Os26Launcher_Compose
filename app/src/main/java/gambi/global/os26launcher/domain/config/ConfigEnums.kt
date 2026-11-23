package gambi.global.os26launcher.domain.config

/**
 * CF — enum của các trường config mới (`docs/config-schema.md`). Lưu bằng `name`; đọc bằng [enumFromName]: tên lạ hoặc null rơi về mặc
 * định, không crash Flow (cùng nguyên tắc `IconStyle.fromName`). Thêm giá trị mới thì thêm vào CUỐI để dữ liệu cũ vẫn đọc được.
 */
inline fun <reified E : Enum<E>> enumFromName(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/** Bốn kiểu giao diện cài sẵn (màn 1.1, onboarding 0.2). Giá trị ở `ThemePresets`. */
enum class ThemeId { LIQUID_GLASS_27, CLASSIC_26, MINIMAL, DARK_PRO }

/** Nguồn nền Home. AUTO = hành vi trước CF (ảnh đã chọn nếu có, không thì ảnh mặc định). PRESET dành cho thư viện hình nền (G3f), chưa có. */
enum class WallpaperKind { AUTO, PHOTO, COLOR, PRESET }

/** Hình dạng icon (màn 2.3). Chưa có tính năng: icon vẫn là squircle cố định (`IconShape`). */
enum class IconShapeKind { SQUIRCLE, CIRCLE, SQUARE, TEARDROP }

/** Tông bề mặt kính của theme (Dark Pro = DARK). Chưa có tính năng. */
enum class SurfaceTone { LIGHT, DARK }

/** Hiệu ứng lướt trang (màn 3.1). Chưa có tính năng. */
enum class PageTransition { DEFAULT, CUBE, STACK, FLIP, ZOOM, ROTATE }

/** Kiểu hiển thị Thư viện ứng dụng (màn 3.2). Chưa có tính năng. */
enum class LibraryStyle { FOLDERS, ALPHABETICAL }

/** Nơi đặt app mới cài (màn 3.2). HOME = hành vi hiện tại. */
enum class NewAppPlacement { HOME, LIBRARY_ONLY }

/** Kiểu huy hiệu thông báo (màn 3.4). Chưa có tính năng. */
enum class BadgeStyle { NUMBER, DOT }

/** Hiệu ứng chuyển cảnh thư mục/tìm kiếm/thư viện/Cài đặt nhanh (màn 4.1). Áp qua `TransitionMotion`. */
enum class TransitionStyle { SMOOTH, FADE, SLIDE, ZOOM, NONE }

/** Tốc độ chuyển cảnh (màn 4.1). */
enum class MotionSpeed { SLOW, MEDIUM, FAST }

/** Hiệu ứng mở ứng dụng (màn 4.2). Chưa có tính năng. */
enum class AppOpenEffect { ZOOM_FROM_ICON, FADE, SLIDE_UP, BOUNCE }
