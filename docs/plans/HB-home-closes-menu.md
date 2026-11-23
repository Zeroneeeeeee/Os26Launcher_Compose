# HB — Nút Home đóng menu nhấn giữ icon

**Nhánh:** `fix/hb-home-closes-menu` · **Bắt đầu:** 2026-11-10 · **Trạng thái:** Xong, chờ merge

## Lỗi
Chủ dự án báo (2026-11-10, Os26Launcher là launcher mặc định): mở thư mục → nhấn giữ một icon → menu hiện → bấm Home trên thanh điều hướng hệ thống → panel thư mục đóng nhưng **menu nhấn giữ và icon nổi vẫn treo trên Home**, kèm lớp tối, tới khi chạm vào màn hình.

Tái hiện trên Oppo A3x (đo được): sau Home 0,4 s và 2 s menu "Thông tin ứng dụng / Ẩn ứng dụng / ..." và icon nổi vẫn còn trên Home, Home bị phủ tối.

## Nguyên nhân (đọc code)
`HomeActivity.onNewIntent` (Home khi đang ở Home, `singleTask`) đóng tìm kiếm, App Library và thư mục nhưng **không đóng menu nhấn giữ** (`HomeUiState.iconMenu`). Back thì có (`HomeBackController.closeIconMenuOnBack`).

## Vì sao chọn thiết kế này
| Cách | Ưu | Nhược |
|---|---|---|
| **Gọi `viewModel.closeIconMenu()` trong `onNewIntent`** (chọn) | Một dòng, cùng đường với Back; trạng thái menu là của ViewModel nên `IconMenuController.render(null)` dọn icon nổi và lớp tối | — |
| Đóng menu ở `onPause`/`onStop` | Bắt cả trường hợp mở app khác | Đóng cả khi chỉ kéo thanh thông báo xuống; khác iOS |

## Task
- [x] Sửa `HomeActivity.onNewIntent`; `assembleDebug lintDebug` xanh.
- [x] Kiểm Oppo A3x (bắt buộc kiểm tay: lỗi hiển thị chỉ thấy trên máy): menu mở từ thư mục và từ lưới Home, bấm Home → menu đóng, không còn lớp tối; không crash.

## Nhật ký
| Ngày | Việc | Ghi chú |
|---|---|---|
| 2026-11-10 | Tái hiện, sửa, kiểm Oppo | Điều phối làm trực tiếp (sửa một dòng) |
