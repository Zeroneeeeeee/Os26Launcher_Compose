# CF — Tái cấu trúc config lựa chọn người dùng + preset theme — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (không cần Oppo: không đổi cách vẽ kính). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-17 · **Plan duyệt:** 2026-11-17 (chủ dự án, Q1–Q4 theo khuyến nghị)
**Trạng thái:** Task 1–4 xong; Task 5 (tài liệu) xong, chờ commit `docs: close CF and sync project docs`
**Máy:** Live 4 · **Đổi schema Room:** có (version 1 → 2, **không viết Migration**: phá dữ liệu cũ, quyết định 3)

**Goal:** Gom mọi lựa chọn của người dùng về một sơ đồ rõ ràng: mỗi nhóm cài đặt là một model + một repository, có tài liệu `docs/config-schema.md` liệt kê từng trường (khóa, kiểu, mặc định, màn, theme có ghi không); khai sẵn trường cho các màn v3 sắp làm; gộp app ẩn + tên app tự đặt (+ chỗ cho icon tự đổi) vào bảng Room `app_overrides`; thêm 4 preset theme áp một lần và nền màu đặc. Không thêm màn mới (màn 1.1 và Onboarding là plan sau).

**Architecture:** Kho giá trị vẫn là DataStore `launcher_settings`, **tên khóa cũ giữ nguyên**, khóa mới đặt tiền tố nhóm. `SettingsRepository` (một model `LauncherSettings` ôm mọi thứ) tách thành `AppearanceRepository`, `GeneralConfigRepository`, `AppStateRepository`; `HomeTogglesRepository` thành `HomeScreenConfigRepository`; `SearchPreferencesRepository` thành `SearchConfigRepository`; thêm `MotionConfigRepository`; `WeatherPreferencesRepository` giữ nguyên. Mọi repository nhóm cùng một hợp đồng: `val config: Flow<X>` + `suspend fun update(transform: (X) -> X)`; `update` ghi trong **một** `DataStore.edit` và **chỉ ghi khóa có giá trị đổi** (khóa vắng vẫn là mặc định). Tùy chỉnh theo app nằm ở bảng Room `app_overrides` (khóa = component). Theme là `AppearanceConfig` cài sẵn trong code; áp theme = một lần `update`.

**Tech Stack:** Kotlin, DataStore Preferences, Room 2.8.5, Hilt (đều đã có). Không dependency mới.

**Spec:** Thảo luận với chủ dự án 2026-11-17 (theme áp một lần = phương án 1; gom config theo nhóm = phương án 1; tùy chỉnh theo app vào Room; không viết migration vì app đang phát triển). `docs/Figma-Draft.md` mục 2–3 (danh sách màn v3), `docs/room-schema.md`.

## Vì sao chọn thiết kế này

**Vấn đề cần giải (đọc code `main` @ `ed5d481`, 2026-11-17):**
- Lựa chọn người dùng nằm ở 3 file DataStore (`launcher_settings`, `app_labels`, `control_center`), 4 file, 1 bảng Room. Riêng `launcher_settings` có 4 bảng khóa (`SettingsKeys`, `HomeTogglesKeys`, `SearchPrefsKeys`, `WeatherPrefsKeys`) với tiền tố lẫn lộn (`grid_preset`, `icon_style` không tiền tố; `home_toggle_*`, `glass_look_*`, `search_*`, `weather_*` có).
- `SettingsRepository` có 12 setter riêng lẻ; muốn áp theme (ghi ~15 trường một lúc) phải gọi 12 lần, mỗi lần một `edit` và một lần phát lại `settings` cho mọi màn đang nghe; dừng giữa chừng thì áp dở.
- Tùy chỉnh theo app nằm hai nơi: app ẩn ở Room `hidden_apps` (dọn trong transaction của `LayoutReconciler` khi gỡ app), tên tự đặt ở DataStore `app_labels` (tự dọn bằng `AppLabelRepository.removePackage/removeUser` theo sự kiện). Màn 5.2 (icon tự đổi) sẽ thành nơi thứ ba nếu không gom.
- Chưa có khái niệm theme, chưa có nền màu đặc; Onboarding (0.2, 0.3) và màn 1.1 cần cả hai.

**Các phương án đã xét** (chọn cùng chủ dự án 2026-11-17):

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Model + repository theo nhóm, giữ DataStore, giữ tên khóa** | Như Architecture | Không mất dữ liệu cài đặt; áp theme một lần ghi; tài liệu schema là nguồn chân lý | Tên khóa cũ vẫn lẫn kiểu; sửa ~15 file đang dùng repository cũ | **Chọn** |
| B. Một file JSON `launcher_config.json` | `AtomicFile` + Flow tự viết, chuyển dữ liệu một lần | Một file đọc được | Viết lại mọi repository, tự lo ghi tuần tự và Flow mà DataStore đã có | Không |
| C. Chỉ viết tài liệu | Không sửa code | Ít việc | Áp theme vẫn gọi 12 setter, code vẫn rời | Không |

**Tùy chỉnh theo app:** bảng Room `app_overrides` thay `hidden_apps` + `app_labels` (thay vì giữ hai nơi): khi gỡ app, transaction đang có của `LayoutReconciler` xóa luôn cả tên lẫn trạng thái ẩn; màn 5.2 thêm cột chứ không thêm kho. Không đặt vào `launcher_items` vì bảng đó là vị trí, app ẩn không có hàng (`room-schema.md` mục 0, plan RN).

**Đánh đổi chấp nhận:** cài đè lên bản có Room v1 sẽ **xóa bố cục Home, app ẩn** (quyết định 3); tên tự đặt cũ trong `app_labels` bị bỏ (file còn trên máy nhưng không ai đọc, gỡ cài lại là sạch). Trước khi phát hành, mọi đổi schema sau v2 phải có Migration viết tay (quy tắc cũ của `LauncherDatabase` áp lại từ v2).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Theme áp một lần: ghi giá trị preset vào từng trường + `appearance_theme_id`; người dùng chỉnh tiếp tự do; "đã chỉnh" = config hiện tại khác preset | Chủ dự án 2026-11-17 |
| 2 | Gom config theo nhóm (phương án A), tên khóa cũ giữ nguyên | Chủ dự án 2026-11-17 |
| 3 | Tùy chỉnh theo app vào Room `app_overrides`; **không viết Migration**: Room v2 dùng `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)` (chỉ từ v1), không chép `app_labels` | Chủ dự án 2026-11-17: app đang phát triển, xóa cài lại được. Claude chọn fallback chỉ-từ-v1 để máy thử không sập khi cài đè |
| 4 | `docs/config-schema.md` là nguồn chân lý mọi lựa chọn người dùng; `room-schema.md` giữ riêng (xem Câu hỏi Q4) | Claude đề xuất |
| 5 | Trường mới mà tính năng chưa làm: khai đủ (khóa, kiểu, mặc định = hành vi hiện tại), **chưa ai đọc**, không có UI. Màn sau tự nối | Chủ dự án 2026-11-17 ("phần nào thiếu tính năng thì ẩn") |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Nền icon của 4 theme map vào `icon_style` thế nào? | Kính = `GLASS` (có), gradient màu = `THREE_D` (có, "Fluffy 3D"), màu dịu = `TINTED` (có), trong suốt = `TRANSPARENT` (**mới**, chưa vẽ được: tạm vẽ như `DEFAULT`, ẩn khỏi màn 2.3) || Đồng ý (chủ dự án 2026-11-17). `THREE_D` dùng cho DARK_PRO |
| Q2 | Giá trị 4 preset (bảng "Preset theme" bên dưới) | Dùng bảng đề xuất, chỉnh sau khi xem trên máy || Giữ đề xuất, chỉnh bằng mắt sau (2026-11-17) |
| Q3 | Áp theme có ghi đè ảnh nền người dùng tự chọn (PHOTO) không? | Có (áp một lần là ghi đủ, đúng quyết định 1). Onboarding 0.3 chọn sau theme nên vẫn đổi được || Có (2026-11-17) |
| Q4 | Gộp `room-schema.md` vào `config-schema.md`? | Không gộp. `room-schema.md` là thiết kế bảng bố cục (giải thích từng cột, ví dụ, quyết định lịch sử, 55 file link tới). `config-schema.md` là bản đồ "lựa chọn người dùng nằm ở đâu", chỉ trỏ sang `room-schema.md` cho bảng `app_overrides`. Hai file khác mục đích, khác nhịp sửa || Không gộp (2026-11-17) |

