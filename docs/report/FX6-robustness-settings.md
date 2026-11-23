# FX6 — Độ bền coroutine, thời tiết và hàng "Launcher mặc định" (B1 phần scope/thời tiết, B12, S1) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ kiểm tay; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (điều phối chạy mọi bước trên máy qua adb; agent thực thi không chạy adb, không cài APK). Lệnh viết cho CMD/PowerShell (phần chạy trên máy là `sh`).

**Nhánh:** `fix/fx6-robustness-settings` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX6-robustness-settings` · **PR:** #35 (draft) · **Bắt đầu:** 2026-11-11 · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review (Ready for review, PR #35)
**Khóa:** không (không đổi schema Room, không thêm khóa DataStore, không thêm thư viện, không thêm chuỗi)

**Goal:** (1) Một ngoại lệ lạ trong `ApplicationScope` không còn giết cả launcher: có `CoroutineExceptionHandler` chỉ ghi loại ngoại lệ và một khung stack, không ghi thông điệp hay dữ liệu cá nhân. (2) Thời tiết không còn là đường sập: mọi lỗi lạ khi làm mới, tìm thành phố, tra tên nơi hoặc lấy vị trí đều thành `WeatherError` (`SERVER`/`PARSE`/`NETWORK`), JSON có `"NaN"` thành `PARSE`, `URL(...)` hỏng thành lỗi chứ không thoát ra ngoài. (3) S1: `ui/weather` không còn import `data`; vị trí thô đi qua một interface ở `domain` được nối ở `di`. (4) B12: bấm hàng "Launcher mặc định" khi hệ thống không hiện hộp thoại (đã chọn "Không hỏi lại") thì mở màn Launcher mặc định của hệ thống (rồi Cài đặt chung), còn người dùng tự bấm Hủy trên hộp thoại thì không bị đẩy đi đâu.

**Architecture:**
- B1 gồm hai lớp phòng thủ, không lớp nào thay lớp kia: (a) handler ở `ApplicationScope` (`di/AppModule`) là lưới cuối, chỉ làm "không sập"; (b) hợp đồng của `WeatherRepository` được siết lại: `doRefresh`, `search`, `reverse` không ném gì ngoài `WeatherException` (trừ hủy coroutine), nên các `catch (e: WeatherException)` ở `WeatherSheetViewModel` (không có handler vì chạy trong `viewModelScope`) trở thành đúng. `WeatherHttp` dựng `URL` bên trong `try`; `OwmParser` kiểm số hữu hạn trước khi làm tròn.
- S1: interface `CoarseLocationSource` (giữ tên) + `CoarseFix` sang `domain`; lớp hiện thực đổi tên `CoarseLocationSourceImpl` (giữ ở `data/weather`), `@Binds` ở `di/WeatherModule`. `WeatherSheetViewModel` chỉ đổi dòng import.
- B12: `DefaultLauncherRepository` tách hai đường (xin role, và các màn hệ thống dự phòng); `SettingsActivity` đo thời gian từ lúc mở hộp thoại xin role tới lúc nhận kết quả. Kết quả `RESULT_CANCELED` trong thời gian ngắn hơn ngưỡng người dùng không thể phản ứng kịp, mà app vẫn chưa là launcher mặc định, thì mở màn dự phòng. Ngưỡng chọn bằng số đo ở Task 4 (bắt buộc kiểm tay).

**Tech Stack:** không thêm gì. Dùng `CoroutineExceptionHandler` (kotlinx.coroutines đã có), `SystemClock.elapsedRealtime`, `Settings.ACTION_HOME_SETTINGS` / `ACTION_SETTINGS` (đã khai trong `<queries>` của manifest, M1).

**Spec:** Audit `docs/report/Audit-Report-2026-11-10.md` mục B1 (chỉ phần `ApplicationScope` và thời tiết; phần flow bố cục do FX3), B12, cùng "Quyết định của chủ dự án (2026-11-11)" (C15 hoãn: không đụng khóa API) và "Kế hoạch sửa"; phần S1 do điều phối giao thêm (ngoại lệ duy nhất của luật `ui → domain ← data`, `project_structure.md` mục 2). Liên quan: `docs/plans/G3e-weather-widget.md` và `docs/knowledgements/G3e-weather-widget.md` (nguồn của thời tiết, hợp đồng chặn tần suất D2, quy tắc bí mật của `WeatherHttp`), `docs/plans/M1-home-activity.md` Task 3 (hàng đặt launcher mặc định, `<queries>`), `docs/lessons/widgets.md` (G3e), `docs/lessons/permissions-and-device-testing.md` (từ chối vĩnh viễn, sao lưu, `run-as`), `docs/lessons/settings-and-data.md`.

## Đã đối chiếu với code hiện tại (`b1af810`, `main` chưa đổi)

| Mục | Điều audit nói | Kết quả đọc code | Nhãn |
|---|---|---|---|
| B1 | `AppModule.kt:25` tạo scope không có handler | Đúng: `CoroutineScope(SupervisorJob() + dispatcher)`. `SupervisorJob` chặn lỗi lan sang coroutine anh em, **không** chặn lỗi tới handler của luồng. 7 nơi dùng scope này (`GlassEnvironment`, `AppRepositoryImpl`, `IconDiskCache`, `IconStyleSource`, `LabelCache`, `LayoutRepositoryImpl`, `RecentAppsRepositoryImpl`) cộng `WeatherRepositoryImpl` và `LauncherApp` (cho `WeatherWidgetHub`) | đọc code |
| B1 | `doRefresh` chỉ bắt `WeatherException` | Đúng: `WeatherRepositoryImpl.kt:153`. `doRefresh` chạy trong `scope.launch` của `refreshIfStale` (`:88`) và `init` (`:70`) nên lỗi khác thoát ra handler luồng. Cũng chạy từ `refreshNow`/`select` trong `viewModelScope` của bảng thời tiết (luồng chính, sập tương tự) | đọc code |
| B1 | `URL(...)` nằm ngoài try của `WeatherHttp` | Đúng: `OpenWeatherMapProvider.kt:97` `return URL(sb.toString())` chạy trong `url()` trước khi vào `WeatherHttp.get(url: URL)` (`WeatherHttp.kt:24`, `try` mở ở `:26`). `MalformedURLException` là `IOException` nhưng nó bị ném **trước** `WeatherHttp.get` nên không được map sang `NETWORK`; nó thoát khỏi `provider.fetch`/`search`/`reverse` nguyên dạng | đọc code |
| B1 | `roundToInt()` ném khi JSON có `"NaN"` | Đúng: `OwmParser.kt:49` và `:56`. `JSONObject.getDouble` chấp nhận chuỗi `"NaN"`; Kotlin `Double.roundToInt()` ném `IllegalArgumentException` với NaN, còn `Infinity` thì trả `Int.MAX_VALUE` (không ném, nhưng ra số vô nghĩa). `guard` (`:444`) chỉ bắt `JSONException`. Thêm: `place()` gọi `roundCoord(NaN)` ra `0.0` im lặng (địa điểm sai) | đọc code (hành vi `roundToInt` theo tài liệu Kotlin, chưa chạy; Task 1 kiểm trên máy) |
| B1 | Lỗi runtime lạ của HTTP stack hãng máy | `WeatherHttp.get` chỉ bắt `WeatherException`, `SocketTimeoutException`, `IOException` (`:345-350`); `RuntimeException` khác thoát ra. Không tái hiện được trên máy | suy luận |
| B1 | Kiểm `WeatherSheetViewModel.kt:80-89`, `:115-119` | `search` (`:78-90`) và `useCurrentLocation` (`:108-125`) chạy trong `viewModelScope.launch` (không handler) và chỉ bắt `WeatherException` (`:87`, `:117`). Hợp lệ **nếu** repository đảm bảo không ném gì khác; hiện không đảm bảo (`WeatherRepositoryImpl.search/reverse` gọi thẳng `provider`, `:125-127`). `choose`/`refresh` (`:98-102`, `:134-141`) không có try, dựa vào `select`/`refreshNow` không ném. `location.getOnce()` (`:110`) cũng không có try | đọc code |
| B1 | (phát hiện thêm) `init` của repository | `loaded.complete(Unit)` (`:81`) chỉ chạy nếu khối trước nó không ném. Nếu nó ném (hiếm: `store.load()` đã nuốt lỗi, `publish()` an toàn), mọi `refreshIfStale`/`refreshNow` sau đó treo vĩnh viễn ở `loaded.await()`. Sửa bằng `finally` | đọc code |
| B1 | (phát hiện thêm) lỗi lạ phải cập nhật `lastAttemptAt` | Nhánh `WeatherException` đặt `lastAttemptAt`/`lastResult`; nhánh lạ mới thêm phải làm tương tự, nếu không `due()` (`:131-143`) vẫn đúng hạn và nhịp phút của widget sẽ gọi lại mỗi phút, vi phạm hợp đồng D2 (sau lỗi chỉ thử lại sau 5 phút) | đọc code |
| B1 | Handler làm một số flow "đứng yên" thay vì sập | Một flow `stateIn`/`shareIn` trong scope này mà upstream ném thì coroutine chia sẻ chết, người nghe giữ giá trị cuối (suy luận từ cách `stateIn` chạy một coroutine duy nhất). Không sai so với sập, nhưng không phải là chữa: chữa thật là `catch` từng flow (FX3 làm cho flow bố cục). Ghi ở "Ngoài phạm vi" | suy luận |
| S1 | `ui/weather/WeatherSheetViewModel.kt:6` là `ui → data` duy nhất | Đúng: `grep "os26launcher\.data\." ui core domain` chỉ ra dòng này (hai kết quả khác là chú thích KDoc). `CoarseFix` (kết quả của `getOnce`) cũng nằm ở `data/weather/CoarseLocationSource.kt:22` nên phải chuyển cùng | đọc code |
| B12 | Callback kết quả rỗng | Đúng: `SettingsActivity.kt:69-70` `registerForActivityResult(StartActivityForResult()) { }`. Cả hàng "Launcher mặc định" (`:525`) và hàng gợi ý "shortcut cần launcher mặc định" của khối Tìm kiếm (`SearchSettingsBlock.kt:34`, qua `:519`) dùng `onDefaultLauncherClicked` (`:546`) nên sửa một chỗ là xong cả hai | đọc code |
| B12 | Repository luôn ưu tiên role | Đúng: `DefaultLauncherRepositoryImpl.kt:45-51` trả `createRequestRoleIntent(ROLE_HOME)` khi `isRoleAvailable` và resolve được; chỉ khi không có role mới rơi xuống `ACTION_HOME_SETTINGS`/`ACTION_SETTINGS`. Hộp thoại "không hỏi lại" không đổi `isRoleAvailable` hay resolve, nên app vẫn nhận intent role | đọc code |
| B12 | `RESULT_CANCELED` ngay lập tức khi "Không hỏi lại" | **Chưa tái hiện**: hành vi `RequestRoleActivity` của PermissionController (hộp thoại không hiện, trả `RESULT_CANCELED`) là suy luận từ AOSP, audit cũng gắn nhãn suy luận. Task 4 đo trên Live 4 (chủ dự án cho phép từ chối thật rồi khôi phục, 2026-11-11) | suy luận |
| B12 | Role không hợp lệ trả `RESULT_CANCELED` ngay, không UI | Suy luận từ AOSP (`RequestRoleActivity` kết thúc với `RESULT_CANCELED` khi role không có). Dùng làm cách thử **không đụng trạng thái từ chối thật** của chủ dự án (Task 4/5), kiểm bằng số đo | suy luận |

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- Launcher là app HOME: một ngoại lệ chưa bắt ở thời tiết (chạy theo nhịp phút của widget) làm sập process, và nếu lỗi lặp lại thì sập lặp lại ngay trên màn hình chính (cùng kiểu A1).
- `SupervisorJob` không phải "chống sập": nó chỉ cô lập các coroutine con với nhau. Handler phải đặt trong context của scope.
- Hai đường chạy thời tiết có hai kiểu bảo vệ khác nhau: `scope.launch` của app (không có catch), `viewModelScope.launch` của bảng thời tiết (chỉ bắt `WeatherException`). Chỉ thêm handler cho scope app thì đường `viewModelScope` vẫn sập; handler không thay hợp đồng.
- Quy tắc bí mật của `WeatherHttp` (G3e): URL chứa `appid`, không log URL, thân phản hồi, thông điệp ngoại lệ gốc. Thông điệp của `MalformedURLException`/`UnknownHostException` có thể chứa host hoặc URL, nên handler và lớp map lỗi chỉ được ghi **tên lớp ngoại lệ**.
- Chủ dự án chạy bản debug làm launcher mặc định hằng ngày; mọi bản sửa phải kiểm trên Live 4 mà không làm kẹt màn hình chính (xem hạ tầng kiểm ở cuối).
- File nóng: `HomeActivity`, `HomeViewModel`, `core/glass/*` không đụng. `SettingsViewModel` là file dùng chung với FX1 (đợt 1, sửa `onImagePicked`): chỉ thêm hàm.

### B1 — các phương án

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ thêm handler vào `ApplicationScope` | `CoroutineScope(SupervisorJob() + dispatcher + handler)`; handler ghi log | Một chỗ, nhỏ nhất; bao cả mọi lỗi lạ của 9 nơi dùng scope | Không đụng `viewModelScope` của bảng thời tiết (vẫn sập khi `search` gặp `MalformedURLException`); lỗi bị nuốt thì `doRefresh` bỏ dở: không cập nhật `lastAttemptAt` nên nhịp phút gọi lại mỗi phút (vi phạm D2); `_state` kẹt `Loading` | Không đủ một mình |
| B. Chỉ bắt `Exception` trong `doRefresh` và map lỗi | Đúng "Hướng sửa" của audit cho thời tiết | Sửa tận gốc đường thời tiết; giữ đúng D2 (lỗi lạ cũng tính là một lần thử) | Không bảo vệ các nơi khác dùng scope; `search`/`reverse`/`getOnce` ở đường `viewModelScope` vẫn hở | Không đủ một mình |
| **C. A + B + siết hợp đồng repository (chọn)** | Handler ở scope app (lưới cuối); `doRefresh` bắt `Exception` (ném lại `CancellationException`), map `PARSE` cho `IllegalArgumentException`, còn lại `SERVER`, và cập nhật `lastAttemptAt`; `search`/`reverse` bọc cùng bộ map; `WeatherHttp` dựng `URL` trong try; `OwmParser` kiểm số hữu hạn; `getOnce` không ném | Mỗi lớp có việc riêng: scope app = không sập; repository = trạng thái đúng và đúng D2; VM giữ nguyên `catch WeatherException` (nay đúng); log chỉ có tên lớp | Nhiều file hơn (7), nhưng mỗi file đổi nhỏ | **Chọn** |
| D. Handler gọi lại handler mặc định ở bản debug (crash để thấy lỗi) | `if (BuildConfig.DEBUG) throw e` | Lỗi không bị giấu khi phát triển | Chủ dự án dùng bản debug làm launcher mặc định: crash lặp lại trên màn hình chính, đúng thứ B1 muốn tránh; log `Log.e` đã đủ thấy | Không |
| E. `try/catch` thủ công quanh từng `launch` của 9 nơi | Mỗi nơi tự bắt | Chính xác từng chỗ | Quên một chỗ là sập; trùng lặp; flow `shareIn` vẫn cần `.catch` riêng (FX3 làm cho bố cục) | Không (xem "Ngoài phạm vi") |

**Vì sao C (cơ sở):** (1) audit B1 đề đúng hai việc: handler và `catch Exception` trong `doRefresh`; plan làm cả hai và thêm phần hợp đồng vì đọc code thấy `viewModelScope` của bảng thời tiết cũng sập (đọc code). (2) Giữ D2 là ràng buộc của dự án (KDoc `WeatherRepository`, plan G3e): lỗi lạ coi như một lần thử thất bại. (3) Quy tắc bí mật của G3e đòi log chỉ có tên lớp. (4) Handler cho `Error`: chỉ ghi log và **ném lại `VirtualMachineError`** (`OutOfMemoryError`, `StackOverflowError`), vì launcher đang hết bộ nhớ thì nuốt lỗi chỉ trì hoãn một lỗi khó hiểu hơn; kotlinx.coroutines chuyển ngoại lệ ném từ handler sang handler luồng nên process vẫn chết đúng như trước (đọc tài liệu `CoroutineExceptionHandler`, chưa kiểm trên máy: không có cách an toàn để ép OOM trên máy chủ dự án; nếu điều phối thấy lo, bỏ nhánh này là một dòng).

**Đánh đổi chấp nhận:** (1) một flow `stateIn` lỗi sẽ "đứng yên" với giá trị cuối thay vì sập (xem bảng đối chiếu); đó là cái giá của lưới cuối, chữa thật là `.catch` từng flow. (2) Lỗi lạ ở thời tiết hiện thành "lỗi máy chủ" hoặc "dữ liệu không đọc được" thay vì dấu vết rõ hơn, nhưng log có tên lớp. Xét lại khi: thêm nguồn thời tiết/proxy (C15): lớp map lỗi của repository vẫn đúng.

### B12 — các phương án phân biệt "hộp thoại không hiện" với "người dùng hủy"

Cả hai trường hợp trả `RESULT_CANCELED`, không có extra nào trong `ActivityResult` để phân biệt (đọc tài liệu `RoleManager.createRequestRoleIntent`; Android không có `shouldShowRequestPermissionRationale` cho role).

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Thời gian trôi qua | Ghi `elapsedRealtime` lúc `launch`; kết quả `RESULT_CANCELED` nhanh hơn ngưỡng T, và `!isDefaultLauncher()` → mở màn dự phòng | Sửa đúng ca hỏng, kể cả **lần bấm đầu tiên** sau khi từ chối vĩnh viễn; không cần lưu gì; người dùng tự bấm Hủy (cần nhìn hộp thoại rồi chạm) không bao giờ nhanh hơn T nếu T chọn đủ thấp; Activity bị dựng lại giữa chừng thì mất mốc → không làm gì (mặc định an toàn) | T là một con số phải đo trên máy (phụ thuộc máy chậm/nhanh); Back bấm cực nhanh khi hộp thoại vừa hiện có thể đẩy người dùng sang màn Cài đặt (vô hại: đó cũng là nơi họ có thể đặt mặc định) | **Chọn**, T bằng số đo Task 4 |
| B. Đếm số lần hủy liên tiếp (RAM hoặc DataStore) | Hủy lần thứ hai liên tiếp thì mở màn dự phòng | Không phụ thuộc đồng hồ | Lần bấm đầu tiên sau "Không hỏi lại" vẫn không làm gì (lỗi chưa sửa); người dùng hủy hai lần vì đổi ý bị đẩy đi; bộ đếm trong RAM mất khi đóng Cài đặt (Activity bị hủy), muốn giữ phải thêm khóa DataStore (ngoài phạm vi "không thêm khóa") | Không |
| C. Bỏ hộp thoại role, luôn mở `ACTION_HOME_SETTINGS` | Hết vấn đề "không hỏi lại" | Đơn giản, không phụ thuộc hành vi PermissionController | Bỏ luồng một chạm đã kiểm trên cả hai máy ở M1 Task 3 (hộp thoại chọn ngay trong app); thêm một bước chạm cho mọi người dùng chỉ để chữa một ca hiếm | Không |
| D. Mở màn dự phòng sau **mọi** `RESULT_CANCELED` | Không cần đo | Rất đơn giản | Người dùng bấm Hủy trên hộp thoại bị ném sang Cài đặt hệ thống: cảm giác sai, đúng điều yêu cầu cảnh báo | Không |
| E. Kiểm trước bằng `RoleManager` có cho phép request không | `isRoleAvailable` vẫn đúng khi bị từ chối; không có API công khai trả "đã bị từ chối vĩnh viễn" | — | Không có tín hiệu để kiểm (đọc tài liệu API, chưa thấy API tương ứng) | Không khả thi |

**Vì sao A (cơ sở):** (1) là phương án duy nhất sửa cả lần bấm đầu tiên và không đổi trải nghiệm của người dùng bình thường; (2) không thêm trạng thái bền (không khóa DataStore); (3) phụ thuộc một giả định chỉ máy thật trả lời (hộp thoại bị từ chối quay về nhanh hơn hẳn thời gian một người nhìn và chạm), nên giả định được đo trước khi code (Task 4, **bắt buộc kiểm tay** theo mục 4.2: giả định chỉ máy thật trả lời + số đo chọn tham số; nếu số đo bác bỏ, đổi sang B/D mà chưa viết dòng code nào cho A). Quy tắc chọn T ở Task 4.

**Đánh đổi chấp nhận:** (1) T cố định trong code (`CHỈNH TAY`, cùng quy ước của `WeatherHttp`); máy rất chậm có thể trả "không hiện" chậm hơn T, khi đó vẫn như hiện tại (không làm gì), không tệ hơn. (2) Không sửa được ca Oppo (Android 14) vì không kiểm trên máy đó; ghi vào "Ngoài phạm vi". (3) Khi từ chối thật lần đầu ("Không hỏi lại" chưa có), hộp thoại hiện bình thường; chỉ khi đã bị từ chối vĩnh viễn mới đi màn dự phòng.

### S1 — chọn tên và chỗ đặt

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| A. Interface `CoarseLocationSource` ở `domain/weather`, `CoarseFix` ở `domain/model/weather`, hiện thực `CoarseLocationSourceImpl` ở `data/weather`, `@Binds` ở `WeatherModule` | Giống mẫu có sẵn (`WeatherProvider`/`OpenWeatherMapProvider`, `WeatherRepository`/`WeatherRepositoryImpl`); VM chỉ đổi dòng import; `OwmParser.roundCoord` vẫn dùng ở `data` | Phải đổi tên một file (`git mv`, không xóa nội dung) | **Chọn** |
| B. Đặt interface ở `domain/repository` | Có sẵn thư mục | Thư mục đó dành cho repository của dữ liệu app; thời tiết có `domain/weather` riêng | Không |
| C. Chuyển việc lấy vị trí thành hàm của `WeatherRepository` | Bớt một interface | Trộn việc lấy vị trí (quyền, `LocationManager`) vào repository thời tiết; VM vẫn cần `hasPermission()`; đổi chữ ký của `WeatherRepository` đã được widget dùng | Không |

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | C15 không đụng: không đổi cách xử lý khóa API, `baseUrl` ở `WeatherModule` giữ nguyên trong code cuối | Chủ dự án, 2026-11-11 (audit, "Quyết định của chủ dự án") |
| 2 | Handler ghi `Log.e` gồm tên lớp ngoại lệ và đúng một khung stack đầu; không ghi thông điệp, không ghi `cause`, không ghi URL | Claude đề xuất, 2026-11-11; cơ sở: quy tắc bí mật của `WeatherHttp` (G3e), audit "Log: không chứa dữ liệu cá nhân" |
| 3 | Handler ném lại `VirtualMachineError` (OOM, stack overflow); mọi ngoại lệ khác chỉ log | Claude đề xuất, 2026-11-11 (suy luận, mục B1 ở trên) |
| 4 | Lỗi lạ ở `doRefresh` cập nhật `lastAttemptAt`/`lastResult` như `WeatherException` (nghĩa là thử lại sau 5 phút, đúng D2) | Claude đề xuất, 2026-11-11; cơ sở: KDoc `WeatherRepositoryImpl`, `due()` |
| 5 | `WeatherException` mới tạo từ lỗi lạ **không mang `cause`** | Claude đề xuất, 2026-11-11; cơ sở: quy tắc bí mật `WeatherHttp` ("không bao giờ log thông điệp ngoại lệ gốc") |
| 6 | B12 dùng phương án A (thời gian); ngưỡng T chọn theo số đo Task 4 bằng quy tắc: `T = max(300 ms, 2 x lần đo "không hiện" lớn nhất)` và phải **nhỏ hơn** lần Hủy thật nhanh nhất đo được; nếu không thỏa, dừng và báo điều phối (đổi sang B/D) | Claude đề xuất, 2026-11-11 (điều phối duyệt khi duyệt plan) |
| 7 | Chỉ kích hoạt dự phòng khi người dùng bấm hàng và **app đã mở hộp thoại role**; khi `createRoleRequestIntent()` trả null (ROM không có role) thì đi thẳng màn dự phòng, không đo thời gian (vì `RESULT_CANCELED` của màn Cài đặt khi Back là bình thường, tránh vòng lặp mở màn Cài đặt) | Claude đề xuất, 2026-11-11 |
| 8 | Không thêm chuỗi, không thêm Toast cho nhánh dự phòng; khi mọi màn dự phòng đều không mở được thì dùng chuỗi có sẵn `home_set_default_failed` | Claude đề xuất, 2026-11-11 (giữ `strings.xml` nguyên, file dùng chung) |
| 9 | Cách thử "không hiện hộp thoại" ưu tiên role giả (không đụng trạng thái từ chối thật); từ chối thật "Không hỏi lại" chỉ để xác nhận số đo, rồi khôi phục như ở "Hạ tầng kiểm" | Chủ dự án cho phép từ chối thật và khôi phục, 2026-11-11 (audit); đề xuất ưu tiên role giả: Claude, vì khôi phục từ chối thật là phần rủi ro nhất |

## Ngoài phạm vi / ghi để sau

- Flow bố cục (`shareIn` trong `LayoutRepositoryImpl`, reconciler): FX3 (Task 1 của FX3 thêm `.catch`). FX6 chỉ cung cấp handler làm lưới cuối.
- `.catch` cho các flow `stateIn`/`shareIn` còn lại trong `AppRepositoryImpl`, `IconStyleSource`, `GlassEnvironment`, `RecentAppsRepositoryImpl`: không thuộc audit B1, ghi để xét sau (handler chỉ đổi "sập" thành "đứng yên" cho chúng).
- C15 (khóa OpenWeatherMap trong APK): hoãn (quyết định chủ dự án).
- B12 trên Oppo A3x (Android 14): không kiểm; hành vi PermissionController có thể khác. Nếu chủ dự án gặp lại trên Oppo thì mở lại.
- Cache `weather.json` nhận lỗi `PARSE` lặp lại: đã có chặn tần suất 5 phút; không thêm backoff.
- Không viết lại `WeatherHttp` sang thư viện mạng (D4 của G3e: không thêm thư viện).
- Đổi tên hoặc chuyển `CoarseFix` chỉ để sửa S1, không đổi hành vi lấy vị trí (D6).

## Vùng file

Đường dẫn từ `app/src/main/java/gambi/global/os26launcher/`.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `di/AppModule.kt` | Sửa: thêm handler vào scope | Không |
| `data/weather/WeatherHttp.kt` | Sửa: `get(spec: String)` dựng `URL` trong try; thêm nhánh `RuntimeException` | Không |
| `data/weather/OpenWeatherMapProvider.kt` | Sửa: `url()` trả `String`, bỏ import `java.net.URL` | Không |
| `data/weather/OwmParser.kt` | Sửa: kiểm số hữu hạn (nhiệt độ, tọa độ), `guard` bắt thêm `IllegalArgumentException` | Không |
| `data/weather/WeatherRepositoryImpl.kt` | Sửa: `doRefresh`, `search`, `reverse`, `init`, KDoc | Không |
| `domain/weather/WeatherRepository.kt` | Sửa: chỉ KDoc (hợp đồng "chỉ ném `WeatherException`") | Không |
| `data/weather/CoarseLocationSource.kt` → `data/weather/CoarseLocationSourceImpl.kt` | Đổi tên (`git mv`), thành hiện thực của interface; `getLastKnownLocation` bắt `Exception` | Không |
| `domain/weather/CoarseLocationSource.kt` | **Tạo** (interface) | Không |
| `domain/model/weather/CoarseFix.kt` | **Tạo** (chuyển `CoarseFix` từ `data/weather`) | Không |
| `di/WeatherModule.kt` | Sửa: thêm `@Binds` cho `CoarseLocationSource` | Không |
| `ui/weather/WeatherSheetViewModel.kt` | Sửa: một dòng import | Không (FX2 sửa `WeatherSheetController`, file khác cùng thư mục) |
| `domain/repository/DefaultLauncherRepository.kt` | Sửa: thêm hai hàm | Không |
| `data/system/DefaultLauncherRepositoryImpl.kt` | Sửa: tách đường role và đường dự phòng | Không |
| `ui/settings/SettingsViewModel.kt` | Sửa: **chỉ thêm ba hàm một dòng** cạnh `createSetDefaultIntent()` (khoảng `:114`) | **Dùng chung** (FX1 sửa `onImagePicked` `:152-176`, xa chỗ này; nhánh merge sau tự rebase) |
| `ui/settings/SettingsActivity.kt` | Sửa: `requestDefault` (`:69`), `onDefaultLauncherClicked` (`:546`), một hằng số | Dùng chung có kiểm soát (không file nào khác trong đợt 1 và 2 sửa các hàm này) |
| `docs/knowledgements/FX6-robustness-settings.md` | Tạo (Task 6) | Không |
| `docs/report/FX6-robustness-settings.md` | Sửa (nhật ký, kết quả) | Không |

Không đụng: `HomeActivity.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `core/glass/*`, `data/layout/*`, `ui/weather/WeatherSheetController.kt`, `LauncherApp.kt` (bản cuối; chỉ có bản vá tạm ngoài repo khi kiểm), `AndroidManifest.xml`, `res/*`, `data/settings/*`, `ui/widget/*`. Cần file ngoài vùng thì dừng và báo.

Chạm file chung với nhánh khác: **`SettingsViewModel.kt`** (FX1, đợt 1: không chồng hàm). Không chạm file độc quyền nào.

## Global Constraints

- Không code trước khi plan được duyệt. Mỗi Task: `gradlew.bat assembleDebug` xanh trong worktree, commit tiếng Anh không dấu (`fix:`/`refactor:`), một hoặc vài commit mỗi Task, cập nhật Nhật ký, dừng và báo.
- Không thêm test, không thêm thư viện, không đổi schema Room, không thêm khóa DataStore, không thêm chuỗi. Không merge `main` vào nhánh (MultiAgent-Workflow mục 11). Không cài APK, không chạy adb: điều phối làm.
- Không sửa tài liệu chung (mục 5.4). Đề xuất ở cuối plan.
- Tên file `CoarseLocationSource.kt` đổi tên bằng `git mv`: đó là đổi tên, không phải xóa; vẫn báo điều phối ở báo cáo Task 3 (quy ước "không tự xóa file" của `AGENTS.md`).
- Quy tắc bí mật G3e giữ nguyên và mở rộng sang code mới: không log URL, khóa, thân phản hồi, thông điệp hoặc `cause` của ngoại lệ, tọa độ, tên nơi. Chỉ ghi tên lớp ngoại lệ (và một khung stack ở handler).
- Hợp đồng D2 (chặn tần suất) giữ nguyên: lỗi lạ tính là một lần thử thất bại.
- Mọi `catch (e: Exception)` trong code suspend phải ném lại `CancellationException` trước (bài học coroutine; `WeatherRepositoryImpl` hiện chưa bắt `Exception` nên chưa lộ).
- `onDefaultLauncherClicked` không được gây vòng lặp mở màn hệ thống: màn dự phòng mở bằng `startActivity` thường (không chờ kết quả).
- Mọi code tạm để kiểm (bản vá `fx6-*.patch`) nằm ngoài repo, áp bằng `git apply` ở worktree **chỉ khi dựng APK thử**, gỡ bằng `git apply -R`, và `git diff` phải chỉ còn thay đổi của Task. Không commit, không push bản vá. Cuối nhánh `grep "FX6\|fx6_"` trong `app/src` phải rỗng.
- Máy dùng chung: hỏi chủ dự án máy có rảnh trước mọi lần cài hoặc chạy script (bộ nhớ dự án "phone availability").
- Số đo và kết luận ghi rõ bản **debug**, máy Live 4, nhánh@commit.

## Review Focus

1. Handler nằm trong context của scope (không chỉ khai báo): `SupervisorJob() + dispatcher + handler`; log chỉ có tên lớp và một khung stack; `VirtualMachineError` ném lại; không có `Log.e(tag, msg, throwable)` (sẽ in thông điệp).
2. `WeatherRepositoryImpl`: mọi đường `provider.*` (`doRefresh`, `search`, `reverse`) chỉ ra `WeatherException` hoặc `CancellationException`; `CancellationException` được ném lại trước nhánh `Exception`; lỗi lạ cập nhật `lastAttemptAt`/`lastResult` và vẫn `save` + `publish`; `WeatherException` mới không mang `cause`; `init` hoàn tất `loaded` trong `finally`.
3. `WeatherHttp.get(spec)`: `URL(spec)` trong try, `MalformedURLException` → `SERVER` (cấu hình hỏng, không phải mạng), `RuntimeException` khác → `SERVER`, thứ tự catch đúng (`WeatherException` trước); `url.protocol != "https"` vẫn chặn.
4. `OwmParser`: `NaN`/`Infinity` ở nhiệt độ (hiện tại và dự báo) và tọa độ thành `PARSE`; không có `roundToInt()` trực tiếp trên `getDouble` còn sót; `guard` bắt cả `IllegalArgumentException`.
5. `WeatherSheetViewModel` không còn import `data`; `grep "os26launcher\.data\." app/src/main/java/.../ui` rỗng (trừ chú thích KDoc có sẵn ở `IconGridView`); Hilt dựng được `CoarseLocationSource` (build xanh và bảng thời tiết mở được).
6. `DefaultLauncherRepositoryImpl`: `createSetDefaultIntent()` vẫn trả đúng như trước (dùng cho `canRequestDefaultLauncher`, `SettingsViewModel.kt:305`); đường role và đường dự phòng không lặp mã; `Log.w` vẫn không có dữ liệu cá nhân.
7. `SettingsActivity`: mốc thời gian chỉ đặt khi thật sự mở **intent role**; mốc bị xóa sau khi dùng; kết quả `RESULT_OK` và mốc `0` (Activity dựng lại) không làm gì; màn dự phòng mở bằng `startActivity` trong try/catch (`ActivityNotFoundException`, `SecurityException`), thử `ACTION_HOME_SETTINGS` rồi `ACTION_SETTINGS`, hết đường thì Toast chuỗi có sẵn; cả hàng gợi ý ở khối Tìm kiếm đi qua cùng hàm.
8. Không file ngoài vùng; `SettingsViewModel` chỉ thêm hàm; không còn `FX6`/`fx6_` trong `app/src`; không `Log` tạm.

---

### Task 1: B1 — handler cho `ApplicationScope`

**Bắt buộc kiểm tay:** không (điều phối chạy bài kiểm ở "Bài kiểm" B và C)
**Files:** `di/AppModule.kt`.
**Interfaces:** Consumes: không. Produces: `CoroutineScope` `@ApplicationScope` có handler (cùng kiểu, cùng qualifier, không đổi chữ ký).

- [x] **Step 1:** `AppModule`: thêm hàm riêng tư tạo handler và nối vào scope. Hình dạng (chú thích tiếng Việt theo phong cách file, nhắc B1 và quy tắc bí mật):

```kotlin
private const val TAG = "AppScope"

private val handler = CoroutineExceptionHandler { _, e ->
    // Chỉ tên lớp và một khung stack: thông điệp ngoại lệ có thể chứa URL/khóa (G3e).
    Log.e(TAG, "uncaught ${e.javaClass.name} at ${e.stackTrace.firstOrNull()}")
    if (e is VirtualMachineError) throw e // hết bộ nhớ: để process chết như cũ
}
... = CoroutineScope(SupervisorJob() + dispatcher + handler)
```

  Cập nhật chú thích của `provideApplicationScope` (hiện nói "SupervisorJob: một flow lỗi không giết các flow khác"): thêm "handler: lỗi chưa bắt bị ghi log thay vì làm sập process (B1); một flow chia sẻ lỗi sẽ dừng, không sập".
- [x] **Step 2:** Build `assembleDebug` xanh. Commit `fix: log uncaught errors in the application scope instead of crashing`.
- [x] **Step 3: Kịch bản kiểm (Live 4, "Bài kiểm" B1 và C1, cần bản vá `fx6-hooks.patch` mục "gate boom"):**
  (a) **Baseline** (APK `main` + bản vá): tạo cờ `files/fx6_boom`, `force-stop`, mở Home: logcat có `FATAL EXCEPTION` kèm `IllegalStateException`, process chết (pid đổi) — xác nhận B1 còn tái hiện (nâng nhãn từ "đọc code" lên "đo được");
  (b) **Sau sửa** (APK nhánh + bản vá): cùng thao tác: logcat có đúng một dòng `E/AppScope: uncaught java.lang.IllegalStateException at ...`, **không** có `FATAL EXCEPTION`, pid không đổi, Home vẫn vuốt và mở app được; dòng log **không** chứa chuỗi `fx6-selftest` (thông điệp không bị ghi);
  (c) hồi quy: Home khởi động bình thường (không cờ): không có dòng `AppScope` nào trong 60 giây đầu.

---

### Task 2: B1 — thời tiết không sập: map lỗi lạ, dựng URL trong try, làm tròn an toàn

**Bắt buộc kiểm tay:** không (điều phối gộp vào "Bài kiểm" B, C)
**Files:** `data/weather/WeatherHttp.kt`, `OpenWeatherMapProvider.kt`, `OwmParser.kt`, `WeatherRepositoryImpl.kt`; `domain/weather/WeatherRepository.kt` (chỉ KDoc).
**Interfaces:** Consumes: `WeatherException`/`WeatherError` (có sẵn). Produces: hợp đồng `WeatherRepository`: `search`/`reverse`/`select`/`refreshNow`/`refreshIfStale` không bao giờ ném gì ngoài `WeatherException` (cho `search`/`reverse`) hoặc `CancellationException`.

- [x] **Step 1: `WeatherHttp`.** Đổi `fun get(url: URL)` thành `fun get(spec: String)`; trong `try` đầu tiên: `val url = URL(spec)` bắt `MalformedURLException` → `WeatherException(SERVER)` **trước** nhánh `IOException` (vì `MalformedURLException` là `IOException`), giữ kiểm `url.protocol != "https"` → `NETWORK`. Thêm sau `catch IOException`: `catch (e: RuntimeException) { throw WeatherException(SERVER) }` (HTTP stack hãng máy ném lạ; đứng sau `catch WeatherException` để không nuốt lỗi của chính ta; mã chặn nên không có hủy coroutine ở đây). Không đổi `readBody`.
- [x] **Step 2: `OpenWeatherMapProvider`.** `url()` trả `String` (`sb.toString()`), đổi các lời gọi `WeatherHttp.get(url(...))` (đã nhận `String`), bỏ `import java.net.URL`. Giữ `NO_KEY` ném trước khi dựng chuỗi. Không đổi bất kỳ tham số nào của yêu cầu.
- [x] **Step 3: `OwmParser`.** Thêm hàm riêng tư `finite(v: Double): Double` (ném `WeatherException(PARSE)` nếu `!v.isFinite()`). Dùng ở: `place()` (`lat`, `lon` trước `roundCoord`), `snapshot()` (`temp` hiện tại `:49`, `temp` từng mốc dự báo `:56`): `finite(x.getDouble("temp")).roundToInt()`. Sửa `guard` để bắt `JSONException` **và** `IllegalArgumentException` → `PARSE` (phòng thủ cho nơi nào quên, ví dụ `WeatherCondition.fromOwmId`). `roundCoord` giữ nguyên (còn dùng cho `CoarseFix`).
- [x] **Step 4: `WeatherRepositoryImpl`.**
  - Thêm hàm riêng tư `errorOf(e: Exception): WeatherError` (`is WeatherException → e.error`, `is IllegalArgumentException → PARSE`, còn lại `SERVER`) và ghi `Log.w(TAG, "weather call failed: ${e.javaClass.simpleName}")` **chỉ cho nhánh lạ** (cùng kiểu `WeatherStore`; không log thông điệp).
  - `doRefresh`: thêm sau `catch (e: WeatherException)`: `catch (e: CancellationException) { throw e }` đặt **trước** (kotlin yêu cầu thứ tự: `CancellationException` rồi `WeatherException` rồi `Exception`), và `catch (e: Exception) { lastAttemptAt = now; lastResult = errorOf(e).name }` (không có `INVALID_KEY` nên không đụng `keyDead`). Sau khối: `save` + `publish` như cũ.
  - `search`, `reverse`: bọc bằng một hàm `inline` riêng `mapFailures { ... }` (ném lại `CancellationException`, `WeatherException`; còn lại `throw WeatherException(errorOf(e))` **không `cause`**).
  - `init`: `try { ... } finally { loaded.complete(Unit) }` quanh khối đọc `store` + `publish()` (`loaded` luôn hoàn tất để các lệnh sau không treo).
  - `WeatherRepository` KDoc: ghi hợp đồng mới ("`search`/`reverse` chỉ ném `WeatherException`; mọi lệnh khác không ném").
- [x] **Step 5:** Review `WeatherSheetViewModel.kt:80-89` và `:115-119` **không đổi code**: sau Step 4 hai `catch (e: WeatherException)` bao hết (`search`, `reverse`); `choose` → `select` và `refresh` → `refreshNow` không ném. Ghi kết luận này vào Nhật ký (audit yêu cầu kiểm hai chỗ). `location.getOnce()` xử lý ở Task 3.
- [x] **Step 6:** Build xanh. Commit `fix: map unexpected weather errors instead of crashing` (có thể tách hai commit: HTTP/parser, repository).
- [x] **Step 7: Kịch bản kiểm (Live 4, "Bài kiểm" B2–B4 baseline và C2–C5 sau sửa; bản vá `fx6-hooks.patch` gồm gate `badurl` và gate `nan`):**
  (a) **Parser NaN:** tạo cờ `files/fx6_nan`, `force-stop`, mở Home: logcat tag `FX6`: baseline `parser threw IllegalArgumentException`; sau sửa `parser threw WeatherException PARSE`;
  (b) **`MalformedURLException` qua `refreshIfStale` (ApplicationScope):** cờ `files/fx6_badurl` (đổi `baseUrl` thành `foo://x`) + sửa `no_backup/weather.json` để `attempt` cũ (xem "Bài kiểm" B3) + `force-stop` + mở Home có widget thời tiết: baseline sập `FATAL EXCEPTION ... MalformedURLException` (process chết); sau sửa không sập, widget hiện trạng thái lỗi/cũ (`Ready(stale)` nếu đã có số, `Failed(SERVER)` nếu chưa), `weather.json` có `"result":"SERVER"` và `attempt` mới, logcat đúng **một** dòng `weather call failed: MalformedURLException` trong 3 phút (không gọi lại mỗi phút: D2);
  (c) **`MalformedURLException` qua `viewModelScope`:** với `badurl`, mở bảng thời tiết, gõ "Ha Noi", bấm Tìm: baseline sập; sau sửa bảng hiện thông báo lỗi máy chủ (chuỗi có sẵn của `SheetStatus.Failed(SERVER)`), không sập; bấm "Làm mới" (cần ≥ 60 giây từ lần trước): trạng thái lỗi, không sập; bấm chọn một thành phố đã có: không sập;
  (d) **Hồi quy mạng thật:** gỡ cờ `badurl`, `force-stop`, bật lại: bảng thời tiết tìm "Ha Noi" ra kết quả, chọn được, widget hiện số, không dòng `weather call failed`; tắt Wi-Fi và dữ liệu di động bằng `svc` (hỏi chủ dự án trước): Làm mới báo lỗi mạng (`NETWORK`, đã có trước sửa), bật lại: làm mới được;
  (e) log không chứa `appid`, `openweathermap`, tọa độ, tên nơi (`logcat -d | findstr /i "appid openweathermap lat lon"` rỗng cho dòng của app).

---

### Task 3: S1 — interface vị trí thô ở `domain`, bind ở `di`

**Bắt buộc kiểm tay:** không (hồi quy gộp ở "Bài kiểm" D)
**Files:** `domain/weather/CoarseLocationSource.kt` (tạo), `domain/model/weather/CoarseFix.kt` (tạo), `data/weather/CoarseLocationSource.kt` → `CoarseLocationSourceImpl.kt` (đổi tên), `di/WeatherModule.kt`, `ui/weather/WeatherSheetViewModel.kt`.
**Interfaces:** Consumes: không. Produces: `interface CoarseLocationSource { fun hasPermission(): Boolean; suspend fun getOnce(): CoarseFix? }` ở `domain.weather`; `data class CoarseFix(val lat: Double, val lon: Double)` ở `domain.model.weather`.

- [x] **Step 1:** Tạo `domain/model/weather/CoarseFix.kt` (di chuyển nguyên `data class CoarseFix` và KDoc "đã làm tròn 2 số thập phân ngay khi lấy: tọa độ là dữ liệu cá nhân (D3)").
- [x] **Step 2:** Tạo `domain/weather/CoarseLocationSource.kt`: interface với KDoc: lấy vị trí thô **một lần**, chỉ khi người dùng bấm (D6), `null` = không lấy được (không quyền, dịch vụ tắt, hết thời gian), **không ném** (trừ hủy coroutine), không log tọa độ.
- [x] **Step 3:** `git mv` `data/weather/CoarseLocationSource.kt` thành `CoarseLocationSourceImpl.kt`; lớp thành `@Singleton class CoarseLocationSourceImpl @Inject constructor(...) : CoarseLocationSource`, `override` hai hàm; giữ nguyên KDoc và thân (D6). Hai thay đổi nhỏ: trong `getOnce`, `getLastKnownLocation` bắt `Exception` thay vì chỉ `SecurityException` (Review Focus 2: `getOnce` không ném, vì `WeatherSheetViewModel.useCurrentLocation` không có try); `import ...domain.model.weather.CoarseFix`.
- [x] **Step 4:** `WeatherModule`: thêm `@Binds abstract fun bindCoarseLocationSource(impl: CoarseLocationSourceImpl): CoarseLocationSource` cạnh hai `@Binds` có sẵn (scope `@Singleton` nằm trên lớp Impl, như `WeatherRepositoryImpl`). Cập nhật chú thích KDoc đầu file nếu nhắc danh sách binding.
- [x] **Step 5:** `WeatherSheetViewModel`: đổi `import gambi.global.os26launcher.data.weather.CoarseLocationSource` thành `...domain.weather.CoarseLocationSource`. Không đổi gì khác.
- [x] **Step 6:** Build xanh; `grep -rn "os26launcher\.data\." app/src/main/java/gambi/global/os26launcher/ui` không còn dòng import (còn chú thích KDoc ở `IconGridView.kt` là chấp nhận). Commit `refactor: move the coarse location port to domain` (kèm ghi chú đổi tên file trong báo cáo).
- [x] **Step 7: Kịch bản kiểm (Live 4, "Bài kiểm" D):** (a) mở Home, chạm widget thời tiết (hoặc bảng thời tiết từ nơi hiện có): bảng mở, không sập (Hilt dựng được đồ thị mới); (b) bấm "Dùng vị trí hiện tại": nếu quyền vị trí đã cấp thì có kết quả (tên nơi) hoặc thông báo "không lấy được vị trí"; nếu chưa cấp thì hộp thoại quyền hiện, chọn "Từ chối" một lần (lần 1/2 nên chưa vĩnh viễn) và bảng báo "chưa cấp quyền vị trí" như trước; ghi trạng thái quyền trước/sau bằng `dumpsys package ... | findstr COARSE`; (c) không dòng log mới chứa tọa độ.

---

### Task 4: B12 — đo thời gian "hộp thoại role không hiện" so với "người dùng hủy"

**Bắt buộc kiểm tay:** **có**, theo mục 4.2: (1) Task 5 xây trên giả định chỉ máy thật trả lời được (hộp thoại bị từ chối vĩnh viễn quay về nhanh hơn hẳn thời gian một người xem và chạm; sai thì phải đổi thiết kế sang phương án B/D) và (2) số đo dùng để chọn tham số T. Task này **không commit code**: chỉ có bản vá tạm ngoài repo; kết quả là con số T ghi vào Nhật ký. Điều phối chạy và quyết theo quy tắc ở "Quyết định đã chốt" #6 (chủ dự án giao điều phối duyệt và kiểm lại, 2026-11-11).
**Files:** không có (bản vá tạm `fx6-role-measure.patch` ngoài repo: ghi `Log.w("FX6", "role result code=<n> ms=<n>")` trong callback `requestDefault` và, tùy chọn "role giả", đổi hằng `ROLE_HOME` trong `createSetDefaultIntent` thành `"android.app.role.FX6_FAKE"`).
**Interfaces:** Consumes: `SettingsActivity` hiện tại. Produces: số `T` (ms) và bảng số đo cho Task 5.

- [x] **Step 1:** Điều phối dựng bản vá `fx6-role-measure.patch` trên `main` (không phải trên nhánh): (i) `SettingsActivity`: `private var t0 = 0L`; `onDefaultLauncherClicked` đặt `t0 = SystemClock.elapsedRealtime()` ngay trước `requestDefault.launch(intent)`; callback thay `{ }` bằng `{ Log.w("FX6", "role result code=${it.resultCode} ms=${SystemClock.elapsedRealtime() - t0}") }`; (ii) `DefaultLauncherRepositoryImpl.createSetDefaultIntent`: dùng `createRequestRoleIntent(if (File(context.filesDir, "fx6_fakerole").exists()) "android.app.role.FX6_FAKE" else RoleManager.ROLE_HOME)` (cờ tồn tại thì dùng role giả). Dựng `main-role-measure.apk`.
- [x] **Step 2: Chuẩn bị máy (xem "Hạ tầng kiểm"):** hỏi chủ dự án máy rảnh, sao lưu, ghi người giữ role HOME, đặt launcher của hệ thống làm mặc định, cài `main-role-measure.apk`, mở Os26Launcher bằng `am start` (không phải nút Home), vào Cài đặt từ edit mode.
- [x] **Step 3: Đo (ghi từng lần trong Nhật ký):**
  (a) **Role giả** (cờ `files/fx6_fakerole`): bấm hàng "Launcher mặc định" 5 lần, mỗi lần `logcat -d -s FX6`: kỳ vọng `code=0` và `ms` nhỏ (không UI). Ghi 5 số, lấy `max_fake`;
  (b) **Hủy thật, chưa từ chối vĩnh viễn** (gỡ cờ): bấm hàng, khi hộp thoại hiện, bấm "Hủy"/Back bằng `adb shell input keyevent KEYCODE_BACK` gửi **ngay** (cùng lệnh `adb shell "input tap ...; input keyevent 4"` cách nhau ~0,2 giây để lấy cận dưới "Back cực nhanh") và 3 lần bấm tay "Hủy" bình thường: ghi `min_cancel`;
  (c) **Từ chối vĩnh viễn thật** (chủ dự án đã cho phép): bấm hàng, "Hủy" (từ chối lần 1), bấm hàng, tick "Không hỏi lại" nếu có rồi "Hủy" (lần 2); bấm hàng lần 3 và các lần sau: kỳ vọng hộp thoại không hiện, `code=0`, `ms` nhỏ. Ghi 5 số `ms`, lấy `max_denied`. Ghi cả quan sát nếu hộp thoại **khác** kỳ vọng (ví dụ không có ô "Không hỏi lại" trên Android 11): đó là dữ liệu quan trọng cho B12;
  (d) ghi `T = max(300, 2 x max(max_fake, max_denied))`; điều kiện chấp nhận: `T < min_cancel` (kể cả cận dưới Back nhanh, nếu không thỏa thì dùng cận dưới của lần Hủy bằng tay và ghi rõ). Nếu (c) cho thấy hộp thoại vẫn hiện, hoặc `ms` của (c) lớn hơn lần hủy thật: **dừng**, báo điều phối, đổi sang phương án B hoặc D (chưa có dòng code nào bị bỏ).
- [x] **Step 4:** Khôi phục: gỡ từ chối (xem "Hạ tầng kiểm" mục 4), xóa cờ, `git apply -R` bản vá, cài lại bản debug thường, đặt Os26Launcher làm mặc định, `force-stop`, so md5 sao lưu. Ghi T và bảng số vào Nhật ký trước khi sang Task 5.

---

### Task 5: B12 — màn hệ thống dự phòng khi hộp thoại role không hiện

**Bắt buộc kiểm tay:** không (đã đo ở Task 4; kiểm trong "Bài kiểm" E)
**Files:** `domain/repository/DefaultLauncherRepository.kt`, `data/system/DefaultLauncherRepositoryImpl.kt`, `ui/settings/SettingsViewModel.kt` (chỉ thêm hàm), `ui/settings/SettingsActivity.kt`.
**Interfaces:** Consumes: số `T` của Task 4. Produces: `DefaultLauncherRepository.createRoleRequestIntent(): Intent?` (chỉ đường role, `null` nếu ROM không có), `createFallbackIntents(): List<Intent>` (`ACTION_HOME_SETTINGS` rồi `ACTION_SETTINGS`, chỉ những intent resolve được); `createSetDefaultIntent()` không đổi hành vi (= role ?: intent dự phòng đầu tiên) để `readDefaultInfo()` vẫn đúng; ba hàm ở `SettingsViewModel`: `createRoleRequestIntent()`, `createDefaultLauncherFallbackIntents()`, `isDefaultLauncher()`.

- [x] **Step 1: Repository.** Tách thân `createSetDefaultIntent()` thành `createRoleRequestIntent()` (khối RoleManager hiện có, giữ hai `catch` và `Log.w`) và `createFallbackIntents()` (`ACTION_HOME_SETTINGS`, `ACTION_SETTINGS`, lọc `isResolvable`). `createSetDefaultIntent() = createRoleRequestIntent() ?: createFallbackIntents().firstOrNull()` (đúng thứ tự cũ). Cập nhật KDoc interface.
- [x] **Step 2: ViewModel.** Cạnh `createSetDefaultIntent()`: ba hàm một dòng ủy quyền cho repository (`isDefaultLauncher()` dùng `defaultLauncherRepository.isDefaultLauncher()` đồng bộ, như `readDefaultInfo()`). Không đổi gì khác trong file.
- [x] **Step 3: Activity.** Hằng `ROLE_DIALOG_NEVER_SHOWN_MS` (`CHỈNH TAY`, giá trị = T của Task 4, chú thích kèm số đo và ngày) ở `companion object`. Sửa:

```kotlin
private var roleRequestAt = 0L // elapsedRealtime lúc mở hộp thoại role; 0 = không biết (B12)

private val requestDefault = registerForActivityResult(StartActivityForResult()) { result ->
    val startedAt = roleRequestAt
    roleRequestAt = 0L
    if (startedAt == 0L || result.resultCode == RESULT_OK) return@registerForActivityResult
    val tooFast = SystemClock.elapsedRealtime() - startedAt < ROLE_DIALOG_NEVER_SHOWN_MS
    if (tooFast && !viewModel.isDefaultLauncher()) openDefaultLauncherFallback()
}
```

  `onDefaultLauncherClicked`: `val role = viewModel.createRoleRequestIntent()`; nếu `role != null` thì đặt `roleRequestAt = SystemClock.elapsedRealtime()` và `launch(role)` trong try/catch hiện có (nếu `launch` ném thì xóa mốc và rơi xuống `openDefaultLauncherFallback()`); nếu `role == null` thì thẳng `openDefaultLauncherFallback()` (Quyết định #7: không đặt mốc). `openDefaultLauncherFallback()`: duyệt `viewModel.createDefaultLauncherFallbackIntents()`, `startActivity` từng intent trong `try/catch (e: RuntimeException)`, thành công thì dừng; hết danh sách mà chưa mở được (hoặc danh sách rỗng) thì `Toast` `R.string.home_set_default_failed` (đúng như nhánh `intent == null` hiện tại). Bỏ biến cục bộ `intent` cũ nếu không còn dùng.
- [x] **Step 4:** Build xanh; `grep "FX6\|fx6_"` rỗng. Commit `fix: open the system home settings when the role dialog is not shown`.
- [x] **Step 5: Kịch bản kiểm (Live 4, "Bài kiểm" E; cần `fx6-role-fake.patch` chỉ gồm phần role giả của Task 4, không log):**
  (a) **Role giả** (cờ `files/fx6_fakerole`, launcher hệ thống đang mặc định): bấm hàng "Launcher mặc định": màn "Ứng dụng màn hình chính" (`ACTION_HOME_SETTINGS`) mở ra (chụp ảnh); Back về Cài đặt Os26: hàng vẫn "Chưa đặt", **không** mở lại màn hệ thống (không vòng lặp);
  (b) cùng bước, hàng gợi ý trong khối Tìm kiếm (nếu hiện được; cần bật mục shortcut trong Cài đặt tìm kiếm, sẽ hoàn lại bằng sao lưu): cũng mở màn hệ thống;
  (c) **Hủy thật bình thường** (gỡ cờ role giả): bấm hàng, hộp thoại hiện, đợi ≥ 2 giây, bấm "Hủy": ở lại Cài đặt Os26, **không** nhảy sang màn hệ thống;
  (d) **Đặt mặc định thật** (hộp thoại hiện, bấm "Đặt làm mặc định"): trở về Cài đặt, hàng đổi thành "Đang là mặc định" (onResume), không mở màn hệ thống; ở trạng thái đang là mặc định bấm hàng: hộp thoại không cần hiện hoặc trả `RESULT_OK` tức thì, không nhảy màn hệ thống (kiểm `isDefaultLauncher`);
  (e) **Từ chối thật "Không hỏi lại"** (Task 4 Step 3c, nếu còn hoặc làm lại): bấm hàng: màn `ACTION_HOME_SETTINGS` mở ngay ở lần bấm thứ nhất, chọn Os26Launcher ở đó rồi Back: hàng thành "Đang là mặc định";
  (f) **Activity dựng lại giữa chừng** (đổi sáng/tối khi hộp thoại đang mở, nếu làm được): không nhảy màn hệ thống (mốc bị mất → mặc định an toàn).

---

### Task 6: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không
**Files:** `docs/knowledgements/FX6-robustness-settings.md` (tạo), plan này (cập nhật).

- [x] **Step 1:** Viết knowledgement theo mẫu mục 8 của `MultiAgent-Workflow.md`: Mermaid `sequenceDiagram` cho luồng "widget → `refreshIfStale` → `doRefresh` → ngoại lệ lạ → map `WeatherError` → `publish`" và cho luồng "bảng thời tiết → `search` → `mapFailures`"; `flowchart` cho "bấm hàng Launcher mặc định → role hay dự phòng → đo thời gian → màn dự phòng"; bảng số đo Task 4 (ghi rõ **debug**, Live 4, hộp thoại Android 11) và kết quả baseline so với sau sửa của B1; mục "Bài học cho AGENTS.md" (xem "Đề xuất cập nhật tài liệu chung").
- [x] **Step 2:** Điền "Bằng chứng Review Focus" và "Kết quả kiểm chứng"; điều phối rebase, build xanh, rà `git diff main -- app` chỉ trong vùng file, `grep "FX6\|fx6_"` rỗng, không còn `Log` tạm.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11), Os26Launcher là launcher mặc định lúc bắt đầu. **Bản:** debug; ghi `git rev-parse --short HEAD` của từng APK. Chủ dự án đã cho điều phối kiểm lại trên Live 4 (2026-11-11); ngay trước mỗi lượt điều phối kiểm máy đang rảnh và sao lưu lại. Chạy theo thứ tự dưới đây (mọi ca "sập" nằm lúc Os26Launcher **không** là launcher mặc định, để không có vòng sập trên màn hình chính).

### Hạ tầng kiểm (điều phối chuẩn bị một lần, ngoài repo)

1. **Ghi trạng thái ban đầu:** `adb shell cmd role get-role-holders --user 0 android.app.role.HOME` (kỳ vọng `gambi.global.os26launcher`; nếu máy báo lệnh `cmd role` không có, dùng cách giao diện: Cài đặt > Ứng dụng > Ứng dụng mặc định > Ứng dụng màn hình chính). Tìm package launcher hệ thống: `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME` (hoặc `pm resolve-activity` nếu lệnh trước không chạy); ghi tên (ví dụ launcher gốc của Vsmart). Ghi `adb shell dumpsys package gambi.global.os26launcher | findstr /i "COARSE"` (trạng thái quyền vị trí) và chủ đề sáng/tối (`cmd uimode night`).
2. **Sao lưu dữ liệu app** (theo bài học DG, lấy cả `files/`, so md5 từng file):
   - `cmd /c "adb exec-out run-as gambi.global.os26launcher tar cf - databases files no_backup > %TEMP%\fx6-backup.tar"`
   - `adb shell "run-as gambi.global.os26launcher sh -c 'find databases files no_backup -type f | sort | xargs md5sum'" > %TEMP%\fx6-md5-before.txt` (kỳ vọng 8 file; ghi số file).
   - `no_backup/weather.json` chứa tên nơi và tọa độ làm tròn của chủ dự án: chỉ nằm trong bản sao lưu cục bộ, không dán nội dung vào báo cáo.
3. **Ba bản vá tạm** (ngoài repo, `git apply` vào worktree chỉ để dựng APK thử, `git apply -R` ngay sau đó):
   - `fx6-hooks.patch` (ba cổng theo cờ file trong `files/`, mỗi cổng gọi bằng `adb shell run-as gambi.global.os26launcher touch files/<cờ>`): `fx6_boom` (một lần: trong `LauncherApp.onCreate`, `if (File(filesDir, "fx6_boom").delete()) appScope.launch { throw IllegalStateException("fx6-selftest") }`); `fx6_nan` (một lần: `if (File(filesDir, "fx6_nan").delete()) Thread { ... }` gọi `OwmParser.snapshot(GeoPlace("t", 0.0, 0.0), """{"weather":[{"id":800,"icon":"01d"}],"main":{"temp":"NaN"}}""", """{"list":[]}""", 0L)` rồi `Log.w("FX6", "parser threw " + e.javaClass.simpleName + " " + (e as? WeatherException)?.error)`; nếu không ném thì `Log.w("FX6", "parser no throw")`); `fx6_badurl` (cờ bền, cần xóa tay: `WeatherModule.provideWeatherProviderConfig` nhận `@ApplicationContext` và dùng `baseUrl = if (File(ctx.filesDir, "fx6_badurl").exists()) "foo://x" else "https://api.openweathermap.org"`; giữ `apiKey` nguyên, không in ra).
   - `fx6-role-measure.patch` (Task 4) và `fx6-role-fake.patch` (chỉ phần role giả, không log).
4. **Khôi phục từ chối role** (dùng ở Task 4 và bài kiểm E, theo thứ tự thử tới khi dùng được; ghi cách nào đã dùng):
   - (i) kiểm nơi lưu từ chối trước khi đụng gì: `adb shell dumpsys role` (xem phần holders và mọi dòng "denied"), `adb shell dumpsys package com.google.android.permissioncontroller | findstr /i version` (xác định tên package PermissionController: có thể `com.google.android.permissioncontroller` hoặc `com.android.permissioncontroller`);
   - (ii) **xóa dữ liệu của PermissionController**: `adb shell pm clear <package PermissionController>`. Lý do chọn: cờ "không hỏi lại" của hộp thoại role do PermissionController tự lưu (suy luận từ AOSP, chưa kiểm trên Live 4: **đo hiệu quả bằng cách bấm hàng và thấy hộp thoại role hiện lại**). Ảnh hưởng phụ đã biết: mất trạng thái nội bộ của PermissionController (thống kê quyền, thông báo "ứng dụng không dùng"), **không** thu hồi quyền runtime đã cấp (do system_server giữ). Nếu `pm clear` bị chặn (SecurityException như Oppo, AGENTS.md), dùng giao diện: Cài đặt > Ứng dụng > Hiện hệ thống > Bộ điều khiển quyền > Bộ nhớ > Xóa dữ liệu (cùng hiệu quả);
   - (iii) nếu (ii) không làm hộp thoại hiện lại: **dừng**, báo chủ dự án; không thử cách khác nguy hiểm hơn (không root, không sửa `roles.xml`).
5. **Đặt lại launcher mặc định:** `adb shell cmd role add-role-holder --user 0 android.app.role.HOME gambi.global.os26launcher`, kiểm `get-role-holders`. Nếu lệnh không có: Cài đặt > Ứng dụng mặc định > Ứng dụng màn hình chính > chọn Os26Launcher; hoặc dùng hàng "Launcher mặc định" trong app (đồng thời là một ca kiểm: hộp thoại hiện, bấm "Đặt làm mặc định", app hiện "Đang là mặc định").
6. **Dựng 4 APK:** `main-hooks.apk` (HEAD `main` + `fx6-hooks.patch`), `fx6-hooks.apk` (HEAD nhánh + `fx6-hooks.patch`), `main-role-measure.apk` (Task 4), `fx6-role-fake.apk` (HEAD nhánh + `fx6-role-fake.patch`), và `fx6-final.apk` (HEAD nhánh sạch, không vá). Không dựng khi `git status` còn thay đổi ngoài ý.
7. **Khóa API:** build debug lấy khóa từ `local.properties` của worktree (cùng cách các nhánh khác); kiểm `weather.json` có tên nơi và widget thời tiết có trên Home trước khi bắt đầu (nếu chủ dự án chưa có widget thời tiết: thêm một widget và chọn một thành phố; mọi thay đổi bố cục này được hoàn lại ở bước khôi phục).

### A. Chuẩn bị

1. Làm hạ tầng 1–2; hỏi chủ dự án máy rảnh.
2. Đặt launcher hệ thống làm mặc định (hạ tầng 4/5 theo chiều ngược: `adb shell cmd role add-role-holder --user 0 android.app.role.HOME <package launcher hệ thống>`, kiểm `get-role-holders`).
3. Cài `main-hooks.apk` (`adb install -r`), mở Os26Launcher bằng `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity` (kiểm tên activity bằng `cmd package resolve-activity` nếu khác), xác nhận Home và widget thời tiết hiện.

### B. B1 baseline (`main-hooks.apk`: xác nhận lỗi còn tái hiện)

1. **B1 handler:** `adb shell run-as gambi.global.os26launcher touch files/fx6_boom`, `adb shell am force-stop gambi.global.os26launcher`, `adb logcat -c`, mở Home. Kỳ vọng: `FATAL EXCEPTION ... IllegalStateException` trong `logcat -d`, pid đổi.
2. **Parser NaN:** cờ `fx6_nan`, `force-stop`, mở Home. Kỳ vọng: `FX6 parser threw IllegalArgumentException null`.
3. **Thời tiết `foo://x` qua ApplicationScope:** cờ `fx6_badurl`; `force-stop`; sửa `weather.json` để ép làm mới: `adb shell "run-as gambi.global.os26launcher sh -c 'sed -i \"s/\\\"attempt\\\":[0-9]*/\\\"attempt\\\":1/\" no_backup/weather.json'"` (kiểm `cat` rằng khóa `attempt` đã đổi, nếu tên khóa khác thì đọc `WeatherStore.kt` để dùng đúng khóa); mở Home có widget thời tiết, chờ tối đa 90 giây (nhịp phút của widget). Kỳ vọng: `FATAL EXCEPTION ... MalformedURLException`.
4. **Thời tiết `foo://x` qua `viewModelScope`:** (cờ `badurl` còn) mở bảng thời tiết, gõ "Ha Noi", Tìm. Kỳ vọng: sập (nếu bước 3 đã sập process thì mở lại Home trước).
5. Ghi mọi kết quả (pid, dòng `FATAL`) vào Nhật ký, nâng nhãn B1 lên "đo được" trong plan. Nếu một ca **không** sập, ghi lại: kết luận đọc code cần sửa.

