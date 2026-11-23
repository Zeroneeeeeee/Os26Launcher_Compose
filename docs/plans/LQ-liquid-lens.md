# LQ — Viền sáng SweepGradient và kính khúc xạ bằng RenderEffect (học từ Launcher OS) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo, qua review mới tick; mỗi mốc (LQ1, LQ2) kiểm tay một lần ở cuối mốc. Dự án **không viết test tự động**. Agent thực thi không dùng máy thật (không adb). Lệnh viết cho PowerShell.

**Nhánh:** `feat/lq-liquid-lens` (tách từ `main`) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (chủ dự án: "áp thử đi")
**Trạng thái:** Đang làm
**Khóa:** không đổi schema DB, không thêm cài đặt, không thêm dependency, không gỡ thư viện QWEA0. **Có chạm `core/glass/*`** (vùng nóng): chỉ thêm file mới và sửa phần viền của hai lớp kính tại chỗ, cộng một hằng debug trong `GlassSurfaceFactory`.

**Goal:**
- **LQ1:** kính tại chỗ (`BackdropBlurDrawable`, `StaticBlurSurface`) có viền sáng kiểu iOS 26: nét viền `SweepGradient` ép elip theo tỉ lệ khung, hai đốm sáng chéo nhau (trên-trái, dưới-phải), phần còn lại gần trong suốt. Không thêm lệnh vẽ.
- **LQ2:** thử một loại kính thật mới cho dock trên máy API 33+: lát ảnh nền đã mờ (đường kính tại chỗ có sẵn) + `RenderEffect.createRuntimeShaderEffect` khúc xạ mép bằng shader AGSL tự viết. Không chụp nền, không vẽ lại theo cuộn. Đo so với `RealGlassSurface` (`LiquidGlassView`) trên Oppo A3x để quyết có mở rộng hay không.

**Architecture:** thêm `GlassRim` (viền), `RefractionShader` (AGSL + dựng `RenderEffect`), `LensRefractionSurface : GlassSurface` (khung chứa: view lát nền có `RenderEffect` + viền vẽ đè không bị khúc xạ). `GlassPolicy` và `GlassMode` giữ nguyên; LQ2 chỉ bật qua hằng debug trong `GlassSurfaceFactory` khi chính sách đã chọn `REAL` cho dock.
**Tech Stack:** Kotlin, View, `android.graphics.RuntimeShader` / `RenderEffect` (API 33). Không dependency mới.

---

## Đầu vào: Launcher OS làm thế nào (phân tích tĩnh, 2026-11-10)

Phân tích `com.babydola.launcherios` (APK base sha256 `036c408b…`) bằng REA/JADX, chỉ đọc code đã dịch ngược, không chạy app:
- **Nguồn nền:** ảnh wallpaper cỡ màn hình thu 0,12, stack blur native bán kính 4 (2 lượt), làm một lần mỗi khi nền đổi. Mỗi phần tử kính cắt đúng lát của bitmap theo `getLocationOnScreen`, nhân scale của các cha, rồi `drawBitmap` + tint `SRC_ATOP`. Cùng ý với `BlurredBackdropCache` + `BackdropBlurDrawable` của dự án.
- **Khúc xạ (API 33+, chế độ "OS 26"):** view lát nền gắn `setRenderEffect(chain(ColorFilter → Blur → RuntimeShader "Refraction"))`. Shader dùng SDF hình chữ nhật bo góc, chỉ dịch pixel trong dải sát mép rộng `refractionHeight`, độ lệch theo cung tròn `1 − √(1 − x²)` nhân `refractionAmount` (âm: lấy mẫu vào trong), hướng theo pháp tuyến SDF, có hệ số kéo về tâm. Tham số thực tế: dock-kiểu `0,1 / 0,1` và nút `0,5 / 0,2` (tỉ lệ cạnh ngắn). Dưới API 33 rơi về lát bitmap phẳng.
- **Viền:** không phải shader khúc xạ, chỉ là nét `SweepGradient` 1–2 dp, local matrix ép elip; 8 điểm màu: nền `#0DFFFFFF`, đốm `#BFFFFFFF` ở 0,13 và `#FFFFFFFF` ở 0,63.
- **Tên tham số shader** (`refractionHeight`, `refractionAmount`, `depthEffect`, "Backdrop") trùng thư viện mở AndroidLiquidGlass của Kyant: kỹ thuật đã công khai. **Quy tắc LQ: tự viết shader và viền theo kỹ thuật, không chép mã đã dịch ngược.**

