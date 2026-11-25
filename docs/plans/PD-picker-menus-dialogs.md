# PD — Bảng chọn widget (cao tối đa, giữ là kéo ngay), chữ trắng cho menu tối, thẻ xác nhận dùng chung cho Home / Thư viện / Today — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code liền các Task, chủ dự án build và kiểm một lượt; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án: "ghi vào plan và làm luôn", trả lời Q1–Q3)
**Trạng thái:** Đóng 2026-11-25 theo yêu cầu chủ dự án (Task 1–5); kiểm tay mới một phần (xem "Kết quả kiểm chứng")
**Nhánh:** `an` (chồng lên WF chưa commit) · **Máy:** Oppo PKD130, Vsmart Live 4 · **Đổi schema Room:** không

**Goal:** (1) Bảng chọn widget: trang danh sách cao tối đa 85% màn, trang chi tiết tối đa 80%. (2) Nhấn giữ một widget trong bảng (trang danh sách hoặc khung xem trước ở trang chi tiết) đủ lâu, chưa kéo: bảng trượt xuống đóng ngay, widget nổi dưới ngón tay và vào phiên kéo luôn. (3) Menu nhấn giữ icon và dropdown nút "Sửa" (đã có lớp tint tối) dùng chữ / icon trắng, "Gỡ cài đặt" giữ đỏ. (4) Mọi thẻ xác nhận của lưới chính, Thư viện, Today dùng chung kiểu thẻ kính tối chữ trắng của commit `b2672cc` "color dialog"; thẻ luôn ở lớp trên cùng, chặn mọi tương tác phía dưới.
**Spec:** yêu cầu chủ dự án 2026-11-25; commit `b2672cc` (Nam, RA2–RA4: `RemoveAppOverlay`, thẻ kính thật trong cây Compose của `HomeScreen`).

## Hiện trạng (đọc code 2026-11-25)

- **Chiều cao bảng chọn** (`HomeWidgetPicker.Gallery`): phần nội dung đặt cứng `0.85 × cao cửa sổ`, rồi `DsBottomSheetLayer` cộng thêm tay nắm (10 + 5 dp), lề dưới 32 dp và inset thanh điều hướng → cả bảng cao hơn 85%. Trang chi tiết cao theo nội dung (pager 360 dp + đầu trang + chấm + nút ≈ 560 dp), máy màn thấp có thể quá 80%.
- **Giữ để kéo** (`pickerCardDrag`, PG2): giữ đủ `longPressTimeout` chỉ rung và "arm"; phải di quá slop mới `startNewWidget`, bảng mờ (alpha) về 0 chứ không trượt xuống.
- **Màu menu:** `b2672cc` đổi `shortcut_menu_glass_tint` (bản sáng) từ `#40F5F5F7` sang `#595B5B5B` (xám đậm 35%) nhưng `shortcut_menu_text` bản sáng vẫn `#1C1C1E` (đen), `shortcut_menu_divider` vẫn đen 12%. Dùng ở `HomeIconMenu` (menu nhấn giữ icon) và `HomeEditMenu` (`HomeEditMode.kt`, dropdown nút "Sửa"), cùng tint các drawable `ic_shortcut_menu_*`. Bản tối (`values-night`) đã trắng.
- **Thẻ xác nhận:** `showRemoveAppDialog` và `showRemoveWidgetDialog(inHome = true)` vẽ bằng `RemoveAppOverlay` (kính tối, chữ trắng) TRONG `HomeScreen`. `showUninstallAppDialog` (Thư viện) và `showRemoveWidgetDialog` của Today vẫn mở `Dialog` cửa sổ riêng kiểu cũ (`RemoveCard`, kính cửa sổ hệ thống, chữ `shortcut_menu_text` đen sau `b2672cc`) → lệch kiểu (Today "lỗi UI").
- **Lỗi thẻ không chặn tương tác (đọc code):** `RemoveAppOverlay` nằm TRONG `Box` gốc của `HomeScreen`, mà `Box` đó gắn `Modifier.homeGestures(...)` (nhấn giữ vào / chạm thoát edit mode, kéo icon) xét ở pass **Initial** với `requireUnconsumed = false` → cha thấy MỌI chạm trước cả lớp phủ, kể cả khi thẻ đang hiện. Thêm nữa, `HomeActivity.dispatchTouchEvent` đưa mọi chạm vào bộ vuốt Thư viện / Today / kéo app từ Thư viện trước Compose. Nên chạm ra ngoài thẻ vừa đóng thẻ vừa thoát edit mode / nhấn giữ icon phía dưới.

## Vì sao chọn thiết kế này

