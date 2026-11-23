# GI — Kiểu icon "Kính": hình icon trên tấm kính (học từ Launcher OS) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Code từng Task, báo cáo, qua review mới tick. Dự án **không viết test tự động**. Agent thực thi không dùng máy thật (không adb). Lệnh viết cho PowerShell.

**Nhánh:** `feat/gi-glass-icons`, tách từ `main` · **Plan viết:** 2026-11-10
**Trạng thái:** Chờ chủ dự án duyệt
**Khóa:**
- Không đổi schema DB, không thêm dependency, không gỡ thư viện QWEA0, không đụng plan LQ.
- Có thêm giá trị enum `IconStyle.GLASS`, lưu bằng tên trong DataStore. Thêm ở cuối, đọc bằng `fromName` (bài học GS).
- **Có chạm `core/glass/*`** (vùng nóng): `BackdropBlurDrawable` thêm vẽ theo `Path` (mặc định giữ hành vi cũ), `GlassBudget` thêm một hàm.

**Goal:** thêm lựa chọn thứ tư "Kính" ở Cài đặt → Kiểu icon. Icon app ở lưới Home và dock trông như Launcher OS (theme Bubble Kiss):
- Hình (glyph) của icon được đổi sang **màu trắng**.
- Hình nằm trên một **tấm kính** mang ảnh nền đã làm mờ, bám theo vị trí icon.
- Ở nơi không có kính thật (thư mục, Library, tìm kiếm, máy yếu), hình nằm trên một tấm trắng mờ "nướng" sẵn trong bitmap.

**Architecture:**
- `IconRenderer` vẽ bitmap kiểu GLASS, gồm tấm trắng mờ và glyph trắng, cắt theo `IconShape`.
- `IconCellView` vẽ một `BackdropBlurDrawable` (kính tại chỗ có sẵn từ GL) theo đúng path của `IconShape`, phía sau bitmap. Cách này giống ô thư mục và widget G2c.
- `IconGridView` và `DockView` quyết định ô nào được cấp kính, theo kiểu icon và `GlassBudget`.

**Tech Stack:** Kotlin, View/Canvas, `ColorMatrixColorFilter`, `BitmapShader`. Không dependency mới.
**Spec:** `docs/LauncherOS-LiquidGlass-RE.md` (mục 2 "Icons", mục 3 bảng tham số, mục 4 "Áp dụng cho Os26Launcher").

---

## Đầu vào: Launcher OS làm thế nào (tóm tắt từ spec)

- Kính của icon là **một view vẽ lúc chạy** đặt sau icon: vuông, bo 25% cạnh icon, cùng cỡ icon. Ảnh icon vẽ đè lên trên, không đổi. Kính chỉ có ở màn hình chính và dock, **không** có trong drawer hay thư mục. Bật theo cờ `isBlur` của icon pack.
- Kính lấy lát của **ảnh wallpaper đã làm mờ một lần** theo `getLocationOnScreen`. Đây là đúng cách `BackdropBlurDrawable` + `BlurredBackdropCache` của dự án đã làm.
- Theme kiểu Bubble Kiss dùng icon pack có **nền trong suốt** (`isTransparentMockup`), vì vậy kính sau icon mới lộ ra. JSON thật của Bubble Kiss chưa đọc được (theme nằm trên server). Màu glyph trắng là **lựa chọn của plan này** theo ảnh chủ dự án mô tả, chỉnh theo mắt ở Task 5.
- Icon Android vốn có nền đục. Muốn kính lộ ra thì phải bỏ lớp nền: dùng lớp `foreground` của `AdaptiveIconDrawable`, như kiểu Tô màu (G2b) đã làm.

## Vì sao chọn thiết kế này

| | A. Kính sau icon gốc | B. Kiểu icon "Kính", glyph trắng (chọn) |
|---|---|---|
| Kính có lộ ra không | Không: icon đục che kín, chỉ lộ ở viền nếu kính to hơn icon | Có: nền icon bị bỏ, glyph nằm trên kính |
| Giống Launcher OS | Không | Có (icon nền trong suốt trên kính) |
| Chỗ không có kính tại chỗ | Không đổi | Tấm trắng mờ nướng sẵn trong bitmap, nên glyph không bao giờ "lơ lửng" |
| Chi phí | Như B | Một lệnh vẽ kính mỗi ô có kính, cùng loại với ô thư mục (GL) |

