# Việc còn lại (backlog)

> DC (2026-11-25). Mỗi việc một dòng, kèm nguồn. Bắt đầu làm một mục: chủ dự án duyệt → viết plan `plans/<ID>-<slug>.md` → xong thì chuyển dòng xuống "Đã xong" kèm tên plan. Mục lục plan: [README.md](README.md).

## Đang mở

| # | Việc | Loại | Ưu tiên | Nguồn | Ghi chú |
|---|---|---|---|---|---|
| B3 | Phần còn lại của bố cục lần đầu: thư mục gợi ý (Google / Mạng xã hội / Trò chơi), bước "Sắp xếp màn hình chính" trong Onboarding | Tính năng | — | `IosLauncher/docs/plans/FL-first-run-layout.md` (Q1–Q10 chưa trả lời) | **Đang làm ở AG** (`AG-app-groups.md`, 2026-11-26): thư mục gợi ý thành mục "Gom nhóm ứng dụng" trong Cài đặt → Chung (6 nhóm, sheet xem trước); bước Onboarding bỏ (chủ dự án). Chờ kiểm tay |
| B4 | Mở rộng kéo từ Thư viện: gộp thư mục khi thả lên app / thư mục; kéo từ kết quả tìm kiếm; kéo nhiều app một lúc | Tính năng | — | `LD-library-drag.md` (ngoài phạm vi) | |
| B5 | Lối "Sắp xếp lại màn hình chính" trong Cài đặt cho người đã có bố cục | Tính năng | — | FL, WF (ngoài phạm vi) | AG (2026-11-26) cho người đã có bố cục gom app thành thư mục theo nhóm; sắp xếp lại cả màn hình vẫn chưa có |
| B6 | Thẻ xác nhận xóa trên máy dưới Android 12: nền là kính mờ tĩnh (`dsGlass(frosted = …)`) thay cho nền tối trong suốt | Lỗi hiển thị | Thấp | `PD-picker-menus-dialogs.md` (ngoài phạm vi), ảnh Live 4 | Theo quyết định "dưới API 31 chỉ mờ tĩnh" |
| B7 | Kiểm TalkBack (K1 mục 5c) | Kiểm | — | `PG4-home-perf-and-device-checks.md` | Chủ dự án bỏ qua 2026-11-23 |
| B8 | Nhãn tên dưới widget tự vẽ như iOS (dùng phần đáy để trống của thẻ vuông) | Tính năng | — | `PW-menu-block-widget-square-font.md` (ngoài phạm vi), FL Q9 | |
| B9 | `GridMetricsTest` hỏng 4/112 test (default sizing, dock cap, measure at density one, cellAt): test viết từ `2615d5d`, chưa cập nhật theo `5d96e78` của anh Nam (icon scale 1, nhãn 12,5 sp, `ROW_GUTTER_SCALE = 0.5` nên `gutterY` 8 → 4, hàng thấp hơn); code đúng thiết kế, cần sửa số trong test | Test | Thấp | `.\gradlew.bat :app:testDebugUnitTest` (chủ dự án chạy 2026-11-26, sau merge `016c6d0`) | Chủ dự án 2026-11-26: bỏ qua, để anh Nam sửa; `master` cũng hỏng đúng 4 test này |

## Chờ anh dvha (tick khi merge lên `master`)

| # | Việc | Nguồn |
|---|---|---|
| D1 | RA phần giao diện: menu nhấn giữ ở Thư viện và Tìm kiếm, mục "Thêm vào Màn hình chính" (anh dvha đã làm, chưa đẩy lên) | `AGENTS.md` (TF Task 5) · Kiểm 2026-11-26 (sau merge `master` `016c6d0`): chưa có (không có chuỗi "Thêm vào Màn hình chính", chưa có `TF-test-fixes.md`) |
| D2 | LD Task 4: kiểm tay kéo app từ Thư viện ra Home | `LD-library-drag.md` · Kiểm 2026-11-26: plan vẫn "chờ kiểm tay", không có commit mới |
| D4 | Tài liệu cho kính sau icon kiểu Kính (`core/glass/compose/IconGlass.kt`, `IconGlassShader.kt`, công tắc `noIconGlass` / `noIconShader`, commit `7abcbcf`, `5f716ae`): chưa có plan / docs | `git log --author=dvha` (2026-11-26) |

## Hoãn

| # | Việc | Lý do | Nguồn |
|---|---|---|---|
| H1 | Home nhiều trang (> 5) còn khựng lúc dựng trang mới (baseline profile, trang nhẹ hơn khi dựng) | Chủ dự án kiểm Oppo thấy ổn (2026-11-25); làm khi mượn được máy lag để xác nhận nguyên nhân (anh Nam báo còn lag) | `SW-home-swipe-smoothness.md` Task 4 |

## Đã bỏ

| Việc | Quyết định |
|---|---|
| Kính mờ tĩnh kiểu XML cho mọi bề mặt còn lại dưới API 31 | Chủ dự án 2026-11-25: dưới API 31 chỉ mờ tĩnh cho qua, tập trung API 31+ (`AGENTS.md`) |

## Đã xong (gần đây)

| Việc | Plan |
|---|---|
| WS: kéo widget đè lên widget khác thì đổi chỗ (anh dvha, kiểm A17 2026-11-25) | `WS-widget-swap.md` |
| B2: Thư viện chỉ tìm app; Spotlight lúc gõ chia section cuộn được (Lối tắt, Danh bạ, Cài đặt, Đề xuất), mọi section thẻ kính thật; "Gần đây" ở ô trống trong thẻ kính; thanh tìm kiếm Today kính thật (chủ dự án đã kiểm) | `SP-spotlight-sections.md` |
| D3 · G2: pager dừng lệch khi kéo icon về trang cuối (chủ dự án kiểm ổn 2026-11-26) | `PG4-home-perf-and-device-checks.md` (K1 mục 1a) |
| Kéo widget từ trang danh sách, "App gần đây" trên Home, bố cục lần đầu (chủ dự án đã kiểm) | `WF-widget-drag-first-run.md` |
| Bảng chọn widget cao tối đa, giữ là kéo ngay, chữ trắng menu, thẻ xác nhận chung chặn chạm (chủ dự án đã kiểm) | `PD-picker-menus-dialogs.md` |
| Đồng bộ tài liệu, mục lục, backlog | `DC-docs-sync.md` |
| Widget Pin tự vẽ kiểu iOS (chủ dự án đã kiểm) | `BW-battery-widget.md` |
| Hộp thoại hết khung mờ thừa; nút "+" / "Xong" của Today dạng kính (chủ dự án đã kiểm) | `DW-dialog-window-glass.md` |
| B1 menu nhấn giữ chặn chạm; widget tự vẽ vuông; font Inter cho widget; tắt nhãn giữ vị trí hàng (chủ dự án đã kiểm) | `PW-menu-block-widget-square-font.md` |
