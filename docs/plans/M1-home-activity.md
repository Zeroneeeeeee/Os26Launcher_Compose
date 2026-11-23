# M1 — HomeActivity, HOME intent-filter, launcher mặc định, di trú backdrop

> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans` để thực thi từng task. Các bước dùng checkbox `- [ ]` để theo dõi.
>
> **Dự án này KHÔNG có test** (quyết định A7, chốt 2026-10-16). Đừng viết unit test, instrumented test, và đừng thêm thư viện test — nếu thấy thiếu test thì đó là có chủ ý. Vòng TDD được thay bằng **kiểm chứng tay trên máy thật**, và mỗi task có bước kiểm chứng với lệnh cụ thể cùng kết quả mong đợi. Không tick checkbox khi chưa có người xác nhận.

**Goal:** Launcher có màn hình chính thật nhận HOME intent, vẽ được ảnh nền của chính nó, biết mình có đang là launcher mặc định hay không, và hỏi áp wallpaper đúng một lần.

**Architecture:** `HomeActivity` (Activity duy nhất giữ HOME) + `HomeViewModel` phát một `HomeUiState` qua `StateFlow` (A4). Mọi truy cập dữ liệu đi qua interface trong `domain/repository`, Hilt nối impl ở `di/RepositoryModule`. Code đọc/ghi ảnh nền và áp wallpaper được **di trú nguyên vẹn** từ `demo/` sang `data/backdrop/` rồi bọc sau `BackdropRepository` — không viết lại logic đã chạy được trên hai máy.

**Tech Stack:** Kotlin, Android View/XML (không Compose), ViewBinding, Hilt 2.60.1, KSP 2.3.11, lifecycle 2.11.0, DataStore Preferences 1.2.1, AGP 9.3.3 + KGP 2.2.10 (built-in Kotlin), Gradle 9.5.0, minSdk 26 / targetSdk 37.

**Spec:** [../project_structure.md](../project_structure.md) — mục 2 (ràng buộc B1–B8, quyết định A1–A7), mục 3 (cây thư mục), mục 4 (manifest launcher), mục 7 (res và trạng thái màn hình), mục 9 (mốc M1), mục 10 (rủi ro), mục 11 điểm 4. Tính năng: [../Launcher-Features.md](../Launcher-Features.md) TT#1, TT#6. Hành vi wallpaper: [../LiquidGlass-Demo.md](../LiquidGlass-Demo.md) "Quyết định 2".

## Global Constraints

Áp cho **mọi task**, không nhắc lại ở từng task:

- `minSdk 26`, `targetSdk 37`, `compileSdk 37`, Java source/target 11. Mọi API từ 27 trở lên phải có nhánh dự phòng cho 26.
- UI là **View/XML**, không Compose (B1). ViewBinding đã bật — màn mới dùng ViewBinding, **không** `findViewById`.
- Layout có kính: nền phải là view em đứng **trước** kính trong một parent chồng lớp (`FrameLayout`) (B2). **Không** áp padding system-bar lên root; áp inset cho từng view con (B2).
- Không thêm thư viện mới. Nếu buộc phải thêm, cập nhật `gradle/libs.versions.toml` và mục 8 của `project_structure.md` trong cùng task.
- **Không xóa file** ngoài danh sách đã duyệt trong task 1. `demo/DemoBackdropView.kt`, `MainActivity.kt`, `activity_main.xml`, `view_extra_glass.xml` **giữ nguyên** đến sau M5.
- Màn demo (`MainActivity`) phải chạy **y như trước** sau mỗi task. Đây là mốc đối chứng hiệu năng duy nhất đang có.
- Mọi chữ hiển thị vào `res/values/strings.xml`, không hardcode trong Kotlin.
- Kiểm chứng tay trên **Oppo A3x** (Android 14, SDK 34, tier 34) và **Vsmart Live 4** (Android 11, SDK 30, tier 30). Task nào chỉ cần một máy thì ghi rõ.
- Commit message **tiếng Anh**, dạng `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt.
- Máy phát triển là Windows: lệnh viết cho PowerShell (`.\gradlew.bat`).

## Review Focus

Năm trường hợp spec hàm ý nhưng đường đi hạnh phúc của các task không chạm tới. Mỗi dòng đã được gắn một bước kiểm chứng trong task sở hữu nó:

