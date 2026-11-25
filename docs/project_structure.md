# Kiến trúc và cấu trúc dự án Os26Launcher

Trạng thái: **tài liệu sống, cập nhật theo code** (lần đối chiếu gần nhất: DC, 2026-11-25). Bản thiết kế gốc tạo 2026-10-16; các đoạn "Tệp thêm ở …" bên dưới ghi theo mốc, mới nhất là đoạn repo Compose.
Gốc dự án: `E:\SourceTree\Os26Launcher_Compose` (từ 2026-11-23; repo XML cũ `E:\SourceTree\IosLauncher` đã ngừng, xem `AGENTS.md` mục "Dự án hiện hành"). Package gốc: `gambi.global.os26launcher`.

> **Đổi tên 2026-10-16** để tránh rủi ro thương hiệu. Package `gambi.global.ioslauncher` → `gambi.global.os26launcher` (thư mục source, `package`/`import`, `namespace`, `applicationId`, tên custom view trong `activity_main.xml`). Cùng ngày: `app_name` → **"OS26 Launcher"**, style `Theme.IosLauncher` → `Theme.Os26Launcher` (5 chỗ), `rootProject.name` → `Os26Launcher`. **Giữ nguyên** tên thư mục trên ổ đĩa `E:\SourceTree\IosLauncher`.

Tài liệu liên quan:
- [Launcher-Features.md](Launcher-Features.md) — 21 tính năng, 4 cấp, giai đoạn G1–G3. Số "TT#n" dưới đây trỏ vào bảng tính năng của file đó.
- [LiquidGlass-Android.md](LiquidGlass-Android.md) — API thư viện kính.
- [LiquidGlass-Demo.md](LiquidGlass-Demo.md) — demo đã đóng 2026-10-16, Quyết định 1–6.

---

## 1. Hiện trạng (đọc code 2026-10-16)

| Hạng mục | Hiện trạng |
|---|---|
| Module | 1 module `:app`, 1 commit git duy nhất |
| Build | AGP 9.3.3, Gradle 9.5.0, toolchain JDK 25, Java source/target 11, `configuration-cache=true` |
| Kotlin | Không có plugin `kotlin-android` — AGP 9 tích hợp Kotlin sẵn (built-in Kotlin) |
| SDK | `minSdk 26`, `targetSdk 37`, `compileSdk 37` |
| Code | `MainActivity.kt` (343 dòng, toàn bộ là demo + bảng điều khiển) + `demo/` 5 file |
| UI | XML/View, theme `Theme.Material3.DayNight.NoActionBar`, `enableEdgeToEdge()`, **chưa dùng ViewBinding** (`findViewById` thủ công) |
| Chưa có | `intent-filter` HOME, Application class, ViewModel, DI, lưu trữ, Fragment, RecyclerView |
| Release | `optimization { enable = false }` → R8 đang **tắt**; `app/src/main/keepRules/rules.keep` còn nguyên mẫu |
| Test | **Dự án không làm test** (A7). Đã xóa `src/test`, `src/androidTest`, 3 dependency test và 3 alias trong `libs.versions.toml` (2026-10-16) |

---

## 2. Quyết định kiến trúc đã chốt (2026-10-16)

| # | Chủ đề | Quyết định | Lý do |
|---|---|---|---|
| A1 | Module | **1 module `:app`**, chia package theo layer + feature | 1–2 dev, build nhanh, không cần convention plugin. Ranh giới layer giữ bằng quy ước package + review |
| A2 | DI | **Hilt** (`com.google.dagger`) | Chuẩn Google, scope sẵn (`@Singleton`, `@ActivityRetainedScoped`), mọi khai báo tập trung ở `di/` |
| A3 | Lưu trữ | **Room** cho bố cục + **DataStore Preferences** cho cài đặt | Bố cục là dữ liệu quan hệ có thứ tự và span → cần truy vấn và migration; cài đặt là key-value |
| A4 | Presentation | **MVVM + StateFlow**, mỗi màn một `UiState` data class | Quen tay, ít boilerplate hơn MVI |
| A5 | Kotlin / KSP | **Giữ KGP 2.2.10** (bản AGP 9.3.3 mang theo, đo 2026-10-16) + **KSP `2.3.11`** | Giữ KGP của AGP mang theo, không ghi đè. **Sửa 2026-10-16:** bản đầu chốt KSP `2.2.10-2.0.2` nhưng lỗi `kotlin.sourceSets` với built-in Kotlin của AGP 9 (đã đo ở M0.1), nên dùng KSP 2.3.x. Nâng KGP chỉ khi Hilt báo lỗi metadata version (R9 chưa xảy ra) |
| A6 | Bàn phím | `windowSoftInputMode="adjustNothing"` cho HomeActivity | Bàn phím đẩy layout thì nền dịch mà kính không biết (B3). Overlay tìm kiếm tự tính inset bàn phím ở M8 |
| A7 | Test | **Không làm test**, không thêm thư viện test | Kiểm chứng tay trên Oppo A3x (Android 14) + Vsmart Live 4 (Android 11) |
| A8 | Máy test | **Vsmart Live 4 là máy mặc định** cho mọi việc không liên quan kính; **Oppo A3x** chỉ khi cần kính hoặc khi Live 4 không tái hiện được ca đang xét | Chốt 2026-10-26. Ca đã đo cả hai máy trước ngày này (M1, M2 Task 1–2) không đo lại |
| A9 | Kính | **Mọi việc dùng liquid glass gom vào đúng một mốc (M6)**, không rải vào các mốc khác | Chốt 2026-10-26. Để đo hiệu năng kính tập trung trên bố cục đã ổn định, thay vì đo lại mỗi lần bố cục đổi |
| A10 | Dock | **Nền dock giữ nguyên chiều rộng** bất kể có 0, 1, 2, 3 hay 4 icon bên trong. Không co giãn theo số icon | Chốt 2026-10-27 từ quan sát của người dùng trên iPhone XS Max (iOS 18): nền dock một độ dài cố định dù số app bên trong đổi. Thêm lý do kỹ thuật ở mục 5 |

### Ràng buộc kế thừa từ demo (không được phá)

| # | Ràng buộc | Nguồn |
|---|---|---|
| B1 | UI là View/XML, **không Compose** — thư viện kính là View-based, tác giả khuyên không bọc trong `AndroidView` | LiquidGlass-Android.md mục 1 |
| B2 | Kính chỉ chụp được thứ nằm **trong cây View của app**, và phải là view em **đứng sau** nền trong một parent chồng lớp (`FrameLayout`) | mục 4 |
| B3 | Nền hoặc kính di chuyển → bắt buộc `enableDynamicBackground = true` (lỗi tích hợp số 1) | mục 4 |
| B4 | Kính **không khúc xạ kính khác**; kính nằm trong vùng chụp của kính khác bị ẩn tạm | mục 9b |
| B5 | Mức hiệu ứng chọn **lúc chạy** theo `effectiveApiLevel` (tier), không theo `minSdk` | Quyết định 3, 4 |
| B6 | Nguồn nền là phương án A: app tự giữ bản sao ảnh người dùng chọn; B (đọc wallpaper hệ thống) nằm sau interface `BackdropSource`, đang gác | Quyết định 1 |
| B7 | `RenderEffect` blur chỉ có từ API 31 → nền mờ tĩnh cho API 26–30 phải làm mờ sẵn một lần bằng bitmap | Quyết định 5b |
| B8 | Ô lưới cần `span` để sau chứa widget và thư mục | TT#3 |

### Luật phụ thuộc giữa các layer

```
ui ──────► domain ◄────── data
 │           ▲               │
 └──► core ──┘               └──► core
```

- `ui` **không** được import `data`. Mọi truy cập đi qua interface trong `domain/repository`. Từ FX6 (2026-11-11) không còn ngoại lệ nào: `CoarseLocationSource` đã chuyển sang `domain/weather`.
- `domain` là Kotlin thuần + coroutines: **không** import `android.*` (trừ không tránh được, ghi rõ lý do tại chỗ).
- `data` hiện thực interface của `domain`; Hilt `@Binds` nối impl vào interface trong `di/RepositoryModule.kt`.
- `core` là tiện ích dùng chung, không biết về feature nào. **Ngoại lệ có chủ ý:** `core/glass` phụ thuộc `android.view` và Backdrop (Kyant0) — đây là hạ tầng UI, không phải logic nghiệp vụ (thư viện QWEA0 gỡ ở JX, 2026-11-20).

---

## 3. Cây thư mục dự kiến

Ký hiệu: `[có]` đã tồn tại · `[mới]` sẽ tạo · `[di trú]` chuyển từ `demo/` sang · `[xóa?]` đề nghị xóa, chờ bạn duyệt (mục 11).

