# WU — Đơn vị nhiệt độ °C/°F (màn 1.4 Thời tiết, một phần) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không cần: tính năng không đụng kính). Lệnh viết cho CMD/PowerShell. Máy thật dùng chung với việc khác của chủ dự án: xác nhận máy rảnh trước mỗi lần cài hoặc chạy script.

**Nhánh:** `feature/wu-weather-units` · **Worktree:** `E:\SourceTree\IosLauncher-wt\WU-weather-units` · **PR:** #50 · **Bắt đầu:** 2026-11-12 · **Plan duyệt:** 2026-11-13 (điều phối)
**Trạng thái:** Chờ review toàn nhánh (điều phối rebase và chuyển PR sang Ready); còn mục chưa kiểm cho bài kiểm tay của chủ dự án
**Khóa:** không (không Room, không khóa schema)

**Goal:** Người dùng chọn °C hoặc °F ở Cài đặt → Thời tiết. Widget thời tiết (2x2, 4x2, cả bản xem trước trong bảng chọn widget) đổi đơn vị ngay khi quay về Home, không gọi mạng thêm. Mặc định °C (đúng hành vi hiện tại).

**Architecture:** Dữ liệu thời tiết luôn lưu và truyền ở **°C chính xác (Double)**, đơn vị chỉ là cách hiển thị: một enum `TemperatureUnit` ở domain có hàm `display(tempC: Double): Int` làm tròn ở bước cuối. Lựa chọn lưu trong DataStore qua một repository riêng của thời tiết (`WeatherPreferencesRepository`, cùng mẫu `SearchPreferencesRepository`). Màn `WeatherSettingsScreen` (một hàng "Đơn vị nhiệt độ", hộp thoại chọn một) đăng ký vào ô 1.4 của bảng màn G2d. `WeatherWidgetHub` thu flow đơn vị và gọi `WidgetTicker.tickNow()` khi đổi, như đang làm với `WeatherRepository.state`.

**Tech Stack:** không dependency mới. Kotlin, DataStore Preferences (đã có), Hilt.
**Spec:** màn 1.4 trong `docs/Figma-Draft.md` (mục 3, 4, 6; quyết định FD2, FD6); nền kỹ thuật G3e (`plans/G3e-weather-widget.md`, `knowledgements/G3e-weather-widget.md`); công thức thêm màn/hàng ở `plans/G2d-settings-hub.md`.

## Hiện trạng đã đọc (đọc code, 2026-11-12, `main` @ b76f4bb)

- Gọi OWM với `units=metric` ở **hai** chỗ: `data/weather/OpenWeatherMapProvider.fetch` (`data/2.5/weather` và `data/2.5/forecast`). Mỗi lần làm mới = 2 lệnh gọi.
- `OwmParser.snapshot` **làm tròn ngay lúc parse** (`roundToInt`) và lưu `Int`: `WeatherSnapshot.tempC/highC/lowC` và `HourlyPoint.tempC` đều là `Int`. `no_backup/weather.json` lưu các số nguyên này (`WeatherStore`, khóa `t`, `hi`, `lo`, `h[].t`). Cao/thấp là max/min của số đã làm tròn.
- Chỗ nhiệt độ xuất hiện (rà toàn `app/src`, không có "cảm giác như", gió, độ ẩm, áp suất, `temp_min/temp_max` nào được parse):
  1. nhiệt độ hiện tại: `WeatherRenderer.prepareData` → `temp = "${s.tempC}°"` (2x2 và 4x2);
  2. cao/thấp: chuỗi `weather_hi_lo_short` ("24h ↑%1$d° ↓%2$d°", 2x2) và `weather_hi_lo_long` ("24 giờ tới: cao %1$d°, thấp %2$d°", 4x2);
  3. dải 4 mốc theo giờ của 4x2: `hourTemps[i] = "${p.tempC}°"`;
  4. mô tả truy cập (`contentDescription`): `weather_description` ("%2$d độ ... cao %4$d độ, thấp %5$d độ");
  5. dữ liệu mẫu cho bản xem trước trong bảng chọn widget: `WeatherWidgetHub.sample()` (28°, 31°/24°, 29/27/25/24).
- **Bảng Thời tiết (G3e, `ui/weather/WeatherSheetController`) hiện KHÔNG hiện nhiệt độ nào**: chỉ tên thành phố đang dùng, ô tìm, nút, giờ cập nhật và ghi chú "cao/thấp 24 giờ tới". Việc bàn giao nói bảng có hiện thời tiết; đọc code thì không. Nên bảng không cần đổi trong WU. Card "hiện tại + theo giờ" của màn 1.4 trong bản nháp thuộc WX, không thuộc WU.
- Làm mới widget: `WeatherWidgetHub.init` thu `repo.state` rồi gọi `WidgetTicker.tickNow()`; `tickNow` chỉ làm gì khi có widget đang hiện; mọi widget đang hiện gọi lại `prepare()` (dựng chuỗi) rồi vẽ. Khi widget hiện lại sau lúc Home bị che (vd quay về từ Cài đặt), `WidgetTicker.register` tự gọi `tickWidget()` ngay. `docs/lessons/widgets.md` và G3e: `prepare()` dựng chuỗi, `draw()` không cấp phát; cỡ chữ suy từ cạnh ngắn và `fitWidth` co chữ cho vừa.
- Cài đặt: màn mới = một file `XxxSettingsScreen.kt` + thay MỘT dòng trong `SettingsScreens` (ô `// 1.4 WEATHER (WX)`); mục "Thời tiết" ở trang chủ (`SettingsScreenId.WEATHER`, chuỗi `settings_hub_weather`) tự bật khi màn đăng ký. Mẫu màn có ViewModel riêng và flow riêng: `SearchSettingsScreen` + `SearchSettingsViewModel` + `SearchPreferencesRepository` + `SearchPrefsKeys` (khóa tách khỏi `SettingsKeys`).

