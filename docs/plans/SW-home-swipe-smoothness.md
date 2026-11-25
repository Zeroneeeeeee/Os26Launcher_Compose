# SW — Vuốt trang Home mượt hơn trên mọi dòng máy — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Phiên claude.ai qua VM **không build được, không có `adb`**: chủ dự án build, cài, chạy script đo và gửi kết quả. **Đo trước rồi mới sửa** (như PG4 G3). Đo bằng `tools/perf/measure-home.ps1` (AGENTS.md "Đo hiệu năng"); "bản release" để đo = build type `perf` (cấu hình release, ký khoá debug), vì bản `release` chưa có khoá ký nên không cài được.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (Task 1 làm trước theo yêu cầu; Task 3 = A + C' chủ dự án duyệt)
**Trạng thái:** Xong 2026-11-25 (Task 1–3, 5; Task 4 để sau theo chủ dự án)
**Nhánh:** `an` · **Máy:** Oppo PKD130 (Android 14), Vsmart Live 4 (Android 11) · **Đổi schema Room:** không

**Goal:** Người dùng báo "vẫn còn lag khi vuốt trang" mà không rõ máy nào, lúc nào. Tìm các cú khựng còn lại (khung > 33 ms) ở những tình huống PG4 chưa đo (vuốt ngay sau khi mở app, vuốt qua nhiều trang liên tiếp, trang có widget của app khác) và giảm chúng trên mọi dòng máy, ưu tiên cách 2 (dựng sẵn trang) theo chủ dự án.
**Spec:** PG4 (`PG4-home-perf-and-device-checks.md`: bản `perf` lướt 2 trang đã nóng ~1 % Janky; bản debug 36 %); app mẫu (chủ dự án thấy 2026-11-25): trước khi hiện lưới Home có một màn "đang tối ưu" kèm thanh %.

## Hiện trạng (đọc code 2026-11-25, nhánh `an`)

| # | Nghi vấn | Bằng chứng (đọc code) | Kiểm bằng |
|---|---|---|---|
| H1 | Trang mới dựng ngay giữa lúc vuốt | `HorizontalPager(beyondViewportPageCount = 1)` (commit `a350f1a`, chủ ý: "nhiều trang thì giật"): vuốt qua nhiều trang thì trang kế (lưới, icon, widget) dựng trong lúc trượt | log `SwPage` trùng lúc với log `SwFrame` (`recentPageComposed`) |
| H2 | Đổi nguồn kính giữa lúc vuốt | Mỗi trang đổi `LocalDsBackdrop` null ↔ `backdrop` khi `onScreen` đổi (trang vừa trượt vào màn) → cả trang dựng lại, `dsGlass` đổi nhánh (tạo node `drawBackdrop`) đúng lúc bắt đầu trượt | log `SwFrame` ở đầu cú vuốt, pha `anim` / `layout` cao |
| H3 | Code Home chưa có trong baseline profile | `app/src/main/baseline-prof.txt` 26 dòng, không dòng nào cho `ui/home/compose`: ở bản release vài cú vuốt đầu chạy code chưa biên dịch trước | kịch bản `cold` (bản `perf`) so với `swipe` |
| H4 | Widget của app khác | `AndroidView` + `AppWidgetHostView` (`HomeAppWidgetCell`, `HostedWidgetViews` giữ view theo id): trang rời composition rồi quay lại thì view gắn và dàn bố cục lại; RemoteViews có thể nặng | trang có widget app khác; `appWidgets=` trong log `SwPage` |
| H5 | Kính thật trên máy API ≥ 31 yếu hơn Oppo | PG4: Oppo +1,3–2,3 ms p95 do kính thật; máy yếu có thể vượt khung | pha `gpu` / `issue` trong `SwFrame`; cờ `noRealGlass` |
| H6 | Bản debug | PG4: Janky 36 % ở debug | so debug / `perf` cùng kịch bản |

## Hướng sửa (theo số đo, Task 3+)

