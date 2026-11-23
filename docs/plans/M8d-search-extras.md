# M8d — Phần còn lại của tìm kiếm: Cài đặt hệ thống, shortcut app, gợi ý và app gần đây (TT#9) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần số máy mạnh/344 app). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/m8d-search-extras` · **Worktree:** `E:\SourceTree\IosLauncher-wt\M8d-search-extras` · **PR:** (draft, điền số khi mở) · **Bắt đầu:** 2026-11-05 · **Plan duyệt:** 2026-11-05 (chủ dự án; Task 4 shortcut đã xác nhận lại sau một lần tạm hoãn)
**Trạng thái:** Code Task 2-6, sửa theo review và tài liệu Task 7 xong, build xanh; còn kiểm tay phần chưa chạy trên Live 4 (chưa tick Task nào)
**Khóa:** không (không đổi schema Room; không đụng file nóng độc quyền — xem "Vùng file")

**Goal:** Ô tìm kiếm tìm được thêm (1) mục Cài đặt hệ thống, (2) shortcut của app (khi launcher là mặc định), và khi ô trống thì hiện (3) app mở gần đây + app dùng nhiều, thay cho dòng gợi ý "Nhập tên ứng dụng".

**Architecture:** Mở rộng đường M8b thay vì dựng đường mới. `SearchUiState.results` đổi từ `List<AppItem>` sang `List<SearchResult>` (sealed: `App`, `Setting`, `Shortcut`, `Header`). Mỗi nguồn là một repository nhỏ trong `data/search/` (`SystemSettingsRepository`, `ShortcutSearchRepository`, `RecentAppsRepository`, `SearchPreferencesRepository`). `AppSearchMatcher` giữ nguyên luật xếp hạng D2 của M8b nhưng chạy trên **chỉ mục chuẩn hóa dựng sẵn** (`SearchIndex`), không chuẩn hóa lại nhãn ở mỗi phím gõ. Lịch sử mở app ghi một chỗ duy nhất, trong `AppRepositoryImpl.launch`, nên không phải sửa `HomeViewModel`.

**Tech Stack:** View/XML, MVVM + StateFlow, Hilt, DataStore Preferences (đã có). **Không thêm dependency, không đổi schema Room, không thêm quyền runtime.** Chỉ thêm khối `<queries>` vào manifest.

**Spec:** TT#9 + dòng "mở rộng của M8b" trong `Launcher-Features.md`; `plans/M8b-search.md` (D1 nguồn kết quả, D2 xếp hạng, D3 ô trống, D10 chưa có cấu hình tìm kiếm); `AGENTS.md` (M2 package visibility, M7 Task 10 shortcut, M8b bài học).

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật)

- **M8b chốt D1/D3/D10 là "để sau"**, nên `SearchViewModel` hiện chỉ biết `AppItem`. Thêm hai loại kết quả mới buộc phải có một kiểu kết quả chung; cố nhét vào `AppItem` thì hàng Cài đặt/shortcut mang `ComponentKey` giả.
- **Package visibility (AGENTS.md, M2):** từ Android 11, `resolveActivity`/`queryIntentActivities` bị lọc theo `<queries>`; `LauncherApps` cũng bị lọc. Mỗi action Cài đặt phải được đo, không đoán.
- **Shortcut chỉ đọc được khi launcher là mặc định** (`hasShortcutHostPermission`, M7 Task 10). **Đo 2026-11-05: cả hai máy test hiện không có Os26Launcher là mặc định** (Oppo A3x: holder `com.android.launcher`; Live 4: activity HOME gần nhất là launcher khác). Nên kiểm shortcut cần chủ dự án đặt mặc định tạm.
- **Chi phí mỗi phím gõ:** `SearchViewModel.uiState` chạy trên `viewModelScope` (Main). Hiện mỗi phím gọi `normalize()` (Normalizer + 2 Regex) cho **cả 344 nhãn** trên Oppo. Thêm cài đặt (~20) và shortcut (có thể hàng trăm) mà vẫn chuẩn hóa lại mỗi phím là sai hướng. M8b còn món nợ: "Oppo 344 app gõ nhanh chưa đo" (Task 3 (g) hoãn).
- **File nóng:** `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml` là độc quyền; GS đang sửa `SearchPillView`, `SearchOverlayView` (kính ô nhập) và hàng Cài đặt; G2c sửa `HomeViewModel`. Mọi thứ M8d cần phải đi qua `domain/search`, `data/search`, `ui/search` và vài điểm "chỉ thêm" ở file dùng chung.
- **Dữ liệu thật đo hôm nay (2026-11-05, `adb shell cmd package query-activities --brief -a android.settings.X`):** xem mục "Cài đặt hệ thống" bên dưới; điểm đáng chú ý là Oppo có app thứ ba (Instabridge) cũng đăng ký `android.settings.WIFI_SETTINGS`.

### Quyết định 1 — Nguồn "app gần đây"

| Phương án | Cho gì | Chủ dự án phải đánh đổi | Chi phí / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Lịch sử mở app của chính launcher** (ghi lúc `AppRepositoryImpl.launch` thành công, lưu file ở `noBackupFilesDir`) | Đúng cái "người dùng mở từ launcher này"; có cả **tần suất** (đếm) lẫn **thời điểm**; xóa/tắt được ngay trong app; không cần quyền | Không thấy app mở từ thông báo, từ widget, từ launcher khác hay bằng nút Recents; **bắt đầu rỗng** sau khi cài (lịch sử trước đó mất) | Một khóa DataStore, ~30 mục × ~100 byte; ghi 1 lần/lần mở app | **Có** |
| **B. `UsageStatsManager`** + quyền Usage Access (`PACKAGE_USAGE_STATS`) | Có lịch sử **ngay từ lần mở đầu tiên**, gồm mọi đường mở app (thông báo, widget, app khác) | Phải rời launcher sang màn hệ thống cấp "Truy cập dữ liệu sử dụng" (đo: `ACTION_USAGE_ACCESS_SETTINGS` có trên cả hai máy), và chấp nhận launcher **đọc được mọi hoạt động của máy**, kể cả app ẩn khỏi lưới; gợi ý không có quyền = trống | Dữ liệu là **sự kiện foreground**, không phải "mở từ launcher": phải lọc launcher, SystemUI, bàn phím, app cài đặt; ROM (ColorOS) có thể cắt quyền hoặc rút ngắn lịch sử; đo thô `dumpsys usagestats` hôm nay: Live 4 620 sự kiện `ACTIVITY_RESUMED` (trong đó nhiều dòng là launcher), Oppo 245 — nhiều nhiễu cần lọc | Không (xem "Đánh đổi") |
| C. Lai: A mặc định, B tùy chọn bật trong Cài đặt | Có cả hai | Hai đường code + một đường quyền cho một dòng "gần đây" | Tăng bề mặt kiểm chứng (hai tier ROM) | Để sau, chỉ nếu chủ dự án muốn |

**Vì sao A:** (1) "app gần đây" trong ô tìm kiếm của launcher là để mở lại nhanh app mình vừa mở **từ đây**, nên đúng nguồn A. (2) B yêu cầu một quyền đặc biệt mà người dùng bình thường thấy đáng ngại cho một việc nhỏ; dự án đã tránh `QUERY_ALL_PACKAGES` vì lý do tương tự (AGENTS.md M2). (3) A lưu được số lần mở nên làm được cả "gợi ý" (dùng nhiều); B chỉ cho thời gian foreground, không phải số lần mở. (4) A không đổi schema Room (ràng buộc G2c). **Nhãn:** các điểm về hành vi `UsageStatsManager` là **đọc tài liệu + số `dumpsys` thô**, chưa dùng API thật; nếu chủ dự án chọn B thì Task đầu tiên phải đo bằng API thật trên cả hai máy.

**Nơi ghi:** `AppRepositoryImpl.launch` (thuộc `data/apps`, không nóng) là điểm chung của mọi lần mở app (`HomeViewModel:201`, `AppLibraryViewModel:65`, `SearchViewModel`). Ghi ở đây thay vì từng ViewModel = không sửa `HomeViewModel` (độc quyền, G2c đang giữ).

