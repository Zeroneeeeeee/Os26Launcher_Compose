# CP — Tìm danh bạ theo số điện thoại (TT#18, nối tiếp G3a) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi Live 4 không tái hiện được). Lệnh viết cho CMD/PowerShell. **Quy tắc dữ liệu thử (chủ dự án, nghiêm): không bao giờ cấp `READ_CONTACTS` cho app trên máy và không chèn gì vào danh bạ của máy; mọi kiểm chứng dùng nguồn danh bạ giả chỉ có trong bản debug (Task 1).**

**Nhánh:** `feature/cp-contacts-phone` · **Worktree:** `E:\SourceTree\IosLauncher-wt\CP-contacts-phone` · **PR:** #54 · **Bắt đầu:** 2026-11-13 · **Plan duyệt:** 2026-11-13 (điều phối; Q1 giữ nguồn giả sau merge chờ chủ dự án)
**Trạng thái:** Chờ review (Task 1-6 xong; PR sẵn sàng chuyển Ready for review)
**Khóa:** không (không đổi schema Room; không đụng file nóng độc quyền; không thêm quyền, không thêm khóa DataStore, không thêm chuỗi)

**Goal:** Trong ô tìm kiếm, gõ từ 3 chữ số trở lên thì hiện các người trong danh bạ có số khớp (khớp ở bất kỳ đâu trong số, bỏ qua khoảng trắng, gạch ngang, dấu chấm, ngoặc; `+84` và `0` đầu coi là một). Hàng kết quả hiện đúng số đã khớp, và nút gọi/nhắn dùng số đó.

**Architecture:** Giữ nguyên đường G3a/FX4: danh bạ là một `SearchIndex.Source` trong RAM, tối đa 5 người, đứng sau app. Thay đổi: (1) `ContactEntry` mang thêm danh sách số (số mặc định đứng đầu); (2) số được chuẩn hóa **một lần lúc dựng nguồn** thành chuỗi chỉ-chữ-số (`+84…` → `0…`), mỗi phím chỉ chuẩn hóa truy vấn rồi quét `contains`; (3) nạp theo **hai pha** — tên trước (như G3a), số sau bằng đúng một truy vấn `Phone.CONTENT_URI` toàn bảng, nên tìm theo tên không phải chờ số; (4) bỏ đường "hỏi provider số cho 5 hàng đang hiện" (`numbersFor` + `numberCache` của C7) vì số đã nằm sẵn trong RAM. Tách thêm một **nguồn danh bạ giả chỉ có trong source set `debug`** (Hilt chọn theo source set, không có trong build release).

**Tech Stack:** Kotlin, Hilt, Flow (đã có). **Không thêm dependency, không đổi schema Room, không thêm khóa DataStore, không thêm chuỗi.** Manifest: chỉ `src/debug/AndroidManifest.xml` (mới) thêm một receiver cho bản debug; `src/main/AndroidManifest.xml` không đổi.

**Spec:** TT#18; `plans/G3a-contacts-search.md` + `knowledgements/G3a-contacts-search.md` (nguồn danh bạ, quyền, chọn số mặc định trên ColorOS); `report/FX4-search-library.md` (`SearchIndex.Source`, chuẩn hóa một lượt, nguồn danh bạ giả của FX4, số đo nền); `lessons/search.md` (mục G3a, FX4, M8d).

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật)

- **Hiện chỉ khớp tên.** G3a cố ý cắt "khớp theo số" khỏi nhánh (chủ dự án, 2026-11-09). Số chỉ được hỏi provider cho tối đa 5 hàng đang hiện (`SearchViewModel.withContactNumbers` → `ContactsRepositoryImpl.numbersFor`), nên chỉ mục **không có số** để so khớp. Muốn khớp số thì số phải nằm trong chỉ mục, tức nằm trong RAM cùng tên.
- **Riêng tư:** danh bạ vẫn chỉ trong RAM (quyết định cũ: `allowBackup="true"`, bài học M8d). Số điện thoại nhạy cảm không kém tên: không ghi đĩa, không log (log chỉ số lượng), xóa cùng `contactEntries` khi đóng lớp / `ON_STOP`.
- **Chi phí:** FX4 đo chỉ mục 2000 danh bạ ~114 ms cho cả chỉ mục, nguồn danh bạ riêng ~15 ms (Live 4, debug, **đo được**). Mỗi người thêm vài số thì số chuỗi phải chuẩn hóa nhân lên (2000 × ~3–5 = 6–10 nghìn chuỗi ngắn). Phải đo lại, không đoán.
- **Số ở nhiều định dạng:** `0912345678`, `0912 345 678`, `091-234-5678`, `+84 912 345 678`, `(091) 2345678`, `0084912345678`, số nước ngoài, số bàn có `(024)`. Chủ dự án chốt: khớp ở bất kỳ đâu, bỏ ký tự phân cách, `+84` ≡ `0` đầu.
- **Không được thử trên danh bạ thật** (D3 của FX4 giữ nguyên): không cấp quyền, không chèn. Hệ quả: **đường đọc `ContactsContract` thật của tính năng này không chạy được trên máy** — xem rủi ro ở "Đánh đổi chấp nhận" và cách giảm.
- **Hilt không cho hai `@Binds` cùng kiểu** (FX4 đã gặp): nguồn giả trong `src/debug` buộc phải gỡ binding `ContactsRepository` khỏi `main`.

### Các phương án đã xét

**Câu hỏi 1: số nằm ở đâu để khớp.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Số vào chỉ mục RAM, chuẩn hóa một lần (chọn)** | `ContactEntry.phones`; `SearchIndex.Source` giữ thêm mảng số đã chuẩn hóa mỗi người; mỗi phím chỉ `contains` trên chuỗi ngắn | Cùng đường FX4 đã đo; mỗi phím không chạm provider; khớp ở bất kỳ đâu và quy tắc `+84`↔`0` làm được trên chuỗi đã chuẩn hóa; hàng kết quả biết **số nào** khớp | RAM thêm số của mọi người (2000 × 5 số ≈ vài trăm KB đến ~1 MB, **suy luận**, đo ở Task 5); một truy vấn provider lớn hơn lúc mở lớp | **Có** |
| B. Hỏi provider mỗi phím bằng `PhoneLookup`/`CONTENT_FILTER_URI` | Không giữ số, để provider khớp | Không giữ thêm gì trong RAM | `PhoneLookup` khớp **trọn số** (không khớp "ở bất kỳ đâu"); `Phone.CONTENT_FILTER_URI` khớp theo quy tắc riêng của provider (không đảm bảo bỏ `+84`↔`0`, **đọc tài liệu/suy luận chưa kiểm chứng**); một vòng binder mỗi phím, ColorOS đã từng bỏ ORDER BY (G3a) nên không tin hành vi provider; không chạy thử được trên nguồn giả | Không |
| C. Chỉ khớp trên số mặc định của mỗi người | Không đổi gì nhiều: chỉ số đã chọn | Ít RAM nhất | Mất ca "số thứ hai/thứ ba của người đó": người dùng nhớ số cơ quan nhưng số mặc định là số di động; tìm ra "không có" là lỗi mắt thấy | Không |

**Câu hỏi 2: nạp số lúc nào.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **Hai pha (chọn)** | Pha 1 `loadNames` như G3a, hiện được tìm theo tên ngay; pha 2 `loadPhones` một truy vấn `Phone.CONTENT_URI` toàn bảng, ghép vào theo `contactId`, dựng lại nguồn danh bạ | Tìm theo tên không chậm đi dù provider trả số chậm (chi phí đọc provider thật trên Oppo/Live 4 **chưa đo và không đo được**, xem trên); hủy được bằng `CancellationSignal` như C7 | Nguồn dựng 2 lần mỗi lần mở lớp (FX4: ~15 ms mỗi lần); gõ chữ số trong cửa sổ chờ pha 2 thì chưa ra kết quả theo số, rồi tự ra khi pha 2 xong (chỉ mục phát lại, `mapLatest` tính lại) | **Có** |
| Một pha | Một hàm `loadContacts` trả cả tên + số | Đơn giản hơn | Cửa sổ chờ gộp chi phí hai truy vấn trước khi tìm tên được; không có số đo provider để biện minh | Không |
| Giữ `numbersFor` (hỏi lại 5 hàng) + truy vấn riêng cho khớp | Hai đường số | Ít sửa `SearchViewModel` | Hai nguồn sự thật cho số; vẫn phải tải toàn bộ số để khớp, nên `numbersFor` thừa | Không |

