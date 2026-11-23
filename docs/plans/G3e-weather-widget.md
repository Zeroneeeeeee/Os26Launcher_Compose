# G3e — Widget thời tiết (OpenWeatherMap) trên lưới Home (TT#14) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ ở bài kiểm cuối để xem kính). Lệnh viết cho CMD/PowerShell. **Không viết code trước khi plan được duyệt.**

**Nhánh:** `feature/g3e-weather-widget` · **Worktree:** `E:\SourceTree\IosLauncher-wt\G3e-weather-widget` · **PR:** #23 (draft) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09 (chủ dự án)
**Trạng thái:** Xong, chờ merge (Oppo đạt; Live 4 chưa kiểm)
**Khóa:** **không khóa schema** (xem D1: không đổi `itemType`, không cột, không `Migration`) · cần Oppo ở bài kiểm cuối (kính thật/mờ) · file độc quyền cần xin một đoạn hẹp: `ui/home/HomeViewModel.kt` (1 nhánh `when`), `ui/home/HomeActivity.kt` (~6 dòng dựng controller) — xem "Vùng file".

**Goal:** Thêm widget **Thời tiết** tự vẽ, dùng lại hạ tầng widget của G2c (ô `itemType = 4`, nút "Tiện ích", đặt chỗ, kéo, xóa bằng "−"): hai biến thể 2×2 (thành phố, nhiệt độ, biểu tượng, tình trạng, cao/thấp) và 4×2 (thêm dải dự báo theo 3 giờ). Dữ liệu từ OpenWeatherMap, một vị trí, °C, tự làm mới tối đa mỗi 30 phút và chỉ khi Home đang hiện, có cache nên khởi động là có số ngay, có trạng thái "chưa cấu hình khóa" / ngoại tuyến / lỗi. Người dùng chọn thành phố bằng cách gõ tên hoặc bấm một lần "Dùng vị trí hiện tại" (quyền vị trí thô, chỉ khi đang mở app).
**Architecture:** (1) Domain thuần: `WeatherProvider` (interface; OpenWeatherMap là bản hiện thực duy nhất), `WeatherRepository`, mô hình `WeatherSnapshot`/`GeoPlace`/`WeatherCondition`/`WeatherError`. (2) Data: `OpenWeatherMapProvider` bằng `HttpURLConnection` + `org.json` (không thêm thư viện), `WeatherStore` là một file JSON trong `noBackupFilesDir` (như `LabelCache` của LC), `WeatherRepositoryImpl` giữ `StateFlow`, chặn tần suất và chạy một-lượt-một-lúc. (3) UI: `WidgetKind.WEATHER` + hai renderer Canvas (cùng họ `ClockAnalogRenderer`), biểu tượng tự vẽ bằng Canvas, `WeatherWidgetHub` móc vào `WidgetTicker` có sẵn để làm mới khi widget hiện (không `Handler`/`WorkManager` mới). (4) Bấm widget mở một bottom sheet nhỏ: ô nhập thành phố, danh sách kết quả, nút vị trí, nút làm mới, trạng thái.
**Tech Stack:** `HttpURLConnection`, `org.json`, `android.location.LocationManager`, `BottomSheetDialog` (Material 1.14 có sẵn), `AtomicFile`. **Không thêm dependency.** Thêm quyền `INTERNET` và `ACCESS_COARSE_LOCATION` vào manifest (xem D5, D6: đây là lần đầu app có quyền mạng).
**Spec:** TT#14 trong `Launcher-Features.md` (dòng 26-29: `WeatherProvider`, gọi thẳng từ app, vị trí nhập tay + một lần lấy vị trí thô; mục 5 "Cần xin ý kiến") + `plans/G2c-clock-calendar-widgets.md` + `knowledgements/G2c-clock-calendar-widgets.md`. **Không gồm TT#17 (thông báo thời tiết)**; chủ dự án đã chốt bỏ khỏi nhánh này (2026-11-09).

---

## Nguồn dữ liệu đã kiểm (đọc tài liệu OpenWeatherMap, 2026-11-09)

Mọi dòng dưới đây đọc từ trang chính thức vào ngày ghi; số cụ thể có thể đổi, executor đọc lại trước khi code (Task 1). Điểm còn **chưa kiểm** ghi rõ.

| Việc | Endpoint (đọc tài liệu) | Gói miễn phí? | Tài liệu |
|---|---|---|---|
| Thời tiết hiện tại | `GET https://api.openweathermap.org/data/2.5/weather?lat=&lon=&units=metric&appid=` | Có (trang giá liệt kê "Current Weather") | https://openweathermap.org/current |
| Dự báo 5 ngày / 3 giờ | `GET https://api.openweathermap.org/data/2.5/forecast?lat=&lon=&cnt=&units=metric&appid=` (`cnt` giới hạn số mốc; tối đa 40) | Có (trang giá liệt kê "3-hour forecast for 5 days"); trang `forecast5` tự nó không nói gói nào, nên **xác nhận bằng một lệnh gọi thật với khóa miễn phí** ở Task 3 | https://openweathermap.org/forecast5 |
| Mã tên thành phố → tọa độ | `GET https://api.openweathermap.org/geo/1.0/direct?q=&limit=5&appid=` (trang ghi `http://`; dùng `https://`) | Có (trang giá liệt kê Geocoding) | https://openweathermap.org/api/geocoding-api |
| Tọa độ → tên | `GET https://api.openweathermap.org/geo/1.0/reverse?lat=&lon=&limit=1&appid=` | Có | cùng trang |
| One Call 3.0 | `https://api.openweathermap.org/data/3.0/onecall` | **Không dùng.** Trang nói "included in the One Call API subscription only", kèm 1.000 lệnh/ngày miễn phí, vượt thì tính tiền; trang không nói có bắt thẻ thanh toán hay không → chưa kiểm | https://openweathermap.org/api/one-call-3 |