- **Cách 2 (chủ dự án chọn thử trước):** dựng sẵn trang: `beyondViewportPageCount` 2, hoặc dựng hết khi Home ít trang (ví dụ ≤ 5); giữ `onScreen` (H2) hoặc bỏ đổi nguồn kính giữa lúc vuốt. Đổi lại: thêm bộ nhớ và thời gian dựng lúc mở Home.
- **Màn "đang tối ưu" như app mẫu:** lần đầu (hoặc sau khi cài / cập nhật), trước khi hiện lưới: dựng sẵn mọi trang ngoài màn, nạp icon vào bộ đệm, tạo view widget, có thanh %. Chỉ làm nếu cách 2 làm lần mở Home chậm thấy rõ, hoặc cú khựng lần đầu còn.
- **Baseline profile cho Home (H3):** thêm luật `HSPL...ui/home/compose/**` (và `core/glass/compose/**`, `core/designsystem/compose/**`) vào `baseline-prof.txt`; không thêm dependency (dự án không dùng thư viện test / benchmark).
- **Kính thật khi vuốt (H5):** tạm về kính mờ tĩnh trong lúc pager cuộn (Q3 của PG4, đã gác).

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Thứ tự: đo (Task 2) → cách 2 (Task 3) → đo lại → mới tính màn "đang tối ưu", baseline profile, kính khi vuốt? | Có | |
| Q2 | Màn "đang tối ưu": giao diện theo app mẫu hay theo Figma v3 (nếu có màn đó)? Chỉ hỏi khi tới bước đó | — | |

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `core/perf/HomePerfLog.kt` (log khung chậm + dựng trang, chỉ debug / perf) | Tạo | 1 |
| `ui/home/HomeActivity.kt` (gắn / gỡ ở `onResume` / `onPause`) | Sửa | 1 |
| `ui/home/compose/HomeScreen.kt` (ngữ cảnh cuộn; log dựng trang) | Sửa | 1, 3 |
| `tools/perf/measure-home.ps1` (kịch bản `cold`, cột `Slow33` / `SlowLog` / `PagesComposed`, lưu logcat mỗi lượt) | Sửa | 1 |
| Tuỳ kết quả: `HomeScreen.kt`, `baseline-prof.txt`, màn mới | Sửa / Tạo | 3+ |

## Global Constraints

- Log đo chỉ chạy ở bản `debug` / `perf` (`HomePerfLog.enabled`), bản `release` không làm gì.
- Không dependency mới, không chuỗi mới (nếu có màn "đang tối ưu": đủ 9 file `strings.xml`).
- Giữ CRLF; không viết `/*` trong comment Kotlin.

## Review Focus

1. Số đo theo cách PG4: cùng phiên, xen kẽ, lặp cấu hình đầu cuối phiên; thêm số khung > 33 ms.
2. Sau sửa: Home lúc đứng yên như trước; bộ nhớ và thời gian mở Home không tăng thấy rõ.
3. Log đo không lọt vào bản `release`.

---

### Task 1: Log đo
**Bắt buộc kiểm tay:** có (chạy script, xem log).
- [x] Step 1: `HomePerfLog`: `Window.addOnFrameMetricsAvailableListener` (luồng riêng) log khung > 1,5 chu kỳ vsync (tag `SwFrame`: total, input, anim, layout, draw, sync, issue, swap, gpu từ API 31; ngữ cảnh `scrolling`, `page`, `recentPageComposed` trong 300 ms); `pageComposed` (tag `SwPage`: trang, thời gian dựng xấp xỉ, số item / widget / widget app, đang cuộn không).
- [x] Step 2: `HomeActivity`: gắn ở `onResume`, gỡ ở `onPause`. `HomeScreen`: cập nhật ngữ cảnh trong `SideEffect`; log mỗi trang pager dựng lần đầu.
- [x] Step 3: Script: kịch bản `cold`; cột `Slow33` (khung > 33 ms từ HISTOGRAM gfxinfo), `SlowLog` (số dòng `SwFrame`), `PagesComposed`; lưu logcat `SwFrame` / `SwPage` mỗi lượt vào `tools/perf/out/*.log`.