**Câu hỏi 3: nguồn danh bạ giả, đặt ở đâu.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Source set `src/debug` + module Hilt riêng mỗi build type (chọn)** | `src/debug/.../DebugContactsModule` bind `DebugContactsRepository` (bọc `ContactsRepositoryImpl`, chuyển sang nguồn giả khi bật cờ debug); `src/release/.../ContactsModule` bind thẳng `ContactsRepositoryImpl`; gỡ dòng `@Binds` danh bạ khỏi `main/di/SearchModule.kt` | Mã giả **không được biên dịch vào release** (không phải "bị R8 cắt"): kiểm được bằng cách tìm tên lớp trong dex của APK release; không thể quên revert | Sửa cấu trúc DI một lần (gỡ 1 binding, thêm 2 module nhỏ); hai module trùng tên lớp ở hai source set phải đúng cú pháp (không cùng có trong `main`) | **Có** |
| B. Nguồn giả trong `main`, một commit tạm, `git revert` trước PR (cách FX4) | Đổi một dòng `SearchModule` | Rẻ nhất, đã dùng ở FX4 | Mã giả nằm trong `main` suốt thời làm; revert là việc người làm phải nhớ; không giữ lại được cho lần đo sau | Không (nhưng là phương án dự phòng nếu A không dựng được) |
| C. Cờ `BuildConfig.DEBUG` trong `main`, mã giả vẫn ở `main` | `if (BuildConfig.DEBUG) fake else real` | Không đụng DI | Mã giả vào release trừ khi R8 cắt (R8 **có thể** cắt nhánh `DEBUG` hằng, nhưng "có thể" không phải bảo đảm; không muốn dựa vào) | Không |
| D. Chèn vào provider danh bạ máy | — | Đo đường thật | **Bị chủ dự án cấm** | Không |

### Vì sao chọn

- A1 (số vào chỉ mục) là cách duy nhất vừa khớp "ở bất kỳ đâu" vừa xử lý `+84`↔`0` mà không phụ thuộc quy tắc của provider, và dùng lại đúng cấu trúc FX4 đã đo (đo được: nguồn danh bạ 2000 người ~15 ms, chỉ mục ~114 ms, Live 4 debug).
- Hai pha vì chi phí đọc provider thật không đo được (quy tắc dữ liệu thử), nên thiết kế phải **không làm tìm theo tên tệ đi** dù pha số chậm.
- Source set `debug` vì chủ dự án yêu cầu mã giả không tồn tại trong release; chỉ source set bảo đảm điều đó bằng cấu trúc.

### Đánh đổi chấp nhận

- **Đường đọc provider thật của tính năng này không được chạy trên máy** trước khi merge (không cấp quyền). Giảm rủi ro: truy vấn số mới dùng **đúng cột và đúng cách chọn số đã chạy được trên Live 4 + Oppo ở G3a** (`Phone.CONTACT_ID/NUMBER/IS_SUPER_PRIMARY/IS_PRIMARY/_ID`, chọn bằng code), chỉ bỏ điều kiện `CONTACT_ID IN (…)`; ghép theo `contactId` trong RAM; mọi lỗi `RuntimeException/SecurityException` bắt như cũ. Chủ dự án cần biết: phần này chỉ được **đọc code**; nếu muốn chạy thật, chỉ chủ dự án làm trên máy của mình (việc đó nằm ngoài kịch bản của agent).
- Số điện thoại của mọi người nằm trong RAM suốt lúc lớp mở (G3a chỉ giữ tên). Đã có sẵn cơ chế xóa (đóng lớp, `ON_STOP`, danh bạ đổi, tắt công tắc).
- Số dạng lạ (`+84 (0) 912…`, mã vùng nước ngoài có số 0 đầu bị bỏ) không đảm bảo ghép đúng; chỉ xử lý `+84…`, `0084…`, `0…` (quy tắc Việt Nam) còn lại so thẳng chuỗi số. Xem xét lại khi có ca thật.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Bắt đầu khớp số khi truy vấn có **ít nhất 3 chữ số**; khớp ở bất kỳ đâu trong số; bỏ khoảng trắng, `-`, `.`, ngoặc; `+84` đầu và `0` đầu coi là một | Chủ dự án 2026-11-13 |
| D2 | Hàng kết quả hiện **số đã khớp**; nút gọi/nhắn dùng số đó | Chủ dự án 2026-11-13 |
| D3 | Danh bạ chỉ trong RAM như G3a (số cũng vậy); không lưu, không log | Chủ dự án 2026-11-13 (giữ quyết định G3a) |
| D4 | Giữ giới hạn: tối đa 5 người, xếp sau app (`SearchKind.CONTACT` priority 1, `maxResults` 5) | Chủ dự án 2026-11-13; không có lý do để đổi |
| D5 | Không đổi schema Room, không thêm khóa DataStore, không thêm chuỗi | Chủ dự án 2026-11-13 |
| D6 | Không cấp `READ_CONTACTS` trên máy, không chèn vào danh bạ máy; nguồn giả chỉ trong source set `debug` | Chủ dự án 2026-11-13 (nghiêm) |
| D7 | Đếm "3 chữ số" tính **sau khi** quy `+84` → `0`: kim tìm phải dài ít nhất 3 ký tự số (vd `+84 9` chưa khớp, `+84 91` khớp vì thành `091`) | Claude đề xuất 2026-11-13 để tránh kim `09` khớp gần hết danh bạ; điều phối rà |
| D8 | Truy vấn có thể là chữ số thuần **hoặc** có `+`, khoảng trắng, `-`, `.`, `(`, `)`; truy vấn có chữ cái là truy vấn tên như cũ. Truy vấn chữ số vẫn chạy khớp tên (người tên "Cafe 24") nhưng số mới là đường chính | Claude đề xuất |
| D9 | Hạng khớp số: số **bắt đầu bằng** kim → hạng 1; số **chứa** kim → hạng 2 (cùng thang với tên). Cùng hạng: ưu tiên loại, rồi thứ tự đầu vào (tên A→Z) | Claude đề xuất; app có chữ số trong tên vẫn đứng trước người cùng hạng |
| D10 | Mỗi người giữ tối đa 10 số (số mặc định đứng đầu); số thứ 11 trở đi bỏ | Claude đề xuất (chặn RAM trong ca xấu) |
| D11 | Số chọn cho khớp **tên** (khi truy vấn là tên) giữ nguyên luật G3a: super primary > primary > `_ID` nhỏ nhất (chọn trong code, ColorOS bỏ ORDER BY) | G3a, đo 2026-11-09 |
| D12 | Nguồn giả: **giữ lại trong `src/debug` sau khi merge**, chỉ debug, mặc định `off` | Chủ dự án 2026-11-13 (chấp nhận đề xuất của Claude) |

## Ngoài phạm vi / ghi để sau