## Ngoài phạm vi / ghi để sau

- Màn 1.1 chọn theme, card theme trên Home cài đặt, Onboarding: plan sau.
- Đọc các trường mới (hình dạng/cỡ icon, độ trong kính, màu ánh kính, tông bề mặt, chung, màn hình nền, chuyển động, tìm tệp/web, khóa app ẩn): plan của từng tính năng.
- Màn 5.2 (icon tự đổi): cột `customIconPath` có sẵn, chưa ghi; xóa file icon khi gỡ app làm cùng 5.2.
- Nền `PRESET` (thư viện hình nền, G3f): chỉ khai giá trị enum.
- Hỏi "áp làm hình nền hệ thống" cho nền màu: không hỏi (coi như nền mặc định).
- `control_center` (DataStore riêng) và các file JSON thời tiết/widget ảnh: giữ nguyên, chỉ ghi vào `config-schema.md`.

## Vùng file

Đường dẫn tính từ `app/src/main/java/gambi/global/os26launcher/`.

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `docs/config-schema.md` | Tạo | Task 1 |
| `docs/room-schema.md`, `docs/project_structure.md` | Sửa | Task 3, Task 5 |
| `domain/config/*.kt` (model + enum + interface repository) | Tạo | Task 2 |
| `data/config/*.kt` (khóa, repository impl) | Tạo | Task 2 |
| `data/settings/*` (`SettingsKeys`, `SettingsRepositoryImpl`, `HomeTogglesKeys`, `HomeTogglesRepositoryImpl`), `data/search/SearchPrefsKeys.kt` + impl, `domain/repository/SettingsRepository.kt`, `HomeTogglesRepository.kt`, `SearchPreferencesRepository.kt`, `domain/model/LauncherSettings.kt`, `HomeToggles.kt`, `SearchPreferences.kt` | Xóa (thay bằng file mới) | **Cần chủ dự án duyệt xóa** (AGENTS.md); liệt kê lại ở báo cáo Task 2 |
| ~15 file đang dùng các repository trên (`core/glass/GlassEnvironment.kt`, `data/apps/IconStyleSource.kt`, `LauncherAppsDataSource.kt`, `data/layout/LayoutReconciler.kt`, `data/search/SystemSettingsRepositoryImpl.kt`, `ui/home/HomeViewModel.kt`, `ui/search/SearchViewModel.kt`, `ui/settings/*ViewModel.kt`, …) | Sửa | Task 2: chỉ đổi kiểu và lời gọi, không đổi hành vi |
| `di/RepositoryModule.kt`, `di/SearchModule.kt`, `di/DataStoreModule.kt`, `di/DatabaseModule.kt` | Sửa | Task 2, 3 |
| `data/layout/db/AppOverrideEntity.kt`, `AppOverrideDao.kt` | Tạo | Task 3 |
| `data/layout/db/HiddenAppEntity.kt`, `HiddenAppDao.kt`, `LauncherDatabase.kt`, `app/schemas/.../2.json` | Sửa / sinh | Task 3 |
| `data/apps/AppLabelRepositoryImpl.kt`, `AppLabelKeys.kt` | Sửa / xóa `AppLabelKeys` | Task 3 |
| `domain/config/ThemePreset.kt`, `domain/usecase/ApplyThemeUseCase.kt`, `data/backdrop/ColorBackdropSource.kt`, `data/backdrop/BackdropRepositoryImpl.kt` | Tạo / Sửa | Task 4 |
| `src/debug/.../debug/ThemeDebugReceiver.kt`, `src/debug/AndroidManifest.xml` | Tạo / Sửa | Task 4 |

## Global Constraints

- **Tên chuỗi khóa DataStore cũ không đổi** (bảng "Sơ đồ config", cột "Khóa"); tên tiền tố mới: `appearance_`, `general_`, `home_`, `search_`, `motion_`, `apps_`.
- Mặc định của mọi trường mới = hành vi hiện tại của app (khóa vắng không được đổi gì trên màn hình).
- Mọi repository nhóm: `update` = một `DataStore.edit`, chỉ ghi khóa đổi giá trị; `config` có `.catch { IOException -> emit(emptyPreferences()) }` và `.distinctUntilChanged()`; đọc enum lạ hoặc null → mặc định, không crash (mẫu `IconStyle.fromName`).
- Task 2 **không đổi hành vi** nào nhìn thấy được.
- SQLite trên API 26 (3.18) không có `UPSERT`: dùng `INSERT OR IGNORE` + `UPDATE` trong `@Transaction`.
- Không dependency mới, không test. Commit tiếng Anh theo `AGENTS.md`. Không tự xóa file: liệt kê, chờ duyệt.

## Review Focus

1. **Kính tùy chỉnh lần đầu (GS D8):** chuyển sang mức "Tùy chỉnh" lần đầu vẫn gieo công tắc theo mức cũ; `update` ghi giá trị trùng mặc định không được làm `glassCustomSaved` thành true. Kiểm ở Task 2.
2. **Ẩn + đổi tên cùng một app:** đổi tên, ẩn, bỏ ẩn → tên riêng còn; khôi phục tên khi đang ẩn → app vẫn ẩn; hàng chỉ biến mất khi mọi cột về mặc định. Kiểm ở Task 3.
3. **Gỡ app có tên riêng + đang ẩn, cài lại:** hiện tên gốc, không ẩn. Hồ sơ công việc: đổi tên/ẩn "Chrome" công việc không ảnh hưởng "Chrome" chính. Kiểm ở Task 3.
4. **Cài đè lên bản Room v1 đang có dữ liệu:** không sập, Home dựng lại bố cục mặc định. Kiểm ở Task 3.
5. **Áp theme rồi chỉnh tay:** áp một lần ghi đủ (không thấy trạng thái nửa vời); chỉnh một trường → "đã chỉnh"; chỉnh về đúng giá trị preset → hết "đã chỉnh". Kiểm ở Task 4.

---

## Sơ đồ config (nội dung chính của `docs/config-schema.md`)

### DataStore `launcher_settings`

Khóa đậm là khóa mới. "Theme" = preset có ghi trường này.

