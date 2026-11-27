# WR — Làm mới thời tiết: nút làm mới trên widget + chu kỳ "Làm mới sau" (B22), kèm B21 và B11 — Implementation Plan

> Làm theo `docs/AGENTS.md`. Chủ dự án cho làm một mạch (2026-11-27 09:08), mỗi Task một commit; giữ OWM, không đổi API. VM không có Android SDK:
> commit chỉ được rà bằng đọc code. Không có test tự động.

**Bắt đầu:** 2026-11-27 · **Plan duyệt:** 2026-11-27 (chốt trong chat: "theo đề xuất")
**Trạng thái:** Đang làm

**Goal:** Widget thời tiết có nút làm mới nhỏ (icon xoay vòng, góc dưới phải); Cài đặt → Thời tiết có "Làm mới sau" 1 giờ / 3 giờ / 6 giờ / 9 giờ /
12 giờ / 1 ngày (mặc định 1 ngày) thay cho hằng 2 giờ. Kèm sửa B21 (hộp "Đặt làm màn hình chính" chồng lên hộp xin quyền) và B11 (dấu "−" phóng to /
thu nhỏ khi vào / ra edit mode).
**Architecture:** Chu kỳ lưu ở DataStore thời tiết (`weather_refresh_interval`), `WeatherRepositoryImpl` giữ bản đọc nhanh (@Volatile) cho
`dueReason`; `WeatherRepository.refreshNow(placeId)` (làm mới tay, chặn 1 phút / địa điểm) + `refreshing: StateFlow<Set<String>>` cho icon xoay. Nút trên
widget nằm trong `HomeWidgetCell` (Compose, đè lên thẻ vẽ Canvas), gọi qua `WeatherWidgetHub` (như `tappedWidgetId`).
**Spec:** chủ dự án 2026-11-27 08:39 + chốt 08:47 / 09:08 (chat); backlog B22, B21, B11.

## Quyết định đã chốt (chủ dự án)

| # | Quyết định |
|---|---|
| 1 | Chu kỳ "Làm mới sau": 1h, 3h, 6h, 9h, 12h, 1 ngày; mặc định 1 ngày. Áp cho cả thời tiết lẫn cập nhật vị trí im lặng (sửa câu quyền riêng tư × 9) |
| 2 | Làm mới tay: mỗi địa điểm tối đa 1 lần / phút; bấm trong lúc chặn thì icon chỉ xoay một vòng |
| 3 | Bấm nút trên widget dùng "Vị trí hiện tại" (chế độ Tự động): chưa có quyền → luồng xin quyền như bấm widget; có quyền → lấy lại vị trí rồi tải thời tiết |
| 4 | "Dữ liệu cũ" = quá chu kỳ + 1 giờ (1 ngày → 25 giờ) |
| 5 | Icon ~12dp, vùng chạm 32dp, góc dưới phải thẻ; ẩn ở edit mode |
| 6 | Gộp B21, B11 vào lô; giữ OWM |

## Ruling (Claude tự quyết)

| # | Ruling | Vì sao | Sai thì tốn |
|---|---|---|---|
| R1 | 429 thử lại sau 2 giờ cố định (`RATE_LIMIT_RETRY_MS`), không theo chu kỳ | Chu kỳ 1 ngày mà gặp 429 thì chờ 1 ngày là quá lâu; 2 giờ là mức cũ | Đổi một hằng |
| R2 | Làm mới tay vẫn qua `mutex` và vẫn bỏ qua khi khóa API chết (401) | Giữ hợp đồng "một lượt một lúc", không gọi khi khóa hỏng | — |
| R3 | Nút gọi qua `WeatherWidgetHub.onRefreshTapped` (Activity gán), không luồn tham số qua `HomeEditContext` / `TodayActions` | Cùng cách `tappedWidgetId`; Home và Today dùng chung `HomeWidgetCell` | Luồn tham số |
| R4 | Làm mới tay lỗi: không Toast; widget hiện lỗi như hiện tại | Như luồng tự làm mới | Thêm Toast |
| R5 | B21: `PermissionRequester(onSystemUi)` báo ngay trước khi mở hộp quyền hệ thống / trang ứng dụng → Home `defaultPrompt.skipNextResume()`; hộp mặc định không hiện khi `DsDialogHost` còn hộp khác | Hướng sửa đã ghi ở B21 | — |
| R6 | B11: `HomeRemoveBadge(visible)`: vào edit mode phóng từ 0 (lò xo hơi nảy), ra thu về 0 (150 ms); item mới dựng khi đang ở edit mode không chạy hoạt ảnh | Như iOS; nơi gọi giữ badge trong composition để kịp thu nhỏ | Chỉ hoạt ảnh vào |

## Vùng file

