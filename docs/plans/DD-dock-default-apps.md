# DD — Dock chọn sai app mặc định trên Oppo (sửa lỗi) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ kết quả trên máy. Tối đa 5 vòng mỗi Task. Dự án **không viết test tự động** (AGENTS.md, chốt 2026-10-16). **Agent thực thi không dùng máy thật và không chạy `adb`**: điều phối chạy mọi lệnh trên máy. **Máy kiểm của DD là Oppo A3x** (Android 14). **Vsmart Live 4 hiện không dùng được** (chủ dự án bận dự án khác): mọi bước trên Live 4 trong plan này là tùy chọn, ghi "chỉ khi chủ dự án cho dùng Live 4". Lệnh viết cho PowerShell.

**Nhánh:** `fix/dd-dock-default-apps` · **Worktree:** `E:\SourceTree\IosLauncher-wt\DD-dock-default-apps` · **PR:** #17 (draft) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09
**Trạng thái:** Xong, chờ chủ dự án xem và merge
**Khóa:** cần Oppo (Task 1, Task 3) · không khóa schema Room · không đụng file nóng độc quyền (theo phương án khuyến nghị ở D5)

**Goal:** Lần dựng bố cục đầu tiên (seed) trên Oppo A3x phải đặt vào dock đúng 4 app Điện thoại, Tin nhắn, Trình duyệt, Máy ảnh, thay vì Điện thoại, Chrome, một app giao diện cuộc gọi và một app tài liệu như hiện nay. Live 4 (đang đúng) không được đổi kết quả.

**Architecture:** Giữ nguyên chỗ gọi (`LayoutReconciler.seedIfEmpty` → `PickDefaultDockUseCase`). Đổi cổng `DefaultAppResolverPort` từ "một component cho mỗi vai trò" sang "danh sách package ứng viên có thứ tự cho mỗi vai trò". `DefaultAppResolver` (tầng data) dựng danh sách đó theo một chuỗi dự phòng riêng cho từng vai trò; `PickDefaultDockUseCase` (tầng domain) lấy ứng viên đầu tiên có trong danh sách app và chưa nằm trong dock, hết ứng viên mới bù bằng app đầu bảng chữ cái như hôm nay.

**Tech Stack:** không thêm dependency. API công khai của framework: `PackageManager.resolveActivity`/`queryIntentActivities`, `TelecomManager.getDefaultDialerPackage` (API 23), `TelecomManager.getSystemDialerPackage` (API 29), `Telephony.Sms.getDefaultSmsPackage` (API 19), `ApplicationInfo.FLAG_SYSTEM`.

**Spec:** yêu cầu của chủ dự án 2026-11-09: "fix the bug read icon for default apps in dock wrong - although it's fine on live 4, the oppo is still not enough if apply the same logic". Dòng DD ở `MultiAgent-Workflow.md` mục 9 (xếp hàng 2026-11-09). Liên quan: AGENTS.md mục "Dock mặc định (M3 Task 4)", B1 (`startSystemHandler`), M8d (`SystemSettingsRepositoryImpl`).

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (số đo và code, có nhãn nguồn)

1. **Số đo của điều phối trên Oppo A3x, 2026-11-09** (đo bằng `cmd package resolve-activity`, `query-activities`, `cmd role get-role-holders` từ shell, **không phải từ trong app**):

   | Vai trò | Intent | `resolve-activity` trả | Số trình xử lý | Người giữ role |
   |---|---|---|---|---|
   | PHONE | `ACTION_DIAL` | `com.google.android.dialer/...MainActivity` | 13 | DIALER = `com.google.android.dialer` |
   | MESSAGES | `ACTION_MAIN` + `CATEGORY_APP_MESSAGING` | `android/com.android.internal.app.ResolverActivity` | 2: `com.android.mms/.ui.ConversationList`, `com.google.android.apps.messaging/.ui.ConversationListActivity` | SMS = `com.google.android.apps.messaging` |
   | BROWSER | `ACTION_VIEW` http + `BROWSABLE` | `android/com.android.internal.app.ResolverActivity` | 2: `com.iptv.smartplayer.onlinetv/...WebBrowserActivity`, `com.android.chrome/...IntentDispatcher` | BROWSER = `com.android.chrome` |
   | CAMERA | `STILL_IMAGE_CAMERA` | `android/com.android.internal.app.ResolverActivity` | 3: `com.oplus.camera/.component.CameraImageActivity`, `com.gpsmapcamera...`, `photo.android.hd.camera...` | (không có role) |

   Dock hiện tại trên Oppo: Điện thoại, Chrome, một app giao diện cuộc gọi, một app tài liệu. Thiếu Tin nhắn và Máy ảnh.

2. **Nguyên nhân gốc (đọc code, khớp số đo):** `DefaultAppResolver.resolve` chỉ bỏ kết quả có package bắt đầu bằng `android.permission`. Hộp chọn app của hệ thống là package `android`, class `com.android.internal.app.ResolverActivity`, nên lọt qua và được trả như một app thật. `PickDefaultDockUseCase` tìm app có package `android` trong danh sách app, không thấy, bỏ vai trò đó; ba vai trò rơi hết và ba ô được bù bằng app đầu bảng chữ cái (Chrome nằm trong đó vì nhãn bắt đầu bằng "C"; app giao diện cuộc gọi và app tài liệu cũng vậy). Live 4 đúng vì mỗi vai trò chỉ có **một** trình xử lý nên hệ thống trả thẳng app đó (đo M3 Task 4, AGENTS.md).

3. **Chỉ lọc hộp chọn thôi chưa đủ.** Lọc xong thì MESSAGES/BROWSER/CAMERA vẫn không có kết quả; phải có cách khác để biết app nào là "đúng". Máy có app ngoài đăng ký cùng intent (app IPTV nhận `VIEW http`, hai app máy ảnh ngoài nhận `STILL_IMAGE_CAMERA`), nên "lấy trình xử lý đầu tiên" có thể ra app rác. Đúng bài học B1 và M8d: `ACTION_*` có thể có app ngoài đăng ký.

4. **Dock chỉ được chọn một lần, lúc seed** (đọc code): `LayoutReconciler.seedIfEmpty` gọi `pickDefaultDock` chỉ khi `itemDao.count() == 0`. Sửa resolver **không** đổi dock đã seed, kể cả dock sai trên Oppo của chủ dự án. Xem nhóm quyết định C.

5. **Ràng buộc dự án:** `LayoutReconciler.kt` là file nóng độc quyền (MultiAgent-Workflow 5.2); `AndroidManifest.xml` dùng chung có kiểm soát; luật phụ thuộc `ui → domain ← data` (domain không được biết `PackageManager`); không thêm quyền, không `QUERY_ALL_PACKAGES` (AGENTS.md M2); `pickDefaultDock` chạy **bên trong** `db.withTransaction` nên mọi IPC thêm vào đều kéo dài transaction lúc seed.