1. **Chưa từng chọn ảnh nền** → HomeActivity phải hiện empty state có nút chọn ảnh, không được là màn hình đen. (Task 2)
2. **Bản sao ảnh trong `filesDir` bị xóa hoặc hỏng giữa hai lần mở app** (`hasSaved()` trả true nhưng decode ra null) → rơi về empty state, không crash. (Task 2)
3. **ROM không có đường mở màn chọn launcher mặc định** (RoleManager không cho request `ROLE_HOME`, hoặc không có `ACTION_HOME_SETTINGS`) → nút phải ẩn hoặc báo, tuyệt đối không `ActivityNotFoundException`. (Task 3)
4. **Máy không cho đặt wallpaper** (`isWallpaperSupported` hoặc `isSetWallpaperAllowed` false — có ROM và chính sách doanh nghiệp chặn) → báo lý do, không ném. (Task 4)
5. **Người dùng đổi sang launcher khác rồi quay lại** → trạng thái cập nhật ở `onResume`, và app **không** tự khôi phục wallpaper cũ (Quyết định 2). (Task 3)

---

## Task 1: Di trú backdrop sang `data/backdrop` và bọc sau `BackdropRepository`

Không đổi hành vi gì. Mục tiêu là chuyển code đã chạy được sang đúng chỗ trong kiến trúc và cho nó một interface, để Task 2 dùng mà không chạm vào chi tiết ảnh.

**Files:**
- Move (dùng `git mv` để giữ history): `app/src/main/java/gambi/global/os26launcher/demo/{BackdropSource,PickedImageSource,SystemWallpaperSource,WallpaperApplier}.kt` → `.../data/backdrop/` (4 file)
- Create: `.../domain/repository/BackdropRepository.kt`
- Create: `.../data/backdrop/BackdropRepositoryImpl.kt`
- Modify: `.../MainActivity.kt` (4 dòng import, hiện là dòng 29–32)
- Modify: `.../di/RepositoryModule.kt` (thêm một `@Binds`)
- **Không** di chuyển: `demo/DemoBackdropView.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, qualifier `@IoDispatcher` (đã có từ M0).
- Produces:

```kotlin
// domain/repository/BackdropRepository.kt
interface BackdropRepository {
    fun hasBackdrop(): Boolean
    suspend fun loadBackdrop(reqW: Int, reqH: Int): Bitmap?
    suspend fun importBackdrop(uri: Uri, reqW: Int, reqH: Int): Bitmap?
    /** Lý do máy này không đặt được wallpaper, null nếu đặt được. */
    fun wallpaperUnavailableReason(): String?
    suspend fun applyAsWallpaper(target: WallpaperTarget, width: Int, height: Int): Result<Unit>
}
```

`WallpaperApplier.Target` (HOME / LOCK / BOTH) giữ nguyên như hiện tại, chỉ đổi package; demo vẫn dùng nó. **Ruling 2026-10-16:** interface của `domain` dùng `domain/model/WallpaperTarget` (enum HOME / LOCK / BOTH, không có chữ hiển thị) thay vì `WallpaperApplier.Target`, vì plan gốc làm `domain` và `ui` import `data`, vi phạm luật phụ thuộc ở mục 2 của `project_structure.md`. `BackdropRepositoryImpl` đổi qua lại giữa hai enum. Rủi ro nếu sai: đổi lại 3 file nhỏ.

**Quyết định phạm vi:** mục 3 của spec có `core/bitmap/{BitmapScaler,ExifReader,TransparentTrimmer}` tách từ `PickedImageSource`. **M1 không tách.** Hiện chỉ có một người dùng các hàm đó, tách ra là thêm 3 file và 3 cơ hội sai mà không ai được lợi. Việc tách để đến khi `StaticBlurSurface` (M6) cần cắt và làm mờ — lúc đó mới có người dùng thứ hai.

- [x] **Bước 1: `git mv` 4 file sang `data/backdrop/`, sửa dòng `package` trong từng file**

```powershell
cd E:\SourceTree\IosLauncher\app\src\main\java\gambi\global\os26launcher
git mv demo\BackdropSource.kt data\backdrop\BackdropSource.kt
git mv demo\PickedImageSource.kt data\backdrop\PickedImageSource.kt
git mv demo\SystemWallpaperSource.kt data\backdrop\SystemWallpaperSource.kt
git mv demo\WallpaperApplier.kt data\backdrop\WallpaperApplier.kt
```

`package gambi.global.os26launcher.demo` → `package gambi.global.os26launcher.data.backdrop` trong cả 4 file. Giữ nguyên mọi comment KDoc — chúng ghi lý do của Quyết định 1, 2, 5a và vẫn đúng. Sửa 4 dòng import trong `MainActivity.kt` cho khớp package mới.

- [x] **Bước 2: Tạo `BackdropRepository` đúng như khối Interfaces ở trên**

- [x] **Bước 3: Tạo `BackdropRepositoryImpl`**

Chữ ký: `@Singleton class BackdropRepositoryImpl @Inject constructor(@ApplicationContext context: Context, @IoDispatcher private val io: CoroutineDispatcher) : BackdropRepository`.

Bên trong giữ một `PickedImageSource(context)` và gọi `WallpaperApplier`. Ba việc cần đúng:
- Mọi hàm `suspend` chạy trong `withContext(io)` — thay cho `Thread { }` mà demo đang dùng.
- `applyAsWallpaper` bắt `IOException` và `SecurityException`, trả `Result.failure`; **không** để ném ra ngoài.
- `SystemWallpaperSource` **không** được nối vào impl: nó là phương án B đang gác (B6). Chỉ đổi package, để nguyên không ai gọi.

- [x] **Bước 4: Thêm `@Binds` vào `di/RepositoryModule.kt`** cho `BackdropRepositoryImpl` → `BackdropRepository`, không scope (scope đã ở `@Singleton` trên impl).

- [x] **Bước 5: Build và chạy demo để chứng minh không đổi hành vi**

```powershell
.\gradlew.bat assembleDebug
```
Mong đợi: BUILD SUCCESSFUL. Cài lên Oppo A3x, mở app, kiểm tra 4 thứ của demo vẫn chạy: kính hiện và khúc xạ nền động · bảng điều khiển đổi tham số · nút chọn ảnh nạp được ảnh · nút áp wallpaper mở hộp thoại 3 lựa chọn.

- [x] **Bước 6: Commit**

```powershell
git add -A
git commit -m "refactor: move backdrop sources to data layer behind repository"
```

---

**Ghi chú sau khi thực hiện (2026-10-16):** (1) dùng `mv` thay `git mv` vì shell của Claude không xóa được lock file của git; `git add -A` vẫn nhận ra là rename. (2) `MainActivity` chỉ cần sửa 3 import (không phải 4): `DemoBackdropView` ở lại `demo/`. (3) Ruling về `WallpaperTarget` ở trên. (4) Xác nhận 2026-10-16: build xanh, demo chạy như trước trên Oppo A3x.

---

## Task 2: `HomeActivity` nhận HOME intent và vẽ ảnh nền

**Files:**
- Create: `.../ui/home/HomeActivity.kt`, `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeUiState.kt`
- Create: `app/src/main/res/layout/activity_home.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `BackdropRepository` (Task 1), `SettingsRepository` (M0).
- Produces:

