# Sơ đồ config: lựa chọn của người dùng nằm ở đâu

Trạng thái: **đang dùng** — tạo 2026-11-17 ở plan [CF](plans/CF-config-refactor.md). Cột "Ai đọc hôm nay" đối chiếu code lúc chốt CF (2026-11-17, sau commit `06df467`): "—" = chưa nơi nào ngoài `domain/config`, `data/config` đọc trường đó.

File này là **nguồn chân lý** cho mọi lựa chọn người dùng của launcher: trường nào, lưu ở kho nào, khóa gì, kiểu gì, mặc định gì, màn nào sửa, theme có ghi không. **Thêm, đổi, bỏ một khóa phải sửa file này trong cùng commit.**

Bố cục màn hình chính (trang, ô, thư mục, widget) không phải config: xem [room-schema.md](room-schema.md). Bảng Room `app_overrides` (tùy chỉnh theo từng app) có ở cả hai file: ở đây chỉ liệt kê, chi tiết cột và quy tắc ở `room-schema.md` mục 4.

## 1. Quy tắc

- **Đặt khóa:** chữ thường, gạch dưới, tiền tố theo nhóm: `appearance_`, `general_`, `home_`, `search_`, `motion_`, `apps_`, `weather_`. Khóa có trước CF (`icon_style`, `grid_preset`, `show_labels`, `glass_*`, `home_toggle_*`, `first_run_done`, …) **giữ nguyên tên**: đổi tên khóa = mất lựa chọn của người dùng.
- **Mặc định:** khóa vắng = giá trị mặc định ở bảng. Mặc định của trường mới phải bằng **hành vi hiện tại** của app (thêm khóa không được đổi gì trên màn hình).
- **Ghi:** mỗi nhóm có một repository với `config: Flow<X>` và `update { }`. Một lần `update` = một `DataStore.edit`, chỉ ghi khóa có giá trị đổi (nhờ vậy khóa chưa từng chỉnh vẫn vắng).
- **Đọc:** enum lạ hoặc null → mặc định, không crash; số ngoài khoảng → kẹp vào khoảng.
- **Trường chưa có tính năng:** vẫn khai đủ ở đây và trong model; cột "Ai đọc hôm nay" ghi "—". Tính năng làm sau tự nối, không đổi khóa.
- **Theme:** cột "Theme" = "ghi" nghĩa là áp theme ghi đè trường này (áp một lần, quyết định CF 1). Trường "không" là lựa chọn riêng, theme không đụng.

## 2. Bản đồ kho

| Kho | Vị trí trên máy | Nội dung | Sao lưu (Auto Backup) |
|---|---|---|---|
| DataStore `launcher_settings` | `files/datastore/launcher_settings.preferences_pb` | Mọi cài đặt dạng giá trị (mục 3) | Có |
| DataStore `control_center` | `files/datastore/control_center.preferences_pb` | Trung tâm điều khiển (mục 4) | Có |
| DataStore `today_widgets` | `files/datastore/today_widgets.preferences_pb` | Danh sách widget của Today View (mục 4b, TV-3) | Có |
| Room `launcher.db`, bảng `app_overrides` | `databases/launcher.db` | Tùy chỉnh theo app: ẩn, tên, icon (mục 5) | Có |
| File `backdrop_image` | `files/backdrop_image` | Ảnh nền người dùng chọn (dùng khi `appearance_wallpaper_kind` = PHOTO, hoặc AUTO và file có) | Có |
| File `control_center_background.jpg` | `files/` | Ảnh nền riêng của Trung tâm điều khiển | Có |
| File `weather_places.json` | `noBackupFilesDir/` | Địa điểm theo dõi (tối đa 3), chế độ vị trí Tự động/Thủ công, địa điểm của từng widget | Không (tọa độ là dữ liệu cá nhân) |
| File `photo_widgets.json` + ảnh | `noBackupFilesDir/` | Ảnh của từng widget ảnh | Không |
| Hệ thống | `AppCompatDelegate.setApplicationLocales` (API 33+: `LocaleManager` của hệ thống; API < 33: file riêng của AppCompat nhờ `AppLocalesMetadataHolderService` `autoStoreLocales`) | Ngôn ngữ app (màn 1.3, chip Onboarding 0.1; LG). Danh sách rỗng = Theo hệ thống | Do hệ thống |
| SharedPreferences `app_locale` (khóa `tag`) | `shared_prefs/app_locale.xml` | **Bản sao** tag ngôn ngữ đã chọn (LG Task 4), vắng = Theo hệ thống. Chỉ đọc ở API < 33 để bọc context không phải Activity (`core/locale/AppLocaleContext`); ghi đồng bộ ở `AppLanguages.apply` trước `setApplicationLocales`. Không phải nguồn chân lý: màn 1.3 đọc `AppCompatDelegate.getApplicationLocales()` | Có |

