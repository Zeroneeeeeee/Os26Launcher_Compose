# M3 — Lưới icon nhiều trang và dock phẳng

> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans`. Các bước dùng checkbox `- [ ]`.
>
> **Dự án này KHÔNG có test** (A7). Đừng viết unit test, instrumented test, đừng thêm thư viện test. Vòng TDD được thay bằng **kiểm chứng tay trên máy thật**, mỗi task có lệnh cụ thể và kết quả mong đợi. Không tick checkbox khi chưa có người xác nhận.
>
> **KHÔNG dùng `LiquidGlassView` ở mốc này** (A9). Dock là màu đặc bán trong. Mọi việc dùng kính gom vào M6.

**Goal:** Màn hình chính có lưới icon 4×6 nhiều trang vuốt ngang và một dock 4 ô, bấm icon mở được app, và cold start không còn chờ 8 giây trên máy nhiều app.

**Architecture:** `GridMetrics` tính kích thước ô từ kích thước view thật, `IconGridView` (custom `ViewGroup`) layout các ô theo `GridPosition`, một trang là một `IconGridView` trong `ViewPager2`. Bố cục **suy ra mỗi lần mở app** từ danh sách app đã sắp (`BuildDefaultLayoutUseCase`), không lưu — Room là M4, kéo-thả là M7. Nút chặn hiệu năng hiện tại là đọc nhãn tuần tự, nên Task 1 chuyển nó sang song song trước khi vẽ bất cứ thứ gì.

**Tech Stack:** Kotlin, View/XML, custom `ViewGroup`, ViewPager2 1.1.0, RecyclerView 1.4.0, coroutines (`Semaphore` giới hạn song song), Hilt, ViewBinding. Không Compose (B1), không kính (A9).

**Spec:** [../project_structure.md](../project_structure.md) — mục 2 (A1–A9, B1–B8, luật phụ thuộc), mục 3 (`core/ui`, `ui/home/view`, `domain/usecase`), mục 7 (res, quy ước đặt tên, bảng trạng thái màn hình), mục 9 (mốc M3). Tính năng: [../Launcher-Features.md](../Launcher-Features.md) **TT#3**. Khóa dữ liệu dùng lại ở M4: [../room-schema.md](../room-schema.md) mục 3 (`gridX`, `gridY`, `spanX`, `spanY`, `rank`).

## Global Constraints

Áp cho **mọi task**, không nhắc lại ở từng task:

- `minSdk 26`, `targetSdk 37`, Java 11. API từ 27 trở lên phải có nhánh dự phòng cho 26.
- View/XML, ViewBinding, **không** `findViewById`, **không** Compose (B1).
- **Không `LiquidGlassView`, không `core/glass`** (A9) — thuộc M6.
- **Luật phụ thuộc:** `ui → domain ← data`. `domain` không tham chiếu kiểu nằm ở `data` và không import `android.*` trừ `Bitmap` (ngoại lệ đã ghi trong `AppRepository`). Kiểu hình học tự định nghĩa trong `domain/model`.
- **Máy test mặc định là Vsmart Live 4** (A8, Android 11, SDK 30, 87 app). **Không** cần Oppo A3x ở mốc này; chỉ ghi lại chỗ nào cần đối chứng 344 app sau.
- Không áp padding system-bar lên root `home_root` (B2) — nền phải phủ kín dưới các thanh hệ thống. Inset áp cho view con, như M1 Task 5 đã làm.
- Lưới 8dp, vùng chạm tối thiểu 48dp, nhãn phải đọc được trên ảnh nền bất kỳ (mục 7).
- Mọi chữ hiển thị vào `strings.xml`. Mọi số đo vào `dimens.xml`, **không** hardcode trong Kotlin — trừ các hằng của `GridMetrics` (xem Task 2).
- Không xóa file ngoài danh sách ở Task 5. `demo/`, `MainActivity`, `activity_main.xml`, `view_extra_glass.xml` giữ đến sau M5; màn demo phải chạy y như trước.
- Thêm thư viện phải cập nhật `libs.versions.toml` **và** mục 8 của `project_structure.md` trong cùng task. M3 **không** cần thư viện mới.
- Commit message tiếng Anh, `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt.
- Lệnh viết cho PowerShell. `adb shell am force-stop gambi.global.os26launcher` trước mỗi lần thử lại trạng thái khởi động.