```
IosLauncher/
├─ docs/
│   ├─ AGENTS.md                         [mới]  quy ước, build, môi trường, máy thật, viết docs, phát hành (ngắn)
│   ├─ lessons/                          [mới]  bài học theo chủ đề: glass, drag-and-layout, startup-performance, search, widgets, icons-and-app-list, permissions-and-device-testing, home-pager-animation, settings-and-data + README.md (mục lục, bảng tra mục cũ)
│   ├─ project_structure.md              [mới]  file này
│   ├─ Launcher-Features.md              [có]
│   ├─ LiquidGlass-Android.md            [có]
│   ├─ LiquidGlass-Demo.md               [có]
│   └─ plans/                            [mới]  1 file kế hoạch cho mỗi mốc, ví dụ M3-icon-grid.md
├─ gradle/
│   ├─ libs.versions.toml                [có]   nguồn duy nhất của mọi version
│   └─ wrapper/                          [có]   Gradle 9.5.0
├─ build.gradle.kts                      [có]   khai báo plugin apply false; thêm hilt + ksp
├─ settings.gradle.kts                   [có]   FAIL_ON_PROJECT_REPOS (JitPack gỡ ở JX)
├─ gradle.properties                     [có]
└─ app/
    ├─ build.gradle.kts                  [có]   thêm viewBinding, ksp, hilt, room, datastore
    ├─ schemas/                           [mới]  Room exportSchema → commit vào git để theo dõi migration
    └─ src/
        ├─ main/
        │   ├─ AndroidManifest.xml        [có]   thêm HOME intent-filter, LauncherApp, HomeActivity
        │   ├─ keepRules/rules.keep       [có]   bổ sung keep rule khi bật lại R8
        │   ├─ res/                       (chi tiết ở mục 7)
        │   └─ java/gambi/global/os26launcher/
        │       ├─ LauncherApp.kt                      [mới]  @HiltAndroidApp
        │       │
        │       ├─ di/                                        Hilt modules — chỉ khai báo, không logic
        │       │   ├─ AppModule.kt                    [mới]  Context, CoroutineScope cấp app (có `CoroutineExceptionHandler` từ FX6: chỉ log tên lớp + một khung stack)
        │       │   ├─ DatabaseModule.kt               [mới]  LauncherDatabase + các DAO
        │       │   ├─ DataStoreModule.kt              [mới]  DataStore<Preferences>
        │       │   ├─ RepositoryModule.kt             [mới]  @Binds impl → interface
        │       │   ├─ DispatcherModule.kt             [mới]  @IoDispatcher / @DefaultDispatcher
        │       │      (GlassModule.kt [không làm M6]: GlassTierResolver/GlassSurfaceFactory dùng @Inject constructor trực tiếp, không cần module riêng)
        │       │
        │       ├─ core/
        │       │   ├─ glass/                                 ★ lõi khác biệt của app, xem mục 5
        │       │   │   ├─ GlassTier.kt                [xong M6]  enum LENS(33+) / CLASSIC_GPU(31-32) / CLASSIC_CPU(26-30)
        │       │   │   ├─ GlassTierResolver.kt        [xong M6]  effectiveApiLevel + override → tier (thư viện đã tự lo high-contrast/pin)
        │       │   │   ├─ GlassBudget.kt              [xong M6]  tier → số kính thật tối đa/màn. LENS=6 (demo), CLASSIC=1 (đo 4b thật — mục 5). GL thêm: ô thư mục mỗi trang K = 6 (24 khi có đường Lens), ô nhóm 8.
        │       │   │   ├─ GlassSurface.kt             [xong M6]  interface: 1 "bề mặt kính" bất kể cách vẽ
        │       │   │   ├─ RealGlassSurface.kt         [xong M6]  bọc LiquidGlassView, quản enableDynamicBackground
        │       │   │   ├─ StaticBlurSurface.kt        [xong M6]  card nền mờ tĩnh, API 26+ (Quyết định 5b). Đo Janky 2.13% trên Live 4
        │       │   │   ├─ GlassSurfaceFactory.kt      [xong M6]  tier → chọn Real hay Static. KHÔNG dùng GlassBudget (chỉ 1 kính/dock, xem mục 5)
        │       │   │   ├─ BackdropBlurDrawable.kt     [xong GL]  kính tại chỗ: vẽ lát cắt bản mờ nhỏ theo vị trí màn hình của view chủ
        │       │   │   ├─ GlassPolicy.kt              [xong GL]  hàm thuần tier + vai trò + khả năng thật -> GlassMode
        │       │   │   ├─ GlassEnvironment.kt         [xong GL]  singleton Hilt: tier, cache mờ, lắng nghe đổi mức kính (view lấy qua EntryPoint)
        │       │   │   ├─ GlassMode.kt, GlassRole.kt, GlassStyle.kt  [xong GL]
        │       │   │   └─ BlurredBackdropCache.kt     [sửa GL]   bản mờ nhỏ (chia đôi + box blur) dựng ngoài main thread (luồng GlassBlur), dùng chung cho kính tại chỗ và StaticBlurSurface
        │       │   ├─ ui/
        │       │   │   ├─ GridMetrics.kt              [xong M3]  tính cellSize/pitch/padding từ kích thước màn + cấu hình lưới
        │       │   │   ├─ DragHelper.kt               [mới]  gom logic kéo-thả dùng chung (home, dock, folder)
        │       │   │   ├─ InsetsExt.kt                [mới]  áp inset cho con, KHÔNG padding root (B2)
        │       │   │   └─ ViewExt.kt                  [mới]  dp/px, doOnLayout, debounce click
        │       │   ├─ bitmap/
        │       │   │   ├─ IconShape.kt                [xong TL2] path bo góc liên tục kiểu iOS (CORNER_RATIO, SMOOTHING chỉnh tay), dùng chung data + ui
        │       │   │   ├─ BitmapScaler.kt             [xong FX1] tính vùng centerCrop, cỡ đầu ra, inSampleSize, trần pixel (tách từ PickedImageSource)
        │       │   │   ├─ ExifReader.kt               [xong FX1] readOrientation + applyOrientation
        │       │   │   └─ TransparentTrimmer.kt       [xong FX1] trimTransparentBorders (Quyết định 5a)
        │       │   ├─ coroutines/Dispatchers.kt       [mới]  qualifier @IoDispatcher / @DefaultDispatcher cho DispatcherModule
        │       │   └─ designsystem/                    [xong DS3]  token + thành phần giao diện v3 (`Ds*`, 23 file), chưa áp vào màn nào: bề mặt kính xấp xỉ (`DsSurface`, `DsSurfaceDrawable`, `applyDsSurface`), bo góc nhân theo thanh "Bo góc" của kính (`DsCorners`), nền aurora, nút, chip, segmented, ô tìm, công tắc/thanh trượt/chỉ báo bước, hàng + card nhóm, nav bar, hộp thoại, bottom sheet. Xem plans/DS3-design-system-v3.md
        │       │
        │       ├─ domain/
        │       │   ├─ model/
        │       │   │   ├─ AppItem.kt                  [mới]  componentKey, label, user, icon handle
        │       │   │   ├─ LauncherItem.kt             [mới]  sealed: App / Folder / Widget / Shortcut
        │       │   │   ├─ GridPosition.kt             [xong M3]  x, y, spanX, spanY (spanX/Y để sẵn, chưa dùng tới M4+)
        │       │   │   ├─ HomePage.kt                 [xong M3]  index + danh sách PlacedApp (chưa có LauncherItem sealed, chỉ AppItem)
        │       │   │   ├─ DockLayout.kt               [mới]
        │       │   │   ├─ LauncherSettings.kt         [bỏ ở CF] thay bằng domain/config/*Config (xem docs/config-schema.md)
        │       │   │   ├─ BackdropState.kt            [mới]  có ảnh chưa, có alpha không, tier đang dùng
        │       │   │   ├─ DockRole.kt                 [xong M3]  PHONE/MESSAGES/BROWSER/CAMERA — KHÔNG có trong bản vẽ gốc, thêm ở Task 4
        │       │   │   └─ PackageEvent.kt             [xong M4]  type (ADDED/REMOVED/CHANGED/.../UNSUSPENDED) + packageNames + userSerial — KHÔNG có trong bản vẽ gốc, thêm ở M4 Task 3
        │       │   ├─ repository/                            interface, không hiện thực
        │       │   │   ├─ AppRepository.kt            [mới]  Flow<List<AppItem>>; M4 Task 3-4 thêm `packageEvents: Flow<PackageEvent>` và `isPackageInstalled()`
        │       │   │   ├─ LayoutRepository.kt         [xong M4]  Flow<List<HomePage>> + Flow<List<DockApp>> dock; M7 thêm moveItem/removeItem/setHidden/hiddenApps
        │       │   │   ├─ SettingsRepository.kt       [bỏ ở CF] thay bằng domain/config/ConfigRepositories.kt
        │       │   │   ├─ BackdropRepository.kt       [mới]  ảnh nền + áp wallpaper; `backdropVersions` (FX1) báo Home có ảnh mới
        │       │   │   ├─ DefaultLauncherRepository.kt[mới]  app có đang là launcher mặc định không
        │       │   │   └─ DefaultAppResolverPort.kt    [xong M3]  resolve(DockRole) → ComponentKey? — KHÔNG có trong bản vẽ gốc, thêm ở Task 4
        │       │   └─ usecase/
        │       │       ├─ ObserveHomeLayoutUseCase.kt [mới]  ghép AppRepository + LayoutRepository
        │       │       ├─ MoveItemUseCase.kt          [mới]  kiểm tra chỗ trống theo span rồi ghi
        │       │       ├─ SearchLauncherUseCase.kt    [mới]  app + danh mục Cài đặt (+ danh bạ ở G3)
        │       │       ├─ ResolveGlassPlanUseCase.kt  [không làm M6] phạm vi M6 chỉ wire kính vào dock (chốt lúc
        │       │       │  brainstorm, YAGNI) — GlassSurfaceFactory chọn trực tiếp theo tier, chưa cần phân bổ
        │       │       │  ngân sách giữa nhiều phần tử. Để M7+/G2 khi có pill/thư mục/widget cũng cần kính
        │       │       ├─ BuildDefaultLayoutUseCase.kt [xong M3]  apps + dock → List<HomePage> — KHÔNG có trong bản vẽ gốc, thay cho phần layout tĩnh của ObserveHomeLayoutUseCase tới khi có M4
        │       │       └─ PickDefaultDockUseCase.kt    [xong M3]  apps → 4 app dock mặc định theo DockRole — KHÔNG có trong bản vẽ gốc, thêm ở Task 4
        │       │
        │       ├─ data/
        │       │   ├─ apps/
        │       │   │   ├─ LauncherAppsDataSource.kt   [xong M2]  LauncherApps + registerCallback (KHÔNG QUERY_ALL_PACKAGES)
        │       │   │   ├─ IconLoader.kt               [xong M2]  lấy icon, giao IconRenderer vẽ + cắt hình (TL2), cache bitmap
        │       │   │   ├─ IconRenderer.kt             [xong TL2] flatten (adaptive hai lớp / legacy tấm nền) → mask (BitmapShader + IconShape); chỗ cắm G2b giữa hai bước
        │       │   │   ├─ IconStyleSource.kt          [xong G2b] giữ kiểu icon trong bộ nhớ (StateFlow), lần nạp đầu chờ giá trị thật, xóa IconCache khi đổi
        │       │   │   ├─ IconCache.kt                [xong M2]  hiện chỉ LruCache RAM (maxMemory/8), key = component+user+size; cache đĩa hoãn tới quyết định ở M3
        │       │   │   ├─ AppRepositoryImpl.kt        [xong M2]
        │       │   │   └─ DefaultAppResolver.kt       [xong M3]  PackageManager.resolveActivity(MATCH_DEFAULT_ONLY) — KHÔNG có trong bản vẽ gốc, thêm ở Task 4
        │       │   ├─ layout/
        │       │   │   ├─ db/LauncherDatabase.kt      [xong M4]  @Database, exportSchema = true, 3 bảng, schema v1 ở app/schemas/
        │       │   │   ├─ db/LauncherItemDao.kt       [xong M4]
        │       │   │   ├─ db/HomePageDao.kt           [xong M4]  KHÔNG có trong bản vẽ gốc, thêm cùng `home_pages` (xem mục 6)
        │       │   │   ├─ db/LauncherItemEntity.kt    [xong M4]  1 bảng phẳng, xem mục 6 — đường dẫn thật là `db/`, KHÔNG có `db/entity/` như bản vẽ gốc
        │       │   │   ├─ db/HiddenAppEntity.kt       [đổi CF]   thành POJO; app ẩn nằm ở bảng `app_overrides` (Room v2, `AppOverrideEntity`/`AppOverrideDao`)
        │       │   │   ├─ LayoutReconciler.kt         [xong M4]  thuật toán seed/thêm/xóa/dồn ô, tách khỏi Impl để đọc được — KHÔNG có trong bản vẽ gốc
        │       │   │   └─ LayoutRepositoryImpl.kt     [xong M4]
        │       │   ├─ config/                             [CF] ConfigKeys (khóa từng nhóm), ConfigStore (đọc/ghi chung), 6 repository *Impl
        │       │   ├─ backdrop/
        │       │   │   ├─ BackdropSource.kt           [di trú] interface (giữ nguyên, B6)
        │       │   │   ├─ PickedImageSource.kt        [di trú] phương án A
        │       │   │   ├─ SystemWallpaperSource.kt    [di trú] phương án B, vẫn không nối vào UI
        │       │   │   ├─ WallpaperApplier.kt         [di trú] setBitmap 3 đích (Quyết định 2)
        │       │   │   └─ BackdropRepositoryImpl.kt   [mới]
        │       │   ├─ search/SettingsCatalog.kt       [mới]  danh mục Settings.ACTION_* tự lập (TT#9)
        │       │   └─ system/DefaultLauncherRepositoryImpl.kt [mới] RoleManager (29+) / fallback Intent HOME
        │       │
        │       ├─ ui/
        │       │   ├─ home/
        │       │   │   ├─ HomeActivity.kt             [tách R1]  Activity duy nhất giữ HOME, singleTask; còn ~200 dòng: nối controller, giữ các thứ tự là hành vi (render, Back, chạm, onDestroy)
        │       │   │   ├─ controller/                 [xong R1]  HomeBackController, HomeScreenState, FolderPanelController, AppLibraryController, DockController, RecreateGuard, BackdropController, HomePagerController (khôi phục trang theo `pageId`, chỉ khi pager rảnh: `settlePageSelection`), IconMenuController, SearchOverlayController, HomeTouchRouter (theo `pointerId` của phiên kéo), SystemBarsController
        │       │   │   ├─ HomeViewModel.kt            [mới]
        │       │   │   ├─ HomeUiState.kt              [xong, cập nhật dần qua M1/M3] pages, dock, backdrop — KHÔNG có
        │       │   │                                        `glassPlan`/`editMode` như bản vẽ gốc: glassPlan cần
        │       │   │                                        ResolveGlassPlanUseCase (không làm ở M6, xem mục 5);
        │       │   │                                        editMode/draggingItemId/ghostPage/iconMenu thêm ở M7
        │       │   │   ├─ HomePagerAdapter.kt         [mới]  RecyclerView.Adapter cho ViewPager2 (không Fragment)
        │       │   │   ├─ PageMotion.kt               [xong TL1] thời lượng/đường cong đổi trang dùng chung (300–450 ms, easeOutQuint), nghỉ giữa hai lượt lật mép
        │       │   │   ├─ TunedPagerSnapHelper.kt     [xong TL1] thay PagerSnapHelper của ViewPager2 để chỉnh settle khi vuốt
        │       │   │   ├─ PagerFlipAnimator.kt        [xong TL1] lật trang có chủ đích bằng FakeDrag (lật mép khi kéo, trang tạm)
        │       │   │   ├─ view/IconGridView.kt        [xong M3]  custom ViewGroup (B8 span chưa làm, hiện mỗi ô 1x1)
        │       │   │   ├─ view/IconCellView.kt        [xong M3]  icon + label tự vẽ bằng Canvas (chấm thông báo chưa làm, để sau)
        │       │   │   ├─ view/DockView.kt            [xong M3, kính vào ở M6]  hàng app yêu thích, nhận GlassSurface
        │       │   │   │   thật từ HomeActivity (Real/StaticBlurSurface theo tier máy)
        │       │   │   ├─ view/SearchPillView.kt      [xong M8c]  pill tìm kiếm cố định + hàng chấm ổ quay số (thay PageIndicatorView)
        │       │   │   └─ view/PageDotsLayout.kt      [xong M8c]  phép tính cửa sổ 5 ô của hàng chấm (hàm thuần)
        │       │   ├─ folder/                         [chưa dùng]  thư mục hiện nằm ở ui/home/view (FolderPanelView), domain/layout (FolderPlanner), domain/model (PlacedItem); thư mục con riêng chỉ lập nếu cần (G2a)
        │       │   ├─ search/                         [xong M8b]  SearchOverlayView, SearchViewModel, SearchUiState (+ SearchStatus, SearchEvent), SearchResultAdapter (TT#9)
        │       │   ├─ library/                        [mới]  AppLibraryActivity (G2, TT#11)
        │       │   ├─ settings/                       [xong M8a, dựng lại G2d]  khung SettingsActivity (nhận extra `settings_screen`, thiếu/lạ thì về trang chủ) + SettingsScreen.kt (interface, context, enum mã màn, bảng đăng ký) + SettingsHubScreen (trang chủ, bảng nhóm theo bản nháp Figma) + 5 màn con (General, LiquidGlass, Wallpaper, IconSet, SearchSettingsScreen) + DefaultLauncherAction + SettingsViewModel/UiState/Row/Adapter/Event + SearchSettingsBlock/ViewModel (RecyclerView nhóm kiểu iOS, KHÔNG dùng Fragment)
        │       │   ├─ wallpaper/                      [mới]  chọn ảnh, hộp thoại áp wallpaper (Quyết định 2)
        │       │   ├─ widget/                         [mới]  widget tự vẽ: đồng hồ, lịch (G2, TT#13)
        │       │   └─ common/
        │       │       ├─ EditModeController.kt       [mới]  TT#8
        │       │       └─ LauncherDialogs.kt          [mới]  dùng LiquidGlassDialogBuilder
        │       │
        │       (demo/ đã xóa hoàn toàn — 2026-10-28, M6 Task 4: DemoBackdropView.kt cùng MainActivity
        │        demo, activity_main.xml, view_extra_glass.xml đã xóa sau khi M6 có số đo 4b thật; 4
        │        file BackdropSource/PickedImageSource/SystemWallpaperSource/WallpaperApplier đã di
        │        trú sang data/backdrop/ từ trước, 2026-10-16, M1 Task 1)
        │
        └─ (không có src/test và src/androidTest — dự án không làm test, chốt 2026-10-16)
```

### Vai trò từng folder (tóm tắt để tra nhanh)

| Folder | Trả lời câu hỏi | Được import gì |
|---|---|---|
| `di/` | Object nào sống ở đâu, ai cấp | tất cả |
| `core/glass/` | Màn này vẽ kính thật hay card mờ | `android.view`, Backdrop (Kyant0) |
| `core/ui/` | Đo lưới, kéo-thả, inset, dp/px | `android.view` |
| `core/bitmap/` | Giải mã, xoay, cắt, làm mờ ảnh; hình icon `IconShape` (TL2, 2026-11-03). Phần giải mã **đã tách ở FX1** (2026-11-11): `BitmapScaler`, `ExifReader`, `TransparentTrimmer`; `data/backdrop/PickedImageSource` chỉ còn lo nguồn ảnh | `android.graphics` |
| `core/designsystem/` | Màn v3 dùng thành phần nào, token nào (DS3, 2026-11-16) | Compose, `android.graphics` (drawable DS), `core/glass` (đọc mức kính và bo góc qua `GlassEnvironment`/`GlassTuning`); không biết về feature nào |
| `domain/model/` | Dữ liệu của app là gì | Kotlin thuần |
| `domain/repository/` | Hợp đồng lấy/ghi dữ liệu | `domain/model` |
| `domain/usecase/` | Một hành vi nghiệp vụ | `domain/*` |
| `data/*` | Dữ liệu đến từ đâu thật sự | Android API, Room, DataStore |
| `ui/<feature>/` | Một màn hình hoặc một overlay | `domain`, `core`, binding |

---

## 4. Manifest dự kiến cho launcher thật

```xml
<uses-permission android:name="android.permission.SET_WALLPAPER" />
<!-- G3, chỉ khi làm TT#18: <uses-permission android:name="android.permission.READ_CONTACTS" /> -->
<!-- G3, chỉ khi làm TT#19: <uses-permission android:name="android.permission.BIND_APPWIDGET" /> -->

<application
    android:name=".LauncherApp"
    android:theme="@style/Theme.Os26Launcher"
    ... >

    <activity
        android:name=".ui.home.HomeActivity"
        android:exported="true"
        android:launchMode="singleTask"
        android:stateNotNeeded="true"
        android:clearTaskOnLaunch="true"
        android:resizeableActivity="true"
        android:screenOrientation="nosensor"
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
</application>
```