## Vì sao chọn thiết kế này

| | A. Giữ `LiquidGlassView` cho dock (hiện tại) | B. `LensRefractionSurface` (chọn để thử) |
|---|---|---|
| Lấy nền | Thư viện chụp nền khi vẽ kính; `invalidate()` theo mọi `onScrollChanged` (TL1 2b phải chặn) | Lát bitmap mờ nhỏ có sẵn, không chụp |
| Khúc xạ | Có (lens pipeline của thư viện) | Có (shader tự viết, một lượt GPU qua `RenderEffect`) |
| Khi nền đổi | Phải `captureGlassOnce` (DG) | `invalidate()` lát nền là đủ (drawable nhận ra bitmap mới) |
| Nội dung bị khúc xạ | Chỉ ảnh nền (backdropSource là `ImageView`) | Chỉ ảnh nền, như nhau |
| API | Thư viện tự chọn tier | Chỉ API 33+; dưới đó dùng `StaticBlurSurface` |
| Rủi ro | Đã đo, đã biết | Chưa đo: chi phí `RenderEffect` trên GPU Oppo, độ mịn khi phóng bản nhỏ |

**Vì sao B chỉ cho dock và sau hằng debug:** dock là kính thật luôn hiện, đứng yên, có số đo cũ để so; mở rộng sang pill/panel/menu cần số đo của LQ2 và một plan riêng (có thể thêm công tắc trong mức Tùy chỉnh).

**Vì sao viền là view/lệnh vẽ riêng ở LQ2:** `RenderEffect` áp lên toàn bộ nội dung của view mang nó; vẽ viền trong cùng view thì viền bị bẻ cong. Khung chứa vẽ viền trong `dispatchDraw` sau con, khung chứa không mang hiệu ứng.

## Thiết kế chi tiết

### `GlassRim` (LQ1)
- Đầu vào: màu đốm (lấy từ `GlassStyle.border`, đã có alpha theo vai trò), độ dày (dp). Đầu ra: `Paint` STROKE có `SweepGradient`.
- `update(width, height)`: chỉ dựng lại khi kích thước đổi; tâm giữa khung, local matrix `scale(1, h/w)` quanh tâm (hoặc ngược lại theo cạnh dài) để vệt sáng bám góc của hình chữ nhật dài.
- Bảng màu (tự đặt, chỉnh theo mắt ở Task 3): nền = màu viền alpha ~5%; đốm chính 100% màu viền ở góc trên-trái, đốm phụ ~75% ở góc dưới-phải; nhớ `SweepGradient` của Android bắt đầu ở hướng 3 giờ, chạy theo chiều kim đồng hồ.
- Không cấp phát trong `draw`. Ô đang rung vẫn bỏ viền như cũ (GL Task 6a).

