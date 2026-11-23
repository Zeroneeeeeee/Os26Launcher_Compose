# OB — Luồng Onboarding 0.0–0.5 — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm tay trên Vsmart Live 4 (không đổi cách vẽ kính Home, không cần Oppo). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-17 · **Plan duyệt:** 2026-11-17 (chủ dự án, Q1–Q6 theo khuyến nghị; cho làm một mạch Task 1–6)
**Trạng thái:** Xong 2026-11-17 (Task 1–7), chờ chủ dự án commit
**Máy:** Live 4 · **Đổi schema Room:** không · **Khóa config mới:** không (dùng `first_run_done` = `AppState.onboardingDone` đã có)

**Goal:** Lần đầu mở launcher, người dùng đi qua 5 màn (0.1 Chào mừng → 0.2 Chọn kiểu giao diện → 0.3 Chọn hình nền → 0.4 Đặt làm màn hình chính → 0.5 Hoàn tất) đúng thiết kế v3; lựa chọn ghi ngay vào config CF (theme, nền màu), xong thì về Home đã áp giao diện.

**Architecture:** Một `OnboardingActivity` riêng (nền aurora tối, theme DS3), một `OnboardingViewModel` giữ bước hiện tại và lựa chọn (qua `SavedStateHandle` để sống qua xoay màn và process death), mỗi màn là một layout + một lớp bind nhỏ (`OnboardingStepXxx`) chỉ chứa **phần giữa**; đổi màn bằng hoạt ảnh trượt ngang trong một `FrameLayout`. **Thanh trên, chỉ báo bước và hàng nút nằm cố định trong `activity_onboarding.xml`, không trượt theo màn**: mỗi bước chỉ khai cấu hình (nhãn nút chính, nút phụ có/không, hiện back/Bỏ qua), Activity áp cấu hình đó khi đổi bước. `HomeActivity.onCreate` mở Onboarding khi `onboardingDone = false` và Activity không phải dựng lại (`savedInstanceState == null`). Theme ghi bằng `ApplyThemeUseCase` (CF), nền màu bằng `AppearanceRepository.update`, đặt launcher mặc định bằng `DefaultLauncherAction` (G2d) tách khỏi `SettingsViewModel`. Màn 0.0 Splash là splash của hệ thống cấu hình bằng theme, không phải một Activity.

**Tech Stack:** Kotlin, Views/XML + ViewBinding, Hilt, DataStore (qua repository CF), component DS3 (`core/designsystem`). Không dependency mới.

**Spec:** Figma v3 trang "v3 · Thiết kế mới", section `302:3673` (0.0 `302:3674`, 0.1 `302:3677`, 0.2 `302:3695`, 0.3 `302:3737`, 0.4 `302:3772`, 0.5 `302:3796`); `docs/Figma-Draft.md` hàng 2 và quyết định V2; thảo luận chủ dự án 2026-11-16/17 (6 bước, 0.1 phương án A, ẩn tính năng chưa có, nội dung chính chưa có thì dùng lựa chọn tạm như nền màu); `docs/config-schema.md` (CF).

## Đọc Figma (figma-bridge, 2026-11-17)

| Màn | Nội dung | Thành phần DS3 / tài nguyên |
|---|---|---|
| 0.0 Splash | Nền `#07080E`, icon app 120 dp giữa màn, quầng sáng `#4F6DFF` mờ sau icon | Splash hệ thống (theme). Quầng sáng: bỏ (hệ thống không vẽ được) |
| 0.1 Chào mừng | Chip ngôn ngữ (góc trên trái), ảnh hero 300×375 có mặt nạ mờ viền, tiêu đề 26 Bold "Giao diện kính lỏng cho điện thoại của bạn", phụ đề 14, chỉ báo bước (1/5), nút "Bắt đầu"; thanh trên chỉ có "Bỏ qua" | `DsOnboardingTopBar` (không back), `DsStepIndicatorView`, `DsButton` L; ảnh hero `302:3684` |
| 0.2 Chọn kiểu giao diện | Tiêu đề "Chọn kiểu giao diện", phụ đề "Bạn có thể đổi lại bất cứ lúc nào trong Cài đặt."; lưới 2×2 thẻ kính 158×213 (ảnh xem trước 142×150 bo 12 có giờ "9:41", dấu chọn tròn `#4370FF` 24 dp góc trên phải; tên 14 SemiBold; mô tả 12); Dark Pro có badge "MỚI"; nút "Tiếp tục" | Thẻ: `DsSurface` kính bo 20; `DsBadgeView` NEW; 4 ảnh xem trước `302:3706/3715/3721/3727` |
| 0.3 Chọn hình nền | Tiêu đề "Chọn hình nền", phụ đề "Hình nền đẹp giúp hiệu ứng kính nổi bật hơn."; khung điện thoại 176×312 bo 28 (giờ 40 Light, ngày 12, dock kính 4 ô); hàng 6 ảnh nhỏ 56×96 bo 14 cuộn ngang, ô chọn có dấu ✓; nút "Tiếp tục" | Ảnh nền thư viện: **chưa có** (G3f) → ô màu đặc (Q3) |
| 0.4 Đặt làm màn hình chính | Hình minh họa 180, tiêu đề "Đặt Launcher OS làm màn hình chính", phụ đề; thẻ kính 2 bước có số tròn 28 dp; nút chính "Đặt làm mặc định", nút phụ "Để sau" | `DsButton` PRIMARY + SECONDARY; minh họa `302:3777` |
| 0.5 Hoàn tất | Hình minh họa 200, "Mọi thứ đã sẵn sàng!", phụ đề; thẻ tóm tắt 3 hàng có ô icon + giá trị + chevron (Kiểu giao diện / Hình nền / Màn hình chính); nút "Về màn hình chính" | `DsCardGroup` + `DsRowView` CHEVRON có giá trị; minh họa `302:3801` |

