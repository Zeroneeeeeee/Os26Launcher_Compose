# DS3 — Hệ thống thiết kế v3 (token + thành phần nền) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md` (đổi 2026-11-16 từ luồng nhiều agent): code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chủ dự án kiểm trên máy trước khi xác nhận; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (không cần Oppo: không đụng kính thật). Lệnh viết cho CMD/PowerShell. Trong plan này "Claude" là người làm mọi bước trước đây ghi cho điều phối hay agent; bước cần `adb` hay build mà phiên không làm được (`Workflow.md` mục 5) thì chủ dự án làm.

**Bắt đầu:** 2026-11-16 · **Plan duyệt:** 2026-11-16 (chủ dự án, Q1–Q5 theo đề xuất)
**Trạng thái:** Xong 2026-11-17 (Task 1–7, kiểm cả tính năng trên Live 4, tài liệu chung đã cập nhật; chưa đo APK release)
**Máy:** Live 4 · **Đổi schema Room:** không (không thêm khóa DataStore)

**Goal:** Đưa phong cách v3 vào code thành **token** (màu, chữ, khoảng cách, bo góc) và **bộ thành phần nền** dùng lại được (bề mặt kính, nút, chip, segmented, badge, công tắc, radio, thanh trượt, thanh tiến độ, chỉ báo bước, hàng, card, ô tìm kiếm, thanh điều hướng, hộp thoại, bottom sheet, icon), kèm **màn catalog chỉ có ở bản debug** để đối chiếu với Figma. **Chưa áp vào màn nào của app**: các plan sau (Home cài đặt, Onboarding, từng màn) chỉ việc dùng bộ này.

**Architecture:** Toàn bộ là Views/XML như phần còn lại của app (không Compose). Token là tài nguyên Android tiền tố `ds_` trong **file mới riêng** (`ds_colors.xml`, `ds_dimens.xml`, `ds_type.xml`, `ds_themes.xml`, `ds_attrs.xml`) để không đụng file dùng chung. Hiệu ứng kính v3 vẽ bằng **một lớp `Drawable` theo bảng thông số** (`DsSurfaceDrawable` + `DsSurface`), không dùng blur nền thật: màn v3 nằm trên nền tối "aurora" tĩnh nên blur nền không đổi được gì nhìn thấy. Thành phần đơn giản là style + drawable XML; thành phần có hành vi là custom view nhỏ trong package mới `core/designsystem`. Catalog nằm ở `src/debug`, mở bằng `adb shell am start`. **Bo góc của bề mặt kính v3 (card, card nổi bật, hộp thoại/sheet) không phải hằng:** số trong `ds_dimens.xml` là bán kính gốc; thành phần hỏi bo góc lúc chạy qua `DsCorners`, và bán kính thật = gốc × thanh "Bo góc" của kính (GT, khóa `glass_look_corner`, quy đổi qua `GlassTuning.cornerRadius`). Không thêm khóa cài đặt mới (Task 1b, quyết định 7).

**Tech Stack:** Kotlin, Views/XML, Material Components 1.14.0 (đã có), AppCompat 1.8.0 (đã có). **Không dependency mới.** Thêm 4 file font Be Vietnam Pro (OFL) vào `res/font`.

**Spec:** `docs/Figma-Draft.md` mục 2 và mục 4 (V1: v3 là thiết kế chính thức). Figma v3 trang "v3 · Thiết kế mới": section **v3 · Components** (`302:3330`) và Figma styles của file (`Core/*`, `v3/*`, kiểu chữ), đọc bằng `figma-bridge` 2026-11-16. Không có mục TT# trong `Launcher-Features.md`. Yêu cầu của chủ dự án (2026-11-16): plan gồm cả bước nhập màu, quy ước padding/margin chung và mọi thành phần nền (dialog, switch, progress bar, ...).

## Vì sao chọn thiết kế này

### Vấn đề cần giải

- **Hiện chưa có hệ thống thiết kế.** `themes.xml` là `Theme.Material3.DayNight.NoActionBar` mặc định, không đặt màu nào; `colors.xml` (54 màu) và `dimens.xml` (~90 kích thước) là màu/kích thước rời theo từng mốc ("điểm xuất phát, chỉnh theo mắt"). Không có font riêng (`res/font` chưa có). Cài đặt dùng hàng Material 3 mặc định (`item_settings_*.xml`). Đọc code `main` @ `47728c9`.
- **v3 có hệ thống rõ ràng trong Figma** (đọc 2026-11-16): paint styles `Core/Text/*`, `Core/Surface/*`, `Core/Accent/*`, `Core/Aurora/*`, `Core/Status/*`, `Core/Icon/*`, `Core/Icon Glyph/*` (10 màu); 10 text style (Be Vietnam Pro); effect styles `v3/Card/Default`, `v3/Control`, `v3/Button/Glow`, `v3/Tile/Glow/*`, `v3/Highlight/*`. **Không có Figma variables** (`get_variable_defs` trả rỗng), nên "nhập màu" là chép tay theo bảng ở Task 1, không có công cụ tự xuất.
- **Hiệu ứng kính v3 nặng nếu làm đúng từng lớp:** mỗi card có background blur 30, 4 inner shadow, 2 drop shadow; nút chính 3 inner shadow + glow tím. `minSdk = 26`: `Paint.setShadowLayer`/`BlurMaskFilter` trên canvas tăng tốc phần cứng chỉ chạy từ API 28 (đọc tài liệu Android về hardware acceleration), còn vẽ ra bitmap phần mềm thì tốn bộ nhớ (card 328×280 dp ở 2,75x ≈ 2,8 MB ARGB mỗi card).
- **Tách token khỏi file chung:** `colors.xml`, `dimens.xml`, `themes.xml` đang chứa màu/kích thước rời của các mốc cũ. Token đặt ở file mới (`ds_*.xml`) để thấy rõ phần nào là hệ thống v3 và dễ thay dần khi áp vào từng màn. (Lý do gốc lúc viết plan là tránh xung đột giữa nhiều nhánh song song; từ 2026-11-16 làm nối tiếp nên lý do đó không còn, nhưng cách tách vẫn giữ.)
- **Thiết kế thiếu một số trạng thái:** không có progress bar, trạng thái disabled/pressed, text field (ô tìm chỉ xuất hiện trong màn 1.3, 5.1). Plan phải tự định nghĩa từ token và ghi rõ là suy ra.

### Các phương án đã xét

**(a) Vẽ hiệu ứng kính**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Drawable theo bảng thông số, xấp xỉ inner shadow bằng gradient, bỏ drop shadow đen và blur nền** | Một `DsSurfaceDrawable` vẽ: các lớp fill (solid, radial, linear) + một lớp phủ gradient dọc mô phỏng viền sáng trên/tối dưới + viền mảnh (badge). Glow màu của nút chính dùng `elevation` + `outlineSpotShadowColor` (API 28+) | Chạy trên canvas phần cứng mọi API 26+, không cấp bitmap; một chỗ giữ số liệu Figma; thêm biến thể = thêm một dòng bảng | Không giống từng pixel: inner shadow có blur 2–18 thành gradient cứng hơn; drop shadow đen 18–35% không vẽ (trên nền `#07080E` gần như không thấy — suy luận, kiểm bằng mắt ở Task 2); API 26–27 không có glow tím | **Chọn** |
| B. Vẽ đủ mọi lớp vào bitmap phần mềm (blur thật), cache theo kích thước | `BlurMaskFilter`/`setShadowLayer` trên `Canvas(Bitmap)` | Giống Figma nhất | ~2–3 MB mỗi card, danh sách 6 card ~15 MB; dựng lại khi đổi cỡ; Live 4 đã có giới hạn bộ nhớ ảnh nền (FX1) | Không |
| C. Dùng lại `core/glass` (kính thật/StaticBlurSurface) cho card | `GlassSurfaceFactory` như dock | Có khúc xạ thật | Kính thật trên Live 4 giật (M6: S1 Janky 30,7% với panel); nền aurora tĩnh nên khúc xạ không lộ gì; `core/glass` gắn với ảnh nền Home | Không |

**(b) Font Be Vietnam Pro**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Đóng gói 4 file TTF trong `res/font`** | Light 300, Regular 400, SemiBold 600, Bold 700 từ `google/fonts` (OFL) | Có ngay từ khung hình đầu kể cả khi mất mạng; launcher là màn đầu sau khởi động máy | Tăng APK (ước lượng vài trăm KB so với APK release 2,75 MB — đo ở Task 1); phải kèm giấy phép OFL | **Chọn** |
| B. Downloadable Fonts (nhà cung cấp Google Fonts qua GMS) | `res/font/*.xml` kiểu provider | Không tăng APK | Lần đầu tải bất đồng bộ nên khung hình đầu dùng font hệ thống rồi nhảy font; máy không có GMS không bao giờ có font | Không |

**(c) Thành phần có hành vi**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Style + drawable cho widget sẵn có; custom view nhỏ khi cần ghép** | Công tắc = `SwitchCompat` + drawable; radio = `AppCompatRadioButton` + drawable; thanh trượt = `AppCompatSeekBar` + drawable; tiến độ = `LinearProgressIndicator`/`CircularProgressIndicator` + style; còn lại (hàng, segmented, chỉ báo bước, thanh điều hướng, hộp thoại, sheet) là custom view/lớp bọc | Giữ trợ năng, bàn phím, lưu trạng thái của widget gốc; code ít | Phải gỡ các mặc định M3 của Material (khe chỉ báo, chấm cuối thanh) | **Chọn** |
| B. Tự vẽ mọi thứ bằng custom view | — | Toàn quyền hình | Tự viết lại trợ năng, lưu trạng thái, chạm cho từng thành phần | Không |
| C. Chuyển sang Jetpack Compose | — | Hợp xu hướng | Cả app là Views; thêm Compose là thay đổi lớn ngoài phạm vi, tăng APK | Không |

### Vì sao chọn

- (a) A: màn v3 đặt trên nền aurora tĩnh (Figma: 4 ellipse blur 170 trên `#07080E`), nên blur nền dưới card cho kết quả gần như đồng màu; chính Figma có style `v2/Card/Fill Solid (Android 11-)` cho máy không blur, tức nhà thiết kế đã chấp nhận bản không blur. Bỏ drop shadow đen: bóng đen 35% trên nền gần đen gần như vô hình (suy luận, Task 2 kiểm bằng mắt cạnh ảnh Figma).
- (b) A: launcher không được nhảy font ở màn đầu; dung lượng đo được ở Task 1, nếu vượt ngưỡng ghi ở Global Constraints thì dừng, báo chủ dự án.
- (c) A: dự án không có test, nên dựa vào hành vi đã kiểm của widget gốc (TalkBack, lưu trạng thái khi dựng lại) là cách an toàn nhất.

### Đánh đổi chấp nhận

