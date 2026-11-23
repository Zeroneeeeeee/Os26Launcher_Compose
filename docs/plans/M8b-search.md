# M8b — Tìm kiếm app (vuốt xuống + overlay) (TT#9) — Implementation Plan

> **Dành cho agent thực thi:** làm theo đúng quy ước dự án trong `docs/AGENTS.md` — code từng Task, báo cáo, **chờ người dùng xác nhận trên máy thật** rồi mới tick hoàn thành và sang Task sau. Dự án **không có test tự động** (A7) — mọi bước "kiểm chứng" dưới đây là kiểm chứng tay trên Vsmart Live 4 (máy mặc định, A8); Oppo A3x chỉ khi cần số máy mạnh/danh sách 344 app. Claude không build/cài được từ môi trường của mình — người dùng build (`gradlew.bat assembleDebug`), cài, chạy kịch bản, dán log/kết quả lại. Lệnh viết cho CMD/PowerShell, không viết bash.

**Goal:** Vuốt xuống ở giữa màn hình chính mở lớp tìm kiếm (nền tối + ô nhập phẳng); gõ tên (không cần gõ dấu tiếng Việt) thấy danh sách app đã cài (trừ app ẩn), bấm để mở app.

**Architecture:** Hàm thuần `AppSearchMatcher` (domain) lọc và xếp hạng; `SearchViewModel` (Hilt, thuộc `HomeActivity`) ghép `AppRepository.apps` với app đã ẩn rồi áp truy vấn, giữ `isOpen` + `query` để sống qua việc Activity bị tạo lại. Lớp phủ là một `SearchOverlayView` nằm TRONG `activity_home.xml` (không Activity/Dialog riêng). Cử chỉ nhận ở `HomeActivity.dispatchTouchEvent` (cùng mẫu "tiếp quản" của kéo-thả M7), logic hình học tách ra `SearchSwipeDetector` để đọc riêng.

**Tech Stack:** View/XML (B1), MVVM + StateFlow (A4), Hilt (A2), Material 3, `RecyclerView` + `ListAdapter`. Không thêm dependency. `java.text.Normalizer` (JDK) để bỏ dấu.

**Spec:** thiết kế duyệt qua khảo sát 2026-10-29 (bảng "Quyết định đã chốt") + `docs/Launcher-Features.md` TT#9 + `docs/project_structure.md` A6 (`adjustNothing`, overlay tự tính inset bàn phím). Pill lồng chấm trang là M8c, không nằm trong plan này.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Nguồn kết quả: **app đã cài, trừ app ẩn** (= tab "Chưa ẩn" của Thư viện) | Chốt 2026-10-29. Danh mục Cài đặt hệ thống, shortcut, gợi ý/app dùng gần đây: để sau, chỉ ghi chú |
| D2 | Khớp: không phân biệt hoa thường, **bỏ dấu tiếng Việt** (`đ/Đ` → `d`), khớp **đầu từ** hoặc **chuỗi con**. Xếp hạng: (1) nhãn bắt đầu bằng truy vấn → (2) một từ của nhãn bắt đầu bằng truy vấn → (3) nhãn chứa truy vấn; cùng hạng giữ thứ tự của `AppRepository.apps` | Truy vấn nhiều từ coi là một chuỗi đã chuẩn hóa khoảng trắng (`"may anh"` khớp `"Máy ảnh"` ở hạng 1) |
| D3 | Truy vấn rỗng (hoặc chỉ khoảng trắng): **không liệt kê gì**, hiện dòng gợi ý "Nhập tên ứng dụng" | Chưa có gợi ý/app gần đây (để sau) |
| D4 | Cử chỉ: vuốt xuống **≥ 64dp**, gần dọc (`dy ≥ 2·|dx|`), điểm chạm xuống nằm trong vùng hợp lệ: dưới `inset thanh trạng thái + 48dp` và trên đỉnh dock. Cạnh trên thuộc hệ thống (kéo thông báo) nên không bắt ở đó | Không kích hoạt khi: edit mode, menu nhấn giữ đang mở, phiên kéo-thả, hộp thoại wallpaper, lưới chưa `Ready`, lớp tìm kiếm đã mở |
| D5 | Giao diện: nền tối `#CC000000` toàn màn, ô nhập **phẳng** (không kính) bo tròn, cao 48dp, ô nằm sát phía trên (dưới thanh trạng thái), danh sách kết quả bên dưới | Chữ trắng trên nền tối → tương phản đạt AA ở cả chế độ sáng/tối của hệ thống |
| D6 | Bàn phím: `adjustNothing` giữ nguyên (A6). Ô nằm trên cùng nên bàn phím chỉ che cuối danh sách; **API ≥ 30** đệm đáy danh sách bằng `WindowInsets.ime()`. **API 26–29: chưa có máy kiểm** → không đệm (danh sách có thể bị che phần cuối, vẫn gõ được) | Ghi vào giới hạn đã biết |
| D7 | Đóng lớp tìm kiếm: Back, chạm vào vùng nền ngoài ô/danh sách, nhấn Home, mở app từ kết quả. Đóng thì xóa truy vấn và ẩn bàn phím | Back lần đầu có thể chỉ ẩn bàn phím (hệ thống xử lý), lần sau mới đóng lớp |
| D8 | Bấm kết quả: `AppRepository.launch(key, source)` với `source` = vùng icon của hàng; thất bại → `Toast` (chuỗi `app_launch_failed`), **giữ** lớp mở | |
| D9 | `isOpen` và `query` nằm ở `SearchViewModel` để sống qua `recreate()` / đổi giao diện sáng-tối | |
| D10 | Không thêm mục "tìm kiếm" vào Cài đặt ở M8b (chưa có tùy chọn nào đáng cấu hình — YAGNI) | |