| Model . trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `AppearanceConfig.themeId` | **`appearance_theme_id`** | `ThemeId?` (LIQUID_GLASS_27, CLASSIC_26, MINIMAL, DARK_PRO) | null | 1.1, 0.2 | ghi | — |
| `.wallpaperKind` | **`appearance_wallpaper_kind`** | `WallpaperKind` (AUTO, PHOTO, COLOR, PRESET) | AUTO (= hành vi cũ: ảnh đã chọn nếu có, không thì ảnh mặc định) | 2.2, 0.3 | ghi | Task 4 |
| `.wallpaperColor` | **`appearance_wallpaper_color`** | Int ARGB | `0xFF000000` | 2.2, 0.3 | ghi | Task 4 |
| `.iconStyle` | `icon_style` | `IconStyle` + **TRANSPARENT** | DEFAULT | 2.3 | ghi | có |
| `.iconTint` | `icon_tint` | `IconTint` | BLUE | 2.3 | ghi | có |
| `.iconPack` | `icon_pack` | String? | null | 2.3 | **không** | có |
| `.iconShape` | **`appearance_icon_shape`** | `IconShapeKind` (SQUIRCLE, CIRCLE, SQUARE, TEARDROP) | SQUIRCLE | 2.3 | ghi | — |
| `.iconCorner` | **`appearance_icon_corner`** | Int 0..100 | 62 | 2.3 | ghi | — |
| `.iconSize` | **`appearance_icon_size`** | Int 0..100 | 50 | 2.3 | ghi | — |
| `.showLabels` | `show_labels` | Boolean | true | 1.2 | ghi | có |
| `.glassLevel` | `glass_level_override` | `GlassLevelOverride` | AUTO | 2.1 | ghi | có |
| `.glassCustom` | `glass_custom_dock`, `_panels`, `_search_bar`, `_pill`, `_icon_menu` | `GlassCustom` | như hiện tại | 2.1 | **không** | có |
| `.glassLook` | `glass_look_refraction`, `_gloss`, `_blur`, `_corner` | `GlassLook` | 0/50/50/50 | 2.1 | ghi | có |
| `.glassTransparency` | **`appearance_glass_transparency`** | Int 0..100 | 70 | 2.1 | ghi | — |
| `.glassTint` | **`appearance_glass_tint`** | Int ARGB, 0 = không tô | 0 | 2.1 | ghi | — |
| `.surfaceTone` | **`appearance_surface_tone`** | `SurfaceTone` (LIGHT, DARK) | LIGHT | (theme) | ghi | — |
| `GeneralConfig.gridPreset` | `grid_preset` | `GridPreset` | DEFAULT | 1.2 | không | có |
| `.navBarHidden` | **`general_nav_bar_hidden`** | Boolean | false | 1.2 | không | — |
| `.autoArrange` | **`general_auto_arrange`** | Boolean | true | 1.2 | không | — |
| `.blurEffect` | **`general_blur_effect`** | Boolean | true | 1.2 | không | — |
| `.textFollowSystem` | **`general_text_follow_system`** | Boolean | true | 1.2 | không | — |
| `.textScale` | **`general_text_scale`** | Int % 80..130 | 100 | 1.2 | không | — |
| `HomeScreenConfig.pageDots` | `home_toggle_page_dots` | Boolean | true | 3.1 | không | có |
| `.swipeDownSearch` | `home_toggle_swipe_down_search` | Boolean | true | 3.3 | không | có |
| `.searchPill` | `home_toggle_search_pill` | Boolean | true | 3.3 | không | có |
| `.pageTransition` | **`home_page_transition`** | `PageTransition` (DEFAULT, CUBE, STACK, FLIP, ZOOM, ROTATE) | DEFAULT | 3.1 | không | — |
| `.libraryEnabled` | **`home_library_enabled`** | Boolean | true | 3.2 | không | — |
| `.libraryStyle` | **`home_library_style`** | `LibraryStyle` (FOLDERS, ALPHABETICAL) | FOLDERS | 3.2 | không | — |
| `.libraryAutoCategorize` | **`home_library_auto_categorize`** | Boolean | true | 3.2 | không | — |
| `.librarySuggestions` | **`home_library_suggestions`** | Boolean | false | 3.2 | không | — |
| `.newAppPlacement` | **`home_new_app_placement`** | `NewAppPlacement` (HOME, LIBRARY_ONLY) | HOME | 3.2 | không | — |
| `.badgesEnabled` | **`home_badges_enabled`** | Boolean | false | 3.4 | không | — |
| `.badgeStyle` | **`home_badge_style`** | `BadgeStyle` (NUMBER, DOT) | NUMBER | 3.4 | không | — |
| `SearchConfig.*` | `search_contacts`, `search_system_settings`, `search_shortcuts`, `search_suggestions` (`search_recent_apps` chỉ là khóa cũ để dọn) | như `SearchPreferences` hiện tại | như hiện tại | 3.3 | không | có |
| `.files` | **`search_files`** | Boolean | false | 3.3 | không | — |
| `.webSuggestions` | **`search_web_suggestions`** | Boolean | false | 3.3 | không | — |
| `.webEngine` | **`search_web_engine`** | String? (package), null = mặc định hệ thống | null | 3.3 | không | — |
| `MotionConfig.transitionStyle` | **`motion_transition_style`** | `TransitionStyle` (SMOOTH, FADE, SLIDE, ZOOM, NONE) | SMOOTH | 4.1 | không | — |
| `.transitionSpeed` | **`motion_transition_speed`** | `MotionSpeed` (SLOW, MEDIUM, FAST) | MEDIUM | 4.1 | không | — |
| `.reduceMotion` | **`motion_reduce`** | Boolean | false | 4.1 | không | — |
| `.appOpenEffect` | **`motion_app_open_effect`** | `AppOpenEffect` (ZOOM_FROM_ICON, FADE, SLIDE_UP, BOUNCE) | ZOOM_FROM_ICON | 4.2 | không | — |
| `.appOpenSpeed` | **`motion_app_open_speed`** | Int 0..100 | 55 | 4.2 | không | — |
| `.pressShrink` | **`motion_press_shrink`** | Boolean | false | 4.2 | không | — |
| `.hapticOnOpen` | **`motion_haptic_on_open`** | Boolean | false | 4.2 | không | — |
| `WeatherConfig` (giữ `WeatherPreferencesRepository`) | `weather_temperature_unit`, `weather_notify_daily`, `weather_notify_tomorrow` | như hiện tại | như hiện tại | 1.4 | không | có |
| `GeneralConfig.hiddenAppsLock` | **`apps_hidden_lock`** | Boolean | false | 5.1 | không | — |
| `AppState.onboardingDone` | `first_run_done` | Boolean | false | Onboarding | — | — |
| `AppState.defaultLauncherPromptShown` | `default_launcher_prompt_shown` | Boolean | false | — | — | có |

Cờ trạng thái giữ ở repository của tính năng (không phải lựa chọn người dùng, ghi vào schema với nhãn "trạng thái"): `search_contacts_asked` (`SearchConfigRepository`), `weather_notify_shown_daily`, `weather_notify_shown_tomorrow` (`WeatherPreferencesRepository`).

Khóa app ẩn chỉ có một trường nên nằm trong `GeneralConfig.hiddenAppsLock` (khóa vẫn tiền tố `apps_` để sau này tách nhóm không phải đổi tên). Tổng cộng 5 repository nhóm (Appearance, General, HomeScreen, Search, Motion) + `AppStateRepository` + `WeatherPreferencesRepository` giữ nguyên.

### Room `launcher.db` v2

| Bảng | Cột | Ghi chú |
|---|---|---|
| `launcher_items`, `home_pages` | không đổi | Bố cục (`room-schema.md`) |
| **`app_overrides`** | PK (`packageName` TEXT, `className` TEXT, `userSerial` INTEGER); `hidden` INTEGER 0/1 (mặc định 0); `customLabel` TEXT null; `customIconPath` TEXT null | Thay `hidden_apps`. Một hàng tồn tại khi ít nhất một cột khác mặc định; ghi xong mà mọi cột về mặc định thì xóa hàng |