## Review Focus

Năm trường hợp spec hàm ý nhưng đường đi hạnh phúc không chạm tới. Mỗi dòng gắn với một bước kiểm chứng trong task sở hữu nó:

1. **App bị gỡ ngay giữa lúc nạp nhãn song song** → một `loadLabel` ném không được làm sập cả danh sách; dòng đó dùng `packageName` làm nhãn dự phòng hoặc bị bỏ, và có `Log.w`. Song song làm cửa sổ này rộng hơn tuần tự, nên đây là rủi ro do chính Task 1 tạo ra. (Task 1)
2. **Nhãn dài và nhiều dấu** ("Bộ công cụ SIM 1", tên app 40 ký tự) → tối đa 2 dòng, ellipsize cuối, **không** đẩy lệch lưới, không che icon hàng dưới. (Task 2)
3. **Số app không chia hết cho 24** (Live 4: 87 app − 4 dock = 83 → 4 trang, trang cuối 11 ô) → trang cuối không vẽ ô rỗng lạ, không crash khi index vượt. (Task 3)
4. **Máy gần như không có app nào mở được** (0 app sau khi trừ dock) → Empty state, và tuyệt đối không chia cho 0 trong `GridMetrics` hay `BuildDefaultLayoutUseCase`. (Task 3)
5. **Cài hoặc gỡ app khi người dùng đang ở trang 3** → lưới cập nhật nhưng **giữ nguyên trang đang xem**, không nhảy về trang 1, không mất vị trí cuộn. (Task 3)

---

## Task 1: Nạp nhãn song song

Task hiệu năng, không đổi giao diện. Làm trước vì mọi thứ còn lại của M3 nằm sau nó trong luồng khởi động.

**Cơ sở số đo (M2, 2026-10-26):** Live 4 87 app ≈ 1,45 s (**~16,7 ms/app**), Oppo A3x 344 app ≈ 7,9 s (**~23 ms/app**). Chi phí tuyến tính theo số app → nghi vấn là `LauncherActivityInfo.getLabel()`, vì nó phải mở `AssetManager` của từng package. `getActivityList` chỉ một lần IPC mỗi profile, `Collator` sort vài trăm phần tử là vài ms. Bước 1 xác nhận, bước 2 sửa.

**Files:**
- Modify: `.../data/apps/LauncherAppsDataSource.kt`
- Modify: `.../data/apps/AppRepositoryImpl.kt`

**Interfaces:**
- Consumes: `AppItem`, `ComponentKey` (M2).
- Produces: `loadApps()` đổi thành `suspend`, giữ nguyên kiểu trả về.

```kotlin
// data/apps/LauncherAppsDataSource.kt
/** Đọc nhãn song song bên trong. Vẫn phải gọi từ luồng nền vì có IPC. */
suspend fun loadApps(): List<AppItem>
```
`AppRepositoryImpl.loadSorted()` đổi thành `private suspend fun loadSorted()`. Chữ ký công khai của `AppRepository` **không đổi**.

- [x] **Bước 1: Đo tách ba đoạn, trên Live 4**