- Card, nút, ô icon **giống ở mức nhìn**, không trùng pixel với Figma. Xét lại nếu chủ dự án thấy khác biệt rõ ở bài kiểm Task 2.
- API 26–27 không có glow tím của nút chính (không có máy thử; chỉ kiểm bằng lint `NewApi`).
- Theme v3 **chỉ tối** (Figma chỉ có giao diện tối); bật chế độ sáng của hệ thống không đổi màn v3.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | v3 là thiết kế chính thức; dựng giao diện v3 bắt đầu từ Components | Chủ dự án 2026-11-16 (V1 trong `Figma-Draft.md`) |
| 2 | Plan gồm bước nhập màu, quy ước padding/margin chung, mọi thành phần nền | Chủ dự án 2026-11-16 |
| 3 | Vẽ kính bằng drawable theo thông số, không blur nền, không drop shadow đen (phương án (a) A) | Claude đề xuất, chủ dự án duyệt 2026-11-16 (Q1) |
| 4 | Đóng gói font Be Vietnam Pro (phương án (b) A) | Claude đề xuất, chủ dự án duyệt 2026-11-16 (Q2) |
| 5 | Thành phần thiếu trong Figma (progress bar, disabled, pressed, text field) suy ra từ token theo bảng ở Task 4–5 | Claude đề xuất, chủ dự án duyệt 2026-11-16 (Q3); nhà thiết kế có thể bổ sung sau |
| 6 | Theme v3 chỉ tối | Claude đề xuất theo Figma, chủ dự án duyệt 2026-11-16 (Q4) |
| 7 | Bo góc 3 vai kính `CARD` 20, `CARD_LARGE` 24, `SHEET` 28 là **bán kính gốc** (`ds_radius_*`), nhân theo thanh "Bo góc" **có sẵn của kính** (GT: `GlassLook.corner`, `GlassTuning.cornerRadius`), đổi lúc chạy qua listener của `GlassEnvironment`. **Không thêm khóa cài đặt.** Vai `TILE` 8 (ô icon trong hàng) giữ cố định: cơ chế bo góc cho icon là việc riêng sắp làm (IS), không làm trong DS3. Bo PILL (nửa chiều cao) và bo nội bộ nhỏ (công tắc 16, tay nắm 3) giữ hình học | Chủ dự án quyết 2026-11-16 (sau khi GT merge, PR #62): "cơ chế bo góc sắp làm là cho icon, bo góc sẵn có dành cho kính". Thay bản trước (4 khóa `ui_radius_*` riêng), bản đó bỏ |
| 8 | Bề mặt kính v3 `CARD`, `CONTROL`, `SHEET` theo thanh "Độ bóng" của kính (GT): lớp nền đặc phủ qua `GlassTuning.tint`, viền sáng mép trên nhân qua `GlassTuning.border`; ở mặc định 50 giữ đúng Figma. `PRIMARY`, `CONTROL_SELECTED`, badge giữ cố định. Khúc xạ và độ mờ của GT không áp (v3 không làm mờ nền) | Chủ dự án chọn 2026-11-16 khi rà GT trước Task 2 |

**Câu hỏi cho chủ dự án** (đã trả lời 2026-11-16: đồng ý cả 5 đề xuất):
- Q1: Đồng ý bỏ blur nền và drop shadow đen, xấp xỉ inner shadow bằng gradient? (Đề xuất: đồng ý; kiểm bằng mắt ở Task 2.)
- Q2: Đóng gói font trong APK? (Đề xuất: đồng ý.)
- Q3: Tự định nghĩa progress bar, disabled, pressed theo bảng ở Task 4–5, hay chờ nhà thiết kế vẽ? (Đề xuất: tự định nghĩa, nhà thiết kế sửa sau chỉ cần đổi token.)
- Q4: Màn v3 luôn tối kể cả khi máy đặt giao diện sáng? (Đề xuất: đồng ý.)
- Q5 (đã trả lời lại 2026-11-16): bề mặt kính v3 bo theo thanh "Bo góc" của kính (GT); bo góc icon là cơ chế riêng ở plan IS; không thêm khóa `ui_radius_*`.

## Ngoài phạm vi / ghi để sau

- **Áp v3 vào màn thật** (Cài đặt, Ứng dụng ẩn, hộp thoại đang dùng `MaterialAlertDialogBuilder`): plan "Home cài đặt" (hạng 3 trong `Figma-Draft.md` mục 3) và các plan màn sau.
- Màn Onboarding, card theme có ảnh xem trước (1.1), thẻ thời tiết, lưới ảnh nền: thuộc plan màn tương ứng.
- **Cơ chế bo góc cho icon** (icon app `core/bitmap/IconShape.CORNER_RATIO` đang là hằng, thanh "Bo góc" màn 2.3): plan IS. Khi IS xong, xét cho vai `TILE` của DS3 đi theo cơ chế đó.
- Vệt sáng có blur ở góc card/nút (`v3/Highlight/*`): Task 2 chỉ vẽ bản xấp xỉ không blur; bản có blur để sau nếu chủ dự án thấy thiếu.
- Bóng của nét icon (`Core/Icon/Glyph Shadow`, đen 22% lệch 1 dp) và glow màu nhạt 10% của ô icon: bỏ.
- Bản dịch chuỗi `ds_` sang tiếng Anh: plan LG.
- Kính thật hay blur nền khi thành phần v3 nằm trên ảnh nền Home (vd hộp thoại mở trên Home): xét khi có màn cần.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `app/src/main/res/values/ds_colors.xml`, `ds_dimens.xml`, `ds_type.xml`, `ds_themes.xml`, `ds_attrs.xml`, `ds_styles.xml` | Tạo | Mới |
| `app/src/main/res/font/be_vietnam_pro_*.ttf`, `be_vietnam_pro.xml` | Tạo | Độc quyền |
| `app/src/main/res/drawable/ds_*.xml` | Tạo | Độc quyền |
| `app/src/main/java/gambi/global/os26launcher/core/designsystem/*.kt` | Tạo | Độc quyền (package mới) |
| `app/src/main/res/layout/ds_*.xml` | Tạo | Độc quyền |
| `app/src/main/res/values/strings.xml` | Sửa: thêm khối `ds_` ở cuối | Dùng chung (chỉ thêm) |
| `app/src/debug/AndroidManifest.xml` | Sửa: thêm một `<activity>` | Dùng chung (chỉ thêm) |
| `app/src/debug/java/gambi/global/os26launcher/debug/DsCatalogActivity.kt`, `app/src/debug/res/layout/activity_ds_catalog.xml` | Tạo | Độc quyền |
| `docs/third_party/BeVietnamPro-OFL.txt` | Tạo | Độc quyền |

Không sửa: `colors.xml`, `dimens.xml`, `themes.xml`, `AndroidManifest.xml` (main), mọi file `ui/*`, `core/glass/*`, `HomeActivity`.

## Global Constraints

- `minSdk = 26`: mọi API từ 28 trở lên phải bọc `Build.VERSION.SDK_INT`; `gradlew.bat lintDebug` không có lỗi `NewApi`.
- Không thêm dependency, không thêm test hay thư viện test.
- Tiền tố tài nguyên `ds_`; style tiền tố `Widget.Os26.Ds.` / `TextAppearance.Os26.Ds.` / `Theme.Os26Launcher.Ds`; attr tiền tố `ds`.
- Số đo lấy đúng từ bảng trong plan (đã chép từ Figma). Không làm tròn sang lưới 8 dp những số Figma cố ý lệch (14, 10, 6...): chúng là kích thước nội bộ của thành phần, không phải khoảng cách bố cục (xem "Quy ước khoảng cách").
- Vùng chạm tối thiểu **48 dp** cho mọi thành phần bấm được, kể cả khi hình nhìn thấy nhỏ hơn (chip 34, segment 30, nút quay lại 32, nút đóng 32, núm trượt 10).
- Chữ dùng `sp`; thành phần phải giãn theo cỡ chữ hệ thống, không cắt chữ.
- Chuỗi hiển thị tiếng Việt trong `strings.xml` khối `ds_`; mọi icon bấm được có `contentDescription`.
- Tổng dung lượng 4 file font ≤ **800 KB**; vượt thì dừng, báo chủ dự án (cân nhắc cắt bớt bảng mã).
- **Bo góc 4 vai luôn đi qua `DsCorner`/`DsCorners`** (quyết định 7): không thành phần nào đọc thẳng `ds_radius_card`, `ds_radius_card_large`, `ds_radius_sheet`, `ds_radius_tile`, không drawable XML nào dùng `<corners>` cố định cho 4 vai này. Bo của vai kính chỉ quy đổi qua `GlassTuning.cornerRadius` (một chỗ duy nhất, như mọi bề mặt kính của GT). Không thêm khóa cài đặt cho bo góc.
- Commit message tiếng Anh, theo `AGENTS.md`.

## Review Focus

1. **Cỡ chữ hệ thống lớn nhất (Live 4: Cài đặt > Hiển thị > Cỡ chữ lớn nhất):** hàng, nút, chip, badge, thanh điều hướng giãn cao, không cắt chữ, không chồng lên nhau. Kiểm ở Task 4, 6, 7 (bước kịch bản).
2. **Vùng chạm nhỏ hơn hình:** chip, segment, nút quay lại, nút đóng, núm trượt bấm trúng ở mép trong 48 dp. Kiểm bằng "Hiện ranh giới bố cục" (Developer options) ở Task 4, 5, 7.
3. **TalkBack:** công tắc, radio, segmented, thanh trượt, chỉ báo bước đọc đúng vai trò và trạng thái; hàng có công tắc đọc thành **một** mục. Kiểm ở Task 5, 6.
4. **Chữ dài:** tiêu đề hàng dài hai dòng khi có giá trị bên phải; giá trị dài thì cắt "…" chứ không đẩy chevron ra ngoài; nhãn nút dài cắt "…". Kiểm ở Task 4, 6 bằng chuỗi thử trong catalog.
5. **API 26–27:** không gọi API 28+ ngoài nhánh kiểm phiên bản (lint `NewApi`), nút chính vẫn hiện (không glow). Kiểm ở Task 2 và mỗi Task bằng lint.
6. **Bo góc đổi lúc chạy:** đổi thanh "Bo góc" của kính thì mọi thành phần vai kính đổi theo (ô icon và PILL không đổi), `clipToOutline` và gợn nhấn theo bo mới; view tách khỏi cửa sổ không còn listener (không rò). Kiểm ở Task 2 Step 9, Task 6, Task 7.

---

## Quy ước khoảng cách và bố cục (áp cho mọi màn v3)

Rút từ khung màn và thành phần Figma v3 (đọc 2026-11-16). Đây là luật cho các plan màn sau; Task 1 tạo đủ các `dimen` dưới đây.

**Thang khoảng cách bố cục** (lưới 8 dp, thêm bước 4 và 12, 20): `ds_space_4` 4 · `ds_space_8` 8 · `ds_space_12` 12 · `ds_space_16` 16 · `ds_space_20` 20 · `ds_space_24` 24 · `ds_space_32` 32. Margin/padding **giữa** các khối luôn lấy từ thang này. Số lẻ (2, 3, 6, 10, 14) chỉ xuất hiện **bên trong** thành phần, dưới tên riêng của thành phần.

| Quy ước | Giá trị | `dimen` |
|---|---|---|
| Lề ngang màn hình | 16 | `ds_screen_margin_h` |
| Đệm trên nội dung (dưới thanh điều hướng) | 8 | `ds_screen_content_top` |
| Đệm dưới nội dung (cộng thêm inset thanh hệ thống) | 32 | `ds_screen_content_bottom` |
| Khoảng giữa hai nhóm (section) | 20 | `ds_section_gap` |
| Tiêu đề nhóm → card | 12 | `ds_section_header_gap` |
| Bo góc card danh sách | 20 (gốc, nhân theo thanh "Bo góc" của kính) | `ds_radius_card` · vai `DsCornerRole.CARD` |
| Bo góc card nổi bật (card theme, card launcher) | 24 (gốc, nhân theo thanh "Bo góc" của kính) | `ds_radius_card_large` · vai `DsCornerRole.CARD_LARGE` |
| Bo góc hộp thoại, bottom sheet | 28 (gốc, nhân theo thanh "Bo góc" của kính) | `ds_radius_sheet` · vai `DsCornerRole.SHEET` |
| Bo góc ô icon | 8 (cố định tới khi có cơ chế bo icon, IS) | `ds_radius_tile` · vai `DsCornerRole.TILE` |
| Đệm trong card có nội dung tự do | 12 | `ds_card_padding` |
| Hàng: cao tối thiểu | 56 | `ds_row_min_height` |
| Hàng: đệm đầu (trước ô icon/radio) | 12 | `ds_row_padding_start` |
| Hàng: đệm cuối | 12 | `ds_row_padding_end` |
| Hàng: đệm dọc phần chữ | 10 | `ds_row_padding_v` |
| Hàng: khe giữa các phần (ô icon, chữ, phần cuối) | 12 | `ds_row_gap` |
| Đường kẻ giữa hàng | 1, bắt đầu từ mép trái phần chữ (sau ô icon), chạy hết mép phải | `ds_divider` |
| Ô icon / icon | 32 / 24 | `ds_tile_size` / `ds_icon_size` |
| Vùng chạm tối thiểu | 48 | `ds_touch_min` |
| Khung footer nút cố định đáy màn | đệm 16/16/28/16, khe 8 | `ds_footer_padding_*` |

---

### Task 1: Token, font, theme và khung catalog

**Bắt buộc kiểm tay:** có — font và màu chỉ đánh giá được bằng mắt trên máy (`Workflow.md` mục 3).

**Files:**
- Create: `res/values/ds_colors.xml`, `res/values/ds_dimens.xml`, `res/values/ds_type.xml`, `res/values/ds_themes.xml`, `res/font/be_vietnam_pro_light.ttf`, `be_vietnam_pro_regular.ttf`, `be_vietnam_pro_semibold.ttf`, `be_vietnam_pro_bold.ttf`, `res/font/be_vietnam_pro.xml`, `docs/third_party/BeVietnamPro-OFL.txt`
- Create: `src/debug/java/.../debug/DsCatalogActivity.kt`, `src/debug/res/layout/activity_ds_catalog.xml`
- Modify: `src/debug/AndroidManifest.xml` (thêm activity)

**Interfaces:**
- Produces: màu `@color/ds_*`, kích thước `@dimen/ds_*`, `@font/be_vietnam_pro`, text appearance `TextAppearance.Os26.Ds.*`, theme `Theme.Os26Launcher.Ds`; `DsCatalogActivity` có `fun addSection(title: String, content: View)` để Task sau thêm mục.

- [x] **Step 1: Claude xuất ảnh tham chiếu.** Chủ dự án mở Figma v3, chạy plugin bridge. Claude gọi `save_screenshots` cho các node `302:3331` … `312:2699` (15 thành phần ở `Figma-Draft.md` mục 2) và frame `302:1856` (màn 1.3, có nền aurora + ô tìm), scale 3, vào `build/figma-v3/components/`. Kết quả: 16 file PNG.
- [x] **Step 2: Nhập màu** vào `ds_colors.xml` đúng bảng sau (ARGB; % là độ mờ trong Figma). Nguồn: Figma paint styles, đọc 2026-11-16.

| Tên | Giá trị | Nguồn Figma |
|---|---|---|
| `ds_bg_base` | `#FF07080E` | nền mọi frame v3 |
| `ds_on_light` | `#FF0B1020` | `Core/Background/Base` (chữ trên nền trắng: chip/segment chọn) |
| `ds_text_primary` | `#FFF5F7FF` | `Core/Text/Primary` |
| `ds_text_secondary` | `#ADF5F7FF` | `Core/Text/Secondary` (68%) |
| `ds_text_tertiary` | `#79F5F7FF` | nút "Bỏ qua" (68% × layer 70%) |
| `ds_surface_border` | `#24FFFFFF` | `Core/Surface/Border` (14%) |
| `ds_surface_control` | `#2EFFFFFF` | `Core/Surface/Control` (18%): ray trượt, công tắc tắt, nút đóng, tay nắm sheet |
| `ds_surface_fill` | `#330F121C` | lớp đáy card/control (20%) |
| `ds_surface_sheet` | `#F0181E3A` | `Core/Surface/Sheet` (94%) |
| `ds_scrim` | `#99030614` | `Core/Surface/Scrim` (60%) |
| `ds_divider` | `#1AF5F7FF` | đường kẻ hàng (10%, hai đầu mờ về 0) |
| `ds_accent` | `#FF4370FF` | `Core/Accent/Solid` |
| `ds_accent_light` | `#FF6E95FF` | `Core/Accent/Light` (viền núm trượt) |
| `ds_primary_start` / `ds_primary_end` | `#FF3B57E8` / `#FF6440E6` | `v2/Button/Primary` |
| `ds_primary_glow` | `#FF5A4BF5` | `v3/Button/Glow` |
| `ds_aurora_blue` / `_purple` / `_cyan` / `_pink` | `#FF4F6DFF` / `#FF9B5CFF` / `#FF00C2B8` / `#FFFF7AB6` | `Core/Aurora/*` |
| `ds_status_warning` / `ds_status_danger` | `#FFFFB74D` / `#FFFF4D4F` | `Core/Status/*` |
| `ds_status_warning_text` / `ds_status_danger_text` | `#FFFFD08A` / `#FFFF8A8A` | `Core/Status/* Text` |
| `ds_badge_base` / `ds_tile_base` | `#8C171A27` / `#D9171A27` | đáy badge (55%) / đáy ô icon (85%) |
| `ds_tile_<màu>` (10) | blue `#FF5B8CFF` · violet `#FFA78BFA` · sky `#FF38BDF8` · amber `#FFF5B83D` · green `#FF34D399` · teal `#FF2DD4BF` · orange `#FFFB923C` · pink `#FFF472B6` · red `#FFF87171` · slate `#FF94A3B8` | `v2/Tile/Fill/*` |
| `ds_glyph_<màu>` (10) | blue `#FF7AA2FF` · violet `#FFB9A3FB` · sky `#FF7DD3FC` · amber `#FFFDE68A` · green `#FF4ADE80` · teal `#FF2DD4BF` · orange `#FFFDBA74` · pink `#FFF9A8D4` · red `#FFFCA5A5` · slate `#FFB4C0D3` | `Core/Icon Glyph/*` |

- [x] **Step 3: Nhập kích thước** vào `ds_dimens.xml`: toàn bộ bảng "Quy ước khoảng cách" ở trên, cộng kích thước nội bộ thành phần liệt kê trong Task 2–7 (Claude thêm khi làm Task đó, cùng file).
- [x] **Step 4: Font.** Tải `BeVietnamPro-Light.ttf`, `-Regular`, `-SemiBold`, `-Bold` và `OFL.txt` từ `https://github.com/google/fonts/tree/main/ofl/bevietnampro`; đổi tên chữ thường gạch dưới; `be_vietnam_pro.xml` là `<font-family>` 4 mục với `android:fontWeight` 300/400/600/700 và `app:fontWeight` tương ứng (AppCompat cho API 26–27). Ghi tổng dung lượng 4 file vào Nhật ký (ràng buộc ≤ 800 KB).
- [x] **Step 5: Kiểu chữ** trong `ds_type.xml`, mỗi style `parent="TextAppearance.Material3.BodyMedium"`, đặt `android:fontFamily="@font/be_vietnam_pro"`, `android:textFontWeight`, `fontWeight` (AppCompat), `android:textSize`, `lineHeight` (bằng 120% cỡ chữ, trừ chỗ ghi khác), `android:letterSpacing`, `android:textColor`:

| Style | Cỡ / độ đậm | Line height | Màu | Figma |
|---|---|---|---|---|
| `TextAppearance.Os26.Ds.HeadingLarge` | 26sp / 700 | 34sp, letterSpacing −0.0115 | primary | `Heading/Large` |
| `.Title` | 20sp / 700 | 24sp | primary | `Heading/Title` |
| `.Card` | 16sp / 700 | 19sp | primary | `Heading/Card` |
| `.Body` | 14sp / 400 | 17sp | primary | `Body/Default` |
| `.BodyStrong` | 14sp / 600 | 17sp | primary | `Body/Strong` |
| `.Value` | 13sp / 400 | 16sp | secondary | `Body/Value` |
| `.Caption` | 12sp / 400 | 14sp | secondary | `Caption` |
| `.CaptionSmall` | 11sp / 400 | 13sp | secondary | `Caption/Small` |
| `.Badge` | 11sp / 600 | 13sp | (đặt theo loại badge) | `Label/Badge` |
| `.Display` | 40sp / 300 | 40sp | primary | `Display/Number` |
| `.SectionHeader` | 14sp / 600 | 17sp | secondary | tiêu đề nhóm ("Chung", "Giao diện") |

- [x] **Step 6: Theme** `Theme.Os26Launcher.Ds` trong `ds_themes.xml`, `parent="Theme.Material3.Dark.NoActionBar"` (luôn tối, quyết định 6): `android:windowBackground=@color/ds_bg_base`, `colorPrimary=@color/ds_accent`, `colorOnPrimary=@color/ds_text_primary`, `colorSurface=@color/ds_bg_base`, `colorOnSurface=@color/ds_text_primary`, `colorOnSurfaceVariant=@color/ds_text_secondary`, `colorError=@color/ds_status_danger`, `android:fontFamily` và `fontFamily=@font/be_vietnam_pro`, `android:statusBarColor` và `android:navigationBarColor` trong suốt, `android:windowLightStatusBar=false`, `android:windowLightNavigationBar=false` (API 27+, đặt trong `values-v27/ds_themes.xml`).
- [x] **Step 7: Khung catalog.** `DsCatalogActivity : AppCompatActivity` (không Hilt), theme `Theme.Os26Launcher.Ds`, nội dung `ScrollView` > `LinearLayout` dọc có đệm theo quy ước màn; `fun addSection(title: String, content: View)` thêm tiêu đề `.SectionHeader` + nội dung, cách nhau `ds_section_gap`. Edge-to-edge: đệm theo inset `systemBars` như `AppLibraryActivity`. Mục đầu tiên: bảng mẫu màu (ô 40 dp + tên) cho mọi màu ở Step 2 và một dòng chữ mẫu "Màn hình chính mặc định — 9:41" cho mỗi kiểu chữ ở Step 5. Khai trong `src/debug/AndroidManifest.xml`: `<activity android:name="gambi.global.os26launcher.debug.DsCatalogActivity" android:exported="true" android:screenOrientation="nosensor" android:theme="@style/Theme.Os26Launcher.Ds" />` kèm chú thích "CHỈ BẢN DEBUG".
- [x] **Step 8: Build và lint.** `gradlew.bat assembleDebug lintDebug` → `BUILD SUCCESSFUL`, báo cáo lint không có `NewApi`. `gradlew.bat assembleRelease` → APK release không có `DsCatalogActivity` (kiểm: `aapt2 dump xmltree --file AndroidManifest.xml app\build\outputs\apk\release\app-release.apk | findstr DsCatalog` không ra gì).
- [x] **Step 9: Kịch bản kiểm (Live 4).** Mở `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.DsCatalogActivity`. (a) Chữ là Be Vietnam Pro (chữ "ư", "ơ", "ạ" có dấu đẹp, không phải Roboto); (b) 4 độ đậm phân biệt được; (c) màu mẫu khớp ảnh `build/figma-v3/` theo mắt; (d) thanh trạng thái trong suốt, chữ trạng thái màu sáng; (e) đặt máy giao diện sáng: catalog vẫn tối.
- [x] **Step 10: Commit** `feat: add v3 design tokens, font and debug catalog`.

---

### Task 1b: Bo góc bề mặt kính theo thanh "Bo góc" của kính (GT)

**Bắt buộc kiểm tay:** không — kiểm bằng mục "Bo góc" của catalog (số đọc ra) ngay ở Task này, bằng mắt ở Task 2 Step 9.

**Files:**
- Create: `core/designsystem/DsCorner.kt`, `core/designsystem/DsCorners.kt`
- Modify: `res/values/ds_dimens.xml` (chú thích 4 bo góc), `src/debug/.../debug/DsCatalogActivity.kt` (mục "Bo góc")

**Interfaces:**
- Consumes: `ds_radius_*` Task 1; `core/glass/GlassEnvironment` (`from`, `look`, `addListener`/`removeListener`, chạy trên main thread); `core/glass/GlassTuning.cornerRadius(look, basePx, width, height)` (GT).
- Produces:
  - `enum class DsCornerRole(isGlass: Boolean) { CARD(true), CARD_LARGE(true), SHEET(true), TILE(false) }`
  - `sealed interface DsCorner { data object Pill; data class Role(val role: DsCornerRole); data class Fixed(val dp: Float) }`
  - `object DsCorners` — `fun basePx(context: Context, role: DsCornerRole): Float` (bán kính gốc từ `ds_radius_*`); `fun radiusPx(context: Context, corner: DsCorner, widthPx: Int, heightPx: Int): Float`; `fun addListener(context: Context, listener: () -> Unit)` / `removeListener` (chuyển tiếp sang `GlassEnvironment`, gọi trên main thread).

- [x] **Step 1: `DsCorner` + `DsCornerRole`.** Đặt ở `core/designsystem` (tầng `core` được dùng `core/glass`, không chạm `domain`/`data`).
- [x] **Step 2: `DsCorners.radiusPx`.** `Pill` → `min(width, height) / 2` (cỡ chưa biết thì 0, drawable tính lại ở `onBoundsChange`); `Role` có `isGlass` → `GlassTuning.cornerRadius(GlassEnvironment.from(context).look, basePx, width, height)` (đã tự kẹp nửa cạnh ngắn khi biết cỡ; ở mặc định 50 trả đúng số gốc); `Role(TILE)` → `basePx` (cố định, quyết định 7); `Fixed` → dp ra px. Không cấp phát, gọi được trong `onBoundsChange`.
- [x] **Step 3: Listener.** `addListener`/`removeListener` chuyển tiếp sang `GlassEnvironment` (listener đó báo mỗi khi cấu hình kính đổi, kể cả mức kính; làm mới bo thừa vài lần là rẻ). Thành phần dùng qua `applyDsSurface` ở Task 2, không tự đăng ký.
- [x] **Step 4: Catalog** mục "Bo góc": 4 dòng `CARD gốc 20 dp → hiện 20 dp`, ... (đổi px ra dp, cỡ khung chưa biết nên không kẹp), tự làm mới qua `DsCorners.addListener` (đăng ký ở `onStart`, gỡ ở `onStop`). Không có thanh trượt thử: đổi bằng thanh "Bo góc" thật trong Cài đặt của launcher → Liquid Glass → Tinh chỉnh kính.
- [x] **Step 5: Kịch bản kiểm (Live 4).** (a) Mở catalog: mục "Bo góc" hiện CARD 20, CARD_LARGE 24, SHEET 28, TILE 8 khi thanh "Bo góc" ở giữa (50); (b) vào Cài đặt của launcher → Liquid Glass → Tinh chỉnh kính, kéo "Bo góc" về 0 rồi mở lại catalog: CARD/CARD_LARGE/SHEET hiện 0, TILE vẫn 8; kéo lên tối đa: 40/48/56, TILE vẫn 8; (c) trả thanh về giữa: về 20/24/28/8; (d) Home không đổi gì so với trước Task này (Task này không sửa file nào của Home).

---

### Task 2: Bề mặt kính, nền aurora, đường kẻ

**Bắt buộc kiểm tay:** có — mức giống Figma của bản xấp xỉ (quyết định 3) chỉ đánh giá được bằng mắt.

**Files:**
- Create: `core/designsystem/DsSurface.kt`, `core/designsystem/DsSurfaceDrawable.kt`, `core/designsystem/AuroraBackgroundDrawable.kt`, `core/designsystem/DsDividerDrawable.kt`, `res/values/ds_ids.xml`

**Interfaces:**
- Consumes: màu Task 1; `DsCorner`, `DsCornerRole`, `DsCorners` Task 1b.
- Produces:
  - `enum class DsSurface(followsGloss: Boolean, darkPress: Boolean) { CARD, CONTROL, CONTROL_SELECTED, SHEET, PRIMARY, BADGE_WARNING, BADGE_DANGER, BADGE_NEW }`
  - `class DsSurfaceDrawable(context: Context, surface: DsSurface, corner: DsCorner = DsCorner.Pill) : Drawable` — bo tính lại bằng `DsCorners.radiusPx(context, corner, bounds.width(), bounds.height())` trong `onBoundsChange` và khi `setCorner(corner)`/`refreshCorner()` được gọi; hỗ trợ `setState` cho pressed (xem Step 3).
  - `fun View.applyDsSurface(surface: DsSurface, corner: DsCorner = DsCorner.Pill)` — đặt background, `clipToOutline` theo đúng bo góc, và với `PRIMARY` thì `elevation` + màu bóng (Step 4). Với `DsCorner.Role` **hoặc bề mặt theo độ bóng** (quyết định 8) thì tự đăng ký listener của `DsCorners` ở `onViewAttachedToWindow`, gỡ ở `onViewDetachedFromWindow` (`View.OnAttachStateChangeListener`); đổi thì `refreshCorner()` + `invalidateOutline()`. Thành phần khác không tự viết lại phần này.
  - `class AuroraBackgroundDrawable(context: Context) : Drawable`
  - `class DsDividerDrawable(context: Context) : Drawable` (cao 1 dp; thay `@drawable/ds_divider`, xem Step 6)

- [x] **Step 1: Bảng lớp của từng bề mặt** trong `DsSurface` (thứ tự vẽ từ dưới lên). Màu kèm % là độ mờ của điểm dừng gradient; "dọc" = từ trên xuống, "ngang" = trái sang phải. Nguồn: component Figma đọc 2026-11-16.

| Bề mặt | Lớp fill | Lớp phủ viền (gradient dọc, xấp xỉ inner shadow) | Viền |
|---|---|---|---|
| `CARD` | `ds_surface_fill`; radial tâm giữa trắng 0% → 1% (0,65) → 5% (1,0); dọc trắng 3,5% → 0% (0,4) | trắng 12% ở mép trên mờ về 0 sau 3 dp; đen 8% ở mép dưới mờ về 0 sau 18 dp | — |
| `CONTROL` | giống `CARD` | trắng 22% ở mép trên mờ về 0 sau 2 dp | — |
| `CONTROL_SELECTED` | `ds_text_primary` đặc | như `CONTROL` | — |
| `SHEET` | `ds_surface_sheet` | như `CARD` | — |
| `PRIMARY` | ngang `ds_primary_start` → `ds_primary_end`; dọc trắng 18% → 5% (0,3) → 0% (0,55) → đen 10% (1,0) | trắng 30% ở mép trên mờ về 0 sau 3 dp; đen 12% ở mép dưới mờ về 0 sau 8 dp | — |
| `BADGE_*` | `ds_badge_base`; dọc màu trạng thái 26% → 12% | trắng 22% ở mép trên mờ về 0 sau 2 dp | 0,5 dp, gradient dọc trắng 19% → màu 7% (0,5) → màu 21% |

Màu trạng thái của badge: WARNING `ds_status_warning`, DANGER `ds_status_danger`, NEW `ds_accent`.

**Độ bóng (quyết định 8):** với `CARD`, `CONTROL`, `SHEET`, màu nền đặc (`ds_surface_fill` / `ds_surface_sheet`) đi qua `GlassTuning.tint(look, màu)`, màu viền sáng mép trên đi qua `GlassTuning.border(look, màu)`; đọc `GlassEnvironment.look` mỗi lần dựng lại shader. Ở `gloss` 50 cả hai trả đúng màu gốc.

- [x] **Step 2: `DsSurfaceDrawable.draw`** vẽ các lớp bằng `canvas.drawRoundRect` với `Shader` (`LinearGradient`, `RadialGradient`), dựng lại shader trong `onBoundsChange`, không cấp bitmap. Viền badge dùng `Paint.Style.STROKE` lùi vào 0,25 dp. `getOutline` trả `setRoundRect` cùng bo góc. `getOpacity` = `PixelFormat.TRANSLUCENT`.
- [x] **Step 3: Trạng thái** (quyết định 5, suy ra): pressed = phủ thêm trắng 8% (bề mặt tối) hoặc đen 8% (`CONTROL_SELECTED`, `PRIMARY`); disabled do view đặt `alpha = 0.4` (Task 4), drawable không xử lý. `isStateful = true`, đổi phủ khi `onStateChange` thấy `state_pressed`.
- [x] **Step 4: Glow nút chính** trong `applyDsSurface(PRIMARY)`: `elevation = 8dp`; API ≥ 28 đặt `outlineSpotShadowColor` và `outlineAmbientShadowColor` = `ds_primary_glow` (alpha 32%, tức `#525A4BF5`); API 26–27 đặt `elevation = 0` (không bóng đen thay glow).
- [x] **Step 5: `AuroraBackgroundDrawable`**: nền `ds_bg_base`, 4 `RadialGradient` (màu → trong suốt), tâm và độ mờ theo khung 360 × 800 của Figma, quy đổi theo tỉ lệ bề rộng/bề cao bounds:

| Đốm | Tâm (x, y) trong 360 × 800 | Độ mờ tâm (điểm xuất phát) | Bán kính |
|---|---|---|---|
| `ds_aurora_blue` | (0, 100) | 48% | 300 dp × (rộng/360) |
| `ds_aurora_purple` | (360, 300) | 42% | như trên |
| `ds_aurora_cyan` | (0, 500) | 28% | như trên |
| `ds_aurora_pink` | (360, 700) | 24% | như trên |

Độ mờ và bán kính là điểm xuất phát (Figma: ellipse 168 × 268 blur 170, độ mờ lớp 0,24–0,48); Claude chỉnh để khớp `build/figma-v3/` frame `302:1856` theo mắt, ghi số cuối vào Nhật ký.
- [x] **Step 6: `DsDividerDrawable`**: cao 1 dp, `LinearGradient` ngang 4 điểm `#00F5F7FF` (0) → `ds_divider` (0,12) → `ds_divider` (0,88) → `#00F5F7FF` (1). Đổi từ `ds_divider.xml` (lúc code): `<gradient>` XML chỉ có 3 điểm nên không tạo được đoạn bằng ở giữa, `layer-list` không đặt được bề rộng theo %; Task 6 gán drawable này bằng code.
- [x] **Step 7: Catalog** mục "Bề mặt" và mục "So với mặc định" (mỗi bề mặt theo kính một cặp khối "Hiện tại" / "Mặc định", khối mặc định dùng `DsSurfaceDrawable(..., followGlassSettings = false)`, cộng dòng số Độ bóng/Bo góc đang đặt). Mục "Bề mặt": nền catalog đổi sang `AuroraBackgroundDrawable` (đặt cho `window`), mỗi bề mặt một khối 328 × 120 (CARD với `Role(CARD)` và `Role(CARD_LARGE)`, SHEET với `Role(SHEET)`) và 160 × 48 (CONTROL, CONTROL_SELECTED, PRIMARY bo PILL), 3 badge 46 × 20, một card có 3 đường kẻ.
- [x] **Step 8: Build và lint** như Task 1 Step 8.
- [x] **Step 9: Kịch bản kiểm (Live 4).** Độ bóng (quyết định 8): (g) kéo "Độ bóng" ở Tinh chỉnh kính lên tối đa, mở lại catalog, xem mục "So với mặc định": khối "Hiện tại" của CARD/SHEET/CONTROL sáng hơn khối "Mặc định" bên cạnh, mép trên sáng mạnh hơn; kéo về 0: khối "Hiện tại" tối hơn, mép trên mất; dòng số trên cùng mục khớp giá trị vừa đặt; khối PRIMARY, SELECTED, badge ở mục "Bề mặt" không đổi. (So trong một màn: không xem được màn Cài đặt và catalog cùng lúc.) Chụp `adb exec-out screencap -p` (Claude nếu phiên có `adb`, không thì chủ dự án) mục Bề mặt, đặt cạnh ảnh Figma `302:1856` và các thành phần. Chủ dự án kiểm: (a) card đọc được là "kính tối" trên nền aurora, mép trên sáng nhẹ; (b) nút chính có glow tím; (c) đốm aurora đúng vị trí và độ đậm; (d) nhấn giữ khối CONTROL/PRIMARY thấy đổi sáng/tối. **Chủ dự án quyết luôn Q1 tại bước này** nếu bản xấp xỉ không đạt. Bo góc (Task 1b): (e) đổi thanh "Bo góc" ở Cài đặt của launcher → Liquid Glass → Tinh chỉnh kính rồi mở lại catalog, xem mục "So với mặc định": khối "Hiện tại" của CARD/SHEET bo khác khối "Mặc định" bên cạnh, viền sáng mép trên và vùng clip theo bo mới; hàng CONTROL (PILL) hai khối giống nhau; (f) `adb shell am force-stop gambi.global.os26launcher` rồi mở lại catalog: giữ bo đã chọn, không thấy khung đầu bo gốc rồi nhảy.
- [x] **Step 10: Commit** `feat: add v3 glass surfaces and aurora background`.

---

### Task 3: Bộ icon và ô icon

**Bắt buộc kiểm tay:** không (kiểm bằng ảnh chụp catalog ở bài kiểm cuối).

**Files:**
- Create: `res/drawable/ds_ic_<tên>.xml` (24 file), `core/designsystem/DsTileColor.kt`, `core/designsystem/DsIconTileView.kt`, `core/designsystem/DsTileDrawable.kt`, `res/values/ds_attrs.xml` (khối `DsIconTileView`)

**Interfaces:**
- Produces:
  - Drawable 24 dp, nét trắng, tô màu bằng `tint`: `ds_ic_home`, `ds_ic_general`, `ds_ic_language`, `ds_ic_weather`, `ds_ic_location`, `ds_ic_liquid_glass`, `ds_ic_wallpaper`, `ds_ic_icon_pack`, `ds_ic_page_scroll`, `ds_ic_app_library`, `ds_ic_search`, `ds_ic_badge`, `ds_ic_transition`, `ds_ic_app_animation`, `ds_ic_hidden_apps`, `ds_ic_change_icon`, `ds_ic_rename`, `ds_ic_rate`, `ds_ic_more_apps`, `ds_ic_privacy`, `ds_ic_chevron_right`, `ds_ic_chevron_left`, `ds_ic_close`, `ds_ic_search_field`
  - `enum class DsTileColor(@ColorRes val tint: Int, @ColorRes val glyph: Int) { BLUE, VIOLET, SKY, AMBER, GREEN, TEAL, ORANGE, PINK, RED, SLATE }`
  - `class DsTileDrawable(context: Context, color: DsTileColor) : Drawable`
  - `class DsIconTileView : AppCompatImageView` — `fun setTile(@DrawableRes icon: Int, color: DsTileColor)`; attr `dsTileIcon` (reference), `dsTileColor` (enum cùng tên, chữ thường)

- [x] **Step 1: Xuất SVG.** Claude (chủ dự án mở Figma + plugin) gọi `get_screenshot` `format: "SVG"` cho 20 node icon `302:3410` (home), `302:3414` (general), `302:3419` (language), `302:3423` (weather), `302:3427` (location), `302:3431` (liquid-glass), `302:3435` (wallpaper), `302:3440` (icon-pack), `302:3446` (page-scroll), `302:3450` (app-library), `302:3459` (search), `302:3463` (badge), `302:3467` (transition), `302:3471` (app-animation), `302:3474` (hidden-apps), `302:3477` (change-icon), `302:3481` (rename), `302:3484` (rate), `302:3487` (more-apps), `302:3490` (privacy) và 4 icon phụ: chevron phải `302:3370`, chevron trái `312:2677`, đóng `312:2687`, kính lúp ô tìm `302:1886`; lưu vào `build/figma-v3/icons/<tên>.svg`.
- [x] **Step 2: Đổi SVG sang VectorDrawable.** Nếu máy có `node` (`node -v`): `npx svg2vectordrawable@2 -i build\figma-v3\icons -o build\figma-v3\vd`; không thì Android Studio > New > Vector Asset > Local file từng file. Sửa tay mỗi file: `android:width/height="24dp"`, `viewportWidth/Height="24"`, mọi `strokeColor`/`fillColor` có màu đổi thành `#FFFFFFFF` (để `tint` tô), giữ `strokeWidth` gốc (1,4 cho icon nhóm, 1,5 cho chevron), bỏ lớp bóng nếu SVG có `filter`. Chép vào `res/drawable/ds_ic_<tên>.xml`.
- [x] **Step 3: `DsTileDrawable`** (ô 32, bo `DsCorner.Role(DsCornerRole.TILE)` mặc định 8, nguồn `v2/Tile/Fill/*`): `ds_tile_base`; gradient chéo (trên trái → dưới phải) màu tint 14% → 3%; radial tâm (0,5; 0,08) bán kính 0,83 × cạnh, màu tint 20% → 0; radial tâm (0,7; 0,88) bán kính cạnh, trắng 12% → 0; lớp phủ viền dọc trắng 30% ở mép trên mờ về 0 sau 1 dp, đen 25% ở mép dưới mờ về 0 sau 4 dp. Không bóng ngoài.
- [x] **Step 4: `DsIconTileView`**: kích thước cố định `ds_tile_size`, đệm 4 dp, nền `DsTileDrawable`, ảnh 24 dp tô `glyph` của màu; `importantForAccessibility="no"` mặc định (ô icon chỉ trang trí, hàng đọc tiêu đề).
- [x] **Step 5: Catalog** mục "Icon": lưới 20 ô icon với màu theo trang chủ Cài đặt của Figma — Màn hình chính mặc định BLUE · Chung SLATE · Ngôn ngữ SKY · Thời tiết AMBER · Vị trí GREEN · Liquid Glass VIOLET · Hình nền PINK · Bộ biểu tượng ORANGE · Lướt màn hình TEAL · Thư viện ứng dụng BLUE · Cài đặt tìm kiếm SLATE · Huy hiệu thông báo RED · Hiệu ứng chuyển cảnh VIOLET · Hiệu ứng ứng dụng AMBER · Ứng dụng ẩn SLATE · Thay đổi biểu tượng PINK · Thay đổi tên ORANGE · Đánh giá AMBER · Tải ứng dụng khác BLUE · Chính sách bảo mật GREEN; thêm 4 icon phụ tô `ds_text_primary`. Ghi bảng ánh xạ này thành KDoc của `DsTileColor` để plan Home cài đặt dùng.
- [x] **Step 6: Build, lint, commit** `feat: import v3 icon set and icon tile`.

---

### Task 4: Nút, badge, chip, segmented, ô tìm kiếm

**Bắt buộc kiểm tay:** có — vùng chạm và cỡ chữ lớn (Review Focus 1, 2, 4).

**Files:**
- Create: `res/values/ds_styles.xml`, `core/designsystem/DsButton.kt`, `core/designsystem/DsBadgeView.kt`, `core/designsystem/DsChipView.kt`, `core/designsystem/DsSegmentedControl.kt`, `res/layout/ds_search_field.xml`, `core/designsystem/DsSearchField.kt`; thêm khối vào `ds_attrs.xml`

**Interfaces:**
- Consumes: `DsSurface`, `applyDsSurface`, `ds_ic_search_field`.
- Produces:
  - `class DsButton : AppCompatButton` — attr `dsButtonVariant` (`primary` | `secondary`), `dsButtonSize` (`l` | `m` | `s`); `isEnabled = false` → `alpha 0.4`
  - `class DsBadgeView : AppCompatTextView` — attr `dsBadgeType` (`warning` | `danger` | `new_item`)
  - `class DsChipView : AppCompatTextView` — `isSelected` đổi bề mặt, màu chữ, độ đậm
  - `class DsSegmentedControl : LinearLayout` — `fun setOptions(labels: List<CharSequence>)`, `var selectedIndex: Int`, `fun setOnSelectedChangeListener(listener: ((Int) -> Unit)?)` (chỉ gọi khi người dùng chọn)
  - `class DsSearchField : FrameLayout` — `val editText: EditText`, attr `android:hint`

- [x] **Step 1: `DsButton`** (nguồn component `Button` `302:3350`): bề mặt `PRIMARY` hoặc `CONTROL`, bo PILL; chữ `.BodyStrong` màu primary, một dòng, cắt "…" ở cuối; khe icon–chữ 8.

| Cỡ | Cao nhìn thấy | Đệm ngang | Vùng chạm |
|---|---|---|---|
| L | 48 | 20 | 48 |
| M | 40 | 16 | 48 (inset dọc 4) |
| S | 34 | 14 | 48 (inset dọc 7) |

Inset dọc làm bằng `InsetDrawable` bọc nền; `minHeight` của view = 48 dp; bỏ `stateListAnimator` mặc định của AppCompat.
- [x] **Step 2: `DsBadgeView`** (`302:3355`): cao 20, đệm 3/8, chữ `.Badge`; màu chữ WARNING `ds_status_warning_text`, DANGER `ds_status_danger_text`, NEW `ds_text_primary`; bề mặt `BADGE_*`. Không bấm được.
- [x] **Step 3: `DsChipView`** (`302:3339`): cao nhìn thấy 34, vùng chạm 48, đệm ngang 14, bo PILL; chọn: `CONTROL_SELECTED`, chữ `ds_on_light` 600; thường: `CONTROL`, chữ primary 400. Đổi độ đậm trong `setSelected` (đổi `typeface` giữa hai weight của `@font/be_vietnam_pro`). Trợ năng: `isCheckable`, `isChecked = isSelected` trong `onInitializeAccessibilityNodeInfo`.
- [x] **Step 4: `DsSegmentedControl`** (`312:2671` + `302:3344`): khung bề mặt `CONTROL` bo PILL, đệm 3, khe 2, cao nhìn thấy 36 (vùng chạm 48: khung có inset dọc 6); mỗi lựa chọn cao 30, đệm ngang 14; chọn: nền `ds_text_primary` bo PILL, chữ `ds_on_light` 600; thường: không nền, chữ primary 400. Mỗi lựa chọn chia đều bề rộng khi khung `match_parent`, ôm chữ khi `wrap_content`. Trợ năng: mỗi lựa chọn báo `className = RadioButton`, `isChecked`. Đổi lựa chọn không có hoạt ảnh.
- [x] **Step 5: `DsSearchField`** (từ ô tìm của màn 1.3, Figma không có component — quyết định 5): cao 44 (vùng chạm đã ≥ 44; khung đặt `minHeight` 48 với inset dọc 2), bo PILL (= 22 ở cao 44), bề mặt `CONTROL`, đệm ngang 14, khe 8; icon `ds_ic_search_field` 24 tô `ds_text_secondary`; `EditText` không nền, chữ `.Body`, hint màu `ds_text_secondary`, `imeOptions="actionSearch"`, một dòng; con trỏ màu `ds_accent`.
- [x] **Step 6: Catalog** mục "Nút & chọn": 6 nút (2 kiểu × 3 cỡ) + 1 nút tắt; nút có nhãn dài "Áp dụng & đặt làm mặc định cho mọi màn hình" ở bề rộng 160 dp (phải cắt "…"); 3 badge; 3 chip (1 chọn) bấm đổi chọn được; segmented "Chậm/Vừa/Nhanh" (chọn Vừa) và segmented 2 lựa chọn `match_parent`; ô tìm "Tìm ngôn ngữ".
- [x] **Step 7: Build, lint.**
- [x] **Step 8: Kịch bản kiểm (Live 4).** (a) Bật "Hiện ranh giới bố cục": nút M/S, chip, segmented có hộp 48 dp; bấm sát mép trên/dưới hộp vẫn ăn; (b) cỡ chữ lớn nhất: nút giãn cao, chữ không bị cắt dọc; nhãn dài cắt "…"; (c) chip/segmented đổi chọn đúng, chữ chọn đậm hơn; (d) TalkBack trên segmented đọc "Vừa, đã chọn, 2 trong 3" (hoặc tương đương của hệ thống); (e) gõ được vào ô tìm, bàn phím có nút tìm.
- [x] **Step 9: Commit** gộp với Task 5 (chủ dự án chốt 2026-11-16).

---

### Task 5: Công tắc, radio, thanh trượt, tiến độ, chỉ báo bước

**Bắt buộc kiểm tay:** có — TalkBack và kéo thanh trượt (Review Focus 2, 3).

**Files:**
- Create: `res/drawable/ds_switch_track.xml`, `ds_switch_thumb.xml`, `ds_radio.xml`, `ds_slider_progress.xml`, `ds_slider_thumb.xml`, `res/layout/ds_slider_row.xml`, `core/designsystem/DsSliderRow.kt`, `core/designsystem/DsStepIndicatorView.kt`; thêm style vào `ds_styles.xml`, khối vào `ds_attrs.xml`

**Interfaces:**
- Produces:
  - style `Widget.Os26.Ds.Switch` (cho `SwitchCompat`), `Widget.Os26.Ds.Radio` (cho `AppCompatRadioButton`), `Widget.Os26.Ds.Slider` (cho `AppCompatSeekBar`), `Widget.Os26.Ds.LinearProgress` (cho `LinearProgressIndicator`), `Widget.Os26.Ds.CircularProgress` (cho `CircularProgressIndicator`)
  - `class DsSliderRow : LinearLayout` — attr `dsLabel`; `var value: Int` (0..100); `fun setOnValueChangeListener(listener: ((value: Int, fromUser: Boolean) -> Unit)?)`; nhãn giá trị hiện `"$value%"`
  - `class DsStepIndicatorView : View` — `var stepCount: Int` (mặc định 5), `var currentStep: Int` (0-based); đổi bước có hoạt ảnh 200 ms

- [x] **Step 1: Công tắc** (`302:3331`): ray 51 × 31 bo 16 — bật `ds_accent`, tắt `ds_surface_control`; núm tròn 27 đệm 2 màu `ds_text_primary`, dưới núm một vòng đen 30% lệch xuống 1 dp rộng hơn 1 dp (xấp xỉ `Core/Shadow/Knob`). `SwitchCompat`: `android:thumb`, `app:track`, `app:switchMinWidth=51dp`, `android:showText=false`, `minHeight` 48, chuyển màu ray do `SwitchCompat` lo (selector `state_checked`).
- [x] **Step 2: Radio** (`302:3336`): 22 × 22; chọn: tròn `ds_text_primary` có viền 7 dp `ds_accent` (nhìn như vòng xanh, chấm trắng 8 dp giữa); không chọn: viền 2 dp `ds_text_secondary`, không nền. `android:button=@drawable/ds_radio`, `minHeight`/`minWidth` 48.
- [x] **Step 3: Thanh trượt** (`302:3493`): ray cao 4 bo 2 `ds_surface_control`, phần đã chọn `ds_accent`; núm tròn 10 màu `ds_text_primary` viền 1 dp `ds_accent_light`, vòng bóng như công tắc; `AppCompatSeekBar` `maxHeight/minHeight` 4 dp cho ray, vùng chạm (chiều cao view) 48, `splitTrack=false`, `max=100`. `ds_slider_row.xml`: nhãn `.Body` rộng 104, khe 12, thanh trượt chiếm phần còn lại, giá trị `.Caption` rộng 34 căn phải; hàng cao 48 (Figma 40, nâng lên 48 vì vùng chạm). Trợ năng: `SeekBar` tự đọc phần trăm; đặt `contentDescription` của thanh trượt = nhãn.
- [x] **Step 4: Tiến độ** (quyết định 5, suy ra từ thanh trượt): `LinearProgressIndicator` dày 4, bo 2, ray `ds_surface_control`, chỉ báo `ds_accent`, `trackStopIndicatorSize=0dp`, `indicatorTrackGapSize=0dp`; `CircularProgressIndicator` cỡ 32, dày 3, chỉ báo `ds_accent`, ray trong suốt, `indicatorTrackGapSize=0dp`. Kiểm tên attr với Material 1.14.0 trước khi viết (đọc `attrs.xml` trong AAR qua Android Studio); attr không có thì bỏ, ghi Nhật ký.
- [x] **Step 5: `DsStepIndicatorView`** (`302:3641`): khung bề mặt `CONTROL` bo PILL, đệm 7/10, chấm cao 6 bo 3 cách nhau 6; chấm thường 6 × 6 `ds_surface_control`; chấm hiện tại 18 × 6 gradient ngang `ds_primary_start` → `ds_primary_end`. Đổi bước: bề rộng chấm cũ 18 → 6 và chấm mới 6 → 18 trong 200 ms (`ValueAnimator`, tôn trọng `ValueAnimator.areAnimatorsEnabled()`). Trợ năng: `contentDescription` = chuỗi `ds_step_indicator_desc` ("Bước %1$d trên %2$d").
- [x] **Step 6: Catalog** mục "Điều khiển": 2 công tắc (bật/tắt), 2 radio trong một `RadioGroup`, 3 hàng trượt "Độ trong suốt 70%", "Độ mờ 55%", "Độ khúc xạ 40%", thanh tiến độ ngang 60% và không xác định, vòng tiến độ, chỉ báo bước có nút "Bước tiếp" chạy vòng 1→5.
- [x] **Step 7: Build, lint.**
- [x] **Step 8: Kịch bản kiểm (Live 4).** (a) Bấm công tắc, radio đổi trạng thái đúng màu; (b) kéo thanh trượt mượt, giá trị nhảy 0–100, chạm hụt núm 15 dp theo chiều dọc vẫn kéo được; (c) TalkBack: công tắc đọc "Bật/Tắt", radio đọc "đã chọn", thanh trượt đọc phần trăm và chỉnh được bằng vuốt lên/xuống; (d) chỉ báo bước chạy mượt, tắt hoạt ảnh trong Developer options thì nhảy thẳng.
- [x] **Step 9: Commit** gộp với Task 4: `feat: add v3 buttons, chips, segmented control, search field and controls`.

---

### Task 6: Hàng, card nhóm, tiêu đề nhóm, chân trang

**Bắt buộc kiểm tay:** có — TalkBack đọc hàng thành một mục, chữ dài (Review Focus 3, 4).

**Files:**
- Create: `res/layout/ds_row.xml`, `core/designsystem/DsRowView.kt`, `core/designsystem/DsCardGroup.kt`; style `Widget.Os26.Ds.SectionHeader`, `Widget.Os26.Ds.Footer` trong `ds_styles.xml`; khối `DsRowView` trong `ds_attrs.xml`

**Interfaces:**
- Consumes: `DsIconTileView`, `DsTileColor`, style công tắc/radio Task 5, `ds_ic_chevron_right`, `ds_divider`, `applyDsSurface`.
- Produces:
  - `enum class DsRowType { CHEVRON, VALUE, TOGGLE, RADIO, PLAIN }`
  - `class DsRowView : ConstraintLayout` — attr `dsRowType`, `dsTitle`, `dsDescription`, `dsValue`, `dsTileIcon`, `dsTileColor`, `dsShowDivider` (mặc định true); thuộc tính `title`, `description`, `value: CharSequence?`, `isChecked: Boolean` (TOGGLE/RADIO), `showDivider: Boolean`; `fun setTile(@DrawableRes icon: Int?, color: DsTileColor)` (`icon = null` ẩn ô icon); `fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?)` — chỉ gọi khi người dùng đổi, không gọi khi code đặt `isChecked` (bài học M8a: gỡ listener trước khi đặt)
  - `class DsCardGroup : LinearLayout` — dọc, bề mặt `CARD` bo `DsCorner.Role(DsCornerRole.CARD)` qua `applyDsSurface`, `clipToOutline = true`; tự ẩn đường kẻ của hàng con cuối (`onViewAdded`/`onViewRemoved`)

- [x] **Step 1: Bố cục `ds_row.xml`** (component `Row` `302:3362` + hàng trang chủ): `minHeight` 56, đệm đầu 12, đệm cuối 12, đệm dọc 10; thứ tự: [radio 22 nếu RADIO] [ô icon 32 nếu có] khe 12 [khối chữ: tiêu đề `.Body`, mô tả `.Caption` khe 2] khe 12 [phần cuối]. Phần cuối: CHEVRON → chevron 24 tô `ds_text_secondary`; VALUE → giá trị `.Value` (một dòng, cắt "…", `maxWidth` 50% hàng) khe 4 + chevron; TOGGLE → `SwitchCompat` style Task 5; PLAIN, RADIO → không. Tiêu đề tối đa 2 dòng. Đường kẻ `ds_divider` ở đáy, bắt đầu từ mép trái khối chữ (sau ô icon hoặc radio), chạy tới mép phải hàng.
- [x] **Step 2: Hành vi** `DsRowView`: cả hàng bấm được (nền gợn `?attr/selectableItemBackground`, giới hạn trong card); TOGGLE/RADIO: bấm hàng đổi trạng thái công tắc/radio; công tắc/radio con `isClickable = false`, `isFocusable = false`, `importantForAccessibility = no` để hàng đọc thành **một** mục. Trợ năng của hàng: TOGGLE báo `className = Switch`, `isCheckable`, `isChecked`; RADIO báo `RadioButton`; VALUE nối giá trị vào nội dung đọc ("Ngôn ngữ, Tiếng Việt").
- [x] **Step 3: `DsCardGroup`**: khoảng giữa các hàng 0; ẩn đường kẻ hàng cuối; đặt `importantForAccessibility` mặc định.
- [x] **Step 4: Style** `Widget.Os26.Ds.SectionHeader` (`.SectionHeader`, `marginBottom` = `ds_section_header_gap`, `accessibilityHeading` đặt bằng `ViewCompat.setAccessibilityHeading` trong code của view, không đặt trong style vì attr từ API 28 làm lint `NewApi`) và `Widget.Os26.Ds.Footer` (`.Caption`, căn giữa, đệm trên 16 — dòng "Phiên bản 1.0.0").
- [x] **Step 5: Catalog** mục "Danh sách": tiêu đề nhóm "Chung" + card 5 hàng như trang chủ Figma (icon + màu theo Task 3, hàng Ngôn ngữ VALUE "Tiếng Việt", Thời tiết VALUE "°C", Vị trí VALUE "Tự động"); card 2: TOGGLE "Ẩn thanh điều hướng" có mô tả "Ẩn 3 nút hoặc thanh cử chỉ ở đáy", TOGGLE tiêu đề dài 2 dòng, VALUE giá trị dài "Bahasa Indonesia (Indonesia)"; card 3: 3 hàng RADIO (chỉ một chọn); hàng PLAIN; chân trang "Phiên bản 1.0.0".
- [x] **Step 6: Build, lint.**
- [x] **Step 7: Kịch bản kiểm (Live 4).** (a) Đường kẻ bắt đầu sau ô icon, hàng cuối không có kẻ, gợn nhấn không tràn khỏi góc bo của card; (b) bấm bất kỳ đâu trên hàng TOGGLE đổi công tắc; hàng RADIO chọn một; (c) TalkBack: hàng TOGGLE đọc một lần "Ẩn thanh điều hướng, Ẩn 3 nút…, Tắt, công tắc"; hàng VALUE đọc kèm giá trị; tiêu đề nhóm đọc là tiêu đề; (d) chữ dài: tiêu đề xuống 2 dòng, giá trị dài cắt "…", chevron không bị đẩy ra; (e) cỡ chữ lớn nhất: hàng cao lên, không chồng chữ.
- [x] **Step 8: Commit** gộp với Task 7 (chủ dự án chốt 2026-11-16).

---

### Task 7: Thanh điều hướng, thanh trên onboarding, hộp thoại, bottom sheet

**Bắt buộc kiểm tay:** có — cửa sổ hộp thoại/sheet, nút Back, cỡ chữ lớn (Review Focus 1, 2).

**Files:**
- Create: `res/layout/ds_nav_bar.xml`, `core/designsystem/DsNavBar.kt`, `res/layout/ds_onboarding_top_bar.xml`, `core/designsystem/DsOnboardingTopBar.kt`, `res/layout/ds_dialog.xml`, `core/designsystem/DsDialog.kt`, `res/layout/ds_bottom_sheet.xml`, `core/designsystem/DsBottomSheet.kt`; theme hộp thoại/sheet trong `ds_themes.xml`; khối `ds_` trong `strings.xml`

**Interfaces:**
- Consumes: `DsButton`, `applyDsSurface`, icon Task 3.
- Produces:
  - `class DsNavBar : LinearLayout` — attr `dsTitle`, `dsBackLabel` (mặc định `@string/ds_nav_back_default` "Cài đặt"), `dsShowBack` (mặc định true); `fun setOnBackClickListener(listener: View.OnClickListener?)`
  - `class DsOnboardingTopBar : FrameLayout` — attr `dsShowBack`, `dsShowSkip`; `fun setOnBackClickListener(...)`, `fun setOnSkipClickListener(...)`
  - `class DsDialog(context: Context)` — builder: `title(text: CharSequence)`, `message(text: CharSequence)`, `content(view: View)`, `negative(text: CharSequence, onClick: (() -> Unit)? = null)`, `positive(text: CharSequence, onClick: (() -> Unit)? = null)`, `cancelable(value: Boolean)`, `fun show(): AppCompatDialog` (mỗi hàm builder trả `DsDialog`)
  - `class DsBottomSheet(context: Context) : BottomSheetDialog` — `fun setSheetTitle(text: CharSequence)`, `fun setSheetContent(view: View)`, `var showClose: Boolean`

- [x] **Step 1: `DsNavBar`** (`312:2675`): dọc, đệm 4/16/8/16, khe 10; nút quay lại: bề mặt `CONTROL` bo PILL, cao nhìn thấy 32 (vùng chạm 48, inset dọc 8), đệm 4/14/4/8, khe 2, chevron trái 24 + nhãn `.Body`; tiêu đề `.Title`, tối đa 2 dòng, `accessibilityHeading=true`. `contentDescription` nút quay lại = "Quay lại, <nhãn>" (`ds_nav_back_desc`). Không tự xử lý inset trạng thái: màn chứa đặt đệm trên = inset `statusBars`.
- [x] **Step 2: `DsOnboardingTopBar`** (`312:2699`): cao 56, đệm ngang 16; nút quay lại tròn 40 bề mặt `CONTROL` bo PILL (= 20), chevron trái 24, vùng chạm 48; "Bỏ qua" chữ `.Value` màu `ds_text_tertiary`, đệm 11/8, vùng chạm 48, căn phải. Chuỗi `ds_onboarding_skip` "Bỏ qua", `ds_back` "Quay lại".
- [x] **Step 3: `DsDialog`** (`312:2691`): cửa sổ rộng 312 dp (tối đa bề rộng màn − 2 × 24), nền trong suốt, nội dung bề mặt `SHEET` bo `Role(SHEET)` (mặc định 28); đệm 24/20/20/20, khe 12; tiêu đề `.Card`, nội dung `.Body` màu secondary, khe nội dung tùy ý, hàng nút: hai `DsButton` cỡ L chia đều, khe 10 (negative = secondary, positive = primary); chỉ một nút thì nút chiếm cả hàng. Lớp mờ: `backgroundDimEnabled=true`, `backgroundDimAmount=0.6`. Theme cửa sổ `Theme.Os26Launcher.Ds.Dialog` (parent `Theme.Os26Launcher.Ds`, `windowIsFloating=true`, `windowBackground` trong suốt, `windowMinWidthMinor` 0). Hộp thoại tự đóng sau khi gọi `onClick`.
- [x] **Step 4: `DsBottomSheet`** (`312:2681`): nền bề mặt `SHEET`, bo `Role(SHEET)` (mặc định 28) hai góc trên, 0 hai góc dưới (Figma ghi "mixed", đọc là bo trên — **giả định**, kiểm ảnh ở Task 7 Step 7); đệm 10/20/32/20 + inset `navigationBars` ở đáy, khe 16; tay nắm 38 × 5 bo 3 `ds_surface_control` căn giữa; tiêu đề `.Card` + nút đóng tròn 32 nền `ds_surface_control`, icon `ds_ic_close` 20, vùng chạm 48, `contentDescription` `ds_close` "Đóng"; vùng nội dung tùy ý. Style `Theme.Os26Launcher.Ds.BottomSheet` đặt `bottomSheetStyle` với `backgroundTint` trong suốt (để thấy nền `SHEET`) và `shapeAppearance` không bo (bo do drawable lo).
- [x] **Step 5: Chuỗi** khối `ds_` cuối `strings.xml`: `ds_back` "Quay lại", `ds_nav_back_default` "Cài đặt", `ds_nav_back_desc` "Quay lại, %1$s", `ds_close` "Đóng", `ds_onboarding_skip` "Bỏ qua", `ds_step_indicator_desc` "Bước %1$d trên %2$d" (Task 5 dùng; thêm ở Task 5 nếu làm trước).
- [x] **Step 6: Catalog** mục "Khung": một `DsNavBar` tiêu đề "Ngôn ngữ", một `DsNavBar` tiêu đề dài 2 dòng; một `DsOnboardingTopBar`; nút "Mở hộp thoại" (tiêu đề "Bạn thấy ứng dụng thế nào?", nội dung "Đánh giá của bạn giúp chúng tôi làm launcher tốt hơn.", nút "Để sau"/"Gửi đánh giá"); nút "Mở hộp thoại 1 nút"; nút "Mở bottom sheet" (tiêu đề "Đặt hình nền cho", nội dung 3 hàng PLAIN "Màn hình chính", "Màn hình khóa", "Cả hai").
- [x] **Step 7: Build, lint.**
- [x] **Step 8: Kịch bản kiểm (Live 4).** (a) Hộp thoại rộng 312, giữa màn, nền tối 60%, hai nút chia đều; bấm ngoài hoặc Back đóng; (b) bottom sheet bo hai góc trên, vuốt xuống đóng, nút đóng đóng, đáy không bị thanh điều hướng che; (c) chạm nút quay lại của `DsNavBar` sát mép trên/dưới vẫn ăn; (d) cỡ chữ lớn nhất: tiêu đề dài xuống dòng, nút hộp thoại không cắt chữ dọc; (e) TalkBack đọc "Quay lại, Cài đặt" và tiêu đề là tiêu đề.
- [x] **Step 9: Commit** gộp với Task 6: `feat: add v3 rows, card group, nav bars, dialog and bottom sheet`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11). Không cần Oppo.
**Chuẩn bị:** cài bản debug; ảnh Figma ở `build/figma-v3/` (Task 1 Step 1, Task 3 Step 1).
**Thứ tự:**
1. Mở catalog bằng `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.DsCatalogActivity`.
2. Cuộn từng mục, chụp màn từng mục (Claude nếu phiên có `adb`, không thì chủ dự án), đặt cạnh ảnh Figma tương ứng; chủ dự án duyệt mức giống.
3. Lặp kịch bản (b) của Task 4, 6, 7 ở cỡ chữ lớn nhất.
4. Bật TalkBack, đi qua mục Điều khiển và Danh sách (Task 5 (c), Task 6 (c)).
5. Đặt máy giao diện sáng: catalog vẫn tối.
6. Đổi thanh "Bo góc" của kính (Cài đặt của launcher → Liquid Glass → Tinh chỉnh kính) về 0, tối đa, rồi giữa; mỗi lần cuộn catalog qua Danh sách (card nhóm), Hộp thoại, Bottom sheet, ô icon: card/hộp thoại/sheet đổi theo, ô icon và các thành phần PILL không đổi (Review Focus 6).