- Tô sáng đoạn số đã khớp trong hàng kết quả.
- Cho chọn số khác của một người (hiện hàng hiện số đã khớp, hoặc số mặc định nếu khớp tên).
- Chuẩn hóa số quốc tế ngoài Việt Nam (so thẳng chuỗi số; không tách mã quốc gia).
- Khớp số khi người dùng chưa cấp quyền (không có dữ liệu).
- Đo chi phí đọc `ContactsContract` thật (không được phép trên máy).
- Số khớp "ở cuối" kiểu so sánh quốc tế (`PhoneNumberUtils.compare`): không dùng, khớp "chứa" đã bao ca đó.

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/ContactEntry.kt` | Sửa (thêm `phones`) | không |
| `domain/search/PhoneMatcher.kt` | Tạo (hàm thuần: chuẩn hóa số, phân loại kim, so khớp) | không |
| `domain/search/SearchIndex.kt` | Sửa (nguồn mang thêm số đã chuẩn hóa; xếp hạng số; trả số đã khớp) | không |
| `domain/search/SearchResult.kt` | Sửa nhẹ nếu cần (`Contact.phone` giữ; không đổi `SearchKind`) | không |
| `domain/repository/ContactsRepository.kt` | Sửa (thêm `loadPhones`; bỏ `numbersFor`) | không |
| `data/search/ContactsRepositoryImpl.kt` | Sửa (thêm `loadPhones`, bỏ `numbersFor`) | không |
| `ui/search/SearchViewModel.kt` | Sửa (nạp hai pha; bỏ `withContactNumbers`, `numberCache`, `contactSession`; dựng nguồn danh bạ với số) | không |
| `di/SearchModule.kt` | Sửa (**gỡ** một dòng `@Binds` danh bạ — Hilt dùng chung, hiếm khi gỡ; xem Global Constraints) | dùng chung có kiểm soát |
| `app/src/release/java/gambi/global/os26launcher/di/ContactsModule.kt` | Tạo (bind `ContactsRepositoryImpl`) | không |
| `app/src/debug/java/gambi/global/os26launcher/di/ContactsModule.kt` | Tạo (bind `DebugContactsRepository`) | không |
| `app/src/debug/java/gambi/global/os26launcher/debug/DebugContactsRepository.kt` | Tạo (bọc thật + giả, chọn theo cờ) | không (chỉ debug) |
| `app/src/debug/java/gambi/global/os26launcher/debug/FakeContactsSource.kt` | Tạo (sinh danh bạ giả: bộ nhỏ + 2000 người) | không (chỉ debug) |
| `app/src/debug/java/gambi/global/os26launcher/debug/FakeContactsModeReceiver.kt` | Tạo (đặt chế độ qua `adb shell am broadcast`) | không (chỉ debug) |
| `app/src/debug/AndroidManifest.xml` | Tạo (một `<receiver>`; AGP gộp vào manifest debug) | dùng chung có kiểm soát (chỉ build debug) |
| `ui/search/SearchResultAdapter.kt` | Chỉ nếu Task 4 thấy hàng hiện số khớp chưa đủ (kỳ vọng: **không sửa**; hàng đã hiện `result.phone`) | không |
| `docs/plans/CP-contacts-phone.md`, `docs/knowledgements/CP-contacts-phone.md` | Tạo/sửa | không |

**Không** có file độc quyền (`HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `LayoutReconciler`, `data/layout/db/*`, `core/glass/*`, `DragHelper`, `DragDropTarget`). Không chạm `SettingsUiState/SettingsViewModel`, `SettingsKeys`, `strings.xml`, `libs.versions.toml`, `app/build.gradle.kts` (source set `debug`/`release` có sẵn theo quy ước của AGP, không cần khai). Không chạm vùng của WX (`data/weather`, `ui/weather`, `ui/settings/WeatherSettingsScreen.kt`, widget thời tiết).

## Global Constraints

- Layering `ui → domain ← data`: `PhoneMatcher` thuần Kotlin, không `android.*` (`java.text`, `java.util` được); `ContactEntry`, `SearchIndex` ở `domain` không biết `ContactsContract`.
- **Không tên, không số trong `Log`**, không trong thông báo lỗi; log chỉ số lượng, thời gian, tên lớp ngoại lệ. Log tạm của Task 2/5 dùng tiền tố `CPTMP`, gỡ ở Task 6; không bao giờ in nội dung số hay tên (kể cả số giả, để quy tắc không bị lệch).
- Không ghi số ra DataStore/Room/file/`SharedPreferences`. **Ngoại lệ duy nhất** (chỉ bản debug): `SharedPreferences` của mã debug lưu **chuỗi chế độ** (`off`/`small`/`load`), không phải dữ liệu danh bạ.
- Không đổi schema Room; không thêm khóa DataStore; không thêm chuỗi vào `strings.xml`.
- Mã giả **chỉ** ở `app/src/debug/`. Không `if (BuildConfig.DEBUG)` trong `main` để chọn nguồn giả. Không reference lớp trong `src/debug` từ `main`.
- Bản debug mặc định chế độ **`off`** (đọc `ContactsContract` thật, hành vi như hôm nay: chưa cấp quyền thì hiện hàng mời). Chỉ khi điều phối đặt `small`/`load` bằng broadcast thì dùng nguồn giả.
- `di/SearchModule.kt` là Hilt dùng chung: chỉ **gỡ đúng một `@Binds`** (danh bạ) và để comment trỏ tới hai module theo build type; không đổi binding khác. Nếu WX cũng sửa file này, chỉ phần của mình và rebase.
- Không cấp quyền, không chèn danh bạ, không cài APK, không chạm máy trong bước viết plan; khi thực thi, điều phối cài và chạy `adb`.
- Mọi phép đo ghi rõ debug/release và máy (bài học "bản debug khởi động chậm ~4× release").

## Review Focus

1. **Khớp số đúng bảng trường hợp** (Task 1 Step 4): cả `+84`↔`0`, 3 chữ số tối thiểu, khớp ở giữa số, các dấu phân cách, số nước ngoài, và **không** khớp sai (`+84` bị nhầm với số bắt đầu `84`, `0` đầu bị bỏ sai).
2. **Số đã khớp là số hiển thị và dùng cho gọi/nhắn** (kết quả ghép đúng chỉ số, không nhầm sang số mặc định khi khớp theo số khác).
3. **Tìm theo tên không tệ đi**: nguồn tên vẫn dùng được trước khi pha số xong; không có cửa sổ nào kết quả tên biến mất.
4. **Riêng tư**: không số/tên trong log; `contactEntries` rỗng (cùng mọi số) khi đóng lớp, `ON_STOP`, tắt công tắc, mất quyền; bản sao chuẩn hóa chết cùng nguồn.
5. **Race**: pha 2 về muộn sau khi lớp đã đóng/dừng (phiên mới) thì **không** ghi số vào `contactEntries` (đã rỗng); phím mới hủy phép tìm cũ (`mapLatest`).
6. **Chi phí**: số liệu Task 5 (dựng nguồn, mỗi phím, RAM) đạt ngưỡng ở Step của Task 5; không dựng lại nguồn không liên quan (giữ Review Focus 4 của FX4).
7. **Hilt/DI**: build debug và release đều xanh; mỗi build chỉ có đúng một binding `ContactsRepository`; `main` không import gì từ `debug`.
8. **Release không chứa mã giả**: không có `FakeContactsSource`, `DebugContactsRepository`, `FakeContactsModeReceiver` trong dex của APK release; manifest release không có receiver debug (cách kiểm ở Task 6).
9. **Đường provider thật**: truy vấn `loadPhones` dùng cùng cột/cách chọn với `numbersFor` cũ; chỉ khác không có `IN`; ghép theo `contactId` bỏ qua id không thuộc nhóm hiển thị; `CancellationSignal` và các `catch` giữ nguyên mẫu `queryCancellable`. (Chỉ đọc code; không chạy được.)

---

### Task 1: Nguồn danh bạ giả trong `src/debug` (chưa đụng tính năng)

**Bắt buộc kiểm tay:** không (điều phối cài và đọc log). Task này đứng đầu vì mọi Task sau cần dữ liệu thử.
**Files:** `di/SearchModule.kt` (gỡ 1 dòng), `app/src/release/.../di/ContactsModule.kt`, `app/src/debug/.../di/ContactsModule.kt`, `.../debug/DebugContactsRepository.kt`, `.../debug/FakeContactsSource.kt`, `.../debug/FakeContactsModeReceiver.kt`, `app/src/debug/AndroidManifest.xml`
**Interfaces:** Consumes `ContactsRepository` (giữ nguyên ở Task này). Produces: nguồn giả chọn bằng cờ chế độ; trong build release hành vi **không đổi**.