**Lưu ở đâu (sửa 2026-11-05 sau review):** một file nhỏ trong `noBackupFilesDir`, ghi nguyên tử bằng `AtomicFile` như `LabelCache`, KHÔNG ở DataStore: `launcher_prefs` nằm trong Auto Backup (`allowBackup="true"`, quy tắc backup không loại trừ gì) nên lịch sử sẽ rời máy và việc xóa không với tới bản sao lưu. Không loại `launcher_prefs` khỏi backup (mất cả các cài đặt khác) và không đổi cấu hình backup của manifest. Ba cờ bật/tắt của khối Tìm kiếm vẫn ở DataStore (khóa định nghĩa trong `data/search/`, không vào `SettingsKeys`). Không dùng Room.

### Quyết định 2 — "Gợi ý" khi ô trống

Gợi ý = **app dùng nhiều** (số lần mở ≥ 3, trừ các app đã nằm trong "Gần đây"), tối đa 4. Lý do: nguồn A đã có số đếm, không cần thêm dữ liệu. Không làm gợi ý theo giờ/vị trí (cần theo dõi nhiều hơn, vượt phạm vi). Ô trống hiện: `Gần đây` (≤ 5 app) rồi `Gợi ý` (≤ 4 app). Máy mới cài, chưa có lịch sử, hoặc tắt trong Cài đặt: về lại dòng gợi ý "Nhập tên ứng dụng" của M8b (không đổi hành vi cũ). Chế độ mở từ trang App Library (`open(showAll = true)`, liệt kê toàn bộ a-z) **giữ nguyên**, không hiện Gần đây.

### Quyết định 3 — Thứ tự kết quả và chi phí mỗi phím

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| **A. Xếp hạng chung rồi hòa theo loại** | Mọi kết quả cùng một danh sách; sắp theo (hạng D2, ưu tiên loại: App < Cài đặt < Shortcut, thứ tự gốc) | "wifi" ra Cài đặt Wi-Fi ngay đầu nếu không có app nào khớp tốt hơn; không cần tiêu đề nhóm | Query ngắn có thể trộn lẫn | **Có** |
| B. Nhóm cố định (Ứng dụng → Cài đặt → Shortcut) có tiêu đề | Dễ đoán như Spotlight | Truy vấn `a` ra 49 app đẩy Cài đặt/shortcut xuống dưới màn hình, không ai thấy | Mất lợi ích của việc thêm nguồn mới | Không |

**Chốt A với ba chặn nhiễu:**
- Truy vấn **1 ký tự (sau chuẩn hóa) chỉ ra app** (như M8b). Cài đặt và shortcut bắt đầu từ **2 ký tự**.
- **Tối đa 5 mục Cài đặt và 5 mục shortcut** trong một lần tìm.
- Mục Cài đặt khớp thêm **từ khóa** (vd "wifi", "mang", "internet") nhưng ở hạng thấp hơn khớp nhãn. Shortcut chỉ khớp **nhãn của chính shortcut**, không khớp theo nhãn app cha (nếu không, gõ "cam" sẽ đổ cả bốn shortcut của Camera ngay dưới Camera).

**Chi phí mỗi phím (mục tiêu, sẽ đo ở Task 2):** chuẩn hóa nhãn **một lần** khi nguồn đổi (app đổi, shortcut nạp xong, đổi ngôn ngữ), lưu trong `SearchIndex`. Mỗi phím chỉ chuẩn hóa **truy vấn** rồi quét ~344 + ~25 + (shortcut) chuỗi bằng `startsWith/contains`. Việc quét chuyển sang `Dispatchers.Default` với `mapLatest` (phím mới hủy phím cũ) để Main không bị chiếm. **Ngân sách:** p95 thời gian tính ≤ 4 ms trên Oppo 344 app; không thêm khung > 33 ms so với bản M8b trong cùng kịch bản gõ (so **số khung tuyệt đối**, bài học AN). Con số này là **mục tiêu chưa đo**, Task 2 đo bản M8b làm mốc trước rồi đo bản mới.

### Quyết định 4 — Cài đặt của tìm kiếm có nằm trong M8d không?

**Có, nhưng tối thiểu và tách rời:** lịch sử là dữ liệu riêng tư mới (M8b D10 chỉ bỏ qua vì "chưa có gì đáng cấu hình"; giờ có), nên cần ít nhất công tắc tắt và nút xóa. Bốn hàng, một khối `Tìm kiếm` ở **cuối** danh sách Cài đặt: công tắc *Cài đặt hệ thống*, công tắc *Lối tắt ứng dụng*, công tắc *Gợi ý và app gần đây*, hàng *Xóa lịch sử mở app*. Khối nằm trong file riêng (`SearchSettingsBlock`), có ViewModel riêng, chỉ cần vài dòng "thêm" ở `SettingsActivity` (xem Vùng file). Khi G2d (dashboard) chuyển Cài đặt, chỉ việc di chuyển khối này. Đây là Task cuối và **cắt được** mà không ảnh hưởng ba nguồn còn lại (mặc định bật hết).

### Đánh đổi chấp nhận

- Lịch sử bắt đầu rỗng; app mở ngoài launcher không có trong "Gần đây". Xét lại (phương án C) nếu chủ dự án thấy trống quá lâu.
- Shortcut im lặng vắng mặt khi không là launcher mặc định (không nhắc nhở trong kết quả); trạng thái hiển thị ở hàng *Lối tắt ứng dụng* trong Cài đặt.
- Một biểu tượng bánh răng chung cho mọi mục Cài đặt (không vẽ 20 icon riêng).

---

## Cài đặt hệ thống: bằng chứng đo và danh sách phiên bản đầu

Đo 2026-11-05 bằng `adb shell cmd package query-activities --brief -a android.settings.<ACTION>` (số activity xử lý được action; **chưa** tính lọc `<queries>`, cái đó đo ở Task 1). Live 4 = Android 11 (API 30), Oppo A3x = Android 14 (API 34).

| Action (`Settings.ACTION_*`) | Live 4 | Oppo | Ghi chú |
|---|---|---|---|
| `SETTINGS`, `AIRPLANE_MODE`, `DISPLAY`, `SOUND`, `APPLICATION`, `BATTERY_SAVER`, `LOCATION_SOURCE`, `DATE`, `LOCALE`, `INTERNAL_STORAGE`, `SECURITY`, `PRIVACY`, `ACCESSIBILITY`, `DATA_ROAMING`, `INPUT_METHOD`, `MANAGE_ALL_APPLICATIONS`, `MANAGE_DEFAULT_APPS`, `DEVICE_INFO`, `VPN`, `CAST`, `NOTIFICATION`, `SYNC` | 1 | 1 | Có trên cả hai |
| `WIFI_SETTINGS` | 1 | **3** | Oppo: `com.oplus.wirelesssettings`, `com.android.settings`, và **`com.instabridge.android` (app ngoài)** |
| `BLUETOOTH_SETTINGS` | 1 | 2 | Oppo: `oplus.wirelesssettings` + Settings |
| `WIRELESS_SETTINGS` | 1 | 2 (+1 app ngoài) | Oppo có `com.instabridge.android` |
| `NFC_SETTINGS` | **2** | 1 | Live 4 có hai trình xử lý |
| `ZEN_MODE_SETTINGS`, `DATA_USAGE_SETTINGS` | 1 | 2 | |
| `NIGHT_DISPLAY_SETTINGS` | 1 | **0** | Oppo không có (tên khác của ROM) |
| `PRINT_SETTINGS` | 0 | 0 | Bỏ |