## Vì sao chọn thiết kế này

**Vấn đề cần giải.** (1) Đổi °C/°F không được làm tốn lệnh gọi OWM: khóa nằm trong APK (C15, hoãn) nên mọi lệnh gọi thừa là rủi ro và hạn mức, và FD6 dặn "giới hạn số lượt gọi". (2) Số hiển thị phải đúng: số lưu hiện là °C **đã làm tròn**, đổi sang °F từ số đó có thể lệch 1°F (xem bảng dưới). (3) Cache `weather.json` và dữ liệu đang có phải dùng được ngay sau khi đổi, cả khi offline. (4) Widget phải đổi ngay, không chờ nhịp 30 phút. (5) Không chạm file nóng độc quyền, không đổi schema Room, ít đụng file Cài đặt dùng chung vì HT, AB, RN cùng sửa chúng.

**Các phương án đã xét (đơn vị lưu ở đâu, đổi ở đâu).**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Đổi lúc hiển thị, từ số `Int` °C đang lưu | Thêm hàm đổi, giữ nguyên dữ liệu | Ít code nhất, không đổi định dạng file | Sai số: °C đã làm tròn ±0.5 → °F lệch tới ±0.9, hiển thị có thể lệch 1°F so với thực tế (vd 24.4°C lưu thành 24 → 75°F, đúng là 75.9 → 76°F; −0.4°C lưu thành 0 → 32°F, đúng là 31.3 → 31°F). Cao/thấp tính trên số đã tròn nên cũng lệch | Không |
| **B. Đổi lúc hiển thị, từ °C chính xác (`Double`)** | Parser không làm tròn, lưu `Double`; làm tròn một lần ở `TemperatureUnit.display` | Đúng cho cả hai đơn vị; không gọi mạng; cache cũ (số nguyên) vẫn đọc được; đổi tức thì và hoạt động offline; °C hiển thị y như cũ (`round` của max = max của `round`) | Sửa kiểu `Int` → `Double` ở mô hình, parser, store, renderer (một Task); file cache cũ chỉ chính xác ở mức số nguyên cho tới lần làm mới kế (tối đa 30 phút hoặc khi mở Home) | **Chọn** |
| C. Gọi OWM `units=imperial` khi chọn °F | Provider nhận đơn vị, `fetch` đổi tham số | Số do chính OWM tính | Mỗi lần đổi tốn 2 lệnh gọi (mạng, hạn mức, khóa lộ), không có mạng thì không đổi được; cache phải gắn nhãn đơn vị hoặc bỏ khi đổi (hoặc hiện sai đơn vị); `select()` không bị chặn tần suất nên người bấm qua lại sẽ đốt lệnh; phải đổi hợp đồng `WeatherProvider`/`WeatherRepository` và chặn D2 | Loại |
| D. Lưu cả hai (gọi cả metric và imperial mỗi lần) | Gấp đôi lệnh gọi, đổi tức thì | Số "chính chủ" | Gấp đôi hạn mức mọi lúc cho một tùy chọn ít dùng; ngược FD6 | Loại |

Nơi lưu lựa chọn:

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| 1. Thêm vào `LauncherSettings`/`SettingsKeys`/`SettingsRepositoryImpl`/`SettingsUiState`/`SettingsViewModel` | Theo đúng hướng "thêm trường cuối" của G2d | Đụng 5 file dùng chung mà HT cũng thêm vào, nên thêm rủi ro xung đột rebase; widget (`WeatherWidgetHub`, `object` ngoài Hilt) phải thu cả `LauncherSettings` (mọi khóa) rồi `distinctUntilChanged` | Không |
| **2. Repository riêng `WeatherPreferencesRepository` + `WeatherPrefsKeys` trên cùng `DataStore<Preferences>`** | Cùng mẫu `SearchPreferencesRepository`/`SearchPrefsKeys` (M8d); không đụng 5 file trên; flow đơn vị riêng, nhỏ, `distinctUntilChanged`; WX thêm thành phố/thông báo vào đúng chỗ này | Một interface + một lớp + một khóa mới | **Chọn** |
| 3. Lưu vào `weather.json` | Cùng chỗ với dữ liệu thời tiết | Sai bản chất: file `noBackupFilesDir` chứa dữ liệu dẫn xuất, lựa chọn của người dùng nên ở DataStore; ghi file nguyên tử mỗi lần đổi; nạp không đồng bộ chung với cache | Loại |

**Vì sao chọn B + 2.** B là phương án duy nhất vừa đúng số (mục "Hiện trạng" và bảng ví dụ ở Task 1) vừa không tốn lệnh gọi nào (đọc code: `fetch` là 2 lệnh/lần; C tốn thêm 2 lệnh mỗi lần đổi, và C15 đang hoãn nên khóa càng ít dùng càng tốt). Phương án 2 theo đúng tiền lệ đã có và giữ vùng file nhỏ trong đợt song song bốn nhánh.