```kotlin
// ui/home/HomeUiState.kt
enum class BackdropStatus { Loading, Empty, Ready, Error }

data class HomeUiState(
    val backdropStatus: BackdropStatus = BackdropStatus.Loading,
    val backdrop: Bitmap? = null,
    val isDefaultLauncher: Boolean = false,   // Task 3 ghi vào
    val showWallpaperPrompt: Boolean = false  // Task 4 ghi vào
)

// ui/home/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val backdropRepository: BackdropRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>
    fun onScreenSize(width: Int, height: Int)      // gọi một lần khi biết kích thước màn
    fun onImagePicked(uri: Uri, width: Int, height: Int)
}
```

`backdrop: Bitmap?` là **tham chiếu do ViewModel sở hữu**. Không `recycle()` nó khi Activity chết: với `configChanges` đã khai thì Activity không bị tạo lại khi xoay, và ViewModel sống lâu hơn Activity. Chỉ thay bitmap khi người dùng chọn ảnh mới.

- [x] **Bước 1: Tạo `HomeUiState.kt` và `HomeViewModel.kt` đúng chữ ký ở trên**

`uiState` là `MutableStateFlow(HomeUiState()).asStateFlow()`. `onScreenSize` chỉ làm việc một lần (nhớ bằng một cờ nội bộ): nếu `hasBackdrop()` false → `Empty`; nếu true → `loadBackdrop(w, h)`, ra null thì `Error`, khác null thì `Ready`. `onImagePicked` gọi `importBackdrop` rồi cập nhật y như vậy.

- [x] **Bước 2: Tạo `activity_home.xml`**

`FrameLayout` id `home_root`, không padding (B2), nền đen. Bên trong theo đúng thứ tự z (nền trước, thứ phủ lên sau — B2 sẽ cần đúng thứ tự này khi kính vào ở M3):

| id | view | ghi chú |
|---|---|---|
| `backdrop_image` | `ImageView` | `match_parent`, `scaleType="centerCrop"`, `visibility="gone"` |
| `empty_state` | `LinearLayout` dọc, `layout_gravity="center"` | chứa `TextView` + `MaterialButton` id `btn_pick_image`; `visibility="gone"` |
| `home_status` | `TextView` | trên cùng, chữ nhỏ. **Chỉ để debug, sẽ xóa ở M3** — ghi comment XML nói vậy |