Chung: khung 360×800, thanh trên `DsOnboardingTopBar` ở y 24 cao 56 (nút back kính 40 dp, "Bỏ qua" 13), nội dung bắt đầu y 88 lề 16, chân trang (chỉ báo 5 bước + nút) neo đáy, đệm đáy 28. Nền mỗi màn: `#07080E` + 3 đốm aurora khác vị trí (`AuroraBackgroundDrawable` hiện cố định 4 đốm).

## Vì sao chọn thiết kế này

**Hiện trạng (đọc code `main` @ CF xong, 2026-11-17):** chưa có màn onboarding; `AppState.onboardingDone` (khóa `first_run_done`) khai ở CF nhưng chưa ai đọc/ghi; `ApplyThemeUseCase` và nền màu (`WallpaperKind.COLOR`) có sẵn và đã kiểm; `DefaultLauncherAction` chỉ dùng được trong Cài đặt vì nhận `SettingsViewModel`; DS3 có đủ thanh trên, chỉ báo bước, nút, hàng, badge, bề mặt kính, nền aurora.

**Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| **A. Một Activity, các màn là layout đổi trong một khung** | `OnboardingActivity` + ViewModel giữ bước; mỗi màn một layout | Một nơi giữ trạng thái; hoạt ảnh chuyển màn tự chủ; Back hệ thống = lùi bước dễ | Activity có thêm vài lớp bind | **Chọn** |
| B. `ViewPager2` khóa vuốt | Mỗi màn một trang | Có sẵn hoạt ảnh trượt | Phải khóa vuốt (thiết kế đi bằng nút), giữ 5 trang trong bộ nhớ, ràng buộc bước 0.4 có thể tự bỏ qua | Không |
| C. Mỗi màn một Activity | 5 Activity | Tách biệt | Trạng thái chia 5 nơi, Back/Bỏ qua phải đóng cả chuỗi, chuyển màn kiểu hệ thống | Không |
| D. Làm thành lớp phủ trong `HomeActivity` | View phủ trên Home | Không thêm Activity | `HomeActivity` đã ~200 dòng nối controller; Home bên dưới dựng lại khi áp theme (`recreate()`) sẽ xóa lớp phủ | Không |

**Đánh đổi chấp nhận:** splash 0.0 không có quầng sáng (giới hạn splash hệ thống); 0.3 dùng màu đặc thay ảnh thư viện tới khi có G3f; chip ngôn ngữ 0.1 ẩn tới khi làm LG (1.3).

