# Việc còn lại (backlog)

> DC (2026-11-25). Mỗi việc một dòng, kèm nguồn. Bắt đầu làm một mục: chủ dự án duyệt → viết plan `plans/<ID>-<slug>.md` → xong thì chuyển dòng xuống "Đã xong" kèm tên plan. Mục lục plan: [README.md](README.md).

## Đang mở

| # | Việc | Loại | Ưu tiên | Nguồn | Ghi chú |
|---|---|---|---|---|---|
| B20 | Widget thời tiết chưa có quyền vị trí: bấm thì xin quyền (cấp xong tự cập nhật vị trí, không mở bảng); quyền Vị trí / Danh bạ bị chặn thì hộp "Đóng / Cấp quyền" nói rõ lý do, ở mọi chỗ xin quyền | Tính năng | Cao | Chủ dự án 2026-11-26 17:03 | **Code xong 2026-11-26 (`PM-permission-flow.md`), chờ chủ dự án build + kiểm** |
| B19 | Bỏ màn "Màn hình chính mặc định" (Cài đặt → hàng `settings_hub_theme` → `ThemeSettingsActivity`, chọn preset Liquid Glass 27 / Classic 26 / Minimal / Dark Pro) cùng logic preset; bước THEME của Onboarding đổi thành chọn kiểu icon (G2b, chủ dự án chốt 2026-11-26) | Dọn + tính năng | **Cao nhất** | Chủ dự án 2026-11-26 | Lý do: các mục cài đặt nhỏ (kiểu icon, kính, hình nền…) đã thay hết vai trò của preset. Phạm vi đọc nhanh: `ThemeSettingsActivity/Content/ViewModel`, `domain/config/ThemePreset.kt`, `ApplyThemeUseCase`, `ObserveThemeStateUseCase`, `ConfigEnums`, `SettingsHubScreen:47`, `OnboardingViewModel` (bước THEME), `OnboardingScreen` (`ThemePage`, dòng tóm tắt), chuỗi `settings_theme_*` / `onb_theme_*`, ảnh xem trước `onb_theme_*`, Manifest. Giữ logic "đặt làm launcher mặc định" (`DefaultLauncherRepository`, `DefaultLauncherAction`, `DefaultLauncherPromptController`: hero card, lời nhắc) vì không thuộc màn này. B14 (ghép hình nền theo preset) đã bỏ vì việc này . **Code xong 2026-11-26 (`TR-remove-theme-presets.md`), chờ chủ dự án build + kiểm** |
| B4 | Mở rộng kéo từ Thư viện: gộp thư mục khi thả lên app / thư mục; kéo từ kết quả tìm kiếm; kéo nhiều app một lúc | Tính năng | — | `LD-library-drag.md` (ngoài phạm vi) | |
| B5 | Lối "Sắp xếp lại màn hình chính" trong Cài đặt cho người đã có bố cục | Tính năng | — | FL, WF (ngoài phạm vi) | AG (2026-11-26): Cài đặt → Chung gom app thành thư mục theo nhóm; sắp xếp lại cả màn hình vẫn chưa có |
| B6 | Thẻ xác nhận xóa trên máy dưới Android 12: nền là kính mờ tĩnh (`dsGlass(frosted = …)`) thay cho nền tối trong suốt | Lỗi hiển thị | Thấp | `PD-picker-menus-dialogs.md` (ngoài phạm vi), ảnh Live 4 | Theo quyết định "dưới API 31 chỉ mờ tĩnh" |
| B7 | Kiểm TalkBack (K1 mục 5c) | Kiểm | — | `PG4-home-perf-and-device-checks.md` | Chủ dự án bỏ qua 2026-11-23 |
| B8 | Nhãn tên dưới widget tự vẽ như iOS (dùng phần đáy để trống của thẻ vuông) | Tính năng | — | `PW-menu-block-widget-square-font.md` (ngoài phạm vi), FL Q9 | |
| B9 | `GridMetricsTest` hỏng 4/112 test (default sizing, dock cap, measure at density one, cellAt): test viết từ `2615d5d`, chưa cập nhật theo `5d96e78` của anh Nam (icon scale 1, nhãn 12,5 sp, `ROW_GUTTER_SCALE = 0.5` nên `gutterY` 8 → 4, hàng thấp hơn); code đúng thiết kế, cần sửa số trong test | Test | Thấp | `.\gradlew.bat :app:testDebugUnitTest` (chủ dự án chạy 2026-11-26, sau merge `016c6d0`) | Chủ dự án 2026-11-26: bỏ qua, để anh Nam sửa; `master` cũng hỏng đúng 4 test này |
| B11 | Nút "−" (ẩn / xóa app) ở edit mode: hoạt ảnh phóng to khi vào, thu nhỏ khi ra edit mode | Tính năng | — | Chủ dự án 2026-11-26 | |
| B12 | Ẩn nhãn ("Hiện tên" tắt) thì icon trong thư mục vẽ lệch | Lỗi hiển thị | — | Chủ dự án 2026-11-26, video `Screen_recording_20261126_153649.mp4` | Đọc code + video: tắt nhãn thì `GridMetrics(3, 3, showLabels = false)` cho icon bằng bề ngang ô nhưng ô vẫn cao như cũ; `HomeAppCell` căn icon `TopCenter` nên icon dồn lên trên (lề trên ~1/2 lề dưới). Thêm: trang kế của thư mục ló vào đệm phải của bảng (bước trang = `side`, cắt theo khung gồm cả đệm) . **Code xong 2026-11-26 (`FX-folder-polish.md`), chờ chủ dự án kiểm** |
| B16 | Mở thư mục: Home phía sau bảng không mờ (chỉ trong bảng mờ) | Lỗi hiển thị | — | Chủ dự án 2026-11-26, video | `HomeScreen` chỉ `Modifier.blur(FOLDER_BLUR_DP)` khi API ≥ 31; dưới 31 chỉ tối 15 % (`FOLDER_DIM`). Chủ dự án: máy quay video là Android 11 (API 30) → đúng nhánh dưới 31. Hướng sửa: phủ ảnh chụp Home đã làm mờ (`folderSnapshot` / `folderFrosted`, đã có sẵn cho kính bảng) ra toàn màn sau bảng, mờ dần theo `folderProgress` . **Code xong 2026-11-26 (`FX-folder-polish.md`), chờ chủ dự án kiểm** |
| B17 | Bắt đầu kéo app (từ menu nhấn giữ) còn dư ảnh icon ở chỗ cũ | Lỗi hiển thị | — | Chủ dự án 2026-11-26, video | Đọc code: `HomeIconMenu` vẽ icon nổi tại chỗ icon thật, không mờ dần theo `progress`, chỉ mất khi hoạt ảnh đóng menu xong (`shown = null`); kéo bắt đầu thì ảnh kéo + icon nổi cùng hiện ~2 khung . **Code xong 2026-11-26 (`FX-folder-polish.md`), chờ chủ dự án kiểm** |
| B18 | Icon trong thư mục to hơn icon ngoài Home | Lỗi hiển thị | — | Chủ dự án 2026-11-26, video | `HomeFolderPanel` cố ý đo icon riêng (`GridMetrics(3, 3)`, comment "không lấy cỡ icon Home"); iOS: icon trong thư mục cùng cỡ ngoài Home. Menu nhấn giữ trong thư mục lại vẽ icon nổi theo cỡ Home (`cells.iconSize`) nên lệch cỡ với ô . **Code xong 2026-11-26 (`FX-folder-polish.md`), chờ chủ dự án kiểm** |
| B13 | Widget của app khác lấy kích cỡ sai | Lỗi | — | Chủ dự án 2026-11-26 | Chưa có ảnh / log; chủ dự án cho xem khi cần |

