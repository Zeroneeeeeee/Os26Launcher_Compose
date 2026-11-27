# WM — Thời tiết: Open-Meteo làm nguồn dự phòng khi OpenWeatherMap lỗi — Implementation Plan

> Làm theo `docs/AGENTS.md`. Mỗi Task một commit. VM không có Android SDK: commit chỉ được rà bằng đọc code. Không có test tự động.

**Bắt đầu:** 2026-11-27 · **Plan duyệt:** 2026-11-27 09:52 (chủ dự án: "vậy là được rồi … làm luôn"; duyệt cả P1–P7)
**Trạng thái:** Đang làm

**Goal:** Khi OpenWeatherMap (OWM) không trả được dữ liệu (429 hết hạn mức, 401 khóa bị khóa, lỗi máy chủ), app lấy thời tiết từ Open-Meteo để widget,
bảng và thông báo vẫn có số liệu. Bộ icon giữ nguyên (nhóm `WeatherCondition` hiện có), chỉ thay nguồn số liệu.
**Architecture:** Thêm `OpenMeteoProvider` (chỉ `fetch`) và `FallbackWeatherProvider` (bọc OWM + Open-Meteo) cài `WeatherProvider`; `WeatherModule` bind
bản bọc. `WeatherRepositoryImpl`, widget, bảng, thông báo không đổi. Địa chỉ máy chủ + khóa Open-Meteo nằm trong `buildTypes` của
`app/build.gradle.kts` (như khóa OWM): mua gói thương mại thì chỉ đổi 2 dòng cấu hình.
**Spec:** chủ dự án / anh lead 2026-11-27 09:31 và 09:37 (chat); backlog B23.

## Quyết định đã chốt (chủ dự án / anh lead)

| # | Quyết định |
|---|---|
| 1 | Nguồn thứ 2 là Open-Meteo, dùng bản miễn phí (`api.open-meteo.com`) làm dự phòng |
| 2 | Kiểu xoay vòng: OWM là nguồn chính; chỉ chuyển sang Open-Meteo khi OWM lỗi (không luân phiên đều) |
| 3 | Giữ nguyên bộ icon; Open-Meteo chỉ cấp số liệu (mã WMO quy về `WeatherCondition`) |
| 4 | Không ghi nguồn Open-Meteo trên giao diện |
| 5 | Địa chỉ máy chủ + khóa Open-Meteo đặt ở `buildTypes`; khóa rỗng = gói miễn phí |

## Rủi ro đã chấp nhận (anh lead, 2026-11-27; chủ dự án đã đọc phân tích trong chat)

- Điều khoản Open-Meteo: bản miễn phí chỉ cho dùng phi thương mại; app có quảng cáo bị tính là thương mại. Bản miễn phí đi kèm giấy phép CC BY 4.0
  (yêu cầu ghi nguồn), app không ghi nguồn (QĐ 4).
- Open-Meteo có thể chặn app hoặc IP không báo trước; khi đó app quay về chỉ còn OWM (không crash, như trước WM).
- Gỡ rủi ro: mua gói thương mại → đổi `OPEN_METEO_BASE_URL` sang `https://customer-api.open-meteo.com` và điền `OPEN_METEO_API_KEY` (Task 1).

## Đề xuất của Claude (cần chủ dự án duyệt cùng plan)

| # | Đề xuất | Vì sao | Sai thì tốn |
|---|---|---|---|
| P1 | Lỗi OWM nào thì chuyển nguồn: `RATE_LIMITED` (429), `INVALID_KEY` (401), `SERVER` (5xx), `PARSE`. KHÔNG chuyển khi `NETWORK` (mất mạng: nguồn nào cũng hỏng) và `NOT_FOUND` | Chỉ chuyển khi lỗi nằm ở phía OWM | Sửa một tập lỗi |
| P2 | "Bám" Open-Meteo (gọi thẳng, không thử OWM): sau 429 → 30 phút; sau 401 → tới khi tiến trình khởi động lại; 5xx / parse → không bám (lần sau thử OWM trước). Chỉ trong RAM | Không đốt thêm hạn mức OWM khi đã biết nó đang chặn; 401 thì khóa chết tới khi ra bản mới | Đổi hằng |
| P3 | Chỉ `fetch` (thời tiết) có dự phòng. Tìm thành phố (`search`) và tra tên nơi (`reverse`) vẫn chỉ OWM: Open-Meteo không có tra tên từ tọa độ, và QĐ 3 nói "chỉ cần dữ liệu". OWM chết thì "Vị trí hiện tại" giữ nơi cũ (đã có sẵn: `CurrentLocationUpdater` lỗi thì thử lại sau 15 phút) | Ít việc nhất, đúng phạm vi | Thêm geocoding Open-Meteo cho `search` (1 Task) |
| P4 | Dự báo Open-Meteo theo giờ → lấy mỗi 3 giờ một điểm, 12 điểm (36 giờ), giống `data/2.5/forecast` của OWM | Thông báo (WN, khung 3 giờ `SLOT_SECONDS`) và cao/thấp (8 điểm = 24 giờ) giữ đúng nghĩa | — |
| P5 | Repository không biết nguồn nào trả: kết quả Open-Meteo tính là thành công như thường; lần tự làm mới kế theo chu kỳ "Làm mới sau" và thử OWM trước (trừ khi đang bám) | Không đổi hợp đồng chặn tần suất, không đổi `weather.json` | — |
| P6 | Một dòng log `Log.i` (tag `WeatherFallback`) khi chuyển nguồn: chỉ tên lỗi OWM, không URL / tọa độ / khóa | Luật log mạng ở `lessons/widgets.md`; cần để kiểm trên máy | Gỡ một dòng |
| P7 | Mã WMO → `WeatherCondition`: 0 → CLEAR; 1, 2 → PARTLY_CLOUDY; 3 → CLOUDY; 45, 48 → FOG; 51–57, 61–67, 80–82 → RAIN; 71–77, 85, 86 → SNOW; 95, 96, 99 → THUNDERSTORM; mã khác → UNKNOWN. Đêm = `is_day == 0` | Theo bảng mã WMO trong tài liệu Open-Meteo; cùng độ mịn với ánh xạ OWM hiện có | Sửa bảng |

