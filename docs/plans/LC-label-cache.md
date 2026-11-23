# LC — Cache nhãn app xuống đĩa (task phụ P1 của AGENTS.md) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Oppo A3x (máy có vấn đề) và Vsmart Live 4 (không được tệ hơn). Lệnh viết cho CMD/PowerShell. **Agent thực thi không dùng máy thật**: `adb install` và thao tác kịch bản do điều phối chạy. **Chưa code cho tới khi plan được duyệt VÀ nhánh GP đã merge.**

**Nhánh:** `feature/lc-label-cache` · **Worktree:** `E:\SourceTree\IosLauncher-wt\LC-label-cache` · **PR:** # (draft) · **Bắt đầu:** 2026-11-05 · **Plan duyệt:** 2026-11-05 (chủ dự án)
**Trạng thái:** Mã xong (Task 2, 3), đã đo; Task 1 đạt; Task 4 đã bỏ; còn thiếu ca chưa đo (xem Bằng chứng). Chờ review PR
**Khóa:** cần Oppo (đo) + Live 4 (không tệ hơn) · không khóa schema Room · không đụng file nóng độc quyền

**Goal:** Cold start trên máy nhiều app (Oppo A3x, 345 activity) hiện màn trống ~5,3 giây vì đọc nhãn (`LauncherActivityInfo.getLabel`) chiếm ~5,4 giây (~84% thời gian tới lưới). Lưu nhãn xuống đĩa để lần mở sau chỉ đọc nhãn của app **mới hoặc đã đổi**, lưới hiện ngay sau `getActivityList` (~140 ms).

**Architecture:** Một lớp mới `LabelCache` (file JSON trong `noBackupFilesDir`, ghi nguyên tử) nằm trong `data/apps/`. `LauncherAppsDataSource.loadApps()` vẫn gọi `getActivityList` trước (nguồn sự thật về app nào đang tồn tại), rồi với từng activity: có trong cache VÀ dấu vân tay (`stamp`) khớp thì lấy nhãn từ cache, ngược lại đọc nhãn như hôm nay (song song, `Semaphore(8)`). Xong thì ghi lại cache ngoài đường găng. Phần còn lại của luồng (`AppRepositoryImpl.loadSorted`, sắp xếp `Collator`, `stateIn`, `LayoutRepositoryImpl`, `HomeViewModel`) **không đổi hợp đồng**: `AppRepository.apps` vẫn phát rỗng rồi danh sách thật, chỉ là danh sách thật tới sớm hơn.

**Tech Stack:** không thêm dependency. `org.json` (có sẵn trong Android) + `android.util.AtomicFile`.

**Spec:** task phụ P1 (`AGENTS.md` mục "Nạp danh sách app", `room-schema.md` Q6: cache nhãn **không** vào Room, làm sau M6 khi có Oppo để đo) + số đo P1 cuối `AGENTS.md`. Không có TT# riêng; LC là mã điều phối đặt.

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (số đo và code, có nhãn nguồn)

1. **Cái gì thật sự chặn lưới đầu tiên** (đọc code, theo đúng thứ tự gọi):
   - `AppRepositoryImpl.apps` = `observeChanges().debounce.onStart{emit}.map{ loadSorted() }.stateIn(emptyList())`. `loadSorted()` = `dataSource.loadApps()` (một lời gọi **trọn gói**: `getActivityList` cho mọi profile, rồi đọc nhãn mọi activity, mới trả list) rồi `Collator`.
   - `LayoutRepositoryImpl.layout` bắt đầu bằng `appRepository.apps.first { it.isNotEmpty() }` → `seedIfEmpty` → `ensureFitsGrid` → `catchUp(firstApps)` → mới `combine(pageDao, itemDao, apps)` → `assemble`. `HomeViewModel` chỉ `Ready` khi `pages` phát lần đầu **và** settings đã về (`gridStatusFor`). Nghĩa là **cả chuỗi chờ danh sách app đầy đủ**; mọi consumer khác (`SearchViewModel`, `GetCategoryGroupsUseCase`, `hiddenApps`) cũng `filter { isNotEmpty() }`.
   - **Bố cục có cần nhãn không?** Với DB, chỉ **khóa** (`ComponentKey`) là đủ: `catchUp` chỉ dùng `it.key`. Nhưng (a) `seedIfEmpty`/`placeNewApps` dựa vào **thứ tự sắp theo nhãn** để chọn ô (máy mới, app mới); (b) `assemble` ghép hàng DB với `AppItem` theo khóa và nhét nguyên `AppItem` (có `label`) vào `PlacedApp`/`PlacedFolder.previewApps`; view vẽ chữ nhãn và `contentDescription` từ đó (`IconCellView`: `item.label`). Vậy **vẽ được lưới thì phải có nhãn**; icon bitmap thì không chặn (nạp trễ qua `AppRepository.icon`).