## Chờ anh dvha (tick khi merge lên `master`)

| # | Việc | Nguồn |
|---|---|---|
| D1 | RA phần giao diện: menu nhấn giữ ở Thư viện và Tìm kiếm, mục "Thêm vào Màn hình chính" (anh dvha đã làm, chưa đẩy lên) | `AGENTS.md` (TF Task 5) · Kiểm 2026-11-26 (sau merge `master` `016c6d0`): chưa có (không có chuỗi "Thêm vào Màn hình chính", chưa có `TF-test-fixes.md`) |
| D2 | LD Task 4: kiểm tay kéo app từ Thư viện ra Home | `LD-library-drag.md` · Kiểm 2026-11-26: plan vẫn "chờ kiểm tay", không có commit mới |
| D4 | Tài liệu cho kính sau icon kiểu Kính (`core/glass/compose/IconGlass.kt`, `IconGlassShader.kt`, công tắc `noIconGlass` / `noIconShader`, commit `7abcbcf`, `5f716ae`): chưa có plan / docs | `git log --author=dvha` (2026-11-26) |

## Hoãn

| # | Việc | Lý do | Nguồn |
|---|---|---|---|
| B15 | Cài đặt → Vị trí: "Cập nhật vị trí" lưu chữ "Vị trí hiện tại" khi `reverse` (OWM `geo/1.0/reverse`) lỗi, cập nhật im lặng chỉ đổi tên khi đi xa > 5 km | Chủ dự án 2026-11-26: bỏ khỏi việc cần làm; khóa đã ghi thẳng trong `buildTypes` nên chỉ còn khi mạng lỗi / 429. Cần thì đổi cách gọi (proxy, thử lại `reverse` khi chưa có tên) | Chủ dự án 2026-11-26, `LocationSettingsViewModel.updateAutoPlace`, `CurrentLocationUpdater` |
| H1 | Home nhiều trang (> 5) còn khựng lúc dựng trang mới (baseline profile, trang nhẹ hơn khi dựng) | Chủ dự án kiểm Oppo thấy ổn (2026-11-25); làm khi mượn được máy lag để xác nhận nguyên nhân (anh Nam báo còn lag) | `SW-home-swipe-smoothness.md` Task 4 |