## Vùng file

- Tạo: `data/weather/OpenMeteoProvider.kt`, `data/weather/OpenMeteoParser.kt`, `data/weather/FallbackWeatherProvider.kt`
- Sửa: `app/build.gradle.kts` (2 `buildConfigField` mỗi build type), `di/WeatherModule.kt` (bind bản bọc, cấu hình Open-Meteo),
  `domain/model/weather/WeatherModels.kt` (`WeatherCondition.fromWmoCode`), `domain/weather/WeatherProvider.kt` (KDoc)
- Docs: `Launcher-Features.md` (dòng "Nguồn dữ liệu thời tiết"), `lessons/widgets.md`, `plans/BACKLOG.md` (B23), `plans/README.md`

## Task

### Task 1: Open-Meteo provider + cấu hình
- [x] `build.gradle.kts`: `OPEN_METEO_BASE_URL` = `"https://api.open-meteo.com"`, `OPEN_METEO_API_KEY` = `""` cho debug / release / perf (chú thích: mua
      gói thương mại thì đổi sang `https://customer-api.open-meteo.com` + điền khóa).
- [x] `OpenMeteoProvider(config: OpenMeteoConfig, io)`: `fetch` gọi MỘT lệnh `v1/forecast?latitude=&longitude=&current=temperature_2m,weather_code,is_day`
      `&hourly=temperature_2m,weather_code,is_day&forecast_hours=36&timezone=auto&timeformat=unixtime` (+ `&apikey=` khi khóa không rỗng); `search` trả
      rỗng, `reverse` trả null (P3). Dùng lại `WeatherHttp.get` (mã lỗi HTTP → `WeatherError` như OWM).
- [x] `OpenMeteoParser.snapshot(place, json, nowMillis)`: `current` → nhiệt độ / tình trạng / đêm; `hourly` → mỗi 3 giờ một `HourlyPoint`, tối đa 12 (P4);
      cao/thấp trên 8 điểm đầu + hiện tại (như `OwmParser`); `utc_offset_seconds` → `utcOffsetSeconds`; NaN → `PARSE`.
- [x] `WeatherCondition.fromWmoCode(code)` (P7). Commit `feat: Open-Meteo weather provider`.

### Task 2: Bản bọc dự phòng
- [ ] `FallbackWeatherProvider(primary: OpenWeatherMapProvider, backup: OpenMeteoProvider)`: `fetch` thử OWM, lỗi thuộc P1 → Open-Meteo; Open-Meteo cũng
      lỗi → ném lỗi của OWM (widget hiện lỗi như hiện nay). Bám theo P2 (`@Volatile` mốc thời gian + cờ 401). `search` / `reverse` gọi thẳng OWM.
- [ ] `WeatherModule`: bind `FallbackWeatherProvider`; cung cấp `OpenMeteoConfig` từ `BuildConfig`. Log P6. Commit `feat: fall back to Open-Meteo when OWM fails`.

### Task 3: Docs
- [ ] `Launcher-Features.md`, `lessons/widgets.md` (mục WM), `BACKLOG.md` (B23 chờ kiểm), `plans/README.md`. Commit `docs: WM weather fallback`.

## Cách kiểm (chủ dự án)

1. Bình thường: thời tiết vẫn từ OWM; Logcat không có dòng `WeatherFallback`.
2. Giả OWM chết: đặt `OWM_API_KEY=sai` trong `local.properties` (ghi đè khóa, có sẵn) → build → widget vẫn có số liệu; Logcat có
   `WeatherFallback … INVALID_KEY`. Xóa dòng đó sau khi thử.
3. Tắt mạng: widget báo lỗi mạng như cũ (không chuyển nguồn).
4. Thông báo 07:00 / 21:00 khi đang dùng Open-Meteo: nội dung mưa / nhiệt độ hợp lý.

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-27 | 1 | `OpenMeteoProvider` + `OpenMeteoConfig` (`fetch` 1 lệnh; `search` rỗng, `reverse` null); `OpenMeteoParser` (mốc giờ kế tiếp, mỗi 3 giờ, 12 mốc); `WeatherCondition.fromWmoCode` (thêm 97 vào giông theo bảng WMO); `OPEN_METEO_BASE_URL` / `OPEN_METEO_API_KEY` × 3 build type; `WeatherModule.provideOpenMeteoConfig`; `OwmParser.HIGH_LOW_STEPS` bỏ `private` | Chưa build. Ruling: mốc đầu từ chỉ số 1 (giờ hiện tại đã qua một phần, OWM trả mốc tương lai) |
