# WN — Thông báo thời tiết (TT#17; màn 1.4 nhóm "Thông báo") — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Android 11, API 30); Oppo A3x (Android 14, API 34) chỉ cho các ca ColorOS/Android 12+ ghi rõ bên dưới. Lệnh viết cho CMD/PowerShell. Máy thật dùng chung với việc khác của chủ dự án và do điều phối chạy: agent không cài APK, không chạm máy. **Khóa OWM, URL có khóa, tọa độ, tên địa điểm, nội dung thông báo không bao giờ được in ra log, plan, commit, PR, báo cáo.**

**Nhánh:** `feature/wn-weather-notifications` · **Worktree:** `E:\SourceTree\IosLauncher-wt\WN-weather-notifications` · **PR:** #58 (draft) · **Bắt đầu:** 2026-11-13 · **Plan duyệt:** 2026-11-13 (điều phối; câu hỏi Q1–Q4 gửi chủ dự án theo lô, làm theo mặc định trong ngoặc: báo thức không chính xác, kênh LOW, chạm mở Home, chưa gợi ý ColorOS)
**Trạng thái:** Sẵn sàng review (2026-11-16). Task 1-6 xong; mọi ca chính đạt trên Live 4 và Oppo (bật công tắc + xin quyền, đăng thông báo, báo thức nổ đúng giờ khi tắt màn và Doze). Chủ dự án quyết 2026-11-13 bỏ các ca biên còn lại (đổi múi giờ qua Cài đặt, khởi động lại máy) vì mọi ca chính đã đạt. Bản release đã kiểm: không có receiver debug `WN_DEBUG_*`.
**Khóa:** **không khóa schema Room** · không cần Oppo cho phần lớn, **cần Oppo A3x** cho ca ColorOS (Task 3) · không đụng file độc quyền nào (xem "Vùng file"; **không đụng Home**)
**Ghi chú điều phối (2026-11-13):** (1) mức quan trọng kênh và lựa chọn "chính xác/không chính xác" mỗi thứ nằm sau MỘT hằng (`WeatherNotificationConfig`), đổi rẻ. (2) Kịch bản Task 3 chạy được KHÔNG cần khởi động lại: `dumpsys alarm`, `dumpsys deviceidle force-idle`, đổi múi giờ, cài đè; ca khởi động lại là bước cuối riêng, cần chủ dự án đồng ý trước. (3) `LauncherApp.kt`: G3b cũng thêm vào; chỉ thêm MỘT lời gọi tách riêng. (4) Ở chỗ gọi `snapshotForNotification` ghi chú một dòng vì sao không bao giờ kích hoạt `CurrentLocationUpdater`.
**Mức rủi ro:** trung bình (thêm quyền `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`, thêm receiver/JobService, mở rộng hợp đồng `WeatherRepository`; không đụng lớp widget). Model/effort đề xuất: Sonnet 5.5 / **high** (hành vi nền của Android và ROM, mục 2.1 của workflow).

**Goal:** Hai thông báo thời tiết, mỗi cái có công tắc riêng ở Cài đặt → Thời tiết → nhóm "Thông báo": **dự báo hằng ngày lúc 07:00** và **dự báo ngày mai lúc 21:00**, cho địa điểm "Vị trí hiện tại" như đang lưu. Dùng lại danh sách địa điểm, bộ nhớ đệm từng địa điểm và quy tắc 2 giờ của WX; dùng khóa OWM hiện có.
**Architecture:** `AlarmManager` (báo thức không chính xác, bật khi máy ngủ sâu) nối nhau, mỗi lần chỉ đặt **một** báo thức tiếp theo cho mỗi loại; báo thức gọi `BroadcastReceiver` (Hilt) → đặt lại báo thức kế tiếp ngay → xếp một `JobService` (`JobScheduler`, không thư viện mới) lấy dữ liệu từ `WeatherRepository` (dùng cache nếu < 2 giờ, tải một lần nếu cũ hơn) → dựng bản tóm tắt thuần Kotlin ở `domain` → `ui` đăng thông báo. Lịch được đặt lại khi: bật/tắt công tắc, khởi động máy, cập nhật app, đổi giờ, đổi múi giờ, và mỗi lần tiến trình tạo (idempotent). Không dịch vụ nền, không vị trí nền, không thư viện mới.
**Tech Stack:** Kotlin, Hilt, DataStore (khóa riêng `WeatherPrefsKeys`), `AlarmManager`/`JobScheduler`/`NotificationManager` (framework). **Không dependency mới** (xem D1 vì sao không WorkManager).
**Spec:** TT#17 trong `Launcher-Features.md` (dòng 17: "Trong đợt FD (FD6), nhánh riêng sau WX, dùng lại danh sách địa điểm và giới hạn 2 giờ của WX"), `Figma-Draft.md` màn 1.4 "Thông báo" (cảnh báo thời tiết xấu: **bỏ**, chủ dự án 2026-11-13), `plans/WX-weather-cities.md`, `knowledgements/WX-weather-cities.md`, `G3e-weather-widget.md`, `WU-weather-units.md`, `lessons/widgets.md` (mục G3e/FX6/WU/WX).

---

## Hiện trạng đã đọc (đọc code, `main` @ d920c5a)