Thêm log tạm tag `M3` quanh ba đoạn trong `loadApps()` + `loadSorted()`: (a) tổng thời gian `getActivityList` của mọi profile, (b) tổng thời gian đọc nhãn, (c) thời gian `sortedWith`. In kèm số app và **ms/app của đoạn (b)**.

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -s M3
```
Ghi lại ba số. Mong đợi (b) chiếm phần lớn. **Nếu (b) không phải thủ phạm, dừng và báo** — cách sửa ở bước 2 sẽ không giúp gì và phải thiết kế lại.

- [x] **Bước 2: Đọc nhãn song song, giới hạn 8 việc cùng lúc**

Giữ danh sách `LauncherActivityInfo` đã lấy được (đừng lấy lại theo key — tìm lại là thêm một lần quét), rồi đọc nhãn trong `coroutineScope { ... map { async { ... } }.awaitAll() }`, bọc bằng `kotlinx.coroutines.sync.Semaphore(8)`.

Hai điều bắt buộc:
- `Semaphore(8)` chứ không phải không giới hạn: 344 coroutine cùng mở `AssetManager` sẽ đổi nút chặn từ CPU sang I/O và có thể tệ hơn. Con số 8 là điểm khởi đầu, bước 3 xác nhận.
- Mỗi lần đọc nhãn bọc `try/catch`: app có thể bị gỡ giữa lúc đọc (Review Focus 1). Trả `packageName` làm nhãn dự phòng, kèm `Log.w`. **Không** để một app làm rỗng cả danh sách.

- [x] **Bước 3: Đo lại và so bằng ms/app**

Cùng lệnh ở bước 1. Đạt khi ms/app của đoạn (b) **giảm ít nhất một nửa** và tổng thời gian trên Live 4 **dưới 800 ms**. Nếu chưa đạt, thử `Semaphore(4)` và `Semaphore(16)`, ghi cả ba số rồi chọn.

Ghi vào báo cáo con số dự đoán cho Oppo: `ms/app mới × 344`. Đó là số sẽ đối chứng khi có máy, **không** chặn M3.

- [x] **Bước 4: Kiểm chứng Review Focus 1 — app bị gỡ giữa lúc nạp nhãn**

Khó bắt đúng nhịp, nên làm theo cách tất định: tạm sửa nhánh đọc nhãn để ném `RuntimeException` cho **một** package cố định, chạy, xác nhận danh sách vẫn đủ các app còn lại và dòng đó có nhãn dự phòng + `Log.w`. Hoàn nguyên đoạn sửa tạm.

- [x] **Bước 5: Commit**

```powershell
git add -A
git commit -m "perf: load app labels in parallel"
```

---

## Task 2: `GridMetrics`, `IconCellView`, `IconGridView` — một trang

**Files:**
- Create: `.../domain/model/GridPosition.kt`
- Create: `.../core/ui/GridMetrics.kt`
- Create: `.../ui/home/view/IconCellView.kt`
- Create: `.../ui/home/view/IconGridView.kt`
- Modify: `res/values/dimens.xml` (tạo nếu chưa có), `res/values/strings.xml`
- Modify: `res/layout/activity_home.xml`, `.../ui/home/HomeActivity.kt` (gắn tạm **một** trang để xem được)

**Interfaces:**
- Consumes: `AppItem`, `AppRepository.icon(key, sizePx)`, `AppRepository.launch(key, source)` (M2).
- Produces:

```kotlin
// domain/model/GridPosition.kt
/** Ô trong lưới, tính từ góc trên-trái. spanX/spanY để sẵn cho widget và thư mục (B8). */
data class GridPosition(val x: Int, val y: Int, val spanX: Int = 1, val spanY: Int = 1)

// core/ui/GridMetrics.kt
class GridMetrics(val columns: Int = DEFAULT_COLUMNS, val rows: Int = DEFAULT_ROWS) {

    data class Cells(
        val cellWidth: Int, val cellHeight: Int,
        val iconSize: Int,
        val gutterX: Int, val gutterY: Int,
        val paddingLeft: Int, val paddingTop: Int
    )

    /** Tính kích thước ô từ kích thước view THẬT. Trả null nếu view chưa được layout hoặc nhỏ hơn mức tối thiểu. */
    fun measure(widthPx: Int, heightPx: Int, density: Float): Cells?

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val DOCK_SLOTS = 4
    }
}

// ui/home/view/IconCellView.kt
class IconCellView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    fun bind(item: AppItem, icon: Bitmap?, cells: GridMetrics.Cells)
}