2. **Số đo** (đo, P1, Oppo A3x, APK debug, 5 lượt sau `force-stop`, ms từ lúc tiến trình bắt đầu): cửa sổ hiện ~1200; bắt đầu nạp ~800; `getActivityList` xong ~940 (≈140 ms); đọc nhãn xong 6271–6423 (≈5,4 s, ≈15,7 ms/app); sắp ~25 ms; có trang để vẽ 6466–6608. Live 4 (87 app): nạp 0,4–1,45 s (M2). **Chưa đo** sau khi khởi động lại máy.
3. **Đã thử làm nhãn nhanh hơn mà không cache và không đạt** (đo, M3 Task 1 trên Live 4): song song `Semaphore(4/8/16)` chỉ giảm ~24%; nghi có khóa trong framework khi nạp tài nguyên nhãn (suy luận chưa kiểm chứng). Nên không còn dư địa lớn ở "đọc nhanh hơn".
4. **Mỗi lần có sự kiện package, `apps` nạp lại TOÀN BỘ** (đọc code: `observeChanges → debounce → loadSorted`), tức cài hay cập nhật một app trên Oppo hiện đốt thêm ~5 s CPU nền để đọc lại 345 nhãn. Cache sửa luôn việc này (chỉ đọc lại nhãn của package đổi).
5. **Ràng buộc:** schema Room khóa (có thể thuộc G2c) và `room-schema.md` Q6 đã quyết **không** cache nhãn trong DB; `HomeViewModel`, `HomeUiState`, `LayoutReconciler`, `data/layout/db` là file nóng của nhánh khác; M8d sửa `AppRepositoryImpl.launch`; `stateIn(emptyList())` buộc phân biệt Loading với Empty (đã có sẵn ở mọi consumer qua `filter { isNotEmpty() }`).

### Các phương án đã xét

**A. Chiến lược nạp**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A1. Cache kiểm chứng trước (validated-first)** | `getActivityList` trước (140 ms), nhãn lấy từ cache nếu `stamp` khớp, chỉ đọc nhãn cho app thiếu/đổi | Danh sách khóa **luôn do hệ thống xác nhận** → không bao giờ hiện app đã gỡ, không cần phát lần hai, không nháy, không đụng kéo-thả; sắp xếp từ cùng thứ tự đầu vào nên giống hệt; sửa luôn nạp lại sau sự kiện package | Nhãn đổi mà `stamp` không đổi thì sai tới lần kiểm lại (mục Refresh); sàn thời gian = 140 ms `getActivityList` | **Chọn** |
| A2. Hiện cache ngay, đọc nhãn nền, phát lần hai nếu khác (gợi ý ban đầu) | Phát danh sách cache trước khi `getActivityList` xong, rồi nạp thật | Sớm hơn A1 thêm ~140 ms | App đã gỡ lúc launcher không chạy hiện thành icon bấm không mở được (Toast `AppLaunchFailed`); `catchUp` chạy với tập khóa cũ (không xóa hàng đã gỡ, hoặc xóa oan nếu cache thiếu app mới); **hai lần phát** buộc `layout` assemble lại → rủi ro đúng bài học M7 (view bị thay giữa kéo-thả); tiết kiệm 140 ms không đáng | Không |
| A3. Không cache; đọc nhãn **ưu tiên** cho app trong bố cục trang 1 + dock (~30 app), phần còn lại nền | Đọc ~30 nhãn (~0,5 s), phát lần một, rồi đọc nốt | Không cần đĩa, không cần vô hiệu hóa | **Sắp xếp** cần mọi nhãn (máy mới, `placeNewApps`, Thư viện, tìm kiếm) → thành hai pha phát, consumer nào cũng phải chịu "nhãn chưa đủ"; muốn ưu tiên phải biết bố cục DB trong `data/apps` hoặc đổi `LayoutRepositoryImpl`/`HomeViewModel` (file nóng); mỗi cold start trả phí ~0,5 s + lần phát hai | Không (dự phòng, mục Ngoài phạm vi) |
| A4. Vẽ lưới với nhãn tạm (tên package) rồi thay | Không cache | Đơn giản | Nháy chữ nhãn; `PlacedApp` bất biến nên thay = phát lại toàn bộ; sắp xếp tạm sai làm `seedIfEmpty`/`placeNewApps` đặt app sai ô (**hỏng dữ liệu**, không chỉ thẩm mỹ) | Không |

Câu hỏi "có cách rẻ hơn cache như không chờ nhãn cho icon": icon **không** chờ nhãn (nạp riêng bằng `AppRepository.icon`); thứ chờ nhãn là chữ dưới icon, sắp xếp và seed. A3/A4 là hai biến thể của ý đó và đều thua A1 vì lý do trên.

**B. Nơi lưu**

| Phương án | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|
| Room (bảng mới) | Có sẵn hạ tầng | Cần nâng schema (khóa Room có thể thuộc G2c, hai nhánh cùng nâng là xung đột migration, MultiAgent-Workflow 5.3); Q6 đã quyết không; mở DB chậm hơn đọc file | Không |
| DataStore Preferences (một khóa chuỗi JSON) | Dự án đã dùng; ghi nguyên tử, có `corruptionHandler` | Mỗi lần ghi **ghi lại cả file settings** và phát lại flow `settings` cho mọi bên thu; cache ~35 KB nằm lẫn với cài đặt; đọc qua coroutine, chậm hơn file thô; không thêm lợi ích cho một blob | Không |
| **File JSON trong `noBackupFilesDir`, ghi bằng `AtomicFile`** | Độc lập với settings, không phát flow nào; `AtomicFile` ghi tmp rồi đổi tên, chết giữa chừng vẫn còn bản cũ nguyên vẹn; hỏng/thiếu thì parse lỗi → đường cũ; tự mô tả (version, locale); 345 mục ≈ 35–45 KB | Tự viết đọc/ghi (~100 dòng). `noBackupFilesDir` (không phải `filesDir`) để Auto Backup không khôi phục cache sang máy khác (manifest đang `allowBackup=true`) | **Chọn** |
| File nhị phân `DataOutputStream` | Nhỏ, nhanh hơn chút | Khó soi bằng mắt và khó tạo cache hỏng/cũ để kiểm; chênh tốc độ không đáng ở 345 mục | Không |

