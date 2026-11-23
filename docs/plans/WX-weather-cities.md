# WX — Thời tiết nhiều địa điểm (TT#14 phần 2; màn 1.4 "Địa điểm theo dõi" và 1.5 Vị trí) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không cần: không đụng kính). Lệnh viết cho CMD/PowerShell. Máy thật dùng chung với việc khác của chủ dự án: xác nhận máy rảnh trước mỗi lần cài hoặc chạy script. Agent không tự cài APK. **Khóa OWM không bao giờ được in ra log, plan, commit, PR, báo cáo.**

**Nhánh:** `feature/wx-weather-cities` · **Worktree:** `E:\SourceTree\IosLauncher-wt\WX-weather-cities` · **PR:** #55 (draft) · **Bắt đầu:** 2026-11-13 · **Plan duyệt:** 2026-11-13 (điều phối; 4 câu hỏi cho chủ dự án đã trả lời 2026-11-13: chấp nhận đúng đề xuất)
**Trạng thái:** Chờ review toàn nhánh (Task 1–6 đã qua kiểm Live 4 của điều phối, Task 7 là rà cuối + tài liệu; chờ bài kiểm tay của chủ dự án)
**Khóa:** **không khóa schema Room** (xem D1: dùng lưu trữ ngoài Room) · không cần Oppo · không đụng file độc quyền nào (xem "Vùng file"; `HomeActivity` KHÔNG sửa, có đường lùi 1 dòng nếu điều phối muốn)
**Mức rủi ro:** **cao ở Task 5** (lớp widget: `IconCellView`, `IconGridView`, `HomePagerAdapter`, `HomePagerController`; đổi chữ ký callback bấm widget). Các Task còn lại trung bình/thấp. Model/effort đề xuất: Sonnet 5.5 / **high** (chạm lớp widget và chạm vào cử chỉ bấm widget; mục 2.1 của workflow).

**Goal:** Người dùng theo dõi **tối đa 3 địa điểm** (kể cả "Vị trí hiện tại"). Danh sách nằm ở Cài đặt → Thời tiết (thêm bằng "+ Thêm địa điểm" tìm thành phố qua OWM Geocoding, xóa). Mỗi widget thời tiết chọn **một** địa điểm trong danh sách (mặc định "Vị trí hiện tại"). Cài đặt → Vị trí (1.5): chế độ **Tự động** (vị trí gần đúng như G3e, cần quyền) hoặc **Chọn thủ công** (chọn thành phố, không cần quyền); kết quả là địa điểm của mục "Vị trí hiện tại". Mỗi địa điểm làm mới **tối đa 2 giờ một lần** và **chỉ khi có widget đang hiện địa điểm đó**; đổi °C/°F vẫn không gọi mạng.
**Architecture:** (1) Domain: mô hình `WeatherPlaces` (chế độ, vị trí tự động, thành phố thủ công, tối đa 2 thành phố thêm), interface `WeatherPlacesRepository` (danh sách + chọn địa điểm cho từng widget), `WeatherRepository` đổi thành **theo từng địa điểm** (`state` là map `placeId → WeatherState`, `refreshIfStale(placeId)`). (2) Data: danh sách địa điểm + ánh xạ widget → địa điểm ở file riêng `weather_places.json` (`noBackupFilesDir`, `AtomicFile`, `org.json`, cùng tiền lệ G3e/LC); bộ nhớ đệm thời tiết `weather.json` nâng version 2 (một mục cho mỗi địa điểm, đọc được file version 1). Không Room, không DataStore mới ngoài khóa đơn vị của WU. (3) UI: renderer thời tiết đọc địa điểm của **chính widget** qua `itemId` (id hàng `launcher_items`, ổn định nhờ `AUTOINCREMENT`); bấm widget mở bảng Thời tiết, bảng nay là **bộ chọn địa điểm cho widget đó** (radio ≤ 3 dòng) + nút "Quản lý địa điểm" mở Cài đặt → Thời tiết; hai màn Cài đặt mới theo "Công thức thêm màn/hàng" của G2d.
**Tech Stack:** không dependency mới. Kotlin, Hilt, `HttpURLConnection` + `org.json` (G3e), `LocationManager` (G3e), Views/XML.
**Spec:** TT#14 trong `Launcher-Features.md` (phần "một vị trí" của G3e được mở rộng) + `docs/Figma-Draft.md` mục 3 (màn 1.4, 1.5), 5 (FD2, FD6) + `plans/G3e-weather-widget.md`, `plans/WU-weather-units.md` (và knowledgement của hai mốc) + `plans/G2d-settings-hub.md` (công thức thêm màn/hàng).

---

## Nguồn tài liệu đã đọc cho nhánh này (đọc tài liệu OpenWeatherMap, 2026-11-13)