### Nhóm A: nhận ra hộp chọn

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A1. Danh sách tên đen | Coi là hộp chọn nếu package là `android` hoặc `com.android.intentresolver`, hoặc tên class kết thúc bằng `ResolverActivity`/`ChooserActivity`, hoặc package bắt đầu bằng `android.permission` (luật cũ) | Đơn giản; khớp đúng cái đã đo (`android/...ResolverActivity`); B1 và M8d đã dùng `name != ResolverActivity` | ROM có hộp chọn mang tên khác (OEM tự viết) sẽ lọt qua. `com.android.intentresolver` là module hộp chọn từ Android 13 (đọc tài liệu AOSP, chưa đo trên máy dự án) | Chọn kèm A2 |
| A2. Kiểm thành viên | Kết quả của `resolveActivity` chỉ được coi là mặc định thật nếu component đó **nằm trong** danh sách `queryIntentActivities` của cùng intent và cùng cờ | Bắt được mọi kiểu hộp chọn, kể cả của OEM, vì hộp chọn không phải một trình xử lý của intent (số đo shell: danh sách trình xử lý không chứa `ResolverActivity`) | Thêm một IPC mỗi vai trò (danh sách đó đằng nào cũng cần cho bước dự phòng nên không tốn thêm); ROM lạ có thể trả component đúng mà không nằm trong danh sách (chưa thấy, suy luận) | **Chọn: A2 là điều kiện chính, A1 là lớp bảo hiểm và để log ghi rõ lý do** |
| A3. So với role holder | Hỏi `RoleManager.getRoleHolders` | Đúng ý nghĩa nhất | **Không gọi được:** `getRoleHolders` là `@SystemApi` cần `MANAGE_ROLE_HOLDERS`. Tài liệu công khai của `RoleManager` chỉ có `isRoleAvailable`, `isRoleHeld` (chỉ hỏi app gọi có giữ role không) và `createRequestRoleIntent` (đọc tài liệu developer.android.com, 2026-11-09) | Không |

### Nhóm B: chuỗi dự phòng cho từng vai trò

Các nguồn có thể dùng (nhãn nguồn từng dòng):

| Nguồn | Vai trò | Công khai? | Ghi chú |
|---|---|---|---|
| `TelecomManager.getDefaultDialerPackage()` | PHONE | Có, API 23, không cần quyền (đọc tài liệu) | Trả package hoặc null. Máy không có telephony có thể không có dịch vụ Telecom (suy luận) |
| `TelecomManager.getSystemDialerPackage()` | PHONE | Có, API 29, không cần quyền (đọc tài liệu) | App điện thoại cài sẵn; minSdk 26 nên phải kiểm `SDK_INT >= 29` |
| `Telephony.Sms.getDefaultSmsPackage(context)` | MESSAGES | Có, API 19, không cần quyền (đọc tài liệu) | **Tài liệu ghi:** từ Android 11 app phải khai `<queries><intent><action android:name="android.provider.Telephony.SMS_DELIVER"/></intent></queries>` để dùng hàm này. Dự án chưa khai. Có thể không cần vì app SMS có activity LAUNCHER nên đã thấy được nhờ khối MAIN/LAUNCHER (bài học M8d: `<queries>` mở tầm nhìn theo package, không theo action), nhưng đó là suy luận: **Task 1 đo** |
| `resolveActivity(..., MATCH_DEFAULT_ONLY)` không phải hộp chọn | cả 4 | Có | Mặc định do người dùng đặt (kể cả app ngoài), hoặc trình xử lý duy nhất (Live 4) |
| `resolveActivity(MAIN + CATEGORY_APP_BROWSER)` | BROWSER | Có | Tài liệu `ROLE_BROWSER` trỏ tới `CATEGORY_APP_BROWSER`; chưa biết hệ thống có trả role holder qua intent này không. **Chỉ đo ở Task 1**, chỉ đưa vào chuỗi nếu số đo cho thấy nó trả Chrome |
| Trình xử lý hệ thống trong `queryIntentActivities` (`FLAG_SYSTEM`, mẫu B1/M8d) | cả 4 | Có | App hệ thống đã cập nhật qua store vẫn mang `FLAG_SYSTEM` kèm `FLAG_UPDATED_SYSTEM_APP` (đọc tài liệu, suy luận cho Chrome trên Oppo; **Task 1 đo cờ của Chrome**). Nếu có nhiều trình xử lý hệ thống (Oppo MESSAGES có thể có 2: `com.android.mms` và Google Messages), xếp cái có trong danh sách package đã biết lên trước, còn lại giữ thứ tự hệ thống trả |
| Danh sách package đã biết theo vai trò | cả 4 | — | Chỉ là suy luận, trừ các package đã đo (Oppo shell 2026-11-09, Live 4 M3) |
| App đầu bảng chữ cái chưa nằm trong dock | cả 4 | — | Hành vi hôm nay, giữ làm bước cuối |

So các cách ghép:

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| B1. Chỉ sửa lọc hộp chọn | A1/A2, còn lại như hôm nay | Diff nhỏ nhất | Oppo vẫn hỏng: 3 vai trò rơi về bảng chữ cái (mục Vấn đề 3) | Không |
| B2. Chỉ lọc hệ thống (mẫu B1) cho mọi vai trò | Bỏ hộp chọn; `FLAG_SYSTEM` | Một luật cho cả 4; đã chạy tốt ở B1, M8d | Bỏ qua mặc định thật của người dùng khi họ chọn app ngoài (vd Firefox); MESSAGES trên Oppo có thể có 2 app hệ thống, chọn theo thứ tự hệ thống trả thì có thể ra `com.android.mms` thay vì Google Messages mà người dùng đang dùng làm mặc định | Không |
| B3. Chỉ danh sách package đã biết | Bảng tên package theo vai trò | Dễ đoán | Danh sách là suy luận, thiếu ROM nào là hỏng (lý do B1 không chọn cách này làm đường chính) | Không (giữ làm một bậc) |
| **B4. Chuỗi theo vai trò, nguồn chắc nhất trước** | (1) API công khai cho mặc định thật nếu vai trò có; (2) `resolveActivity` khi không phải hộp chọn; (3) trình xử lý hệ thống; (4) package đã biết; (5) bảng chữ cái | Tôn trọng lựa chọn của người dùng; mỗi bậc chỉ dùng khi bậc trên không có; Live 4 vẫn đi bậc (1)/(2) như hôm nay | Nhiều code hơn (~80 dòng); thêm tối đa ~6 IPC lúc seed (một lần mỗi lần cài); cần Task 1 đo trước | **Chọn** |