**Vì sao tấm trắng mờ nướng vào bitmap:** bitmap icon được dùng ở ~8 nơi (panel thư mục, Library, tìm kiếm, menu nhấn giữ, ảnh kéo, xem trước 9 icon trong ô thư mục…). Nướng tấm nền vào bitmap thì mọi nơi đều đúng mà không phải sửa từng nơi. Chỉ lưới và dock vẽ thêm kính mờ thật phía dưới.

**Vì sao kính theo `Path` của `IconShape` chứ không `drawRoundRect`:** icon dùng siêu elip (`IconShape.DEFAULT`, bo 0,2237, smoothing 0,6). Rounded rect cùng bán kính sẽ lộ một mẩu kính ở 4 góc, ngoài viền tấm nướng.

**Vì sao chưa đổi widget:** widget Đồng hồ/Lịch (G2c) **đã có nền kính tại chỗ** (`IconCellView.drawWidget`), tức đã giống widget theme của Launcher OS. Plan này không đụng widget. Widget của app khác (AppWidgetHost) là một tính năng lớn, cần plan riêng nếu chủ dự án muốn.

## Thiết kế chi tiết

### `IconStyle.GLASS` (Task 1)
- Thêm ở **cuối** enum: `DEFAULT, TINTED, THREE_D, GLASS`. Không dùng màu tô: `IconStyleSource` đã ép `tint = DEFAULT` khi kiểu khác `TINTED`.
- Chuỗi `settings_icon_style_glass` = "Kính". `SettingsActivity.labelRes()` thêm nhánh. Hộp chọn tự có thêm lựa chọn vì đọc `IconStyle.entries`.
- Tên file cache đĩa đã gồm `style.name` (`IconLoader.diskName`) nên bitmap GLASS không lẫn với kiểu khác. Đổi kiểu đi qua `RecreateGuard` có sẵn (G2b).

### `IconRenderer`, kiểu GLASS (Task 1)
Tạo nội dung `content` rồi `mask()` như mọi kiểu:
1. Tô nền vuông bằng màu `R.color.icon_glass_plate`. Giá trị khởi đầu `#2EFFFFFF` (trắng ~18%), không đổi theo sáng/tối hệ thống.
2. **Adaptive có glyph tách được** (`foregroundIsGlyph == true`): vẽ `foreground` với `ColorMatrix` đưa mọi màu về trắng, giữ alpha. Hàng R/G/B là `[0,0,0,0,255]`, hàng A là `[0,0,0,1,0]`. Glyph co `TINT_GLYPH_SCALE` quanh tâm, như `tintGlyphTile`.
3. **Còn lại** (legacy, foreground phủ kín): dùng `flatten()` vào bitmap tạm. Vẽ bitmap tạm lên `content` bằng `ColorMatrix` bão hòa 0 và paint alpha `GLASS_FALLBACK_ALPHA = 0.7f` (giá trị khởi đầu, chỉnh ở Task 5), để hình xám mờ vẫn để kính lộ qua. Thu hồi bitmap tạm.
4. `mask(content, sizePx)` như cũ. Không bevel.

Tách phần dựng `ColorMatrix` theo khoảng màu ra khỏi `tintGlyphTile` **chỉ khi** dùng lại được nguyên văn; không bắt buộc. Hành vi TINTED, THREE_D, DEFAULT phải giữ y nguyên.

### `BackdropBlurDrawable.shapePath` (Task 2)
- `var shapePath: Path? = null`: hình theo toạ độ của drawable (gốc ở `bounds.left/top`). Khác null thì tô và viền vẽ theo path thay cho `drawRoundRect`. `cornerRadiusPx` bị bỏ qua.
- Không cấp phát trong `draw`. Dùng `workPath` (một `Path`) và `pathMatrix` (một `Matrix`) là trường của lớp.
  - Đường thường: `shapePath.offset(bounds.left + tx, bounds.top + ty, workPath)`, rồi vẽ `workPath` sau `canvas.translate(-tx, -ty)`.
  - Đường `drawScaled`: `pathMatrix` gồm dịch về bounds, scale (sx, sy), rồi dịch `tx/ty`, sau đó `shapePath.transform(pathMatrix, workPath)`.
