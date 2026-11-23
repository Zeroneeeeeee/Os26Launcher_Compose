# SW — Menu nhấn giữ icon bị cắt chữ khi cỡ chữ hệ thống lớn — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo điều phối, qua review mới tick; cả tính năng kiểm tay một lần ở cuối. Dự án **không viết test tự động**. **Agent thực thi không dùng máy thật** (không adb): cài APK, đo trên máy do điều phối chạy. Lệnh viết cho PowerShell.

**Nhánh:** `fix/sw-menu-width-fit` · **Worktree:** `E:\SourceTree\IosLauncher-wt\SW-menu-width-fit` · **PR:** draft (xem PR của nhánh) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09 (chủ dự án duyệt thiết kế)
**Trạng thái:** Chờ kiểm tay trên Live 4
**Khóa:** không khóa schema DB, không thêm cài đặt; chỉ sửa vùng menu icon (`ui/home`, `res/values/dimens.xml`)

**Goal:** Menu nhấn giữ icon hiện đủ chữ ("Thông tin ứng dụng", "Sửa màn hình chính", nhãn shortcut của app) khi cỡ chữ hệ thống lớn, mà không đổi gì ở cỡ chữ nhỏ/vừa.
**Architecture:** Bề rộng menu = `clamp(contentWidth, 200dp, 260dp)`, không vượt vùng an toàn ngang. `contentWidth` là bề rộng một dòng của hàng dài nhất (đo bằng chính `TextPaint` của hàng + padding + icon đầu hàng + khoảng cách icon-chữ) cộng padding ngang của bảng. Luật nằm trong hàm thuần `IconMenuWidth.fit`; `IconActionMenu.naturalContentWidth()` cung cấp `contentWidth`; `IconMenuController` dùng bề rộng đã chốt cho cả `measure`, `menuWidth` của `IconMenuPlacement` và layout params.
**Tech Stack:** Kotlin, View/XML. Không thêm dependency.
**Spec:** menu nhấn giữ icon (SM, [SM-shortcut-menu-glass.md](SM-shortcut-menu-glass.md)).

## Số đo đầu vào (điều phối)

- Oppo A3x (`font_scale` 0,9): chữ vừa trong bảng 200dp. Vsmart Live 4 (`font_scale` 1,15): "Thông tin ứng dụng", "Sửa màn hình chính" và nhãn shortcut bị cắt thành "...". (đo được)
- Nguyên nhân (đọc code): `IconMenuController` đo bảng với `MeasureSpec.EXACTLY` theo `R.dimen.shortcut_menu_width` = 200dp cố định, hàng `maxLines=1` + `ellipsize=end`.

## Vì sao chọn thiết kế này

**Vấn đề cần giải.** Bề rộng cố định 200dp lấy từ launcher mẫu trên Oppo (đo được, SM). Phần cố định mỗi hàng ≈ 60dp (padding bảng 8+8, padding hàng 8+8, icon 20, khoảng cách icon-chữ 8; đọc `dimens.xml`), nên chỗ cho chữ là 140dp. Chữ 15sp ở `font_scale` 1,15 thành ~17sp; nhãn tiếng Việt dài hơn 140dp. Phải để chữ đủ chỗ mà không phá dáng menu ở cỡ chữ thường.

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Nở theo chữ, kẹp 200 đến 260dp | Đo hàng dài nhất, bảng rộng `clamp(content, 200, 260)` | Cỡ chữ thường: đúng 200dp như mẫu, không đổi gì. Cỡ chữ lớn: chỗ cho chữ tăng từ 140dp lên tới 200dp (~+43%). Tôn trọng cỡ chữ người dùng chọn | Thêm một lần đo chữ (rẻ: vài `measureText` lúc mở menu). Hàng quá dài vẫn "..." | Có |
| B. Cho nhãn 2 dòng ở 200dp cố định | Bỏ `maxLines=1`, hàng cao tự do | Không đổi bề rộng | Hàng cao không đều, menu cao hơn, lệch hẳn dáng menu mẫu (hàng 39dp đồng nhất); phải tính lại chiều cao trước khi đặt | Không |
| C. Co chữ theo `font_scale` hoặc bỏ qua `font_scale` | Đặt cỡ chữ bằng dp thay sp, hoặc nhân ngược scale | Giữ 200dp, không cần đo | Phá khả năng truy cập: người cần chữ lớn bị chính menu của launcher bỏ qua cài đặt hệ thống | Không |

**Vì sao chọn A.** (1) Không đổi hình ảnh ở cỡ chữ nhỏ/vừa, nơi dáng mẫu đã đo đúng (đo được trên Oppo). (2) Là cách duy nhất vừa giữ hàng một dòng đồng nhất vừa theo cỡ chữ hệ thống. (3) Luật là hàm thuần px vào/ra, dễ đọc và kiểm bằng mắt, cùng kiểu `IconMenuPlacement`.
**Ước tính (suy luận chưa kiểm chứng):** phần cố định ≈ 60dp nên 200dp cho 140dp chữ, 260dp cho 200dp chữ. Sẽ đối chiếu với số đo thật trên Live 4.
**Đánh đổi chấp nhận:** menu rộng tới 260dp trên máy cỡ chữ lớn (lệch dáng mẫu có chủ đích); nhãn rất dài (hơn 200dp chữ) vẫn "...". Xét lại nếu nhãn shortcut của app thật vẫn bị cắt nhiều: nâng trần hoặc cho 2 dòng riêng ca đó.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Rộng = `clamp(content, 200dp, 260dp)` rồi `min(safeWidth)` | Chủ dự án duyệt 2026-11-09 |
| D2 | `safeWidth` = rộng root − inset trái − inset phải − 2×`shortcut_menu_margin` | Cùng vùng an toàn mà `IconMenuPlacement` đang dùng |
| D3 | Bỏ dimen `shortcut_menu_width`, thay bằng `shortcut_menu_min_width` 200dp và `shortcut_menu_max_width` 260dp | Không còn chỗ nào dùng tên cũ (đã grep) |
| D4 | Bind lại theo chiều đảo giữ nguyên bề rộng | Cùng tập hàng, cùng chữ |