## Ngoài phạm vi M8b / ghi để sau

- Pill tìm kiếm lồng pill chấm trang (M8c); Dashboard; TalkBack dấu "−".
- Gợi ý/app dùng gần đây khi ô trống; danh mục Cài đặt hệ thống (`Settings.ACTION_*`); shortcut app; danh bạ (G3).
- Tìm trong app đã ẩn (cố ý loại, D1); sắp xếp theo mức dùng.
- Đệm bàn phím cho API 26–29 (cần máy để đo).

## Global Constraints

- `domain` không import `android.*` (`java.text.Normalizer` là JDK, được); `ui` không import `data` trực tiếp.
- Mọi chữ hiển thị ở `strings.xml` (tiếng Việt); màu ở `colors.xml`; kích thước ở `dimens.xml`; 8dp grid, vùng chạm ≥ 48dp, WCAG AA.
- Màn mới định nghĩa đủ trạng thái: Default (có kết quả), Empty (không khớp), Idle (truy vấn rỗng), Loading (danh sách app chưa nạp), Error (mở app thất bại → Toast), Disabled (không áp dụng).
- Không đổi schema Room, không thêm khóa DataStore.
- Cử chỉ mới **không được** làm hỏng: vuốt ngang chuyển trang, nhấn giữ + kéo-thả icon, menu nhấn giữ, lật trang khi kéo, kéo thông báo từ mép trên.
- Mọi `View` giữ trạng thái phải suy ra từ dữ liệu (`SearchUiState`), không giữ bản sao lâu dài trong view (bài học M7 Task 5).
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự, không dấu tiếng Việt (AGENTS.md).

## Review Focus

- **Cử chỉ kích hoạt nhầm / bị chặn nhầm:** vuốt ngang chuyển trang, nhấn giữ rồi kéo icon xuống, vuốt xuống trên dock, kéo thông báo từ mép trên, vuốt xuống khi đang ở edit mode hoặc khi menu nhấn giữ đang mở → lớp tìm kiếm chỉ mở đúng một lần khi vuốt xuống thật ở vùng hợp lệ; các thao tác kia hoạt động như cũ.
- **Gõ tiếng Việt:** gõ không dấu (`dien thoai`), có dấu (`Điện thoại`), chữ `đ/Đ`, dấu tổ hợp (NFD) và dựng sẵn (NFC), bộ gõ đang "composing" → kết quả đúng và không giật; nhãn app có ký tự đặc biệt/emoji không làm crash.
- **Đóng/mở và bàn phím:** Back, chạm nền, nhấn Home, mở app → lớp đóng, bàn phím ẩn, truy vấn xóa; mở lại lần hai ô trống và bàn phím hiện lại; không rò cửa sổ/bàn phím.
- **Dữ liệu đổi khi lớp đang mở:** cài mới, gỡ, ẩn/bỏ ẩn app (từ nền hoặc app khác) → danh sách cập nhật, không crash; bấm app vừa bị gỡ → Toast, lớp vẫn mở.
- **Vòng đời:** đổi giao diện sáng/tối hoặc mức kính (Home `recreate()`) khi lớp đang mở → giữ truy vấn, không hiện hai lớp; danh sách lớn (Oppo 344 app) gõ nhanh không giật.

