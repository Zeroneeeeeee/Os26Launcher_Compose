# JH4 — Home Compose thành màn hình chính thật — đọc trước khi review

**Plan:** [plans/JH4-compose-home-switchover.md](../plans/JH4-compose-home-switchover.md) (tiếng Anh, ngoại lệ ở `AGENTS.md`)

## 1. Tóm tắt

Bước 3g của hướng B. Từ JH4, `.ui.home.HomeActivity` (thành phần hệ thống lưu làm launcher mặc định) là **Home Compose**. Home View cũ đổi tên thành `ViewHomeActivity`, vẫn được biên dịch nhưng chỉ mở được qua công tắc debug; JX gỡ hẳn.

Quyết định của chủ dự án (2026-11-20): Compose mặc định, giữ Home View dự phòng; chuyển trước, tối ưu lướt trang trên Samsung sau.

Những gì Home View làm mà Home Compose chưa có được chuyển sang trong JH4: mở Onboarding lần đầu (khung tối chờ, dọn task cũ), dựng lại khi đổi kiểu icon / giao diện, công tắc ẩn thanh điều hướng, báo kính View vẽ lại khi đổi ảnh nền, chỉ nghe widget khi máy có tính năng widget, đóng menu khi đổi cỡ cửa sổ.

Kiểm trên Samsung (chủ dự án chọn chỉ Samsung): 4/5 điểm đạt; "giữ launcher mặc định sau khi cập nhật" chưa chạy vì app không là launcher mặc định trên máy đó.

## 2. Kiến thức nền

- **Tên thành phần HOME là dữ liệu của hệ thống:** máy lưu launcher mặc định theo tên lớp Activity có bộ lọc HOME. Đổi tên lớp đó thì sau khi cập nhật máy coi như launcher mặc định đã bị gỡ và hỏi lại. Vì vậy giữ tên `HomeActivity`, thay nội dung bên trong; bản cũ mang tên mới.
- **`finish()` ngay trong `onCreate`:** các callback vòng đời vẫn chạy (`onStart`… `onDestroy`). Activity có nhiều `lateinit` phải có cờ (`redirected`) để bỏ qua chúng, nếu không sẽ `UninitializedPropertyAccessException`.
- **Công tắc chỉ ở bản debug mà không cần `BuildConfig`:** đọc `ApplicationInfo.FLAG_DEBUGGABLE`; bản release ký thật không debuggable nên nhánh dự phòng không bao giờ chạy.

## 3. Cách chuyển về Home View để so sánh (bản debug)

```powershell
adb shell am broadcast -a gambi.global.os26launcher.JH4_HOME_MODE --ez view true -n gambi.global.os26launcher/gambi.global.os26launcher.debug.HomeModeDebugReceiver
```

`view false` để quay lại Home Compose. Áp từ lần mở Home kế tiếp.

## 4. Số đo

Bản release 8 334 838 B (JD: 8 031 918 B; JH1–JH4 thêm 303 KB). Lướt trang: như JH2 (Samsung p90 31–32 ms so với Home View 25–28 ms; Oppo Compose nhanh hơn) — để plan tối ưu sau.

## 5. Việc còn mở

- Kiểm "giữ launcher mặc định sau khi cập nhật" trên máy đặt app làm launcher mặc định.
- Kéo thẻ widget từ bảng chọn ra lưới; tối ưu lướt trang trên Samsung (plan sau JH4).
- Gặp một lần: Home mở thẳng vào Thư viện sau khi bỏ qua Onboarding; sau khi dựng lại do đổi theme, pager đứng ở trang khác.