Vùng chạm của `btn_pick_image` tối thiểu 48dp (chuẩn ở mục 7 của spec).

- [x] **Bước 3: Tạo `HomeActivity.kt`**

`@AndroidEntryPoint class HomeActivity : AppCompatActivity()`, dùng `ActivityHomeBinding`, `enableEdgeToEdge()`, lấy ViewModel bằng `by viewModels()`. Thu `uiState` trong `lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }` và render 4 trạng thái: `Loading` ẩn cả hai, `Empty` hiện `empty_state`, `Ready` hiện `backdrop_image` với bitmap, `Error` hiện `empty_state` kèm chữ báo không đọc được ảnh.

Gọi `onScreenSize` bằng `binding.homeRoot.doOnLayout { vm.onScreenSize(it.width, it.height) }` — lấy kích thước view thật, không lấy `displayMetrics`, vì đó là kích thước mà `centerCrop` sẽ dùng.

Nút chọn ảnh dùng `ActivityResultContracts.PickVisualMedia` (Photo Picker, không cần quyền), giống demo đang làm.

- [x] **Bước 4: Khai `HomeActivity` trong manifest**

```xml
<activity
    android:name=".ui.home.HomeActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:clearTaskOnLaunch="true"
    android:resizeableActivity="true"
    android:configChanges="keyboard|keyboardHidden|navigation|screenSize|smallestScreenSize|screenLayout|orientation"
    android:windowSoftInputMode="adjustNothing"
    android:enableOnBackInvokedCallback="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

Lý do từng thuộc tính nằm ở mục 4 của spec. Thêm `android:label="@string/demo_activity_label"` ("Demo kính") vào `MainActivity` đang có, để hai icon trong danh sách app phân biệt được — demo vẫn giữ `MAIN` + `LAUNCHER` của nó, không sửa gì khác.

- [x] **Bước 5: Kiểm chứng trên Oppo A3x — đường đi chính**

```powershell
.\gradlew.bat installDebug
adb shell input keyevent KEYCODE_HOME
```
Mong đợi: hệ thống hiện bộ chọn launcher và có "OS26 Launcher" trong danh sách. Chọn nó (chọn "Chỉ lần này" để còn thử lại) → thấy màn hình chính mới. Nếu `filesDir` đã có ảnh từ demo thì ảnh hiện ngay; chưa có thì thấy empty state và nút chọn ảnh dùng được.

- [x] **Bước 6: Kiểm chứng Review Focus 1 và 2 — không có ảnh, và ảnh hỏng**

Trường hợp chưa có ảnh:
```powershell
adb shell run-as gambi.global.os26launcher ls files
adb shell run-as gambi.global.os26launcher rm files/<tên file bản sao>
```
Tên file lấy từ hằng `FILE_NAME` trong `data/backdrop/PickedImageSource.kt`. Xóa xong mở lại app → mong đợi empty state, **không** màn hình đen.

Trường hợp ảnh hỏng (`hasSaved()` true nhưng decode null):
```powershell
adb shell "run-as gambi.global.os26launcher sh -c 'echo not-an-image > files/<tên file bản sao>'"
```
Mở lại app → mong đợi trạng thái `Error`: hiện empty state kèm chữ báo không đọc được ảnh, app **không** crash. Xem log: `adb logcat -b crash`. Chọn lại một ảnh phải trở về bình thường.

- [x] **Bước 7: Commit**

```powershell
git add -A
git commit -m "feat: add HomeActivity with HOME intent-filter and backdrop"
```

**Ghi chú sau khi thực hiện (2026-10-16):** (1) `HomeViewModel` chưa nhận `SettingsRepository` ở Task 2 vì chưa dùng; Task 4 thêm vào constructor. (2) `home_status` có `marginTop` 48dp tạm, Task 5 thay bằng inset. (3) Xác nhận 2026-10-16 trên Oppo A3x: đường chính, chưa có ảnh (`Empty`), ảnh hỏng (`Error`, không crash, chọn lại ảnh về bình thường) đều đạt.

---

## Task 3: Phát hiện và đặt launcher mặc định

**Files:**
- Create: `.../domain/repository/DefaultLauncherRepository.kt`
- Create: `.../data/system/DefaultLauncherRepositoryImpl.kt`
- Modify: `.../di/RepositoryModule.kt`, `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeActivity.kt`
- Modify: `res/layout/activity_home.xml`, `res/values/strings.xml`

**Interfaces:**
- Consumes: `HomeUiState.isDefaultLauncher` (Task 2).
- Produces:

```kotlin
// domain/repository/DefaultLauncherRepository.kt
interface DefaultLauncherRepository {
    fun isDefaultLauncher(): Boolean
    /** Intent mở màn chọn launcher mặc định; null nếu máy này không có đường nào mở được. */
    fun createSetDefaultIntent(): Intent?
}
```
- Produces thêm trên ViewModel: `fun refreshDefaultLauncher()` — `HomeActivity` gọi trong `onResume`.

- [x] **Bước 1: Tạo `DefaultLauncherRepositoryImpl`**

`@Singleton class DefaultLauncherRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) : DefaultLauncherRepository`.

`isDefaultLauncher()`:
- API 29+ (`Build.VERSION_CODES.Q`): `context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME)`.
- API 26–28: `packageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)` rồi so `activityInfo.packageName` với `context.packageName`.

`createSetDefaultIntent()` thử theo thứ tự, trả cái đầu tiên **resolve được** (`packageManager.resolveActivity(it, 0) != null`), hết thì trả null:
1. API 29+ và `roleManager.isRoleAvailable(ROLE_HOME)`: `roleManager.createRequestRoleIntent(ROLE_HOME)`
2. `Intent(Settings.ACTION_HOME_SETTINGS)`
3. `Intent(Settings.ACTION_SETTINGS)`

Bọc toàn bộ nhánh RoleManager trong `try/catch` cho `SecurityException` và `IllegalArgumentException`: một số ROM khai báo role nhưng từ chối tạo intent.

- [x] **Bước 2: Nối vào Hilt và ViewModel**

Thêm `@Binds` vào `RepositoryModule`. `HomeViewModel` nhận thêm `DefaultLauncherRepository`, thêm `refreshDefaultLauncher()` cập nhật `isDefaultLauncher` vào state.

- [x] **Bước 3: UI**

Thêm `MaterialButton` id `btn_set_default` vào `empty_state`… **không** — đặt nó thành một view riêng ở dưới cùng giữa màn (`layout_gravity="bottom|center_horizontal"`), vì nó phải hiện cả khi đã có ảnh nền. Hiện khi `isDefaultLauncher == false` **và** `createSetDefaultIntent() != null`; ẩn ở mọi trường hợp khác (Review Focus 3: không có đường mở thì ẩn, không hiện nút chết). `home_status` in thêm `mặc định: có/không`.

`HomeActivity.onResume` gọi `vm.refreshDefaultLauncher()`. Nút gọi `startActivity` trong `runCatching`, lỗi thì hiện `LiquidGlassToast` hoặc `Toast` với chuỗi trong `strings.xml`.

- [x] **Bước 4: Kiểm chứng trên Oppo A3x (Android 14) — Review Focus 3 và 5**

```powershell
.\gradlew.bat installDebug
```
- Khi **chưa** là mặc định: nút hiện, bấm vào mở được màn chọn launcher của hệ thống. Ghi lại đường nào trong 3 đường đã dùng (thêm một `Log.d` tạm nếu cần, xóa trước khi commit).
- Đặt làm mặc định rồi quay lại app: nút biến mất, `home_status` ghi `mặc định: có`.
- Đổi sang launcher khác bằng `adb shell cmd package set-home-activity <launcher khác>` hoặc trong Cài đặt, rồi mở lại app: `home_status` phải ghi `mặc định: không` ngay (đây là `onResume`, không phải `onCreate`), và **wallpaper hệ thống không được tự đổi lại** (Quyết định 2).
- Bấm Back ở màn hình chính: ghi lại hành vi hiện tại — Task 5 sẽ sửa thành no-op.

- [x] **Bước 5: Kiểm chứng trên Vsmart Live 4 (Android 11)**

Đây là máy dễ vỡ nhất cho Review Focus 3: Android 11 có RoleManager nhưng ROM VOS có thể không cho request `ROLE_HOME`. Mong đợi: hoặc nút mở được một màn chọn launcher, hoặc nút **ẩn**. Crash `ActivityNotFoundException` là FAIL.

- [x] **Bước 6: Commit**

```powershell
git add -A
git commit -m "feat: detect and request default launcher role"
```

**Ghi chú sau khi thực hiện (2026-10-16):** (1) Thêm `canRequestDefaultLauncher` vào `HomeUiState` để nút biết khi nào hiện. (2) Mở màn chọn bằng `StartActivityForResult`, không dùng `startActivity` thường. (3) Thêm `<queries>` (REQUEST_ROLE, HOME_SETTINGS, SETTINGS) vào manifest để `resolveActivity()` thấy được màn hệ thống trên Android 11+. (4) Đã xóa 4 dòng `Log.d` tạm; giữ 2 dòng `Log.w` báo RoleManager từ chối. (5) Đo 2026-10-16 bằng log tạm tag `M1`: cả Oppo A3x và Vsmart Live 4 đều in `RoleManager`, tức đi đường 1, nút mở được màn chọn, không crash; đổi sang launcher khác rồi quay lại thì `onResume` cập nhật đúng. (6) Back ở màn hình chính, chưa có callback: không thấy gì xảy ra trên cả hai máy; Task 5 vẫn thêm callback rỗng tường minh.

---

## Task 4: Hỏi áp wallpaper đúng một lần

Hành vi của Quyết định 2: app chỉ hỏi khi **đã** là launcher mặc định, và chỉ hỏi **một lần**.

**Files:**
- Modify: `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeActivity.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: `BackdropRepository.wallpaperUnavailableReason()` và `applyAsWallpaper()` (Task 1), `SettingsRepository.setDefaultLauncherPromptShown()` (M0), `HomeUiState.showWallpaperPrompt` (Task 2).
- Produces trên ViewModel:

```kotlin
fun onWallpaperPromptDismissed()                                  // ghi cờ, ẩn prompt
fun onWallpaperTargetChosen(target: WallpaperTarget, width: Int, height: Int)
```

- [x] **Bước 1: Điều kiện bật prompt trong ViewModel**

Sau mỗi lần `refreshDefaultLauncher()`, đặt `showWallpaperPrompt = true` khi **cả bốn** đúng: `isDefaultLauncher` · `defaultLauncherPromptShown == false` (đọc từ `SettingsRepository`) · `backdropStatus == Ready` · `wallpaperUnavailableReason() == null`.

Điều kiện thứ tư là Review Focus 4: máy không đặt được wallpaper thì **không hỏi**, và vẫn ghi cờ đã hỏi để không dồn câu hỏi lại cho lần sau.

- [x] **Bước 2: Hộp thoại trong `HomeActivity`**

`MaterialAlertDialogBuilder` với `setItems` 3 lựa chọn từ `WallpaperTarget.entries`, chữ hiển thị lấy từ `strings.xml` (Màn hình chính / Màn khóa / Cả hai), không hardcode và `setNegativeButton` "Không áp dụng". Thêm `setOnDismissListener`.

**Mọi đường ra đều gọi `onWallpaperPromptDismissed()`** — chọn, bấm "Không áp dụng", bấm ra ngoài, bấm Back. Nếu chỉ ghi cờ khi người dùng chọn thì app sẽ hỏi lại mỗi lần mở, đúng thứ Quyết định 2 cấm.

