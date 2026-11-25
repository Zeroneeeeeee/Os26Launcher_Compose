# WS — Kéo widget đè lên widget khác thì đổi chỗ (xem trước ngay lúc kéo) — Plan

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án chọn hướng A trong chat)
**Đổi schema Room:** không

**Goal:** Kéo widget D đè lên đúng một widget W trên cùng trang: W trượt về chỗ cũ của D ngay lúc kéo (ngón dừng như đẩy-dồn icon), thả là ghi. Trước đây (G2c Task 8) widget chồng widget bị từ chối: không xem trước, thả thì bay về.

## Hiện trạng (đọc code 2026-11-25)

- Xem trước (`HomeDragState.applyGridPreview`), cập nhật lạc quan (`HomeViewModel.planGridOptimistic`) và ghi DB (`LayoutReconciler.moveToHomeInTransaction`) cùng gọi `GridInsertPlanner.plan` rồi áp mọi `placements` (kể cả widget, giữ span). Nên đổi luật ở `planWidget` là đủ cho cả ba; đường ghi không sửa.
- Từ chối nằm ở `GridInsertPlanner.planWidget` (`overlapsWidget → rejected`), `HomeDragState.desiredGrid` (`overlapsOtherWidget → NO_HOVER`) và `HomeDragState.drop` (`overlapsOtherWidget → cancelDrop`).

## Hướng đã cân nhắc

| Hướng | Đánh đổi | Chọn |
|---|---|---|
| **A. Đổi chỗ:** D vào chỗ mới, W về đúng chỗ cũ của D; icon bị phủ dồn theo luật "dồn sát đuôi" sẵn có | Ít code, dễ đoán; không gỡ được mọi ca (W không vừa chỗ cũ thì vẫn từ chối) | ✔ |
| B. Dàn lại cả trang kiểu iOS (widget + icon là một dòng chảy) | Giống iOS nhất, nhưng viết lại planner, dễ lệch bố cục đang có | |
| C. Đẩy W sang chỗ trống gần nhất | Khó đoán, W có thể nhảy xa / sang trang | |

## Luật (hướng A)

1. Áp khi hình chữ nhật của D (neo đã kẹp vào lưới) chồng **đúng một** widget W và D đang ở **cùng trang** đích. Không thì giữ luật cũ (từ chối).
2. Thử neo của D theo thứ tự: (a) neo đích; (b) neo của W (D lấy đúng chỗ W). Chọn neo đầu tiên mà: W đặt ở neo cũ của D vừa lưới; W mới không chồng D mới; D mới chỉ chồng W (ở chỗ cũ của W); W mới không chồng widget khác.
3. Icon nằm trong D mới hoặc W mới bị dồn: mọi icon có chỉ số đọc ≥ min(neo D mới, neo W mới) xếp sát vào ô tự do (như `planWidget` hiện nay), tràn sang trang kế như cũ.
4. Xem trước bật sau `PUSH_ARM_MS` (500 ms) khi ngón đã dừng, như đẩy-dồn; ô neo của xem trước là neo D mà planner chọn, nên thả ghi đúng thứ đã thấy.
5. Widget mới từ bảng chọn, widget kéo từ trang khác: không đổi chỗ (không có chỗ cũ trên trang này).

## Task

- [x] **Task 1 — `GridInsertPlanner.planWidget`:** luật trên + test trong `GridInsertPlannerTest` (đổi chỗ cơ bản; dùng neo W khi neo đích làm W chồng D; W không vừa → từ chối; chồng 2 widget → từ chối; D từ trang khác → từ chối; icon bị W mới phủ được dồn). Sửa KDoc G2c.
- [x] **Task 2 — `HomeDragState`:** `desiredGrid` (nhánh widget chồng widget: chạy planner trên trang, không từ chối thì PUSH với ô neo = neo D của plan); `drop` (thay kiểm `overlapsOtherWidget` bằng "planner từ chối").
- [x] **Task 3 — Kiểm trên máy:** A17, trang có widget "không khả dụng" + đồng hồ 2×2: kéo lên đồng hồ, dừng → đồng hồ trượt về chỗ cũ; thả → giữ sau khi mở lại app.

## Kết quả kiểm chứng (2026-11-25)

- `GridInsertPlannerTest`: 21/21 đạt (6 test WS mới).
- A17, trang 4 (đồng hồ 4×2; VNeTraffic (0,2), đồng hồ 2×2 (1,2), Wallet (3,2); widget Thời tiết Samsung "không khả dụng" 3×2 (0,4)): kéo widget 3×2 lên (0,2), dừng → đồng hồ 2×2 trượt về (0,4), VNeTraffic / Wallet dồn về (3,2) / (3,3); thả → giữ đúng như xem trước.
- Mở lại app (force-stop): DB có đồng hồ 2×2 (0,2), widget 3×2 (0,4), VNeTraffic (3,2), Wallet (3,3) — đúng lần đổi chỗ thứ hai do chủ dự án kéo tay (xác nhận 2026-11-25). Bố cục giữ qua khởi động lại.

## Ngoài phạm vi

- Dàn lại cả trang kiểu iOS (hướng B); widget chồng nhiều widget.
- Rút ngắn thời gian chờ xem trước riêng cho widget.
