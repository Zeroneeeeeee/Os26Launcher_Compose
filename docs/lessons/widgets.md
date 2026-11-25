# Bài học: Widget

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Widget Đồng hồ/Lịch và widget trên lưới.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## G2c — widget trên lưới: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x)

- **Thêm một loại item vào bảng phẳng: rà MỌI `as PlacedApp`, `when` trên `PlacedItem` và guard `spanX > 1`.** Có chỗ thoát sớm toàn lưới (`reflow`, `deleteAndCompact`) che lỗi tới khi có item nhiều ô. Bảng "đường bố cục gặp item nhiều ô" trong plan G2c là mẫu rà soát.
- **Hàm thuần cho phép chứng minh không hồi quy:** giữ nguyên văn hàm cũ trong harness tạm, so vài nghìn đầu vào ngẫu nhiên (đo được: 3000 trang không có widget khớp hoàn toàn, 4000 trang có widget không vi phạm bất biến).
- **Vị trí kính tại chỗ tính lúc ghi display list (đo được trên Live 4, nguyên nhân đọc code):** view trong trang chưa hiện của `ViewPager2` phải được `invalidate()` khi pager cuộn, nếu không shader lấy mẫu ngoài bản mờ, CLAMP thành vệt ngang.
- **Chuỗi chạm gắn với cửa sổ nhận `ACTION_DOWN`:** muốn kéo từ Dialog ra Activity thì giữ cửa sổ, làm mờ nó, chuyển sự kiện tới `Activity.dispatchTouchEvent`, đóng sau khi thả (chạy được trên Live 4 và Oppo).
- **`ACTION_CANCEL` không phải thả:** phiên kéo bị hệ thống ngắt phải hủy; nếu coi là thả thì ghi dữ liệu người dùng chưa quyết. Mọi đường kết thúc phiên kéo phải gọi `onFinished`, kể cả sheet bị đóng và Activity bị hủy.
- **Đọc code, không đọc chú thích:** KDoc `GridInsertPlanner` và chú thích "bay về" đều sai so với code.
- **Kiểm xóa app mà không gỡ app thật:** trong bản sao DB, trỏ một hàng tới package không tồn tại; lúc mở app `catchUp` coi nó đã gỡ và chạy đúng đường `deleteAndCompact`. `removePackages` không được chạy theo cách này.
- **Rebase nhánh có commit merge:** `git diff <đầu cũ> HEAD -- app` phải rỗng; xung đột giải lúc rebase có thể đổi thứ tự khai báo, khi đó lấy lại file từ đầu cũ.
- **Re-flow đổi cỡ lưới nén khe nên không đảo ngược được:** icon đủ nhưng widget có thể về ô khác; đây là thiết kế M8a.
- **Agent không được chờ bằng vòng lặp shell dựa trên `java.exe`:** VS Code luôn có Gradle server chạy nên vòng lặp không thoát; một agent kẹt ~2.5 giờ. Điều phối phải kiểm commit thật thay vì báo "đang làm".

## B1 — widget Đồng hồ/Lịch (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **B1 — widget Đồng hồ/Lịch:** `AlarmClock.ACTION_SHOW_ALARMS` trên Oppo có 3 trình xử lý, chỉ `com.coloros.alarmclock` mang cờ `SYSTEM`. Mở bằng `startSystemHandler` (lọc `FLAG_SYSTEM`, bỏ `ResolverActivity`, mở bằng component cụ thể) thì không còn hộp chọn app, cùng cách M8d dùng cho Cài đặt. Đo: Oppo 3/3 lượt cho cả Đồng hồ và Lịch.

## G3e — widget thời tiết (2026-11-09/10, Oppo A3x + Vsmart Live 4)

