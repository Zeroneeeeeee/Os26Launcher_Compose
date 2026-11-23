# PG3 — Kính mờ tĩnh: ô thư mục, pill, icon kiểu "Kính", và kính trên Android 8–11 (G4) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code từng mốc nhỏ, báo cáo, chủ dự án build và kiểm; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi. Máy: Oppo PKD130 (Android 14) và máy Android 11.

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** 2026-11-23 (chủ dự án trả lời Q-A, Q1–Q6)
**Trạng thái:** Xong 2026-11-23 (chủ dự án build và kiểm tay đạt trên Vsmart Live 4 + Oppo PKD130). Task 4 bỏ (Q1); Task 6 (đo) bỏ qua theo chủ dự án, chuyển sang G3
**Nhánh:** `feature/pg1-parity` (đã merge `origin/master` `bb3e042`) · **Máy:** Oppo PKD130 + Vsmart Live 4 (Android 11) · **Đổi schema Room:** không

**Goal:** Ô thư mục và pill tìm kiếm có lại tấm kính mờ phía sau như bản XML (icon kiểu "Kính": bỏ, Q1); trên Android 8–11 (API 26–30), các bề mặt kính thật (CARD/SHEET) của Home không còn phẳng mà là kính mờ tĩnh. Máy API ≥ 31 giữ nguyên hành vi của mọi bề mặt đang có (chủ dự án 2026-11-23).
**Architecture (đề xuất, Q-A):** một nguồn "ảnh nền đã làm mờ" dùng chung (bản nhỏ của `BlurredBackdropCache`, dựng một lần ngoài main thread), ghi thành một lớp `LayerBackdrop` thứ hai của Home; bề mặt kính mờ tĩnh vẽ lát cắt của lớp đó bằng `drawBackdrop` **không hiệu ứng** (không `blur`/`lens`/`vibrancy`, nên không có RenderEffect), thư viện Backdrop tự lo vị trí lấy mẫu khi lướt trang, Today/Thư viện trượt, ảnh kéo bay.
**Spec:** `PG-parity-gaps.md` G4 (gộp G5, G6 cũ); repo XML: `BackdropBlurDrawable` (kính tại chỗ), `GlassBudget`, `plans/GL-glass-pass.md`, `plans/GI-glass-icons.md`; bài học `lessons/glass.md`.

## Hiện trạng (đọc code 2026-11-23)

- `Modifier.dsGlass` (`core/glass/compose/DsGlass.kt`): chỉ CARD/SHEET lấy mẫu nền (JD D9), và chỉ khi API ≥ 31 (`drawBackdrop` + `vibrancy` → `blur` → `lens`). API < 31 hoặc không có nền: chỉ vẽ `DsSurfaceDrawable` (màu phẳng 20 % + viền sáng). Dưới API 31 thư viện vẽ nền mà không hiệu ứng nào, nên hiện tại không gọi.
- Home (`HomeScreen.kt`): `Wallpaper(state.backdrop, Modifier.layerBackdrop(backdrop))`, `LocalDsBackdrop provides backdrop` cho lưới, dock, menu. Thư mục mở thì cả lớp Home ghi thành `homeBackdrop` (kính bảng thư mục thấy icon phía sau).
- Phẳng ở mọi API: `HomeFolderPlate` `.background(folder_background)` (lưới, dock, ảnh kéo `HomeDragLayer`), pill `HomeSearchPill` `.background(search_pill_bg)`, icon kiểu "Kính": `IconRenderer` nướng tấm trắng 8 % + glyph, không có kính mờ phía sau (XML: `BackdropBlurDrawable` theo path `IconShape`, GI Task 4).
- Today / Thư viện: nền là ảnh nền thu nhỏ `shrinkForBlur` (cạnh dài cố định) + `wallpaperSoftBlur()` (RenderEffect, chỉ API ≥ 31). Dưới 31 nền chỉ là bản thu nhỏ phóng lên (lộ khối, ghi chú JS 2026-11-20); card bên trong phẳng.
- Có sẵn, chưa ai gọi trong Compose: `BlurredBackdropCache` (thu chia đôi + box blur 3 lượt, ngoài main thread, cache theo danh tính bitmap, `trim` khi `TRIM_MEMORY_COMPLETE`, bán kính theo "Độ mờ"), `GlassStyle.forRole` (tint/viền/màu phẳng cho FOLDER_CELL, SEARCH_PILL, ICON_PLATE...), `GlassBudget` (XML: 6 ô thư mục kính mỗi trang trên máy dưới API 33, icon có tấm kính chỉ trên API ≥ 33). `BackdropBlurDrawable` là bản View (cần `ImageView` ảnh nền), không dùng thẳng được.