### Vô hiệu hóa: cái nào phát hiện được rẻ, không cần đọc nhãn

`stamp` của mỗi activity, đọc miễn phí từ `LauncherActivityInfo.applicationInfo` (đã có trong vòng lặp hôm nay) = `sourceDir` của APK. **Giả định cần máy thật xác nhận (Task 1):** từ Android 8 thư mục cài đặt có hậu tố ngẫu nhiên (`/data/app/~~xxx==/com.x-yyy==/base.apk`) và đổi mỗi lần cài/cập nhật, nên `sourceDir` đổi khi cập nhật app (suy luận chưa kiểm chứng; minSdk 26, Live 4 = Android 11, Oppo = Android 14). `LauncherActivityInfo` không có `lastUpdateTime` (chỉ có `getFirstInstallTime`); `PackageInfo.lastUpdateTime` cần một IPC mỗi package nên chỉ làm dự phòng.

| Tình huống | Phát hiện | Cách xử lý |
|---|---|---|
| Lần đầu / chưa có cache / file hỏng / sai version | Không đọc được hoặc `v` lạ | Đường cũ nguyên vẹn (đọc mọi nhãn), rồi ghi cache. Không crash, không chặn |
| App cập nhật lúc launcher không chạy | `sourceDir` khác | Chỉ app đó là "miss", đọc lại nhãn |
| App cài mới / gỡ lúc launcher không chạy | Khóa có trong `getActivityList` mà không có trong cache (miss) / ngược lại (rơi khỏi cache lần ghi sau) | Cài mới: miss, đọc nhãn. Gỡ: không xuất hiện vì danh sách do hệ thống trả; `catchUp` nhận đúng tập khóa như hôm nay |
| Đổi ngôn ngữ hệ thống (nhãn dịch theo locale) | Header ghi `Locale.getDefault().toLanguageTag()`; khác thì **bỏ cả cache** | Đường cũ. Hiển thị nhãn ngôn ngữ cũ còn sai hơn là chờ. `Collator` ở `loadSorted` dùng cùng locale |
| Cập nhật hệ điều hành (nhãn app hệ thống đổi mà `sourceDir` /system không đổi) | Header ghi `Build.FINGERPRINT`; khác thì bỏ cả cache | Đường cũ |
| Work profile | Khóa chứa `userSerial` (serial không tái sử dụng). Profile bị xóa: khóa biến mất khỏi `getActivityList`; profile mới: miss | Như trên. Profile tắt (quiet mode) mà `getActivityList` không trả activity: giống hôm nay |
| **Nhãn đổi mà không có cập nhật** (nhãn động, RRO, ngôn ngữ riêng từng app) | **Không phát hiện được rẻ** | Kiểm lại toàn bộ nền theo chu kỳ (mục Refresh). Chấp nhận nhãn sai tối đa một chu kỳ |

`getActivityList` (~140 ms trên Oppo, đo P1) vẫn chạy mỗi cold start: đó là sàn của LC và là điều kiện để các bảo đảm trên đứng vững.

### Refresh nền (yêu cầu "đọc lại nhãn nền rồi chỉ phát nếu khác")

Vì A1 đã bảo đảm tập khóa và nhãn của mọi mục có `stamp` khớp, refresh chỉ còn lo ca **nhãn đổi không kèm cập nhật**. Thiết kế: **kiểm lại toàn bộ theo chu kỳ**, không mỗi lần mở (đọc 345 nhãn = ~5 s CPU nền tranh với nạp icon và kính, chính là chi phí LC muốn tránh).
- Header cache ghi `verifiedAt`. Sau lần phát đầu của `apps`, nếu `now - verifiedAt > 7 ngày` (**câu hỏi cho chủ dự án**), một coroutine nền (`Semaphore(2)`, trễ 10 giây sau lần phát đầu) đọc lại **mọi** nhãn, ghi cache mới + `verifiedAt`. Chỉ khi có nhãn khác thì `apps` phát danh sách mới (so `List<AppItem>` bằng `==`; `stateIn` cũng bỏ giá trị bằng nhau). Không có nhãn đổi thì **không phát gì**; `pages` ở `LayoutRepositoryImpl` còn có `distinctUntilChanged` nên không đi tiếp.
- Khi **có** nhãn đổi: đi đúng đường mà cập nhật app đã đi từ M4 (`apps` phát lại → `assemble` → `pages`); `HomePage`/`PlacedApp`/`AppItem` là `data class` nên chỉ ô có nhãn khác mới bind lại. Kéo-thả: trạng thái kéo là DỮ LIỆU (`draggingItemId`, bài học M7) và `HomeViewModel` giữ `latestDbPages` khi `pendingMoves > 0`; lần phát nhãn không đổi vị trí/`pageId`/số trang nên không reset trang đang xem. **Rủi ro còn lại, chấp nhận, có kịch bản kiểm:** một lần phát nhãn đúng lúc đang kéo. Trễ 10 s + chu kỳ 7 ngày làm xác suất rất thấp.
- Task này (Task 4) **tách riêng và có thể bỏ** nếu chủ dự án chọn "không kiểm lại".

### Sau khi hết đọc nhãn, nút cổ chai tiếp theo là gì