- **Khóa API trong build:** đọc `local.properties` bằng `providers.fileContents(...).asText` (an toàn với configuration cache) vào `BuildConfig`; thiếu khóa thì chuỗi rỗng, build vẫn xanh, widget báo "Chưa cấu hình khóa". Không bao giờ in khóa ra console, log, commit; khóa nhúng trong APK coi như có thể bị trích ra, thiết kế để đổi sang proxy bằng một dòng DI.
- **OpenWeatherMap gói Free (đo được 2026-11-10):** `/data/2.5/weather`, `/data/2.5/forecast?cnt=9`, `/geo/1.0/direct` và `/geo/1.0/reverse` đều chạy; khóa sai hoặc thiếu trả 401. `weather?q=` đã deprecated, dùng geocoding rồi lat/lon. `name` trong `/weather` có thể là tên thôn ("Xom Pho" cho tọa độ Hà Nội): hiển thị tên lấy từ geocoding (`local_names.vi`). Tìm "Ha Noi"/"Da Nang" không dấu vẫn ra đúng; "Da Nang" trả thêm hai xã cùng tên và có lúc một kết quả lạ ở Hong Kong. `temp_min/temp_max` của `/weather` không phải cao/thấp cả ngày; widget ghi rõ "24 giờ tới".
- **Vị trí một lần:** `getCurrentLocation` chờ 10 s; Oppo không có fix mới trong 10 s nên rơi về `getLastKnownLocation(NETWORK_PROVIDER)`. Kiểm `dumpsys location` để chắc yêu cầu đã gỡ. Dữ liệu vị trí suy ra (tên nơi) nằm trong `no_backup/weather.json`: xóa sau khi thử trên máy của chủ dự án.
- **Log mạng** không được chứa URL (có `appid`), khóa, tọa độ. Log đo tạm (tag `G3E`) gỡ trước merge.


## FX6 — thời tiết bền (2026-11-11, Vsmart Live 4)

- **Lỗi lạ cũng phải tính là một lần thử** (đọc code, 2026-11-11): nếu có hợp đồng chặn tần suất (`lastAttemptAt`), nếu không nhịp phút của widget gọi lại mỗi phút. Mọi lỗi lạ khi làm mới, tìm thành phố, tra tên nơi hay lấy vị trí đều thành `WeatherError`; repository chỉ ném `WeatherException` (trừ hủy coroutine).

## WU — đơn vị °C/°F (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/WU-weather-units.md](../knowledgements/WU-weather-units.md)

- (2026-11-13, đọc code) Dữ liệu có nhiều đơn vị hiển thị: **lưu giá trị thô, làm tròn một lần ở bước hiển thị**; làm tròn lúc parse rồi đổi đơn vị gây sai số tích lũy (24.4 °C → 24 → 75 °F thay vì 76). Chủ đề đề xuất: `widgets`.
- (2026-11-13, đọc code) Đổi một tùy chọn chỉ ảnh hưởng cách hiển thị thì không gọi lại API: đổi `units` của OWM sẽ tốn lệnh, lộ khóa nhiều hơn và hỏng khi offline. Chủ đề: `widgets`.
- (2026-11-13, đọc code) Widget tự vẽ nhận thay đổi cài đặt bằng cách `object` cầu nối thu `Flow` rồi gọi `WidgetTicker.tickNow()`; trước khi đọc xong cài đặt lần đầu trả trạng thái `Loading` thay vì giá trị mặc định để không chớp sai. Chủ đề: `widgets`.
- (2026-11-13, đọc code) Chuỗi có đơn vị làm dài chữ: cỡ chữ cố định dùng chung nhiều ô (dải theo giờ) phải co theo chuỗi dài nhất (`fitWidth`). Chủ đề: `widgets`.

## WX — thời tiết nhiều địa điểm (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/WX-weather-cities.md](../knowledgements/WX-weather-cities.md)

- (2026-11-13, đọc code) **Cấu hình riêng từng widget tự vẽ lưu ngoài Room, khóa theo `itemId`** an toàn vì `launcher_items.id` là `AUTOINCREMENT`; trước khi dựa vào đó phải đọc mọi đường ghi (danh sách ở mục 5) xem có xóa-rồi-tạo-lại không. Khi nhiều loại widget cần cấu hình thì xét cột `config` + migration một lần cho tất cả. Chủ đề: `widgets`.
- (2026-11-13, đọc code) **Ô widget được tái dùng giữa widget và giữa loại item:** danh tính của widget (`itemId`) phải được đưa vào renderer ở **mỗi** lần bind, trước lần vẽ đầu của lần bind đó; không lưu trong renderer khi tạo. Chủ đề: `widgets`.
- (2026-11-13, đọc code) **Cổng "cấu hình đã nạp":** cầu nối (`object`) giữa repository và widget không được kích hoạt tác dụng phụ (gọi mạng) trước khi cấu hình riêng của widget đã nạp, kẻo xử lý nhầm địa điểm mặc định. Chủ đề: `widgets`.
- (2026-11-13, đọc code) `StateFlow` không phát lại giá trị bằng nhau: một thay đổi chỉ chạm một phần dữ liệu (ánh xạ widget) cần luồng riêng nếu có view phải vẽ lại. Chủ đề: `widgets`.
- (2026-11-13, đọc tài liệu) OpenWeather One Call 3.0 mới có cảnh báo thời tiết của cơ quan quốc gia và nằm trong gói đăng ký riêng (1.000 lệnh/ngày miễn phí); trang không nói có cần thẻ, và không nói gói Free có cảnh báo. Chủ đề: `widgets` (mục thời tiết).

