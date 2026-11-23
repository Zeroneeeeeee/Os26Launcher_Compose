# RS — Đổi giao diện Cài đặt sang v3 (DS3) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm tay trên Vsmart Live 4 (không đổi kính Home, không cần Oppo). Lệnh viết cho PowerShell.

**Bắt đầu:** 2026-11-17 · **Plan duyệt:** 2026-11-17
**Trạng thái:** Xong 2026-11-18 (Task 1–8, đã commit). Việc tiếp theo ở `plans/HC-hero-card-entry.md`
**Máy:** Live 4 · **Đổi schema Room:** không · **Khóa config mới:** không · **Dependency mới:** không

**Goal:** Các màn Cài đặt ĐÃ CÓ trông như Figma v3 (nền aurora tối, thanh điều hướng v3, hàng trong card kính, ô icon màu ở trang chủ, hộp thoại/bottom sheet v3) mà không thêm hay bớt tính năng nào.

**Architecture:** Mọi màn con của Cài đặt đi qua một khung (`SettingsActivity`) và một `SettingsAdapter` với 6 loại hàng (`SettingsRow`). Đổi khung sang theme DS3 + `DsNavBar`, đổi 6 layout hàng sang `DsRowView`/`DsSliderRow`, vẽ card kính cho mỗi nhóm bằng một `ItemDecoration` (một bề mặt liền cho cả nhóm, không ghép từng hàng), thêm ô icon cho hàng trang chủ. Hộp thoại đi qua hai hàm dùng chung (`singleChoice`, `confirm`) dựng trên `DsDialog`. Hai Activity riêng có trong Figma (Ứng dụng ẩn 5.1, chỉnh thông số kính của 2.1) chỉ đổi khung.

**Tech Stack:** Kotlin, Views/XML + ViewBinding, `core/designsystem` (DS3). Không dependency mới.

**Spec:** Thảo luận chủ dự án 2026-11-17: "chỉ nối các chức năng có sẵn với giao diện, thay layout item, switch, các component", Cài đặt **luôn tối**, phạm vi **chỉ màn có trong Figma v3**; lưới và Trung tâm điều khiển làm sau. Figma v3 trang "v3 · Thiết kế mới": Home cài đặt `302:1492`, 1.2 `302:1800`, 1.4 `302:1900`, 1.5 `302:1971`, 2.1 `302:2020`, 2.2 `302:3232` (popup `302:2090`), 2.3 `302:2172`, 3.1 `302:2267`, 3.3 `302:2414`, 5.1 `302:2648`, 5.3 `302:2776`, 6.1 `302:2958`, 6.3 `302:2899`. `docs/plans/DS3-design-system-v3.md` (thành phần), `docs/Figma-Draft.md` mục 3.

## Global Constraints

- Không thêm, bớt, đổi hành vi tính năng nào: cùng hàng, cùng thứ tự, cùng id hàng (`SettingsRow.id`), cùng ghi config. Hàng của màn chưa làm vẫn mờ "Sắp có" như hiện tại.
- Cài đặt luôn tối: theme `Theme.Os26Launcher.Ds` (DS3 quyết định 6), không có bản sáng.
- Chỉ dùng thành phần và token DS3 (`Ds*`, `ds_*`); không viết bề mặt kính mới.
- Không đụng: màn lưới (Chung → cỡ lưới vẫn là hộp thoại chọn, chỉ đổi kiểu hộp thoại), 4 màn Trung tâm điều khiển (`ui/controlcenter/*Activity`), bảng Thời tiết trên Home (`WeatherSheetController`), màn ảnh widget.
- Hàng "Trung tâm điều khiển" trên trang chủ (không có trong Figma) giữ nguyên chức năng, đổi kiểu như hàng khác.
- Xóa file (layout/drawable cũ không còn dùng) phải liệt kê và chờ chủ dự án duyệt.

## Đọc Figma (2026-11-17)

Trang chủ `302:1492`: nền `#07080E` + 4 đốm aurora; tiêu đề trang 20 Bold; nhóm = tiêu đề nhóm 14 SemiBold `ds_text_secondary` cách card 12, các nhóm cách nhau 20; card kính bo 20, hàng cao 56, đệm trái 12, ô icon 32 bo 8, chữ 14, giá trị 13 `ds_text_secondary`, chevron 24, đường kẻ gradient từ mép chữ. Card "Launcher hiện tại" bo 24 ở đầu (ảnh xem trước theme, "Màn hình chính đang chọn", tên theme, badge cảnh báo "Chưa đặt làm mặc định", nút "Đặt làm mặc định").

Ô icon trang chủ (đọc fill của từng "Icon tile"):