- `WeatherRepository.refreshIfStale(placeId)` là đường làm mới duy nhất; **chỉ widget đang hiện gọi** (hợp đồng ở KDoc: "Chỉ widget đang hiện mới kích hoạt làm mới"). Với mã `current` nó còn gọi `CurrentLocationUpdater.maybeUpdate()` (lấy vị trí im lặng, `ACCESS_COARSE_LOCATION`). Đường này **không dùng được** cho thông báo: (a) lúc 07:00 thường không có widget nào hiện nên không ai làm mới; (b) nó có thể kích hoạt lấy vị trí, mà chủ dự án chốt "không vị trí nền" (và không có `ACCESS_BACKGROUND_LOCATION`).
- Quyết định đến hạn chỉ đọc `lastAttemptAt` + `lastResult` của mục cache (`dueReason`): 2 giờ sau thành công/429, 15 phút sau lỗi, 401 chặn tới khi khởi động lại tiến trình. `doRefresh` chạy trong `mutex` chung toàn app, ghi `weather.json`. Một lần làm mới = **2 lệnh** (`weather` + `forecast`).
- `WeatherSnapshot` chỉ giữ 4 mốc `hourly` (dải widget) và cao/thấp 24 giờ tới; parser xin `cnt=9` (27 giờ không đủ để tóm tắt "cả ngày mai" khi cache có thể cũ tới 2 giờ). `OwmParser.FORECAST_COUNT`, `WeatherStore.writeSnapshot/readSnapshot` là chỗ cần mở rộng.
- Cài đặt → Thời tiết (`WeatherSettingsScreen`) có nhóm "Địa điểm theo dõi" + nhóm "Đơn vị"; `onSwitchChanged` hiện là `Unit` (chưa có công tắc). `SettingsRow.Switch` không có trường `enabled` (chỉ `description` là `@StringRes`). Màn Vị trí (`LocationSettingsScreen`) đã có mẫu xin quyền runtime từ một Activity cài đặt (`registerForActivityResult` ở lúc dựng, xử lý "từ chối vĩnh viễn" bằng `shouldShowRequestPermissionRationale` rồi mở cài đặt ứng dụng).
- Manifest: chưa có `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, receiver hay service nào; `targetSdk = 37`, `minSdk = 26`. Chưa có kênh thông báo, icon thông báo, `NotificationManager` ở đâu trong code (đã `grep`). Có tiền lệ receiver chỉ-debug ở `app/src/debug` (CP: `FakeContactsModeReceiver`, `exported=true` + `android:permission=DUMP`).
- `HomeActivity` là `singleTask`, có `onNewIntent` xử lý `ACTION_MAIN` + `CATEGORY_HOME`. **`HomeActivity` thuộc độc quyền của G3c trong đợt này** (brief), nên thông báo không được thêm extra/xử lý mới ở Home.
- `GeoPlace.displayName` + `WeatherPlaces.resolve("current")` cho tên và tọa độ của "Vị trí hiện tại" (Tự động: `autoPlace`, Thủ công: `manualPlace`).

## Tài liệu chính thức đã đọc (2026-11-13, WebFetch) — nền cho các quyết định

| # | Điều | Nguồn | Nhãn |
|---|---|---|---|
| F1 | Báo thức không chính xác (`set`, `setAndAllowWhileIdle`) không bao giờ nổ trước giờ đặt; **từ Android 12 hệ thống nổ trong vòng 1 giờ kể từ giờ đặt**, trừ khi có hạn chế pin (Battery Saver, Doze) | developer.android.com/develop/background-work/services/alarms | đọc tài liệu |
| F2 | `setWindow`: app target ≥ 12 thì cửa sổ nhỏ hơn 600000 ms thường bị nâng lên 600000 (10 phút) | cùng trang | đọc tài liệu |
| F3 | Doze: "Alarms don't fire when the device is in Doze mode; deferred until the device exits Doze"; muốn chạy khi máy đang Doze: báo thức chính xác hoặc WorkManager; trang nêu `setAndAllowWhileIdle` cho "hành động người dùng chỉ định chạy sau một giờ nhất định (kể cả khi máy rảnh)" | cùng trang | đọc tài liệu (hai câu hơi lệch nhau: **chưa đo** `setAndAllowWhileIdle` thật sự nổ trong Doze trên Live 4 và Oppo, xem Task 3) |
| F4 | `SCHEDULE_EXACT_ALARM` **không** cấp sẵn cho app mới target ≥ 13 (người dùng phải cấp ở "Báo thức & lời nhắc"); `USE_EXACT_ALARM` cấp tự động nhưng chịu chính sách Google Play, chỉ cho app báo thức/lịch; tài liệu khuyên dùng báo thức không chính xác khi có thể | cùng trang | đọc tài liệu |
| F5 | Mọi báo thức bị hủy khi máy tắt; khởi động lại phải đặt lại từ `ACTION_BOOT_COMPLETED` (cần `RECEIVE_BOOT_COMPLETED`; "chỉ chạy nếu app đã được người dùng mở ít nhất một lần"). `RTC` hợp với "giờ trong ngày" nhưng "nếu người dùng đổi giờ máy sẽ có hành vi bất ngờ" | cùng trang | đọc tài liệu |
| F6 | Receiver khai trong manifest nhận được các broadcast ngầm được miễn: `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`. `ACTION_PACKAGE_REPLACED` **không** được miễn (khác `MY_PACKAGE_REPLACED`, gửi thẳng tới app: không nằm trong trang này, suy luận chưa kiểm chứng, phải đo) | developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions | đọc tài liệu + suy luận |
| F7 | `onReceive` chạy trên luồng chính, phải trả về nhanh; `goAsync()` vẫn buộc xong **dưới 10 giây**; việc dài hơn nên xếp `JobScheduler`; process chỉ chứa receiver có thể bị hệ thống giết ngay sau `onReceive` | developer.android.com/develop/background-work/background-tasks/broadcasts | đọc tài liệu |
| F8 | Android 13+: `POST_NOTIFICATIONS` là quyền runtime; app **cài mới** có thông báo tắt mặc định; app target ≥ 13 tự quyết lúc nào hiện hộp xin (app target ≤ 12L: hệ thống hiện ngay lần mở Activity đầu sau khi tạo kênh); từ chối thì mọi kênh bị chặn; nên gọi `areNotificationsEnabled()` trước khi đăng; app cũ nâng cấp lên Android 13 được cấp sẵn nếu từng có kênh và chưa tắt | developer.android.com/develop/ui/views/notifications/notification-permission | đọc tài liệu |
| F9 | WorkManager `androidx.work:work-runtime` bản ổn định mới nhất **2.12.0, ngày 2026-09-23** | developer.android.com/jetpack/androidx/releases/work | đọc tài liệu (trùng ghi chú AGENTS.md 2026-10-16) |
| F10 | ColorOS/Oppo giết dịch vụ nền, cần "Tự khởi động"/tối ưu pin; **trang dontkillmyapp không nhắc tới báo thức**, chỉ có dữ liệu cho Oppo F1S (ColorOS 6), nên **không áp dụng chắc chắn cho Oppo A3x** | dontkillmyapp.com/oppo (bên thứ ba) | đọc tài liệu bên thứ ba, độ tin thấp: **phải đo trên Oppo A3x** |

## Vì sao chọn thiết kế này

**Vấn đề cần giải.** Hai thông báo theo giờ trong ngày (07:00, 21:00) phải: (1) nổ đúng ngày dù app không chạy và máy ngủ; (2) sống qua khởi động lại, đổi giờ, đổi múi giờ; (3) lấy dữ liệu mà **không phá ngân sách API** (WX: tối đa 72 lệnh/ngày, 2 giờ/địa điểm) và **không bao giờ xin vị trí**; (4) không tăng chi phí khởi động của launcher (bài học `startup-performance`: mọi thứ thêm vào `Application.onCreate` đều bị soi); (5) không đụng Home. Máy mục tiêu: Live 4 (API 30, trước Android 12) và Oppo A3x (API 34, ColorOS).

### D1 — Cơ chế lên lịch

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Báo thức chính xác** (`setExactAndAllowWhileIdle`) + receiver | Đúng 07:00:00 | Đúng giờ tới phút | Cần `SCHEDULE_EXACT_ALARM` (F4): app mới target 37 **không** có sẵn, người dùng phải vào "Báo thức & lời nhắc" bật (thêm màn xin, thêm ca hỏng khi bị thu hồi, thu hồi thì mọi báo thức chính xác bị hủy). `USE_EXACT_ALARM` trái chính sách Play (launcher không phải app báo thức/lịch). Tốn pin hơn, tài liệu khuyên tránh. Bản tin dự báo không cần chính xác tới phút | Không |
| **B. Báo thức không chính xác nối nhau** (`setAndAllowWhileIdle`, `RTC_WAKEUP`) + receiver → `JobScheduler` | Mỗi loại một báo thức một lần; khi nổ: đặt ngay báo thức của lần kế tiếp rồi xếp job lấy dữ liệu | Không quyền đặc biệt; không dependency; giờ-trong-ngày tính bằng `java.time` nên đổi múi giờ/giờ máy xử lý được bằng cách đặt lại; nổ cả khi Doze (F3, cần đo) | Có thể trễ tới 1 giờ trên Android 12+ (F1): 07:00 có thể thành 07:40; Live 4 (API 30) chưa áp quy tắc này nên có thể đúng giờ hơn (chưa đo). ColorOS có thể giết/chặn (F10, chưa đo). Phải tự đặt lại khi boot/đổi giờ (F5, F6) | **Chọn** |
| C. WorkManager `OneTimeWorkRequest` tự nối (initialDelay tới lần 07:00 kế) | Worker làm xong tự xếp worker của ngày sau | Tự có backoff, ràng buộc mạng, sống qua boot (thư viện tự xếp lại) | **Dependency mới** `work-runtime` 2.12.0 (F9) + `androidx.hilt:hilt-work` nếu muốn Worker nhận Hilt; `WorkManagerInitializer` thêm vào `androidx.startup` làm tăng chi phí khởi động (muốn tránh phải tắt initializer và khởi tạo lười: thêm một bẫy). `initialDelay` là khoảng **tương đối**: đổi giờ/múi giờ máy làm lệch 07:00, vẫn phải tự lắng nghe `TIME_SET`/`TIMEZONE_CHANGED` rồi hủy và xếp lại, tức là tự viết gần như đủ phần B và thêm phần WorkManager lên trên. Độ lệch giờ của JobScheduler tương tự B | Không |
| D. WorkManager `PeriodicWorkRequest` 24 giờ + `flexInterval` | Khoảng lặp 24 giờ, cửa sổ flex | Ít code nhất | Không có khái niệm "07:00": chỉ là "mỗi 24 giờ ± flex" tính từ lúc xếp; flex tối thiểu 15 phút nhưng pha trôi theo từng lần chạy trễ; không thể bảo đảm hai lần 07:00 và 21:00 không lệch dần. Không đạt yêu cầu "giờ cố định" | Không |

**Vì sao B.** Chủ dự án chốt hai giờ cố định và không đòi chính xác tới phút; B là phương án duy nhất vừa đúng "giờ trong ngày" (đặt lại bằng `java.time` theo múi giờ hiện hành), vừa không thêm quyền đặc biệt, vừa không thêm thư viện hay initializer (tiền lệ dự án: tránh thêm chi phí khởi động; AGENTS.md "không đoán version, dependency mới cần lý do" — ở đây chưa có lý do đủ). Phần việc dài (mạng) tách khỏi `onReceive` sang `JobService` đúng khuyến nghị F7, mà không cần WorkManager. Việc đặt báo thức kế tiếp **trước** khi làm việc bảo đảm chuỗi không bao giờ đứt dù job hỏng.
**Đánh đổi chấp nhận:** trễ tối đa ~1 giờ trên Android 12+ (hỏi chủ dự án, Q1); phụ thuộc ROM (Oppo) chưa biết, nên Task 3 **bắt buộc kiểm tay** trên cả hai máy trước khi xây tiếp. Nếu đo trên Oppo A3x báo thức không nổ sau khi vuốt app khỏi đa nhiệm hoặc sau boot, **xét lại**: thêm gợi ý "Tự khởi động" trong Cài đặt, hoặc chuyển sang C (WorkManager 2.12.0) — chi phí chuyển không lớn vì `PrepareWeatherNotificationUseCase` và `WeatherNotifier` giữ nguyên.

### D2 — Nơi chạy phần lấy dữ liệu sau khi báo thức nổ

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| a. `goAsync()` trong receiver, coroutine trên `ApplicationScope` | Làm hết trong receiver | Ít file nhất | Phải xong dưới 10 giây (F7); `WeatherHttp` đặt timeout 10 giây **mỗi** lệnh và `fetch` là 2 lệnh tuần tự (đọc code) nên ca mạng chậm vượt giới hạn; không có ràng buộc mạng, offline là lỗi ngay | Không |
| **b. Receiver xếp `JobScheduler` job** (`JobService`, `NETWORK_TYPE_ANY`, `setOverrideDeadline` 20 phút) | Receiver: đặt báo thức kế rồi xếp job; job gọi use case trong coroutine | Job có thời gian dài (không bị 10 giây), chờ có mạng tới hạn chót rồi vẫn chạy (hết hạn thì dùng cache); process được hệ thống giữ trong lúc job chạy (F7) | Thêm một `JobService` (~40 dòng) và một mục manifest; job có thể bị Doze/standby hoãn. **Đo được 2026-11-13 (Live 4, API 30, debug a9c0ee1): báo thức nổ đúng giờ (+3 phút 14 giây) nhưng job chỉ chạy lúc BẬT màn hình, 17 phút 42 giây sau; dumpsys jobscheduler thấy job "idle … RUNNABLE" chờ mạng VALIDATED.** Vậy thông báo 07:00 có thể chờ tới lúc người dùng cầm máy | Chỉ còn là đường lùi |
| **c. Lai: receiver đăng NGAY trong goAsync, job chỉ khi chưa có dữ liệu** | Báo thức nổ -> receiver: đặt báo thức kế, rồi Dispatcher.run chờ dữ liệu tối đa 8 giây (cache nếu chưa đến hạn 2 giờ; đến hạn thì MỘT lần tải ở scope ứng dụng, người gọi chỉ chờ, hết hạn thì dùng cache) và đăng; chỉ khi chưa có dữ liệu dùng được (không cache hoặc cache > 12 giờ, tức gần như ngoại tuyến) mới xếp job thử lại khi có mạng | Thông báo xuất hiện ngay khi báo thức nổ (đường chính không phụ thuộc lịch job); vẫn đúng ngân sách (cùng dueReason); có đường lùi khi ngoại tuyến | Chờ mạng trong cửa sổ báo thức bị giới hạn: goAsync mong xong dưới 10 giây (F7) nên hạn chót 8 giây; trong Doze sâu app có thể chưa có mạng ở lúc nổ (chưa đo): khi đó dùng cache, hoặc job | **Chọn** |
| d. Dịch vụ foreground | Chạy dịch vụ | Chạy chắc | Từ Android 12 app không được khởi động dịch vụ foreground từ nền (trừ ngoại lệ báo thức chính xác, mà ta không dùng); thêm quyền; thông báo dịch vụ | Không |

### D3 — Dữ liệu cho thông báo (cache hay tải)

Quy tắc chốt (chủ dự án): dùng cache nếu mục của "Vị trí hiện tại" **chưa đến hạn 2 giờ**; cũ hơn thì tải **một lần**. Thực hiện bằng hàm mới của `WeatherRepository` (D3.1) thay vì tái dùng `refreshIfStale`:

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| 1. Gọi `refreshIfStale("current")` rồi đọc `states` | Không đổi giao diện repository | Kích hoạt `CurrentLocationUpdater` (lấy vị trí, vi phạm "không vị trí nền"); hàm không chờ kết quả (chạy `scope.launch`), job không biết khi nào xong | Không |
| **2. Hàm mới `suspend fun snapshotForNotification(placeId): WeatherSnapshot?`**: chờ nạp xong, trong `mutex` dùng đúng `dueReason` (2 giờ/15 phút/401), `doRefresh` nếu đến hạn, trả snapshot hợp lệ (mới tải hoặc cache) hoặc `null`; **không bao giờ** gọi `locationUpdater` | Chờ được kết quả; cùng một chính sách tần suất (không đường vòng quanh 2 giờ); lần tải của thông báo cũng ghi `lastAttemptAt` nên widget không tải lại trong 2 giờ kế (hai bên dùng chung ngân sách) | Mở rộng hợp đồng: KDoc "chỉ widget kích hoạt làm mới" phải sửa thành "widget hoặc thông báo" | **Chọn** |

Ngân sách (suy luận từ chính sách, chưa đo): mỗi thông báo tối đa 1 lần làm mới = 2 lệnh; 2 thông báo/ngày = **+4 lệnh/ngày**, tổng tối đa 72 + 4 = 76/ngày so với giới hạn gói Free 60 lệnh/phút và 1.000.000/tháng (đọc tài liệu, G3e). Thực tế thấp hơn vì tải của thông báo làm widget bớt tải.

### D4 — Dữ liệu dự báo đủ cho "cả ngày mai"

Dự báo OWM 3 giờ/mốc. Snapshot hiện chỉ giữ 4 mốc và cao/thấp 24 giờ tới. Tóm tắt "ngày mai" lúc 21:00 cần các mốc của cả ngày dương lịch kế tiếp **theo giờ địa phương của địa điểm**; cache có thể cũ tới 2 giờ nên cần ~12 mốc (36 giờ) thay vì 9.

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| a. Gọi thêm endpoint riêng cho thông báo | Thêm lệnh | Tách bạch | Thêm lệnh API (phá ngân sách), thêm đường mã | Không |
| **b. Nâng `cnt` từ 9 lên 12 và lưu thêm danh sách mốc `forecast` vào snapshot** (trường mới có mặc định rỗng; JSON thêm khóa `"f"` tùy chọn; `hourly` và cao/thấp của widget **không đổi**) | Cùng 1 lệnh `forecast`, chỉ khác tham số | Không thêm lệnh; cache cũ đọc được (thiếu `"f"` = rỗng); widget không đổi | Phải sửa parser/store/model (chung vùng với WU/WX, đã merge); cache cũ không có `forecast` thì thông báo dùng nó như "thiếu dữ liệu" → tải (nếu đến hạn) hoặc bỏ (ghi ở Task 2) | **Chọn** |
| c. Chỉ dùng 4 mốc có sẵn | Không sửa gì | "Ngày mai" chỉ 12 giờ, sai nghĩa | Không |

### D5 — Quyền thông báo và kênh

- **Quyền (F8):** `POST_NOTIFICATIONS` khai trong manifest; **xin khi người dùng bật công tắc đầu tiên** ở Cài đặt → Thời tiết (target 37 ≥ 33 nên hệ thống không tự hiện hộp). API < 33 (Live 4, API 30) không có quyền runtime: thông báo bật sẵn theo hệ thống, ta chỉ kiểm `NotificationManager.areNotificationsEnabled()` (framework API 24+, không cần dependency).
  - Bật công tắc → chưa có quyền (API ≥ 33) → hộp xin; được cấp thì công tắc bật; từ chối thì công tắc về tắt + Toast; "không hỏi lại" (cùng cách nhận biết `LocationSettingsScreen`: `shouldShowRequestPermissionRationale == false` **sau khi** đã xin) thì mở `ACTION_APP_NOTIFICATION_SETTINGS`.
  - API < 33 mà `areNotificationsEnabled()` = false (người dùng tắt thông báo app trong hệ thống) → công tắc không bật, Toast + mở cài đặt thông báo của app.
  - Hết quyền về sau: **không** tự tắt công tắc; lúc nổ, kiểm `areNotificationsEnabled()` và kênh chưa bị chặn **trước khi tải** (không tốn lệnh API cho thông báo không hiện được); màn Cài đặt hiện dòng chú thích khi hệ thống đang chặn (đọc lại ở `onResume`).
- **Kênh:** tạo **lười**, ở lần bật công tắc và ở đầu tiến trình đăng (idempotent), **không** tạo ở `Application.onCreate` (tránh chi phí khởi động và tránh gợi hộp xin quyền sớm). Hai phương án:

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| Một kênh `weather_forecast` | Gọn | Người dùng không tắt riêng được từng loại ở cài đặt hệ thống (đã có công tắc trong app, nhưng người đã quen cài đặt hệ thống) | Không |
| **Hai kênh** `weather_daily` ("Dự báo hằng ngày") và `weather_tomorrow` ("Dự báo ngày mai"), cùng nhóm kênh "Thời tiết", `IMPORTANCE_LOW` | Mỗi loại tắt/đổi âm riêng ở hệ thống; công tắc app và kênh khớp nhau; mức LOW (im lặng, vẫn hiện trong ngăn thông báo) hợp bản tin 21:00 lúc sắp ngủ | Mức quan trọng **không đổi được bằng code sau khi kênh tạo** (chỉ người dùng đổi) nên chọn sai là kẹt tới khi đổi `id` kênh (Q2) | **Chọn** |

### D6 — Nội dung và chạm vào thông báo

- **Địa điểm:** đúng "Vị trí hiện tại" như đang lưu (Tự động = lần lấy cuối, Thủ công = thành phố đã chọn); không bao giờ xin vị trí; **chưa xác định thì không đăng gì** (không tải, không thông báo lỗi), màn Cài đặt hiện chú thích. Chọn địa điểm khác cho thông báo: ngoài phạm vi.
- **Tiêu đề:** `"<tên địa điểm> · Hôm nay"` / `"<tên địa điểm> · Ngày mai"`. **Nội dung:** `"<tình trạng>, cao <hi><đơn vị>, thấp <lo><đơn vị>"`, nhiệt độ qua `TemperatureUnit.display(...)` + ký hiệu **y như widget** ("24°C"/"75°F", WU5); đơn vị đọc lúc đăng (đổi °C/°F xong thì thông báo kế theo đơn vị mới; không đổi lại thông báo đã đăng). Dòng mở rộng (BigText) khi có mưa/giông/tuyết: `"Có mưa từ 13:00 đến 19:00"` (giờ theo múi giờ của địa điểm, mốc đầu và cuối có hiện tượng).
- **Quy tắc chọn mốc** (hàm thuần ở `domain`, soi kỹ): ngày = ngày dương lịch theo `utcOffsetSeconds` của địa điểm. "Hôm nay" = mốc cùng ngày chưa kết thúc (`epoch + 3h > now`), cần ≥ 2 mốc; "Ngày mai" = mốc của ngày kế, cần ≥ 4 mốc; thiếu thì **không đăng** (không đoán). Cao/thấp = max/min `tempC` thô của các mốc đó (làm tròn một lần lúc hiển thị, WU1). Tình trạng đại diện = nặng nhất trong các mốc 06:00–21:59 giờ địa điểm theo thứ tự giông > tuyết > mưa > sương mù > nhiều mây > ít mây > quang (mốc ban ngày không có thì lấy cả ngày). Giữ đơn giản, ghi trong knowledgement là quy tắc của app (suy luận), không phải của OWM.
- **Ngoại tuyến / lỗi:** tải lỗi (mạng, 429, máy chủ, 401) → dùng cache nếu cache có đủ mốc cho kỳ cần tóm tắt **và** tuổi `fetchedAtMillis` ≤ 12 giờ; không thì **không đăng** (một thông báo lỗi lúc 07:00 chỉ là nhiễu). Không tự thử lại sau: `lastShownDay` chưa ghi nên lần đặt lại lịch kế (mở app, boot, đổi giờ) trong thời gian ân hạn 3 giờ sẽ thử lại, vẫn qua quy tắc 15 phút/2 giờ của repository.
- **Không khóa:** (`hasKey = false`) công tắc ẩn, chỉ có dòng chú thích; không lên lịch.
- **Chạm thông báo:** mở Home (PendingIntent tường minh tới `HomeActivity` với `ACTION_MAIN`, `FLAG_ACTIVITY_NEW_TASK`, `FLAG_IMMUTABLE`; `singleTask` đưa Home lên trước, **không sửa Home**). **Mở thẳng bảng Thời tiết** cần thêm xử lý extra ở `HomeActivity` (độc quyền G3c đợt này) và bảng cần `tappedWidgetId`: để sau, nhánh nhỏ khi G3c đã merge (ghi ở "Ngoài phạm vi").
  | Phương án | Ưu | Nhược | Chọn? |
  |---|---|---|---|
  | Mở Home | Không đụng Home; đơn giản; widget thời tiết (nếu có) hiện ngay | Không tới thẳng chi tiết | **Chọn** |
  | Mở bảng Thời tiết | Đúng ý "xem thêm" | Sửa `HomeActivity` (cấm đợt này) | Để sau |
  | Mở Cài đặt → Thời tiết | Sửa được khi đọc sai | Không phải điều người dùng muốn thấy lúc 07:00 | Không |
- **Riêng tư trên màn hình khóa:** `setVisibility(VISIBILITY_PRIVATE)` kèm `setPublicVersion` chỉ có "Dự báo thời tiết" (không tên địa điểm). Dùng icon `ic_stat_weather` mới (vector một màu, đơn sắc). `setOnlyAlertOnce`, `setAutoCancel`, `setWhen` = lúc đăng, mỗi loại một `id` thông báo cố định (đăng lại thay thế bản cũ, không chồng).

### D7 — Khi nào đặt lại lịch (đọc tài liệu F5, F6; nhãn từng dòng)

| Sự kiện | Cách bắt | Vì sao | Nhãn |
|---|---|---|---|
| Bật/tắt một công tắc | Gọi trực tiếp `rearm()` | Đặt hoặc hủy | đọc code |
| Khởi động máy | `BOOT_COMPLETED` (manifest, `RECEIVE_BOOT_COMPLETED`) | Báo thức mất khi tắt máy (F5) | đọc tài liệu; **ColorOS có chặn "tự khởi động" không: chưa đo** |
| Cập nhật app (cài đè bản mới) | `MY_PACKAGE_REPLACED` (manifest) | Báo thức có thể mất khi app bị cập nhật | suy luận chưa kiểm chứng: đo ở Task 3 xem có cần không; thêm vì rẻ |
| Đổi giờ máy / múi giờ | `TIME_SET`, `TIMEZONE_CHANGED` (manifest; được miễn, F6) | Mốc 07:00 theo giờ địa phương hiện hành | đọc tài liệu |
| Tiến trình tạo | `rearm()` một lần ở nền (IO), sau khung hình đầu, không chặn `onCreate` | Vớt trường hợp chưa bắt được sự kiện nào ở trên (vd "dừng ép buộc" rồi mở lại); idempotent (cùng `PendingIntent` thay báo thức cũ, F5 trang alarms) | đọc code |
| Báo thức nổ | Receiver | Đặt kế tiếp **trước** khi làm gì khác | đọc code |

Hàm `WeatherNotificationPlanner.nextTrigger(kind, now, zone, lastShownDay)` (thuần, `domain`): giờ mục tiêu hôm nay (`07:00`/`21:00` theo `zone`); nếu `now` đã qua mục tiêu và vẫn trong **ân hạn 3 giờ** và `lastShownDay != hôm nay` → trả `now` (nổ ngay, bù thông báo bị lỡ do máy tắt/Doze dài); ngược lại mục tiêu kế tiếp (hôm nay nếu chưa tới, nếu không thì ngày mai). Xử lý giờ nhảy mùa hè bằng `ZonedDateTime` (Việt Nam không có, nhưng người dùng đổi múi giờ thì có).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| N1 | Chỉ hai thông báo: dự báo hằng ngày 07:00 và dự báo ngày mai 21:00, mỗi cái một công tắc ở Cài đặt → Thời tiết → nhóm "Thông báo". Giờ cố định (không chọn giờ) | Chủ dự án 2026-11-13 |
| N2 | Cảnh báo thời tiết xấu **bỏ** (One Call 3.0 là gói riêng) | Chủ dự án 2026-11-13; đọc tài liệu |
| N3 | Dùng lại dữ liệu địa điểm và cache từng địa điểm của WX; làm mới theo quy tắc 2 giờ; thông báo được tải một lần nếu cache cũ hơn 2 giờ, không thì dùng cache; dùng khóa OWM hiện có | Chủ dự án 2026-11-13 |
| N4 | Không vị trí nền: thông báo dùng "Vị trí hiện tại" như đang lưu, không xin vị trí | Chủ dự án 2026-11-13 |
| N5 | Báo thức không chính xác nối nhau + `JobScheduler`; không WorkManager, không `SCHEDULE_EXACT_ALARM` | Đề xuất của agent (D1, D2), chờ duyệt |
| N6 | Hàm mới `snapshotForNotification` thay vì `refreshIfStale` | Đề xuất của agent (D3), chờ duyệt |
| N7 | Mở rộng snapshot: `cnt` 9 → 12 và danh sách mốc `forecast` (D4) | Đề xuất của agent, chờ duyệt |
| N8 | Quyền `POST_NOTIFICATIONS` xin lúc bật công tắc; kênh lười, hai kênh `IMPORTANCE_LOW` | Đề xuất của agent (D5), chờ duyệt (Q2) |
| N9 | Chạm thông báo → mở Home | Đề xuất của agent (D6), chờ duyệt (Q3) |
| N10 | Không có lệnh gọi mạng nếu thông báo bị hệ thống chặn; không đăng thông báo lỗi | Đề xuất của agent (D5, D6) |
| N11 | Q1: báo thức không chính xác, chấp nhận trễ tới ~1 giờ (đo ở Task 3); hằng `WeatherNotificationConfig.USE_EXACT_ALARMS` = false | **Chủ dự án 2026-11-13** |
| N12 | Q2: kênh `IMPORTANCE_LOW` (hằng của ui ở Task 4) | **Chủ dự án 2026-11-13** |
| N13 | Q3: chạm thông báo mở Home; bảng Thời tiết để sau khi G3c merge | **Chủ dự án 2026-11-13** |
| N15 | **Sửa sau đo Live 4 (2026-11-13):** đường chính là receiver đăng ngay trong `goAsync` (D2c), job chỉ là đường lùi khi chưa có dữ liệu; `snapshotForNotification(placeId, timeoutMs)` chờ tối đa `timeoutMs` (tải ở scope ứng dụng); cache chưa có đủ mốc dự báo (cache bản trước WN) được tải MỘT lần dù chưa đến 2 giờ (ngoại lệ 2 lệnh, không lặp) | Điều phối đo + agent, 2026-11-13 |
| N14 | Q4: chưa có gợi ý ColorOS/tự khởi động; quyết sau số đo Oppo Task 3 | **Chủ dự án 2026-11-13** |

## Ngoài phạm vi / ghi để sau

- Cảnh báo thời tiết xấu (N2); chọn giờ thông báo; chọn địa điểm khác "Vị trí hiện tại" cho thông báo; thông báo nhiều địa điểm; xác suất mưa (`pop`) và sức gió/độ ẩm trong nội dung (parser hiện không đọc).
- **Chạm thông báo mở thẳng bảng Thời tiết** (cần `HomeActivity`, đợi G3c merge).
- Nút "Gửi thử" trong Cài đặt (xem Task 5 ghi chú: chỉ có công cụ gỡ lỗi ở bản debug).
- Gợi ý "Tự khởi động"/tối ưu pin cho ColorOS: chỉ làm nếu đo trên Oppo cho thấy cần (Task 3).
- Chuỗi tiếng Anh: đợt LG (ngôn ngữ).

## Vùng file

Đường dẫn từ `app/src/main/java/gambi/global/os26launcher/` (trừ ghi chú). **Không file độc quyền nào** (không `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `data/layout/*`, `core/glass/*`, `DragHelper`). Không động `IconGridView`, `IconCellView`, `WidgetPickerController`, lớp widget (vùng của G3b/G3c).

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/weather/WeatherNotification.kt` (`WeatherNotificationKind`, `WeatherNotificationSettings`, `ForecastSummary`) | Tạo | — |
| `domain/weather/WeatherNotificationPlanner.kt`, `WeatherForecastSummarizer.kt` (hàm thuần) | Tạo | — |
| `domain/weather/WeatherNotificationScheduler.kt` (interface `rearm()`), `domain/usecase/PrepareWeatherNotificationUseCase.kt` | Tạo | — |
| `domain/weather/WeatherPreferencesRepository.kt` (thêm luồng/ghi cho hai công tắc và `lastShownDay`) | Sửa | — (chung với WU, đã merge) |
| `domain/weather/WeatherRepository.kt` (KDoc + `snapshotForNotification`) | Sửa | — |
| `domain/model/weather/WeatherModels.kt` (`WeatherSnapshot.forecast` mặc định rỗng) | Sửa | — |
| `data/weather/WeatherPrefsKeys.kt`, `WeatherPreferencesRepositoryImpl.kt` | Sửa | — |
| `data/weather/WeatherRepositoryImpl.kt` (hàm mới, dùng chung `dueReason`/`doRefresh`) | Sửa | — |
| `data/weather/OwmParser.kt`, `WeatherStore.kt` (`cnt` 12, khóa `"f"`) | Sửa | — |
| `data/weather/notify/AlarmWeatherNotificationScheduler.kt` | Tạo | — |
| `ui/weather/notify/WeatherNotificationReceiver.kt`, `WeatherNotificationJobService.kt`, `WeatherNotifier.kt`, `NotificationPermission.kt` | Tạo | — |
| `ui/settings/WeatherSettingsScreen.kt`, `WeatherSettingsViewModel.kt` (nhóm "Thông báo") | Sửa | — |
| `ui/settings/SettingsRow.kt` (thêm hằng `ID_WEATHER_NOTIFY_*`) | Sửa (chỉ thêm) | **Chung** (append-only) |
| `LauncherApp.kt` (một dòng gọi `rearm()` ở nền) | Sửa | — (file nhỏ, không nằm trong danh sách nóng; báo vì G3c/G3b có thể cũng sửa: chỉ thêm) |
| `di/WeatherModule.kt` (thêm `@Binds` scheduler) | Sửa (chỉ thêm) | **Chung** (Hilt module) |
| `AndroidManifest.xml` (quyền `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; một `<receiver>`, một `<service>`) | Sửa (chỉ thêm) | **Chung** (append-only) |
| `res/values/strings.xml` (khối `wn_` ở **cuối** file) | Sửa (chỉ thêm) | **Chung** |
| `res/drawable/ic_stat_weather.xml` | Tạo | — |
| `app/src/debug/AndroidManifest.xml`, `app/src/debug/java/.../debug/WeatherNotificationDebugReceiver.kt` (chỉ bản debug: gửi thử một loại ngay, in tên loại và kết quả đã đăng/bỏ, không in nội dung) | Sửa / Tạo | — |
| `docs/plans/WN-weather-notifications.md`, `docs/knowledgements/WN-weather-notifications.md` | Tạo | — |