// ui/home/view/IconGridView.kt
class IconGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    var onAppClick: ((AppItem, ScreenRect) -> Unit)? = null
    /** Đặt nội dung một trang. Gọi lại được nhiều lần, tái dùng view con. */
    fun setItems(items: List<PlacedApp>, metrics: GridMetrics)
}
```

`PlacedApp` do Task 3 định nghĩa; Task 2 có thể dùng `List<Pair<GridPosition, AppItem>>` tạm rồi đổi ở Task 3, **hoặc** định nghĩa `PlacedApp` ngay tại Task 2 theo đúng chữ ký ở Task 3 — chọn cách hai, ít sửa hơn.

**Quyết định đã chốt (2026-10-26):** 4 cột × 6 hàng, dock 4 ô, **có nhãn** dưới icon. Ba hằng số này là nguồn duy nhất, đặt trong `GridMetrics.Companion`. **Chưa** đọc từ DataStore — màn cài đặt là M8, lúc đó mới nối `grid_columns`/`grid_rows`/`show_labels` vào. Đừng thêm khóa DataStore ở mốc này.

- [x] **Bước 1: `GridPosition` và `GridMetrics`**

Luật tính trong `measure`:
- `cellWidth = (widthPx - 2*paddingLeft - (columns-1)*gutterX) / columns`, tương tự cho chiều dọc.
- `iconSize` kẹp trong `[48dp, 64dp]`, và không lớn hơn `cellHeight - labelHeight - 4dp`.
- Mọi khoảng cách làm tròn về bội số 4dp (lưới 8dp cho khoảng cách lớn, 4dp cho khoảng cách trong ô).
- Trả **null** khi `widthPx <= 0`, `heightPx <= 0`, `columns <= 0`, `rows <= 0`, hoặc `cellWidth < 48dp`. Không bao giờ chia cho 0 (Review Focus 4).

- [x] **Bước 2: `IconCellView`**

Là `View` tự vẽ, **không** phải `ViewGroup` chứa `ImageView` + `TextView`: 24 ô mỗi trang × 4 trang là gần 200 view nếu dùng cách kia, và M6 sẽ cần vẽ ô này vào bitmap.

Vẽ: bitmap icon ở giữa phần trên, nhãn `StaticLayout` tối đa **2 dòng**, `TextUtils.TruncateAt.END`, canh giữa. Nhãn phải đọc được trên ảnh nền bất kỳ → `setShadowLayer` (bán kính 4dp, màu đen 60%). Tương phản không kiểm soát được nên bóng là bắt buộc, không phải trang trí.

- [x] **Bước 3: `IconGridView`**

`onMeasure` lấy kích thước cha, gọi `metrics.measure(...)`, `null` thì báo `0×0` và thôi. `onLayout` đặt từng `IconCellView` theo `GridPosition` và `Cells`. `setItems` tái dùng view con đang có (thêm/bớt cho khớp số lượng) thay vì `removeAllViews()` mỗi lần — Review Focus 5 phụ thuộc vào điều này.

Click: `onAppClick` truyền `ScreenRect` lấy từ `getLocationOnScreen` của ô vừa bấm, để `AppRepository.launch` dựng được animation từ đúng icon.

- [x] **Bước 4: Gắn tạm một trang vào `activity_home.xml`**

Thêm `IconGridView` id `icon_grid` vào `home_root`, **sau** `backdrop_image` (B2), chiếm phần trên màn hình. `HomeActivity` nạp 24 app đầu từ `HomeUiState.apps` (trường tạm của M2) vào đó. `debug_app_list` **giữ nguyên** ở task này để còn đối chứng nhãn và icon; Task 5 mới xóa.

- [x] **Bước 5: Kiểm chứng trên Live 4**

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
```
Đạt khi: 24 icon xếp đúng 4 cột × 6 hàng · icon và nhãn không tràn khỏi ô · bấm một icon mở đúng app · lưới nằm trên ảnh nền và ảnh nền vẫn phủ kín màn.

- [x] **Bước 6: Kiểm chứng Review Focus 2 — nhãn dài**

Trong danh sách Live 4 tìm nhãn dài nhất (có "Bộ công cụ SIM 1", "Google Play Store"). Nếu chưa đủ dài thì tạm đổi `bind` để nối thêm chữ cho một ô, xem rồi hoàn nguyên. Đạt khi: nhãn cắt ở 2 dòng có dấu `…` · không ô nào đẩy ô khác lệch · không chữ nào chồng lên icon hàng dưới · chụp màn hình lưu kèm báo cáo (`adb exec-out screencap -p > grid.png`).