| Nhóm | Hàng | Màu | Icon DS3 |
|---|---|---|---|
| Chung | Màn hình chính mặc định (1.1) | BLUE | `ds_ic_home` |
| | Chung | SLATE | `ds_ic_general` |
| | Ngôn ngữ | SKY | `ds_ic_language` |
| | Thời tiết | AMBER | `ds_ic_weather` |
| | Vị trí | GREEN | `ds_ic_location` |
| | Trung tâm điều khiển (ngoài Figma) | TEAL | `ds_ic_page_scroll` (Claude chọn, Q2) |
| Giao diện | Liquid Glass | VIOLET | `ds_ic_liquid_glass` |
| | Hình nền | PINK | `ds_ic_wallpaper` |
| | Bộ biểu tượng | ORANGE | `ds_ic_icon_pack` |
| Màn hình nền | Lướt màn hình | TEAL | `ds_ic_page_scroll` |
| | Thư viện ứng dụng | BLUE | `ds_ic_app_library` |
| | Cài đặt tìm kiếm | SLATE | `ds_ic_search` |
| | Huy hiệu thông báo | RED | `ds_ic_badge` |
| Chuyển cảnh | Hiệu ứng chuyển cảnh | VIOLET | `ds_ic_transition` |
| | Hiệu ứng ứng dụng | AMBER | `ds_ic_app_animation` |
| Tùy chỉnh biểu tượng | Ứng dụng ẩn | SLATE | `ds_ic_hidden_apps` |
| | Thay đổi biểu tượng | PINK | `ds_ic_change_icon` |
| | Thay đổi tên ứng dụng | ORANGE | `ds_ic_rename` |
| Liên hệ | Đánh giá ứng dụng | AMBER | `ds_ic_rate` |
| | Tải ứng dụng khác | BLUE | `ds_ic_more_apps` |
| | Chính sách bảo mật | GREEN | `ds_ic_privacy` |

Màn con dùng cùng kiểu card/hàng (không có ô icon), hộp thoại v3 `312:2691`, bottom sheet 2.2 popup `302:2090`.

## Vì sao chọn thiết kế này

**Hiện trạng (đọc code `main` @ `164cdc5`):** `SettingsActivity` là khung chung (`MaterialToolbar`, `RecyclerView`, theme DayNight); 14 màn con chỉ trả danh sách `SettingsRow`; `SettingsAdapter` có 6 holder (Header, Value, Switch, Slider, AppName, Footer), mỗi hàng tự đặt nền nhóm bằng `groupBackground(first, last)` (drawable bo góc từng đoạn); khoảng 26 chỗ dùng `MaterialAlertDialogBuilder`, phần lớn là `setSingleChoiceItems` hoặc hộp xác nhận; 5.1 là `AppLibraryActivity`, 2.1 mở `GlassTuningActivity`.

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| **A. Đổi khung + holder + decoration vẽ card** | Holder dùng `DsRowView`; `ItemDecoration` vẽ một `DsSurfaceDrawable` cho cả nhóm | Card kính liền một khối như Figma (gradient, bóng không bị cắt theo hàng); màn con không sửa | Gợn nhấn của hàng đầu/cuối có thể lấn góc bo (xử lý ở Task 1) | **Chọn** |
| B. Nền từng hàng (giữ `groupBackground`, đổi drawable) | Mỗi hàng một đoạn kính | Ít đổi | Gradient/bóng kính vẽ lặp từng hàng, lộ vết nối | Không |
| C. Bỏ RecyclerView, mỗi nhóm một `DsCardGroup` | Dựng View theo nhóm | Đúng component DS3 | Viết lại khung + 14 màn, mất DiffUtil, rủi ro hồi quy lớn | Không |