## Quyết định đã chốt

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Làm đủ 0.0–0.5 theo Figma v3; 0.1 dùng phương án A | Chủ dự án (V2) |
| 2 | Tính năng chưa có thì ẩn component: chip ngôn ngữ 0.1 (`GONE`, chưa có LG) | Chủ dự án 2026-11-17 |
| 3 | Nội dung chính chưa có thì dùng lựa chọn tạm: 0.3 là các ô màu đặc (`WallpaperKind.COLOR`) | Chủ dự án 2026-11-17 |
| 4 | Cờ đã xem = `AppState.onboardingDone` (khóa `first_run_done` có sẵn), không thêm khóa | Claude (theo CF) |
| 5 | Chỉ dùng component DS3 và token `ds_*`; chuỗi tiền tố `onboarding_` | Claude (theo DS3) |
| 6 | Splash 0.0 = splash hệ thống qua theme (API 31+: `windowSplashScreenBackground` `#07080E` + icon app; API 26–30: `windowBackground` layer-list nền + icon giữa); không thêm `core-splashscreen` | Claude: không dependency mới, splash hệ thống luôn hiện trước Activity trên API 31+ dù có làm hay không |
| 7 | Đặt mặc định ở 0.4: hệ thống (PermissionController) gửi HOME và dựng `HomeActivity` mới trong task Home, kết quả không về Onboarding cũ, không chặn được. Onboarding bật cờ trong process (`OnboardingRuntime`) trước khi mở hộp thoại; Home mới thấy cờ thì mở Onboarding **thẳng ở 0.5** (extra `start_at_done`), và dọn mọi task khác của app (`appTasks…finishAndRemoveTask()`) | Chủ dự án 2026-11-17 (đề xuất "thấy cờ false thì nav tới màn cuối"). Process bị đóng giữa chừng thì về 0.1 (chấp nhận) |
| 8 | Home **không giữ khung hình đầu** khi mở Onboarding: che nội dung (`INVISIBLE`, nền cửa sổ `ds_bg_base`), vẽ khung tối, mở Onboarding sau khung đầu + 50 ms | Claude, chủ dự án duyệt 2026-11-17. Lý do: giữ khung hình đầu rồi mở Activity cùng task làm hệ thống chuyển splash sang Activity đó và không gỡ (đo Live 4) |
| 9 | Onboarding **cùng task** với Home (không `taskAffinity` riêng) | Đã thử task riêng + `singleTask` 2026-11-17: Home mới vẫn mở Onboarding mới ở 0.1, vào Đa nhiệm chọn màn cũ ra Home cũ. Hoàn tác |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Ảnh (hero 0.1, 4 ảnh xem trước theme 0.2, minh họa 0.4, 0.5) lấy từ đâu? | Xuất thẳng từ Figma bằng figma-bridge (PNG 3x → WebP vào `res/drawable-nodpi/onb_*.webp`). Ảnh xem trước Liquid Glass có dấu chọn dính vào: tạm ẩn node `302:3707` trong file Figma "(Copy)" lúc xuất rồi bật lại. Giờ "9:41" nằm sẵn trong ảnh xem trước (không vẽ thêm bằng code). Thay asset sau chỉ là đổi file | Đồng ý khuyến nghị (2026-11-17) |
| Q2 | "Bỏ qua" làm gì? | Đánh dấu xong, về Home; **giữ** những gì đã áp ở bước trước (theme, nền), không hoàn tác. Màn 0.5 ẩn "Bỏ qua" (đã ở bước cuối) | Đồng ý khuyến nghị (2026-11-17) |
| Q3 | 0.3 có những ô màu nào? | 6 ô như Figma: ô 1 "Theo giao diện" = màu nền của theme vừa chọn (chọn sẵn, không làm theme thành "đã chỉnh"); 5 ô còn lại là màu chủ đạo đo từ 5 ảnh nhỏ Figma, giữ tên Figma (Lagoon, Night Bloom, Sunset, Prism, Deep Blue). Đo ở Task 4 | Đồng ý khuyến nghị (2026-11-17) |
| Q4 | Theme và nền ghi lúc nào? | Ghi ngay khi bấm "Tiếp tục" ở mỗi màn (lùi lại chọn khác thì ghi đè). Nhờ vậy "Bỏ qua" giữa chừng vẫn giữ phần đã chọn; Home bên dưới tự dựng lại khi về (`appearanceChanges` của CF) | Đồng ý khuyến nghị (2026-11-17) |
| Q5 | Máy đã đặt launcher này làm mặc định khi tới 0.4? | Bỏ qua màn 0.4 (chỉ báo bước nhảy 3 → 5); 0.5 ghi "Đã đặt mặc định". Lùi từ 0.5 về thì về 0.3 | Đồng ý khuyến nghị (2026-11-17) |
| Q6 | Bấm nút Home hệ thống (hoặc vuốt về Home) giữa onboarding? | Onboarding đóng (Home `singleTask` + `clearTaskOnLaunch` dọn task), **chưa** đánh dấu xong: lần dựng Home mới (mở lạnh) hiện lại từ 0.1. Không coi là "Bỏ qua" vì người dùng có thể lỡ tay | Đồng ý khuyến nghị (2026-11-17). |

## Ngoài phạm vi / ghi để sau

- Chip ngôn ngữ 0.1: hiện lại khi làm LG (1.3), mở màn chọn ngôn ngữ.
- Ảnh nền thư viện (Aurora, Lagoon, …) cho 0.3: khi có G3f; ô màu thay bằng ảnh, `WallpaperKind.PRESET` đã khai sẵn.
- Phương án B của 0.1 (`302:3815`): chỉ là ảnh khác để so sánh, không làm.
- Màn 1.1 chọn theme trong Cài đặt (dùng lại thẻ theme của 0.2): plan TH riêng.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `ui/onboarding/OnboardingActivity.kt`, `OnboardingViewModel.kt`, `OnboardingStep.kt` (enum + điều hướng), `res/layout/activity_onboarding.xml` | Tạo | 1 |
| `AndroidManifest.xml` (activity), `res/values/themes.xml` + `values-v31/themes.xml` (splash), `res/drawable/splash_background.xml` | Sửa / Tạo | 1 |
| `core/designsystem/AuroraBackgroundDrawable.kt` (nhận bộ đốm), `res/values/strings.xml` (khối `onboarding_`) | Sửa | 1 |
| `ui/home/HomeActivity.kt` (mở Onboarding) | Sửa | 1 |
| `src/debug/.../debug/ThemeDebugReceiver.kt` (thêm `id=OB_RESET`) | Sửa | 1 |
| `ui/onboarding/WelcomeStep.kt`, `res/layout/onboarding_welcome.xml`, `res/drawable-nodpi/onb_hero.webp` | Tạo | 2 |
| `ui/onboarding/ThemeStep.kt`, `ThemeCardView.kt`, `res/layout/onboarding_theme.xml`, `view_onboarding_theme_card.xml`, `onb_theme_*.webp` (4) | Tạo | 3 |
| `ui/onboarding/WallpaperStep.kt`, `WallpaperSwatches.kt`, `res/layout/onboarding_wallpaper.xml`, `view_onboarding_phone_preview.xml` | Tạo | 4 |
| `ui/settings/DefaultLauncherAction.kt` (nhận `DefaultLauncherRepository`), `SettingsActivity.kt` (chỗ dựng) | Sửa | 5 |
| `ui/onboarding/DefaultLauncherStep.kt`, `res/layout/onboarding_default_launcher.xml`, `onb_default_launcher.webp` | Tạo | 5 |
| `ui/onboarding/DoneStep.kt`, `res/layout/onboarding_done.xml`, `onb_done.webp` | Tạo | 6 |
| `docs/Figma-Draft.md`, `docs/project_structure.md`, `docs/config-schema.md` (cột người đọc `onboardingDone`), `docs/lessons/*` | Sửa | 7 |