Không đổi: Room, `SettingsKeys`/`SettingsRepositoryImpl`/`SettingsUiState`/`SettingsViewModel` (khóa riêng ở `WeatherPrefsKeys`), `libs.versions.toml`, `app/build.gradle.kts`.

## Global Constraints

- Layering `ui → domain ← data`: `domain` không `android.*` (planner, summarizer, use case thuần); `AlarmManager` ở `data`; `NotificationManager`, receiver, `JobService`, `PendingIntent` tới `HomeActivity` ở `ui` (data không import ui).
- Chuỗi ở `strings.xml` (khối `wn_`, tiếng Việt; tiếng Anh theo LG); tên tài nguyên và code tiếng Anh. Màu/kích thước không cần mới.
- **Riêng tư:** không log khóa, URL, tọa độ, tên địa điểm, nội dung thông báo; `Log.*` chỉ ghi tên lớp ngoại lệ và mã loại (`DAILY`/`TOMORROW`) và cờ kết quả (`shown`/`skipped:<lý do>`). `grep` toàn diff trước khi báo xong Task (như WX).
- Không thư viện mới, không quyền vị trí nền, không dịch vụ foreground. Quyền mới đúng hai: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- Không `Handler`/`Timer`/vòng lặp nền riêng; không thêm việc nặng vào `Application.onCreate` (chỉ một lần `launch` ở IO).
- Thông báo **không bao giờ** kích hoạt `CurrentLocationUpdater` hay `CoarseLocationSource`.
- Build `gradlew.bat assembleDebug` xanh mới báo xong Task. Mọi số đo ghi rõ bản debug.