**Chuỗi đã chọn cho từng vai trò** (D2):

| Vai trò | (1) Mặc định qua API | (2) `resolveActivity` không phải hộp chọn | (3) Trình xử lý hệ thống | (4) Package đã biết | Kỳ vọng trên Oppo |
|---|---|---|---|---|---|
| PHONE | `getDefaultDialerPackage()`, rồi `getSystemDialerPackage()` (API 29+) | `ACTION_DIAL` | `ACTION_DIAL` lọc `FLAG_SYSTEM` | `com.google.android.dialer` (đo), `com.vinsmart.android.dialer` (đo), `com.android.dialer`, `com.samsung.android.dialer` | `com.google.android.dialer` ở bậc (1) |
| MESSAGES | `Telephony.Sms.getDefaultSmsPackage()` | `MAIN` + `APP_MESSAGING` | như (2), lọc `FLAG_SYSTEM` | `com.google.android.apps.messaging` (đo), `com.android.mms` (đo), `com.vinsmart.messaging` (đo), `com.samsung.android.messaging`, `com.android.messaging` | `com.google.android.apps.messaging` ở bậc (1) |
| BROWSER | không có API công khai (role BROWSER là system API) | `VIEW http` + `BROWSABLE` | như (2), lọc `FLAG_SYSTEM` | `com.android.chrome` (đo), `com.sec.android.app.sbrowser`, `com.heytap.browser`, `com.android.browser` | `com.android.chrome` ở bậc (3) nếu Chrome mang `FLAG_SYSTEM`, không thì bậc (4) |
| CAMERA | không có (không có role máy ảnh) | `STILL_IMAGE_CAMERA` | như (2), lọc `FLAG_SYSTEM` | `com.oplus.camera` (đo), `com.vinsmart.camera` (đo), `com.google.android.GoogleCamera`, `com.sec.android.app.camera`, `com.android.camera2`, `com.android.camera` | `com.oplus.camera` ở bậc (3) |

Bậc (5) bảng chữ cái nằm ở use case, chung cho cả 4 vai trò.

**Vì sao chia data / domain như vậy (D3):** cổng mới `candidates(role): List<String>` trả **tên package** có thứ tự, không trả `ComponentKey`. Use case đằng nào cũng chỉ so theo `packageName` (bài học M3: `resolveActivity` trả activity trung gian khác activity LAUNCHER) và luôn mở app bằng khóa LAUNCHER của `AppItem`, nên `className` từ resolver đã thừa. Trả cả danh sách thay vì một kết quả giúp use case bỏ qua ứng viên không có trong `apps` (bị ẩn, ở profile khác, không có activity LAUNCHER) hoặc đã nằm trong dock, rồi thử ứng viên kế, thay vì rơi thẳng xuống bảng chữ cái. Phương án khác đã xét: để use case gọi nhiều hàm cổng (`defaultPackage`, `systemHandlers`, `knownPackages`) rồi tự ghép chuỗi. Bỏ vì domain sẽ phải biết khái niệm Android ("app hệ thống", "hộp chọn") và thứ tự chuỗi bị chia ra hai tầng.

**Tầm nhìn package (`<queries>`) cho từng intent** (D4):

| Intent / API | Khai báo hiện có | Đánh giá |
|---|---|---|
| `ACTION_DIAL` | không có khối riêng | M3 đo được trên Live 4. Các app điện thoại đều có activity LAUNCHER nên đã thấy được nhờ khối MAIN/LAUNCHER (suy luận theo bài học M8d). Task 1 đo số trình xử lý thấy được từ trong app |
| `MAIN` + `APP_MESSAGING` | không có khối riêng | Như trên |
| `VIEW http` + `BROWSABLE` | có (M3) | Đủ |
| `STILL_IMAGE_CAMERA` | có (M3) | Đủ |
| `MAIN` + `APP_BROWSER` (chỉ đo) | không | Chỉ đo ở Task 1; app trình duyệt đã thấy được nhờ khối `VIEW http` |
| `getDefaultDialerPackage` | không cần theo tài liệu | Trả tên package; Task 1 đo có ra null từ trong app không |
| `getDefaultSmsPackage` | **tài liệu đòi khối `SMS_DELIVER`** | Task 1 đo. Ra null thì Task 2 thêm khối `<intent><action android:name="android.provider.Telephony.SMS_DELIVER"/></intent>` vào `<queries>` (khối riêng ở cuối, dùng chung có kiểm soát), rồi điều phối đo lại |

Package được so với `apps` từ `LauncherApps`, vốn đã thấy mọi app có activity LAUNCHER (đo M2: Oppo 344/344). Nên ứng viên nào "không thấy được" thì cũng không thể đặt vào dock, không cần mở rộng tầm nhìn hơn nữa.

### Nhóm C: máy đã seed (như Oppo của chủ dự án)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **C-a. Chỉ sửa cho lần seed mới** | Không đụng dock đã có. Máy đã seed tự sửa bằng tay (kéo Tin nhắn, Máy ảnh vào dock, chế độ chỉnh sửa M7 Task 6) hoặc giữ bố cục seed mới mà Task 3 tạo ra (xem Q2) | Không bao giờ ghi đè lựa chọn của người dùng; không đụng `LayoutReconciler` (file nóng độc quyền), không khóa DataStore mới; app chưa phát hành nên "máy đã seed" hiện chỉ là hai máy thử | Oppo của chủ dự án vẫn sai cho tới khi sửa tay hoặc chọn giữ bố cục mới | **Khuyến nghị** |
| C-b. Một lần chọn lại tự động | Lần mở đầu sau khi cập nhật: ô dock nào đang chứa app mà lỗi cũ đã chọn thì thay bằng app của chuỗi mới; cờ "đã chạy" trong DataStore | Oppo tự đúng sau khi cài | **Không phân biệt được** ô do lỗi cũ bù với ô người dùng tự đặt: phải đoán bằng cách chạy lại thuật toán cũ, mà danh sách app đã đổi từ lúc seed (cài app mới, đổi nhãn) nên kết quả không còn khớp; đoán sai là ghi đè dock của người dùng. Cần sửa `LayoutReconciler` (độc quyền) và `SettingsKeys` (dùng chung) | Không |
| C-c. Hành động trong Cài đặt "Đặt lại dock mặc định" | Một hàng trong Cài đặt, hỏi xác nhận, thay 4 ô dock bằng kết quả chuỗi mới; app cũ trong dock về ô trống trên lưới | Người dùng tự quyết; dùng được cả sau này khi đổi app mặc định | Cần hàm mới trong `LayoutReconciler` (độc quyền), hàng Cài đặt + chuỗi (dùng chung), xử lý app bị đẩy khỏi dock (đi về đâu, trùng hàng); khoảng 2 Task nữa, có kéo dữ liệu bố cục nên phải kiểm kỹ | Không ở DD; làm nhánh riêng nếu chủ dự án muốn (Q1) |