## Vì sao chọn thiết kế này

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| A. Port `BackdropBlurDrawable` sang Compose | `drawBehind` vẽ `ImageShader` của bản mờ nhỏ, dịch theo vị trí phần tử so với ảnh nền (`positionInRoot`) | Như XML, kiểm soát từng lệnh vẽ | Phải tự bám vị trí: đổi vị trí do lớp cha `graphicsLayer` (trượt Today/Thư viện, rung, dời A1) không tự vẽ lại phần tử; dễ lệch nền như lỗi G2c/GL của bản View | Không |
| B. Lớp nền mờ thứ hai + `drawBackdrop` không hiệu ứng | Ghi ảnh nền đã mờ thành `frostedBackdrop` (`rememberLayerBackdrop`), bề mặt vẽ lát cắt bằng `drawBackdrop(frostedBackdrop, shape, effects = {})` | Thư viện đã bám vị trí đúng cho kính thật (lướt trang, trượt, rung); không RenderEffect nên rẻ; chạy cả dưới API 31 (thư viện vẽ nền không hiệu ứng); ít code | Thêm một lớp vẽ ảnh nền nhỏ dưới ảnh nền thật (vẽ đè một lần khi lớp Home vẽ lại); chưa đo chi phí `drawBackdrop` dưới API 31; `drawBackdrop` có thể đòi hình bo góc (icon dùng siêu elip) | **Đề xuất** (Q-A) |

- **Vì sao không kính thật:** G3 (đo trước) lo số kính thật; JD D9 không cho thành phần nhỏ. Kính mờ tĩnh không chạy hiệu ứng mỗi khung nên không vướng D9.
- **Lớp nền mờ phải che kín, không hiện ra:** đặt dưới `Wallpaper` trong cùng Box (ảnh nền thật đục che hết); nếu ảnh nền là màu phẳng / preset (không có bitmap) thì không ghi lớp mờ, bề mặt vẽ màu phẳng `GlassStyle.flat` như XML.
- **Bản mờ đúng hình học:** bản nhỏ của `BlurredBackdropCache` theo tỉ lệ VIEW (FX8), không theo tỉ lệ bitmap; vẽ bằng đúng phép Crop của ảnh nền gốc (cỡ intrinsic của bitmap gốc), như `BackdropBlurDrawable.prepareShader`.