**Không phải config** (xóa đi không mất lựa chọn nào): `weather.json` (cache thời tiết), `search_recent_apps.txt` (lịch sử tìm kiếm), cache icon và cache nhãn app.

## 3. DataStore `launcher_settings`

Khóa **đậm** là khóa mới từ CF. Màn theo số màn Figma v3 (`Figma-Draft.md` mục 3).

### 3.1 `AppearanceConfig` — `AppearanceRepository`

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `themeId` | **`appearance_theme_id`** | `ThemeId?` (LIQUID_GLASS_27, CLASSIC_26, MINIMAL, DARK_PRO) | null | 1.1, 0.2 | ghi | `ObserveThemeStateUseCase` (chưa có màn; debug `CF_APPLY_THEME`) |
| `wallpaperKind` | **`appearance_wallpaper_kind`** | `WallpaperKind` (AUTO, PHOTO, COLOR, PRESET) | AUTO (ảnh đã chọn nếu có, không thì ảnh mặc định). PRESET = ảnh cài sẵn `wallpaperPreset` (WI) | 2.2, 0.3 | giữ (WI) | Nền Home |
| `wallpaperColor` | **`appearance_wallpaper_color`** | Int ARGB | `0xFF000000` | 2.2, 0.3 | giữ (WI) | Nền Home |
| `wallpaperPreset` | **`appearance_wallpaper_preset`** | String? (id trong `WallpaperPresets`: `wi_dawn`, `wi_rose`, `wi_ocean`, `wi_copper`, `wi_sand`) | null | 2.2, 0.3 | giữ | Nền Home khi `wallpaperKind` = PRESET (WI). Id lạ = như AUTO |
| `iconStyle` | `icon_style` | `IconStyle` (DEFAULT, TINTED, THREE_D, GLASS, **TRANSPARENT**) | DEFAULT | 2.3 | ghi | Icon (TRANSPARENT: chỉ vẽ hình, không ô nền; icon không tách được hình vẽ như DEFAULT) |
| `iconTint` | `icon_tint` | `IconTint` (BLUE, PINK, GREEN, ORANGE, GRAPHITE) | BLUE | 2.3 | ghi | Icon |
| `iconBevel` | **`appearance_icon_bevel`** | Boolean | false | — (chỉ theme ghi) | ghi | Icon: lớp nổi 3D chồng lên `iconStyle` (THREE_D luôn nổi) |
| `iconPack` | `icon_pack` | String? (package) | null | 2.3 | **không** | Icon |
| `iconShape` | **`appearance_icon_shape`** | `IconShapeKind` (SQUIRCLE, CIRCLE, SQUARE, TEARDROP) | SQUIRCLE | 2.3 | ghi | — |
| `iconCorner` | **`appearance_icon_corner`** | Int 0..100 | 62 | 2.3 | ghi | — |
| `iconSize` | **`appearance_icon_size`** | Int 0..100 | 50 | 2.3 | ghi | — |
| `showLabels` | `show_labels` | Boolean | true | 1.2 | ghi | Lưới Home |
| `glassLevel` | `glass_level_override` | `GlassLevelOverride` (AUTO, FORCE_LOW, FORCE_HIGH, CUSTOM) | AUTO | 2.1 | ghi | Kính |
| `glassCustom` | `glass_custom_dock`, `glass_custom_panels`, `glass_custom_search_bar`, `glass_custom_pill`, `glass_custom_icon_menu` | `GlassCustom` (5 Boolean) | false hết | 2.1 | **không** | Kính (chỉ khi `glassLevel` = CUSTOM) |
| `glassLook` | `glass_look_refraction`, `glass_look_gloss`, `glass_look_blur`, `glass_look_corner` | `GlassLook` (4 Int 0..100) | 0 / 50 / 50 / 50 | 2.1 | ghi | Kính, bo góc bề mặt v3 |
| `glassTransparency` | **`appearance_glass_transparency`** | Int 0..100 | 70 | 2.1 | ghi | — |
| `glassTint` | **`appearance_glass_tint`** | Int ARGB, 0 = không tô | 0 | 2.1 | ghi | — |
| `surfaceTone` | **`appearance_surface_tone`** | `SurfaceTone` (LIGHT, DARK) | LIGHT | (theme) | ghi | — |