Kích thước truyền cho `onWallpaperTargetChosen` lấy từ `binding.homeRoot.width/height` (view luôn phủ kín màn), không lấy từ `backdrop_image` vì nó có thể đang `gone`.

- [x] **Bước 3: Hiện kết quả**

`applyAsWallpaper` trả `Result` → thành công thì `Toast` báo đã áp dụng cho đích nào; thất bại thì `Toast` kèm lý do. Không chặn UI trong lúc chạy (đang ở coroutine trên `@IoDispatcher`).

- [x] **Bước 4: Kiểm chứng trên Oppo A3x**

Đặt lại cờ để thử từ đầu (Oppo A3x chặn `adb shell pm clear` với `SecurityException: CLEAR_APP_USER_DATA`, đo 2026-10-16). Chỉ xóa file cờ của DataStore, ảnh nền được giữ nguyên:
```powershell
adb shell am force-stop gambi.global.os26launcher
adb shell run-as gambi.global.os26launcher ls files/datastore
adb shell run-as gambi.global.os26launcher rm files/datastore/launcher_settings.preferences_pb
```
Phải `force-stop` trước, vì DataStore giữ dữ liệu trong RAM khi app đang chạy. Dòng `ls` để kiểm tra tên file thật trước khi xóa. Nếu cần xóa sạch cả ảnh nền thì vào Cài đặt > Ứng dụng > OS26 Launcher > Bộ nhớ > Xóa dữ liệu.

Trình tự mong đợi: chọn ảnh → đặt app làm launcher mặc định → quay lại app → hộp thoại hiện **một lần** → chọn "Cả hai" → kiểm tra wallpaper màn hình chính và màn khóa của hệ thống khớp với ảnh trong app. Kill app, mở lại → **không** hỏi nữa. Xoay màn hình trong lúc hộp thoại đang mở → không crash (Activity không bị tạo lại vì đã khai `configChanges`).