## Quyết định đã chốt

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Cài đặt luôn tối (`Theme.Os26Launcher.Ds`) | Chủ dự án 2026-11-17 |
| 2 | Phạm vi: các màn có trong Figma v3; không làm lưới, Trung tâm điều khiển | Chủ dự án 2026-11-17 |
| 3 | Chỉ đổi giao diện của tính năng đã có; không thêm hàng/màn | Chủ dự án 2026-11-17 |
| 4 | Card nhóm vẽ bằng `ItemDecoration` (phương án A) | Claude |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Hàng "Launcher mặc định" (Có/Không) hiện ở đầu trang chủ: đổi sang card "Launcher hiện tại" của Figma? | Có: ảnh xem trước + tên theme đang áp (có sẵn từ OB: `ThemeCatalog`; chưa áp theme nào thì ẩn ảnh, ghi "Tùy chỉnh"), badge cảnh báo "Chưa đặt làm mặc định" + nút "Đặt làm mặc định" chỉ hiện khi chưa mặc định (nút gọi `DefaultLauncherAction` có sẵn). Chạm ảnh/tên không làm gì (1.1 chưa có) | Đồng ý, đổi theo Figma |
| Q2 | Hàng "Trung tâm điều khiển" (ngoài Figma) dùng ô icon nào? | TEAL + `ds_ic_page_scroll` (chưa có icon riêng; vẽ icon riêng làm cùng CC sau) | Đồng ý, icon tạm, đổi sau |
| Q3 | Hộp thoại chọn một trong nhiều (cỡ lưới, kiểu icon, màu tô, mức kính, °C/°F, chế độ vị trí): giữ là hộp thoại hay đổi sang bottom sheet? | Hộp thoại `DsDialog` với hàng radio (`DsRowView` RADIO), chạm là chọn và đóng (như hiện tại). Riêng "đặt hình nền cho Home/Khóa/Cả hai" dùng `DsBottomSheet` vì Figma vẽ popup 2.2 là sheet | Đổi theo thiết kế từng màn; chỉ giữ hộp thoại khi Figma không vẽ hoặc là trường hợp đặc biệt (Task 3 đọc lại Figma từng màn) |

## Ngoài phạm vi / ghi để sau

- Màn chưa có tính năng (1.1, 1.3, 3.2, 3.4, 4.1, 4.2, 5.2) và các hàng thiết kế chưa có chức năng (xem trước kính 2.1, thanh trượt kính, danh mục ảnh nền 2.2, hình dạng/cỡ icon 2.3, thẻ thời tiết 1.4, công tắc từng app ở 5.1).
- Màn lưới, 4 màn Trung tâm điều khiển, bảng Thời tiết trên Home, màn ảnh widget.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `AndroidManifest.xml` (theme `SettingsActivity`, `AppLibraryActivity`, `GlassTuningActivity`) | Sửa | 1, 4 |
| `res/layout/activity_settings.xml`, `ui/settings/SettingsActivity.kt` | Sửa | 1 |
| `res/layout/item_settings_{header,value,switch,slider,footer,app_name}.xml`, `ui/settings/SettingsAdapter.kt`, `ui/settings/SettingsRow.kt` | Sửa | 1 |
| `ui/settings/SettingsCardDecoration.kt` | Tạo | 1 |
| `core/designsystem/DsSliderRow.kt` (thêm báo nhả tay) | Sửa | 1 |
| `ui/settings/SettingsHubScreen.kt` (ô icon, card launcher), `res/layout/item_settings_launcher_card.xml` | Sửa / Tạo | 2 |
| `ui/settings/SettingsDialogs.kt` | Tạo | 3 |
| `GeneralSettingsScreen`, `IconSetSettingsScreen`, `LiquidGlassSettingsScreen`, `LocationSettingsScreen`, `WeatherSettingsScreen`, `SearchSettingsBlock`, `WallpaperSettingsScreen`, `RateAction`, `RenameAppsSettingsScreen`, `ui/weather/CitySearchDialog.kt`, layout `dialog_rate`, `dialog_rename_app`, `dialog_city_search` | Sửa | 3 |
| `ui/library/AppLibraryActivity.kt` + `activity_app_library.xml`, `item_app_library_app.xml`; `ui/settings/GlassTuningActivity.kt` + `activity_glass_tuning.xml` | Sửa | 4 |
| `docs/Figma-Draft.md`, `docs/project_structure.md`, `docs/lessons/*` | Sửa | 5 |

## Review Focus

1. **Cỡ chữ hệ thống lớn nhất:** hàng giãn cao, giá trị dài cắt "…" (đã có ở `DsRowView`), card vẫn bao đủ hàng, tiêu đề thanh điều hướng không đè nút quay lại. Kiểm ở Task 1 và Task 5.
2. **Công tắc:** chạm hàng hoặc chạm công tắc đều đổi đúng một lần và ghi config; bind lại (cuộn, dựng lại danh sách) không tự gọi ghi. Kiểm ở Task 1.
3. **Thanh trượt:** chỉ ghi khi nhả tay (hành vi GT hiện tại), kéo không giật vì danh sách dựng lại. Kiểm ở Task 1 (màn 2.1 → Chỉnh thông số kính nếu có hàng trượt; Thời tiết nếu có).
4. **Danh sách dài + card:** màn Đổi tên ứng dụng (100+ app) cuộn mượt, card không lệch khi hàng tái dùng; hàng mờ "Sắp có" vẫn không bấm được. Kiểm ở Task 1.
5. **Hộp thoại sống qua dựng lại Activity / xoay / đóng màn:** không rò cửa sổ (`onDestroy` vẫn dismiss), chọn trong hộp thoại ghi đúng như trước. Kiểm ở Task 3.