### Kho khác (giữ nguyên, chỉ ghi vào schema)

DataStore `control_center` (15 khóa, liệt kê theo `ControlCenterPrefsStore`); file `files/backdrop_image` (ảnh khi `wallpaperKind = PHOTO`, hoặc AUTO và có file); `files/control_center_background.jpg`; `noBackupFilesDir/weather_places.json` (địa điểm, chế độ vị trí); `noBackupFilesDir/photo_widgets.json` + ảnh; ngôn ngữ app do hệ thống lưu (`AppCompatDelegate.setApplicationLocales`). Cache (`weather.json`, `search_recent_apps.txt`, cache icon và nhãn) không phải config.

### Preset theme (chốt 2026-11-17, chỉnh bằng mắt khi kiểm Task 4)

| Trường | LIQUID_GLASS_27 | CLASSIC_26 | MINIMAL | DARK_PRO |
|---|---|---|---|---|
| `wallpaperKind` / `wallpaperColor` | COLOR `#FF1E3A8A` (xanh) | COLOR `#FF27142C` (tím mận tối) | COLOR `#FF11141B` (gần đen) | COLOR `#FF000000` (đen) |
| `iconStyle` / `iconTint` | GLASS / BLUE | DEFAULT / BLUE | TRANSPARENT / BLUE | TINTED / GRAPHITE |
| `iconBevel` | true | false | false | true |
| `iconShape` / `iconCorner` / `iconSize` | SQUIRCLE / 62 / 50 | SQUIRCLE / 62 / 50 | SQUARE / 30 / 50 | CIRCLE / 100 / 50 |
| `showLabels` | true | true | false | true |
| `glassLevel` / `glassLook` (khúc xạ/bóng/mờ/bo góc) | AUTO / 40/60/55/50 | FORCE_LOW / 0/30/50/50 | FORCE_LOW / 0/20/40/30 | AUTO / 20/40/60/50 |
| `glassTransparency` / `glassTint` / `surfaceTone` | 70 / 0 / LIGHT | 80 / 0 / LIGHT | 90 / 0 / LIGHT | 50 / 0 / DARK |

Màu nền là lựa chọn tạm (chưa có thư viện ảnh nền); ảnh xem trước theme là việc của màn 1.1.

---

### Task 1: Tài liệu `docs/config-schema.md`

**Bắt buộc kiểm tay:** không (chủ dự án đọc duyệt).

**Files:** Create `docs/config-schema.md`.

- [x] **Step 1:** Chép mục "Sơ đồ config" trên thành `config-schema.md`, thêm: đoạn mở đầu (file này là nguồn chân lý; thêm/đổi khóa phải sửa file này trong cùng commit), quy tắc đặt khóa (tiền tố nhóm, chữ thường gạch dưới, không đổi tên khóa đã phát hành), quy tắc mặc định (khóa vắng = mặc định; mặc định = hành vi hiện tại), bảng 15 khóa `control_center` (đọc `ControlCenterPrefsStore.kt`), một dòng trỏ `room-schema.md` mục 4 cho `app_overrides`.
- [x] **Step 2:** Báo chủ dự án đọc. Commit `docs: add the user config schema` (chủ dự án commit).

---

### Task 2: Model và repository theo nhóm (không đổi hành vi)

**Bắt buộc kiểm tay:** có — đụng mọi màn đọc cài đặt (`Workflow.md` mục 3).

**Files:** Create `domain/config/` (`AppearanceConfig.kt`, `GeneralConfig.kt`, `HomeScreenConfig.kt`, `SearchConfig.kt`, `MotionConfig.kt`, `AppState.kt`, `ConfigEnums.kt`, `ConfigRepositories.kt`), `data/config/` (`ConfigKeys.kt`, `AppearanceRepositoryImpl.kt`, `GeneralConfigRepositoryImpl.kt`, `HomeScreenConfigRepositoryImpl.kt`, `SearchConfigRepositoryImpl.kt`, `MotionConfigRepositoryImpl.kt`, `AppStateRepositoryImpl.kt`). Modify consumers + DI. Xóa (chờ duyệt) các file cũ ở "Vùng file".

**Interfaces:**
- Produces (trong `domain/config/ConfigRepositories.kt`):
  - `interface ConfigRepository<T> { val config: Flow<T>; suspend fun update(transform: (T) -> T) }`
  - `interface AppearanceRepository : ConfigRepository<AppearanceConfig>` — thêm `val glassCustomSaved: Flow<Boolean>` (GS D8)
  - `interface GeneralConfigRepository : ConfigRepository<GeneralConfig>`; `HomeScreenConfigRepository : ConfigRepository<HomeScreenConfig>`; `SearchConfigRepository : ConfigRepository<SearchConfig>`; `MotionConfigRepository : ConfigRepository<MotionConfig>`
  - `interface AppStateRepository { val state: Flow<AppState>; suspend fun setOnboardingDone(done: Boolean); suspend fun setDefaultLauncherPromptShown(shown: Boolean) }`
  - Model là `data class` có mặc định đúng bảng "Sơ đồ config"; enum mới trong `ConfigEnums.kt` mỗi cái có `companion fun fromName(name: String?): X` trả mặc định khi lạ.
  - `GridPreset`, `IconStyle` (+ `TRANSPARENT`), `IconTint`, `GlassLevelOverride`, `GlassCustom`, `GlassLook` giữ ở `domain/model` (không chuyển chỗ).
- Consumes: `DataStore<Preferences>` không qualifier (`launcher_settings`).

- [x] **Step 1:** Viết `ConfigKeys.kt`: một `internal object` mỗi nhóm (`AppearanceKeys`, `GeneralKeys`, `HomeScreenKeys`, `SearchKeys`, `MotionKeys`, `AppStateKeys`), chuỗi khóa đúng bảng. Khóa cũ copy nguyên chuỗi.
- [x] **Step 2:** Viết 6 impl. Hàm dùng chung `private suspend fun <T> DataStore<Preferences>.updateGroup(read: (Preferences) -> T, transform: (T) -> T, write: MutablePreferences.(old: T, new: T) -> Unit)` trong `data/config/ConfigStore.kt`; mỗi impl viết `write` so từng trường `if (old.x != new.x) this[KEY] = new.x` (enum ghi `name`, nullable null thì `remove`). `IconStyle.TRANSPARENT` thêm vào enum; `IconStyleSource` vẽ `TRANSPARENT` như `DEFAULT` (KDoc ghi "chưa làm").
- [x] **Step 3:** Đổi mọi nơi dùng `SettingsRepository`/`LauncherSettings`, `HomeTogglesRepository`/`HomeToggles`, `SearchPreferencesRepository`/`SearchPreferences` sang repository/model mới (`grep -rn "SettingsRepository\|LauncherSettings\|HomeToggles\|SearchPreferences" app\src`). Setter cũ thành `update { it.copy(...) }`. `setGlassCustom` (gieo cả bốn) giữ ý nghĩa: một `update` đổi cả `glassCustom`. `firstRunDone` → `AppState.onboardingDone`.
- [x] **Step 4:** DI: `@Binds` 6 repository mới trong `RepositoryModule` (và `SearchModule` nếu đang bind ở đó); bỏ bind cũ. Liệt kê file cũ cần xóa vào báo cáo, chờ duyệt rồi mới xóa.
- [x] **Step 5:** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
- [x] **Step 6: Kịch bản kiểm (Live 4, cài đè bản `main` đang có cài đặt khác mặc định: lưới 5×6, ẩn nhãn, kiểu icon Tô màu hồng, mức kính Tùy chỉnh với dock tắt, tắt chấm trang, tắt tìm danh bạ, °F).** (a) Sau khi cài: mọi giá trị trên còn nguyên ở Home và trong Cài đặt (chứng minh tên khóa không đổi); (b) đổi từng giá trị trong Cài đặt, Home cập nhật như trước; (c) xóa dữ liệu app, mở lại, chuyển mức kính sang Tùy chỉnh lần đầu: công tắc được gieo theo mức cũ (Review Focus 1); (d) `adb shell run-as gambi.global.os26launcher cat files/datastore/launcher_settings.preferences_pb | findstr appearance_` không ra gì (chưa trường mới nào bị ghi khi chỉ chỉnh trường cũ).
- [x] **Step 7:** Commit `refactor: split launcher settings into grouped config repositories`.