- [x] **Step 1: DI theo build type.** Gỡ `@Binds bindContactsRepository` khỏi `main/di/SearchModule.kt`. Tạo `src/release/.../di/ContactsModule.kt` (`@Module @InstallIn(SingletonComponent::class) abstract class ContactsModule` với `@Binds` `ContactsRepositoryImpl` → `ContactsRepository`) và `src/debug/.../di/ContactsModule.kt` cùng tên lớp, bind `DebugContactsRepository`. Hai tệp cùng package + cùng tên lớp ở hai source set khác nhau là hợp lệ vì mỗi build type chỉ biên dịch một. Không để `ContactsModule` trong `main`.
- [x] **Step 2: `DebugContactsRepository`.** `@Singleton`, inject `ContactsRepositoryImpl` (thật) và `FakeContactsSource`; implements `ContactsRepository` bằng cách ủy quyền theo **chế độ hiện tại**: `off` → thật; `small`/`load` → giả. Ở chế độ giả: `hasPermission() = true` (bỏ qua cổng quyền; hàng mời không hiện — kiểm hàng mời ở chế độ `off`), `canCallAndMessage() = true`, `openContact/dial/message` trả `false` và **không** mở app Danh bạ/Quay số (không chạm danh bạ máy), `avatar` dùng `ContactAvatar.initialsCircle`. `changes()` = hợp của luồng thật và một `SharedFlow` "chế độ đổi / danh bạ giả đổi" để điều phối kích hoạt nhánh `refreshContacts` mà không cần quyền.
- [x] **Step 3: `FakeContactsSource`.** Sinh **trong RAM, xác định (không `Random` không seed)**, hai bộ:
  - `small` (~16 người cố định, bảng ở Step 4 để đối chiếu kết quả bằng mắt).
  - `load`: **2000 người**, tên `ZZTEST <họ> <đệm> <tên> <số thứ tự>` (nửa có dấu: Nguyễn, Trần, Lê, Phạm, Hoàng, Đặng, Đỗ, Bùi, Vũ, Dương, Ngô, Lý; nửa không dấu — như FX4), mỗi người **0 đến 6 số** (trung bình ~3), phân bố định dạng quay vòng: `09xxxxxxxx` liền; `09x xxx xxxx` cách; `09x-xxx-xxxx` gạch; `09x.xxx.xxxx` chấm; `+84 9xx xxx xxx`; `+849xxxxxxxx`; `(09x) xxxxxxx`; `0084 9xx xxx xxx`; số bàn `(024) 3825 xxxx`; số nước ngoài `+1 (650) 555-xxxx`. Số theo hàm của `id` + chỉ số số, **không trùng** giữa hai người trừ các ca cố ý trùng (Step 4). `contactId` 1..2000, `lookupKey` `cp-<id>`, `photoUri = null`.
  - Chế độ giả có độ trễ mô phỏng `delay(300)` (tên) và `delay(400)` (số; để thấy cửa sổ hai pha) — **không phải** số đo provider (nói rõ ở Nhật ký/knowledgement).
- [x] **Step 4: Bảng bộ nhỏ `small` (đối chiếu kết quả) — ví dụ, agent có thể đổi tên nhưng giữ ca:**

  | Người (ZZTEST…) | Số lưu | Dùng để kiểm |
  |---|---|---|
  | An A | `0912345678` | liền, bắt đầu bằng `0` |
  | Bình B | `+84 912 345 678` | cùng số với An A ở dạng quốc tế → `+84`↔`0` |
  | Chi C | `091-234-5678`, `0987 654 321` | gạch ngang; hai số: khớp số thứ hai |
  | Dũng D | `(091) 2345678` | ngoặc |
  | Đức E | `0912.345.600` | dấu chấm |
  | Em F | `0084 912 345 999` | tiền tố `0084` |
  | Giang G | `+1 (650) 555-0134` | số nước ngoài: khớp `555`, không khớp `0912` |
  | Hà H | `024 3825 1234` | số bàn |
  | Khoa K | (không có số) | không số: kết quả theo tên chỉ không nút |
  | Lan L | `0901 000 003` (mặc định), `0901 000 004` | số mặc định hiện khi khớp **theo tên** |
  | Mai M | `84912345678` | bare `84` không `+`: kỳ vọng **không** bị coi là `0912…` khi lưu (xem Task 1 của kịch bản); truy vấn `84912` vẫn ra (khớp trực tiếp chuỗi số) |
  | Nam N | `+84 (0) 912 000 111` | ca lạ: ghi nhận hành vi (xem "Đánh đổi chấp nhận"), không đòi đúng |
  | Oanh O, Phúc P | `0905 111 222` cả hai | hai người cùng số: cả hai ra, tối đa 5 |
  | Quân Q | `0912 345 678` | trùng số với An A, Bình B: ba người cùng một số → thứ tự theo tên |
  | + vài người nữa (tên có dấu/không dấu) | số có `555` ở giữa | khớp ở giữa số |
- [x] **Step 5: `FakeContactsModeReceiver` + manifest debug.** Receiver đặt chế độ: `adb shell am broadcast -a gambi.global.os26launcher.CP_FAKE_MODE -p gambi.global.os26launcher --es mode off|small|load` (cần `exported="true"` chỉ trong **manifest debug**; Android 14 chặn broadcast không xuất khẩu từ shell). Lưu chuỗi chế độ vào `SharedPreferences` (chuỗi, không phải dữ liệu danh bạ) để sống qua khởi động lại tiến trình; mặc định `off`. Đổi chế độ gọi tín hiệu "danh bạ đổi" để `SearchViewModel.refreshContacts()` chạy lại.
- [x] **Step 6: Build cả hai.** `gradlew.bat assembleDebug` và `gradlew.bat assembleRelease` xanh. Kiểm release sạch ngay (cách ở Task 6 Step 2) để lỗi cấu trúc lộ sớm.
- [x] **Step 7: Kịch bản kiểm (Live 4, APK debug, chế độ `small`/`load`; không cấp quyền):** (a) chế độ `off`: mở tìm kiếm, gõ `ng` → hàng mời "Hiện danh bạ" xuất hiện như cũ (hồi quy cổng quyền); (b) `--es mode small`, mở tìm kiếm, gõ `zztest` → ra 5 người tên khớp, không hàng mời; (c) `--es mode load` rồi mở tìm kiếm gõ `zztest 00`: có kết quả; (d) `adb shell dumpsys package gambi.global.os26launcher | findstr READ_CONTACTS` → quyền **được khai trong manifest nhưng `granted=false`**; (e) `adb shell dumpsys package` hoặc Cài đặt: không có quyền danh bạ được cấp. Điều phối ghi vào Nhật ký.

---

### Task 2: Lõi khớp số — `PhoneMatcher` + chỉ mục (domain thuần)

**Bắt buộc kiểm tay:** không (điều phối chạy bảng kiểm bằng log tạm, như M8b Task 1)
**Files:** `domain/search/PhoneMatcher.kt` (tạo), `domain/search/SearchIndex.kt`, `domain/model/ContactEntry.kt`, `domain/search/SearchResult.kt` (nếu cần)
**Interfaces:** Consumes `AppSearchMatcher.normalize` (không đổi). Produces: `PhoneMatcher.canonical(raw): String`, `PhoneMatcher.needle(query): Needle?` (null = không phải truy vấn số hoặc ngắn hơn 3 ký tự số sau quy đổi), `SearchIndex.Candidate(..., phones)`, kết quả `Contact` mang đúng số đã khớp.

- [x] **Step 1: `ContactEntry`.** Thêm `val phones: List<String> = emptyList()` (số **gốc** như provider trả, số mặc định đứng đầu, tối đa 10). `ContactEntry` vẫn không `toString` số ra log (data class mặc định có `toString`: không ai log `ContactEntry`, ghi trong Review Focus 4).
- [x] **Step 2: `PhoneMatcher` (hàm thuần).**
  - `canonical(raw)`: giữ chữ số; nếu chuỗi (bỏ khoảng trắng đầu) bắt đầu bằng `+84` hoặc `0084` thì thay bằng `0` rồi nối phần chữ số còn lại; các ký tự khác (`+` ở giữa, `-`, `.`, `(`, `)`, khoảng trắng, `;`, `,`, `#`, `*`) bỏ. Số nước ngoài (`+1…`) giữ chữ số, **không** thêm `0`. Dùng một lượt duyệt ký tự, không regex.
  - `needle(query)`: chỉ nhận truy vấn gồm chữ số và `+ - . ( )` khoảng trắng; có chữ cái → `null`. Quy `+84…`/`0084…` đầu thành `0…`. Truy vấn bắt đầu `84` **không** có `+`/`0084`: trả **hai kim** (`84…` và `0…`) — vì người dùng có thể gõ `84912` ý là `+84 912…`, hoặc là một đoạn giữa số. Kim quá ngắn (< 3 ký tự số sau quy đổi) → `null` (D7).
  - Khớp: kim bắt đầu số → hạng 1; số chứa kim → hạng 2 (D9).