## Quyết định đã chốt

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Không dùng kính thật cho ô thư mục, pill, icon; dùng kính mờ tĩnh như XML | Chủ dự án 2026-11-23 (G4) |
| 2 | Android 8–11: giữ mọi thứ máy đó còn dùng được, bề mặt kính thành kính mờ tĩnh | Chủ dự án 2026-11-23 (QĐ 5 của PG1, G4) |
| 3 | API ≥ 31: mọi bề mặt `dsGlass` đang có không đổi hành vi (nhánh mới chỉ vào khi `SDK_INT < 31`) | Chủ dự án 2026-11-23 (QĐ 6 của PG1) |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q-A | Chọn phương án B (lớp nền mờ thứ hai + `drawBackdrop` không hiệu ứng)? | B; Task 1 là bước thử trên máy Android 11 trước khi làm tiếp, B hỏng thì quay về A | **B** (chủ dự án 2026-11-23) |
| Q1 | Icon kiểu "Kính" có tấm kính mờ trên máy nào? XML chỉ bật ở API ≥ 33 (máy yếu chỉ đủ 6 ô kính tại chỗ mỗi trang, trang có 24 icon) | Như XML: API ≥ 33; Task 6 đo trên máy Android 11, đạt thì mở cho mọi máy | **Bỏ tấm kính sau icon "Kính"** ở mọi máy (chủ dự án 2026-11-23: Oppo đã giật khi lướt dù chưa bật, chưa rõ do icon hay gì; ghi vào G3). Task 4 bỏ |
| Q2 | Trên Android 8–11, bề mặt nào thành kính mờ tĩnh? | CARD/SHEET của Home, Today, Thư viện (dock, widget, menu nhấn giữ, menu Sửa, bảng thư mục, card Today/Thư viện). Màn Cài đặt / Onboarding (nền aurora động, `DsScreen`) giữ phẳng như hiện tại: nền động không làm mờ trước được | Đồng ý |
| Q3 | Bảng thư mục trên Android 8–11: kính lấy mẫu ảnh nền đã mờ (không thấy icon phía sau như API ≥ 31) được không? | Được (XML máy yếu cũng vậy); máy ≥ 31 không đổi | **Không: phải thấy cả icon phía sau** (chủ dự án 2026-11-23) → Task 2 Step 3: chụp lớp Home một lần lúc mở thư mục, làm mờ ngoài main thread, dùng làm nguồn cho bảng |
| Q4 | Nền Today / Thư viện trên Android 8–11 đang lộ khối (chỉ thu nhỏ rồi phóng): đổi sang bản mờ thật của `BlurredBackdropCache`? | Có, chỉ nhánh `SDK_INT < 31`; máy ≥ 31 giữ `shrinkForBlur` + `wallpaperSoftBlur` | Đồng ý |
| Q5 | Ngân sách số bề mặt kính mờ mỗi trang? | Chưa giới hạn; Task 6 đo. Nếu khung giật tăng rõ (Janky +2 điểm hoặc p95 +3 ms so với bản phẳng, cùng phiên) thì áp số của XML (`GlassBudget`: 6 ô thư mục mỗi trang dưới API 33) | Đồng ý |
| Q6 | Máy Android 11 dùng để kiểm và đo là máy nào (tên máy để ghi vào plan)? | — | **Vsmart Live 4** |

## Ngoài phạm vi / ghi để sau

- Số kính thật mỗi trang và lướt trang trên Samsung (G3, đo riêng).
- Màn Cài đặt / Onboarding dưới API 31 (Q2).
- Kính mờ cho thanh tìm kiếm của lớp tìm kiếm (`SearchOverlay`, CONTROL) và pill/thanh của Thư viện, Today: không thuộc G4 (giữ JD D9), ghi lại nếu chủ dự án muốn.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `core/glass/compose/DsFrosted.kt` (`rememberFrostedWallpaper`, `FrostedWallpaperLayer`, `LocalDsFrostedBackdrop`, `Modifier.dsFrosted`) | Tạo | 1, 2 |
| `core/glass/compose/DsGlass.kt` (nhánh `SDK_INT < 31` cho CARD/SHEET) | Sửa | 2 |
| `ui/home/compose/HomeScreen.kt` (lớp nền mờ dưới `Wallpaper`, cung cấp `LocalDsFrostedBackdrop`) | Sửa | 1 |
| `ui/home/HomeActivity.kt` (cung cấp cho `HomeDragLayer` ở tầng Activity) | Sửa | 3 |
| `ui/home/compose/HomeGridPage.kt` (`HomeFolderPlate`), `HomeSearchPill.kt` | Sửa | 3 |
| `ui/home/compose/today/HomeToday.kt`, `library/HomeLibrary.kt` (nền và nguồn kính dưới API 31) | Sửa | 5 |
| `docs/plans/PG-parity-gaps.md`, `docs/lessons/glass.md`, `docs/lessons/README.md` | Sửa | 7 |