### `RefractionShader` (LQ2)
- Chuỗi AGSL tự viết: `uniform shader content; uniform float2 size; uniform float radius; uniform float band; uniform float amount; uniform float centerPull;`.
- `main(coord)`: toạ độ tâm `p = coord − size/2`; `d` = SDF hình chữ nhật bo góc; nếu `−d ≥ band` trả `content.eval(coord)`; ngược lại `x = 1 + d/band` (0 ở mép trong của dải, 1 ở mép kính), độ lệch `amount · (1 − sqrt(1 − x²))`, hướng = pháp tuyến SDF (giải tích, chuẩn hoá an toàn) cộng `centerPull · normalize(p)`; lấy mẫu `coord − dir · lệch`, **kẹp vào [0, size]** (ngoài view `RenderEffect` trả trong suốt).
- `@RequiresApi(33)` `fun effect(w, h, radiusPx, band, amount): RenderEffect`: một `RuntimeShader` mỗi surface, chỉ `setFloatUniform` lại khi kích thước/bo góc đổi rồi `createRuntimeShaderEffect(shader, "content")`.
- Giá trị khởi đầu cho dock: `band = 0,10 · cạnh ngắn`, `amount = 0,10 · cạnh ngắn`, `centerPull = 1` (chỉnh theo mắt ở Task 7).

### `LensRefractionSurface` (LQ2)
- `view` = `FrameLayout` (vẽ viền `GlassRim` trong `dispatchDraw` sau con) chứa `lensView` MATCH_PARENT.
- `lensView.background = BackdropBlurDrawable(lensView, backdrop, cache, GlassStyle.forRole(DOCK), drawBorder = false)`; `lensView.setRenderEffect(...)` dựng lại ở `onSizeChanged` và `setCornerRadius`.
- `setBackdrop(source, cache)`: lưu nguồn, tạo drawable nếu chưa có, `lensView.invalidate()` (thay cho `captureGlassOnce` của kính thật: drawable tự nhận bitmap mới ở lần vẽ).
- `setTint`: dock hiện không gọi (chỉ `BackgroundGlass`, `SearchPillView`); bản LQ2 bỏ qua, ghi chú trong KDoc.
- `setDynamic`, `setInvalidationSuppressed`, `setAppearanceListener`: no-op (lát nền tự đúng vị trí mỗi lần vẽ; dock không cuộn).
- `onDetachedFromWindow` của `lensView`: `cancelPending()` như các nơi dùng drawable.
- `DockView` đã `measure()` `glassSurface.view` có con (M6 Task 2) nên không phải sửa layout.

### `BackdropBlurDrawable` (sửa nhỏ)
- LQ1: thay `borderPaint` màu đơn bằng `GlassRim` (cả `draw` và `drawScaled`; ở `drawScaled` độ dày nhân scale như cũ).
- LQ2: thêm tham số `drawBorder: Boolean = true` (mặc định giữ hành vi cũ cho mọi nơi gọi).

### `GlassSurfaceFactory` (LQ2)
- `private const val LENS_REFRACTION_DOCK = false` (debug, bật tay khi đo). Khi `true`, `GlassMode.REAL` và `Build.VERSION.SDK_INT >= 33` → `LensRefractionSurface`; còn lại giữ nguyên. Dùng `SDK_INT` ở đây là cố ý: đường này không qua thư viện (AGENTS.md: `effectiveApiLevel` là tier của thư viện QWEA0).

## Vùng file

| File | Tạo / Sửa | Nóng | Task |
|---|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassRim.kt` | Tạo | Có | 1 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/BackdropBlurDrawable.kt` | Sửa: viền dùng `GlassRim`; tham số `drawBorder` | Có | 1, 5 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/StaticBlurSurface.kt` | Sửa: viền dùng `GlassRim` | Có | 2 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/RefractionShader.kt` | Tạo | Có | 4 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/LensRefractionSurface.kt` | Tạo | Có | 5 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurfaceFactory.kt` | Sửa: hằng debug + nhánh API 33 | Có | 6 |
| `docs/plans/LQ-liquid-lens.md`, `docs/knowledgements/LQ-liquid-lens.md` | Tạo | Không | 0, 8 |

**Không sửa:** `GlassPolicy.kt`, `GlassMode.kt`, `RealGlassSurface.kt`, `DockView.kt`, `DockController.kt`, schema, cài đặt.