- [x] **Bước 7: Commit**

```powershell
git add -A
git commit -m "feat: add grid metrics, icon cell and icon grid view"
```

---

## Task 3: Nhiều trang, chỉ báo trang, bố cục mặc định

**Files:**
- Create: `.../domain/model/HomePage.kt` (gồm `PlacedApp`)
- Create: `.../domain/usecase/BuildDefaultLayoutUseCase.kt`
- Create: `.../ui/home/HomePagerAdapter.kt`
- Create: `.../ui/home/view/PageIndicatorView.kt`
- Modify: `.../ui/home/HomeUiState.kt`, `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeActivity.kt`, `res/layout/activity_home.xml`

**Interfaces:**
- Consumes: `IconGridView.setItems`, `GridMetrics` (Task 2); `AppRepository.apps` (M2).
- Produces:

```kotlin
// domain/model/HomePage.kt
data class PlacedApp(val position: GridPosition, val app: AppItem)
data class HomePage(val index: Int, val items: List<PlacedApp>)

// domain/usecase/BuildDefaultLayoutUseCase.kt
class BuildDefaultLayoutUseCase @Inject constructor() {
    /**
     * Xếp [apps] (ĐÃ sắp theo nhãn) vào các trang [columns]×[rows], điền trái→phải rồi trên→dưới.
     * App nằm trong [dock] bị loại khỏi lưới. Trả list rỗng nếu không còn app nào.
     */
    operator fun invoke(
        apps: List<AppItem>,
        dock: List<AppItem>,
        columns: Int = GridMetrics.DEFAULT_COLUMNS,
        rows: Int = GridMetrics.DEFAULT_ROWS
    ): List<HomePage>
}
```
- Produces trên state: `HomeUiState` **thay** `apps: List<AppItem>` bằng `pages: List<HomePage>` + `gridStatus: GridStatus`, với `enum class GridStatus { Loading, Empty, Ready }` nằm cùng file `HomeUiState.kt` (như `BackdropStatus` đang làm).

- [x] **Bước 1: `HomePage`, `PlacedApp`, `BuildDefaultLayoutUseCase`**

`columns <= 0` hoặc `rows <= 0` → trả list rỗng, không ném, không chia (Review Focus 4). `apps` rỗng sau khi trừ dock → trả list rỗng, ViewModel chuyển `gridStatus = Empty`.

- [x] **Bước 2: `HomePagerAdapter`**

`RecyclerView.Adapter` với `ViewHolder` bọc một `IconGridView` — **không** dùng Fragment: `FragmentStateAdapter` kéo theo `FragmentManager` cho mỗi trang, và M6 cần kiểm soát chính xác thứ tự vẽ của trang để kính chụp nền đúng.

`DiffUtil` so theo `HomePage.index`. Icon nạp trong `onBindViewHolder` bằng một job gắn với `ViewHolder`, **cancel job cũ** trong `onViewRecycled` — nếu không, vuốt nhanh sẽ gán icon của trang cũ vào trang mới (đúng bẫy đã gặp ở M2 Task 3 với `AppListAdapter`, xem `AGENTS.md`).

- [x] **Bước 3: `PageIndicatorView`**

`View` tự vẽ các chấm, `fun setPageCount(count: Int)` và `fun setSelected(index: Int, offset: Float)`. Nhận `offset` để chấm chuyển mượt theo lúc vuốt chứ không nhảy, lấy từ `ViewPager2.OnPageChangeCallback.onPageScrolled`.

- [x] **Bước 4: Nối vào `HomeViewModel` và `HomeActivity`**

ViewModel: thu `appRepository.apps`, gọi `BuildDefaultLayoutUseCase`, phát `pages` + `gridStatus`. `gridStatus = Loading` khi chưa có dữ liệu lần đầu.

Activity: `ViewPager2` id `home_pager` thay chỗ `icon_grid` tạm của Task 2, `PageIndicatorView` id `page_indicator` dưới pager và trên dock.

