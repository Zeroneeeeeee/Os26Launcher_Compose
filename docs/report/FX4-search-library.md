# FX4 — Tìm kiếm và App Library: chỉ mục, danh bạ, shortcut, khởi động (B11, C1, C6, C7, C8, C9) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không dùng đợt này). Lệnh viết cho CMD/PowerShell. **Agent không chạy adb, không cài APK**: mọi bước "Live 4" do điều phối chạy (quyết định 7 ở mục 12 của `MultiAgent-Workflow.md`), agent chỉ build và đưa lệnh/số cần đọc.

**Nhánh:** `fix/fx4-search-library` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX4-search-library` · **PR:** #34 · **Bắt đầu:** 2026-11-11 · **Plan duyệt:** 2026-11-11 (điều phối, vòng 1/5)
**Trạng thái:** Chờ review (PR Ready for review)
**Khóa:** không (không đổi schema Room, không đụng file độc quyền: `HomeActivity` thuộc FX2, `HomeViewModel` thuộc FX1)

**Goal:** Sửa sáu mục của báo cáo rà soát `docs/report/Audit-Report-2026-11-10.md` thuộc vùng tìm kiếm / App Library mà không đổi hành vi nhìn thấy được:
- **B11** chỉ mục tìm kiếm không còn chuẩn hóa lại mọi nhãn ở mỗi lần dựng; chuẩn hóa nhanh hơn nhiều; danh bạ 2000–5000 người không làm mỗi lần mở/đóng/quay lại tốn cả giây.
- **C1** quay về Home sau hơn 5 giây không còn phát `null` (vòng quay "Đang tải" + dựng lại mọi ô) ở App Library và ở tìm kiếm.
- **C6** rời launcher khi ô tìm kiếm còn mở: tên danh bạ rời RAM và `ContentObserver` bị gỡ; quay lại thì nạp lại, ô tìm kiếm và chuỗi gõ giữ nguyên.
- **C7** truy vấn số điện thoại hủy được và có cache theo phiên.
- **C8** hàng shortcut trong tìm kiếm chỉ nạp đúng một icon, có cache.
- **C9** danh mục Cài đặt hệ thống và chỉ mục tìm kiếm không dựng lúc khởi động lạnh.

**Architecture:** Giữ nguyên luồng M8d/G3a (`SearchViewModel` → `SearchIndex` → `SearchUiState`). Thay đổi gói gọn ở ba tầng: (1) `domain/search`: `AppSearchMatcher.normalize` thành một vòng ký tự có bảng tra, giữ bản cũ làm đường dự phòng nên kết quả giống hệt; `SearchIndex` nhận các **nguồn đã chuẩn hóa sẵn** (`SearchIndex.Source`) thay vì danh sách thô. (2) `ui/search/SearchViewModel`: mỗi nguồn (app, Cài đặt, danh bạ, shortcut) tự giữ `Source` của mình trong `StateFlow`; chỉ mục là phép ghép các `Source` (không chuẩn hóa lại); mọi thứ nặng chỉ bắt đầu sau lần `open()` đầu tiên; thêm `onHostStopped()` cho C6; cache số điện thoại theo phiên. (3) `data/`: `CancellationSignal` ở truy vấn danh bạ; `ShortcutRepository.shortcutIcon` + `LruCache`. App Library: bỏ `.onStart { emit(null) }` ở use case.
**Tech Stack:** Kotlin, Views, Hilt, coroutines/StateFlow (đã có). **Không thêm dependency.** Dùng `android.os.CancellationSignal`, `android.util.LruCache` (framework).
**Spec:** Mục B11, C1, C6, C7, C8, C9 và "Quyết định của chủ dự án (2026-11-11)" trong `docs/report/Audit-Report-2026-11-10.md`; `docs/plans/M8b-search.md`, `M8c-search-pill.md`, `M8d-search-extras.md`, `G3a-contacts-search.md`, `AP-applib-animations.md`; `docs/lessons/search.md`, `startup-performance.md`, `permissions-and-device-testing.md`.

> Mọi dòng số ở dưới tính từ `b1af810` (đã đọc lại code hôm nay, 2026-11-11, **không thay đổi** so với báo cáo). Nhãn nguồn: **đo được** / **đọc code** / **đọc tài liệu** / **suy luận chưa kiểm chứng**.

## Đã kiểm lại từng khẳng định của báo cáo với code hiện tại

| Mục | Khẳng định | Kết quả khi đọc code | Nhãn |
|---|---|---|---|
| B11 | Mọi nguồn đổi đều dựng lại cả chỉ mục | Đúng: `SearchViewModel.kt:145-161` `combine(visibleApps, settingEntries, shortcutEntries, prefs, contactEntries)` gọi `buildIndex` (`:200-224`) → `SearchIndex(candidates)` (`SearchIndex.kt:26-33`) chuẩn hóa từng nhãn và từ khóa. Nguồn kích hoạt trong một phiên: `settingEntries` (init), `shortcutEntries` (`open()` → `refreshShortcuts`), `contactEntries` (nạp lúc `open()`, xóa lúc `close()`), `visibleApps` (phát lại sau mỗi lần `uiState` khởi động lại, xem C1), `prefs` (đổi công tắc) | đọc code |
| B11 | Chuẩn hóa tốn ~0,2 ms mỗi nhãn | M8d đo đường M8b (chuẩn hóa lại ~344 nhãn mỗi phím) p50 67–82 ms ⇒ ~0,2 ms/nhãn trên Oppo (APK debug); bài học M8d "Chuẩn hóa nhãn một lần vào chỉ mục". Live 4 **chưa đo** → Task 1 đo | đo được (Oppo) / ngoại suy (2000–5000 danh bạ ≈ 0,4–1 s mỗi lần dựng) |
| B11 | `normalize` dùng NFD + 2 regex | Đúng: `AppSearchMatcher.kt:23-30` (`Normalizer.normalize` NFD, `replace(Regex("\\p{Mn}+"))`, `lowercase`, `replace('đ','d')`, `replace(Regex("\\s+"))`, `trim`) | đọc code |
| C1 | `.onStart { emit(null) }` làm Library chớp | Đúng: `GetCategoryGroupsUseCase.kt:31`; `HomeViewModel.kt:90-91` `stateIn(WhileSubscribed(5_000), null)`. `AppRepositoryImpl.apps` là `stateIn(WhileSubscribed(5_000))` nên sau 5 giây upstream chạy lại và `onStart` phát `null` **trước** giá trị cũ. Cùng mẫu: `SearchViewModel.kt:79-81` | đọc code (báo cáo: không thấy trên Live 4) |
| C6 | Rời launcher giữ danh bạ trong RAM | Đúng: chỉ `close()` (`:435-443`) xóa; `HomeActivity.onNewIntent` (nút Home) gọi `search.closeIfOpen()`; không có `ON_STOP`. `SearchOverlayController.kt:74-76` đã có `LifecycleEventObserver` chỉ xử lý `ON_RESUME` | đọc code |
| C7 | `numbersFor` không hủy được, chạy mỗi phím | Đúng: `SearchViewModel.kt:330` `withContactNumbers`, `ContactsRepositoryImpl.kt:80-114` `query` không có `CancellationSignal`; `mapLatest` chỉ hủy coroutine, không hủy lời gọi `ContentResolver` đang chặn | đọc code |
| C8 | Mỗi hàng shortcut nạp tới 4 icon | Đúng: `SearchViewModel.kt:487-489` gọi `getShortcuts(key, sizePx)`; `ShortcutRepositoryImpl.kt:38-59` `take(4).map { iconOf(...) }` (mỗi cái 1 IPC `getShortcutIconDrawable` + 1 bitmap) rồi lọc lấy 1. Lưu ý: chỉ mục dùng `getShortcuts(key, 0)` (`:244`) nên `iconOf` trả `null` ngay, không tốn icon | đọc code |
| C9 | Danh mục Cài đặt dựng lúc khởi động | Đúng: `SearchViewModel.kt:107-118` chạy trong `init`; `HomeActivity.kt:47` tạo ViewModel, `:176` thu `uiState` ở `STARTED` nên `computed`/`searchIndex` chạy ngay; `SystemSettingsRepositoryImpl.build` (`:54-82`) làm ≥ 2 lời gọi PackageManager cho mỗi trong ~25 mục. Cold start: chỉ mục dựng ít nhất 2 lần trước khi người dùng mở tìm kiếm (khi có app, khi có danh mục) | đọc code (chi phí chưa đo) |
| — | "Khớp tiếng Việt không dấu đúng" | Giữ nguyên: là ràng buộc cứng của Task 2 và của bước đối chiếu cuối | báo cáo "Đã kiểm và ổn" |

## Vì sao chọn thiết kế này

### Vấn đề cần giải

- **Cái đắt là chuẩn hóa, và nó bị làm lặp.** Đo (M8d, Oppo, debug): ~0,2 ms/nhãn. Chỉ mục chứa app (~90–345), Cài đặt (~25 + từ khóa), shortcut (vài chục–trăm), **danh bạ tới 5000** (`MAX_CONTACTS`). Mỗi sự kiện của bất kỳ nguồn nào dựng lại toàn bộ ⇒ danh bạ lớn bị chuẩn hóa nhiều lần mỗi phiên, trong khi tên danh bạ gần như không đổi trong phiên.
- **Ràng buộc riêng tư của G3a (đã chốt):** tên danh bạ chỉ ở RAM khi lớp tìm kiếm mở, không ra DataStore/Room/file/log, `allowBackup="true"`. Mọi bản sao đã chuẩn hóa của tên cũng là dữ liệu danh bạ: phải sống/chết cùng `contactEntries`.
- **Khởi động lạnh đang bị giành tài nguyên:** P1 đo (Oppo, debug) đọc nhãn chiếm ~84% thời gian tới lúc có lưới; mọi việc nền khác lúc khởi động đều đua CPU và binder với `getActivityList`. Danh mục Cài đặt (~50 IPC) và hai lần dựng chỉ mục không phục vụ gì cho tới khi người dùng mở tìm kiếm.
- **File nóng:** `HomeActivity` (FX2) và `HomeViewModel` (FX1) không được sửa. Mọi thứ phải nằm trong `ui/search`, `data/search`, `domain/search`, `GetCategoryGroupsUseCase`, `ShortcutRepositoryImpl`, cộng một nhánh `ON_STOP` trong `SearchOverlayController` (file R1, **không** trong danh sách độc quyền của mục 5.2; G3a đã sửa chính chỗ này).
- **Không đổi kết quả tìm.** Thứ tự ứng viên trong chỉ mục (app, Cài đặt, danh bạ, shortcut) quyết định thứ tự cùng hạng; cả hai phải giữ.
- **Debug chậm ~4,4 lần release** (bài học RB, Oppo): không dùng số debug để nói "người dùng thấy".

### B11 — các phương án

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `SearchIndex.Source` theo nguồn + chuẩn hóa nhanh (chọn)** | Mỗi nguồn dựng một `Source` bất biến (mảng `Entry` đã chuẩn hóa) khi **chính nguồn đó** đổi; `SearchIndex` là danh sách `Source` duyệt lần lượt theo thứ tự cũ (không nối mảng). `normalize` thành vòng ký tự một lượt + bảng tra cho Latin/tiếng Việt, ký tự lạ rơi về bản cũ | Danh bạ chuẩn hóa **một lần mỗi lần nạp**; shortcut/đóng lớp/quay lại không đụng danh bạ; vòng ký tự giảm hằng số cho cả lần nạp đầu; thứ tự và kết quả giữ nguyên chứng minh được | Đổi `SearchIndex` (domain) và phần `combine` của ViewModel; thêm một lớp `Source`; cần tách nguồn shortcut vì nó lọc theo app hiển thị | **Có** |
| B. Memo toàn cục `HashMap<String,String>` raw → normalized trong `AppSearchMatcher` | Giữ nguyên `SearchIndex`; tra bảng trước khi chuẩn hóa | Sửa một file; mọi chỗ gọi `normalize` hưởng lợi | Vẫn duyệt và tra băm cả 2000–5000 chuỗi ở mỗi lần dựng; bản đồ chứa **tên danh bạ** ở một singleton nằm ngoài vòng đời `contactEntries` ⇒ phải tự nghĩ thêm cách xóa theo phiên, tăng bề mặt rò rỉ riêng tư; phình không giới hạn; luồng nhiều thread cần `ConcurrentHashMap` | Không |
| C. Chỉ tăng tốc `normalize` (đường tắt ASCII như báo cáo gợi ý), không giữ gì | Một file | Ít rủi ro nhất | Đường tắt **chỉ ASCII** gần như không trúng với tên người Việt (đa số có dấu) ⇒ gần như không giảm; vẫn dựng lại 4 lần/phiên | Không (nhưng tăng tốc `normalize` là một nửa của A, theo hướng vòng ký tự + bảng tra) |
| D. Không gộp: mỗi nguồn một `SearchIndex` riêng, tìm từng nguồn rồi trộn theo hạng lúc truy vấn | Không bao giờ dựng chỉ mục chung | Không ghép gì | Phải viết lại bước xếp hạng/cắt `maxResults`/phá hòa theo `SearchKind.priority`; dễ lệch thứ tự so với hiện tại (đúng thứ mà ràng buộc "kết quả giống hệt" cấm); đổi nhiều hơn A | Không |

**Vì sao A:** (1) báo cáo B11 đưa chính hai gợi ý này ("chuẩn hóa mỗi nguồn một lần và giữ lại", "đường tắt ASCII hoặc vòng ký tự") — *đọc code*. (2) Số đo M8d cho thấy lợi ích lớn nhất đã đến từ "chuẩn hóa một lần vào chỉ mục" (p50 67–82 → 5–7 ms, **đo được** Oppo); A đẩy đúng nguyên lý đó thêm một bước: nguồn nào đổi mới chuẩn hóa lại nguồn đó. (3) Ràng buộc riêng tư G3a làm B xấu hơn A: bản sao chuẩn hóa của tên danh bạ trong `Source` chết cùng `contactEntries` (một chỗ xóa, vẫn là `contactEntries.value = emptyList()` → `Source.EMPTY`). (4) Vì sao chuẩn hóa nhanh dùng **bảng tra + dự phòng** chứ không đường tắt ASCII: tên tiếng Việt có dấu nên ASCII-only gần như vô dụng; bảng tra được **dựng bằng chính thuật toán cũ** trên từng ký tự nên kết quả giống hệt, ký tự nào bảng không chắc chắn thì cả chuỗi chạy bản cũ — chứng minh được mà không cần test (dự án không làm test; xem Review Focus 1 về bước đối chiếu bằng log một lần).
**Đánh đổi chấp nhận:** thêm một lớp (`Source`) và một ViewModel phức tạp hơn một chút (nhiều `StateFlow` thay cho một `combine`); mỗi nguồn giữ thêm một bản chuỗi chuẩn hóa (danh bạ 5000 × vài chục byte ≈ 300 KB, chết khi đóng). Xét lại nếu Task 1/2 cho thấy chuẩn hóa nhanh một mình đã đạt ngân sách (xem Task 4 "bắt buộc kiểm tay").

### C9 — các phương án

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Dựng ở lần `open()` đầu tiên (chọn)** | `init` không làm gì; `open()` bật cờ "chỉ mục hoạt động", gọi `systemSettings.entries()` (đã nhớ theo ngôn ngữ; gọi lại ở mỗi `open()` là rẻ) và bắt đầu dựng `Source` của app | Chi phí khởi động lạnh = 0 cho tới khi người dùng thật sự tìm; không cần đoán "sau khung đầu" là lúc nào; người dùng không bao giờ mở tìm kiếm thì không bao giờ trả | Lần mở đầu tiên của một process phải đợi dựng (nền, IO/Default); truy vấn gõ trong vài chục ms đầu có thể thấy `LOADING` hoặc thiếu mục Cài đặt rồi tự bổ sung | **Có** |
| B. Sau khung hình đầu (`IdleHandler`/`doOnPreDraw`/`delay`) | Dựng ngay sau khi Home vẽ xong | Lần mở đầu tiên đã có sẵn | Vẫn tranh binder/CPU với nạp danh sách app trên máy chậm (Oppo debug đọc nhãn tới ~6 s sau khung đầu, P1); "sau khung đầu" không phải "sau khi danh sách app xong"; cần hook ở `HomeActivity` (bị khóa) hoặc đoán thời điểm trong ViewModel | Không |
| C. Giữ ngay lúc khởi động nhưng đẩy xuống ưu tiên thấp/luồng riêng | Chỉ đổi dispatcher | Một dòng | Không bỏ việc, chỉ dời ai bị giành; binder vẫn đông; chỉ mục vẫn dựng 2 lần lúc khởi động | Không |
| D. Lưu danh mục xuống đĩa theo ngôn ngữ + fingerprint | Lần khởi động sau đọc file | Không IPC ở lần sau | Thêm file, thêm chỗ cần vô hiệu hóa (cập nhật hệ thống, app ngoài đăng ký `WIFI_SETTINGS`, đổi ngôn ngữ), cho một việc mà A đã làm bằng 0 | Không |

**Vì sao A:** lợi ích kiểm chứng được *bằng log, không cần phép đo thời gian tinh*: sau A, log cho thấy catalog **không** được dựng trước `open()` (*sẽ đo được* ở Task 5). Lập luận đảm bảo không lùi UX: người dùng cần mở lớp rồi mới gõ — khoảng thời gian đó (hàng trăm ms) đủ cho dựng nền ở Default/IO (chi phí catalog ~ 50 IPC chưa đo, ước vài chục ms, *suy luận chưa kiểm chứng*, Task 1 đo). B/C chỉ dời chi phí; D thêm bề mặt lỗi.
**Đánh đổi chấp nhận:** lần mở đầu tiên sau khi process khởi động có thể trễ vài chục ms để mục Cài đặt hiện; xét lại (thêm prewarm khi chạm pill) chỉ nếu chủ dự án thấy trễ khi dùng thật.

### C1 / C6 / C7 / C8 — chọn ngắn gọn

- **C1:** bỏ `.onStart { emit(null) }` ở `GetCategoryGroupsUseCase`; ở `SearchViewModel` biến `visibleApps` thành `stateIn(viewModelScope, WhileSubscribed(5_000), null)` (null chỉ là giá trị ban đầu = "chưa nạp"). Không chọn "giữ `onStart` rồi lọc null sau lần có dữ liệu đầu" (cần cờ trạng thái trong use case) và không chọn nâng `WhileSubscribed` lên 60 s/`Eagerly` (che chứ không sửa; giữ callback gói được đăng ký ở `AppRepositoryImpl` đi ngược thiết kế "rời launcher 5 giây thì hủy đăng ký"). `StateFlow` giữ giá trị cuối khi upstream dừng (*đọc tài liệu*: `replayExpiration` mặc định không hết hạn) và lọc trùng theo `equals` (`AppItem`, `CategoryGroupApps` là data class) nên danh sách phát lại giống hệt **không** tới UI.
- **C6:** thêm nhánh `ON_STOP` vào `LifecycleEventObserver` **đã có** ở `SearchOverlayController` (không đăng ký thêm observer mới, không đụng `HomeActivity`); gọi `SearchViewModel.onHostStopped()`. Phương án thay: `onWindowVisibilityChanged` của `SearchOverlayView` (hoàn toàn trong `ui/search`, nhưng đó là tín hiệu cửa sổ, không phải vòng đời, và recreate cũng gọi) — loại vì không bằng `ON_STOP` về nghĩa. Phương án thay nữa: `ProcessLifecycleOwner` — cần thư viện `lifecycle-process`, bị cấm.
- **C7:** `CancellationSignal` gắn vào `ContentResolver.query` + cache `contactId → số` trong ViewModel (không trong repository: repository giữ nguyên "chỉ đọc, không nhớ gì"; xóa cùng lúc xóa `contactEntries`).
- **C8:** `ShortcutRepository.shortcutIcon(key, id, sizePx)` + `LruCache` có TTL 60 s (cùng `SHORTCUT_CACHE_MS`). TTL thay cho việc tự vô hiệu hóa: icon shortcut động có thể đổi, ta không muốn thêm API xóa cache.

### Kiểm với dữ liệu giả (không dùng quyền danh bạ thật)

Chủ dự án (2026-11-11) cấm cấp `READ_CONTACTS` và cấm chèn vào danh bạ của máy. Ba chỗ còn lại để có 2000 danh bạ phục vụ B11/C6/C7:

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Nguồn giả trong app, commit tạm (chọn)** | `FakeContactsRepository` (2000 `ZZTEST`, nửa có dấu, số giả `0900000001`…, độ trễ mô phỏng, kích hoạt "danh bạ đổi" bằng broadcast) bind thế `ContactsRepositoryImpl` trong `di/SearchModule.kt`; `hasPermission() = true` nên cổng quyền bị bỏ qua; một commit, `git revert` trước PR | Không chạm danh bạ máy, không cần quyền; dữ liệu và độ trễ lặp lại được; thấy hủy truy vấn và quan sát `ContentObserver` qua log đăng ký/hủy | Không đo được chi phí đọc `ContentResolver` thật và `CancellationSignal` thật; mã tạm phải gỡ | **Có** |
| B. Đặt nguồn giả ở `app/src/debug/` | Source set debug thay binding | Không vào build release | Hilt không cho hai `@Binds` cùng kiểu: phải **gỡ** binding khỏi `main` và dựng module riêng cho `debug` và `release` (sửa cấu trúc DI vĩnh viễn, ngoài phạm vi). Bản tạm trong `main` rồi revert đơn giản hơn | Không |
| C. Chèn vào provider danh bạ (kế hoạch cũ) | Công cụ `app_process`/`content insert` | Đo đường thật | Cần `READ_CONTACTS` (lộ danh bạ thật), ghi vào máy của chủ dự án | **Bị chủ dự án bác** |

**Phần kiểm được và không kiểm được (nói thẳng):** B11 (chuẩn hóa, ghép chỉ mục, đếm lần dựng, đối chiếu kết quả) **kiểm đầy đủ**: chi phí nằm ở chuẩn hóa và ghép, không ở provider. C6 (ViewModel bỏ tên khỏi RAM lúc `ON_STOP`, nạp lại lúc `ON_RESUME`) kiểm được; việc gỡ `ContentObserver` thật chỉ kiểm bằng đọc code + log `stopContactsWatch()`. C7: cache số + mã phiên + hủy coroutine kiểm được nhờ độ trễ mô phỏng; `CancellationSignal` thật trên `ContentResolver.query` **chỉ đọc code** (kể cả `OperationCanceledException`). Thời gian `loadNames` trong log là **độ trễ mô phỏng, không phải số đo provider** (không dùng nó để kết luận gì về chi phí đọc thật).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Sửa đúng B11, C1, C6, C7, C8, C9; **không** làm B9/C10 (FX5), B12 (FX6), HomeViewModel (FX1/FX7) | Chủ dự án 2026-11-11, bảng "Kế hoạch sửa" của báo cáo |
| D2 | Máy: **chỉ Vsmart Live 4**; điều phối chạy mọi lệnh adb; có sao lưu và trả lại | Chủ dự án 2026-11-11; quyết định 7 mục 12 của `MultiAgent-Workflow.md` |
| D3 | **Danh bạ chỉ là dữ liệu giả** (2000 người `ZZTEST` sinh trong app). **Không** cấp `READ_CONTACTS` cho app trên Live 4 và **không** chèn gì vào danh bạ của máy (quyền sẽ lộ cả danh bạ thật của chủ dự án). Thay cho quyết định cũ "chèn ~2000 danh bạ giả vào máy" | Chủ dự án 2026-11-11 (đổi sau bảng quyết định của báo cáo), qua điều phối |
| D11 | Nguồn danh bạ giả tạm trong app: `FakeContactsRepository` gắn thế `ContactsRepositoryImpl` ở `di/SearchModule.kt`, trong **một commit tạm** có dấu `FX4_TEMP_FAKE_CONTACTS`, `git revert` trước khi mở PR (như log `FX4`) | Claude đề xuất, điều phối duyệt 2026-11-11; so sánh ở "Kiểm với dữ liệu giả" |
| D12 | Mục shortcut trong Cài đặt tìm kiếm được tạm bật để kiểm C8, trả lại cuối | Chủ dự án 2026-11-11 |
| D4 | `normalize` giữ kết quả **giống hệt** bản hiện tại (kể cả `đ/Đ`, bàn phím NFD, ư/ơ); ký tự lạ rơi về bản cũ | Ràng buộc từ "Đã kiểm và ổn" của báo cáo; Claude đề xuất cách làm |
| D5 | Chỉ mục = các `SearchIndex.Source` duyệt theo thứ tự cũ app → Cài đặt → danh bạ → shortcut | Claude đề xuất, chờ duyệt (giữ thứ tự cùng hạng, mục 2 của `lessons/search.md` M8b) |
| D6 | Bản sao đã chuẩn hóa của tên danh bạ sống/chết cùng `contactEntries`; thêm xóa ở `onHostStopped()` | Claude đề xuất; bám quyết định 1 của G3a (chỉ RAM) |
| D7 | C6 giữ `isOpen` và chuỗi gõ khi `ON_STOP`; `ON_RESUME` nạp lại danh bạ (cơ chế `refreshContacts` sẵn có) | Báo cáo mục C6 |
| D8 | C9: dựng ở `open()` đầu tiên (không "sau khung đầu") | Claude đề xuất, so sánh ở trên |
| D9 | C8: `LruCache` 32 mục, TTL 60 s, khóa `(package, id, userSerial, size)` | Claude đề xuất, chờ duyệt |
| D10 | Mọi log tạm gắn tag `FX4`, chỉ ghi số đếm/ms/băm; không ghi tên, số, lookup key; gỡ ở Task 9 | AGENTS.md ("log không chứa dữ liệu cá nhân", `lessons/search.md` G3a) |

## Ngoài phạm vi / ghi để sau

- Không đổi UI tìm kiếm, bố cục hàng kết quả, số kết quả mỗi loại, hay luật xếp hạng.
- **Không** gating `computed`/`uiState` theo `isOpen` (khi lớp đóng, `compute` với truy vấn rỗng vẫn chạy khi danh sách app đổi; rất rẻ). Ghi để sau nếu đo thấy tốn.
- **Không** sửa `HomeViewModel.categoryGroups` (`WhileSubscribed(5_000)`, FX1): C1 sửa ở nguồn (use case) nên không cần.
- Mở rộng đo/hủy sang `loadNames`: có (Task 7, cùng helper) — nhưng không cache `loadNames` giữa các phiên (sẽ trái G3a).
- Không đo bản release (không gắn với kết luận nào cần số release); mọi số ghi rõ **debug** (memory `release-vs-debug-startup`). Nếu điều phối muốn số release cho C9, xem Task 5 Step 4 (tùy chọn).
- Oppo A3x không kiểm đợt này (chủ dự án: chỉ Live 4). C1 trên Oppo 345 app mà báo cáo đề nghị thử lại: để sau.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `domain/search/AppSearchMatcher.kt` | Sửa (`normalize` vòng ký tự + bảng tra; giữ bản cũ làm đường dự phòng) | Không |
| `domain/search/SearchIndex.kt` | Sửa (`Source`, constructor nhận danh sách `Source`) | Không |
| `ui/search/SearchViewModel.kt` | Sửa (nhiều Task) | Không (vùng FX4) |
| `data/search/ContactsRepositoryImpl.kt` | Sửa (C7: `CancellationSignal`, cả `numbersFor` lẫn `loadNames`). **Không chạy được trên máy** (không có quyền danh bạ): kiểm bằng đọc code + build | Không |
| `data/search/FakeContactsRepository.kt` | **Tạo, TẠM** (dấu `FX4_TEMP_FAKE_CONTACTS`; commit riêng, `git revert` ở Task 9) | Không |
| `di/SearchModule.kt` | Sửa **một dòng, TẠM**: `bindContactsRepository(impl: FakeContactsRepository)` thay `ContactsRepositoryImpl` (cùng commit tạm, revert ở Task 9). Không đổi vĩnh viễn | Dùng chung (DI); không nhánh nào khác khai file này |
| `data/search/SystemSettingsRepositoryImpl.kt` | Chỉ log tạm Task 1 (gỡ Task 9); không đổi hành vi | Không |
| `domain/usecase/GetCategoryGroupsUseCase.kt` | Sửa (C1: bỏ `onStart { emit(null) }`, bỏ import) | Không |
| `data/apps/ShortcutRepositoryImpl.kt` | Sửa (C8) — file này thuộc FX4; **phần còn lại của `data/apps/*` thuộc FX5, không đụng** | Không |
| `domain/repository/ShortcutRepository.kt` | Sửa (thêm `shortcutIcon`) | Không (điều phối đồng ý 2026-11-11) |
| `ui/home/controller/SearchOverlayController.kt` | Sửa **một dòng**: nhánh `ON_STOP` trong observer sẵn có (C6) | Không độc quyền; điều phối đồng ý 2026-11-11 |
| `ui/home/HomeActivity.kt`, `ui/home/HomeViewModel.kt` | **KHÔNG SỬA** (FX2 / FX1) | Độc quyền của nhánh khác |

## Global Constraints

- Không test tự động, không thư viện mới. Không đổi schema Room, không thêm khóa DataStore, không đổi Manifest.
- Luật phụ thuộc `ui → domain ← data`: `domain/search` không `import android.*` (đếm thời gian tạm dùng `System.nanoTime`, log tạm đặt ở `SearchViewModel`/`data`). `ui` không import `data`.
- Mọi lời gọi nặng (PackageManager, ContentResolver, chuẩn hóa hàng loạt) ở luồng nền (`defaultDispatcher`/`io`), không ở Main.
- **Không** cấp `READ_CONTACTS` cho app trên Live 4, **không** ghi vào/đọc danh bạ thật của máy; không mở app Danh bạ/Quay số của máy trong lúc thử. Mọi dữ liệu danh bạ khi kiểm là dữ liệu giả sinh trong app (D3, D11); mã giả chỉ nằm trong commit tạm có dấu `FX4_TEMP_FAKE_CONTACTS`.
- Tên danh bạ, số điện thoại, lookup key **không** vào log (kể cả log tạm); chỉ số đếm, ms, băm của danh sách `stableId`.
- Commit tiếng Anh, `<type>: <imperative>` không quá 72 ký tự; tên file stage bằng đường dẫn tường minh (không `git add docs`/`-A`).
- Comment code tiếng Việt theo quy ước hiện có của file, ghi mã mục (`B11`, `C9`...).
- Build `gradlew.bat assembleDebug` trong worktree sau mỗi Task (nhớ chép `local.properties`), không báo xong Task nếu build đỏ.
- Mọi số đo ghi rõ máy (Live 4), bản (debug) và ngày; **không** nói "người dùng thấy" từ số debug.

## Review Focus

1. **Kết quả tìm kiếm giống hệt trước/sau**, kể cả tiếng Việt không dấu (`nguyen`/`nguyễn`, `duc`/`đức`, chữ hoa, khoảng trắng thừa, bàn phím NFD): so băm danh sách `stableId` theo truy vấn mẫu (bước ghi ở Task 1 và Task 9) **và** đối chiếu `normalize` mới/cũ trên toàn dải ký tự (Task 2).
2. Thứ tự ứng viên trong chỉ mục giữ app → Cài đặt → danh bạ → shortcut; `maxResults`, `minQueryLength` không đổi.
3. Tên danh bạ (và bản chuẩn hóa, và cache số) **không còn trong RAM** sau `close()` và sau `ON_STOP` (xem log đếm, không cần dump heap). Việc gỡ `ContentObserver` thật không kiểm trên máy được: bằng chứng là log `stopContactsWatch()` đã chạy + log hủy đăng ký của nguồn giả + đọc code `awaitClose` của `ContactsRepositoryImpl.changes()` (không đổi).
4. Không có `Source` nào dựng lại khi chỉ shortcut/đóng lớp/quay lại sau 5 giây (log đếm lần dựng từng nguồn).
5. Trước `open()` đầu tiên: không có lời gọi `SystemSettingsRepositoryImpl.build`, không có `Source` nào được chuẩn hóa (log).
6. Quay lại sau >5 giây: không phát `null` ở use case Library, không `LOADING` ở tìm kiếm.
7. Hủy truy vấn số: với nguồn giả (có độ trễ mô phỏng) thấy `cancelled` trong log khi gõ nhanh, cache trúng, không ghi cache sau khi phiên đã đóng (race `close()`). **`CancellationSignal` thật trên `ContentResolver.query` chỉ kiểm bằng đọc code** (không chạy được trên máy vì không có quyền danh bạ): soi `OperationCanceledException` bắt riêng trước `RuntimeException`, signal hủy khi coroutine hủy, không để thành log lỗi hay kết quả rỗng ghi vào cache.
8. `shortcutIcon` chỉ gọi `getShortcutIconDrawable` đúng một lần mỗi lần nạp; TTL hết hạn thì nạp lại.
9. Không còn chuỗi `FX4` và **không còn `FX4_TEMP_FAKE_CONTACTS`** trong mã nguồn khi đóng nhánh (`Grep` cả hai trên `app/`); `FakeContactsRepository.kt` không còn trong cây; `di/SearchModule.kt` bind lại `ContactsRepositoryImpl` và **không có trong diff cuối so với `main`**.
10. Không sửa file ngoài vùng; `HomeActivity`/`HomeViewModel` không có trong diff.
11. **Hồi quy trên bản sạch (không có nguồn giả):** chưa cấp quyền danh bạ thì hàng mời "Hiện danh bạ" vẫn hiện (ô trống và từ 2 ký tự); công tắc "Tìm trong danh bạ" tắt thì hàng mời biến mất.

---

### Task 1: Đo nền (log tạm `FX4`) và bộ truy vấn mẫu
**Bắt buộc kiểm tay:** không (điều phối chạy kịch bản và ghi số vào Nhật ký; đây là số nền cho mọi Task sau)
**Files:** `domain/search/AppSearchMatcher.kt`, `ui/search/SearchViewModel.kt`, `data/search/FakeContactsRepository.kt` (tạm, tạo), `di/SearchModule.kt` (tạm, một dòng), `data/search/SystemSettingsRepositoryImpl.kt`, `data/apps/ShortcutRepositoryImpl.kt`, `domain/usecase/GetCategoryGroupsUseCase.kt`
**Interfaces:** Consumes: code hiện tại (`b1af810`), `ContactsRepository` (interface giữ nguyên). Produces: log tag `FX4` (gỡ ở Task 9), nguồn danh bạ giả tạm (revert ở Task 9) và bảng số nền trong Nhật ký.
- [x] **Step 0: Nguồn danh bạ giả (commit riêng, tạm).** Tạo `data/search/FakeContactsRepository.kt` (`@Singleton`, implements `ContactsRepository`; mọi file/đoạn tạm ghi dấu `FX4_TEMP_FAKE_CONTACTS` trong comment). Hành vi: `hasPermission() = true` (bỏ qua cổng quyền); `loadNames(limit)` sinh 2000 `ContactEntry` (`contactId` 1..2000, `lookupKey` `fx4-<id>`, tên `ZZTEST <họ> <đệm> <tên> <số thứ tự 4 chữ số>`; id lẻ dùng họ/tên **có dấu** gồm `Nguyễn, Trần, Lê, Phạm, Hoàng, Đặng, Đỗ, Bùi, Vũ, Dương, Ngô, Lý`, id chẵn dùng dạng không dấu tương ứng; `photoUri = null`), `delay(300)` mô phỏng đọc và đếm lời gọi; `numbersFor(ids)` trả `0900000000 + id` với `delay(150)` có thể hủy (`delay` ném `CancellationException` khi bị hủy, log `cancelled`), người có `id % 10 == 0` không có số; `changes()` = `callbackFlow` đăng ký `BroadcastReceiver` động cho action `gambi.global.os26launcher.FX4_FAKE_CHANGE` (điều phối kích hoạt bằng `adb shell am broadcast -a gambi.global.os26launcher.FX4_FAKE_CHANGE -p gambi.global.os26launcher`), log `FX4 fake watch registered/unregistered` trong `awaitClose`; `avatar` dùng `ContactAvatar.initialsCircle`; `openContact/dial/message` trả `false` (không gọi, không mở gì); `canCallAndMessage() = true`. Trong `di/SearchModule.kt` đổi **một dòng** `bindContactsRepository(impl: FakeContactsRepository)` (cùng dấu). Build xanh, commit riêng `chore: FX4 TEMP fake contacts source (revert before PR)`. Không đụng `ContactsRepositoryImpl` ở commit này.
- [x] **Step 1: Bộ đếm chuẩn hóa.** Trong `AppSearchMatcher` thêm `@Volatile var normalizeCalls: Long` (tăng mỗi lần `normalize`) và `var normalizeNanos` (cộng `System.nanoTime` quanh thân hàm). Chỉ tạm; không `android.*`.
- [x] **Step 2: Log lần dựng chỉ mục.** Trong `SearchViewModel.buildIndex`, bao `System.nanoTime`; ghi một dòng `FX4 index#n apps=… settings=… contacts=… shortcuts=… ms=… normalizeCalls(delta)=… sinceProcessStartMs=…` (dùng `SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()`). Đếm `n` bằng `AtomicInteger`.
- [x] **Step 3: Log các nguồn đọc dữ liệu.** nguồn giả (`FakeContactsRepository`): `FX4 loadNames n=… ms=…` (ms là độ trễ mô phỏng, không phải số provider), `FX4 numbers start ids=…` / `done` / `cancelled` (đếm lời gọi); log tương ứng ở `ContactsRepositoryImpl` **không cần** (không chạy trên máy); `SystemSettingsRepositoryImpl.build`: `FX4 settings.build ms=… sinceProcessStartMs=…`; `ShortcutRepositoryImpl.iconOf`: đếm `FX4 shortcutIcon draw id#=…` (chỉ số đếm, không id); `GetCategoryGroupsUseCase`: `.onEach { FX4 groups nullEmit=${it==null} }` ở cuối chuỗi.
- [x] **Step 3: Log băm kết quả.** Ở `SearchViewModel.compute`: với truy vấn khác rỗng ghi `FX4 q#=… hash=… size=…` (băm của `results.map { it.stableId }.joinToString("|")`, **không** ghi truy vấn). Điều phối gõ lần lượt theo danh sách cố định (ghi trong Nhật ký: `nguyen`, `nguyễn`, `duc`, `đức`, `d`, `ca`, `cam`, `chrome`, `wifi`, `zztest 00`, `  ng  `, `NGUYEN`) và chép lại dãy băm làm chuẩn đối chiếu.
- [x] **Step 4: Build.** `gradlew.bat assembleDebug` xanh. Commit `chore: add temporary FX4 timing logs`.
- [x] **Step 5: Kịch bản kiểm (Live 4, APK debug có nguồn giả; điều phối chạy theo mục "Bài kiểm cả tính năng" — chuẩn bị máy A–C; không cấp quyền danh bạ):** (a) khởi động lạnh (mở Cài đặt hệ thống lên trên rồi `force-stop`, rồi `am start -W`; bài học RB) → ghi `settings.build` và `index#` **trước khi mở tìm kiếm** (kỳ vọng nền: ≥ 2 dòng `index#`, 1 dòng `settings.build`, xuất hiện lúc khởi động); (b) phiên tìm: mở tìm kiếm bằng vuốt xuống, gõ `nguyen`, đợi, đóng, ra Cài đặt app khác 9 s, về → ghi số lần `index#`, ms mỗi lần, `normalizeCalls` mỗi lần (kỳ vọng nền: ≥ 4 lần dựng, mỗi lần ~ (≈ 2100 nhãn × 0,2 ms) ≈ 0,4 s theo ngoại suy M8d, Live 4 chưa đo); (c) gõ nhanh `nguyen` bằng `adb shell input text` → ghi số `numbers start/done`; (d) mở Library, sang app khác 9 s, về → đếm `nullEmit=true`; (e) bật mục shortcut trong Cài đặt tìm kiếm (xem câu hỏi 2) rồi gõ `chrome` → đếm `shortcutIcon draw`. Ghi hết vào Nhật ký.

### Task 2: `normalize` một lượt có bảng tra (B11, phần chuẩn hóa)
**Bắt buộc kiểm tay:** không
**Files:** `domain/search/AppSearchMatcher.kt`, `ui/search/SearchViewModel.kt` (chỉ log tạm đối chiếu)
**Interfaces:** Consumes: `AppSearchMatcher.normalize(String): String` (cùng chữ ký). Produces: cùng chữ ký, kết quả giống hệt.
- [x] **Step 1: Giữ bản cũ.** Đổi tên thân hàm hiện tại thành `private fun normalizeReference(text: String): String` (nguyên văn: NFD, bỏ `\p{Mn}+`, `lowercase(Locale.ROOT)`, `đ→d`, `\s+`→" ", `trim`). Đây vừa là đường dự phòng vừa là chuẩn đối chiếu.
- [x] **Step 2: Bảng tra dựng bằng bản cũ.** Trong `object`, dựng `CharArray` cho các dải `0x00C0..0x024F` và `0x1E00..0x1EFF`: với mỗi ký tự `c`, `r = normalizeReference(c.toString())`; nếu `r` đúng một ký tự trong `0x21..0x7E` thì `TABLE[c] = r[0]`, ngược lại `0` (= "không chắc, dùng bản cũ"). Dựng một lần ở `init` (~900 lần gọi bản cũ, một lần mỗi process; đo ms trong log tạm).
- [x] **Step 3: Vòng một lượt.** `normalize(text)`: duyệt từng ký tự; `' '`: ghi một dấu cách nếu ký tự ghi trước không phải dấu cách và đã có nội dung; `'A'..'Z'`: ghi chữ thường; ASCII in được `0x21..0x7E` còn lại: ghi nguyên; ký tự trong dải bảng tra: ghi `TABLE[c]` (nếu `0` → `return normalizeReference(text)`); ký tự tổ hợp `0x0300..0x036F`: bỏ qua; **mọi ký tự khác** (điều khiển, tab/xuống dòng, nbsp, ký tự ngoài bảng, chữ Cyrillic/CJK/emoji...): `return normalizeReference(text)`. Cuối: bỏ dấu cách thừa ở cuối. Dấu cách gộp trên **chuỗi đầu ra** (đúng như bản cũ: bỏ dấu tổ hợp trước rồi mới gộp khoảng trắng). Không dùng regex, không `Normalizer` trên đường nhanh.
- [x] **Step 4: Đối chiếu một lần bằng log (không phải test).** Ở `SearchViewModel.init` tạm: chạy `normalize` mới và `normalizeReference` (mở tạm `internal`) trên (i) mọi ký tự của hai dải bảng tra và `0x20..0x7F` và `0x300..0x36F` đứng một mình và kẹp giữa chữ (`"a<c>b"`, `"<c> x"`, `"x <c>"`, `"  a  <c>  b "`); (ii) dạng NFD của toàn bộ chữ có dấu tiếng Việt (a, ă, â, e, ê, i, o, ô, ơ, u, ư, y × 5 dấu thanh, hoa và thường, cộng `Đ/đ`); (iii) mọi nhãn app, Cài đặt, từ khóa và, khi nạp, mọi tên danh bạ (đếm, không ghi tên). Ghi `FX4 normalize check cases=… mismatches=…`. **Phải bằng 0** trước khi tiếp. Mỗi lần lệch: sửa bảng/nhánh, không nới tiêu chí.
- [x] **Step 5: Build và commit** `perf: single-pass normalize for search matching`.
- [x] **Step 6: Kịch bản kiểm (Live 4):** (a) `FX4 normalize check mismatches=0`; (b) chạy lại đúng bộ truy vấn của Task 1: dãy băm **bằng** dãy chuẩn; (c) đo lại phiên tìm (cùng bước (b) của Task 1): ghi ms mỗi lần dựng và `normalizeNanos` (kỳ vọng: giảm rõ so với nền; mục tiêu ≥ 5 lần, ước tính ≥ 10 lần là *suy luận chưa kiểm chứng*, vì bỏ 2 regex và 1 lần gọi ICU mỗi nhãn). Số này quyết định ngân sách cho Task 4.

### Task 3: App Library và tìm kiếm không phát `null` khi quay lại (C1)
**Bắt buộc kiểm tay:** không
**Files:** `domain/usecase/GetCategoryGroupsUseCase.kt`, `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes: `AppRepository.apps` (`stateIn(WhileSubscribed(5_000), emptyList())`, phát lại giá trị cuối khi chạy lại), `LayoutRepository.hiddenApps`. Produces: `GetCategoryGroupsUseCase()` vẫn trả `Flow<List<CategoryGroupApps>?>` (chữ ký giữ để `HomeViewModel` không phải sửa; giá trị đầu `null` do `stateIn` của `HomeViewModel` đảm nhiệm); `SearchViewModel.visibleApps` là `StateFlow<List<AppItem>?>`.
- [x] **Step 1: Use case.** Bỏ `.onStart<List<CategoryGroupApps>?> { emit(null) }` và import `onStart`; sửa comment đầu file: "`null` là giá trị đầu của `stateIn` ở người gọi; use case không tự phát `null`" (giữ lời giải thích `.filter { isNotEmpty() }`). Giữ chữ ký trả `Flow<List<CategoryGroupApps>?>` (một `Flow<List<X>>` gán được vào nó vì `Flow` hiệp biến).
- [x] **Step 2: `visibleApps` thành `StateFlow`.** Đổi định nghĩa (`SearchViewModel.kt:79-88`): bỏ `.onStart { emit(null) }`; `combine(...)` giữ logic lọc app ẩn; thêm `.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)`. `null` = "chưa nạp" (suy ra `LOADING`), đúng nghĩa cũ. Kiểm các chỗ dùng: `computed` (`:172`), `refreshShortcuts` (`:240`, `filterNotNull().first()` vẫn đúng), `searchIndex`.
- [x] **Step 3: Không đổi `uiState`** (`WhileSubscribed(5_000)` giữ; `StateFlow` giữ giá trị cuối, `compute` chạy lại chỉ khi đầu vào đổi giá trị).
- [x] **Step 4: Build và commit** `fix: stop emitting null when returning to library and search`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** (a) Library → mở app Cài đặt hệ thống 9 s → Back: log `FX4 groups nullEmit` **không** có dòng `true` sau lần đầu (nền có ≥ 1 dòng `true` mỗi lần quay lại; xem Task 1 (d)); Library vẫn hiện đủ nhóm, ô tìm kiếm trong Library vẫn mở được; (b) mở tìm kiếm, gõ `cam`, ra app khác 9 s, về: log `compute` không có trạng thái `LOADING` (thêm đếm tạm `FX4 loading#` ở nhánh `apps == null`); kết quả giữ; (c) cài lại một app thử/ẩn một app trong lúc ở ngoài (tùy chọn): danh sách vẫn cập nhật (đảm bảo không "đóng băng" dữ liệu). Báo cáo ghi rõ: báo cáo gốc không thấy chớp bằng mắt trên Live 4 (truy vấn Room về kịp), nên bằng chứng chính là log, không phải mắt.

### Task 4: Chỉ mục ghép từ các nguồn đã chuẩn hóa sẵn (B11, phần giữ lại)
**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng dùng để chọn phương án (mục 4.2): số đo với 2000 danh bạ chốt việc giữ cấu trúc nguồn-riêng (A) hay thu hẹp (chỉ giữ Source cho danh bạ). Lý do đứng vững: nếu sau Task 2 một lần dựng đã nằm trong ngân sách thì điều phối có thể cắt Task này còn phần "không dựng lại khi shortcut/đóng lớp"; nếu không thì làm đủ. Tiêu chí duyệt xem Step 6.
**Files:** `domain/search/SearchIndex.kt`, `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes: `SearchIndex.Candidate(result, label, keywords)`, `ContactEntry`, `ShortcutEntry`, `SearchSettingEntry`, `visibleApps` (Task 3). Produces: `class SearchIndex.Source` (bất biến, `Source.of(candidates)`, `Source.EMPTY`) và `SearchIndex(sources: List<Source>)`; `SearchViewModel` giữ một `StateFlow<Source>` mỗi nguồn.
- [x] **Step 1: `Source` trong domain.** Trong `SearchIndex` thêm `class Source private constructor(entries: Array<Entry>)` với `companion { fun of(candidates: List<Candidate>): Source; val EMPTY }`. `Of` chuẩn hóa nhãn và từ khóa **một lần** (dùng `AppSearchMatcher.normalize` của Task 2). `SearchIndex(sources: List<Source>)`: `search` duyệt lần lượt từng `Source` theo thứ tự truyền vào (không nối mảng, không cấp phát thêm), logic `rankOf`, sắp xếp ổn định và `maxResults` giữ nguyên. Giữ constructor cũ `SearchIndex(candidates)` bằng cách bọc một `Source` (nếu chỉ còn ViewModel gọi thì có thể xóa — báo điều phối trước khi xóa).
- [x] **Step 2: Nguồn app.** `appsSource: StateFlow<Source>` = `visibleApps.filterNotNull().map { Source.of(candidates app) }.flowOn(defaultDispatcher).stateIn(..., WhileSubscribed(5_000), Source.EMPTY)`. Phát lại danh sách giống hệt (Task 3) không tới đây vì `visibleApps` đã lọc trùng.
- [x] **Step 3: Nguồn Cài đặt, danh bạ, shortcut.** `settingsSource` từ `settingEntries`; `contactsSource` từ `contactEntries` (rỗng → `Source.EMPTY`, không cấp phát); `shortcutsSource` từ `combine(shortcutEntries, visibleApps)` (vì shortcut được lọc theo app hiển thị, `:208`; nguồn này nhỏ, dựng lại khi app đổi chấp nhận được). Mỗi nguồn dựng ở `defaultDispatcher`.
- [x] **Step 4: Ghép chỉ mục.** `searchIndex = combine(appsSource, settingsSource, contactsSource, shortcutsSource, prefs) { …}` chỉ chọn `Source` hay `EMPTY` theo công tắc `prefs` (`p.systemSettings/shortcuts/contacts`), tạo `SearchIndex(listOf(apps, settings, contacts, shortcuts))` và `shortcutsByApp` (nhóm `SearchResult.Shortcut` theo app — vẫn gom ở đây, không chuẩn hóa gì). Thứ tự **phải** là app, Cài đặt, danh bạ, shortcut (như `buildIndex` cũ, `:209-222`).
- [x] **Step 5: Xóa bản sao khi xóa danh bạ.** `contactEntries.value = emptyList()` (ở `close()`, `refreshContacts` nhánh không quyền/tắt) kéo `contactsSource` về `Source.EMPTY`; không giữ tham chiếu `Source` danh bạ nào khác (kiểm lại ở Review Focus 3). Log tạm: đếm số lần dựng **từng nguồn** (`FX4 source=apps|settings|contacts|shortcuts built n=… ms=…`).
- [x] **Step 6: Build và commit** `perf: build search index from per-source normalized snapshots`.
- [x] **Step 7: Kịch bản kiểm (Live 4, nguồn danh bạ giả 2000 người):** (a) bộ truy vấn Task 1: dãy băm **bằng** chuẩn (kết quả giống hệt; bắt buộc); (b) phiên tìm (Task 1 (b)): ghi số lần dựng từng nguồn và ms. **Tiêu chí duyệt:** (i) nguồn danh bạ dựng **đúng 1 lần** cho mỗi lần nạp (mở lớp = 1; ON_RESUME/đóng không thêm lần nào ngoài nạp lại thật); (ii) shortcut đổi, đóng lớp, quay lại sau 5 giây **không** làm `contacts`/`apps` dựng lại; (iii) tổng `normalizeCalls` mỗi phiên ≤ (số nhãn + số lần nạp lại) thay vì ≥ 4 × số nhãn (đếm chính xác, không phụ thuộc máy); (iv) thời gian một lần chỉ-ghép ≤ 5 ms ở 2000 danh bạ; (v) lần dựng nguồn danh bạ ≤ 1/5 ms của một lần dựng nền Task 1 (mục tiêu nền là số đã đo ở Task 1, **không** đặt trước số tuyệt đối vì Live 4 chưa có số). Không đạt → trả Task (vòng +1).

### Task 5: Danh mục Cài đặt và chỉ mục không dựng lúc khởi động (C9)
**Bắt buộc kiểm tay:** không
**Files:** `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes: `SystemSettingsRepository.entries()` (nhớ theo ngôn ngữ, dựng ở IO), `Source` của Task 4. Produces: cờ nội bộ `indexActive: MutableStateFlow<Boolean>` bật ở `open()` lần đầu.
- [x] **Step 1: Bỏ khối `init` dựng danh mục** (`:107-118`). `settingEntries` giữ là `MutableStateFlow(emptyList())`.
- [x] **Step 2: Hàm `ensureSettingsCatalog()`** gọi `systemSettings.entries()` (try/catch như cũ, `CancellationException` ném lại) rồi gán `settingEntries.value`. Gọi ở **mỗi** `open()` (lần đầu dựng, các lần sau lấy từ cache theo ngôn ngữ của repository; nếu ngôn ngữ đổi trong process thì cũng tự làm mới; `StateFlow` lọc trùng nên không dựng lại `Source` khi danh sách giống hệt). Gọi sau khi `indexActive.value = true`.
- [x] **Step 3: Cổng cho các `Source`.** `appsSource`, `settingsSource`, `shortcutsSource` chỉ bắt đầu chạy sau khi `indexActive` là `true` (`indexActive.flatMapLatest { if (it) … else emptyFlow() }` hoặc `filter`+`take`; hình dạng do agent chọn, bất biến cần giữ: *trước `open()` đầu tiên không có phép chuẩn hóa hay lời gọi PackageManager nào của tìm kiếm*). `computed`/`uiState` vẫn thu từ `STARTED` (rẻ: truy vấn rỗng không cần chỉ mục); `index == null` nghĩa là chưa sẵn sàng ⇒ `compute` đã có nhánh `LOADING` (`:329`), và chỉ gặp nếu người dùng gõ trong vài chục ms đầu của lần mở đầu tiên.
- [x] **Step 4: Build và commit** `perf: defer settings catalog and search index until first open`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** (a) khởi động lạnh (như Task 1 (a)): log **không** có `settings.build` và không có `index#`/`source built` trước khi mở tìm kiếm (nền: có); (b) mở tìm kiếm lần đầu: thấy `settings.build` và `source built`; gõ `wifi`, `bluetooth`, `cam`: kết quả đủ (Cài đặt hiện đúng, kể cả Live 4 có action trả `ResolverActivity` bị loại như trước); (c) đóng, mở lại: **không** dựng lại danh mục (log); (d) tùy chọn, nếu điều phối muốn có số `TotalTime`: 5 lượt `am start -W` bản **debug** nền (Task 1) so với sau; ghi trung vị và nói rõ "debug, Live 4, không phải cái người dùng thấy". Bằng chứng chính của C9 là (a), không phải (d).

### Task 6: Rời launcher khi ô tìm kiếm còn mở: bỏ danh bạ khỏi RAM (C6)
**Bắt buộc kiểm tay:** không
**Files:** `ui/search/SearchViewModel.kt`, `ui/home/controller/SearchOverlayController.kt` (một nhánh trong observer sẵn có)
**Interfaces:** Consumes: `Lifecycle.Event.ON_STOP`, `refreshContacts()`. Produces: `SearchViewModel.onHostStopped()`.
- [x] **Step 1: `onHostStopped()`.** Hủy `contactsLoadJob`, `stopContactsWatch()`, `contactEntries.value = emptyList()` (kéo theo `contactsSource` rỗng, Task 4), xóa cache số điện thoại và tăng "mã phiên" (Task 7). **Không** đụng `isOpen`, `query`, `showAllWhenEmpty`.
- [x] **Step 2: Observer.** Trong `SearchOverlayController.setupContactsPermission`, observer đã có dòng `ON_RESUME → refreshContacts()` (`:74-76`); thêm `Lifecycle.Event.ON_STOP -> searchViewModel.onHostStopped()`. Không đăng ký observer mới, không đụng `HomeActivity`. Bình luận một dòng nói rõ vì sao đặt ở đây (C6).
- [x] **Step 3: Kiểm hành vi biên.** `ON_RESUME` sẵn có gọi `refreshContacts()` nên: lớp mở + đủ quyền + công tắc bật → nạp lại; lớp đóng → `refreshContacts` đặt rỗng và `stopContactsWatch` (không đổi). Hộp thoại xin quyền của hệ thống chỉ làm `ON_PAUSE` (không `ON_STOP`): không ảnh hưởng. Recreate (đổi giao diện khi lớp mở) cũng đi `ON_STOP`: danh bạ nạp lại một lần ở Activity mới (chấp nhận).
- [x] **Step 4: Build và commit** `fix: drop contacts from memory when the launcher stops`.
- [x] **Step 5: Kịch bản kiểm (Live 4, nguồn danh bạ giả):** (a) mở tìm kiếm, gõ `nguyen` (hàng danh bạ giả hiện); bấm Recents hoặc kéo thông báo rồi mở app khác; log tạm `FX4 contacts watch stopped entries=0` ngay khi `ON_STOP`, kèm `FX4 fake watch unregistered` (bằng chứng `stopContactsWatch()` đã chạy và luồng `changes()` đã bị hủy; **gỡ `ContentObserver` thật** chỉ kiểm bằng đọc code `awaitClose` của `ContactsRepositoryImpl.changes()`, không đổi); (b) trong lúc ở ngoài, gửi broadcast `FX4_FAKE_CHANGE`: **không** có dòng `loadNames` nào (nền: có 1 dòng ~300 ms sau khi nhận); (c) quay lại launcher: ô tìm kiếm vẫn mở, chuỗi `nguyen` vẫn nguyên; có `fake watch registered`, một dòng `loadNames` (~300 ms mô phỏng), hàng danh bạ hiện lại; (d) mở tìm kiếm, nút Home: vẫn đóng như cũ (`close()`), danh bạ rỗng, `entries=0`. Báo: có một thoáng hàng danh bạ biến mất rồi hiện lại khi quay về (chấp nhận); thời gian nạp lại trong log là mô phỏng, không phải số thật.

### Task 7: Truy vấn danh bạ hủy được và cache số theo phiên (C7)
**Bắt buộc kiểm tay:** không
**Files:** `data/search/ContactsRepositoryImpl.kt`, `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes: `ContactsRepository.numbersFor(ids)` / `loadNames(limit)` (chữ ký giữ nguyên). Produces: `numbersFor` và `loadNames` hủy truy vấn thật khi coroutine bị hủy; `SearchViewModel.numberCache` (`ConcurrentHashMap<Long, String>`, `""` = người không có số) và mã phiên.
- [x] **Step 1: Hủy ở repository.** Tạo `android.os.CancellationSignal` cho mỗi truy vấn và truyền vào `ContentResolver.query(uri, projection, selection, args, sortOrder, signal)`. Nối với coroutine: chạy truy vấn trong `async(io)` bên trong `coroutineScope`; `try { job.await() } catch (e: CancellationException) { signal.cancel(); throw e }`. Bắt `OperationCanceledException` (hạng `RuntimeException` của framework) **riêng, trước** `catch (RuntimeException)` hiện có để không ghi log lỗi "numbersFor failed" và không trả kết quả cụt. Áp cùng cách cho `loadNames` (cùng helper, ít dòng; ngăn `refreshContacts` bị hủy vì `close()`/`ON_STOP` mà đọc dở 5000 hàng vẫn chạy tiếp).
- [x] **Step 2: Cache ở ViewModel.** Trong `withContactNumbers`: lấy danh sách id còn thiếu cache; nếu rỗng thì không gọi repository; còn thiếu thì gọi `numbersFor(thiếu)`, ghi vào cache (`""` cho người không có số, để không hỏi lại). Chỉ ghi nếu **mã phiên** lúc bắt đầu còn bằng mã phiên hiện tại (chống ghi sau khi `close()`/`onHostStopped()` đã xóa; `compute` chạy ở Default, nhiều thread). `close()`, `onHostStopped()` và sự kiện đổi danh bạ (`changes()` → trước khi `refreshContacts`) xóa cache và tăng mã phiên.
- [x] **Step 3: Build và commit** `perf: cancel contact number queries and cache numbers per session`.
- [x] **Step 4: Kịch bản kiểm (Live 4, nguồn danh bạ giả có số và độ trễ 150 ms):** (a) gõ nhanh `nguyen` bằng `adb shell input text nguyen` (lặp 3 lần với xóa giữa các lần): đếm `numbers start` / `done` / `cancelled` (nền Task 1 (c): mọi lời gọi chạy hết; mong đợi: ít `start` hơn nhiều, vì top-5 của `ng`…`nguyen` gần như cùng một tập người nên cache trúng, và lời gọi dở bị hủy → thấy `cancelled`); (b) hàng danh bạ giả vẫn có số và nút gọi/nhắn đúng (số khớp quy tắc `0900000000 + id`); người `id % 10 == 0` không có số và không có nút; (c) đóng lớp rồi mở lại: cache đã xóa (có `start` mới), không rò số cũ; (d) gửi broadcast `FX4_FAKE_CHANGE` khi lớp mở: cache bị xóa theo `changes()`, ~300 ms sau nạp lại, số vẫn hiện đúng; (e) log không có tên/số. **Không kiểm được trên máy:** `CancellationSignal` thật trên `ContentResolver.query` và nhánh `OperationCanceledException` — chỉ đọc code ở review (Review Focus 7) và build xanh; ghi rõ vào báo cáo Task.

### Task 8: Shortcut trong tìm kiếm: nạp đúng một icon, có cache (C8)
**Bắt buộc kiểm tay:** không
**Files:** `domain/repository/ShortcutRepository.kt`, `data/apps/ShortcutRepositoryImpl.kt`, `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes: `LauncherApps.getShortcuts(ShortcutQuery, UserHandle)`, `getShortcutIconDrawable`. Produces: `suspend fun ShortcutRepository.shortcutIcon(key: ComponentKey, shortcutId: String, iconSizePx: Int): Bitmap?`.
- [x] **Step 1: Interface.** Thêm `shortcutIcon` vào `ShortcutRepository` (comment: chỉ nạp icon của đúng một shortcut; cùng quy ước "mọi lỗi trả `null`").
- [x] **Step 2: Cài đặt.** Trong `ShortcutRepositoryImpl`: `private val iconCache = LruCache<String, CachedIcon>(32)` với `CachedIcon(bitmap, atMs)`; khóa `"${pkg}/${id}/${userSerial}/${size}"`; trả cache nếu tuổi < 60 s. Chưa có: `hasShortcutHostPermission` (không có → `null`), `userManager.getUserForSerialNumber`, `ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id)).setQueryFlags(DYNAMIC or MANIFEST)`, lấy phần tử đầu `isEnabled`, `iconOf(info, size)` (hàm sẵn), ghi cache nếu không `null`. `CancellationException` ném lại; lỗi khác log và `null` (cùng mẫu `getShortcuts`). *Đọc tài liệu cần xác nhận khi code:* `setShortcutIds` phải đi cùng `setPackage` (ta luôn đặt `setPackage`, nên an toàn dù tài liệu yêu cầu hay không); không đặt `setActivity` vì id duy nhất theo package.
- [x] **Step 3: Dùng ở tìm kiếm.** `SearchViewModel.loadIcon` nhánh `SearchResult.Shortcut` gọi `shortcutRepository.shortcutIcon(entry.key, entry.id, sizePx)` thay cho `getShortcuts(...).firstOrNull { ... }?.icon`. `getShortcuts` (menu nhấn giữ M7, `GetAppShortcutsUseCase`) **giữ nguyên**.
- [x] **Step 4: Build và commit** `perf: load a single shortcut icon with a short-lived cache`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** (a) cần bật mục shortcut trong Cài đặt tìm kiếm (báo cáo ghi `search_shortcuts = false`; xem câu hỏi 2, trả lại cuối) và Live 4 đang là launcher mặc định; (b) gõ `chrome` (hoặc app có ≥ 3 shortcut, ví dụ Camera): log `FX4 shortcutIcon draw` **một** lần mỗi hàng (nền Task 1 (e): tới 4 lần mỗi hàng); icon đúng; (c) xóa chữ, gõ lại cùng truy vấn trong 60 s: **không** có `draw` mới (cache); sau > 60 s: có `draw` lại; (d) mở shortcut từ kết quả vẫn chạy (`startShortcut` không đổi); menu nhấn giữ icon Chrome trên Home vẫn hiện đủ shortcut và icon (đường `getShortcuts` còn nguyên).

### Task 9: Gỡ log tạm, đối chiếu cuối, trả máy
**Bắt buộc kiểm tay:** không
**Files:** tất cả file đã có log `FX4` (xem Task 1) và `docs/report/FX4-search-library.md` (Nhật ký, bằng chứng)
**Interfaces:** Consumes: các Task trước. Produces: nhánh sạch log, bảng bằng chứng, bản kiểm cuối.
- [x] **Step 1:** `Grep` tìm `FX4` và `normalizeCalls`/`normalizeNanos`/`normalizeReference` còn dư: gỡ mọi log, bộ đếm, hàm `internal` mở tạm, import `android.util.Log`/`Process` không còn dùng (cùng mẫu gỡ log của G2A). `normalizeReference` vẫn **giữ** làm đường dự phòng (đổi tên `normalizeSlow`, `private`), chỉ gỡ phần đối chiếu.
- [x] **Step 2:** Build `assembleDebug` xanh; `git diff --stat main` chỉ gồm các file ở "Vùng file".
- [x] **Step 3: Gỡ nguồn giả.** `git revert` commit `chore: FX4 TEMP fake contacts source` (xóa `FakeContactsRepository.kt`, trả `di/SearchModule.kt` về `ContactsRepositoryImpl`; việc xóa file nằm trong commit revert, không xóa tay). `Grep` `FX4_TEMP_FAKE_CONTACTS` và `FakeContactsRepository` trong `app/` phải rỗng; `git diff main -- app/src/main/java/gambi/global/os26launcher/di/SearchModule.kt` rỗng. Điều phối có thể chọn bỏ luôn cặp commit thêm/revert khi rebase trước merge.
- [x] **Step 3b:** Commit `chore: remove temporary FX4 logs` (log), tách khỏi revert nguồn giả.
- [x] **Step 4: Kịch bản kiểm cuối (Live 4):** build sạch log: lặp bộ truy vấn Task 1 nhưng **không có băm** (đã gỡ log): so tay bốn truy vấn then chốt (`nguyen`, `nguyễn`, `duc`, `đức`) cho cùng thứ tự kết quả trên màn (đã chụp ở Task 1); chạy bài kiểm cả tính năng bên dưới trên **bản có nguồn giả** (trước khi revert) cho các mục dữ liệu; sau revert, build **bản sạch** (`ContactsRepositoryImpl` thật, chưa cấp quyền) và chạy hồi quy mục C.8; **dọn máy** (mục D).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (serial `BGB00153427`, Android 11, kính FORCE_HIGH, Os26Launcher đang là launcher mặc định), APK **debug** của nhánh. Mọi số ghi "debug, Live 4". Chủ dự án đã cho điều phối kiểm lại trên Live 4 sau mỗi bản sửa (2026-11-11); ngay trước mỗi lượt điều phối vẫn kiểm máy đang rảnh (màn hình chính, không app nào của chủ dự án ở trước) và sao lưu lại; script nhập liệu kiểm tiền cảnh bằng `dumpsys activity activities` ở đầu và trong vòng lặp.

### A. Chuẩn bị máy (một lần, trước Task 1)

1. **Xác nhận rảnh** và `adb devices` thấy Live 4.
2. **Lưu lại hiện trạng:** `adb shell pm path gambi.global.os26launcher` rồi `adb pull` APK đang cài (để trả đúng bản); phiên bản/`lastUpdateTime` (`dumpsys package`); xác nhận quyền `READ_CONTACTS` của app **chưa cấp** (`dumpsys package` mục runtime permissions) và sẽ **không bao giờ** cấp; ghi nhận (không ghi tên) `search_shortcuts`/`search_contacts` hiện tại — nằm trong bản sao lưu DataStore bên dưới.
3. **Sao lưu dữ liệu app ngay trước lượt này** (không dùng bản của lượt trước): `run-as gambi.global.os26launcher tar cf - databases files no_backup` ra máy tính; **cả** thư mục `files/`; so khớp md5 **từng file** (kể cả `-wal`/`-shm`) sau khi giải nén thử ra thư mục tạm; ghi hash vào Nhật ký. Windows: Git Bash với `MSYS_NO_PATHCONV=1` cho đường dẫn máy, đường dẫn cục bộ dạng `C:/...`; binary dùng `adb exec-out ... > file`, không dùng chuyển hướng của PowerShell.
4. **Không đụng danh bạ thật:** không đếm, không đọc, không ghi provider danh bạ của máy (D3). Không có bước chuẩn bị danh bạ nào trên máy.

### B. Danh bạ giả: nguồn trong app (không đụng máy)

Quyết định của chủ dự án (D3): **không** cấp `READ_CONTACTS`, **không** chèn vào danh bạ máy. Danh bạ khi kiểm đến từ `FakeContactsRepository` (Task 1 Step 0), có trong APK debug của nhánh cho tới khi revert ở Task 9:

1. **Dữ liệu:** 2000 người `ZZTEST …` sinh trong RAM khi nạp, nửa có dấu (Nguyễn, Đặng, Đỗ, Lê, Hoàng, Dương…), nửa không dấu; số `0900000000 + id` (trừ id chia hết cho 10: không có số); độ trễ mô phỏng 300 ms (nạp tên) và 150 ms (tra số, hủy được).
2. **Kích hoạt "danh bạ đổi" (nhánh `ContentObserver`):** `adb shell am broadcast -a gambi.global.os26launcher.FX4_FAKE_CHANGE -p gambi.global.os26launcher`; log `FX4 fake watch registered/unregistered` cho thấy luồng `changes()` đang sống hay đã bị hủy (C6).
3. **Không cần quyền:** nguồn giả báo `hasPermission() = true`, nên hàng mời xin quyền **không** hiện trong APK này; kiểm hàng mời trên **bản sạch** (mục C.8).
4. **Không dùng được** để kết luận chi phí đọc provider thật hoặc hành vi `CancellationSignal` thật (nói rõ ở Review Focus 3 và 7).
5. **Không mở** app Danh bạ/Quay số của máy trong lúc thử.

### C. Các mục kiểm theo thứ tự thao tác

Mỗi Task ở trên đã ghi "Kịch bản kiểm" riêng; khi gộp, điều phối sắp theo thứ tự thao tác tự nhiên:
1. Khởi động lạnh (Task 5a), ghi `settings.build`/`index#` trước khi mở tìm kiếm.
2. Mở tìm kiếm bằng vuốt xuống: bộ truy vấn mẫu và băm (Task 1/2/4a), đo thời gian dựng nguồn (Task 4).
3. Gõ nhanh `nguyen`: số `numbers start/done/cancelled` và số trúng cache (Task 7).
4. Đóng lớp (Back hoặc Home), mở lại: không dựng lại danh mục, danh bạ nạp một lần (Task 5c, 4).
5. Mở tìm kiếm, gõ `nguyen`, ra Cài đặt hệ thống 9 s, về: không `LOADING`, không dựng lại nguồn app/Cài đặt (Task 3b, 4); lặp lần nữa nhưng ra bằng Recents để thử `ON_STOP`: danh bạ rời RAM và nạp lại khi về (Task 6).
6. Library → Cài đặt 9 s → Back: không `nullEmit=true` (Task 3a).
7. Bật mục shortcut trong Cài đặt tìm kiếm, gõ `chrome`/`camera`: một icon mỗi hàng, cache 60 s (Task 8).
8. **Hồi quy:** (trên bản có nguồn giả) mở app từ kết quả; tắt công tắc "Tìm trong danh bạ" → danh bạ rời RAM ngay; tìm kiếm từ trang Library (`showAll`) vẫn liệt kê a–z; menu nhấn giữ icon (shortcut) vẫn đúng; hàng Cài đặt mở đúng màn hệ thống. Rồi, **trên bản sạch** sau revert (chưa cấp quyền danh bạ): hàng mời "Hiện danh bạ" vẫn hiện ở ô trống và từ 2 ký tự; bấm nút "x" hoặc tắt công tắc thì hàng mời biến mất. **Không** bấm "xin quyền" (sẽ mở hộp thoại cấp quyền; không cấp quyền trong lượt này), không có bước thu hồi quyền vì quyền chưa bao giờ được cấp.

### D. Dọn máy (một lần, cuối cùng)

1. **Danh bạ:** không có gì để dọn trên máy (không chèn, không cấp quyền). Xác nhận lại `READ_CONTACTS` vẫn **chưa cấp**.
2. **Trả cờ cài đặt tìm kiếm** (mục shortcut tạm bật cho C8) về giá trị ban đầu (nút trong Cài đặt hoặc khôi phục DataStore từ bản sao lưu).
3. **Trả dữ liệu app:** dừng app, khôi phục từ bản sao lưu A3 (đặc biệt `no_backup` — lịch sử mở app bị ghi thêm khi thử), so khớp lại md5 **từng file** kể cả `-wal`/`-shm`; một lần `tar xf` qua `run-as` từng đứt đoạn mà vẫn exit 0 (bài học DG): làm lại nếu lệch.
4. **Trả bản cài:** `adb install -r -d` APK đã pull ở A2 (bản có nguồn giả không được để lại trên máy); kiểm lại `lastUpdateTime`/phiên bản.
5. Ghi vào Nhật ký: giờ bắt đầu/kết thúc, hash trước/sau, trạng thái máy trả lại.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** (điền khi kiểm xong)

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | — | Viết plan (chưa code, chưa chạy lệnh adb/git) | — | Chờ điều phối rà |
| 2026-11-11 | — | Sửa plan vòng 1/5: danh bạ chỉ là nguồn giả trong app (`FakeContactsRepository`, commit tạm), bỏ chèn provider và cấp quyền; thêm D3 mới, D11, D12, mục "Kiểm với dữ liệu giả", Review Focus 11, sửa Task 1/4/6/7/9 và bài kiểm A–D | — | Điều phối duyệt plan 2026-11-11 sau chỉnh |
| 2026-11-11 | 1 | Step 0: nguon danh ba gia tam (`FakeContactsRepository` + 1 dong `SearchModule`); Steps 1-4: log tam `FX4` (dem/thoi gian `normalize`, `index#`, `settings.build`, `shortcutIcon draw`, `nullEmit`, `loading#`, `q# hash`). Build xanh. APK `E:\SourceTree\IosLauncher-wtpk\FX4-task1.apk`. Chua chay kich ban Live 4 | f338c2a, 19e1114 | Cho dieu phoi do nen (Step 5). Draft PR #34 |
| 2026-11-11 | 1 (đo nền) | Điều phối đo trên Live 4 (debug, FX4-task1.apk, 12:10–12:20; Os26Launcher chưa là launcher mặc định nên mở Home tường minh; `search_contacts`/`search_shortcuts` đặt true trong DataStore, khôi phục từ bản sao lưu cuối). Khởi động lạnh trước khi mở tìm kiếm: `settings.build entries=25 ms=88–94 sinceProcessStartMs≈980`; `index#1 apps=81 settings=0 ms=10–67 normalizeCalls=81–83`; `index#2 settings=25 ms=55–115 normalizeCalls=178`; `loading#1`; groups `nullEmit=true` rồi `false`; TotalTime 1187–1315 ms. Mở tìm kiếm + `nguyen`: `loadNames n=2000 ms=318 (mô phỏng)`; `index#3 contacts=2000 ms=114 normalizeCalls=2178`; numbers start#1–#4, cancelled#1–#3, done#1 ms=152. Đóng: `index#4 contacts=0 ms=23`. Ra Cài đặt 9 s về: `groups nullEmit=true` (C1 xác nhận), `index#5 ms=12`. Băm: nguyen 6/1785362264 · duc 5/1567268006 · d 21/-648035484 · ca 8/-1232235718 · cam 0/0 · chrome 1/1492834633 · wifi 1/482764287 · `zztest 00` 0/0 · `  ng  ` 17/2040743061 · NGUYEN 6/1785362264. `shortcutIcon draw` = 0 (không phải launcher mặc định; C8 chờ). | — | Số nền. Một chỉ mục đủ 2000 danh bạ chỉ ~114 ms trên Live 4 debug (thấp xa ước 0,4 s): lưu ý cho cổng Task 4. Tick Task 1 theo điều phối |
| 2026-11-11 | 2 | `normalize` một lượt có bảng tra (`TABLE` dựng bằng `normalizeReference` cho 0x00C0..0x024F và 0x1E00..0x1EFF), ký tự lạ → bản gốc cho cả chuỗi; log tạm `FX4 normalize check` (dải ký tự, mỗi nguồn) trên luồng nền | 4338120 | Build xanh. Chưa chạy trên máy; cần `mismatches=0` |
| 2026-11-11 | 3 | C1: bỏ `onStart { emit(null) }` ở `GetCategoryGroupsUseCase`; `SearchViewModel.visibleApps` thành `StateFlow` (`stateIn(WhileSubscribed(5_000), null)`) | 32839a2 | Build xanh. Kiểm bằng log `nullEmit` và `loading#` |
| 2026-11-11 | 4 | `SearchIndex.Source` + chỉ mục ghép từ 4 nguồn (apps, settings, contacts, shortcuts) giữ trong `StateFlow`; log `FX4 source=… built#`, `FX4 index#n merged` | 68fd0bf | Build xanh. APK `E:\SourceTree\IosLauncher-wtpk\FX4-task4.apk`. Task 4 bắt buộc kiểm tay (điều phối đo) |
| 2026-11-11 | 2–4 (đo) | Điều phối đo `FX4-task4.apk` trên Live 4 (debug, 12:20–12:33, Os26 là launcher mặc định). Khởi động lạnh: `settings.build ms=81`; `source=settings built#1 ms=80 normalizeCalls=121`; `source=apps built#2 ms=120 normalizeCalls=179`; `index#1 merged ms=1`; `normalize check` sweep cases=10064 mismatches=0, settings 194/0, apps 162/0; `groups nullEmit=true` không còn (chỉ `false`); `loading#1` một lần. Mở + `nguyen`: `source=shortcuts ms=13`; `loadNames n=2000 ms=312 (mô phỏng)`; `source=contacts size=2000 ms=15 normalizeCalls=2000` (nền: cả chỉ mục có danh bạ 114 ms); merge 0 ms; check contacts 4000/0, shortcuts 166/0. Đóng: chỉ `index#4 merged ms=0`. Ra ngoài 9 s về: `index#5 merged ms=0`, `nullEmit=false`, không dựng lại nguồn (C1 đạt). Băm kết quả giống hệt giữa task1 và task4 khi Os26 là launcher mặc định (nguyen 7/1947458273, duc 5/1567268006, d 21/-648035484, ca 11/20987776, chrome 3/-902828424, wifi 1/482764287, `  ng  ` 22/12857941, NGUYEN 7/1947458273). Quyết định: giữ Task 4 như đã dựng. Tick Task 2, 3, 4 theo điều phối | — | Điều phối ghi 2026-11-11. `shortcutIcon draw` thay đổi theo cache, so sánh sau Task 8 |
| 2026-11-11 | 5 | C9: bỏ khối `init` dựng danh mục Cài đặt; `ensureSettingsCatalog()` gọi ở mỗi `open()`; nguồn app chờ `indexActive` (bật ở `open()` đầu tiên) | fadf881 | Build xanh. Kiểm: khởi động lạnh không có `settings.build`/`source=apps` trước khi mở tìm kiếm |
| 2026-11-11 | 6 | C6: `SearchViewModel.onHostStopped()` (hủy nạp, `stopContactsWatch`, xóa `contactEntries`, giữ `isOpen`/chuỗi gõ) + nhánh `ON_STOP` trong observer sẵn có của `SearchOverlayController` | 0a72932 | Build xanh. Log `FX4 stopContactsWatch ran`, `FX4 contacts watch stopped entries=0`, `FX4 fake watch unregistered` |
| 2026-11-11 | 7 | C7: `queryCancellable` (`CancellationSignal` + `async`/`await`) cho `numbersFor` và `loadNames`, `OperationCanceledException` bắt riêng; cache số theo phiên trong ViewModel (`numberCache`, mã phiên `contactSession`), xóa ở `close()`, `onHostStopped()` và khi danh bạ đổi | e34565a | Build xanh. `CancellationSignal` thật chỉ kiểm bằng đọc code (không có quyền danh bạ); nguồn giả kiểm cache và hủy coroutine. Log `FX4 numbers cache hit= missing=` |
| 2026-11-11 | 8 | C8: `ShortcutRepository.shortcutIcon` (`setPackage` + `setShortcutIds(listOf(id))`, `LruCache` 32 mục, TTL 60 s); `SearchViewModel.loadIcon` dùng nó; `getShortcuts` (menu) giữ nguyên | 43c4375 | Build xanh. APK `E:\SourceTree\IosLauncher-wt\apk\FX4.apk` (còn nguồn giả + log). Dừng trước Task 9 |
| 2026-11-11 | 5–8 (đo) | Điều phối đo `FX4.apk` (43c4375) trên Live 4, debug, 13:53–14:00, Os26 mặc định, công tắc tìm danh bạ + shortcut bật (đã trả). **C9:** trước khi mở tìm kiếm không có `settings.build`, không `source=`, không `index#`; chỉ `loading#1`, `groups nullEmit=false`, một `stopContactsWatch ran`. Lần mở đầu: `source=apps ms=2`, `settings.build ms=125`, `source=shortcuts`, `source=settings`, `loadNames 2000 ms=322 (mô phỏng)`, `source=contacts ms=9 normalizeCalls=2000`; mọi `normalize check` mismatches=0 (sweep 10064, contacts 4000, apps 162, settings 194, shortcuts 166). **C7:** gõ `nguyen`: `numbers cache hit=0 missing=5`, start/cancelled ×3, done ×1 (cache chỉ trúng khi top-5 ổn định; chấp nhận). **C6:** tìm kiếm mở + `nguyen`, đưa Cài đặt lên trước → `fake watch unregistered`, `stopContactsWatch ran`, `contacts watch stopped entries=0`; broadcast đổi danh bạ lúc ở ngoài → không `loadNames`; quay lại → tìm kiếm còn mở với `nguyen`, `fake watch registered`, một `loadNames`, danh bạ về, số lấy lại (cache đã xóa). Back từ Recents về Home đóng luôn tìm kiếm là hành vi Back sẵn có. **C8:** `chrome` lần đầu 7 lần vẽ icon (gồm các tiền tố c/ch/chr…), gõ lại trong 60 s: 0. **C1:** chỉ `nullEmit=false`. Băm kết quả giống hệt Task 1/4 (nguyen 7/1947458273, duc 5/1567268006, d 21/-648035484, ca 11/20987776, chrome 3/-902828424, wifi 1/482764287, `  ng  ` 22/12857941, NGUYEN 7/1947458273). Không FATAL. Review Task 5–8 đạt, tick theo điều phối | — | Điều phối ghi 2026-11-11 |
| 2026-11-11 | 9 | `git revert` commit nguồn danh bạ giả (`436d9ba`); gỡ toàn bộ log tạm `FX4`, bộ đếm `normalizeCalls/Nanos`, `fx4Check/fx4SweepSamples` (`939fed7`; `normalizeReference` thành `private normalizeSlow`, giữ làm đường dự phòng); `git grep` `FX4`/`FakeContacts` trong `app/` rỗng; `SearchModule.kt` không còn trong diff so với `main`; APK sạch `E:\SourceTree\IosLauncher-wt\apk\FX4-clean.apk` (không chứa `FakeContactsRepository` hay chuỗi `FX4 `); viết knowledgement `docs/knowledgements/FX4-search-library.md`; điền bảng bằng chứng | 436d9ba, 939fed7 | Chờ điều phối kiểm bản sạch (hàng mời danh bạ khi chưa cấp quyền, tìm kiếm vẫn đúng). PR vẫn draft |
| 2026-11-11 | 9 (kiểm bản sạch) | Điều phối kiểm `FX4-clean.apk` (939fed7) trên Live 4, 14:00, chưa cấp `READ_CONTACTS`, công tắc danh bạ bật: tìm kiếm chạy (`ng`, `wifi`), hàng mời "Hiện danh bạ" hiện, không FATAL. Truy vấn có dấu không gõ tự động được: dựa vào sweep `mismatches=0`. Tick Task 9; PR chuyển Ready for review | — | Điều phối ghi 2026-11-11 |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Kết quả giống hệt, tiếng Việt | Băm giống hệt giữa `FX4-task1.apk`, `FX4-task4.apk` và `FX4.apk` (Live 4, Os26 mặc định); `normalize check` mismatches=0 (sweep 10064, contacts 4000, apps 162, settings 194, shortcuts 166). Phần có dấu (`nguyễn`, `đức`) trên bản sạch: đối chiếu bằng sweep (gồm NFD tiếng Việt), chủ dự án gõ thử khi duyệt PR | Đạt (băm + sweep); gõ tay có dấu chờ chủ dự án |
| 2 | Thứ tự nguồn trong chỉ mục | Đọc diff: `SearchIndex(listOf(apps, settings, contacts, shortcuts))`; `maxResults`/`minQueryLength` không đổi | Đạt |
| 3 | Danh bạ rời RAM sau `close()`/`ON_STOP` | Log `contacts watch stopped entries=0`, `stopContactsWatch ran`, `fake watch unregistered`, số lấy lại khi quay về. Gỡ `ContentObserver` thật: chỉ đọc code (không có quyền danh bạ) | Đạt (nguồn giả); phần thật chỉ code |
| 4 | Không dựng lại nguồn không liên quan | Task 4: đóng lớp chỉ `index merged ms=0`; ra ngoài 9 s về: không dựng lại nguồn; `source=contacts ms=15` so với 114 ms cả chỉ mục (nền) | Đạt |
| 5 | Chưa dựng gì trước `open()` đầu tiên | Task 5: trước khi mở tìm kiếm không có `settings.build`, không `source=`, không `index#` (nền: `settings.build ms=88–94`, 2 lần `index#`) | Đạt |
| 6 | Không `null`/`LOADING` khi quay lại | Task 3: `groups nullEmit=true` không còn sau lần đầu (nền: có mỗi lần quay lại); Task 8: chỉ `nullEmit=false` | Đạt |
| 7 | Hủy truy vấn, race đóng phiên | Nguồn giả: `numbers cancelled` ×3, cache xóa khi ON_STOP/đóng/đổi danh bạ. `CancellationSignal` thật + `OperationCanceledException`: **chỉ đọc code** (`queryCancellable`) | Đạt (nguồn giả); phần thật chỉ code |
| 8 | Một icon mỗi lần, TTL | `chrome` lần đầu 7 lần vẽ (gồm các tiền tố đã gõ), gõ lại trong 60 s: 0; TTL hết hạn chưa đo riêng (đọc code) | Đạt (cache); TTL chỉ code |
| 9 | Không còn `FX4`/`FX4_TEMP_FAKE_CONTACTS` trong mã | `git grep "FX4\|FakeContacts" -- app` rỗng; `FakeContactsRepository.kt` không còn; `SearchModule.kt` không còn trong diff so với `main`; APK sạch không chứa lớp giả | Đạt |
| 10 | Không sửa file ngoài vùng | `git diff --stat main -- app`: 8 file, đều trong vùng đã khai (kể cả `SearchOverlayController` và `ShortcutRepository` đã được điều phối đồng ý); không có `HomeActivity`/`HomeViewModel` | Đạt |
| 11 | Hồi quy bản sạch (hàng mời danh bạ khi chưa cấp quyền) | `FX4-clean.apk` (939fed7), Live 4, 14:00, chưa cấp `READ_CONTACTS`, công tắc danh bạ bật: tìm kiếm chạy (`ng` → hàng Cài đặt, app, shortcut; `wifi` → hàng Wi-Fi), hàng mời "Hiện danh bạ" hiện dưới kết quả `wifi`, không FATAL | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: không đổi (không đổi phạm vi tính năng).
- `project_structure.md`: thêm `SearchIndex.Source` vào mô tả `domain/search` nếu có liệt kê; không đổi cây thư mục.
- `room-schema.md`: không đổi.
- `docs/report/Audit-Report-2026-11-10.md`: tick B11, C1, C6, C7, C8, C9 sau khi merge (điều phối).
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (dự kiến: `search.md` — chuẩn hóa một lượt có bảng tra dựng bằng bản cũ và dự phòng nguyên chuỗi; chỉ mục ghép từ nguồn đã chuẩn hóa; đường tắt ASCII vô dụng với tên người Việt; `permissions-and-device-testing.md` — kiểm tính năng danh bạ không cần quyền thật bằng nguồn giả tạm gắn thế qua Hilt trong commit rồi revert (dấu `FX4_TEMP_FAKE_CONTACTS`), và phần nào của nó không kiểm được (provider, `CancellationSignal` thật); `startup-performance.md` — "chưa dựng gì trước khi người dùng cần" kiểm bằng log thay vì `TotalTime` debug).

---

## Câu hỏi cần chủ dự án / điều phối quyết

1. **(Điều phối, 2026-11-11: đồng ý)** Được sửa `ui/home/controller/SearchOverlayController.kt` đúng một nhánh `ON_STOP` trong observer sẵn có. Không nhánh nào khác trong đợt 1 khai file này; đã thêm vào Vùng file.
2. **(Chủ dự án, 2026-11-11)** Được tạm bật mục shortcut trong Cài đặt tìm kiếm (trả lại cuối). Danh bạ: **chỉ dữ liệu giả**, không cấp `READ_CONTACTS`, không chèn vào danh bạ của máy. **Đã sửa plan (vòng 1/5):** mục B thành nguồn danh bạ giả tạm trong app, xem D3/D11.
3. **(Điều phối, 2026-11-11: đồng ý)** `domain/repository/ShortcutRepository.kt` thuộc FX4 (không nhánh nào khác dùng).
4. **(Điều phối, 2026-11-11: chấp nhận)** Hàng danh bạ biến mất một thoáng khi quay về lúc ô tìm kiếm còn mở là chấp nhận được: chỉ gặp khi rời bằng Recents/thông báo, và đúng mục đích "tên chỉ ở RAM khi launcher đang hiện". Task 6 ghi thời gian nạp lại.
