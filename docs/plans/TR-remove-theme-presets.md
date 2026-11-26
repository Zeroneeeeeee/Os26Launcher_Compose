# TR — Bỏ màn "Màn hình chính mặc định" và preset theme; Onboarding chọn kiểu icon (B19) — Implementation Plan

> Làm theo `docs/AGENTS.md`. Chủ dự án cho làm một mạch (2026-11-26 16:09), mỗi Task một commit; build/kiểm một thể sau cùng (VM không có
> Android SDK nên Claude không build được: mọi commit chỉ được rà bằng đọc code). Không có test tự động (không chạm planner/domain có test).

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (chốt trong chat)
**Trạng thái:** Đang làm
**Đổi schema Room:** không · **Đổi khóa DataStore:** bỏ `appearance_theme_id`, `appearance_icon_bevel` (giá trị cũ trong máy bị bỏ qua)

**Goal:** Bỏ màn chọn 4 preset theme (Liquid Glass 27 / Classic 26 / Minimal / Dark Pro) và mọi logic preset; bước THEME của Onboarding đổi thành chọn kiểu icon (G2b).
**Architecture:** Onboarding có bước `ICON_STYLE` thay `THEME`: lưới thẻ, mỗi thẻ 4 icon mẫu vẽ theo kiểu đó (`AppRepository.iconWithStyle`, app mẫu dùng chung với khung xem trước của Cài đặt → Bộ biểu tượng). Bỏ `ThemeId`, `ThemePresets`, use case theme, màn `ThemeSettingsActivity`, cờ `iconBevel`. Hero Card Cài đặt không còn đọc theme.
**Tech Stack:** không thêm dependency.
**Spec:** chủ dự án 2026-11-26 (chat, backlog B19).

## Quyết định đã chốt (chủ dự án, chat 2026-11-26)

| # | Quyết định |
|---|---|
| 1 | Bỏ màn 1.1 "Màn hình chính mặc định" (`ThemeSettingsActivity`) và logic preset: các mục cài đặt nhỏ đã thay vai trò của nó |
| 2 | Bước THEME của Onboarding → chọn **kiểu icon** (G2b: Mặc định, Tô màu, 3D, Kính…), không phải gói icon |
| 3 | Mặc định cho người dùng mới: theo đề xuất "lấy bộ số Liquid Glass 27 trừ kiểu icon" — xem Ruling R1 |
| 4 | Lớp nổi 3D (`iconBevel`) trùng hiệu ứng của kiểu 3D → bỏ cờ (app chưa phát hành, không có người dùng cũ) |
| 5 | B14 (ghép hình nền theo preset) bỏ; giữ logic "đặt làm launcher mặc định" (`DefaultLauncherRepository`, `DefaultLauncherAction`, `DefaultLauncherPromptController`) |
| 6 | Chủ dự án duyệt xóa các file liệt kê ở "Vùng file" (chat 16:09) |

## Ruling (Claude tự quyết khi làm, chủ dự án xem lại)

| # | Ruling | Vì sao | Sai thì tốn |
|---|---|---|---|
| R1 | KHÔNG đổi `GlassLook.DEFAULT` sang 40/60/55/50 của Liquid Glass 27 | `GlassLook.DEFAULT` (khúc xạ 0, bóng 50, mờ 0, bo 57) do chủ dự án chốt 2026-11-25, sau preset (2026-11-17), và là đích của nút "Mặc định" trong Cài đặt kính. Các trường khác của LG27 (bo 62, cỡ 50, nhãn, mức kính AUTO, trong suốt 70, tông sáng, màu tô xanh) đã trùng mặc định | Đổi một dòng ở `GlassLook` |
| R2 | Bước kiểu icon chọn sẵn kiểu đang lưu (máy mới: Mặc định) | Không áp lựa chọn ngầm; thẻ đang chọn = cấu hình thật | Đổi giá trị khởi tạo trong ViewModel |
| R3 | Kiểu "Trong suốt" (`TRANSPARENT`) hiện ở cả Onboarding và hộp chọn của Cài đặt | Trước chỉ preset Minimal ghi được; bỏ preset mà không hiện thì kiểu này thành mã chết | Bỏ khỏi danh sách hiện |
| R4 | Hero Card "chưa mặc định": ảnh `onb_hero`, tên `app_name`; huy hiệu tròn "đã mặc định" cố định "27" | Trước lấy ảnh/tên/số của theme (theme null = như Liquid Glass 27) | Đổi ảnh/chữ trong `SettingsCards.kt` |
| R5 | Bỏ hẳn `SettingsScreenId.THEME` (giá trị giữ chỗ chưa đăng ký) | Màn gửi qua extra bằng TÊN, bỏ giá trị không lệch dữ liệu | Thêm lại một giá trị enum |