- Viền theo path: không inset được path, nên vẽ `workPath` bằng stroke dày như cũ **trong `canvas.clipPath(workPath)`**, để nửa trong của nét hiện ra và nửa ngoài bị cắt. Chỉ vẽ viền khi `owner.rotation == 0f`, như hiện tại. Ô đang rung chỉ tốn một lệnh tô (GL Task 6a), không có `clipPath`.
- Khi `shapePath == null`, mọi đường vẽ giữ nguyên từng lệnh (ô thư mục, widget, hover, panel không đổi).

### `IconCellView`, kính sau icon app (Task 3)
- `fun bind(item: AppItem, icon: Bitmap?, cells: GridMetrics.Cells, plate: GlassEnvironment? = null)`. Tham số `plate != null` nghĩa là ô được cấp kính.
  - Tạo `iconGlass: BackdropBlurDrawable` một lần, tái dùng như `folderGlass`. Style là `GlassStyle(tint = icon_glass_tint, border = icon_glass_border, flat = icon_glass_flat)`, màu mới ở `values/colors.xml`.
  - `shapePath = IconShape.DEFAULT.path(iconSize)`, chỉ dựng lại khi `iconSize` đổi. Giữ trong trường `iconPlatePath` + `iconPlatePathSize`.
  - `bindFolder`, `bindWidget` và `bind` không có `plate` thì đặt `iconGlass = null` (bài học A1a: ô được tái dùng giữa các loại item).
- `onDraw` nhánh app: nếu `iconGlass != null` thì `setBounds(left, 0, left + iconSize, iconSize)` rồi `draw(canvas)` **trước** `drawBitmap(icon)`.
- `invalidateGlass()`, phần vẽ lại khi scale đổi (dòng 163, 286) và `onDetachedFromWindow` (`cancelPending()`) phải tính cả `iconGlass`, như `folderGlass`/`widgetGlass`.
- Ảnh kéo `snapshotWithoutBadge` đi qua canvas phần mềm. `BackdropBlurDrawable` đã xử lý trường hợp này (scale = 1). Kiểm tay ở Task 5.

### Cấp kính cho ô: `GlassBudget`, `IconGridView`, `DockView` (Task 4)
- `GlassBudget.maxIconPlatesPerPage(tier: GlassTier, realGlassCapable: Boolean): Int`:
  - `tier == LENS && realGlassCapable` → `Int.MAX_VALUE` (chưa đo, Task 5 đo trên Oppo).
  - Còn lại → `0`. Live 4 hiện chỉ đủ ngân sách 6 ô kính tại chỗ mỗi trang, mà một trang có tới 24 icon. Icon chỉ có tấm nướng sẵn, nên cả trang trông đồng đều. Task 5 đo lại trên Live 4 và đổi số nếu đạt.
- `GlassEnvironment.iconPlatesPerPage(): Int` = `0` nếu `modeFor(GlassRole.FOLDER_CELL) == FLAT`, ngược lại là `GlassBudget.maxIconPlatesPerPage(tier, realGlassCapable)`.
- **Kiểu icon tới view:** `IconGridView` và `DockView` có `var glassIcons: Boolean = false`. Setter khác giá trị cũ thì bind lại bằng `bindAllCells` riêng của từng view (cả hai view đều có hàm private cùng tên). `HomePagerController` và `DockController` gán cờ này sau khi đọc `viewModel.currentIconStyleKey().style == IconStyle.GLASS` một lần lúc `onCreate`. Đổi kiểu thì Activity được tạo lại (`RecreateGuard`), nên không cần nghe Flow.
- **`IconGridView`:**
  - Ở `bindAllCells`, tính `glassIconIds` = các `PlacedApp` của trang theo thứ tự đọc, lấy `iconPlatesPerPage()` ô đầu (khi `glassIcons`).
  - Hai chỗ `cell.bind(...)` (dòng 1021 và 1116) truyền `plate = glassEnv` nếu `itemId in glassIconIds`.
  - **Không** trừ vào ngân sách ô thư mục/widget: icon có ngân sách riêng.
  - `invalidateGlass()` không được thoát sớm khi `glassIconIds` khác rỗng. Bài học G2c: trang chưa hiện của `ViewPager2` phải vẽ lại khi cuộn, nếu không sẽ thành vệt CLAMP.
  - `glassListener` (mức kính đổi lúc chạy) đã gọi `bindAllCells`, nên không phải sửa.