**Hệ quả thiết kế (đã thấy, chưa kiểm hành vi):**
1. Một action có thể có **nhiều trình xử lý, kể cả app ngoài**. Gọi `startActivity(Intent(action))` trần có thể hiện hộp chọn app hoặc mở nhầm app ngoài. Cách xử lý đề xuất: khi dựng danh mục, `queryIntentActivities`, **chỉ nhận trình xử lý có cờ hệ thống** (`FLAG_SYSTEM`), chọn một và mở bằng `setPackage`/component đó; không có trình xử lý hệ thống thì bỏ mục khỏi danh mục của máy này.
2. Mục nào máy không hỗ trợ (đếm = 0, như `NIGHT_DISPLAY_SETTINGS` trên Oppo) **không hiện** trong kết quả: lọc ngay lúc dựng danh mục, không đợi người dùng bấm rồi `ActivityNotFoundException`.
3. `startActivity` vẫn bọc `try/catch` (`ActivityNotFoundException`, `SecurityException`): lỗi thì Toast (tái dùng `app_launch_failed`), giữ lớp tìm kiếm mở, như D8 của M8b.
4. `<queries>` cần **khai theo từng action** (AGENTS.md M2: `resolveActivity` chỉ thấy cái được khai). Đã có sẵn `SETTINGS` và `HOME_SETTINGS`. Task 1 đo, với từng action, `resolve` ra gì **có** và **không có** khai; chỉ khai những action cần.

**Danh sách phiên bản đầu (ứng viên, chốt lại sau Task 1; ≤ 24 mục):** Wi-Fi, Bluetooth, Chế độ máy bay, Mạng di động/dữ liệu (`DATA_ROAMING`/`WIRELESS`), Sử dụng dữ liệu, Màn hình (`DISPLAY`), Âm thanh (`SOUND`), Thông báo, Không làm phiền (`ZEN_MODE`), Pin/Tiết kiệm pin, Vị trí, Ngày giờ, Ngôn ngữ, Bộ nhớ, Bảo mật, Quyền riêng tư, Trợ năng, Ứng dụng (`APPLICATION`), Ứng dụng mặc định (`MANAGE_DEFAULT_APPS`), Bàn phím (`INPUT_METHOD`), Cast, NFC, VPN, Giới thiệu về điện thoại (`DEVICE_INFO`), Cài đặt (mở trang chủ Cài đặt). Mỗi mục có nhãn tiếng Việt và 1–4 từ khóa (vd Wi-Fi: "wifi", "wi-fi", "mang", "internet"). **Không** đưa các action cần extras hoặc quyền đặc biệt (`ACTION_APP_NOTIFICATION_SETTINGS`, `ACTION_MANAGE_OVERLAY_PERMISSION`...). Hằng số nào chỉ có từ API > 26 thì dùng chuỗi action hoặc chặn bằng `Build.VERSION.SDK_INT` (kiểm khi viết, Task 3).

Từ khóa gõ **không dấu** hợp với chuẩn hóa M8b (`AppSearchMatcher.normalize`): "man hinh" khớp "Màn hình", "dien thoai" khớp "Giới thiệu về điện thoại".

---

## Shortcut app: hành vi theo trạng thái launcher

| Tình huống | Hành vi |
|---|---|
| Là launcher mặc định (`hasShortcutHostPermission() == true`) | Dựng chỉ mục shortcut (động + manifest, **không** pinned, như menu nhấn giữ M7), bỏ app ẩn, tối đa 5 mục/lần tìm, từ 2 ký tự |
| **Không** là mặc định | Không có shortcut trong kết quả, **không** báo lỗi, **không** nhắc trong danh sách kết quả. Hàng *Lối tắt ứng dụng* ở Cài đặt (Task 6) hiển thị "Cần đặt làm launcher mặc định" và bấm vào mở luồng đặt mặc định có sẵn. Quyền đổi giữa chừng (người dùng vừa đặt mặc định): chỉ mục dựng lại mỗi lần mở lớp tìm kiếm nên lần mở sau tự có |
| Đọc lỗi (`SecurityException`, app vừa gỡ, profile mất) | Trả rỗng + log, như `ShortcutRepositoryImpl` hiện có |
| Bấm shortcut | `ShortcutRepository.startShortcut` đã có (M7); thất bại → Toast, giữ lớp |

**Điều chưa biết, buộc đo ở Task 1:** `LauncherApps.getShortcuts` có cho truy vấn **không đặt package** (lấy tất cả) hay không; nếu không, phải lặp theo từng app (344 lần gọi binder trên Oppo) và khi đó chi phí dựng chỉ mục phải đo. Dựng chỉ mục luôn ở luồng nền, có hạn chót thời gian, và kết quả shortcut chỉ **thêm vào** khi sẵn sàng (app và Cài đặt hiện ngay, không chờ shortcut).

---

## Cập nhật sau duyệt (2026-11-05)

- Đã chốt: câu 1 (A), 2 (gợi ý = app dùng nhiều), 3, 5 (làm khối Tìm kiếm ngay), 6 (tắt gợi ý thì xóa lịch sử). Điều phối cập nhật: Task 4 và công tắc *Lối tắt ứng dụng* **giữ**.
- **Shortcut theo kiểu iOS 17:** ngoài khớp nhãn riêng của shortcut, khi có app đứng đầu (top hit) thì hiện thêm **tối đa 3 shortcut của app đó** ngay dưới nó. Tổng shortcut vẫn ≤ 5, khớp nhãn từ 2 ký tự, vắng mặt im lặng khi launcher không là mặc định. **Dùng lại đường đọc shortcut của menu nhấn giữ (M7 Task 10, `ShortcutRepository.getShortcuts`/`startShortcut`)**, không dựng đường thứ hai; chỉ mục toàn cục (nếu Task 1 chứng minh được) là phần thêm, không thay thế.
- **Kiểm shortcut chỉ trên Live 4** (điều phối đặt mặc định tạm rồi trả). **Oppo: hành vi shortcut chưa đo.**
- Task 1 bỏ phần thăm dò shortcut toàn cục trên Oppo; chỉ thăm dò `getShortcuts` trên Live 4 khi đã là mặc định.

## Câu hỏi chờ chủ dự án (đã trả lời, giữ để tra)

1. Nguồn "gần đây": chọn A (lịch sử của launcher, không quyền) như đề xuất, hay B/C (UsageStats)?
2. "Gợi ý" = app dùng nhiều (≥ 3 lần), tối đa 4, dưới "Gần đây" tối đa 5. Đúng ý chưa, hay muốn gợi ý cả mục Cài đặt?
3. Danh sách ~24 mục Cài đặt và một icon bánh răng chung: đủ cho bản đầu chưa?
4. Không phải launcher mặc định: shortcut im lặng, trạng thái chỉ ở Cài đặt. Có muốn nhắc ngay trong kết quả không?
5. Khối "Tìm kiếm" trong Cài đặt làm ngay ở M8d (Task 6) hay chờ dashboard G2d?
6. Tắt "Gợi ý và app gần đây" thì xóa luôn lịch sử: chấp nhận không?

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | `SearchUiState.results` thành `List<SearchResult>` (sealed `App / Setting / Shortcut / Header`) | Agent đề xuất 2026-11-05, chờ duyệt. Không nhét Cài đặt/shortcut vào `AppItem` |
| D2 | Nguồn "gần đây" = lịch sử mở app của chính launcher (file noBackup), không dùng UsageStats | Đề xuất 2026-11-05, chờ chủ dự án (câu hỏi 1) |
| D3 | Ghi lịch sử trong `AppRepositoryImpl.launch` khi mở thành công, bỏ qua chính package của launcher | Để không sửa `HomeViewModel` (độc quyền) |
| D4 | Gợi ý = app mở ≥ 3 lần, ≤ 4, trừ app đã có trong "Gần đây" (≤ 5). Lưu file noBackup, tối đa 30 mục | Chờ chủ dự án (câu hỏi 2) |
| D5 | Xếp hạng chung: (hạng D2 của M8b, ưu tiên App < Cài đặt < Shortcut, thứ tự gốc). 1 ký tự chỉ ra app; Cài đặt/shortcut từ 2 ký tự, ≤ 5 mỗi loại | Đề xuất 2026-11-05 |
| D6 | Mục Cài đặt: chỉ nhận trình xử lý hệ thống (`FLAG_SYSTEM`), mở bằng component đó; không có thì ẩn mục trên máy đó | Dựa trên số đo Oppo có app ngoài đăng ký `WIFI_SETTINGS` |
| D7 | Shortcut: chỉ khi launcher mặc định; không mặc định thì im lặng, trạng thái ở Cài đặt | Chờ chủ dự án (câu hỏi 4) |
| D8 | Chỉ mục chuẩn hóa dựng sẵn + tính trên `Dispatchers.Default` với `mapLatest` | Ngân sách ở Quyết định 3, đo ở Task 2 |
| D9 | Khối Cài đặt "Tìm kiếm" ở cuối, file riêng, mặc định bật hết; tắt "gợi ý và gần đây" thì xóa lịch sử | Chờ chủ dự án (câu hỏi 5, 6) |
| D10 | Không đổi schema Room; không thêm quyền runtime; không thêm vào `SettingsKeys.kt` | Ràng buộc G2c/MultiAgent 5.3 |

