# BW — Widget Pin (tự vẽ) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code liền các Task, chủ dự án build và kiểm một lượt; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án trả lời Q1–Q6 + 2 yêu cầu thêm)
**Trạng thái:** Xong 2026-11-26 (Task 1–3; chủ dự án build và kiểm đạt)
**Nhánh:** `an` · **Máy:** Oppo PKD130 (Android 14), Vsmart Live 4 (Android 11) · **Đổi schema Room:** không (`itemType = 4`, `className = "battery"`)
**Nguồn:** `BACKLOG.md` B3 (phần widget Pin của `IosLauncher/docs/plans/FL-first-run-layout.md` Task 1, chưa duyệt bên XML); yêu cầu chủ dự án 2026-11-25.

**Goal:** Thêm widget tự vẽ "Pin" kiểu iOS: hiện % pin của máy, trạng thái sạc, tiết kiệm pin; thêm được từ bảng chọn (Home, Today), kéo được như widget tự vẽ khác; bấm mở màn dùng pin của hệ thống.

## Hiện trạng (đọc code 2026-11-25)

- Widget tự vẽ: `WidgetKind` (CLOCK, CALENDAR, WEATHER, PHOTO, RECENTS), renderer Canvas qua `WidgetRenderers.create` (`ui/widget/`), ô `HomeWidgetCell` (thẻ vuông kiểu iOS, PW), nhịp vẽ lại `WidgetTicker` (mỗi phút + `tickNow()`), font Inter (`WidgetFont`, PW). Dữ liệu ngoài theo mẫu hub (`WeatherWidgetHub`: renderer đọc trạng thái ở `prepare`, hub gọi `WidgetTicker.tickNow()` khi đổi).
- Bảng chọn (`HomeWidgetPicker`): ô gợi ý `FEATURED` dùng chung Home và Today; trang chi tiết theo `WidgetKind.sizes`; thêm / kéo ra lưới đã có cho mọi loại tự vẽ.
- Mở màn pin đã có mẫu ở Trung tâm điều khiển (`SystemIntents`: `Settings.ACTION_BATTERY_SAVER_SETTINGS`, `Intent.ACTION_POWER_USAGE_SUMMARY`).
- Bố cục lần đầu (WF) đang seed Đồng hồ + Lịch; FL bản XML dự tính Đồng hồ + Pin (xem Q5).

## Thiết kế đề xuất

- **Dữ liệu:** `BatteryWidgetHub` (`ui/widget/`, như `WeatherWidgetHub`/`WidgetTicker`): đăng ký receiver động `ACTION_BATTERY_CHANGED` (sticky, không cần quyền) + `PowerManager.ACTION_POWER_SAVE_MODE_CHANGED` **chỉ khi có ít nhất một widget Pin đang hiện** (đếm tham chiếu từ `HomeWidgetCell` khi `kind == BATTERY`), gỡ khi không còn. Chỉ gọi `WidgetTicker.tickNow()` khi %, sạc hoặc tiết kiệm pin thật sự đổi (broadcast pin gửi dày lúc sạc). Không thêm tầng domain / repository: dữ liệu là broadcast hệ thống chỉ widget dùng (như `WidgetTicker` nghe đổi giờ).
- **Vẽ (Q1, theo widget "Pin" của iOS):** thẻ kính như widget khác; góc trên trái một vòng tròn tiến độ quanh biểu tượng điện thoại; dưới là số % lớn (Inter Display Medium, `tnum`). Màu vòng (quy ước iOS, tra 2026-11-26): tiết kiệm pin vàng (ưu tiên, kể cả khi sạc); đang sạc xanh lá; ≤ 20 % đỏ; còn lại xanh lá. Đang sạc: tia sét bên phải vòng. Bản xem trước trong bảng chọn dùng dữ liệu thật (yêu cầu thêm b).
- **Bấm (Q4):** mở `Intent.ACTION_POWER_USAGE_SUMMARY`, máy không có thì `Settings.ACTION_BATTERY_SAVER_SETTINGS`, không có nữa thì Toast chung như Đồng hồ / Lịch.
- **Bảng chọn:** `FEATURED` = Đồng hồ, Lịch, Thời tiết, Pin (2 × 2), Ảnh 4 × 2, "App gần đây" 4 × 2 (Home và Today); trang chi tiết của Pin là pager một trang.
- **Chuỗi:** tên, tiêu đề / mô tả biến thể, câu đọc TalkBack ("Pin 85 %, đang sạc") đủ 9 ngôn ngữ.

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Vẽ theo đâu? Figma chưa có widget Pin | Như widget "Pin" của iOS (mô tả ở "Thiết kế đề xuất"); chỉnh mắt sau khi xem trên máy | Kết hợp đề xuất và ảnh trên mạng |
| Q2 | Cỡ nào? | Chỉ 2 × 2. Bản 4 × 2 của iOS là danh sách nhiều thiết bị; chỉ có pin máy thì 4 × 2 trống trải | 2 × 2, vẫn có trang chi tiết (pager một trang) |
| Q3 | Có hiện pin tai nghe / đồng hồ Bluetooth như iOS không? | Không ở bản này (cần quyền Bluetooth, mỗi hãng một kiểu báo pin) | Theo đề xuất |
| Q4 | Bấm widget làm gì? | Mở màn dùng pin của hệ thống (dự phòng: màn tiết kiệm pin) | Theo đề xuất |
| Q5 | Bố cục lần đầu đổi Lịch thành Pin (như FL / app mẫu) không? | Giữ Đồng hồ + Lịch như WF đã kiểm; Pin chỉ thêm từ bảng chọn | Theo đề xuất |
| Q6 | Có cho thêm Pin vào Today không? | Có (iOS có widget Pin ở Today); bảng của Today dùng chung danh sách ô gợi ý | Theo đề xuất |