## Đã bỏ

| Việc | Quyết định |
|---|---|
| B14: ghép hình nền cài sẵn với preset theme (`wp_ios27_1..3`, `wp_sunset`) | Chủ dự án 2026-11-26: bỏ, vì B19 bỏ luôn màn chọn preset theme |
| B10: kính cho widget Today ngoài 4 widget đầu | Chủ dự án 2026-11-26: giữ nguyên (`GLASS_BUDGET = 4` tính theo thứ tự danh sách, xóa / kéo widget thì widget kế lên có kính) |
| Kính mờ tĩnh kiểu XML cho mọi bề mặt còn lại dưới API 31 | Chủ dự án 2026-11-25: dưới API 31 chỉ mờ tĩnh cho qua, tập trung API 31+ (`AGENTS.md`) |

## Đã xong (gần đây)

| Việc | Plan |
|---|---|
| B3: bố cục lần đầu gom sẵn thư mục theo nhóm (thay bước Onboarding, chủ dự án bỏ) + mục "Gom nhóm ứng dụng" trong Cài đặt → Chung | `AG-app-groups.md` |
| WS: kéo widget đè lên widget khác thì đổi chỗ (anh dvha, kiểm A17 2026-11-25) | `WS-widget-swap.md` |
| B2: Thư viện chỉ tìm app; Spotlight lúc gõ chia section cuộn được (Lối tắt, Danh bạ, Cài đặt, Đề xuất), mọi section thẻ kính thật; "Gần đây" ở ô trống trong thẻ kính; thanh tìm kiếm Today kính thật (chủ dự án đã kiểm) | `SP-spotlight-sections.md` |
| D3 · G2: pager dừng lệch khi kéo icon về trang cuối (chủ dự án kiểm ổn 2026-11-26) | `PG4-home-perf-and-device-checks.md` (K1 mục 1a) |
| Kéo widget từ trang danh sách, "App gần đây" trên Home, bố cục lần đầu (chủ dự án đã kiểm) | `WF-widget-drag-first-run.md` |
| Bảng chọn widget cao tối đa, giữ là kéo ngay, chữ trắng menu, thẻ xác nhận chung chặn chạm (chủ dự án đã kiểm) | `PD-picker-menus-dialogs.md` |
| Đồng bộ tài liệu, mục lục, backlog | `DC-docs-sync.md` |
| Widget Pin tự vẽ kiểu iOS (chủ dự án đã kiểm) | `BW-battery-widget.md` |
| Hộp thoại hết khung mờ thừa; nút "+" / "Xong" của Today dạng kính (chủ dự án đã kiểm) | `DW-dialog-window-glass.md` |
| B1 menu nhấn giữ chặn chạm; widget tự vẽ vuông; font Inter cho widget; tắt nhãn giữ vị trí hàng (chủ dự án đã kiểm) | `PW-menu-block-widget-square-font.md` |