Phần sau mốc 940 ms **chưa đo**; thứ tự dự kiến (đọc code), Task 3 đo để biết: (1) ~800 ms trước khi bắt đầu nạp (Application, Hilt, mở DB, Activity) — **ngoài phạm vi LC**; (2) `getActivityList` ~140 ms — sàn của LC; (3) cache + hợp nhất, mục tiêu < 100 ms; (4) `Collator` ~25 ms; (5) `seedIfEmpty`/`ensureFitsGrid`/`catchUp` (DB; `isPackageInstalled` chỉ cho khóa vắng mặt; máy mới seed 345 hàng một lần) và `assemble`; (6) `HomeViewModel` chờ cả settings; (7) vẽ ô và **nạp icon** (`AppRepository.icon` → `getActivityList(package)` + render từng icon cho 24 ô + dock, chưa có số), kính. **Mục tiêu trung thực:** LC không hứa dưới ~1100 ms; ngưỡng đặt ở "pages sẵn sàng", **không phải "icon đã vẽ xong"**. Nếu sau LC icon hoặc seed thành nút cổ chai thì mở tính năng riêng, không mở rộng LC.

### Vì sao chọn A1 + file JSON

- A2 chỉ hơn A1 ~140 ms (đo, P1) nhưng đổi lấy các lỗi dữ liệu (icon của app đã gỡ, `catchUp` chạy với tập sai, hai lần phát; suy luận từ code), đúng loại lỗi bài học M4/M7 cảnh báo. Chi phí sai (xóa oan hàng, icon chết) cao hơn lợi ích.
- File thô tách khỏi Room (Q6, khóa schema) và khỏi DataStore (không phát flow `settings`, không ghi lại file cài đặt). `AtomicFile` là cơ chế chuẩn của framework cho đúng ca này (đọc tài liệu).
- Cache chỉ giữ **chuỗi nhãn**. `category`/`isSystem` của `AppItem` đã nằm trong 140 ms của `getActivityList` (`mapPlatformCategory` chỉ đọc `appInfo.category`), nên cache **không giúp thêm** cho chúng và không đưa vào để khỏi thêm một nguồn có thể sai.
- Sắp xếp: nhãn là cùng chuỗi, `Collator` cùng locale, đầu vào cùng thứ tự `getActivityList`, `sortedWith` ổn định ⇒ thứ tự giống đường cũ (cần kiểm: Review Focus 4).

**Tap vào mục lỗi thời:** khóa luôn từ `getActivityList` của lần nạp đó nên chỉ còn ca app bị gỡ *sau* lần nạp, y hệt hôm nay: `startMainActivity` trả false → `AppLaunchFailed` → Toast. LC không làm ca này nhiều hơn.

**Đánh đổi chấp nhận:** nhãn đổi không kèm cập nhật có thể sai tối đa một chu kỳ kiểm lại; thêm file ~40 KB; thêm ~100 dòng code. **Xét lại khi:** `sourceDir` không đổi khi cập nhật trên máy thật (Task 1), hoặc đo sau LC cho thấy nút cổ chai không còn ở nhãn.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | File JSON ở `context.noBackupFilesDir/app_label_cache.json`, `AtomicFile`, không Room, không DataStore | Q6 + so sánh trên |
| D2 | Phương án nạp A1 (kiểm chứng trước, không phát hai lần) | Chủ dự án duyệt 2026-11-05 |
| D3 | `stamp` = `sourceDir`; header = `v`, `locale`, `fp`, `verifiedAt`, `n` | Task 1 xác nhận giả định |
| D4 | Locale hoặc fingerprint khác = bỏ cả cache | Chủ dự án duyệt 2026-11-05 |
| D5 | Cache chỉ chứa **nhãn**; `category`/`isSystem` đọc từ `getActivityList` | Xem trên |
| D6 | Ghi cache ngoài đường găng, `@ApplicationScope` + `@IoDispatcher`, chỉ ghi nếu nội dung đổi | Không làm chậm lần phát đầu |
| D7 | **Bỏ** kiểm lại nền (Task 4). Nhãn đổi mà app không cập nhật là giới hạn đã biết: nhãn có thể sai cho tới khi app đó cập nhật, đổi ngôn ngữ, cập nhật hệ điều hành hoặc xóa dữ liệu app | Chủ dự án quyết 2026-11-05: hiếm, và giữ LC ngoài `AppRepositoryImpl.kt`. D1 (`noBackupFilesDir`) cũng đã duyệt |

## Ngoài phạm vi / ghi để sau