**Yêu cầu thêm (chủ dự án 2026-11-25):** (a) trong bảng chọn, ô Pin 2 × 2 đứng vào chỗ ô Ảnh (cạnh Thời tiết); ô Ảnh chuyển thành 4 × 2, đặt trên ô "App gần đây"; (b) bản xem trước Pin trong bảng chọn dùng **dữ liệu thật** (không số mẫu): bảng mở thì hub cũng nghe pin, số đổi thì xem trước vẽ lại.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `domain/model/WidgetKind.kt` (`BATTERY("battery", [2×2])`) | Sửa | 1 |
| `ui/widget/BatteryWidgetHub.kt`, `ui/widget/BatteryWidgetRenderer.kt` | Tạo | 1 |
| `ui/widget/WidgetRenderer.kt` (`create`), `ui/home/compose/HomeWidgetCell.kt` (đếm tham chiếu hub), `ui/home/HomeViewModel.kt` (`onWidgetTapped`), `ui/home/HomeActivity.kt` (`openBatteryUsage`), `res/values/colors.xml` (`battery_ring_*`) | Sửa | 1 |
| `ui/home/compose/HomeWidgetPicker.kt` (`FEATURED`: Pin chỗ Ảnh, Ảnh 4 × 2; tên / tiêu đề / mô tả; xem trước Pin dùng hub) | Sửa | 2 |
| `res/values*/strings.xml` (9 file) | Sửa | 2 |
| `docs/Launcher-Features.md`, `docs/room-schema.md` (`className = "battery"`), `docs/project_structure.md`, `lessons/widgets.md`, `plans/BACKLOG.md` | Sửa | 3 |

## Global Constraints

- Receiver pin chỉ sống khi có widget Pin hiện (không giữ suốt đời process); không cấp phát trong `draw` (luật GL Task 6); renderer đọc trạng thái ở `prepare`.
- Không đổi schema; không chuỗi cứng; giữ CRLF; không viết `/*` trong comment Kotlin.

---

### Task 1: Dữ liệu pin + renderer
**Bắt buộc kiểm tay:** có (Live 4 + Oppo: broadcast pin / tiết kiệm pin của từng ROM).
- [x] Step 1: `WidgetKind.BATTERY`; mọi `when` đủ nhánh.
- [x] Step 2: `BatteryWidgetHub` (đếm tham chiếu, receiver động, lọc trùng, `tickNow`).
- [x] Step 3: `BatteryWidgetRenderer` (vòng tiến độ, glyph điện thoại, số %, tia sét, màu theo Q1; dữ liệu thật cả trong bảng chọn) + bấm mở màn pin (`HomeActivity.openBatteryUsage`).

### Task 2: Bảng chọn + chuỗi
- [x] Step 1: ô "Pin" trong `FEATURED`, tên / biến thể; chuỗi 9 ngôn ngữ.
- [x] Step 2: Kịch bản kiểm: thêm Pin từ bảng chọn Home và Today (bấm "Thêm", kéo ra lưới); số % khớp thanh trạng thái; cắm / rút sạc: tia sét hiện / ẩn trong vài giây; bật tiết kiệm pin: vòng vàng; pin ≤ 20 %: đỏ; bấm mở màn pin; tắt / mở màn hình, vào Cài đặt rồi về: số vẫn đúng; gỡ hết widget Pin rồi `adb shell dumpsys activity broadcasts | findstr /i os26launcher` không còn receiver pin.

### Task 3: Docs
- [x] Step 1: `Launcher-Features.md` (dòng widget), `room-schema.md`, `project_structure.md`, bài học, `BACKLOG.md` (B3: tách phần Pin xong).

## Nhật ký

| Ngày | Task | Việc | Kết quả |
|---|---|---|---|
| 2026-11-25 | — | Đọc `WidgetKind`, renderer, `WeatherWidgetHub`, `WidgetTicker`, bảng chọn, FL bản XML; viết plan | Chờ duyệt Q1–Q6 |
| 2026-11-26 | — | Chủ dự án kiểm đạt ("ok") | Đóng |
| 2026-11-26 | 1 | Chủ dự án kiểm: dời tia sét sạc lên cạnh vòng ("100%" không đủ chỗ dưới); tra quy ước màu: iOS tiết kiệm pin vàng (kể cả khi sạc), sạc xanh lá, ≤ 20 % đỏ; Android AOSP cảnh báo 15 % (`config_lowBatteryWarningLevel`, nhiều ROM 20) → giữ 20. Sửa `BatteryWidgetRenderer` | Chờ build + kiểm |
| 2026-11-25 | 1–3 | Chủ dự án trả lời Q1–Q6 + 2 yêu cầu thêm. Code `WidgetKind.BATTERY`, `BatteryWidgetHub`, `BatteryWidgetRenderer`, bảng chọn, chuỗi 9 ngôn ngữ, docs | Chờ build + kiểm |
