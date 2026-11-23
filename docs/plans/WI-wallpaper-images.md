# WI — Hình nền ảnh thay ô màu (0.3 + 2.2), rút chữ nút hộp hỏi mặc định — Implementation Plan

> Làm theo `docs/AGENTS.md`. Không có test tự động (chủ dự án). Claude sửa code, chủ dự án build/test/commit.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** 2026-11-18 (22:26)
**Trạng thái:** ✅ Xong 2026-11-18 (chủ dự án kiểm trên Live 4)
**Máy:** Live 4 · **Đổi schema Room:** không · **Khóa config mới:** có (`wallpaper_preset`, Task 2) · **Dependency mới:** không
**Chuỗi:** mọi chuỗi thêm/sửa làm đủ 9 file `values*/strings.xml`, kiểm `lintDebug` (`MissingTranslation`).

**Goal:** (1) Màn 0.3 (Onboarding chọn hình nền) và 2.2 (Cài đặt → Hình nền) dùng 6 ảnh chủ dự án thêm vào `docs/` (2026-11-18) thay 5 ô màu đặc; chọn ảnh → Home dùng ảnh đó làm nền. (2) Hộp hỏi đặt mặc định mỗi lần về Home (PB D1, `DefaultLauncherPromptController`) dùng chữ nút ngắn để không bị cắt.

**Spec:** Chủ dự án 2026-11-18 22:19: "dùng tạm [ảnh trong docs] để thay cho màu đặc ở màn chọn hình nền và màn cài đặt hình nền", "rút lại nội dung nút đặt làm màn hình chính của dialog hiện mỗi lần về home".

## Hiện trạng (đọc code 2026-11-18)

- 0.3: `WallpaperSwatches` (ô 0 "Theo giao diện" = màu theme + 5 màu). Chọn → `AppearanceConfig.wallpaperKind = COLOR`, `wallpaperColor`.
- 2.2: `WallpaperPlaceholders` = 5 màu của 0.3 (bỏ ô 0), ô `WallpaperTileView` tô gradient màu. Chọn → hộp xác nhận → ghi `COLOR` như 0.3.
- Home: `BackdropRepositoryImpl` — `COLOR` đi đường "nền mặc định" (`loadDefaultBackdrop` → `ColorBackdropSource`), không hỏi áp làm wallpaper hệ thống. `WallpaperKind.PRESET` đã có trong enum, chưa dùng (dành cho thư viện hình nền).
- Hộp hỏi mặc định: `SettingsDialogs.confirm(..., positive = onboarding_set_default "Đặt làm mặc định", negative = onboarding_later)`. Chuỗi dùng chung với nút toàn chiều rộng của Onboarding 0.4 (không bị cắt).

## Ảnh (6 tệp, 1320 × 2868)

| # | Tệp | Màu | Ghi chú |
|---|---|---|---|
| 1 | `hinh-nen-ios-27-1.jpeg` (434 KB) | be / tím nhạt | |
| 2 | `hinh-nen-ios-27-2.jpeg` (440 KB) | hồng phấn / tím | gần giống #4 |
| 3 | `hinh-nen-ios-27-3.jpeg` (410 KB) | xanh dương đậm | ảnh tối duy nhất |
| 4 | `hinh-nen-ios-27-4.png` (3.8 MB) | hồng phấn / tím | gần giống #2 |
| 5 | `hinh-nen-ios-27-5.png` (4.3 MB) | nâu đồng / cam | |
| 6 | `hinh-nen-ios-27-6.png` (4.0 MB) | đào / cát | |

**Bản quyền:** tên tệp và kiểu ảnh là hình nền iOS 26/27 của Apple, như `default_wallpaper_ios27.jpg` (AGENTS.md "Trước khi phát hành": không có giấy phép, chỉ để thử). Plan thêm cả 6 ảnh vào `checkNoStoreTestWallpaper` để `bundleRelease` thất bại chừng nào chúng còn trong `res/`.