### C. B1 sau sửa (`fx6-hooks.apk`, cùng các bước)

1. **C1 handler:** `adb shell run-as ... touch files/fx6_boom`, `force-stop`, `logcat -c`, mở Home. Kỳ vọng: một dòng `E/AppScope: uncaught java.lang.IllegalStateException at ...LauncherApp...`, không `FATAL`, pid không đổi sau đó, Home vuốt được; dòng log **không có** `fx6-selftest`.
2. **C2 parser NaN:** `FX6 parser threw WeatherException PARSE`.
3. **C3 `foo://x` qua ApplicationScope:** như B3. Kỳ vọng: không sập; widget hiện trạng thái lỗi/cũ; `weather.json`: `"result":"SERVER"` và `attempt` mới; `logcat -d -s Weather*:W` hoặc `findstr "weather call failed"` đúng **một** dòng `MalformedURLException` trong 3 phút.
4. **C4 qua bảng thời tiết:** như B4. Kỳ vọng: thông báo lỗi máy chủ trong bảng, không sập; "Làm mới" (sau ≥ 60 giây) cũng không sập.
5. **C5 hồi quy mạng thật:** gỡ cờ `fx6_badurl` (`run-as ... rm files/fx6_badurl`), `force-stop`, mở Home, bảng thời tiết: tìm "Ha Noi" ra kết quả, chọn, widget hiện số. Hỏi chủ dự án rồi tắt mạng (`svc wifi disable; svc data disable`), Làm mới: báo lỗi mạng; bật lại (`svc wifi enable; svc data enable`), Làm mới sau ≥ 60 giây thành công. Kiểm log: không có `appid`, `openweathermap`, tọa độ.