## Ngoài phạm vi / ghi để sau
- Cho nhãn xuống 2 dòng khi vượt trần 260dp.
- Cỡ "Display size" (đổi `densityDpi`) không phải `font_scale`: không xét riêng, luật tính theo px nên tự thích nghi.

## Vùng file
| File | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/controller/IconMenuWidth.kt` | Tạo | Không (mới) |
| `ui/home/view/IconActionMenu.kt` | Sửa (thêm `naturalContentWidth`) | Độc quyền menu |
| `ui/home/controller/IconMenuController.kt` | Sửa (khối đo/đặt trong `render`) | Độc quyền menu |
| `res/values/dimens.xml` | Sửa (dimen menu) | Dùng chung, sửa tối thiểu |

## Global Constraints
- Không `android.*` trong `IconMenuWidth`; mọi số px.
- Không đổi `IconMenuPlacement`, `IconMenuMotion`.
- Không test tự động.

## Review Focus
1. Cỡ chữ nhỏ/vừa: bảng vẫn đúng 200dp (không lệch so với trước).
2. `IconMenuMotion` đọc `panelWidth` = `rows.measuredWidth` sau `measure` với bề rộng đã fit: điểm xoay đúng ở 260dp.
3. Ca sát mép màn hình khi 260dp không vừa: `safeWidth` kẹp bề rộng, `IconMenuPlacement` kẹp vị trí.
4. Bind đảo (menu nằm trên icon) không làm đổi bề rộng.

---

### Task 1: Luật bề rộng + nối vào controller
**Bắt buộc kiểm tay:** có — cần số đo trên máy thật (mục 4.2 của MultiAgent-Workflow.md)
**Files:** xem Vùng file.
**Interfaces:** Produces `IconMenuWidth.fit(contentWidth, minWidth, maxWidth, safeWidth): Int`, `IconActionMenu.naturalContentWidth(): Int`.
- [x] **Step 1:** Thêm dimen min/max, bỏ `shortcut_menu_width`.
- [x] **Step 2:** `IconMenuWidth.fit`.
- [x] **Step 3:** `IconActionMenu.naturalContentWidth()`.
- [x] **Step 4:** `IconMenuController.render` dùng bề rộng fit cho measure, `menuWidth`, layout params.
- [x] **Step 5:** `assembleDebug` và `lintDebug` xanh.
- [x] **Step 6: Kịch bản kiểm (Live 4, `font_scale` 1,15)** — (a) giữ icon có shortcut: không còn "..." ở nhãn, đo bề rộng bảng; (b) icon không có shortcut: "Thông tin ứng dụng", "Sửa màn hình chính" đủ chữ; (c) đổi `font_scale` về 0,85 và 1,0: bảng đúng 200dp; (d) icon sát mép phải và mép trái: bảng không tràn mép; (e) menu mọc lên trên icon (đảo hàng): cùng bề rộng; (f) hoạt ảnh mở/đóng xoay quanh đúng góc gần icon.

---

## Bài kiểm cả tính năng
Máy: Live 4 (chính), Oppo A3x để xác nhận không đổi ở 200dp. **Kết quả kiểm chứng (điều phối đo 2026-11-09, APK debug `2c5b022`, uiautomator + ảnh chụp):**
- Live 4 (`font_scale` 1,15, 3 px/dp): bảng **213,3 dp** ở cả 4 lần (Dragon Boy cột trái, Drive cột phải, Chess 2D, Chrome ở dock); đủ chữ "Thông tin ứng dụng", "Sửa màn hình chính", không còn "...". Đạt (b), (d), (e).
- Oppo A3x (`font_scale` 0,9, 2 px/dp): bảng **200,0 dp** ở cả 3 lần (Battery Animation, Call Screen, Chrome ở dock), vị trí trùng bản SM (`[220,637][620,973]` cho ô cột 3). Đạt (c) ở 0,9; không đổi `font_scale` của máy chủ dự án nên 0,85 và 1,0 không đo.
- (a) chưa đo: cả hai máy không đặt Os26Launcher làm launcher mặc định nên menu không có shortcut app. (f) chỉ thấy trạng thái cuối (bảng thẳng mép icon); hoạt ảnh để chủ dự án xem ở bài kiểm cuối.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-09 | 1 | Viết plan, code, build | xem lịch sử nhánh | |
| 2026-11-09 | 1 | Điều phối đo Live 4 + Oppo | | Đạt; dữ liệu app so hash không đổi (chỉ cache nhãn) |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Cỡ chữ nhỏ/vừa vẫn 200dp | Oppo 0,9: 200,0 dp ×3 | Đạt |
| 2 | Điểm xoay đúng | Đọc code (`panelWidth` = bề rộng đã đo); trạng thái cuối thẳng mép icon | Chờ chủ dự án xem hoạt ảnh |
| 3 | Sát mép màn hình | Live 4 cột trái `[72..712]`, cột phải `[368..1008]` px, trong màn 1080 | Đạt |
| 4 | Bind đảo giữ bề rộng | Chrome ở dock (menu trên icon, hàng đảo): Live 4 213,3 dp, Oppo 200,0 dp | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: menu nhấn giữ icon rộng 200 đến 260dp theo cỡ chữ.
- `project_structure.md`: thêm `ui/home/controller/IconMenuWidth.kt`.
- `room-schema.md`: không.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement.