**Đánh đổi chấp nhận.** (a) Số °F tính, không phải OWM trả; công thức chuẩn `°F = °C × 9/5 + 32` nên chấp nhận được. (b) Cache cũ của người dùng nâng cấp có số nguyên: °F có thể lệch 1 ở tối đa một chu kỳ làm mới. (c) Đơn vị ghi là tên enum ("CELSIUS"/"FAHRENHEIT"), thêm giá trị sau (vd "AUTO") không cần di trú.

**Đơn vị mặc định và theo hệ thống.** Mặc định `CELSIUS`: khóa vắng = °C, giữ nguyên hành vi hiện tại và khớp bản nháp (hai nút °C | °F, không có "Theo hệ thống"). Có thể theo đơn vị của hệ thống nhưng cần chủ dự án quyết (Câu hỏi 1): Android 14 (API 34) có "Tùy chọn khu vực → Nhiệt độ" ở Cài đặt hệ thống; đọc được qua `androidx.core.text.util.LocalePreferences.getTemperatureUnit()` (suy luận chưa kiểm chứng: lớp này có trong androidx.core từ bản 1.10 theo trí nhớ, repo đang dùng core-ktx 1.19.1; phải đọc tài liệu và kiểm trên máy khi làm, không đoán), trên bản cũ hơn nó chỉ suy ra từ vùng của ngôn ngữ (suy luận chưa kiểm chứng). minSdk = 26 nên máy thử Live 4 (Android cũ) sẽ không có thiết lập hệ thống đó. WU không làm "Theo hệ thống".

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| WU1 | Dữ liệu lưu °C chính xác (`Double`), đổi đơn vị lúc hiển thị, không gọi mạng khi đổi | Đề xuất của agent, chờ duyệt |
| WU2 | Mặc định °C, hai lựa chọn °C/°F (không "Theo hệ thống") | Đề xuất, xem Câu hỏi 1 |
| WU3 | Lưu bằng `WeatherPreferencesRepository` + `WeatherPrefsKeys`, không sửa `SettingsKeys`/`SettingsRepositoryImpl`/`LauncherSettings`/`SettingsUiState`/`SettingsViewModel` | Lệch nhẹ so với lời dặn "append khóa vào SettingsKeys" của brief: cố ý, xem phương án 2 |
| WU4 | Giữ kiểu hàng Cài đặt hiện tại (FD2): một `SettingsRow.Value` mở hộp thoại chọn một, không dựng nút phân đoạn của bản nháp | FD2 |
| WU5 | Widget hiện đơn vị rõ ràng kèm chữ: "24°C", "75°F", ở mọi chỗ có nhiệt độ (hiện tại, cao/thấp, 4 mốc theo giờ); hàng Cài đặt và mô tả truy cập cũng có đơn vị. Phải kiểm độ rộng ở ca xấu nhất: "-40°F", "104°F", cao/thấp 3 chữ số | Chủ dự án (Q2), qua điều phối 2026-11-13 |
| WU6 | Q1: mặc định °C, chỉ °C/°F, "Theo hệ thống" để sau. Q3: màn Thời tiết chỉ có hàng đơn vị tới khi WX merge. Kho lưu riêng và sửa tại chỗ `weather_description` được chấp nhận | Điều phối, 2026-11-13 |

## Ngoài phạm vi / ghi để sau

- Nhiều địa điểm, thông báo, trạng thái quyền vị trí (1.5): nhánh WX. WX thêm hàng vào `WeatherSettingsScreen` do WU tạo; **không** cần sửa `SettingsScreens` nữa.
- Card "hiện tại + theo giờ" ở đầu màn 1.4 của bản nháp: WX.
- "Theo hệ thống" cho đơn vị; tốc độ gió, "cảm giác như" (chưa có trong app, không parse).
- Hiện đơn vị đang chọn ngay ở trang chủ Cài đặt (cần sửa `SettingsHubScreen`; brief cấm sửa bảng trang chủ cho mục bản nháp).
- Việc làm mới ngay khi đang ở split-screen với Cài đặt: đã được `tickNow` phủ về lý thuyết, chỉ kiểm nếu tiện (Task 3, bước d).

## Vùng file