### D. S1 hồi quy (`fx6-final.apk`)

Cài `fx6-final.apk`, mở bảng thời tiết, bấm "Dùng vị trí hiện tại" (theo Task 3 Step 7), kiểm không sập và bảng đóng/mở lại bình thường. `grep` Review Focus 5 (làm trong worktree).

### E. B12

1. **Role giả (không đụng trạng thái từ chối thật):** cài `fx6-role-fake.apk`, tạo cờ `fx6_fakerole`, mở Cài đặt Os26 (từ edit mode), bấm hàng "Launcher mặc định": màn "Ứng dụng màn hình chính" mở; Back: ở lại Cài đặt Os26, hàng "Chưa đặt", không vòng lặp. Bấm lại 3 lần liên tiếp để chắc.
2. **Hủy thật bình thường:** gỡ cờ (`rm files/fx6_fakerole`), bấm hàng: hộp thoại hiện; đợi ≥ 2 giây, "Hủy": ở lại Cài đặt, không nhảy.
3. **Từ chối thật "Không hỏi lại"** (đã cho phép): bấm hàng, "Hủy"; bấm lại, tick "Không hỏi lại" (nếu có), "Hủy"; bấm lần 3: **màn hệ thống mở ngay ở lần bấm này**. Ghi ảnh; ghi `ms` nếu dùng bản đo.
4. **Hoàn trả từ chối:** hạ tầng 4(ii). Xác nhận: bấm hàng thì hộp thoại role hiện lại (chứng tỏ từ chối đã gỡ).
5. **Đặt mặc định thật:** trong hộp thoại, "Đặt làm mặc định": Cài đặt Os26 hiện "Đang là mặc định", không nhảy màn hệ thống. (Đây cũng là bước khôi phục launcher mặc định; kiểm `get-role-holders`.)
6. Ghi quan sát khác thường (ví dụ ROM không có ô "Không hỏi lại"): thay đổi đánh giá B12 trong knowledgement.

