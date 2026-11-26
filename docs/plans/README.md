# Mục lục plan

> DC (2026-11-25). Việc chưa làm: [BACKLOG.md](BACKLOG.md). Quy trình viết plan: `../Workflow.md`, `../AGENTS.md`.

## Quy ước

- Việc mới hoặc việc nhỏ phát sinh: ghi **một dòng** vào [BACKLOG.md](BACKLOG.md). Chỉ tạo file `plans/<ID>-<slug>.md` khi bắt đầu làm một mục đã duyệt (tránh file plan rác); plan xong thì tick mục backlog và ghi tên plan.
- Mã plan mới không trùng mã đã có (đã trùng một lần: `SW-menu-width-fit` của repo XML và `SW-home-swipe-smoothness`). Kiểm bằng `ls docs/plans` trước khi đặt mã.
- Không di chuyển / đổi tên file plan: hơn 300 đường dẫn `plans/…` trong docs và comment code.

## Repo Compose (từ 2026-11-23)

| Plan | Nội dung | Trạng thái |
|---|---|---|
| [BW](BW-battery-widget.md) | Widget Pin tự vẽ kiểu iOS | Xong 2026-11-26 |
| [DW](DW-dialog-window-glass.md) | Hộp thoại hết khung mờ thừa (cửa sổ ôm vừa thẻ); nút "+" / "Xong" của Today dạng kính | Xong 2026-11-25 |
| [PW](PW-menu-block-widget-square-font.md) | Menu nhấn giữ chặn chạm (B1), widget tự vẽ vuông kiểu iOS, font Inter cho widget, tắt nhãn giữ vị trí hàng | Xong 2026-11-25 |
| [DC](DC-docs-sync.md) | Đồng bộ tài liệu chung, mục lục, backlog | Xong 2026-11-25 |
| [PD](PD-picker-menus-dialogs.md) | Bảng chọn widget cao tối đa, giữ là kéo; chữ trắng menu tối; thẻ xác nhận dùng chung, lớp trên cùng | Xong 2026-11-25 |
| [WF](WF-widget-drag-first-run.md) | Kéo widget từ trang danh sách; "App gần đây" trên Home; bố cục lần đầu có Đồng hồ + Lịch | Xong 2026-11-25 |
| [SW](SW-home-swipe-smoothness.md) | Vuốt trang Home mượt (dựng sẵn trang, kính dock vẽ một lần, log đo) | Xong 2026-11-25 (Task 4 hoãn, xem backlog) |
| [LD](LD-library-drag.md) | Kéo app từ Thư viện ra Home (anh dvha) | Task 4 chờ kiểm tay (backlog) |
| [PG4](PG4-home-perf-and-device-checks.md) · [PG3](PG3-static-frosted-glass.md) · [PG2](PG2-widget-card-drag.md) · [PG1](PG1-parity-batch-1.md) · [PG](PG-parity-gaps.md) | Bù chỗ thiếu so với repo XML, đo hiệu năng, kính mờ tĩnh | Xong 2026-11-23 |
| TF (`TF-test-fixes.md`) | RA "Xóa khỏi Màn hình chính" ≠ ẩn, menu ở Thư viện / Tìm kiếm (anh dvha) | Chưa có trong repo (chờ anh dvha đẩy lên) |

## Chuỗi viết lại UI bằng Compose (2026-11-19 → 2026-11-20, viết trong repo XML rồi chép sang)

[JC](JC-compose-pilot.md) · [JD](JD-compose-design-system.md) · [JH1](JH1-compose-home-static.md) · [JH2](JH2-compose-home-drag.md) · [JH3a](JH3a-compose-home-wiring.md) · [JH3b](JH3b-compose-home-menu-folder.md) · [JH3c](JH3c-compose-home-library.md) · [JH3d](JH3d-compose-home-widgets.md) · [JH4](JH4-compose-home-switchover.md) · [JS](JS-compose-remaining-screens.md) · [JX](JX-compose-cleanup.md)

## Lịch sử repo XML (`E:\SourceTree\IosLauncher`, trước 2026-11-19)

Chép nguyên từ repo XML để tra cứu thiết kế, quyết định, cách đo. **Dòng "Trạng thái" bên trong là của repo XML, không cập nhật** (nhiều file còn ghi "chờ review / chờ merge"); muốn biết một tính năng đã có trên Compose chưa thì đọc code hoặc `../Launcher-Features.md`. Plan chỉ nằm ở repo XML, không chép sang: `FL-first-run-layout` (phần còn lại ở backlog).

[AB](AB-about-section.md) · [AN](AN-polish-animation.md) · [AP](AP-applib-animations.md) · [B1](B1-clock-chooser.md) · [CF](CF-config-refactor.md) · [CG](CG-control-center-glass.md) · [CL](CL-small-fixes.md) · [CP](CP-contacts-phone.md) · [DD](DD-dock-default-apps.md) · [DG](DG-dock-glass-refresh.md) · [DR](DR-docs-reorg.md) · [DS3](DS3-design-system-v3.md) · [EP](EP-empty-page-cleanup.md) · [FP](FP-folder-panel-square.md) · [G2a](G2a-folders-library.md) · [G2b](G2b-icon-styles.md) · [G2c](G2c-clock-calendar-widgets.md) · [G2d](G2d-settings-hub.md) · [G3a](G3a-contacts-search.md) · [G3b](G3b-photo-widget.md) · [G3c](G3c-appwidget-host.md) · [G3d](G3d-control-center.md) · [G3e](G3e-weather-widget.md) · [GF](GF-glass-follow-ups.md) · [GI](GI-glass-icons.md) · [GL](GL-glass-pass.md) · [GP](GP-gap-long-press.md) · [GS](GS-glass-settings.md) · [HB](HB-home-closes-menu.md) · [HC](HC-hero-card-entry.md) · [HT](HT-home-toggles.md) · [IC](IC-icon-disk-cache.md) · [IP](IP-icon-packs.md) · [LB](LB-library-back-swipe.md) · [LC](LC-label-cache.md) · [LG](LG-languages.md) · [LO](LO-library-settings.md) · [LQ](LQ-liquid-lens.md) · [M0](M0-infrastructure.md) · [M1](M1-home-activity.md) · [M2](M2-app-list.md) · [M3](M3-icon-grid.md) · [M4](M4-layout-persistence.md) · [M5](M5-orientation-theme.md) · [M6](M6-glass-layer.md) · [M7](M7-edit-mode.md) · [M8a](M8a-settings.md) · [M8b](M8b-search.md) · [M8c](M8c-search-pill.md) · [M8d](M8d-search-extras.md) · [OB](OB-onboarding.md) · [PB](PB-polish-batch-1.md) · [PL](PL-dots-and-launch.md) · [R1](R1-home-controllers.md) · [RB](RB-release-build.md) · [RN](RN-rename-apps.md) · [RR](RR-rotation-recovery.md) · [RS](RS-settings-reskin.md) · [SM](SM-shortcut-menu-glass.md) · [SW](SW-menu-width-fit.md) · [TL1](TL1-home-polish.md) · [TL2](TL2-icon-shape.md) · [TV](TV-today-view.md) · [WI](WI-wallpaper-images.md) · [WN](WN-weather-notifications.md) · [WP](WP-default-wallpaper.md) · [WU](WU-weather-units.md) · [WX](WX-weather-cities.md)