- [x] **Step 3: `SearchIndex.Source`.** `Candidate` thêm `phones: List<String> = emptyList()`; `Entry` giữ `canon: Array<String>` + số gốc theo cùng chỉ số, chuẩn hóa **một lần** ở `Source.of`. `search`: dựng `needle` một lần cho truy vấn; nếu có kim thì mỗi `Entry` thử khớp số **song song** với khớp tên (khớp tên giữ nguyên, D8); lấy hạng tốt nhất; ghi chỉ số số đã khớp (bắt đầu-bằng ưu tiên hơn chứa; trong cùng loại chọn số đầu tiên theo thứ tự lưu). Khi xuất kết quả: nếu khớp theo số thì `Contact.copy(phone = số gốc đã khớp)`; khớp theo tên thì giữ `phone` mặc định (`phones.firstOrNull()`). `minQueryLength` của `CONTACT` (2) áp cho **tên**; số dùng ngưỡng của `PhoneMatcher`.
- [x] **Step 4: Bảng kiểm bằng log tạm một lần (tag `CPTMP`, M8b Task 1; gỡ ở Task 6).** Gọi `PhoneMatcher`/`SearchIndex` với bộ nhỏ `small` và in **chỉ** `hạng/chỉ số/khớp hay không` (không in số). Đối chiếu bảng kỳ vọng sau (nếu lệch → sửa, không sửa bảng):

  | Truy vấn | Kỳ vọng |
  |---|---|
  | `091` | An A, Bình B, Chi C, Dũng D, Đức E, … (bắt đầu `091` sau quy đổi) — tối đa 5 người, hạng 1 trước hạng 2 |
  | `912345` | An A, Bình B, Quân Q (cả `0912…` lẫn `+84 912…`: số chứa `912345` sau quy đổi) |
  | `+84912345` | cùng tập `0912345` (hai dạng hợp nhất) |
  | `0912 345` | cùng tập, bỏ khoảng trắng |
  | `091-234` / `091.234` / `(091) 234` | khớp An A, Bình B, Chi C, Dũng D (đã bỏ phân cách) |
  | `84912` | Mai M (khớp trực tiếp `84912…`) và các số `0912…` (kim thứ hai `0912`): ghi nhận tập thật |
  | `0084912` | cùng như `0912`, gồm Em F |
  | `987654` | Chi C (số thứ hai) và hiện đúng số thứ hai trên hàng |
  | `555` | Giang G và các người có `555` ở giữa số |
  | `09` | **không** khớp số (ngắn hơn 3 ký tự số) |
  | `+84 9` | **không** khớp số (sau quy đổi `09`, ngắn hơn 3; D7) |
  | `ab 123` | truy vấn tên, không khớp số |
  | `zzz` | tên, không đổi so với G3a |

  Kèm so sánh hàng loạt: mọi truy vấn tên của FX4 (`nguyen`, `duc`, `đức`, `d`, `ca`, `cam`, `chrome`, `wifi`, `zztest 00`, `  ng  `, `NGUYEN`) cho **dãy `stableId` giống hệt** trước/sau (băm như FX4; chỉ khác khi nguồn danh bạ giờ có số — truy vấn không phải số thì không đổi).
- [x] **Step 5: Build xanh, commit** `feat: match contacts by phone number in the search index`.

---

### Task 3: Nạp số (hai pha) + bỏ đường hỏi từng hàng

**Bắt buộc kiểm tay:** không
**Files:** `domain/repository/ContactsRepository.kt`, `data/search/ContactsRepositoryImpl.kt`, `ui/search/SearchViewModel.kt`, mã debug (`DebugContactsRepository`, `FakeContactsSource`)
**Interfaces:** Consumes `ContactEntry.phones`, `SearchIndex.Candidate.phones`. Produces: `ContactsRepository.loadPhones(ids: Set<Long>): Map<Long, List<String>>`; `numbersFor` bị bỏ.

- [x] **Step 1: `loadPhones`.** Một truy vấn `Phone.CONTENT_URI` **không có `CONTACT_ID IN`**, cùng cột như `numbersFor` cũ (`CONTACT_ID, NUMBER, IS_SUPER_PRIMARY, IS_PRIMARY, _ID`), sắp `IS_SUPER_PRIMARY DESC, IS_PRIMARY DESC, _ID ASC` làm gợi ý; **thứ tự cuối cùng quyết định trong code** (ColorOS bỏ ORDER BY, G3a): mỗi người gom số, số mặc định (super primary) > số chính (primary) > `_ID` nhỏ nhất đứng đầu; các số còn lại theo `_ID`; chặn 10 số mỗi người; bỏ id không có trong tập tên (đã lọc `IN_VISIBLE_GROUP` ở pha 1); bỏ số rỗng và số trùng (so theo chuỗi `canonical`). Chạy trong `queryCancellable` (C7) và bắt `OperationCanceledException`/`SecurityException`/`RuntimeException` đúng mẫu hiện tại. Chặn tổng số hàng đọc (hằng số, vd 50 000) để không ngốn RAM ở ca bất thường. Log chỉ số lượng hàng và ms (tag `CPTMP`, gỡ ở Task 6), không nội dung.
- [x] **Step 2: `SearchViewModel` hai pha.** `refreshContacts()`: pha 1 như hiện nay (`loadNames` → `contactEntries`); pha 2 ngay sau (cùng `contactsLoadJob`, nên bị hủy khi đóng/`ON_STOP`/đổi danh bạ): `loadPhones(ids)` → `contactEntries.value = entries.map { it.copy(phones = ...) }` **chỉ khi** lớp còn mở và phiên không đổi (so mã phiên như C7; Review Focus 5). `contactsSource` dựng lại từ `contactEntries` (đã có số): `Candidate(Contact(it, phone = it.phones.firstOrNull()), it.name, phones = it.phones)`.
- [x] **Step 3: Bỏ đường cũ.** Xóa `withContactNumbers`, `numberCache`, `contactSession`/`clearNumberCache` và lời gọi trong `compute`, `close()`, `onHostStopped()`, đổi danh bạ (mã phiên cho pha 2 giữ dưới tên mới gọn hơn nếu cần). Xóa `numbersFor` khỏi interface và `ContactsRepositoryImpl`. Hàng khớp theo tên hiện `phone` mặc định lấy từ chỉ mục (D11).
- [x] **Step 4: Nguồn giả cập nhật** theo interface mới (`loadPhones` có `delay(400)` hủy được).
- [x] **Step 5: Build xanh, commit** `feat: load contact numbers in a second phase and drop per-row lookups`.
- [x] **Step 6: Kịch bản kiểm (Live 4, debug, chế độ `small`):** (a) mở tìm kiếm và **gõ tên ngay** (trước 400 ms): hàng tên hiện, chưa chắc có số; sau đó tự cập nhật hiện số mặc định (không nhấp nháy mất hàng); (b) gõ `912345`: ra đúng người theo bảng Task 2, hàng hiện **số đã khớp** (người Bình B hiện `+84 912 345 678` dạng gốc); (c) nút Gọi trên hàng khớp theo số **thứ hai** của Chi C: ở chế độ giả `dial` trả `false` nên Toast "Không mở được…" xuất hiện, **không** mở trình quay số — để kiểm số được truyền, xem log tạm `CPTMP dial len=<số ký tự>` (chỉ độ dài, không số) hoặc đối chiếu bằng contentDescription; ghi cách điều phối chọn; (d) đóng lớp rồi mở lại: nguồn nạp lại, không còn số cũ (log `contacts=0` khi đóng); (e) `adb shell am broadcast … --es mode small` khi lớp đang mở: danh bạ nạp lại và kết quả cập nhật.