### F. Hồi quy và trả máy

1. Hồi quy: Home vuốt trang, mở/đóng bảng thời tiết, thêm/bỏ widget thời tiết, Cài đặt mở được và các hàng khác (kính, lưới, kiểu icon) bấm được; không dòng `AppScope` trong 60 giây đầu khi dùng bình thường.
2. **Trả máy (thứ tự):** (a) `force-stop`; (b) xóa mọi cờ còn lại (`run-as ... ls files` không còn `fx6_*`); (c) cài lại bản debug của chủ dự án (HEAD `main`, `adb install -r`), `git apply -R` mọi bản vá và `git status` sạch; (d) khôi phục dữ liệu: `adb push %TEMP%\fx6-backup.tar /data/local/tmp/` rồi `adb shell "run-as gambi.global.os26launcher sh -c 'tar xf /data/local/tmp/fx6-backup.tar'"`; (e) đặt Os26Launcher làm mặc định (hạ tầng 5), kiểm `get-role-holders`; (f) chủ đề sáng/tối như hạ tầng 1; Wi-Fi và dữ liệu di động bật; (g) so md5: chạy lại lệnh `find ... md5sum` thành `fx6-md5-after.txt` và `fc` hai file: **8 file khớp**; lệch thì khôi phục lại từ tar và so lại; (h) xóa `/data/local/tmp/fx6-backup.tar` trên máy (file của điều phối). Ghi kết quả vào "Kết quả kiểm chứng".