## Review Focus

1. `snapshotForNotification` không gọi `locationUpdater`, dùng cùng `dueReason`/`doRefresh`/`mutex` với `refreshIfStale` (không đường vòng quanh 2 giờ), và không phát trạng thái sai cho widget.
2. Chuỗi báo thức: `rearm()` idempotent; receiver đặt báo thức kế **trước** khi xếp job; công tắc tắt thì hủy `PendingIntent` của loại đó (khớp request code/extra); `FLAG_IMMUTABLE`.
3. Nhận biết "đã đăng hôm nay" (`lastShownDay`) và ân hạn 3 giờ: không đăng trùng; không đăng thông báo 07:00 lúc 15:00.
4. Ca giờ/múi giờ: `TIME_SET`, `TIMEZONE_CHANGED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` đều gọi cùng đường `rearm()`; không có việc nặng trong `onReceive`.
5. Kiểm `areNotificationsEnabled()` + kênh **trước khi tải**; từ chối quyền không để công tắc ở trạng thái "bật giả"; không crash khi `POST_NOTIFICATIONS` thu hồi.
6. Quy tắc tóm tắt (hôm nay/ngày mai, múi giờ địa điểm, ngưỡng số mốc, điều kiện đại diện) đúng như D6 và không làm tròn trung gian (WU1).
7. Cache cũ không có `forecast` đọc được (khóa `"f"` tùy chọn); widget hiển thị không đổi (`hourly` 4 mốc, cao/thấp 24 giờ).
8. Không log dữ liệu cá nhân (`grep` log mới); không ghi nội dung thông báo ở debug receiver.
9. Không có file ngoài "Vùng file"; manifest/strings/Hilt chỉ thêm.

---

### Task 1: Mô hình, quy tắc thuần và kho lưu hai công tắc (chưa UI, chưa lịch) (code xong 2026-11-13, chờ review điều phối)