---

### Task 1: Khung Cài đặt, 6 loại hàng, card nhóm

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces:
  - `data class SettingsTile(@DrawableRes val icon: Int, val color: DsTileColor)` trong `SettingsRow.kt`; `SettingsRow.Value` và `SettingsRow.Switch` thêm `val tile: SettingsTile? = null` (mặc định null: màn con không đổi)
  - `class SettingsCardDecoration(context: Context) : RecyclerView.ItemDecoration` — vẽ một `DsSurfaceDrawable(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))` sau mỗi dãy hàng liền nhau nằm giữa hai `Header`/`Footer`; khoảng cách: tiêu đề nhóm → card 12 dp, card → tiêu đề nhóm sau 20 dp, lề ngang 16 dp
  - `DsSliderRow.setOnTrackingChangeListener(listener: ((tracking: Boolean) -> Unit)?)` (bắt đầu kéo `true`, nhả `false`)
  - `SettingsAdapter.rowAt(position): SettingsRow?`; `SettingsCardDecoration.refresh()` (gọi khi cấu hình kính đổi)
- Consumes: `DsNavBar`, `DsRowView` (`DsRowType.VALUE/CHEVRON/TOGGLE`, `showDivider`, `setTile`, `setOnCheckedChangeListener`), `DsSliderRow`, `AuroraBackgroundDrawable` (DS3/OB).

