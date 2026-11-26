# PM — Xin quyền Vị trí / Danh bạ: widget thời tiết xin quyền tại chỗ, hộp "Cấp quyền" khi bị chặn — Implementation Plan

> Làm theo `docs/AGENTS.md`. Chủ dự án cho làm một mạch (2026-11-26 17:03), mỗi Task một commit, kiểm cả lô (TR + FX + PM) rồi mở PR. VM không có
> Android SDK: commit chỉ được rà bằng đọc code. Không có test tự động.

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (yêu cầu chi tiết trong chat)
**Trạng thái:** Đang làm

**Goal:** Bấm widget thời tiết khi chưa có quyền vị trí thì hiện hộp xin quyền của hệ thống (không mở bảng); cấp xong tự cập nhật vị trí. Quyền Vị trí
hoặc Danh bạ đã bị từ chối tới mức Android không hỏi nữa thì ở MỌI chỗ xin quyền hiện một hộp xác nhận riêng ("Đóng" / "Cấp quyền") nói rõ lý do,
"Cấp quyền" mở trang thông tin ứng dụng.
**Architecture:** Một lớp dùng chung `core/permission/PermissionRequester` (đăng ký `RequestPermission` lúc dựng, nhớ "đã từ chối rõ ràng" trong
SharedPreferences `permission_denials`, quyết định hiện hộp hệ thống hay hộp bị chặn) + `showPermissionBlockedDialog` (DsAlertDialog không đóng khi
chạm ngoài). Việc "lấy vị trí một lần rồi ghi làm vị trí tự động" tách khỏi `LocationSettingsViewModel` thành `UpdateAutoPlaceUseCase` để Home dùng chung.
**Spec:** chủ dự án 2026-11-26 17:03 (chat).

## Yêu cầu (chủ dự án)

| # | Yêu cầu |
|---|---|
| Y1 | Widget thời tiết, chưa cấp quyền: bấm vào → hộp xin quyền của hệ thống thay cho bảng chọn địa điểm |
| Y2 | Được cấp → tự cập nhật vị trí, KHÔNG mở bảng |
| Y3 | Bị từ chối quá nhiều lần → hộp xác nhận riêng 2 nút "Đóng" / "Cấp quyền" (mở màn chi tiết ứng dụng); áp cho cả Vị trí và Danh bạ, ở mọi chỗ xin hai quyền này |
| Y4 | Chạm ra ngoài hộp của hệ thống: hộp đóng, không làm gì thêm |
| Y5 | Chạm ra ngoài hộp riêng: không làm gì, không đóng |
| Y6 | Hộp riêng nói rõ lý do cần quyền |

## Ruling (Claude tự quyết)

| # | Ruling | Vì sao | Sai thì tốn |
|---|---|---|---|
| R1 | "Bị chặn" = đã từng từ chối rõ ràng (sau kết quả từ chối mà `shouldShowRequestPermissionRationale` trước HOẶC sau là true) VÀ giờ rationale = false. Cấp quyền thì xóa cờ | Android 11+ không có API "bị chặn"; chạm ra ngoài hộp hệ thống không đổi rationale nên không bị tính là từ chối (Y4) | Đổi điều kiện trong `PermissionRequester` |
| R2 | Widget chỉ xin quyền khi nó đang dùng "Vị trí hiện tại" VÀ chế độ vị trí là Tự động. Widget đặt thành phố cố định, hoặc chế độ Thủ công, vẫn mở bảng như cũ | Hai trường hợp đó không cần quyền | Bỏ điều kiện |
| R3 | Back trên hộp riêng = "Đóng" | Y5 chỉ nói chạm ngoài; Back không đóng được thì người dùng kẹt | `cancelable = false` |
| R4 | Từ chối ở hộp hệ thống: không Toast (trước: Cài đặt → Vị trí báo "Chưa cấp quyền…") | Y4 "không làm gì"; không tách được chạm ngoài và Từ chối trên mọi bản Android | Thêm Toast lại |
| R5 | Cờ lưu ở SharedPreferences `permission_denials` (đọc đồng bộ lúc bấm), ghi trong `config-schema.md` | Quyết định phải có ngay trong lần bấm, không chờ DataStore | Chuyển sang DataStore |

## Vùng file

**Tạo:** `core/permission/PermissionRequester.kt` (+ `PermissionKind`, `PermissionDenials`, `showPermissionBlockedDialog`, `openAppDetailsSettings`),
`domain/usecase/UpdateAutoPlaceUseCase.kt`.
**Sửa:** `core/designsystem/compose/DsDialogs.kt` (`DsAlertDialog(dismissOnClickOutside)`), `ui/home/HomeActivity.kt` (widget thời tiết, quyền Danh bạ
của Spotlight), `ui/settings/LocationSettingsScreen.kt`, `LocationSettingsViewModel.kt`, `SearchSettingsScreen.kt`, `strings.xml` × 9, `docs/config-schema.md`.

## Global Constraints

