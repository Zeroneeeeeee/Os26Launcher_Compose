# RN — Thay đổi tên ứng dụng (màn 5.3 của bản nháp Figma) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không cần: nhánh này không đụng kính). Lệnh viết cho CMD/PowerShell. Agent không tự cài APK; máy test dùng chung với việc khác của chủ dự án nên **xác nhận máy rảnh trước mọi lần cài hoặc chạy script**.

**Nhánh:** `feature/rn-rename-apps` · **Worktree:** `E:\SourceTree\IosLauncher-wt\RN-rename-apps` · **PR:** #51 (draft) · **Bắt đầu:** 2026-11-12 · **Plan duyệt:** 2026-11-13 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review Task 6 (đóng nhánh); chờ kiểm tay
**Khóa:** không (không đổi schema Room, không cần Oppo)

**Goal:** Người dùng đặt **tên riêng cho từng app** trong Cài đặt → "Thay đổi tên ứng dụng" (màn 5.3 bản nháp): danh sách app, mỗi hàng hiện tên đang dùng (kèm "Tên gốc: …" nếu đã đổi), bấm vào để sửa, có "Khôi phục" (về tên gốc) và "Lưu". Tên mới hiện ở **mọi nơi app xuất hiện** (lưới Home, dock, thư mục, App Library, tìm kiếm, Ứng dụng đã ẩn, trợ năng) và Home cập nhật ngay khi quay lại, **không cần khởi động lại**.

**Architecture:** Tên tùy chỉnh là một **lớp phủ (overlay) áp ở một chỗ duy nhất**: `AppRepositoryImpl.apps`, nguồn mà mọi nơi hiển thị app đều đọc (đã kiểm bằng grep, xem mục "Mọi nơi nhãn xuất hiện"). `AppItem.label` từ đó là **tên hiển thị**; thêm trường `AppItem.originalLabel` giữ tên gốc. Tên lưu trong **một file DataStore riêng** (`app_labels`), mỗi app một khóa theo `ComponentKey` (package + class + user serial). Không đụng `LabelCache` (cache vẫn chỉ giữ tên gốc), không đụng cache icon, không đụng Room, không đụng file độc quyền nào. Màn 5.3 là một `SettingsScreen` mới đăng ký vào ô `RENAME_APPS` có sẵn; sửa tên bằng hộp thoại.

**Tech Stack:** Kotlin, Views/XML, Hilt, DataStore Preferences (đã có, thêm **một instance thứ hai** cho file khác). Không dependency mới.

**Spec:** `docs/Figma-Draft.md` mục 3 (5.3), 5 (FD2, FD10), 6 (RN) + màn `v2-53-rename-app.png` / `ui-v2-text.txt` (chỉ trên máy chủ dự án) + `docs/plans/G2d-settings-hub.md` ("Công thức thêm màn/hàng") + `docs/lessons/icons-and-app-list.md`, `startup-performance.md` (LC), `search.md`. Không có mục TT# riêng trong `Launcher-Features.md`.

## Vì sao chọn thiết kế này

### Vấn đề cần giải (đọc code `main` @ b76f4bb, 2026-11-12)

- **Nhãn app đi qua nhiều tầng.** `LauncherAppsDataSource.loadApps` đọc nhãn (qua `LabelCache` LC, khóa `ComponentKey` + `sourceDir`) → `AppRepositoryImpl.loadSorted` sắp theo `Collator` → `AppRepository.apps` → `LayoutRepositoryImpl.assemble` (Home, dock, thư mục) / `GetCategoryGroupsUseCase` (App Library) / `SearchViewModel` (chỉ mục) / `hiddenApps` (Ứng dụng đã ẩn). Mọi chỗ đọc `AppItem.label` đều lấy từ `AppRepository.apps` (grep `\.label`, 2026-11-12: không có chỗ nào đọc nhãn thẳng từ hệ thống ngoài `LauncherAppsDataSource`).
- **Schema Room đang khóa** (chưa cấp). Bảng `launcher_items` là *vị trí* của app, không phải *danh tính* của app: app đang ẩn không có hàng ở đó, app trong dock/thư mục/lưới là các hàng khác nhau. Tên thuộc về **component**, không thuộc về vị trí.
- **`LabelCache` (LC) là cache**: header ghi `locale` + `Build.FINGERPRINT`, khác một trong hai thì **bỏ cả file** (`LabelCache.load`), mục có `stamp` (sourceDir) cũ thì bỏ từng mục. Dữ liệu người dùng tự đặt không được nằm ở đó. Bài học LC: "khóa luôn từ hệ thống, cache chỉ trả lời nhãn".
- **Đường khởi động đã tối ưu** (`lessons/startup-performance.md`: đọc nhãn chiếm ~84% thời gian tới lúc có lưới trên Oppo khi không cache; LC giảm còn ~1,6 s ở cache ấm). Lớp phủ mới nằm trên đường này nên phải rẻ và **không được làm `apps` phát hai lần** (nhãn gốc rồi nhãn riêng: Home sẽ nháy).
- **Home đã tự làm mới theo `apps`**: `HomeViewModel` thu `layoutRepository.pages` suốt đời ViewModel; `HomePage` là data class chứa `PlacedApp.app` nên đổi nhãn → `DiffUtil` coi trang đã đổi → `IconGridView.setItems` bind lại; `DockView.setItems` so `items == this.items` (cũng so cả `AppItem`) nên cũng bind lại; `FolderPanelView` so `shown == this.apps`; `CategoryGroupPanelView.Diff.areContentsTheSame = old == new` (đọc code). Khóa bitmap icon là `AppIconKey` (key + stamp), **không chứa nhãn** nên đổi nhãn không làm nháy icon.
- **A3 (FX3)**: khi app cập nhật mà đổi tên activity khởi động, `ComponentKey` đổi (cùng package, class khác). `LayoutReconciler` xử lý bằng re-point hàng mồ côi. Tên tùy chỉnh cũng phải trả lời câu hỏi này (xem "Khóa của tên").
- **Cài đặt hiện có chỉ có kiểu hàng với chuỗi tài nguyên** (`SettingsRow.Value.title` là `@StringRes Int`), nên một danh sách app (tên là chuỗi động) cần một kiểu hàng mới. Hộp thoại là cách mọi màn con khác đang dùng (`IconSetSettingsScreen`, `WallpaperSettingsScreen`).