- [x] **Bước 5: Kiểm chứng Review Focus 4**

Không có cách bắt `isSetWallpaperAllowed` trả false trên máy thường. Thay bằng: tạm đổi `wallpaperUnavailableReason()` để luôn trả một chuỗi, chạy lại, xác nhận **không** có hộp thoại nào hiện và cờ vẫn được ghi (mở lại app vẫn không hỏi). Hoàn nguyên đoạn sửa tạm trước khi commit, và ghi kết quả vào báo cáo.

- [x] **Bước 6: Commit**

```powershell
git add -A
git commit -m "feat: ask once to apply backdrop as system wallpaper"
```

**Ghi chú sau khi thực hiện (2026-10-16):** (1) Thêm `HomeEvent` (Applied/Failed) qua Channel để báo Toast một lần. (2) Cờ `default_launcher_prompt_shown` ghi ở mọi đường ra của hộp thoại (chọn, Không áp dụng, chạm ngoài, Back) và khi máy không đặt được wallpaper. (3) `promptResolved` trong ViewModel chặn hỏi lại trong cùng phiên; `onDestroy` gỡ dismiss listener trước khi đóng hộp thoại để Activity bị hủy không bị tính là đã trả lời. (4) Oppo A3x không cho `pm clear` (SecurityException `CLEAR_APP_USER_DATA`); đặt lại cờ bằng force-stop rồi xóa `files/datastore/launcher_settings.preferences_pb` qua `run-as`. (5) Kiểm chứng: hộp thoại hiện đúng một lần, "Cả hai" áp đúng cho cả hai màn, Toast đúng, mọi đường thoát không hỏi lại; sửa tạm `wallpaperUnavailableReason()` trả chuỗi thì không hiện hộp thoại và mở lại vẫn không hỏi; đã hoàn nguyên (grep `TEMP-M1` sạch).

---

## Task 5: Back no-op, inset, và cập nhật tài liệu

**Files:**
- Modify: `.../ui/home/HomeActivity.kt`
- Modify: `../project_structure.md`, `../AGENTS.md`, `M1-home-activity.md` (file này)

- [x] **Bước 1: Back ở màn hình chính là no-op**

`onBackPressedDispatcher.addCallback(this) { }` — callback rỗng, enabled. Màn hình chính của launcher không có gì để quay lại, và `finish()` sẽ để lộ màn hình phía dưới.

**Vòng lặp Back → quay lại app (người dùng báo 2026-10-16, chưa tái hiện chắc chắn):** hiện `HomeActivity` chưa có xử lý Back nên chạy hành vi mặc định của hệ thống. Xảy ra trên Oppo A3x (Android 14), Vsmart (Android 11) chưa thử. Khi `HomeActivity` là Activity gốc của task và là launcher mặc định: Android 12+ gọi `moveTaskToBack()` (suy luận, chưa đọc source máy thật), Android ≤ 11 gọi `finish()`. Cả hai đều đẩy task ra nền/rỗng → hệ thống khởi động lại HOME, tức chính app này → thấy app đóng rồi mở lại. Bấm Back lại thì lặp lại. Callback rỗng chặn tận gốc: Back không rời Activity nên không có gì để khởi động lại.

- [x] **Bước 2: Inset cho view con**

`ViewCompat.setOnApplyWindowInsetsListener` trên `home_root`, áp `systemBars()` làm **margin** cho `home_status` (top) và `btn_set_default` (bottom). **Không** padding lên root (B2) — nền phải phủ kín dưới các thanh hệ thống, nếu không kính ở M3 sẽ chụp được viền trống. Trả `insets` nguyên vẹn.

Phần đo lại khi xoay màn và kiểm tra sáng/tối thuộc **M5**, không làm ở đây.

- [x] **Bước 3: Kiểm chứng trên cả hai máy**

- Bấm Back ở màn hình chính: app **không** đóng, không thấy màn hình nào khác.
- Kịch bản vòng lặp: app đang là launcher mặc định → bấm Home → bấm Back **5 lần liên tiếp**. Mong đợi: không có animation đóng/mở, không chớp, ảnh nền không nạp lại. Đối chứng khách quan: `adb logcat -s ActivityTaskManager:I | findstr HomeActivity` trong lúc bấm — không được có dòng `START`/`Displayed` mới của `HomeActivity` sau mỗi lần Back. Làm cả trên Oppo A3x (nơi đã thấy lỗi) và Vsmart Live 4 (chưa thử, nhánh Android 11 có thể khác).
- Nếu vẫn còn vòng lặp sau khi có callback rỗng: dừng, thu log (`adb logcat -b events -s wm_create_activity wm_destroy_activity`) rồi chẩn đoán tiếp, không đoán.
- `home_status` không bị thanh trạng thái che; `btn_set_default` không bị thanh điều hướng che.
- Ảnh nền vẫn phủ **hết** màn hình, kể cả vùng dưới hai thanh hệ thống. Chụp màn hình để đối chiếu: `adb exec-out screencap -p > home.png`.