## Ngoài phạm vi / ghi để sau

- Danh bạ, tìm trong web, tìm trong file (G3 hoặc sau).
- `UsageStatsManager` (phương án B/C) — chỉ khi chủ dự án đổi ý.
- Icon riêng cho từng mục Cài đặt; xếp hạng theo mức dùng cho kết quả tìm (hiện chỉ dùng cho ô trống).
- Mở shortcut đã ghim (pinned); shortcut của app trong work profile ngoài những gì `LauncherApps` trả về.
- Đệm bàn phím API 26–29 (món nợ M8b, chưa có máy).

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/search/SearchResult.kt` | Tạo | không |
| `domain/search/SearchIndex.kt` | Tạo | không |
| `domain/search/AppSearchMatcher.kt` | Sửa (thêm hàm xếp hạng dùng chung, giữ `normalize/search` cũ) | không |
| `domain/model/SearchSettingEntry.kt`, `ShortcutEntry.kt`, `RecentApp.kt`, `SearchPreferences.kt` | Tạo | không |
| `domain/repository/SystemSettingsRepository.kt`, `ShortcutSearchRepository.kt`, `RecentAppsRepository.kt`, `SearchPreferencesRepository.kt` | Tạo | không |
| `data/search/SystemSettingsCatalog.kt`, `SystemSettingsRepositoryImpl.kt`, `ShortcutSearchRepositoryImpl.kt`, `RecentAppsRepositoryImpl.kt`, `SearchPreferencesRepositoryImpl.kt` | Tạo | không |
| `di/SearchModule.kt` | Tạo (binding riêng, không sửa `RepositoryModule.kt`) | không |
| `data/apps/AppRepositoryImpl.kt` | Sửa (inject `RecentAppsRepository`, ghi khi `launch` thành công) | không (M2/M3) |
| `ui/search/SearchViewModel.kt`, `SearchUiState.kt`, `SearchResultAdapter.kt` | Sửa | không (vùng của M8b) |
| `res/layout/item_search_result.xml` | Sửa (thêm dòng phụ); tạo `item_search_header.xml` | không |
| `res/drawable/ic_search_setting.xml` | Tạo (bánh răng) | không |
| `ui/settings/SearchSettingsBlock.kt`, `SearchSettingsViewModel.kt` | Tạo | không |
| `ui/settings/SettingsActivity.kt`, `SettingsRow.kt` | Sửa — **chỉ thêm**: nối khối vào cuối `buildRows`, nhánh `when` bấm/công tắc, 4 hằng `ID_SEARCH_*` ở cuối `companion` | dùng chung có kiểm soát |
| `res/values/strings.xml`, `dimens.xml` | Sửa — một khối `search_extras_` / `settings_search_` ở **cuối** file | dùng chung có kiểm soát |
| `AndroidManifest.xml` | Sửa — thêm khối `<intent><action …/></intent>` trong `<queries>` (chỉ những action Task 1 chứng minh cần) | dùng chung có kiểm soát |

**Không đụng:** `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `LayoutReconciler`, `data/layout/db`, `core/glass`, `DragHelper`, `SearchPillView`, `SearchOverlayView` (GS giữ phần kính ô nhập; M8d không cần sửa vì `render()` chỉ đọc `state.results` + `status`, kiểu mới vẫn tương thích), `SearchOverlayController` (`SearchResultAdapter(scope, loadIcon, onTapped)` giữ nguyên dạng gọi; lỗi mở mục mới tái dùng `SearchEvent.LaunchFailed(label)` nên không thêm nhánh vào `when`). Nếu khi viết mã phát hiện **buộc** phải sửa một trong số này, agent dừng và báo trước (MultiAgent 5.1).

**Rủi ro chồng file:** `SearchResultAdapter.kt`/`item_search_result.xml` — nếu GS đổi kiểu hàng kết quả thì phải rebase; chưa thấy GS khai. `SettingsActivity.kt` — GS thêm hàng, G2d sắp di chuyển; mỗi điểm sửa của M8d là một khối thêm riêng để giải xung đột nhanh.

## Global Constraints

- `domain` không import `android.*` (Normalizer của JDK được); `ui` không import `data` trực tiếp. Intent, `PackageManager`, `LauncherApps` chỉ ở `data/`.
- Mọi chữ hiển thị ở `strings.xml` (tiếng Việt), màu `colors.xml`, kích thước `dimens.xml`; 8dp grid, vùng chạm ≥ 48dp (hàng ≥ 56dp), WCAG AA. Tài nguyên mới có tiền tố `search_extras_` / `settings_search_`, đặt thành khối liền ở cuối file.
- Màn mới định nghĩa đủ trạng thái: Default (có kết quả), Empty (không khớp), Idle (ô trống và chưa có lịch sử → dòng gợi ý M8b), Idle-có-gợi-ý (Gần đây/Gợi ý), Loading (app chưa nạp), Error (mở thất bại → Toast, giữ lớp), Disabled (nguồn bị tắt hoặc không khả dụng: không hiện mục nào, không báo lỗi).
- **Không đổi schema Room**, không thêm quyền runtime, không `QUERY_ALL_PACKAGES`. Khóa DataStore mới định nghĩa trong `data/search/`.
- Hành vi M8b không được hỏng: kết quả chỉ có app khi gõ 1 ký tự; thứ tự app như cũ; mở từ App Library vẫn liệt kê toàn bộ a-z; cử chỉ mở, Back, Home, đổi sáng/tối, ẩn/gỡ app khi lớp mở.
- Chuẩn hóa nhãn tiếng Việt bằng đúng `AppSearchMatcher.normalize`; không tạo bản chuẩn hóa thứ hai.
- Mọi lời gọi `LauncherApps`/`PackageManager` ở luồng nền (`@IoDispatcher`); không chặn Main khi dựng chỉ mục.
- Lịch sử mở app: chỉ lưu ở máy (file `noBackupFilesDir`, không vào backup), không log nội dung vào logcat bản phát hành; log tạm tag `M8D` phải xóa trước khi đóng Task.
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự, không dấu; kèm dòng đồng tác giả theo quy ước repo.

## Review Focus

- **Hồi quy M8b:** gõ `may anh`, `dien thoai`, `đ`, `a` ra đúng thứ tự cũ, chỉ có app; nhãn emoji/NFD không crash; đóng/mở, Back, Home, đổi sáng/tối, gỡ app khi lớp mở vẫn như M8b.
- **Mục Cài đặt:** không hiện mục máy không hỗ trợ; không mở nhầm app ngoài (ca Instabridge trên Oppo); không hiện hộp chọn app; bấm mục thật sự mở đúng màn Cài đặt rồi quay lại launcher được.
- **Shortcut:** không mặc định → không có shortcut, không lỗi, không nháy; mặc định → có, mở đúng; đổi mặc định giữa chừng không crash; app ẩn không lộ shortcut.
- **Lịch sử:** ghi cho MỌI lần mở qua `AppRepositoryImpl.launch`, kể cả từ App Library (quyết định 2026-11-05); ghi đúng một lần mỗi lần mở thành công (mở thất bại không ghi); app đã gỡ/ẩn không còn trong Gần đây/Gợi ý; xóa lịch sử và tắt công tắc thật sự xóa; lịch sử KHÔNG nằm trong dữ liệu Auto Backup; không ghi từ chế độ App Library.
- **Hiệu năng:** p95 thời gian tính mỗi phím và số khung > 33 ms khi gõ nhanh trên Oppo 344 app không tệ hơn mốc M8b; không dựng chỉ mục shortcut trên Main.
- **Vòng đời:** `recreate()` khi lớp mở giữ truy vấn; chỉ mục không rò (Job hủy khi ViewModel clear); Hilt không vòng phụ thuộc (`AppRepositoryImpl` ← `RecentAppsRepository`).
- **Cô lập khối Cài đặt:** khối "Tìm kiếm" rời `SettingsActivity` được mà không để lại tham chiếu.