Đường dẫn Kotlin tính từ `app/src/main/java/gambi/global/os26launcher/`. Không file nào thuộc nhóm độc quyền (`HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `LayoutReconciler`, `data/layout/db/*`, `core/glass/*`, `DragHelper`, `DragDropTarget`).

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/weather/TemperatureUnit.kt` | Tạo (enum + `display` + `fromName`) | không |
| `domain/model/weather/WeatherModels.kt` | Sửa (`tempC/highC/lowC` Int → Double, KDoc) | không |
| `data/weather/OwmParser.kt` | Sửa (bỏ `roundToInt`) | không |
| `data/weather/WeatherStore.kt` | Sửa (`getDouble`, ghi số thực; `VERSION` giữ 1 vì đọc được cả số nguyên cũ) | không |
| `domain/weather/WeatherPreferencesRepository.kt` | Tạo | không |
| `data/weather/WeatherPreferencesRepositoryImpl.kt`, `data/weather/WeatherPrefsKeys.kt` | Tạo | không |
| `di/WeatherModule.kt` | Sửa (thêm một `@Binds`) | không (WX cũng sẽ sửa, sau WU) |
| `LauncherApp.kt` | Sửa (tiêm repository đơn vị, truyền vào `WeatherWidgetHub.init`) | không |
| `ui/widget/WeatherWidgetHub.kt`, `ui/widget/WeatherWidgetRenderers.kt` | Sửa | không |
| `ui/settings/WeatherSettingsScreen.kt`, `ui/settings/WeatherSettingsViewModel.kt` | Tạo | không |
| `ui/settings/SettingsScreen.kt` | Sửa MỘT dòng (ô `// 1.4 WEATHER (WX)`) | dùng chung, chỉ thay một ô |
| `ui/settings/SettingsRow.kt` | Sửa (thêm `ID_WEATHER_UNIT` ở CUỐI `Companion`) | dùng chung, chỉ thêm |
| `res/values/strings.xml` | Sửa: khối mới ở cuối (tiền tố `settings_weather_`) + sửa tại chỗ `weather_description` (thêm tên đơn vị) | dùng chung |
| `docs/plans/WU-weather-units.md`, `docs/knowledgements/WU-weather-units.md` | Tạo | không |

Không sửa: `SettingsKeys.kt`, `SettingsRepositoryImpl.kt`, `LauncherSettings.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsHubScreen.kt`, manifest, `libs.versions.toml`, `app/build.gradle.kts`, `OpenWeatherMapProvider.kt` (vẫn `units=metric`), `WeatherRepositoryImpl.kt`, `WeatherSheetController.kt`/`WeatherSheetViewModel.kt`.

## Global Constraints

- Layering `ui → domain ← data`: `ui/settings` và `ui/widget` chỉ biết interface ở `domain`. `TemperatureUnit` ở `domain/model/weather` là Kotlin thuần.
- Không thêm lệnh gọi mạng, không đổi `units=metric`, không đổi hợp đồng chặn tần suất D2. Đổi đơn vị không được thay `lastAttemptAt` trong `weather.json`.
- `draw()` không cấp phát; chuỗi dựng ở `prepare()` (bài học GL Task 6, G3e).
- Chữ hiển thị ở `strings.xml`; chuỗi mới nằm một khối ở cuối file, tiền tố `settings_weather_`.
- Không Room, không dependency mới, không test, không sửa tài liệu chung (mục 5.4). Commit message tiếng Anh `<type>: <imperative>` ≤ 72 ký tự, không dấu, dòng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; chỉ `git add` theo đường dẫn tường minh.
- Build `gradlew.bat assembleDebug` xanh mới báo xong Task. Cài lên Live 4 chỉ khi chủ dự án xác nhận máy rảnh. Mọi số đo ghi rõ build debug (debug khởi động chậm hơn release khoảng 4 lần trên Oppo).
- Không log nội dung `weather.json` (có tọa độ).

## Review Focus

1. **°C không đổi so với `main`**: cùng thành phố, cùng dữ liệu, số nguyên trên widget (hiện tại, cao/thấp, 4 mốc) giống bản `main`; không xuất hiện "-0°".
2. **Làm tròn một lần ở cuối**, từ `Double`; cao/thấp = `display(max/min thô)`, không `max` của số đã tròn; hiển thị nhất quán với số hiện tại và các mốc.
3. **Không lệnh gọi mạng nào do đổi đơn vị** (`lastAttemptAt` và dấu thời gian trong `weather.json` giữ nguyên sau khi bấm qua lại).
4. **Cache cũ (số nguyên) đọc được** sau nâng cấp, không crash, `weather.json` hỏng vẫn rơi về "không có dữ liệu" như trước.
5. **Làm mới tức thì**: sau khi đổi và quay về Home, số đúng đơn vị ngay, không chờ nhịp phút; bản xem trước trong bảng chọn widget theo đơn vị.
6. **Không có chớp sai đơn vị lúc khởi động**: trước khi đọc được đơn vị, widget ở trạng thái "Đang tải…" thay vì hiện °C rồi nhảy sang °F.
7. **Chữ có đơn vị và không tràn** ("24°C", "75°F" ở số hiện tại, cao/thấp, 4 mốc): ca xấu nhất "-40°F", "104°F", cao/thấp 3 chữ số; co chữ (`fitWidth`), dải 4 mốc (hiện `textSize` cố định) và dòng cao/thấp không bị cắt ở 2x2 và 4x2.
8. **Mô tả truy cập** đọc đúng đơn vị ("độ C" / "độ F").
9. **Hồi quy Cài đặt**: màn Thời tiết đăng ký không làm đổi các mục khác của trang chủ; Back từ màn về trang chủ; hộp thoại đóng ở `onDestroy`; ghi DataStore lỗi thì Toast và dựng lại hàng từ trạng thái thật.
10. **Vùng file**: không sửa file ngoài danh sách; `SettingsScreen.kt` chỉ đổi đúng một ô.

---

### Task 1: `TemperatureUnit` và giữ °C chính xác (chưa có UI, hiển thị y như cũ) (qua review điều phối 2026-11-13)
**Bắt buộc kiểm tay:** không (đổi nội bộ; đối chiếu `main` nằm trong bài kiểm cuối).
**Files:** `domain/model/weather/TemperatureUnit.kt` (mới), `WeatherModels.kt`, `data/weather/OwmParser.kt`, `data/weather/WeatherStore.kt`, `ui/widget/WeatherWidgetHub.kt` (hàm `sample()` kiểu Double), `ui/widget/WeatherWidgetRenderers.kt` (gọi `TemperatureUnit.CELSIUS.display(...)` tạm cho tới Task 3).
**Interfaces:** Produces `enum TemperatureUnit { CELSIUS, FAHRENHEIT }` với `fun display(tempC: Double): Int` (`CELSIUS` → `roundToInt`, `FAHRENHEIT` → `(tempC * 9.0 / 5.0 + 32.0).roundToInt()`, `Math.round` làm tròn nửa lên, kết quả `Int` nên không bao giờ in "-0") và `companion fromName(String?)` (tên lạ hoặc null → `CELSIUS`, cùng nguyên tắc `GridPreset.fromName`). Consumes: không.
- [x] **Step 1:** Tạo `TemperatureUnit.kt`.
- [x] **Step 2:** Đổi `tempC`, `highC`, `lowC` (`WeatherSnapshot`) và `HourlyPoint.tempC` sang `Double`; cập nhật KDoc (nói rõ đây là °C thô, chưa làm tròn).
- [x] **Step 3:** `OwmParser.snapshot`: giữ `finite(...)`, bỏ `roundToInt`; `highC/lowC` = max/min của các giá trị thô trong cửa sổ cũ (hiện tại + 8 mốc đầu).
- [x] **Step 4:** `WeatherStore`: ghi số thực (`put("t", s.tempC)` ...), đọc bằng `getDouble` (số nguyên cũ trong file vẫn đọc được vì `getDouble` chấp nhận số nguyên; đọc code, kiểm lại ở kịch bản a). `VERSION` giữ 1.
- [x] **Step 5:** Cập nhật `sample()` và renderer (tạm dùng `CELSIUS.display`) cho biên dịch; chuỗi hi/lo, mô tả truy cập giữ nguyên.
- [x] **Step 6:** `gradlew.bat assembleDebug` xanh. Ví dụ để reviewer tự đối chiếu bằng tay (không có test tự động): `display` cho −40.0 → −40 °C / −40 °F; −17.8 → −18 / 0; −0.4 → 0 / 31; 0.0 → 0 / 32; 24.4 → 24 / 76; 24.5 → 25 / 76; 37.8 → 38 / 100.
- [ ] **Step 7: Kịch bản kiểm (Live 4)** — (a) cài đè bản `main` đã có thành phố và `weather.json` số nguyên: mở Home, widget hiện đúng số cũ, không crash, không về "Chọn thành phố"; (b) mở bảng Thời tiết, bấm "Làm mới" (nếu còn trong 60 giây thì đợi): số cập nhật, đúng như `main` (so với ảnh chụp widget bản `main` cùng thời điểm); (c) `adb shell run-as gambi.global.os26launcher cat no_backup/weather.json` (bản debug): các trường `t`, `hi`, `lo` có phần thập phân.

### Task 2: Lưu đơn vị và màn Cài đặt → Thời tiết (qua review điều phối 2026-11-13)
**Bắt buộc kiểm tay:** không.
**Files:** `domain/weather/WeatherPreferencesRepository.kt`, `data/weather/WeatherPreferencesRepositoryImpl.kt`, `data/weather/WeatherPrefsKeys.kt`, `di/WeatherModule.kt`, `ui/settings/WeatherSettingsViewModel.kt`, `ui/settings/WeatherSettingsScreen.kt`, `ui/settings/SettingsScreen.kt` (một ô), `ui/settings/SettingsRow.kt` (`ID_WEATHER_UNIT`), `res/values/strings.xml` (khối `settings_weather_`).
**Interfaces:** Produces `WeatherPreferencesRepository { val unit: Flow<TemperatureUnit>; suspend fun setUnit(unit: TemperatureUnit) }` (flow có `catch` IOException → mặc định và `distinctUntilChanged`, như `SearchPreferencesRepositoryImpl`); khóa `weather_temperature_unit` (string, tên enum). Consumes `DataStore<Preferences>` (đã có ở `DataStoreModule`).
- [x] **Step 1:** Interface, lớp hiện thực, khóa; `@Binds` ở `WeatherModule`.
- [x] **Step 2:** `WeatherSettingsViewModel` (`@HiltViewModel`, mẫu `SearchSettingsViewModel`): `uiState: StateFlow<TemperatureUnit>` (`WhileSubscribed(5_000)`, khởi đầu `CELSIUS`), `onUnitChosen`, kênh `failures` (dùng chuỗi `settings_save_failed` đã có), `CancellationException` ném lại.
- [x] **Step 3:** `WeatherSettingsScreen`: `titleRes = R.string.settings_hub_weather`; `rows(state)` = một `SettingsRow.Value(ID_WEATHER_UNIT, R.string.settings_weather_unit, "°C" hoặc "°F")` (WX thêm hàng sau, theo công thức "thêm hàng"); bấm → hộp thoại chọn một ("Độ C (°C)", "Độ F (°F)") theo mẫu `GeneralSettingsScreen.showGridPresetDialog`: một hộp mỗi lúc, chọn xong `dismiss` và ghi ngay, `onDestroy` gỡ listener rồi `dismiss`; `observe()` thu `uiState` và `ctx.rerender(false)`, thu `failures` → Toast + `ctx.rerender(true)`.
- [x] **Step 4:** Thay đúng một dòng `// 1.4 WEATHER (WX)` trong `SettingsScreens` bằng `SettingsScreenId.WEATHER to ::WeatherSettingsScreen` (giữ dòng trống hai bên; để lại ghi chú `// WX thêm hàng ở WeatherSettingsScreen`). Thêm `ID_WEATHER_UNIT` cuối `SettingsRow.Companion`. Chuỗi: `settings_weather_unit` ("Đơn vị nhiệt độ"), `settings_weather_unit_celsius` ("Độ C (°C)"), `settings_weather_unit_fahrenheit` ("Độ F (°F)"), `settings_weather_unit_value_c` ("°C"), `settings_weather_unit_value_f` ("°F").
- [x] **Step 5:** Build xanh.
- [ ] **Step 6: Kịch bản kiểm (Live 4)** — (a) Cài đặt: mục "Thời tiết" hết mờ "Sắp có", các mục khác của trang chủ không đổi; (b) vào, thấy đúng một hàng "Đơn vị nhiệt độ  °C" (mặc định); (c) chọn °F: hộp thoại đóng, hàng đổi "°F"; chạm đúp nhanh vào hàng chỉ mở một hộp thoại; (d) Back về trang chủ rồi vào lại: vẫn °F; thoát Cài đặt, `force-stop` rồi mở lại: vẫn °F; (e) xoay màn hình khi hộp thoại đang mở: không crash, không rò (như các hộp thoại cũ); (f) chọn lại °C: hàng về "°C".

### Task 3: Widget hiển thị theo đơn vị và làm mới tức thì (qua review điều phối 2026-11-13)
**Bắt buộc kiểm tay:** không.
**Files:** `ui/widget/WeatherWidgetHub.kt`, `ui/widget/WeatherWidgetRenderers.kt`, `LauncherApp.kt`, `res/values/strings.xml` (sửa `weather_description`).
**Interfaces:** Consumes `WeatherPreferencesRepository.unit`, `TemperatureUnit.display`. Produces `WeatherWidgetHub.unit: TemperatureUnit` (đọc ở `prepare`) và `WeatherWidgetHub.init(repo, units, scope)`.
- [x] **Step 1:** `LauncherApp` tiêm `WeatherPreferencesRepository` và truyền vào `WeatherWidgetHub.init`. Hub thu `units.unit`: lưu vào biến `@Volatile`, rồi `WidgetTicker.tickNow()`. Trước emission đầu, `state` trả `WeatherState.Loading` (không hiện °C tạm); khi đã có đơn vị trả `repo.state.value` như cũ.
- [x] **Step 2:** `WeatherRenderer.prepareData`: mọi số hiển thị đi qua `WeatherWidgetHub.unit.display(...)`: `temp`, `hiLo` (cao = `display(highC)`, thấp = `display(lowC)`), `hourTemps[i]`, mỗi số kèm ký hiệu đầy đủ ("24°C", "75°F"; chuỗi mới `settings_weather_symbol_c`/`_f` = "°C"/"°F"). Sửa tại chỗ `weather_hi_lo_short`/`_long`: thay "°" bằng `%3$s` (ký hiệu). Dữ liệu mẫu (bản xem trước) cũng đi qua cùng đường. Vì chữ dài hơn: kiểm lại `fitWidth` của `tempPaint`, `hiLoPaint`; dải 4 mốc (`hourTempPaint.textSize = h * 0.12f` cố định, không co) phải co theo cột `stripColW` cho vừa "-40°F"/"104°F".
- [x] **Step 3:** `description` (mô tả truy cập) dùng số đã đổi và tên đơn vị: sửa `weather_description` thêm `%6$s` ("độ C" / "độ F", lấy từ hai chuỗi mới `settings_weather_a11y_c`, `settings_weather_a11y_f`).
- [x] **Step 4:** Build xanh.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — chuẩn bị: widget 2x2 và 4x2 trên Home, có thành phố và số liệu. (a) Home đang °C ghi lại số; Cài đặt → Thời tiết → °F → Back về Home: ngay lập tức (không chờ tới ranh giới phút) số hiện tại, cao/thấp, 4 mốc theo giờ đều là °F, giá trị đúng công thức (đối chiếu bảng ở Task 1, ví dụ 24°C ↔ 75/76°F); (b) chọn lại °C: số về như ghi ở (a); (c) mở bảng chọn widget (edit mode → Tiện ích): bản xem trước thời tiết theo đơn vị đang chọn; (d) nếu máy hỗ trợ cửa sổ chia đôi: Cài đặt cạnh Home, đổi đơn vị, widget bên Home đổi ngay (nếu không có thì ghi "không kiểm"); (e) **không gọi mạng**: `adb shell run-as gambi.global.os26launcher cat no_backup/weather.json` trước và sau năm lần đổi liên tiếp: `attempt` và `at` giữ nguyên (đo được); (f) `force-stop` rồi mở: widget không chớp °C rồi mới sang °F (quay phim hoặc chụp liên tiếp; nếu chớp thì báo); (g) máy bay (offline) rồi đổi đơn vị: số vẫn đổi; (h) đơn vị có chữ và không tràn: "24°C"/"75°F" đúng ở số hiện tại, cao/thấp, 4 mốc; tạm thời trên bản build cục bộ KHÔNG commit, đổi `sample()` thành −40°C ("-40°F"/"-40°C") rồi 40°C/45°C ("104°F"/"113°F", cao/thấp 3 chữ số, "24h ↑113°F ↓104°F") và xem 2x2, 4x2 trong bảng chọn widget và trên Home: không cắt, không chồng chữ, dải 4 mốc vừa cột; (i) TalkBack hoặc `adb shell uiautomator dump`: mô tả widget chứa "độ F" khi chọn °F.

### Task 4: Knowledgement và đóng nhánh
**Bắt buộc kiểm tay:** không.
**Files:** `docs/knowledgements/WU-weather-units.md`, plan này.
- [x] **Step 1:** Knowledgement theo mẫu mục 8 `MultiAgent-Workflow.md` (sơ đồ Mermaid: chọn đơn vị → DataStore → Hub → `tickNow` → `prepare` → `display`; bảng mọi chỗ nhiệt độ hiện; ví dụ làm tròn; lý do không gọi `imperial`; hướng cho WX thêm hàng vào `WeatherSettingsScreen`).
- [ ] **Step 2 (đã điền phần đọc code, phần máy chờ kết quả Live 4):** Điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung" (điều phối áp dụng).
- [ ] **Step 3 (chưa làm, điều phối dặn chưa rebase):** Rebase lên `main` mới nhất, build xanh, chuyển PR sang Ready (sau khi điều phối review toàn nhánh).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy Vsmart Live 4 (rảnh, được chủ dự án xác nhận) · bản debug nhánh@commit · cần: widget thời tiết 2x2 và 4x2 đã đặt, đã chọn thành phố, có mạng; một bản `main` đã cài từ trước để kiểm nâng cấp cache · các mục theo thứ tự thao tác: Task 1(a)(b)(c) → Task 2(a)-(f) → Task 3(a)-(i) · hồi quy: đồng hồ và lịch (widget khác) không đổi, bảng Thời tiết mở/tìm/chọn thành phố/làm mới như cũ, các màn Cài đặt khác từ trang chủ vẫn mở và Back đúng.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Vsmart Live 4, 2026-11-13, bản debug nhánh@`85630da` (md5 7b38f266), kính FORCE_HIGH; do điều phối chạy, dữ liệu chủ dự án sao lưu trước và khôi phục sau; không crash.
- **Đạt:** Task 1 bước 7 (cache số nguyên cũ nạp được, sau làm mới lưu số thực); Task 2 bước 6 phần (a), (b), (c) mở màn, hộp thoại, hàng đổi "°F" ngay; Task 3 bước 5 (a)(b) đổi °F và Back về Home hiện ngay "66°F", cao/thấp "72°F"/"66°F", dải "66°F 67°F 72°F 70°F"; (e) `weather.json` `attempt`/`at` không đổi (không gọi mạng); (i) mô tả truy cập "độ F".
- **Chưa kiểm (đưa vào bài kiểm tay của chủ dự án):** Task 3 (c) bản xem trước trong bảng chọn widget, (f) chớp °C lúc khởi động, (g) offline, (d) chia đôi màn hình, (h) ca xấu nhất "-40°F"/"104°F"/cao-thấp 3 chữ số (chỉ ước lượng tay, suy luận chưa kiểm chứng; hai chữ số thì dải vừa nhưng chật); Task 2 (d) giữ lựa chọn qua force-stop, (e) xoay màn khi hộp thoại mở, chạm đúp.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | Plan | Viết plan | 72da325 | Chờ điều phối rà |
| 2026-11-13 | Plan | Điều phối duyệt; sửa theo Q2 (hiện "°C"/"°F" kèm chữ ở widget), ghi Q1, Q3 | (docs: update the WU plan after approval) | Q2 chủ dự án, Q1/Q3 điều phối |
| 2026-11-13 | 1 | TemperatureUnit, tempC/highC/lowC sang Double, parser không làm tròn, store getDouble, renderer tạm CELSIUS.display; assembleDebug xanh (Step 7 kiểm Live 4 chưa chạy: chờ máy rảnh) | 05292e6 | Qua review điều phối 2026-11-13 (vòng 1/5). Step 7 (kiểm Live 4, cache cũ) chờ chủ dự án trả máy, điều phối tự chạy; không chặn |
| 2026-11-13 | 2 | WeatherPreferencesRepository + Impl + WeatherPrefsKeys, @Binds, WeatherSettingsViewModel, WeatherSettingsScreen (hàng Đơn vị nhiệt độ + hộp thoại), 1 dòng đăng ký, ID_WEATHER_UNIT, chuỗi settings_weather_*; assembleDebug xanh. Step 6 (Live 4) chưa chạy | b32de17 | Qua review điều phối 2026-11-13 (vòng 1/5) |
| 2026-11-13 | 3 | WeatherWidgetHub (unit flow, tickNow, Loading tới khi đọc xong đơn vị), LauncherApp tiêm repo, renderer dùng unit + ký hiệu "°C"/"°F" ở hiện tại, cao/thấp, 4 mốc; hourTempPaint co theo mốc dài nhất; weather_hi_lo_* dùng %3$s; weather_description thêm %6$s (độ C/độ F); assembleDebug xanh. Step 5 (Live 4) do điều phối chạy trên bản này | 85630da | Qua review điều phối 2026-11-13 (vòng 1/5). Độ rộng ca xấu: ước lượng tính tay, chưa đo trên máy |
| 2026-11-13 | 4 | Viết knowledgement `docs/knowledgements/WU-weather-units.md` (2 sơ đồ Mermaid, bảng mọi chỗ nhiệt độ, bài học); điền bảng Review Focus (phần đọc code), Đề xuất tài liệu chung giữ nguyên | (Task 4 commit) | Kết quả Live 4 và rebase chờ điều phối |
| 2026-11-13 | 1-3 | Điều phối kiểm Live 4 trên 85630da (md5 7b38f266): Task 1 bước 7, Task 2 bước 6 (a-c), Task 3 bước 5 (a)(b)(e)(i) đạt; (c)(d)(f)(g)(h ca 3 chữ số/âm) chưa kiểm, chuyển sang bài kiểm tay của chủ dự án | (docs: record WU Live 4 results) | Không crash; không rebase |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | °C không đổi so với `main` | Đọc code: `round(max thô)` = `max(round)`, Int không in "-0". Đo được (Live 4, 2026-11-13): sau cài đè, widget hiện "Hà Nội, 19 độ C…" bình thường. Chưa đặt cạnh bản `main` cùng lúc | Đạt một phần (không có so sánh trực tiếp) |
| 2 | Làm tròn một lần ở cuối, từ `Double` | Đọc code `TemperatureUnit.display`, `OwmParser`; điều phối đã grep mọi chỗ dùng. Đo được: 19.03 °C → 66 °F, 22.12 °C → 72 °F (đúng công thức) | Đạt |
| 3 | Không lệnh gọi mạng khi đổi đơn vị | Đo được (Live 4, 2026-11-13): `weather.json` `attempt`=1794536598273, `at`=1794536599446 không đổi sau khi đổi đơn vị | Đạt |
| 4 | Cache cũ (số nguyên) đọc được | Đo được: cài đè lên `main` với `weather.json` `"t":18,"hi":26`, nạp không lỗi, sau lần làm mới thường cache lưu số thực (`"t":19.03,"hi":22.12`) | Đạt |
| 5 | Làm mới tức thì, bản xem trước theo đơn vị | Đo được: chọn °F rồi Back về Home, widget 4x2 hiện ngay "66°F", cao/thấp "72°F"/"66°F", dải "66°F 67°F 72°F 70°F". Bản xem trước trong bảng chọn widget: chưa kiểm | Đạt một phần |
| 6 | Không chớp sai đơn vị lúc khởi động | Đọc code: `state` = `Loading` khi `loadedUnit == null`. Cold start (force-stop): chưa kiểm | Chưa kiểm (chỉ code bảo vệ) |
| 7 | Chữ có đơn vị, không tràn ở ca xấu nhất | Đo được: với °F hai chữ số dải 4 mốc vừa nhưng khoảng cách chật. Ca 3 chữ số ("104°F", "113°F") và số âm ("-40°F") chưa ép trên máy: ước lượng tay, suy luận chưa kiểm chứng (`fitWidth` co chữ ở mọi ô) | Chưa kiểm (ca xấu nhất) |
| 8 | Mô tả truy cập đúng đơn vị | Đo được: "Hà Nội, 66 độ F, Mưa. 24 giờ tới cao 72 độ F, thấp 66 độ F" | Đạt |
| 9 | Hồi quy Cài đặt | Đo được: mục "Thời tiết" ở trang chủ hết mờ; màn một hàng "Đơn vị nhiệt độ °C"; hộp thoại "Độ C (°C)" (đánh dấu) và "Độ F (°F)"; chọn °F hàng đổi "°F" ngay; không crash. Chưa kiểm: xoay màn khi hộp thoại mở, chạm đúp, persist qua force-stop | Đạt một phần |
| 10 | Vùng file | `git diff --name-only origin/main`: chỉ file trong "Vùng file" | Đạt (đọc diff) |

## Câu hỏi cho chủ dự án

1. **Mặc định và "Theo hệ thống".** Đề xuất: mặc định °C (khóa vắng = °C), chỉ hai lựa chọn °C/°F như bản nháp; không theo đơn vị hệ thống trong WU. Nếu muốn có lựa chọn thứ ba "Theo hệ thống" (Android 14 có Tùy chọn khu vực → Nhiệt độ, bản cũ chỉ suy từ vùng ngôn ngữ), nên làm ở nhánh sau vì cần kiểm API trên máy Android 14+ (Live 4 không đủ) và thêm một dòng vào hộp thoại; dữ liệu đã lưu không phải di trú.
2. **Ký hiệu trên widget — ĐÃ CHỐT 2026-11-13 (chủ dự án): hiện chữ "24°C"/"75°F" (WU5); Q1 và Q3 đã được điều phối chốt như đề xuất (WU6).** Đề xuất ban đầu của agent: widget và dải theo giờ chỉ hiện "75°" (không chữ F, như bản nháp "24°"), hàng Cài đặt hiện "°C"/"°F". Nếu muốn widget có chữ "°F" thì chỉ đổi chuỗi định dạng, nhưng 2x2 hẹp hơn nên dễ co chữ.
3. **Màn Thời tiết chỉ một hàng cho tới khi WX merge.** Mục "Thời tiết" ở trang chủ sẽ bật và mở màn chỉ có "Đơn vị nhiệt độ". Đề xuất: chấp nhận (đúng ý "đăng ký chỗ để WX thêm hàng"). Hỏi để chắc không cần giữ mục mờ "Sắp có" tới khi WX xong.

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: dòng "Thời tiết": thêm °C/°F (WU), vẫn một vị trí; WX còn nhiều thành phố và thông báo.
- `project_structure.md`: thêm `domain/model/weather/TemperatureUnit.kt`, `domain/weather/WeatherPreferencesRepository.kt`, `data/weather/WeatherPreferencesRepositoryImpl.kt`, `WeatherPrefsKeys.kt`, `ui/settings/WeatherSettingsScreen.kt`, `WeatherSettingsViewModel.kt`; khóa DataStore `weather_temperature_unit`; ghi `weather.json` nay lưu °C dạng số thực.
- `Figma-Draft.md`: mục 4 dòng 1.4: °C/°F đã có (WU), còn nhiều địa điểm, thông báo.
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `lessons/widgets.md` và `lessons/settings-and-data.md`). Gợi ý dự kiến: lưu số thô và làm tròn ở bước hiển thị; đổi đơn vị không gọi lại API.