- Nút cổ chai ngoài nhãn (khởi động tiến trình ~800 ms, nạp icon, seed, kính): đo ở Task 3, mở tính năng riêng nếu cần.
- A3 giữ làm dự phòng nếu `sourceDir` không dùng được làm `stamp`; sẽ cần chạm `LayoutRepositoryImpl`/`HomeViewModel`, phải xin lại.
- Cache icon bitmap xuống đĩa. Đo sau LC rồi mới quyết.
- Nút "làm mới nhãn" trong Cài đặt (GS giữ `ui/settings`).

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/data/apps/LabelCache.kt` | Tạo | Không |
| `app/src/main/java/gambi/global/os26launcher/data/apps/LauncherAppsDataSource.kt` | Sửa `loadApps()` + constructor (thêm `LabelCache`); Task 4 thêm `verifyAllLabels()`; không đụng hàm khác | Không |
| `app/src/main/java/gambi/global/os26launcher/data/apps/AppRepositoryImpl.kt` | **Không sửa** (Task 4 đã bỏ) | Tránh chồng với M8d |
| `docs/plans/LC-label-cache.md`, `docs/knowledgements/LC-label-cache.md` | Tạo | Không |

Không dùng file nóng độc quyền nào (`HomeViewModel`, `HomeUiState`, `HomeActivity`, `LayoutReconciler`, `data/layout/db`, `core/glass`, `IconGridView`, `HomePagerAdapter`). `AppItem` và `AppRepository` không đổi nên GP, G2c, GS, M8d không bị chạm. DI: `LabelCache` có `@Inject constructor` + `@Singleton`, không sửa module Hilt.

## Global Constraints

- Cache hỏng, thiếu, sai version, lỗi IO đọc/ghi: **luôn** quay về đường hôm nay, không crash, không ném ra ngoài `loadApps`. Mọi `catch` ghi `Log.w` rồi tiếp tục.
- Không đổi `AppItem`, `AppRepository`, hợp đồng "`apps` phát rỗng rồi thật"; không thêm giá trị khởi tạo giả.
- Danh sách khóa trả về **luôn** từ `getActivityList` của lần nạp đó, không bao giờ từ cache.
- Thứ tự đầu vào cho `Collator` giữ nguyên (theo `userProfiles` rồi thứ tự `getActivityList`). `Collator` vẫn tạo mới mỗi lần sắp (không thread-safe).
- Đọc cache và hợp nhất chạy trên `@IoDispatcher`, không trên luồng chính.
- Không thêm log thời gian vĩnh viễn (điều phối áp patch đo vào bản sao bỏ đi). `Log.w` ngắn cho lỗi cache được phép.
- Nhãn lấy từ nhánh `catch` (dự phòng bằng tên package) **không** ghi vào cache.
- Không sửa tài liệu chung (AGENTS.md, Launcher-Features.md, project_structure.md, room-schema.md, MultiAgent-Workflow.md).
- Không `gradlew --stop`. PowerShell: `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"`.
- Commit/PR tiếng Anh; docs tiếng Việt.

## Review Focus

1. **Không có app ma:** mọi khóa trong `apps` có mặt trong `getActivityList` lần nạp đó; cache không bao giờ thêm khóa.
2. **Cache hỏng không làm chết app:** file rỗng, JSON cụt, `v` lạ, trường thiếu/sai kiểu, `n` không khớp, không đọc được → đường cũ.
3. **`stamp` đúng:** cập nhật app → nhãn đọc lại; không cập nhật → không đọc lại (đếm hit/miss).
4. **Thứ tự sắp** có cache == không cache (so danh sách nhãn theo thứ tự, cả 345 và 87, gồm nhãn tiếng Việt có dấu và nhãn bắt đầu bằng dấu cách).
5. **Ghi cache** nằm ngoài đường găng, nguyên tử (kill giữa lúc ghi vẫn đọc được bản cũ), không ghi khi không đổi, không ghi nhãn dự phòng.
6. **Locale/fingerprint** khác → bỏ cache.
7. **Lần đầu không cache không chậm hơn hôm nay** (đo ghép cặp).
8. **Phát lần hai (Task 4):** chỉ khi có nhãn khác; không đụng kéo-thả/chỉnh sửa/trang đang xem; không nháy.
9. Mục lỗi thời hành xử như hôm nay (`launch` false → `AppLaunchFailed`).
10. Không đụng `AppRepositoryImpl.launch`; không sửa file ngoài Vùng file.

---

### Task 1: Xác nhận giả định `sourceDir` đổi khi cập nhật (điều phối chạy, không code)
**Bắt buộc kiểm tay:** có — giả định chỉ máy thật trả lời; sai thì Task 2–4 đổi `stamp` hoặc đổi sang A3 (mục 4.2 của MultiAgent-Workflow.md).
**Files:** không (chỉ lệnh `adb`).
**Interfaces:** Produces: kết luận `stamp` dùng được trên Oppo (Android 14) và Live 4 (Android 11).
- [x] **Step 1 (chủ dự án chốt 2026-11-05):** không cập nhật app thật trên máy chủ dự án. Test case = **chính APK debug của dự án** (`gambi.global.os26launcher`) cài lại bằng `adb install -r`; app hệ thống chỉ đọc.
- [x] **Step 2: Lệnh (PowerShell, Oppo + Live 4, điều phối chạy):**
  ```
  adb shell pm path gambi.global.os26launcher
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  adb shell pm path gambi.global.os26launcher
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  adb shell pm path gambi.global.os26launcher
  adb shell dumpsys package gambi.global.os26launcher | findstr "codePath lastUpdateTime"
  adb shell pm path com.android.settings
  adb shell pm list packages -s -f
  ```
  (a) Ba lần `pm path` **phải khác nhau** (hậu tố `~~...==` / `...-...==` đổi mỗi lần cài). (b) `pm path com.android.settings` (hoặc app hệ thống bất kỳ): đường dẫn dưới `/system`, `/product`, `/system_ext`... ghi lại. (c) Trong `pm list packages -s -f` tìm app hệ thống có đường dẫn `/data/app/` (đã được cập nhật qua store): chứng tỏ app hệ thống đã cập nhật nằm ở đường dẫn `/data/app/~~...`, khác `/system`, nên `sourceDir` đổi khi nó cập nhật.
- [x] **Step 3: Đủ bằng chứng chưa:** đủ nếu (a) khác trên cả hai máy. Phần không kiểm được vì không cập nhật app thật: cập nhật qua Play Store/trình cài của OEM đi qua cùng PackageManager nên cùng cơ chế (suy luận, không đo); nếu có ca lệch thì chỉ một nhãn sai tới lần cập nhật kế (giới hạn D7). Nếu (a) KHÔNG khác trên máy nào: dừng, báo executor để chuyển sang `lastUpdateTime` (`getPackageInfo`).