- Chuỗi mới đủ 9 ngôn ngữ. Không log tọa độ, tên nơi, dữ liệu danh bạ.
- `registerForActivityResult` gọi lúc dựng (trước STARTED): Home ở `onCreate`, màn Cài đặt lúc dựng màn (công thức G2d).
- Commit message tiếng Anh không dấu, có dòng attribution.

## Review Focus

1. Lần xin đầu tiên chạm ra ngoài hộp hệ thống → lần bấm sau vẫn hiện hộp hệ thống (không nhảy sang hộp bị chặn).
2. Từ chối lần 2 (Android 11+ chặn luôn) → ngay lúc đó KHÔNG hiện hộp riêng; lần bấm sau mới hiện.
3. Cấp quyền ở trang ứng dụng rồi quay lại → lần sau không còn hộp riêng (cờ xóa khi thấy đã có quyền).
4. Spotlight: hàng mời Danh bạ khi bị chặn → hộp riêng, không mở thẳng Cài đặt.
5. Xoay màn / Activity tạo lại khi hộp riêng đang mở: hộp đóng, không crash (như mọi `dsDialogs`).

---

### Task 1: Lõi xin quyền dùng chung + hộp bị chặn

- [x] Step 1: `DsAlertDialog` thêm `dismissOnClickOutside: Boolean = cancelable`.
- [x] Step 2: `PermissionKind { LOCATION, CONTACTS }` (quyền Android, tiêu đề, lời giải thích), `PermissionDenials` (SharedPreferences), `PermissionRequester(activity, kind)` với `isGranted()`, `isBlocked()`, `request(onResult: (granted: Boolean) -> Unit)`; `showPermissionBlockedDialog(activity, kind, onOpenSettings)`; `openAppDetailsSettings(activity): Boolean`.
- [x] Step 3: Chuỗi `perm_blocked_grant`, `perm_blocked_location_title/message`, `perm_blocked_contacts_title/message` × 9 ("Đóng" dùng `ds_close`); `config-schema.md`.
- [x] Step 4: Commit `feat: shared permission requester with blocked-permission dialog`.

### Task 2: Widget thời tiết xin quyền tại chỗ (Y1, Y2)

- [x] Step 1: `UpdateAutoPlaceUseCase` (lấy vị trí một lần, `reverse`, ghi `setAutoPlace`; trả `false` khi không lấy được vị trí); `LocationSettingsViewModel.updateAutoPlace` gọi nó.
- [x] Step 2: `HomeActivity`: widget thời tiết đang dùng "Vị trí hiện tại" + chế độ Tự động + chưa có quyền (R2) → `PermissionRequester.request`; được cấp → use case (Toast `wx_loc_unavailable` khi không lấy được); còn lại mở bảng như cũ.
- [x] Step 3: Commit `feat: weather widget asks for location permission in place`.

### Task 3: Mọi chỗ xin Vị trí / Danh bạ dùng chung (Y3–Y5)

- [ ] Step 1: `LocationSettingsScreen` (hàng quyền, "Cập nhật vị trí") dùng `PermissionRequester` (R4).
- [ ] Step 2: Danh bạ: `HomeActivity.requestContacts` (hàng mời của Spotlight) và `SearchSettingsScreen` (công tắc Danh bạ) dùng `PermissionRequester`; báo `onContactsPermissionResult(granted, permanentlyDenied = isBlocked())`.
- [ ] Step 3: Rà: grep `ACTION_APPLICATION_DETAILS_SETTINGS` / `RequestPermission()` cho Vị trí và Danh bạ chỉ còn trong `core/permission`. Commit `feat: blocked dialog for location and contacts everywhere`.

### Task 4: Docs

- [ ] `lessons/permissions-and-device-testing.md`, `BACKLOG.md`, `plans/README.md`, `project_structure.md`. Commit `docs: PM permission flow`.

## Kịch bản kiểm (chủ dự án)

Xem báo cáo cuối lô (TR + FX + PM) trong chat.

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-26 | 1 | `DsAlertDialog(dismissOnClickOutside)`; `core/permission/PermissionRequester.kt` (`PermissionKind`, `PermissionDenials`, `PermissionRequester`, `showPermissionBlockedDialog`, `openAppDetailsSettings`); 5 chuỗi × 9; `config-schema.md` mục 2 | XML parse được, chưa build |
| 2026-11-26 | 2 | `UpdateAutoPlaceUseCase` (tách từ `LocationSettingsViewModel`); `HomeActivity`: widget thời tiết xin quyền khi dùng Vị trí hiện tại + Tự động + chưa có quyền, cấp xong tự cập nhật; Danh bạ của Spotlight dùng `PermissionRequester` (làm sớm cùng commit vì chung chỗ khai báo trong HomeActivity) | Ruling: gộp phần Danh bạ của HomeActivity (Task 3 Step 2) vào commit này — cùng khối khai báo, tách ra thì commit giữa không biên dịch được. Chưa build |