---

### Task 1: Đo giả định trên máy thật (không code tính năng)

**Bắt buộc kiểm tay:** có — mục 4.2: các Task sau xây trên giả định chỉ máy thật trả lời được (action nào resolve được dưới `<queries>`, `getShortcuts` có truy vấn toàn bộ không, thời gian dựng chỉ mục).
**Máy:** Live 4 và Oppo A3x (cả hai; đây là Task đo ROM). Shortcut cần launcher **mặc định**: điều phối đặt tạm trên từng máy rồi trả lại (MultiAgent quyết định 7).

**Files:** Sửa tạm `ui/search/SearchViewModel.kt` hoặc một lớp thăm dò `data/search/M8dProbe.kt` (xóa cuối Task; log tag `M8D`). Không đổi `<queries>` cố định trước khi có số đo.

**Interfaces:** Produces: bảng số đo ghi vào "Nhật ký" của plan và cập nhật lại danh sách mục Cài đặt + danh sách action cần `<queries>`.

- [ ] **Step 1: Resolve từng action ứng viên (khoảng 30 action ở mục Cài đặt hệ thống)** với `PackageManager.queryIntentActivities`: (a) manifest chưa khai thêm action nào ngoài `SETTINGS`/`HOME_SETTINGS`; (b) sau khi khai đủ. Ghi mỗi action: số trình xử lý, package, có `FLAG_SYSTEM` không. Tìm ra action nào resolve được **không cần** khai (nhóm tự miễn trừ) để khỏi khai thừa.
- [ ] **Step 2: Mở thử từng mục đã chọn bằng component hệ thống** (`setPackage`/`ComponentName`): màn đúng mở không, có hộp chọn app không, `SecurityException`/`ActivityNotFoundException` ở mục nào. Riêng `WIFI_SETTINGS` trên Oppo: so sánh mở bằng intent trần (thấy chọn app/mở nhầm Instabridge hay không) với mở bằng component hệ thống.
- [ ] **Step 3: Shortcut khi launcher mặc định** — `getShortcuts` với `ShortcutQuery` **không** `setPackage` (có ra kết quả hay ném lỗi); nếu lỗi, lặp theo từng package app hiển thị và đo tổng thời gian (Oppo 344 app). Số shortcut thu được (động + manifest, không pinned). Cũng ghi `hasShortcutHostPermission()` khi **không** mặc định để xác nhận hành vi "im lặng".
- [ ] **Step 4: Chốt** — cập nhật plan: danh sách mục Cài đặt cuối cùng, danh sách `<queries>` cần khai, cách dựng chỉ mục shortcut (một lần hay theo package) và thời gian đo. Gỡ code thăm dò.
- [x] **Step 5: Kịch bản kiểm (Live 4 + Oppo)** — chủ dự án xác nhận bảng số đo và quyết định cuối (câu hỏi còn mở ở Task này). Commit: `docs: record M8d probe results` (chỉ plan, không mã).

**Kết quả Task 1 (2026-11-05, probe `resolveActivity` + `queryIntentActivities` trong app, manifest CHƯA khai thêm action nào; điều phối chạy):**
- **Live 4 (API 30), log đủ 32 action:** mọi action đều `resolve` được, 1 trình xử lý hệ thống, **không cần khai thêm `<queries>`** (package `com.android.settings` đã thấy được nhờ `<intent>` `SETTINGS`/`HOME_SETTINGS` sẵn có; khai theo intent mở tầm nhìn cả package). Hai ngoại lệ: `NFC_SETTINGS` và `APPLICATION_DEVELOPMENT_SETTINGS` có 2 trình xử lý cùng package `com.android.settings`, intent trần `resolve` ra `ResolverActivity` (hộp chọn) -> bắt buộc mở bằng component cụ thể. `NIGHT_DISPLAY_SETTINGS` có trên Live 4. `HOME_SETTINGS`/`MANAGE_DEFAULT_APPS_SETTINGS` thuộc `permissioncontroller` (hệ thống).
- **Oppo A3x (API 34), log bị cắt sau 17 dòng** (nghi điện thoại bỏ log dồn, chưa xác nhận): `SETTINGS`, `AIRPLANE`, `DISPLAY`, `SOUND`, `APPLICATION`, `NOTIFICATION`, `BATTERY_SAVER`, `LOCATION`, `DATE`, `LOCALE`, `STORAGE`, `SECURITY`, `PRIVACY` đều resolve, 1 trình xử lý hệ thống. **`WIFI_SETTINGS`: 3 trình xử lý, trong đó `com.instabridge.android` (APP) — xác nhận**; intent trần resolve ra `oplus.wirelesssettings` (hệ thống). `BLUETOOTH_SETTINGS`: 2 (cả hai hệ thống). Phần còn lại dùng số `query-activities` của adb (không lọc `<queries>`): `NIGHT_DISPLAY_SETTINGS` không có trên Oppo. Mục chưa thấy qua probe được bảo vệ bằng bước lọc lúc chạy (resolve lúc dựng danh mục), nên không cần chạy lại probe.
- **Quyết định:** (1) **không thêm `<queries>`** cho Settings; giữ lọc lúc chạy để mục không resolve được thì ẩn. (2) Mở mục bằng component: lấy `resolveActivity` nếu nó là hệ thống và không phải `ResolverActivity`, không thì trình xử lý hệ thống đầu tiên; không có -> bỏ mục. (3) Probe shortcut (Step 3) dời sang Task 4, chỉ trên Live 4.

### Task 2: Lõi — `SearchResult`, `SearchIndex`, đo chi phí mỗi phím (chỉ app, hành vi M8b không đổi)

**Bắt buộc kiểm tay:** có — mục 4.2: phép đo hiệu năng dùng để chọn phương án (có cần `mapLatest`/chỉ mục hay đủ rồi).
**Máy:** Oppo A3x (344 app, đo chính), Live 4 (87 app, đối chiếu).

**Files:** Tạo `domain/search/SearchResult.kt`, `SearchIndex.kt`; sửa `domain/search/AppSearchMatcher.kt`, `ui/search/SearchUiState.kt`, `SearchViewModel.kt`, `SearchResultAdapter.kt`, `res/layout/item_search_result.xml` (dòng phụ, ẩn mặc định).

**Interfaces:**
- Consumes: `AppRepository.apps`, `GetHiddenAppsUseCase`.
- Produces: `sealed interface SearchResult { App(item) ; Setting(entry) ; Shortcut(entry) ; Header(kind) }`; `class SearchIndex` chứa các mục kèm nhãn **đã chuẩn hóa** và từ khóa; `SearchIndex.search(query, limits): List<SearchResult>` thuần, không `android.*`. `SearchViewModel.onResultTapped(result: SearchResult, source: ScreenRect?)`, `loadIcon(result: SearchResult, sizePx: Int): Bitmap?` (giữ chữ ký gọi từ `SearchOverlayController`).