**Kết quả kiểm chứng (Live 4, Android 11, bản debug, 2026-11-11, `fix/fx6-robustness-settings`):** B1 handler và parser NaN đạt (14:06–14:14, `main-hooks` so với `FX6-hooks`, xem Nhật ký). B12 đạt với role giả, Hủy thật và Đặt làm mặc định (`FX6-role-fake.apk` @0868f90, ~14:20, xem Nhật ký). **Không kiểm trên máy:** `fx6_badurl` (đường làm mới/tìm kiếm thời tiết) và hồi quy S1 (chủ dự án không có widget thời tiết nên không có `weather.json`): chỉ đọc code, chủ dự án kiểm khi duyệt PR. B12 với từ chối vĩnh viễn thật không tái hiện được trên Live 4: nhãn suy luận.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | Plan | Viết plan, đối chiếu code `b1af810` (AppModule, `data/weather/*`, `ui/weather/*`, `data/system/*`, `SettingsActivity`) | — | Chờ điều phối rà. Phát hiện thêm so với audit: `viewModelScope` của bảng thời tiết cũng sập (cần hợp đồng repository, không chỉ handler); `init` có thể treo `loaded`; lỗi lạ phải cập nhật `lastAttemptAt` (D2) |
| 2026-11-11 | Task 1 | Handler `uncaughtHandler` trong `AppModule` (log tên lớp + một khung, ném lại `VirtualMachineError`) | b38dda4 | Build xanh. Chưa kiểm máy (điều phối) |
| 2026-11-11 | Task 2 | `WeatherHttp.get(spec)` dựng URL trong try (`MalformedURLException` và `RuntimeException` → `SERVER`); `OwmParser.finite` + `guard` bắt `IllegalArgumentException`; `doRefresh` bắt `Exception` và tính một lần thử; `search`/`reverse` qua `mapFailures`; `init` finally; KDoc hợp đồng | b4871c8, cdc76b2 | Build xanh. Review Focus audit: `WeatherSheetViewModel` `:80-89`, `:115-119` không đổi code (hai `catch WeatherException` nay đúng nhờ hợp đồng repository). Chưa kiểm máy |
| 2026-11-11 | Task 3 | Interface `CoarseLocationSource` + `CoarseFix` ở `domain`, `CoarseLocationSourceImpl` (`git mv`), `@Binds` ở `WeatherModule`, VM đổi import; `getLastKnownLocation` bắt `Exception` | 2b4330a, c30a729 | Build xanh ở HEAD. **2b4330a chỉ là bước đổi tên (lệnh `git add` cả lô thất bại); c30a729 hoàn tất, nên 2b4330a đứng riêng không build được**; không sửa lịch sử vì đã push. Không còn import `data` trong `ui`. Đổi tên `CoarseLocationSource.kt` là đổi tên, không xóa |
| 2026-11-11 | Task 4 | Dựng APK đo và bản vá ngoài repo tại `E:\SourceTree\IosLauncher-wt\apk\`: `FX6-hooks`, `main-hooks`, `main-role-measure`, `FX6-role-fake` (`.apk` và `.patch`) | — | Dừng chờ điều phối chạy B1 baseline/sau sửa và đo B12. `FX6-role-fake.apk` dựng trên HEAD Task 3 (chưa có sửa B12): dựng lại sau Task 5 |
| 2026-11-11 | Task 1–3 (kiểm máy) | Điều phối kiểm trên Live 4 (14:06–14:14, Os26 vẫn là launcher mặc định vì chỉ dùng cổng một lần). `main-hooks`: `fx6_boom` → 1 `FATAL EXCEPTION … IllegalStateException: fx6-selftest`, process chết. `FX6-hooks`: không `FATAL`, một dòng `AppScope: uncaught java.lang.IllegalStateException at gambi.global.os26launcher.LauncherApp$onCreate$1.invokeSuspend(LauncherApp.kt:29)`, thông điệp không được ghi. `fx6_nan`: main `parser threw IllegalArgumentException null`; FX6 `parser threw WeatherException PARSE` | — | Review Task 1–3 đạt. **`fx6_badurl` chưa chạy**: chủ dự án không có widget thời tiết trên Home và không có `weather.json` nên không đường làm mới nào chạy; chỉ đọc code, chủ dự án kiểm khi duyệt PR nếu dùng widget thời tiết. `2b4330a` + `c30a729` chấp nhận (không force-push) |
| 2026-11-11 | Task 4 (đo) | `main-role-measure.apk`, launcher hệ thống mặc định. Role giả: `code=0`, `ms` = 176, 59, 54, 49, 54 → `max_fake` = 176. Hộp thoại thật, Hủy bằng script (bấm hàng, 0,6 s, bấm Hủy): 1314, 1309 ms; Hủy tay bình thường 8–54 s → `min_cancel` ≈ 1309. **Từ chối thật không tái hiện được trên máy này**: lần xin thứ hai hiện "Không hỏi lại"; tick (xác nhận `checked=true`) rồi Hủy (`code=0 ms=54097`), lần bấm kế hộp thoại role hiện lại đầy đủ (ô tick bỏ chọn): PermissionController Google trên ROM Android 11 này vẫn hiện hộp thoại HOME. Nên không đo được `max_denied`; dữ liệu PermissionController không bị xóa (không có gì để khôi phục) | — | Quyết định điều phối: `T = max(300, 2 × 176) = 352 ms`, thấp hơn nhiều so với 1309. Giữ phương án A (bảo vệ ROM/phiên bản có tôn trọng "Không hỏi lại" và ca `RESULT_CANCELED` tức thì); nhãn B12 trên máy này vẫn **suy luận** |
| 2026-11-11 | Task 5 | `DefaultLauncherRepository`: `createRoleRequestIntent()`, `createFallbackIntents()` (`createSetDefaultIntent()` = role ?: dự phòng đầu); `SettingsViewModel`: thêm ba hàm; `SettingsActivity`: mốc `roleRequestAt`, `ROLE_DIALOG_NEVER_SHOWN_MS = 352`, `openDefaultLauncherFallback()` | 0868f90 | Build xanh. `grep FX6\|fx6_` trong `app/src` rỗng. Dựng `FX6.apk` (sạch) và `FX6-role-fake.apk` (+ cổng role giả) ở `E:\SourceTree\IosLauncher-wt\apk\`. Step 5 (kiểm máy) chờ điều phối |
| 2026-11-11 | Task 6 | Viết `docs/knowledgements/FX6-robustness-settings.md` (Mermaid, bảng file, giới hạn B12, bài học) | — | Step 2 (điền bằng chứng Review Focus, rebase, rà diff tổng) chờ điều phối đóng |
| 2026-11-11 | Task 5 (kiểm máy) + Task 6 | Live 4 ~14:20, `FX6-role-fake.apk` @0868f90, Vsmart là mặc định. Role giả: bấm hàng mở ngay `com.android.permissioncontroller.role.ui.DefaultAppActivity` (hai lần), Back về Cài đặt Os26, không vòng lặp. Hộp thoại thật, đợi > 2 s, Hủy: ở lại `SettingsActivity`. Chọn OS26 rồi "Đặt làm mặc định": role holder HOME = `gambi.global.os26launcher`, không nhảy. Điền bằng chứng Review Focus, kết quả kiểm chứng, đề xuất tài liệu chung (kèm C15) | — | Review Task 5 đạt. Hồi quy S1 chưa chạy (không có widget thời tiết): đọc code |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Handler trong context scope, log chỉ tên lớp + một khung, ném lại `VirtualMachineError` | `AppModule.kt` (`+ uncaughtHandler`); máy: `AppScope: uncaught java.lang.IllegalStateException at ...LauncherApp.kt:29`, không có `fx6-selftest`, không `FATAL`. Ném lại `VirtualMachineError` chỉ đọc code | Đạt (đo được); nhánh OOM đọc code |
| 2 | Repository chỉ ném `WeatherException`/hủy; lỗi lạ cập nhật `lastAttemptAt`; không `cause`; `init` finally | Đọc diff `WeatherRepositoryImpl` (`mapFailures`, `errorOf`, thứ tự catch, `finally`); `WeatherSheetViewModel` `:80-89`, `:115-119` không đổi | Đạt (đọc code, chưa chạy: không có `weather.json` trên máy thử) |
| 3 | `WeatherHttp.get(spec)`: URL trong try, thứ tự catch | Đọc diff: `URL(spec)` trong try, `MalformedURLException` → `SERVER`, `WeatherException` trước `IOException` trước `RuntimeException` | Đạt (đọc code; `fx6_badurl` chưa chạy) |
| 4 | `OwmParser` NaN/Infinity thành `PARSE` | `fx6_nan`: main `IllegalArgumentException`, nhánh `WeatherException PARSE` | Đạt (đo được) |
| 5 | `ui` không còn import `data`; Hilt dựng được | `grep "os26launcher\.data\." ui` chỉ còn chú thích KDoc; build xanh, app khởi động (Hilt dựng đồ thị); bảng thời tiết chưa mở trên máy | Đạt (build + đọc code); mở bảng chưa kiểm |
| 6 | `createSetDefaultIntent()` giữ hành vi; không lặp mã | `createSetDefaultIntent() = createRoleRequestIntent() ?: createFallbackIntents().firstOrNull()`; hàng vẫn hiện đúng "Chưa đặt"/"Đang là mặc định" trên máy | Đạt |
| 7 | `SettingsActivity`: mốc chỉ cho intent role, dự phòng bằng `startActivity` trong try/catch, hàng gợi ý cùng đường | Máy: role giả → `DefaultAppActivity` mở ngay (2 lần), Back không vòng lặp; Hủy thật sau > 2 s ở lại `SettingsActivity`; Đặt làm mặc định → role holder là Os26, không nhảy. Hàng gợi ý: cùng hàm `onDefaultLauncherClicked` (đọc code) | Đạt |
| 8 | Không file ngoài vùng; không `FX6`/`fx6_`; `SettingsViewModel` chỉ thêm hàm | `grep "FX6\|fx6_" app/src` rỗng; diff tổng chỉ trong vùng file của plan | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: mục Cài đặt, hàng "Launcher mặc định": ghi "nếu hệ thống không hiện hộp thoại xin vai trò Home (đã chọn Không hỏi lại) thì mở màn Ứng dụng màn hình chính của hệ thống".
- `project_structure.md`: thêm `domain/weather/CoarseLocationSource.kt`, `domain/model/weather/CoarseFix.kt`, `data/weather/CoarseLocationSourceImpl.kt`; dòng `AppModule.kt` ghi "scope ứng dụng có `CoroutineExceptionHandler`"; mục 2 ghi nhận `ui → data` không còn ngoại lệ nào.
- `room-schema.md`: không.
- `AGENTS.md` / `lessons/` (điều phối chép từ knowledgement vào `settings-and-data.md`, `permissions-and-device-testing.md`, `widgets.md`):
  - `SupervisorJob` chỉ cô lập coroutine con, không chặn lỗi tới handler của luồng: scope dùng chung cần `CoroutineExceptionHandler`; log chỉ tên lớp (đọc tài liệu + đo, nếu kiểm ở Live 4).
  - Hợp đồng repository phải nói rõ ngoại lệ nào thoát ra; `viewModelScope` không có handler nên mọi `catch` ở ViewModel chỉ đúng khi repository bảo đảm.
  - Lỗi lạ cũng phải tính là một lần thử (cập nhật `lastAttemptAt`) nếu có hợp đồng chặn tần suất.
  - Hộp thoại xin vai trò: `RESULT_CANCELED` không phân biệt "người dùng hủy" và "hệ thống không hiện hộp thoại" (từ chối vĩnh viễn); phân biệt bằng thời gian trôi qua (Live 4/Android 11, debug: không hiện 49–176 ms, Hủy thật nhanh nhất ~1309 ms, ngưỡng 352 ms; đo được). Trên Live 4 ca "Không hỏi lại" không tái hiện được (hộp thoại role vẫn hiện), nên cách sửa cho ca đó là suy luận.
  - Cách thử an toàn: role không tồn tại cho `RESULT_CANCELED` tức thì, không đụng trạng thái từ chối thật (đo được trên Live 4: `code=0` sau 49–176 ms, không có UI). Không cần khôi phục từ chối vĩnh viễn nên không phải xóa dữ liệu PermissionController.
  - Bản vá thử có cổng theo cờ file trong `files/` giúp thử đường lỗi mà không có vòng sập trên launcher mặc định.
  - `cmd role add-role-holder/get-role-holders` để đặt/kiểm launcher mặc định qua adb (điều phối đã dùng để kiểm role holder trên Live 4).
  - **`AGENTS.md` mục "Trước khi phát hành":** thêm C15 (hoãn theo quyết định chủ dự án 2026-11-11): khóa OpenWeatherMap nằm nguyên văn trong APK (`app/build.gradle.kts:11-16`, `:33`, `di/WeatherModule.kt:33`). Trước khi phát hành: đưa gọi API qua một proxy nhỏ giữ khóa (chỉ đổi `baseUrl`, `WeatherModule` đã ghi sẵn), tối thiểu giới hạn khóa trên trang OWM, đặt cảnh báo hạn mức, xoay khóa định kỳ. FX6 không đổi cách xử lý khóa.