## Global Constraints

- `ui → domain ← data`; không đổi `domain`/`data`, không khóa config mới, không dependency mới, không chuỗi mới.
- API ≥ 31: `dsGlass` của mọi bề mặt đang có không đổi (QĐ 3). Phần mới ở API ≥ 31 chỉ là ô thư mục và pill (trước đây phẳng).
- Làm mờ chỉ một lần mỗi ảnh nền, ngoài main thread (`BlurredBackdropCache`); không cấp phát trong pha vẽ.
- Không tạo đối tượng `@RememberInComposition` lúc dựng ngoài `remember` (bài học PG1).
- Giữ CRLF; không viết `/*` trong comment Kotlin.

## Review Focus

1. Máy API ≥ 31: dock, widget, menu, bảng thư mục, card Today/Thư viện trông và chạy y như trước (so ảnh chụp trước/sau trên Oppo).
2. Kính mờ bám đúng nền: lướt trang, kéo Today / Thư viện vào ra, edit mode (rung), kéo ô thư mục (ảnh bay), dời A1, ô trong dock; không vệt kẹp (CLAMP) ở mép, không lệch nửa ô.
3. Ảnh nền đổi (chọn ảnh khác, đổi sang màu phẳng / preset, xoá): kính đổi theo hoặc về màu phẳng; không giữ bitmap cũ.
4. Bộ nhớ: chỉ thêm bản nhỏ (~0,5 MB); `trim` vẫn bỏ được; không giữ ảnh nền đầy đủ cũ.
5. Hiệu năng (Task 6): vuốt trang, rung, mở thư mục trên máy Android 11 và Oppo, so với bản phẳng cùng phiên.

---

### Task 1: Nguồn kính mờ tĩnh và bước thử trên máy Android 11
**Bắt buộc kiểm tay:** có (bước thử quyết định Q-A).
- [x] Step 1: `DsFrosted.kt`: `rememberFrostedWallpaper(bitmap, viewSize): ImageBitmap?` (gọi `GlassEnvironment.blurCache.smallOrRequest`, nghe `SmallListener` để cập nhật state; theo dõi "Độ mờ" của `GlassLook`); `FrostedWallpaperLayer(source, small)` vẽ bản nhỏ bằng phép Crop của bitmap gốc; `LocalDsFrostedBackdrop`.
- [x] Step 2: `HomeScreen`: đặt `FrostedWallpaperLayer(... Modifier.layerBackdrop(frostedBackdrop))` dưới `Wallpaper` trong cùng Box; `LocalDsFrostedBackdrop provides frostedBackdrop` cùng chỗ với `LocalDsBackdrop`. Không có bitmap (màu / preset) thì `null`.
- [x] Step 3: Gắn thử `drawBackdrop(frostedBackdrop, pill shape, effects = {})` cho pill tìm kiếm.
- [x] Step 4: Kịch bản kiểm (máy Android 11, rồi Oppo): pill hiện kính mờ đúng phần ảnh nền phía sau; lướt trang, kéo Today / Thư viện vào ra thì pill vẫn bám; đổi ảnh nền thì đổi theo; ảnh nền màu phẳng thì pill phẳng. Hỏng (không vẽ, lệch, chậm thấy rõ) → báo lại, quay về phương án A.