- [ ] **Step 0: Đo mốc M8b** trên bản `main` (chưa sửa gì): Oppo 344 app gõ liên tục `d`, `di`, `die`, `dien`, ... 10 lần; ghi `dumpsys gfxinfo` (tổng khung, số khung > 33 ms, p95) và, bằng log tạm `M8D`, thời gian tính của khối `AppSearchMatcher.search`. Đây là mốc so sánh; ghi vào plan.
- [ ] **Step 1: `SearchResult` + `SearchIndex`** — chuẩn hóa nhãn một lần; `search` giữ đúng luật D2 (hạng 0/1/2, ổn định); trả `List<SearchResult>`. Mục `Setting/Shortcut` chưa có nguồn (danh sách rỗng) ở Task này.
- [ ] **Step 2: `SearchViewModel`** — dựng `SearchIndex` khi `visibleApps` đổi (không phải mỗi phím) bằng `flowOn(Dispatchers.Default)`; truy vấn dùng `mapLatest`. `showAll` giữ nguyên.
- [ ] **Step 3: `SearchResultAdapter`** — nhiều `viewType` (App, Setting, Shortcut, Header); hàng App giữ hành vi M8b; `DiffUtil` theo khóa ổn định của từng loại.
- [ ] **Step 4: Kịch bản đo (Oppo + Live 4)** — (a) hồi quy M8b: các truy vấn ở Review Focus 1 ra đúng thứ tự; (b) lặp kịch bản gõ ở Step 0 trên bản mới, so **số khung tuyệt đối**, p95 và thời gian tính; ngân sách p95 tính ≤ 4 ms, không thêm khung > 33 ms; (c) xen kẽ chạy bản cũ/mới ít nhất hai lượt mỗi bên (bài học AN: máy nóng dần). Nếu không đạt: phân tích rồi báo, không vá liều.
- [ ] **Step 5: Gỡ log `M8D`, commit** `refactor: index search results and run matching off main`.

**Kết quả đo Task 2 (Oppo A3x 344 app, 2026-11-05, APK có M8dTiming, 2 lượt, gõ `dienth` và `abccam` 4 vòng, 48 phím mỗi lượt; điều phối chạy):**

| Chỉ số | Lượt 1 | Lượt 2 |
|---|---|---|
| Bản mới, p50 mỗi 20 phím (ms) | 7,6 / 4,6 / 4,7 / 4,8 | 7,3 / 5,0 / 5,1 / 5,4 |
| Bản mới, p95 mỗi 20 phím (ms) | 43,3 / 8,8 / 7,3 / 22,2 | 22,2 / 9,3 / 8,6 / 33,1 |
| Đường M8b, p50 (ms) | 81,9 / 68,3 / 73,6 / 67,6 | 78,9 / 76,8 / 71,4 / 69,8 |
| Đường M8b, p95 (ms) | 166 / 93 / 91 / 93 | 180 / 92 / 88 / 110 |
| gfxinfo bản mới: khung, janky, p95 khung | 353, 27,2%, 101 ms | 355, 30,4%, 117 ms |
| gfxinfo `main` cùng kịch bản: khung, janky, p95 khung | 300, 45,0%, 109 ms | 314, 42,7%, 109 ms |

- **Kết luận:** thời gian tính nhanh hơn khoảng 14 lần ở p50 so với M8b. **Mục tiêu p95 <= 4 ms KHÔNG đạt:** p95 thường 7-9 ms, có đỉnh 22-43 ms. Ghi nhận là trượt mục tiêu, chưa tìm nguyên nhân đỉnh (chưa khẳng định do GC, dựng lại chỉ mục khi gõ, hay máy nóng).
- **gfxinfo:** tỷ lệ khung janky thấp hơn `main` (27-30% so với 43-45%); p95 thời gian khung không đổi (101-117 so với 109 ms, p99 cùng 150 ms). Bàn phím đang hiện trong cảnh đo nên p95 khung có thể không phải do code này; **không khẳng định nguyên nhân**.
- Chưa có dòng `index ...` (bộ lọc log của người đo chỉ lấy dòng n=20). Live 4 và kiểm shortcut chờ chủ dự án xong việc trên máy đó.

**Kết quả kiểm Live 4 trên bản đã rebase `bf567a4` (2026-11-05, điều phối chạy; đặt launcher mặc định tạm rồi trả):**
- Chỉ mục shortcut: 85 mục từ 83 app trong 197 ms (đo được).
- `wifi`, `nfc`, `bluetooth` ra mục Cài đặt; Wi-Fi mở `WifiSettings2Activity`, Back về launcher. `dev` và `camera` không ra kết quả. `chrome` ra Chrome kèm "Thẻ mới" và "Thẻ ẩn danh mới"; bấm "Thẻ mới" mở Chrome.
- `may` ra Máy ảnh, Máy tính, Chế độ máy bay, Giới thiệu về điện thoại. Mở Máy tính thì nó xuất hiện dưới "Gần đây".
- Cài đặt: công tắc kính và công tắc tìm kiếm nằm cùng nhau, định tuyến đúng; tắt gợi ý thì hỏi trước; Hủy giữ nguyên trạng thái.
- **Chưa chạy:** lượt gõ trên Live 4, "Gợi ý" sau 3 lần mở trở lên, ẩn app khỏi danh sách, chuyển lại không mặc định, xóa lịch sử qua hộp thoại, shortcut trên Oppo (Oppo không đo shortcut).

**Sửa theo review độc lập (2026-11-05, đọc code, 6 điểm đều xác nhận và đã sửa):**
1. (Trung bình, riêng tư) Lịch sử rời máy qua Auto Backup: chuyển sang file `noBackupFilesDir`, dọn khóa DataStore cũ.
2. (Trung bình) `SearchSettingsViewModel` không bắt lỗi ghi: thêm try/catch, Toast `settings_save_failed` và dựng lại hàng.
3. (Thấp-trung bình) Danh sách giữ mốc cũ sau `submitList`: `scrollToPosition(0)` khi truy vấn đổi hoặc lớp vừa mở. Sửa `ui/search/SearchOverlayView.kt` (file GS cũng chạm; chỉ thêm trong `render`).
4. (Thấp) `refreshShortcuts` kiểm cache 60 giây trước quyền: nay kiểm `canReadShortcuts()` trước, mất quyền thì xóa shortcut ngay.
5. (Thấp) `prefs` thiếu `distinctUntilChanged` và `settingEntries` dựng hai lần: thêm `distinctUntilChanged` ở repository và ViewModel; `settingEntries` thành `StateFlow` nạp một lần.
6. (Thấp) Hộp thoại khối Tìm kiếm không được theo dõi: `SearchSettingsBlock` thành lớp giữ tối đa một hộp thoại, `SettingsActivity.onDestroy` gọi `dismiss()`.

### Task 3: Cài đặt hệ thống trong kết quả

**Bắt buộc kiểm tay:** không (đã có Task 1 làm giả định); điều phối gộp vào bài kiểm cuối.
**Máy:** Live 4; Oppo cho ca nhiều trình xử lý (Wi-Fi/Instabridge, Night display vắng).

**Files:** Tạo `domain/model/SearchSettingEntry.kt`, `domain/repository/SystemSettingsRepository.kt`, `data/search/SystemSettingsCatalog.kt`, `SystemSettingsRepositoryImpl.kt`, `di/SearchModule.kt`, `res/drawable/ic_search_setting.xml`; sửa `SearchViewModel.kt`, `SearchResultAdapter.kt`, `item_search_result.xml`, `AndroidManifest.xml` (`<queries>`), `strings.xml` (khối cuối).

**Interfaces:**
- Produces: `SystemSettingsRepository { suspend fun entries(): List<SearchSettingEntry>; fun open(id: String): Boolean }` — `entries()` đã lọc theo máy (chỉ mục có trình xử lý hệ thống), nhãn/từ khóa lấy từ `strings.xml`.

- [ ] **Step 1: Danh mục** — bảng cố định `id → (action, nhãn, từ khóa)` theo kết quả Task 1; `entries()` dựng một lần (lazy, ở IO), cache theo locale.
- [ ] **Step 2: Mở mục** — dùng component hệ thống tìm được ở Step 2 Task 1; `try/catch` → `false`; `SearchViewModel` phát `LaunchFailed(label)` (Toast, giữ lớp) nếu thất bại, `close()` nếu thành công.
- [ ] **Step 3: Nối vào `SearchIndex`** — hạng: khớp nhãn (0/1/2 như app), khớp từ khóa ở hạng thấp hơn khớp nhãn; từ 2 ký tự; ≤ 5 mục; ưu tiên App < Cài đặt.
- [ ] **Step 4: Hàng Cài đặt** — icon bánh răng, dòng phụ "Cài đặt", cao ≥ 56dp, `contentDescription` đọc được ("Cài đặt: Wi-Fi").
- [ ] **Step 5: Kịch bản kiểm (Live 4, Oppo)** — (a) `wifi`, `wi-fi`, `mang` ra Wi-Fi; (b) `man hinh` / `Màn hình` / `display` ra mục Màn hình; (c) bấm: màn Cài đặt đúng mở, Back quay lại launcher; (d) Oppo `wifi`: mở thẳng màn hệ thống, **không** hộp chọn app, **không** Instabridge; (e) Oppo gõ `night`/`ban dem`: không hiện (máy không hỗ trợ), Live 4 hiện; (f) gõ 1 ký tự `w`: chỉ app; (g) hồi quy M8b.
- [ ] **Step 6: Commit** `feat: add system settings entries to search`.