## WN — thông báo thời tiết (2026-11-13/16). Nguồn: [knowledgements/WN-weather-notifications.md](../knowledgements/WN-weather-notifications.md)

- (2026-11-13, **đo được**) Chờ I/O chặn luồng với hạn chót: `withTimeout` không ngắt được lời gọi HTTP chặn; chạy việc ở scope khác và `await` có hạn chót, việc vẫn tiếp tục sau hạn. Chủ đề: `widgets` (mục thời tiết).
- (2026-11-13, **đo được**) Cache định dạng cũ thiếu trường mới (khóa `f`) làm tính năng mới bỏ qua âm thầm: kiểm "dữ liệu đủ dùng cho tính năng này" chứ không chỉ "dữ liệu còn mới". Chủ đề: `widgets`.

## G3b — widget ảnh (2026-11-13/16, Live 4). Nguồn: [knowledgements/G3b-photo-widget.md](../knowledgements/G3b-photo-widget.md)

- 2026-11-13 [đo được, Live 4] `BitmapFactory.decodeStream` với `inJustDecodeBounds = true` **luôn trả null**; kết quả nằm ở `outWidth`/`outHeight`. Kiểm `== null` làm mọi ảnh hợp lệ bị loại ngay bước đọc cỡ (staging rỗng, không báo lỗi). Chủ đề `bitmaps`.
- 2026-11-13 [đo được, Live 4] Lỗ trong lớp tối vẽ bằng `Path.FillType.EVEN_ODD` **không hoạt động** (vùng trong khung vẫn tối). Dùng `WINDING` với hai đường có hướng ngược nhau (ngoài CW, trong CCW). Chủ đề `widgets`/`glass`.
- 2026-11-13 [đo được, Live 4] Item gốc của `RecyclerView` đặt `match_parent` theo chiều cao làm ô cao thấp không đều (tile cao). Dùng `ConstraintLayout` với tỉ lệ 1:1 cho ô vuông. Chủ đề `widgets`.
- 2026-11-13 [đọc tài liệu + đo được] `Bitmap.compress` không mang EXIF; lưu ảnh widget bằng nén lại là cách bỏ GPS (đếm EXIF/GPS = 0). Chủ đề `bitmaps`.

## G3c — widget của app khác (2026-11-13/16, Live 4 + Oppo). Nguồn: [knowledgements/G3c-appwidget-host.md](../knowledgements/G3c-appwidget-host.md)

- (W, 2026-11-16, **đo được** Oppo A3x API 34) Mở màn cấu hình widget trên Android 14 phải kèm `ActivityOptions` có `setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`; không có thì `RESULT_CANCELED` tới hai lần và màn không hiện. Mọi bước kết thúc luồng thêm phải idempotent.
- (W, 2026-11-13, **đo được**) Khối `<queries>` với `android.appwidget.action.APPWIDGET_UPDATE` cần trên API 34 (thấy 197 provider thay vì 201) nhưng không cần trên API 30 (85/85). Không cần `QUERY_ALL_PACKAGES`.
- (W, 2026-11-13 và 2026-11-16, **đo được** Live 4) Id widget của host sống qua `pm clear` và qua `force-stop`; nhấn Home giữa lúc màn cấu hình chạy không trả `RESULT_CANCELED` và không `onDestroy`, nên id rò nếu ta không tự `abort()` (xử lý ở `HomeActivity.onNewIntent` và hết hạn 10 phút). Cần quét `getAppWidgetIds()` lúc khởi động.
- (W, 2026-11-16, **đọc code**) `deleteAppWidgetId` là lệnh binder, không nằm trong transaction Room: ghi nhận id trong transaction, giải phóng ngay sau commit, quét khởi động là lưới an toàn.
- (W, 2026-11-16, **đo được** Live 4, chủ dự án kiểm tay) Activity dùng AppCompat làm `RemoteViews` hỏng: inflater của `AppCompatActivity` đổi `ImageView` → `AppCompatImageView` (không có phương thức `@RemotableViewMethod`) nên `RemoteViews$ActionException`/`InflateException`, host hiện chữ lỗi (Lịch Vsmart, YouTube). Dựng host view bằng `ContextWrapper` của Activity trả `LayoutInflater` lấy từ application context (không factory AppCompat).