### Task 2: `LabelCache` (đọc/ghi/vô hiệu, chưa nối vào luồng)
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `data/apps/LabelCache.kt`.
**Interfaces:** Produces: `class LabelCache @Inject constructor(@ApplicationContext, @IoDispatcher, @ApplicationScope)`: `fun load(): Snapshot?` (blocking, gọi từ IO; null nếu thiếu/hỏng/khác locale/khác fingerprint), `fun Snapshot.labelFor(key, stamp): String?`, `fun scheduleSave(entries, verifiedAtMs)` (gộp, chỉ ghi nếu khác bản đã đọc/ghi gần nhất). `Entry(label, stamp)`.
- [x] **Step 1:** Định dạng: `{"v":1,"locale":"vi-VN","fp":"...","n":345,"e":[{"p":"..","c":"..","u":0,"s":"..","l":".."}]}`. `n` phải bằng số phần tử `e`.
- [x] **Step 2:** `load()`: `AtomicFile.openRead` → parse `org.json`; bắt **mọi** `Exception` (`JSONException`, `IOException`, `ClassCastException`…); lỗi thì `Log.w`, xóa file hỏng (best effort), trả null.
- [x] **Step 3:** `scheduleSave`: `AtomicFile.startWrite/finishWrite/failWrite` trong `scope.launch(io)`; một việc ghi tại một lúc (job trước chưa xong thì giữ bản mới nhất).
- [x] **Step 4: Build** `assembleDebug` xanh.
- [x] **Step 5: Kịch bản kiểm (Live 4, điều phối):** chưa nối nên chỉ cần build xanh và app chạy y như cũ (hồi quy).

### Task 3: Nối vào `loadApps()` + đo (Oppo bắt buộc)
**Bắt buộc kiểm tay:** có — phép đo hiệu năng dùng để chọn phương án (không đạt thì xét lại thiết kế, mục 4.2).
**Files:** Sửa `data/apps/LauncherAppsDataSource.kt` (`loadApps`, constructor).
**Interfaces:** Consumes: `LabelCache`. Produces: `loadApps()` cùng chữ ký.
- [x] **Step 1:** Trong `loadApps()`: sau `getActivityList` của mọi profile, `labelCache.load()` một lần; mỗi `info`: `stamp = info.applicationInfo.sourceDir`; hit thì dùng ngay; miss thì `async` đọc nhãn như hôm nay (giữ `Semaphore(LABEL_CONCURRENCY)` và nhánh `catch → packageName`). Giữ **nguyên thứ tự** danh sách đầu ra.
- [x] **Step 2:** Sau khi có `items`, `scheduleSave` với mọi mục (hit + miss), **chỉ nếu** khác snapshot đã đọc; không chờ. Mục dùng nhãn dự phòng không ghi vào cache.
- [x] **Step 3: Build** xanh.
- [x] **Step 4: Đo (Oppo A3x, điều phối; patch đo trên bản sao bỏ đi):** theo mục "Phép đo và ngưỡng đạt". Ghi bảng pha vào Nhật ký. Đã đo pha "pages đầu tiên" và "sort xong" (không tách pha 1-10): xem Nhật ký.
- [ ] **Step 5 (CHƯA XONG: đã đo (a)(b) một phần và (e) mô phỏng bằng sửa `locale`; chưa làm (c)(d), chưa so thứ tự nhãn; xem Bằng chứng):** (a) cài mới, chưa có cache: lưới lên như cũ, file cache xuất hiện sau vài giây (`adb shell run-as gambi.global.os26launcher ls -l no_backup`); (b) `force-stop` mở lại: lưới lên nhanh, thứ tự nhãn y hệt (chụp danh sách Thư viện/tìm kiếm a-z trước và sau); (c) `adb install -r -t` một app (cả lúc đang chạy: sự kiện package, và lúc launcher không chạy): chỉ nhãn app đó đọc lại; (d) `pm uninstall -k --user 0` một app rồi `force-stop` mở lại: không còn icon, bố cục dồn như M4 Task 4; (e) đổi ngôn ngữ hệ thống: nhãn đúng ngôn ngữ mới ở lần mở kế, không hiện nhãn cũ.
- [x] **Step 6: Kịch bản hỏng cache (Live 4, điều phối):** thay file bằng (i) rỗng, (ii) JSON cụt, (iii) `{"v":99}`, (iv) `n` sai, (v) xóa file; mỗi lần `force-stop` rồi mở: không crash, lưới lên như hôm nay, file được ghi lại hợp lệ. Cách thay: `adb shell run-as gambi.global.os26launcher sh -c "echo '{}' > no_backup/app_label_cache.json"`. Đã làm trên Oppo (không phải Live 4): rỗng, cụt, `{"v":99}`, `n` sai, xóa file, sửa `locale`; đều rơi về đọc đủ ~6,6 s, ghi lại file 81.321 byte, không crash.

### Task 4: ĐÃ BỎ (chủ dự án quyết 2026-11-05)
Không có kiểm lại nhãn nền. Giới hạn đã biết ghi ở D7. Review Focus 8 không còn áp dụng; header cache không có `verifiedAt`.

### Task 5: Knowledgement, rà Review Focus, đóng
**Bắt buộc kiểm tay:** không.
- [x] **Step 1:** Hoàn chỉnh `docs/knowledgements/LC-label-cache.md` (có `sequenceDiagram` cold start và `flowchart` hit/miss/vô hiệu).
- [x] **Step 2 (đã điền bằng chứng và đề xuất; KHÔNG rebase theo lệnh điều phối, đã `git merge origin/main`; chuyển Ready do điều phối):** Điền "Bằng chứng Review Focus", "Đề xuất cập nhật tài liệu chung", rebase lên `main`, build xanh, chuyển PR sang Ready.

---