### Task 4: Shortcut app trong kết quả

**Bắt buộc kiểm tay:** không (đã đo ở Task 1); gộp vào bài kiểm cuối.
**Máy:** Live 4 (đặt mặc định tạm để kiểm).

**Files:** Tạo `domain/model/ShortcutEntry.kt`, `domain/repository/ShortcutSearchRepository.kt`, `data/search/ShortcutSearchRepositoryImpl.kt`; sửa `di/SearchModule.kt`, `SearchViewModel.kt`, `SearchResultAdapter.kt`.

**Interfaces:**
- Consumes: `LauncherApps`, `UserManager`, danh sách app hiển thị (bỏ app ẩn), `ShortcutRepository.startShortcut`.
- Produces: `ShortcutSearchRepository { suspend fun snapshot(visibleApps: List<AppItem>): List<ShortcutEntry>; val available: Boolean }`; `ShortcutEntry(key: ComponentKey, id: String, label: String, appLabel: String)`; icon qua `loadIcon` (nạp khi hiển thị hàng).

- [ ] **Step 1: Chỉ mục** — theo cách Task 1 chốt (một truy vấn hoặc theo package); dựng ở IO khi mở lớp tìm kiếm (không ở mỗi phím), hạn chót thời gian, bỏ shortcut chưa bật/nhãn rỗng/pinned; giữ ở bộ nhớ, bỏ khi đóng lớp lâu hoặc package đổi.
- [ ] **Step 2: Nối vào `SearchIndex`** — chỉ khớp nhãn shortcut (không khớp nhãn app cha), từ 2 ký tự, ≤ 5 mục, ưu tiên thấp nhất; kết quả shortcut thêm vào khi chỉ mục sẵn sàng, không chặn app/Cài đặt.
- [ ] **Step 3: Hàng shortcut** — icon shortcut (nạp bất đồng bộ như app), dòng phụ = nhãn app cha; bấm → `startShortcut`; thất bại → Toast, giữ lớp.
- [ ] **Step 4: Không mặc định** — `available == false` thì không dựng chỉ mục, không thêm mục, không log lỗi lặp.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — (a) **mặc định**: gõ tên một shortcut thật (vd Camera "Chụp ảnh tự sướng" nếu có) → hàng shortcut, bấm mở đúng; (b) app ẩn: shortcut của nó không hiện; (c) **không mặc định**: cùng truy vấn, không có shortcut, app vẫn đúng, không Toast/crash; (d) đặt mặc định khi lớp đang mở rồi mở lại lớp: lần mở sau có shortcut; (e) gõ nhanh, lớp mở lại nhiều lần: không rò, không khựng; (f) hồi quy M8b.
- [ ] **Step 6: Commit** `feat: add app shortcuts to search`.

### Task 5: App gần đây và gợi ý khi ô trống

**Bắt buộc kiểm tay:** không; gộp vào bài kiểm cuối.
**Máy:** Live 4.

**Files:** Tạo `domain/model/RecentApp.kt`, `domain/repository/RecentAppsRepository.kt`, `data/search/RecentAppsRepositoryImpl.kt`, `res/layout/item_search_header.xml`; sửa `di/SearchModule.kt`, `data/apps/AppRepositoryImpl.kt`, `SearchViewModel.kt`, `SearchResultAdapter.kt`, `strings.xml`.

**Interfaces:**
- Produces: `RecentAppsRepository { val entries: Flow<List<RecentApp>>; fun record(key: ComponentKey); suspend fun clear() }`; `RecentApp(key, launchCount, lastLaunchedMillis)`. Lưu ở DataStore: một khóa chuỗi `search_recent_apps`, tối đa 30 mục, mỗi dòng `serial|package|class|count|lastMs`; đọc lỗi/định dạng lạ → bỏ dòng đó (không crash cả flow, như `SettingsRepositoryImpl.fromName`).

- [ ] **Step 1: Kho lịch sử** — đọc/ghi file `noBackupFilesDir` (AtomicFile, có Mutex, trạng thái giữ trong bộ nhớ); `record` ở `ApplicationScope`, không chặn Main; gộp theo key (tăng đếm, cập nhật thời điểm), cắt về 30 theo thời điểm.
- [ ] **Step 2: Ghi** — `AppRepositoryImpl.launch`: khi `startMainActivity` thành công và không phải package của launcher thì `record(key)`. Mở thất bại không ghi.
- [ ] **Step 3: Ô trống** — `SearchViewModel`: truy vấn rỗng, không `showAll`, có lịch sử → `status = RESULTS` với `Header(RECENT)` + ≤ 5 app, `Header(FREQUENT)` + ≤ 4 app (đếm ≥ 3, loại app đã ở Gần đây); lọc giao với `visibleApps` (loại app ẩn, app đã gỡ). Không có gì để hiện thì quay về `IDLE` của M8b.
- [ ] **Step 4: Hàng tiêu đề** — `Header` không bấm được, không nhận focus TalkBack như một mục thao tác (dùng `heading`).
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — (a) cài mới/xóa dữ liệu app: ô trống hiện dòng gợi ý cũ; (b) mở 3 app từ lưới, từ dock, từ kết quả tìm: mở lớp tìm kiếm thấy cả 3 theo thứ tự mới nhất trước; (c) mở một app 3 lần: có ở Gợi ý (nếu không còn trong 5 gần nhất); (d) ẩn app (edit mode) → biến mất khỏi Gần đây/Gợi ý; gỡ app (`adb shell pm uninstall`) → biến mất; (e) mở app thất bại (vừa gỡ) không ghi; (f) mở từ App Library (showAll): vẫn liệt kê a-z, không có Gần đây; (g) `force-stop` rồi mở: lịch sử còn; (h) gõ chữ rồi xóa hết: về lại Gần đây/Gợi ý; (i) hồi quy M8b.
- [ ] **Step 6: Commit** `feat: show recent and frequent apps when search is empty`.

### Task 6: Khối Cài đặt "Tìm kiếm" + xóa lịch sử (cắt được)

**Bắt buộc kiểm tay:** không; gộp vào bài kiểm cuối.
**Máy:** Live 4.

**Files:** Tạo `domain/model/SearchPreferences.kt`, `domain/repository/SearchPreferencesRepository.kt`, `data/search/SearchPreferencesRepositoryImpl.kt`, `ui/settings/SearchSettingsBlock.kt`, `SearchSettingsViewModel.kt`; sửa `di/SearchModule.kt`, `SearchViewModel.kt`, `SettingsActivity.kt` (thêm), `SettingsRow.kt` (thêm hằng), `strings.xml`.

**Interfaces:**
- Produces: `SearchPreferencesRepository { val preferences: Flow<SearchPreferences>; suspend fun setSystemSettings(Boolean); setShortcuts(Boolean); setSuggestions(Boolean) }`; ba khóa boolean trong `data/search/`, mặc định `true`. `SearchSettingsBlock.rows(state): List<SettingsRow>` và `SearchSettingsBlock.onClick/onSwitch(...)` đứng riêng khỏi `SettingsViewModel`.
- Hàng: `Header "Tìm kiếm"`; `Switch "Cài đặt hệ thống"`; `Switch "Lối tắt ứng dụng"` (chữ phụ cảnh báo khi không là launcher mặc định; bấm dòng này vẫn bật/tắt được, chỉ là không có tác dụng đến khi là mặc định); `Switch "Gợi ý và app gần đây"`; `Value "Xóa lịch sử mở app"` (hộp xác nhận → `clear()`).