## Review Focus

1. **Xoay màn / đổi cỡ cửa sổ / process death giữa chừng:** về đúng bước đang ở, lựa chọn còn nguyên (bước và lựa chọn trong `SavedStateHandle`). Kiểm ở Task 1 (khung) và Task 7 (cả luồng, `adb shell am kill` khi app ở nền).
2. **Hộp thoại chọn launcher bị Hủy / ROM không có hộp thoại:** ở lại 0.4, không kẹt; ROM không có role thì mở màn hệ thống dự phòng như Cài đặt (B12). Kiểm ở Task 5.
3. **Home dựng lại khi áp theme lúc Onboarding đang mở:** Home không mở Onboarding lần hai (`savedInstanceState != null` khi `recreate()`). Kiểm ở Task 3.
4. **Cỡ chữ hệ thống lớn / màn thấp (Live 4 cỡ chữ lớn nhất):** nội dung giữa cuộn được, nút chân trang không bị đè, tiêu đề xuống dòng. Kiểm ở Task 2 (khung chung) và Task 7.
5. **Back hệ thống:** lùi một bước (0.5 → 0.4/0.3 theo Q5); ở 0.1 thì đóng Onboarding về Home, chưa đánh dấu xong (như Q6). Kiểm ở Task 1.

---

### Task 1: Khung Onboarding, splash, điểm vào từ Home

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces:
  - `enum class OnboardingStep(val index: Int, @StringRes val primaryLabel: Int, @StringRes val secondaryLabel: Int?, val showBack: Boolean, val showSkip: Boolean) { WELCOME, THEME, WALLPAPER, DEFAULT_LAUNCHER, DONE }` — `index` 0..4 cho `DsStepIndicatorView.currentStep`; nhãn: Bắt đầu / Tiếp tục / Tiếp tục / Đặt làm mặc định + Để sau / Về màn hình chính; back ẩn ở WELCOME; Bỏ qua ẩn ở DONE
  - `data class OnboardingSelection(val theme: ThemeId = ThemeId.LIQUID_GLASS_27, val wallpaperSwatch: Int = 0)`
  - `class OnboardingViewModel @Inject constructor(state: SavedStateHandle, appearance: AppearanceRepository, appState: AppStateRepository, applyTheme: ApplyThemeUseCase, defaultLauncher: DefaultLauncherRepository)`: `val step: StateFlow<OnboardingStep>`, `val selection: StateFlow<OnboardingSelection>`, `fun next()`, `fun back(): Boolean` (false = đang ở WELCOME), `fun skip()`, `fun finish()`, `val events: Flow<OnboardingEvent>` (`Close`)
  - `finish()` và `skip()`: `appState.setOnboardingDone(true)` rồi phát `Close`
  - `class OnboardingActivity : AppCompatActivity` (Hilt); `companion fun intent(context): Intent`
  - `AuroraBackgroundDrawable(context, spots: List<AuroraSpot> = DEFAULT)` + `AuroraSpots.ONBOARDING_1..5` (màu/vị trí theo từng màn Figma)
- Consumes: `AppStateRepository`, `ApplyThemeUseCase`, `AppearanceRepository` (CF); `DsOnboardingTopBar`, `DsStepIndicatorView`, `DsButton` (DS3).