### Chỗ lưu tên: các phương án đã xét

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Bảng Room `app_labels(packageName, className, userSerial, label)` | Entity + DAO + migration v1→v2; dọn cùng transaction với `removePackages`/`removeUser` | Flow có sẵn; xóa mồ côi nằm trong transaction đã có | **Cần khóa schema** (chưa cấp, `MultiAgent-Workflow.md` mục 5.3) và migration không có test tự động (AGENTS.md: kiểm tay cài bản cũ → bản mới); đụng `data/layout/db/*` (độc quyền) và `LayoutReconciler` (độc quyền) để dọn mồ côi cùng transaction; chặn các nhánh khác cần khóa | Không |
| B. Cột `label` nullable trên `launcher_items` | Thêm cột | Không thêm bảng | Sai mô hình: app ẩn không có hàng; app xuất hiện ở nhiều hàng (không, mỗi app một hàng, nhưng hàng bị xóa khi gỡ/ẩn rồi quay lại thì mất tên); cũng đổi schema | Không |
| C. Cùng DataStore `launcher_settings`, repository riêng + file khóa riêng, mỗi app một khóa | Mẫu `SearchPrefsKeys` (M8d) và `WeatherPreferencesRepository` + `WeatherPrefsKeys` của nhánh WU (PR #50, điều phối gợi ý 2026-11-13): không đụng `SettingsKeys`/`SettingsRepositoryImpl`, không đổi DI | Không đổi `DataStoreModule`; tránh xung đột với các nhánh khác ở file dùng chung; ít code nhất | Phần "repository + khóa riêng" **ta vẫn lấy** (xem E). Chỉ phần **dùng chung một file** là vấn đề: mỗi lần đổi tên **ghi lại cả file settings và phát lại flow `settings` cho mọi bên thu** (`SettingsRepositoryImpl.settings` không `distinctUntilChanged`; `.distinctUntilChanged()` của repository riêng chỉ bảo vệ chính nó, không bảo vệ các bên thu `settings`; chính LC đã loại DataStore chung vì lý do này); khóa động mỗi app làm ô settings phình theo số app; file settings hỏng (mất cả tên lẫn cài đặt) hoặc handler hỏng-thay-thế nếu thêm sẽ xóa cả cài đặt | Không (nhưng là phương án gần nhất) |
| D. File JSON + `AtomicFile` riêng (như `LabelCache`) | Tự đọc/ghi, `MutableStateFlow` | Không dính DataStore | Tự viết lại điều DataStore đã làm (ghi nguyên tử, tuần tự hóa ghi, Flow); đặt vào `noBackupFilesDir` thì **không** sao lưu tên người dùng tự đặt | Không |
| **E. Repository riêng + khóa riêng (`AppLabelKeys`, như WU) trên một DataStore Preferences riêng, file `app_labels`, mỗi app một khóa chuỗi** | Một `DataStore<Preferences>` thứ hai (`preferencesDataStore(name = "app_labels")`, `@Named("app_labels")`), khóa `l:<package>/<class>#<serial>`, giá trị = tên | Ghi nguyên tử và Flow có sẵn; **không phát lại `settings`**, không bị cài đặt khác làm phân tích lại; `ReplaceFileCorruptionHandler` riêng: file tên hỏng chỉ mất tên, không mất cài đặt; xóa một file là về mặc định; theo Auto Backup mặc định (tên người dùng đặt nên được khôi phục khi đổi máy); không đổi Room, không file độc quyền | Thêm một file DataStore và một khóa Hilt; một lần đổi tên vẫn ghi cả file nhỏ (vài trăm byte) | **Chọn** |

**Vì sao E (nhãn nguồn):** (1) *đọc code*: schema khóa nên A/B loại; (2) *đọc code + LC D1*: dữ liệu người dùng không đặt trong cache bị xóa theo locale/fingerprint; (3) *đọc code* `SettingsRepositoryImpl`/`HomeViewModel`: C làm mọi bên thu `settings` chạy lại cho mỗi lần đổi tên, mà tách file thì không; (4) *mẫu của WU*: giữ nguyên phần tốt của mẫu (repository riêng + file khóa riêng, không sửa `SettingsKeys`/`SettingsRepositoryImpl`) và chỉ khác ở việc dùng file riêng; chi phí là một provider thêm trong `DataStoreModule` (file dùng chung, chỉ thêm, không sửa provider cũ, không ảnh hưởng nhánh khác); (5) *suy luận chưa kiểm chứng*: kích thước (vài chục tên × ~60 byte) quá nhỏ để file riêng thành gánh nặng.

### Nơi áp tên: các phương án đã xét

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Overlay trong `AppRepositoryImpl.apps`** | `combine(danh sách app gốc, tên riêng)`, thay `label`, giữ `originalLabel`, sắp lại theo tên hiển thị | Một chỗ; mọi nơi hiển thị (Home, dock, thư mục, Library, tìm kiếm, ẩn, toast) tự có; không sửa `HomeActivity`/`HomeViewModel`/`HomeUiState`/view nào; `LabelCache` và cache icon nguyên vẹn | `apps` phát lại cho mọi bên thu mỗi lần đổi tên: `LayoutRepositoryImpl` chạy lại `placeNewApps` (idempotent, đọc code) và dựng lại bố cục. Chấp nhận (đổi tên hiếm); sắp lại ~345 nhãn ≈ chục ms (đo P1: sắp 25 ms, debug) | **Chọn** |
| B. Áp ở từng chỗ hiển thị | Mỗi view/ViewModel tra bảng tên | Không làm `apps` phát lại | 8+ chỗ phải nhớ (Home, dock, thư mục, Library, tìm kiếm, ẩn, trợ năng, toast); sót một chỗ là tên hiện lệch; `HomeViewModel`/`HomeActivity` thuộc file độc quyền | Không |
| C. Ghi tên vào `LabelCache` | Sửa mục cache | Không thêm nguồn | Cache bị xóa khi đổi locale/fingerprint hoặc app cập nhật (mất tên người dùng); trộn dữ liệu người dùng với cache; đụng LC đã chốt (D1, D5, D7) | Không |
| D. Overlay ở `LayoutRepositoryImpl.assemble` | Thay nhãn khi ghép bố cục | Không đổi `apps` | Chỉ phủ Home/dock/thư mục; tìm kiếm, Library, ẩn vẫn đọc `apps` nên phải làm lần nữa (thành phương án B) | Không |

**Đánh đổi chấp nhận:** (1) `apps` phát lại sau mỗi lần đổi tên (đổi tên không phải thao tác nóng; không ảnh hưởng cold start vì lúc đó chỉ có thêm một lần đọc DataStore, cần **đo**, xem Task 1). (2) Khi chờ lần đọc đầu của `app_labels`, `apps` chưa phát (cần phát một lần đúng ngay từ đầu, tránh nháy); lần đọc này chạy song song với `getActivityList`/đọc nhãn nên suy luận là không thêm thời gian, **chưa đo**. (3) Mất tên khi gỡ thật rồi cài lại (xem "Vòng đời của tên"). Xét lại nếu số đo Task 1 cho thấy chậm khởi động, hoặc nếu sau này cần tên tùy chỉnh cho thứ ngoài app (thư mục đã có `RenameFolderUseCase` riêng).

### Khóa của tên (mục brief: component + user serial, cập nhật, đổi tên activity, gỡ rồi cài lại)

Khóa = `ComponentKey(packageName, className, userSerial)`, đúng khóa mà Room, cache icon và cache nhãn đang dùng; chuỗi lưu: `l:<package>/<class>#<serial>` (package không chứa `/`; tách ở `/` đầu tiên và `#` cuối cùng; khóa không phân tích được thì bỏ qua, không crash).

- **`userSerial` nằm trong khóa** (FX5): "Chrome" của hồ sơ công việc và "Chrome" của user chính là hai app, mỗi cái một tên. Hàng trong màn 5.3 của app hồ sơ có icon huy hiệu (Task 5) và dòng phụ phân biệt (từ `a11yLabel`).
- **App cập nhật** (cùng component): tên riêng giữ nguyên; `originalLabel` tự đổi theo nhãn mới của hệ thống (LC vẫn làm việc của nó). Đổi ngôn ngữ: tên riêng giữ, tên gốc đổi theo locale.
- **Đổi tên activity khi cập nhật (A3):** khóa cũ không còn trong `apps` → tên riêng **không còn áp** (app hiện lại tên gốc), mục cũ nằm lại trong file (mồ côi, vô hại). Đây là **hạn chế đã biết của v1**. Sửa được bằng re-point giống `LayoutReconciler`, nhưng ghép chéo có thể gán nhầm tên cho activity khác cùng package (FX3 đã chấp nhận rủi ro tương tự cho vị trí; với tên thì nhầm lộ rõ hơn), nên **không làm** (chủ dự án/điều phối chốt Q4, 2026-11-13).
- **Gỡ rồi cài lại:** (đã chốt Q5, 2026-11-13) gỡ **thật** thì xóa tên (giống `hidden_apps` và hàng bố cục: cài lại là app mới tinh). Xóa ở hai chỗ, đều có chốt an toàn `isPackageInstalled == false` (không xóa oan app bị tắt/khóa/thẻ nhớ tháo, đúng nguyên tắc Q11 của FX3): (a) sự kiện `PackageEvent.Type.REMOVED` / `USER_REMOVED` lúc launcher chạy; (b) quét một lần mỗi tiến trình sau lần nạp đầu cho mục mà khóa không còn trong `apps` (gỡ lúc launcher không chạy). Không xóa theo "vắng mặt khỏi danh sách".

### Chọn nhãn nào để tìm kiếm (mục brief)

Khớp **cả hai**, tên riêng trước: truyền `label = tên hiển thị` và `keywords = [tên gốc]` cho `SearchIndex.Candidate` (cơ chế `keywords` có sẵn: khớp nhãn xếp hạng 0–2, khớp từ khóa hạng 3–4, nên "web" tìm ra app đã đổi thành "Web" trước, còn gõ tên gốc "chrome" vẫn tìm ra nó nhưng sau). Lý do: người dùng quen tên gốc vẫn tìm được; không phải sửa `SearchIndex`/`AppSearchMatcher` (chuẩn hóa không dấu vẫn do một hàm `normalize` cho cả hai, đọc code). Chỉ làm khi tên khác tên gốc để chỉ mục không nở ra cho 345 app.

### Làm mới Home không khởi động lại (mục brief)

Không cần sửa file Home nào. Chuỗi (đọc code): `AppLabelRepository.customLabels` đổi → `combine` trong `AppRepositoryImpl.apps` phát danh sách mới → `LayoutRepositoryImpl.layout` (combine `dbSnapshots()` và `apps`) assemble lại → `HomeViewModel` đang thu `layoutRepository.pages`/`dock` trong `viewModelScope` (không phụ thuộc Activity) → `HomeUiState` → `HomePagerAdapter` (DiffUtil), `DockView.setItems`, `FolderPanelView`, `AppLibraryController` bind lại. Màn Cài đặt mở từ nút "Cài đặt" của edit mode nên Home đang dừng nhưng ViewModel vẫn thu; quay lại thì state đã mới. `HomeViewModel` có cơ chế `pendingMoves` bỏ emission DB lúc đang ghi kéo-thả, nhưng không có thao tác kéo khi đang ở Cài đặt, và nó luôn nhớ bản mới nhất (`latestDbPages`) nên không mất. Phải xác nhận trên máy (Task 2).

### Có cho đổi tên từ menu nhấn giữ trên Home không (mục brief)

Mặc định **không** (chỉ màn Cài đặt, đúng bản nháp). Menu nhấn giữ hiện nay không hiển thị tên app (đọc code `IconActionMenu`: chỉ nhãn hành động và `shortcut.shortLabel`). Thêm mục "Đổi tên" vào menu cần `IconMenuAction` (mới), `IconMenuController`/`IconActionMenu` (không độc quyền) **và** chỗ xử lý hành động trong `HomeViewModel`/`HomeActivity` (độc quyền) cùng một hộp thoại trên Home. Câu hỏi cho chủ dự án: Q1.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Làm màn 5.3 theo bản nháp; **không** dựng phong cách hình ảnh của bản nháp, giữ kiểu hàng/danh sách hiện tại | Chủ dự án, 2026-11-12 (FD2) |
| 2 | Slot `SettingsScreenId.RENAME_APPS` (đã có trong enum, bảng trang chủ và chuỗi `settings_hub_rename_apps`); đăng ký bằng thay MỘT dòng `// 5.3 RENAME_APPS (RN)` trong `SettingsScreens` | G2d, công thức thêm màn |
| 3 | Lưu tên: `AppLabelRepository` + `AppLabelKeys` riêng (mẫu WU/`SearchPrefsKeys`, không đụng `SettingsKeys`) trên DataStore riêng `app_labels`, mỗi app một khóa (phương án E) | Agent đề xuất 2026-11-12 (so sánh ở trên); điều phối gợi ý mẫu WU 2026-11-13; **điều phối duyệt 2026-11-13** (file `app_labels` riêng được chấp nhận) |
| 4 | Áp tên: overlay trong `AppRepositoryImpl.apps`; `AppItem.label` = tên hiển thị, thêm `AppItem.originalLabel` (mặc định = `label`) | Agent đề xuất; điều phối duyệt 2026-11-13 |
| 5 | Thứ tự sắp xếp toàn app (Library, tìm kiếm a–z, danh sách ẩn) theo **tên hiển thị**; riêng danh sách ở màn 5.3 sắp theo **tên gốc** để hàng không nhảy chỗ sau khi lưu | Agent đề xuất |
| 6 | Tìm kiếm khớp cả tên riêng và tên gốc (tên riêng xếp trước) | Agent đề xuất; Q3 duyệt 2026-11-13 |
| 7 | `LabelCache` và cache icon **không đổi** (không tăng `VERSION`, không đổi khóa) | Đọc code: tên riêng nằm ngoài cả hai |
| 8 | Sửa tên bằng hộp thoại (ô nhập + Lưu / Khôi phục / Hủy), không mở rộng tại chỗ trong danh sách | Agent đề xuất; Q2 duyệt 2026-11-13 |
| 9 | Chuẩn hóa đầu vào: bỏ khoảng trắng đầu/cuối, gộp khoảng trắng và xuống dòng thành một dấu cách, tối đa 30 ký tự, rỗng hoặc bằng tên gốc = khôi phục (xóa khóa) | Agent đề xuất; Q6 duyệt 2026-11-13 |
| 10 | Màn 5.3 liệt kê **mọi app** của `AppRepository.apps` (kể cả app đang ẩn) | Agent đề xuất |
| 11 | Gỡ thật thì xóa tên (có chốt `isPackageInstalled`); đổi tên activity (A3) v1 không giữ tên | Agent đề xuất; Q4, Q5 duyệt 2026-11-13 |
| 12 | Tên nằm trong file Auto Backup mặc định (không đặt vào `noBackupFilesDir`) | Agent đề xuất; Q7 duyệt 2026-11-13 |

## Câu hỏi cho chủ dự án

**Đã chốt 2026-11-13 (điều phối, theo ủy quyền của chủ dự án): Q1–Q7 đều theo đề xuất.** Q4 = không giữ tên khi đổi activity, nên **bỏ Task 6**.

- **Q1 — Có thêm "Đổi tên" vào menu nhấn giữ trên Home không?** Đề xuất: **không**, chỉ màn Cài đặt (đúng bản nháp). Làm thêm cần sửa file độc quyền `HomeViewModel`/`HomeActivity`, loại khỏi nhánh này.
- **Q2 — Sửa tên "tại chỗ" như bản nháp (hàng mở rộng ra ô nhập + hai nút) hay hộp thoại?** Đề xuất: **hộp thoại**. Hàng mở rộng trong `RecyclerView` dễ vỡ: mỗi lần danh sách phát lại (app cài/gỡ, đổi tên) sẽ bind lại và mất ô nhập/tiêu điểm/bàn phím, và dự án chưa có hàng nào chứa ô nhập. Hộp thoại là cách mọi màn con hiện nay đang chọn giá trị. Mất: không "tại chỗ" như bản nháp.
- **Q3 — Tìm kiếm khớp tên riêng, tên gốc, hay cả hai?** Đề xuất: **cả hai**, tên riêng xếp trước (một dòng `keywords`).
- **Q4 — App cập nhật mà đổi tên activity khởi động (A3) thì có giữ tên riêng không?** Đề xuất: **không giữ ở v1** (app hiện tên gốc, hạn chế được ghi rõ); Không làm re-point (Task 6 đã bỏ).
- **Q5 — Gỡ app thật rồi cài lại có giữ tên riêng không?** Đề xuất: **không** (giống vị trí và trạng thái ẩn: cài lại là app mới). Giữ vĩnh viễn thì file tích mục mồ côi và có thể gán lại tên cho một app cài lại sau nhiều tháng.
- **Q6 — Giới hạn 30 ký tự; chưa làm ô tìm trong danh sách và nút "Khôi phục tất cả" (bản nháp không có).** Đề xuất: giữ 30; hai thứ kia ghi để sau (danh sách ~345 app trên Oppo cuộn dài; ô tìm là việc nhỏ nếu cần).
- **Q7 — Tên riêng có được Auto Backup sao lưu/khôi phục không?** Đề xuất: **có** (mặc định của DataStore; người đổi máy giữ được tên). Tên là dữ liệu người dùng tự gõ, sao lưu vào tài khoản Google của chính họ như các cài đặt khác. Khóa user hồ sơ công việc có thể không khớp sau khôi phục: mục đó tự bị quét như mồ côi.

## Ngoài phạm vi / ghi để sau

- Đổi tên thư mục: đã có (`RenameFolderUseCase`), không đổi.
- Đổi icon (5.2, nhánh CI), ẩn app (5.1): không đụng.
- Ô tìm kiếm trong màn 5.3, "Khôi phục tất cả", nhập tên bằng giọng nói: ghi để sau (Q6).
- Sửa tên ngay trên Home / từ menu nhấn giữ (Q1).
- Tên riêng cho shortcut của app (`shortLabel`), cho widget, cho nhóm Library: ngoài phạm vi.
- Re-point tên khi app đổi activity (Q4): bỏ, hạn chế đã biết.

## Vùng file

Đường dẫn Kotlin tính từ `app/src/main/java/gambi/global/os26launcher/`, tài nguyên từ `app/src/main/`.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/AppItem.kt` | Sửa: thêm trường **cuối** `originalLabel: String = label` | Không (mọi nơi dựng `AppItem` đang dùng tên tham số: chỉ có một, `LauncherAppsDataSource.kt:96`) |
| `domain/repository/AppLabelRepository.kt` | Tạo | Không |
| `domain/usecase/RenameAppUseCase.kt`, `RestoreAppLabelUseCase.kt` | Tạo | Không |
| `data/apps/AppLabelRepositoryImpl.kt`, `data/apps/AppLabelKeys.kt` | Tạo (không đụng `SettingsKeys.kt`/`SettingsRepositoryImpl.kt`) | Không |
| `data/apps/AppRepositoryImpl.kt` | Sửa: tách `loadSorted` thành nạp + overlay + sắp; dọn tên mồ côi | Không |
| `data/apps/LauncherAppsDataSource.kt` | Sửa nhỏ: hàm công khai tính lại `a11yLabel` cho app hồ sơ từ tên riêng | Không |
| `di/DataStoreModule.kt` | Sửa (chỉ thêm, provider cũ giữ nguyên): thêm một provider `@Named("app_labels")` (delegate ở cấp file) | Không |
| `di/RepositoryModule.kt` | Sửa: một `@Binds` | Không |
| `ui/search/SearchViewModel.kt` | Sửa một dòng (dòng 155): thêm `keywords` | Không (có thể trùng nhánh HT nếu HT sửa file này: HT khai `ui/home`, hàng Cài đặt tìm kiếm; nếu trùng thì chỉ là một dòng, điều phối rebase) |
| `ui/settings/RenameAppsSettingsScreen.kt`, `RenameAppsViewModel.kt` | Tạo | Không |
| `ui/settings/SettingsScreen.kt` | Sửa: thay MỘT dòng giữ chỗ `// 5.3 RENAME_APPS (RN)`; Task 5 thêm thuộc tính tùy chọn nạp icon | **Dùng chung** (`ui/settings/*`) |
| `ui/settings/SettingsRow.kt`, `SettingsAdapter.kt` | Sửa: thêm kiểu hàng `AppName` (chỉ thêm) | **Dùng chung** (chỉ thêm; **ngoại lệ so với công thức "một file + một dòng"**: hàng tiêu đề là chuỗi động nên cần kiểu hàng mới) |
| `ui/settings/SettingsActivity.kt` | Sửa nhỏ: Task 2 một dòng (đưa `screen::onAppNameClicked` cho `SettingsAdapter`), Task 5 đưa bộ nạp icon của màn cho `SettingsAdapter` | **Dùng chung** |
| `res/layout/item_settings_app_name.xml`, `res/layout/dialog_rename_app.xml` | Tạo | Không |
| `res/values/strings.xml` | Sửa: **một khối mới ở cuối**, tiền tố `settings_rename_` | **Dùng chung** (khối cuối) |
| `docs/plans/RN-rename-apps.md`, `docs/knowledgements/RN-rename-apps.md` | Tạo | Không |
| **Không sửa:** `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `LayoutReconciler`, `data/layout/db/*`, `core/glass/*`, `DragHelper`, `DragDropTarget` (không cái nào cần); `LabelCache.kt`, `IconCache`, `IconDiskCache`, `IconLoader`; `AndroidManifest.xml`; mọi file `ui/home/view/*`; Room/`app/schemas/*` | — | — |

Không có file độc quyền nào trong diff. Khóa schema: không.

## Global Constraints

- Tầng `ui → domain ← data`: `AppLabelRepository` và use case ở `domain`; `AppLabelRepositoryImpl` ở `data/apps`; màn chỉ import `domain`/`ui`. Chuẩn hóa đầu vào ở use case (hàm thuần), không ở view.
- Mọi chữ hiển thị ở `strings.xml`, khối cuối, tiền tố `settings_rename_`.
- `preferencesDataStore` phải là delegate ở **cấp file** (AGENTS.md "Build và toolchain"); `ReplaceFileCorruptionHandler` cho file mới.
- **Lỗi đọc/ghi `app_labels` không bao giờ làm `apps` ngừng phát** (`ApplicationScope` không có handler, lỗi lọt ra làm sập process — bài học FX3/FX6): đọc lỗi `IOException` → bản đồ rỗng; ghi lỗi → báo cho người dùng qua `SettingsEvent`/Toast như `SearchSettingsViewModel`.
- `apps` chỉ phát **một lần đúng** ở lần nạp đầu (không phát nhãn gốc trước rồi nhãn riêng); đổi tên không kích hoạt nạp lại nhãn từ hệ thống (chỉ chạy lại overlay).
- Không `Log` chứa tên người dùng đặt hay nhãn app (bài học FX5 mục 11); log đo tạm đặt tag `RN`, gỡ trước commit (`grep` rỗng).
- Không Room, không dependency mới, không test, không đụng file độc quyền. Commit message tiếng Anh `<type>: <imperative>` ≤ 72 ký tự, không dấu tiếng Việt, kèm dòng `Co-Authored-By`. Chỉ `git add` theo đường dẫn tường minh.
- Build `gradlew.bat assembleDebug` xanh mới báo xong Task.

## Review Focus

1. **Một chỗ áp tên:** diff không có `label` nào bị thay ngoài `AppRepositoryImpl`; không file `ui/home/*`, `data/layout/*`, `LabelCache`, cache icon trong diff.
2. **Không nháy lúc khởi động:** `apps` đầu tiên đã có tên riêng (log đo tạm: số lần phát = như trước); độ trễ thêm của overlay ở cold start đã đo và ghi (Task 1).
3. **Khóa:** `userSerial` có trong khóa; app hồ sơ công việc có tên riêng độc lập với cùng package của user chính; khóa hỏng/lạ không crash.
4. **Lỗi lưu trữ không giết `apps`:** file `app_labels` hỏng/xóa → app vẫn chạy với tên gốc; ghi lỗi → Toast, danh sách dựng lại từ trạng thái thật.
5. **Làm mới Home:** đổi tên xong Back về Home thấy tên mới ở lưới, dock, thư mục đã mở, không khởi động lại; icon không nháy.
6. **Sắp xếp và tìm kiếm:** tên mới đứng đúng chỗ trong panel nhóm Library; tìm bằng tên riêng và bằng tên gốc đều ra app, tên riêng xếp trước; chuẩn hóa không dấu đúng cho tên riêng tiếng Việt ("Đi đâu đây" tìm bằng `di dau`).
7. **Trợ năng:** `contentDescription` của ô dùng tên riêng; app hồ sơ công việc đọc "<tên riêng> (Công việc)" (tính lại từ tên mới), không phải tên gốc.
8. **Dọn mồ côi an toàn:** gỡ app thật xóa mục; `pm disable-user` / tạm khóa **không** xóa mục; không xóa vì "vắng khỏi danh sách".
9. **Cache:** `app_label_cache.json` không có tên riêng (đọc file); tên gốc vẫn vào cache như trước; đổi locale vẫn làm tên gốc đổi, tên riêng giữ.
10. **Hộp thoại:** một hộp mỗi lúc, `dismiss()` ở `onDestroy` (gỡ listener trước), chạm đúp không mở hai hộp; không `Log` tên.
11. **Không sửa ngoài vùng**; `strings.xml` chỉ thêm ở cuối.

---

### Task 1: Lưu trữ tên + lớp phủ trong `AppRepositoryImpl` (chưa có UI)

**Bắt buộc kiểm tay:** không. Chưa có chỗ nhập tên; kiểm "không hồi quy" (khởi động, thứ tự) và đo độ trễ overlay. Nó là nền cho Task sau nhưng không dựa vào giả định chỉ máy thật trả lời (4.2 không áp dụng); số đo khởi động nằm trong bài kiểm cuối.
**Files:** `AppItem.kt`, `AppLabelRepository.kt`, `RenameAppUseCase.kt`, `RestoreAppLabelUseCase.kt`, `AppLabelRepositoryImpl.kt`, `AppRepositoryImpl.kt`, `LauncherAppsDataSource.kt`, `DataStoreModule.kt`, `RepositoryModule.kt`.
**Interfaces:** Consumes `DataStore<Preferences>` (instance mới), `LauncherAppsDataSource`. Produces `AppLabelRepository { val customLabels: Flow<Map<ComponentKey,String>>; suspend fun setCustomLabel(key, label: String?); suspend fun removePackage(pkg, userSerial); suspend fun removeUser(serial); suspend fun prune(...) }` (hình chính xác chốt khi code, theo Task 4), `AppItem.originalLabel`, `RenameAppUseCase(key, originalLabel, input)`, `RestoreAppLabelUseCase(key)`.

- [x] **Step 1:** `AppItem`: thêm `val originalLabel: String = label` ở **cuối** danh sách tham số, KDoc "tên hiển thị là `label`; đây là tên gốc của hệ thống (hoặc cache LC)". Không đổi chỗ dựng `AppItem` nào.
- [x] **Step 2:** `AppLabelRepository` (domain) và `AppLabelRepositoryImpl` (data/apps): `store.data.catch { IOException → emptyPreferences() }.map(parse).distinctUntilChanged()`; khóa chuỗi `l:<pkg>/<cls>#<serial>`; `setCustomLabel(key, null)` = `remove`. `DataStoreModule`: delegate cấp file `appLabelsDataStore` (`preferencesDataStore(name = "app_labels", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`), provider `@Named("app_labels")`; `RepositoryModule`: `@Binds`. Đọc `docs/lessons/settings-and-data.md` mục DataStore trước khi viết.
- [x] **Step 3:** use case (hàm thuần cho phần chuẩn hóa): trim, gộp khoảng trắng/xuống dòng/điều khiển thành một dấu cách, cắt 30 ký tự **không tách cặp surrogate**, rỗng hoặc (sau chuẩn hóa) bằng `originalLabel` → xóa khóa; ngược lại lưu. `RestoreAppLabelUseCase` = xóa khóa.
- [x] **Step 4:** `AppRepositoryImpl`: tách chuỗi hiện có thành `loaded` (nạp từ hệ thống, `loadSorted` đổi tên thành `loadBase`, **bỏ phép sắp ở cuối**, giữ nguyên mọi phần phụ: so stamp, dọn icon, `USER_REMOVED` giả) rồi `apps = combine(loaded, labelRepository.customLabels) { base, labels -> overlayAndSort(base, labels) }.flowOn(io).stateIn(...)` (cùng `WhileSubscribed(5_000)`, cùng giá trị đầu `emptyList()`). `overlayAndSort`: với mỗi app có tên riêng → `copy(label = riêng)` (giữ `originalLabel` = tên gốc), app hồ sơ có `a11yLabel` thì tính lại bằng hàm mới của data source (`getUserBadgedLabel(tên riêng, user)`, lỗi → giữ nguyên); rồi sắp bằng `Collator` PRIMARY theo `label` hiển thị. Tên riêng không nằm trong `loadApps`/`LabelCache.scheduleSave` (cache vẫn chỉ nhận tên gốc, vì phép phủ xảy ra sau).
- [x] **Step 5: Đo (log tạm, tag `RN`, gỡ trước commit):** ghi `SystemClock.uptimeMillis() - Process.getStartUptimeMillis()` tại (a) lúc `loaded` phát, (b) lúc `apps` phát lần đầu có dữ liệu thật; chênh (b)−(a) là chi phí overlay + lần đọc đầu của `app_labels`. Ghi cả số lần `apps` phát trong cold start (phải bằng bản `main`).
- [x] **Step 6: Kịch bản kiểm (Live 4, 87 app, bản debug; ghi rõ build):** xác nhận máy rảnh. (a) Cài, `am force-stop`, mở `am start -n gambi.global.os26launcher/.ui.home.HomeActivity`; Home, dock, thư mục, Library, tìm kiếm y như `main` (thứ tự và tên không đổi vì chưa có tên riêng); (b) 5 lần khởi động nguội: chênh (b)−(a) và số lần phát, ghi trung vị; (c) `adb shell run-as gambi.global.os26launcher ls files/datastore` thấy `launcher_settings.preferences_pb` và (sau lần đọc/ghi đầu) `app_labels.preferences_pb` hoặc chưa có file (DataStore tạo khi ghi lần đầu: chấp nhận cả hai); (d) logcat không có lỗi `RN`/`AppLabel`, không FATAL. **Lưu ý đã biết:** số `TotalTime` của bản debug không nói thay trải nghiệm người dùng (`lessons/startup-performance.md`); đây là phép đo **tương đối** trong cùng build.

  **Kết quả đo cold start (điều phối, Live 4, 2026-11-13, bản debug của `4059aa6`, md5 7829bc7e, 6 lần `force-stop` + `am start`; chỉ là số tương đối trong cùng một build; nhãn log: `labels#1` = lần đọc đầu file `app_labels`, `loaded#1` = lần nạp đầu từ hệ thống, `apps#1` = lần phát đầu của `AppRepository.apps`):**

  | lần | labels#1 (ms) | loaded#1 (ms) | apps#1 (ms) |
  |---|---|---|---|
  | 1 | 995 | 1068 | 1099 |
  | 2 | 942 | 1067 | 1082 |
  | 3 | 918 | 1037 | 1050 |
  | 4 | 914 | 1020 | 1043 |
  | 5 | 934 | 1065 | 1085 |
  | 6 | 923 | 1001 | 1021 |

  Mọi lần: `labels size=0`, `loaded size=81`, `apps size=81`, **một** lần phát `apps` mỗi lần khởi động (không có `apps#2`). `labels − loaded` = −73 đến −131 ms: đọc file xong **trước** khi nạp từ hệ thống, nên không thêm độ trễ. `apps − loaded` = 13–31 ms, đã gồm phép sắp `Collator` trước đây nằm trong `loadSorted`. **Kết luận (đo được):** không thấy chi phí khởi động khi chưa có tên riêng nào. **Chưa đo:** khi có nhiều tên riêng, và trên Oppo A3x (345 app). Log đo tạm đã gỡ bằng `git revert` (`2f1c419`), `grep RN-TMP` rỗng.

### Task 2: Màn "Thay đổi tên ứng dụng" + hộp thoại sửa tên

**Bắt buộc kiểm tay:** không (4.2 không áp dụng: không cử chỉ, không giả định máy). Kiểm Live 4 gộp vào bài cuối.
**Files:** `RenameAppsViewModel.kt`, `RenameAppsSettingsScreen.kt`, `SettingsRow.kt`, `SettingsAdapter.kt`, `SettingsScreen.kt` (một dòng), `item_settings_app_name.xml`, `dialog_rename_app.xml`, `strings.xml` (khối `settings_rename_`).
**Interfaces:** Consumes `AppRepository.apps`, `AppLabelRepository`, `RenameAppUseCase`, `RestoreAppLabelUseCase`. Produces màn đăng ký ở `SettingsScreenId.RENAME_APPS`.

- [x] **Step 1:** `SettingsRow.AppName(id, app: AppItem)` (kiểu hàng mới, chỉ thêm) + `ViewHolder` trong `SettingsAdapter` (bỏ qua nạp icon ở Task này): dòng chính = `app.label`; dòng phụ = `"Tên gốc: …"` khi `label != originalLabel`, nếu không thì (app hồ sơ) `a11yLabel`, nếu không thì ẩn. Nền nhóm bo góc dùng lại `groupBackground`. Mở rộng `getItemViewType`/`when` bằng nhánh mới. Hàng bấm gọi `onValueClicked`-tương đương (thêm callback `onAppClicked` hoặc dùng chung callback hiện có qua id: chốt khi code, **không** đổi chữ ký hiện có của hai callback cũ).
- [x] **Step 2:** `RenameAppsViewModel` (`@HiltViewModel`): `rows` = `appRepository.apps` sắp theo **tên gốc** (`Collator`, PRIMARY), `stateIn(WhileSubscribed(5_000))`; `rename(app, input)` / `restore(app)` chạy trong `viewModelScope`, lỗi (`Exception` không phải hủy) → kênh `failures` như `SearchSettingsViewModel`.
- [x] **Step 3:** `RenameAppsSettingsScreen` (mẫu `SearchSettingsScreen`): lấy VM bằng `ViewModelProvider(activity)[RenameAppsViewModel::class.java]`, `observe()` thu danh sách và `failures` rồi `ctx.rerender(...)`; `rows(state)` = một `Header` ngắn (mô tả: "Đặt tên riêng cho ứng dụng; tên gốc luôn khôi phục được") + `AppName` cho từng app; danh sách rỗng/đang nạp: trạng thái trống đơn giản (không hàng, không crash). Hộp thoại: `MaterialAlertDialogBuilder` với layout `dialog_rename_app.xml` (`TextInputLayout`/`EditText` một dòng, `maxLength=30`, đặt sẵn tên hiện tại và bôi đen, gợi ý "Tên gốc: …"), nút **Lưu** (tắt khi rỗng sau chuẩn hóa), **Khôi phục** (chỉ khi đã đổi), **Hủy**; một hộp mỗi lúc, `onDestroy` gỡ listener rồi `dismiss()`; bàn phím mở cùng hộp (`SOFT_INPUT_STATE_VISIBLE`); không `Log` tên.
- [x] **Step 4:** `SettingsScreens`: thay **đúng một dòng** `// 5.3 RENAME_APPS (RN)` bằng `SettingsScreenId.RENAME_APPS to ::RenameAppsSettingsScreen`. Mục ở trang chủ tự bật (hết mờ "Sắp có").
- [ ] **Step 5: Kịch bản kiểm (Live 4):** xác nhận máy rảnh; mở Home → vào chế độ sửa → Cài đặt → "Thay đổi tên ứng dụng" (hết mờ). (a) Danh sách đủ 87 app, thứ tự theo tên gốc, tiếng Việt đúng ("Đồng hồ" sau nhóm D). (b) Bấm một app, đổi tên thành "Đi đâu đây", Lưu: hàng đổi tên và hiện "Tên gốc: …", hàng **giữ nguyên chỗ**. (c) Back × 2 về Home: **tên mới trên lưới/dock/thư mục** không khởi động lại, icon không nháy (nếu app đó nằm trong thư mục, mở thư mục xem). (d) Mở lại hộp: Khôi phục → hàng mất dòng "Tên gốc", Home về tên cũ. (e) Nhập rỗng/toàn dấu cách: Lưu tắt; nhập đúng tên gốc rồi Lưu: coi như khôi phục; nhập 31+ ký tự: bị chặn ở 30; nhập có xuống dòng/emoji: không crash, một dòng. (f) Chạm đúp hàng: chỉ một hộp. (g) `am force-stop` rồi mở lại: tên còn. (h) Đo overlay: đổi tên 1 app, ghi thời gian từ Lưu tới lúc Home phát lại (log tạm `RN` ở `AppRepositoryImpl`).

### Task 3: Tìm kiếm khớp cả tên riêng và tên gốc

**Bắt buộc kiểm tay:** không.
**Files:** `ui/search/SearchViewModel.kt` (một dòng).
**Interfaces:** Consumes `AppItem.originalLabel`, `SearchIndex.Candidate.keywords`.

- [x] **Step 1:** dòng 155: `SearchIndex.Candidate(SearchResult.App(it), it.label, keywords = if (it.originalLabel != it.label) listOf(it.originalLabel) else emptyList())`. Không sửa `SearchIndex`/`AppSearchMatcher`.
- [ ] **Step 2: Kịch bản kiểm (Live 4):** đã đổi "Máy tính" (hoặc app có sẵn) thành "Zebra". (a) Tìm `zeb` → app ra, hiện tên "Zebra"; (b) tìm tên gốc (`may tinh`) → app vẫn ra, **sau** các app khớp nhãn trực tiếp nếu có; (c) đổi tên có dấu "Đi đâu đây", tìm `di dau` → ra; (d) mở lớp tìm kiếm từ trang Library, danh sách a–z (truy vấn rỗng, `showAll`) xếp theo tên hiển thị; (e) lỗi mở app (gỡ app nền rồi bấm kết quả) hiện toast với tên riêng.

### Task 4: Dọn tên mồ côi + nhãn trợ năng của app hồ sơ công việc

**Bắt buộc kiểm tay:** không (kiểm bằng `pm` do điều phối chạy được, như FX3/FX5).
**Files:** `AppLabelRepository(Impl).kt` (hàm dọn), `AppRepositoryImpl.kt`, `LauncherAppsDataSource.kt` (nếu chưa làm ở Task 1).
**Interfaces:** Consumes `AppRepository.packageEvents`, `isPackageInstalled`.

- [x] **Step 1:** trong `AppRepositoryImpl.init`, thu `packageEvents` (REMOVED, USER_REMOVED) trên `ApplicationScope`/`io`, **bọc `try/catch` từng sự kiện** (scope không có handler): REMOVED: nếu `!isPackageInstalled(pkg, serial)` thì xóa mọi khóa `(pkg, serial)`; USER_REMOVED: xóa mọi khóa của serial (không bao giờ user chính). Không đụng `data/layout/*`.
- [x] **Step 2:** quét một lần mỗi tiến trình ngay sau lần nạp đầu (chạy nền, không chặn phát `apps`): mọi khóa trong file mà `ComponentKey` không có trong danh sách app sống → gom theo `(pkg, serial)` → chỉ xóa nhóm mà `isPackageInstalled == false`. Khóa còn cài nhưng vắng (app bị tắt, khóa, thẻ nhớ tháo) **giữ nguyên**.
- [x] **Step 3:** xác minh `a11yLabel` của app hồ sơ có tên riêng được tính lại ở Task 1 Step 4 (không phải việc mới ở đây; Task này chỉ kiểm).
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy; công cụ app thử `dev.fx3.renametest` và hồ sơ tạm như ở `docs/report/FX3-layout-reconciler.md` "Công cụ kiểm" và `FX5-icons-labels.md` mục kiểm hồ sơ; không bấm mở app thử):** (a) đổi tên một app thử đã cài → `pm uninstall` → mục biến mất khỏi `app_labels` (đọc bằng `run-as`, chỉ cần số khóa, không in tên) → cài lại → tên gốc. (b) đổi tên app rồi `pm disable-user` → mục **còn**; `pm enable` → tên riêng hiện lại. (c) kill launcher giữa lúc gỡ (`force-stop`, `pm uninstall`, mở lại): quét đầu tiến trình xóa mục. (d) hồ sơ công việc (`pm create-user --profileOf 0 --managed`): đổi tên "Chrome" của hồ sơ **không** đổi "Chrome" của user chính; `uiautomator dump` ô hồ sơ có content-desc "<tên riêng> làm việc/(Công việc)" (theo chuỗi hệ thống dịch); `pm remove-user` → mục của hồ sơ bị xóa. Nhớ gỡ user tạm. (e) mở màn 5.3: hàng hồ sơ phân biệt được với hàng user chính.

### Task 5: Icon trong danh sách màn 5.3

**Bắt buộc kiểm tay:** không.
**Files:** `SettingsAdapter.kt`, `SettingsScreen.kt` (thêm thuộc tính tùy chọn), `SettingsActivity.kt` (đưa bộ nạp cho adapter), `item_settings_app_name.xml`, `RenameAppsViewModel.kt` (hàm nạp icon qua `AppRepository.icon`).
**Interfaces:** Consumes `AppRepository.icon(key, sizePx)`.

- [x] **Step 1:** theo mẫu `AppLibraryAdapter` (đã có, chống bẫy RecyclerView "icon nhảy sai dòng": mỗi holder giữ một `Job`, hủy ở `onBindViewHolder`/`onViewRecycled`; `boundKey` + `boundStamp` để cập nhật APK vẫn nạp lại): thêm `ImageView` vào `item_settings_app_name.xml`; adapter nhận `loadIcon` và `CoroutineScope` tùy chọn (null = không icon, màn khác không đổi). Màn khai báo bộ nạp qua thuộc tính tùy chọn của `SettingsScreen` (mặc định null).
- [ ] **Step 2: Kịch bản kiểm (Live 4):** (a) icon hiện đúng cho từng hàng khi cuộn nhanh (không nhảy sai dòng); (b) app hồ sơ có huy hiệu; (c) các màn Cài đặt khác nhìn y như trước (không icon, không sai lệch layout); (d) bộ nhớ: cuộn hết danh sách 87 app, `dumpsys meminfo` không tăng bất thường (icon đi qua `IconCache` chung).

### Task 6: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không.
**Files:** `docs/knowledgements/RN-rename-apps.md`, plan (nhật ký, bảng bằng chứng, đề xuất tài liệu chung).

- [x] **Step 1:** knowledgement theo mẫu mục 8 `MultiAgent-Workflow.md`, gồm một sơ đồ Mermaid `sequenceDiagram` cho chuỗi "Lưu tên → `apps` → Home/Library/tìm kiếm" và một `flowchart` cho vòng đời tên (đổi, khôi phục, gỡ, hồ sơ). Bài học cho AGENTS.md (chủ đề `icons-and-app-list`, `startup-performance`, `settings-and-data`).
- [x] **Step 2:** điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung"; `grep` log tạm `RN` rỗng; rà diff không có file ngoài vùng.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Oppo A3x không cần). **Xác nhận máy rảnh trước khi cài** (máy dùng chung với việc khác của chủ dự án). **Cài:** nhánh `feature/rn-rename-apps` tại commit đầu nhánh lúc kiểm (`git rev-parse --short HEAD` trong worktree), `gradlew.bat installDebug`; ghi nhánh@commit vào báo cáo. **Mở Home:** `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity` (Home mặc định của Live 4 là launcher gốc). **Chuẩn bị dữ liệu:** một thư mục có ít nhất hai app; một app trong dock; một app đã ẩn (để kiểm mục 13); APK thử FX3 `dev.fx3.renametest` v1 cho cụm E (dựng theo "Công cụ kiểm" của `docs/report/FX3-layout-reconciler.md`, cài `adb install -r`, **không bấm mở** vì không có code). Báo kết quả theo **số mục** (đạt, hoặc số mục + mô tả/log). Các mục chưa ai chạy được đánh dấu "chưa kiểm" trong bảng bằng chứng bên dưới cho tới khi có kết quả.

**A. Màn 5.3 và hộp thoại (Task 2, 5)**
1. Home → vào chế độ sửa → Cài đặt: hàng "Thay đổi tên ứng dụng" **không còn mờ** và không còn chữ "Sắp có"; bấm vào mở màn.
2. Danh sách có đủ app (kể cả app đang ẩn), **mỗi hàng có icon**, sắp theo tên gốc, tiếng Việt đúng ("Đồng hồ" sau nhóm D).
3. Cuộn nhanh lên xuống: icon không nhảy sai hàng, không trống kéo dài.
4. Bấm một app: hộp thoại có ô nhập đã điền tên hiện tại và bôi đen, bàn phím mở, dòng "Tên gốc: …", bộ đếm ký tự; app chưa đổi tên **không có** nút Khôi phục.
5. Nhập "Đi đâu đây" rồi Lưu: hàng hiện tên mới và dòng phụ "Tên gốc: …", hàng **giữ nguyên chỗ**.
6. Bấm lại hàng đó: có nút Khôi phục; bấm Khôi phục: về tên gốc, mất dòng phụ.
7. Biên: xóa hết hoặc toàn dấu cách thì Lưu tắt; nhập đúng tên gốc rồi Lưu coi như khôi phục; gõ quá 30 ký tự bị chặn ở 30; dán nhiều dòng hoặc emoji thành một dòng, không crash; "Xong" trên bàn phím = Lưu; chạm đúp một hàng chỉ mở một hộp; Hủy hoặc chạm ra ngoài thì không đổi gì.
8. (Nếu có app tên gốc dài hơn 30 ký tự) mở hộp rồi bấm Lưu ngay khi chưa sửa: **không** tạo tên riêng.

**B. Home làm mới không khởi động lại (Task 1, 2)**
9. Đổi tên ba app: một ở lưới, một ở dock, một trong thư mục. Back về Home: cả ba hiện tên mới ngay, **không khởi động lại**, icon không nháy; mở thư mục thấy tên mới.
10. Chế độ sửa vẫn dùng bình thường với app đã đổi tên: kéo-thả, ẩn bằng dấu "−", bỏ ẩn.

**C. Library, tìm kiếm, Ứng dụng đã ẩn (Task 1, 3)**
11. Mở App Library, vào panel nhóm chứa app đã đổi tên: hiện tên mới và đứng đúng chỗ theo tên mới.
12. Tìm kiếm: gõ tên riêng ra app; gõ tên gốc vẫn ra app (sau các kết quả khớp trực tiếp); gõ không dấu `di dau` ra "Đi đâu đây"; mở tìm kiếm từ trang Library với ô trống: danh sách a–z theo tên hiển thị.
13. Ẩn một app đã đổi tên: màn "Ứng dụng đã ẩn" hiện tên riêng; bỏ ẩn: app về lại Home với tên riêng.

**D. Bền và hồi quy cache (Task 1, 4)**
14. `adb shell am force-stop gambi.global.os26launcher`, mở lại: tên riêng còn ở mọi nơi. File cache nhãn không chứa tên riêng: `adb shell "run-as gambi.global.os26launcher grep -c <chuỗi tên riêng> no_backup/app_label_cache.json"` trả 0.
15. Đổi cỡ lưới và bật/tắt "hiện nhãn" trong Cài đặt chung: tên riêng còn.
16. (Tùy chọn) đổi ngôn ngữ hệ thống sang ngôn ngữ đã có gói: tên riêng giữ; dòng phụ "Tên gốc" đổi theo ngôn ngữ với app có nhãn khác nhau giữa các ngôn ngữ. Trả lại ngôn ngữ sau khi kiểm.

**E. Gỡ, tắt, cài lại với APK thử FX3 (Task 4; điều phối chạy hoặc chủ dự án chạy theo lệnh; PowerShell)**
Đặt `$P = "gambi.global.os26launcher"`, `$T = "dev.fx3.renametest"`, `$W` = thư mục làm việc. Đếm số mục tên riêng **không in tên**:
```powershell
function Count-Labels { cmd /c "adb exec-out run-as $P cat files/datastore/app_labels.preferences_pb > $W\labels.pb"; python -I -c "import re,sys;print(len(re.findall(rb'l:[^/]+/[^#]+#\d+',open(sys.argv[1],'rb').read())))" "$W\labels.pb" }
```
17. Cài v1 (`adb install -r $W\fx3-rename-v1.apk`), mở Home: app "FX3 Rename" có trên Home. Đổi tên thành "Thử nghiệm" ở màn 5.3. `Count-Labels` tăng đúng 1.
18. `adb shell pm disable-user --user 0 $T`: app biến khỏi Home, `Count-Labels` **không đổi**. `adb shell pm enable $T`: app trở lại với tên "Thử nghiệm".
19. `adb shell pm uninstall $T` khi launcher đang chạy: app biến, `Count-Labels` **giảm 1**. Cài lại v1: app hiện tên gốc "FX3 Rename" (không giữ tên riêng, quyết định Q5).
20. Đổi tên lại thành "Thử nghiệm". `adb shell am force-stop $P`, rồi `adb shell pm uninstall $T`, rồi mở Home: app không còn, `Count-Labels` giảm 1 (quét đầu tiến trình).
21. (Hạn chế v1 đã biết, A3) cài lại v1, đổi tên, rồi `adb install -r $W\fx3-rename-v2.apk` (activity đổi `.OldMain` thành `.NewMain`): app hiện lại **tên gốc** (đúng hạn chế đã ghi, không phải lỗi) và `Count-Labels` chưa giảm (mục mồ côi nằm lại). Sau đó `pm uninstall $T`: `Count-Labels` giảm (sự kiện gỡ xóa mọi mục của package). Gỡ APK thử khi xong.

**F. Tùy chọn: hồ sơ công việc tạm (cần dựng hồ sơ tạm bằng Test DPC của Google, đã được chủ dự án cho phép ở FX5; FX5 cũng từng dùng `pm create-user --profileOf 0 --managed Work` chạy được trên Live 4). Tạo hồ sơ làm launcher đặt hàng chục icon vào bố cục thật, nên sao lưu và khôi phục DB trước theo FX3/FX5; gỡ hồ sơ và Test DPC sau khi kiểm.**
22. Màn 5.3 có hai hàng cùng tên (cá nhân và công việc) và phân biệt được bằng huy hiệu trên icon và dòng phụ.
23. Đổi tên "Chrome" của hồ sơ công việc: "Chrome" của user chính **không đổi** (và ngược lại).
24. `uiautomator dump` ô hồ sơ trên Home: content-desc là tên riêng kèm hậu tố công việc do hệ thống dịch (không phải tên gốc).
25. Xóa hồ sơ: số mục tên riêng của hồ sơ về 0 (`Count-Labels` giảm tương ứng); tên của user chính còn nguyên.

**G. Trợ năng (nếu có TalkBack)**
26. TalkBack đọc tên riêng ở ô Home và ở hàng danh sách màn 5.3.

**H. Hồi quy ngắn**
27. Kéo-thả icon và thư mục ở chế độ sửa; ẩn/bỏ ẩn app; cài/gỡ một app khác; chọn gói icon; mở app từ Home và từ tìm kiếm rồi kiểm gợi ý "Thường dùng"; các màn Cài đặt khác nhìn như trước.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** (chưa kiểm tay; điền khi chủ dự án chạy)

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | — | Viết plan | 249c4cc | |
| 2026-11-13 | 1 | Lưu trữ + overlay: `AppItem.originalLabel`, `AppLabelRepository`/`AppLabelRepositoryImpl`/`AppLabelKeys` (file `app_labels`, mỗi app một khóa), `RenameAppUseCase`/`RestoreAppLabelUseCase`, `AppRepositoryImpl` tách `loaded` + `combine` + `overlayAndSort`, `badgedLabelFor`. Build xanh (debug). Log đo tạm tag `RN` (`loaded#n`, `labels#n`, `apps#n`) ở **commit riêng** 4059aa6 để revert khi đóng nhánh | acc6ede, 4059aa6 | Chờ điều phối review và đo cold start trên Live 4 |
| 2026-11-13 | 2 | `RenameAppsViewModel` + `RenameAppsSettingsScreen` (danh sách mọi app sắp theo tên gốc; bấm = hộp thoại một ô nhập, Lưu/Khôi phục/Hủy; Lưu tắt khi rỗng, không đổi chữ thì Lưu không làm gì; một hộp mỗi lúc), hàng mới `SettingsRow.AppName` + `AppNameHolder`, `SettingsScreen.onAppNameClicked` (mặc định rỗng), một dòng ở `SettingsActivity`, layout `item_settings_app_name.xml`/`dialog_rename_app.xml`, khối chuỗi `settings_rename_`, đăng ký ô `RENAME_APPS`. Build xanh (debug). Step 5 (kịch bản Live 4) chưa chạy: chờ máy rảnh | 04ffb17 | Task 1 qua review điều phối 2026-11-13 (vòng 1/5); giữ commit `RN-TMP` 4059aa6 tới khi điều phối báo số đo |
| 2026-11-13 | 3 | `SearchViewModel` dòng 155: `Candidate(App(it), it.label, keywords = [tên gốc] khi khác tên riêng)`. Build xanh (debug). Step 2 (kịch bản Live 4) chưa chạy | 5c7780e | Task 2 qua review điều phối 2026-11-13 (vòng 1/5) |
| 2026-11-13 | 4 | `AppRepositoryImpl.init`: thu `packageEvents` (REMOVED, USER_REMOVED), xóa tên riêng của package/hồ sơ chỉ khi `isPackageInstalled == false` (USER_REMOVED chỉ cho user hồ sơ, `isWorkUser`), mỗi sự kiện bọc try/catch; quét một lần mỗi tiến trình sau lần nạp đầu (`sweepOrphanLabels`, nền, bỏ qua danh sách rỗng); `AppLabelRepository.removePackage/removeUser`. Step 3 (a11yLabel hồ sơ): đã có ở Task 1 (`overlayAndSort` gọi `badgedLabelFor`), chỉ đọc lại code. Build xanh (debug). Step 4 (kịch bản `pm` do điều phối) chưa chạy | 390f94f | Task 3 qua review điều phối 2026-11-13 (vòng 1/5) |
| 2026-11-13 | 5 | `SettingsAdapter`: `AppNameHolder` nạp icon (`iconScope` + `loadIcon` tùy chọn; một Job mỗi holder, hủy ở bind lại và `onViewRecycled`; giữ icon khi key + stamp không đổi), `SettingsScreen.appIconLoader` (mặc định null), `SettingsActivity` truyền `lifecycleScope` + bộ nạp, `RenameAppsViewModel.loadIcon`, layout thêm `ImageView` (`library_icon_size`); sửa cảnh báo `@param:Named`. Gỡ log `RN-TMP` (revert `2f1c419`) sau khi điều phối đo cold start. Build xanh (debug). Step 2 chưa chạy | 418c2ab | Task 4 qua review điều phối 2026-11-13 (vòng 1/5). Điều phối chạy kịch bản `pm`/hồ sơ của Task 4 và kiểm màn Task 2–3 trong bài kiểm tay của chủ dự án |
| 2026-11-13 | 6 | Knowledgement `docs/knowledgements/RN-rename-apps.md` (3 sơ đồ Mermaid, số đo cold start kèm nhãn log, ghi chú mở rộng công thức G2d: hàng `AppName` và hook `appIconLoader`, bài học); điền bảng bằng chứng Review Focus (các dòng cần máy ghi "chưa kiểm"); viết lại "Bài kiểm cả tính năng" thành bài kiểm tay gộp 27 mục (cụm E là kịch bản `pm` với APK thử FX3, cụm F hồ sơ công việc là tùy chọn, cần Test DPC); cập nhật "Đề xuất cập nhật tài liệu chung". Không đổi code | 450255b | Task 5 qua review điều phối 2026-11-13 (vòng 1/5). Không rebase theo chỉ đạo điều phối |
| 2026-11-13 | — | Điều phối duyệt plan; Q1–Q7 theo đề xuất; bỏ Task 6 (re-point A3); ghi nhận hàng `AppName` và hook nạp icon là mở rộng của công thức G2d (ghi vào knowledgement) | — | Duyệt 2026-11-13 |

## Mọi nơi nhãn xuất hiện, và tên riêng đến đó bằng cách nào (đọc code, 2026-11-12)

| Nơi | Đọc nhãn thế nào | Tên riêng đến bằng | Việc cần làm |
|---|---|---|---|
| Lưới Home | `IconCellView.bind`: `item.label`; trợ năng `item.a11yLabel.ifEmpty { item.label }` (dòng ~438) | `PlacedApp.app` ← `LayoutRepositoryImpl.assemble` ← `AppRepository.apps` | Tự có (Task 1). Làm mới: `HomePage ==` + `setItems` bind lại |
| Dock | `DockView.setItems`: `items == this.items` (so cả `AppItem`) → bind lại ô | `DockApp.app` cùng nguồn | Tự có |
| Thư mục (tên) | `RenameFolderUseCase`, `PlacedFolder.title` | Hoàn toàn riêng | Không đổi |
| Thư mục (ô con trong panel) | `FolderPanelView` bind `AppItem` (`shown == this.apps`) | `previewApps`/app con ← `assemble` | Tự có |
| App Library (trang danh mục) | Ô nhóm hiện tên nhóm (chuỗi); trợ năng ô app `CategoryPageView.bindAppTarget` (`a11yLabel.ifEmpty { label }`, dòng ~510) | `GetCategoryGroupsUseCase` ← `apps` | Tự có (chỉ trợ năng) |
| App Library (panel nhóm) | `CategoryGroupPanelView`: `holder.label.text = app.label`, `Diff old == new` | cùng nguồn | Tự có; thứ tự trong nhóm theo tên hiển thị (Task 1 sắp lại) |
| Tìm kiếm | `SearchViewModel` dòng 155: `Candidate(App(it), it.label)`; `SearchResultAdapter.labelOf`; `ShortcutEntry.appLabel` (dòng 263); toast `LaunchFailed(app.label)` (514, 528) | `visibleApps` ← `apps` | Hiển thị tự có; chỉ mục thêm `keywords = tên gốc` (Task 3). Chuẩn hóa do `AppSearchMatcher.normalize` (một hàm cho cả nhãn lẫn truy vấn) |
| Ứng dụng đã ẩn | `AppLibraryAdapter`: `row.app.label` | `hiddenApps` = `combine(hiddenAppDao, apps)` | Tự có |
| Menu nhấn giữ | Không hiện tên app (chỉ nhãn hành động + `shortcut.shortLabel`) | — | Không cần; Q1 |
| Trợ năng | `contentDescription` ô Home/Library; app hồ sơ dùng `a11yLabel` do hệ thống dịch từ nhãn **gốc** (`getUserBadgedLabel`) | overlay | Tính lại từ tên riêng cho app hồ sơ (Task 1 Step 4) |
| Toast "Không mở được …" | `HomeViewModel` (dòng ~265), `SearchViewModel` | `AppItem.label` | Tự có (người dùng thấy tên họ đặt) |
| Cache nhãn đĩa LC (`no_backup/app_label_cache.json`) | `loadApps` ghi `item.label` (gốc) | — (overlay sau `loadApps`) | **Không đổi**, không tăng `VERSION`; LC tiếp tục giữ nguyên tên gốc, header locale/fingerprint vẫn hợp lệ |
| Cache icon RAM/đĩa/view | Khóa `ComponentKey` + `versionStamp` (`AppIconKey`), không chứa nhãn | — | Không đổi; đổi tên không làm nháy icon |
| Sắp xếp toàn app | `Collator` trong `loadSorted` | — | Chuyển ra sau overlay (Task 1); ảnh hưởng thứ tự Library, tìm kiếm a–z, danh sách ẩn, seed lần đầu (máy khôi phục từ backup) |

## Bằng chứng Review Focus (điền khi đóng)

Nhãn nguồn: đọc code / đo được / suy luận chưa kiểm chứng. "Chưa kiểm" = chưa ai chạy trên máy; số mục trỏ về "Bài kiểm cả tính năng".

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Một chỗ áp tên | Đọc code: `git diff origin/main --name-only` không có `ui/home/*`, `data/layout/*`, `LabelCache`, `IconCache`, `IconDiskCache`, `IconLoader`, manifest, schema; `grep "label = custom"` chỉ ở `AppRepositoryImpl.overlayAndSort` | Đạt |
| 2 | Không nháy lúc khởi động; độ trễ overlay | Đo được (điều phối, Live 4, debug, 6 lần, 0 tên riêng): một lần phát `apps` mỗi lần khởi động, `labels − loaded` −73 đến −131 ms, `apps − loaded` 13–31 ms. Chưa đo nhiều tên riêng và Oppo | Đạt (với 0 tên riêng) |
| 3 | Khóa có `userSerial`; khóa hỏng không crash | Đọc code: `AppLabelKeys.keyOf/parse` (khóa lạ trả null và bị bỏ qua). Hồ sơ công việc: mục 22–25 | Chưa kiểm trên máy |
| 4 | Lỗi lưu trữ không giết `apps` | Đọc code: `IOException` thành bản đồ rỗng, `ReplaceFileCorruptionHandler`, ghi lỗi báo qua `failures` rồi `rerender(true)`. Chưa làm hỏng/xóa file trên máy | Chưa kiểm trên máy |
| 5 | Làm mới Home | Đọc code: `apps` → `LayoutRepositoryImpl` → `HomeViewModel.pages`; `==` của `HomePage`/`DockView`/`FolderPanelView` thấy khác. Mục 9, 10 | Chưa kiểm trên máy |
| 6 | Sắp xếp và tìm kiếm | Đọc code: `overlayAndSort` sắp theo tên hiển thị, `Candidate` có `keywords`. Mục 11, 12 | Chưa kiểm trên máy |
| 7 | Trợ năng | Đọc code: `badgedLabelFor` cho app có `a11yLabel`; user chính dùng `label`. Mục 24, 26 | Chưa kiểm trên máy |
| 8 | Dọn mồ côi an toàn | Đọc code + review điều phối (vòng 1/5): chốt `isPackageInstalled`, `isWorkUser`, quét một lần, bỏ qua danh sách rỗng, `try/catch` từng sự kiện. Mục 18–21, 25 | Chưa kiểm trên máy |
| 9 | Cache | Đọc code: `loadApps` ghi `item.label` (gốc) vào `LabelCache` trước overlay; không đổi `VERSION`. Mục 14 (đọc file `app_label_cache.json` trên máy) | Chưa kiểm trên máy |
| 10 | Hộp thoại | Đọc code + review điều phối: một hộp, `onDestroy` gỡ listener rồi `dismiss()`, chữ đặt trước `InputFilter`, không đổi chữ thì Lưu không làm gì, không `Log` tên. Mục 4–8 | Chưa kiểm trên máy |
| 11 | Không sửa ngoài vùng; `strings.xml` chỉ thêm cuối; log tạm đã gỡ | Đọc code: `git diff origin/main --stat` (21 file, đúng "Vùng file"); `strings.xml` +7 dòng ở cuối; `grep RN-TMP` rỗng (log gỡ bằng revert `2f1c419`) | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: thêm mục "Đổi tên ứng dụng" (màn 5.3 của bản nháp), trạng thái theo kết quả kiểm tay; ghi hạn chế A3 (app đổi activity khi cập nhật thì mất tên riêng) và Q5 (gỡ thật rồi cài lại không giữ tên).
- `project_structure.md`: `data/apps` thêm `AppLabelRepositoryImpl`, `AppLabelKeys` và file DataStore `app_labels` (provider `@Named("app_labels")` trong `DataStoreModule`); `AppItem` thêm `originalLabel` (**`label` giờ là tên hiển thị**); `AppRepositoryImpl.apps` = `combine(loaded, customLabels)` + `overlayAndSort`, dọn tên mồ côi (sự kiện + quét một lần); `LauncherAppsDataSource.badgedLabelFor`; `domain` thêm `AppLabelRepository`, `RenameAppUseCase`, `RestoreAppLabelUseCase`; `ui/settings` thêm `RenameAppsSettingsScreen`, `RenameAppsViewModel`, `SettingsRow.AppName`, hook `SettingsScreen.appIconLoader`.
- `plans/G2d-settings-hub.md` mục "Công thức thêm màn/hàng": thêm ghi chú mở rộng cho hàng có **chuỗi động** hoặc **icon** (dùng `SettingsRow.AppName`, `onAppNameClicked`, `appIconLoader`; xem knowledgement RN mục 4) để nhánh sau (vd CI 5.2) dùng lại.
- `room-schema.md`: không đổi; có thể ghi một dòng "tên riêng của app không nằm ở Room (xem plan RN) vì `launcher_items` là vị trí, app ẩn không có hàng và schema đang khóa".
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement RN. Chủ đề đề xuất: `icons-and-app-list` (overlay tên riêng, khóa `userSerial`, `a11yLabel`, hạn chế A3), `startup-performance` (số đo `labels#1/loaded#1/apps#1`; build sạch worktree mới 6 phút 32 giây thay cho "chưa đo" ở AGENTS.md), `settings-and-data` (DataStore riêng, mở rộng công thức G2d, ô nhập đặt chữ trước `InputFilter`), `permissions-and-device-testing` (heredoc lồng trong Git Bash).