---

### Task 3: Bảng `app_overrides` (Room v2), gộp app ẩn và tên tự đặt

**Bắt buộc kiểm tay:** có — đổi schema, dọn khi gỡ app, hồ sơ công việc.

**Files:** Create `data/layout/db/AppOverrideEntity.kt`, `AppOverrideDao.kt`. Modify `HiddenAppEntity.kt`, `HiddenAppDao.kt`, `LauncherDatabase.kt`, `di/DatabaseModule.kt`, `data/apps/AppLabelRepositoryImpl.kt`, `di/DataStoreModule.kt`, `di/RepositoryModule.kt` (nếu đổi tham số), `docs/room-schema.md`. Xóa (chờ duyệt) `data/apps/AppLabelKeys.kt`.

**Interfaces:**
- Produces:
  - `@Entity(tableName = "app_overrides", primaryKeys = ["packageName", "className", "userSerial"]) data class AppOverrideEntity(packageName: String, className: String, userSerial: Long, hidden: Boolean = false, customLabel: String? = null, customIconPath: String? = null)`
  - `HiddenAppEntity` thành data class thường (bỏ `@Entity`, giữ tên và 3 trường) dùng làm kiểu trả về; `HiddenAppDao` thành `abstract class`, **giữ nguyên tên và chữ ký mọi hàm** để `LayoutReconciler`, `LayoutRepositoryImpl` không phải sửa.
  - `@Dao abstract class AppOverrideDao { fun observeLabels(): Flow<List<AppLabelRow>>; suspend fun setLabel(packageName: String, className: String, userSerial: Long, label: String?); suspend fun deleteByPackage(packageName: String, userSerial: Long); suspend fun deleteByUser(userSerial: Long) }` với `data class AppLabelRow(packageName: String, className: String, userSerial: Long, customLabel: String)`
- Consumes: `AppLabelRepository` (interface giữ nguyên).

- [x] **Step 1:** SQL của `HiddenAppDao` trên bảng `app_overrides`:
  - `observeAll()`, `getAll()`: `SELECT packageName, className, userSerial FROM app_overrides WHERE hidden = 1`
  - `insert(entity)` (`@Transaction`): `INSERT OR IGNORE INTO app_overrides(packageName, className, userSerial, hidden) VALUES(:p, :c, :u, 0)` rồi `UPDATE app_overrides SET hidden = 1 WHERE <khóa>`
  - `delete(p, c, u)` (`@Transaction`): `UPDATE ... SET hidden = 0 WHERE <khóa>` rồi `pruneEmpty()`
  - `deleteByPackage`, `deleteByUser`: `DELETE` cả hàng (gỡ app thật = bỏ mọi tùy chỉnh)
  - `renameComponent`: `UPDATE app_overrides SET className = :newClass WHERE ...` (mọi tùy chỉnh theo app đổi activity)
  - `pruneEmpty()`: `DELETE FROM app_overrides WHERE hidden = 0 AND customLabel IS NULL AND customIconPath IS NULL`
- [x] **Step 2:** `AppOverrideDao.setLabel` (`@Transaction`): `INSERT OR IGNORE` hàng mặc định, `UPDATE ... SET customLabel = :label`, `pruneEmpty()` (dùng chung câu lệnh, khai trong `AppOverrideDao`). `AppLabelRepositoryImpl` đọc/ghi qua `AppOverrideDao` (giữ ComponentKey, giữ `distinctUntilChanged`); `removePackage`/`removeUser` gọi `deleteByPackage`/`deleteByUser`. Bỏ provider `@Named("app_labels")` và delegate `appLabelsDataStore` trong `DataStoreModule`.
- [x] **Step 3:** `LauncherDatabase`: `entities = [LauncherItemEntity, HomePageEntity, AppOverrideEntity]`, `version = 2`, thêm `abstract fun appOverrideDao(): AppOverrideDao`; sửa chú thích "KHÔNG BAO GIỜ fallbackToDestructiveMigration" thành: "Ngoại lệ duy nhất: v1 → v2 (CF, 2026-11-17, app chưa phát hành) phá dữ liệu. Từ v2 trở đi mọi đổi entity là Migration viết tay." `DatabaseModule`: `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`, provider `AppOverrideDao`. Build sinh `app/schemas/.../2.json`, commit cùng.
- [x] **Step 4:** `docs/room-schema.md`: mục 0 sửa dòng "Danh sách app bị ẩn" thành "Tùy chỉnh theo app (ẩn, tên, icon)"; mục 4 viết lại thành "Bảng `app_overrides`" (cột, quy tắc tồn tại hàng, quy tắc dọn khi gỡ app/xóa hồ sơ/đổi activity, vì sao không nằm trong `launcher_items`); dòng trạng thái đầu file thêm "v2 (CF, 2026-11-17)".
- [x] **Step 5:** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
- [x] **Step 6: Kịch bản kiểm (Live 4).** (a) Cài đè lên bản `main` có bố cục và app ẩn: không sập, Home dựng bố cục mặc định (Review Focus 4); (b) đổi tên app A, ẩn A, bỏ ẩn A → tên riêng còn; khôi phục tên khi A đang ẩn → A vẫn ẩn (Review Focus 2); (c) gỡ/cài lại và đổi activity: xem "Chi tiết kiểm Task 3 (c), (d)" ngay dưới; (d) hồ sơ công việc: như trên (Review Focus 3); (e) đổi tên app khi đang mở màn 5.3: danh sách cập nhật.
  **Chi tiết kiểm Task 3 (c), (d)** (PowerShell, bản debug; `$P = "gambi.global.os26launcher"`, `$W = "$env:USERPROFILE\fx3-test"`; APK thử dựng bằng `New-FxApk` ở `docs/report/FX3-layout-reconciler.md` mục "Công cụ kiểm", đã có thì dùng lại `$W\fx3-rename-v1.apk`, `$W\fx3-rename-v2.apk`; đọc DB bằng `Get-FxDb`/`Q` cùng mục đó, truy vấn `SELECT * FROM app_overrides`):
  - **(c1) Gỡ thật dọn hết:** `adb install $W\fx3-rename-v1.apk` → Cài đặt → Thay đổi tên ứng dụng → "FX3 Rename" đổi thành "Thu A" → về Home, nhấn giữ icon "Thu A" → Ẩn ứng dụng. Kiểm: icon biến khỏi Home, có trong Cài đặt → Ứng dụng ẩn với tên "Thu A"; DB có 1 hàng `('dev.fx3.renametest', '...OldMain', 0, 1, 'Thu A', NULL)`. `adb uninstall dev.fx3.renametest`, đợi 3 giây: hàng biến mất khỏi `app_overrides`, màn Ứng dụng ẩn trống. `adb install $W\fx3-rename-v1.apk` lại: icon hiện trên Home với tên gốc "FX3 Rename", không ẩn.
  - **(c2) Đổi activity khi cập nhật giữ tùy chỉnh:** từ trạng thái cuối (c1), bỏ ẩn "FX3 Rename" ở màn Ứng dụng ẩn, đổi tên thành "Thu B" (Cài đặt → Thay đổi tên ứng dụng), rồi ẩn lại từ Home. `adb install -r $W\fx3-rename-v2.apk` (activity `.OldMain` → `.NewMain`), đợi 6 giây. Kiểm: DB còn **1** hàng, `className = dev.fx3.renametest.NewMain`, `hidden = 1`, `customLabel = 'Thu B'`; màn Ứng dụng ẩn vẫn liệt kê "Thu B". Dọn: `adb uninstall dev.fx3.renametest`.
  - **(d) Hồ sơ công việc:** `adb shell pm create-user --profileOf 0 --managed Work` (ghi id in ra, ví dụ 10) → `adb shell am start-user 10` → `adb install $W\fx3-rename-v1.apk` (user chính) → `adb install --user 10 $W\fx3-rename-v1.apk` (hồ sơ). Home có hai icon "FX3 Rename", bản hồ sơ có huy hiệu cặp táp. (d1) Cài đặt → Thay đổi tên ứng dụng: hai hàng "FX3 Rename" phân biệt được (hàng hồ sơ có icon huy hiệu cặp táp); đổi tên **hàng hồ sơ** thành "Thu Work" → chỉ icon có huy hiệu đổi tên, icon user chính vẫn "FX3 Rename". (d2) Nhấn giữ icon hồ sơ → Ẩn: chỉ icon hồ sơ biến, icon user chính còn. DB: đúng 1 hàng, `userSerial` ≠ 0, `hidden = 1`, `customLabel = 'Thu Work'`. (d3) `adb shell pm remove-user 10`, đợi 5 giây: hàng của hồ sơ biến khỏi `app_overrides`; icon user chính không đổi. Dọn: `adb uninstall dev.fx3.renametest`; `adb shell pm list users` chỉ còn user 0. Lưu ý: tạo hồ sơ làm launcher đặt thêm icon của hồ sơ vào bố cục (đã biết từ FX5); bỏ hồ sơ thì chúng được dọn.