---

### Task 4: Hàng kết quả, nút gọi/nhắn, trường hợp biên (UI)

**Bắt buộc kiểm tay:** không
**Files:** `ui/search/SearchResultAdapter.kt` (chỉ nếu cần), `ui/search/SearchViewModel.kt`
**Interfaces:** Consumes `SearchResult.Contact.phone` = số đã khớp.

- [x] **Step 1: Soát hàng.** `SearchResultAdapter` đã hiện `result.phone` làm dòng phụ và nút Gọi/Nhắn đã dùng `result.phone`; xác nhận bằng đọc code rồi bằng mắt (D2). Số dài (`+84 912 345 678`, `+1 (650) 555-0134`) không tràn hàng (`maxLines=1` + `ellipsize` nếu thiếu thì thêm trong layout hiện có, không thêm chuỗi). `contentDescription` của nút giữ `Gọi %1$s` (tên) — không đọc số khi TalkBack (giữ hành vi G3a).
- [x] **Step 2: Biên.** (i) Người **không có số**: không khớp theo số, vẫn ra theo tên, không nút; (ii) hai người cùng số: cả hai ra, thứ tự theo tên (D9); (iii) truy vấn số trùng tên có số (ZZTEST có `00` trong tên + `00` trong số): kết quả không nhân đôi (một `stableId` một hàng); (iv) truy vấn số khi công tắc "Tìm trong danh bạ" tắt hoặc chưa cấp quyền: không có hàng người, hàng mời như G3a (từ 2 ký tự, như cũ); (v) xoay màn hình khi đang có kết quả theo số: giữ kết quả; (vi) tối đa 5 người dù hàng trăm số khớp (`load`, gõ `091`).
- [x] **Step 3: Kịch bản kiểm (Live 4, debug, `small` rồi `load`):** (a) các ca (i)–(vi); (b) gõ `091`, `0912 345`, `+84 912 345`, `(091) 234`, `91-234`, `912.345`: cùng tập người nhất quán với bảng Task 2; (c) bấm hàng kết quả: ở chế độ giả Toast "Không mở được…" (hành vi chủ ý), lớp **không** đóng; (d) giao diện tối/sáng không đổi so với G3a; (e) chữ hiển thị lớn (cỡ chữ hệ thống lớn) không cắt số mất nghĩa.

---

### Task 5: Đo chi phí với 2000 người (nhiều số)

**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng dùng để chọn phương án (mục 4.2): số đo quyết định giữ A1 (số vào chỉ mục RAM, một nguồn) hay phải thu hẹp (chỉ giữ số mặc định hoặc tải số lười sau khi gõ chữ số đầu tiên). Lý do đứng vững: nếu dựng nguồn hoặc mỗi phím vượt ngưỡng, thiết kế đổi. Điều phối chạy, ghi số vào Nhật ký.
**Files:** log tạm `CPTMP` trong `SearchViewModel` (`buildSource`, `compute`), `ContactsRepositoryImpl`/nguồn giả
**Interfaces:** Produces: bảng số đo.

- [x] **Step 1: Log tạm tag `CPTMP`** (gỡ ở Task 6; chỉ số và ms, **không** nội dung): `contacts source built size=… phoneCount=… ms=…`, `search q#=… ms=…` (từ đầu `compute` đến xong khớp, không gồm UI), `contacts index phase2 applied ms=…`. RAM: `Debug.getNativeHeapAllocatedSize`/`Runtime.totalMemory` trước và sau mở lớp (chỉ để so, không in tên).
- [x] **Step 2: Chuẩn bị:** `--es mode load` (2000 người, ~6000 số — ghi số đếm thực vào Nhật ký), APK debug trên Live 4, Os26 là launcher mặc định, **không cấp quyền danh bạ**. Khởi động lạnh (mở Cài đặt lên trên rồi `force-stop` rồi `am start -W`, bài học RB). Bản debug không đại diện cho release; **ghi cả hai nhãn**.
- [x] **Step 3: Số đo cần có (Live 4, debug):**

  | # | Số đo | Ngưỡng dùng để quyết (Claude đề xuất, suy luận chưa kiểm chứng) | Cách đo |
  |---|---|---|---|
  | 1 | Dựng nguồn danh bạ **không số** (pha 1) và **có số** (pha 2), ms | pha 2 ≤ 60 ms (FX4 nền: 15 ms cho 2000 tên) | log `source built` |
  | 2 | Mỗi phím gõ chữ số trên 2000 × ~3 số: `search ms` p50/p95 | p50 ≤ 8 ms, p95 ≤ 20 ms (M8d đo Oppo p50 5–7 ms, p95 7–9 ms với 345 app) | gõ `0`, `09`, `091`, `0912`, `09123`, `091234`, `0912345` bằng `input text`, đọc 5 lần lặp |
  | 3 | Mỗi phím gõ tên trên cùng dữ liệu | **không tệ hơn** số nền FX4 (so băm/ms) | `nguyen`, `duc` |
  | 4 | Số chuỗi chuẩn hóa mỗi lần dựng (đếm) | ghi nhận (~phoneCount) | log |
  | 5 | RAM tăng khi mở lớp có 2000 × ~3 số (so với tên-only) | ≤ ~3 MB (suy luận; nếu lớn hơn, xem `ContactEntry.phones` giữ cả chuỗi gốc **và** chuẩn hóa) | `Runtime` trước/sau |
  | 6 | Cửa sổ chờ pha 2 với độ trễ giả 400 ms: gõ `912` ngay khi mở — kết quả ra sau khi pha 2 xong, không lỗi, không nhấp nháy | đúng hành vi | quan sát + log |
  | 7 | Đóng lớp / `ON_STOP`: bản sao số biến mất (`contacts=0`, `phones` rỗng) | đúng | log |
  | 8 | Phiên mở lại 3 lần liên tiếp: không rò (RAM không tăng dần) | RAM ổn định | `Runtime` |

  Chi phí đọc `ContactsContract` thật: **không đo được, không đo** (Review Focus 9); nguồn giả dùng độ trễ mô phỏng. Ghi thẳng vào knowledgement để khỏi bị đọc nhầm.
- [x] **Step 4: Kết luận trong Nhật ký:** đạt ngưỡng → giữ thiết kế; trượt → đề xuất thu hẹp (D10 giảm số mỗi người, hoặc pha 2 chỉ chạy khi truy vấn đầu tiên là số) và **báo điều phối**, không tự đổi phạm vi.

---

### Task 6: Đóng nhánh — gỡ log, kiểm release không chứa mã giả, tài liệu

**Bắt buộc kiểm tay:** không (điều phối kiểm bản release)
**Files:** `SearchViewModel.kt`, `ContactsRepositoryImpl.kt` (gỡ log `CPTMP`), `docs/knowledgements/CP-contacts-phone.md` (tạo), plan (Nhật ký, bằng chứng)

- [x] **Step 1: Gỡ log tạm.** `git grep CPTMP -- app` rỗng; `import android.util.Log` không còn dùng thì gỡ. Giữ các `Log.w` tên lớp ngoại lệ của G3a.
- [x] **Step 2: Kiểm release không có mã giả (bắt buộc, ghi bằng chứng vào plan).**
  1. `gradlew.bat assembleRelease` xanh.
  2. Trong APK release (`app/build/outputs/apk/release/*.apk`) tìm tên lớp: giải nén bằng `apkanalyzer` (nếu có trong SDK) hoặc `unzip` rồi `findstr /i "FakeContactsSource DebugContactsRepository FakeContactsModeReceiver CP_FAKE_MODE" classes*.dex` → **không có dòng nào**. (Release dùng `-dontobfuscate` nên tên lớp không bị đổi: nếu có mã giả thì sẽ thấy tên; nhưng bằng chứng chính là lớp **không được biên dịch**, vì ở `src/debug`.)
  3. `build/intermediates/merged_manifests/release/.../AndroidManifest.xml` không chứa `FakeContactsModeReceiver` hay `CP_FAKE_MODE`.
  4. `git grep -n "src/debug\|debug\\." -- app/src/main` không có import từ package `debug` (không có tham chiếu ngược từ `main`).
  5. APK debug **có** các lớp đó (đối chứng để chứng minh lệnh tìm hoạt động).