---

### Task 1: Lõi tìm kiếm — `AppSearchMatcher` + `SearchViewModel` (chưa có giao diện)

**Files:**
- Create: `domain/search/AppSearchMatcher.kt`, `ui/search/SearchViewModel.kt`, `ui/search/SearchUiState.kt`
- Modify: `res/values/strings.xml` (chuỗi dùng ở Task 2-3 thêm ở đó)

**Interfaces:**
- Consumes: `AppRepository.apps: Flow<List<AppItem>>`, `GetHiddenAppsUseCase(): Flow<List<AppItem>>` (đã có), `AppRepository.launch/icon`.
- Produces:
  - `object AppSearchMatcher { fun normalize(text: String): String; fun search(apps: List<AppItem>, query: String): List<AppItem> }` — thuần, không `android.*`. `normalize`: `Normalizer.Form.NFD`, bỏ mọi ký tự `\p{Mn}`, `đ/Đ → d`, `lowercase(Locale.ROOT)`, trim, thu nhiều khoảng trắng thành một. `search`: truy vấn chuẩn hóa rỗng → danh sách rỗng; còn lại xếp hạng theo D2, ổn định theo thứ tự đầu vào.
  - `enum class SearchStatus { IDLE, LOADING, NO_RESULTS, RESULTS }`; `data class SearchUiState(val isOpen: Boolean = false, val query: String = "", val results: List<AppItem> = emptyList(), val status: SearchStatus = SearchStatus.LOADING)`.
  - `SearchViewModel`: `uiState: StateFlow<SearchUiState>`, `open()`, `close()` (xóa truy vấn), `onQueryChanged(text: String)`, `onResultTapped(app: AppItem, source: ScreenRect?)`, `events: Flow<SearchEvent>` với `SearchEvent.LaunchFailed(label: String)`, `suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap?`.

- [x] **Step 1: `AppSearchMatcher`** — viết theo đặc tả; ca biên: nhãn rỗng, truy vấn dài hơn nhãn, nhãn có ký tự ngoài BMP (emoji) không làm hỏng chỉ số (dùng `String`/`indexOf`, không cắt theo `Char`).
- [x] **Step 2: `SearchViewModel` + `SearchUiState`** — `combine(appRepository.apps.filter { it.isNotEmpty() }, getHiddenApps(), query)`: loại app có trong tập ẩn rồi `AppSearchMatcher.search`; `status`: chưa có danh sách app → `LOADING`; truy vấn chuẩn hóa rỗng → `IDLE`; có truy vấn mà rỗng kết quả → `NO_RESULTS`; còn lại `RESULTS`. `stateIn(WhileSubscribed(5_000))`. `isOpen`/`query` là `MutableStateFlow` trong ViewModel (D9). `onResultTapped`: `launch` thất bại → `SearchEvent.LaunchFailed`; thành công → `close()`.
- [x] **Step 3: Log tạm tag `M8B`** — khi ViewModel khởi tạo xong danh sách, ghi kết quả của 8 truy vấn mẫu (`"cam"`, `"may anh"`, `"dien thoai"`, `"đ"`, `"  "`, `"zzz"`, một chữ cái, một từ giữa nhãn) để đọc trong logcat. **Xóa log ở Task 3 sau khi kiểm chứng.**
- [x] **Step 4: Kiểm chứng (Live 4)** — build xanh (`gradlew.bat assembleDebug`); cài đè; `adb logcat -s M8B`: `"may anh"` ra `Máy ảnh`/`Camera` (tên app thật trên máy); `"dien thoai"` ra `Điện thoại`; `"  "` ra rỗng; `"zzz"` ra rỗng; thứ tự: nhãn bắt đầu bằng truy vấn đứng trước nhãn chỉ chứa nó. App đã ẩn không xuất hiện. Dán log lại.
- [x] **Step 5: Ghi `docs/AGENTS.md` (nếu có bài học)** — chưa commit riêng; gộp commit ở Task 2 nếu chưa có giao diện thì commit ở bước này: `feat: add app search matcher and view model`.

---

