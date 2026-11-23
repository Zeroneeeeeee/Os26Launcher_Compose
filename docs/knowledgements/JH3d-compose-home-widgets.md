# JH3d — Home Compose: bảng chọn widget, thêm widget app, bấm widget — đọc trước khi review

**Plan:** [plans/JH3d-compose-home-widgets.md](../plans/JH3d-compose-home-widgets.md) (tiếng Anh, ngoại lệ ở `AGENTS.md`)

## 1. Tóm tắt

Bước 3f của hướng B, phần cuối của JH3. Home Compose (`debug.ComposeHomeActivity`) có thêm:

- Nút **"Tiện ích"** trong edit mode mở bảng chọn widget (dùng lại bảng của Home View). Bấm thẻ → widget được thêm vào trang đầu tiên còn chỗ, Home lật tới trang đó.
- **Luồng thêm widget app** (`AppWidgetFlowController`: cấp id, xin quyền gắn, màn cấu hình, ghi hàng) nối như `HomeActivity`.
- **Bấm widget** ngoài edit mode: thời tiết → bảng Thời tiết; ảnh → màn Ảnh; đồng hồ/lịch → app của máy.

Thay đổi duy nhất ngoài Home Compose: `WidgetPickerController` nhận `WidgetPickerHost` thay cho `ActivityHomeBinding` (4 chỗ dùng: nút, trang đang xem, cỡ pager, context). Home View đổi theo nhưng hành vi giữ nguyên (kiểm trên máy).

Chưa làm: kéo thẻ từ bảng chọn ra lưới trong Home Compose (JH4).

## 2. Kiến thức nền

- **Bảng chọn là cửa sổ riêng** (`BottomSheetDialog`): khi kéo thẻ, Home View chuyển tiếp chạm của cửa sổ đó sang `Activity.dispatchTouchEvent` rồi vào `DragHelper`. Home Compose kéo bằng `HomeDragState` qua cử chỉ gốc, không nhận một chuỗi chạm "đã bắt đầu" từ cửa sổ khác; nối hai bên cần một cầu riêng, nên tạm tắt kéo thẻ (`supportsCardDrag = false`).
- **Interface thay binding:** lớp View cũ chỉ cần vài giá trị từ màn hình thì tách chúng ra interface nhỏ là cách rẻ nhất để hai màn dùng chung, không phải viết lại.

## 3. Lưu ý khi kiểm trên máy

Tiến trình app chạy lại ngay sau `am force-stop` vì dịch vụ đọc thông báo (`ControlCenterService`) nằm trong cùng tiến trình. Lần khôi phục đầu so không khớp; giải nén lại rồi `force-stop` thêm một lần thì khớp từng byte và ổn định. Xem bài học ở `lessons/settings-and-data.md`.

## 4. Việc còn mở

- Kéo thẻ widget từ bảng chọn ra lưới trong Home Compose: JH4.
- Chưa chạy: thêm widget app có màn cấu hình; thoát edit mode khi bảng chọn đang mở.
- Mô tả trợ năng của pill có lúc đọc trang cũ ngay sau khi tự lật trang (chấm vẫn đúng): kiểm ở JH4.