- [ ] **Step 1: Kho tùy chọn** + `SearchViewModel` đọc: tắt nguồn nào thì nguồn đó không vào `SearchIndex`/ô trống. Tắt "Gợi ý và app gần đây" thì **dừng ghi và xóa** lịch sử (D9, hiện trong hộp thoại hoặc chữ phụ để người dùng biết).
- [ ] **Step 2: Khối UI** — dựng hàng ở `SearchSettingsBlock`, nối vào **cuối** `buildRows` của `SettingsActivity` bằng một dòng, thêm nhánh xử lý bấm/công tắc bằng một khối `when` riêng.
- [ ] **Step 3: Xóa lịch sử** — hộp xác nhận (tái dùng kiểu hộp thoại có sẵn của Cài đặt), sau khi xóa mở ô trống chỉ còn dòng gợi ý M8b.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — (a) cuối Cài đặt có khối "Tìm kiếm" bốn hàng, cuộn được; (b) tắt "Cài đặt hệ thống": `wifi` không còn mục Cài đặt; bật lại: có; (c) tắt "Lối tắt": không shortcut dù là mặc định; (d) tắt "Gợi ý và app gần đây": ô trống về dòng gợi ý cũ, lịch sử đã xóa, mở app không ghi mới; (e) "Xóa lịch sử": hỏi, xóa; (f) đổi sáng/tối và xoay màn khi đang ở Cài đặt: công tắc đúng giá trị; (g) bỏ thử khối này ra khỏi `SettingsActivity` (không commit): còn lại nguyên màn cũ, build xanh (chứng minh cô lập).
- [ ] **Step 5: Commit** `feat: add search options to settings`.

### Task 7: Đóng M8d — rà Review Focus, kiểm cả tính năng, đề xuất tài liệu

- [ ] **Step 1: Rà Review Focus** — mỗi dòng có bằng chứng (kịch bản, máy, kết quả) như bảng của M8a/M8c; ca chỉ được bảo vệ bằng code ghi rõ.
- [ ] **Step 2: Bài kiểm cả tính năng** (điều phối gộp, bên dưới) trên Live 4, thêm Oppo cho ca Wi-Fi/Instabridge và 344 app.
- [x] **Step 3: Knowledgement** `docs/knowledgements/M8d-search-extras.md` (viết dần từ Task 1): luồng Mermaid (tìm kiếm qua các nguồn; ghi/đọc lịch sử; rẽ nhánh launcher mặc định), số đo Task 1 và 2.
- [ ] **Step 4: Điền "Đề xuất cập nhật tài liệu chung"**, rebase lên `main`, build xanh, chuyển PR sang Ready for review. Commit `docs: close M8d plan with measured results`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Live 4 (chính); Oppo A3x cho Wi-Fi/Instabridge, Night display vắng, 344 app gõ nhanh. **Chuẩn bị:** cài bản debug của nhánh; đặt launcher mặc định tạm để kiểm shortcut rồi trả; mở vài app từ lưới/dock/tìm kiếm để có lịch sử.

1. Ô trống: Gần đây/Gợi ý đúng thứ tự; ẩn/gỡ app biến mất.
2. Gõ `wifi`, `man hinh`, `bluetooth`: Cài đặt đúng, bấm mở đúng màn, Back về launcher.
3. Gõ tên shortcut khi là mặc định: có; không mặc định: không, không lỗi.
4. Gõ `cam` / `may anh` / `dien thoai` / `đ` / `a`: hành vi M8b không đổi.
5. Cài đặt → khối Tìm kiếm: bật/tắt từng nguồn, xóa lịch sử.
6. Hồi quy: vuốt xuống mở lớp, vuốt ngang, mép trên, kéo-thả, menu nhấn giữ, đóng bằng Back/chạm nền/Home, đổi sáng-tối khi lớp mở, mở từ App Library.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** (điền khi đóng)

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-05 | Task 2-6 | Code xong: SearchResult/SearchIndex, Cài đặt hệ thống (25 mục), Gần đây/Gợi ý, shortcut (đọc qua `ShortcutRepository` của M7, thêm `canReadShortcuts()`), khối Tìm kiếm trong Cài đặt | 643cf7b..HEAD | Chờ số đo Task 2 (APK có M8dTiming đã đưa điều phối); không ghi bỏ qua package launcher trong lịch sử (D3 lệch nhẹ) |
| 2026-11-05 | Sửa review | 6 điểm của review độc lập đều xác nhận và sửa (xem mục Sửa theo review); lịch sử chuyển sang file noBackup | (commit `fix:` sau bf567a4) | Không push; chủ dự án đẩy |
| 2026-11-05 | Task 1 | Probe resolve 32 action trên hai máy (Oppo log cắt), xem mục Kết quả Task 1 | (không mã) | Không cần `<queries>` mới |
| 2026-11-05 | Plan | Viết plan, đo thử `query-activities` 41 action trên Live 4 + Oppo A3x, `dumpsys usagestats` | `docs: add M8d plan` | Chờ duyệt plan |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Hồi quy M8b | Live 4: `may` ra Máy ảnh trước, `camera` không ra (máy không có app tên đó). Code: giữ luật hạng và thứ tự của M8b. **Chưa chạy** lượt gõ đầy đủ | PASS một phần |
| 2 | Mục Cài đặt | Đo Task 1: Live 4 32/32 action resolve, Oppo `WIFI_SETTINGS` có app ngoài; Live 4: `wifi`/`nfc`/`bluetooth` ra mục, Wi-Fi mở `WifiSettings2Activity`, Back về; `dev` không ra. Oppo chưa kiểm mở thật | PASS một phần (Live 4) |
| 3 | Shortcut | Live 4 `bf567a4`: chỉ mục 85 mục/83 app/197 ms; `chrome` ra "Thẻ mới" và "Thẻ ẩn danh mới", bấm mở Chrome. Chưa chạy: chuyển lại không mặc định. Oppo không đo | PASS một phần (Live 4) |
| 4 | Lịch sử | Live 4: mở Máy tính thì nó vào "Gần đây". Code: file noBackup (không vào backup), ghi chỉ khi thành công, giao với app hiển thị. **Chưa chạy:** Gợi ý sau 3 lần mở, ẩn app, xóa qua hộp thoại | PASS một phần |
| 5 | Hiệu năng | Oppo A3x đã đo (xem Kết quả đo Task 2): nhanh hơn M8b ~14x ở p50 nhưng p95 7-9 ms, đỉnh 22-43 ms, **trượt mục tiêu <= 4 ms**; janky 27-30% so với 43-45% của `main`. Live 4 chưa đo | Trượt mục tiêu p95; hồi quy so với M8b: không |
| 6 | Vòng đời | Code: trạng thái nằm ở `SearchViewModel` như M8b; không giữ bản sao trong view. **Chưa kiểm tay** `recreate()` | (điền sau) |
| 7 | Cô lập khối Cài đặt | Live 4: khối nằm cạnh công tắc kính, định tuyến đúng, tắt gợi ý hỏi trước, Hủy giữ trạng thái. Code: `SearchSettingsBlock`/`SearchSettingsViewModel` riêng. **Chưa thử gỡ khối** | PASS một phần |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#9 → danh mục Cài đặt hệ thống, shortcut, gợi ý/app gần đây chuyển từ "Còn lại" sang "Đã có (M8d)"; dòng TT#20 "cấu hình tìm kiếm" → đã có khối "Tìm kiếm" (4 hàng); xóa dòng "mở rộng của M8b" ở mục việc để sau.
- `project_structure.md`: dòng M8d ✅; cây `data/search/`, `domain/search/`, `ui/search/` thêm tệp mới; ghi `di/SearchModule.kt`.
- `room-schema.md`: không đổi (lịch sử nằm ở file noBackup, không phải Room).
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement (số đo `<queries>` theo action; app ngoài đăng ký `ACTION_*_SETTINGS`; `getShortcuts` toàn bộ; chi phí chỉ mục).