## Global Constraints
- Không test tự động, không dependency mới, không đổi schema, không thêm cài đặt.
- Không chép mã dịch ngược từ APK khác; shader và viền tự viết.
- Không cấp phát trong `draw`/`dispatchDraw`; không vòng `invalidate()` liên tục.
- Hằng `LENS_REFRACTION_DOCK` để `false` khi merge (trừ khi chủ dự án quyết khác sau đo).
- Commit tiếng Anh, không dấu; `gradlew.bat assembleDebug` xanh trước khi báo xong mỗi Task.

## Review Focus
1. Viền `GlassRim`: không cấp phát mỗi lần vẽ; shader chỉ dựng lại khi cỡ đổi; `drawScaled` vẫn đúng độ dày; ô rung vẫn bỏ viền.
2. Shader: lấy mẫu luôn trong [0, size]; không chia cho 0 ở tâm/góc (chuẩn hoá an toàn); vùng ngoài dải trả đúng `content.eval(coord)`.
3. `LensRefractionSurface`: viền không bị khúc xạ; `RenderEffect` chỉ dựng lại khi cỡ/bo góc đổi; nền đổi thì lát nền đổi ngay (không cần `captureGlassOnce`).
4. Máy API < 33 không bao giờ chạm `RuntimeShader` (kể cả khi bật hằng debug).
5. Mọi nơi gọi `BackdropBlurDrawable` cũ không đổi hành vi ngoài kiểu viền.

## Tasks

### LQ1 — Viền sáng
- [ ] **Task 0 — Plan.** File này. Commit `docs: add LQ plan`.
- [ ] **Task 1 — `GlassRim` + `BackdropBlurDrawable`.** Commit `feat: add sweep-gradient rim to in-place glass`.
- [ ] **Task 2 — `StaticBlurSurface` dùng `GlassRim`.** Commit `feat: use sweep-gradient rim on static blur glass`.
- [ ] **Task 3 — Kiểm tay LQ1 (chủ dự án).** Oppo A3x và Vsmart Live 4: nhìn viền trên ô thư mục, ô nhóm, pill, thanh tìm kiếm, dock mờ (Live 4), sáng/tối hệ thống; chỉnh màu/độ dày theo mắt. Đo Live 4 vuốt trang và rung (`gfxinfo` / framestats, cùng cách GL Task 5): Δp95 so với `main` không xấu hơn nhiễu đo.

### LQ2 — Kính khúc xạ cho dock (thử)
- [ ] **Task 4 — `RefractionShader`.** Commit `feat: add AGSL edge refraction shader`.
- [ ] **Task 5 — `LensRefractionSurface` + `drawBorder`.** Commit `feat: add RenderEffect lens glass surface`.
- [ ] **Task 6 — Nhánh debug trong `GlassSurfaceFactory`.** Commit `feat: allow lens refraction glass on the dock behind a debug flag`.
- [ ] **Task 7 — Đo trên Oppo A3x (điều phối/chủ dự án).** Mức kính Cao, dock: (a) `RealGlassSurface` (hằng `false`) và (b) `LensRefractionSurface` (hằng `true`), mỗi cấu hình 2 lượt: G1 đứng yên 30 s, G2 vuốt trang 30 s, đổi nền ảnh→ảnh và none→ảnh (dock đúng màu ngay, cách đo DG). Nhìn: mép có bẻ cong rõ không, có lộ ô vuông khi phóng bản nhỏ không; chỉnh `band`/`amount`.
- [ ] **Task 8 — Docs.** Knowledgement + nhật ký, ghi quyết định mở rộng hay dừng. Commit `docs: record LQ results`.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | Phân tích | REA/JADX trên APK Launcher OS lấy từ Galaxy A17 qua adb; ba hướng: luồng cài đặt, shader, nguồn blur | — | Chỉ phân tích tĩnh |
| 2026-11-10 | 0 | Viết plan | — | Chờ chủ dự án duyệt |