- **85% / 80%:** thêm tham số `maxHeightFraction` cho `DsBottomSheetLayer` (cả bảng tính cả tay nắm, lề, inset); bảng danh sách cho phần nội dung `weight(1f)` nên cao đúng 85%; trang chi tiết để pager `weight(1f, fill = false)` + tối đa 360 dp nên chỉ co khi không đủ chỗ. Bảng khác dùng `DsBottomSheetLayer` không đổi (mặc định null).
- **Giữ là kéo:** sửa ngay trong `pickerCardDrag` (một chỗ cho cả ô danh sách và khung chi tiết): arm xong `startNewWidget` tại vị trí ngón; bảng trượt xuống bằng tham số `retracted` của `DsBottomSheetLayer` (đẩy `offset` về cao bảng, lớp tối nhạt theo `offset` như lúc đóng). Bảng vẫn trong composition nên cử chỉ của ô không bị huỷ (như PG2). Nhả tay chưa kéo = thả tại chỗ (Q1).
- **Thẻ xác nhận:** chuyển `RemoveAppOverlay` lên `HomeActivity`, là lớp trên cùng (trên Home, Today, Thư viện, ảnh kéo, bảng chọn): anh em phía dưới không nhận chạm (hit test của Compose dừng ở anh em trên cùng bị chạm), và không còn nằm dưới `homeGestures`. `dispatchTouchEvent` bỏ qua bộ vuốt khi thẻ mở (như WF với bảng chọn). Kính lấy mẫu lớp Home + Today + Thư viện (dùng lại `searchBackdrop`, đang ghi đúng lớp đó cho ô tìm kiếm), thẻ tự vẽ lớp tối 15% (như RA4: chỉ tối, không nhoè) nên giống nhau ở cả ba màn. Thư viện / Today gọi cùng đường → bỏ `Dialog` kiểu cũ (`showCard`, `RemoveCard`).

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Giữ đủ lâu rồi nhả ngay (chưa kéo) | Thả tại chỗ như iOS | Thả tại chỗ |
| Q2 | Đường kẻ phân cách trong menu (đen 12%, mất trên nền tối) | Đổi trắng 20% như `values-night` | Đổi trắng 20% |
| Q3 | Giới hạn chiều cao | Cả hai bảng 85% | Danh sách **85%**, trang chi tiết **80%** |

## Ngoài phạm vi / ghi để sau

- Hộp thoại ngoài Home (Cài đặt, Thời tiết, `dsDialogs` khác) giữ nguyên.
- Bảng thư mục, menu nhấn giữ icon cũng nằm dưới `homeGestures` (cùng cơ chế) nhưng chủ dự án chưa báo lỗi; không đổi.
- **Thẻ xác nhận trên máy dưới Android 12** (Vsmart Live 4, ảnh chủ dự án 2026-11-25): `dsGlass` chỉ vẽ kính thật từ API 31; thẻ không truyền nguồn kính mờ tĩnh (PG3) nên dưới 31 chỉ còn nền tối trong suốt, icon phía sau rõ nét, chữ khó đọc. Có từ RA2 (`b2672cc`), không do PD. Hướng sửa (chờ chủ dự án): truyền `frosted.backdropOrNull` của `HomeActivity` vào `dsGlass(frosted = …)` của thẻ (nền = ảnh nền đã mờ, như dock trên máy cũ).

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `core/designsystem/compose/DsDialogs.kt` (`DsBottomSheetLayer`: `maxHeightFraction`, `retracted`) | Sửa | 1, 2 |
| `ui/home/compose/HomeWidgetPicker.kt` (chiều cao, `pickerCardDrag` arm là kéo, bỏ mờ alpha) | Sửa | 1, 2 |
| `res/values/colors.xml` (`shortcut_menu_text` trắng, `shortcut_menu_divider` trắng 20%) | Sửa | 3 |
| `ui/home/compose/RemoveAppDialog.kt` (một đường thẻ trong cây; lớp tối riêng; Back; bỏ `Dialog`) | Sửa | 4 |
| `ui/home/compose/HomeScreen.kt` (bỏ `RemoveAppOverlay` và phần tối / ghi lớp theo `removeOverlayProgress`) | Sửa | 4 |
| `ui/home/HomeActivity.kt` (vẽ thẻ trên cùng, ghi lớp kính khi thẻ mở; `dispatchTouchEvent` bỏ qua khi thẻ mở) | Sửa | 4 |
| `docs/lessons/*` | Sửa | 5 |

## Global Constraints

- Không chuỗi mới; giữ CRLF; không viết `/*` trong comment Kotlin.
- Thẻ: chạm ngoài thẻ chỉ đóng thẻ; chạm trong thẻ mà không trúng nút thì không làm gì; Back đóng thẻ trước mọi lớp khác.

---

