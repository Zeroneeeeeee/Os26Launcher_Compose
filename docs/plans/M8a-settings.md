# M8a — Màn Cài đặt launcher (TT#20) — Implementation Plan

> **Trạng thái: HOÀN THÀNH 2026-10-30** — cả 7 Task đã kiểm chứng trên máy thật và được người dùng xác nhận. Bằng chứng Review Focus ở bảng bên dưới.

> **Dành cho agent thực thi:** làm theo đúng quy ước dự án trong `docs/AGENTS.md` — code từng Task, báo cáo, **chờ người dùng xác nhận trên máy thật** rồi mới tick hoàn thành và sang Task sau. Dự án **không có test tự động** (A7) — mọi bước "kiểm chứng" dưới đây là kiểm chứng tay trên Vsmart Live 4 (máy mặc định, A8) hoặc Oppo A3x (chỉ khi liên quan kính). Claude không build/cài được từ môi trường của mình (không JDK/adb) — người dùng build (`gradlew.bat assembleDebug`), cài, chạy kịch bản, dán log/kết quả lại. Lệnh viết cho CMD/PowerShell, không viết bash.

**Goal:** Có màn Cài đặt launcher mở từ edit mode, gồm: trạng thái và nút đặt launcher mặc định, cỡ lưới theo preset (kèm re-flow bố cục đã lưu), bật/tắt nhãn dưới icon, mức kính (Tự động / Ép thấp / Ép cao), đổi ảnh nền (hỏi áp wallpaper hệ thống mỗi lần), và lối vào danh sách app đã ẩn.

**Architecture:** `SettingsActivity` riêng (View/XML, `RecyclerView` tự dựng, nhóm bo góc kiểu iOS, không thêm thư viện) + `SettingsViewModel` + `SettingsUiState`. Cỡ lưới và nhãn trở thành **dữ liệu trong DataStore** (`GridPreset`, `showLabels`), `HomeViewModel` đưa vào `HomeUiState` để mọi view đọc từ một nguồn, thay cho 10 chỗ đang dùng cứng 4×6. Re-flow là hàm thuần `GridReflowPlanner` (domain) chạy trong transaction dưới `Mutex` của `LayoutReconciler`, điều phối bởi `ChangeGridPresetUseCase`. Home phát hiện đổi mức kính / đổi ảnh nền khi `onResume` (so với giá trị đã áp), không phụ thuộc kết quả trả về từ Activity cài đặt.

**Tech Stack:** View/XML (B1), MVVM + StateFlow (A4), Room (A3), DataStore (A3), Hilt (A2), Material 3 (`MaterialAlertDialogBuilder`, `MaterialToolbar`, `MaterialButton`), Photo Picker (`PickVisualMedia`, đã dùng ở `HomeActivity`). Không thêm dependency.

**Spec:** thiết kế duyệt qua khảo sát + trao đổi 2026-10-29 (bảng "Quyết định đã chốt" bên dưới). M8 được tách: **M8a = file này**; M8b (tìm kiếm: vuốt xuống + overlay) viết plan riêng sau khi M8a xong; pill tìm kiếm lồng chấm trang là mốc ngay sau M8b.

## Quyết định đã chốt (khảo sát 2026-10-29)

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Tách M8: M8a Cài đặt → M8b Tìm kiếm → pill lồng chấm trang | Người dùng chọn |
| D2 | Giao diện Cài đặt: `RecyclerView` tự dựng, nhóm kiểu iOS | Không thêm `preference-ktx` |
| D3 | Preset cỡ lưới: **4×5, 4×6 (mặc định), 5×6**; dock giữ 4 ô | Ba preset lồng nhau theo bao hàm (4×5 ⊂ 4×6 ⊂ 5×6) — xem Global Constraints |
| D4 | Re-flow: trải phẳng icon theo thứ tự đọc (trang → hàng `y` → cột `x`) rồi chia lại theo cỡ mới, **nén hết khe trống**, một chiều, **có hộp xác nhận** | "Thứ tự đọc" = đúng thứ tự `GridInsertPlanner`/`GridRemovePlanner` dùng, **không** phải alphabet |
| D5 | Mức kính: ghi DataStore xong (chờ hoàn tất) rồi Home **tạo lại Activity** để `GlassSurfaceFactory` đọc lại | Bài học M6 Task 3: ghi bất đồng bộ, không `recreate()` ngay |
| D6 | Đổi ảnh nền: **hỏi áp wallpaper hệ thống mỗi lần** (tái dùng `WallpaperTarget`, chuỗi M1) | Quyết định 2 (áp được cả ba đích) giữ nguyên |
| D7 | Lối vào: nút "Cài đặt" trong edit mode, cạnh "Thư viện" | Home trống hết icon thì vẫn không vào được — ca đã biết, xử lý bằng Dashboard ở mốc sau |
| D8 | Mục trong M8a: launcher mặc định, cỡ lưới, nhãn, mức kính, ảnh nền, app đã ẩn | Mọi thứ đã có hạ tầng sẵn từ M1–M7 + hai khóa DataStore mới |

## Ngoài phạm vi M8a / ghi để sau