**Đây là quyết định của chủ dự án (Q1).** Khuyến nghị C-a vì: không có cách an toàn để biết ô nào là "lỗi cũ" (C-b), và C-c là một tính năng mới chạm file nóng, không cần cho mục tiêu sửa lỗi. Bố cục trên Oppo của chủ dự án được xử lý bằng lựa chọn ở cuối Task 3 (Q2), không bằng code.

### Đánh đổi chấp nhận

- Dock đã seed không tự sửa (C-a).
- Lúc seed, transaction Room kéo dài thêm vài IPC (ước tính dưới 100 ms trên Oppo, chưa đo; Task 1 đo tổng thời gian chuỗi). Chỉ một lần mỗi lần cài hoặc xóa dữ liệu. Dời `pickDefaultDock` ra ngoài transaction thì phải sửa `LayoutReconciler` (độc quyền), không đáng nếu số đo nhỏ.
- BROWSER không có API công khai cho mặc định thật: nếu người dùng chọn trình duyệt ngoài làm mặc định, bậc (2) bắt được (resolve trả thẳng app đó); nếu ROM vẫn hiện hộp chọn dù đã có role holder (như số đo shell trên Oppo: role BROWSER là Chrome nhưng resolve vẫn ra hộp chọn), ta lấy app hệ thống, có thể khác role holder.
- Danh sách package đã biết là suy luận ngoài các package đã đo.
- **Xét lại khi:** Task 1 cho thấy API công khai trả null từ trong app (đổi bậc hoặc thêm `<queries>`); có ROM mà app máy ảnh/trình duyệt hệ thống không mang `FLAG_SYSTEM`; chủ dự án muốn C-c.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Nhận hộp chọn bằng A2 (không nằm trong `queryIntentActivities`) cộng A1 (package `android`/`com.android.intentresolver`, class `ResolverActivity`/`ChooserActivity`, tiền tố `android.permission`) | Claude đề xuất; chủ dự án duyệt 2026-11-09 |
| D2 | Chuỗi B4 theo vai trò như bảng trên; bậc (5) bảng chữ cái giữ ở use case | Claude đề xuất; chủ dự án duyệt 2026-11-09 |
| D3 | Cổng mới `DefaultAppResolverPort.candidates(role): List<String>` (tên package có thứ tự); bỏ `resolve(role): ComponentKey?` (chỉ `PickDefaultDockUseCase` dùng, đã grep) | Claude đề xuất; chủ dự án duyệt 2026-11-09 |
| D4 | Không thêm `<queries>` trừ khi Task 1 cho thấy `getDefaultSmsPackage` trả null từ trong app; khi đó thêm đúng một khối `SMS_DELIVER` | Tài liệu `Telephony.Sms`. Duyệt 2026-11-09; số đo lấy từ dòng log DDM của Task 2 (Task 1 gộp vào Task 3) |
| D5 | Máy đã seed: phương án C-a (chỉ sửa seed mới), không code chọn lại | Chủ dự án chốt Q1 = C-a (2026-11-09) |
| D6 | Không gọi `RoleManager.getRoleHolders` (system API), không `QUERY_ALL_PACKAGES`, không thêm quyền | Tài liệu RoleManager; AGENTS.md M2 |
| D7 | Mỗi lần seed, resolver ghi **một** dòng `Log.i` mỗi vai trò (vai trò, bậc nào ra kết quả, danh sách ứng viên); không log thời gian | Seed chỉ chạy một lần mỗi cài; Oppo bỏ dòng khi in dồn (bài học M8d) nên mỗi vai trò gói vào một dòng. Điều phối có thể bảo bỏ khi review |
| D8 | Không thêm test tự động | AGENTS.md; duyệt 2026-11-09 |
| Q1 | C-a: chỉ sửa seed mới, không chọn lại, không hành động trong Cài đặt | Chủ dự án, 2026-11-09 |
| Q2 | Sau khi điều phối seed thử trên Oppo, **khôi phục bố cục cũ** của chủ dự án (có kiểm hash) | Chủ dự án, 2026-11-09 |
| Q3 | Giữ bù bảng chữ cái khi vai trò không tìm được app | Chủ dự án, 2026-11-09 |

## Câu hỏi cho chủ dự án

- **Q1 (D5):** Máy đã seed: chọn C-a (khuyến nghị: chỉ sửa seed mới, bố cục cũ tự kéo tay), C-b (tự chọn lại một lần, Claude không khuyến nghị vì có thể ghi đè dock của bạn), hay C-c (thêm nút "Đặt lại dock mặc định" trong Cài đặt, làm nhánh riêng sau DD)?
- **Q2 (Task 3):** Sau khi điều phối thử seed mới trên Oppo, bạn muốn **khôi phục bố cục cũ** (mặc định, có kiểm hash) hay **giữ bố cục seed mới** (dock đúng ngay, nhưng mất sắp xếp trang, thư mục, widget, app ẩn hiện có)?
- **Q3:** Khi một vai trò không tìm được app nào qua cả 4 bậc, giữ hành vi hôm nay (bù bằng app đầu bảng chữ cái để dock luôn đủ 4 ô) hay để trống ô đó? Claude đề xuất giữ như hôm nay; với chuỗi mới, ca này chỉ còn xảy ra trên máy không có app loại đó.

## Ngoài phạm vi / ghi để sau