- [x] **Step 1:** `OnboardingStep`, `OnboardingViewModel` (bước + lựa chọn lưu ở `SavedStateHandle` khóa `step`, `theme`, `swatch`), `OnboardingActivity` + `activity_onboarding.xml`: thanh trên (`DsOnboardingTopBar`), khung nội dung `FrameLayout`, chân trang (`DsStepIndicatorView` + `DsButton` PRIMARY + `DsButton` SECONDARY). **Cố định, không trượt:** thanh trên (back/Bỏ qua hiện-ẩn bằng mờ dần 150 ms), chỉ báo bước (chấm dài chạy sang bằng hoạt ảnh 200 ms sẵn có của `DsStepIndicatorView`), hàng nút (đổi nhãn tại chỗ; nút phụ "Để sau" ở 0.4 hiện bằng mờ dần + giãn chiều cao chân trang, đúng Figma: chân trang 0.4 cao 180 thay 124). Nền aurora đổi bộ đốm bằng mờ chéo cùng lúc trượt. **Chỉ phần giữa trượt:** layout mới vào từ phải khi tới, từ trái khi lùi (`translationX` + alpha, 280 ms), màn cũ gỡ khi xong. Bấm nút trong lúc đang trượt: bỏ qua (chống bấm hai lần). Tạm thời mỗi bước là layout trống có tiêu đề (Task 2–6 thay). Back hệ thống (`OnBackPressedCallback`): `back()`, false thì `finish()` Activity.
- [x] **Step 2:** `AuroraBackgroundDrawable` nhận danh sách đốm; mặc định giữ y hệt hiện tại (catalog DS3 không đổi); 5 bộ cho 5 màn theo Figma (màu, góc, độ mờ).
- [x] **Step 3:** Manifest: `OnboardingActivity` (`exported=false`, `screenOrientation="nosensor"` như Home, theme DS3 tối, `windowBackground` = `ds_bg_base` để không nháy trắng). Splash: `values-v31/themes.xml` cho theme của `HomeActivity` (`windowSplashScreenBackground` `@color/ds_bg_base`, `windowSplashScreenAnimatedIcon` = icon app); API 26–30: `windowBackground` = `drawable/splash_background.xml` (layer-list nền + icon giữa) chỉ trong theme khởi động, `HomeActivity` đặt lại nền sau `onCreate` nếu cần (kiểm Home không đổi màu nền).
- [x] **Step 4:** `HomeActivity.onCreate`: khi `savedInstanceState == null`, đọc `appState.state.first().onboardingDone` (lifecycleScope); false thì `startActivity(OnboardingActivity.intent(this))` (không hoạt ảnh mở). Home vẫn dựng bình thường bên dưới.
- [x] **Step 5:** `ThemeDebugReceiver`: `--es id OB_RESET` ghi `onboardingDone = false` (log `CF`); để kiểm lại không phải xóa dữ liệu.
- [x] **Step 6:** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
- [x] **Step 7: Kiểm (Live 4).** (a) `OB_RESET` rồi `adb shell am force-stop gambi.global.os26launcher`, bấm Home: splash nền tối có icon, rồi Onboarding màn 1/5; (b) "Tiếp tục"/Back đi hết 5 bước và lùi lại, hoạt ảnh đúng chiều, chỉ báo đúng; Back ở 0.1 về Home; (c) xoay màn (nếu máy cho) / `adb shell am kill` khi Onboarding ở nền rồi mở lại từ Đa nhiệm: đúng bước (Review Focus 1); (d) "Bỏ qua" về Home, mở lạnh lại không hiện Onboarding; (e) Home dựng lại (đổi kiểu icon trong Cài đặt rồi về) không mở Onboarding.
- [x] **Step 8:** Commit `feat: add onboarding shell, splash and first-run entry`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 2: 0.1 Chào mừng

**Bắt buộc kiểm tay:** có (so với Figma).

- [x] **Step 1:** Xuất ảnh hero (Q1) → `onb_hero.webp`.
- [x] **Step 2:** `onboarding_welcome.xml` + `WelcomeStep`: hero 300×375 giữa (tỉ lệ giữ, co theo bề rộng khi màn hẹp), tiêu đề 26 Bold căn giữa, phụ đề 14 `ds_text_secondary` căn giữa; chip ngôn ngữ có trong layout nhưng `GONE` (quyết định 2); thanh trên và nút theo cấu hình bước (Task 1). Vùng giữa nằm trong `NestedScrollView` (Review Focus 4).
- [x] **Step 3:** Build + kiểm: chụp màn so với `302:3677` (khoảng cách, cỡ chữ); cỡ chữ hệ thống lớn nhất: chữ xuống dòng, nút không bị đè.
- [x] **Step 4:** Commit `feat: add onboarding welcome step`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 3: 0.2 Chọn kiểu giao diện

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces: `class ThemeCardView : FrameLayout` — `fun bind(theme: ThemeId, @DrawableRes preview: Int, name: CharSequence, description: CharSequence, isNew: Boolean)`, `var isChosen: Boolean` (dấu chọn tròn `ds_accent`, viền sáng); dùng lại ở màn 1.1 sau này.
- Consumes: `ApplyThemeUseCase` (CF), `DsSurface`, `DsBadgeView`.

- [x] **Step 1:** Xuất 4 ảnh xem trước (Q1) → `onb_theme_liquid_glass_27.webp`, `onb_theme_classic_26.webp`, `onb_theme_minimal.webp`, `onb_theme_dark_pro.webp`.
- [x] **Step 2:** `ThemeCardView` + `view_onboarding_theme_card.xml` (thẻ kính bo 20, đệm 8/8/10, ảnh 142:150 bo 12, tên 14 SemiBold, mô tả 12, badge "MỚI" cho DARK_PRO); `onboarding_theme.xml` lưới 2 cột khoảng cách 12, thẻ co theo bề rộng.
- [x] **Step 3:** `ThemeStep`: chọn sẵn `selection.theme` (lần đầu = theme đang lưu nếu có, không thì LIQUID_GLASS_27); chạm thẻ đổi lựa chọn (chưa ghi); "Tiếp tục" → `applyTheme(selection.theme)` rồi `next()` (Q4). Chuỗi tên/mô tả theo Figma.
- [x] **Step 4:** Build + kiểm: chọn từng theme, Tiếp tục, lùi, chọn khác; sau khi xong Onboarding Home đúng theme cuối; logcat `CF` (`STATE`) `modified=false`; Home bên dưới dựng lại không mở Onboarding lần hai (Review Focus 3).
- [x] **Step 5:** Commit `feat: add onboarding theme picker`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 4: 0.3 Chọn hình nền (ô màu tạm)

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces: `object WallpaperSwatches { val list: List<Swatch> }`, `data class Swatch(@StringRes val name: Int, val color: Int?)` — `color = null` là "Theo giao diện" (màu của `ThemePresets.of(theme).wallpaperColor`).