- **Dashboard** (màn vào lại app để chỉnh khi Home trống / ẩn hết app; tham khảo app mẫu) — mốc riêng, sau M8b. Cho tới đó giữ nguyên giới hạn "ẩn hết app → không vào được edit mode".
- TalkBack cho dấu "−" (nợ từ M7).
- Kiểu icon (TT#12), tìm kiếm (M8b), mục "tìm kiếm" trong cài đặt (có từ M8b).
- Cỡ lưới tự do cột × hàng, re-flow giữ khe, hoàn tác re-flow (đã quyết: không).
- Cache nhãn app xuống đĩa / đo cold start Oppo (task phụ P1, độc lập).

## Global Constraints

- `domain` không import `android.*`; `ui` không import `data` trực tiếp (luật `ui → domain ← data`).
- Mọi chữ hiển thị ở `strings.xml` (tiếng Việt); enum chỉ giữ giá trị, không hardcode chữ.
- Màn mới tuân 8dp grid, vùng chạm ≥ 48dp, tương phản WCAG AA, Material 3; mọi màn định nghĩa đủ trạng thái Default / Loading / Error / Disabled (bảng ở từng Task).
- **Bất biến của preset:** các `GridPreset` phải sắp được thành một chuỗi bao hàm (mỗi preset chứa trọn preset nhỏ hơn: cột và hàng đều không nhỏ hơn). `ChangeGridPresetUseCase` dựa vào đó để chọn thứ tự ghi an toàn (thu nhỏ: ghi bố cục trước, cài đặt sau; mở rộng: cài đặt trước, bố cục sau). Thêm preset phá bao hàm (ví dụ 3×8) phải thiết kế lại use case.
- Mọi ghi `launcher_items` / `home_pages` đi qua `Mutex` của `LayoutReconciler` (mutex không tái nhập: hàm công khai không gọi lẫn nhau, dùng hàm `private ...InTransaction`).
- Không bao giờ `fallbackToDestructiveMigration()`. M8a **không đổi schema Room** (preset nằm ở DataStore) — nếu Task nào buộc đổi schema thì dừng và báo.
- Giá trị mặc định (`GridPreset.P4X6`, `showLabels = true`, `GlassLevelOverride.AUTO`) phải cho hành vi **y hệt M7** — máy chưa từng mở Cài đặt không được thấy khác biệt.
- Commit message tiếng Anh, dạng `<type>: <imperative>` ≤ 72 ký tự, không dấu tiếng Việt (AGENTS.md).

## Review Focus

- **Kill app giữa hai bước của đổi cỡ lưới** (đã ghi một nửa: cài đặt hoặc bố cục) → mọi icon vẫn nằm trong lưới hợp lệ và nhìn thấy được nhờ thứ tự ghi theo hướng thu nhỏ/mở rộng; nếu DataStore và DB vẫn lệch (khôi phục sao lưu, xóa DataStore) thì `ensureFitsGrid` đưa icon về trong lưới ở lần mở kế tiếp.
- **Re-flow với dữ liệu thật:** có icon trong dock, app đã ẩn, trang trống ở giữa, khe trống do kéo-thả → dock và `hidden_apps` không đổi, mọi icon lưới còn nguyên số lượng, trang cuối thừa bị xóa nhưng luôn còn ≥ 1 trang; trang đang xem được kẹp về trang hợp lệ (không crash `ViewPager2`).
- **Re-flow chạy đồng thời với sự kiện cài/gỡ app ở nền** (`packageEvents`) → không ghi đè nhau (cùng `Mutex`), app cài mới lấy theo cỡ lưới MỚI.
- **Đổi mức kính rồi quay về Home:** `recreate()` đúng 1 lần (không vòng lặp), kính dựng đúng loại theo override đã ghi, không đọc phải giá trị cũ vì ghi chưa xong.
- **Ảnh nền:** hủy bộ chọn ảnh, chọn ảnh hỏng/không giải mã được, máy không cho đặt wallpaper (`wallpaperUnavailableReason() != null`), bỏ qua hộp thoại áp wallpaper → không crash, nền trong app vẫn đúng với lựa chọn, hàng cài đặt hiển thị trạng thái hợp lý.

### Bằng chứng Review Focus (Task 7 Step 1, ghi 2026-10-30)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Kill app giữa hai bước đổi cỡ lưới; DataStore/DB lệch | Task 6 Step 7 (e) `force-stop` ngay sau xác nhận, thử nhiều lần; (f) ép lệch bằng xóa `launcher_settings.preferences_pb` ở 5×6 rồi mở lại — Live 4 | **PASS** (người dùng xác nhận 2026-10-30). Thứ tự ghi theo bao hàm + `ensureFitsGrid` |
| 2 | Re-flow với dữ liệu thật (dock đủ, app ẩn, trang trống giữa, khe kéo-thả); trang đang xem | Task 6 Step 7 (a)(b)(c)(j) — Live 4 | **PASS**: số hàng HOME trước = sau, dock và `hidden_apps` không đổi, trang cuối thừa bị xóa, không crash khi đang ở trang cuối |
| 3 | Re-flow đồng thời với sự kiện cài/gỡ app ở nền | Đọc code: `reflowTo`, `ensureFitsGrid`, `placeNewApps`, `removePackages`, `catchUp`, `moveItem`, `setHidden` đều khóa cùng một `Mutex`, mỗi hàm đọc `currentGrid()` một lần trong mutex. Kiểm tay (g): cài mới/gỡ 1 app **sau** khi đổi preset đúng cỡ mới | **PASS ở mức đọc code + (g)**; chưa dựng ca cài/gỡ chạy **cùng lúc** với đang re-flow (khó tạo tay, cửa sổ vài chục ms) |
| 4 | Đổi mức kính → `recreate()` đúng 1 lần, đúng loại kính | Task 4 Step 3 — Oppo A3x (LENS) và Live 4 (CLASSIC_CPU): log `M8` đếm `onCreate` đúng 1 lần mỗi lần đổi, đổi nhanh 5 lần ra đúng mức cuối, xoay/kill giữ mức | **PASS** (người dùng xác nhận 2026-10-29) |
| 5 | Ảnh nền: hủy chọn, ảnh hỏng, máy chặn wallpaper, bỏ qua hộp thoại | Task 5 Step 4 — Oppo A3x và Live 4: chọn ảnh, từng đích wallpaper, nền + dock đổi theo; phát hiện và sửa độ trễ làm mới kính (`pulseGlassRefresh`) | **PASS luồng chính** (người dùng xác nhận 2026-10-29). Các ca lỗi hiếm (ảnh hỏng không giải mã được, máy chặn đặt wallpaper) chỉ được bảo vệ bằng code (`importBackdrop` trả null → Toast giữ ảnh cũ; `wallpaperUnavailableReason()` → Toast, bỏ hộp thoại), **chưa có ghi nhận kiểm tay riêng** |

---

### Task 1: Dữ liệu cài đặt — `GridPreset`, `showLabels` [HOÀN THÀNH 2026-10-29]

**Files:**
- Create: `domain/model/GridPreset.kt`
- Modify: `domain/model/LauncherSettings.kt`, `domain/repository/SettingsRepository.kt`, `data/settings/SettingsKeys.kt`, `data/settings/SettingsRepositoryImpl.kt`

**Interfaces:**
- Produces:
  - `enum class GridPreset(val columns: Int, val rows: Int) { P4X5(4, 5), P4X6(4, 6), P5X6(5, 6) }` với `val capacity: Int` (= cột × hàng) và `companion { val DEFAULT = P4X6; fun fromName(name: String?): GridPreset }` (tên lạ hoặc null → `DEFAULT`).
  - `LauncherSettings.gridPreset: GridPreset = GridPreset.DEFAULT`, `LauncherSettings.showLabels: Boolean = true`.
  - `SettingsRepository.setGridPreset(preset: GridPreset)`, `SettingsRepository.setShowLabels(show: Boolean)` — cả hai là `suspend`, **trả về sau khi `DataStore.edit` đã hoàn tất**.
  - Khóa DataStore: `grid_preset` (string, lưu `GridPreset.name`), `show_labels` (boolean).

- [x] **Step 1: Thêm model + khóa + setter** — theo đúng khuôn của `glass_level_override` (đọc lỗi → mặc định, đã có `Log.w` ở `SettingsRepositoryImpl`).
- [x] **Step 2: Kiểm chứng (Live 4)** — **PASS (người dùng xác nhận 2026-10-29).** build xanh (`gradlew.bat assembleDebug`), cài đè lên bản M7: app chạy y hệt (chưa có UI dùng hai khóa). Máy đang có sẵn DataStore cũ không lỗi (thiếu khóa → mặc định).
- [x] **Step 3: Ghi `docs/AGENTS.md` (nếu có bài học) + Commit** (`feat: add grid preset and show-labels settings`).

---

### Task 2: Bỏ cỡ lưới cứng 4×6 — đọc từ cài đặt [HOÀN THÀNH 2026-10-29]

> Task này **không thêm tính năng nhìn thấy được**; mục tiêu duy nhất là hành vi giữ nguyên với giá trị mặc định (Global Constraints). Hiện có 10 chỗ dùng cứng `GridMetrics.DEFAULT_COLUMNS/DEFAULT_ROWS`; xóa hai hằng này để trình biên dịch chỉ ra hết.

**Files:**
- Modify: `core/ui/GridMetrics.kt`, `ui/home/view/IconCellView.kt`, `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt`, `ui/home/HomePagerAdapter.kt`, `ui/home/HomeActivity.kt` (`updateDockCells`, chỗ tạo `GridMetrics()`), `ui/home/HomeUiState.kt`, `ui/home/HomeViewModel.kt` (dòng dùng `DEFAULT_COLUMNS`/`DEFAULT_ROWS` ở `removeAndCompact` và `planGridOptimistic`), `data/layout/LayoutReconciler.kt` (seed, `moveToHomeInTransaction`, `compactPageAfterRemoval`, `placeNewInTransaction`, `compactHome`), `domain/usecase/BuildDefaultLayoutUseCase.kt`

**Interfaces:**
- Consumes: `LauncherSettings.gridPreset`, `LauncherSettings.showLabels` (Task 1).
- Produces:
  - `GridMetrics(val columns: Int, val rows: Int, val showLabels: Boolean = true)` — **bỏ** `DEFAULT_COLUMNS`/`DEFAULT_ROWS` (giữ `DOCK_SLOTS = 4`). `GridMetrics.Cells` thêm `showLabels: Boolean` để so sánh theo giá trị làm `IconGridView.bindAllCells` bind lại khi đổi nhãn (bài học M5 Task 2: view con giữ dữ liệu suy ra từ kích thước thì phải bind lại khi `Cells` đổi).
  - `HomeUiState.grid: GridPreset = GridPreset.DEFAULT`, `HomeUiState.showLabels: Boolean = true`.
  - `LayoutReconciler` nhận thêm `SettingsRepository` (Hilt constructor) và có `private suspend fun currentGrid(): GridPreset` (= `settings.first().gridPreset`), mọi chỗ cần cột/hàng gọi hàm này; `BuildDefaultLayoutUseCase.invoke` bỏ giá trị mặc định của `columns`/`rows`.

- [x] **Step 1: `GridMetrics`** — thêm `showLabels` vào constructor và `Cells`; khi `showLabels = false`, chiều cao nhãn và khoảng cách trong (`INNER_GAP_DP`) trong `measure()` tính bằng 0 (ô giữ nguyên kích thước, icon vẫn bị chặn trên bởi `ICON_MAX_DP`).
- [x] **Step 2: Vẽ nhãn có điều kiện** — `IconCellView`: `cells.showLabels == false` thì không dựng `StaticLayout`, `contentHeight()` = `iconSize`; `DockView` dùng cùng `Cells` nên căn giữa theo `contentHeight()` như cũ.
- [x] **Step 3: `HomeViewModel`** — thu `settingsRepository.settings.map { it.gridPreset to it.showLabels }.distinctUntilChanged()` vào `HomeUiState.grid`/`showLabels`; `gridStatus` chỉ thành `Ready` sau khi **cả** lần phát đầu của cài đặt **và** của bố cục đã có (tránh nháy 4×6 rồi mới ra cỡ đã lưu). Hai chỗ dùng cứng cột/hàng đổi sang `uiState.value.grid.columns/rows`.
- [x] **Step 4: View** — `HomePagerAdapter`, `IconGridView` và `HomeActivity` tạo `GridMetrics(grid.columns, grid.rows, showLabels)` từ state thay vì `GridMetrics()`; adapter nhận metrics qua cùng đường `submit` với danh sách trang để cả hai cập nhật trong một lần.
- [x] **Step 5: `LayoutReconciler` + `BuildDefaultLayoutUseCase`** — thay các hằng bằng `currentGrid()` (đọc **trong** `mutex`, trước khi vào transaction nếu có thể để không giữ khóa DB trong lúc chờ DataStore).
- [x] **Step 6: Kiểm chứng hồi quy (Live 4, mặc định 4×6)** — build xanh; cài đè; chạy lại bộ kịch bản rút gọn của M3–M7: lưới 4×6 đúng cỡ, vuốt trang, kéo-thả trong trang / giữa trang / lưới↔dock, dấu "−", menu nhấn giữ, Thư viện ứng dụng, gỡ rồi cài lại 1 app (dồn/đặt ô đúng), xoay màn hình và split-screen. Đọc DB (pull đủ 3 file, AGENTS.md) trước và sau: không hàng nào đổi tọa độ. **PASS (người dùng xác nhận 2026-10-29).**
- [x] **Step 7: Ghi `docs/AGENTS.md` + Commit** (`refactor: read grid size and labels from settings`).

---

### Task 3: `SettingsActivity` + lối vào + nhóm mục đơn giản (mặc định, nhãn, app đã ẩn) [HOÀN THÀNH 2026-10-29]

**Files:**
- Create: `ui/settings/SettingsActivity.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsRow.kt`, `ui/settings/SettingsAdapter.kt`
- Create: `res/layout/activity_settings.xml`, `res/layout/item_settings_header.xml`, `res/layout/item_settings_value.xml`, `res/layout/item_settings_switch.xml`, `res/drawable/bg_settings_group_single.xml`, `..._top.xml`, `..._middle.xml`, `..._bottom.xml`
- Modify: `AndroidManifest.xml` (khai `SettingsActivity`, `exported="false"`), `res/layout/activity_home.xml` (thêm `btn_settings` vào `edit_toolbar`, giữa `btn_library` và `btn_done`), `ui/home/HomeActivity.kt` (mở màn Cài đặt), `ui/library/AppLibraryActivity.kt` + `AppLibraryViewModel.kt` (nhận mục khởi đầu), `res/values/strings.xml`, `res/values/dimens.xml`

**Interfaces:**
- Consumes: `SettingsRepository.settings`, `setShowLabels` (Task 1), `DefaultLauncherRepository` (`isDefaultLauncher()`, `createSetDefaultIntent()`), `LayoutRepository.hiddenApps`.
- Produces:
  - `sealed interface SettingsRow { val id: String }` với `Header(id, @StringRes title)`, `Value(id, @StringRes title, value: String, enabled: Boolean = true)`, `Switch(id, @StringRes title, checked: Boolean)`. Adapter nhóm các hàng liên tiếp giữa hai `Header` và chọn nền bo góc `single / top / middle / bottom` theo vị trí trong nhóm.
  - `SettingsUiState(loading: Boolean = true, isDefaultLauncher: Boolean = false, canRequestDefault: Boolean = false, showLabels: Boolean = true, hiddenCount: Int = 0)` — các Task sau thêm trường (không đổi tên).
  - `SettingsViewModel`: `uiState: StateFlow<SettingsUiState>`, `refreshDefaultLauncher()`, `createSetDefaultIntent(): Intent?`, `onShowLabelsChanged(checked: Boolean)`.
  - `AppLibraryActivity.EXTRA_INITIAL_SECTION = "initial_section"` (giá trị `AppLibrarySection.ID_HIDDEN` / `ID_VISIBLE`); `AppLibraryViewModel` đặt `_selectedSectionId` ban đầu từ extra (qua `SavedStateHandle`).

Trạng thái màn: **Default** = danh sách hàng; **Loading** = chưa có lần phát đầu của cài đặt (ẩn danh sách, hiện thanh tiến trình); **Disabled** = hàng "Launcher mặc định" khi đã là mặc định (hiện "Đang là mặc định") hoặc máy không có đường mở bộ chọn; **Error** = ghi cài đặt lỗi → `Toast` + log, công tắc trở lại giá trị thật từ flow. **Empty** không áp dụng.

- [x] **Step 1: Khung màn + layout + dimens** — `MaterialToolbar` có nút quay lại, `RecyclerView` nhóm bo góc (`settings_group_radius` = 16dp, lề nhóm 16dp, hàng cao tối thiểu 56dp, vùng chạm ≥ 48dp); màu nền nhóm lấy từ thuộc tính theme Material (`?attr/colorSurfaceContainer`…) để sáng/tối tự đúng, không thêm `values-night/colors.xml`. Đệm inset thanh hệ thống như `AppLibraryActivity` (tràn viền từ target SDK 35). Tắt `itemAnimator` (tránh nháy khi đổi công tắc).
- [x] **Step 2: Các hàng** — nhóm "Chung": *Launcher mặc định* (giá trị "Đang là mặc định" / "Chưa đặt"; bấm khi chưa mặc định và `canRequestDefault` → mở bằng `registerForActivityResult(StartActivityForResult())` giống `HomeActivity.onSetDefaultClicked`; `onResume` gọi `refreshDefaultLauncher()`); *App đã ẩn* (giá trị = `hiddenCount`; bấm mở `AppLibraryActivity` với `EXTRA_INITIAL_SECTION = ID_HIDDEN`). Nhóm "Màn hình chính": *Hiện nhãn ứng dụng* (công tắc → `onShowLabelsChanged`).
- [x] **Step 3: Lối vào** — `btn_settings` ("Cài đặt", `TonalButton`, `minHeight 48dp`) trong `edit_toolbar`; `HomeActivity` đặt listener `startActivity(Intent(this, SettingsActivity::class.java))`. Chữ ở `strings.xml`. Ngoài plan (theo yêu cầu người dùng): bỏ `home_status` (chữ debug) và nút `btn_set_default` khỏi Home vì đã có trong Cài đặt.
- [x] **Step 4: Kiểm chứng (Live 4)** — (a) vào edit mode thấy nút Cài đặt; bố cục thanh công cụ không tràn trên màn hẹp / chữ lớn. (b) *Hiện nhãn*: tắt → quay về Home: nhãn lưới + dock biến mất, icon không lệch cột, kéo-thả/ẩn vẫn đúng; bật lại → nhãn về; xoay màn hình và split-screen vẫn đúng. (c) *App đã ẩn*: ẩn 2 app, mở Cài đặt thấy "2", bấm vào đúng tab "Đã ẩn". (d) *Launcher mặc định*: đúng trạng thái khi đang là mặc định và khi chưa; bấm nút mở bộ chọn, quay lại hàng cập nhật. (e) Chế độ sáng/tối đều đọc được (tương phản). (f) TalkBack không bắt buộc ở mốc này nhưng mọi hàng có `contentDescription`/text hợp lý. **PASS (người dùng xác nhận 2026-10-29).**
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: add settings screen with default launcher, labels, hidden apps`).

---

### Task 4: Mức kính (Tự động / Ép thấp / Ép cao) [HOÀN THÀNH 2026-10-29]

**Files:**
- Modify: `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsActivity.kt`, `ui/home/HomeActivity.kt` (`setupDock`, `onResume`), `res/values/strings.xml`

**Interfaces:**
- Consumes: `SettingsRepository.setGlassLevelOverride(value: GlassLevelOverride)` (đã có từ M6; **trả về sau khi ghi xong**), `HomeViewModel.currentGlassLevelOverride()`.
- Produces: `SettingsUiState.glassOverride: GlassLevelOverride = GlassLevelOverride.AUTO`; `SettingsViewModel.onGlassOverrideChosen(value: GlassLevelOverride)`; trong `HomeActivity` một trường lưu **mức kính đã dùng khi dựng dock** (`appliedGlassOverride`).

- [x] **Step 1: Hàng "Mức kính"** — hàng `Value` hiển thị tên mức; bấm mở `MaterialAlertDialogBuilder.setSingleChoiceItems` ba lựa chọn (chữ ở `strings.xml`, kèm mô tả ngắn: Tự động theo máy / Ép thấp = card mờ / Ép cao = kính thật). Chọn xong gọi `onGlassOverrideChosen` (ghi trong `viewModelScope`, lỗi → `Toast` + log).
- [x] **Step 2: Home tự tạo lại khi đổi** — `appliedGlassOverride: GlassLevelOverride?` (null lúc đầu) được gán **trong coroutine của `setupDock()`, ngay sau khi đọc `override`** (việc đọc là bất đồng bộ); `onResume` đọc `currentGlassLevelOverride()` (suspend, trong `lifecycleScope`) và chỉ khi `appliedGlassOverride != null` và khác giá trị vừa đọc thì gọi `recreate()` (null = dock chưa dựng xong, bỏ qua để không tạo lại vô ích). Sau `recreate()` giá trị áp = giá trị hiện tại nên không lặp. Không gọi `recreate()` từ `SettingsActivity` (nó không sở hữu dock).
- [x] **Step 3: Kiểm chứng** — **Oppo A3x** (tier LENS): Tự động → Ép thấp: quay về Home, dock thành card mờ (`StaticBlurSurface`); Ép thấp → Ép cao: dock lại kính thật; chọn đúng mức đang dùng: Home không tạo lại. **Live 4** (tier CLASSIC_CPU): Ép cao cho kính thật (chỉ xem chuyển loại, không đo hiệu năng), trả Tự động về card mờ. Thêm log tạm tag `M8` ở `onCreate` đếm số lần tạo: mỗi lần đổi đúng **một** lần; đổi liên tục 5 lần nhanh vẫn đúng mức cuối. Xoay, kill app, mở lại: giữ mức đã chọn. Xóa log tạm sau khi xong. **PASS (người dùng xác nhận 2026-10-29).** Log tạm `M8` đã xóa.
- [x] **Step 4: Ghi `docs/AGENTS.md` + Commit** (`feat: glass level setting with home recreate on change`).

---

### Task 5: Đổi ảnh nền + áp wallpaper hệ thống (hỏi mỗi lần) [HOÀN THÀNH 2026-10-29]

**Files:**
- Modify: `domain/repository/BackdropRepository.kt`, `data/backdrop/BackdropRepositoryImpl.kt`, `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsActivity.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeActivity.kt` (`onResume`), `res/values/strings.xml`

**Interfaces:**
- Consumes: `BackdropRepository.importBackdrop(uri, reqW, reqH)`, `applyAsWallpaper(target, width, height)`, `wallpaperUnavailableReason()`; `WallpaperTarget` (HOME / LOCK / BOTH) và chuỗi `wallpaper_target_*` của M1.
- Produces:
  - `BackdropRepository.backdropVersion(): Long` — `lastModified` của file ảnh nền, `0` nếu chưa có ảnh.
  - `SettingsUiState.hasBackdrop: Boolean`, `SettingsUiState.wallpaperUnavailableReason: String?`.
  - `SettingsViewModel.onImagePicked(uri: Uri, width: Int, height: Int)`, `SettingsViewModel.onWallpaperTargetChosen(target: WallpaperTarget?, width: Int, height: Int)` (`null` = bỏ qua, chỉ đổi nền trong app); sự kiện một lần `SettingsEvent.AskWallpaperTarget`, `SettingsEvent.Message(text)`.
  - `HomeViewModel.reloadBackdropIfChanged(width: Int, height: Int)` — so `backdropVersion()` với giá trị đã hiển thị, đổi thì nạp lại và phát `BackdropStatus.Ready` mới.

- [x] **Step 1: Hàng "Ảnh nền"** — `Value` ("Đã có ảnh" / "Chưa chọn"); bấm mở `PickVisualMedia(ImageOnly)`. Hủy chọn = không làm gì. Ảnh không nhập được (`importBackdrop` trả null) → `Toast` lỗi, giữ ảnh cũ.
- [x] **Step 2: Hộp thoại áp wallpaper** — sau khi nhập thành công, hiện hộp thoại ba đích + "Không áp dụng" (tái dùng chuỗi `wallpaper_dialog_title`, `wallpaper_dialog_skip`, `wallpaper_target_*` của M1). Nếu `wallpaperUnavailableReason() != null`: bỏ qua hộp thoại, `Toast` nêu lý do, nền trong app vẫn đổi. Áp thất bại (`Result.failure`) → `Toast`, nền trong app vẫn đổi.
- [x] **Step 3: Home nạp lại nền** — `HomeActivity.onResume` gọi `viewModel.reloadBackdropIfChanged(w, h)`; khi `render()` gắn bitmap mới phải gọi lại `dockView.setBackdropSource(...)` (bài học M6 Task 2: `StaticBlurSurface` không tự tính lại khi ảnh đến sau).
- [x] **Step 4: Kiểm chứng** — **Oppo A3x** (kính thật) và **Live 4** (card mờ): chọn ảnh mới → hộp thoại → chọn từng đích, mở khóa màn hình / màn Home hệ thống xem wallpaper đúng đích; nền launcher + dock đổi theo. Đo độ trễ làm mới kính trên Oppo (điều ghi ở M6 Task 1): nếu thấy dock còn mờ ảnh cũ quá ~0,5 giây thì bật `GlassSurface.setDynamic(true)` khoảng 1 giây sau khi đổi ảnh rồi trả `false` (giải pháp đã ghi, **chỉ làm nếu thấy lỗi**); ghi kết quả đo vào AGENTS.md. Ca lỗi: hủy bộ chọn; ảnh có viền trong suốt (cắt viền 5a); ảnh rất lớn; máy chặn đặt wallpaper (nếu có) → không crash. **PASS (người dùng xác nhận 2026-10-29).** Đo trên Oppo A3x: lần đầu dock chỉ đổi khi chuyển trang/chạm; sau khi thêm `DockView.pulseGlassRefresh()` (động 1 giây rồi tĩnh) mắt thường không còn thấy độ trễ.
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: change backdrop image from settings with wallpaper prompt`).