### Task 2: Đo gốc (bản debug, rồi bản `perf`)
**Bắt buộc kiểm tay:** có.
- [x] Step 1: Chuẩn bị Home: dùng **hết số trang thật** (`-Pages` = số trang), ít nhất một trang có widget của app khác.
- [x] Step 2: Bản debug (`installDebug`), Oppo rồi Live 4: `-Scenario cold -WarmupSec 1 -Configs full -Rounds 3` và `-Scenario swipe -Configs full`.
- [x] Step 3: Bản `perf` (`installPerf`), Oppo rồi Live 4: như Step 2, thêm cấu hình `noRealGlass` (Oppo).
- [x] Step 4: Claude đọc số + log, chốt nghi vấn (H1–H6) và cách sửa; chủ dự án duyệt.

### Task 3: A (dựng sẵn trang) + C' (kính dock vẽ một lần)
**Bắt buộc kiểm tay:** có.
- [x] Step 1 (A): `HorizontalPager(beyondViewportPageCount = prebuiltPages(pages.size))`: dựng sẵn mọi trang khi Home ≤ 20 trang, quá thì 2 trang mỗi bên. `LocalDsBackdrop` thành `compositionLocalOf`: trang trượt vào màn đổi nguồn kính (`onScreen`) chỉ dựng lại bề mặt kính, không dựng lại cả trang.
- [x] Step 2 (C', chủ dự án 2026-11-25): `CachedGlass` (`core/glass/compose/DsGlassCache.kt`): kính thật của dock vẽ sống vài khung, chụp lớp thành ảnh (`GraphicsLayer.toImageBitmap`) rồi chỉ vẽ ảnh; chụp lại khi đổi ảnh nền (`glassKey = state.backdrop`), thông số kính, cỡ dock, nguồn nền; vẽ sống khi Home đang trượt theo Today / Thư viện (dock dịch so với ảnh nền). Kính đã là Box riêng, tách khỏi lớp icon. Dưới API 31 vẽ thẳng `dsGlass` (kính mờ tĩnh).
  - **Giới hạn (chủ dự án chấp nhận):** hình nền động (video / GIF) thì kính dock đứng ở khung lúc chụp; hiện app chỉ có ảnh nền tĩnh, hình nền hệ thống không lấy mẫu được.
- [x] Step 3: Log `SwDock` (mỗi lần kính dock vẽ sống, mỗi lần chụp); script thêm cột `DockLive`. Công tắc đo mới (bản `perf`): `noPrebuild` (về 1 trang mỗi bên), `noGlassCache` (kính dock sống như trước).
- [x] Step 3b: Trang dựng sẵn nằm ngoài màn không vẽ (chỉ vẽ trang đang hiện một phần, `onScreen` derivedStateOf đọc trong pha vẽ); công tắc `noOffscreenSkip`.
- [x] Step 3c: Dựng sẵn hết chỉ khi Home ≤ 5 trang (`PREBUILD_ALL_MAX`), nhiều hơn thì 1 trang mỗi bên.
- [x] Step 4: Đo lại (bản `perf`, Oppo rồi Live 4; thêm một widget của app khác vào một trang): `swipe` và `cold` với `-Configs full,noPrebuild,noGlassCache,noPrebuild+noGlassCache`; kiểm tay: đổi ảnh nền (dock đổi theo), kéo Today / Thư viện (kính dock đúng chỗ khi trượt và khi dừng), mở thư mục, edit mode (icon dock rung, kính đứng yên), đổi thông số kính.

### Task 4: Việc tiếp theo theo số đo (baseline profile, kính khi vuốt, màn "đang tối ưu") — **ĐỂ SAU** (chủ dự án 2026-11-25: đóng plan)
- Ghi lại cho lần sau: Home nhiều trang (> 5) còn khựng lúc dựng trang mới (Oppo 15 trang: dựng một trang 9–11 ms, màn 120 Hz có 8,3 ms / khung). Hướng: làm trang nhẹ hơn khi dựng và khi đặt chỗ (bỏ `onGloballyPositioned` theo từng icon, tắt kính mờ ở trang ngoài màn), baseline profile cho `ui/home/compose`, rồi thử lại dựng sẵn cho Home nhiều trang; màn "đang tối ưu" như app mẫu chỉ khi vẫn còn khựng.

### Task 5: Docs
- [x] Step 1: Số đo, bài học vào `lessons/startup-performance.md` / `glass.md`; `README.md` số mục.

---

## Kết quả đo
| Máy | Bản | Kịch bản | Cấu hình | Lượt | Janky % | p50 | p95 | p99 | >33 ms | Log chậm | Ghi chú |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Oppo PKD130 (15 trang, 120 Hz) | debug | cold | full | TB 3 vòng | 55,8* | 350* | 554* | 617* | 173* | — | *vòng 2 hỏng (4 khung); 2 vòng còn lại ~33 %, ~257 khung >33 ms |
| Oppo PKD130 | debug | swipe 15 trang | full | TB 3 vòng | 13,07 | 17 | 51,3 | 150 | 892,7 | — | |
| Oppo PKD130 | perf | cold | full | TB 3 vòng | 2,2 | 9 | 17 | 28 | 6,7 | — | |
| Oppo PKD130 | perf | cold | noRealGlass | TB 3 vòng | 1,25 | 8,3 | 14,3 | 22,3 | 1 | — | |
| Oppo PKD130 | perf | swipe 15 trang | full | TB 3 vòng | 2,0 | 9 | 16,3 | 25,3 | 19,3 | — | ~10 700 khung / lượt |
| Oppo PKD130 | perf | swipe 15 trang | noRealGlass | TB 3 vòng | 1,27 | 8,7 | 15 | 21,3 | 5 | — | |
| Vsmart Live 4 (4 trang, 60 Hz) | debug | cold | full | TB 3 vòng | 21,62 | 9,7 | 29,3 | 102,7 | 16 | — | |
| Vsmart Live 4 | debug | swipe 4 trang | full | TB 3 vòng | 5,29 | 8,3 | 17 | 36,7 | 26,7 | — | |
| Vsmart Live 4 | perf | cold | full | TB 3 vòng | 5,06 | 5 | 13 | 19 | 0,3 | — | vòng 1 và lặp cuối ~12 %, giữa 1–2 %; 5 khung ~82 ms không gần lúc dựng trang |
| Vsmart Live 4 | perf | cold | noRealGlass | TB 3 vòng | 1,48 | 5 | 10 | 18,3 | 0 | — | API 30 không có kính thật: khác biệt là nhiễu |
| Vsmart Live 4 | perf | swipe 4 trang | full | TB 3 vòng | 0,94 | 5,7 | 10,3 | 15,3 | 0 | — | |
| Vsmart Live 4 | perf | swipe 4 trang | noRealGlass | TB 3 vòng | 1,06 | 5,7 | 10,3 | 16,3 | 0 | — | |
| Oppo PKD130 (15 trang, 2 widget) | perf | swipe | full (A dựng sẵn + C' ảnh chụp kính) | TB 3 vòng | 3,56 | 11 | 26,7 | 31 | 62,7 | — | DockLive 0; lặp cuối 3,56 %; 31,9–35,9 °C |
| Oppo PKD130 | perf | swipe | noPrebuild (chỉ C') | TB 3 vòng | 2,54 | 9 | 21 | 26 | 37 | — | DockLive 0 |
| Oppo PKD130 | perf | swipe | noGlassCache (chỉ A) | TB 3 vòng | 3,73 | 11,3 | 27,3 | 32 | 74,3 | — | DockLive ~8 700 |
| Oppo PKD130 | perf | swipe | noPrebuild+noGlassCache (như trước SW) | TB 3 vòng | 2,71 | 9 | 22 | 26,7 | 38,3 | — | DockLive ~8 900 |
| Oppo PKD130 | perf | cold | full | TB 3 vòng | 3,74 | 12 | 28 | 32 | 16,3 | — | |
| Oppo PKD130 | perf | cold | noPrebuild | TB 3 vòng | 2,58 | 9 | 21 | 27,3 | 10,7 | — | |
| Vsmart Live 4 (4 trang, 2 widget) | perf | swipe | full | TB 3 vòng | 0,22 | 6 | 10,7 | 13 | 0 | — | API 30: không có kính thật, không chụp |
| Vsmart Live 4 | perf | swipe | noPrebuild | TB 3 vòng | 0,96 | 7 | 12 | 16 | 0 | — | |
| Vsmart Live 4 | perf | swipe | noGlassCache | TB 3 vòng | 0,16 | 7 | 11 | 13 | 0 | — | |
| Vsmart Live 4 | perf | swipe | noPrebuild+noGlassCache | TB 3 vòng | 0,99 | 7 | 12 | 16,3 | 0 | — | |
| Vsmart Live 4 | perf | cold | full | TB 3 vòng | 0,28 | 7 | 12 | 14 | 0 | — | |
| Vsmart Live 4 | perf | cold | noPrebuild | TB 3 vòng | 1,61 | 7 | 13 | 20 | 0,7 | — | |
| Oppo PKD130 (15 trang) | perf | swipe | full (3b: dựng sẵn hết, không vẽ trang ngoài màn) | TB 3 vòng | 2,99 | 10 | 25 | 30 | 54 | — | |
| Oppo PKD130 | perf | swipe | noOffscreenSkip | TB 3 vòng | 3,54 | 11 | 26,3 | 31 | 54 | — | |
| Oppo PKD130 | perf | swipe | noPrebuild | TB 3 vòng | 2,35 | 9 | 20,3 | 25,7 | 34,7 | — | |
| Oppo PKD130 | perf | cold | full (3b) | TB 3 vòng | 2,95 | 10 | 24,7 | 30,7 | 10 | — | |
| Oppo PKD130 | perf | cold | noPrebuild | TB 3 vòng | 2,66 | 9 | 21 | 26,7 | 9 | — | |
| Vsmart Live 4 (4 trang) | perf | swipe | full (3b) | TB 3 vòng | 0,16 | 6 | 10 | 13,3 | 0,3 | — | |
| Vsmart Live 4 | perf | swipe | noOffscreenSkip | TB 3 vòng | 0,11 | 7 | 10,7 | 13 | 0 | — | |
| Vsmart Live 4 | perf | swipe | noPrebuild | TB 3 vòng | 1,05 | 6 | 12 | 16 | 0 | — | |
| Vsmart Live 4 | perf | cold | full (3b) | TB 3 vòng | 0,56 | 6 | 11,3 | 15,3 | 0 | — | lượt `cold` đầu bỏ (chạm nhầm) |
| Vsmart Live 4 | perf | cold | noPrebuild | TB 3 vòng | 1,6 | 6 | 12,3 | 19 | 0 | — | |
| Oppo PKD130 (15 trang) | perf | swipe | full (3c: >5 trang → 1 mỗi bên) | TB 2 vòng | 2,38 | 9 | 20,5 | 25,5 | 26,5 | — | |
| Oppo PKD130 | perf | swipe | noPrebuild | TB 2 vòng | 2,37 | 9 | 20,5 | 25,5 | 35,5 | — | |
| Vsmart Live 4 (4 trang) | perf | swipe | full (3c) | TB 2 vòng | 9,88 | 9 | 19,5 | 23,5 | 2,5 | — | **lệch: máy đang biên dịch nền sau khi cài**, bỏ |
| Vsmart Live 4 | perf | swipe | full (3c, sau `compile -f`) | TB 2 vòng | 0,02 | 5 | 8 | 11,5 | 0 | — | 27–29 °C |
| Vsmart Live 4 | perf | swipe | noPrebuild (sau `compile -f`) | TB 2 vòng | 0,79 | 5 | 12 | 14,5 | 0 | — | |

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
- Đo: `tools/perf/measure-home.ps1` bản `perf` (sau `compile -f`), `swipe` và `cold`, xen kẽ cấu hình, lặp cấu hình đầu cuối phiên; Oppo PKD130 (15 trang) và Vsmart Live 4 (4 trang), mỗi máy có widget tự vẽ + widget app khác; công tắc `noPrebuild`, `noGlassCache`, `noOffscreenSkip` để so với trước.
- Kiểm tay (Oppo): đổi ảnh nền (kính dock đổi theo), kéo Today / Thư viện (kính dock đúng chỗ khi trượt và khi dừng), mở thư mục, edit mode (icon dock rung, kính đứng yên), đổi thông số kính, trang trượt vào không nháy trống.

**Kết quả kiểm chứng (máy, ngày):** Đạt — chủ dự án đo và kiểm tay trên Oppo PKD130 và Vsmart Live 4, 2026-11-25. Live 4 vuốt 1,05 → 0,02 % Janky, p95 12 → 8 ms; Oppo 2,71 → 2,35–2,38 %; kiểm tay không thấy vấn đề.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-25 | — | Chủ dự án: "vẫn còn lag khi vuốt trên một số máy", không rõ máy / lúc nào; app mẫu có màn "đang tối ưu" + thanh % trước lưới Home; thử cách 2 trước; đặt log đo, đo bản debug rồi bản release. Đọc `HomeScreen` (pager `beyondViewportPageCount = 1` từ `a350f1a`, đổi `LocalDsBackdrop` theo `onScreen`), `HomeIcons` (nạp icon bất đồng bộ + bộ đệm), `HomeAppWidgetCell` / `HostedWidgetViews`, `baseline-prof.txt`; viết plan | — |
| 2026-11-25 | 1 | `core/perf/HomePerfLog.kt`; `HomeActivity` gắn / gỡ; `HomeScreen` ngữ cảnh + log dựng trang; script: `cold`, `Slow33`, `SlowLog`, `PagesComposed`, logcat mỗi lượt | Chưa build |
| 2026-11-25 | 2 | Chủ dự án build, đo debug + `perf` trên Oppo (15 trang) và Live 4 (4 trang). Claude đọc 44 file log: **khung > 33 ms ở bản `perf` gần như luôn trùng lúc vừa dựng một trang** (Oppo swipe full 95/99, noRealGlass 22/22, cold 39/40). Dựng một trang (24 icon) ở `perf`: Oppo trung vị 8,6–11 ms, tối đa 41–50 ms (Oppo chạy 120 Hz, khung 8,3 ms); Live 4 trung vị ~5 ms (60 Hz, khung 16,7 ms) → Live 4 không khựng. Kính thật (dock) làm số khung > 33 ms trên Oppo gấp ~4 lần (19,3 so với 5 / lượt swipe); pha `issue` của khung chậm 4,2 so với 2,0–2,8 ms. Bản debug: dựng trang trung vị 35 ms (Oppo), 893 khung > 33 ms / lượt. Kết luận: **H1 đúng (nguyên nhân chính), H5 góp phần trên máy API ≥ 31, H6 với bản debug**; trang chưa có widget app khác nên H4 chưa đo | Chờ duyệt cách sửa |
| 2026-11-25 | 3 | Chủ dự án hỏi C có ảnh hưởng kính máy đời cao không → đề xuất C' (vẽ kính dock một lần, giữ kính thật); chủ dự án: hình nền động là giới hạn chấp nhận được, kính dock vẽ một lần, chỉ vẽ lại khi đổi hình nền; duyệt A + C', "ghi vào plan và làm luôn". Đọc thêm: Home (trang, pill, dock) trượt `translationX` theo Today / Thư viện còn ảnh nền đứng yên → kính dock phải vẽ sống khi trượt; `LocalDsBackdrop` là `staticCompositionLocalOf` nên đổi theo `onScreen` dựng lại cả trang | — |
| 2026-11-25 | 3 | A: `prebuiltPages` (≤ 20 trang: dựng hết; quá: 2 mỗi bên), `LocalDsBackdrop` → `compositionLocalOf`. C': `CachedGlass` + `HomeDock(glassKey, glassLive)`, `dockGlassLive` = derivedStateOf(tiến độ Thư viện / Today > 0). Log `SwDock`, công tắc `noPrebuild` / `noGlassCache`, script cột `DockLive` | Chưa build |
| 2026-11-25 | 3 | Chủ dự án build, đo song song (`-Serial`) Oppo (15 trang) + Live 4 (4 trang), mỗi máy có 2 widget 2×2 (1 tự vẽ, 1 app khác). **C' xác nhận:** không ảnh chụp thì kính dock vẽ sống ~8 700 lần / lượt (gần như mỗi khung), có ảnh chụp: 0; Oppo khung > 33 ms 74 → 63, Janky 3,73 → 3,56 % (dựng sẵn), 38 → 37 (không dựng sẵn). **A: tốt ở Live 4, xấu ở Oppo 15 trang:** Live 4 Janky 0,96 → 0,22 %, p99 16 → 13 ms, cold 1,61 → 0,28 %; Oppo dựng sẵn Janky 2,54 → 3,56 %, p50 9 → 11 ms, khung > 33 ms 37 → 63. Log Oppo: dựng sẵn thì khung chậm không còn trùng lúc dựng trang (0/384) nhưng pha `draw` 1,0 → 2,2 ms, `issue` ~10,7 ms: **trang ngoài màn vẫn được vẽ mỗi khung**. → Task 3b: trang ngoài màn không vẽ (`drawWithContent { if (onScreen) drawContent() }`), công tắc `noOffscreenSkip` | Chưa build |
| 2026-11-25 | 3 | Đo 3b (song song, `-Serial`; Live 4: bỏ lượt `cold` đầu do chạm nhầm). Live 4 (4 trang): swipe dựng sẵn 0,16 % (không vẽ trang ngoài màn) / 0,11 % (vẽ) so 1,05 % (không dựng sẵn); cold 0,56 so 1,6 %. Oppo (15 trang): swipe dựng sẵn 2,99 % / 54 khung > 33 ms (không vẽ trang ngoài màn) so 3,54 % / 54 (vẽ) so **2,35 % / 34,7 (không dựng sẵn)**; cold 2,95 so 2,66 %. → không vẽ trang ngoài màn đỡ một phần nhưng dựng sẵn nhiều trang vẫn tốn mỗi khung (pager đặt chỗ mọi trang đã dựng). **Task 3c:** dựng sẵn hết chỉ khi ≤ 5 trang, nhiều hơn thì 1 mỗi bên. Không dựng sẵn + ảnh chụp kính dock (Oppo 2,35 % / 34,7) tốt hơn như trước SW (2,71 % / 38,3, cùng ngày) | Chưa build |
| 2026-11-25 | 3 | Chủ dự án build 3c, kiểm tay đạt (đổi ảnh nền, Today / Thư viện, edit mode, thông số kính, trang trượt vào không nháy). Đo: Oppo full 2,38 % = noPrebuild 2,37 % (đúng kỳ vọng: 15 trang > 5). Live 4 **mọi cấu hình xấu đi** so với lượt trước (full 9,88 %, noPrebuild 6,2 % so 0,16 / 1,05 %; p50 9 so 6 ms) dù code của Live 4 (4 trang) giống 3b: nghi do máy (vừa cài, ART đang biên dịch nền theo `.dm`), chưa kết luận | Chờ đo lại Live 4 |
| 2026-11-25 | 3 | Live 4 đo lại sau khi ép biên dịch (`cmd package compile -m speed-profile -f`): full 0,02 %, p95 8, p99 11,5 ms so noPrebuild 0,79 %, p95 12, p99 14,5 ms → lượt trước lệch do máy đang biên dịch nền sau khi cài. Script thêm `-Compile` (mặc định bật, bỏ qua bản debug). **Task 3 xong:** Live 4 (≤ 5 trang) mượt hơn rõ; Oppo (15 trang) bằng trước dựng sẵn, cộng phần lợi của ảnh chụp kính dock (2,35–2,38 % so 2,71 % như trước SW) | Xong Task 3 |
| 2026-11-25 | 4, 5 | Chủ dự án đóng plan, Task 4 để sau. Bài học vào `lessons/`, `AGENTS.md` | Xong |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Số đo đúng cách PG4 + khung > 33 ms | Cùng phiên, xen kẽ, lặp cuối; cột `Slow33`; lượt lệch (Live 4 đang biên dịch nền, chạm nhầm) đánh dấu và đo lại | Đạt |
| 2 | Home đứng yên như trước; bộ nhớ / mở Home không tăng rõ | Kiểm tay đạt; dựng sẵn chỉ khi ≤ 5 trang; ảnh chụp kính dock ~1 MB | Đạt (bộ nhớ chưa đo bằng công cụ) |
| 3 | Log đo không lọt vào `release` | `HomePerfLog.enabled = BuildConfig.DEBUG || BuildConfig.PERF_SWITCHES`; `PerfSwitches` rỗng ngoài `perf` | Đạt (đọc code) |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `lessons/startup-performance.md`: bài học SW; `lessons/glass.md`: ảnh chụp kính dock; `lessons/README.md`: số mục.
- `AGENTS.md`: ghi thêm `-Serial`, `-Compile`, log `SwFrame` / `SwPage` / `SwDock` vào dòng "Đo hiệu năng".