- [x] **Step 3: Knowledgement** (`docs/knowledgements/CP-contacts-phone.md`) theo mẫu mục 8: Mermaid luồng hai pha (`sequenceDiagram`: mở lớp → pha 1 tên → pha 2 số → dựng nguồn → gõ số → khớp → hàng hiện số khớp → gọi/nhắn), `flowchart` quy tắc quy đổi số và phân loại kim, bảng số đo Task 5, mục "Bài học cho AGENTS.md" (ghi rõ: Hilt theo source set `debug`/`release` thay vì cờ; Phone.CONTENT_URI toàn bảng không cần `IN`; số trong RAM), mục nói thẳng đường provider thật chỉ đọc code.
- [x] **Step 4: Rà Review Focus và điền bảng bằng chứng; đề xuất cập nhật tài liệu chung; rebase lên `main`; build xanh cả debug và release; chuyển PR sang Ready for review.**

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Os26 là launcher mặc định). Oppo A3x **không cần** (không liên quan kính; ColorOS chỉ liên quan thứ tự số khi đọc provider thật, không chạy được). **Dữ liệu:** nguồn giả `small` rồi `load`; **không cấp quyền danh bạ**; không chèn danh bạ. APK debug của nhánh (`git rev-parse --short HEAD` ghi vào báo cáo).

1. Chế độ `off`: tìm kiếm chạy như hôm nay, hàng mời "Hiện danh bạ" hiện, tìm app/Cài đặt đúng (hồi quy G3a, FX4).
2. `--es mode small`: bảng truy vấn ở Task 2 Step 4 cho đúng người; hàng hiện **số đã khớp**; nút Gọi/Nhắn có mặt ở người có số, vắng ở người không số.
3. Gõ `091`, `0912 345`, `+84 912 345`, `(091) 234`, `912.345`, `0084 912`: cùng tập người (Bình B ở dạng `+84` cũng ra khi gõ `0912…`).
4. `09`, `+84 9`: **không** ra người theo số (dưới 3 ký tự số); `091` ra.
5. Người có hai số, khớp theo số thứ hai: hàng hiện số thứ hai; nút Gọi dùng số đó (qua log độ dài `CPTMP`, rồi gỡ).
6. Khớp theo **tên** của người có hai số, một số mặc định: hàng hiện số mặc định (hồi quy G3a).
7. `--es mode load`: Task 5 (số đo 1–8); tối đa 5 người cho truy vấn `091`; gõ liên tục nhanh không đơ (cảm nhận) và không crash.
8. Đóng lớp, ra Cài đặt app khác 9 s, về: không còn số trong RAM (log `contacts=0`), mở lại nạp lại.
9. Release: `assembleRelease` + Task 6 Step 2 (không có mã giả); (nếu điều phối cài release thử trên Live 4 theo RB: tìm kiếm chạy, hàng mời hiện, không có receiver debug trong `dumpsys package`).