- **Hạn mức gói Free:** "60 API calls/minute, 1,000,000 calls/month" (https://openweathermap.org/price). Kế hoạch dùng ≤ 2 lệnh mỗi 30 phút (hiện tại + dự báo) + vài lệnh geocoding khi đổi thành phố, tức dưới 100 lệnh/ngày: xa mức trần.
- **Tham số `q`, `id`, `zip` của `/weather` bị đánh dấu deprecated** ("built-in geocoder has been deprecated"): dùng tọa độ (`lat`/`lon`) từ Geocoding API, không gọi `/weather?q=`.
- **`temp_min`/`temp_max` của `/weather` KHÔNG phải cao/thấp cả ngày** (trang `forecast5` ghi chúng thường bằng `temp` trừ đô thị lớn; với `/weather` là cực trị quan sát tại thời điểm đó — suy luận từ mô tả, chưa kiểm số liệu). Cao/thấp của widget tính từ dự báo (D8), không dùng hai trường này.
- **Khóa mới kích hoạt chậm tới 2 giờ**, trong lúc đó trả 401 (https://openweathermap.org/faq). 429 = vượt hạn mức. Hệ quả cho kiểm tay: khóa vừa tạo có thể "sai" giả; phân biệt bằng thời gian.
- **Ghi công (attribution):** gói Free tới Professional, khi giấy phép đòi, cần câu "Weather data provided by OpenWeather", liên kết `openweathermap.org` và logo OpenWeather (faq). Plan xử lý ở Task 7 (câu chữ + liên kết trong bottom sheet); **logo chưa quyết** (câu hỏi Q4).
- **`lang=vi` được hỗ trợ**, nhưng plan không dùng (D9): mô tả tình trạng lấy từ chuỗi của app theo mã `weather[0].id` để cache đổi ngôn ngữ theo máy.
- **Mã tình trạng và hậu tố biểu tượng `d`/`n`** (https://openweathermap.org/weather-conditions): 2xx giông, 3xx mưa phùn, 5xx mưa, 6xx tuyết, 7xx hiện tượng khí quyển, 800 quang, 801-804 mây. Trang **không nói gì về giấy phép của ảnh biểu tượng** (và đường dẫn ảnh trong trang khác dạng `img/wn/` thường dùng) → không dùng ảnh của OWM (D7).
- **Chưa kiểm được và sẽ đo ở Task 3 bằng khóa thật:** (a) `geo/1.0/direct` với "Hà Nội" / "Đà Nẵng" có ra kết quả đúng không (trang không nhắc tiếng Việt); (b) `/forecast` có chạy với khóa Free không; (c) mã lỗi thật của khóa sai/chưa kích hoạt; (d) kích thước phản hồi.

## Hiện trạng đọc từ code (2026-11-09, `13b0fe5`)

| # | Chỗ | Hiện trạng | Hệ quả cho G3e |
|---|---|---|---|
| 1 | `AndroidManifest.xml` | Chỉ có `SET_WALLPAPER`, `REQUEST_DELETE_PACKAGES`; **không có `INTERNET`** | Thêm `INTERNET`, `ACCESS_COARSE_LOCATION` (+ `uses-feature location required=false`, cần kiểm: quyền vị trí kéo theo yêu cầu phần cứng, suy luận từ tài liệu Android) — Task 1 |
| 2 | `app/build.gradle.kts` | Không bật `buildConfig`, không đọc `local.properties`; `org.gradle.configuration-cache=true` (AGENTS.md: tránh đọc file thẳng lúc configuration) | Dùng `providers.fileContents(...)` (API tương thích configuration cache) — Task 1 |
| 3 | `WidgetKind` | `CLOCK`, `CALENDAR`; `id` lưu ở `className` của hàng `itemType = 4`; `fromId(null/lạ) = null` → hàng bị bỏ qua, **không xóa** (`LayoutRepositoryImpl.assemble`) | Thêm `WEATHER("weather", 2×2, 4×2)` không đổi schema; bản cũ gặp hàng `weather` thì bỏ qua im lặng |
| 4 | `when (kind)` | Có ở `WidgetRenderers.create`, `WidgetPickerController.kindNameRes`, `HomeViewModel.onWidgetTapped` (biểu thức, phải đủ nhánh) | Thêm `WEATHER` vào cả ba; nhánh ở `HomeViewModel` là 1 dòng trong file độc quyền |
| 5 | `WidgetRenderer.prepare(width, height, density, now, locale, is24h)` | Renderer không nhận dữ liệu ngoài | Thêm interface con `WeatherDataRenderer.setData(state)` thay vì đổi chữ ký chung (không đụng 4 renderer cũ) |
| 6 | `WidgetTicker` | Nhịp mỗi phút chỉ khi có widget hiện; `register` làm mới ngay khi cửa sổ hiện lại; 0 `Handler`/receiver khi không có widget | Dùng lại làm "cổng hiện + nhịp phút" cho làm mới thời tiết: không `Handler` mới (D2) |
| 7 | `WidgetPreviewView` | Dựng renderer thật, vẽ một lần | Renderer thời tiết cần chế độ dữ liệu mẫu cố định cho bảng chọn |
| 8 | Bấm widget | `IconGridView.onWidgetClick(kind)` ← `HomePagerController` ← `viewModel::onWidgetTapped` (mở app Đồng hồ/Lịch) | Chặn riêng `WEATHER` ở `HomePagerController` để mở sheet; `HomeViewModel` chỉ thêm nhánh `-> Unit` cho đủ `when` |
| 9 | `LabelCache` (LC) | `AtomicFile` + `org.json` trong `noBackupFilesDir`, ghi nền, mọi lỗi chỉ log và rơi về đường cũ | Tiền lệ cho `WeatherStore` |
| 10 | `data_extraction_rules.xml`, `allowBackup=true` | Mẫu trống (không loại gì) | Vị trí người dùng và cache không được vào bản sao lưu đám mây → để trong `noBackupFilesDir` (D3) |
| 11 | `AppSearchMatcher.normalize` | Đã có bỏ dấu tiếng Việt (`đ` = `d`) bằng `Normalizer` NFD | Tái dùng ý tưởng cho đường dự phòng của geocoding (D4) |
| 12 | `res/` | Chỉ có `values`, `values-night`; không có `values-en` | Chuỗi mới vào `values/strings.xml`, khối `weather_` ở cuối |

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật, có nhãn nguồn)

1. **App chưa từng ra mạng** (đọc manifest). Mọi thứ mới: quyền, xử lý lỗi, riêng tư, và việc khóa API nằm trong APK. Một khóa trong APK **có thể bị trích ra** (đọc tài liệu Android/thực tế ngành; `Launcher-Features.md` dòng 28 đã ghi "Key trong APK có thể bị lấy"); chủ dự án chấp nhận ở giai đoạn này, nhưng thiết kế phải cho phép đổi sang proxy sau mà không đụng UI (D1 của provider).
2. **Hạ tầng widget đã xong và đã đo** (G2c): một ô `IconCellView` ở chế độ widget, renderer Canvas không cấp phát trong `draw`, `WidgetTicker` chỉ chạy khi có widget hiện, kính tại chỗ đã đo đạt trên Live 4 và Oppo. Widget thời tiết phải chạy trên đó, **không sửa `core/glass`, không sửa planner/`LayoutReconciler`/`data/layout/db`**.
3. **Làm mới chỉ khi Home hiện, tối đa mỗi 30 phút** (chủ dự án chốt 2026-11-09). Không có test tự động (AGENTS.md): kiểm bằng logcat có tag riêng.
4. **Dữ liệu cá nhân:** tọa độ thô của người dùng. Phải nằm ngoài sao lưu đám mây, không bao giờ vào log, làm tròn (~1 km).
5. **Máy có thể không có Google Play Services** (không thể giả định): vị trí phải đi bằng `LocationManager` hệ thống, không thêm thư viện fused.
6. **File nóng:** G3e dùng lại G2c nên gần như chỉ thêm file mới; hai file độc quyền chỉ xin một đoạn hẹp. Không có nhánh khác đang giữ khóa schema.
7. **Tiếng Việt có dấu:** người gõ "Hà Nội", "Đà Nẵng"; bàn phím có thể sinh chuỗi tổ hợp NFC hoặc rời NFD; API geocoding không hứa gì về tiếng Việt (chưa kiểm).

### D1. Lưu widget thế nào (schema Room)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Dùng lại `itemType = 4`, `className = "weather"`, `spanX/spanY` = 2×2 hoặc 4×2** | Chỉ thêm giá trị enum `WidgetKind.WEATHER` | **Không đổi schema Room**: không cột, không bảng, không `version`, không `Migration`, `app/schemas/*` không đổi, **không cần khóa schema** (khóa chỉ cần khi đặt nghĩa cho một `itemType` mới; `className` thuộc nghĩa đã đặt ở G2c: "mã loại"); mọi planner/`LayoutReconciler` đã xử lý hàng loại 4 tổng quát; bản cũ đọc hàng lạ thì bỏ qua, không xóa | Không có chỗ lưu cấu hình riêng từng widget (một widget thời tiết nào cũng dùng **một vị trí chung toàn app**) | **Chọn** |
| B. Cột `widgetConfig` + `Migration 1→2` để mỗi widget có thành phố riêng | Nhiều vị trí | Mở rộng sau | Cần khóa schema, kịch bản cài-đè, G2c đã ghi "xét lại khi có widget cần cấu hình"; phạm vi chủ dự án chốt là **một vị trí** | Không (xét lại khi làm nhiều vị trí) |

**Vì sao A:** phạm vi chốt là một vị trí nên không có cấu hình riêng từng widget để lưu; vị trí là cài đặt toàn app, để ở `WeatherStore` (D3). **Đánh đổi:** thêm hai widget thời tiết sẽ cùng hiện một thành phố; chấp nhận. **Câu hỏi cho điều phối:** xác nhận đây không phải "đặt nghĩa cho giá trị mới" cần khóa (Q6).

### D2. Chiến lược làm mới dữ liệu

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Theo cổng "widget hiện": làm mới khi widget đăng ký (Home hiện) và ở mỗi nhịp phút của `WidgetTicker`, nhưng chỉ gọi mạng nếu quá 30 phút kể từ lần thử gần nhất** | `WeatherWidgetHub.onVisibleTick()` so thời gian (rẻ, so một số nguyên); lần thử gần nhất lưu trong `WeatherStore` nên khởi động lại tiến trình không vượt được chặn | **Không thêm `Handler`/receiver/job nào**: tái dùng nhịp phút đã đo của G2c; đúng chữ "chỉ khi Home hiện"; 0 chi phí khi không ai nhìn; không cần quyền/cấu hình nền | Nếu người dùng để Home hiện cả ngày thì cập nhật đúng mỗi ~30 phút (chấp nhận); dữ liệu có thể cũ tới 30 phút lúc vừa mở lại (có cache hiện ngay, làm mới trong nền) | **Chọn** |
| B. `WorkManager` định kỳ 30 phút | Công việc nền | Số luôn mới khi mở Home | Chạy khi không ai nhìn (tốn pin/mạng, trái yêu cầu "chỉ khi Home hiện"); thêm dependency `work` (2.12.0 có trong AGENTS.md); WorkManager tối thiểu 15 phút và bị Doze/OEM hoãn nên **không bảo đảm** 30 phút; thêm bề mặt mới (service, constraint) | Không (chủ dự án: "không WorkManager trừ khi justify"; không justify được) |
| C. Thêm `Handler.postDelayed(30 phút)` riêng của thời tiết | Hẹn giờ riêng | Đơn giản | Một nguồn nhịp thứ hai song song với ticker; phải tự xử lý hiện/ẩn, tắt màn hình, rò view (G2c đã giải một lần) | Không |
| D. `ACTION_TIME_TICK`/broadcast | Hệ thống bắn mỗi phút | — | G2c đã cân nhắc và bỏ (không rẻ hơn A) | Không |

**Quy tắc chặn (cụ thể, kiểm được bằng logcat):** `lastAttemptAt` là giờ treo tường lưu trong file. Thành công → lần kế **sau ≥ 30 phút**. Lỗi mạng/máy chủ (không có cache hoặc có cache) → thử lại sau **≥ 5 phút** (ngoại lệ có chủ đích so với "tối đa mỗi 30 phút", để vào được sau khi mất mạng; vẫn tối đa 12 chu kỳ/giờ ≈ 24 lệnh/giờ ≪ 60 lệnh/phút; **câu hỏi Q2**). 401 (khóa sai/chưa kích hoạt) → **không thử lại** cho tới khi khóa đổi hoặc tiến trình khởi động lại, để không đập vào API. 429 → 30 phút. Đổi thành phố / bấm "Làm mới" trong sheet → thử ngay (tối thiểu 60 giây giữa hai lần bấm). `lastAttemptAt` ở tương lai (người dùng chỉnh giờ lùi) → coi là đã hết hạn. Một lượt làm mới tại một thời điểm (single-flight `Mutex`), nhiều widget dùng chung một kết quả.

**Đánh đổi chấp nhận:** không cập nhật khi Home đóng; số có thể cũ ≤ 30 phút khi mở. **Xét lại khi:** có thông báo thời tiết (TT#17, ngoài phạm vi) vì khi đó cần nền thật.

### D3. Lưu cache và vị trí ở đâu

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Một file JSON `weather.json` trong `noBackupFilesDir`, `AtomicFile`, `org.json`** | Chứa `v`, vị trí đã chọn (tên, lat, lon làm tròn 2 số thập phân), `snapshot` cuối cùng, `lastAttemptAt`, `lastSuccessAt` | Tiền lệ LC (đã chạy ổn trên cả hai máy); **không vào sao lưu đám mây** (tọa độ là dữ liệu cá nhân, mục D-vấn đề 4); ghi nguyên tử; một nơi cho vị trí + cache + dấu thời gian; không chạm Room/DataStore | Phải tự lo version/hỏng file (như LC: sai version hoặc hỏng thì bỏ cache, không crash) | **Chọn** |
| B. DataStore Preferences (`launcher_settings` có sẵn) | Khóa `weather_*` + snapshot dạng chuỗi JSON | Có Flow và an toàn đa luồng sẵn | Nằm trong `files/datastore` → vào bản sao lưu tự động (`allowBackup=true`, quy tắc trích xuất đang trống); snapshot dạng blob lạc loài trong kho cài đặt; chung file với mọi cài đặt khác, ghi nhiều làm phát lại | Không (xét lại nếu chủ dự án muốn vị trí là "cài đặt" đồng bộ qua sao lưu) |
| C. Room (bảng `weather_cache`) | Bảng mới | Truy vấn | Cần khóa schema + `Migration` cho một blob 1 hàng; trái "không Room trừ khi justify" | Không |
| D. Chỉ RAM | `StateFlow` | Đơn giản | Mỗi lần khởi động trống, mất yêu cầu "khởi động có số ngay" | Không |

**Vì sao A:** cùng lý do LC chọn file (không Room, không sao lưu). Trạng thái luôn sống trong `StateFlow` của repository; file chỉ là bản lưu khi khởi động. **Xét lại khi:** làm nhiều vị trí (cần danh sách có thứ tự → vẫn file hoặc Room tùy khi đó).

### D4. Gọi HTTP bằng gì

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `HttpURLConnection` + `org.json` (có sẵn trong Android)** | Một lớp `WeatherHttp.get(url): String` với timeout 10 s, giới hạn 512 KB, `UTF-8`, mã lỗi → `WeatherError`; chạy trên `IoDispatcher` | **0 dependency mới** (chủ dự án muốn ưu tiên cái có sẵn); `org.json` đã dùng ở `LabelCache`; mỗi lần làm mới chỉ 2-3 lệnh GET nhỏ, không cần pool/interceptor/cache HTTP | Tự viết xử lý lỗi/đóng luồng/đọc `errorStream`; `HttpURLConnection` không hủy theo coroutine (dùng `disconnect()` khi hủy + timeout cứng làm chặn trên); không có HTTP/2 (không cần ở đây) | **Chọn** |
| B. OkHttp (+ Retrofit + kotlinx.serialization) | Thư viện chuẩn thực tế | Hủy, interceptor, ghi log có che khóa, kiểu dữ liệu an toàn | Ba dependency mới + plugin KSP/serialization cho vài lệnh GET; tăng kích thước APK và bề mặt bảo trì; **chưa kiểm version mới nhất trên Maven** nên plan **không đề xuất** version | **Không đề xuất.** Xét lại khi có ≥ 3 loại lệnh gọi mạng hoặc cần hủy/ghi log tinh |
| C. `java.net.http.HttpClient` | Java 11 | Hiện đại | Không có trên Android (minSdk 26) | Loại |

**Quy tắc bảo mật của lớp HTTP:** URL chứa `appid` **không bao giờ được log**; log chỉ đường dẫn không có query hoặc đã che khóa. Chỉ `https`. `usesCleartextTraffic` không bật. Đọc thân lỗi chỉ để lấy `cod`/`message`, không in nguyên.

**Đường chuyển sang proxy/backend sau (Launcher-Features dòng 28):** `WeatherProvider` là interface ở `domain`; `OpenWeatherMapProvider` nhận `WeatherProviderConfig(baseUrl, apiKey)` từ DI. Thêm backend = viết `ProxyWeatherProvider` (hoặc đổi `baseUrl` và bỏ `apiKey`) và đổi một dòng ở `WeatherModule`; widget, repository, sheet không đổi. Không làm sẵn gì thêm (không đoán hình dạng backend).

### D5. Khóa API: đưa vào build thế nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `OWM_API_KEY=` trong `local.properties` (gitignore) → `buildConfigField("String", "OWM_API_KEY", ...)`; dự phòng biến môi trường `OWM_API_KEY` cho máy khác** | Đọc bằng `providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText` (tương thích configuration cache), `Properties.load`, thiếu → chuỗi rỗng | Không bao giờ vào git (`local.properties` đã trong `.gitignore`, kiểm `git check-ignore`); **thiếu khóa vẫn build xanh**, widget hiện "chưa cấu hình khóa"; mỗi worktree chép `local.properties` đã là quy trình có sẵn | Khóa **nằm trong APK** (BuildConfig là chuỗi hằng): trích được bằng giải nén. Chủ dự án chấp nhận cho giai đoạn này (2026-11-09). Debug và release giống nhau | **Chọn** |
| B. Nhập khóa trong app (Cài đặt) | Người dùng dán khóa | Không khóa trong APK | Người dùng cuối không có khóa; đổi hướng sản phẩm | Không |
| C. Gradle property `-POWM_API_KEY` / `gradle.properties` toàn máy | Ngoài repo | Không đụng `local.properties` | Dễ vô tình commit `gradle.properties` của dự án; worktree không tự có | Chỉ như dự phòng đọc thêm, không thay A |
| D. Proxy/backend giữ khóa | Khóa không ra khỏi máy chủ | An toàn nhất | Cần hạ tầng chưa có; chủ dự án chốt "gọi thẳng trước" | Không (đường đi tương lai ở D4) |

**Ghi vào knowledgement và plan:** khóa trong APK có thể bị trích ra; nếu bị lạm dụng thì xoay khóa trên OpenWeather và phát hành lại. Executor **không bao giờ** in khóa ra log, plan, commit, PR, báo cáo. Khi viết kịch bản kiểm dùng chữ `<KEY>`.

### D6. Vị trí: nhập tay + một lần lấy vị trí thô (UX)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Bấm widget → bottom sheet "Thời tiết": ô nhập thành phố + danh sách kết quả (tối đa 5) + nút "Dùng vị trí hiện tại" + nút "Làm mới" + trạng thái** | Một nơi cho mọi thao tác; vị trí thô xin **đúng lúc người dùng bấm nút** (có ngữ cảnh) | Không thêm màn mới, không phụ thuộc Cài đặt; cùng ngôn ngữ với bảng chọn "Tiện ích" (BottomSheetDialog); xin quyền khi cần, không lúc cài | Sheet là cửa sổ riêng nên mất khi `recreate()` (chấp nhận, như bảng chọn G2c); bàn phím trong dialog cần kiểm (`HomeActivity` dùng `adjustNothing`, dialog có cửa sổ riêng) | **Chọn** |
| B. Một hàng "Thời tiết" trong Cài đặt | Màn Cài đặt có sẵn | Không động tới chạm widget | Rời Home để đặt thành phố; hàng Cài đặt chỉ vào được từ edit mode | Không ở bản đầu (xét thêm sau: hàng chỉ mở cùng sheet; Q5) |
| C. Hộp thoại xin vị trí ngay khi thêm widget | Tự động | Một bước | Xin quyền không có ngữ cảnh, dễ bị từ chối vĩnh viễn; người dùng thích gõ tay vẫn bị hỏi | Không |
| D. Theo dõi vị trí liên tục / nền | Tự cập nhật | Tiện | Trái chốt ("không chạy nền, không xin quyền nền"), tốn pin, riêng tư | Loại |

**Cách xin vị trí thô một lần (trong A):** nút "Dùng vị trí hiện tại" → nếu chưa có `ACCESS_COARSE_LOCATION` thì `registerForActivityResult(RequestPermission)` (đăng ký lúc dựng controller ở `HomeActivity.onCreate`, vì launcher phải đăng ký trước khi Activity STARTED — đọc tài liệu AndroidX Activity, chưa kiểm trên dự án) → có quyền thì `LocationManager.getCurrentLocation(NETWORK_PROVIDER, …)` (API 30+) hoặc `requestSingleUpdate` / `getLastKnownLocation` (API 26-29), hết hạn 10 giây → `reverse geocoding` OWM để lấy tên → lưu tọa độ làm tròn 2 số thập phân + tên → làm mới ngay. **Chỉ một lần, chỉ khi sheet đang mở, không `LocationListener` sống lâu, không dịch vụ nền, không `ACCESS_BACKGROUND_LOCATION`.** Từ chối → thông báo "chưa cấp quyền vị trí, hãy nhập tên thành phố", giữ nguyên lựa chọn cũ; "không hỏi lại" → cũng chỉ thông báo (không tự mở Cài đặt ở bản đầu). **Giả định chưa kiểm:** `NETWORK_PROVIDER` có trả vị trí trên Live 4 và Oppo (cần dịch vụ vị trí của hệ thống/Google); Task 6b kiểm trên máy thật (bắt buộc kiểm tay).

**Nhập tên:** `Normalizer.normalize(raw, NFC)` + `trim` (bàn phím có thể sinh NFD; chưa kiểm) → `geo/1.0/direct?q=<UTF-8 URL-encode>&limit=5`. Rỗng kết quả → thử lại **một lần** với chuỗi đã bỏ dấu (`đ` → `d`, cùng ý tưởng `AppSearchMatcher.normalize`) trước khi báo "không tìm thấy". Nhiều kết quả → danh sách "Tên, tỉnh/bang, mã nước" để chọn; một kết quả → chọn luôn. Tên hiển thị trên widget lấy từ `local_names.vi` nếu có, ngược lại `name` (không lấy `name` của `/weather`, vì đó là tên trạm).

### D7. Biểu tượng thời tiết

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Tự vẽ bằng Canvas/`Path` trong `WeatherIcons`** | Hàm `draw(canvas, condition, cx, cy, size)`: mặt trời (tròn + tia), trăng (hai hình tròn cắt), mây (tròn + hình chữ nhật bo), mưa (nét xiên), giông (tia chớp), tuyết (chấm/dấu sao), sương mù (vạch ngang); phối bằng mây + mặt trời/trăng cho "ít mây" | **Hợp lệ giấy phép 100%** (tác phẩm của dự án); không tải ảnh, không bitmap cache, chạy cả khi ngoại tuyến; vector → sắc nét mọi cỡ widget; cùng họ `ClockAnalogRenderer`; `Path` dựng ở `prepare`, không cấp phát trong `draw` | Phải thiết kế tay (≈ 10 biểu tượng); chất lượng thị giác phụ thuộc người vẽ, cần chủ dự án nhìn | **Chọn** |
| B. `VectorDrawable` XML trong `res/drawable` | Mỗi biểu tượng một file | Nhà thiết kế sửa được; `draw(canvas)` đã đủ nhanh | 10-20 file; màu cố định hoặc phải tint từng lớp; nếu nhà thiết kế chưa có thì vẫn phải có người vẽ | Không ở bản đầu (đường nâng cấp nếu có designer; Q3) |
| C. Ảnh `openweathermap.org/img/wn/*.png` | Tải theo mã `icon` | Có sẵn | **Giấy phép ảnh không nêu** trong tài liệu đã đọc → không dùng; thêm tải mạng + cache bitmap | Loại |
| D. Emoji/ký tự Unicode | Chữ | Rẻ nhất | Hiển thị khác nhau giữa ROM (đặc biệt Oppo/Vinsmart), không đồng bộ với kính | Loại |

**Ánh xạ mã OWM → `WeatherCondition`** (bảng cố định ở `domain`, Kotlin thuần, từ https://openweathermap.org/weather-conditions): `2xx` → `THUNDERSTORM`; `3xx`, `5xx` → `RAIN` (kể cả `511` mưa đóng băng, ghi chú); `6xx` → `SNOW`; `7xx` (701-762) → `FOG`; `771`, `781` → `STORM_WIND` dùng chung biểu tượng giông (ghi chú, hiếm); `800` → `CLEAR`; `801`, `802` → `PARTLY_CLOUDY`; `803`, `804` → `CLOUDY`. Ngày/đêm: lấy hậu tố `d`/`n` của `weather[0].icon` (có trong `/weather` và `/forecast`), vì tự tính từ mặt trời mọc/lặn cần thêm trường và múi giờ. Mã lạ → `UNKNOWN` (biểu tượng mây mờ, chữ "—"), **không crash**.

**Mô tả tình trạng:** chuỗi của app theo `WeatherCondition` (≈ 10 chuỗi tiếng Việt trong `strings.xml`), **không** dùng `description` của OWM, để cache đổi ngôn ngữ theo máy mà không gọi lại (đổi ngôn ngữ làm `HomeActivity` tạo lại, G2c). Mất độ mịn ("mưa nhẹ"/"mưa lớn"): chấp nhận.

### D8. Nội dung widget (iOS-like) và dải 4×2

| Phương án 4×2 | Mô tả | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Dải theo giờ: 4 mốc kế tiếp của `/forecast` (bước 3 giờ), mỗi mốc: giờ ("15", "18"…), biểu tượng nhỏ, nhiệt độ** | Trái: thành phố, nhiệt độ lớn, tình trạng, cao/thấp; phải: dải 4 mốc | Giống bố cục medium của widget thời tiết iOS (dải theo giờ); dùng đúng `list[0..3]` của một lệnh `cnt=9`; không cần tính gộp | Bước 3 giờ chứ không phải 1 giờ (giới hạn gói Free, đã đọc: "3-hour forecast for 5 days") | **Chọn** |
| B. Dải 5 ngày (cao/thấp từng ngày) | Gộp 40 mốc theo ngày địa phương | Có dự báo nhiều ngày | Cao/thấp từng ngày là gộp từ mốc 3 giờ (ngày đầu/cuối thiếu mốc); cần xử lý múi giờ `city.timezone`; nhiều chữ, ít chỗ trên 4×2 | Không ở bản đầu (hướng mở rộng khi làm cỡ lớn hơn) |

**Cao/thấp (cả hai cỡ):** `max/min` của {nhiệt độ hiện tại} ∪ {`main.temp` của 8 mốc tiếp theo ≈ 24 giờ tới}. Đây là "cao/thấp trong 24 giờ tới", **không phải cao/thấp của ngày dương lịch** (gói Free không có trường đó; `/weather.temp_min/max` không đáng tin cho việc này, xem phần nguồn). Nhãn trong sheet/knowledgement ghi rõ cách tính; Q1 hỏi chủ dự án có chấp nhận.

**Hiển thị 2×2:** tên thành phố (cắt `…`), nhiệt độ lớn "28°", biểu tượng, tình trạng, "C 31° · T 24°" (hoặc ký hiệu mũi tên ↑↓). Cỡ chữ suy từ cạnh ngắn như G2c (không dùng `sp`); dòng phụ ẩn khi quá thấp (ngưỡng CHỈNH TAY).

**Trạng thái (cả hai cỡ):** (1) **Chưa có khóa** (`BuildConfig.OWM_API_KEY` rỗng): biểu tượng mây gạch, chữ "Chưa cấu hình khóa" + dòng phụ (4×2) "Thêm OWM_API_KEY vào local.properties"; (2) **Chưa chọn thành phố**: "Chọn thành phố" (bấm mở sheet); (3) **Đang tải lần đầu** (không cache): khung xám; (4) **Có dữ liệu**; (5) **Dữ liệu cũ** (cache > 2 giờ hoặc lần gần nhất lỗi): vẫn hiện số, chuyển mờ nhẹ + biểu tượng "ngoại tuyến" nhỏ ở góc, 4×2 ghi "Cập nhật HH:mm"; (6) **Lỗi và không có cache** (mạng/401/429/404): biểu tượng "!" + câu ngắn theo loại lỗi. Mọi chuỗi ở `strings.xml`, `contentDescription` đọc đủ thành phố, nhiệt độ, tình trạng, cao/thấp.

### Đánh đổi chấp nhận

- Khóa API nằm trong APK (trích ra được); không có proxy ở bản đầu.
- Một vị trí toàn app; hai widget thời tiết cùng hiện một thành phố.
- Cập nhật chỉ khi Home hiện, tối đa ~30 phút (thử lại sau lỗi mạng nhanh hơn: 5 phút).
- Dự báo theo bước 3 giờ; cao/thấp là 24 giờ tới; tình trạng mô tả theo nhóm, không theo cường độ.
- Không có thông báo thời tiết, không nhiều vị trí, không °F, không đổi cỡ tại chỗ (xóa rồi thêm lại, như G2c).

### Rủi ro chính

1. **Giả định mạng chưa kiểm** (geocoding tiếng Việt, `/forecast` với khóa Free, mã lỗi thật) → Task 3 đo bằng khóa thật trước khi xây tiếp (bắt buộc kiểm).
2. **Vị trí thô trên Oppo/Live 4** có thể không có nhà cung cấp mạng → Task 6b bắt buộc kiểm tay; đường lùi là chỉ nhập tay.
3. **Rò khóa/tọa độ qua log** → quy tắc che trong D4, rà bằng `grep` ở mỗi Task (không `Log` có URL/khóa/lat/lon).
4. **Đua dữ liệu:** nhiều widget, sheet và nhịp phút cùng gọi làm mới → single-flight `Mutex` trong repository, kết quả phát qua một `StateFlow`.
5. **Hồi quy G2c** khi thêm `WEATHER` vào `when` và renderer: giữ nguyên hành vi đồng hồ/lịch; kiểm ở bài cuối.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Thời tiết được gỡ đóng băng; nguồn là **OpenWeatherMap**; giữ interface `WeatherProvider` | Chủ dự án, 2026-11-09 (tech lead đề xuất OWM) |
| 2 | Chỉ dùng endpoint gói Free (Current 2.5, Forecast 5 ngày/3 giờ, Geocoding); **không One Call 3.0** | Chủ dự án chốt; plan kiểm bằng tài liệu (bảng "Nguồn dữ liệu"); xác nhận `/forecast` bằng lệnh thật ở Task 3 |
| 3 | Gọi thẳng từ app, thiết kế sẵn để thêm proxy sau (config + interface) | Chủ dự án, 2026-11-09; `Launcher-Features.md` dòng 28 |
| 4 | Khóa đọc từ `local.properties` (`OWM_API_KEY`) vào BuildConfig; không bao giờ commit; thiếu khóa vẫn build và widget báo "chưa cấu hình khóa"; **khóa trong APK có thể bị trích ra, chấp nhận lúc này** | Chủ dự án, 2026-11-09 |
| 5 | Vị trí: nhập tay + một lần lấy vị trí thô khi đang mở app; không nền, không quyền nền | Chủ dự án (`Launcher-Features.md` dòng 29) |
| 6 | Phạm vi bản đầu: widget 2×2 và 4×2, một vị trí, °C, làm mới ≤ 30 phút và chỉ khi Home hiện, có cache; **không thông báo thời tiết (TT#17), không WorkManager** | Chủ dự án, 2026-11-09 |
| 7 | D1: dùng lại `itemType = 4`, `className = "weather"`; **không đổi schema Room, không khóa** | Agent đề xuất 2026-11-09, chờ duyệt (Q6) |
| 8 | D2: làm mới qua `WidgetTicker` + chặn 30 phút lưu trong file; thử lại sau lỗi mạng 5 phút (Q2) | Agent đề xuất, chờ duyệt |
| 9 | D3: cache + vị trí ở một file JSON trong `noBackupFilesDir`, không Room, không DataStore | Agent đề xuất, chờ duyệt |
| 10 | D4: `HttpURLConnection` + `org.json`, **không thêm thư viện** | Agent đề xuất (đúng ưu tiên của chủ dự án), chờ duyệt |
| 11 | D6: bấm widget mở bottom sheet (nhập tên, kết quả, vị trí hiện tại, làm mới, trạng thái) | Agent đề xuất, chờ duyệt |
| 12 | D7: biểu tượng tự vẽ Canvas; không dùng ảnh OWM; mô tả tình trạng theo chuỗi của app | Agent đề xuất, chờ duyệt (Q3) |
| 13 | D8: 4×2 có dải 4 mốc theo 3 giờ; cao/thấp = 24 giờ tới | Agent đề xuất, chờ duyệt (Q1) |
| 14 | Thêm quyền `INTERNET` và `ACCESS_COARSE_LOCATION` (lần đầu app có quyền mạng/vị trí); không `ACCESS_BACKGROUND_LOCATION`, không `ACCESS_FINE_LOCATION` | Nêu rõ cho chủ dự án duyệt cùng plan |

## Ngoài phạm vi / ghi để sau

- Thông báo thời tiết (TT#17), cảnh báo thời tiết, nhiều vị trí, °F/đổi đơn vị, dự báo nhiều ngày, hiển thị mưa %/gió/độ ẩm, widget cỡ lớn hơn, đổi cỡ tại chỗ.
- Proxy/backend giữ khóa; xoay khóa; bảo vệ chống trích khóa.
- Hàng "Thời tiết" trong Cài đặt (Q5), logo OpenWeather (Q4), bộ biểu tượng của nhà thiết kế (Q3).
- Làm mới nền (WorkManager), Fused Location/Play Services, `ACCESS_FINE_LOCATION`.
- Thư viện HTTP/JSON (OkHttp, Retrofit, kotlinx.serialization): chỉ xét lại khi có nhu cầu mạng lớn hơn; nếu đề xuất sẽ lấy version thật từ Maven lúc đó.
- Test tự động (dự án không làm, AGENTS.md).

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `domain/model/weather/WeatherModels.kt` (`WeatherSnapshot`, `HourlyPoint`, `GeoPlace`, `WeatherCondition`, `WeatherError`, ánh xạ mã OWM) | Tạo | Không |
| `domain/weather/WeatherProvider.kt`, `WeatherRepository.kt` (interface) | Tạo | Không |
| `data/weather/OpenWeatherMapProvider.kt`, `OwmParser.kt`, `WeatherHttp.kt`, `WeatherStore.kt`, `WeatherRepositoryImpl.kt`, `CoarseLocationSource.kt` | Tạo | Không |
| `di/WeatherModule.kt` (bind provider/repository, `WeatherProviderConfig` từ BuildConfig) | Tạo | Không |
| `domain/model/WidgetKind.kt` (thêm `WEATHER`) | Sửa | Không |
| `ui/widget/WeatherIcons.kt`, `WeatherWidgetRenderers.kt`, `WeatherWidgetHub.kt` | Tạo | Không |
| `ui/widget/WidgetRenderer.kt` (nhánh `WEATHER` ở `create`, interface `WeatherDataRenderer`), `WidgetPreviewView.kt` (dữ liệu mẫu), `WidgetTicker.kt` (thêm `tickNow()`) | Sửa nhỏ | Không |
| `ui/home/controller/WidgetPickerController.kt` (`kindNameRes`, thẻ thời tiết) | Sửa nhỏ | Không |
| `ui/weather/WeatherSheetController.kt`, `WeatherSheetViewModel.kt` | Tạo | Không |
| `res/layout/view_weather_sheet.xml`, `item_weather_place.xml` | Tạo | Không |
| `ui/home/controller/HomePagerController.kt` (bấm `WEATHER` mở sheet) | Sửa nhỏ | Không độc quyền |
| `ui/home/HomeViewModel.kt` (một nhánh `WidgetKind.WEATHER -> Unit` trong `onWidgetTapped`) | Sửa (1 dòng) | **Độc quyền — xin phép** (đường lùi: không có; `when` biểu thức bắt buộc đủ nhánh) |
| `ui/home/HomeActivity.kt` (dựng `WeatherSheetController`, đăng ký launcher quyền vị trí, đóng sheet ở `onDestroy`) | Sửa (~6 dòng) | **Độc quyền — xin phép** |
| `LauncherApp.kt` (khởi tạo `WeatherWidgetHub`) | Sửa (~3 dòng) | Không |
| `AndroidManifest.xml` (quyền) | Sửa | Dùng chung (chỉ thêm) |
| `app/build.gradle.kts` (`buildConfig`, `OWM_API_KEY`) | Sửa | Dùng chung (chỉ thêm) |
| `res/values/strings.xml`, `dimens.xml`, `colors.xml` (khối `weather_` ở cuối) | Sửa | Dùng chung (chỉ thêm) |
| `docs/plans/G3e-…md`, `docs/knowledgements/G3e-…md` | Tạo | — |
| **Không đụng:** `data/layout/*`, `data/layout/db/*`, `core/glass/*`, `core/ui/DragHelper.kt`, `domain/layout/*`, `IconGridView`, `IconCellView`, `ui/settings/*`, `data/settings/*`, `app/schemas/*`, `gradle/libs.versions.toml`, shared docs | — | — |

**Room:** **không có thay đổi** (xem D1). `git diff --stat -- app/schemas app/src/main/java/gambi/global/os26launcher/data/layout` phải rỗng ở mọi commit.

## Global Constraints

- Luật `ui → domain ← data`. `domain/weather`, `domain/model/weather` là Kotlin thuần, **không** `android.*`/`org.json`; parse JSON ở `data`. Chuỗi hiển thị ở `strings.xml`, định dạng giờ/nhiệt độ ở `ui/widget`.
- **Không đổi schema Room.** Không thêm thư viện (mọi tool có sẵn). Không đoán version.
- **Không bao giờ ghi vào log, plan, commit, PR hay báo cáo:** khóa API, URL có `appid`, tọa độ. Log chẩn đoán dùng tag `G3E`, chỉ ghi: quyết định chặn (`skip age=…s`), bắt đầu/kết quả (`ok`/`err=<loại>`), số mốc nhận được, thời gian; xóa trước khi tick (grep tiền tố tag + import `Log`).
- Mọi lệnh mạng và đọc/ghi file trên `IoDispatcher`; không bao giờ trên luồng chính. Mọi lỗi (IO, JSON, hết quyền, file hỏng) → `WeatherError` hoặc bỏ cache, **không crash, không ném ra UI**.
- Không `onDraw` cấp phát (`Paint`, `Path`, `StaticLayout`, `Rect`): dựng ở `prepare`/`setData` (bài học GL Task 6, G2c). Cỡ chữ suy từ cạnh ngắn, không `sp` (bài học GL Task 11).
- Widget là `WidgetTicker.Tickable` như G2c: đăng ký khi attach **và** cửa sổ hiện; không giữ `View` của Activity đã hủy; không `Handler`/receiver mới.
- Chặn tần suất lưu ở file (bền qua khởi động lại); thời gian dùng giờ treo tường.
- Quyền vị trí xin **một lần, theo thao tác của người dùng**, không `LocationListener` sống lâu, không dịch vụ.
- Hằng chỉnh tay (30 phút, 5 phút, 60 giây, 2 giờ cũ, cỡ chữ, ngưỡng ẩn dòng) đặt trong `companion object`, ghi "CHỈNH TAY".
- Mọi chuỗi/màu/dimens mới là khối liền `weather_` ở cuối file `res` (Workflow mục 3). Không thêm test, không xóa file, không sửa shared docs, không cài APK khi chưa được bảo.
- Công tắc/seed tạm (trạng thái giả của Task 5) **không vào `main`**: gỡ ở Task 7 và so `git diff <commit đã kiểm> HEAD -- app`.

## Review Focus

1. **Bí mật:** khóa không có trong `git ls-files`/diff/log; `local.properties` vẫn bị ignore; `BuildConfig.OWM_API_KEY` rỗng khi thiếu khóa và build xanh; không log URL/khóa/tọa độ.
2. **Chặn tần suất đúng:** thử mạng sớm hơn 30 phút chỉ xảy ra trong các ngoại lệ đã nêu (đổi thành phố, "Làm mới" ≥ 60 s, lỗi mạng ≥ 5 phút); 401 không bị thử lại; `lastAttemptAt` sống qua khởi động lại; giờ lùi không làm treo.
3. **Chỉ khi Home hiện:** không `Handler`/receiver/`WorkManager` mới; 0 lệnh gọi mạng khi không có widget hiện hoặc khi Home ở nền.
4. **Mọi trạng thái hiển thị đúng:** không khóa, chưa chọn thành phố, đang tải, có dữ liệu, dữ liệu cũ, lỗi mạng/401/429/404; không crash khi JSON thiếu trường/mã lạ.
5. **Tiếng Việt có dấu:** "Hà Nội", "Đà Nẵng" gõ bằng NFC và NFD cho cùng kết quả; đường dự phòng bỏ dấu; URL mã hóa UTF-8.
6. **Vị trí:** một lần, đúng lúc bấm nút; từ chối/không có nhà cung cấp/hết 10 giây đều xử lý; không `LocationListener` sót; tọa độ làm tròn, ngoài sao lưu.
7. **Cache:** khởi động nguội có số ngay (trước khi mạng trả lời); file hỏng/sai version không crash; không vào sao lưu.
8. **Không hồi quy G2c:** widget đồng hồ/lịch, kéo-thả, xóa "−", bảng chọn không đổi hành vi; `git diff --stat` không chạm `data/layout`, `core/glass`, `domain/layout`, `app/schemas`.
9. **Kính/giao diện:** widget thời tiết đọc được trên ảnh nền sáng/tối, kính thật/mờ, sáng/tối hệ thống; không bộ đệm kính mới.
10. **Công tắc/seed tạm đã gỡ**; không file nào ngoài vùng khai.

---

### Task 1: Nền tảng build và quyền — khóa vào BuildConfig, `INTERNET`, `ACCESS_COARSE_LOCATION`
**Bắt buộc kiểm tay:** không (executor tự kiểm bằng build; không dùng máy).
**Files:** `app/build.gradle.kts`, `AndroidManifest.xml`, `res/values/strings.xml` (khối `weather_`).
- [x] **Step 1: Đọc lại** các trang OWM trong bảng "Nguồn dữ liệu" (giá/hạn mức, endpoint, lỗi); sửa plan nếu số đã đổi.
- [x] **Step 2: `buildFeatures { buildConfig = true }`** và `buildConfigField("String", "OWM_API_KEY", "\"<giá trị>\"")` với giá trị đọc bằng `providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText` + `Properties`, thiếu file/thiếu khóa → rỗng; dự phòng `providers.environmentVariable("OWM_API_KEY")`; thoát ký tự `"` và `\` khi ghép chuỗi. Tương thích configuration cache (không `File.readText` thẳng lúc cấu hình).
- [x] **Step 3: Manifest:** thêm `INTERNET`, `ACCESS_COARSE_LOCATION`, `<uses-feature android:name="android.hardware.location" android:required="false"/>` (kiểm bằng tài liệu Android trước khi giữ dòng này). Kèm chú thích tiếng Việt theo phong cách file (lý do từng quyền, nhắc "không có quyền nền").
- [x] **Step 4: Kiểm (không máy):** (a) build xanh khi `local.properties` **không** có khóa; BuildConfig sinh ra có `OWM_API_KEY = ""`; (b) đặt khóa giả `TESTKEY` trong một bản `local.properties` tạm (chỉ trên máy, không commit), build lại: BuildConfig có `TESTKEY`, rồi **xóa dòng tạm**; (c) `git status` không có `local.properties`; `git check-ignore -v local.properties` in luật; (d) `gradlew.bat assembleDebug` chạy lần hai với configuration cache không cảnh báo mới. Commit `build: read the OpenWeatherMap key from local properties and add network permissions`.

### Task 2: Domain — mô hình, `WeatherProvider`, `WeatherRepository`, ánh xạ mã tình trạng
**Bắt buộc kiểm tay:** không.
**Files:** `domain/model/weather/WeatherModels.kt`, `domain/weather/WeatherProvider.kt`, `WeatherRepository.kt`.
**Interfaces:**
- Produces: `GeoPlace(displayName, lat, lon, countryCode?, admin?)`; `WeatherSnapshot(place, tempC: Int, condition: WeatherCondition, isNight: Boolean, highC, lowC, hourly: List<HourlyPoint>, fetchedAtMillis)`; `HourlyPoint(epochSeconds, tempC, condition, isNight)`; `enum WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, THUNDERSTORM, SNOW, FOG, UNKNOWN }` + `fromOwmId(id)`; `sealed WeatherError { NoKey, NoLocation, InvalidKey, RateLimited, NotFound, Network, Server, Parse }`; `interface WeatherProvider { suspend fun search(query, limit): List<GeoPlace>; suspend fun reverse(lat, lon): GeoPlace?; suspend fun fetch(place): WeatherSnapshot }` (lỗi qua `WeatherException(WeatherError)`); `WeatherRepository { val state: StateFlow<WeatherState>; suspend fun search(...); suspend fun select(place); suspend fun refreshIfStale(); suspend fun refreshNow(): …; fun hasKey }`; `WeatherState { Idle/NoKey/NoLocation/Loading/Ready(snapshot, stale, lastError)/Error(error) }`.
- [x] **Step 1:** viết mô hình và bảng `fromOwmId` đúng bảng ở D7 (tài liệu weather-conditions), mã lạ → `UNKNOWN`. Kotlin thuần.
- [x] **Step 2:** interface và KDoc nêu rõ hợp đồng chặn tần suất (D2) để Task 4 hiện thực.
- [x] **Step 3: Kiểm:** build xanh; `grep` không có `android.`/`org.json` trong `domain/weather*`. Commit `feat: add weather domain models and provider interface`.

### Task 3: Data — `OpenWeatherMapProvider` và phép đo mạng thật
**Bắt buộc kiểm tay:** **có (phép đo do điều phối chạy, không cần chủ dự án thao tác trên máy)** — lý do (mục 4.2): Task sau xây trên giả định chỉ mạng thật trả lời được: geocoding có hiểu "Hà Nội"/"Đà Nẵng" không, `/forecast` có chạy với khóa Free không, mã/thân lỗi thật của khóa sai. Sai thì Task 4-6 phải đổi. Cần **khóa thật của chủ dự án** (Q7).
**Files:** `data/weather/WeatherHttp.kt`, `OwmParser.kt`, `OpenWeatherMapProvider.kt`, `di/WeatherModule.kt`.
- [x] **Step 1: `WeatherHttp`** theo D4: `https`, timeout 10 s, đọc tối đa 512 KB, UTF-8, `errorStream`, ánh xạ 401/404/429/5xx/IOException/timeout → `WeatherError`; che `appid` ở mọi log; `disconnect()` khi hủy.
- [x] **Step 2: `OwmParser`** (`org.json`): geocoding (`name`, `local_names.vi`, `lat`, `lon`, `country`, `state`), `/weather` (`weather[0].id/icon`, `main.temp`), `/forecast` (`list[].dt`, `main.temp`, `weather[0].id/icon`); trường thiếu → `Parse`, không NPE; làm tròn nhiệt độ; tính cao/thấp theo D8; `isNight` từ hậu tố `n`.
- [x] **Step 3: `OpenWeatherMapProvider` + `WeatherModule`:** `search` (NFC, mã hóa UTF-8, `limit=5`, rỗng thì thử lại một lần bỏ dấu), `reverse`, `fetch(place)` = hai lệnh (`/weather` rồi `/forecast?cnt=9`) gộp thành `WeatherSnapshot`; `WeatherProviderConfig(baseUrl = "https://api.openweathermap.org", apiKey = BuildConfig.OWM_API_KEY)`; khóa rỗng → ném `NoKey` **trước khi** gọi mạng.
- [x] **Step 4: Phép đo (điều phối, bằng `curl` từ máy dev hoặc harness log tạm tag `G3E`; khóa thật):** (a) `search("Hà Nội")`, `search("Đà Nẵng")`, `search("Ha Noi")`, một tên mơ hồ ("Springfield"): ghi số kết quả, `name`/`local_names.vi`, quốc gia; (b) `fetch` thành phố một: ghi số mốc `/forecast`, kích thước phản hồi, thời gian; (c) khóa sai (`bad`): mã HTTP và thân; (d) bỏ khóa: mã; (e) kết quả vào Nhật ký, **không ghi khóa**. Nếu (a) hỏng với dấu thì xác nhận đường dự phòng bỏ dấu cứu được; nếu cả hai hỏng → dừng, báo điều phối (đổi thiết kế geocoding). Nếu `/forecast` bị từ chối trên gói Free → dừng, báo (D8 và 4×2 phải đổi).
- [x] **Step 5: Gỡ harness**, grep `G3E`/`Log`. Commit `feat: add the OpenWeatherMap provider over HttpURLConnection`.

### Task 4: Repository — cache, chặn tần suất, một-lượt-một-lúc
**Bắt buộc kiểm tay:** không (kiểm bằng logcat trong bài cuối; điều phối gom).
**Files:** `data/weather/WeatherStore.kt`, `WeatherRepositoryImpl.kt`, `CoarseLocationSource.kt` (khung, hiện thực ở Task 6b), `di/WeatherModule.kt`.
- [x] **Step 1: `WeatherStore`** (D3): `AtomicFile` trong `noBackupFilesDir`, JSON `v`/`place`/`snapshot`/`lastAttemptAt`/`lastSuccessAt`, ghi nền gộp ghi (như LC), file hỏng/sai version → bỏ, không crash; tọa độ làm tròn 2 số thập phân khi ghi.
- [x] **Step 2: `WeatherRepositoryImpl`:** nạp file lúc khởi tạo (đọc nền) rồi phát `Ready(stale=true)` ngay nếu có cache (để widget có số khi mạng chưa trả lời); `refreshIfStale()` áp **đúng quy tắc D2** (30 phút/5 phút/401 khóa chết/429 30 phút/giờ lùi), `Mutex` single-flight, `refreshNow()` (≥ 60 s), `select(place)` lưu và làm mới ngay; `NoKey` khi `apiKey` rỗng (không ghi `lastAttemptAt`).
- [x] **Step 3: Log tạm `G3E`:** mỗi lần gọi `refreshIfStale` in một dòng: `skip reason=… ageSec=…` hoặc `start` / `ok n=…` / `err=<loại>`.
- [x] **Step 4: Kiểm (không máy được; điều phối làm ở bài cuối):** build xanh; đọc code đối chiếu bảng quy tắc D2 từng dòng; ghi vào Nhật ký các ca cần kiểm bằng logcat. Commit `feat: add the weather repository with a persisted refresh throttle`.

### Task 5: Vẽ widget — biểu tượng, hai renderer, mọi trạng thái, dữ liệu mẫu (chưa vào bảng chọn)
**Bắt buộc kiểm tay:** không (hiển thị; gộp vào bài kiểm cuối). Điều phối có thể xem sớm bằng công tắc debug nếu muốn.
**Files:** `ui/widget/WeatherIcons.kt`, `WeatherWidgetRenderers.kt`, `WidgetRenderer.kt`, `WidgetPreviewView.kt`, `res/values/*` (khối `weather_`).
- [x] **Step 1: `WeatherIcons`** (D7): tám biểu tượng (+ biến thể đêm cho quang/ít mây, biểu tượng "ngoại tuyến", "!" lỗi, "mây gạch" chưa khóa), vẽ bằng `Path`/hình tròn dựng ở `prepare`, không cấp phát trong `draw`.
- [x] **Step 2: `WeatherSmallRenderer` (2×2) và `WeatherMediumRenderer` (4×2)** theo D8: nhận `WeatherDataRenderer.setData(WeatherState)` + `prepare(...)`; các trạng thái (1)-(6) của D8; chữ trắng + bóng như widget G2c; dòng phụ/dải ẩn khi cạnh ngắn dưới ngưỡng (CHỈNH TAY); `contentDescription` đủ thông tin.
- [x] **Step 3: Chế độ dữ liệu mẫu** cho `WidgetPreviewView` (cố định "Hà Nội 28°, mây, 31/24 và dải mẫu") để bảng chọn không phụ thuộc mạng.
- [x] **Step 4: Công tắc debug tạm (xóa ở Task 7):** vòng qua các trạng thái giả để thấy cả sáu trạng thái mà không cần khóa/mạng, chỉ khi `BuildConfig.DEBUG` + một hằng tạm; ghi điểm đặt trong Nhật ký.
- [x] **Step 5: Kiểm:** build xanh; không `Paint`/`Path`/`StaticLayout` trong `draw` (grep); không `sp`. Commit `feat: draw weather widget renderers and icons`.

### Task 6a: Gắn vào hạ tầng widget — `WidgetKind.WEATHER`, hub làm mới, bảng chọn
**Bắt buộc kiểm tay:** không.
**Files:** `domain/model/WidgetKind.kt`, `ui/widget/WeatherWidgetHub.kt`, `WidgetTicker.kt`, `WidgetRenderer.kt`, `ui/home/controller/WidgetPickerController.kt`, `ui/home/HomeViewModel.kt` (1 dòng, xin phép), `LauncherApp.kt`, `res/values/*`.
- [x] **Step 1:** `WidgetKind.WEATHER("weather", 2×2, 4×2)`; thêm nhánh vào `WidgetRenderers.create`, `kindNameRes`, `onWidgetTapped` (`-> Unit`: nhánh thật chặn ở `HomePagerController`, Task 6b).
- [x] **Step 2: `WeatherWidgetHub`** (object, khởi tạo ở `LauncherApp`): thu `WeatherRepository.state`, mỗi lần đổi gọi `WidgetTicker.tickNow()` để các widget thời tiết đang hiện vẽ lại; `onVisibleTick()` (gọi từ `tickWidget` của ô thời tiết) chạy `repository.refreshIfStale()` trên `ApplicationScope`. Không `Handler`/receiver riêng.
- [x] **Step 3: Bảng chọn:** hai thẻ "Thời tiết 2×2" và "4×2" với dữ liệu mẫu; thêm bằng bấm hoặc kéo như widget G2c (không đổi mã G2c).
- [x] **Step 4: Kiểm:** build xanh; đọc code: 0 `Handler`/receiver mới; `grep` các `when (kind)` đủ nhánh. Commit `feat: register the weather widget in the picker and refresh it while visible`.

### Task 6b: Bottom sheet — nhập thành phố, vị trí thô một lần, làm mới
**Bắt buộc kiểm tay:** **có** — lý do (mục 4.2): giả định chỉ máy thật trả lời được: `LocationManager` có trả vị trí thô trên Live 4 (Android 11) và Oppo (Android 14, hộp quyền của ColorOS) hay không, bàn phím tiếng Việt (NFC/NFD) trong dialog, và luồng xin quyền từ một dialog; sai thì phải đổi cách lấy vị trí (đường lùi: chỉ nhập tay).
**Files:** `ui/weather/WeatherSheetController.kt`, `WeatherSheetViewModel.kt`, `res/layout/view_weather_sheet.xml`, `item_weather_place.xml`, `data/weather/CoarseLocationSource.kt`, `ui/home/controller/HomePagerController.kt`, `ui/home/HomeActivity.kt` (~6 dòng, xin phép).
- [x] **Step 1: Sheet** (D6-A): tiêu đề, dòng "Đang dùng: <tên>", ô nhập (IME action Search, chống gõ trùng), danh sách ≤ 5 kết quả, nút "Dùng vị trí hiện tại", nút "Làm mới" kèm "Cập nhật lúc HH:mm" / lỗi, dòng trạng thái (chưa khóa: vô hiệu hóa ô nhập và giải thích); câu ghi công "Weather data provided by OpenWeather" + liên kết `openweathermap.org` (Q4 về logo).
- [x] **Step 2: `CoarseLocationSource`** (D6): kiểm quyền → `getCurrentLocation(NETWORK_PROVIDER)` (API 30+) hoặc `requestSingleUpdate`/`getLastKnownLocation` (API 26-29), hết hạn 10 s, hủy khi sheet đóng, không listener sống lâu; kết quả → `reverse` lấy tên → `select(place)`.
- [x] **Step 3: Nối:** `HomePagerController` chặn bấm `WEATHER` mở sheet (không qua `viewModel.onWidgetTapped`); `HomeActivity` dựng controller + đăng ký launcher quyền ở `onCreate`, đóng sheet ở `onDestroy`; edit mode vẫn nuốt chạm như G2c.
- [x] **Step 4: Kịch bản kiểm (Live 4 trước, Oppo sau; có khóa thật):** (a) gõ "Hà Nội" rồi "Đà Nẵng" (và "Ha Noi"): ra danh sách đúng, chọn → widget đổi; (b) "Dùng vị trí hiện tại": hộp quyền hiện, **Cho phép** → có tên và số; **Từ chối** → thông báo, lựa chọn cũ giữ nguyên; từ chối rồi bấm lại; (c) tắt dịch vụ vị trí của máy → hết 10 s có thông báo, không treo; (d) đóng sheet giữa chừng khi đang lấy vị trí: không crash, không `LocationListener` sót (`adb shell dumpsys location` không còn request của app); (e) bàn phím không che ô nhập/danh sách; (f) xoay/`recreate()` khi mở sheet: sheet đóng, không crash. Commit `feat: add the weather sheet with typed city search and one-time coarse location`.

### Task 7: Hoàn thiện — ghi công, gỡ công tắc tạm, dọn log
**Bắt buộc kiểm tay:** không.
**Files:** các file của Task 5-6 (dọn), `res/values/*`.
- [x] **Step 1:** gỡ công tắc/seed debug (Task 5 Step 4) và log `G3E` (giữ lại một dòng log lỗi không chứa khóa/tọa độ nếu cần, tag riêng); grep `G3E`, import `Log` thừa, `TESTKEY`, `appid`.
- [x] **Step 2:** rà trạng thái "dữ liệu cũ", `contentDescription`, ghi công ở sheet; rà chuỗi tiếng Việt.
- [x] **Step 3:** `git diff <commit đã kiểm> HEAD -- app` chỉ còn phần xóa tạm; `git diff --stat` không chạm `data/layout`, `core/glass`, `domain/layout`, `app/schemas`. Commit `chore: remove temporary weather debug switches and logging`.

### Task 8: Knowledgement, bằng chứng, đề xuất tài liệu chung
**Bắt buộc kiểm tay:** không.
- [x] Viết `docs/knowledgements/G3e-weather-widget.md` theo mẫu mục 8 (Mermaid: `sequenceDiagram` cho luồng "Home hiện → tick → `refreshIfStale` → provider → store → widget vẽ", `flowchart` cho chặn tần suất và các trạng thái, `sequenceDiagram` cho luồng chọn thành phố/vị trí thô). Điền bảng Review Focus, "Đề xuất cập nhật tài liệu chung". Commit `docs: add G3e knowledgement`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (chính; `FORCE_HIGH` theo ghi nhớ đo, ghi rõ mức kính) và Oppo A3x (kính thật + xem hình; hộp quyền ColorOS). **Bản cài:** nói rõ debug hay release (debug chậm ~4× release trên Oppo — ghi nhớ dự án). **Khóa:** `<KEY>` thật của chủ dự án trong `local.properties` của worktree (khóa mới tạo có thể 401 tới 2 giờ). **Máy dùng chung với việc khác của chủ dự án:** xác nhận máy rảnh trước khi cài hay chạy script. Dùng `adb logcat -s G3E` chỉ trên bản có log (nếu đã dọn ở Task 7, điều phối cài bản harness tạm cho các mục chặn tần suất).

1. **Không khóa:** build không có `OWM_API_KEY` → mở Home, thêm widget Thời tiết 2×2 và 4×2 từ "Tiện ích": hiện "Chưa cấu hình khóa"; sheet giải thích, ô nhập bị vô hiệu; **không có lệnh mạng nào** (logcat không có `start`).
2. **Khóa sai:** `OWM_API_KEY=bad` → chọn thành phố: widget báo lỗi khóa (401), **không thử lại** tới khi khởi động lại; khởi động lại → đúng một lần thử.
3. **Khóa đúng, chưa chọn thành phố:** widget "Chọn thành phố"; bấm → sheet.
4. **Gõ thành phố có dấu:** "Hà Nội", "Đà Nẵng" (cả bàn phím Telex/gõ tổ hợp), "Ha Noi", tên mơ hồ → danh sách; chọn → số hiện trong vài giây; widget 2×2 và 4×2 cùng thành phố.
5. **Dùng vị trí hiện tại:** cho phép → tên gần đúng; từ chối → thông báo; bật/tắt dịch vụ vị trí.
6. **Không mạng / chế độ máy bay:** bật máy bay rồi mở Home (đã có cache): **số cũ hiện ngay**, chuyển "dữ liệu cũ"; tắt máy bay → trong ≤ 5 phút (nhịp phút) tự cập nhật và hết "cũ"; xóa cache chưa có mạng → trạng thái lỗi mạng gọn, không crash.
7. **Khởi động nguội có cache:** `force-stop` rồi mở: số hiện **trước** khi có phản hồi mạng (đối chiếu logcat: `Ready(stale)` rồi mới `start`).
8. **Chặn 30 phút bằng logcat:** mở/đóng Home nhiều lần trong 30 phút → chỉ `skip reason=…`; qua 30 phút (hoặc chỉnh giờ hệ thống) → đúng một `start`; để Home hiện 65 phút → không quá 3 lần thử; Home ở nền → không dòng nào.
9. **Thêm / kéo / xóa widget:** thêm từ bảng chọn (bấm và kéo thẻ), kéo sang trang khác, "−" có xác nhận; sau khi xóa hết widget: không còn nhịp (không `skip`/`start`).
10. **Hai cỡ:** 2×2 và 4×2 cùng lúc; (đổi cỡ = xóa rồi thêm lại, như G2c).
11. **Giao diện:** ảnh nền sáng/tối, giao diện hệ thống sáng/tối, kính thật vs mờ (Oppo/Live 4), chữ đọc được; biểu tượng đúng với vài tình trạng thật (nếu thời tiết hôm đó cho phép) và qua công tắc mẫu nếu còn.
12. **Đổi ngôn ngữ/múi giờ:** mô tả tình trạng, giờ ở dải 4×2 đổi theo máy; không gọi lại mạng chỉ vì đổi ngôn ngữ.
13. **Hồi quy G2c và nền tảng:** widget đồng hồ/lịch, bảng chọn, kéo icon quanh widget, App Library, tìm kiếm, thư mục không đổi; quyền: **Cài đặt hệ thống → Ứng dụng → OS26 Launcher → Quyền** chỉ có Vị trí (khi đã cấp) và không có quyền nền.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Oppo A3x (Android 14), 2026-11-10, `feature/g3e-weather-widget@c982270`, bản debug có khóa: đạt các ca ở knowledgement mục 9 (thêm widget, chọn thành phố, cache khi khởi động nguội, chặn 30 phút, vị trí thô chỉ-lần-này, xóa bằng "−"), không crash. API (máy dev, khóa Free) đạt. **Chưa kiểm:** Live 4, chế độ tối, máy bay/ngoại tuyến, gõ dấu tiếng Việt trên máy, thử lại 5 phút, khóa sai trên máy. Mục 1, 2, 6, 11, 12 của bài kiểm chưa chạy; mục 3, 4 (một phần, gõ "Da Nang"), 5 (một phần), 7, 8 (một phần), 9 (thêm và xóa) đã chạy trên Oppo.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-09 | Plan | Viết plan, đọc tài liệu OpenWeatherMap (current, forecast5, geocoding, one-call-3, price, faq, weather-conditions) | (docs: add G3e plan) | Chờ điều phối rà; 7 câu hỏi cho chủ dự án ở "Câu hỏi" |
| 2026-11-09 | Plan duyệt | Chủ dự án duyệt plan, trả lời Q1-Q7 (mục "Trả lời của chủ dự án") | 8452e9a | Khóa nằm ở local.properties của thư mục chính; kích hoạt khoảng 17:30; đo mạng ngày kế |
| 2026-11-09 | Task 1 | `OWM_API_KEY` từ local.properties (hoặc biến môi trường) vào BuildConfig qua `providers.fileContents`, `INTERNET`, `ACCESS_COARSE_LOCATION`, `uses-feature location required=false` (giữ dòng này: quyền vị trí thô kéo theo yêu cầu phần cứng nếu không khai, suy luận từ tài liệu Android, chưa kiểm trên Play Console) | 8452e9a | Build xanh không khóa: BuildConfig `""`; có khóa: không rỗng (kiểm bằng cách thay giá trị bằng `<nonempty>`, không in khóa); `assembleDebug lintDebug` xanh, cấu hình cache không cảnh báo mới |
| 2026-11-09 | Task 2, 3 (code) | Mô hình domain, `WeatherProvider`/`WeatherRepository`, ánh xạ mã OWM; `OpenWeatherMapProvider`, `OwmParser`, `WeatherHttp` (HttpURLConnection + org.json), `WeatherModule` | 9643ed8 | **Phép đo mạng của Task 3 (Step 4) CHƯA làm**: agent không gọi API OWM; điều phối làm theo knowledgement mục 7.A. Không có harness (không cần) |
| 2026-11-09 | Task 4 | `WeatherStore` (weather.json, noBackupFilesDir, AtomicFile), `WeatherRepositoryImpl` (30 phút / 5 phút / 429 / 401 chết, single-flight, giờ lùi) | f77c9a7 | Các dòng log `G3E` (skip/start/ok/err/select/cache loaded) KHÔNG chứa khóa, URL, tọa độ và được GIỮ (đổi so với plan, nơi Task 7 nói dọn log): cần để điều phối kiểm chặn 30 phút bằng logcat. Quyết định này xin điều phối xác nhận |
| 2026-11-09 | Task 5 | `WeatherIcons` (Canvas, Path dựng một lần), `WeatherRenderer` 2x2/4x2 sáu trạng thái, `WeatherWidgetHub`, `WidgetTicker.tickNow()`, chuỗi/màu `weather_` | c0ff6b1 | Đổi so với plan: `WeatherWidgetHub` và `tickNow` làm ở Task 5 (renderer cần hub), không ở Task 6a. Công tắc debug tạm (mỗi phút đổi trạng thái giả) đã gỡ ở Task 7 |
| 2026-11-09 | Task 6a | `WidgetKind.WEATHER`, `WidgetRenderers.create(sample)`, thẻ trong bảng chọn (dữ liệu mẫu), hub khởi tạo ở `LauncherApp` | 1b468ab | **HomeViewModel.kt: +1 dòng** (`WidgetKind.WEATHER -> true` trong `onWidgetTapped`) |
| 2026-11-09 | Task 6b | `CoarseLocationSource`, `WeatherSheetViewModel`, `WeatherSheetController`, `view_weather_sheet.xml`, `item_weather_place.xml`; pager chặn bấm WEATHER | 9c601da | **HomeActivity.kt: +5 dòng** (import, field, dựng controller và truyền `onWeatherClick`, `onDestroy`). **Step 4 (kiểm tay, bắt buộc) CHƯA chạy**; chỉ đọc code |
| 2026-11-09 | Task 7 | Gỡ công tắc debug; kiểm grep: không `TESTKEY`/`appid` ngoài provider, không công tắc | fd36ea8 | Build không khóa xanh (BuildConfig `""`); `assembleDebug lintDebug` xanh |
| 2026-11-09 | Task 8 | Knowledgement | (docs: add G3e knowledgement) | Mục 7 có lệnh đo mạng và bài kiểm; mục 9 để trống chờ số đo |
| 2026-11-10 | Kiểm API | Điều phối kiểm API với khóa thật (gói Free) trên máy dev | — | Hà Nội/Ha Noi ra Hà Nội có `local_names.vi`; Đà Nẵng ra 3 kết quả; `/weather` 200 (500 B, `name` là tên trạm "Xom Pho" nên dùng tên geocoding là đúng); `/forecast` cnt=9 200 (3,9 KB, 9 mốc, chạy với khóa Free); reverse 200; khóa sai và không khóa 401 |
| 2026-11-10 | Kiểm Oppo | Oppo A3x (Android 14), debug từ `c982270` | — | Đạt các ca ở knowledgement mục 9. Vị trí thô "Chỉ lần này": 0 vị trí mới trong 10 giây rồi lấy được thời tiết, nhiều khả năng qua `getLastKnownLocation` (đọc code `CoarseLocationSource.getOnce`, suy luận). Chưa kiểm Live 4, chế độ tối, ngoại tuyến, gõ dấu trên máy, thử lại 5 phút, khóa sai |
| 2026-11-10 | Gỡ log | Gỡ mọi `Log.d` tag `G3E` (đúng thỏa thuận trước merge); giữ `Log.w` của `WeatherStore` không chứa URL/khóa/tọa độ | (chore: drop the G3E debug logs) | Thay quyết định "giữ log" ở dòng Task 4 ngày 2026-11-09. Build `assembleDebug lintDebug` xanh |

## Trả lời của chủ dự án (2026-11-09, điều phối chuyển)

- **Q1:** cao/thấp = 24 giờ tới là được; **nhãn trên widget/sheet phải ghi rõ "24 giờ tới"** để không bị hiểu là cao/thấp trong ngày.
- **Q2:** lỗi mạng thử lại sau 5 phút; 401 không thử lại tới khi khóa đổi hoặc tiến trình khởi động lại.
- **Q3:** biểu tượng tự vẽ Canvas.
- **Q4:** bản đầu chỉ câu ghi công + liên kết; **logo và điều khoản giấy phép kiểm trước khi phát hành, ngoài phạm vi nhánh này**.
- **Q5:** không có hàng Cài đặt ở v1.
- **Q6:** điều phối xác nhận không cần khóa schema (không đổi Room). Được sửa tối thiểu `HomeViewModel.kt` (một nhánh `when`) và `HomeActivity.kt` (~6 dòng); phải nêu đúng đã đổi gì.
- **Q7:** khóa nằm ở `E:\SourceTree\IosLauncher\local.properties`, kích hoạt khoảng 17:30 ngày 2026-11-09; kiểm bằng khóa thật vào ngày kế. **Agent không gọi API OWM và không in/ghi khóa ở bất cứ đâu**; phép đo mạng của Task 3 do điều phối làm theo hướng dẫn trong Nhật ký.

## Câu hỏi cho chủ dự án (đã trả lời ở trên)

- **Q1.** Cao/thấp = "24 giờ tới" (gói Free không có cao/thấp cả ngày dương lịch) có được không? Phương án khác: cao/thấp trong ngày địa phương, nhưng buổi tối chỉ còn 1-2 mốc nên gần như bằng nhiệt độ hiện tại.
- **Q2.** Sau lỗi mạng cho phép thử lại sau 5 phút (thay vì 30) có được không? Đó là ngoại lệ duy nhất cho "tối đa mỗi 30 phút", để widget tự hồi phục sau khi mất mạng; nếu không, bấm "Làm mới" trong sheet là cách duy nhất trong 30 phút.
- **Q3.** Biểu tượng thời tiết tự vẽ bằng Canvas (bản đầu) có chấp nhận về chất lượng thị giác không, hay có designer cấp bộ `VectorDrawable`?
- **Q4.** Ghi công OpenWeather: chỉ câu chữ + liên kết trong sheet (đề xuất) hay cần cả logo? Điều khoản logo/trang attribution chưa đọc đủ; và bản phát hành thương mại có cần gói trả phí hay không là việc của bạn/lead (mục 5 của `Launcher-Features.md` còn mở).
- **Q5.** Có cần thêm hàng "Thời tiết" trong Cài đặt (mở cùng sheet) ở bản đầu không? Plan để ngoài phạm vi.
- **Q6.** (cho điều phối) Xác nhận không cần khóa schema vì `className = "weather"` không đặt nghĩa mới cho `itemType`.
- **Q7.** Ai cung cấp khóa OWM thật cho Task 3 và bài kiểm cuối, và có đã kích hoạt (tới 2 giờ sau khi đăng ký) chưa? Dự án không có test tự động nên thiếu khóa thì chỉ kiểm được các trạng thái không-khóa (mục 1 của bài kiểm).

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1-10 | (điền khi đóng nhánh) | | |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#14 → "Một phần/Xong" (widget Thời tiết 2×2/4×2, OpenWeatherMap, một vị trí); ghi quyết định mục 2 dòng 26-29 đã chốt (mở băng, nguồn OWM, khóa trong APK chấp nhận); TT#17 vẫn "Đóng băng"; mục 5 cập nhật còn mở: điều khoản OWM bản phát hành, proxy/backend.
- `project_structure.md`: thêm `domain/weather`, `data/weather`, `ui/weather`, `ui/widget/Weather*`; bảng mốc G3e.
- `room-schema.md`: **không đổi** (ghi chú: `className = "weather"` dưới `itemType = 4`).
- `AGENTS.md`: xem "Bài học cho AGENTS.md" trong knowledgement (khóa API qua `local.properties`/configuration cache; app lần đầu có quyền mạng; lọc log khóa).
- `MultiAgent-Workflow.md`: dòng G3e ở mục 9 và bảng trạng thái mục 10 (`Đóng băng` → đang làm; `Khóa`: không; `cần Oppo`: bài cuối).
