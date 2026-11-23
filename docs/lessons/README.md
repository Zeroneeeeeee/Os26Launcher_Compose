# Bài học của dự án, theo chủ đề

`../AGENTS.md` giữ quy ước và thông tin mọi agent cần mỗi lần. Các bài học theo mốc (trước đây nối đuôi trong `AGENTS.md`) nằm ở đây, mỗi file một chủ đề. **Đọc file chủ đề liên quan tới việc mình sắp làm** (vd làm kính thì đọc `glass.md`), không cần đọc hết.

## Chủ đề

| File | Nội dung | Số mục |
|---|---|---|
| [glass.md](glass.md) | **Kính (liquid glass).** Thư viện LiquidGlass, mức kính, áp kính theo phần tử, làm mới kính khi đổi nền, số đo hiệu năng kính, kính mờ tĩnh trong Compose. | 15 |
| [drag-and-layout.md](drag-and-layout.md) | **Kéo-thả và bố cục lưới.** Custom ViewGroup của lưới, trạng thái kéo-thả, dock/lưới/trang, menu nhấn giữ (cả từ Thư viện/Tìm kiếm), bố cục và cỡ lưới, thư mục nhiều trang, Today View, kéo thẻ từ bảng chọn widget. | 17 |
| [startup-performance.md](startup-performance.md) | **Khởi động và đo hiệu năng.** Số đo nạp danh sách app, cold start, cache nhãn, bản debug so với release, R8. | 8 |
| [search.md](search.md) | **Tìm kiếm.** Lõi tìm kiếm, lớp tìm kiếm, pill, trợ năng, mở rộng tìm kiếm, danh bạ. | 9 |
| [widgets.md](widgets.md) | **Widget.** Widget Đồng hồ/Lịch và widget trên lưới. | 2 |
| [icons-and-app-list.md](icons-and-app-list.md) | **Icon và danh sách app.** Nạp danh sách app, cache icon, kiểu và bo góc icon. | 5 |
| [permissions-and-device-testing.md](permissions-and-device-testing.md) | **Quyền, hiển thị app và kiểm trên máy thật.** Quyền runtime, `<queries>`, app mặc định ở dock, bẫy khi đo/cài trên Oppo và Live 4. | 12 |
| [home-pager-animation.md](home-pager-animation.md) | **Home, pager và hoạt ảnh.** Launcher mặc định, inset, trang, thư viện ứng dụng, hoạt ảnh, tách controller, Onboarding và splash. | 14 |
| [settings-and-data.md](settings-and-data.md) | **Cài đặt và dữ liệu.** Màn Cài đặt, ảnh nền, đồng bộ bố cục với Room khi cài/gỡ app, catch-up lúc khởi động, bỏ khỏi Home khác ẩn và Migration Room. | 8 |
| [design-system.md](design-system.md) | **Hệ thống thiết kế v3 (DS3, RS).** Token `ds_*`, thành phần `Ds*` trong `core/designsystem`, bề mặt kính xấp xỉ, lỗi lint API 26–27; áp v3 vào Cài đặt (card nhóm bằng ItemDecoration, dấu chọn hai lớp, nội dung DsDialog). | 2 |

## Bài học mới đi vào đâu

Từ 2026-11-16 (một tính năng một lúc, [../Workflow.md](../Workflow.md)):

1. Trong lúc làm, Claude ghi bài học vào mục "Bài học" của knowledgement (hoặc Nhật ký của plan nếu tính năng không có knowledgement).
2. Ở bước chốt plan lần cuối (`Workflow.md` bước 7), Claude chép từng dòng vào **cuối file chủ đề đúng** trong thư mục này (mục mới có tiêu đề `## <mốc> — <việc>: bài học (ngày, máy)`), giữ ngày và nhãn nguồn. Bài học không thuộc chủ đề nào: thêm file chủ đề mới và một dòng vào bảng trên.
3. Chỉ quy ước làm việc, build/toolchain, môi trường, quy tắc máy thật, quy tắc viết docs, việc trước khi phát hành mới vào `../AGENTS.md`.

## Bảng tra: tên mục cũ của `AGENTS.md` sang file mới

Các plan và knowledgement cũ ghi "AGENTS.md mục X" bằng chữ; tra tên mục X ở đây.