- [x] **Step 1:** Đo màu chủ đạo 5 ảnh nhỏ Figma (`302:3763..3767`) bằng ảnh xuất, ghi vào `WallpaperSwatches` và Nhật ký (Q3).
- [x] **Step 2:** `view_onboarding_phone_preview.xml`: khung 176×312 bo 28 tô màu đang chọn, `TextClock` 40 Light, ngày 12 (định dạng theo locale), dock kính 4 ô `DsIconTileView` (icon DS3 điện thoại, tin nhắn, trình duyệt, nhạc) — không dùng icon app thật.
- [x] **Step 3:** `onboarding_wallpaper.xml` + `WallpaperStep`: xem trước giữa, hàng ô 56×96 bo 14 cuộn ngang (`HorizontalScrollView`), ô chọn có dấu ✓ tròn `ds_accent` 20 dp; chọn sẵn ô theo `selection.wallpaperSwatch`; chạm ô đổi xem trước ngay. "Tiếp tục": ô "Theo giao diện" → không ghi gì (theme đã ghi màu); ô khác → `appearance.update { it.copy(wallpaperKind = COLOR, wallpaperColor = c) }` rồi `next()`.
- [x] **Step 4:** Build + kiểm: từng ô đổi xem trước; Tiếp tục với ô màu → Home nền đúng màu sau khi xong; ô "Theo giao diện" → `modified=false`; lùi về 0.2 đổi theme rồi tới 0.3: ô 1 đổi theo theme mới.
- [x] **Step 5:** Commit `feat: add onboarding wallpaper step with solid colors`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 5: 0.4 Đặt làm màn hình chính

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces: `DefaultLauncherAction(activity: AppCompatActivity, repository: DefaultLauncherRepository, onResult: (isDefault: Boolean) -> Unit = {})` — thay tham số `SettingsViewModel`; hành vi B12 giữ nguyên (đo thời gian hộp thoại, màn dự phòng, Toast lỗi).
- Consumes: `DefaultLauncherRepository` (đã có).

- [x] **Step 1:** Sửa `DefaultLauncherAction` nhận repository + `onResult`; `SettingsActivity` truyền repository (inject) thay ViewModel. Không đổi gì nhìn thấy ở Cài đặt.
- [x] **Step 2:** Xuất minh họa → `onb_default_launcher.webp`. `onboarding_default_launcher.xml` + `DefaultLauncherStep`: minh họa 180, tiêu đề, phụ đề, thẻ kính 2 bước (số trong ô tròn 28 `DsSurface` bo 14), hai nút ở chân trang cố định (Task 1). Tên app trong chữ lấy `@string/app_name` (Figma ghi "Launcher OS").
- [x] **Step 3:** "Đặt làm mặc định" → `request()`; `onResult(true)` hoặc `onResume` thấy đã là mặc định → `next()`; Hủy → ở lại. "Để sau" → `next()`. Tới 0.4 mà đã là mặc định → ViewModel bỏ qua bước này cả khi tiến lẫn lùi (Q5).
- [x] **Step 4:** Build + kiểm (Review Focus 2): máy chưa mặc định → hộp thoại hệ thống, chọn app → sang 0.5; Hủy → ở lại; máy đã mặc định → 0.3 Tiếp tục nhảy thẳng 0.5; Cài đặt → Launcher mặc định vẫn chạy như trước.
- [x] **Step 5:** Commit `feat: add onboarding default launcher step`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 6: 0.5 Hoàn tất

**Bắt buộc kiểm tay:** có.

- [x] **Step 1:** Xuất minh họa → `onb_done.webp`. `onboarding_done.xml` + `DoneStep`: minh họa 200, tiêu đề, phụ đề, `DsCardGroup` 3 `DsRowView` CHEVRON có ô icon màu (icon DS3) và giá trị: tên theme; tên ô màu ("Theo giao diện" hiện tên màu nền theme); "Đã đặt mặc định"/"Chưa đặt" (đọc `isDefaultLauncher()` lúc hiện). Thanh trên và nút theo cấu hình bước (back có, Bỏ qua ẩn, Q2); nút chính "Về màn hình chính" → `finish()` của ViewModel.
- [x] **Step 2:** Chạm hàng → nhảy về bước đó (0.2 / 0.3 / 0.4; hàng 3 khi đã mặc định thì không làm gì); Tiếp tục từ bước đó đi tiếp tới 0.5 như thường.
- [x] **Step 3:** Build + kiểm: giá trị đúng theo lựa chọn; chạm từng hàng; "Về màn hình chính" → Home đúng giao diện, mở lạnh lại không hiện Onboarding.
- [x] **Step 4:** Commit `feat: add onboarding summary step`. **Gộp** vào một commit của chủ dự án (xem Task 7 Step 3).

### Task 7: Kiểm cả luồng, chốt tài liệu

**Bắt buộc kiểm tay:** có.