### Task 1: Chiều cao bảng chọn
**Bắt buộc kiểm tay:** có.
- [x] Step 1: `DsBottomSheetLayer(maxHeightFraction)`: `heightIn(max = fraction × cao cửa sổ)` cho cả khối bảng.
- [x] Step 2: Trang danh sách `0.85`, nội dung `weight(1f)` (bỏ chiều cao cứng); trang chi tiết `0.80`, pager `weight(1f, fill = false).heightIn(max = 360 dp)`.

### Task 2: Giữ đủ lâu là kéo ngay
**Bắt buộc kiểm tay:** có.
- [x] Step 1: `pickerCardDrag`: arm (rung) → `startNewWidget` tại vị trí ngón (gốc cửa sổ), `dragging = true`; di → `move`; nhấc → `drop` (Q1); huỷ → `cancel`; xong đóng cả hai bảng.
- [x] Step 2: `DsBottomSheetLayer(retracted)`: `retracted = true` thì bảng trượt xuống (`offset` → cao bảng, lớp tối nhạt dần); bảng chọn truyền `dragging`, bỏ mờ alpha cũ.
- [x] Step 3: Kịch bản kiểm: giữ ô ở trang danh sách / khung xem trước ở trang chi tiết → bảng trượt xuống, widget dưới ngón; nhả ngay → widget đặt ở ô dưới ngón; kéo sang ô khác / mép trang; bấm nhanh vẫn mở chi tiết; vuốt dọc danh sách vẫn cuộn; lướt pager chi tiết vẫn đổi cỡ.

### Task 3: Chữ trắng cho menu tối
**Bắt buộc kiểm tay:** có (chế độ sáng và tối của máy).
- [x] Step 1: `values/colors.xml`: `shortcut_menu_text` `#FFFFFFFF`, `shortcut_menu_divider` `#33FFFFFF` (như `values-night`); `shortcut_menu_destructive` giữ đỏ. Áp cho menu nhấn giữ icon, dropdown "Sửa", icon `ic_shortcut_menu_*`.

### Task 4: Thẻ xác nhận dùng chung, luôn trên cùng
**Bắt buộc kiểm tay:** có.
- [x] Step 1: `RemoveAppDialog.kt`: mọi hàm `show…Dialog` đặt `removeAppRequest`; `RemoveAppOverlay` tự vẽ lớp tối 15% theo tiến độ, `BackHandler` đóng thẻ; bỏ `showCard` / `RemoveCard` (`Dialog` cửa sổ).
- [x] Step 2: `HomeScreen`: bỏ `RemoveAppOverlay` và phần tối / ghi `homeBackdrop` theo `removeOverlayProgress`.
- [x] Step 3: `HomeActivity`: vẽ `RemoveAppOverlay(searchBackdrop)` sau `widgetPicker.Render()`; bật ghi `searchBackdrop` khi thẻ mở; `dispatchTouchEvent` bỏ qua bộ vuốt Thư viện / Today / kéo từ Thư viện khi thẻ mở.
- [x] Step 4: Kịch bản kiểm: edit mode, bấm "−" của app (lưới, dock, trong thư mục) → thẻ hiện; chạm icon khác / chỗ trống / nhấn giữ ngoài thẻ → chỉ đóng thẻ, vẫn ở edit mode, không mở menu / kéo; chạm chữ trong thẻ → không gì; vuốt ngang ngoài thẻ → không mở Thư viện / Today; Back → đóng thẻ, vẫn ở edit mode. Lặp lại với "−" widget (Home), "−" app trong Thư viện (chế độ sửa), "−" widget của Today: cùng kiểu thẻ kính tối chữ trắng, nút đúng hành động.

### Task 5: Docs
- [x] Step 1: `lessons/`: cha có `pointerInput` pass Initial thấy mọi chạm của lớp phủ con → lớp phủ chặn tương tác phải là anh em trên cùng, không phải con; tham số `retracted` / `maxHeightFraction` của `DsBottomSheetLayer`.

## Kết quả kiểm chứng

Chủ dự án đóng plan 2026-11-25 trước khi kiểm hết các kịch bản ở Task 2 Step 3 và Task 4 Step 4 (đã xem thẻ trên Oppo và Live 4). Lỗi phát sinh sau này ghi vào plan mới, dẫn lại plan này.

## Nhật ký

| Ngày | Commit | Việc | Kết quả |
|---|---|---|---|
| 2026-11-25 | — | Plan, chủ dự án trả lời Q1–Q3, code Task 1–4 | Chờ build + kiểm |
| 2026-11-25 | — | Chủ dự án hỏi thẻ trên Live 4 "đen": so ảnh trước / sau, xác định là giới hạn dưới API 31 có từ RA2 (ghi ở "Ngoài phạm vi") | Giữ nguyên mẫu thẻ của Nam |
| 2026-11-25 | — | Chủ dự án đóng plan, gộp với WF vào một PR cho anh Nam; đo lại vuốt trang trên Oppo sau khi cài (cảm giác giật) | Đóng |