## TV — dấu "−" hàng widget đầu của Today bị cắt (2026-11-20) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **Gốc (đọc code):** widget Today nằm trong `NestedScrollView`. `ViewGroup.dispatchDraw` cắt con theo mép của chính nó khi `clipToPadding`
  (mặc định `true`), **kể cả** khi `clipChildren = false`. Đệm trên 12dp của lưới không đủ cho dấu "−" (nhô r/2 + bóng) cộng góc widget nâng
  lên khi rung ±1° (widget rộng ~344dp: ~3dp).
- **Sửa:** `TodayView.GRID_TOP_DP` 12 → 20dp. Thứ trong khung cuộn mà vẽ tràn ra ngoài con (dấu, bóng, rung) phải chừa đệm trong khung,
  `clipChildren = false` không cứu được.

## WF — kéo widget từ trang danh sách, "App gần đây" trên Home, bố cục lần đầu (2026-11-25). Plan: [../plans/WF-widget-drag-first-run.md](../plans/WF-widget-drag-first-run.md)

- (WF, 2026-11-25, **đọc code**, chờ kiểm máy) Đổi một bảng từ `Dialog` (cửa sổ riêng) sang lớp phủ vẽ trong cây Compose của Activity (PG2) thì mọi chạm trong bảng **đi qua `Activity.dispatchTouchEvent`** trước Compose. Bộ cử chỉ đứng ở đó (vuốt mở / đóng Thư viện, Today) thấy cả cú lướt pager trong bảng → lướt đổi cỡ ở bảng mở từ Today bị hiểu là vuốt đóng Today. Khi chuyển `Dialog` → lớp phủ, rà mọi chỗ chặn chạm ở cấp Activity và cho bỏ qua khi lớp phủ mở (`HomeWidgetPicker.isOpen`).
- (WF, 2026-11-25, **đọc code**) Hai `pointerInput` trên cùng một nút: pass Main đi từ modifier TRONG ra NGOÀI. Đặt cử chỉ "giữ rồi kéo" sau (`trong`) `clickable` thì nó thấy sự kiện trước, chưa arm thì không tiêu thụ (bấm vẫn chạy), arm xong tiêu thụ hết nên `clickable` huỷ (không mở chi tiết sau khi kéo).
- (WF, 2026-11-25) Loại widget mới không vẽ bằng `WidgetRenderer` (vd "App gần đây" vẽ bằng Compose) vẫn nên là một `WidgetKind`: dùng lại nguyên đường thêm / kéo / lưu (`itemType = 4`, `className`), chỉ thêm nhánh vẽ ở `HomeWidgetCell`; `WidgetRenderers.create` trả renderer rỗng thay vì ném lỗi. Nơi đọc mã loại của chỗ khác (Today: `TodayWidget.builtin`) phải loại trừ nó nếu nơi đó đã có nhánh riêng.

## PW — widget vuông, font widget (2026-11-25). Plan: [../plans/PW-menu-block-widget-square-font.md](../plans/PW-menu-block-widget-square-font.md)

- (PW, 2026-11-25, **đọc tài liệu**) SF Pro không nhúng vào app Android được: giấy phép tải từ Apple Developer chỉ cho làm giao diện phần mềm chạy trên hệ điều hành của Apple. Thay bằng Inter (SIL OFL) — Inter cho chữ nhỏ, Inter Display cho chữ / số lớn, như SF Pro Text / Display. Bản đầy đủ ~410 KB mỗi độ đậm; cắt còn Latin + tiếng Việt bằng `pyftsubset` (`--no-hinting`, giữ `kern`, `tnum`...) còn ~115 KB. Chữ không Latin (Hindi, Thái, Nhật, Hàn) tự rơi về font hệ thống.
- (PW, 2026-11-25, **đọc code**) Vùng 2 × 2 của lưới Home không vuông vì một hàng = icon + nhãn + khe, một cột = icon + khe; tắt nhãn thì ngược lại. Không ép lưới cho vuông (phụ thuộc cỡ lưới, bật / tắt nhãn, lệch cả bố cục icon) mà vẽ THẺ vuông trong vùng ô (`widgetCardFrame`, neo trên như iOS); mọi chỗ vẽ quanh thẻ (dấu "−", bóng ảnh kéo) phải dùng cùng khung thẻ, không dùng khung ô.