**Hồi quy cần kèm:** tìm app/Cài đặt/shortcut không đổi (băm kết quả FX4), hàng mời + nút "x" + công tắc "Tìm trong danh bạ", lớp tìm kiếm mở/đóng, xoay.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Live 4 (Android 11), 2026-11-13, nguồn giả, quyền danh bạ luôn `granted=false`: Task 1-2 `4aa5d5b`, Task 3-4 `e0b1eef`, Task 5 đo lại `d4d1d14` — đạt. Chưa kiểm: Oppo A3x, bản release trên máy, đường đọc ContactsContract thật.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-13 | Plan | Viết plan, chưa code | — | Chờ điều phối rà |
| 2026-11-13 | 1 | Nguồn danh bạ giả trong src/debug + Hilt theo build type; receiver exported=true chỉ ở manifest debug. assembleDebug + assembleRelease xanh; dex release không có FakeContactsSource/DebugContactsRepository/FakeContactsModeReceiver/CP_FAKE_MODE, manifest release không có receiver; debug có đủ | 3a3b586 | Chờ điều phối kiểm trên Live 4 |
| 2026-11-13 | 1 (bổ sung) | Receiver debug thêm `android:permission="android.permission.DUMP"` (đọc tài liệu, chưa đo) | xem git log | Điều phối yêu cầu; kiểm trên máy cùng Task 1 |
| 2026-11-13 | 2 | `PhoneMatcher`, chỉ mục mang số chuẩn hóa + trả số đã khớp, `ContactEntry.phones`; `compileDebugKotlin` + `compileReleaseKotlin` xanh (chưa assemble để không ghi đè APK đang kiểm). Lệch nhỏ so với plan: số 0 trung gian sau `+84` (`+84 (0) 912…`) được bỏ nên Nam N cũng khớp. Log tạm `CPTMP` (debug, `CpTmpCheck`) chạy khi đặt chế độ `small`. Fake `loadNames` tạm kèm số để thử trước Task 3 | xem git log | Chờ điều phối kiểm bảng truy vấn trên Live 4 |
| 2026-11-13 | 1, 2 (kiểm) | Điều phối kiểm Live 4 (debug 4aa5d5b, 11:03–11:10): broadcast tới receiver DUMP + exported=true được, READ_CONTACTS vẫn granted=false; bảng CPTMP khớp quy tắc. **Sửa bảng kỳ vọng Task 2:** (a) khớp theo tên log chỉ số số mặc định (0), không phải -1; (b) `912345` gồm cả 3 và 4 (đều chứa `912345`), năm người đầu = 1..5. Khe hở debug: sau `--es mode off` hàng "Hiện danh bạ" không hiện khi tiến trình còn sống (nghi `contactsGranted` giữ giá trị giả tới lần đọc lại quyền) | — | Đạt, tick Task 1, 2 |
| 2026-11-13 | 3 | `ContactsRepository.loadPhones` (một truy vấn Phone toàn bảng, ghép theo contactId, số mặc định đứng đầu, bỏ trùng sau chuẩn hóa, tối đa 10) thay `numbersFor`; `SearchViewModel` hai pha (pha 2 trong cùng job, `ensureActive` trước khi ghi); bỏ `withContactNumbers`/`numberCache`/`contactSession`; nguồn giả: `loadNames` không kèm số, `loadPhones` trễ 400 ms. `assembleDebug` xanh. Khe hở debug ở dòng trên: `refreshContacts` đọc lại `hasPermission()` mỗi lần mở lớp và khi `changes()` phát, nên đường đổi chế độ đã gọi đúng chỗ; chưa tìm ra nguyên nhân từ code, (ĐÃ RÚT LẠI 2026-11-13: hàng mời hiện đúng, lỗi là do cách thử, không phải giới hạn debug) | xem git log | Chờ điều phối kiểm Task 3 |
| 2026-11-13 | 4 | Đọc code: `SearchResultAdapter` đã hiện `result.phone` (số đã khớp) ở dòng phụ `maxLines=1` + `ellipsize=end`, nút gọi/nhắn dùng `result.phone`, `areContentsTheSame = old == new` nên số đến ở pha 2 làm hàng bind lại; **không sửa adapter/layout**. Thất bại `loadPhones` (lỗi provider, chạm trần 50 000 hàng, rỗng): hàng mất số + nút gọi/nhắn, **chấp nhận đánh đổi, không làm dự phòng từng hàng** (G3a tra từng hàng; dự phòng sẽ giữ lại đường provider mỗi phím mà C7 đã gỡ); ghi log chỉ số lượng/tên lớp (`G3A`: lỗi, "hit the row cap", "no numbers loaded for N contacts"). Log tạm `CPTMP` ghi `contactsGranted` và `promptRow` để chẩn đoán hàng mời | xem git log | Chờ điều phối kiểm Task 3 + 4 |
| 2026-11-13 | 5 (log) | Log tạm `CPTMP` cho phép đo: `source=contacts size phones ms` (dựng nguồn, phase 1 có phones=0, phase 2 có phones>0), `phase1 loadNames`/`phase2 loadPhones` ms (gồm độ trễ giả), `search qlen textual results ms` mỗi phím, `ram … usedKB` (sau gc gợi ý) trước pha 1 và sau pha 2. Không in tên/số | xem git log | Chờ điều phối đo trên Live 4 |
| 2026-11-13 | 3, 4 (kiểm) + 5 (đo lần 1) | Điều phối kiểm Live 4 (debug e0b1eef): Task 3 + 4 đạt (hàng mời hiện ở chế độ off; số khớp, số thứ hai, số mặc định, không số, trùng số, 091 tối đa 5); cửa sổ pha 2 chưa bắt được trên máy, giữ là đọc code. Task 5 (2000 người, ~6000 số): dựng nguồn pha 1 8–16 ms, pha 2 12.7–17.5 ms, RAM +2558 KB rồi ~+2034 KB không tăng; **mỗi phím truy vấn số ≥ 3 chữ số p50 16.5–22.1 ms, p95 19–24.3 ms: TRƯỢT ngưỡng 8/20**; tên p50 8–14 ms | — | Tối ưu trong thiết kế A1, không đổi thiết kế |
| 2026-11-13 | 5 (tối ưu) | `SearchIndex.search`: bỏ sắp xếp toàn bộ hit (thùng theo (hạng, loại), mỗi thùng tối đa `maxResults`), hạng dạng Int thay `Int?`, dựng `" $q"` một lần thay cho mỗi nhãn; log tạm tách `nameMs`/`phoneMs`/`assembleMs`. Kết quả và thứ tự giữ như cũ (đọc code) | xem git log | Chờ điều phối đo lại |
| 2026-11-13 | 5 (đo lại) | Điều phối đo `d4d1d14` (debug, load): pha 1 3,8–19,7 ms, pha 2 9,7–25,7 ms, RAM +2,0–2,7 MB không tăng; số qlen3–7 p50 3,2–9,1 / p95 9,9–12,3 ms (trước 16,5–22,1 / 19–24,3); tên p50 4,3–7,0; tách: name 2,9–3,4, phone 4,5–5,5 (gồm chi phí nanoTime), ghép <0,04 | — | **Đạt** (điều phối quyết giữ A1, không bỏ khớp tên với truy vấn số) |
| 2026-11-13 | 6 | Gỡ mọi log tạm `CPTMP`, trường đo thời gian của `SearchIndex`, `CpTmpCheck.kt` (file tạm của chính nhánh này); giữ cảnh báo `G3A`. Kiểm release: dex + manifest không có mã giả. Viết knowledgement, điền bằng chứng. `assembleDebug` + `assembleRelease` xanh | xem git log | Chờ chuyển PR sang Ready (điều phối) |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Khớp số đúng bảng trường hợp | Live 4, nguồn giả `small`, debug `4aa5d5b`: log CPTMP khớp quy tắc (điều phối tính tay); UI: `0912`/`+84912` → An, Bình, Chi, Dũng, Đức đúng định dạng đã lưu; `09`, `+84 9` không khớp. Bảng kỳ vọng đã sửa 2 chỗ (chỉ số số mặc định khi khớp tên; `912345` gồm cả 3 và 4) | Đạt |
| 2 | Số đã khớp là số hiển thị và dùng cho gọi/nhắn | `987654` → Chi "0987 654 321"; đọc code: nút dùng `result.phone`. Gọi/nhắn thật không chạy ở chế độ giả (chỉ Toast), nên số truyền vào `dial/message` **chỉ đọc code** | Đạt (hiển thị); gọi/nhắn thật chỉ code |
| 3 | Tìm theo tên không tệ đi | Task 3/4: `an`, `lan`, `khoa` hiện ngay; số đến sau; Task 5 lần 2: tên qlen1 2,1/4,3 ms, qlen2 7,0/12,1, qlen3 5,3/9,0. Không có nền FX4 mỗi phím để so trực tiếp; cửa sổ pha 2 chưa bắt được trên máy | Đạt (đo + code); cửa sổ pha 2 chỉ code |
| 4 | Riêng tư | `git grep` log: chỉ còn `G3A` số lượng/tên lớp; `ContactEntry.toString` không in số; đóng/ON_STOP xóa `contactEntries` (RAM không tăng qua 4 lần mở). Không tên/số trong dữ liệu app | Đạt (đọc code + RAM); quét dữ liệu app bằng `run-as` chưa làm |
| 5 | Race pha 2 sau khi đóng | `ensureActive()` + kiểm `isOpen` trước khi ghi; hủy job khi đóng. Chưa bắt được trên máy | Chỉ đọc code |
| 6 | Chi phí | Mục 9 knowledgement: pha 2 9,7–25,7 ms (≤ 60), RAM +2,0–2,7 MB (≤ 3), mỗi phím ≥ 3 chữ số p95 ≤ 12,3 ms, p50 3,2–9,1 ms (debug, nguồn giả) | Đạt (debug) |
| 7 | Hilt/DI | `assembleDebug` và `assembleRelease` xanh (13:51–13:52); mỗi build một binding; `git grep "os26launcher\.debug" -- app/src/main` rỗng | Đạt |
| 8 | Release không chứa mã giả | APK release: không có `FakeContactsSource`/`DebugContactsRepository`/`FakeContactsModeReceiver`/`CP_FAKE_MODE`/`CPTMP` trong dex; manifest gộp release không có `CP_FAKE_MODE`; APK debug có đủ (classes3/4/17) | Đạt |
| 9 | Đường provider thật | `loadPhones` cùng cột và cách chọn số với G3a, ghép theo `contactId`, `queryCancellable` | **Chỉ đọc code** (không cấp quyền theo quy tắc dữ liệu thử) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#18 thêm "khớp theo số điện thoại (từ 3 chữ số, `+84`≡`0`)".
- `project_structure.md`: mục G3a/FX4 — thêm `domain/search/PhoneMatcher`; `ContactEntry.phones`; `ContactsRepository.loadPhones` thay `numbersFor`; **source set `debug`/`release`** cho `ContactsModule` (nguồn giả trong `src/debug`, nếu giữ sau merge — xem câu hỏi 1).
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `lessons/search.md` và `lessons/permissions-and-device-testing.md`): (1) dữ liệu thử riêng tư cho tính năng cần quyền nhạy cảm = nguồn giả ở source set `debug` + Hilt theo build type, kiểm release bằng dex; (2) chuẩn hóa số `+84`↔`0`; (3) `Phone.CONTENT_URI` không có `IN` và ghép theo id.

## Câu hỏi cho chủ dự án

1. **(ĐÃ TRẢ LỜI 2026-11-13: giữ, debug-only, mặc định off.) Giữ nguồn danh bạ giả sau khi merge hay gỡ?** — Khuyến nghị: **giữ trong `src/debug`, mặc định chế độ `off`**. Lý do: mã chỉ biên dịch vào build debug (không thể lọt release, kiểm bằng dex ở Task 6), và nó là cách duy nhất đo lại tìm kiếm với 2000 người mà không động vào danh bạ thật; gỡ đi thì lần sau (FX khác, WX, đổi chuẩn hóa) lại phải viết lại như FX4. Nếu không muốn giữ, gỡ bằng `git revert` của commit Task 1 trước khi merge (một commit riêng, tách khỏi tính năng, nên revert gọn).
2. **Phiên bản "3 chữ số" tính sau khi quy `+84` → `0`** (D7: `+84 9` chưa khớp, `+84 91` khớp). Khuyến nghị: giữ; nếu chủ dự án muốn đếm đúng ba chữ số gõ vào (kể cả `84`) thì `+84 9` sẽ kéo gần hết danh bạ bắt đầu `09`.

Không có câu hỏi nào khác cần chủ dự án quyết; còn lại là quyết định kỹ thuật ghi ở bảng trên.