**Hồi quy:** mở Home, Cài đặt, Ứng dụng ẩn, sheet thời tiết: không có gì đổi (DS3 không sửa file của các màn này; chỉ kiểm nhanh bằng mắt). `assembleRelease`: APK không chứa `DsCatalogActivity`; ghi dung lượng APK release trước/sau (tăng do font).

**Kết quả kiểm chứng (máy, ngày):** Vsmart Live 4, 2026-11-16 đến 2026-11-17. Chủ dự án kiểm cả catalog (Nút & chọn, Điều khiển, Danh sách, Khung) so với ảnh Figma: **giống ở mức nhìn, chênh các tiểu tiết nhỏ, không trùng từng pixel** (đúng đánh đổi đã chấp nhận: không blur nền, không drop shadow đen, viền sáng xấp xỉ bằng gradient). Ba bài kiểm chính (cỡ chữ lớn nhất, TalkBack, đổi thanh "Bo góc") đạt. Giả định bottom sheet bo hai góc trên: chủ dự án xem ảnh Figma `312:2681`, không báo lệch. Hồi quy Home, Cài đặt, Ứng dụng ẩn, sheet thời tiết: chủ dự án xem, không đổi (DS3 không sửa file của các màn này). **Chưa build release** (chưa chạy `assembleRelease`): dung lượng APK release trước/sau và việc `DsCatalogActivity` không có trong bản release **chưa đo**. Bản debug cài trên máy nặng 14,97 MB (gồm catalog debug, chưa bật R8) nên bản release sẽ nhỏ hơn con số đó. Font Be Vietnam Pro 4 file tổng 546 756 byte (≤ 800 KB, đo ở Task 1).

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-16 | — | Viết plan từ Figma v3 (component + styles đọc bằng `figma-bridge`) | — | Chờ chủ dự án duyệt plan và trả lời Q1–Q4 |
| 2026-11-16 | — | Sửa plan theo góp ý chủ dự án: bo góc 4 vai thành giá trị mặc định đổi được lúc chạy, lưu ở `launcher_settings` (thêm Task 1b, quyết định 7, Q5); bo PILL tính theo chiều cao thay số cứng ở ô tìm và nút tròn | — | Chủ dự án duyệt plan cùng ngày, Q1–Q5 theo đề xuất |
| 2026-11-16 | 1 | Step 1: 16 ảnh tham chiếu (15 thành phần + màn 1.3) ở `build/figma-v3/components/`, scale 3. Step 2–7: `ds_colors.xml` (46 màu), `ds_dimens.xml` (bảng quy ước, 4 bo góc ghi chú là mặc định), font 4 độ đậm + `be_vietnam_pro.xml` + `docs/third_party/BeVietnamPro-OFL.txt`, `ds_type.xml` (11 kiểu), `ds_themes.xml` + `values-v27/ds_themes.xml`, `DsCatalogActivity` + layout + khai ở manifest debug. Tổng 4 file font **546 756 byte** (≤ 800 KB) | — (chủ dự án commit) | **Chưa build** (phiên VM, `Workflow.md` mục 5): chủ dự án chạy Step 8. Lệch plan: (1) `save_screenshots` bị từ chối (bridge chạy ở `C:\Windows\System32`), dùng `get_screenshot` base64 rồi giải mã; (2) kiểu chữ trỏ thẳng file ttf từng độ đậm thay cho `textFontWeight`, vì API 26–27 không chọn được 300/600 từ một họ font; (3) `lineHeight` dùng attr của Material (không dùng `android:lineHeight`, chỉ có từ API 28) |
| 2026-11-16 | 3 (Step 1, làm sớm) | Xuất trước 24 icon SVG vào `build/figma-v3/icons/` và JSON đầy đủ của section Components vào `build/figma-v3/components_302-3330.json` (số đo, hiệu ứng, màu) để chủ dự án ngắt `figma-bridge`: phần còn lại của DS3 không cần Figma | — | Bước 1 Task 3 coi như xong, chờ tick cùng Task 3 |
| 2026-11-16 | 1 | Chủ dự án build, kiểm tay và commit (`3ac530c`) | `3ac530c` | Tick Task 1 |
| 2026-11-16 | 1b | Đổi thiết kế Task 1b trước khi code: GT (PR #62) đã có thanh "Bo góc" chung cho kính (`glass_look_corner`). Chủ dự án quyết: bo góc sẵn có dành cho kính, cơ chế bo góc sắp làm là cho icon. Bỏ 4 khóa `ui_radius_*` và mọi sửa ở `domain`/`data`; vai kính nhân qua `GlassTuning.cornerRadius`, `TILE` cố định | — | Quyết định 7, Q5, Task 1b, Vùng file sửa theo |
| 2026-11-16 | 1b | Code: `core/designsystem/DsCorner.kt` (`DsCornerRole`, `DsCorner`), `core/designsystem/DsCorners.kt` (`basePx`, `radiusPx` qua `GlassTuning.cornerRadius`, listener chuyển tiếp `GlassEnvironment`), chú thích `ds_dimens.xml`, mục "Bo góc" trong catalog (đăng ký ở `onStart`, gỡ ở `onStop`) | — (chủ dự án commit) | **Chưa build** (phiên VM). Chờ chủ dự án build + kiểm Step 5 |
| 2026-11-16 | 1b | Chủ dự án build, kiểm tay và commit | `4068194` | Tick Task 1b |
| 2026-11-16 | 2 | Rà GT trước khi code: GT không có phần nào của Task 2 (không aurora, không drawable nhiều lớp; `BackdropBlurDrawable` cần ảnh nền Home nên không dùng được trên aurora). Trùng ý: thanh "Độ bóng" áp cho mọi bề mặt kính. Chủ dự án chọn cho v3 theo độ bóng (quyết định 8) | — | Plan Task 2 sửa: bảng độ bóng, Step 6 thành `DsDividerDrawable`, Step 9 (g) |
| 2026-11-16 | 2 | Code Step 1–7: `DsSurface` (cờ `followsGloss`, `darkPress`), `DsSurfaceDrawable` (lớp gradient theo bảng, độ bóng qua `GlassTuning.tint`/`border`, nhấn 8%, outline đặc alpha 1 cho glow), `DsSurfaceViews.kt` (`applyDsSurface` + binding nghe `DsCorners` khi gắn cửa sổ, tag `R.id.ds_surface_binding` trong `ds_ids.xml` mới), `AuroraBackgroundDrawable` (số điểm xuất phát, chưa chỉnh theo mắt), `DsDividerDrawable`; catalog: nền aurora cho cửa sổ + mục "Bề mặt" | — (chủ dự án commit) | **Chưa build** (phiên VM). Thêm file ngoài danh sách: `DsSurfaceViews.kt`, `ds_ids.xml`. Chờ chủ dự án build + kiểm Step 9 (bắt buộc kiểm tay) |
| 2026-11-16 | 2 | Chủ dự án góp ý: không xem được hai màn cùng lúc trên một máy nên kịch bản (e)/(g) "đổi rồi so" không làm được. Thêm mục "So với mặc định" trong catalog (cặp khối Hiện tại/Mặc định + dòng số thông số kính); `DsSurfaceDrawable` thêm tham số `followGlassSettings` (mặc định true, chỉ catalog dùng false). Tick Step 1–7 Task 2 (đã code) và Step 1 Task 3 (đã xuất SVG) | — | Step 8–10 Task 2 chờ build + kiểm |
| 2026-11-16 | 2 | Chủ dự án build, kiểm tay Step 9 (a)–(g) đạt | `8725184` | Tick Task 2 |
| 2026-11-16 | 3 | Code Step 2–5: 24 `ds_ic_*.xml` đổi từ SVG bằng script Python (chỉ có `<path>` nét; bỏ `filter` bóng và `clip-path`; mọi nét `#FFFFFFFF`), `DsTileColor` (KDoc có bảng ánh xạ), `DsTileDrawable`, `DsIconTileView`, `ds_attrs.xml` (`dsTileColor`, `dsTileIcon` khai ở cấp ngoài để `DsRowView` Task 6 dùng lại); catalog mục "Icon" | — (chủ dự án commit) | **Chưa build** (phiên VM). Lệch plan: (1) `ds_ic_close` giữ cỡ gốc Figma 20 dp (viewport 20), không ép 24; (2) nét chevron 1,8 (plan ghi 1,5), nét đóng 2: lấy đúng SVG; (3) bỏ `stroke-opacity` 0,68 của chevron và kính lúp ô tìm, độ mờ do màu tint (vd `ds_text_secondary`) quyết |
| 2026-11-16 | 3 | Sửa lỗi biên dịch: `/*` trong KDoc của `DsTileColor`, `DsTileDrawable` mở comment lồng; chủ dự án build, kiểm, commit | `f4ed2ab` | Tick Task 3. Chủ dự án chốt: Task 4 và 5 làm cùng lúc, gộp một commit |
| 2026-11-16 | 4 + 5 | Code Step 1–6 của cả hai Task: `DsButton`, `DsBadgeView`, `DsChipView`, `DsSegmentedControl`, `DsSearchField` (+ `ds_search_field.xml`), `ds_styles.xml` (Switch, Radio, Slider, LinearProgress, CircularProgress), drawable công tắc/radio/thanh trượt, `DsSliderRow` (+ `ds_slider_row.xml`), `DsStepIndicatorView`, chuỗi `ds_step_indicator_desc` (khối `ds_` cuối `strings.xml`), attr `DsButton`/`DsBadgeView`/`DsSliderRow`; catalog mục "Nút & chọn", "Điều khiển" (layout debug `ds_catalog_buttons.xml`, `ds_catalog_controls.xml`) | — (chủ dự án commit gộp) | **Chưa build** (phiên VM). Lệch plan: (1) `applyDsSurface` thêm tham số `insetVerticalPx` (nền `InsetDrawable`, outline theo phần nhìn thấy) cho vùng chạm 48 của nút M/S, chip, segmented, ô tìm; (2) `DsButton` dùng `defStyleAttr = 0` vì style nút Material3 có `backgroundTint` tô đè bề mặt; (3) chip không tự đổi chọn khi bấm, màn quyết (một hay nhiều); (4) thêm `ds_search_cursor.xml`; (5) tên attr tiến độ (`trackStopIndicatorSize`, `indicatorTrackGapSize`, `trackCornerRadius`) chưa đối chiếu AAR 1.14.0: nếu AAPT báo không có attr thì bỏ dòng đó |
| 2026-11-16 | 4 + 5 | Sửa lỗi build `parseDebugLocalResources` ("Failed to parse XML resource file ...packaged_res\\debug\\packageDebugResources\\values\\values.xml"): giá trị enum `new` của `dsBadgeType` là từ khóa Java; đổi thành `new_item` | — | |
| 2026-11-16 | 4 + 5 | Chủ dự án build, kiểm, commit gộp | `ffafd0b` | Tick Task 4, 5. Chủ dự án chốt: Task 6 và 7 làm cùng lúc, gộp một commit |
| 2026-11-16 | 6 + 7 | Code Step 1–6 của cả hai Task: `DsRowView` (ConstraintLayout, nhập `ds_row.xml`), `DsCardGroup`, `DsNavBar`, `DsOnboardingTopBar`, `DsDialog`, `DsBottomSheet`; thêm `ds_sheet_handle.xml`, `ds_sheet_close_bg.xml`, dimen `ds_onboarding_bar_height`, `ds_dialog_width`, `android:elevation` 0 cho style bottom sheet; catalog mục "Danh sách", "Khung". Layout, attr, style, theme, chuỗi `ds_`, `topCornersOnly` của `DsSurfaceDrawable` đã có sẵn từ trước | — (chủ dự án commit) | **Chưa build** (phiên VM). Lệch plan: (1) `DsRowView` lấy biến thể `value_row` cho enum attr (`value` trùng từ khóa); (2) radio không tự loại trừ nhau, màn chứa lo (catalog làm mẫu); (3) `DsBottomSheet.onStart` bỏ trạng thái thu gọn, mở thẳng STATE_EXPANDED; (4) `android:accessibilityHeading` trong XML chỉ có từ API 28 nên code gọi thêm `ViewCompat.setAccessibilityHeading` (lint có thể báo `UnusedAttribute`, cảnh báo). Chờ chủ dự án build + kiểm Step 7 của Task 6, Step 8 của Task 7 |
| 2026-11-16 | 6 + 7 | Gỡ `android:accessibilityHeading` khỏi style `Widget.Os26.Ds.SectionHeader` (lint `NewApi`, minSdk 26). Chủ dự án build, kiểm, commit gộp | `ed5d481` | Tick Task 6, 7. Còn: bài kiểm cả tính năng, bảng Review Focus, tài liệu chung |
| 2026-11-17 | — | Bài kiểm cả tính năng trên Live 4: chủ dự án duyệt catalog so với Figma, "khá giống, chênh tiểu tiết"; điền bảng Review Focus 6 dòng và "Kết quả kiểm chứng". Cập nhật tài liệu chung: `project_structure.md` (cây thư mục, vai trò folder, quy ước khoảng cách v3, tệp thêm ở DS3, mục 9), `Figma-Draft.md` mục 3 hàng 1, `lessons/design-system.md` (mới), `lessons/README.md`, `AGENTS.md` (mục lục) | — | Chưa build release: dung lượng APK release và việc `DsCatalogActivity` vắng trong release chưa đo (bản debug trên máy 14,97 MB). Lệch tiểu tiết so với Figma: chủ dự án bảo không cần ghi |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Cỡ chữ lớn nhất | Chủ dự án duyệt catalog (Nút & chọn, Điều khiển, Danh sách, Khung) ở cỡ chữ lớn nhất trên Live 4, 2026-11-16: không báo cắt chữ hay chồng nhau | Đạt |
| 2 | Vùng chạm 48 dp | Chủ dự án kiểm bằng "Hiện ranh giới bố cục" trên Live 4, 2026-11-16, không báo chỗ chạm hụt | Đạt |
| 3 | TalkBack | Chủ dự án kiểm TalkBack trên Live 4, 2026-11-16, không báo lỗi | Đạt |
| 4 | Chữ dài | Chuỗi thử trong catalog (tiêu đề hai dòng có công tắc, giá trị "Bahasa Indonesia (Indonesia)", tiêu đề nav bar hai dòng); chủ dự án duyệt trên Live 4, 2026-11-16 | Đạt |
| 5 | API 26–27 (lint `NewApi`) | `lintDebug` lỗi `NewApi` vì `android:accessibilityHeading` trong style `Widget.Os26.Ds.SectionHeader` → gỡ attr khỏi style, code gọi `ViewCompat.setAccessibilityHeading`; chủ dự án build lại, báo hết lỗi `NewApi` (số cảnh báo còn lại chưa đếm), commit `ed5d481`. Không có máy API 26–27 để thử, chỉ có lint | Đạt (chỉ lint) |
| 6 | Bo góc đổi lúc chạy | Chủ dự án đổi thanh "Bo góc" của kính (0, tối đa, giữa) và xem card, hộp thoại, sheet trên Live 4, 2026-11-16: đổi theo, ô icon và PILL không đổi | Đạt |

## Tài liệu chung đã cập nhật (bước 7 của `Workflow.md`, 2026-11-17)

- `project_structure.md`: cây thư mục và bảng vai trò folder có `core/designsystem/`; mục 7 thêm "Quy ước khoảng cách và bố cục cho màn v3"; mục 9 thêm đoạn "Tệp thêm ở DS3" và đánh dòng DS3 xong.
- `Figma-Draft.md`: mục 1 ghi nơi lưu ảnh v3 (`build\figma-v3\`); mục 3 hàng 1 (Components) lên 96 vì giống ở mức nhìn nhưng chưa áp vào màn nào (không ghi 100%: lệch tiểu tiết, đánh đổi đã chấp nhận), dòng Tổng ~36%; mục 6 ghi DS3 xong.
- `lessons/design-system.md` (mới), dòng ở `lessons/README.md` và mục lục của `AGENTS.md`: bài học DS3 (attr API 28 trong style, nút Material3, font nhiều độ đậm, vùng chạm 48, gradient XML, đổi SVG, trợ năng của hàng, hộp thoại/sheet, tên attr tiến độ). Bài học chung (enum trùng từ khóa Java, `/*` trong KDoc, không so hai màn cùng lúc, số đo chỉnh được không là hằng) đã nằm ở `AGENTS.md` từ lúc làm.
- `Launcher-Features.md`, `room-schema.md`: không đổi (DS3 không có mục TT#, không đổi schema Room).