`glassCustomSaved` (đọc kèm): true khi có ít nhất một khóa `glass_custom_*` trong file. Chuyển sang mức CUSTOM lần đầu (chưa lưu) thì gieo công tắc theo mức cũ (GS D8).

### 3.2 `GeneralConfig` — `GeneralConfigRepository`

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `gridPreset` | `grid_preset` | `GridPreset` (P4X5, P4X6, P5X6) | P4X6 | 1.2 | không | Lưới Home |
| `navBarHidden` | **`general_nav_bar_hidden`** | Boolean | false | 1.2 | không | Home ẩn thanh điều hướng (PB, 2026-11-18) |
| `autoArrange` | **`general_auto_arrange`** | Boolean | true | 1.2 | không | — |
| `blurEffect` | **`general_blur_effect`** | Boolean | true | 1.2 | không | — (trùng nghĩa với mức kính, map khi làm màn 1.2) |
| `textFollowSystem` | **`general_text_follow_system`** | Boolean | true | 1.2 | không | — |
| `textScale` | **`general_text_scale`** | Int % 80..130 | 100 | 1.2 | không | — |
| `hiddenAppsLock` | **`apps_hidden_lock`** | Boolean | false | 5.1 | không | — |

### 3.3 `HomeScreenConfig` — `HomeScreenConfigRepository`

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `pageDots` | `home_toggle_page_dots` | Boolean | true | 3.1 | không | Chấm trang |
| `swipeDownSearch` | `home_toggle_swipe_down_search` | Boolean | true | 3.3 | không | Cử chỉ Home |
| `searchPill` | `home_toggle_search_pill` | Boolean | true | 3.3 | không | Nút tìm kiếm trên dock |
| `pageTransition` | **`home_page_transition`** | `PageTransition` (DEFAULT, CUBE, STACK, FLIP, ZOOM, ROTATE) | DEFAULT | 3.1 | không | — |
| `libraryEnabled` | **`home_library_enabled`** | Boolean | true | 3.2 | không | Home: mở App Library (LO) |
| `libraryStyle` | **`home_library_style`** | `LibraryStyle` (FOLDERS, ALPHABETICAL) | FOLDERS | 3.2 | không | App Library: thư mục / A–Z (LO) |
| `libraryAutoCategorize` | **`home_library_auto_categorize`** | Boolean | true | 3.2 | không | — (LO: công tắc tạm ẩn, Home bỏ qua) |
| `librarySuggestions` | **`home_library_suggestions`** | Boolean | false | 3.2 | không | — (LO: công tắc tạm ẩn, Home bỏ qua) |
| `newAppPlacement` | **`home_new_app_placement`** | `NewAppPlacement` (HOME, LIBRARY_ONLY) | HOME | 3.2 | không | — |
| `badgesEnabled` | **`home_badges_enabled`** | Boolean | false | 3.4 | không | Huy hiệu icon Home |
| `badgeStyle` | **`home_badge_style`** | `BadgeStyle` (NUMBER, DOT) | NUMBER | 3.4 | không | Huy hiệu icon Home |
| `badgeDisabledApps` | **`home_badge_disabled_apps`** | Set\<String\> (`gói#serialUser`) | rỗng | 3.4 | không | Huy hiệu icon Home |
| `todayEnabled` | **`home_today_enabled`** | Boolean | true | 3.1 | không | TV: Home mở màn widget (Today) khi vuốt phải ở trang 1; tắt thì đóng màn đang mở |