**Kết quả kiểm chứng (Live 4, 2026-10-30, 85 app hiển thị):** `may anh` → `[Máy ảnh]`; `dien thoai` → `[Điện thoại]`; `đ` → 24 kết quả, nhãn bắt đầu bằng d/đ (Danh bạ, Dragon Boy, Drive, Điện thoại, Đồng hồ…) đứng trước `Cài đặt` (khớp đầu từ); `  ` và `zzz` → rỗng; `a` → 49, nhãn bắt đầu bằng `a` (AccelerometerSensor, Authenticator, Auto Clicker) đứng trước nhãn khớp đầu từ (Ghi âm, Magic AR…); `Clicker` (từ giữa nhãn) → `[Auto Clicker]`; `cam` → rỗng vì máy không có app tên Camera. Hạng 2 (chuỗi con giữa từ) và app ẩn chưa có ca log riêng: kiểm ở Task 3 trên giao diện.

### Task 2: Cử chỉ vuốt xuống + khung lớp tìm kiếm (nền tối, ô nhập, bàn phím, đóng)

**Files:**
- Create: `ui/home/SearchSwipeDetector.kt`, `ui/search/SearchOverlayView.kt`, `res/layout/view_search_overlay.xml`, `res/drawable/bg_search_field.xml`
- Modify: `ui/home/HomeActivity.kt` (`dispatchTouchEvent`, Back, `onNewIntent`, quan sát `SearchViewModel`), `res/layout/activity_home.xml` (thêm `search_overlay` ngay DƯỚI `drag_layer`), `res/values/strings.xml`, `colors.xml`, `dimens.xml`

**Interfaces:**
- Consumes: `SearchViewModel.open()/close()/onQueryChanged`, `SearchUiState.isOpen/query` (Task 1).
- Produces:
  - `class SearchSwipeDetector(slopPx: Float, triggerDistancePx: Float)` — `fun onTouchEvent(ev: MotionEvent, enabled: Boolean, startZone: ClosedFloatingPointRange<Float>): Boolean` trả `true` đúng một lần cho mỗi chuỗi chạm khi thỏa D4 (đã có `DOWN` trong vùng, rồi `dy ≥ triggerDistancePx` và `dy ≥ 2·|dx|`); `reset()` khi `UP/CANCEL`. Không giữ tham chiếu `View`.
  - `SearchOverlayView : FrameLayout` — `fun render(state: SearchUiState)`, `var onQueryChanged: ((String) -> Unit)?`, `var onDismissRequested: (() -> Unit)?`; tự `requestFocus` + hiện bàn phím khi vừa mở (`WindowInsetsControllerCompat.show(ime())`) và ẩn khi đóng; đệm đáy danh sách theo `ime()` (D6).

Trạng thái: **Default** = lớp mở, ô nhập có chữ; **Idle** = ô rỗng (hiện gợi ý "Nhập tên ứng dụng"); **Loading/Empty/Error/Disabled** thuộc Task 3 (danh sách). Ở Task này chỉ cần khung + Idle.