- **`DockView`:** hai chỗ `cell.bind(...)` (dòng 469 và 495) truyền `plate = glassEnv` khi `glassIcons && glassEnv.iconPlatesPerPage() > 0`. Dock không cuộn, không cần `invalidateGlass`. Dock nằm trên kính dock: kính icon lấy mẫu ảnh nền, không lấy mẫu kính dock. Chấp nhận, kiểm bằng mắt ở Task 5.

## Vùng file

| File | Tạo / Sửa | Nóng | Task |
|---|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/domain/model/IconStyle.kt` | Sửa: thêm `GLASS` | Không | 1 |
| `app/src/main/java/gambi/global/os26launcher/data/apps/IconRenderer.kt` | Sửa: nhánh GLASS | Không | 1 |
| `app/src/main/java/gambi/global/os26launcher/ui/settings/SettingsActivity.kt` | Sửa: `labelRes` | Không | 1 |
| `app/src/main/res/values/strings.xml`, `app/src/main/res/values/colors.xml` | Sửa: chuỗi "Kính", 4 màu `icon_glass_*` | Không | 1, 3 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/BackdropBlurDrawable.kt` | Sửa: `shapePath` | Có | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconCellView.kt` | Sửa: `iconGlass`, tham số `plate` | Không | 3 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassBudget.kt`, `.../core/glass/GlassEnvironment.kt` | Sửa: `maxIconPlatesPerPage`, `iconPlatesPerPage` | Có | 4 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconGridView.kt`, `.../ui/home/view/DockView.kt` | Sửa: `glassIcons`, `glassIconIds`, truyền `plate` | Không | 4 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/HomePagerController.kt`, `.../ui/home/controller/DockController.kt` | Sửa: gán `glassIcons` | Không | 4 |
| `docs/plans/GI-glass-icons.md`, `docs/LauncherOS-LiquidGlass-RE.md`, `docs/knowledgements/GI-glass-icons.md` | Tạo | Không | 0, 6 |

**Không sửa:** `GlassPolicy.kt`, `GlassRole.kt`, `RealGlassSurface.kt`, `StaticBlurSurface.kt`, widget G2c, schema, plan LQ.

## Global Constraints
- Không test tự động, không dependency mới, không đổi schema.
- Không chép mã dịch ngược từ APK Launcher OS; chỉ dùng kỹ thuật đã ghi trong spec.
- Không cấp phát trong `draw`/`onDraw`/`dispatchDraw`; không có vòng `invalidate()` liên tục.
- Kiểu DEFAULT, TINTED và THREE_D, cùng ô thư mục, widget, hover, panel phải giữ y nguyên điểm ảnh. Kiểm bằng `screencap` trước/sau, như bài học GL.
- Commit tiếng Anh, không dấu. `gradlew.bat assembleDebug` phải xanh trước khi báo xong mỗi Task.
- Máy kiểm: Vsmart Live 4 cho phần không kính (kiểu icon, tấm nướng, chạy đường không có kính). Oppo A3x cho kính sau icon.

## Review Focus
1. **Đổi kiểu qua lại** Mặc định → Kính → Tô màu → Kính: không còn bitmap cũ ở Home, dock, thư mục, tìm kiếm. Tên cache đĩa khác nhau và `RecreateGuard` tạo lại Activity. Task 5 kiểm bước này.
2. **Trang có kính lúc vuốt:** trang vuốt tới không có vệt ngang CLAMP, kính bám nền suốt lúc trượt (cách lỗi G2c Task 5). Task 4 lo `invalidateGlass`, Task 5 kiểm.
3. **Ô đang rung, ô phóng khi hover, ô co khi nhấn, ảnh bay theo tay:** kính bám đúng hình icon, không lệch nửa ô. Đây là đường `drawScaled` có `shapePath`. Task 2 phải đi đủ hai đường vẽ, Task 5 kiểm.
4. **Mức kính "Tắt" (FLAT) hoặc máy CLASSIC:** chỉ có tấm nướng, không kính tại chỗ. Đổi mức kính lúc chạy thì ô vẽ lại đúng kiểu (`glassListener`). Task 4 lo, Task 5 kiểm trên Live 4.
5. **Icon legacy và icon foreground phủ kín** (game, ảnh): vẫn nhận ra được, không thành ô xám đặc che hết kính. `GLASS_FALLBACK_ALPHA` ở Task 1, chỉnh theo mắt ở Task 5.