## Vùng file

**Xóa (đã duyệt):** `ui/settings/ThemeSettingsActivity.kt`, `ThemeSettingsContent.kt`, `ThemeSettingsViewModel.kt`, `ThemeBadge.kt`; `domain/config/ThemePreset.kt`; `domain/usecase/ApplyThemeUseCase.kt`, `ObserveThemeStateUseCase.kt`; `ui/onboarding/OnboardingCatalog.kt`; `app/src/debug/java/.../debug/ThemeDebugReceiver.kt`; `res/drawable-nodpi/onb_theme_{liquid_glass_27,classic_26,minimal,dark_pro}.webp`.
**Tạo:** `ui/onboarding/IconStyleChoicesViewModel.kt`, `ui/settings/IconStyleSamples.kt`.
**Sửa:** Onboarding (`OnboardingStep`, `OnboardingViewModel`, `OnboardingActivity`, `compose/OnboardingScreen.kt`); Cài đặt (`SettingsHubScreen`, `SettingsScreen`, `SettingsRow`, `SettingsUiState`, `SettingsViewModel`, `IconSetSettingsScreen`, `IconStylePreviewViewModel`, `compose/SettingsCards.kt`); cấu hình (`AppearanceConfig`, `ConfigEnums`, `ConfigKeys`, `AppearanceRepositoryImpl`); icon (`IconStyle.kt` `IconStyleKey`, `IconRenderer`, `IconCache`, `IconLoader`, `IconStyleSource`, `HomeViewModel`); `AndroidManifest.xml` (main + debug); `strings.xml` × 9; docs.

## Global Constraints

- Chuỗi người dùng thấy: thêm/sửa đủ 9 file `values*/strings.xml` (en, es, hi, in, ja, ko, pt, th, vi).
- Thêm/đổi/bỏ khóa cấu hình: sửa `docs/config-schema.md` trong CÙNG commit (`AGENTS.md`).
- Không đụng luồng "đặt launcher mặc định" (hero card, bước 0.4, lời nhắc ở Home).
- Commit message tiếng Anh không dấu, kết thúc bằng dòng attribution.

## Review Focus

1. Không còn tham chiếu tới `ThemeId`, `ThemePresets`, `ThemeCatalog`, `ThemeBadge`, `ApplyThemeUseCase`, `ObserveThemeStateUseCase`, `ThemeSettingsActivity`, `iconBevel`, `R.drawable.onb_theme_*`, `R.string.settings_theme_*`, `R.string.onboarding_theme_*` (grep cả `src/debug`, `src/release`, `src/perf`, `src/test`).
2. Onboarding máy mới, app chưa nạp xong: thẻ kiểu icon hiện khung trống, không crash, chọn/ghi vẫn được.
3. Onboarding bị process death ở bước kiểu icon: lựa chọn giữ qua `SavedStateHandle`.
4. Khóa cache icon: bỏ cờ nổi không đổi chuỗi khóa của icon không nổi (cache đĩa cũ vẫn trúng).
5. Hàng tóm tắt 0.5 nhảy về bước kiểu icon.

---

### Task 1: Onboarding — bước chọn kiểu icon

**Files:** `ui/onboarding/OnboardingStep.kt`, `OnboardingViewModel.kt`, `OnboardingActivity.kt`, `compose/OnboardingScreen.kt`; tạo `ui/onboarding/IconStyleChoicesViewModel.kt`, `ui/settings/IconStyleSamples.kt`; sửa `ui/settings/IconStylePreviewViewModel.kt` (dùng `IconStyleSamples`); `strings.xml` × 9.

**Interfaces:**
- Produces: `object IconStyleSamples { fun pick(apps: List<AppItem>): List<AppItem> }` (4 app mẫu, chuyển nguyên từ `IconStylePreviewViewModel.pickSamples`); `IconStyleChoicesViewModel.start(iconPx: Int)`, `val previews: StateFlow<Map<IconStyle, List<Bitmap>>>`; `IconStyleChoices.list: List<IconStyle>` (Mặc định, Kính, Tô màu, 3D, Trong suốt).
- `OnboardingStep.ICON_STYLE` thay `THEME` (cùng vị trí). `OnboardingViewModel.iconStyle: StateFlow<IconStyle>`, `chooseIconStyle(IconStyle)`; nút "Tiếp tục" ở bước này ghi `appearance.update { it.copy(iconStyle = …) }`.