- [x] **Step 7:** Commit `refactor: merge hidden apps and custom labels into app_overrides`.

---

### Task 4: Preset theme, áp theme, nền màu đặc

**Bắt buộc kiểm tay:** có — nhìn kết quả áp theme trên Home.

**Files:** Create `domain/config/ThemePreset.kt`, `domain/usecase/ApplyThemeUseCase.kt`, `domain/usecase/ObserveThemeStateUseCase.kt`, `data/backdrop/ColorBackdropSource.kt`, `src/debug/java/.../debug/ThemeDebugReceiver.kt`. Modify `data/backdrop/BackdropRepositoryImpl.kt`, `src/debug/AndroidManifest.xml`.

**Interfaces:**
- Produces:
  - `enum class ThemeId { LIQUID_GLASS_27, CLASSIC_26, MINIMAL, DARK_PRO }` (trong `ConfigEnums.kt`, Task 2)
  - `object ThemePresets { fun of(id: ThemeId): AppearanceConfig; fun AppearanceConfig.applyPreset(id: ThemeId): AppearanceConfig }` — `applyPreset` chép mọi trường có cột "Theme = ghi" từ preset, giữ `iconPack`, `glassCustom`, đặt `themeId = id`
  - `class ApplyThemeUseCase @Inject constructor(repo: AppearanceRepository) { suspend operator fun invoke(id: ThemeId) }` — một `update { it.applyPreset(id) }`
  - `data class ThemeState(val id: ThemeId?, val modified: Boolean)`; `class ObserveThemeStateUseCase { operator fun invoke(): Flow<ThemeState> }` — `modified = id != null && config.applyPreset(id) != config`
  - `BackdropRepository` không đổi chữ ký; nội bộ đọc `AppearanceRepository.config`
- Consumes: `AppearanceRepository`, `AppearanceConfig` (Task 2).

- [x] **Step 1:** `ThemePresets` với giá trị bảng "Preset theme" (đã chốt 2026-11-17).
- [x] **Step 2:** `ColorBackdropSource.load(color: Int, reqW: Int, reqH: Int): Bitmap` tạo bitmap `RGB_565` cỡ yêu cầu tô một màu. `BackdropRepositoryImpl`: `wallpaperKind == COLOR` → nguồn màu, `isDefault = true` (không hỏi áp hình nền hệ thống); `PHOTO` hoặc `AUTO` → hành vi cũ; `PRESET` → như `AUTO`. `backdropVersions` phát giá trị mới khi `wallpaperKind` hoặc `wallpaperColor` đổi (Home tải lại nền và làm mới kính như khi đổi ảnh). `importBackdrop` thành công → `update { it.copy(wallpaperKind = PHOTO) }`.
- [x] **Step 3:** `ThemeDebugReceiver` (chỉ bản debug, khai như các receiver CP/WN: `exported="true"`, `android:permission="android.permission.DUMP"`), action `gambi.global.os26launcher.CF_APPLY_THEME`, extra `id` = tên `ThemeId` hoặc `NONE` (xóa `appearance_theme_id`); log `CF` in `ThemeState` sau khi áp.
- [x] **Step 4:** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
- [x] **Step 5: Kịch bản kiểm (Live 4).** Lệnh: `adb shell am broadcast -a gambi.global.os26launcher.CF_APPLY_THEME --es id DARK_PRO -n gambi.global.os26launcher/gambi.global.os26launcher.debug.ThemeDebugReceiver`. Chạy lệnh khi Home đang hiện: Home tự dựng lại một lần (kiểu icon, kính) và đổi nền màu, nhãn ngay, không cần bấm gì. Kiểm thêm: chạy lệnh khi đang ở Cài đặt rồi quay về Home cũng ra đúng. Cũng có `--es id STATE` (chỉ in trạng thái) và `--es id NONE` (bỏ theme). (a) Mỗi theme: Home đổi nền màu, kiểu icon, nhãn, kính trong một lần (không thấy đổi từng phần); Cài đặt hiện đúng giá trị mới; (b) logcat `CF` báo `modified=false`; đổi kiểu icon trong Cài đặt → `modified=true`; đổi lại đúng giá trị preset → `false` (Review Focus 5); (c) chọn ảnh nền trong Cài đặt → nền là ảnh, `wallpaperKind=PHOTO`; áp theme lại → nền màu (Q3); (d) gói icon đang chọn giữ nguyên sau khi áp theme.
- [x] **Step 6:** Commit `feat: add theme presets and solid color wallpaper` (mốc trước bevel; Step 5 kiểm lại sau Step 7).
- [x] **Step 7: Lớp nổi 3D chồng lên kiểu icon (chủ dự án chọn hướng 2026-11-17, bảo làm tiếp).** Xong, commit `06df467`. Lý do: ảnh xem trước 0.2 cho thấy icon Liquid Glass = ô kính + lớp nổi + nét trắng, Dark Pro = ô tròn gradient đen + lớp nổi + nét trắng; `IconStyle` chọn một nên 3D loại trừ Kính/Tô màu.
  - `AppearanceConfig.iconBevel: Boolean = false`, khóa mới **`appearance_icon_bevel`** (`AppearanceKeys`, đọc/ghi trong `AppearanceRepositoryImpl` bằng `putIfChanged`). Thêm hàng vào `config-schema.md` (Theme = ghi, màn: không có, chỉ theme ghi) cùng commit.
  - `IconStyleKey` thêm `bevel: Boolean = false` (khóa cache tách theo cờ); `HomeViewModel.currentIconStyleKey()` và `IconStyleSource` điền `bevel = s.iconBevel`.
  - `IconRenderer.render`: vẽ `bevel(out, sizePx)` khi `style.style == IconStyle.THREE_D || style.bevel`. Bevel vẽ sau bước cắt nên chồng được lên GLASS, TINTED, TRANSPARENT.
  - `ThemePresets`: LIQUID_GLASS_27 và DARK_PRO `iconBevel = true`; CLASSIC_26, MINIMAL `false`. Áp theme ghi cờ (trường theme ghi), `modified` tính cả cờ.
  - Màn 2.3 không đổi (chọn một kiểu như Figma); chọn kiểu khác trong 2.3 không xóa cờ → Home vẫn nổi, thẻ theme báo `modified` chỉ khi kiểu khác preset. Bỏ theme (`NONE`) không đổi cờ.
  - Kiểm: Step 4 + Step 5 (a) cho LIQUID_GLASS_27, DARK_PRO: icon có vệt sáng mép trên và bóng đáy trên nền kính/ô đen; CLASSIC_26, MINIMAL không có. Commit `feat: layer the 3D bevel over other icon styles`.
  - **Bỏ (chủ dự án 2026-11-17):** lấy nét đơn sắc từng icon (`getMonochrome()`, bộ nét tự vẽ cho app hệ thống). Giữ quyết định G2b D2.