Giải thích các chỗ dễ sai:

- Khối `<queries>` (đã thêm ở M2 Task 1B) phải có intent `MAIN` + `LAUNCHER`, nếu không `LauncherApps.getActivityList` chỉ trả vài app hệ thống (đo trên Oppo A3x: 9 app). Xem R8 ở mục 10.

- `configChanges` gồm cả `orientation|screenSize`: launcher **tự xử lý** xoay màn thay vì cho Activity tạo lại, vì tạo lại nghĩa là mọi `LiquidGlassView` phải chụp lại nền (đắt). Cây view tự đo lại khi cửa sổ đổi cỡ; những thứ tính một lần theo cỡ màn (bitmap ảnh nền cắt theo tỉ lệ màn, ảnh chụp của kính, vị trí menu nhấn giữ) do `ui/home/controller/WindowSizeRecovery.kt` hồi phục khi cỡ đứng yên (RR).
- `screenOrientation="nosensor"` (RR, 2026-11-12): luôn theo hướng tự nhiên của máy, bỏ qua cảm biến và khóa xoay của người dùng. Không dùng `locked` (ghim góc xoay đang có: về Home từ game ngang thì Home ngang) hay `portrait` (hướng cố định, dễ bị letterbox ở split-screen). Cài đặt và Ứng dụng ẩn cũng `nosensor`. Android 16 trên máy lớn (sw ≥ 600dp) có thể bỏ qua yêu cầu hướng: ngoài phạm vi.
- `windowSoftInputMode="adjustNothing"` thay cho `adjustResize` hiện tại: bàn phím đẩy layout sẽ làm nền dịch mà kính không biết (B3). Overlay tìm kiếm tự xử lý inset bàn phím.
- Không áp padding system-bar lên root (B2) — inset áp cho dock, page indicator, overlay qua `core/ui/InsetsExt.kt`.
- `category.LAUNCHER` giữ lại để app còn hiện trong danh sách app khi chưa được đặt mặc định.

**Chưa kiểm chứng:** `enableOnBackInvokedCallback` với launcher (Back ở home phải là no-op, không được finish Activity) — cần thử trên máy thật ở Mốc 1.

---

## 5. Lớp kính: `core/glass` (phần quan trọng nhất)

> **Cập nhật 2026-10-28:** mục này đã làm **XONG ở M6** (dock, chỉ dock — không có pill/thư mục/widget nào có UI để wire ở mốc này). Chi tiết số đo và quyết định thật nằm ở [plans/M6-glass-layer.md](plans/M6-glass-layer.md); mục này giữ lại phần thiết kế còn đúng + 2 chỗ **lệch so với dự kiến ban đầu** (đánh dấu rõ dưới đây).

Vấn đề: Quyết định 3 nói mỗi phần tử UI (dock, pill tìm kiếm, thư mục, widget, App Library) có cách hiển thị **khác nhau theo tier**, và Quyết định 5b nói máy tier thấp dùng card nền mờ tĩnh chưa được hiện thực. Nếu để `if (Build.VERSION…)` rải khắp `ui/` thì không sửa nổi.

Giải pháp: một interface duy nhất, hai cách hiện thực, nhà máy chọn giúp.

```kotlin
// core/glass/GlassSurface.kt
interface GlassSurface {
    /** View thật sẽ gắn vào layout; con của nó là nội dung (icon, chữ). */
    val view: ViewGroup
    /** Nguồn nền để khúc xạ hoặc để cắt vùng mờ. */
    fun setBackdrop(source: View?, blurred: BlurredBackdropCache?)
    /** Bật khi nền hoặc chính kính đang di chuyển (B3). Tắt khi đứng yên để tiết kiệm. */
    fun setDynamic(dynamic: Boolean)
    fun setCornerRadius(px: Float)
    /** Gọi khi nền sáng/tối đổi, để UI đổi màu chữ. */
    fun setAppearanceListener(listener: ((isOverLight: Boolean) -> Unit)?)
}
```

| Hiện thực | Dùng khi | Cách làm |
|---|---|---|
| `RealGlassSurface` | tier LENS hoặc CLASSIC_GPU (31+) | Bọc `LiquidGlassView`, set `material`, `enableDynamicBackground` |
| `StaticBlurSurface` | tier CLASSIC_CPU (26-30) | Custom View vẽ đúng vùng tương ứng của bitmap mờ sẵn (`BlurredBackdropCache`), cắt theo bo góc, phủ tint + viền sáng mảnh. Không `RenderEffect` → chạy từ API 26 (B7) |

**Lệch so với thiết kế gốc:** bản vẽ ban đầu (dòng trên, giữ nguyên ý tưởng) nói "còn ngân sách"/"vượt ngân sách" như thể `GlassSurfaceFactory` tham vấn `GlassBudget` cho từng phần tử. Thật tế M6 chỉ wire đúng **1 kính** (dock, quyết định lúc brainstorm, YAGNI — không có pill/thư mục/widget để chia ngân sách), nên `GlassSurfaceFactory.create()` chỉ map thẳng tier → loại surface, **không gọi `GlassBudget`**. `GlassBudget` vẫn tồn tại với số đã chốt (LENS=6, CLASSIC=1) nhưng chưa có ai dùng tới nó — sẽ cần khi M7+ thêm phần tử kính thứ hai.

### Dock: chiều rộng cố định (A10)

Nền dock giữ nguyên kích thước dù còn mấy icon. Ba lý do, hai trong số đó là lý do kỹ thuật của chính thư viện kính:

1. **Khớp iOS.** Người dùng quan sát trên iPhone XS Max (iOS 18): nền dock một độ dài cố định, số app bên trong đổi thì nền không đổi. Lưu ý mức bằng chứng: XS Max dừng ở iOS 18 (iOS 26 bỏ XS, XS Max, XR), nên hành vi của **iOS 26** cho điểm này **chưa kiểm chứng trực tiếp**.
2. **Đổi kích thước kính là đổi vùng chụp nền.** `LiquidGlassView` chụp lại thứ nằm sau nó; đổi `width` hoặc `cornerRadius` buộc nó measure lại và chụp lại. Dock là phần tử đứng yên nên lẽ ra chạy được với `enableDynamicBackground = false` (chụp một lần, rẻ nhất). Cho dock co giãn là tự biến nó thành phần tử động mỗi lần người dùng gỡ hoặc thêm app vào dock, đúng thứ đắt nhất trên máy tier thấp.
3. **Một hình dạng cố định thì `GlassBudget` dễ tính.** Số kính và kích thước kính là hai biến của phép đo 4b; cố định một biến thì số đo còn dùng lại được.

**Hệ quả có chủ ý:** dock 0 icon là một viên kính rỗng vẫn nằm đó, không tự ẩn. Đừng "cải tiến" bằng cách ẩn dock khi trống — làm vậy là đổi bố cục màn hình chính theo số icon, và kính phải dựng lại từ đầu mỗi lần. Nếu lúc làm M6 nhìn thực tế thấy xấu thì mở lại quyết định này, kèm ảnh chụp.

Hai lớp hỗ trợ:

- `GlassTierResolver`: đọc `LiquidGlassView.effectiveApiLevel` (chứ không đọc `Build.VERSION.SDK_INT`, vì AGSL có thể lỗi driver và thư viện tự rơi về classic) + `glass_level_override` → `GlassTier`. **Lệch so với dự kiến ban đầu** (dòng này từng nói "cộng trạng thái high-contrast và tiết kiệm pin"): không tự đọc 2 thứ đó nữa, vì thuộc tính `accessibilityMode=AUTO` (mặc định) của chính `LiquidGlassView` đã tự tôn trọng cả hai ở tầng thư viện — đọc lại ở `GlassTierResolver` là trùng lặp hai tầng logic (chốt lúc code M6 Task 3, 2026-10-28).
- `GlassBudget`: tier → số kính thật tối đa trên một màn. LENS = 6 (Oppo Android 14 giữ ~60 fps đến 6 kính, từ demo). CLASSIC = 1, **đã đo 4b thật trên màn hình chính** (M6 Task 2, 2026-10-28, Vsmart Live 4): `RealGlassSurface` 10.29% Janky (median +9ms so với không kính) → không đạt; `StaticBlurSurface` 2.13% Janky → chốt dùng cho CLASSIC. `ResolveGlassPlanUseCase` (phân bổ ngân sách dock → pill → thư mục mở → thư mục đóng) **không làm ở M6** — chưa có phần tử kính thứ hai để phân bổ, hoãn tới khi M7+/G2 có pill tìm kiếm hoặc thư mục thật.

Nguyên tắc dùng trong `ui/`: feature **không** được `new LiquidGlassView`, chỉ nhận `GlassSurface` từ factory. Nhờ vậy khi làm xong nền mờ tĩnh, hoặc khi đo 4b xong và đổi ngân sách, chỉ sửa `core/glass`.

---

## 6. Mô hình dữ liệu

### Room

Thiết kế chi tiết và lý do từng trường: **[room-schema.md](room-schema.md)** (tạo 2026-10-16, đã hiện thực ở M4 — xem mục 7 của file đó).

Tóm tắt: **3 bảng**.

| Bảng | Vai trò |
|---|---|
| `launcher_items` | một bảng phẳng cho mọi loại item (app, thư mục, shortcut, widget). `container` = `-100` trang home / `-101` dock / `>0` id của thư mục chứa nó. Kéo-thả bất kỳ = `UPDATE` một hàng |
| `home_pages` | `id` + `rank`. Tồn tại để **trang trống** sống được và để **đổi thứ tự trang** mà không phải viết lại mọi item |
| `app_overrides` | component + `userSerial` + `hidden`, `customLabel`, `customIconPath` (Room v2, CF; thay `hidden_apps`). Bảng riêng vì tùy chỉnh là tính chất của app, không phải của một ô trên màn hình |

**Sửa so với bản đầu (2026-10-16):** bản đầu không có `home_pages` và dùng `pageIndex: Int` trên item. Đổi thành `pageId: Long` trỏ vào `home_pages` vì chỉ số trang làm hai việc không thể làm được: giữ trang trống, và reorder trang không chạm vào item. Lý do đầy đủ ở mục 2 của `room-schema.md`.

Quy ước bắt buộc: `exportSchema = true`, schema commit vào `app/schemas/`, **không bao giờ** `fallbackToDestructiveMigration()`, mỗi `Migration` viết tay. Ngoại lệ duy nhất: v1 → v2 (CF, 2026-11-17, app chưa phát hành) phá dữ liệu bằng `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`; từ v2 trở đi lại viết Migration tay. Vì dự án không có test (A7), mỗi lần đổi schema phải thử tay: cài bản cũ → tạo bố cục có thư mục và dock → cài bản mới lên trên → kiểm tra bố cục còn nguyên.

### DataStore Preferences — khóa cài đặt

Từ CF (2026-11-17) mọi khóa, kho, mặc định và màn sửa nằm ở **[config-schema.md](config-schema.md)** (nguồn chân lý; thêm/đổi khóa phải sửa file đó cùng commit). Bảng khóa cũ ở đây đã bỏ để khỏi lệch.

Ảnh nền **không** vào DataStore: vẫn là file trong `filesDir` như `PickedImageSource` đang làm.

---

## 7. `res/` dự kiến và quy ước đặt tên

```
res/
├─ layout/
│   ├─ activity_home.xml            FrameLayout: backdropImage → pageContainer → dockContainer → overlay
│   ├─ item_home_page.xml           IconGridView cho 1 trang
│   ├─ view_dock.xml                nội dung bên trong GlassSurface của dock
│   ├─ view_search_pill.xml
│   ├─ overlay_search.xml
│   ├─ activity_settings.xml  +  fragment_settings_*.xml
│   ├─ dialog_apply_wallpaper.xml
│   └─ activity_main.xml            [có] của demo
├─ values/
│   ├─ colors.xml                   mở rộng: glass_tint_regular, glass_tint_clear, label_on_light, label_on_dark
│   ├─ dimens.xml          [mới]    lưới 8dp: cell_pitch, cell_icon_size, grid_gutter_16, dock_height, touch_min_48
│   ├─ styles.xml          [mới]    TextAppearance.Launcher.IconLabel / PageTitle / SettingRow
│   ├─ strings.xml                  mọi chữ hiển thị, không hardcode trong Kotlin
│   └─ themes.xml                   [có]
├─ values-night/themes.xml          [có]
└─ xml/, drawable/, mipmap-*/       [có]
```

Quy ước (để Figma ↔ XML ↔ Kotlin khớp nhau, giảm công handoff):

| Tầng | Quy ước | Ví dụ |
|---|---|---|
| Figma component | `Màn/Thành phần`, variant theo trạng thái | `Home/IconCell`, `State=Default\|Pressed\|Dragging\|Empty` |
| Layout file | `activity_` / `fragment_` / `view_` / `item_` / `overlay_` / `dialog_` + snake_case | `view_dock.xml` |
| View id | snake_case, ViewBinding tự sinh camelCase | `dock_container` → `binding.dockContainer` |
| Dimen | theo vai trò, không theo số | `cell_pitch` (không phải `dp_88`) |
| Kotlin class | `<Feature><Loại>` | `HomeViewModel`, `HomeUiState`, `IconGridView` |
| State flow | `uiState: StateFlow<HomeUiState>` | 1 state / 1 màn (A4) |

Chuẩn thiết kế áp cho mọi màn: lưới 8dp, vùng chạm tối thiểu 48dp, tương phản WCAG AA. Nhãn icon trên hình nền ảnh phải có bóng hoặc nền mờ vì tương phản không kiểm soát được — `glassAppearanceListener` chỉ đổi được đen/trắng.

### Quy ước khoảng cách và bố cục cho màn v3 (DS3, 2026-11-16)

Áp cho mọi màn dựng theo Figma v3. Token nằm ở `res/values/ds_dimens.xml` (tiền tố `ds_`), không dùng `dimens.xml` cũ của các mốc trước. Bản đầy đủ kèm nguồn Figma: [plans/DS3-design-system-v3.md](plans/DS3-design-system-v3.md) mục "Quy ước khoảng cách và bố cục".

