# JS + JX — Toàn bộ UI sang Compose, gỡ lớp View (2026-11-20)

Plan: [plans/JS-compose-remaining-screens.md](../plans/JS-compose-remaining-screens.md), [plans/JX-compose-cleanup.md](../plans/JX-compose-cleanup.md). Máy kiểm: Samsung SM-A176B (Android 16), dữ liệu app sao lưu trước và khôi phục sau mỗi lần thử.

## Kết quả

- Một ngăn xếp UI: mọi màn (Home, Thư viện, Tìm kiếm, Cài đặt, Onboarding, ảnh widget, bảng chọn widget, Trung tâm điều khiển và các màn cài đặt CC) vẽ bằng Compose. Không còn `res/layout`, ViewBinding, Material, RecyclerView, ViewPager2, ConstraintLayout, thư viện kính QWEA0, JitPack.
- JX xoá 146 file (Home View, màn View cũ, DS View, kính QWEA0, layout, debug cũ) + 24 file resource + 250 mục value (gồm bản dịch) mà lint báo không dùng. `UnusedResources` về 0 sau một vòng.
- APK debug: 26,672,871 B → 20,786,071 B (−22 %). Release: xem Log của plan JX.
- Theme: `Theme.Material3.*` → `Theme.AppCompat.*` (AppCompat ở lại vì ngôn ngữ theo app dùng `AppCompatDelegate`). Splash, thanh hệ thống, hộp thoại kiểm trên máy: như cũ.
- `GlassEnvironment` dò mức API bằng `Build.VERSION.SDK_INT` (trước đây hỏi `LiquidGlassView.effectiveApiLevel`). Cài đặt "Mức kính" và công tắc kính từng thành phần còn giữ, chưa nối vào Compose (JX Q3, plan riêng sau).

## Điều học được

- **Chứng minh danh sách xoá bằng build thật trong worktree bỏ đi** (đo, JX): quét tĩnh tìm ứng viên, rồi xoá trong `git worktree` tạm, gỡ dependency, đổi theme, chạy `assembleDebug lintDebug testDebugUnitTest`. Danh sách đưa chủ dự án duyệt là danh sách đã build xanh, kèm số byte APK. Bản checkout thật không bị đụng tới khi chưa duyệt.
- **Đổi theme cha từ Material3 sang AppCompat phải gỡ thuộc tính chỉ Material có** (đo, JX): `colorOnPrimary`, `colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `bottomSheetStyle`, style cha `ThemeOverlay.Material3.*`, `TextAppearance.Material3.*`. Thiếu bước này thì AAPT báo lỗi link resource.
- **Lint `LocalContextGetResourceValueCall` và `NonObservableLocale`** (đo, JS): trong composable dùng `stringResource(...)`, không `LocalContext.current.getString(...)`; locale dùng `androidx.compose.ui.text.intl.Locale.current.platformLocale`, không `Locale.getDefault()`.
- **`scrollToItem` làm `isScrollInProgress` = true** (đo trên máy, JS4): ẩn bàn phím theo `isScrollInProgress` thì ô tìm kiếm Thư viện vừa mở đã đóng bàn phím. Chỉ ẩn khi có `DragInteraction.Start` của người dùng.
- **Nền mờ từ ảnh thu nhỏ bị vỡ điểm** (quan sát, JS): thu ảnh nền về 96 px phải vẽ lại với `FilterQuality.High`, thêm `Modifier.blur(24.dp)` trên API ≥ 31 (`wallpaperSoftBlur()`).
- **Cửa sổ dialog có thanh trạng thái riêng** (quan sát, JS3): bảng chọn widget ở edit mode bị thanh trạng thái của cửa sổ dialog che thanh công cụ; phải ẩn thanh trạng thái trên chính cửa sổ dialog.
- **ComposeView trong cửa sổ overlay của Service** (đo, JS7b): không có Activity nên phải tự cấp `Lifecycle`/`SavedState`/`ViewModelStore` owner và `Recomposer` riêng; frame clock tạm dừng khi panel đóng để không vẽ nền.
- **Nền CC quá trong** (chủ dự án, JS7b): blur sau panel 48 dp (Compose) và `cc_blur_behind_radius` 56 dp; chủ dự án chốt "Vừa rồi".

## Bẫy khi kiểm trên máy

- **Git Bash đổi đường dẫn `/data/...` trong tham số `adb`** (quan sát): `adb push x /data/local/tmp/f` có thể thành `D:/Git/data/...`. Đặt `MSYS_NO_PATHCONV=1` trước lệnh `adb` có đường dẫn trên máy.
- **`adb shell input swipe` từ mép trên không mở được CC panel trên Samsung** (quan sát): hệ thống bắt cú vuốt cho thanh thông báo. Nhờ người mở tay.
- **Không bấm HOME/BACK ở gốc Home khi app chưa là launcher mặc định**: rơi về launcher Samsung của chủ máy.
- **Onboarding chỉ hiện khi cờ `first_run_done` trong DataStore = false**: muốn xem lại phải xoá dữ liệu (`pm clear` có thể thu hồi quyền overlay/notification listener); JX không thử lại vì không sửa code Onboarding.

## Còn lại

- Comment lịch sử trong code còn nhắc tên lớp View đã xoá (khoảng 250 chỗ, kiểu "thay `DsButton`"): giữ làm ghi chú nguồn gốc.
- Chưa kiểm sau JX: Onboarding, màn ảnh widget, hộp thoại đổi tên / đánh giá / chọn thành phố, số đo trên Oppo.
- JX Q3: nối (hoặc gỡ) cài đặt "Mức kính" cho Compose — plan riêng.