- C-b, C-c (xem Q1).
- Dời `pickDefaultDock` ra ngoài `db.withTransaction` (cần `LayoutReconciler`, chỉ làm nếu Task 1 đo chuỗi chậm).
- Chọn bản ở profile chính khi app có cả bản work profile (`byPackage[...]?.firstOrNull()` giữ như hôm nay).
- Đổi dock khi người dùng đổi app mặc định sau này (vd đổi SMS mặc định): hệ thống không báo launcher, và tự đổi dock là ghi đè lựa chọn của người dùng.
- Mở rộng chuỗi cho Đồng hồ/Lịch (B1 đã xử lý ở `LauncherAppsDataSource`).

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/data/apps/DefaultAppResolver.kt` | Sửa: viết lại thân lớp (nhận hộp chọn, chuỗi theo vai trò, danh sách package đã biết, log D7) | Không. Vùng `data/apps/*` từng là của LC/IC (đã merge); hiện không nhánh nào đang mở sửa file này |
| `app/src/main/java/gambi/global/os26launcher/domain/repository/DefaultAppResolverPort.kt` | Sửa chữ ký (D3) | Không |
| `app/src/main/java/gambi/global/os26launcher/domain/usecase/PickDefaultDockUseCase.kt` | Sửa vòng chọn theo ứng viên | Không |
| `app/src/main/AndroidManifest.xml` | **Chỉ sửa nếu Task 1 cần** (D4): thêm một khối `<intent>` `SMS_DELIVER` ở cuối `<queries>` | Dùng chung có kiểm soát; chỉ thêm. RB (xếp hàng) có thể sửa manifest: nhánh merge sau tự rebase |
| `docs/plans/DD-dock-default-apps.md`, `docs/knowledgements/DD-dock-default-apps.md` | Tạo | Không |

**Không sửa:** `data/layout/LayoutReconciler.kt` (độc quyền; gọi `pickDefaultDock(apps, slots)` giữ nguyên chữ ký), `di/RepositoryModule.kt` (binding giữ nguyên), `domain/model/DockRole.kt`, `data/apps/LauncherAppsDataSource.kt`, mọi file `ui/`, `SettingsKeys`. Nếu Q1 chọn C-b hoặc C-c, plan phải viết lại phần đó và điều phối cấp `LayoutReconciler` trước khi code.

## Global Constraints

- Không ném lỗi ra khỏi resolver: mỗi bậc của chuỗi bọc `try/catch (RuntimeException)` riêng (`SecurityException`, dịch vụ Telecom không có, `NullPointerException` của ROM...), `Log.w` rồi sang bậc kế. Một bậc lỗi không được làm mất các bậc sau.
- Không bao giờ coi hộp chọn là app (D1), ở mọi bậc: bậc (3) cũng phải loại hộp chọn như B1/M8d.
- Không bao giờ mở component mà resolver trả: dock luôn mở bằng khóa LAUNCHER của `AppItem` (bài học M3, không đổi).
- `getSystemDialerPackage` chỉ gọi khi `Build.VERSION.SDK_INT >= 29` (minSdk 26).
- Không đổi hành vi khi một vai trò có đúng một trình xử lý (Live 4): bậc (1)/(2) phải ra cùng package như hôm nay.
- Không dùng API system (`RoleManager.getRoleHolders`), không quyền mới, không `QUERY_ALL_PACKAGES`.
- Không đổi schema Room, không khóa DataStore mới, không đụng dock đã seed.
- Domain không import `android.*` (`PickDefaultDockUseCase`, `DefaultAppResolverPort` giữ thuần Kotlin).
- Bản đo của Task 1 là bản sao bỏ đi, không commit, không đẩy lên nhánh (bài học AN: công tắc đo tạm không vào `main`).
- Không sửa tài liệu chung (AGENTS.md, Launcher-Features.md, project_structure.md, room-schema.md, MultiAgent-Workflow.md).
- Build: `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"`. Không `gradlew --stop`. Commit/PR tiếng Anh, docs tiếng Việt. Push thường, không force (MultiAgent-Workflow mục 12.12).

## Review Focus

1. Hộp chọn (`android/...ResolverActivity`, `ChooserActivity`, `com.android.intentresolver`, mọi component không nằm trong danh sách trình xử lý) không bao giờ thành ứng viên, ở bậc nào cũng vậy.
2. Mặc định thật của người dùng thắng: PHONE theo `getDefaultDialerPackage`, MESSAGES theo `getDefaultSmsPackage`, mọi vai trò theo `resolveActivity` khi đó là trình xử lý thật (kể cả app ngoài).
3. Thứ tự chuỗi đúng bảng D2; danh sách ứng viên không trùng package.
4. Use case: ứng viên không có trong `apps` hoặc đã trong dock thì thử ứng viên kế, không rơi thẳng xuống bảng chữ cái; không trả quá `slots`, không trùng; `slots <= 0` trả rỗng như cũ.
5. Live 4 (một trình xử lý mỗi vai trò) ra cùng 4 package như M3 (đọc code; máy chỉ khi chủ dự án cho dùng Live 4).
6. Không đường code nào gọi `pickDefaultDock` ngoài `seedIfEmpty` (grep): dock đã seed không bị đổi.
7. Một bậc ném lỗi không làm mất các bậc sau; `getSystemDialerPackage` có kiểm API 29.
8. Tầm nhìn package: mỗi intent/API dùng đều có số đo Task 1 từ trong app; `<queries>` chỉ thêm nếu số đo đòi (D4).
9. Tổng thời gian chuỗi lúc seed (Task 1) chấp nhận được trong transaction.
10. Chỉ sửa trong Vùng file; domain không import `android.*`.

---

### Task 1: Đo từ trong app trên Oppo (điều phối chạy, bản sao bỏ đi, chưa code)

**Bắt buộc kiểm tay:** có — giả định chỉ máy thật trả lời được (mục 4.2): API công khai trả gì **từ trong app** khi có lọc tầm nhìn package (số đo shell không chịu lọc này), Chrome có `FLAG_SYSTEM` không, cần `<queries>` `SMS_DELIVER` không. Sai thì chuỗi D2 và Vùng file đổi. Kèm chạy thử sao lưu/khôi phục mà Task 3 dựa vào.
**Máy:** Oppo A3x. Không dùng Live 4.
**Files:** không file nào của nhánh (bản sao bỏ đi).
**Interfaces:** Produces: bảng số đo theo vai trò, kết luận cho D1, D2, D4 và Q2/Task 3.

- [ ] **Step 1: Chuẩn bị bản đo.** Điều phối chép worktree sang một thư mục bỏ đi (vd `E:\SourceTree\IosLauncher-wt\DD-probe`, kèm `local.properties`), thêm một đoạn đo tạm chạy **một lần mỗi lần mở app**, trên luồng nền, không cần seed (gợi ý: trong `LauncherApp.onCreate`, `Thread { ... }.start()`), để không phải xóa bố cục. Mỗi vai trò in **một** dòng `Log.i` tag `DDPROBE` (Oppo cắt log in dồn), hoặc ghi ra file `no_backup/dd_probe.txt` rồi đọc bằng `run-as cat`. Nội dung từng vai trò:
  - (a) `resolveActivity(intent, MATCH_DEFAULT_ONLY)`: package/class, hoặc null;
  - (b) `queryIntentActivities(intent, MATCH_DEFAULT_ONLY)` và `queryIntentActivities(intent, 0)`: số lượng; với từng trình xử lý: package/class, có `FLAG_SYSTEM`, có `FLAG_UPDATED_SYSTEM_APP`; (a) có nằm trong (b) không;
  - (c) package đó có trong `LauncherApps.getActivityList(pkg, myUserHandle)` không (rỗng = không thể vào dock);
  - PHONE thêm: `TelecomManager.getDefaultDialerPackage()`, `getSystemDialerPackage()`;
  - MESSAGES thêm: `Telephony.Sms.getDefaultSmsPackage(context)`;
  - BROWSER thêm: `resolveActivity(Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_BROWSER), MATCH_DEFAULT_ONLY)` và `queryIntentActivities` của intent đó;
  - một dòng cuối: tổng thời gian (ms) chạy cả 4 vai trò, đo bằng `SystemClock.elapsedRealtime()`.
  Intent phải **giống hệt** `DefaultAppResolver.intentFor` hiện tại (`http://example.com`, `CATEGORY_BROWSABLE`...).
- [ ] **Step 2: Chạy.** Build bản đo, `adb install -r` (giữ dữ liệu, cùng khóa ký debug, bố cục không đổi vì không seed), `am force-stop`, mở app, đọc `adb logcat -d -s DDPROBE`. Chạy 2 lượt để chắc số không đổi. Ghi bảng vào Nhật ký.
- [ ] **Step 3: Nếu `getDefaultSmsPackage` trả null:** thêm vào bản đo khối `<queries>` `SMS_DELIVER`, cài, đo lại dòng MESSAGES. Kết quả quyết D4.
- [ ] **Step 4: Chạy thử sao lưu/khôi phục, không đổi gì** (xem mục "Quy trình sao lưu và khôi phục" bên dưới, bước S1–S4 rồi R1–R4, **bỏ bước X**): chứng minh trên chính Oppo rằng `run-as tar` có, file tar không bị PowerShell làm hỏng, và hash sau khôi phục trùng hash trước. Không đạt thì **dừng**, không làm Task 3.
- [ ] **Step 5: Cài lại bản đang dùng** (bản `main` hoặc bản chủ dự án đang chạy) để không để bản đo trên máy; xóa thư mục bản sao (thư mục ngoài repo do điều phối tạo, không phải file dự án).
- [ ] **Step 6: Kết luận, gửi agent:** cho từng vai trò, bậc nào sẽ ra kết quả; Chrome có `FLAG_SYSTEM` không; có cần `SMS_DELIVER` không; `MAIN`+`APP_BROWSER` có ích không (có thì agent đề xuất thêm vào bậc (2) của BROWSER và chờ điều phối đồng ý); tổng thời gian chuỗi. Nếu tổng > 200 ms: dừng, báo chủ dự án trước Task 2 (cân nhắc dời ra ngoài transaction, cần `LayoutReconciler`).

### Task 2: Cổng mới, chuỗi theo vai trò, use case

**Bắt buộc kiểm tay:** không (kiểm ở Task 3).
**Files:** Sửa `domain/repository/DefaultAppResolverPort.kt`, `data/apps/DefaultAppResolver.kt`, `domain/usecase/PickDefaultDockUseCase.kt`; `AndroidManifest.xml` chỉ khi Task 1 Step 3 cần.
**Interfaces:** Consumes: kết luận Task 1. Produces: `DefaultAppResolverPort.candidates(role: DockRole): List<String>`; `PickDefaultDockUseCase.invoke(apps, slots)` giữ nguyên chữ ký (nên `LayoutReconciler` không đổi).

- [ ] **Step 1: Cổng.** Thay `resolve(role): ComponentKey?` bằng `candidates(role): List<String>`: tên package, thứ tự từ chắc nhất tới kém nhất, không trùng, có thể rỗng. KDoc nói rõ: không phải component để mở; use case tự lọc theo `apps`.
- [ ] **Step 2: Nhận hộp chọn (D1).** Trong resolver, một hàm riêng tư nhận kết quả `resolveActivity` và danh sách trình xử lý của cùng intent, trả "mặc định thật" hoặc null: null nếu kết quả null, nằm trong danh sách tên đen A1, hoặc không trùng (package + class) với phần tử nào của danh sách trình xử lý. Danh sách trình xử lý lấy một lần mỗi vai trò bằng `queryIntentActivities(intent, MATCH_DEFAULT_ONLY)` và **dùng lại** cho bậc (3).
- [ ] **Step 3: Chuỗi theo vai trò (D2).** Dựng danh sách theo bảng D2: (1) API công khai (PHONE: hai hàm Telecom, `getSystemDialerPackage` chỉ khi API ≥ 29; MESSAGES: `getDefaultSmsPackage`); (2) mặc định thật từ Step 2; (3) trình xử lý không phải hộp chọn có `FLAG_SYSTEM`, xếp package có trong danh sách đã biết lên trước, phần còn lại giữ thứ tự hệ thống; (4) danh sách đã biết của vai trò. Mỗi bậc một `try/catch (RuntimeException)`. Bỏ trùng giữ lần xuất hiện đầu (`LinkedHashSet`). Danh sách package đã biết để trong `companion object`, mỗi dòng ghi nguồn: "đo Oppo shell 2026-11-09", "đo Live 4 M3", hoặc "suy luận".
- [ ] **Step 4: Log (D7).** Mỗi vai trò một dòng `Log.i(TAG, "role=MESSAGES src=SMS_DEFAULT cands=[...]")`: `src` là bậc cho ứng viên đầu tiên. Không log thời gian.
- [ ] **Step 5: Use case.** Với mỗi vai trò theo `ROLE_ORDER`: duyệt `candidates(role)`, lấy package đầu tiên có trong `byPackage` mà `AppItem` chưa nằm trong dock; không có thì bỏ vai trò. Sau đó bù bảng chữ cái như hôm nay (Q3 có thể đổi). Giữ chú thích "so theo packageName" của M3, sửa KDoc lớp cho khớp.
- [ ] **Step 6: Manifest (chỉ khi Task 1 Step 3 cần).** Thêm khối `<intent><action android:name="android.provider.Telephony.SMS_DELIVER"/></intent>` vào cuối `<queries>`, kèm chú thích ngày đo và lý do (tài liệu `Telephony.Sms.getDefaultSmsPackage`).
- [ ] **Step 7: Grep** `resolve(` của cổng cũ không còn ai gọi; `pickDefaultDock` chỉ còn ở `seedIfEmpty` (Review Focus 6).
- [ ] **Step 8: Build** xanh. Commit (vd `fix: pick dock apps through per-role fallback chain`), push, ghi Nhật ký, báo điều phối.

### Task 3: Kiểm seed mới trên Oppo, giữ nguyên bố cục của chủ dự án (điều phối chạy)

**Bắt buộc kiểm tay:** không ở mục 4.2 (đây là bài kiểm cả tính năng, chạy sau khi Task 2 qua review). Điều phối hỏi Q2 **trước** khi bắt đầu.
**Máy:** Oppo A3x.
**Files:** không.

- [ ] **Step 1:** Cài bản debug của nhánh (`gradlew.bat installDebug` trong worktree, giữ dữ liệu). Ghi `git rev-parse --short HEAD`.
- [ ] **Step 2:** Chụp màn Home trang 1 + dock của bố cục hiện có (`screencap`), để so sau khôi phục.
- [ ] **Step 3:** Theo "Quy trình sao lưu và khôi phục": S1–S4 (sao lưu, kiểm hash), rồi X (xóa bố cục).
- [ ] **Step 4:** Mở app, chờ lưới hiện. Đọc dock bằng ba cách: (a) `adb logcat -d -s DefaultAppResolver` (4 dòng D7); (b) kéo 3 file DB (`launcher.db`, `-wal`, `-shm`, bài học M4) và đọc hàng dock (`container = -101`, theo `rank`; kiểm tên cột trong `LauncherItemEntity`) bằng `sqlite3` của Python; (c) `screencap` dock. **Đạt khi:** rank 0..3 lần lượt là `com.google.android.dialer`, `com.google.android.apps.messaging`, `com.android.chrome`, `com.oplus.camera` (hoặc đúng package mà Task 1 đã dự đoán cho máy này); không có app giao diện cuộc gọi hay app tài liệu.
- [ ] **Step 5:** Bấm từng icon dock: mở đúng app, không hiện hộp chọn (dock mở bằng khóa LAUNCHER).
- [ ] **Step 6:** Theo Q2: **khôi phục** (R1–R4, so hash, mở app, so ảnh Step 2) hoặc **giữ** bố cục mới (xóa file tar sau khi chủ dự án xác nhận).
- [ ] **Step 7 (chỉ khi chủ dự án cho dùng Live 4):** cùng quy trình trên Live 4; đạt khi dock vẫn là `com.vinsmart.android.dialer`, `com.vinsmart.messaging`, `com.android.chrome`, `com.vinsmart.camera` (số M3), rồi khôi phục.

### Task 4: Knowledgement, Review Focus, đóng

**Bắt buộc kiểm tay:** không.
- [ ] **Step 1:** Hoàn chỉnh `docs/knowledgements/DD-dock-default-apps.md`: sơ đồ khớp code thật (tên hàm đúng), bảng số đo Task 1, kết quả Task 3.
- [ ] **Step 2:** Điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung". Lấy `main` mới bằng `git merge origin/main` (không rebase, mục 12.12), build xanh, báo điều phối để chuyển PR sang Ready.

---

## Quy trình sao lưu và khôi phục (Oppo, điều phối chạy, PowerShell)

Chỉ chạy được với bản **debug** (`run-as`). File sao lưu để **ngoài repo** (bài học M4: `git add -A` sẽ commit nhầm). Đặt `$P = "gambi.global.os26launcher"`, thư mục `$B = "$env:USERPROFILE\dd-backup"`.

**Bẫy PowerShell 5.1:** chuyển hướng `>` của PowerShell ghi văn bản (UTF-16), làm hỏng file nhị phân. Lệnh ra file nhị phân phải chạy qua `cmd /c "... > file"`. Lệnh ra văn bản (danh sách hash) thì dùng được PowerShell.

- **S1.** `adb shell am force-stop $P`, rồi `adb shell run-as $P ls databases files/datastore no_backup` (biết có những file nào; kiểm `run-as` chạy).
- **S2. Hash trước:** `adb shell run-as $P sh -c "find databases files/datastore no_backup -type f | sort | xargs sha256sum" > $B\before.txt` (văn bản).
- **S3. Sao lưu:** `cmd /c "adb exec-out run-as $P tar cf - databases files/datastore no_backup > %USERPROFILE%\dd-backup\layout.tar"`.
- **S4. Kiểm bản sao lưu:** `tar -tvf $B\layout.tar` liệt kê đủ file của S1; so `adb shell run-as $P sh -c "tar cf - databases files/datastore no_backup | sha256sum"` với `Get-FileHash -Algorithm SHA256 $B\layout.tar` (app đã dừng nên tar ra cùng byte; khác thì làm lại S3, **không** sang bước sau).
- **X. Xóa bố cục (chỉ Task 3):** `adb shell run-as $P rm -f databases/launcher.db databases/launcher.db-wal databases/launcher.db-shm` (đúng tên đã thấy ở S1). Giữ DataStore để cỡ lưới, mức kính, cờ hỏi ảnh nền không đổi. Lần mở sau `itemDao.count() == 0` nên `seedIfEmpty` chạy.
- **R1.** `adb shell am force-stop $P`; `adb shell run-as $P rm -rf databases files/datastore no_backup` (để không còn `-wal`/`-shm` của lần thử nằm cạnh DB khôi phục).
- **R2.** `adb push $B\layout.tar /data/local/tmp/dd_layout.tar`; `adb shell "cat /data/local/tmp/dd_layout.tar | run-as $P tar xf -"`; `adb shell rm /data/local/tmp/dd_layout.tar`.
- **R3. Hash sau (trước khi mở app):** chạy lại lệnh S2 ra `$B\after.txt`; `Compare-Object (Get-Content $B\before.txt) (Get-Content $B\after.txt)` phải rỗng.
- **R4.** Mở app, so trang 1 + dock với ảnh chụp trước. Giữ `layout.tar` cho tới khi chủ dự án xác nhận bố cục đúng, rồi mới xóa (thư mục ngoài repo).

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Oppo A3x. Live 4 chỉ khi chủ dự án cho dùng. **Dữ liệu:** bố cục hiện có của chủ dự án (được sao lưu), bản debug của nhánh. **Không** đổi app mặc định trên máy chủ dự án.

1. Hỏi Q2 (khôi phục hay giữ bố cục seed mới).
2. Cài bản nhánh, chụp màn Home + dock hiện có.
3. Sao lưu S1–S4.
4. Xóa bố cục (X), mở app: dock = Điện thoại (Google), Tin nhắn (Google Messages), Chrome, Máy ảnh Oppo; log 4 dòng D7 khớp.
5. Bấm 4 icon dock: mở đúng app, không hộp chọn.
6. Trang 1 lưới: app bị dời khỏi dock cũ (Chrome nếu không còn ở dock, app giao diện cuộc gọi, app tài liệu) xuất hiện trên lưới như mọi app khác.
7. Khôi phục R1–R4 (hoặc giữ, theo Q2).
8. **Hồi quy:** cài/gỡ không ảnh hưởng dock (`removePackages`, dock không tự lấp, Q10); mở lại app (`force-stop`) dock không đổi; widget Đồng hồ/Lịch (B1) vẫn mở thẳng app hệ thống.
9. (Chỉ khi chủ dự án cho dùng Live 4) seed mới trên Live 4 ra đúng 4 package M3, rồi khôi phục.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Oppo A3x, 2026-11-09, nhánh@e179bb5: dock = Điện thoại (Google), Tin nhắn (Google Messages), Chrome, Camera (Oppo); bố cục chủ dự án đã khôi phục (hash trùng). Live 4: chưa kiểm (máy bận).

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-09 | Plan | Viết plan + khung knowledgement; đọc tài liệu `TelecomManager`, `Telephony.Sms`, `RoleManager` | `docs: add DD plan` | Chờ duyệt; câu hỏi Q1–Q3 |
| 2026-11-09 | Plan | Chủ dự án duyệt plan: Q1 = C-a, Q2 = khôi phục bố cục cũ, Q3 = giữ bù bảng chữ cái, D1-D8 như viết | (docs) | Plan duyệt 2026-11-09 |
| 2026-11-09 | Thứ tự Task | Điều phối đổi: **Task 1 (đo trong app) gộp vào Task 3**. Làm Task 2 trước, resolver tự đo: ngoài log D7 còn một dòng tạm tag `DDM` mỗi vai trò (giá trị thô của từng nguồn, cờ `FLAG_SYSTEM`/`FLAG_UPDATED_SYSTEM_APP` của từng trình xử lý, tổng ms cả chuỗi). Điều phối seed thử trên Oppo, đọc log, quyết có cần khối `<queries>` `SMS_DELIVER` không; Task 4 gỡ mọi dòng DDM. **Chưa thêm `<queries>`** | (docs) | Mọi dòng DDM có chú thích `// DDM: remove before closing (Task 4)`. Thời gian chuỗi trong DDM tính cho từng vai trò (không gồm phần của vai trò khác) |
| 2026-11-09 | Task 2 | Cổng `candidates(role)`, `DefaultAppResolver` viết lại theo chuỗi D2 (API, resolve không phải hộp chọn, hệ thống, package đã biết), use case duyệt ứng viên, log D7 + DDM tạm. `assembleDebug` xanh, APK mới. Grep: `pickDefaultDock` chỉ ở `seedIfEmpty`, không ai gọi `resolve(` cũ | (xem git log) | Chờ điều phối seed thử (Task 3) |
| 2026-11-09 | Task 3 | Điều phối seed thử trên Oppo 11:05, hash khôi phục trùng. Đo được: dock đúng 4 app; tổng chuỗi khoảng 123 ms; `getDefaultSmsPackage` đúng không cần `<queries>` (D4 đóng). Live 4: chưa kiểm (máy bận) | (không code) | Q2 đã khôi phục bố cục chủ dự án |
| 2026-11-09 | Task 4 | Gỡ mọi dòng DDM (giữ log D7), điền Bằng chứng Review Focus, knowledgement, đề xuất tài liệu chung. `assembleDebug` xanh | (xem git log) | Xong, chờ chủ dự án xem và merge; PR vẫn draft |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Hộp chọn không thành ứng viên | Bộ lọc hộp chọn (`isChooser` + `realDefault`). Đo được, Oppo A3x, 2026-11-09: MESSAGES `resolve=android/ResolverActivity` bị loại; CAMERA `android/ResolverActivity` bị loại. Đọc code: A1 và A2 ở mọi bậc, bậc (3) cũng lọc `isChooser` | Đạt |
| 2 | Mặc định thật thắng | Đo được, Oppo, 2026-11-09: PHONE `defaultDialer=systemDialer=com.google.android.dialer` (src=API); MESSAGES `defaultSms=com.google.android.apps.messaging` (src=API); BROWSER `resolveActivity` trong app trả `com.android.chrome/IntentDispatcher` (src=RESOLVE), khác số đo shell (hộp chọn) | Đạt |
| 3 | Thứ tự chuỗi, không trùng | Đọc code: `LinkedHashMap` giữ thứ tự bậc 1,2,3,4 và bỏ trùng; CAMERA src=SYSTEM (đo được, Oppo, 2026-11-09) | Đạt |
| 4 | Use case thử ứng viên kế | Đọc code: `firstNotNullOfOrNull` duyệt ứng viên, bỏ cái đã trong dock hoặc không có trong `apps`. Đo được, Oppo, 2026-11-09: dock sau seed = Điện thoại (Google), Tin nhắn (Google Messages), Chrome, Camera (Oppo) | Đạt |
| 5 | Live 4 không đổi | Live 4: chưa kiểm (máy bận). Đọc code: một trình xử lý mỗi vai trò thì `resolveActivity` trả app đó, đi bậc (2) | Chưa kiểm trên máy |
| 6 | Dock đã seed không đổi | Grep: `pickDefaultDock(` chỉ ở `LayoutReconciler.seedIfEmpty`. Đo được, Oppo, 2026-11-09: bố cục chủ dự án khôi phục, hash trùng | Đạt |
| 7 | Bậc lỗi không làm mất bậc sau; API 29 | Đọc code: `guarded()` bọc từng bậc (`RuntimeException`), `queryIntentActivities` có try/catch riêng; `getSystemDialerPackage` chỉ khi `SDK_INT >= 29` | Đạt (đọc code) |
| 8 | Tầm nhìn package có số đo | Đo được, Oppo, 2026-11-09: `getDefaultSmsPackage` trả `com.google.android.apps.messaging` từ trong app, không cần khối `SMS_DELIVER`; D4 đóng, manifest không đổi | Đạt |
| 9 | Thời gian chuỗi trong transaction | Đo được, Oppo, 2026-11-09: PHONE 30 ms, MESSAGES 70 ms, BROWSER 12 ms, CAMERA 11 ms, tổng khoảng 123 ms, một lần mỗi seed (dưới ngưỡng 200 ms của plan) | Đạt |
| 10 | Chỉ sửa trong Vùng file | `git diff --stat`: chỉ 3 file nguồn trong Vùng file + docs; `LayoutReconciler`, manifest không đổi | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `AGENTS.md` mục "Dock mặc định (M3 Task 4)": thêm rằng `resolveActivity` trả `android/com.android.internal.app.ResolverActivity` khi có ≥ 2 trình xử lý và chưa đặt mặc định, kèm số đo Oppo; trỏ tới chuỗi DD. Phần còn lại xem "Bài học cho AGENTS.md" trong knowledgement.
- `Launcher-Features.md`: ghi DD đã sửa lỗi dock mặc định trên máy nhiều trình xử lý (điền khi đóng).
- `project_structure.md`: cổng `DefaultAppResolverPort` đổi sang `candidates(role)`.
- `AGENTS.md`: ghi thêm đo được Oppo 2026-11-09: `getDefaultSmsPackage` dùng được từ trong app không cần `<queries>` `SMS_DELIVER`; `resolveActivity` trong app có thể trả app thật (Chrome) dù shell thấy hộp chọn; dock chỉ đổi ở lần seed mới (Q1 = C-a).
- `room-schema.md`: không đổi.