| Việc | Điều đọc được | Nhãn |
|---|---|---|
| **Cảnh báo thời tiết xấu** (đã bỏ khỏi phạm vi) | Trang One Call 3.0 liệt kê "government weather alerts" từ "major national weather warning systems", và nói endpoint `data/3.0/onecall` "is included in the One Call API subscription only", gồm **1.000 lệnh/ngày miễn phí**, vượt thì tính tiền. Trang **không nói** có bắt thẻ thanh toán hay không, và **không nói** `/weather` hay `/forecast` (gói Free đang dùng) có trường cảnh báo hay không. Hệ quả: dữ liệu hiện có (G3e) **không có** cảnh báo; muốn có phải thêm gói One Call (đăng ký riêng), chưa kiểm thẻ | đọc tài liệu (https://openweathermap.org/api/one-call-3); "gói Free không có cảnh báo" là **suy luận chưa kiểm chứng** (trang không khẳng định) |
| Geocoding trực tiếp | `geo/1.0/direct?q=<tên>[,<mã bang>][,<mã nước>]&limit=` (`limit` tối đa 5, dùng để tách các nơi trùng tên); phản hồi có `name`, `local_names.<mã ngôn ngữ>` (có hay không tùy nơi), `lat`, `lon`, `country`, `state` (nếu có); mã nước theo ISO 3166. Trang ghi `http://`, app dùng `https://` (như G3e) | đọc tài liệu (https://openweathermap.org/api/geocoding-api) |
| Hạn mức gói Free | 60 lệnh/phút, 1.000.000 lệnh/tháng (G3e đã ghi, không đọc lại) | đọc tài liệu (G3e) |
| Số đo API đã có | `geo/1.0/direct` "Hà Nội"/"Ha Noi"/"Đà Nẵng" ra đúng (có `local_names.vi`); `/weather` 200 (500 byte); `/forecast?cnt=9` 200 (3,9 KB); `geo/1.0/reverse` 200; khóa sai → 401 | đo được (G3e, 2026-11-10) |

**Ngân sách lệnh gọi (tính từ chính sách mới, suy luận từ số liệu trên):** một lần làm mới một địa điểm = 2 lệnh (`/weather` + `/forecast`). Tối đa 3 địa điểm × 12 lần/ngày × 2 lệnh = **72 lệnh/ngày**, thấp hơn nhiều so với 60 lệnh/phút và 1.000.000/tháng. Tìm thành phố khi thêm địa điểm: 1 lệnh (2 nếu rơi vào đường dự phòng bỏ dấu), chỉ khi người dùng bấm Tìm, không tìm theo từng phím.

## Hiện trạng đọc từ code (2026-11-13, `main` @ a0c1be2)

| # | Chỗ | Hiện trạng | Hệ quả cho WX |
|---|---|---|---|
| 1 | `WeatherRepository` / `WeatherRepositoryImpl` | **Một** địa điểm (`place: StateFlow<GeoPlace?>`), một `WeatherState`, `refreshIfStale()` không tham số, `select(place)` gọi mạng ngay, `refreshNow()` (≥ 60 s). Chặn 30 phút, lỗi mạng 5 phút, `keyDead` khi 401. `Mutex` một lượt một lúc | Đổi sang theo từng địa điểm; chặn 2 giờ; **bỏ** `select`/`refreshNow` (chọn địa điểm không còn gọi mạng, xem D4) |
| 2 | `WeatherStore` (`weather.json`, version 1) | Một `place`, một `snapshot`, `attempt`, `result`; `noBackupFilesDir`; `AtomicFile`; sai version → coi như không có | Nâng version 2 (mục theo địa điểm), đọc được version 1 để người dùng nâng cấp không mất địa điểm |
| 3 | `WeatherWidgetHub` (`object`) | `state` một giá trị; `unit` (WU); `onVisibleTick()`; `sample()` | `state(placeId)`, `onVisible(placeId)`; vẫn không giữ `View` |
| 4 | `WeatherRenderer` (G3e/WU) | Đọc `WeatherWidgetHub.state` trong `prepare`, gọi `onVisibleTick()` (trừ bản mẫu); **không biết mình là widget nào** | Cần biết `itemId` của widget để tra địa điểm |
| 5 | `IconGridView.bindWidgetCell` → `IconCellView.bindWidget(kind, spanX, spanY, c, glass)` | `placed.itemId` có sẵn tại chỗ nhưng **không truyền** vào ô/renderer. Ô được tái dùng giữa loại item (bài học A1a) | Thêm tham số `itemId`; renderer cập nhật mỗi lần bind |
| 6 | Bấm widget (ngoài edit mode) | `IconGridView` `setOnClickListener { onWidgetClick(kind) }` → `HomePagerAdapter` → `HomePagerController` → `onWeatherClick()` (lambda do `HomeActivity` truyền: `weatherSheet.show()`). Trong edit mode: nuốt chạm. **Nhấn giữ widget ngoài edit mode = vào edit mode** (`onFolderLongPress`) | Nhấn giữ đã có nghĩa, nên không dùng nhấn giữ để chọn địa điểm (D2) |
| 7 | `WeatherSheetController` / `WeatherSheetViewModel` / `view_weather_sheet.xml` | Ô nhập tên, kết quả (≤ 5), "Dùng vị trí hiện tại", "Làm mới", trạng thái, ghi công. **Không hiện nhiệt độ nào** (WU đã đọc code xác nhận). `registerForActivityResult` quyền vị trí đăng ký ở `HomeActivity.onCreate` | Chuyển phần tìm thành phố và vị trí ra Cài đặt; bảng thành bộ chọn địa điểm của widget |
| 8 | `WeatherSettingsScreen` (WU) | Một hàng "Đơn vị nhiệt độ"; ViewModel riêng `WeatherSettingsViewModel`; WU ghi rõ WX thêm hàng vào đây, không sửa bảng đăng ký | Thêm hàng địa điểm (D6) |
| 9 | `SettingsScreens` (`SettingsScreen.kt`) | Ô `// 1.5 LOCATION (WX)` còn trống; `SettingsScreenId.LOCATION` và chữ `settings_hub_location` đã có sẵn ở trang chủ (mục "Vị trí" đang hiện mờ) | Thay **một dòng** |
| 10 | `SettingsRow.Value` | `title` là `@StringRes` → không hiện được tên thành phố động (RN phải thêm `AppName` cho việc tương tự) | Thêm tham số cuối `titleText: String? = null` (D6) |
| 11 | `CoarseLocationSourceImpl` | `getOnce()` (≤ 10 s, `NETWORK_PROVIDER`, rơi về `getLastKnownLocation`), làm tròn 2 số thập phân; đo được trên Oppo; **Live 4 (API 30, đường `getCurrentLocation`) chưa kiểm** | Tái dùng nguyên; thêm kiểm Live 4 vào bài kiểm |
| 12 | `AndroidManifest.xml` | Đã có `INTERNET`, `ACCESS_COARSE_LOCATION` | Không sửa manifest |
| 13 | `launcher_items.id` | `INTEGER PRIMARY KEY AUTOINCREMENT` (đọc `app/schemas/.../1.json`) → id không bao giờ dùng lại | Khóa an toàn cho ánh xạ widget → địa điểm ngoài Room (D1) |
| 14 | `LayoutRepositoryImpl.assemble` | `PlacedWidget(e.id, position, kind)` chỉ có id, vị trí, loại | Không đổi (không lưu cấu hình trong Room) |
| 15 | `WeatherPolicy` | `REFRESH_INTERVAL_MS = 30 phút`, `RETRY_AFTER_NETWORK_ERROR_MS = 5 phút`, `MANUAL_REFRESH_MIN_MS = 60 s`, `STALE_AFTER_MS = 2 giờ` | Đổi theo chủ dự án (2 giờ); `STALE_AFTER` phải lớn hơn chu kỳ (D5) |

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật)

1. **Mỗi widget cần một cấu hình riêng** (địa điểm), trong khi G3e chốt D1 là "không có chỗ lưu cấu hình riêng từng widget" và đã ghi sẵn "xét lại khi làm nhiều vị trí" — đây chính là lúc xét lại.
2. **Lớp widget và cử chỉ trên widget rất nhạy** (bài học G2c: ô được tái dùng giữa loại item, kính tại chỗ, kéo-thả, nhấn giữ = edit mode). Mỗi chỗ phải đụng vào đều phải nhỏ và có kịch bản hồi quy.
3. **File độc quyền và khóa schema** (workflow 5.2, 5.3): đổi Room kéo theo `LauncherItemDao`/`LayoutReconciler` (độc quyền), migration, `app/schemas/2.json`, kịch bản cài-đè, và rủi ro hạ phiên bản làm crash khi chuyển nhánh (workflow mục 6).
4. **Giới hạn lệnh gọi do chủ dự án chốt:** mỗi địa điểm ≤ 1 lần/2 giờ, **chỉ địa điểm có widget đang hiện** (khóa nằm trong APK, C15 hoãn; FD6 "giới hạn số lượt gọi").
5. **Tọa độ là dữ liệu cá nhân:** danh sách địa điểm phải ở ngoài sao lưu đám mây, không vào log (G3e D3).
6. **Thông báo thời tiết sẽ làm ở nhánh sau và tái dùng dữ liệu này:** chính sách chặn tần suất phải nằm ở repository (không ở widget) để một worker sau này gọi cùng hàm mà vẫn bị chặn như nhau.
7. **1.5 phải khớp với "Vị trí hiện tại" trong danh sách:** Tự động cần quyền, Thủ công thì không; danh sách luôn phải có một địa điểm mặc định cho widget.

### D1. Lưu "widget nào hiển thị địa điểm nào" ở đâu (câu hỏi khóa schema)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Dùng lại cột `title` của hàng `itemType = 4` để chứa mã địa điểm | Không đổi `version`, không migration; `PlacedWidget` thêm trường; dọn tự động khi xóa hàng | Cấu hình đi cùng hàng (xóa, sao lưu đồng bộ với bố cục) | Phải **đặt nghĩa mới** cho một cột của hàng loại 4 (tinh thần mục 5.3 "đặt nghĩa" nên cần khóa + sửa `room-schema.md`); cần câu `UPDATE` mới trong `LauncherItemDao` và một đường ghi trong `LayoutReconciler`/`LayoutRepository` (cả hai là **file độc quyền**, ghi dưới `mutex` của reconciler); mọi chỗ dựng lại `PlacedWidget` phải giữ trường (kéo, reflow, đổi cỡ lưới); chưa có ai kiểm đường reflow giữ được `title` | Không |
| B. Cột mới `widgetConfig TEXT` + `Migration 1→2` | Sạch về mô hình | Chỗ đặt cấu hình đúng nghĩa; mở rộng được (nhiều tùy chọn cho mỗi widget) | **Cần khóa schema**, migration, `app/schemas/2.json`, kịch bản cài bản cũ → tạo dữ liệu → cài bản mới (bắt buộc kiểm tay, workflow 4.2), cùng các file độc quyền như A; cài bản nhánh khác (schema 1) lên sau đó thì crash (workflow mục 6); chỉ để lưu một chuỗi ngắn | Không (xét lại khi một widget có ≥ 3 tùy chọn, hoặc có widget loại khác cần cấu hình) |
| **C. Ngoài Room: ánh xạ `itemId → placeId` trong `weather_places.json`** | Hub/renderer tra theo `itemId`; xóa widget thì `RemoveWidgetUseCase` gọi `forgetWidget(itemId)` | **Không khóa schema, không migration, không file độc quyền**; cùng chỗ với danh sách địa điểm (cùng nơi dọn khi xóa địa điểm); `itemId` không bao giờ dùng lại nhờ `AUTOINCREMENT` (đọc code), nên mục mồ côi vô hại | Dọn mồ côi không tự động hoàn toàn (widget bị xóa gián tiếp, ví dụ trang bị dồn, để lại một dòng ~20 byte; chấp nhận, lọc khi nạp nếu quá 64 mục); sao lưu/khôi phục máy: Room có thể được khôi phục còn `no_backup` thì không → widget về "Vị trí hiện tại" (an toàn, không crash); renderer phải tra theo `itemId` ở mỗi `prepare` | **Chọn** |

**Vì sao C:** lựa chọn địa điểm là *tùy chọn của người dùng về dữ liệu thời tiết*, cùng loại với danh sách địa điểm và đơn vị (đều nằm ngoài Room); cách này tránh toàn bộ chi phí và rủi ro của A/B (độc quyền, migration, chuyển nhánh). Nhược điểm "mồ côi" có chặn trên nhỏ và không gây sai hành vi (id không tái dùng). **Câu trả lời cho brief:** **không xin khóa schema Room.** **Đánh đổi chấp nhận:** cấu hình widget không đi theo bản sao lưu bố cục; nếu sau này nhiều loại widget cần cấu hình thì xét lại phương án B một lần, cho tất cả.

**Đã kiểm (yêu cầu của điều phối, 2026-11-13): không đường nào xóa rồi tạo lại hàng widget với id mới.** Đọc `data/layout/LayoutReconciler.kt` (mọi `insertAll`, `deleteByIds`, `.copy(`), `LauncherItemDao` (`@Update` theo khóa chính; `updateAll` giữ nguyên `id`) và `LayoutRepositoryImpl`. Nhãn: **đọc code**, chưa đo trên máy.

| Đường | Cách ghi hàng widget (đọc code) | Id đổi? |
|---|---|---|
| Đổi cỡ lưới 4×6 ↔ 4×5 (`changeGridPreset` → `reflowInTransaction`, G2d "Áp dụng") | `itemDao.updateAll(byId.getValue(id).copy(pageId, gridX, gridY))` và `copy(spanX, spanY)` khi kẹp cỡ; chỉ xóa **trang** thừa (`pageDao.deleteByIds(surplus)`) sau khi mọi item đã dời xuống trang thấp hơn | Không |
| `ensureFitsGrid` lúc khởi động (FX3 bảo hiểm) | Gọi lại đúng `reflowInTransaction` ở trên | Không |
| FX3/FX1 sửa bố cục khi cập nhật app (`catchUp`, `placeNewInTransaction`, `repointOrphans`/`renameComponent`) | `renameComponent` chỉ `UPDATE ... WHERE itemType = 0` (app); `catchUp` chỉ đưa vào `doomed` hàng `itemType == APP`; `placeNewInTransaction` chỉ `insertAll` app mới | Không (không chạm widget) |
| Gỡ app thật (`removePackages`) | `doomed` lọc `itemType == APP`; widget chỉ bị `updateAll` khi dồn ô cùng trang (`deleteAndCompact`, `GridRemovePlanner` có biết widget) | Không |
| Hồ sơ người dùng bị bỏ (`removeUser`) | `doomed = rows.filter { userSerial == userSerial }`, nhưng `userSerial == 0L` thì thoát ngay, mà hàng widget luôn `userSerial = 0` (không truyền khi tạo, `LauncherItemEntity.userSerial` mặc định 0) | Không |
| Xóa trang / gộp trang | Chỉ `deleteTrailingEmptyPages` và `reflow`: xóa trang **không còn item** (`usedPageIds`), luôn giữ ≥ 1 trang; item trên trang bị gộp đã được `updateAll` sang trang khác trước | Không |
| Kéo widget sang trang khác / trang mới / thả đè icon (`moveItem`) | `GridInsertPlanner` → `itemDao.updateAll(byId.getValue(id).copy(container, pageId, gridX, gridY))`; widget bị từ chối thì không ghi gì | Không |
| Gộp vào thư mục (`createFolder`, `addToFolder`) | `copy(container = folderId, ...)` bằng `updateAll`; vả lại `IconGridView` không bao giờ cho widget làm nguồn/đích gộp (đọc code `isWidget`) | Không |
| Ẩn app (`setHidden`) | Chỉ `deleteByIds` hàng `itemType == APP` | Không |
| Seed lần đầu (`seedIfEmpty`, dòng ~101–114) | Chỉ chạy khi `itemDao.count() == 0` (không có widget nào để mất), chỉ `insertAll` app; không tạo widget | Không áp dụng |
| Hoàn tác (undo) | Không có tính năng hoàn tác trong dự án (grep `undo` không có kết quả) | Không áp dụng |
| Thêm widget (`addWidget`, `addWidgetAt`) | **Hai nơi duy nhất `insertAll` hàng `ITEM_TYPE_BUILTIN_WIDGET`**: đều là widget **mới** do người dùng thêm (id mới là đúng); widget mới thả có `itemId` tạm ở UI cho tới khi DB trả id thật, và chưa có mục ánh xạ nào nên mặc định "Vị trí hiện tại" | Chỉ widget mới |
| Xóa widget (`removeItem`) | `deleteByIds` đúng id đó; `RemoveWidgetUseCase` gọi thêm `forgetWidget(itemId)` (Task 5) | Hàng mất, mục ánh xạ bị dọn |
| Hạ cấp/lỗi DB | Không `fallbackToDestructiveMigration` (comment ở `LauncherDatabase`, `DatabaseModule`); xóa dữ liệu app xóa cả `no_backup/` cùng lúc | Không áp dụng |

Kết luận: ánh xạ `itemId → placeId` **không cần đi theo id mới ở đường nào**; D1 giữ nguyên. Lưu ý duy nhất: `sqlite_sequence` (AUTOINCREMENT) bị xóa chỉ khi bảng bị tạo lại, điều chỉ xảy ra khi xóa dữ liệu app (khi đó `weather_places.json` cũng mất).

### D2. Người dùng chọn địa điểm cho widget bằng cách nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Bấm widget (ngoài edit mode) → bảng Thời tiết hiện radio các địa điểm cho widget đó** | Tận dụng đường bấm đã có (G3e); bảng chuyển từ "gõ tên thành phố" sang "chọn trong danh sách" | **Không thêm cử chỉ mới**; đường bấm → `onWeatherClick` đã có và đã kiểm; dễ khám phá (bấm widget thời tiết là hành vi tự nhiên); chọn xong thấy ngay | Bấm không còn "mở app thời tiết" (G3e cũng không mở app, nên không mất gì); `itemId` phải đi cùng lần bấm (đổi chữ ký 3 lớp, xem Task 5) | **Chọn** |
| B. Nhấn giữ widget → menu chọn địa điểm | Giống menu icon | Giống iOS ("Edit Widget") | **Nhấn giữ widget ngoài edit mode đã = vào edit mode** (đọc code `IconGridView`, `onFolderLongPress`): đổi nghĩa một cử chỉ cốt lõi, đụng `HomeTouchRouter`/`DragHelper` (độc quyền), phải kiểm lại kéo-thả và edit mode; rủi ro cao nhất, lợi ích thấp | Không |
| C. Chọn ngay lúc thêm widget (bảng "Tiện ích") | Thêm bước chọn địa điểm trước khi thả | Chọn từ đầu | `WidgetPickerController` (344 dòng) có luồng kéo thẻ → thả (G2c Task 13) với phiên kéo chưa có hàng DB; thêm bước chọn làm phức tạp đúng chỗ đã dễ vỡ; vẫn cần cách *đổi* địa điểm sau này, tức là cần A | Không (xét sau nếu cần) |
| D. Trong edit mode: bấm widget → chọn | Đúng kiểu iOS (jiggle) | Gần với hành vi quen thuộc | Phải đổi nghĩa "nuốt chạm" trong edit mode (hiện cố ý nuốt để không thoát edit mode); người dùng phải vào edit mode chỉ để đổi địa điểm | Không (có thể làm thêm sau, dùng cùng bảng) |
| E. Chọn trong Cài đặt (danh sách widget) | Màn riêng liệt kê widget đang có | Không đụng Home | Người dùng không phân biệt được "widget #3"; cần đọc bố cục từ Settings | Không |

**Vì sao A:** không có cử chỉ mới, ít đụng lớp nhạy nhất, và là cách ít bước nhất (một chạm → chọn). **Đánh đổi:** chọn địa điểm nằm sau một lần bấm vào widget chứ không ở edit mode; chủ dự án có thể muốn thêm D sau (cùng bảng, thêm một lời gọi). **Câu hỏi 3.**

### D3. "Vị trí hiện tại" khớp với danh sách và 1.5 thế nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Mục "Vị trí hiện tại" luôn có (mục đầu, không xóa được); nguồn của nó do 1.5 quyết định: Tự động → vị trí gần đúng lấy được; Thủ công → thành phố người dùng chọn. Thêm tối đa 2 thành phố nữa (tổng 3)** | Giữ riêng `autoPlace` và `manualPlace`; "hiện tại" = theo chế độ; đổi chế độ không mất cái kia | Widget luôn có địa điểm mặc định hợp lệ; khớp đúng hai nút của 1.5; người không muốn cấp quyền vẫn dùng đủ 3 thành phố (Thủ công: mục đầu là một thành phố); đúng "kể cả vị trí hiện tại ≤ 3" | Ở chế độ Thủ công, tên "Vị trí hiện tại" hơi lệch nghĩa → hiển thị bằng tên thành phố, nhãn phụ "(chọn thủ công)" | **Chọn** |
| B. "Tự động" là một mục xóa được trong danh sách như thành phố | Danh sách tự do 3 mục | Linh hoạt | Có thể không còn mục nào thành "mặc định" của widget; 1.5 phải sửa danh sách; ba trạng thái rỗng/thiếu quyền khó xử lý | Không |

### D4. Khi nào gọi mạng

Chốt của chủ dự án: mỗi địa điểm ≤ 1 lần/2 giờ, chỉ địa điểm có widget đang hiện. Thiết kế:

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Do widget kéo: mỗi `prepare()` của renderer gọi `refreshIfStale(placeIdCủaWidgetĐó)`; repository so giờ của từng địa điểm** | Giữ nguyên cơ chế G3e (`WidgetTicker` + chặn lưu trong file). Thêm địa điểm / chọn địa điểm **không** gọi mạng; địa điểm chỉ được tải khi một widget bắt đầu hiện nó | Đúng chữ "chỉ khi có widget hiện"; không `Handler`/WorkManager; địa điểm thêm vào danh sách mà chưa widget nào dùng thì 0 lệnh | Địa điểm vừa được gán cho widget phải chờ tới lần `tickNow` kế để tải (gần như ngay: gán xong gọi `WidgetTicker.tickNow()`); widget nằm ở trang chưa được RecyclerView dựng thì chưa kích hoạt tới khi vuốt tới (có cache hiện ngay) | **Chọn** |
| B. Vòng lặp theo danh sách địa điểm (mỗi 2 giờ, mọi địa điểm) | Lập lịch theo danh sách | Dữ liệu luôn mới | Gọi cả địa điểm không ai nhìn (trái chốt), cần nguồn nhịp thứ hai | Không |

**Chính sách (hằng ở `WeatherPolicy`, CHỈNH TAY):** thành công → lần kế sau ≥ **2 giờ**; 429 → 2 giờ; lỗi mạng/máy chủ/parse → thử lại sau ≥ **15 phút** (G3e là 5 phút; nâng lên để không trái tinh thần "tối đa mỗi 2 giờ" quá nhiều, nhưng vẫn để người dùng khỏi chờ cả 2 giờ sau một lần mất mạng; **Câu hỏi 1**); 401 → không thử lại (tới khi khởi động lại tiến trình); giờ máy lùi → coi hết hạn. **Bỏ nút "Làm mới"** (hệ quả trực tiếp của "tối đa 2 giờ"); bảng ghi "Cập nhật lúc HH:mm". `STALE_AFTER` đổi từ 2 giờ lên **4 giờ** (nếu để 2 giờ thì dữ liệu bình thường cũng bị đánh dấu cũ đúng lúc đến hạn làm mới). Một lượt làm mới một lúc cho toàn app (giữ `Mutex`), nhiều widget cùng địa điểm dùng chung kết quả. Một mục bộ nhớ đệm chỉ hợp lệ khi tọa độ của nó trùng địa điểm đang được tra (đổi vị trí tự động → mục cũ bị bỏ, tải lại).

**Thông báo sau này tái dùng được vì:** `WeatherPlacesRepository` + `WeatherRepository.refreshIfStale(placeId)` + `states` không phụ thuộc UI; một worker chỉ cần gọi cùng hàm (vẫn bị chặn 2 giờ); `WeatherSnapshot` có sẵn nhiệt độ hiện tại, 4 mốc 3 giờ, cao/thấp 24 giờ. Chưa đủ cho "dự báo ngày mai 21:00": `/forecast` đang lấy `cnt=9` (24 giờ); tăng `cnt` không tăng số lệnh (cùng một lệnh GET), nhưng đó là việc của nhánh thông báo (suy luận từ tài liệu G3e, chưa kiểm số phần tử). **WX không xây gì cho thông báo.**

### D5. Ánh xạ mã địa điểm

`placeId`: hằng `"current"` cho "Vị trí hiện tại"; thành phố thêm có `placeId` = `"<lat>,<lon>"` (làm tròn 2 số thập phân như G3e). Cách này tự chặn trùng (thêm lần hai cùng thành phố → báo "đã có"), ổn định qua nhiều phiên, không cần sinh id. Widget trỏ vào `placeId` đã bị xóa → rơi về `"current"` (và mục ánh xạ bị dọn khi xóa địa điểm).

### D6. Cách hiện danh sách trong Cài đặt (FD2: giữ kiểu hàng hiện tại)

Màn "Thời tiết" (1.4): nhóm "Địa điểm theo dõi": hàng "Vị trí hiện tại" (giá trị = tên, bấm mở 1.5), tối đa 2 hàng thành phố (bấm → hộp thoại "Xóa <tên>?"), hàng "+ Thêm địa điểm" (mờ + "Đã đủ 3 địa điểm" khi đầy); hàng "Đơn vị nhiệt độ" của WU giữ nguyên. Hàng có tên động cần `SettingsRow.Value` nhận chữ trực tiếp: thêm tham số cuối `titleText: String? = null` (nhánh khác không bị ảnh hưởng). **Không** dựng thẻ "hiện tại + theo giờ" của bản nháp (FD2; chủ dự án chốt chỉ làm địa điểm; xem "Ngoài phạm vi").

### Đánh đổi chấp nhận

- Cấu hình widget ở ngoài Room: không theo sao lưu bố cục, có thể để lại mục mồ côi nhỏ.
- Không còn "Làm mới" thủ công; dữ liệu có thể cũ tới 2 giờ khi mở; lỗi mạng thử lại sau 15 phút.
- Chọn địa điểm cho widget qua một lần bấm vào widget (không ở edit mode, không nhấn giữ).
- Chế độ Tự động chỉ dùng vị trí gần đúng (không GPS thật), lấy khi người dùng bấm "Cập nhật vị trí" và (nếu chủ dự án đồng ý, Câu hỏi 2) im lặng một lần mỗi chu kỳ làm mới khi widget đang hiện và đã được cấp quyền.
- Tối đa 3 địa điểm (cứng ở domain); không sắp xếp lại, không đổi tên địa điểm.

### Rủi ro chính

1. **Lớp widget (Task 5):** đổi chữ ký `bindWidget`/`onWidgetClick` qua ba lớp; ô tái dùng giữa các loại item; widget mới thả có `itemId` tạm. Giảm rủi ro: chỉ thêm tham số, renderer cập nhật `itemId` ở mỗi lần bind, kịch bản hồi quy nêu rõ kéo/xóa/đổi cỡ lưới/xoay màn hình.
2. **Lỗi hóa 2 giờ:** người thử không thể chờ 2 giờ → kịch bản kiểm dùng chỉnh `attempt` trong `weather.json` bản debug qua `run-as` (xem Task 2).
3. **Vị trí trên Live 4 (API 30) chưa từng kiểm** (G3e chỉ kiểm Oppo): Task 4 kiểm; đường lùi là chế độ Thủ công.
4. **Mất địa điểm khi nâng cấp từ G3e:** Task 1 đọc file version 1 và kiểm bằng cài đè.
5. **Rò khóa/tọa độ qua log:** quy tắc G3e giữ nguyên (không log URL, khóa, tọa độ); rà bằng `findstr`/`Select-String` ở mỗi Task. Log tạm đo số lệnh gọi chỉ ghi chỉ số địa điểm và mã kết quả, commit rồi revert như tiền lệ RN.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Phạm vi = **chỉ địa điểm**; thông báo hằng ngày/ngày mai là nhánh riêng sau; cảnh báo thời tiết xấu **bỏ** | Chủ dự án, 2026-11-13. Thiết kế dữ liệu để tái dùng (D4), không xây gì cho thông báo |
| 2 | Tối đa **3 địa điểm** kể cả vị trí hiện tại | Chủ dự án, 2026-11-13 |
| 3 | Mỗi địa điểm làm mới ≤ 1 lần/2 giờ, chỉ địa điểm có widget đang hiện; đổi đơn vị vẫn không gọi mạng | Chủ dự án, 2026-11-13 (+ WU) |
| 4 | Danh sách ở Cài đặt → Thời tiết ("+ Thêm địa điểm"); mỗi widget chọn một địa điểm, mặc định vị trí hiện tại | Chủ dự án, 2026-11-13; cách chọn là D2 (agent đề xuất, chờ duyệt) |
| 5 | Tìm thành phố bằng OWM Geocoding với khóa hiện có (không in/log/commit khóa) | Chủ dự án, 2026-11-13 |
| 6 | 1.5 Vị trí: Tự động (vị trí gần đúng như G3e) hoặc Thủ công (không quyền); khớp "Vị trí hiện tại" theo D3 | Chủ dự án, 2026-11-13; D3 là đề xuất của agent |
| 7 | D1: lưu ánh xạ widget → địa điểm **ngoài Room** (`weather_places.json`); **không khóa schema** | Agent đề xuất, chờ duyệt |
| 8 | D4: làm mới do widget kéo; lỗi mạng thử lại sau 15 phút; bỏ nút "Làm mới"; `STALE_AFTER` = 4 giờ | Agent đề xuất, chờ duyệt (Câu hỏi 1) |
| 9 | Giữ kiểu hàng Cài đặt hiện tại (FD2); chữ ở `strings.xml` (khối `wx_` cuối file); tiếng Anh đến cùng nhánh ngôn ngữ LG | FD2; brief |
| 10 | **Câu hỏi 1 chốt theo đề xuất:** không có nút "Làm mới"; sau lỗi mạng/máy chủ thử lại sau 15 phút; lần tải đầu của một địa điểm chưa có dữ liệu chạy ngay khi widget đầu tiên hiện nó | Chủ dự án, 2026-11-13 (qua điều phối) |
| 11 | **Câu hỏi 2 chốt:** có — Task 6 cập nhật vị trí tự động im lặng: một lần mỗi chu kỳ làm mới (≥ 2 giờ), chỉ khi widget "Vị trí hiện tại" đang hiện, đã cấp quyền, chế độ Tự động, và chỉ đổi khi xa hơn 5 km | Chủ dự án, 2026-11-13 |
| 12 | **Câu hỏi 3 chốt:** chọn địa điểm cho widget bằng cách bấm widget (D2-A); đường "edit mode → bấm widget" có thể làm sau | Chủ dự án, 2026-11-13 |
| 13 | **Câu hỏi 4 chốt:** "Vị trí hiện tại" không xóa được; ở chế độ Thủ công nó là một thành phố | Chủ dự án, 2026-11-13 |
| 14 | Chủ dự án cho phép cấp quyền vị trí cho Os26Launcher trên Live 4 khi kiểm (điều phối thu hồi sau) | Chủ dự án, 2026-11-13 (qua điều phối) |

## Câu hỏi cho chủ dự án

**Cả bốn câu đã được chủ dự án chấp nhận đúng như đề xuất (2026-11-13), xem Quyết định 10–14.** Nội dung giữ lại để đọc lý do.

1. **Mức nghiêm của "tối đa 2 giờ".** Đề xuất: (a) bỏ nút "Làm mới" trong bảng; (b) sau lỗi mạng/máy chủ thử lại sau **15 phút** thay vì chờ đủ 2 giờ (lỗi mạng thường chưa tới được API); (c) thêm địa điểm hoặc gán địa điểm cho widget chưa có dữ liệu thì tải lần đầu ngay (không phải "làm mới"). Nếu chủ dự án muốn tuyệt đối 2 giờ kể cả sau lỗi: đặt hằng `RETRY_AFTER_ERROR_MS` = 2 giờ, không sửa code khác.
2. **Chế độ Tự động có cập nhật vị trí im lặng không?** G3e chỉ lấy vị trí khi người dùng bấm nút. Đề xuất: **có**, đúng một lần mỗi chu kỳ làm mới (≥ 2 giờ) khi *widget "Vị trí hiện tại" đang hiện và đã cấp quyền*, chỉ đổi địa điểm nếu xa hơn 5 km (chống nhảy), không chạy nền, không `LocationListener` sống lâu. Lý do: người đi xa mà widget vẫn hiện nhà thì dở. Làm ở Task 6, **tách riêng**: cắt Task 6 thì chế độ Tự động chỉ cập nhật khi người dùng bấm "Cập nhật vị trí" (đúng G3e).
3. **Chọn địa điểm cho widget qua bấm widget (D2-A)** — đề xuất giữ; có muốn thêm đường "edit mode → bấm widget" (D2-D) sau không?
4. **"Vị trí hiện tại" không xóa được** (luôn là mục đầu, D3-A); ở chế độ Thủ công nó là một thành phố. Đề xuất giữ.

## Ngoài phạm vi / ghi để sau

- **Thông báo thời tiết** (07:00, 21:00 ngày mai, TT#17) và **cảnh báo thời tiết xấu**: nhánh riêng (cảnh báo bỏ vì lý do ở bảng "Nguồn tài liệu").
- **Thẻ "hiện tại + theo giờ" trong màn 1.4 của bản nháp:** plan WU ghi nó "thuộc WX"; theo FD2 (giữ kiểu hiển thị hiện tại) và phạm vi chủ dự án chốt (chỉ địa điểm) thì **không làm** ở nhánh này; điều phối cho biết nếu muốn thêm.
- Gõ-đến-đâu-tìm-đến-đó (autocomplete), đổi tên/sắp xếp địa điểm, địa điểm thứ tư, widget cỡ khác, ngôn ngữ EN (nhánh LG), logo OpenWeather (câu hỏi Q4 của G3e vẫn mở, trước phát hành), proxy che khóa (C15, trước phát hành).
- Chuỗi của sheet cũ không còn dùng (`weather_sheet_use_location`, `weather_sheet_refresh`, `weather_sheet_refresh_too_soon`, ...): **không xóa** (quy ước không tự xóa); ghi tên vào Nhật ký để điều phối quyết.

## Vùng file

Đường dẫn tính từ `app/src/main/java/gambi/global/os26launcher/`, trừ khi ghi khác. **Không có file độc quyền (workflow 5.2) nào trong danh sách.**

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/weather/WeatherPlaces.kt` | Tạo (mô hình địa điểm: `LocationMode`, `WeatherPlaces`, hằng `CURRENT`, `MAX_PLACES`) | Không |
| `domain/weather/WeatherPlacesRepository.kt` | Tạo | Không |
| `domain/model/weather/WeatherModels.kt` | Sửa (`WeatherPolicy`: 2 giờ, 15 phút, bỏ `MANUAL_REFRESH_MIN_MS`, `STALE_AFTER` 4 giờ) | Không |
| `domain/weather/WeatherRepository.kt`, `data/weather/WeatherRepositoryImpl.kt` | Sửa (theo từng địa điểm; bỏ `select`/`refreshNow`) | Không |
| `data/weather/WeatherStore.kt` | Sửa (version 2, đọc version 1) | Không |
| `data/weather/WeatherPlacesStore.kt`, `data/weather/WeatherPlacesRepositoryImpl.kt` | Tạo (`weather_places.json`) | Không |
| `data/weather/CurrentLocationUpdater.kt` | Tạo (Task 6, tùy chọn) | Không |
| `di/WeatherModule.kt` | Sửa (thêm binding, **chỉ thêm**) | Dùng chung có kiểm soát (Hilt module) |
| `LauncherApp.kt` | Sửa (đưa `WeatherPlacesRepository` vào `WeatherWidgetHub.init`) | Không (file khởi tạo; G3e/WU đã sửa) |
| `domain/usecase/RemoveWidgetUseCase.kt` | Sửa (1 dòng `forgetWidget`) | Không |
| `ui/widget/WeatherWidgetHub.kt`, `ui/widget/WeatherWidgetRenderers.kt` | Sửa | **Lớp widget — cẩn thận** |
| `ui/widget/WidgetRenderer.kt` | Sửa (thêm hàm mặc định `bindItem(itemId)`, các renderer khác không đổi) | **Lớp widget — cẩn thận** |
| `ui/home/view/IconCellView.kt` | Sửa (`bindWidget` thêm `itemId`) | **Lớp widget — cẩn thận** (không độc quyền) |
| `ui/home/view/IconGridView.kt` | Sửa (truyền `itemId` khi bind và khi bấm) | **Lớp widget — cẩn thận** (không độc quyền) |
| `ui/home/HomePagerAdapter.kt`, `ui/home/controller/HomePagerController.kt` | Sửa (chữ ký `onWidgetClick(kind, itemId)`; controller gọi `WeatherWidgetHub.tappedWidgetId` rồi `onWeatherClick()`) | **Cử chỉ bấm widget — cẩn thận** |
| `ui/weather/CitySearchDialog.kt`, `ui/weather/CitySearchViewModel.kt` | Tạo (tách phần tìm thành phố ra khỏi bảng) | Không |
| `ui/weather/WeatherSheetController.kt`, `ui/weather/WeatherSheetViewModel.kt`, `res/layout/view_weather_sheet.xml` | Sửa (bảng thành bộ chọn địa điểm) | Không |
| `res/layout/dialog_city_search.xml` (tên chốt khi làm), `res/layout/item_weather_choice.xml` | Tạo | Không |
| `ui/settings/WeatherSettingsScreen.kt`, `ui/settings/WeatherSettingsViewModel.kt` | Sửa (thêm hàng địa điểm) | Không (do WU/WX sở hữu) |
| `ui/settings/LocationSettingsScreen.kt`, `ui/settings/LocationSettingsViewModel.kt` | Tạo (màn 1.5) | Không |
| `ui/settings/SettingsScreen.kt` | Sửa **một dòng** (thay `// 1.5 LOCATION (WX)`) | Dùng chung có kiểm soát (`ui/settings/*`) |
| `ui/settings/SettingsRow.kt`, `ui/settings/SettingsAdapter.kt` | Sửa, **chỉ thêm** (`Value.titleText` có mặc định; ID hàng mới ở cuối companion; 2 dòng dùng `titleText` ở `ValueHolder`) | Dùng chung có kiểm soát |
| `res/values/strings.xml` | Sửa, **một khối `wx_` liền ở cuối file** | Dùng chung có kiểm soát |
| `docs/plans/WX-weather-cities.md`, `docs/knowledgements/WX-weather-cities.md` | Tạo / cập nhật | Không |

**Không sửa:** `HomeActivity.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `activity_home.xml`, `data/layout/*`, `core/glass/*`, `DragHelper`, `DragDropTarget`, `AndroidManifest.xml`, `app/build.gradle.kts`, `libs.versions.toml`, `app/schemas/*`, `SettingsKeys`/`SettingsRepositoryImpl`/`SettingsUiState`/`SettingsViewModel`, `SettingsHubScreen` (mục "Vị trí" đã khai sẵn). **Về `HomeActivity`:** lambda `onWeatherClick = { weatherSheet.show() }` vẫn biên dịch khi `HomePagerController` giữ kiểu `() -> Unit`; id widget đi qua `WeatherWidgetHub.tappedWidgetId`. Nếu điều phối thấy việc truyền qua `object` là xấu, đường lùi là đổi lambda đó thành `{ id -> weatherSheet.show(id) }` (1 dòng trong file độc quyền, cần xin) — **nhánh này không làm vậy trừ khi được bảo.**

## Global Constraints

- Tầng `ui → domain ← data`: `ui/weather`, `ui/settings`, `ui/widget` chỉ biết interface ở `domain`; không import `data`.
- **Khóa OWM không bao giờ** xuất hiện trong log, plan, commit, PR, báo cáo, ảnh chụp; kịch bản kiểm dùng `<KEY>`. Mọi log mạng không chứa URL, khóa, **tọa độ**, tên địa điểm (log tạm đo số lệnh chỉ ghi chỉ số và mã kết quả, và bị revert trước khi đóng nhánh).
- Chuỗi hiển thị ở `strings.xml` (tiếng Việt), một khối `wx_` ở cuối file; tên chuỗi cũ giữ nguyên.
- Không Room, không khóa DataStore mới (khóa đơn vị của WU giữ), không dependency mới, không đổi manifest.
- Không `Handler`/receiver/WorkManager mới; làm mới chỉ qua `WidgetTicker` có sẵn (G3e D2).
- Không gọi mạng khi: đổi đơn vị; thêm/xóa địa điểm trong Cài đặt; mở bảng; đổi chế độ 1.5. Chỉ gọi mạng: làm mới theo chu kỳ cho địa điểm đang hiện (2 lệnh), tìm thành phố khi bấm Tìm (1–2 lệnh), tra tên khi cập nhật vị trí tự động (1 lệnh).
- Hộp thoại dismiss ở `onDestroy` (gỡ listener trước), một hộp một lúc; `registerForActivityResult` gọi lúc dựng (theo "Công thức thêm màn/hàng").
- Tối đa 3 địa điểm cứng ở domain (repository từ chối, UI chỉ là lớp thứ hai).
- Mọi `when (kind)`, `bindWidget`, renderer cũ (đồng hồ, lịch) giữ nguyên hành vi.
- Commit tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự, không dấu, có dòng `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Chỉ `git add` theo đường dẫn tường minh. Build `gradlew.bat assembleDebug` xanh mới báo xong Task.

## Review Focus

1. **Không đổi Room/`launcher_items`:** `git diff --stat` không có `data/layout/*`, `app/schemas/*`.
2. **Chặn 2 giờ đúng bảng D4**, theo từng địa điểm: thành công +2 giờ; 429 +2 giờ; lỗi mạng/máy chủ/parse +15 phút; 401 không thử lại; giờ lùi = hết hạn; địa điểm không có widget hiện thì **0 lệnh**; thêm/xóa/gán địa điểm không gọi mạng nếu địa điểm chưa được widget hiện.
3. **Không có lệnh gọi mạng khi đổi °C/°F** (giữ hành vi WU): `attempt` trong `weather.json` không đổi.
4. **Nâng cấp từ G3e:** `weather.json` version 1 đọc được; địa điểm cũ thành "Vị trí hiện tại" (Thủ công); không mất số trên widget.
5. **Lớp widget:** mọi `bindWidget` truyền `itemId`; renderer cập nhật `itemId` ở **mỗi** lần bind (ô tái dùng); `clearWidget` không để lại địa điểm của widget trước; không cấp phát trong `draw`; bản xem trước (`sample`) không đọc ánh xạ, không gọi mạng; đồng hồ/lịch không đổi.
6. **Bấm widget:** vẫn nuốt chạm trong edit mode; ngoài edit mode mở bảng cho **đúng widget được bấm** (kể cả hai widget thời tiết ở hai địa điểm khác nhau); nhấn giữ vẫn vào edit mode.
7. **Địa điểm bị xóa:** widget đang dùng rơi về "Vị trí hiện tại", không crash, không để mục ánh xạ trỏ vào địa điểm không còn.
8. **Giới hạn 3:** thêm lần thứ 3 (mục thứ 4 tính cả hiện tại) bị từ chối ở repository, hàng "+ Thêm địa điểm" mờ; trùng địa điểm (kể cả trùng "Vị trí hiện tại") báo "đã có".
9. **1.5:** Tự động không có quyền → trạng thái rõ + nút cấp quyền, widget "Vị trí hiện tại" hiện "Chọn thành phố"/hướng dẫn, không crash; Thủ công không đòi quyền; đổi qua lại không mất giá trị của chế độ kia; từ chối vĩnh viễn → mở cài đặt ứng dụng.
10. **Không rò:** không log khóa/URL/tọa độ; listener vị trí gỡ khi hủy (`dumpsys location`); hộp thoại tìm thành phố đóng khi Activity hủy.
11. **Quyền riêng tư:** `weather_places.json` và `weather.json` ở `noBackupFilesDir`.
12. **Hồi quy Cài đặt:** các màn khác dùng `SettingsRow.Value` không đổi hiển thị (`titleText` mặc định null).

---

### Task 1: Mô hình địa điểm, kho lưu và di trú file cũ (domain + data, chưa đổi hành vi)

**Bắt buộc kiểm tay:** không — chưa đổi giao diện; kịch bản cài đè đủ cho điều phối.
**Files:** `domain/model/weather/WeatherPlaces.kt`, `domain/weather/WeatherPlacesRepository.kt`, `data/weather/WeatherPlacesStore.kt`, `data/weather/WeatherPlacesRepositoryImpl.kt`, `data/weather/WeatherStore.kt`, `data/weather/WeatherRepositoryImpl.kt` (chỉ để `place`/`select` đi qua kho mới, hành vi như G3e), `di/WeatherModule.kt`, `LauncherApp.kt`.
**Interfaces:**
- Produces: `WeatherPlaces(mode: LocationMode, autoPlace: GeoPlace?, manualPlace: GeoPlace?, saved: List<GeoPlace>)` với `current` suy ra theo chế độ, `MAX_PLACES = 3`, `CURRENT = "current"`, `placeIdOf(GeoPlace)`; `WeatherPlacesRepository`: `places: StateFlow<WeatherPlaces>`, `suspend add(place): AddResult` (`Added`/`Duplicate`/`Full`), `suspend remove(placeId)`, `suspend setMode(mode)`, `suspend setAutoPlace(place)`, `suspend setManualPlace(place)`, `resolve(placeId): GeoPlace?`, `widgetPlaceId(itemId): String`, `suspend setWidgetPlace(itemId, placeId)`, `suspend forgetWidget(itemId)`.
- Consumes: `GeoPlace`, `AtomicFile` (mẫu `WeatherStore`).
- [x] **Step 1:** Mô hình + `WeatherPlacesStore` (`weather_places.json`, `noBackupFilesDir`, `AtomicFile`, version 1 của file mới; hỏng/sai version → mặc định; không log nội dung).
- [x] **Step 2:** `WeatherPlacesRepositoryImpl` giữ `StateFlow`, mọi ghi dưới một `Mutex`, ghi file ở `IoDispatcher`; từ chối khi đủ `MAX_PLACES` hoặc trùng `placeId`; `remove` cũng dọn ánh xạ widget trỏ vào địa điểm đó; mặc định chế độ Tự động.
- [x] **Step 3:** Di trú: lần đầu chạy, nếu chưa có `weather_places.json` mà `weather.json` (version 1) có `place` thì đặt `manualPlace = place`, `mode = MANUAL` (không phân biệt được nguồn gốc; chấp nhận). `WeatherRepositoryImpl.place`/`select` đọc/ghi qua kho mới (hành vi G3e giữ nguyên ở Task này).
- [x] **Step 4:** Nối Hilt + `LauncherApp`; build xanh.
- [x] **Step 5: Kịch bản kiểm (Live 4, xác nhận máy rảnh).** (a) Cài bản `main` (a0c1be2) có khóa, chọn "Đà Nẵng", thấy số trên widget; cài đè bản nhánh: widget vẫn "Đà Nẵng" và có số ngay (không chớp "Chọn thành phố"). (b) `adb shell run-as gambi.global.os26launcher ls no_backup`: có `weather_places.json` và `weather.json`. (c) `Select-String` không thấy khóa/tọa độ trong logcat. (d) Bản sạch (xóa file `no_backup/*` bằng `run-as rm`): widget "Chọn thành phố", không crash.

### Task 2: Làm mới theo từng địa điểm, chặn 2 giờ

**Bắt buộc kiểm tay:** không — kiểm bằng chỉnh `attempt` (script dưới) và log tạm; chưa phải cử chỉ/migration/đo hiệu năng.
**Files:** `domain/weather/WeatherRepository.kt`, `data/weather/WeatherRepositoryImpl.kt`, `data/weather/WeatherStore.kt` (version 2, mục theo địa điểm, đọc version 1), `domain/model/weather/WeatherModels.kt` (`WeatherPolicy`), `ui/widget/WeatherWidgetHub.kt`, `ui/weather/WeatherSheetViewModel.kt` (chỉ đủ để biên dịch: dùng `"current"`).
**Interfaces:**
- Produces: `WeatherRepository.states: StateFlow<Map<String, WeatherState>>`, `refreshIfStale(placeId)`, `hasKey`, `search`, `reverse`; **bỏ** `place`, `select`, `refreshNow`. Mục bộ nhớ đệm hợp lệ chỉ khi tọa độ khớp địa điểm đang tra. `WeatherWidgetHub.stateOf(placeId)`, `onVisible(placeId)`.
- Consumes: `WeatherPlacesRepository.resolve(placeId)` (Task 1).
- [x] **Step 1:** `WeatherStore` version 2: `{"v":2,"entries":{"<placeId>":{"place","snapshot","attempt","result"}}}`; đọc version 1 thành `entries["current"]`; version lạ → bỏ cache, không crash.
- [x] **Step 2:** `WeatherRepositoryImpl`: `due(entry, now)` theo D4; `refreshIfStale(placeId)` kiểm nhanh (so số nguyên) rồi `scope.launch` + `mutex`; `keyDead` toàn cục; `doRefresh` chỉ chạm một địa điểm; xóa mục cache của địa điểm không còn trong danh sách (khi `places` đổi).
- [x] **Step 3:** Hằng `WeatherPolicy`: `REFRESH_INTERVAL_MS` = 2 giờ, `RETRY_AFTER_ERROR_MS` = 15 phút, `STALE_AFTER_MS` = 4 giờ; bỏ `MANUAL_REFRESH_MIN_MS`.
- [x] **Step 4:** `WeatherWidgetHub` theo địa điểm; widget hiện vẫn dùng `"current"` ở Task này (Task 5 mới theo widget).
- [x] **Step 5: Kịch bản kiểm (Live 4).** Dùng bản debug có khóa. Log tạm đã có ở commit 0890e62 (tag `WXNET`: `start idx=<chỉ số địa điểm>` rồi `done idx=<chỉ số> result=<ok/mã lỗi>`; không URL/khóa/tọa độ/tên); revert ở Task 7. Một dòng `start` = 2 lệnh gọi. (a) Mở Home có widget: đúng 1 dòng `refresh` (2 lệnh), mở/đóng Home 10 lần trong 5 phút: không thêm dòng. (b) `force-stop`, mở lại: 0 dòng (cache + chặn lưu trong file). (c) Giả lập quá 2 giờ: `adb exec-out run-as gambi.global.os26launcher cat no_backup/weather.json > E:\tmp\w.json`, sửa `entries.current.attempt` (file version 2; mili giây) lùi 2 giờ 1 phút bằng PowerShell, `adb push` lên `/data/local/tmp` rồi `run-as ... cp` vào `no_backup/weather.json` (sau `force-stop`): đúng 1 `refresh` khi mở Home. (d) Lùi 1 giờ 59 phút: 0 dòng. **(d2) Lùi CẢ `attempt` VÀ `snapshot.at` 1 giờ 59 phút (giống một lần thành công thật cách đây 1 giờ 59): 0 dòng; chỉnh file bằng thay chuỗi số (regex), KHÔNG dùng `ConvertTo-Json` mặc định (độ sâu 2 làm hỏng `place`/`snapshot`, mục bị bỏ qua và tải lại ngay); làm nhanh dưới 1 phút từ lúc đặt tới lúc mở, hoặc dùng 1 giờ 55 phút; nhìn `adb logcat -s G3E WXNET`: dòng `start` ghi `why=...` và tuổi `attemptMin`/`snapMin`, dòng `G3E entry skipped` hay `load failed` cho biết file bị hỏng.** (e) Máy bay + lùi 2 giờ: 1 dòng `result=NETWORK`, rồi 0 dòng trong 14 phút, 1 dòng sau 15 phút (tăng `attempt` lùi thay vì chờ). (f) Đổi °C/°F: 0 dòng, `attempt` giữ nguyên. (g) Ẩn widget (xóa khỏi Home): 0 dòng dù qua hạn. (h) Cài đè lên bản có `weather.json` version 1: vẫn có số.

### Task 3: Thành phần "tìm thành phố" dùng chung và danh sách địa điểm ở Cài đặt → Thời tiết

**Bắt buộc kiểm tay:** không.
**Files:** `ui/weather/CitySearchDialog.kt`, `ui/weather/CitySearchViewModel.kt`, `res/layout/dialog_city_search.xml`, `ui/settings/WeatherSettingsScreen.kt`, `ui/settings/WeatherSettingsViewModel.kt`, `ui/settings/SettingsRow.kt`, `ui/settings/SettingsAdapter.kt`, `res/values/strings.xml`.
**Interfaces:**
- Produces: `CitySearchDialog.show(activity, onPicked: (GeoPlace) -> Unit)` (ô nhập, nút Tìm, danh sách ≤ 5 kết quả "Tên, tỉnh, mã nước", trạng thái đang tìm/không thấy/lỗi mạng; tìm khi bấm Tìm hoặc phím Search, không tìm theo từng phím; không khóa → thông báo như G3e; tự đóng ở `onDestroy`); `SettingsRow.Value(..., titleText: String? = null)`; `ID_WEATHER_CURRENT`, `ID_WEATHER_PLACE_1/2`, `ID_WEATHER_ADD` cuối `SettingsRow.Companion`.
- Consumes: `WeatherRepository.search` (G3e), `WeatherPlacesRepository` (Task 1).
- [x] **Step 1:** Tách logic tìm khỏi `WeatherSheetViewModel` thành `CitySearchViewModel` (bảng cũ tạm thời vẫn dùng được: Task 5 mới gỡ).
- [x] **Step 2:** `WeatherSettingsScreen`: nhóm "Địa điểm theo dõi" (hàng "Vị trí hiện tại" giá trị tên hoặc "Chưa xác định", bấm mở `SettingsScreenId.LOCATION` chỉ khi `SettingsScreens.isRegistered(LOCATION)`; màn 1.5 làm ở Task 4 nên trước đó bấm không làm gì); hàng thành phố đã thêm (bấm → hộp thoại "Xóa <tên>?" Xóa/Hủy → `places.remove`); hàng "+ Thêm địa điểm" (mờ, giá trị "Đã đủ 3 địa điểm" khi đầy; trùng → Toast "Địa điểm này đã có"). Hàng đơn vị của WU giữ nguyên bên dưới.
- [x] **Step 3:** Mô tả ngắn ở chân nhóm: "Mỗi tiện ích thời tiết chọn một địa điểm trong danh sách (bấm vào tiện ích)." (dòng `Footer`).
- [x] **Step 4: Kịch bản kiểm (Live 4).** (a) Cài đặt → Thời tiết: thấy "Vị trí hiện tại", "+ Thêm địa điểm", "Đơn vị nhiệt độ". (b) Thêm "Hà Nội" (gõ có dấu và "Ha Noi"), "Đà Nẵng": 2 hàng; hàng thứ ba "+ Thêm" mờ, ghi đủ 3. (c) Thêm lại "Hà Nội": báo đã có. (d) Xóa một hàng → hộp xác nhận → mất, "+ Thêm" bật lại. (e) Xoay màn hình khi hộp tìm đang mở: không crash, không rò cửa sổ. (f) Không khóa (bản không `OWM_API_KEY`): hộp báo chưa cấu hình, không gọi mạng. (g) `WXNET`: 0 lệnh làm mới khi thêm/xóa địa điểm chưa có widget nào hiện. (h) Các màn Cài đặt khác (Chung, Tìm kiếm, Bộ biểu tượng): hàng hiển thị y như cũ.

### Task 4: Màn 1.5 Vị trí (Tự động / Chọn thủ công, quyền)

**Bắt buộc kiểm tay:** không — nhưng **Live 4 (API 30) là lần đầu kiểm đường `getCurrentLocation`** (G3e chỉ kiểm Oppo); đường lùi là Thủ công nên Task sau không xây trên giả định này.
**Files:** `ui/settings/LocationSettingsScreen.kt`, `ui/settings/LocationSettingsViewModel.kt`, `ui/settings/SettingsScreen.kt` (một dòng), `res/values/strings.xml`.
**Interfaces:**
- Produces: màn `LocationSettingsScreen`: nhóm "Quyền vị trí" (hàng giá trị Đã cấp/Chưa cấp; bấm khi chưa cấp → xin quyền; từ chối vĩnh viễn → mở cài đặt ứng dụng), nhóm "Cách xác định vị trí" (hàng "Chế độ": Tự động / Chọn thủ công qua hộp thoại chọn một), Tự động: hàng "Vị trí hiện tại" (giá trị tên) bấm = "Cập nhật vị trí" (lấy vị trí một lần ≤ 10 s, tra tên bằng `reverse`, `setAutoPlace`); Thủ công: hàng "Thành phố" → `CitySearchDialog` → `setManualPlace`; chân trang (dòng `Footer`) ghi chú riêng tư: "Chỉ dùng vị trí gần đúng, lấy một lần khi bạn bấm hoặc khi tiện ích thời tiết đang hiện; không chạy nền."
- Consumes: `CoarseLocationSource` (G3e), `WeatherPlacesRepository`, `CitySearchDialog` (Task 3).
- [x] **Step 1:** `LocationSettingsViewModel` (mẫu `WeatherSettingsViewModel`, lỗi qua `Channel`); `registerForActivityResult(RequestPermission)` trong `init` của màn (công thức G2d).
- [x] **Step 2:** Đổi chế độ không mất giá trị của chế độ kia; chuyển sang Tự động đã có quyền mà chưa có vị trí thì tự cập nhật một lần.
- [x] **Step 3:** Thay dòng `// 1.5 LOCATION (WX)` bằng `SettingsScreenId.LOCATION to ::LocationSettingsScreen`; mục "Vị trí" ở trang chủ tự bật.
- [x] **Step 4: Kịch bản kiểm (Live 4, xác nhận máy rảnh).** (a) Trang chủ Cài đặt: "Vị trí" bật (không còn "Sắp có"). (b) Chưa cấp quyền, chế độ Tự động: hàng báo Chưa cấp; bấm → hộp quyền hệ thống: Cho phép → "Cập nhật vị trí" ra tên nơi (ghi rõ: có tên không, mất bao lâu, `dumpsys location | findstr gambi` sau đó không còn yêu cầu); Từ chối → thông báo, không crash; Từ chối lần hai (không hỏi lại) → mở cài đặt ứng dụng. (c) Tắt dịch vụ vị trí của máy rồi "Cập nhật vị trí": hết ~10 s báo không lấy được, không treo, giữ giá trị cũ. (d) Chuyển Thủ công: không đòi quyền; chọn "Hà Nội" → "Vị trí hiện tại" ở màn Thời tiết đổi thành Hà Nội. (e) Chuyển lại Tự động: vị trí tự động cũ còn. (f) Thu hồi quyền trong cài đặt máy khi đang ở màn: quay lại màn thì trạng thái cập nhật (`onResume`). (g) Xoay màn hình khi hộp thoại mở.

### Task 5: Mỗi widget chọn một địa điểm; bảng Thời tiết thành bộ chọn  **(rủi ro cao: lớp widget)**

**Bắt buộc kiểm tay:** không — không thêm cử chỉ mới (bấm widget đã có và đã kiểm ở G3e; nhấn giữ và kéo không đổi). Rủi ro cao nên **điều phối nên tự cài và chạy kịch bản hồi quy kéo-thả/xoay (workflow quyết định 7, 9)** trước khi tick; điều phối bỏ/đổi dấu nếu thấy nên bắt buộc.
**Files:** `ui/widget/WidgetRenderer.kt`, `ui/widget/WeatherWidgetRenderers.kt`, `ui/widget/WeatherWidgetHub.kt`, `ui/home/view/IconCellView.kt`, `ui/home/view/IconGridView.kt`, `ui/home/HomePagerAdapter.kt`, `ui/home/controller/HomePagerController.kt`, `domain/usecase/RemoveWidgetUseCase.kt`, `ui/weather/WeatherSheetController.kt`, `ui/weather/WeatherSheetViewModel.kt`, `res/layout/view_weather_sheet.xml`, `res/layout/item_weather_choice.xml`, `res/values/strings.xml`.
**Interfaces:**
- Produces: `WidgetRenderer.bindItem(itemId: Long) {}` (mặc định rỗng); `IconCellView.bindWidget(kind, spanX, spanY, c, glass, itemId)` (tham số mới cuối, gọi `renderer.bindItem(itemId)` **mỗi** lần bind); `IconGridView.onWidgetClick: (kind, itemId)`; `WeatherWidgetHub.placeIdOf(itemId)` (tra `widgetPlaceId`, địa điểm không còn → `"current"`), `WeatherWidgetHub.tappedWidgetId`; bảng Thời tiết: tiêu đề, radio ≤ 3 dòng (tên địa điểm; "Vị trí hiện tại" kèm tên hoặc "chưa xác định"), trạng thái ("Cập nhật lúc HH:mm" hoặc lỗi), nút "Quản lý địa điểm" (mở `SettingsActivity` với `EXTRA_SCREEN = WEATHER`), câu ghi công OpenWeather; chọn radio → `setWidgetPlace(itemId, placeId)` + `WidgetTicker.tickNow()`, bảng giữ mở.
- Consumes: Task 1–4; `WeatherRepository.states`/`refreshIfStale(placeId)`.
- [x] **Step 1:** `WeatherRenderer.bindItem` lưu `itemId`; `prepare` tra `placeId = Hub.placeIdOf(itemId)` (0 cho bản mẫu), lấy `Hub.stateOf(placeId)`, gọi `Hub.onVisible(placeId)` (không với bản mẫu). Không cấp phát mới trong `draw`; chuỗi dựng ở `prepare` như cũ.
- [x] **Step 2:** `IconCellView.bindWidget` thêm `itemId`; `IconGridView.bindWidgetCell` truyền `widget.itemId`; callback bấm truyền `placed.itemId` qua `HomePagerAdapter` → `HomePagerController`, nơi `WeatherWidgetHub.tappedWidgetId = itemId` rồi gọi `onWeatherClick()` (kiểu `() -> Unit` giữ nguyên; `HomeActivity` không sửa). `onWidgetClick` cho đồng hồ/lịch giữ đường cũ.
- [x] **Step 3:** Bảng Thời tiết dựng lại (bảng cũ: gỡ ô nhập, tìm, "Dùng vị trí hiện tại", "Làm mới"; việc thêm địa điểm nay ở Cài đặt). Đọc `tappedWidgetId` lúc `show()`; chưa có khóa → thông báo như G3e.
- [x] **Step 4:** `RemoveWidgetUseCase` gọi `forgetWidget(itemId)` sau khi xóa hàng.
- [x] **Step 5:** Rà `Select-String` cho mọi lời gọi `bindWidget(`/`onWidgetClick` trong `app/src` (không sót chỗ); đọc lại `clearWidget`/tái dùng ô.
- [x] **Step 6: Kịch bản kiểm (Live 4, xác nhận máy rảnh).** Chuẩn bị: danh sách có "Vị trí hiện tại" (Thủ công: Hà Nội) + "Đà Nẵng" + "Huế"; hai widget thời tiết 2×2 (A) và 4×2 (B) ở hai trang khác nhau. (a) Cả hai mặc định hiện Hà Nội. (b) Bấm A → bảng có 3 radio, "Vị trí hiện tại" đang chọn; chọn "Đà Nẵng": A đổi ngay (số lúc đầu "Đang tải"/cache) trong vài giây, **B vẫn Hà Nội**. (c) Bấm B → radio đang chọn là "Vị trí hiện tại" (không lẫn với A). (d) `force-stop` rồi mở: A vẫn Đà Nẵng, B vẫn Hà Nội. (e) Hai widget cùng một địa điểm: `WXNET` chỉ 1 lần làm mới cho địa điểm đó. (f) Xóa địa điểm "Đà Nẵng" trong Cài đặt rồi về Home: A hiện Hà Nội ("Vị trí hiện tại"), không crash; mở lại bảng không có Đà Nẵng. (g) Hồi quy lớp widget: kéo A sang trang khác (cùng trang, trang mới tạo, thả vào chỗ có icon → đẩy-dồn), nhấn giữ widget vào edit mode (rung), "−" xóa có xác nhận (rồi thêm lại: widget mới dùng "Vị trí hiện tại", không thừa hưởng địa điểm cũ), thêm widget từ "Tiện ích" bằng bấm thẻ và bằng kéo thẻ, đổi cỡ lưới trong Cài đặt chung, xoay màn hình: sau mỗi thao tác A vẫn đúng địa điểm đã chọn (trừ khi đã xóa). (h) Đồng hồ và Lịch: thêm, bấm mở app đồng hồ/lịch như cũ. (i) Bản xem trước trong bảng "Tiện ích": thời tiết vẫn hiện số mẫu theo đơn vị. (j) Home → bấm Home khi bảng thời tiết đang mở: bảng đóng (FX2/B10). (k) Gỡ khóa (build không `OWM_API_KEY`): widget "Chưa cấu hình khóa", bảng báo, không crash.

### Task 6 (tùy chọn, theo Câu hỏi 2): Cập nhật vị trí tự động theo chu kỳ làm mới

**Bắt buộc kiểm tay:** không (có thể cắt Task này mà không ảnh hưởng Task khác).
**Files:** `data/weather/CurrentLocationUpdater.kt`, `data/weather/WeatherRepositoryImpl.kt`, `data/weather/WeatherPlacesStore.kt` (thêm `autoFixAt`), `di/WeatherModule.kt`.
**Interfaces:** Consumes `CoarseLocationSource`, `WeatherProvider.reverse`, `WeatherPlacesRepository.setAutoPlace`. Produces: trước khi làm mới `"current"` ở chế độ Tự động, nếu đã cấp quyền và lần lấy vị trí gần nhất cũ hơn 2 giờ thì lấy một lần (≤ 10 s); chỉ khi cách vị trí đã lưu > 5 km (công thức haversine, hằng CHỈNH TAY) mới `reverse` + `setAutoPlace` (đổi tọa độ làm bộ nhớ đệm cũ không hợp lệ). Không `LocationListener` sống lâu; không chạy khi chế độ Thủ công, không có quyền, hoặc không có widget "Vị trí hiện tại" đang hiện.
- [x] **Step 1–2:** Viết `CurrentLocationUpdater`, gọi từ đường làm mới của `"current"` (trong `mutex`, có timeout cứng); lỗi → bỏ qua và dùng vị trí cũ, không ném.
- [x] **Step 3: Kịch bản kiểm (Live 4).** (a) Tự động + có quyền: lùi `fixAt` (trường trong `weather_places.json`, ms) 2 giờ 5 phút (như Task 2; chỉnh bằng thay chuỗi số, không dùng `ConvertTo-Json` mặc định): mở Home → tối đa 1 lần lấy vị trí (`dumpsys location | findstr gambi` thấy yêu cầu ngắn rồi gỡ), vị trí không đổi (đứng yên) → **không** `reverse`, **không** đổi `placeId`. (b) Chế độ Thủ công hoặc thu hồi quyền: 0 yêu cầu vị trí. (c) Giả vị trí xa: sửa tọa độ của mục `auto` trong `weather_places.json` cách thật vài chục km (nếu máy không có mock location) (mock location của máy hoặc Oppo nếu máy dev cho phép; nếu không có thì ghi "chưa kiểm") → tên đổi, widget tải lại. (d) Không có widget "Vị trí hiện tại" hiện: 0 yêu cầu.

### Task 7: Rà cuối, knowledgement, hồi quy tổng

**Bắt buộc kiểm tay:** không.
**Files:** `docs/knowledgements/WX-weather-cities.md`, `docs/plans/WX-weather-cities.md`, rà `strings.xml`.
- [x] **Step 1:** Xóa log tạm `WXNET` (revert) và rà `Select-String` toàn diff: không khóa, không URL, không tọa độ, không `Log` mới ngoài `Log.w` có loại ngoại lệ.
- [x] **Step 2:** Knowledgement theo mẫu mục 8 `MultiAgent-Workflow.md` (sơ đồ Mermaid: luồng chọn địa điểm cho widget; luồng làm mới theo địa điểm; luồng 1.5). Điền "Bài học cho AGENTS.md", "Đề xuất cập nhật tài liệu chung".
- [x] **Step 3:** Liệt kê chuỗi không còn dùng cho điều phối quyết (không tự xóa).
- [ ] **Step 4:** Chạy bài kiểm cả tính năng bên dưới trên Live 4 (hoặc đưa điều phối); điền "Bằng chứng Review Focus".

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: Vsmart Live 4 (xác nhận rảnh), bản debug có khóa, nhánh@commit ghi khi cài. Chuẩn bị: bản `main` có cache G3e (cài đè), hai widget thời tiết (2×2 và 4×2). Thứ tự thao tác tự nhiên:
1. Cài đè lên bản G3e đã chọn "Đà Nẵng": widget vẫn có số; Cài đặt → Thời tiết thấy "Vị trí hiện tại: Đà Nẵng" (Thủ công).
2. Cài đặt → Vị trí: chế độ, quyền, Cập nhật vị trí (Task 4 a–g).
3. Cài đặt → Thời tiết: thêm 2 thành phố, thử thêm lần ba, trùng, xóa, hộp tìm (Task 3).
4. Về Home: bấm từng widget, chọn địa điểm khác nhau, đóng/mở lại, `force-stop` (Task 5 a–f).
5. Chính sách 2 giờ và số lệnh bằng log tạm và chỉnh `attempt` (Task 2 a–h; Task 6 nếu có).
6. Hồi quy: lớp widget và cử chỉ (Task 5 g–k), đồng hồ/lịch, "Tiện ích", đơn vị °C/°F (không gọi mạng), đổi cỡ lưới, xoay màn hình, Cài đặt các màn khác, App Library, tìm kiếm.
7. Không khóa (build không `OWM_API_KEY`); máy bay; thu hồi quyền vị trí.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Điều phối chạy từng Task trên Vsmart Live 4 (Android 11), bản debug, ngày 2026-11-13: Task 1 @f47aaad, Task 2 @0890e62/e0ae214, Task 3 @2520876, Task 4 @bdcdbfb, Task 5 + 6 @68546b0. **Đạt cả sáu Task**; chi tiết ở bảng "Bằng chứng Review Focus". Bản cuối (sau khi gỡ log tạm, 5d1b970): build debug + release xanh, **chưa cài lên máy**. Còn lại cho bài kiểm tay của chủ dự án: các ca ghi "chỉ đọc code/chưa kiểm tay" (từ chối/thu hồi quyền, tắt dịch vụ vị trí, xóa "−" rồi thêm lại, kéo thẻ từ "Tiện ích", xoay, build không khóa, đồng hồ/lịch, bản xem trước, hồi quy các màn Cài đặt khác).

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-13 | — | Viết plan | be30550 | Chờ điều phối rà |
| 2026-11-13 | — | Điều phối duyệt plan; ghi bằng chứng D1 (không đường nào tạo lại hàng widget) | (commit docs này) | Câu hỏi 1–4 gửi chủ dự án (sau đó đã chấp nhận đúng đề xuất, xem Quyết định 10–14) |
| 2026-11-13 | 1 | Mô hình `WeatherPlaces`, `WeatherPlacesRepository` + impl, `weather_places.json`, di trú từ `weather.json` G3e, `WeatherRepository` đọc địa điểm từ danh sách; build `assembleDebug` xanh | f47aaad | Chờ review (chưa tick). Lệch nhỏ so với plan: (1) mã thành phố là `"<lat*100>_<lon*100>"` (số nguyên, không phụ thuộc locale) thay cho `"lat,lon"`; (2) `select(place, auto)` tạm thời ghi vào danh sách (Task 2 bỏ); (3) `places` là `StateFlow` nên đổi chỉ ánh xạ widget không phát — Task 5 cần thêm tín hiệu riêng cho việc đó |
| 2026-11-13 | 1 | Điều phối kiểm trên Live 4 (11:02–11:04, bản f47aaad): di trú từ `weather.json` G3e giữ nguyên thành phố và nhiệt độ, không chớp "Chọn thành phố"; `no_backup` có `weather_places.json` (MANUAL) và `weather.json`; ca sạch không crash; logcat 0 dòng khóa/URL/tọa độ | — | **Task 1 đạt, đã tick.** Chưa chạy: chọn thành phố từ bảng và "Dùng vị trí hiện tại" ở ca sạch (để Task 2) |
| 2026-11-13 | 2 | `states` theo địa điểm, `refreshIfStale(placeId)`, chặn 2 giờ / 15 phút / `STALE_AFTER` 4 giờ, bỏ `select`/`refreshNow` và nút "Làm mới", `weather.json` version 2 (đọc được version 1), widget vẫn dùng `"current"`; build xanh | 3dc089b (code), 0890e62 (log tạm `WXNET`) | Chờ review + kiểm Live 4 (chưa tick Step 5). APK `app/build/outputs/apk/debug/app-debug.apk` dựng từ 0890e62. Lưu ý kiểm: `attempt` nằm ở `entries.current.attempt` trong file version 2; chọn thành phố ở bảng không còn gọi mạng ngay mà chờ nhịp phút kế của widget (tối đa ~1 phút) |
| 2026-11-13 | 2 | Điều phối kiểm Live 4 (0890e62): (a), (b), 1h59/2h01 chỉ `attempt`, (d) mất mạng rồi 15 phút: đạt; riêng ca lùi CẢ `attempt` và `snapshot.at` 1h59 thì tải ngay (hai lần) | — | Đọc code: **quyết định đến hạn chỉ dùng `lastAttemptAt` và `lastResult`** (`dueReason`); `snapshot.at` (`fetchedAtMillis`) chỉ dùng cho `STALE_AFTER_MS` = 4 giờ (nhãn "cũ" trên widget) và giờ cập nhật trong bảng, **không có hằng nào < 2 giờ đọc nó**. Chưa tìm được lỗi trong code; hai nguyên nhân khả dĩ (suy luận chưa kiểm chứng): (1) file chỉnh tay bị hỏng (vd `ConvertTo-Json` độ sâu mặc định biến `place`/`snapshot` thành chuỗi → `readEntries` bỏ qua mục → "noEntry" → tải ngay, đúng thiết kế); (2) thời gian trôi giữa lúc đặt tuổi và lúc mở (1h59 + vài phút > 2h). Thêm log tạm `why=...` + tuổi để phân biệt; thêm ca (d2) vào Step 5 |
| 2026-11-13 | 3 | `CitySearchDialog` + `CitySearchViewModel` + `dialog_city_search.xml` (tìm bằng nút Tìm/phím Search, tối đa 5 kết quả); màn Thời tiết: nhóm "Địa điểm theo dõi" (Vị trí hiện tại, thành phố đã thêm bấm để xóa, "+ Thêm địa điểm" mờ khi đủ 3), dòng hướng dẫn, nhóm "Đơn vị"; `SettingsRow.Value.titleText`; chuỗi khối `wx_`; build xanh | 2520876 | Chờ review + kiểm Live 4 (chưa tick Step 4). Lệch nhỏ so với plan: id hàng thành phố là `ID_WEATHER_PLACE_PREFIX + mã địa điểm` (thay `ID_WEATHER_PLACE_1/2`); dòng hướng dẫn là một `Footer` giữa hai nhóm; hàng "Vị trí hiện tại" bấm không làm gì tới khi Task 4 đăng ký màn 1.5. APK vẫn có log tạm `WXNET` (từ e0ae214) |
| 2026-11-13 | 4 | `LocationSettingsScreen` + `LocationSettingsViewModel`: hàng Quyền vị trí (bấm: xin quyền, hoặc mở cài đặt ứng dụng khi đã cấp/đã chặn), hàng Chế độ (Tự động / Chọn thủ công), Tự động: "Cập nhật vị trí" (lấy một lần ≤ 10 s, tra tên bằng `reverse`, ghi vị trí tự động), Thủ công: "Thành phố" (hộp tìm dùng chung); ghi chú riêng tư; đăng ký ở ô 1.5; build xanh | bdcdbfb | Chờ review + kiểm Live 4 (chưa tick Step 4; lần đầu kiểm đường `getCurrentLocation` ở API 30). Chuỗi riêng tư hiện nói "lấy một lần khi bạn bấm Cập nhật vị trí" — đúng với Task 4; nếu làm Task 6 thì sửa câu này |
| 2026-11-13 | 2–4 | Điều phối kiểm Live 4 (bdcdbfb): Task 2 ca (d2) đạt (lùi cả `attempt` và `snapshot.at` 1h55: 0 dòng `WXNET`; lỗi trước đó là do thời gian: 1h59 + ~70 s chờ nhịp phút vượt 2 giờ), Task 3 đạt, Task 4 đạt (Tự động: tên nơi sau ~4,8 s, yêu cầu vị trí gỡ sau 2 s; Thủ công ↔ Tự động giữ giá trị; widget tải đúng 1 lần `why=noEntry`) | — | **Task 2, 3, 4 đạt, đã tick.** Chưa chạy: từ chối/thu hồi quyền, tắt dịch vụ vị trí, xoay (màn `nosensor`) — chỉ đọc code. Ghi chú cho Task 5/knowledgement: (a) Enter trong ô tìm không tìm → sửa (c1638b4); (b) kết quả trùng tên/tỉnh/nước ("Đà Nẵng, VN" ×2) → bỏ bản trùng (c1638b4); (c) thêm "Hà Nội" khi vị trí hiện tại (thủ công) cũng là Hà Nội vẫn được chấp nhận — cố ý, ghi vào knowledgement (hai chỗ cùng nơi dùng chung một lần tải? KHÔNG: mã địa điểm khác nhau nên tải riêng, tốn thêm 2 lệnh/2 giờ nếu có widget hiện cả hai) |
| 2026-11-13 | 5 | Widget theo địa điểm: `bindItem(itemId)` ở mỗi lần bind (`IconCellView.bindWidget` thêm `itemId`), renderer tra địa điểm từ `WeatherWidgetHub.placeIdOf(itemId)`, `widgetPlaces` là `StateFlow` (widget đổi địa điểm thì widget hiện vẽ lại), `onWidgetClick(kind, itemId)` qua `IconGridView` → `HomePagerAdapter` → `HomePagerController` (đặt `WeatherWidgetHub.tappedWidgetId` rồi gọi `onWeatherClick()`), `RemoveWidgetUseCase` dọn mục; Hub không kích hoạt làm mới trước khi danh sách địa điểm nạp xong. Bảng Thời tiết thành bộ chọn (radio + "Quản lý địa điểm" + giờ cập nhật + ghi công), bỏ tìm/vị trí/làm mới khỏi bảng; build xanh | 3dc8cef (widget), 15e5b75 (bảng) | Chờ review + kiểm Live 4 (chưa tick Step 6). `HomeActivity` KHÔNG sửa. Lưu ý: ảnh kéo từ bảng "Tiện ích" (itemId 0, không phải bản mẫu) vẫn gọi `onVisible("current")` như trước khi nó hiện, vì dùng renderer thật; chuỗi không còn dùng cho điều phối quyết (không xóa): `weather_sheet_current`, `weather_sheet_none`, `weather_sheet_use_location`, `weather_sheet_refresh`, `weather_sheet_locating`, `weather_sheet_location_denied`, `weather_sheet_location_unavailable`, `weather_sheet_refresh_too_soon`, `weather_sheet_current_location` |
| 2026-11-13 | 6 | `CurrentLocationUpdater` (gọi từ `refreshIfStale("current")` nên chỉ khi widget dùng "Vị trí hiện tại" đang hiện): chế độ Tự động + đã cấp quyền + lần lấy gần nhất ≥ 2 giờ (`WeatherPlaces.autoFixAt`, lưu `fixAt`; lấy tay cũng tính) → lấy một lần (≤ 10 s); chỉ khi cách vị trí đã lưu > 5 km (hoặc chưa có) mới `reverse` và đổi, còn lại chỉ ghi giờ; lỗi → thử lại sau 15 phút (RAM); chuyển sang Thủ công giữa chừng thì bỏ kết quả (`updateAutoFix` trả false); chuỗi riêng tư ở màn Vị trí cập nhật; log tạm `WXNET loc result=noFix|same|noName|moved|droppedManual sinceMin=.. movedKm=..`; build xanh | 68546b0 | Chờ kiểm Live 4 cùng Task 5 (một lượt). Không tên giả khi `reverse` thất bại |
| 2026-11-13 | 5–6 | Điều phối kiểm Live 4 (68546b0): Task 5 đạt (A→Đà Nẵng một `start idx=1 why=noEntry`, B giữ Hà Nội; bảng đúng widget; `force-stop` giữ; kéo từ trang cuối về trang 1 giữ địa điểm; đổi cỡ lưới 4×6→4×5 reflow A (item 124) sang trang 2 vẫn Đà Nẵng, `widgets={"124":…}`; xóa Đà Nẵng: A về "Vị trí hiện tại"; Home đóng bảng; không crash). Task 6 đạt (`fixAt` −2h05 → `loc result=same movedKm=0`, không tải thời tiết; −1h50 → không `loc`; `auto.la` +0,3 (~33 km) + −2h05 → `loc result=moved movedKm=33`, tên về Hà Nội qua `reverse`, một lần tải; Thủ công → không có yêu cầu vị trí) | — | **Task 5, 6 đạt, đã tick.** Một lần tải `why=noEntry` trước dòng `loc` là do điều phối sửa trực tiếp tọa độ `auto` làm mục đệm mất hiệu lực, không phải lỗi. Chưa chạy: xóa "−" rồi thêm lại, kéo thẻ từ "Tiện ích", xoay (`nosensor`), build không khóa, đồng hồ/lịch, bản xem trước, thu hồi quyền, chuyển Thủ công giữa lúc lấy vị trí |
| 2026-11-13 | 7 | Gỡ log `WXNET` (5d1b970, sửa tay vì commit log đã bị sửa tiếp); knowledgement `docs/knowledgements/WX-weather-cities.md` (luồng Mermaid, ngân sách lệnh, audit D1, bài học nhịp phút, danh sách chuỗi không dùng); điền bảng bằng chứng và kết quả kiểm; `assembleDebug` xanh sau khi gỡ log | (commit docs này) | `assembleRelease` xanh (2 phút 50 giây, R8 bật); chưa chuyển PR sang Ready (điều phối làm) |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Không đổi Room/`launcher_items` | `git diff <merge-base> --stat`: không có `data/layout/*`, `app/schemas/*`, manifest, gradle, file độc quyền nào; audit D1 ở plan (đọc code) | Đạt (đọc diff) |
| 2 | Chặn 2 giờ đúng bảng D4, theo từng địa điểm; 0 lệnh khi không có widget hiện; thêm/xóa/gán không gọi mạng | Live 4 (điều phối, bản 0890e62/e0ae214): mở Home + 10× Home trong ~2 phút, khởi động nguội với bộ nhớ đệm mới: 0 dòng `start`; `attempt` lùi 1h59 (chỉ `attempt`): 0 dòng; lùi 2h01: đúng 1 `start`/`done ok`; mất mạng + lùi 2h01: 1 `start`, `done result=NETWORK`, về mạng trong 15 phút: 0 dòng, lùi 15 phút 30 giây: 1 `start` ok; cả `attempt` và `snapshot.at` lùi 1h55: 0 dòng (ca 1h59 từng "lỗi" là do chờ nhịp phút vượt 2 giờ, xem knowledgement mục 8); thêm/xóa địa điểm ở Cài đặt: 0 dòng. **Chưa kiểm:** 429, 401 trên máy (chỉ đọc code) | Đạt (đo được); 401/429 chỉ đọc code |
| 3 | Đổi °C/°F không gọi mạng | Chỉ đọc code: đường đổi đơn vị (`units.unit.collect` → `tickNow`) không gọi `refreshIfStale` ngoài lần `prepare` như mọi nhịp, nên chỉ tải khi đến hạn; WU đã đo `attempt` giữ nguyên | Đạt (đọc code, WU đo) |
| 4 | Nâng cấp từ G3e: đọc được `weather.json` version 1 | Live 4 (bản f47aaad): cài đè lên cache thật của chủ dự án, widget vẫn hiện đúng thành phố và nhiệt độ ngay, không chớp "Chọn thành phố"; `no_backup` có `weather_places.json` (MANUAL, manual đã đặt, saved 0) và `weather.json`; ca sạch không crash | Đạt (đo được) |
| 5 | Lớp widget: `bindItem` mỗi lần bind, `clearWidget`, không cấp phát trong `draw`, bản mẫu không đọc ánh xạ, đồng hồ/lịch không đổi | Đọc code (điều phối rà); Live 4 (bản 68546b0): kéo A từ trang cuối (lật mép) về trang 1, giữ địa điểm; đổi cỡ lưới 4×6 → 4×5: A (item 124) reflow sang trang 2, `weather_places.json` còn `widgets = {124: …}`. **Chưa kiểm tay:** xóa bằng "−" rồi thêm lại, thêm bằng kéo thẻ từ "Tiện ích", đồng hồ/lịch, bản xem trước, xoay (Home `nosensor`) | Đạt phần đã đo; phần còn lại chỉ đọc code |
| 6 | Bấm widget mở bảng cho đúng widget; vẫn nuốt chạm trong edit mode; nhấn giữ vẫn vào edit mode | Live 4: bảng của A có radio Đà Nẵng, của B có "Vị trí hiện tại" (không lẫn); nút Home với bảng mở thì đóng. Edit mode/nhấn giữ: không đổi code (đọc diff) | Đạt (đo được); edit mode đọc code |
| 7 | Địa điểm bị xóa: widget rơi về "current", không crash, không mục ánh xạ mồ côi | Live 4: xóa Đà Nẵng ở Cài đặt: A về "Vị trí hiện tại"; bảng của A chỉ còn "current" + Huế. `remove()` dọn mục ánh xạ (đọc code) | Đạt |
| 8 | Giới hạn 3 và trùng địa điểm | Live 4: ở 3 địa điểm hàng "+ Thêm" mờ ghi "Đã đủ 3 địa điểm", bấm không mở gì; thêm lại Đà Nẵng bị từ chối (không hàng mới); thêm "Hà Nội" khi "current" cũng Hà Nội được chấp nhận (cố ý, ghi ở knowledgement); kết quả trùng tên/tỉnh/nước bị gộp | Đạt |
| 9 | 1.5: Tự động không quyền → trạng thái rõ; Thủ công không đòi quyền; đổi qua lại không mất giá trị | Live 4 (bản bdcdbfb): hàng Vị trí bật ở trang chủ; Tự động → "Đang lấy vị trí…" → tên nơi sau ~4,8 giây; `dumpsys location` thấy yêu cầu mạng thêm rồi gỡ sau ~2 giây; Thủ công ↔ Tự động giữ giá trị mỗi bên. **Chưa chạy:** từ chối/"không hỏi lại"/thu hồi quyền, tắt dịch vụ vị trí (chỉ đọc code) | Đạt phần đã đo |
| 10 | Không rò khóa/URL/tọa độ; listener vị trí gỡ; hộp thoại đóng khi Activity hủy | Live 4: logcat của app 0 dòng khóa/`appid`/`openweathermap`/`lat=` (các lần `appid=` duy nhất là dòng force-stop của ActivityManager); `dumpsys location` không sót yêu cầu; `grep` toàn diff: mọi `Log` mới chỉ ghi tên lớp ngoại lệ; log tạm `WXNET` đã gỡ (commit 5d1b970). Hộp thoại đóng ở `onDestroy`: đọc code | Đạt |
| 11 | `weather_places.json` và `weather.json` ở `noBackupFilesDir` | Đọc code (`WeatherPlacesStore`, `WeatherStore`); Live 4: `run-as … ls no_backup` thấy cả hai file | Đạt |
| 12 | Hồi quy Cài đặt: các màn dùng `SettingsRow.Value` không đổi | `titleText` có mặc định null, chỉ nhánh `if` mới trong `ValueHolder` (đọc diff); điều phối kiểm màn Thời tiết/Vị trí và trang chủ; các màn Chung/Tìm kiếm/Bộ biểu tượng **chưa mở riêng** để so | Đạt (đọc diff); chưa kiểm tay riêng |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: mục 14 ghi nhiều địa điểm (≤ 3), chọn theo widget, chính sách 2 giờ, 1.5 Tự động/Thủ công; mục 17 vẫn "Đóng băng" (thông báo là nhánh riêng, cảnh báo bỏ; ghi lý do: One Call 3.0 cần gói riêng). Dòng 27–29 vẫn ghi Open-Meteo trong khi dự án dùng OpenWeatherMap từ 2026-11-09: nên sửa.
- `project_structure.md`: thêm các file mới ở "Vùng file" (`WeatherPlaces*`, `CitySearch*`, `LocationSettings*`, `CurrentLocationUpdater`); `weather.json` version 2 và `weather_places.json` ở `noBackupFilesDir`.
- `room-schema.md`: **không đổi** (nhưng nên thêm một câu: cấu hình riêng của widget thời tiết nằm ngoài Room, và lý do, xem D1).
- `Figma-Draft.md`: đánh dấu 1.4 (địa điểm) và 1.5 đã làm; còn thiếu ở 1.4: thẻ "hiện tại + theo giờ", thông báo; ghi rằng cảnh báo bị bỏ.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (đề xuất chủ đề `widgets`: cấu hình riêng từng widget lưu ngoài Room theo `itemId` vì `AUTOINCREMENT`; `settings-and-data`: ánh xạ ngoài Room cần dọn khi xóa).