- [x] **Step 1: `SearchSwipeDetector`** — theo đặc tả D4. `HomeActivity` chỉ dùng khi `enabled = !editMode && iconMenu == null && DragHelper.currentSession == null && !showWallpaperPrompt && gridStatus == Ready && !isOpen`; `startZone` = từ `statusBarTop + 48dp` tới đỉnh dock (tọa độ màn hình).
- [x] **Step 2: Nối vào `dispatchTouchEvent`** — đặt SAU nhánh phiên kéo-thả, TRƯỚC `super`: khi detector trả `true` thì gửi một `ACTION_CANCEL` giả xuống cây view (giống `dragTouchTakenOver` của M7) rồi gọi `searchViewModel.open()`, và nuốt phần còn lại của chuỗi chạm đó (đến `UP/CANCEL`) để `ViewPager2`/icon không nhận nửa chừng.
- [x] **Step 3: `view_search_overlay.xml` + `SearchOverlayView`** — nền `search_scrim` (#CC000000), ô nhập (`EditText` một dòng, `imeOptions=actionSearch`, `inputType=textNoSuggestions`, nút xóa chữ ≥ 48dp chỉ hiện khi có chữ), `RecyclerView` (để trống ở Task này) và `TextView` gợi ý Idle; chạm vùng nền ngoài ô/danh sách → `onDismissRequested`. Fade vào/ra 150ms; `visibility` suy ra từ `state.isOpen`.
- [x] **Step 4: Đóng** — `OnBackPressedCallback` (đăng ký SAU `closeIconMenuOnBack`, bật khi `isOpen`) gọi `close()`; `onNewIntent` (launcher `singleTask`) với intent `ACTION_MAIN` + `CATEGORY_HOME` khi đang mở → `close()`; `close()` xóa truy vấn và ẩn bàn phím. Thêm `HomeActivity.onNewIntent` nếu chưa có (hiện chưa).
- [x] **Step 5: Kiểm chứng (Live 4)** — build xanh; (a) vuốt xuống giữa màn hình: lớp hiện, bàn phím hiện, con trỏ trong ô; (b) vuốt **ngang** chuyển trang, vuốt xuống từ mép trên kéo thông báo, vuốt xuống trên dock, vuốt xuống ở edit mode, khi menu nhấn giữ đang mở, trong lúc kéo icon: **không** mở lớp; nhấn giữ + kéo icon, kéo-thả, dấu "−", menu nhấn giữ vẫn đúng; (c) Back / chạm nền / nhấn Home: lớp đóng, bàn phím ẩn, mở lại ô trống; (d) đổi giao diện sáng/tối khi lớp đang mở: lớp còn, không rò cửa sổ; (e) vuốt xuống nhanh nhiều lần liên tiếp: chỉ mở một lần.
- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit** (`feat: add swipe-down search overlay shell`).

---

**Kết quả kiểm chứng (Live 4, 2026-10-30):** người dùng xác nhận các kịch bản (a)–(e) đạt: vuốt xuống mở lớp + bàn phím; vuốt ngang, mép trên, dock, edit mode, menu nhấn giữ, kéo icon không mở lớp và các thao tác cũ không đổi; Back/chạm nền/Home đóng lớp; đổi sáng-tối giữ lớp; vuốt liên tiếp chỉ mở một lần.

### Task 3: Danh sách kết quả + mở app + các trạng thái

**Files:**
- Create: `ui/search/SearchResultAdapter.kt`, `res/layout/item_search_result.xml`
- Modify: `ui/search/SearchOverlayView.kt`, `ui/home/HomeActivity.kt` (nối `SearchViewModel.events`, `loadIcon`), `res/values/strings.xml`

**Interfaces:**
- Consumes: `SearchUiState.results/status`, `SearchViewModel.onResultTapped/loadIcon/events` (Task 1); `SearchOverlayView.render/onQueryChanged` (Task 2).
- Produces: `SearchResultAdapter(iconScope: CoroutineScope, loadIcon: suspend (ComponentKey, Int) -> Bitmap?, onTapped: (AppItem, ScreenRect?) -> Unit) : ListAdapter<AppItem, …>` — hàng icon 40dp + nhãn, cao tối thiểu 56dp, nạp icon bất đồng bộ theo mẫu `AppLibraryAdapter` (mỗi holder giữ một `Job`, hủy khi bind lại và kiểm lại `key` trước khi gán). Khi `source` rect tính từ vị trí icon trên màn hình.

Trạng thái: **Default** = danh sách kết quả; **Empty** = "Không tìm thấy ứng dụng"; **Idle** = gợi ý (Task 2); **Loading** = vòng tiến trình nhỏ khi app chưa nạp; **Error** = Toast `app_launch_failed`.

- [x] **Step 1: Hàng kết quả + adapter** — theo đặc tả; tắt `itemAnimator` (tránh nháy khi gõ); `DiffUtil` theo `key`.
- [x] **Step 2: Nối trạng thái** — `render(state)` đổi giữa danh sách / Empty / Idle / Loading theo `SearchStatus`; Enter/`actionSearch` ẩn bàn phím (không mở app tự động).
- [x] **Step 3: Mở app** — bấm hàng → `onResultTapped(app, rect)`; `SearchEvent.LaunchFailed` → `Toast` `getString(R.string.app_launch_failed, label)`, lớp **giữ mở**.
- [x] **Step 4: Xóa log tạm `M8B`** của Task 1.
- [x] **Step 5: Kiểm chứng (Live 4, và Oppo A3x 344 app cho hiệu năng)** — (a) gõ `cam`, `may anh`, `dien thoai`, `Điện`, `đ`: kết quả đúng thứ tự D2, không phân biệt hoa thường và dấu; (b) xóa hết chữ / nút xóa → Idle; gõ `zzz` → Empty; (c) bấm kết quả: app mở với animation từ icon, lớp đóng, quay lại Home ô trống; (d) ẩn một app ở edit mode rồi tìm: **không** thấy; bỏ ẩn: thấy; (e) khi lớp đang mở, gỡ một app bằng `adb shell pm uninstall` rồi bấm nó: Toast, lớp còn mở, không crash; (f) bàn phím tiếng Việt đang "composing" (Telex/VNI): kết quả theo chữ đang gõ, không giật; (g) Oppo: gõ nhanh với 344 app không khựng; (h) danh sách dài cuộn được, đáy không bị bàn phím che (API ≥ 30); (i) split-screen hẹp: lớp và ô vẫn dùng được.
- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit** (`feat: show app search results and launch from overlay`).

---

**Kết quả kiểm chứng (Live 4, 2026-10-30):** người dùng xác nhận đạt (a)–(f), (h), (i) và chạm vùng trống dưới danh sách đóng lớp. **(g) Oppo A3x 344 app gõ nhanh: người dùng hoãn, chưa kiểm** — ghi nhận ở Task 4.

### Task 4: Đóng M8b — rà Review Focus, đồng bộ tài liệu [HOÀN THÀNH 2026-10-30]

**Files:**
- Modify (tài liệu): `docs/Launcher-Features.md` (TT#9 → `Một phần`: M8b đã có gì, M8c và các nguồn kết quả còn lại), `docs/project_structure.md` (dòng M8b ✅; cây `ui/search/`, `domain/search/`; danh sách tệp thêm ở M8b), `docs/AGENTS.md`, file này.

- [x] **Step 1: Rà Review Focus** — mỗi dòng có bằng chứng ghi cạnh (kịch bản, máy, kết quả) như bảng của M8a; ca nào chỉ được bảo vệ bằng code, chưa ai kiểm tay thì ghi rõ.
- [x] **Step 2: Đồng bộ tài liệu** như danh sách Files; ghi rõ giới hạn đã biết (API 26–29 chưa kiểm bàn phím; chưa gợi ý/app gần đây; không tìm app ẩn) và việc để sau (M8c, danh mục Cài đặt, shortcut).
- [x] **Step 3: Tick và đóng** — M8b chỉ được tick sau khi người dùng xác nhận toàn bộ trên máy thật.
- [x] **Step 4: Commit** (`docs: close M8b and sync project docs`).

### Bằng chứng Review Focus (Task 4 Step 1, ghi 2026-10-30)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Cử chỉ kích hoạt nhầm / bị chặn nhầm | Task 2 Step 5 (a)(b)(e), Live 4: vuốt ngang, mép trên, dock, edit mode, menu nhấn giữ, kéo icon không mở lớp; nhấn giữ + kéo, "−", menu giữ nguyên; vuốt liên tiếp chỉ mở một lần | PASS (người dùng xác nhận) |
| 2 | Gõ tiếng Việt | Task 1 log `M8B` (Live 4): `may anh`, `dien thoai`, `đ`, `a`, từ giữa nhãn; Task 3 Step 5 (a)(f): có/không dấu, Telex/VNI đang composing | PASS. **Chưa kiểm tay riêng:** nhãn app chứa emoji / ký tự ngoài BMP, nhãn ở dạng NFD (chỉ được bảo vệ bằng code: `Normalizer` + `indexOf` theo `String`) |
| 3 | Đóng/mở và bàn phím | Task 2 Step 5 (a)(c)(e); Task 3 Step 5 (c) mở app đóng lớp, quay lại Home ô trống; chạm vùng trống dưới danh sách đóng lớp | PASS. Giới hạn: API 26–29 chưa có máy kiểm bàn phím |
| 4 | Dữ liệu đổi khi lớp đang mở | Task 3 Step 5 (d) ẩn/bỏ ẩn, (e) gỡ app bằng `pm uninstall` rồi bấm: Toast, lớp còn mở | PASS. **Chưa kiểm tay riêng:** cài app mới khi lớp đang mở (chỉ đọc code: `combine` theo `AppRepository.apps`) |
| 5 | Vòng đời và danh sách lớn | Task 2 Step 5 (d) đổi sáng/tối giữ lớp, không rò cửa sổ | PASS cho sáng/tối. **Chưa kiểm:** Oppo A3x 344 app gõ nhanh (Task 3 (g), người dùng hoãn); đổi mức kính khi lớp mở (cùng cơ chế `recreate()`, chưa thử riêng) |

**Giới hạn đã biết sau M8b:** API 26–29 chưa kiểm đệm bàn phím (danh sách có thể bị che phần cuối); ô trống chưa có gợi ý/app dùng gần đây; app ẩn không tìm được (cố ý, D1); không có mục cấu hình tìm kiếm trong Cài đặt (D10).