- **Thang khoảng cách giữa các khối:** `ds_space_4` 4 · `ds_space_8` 8 · `ds_space_12` 12 · `ds_space_16` 16 · `ds_space_20` 20 · `ds_space_24` 24 · `ds_space_32` 32. Số lẻ (2, 3, 6, 10, 14) chỉ nằm bên trong thành phần, dưới tên riêng của thành phần.
- **Màn:** lề ngang 16 (`ds_screen_margin_h`); đệm trên nội dung 8, đệm dưới 32 cộng inset thanh hệ thống; khoảng giữa hai nhóm 20 (`ds_section_gap`); tiêu đề nhóm → card 12 (`ds_section_header_gap`); đệm trong card tự do 12 (`ds_card_padding`).
- **Hàng:** cao tối thiểu 56, đệm đầu/cuối 12, đệm dọc 10, khe giữa các phần 12; đường kẻ 1 dp bắt đầu từ mép trái khối chữ; ô icon 32 / icon 24; vùng chạm tối thiểu 48 (`ds_touch_min`).
- **Bo góc 4 vai** đi qua `DsCorner`/`DsCorners`, không đọc thẳng `ds_radius_*`: `CARD` 20, `CARD_LARGE` 24, `SHEET` 28 là bán kính gốc nhân theo thanh "Bo góc" của kính (GT); `TILE` 8 cố định (cơ chế bo góc icon là plan IS). Không thêm khóa cài đặt cho bo góc.
- **Bề mặt và chữ:** bề mặt kính v3 (`CARD`, `CONTROL`, `SHEET`) theo thanh "Độ bóng" của kính; theme v3 chỉ tối; font Be Vietnam Pro; mọi chuỗi hiển thị vào khối `ds_` cuối `strings.xml`.
- Màn dựng theo v3 dùng thành phần `Ds*` trong `core/designsystem`, không tự vẽ lại nền kính. Xem hình thật qua `DsCatalogActivity` (chỉ bản debug): `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.DsCatalogActivity`.

Trạng thái màn hình (bắt buộc định nghĩa trước khi code từng màn):

| Màn | Default | Empty | Loading | Error | Disabled |
|---|---|---|---|---|---|
| Home | lưới + dock + nền | trang trống khi ở chế độ chỉnh sửa | lần đầu quét app (skeleton icon) | không đọc được danh sách app | — |
| Tìm kiếm | kết quả | "không tìm thấy" | đang lọc | — | tắt tìm danh bạ trong cài đặt |
| Hình nền | ảnh hiện tại | chưa chọn ảnh | đang giải mã | ảnh lỗi / không đặt được wallpaper | máy không cho đặt wallpaper (`isSetWallpaperAllowed = false`) |

---

## 8. Tech stack

### 8.1 Đã có trong dự án