### 3.4 `SearchConfig` — `SearchConfigRepository`

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `systemSettings` | `search_system_settings` | Boolean | true | 3.3 | không | Tìm kiếm |
| `shortcuts` | `search_shortcuts` | Boolean | true | 3.3 | không | Tìm kiếm |
| `suggestions` | `search_suggestions` | Boolean | true | 3.3 | không | Tìm kiếm (tắt thì xóa lịch sử) |
| `contacts` | `search_contacts` | Boolean | true | 3.3 | không | Tìm kiếm |
| `files` | **`search_files`** | Boolean | false | 3.3 | không | — |
| `webSuggestions` | **`search_web_suggestions`** | Boolean | false | 3.3 | không | — |
| `webEngine` | **`search_web_engine`** | String? (package), null = mặc định hệ thống | null | 3.3 | không | — |

Trạng thái (không phải lựa chọn): `search_contacts_asked` (Boolean, false: đã từng hỏi quyền Danh bạ). Khóa cũ chỉ để dọn: `search_recent_apps` (bản đầu lưu lịch sử ở DataStore; giờ ở file noBackup).

### 3.5 `MotionConfig` — `MotionConfigRepository`

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `transitionStyle` | **`motion_transition_style`** | `TransitionStyle` (SMOOTH, FADE, SLIDE, ZOOM, NONE) | SMOOTH | 4.1 | không | `TransitionMotion` (TR) |
| `transitionSpeed` | **`motion_transition_speed`** | `MotionSpeed` (SLOW, MEDIUM, FAST) | MEDIUM | 4.1 | không | `TransitionMotion` (TR) |
| `reduceMotion` | **`motion_reduce`** | Boolean | false | 4.1 | không | `TransitionMotion` (TR) |
| `appOpenEffect` | **`motion_app_open_effect`** | `AppOpenEffect` (ZOOM_FROM_ICON, FADE, SLIDE_UP, BOUNCE) | ZOOM_FROM_ICON | 4.2 | không | — |
| `appOpenSpeed` | **`motion_app_open_speed`** | Int 0..100 | 55 | 4.2 | không | — |
| `pressShrink` | **`motion_press_shrink`** | Boolean | false | 4.2 | không | — |
| `hapticOnOpen` | **`motion_haptic_on_open`** | Boolean | false | 4.2 | không | — |

### 3.6 Thời tiết — `WeatherPreferencesRepository` (giữ nguyên)

| Trường | Khóa | Kiểu | Mặc định | Màn | Theme | Ai đọc hôm nay |
|---|---|---|---|---|---|---|
| `unit` | `weather_temperature_unit` | `TemperatureUnit` (CELSIUS, FAHRENHEIT) | CELSIUS | 1.4 | không | Widget, sheet, thông báo |
| thông báo hằng ngày | `weather_notify_daily` | Boolean | false | 1.4 | không | Lịch thông báo |
| thông báo ngày mai | `weather_notify_tomorrow` | Boolean | false | 1.4 | không | Lịch thông báo |