- [x] **Step 1: Bài kiểm cả tính năng (Live 4):** `OB_RESET` + force-stop → đi hết luồng với mỗi theme một lần; Bỏ qua ở từng bước (giữ phần đã áp, Q2); bấm nút Home giữa chừng (Q6: mở lạnh hiện lại); `adb shell am kill` khi ở nền ở 0.3 (Review Focus 1); cỡ chữ lớn nhất (Review Focus 4); chế độ sáng hệ thống (Onboarding vẫn tối). Hồi quy: Home, App Library, Cài đặt → Launcher mặc định, áp theme bằng adb.
- [x] **Step 2a:** Gỡ `OB-TEMP` ở `HomeViewModel.isOnboardingDone()` và log `OBDBG` (2026-11-17). Kiểm lại Task 1 Step 7 (d) trong Step 1.
- [x] **Step 2:** `Figma-Draft.md` hàng 2 (đã làm / chưa làm / %); `project_structure.md` "Tệp thêm ở OB"; `config-schema.md` cột người đọc của `onboardingDone`; bài học vào `lessons/` theo chủ đề; tick plan, ghi kết quả kiểm chứng.
- [ ] **Step 3:** Commit `docs: close OB onboarding`.

---

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-17 | — | Viết plan sau khi đọc Figma 0.0–0.5 (figma-bridge) và code sau CF | Chờ chủ dự án trả lời Q1–Q6 và duyệt |
| 2026-11-17 | — | Ghi rõ theo câu hỏi chủ dự án: thanh trên, chỉ báo bước, hàng nút cố định ở Activity; chỉ phần giữa trượt; bước khai cấu hình nút/thanh trên trong `OnboardingStep` | |
| 2026-11-17 | 1–6 | Chủ dự án duyệt plan, Q1–Q6 theo khuyến nghị, bảo làm một mạch. **Ảnh (Q1):** xuất bằng figma-bridge `get_screenshot` 3x (`save_screenshots` không ghi được ra ngoài thư mục làm việc của server), tạm ẩn dấu chọn `302:3707` **và 4 chữ giờ** `347:1898..1901` rồi bật lại ngay (đã kiểm `visible: true`); ảnh xem trước cắt bỏ phần bóng đổ (522×546 → 426×450); 7 file `res/drawable-nodpi/onb_*.webp` (WebP q88). **Ô màu (Q3):** Lagoon `#047C8A`, Night Bloom `#4D0C71`, Sunset `#882015`, Prism `#143365`, Deep Blue `#020828` (trung bình vùng giữa ảnh nhỏ). **Code:** `ui/onboarding/` (`OnboardingStep` + `OnboardingEvent`, `OnboardingViewModel`, `OnboardingActivity`, `OnboardingPages` (5 trang), `OnboardingCatalog` (`ThemeCatalog`, `WallpaperSwatches`), `ThemeCardView` + `RatioFrameLayout`); layout `activity_onboarding`, `onboarding_welcome/theme/wallpaper/default_launcher/done`, `view_onboarding_theme_card`; drawable `onb_check_circle`, `onb_ic_check`, `splash_background`; `values-v31/themes.xml`; `AuroraBackgroundDrawable(context, spots)` + `AuroraSpot`/`AuroraSpots` (DEFAULT giữ y hệt, 5 bộ ONBOARDING); `HomeActivity` (theme khởi động, `startOnboardingIfNeeded` giữ khung hình đầu tối đa 1,5 s); `HomeViewModel.isOnboardingDone()`; `DefaultLauncherAction(activity, repository, onResult)` + `SettingsActivity` inject repository, bỏ 2 hàm thừa ở `SettingsViewModel`; manifest; 37 chuỗi `onboarding_`; `ThemeDebugReceiver` `OB_RESET` | **Khác plan:** (1) 5 trang gộp vào `OnboardingPages.kt`, catalog vào `OnboardingCatalog.kt` thay vì mỗi màn một file (mỗi trang < 60 dòng); (2) giờ trên thẻ theme là `TextClock` thật thay vì "9:41" trong ảnh; (3) chip ngôn ngữ không có trong layout (thay vì `GONE`), ghi chú ở `onboarding_welcome.xml`; (4) hàng "Hình nền" ở 0.5 hiện tên ô ("Theo giao diện" / tên Figma); (5) chân trang: nút back và "Bỏ qua" ẩn bằng `INVISIBLE` sau mờ dần. Chưa build (VM không build được) |
| 2026-11-17 | 1 | **OB-TEMP** theo yêu cầu chủ dự án (kiểm cho dễ): `HomeViewModel.isOnboardingDone()` trả `false` ở bản debug, nên mỗi lần Home dựng mới (mở lạnh, không phải `recreate()`) đều mở Onboarding; release vẫn đọc cờ. **Phải gỡ ở Task 7** (grep `OB-TEMP`) | Kiểm "Bỏ qua"/"xong thì không hiện lại" chỉ làm được sau khi gỡ |
| 2026-11-17 | 5 | **Lỗi chủ dự án báo:** 0.4 bấm "Đặt làm mặc định" → kẹt ở splash. Log `OBDBG` + `dumpsys activity`: chọn xong, PermissionController gửi intent HOME (uid 10185), hệ thống dựng `HomeActivity` MỚI trong task Home mới (`type=home`, process giữ nguyên); Home mới (cờ `OB-TEMP`) mở Onboarding MỚI từ 0.1 trong cùng task; vì Home đang bị giữ khung hình đầu, hệ thống chuyển cửa sổ splash của Home sang Onboarding (`startingMoved=true`) và không gỡ (`STARTING_WINDOW_SHOWN` dù `firstWindowDrawn=true`) → splash đè lên. Onboarding cũ ở 0.4 (task thường) không nhận kết quả. **Sửa (chủ dự án chọn phương án 2):** `OnboardingActivity` task riêng (`taskAffinity=${applicationId}.onboarding`, `singleTask`, `excludeFromRecents`), Home mở bằng `FLAG_ACTIVITY_NEW_TASK`: khác task thì không chuyển splash; Onboarding đang dở được đưa lên đúng bản cũ (`onNewIntent`), `onResume` thấy đã mặc định thì sang 0.5. Q6 đổi theo | Đã xét: bỏ giữ khung hình đầu (Home nháy 1 khung), tìm task bằng `appTasks.moveToFront()` + `finishAndRemoveTask()` (chỉ chữa đường 0.4). Không chặn được intent HOME của hệ thống. Log `OBDBG` giữ tới khi chủ dự án kiểm đạt |
| 2026-11-17 | 5 | **Hoàn tác** phương án task riêng (manifest, `FLAG_ACTIVITY_NEW_TASK`, `onNewIntent`), Q6 về như cũ. Chủ dự án kiểm: sau khi đặt mặc định vẫn mở Onboarding MỚI ở 0.1; vào Đa nhiệm chọn màn cũ thì ra Home | Lỗi 0.4 chưa sửa. Log `OBDBG` vẫn giữ |
| 2026-11-17 | 5 | **Sửa lỗi 0.4 theo cách chủ dự án đề xuất ("Home mới thấy cờ false thì mở thẳng 0.5").** (1) `OnboardingRuntime` (object, trong process): Onboarding bật `pendingDefaultLauncher` ngay trước khi mở hộp thoại, xóa ở `onResume` (hủy hộp thoại); Home mới đọc-và-xóa, có thì mở Onboarding với extra `start_at_done` (ViewModel đọc qua `SavedStateHandle`, chọn sẵn theme + ô màu khớp config đang áp để hàng tóm tắt đúng). (2) Home mới nhận HOME: `removeStaleAppTasks()` dọn mọi task khác của app (Home cũ + Onboarding ở 0.4) bằng `appTasks…finishAndRemoveTask()`. (3) Splash kẹt: **bỏ giữ khung hình đầu**; thay bằng che nội dung Home (`INVISIBLE`, nền cửa sổ `ds_bg_base`): Home vẽ khung tối để hệ thống gỡ splash của nó; Onboarding chỉ mở sau khung đầu + 50 ms (`afterFirstFrame`), nên splash không bị chuyển sang Onboarding; nội dung hiện lại ở `onStop` hoặc sau 1,5 s | Q5/Q6 không đổi. Không có nháy Home (khung đầu là màu nền giống splash, thiếu icon). Log `OBDBG` giữ tới khi kiểm đạt |
| 2026-11-17 | 5, 7 | Chủ dự án kiểm cách sửa 0.4 (quyết định 7–8): đạt. Gỡ log `OBDBG` (`LauncherApp`, `HomeActivity`, `OnboardingActivity`) và `OB-TEMP` (cờ đọc thật cả bản debug). Tick Task 1–6 (trừ bước commit: chủ dự án gộp một commit). Ghi quyết định 7–9 | Chủ dự án: khi cờ về bình thường thì đường "đã là mặc định, force-stop rồi bấm Home" ít gặp, không cần lo thêm |
| 2026-11-17 | 1 | **Splash thiếu logo + thanh trạng thái tím** (chủ dự án báo: thấy icon Android mặc định). Nguyên nhân: splash dùng `@mipmap/ic_launcher` (icon mẫu của template) thay vì logo Figma; theme khởi động kế thừa màu thanh hệ thống của Material3. Sửa: xuất logo `302:3676` (ngôi sao trắng) 8x, vẽ sẵn quầng sáng `#4F6DFF` (Figma `302:3675`, ellipse 134 dp, độ đục 0,5, mờ 68) vào ảnh 288 dp: `drawable-nodpi/splash_logo.webp` (API 26–30, `splash_background.xml`) và `splash_logo_v31.webp` (API 31+, quầng nhỏ hơn để tắt trước vòng tròn 192 dp hệ thống cắt). `Theme.Os26Launcher.Starting`: `statusBarColor`/`navigationBarColor` = `ds_bg_base`, icon thanh sáng; thêm `values-v27/themes.xml` cho `windowLightNavigationBar` | Quầng sáng giờ có (bỏ khỏi "Ngoài phạm vi"). Icon app (`mipmap/ic_launcher`) vẫn là icon mẫu: ngoài phạm vi OB |
| 2026-11-17 | 7 | Chủ dự án kiểm splash 5 bước (mở lạnh từ icon, mở lạnh khi là mặc định, đã xem thì vào thẳng Home, chế độ sáng/tối): đạt. Cả luồng đã kiểm qua các lượt trước (0.1–0.5, Bỏ qua, Back, đặt mặc định, Hủy hộp thoại). Chốt tài liệu: `Figma-Draft.md` hàng 2 (90%), `project_structure.md` "Tệp thêm ở OB", `config-schema.md` người đọc/ghi `onboardingDone`, `lessons/home-pager-animation.md` mục OB (5 bài học), `lessons/README.md`. Tick hết; bước commit Task 1–6 gộp | |