| Thành phần | Toạ độ / plugin | Version | Dùng để |
|---|---|---|---|
| AGP | `com.android.application` | **9.3.3** | build; Kotlin tích hợp sẵn (không có `kotlin-android`) |
| Gradle | wrapper | **9.5.0** | |
| JDK toolchain | foojay resolver | **25** | `gradle-daemon-jvm.properties` |
| Kính | `com.github.QWEA0:liquidglass` | **v2.0.11** (ghim) | hiệu ứng liquid glass, JitPack **Gỡ ở JX (2026-11-20).** |
| core-ktx | `androidx.core:core-ktx` | **1.19.1** | |
| appcompat | `androidx.appcompat:appcompat` | **1.8.0** | `AppCompatActivity`; cần cho `LiquidGlassDialogBuilder` |
| material | `com.google.android.material:material` | **1.14.0** | M3 component, cần cho dialog kính **Gỡ ở JX (2026-11-20).** |
| activity-ktx | `androidx.activity:activity-ktx` | **1.13.0** | `enableEdgeToEdge`, Photo Picker contract |
| constraintlayout | `androidx.constraintlayout` | **2.2.2** | layout cài đặt; **không** dùng cho màn có kính (B2) **Gỡ ở JX (2026-11-20).** |
| viewpager2 | `androidx.viewpager2:viewpager2` | **1.1.0** | trang home; cũng là điều kiện của `LiquidGlassTabLayoutMediator` **Gỡ ở JX (2026-11-20).** |
| KSP | `com.google.devtools.ksp` (plugin) | **2.3.11** | Hilt sinh code (thêm ở M0.1). **Không** dùng `2.2.10-2.0.2`: lỗi `kotlin.sourceSets` với built-in Kotlin của AGP 9 |
| Hilt | `com.google.dagger:hilt-android` + `hilt-android-compiler` + plugin `com.google.dagger.hilt.android` | **2.60.1** | DI (A2), thêm ở M0.1. Đã đo chạy được với AGP 9.3.3 + KGP 2.2.10 (R1, R9 không nổ) |
| Lifecycle ViewModel | `androidx.lifecycle:lifecycle-viewmodel-ktx` | **2.11.0** | MVVM (A4), thêm ở M0.3 |
| Lifecycle runtime | `androidx.lifecycle:lifecycle-runtime-ktx` | **2.11.0** | `repeatOnLifecycle` để thu StateFlow an toàn, thêm ở M0.3 |
| DataStore | `androidx.datastore:datastore-preferences` | **1.2.1** | cài đặt (A3), thêm ở M0.3 |
| ViewBinding | — (`buildFeatures { viewBinding = true }`) | — | bật ở M0.2, chưa có màn nào dùng (demo giữ `findViewById`; `HomeActivity` ở M1 là file đầu tiên dùng) **Gỡ ở JX (2026-11-20).** |
| RecyclerView | `androidx.recyclerview:recyclerview` | **1.4.0** | danh sách app tạm (M2 Task 3), sau đó adapter cho ViewPager2 (M3), kết quả tìm kiếm, App Library **Gỡ ở JX (2026-11-20).** |
| Room | `androidx.room:room-runtime` + `room-ktx` + `room-compiler`(ksp) | **2.8.5** | lưu bố cục (A3, TT#4), thêm ở M4 |

### 8.2 Dự kiến thêm

Cột "Mốc" trỏ vào mục 9. Version lấy từ [trang stable channel AndroidX](https://developer.android.com/jetpack/androidx/versions/stable-channel) đọc ngày 2026-10-16.

| Thành phần | Toạ độ | Version | Mốc thêm | Vì sao cần |
|---|---|---|---|---|
| Fragment | `androidx.fragment:fragment-ktx` | **1.9.1** | M7 | các trang trong màn cài đặt (TT#20) |
| Palette | `androidx.palette:palette-ktx` | 1.0.0 | G2 (tuỳ chọn) | lấy tông màu nền để tint kính/thư mục nếu `adaptiveTint` chưa đủ |

Số KGP đã đo trên máy phát triển (2026-10-16): `gradlew.bat buildEnvironment` cho `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` — tức AGP 9.3.3 vẫn mang KGP mặc định như AGP 9.0. KSP khớp theo kiểu cũ là `2.2.10-2.0.2` nhưng **không dùng được** với built-in Kotlin của AGP 9 (lỗi `kotlin.sourceSets`, đo ở M0.1); dự án dùng KSP `2.3.11`, bản đánh số độc lập với Kotlin từ 2.3.0.

Lệnh đo lại khi nâng AGP (chạy ở gốc dự án):

```bat
:: CMD
gradlew.bat buildEnvironment | findstr /i "kotlin-gradle-plugin symbol-processing"
```

```powershell
# PowerShell
.\gradlew.bat buildEnvironment | Select-String -Pattern 'kotlin-gradle-plugin|symbol-processing'
```

**Không** dùng `gradlew.bat --version` cho việc này — dòng `Kotlin:` ở đó là Kotlin nhúng của Gradle DSL, không phải KGP.

**Rủi ro kèm theo (R9, xem mục 10):** Dagger 2.60 được build bằng Kotlin 2.3.21 trong khi dự án chạy KGP 2.2.10. **Đã đo ở M0.1 (2026-10-16): không nổ**, Hilt sinh code và graph chạy được.

Không dự kiến dùng:

| Thứ | Lý do |
|---|---|
| Jetpack Compose | B1 — thư viện kính là View-based |
| Coil / Glide | icon app lấy từ `LauncherApps`, không phải ảnh mạng; `IconCache` tự viết nhẹ hơn và kiểm soát được bộ nhớ |
| Navigation component | 1 Activity giữ HOME + vài Activity rời; thêm navigation là bớt kiểm soát mà không được gì |
| kapt | KSP đủ cho Room và Hilt, kapt chậm hơn và đang có cảnh báo với built-in Kotlin của AGP 9 |
| WorkManager | chưa có việc nền định kỳ (thời tiết đang đóng băng, TT#14) |
| junit / espresso / coroutines-test / Robolectric / Turbine | **dự án không làm test** (chốt 2026-10-16) — kiểm chứng bằng chạy tay trên Oppo A3x (Android 14) và Vsmart Live 4 (Android 11) |
| Retrofit / OkHttp | chỉ cần khi mở lại TT#14 (thời tiết) hoặc TT#16 (bộ sưu tập hình nền) |

---

## 9. Thứ tự triển khai

Mỗi mốc = 1 file kế hoạch trong `docs/plans/`, code từng phần nhỏ, báo cáo, chờ bạn xác nhận rồi mới tick và sang phần sau (theo quy ước dự án).

| Mốc | Nội dung | Tính năng | Điều kiện coi là xong |
|---|---|---|---|
| **M0** ✅ | Hạ tầng: ViewBinding, KSP `2.3.11`, Hilt 2.60.1, DataStore, lifecycle, `LauncherApp`, `SettingsRepository` (2 khóa) | — | `gradlew.bat assembleDebug` xanh, demo vẫn chạy như trước trên Oppo. **Xong 2026-10-16**, xem [plans/M0-infrastructure.md](plans/M0-infrastructure.md) |
| **M1** ✅ | `HomeActivity` + HOME intent-filter + phát hiện launcher mặc định + di trú `backdrop/` sang `data/` | TT#1, TT#6 | đặt được làm launcher mặc định, nền ảnh hiện lại sau khi mở lại app. **Xong 2026-10-16** (Oppo A3x và Vsmart Live 4), xem [plans/M1-home-activity.md](plans/M1-home-activity.md) |
| **M2** ✅ | `LauncherAppsDataSource` + `IconCache` + `AppRepository` | TT#2 | danh sách app đúng, cài/gỡ app tự cập nhật. **Xong 2026-10-26** (Task 1, 1B, 2 đo trên Oppo A3x và Vsmart Live 4; Task 3, 4 đo trên Vsmart Live 4), xem [plans/M2-app-list.md](plans/M2-app-list.md) |
| **M3** ✅ | `GridMetrics` + `IconCellView` + `IconGridView` + ViewPager2 nhiều trang + `DockView` **phẳng** (không kính) + nạp nhãn song song | TT#3 | mở app được từ lưới, cold start đo bằng ms/app, đo trên Live 4. **Xong 2026-10-27** (đo trên Vsmart Live 4), xem [plans/M3-icon-grid.md](plans/M3-icon-grid.md). Danh sách debug của M2 (`AppListAdapter`, `item_debug_app.xml`, `ui/debug/`) đã xóa ở Task 5 |
| **M4** ✅ | Room + `LayoutRepository` + đồng bộ cài/gỡ/tạm-khóa app | TT#4 | kéo icon xong, kill app, mở lại vẫn đúng chỗ; cài/gỡ app (cả lúc launcher không chạy) tự đồng bộ; app bị `disable-user` giữ nguyên chỗ. **Xong 2026-10-27** (đo trên Vsmart Live 4), xem [plans/M4-layout-persistence.md](plans/M4-layout-persistence.md) |
| **M5** ✅ | Khóa xoay, sáng/tối theo theme hệ thống, system bar trong suốt, polish lưới + dock (dock có hàng riêng, căn giữa, nút mặc định lên góc trên phải) | TT#5 | xoay không đổi hướng, sáng/tối đúng, lưới co theo split-screen, dock không che hàng 6. **Xong 2026-10-27** (đo trên Vsmart Live 4), xem [plans/M5-orientation-theme.md](plans/M5-orientation-theme.md). **Phép đo nền tĩnh 4b KHÔNG làm ở M5** — đã gom sang M6 theo A9 (chốt 2026-10-26); hàng M5 cũ ghi nhầm 4b ở đây. Chưa thử tay: dock đủ 4 icon thẳng cột với lưới (Chrome rời dock từ M4 nên dock đang 3 icon) |
| **M6** ✅ | **Mốc kính duy nhất** (chốt 2026-10-26): `core/glass` (`GlassTier`, `GlassTierResolver`, `GlassSurface`, `GlassBudget`, `GlassSurfaceFactory`), `RealGlassSurface` cho dock tier LENS/CLASSIC_GPU, `StaticBlurSurface` cho tier CLASSIC_CPU, khóa `glass_level_override`, **và phép đo 4b** | Quyết định 3, 5b, 6 | Live 4 không giật ở màn chính (StaticBlurSurface 2.13% Janky, so với kính thật 10.29%); có số 4b để chốt `GlassBudget` (CLASSIC=1). **Xong 2026-10-28** (đo trên Oppo A3x + Vsmart Live 4), xem [plans/M6-glass-layer.md](plans/M6-glass-layer.md). `ResolveGlassPlanUseCase`/`GlassModule.kt` không làm (xem mục 5, mục 3) — phạm vi chỉ dock |
| **M7** ✅ | Nhấn giữ icon (menu + shortcut), chế độ chỉnh sửa, kéo-thả (trang, giữa trang, dock ↔ lưới, lật trang tự động, trang tạm), dấu "−" ẩn nhanh (dồn trang), màn Thư viện ứng dụng (Chưa ẩn / Đã ẩn) | TT#7, TT#8 | kéo-thả/ẩn/bỏ ẩn/menu đúng và sống qua khởi động lại; đo trên Vsmart Live 4 và Oppo A3x. **Xong 2026-10-29**, xem [plans/M7-edit-mode.md](plans/M7-edit-mode.md) |
| **M8a** ✅ | Màn Cài đặt (mở từ edit mode): launcher mặc định, cỡ lưới theo preset 4×5/4×6/5×6 kèm re-flow bố cục, bật/tắt nhãn, mức kính (Tự động/Ép thấp/Ép cao), đổi ảnh nền (hỏi áp wallpaper mỗi lần), lối vào app đã ẩn | TT#20 | cỡ lưới đổi được và bố cục đã lưu xếp lại đúng, không mất icon; mức kính áp lại sau khi đổi; đo trên Vsmart Live 4 (kính: Oppo A3x). **Xong 2026-10-30** (đo trên Vsmart Live 4; kính và ảnh nền đo thêm trên Oppo A3x), 7 Task, xem [plans/M8a-settings.md](plans/M8a-settings.md). Không đổi schema Room |
| **M8b** ✅ | Tìm kiếm app: vuốt xuống ở giữa màn hình mở overlay (scrim tối + ô tìm kiếm phẳng, tự tính inset bàn phím), chỉ app đã cài trừ app ẩn, khớp bỏ dấu tiếng Việt + đầu từ/chuỗi con | TT#9 | **Xong 2026-10-30** (Live 4; Oppo A3x 344 app chưa đo), 4 Task, xem [plans/M8b-search.md](plans/M8b-search.md). Không đổi schema Room và DataStore. Danh mục Cài đặt hệ thống, shortcut, gợi ý/app dùng gần đây: để sau (chốt 2026-10-29) |
| **M8c** ✅ | Pill tìm kiếm lồng pill chấm trang (thành chấm khi đang lướt, về ô tìm kiếm khi dừng) | TT#9 | **Xong 2026-10-30** (Live 4; TalkBack chưa kiểm), 3 Task, xem [plans/M8c-search-pill.md](plans/M8c-search-pill.md). `SearchPillView` thay `PageIndicatorView` (file đã xóa, cùng dimens `home_indicator_margin_vertical`); pill phẳng, chưa dùng kính. Kèm sửa lỗi gỡ app làm dồn kín bố cục (Q8, `room-schema.md`) |
| **G2a ✅** | Thư mục (kéo icon đè lên icon tạo thư mục, mở/đóng, đổi tên) + App Library (tự phân nhóm app, luật dự phòng khi nhóm của nhà phát triển trống) | TT#10, TT#11 | Plan: [G2a-folders-library.md](plans/G2a-folders-library.md) (Phần A Thư mục: A1a ✅, A1b ✅, A2 ✅, A3a ✅, A3b ✅ A4 ✅ A5 ✅ (Phần A xong); Phần B App Library: B1 ✅, B2 ✅ (lớp phủ ngoài pager, D17), B3 ✅, B4 ✅; G2a xong 2026-10-30). G2a dùng nền phẳng; việc áp kính tách sang mốc "Áp kính" bên dưới |
| **TL1 ✅** | Góp ý tech lead: vuốt trang chậm và mượt hơn (sửa giật do kính dock vẽ lại mỗi khung cuộn), rung icon ±2°, nút Cài đặt/Xong nổi kiểu iOS không đẩy lưới, ẩn thanh trạng thái khi chỉnh sửa | — | Plan: [TL1-home-polish.md](plans/TL1-home-polish.md), xong 2026-11-03 (PR #2) |
| **TL2 ✅** | Góp ý tech lead: bo góc icon kiểu iOS cho mọi icon app, `ICON_SCALE` 0.9 | TT#12 (phần hình dạng) | Plan: [TL2-icon-shape.md](plans/TL2-icon-shape.md), xong 2026-11-03 (PR #1) |
| **G2b ✅** | Kiểu icon (tô màu, 3D) + lựa chọn trong Cài đặt | TT#12, TT#20 (phần kiểu icon) | Plan: [G2b-icon-styles.md](plans/G2b-icon-styles.md), xong 2026-11-04 (PR #4) |
| **G2c** ✅ | Widget tự vẽ: đồng hồ, lịch (không gồm thời tiết) | TT#13 | Merged 2026-11-05 (#12), xem `plans/G2c-clock-calendar-widgets.md` |
| **GL ✅** | Áp kính: ô và panel thư mục, ô nhóm + panel nhóm App Library, thanh tìm kiếm, pill; Library và nhãn Home co theo cửa sổ | TT#6, TT#10, TT#11 | Plan: [GL-glass-pass.md](plans/GL-glass-pass.md), xong 2026-11-04 (PR #3) |
| **R1 ✅** | Tách `HomeActivity` (962 → ~200 dòng) thành controller, không đổi hành vi | — | Plan: [R1-home-controllers.md](plans/R1-home-controllers.md), xong 2026-11-04 (PR #5, #7) |
| **AN** ✅ | Hoạt ảnh và hành vi kiểu iOS: spring mở/đóng panel thư mục và panel nhóm, icon trượt/co/nở, kính hover khi kéo, đẩy-dồn có trễ 500 ms; bấm icon lớn và mini trong ô nhóm mở app, "+N" mở panel; sửa kính khi đổi ảnh nền và dock 0×0 | — | Merged 2026-11-05 (#6), xem `plans/AN-polish-animation.md` |
| **G2d** ✅ | Cài đặt dạng trung tâm: trang chủ chia 6 nhóm theo bản nháp Figma + màn con cho từng mục; mục chưa có màn hiện mờ "Sắp có"; chân trang phiên bản (`versionName` 1.0.0, `versionCode` 1). Không thêm tính năng cài đặt mới, giữ kiểu hiển thị cũ | TT#20 (cấu trúc) | Plan: [G2d-settings-hub.md](plans/G2d-settings-hub.md), knowledgement cùng tên; xong 2026-11-12 (PR #47). Không đổi schema Room, không thêm khóa DataStore |
| **G3a** | Tìm danh bạ trong ô tìm kiếm (quyền `READ_CONTACTS`, xử lý trên máy, bật trong Cài đặt) | TT#18 | Chốt khi viết plan |
| **G3b** | Widget ảnh (bố cục Mosaic, chọn và sửa ảnh) | TT#15 | Chờ chủ dự án trả lời phạm vi (tách khỏi TT#16 ngày 2026-11-10) |
| **G3f** | Bộ sưu tập hình nền, tự đổi, báo hình mới | TT#16 | Chặn: chưa quyết nguồn nội dung/máy chủ |
| **G3c** | AppWidgetHost (chứa widget của app khác) | TT#19 | Cần `BIND_APPWIDGET`; kiểm chứng kính quanh widget của app khác |
| **G3d** | Bảng tiện ích kiểu Trung tâm điều khiển | TT#21 | Tùy chọn; Android hạn chế bật/tắt Wi-Fi |
| **G3e** | Thời tiết (một vị trí rồi nhiều vị trí) + thông báo thời tiết — **chỉ khi hết đóng băng** | TT#14, TT#17 | Chờ ý kiến lead/sếp |
| **DS3 ✅** | Hệ thống thiết kế v3: token màu/chữ/khoảng cách/bo góc, font Be Vietnam Pro, bề mặt kính xấp xỉ bằng drawable, bộ thành phần nền trong `core/designsystem`, catalog chỉ ở bản debug. Bo góc card/sheet nhân theo thanh "Bo góc" của kính (GT), ô icon cố định; không thêm khóa cài đặt. Chưa áp vào màn nào | — (Figma v3, `Figma-Draft.md`) | Plan: [DS3-design-system-v3.md](plans/DS3-design-system-v3.md); duyệt 2026-11-16, **Xong 2026-11-17** (Task 1–7, kiểm cả tính năng trên Vsmart Live 4; giống Figma ở mức nhìn, lệch tiểu tiết; chưa đo APK release). Không đổi schema Room |
| **PB ✅** | Polish đợt 1: hỏi đặt launcher mặc định khi quay về Home, công tắc tìm lối tắt/danh bạ hỏi quyền ngay, ẩn mục Cài đặt chưa có màn + nhóm Liên hệ, ẩn thanh điều hướng (màn Chung), widget thời tiết theo bố cục card 1.4 (nền kính giữ nguyên), bỏ chữ hướng dẫn `local.properties` | TT#20, TT#9, TT#14 | Plan: [PB-polish-batch-1.md](plans/PB-polish-batch-1.md); **Xong 2026-11-18** (Live 4; Oppo chưa kiểm ẩn thanh điều hướng). Không đổi Room, không khóa mới |
| **LO ✅** | Màn 3.2 Thư viện ứng dụng: công tắc bật thư viện, kiểu hiển thị Thư mục danh mục / Danh sách A–Z (cột chữ cái kéo nhanh). Tự phân loại và Gợi ý & Mới thêm đã code nhưng tạm ẩn | TT#11, TT#20 | Plan: [LO-library-settings.md](plans/LO-library-settings.md); **Xong 2026-11-18** (Live 4). Không đổi Room, không khóa mới |

> **Chia nhỏ G2/G3 (2026-10-30):** bản đầu gộp G2 (TT#10–13) và G3 (TT#14–19, 21) thành hai mốc lớn; tách theo từng cụm có cùng phụ thuộc để mỗi mốc đủ nhỏ viết plan và đo trên máy. Thứ tự trong mỗi nhóm chưa phải thứ tự làm.

**Tệp thêm ở DS3 (2026-11-16, không có trong bản vẽ gốc):** `core/designsystem/` (23 file `Ds*`, `AuroraBackgroundDrawable`); `res/values/ds_colors.xml`, `ds_dimens.xml`, `ds_type.xml`, `ds_themes.xml` (+ `values-v27/ds_themes.xml`), `ds_attrs.xml`, `ds_styles.xml`, `ds_ids.xml`; `res/font/be_vietnam_pro*` (4 ttf + `be_vietnam_pro.xml`, giấy phép `docs/third_party/BeVietnamPro-OFL.txt`); `res/drawable/ds_*.xml` (24 icon, công tắc/radio/thanh trượt, tay nắm sheet); `res/layout/ds_*.xml`; khối chuỗi `ds_` cuối `strings.xml`. Catalog chỉ debug: `app/src/debug/.../debug/DsCatalogActivity.kt` + layout. Không thêm dependency, không thêm khóa DataStore, không đổi schema Room. Quy ước: mục 7 ("Quy ước khoảng cách và bố cục cho màn v3"); bài học: `lessons/design-system.md`.

**Tệp thêm ở LO (2026-11-18, màn 3.2 Thư viện):** `ui/settings/LibrarySettingsScreen.kt`, `SettingsRow.LibraryStyle` + `item_settings_library_style.xml` + drawable `lib_style_folders`/`lib_style_list`; `domain/model/AppCategory.kt` thêm `CategoryGroup.SUGGESTED/RECENTLY_ADDED/ALL` (`REGULAR` = 8 nhóm thường) và `LibraryContent`; `domain/category/LibraryArranger.kt` (A–Z, Gợi ý, Mới thêm; hàm thuần); `GetCategoryGroupsUseCase` trả `LibraryContent`; `AppItem.firstInstallMillis`; `ui/home/view/LibraryListAdapter.kt`, `LetterIndexView.kt`, layout `item_library_list_letter`/`item_library_list_app`; `AppLibraryController.setEnabled`. Cờ tạm ẩn: `LibrarySettingsScreen.SHOW_FOLDER_SWITCHES`, `GetCategoryGroupsUseCase.FOLDER_SWITCHES_ENABLED`.

**Tệp thêm ở PB (2026-11-18, polish đợt 1):** `ui/home/controller/DefaultLauncherPromptController.kt` (hộp "Đặt làm màn hình chính" ở `onResume` của Home); `SystemBarsController.setNavBarHidden`/`reapplyNavBar`; `SettingsHubLayout.visibleGroups` + cờ `CONTACT_GROUP_ENABLED`; `SettingsRow.ID_NAV_BAR_HIDDEN`, bỏ `ID_SEARCH_SHORTCUTS_HINT`/`ID_SEARCH_CONTACTS_HINT`; `WeatherRenderer` viết lại bố cục; chuỗi `settings_nav_bar_hidden(_description)`, bỏ `weather_no_key_hint`. Không thêm khóa, không đổi Room, không dependency mới.

**Tệp thêm ở EF (2026-11-18, sửa mất tràn viền sau `recreate()` trên Tecno Spark 40):** `core/ui/EdgeToEdgeFix.kt` (`reassertEdgeToEdge(window)`), gọi sau `setContentView` ở `HomeActivity`, `SettingsActivity`, `OnboardingActivity`, `AppLibraryActivity`, `GlassTuningActivity`, `ThemeSettingsActivity`. Bài học: `lessons/home-pager-animation.md` mục EF.

**Tệp thêm ở LG (2026-11-18, ngôn ngữ app):** `ui/language/AppLanguages.kt` (danh mục 9 ngôn ngữ theo thứ tự Figma 1.3, `current()`/`effective()`/`apply()` qua `AppCompatDelegate.setApplicationLocales`); `core/locale/AppLocaleContext.kt` (bọc context không phải Activity theo ngôn ngữ app ở API < 33, kho phụ SharedPreferences `app_locale`); `ui/settings/LanguageSettingsScreen.kt` (màn 1.3) + `SettingsRow.Search` (ô tìm ngoài card) + `Radio.titleText/descriptionText`; `res/xml/locales_config.xml`; `res/values/strings.xml` thành **tiếng Anh** (dự phòng), tiếng Việt ở `values-vi/`, bản máy dịch ở `values-es`, `values-pt` (Brazil), `values-in` (tag `id`), `values-th`, `values-ja`, `values-ko`, `values-hi`; manifest `localeConfig` + `AppLocalesMetadataHolderService`; `bundle { language { enableSplit = false } }`. Chip ngôn ngữ ở `activity_onboarding.xml` (`onb_language`). Plan: [plans/LG-languages.md](plans/LG-languages.md).
**Tệp thêm ở JC (2026-11-19, thử Compose + kính Backdrop, hướng B):** `core/designsystem/compose/` (`DsText.kt` kiểu chữ DS, `DsViews.kt` nhúng `DsNavBar`/`DsButton`/`DsBadgeView` bằng `AndroidView`, `DsAuroraBackground.kt`), `core/glass/compose/DsGlass.kt` (`Modifier.dsGlass`, kính Backdrop từ API 31, dưới đó `DsSurfaceDrawable`), `ui/settings/ThemeSettingsContent.kt` (màn 1.1 bằng Compose; `ThemeSettingsActivity` gọi `setContent`), `src/debug/.../ComposeHomeProbeActivity.kt` (probe Home, chỉ debug); bỏ `res/layout/activity_theme_settings.xml`. Build: KGP 2.4.10, Compose BOM `2026.09.00`, `io.github.kyant0:backdrop:2.0.1`. Plan: [plans/JC-compose-pilot.md](plans/JC-compose-pilot.md).

**Tệp thêm ở JD (2026-11-19, design system Compose, hướng B):** `core/designsystem/compose/` (`DsScreen`, `DsText` 11 kiểu chữ, `DsPress`, `DsButton`, `DsSmall` chip/badge/ô icon, `DsNavBar`, `DsSearchField`, `DsRow` hàng/card nhóm/công tắc, `DsSegmented`, `DsSlider`, `DsDialogs` hộp thoại/sheet; bỏ `DsViews`), `core/glass/compose/GlassLookState.kt` + `DsGlass.kt` (theo `GlassLook`, viền sáng, `LocalDsBackdrop`, kính thật chỉ CARD/SHEET), `GlassTuning.backdrop*`, `src/debug/.../DsComposeCatalogActivity.kt`; màn 1.1 dựng trên DS Compose; `baseline-prof.txt` thêm `com/kyant/**`. Plan: [plans/JD-compose-design-system.md](plans/JD-compose-design-system.md).

**Tệp thêm ở JH1 (2026-11-19, Home Compose tĩnh, hướng B):** `ui/home/compose/` (`HomeScreen` khung + ảnh nền + pager/pill/dock, `HomeGridPage` đặt item theo `GridMetrics.Cells` + ô app/thư mục/widget app, `HomeWidgetCell` `WidgetRenderer` trên canvas Compose, `HomeSearchPill` pill + chấm `PageDotsLayout`, `HomeDock`, `HomeIcons` nạp + đệm icon), `src/debug/.../ComposeHomeActivity.kt` (Home Compose chạy song song, chỉ debug), `app/src/test/` (101 unit test JVM cho `domain/layout/*` và `GridMetrics`; thêm `junit` vào `libs.versions.toml`). Plan: [plans/JH1-compose-home-static.md](plans/JH1-compose-home-static.md).

**Tệp thêm ở JH2 (2026-11-19, edit mode + kéo-thả Home Compose):** `ui/home/compose/HomeDragState.kt` (phiên kéo, xem trước PUSH/MERGE, dock, lật mép, trang tạm), `HomeDragGesture.kt` (cử chỉ gốc), `HomeDragLayer.kt` (ảnh nổi), `HomeEditMode.kt` (rung, dấu "−", thanh nút); `HomeGridPage`/`HomeDock`/`HomeScreen` nối xem trước và edit mode; `debug/ComposeHomeActivity` nối `HomeViewModel`. Plan: [plans/JH2-compose-home-drag.md](plans/JH2-compose-home-drag.md).

**Tệp thêm ở JH3a (2026-11-19, nối Activity của Home Compose):** `ui/home/compose/HomeBadges.kt` (đếm + vẽ huy hiệu thông báo); `HomeDragGesture.kt` thêm vuốt xuống mở tìm kiếm (`HomeSearchSwipe`); `debug/ComposeHomeActivity` thành `AppCompatActivity` với hộp thoại launcher/ảnh nền, Back/Home, lớp tìm kiếm View. Plan: [plans/JH3a-compose-home-wiring.md](plans/JH3a-compose-home-wiring.md).

**Tệp thêm ở JH3b (2026-11-19, menu + thư mục Home Compose):** `ui/home/compose/HomeIconMenu.kt` (menu nhấn giữ), `HomeFolderPanel.kt` (bảng thư mục, đổi tên); `HomeDragState`/`HomeDragGesture` thêm menu và ô con thư mục; `HomeScreen` làm mờ Home khi có lớp phủ. Plan: [plans/JH3b-compose-home-menu-folder.md](plans/JH3b-compose-home-menu-folder.md).

**Tệp thêm ở JH3c (2026-11-20, App Library + widget app Home Compose):** `src/debug/.../ComposeLibraryHost.kt` (Thư viện View + cử chỉ), `ui/home/compose/HomeAppWidgetCell.kt` (widget app, `LocalHostedWidgets`); `HomeScreen` thêm `libraryProgress`, `HomePagerInfo`. Plan: [plans/JH3c-compose-home-library.md](plans/JH3c-compose-home-library.md).

**Thay đổi ở JH3d (2026-11-20, widget Home Compose):** `ui/home/controller/WidgetPickerController.kt` nhận `WidgetPickerHost` thay `ActivityHomeBinding` (Home View nối nút "Tiện ích" vào `show()`); Home Compose có nút "Tiện ích", bấm widget, lật tới trang có widget mới; `debug/ComposeHomeActivity` dựng bảng chọn, luồng thêm widget app, bảng Thời tiết. Plan: [plans/JH3d-compose-home-widgets.md](plans/JH3d-compose-home-widgets.md).

**Thay đổi ở JH4 (2026-11-20, chuyển Home):** `ui/home/HomeActivity.kt` = Home Compose (chuyển từ `src/debug/.../ComposeHomeActivity.kt`, thêm Onboarding, RecreateGuard, ẩn thanh điều hướng, công tắc dự phòng); `ui/home/ViewHomeActivity.kt` = Home View cũ (không có bộ lọc HOME); `ui/home/ComposeLibraryHost.kt` (chuyển từ debug); `src/debug/.../HomeModeDebugReceiver.kt`. Plan: [plans/JH4-compose-home-switchover.md](plans/JH4-compose-home-switchover.md).

**Thay đổi ở JS + JX (2026-11-20, toàn bộ UI sang Compose, gỡ lớp View):** mọi màn còn lại dựng lại bằng Compose (`ui/settings/compose/`, `ui/home/compose/library/`, `ui/search/compose/`, `ui/onboarding/compose/`, `ui/photo/compose/`, `ui/controlcenter/compose/`, `ui/wallpaper/compose/`; DS thêm `DsDialogHost`, `DsTextField`, `DsProgress`, `DsDrawable`, `DsClock`). JX xoá `ui/home/view/`, `ViewHomeActivity`, các controller của Home View, adapter/View của Cài đặt/Tìm kiếm/Onboarding/ảnh widget/CC, DS View (`core/designsystem/Ds*View`, `DsButton`…), `core/glass/{GlassSurface,GlassSurfaceFactory,RealGlassSurface,StaticBlurSurface}`, cả `res/layout`, debug `HomeModeDebugReceiver`/`DsCatalogActivity`/`ComposeHomeProbeActivity`, và 113 resource không dùng. `NotificationBadges`, `PageDotsLayout`, `AppLibraryMotion` chuyển sang `ui/home/compose/`; `WallpaperTarget.labelRes()` sang `ui/home/WallpaperTargetLabels.kt`. Dependency gỡ: Material, ConstraintLayout, ViewPager2, RecyclerView, QWEA0, ViewBinding, JitPack. Plan: [plans/JS-compose-remaining-screens.md](plans/JS-compose-remaining-screens.md), [plans/JX-compose-cleanup.md](plans/JX-compose-cleanup.md). Knowledgement: [knowledgements/JS-JX-compose-all-screens.md](knowledgements/JS-JX-compose-all-screens.md).

**Tệp thêm từ khi chuyển repo Compose (2026-11-23 → 2026-11-25, đối chiếu `git log --diff-filter=A 2615d5d..HEAD`, DC):**
- TF/LD (anh dvha, `88676e2`): `domain/usecase/RemoveFromHomeUseCase.kt` ("Xóa khỏi Màn hình chính" = cột `offHome`, Room v3); `ui/home/compose/library/LibraryDragGesture.kt` (nhấn giữ app trong Thư viện → chế độ sửa, kéo ra Home; nhận chạm ở `HomeActivity.dispatchTouchEvent`).
- PG1 (`34ad12b`): `ui/home/compose/HomeEnterExit.kt` (hoạt ảnh vào / ra khi danh sách item của trang, dock, thư mục đổi không qua kéo-thả), `PanelStyleMotion.kt` (kiểu chuyển cảnh của panel thư mục và màn nhóm Thư viện). PG2: bảng chọn widget thành lớp phủ trong cây Compose (`HomeWidgetPicker.Render`, `DsBottomSheetLayer`), kéo thẻ ra lưới. PG3 (`6cb1ea4`): `core/glass/compose/DsFrosted.kt` (kính mờ tĩnh cho ô thư mục, pill, bề mặt dưới API 31).
- PG4 + SW (`843c005`, `181a9a6`): build type `perf` (`app/build.gradle.kts`; `src/perf/.../di/ContactsModule.kt` chép từ `src/release`); `core/perf/PerfSwitches.kt` (công tắc đo `debug.os26.perf`), `core/perf/HomePerfLog.kt` (log `SwFrame` / `SwPage` / `SwDock`, chỉ debug / `perf`); `core/glass/compose/DsGlassCache.kt` (`CachedGlass`: kính dock vẽ một lần); `tools/perf/measure-home.ps1` (script đo).
- Anh dvha (`a350f1a`): `core/glass/compose/DialogGlass.kt` (`DialogWindowGlass`: kính cho cửa sổ `Dialog`).
- WF (`9240225`): `ui/home/compose/HomeRecentsWidget.kt` (widget "App gần đây" trên lưới Home, `WidgetKind.RECENTS`, nguồn `LocalHomeRecents`). PD (cùng commit): thẻ xác nhận xóa (`RemoveAppDialog.kt`, `RemoveAppOverlay`) vẽ ở `HomeActivity` là lớp trên cùng, dùng chung cho Home / Thư viện / Today; `DsBottomSheetLayer` thêm `maxHeightFraction`, `retracted`.

**Tệp thêm ở RS (2026-11-18, đổi giao diện Cài đặt sang v3):** `ui/settings/` — `SettingsCardDecoration` (card kính cho mỗi nhóm hàng; `CardKind` + `CardKindSource` để adapter khác dùng lại, như `AppLibraryAdapter`), `SettingsDialogs` (`singleChoice`, `confirm`, `actionSheet` trên `DsDialog`/`DsBottomSheet`), `ThemeSettingsActivity` + `ThemeSettingsViewModel` (màn 1.1), `WeatherPreviewMock` (số liệu mẫu card thời tiết 1.4); `ui/wallpaper/` — `WallpaperTileView` (ô ảnh nền, dùng lại được ở 0.3), `WallpaperPlaceholders` (5 màu của 0.3); layout `activity_theme_settings`, `item_settings_launcher_card/radio/segmented/weather_preview`; drawable `bg_weather_preview`. `SettingsRow` thêm `LauncherCard`, `Radio`, `Segmented`, `WeatherPreview`, `WallpaperGrid` và trường `tile` (`SettingsTile`); `SettingsScreen` thêm `onRadioClicked`, `onSegmentChanged`, `onWallpaperTileClicked`, `onLauncherCardClicked`; `SettingsUiState` thêm `themeId`, `wallpaperKind`, `wallpaperColor`. `DsSliderRow` thêm `setOnTrackingChangeListener`. Theme `Theme.Os26Launcher.Ds` cho `SettingsActivity`, `ThemeSettingsActivity`, `AppLibraryActivity`, `GlassTuningActivity`, `ControlCenterSettingsActivity` (mượn khung). Đã bỏ: `bg_settings_group_*` (4 drawable), chuỗi `wx_unit_header`. Không thêm khóa, không đổi Room, không dependency mới. Bài học: `lessons/design-system.md` (mục RS), `AGENTS.md` (2 mục RS).

**Tệp thêm ở OB (2026-11-17, Onboarding 0.0–0.5):** `ui/onboarding/` (`OnboardingActivity`, `OnboardingViewModel`, `OnboardingStep` + `OnboardingEvent`, `OnboardingPages` (5 trang), `OnboardingCatalog` (`ThemeCatalog`, `WallpaperSwatches`), `ThemeCardView` + `RatioFrameLayout`, `OnboardingRuntime` (cờ trong process khi đặt mặc định)); layout `activity_onboarding`, `onboarding_welcome/theme/wallpaper/default_launcher/done`, `view_onboarding_theme_card`; `res/drawable-nodpi/onb_*.webp` (7 ảnh xuất từ Figma), `splash_logo.webp`, `splash_logo_v31.webp`; drawable `splash_background`, `onb_check_circle`, `onb_ic_check`; theme `Theme.Os26Launcher.Starting` (`values`, `values-v27`, `values-v31`) gắn cho `HomeActivity`; `AuroraSpot`/`AuroraSpots` trong `AuroraBackgroundDrawable`. Sửa: `HomeActivity` (theme khởi động, mở Onboarding ở lần dựng đầu, che nội dung bằng khung tối, dọn task cũ khi nhận HOME), `DefaultLauncherAction` nhận `DefaultLauncherRepository`. Không thêm khóa, không đổi Room, không dependency mới. Bài học: `lessons/home-pager-animation.md` (mục OB).

**Tệp thêm ở CF (2026-11-17, tái cấu trúc config):** `domain/config/` (`AppearanceConfig`, `GeneralConfig`, `HomeScreenConfig`, `SearchConfig`, `MotionConfig`, `AppState`, `ConfigEnums`, `ConfigRepositories`, `ThemePreset`); `data/config/` (`ConfigKeys`, `ConfigStore`, 6 repository `*Impl`); `di/ConfigModule`; `domain/usecase/ApplyThemeUseCase`, `ObserveThemeStateUseCase`; `data/backdrop/ColorBackdropSource` (nền màu đặc); Room v2: `data/layout/db/AppOverrideEntity` (+ `AppLabelRow`), `AppOverrideDao`, `HiddenAppDao` thành `abstract class` trên `app_overrides`, `app/schemas/.../2.json`; receiver chỉ debug `app/src/debug/.../debug/ThemeDebugReceiver` (`CF_APPLY_THEME`, quyền `DUMP`). Đã bỏ: `data/settings/*`, `SettingsRepository`, `HomeTogglesRepository`, `SearchPreferencesRepository` (+ model và Impl), `di/HomeTogglesModule`, DataStore `app_labels`. Sơ đồ khóa: `config-schema.md`; bài học: `lessons/settings-and-data.md`, `lessons/home-pager-animation.md` (mục CF).

**Tệp thêm ở M7 (không có trong bản vẽ gốc):** `domain/layout/` — `GridInsertPlanner`, `GridRemovePlanner`, `DockInsertPlanner` (hàm thuần dùng chung cho ghi DB và cập nhật lạc quan của UI); `domain/model/` — `DockApp`, `PageId`, `ContainerId`, `AppLibrarySection`, `AppShortcutItem`; `domain/repository/ShortcutRepository` + `data/apps/ShortcutRepositoryImpl`; `core/ui/` — `DragHelper`, `DragDropTarget`; `ui/home/view/` — `DragLayer` (hợp đồng `settleTo`/`onEnd`: gọi `onEnd` cả khi hạ cánh bị hủy), `IconDragGesture`, `Jiggler`, `IconActionMenu`, `IconPress`; `ui/library/` — `AppLibraryActivity`/`ViewModel`/`Adapter`. Quy ước và bài học triển khai: `docs/AGENTS.md` (các mục "M7 Task N").

**Tệp thêm ở M8d (không có trong bản vẽ gốc):** `domain/search/` (`SearchResult`, `SearchIndex`); `domain/model/` (`SearchSettingEntry`, `ShortcutEntry`, `RecentApp`, `SearchPreferences`); `domain/repository/` (bốn repository tìm kiếm); `data/search/` (danh mục Cài đặt và các repository, lịch sử ở `noBackupFilesDir`); `di/SearchModule`; `ui/settings/SearchSettingsBlock`, `SearchSettingsViewModel`.

**Tệp thêm ở G2d (2026-11-12):** `ui/settings/` — `SettingsScreen.kt`, `SettingsHubScreen.kt`, `DefaultLauncherAction.kt`, `GeneralSettingsScreen.kt`, `LiquidGlassSettingsScreen.kt`, `WallpaperSettingsScreen.kt`, `IconSetSettingsScreen.kt`, `SearchSettingsScreen.kt`; `res/layout/item_settings_footer.xml`; chuỗi tiền tố `settings_hub_`.

**Công thức thêm hàng/màn Cài đặt (từ G2d, các nhánh FD-1/2/3 làm theo):**
- *Thêm hàng vào màn có sẵn:* chỉ sửa lớp màn đó (`rows`, `onValueClicked`/`onSwitchChanged`, hộp thoại). Trạng thái mới: thêm trường cuối `SettingsUiState` + ghi ở `SettingsViewModel`, hoặc ViewModel riêng của màn (mẫu `SearchSettingsViewModel`). `ID_...` mới thêm cuối `SettingsRow.Companion`; chuỗi vào khối của nhánh ở cuối `strings.xml`. Không sửa `SettingsActivity`, trang chủ, manifest.
- *Thêm màn mới cho mục bản nháp:* tạo `XxxSettingsScreen.kt` (triển khai `SettingsScreen`; hộp thoại và `registerForActivityResult` trong lớp, đăng ký lúc dựng), rồi **thay đúng một dòng giữ chỗ** trong `SettingsScreens` bằng `SettingsScreenId.X to ::XxxSettingsScreen`. Mục trên trang chủ tự bật (enum, bảng `SettingsHubLayout` và chuỗi đã khai sẵn). Màn cần ViewModel riêng: `ViewModelProvider(context.activity)[Xxx::class.java]`.
- *Mục ngoài bản nháp:* thêm giá trị cuối enum `SettingsScreenId` + một dòng trong nhóm của `SettingsHubLayout`.

**Tệp thêm ở GS (không có trong bản vẽ gốc):** `domain/model/GlassCustom` (`GlassElement`, `GlassCustom`), `core/glass/GlassConfig`. `GlassPolicy.modeFor` nhận thêm `custom`; `GlassRole.DOCK`; `GlassLevelOverride.CUSTOM`; `GlassBudget` đã bỏ `maxRealGlass`, không có trần kính thật.

**Tệp thêm ở G2c (không có trong bản vẽ gốc):** `ui/widget/` (renderer, `WidgetTicker`, `WidgetFormat`); `ui/home/controller/WidgetPickerController`; `domain/model/WidgetKind`, `PlacedWidget` trong `PlacedItem.kt`; `domain/layout/PageGeometry`, `WidgetPlacementPlanner`; hai use case thêm và xóa widget.

**Tệp thêm ở LC (không có trong bản vẽ gốc):** `data/apps/LabelCache` (nhãn app trong file JSON ở `noBackupFilesDir`, ngoài Room).

**Tệp thêm ở IC (không có trong bản vẽ gốc):** `data/apps/IconDiskCache` (icon đã render, mỗi icon một file PNG trong `cacheDir/icons-v1-<hash fingerprint>/`, tên `<package>__<hash gồm sourceDir>.png`, trần 8 MB). `IconLoader` thêm bước RAM → đĩa → render; `LauncherAppsDataSource` giữ bảng `key → sourceDir` điền ở cuối `loadApps`.

**Đổi ở DD:** `DefaultAppResolverPort` đổi thành `candidates(DockRole): List<String>` (thứ tự ưu tiên); `DefaultAppResolver` viết lại: app mặc định thật (`TelecomManager`, `Telephony.Sms`) → `resolveActivity` có nhận diện hộp chọn → trình xử lý hệ thống → package quen → theo bảng chữ cái. `PickDefaultDockUseCase` lấy ứng viên đầu tiên đã cài và chưa dùng.

**Tệp thêm ở SM (không có trong bản vẽ gốc):** `ui/home/view/IconActionMenu` (bảng kính 200 dp, hàng 35 dp, chữ 15 sp), `ui/home/view/IconMenuMotion` (spring mở/đóng, gốc phóng từ phía icon), `ui/home/view/IconPress` (icon thu nhỏ khi nhấn), `ui/home/controller/IconMenuPlacement` (hàm thuần: đặt menu trên/dưới icon, căn theo mép); `GlassRole.ICON_MENU` + `GlassElement.ICON_MENU` (khóa `glass_custom_icon_menu`, Tùy chỉnh: bật kính thật, tắt kính mờ); màu `shortcut_menu_glass_tint`.

**Tệp thêm ở SW:** `ui/home/controller/IconMenuWidth` (hàm thuần: bề rộng menu icon = chữ hàng dài nhất, kẹp 200-260 dp và vùng an toàn); `IconActionMenu.naturalContentWidth()`; dimen `shortcut_menu_width` thay bằng `shortcut_menu_min_width`/`shortcut_menu_max_width`.

**Đổi ở RB:** release bật R8 (`optimization.enable = true`), keep rule ở `app/src/main/keepRules/rules.keep`, baseline profile viết tay `app/src/main/baseline-prof.txt`.

**Tệp thêm ở G3a (không có trong bản vẽ gốc):** `domain/model/ContactEntry`, `domain/repository/ContactsRepository`, `data/search/ContactsRepositoryImpl` (đọc `ContactsContract` theo yêu cầu, giữ tên trong RAM, chọn số mặc định bằng code), `data/search/ContactAvatar` (ảnh tròn hoặc chữ cái đầu); `SearchKind.CONTACT`; khóa DataStore `search_contacts`, `search_contacts_asked`; quyền `READ_CONTACTS`.

**Tệp thêm ở GF:** `core/glass/GlassLocation` (vị trí màn hình bỏ phép dịch của tổ tiên có tag `R.id.glass_ignore_translation`, `res/values/ids.xml`); `AppLibraryController.refreshHomeGlass()`; `SearchPillView.refreshGlass()`; nền `CategoryGroupPanelView` = `BackdropBlurDrawable` toàn màn.

**Tệp thêm ở AP:** `ui/home/view/AppLibraryMotion` (mọi hằng chuyển động App Library); `PanelMotion` có chế độ mọc từ ô; `CategoryGroupPanelView` thành màn nhóm toàn màn hình; `RealGlassSurface.setCaptureIgnoringAncestorTransforms` (dock).

**Tệp thêm ở IP:** `data/apps/IconPackManager`, `domain/model/IconPackInfo`, `domain/repository/IconPackRepository`; `IconStyleKey.pack`; khóa DataStore gói icon; `<queries>` cho các intent gói icon.

**Đổi ở PL:** `PageDotsLayout.weightOf`, `SearchPillView.drawDots` (chấm mờ dần theo vị trí).

**Tệp thêm ở G3e (không có trong bản vẽ gốc):** `data/weather/` (`OpenWeatherMapProvider`, `OwmParser`, `WeatherHttp` dùng HttpURLConnection + org.json, `WeatherStore` file `no_backup/weather.json`, `WeatherRepositoryImpl` có chặn 30 phút, `CoarseLocationSource`), `domain/model/weather/`, `domain/weather/`, `di/WeatherModule`, `ui/weather/WeatherSheetController` + `WeatherSheetViewModel`, `ui/widget/WeatherIcons`, `WeatherWidgetRenderers`, `WeatherWidgetHub`; `WidgetKind.WEATHER`; quyền `INTERNET`, `ACCESS_COARSE_LOCATION`; khóa `OWM_API_KEY` từ `local.properties` vào `BuildConfig`.

**Tệp thêm ở CP (2026-11-13, PR #54):** `domain/search/PhoneMatcher` (chuẩn hóa số, `+84` → `0`); `ContactEntry.phones`; `ContactsRepository.loadPhones` thay `numbersFor` (một truy vấn `Phone.CONTENT_URI`, ghép theo id); binding `ContactsRepository` theo build type: `app/src/release/.../di/ContactsModule` và `app/src/debug/.../di/ContactsModule`; nguồn danh bạ giả chỉ debug ở `app/src/debug/.../debug/` (`DebugContactsRepository`, `FakeContactsSource`, `FakeContactsModeReceiver`, receiver trong `app/src/debug/AndroidManifest.xml`, bật bằng `adb shell am broadcast -a gambi.global.os26launcher.CP_FAKE_MODE -p gambi.global.os26launcher --es mode off|small|load`).

**Tệp thêm ở WX (2026-11-13, PR #55):** `domain/model/weather/WeatherPlaces`, `domain/weather/WeatherPlacesRepository`, `data/weather/WeatherPlacesStore` (file `no_backup/weather_places.json`: chế độ, vị trí tự động/thủ công, thành phố đã thêm, ánh xạ `itemId` → địa điểm), `WeatherPlacesRepositoryImpl`, `CurrentLocationUpdater`, `GeoPlaceJson`; `no_backup/weather.json` lên version 2 (một mục mỗi địa điểm); `ui/weather/CitySearchDialog` + `CitySearchViewModel`; `ui/settings/LocationSettingsScreen` + `LocationSettingsViewModel` (màn 1.5); `WidgetRenderer.bindItem(itemId)`; layout `dialog_city_search`, `item_weather_choice`.

**Tệp thêm ở WN (2026-11-16, PR #58):** `domain/model/weather/WeatherNotification.kt`, `domain/weather/WeatherNotificationPlanner`, `WeatherForecastSummarizer`, `WeatherNotificationScheduler`, `domain/usecase/PrepareWeatherNotificationUseCase`; `data/weather/notify/AlarmWeatherNotificationScheduler`; `ui/weather/notify/` (`WeatherNotificationReceiver`, `WeatherNotificationDispatcher`, `WeatherNotificationJobService`, `WeatherNotifier`); receiver debug ở `app/src/debug` (`WN_DEBUG_FIRE|SHIFT|ENABLE|STATE`, chặn bằng quyền `DUMP`). Quyền mới `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. `weather.json` có khóa `"f"` (12 mốc), `WeatherSnapshot.forecast`.

**Tệp thêm ở G3b (2026-11-16, PR #57):** `domain/model/photo/PhotoWidgetConfig`, `domain/photo/PhotoWidgetRepository`, `data/photo/` (`PhotoWidgetStore` → `no_backup/photo_widgets.json`, `PhotoFileStore` → `no_backup/photo_widgets/<itemId>/`, `PhotoWidgetRepositoryImpl`), `di/PhotoWidgetModule`, `ui/photo/` (`PhotoWidgetActivity`, `PhotoWidgetViewModel`, `PhotoCropView`, `PhotoGridAdapter`), `ui/widget/PhotoWidgetRenderers`, `PhotoWidgetHub`, `PhotoBitmapCache`; `WidgetKind.PHOTO`.

**Tệp thêm ở G3c (2026-11-16, PR #59):** `core/appwidget/` (`OsAppWidgetHost` host id cố định `0x4F533236`, `OsAppWidgetHostView`, `AppWidgetAssets`) — **ngoại lệ phân lớp có chủ ý như `core/glass`**: hạ tầng host widget dùng chung cho `data` và `ui`; `data/appwidget/AppWidgetRepositoryImpl`; `domain/layout/AppWidgetSizePlanner`, `AppWidgetLimits`; `domain/model/AppWidgetModels` (`PlacedWidgetBase`, `PlacedAppWidget`); `domain/repository/AppWidgetRepository`; `domain/usecase/AddAppWidgetUseCase`; `di/AppWidgetModule`; `ui/widget/host/` (`HostedWidgetViews`, `AppWidgetFlowController`, `AppWidgetFlowState`, `AppWidgetSection`); `IconCellView` đổi sang `ViewGroup`. Manifest: `BIND_APPWIDGET`, `<queries>` `APPWIDGET_UPDATE`. Công cụ thử chỉ debug ở `app/src/debug` (`AwProbe*`, provider thử A–E, quyền `DUMP`).

**Tệp thêm hoặc đổi ở FX1–FX4, FX6 (2026-11-11, đợt 1 sửa lỗi sau rà soát):**
- **FX1 (ảnh nền, PR #31):** `core/bitmap/` thành thật (`BitmapScaler`, `ExifReader`, `TransparentTrimmer`); `BackdropRepository.backdropVersions` để Home biết ảnh mới. Hằng: `MAX_SAVED_PIXELS` 16 MP (thu ảnh khi nhập), `MAX_DECODE_PIXELS` 25 MP, ngưỡng chép nguyên JPEG 30 MB (D9). Quy ước: file lưu **không cắt**; bitmap Home là vùng giữa theo tỉ lệ vùng Home (`BitmapRegionDecoder`).
- **FX2 (edit mode, PR #33):** `Jiggler.kt` có một cổng `sync` (gắn + hiện + edit mode); ticker `Choreographer` đã thử và bỏ (knowledgement FX2 mục 5). `WidgetPickerController` có `closeIfOpen`/`hideStatusBar`.
- **FX3 (bố cục, PR #32):** `domain/layout/NewAppPlacementPlanner.kt` (đặt app mới sau icon cuối, C20); `LayoutReconciler` đổi cỡ lưới và ghi DataStore trong một `mutex.withLock`; `LauncherItemDao`/`HiddenAppDao` thêm `@Query UPDATE` đổi `className` (re-point A3). Không đổi schema (`app/schemas/1.json` giữ nguyên).
- **FX4 (tìm kiếm, PR #34):** `SearchIndex.Source` trong `domain/search` (chỉ mục ghép từ nguồn đã chuẩn hóa). Không đổi cây thư mục.
- **FX6 (độ bền, PR #35):** `domain/weather/CoarseLocationSource.kt`, `domain/model/weather/CoarseFix.kt`, `data/weather/CoarseLocationSourceImpl.kt` (`@Binds` ở `di/WeatherModule`).

- **FX5 (icon/nhãn, PR #36):** `AppItem` thêm `versionStamp`, `iconKey`, `a11yLabel` và `AppIconKey` (khóa cache mang dấu phiên bản APK ở RAM, đĩa, view); `data/apps` thêm luồng trigger hệ thống (đổi locale, thay đổi hồ sơ); `PackageEvent.Type.USER_REMOVED`; `LayoutReconciler.removeUser`; `HiddenAppDao.deleteByUser`. Huy hiệu hồ sơ vẽ lên bitmap cuối. Không đổi schema.
- **FX8 (kính khớp nền, PR #39):** `core/glass`: kính tại chỗ lấy ánh xạ từ `imageMatrix` của `ImageView` ảnh nền; `BlurredBackdropCache` dựng theo cỡ view và có `prewarm`.

**Tệp thêm ở WP:** `data/backdrop/DefaultBackdropSource` (ảnh nền mặc định khi chưa chọn ảnh, giải mã theo cỡ màn hình), ảnh `res/drawable-nodpi/` + `res/drawable-night-nodpi/default_wallpaper_ios27.jpg` (chỉ để thử, xóa trước bản cửa hàng), task Gradle `checkNoStoreTestWallpaper` chặn `bundleRelease`.

**Đổi ở DG:** `DockView.pulseGlassRefresh()` bỏ, thay bằng `captureGlassOnce()` gọi ở mọi `BackdropChange` từ `DockController.onBackdropChanged`.

**Tệp thêm ở GP (không có trong bản vẽ gốc):** `ui/home/view/GapLongPressGesture` (nhấn giữ khoảng trống trang). Ở `GridStatus.Empty`, pager và dock vẫn hiện, pill ẩn, không có dòng chữ.

**Tệp thêm ở AN (không có trong bản vẽ gốc):** `core/ui/SpringProgress` (spring tiến độ 0..1, tôn trọng cài đặt tắt hoạt ảnh); `ui/home/view/PanelMotion` (spring mở/đóng chung cho hai panel), `ui/home/view/BackgroundGlass` (nền kính của panel). `IconGridView` vẽ kính hover trong `dispatchDraw` và giữ sổ đăng ký ô thư mục; `DragHelper.settlingItemId`; `GlassEnvironment.notifyBackdropChanged`; `BlurredBackdropCache` có người nghe yếu cho `BackdropBlurDrawable`. Thư viện mới: `androidx.dynamicanimation:1.1.0`.

**Tệp thêm ở M8a (không có trong bản vẽ gốc):** `domain/model/` — `GridPreset` (enum 3 preset lồng nhau, `capacity`, `DEFAULT`); `domain/layout/GridReflowPlanner` (hàm thuần: trải phẳng theo thứ tự đọc, nén khe); `domain/usecase/ChangeGridPresetUseCase` (thứ tự ghi cài đặt/DB theo hướng thu nhỏ/mở rộng); `data/layout/LayoutReconciler` thêm `reflowTo`/`ensureFitsGrid` và nhận `SettingsRepository` (đọc cỡ lưới trong mutex); `BackdropRepository.backdropVersion()`; `core/ui/GridMetrics` thành data class có `showLabels` và `from(preset)`; `ui/home/view/DockView.pulseGlassRefresh()`; `ui/settings/` (xem cây thư mục). Quy ước và bài học: `docs/AGENTS.md` (các mục "M8a Task N").

**Tệp thêm ở M8b (không có trong bản vẽ gốc):** `domain/search/AppSearchMatcher` (hàm thuần: `normalize` bỏ dấu + `search` xếp hạng); `ui/home/SearchSwipeDetector` (hình học cử chỉ vuốt xuống, chỉ quan sát); `ui/search/` — `SearchViewModel` (ghép `AppRepository.apps` trừ app ẩn, giữ `isOpen`/`query`), `SearchUiState`/`SearchStatus`/`SearchEvent`, `SearchOverlayView` (tự xử lý insets), `SearchResultAdapter`; `HomeActivity` thêm `dispatchWithSearchSwipe`, `onNewIntent`, callback Back thứ ba; layout `view_search_overlay`, `item_search_result`; drawable `bg_search_field`, `bg_search_row`, `ic_search_clear`. Quy ước và bài học: `docs/AGENTS.md` (các mục "M8b Task N").

**Tệp thêm ở M8c (không có trong bản vẽ gốc):** `ui/home/view/SearchPillView` (pill cố định 144dp × 32dp trong view cao 48dp, làm mờ chéo ô tìm kiếm ↔ hàng chấm, nhận bấm khi là ô, nhãn trợ năng theo chế độ), `ui/home/view/PageDotsLayout` (hàm thuần: `windowStart`, `slotOf`, `activeSlot`, `scaleAt`); `HomeActivity` thêm `requestPillMode` (chế độ mong muốn = đang lướt / edit mode / kéo-thả, về ô tìm kiếm sau 600ms từ lúc `IDLE`) và `search_pill` thay `page_indicator` trong `activity_home.xml`; drawable `ic_search_pill_magnifier`. Sửa kèm: `LayoutReconciler.deleteAndCompact` dồn trong từng trang (bỏ `compactHome`). Quy ước và bài học: `docs/AGENTS.md` (các mục "M8c Task N").

**Tệp thêm ở G2a (không có trong bản vẽ gốc):** Phần A — `domain/model/PlacedItem` (`PlacedApp`/`PlacedFolder`), `domain/layout/FolderPlanner` (hàm thuần: `canCreate`/`canAdd`/`dissolve`/`normalizeTitle`/`isInCenterZone`, `MAX_ITEMS = 9`); use case `CreateFolder`/`AddToFolder`/`RenameFolder`; `data/layout/LayoutReconciler` thêm `createFolder`/`addToFolder`/`renameFolder`/`dissolveFolderIfNeeded`/`deleteAppsAndFixFolders`; `ui/home/view/FolderPanelView` (panel thư mục, là `DragDropTarget` chặn các đích khác). Thư mục nằm ở `ui/home/view` và `domain/layout` chứ không có package `folder/` riêng. Phần B — `domain/model/AppCategory` (`AppCategory`, `CategoryGroup`, `CategoryGroupApps`), `domain/category/AppCategoryGrouper` (hàm thuần), `data/apps/AppCategoryMapper`, `domain/usecase/GetCategoryGroupsUseCase`, `ui/home/view/CategoryPageView` (thanh tìm kiếm + lưới ô nhóm), `ui/home/view/CategoryGroupPanelView` (panel nhóm chỉ đọc), `ui/home/LibraryOverlayController` (cử chỉ + tiến độ của lớp phủ App Library, nằm NGOÀI `ViewPager2`), `SearchViewModel.open(showAll)`.

**Tệp thêm ở WI (2026-11-18, `plans/WI-wallpaper-images.md`):** `core/wallpaper/WallpaperPresets` (5 hình nền cài sẵn: id lưu ở `appearance_wallpaper_preset`, ảnh đầy đủ + ảnh nhỏ); `data/backdrop/ResourceBackdropLoader` (giải mã ảnh nền trong tài nguyên, tách từ `DefaultBackdropSource`); ảnh `res/drawable-nodpi/wi_wallpaper_{1..5}{,_thumb}.webp` (ảnh thử Apple, xóa trước phát hành). Bỏ `ui/wallpaper/WallpaperPlaceholders` và `WallpaperSwatches` (ô màu 0.3/2.2).

**Hai chỗ M0 làm khác bản đầu của bảng trên (2026-10-16):** (1) bỏ việc "dựng khung package rỗng" vì git không theo dõi thư mục rỗng, mỗi package ra đời cùng file đầu tiên của nó; (2) không viết lại `MainActivity` demo theo ViewBinding vì nó sẽ bị xóa sau M5.

Việc dọn dẹp đặt ở M1 (sau khi di trú xong) chứ không ở M0, để demo còn chạy đối chứng trong lúc dựng `HomeActivity`.

---

## 10. Rủi ro và việc cần kiểm chứng

| # | Rủi ro | Mức | Xử lý |
|---|---|---|---|
| R1 | **Hilt + AGP 9 built-in Kotlin chưa ai xác nhận chạy được.** Hilt 2.59 mới thêm "AGP 9 support", release note không nói gì về built-in Kotlin; AGP 9 có log về kapt với built-in Kotlin | Cao | M0 làm trước nhất và chỉ có đúng một mục tiêu: build xanh với 1 `@HiltAndroidApp` + 1 `@AndroidEntryPoint`. Nếu vỡ: hoặc ghim KGP cao hơn, hoặc rơi về AppContainer thủ công (đổi `di/` sang `AppContainer.kt`, phần còn lại của cây thư mục không đổi) |
| R2 | Version KSP/KGP không đoán được từ tài liệu | Trung bình | `./gradlew buildEnvironment` rồi ghim vào `libs.versions.toml` |
| R3 | Ngân sách kính cho tier thấp đang dựa trên phép đo trường hợp xấu nhất (nền animate mọi frame) | ~~Trung bình~~ **Đã giải quyết** | **M6 Task 2 (2026-10-28):** đo 4b thật trên màn hình chính đứng yên (Vsmart Live 4) — `RealGlassSurface` 10.29% Janky, `StaticBlurSurface` 2.13% Janky. `GlassBudget.CLASSIC_MAX = 1` giờ dựa trên số đo thật, không còn là ước lượng |
| R4 | `StaticBlurSurface` chưa hiện thực, chưa đo — mà Quyết định 4 (giữ minSdk 26) dựa vào nó | ~~Trung bình~~ **Đã giải quyết** | **M6 Task 2 (2026-10-28):** đã hiện thực + đo trên Vsmart Live 4, 2.13% Janky — Quyết định 4 (giữ minSdk 26) có cơ sở thật |
| R5 | R8 đang tắt (`optimization { enable = false }`). Bật lại sẽ cần keep rule cho Room, Hilt, và `.so`/AGSL của thư viện kính | Thấp bây giờ, cao lúc phát hành | Bật R8 ở một mốc riêng trước khi làm bản release, viết rule vào `keepRules/rules.keep`, test trên máy thật |
| R6 | AAR thư viện kính chỉ có `.so` cho `arm64-v8a` và `armeabi-v7a` | Thấp | Test trên máy thật hoặc emulator arm64; nếu cần emulator x86 thì phải bọc try/catch quanh đường kính |
| R7 | Thư viện kính còn trẻ, 1 tác giả, API có thể đổi | Thấp | Đã ghim `v2.0.11`; `core/glass` là lớp cách ly duy nhất chạm vào nó |
| R9 | **Dagger 2.60.1 được build bằng Kotlin 2.3.21, dự án chạy KGP 2.2.10.** Runtime của Hilt chủ yếu là Java nên rủi ro thấp, nhưng Hilt Gradle plugin là Kotlin | Thấp–trung bình | M0 phát hiện ngay nếu có. Cách sửa theo thứ tự: (1) nâng KGP qua `buildscript` classpath + KSP khớp, (2) hạ Hilt về 2.59.1 |
| R8 | Quyền xem danh sách app khi phát hành lên Play | Trung bình (đã giải quyết ở M2) | `LauncherApps` không cần *quyền*, nhưng **có chịu lọc hiển thị package** (Android 11+): launcher **không** nằm trong 8 nhóm được miễn trừ tự động, kể cả khi giữ `ROLE_HOME`. Cách đã đo hoạt động (2026-10-19): khai `<queries><intent>` MAIN + LAUNCHER trong manifest. Không cần `QUERY_ALL_PACKAGES` (chính sách Play không liệt launcher vào use case được phép). Oppo A3x: 9 → 344 app, Vsmart Live 4: 87/87, đều khớp số đếm của adb |

---

## 11. Trạng thái duyệt

**Đã chốt 2026-10-16 — không còn mục nào chờ duyệt:** cây thư mục và lớp cách ly `core/glass` (điểm 1) · thiết kế Room 3 bảng kèm Q1–Q5 trong [room-schema.md](room-schema.md) (điểm 2) · thứ tự mốc M0–M8 (điểm 3) · giữ `demo/` đến sau M6 (điểm 4; cập nhật 2026-10-28: demo đã xóa xong, xem điểm 4) · xóa test, đã làm (điểm 5) · `adjustNothing` (điểm 6, A6) · tên gọi, đã làm (điểm 7) · KGP 2.2.10 + KSP (A5; KSP đổi thành `2.3.11` ở M0.1, xem A5).

Mốc tài liệu tổng quan đã đóng. Việc tiếp theo: `docs/plans/M0-infrastructure.md`.


1. **Toàn bộ cây thư mục ở mục 3** — nhất là việc tách `core/glass` thành lớp cách ly (mục 5), vì nó quyết định cách mọi màn hình gọi kính.
2. **Bảng Room ở mục 6** — một bảng phẳng kiểu Launcher3 thay vì nhiều bảng quan hệ. Chọn cách này vì kéo-thả chỉ cần `UPDATE` vài dòng, và thư mục là item có `container` trỏ về chính nó.
3. **Thứ tự mốc ở mục 9** — đặc biệt M6 (`StaticBlurSurface`) nằm sau M5 (đo 4b) chứ không làm sớm.
4. **Đề nghị xóa 5 file sau, sau khi M1 di trú xong** (không tự xóa, chờ bạn đồng ý). **Cập nhật 2026-10-16:** 4 file đầu đã `mv` sang `data/backdrop/` ở M1 Task 1 nên bản trong `demo/` không còn. **Cập nhật 2026-10-28 (M6 Task 4): đã xóa toàn bộ, không còn gì để duyệt ở mục này** — `DemoBackdropView.kt` cùng `MainActivity.kt`, `activity_main.xml`, `view_extra_glass.xml`, khối `<activity>` trong manifest, string `demo_activity_label` đã xóa sau khi có số đo 4b thật, kiểm tham chiếu chéo không còn gì sót:

| File | Lý do đề nghị xóa |
|---|---|
| `demo/BackdropSource.kt` | chuyển nguyên vẹn sang `data/backdrop/`, giữ 2 bản sẽ lệch |
| `demo/PickedImageSource.kt` | chuyển sang `data/backdrop/`, phần giải mã/EXIF/cắt alpha tách ra `core/bitmap/` |
| `demo/SystemWallpaperSource.kt` | chuyển sang `data/backdrop/`, vẫn giữ nguyên trạng thái "gác lại, không nối UI" (B6) |
| `demo/WallpaperApplier.kt` | chuyển sang `data/backdrop/` |
| `demo/DemoBackdropView.kt` | chỉ là nền giả để đo hiệu năng. **Đã xóa 2026-10-28** cùng `MainActivity` demo + `activity_main.xml` + `view_extra_glass.xml` + khối `MainActivity` trong manifest + string `demo_activity_label`, sau khi M6 có số đo 4b thật (M6 Task 4) |

5. **Bỏ hẳn phần test** — **đã chốt và đã làm 2026-10-16**:

| Mục | Trạng thái |
|---|---|
| `app/src/test/`, `app/src/androidTest/` | đã xóa (người dùng) |
| 3 dòng `testImplementation` / `androidTestImplementation` trong `app/build.gradle.kts` | đã xóa |
| 3 alias `junit`, `androidx-junit`, `androidx-espresso-core` + 3 version `junit`, `junitVersion`, `espressoCore` trong `libs.versions.toml` | đã xóa |

Hệ quả cần biết: mọi tiêu chí "coi là xong" ở mục 9 đều là **kiểm chứng tay trên máy thật** (Oppo A3x Android 14 + Vsmart Live 4 Android 11). Riêng Room (M4) mất lưới an toàn tự động cho migration, nên quy tắc ở mục 6 (không bao giờ `fallbackToDestructiveMigration()`, luôn viết `Migration` tay, commit `app/schemas/`) trở thành bắt buộc tuyệt đối, và mỗi lần đổi schema phải thử tay: cài bản cũ → tạo bố cục → cài bản mới lên trên → bố cục còn nguyên.

6. **Đã chốt:** `windowSoftInputMode` dùng `adjustNothing` (A6, mục 4) — bàn phím không đẩy layout nên nền không dịch, kính không lệch. Overlay tìm kiếm tự tính inset bàn phím, tính vào phạm vi M8.

7. **Đã chốt và đã làm (2026-10-16)** — dọn các tên còn mang chữ "Ios":

| Chỗ | Trước | Sau |
|---|---|---|
| `strings.xml` → `app_name` | `IosLauncher` | **`OS26 Launcher`** — tên hiện trên Google Play và dưới icon, chỗ rủi ro thương hiệu cao nhất, cao hơn cả package |
| `themes.xml`, `values-night/themes.xml`, `AndroidManifest.xml` | `Theme.IosLauncher`, `Base.Theme.IosLauncher` | `Theme.Os26Launcher`, `Base.Theme.Os26Launcher` (5 chỗ) |
| `settings.gradle.kts` → `rootProject.name` | `IosLauncher` | `Os26Launcher` — Android Studio sẽ đồng bộ lại và reindex một lần |
| Thư mục trên ổ đĩa | `E:\SourceTree\IosLauncher` | **giữ nguyên** theo quyết định của bạn; mọi đường dẫn trong `docs/` vẫn đúng |