Trạng thái: `weather_notify_shown_daily`, `weather_notify_shown_tomorrow` (String `yyyy-MM-dd` của lần đăng gần nhất).

### 3.7 `AppState` — `AppStateRepository` (cờ nội bộ)

| Trường | Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|---|
| `onboardingDone` | `first_run_done` | Boolean | false | Đã xong (hoặc bỏ qua) onboarding. Ghi: `OnboardingViewModel` (Bỏ qua / Về màn hình chính); đọc: `HomeActivity` lần dựng đầu (OB) |
| `todaySeeded` | `today_seeded` | Boolean | false | TV (bản Compose cũ): đã tạo widget mặc định cho màn Today (hàng Room `container = TODAY`). Từ 2026-11-21 không ai ghi: chỉ còn là đầu vào một lần của `LayoutReconciler.migrateLegacyToday` (cờ bật mà không còn hàng cũ = người dùng đã gỡ hết, ghi danh sách rỗng vào `today_widgets`). Khóa `default_launcher_prompt_shown` đã bỏ (REF cb07222) |

## 4. DataStore `control_center` — `ControlCenterPrefsRepository` (G3d, giữ nguyên)

| Trường | Khóa | Kiểu | Mặc định |
|---|---|---|---|
| `enabled` | `enabled` | Boolean | false |
| `edge.position` | `edge_position` | `EdgePosition` | TOP_RIGHT |
| `edge.lengthFraction` | `edge_length` | Float 0.15..0.6 | 0.35 |
| `edge.thicknessDp` | `edge_thickness_dp` | Int 12..40 | 22 |
| `edge.offsetFraction` | `edge_offset` | Float | 0.1 |
| `edge.color` | `edge_color` | Int ARGB | `0x59FFFFFF` |
| `background` | `background` | `BackgroundMode` | NONE (CG 2026-11-20; trước là WALLPAPER) |
| `blurBackground` | `blur` | Boolean | true |
| `vibrateOnOpen` | `vibrate` | Boolean | true |
| `tiles` | `tiles` | chuỗi `b:<TYPE>;a:<pkg>/<cls>/<user>;…` | `DEFAULT_TILES` |
| `recorderPackage` | `recorder_package` | String (rỗng = chưa chọn) | rỗng |
| `nightShift.enabled` | `night_shift` | Boolean | false |
| `nightShift.warmth` | `night_shift_warmth` | Float 0..1 | 0.5 |
| `nightShift.scheduled` | `night_shift_scheduled` | Boolean | false |
| `nightShift.fromMinutes` | `night_shift_from` | Int phút trong ngày | 1320 (22:00) |
| `nightShift.toMinutes` | `night_shift_to` | Int phút trong ngày | 420 (07:00) |

Kho này ghi cả 16 khóa mỗi lần `update` (khác quy tắc "chỉ ghi khóa đổi" của `launcher_settings`; không ảnh hưởng vì không có ý nghĩa "khóa vắng").

## 4b. DataStore `today_widgets` — `TodayWidgetsRepository` (TV-3, REF 3ab9c20; bản Compose 2026-11-21)

| Trường | Khóa | Kiểu | Mặc định |
|---|---|---|---|
| danh sách widget theo thứ tự hiện | `widgets` | chuỗi `<id>|<kind>|<w hoặc n>;…` (`kind` = `WidgetKind.id` hoặc `recents`; `w` = 4 × 2, `n` = 2 × 2) | khóa vắng = `TodayWidget.DEFAULT` (app gần đây 4 × 2, Thời tiết 2 × 2, Lịch 2 × 2, Đồng hồ 4 × 2); chuỗi rỗng = không còn widget nào |
| id cấp cho widget thêm mới kế tiếp | `next_id` | Long | `TodayWidget.FIRST_FREE_ID` (2^40 + 100) |