### Task 2: `Modifier.dsFrosted` và kính mờ tĩnh cho CARD/SHEET dưới API 31
**Bắt buộc kiểm tay:** có.
- [x] Step 1: `Modifier.dsFrosted(style: GlassStyle, shape)`: lát cắt nền mờ + tint (`GlassStyle.tuned(look)`), viền sáng mảnh, bo góc theo "Bo góc"; thiếu nguồn thì `style.flat`.
- [x] Step 2: `dsGlass`: khi `SDK_INT < 31`, bề mặt CARD/SHEET và có `LocalDsFrostedBackdrop` → nền mờ tĩnh dưới `DsSurfaceDrawable` (giữ nguyên lớp bề mặt DS3); API ≥ 31 không đổi dòng nào của nhánh cũ.
- [x] Step 3: Bảng thư mục dưới API 31 (Q3): lúc thư mục bắt đầu mở, chụp lớp Home (ảnh nền + lưới + dock, `homeBackdrop` hiện có) MỘT lần thành bitmap, làm mờ ngoài main thread (cùng thuật toán `BlurredBackdropCache`, bộ đệm riêng), ghi thành lớp nền mờ của bảng; chưa xong thì dùng lớp nền mờ của ảnh nền rồi chuyển sang (mờ dần ~150 ms như `BackdropBlurDrawable`). Đóng thư mục thì bỏ bitmap. Máy ≥ 31 không đổi. **Lúc code:** chưa làm phần mờ dần (đổi thẳng từ lớp ảnh nền mờ sang lớp ảnh chụp, thường trong lúc bảng còn đang nở); kiểm tay thấy giật thì thêm.
- [x] Step 4: Kịch bản kiểm (Vsmart Live 4): dock, widget, menu nhấn giữ, menu Sửa có kính mờ, bám khi lướt / rung; mở thư mục: bảng thấy icon phía sau đã mờ; (Oppo) so ảnh chụp trước/sau các bề mặt này: không đổi.

### Task 3: Ô thư mục và pill
**Bắt buộc kiểm tay:** có.
- [x] Step 1: `HomeFolderPlate` dùng `dsFrosted(GlassStyle.forRole(FOLDER_CELL))` thay `.background(folder_background)` (lưới, dock, ảnh kéo); `HomeActivity` cung cấp `LocalDsFrostedBackdrop` cho `HomeDragLayer`.
- [x] Step 2: Pill: `dsFrosted(GlassStyle.forRole(SEARCH_PILL))` thay `.background(search_pill_bg)` (bỏ phần gắn thử của Task 1).
- [x] Step 3: Kịch bản kiểm (cả hai máy): ô thư mục trên lưới, trong dock, đang rung, đang kéo, phóng khi hover gộp; pill lúc đứng yên và lúc chuyển sang hàng chấm.

### Task 4: Icon kiểu "Kính" — **BỎ** (Q1, chủ dự án 2026-11-23)
**Bắt buộc kiểm tay:** không.
- ~~Step 1: Ô icon (lưới, dock, ảnh kéo) khi kiểu icon là `GLASS` và máy theo Q1: `dsFrosted(GlassStyle.forRole(ICON_PLATE), shape theo IconShape.path)` phía sau bitmap icon; viền trong suốt (bitmap đã nướng viền, như XML).
- Step 2: Kịch bản kiểm: đổi kiểu icon sang "Kính", xem lưới, dock, rung, kéo; kính theo đúng hình siêu elip (không lộ góc).~~

### Task 5: Today và Thư viện dưới API 31 (theo Q2, Q4)
**Bắt buộc kiểm tay:** có (máy Android 11).
- [x] Step 1: `SDK_INT < 31`: nền Today / Thư viện dùng bản mờ của `BlurredBackdropCache` thay `shrinkForBlur`; lớp nền của chúng đã mờ nên cung cấp chính lớp đó làm `LocalDsFrostedBackdrop` cho card.
- [x] Step 2: Kịch bản kiểm: Today, Thư viện, màn nhóm Thư viện trên máy Android 11 (nền không lộ khối, card có kính mờ); Oppo không đổi.