**Giữ trang đang xem khi danh sách đổi** (Review Focus 5): trước khi `submitList`, lưu `home_pager.currentItem`; sau khi adapter cập nhật, đặt lại bằng `setCurrentItem(min(savedIndex, itemCount - 1), false)`. `false` để không chạy animation vuốt.

- [x] **Bước 5: Kiểm chứng trên Live 4 — đường đi chính**

Đạt khi: Live 4 (87 app − 4 dock = 83) ra **4 trang**, ba trang đầu 24 ô, **trang cuối 11 ô** · vuốt ngang qua đủ 4 trang, không trang nào trống hay trùng app · chấm chỉ báo đúng số trang và chuyển mượt khi vuốt · vuốt nhanh qua lại 10 lần, không icon nào nhảy sai trang (Review Focus 3 và bẫy icon của M2).

- [x] **Bước 6: Kiểm chứng Review Focus 5 — cài/gỡ app khi đang ở trang 3**

Vuốt tới trang 3, **để nguyên màn hình đó**, rồi từ máy tính:
```powershell
adb install <apk bất kỳ>
adb uninstall <package vừa cài>
```
Đạt khi: lưới cập nhật và **vẫn đang ở trang 3**, không nhảy về trang 1, không nháy trắng cả pager.

- [x] **Bước 7: Kiểm chứng Review Focus 4 — gần như không có app**

Không xóa app thật. Tạm chặn nguồn: sửa `HomeViewModel` cho `BuildDefaultLayoutUseCase` nhận `emptyList()` một lần, chạy, xác nhận `gridStatus = Empty` hiện chữ trong `strings.xml` và **không** crash, `adb logcat -b crash` trống. Hoàn nguyên.

- [x] **Bước 8: Commit**

```powershell
git add -A
git commit -m "feat: add paged home grid with page indicator"
```

---

## Task 4: Dock phẳng 4 ô

**Files:**
- Create: `.../domain/model/DockRole.kt`
- Create: `.../data/apps/DefaultAppResolver.kt`
- Create: `.../domain/usecase/PickDefaultDockUseCase.kt`
- Create: `.../ui/home/view/DockView.kt`
- Modify: `app/src/main/AndroidManifest.xml` (thêm `<intent>` vào khối `<queries>`)
- Modify: `.../di/RepositoryModule.kt` hoặc `AppModule.kt` (nối resolver), `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeUiState.kt`, `res/layout/activity_home.xml`, `.../ui/home/HomeActivity.kt`

**Interfaces:**
- Consumes: `AppItem`, `ComponentKey`, `IconCellView` (Task 2), `GridMetrics.DOCK_SLOTS`.
- Produces:

```kotlin
// domain/model/DockRole.kt
enum class DockRole { PHONE, MESSAGES, BROWSER, CAMERA }

// domain/usecase/PickDefaultDockUseCase.kt
class PickDefaultDockUseCase @Inject constructor(private val resolver: DefaultAppResolverPort) {
    /** 4 app mặc định cho dock. Vai trò nào không tìm được thì bù bằng app đầu bảng chữ cái chưa dùng. */
    operator fun invoke(apps: List<AppItem>, slots: Int = GridMetrics.DOCK_SLOTS): List<AppItem>
}

/** Cổng ở domain để usecase không phụ thuộc data (luật phụ thuộc). */
interface DefaultAppResolverPort {
    fun resolve(role: DockRole): ComponentKey?
}
```
Đặt `DefaultAppResolverPort` trong `domain/repository/`, impl `DefaultAppResolver` trong `data/apps/` và `@Binds` như các repository khác.
- Produces trên state: `HomeUiState.dock: List<AppItem> = emptyList()`.

- [x] **Bước 1: `DefaultAppResolver` và 4 intent trong `<queries>`**

Mỗi vai trò một intent, `packageManager.resolveActivity(intent, MATCH_DEFAULT_ONLY)`:

| Vai trò | Intent |
|---|---|
| PHONE | `Intent(Intent.ACTION_DIAL)` |
| MESSAGES | `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)` |
| BROWSER | `Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)` |
| CAMERA | `Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)` |