**Bắt buộc kiểm tay:** không
**Files:** `domain/model/weather/WeatherNotification.kt`, `domain/weather/WeatherNotificationPlanner.kt`, `WeatherForecastSummarizer.kt`, `WeatherPreferencesRepository.kt`, `data/weather/WeatherPrefsKeys.kt`, `WeatherPreferencesRepositoryImpl.kt`
**Interfaces:** Produces: `WeatherNotificationKind { DAILY(7, 0), TOMORROW(21, 0) }` (giờ cố định, tên enum lưu); `WeatherNotificationSettings(daily, tomorrow)` mặc định tắt; `WeatherPreferencesRepository.notifications: Flow<WeatherNotificationSettings>`, `setNotification(kind, enabled)`, `lastShownDay(kind)`/`markShown(kind, day)` (chuỗi `yyyy-MM-dd` giờ máy); `WeatherNotificationPlanner.nextTrigger(...)`; `WeatherForecastSummarizer.summarize(snapshot, kind, nowMillis): ForecastSummary?` (cao/thấp °C thô, tình trạng, khoảng có mưa theo giờ địa điểm, không làm tròn). Consumes: `WeatherSnapshot`, `GeoPlace`.
- [x] **Step 1:** Viết mô hình và hai hàm thuần theo D6/D7 (`java.time`, không `android.*`); KDoc ghi quy tắc từng ngưỡng.
- [x] **Step 2:** Khóa DataStore mới trong `WeatherPrefsKeys` (hai boolean, hai chuỗi ngày); `distinctUntilChanged` như `unit`.
- [x] **Step 3:** Build xanh. Không có UI để kiểm: kiểm bằng đọc và đối chiếu bảng ví dụ ghi trong KDoc (ít nhất: ngày mai khi snapshot chỉ 3 mốc thì `null`; múi giờ lệch; mốc qua nửa đêm).
- [x] **Step 4: Kịch bản kiểm (Live 4)** — chưa có gì chạy trên máy ở Task này; ghi "không kiểm tay" vào nhật ký, kiểm sau ở Task 3, 4.

### Task 2: Dữ liệu dự báo đủ cho tóm tắt + `snapshotForNotification` (code xong 2026-11-13, chờ review điều phối)

**Bắt buộc kiểm tay:** không
**Files:** `OwmParser.kt` (`FORECAST_COUNT` 12, lưu `forecast`), `WeatherModels.kt`, `WeatherStore.kt`, `WeatherRepository.kt` (KDoc hợp đồng), `WeatherRepositoryImpl.kt`
**Interfaces:** Produces: `WeatherSnapshot.forecast: List<HourlyPoint> = emptyList()`; `suspend fun snapshotForNotification(placeId: String): WeatherSnapshot?`. Consumes: `dueReason`, `doRefresh`, `places.awaitLoaded()`.
- [x] **Step 1:** `cnt` 12; `forecast` = toàn bộ mốc đã parse; `hourly` (4 mốc) và `highC/lowC` (8 mốc) **không đổi**. `WeatherStore`: ghi khóa `"f"`; đọc thiếu khóa = rỗng (cache hiện có đọc được, `VERSION` giữ 2).
- [x] **Step 2:** `snapshotForNotification`: `loaded.await()`; trong `mutex`: nếu `hasKey` và `dueReason(...) != null` (kể cả "cache thiếu `forecast`" coi như chưa hợp lệ cho thông báo nếu đến hạn: **không** ép tải khi chưa đến hạn, giữ quy tắc 2 giờ) thì `doRefresh`; trả snapshot của mục hợp lệ hoặc `null` (không khóa, chưa xác định vị trí, chưa có dữ liệu). **Không** gọi `locationUpdater`. Sửa KDoc `WeatherRepository`: "widget đang hiện **hoặc thông báo thời tiết** mới kích hoạt làm mới".
- [x] **Step 3:** `grep` log mới không có dữ liệu cá nhân; build xanh.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — (a) cài đè lên bản `main` có `weather.json` cũ: widget vẫn hiện số, **không** thêm lệnh mạng ngoài chu kỳ (so `attempt` trước/sau mở Home); (b) sau một lần làm mới, `adb shell run-as gambi.global.os26launcher cat no_backup/weather.json` có khóa `"f"` với 12 mốc (không dán nội dung/tọa độ vào báo cáo: chỉ đếm số mốc); (c) widget 2×2 và 4×2 không đổi (4 mốc, cao/thấp 24 giờ như trước); (d) đổi °C/°F vẫn không gọi mạng.

### Task 3: Lịch báo thức, receiver, job, quyền manifest (chưa hiện nội dung thật; kiểm cơ chế) (code xong 2026-11-13; chờ điều phối đo máy)