### Task 6: Đo hiệu năng và ngân sách (Q5) — **BỎ QUA** (chủ dự án 2026-11-23: đóng plan không đo; đo chung ở G3, chưa áp ngân sách)
**Bắt buộc kiểm tay:** có (chủ dự án chạy lệnh đo hoặc cho Claude lệnh `adb`).
- [ ] Step 1: Cách đo như `lessons/glass.md`: `adb shell dumpsys gfxinfo gambi.global.os26launcher reset`, chạy cảnh ~30 s, đọc lại; cảnh: vuốt trang (có ô thư mục), rung edit mode, mở/đóng thư mục; xen kẽ bản phẳng và bản kính mờ trong cùng phiên, lặp bản phẳng cuối phiên.
- [ ] Step 2: Theo số đo: giữ, hoặc áp ngân sách (Q5). Ghi số vào plan.

### Task 7: Docs
- [x] Step 1: Tick G4 ở `PG-parity-gaps.md`; bài học vào `lessons/glass.md`; `README.md` số mục; `Launcher-Features.md` nếu có dòng liên quan.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Gộp từ kịch bản các Task:
- Vsmart Live 4 (Android 11): pill, ô thư mục (lưới, dock, rung, kéo, hover gộp), dock, widget, menu nhấn giữ, menu Sửa có kính mờ và bám nền khi lướt trang, kéo Today / Thư viện; mở thư mục thấy icon phía sau đã mờ; Today, Thư viện, màn nhóm nền mờ mịn, card có kính mờ; đổi ảnh nền / nền màu.
- Oppo PKD130 (Android 14): dock, widget, menu, bảng thư mục, Today, Thư viện như trước; chỉ ô thư mục và pill đổi sang kính mờ.