- [ ] **Step 1:** `IconStyleSamples` + `IconStylePreviewViewModel` gọi nó (không đổi hành vi).
- [ ] **Step 2:** `IconStyleChoicesViewModel` (Hilt): app hiện (`ObserveVisibleAppsUseCase`) → 4 mẫu → mỗi kiểu một `IconStyleKey(style, tint đang lưu, pack đang lưu, corner = glassLook.corner)` → `iconWithStyle`. Dựng lại khi danh sách mẫu/gói/bo góc đổi.
- [ ] **Step 3:** `OnboardingViewModel`: bỏ `ApplyThemeUseCase`, `KEY_THEME`; thêm `KEY_ICON_STYLE`; khởi tạo bằng kiểu đang lưu (R2); ghi khi "Tiếp tục".
- [ ] **Step 4:** `OnboardingScreen`: `IconStylePage` (tiêu đề `onboarding_icon_style_title`, phụ đề `onboarding_icon_style_subtitle`, lưới 2 cột thẻ kính: 4 icon + tên kiểu `settings_icon_style_*`, viền chọn như ô hình nền); hàng tóm tắt 0.5 = `settings_icon_style` + tên kiểu.
- [ ] **Step 5:** Chuỗi: thêm `onboarding_icon_style_title` × 9; đổi tên `onboarding_theme_subtitle` → `onboarding_icon_style_subtitle` (giữ bản dịch); bỏ `onboarding_theme_title`, `onboarding_done_theme`.
- [ ] **Step 6:** Rà: grep `OnboardingStep.THEME`, `chooseTheme`, `ui.theme` = 0. Commit `feat: onboarding icon style step replaces theme step`.

### Task 2: Bỏ màn theme, preset, cờ nổi 3D

**Files:** xóa toàn bộ danh sách "Xóa"; sửa phần còn lại của "Vùng file"; `docs/config-schema.md`.

- [ ] **Step 1:** Cài đặt: bỏ hàng `hub_theme` (`SettingsHubScreen`), `SettingsScreenId.THEME` + dòng giữ chỗ (R5), `themeId` ở `SettingsUiState`/`SettingsViewModel`/`SettingsRow.LauncherCard`; Hero Card theo R4; hộp chọn kiểu icon hiện cả `TRANSPARENT` (R3); `onIconStyleChosen` chỉ ghi `iconStyle`.
- [ ] **Step 2:** Cấu hình: bỏ `ThemeId` (`ConfigEnums`), `AppearanceConfig.themeId`, `iconBevel`, khóa `THEME_ID`, `ICON_BEVEL` (`ConfigKeys`, `AppearanceRepositoryImpl`).
- [ ] **Step 3:** Icon: bỏ `IconStyleKey.bevel` + `bevelFor`; `IconRenderer` chỉ nổi khi `THREE_D`; bỏ phần `+bevel`/`|bevel` trong khóa cache (`IconCache`, `IconLoader`); `IconStyleSource`, `HomeViewModel`, `IconStylePreviewViewModel`, `IconStyleChoicesViewModel` bỏ trường nổi.
- [ ] **Step 4:** Manifest (main: `ThemeSettingsActivity`; debug: `ThemeDebugReceiver`); xóa file; bỏ chuỗi `settings_hub_theme`, `settings_theme_*`, `onboarding_theme_*`, `settings_launcher_card_custom` × 9.
- [ ] **Step 5:** `docs/config-schema.md`: bỏ hai dòng khóa, cột/ghi chú "Theme ghi" và mục áp theme.
- [ ] **Step 6:** Rà Review Focus 1 bằng grep (= 0 kết quả). Commit `refactor: remove theme presets screen and icon bevel flag`.

### Task 3: Docs

- [ ] `Launcher-Features.md` (dòng theme / Onboarding), `project_structure.md` (file đã xóa/tạo), `plans/BACKLOG.md` (B19 → Đã xong), `plans/README.md`, `AGENTS.md` nếu có bài học. Commit `docs: TR theme preset removal`.

## Kịch bản kiểm (chủ dự án, sau khi build)

1. Xóa dữ liệu app → Onboarding: bước 2 là "Chọn kiểu icon", 5 thẻ có icon mẫu đúng kiểu; chọn Kính → Tiếp tục → … → Home icon kiểu Kính.
2. 0.5: hàng "Kiểu icon" hiện "Kính", chạm về bước kiểu icon.
3. Cài đặt: không còn hàng "Màn hình chính mặc định"; Hero Card hiện ảnh + "OS26 Launcher"; đặt mặc định xong hiện huy hiệu "27".
4. Cài đặt → Bộ biểu tượng: hộp chọn có "Trong suốt"; chọn 3D thì icon nổi, chọn kiểu khác thì hết nổi.

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