## Phép đo và ngưỡng đạt (điều phối chạy; mọi so sánh trong **cùng một phiên, ghép cặp xen kẽ**, lặp "trước" ở cuối — máy trôi theo nhiệt độ)

Quy trình P1: `am force-stop` rồi `am start -W`, 5 lượt, bỏ lượt đầu sau khi cài. "Trước" = `main` cùng patch đo, "sau" = nhánh LC. Mốc là ms từ lúc tiến trình bắt đầu (`SystemClock.uptimeMillis() - Process.getStartUptimeMillis()`).

**Các pha cần ghi thời gian (patch đo bản sao bỏ đi):**
1. `Application.onCreate` xong; cửa sổ Home hiện (`am start -W` TotalTime).
2. `loadApps` bắt đầu.
3. `getActivityList` xong (mọi profile).
4. `LabelCache.load()` xong (kèm số byte, số mục), số hit / số miss.
5. Đọc nhãn cho miss xong.
6. `loadApps` trả về; `Collator` xong; `apps` phát lần đầu không rỗng.
7. `seedIfEmpty` + `ensureFitsGrid` + `catchUp` xong (trong `LayoutRepositoryImpl.layout`).
8. `pages` phát lần đầu tới `HomeViewModel` (`gridStatus` = Ready).
9. Ô đầu tiên được bind, **bitmap icon đầu tiên** hiện, và đủ icon cho 24 ô + dock (nơi nút cổ chai tiếp theo sẽ lộ ra).
10. Thời gian ghi cache (nền, chỉ để biết, không ngưỡng).

**Ngưỡng đạt:**

| Ca | Máy | Đạt khi |
|---|---|---|
| Cache nóng (đã chạy một lần sau cài, `force-stop`) | Oppo A3x | "Pages sẵn sàng" (pha 8): **trung vị ≤ 1500 ms, lượt tệ nhất ≤ 1800 ms** (hôm nay 6466–6608). Pha nhãn (4+5) ≤ 100 ms ở trung vị; miss = 0 |
| Chưa có cache (xóa file / `pm clear`), lần đầu | Oppo A3x | Pha 8 không tệ hơn "trước" quá **+100 ms** ở trung vị |
| Cache nóng | Live 4 | Pha 8 **không tệ hơn "trước"** (cho +30 ms nhiễu) |
| Chưa có cache | Live 4 | Không tệ hơn "trước" quá +50 ms |
| Sự kiện package khi đang chạy (cài 1 app) | Oppo A3x | Danh sách mới trong ≤ 1 s (hôm nay ~5 s); miss = 1 |
| Ghi cache | Cả hai | Pha 6 không dài hơn khi không ghi; file < 100 KB |
| Sau khi khởi động lại máy | Oppo A3x | **Chỉ ghi nhận**, không ngưỡng (chưa có số gốc) |

Không đạt ca đầu: dừng, báo điều phối kèm bảng pha; cân nhắc nút cổ chai kế tiếp thay vì vá.

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Oppo A3x (đo) + Vsmart Live 4 (chức năng, không tệ hơn). Dữ liệu: bản cài sạch, một app người dùng có APK, một work profile tạm (`pm create-user --profileOf 0 --managed Work`, nhớ `pm remove-user`). Thứ tự: cài sạch (không cache) → lưới như cũ → mở lại nóng → thứ tự nhãn trước/sau → cập nhật một app → gỡ một app lúc launcher không chạy → đổi ngôn ngữ → làm hỏng cache 5 kiểu → work profile (cài/gỡ) → (nếu có Task 4) kiểm lại nền + kéo-thả. Hồi quy: M4 Task 4 `catchUp`, cài/gỡ app đang chạy, ẩn/bỏ ẩn, tìm kiếm a-z, Thư viện.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Oppo A3x + Vsmart Live 4, 2026-11-05, `feature/lc-label-cache@188d98e`, đo trên bản sao bỏ đi có log thời gian ở `HomeActivity.render` (ms từ lúc tiến trình bắt đầu tới lần `pages` không rỗng đầu tiên), xen kẽ trong một phiên. **Đạt một phần**; các chỗ trượt và chưa đo ghi rõ ở "Trượt và chưa đo" bên dưới.

### Số đo (ms tới lần `pages` không rỗng đầu tiên)

| Ca | Máy | Số đo | Ngưỡng | Kết quả |
|---|---|---|---|---|
| `main` | Oppo, 345 app | 6273, 6212, 6282; cuối phiên 6545, 6353 | (gốc) | — |
| Chưa có cache | Oppo | 6414, 6417 (+~80 ms so với cả 5 lượt `main`) | +100 ms | Đạt |
| Cache nóng | Oppo | 1617, 1593, 1565, 1569; sau đó 1648 (trung vị ~1580, tệ nhất 1648) | trung vị ≤ 1500, tệ nhất ≤ 1800 | **Tệ nhất đạt; trung vị TRƯỢT ~80 ms** |
| `main` | Live 4, 87 app | 1598, 1528, 1540; 1642, 1641 | (gốc) | — |
| Chưa có cache | Live 4 | 1745 (một lượt, ~+155 ms) | +50 ms | **TRƯỢT** (chỉ xảy ra sau cài mới hoặc đổi ngôn ngữ) |
| Cache nóng | Live 4 | 1184, 1141, 1181 | không tệ hơn `main` (+30) | Đạt (tốt hơn ~400 ms) |
| Task 1 (`sourceDir`) | Cả hai | `pm path` của app mình khác ở cả 3 lần cài trên cả hai máy; Settings ở `/system_ext`; `pm list packages -s -f`: 46 (Oppo) và 31 (Live 4) gói hệ thống nằm dưới `/data/app/` | khác sau mỗi cài | Đạt |

