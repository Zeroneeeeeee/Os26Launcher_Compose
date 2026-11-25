# Việc còn lại (backlog)

> DC (2026-11-25). Mỗi việc một dòng, kèm nguồn. Bắt đầu làm một mục: chủ dự án duyệt → viết plan `plans/<ID>-<slug>.md` → xong thì chuyển dòng xuống "Đã xong" kèm tên plan. Mục lục plan: [README.md](README.md).

## Đang mở

| # | Việc | Loại | Ưu tiên | Nguồn | Ghi chú |
|---|---|---|---|---|---|
| B2 | Spotlight (tìm kiếm mở từ ô "Tìm kiếm" / vuốt xuống ở Home) hiện cả danh bạ, shortcut, mục Cài đặt như tìm kiếm của Thư viện | Tính năng | Chờ quyết | `PG4-home-perf-and-device-checks.md` (ngoài phạm vi) | Hiện chế độ Spotlight chỉ giữ kết quả app (`SearchViewModel` nhánh `SearchMode.SPOTLIGHT`, `filterIsInstance<SearchResult.App>()`) |
| B3 | Phần còn lại của bố cục lần đầu: widget Pin, thư mục gợi ý (Google / Mạng xã hội / Trò chơi), bước "Sắp xếp màn hình chính" trong Onboarding | Tính năng | — | `IosLauncher/docs/plans/FL-first-run-layout.md` (Q1–Q10 chưa trả lời) | Đồng hồ + Lịch + icon launcher đã làm ở WF |
| B4 | Mở rộng kéo từ Thư viện: gộp thư mục khi thả lên app / thư mục; kéo từ kết quả tìm kiếm; kéo nhiều app một lúc | Tính năng | — | `LD-library-drag.md` (ngoài phạm vi) | |
| B5 | Lối "Sắp xếp lại màn hình chính" trong Cài đặt cho người đã có bố cục | Tính năng | — | FL, WF (ngoài phạm vi) | |
| B6 | Thẻ xác nhận xóa trên máy dưới Android 12: nền là kính mờ tĩnh (`dsGlass(frosted = …)`) thay cho nền tối trong suốt | Lỗi hiển thị | Thấp | `PD-picker-menus-dialogs.md` (ngoài phạm vi), ảnh Live 4 | Theo quyết định "dưới API 31 chỉ mờ tĩnh" |
| B7 | Kiểm TalkBack (K1 mục 5c) | Kiểm | — | `PG4-home-perf-and-device-checks.md` | Chủ dự án bỏ qua 2026-11-23 |
| B8 | Nhãn tên dưới widget tự vẽ như iOS (dùng phần đáy để trống của thẻ vuông) | Tính năng | — | `PW-menu-block-widget-square-font.md` (ngoài phạm vi), FL Q9 | |

## Chờ anh dvha (tick khi merge lên `master`)

| # | Việc | Nguồn |
|---|---|---|
| D1 | RA phần giao diện: menu nhấn giữ ở Thư viện và Tìm kiếm, mục "Thêm vào Màn hình chính" (anh dvha đã làm, chưa đẩy lên) | `AGENTS.md` (TF Task 5) |
| D2 | LD Task 4: kiểm tay kéo app từ Thư viện ra Home | `LD-library-drag.md` |
| D3 | G2: pager dừng lệch khi kéo icon về trang cuối | `PG4-home-perf-and-device-checks.md` (K1 mục 1a) |

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
| Kéo widget từ trang danh sách, "App gần đây" trên Home, bố cục lần đầu (chủ dự án đã kiểm) | `WF-widget-drag-first-run.md` |
| Bảng chọn widget cao tối đa, giữ là kéo ngay, chữ trắng menu, thẻ xác nhận chung chặn chạm (chủ dự án đã kiểm) | `PD-picker-menus-dialogs.md` |
| Đồng bộ tài liệu, mục lục, backlog | `DC-docs-sync.md` |
| B1 menu nhấn giữ chặn chạm; widget tự vẽ vuông; font Inter cho widget; tắt nhãn giữ vị trí hàng (chủ dự án đã kiểm) | `PW-menu-block-widget-square-font.md` |