`domain/model/weather/WeatherModels.kt` (+ `WeatherRefreshInterval`), `domain/weather/WeatherPreferencesRepository.kt`, `WeatherRepository.kt`;
`data/weather/WeatherPrefsKeys.kt`, `WeatherPreferencesRepositoryImpl.kt`, `WeatherRepositoryImpl.kt`, `CurrentLocationUpdater.kt`;
`ui/settings/WeatherSettingsScreen.kt`, `WeatherSettingsViewModel.kt`; `ui/widget/WeatherWidgetHub.kt`; `ui/home/compose/HomeWidgetCell.kt`;
`res/drawable/ic_widget_refresh.xml`; `ui/home/HomeActivity.kt`; `core/permission/PermissionRequester.kt`; `core/designsystem/compose/DsDialogHost.kt`;
`ui/home/controller/DefaultLauncherPromptController.kt`; `ui/home/compose/HomeEditMode.kt`, `HomeGridPage.kt`, `library/HomeLibrary.kt`, `today/HomeToday.kt`;
`strings.xml` × 9; `docs/config-schema.md`.

## Review Focus

1. Đổi chu kỳ ngắn hơn tuổi dữ liệu → nhịp phút kế tiếp của widget đang hiện tải lại; đổi dài hơn → không gọi mạng.
2. Bấm nút liên tục: tối đa 1 lần gọi mạng / phút / địa điểm; icon vẫn phản hồi.
3. Nút không ăn chạm của thẻ ngoài vùng 32dp (bấm thẻ vẫn mở bảng / xin quyền).
4. Thông báo thời tiết (WN) vẫn theo chu kỳ mới, không lấy vị trí.
5. Dấu "−" ở thư mục, Thư viện, Today, widget: vào/ra edit mode có hoạt ảnh; cuộn sang trang mới lúc đang edit thì hiện ngay (không phóng lại).

---

### Task 1: Chu kỳ làm mới + làm mới tay ở tầng dữ liệu
- [x] `WeatherRefreshInterval` (H1, H3, H6, H9, H12, D1; `millis`; `DEFAULT = D1`; `fromName`), khóa `weather_refresh_interval`, `refreshInterval: Flow` + `setRefreshInterval`.
- [x] `WeatherPolicy`: bỏ `REFRESH_INTERVAL_MS`, `STALE_AFTER_MS`; thêm `RATE_LIMIT_RETRY_MS` (2h, R1), `MANUAL_COOLDOWN_MS` (1 phút), `STALE_GRACE_MS` (1h).
- [x] `WeatherRepositoryImpl`: đọc chu kỳ vào @Volatile; `dueReason` / `stateOf` dùng nó; `refreshNow(placeId)`, `refreshing`. `CurrentLocationUpdater.maybeUpdate(intervalMs)` + `updateNow()`.
- [x] `config-schema.md`. Commit `feat: configurable weather refresh interval and manual refresh`.

### Task 2: Cài đặt → Thời tiết "Làm mới sau"
- [ ] Nhóm "Làm mới": hàng "Làm mới sau" (hộp chọn một), chú thích; chuỗi × 9 (plurals giờ); sửa `wx_loc_privacy` × 9. Commit `feat: weather refresh interval setting`.

### Task 3: Nút làm mới trên widget
- [ ] `ic_widget_refresh.xml`; `HomeWidgetCell` (WEATHER, không edit): icon 12dp / vùng chạm 32dp góc dưới phải thẻ, xoay khi `refreshing` hoặc ít nhất một vòng mỗi lần bấm.
- [ ] `WeatherWidgetHub`: `onRefreshTapped`, `refreshing`, `placeIdOf(itemId)`; `HomeActivity`: xin quyền như bấm widget (QĐ 3) hoặc `refreshNow`. Commit `feat: refresh button on weather widget`.

### Task 4: B21
- [ ] Theo R5. Commit `fix: default-launcher prompt no longer stacks on permission dialogs`.

### Task 5: B11
- [ ] Theo R6 ở 4 nơi gọi. Commit `feat: animate remove badges in and out of edit mode`.

### Task 6: Docs
- [ ] `Launcher-Features.md` (thời tiết), `lessons/widgets.md`, `BACKLOG.md`, `plans/README.md`. Commit `docs: WR weather refresh`.

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-27 | 1 | `WeatherRefreshInterval` + khóa `weather_refresh_interval`; `WeatherPolicy` (bỏ `REFRESH_INTERVAL_MS`, `STALE_AFTER_MS`; thêm `RATE_LIMIT_RETRY_MS`, `MANUAL_COOLDOWN_MS`, `STALE_GRACE_MS`); `WeatherRepositoryImpl` đọc chu kỳ, `refreshNow`, `refreshing`; `CurrentLocationUpdater.maybeUpdate(intervalMs)`, `updateNow()`; `config-schema.md` | Chưa build |