| Mục cũ của AGENTS.md | Nơi mới |
|---|---|
| Quy ước làm việc trong dự án này | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
| Tên gọi và package | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
| Build và toolchain | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
| Thư viện LiquidGlass (`com.github.QWEA0:liquidglass:v2.0.11`) | [glass.md](glass.md) |
| Hiệu năng đã đo (demo, 2026-10-15) | [glass.md](glass.md) |
| Môi trường của Claude khi làm việc trên dự án này | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
| Kiểm chứng trên máy thật | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
| Launcher mặc định (M1 Task 3) | [home-pager-animation.md](home-pager-animation.md) |
| Inset và layout màn hình chính (M1 Task 5) | [home-pager-animation.md](home-pager-animation.md) |
| Danh sách app (M2 Task 1, 1B) | [icons-and-app-list.md](icons-and-app-list.md) |
| Danh sách app: kết quả đo và bẫy (M2 Task 2-4, 2026-10-26) | [icons-and-app-list.md](icons-and-app-list.md) |
| Dock mặc định (M3 Task 4, 2026-10-26/27, đo trên Vsmart Live 4) | [permissions-and-device-testing.md](permissions-and-device-testing.md) |
| "Demo kính" không mở được từ lưới launcher (M3 Task 5, 2026-10-27) | [home-pager-animation.md](home-pager-animation.md) |
| Nạp danh sách app: đo chi tiết sau khi chia nhỏ (M3 Task 1, 2026-10-26, Live 4, 87 app) | [startup-performance.md](startup-performance.md) |
| Custom ViewGroup cho lưới icon: bẫy đã gặp (M3 Task 2-3-5) | [drag-and-layout.md](drag-and-layout.md) |
| Đồng bộ bố cục khi cài và gỡ app (M4 Task 3, 2026-10-27, đo trên Vsmart Live 4) | [settings-and-data.md](settings-and-data.md) |
| Catch-up khi khởi động lại launcher (M4 Task 4, 2026-10-27, đo trên Vsmart Live 4) | [settings-and-data.md](settings-and-data.md) |
| Viết docs | [AGENTS.md](../AGENTS.md) (giữ nguyên); [glass.md](glass.md); [drag-and-layout.md](drag-and-layout.md) |
| M5 — khóa xoay, system bar, polish lưới+dock | [home-pager-animation.md](home-pager-animation.md) |
| M6 — lớp kính (Task 1, 2026-10-28, đo trên Oppo A3x) | [glass.md](glass.md) |
| M6 — lớp kính (Task 2, 2026-10-28, đo trên Vsmart Live 4) | [glass.md](glass.md) |
| M6 — lớp kính (Task 3, 2026-10-28, đo trên Oppo A3x + Vsmart Live 4) | [glass.md](glass.md) |
| M7 — chế độ chỉnh sửa & kéo-thả (Task 3-5, 2026-10-28, đo trên Vsmart Live 4) | [drag-and-layout.md](drag-and-layout.md) |
| M7 — trạng thái kéo-thả KHÔNG được nằm trong view của RecyclerView/ViewPager2 (Task 5, 2026-10-29, Vsmart Live 4) | [drag-and-layout.md](drag-and-layout.md) |
| M7 Task 6 — kéo-thả dock ↔ lưới: bài học triển khai | [drag-and-layout.md](drag-and-layout.md) |
| M7 Task 7 — lật trang khi kéo + trang tạm: bài học triển khai | [drag-and-layout.md](drag-and-layout.md) |
| M7 Task 8 — dấu "−" ẩn nhanh: bài học triển khai | [drag-and-layout.md](drag-and-layout.md) |
| M7 Task 9 — Thư viện ứng dụng: bài học triển khai | [home-pager-animation.md](home-pager-animation.md) |
| M7 Task 10 — menu nhấn giữ icon: bài học triển khai | [drag-and-layout.md](drag-and-layout.md) |
| M7 Task 11 — đóng mốc: bài học | [drag-and-layout.md](drag-and-layout.md) |
| M8a Task 2 — bỏ cỡ lưới cứng: bài học triển khai (2026-10-29) | [drag-and-layout.md](drag-and-layout.md) |
| M8a Task 3 — màn Cài đặt: bài học triển khai (2026-10-29) | [settings-and-data.md](settings-and-data.md) |
| M8a Task 4 — mức kính: bài học triển khai (2026-10-29) | [glass.md](glass.md) |
| M8a Task 5 — đổi ảnh nền từ Cài đặt: bài học và kết quả đo (2026-10-29) | [settings-and-data.md](settings-and-data.md) |
| M8a Task 6 — đổi cỡ lưới + re-flow: bài học triển khai (2026-10-30) | [settings-and-data.md](settings-and-data.md) |
| M8a Task 7 — đóng mốc: bài học (2026-10-30) | [settings-and-data.md](settings-and-data.md) |
| M8b Task 1 — lõi tìm kiếm: bài học (2026-10-30) | [search.md](search.md) |
| M8b Task 2 — cử chỉ vuốt xuống + khung lớp tìm kiếm: bài học (2026-10-30) | [search.md](search.md) |
| M8b Task 3 — danh sách kết quả + mở app: bài học (2026-10-30) | [search.md](search.md) |
| M8c Task 1 — pill tìm kiếm + chấm ổ quay số: bài học (2026-10-30) | [search.md](search.md) |
| Sửa lỗi gỡ app làm dồn kín bố cục (2026-10-30) | [settings-and-data.md](settings-and-data.md) |
| M8c Task 2 — bấm pill + trợ năng: bài học (2026-10-30) | [search.md](search.md) |
| G2a Phần B — App Library (B2): bài học (2026-10-30) | [home-pager-animation.md](home-pager-animation.md) |
| Demo polish (2026-10-30): bài học | [home-pager-animation.md](home-pager-animation.md) |
| TL2 — bo góc icon: bài học (2026-11-03, Vsmart Live 4) | [icons-and-app-list.md](icons-and-app-list.md) |
| TL1 — vuốt trang, rung, nút chỉnh sửa: bài học (2026-11-03, Vsmart Live 4) | [home-pager-animation.md](home-pager-animation.md) |
| GL — áp kính: bài học (2026-11-03 đến 2026-11-04, Vsmart Live 4 + Oppo A3x) | [glass.md](glass.md) |
| R1 — tách `HomeActivity`: bài học (2026-11-04, Vsmart Live 4) | [home-pager-animation.md](home-pager-animation.md) |
| G2b — kiểu icon: bài học (2026-11-04, Vsmart Live 4) | [icons-and-app-list.md](icons-and-app-list.md) |
| AN — hoạt ảnh và đo panel: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x) | [home-pager-animation.md](home-pager-animation.md) |
| P1 — cold start Oppo A3x: số đo (2026-11-05, APK debug, 345 activity, 5 lượt sau một lượt khởi động) | [startup-performance.md](startup-performance.md) |
| GP — nhấn giữ khoảng trống: bài học (2026-11-05, Vsmart Live 4) | [drag-and-layout.md](drag-and-layout.md) |
| LC — cache nhãn app: bài học (2026-11-05, Oppo A3x + Vsmart Live 4, APK debug) | [startup-performance.md](startup-performance.md) |
| G2c — widget trên lưới: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x) | [widgets.md](widgets.md) |
| GS — kính theo phần tử: bài học (2026-11-05, Oppo A3x + Vsmart Live 4) | [glass.md](glass.md) |
| M8d — mở rộng tìm kiếm: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x) | [search.md](search.md) |
| B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4) | [widgets.md](widgets.md); [drag-and-layout.md](drag-and-layout.md); [icons-and-app-list.md](icons-and-app-list.md); [startup-performance.md](startup-performance.md); [permissions-and-device-testing.md](permissions-and-device-testing.md) |
| DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4) | [permissions-and-device-testing.md](permissions-and-device-testing.md); [glass.md](glass.md); [home-pager-animation.md](home-pager-animation.md) |
| RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4) | [startup-performance.md](startup-performance.md); [permissions-and-device-testing.md](permissions-and-device-testing.md); [search.md](search.md) |
| Trước khi phát hành lên cửa hàng (WP, 2026-11-09) | [AGENTS.md](../AGENTS.md) (giữ nguyên) |