Thêm 4 khối `<intent>` tương ứng vào `<queries>` đang có, kèm comment nói rõ dùng cho dock mặc định. **Giả thuyết cần kiểm chứng ở bước 4:** khối MAIN/LAUNCHER của M2 đã làm mọi package mở được trở nên thấy được, nên `resolveActivity` lẽ ra tìm được mà không cần 4 khối này. Làm theo thứ tự: thử **không** thêm 4 khối trước, chỉ thêm nếu `resolve` trả null.

Trả `null` khi không tìm thấy hoặc khi gặp `android.permission.*` resolver của hệ thống (một số ROM trả activity trung gian). Bọc `try/catch`.

- [x] **Bước 2: `PickDefaultDockUseCase`**

Lấy theo thứ tự PHONE, MESSAGES, BROWSER, CAMERA; vai trò nào `null` hoặc trỏ tới app không có trong `apps` thì bỏ qua. Thiếu bao nhiêu ô thì bù bằng app đầu bảng chữ cái **chưa** nằm trong dock. Không bao giờ trả quá `slots` phần tử, và không trả trùng.

- [x] **Bước 3: `DockView`**

`ViewGroup` xếp ngang `slots` ô `IconCellView` (tái dùng y nguyên view của Task 2, **không** viết lớp ô thứ hai), nền `#80000000` bo góc 28dp vẽ bằng `GradientDrawable` hoặc `drawRoundRect`.

Ghi comment ngay đầu file:
```kotlin
// Nền phẳng (màu đặc bán trong) là TẠM theo A9: M6 thay bằng GlassSurface.
// Đừng thêm LiquidGlassView ở đây.
```
Chiều cao và khoảng cách lấy từ `GridMetrics.Cells`, không hardcode.

- [x] **Bước 4: Kiểm chứng trên Live 4**

Đạt khi: dock có đúng 4 icon, nằm dưới `page_indicator`, trên thanh điều hướng (inset của M1) · bấm mỗi icon mở đúng app · 4 app trong dock **không** xuất hiện lại trong lưới.

Ghi vào báo cáo: vai trò nào `resolve` ra app nào, vai trò nào `null` phải bù bằng chữ cái, và **4 khối `<intent>` có cần thiết hay không** (thử cả hai chiều). Đây là dữ kiện cho `AGENTS.md`.

- [x] **Bước 5: Commit**

```powershell
git add -A
git commit -m "feat: add flat dock with default app slots"
```

---

## Task 5: Trạng thái Loading/Empty, xóa danh sách debug, tài liệu

**Files:**
- Modify: `.../ui/home/HomeActivity.kt`, `.../ui/home/HomeUiState.kt`, `res/layout/activity_home.xml`, `res/values/strings.xml`
- Delete: `.../ui/debug/AppListAdapter.kt`, `res/layout/item_debug_app.xml`
- Modify: `../project_structure.md`, `../Launcher-Features.md`, `../AGENTS.md`, `M3-icon-grid.md` (file này)

- [x] **Bước 1: Render ba trạng thái lưới**

`GridStatus.Loading` → hiện một `ProgressBar` id `grid_loading` ở giữa, ẩn pager và dock. `Empty` → hiện chữ từ `strings.xml`, ẩn pager. `Ready` → hiện pager, indicator, dock.

Trạng thái `Loading` là lý do Task 1 tồn tại: sau Task 1 nó chỉ nhấp nháy dưới 800 ms trên Live 4, nhưng vẫn phải có, vì máy nhiều app sẽ thấy nó lâu hơn.

- [x] **Bước 2: Xóa danh sách debug của M2**

Đã được duyệt trước trong plan M2 Task 3 ("TẠM: xóa ở M3"), nên không cần xin lại:

| Mục | Việc |
|---|---|
| `ui/debug/AppListAdapter.kt` | xóa file |
| `res/layout/item_debug_app.xml` | xóa file |
| `debug_app_list`, `btn_toggle_app_list` trong `activity_home.xml` | xóa 2 view |
| `HomeUiState.apps` | xóa trường (đã thay bằng `pages` + `dock`) |
| `ui/debug/` | xóa thư mục nếu đã rỗng |