Id của widget Today nằm trong dải >= 2^40 (`TodayWidget.ID_BASE`), không có hàng `launcher_items`. Địa điểm thời tiết (`weather_places.json`) và ảnh (`photo_widgets.json`) của chúng đánh theo id này như widget trên Home; quét mồ côi của widget Ảnh bỏ qua dải này, xóa widget trên Today thì `RemoveTodayWidgetUseCase` dọn.

Chuyển dữ liệu (bản Compose 2026-11-21): bản Compose trước lưu Today thành hàng Room `container = -102`. `LayoutReconciler.migrateLegacyToday` (mỗi lần nạp bố cục, idempotent) chép widget tự vẽ của các hàng đó (theo `rank`, rộng = spanX >= 4) vào `widgets` nếu khóa còn vắng, rồi xóa các hàng. Mất: widget app trên Today cũ (id host được giải phóng; REF không cho widget app ở Today) và địa điểm thời tiết / ảnh riêng của widget Today cũ (khóa theo id hàng cũ).

## 5. Room `app_overrides` (từ Room v2, CF)

Một hàng cho mỗi app (khóa `packageName` + `className` + `userSerial`) có ít nhất một tùy chỉnh:

| Cột | Kiểu | Mặc định | Màn |
|---|---|---|---|
| `hidden` | Boolean | false | 5.1 (ẩn từ menu nhấn giữ icon) |
| `customLabel` | String? | null | 5.3 |
| `customIconPath` | String? (file trong `files/custom_icons/`) | null | 5.2 (chưa làm) |

Gỡ app thật hoặc xóa hồ sơ công việc → xóa cả hàng. Chi tiết: [room-schema.md](room-schema.md) mục 4.

## 6. Preset theme

Theme là một `AppearanceConfig` cài sẵn trong code (`ThemePresets`). Áp theme = một `update` chép mọi trường cột "Theme = ghi" của preset và đặt `themeId`; `iconPack`, `glassCustom` giữ nguyên. Thẻ theme hiện "đã chỉnh" khi config hiện tại khác preset của `themeId`. Giá trị (chốt 2026-11-17; Classic 26, Minimal, Dark Pro chỉnh theo ảnh xem trước màn 0.2, Figma `302:3704`):

WI (2026-11-18): áp theme **giữ nguyên** `wallpaperKind`, `wallpaperColor`, `wallpaperPreset` (hình nền là lựa chọn riêng; chọn nền không làm theme thành "đã chỉnh"). Hàng nền màu bên dưới chỉ còn là giá trị tham khảo trong `ThemePresets`.

| Trường | LIQUID_GLASS_27 | CLASSIC_26 | MINIMAL | DARK_PRO |
|---|---|---|---|---|
| `wallpaperKind` / `wallpaperColor` (không chép, WI) | COLOR `#FF1E3A8A` | COLOR `#FF27142C` | COLOR `#FF11141B` | COLOR `#FF000000` |
| `iconStyle` / `iconTint` | GLASS / BLUE | DEFAULT / BLUE | TRANSPARENT / BLUE | TINTED / GRAPHITE |
| `iconBevel` | true | false | false | true |
| `iconShape` / `iconCorner` / `iconSize` | SQUIRCLE / 62 / 50 | SQUIRCLE / 62 / 50 | SQUARE / 30 / 50 | CIRCLE / 100 / 50 |
| `showLabels` | true | true | false | true |
| `glassLevel` | AUTO | FORCE_LOW | FORCE_LOW | AUTO |
| `glassLook` (khúc xạ / bóng / mờ / bo góc) | 40 / 60 / 55 / 50 | 0 / 30 / 50 / 50 | 0 / 20 / 40 / 30 | 20 / 40 / 60 / 50 |
| `glassTransparency` / `glassTint` / `surfaceTone` | 70 / 0 / LIGHT | 80 / 0 / LIGHT | 90 / 0 / LIGHT | 50 / 0 / DARK |