- [x] **Step 1:** Manifest: `SettingsActivity` dùng `@style/Theme.Os26Launcher.Ds`. `activity_settings.xml`: `MaterialToolbar` → `DsNavBar` (`app:dsTitle`, nút quay lại gọi `finish()`; trang chủ: tiêu đề `settings_title`, không có nút quay lại như Figma); nền cửa sổ `AuroraBackgroundDrawable(this)`; `RecyclerView` đệm ngang 0 (lề do decoration), đệm trên 8, đệm dưới 32 (`ds_screen_content_*`); vòng chờ `CircularProgressIndicator` giữ, tô `ds_accent`. `SettingsActivity`: `WindowCompat.setDecorFitsSystemWindows(false)` + inset như `OnboardingActivity`.
- [x] **Step 2:** Layout hàng: `item_settings_value.xml` và `item_settings_switch.xml` = một `DsRowView` (lề ngang 16 qua decoration); `item_settings_header.xml` = `TextView` `TextAppearance.Os26.Ds.SectionHeader`, đệm ngang 16 + 4; `item_settings_footer.xml` = `TextAppearance.Os26.Ds.Caption` `ds_text_tertiary` căn giữa; `item_settings_slider.xml` = `DsSliderRow` (+ mô tả `Caption`); `item_settings_app_name.xml` giữ cấu trúc (icon app 32, tên, dòng phụ, chevron), đổi sang chữ/màu `ds_*`, đường kẻ `DsDividerDrawable`, nền trong suốt.
- [x] **Step 3:** `SettingsAdapter`: holder Value → `type = VALUE` khi `value` khác rỗng, không thì `CHEVRON`; `title`/`titleText`; `setTile(tile?.icon, tile?.color)` (null ẩn ô); mờ 40% + không bấm khi `enabled = false` (giữ). Holder Switch → `type = TOGGLE`, `isChecked = row.checked` (không gọi listener), `setOnCheckedChangeListener { onSwitchChanged(row, it) }`. Mọi holder: `showDivider = !last`, bỏ `groupBackground`. Slider → `DsSliderRow`, ghi qua `setOnTrackingStoppedListener`.
- [x] **Step 4:** `SettingsCardDecoration` (đăng ký trong `SettingsActivity`): `getItemOffsets` đặt lề 16 hai bên cho hàng không phải Header/Footer, khoảng cách nhóm theo Interfaces; `onDraw` gom các hàng liên tiếp đang hiện cùng nhóm, vẽ một card bao hợp các bound (kể cả `translationY` khi hoạt ảnh). Hàng đầu/cuối nhóm: gợn nhấn bo theo card (`outlineProvider` bo góc trên/dưới bằng bán kính card, `clipToOutline`).
- [x] **Step 5:** `DsSliderRow`: thêm `setOnTrackingStoppedListener`, gọi ở `onStopTrackingTouch` với giá trị hiện tại.
- [x] **Step 6:** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`.
- [x] **Step 7: Kiểm (Live 4).** Mở từng màn con đã có (Chung, Thời tiết, Vị trí, Liquid Glass, Hình nền, Bộ biểu tượng, Lướt màn hình, Tìm kiếm, Đổi tên, Ứng dụng khác, Chính sách): (a) nền aurora tối, thanh điều hướng v3, card kính liền khối bo 20, đường kẻ giữa hàng, hàng cuối không kẻ; (b) công tắc: chạm hàng và chạm công tắc đều đổi một lần, config đổi đúng (Review Focus 2); (c) thanh trượt ghi khi nhả (Review Focus 3); (d) cỡ chữ lớn nhất (Review Focus 1); (e) Đổi tên: cuộn 100+ app mượt, card không lệch (Review Focus 4); (f) máy ở chế độ sáng: Cài đặt vẫn tối, icon thanh trạng thái trắng.
- [x] **Step 8:** Liệt kê drawable/layout cũ không còn dùng (vd nền nhóm `bg_settings_group_*`) cho chủ dự án duyệt xóa. Commit: Task 1–4 gộp một commit `feat: reskin settings with the v3 design system` (chủ dự án, 2026-11-18).

### Task 2: Trang chủ Cài đặt (ô icon, card Launcher hiện tại)

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces: `HubEntry` thêm `val tile: SettingsTile`; `SettingsRow.LauncherCard(id, @DrawableRes preview: Int?, themeName: String, isDefault: Boolean)` + holder `item_settings_launcher_card.xml` (nếu Q1 = có).
- Consumes: `SettingsTile` (Task 1), `ThemeCatalog` (OB), `AppearanceRepository.config.themeId`, `DefaultLauncherAction.request()`.

- [x] **Step 1:** `SettingsHubLayout`: gán `tile` cho từng `HubEntry` theo bảng "Ô icon trang chủ"; `SettingsHubScreen.rows` truyền `tile` vào `SettingsRow.Value`.
- [x] **Step 2 (Q1):** Thay hàng "Launcher mặc định" bằng `LauncherCard`: card bo 24 (`DsCornerRole.CARD_LARGE`), đệm 12, ảnh xem trước 93×116 bo 12 (ẩn khi chưa áp theme), "Màn hình chính đang chọn" `Caption` `ds_text_secondary`, tên theme `TextAppearance.Os26.Ds.Card` (16 Bold; chưa áp theme: "Tùy chỉnh"), `DsBadgeView` WARNING "Chưa đặt làm mặc định" + `DsButton` M "Đặt làm mặc định" chỉ hiện khi chưa mặc định. Nút gọi `ctx.defaultLauncher.request()`; trạng thái đọc lại ở `onResume` như hàng cũ.
- [x] **Step 3:** Chân trang "Phiên bản 1.0.0": `Caption` căn giữa (Task 1 holder).
- [x] **Step 4:** Build + kiểm: so trang chủ với `302:1492` (ô icon đúng màu/icon từng hàng, hàng "Sắp có" mờ không bấm được, card launcher hiện/ẩn badge + nút theo trạng thái, bấm nút mở hộp thoại hệ thống, quay về cập nhật).
- [x] **Step 5:** Commit `feat: add icon tiles and the current launcher card to the settings hub`.

### Task 3: Hộp thoại và bottom sheet v3

**Bắt buộc kiểm tay:** có.

**Interfaces:**
- Produces (`ui/settings/SettingsDialogs.kt`):
  - `fun singleChoice(context: Context, title: CharSequence, labels: List<CharSequence>, checked: Int, onChoose: (Int) -> Unit): AppCompatDialog` — `DsDialog` + nội dung là các `DsRowView` RADIO, chạm hàng: `onChoose(i)` rồi đóng; nút phụ "Hủy"
  - `fun confirm(context: Context, title: CharSequence, message: CharSequence?, positive: CharSequence, negative: CharSequence, onPositive: () -> Unit): AppCompatDialog`
- Consumes: `DsDialog`, `DsBottomSheet`, `DsRowView`, `DsSearchField`.

> **Đọc lại Figma (2026-11-17, theo Q3):** 1.4 vẽ °C|°F là segmented ngay trên hàng → `SettingsRow.Segmented`; 1.5 vẽ cách xác định vị trí là hai hàng radio có mô tả → `SettingsRow.Radio`; 2.2 popup là bottom sheet → `SettingsDialogs.actionSheet`. Cỡ lưới (1.2), mức kính (2.1), kiểu icon / màu tô / gói icon (2.3) Figma không vẽ hoặc vẽ thành phần chưa có tính năng → giữ hộp chọn kiểu v3. Hộp Đổi tên: nút trung tính "Khôi phục tên gốc" thành nút phụ trong nội dung (DsDialog chỉ có hai nút).

- [x] **Step 1:** Viết `SettingsDialogs`. Trường giữ hộp thoại trong các màn đổi kiểu từ `AlertDialog?` sang `AppCompatDialog?`; giữ nguyên `setOnDismissListener` gán null và dismiss ở `onDestroy`.
- [x] **Step 2:** Đổi `setSingleChoiceItems` sang `singleChoice` ở `GeneralSettingsScreen` (cỡ lưới), `IconSetSettingsScreen` (kiểu icon, màu tô, gói icon), `LiquidGlassSettingsScreen` (mức kính), `LocationSettingsScreen` (chế độ vị trí), `WeatherSettingsScreen` (°C/°F); hộp xác nhận sang `confirm` ở `GeneralSettingsScreen` (đổi lưới), `LiquidGlassSettingsScreen` (cảnh báo kính), `WeatherSettingsScreen` (xóa địa điểm), `SearchSettingsBlock` (2 hộp).
- [x] **Step 3 (Q3):** `WallpaperSettingsScreen` hộp chọn đích đặt hình nền → `DsBottomSheet` với 3 `DsRowView` (Màn hình chính / Khóa / Cả hai) theo `302:2090`.
- [x] **Step 4:** Hộp thoại có nội dung riêng dùng `DsDialog.content(view)`: `RateAction` (`dialog_rate.xml` đổi chữ/sao sang `ds_*`), `RenameAppsSettingsScreen` (`dialog_rename_app.xml`: ô nhập nền `DsSurface.CONTROL` bo pill, chữ `Body`), `CitySearchDialog` (`dialog_city_search.xml`: `DsSearchField` + danh sách hàng `DsRowView`).
- [x] **Step 5:** Build + kiểm: mở từng hộp thoại ở trên, chọn/hủy/xác nhận ghi đúng như trước; xoay/đổi cỡ cửa sổ khi hộp thoại mở và Back khi hộp thoại mở: không crash, không rò cửa sổ (Review Focus 5).
- [x] **Step 6:** Commit `feat: move settings dialogs to the v3 dialog and bottom sheet`.

### Task 4: Ứng dụng ẩn (5.1) và Chỉnh thông số kính (2.1)

**Bắt buộc kiểm tay:** có.

- [x] **Step 1:** Manifest: `AppLibraryActivity`, `GlassTuningActivity` dùng `Theme.Os26Launcher.Ds`. Đổi `MaterialToolbar` sang `DsNavBar` (quay lại = `finish()`), nền aurora, inset như Task 1.
- [x] **Step 2:** `item_app_library_app.xml`: hàng v3 (icon app 32, tên `Body`, đường kẻ) trong card (dùng lại `SettingsCardDecoration` nếu danh sách là một nhóm; empty state `item_app_library_empty.xml` chữ `ds_text_secondary`). Hành vi bấm/bỏ ẩn giữ nguyên.
- [x] **Step 3:** `GlassTuningActivity`: thanh trượt sang `DsSliderRow` trong card; xem trước kính (`GlassPreviewView`) giữ nguyên.
- [x] **Step 4:** Build + kiểm: ẩn một app từ Home → mở Cài đặt → Ứng dụng ẩn: app có trong danh sách, bỏ ẩn hoạt động; Liquid Glass → Chỉnh thông số: kéo thanh trượt, kính xem trước đổi, ghi khi nhả.
- [x] **Step 5:** Commit `feat: reskin hidden apps and glass tuning screens`.

### Task 6: Màn 1.1 Màn hình chính mặc định (thêm 2026-11-18, làm trước Task 5)

**Bắt buộc kiểm tay:** có. **Nguồn:** chủ dự án 2026-11-18 ("nối màn 1.1, phần lớn đã xử lý trong onboarding"), Figma `302:1738`.

**Interfaces:**
- Produces: `ThemeSettingsActivity` (Activity riêng: lưới thẻ + nút cố định không hợp khung danh sách), `ThemeSettingsViewModel` (`theme: StateFlow<ThemeId>`, `choose(id)`, `apply(then)`), layout `activity_theme_settings.xml`; hàng `hub_theme` của trang chủ đổi đích sang `HubTarget.External(ThemeSettingsActivity)`.
- Consumes: `ThemeCardView`, `ThemeCatalog` (OB 0.2), `ApplyThemeUseCase` (CF), `DefaultLauncherAction`.

- [x] **Step 1:** Màn: DsNavBar "Màn hình chính mặc định", phụ đề, lưới 2 × 2 `ThemeCardView`, phần dưới cố định: `DsButton` L "Áp dụng & đặt làm mặc định" + chú thích "Máy sẽ mở hộp thoại hệ thống…". Lựa chọn đầu = theme đang áp (chưa từng chọn: Liquid Glass 27); chạm thẻ chỉ đổi lựa chọn.
- [x] **Step 2:** Nút: ghi theme (`ApplyThemeUseCase`), rồi chưa mặc định thì mở hộp thoại vai trò Home, đóng màn khi hộp thoại trả kết quả (hoặc ở `onResume` nếu đi qua màn hệ thống dự phòng và đã là mặc định). Đã mặc định: nút "Áp dụng", ẩn chú thích, ghi xong đóng màn (Claude chọn: Figma chỉ vẽ trạng thái chưa mặc định).
- [x] **Step 3: Kiểm (Live 4):** 6 bước trong báo cáo 2026-11-18, chủ dự án kiểm: đạt.
- [x] **Step 4:** Commit `feat: add the default home screen theme picker (1.1)`.

### Task 7: Card xem trước thời tiết ở màn 1.4 (thêm 2026-11-18, dữ liệu mẫu)

**Bắt buộc kiểm tay:** có. **Nguồn:** chủ dự án 2026-11-18, Figma 1.4 `302:1900`.

- Produces: `SettingsRow.WeatherPreview` (khối tự vẽ, `CardKind.PLAIN`), `WeatherPreviewMock` (Hà Nội 24°, Nhiều mây, C 27° · T 21°, 5 mốc giờ), layout `item_settings_weather_preview.xml`, nền `bg_weather_preview.xml`.
- [x] **Step 1:** Card đầu màn: tên thành phố, nhiệt độ lớn, icon + tình trạng, cao/thấp, hàng 5 mốc giờ. Dữ liệu mẫu, đổi °F theo đơn vị đang chọn (Claude chọn); tên thành phố = vị trí hiện tại (`places.current`, chủ dự án 2026-11-18), chưa có thì "Hà Nội".
- [x] **Step 2:** Thứ tự màn theo Figma: card xem trước, hàng Đơn vị nhiệt độ (card riêng, không tiêu đề), Địa điểm theo dõi, Thông báo. Decoration: card nhóm / khối PLAIN đứng sau khối PLAIN hoặc card cách 20.
- [x] **Step 3: Kiểm (Live 4):** chủ dự án kiểm 2026-11-18: đạt.

### Task 8: Ô ảnh nền giữ chỗ ở màn 2.2, dùng lại được ở 0.3 (thêm 2026-11-18)

**Bắt buộc kiểm tay:** có. **Nguồn:** chủ dự án 2026-11-18 ("hiện các placeholder giống thiết kế, sau này có ảnh thật sẽ thêm"), Figma 2.2 `302:3232`.

- Produces: `ui/wallpaper/WallpaperTileView` (ô 112:178 bo 14, giờ "9:41", badge "MỚI", viền + dấu chọn), `ui/wallpaper/WallpaperPlaceholders` (5 ô = màu cố định của `WallpaperSwatches` 0.3, bỏ ô "Theo giao diện"), `SettingsRow.WallpaperGrid(tiles, chosen)` (lưới 3 cột, `CardKind.PLAIN`), `SettingsViewModel.onWallpaperColorChosen(color)`.
- [x] **Step 1:** Màn 2.2: hàng chọn ảnh giữ nguyên, bên dưới lưới ô giữ chỗ dùng 5 màu cố định của 0.3 (bỏ "Theo giao diện": không có ảnh xem trước đúng). Chạm ô: hộp xác nhận, đồng ý thì ghi nền màu như 0.3 (`WallpaperKind.COLOR`), ô đó có viền + dấu chọn (chủ dự án 2026-11-18). Ảnh tự chọn vẫn giữ, chọn lại ở hàng chọn ảnh thì nền về ảnh. Chip danh mục và "Tự động đổi hình nền" chưa có tính năng: không làm.
- [x] **Step 2:** `WallpaperTileView` độc lập với Cài đặt để 0.3 dùng lại khi có ảnh thật (0.3 chưa đổi trong task này).
- [x] **Step 3: Kiểm (Live 4):** chủ dự án kiểm 2026-11-18: đạt.

### Task 5: Kiểm cả tính năng, chốt tài liệu

**Bắt buộc kiểm tay:** có.

- [x] **Step 1: Bài kiểm (Live 4):** (chủ dự án kiểm từng Task 1–4, 6–8 trên Live 4, 2026-11-17 đến 2026-11-18: đạt) đi hết trang chủ và mọi màn con đã có, so với Figma từng màn (danh sách node ở mục Spec); chế độ sáng/tối hệ thống; cỡ chữ lớn nhất; hồi quy: đổi lưới, kiểu icon, mức kính, hình nền, °C/°F, vị trí, tìm kiếm, đổi tên, ẩn app, đánh giá, chính sách bảo mật, đặt launcher mặc định đều chạy như trước.
- [x] **Step 2:** `Figma-Draft.md` (cột "Đã làm" + % các hàng đã đổi giao diện), `project_structure.md` "Tệp thêm ở RS", bài học vào `lessons/settings-and-data.md` hoặc `design-system.md`; tick plan, ghi kết quả.
- [x] **Step 3:** Commit `docs: close RS settings reskin` (935ad2d).

---

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-17 | — | Viết plan sau khi đọc khung Cài đặt (`SettingsActivity`, `SettingsAdapter`, `SettingsRow`, 14 màn, 26 hộp thoại) và Figma trang chủ `302:1492` (màu ô icon từng hàng) | Chủ dự án trả lời Q1–Q3, duyệt plan |
| 2026-11-17 | 1 | Bắt đầu Task 1 | |
| 2026-11-17 | 1 | Code xong Task 1, chờ build + kiểm | Phát hiện: `ControlCenterSettingsActivity` dùng chung `activity_settings.xml` + `SettingsAdapter`, `GlassTuningActivity` dùng chung `SettingsAdapter` → cả hai chuyển theme Ds + card ngay ở Task 1 để không vỡ chữ (CC chỉ đổi khung, không làm giao diện riêng) |
| 2026-11-17 | 1 | Chủ dự án build OK, gộp kiểm cả tính năng sau Task 4 | |
| 2026-11-17 | 2 | Code xong: ô icon 21 hàng, card Launcher hiện tại (`SettingsRow.LauncherCard`, `SettingsUiState.themeId`) | Chờ build + kiểm |
| 2026-11-17 | 3 | Code xong: `SettingsDialogs` (singleChoice/confirm/actionSheet), Thời tiết segmented, Vị trí radio, 13 hộp thoại + CitySearchDialog sang DsDialog | `Dialog.dismiss()` gọi dismiss listener SAU qua Handler: hộp lưới gỡ listener trước khi mở hộp xác nhận |
| 2026-11-17 | 4 | Code xong: Ứng dụng ẩn + Tinh chỉnh kính đổi khung v3; decoration tách `CardKindSource` để App Library dùng lại | Chờ build + kiểm |
| 2026-11-18 | 1–4 | Chủ dự án build + kiểm cả Task 1–4 trên Live 4: không lỗi. Tick Task 1–4 (trừ bước commit) | Chờ duyệt xóa `bg_settings_group_*`; Task 5 chưa làm |
| 2026-11-18 | 6 | Thêm Task 6 (màn 1.1) theo yêu cầu chủ dự án; code xong, chờ build + kiểm | Dùng lại thẻ theme của OB 0.2 |
| 2026-11-18 | 6 | Chủ dự án kiểm Task 6: đạt. Tick (trừ commit) | |
| 2026-11-18 | 7, 8 | Thêm theo yêu cầu chủ dự án: card xem trước thời tiết (dữ liệu mẫu), ô ảnh nền giữ chỗ | |
| 2026-11-18 | 7, 8 | Code xong. Bỏ chuỗi `wx_unit_header` (tiêu đề nhóm Đơn vị không còn dùng, Figma không có) | Chờ build + kiểm |
| 2026-11-18 | 7, 8 | Card thời tiết lấy tên vị trí hiện tại; ô nền 2.2 đổi sang 6 màu của 0.3, chạm → xác nhận → ghi nền màu, ô chọn có viền (chủ dự án) | Chờ build + kiểm |
| 2026-11-18 | 8 | Sửa thiếu dấu tích trắng (chỉ đặt lớp tròn, quên lớp `onb_ic_check`); bỏ ô "Theo giao diện"; hộp xác nhận: "Màn hình chính sẽ dùng nền màu này." + nút "Xác nhận" (chủ dự án) | Chờ build + kiểm |
| 2026-11-18 | 5, 7, 8 | Chủ dự án kiểm Task 7–8: đạt. Cập nhật `Figma-Draft.md` (hàng 1, 3, 4, 8, 10, 15, 16; 1.1 đã đăng ký), `project_structure.md` (Tệp thêm ở RS), `lessons/design-system.md` (mục RS), `lessons/README.md` | Chờ commit |
| 2026-11-18 | — | Chủ dự án duyệt đóng plan; bản Figma mới (Hero Card 2 trạng thái) làm ở plan HC | |