---

### Task 6: Cỡ lưới — preset + re-flow có xác nhận [HOÀN THÀNH 2026-10-30]

**Files:**
- Create: `domain/layout/GridReflowPlanner.kt`, `domain/usecase/ChangeGridPresetUseCase.kt`
- Modify: `domain/repository/LayoutRepository.kt`, `data/layout/LayoutRepositoryImpl.kt`, `data/layout/LayoutReconciler.kt`, `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsActivity.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: `GridInsertPlanner.Slot(itemId, x, y)`, `GridInsertPlanner.PageSlots(pageId, slots)`, `GridInsertPlanner.Placement(itemId, pageIndex, x, y)` (đã có từ M7); `SettingsRepository.setGridPreset`; `GridPreset.capacity`.
- Produces:
  - `GridReflowPlanner.plan(pages: List<GridInsertPlanner.PageSlots>, columns: Int, rows: Int): GridReflowPlanner.Plan` với `Plan(placements: List<GridInsertPlanner.Placement>, totalPages: Int)`. Hàm thuần: trải phẳng icon theo (chỉ số trang, `y`, `x`), icon thứ `i` → trang `i / (columns*rows)`, ô `i % (columns*rows)` (cột `ô % columns`, hàng `ô / columns`); `placements` chỉ gồm icon **đổi chỗ**; `totalPages = max(1, ceil(n / (columns*rows)))`; `pageIndex >= pages.size` nghĩa là trang mới. Yêu cầu `columns > 0 && rows > 0`.
  - `LayoutRepository.reflowToGrid(preset: GridPreset)`; `LayoutReconciler.reflowTo(preset: GridPreset)` và `LayoutReconciler.ensureFitsGrid()` (cả hai khóa `mutex`; thân dùng chung là hàm private `reflowInTransaction(preset)`).
  - `ChangeGridPresetUseCase.invoke(target: GridPreset)` (`suspend operator`): đọc preset hiện tại; bằng nhau → không làm gì; `target.capacity < hiện tại.capacity` (thu nhỏ) → `layoutRepository.reflowToGrid(target)` **rồi** `setGridPreset(target)`; ngược lại (mở rộng) → `setGridPreset(target)` **rồi** `reflowToGrid(target)`. Nhờ bất biến bao hàm, mọi thời điểm giữa hai bước icon đều nằm trong lưới hợp lệ của cả hai cỡ.
  - `SettingsUiState.gridPreset: GridPreset`, `SettingsUiState.applyingGrid: Boolean`; `SettingsViewModel.onGridPresetConfirmed(preset: GridPreset)`.

- [x] **Step 1: `GridReflowPlanner`** — viết theo đặc tả trên; không `android.*`; dùng chung kiểu dữ liệu với `GridInsertPlanner`. Ca biên đã định: 0 icon → `totalPages = 1`, `placements` rỗng; icon đã đúng chỗ không nằm trong `placements`.
- [x] **Step 2: `reflowInTransaction`** — trong `db.withTransaction`: đọc `launcher_items` HOME + `home_pages` (sắp theo `rank`); có item `spanX/spanY > 1` thì **không làm gì**, ghi `Log.w` (giống `compactHome`); gọi `GridReflowPlanner`; `itemDao.updateAll` cho icon đổi chỗ (kèm đổi `pageId` khi sang trang khác); tạo trang mới cho `pageIndex >= pages.size` (rank nối tiếp); xóa các trang CUỐI không còn icon nhưng luôn giữ ≥ 1 trang; **không đụng dock và `hidden_apps`**. `ensureFitsGrid()` chỉ gọi `reflowInTransaction(currentGrid())` khi có icon HOME với `gridX >= columns` hoặc `gridY >= rows`.
- [x] **Step 3: Gọi `ensureFitsGrid()`** trong `LayoutRepositoryImpl` ngay trước `reconciler.catchUp(firstApps)` (cùng chỗ khởi động đồng bộ). Đây là lớp bảo hiểm cho ca DataStore và DB lệch nhau (khôi phục sao lưu, thao tác tay), không phải đường chính.
- [x] **Step 4: Use case + repository** — `ChangeGridPresetUseCase` như trên (`@Inject constructor(SettingsRepository, LayoutRepository)`); `LayoutRepositoryImpl.reflowToGrid` ủy quyền `reconciler.reflowTo`.
- [x] **Step 5: UI** — hàng *Cỡ lưới* (giá trị "4 × 6"); bấm mở hộp thoại ba preset; chọn preset khác hiện hộp thoại xác nhận: "Các biểu tượng sẽ được xếp lại theo thứ tự hiện tại và không thể hoàn tác. Áp dụng?" (**Áp dụng** / **Hủy**). Xác nhận → `onGridPresetConfirmed` → `applyingGrid = true` (khóa hàng, hiện tiến trình) → use case → `false`. Lỗi → `Toast` + log, hàng mở lại, giá trị lấy từ flow.
- [x] **Step 6: Trang đang xem** — kiểm `HomeActivity` đặt lại `currentItem` sau `submitList` (đã có từ M3) kẹp trong `0..pages.size-1`; sửa nếu thiếu. Đã có sẵn `setCurrentItem(savedPage.coerceIn(0, displayPages.size - 1), false)` trong `HomeActivity.render()` — không phải sửa.
- [x] **Step 7: Kiểm chứng (Live 4, sao lưu DB trước và so sánh: pull đủ 3 file `launcher.db`, `-wal`, `-shm`)** — (a) 4×6 → 4×5: số trang tăng khi cần, thứ tự đọc giữ nguyên, hết khe; (b) 4×5 → 5×6 và 5×6 → 4×6: số trang giảm, trang cuối thừa bị xóa, ≥ 1 trang; (c) dữ liệu có dock đủ 4 icon, vài app ẩn, một trang trống ở giữa, khe do kéo-thả: **số hàng HOME trước = sau**, dock và `hidden_apps` không đổi; (d) mở lại app sau mỗi lần đổi: không nháy 4×6 trước khi ra cỡ đã lưu; (e) `force-stop` ngay sau khi xác nhận (thử vài lần): mở lại icon luôn thấy được; (f) ép lệch: ở 5×6 kéo một icon sang cột thứ 5 rồi `adb shell run-as gambi.global.os26launcher rm files/datastore/launcher_settings.preferences_pb` (đặt lại preset về 4×6) và mở lại: `ensureFitsGrid` đưa icon ngoài lưới về trong lưới, không mất icon; (g) cài mới và gỡ 1 app sau khi đổi preset: app mới vào ô trống đầu theo cỡ MỚI, gỡ dồn đúng cỡ mới; (h) kéo-thả, dấu "−", menu nhấn giữ hoạt động ở cả ba preset; (i) split-screen hẹp với 5×6: không crash, lưới ẩn theo luật M3 khi ô < 48dp và hiện lại khi rộng ra; (j) đang xem trang cuối rồi đổi sang preset ít trang hơn: không crash. **PASS cả 10 tiêu chí (a)–(j) (người dùng xác nhận 2026-10-30).** Ghi chú: một lần thấy "icon dồn sai / hàng lệch" khi đổi lưới lớn → nhỏ nhưng KHÔNG tái hiện lại được; xem `docs/AGENTS.md` mục "M8a Task 6" để biết cách bật lại log chẩn đoán nếu gặp lần nữa.
- [x] **Step 8: Ghi `docs/AGENTS.md` + Commit** (`feat: grid size presets with reading-order reflow`).

---

### Task 7: Đóng M8a — rà Review Focus, đồng bộ tài liệu [HOÀN THÀNH 2026-10-30]

**Files:**
- Modify (tài liệu): `docs/Launcher-Features.md` (TT#20 → `Một phần`: ghi M8a đã có gì, còn gì), `docs/project_structure.md` (dòng M8 tách thành M8a / M8b / pill lồng; cây file thêm `ui/settings/`, `GridPreset`, `GridReflowPlanner`, `ChangeGridPresetUseCase`), `docs/room-schema.md` (ghi: cỡ lưới nằm ở DataStore, `ensureFitsGrid`, re-flow dùng thứ tự đọc), `docs/AGENTS.md`, file này.

- [x] **Step 1: Rà Review Focus** — mỗi dòng trong "Review Focus" ở đầu file này có bằng chứng ghi cạnh (kịch bản nào, máy nào, kết quả); dòng nào chưa có thì đo bổ sung.
- [x] **Step 2: Đồng bộ tài liệu** như danh sách Files; ghi rõ các mục để sau (Dashboard, TalkBack dấu "−", kiểu icon) ở `Launcher-Features.md`.
- [x] **Step 3: Tick và đóng** — M8a chỉ được tick sau khi người dùng xác nhận toàn bộ trên máy thật. **Người dùng xác nhận đóng M8a 2026-10-30.**
- [x] **Step 4: Commit** (`docs: close M8a and sync project docs`).