**Kết quả kiểm chứng (máy, ngày):** Đạt — chủ dự án kiểm trên Vsmart Live 4 và Oppo PKD130, 2026-11-23 (Task 1 riêng, Task 2–5 một lượt). Chưa đo hiệu năng (Task 6 bỏ qua).

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-23 | — | Đọc `DsGlass`, `BlurredBackdropCache`, `BackdropBlurDrawable`, `GlassBudget`, `GlassStyle`, `HomeScreen`, `HomeFolderPlate`, `HomeSearchPill`, `IconRenderer`, nền Today / Thư viện, plan XML GL / GI, tài liệu Backdrop (5 loại backdrop: Layer, Canvas, Combined, custom, Empty); viết plan | Chờ duyệt Q-A, Q1–Q6 |
| 2026-11-23 | — | Chủ dự án duyệt: Q-A = B; Q1 bỏ kính icon (Task 4 bỏ); Q3 bảng thư mục phải thấy icon phía sau (thêm Task 2 Step 3); Q2, Q4, Q5 đồng ý; Q6 Vsmart Live 4 | Bắt đầu Task 1 |
| 2026-11-23 | 1 | Đọc mã nguồn Backdrop 2.0.1 (tag `2.0.1`): `drawPlainBackdrop` (không highlight / shadow), `LayerBackdropNode.draw` = `drawContent()` rồi ghi lớp bằng `onDraw` của `rememberLayerBackdrop`, vị trí lấy mẫu = `layerCoordinates.localPositionOf(coordinates)`. Tạo `core/glass/compose/DsFrosted.kt`: `FrostedBackdrop`, `rememberFrostedBackdrop`, `FrostedWallpaperLayer` (node rỗng, bản mờ chỉ ghi vào lớp qua `onDraw`, không vẽ đè ra màn; xin bản nhỏ của `GlassEnvironment.blurCache` theo cỡ node, nghe xong / `trim` / ảnh nền đổi / "Độ mờ"), `LocalDsFrostedBackdrop`, `FrostedNoEffects`. `HomeScreen`: lớp nền mờ trước `Wallpaper`, cung cấp `LocalDsFrostedBackdrop` cùng `LocalDsBackdrop`. Pill (bước thử): `drawPlainBackdrop(frosted)` dưới màu `search_pill_bg` cũ | Chưa build |
| 2026-11-23 | 1 | Chủ dự án build và kiểm bước thử: đạt ("ổn rồi"), cho làm tiếp một mạch | Tick Task 1 |
| 2026-11-23 | 2, 3, 5 | `DsFrosted.kt`: `Modifier.dsFrosted(role, shape)` (`drawPlainBackdrop` + tint `GlassStyle.forRole(role).tuned(look)` + viền 1 dp, thiếu nền thì `style.flat`; outline đệm theo cỡ), `FrostedWallpaperImage` (vẽ ra màn), `rememberLayerSnapshot` (chụp lớp `homeBackdrop` sau 2 khung, chép HARDWARE sang ARGB_8888 ngoài main), `rememberSmallBlur` dùng chung (cỡ đích = cỡ cửa sổ `LocalView` cho mọi nơi, tránh bộ đệm dựng qua lại giữa Home / Today / Thư viện). `dsGlass(frosted =)`: nhánh mới chỉ khi `SDK_INT < 31` (chỉ đọc `LocalDsFrostedBackdrop` dưới 31). `HomeActivity` tạo `rememberFrostedBackdrop(state.backdrop)`, truyền `HomeScreen(frosted =)`, bọc `HomeDragLayer`. `HomeScreen`: cung cấp ở cả hai khối `LocalDsBackdrop`; dưới 31 chụp lớp Home khi mở thư mục (`FolderBlurCache` riêng, sàn bán kính 4 px, `trim` khi đóng), `HomeFolderPanel(frostedBackdrop =)`. Ô thư mục (`HomeFolderPlate`) và tấm hover gộp: `dsFrosted(FOLDER_CELL)`; pill: `dsFrosted(SEARCH_PILL)` (tint `gl_bar_tint` thay màu phẳng `search_pill_bg` khi có nền mờ, như XML). Today / Thư viện / màn nhóm dưới 31: nền `FrostedWallpaperImage` thay `shrinkForBlur`, cung cấp lớp nền của chúng làm `LocalDsFrostedBackdrop` | Chưa build |
| 2026-11-23 | 2, 3, 5, 7 | Chủ dự án build và kiểm tay: đạt; bỏ qua Task 6 để đóng plan. Tick Task 2, 3, 5, 7; điền bài kiểm, bằng chứng, docs; bài học vào `lessons/glass.md`; tick G4 | Xong |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | API ≥ 31 không đổi | `dsGlass`: nhánh kính mờ chỉ khi `SDK_INT < 31`, `LocalDsFrostedBackdrop` chỉ đọc dưới 31; Today / Thư viện giữ `shrinkForBlur` + `wallpaperSoftBlur` ở ≥ 31; chủ dự án kiểm Oppo | Đạt |
| 2 | Kính mờ bám đúng nền | Vị trí lấy mẫu do thư viện tính (`LayerBackdrop.drawBackdrop`: `localPositionOf`); chủ dự án kiểm lướt trang, Today / Thư viện, rung, kéo, dock trên Live 4 | Đạt |
| 3 | Ảnh nền đổi | `rememberSmallBlur` theo danh tính bitmap + nghe `BlurredBackdropCache` (`notifyBackdropChanged`, `trim`); nền màu → `backdropOrNull` null → màu phẳng; chủ dự án kiểm | Đạt |
| 4 | Bộ nhớ | Thêm bản nhỏ dùng chung (cỡ đích = cỡ cửa sổ cho mọi nơi); ảnh chụp thư mục dưới 31 bỏ khi đóng (`folderBlurCache.trim()`); chưa đo bằng công cụ | Đạt (đọc code) |
| 5 | Hiệu năng | Task 6 bỏ qua theo chủ dự án; chuyển sang G3 | Chưa đo |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `plans/PG-parity-gaps.md`: tick G4; ghi chú G3 (Oppo giật khi lướt dù chưa bật kính icon; đo cả kính mờ tĩnh của PG3).
- `lessons/glass.md`: bài học PG3; `lessons/README.md`: số mục.
- `Launcher-Features.md`, `config-schema.md`, `room-schema.md`, `project_structure.md`: không đổi (không khóa config, schema hay thư mục mới; file mới `core/glass/compose/DsFrosted.kt` nằm trong gói đã có).