Hỏng cache trên Oppo: file bị xóa, rỗng, JSON cụt, `{"v":99}`, `n` sai, và đổi trường `locale` (giả lập đổi ngôn ngữ) đều rơi về đọc đủ (~6,6 s), ghi lại file hợp lệ 81.321 byte, không crash.

### Trượt và chưa đo (nói thẳng)

- **Trượt:** Oppo cache nóng, trung vị ~1580 ms so với mục tiêu 1500 ms (hụt ~80 ms; tệ nhất vẫn dưới 1800). Live 4 chưa có cache, ~+155 ms so với giới hạn +50 ms (một lượt đo, nhiễu chưa loại được; ca chỉ xảy ra một lần sau cài hoặc đổi ngôn ngữ).
- **Chưa đo:** khởi động lại máy; sự kiện package khi đang chạy (cần cài thứ gì đó lên máy chủ dự án nên bỏ); so thứ tự sắp có/không cache; thời gian icon vẽ xong; Live 4 với cache hỏng (chỉ làm trên Oppo); cập nhật app thật qua Play Store (chỉ suy luận); work profile.
- **Không tách pha 1-10:** chỉ ghi được "sort xong" và "pages đầu tiên". Vì vậy chưa biết ~1580 ms của Oppo (so với `getActivityList` xong ~940 ms ở P1) nằm ở đâu: đọc cache + parse, `seed/catchUp`, hay `HomeViewModel`. Đó là nút cổ chai kế tiếp và cần tách pha nếu muốn xuống dưới 1500 ms.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-05 | Plan | Viết plan + khung knowledgement | `docs: add LC plan` | Duyệt 2026-11-05; bỏ Task 4 |
| 2026-11-05 | 2, 3 | `LabelCache.kt`; nối vào `loadApps` (hit theo khóa + `sourceDir`, miss đọc như cũ, ghi nền, nhãn dự phòng không vào cache). `git merge origin/main`. Build xanh | `188d98e` | Đo xong, xem trên |
| 2026-11-05 | 1 | Điều phối chạy `pm path` trên hai máy | — | Đạt |
| 2026-11-05 | 5 | Knowledgement hoàn chỉnh, điền bằng chứng | (commit `docs: close LC`) | Không rebase (điều phối/chủ dự án rebase lúc cuối) |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Không có app ma | Đọc code: danh sách `loaded` được dựng chỉ từ `infos` của `getActivityList`; cache chỉ tra `cached?.get(key)`; chưa thử gỡ-app-rồi-mở lại trên máy | Đạt theo code; **chưa kiểm máy** |
| 2 | Cache hỏng không làm chết app | Oppo: xóa, rỗng, cụt, `v`=99, `n` sai, `locale` đổi: đều về đọc đủ, ghi lại 81.321 byte, không crash | Đạt (Oppo; chưa chạy trên Live 4) |
| 3 | `stamp` đúng | Task 1: `sourceDir` đổi ở mỗi lần cài. Chưa đếm hit/miss sau cập nhật một app | Một phần: stamp đổi (đo); hành vi miss **chưa đo** |
| 4 | Thứ tự sắp giống nhau | Đọc code: nhãn cùng chuỗi, cùng `Collator`, cùng thứ tự đầu vào, `sortedWith` ổn định. Chưa so danh sách | **Chưa đo** |
| 5 | Ghi cache ngoài đường găng, nguyên tử, không ghi khi không đổi | Đọc code (`scheduleSave` trên `scope.launch(io)`, `AtomicFile`, so `onDisk`). Chưa thử kill giữa lúc ghi | Đạt theo code; chưa thử kill |
| 6 | Locale/fingerprint khác → bỏ cache | Đổi trường `locale` trong file → đọc đủ ~6,6 s (mô phỏng). Đổi ngôn ngữ thật và fingerprint chưa thử | Một phần (mô phỏng) |
| 7 | Lần đầu không chậm hơn | Oppo +~80 ms (đạt, ≤ 100); Live 4 +~155 ms (một lượt, **trượt** +50) | Oppo đạt, Live 4 trượt |
| 8 | Phát lần hai (Task 4) | Task 4 đã bỏ | Không áp dụng |
| 9 | Mục lỗi thời như hôm nay | Đọc code: `launch`/`AppLaunchFailed` không đổi, khóa luôn từ hệ thống | Đạt theo code |
| 10 | Không đụng `launch`, không sửa ngoài Vùng file | `git diff origin/main --stat`: chỉ `LabelCache.kt`, `LauncherAppsDataSource.kt`, hai file docs LC | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: đánh dấu task phụ P1 (cache nhãn) xong, kèm số đo: Oppo 345 app ~6,3 s → ~1,6 s (cache nóng); Live 4 ~1,6 s → ~1,2 s.
- `project_structure.md`: thêm `data/apps/LabelCache.kt` (cache nhãn, file `noBackupFilesDir/app_label_cache.json`).
- `room-schema.md`: Q6 ghi "đã làm ngoài Room (file JSON), xem LC".
- `AGENTS.md`: mục "Nạp danh sách app" thêm số đo trước/sau; phần còn lại xem "Bài học cho AGENTS.md" trong knowledgement. Mục P1 giữ, thêm một dòng "xử lý bởi LC".
- Ghi nhận rủi ro đã biết: nhãn đổi mà app không cập nhật (D7); trượt trung vị 1500 ms trên Oppo và +155 ms ca không cache trên Live 4.