Sau khi xóa, `grep -rn "debug_app_list\|AppListAdapter\|btn_toggle_app_list" app/src` phải không ra gì.

- [x] **Bước 3: Kiểm chứng tổng trên Live 4**

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -b crash
```
Đạt khi: mở app thấy Loading rất ngắn rồi ra lưới · 4 trang + indicator + dock · bấm icon ở lưới và ở dock đều mở đúng app · Back không làm gì (M1) · ảnh nền phủ kín · xoay màn hình rồi xoay lại: lưới vẫn đúng 4×6 (nếu lệch thì ghi lại, **đừng sửa** — thuộc M5) · `logcat -b crash` trống · màn demo (icon "Demo kính") vẫn chạy như trước.

- [x] **Bước 4: Cập nhật tài liệu**

`project_structure.md`: tick M3 ở mục 9 kèm ngày và máy · mục 3 đánh dấu `core/ui/GridMetrics.kt`, `ui/home/view/*`, `domain/usecase/*` đã có · ghi `ui/debug/` đã xóa.

`Launcher-Features.md`: TT#3 sang **Xong (M3)**, ghi rõ chỉ đọc, chưa kéo-thả (M7), chưa lưu bố cục (M4), dock phẳng chưa có kính (M6).

`AGENTS.md` — những thứ không có ở đâu khác:
- **ms/app trước và sau khi nạp nhãn song song**, trên Live 4, và giá trị `Semaphore` đã chọn cùng hai giá trị đã thử. Kèm số dự đoán cho Oppo 344 app để lần sau đối chứng.
- Ba số của phép đo tách ở Task 1 bước 1 (getActivityList / đọc nhãn / sort) — để lần sau không phải đo lại từ đầu.
- Vai trò dock nào `resolveActivity` tìm được, và **4 khối `<intent>` có cần hay không**.
- Bẫy đã gặp khi viết custom `ViewGroup` (`onMeasure`/`onLayout`, `IconCellView` tự vẽ thay vì ghép view con) và cách giữ trang khi `submitList`.

- [x] **Bước 5: Commit**

```powershell
git add -A
git commit -m "feat: add grid loading state and remove debug app list"
```

---

## Checklist

- [x] **Task 1** Nạp nhãn song song — tổng dưới 800ms đạt (531–558ms), ms/app giảm ~24% (chưa đạt 50%, xem Ruling trong code); kiểm chứng app gỡ giữa lúc đọc nhãn đạt
- [x] **Task 2** `GridMetrics` + `IconCellView` + `IconGridView` — 24 icon đúng 4×6, nhãn dài 2 dòng + ellipsize đúng (sau khi sửa lỗi textAlign đè StaticLayout), bấm mở app đúng
- [x] **Task 3** 4 trang vuốt được + chỉ báo trang (đã thêm inset tránh thanh điều hướng che), cài/gỡ app không mất trang đang xem, gridStatus Loading→Empty/Ready đúng (phân biệt được giá trị khởi tạo stateIn và dữ liệu thật)
- [x] **Task 4** Dock 4 ô phẳng, mở được app, không trùng app với lưới
- [x] **Task 5** Loading/Empty, xóa danh sách debug, cập nhật 3 tài liệu

## M3 cố tình KHÔNG làm

| Không làm | Thuộc mốc |
|---|---|
| `core/glass`, `GlassTier`, `GlassSurface`, kính cho dock, phép đo 4b | **M6** (A9 — mốc kính duy nhất) |
| Room, lưu bố cục, `app/schemas/` | M4 |
| Kéo-thả, chế độ chỉnh sửa, ẩn icon | M7 |
| Nhấn giữ icon, shortcut, gỡ app từ launcher | M7 |
| Đọc cỡ lưới từ DataStore, màn cài đặt | M8 |
| Thư mục, App Library, kiểu icon, widget | G2 |
| Xoay màn hình, sáng/tối, đo lại inset | M5 |
| Cache icon hoặc nhãn xuống đĩa | chỉ khi Task 1 bước 3 không đạt |
| Xóa `demo/`, `MainActivity`, `activity_main.xml` | sau M5 |