- [x] **Bước 4: Cập nhật tài liệu**

Trong `project_structure.md`:
- Tick M1 ở mục 9, ghi ngày xác nhận.
- Mục 8.1 còn một câu cũ **sai sự thật**: "Ba dependency test còn sót trong `app/build.gradle.kts` … đề nghị bỏ cùng 2 file test mẫu, xem mục 11." Chúng đã bị xóa ở M0 — xóa câu này.
- Mục 3: đánh dấu 4 file backdrop là đã di trú; ghi chú `core/bitmap/` chưa tách và lý do (hoãn tới M6).

Trong `AGENTS.md` — những thứ không có ở đâu khác và M2 trở đi sẽ cần:
- Đường nào trong 3 đường của `createSetDefaultIntent()` thật sự chạy được, trên từng máy.
- Hai icon trong danh sách app trong lúc demo còn sống, và nhãn của chúng.
- Mẹo đặt lại trạng thái để thử: Oppo A3x chặn `adb shell pm clear` (SecurityException `CLEAR_APP_USER_DATA`) nên chỉ xóa file cờ DataStore sau `force-stop`; xóa sạch cả ảnh nền thì vào Cài đặt > Ứng dụng; `adb shell cmd package set-home-activity` để đổi launcher mặc định không cần vào Cài đặt (chưa thử trên máy thật, kiểm tra khi dùng).
- Hành vi Back mặc định của Activity gốc kiêm launcher (vòng lặp đóng/mở) và cách callback rỗng xử lý.
- Bẫy nào đã gặp khi `HomeActivity` nhận HOME (nếu có).

- [x] **Bước 5: Commit**

```powershell
git add -A
git commit -m "feat: make back a no-op on home and apply window insets"
```

**Ghi chú sau khi thực hiện (2026-10-16):** (1) Vòng lặp Back → mở lại app do người dùng báo trên Oppo A3x (Android 14), hết sau khi thêm callback rỗng; Vsmart Live 4 cũng đạt. (2) Margin gốc của `home_status` (8dp) và `btn_set_default` (16dp) thay cho 48dp tạm; inset cộng vào margin gốc, không cộng dồn. (3) `project_structure.md`: M1 ✅, bỏ câu sai về dependency test, 4 file `demo/` đánh dấu đã di trú, `core/bitmap/` ghi hoãn tới M6. (4) `AGENTS.md`: Back mặc định của launcher, hai icon, mục inset.

---

## Checklist

- [x] **Task 1** Di trú backdrop + `BackdropRepository`, demo chạy y như trước — xác nhận 2026-10-16 (build xanh, demo chạy như trước trên Oppo A3x)
- [x] **Task 2** `HomeActivity` + HOME intent-filter + ảnh nền + empty state + ảnh hỏng không crash — xác nhận 2026-10-16 (Oppo A3x, 3 kịch bản đạt)
- [x] **Task 3** Phát hiện launcher mặc định + nút đặt mặc định, chạy hoặc ẩn trên cả hai máy — xác nhận 2026-10-16 (Oppo A3x và Vsmart Live 4 đều đi đường RoleManager)
- [x] **Task 4** Hỏi áp wallpaper đúng một lần, mọi đường ra đều ghi cờ
- [x] **Task 5** Back no-op + inset + tài liệu — xác nhận 2026-10-16 (Oppo A3x và Vsmart Live 4: Back không còn vòng lặp, inset đúng, nền phủ kín)

## M1 cố tình KHÔNG làm

| Không làm | Thuộc mốc |
|---|---|
| Đọc danh sách app, `IconCache` | M2 |
| Lưới icon, dock, ViewPager2, `core/glass` | M3 |
| Room, lưu bố cục | M4 |
| Đo nền tĩnh 4b, xoay màn, sáng/tối | M5 |
| Tách `core/bitmap/` khỏi `PickedImageSource` | M6 |
| Mục cài đặt "áp dụng lại wallpaper" | M8 |
| Xóa `demo/`, `MainActivity`, `activity_main.xml` | sau M5 |
| Bất kỳ `LiquidGlassView` nào trên `HomeActivity` | M3 |