---

### Task 5: Chốt tài liệu

**Bắt buộc kiểm tay:** không.

- [x] **Step 1:** `config-schema.md`: cột "Ai đọc hôm nay" cập nhật theo code thật; thêm mục "Preset theme" (bảng giá trị đã chốt).
- [x] **Step 2:** `project_structure.md`: thêm `domain/config/`, `data/config/`; bỏ dòng `SettingsRepository`, `HomeTogglesRepository`, `SearchPreferencesRepository`, file `app_labels`; thêm "Tệp thêm ở CF". `AGENTS.md`/`lessons/settings-and-data.md`: quy tắc "thêm khóa = sửa `config-schema.md` cùng commit"; bài học SQLite API 26 không có UPSERT.
- [x] **Step 3:** Commit `docs: close CF and sync project docs`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)

**Máy:** Live 4. **Chuẩn bị:** bản `main` cài sẵn có cài đặt khác mặc định (như Task 2 Step 6) và vài app ẩn, vài tên tự đặt.
**Thứ tự:** cài bản CF đè lên → Task 2 (a) (cài đặt DataStore còn) → Task 3 (a) (bố cục, app ẩn, tên mất là đúng) → Task 3 (b)–(d) → Task 4 (a)–(d).
**Hồi quy:** tìm kiếm (danh bạ, cài đặt hệ thống, phím tắt), thời tiết (°C/°F, thông báo), Trung tâm điều khiển, widget ảnh: không đổi.
**Kết quả kiểm chứng (máy, ngày):** Live 4, 2026-11-17, chủ dự án kiểm theo từng Task (Task 2 `6f16f15`, Task 3 `a776ae7`, Task 4 `cc80d0e` + `0de31e6` + `06df467`), đạt. Build do chủ dự án chạy (phiên VM không build được).

## Nhật ký

| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-17 | — | Viết plan sau thảo luận với chủ dự án | Chủ dự án trả lời Q1–Q4 (theo khuyến nghị), duyệt plan |
| 2026-11-17 | 1 | Viết `docs/config-schema.md` (8 nhóm khóa `launcher_settings`, 16 khóa `control_center`, `app_overrides`, bản đồ kho, preset theme) | Sửa plan: `search_recent_apps` là khóa cũ để dọn (không phải lựa chọn); mức kính preset ghi đúng tên enum `FORCE_LOW`. Chủ dự án bảo làm tiếp Task 2 không chờ |
| 2026-11-17 | 2 | `domain/config/` (6 model, `ConfigEnums`, `ConfigRepositories`), `data/config/` (`ConfigKeys`, `ConfigStore` + 6 impl), `di/ConfigModule`. Đổi sang repository mới: `GlassEnvironment`, `IconStyleSource`, `LayoutReconciler`, `RecentAppsRepositoryImpl`, `HomeViewModel`, `HomeActivity`, `HomeTogglesController`, `SearchViewModel`, `SettingsViewModel`, `HomeTogglesViewModel`, `SearchSettingsViewModel`, `SearchSettingsScreen`, `SwipeSettingsScreen`, `SearchSettingsBlock`, `IconSetSettingsScreen`. `IconStyle.TRANSPARENT` (vẽ như DEFAULT, không có trong hộp chọn của 2.3) | Phiên VM không build được (`Workflow.md` mục 5): chủ dự án build. Thêm `AppearanceRepository.seedGlassCustom` (ghi đủ 5 khóa kể cả trùng mặc định) để giữ đúng GS D8, vì `update` chỉ ghi khóa đổi. Binding và file cũ **giữ tới khi chủ dự án duyệt xóa** (danh sách ở báo cáo Task 2), nên bản build vẫn còn repository cũ nhưng không ai dùng |
| 2026-11-17 | 2 | Sửa sót: `SearchViewModel.dismissContactsPrompt` còn gọi `setContacts(false)` của repository cũ, đổi sang `update { it.copy(contacts = false) }` (chủ dự án phát hiện). Rà lại mọi setter cũ: không còn chỗ nào | Bài học: git trong VM không có `core.autocrlf` nên `git diff` hiện cả file (cây làm việc CRLF, index LF); xem diff thật bằng `git diff --ignore-cr-at-eol`. Ghi vào lessons ở Task 5 |
| 2026-11-17 | 2 | Chủ dự án duyệt và xóa file cũ. Claude: bỏ binding `bindSettingsRepository` (`RepositoryModule`), `bindSearchPreferencesRepository` (`SearchModule`); xóa `SearchPreferencesRepositoryImpl.kt` còn sót (trong danh sách đã duyệt); **khôi phục** `SystemSettingsRepositoryImpl.kt` bị xóa nhầm (không có trong danh sách, cần cho tìm Cài đặt hệ thống) bằng `git checkout` | Grep lại: không còn tham chiếu code tới lớp đã xóa (chỉ còn KDoc nhắc tên cũ) |
| 2026-11-17 | 2 | Chủ dự án build, kiểm, commit `6f16f15` và push. Task 2 xong | |
| 2026-11-17 | 3 | `AppOverrideEntity` (+ `AppLabelRow`), `AppOverrideDao`; `HiddenAppDao` thành `abstract class` trên `app_overrides` (giữ tên/chữ ký hàm); `HiddenAppEntity` thành data class thường; `LauncherDatabase` v2 + `appOverrideDao()`; `DatabaseModule`: `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`, provider `AppOverrideDao`; `AppLabelRepositoryImpl` đọc/ghi qua `AppOverrideDao`; bỏ DataStore `app_labels` khỏi `DataStoreModule`; `room-schema.md` mục 0, 4 và dòng trạng thái | Đổi so với plan: `AppOverrideDao` không có `deleteByPackage/deleteByUser` mà là `clearLabelsOfPackage/clearLabelsOfUser` (chỉ bỏ tên rồi `pruneEmpty`), để `AppLabelRepository.removePackage/removeUser` không bỏ ẩn app ngoài ý muốn; xóa cả hàng khi gỡ app vẫn do `HiddenAppDao.deleteByPackage/deleteByUser` trong transaction của `LayoutReconciler`. `AppLabelKeys.kt` không còn ai dùng: **chờ chủ dự án duyệt xóa**. Schema `app/schemas/.../2.json` sinh khi build |
| 2026-11-17 | 3 | Chủ dự án build, kiểm (a)–(e) trên Live 4: đạt hết. Chủ dự án commit. Task 3 xong | `AppLabelKeys.kt` vẫn chờ duyệt xóa |
| 2026-11-17 | 4 | `ThemePresets` (`of`, `applyPreset`), `ApplyThemeUseCase`, `ObserveThemeStateUseCase` + `ThemeState`; `ColorBackdropSource` (bitmap một màu cùng tỉ lệ vùng Home, nhỏ 4 lần mỗi chiều, phiên bản âm riêng mỗi màu); `BackdropRepositoryImpl` đọc nguồn nền từ `AppearanceRepository` (bộ đệm + collector phát `backdropVersions` khi đổi), nền COLOR đi đường "mặc định" (`hasBackdrop` false, `loadDefaultBackdrop` trả màu: không hỏi áp hình nền hệ thống), `importBackdrop` thành công ghi `wallpaperKind = PHOTO`; `BackdropRepository.awaitWallpaperConfig()` mới, `HomeViewModel` gọi trước lần nạp nền để khỏi nháy ảnh mặc định; `ThemeDebugReceiver` (debug, `id` = theme / `NONE` / `STATE`) + khai trong manifest debug | Thêm vào plan: `awaitWallpaperConfig` (lý do: hàm đồng bộ của repository đọc bộ đệm, chưa đọc thì coi như AUTO). Kiểu icon và kính chỉ áp ở `onResume` của Home (như đổi trong Cài đặt): kịch bản kiểm ghi rõ bấm Home sau mỗi lệnh |
| 2026-11-17 | 4 | Sửa lỗi chủ dự án báo: áp theme lúc Home đang hiện thì trang hiện tại, dock giữ kiểu icon cũ, chỉ App Library đổi. Nguyên nhân: `IconStyleSource` xóa `IconCache` ngay, còn view đang hiện chỉ dựng lại ở `onResume` (`RecreateGuard`); bấm Home khi Home ở trên cùng chỉ gọi `onNewIntent`. Sửa: `HomeViewModel.appearanceChanges` (`appearance.config.distinctUntilChanged().drop(1)`), `HomeActivity` thu khi RESUMED và gọi `recreateGuard.onResume()` | Chủ dự án chọn hướng B (Home tự phản ứng) thay vì chỉ sửa cách kiểm. Collector chỉ là coroutine treo, chỉ sống khi Home RESUMED; `drop(1)` chạy lại mỗi lần collect nên không trùng với check ở `onResume`. Bước kiểm Step 5 bỏ "bấm nút Home" |
| 2026-11-17 | 4 | Chỉnh preset theo ảnh xem trước 0.2 (Figma `302:3704`, chủ dự án chỉ hướng): Classic 26 nền `#27142C`, icon Mặc định; Minimal nền `#11141B`, icon Trong suốt; Dark Pro nền đen, icon Tô màu GRAPHITE (ô gradient đen, hình trắng-xám); Liquid Glass giữ nguyên. Làm thật `IconStyle.TRANSPARENT`: `IconRenderer.bareGlyph` chỉ vẽ foreground của adaptive icon (màu gốc), không tách được hình thì như Mặc định. Cài đặt 2.3 hiện nhãn "Trong suốt" (`settings_icon_style_transparent`), vẫn không cho chọn | Màu đo từ điểm ảnh ảnh xem trước. Rủi ro Minimal: hình foreground màu tối chìm trên nền gần đen |
| 2026-11-17 | 4 | Chủ dự án commit mốc `cc80d0e` (Step 6). Sửa lỗi chủ dự án báo: áp theme lúc App Library mở thì Library trắng (chỉ còn thanh tìm kiếm), vào lại thì cột trái tràn hết bề ngang. Log `CFDBG` (3 file, đã gỡ) cho thấy sau `recreate()` Library hiện trước lần layout đầu nên ô dựng trong `onSizeChanged` (giữa `layout()`). Sửa `CategoryPageView`: `post(rebuildAfterLayout)` | Lỗi có sẵn, lộ ra vì hướng B là đường `recreate()` đầu tiên lúc Library mở. Chủ dự án kiểm lại đạt, log xác nhận ô dựng sau lượt layout. Bài học ghi vào `lessons/home-pager-animation.md` và `lessons/settings-and-data.md` (một phần việc của Task 5) |
| 2026-11-17 | 4 | Step 7 code: `AppearanceConfig.iconBevel` + khóa `appearance_icon_bevel` (`AppearanceKeys`, đọc/ghi trong `AppearanceRepositoryImpl`); `IconStyleKey.bevel` + `bevelFor(style, bevel)` (chuẩn hóa: false khi THREE_D); `IconStyleSource`, `HomeViewModel.currentIconStyleKey()` điền cờ; khóa cache RAM (`IconCache`, `+bevel`) và đĩa (`IconLoader`, `|bevel`) chỉ nối khi bật; `IconRenderer` vẽ `bevel` khi `THREE_D || bevel`; preset LIQUID_GLASS_27, DARK_PRO bật. `config-schema.md` thêm hàng | Gói icon không áp lớp nổi (như Tô màu/3D trước đây). Commit mốc `cc80d0e`, sửa Library `0de31e6` đã xong trước Step 7 |
| 2026-11-17 | 4 | Chủ dự án build, kiểm Step 5 (a)–(d) và Step 7, commit `06df467`. Task 4 xong | |
| 2026-11-17 | 5 | `config-schema.md`: đối chiếu cột "Ai đọc hôm nay" với code (grep mọi trường ngoài `domain/config`, `data/config`; chỉ `themeId` đổi: nay `ObserveThemeStateUseCase`), bảng preset đã có. `project_structure.md`: cây thư mục bỏ `LauncherSettings`, `SettingsRepository`, `data/settings/` (thay `data/config/`), `HiddenAppEntity` ghi đổi CF; mục 6 Room `hidden_apps` → `app_overrides` + ngoại lệ phá dữ liệu v1→v2; bảng khóa DataStore thay bằng trỏ `config-schema.md`; thêm "Tệp thêm ở CF" (đã kiểm từng file có thật / đã bỏ). `AGENTS.md`: quy tắc "thêm khóa = sửa `config-schema.md` cùng commit" ở hàng Cài đặt và dữ liệu. Bài học (UPSERT API 26, `--ignore-cr-at-eol`, `onNewIntent`, `onSizeChanged`) đã ghi ở `0de31e6` | `AppLabelKeys.kt` chủ dự án đã xóa (không còn tham chiếu) |

## Bằng chứng Review Focus (điền khi chốt)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Kính tùy chỉnh lần đầu | | |
| 2 | Ẩn + đổi tên cùng app | | |
| 3 | Gỡ/cài lại, hồ sơ công việc | | |
| 4 | Cài đè lên Room v1 | | |
| 5 | Áp theme rồi chỉnh tay | | |

## Tài liệu chung đã cập nhật (điền ở bước 7)