## Tasks

### GI1 — Kiểu icon Kính
- [ ] **Task 0 — Plan + spec.** File này và `docs/LauncherOS-LiquidGlass-RE.md`. Commit `docs: add GI plan and Launcher OS glass analysis`.
- [ ] **Task 1 — `IconStyle.GLASS` + renderer + Cài đặt.** Theo mục "IconStyle.GLASS" và "IconRenderer". Màu `icon_glass_plate` thêm ở task này. Build xanh. Commit `feat: add glass icon style with white glyphs`.
- [ ] **Task 2 — `BackdropBlurDrawable.shapePath`.** Theo mục cùng tên. Mọi nơi gọi cũ không đặt `shapePath`, nên không đổi. Build xanh. Commit `feat: let in-place glass follow a custom path`.
- [ ] **Task 3 — Kính sau icon trong `IconCellView`.** Theo mục cùng tên. Thêm màu `icon_glass_tint`, `icon_glass_border`, `icon_glass_flat`, khởi đầu bằng giá trị của `gl_cell_*`. Build xanh. Commit `feat: draw a glass plate behind app icons`.
- [ ] **Task 4 — Cấp kính ở lưới và dock.** Theo mục "Cấp kính cho ô". Build xanh. Commit `feat: give home and dock icons glass plates in glass style`.
- [ ] **Task 5 — Kiểm tay và đo (chủ dự án / điều phối).**
  - Live 4: chọn Kính. Icon adaptive thành glyph trắng trên tấm trắng mờ. Icon legacy nhận ra được. Thư mục, Library, tìm kiếm, menu nhấn giữ, ảnh kéo đều có tấm. Đổi kiểu qua lại theo Review Focus 1. Ảnh chụp các kiểu cũ y nguyên so với `main`.
  - Oppo: icon lưới và dock có kính bám nền khi vuốt trang, rung, nhấn, kéo, hover tạo thư mục. Ảnh nền sáng và tối.
  - Đo Oppo (cách GL Task 5 / GS Task 6, xen kẽ và lặp cấu hình gốc cuối phiên): vuốt trang 30 giây và rung, so Mặc định với Kính trên trang 24 icon. Ghi Janky, p95, số khung > 33 ms.
  - Nếu chủ dự án muốn kính cho Live 4: đo một lượt với `maxIconPlatesPerPage` CLASSIC = 24 bằng bản tạm, không commit, rồi quyết số.
  - Chỉnh theo mắt: `icon_glass_plate`, `icon_glass_tint`, `icon_glass_border`, `GLASS_FALLBACK_ALPHA`, `TINT_GLYPH_SCALE` cho kiểu Kính.
- [ ] **Task 6 — Docs.** Knowledgement GI, nhật ký, mục mới trong `AGENTS.md`, ghi số đo và quyết định ngân sách. Commit `docs: record GI results`.

## Ngoài phạm vi (đề xuất plan riêng nếu cần)
- Widget của app khác (AppWidgetHost) có nền kính.
- Khúc xạ mép (shader AGSL) và viền SweepGradient cho tấm kính icon: thuộc plan LQ, áp sau nếu LQ được làm.
- Chọn màu glyph khác trắng (theo theme như icon pack của Launcher OS).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | Phân tích | REA/JADX trên APK Launcher OS 7.0.1 lấy từ SM-A176B qua adb; viết `docs/LauncherOS-LiquidGlass-RE.md` | — | Chỉ phân tích tĩnh |
| 2026-11-10 | 0 | Viết plan | — | Chờ chủ dự án duyệt |