**Bắt buộc kiểm tay:** **có** — lý do (mục 4.2, loại 1): mọi Task sau xây trên giả định "báo thức không chính xác + job nổ đáng tin trên máy thật (Live 4 và đặc biệt Oppo A3x/ColorOS: sau boot, sau vuốt khỏi đa nhiệm, sau Doze)"; chỉ máy thật trả lời được (F3, F10 chưa đo); sai thì phải đổi sang phương án C và làm lại Task 3–5. Điều phối đo (chủ dự án kiểm bài cuối).
**Files:** `data/weather/notify/AlarmWeatherNotificationScheduler.kt`, `domain/weather/WeatherNotificationScheduler.kt`, `ui/weather/notify/WeatherNotificationReceiver.kt`, `WeatherNotificationJobService.kt`, `di/WeatherModule.kt`, `LauncherApp.kt`, `AndroidManifest.xml`, `app/src/debug/*` (receiver gỡ lỗi), `strings.xml` (khối `wn_`)
**Interfaces:** Produces: `WeatherNotificationScheduler.rearm()`; `WeatherNotificationReceiver` (`@AndroidEntryPoint`, hành động riêng `...WN_FIRE` + extra `kind`, và các hành động hệ thống ở D7); job id cố định mỗi loại. Consumes: Task 1 (planner, prefs).
- [x] **Step 1:** `rearm()`: với mỗi loại bật → `nextTrigger` → `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …, PendingIntent.getBroadcast(requestCode theo loại, FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE))`; tắt → `cancel`. Chạy trên IO (đọc DataStore), không chặn luồng chính.
- [x] **Step 2:** Receiver: `super.onReceive` (Hilt) trước; hành động `WN_FIRE`: gọi `rearm()` rồi `JobScheduler.schedule` job của loại (NETWORK_ANY, `setOverrideDeadline` 20 phút); hành động hệ thống (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`): `rearm()`. Dùng `goAsync()` chỉ để chờ ghi lịch xong (ngắn, < 10 giây, F7).
- [x] **Step 3:** `JobService` (tạm: ghi log mã loại `fired:<kind>` và kết thúc, nội dung thật ở Task 4). `LauncherApp`: một `appScope.launch { scheduler.rearm() }` ở IO. Manifest: hai quyền (D5/D7), receiver `exported=false` với intent-filter các hành động hệ thống, service `exported=false` + `BIND_JOB_SERVICE`. Debug receiver (`exported=true`, `android:permission=DUMP`, như CP): hành động `WN_DEBUG_FIRE` kèm `kind` chạy đúng đường `WN_FIRE` ngay (không đổi giờ máy).
- [ ] **Step 4: Kịch bản kiểm gộp Task 2 bước 4 + Task 3 (điều phối chạy, một phiên Live 4, rồi Oppo cho ca có đánh dấu O).** Đặt `P=gambi.global.os26launcher`. Chuẩn bị: máy rảnh (chủ dự án xác nhận), sao lưu dữ liệu app trước; bản debug nhánh@commit; có "Vị trí hiện tại" xác định và khóa OWM; mỗi lần thử `adb logcat -c` rồi `adb logcat -s WN:I WeatherRepo:W` (log chỉ có tên hành động, mã loại, số; không dữ liệu cá nhân). Công cụ debug (cần `-p $P`, DUMP-guarded): `adb shell am broadcast -p $P -a $P.WN_DEBUG_ENABLE --es kind DAILY --ez enabled true` (bật/tắt + đặt lại lịch; `--es kind TOMORROW` cho loại kia), `-a $P.WN_DEBUG_FIRE --es kind DAILY` (chạy đúng đường báo thức nổ ngay), `-a $P.WN_DEBUG_SHIFT --es kind DAILY --ei minutes 3` (dời báo thức sang now+3 phút; **đừng mở app trong lúc chờ**, mở app sẽ đặt lại về 07:00), `-a $P.WN_DEBUG_STATE` (log công tắc, ngày đã đăng, giờ nổ planner sẽ đặt). Xem báo thức thật: `adb shell dumpsys alarm | findstr gambi.global.os26launcher` (mỗi báo thức hiện loại RTC_WAKEUP và giờ nổ), xem job: `adb shell dumpsys jobscheduler | findstr gambi.global.os26launcher`.
  **A. Task 2 bước 4 (không cần công tắc).** (A1) Cài đè lên bản `main` có `weather.json` cũ: widget vẫn hiện số; so `attempt` trước/sau mở Home: không thêm lệnh mạng ngoài chu kỳ. (A2) Sau một lần làm mới (chờ đến hạn, hoặc lùi `entries.current.attempt` 2 giờ 5 phút + `force-stop` + mở Home): đếm `"e":` trong `weather.json` bằng PowerShell `([regex]::Matches((adb shell run-as $P cat no_backup/weather.json), '"e":')).Count`: mỗi địa điểm có dữ liệu có 16 (4 mốc `h` + 12 mốc `f`); chỉ báo số đếm, không dán nội dung/tọa độ. (A3) Widget 2x2 và 4x2 không đổi (4 mốc, cao/thấp 24 giờ). (A4) Đổi °C/°F: `attempt` và `at` giữ nguyên.
  **B. Task 3, KHÔNG khởi động lại máy.** (B0) Chưa bật công tắc nào: `dumpsys alarm` không có báo thức của app (bản cài mới); `WN_DEBUG_STATE` log hai công tắc false. (B1) Bật DAILY và TOMORROW bằng `WN_DEBUG_ENABLE`: `dumpsys alarm` có đúng hai báo thức, giờ nổ khớp 07:00 và 21:00 (giờ máy) của lần kế tiếp (ghi giờ đặt); `WN_DEBUG_STATE` khớp. Tắt TOMORROW: còn một; bật lại. (B2) `WN_DEBUG_FIRE` DAILY: log `recv:WN_FIRE:DAILY` rồi `fired:DAILY`; `dumpsys alarm` cho DAILY giờ nổ đã sang NGÀY MAI (chuỗi không đứt, không nổ lặp); `dumpsys jobscheduler` thấy job id 7200 đã chạy xong. Có/không có mạng đều thấy `fired`; ngoại tuyến (máy bay) thì job chỉ chạy khi hết hạn chót 20 phút (ghi thời gian: đo). (B3) **Chu kỳ báo thức thật:** `WN_DEBUG_SHIFT` DAILY 3 phút, tắt màn hình, không mở app: ghi giờ đặt T0+3 và giờ log `recv:WN_FIRE:DAILY` (đo độ trễ). Lặp trên Live 4 (API 30) và **O** Oppo (API 34: áp quy tắc "trong 1 giờ" của Android 12+, F1). (B4) **Doze:** `WN_DEBUG_SHIFT` DAILY 3 phút trước; `adb shell dumpsys battery unplug`; `adb shell dumpsys deviceidle force-idle` (báo "forced in to idle mode"; `dumpsys deviceidle get deep` = IDLE); chờ quá 3 phút: nổ không, trễ bao lâu (đo F3); **khôi phục ngay** `adb shell dumpsys deviceidle unforce` và `adb shell dumpsys battery reset`. Lặp trên **O**. (B5) **Đổi múi giờ** (xin chủ dự án trước, máy dùng chung): tắt "múi giờ tự động", chọn múi lệch (vd UTC+9): log `recv:TIMEZONE_CHANGED`, `dumpsys alarm` giờ nổ dời theo giờ địa phương mới (07:00/21:00 mới); trả múi giờ cũ. Đổi giờ máy (`TIME_SET`) tương tự nếu chủ dự án đồng ý. (B6) **Cài đè** `adb install -r` cùng bản: báo thức còn hay mất; có log `recv:MY_PACKAGE_REPLACED` không (kiểm giả thuyết F6); nếu mất thì mở app (Home) rồi `dumpsys alarm` có lại (do `rearm()` lúc tiến trình tạo). (B7) `adb shell am force-stop $P`: `dumpsys alarm` rỗng (hệ thống hủy báo thức khi dừng ép buộc, đọc tài liệu/chưa đo), mở lại app: có lại hai báo thức. (B8) **O** (và Live 4): `WN_DEBUG_SHIFT` 3 phút rồi **vuốt app khỏi đa nhiệm** (không force-stop), chờ: nổ không. (B9) Tắt cả hai công tắc: `dumpsys alarm` không còn báo thức của app.
  **C. Cuối cùng, RIÊNG, cần chủ dự án đồng ý trước (khởi động lại máy dùng chung):** bật hai công tắc, `adb reboot`, mở khóa, **không mở app**, chờ ~2 phút: log `recv:BOOT_COMPLETED` và `dumpsys alarm` có hai báo thức (F5). **O:** lặp hai lần với "Tự khởi động" bật rồi tắt cho app (cài đặt ColorOS). Ghi vào bảng: máy, API, ca, kết quả đo (giờ đặt/giờ nổ).

### Task 4: Đăng thông báo thật (use case, kênh, nội dung, chạm) (code xong 2026-11-13; chờ điều phối đo máy)

**Bắt buộc kiểm tay:** không
**Files:** `domain/usecase/PrepareWeatherNotificationUseCase.kt`, `ui/weather/notify/WeatherNotifier.kt`, `NotificationPermission.kt`, `WeatherNotificationJobService.kt`, `res/drawable/ic_stat_weather.xml`, `strings.xml` (khối `wn_`), debug receiver
**Interfaces:** Produces: `PrepareWeatherNotificationUseCase(kind, nowMillis): ForecastSummary?` (kiểm `lastShownDay`, ân hạn; lấy snapshot qua `snapshotForNotification("current")` với hạn chót 30 giây; lỗi thì thử cache ≤ 12 giờ; thiếu thì `null`); `WeatherNotifier.show(kind, summary)`/`ensureChannels()`. Consumes: Task 1, 2, 3.
- [x] **Step 1:** Trước khi tải: nếu `!areNotificationsEnabled()` hoặc kênh chặn → bỏ (log `skipped:blocked`), **không** tải.
- [x] **Step 2:** Use case theo D3/D6; ghi `markShown(kind, hôm nay)` **chỉ khi** thật sự đăng.
- [x] **Step 3:** `WeatherNotifier`: hai kênh `IMPORTANCE_LOW` trong một nhóm; builder theo D6 (tiêu đề, nội dung, BigText khi có mưa, `setPublicVersion` không tên địa điểm, PendingIntent tới Home, `setOnlyAlertOnce`, `setAutoCancel`, id cố định mỗi loại). Chuỗi `wn_` có thông số (tên, tình trạng qua `weather_cond_*` đã có, nhiệt độ + ký hiệu qua `TemperatureUnit`).
- [x] **Step 4:** `JobService` gọi use case + notifier trên `appScope`, `jobFinished(params, false)` ở `finally`; hủy coroutine khi `onStopJob`.
- [ ] **Step 5: Kịch bản kiểm (Live 4, API 30; điều phối).** Ghi chú công cụ: `WN_DEBUG_FIRE` mặc định `force` (bỏ kiểm khung giờ đăng và ngày đã đăng, KHÔNG ghi "đã đăng"), nên đăng được bất kỳ lúc nào; thêm `--ez force false` để thử đúng đường sản xuất (chỉ đăng trong khung 07:00-10:00 / 21:00-24:00 giờ máy và chưa đăng hôm nay, rồi ghi ngày đã đăng; ngoài khung log `result:<KIND>:skipped:nodata`). Log kết quả: `result:<KIND>:shown` hoặc `skipped:off|blocked|nodata`. Chuẩn bị: bản debug, một địa điểm "Vị trí hiện tại" đã xác định, có mạng, khóa OWM. (a) `WN_DEBUG_FIRE` DAILY với cache còn mới (< 2 giờ): thông báo hiện **không** có lệnh mạng mới (`attempt` trong `weather.json` giữ nguyên); (b) lùi `entries.current.attempt` **2 giờ 5 phút** (không 1h59: bài học WX) rồi `force-stop` và bắn lại: đúng **một** lần tải (`attempt` mới), thông báo hiện; (c) đơn vị: °C rồi °F, văn bản đúng "24°C"/"75°F" cả cao/thấp; (d) TOMORROW: có "Ngày mai", cao/thấp khác "24 giờ tới" của widget (là quy tắc ngày dương lịch của địa điểm); (e) máy bay + cache cũ > 2 giờ nhưng ≤ 12 giờ: thông báo hiện từ cache; cache > 12 giờ: không hiện, log `skipped:nodata`; (f) khóa thiếu / vị trí chưa xác định: không hiện, không lệnh mạng; (g) đăng lần hai cùng ngày không tạo trùng (cùng id thay thế); với `--ez force false` trong khung giờ: lần đầu `shown`, lần hai `skipped:nodata` (`lastShownDay` chặn); (h) chạm thông báo: Home lên trước (đang ở app khác, đang ở Home, đang mở Cài đặt); thông báo tự biến mất; (i) màn hình khóa: nội dung riêng tư ẩn tên địa điểm khi cài đặt hệ thống ẩn nội dung nhạy cảm (chỉ kiểm nếu máy bật khóa; ghi "không kiểm" nếu không); (j) `adb shell dumpsys notification --noredact` không dùng (lộ nội dung): thay bằng nhìn trực tiếp trong ngăn thông báo; (k) `grep` log: không có tên địa điểm/tọa độ.

### Task 5: Cài đặt → Thời tiết → nhóm "Thông báo" (công tắc, quyền, chú thích) (code xong 2026-11-13; chờ điều phối đo máy)

**Bắt buộc kiểm tay:** không (cử chỉ chạm thường; quyền do hệ thống hiện hộp)
**Files:** `ui/settings/WeatherSettingsScreen.kt`, `WeatherSettingsViewModel.kt`, `SettingsRow.kt` (hằng id), `NotificationPermission.kt`, `strings.xml` (khối `wn_`)
**Interfaces:** Consumes: `WeatherPreferencesRepository.notifications`, `WeatherNotificationScheduler.rearm()`, `WeatherRepository.hasKey`, `WeatherPlacesRepository.places`. Produces: hai `SettingsRow.Switch` + các `Footer`.
- [x] **Step 1:** Nhóm "Thông báo" sau "Địa điểm theo dõi" (trước "Đơn vị" hoặc sau: theo thứ tự nháp Figma 1.4 là sau "Địa điểm theo dõi"): hai công tắc "Dự báo hằng ngày (07:00)" và "Dự báo ngày mai (21:00)", kèm `Footer`: nói rõ giờ có thể trễ (D1), dùng "Vị trí hiện tại", không vị trí nền, đơn vị theo cài đặt.
- [x] **Step 2:** Bật công tắc theo D5 (xin quyền / mở cài đặt thông báo / từ chối thì về tắt + Toast; `registerForActivityResult` đăng ký lúc dựng theo công thức G2d; cờ "đã xin" để phân biệt từ chối vĩnh viễn như `LocationSettingsScreen`). Ghi prefs rồi `rearm()`. Không khóa → ẩn công tắc, chỉ `Footer` "Thông báo cần khóa OpenWeatherMap". "Vị trí hiện tại" chưa xác định → `Footer` nhắc đặt ở Cài đặt → Vị trí. Hệ thống đang chặn thông báo (đọc lại ở `onResume`) → `Footer` có hướng dẫn.
- [x] **Step 3:** Gỡ listener trước khi `isChecked` (đã có trong adapter, bài học M8a Task 3); hộp thoại/launcher dọn ở `onDestroy`.
- [ ] **Step 4: Kịch bản kiểm (Live 4, API 30; Oppo A3x, API 34, cho quyền runtime).** (a) Live 4: bật công tắc → không hộp xin; thông báo bật sẵn: `dumpsys alarm` có báo thức; tắt thì mất; (b) Live 4: tắt thông báo của app trong cài đặt hệ thống rồi bật công tắc: Toast + mở cài đặt thông báo; công tắc không bật; (c) **Oppo A3x (API 34, bản cài mới hoặc `pm revoke` theo tài liệu F8)**: bật công tắc → hộp xin quyền; Cho phép → công tắc bật + báo thức; Từ chối một lần → công tắc tắt + Toast; Từ chối lần hai/"không hỏi lại" → mở cài đặt thông báo của app; cấp trong cài đặt rồi quay lại: bật được; (d) thu hồi quyền khi công tắc đã bật: màn hiện chú thích; báo thức nổ thì không tải (log `skipped:blocked`), không crash; (e) giữ trạng thái qua `force-stop`/khởi động lại màn; xoay màn hình khi hộp xin hiện (Home `nosensor`; màn Cài đặt xoay được: kiểm); (f) build không khóa (bản local không có `OWM_API_KEY`) — chỉ đọc code nếu không dựng được: ghi "chỉ code bảo vệ".

### Task 6: Knowledgement, rà cuối, đóng nhánh

**Bắt buộc kiểm tay:** không
**Files:** `docs/knowledgements/WN-weather-notifications.md` (theo mẫu mục 8: sơ đồ Mermaid lịch báo thức → receiver → job → use case → notifier; bảng "Android hành vi đã kiểm" kèm nhãn đo/đọc tài liệu/suy luận cho từng dòng F1–F10 và D7; bảng ngân sách API), plan (bảng Review Focus, Đề xuất tài liệu chung)
- [x] **Step 1:** Viết knowledgement; **mục "Chưa kiểm"** liệt kê mọi ca ColorOS/Doze/boot chưa đo.
- [x] **Step 2:** Rà `git diff --stat` so với "Vùng file"; `grep` log và chuỗi; điền "Bằng chứng Review Focus".
- [ ] **Step 3:** Rebase do điều phối (không merge `main` vào nhánh, mục 11); build lại.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy **Vsmart Live 4** (rảnh, chủ dự án xác nhận; sao lưu dữ liệu app trước) cho: Task 2 (a)-(d), Task 3 (a)-(d)(g)(i), Task 4 (a)-(k), Task 5 (a)(b)(d)(e); máy **Oppo A3x** cho: Task 3 (c)(d)(e)(f)(h), Task 5 (c). Dữ liệu: "Vị trí hiện tại" đã xác định, có mạng, bản debug nhánh@commit. Thứ tự thao tác tự nhiên: cài → Cài đặt → Thời tiết → bật công tắc → bắn thử bằng debug receiver → chạm thông báo → đổi °F → ngoại tuyến → khởi động lại máy → để qua 07:00 thật/21:00 thật (một đêm: ghi giờ nổ thực). Hồi quy: widget thời tiết (số, 4 mốc, cao/thấp 24 giờ, đổi °C/°F không gọi mạng, chọn địa điểm), Cài đặt → Vị trí, các màn Cài đặt khác, thời gian khởi động Home (so `Displayed` trước/sau trên cùng bản debug: thêm `rearm()` không được thấy rõ).
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** (điền khi đóng)

### Kịch bản gộp Task 2-5 (một đợt đo; điều phối chạy; thứ tự: không quyền + không khởi động lại trước, bước cần chủ dự án đồng ý ở cuối)

Đặt `P=gambi.global.os26launcher`. Chuẩn bị: máy rảnh (chủ dự án xác nhận), sao lưu dữ liệu app; bản debug nhánh@commit (có khóa OWM); "Vị trí hiện tại" xác định; `adb logcat -c` rồi `adb logcat -s WN:I WeatherRepo:W` (log chỉ có tên hành động, loại, kết quả; không dữ liệu cá nhân). Công cụ debug: `adb shell am broadcast -p $P -a $P.WN_DEBUG_<FIRE|SHIFT|ENABLE|STATE> --es kind DAILY|TOMORROW [--ei minutes N] [--ez enabled true|false] [--ez force false]`; xem báo thức/job: `adb shell dumpsys alarm | findstr gambi.global.os26launcher`, `adb shell dumpsys jobscheduler | findstr gambi.global.os26launcher`. `WN_DEBUG_FIRE` mặc định `force` (bỏ kiểm khung giờ và ngày đã đăng).

**Phần 1 — Vsmart Live 4 (API 30; không có hộp xin quyền thông báo), không khởi động lại, không đổi giờ/múi giờ**
1. *Giao diện (Task 5):* Cài đặt → Thời tiết: cuối màn có nhóm "Thông báo" với hai công tắc tắt ("Dự báo hằng ngày – Lúc 07:00 mỗi sáng", "Dự báo ngày mai – Lúc 21:00 mỗi tối") và chú thích; Back/vào lại giữ trạng thái; `force-stop` rồi mở lại: công tắc giữ nguyên.
2. *Cache và mạng (Task 2 bước 4):* (A1) cài đè lên bản `main` có `weather.json` cũ: widget vẫn hiện số, `attempt` không đổi ngoài chu kỳ; (A2) sau một lần làm mới thật (hoặc lùi `entries.current.attempt` 2 giờ 5 phút + `force-stop` + mở Home) đếm `"e":` bằng `([regex]::Matches((adb shell run-as $P cat no_backup/weather.json), '"e":')).Count` = 16 mỗi địa điểm có dữ liệu (4 `h` + 12 `f`; chỉ báo số đếm); (A3) widget 2x2/4x2 không đổi; (A4) đổi °C/°F không đổi `attempt`/`at`.
3. *Lịch (Task 3 + 5):* trước khi bật: `dumpsys alarm` không có báo thức của app. Bật công tắc DAILY (không hộp xin quyền): `dumpsys alarm` có một báo thức giờ nổ 07:00 lần kế; bật TOMORROW: hai báo thức (07:00 và 21:00); tắt rồi bật lại từng công tắc: số báo thức đúng. `WN_DEBUG_STATE` khớp.
4. *Đăng thông báo (Task 4):* `WN_DEBUG_FIRE` DAILY với cache < 2 giờ: thông báo hiện, KHÔNG lệnh mạng mới (`attempt` giữ), log `fired:DAILY` rồi `result:DAILY:shown`, `dumpsys alarm` DAILY đã sang ngày mai (không nổ lặp); nội dung: tên địa điểm, "Hôm nay", tình trạng, cao/thấp có "°C"; kênh: Cài đặt hệ thống → Ứng dụng → Thông báo thấy nhóm "Thời tiết" với hai kênh, mức Thấp (im lặng). Lùi `attempt` 2 giờ 5 phút + `force-stop` rồi FIRE: đúng một lần tải (`attempt` mới). Đổi °F: lần FIRE kế hiện "°F" và số đúng công thức. TOMORROW: "Ngày mai", cao/thấp theo ngày kế (khác "24 giờ tới" của widget). Máy bay + cache cũ > 2 giờ nhưng ≤ 12 giờ: vẫn hiện từ cache; cache > 12 giờ: `skipped:nodata`. Chạm thông báo: Home lên trước (từ app khác, đang ở Home, đang ở Cài đặt), thông báo tự mất. `--ez force false` ngoài khung giờ: `skipped:nodata`; trong khung 07:00-10:00 / 21:00-24:00: lần đầu `shown`, lần hai `skipped:nodata`. Màn hình khóa (nếu máy có khóa): bản công khai không có tên địa điểm.
5. *Bị chặn (Task 4 + 5):* tắt thông báo của app trong cài đặt hệ thống: quay lại màn Thời tiết thấy chú thích "đang bị tắt" (cập nhật khi quay về, không cần mở lại); `WN_DEBUG_FIRE`: `result:...:skipped:blocked` và `attempt` KHÔNG đổi (không tải); tắt công tắc rồi bật lại khi đang bị chặn: Toast + mở cài đặt thông báo, công tắc không bật. Bật thông báo lại.
6. *Không địa điểm / không khóa:* xóa vị trí hiện tại (chế độ Thủ công chưa chọn, nếu thử được): chú thích "chưa xác định", FIRE `skipped:nodata`, không lệnh mạng. Build không khóa: chỉ đọc code (chú thích "cần khóa", không có công tắc).
7. *Độ tin cậy lịch (Task 3 B3-B9):* (B3) `WN_DEBUG_SHIFT` DAILY 3 phút, tắt màn hình, không mở app: ghi giờ đặt, giờ log `recv:WN_FIRE:DAILY` và giờ log `result:DAILY:shown` (phải sát nhau, KHÔNG đợi tới lúc bật màn hình; thông báo thấy khi bật màn hình; không có dòng `(job)`); (B4) Doze: SHIFT 3 phút trước, `adb shell dumpsys battery unplug`, `adb shell dumpsys deviceidle force-idle`, chờ quá 3 phút: nổ không và trễ bao lâu; **khôi phục** `adb shell dumpsys deviceidle unforce`, `adb shell dumpsys battery reset`; (B6) `adb install -r` cùng bản: báo thức còn/mất, có log `recv:MY_PACKAGE_REPLACED` không; (B7) `force-stop`: `dumpsys alarm` rỗng, mở lại app: có lại; (B8) SHIFT 3 phút rồi vuốt app khỏi đa nhiệm: nổ không; (B9) tắt cả hai công tắc: không còn báo thức.
7b. *Nâng cấp từ cache bản trước WN (Task 2/fix):* bản `main`/WU/WX đã chạy (`weather.json` v1/v2 không có khóa `f`, còn mới dưới 2 giờ), cài đè rồi bật công tắc, `WN_DEBUG_FIRE` DAILY: đúng MỘT lần tải (`attempt` đổi), đăng được (không `skipped`/`nodata`); FIRE lần hai không tải thêm (`attempt` giữ). Ngoại tuyến trong lúc cache thiếu `f`: `result:...:retry` rồi job (có dòng `(job)`) chạy khi có mạng/hết hạn chót.
8. *Hồi quy:* widget thời tiết (số, 4 mốc, cao/thấp 24 giờ), đổi địa điểm cho widget, Cài đặt → Vị trí, các màn Cài đặt khác, thời gian khởi động Home (`Displayed` trước/sau trên cùng bản debug).

**Phần 2 — Oppo A3x (API 34, ColorOS), không khởi động lại**
9. *Quyền thông báo (Task 5):* bản cài mới (hoặc `adb shell pm revoke $P android.permission.POST_NOTIFICATIONS` rồi `pm clear-permission-flags $P android.permission.POST_NOTIFICATIONS user-set` và `user-fixed`, đúng như tài liệu F8): bật công tắc → hộp xin quyền; Cho phép → công tắc bật + có báo thức; Từ chối một lần → công tắc tắt + Toast "chưa cấp quyền"; bấm bật lần hai → hộp xin lần nữa; Từ chối lần hai (hoặc "không hỏi lại") → Toast + mở cài đặt thông báo của app; cấp trong cài đặt rồi quay lại: bật được. Vuốt bỏ hộp xin: không đổi trạng thái. Thu hồi quyền khi công tắc đang bật: chú thích "bị tắt" hiện, FIRE `skipped:blocked`, không crash.
10. *Đăng và kênh:* lặp mục 4 (FIRE DAILY/TOMORROW, kênh mức Thấp, chạm mở Home, văn bản/đơn vị).
11. *ColorOS (Task 3):* lặp B3 (độ trễ; áp quy tắc "trong 1 giờ"), B4 (Doze), B6 (cài đè), B7, B8 (vuốt khỏi đa nhiệm); ghi kết quả từng ca vào bảng (máy, API, ca, giờ đặt/giờ nổ, kết quả).

**Phần 3 — CẦN CHỦ DỰ ÁN ĐỒNG Ý TRƯỚC TỪNG BƯỚC (máy dùng chung), đặt cuối cùng**
12. *Đổi múi giờ (B5), qua giao diện Cài đặt hệ thống (`service call alarm 3` không chắc phát broadcast: lần thử 2026-11-13 không có `recv:TIMEZONE_CHANGED`, kết luận chưa rõ); `WN_DEBUG_STATE` giờ in `zone`, `jvmDefault`, `nowLocal`:* tắt múi giờ tự động, chọn múi lệch (vd UTC+9): log `recv:TIMEZONE_CHANGED`, `dumpsys alarm` giờ nổ dời theo giờ địa phương mới; trả múi giờ cũ. Đổi giờ máy (`TIME_SET`) nếu chủ dự án đồng ý.
13. *Khởi động lại (C):* bật hai công tắc, `adb reboot`, mở khóa, KHÔNG mở app, chờ ~2 phút: log `recv:BOOT_COMPLETED` và `dumpsys alarm` có hai báo thức. Oppo: lặp hai lần với "Tự khởi động" bật rồi tắt.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-13 | Plan | Viết plan (đọc docs + code + 5 tài liệu chính thức của Android/AndroidX) | (docs: add WN plan) | Chờ điều phối rà |
| 2026-11-13 | Plan | Điều phối duyệt; chủ dự án trả lời Q1–Q4 đúng đề xuất (N11–N14) | (docs: record WN plan approval) | |
| 2026-11-13 | 1 | Mô hình/planner/summarizer thuần, prefs hai công tắc + ngày đã đăng. **Lệch nhỏ so với plan:** trường `WeatherSnapshot.forecast` (mặc định rỗng) thêm ở Task 1 thay vì Task 2, vì summarizer cần nó để biên dịch; parser/store vẫn ở Task 2. Build `assembleDebug` xanh | (feat: add weather notification model and prefs) | Không kiểm tay (chưa chạy trên máy) |
| 2026-11-13 | 2 | `cnt` 12 và `forecast` ở parser; khóa `f` tùy chọn ở `weather.json` (VERSION giữ 2); `snapshotForNotification` (cùng `dueReason`/`doRefresh`/mutex, KHÔNG gọi `locationUpdater`, có chú thích tại chỗ); KDoc hợp đồng sửa. Build `assembleDebug` xanh. Log mới: không thêm `Log` nào | (feat: add forecast points and snapshotForNotification) | Step 4 (kiểm Live 4) do điều phối chạy |
| 2026-11-13 | 3 | Lịch `AlarmManager` (`setAndAllowWhileIdle`, hằng `USE_EXACT_ALARMS`=false), receiver (đặt báo thức kế trước, rồi xếp job; đặt lại lịch khi BOOT/MY_PACKAGE_REPLACED/TIME_SET/TIMEZONE_CHANGED), `JobService` tạm (log `fired:<KIND>`), manifest (2 quyền, receiver, service), `LauncherApp` một lời gọi tách riêng (`dagger.Lazy`, ở nền), debug receiver (FIRE/SHIFT/ENABLE/STATE). **Lệch plan nhỏ:** receiver lấy phụ thuộc bằng `EntryPoint` thay `@AndroidEntryPoint` vì Kotlin báo `Abstract member cannot be accessed directly` khi gọi `super.onReceive` của receiver Hilt (đo được khi build); thêm `rearmAfterFire` vào interface (nếu dùng `rearm` thì trong ân hạn nó trả `now` và nổ lặp). Build xanh | (feat: schedule weather notifications with alarms) | Bắt buộc kiểm tay: kịch bản bước 4 do điều phối chạy |
| 2026-11-13 | 4 | `PrepareWeatherNotificationUseCase` (khung giờ + ngày đã đăng, khóa, địa điểm, tải qua `snapshotForNotification`, tuổi cache ≤ 12 giờ, tóm tắt), `WeatherNotifier` (hai kênh trong một nhóm, `CHANNEL_IMPORTANCE` = LOW ở MỘT hằng, văn bản, BigText khi có mưa, bản công khai không tên địa điểm, chạm mở Home), `JobService` thật (tắt/chặn thì không tải), icon `ic_stat_weather`, chuỗi `wn_`; debug FIRE có `force`. Build xanh | (feat: post weather notifications) | Step 5 do điều phối chạy |
| 2026-11-13 | 5 | Nhóm "Thông báo" ở cuối màn Thời tiết (sau "Đơn vị"): hai công tắc + chú thích (gợi ý chung, chưa xác định vị trí, bị chặn); không khóa thì chỉ chú thích; bật công tắc: xin `POST_NOTIFICATIONS` (API 33+, `registerForActivityResult` đăng ký lúc dựng, từ chối vĩnh viễn thì mở cài đặt thông báo), hoặc API thấp/bị tắt ở hệ thống thì Toast + mở cài đặt thông báo; `WeatherSettingsViewModel.setNotification` ghi prefs, tạo kênh khi bật, `rearm()`. Chú thích "bị chặn" đọc lại ở `onResume` của khung. Build xanh | (feat: add weather notification settings) | Kịch bản gộp Task 2-5 ở mục trước "Nhật ký" |
| 2026-11-13 | 4-5 sửa | Theo kết quả đo Live 4 của điều phối: (1) cache thiếu `f` coi như đến hạn MỘT lần cho đường thông báo (`needsForecastOnce`); (2) đăng ngay trong `goAsync` ở receiver qua `WeatherNotificationDispatcher` (chờ tối đa 8 giây), job chỉ khi RETRY; `snapshotForNotification` có `timeoutMs` (tải ở scope ứng dụng); (3) `TimeZone.setDefault(null)` khi `TIMEZONE_CHANGED`, `WN_DEBUG_STATE` in zone/jvmDefault/nowLocal. Build xanh | (fix: post weather notifications from the alarm receiver) | Cần đo lại: B3, Doze, mục 7b |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | `snapshotForNotification` không gọi `locationUpdater`; cùng `dueReason`/`doRefresh`/mutex | Đọc code (chú thích tại chỗ; `needsForecastOnce` là ngoại lệ một lần có điều kiện); **đo được (Live 4, bce6875)**: DAILY với cache quá 2h05 tải đúng một lần, TOMORROW ngay sau không tải (`attempt` giữ); cache v1 thật của chủ dự án tải đúng một lần | Đạt (đo được) |
| 2 | Chuỗi báo thức: `rearm()` idempotent; báo thức kế đặt trước; tắt thì hủy; `FLAG_IMMUTABLE` | Đọc code; **đo được (Live 4)**: 0 báo thức trước khi bật, một mỗi loại sau khi bật (DAILY kế 2026-11-14 07:00, TOMORROW 2026-11-13 21:00), tắt về 0; `force-stop` → 0 rồi mở app → 2 | Đạt (Live 4); Oppo chưa kiểm |
| 3 | `lastShownDay` và ân hạn 3 giờ: không đăng trùng, không đăng 07:00 lúc 15:00 | Đọc code (`Planner.withinWindow`, `Dispatcher`); **đo được**: đường sản xuất ngoài khung 21:00–24:00 cho `result:TOMORROW:skipped` (16:43:13.617); ca trong khung hai lần liên tiếp (`shown` rồi `skipped`) chưa chạy | Một phần: ngoài khung đo được; trong khung chỉ code bảo vệ |
| 4 | Ca giờ/múi giờ: `TIME_SET`, `TIMEZONE_CHANGED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` cùng đường `rearm()`; không việc nặng trong `onReceive` | Đọc code; **đo được**: `MY_PACKAGE_REPLACED` có log `recv` và báo thức còn nguyên khi cài đè không mở app. `TIMEZONE_CHANGED` (đổi bằng `service call alarm 3`) không có log: chưa kết luận; `TIME_SET`, `BOOT_COMPLETED` chưa chạy | Một phần (chờ đổi múi giờ qua Cài đặt và khởi động lại) |
| 5 | `areNotificationsEnabled()` + kênh trước khi tải; từ chối quyền không để công tắc "bật giả"; không crash khi thu hồi | Đọc code; kênh LOW đo được (`importance=2`); ca bị chặn, hộp xin quyền Android 13+, thu hồi: chưa chạy (cần Oppo; Live 4 không có hộp xin) | Chỉ code bảo vệ |
| 6 | Quy tắc tóm tắt (ngày theo múi giờ địa điểm, ngưỡng mốc, điều kiện đại diện) đúng D6, không làm tròn trung gian | Đọc code + KDoc có ví dụ; **đo được**: "Hôm nay" `Ít mây, cao 23°C, thấp 21°C`, "Ngày mai" `Nhiều mây, cao 28°C, thấp 19°C` (Hà Nội), khác cao/thấp 24 giờ của widget. Địa điểm lệch múi giờ, qua nửa đêm: chưa chạy trên máy | Đạt phần đo; biên chỉ code bảo vệ |
| 7 | Cache cũ không có `forecast` đọc được; widget không đổi | **đo được**: cài đè lên cache thật v1: widget vẫn hiện số, `weather.json` v2 có `"e":` = 16 (4 `h` + 12 `f`) sau một lần làm mới | Đạt (Live 4) |
| 8 | Không log dữ liệu cá nhân | `git grep "Log\."` ở `ui/weather/notify`, `data/weather/notify`, debug receiver (2026-11-13): chỉ tên hành động, mã loại, kết quả, tên lớp ngoại lệ, số phút, `zone` (múi giờ hệ thống, không phải địa điểm) | Đạt |
| 9 | Không có file ngoài "Vùng file"; manifest/strings/Hilt chỉ thêm | `git diff --stat main`: 29 file, đều trong "Vùng file" (không `HomeActivity`, `data/layout`, `core/glass`, lớp widget); manifest/strings/`WeatherModule`/`SettingsRow` chỉ thêm | Đạt |

## Câu hỏi cho chủ dự án

1. **Độ trễ cho phép của thông báo (Q1).** Báo thức không chính xác trên Android 12+ có thể nổ trễ tới ~1 giờ (F1, đọc tài liệu; Live 4 API 30 và Oppo API 34 chưa đo). Muốn đúng phút phải xin quyền đặc biệt "Báo thức & lời nhắc" (người dùng tự bật, mỗi lần cài mới phải bật lại) và tốn pin hơn. [Đề xuất: **chấp nhận trễ tới 1 giờ**, đo độ trễ thật ở Task 3; chỉ xét báo thức chính xác nếu đo trên Oppo cho thấy trễ thường xuyên quá 30 phút.]
2. **Âm báo của thông báo (Q2).** Kênh `IMPORTANCE_LOW` = im lặng, hiện trong ngăn thông báo và icon trạng thái; `DEFAULT` = có tiếng. Mức quan trọng của kênh không đổi được bằng code sau khi tạo. [Đề xuất: **LOW (im lặng)**, nhất là bản 21:00.]
3. **Chạm thông báo (Q3).** Hiện chỉ mở Home vì mở thẳng bảng Thời tiết cần sửa `HomeActivity` (độc quyền G3c đợt này). [Đề xuất: **mở Home**, làm "mở bảng Thời tiết" ở nhánh nhỏ sau khi G3c merge.]
4. **Gợi ý "Tự khởi động"/pin cho ColorOS (Q4).** Nếu đo trên Oppo cho thấy báo thức mất sau boot hoặc sau vuốt khỏi đa nhiệm, có thể thêm dòng chú thích trong Cài đặt → Thời tiết trỏ tới cài đặt pin của ứng dụng. [Đề xuất: **chưa làm**; quyết sau khi có số đo Task 3.]

Không có câu hỏi nào khác; không đổi schema Room; không thư viện mới.

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: dòng 17 → **Xong**: hai thông báo 07:00 và 21:00 (cửa sổ báo thức không chính xác, có thể trễ), "Vị trí hiện tại" như đang lưu, cache + quy tắc 2 giờ; cảnh báo thời tiết xấu vẫn bỏ; mở bảng Thời tiết từ thông báo để sau.
- `project_structure.md`: thêm các file ở "Vùng file" (`domain/weather/WeatherNotification*`, `data/weather/notify/`, `ui/weather/notify/`); quyền mới `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; khóa DataStore thông báo; `weather.json` có khóa `"f"` (12 mốc); `WeatherSnapshot.forecast`.
- `Figma-Draft.md`: mục 4 dòng 1.4: thông báo đã có (hai loại), cảnh báo xấu bỏ; mục 5 ghi nhánh WN.
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement. Dự kiến: `permissions-and-device-testing` (báo thức/Doze/ColorOS đã đo, quyền thông báo Android 13 theo máy, `deviceidle`/`dumpsys alarm`), `widgets` (hợp đồng repository thời tiết có hai nguồn kích hoạt; snapshot dự báo 12 mốc), `settings-and-data` (xin quyền từ công tắc).