## Quyết định (Claude đề xuất, chờ duyệt)

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Lưu lựa chọn bằng `WallpaperKind.PRESET` + khóa mới `wallpaper_preset` (chuỗi id ổn định, vd `wi_1`), không nhập ảnh vào ảnh tự chọn (`PHOTO`) | Ô đang chọn ở 2.2 xác định được; ảnh tự chọn của người dùng không bị ghi đè; đi đường "nền mặc định" như `COLOR` (không hỏi áp wallpaper hệ thống) |
| 2 | Ảnh vào `res/drawable-nodpi/wi_wallpaper_N.webp` (WebP chất lượng 90, giữ 1320 × 2868; 146–187 KB/ảnh) + ảnh nhỏ `wi_wallpaper_N_thumb.webp` (400 px rộng, ~12 KB) cho các ô và khung xem trước 0.3 | Chủ dự án: ưu tiên WebP. Chất lượng 82 thấy mất hạt ở vùng chuyển màu (so 1:1), nên 90. Ô lưới không giải mã ảnh to |
| 3 | Home nạp ảnh qua `BackdropRepositoryImpl.loadDefaultBackdrop` (nhánh `PRESET` → `PresetBackdropSource` giải mã có `inSampleSize` theo `reqW/reqH`, bắt `OutOfMemoryError` như hiện có) | Dùng lại đường nền mặc định đã chạy |
| 4 | Ô `COLOR` cũ (người dùng đã chọn màu trước WI) vẫn hiện đúng nền; ở 2.2 không ô nào được chọn | Không cần chuyển đổi dữ liệu |
| 5 | Nút hộp hỏi dùng chuỗi mới `default_prompt_set`; Onboarding 0.4 giữ `onboarding_set_default` | Chỉ hộp hai nút bị cắt |
| 6 | Áp theme (1.1, 0.2) giữ nguyên hình nền (`ThemePresets.applyPreset` không chép `wallpaperKind/Color/Preset`) | Hệ quả Q2: bỏ "Theo giao diện" thì theme không còn nền minh họa; nếu không giữ, áp theme sẽ xóa ảnh vừa chọn và mọi lựa chọn ảnh làm theme thành "đã chỉnh" (`ObserveThemeStateUseCase`) |
| 7 | 0.3 chọn sẵn ảnh đầu (Bình minh); Onboarding luôn ghi PRESET ở bước 0.3 | Không còn ô "Theo giao diện" |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Ảnh #2 và #4 gần như giống nhau. Dùng cả 6 hay bỏ #4 còn 5? | Bỏ #4: 0.3 giữ đúng 6 ô như Figma (ô "Theo giao diện" + 5 ảnh), 2.2 có 5 ảnh | Bỏ #4 |
| Q2 | 0.3 có giữ ô đầu "Theo giao diện" (màu đặc của theme) không? | Giữ: chọn sẵn, theme không thành "đã chỉnh" | **Bỏ** (không có ảnh nền minh họa cho từng giao diện) → 0.3 có 5 ô; xem quyết định 6, 7 |
| Q3 | Tên ảnh (hiện ở hộp xác nhận 2.2 "Đặt hình nền "…"?" và tóm tắt 0.5). Tên cũ Lagoon/Night Bloom… không hợp ảnh mới | Tên mới theo màu: Bình minh (#1), Hồng phấn (#2), Đại dương (#3), Đồng (#5), Cát (#6); dịch đủ 9 ngôn ngữ | Theo đề xuất |
| Q4 | Chữ nút hộp hỏi | vi "Đặt ngay" · en "Set now" (dịch 7 ngôn ngữ còn lại cùng nghĩa, ngắn) | Theo đề xuất |

## Tasks

### Task 1: Nút ngắn ở hộp hỏi mặc định ✅
- `values*/strings.xml` (9): thêm `default_prompt_set` theo Q4.
- `DefaultLauncherPromptController.show()`: positive dùng `R.string.default_prompt_set`.
- Kiểm: Live 4, launcher khác làm mặc định → về Home của mình → hộp hỏi: hai nút đủ chữ, không "…". Đổi ngôn ngữ app sang en/es/hi kiểm lại.

### Task 2: Ảnh + nguồn nền PRESET ✅
- Thêm ảnh theo quyết định 2 (Claude chuyển đổi, đặt tệp). `app/build.gradle.kts`: thêm các ảnh WI vào `checkNoStoreTestWallpaper.testWallpapers`.
- `ui/wallpaper/WallpaperPresets.kt` (mới): `data class WallpaperPreset(id: String, @StringRes name, @DrawableRes full, @DrawableRes thumb, isNew)`, `object WallpaperPresets { list; fun of(id): WallpaperPreset? }`.
- `AppearanceConfig.wallpaperPreset: String?` + khóa DataStore `wallpaper_preset` (`ConfigKeys`, repository), ghi vào `docs/config-schema.md`.
- `data/backdrop/PresetBackdropSource.kt` (mới) + nhánh `PRESET` trong `BackdropRepositoryImpl` (`backdropVersion` riêng mỗi preset để Home nạp lại).
- Kiểm: chưa có UI chọn; Task 3/4 kiểm cùng.

### Task 3: Màn 2.2 dùng ảnh ✅
- `WallpaperTileView.setImage(@DrawableRes thumb)` (ảnh `centerCrop`, giữ bo/viền/dấu chọn/badge).
- `WallpaperSettingsScreen` + `SettingsRow.WallpaperGrid`: nguồn ô = `WallpaperPresets`; ô chọn = preset đang dùng khi `wallpaperKind == PRESET`.
- `SettingsViewModel`: `choosePreset(id)` ghi `PRESET` + `wallpaper_preset`. Hộp xác nhận giữ ("Đặt hình nền "{tên}"?", "Xác nhận"); đổi chữ nội dung `wallpaper_color_confirm_message` "Màn hình chính sẽ dùng nền màu này." → "Màn hình chính sẽ dùng hình nền này." (9 ngôn ngữ).
- Bỏ `WallpaperPlaceholders` (báo chủ dự án trước khi xóa tệp).
- Kiểm: 2.2 hiện ảnh nhỏ; chọn → xác nhận → về Home thấy ảnh; mở lại 2.2 ô đó có viền + dấu chọn; chọn ảnh từ máy (PHOTO) rồi chọn lại preset vẫn đúng.

### Task 4: Màn 0.3 dùng ảnh ✅
- `WallpaperSwatches` → ô 0 "Theo giao diện" (Q2) + các preset; ô ảnh vẽ ảnh nhỏ; khung điện thoại xem trước hiện ảnh đang chọn.
- `OnboardingViewModel.onPrimary`: ô 0 ghi `COLOR` màu theme (như cũ), ô ảnh ghi `PRESET`. Đọc lại lựa chọn khi xoay/khôi phục theo `wallpaper_preset`.
- 0.5 tóm tắt "Hình nền" hiện tên preset (Q3).
- Kiểm: Onboarding từ đầu (xóa dữ liệu app), chọn từng ô, xem trước đổi theo; xong → Home đúng ảnh; chọn "Theo giao diện" → Home màu theme.

### Task 6: Nút cuối Onboarding 0.5 vào Cài đặt ✅, câu dẫn 0.5 (chủ dự án 2026-11-18 22:53)
- `OnboardingStep.DONE`: nút `onboarding_go_settings` ("Vào Cài đặt", thay `onboarding_go_home`). `OnboardingActivity` (sự kiện Close): mở `SettingsActivity` với `CLEAR_TOP | SINGLE_TOP` rồi đóng (thay HC Q3: vào lưới Home).
- `onboarding_done_subtitle` không còn nói tới nút Home: "Khám phá giao diện mới và tinh chỉnh thêm bất cứ lúc nào trong Cài đặt." (9 ngôn ngữ).
- Kiểm: mở app bằng icon lần đầu → Onboarding → "Vào Cài đặt" → trang chủ Cài đặt, Back thoát app (không có Cài đặt thứ hai); Onboarding mở từ Home → "Vào Cài đặt" → Cài đặt, Back về Home.

### Task 5: Docs ✅
- `docs/AGENTS.md` mục "Trước khi phát hành": thêm 5/6 ảnh WI vào việc xóa ảnh Apple. `docs/project_structure.md`, `docs/config-schema.md`, `docs/Figma-Draft.md` (0.3, 2.2).
- Báo chủ dự án xóa 6 ảnh gốc trong `docs/` (đã chuyển vào `res/`) nếu không cần giữ.

## Nhật ký

- 2026-11-18: Task 1–4 code (một mạch). Tệp mới: `core/wallpaper/WallpaperPresets.kt`, `data/backdrop/ResourceBackdropLoader.kt` (tách phần giải mã của `DefaultBackdropSource`), 10 ảnh `res/drawable-nodpi/wi_wallpaper_{1..5}{,_thumb}.webp`. Sửa: `AppearanceConfig` + `ConfigKeys` + `AppearanceRepositoryImpl` (`wallpaper_preset`), `ThemePresets.applyPreset` (quyết định 6), `BackdropRepositoryImpl` (nhánh PRESET), `DefaultLauncherPromptController`, `WallpaperTileView` (`setImage`, viền ở foreground), `SettingsRow/Adapter/UiState/ViewModel`, `WallpaperSettingsScreen`, `OnboardingCatalog` (bỏ `WallpaperSwatches`), `OnboardingPages`, `OnboardingViewModel`, `build.gradle.kts` (`checkNoStoreTestWallpaper`), 9 `strings.xml` (thêm `wallpaper_preset_*`, `default_prompt_set`; đổi `wallpaper_color_confirm_message`; bỏ `onboarding_wallpaper_theme/lagoon/night_bloom/sunset/prism/deep_blue`), `docs/config-schema.md`. Chờ chủ dự án xóa `ui/wallpaper/WallpaperPlaceholders.kt` (không còn dùng, giữ lại thì build lỗi).
- 2026-11-18: Task 5 docs (`AGENTS.md`, `config-schema.md`, `project_structure.md`, `Figma-Draft.md`). Chủ dự án kiểm Task 1–4, 6: ổn. Đóng plan.
- 2026-11-18: Ghi chú lỗi Gần đây (Live 4): lỗi về launcher Vsmart xuất hiện lại trong lúc làm WI, sau Task 6 chưa tái hiện được; theo dõi riêng, không thuộc plan này.
- 2026-11-18: Task 6 code. Hoàn tác `AndroidManifest.xml` (bỏ comment tạm khối `ControlCenterService` dùng cho APK ký tạm).
