# FX8 — Kính khớp nền ảnh (D1) và kính Library sẵn sàng trước khi hiện (D2, phần kính) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ điều phối kiểm bằng adb (chủ dự án giao điều phối chạy kiểm, 2026-11-11); cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính thật/Lens). Lệnh viết cho CMD/PowerShell (script Python chạy ở máy dev).

**Nhánh:** `fix/fx8-glass-backdrop-match` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX8-glass-backdrop-match` · **PR:** #39 · **Bắt đầu:** 2026-11-11 (từ `origin/main` 2bac922) · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review (Task 1–4 đã qua review và kiểm Live 4 của điều phối; Task 5 xong, chờ điều phối rebase)
**Khóa:** không (không đổi schema Room; giữ độc quyền `core/glass/*`)

**Goal:** (1) D1: nền của mọi kính mờ tại chỗ (ô thư mục, widget đồng hồ/lịch, pill, panel, ô nhóm Library, dock ở mức CLASSIC) hiện đúng mảng ảnh nền nằm sau nó với **mọi** ảnh nền, kể cả ảnh có tỉ lệ khác màn hình, bằng cách lấy phép ánh xạ từ chính ma trận vẽ của `ImageView` ảnh nền thay vì giả định bitmap cùng tỉ lệ view; độ mờ không phụ thuộc cỡ bitmap; ảnh PNG có alpha không lệch màu. (2) D2 (phần kính): kính các ô Library đã sẵn sàng (bản mờ đã dựng, ô đã tạo) trước khi Library trượt vào, và không còn lần "nháy" khi bản mờ tới muộn.
**Architecture:** Chỉ đổi trong `core/glass` và một chút ở `CategoryPageView` (phần kính) và `BackdropController` (hai dòng). Nguồn sự thật duy nhất của "bitmap nằm ở đâu trên màn hình" là `ImageView.getImageMatrix()` + `drawable.intrinsicWidth/Height` (đúng thứ `ImageView` dùng khi vẽ). `BlurredBackdropCache` dựng bản mờ nhỏ theo cỡ **view** (không theo cỡ bitmap) và có hàm `prewarm`.
**Tech Stack:** không thêm thư viện. API dùng: `ImageView.getImageMatrix()` (API 21+), `Matrix.preScale`, `BitmapShader.setLocalMatrix`, `MessageQueue.IdleHandler` (nếu cần cho prebuild).
**Spec:** `docs/report/Audit-Report-2026-11-10.md` mục "Phát hiện sau đợt 1" D1 và D2; `docs/lessons/glass.md` (GL, GF, AP, FX1 tách từ knowledgement); `docs/report/FX1-wallpaper-safety.md` (bất biến "bitmap Home cùng tỉ lệ view").

---

## ĐÍNH CHÍNH (2026-11-11, điều phối, sau phép đo bằng `FX8-proof.apk`): giả thuyết gốc D1-1 đã bị BÁC

Số đo k ≈ 1,2 ở mục ngay dưới được lấy trên một **APK cũ trước FX1** do điều phối cài nhầm (output build của worktree chính là ngày 2026-11-10). Với `FX8-proof.apk` (= `main` + log tạm):
- `onScreenSize 1080x2340`; `PickedImageSource.load req=1080x2340 file=1632x2912 region=Rect(144,0-1488,2912) out=1080x2340`; `render bitmap=1080x2340`; `Cache.build small=270x585 ms=41`; `Drawable.shader view=1080x2340 intrinsic=1080x2340 ... imageMatrix=identity`.
- Ảnh lưới 1632x2912: lệch 0 px ở mọi góc widget, 6 lần khởi động lạnh liên tiếp (mỗi lần một lần nạp, một lần xin bản nhỏ). `main` 723affd với ảnh thật của chủ dự án: mép phải widget 0 / −3 px.

**Kết luận:** H-A..H-E đều không xảy ra. `onScreenSize` nhận đúng cỡ view; bitmap đúng tỉ lệ; D1-1 **đã được FX1 sửa**, và phép tính e(x) bên dưới mô tả đúng lỗi của bản **trước FX1** (bitmap chưa cắt), chứ không phải lỗi còn lại trên `main`. Tôi đã tin số đo k = 1,214 vì nó khớp phép tính tới vài px mà không kiểm APK đang chạy là bản nào; bài học: ghi `git rev-parse`/md5 của APK trong mọi phép đo và chỉ kết luận nguyên nhân sau khi log của bản đúng xác nhận.

Việc còn lại sau đính chính: (1) Task 1 (ánh xạ qua `imageMatrix`) giữ lại như **gia cố phòng thủ**, kiểm chỉ là "không hồi quy"; (2) D1 thật còn lại là D1-2 (độ mờ theo cỡ bitmap), D1-3 (alpha), D1-4 (làm tròn ở dock CLASSIC) — Task 2; (3) D2 (Task 3, Task 4).

## Số đo ban đầu của điều phối (2026-11-11, Live 4) — **trên APK cũ trước FX1, giữ lại để hiểu phép tính**

Ảnh nền của chủ dự án hiện tại là **1632x2912** (tỉ lệ 0,560; màn 1080x2340 là 0,4615). Ảnh lưới màu ngẫu nhiên 1632x2912 (ô 102x104 px) ghi thẳng vào `files/backdrop_image`, `force-stop`, mở Home:
- Vùng nền trần khớp ảnh tham chiếu (PIL `centerCrop` về 1080x2340) ở dx = 0, dy = 0, tương quan 0,98–0,99: **`ImageView` hiển thị đúng**.
- Bên trong kính tại chỗ (`BackdropBlurDrawable`, trang 1), ở góc widget: đồng hồ trên-phải (x ≈ 465) dx = −14, đồng hồ dưới-phải dx = −14; lịch trên-trái (x ≈ 617) dx = +16, lịch dưới-trái dx = +16; dy ≈ 0. Tức kính lấy mẫu tại `540 + k·(x − 540)`, k ≈ 1,19–1,21.
- Cùng các ô với ảnh lưới 1080x2340 (đúng tỉ lệ màn): khớp 0 px.

**Phép tính của tôi khớp số đo (đọc code + tính, không cần giả định gì thêm):** kính ánh xạ **toàn bộ** bản mờ nhỏ lên **toàn bộ** view (`matrix.setScale(w / small.width, h / small.height)`, `BackdropBlurDrawable.kt:194`), còn `ImageView` `centerCrop` cắt. Nếu bitmap trong `ImageView` là **ảnh nguyên, chưa cắt theo tỉ lệ** (1632x2912), `centerCrop` có scale s = max(1080/1632, 2340/2912) = 0,8036, lệch ngang dx = (1080 − 1632·0,8036)/2 = −115,8 px, và kính (kéo giãn cả ảnh) sai tại điểm x một lượng **e(x) = x·0,2143 − 115,8**: e(465) = −16,4 (đo −14), e(617) = +16,2 (đo +16), e(0) = −116, e(1080) = +116, theo chiều dọc 0 (khớp "dy ≈ 0"). Nếu bitmap đã bị cắt theo một tỉ lệ view khác (vd 1080x2100) thì k chỉ là 1,114 (e(465) ≈ −8): **số đo loại giả thuyết "cắt sai tỉ lệ", chỉ còn "không cắt"** (bitmap giữ tỉ lệ ảnh gốc).

Vậy kính đúng như thiết kế (bitmap cùng tỉ lệ view); thứ vi phạm là **bất biến "bitmap của `ImageView` ảnh nền cùng tỉ lệ view"** ở runtime. Theo đọc code, `PickedImageSource.load` cắt đúng tỉ lệ **khi** `reqW/reqH > 0` (`BitmapScaler.cropRegion` trả cả ảnh khi `reqW <= 0 || reqH <= 0`, `BitmapScaler.kt:34`, và `outputSize` cũng trả nguyên cỡ ở `:56`), nên bitmap chưa cắt chỉ có thể đến từ một lần nạp với cỡ không dương hoặc không phải cỡ view. **Nguyên nhân gốc chưa chứng minh** (xem H-A..H-E ở D1-1): Task 1 chứng minh bằng log tạm trước, rồi sửa kính để không còn phụ thuộc bất biến đó.

---

## Đã đối chiếu với code hiện tại (`2bac922`, đọc lại từng file)

| Mục | Điều nhiệm vụ/audit nói | Kết quả đọc code |
|---|---|---|
| Ảnh nền trong cây view | `ImageView` `centerCrop`, `match_parent` trong `home_root` | Đúng: `activity_home.xml:13-19`. Mọi kính tại chỗ tìm nó bằng `rootView.findViewById(R.id.backdrop_image)` (`CategoryPageView.kt:309`, `IconCellView.kt:310`, `BackgroundGlass.kt:231`, `SearchPillView.kt:205`, `IconGridView.kt:294`, `CategoryGroupPanelView.kt:111`) |
| Nạp ảnh | Cắt vùng giữa theo tỉ lệ vùng Home | Đúng khi `reqW/reqH > 0`: `PickedImageSource.load` (`:56-72`) → `cropRegion`, `outputSize`, `decodeRegion`/`decodeWholeThenCrop`, `scaleTo`. Cỡ yêu cầu lấy từ `HomeViewModel.onScreenSize(width, height)` (`:172-178`), gọi từ `binding.homeRoot.doOnLayout { ... }` (`HomeActivity.kt:169`) |
| `onScreenSize` nhận cỡ ≤ 0? | — | **Có**: `onScreenSize` gọi `rememberScreenSize` (bỏ qua ≤ 0) nhưng **vẫn** đặt `screenSizeHandled = true` và `launchBackdropLoad(width, height)` với cỡ nhận được; `reloadBackdropIfChanged` về sau không nạp lại nếu phiên bản ảnh không đổi (`:196-205`). Nếu có lần gọi với 0x0, bitmap không cắt được giữ suốt phiên. Chưa chứng minh có xảy ra |
| Ánh xạ của kính tại chỗ | Kéo giãn bản mờ vừa view | `BackdropBlurDrawable.prepareShader` `:158-209`: `matrix.setScale(viewW/small.w, viewH/small.h)` (`:194`); `StaticBlurSurface.computeCrop` `:125-150`: `scaleX = blurredFull.width / source.width` (cùng giả định). Không nơi nào đọc `ImageView.imageMatrix` |
| Bản mờ nhỏ | Thu 25% rồi box blur | `BlurredBackdropCache.build` `:135-143`: `scale = min(0.25, 640/maxSide)` theo **cỡ bitmap**; `smoothBlurred` `:145-176`: bán kính 2 px × 3 lượt ở cỡ nhỏ; `boxBlur` luôn đặt alpha 0xFF (`:193`) |
| D2: Library ban đầu | `library_view` GONE tới lần vuốt đầu | Đúng: `activity_home.xml:67-71` (`visibility="gone"`), `AppLibraryController.applyLibraryProgress` `:161` đặt `isVisible = p > 0f` (GONE khi p = 0). `CategoryPageView.render` chỉ dựng ô khi `width > 0` (`:285`) nên **ô và kính của Library chỉ được dựng ở lượt layout đầu tiên lúc hiện** (`onSizeChanged` `:301-305`) |
| D2: bản mờ lazy | Dựng lần đầu khi có người xin | `smallOrRequest` (`BlurredBackdropCache.kt:66-86`) dựng trên luồng `GlassBlur` khi **ô kính đầu tiên vẽ**; chưa xong thì `BackdropBlurDrawable.draw` vẽ màu phẳng `GlassStyle.flat` (`:93-95`). Với ô nhóm (`GROUP_TILE`) `flat` = `folder_background` = `#59FFFFFF` (trắng 35%), tức ô "nhợt" |
| D2: ai dựng bản mờ sớm | — | Trên Live 4 với mức Ép cao, dock và pill là **kính thật** (không dùng cache); chỉ ô thư mục, widget, thanh/ô nhóm/panel mờ xin bản mờ. Home không có ô thư mục/widget đang vẽ thì **ô nhóm Library là người xin đầu tiên**. `GlassEnvironment.blurCache.trim()` ở `TRIM_MEMORY_COMPLETE` cũng đưa cache về lạnh |
| Biên chế file | `core/glass/*` độc quyền; FX7 giữ cử chỉ Library; FX5 giữ map icon | Plan này không sửa file nào của FX7/FX5; ba chỗ ngoài vùng ở mục "Vùng file" |

---

## D1 — Phân tích end-to-end: nơi kính có thể lấy mẫu khác chỗ nó đang đứng

**Chuỗi (đọc code):** file `backdrop_image` → `PickedImageSource.load(reqW, reqH)` (vùng giữa, ≤ cỡ view, KHÔNG phóng to) → `HomeUiState.backdrop` → `BackdropController.render` → `backdropImage.setImageBitmap(bitmap)` → `ImageView` vẽ bằng ma trận `centerCrop` (scale đồng đều + dịch làm tròn) → mỗi kính tại chỗ: `BlurredBackdropCache.smallOrRequest(drawable.bitmap)` (bản mờ nhỏ, **phủ cả bitmap**) → `BitmapShader` + `setLocalMatrix(scale(viewW/small.w, viewH/small.h))` → `canvas.translate(-(ownerLoc − backdropLoc))` vẽ `drawRoundRect`.

**Bất biến kính dựa vào:** "hai trục của bitmap tỉ lệ đúng như hai trục của view" (kéo giãn = centerCrop). Hai nơi giả định (BackdropBlurDrawable, StaticBlurSurface). Kính thật (`RealGlassSurface`/`LiquidGlassView`) chụp chính `ImageView` đã vẽ nên **không** dính bất biến này (khúc xạ ở rìa là chủ ý của thư viện, không phải lỗi lệch nền).

**Mô phỏng (script `sim.py`, cùng công thức với code; view Live 4 1080x2340 trừ khi ghi khác).** Cột "bitmap đúng tỉ lệ" là đường FX1 chạy đúng; cột "bitmap chưa cắt" là sai số khi bitmap giữ tỉ lệ ảnh gốc (nghi phạm D1-1). Sai số tính bằng px màn hình, tối đa trên cả màn (ở mép) và tại hai góc widget x = 465/617:

| Ảnh nền | Bitmap đúng tỉ lệ (FX1) | Bitmap chưa cắt: sai số tối đa (mép) | Tại x = 465 / 617 |
|---|---|---|---|
| 1080x2340 (lưới đúng cỡ màn) | 0 | 0 | 0 / 0 |
| **1632x2912 (ảnh chủ dự án)** | 0 | **116** | **−16,4 / +16,2** (đo: −14 / +16) |
| 527x1140 (ảnh nhỏ) | ≤ 0,7 | 1,0 | −0,3 / 0 (**không thể phân biệt**: tỉ lệ gần màn) |
| 1320x2868 (ảnh mặc định) | 0 | 0 (dọc 3,5) | 0 / 0 |
| 3024x4032 (ảnh chụp dọc từ camera, 3:4) | 0 | 338 | −47 / +48 |
| 1600x1600 (vuông) | ≤ 1 | 630 | −88 / +90 |
| 4000x1000 (toàn cảnh) | ≤ 1 | 4140 | −575 / +590 |

Hệ quả: **ảnh càng lệch tỉ lệ màn càng sai nhiều; ảnh gần tỉ lệ màn (ảnh mặc định, 527x1140, ảnh lưới đúng cỡ) trông bình thường**. Đây chính là "một số ảnh nền" của chủ dự án, và là lý do ca 1080x2340/527x1140 của điều phối không tái hiện được. (Trên Oppo A3x, vùng Home 720x1604 chưa xác nhận trên máy: dùng `wm size`/log Task 1.)

### Bảng ứng viên nguyên nhân, xếp hạng (độ tin cậy ghi nhãn)

| # | Điều kiện | Sai số (Live 4) | Ảnh bị | Tin cậy | Ghi chú |
|---|---|---|---|---|---|
| **D1-1** | **[ĐÃ BÁC trên `main`, xem Đính chính; đúng cho bản trước FX1]** Bitmap của `ImageView` ảnh nền không cùng tỉ lệ view tại lúc vẽ (kính kéo giãn, `ImageView` cắt) | e(x) = (x·bw/vw − (x−dx)/s)·s; 0 → ±116 (1632x2912), ±338 (3024x4032), tới hàng nghìn (toàn cảnh) | Mọi ảnh có tỉ lệ khác màn đáng kể; ảnh gần tỉ lệ màn không thấy | Số đo ban đầu là trên APK cũ (trước FX1); trên `main` **đã bác** bằng log `FX8-proof.apk` | Task 1 còn lại như gia cố phòng thủ |
| D1-2 | **Độ mờ phụ thuộc cỡ bitmap**: bản mờ nhỏ = 25% **bitmap**, bán kính 2 px cố định ⇒ σ (px màn hình) = 2,45·vw/sw = 9,8·vw/bw | σ = 9,8 px (bitmap = view) · **20,2 px (527x1140)** · 23 (toàn cảnh 4000x1000, vùng 462x1000) · 53 (200x433) | Mọi ảnh có bitmap nhỏ hơn view (ảnh nhỏ, ảnh ít điểm ảnh): `outputSize` không phóng to nên bitmap < view | Cao (tính từ code, chưa đo) | Không lệch vị trí nhưng kính "nhòe hơn" 2× so với ảnh to: màu bị trộn từ vùng rộng hơn, tiếp tục là "không đúng mảng nền sau nó" khi ảnh mềm. Task 2 |
| D1-3 | **PNG có alpha bán trong suốt**: `ImageView` vẽ pixel premultiplied lên nền đen (`home_root` `@color/black`) còn bản mờ lấy `getPixels` (không premultiply) rồi đặt alpha 0xFF | Pixel alpha α: nền hiện `α·C`, kính hiện `C` ⇒ kính sáng hơn nền 1/α lần (alpha 0,5 → 2×); alpha 0 → 0 (khớp) | Ảnh PNG có vùng mờ/trong suốt từng phần; `TransparentTrimmer` chỉ cắt viền trong suốt hoàn toàn | Trung bình (đọc code, chưa đo; hiếm) | Lệch màu chứ không lệch vị trí. Task 2 |
| D1-4 | **`StaticBlurSurface.computeCrop` cắt nguyên (int) trong bản nhỏ**: `left/top/cropW/cropH` bằng `.toInt()`, `Rect` nguyên | ≤ 1 điểm ảnh nhỏ mỗi mép = vw/sw px: **4,1 px (bitmap = view), 8,2 px (527 rộng)**; đã mô phỏng dock: trái −7,5, phải −9,0, trên −3,8, dưới −6,7 px với ảnh 527x1140 | Chỉ **dock ở mức CLASSIC_CPU** (Live 4 ở mức Tự động, hoặc Ép thấp) | Cao (tính từ code), nhỏ | Mức Ép cao của Live 4 dùng kính thật cho dock nên không dính. Task 1 (cùng sửa D1-1) |
| D1-5 | **Phép co của view tổ tiên không được bù**: `GlassLocation` chỉ bỏ phép dịch của `home_content`; `libraryView` co `scaleX/Y` về 0,85 khi panel nhóm mở | (1 − s)·khoảng cách tới pivot: tới ~80 px ở mép, lúc Library đang mờ dần | Ô nhóm/thanh tìm kiếm Library **chỉ lúc panel nhóm đang mở** | Trung bình (đã ghi ở AP, suy luận) | Ngoài phạm vi (ghi để sau); ảnh hưởng thấp vì Library mờ đi |
| — | Khúc xạ/tán sắc ở rìa kính thật (dock, pill, panel, thanh Library trên LENS và mức Ép cao) | Có chủ ý (hiệu ứng liquid glass) | — | Đọc tài liệu thư viện | **Không sửa**; hỏi chủ dự án có muốn kính thật không khúc xạ không (Q2) |

### Vì sao bitmap có thể chưa cắt (H-A..H-E của D1-1; log đã phân xử: không giả thuyết nào xảy ra trên `main`)

| Giả thuyết | Cơ chế (đọc code) | Dấu hiệu trong log | Đã khớp số đo? |
|---|---|---|---|
| H-A | `onScreenSize(width, height)` được gọi với cỡ ≤ 0 (view chưa có cỡ lúc `doOnLayout` chạy): `load(0, 0)` ⇒ `cropRegion` trả cả ảnh, `outputSize` trả nguyên cỡ ⇒ bitmap 1632x2912; `screenSizeHandled = true` nên **không bao giờ nạp lại** (phiên bản ảnh không đổi) | `PickedImageSource.load req=0x0` rồi `bitmap=1632x2912` | Khớp (k = 1,214) |
| H-B | Một lần nạp khác dùng cỡ khác view (vd `lastScreenW/H` cũ, hoặc `reloadBackdropIfChanged` ở `onResume` đầu) và ra bitmap cắt theo tỉ lệ sai | `req=` khác cỡ `backdropImage` | **Không khớp** với số đo trừ khi tỉ lệ sai trùng ảnh gốc |
| H-C | `decodeRegion` bỏ qua vùng (trả cả ảnh) trên Android 11 rồi `scaleTo` kéo giãn | `region=`, `decodedRaw=` khác nhau | Sẽ làm hỏng `ImageView` hiển thị (đã đúng) nên loại |
| H-D | Bản mờ nhỏ lấy từ bitmap khác bitmap của `ImageView` (cache giữ bản của ảnh cũ) | `smallOrRequest source=` khác `setImageBitmap bitmap=` (danh tính, cỡ) | Khớp nếu ảnh cũ chưa cắt; chưa rõ nguồn |
| H-E | `ImageView` chưa đo xong (w/h = 0) lúc kính dựng shader, rồi `shaderViewW/H` không cập nhật | `prepareShader view=0x0` | Không khớp (vẫn có cập nhật khi cỡ đổi, `:187-198`) |

Kể cả khi H-A đúng, sửa chỉ ở `HomeViewModel` (một dòng bảo vệ `width/height <= 0`) là **hàng rào chưa đủ** (bất kỳ lần nạp sai cỡ về sau, đổi cỡ cửa sổ, ảnh cũ trong cache lại lặp lỗi), và `HomeViewModel` là file độc quyền của FX7 ở đợt này. Vì vậy thiết kế sửa kính để **không cần** bất biến (mục sau), còn hàng rào ở ViewModel là câu hỏi cho điều phối (Q1).

---

## D2 (phần kính) — Vì sao kính Library nhợt rồi lấp đầy, và "nháy" ~1,3 s sau khi đáp

Chưa có đo riêng (chỉ có quay màn hình của điều phối). Từ đọc code, xếp hạng; **log Task 1 xác nhận trước khi sửa**:

| # | Nguyên nhân | Cơ chế | Dấu hiệu | Tin cậy |
|---|---|---|---|---|
| **G1** | **Bản mờ chưa có khi Library hiện lần đầu** (cache lạnh) | Ô kính đầu tiên vẽ mới gọi `smallOrRequest`; Home không có ô thư mục/widget đang vẽ (dock và pill là kính thật ở mức Ép cao) thì người xin đầu tiên là ô nhóm Library. Dựng xảy ra trên luồng `GlassBlur` (ưu tiên thấp hơn bình thường), tranh CPU với lượt layout/inflate lần đầu của Library và hoạt ảnh; bản debug trên Live 4: 78,8 ms trên Oppo cho 306x408 (GL, đo), Live 4 chậm hơn nhiều và bị tranh ⇒ plausibly ~1 s. Trong lúc chờ ô vẽ `flatPaint` = trắng 35% (nhợt); khi `onSmallReady` tới, ô **đổi hẳn** sang nền mờ + tint: chính là "nháy" | `request→ready` ≈ 1,3 s; ô vẽ phẳng N khung | Cao (cơ chế chắc, thời gian chưa đo) |
| G2 | **Library dựng ô ngay trong lượt layout đầu tiên lúc hiện** | `libraryView` GONE nên `width = 0`, `render()` không dựng ô; ô (inflate 8 nhóm, `BackdropBlurDrawable` mới, nạp icon bất đồng bộ) chỉ được dựng ở `onSizeChanged` lần đầu, đúng khung Library bắt đầu trượt: một cú hụt trên luồng chính, và icon trong ô còn trống tới khi `iconLoader` xong ("trống") | `render` bắt đầu ngay frame đầu của vuốt; số ms | Cao (đọc code), là phần "ô trống" |
| G3 | **`render()` dựng lại toàn bộ ô khi cỡ/padding/cấu hình kính đổi** | `onSizeChanged`, `setPadding`, `setBottomInset`, `glassListener` đều gọi `render(lastGroups)`: ô và `BackdropBlurDrawable` mới (shader mới, `smallBitmap = null`) | Số lần `render` sau khi đáp | Thấp–trung bình (cần log; nếu có lần dựng lại ~1,3 s sau thì ra "nháy" thứ hai) |
| G4 | `trim()` ở `TRIM_MEMORY_COMPLETE` đưa cache về lạnh sau khi app ở nền lâu | `smallBitmap = null`, lần vẽ sau xin lại | App vừa quay về | Thấp (điều kiện hiếm) |

Hai "pha" chủ dự án thấy ("nhợt/trống lúc đầu, rồi lấp, rồi nháy lần nữa") khớp **G1 + G2**: Library hiện, ô vừa dựng vẽ phẳng nhợt (G1) trong lúc icon chưa có (G2); `onSmallReady` tới ~1,3 s sau (cache dựng chậm) thì mọi ô đổi sang kính thật trong một khung. Nếu log cho thấy hai sự kiện khác nhau (ví dụ `render` lần hai do G3) thì Task 3 xử lý cả hai.

---

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- D1: kính phải khớp ảnh nền với **mọi** ảnh. FX1 đã sửa nguồn (bitmap cùng tỉ lệ view khi nạp) nhưng không ai kiểm ở chỗ vẽ, nên khi nguồn sai (H-A..H-E hay đổi cỡ view về sau) kính sai ±100 px mà `ImageView` vẫn đúng. Ràng buộc: `core/glass` độc quyền của FX8; `HomeViewModel` độc quyền của FX7; `BackdropBlurDrawable.draw` không được cấp phát và shader không được tạo lại mỗi khung (GL Task 6c); phải chạy trên API 26–36; chi phí cho Live 4 (CLASSIC_CPU) đã chật (GL, GS: số đo).
- D2: bản mờ nhỏ chỉ ~0,57 MB nhưng dựng tốn ~0,1–1 s ở bản debug; "pop" nhìn thấy khi nó đổi từ phẳng sang kính; FX7 giữ cử chỉ/vuốt nên không đụng `AppLibraryController`/`HomeTouchRouter`.

**Các phương án đã xét cho D1.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ sửa nguồn | Bảo vệ `onScreenSize(<=0)` và nạp lại ảnh khi cỡ `homeRoot` đổi (ở `HomeViewModel`) | Sửa tận gốc nếu H-A đúng; bitmap luôn đúng tỉ lệ | Chưa biết gốc; đụng file độc quyền của FX7; không phòng được mọi lần sai tương lai (đổi cỡ cửa sổ sau khi nạp: vẫn lệch ngay lập tức tới khi nạp lại), không sửa dock CLASSIC làm tròn | Không (làm thêm như hàng rào, Q1) |
| B. Kính dùng ma trận vẽ thật của `ImageView` | Shader local matrix = `imageMatrix × scale(intrinsicW/smallW, intrinsicH/smallH)`; `StaticBlurSurface` vẽ bằng `drawBitmap(bmp, matrix)` cùng ma trận | **Một nguồn sự thật** (cùng ma trận `ImageView` dùng khi vẽ, gồm làm tròn dịch): đúng với mọi tỉ lệ, mọi scaleType, mọi lần đổi cỡ view, mọi mật độ bitmap; không cần biết bitmap từ đâu; bỏ cắt nguyên của dock | `getImageMatrix()` trả `mDrawMatrix` (nội bộ, `null` thì `Matrix.IDENTITY` mới mỗi lần: cấp phát một lần khi bitmap đúng cỡ view, chỉ lúc dựng shader); cần chạy lại khi `imageMatrix` đổi (cỡ view/drawable đổi, đã có theo dõi `shaderViewW/H`) | **Có** |
| C. Tự tính lại centerCrop trong kính | Công thức `scale = max(vw/dw, vh/dh)`, `dx = round(...)` | Không phụ thuộc nội bộ `ImageView` | Hai bản công thức dễ lệch (làm tròn, `scaleType` khác, padding); phải biết `scaleType` | Không |
| D. Thay `ImageView` bằng view tự vẽ phơi bày ánh xạ | `BackdropView` | Kiểm soát hoàn toàn | Đụng `activity_home.xml` (độc quyền), `BackdropController`, 6 chỗ `findViewById`, và kính thật cần `backdropSource` là view đã vẽ | Không |

**Vì sao chọn B (cho D1-1, D1-4).** Số đo của điều phối (k = 1,214) và phép tính khớp nhau đến vài px cho thấy lỗi là ánh xạ (hình học) chứ không phải mờ hay vị trí; B làm ánh xạ **bằng đúng ma trận mà ảnh nền đang được vẽ**, nên không thể lệch dù nguồn nạp sai. Nó cũng sửa dock CLASSIC (làm tròn ≤ 8 px) ngay trong cùng phép đổi. A chữa nguyên nhân nhưng chưa biết nguyên nhân, và C/D tốn hơn để lấy lại một sự thật mà `ImageView` đã có (nhãn: lập luận từ code + số đo điều phối).

**Các phương án cho độ mờ (D1-2) và alpha (D1-3).** (a) Dựng bản nhỏ theo **cỡ view/4** (chọn): σ ≈ 9,8 px màn hình với mọi ảnh; bitmap nhỏ hơn đích thì phóng bilinear trước khi mờ (ảnh vốn đã mềm). (b) Giữ 25% bitmap, nhân bán kính theo vw/bw: bán kính nguyên 1–2 px nên bậc thang thô, không đều. (c) Giữ nguyên (chấp nhận mờ gấp đôi ở ảnh nhỏ): không sửa "không đúng mảng nền". Alpha: nhân alpha vào RGB (premultiply theo nền đen) trong `smoothBlurred` (chọn) so với tô nền đen rồi vẽ ảnh: cùng kết quả, premultiply nhẹ hơn.

**Các phương án cho D2.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Làm nóng bản mờ ngay khi ảnh nền nạp xong | `BlurredBackdropCache.prewarm(bitmap, viewW, viewH)` gọi từ `BackdropController.render` (cùng chỗ gọi `notifyChanged`) | Bản mờ sẵn lâu trước khi bất kỳ kính nào vẽ, độc lập có ô nào trên Home hay không; chi phí một lần mỗi ảnh (đã có trong đường dùng), không tăng số lần dựng | Dựng cả khi chẳng ai dùng (dock/pill kính thật trên máy mạnh): ~0,57 MB và một lần blur nền; hai dòng ở `BackdropController` | **Có** |
| B. Lưu bản mờ ra đĩa | Ghi PNG nhỏ cạnh `backdrop_image` | Nóng ngay từ lần khởi động | Thêm IO, khóa phiên bản, dọn file; lợi ích thêm so với A chỉ ở 100–300 ms đầu khởi động | Không |
| C. Dựng đồng bộ trên main thread ở lần vẽ đầu | Bỏ luồng nền | Không bao giờ phẳng | Hụt khung (78,8 ms trên Oppo, GL đo) ngay lúc Library trượt | Không |
| D. `libraryView` INVISIBLE thay GONE (dựng ô sẵn) | Đổi `AppLibraryController.applyLibraryProgress` | Ô dựng sẵn, không hụt lúc hiện | **File của FX7**, và INVISIBLE không vẽ nên vẫn không làm nóng bản mờ | Không (câu hỏi Q3 nếu A+E chưa đủ) |
| E. Library tự dựng ô sớm trong `CategoryPageView` | `render()` khi `width = 0` dùng cỡ cha, dựng ô lúc rảnh (`IdleHandler`); `onSizeChanged` không dựng lại nếu `Sizes` không đổi | Không cần file của FX7; icon cũng nạp sẵn; hết hụt lúc hiện | Bộ nhớ ô sẵn (tối đa 8 nhóm × 4 icon nhỏ, vài trăm KB); cần bảo vệ trường hợp cỡ cha ≠ cỡ cuối | **Có** (Task 4) |
| F. Chuyển pha mượt khi bản mờ tới muộn | `BackdropBlurDrawable`: vẽ phẳng rồi **chuyển mờ dần ~150 ms** sang kính khi `onSmallReady`; khi còn phẳng dùng màu gần kết quả | Hết "nháy" ngay cả khi cache lạnh (khởi động, `trim`) | Thêm vài dòng `draw` + invalidate vài khung | **Có** (Task 4) |

**Vì sao chọn A + E + F cho D2.** A đánh vào G1 (nguyên nhân chính, cơ chế chắc), E đánh vào G2 (hụt lúc hiện, ô trống) mà không đụng file của FX7, F xử lý mọi trường hợp lạnh còn lại (khởi động, `trim`) nên không cần chắc chắn 100% về thời điểm. Đánh đổi: bộ nhớ và CPU nền nhỏ ngay sau khởi động (idle); xét lại nếu số đo cho thấy khởi động chậm hơn.

**Đánh đổi chấp nhận (tổng).** Độ mờ kính của ảnh nhỏ giảm so với trước (từ 20 px còn ~10 px): chủ dự án có thể thấy kính "nét hơn" trên ảnh nhỏ (Q3). `getImageMatrix()` là API công khai nhưng `mDrawMatrix` là cấu trúc nội bộ của `ImageView`; đã có hàng rào là phép đo Task 1 (so ma trận với tính tay) nếu một bản Android đổi hành vi thì kiểm trên máy thật.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Task 1 gồm: log tạm chứng minh nguồn bitmap sai tỉ lệ, **và** sửa `BackdropBlurDrawable` + `StaticBlurSurface` dùng ma trận thật của `ImageView` | Điều phối, 2026-11-11 (đo D1 trên Live 4) |
| 2 | Kiểm Task 1: lưới 1632x2912, lưới 1080x2340, ảnh 527x1140; mọi lệch ở góc widget 0 ± 4 px | Điều phối, 2026-11-11 |
| 3 | Độ mờ kính theo cỡ view (σ ≈ 9,8 px màn hình) thay vì cỡ bitmap | Điều phối duyệt 2026-11-11 (Q3); cơ sở: tính từ `BlurredBackdropCache.build` |
| 4 | Không đổi hiệu ứng khúc xạ của kính thật | Điều phối quyết 2026-11-11 (Q2); đọc tài liệu thư viện |
| 5 | Không sửa file của FX7 (`AppLibraryController`, `HomeTouchRouter`, `HomePagerController`) và FX5 (icon) | Điều phối, 2026-11-11 |

## Ngoài phạm vi / ghi để sau

- Cử chỉ/chuyển cảnh trang cuối → Library (Home dịch rồi đứng, Library nhảy ~360 px): FX7.
- D1-5 (kính trong Library khi cả Library co về 0,85): ghi ở AP, ảnh hưởng thấp.
- Hàng rào `HomeViewModel.onScreenSize` (bỏ qua cỡ ≤ 0, nạp lại khi cỡ `homeRoot` đổi): chỉ làm nếu log Task 1 chứng minh cỡ sai (ngoại lệ Q1 đã được cấp).
- Kính thật (`RealGlassSurface`) không sửa; khúc xạ ở rìa là chủ ý.
- Ảnh nền hệ thống/`WallpaperApplier`: không đụng.

## Vùng file

| File (từ `app/src/main/java/gambi/global/os26launcher/`) | Tạo / Sửa | Nóng |
|---|---|---|
| `core/glass/BackdropBlurDrawable.kt` | Sửa: `prepareShader` (`:158-209`) dùng `imageMatrix` + `intrinsic` thay `setScale`; chuyển pha phẳng→kính (Task 4) | **Độc quyền** (`core/glass/*`) |
| `core/glass/StaticBlurSurface.kt` | Sửa: `computeCrop` (`:125-150`) và `onDraw` (`:51-71`) dùng ma trận | Độc quyền |
| `core/glass/BlurredBackdropCache.kt` | Sửa: `build` (`:135-143`) theo cỡ view, `smoothBlurred` (alpha), thêm `prewarm` | Độc quyền |
| `core/glass/GlassEnvironment.kt` | Sửa chỉ khi cần cổng gọi `prewarm` | Độc quyền |
| `ui/home/view/CategoryPageView.kt` | Sửa **chỉ phần kính/dựng ô** (`render` `:271-299`, `onSizeChanged` `:301-305`): dựng ô sớm bằng cỡ cha, không dựng lại khi `Sizes` không đổi | Không nóng; thuộc vùng FX8 theo audit ("phần kính") |
| `ui/home/controller/BackdropController.kt` | Sửa **hai dòng** (`:61-64`): gọi `prewarm` ngay sau `setImageBitmap` | Không nóng; ngoài `core/glass` (xin điều phối) |
| `ui/home/HomeViewModel.kt` | **Ngoại lệ do điều phối cấp (2026-11-11):** chỉ được sửa các dòng `onScreenSize` / `rememberScreenSize` / nạp lại khi cỡ đổi (`:172-205`) và **chỉ khi** log Task 1 cho thấy cỡ yêu cầu ≤ 0 hoặc sai. FX7 chỉ sửa `onIconLongPressed` trong file này; nhánh merge sau rebase. Ngoài ra chỉ có hook tạm `FX8TMP` trong patch ngoài repo | **Độc quyền** (FX7 giữ; ngoại lệ trên) |
| `docs/report/FX8-glass-backdrop-match.md`, `docs/knowledgements/FX8-glass-backdrop-match.md` | Plan (file này); knowledgement tạo ở Task 5 | Không |

Không đụng: `HomeActivity.kt`, `activity_home.xml`, `AppLibraryController.kt`, `HomeTouchRouter.kt`, `HomePagerController.kt`, `IconGridView`/`DockView`/`FolderPanelView` (FX5), `data/apps/*`, `data/backdrop/*` (xong ở FX1), `res/*`. Cần file ngoài vùng thì dừng và báo.

## Global Constraints

- Không code trước khi plan được duyệt. Mỗi Task: `gradlew.bat assembleDebug` xanh trong worktree, commit tiếng Anh không dấu (`fix:`), một hoặc vài commit mỗi Task, cập nhật Nhật ký, dừng và báo. Stage theo đường dẫn, không `git add docs` hay `-A`.
- Không thêm test, không thêm thư viện. Không merge `main` vào nhánh. Không cài APK, không chạy adb: điều phối làm (agent chỉ dựng APK ra thư mục tạm ngoài repo, vd `%TEMP%\fx8-*.apk`, khi điều phối cần bản có log).
- `BackdropBlurDrawable.draw` **không cấp phát** và không tạo lại shader mỗi khung; chỉ `setLocalMatrix` khi (ảnh, cỡ view, cỡ intrinsic, cỡ bản nhỏ) đổi (GL Task 6c). Luật `ui → domain ← data` giữ nguyên (`core/glass` không biết `data`).
- Log tạm chỉ trong patch ngoài repo `%TEMP%\fx8-proof.patch`, nhãn `FX8TMP`; `grep -rn FX8TMP app/src` rỗng ở mọi commit.
- Phép đo là **debug**; ghi mức kính (Live 4: Ép cao, và Ép thấp cho dock CLASSIC), `git rev-parse --short HEAD` của từng APK, ảnh nền, cỡ chữ. Điện thoại là của chủ dự án: chỉ chạy khi chủ dự án nói máy rảnh; trước khi chạy xác nhận `Os26Launcher` đang ở trước và bố cục khớp bản sao lưu; sao lưu mới ngay trước mỗi lượt (xem phần Bài kiểm).

## Review Focus

1. `BackdropBlurDrawable`: ma trận shader = `imageMatrix` (sao chép vào `Matrix` có sẵn, **không** giữ tham chiếu `mDrawMatrix`) rồi `preScale(intrinsicW/smallW, intrinsicH/smallH)`; dựng lại khi ảnh, cỡ view, cỡ intrinsic hoặc cỡ bản nhỏ đổi; `canvas.translate(-t)` và đường `drawScaled` không đổi; không còn `setScale(w/small.width, ...)`.
2. `StaticBlurSurface`: không còn `Rect` nguyên cho nguồn; vẽ bằng ma trận float, cùng nguồn sự thật; clip bo góc giữ nguyên.
3. `BlurredBackdropCache.build`: kích thước bản nhỏ là hàm của cỡ view (trần `MAX_SMALL_SIDE`), khóa cache gồm (bitmap, cỡ đích); ảnh có alpha được nhân alpha vào RGB trước khi mờ; không đổi thuật toán mờ.
4. `prewarm`: chỉ main thread, tham chiếu yếu như cũ, không giữ bitmap nguồn; dựng MỘT lần cho một (bitmap, cỡ), người xin sau nhận bản đã có; `trim()` vẫn đúng.
5. `CategoryPageView`: dựng ô sớm không đổi hành vi khi `width > 0`; không dựng lại ô ở lần hiện đầu nếu `Sizes` bằng nhau; `IdleHandler` được gỡ ở `onDetachedFromWindow`.
6. Chuyển pha phẳng→kính: không cấp phát, chỉ `invalidate` owner trong ~150 ms rồi dừng; `setAlpha` của drawable (nền màn nhóm mờ vào, GF) vẫn nhân đúng.
7. Không file nào ngoài vùng; không log tạm sót.

---

### Task 1: D1 — chứng minh bitmap sai tỉ lệ và để kính dùng ma trận vẽ thật của `ImageView`

**Bắt buộc kiểm tay:** **có** — phép đo dùng để chọn thiết kế (mục 4.2 của MultiAgent-Workflow.md): log phân xử H-A..H-E trước khi tin nguyên nhân, và phép đo tương quan là kiểm chứng duy nhất của sửa hình học. Điều phối kiểm bằng adb trước khi sang Task 2.
**Files:** `BackdropBlurDrawable.kt`, `StaticBlurSurface.kt` (commit); patch log tạm ngoài repo (không commit).
**Interfaces:** Consumes: `ImageView.getImageMatrix()`, `Drawable.intrinsicWidth/Height`, `BlurredBackdropCache.smallOrRequest` (chữ ký không đổi). Produces: không đổi chữ ký công khai.

- [x] **Step 1 (log tạm, không commit):** viết `%TEMP%\fx8-proof.patch` thêm log `FX8TMP` tại: (a) `PickedImageSource.load`: `req=reqWxreqH file=wxh region=... out=... decoded=... result=...`; (b) `HomeViewModel.onScreenSize`/`reloadBackdropIfChanged`/`launchBackdropLoad` (tham số `width,height`, `lastScreenW/H`, `homeRoot` hiện tại, `shownBackdropVersion`); (c) `BackdropController.render`: `bitmap=wxh identity`, `backdropImage w x h`, `homeRoot w x h`, `imageMatrix`; (d) `BlurredBackdropCache.smallOrRequest`: `source=wxh identity`, và `onBuilt`: `small=wxh ms`; (e) `BackdropBlurDrawable.prepareShader` (một lần mỗi drawable): `view=wxh intrinsic=wxh small=wxh imageMatrix=...`. Dựng `%TEMP%\fx8-proof.apk`; không commit.
- [x] **Step 2 (điều phối, "Bài kiểm" mục A, B-baseline):** cài `fx8-proof.apk`, chạy lưới 1632x2912 và đọc log: ghi H-A..H-E nào đúng (bitmap trong `setImageBitmap` có `1632x2912` không; `req=`?). Nếu log cho thấy cỡ yêu cầu ≤ 0 hoặc khác `backdropImage` thì báo điều phối cho Q1 trước khi sang Step 3 (Step 3 vẫn làm). Nếu không khớp H-A..H-E thì dừng và báo: không sửa theo đoán.
- [x] **Step 3: `BackdropBlurDrawable.prepareShader`.** Thay `matrix.setScale(w/small.width, h/small.height)` (`:194`) bằng: `matrix.set(view.imageMatrix)` rồi `matrix.preScale(dw/small.width, dh/small.height)` với `dw/dh = drawable.intrinsicWidth/Height` (≤ 0 thì dùng cỡ bitmap). Khóa dựng lại: (drawable, `view.width`, `view.height`, `dw`, `dh`, `small.width/height`); mở rộng `shaderViewW/H` thành các trường này. Vẫn `canvas.translate(-tx, -ty)` (`:99-110`).
- [x] **Step 4: `StaticBlurSurface`.** Thay `cropSrc: Rect` bằng ma trận nguồn→`drawView` = `translate(srcLoc − dstLoc) × imageMatrix × scale(dw/small.w, dh/small.h)`, vẽ bằng `canvas.drawBitmap(bmp, matrix, bitmapPaint)` trong `clipPath` hiện có; dựng lại ở các chỗ hiện đặt `cropSrc = null` (`onSizeChanged`, `setBackdrop`, `onSmallReady`). Kính thật không đổi.
- [x] **Step 5:** `grep -n "setScale" core/glass/BackdropBlurDrawable.kt` không còn dòng ánh xạ cũ. Build xanh. Commit `fix: map blurred backdrop through the ImageView image matrix`.
- [x] **Step 6: Kịch bản kiểm (Live 4, APK = commit Task 1, mức Ép cao; "Bài kiểm" mục B, C):** (a) lưới 1632x2912: mọi góc của đồng hồ, lịch, ô thư mục (trang 1) có `dx, dy ∈ [−4, +4]`; **trước sửa**: −14/+16 ở góc widget, ±116 ở mép; (b) lưới 1080x2340: 0 ± 4 (không hồi quy); (c) 527x1140: 0 ± 4; (d) ảnh 3024x4032 (3:4) và 1600x1600 (vuông): 0 ± 4; (e) mức Ép thấp (dock = `StaticBlurSurface`): hai góc trên của dock 0 ± 4 với 527x1140 (trước: tới 9 px); (f) **bỏ** (không `wm size` trên máy của chủ dự án, Q4).

---

### Task 2: D1 — độ mờ theo cỡ view và alpha khi mờ

**Bắt buộc kiểm tay:** không (hiệu ứng nhìn thấy được kiểm ở bài kiểm cuối).
**Files:** `BlurredBackdropCache.kt` (và các nơi gọi `smallOrRequest`: `BackdropBlurDrawable.kt`, `StaticBlurSurface.kt`).
**Interfaces:** Consumes: cỡ `ImageView` ảnh nền. Produces: `smallOrRequest(source, viewW, viewH, listener)`.

- [x] **Step 1:** `smallOrRequest` nhận thêm `viewW, viewH` (gọi từ `view.width/height` đã có ở `prepareShader`, `source.width/height` ở `computeCrop`). `build`: đích = (`viewW·0,25`, `viewH·0,25`) kẹp `MAX_SMALL_SIDE`; bitmap nguồn nhỏ hơn đích thì `scale` lên bằng `createScaledBitmap` bilinear trước khi mờ. Khóa cache = (danh tính bitmap, cỡ đích); khác cỡ đích thì dựng lại (cỡ view đổi hiếm).
- [x] **Step 2: alpha.** Trong `smoothBlurred` (và `build` nếu cần), khi `source.hasAlpha()` nhân alpha vào RGB (`c·a/255`) sau `getPixels` để khớp việc `ImageView` vẽ lên nền đen, rồi đặt alpha 0xFF như cũ.
- [x] **Step 3:** build xanh; commit `fix: blur backdrop relative to the view size and premultiply alpha`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" mục D):** (a) σ khớp tốt nhất giữa kính và bản tham chiếu mờ: 527x1140 từ ~20 px về 10 ± 2 px, lưới 1080x2340 vẫn 10 ± 2; (b) PNG 1080x2340 có dải alpha 0,5: độ sáng vòng của ô trước sửa ≈ gấp đôi nền, sau sửa chênh ≤ 8/255.

---

### Task 3: D2 — làm nóng bản mờ ngay khi ảnh nền nạp xong

**Bắt buộc kiểm tay:** **có** — phép đo thời gian (log + quay màn hình) quyết định có cần Task 4 hay không (mục 4.2: phép đo dùng để chọn phương án).
**Files:** `BlurredBackdropCache.kt`, `GlassEnvironment.kt` (nếu cần), `BackdropController.kt` (hai dòng).
**Interfaces:** Produces: `BlurredBackdropCache.prewarm(source: Bitmap, viewW: Int, viewH: Int)`.

- [x] **Step 1: `prewarm`.** Hàm công khai (main thread) đưa `source` vào cùng đường dựng của `smallOrRequest` (không người nghe, không `waiters`), dựng đúng một lần mỗi (bitmap, cỡ); khi xong, `onBuilt` báo các `registered` như `notifyBackdropChanged`.
- [x] **Step 2: nối.** Trong `BackdropController.render`, ngay sau `setImageBitmap(bitmap)` (`:61`) và trước `notifyChanged`, gọi `GlassEnvironment.from(activity).blurCache.prewarm(bitmap, binding.backdropImage.width, binding.backdropImage.height)`; cỡ ≤ 0 thì dùng `binding.homeRoot`. Không đổi thứ tự `notifyChanged`.
- [x] **Step 3:** build xanh; commit `fix: prewarm the blurred backdrop when the wallpaper loads`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" mục E, trạng thái lạnh):** (a) trang cuối chỉ có icon (không thư mục/widget): `force-stop`, đợi 5 s, vuốt nhanh sang Library, quay màn hình: số khung ô nhóm còn nhợt trước và sau khi đáp; (b) thời điểm đổi màu ô sau khi đáp: **trước** ≈ 1,3 s (cần log xác nhận), **sau**: không có đổi màu ô nào sau khi đáp quá ngưỡng (|Δ trung bình RGB của ô| ≤ 3) và ô đã ở màu cuối ngay khung đầu nhìn thấy. Nếu còn "nháy", làm Task 4.

---

### Task 4: D2 — Library dựng ô sẵn và chuyển pha mượt

**Bắt buộc kiểm tay:** không.
**Files:** `CategoryPageView.kt` (phần kính/dựng ô), `BackdropBlurDrawable.kt`.
**Interfaces:** Consumes: kích thước cha của `CategoryPageView`.

- [x] **Step 1: dựng ô sớm.** Khi `render(groups)` chạy lúc `width == 0` mà đã biết cỡ cha (`(parent as View).width/height`) thì tính `Sizes` bằng cỡ đó và dựng ô khi rảnh (`Looper.myQueue().addIdleHandler`); nhớ `Sizes` đã dùng; `onSizeChanged` chỉ gọi lại `render` nếu `Sizes` mới khác. Gỡ idle handler ở `onDetachedFromWindow`. Không dựng khi `groups == null` hay `lastGroups` chưa có.
- [x] **Step 2: chuyển pha.** `BackdropBlurDrawable`: ghi mốc `uptimeMillis` khi `onSmallReady` làm `smallBitmap` chuyển từ null sang có (chỉ khi ô đã từng vẽ phẳng), trong ~150 ms vẽ phẳng bên dưới và kính bên trên với alpha tăng dần, `owner.invalidate()` mỗi khung đến hết rồi dừng; không cấp phát; `setAlpha` (GF) nhân vào như cũ. Ô chưa từng vẽ phẳng (bản mờ có sẵn) vẽ kính thẳng.
- [x] **Step 3:** build xanh; commit `fix: build library tiles early and fade the glass in`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" mục E):** như Task 3 Step 4 ở cả trạng thái lạnh (sau `trim`: `adb shell am send-trim-memory gambi.global.os26launcher COMPLETE`) và nóng; thêm: tổng thời gian luồng chính của `render` Library lúc hiện lần đầu (log) giảm so với trước.

---

### Task 5: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không.
**Files:** `docs/knowledgements/FX8-glass-backdrop-match.md` (tạo), plan này (nhật ký, bằng chứng, kết quả).

- [x] **Step 1:** viết knowledgement theo mẫu mục 8 của MultiAgent-Workflow.md: sơ đồ Mermaid (sequenceDiagram: `BackdropController.render` → `prewarm` → `GlassBlur` → `onBuilt` → `BackdropBlurDrawable.prepareShader` dùng `imageMatrix`), bài học cho `glass`, đề xuất cập nhật tài liệu chung.
- [x] **Step 2:** rebase (điều phối), build lại xanh, điền bảng bằng chứng Review Focus, chuyển PR sang Ready.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (serial `BGB00153427`, Android 11, 1080x2340, mức kính Ép cao; thêm lượt Ép thấp cho dock). Oppo A3x chỉ khi cần đối chiếu Lens (không bắt buộc cho FX8). **Chỉ chạy khi chủ dự án nói máy rảnh.**

### A. Chuẩn bị (một lần)
1. `adb devices`; xác nhận Os26Launcher ở trước (`adb shell dumpsys activity activities | findstr /i "topResumedActivity mResumedActivity"`); nếu không phải, dừng và hỏi.
2. Sao lưu toàn bộ (Git Bash, `MSYS_NO_PATHCONV=1`): `adb exec-out "run-as gambi.global.os26launcher tar cf - databases files no_backup" > backup.tar`; ghi hash từng file (kể cả `-wal`/`-shm`) để so sau khi trả (bài học 2026-11-09: một lần trả bị cắt cụt, exit 0).
3. Ghi lại ảnh nền hiện tại của chủ dự án (`files/backdrop_image`, md5) để trả lại.
4. Dựng ảnh thử (Python + Pillow, máy dev):
```python
# fx8_gen.py: lưới ô ngẫu nhiên có số nhận dạng; usage: python fx8_gen.py
import random
from PIL import Image
def grid(w, h, cw, ch, name, seed=1, alpha=None):
    r = random.Random(seed); mode = "RGBA" if alpha is not None else "RGB"
    im = Image.new(mode, (w, h))
    for y in range(0, h, ch):
        for x in range(0, w, cw):
            c = (r.randrange(40, 256), r.randrange(40, 256), r.randrange(40, 256))
            if alpha is not None: c += (alpha(x, y, w, h),)
            im.paste(c, (x, y, min(x + cw, w), min(y + ch, h)))
    im.save(name)
grid(1632, 2912, 102, 104, "fx8_owner.jpg")                 # ảnh chủ dự án, ô 102x104
grid(1080, 2340, 90, 90, "fx8_screen.jpg")                  # đúng tỉ lệ màn
grid(527, 1140, 40, 40, "fx8_small.jpg")                    # ảnh nhỏ
grid(3024, 4032, 168, 168, "fx8_34.jpg")                    # 3:4
grid(1600, 1600, 100, 100, "fx8_square.jpg")                # vuông
grid(4000, 1000, 100, 100, "fx8_pano.jpg")                  # toàn cảnh
grid(1080, 2340, 90, 90, "fx8_alpha.png", alpha=lambda x,y,w,h: 128 if (y // 90) % 2 else 255)  # hàng alpha 0,5
```
(`.jpg` lưu mặc định chất lượng 75; thêm `quality=95` nếu cần.) Ghi thẳng các file này sang tên `backdrop_image` (định dạng nhận theo nội dung, không theo đuôi).
5. Danh sách ô cần đo: dùng `uiautomator dump` (`adb shell uiautomator dump /sdcard/ui.xml`; `adb pull`) lấy `bounds` của đồng hồ, lịch, ô thư mục và dock (content-desc), đưa vào `tiles.json` dạng `[{"name":"clock","x0":..,"y0":..,"x1":..,"y1":..}, ...]`. Không đoán toạ độ.

### B. Một lượt cho mỗi ảnh nền (Git Bash)
```
export MSYS_NO_PATHCONV=1
adb push C:/path/fx8_owner.jpg /data/local/tmp/fx8_wall
adb shell "run-as gambi.global.os26launcher cp /data/local/tmp/fx8_wall files/backdrop_image"
adb shell am force-stop gambi.global.os26launcher
adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity
sleep 4
adb exec-out screencap -p > shot_owner.png
adb logcat -d | grep -i FX8TMP > log_owner.txt      # chỉ với APK có log tạm (Task 1 Step 2)
python fx8_offsets.py shot_owner.png fx8_owner.jpg 1080 2340 tiles.json
```
**Script đo (`fx8_offsets.py`; đã có script của điều phối thì dùng, tương đương):**
```python
# Với mỗi góc ô: tìm (dx,dy) làm NCC giữa mảng ảnh chụp và ảnh nền tham chiếu (centerCrop rồi mờ) lớn nhất.
import sys, json, numpy as np
from PIL import Image, ImageFilter
shot_p, wall_p, W, H, tiles_p = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
SIGMA, PATCH, INSET, RX, RY = 10, 40, 18, 40, 12          # σ ≈ 9,8 px màn hình (3 lượt box 2 px ở 25%)
shot = np.asarray(Image.open(shot_p).convert("RGB"), float)
im = Image.open(wall_p).convert("RGB"); s = max(W / im.width, H / im.height)
im = im.resize((round(im.width * s), round(im.height * s)), Image.BILINEAR)
l, t = (im.width - W) // 2, (im.height - H) // 2
ref = np.asarray(im.crop((l, t, l + W, t + H)).filter(ImageFilter.GaussianBlur(SIGMA)), float)
def ncc(a, b):
    a = a - a.mean((0, 1)); b = b - b.mean((0, 1))
    return float((a * b).sum() / (np.sqrt((a * a).sum() * (b * b).sum()) + 1e-9))
for tl in json.load(open(tiles_p)):
    for cn, (cx, cy) in {"TL": (tl["x0"] + INSET, tl["y0"] + INSET), "TR": (tl["x1"] - INSET - PATCH, tl["y0"] + INSET),
                         "BL": (tl["x0"] + INSET, tl["y1"] - INSET - PATCH), "BR": (tl["x1"] - INSET - PATCH, tl["y1"] - INSET - PATCH)}.items():
        a = shot[cy:cy + PATCH, cx:cx + PATCH]; best = (-2, 0, 0)
        for dy in range(-RY, RY + 1):
            for dx in range(-RX, RX + 1):
                y, x = cy + dy, cx + dx
                if 0 <= y and y + PATCH <= H and 0 <= x and x + PATCH <= W:
                    c = ncc(a, ref[y:y + PATCH, x:x + PATCH])
                    if c > best[0]: best = (c, dx, dy)
        print(f'{tl["name"]:8s}{cn} dx={best[1]:+d} dy={best[2]:+d} corr={best[0]:.2f}')
```
Chỉ tin dòng có `corr ≥ 0,9` (NCC bất biến theo gain/offset nên không bị tint trắng 15% làm lệch); góc nằm dưới icon/chữ thì bỏ (chọn `INSET` lớn hơn).

**Kỳ vọng (Live 4, ảnh nền → trước sửa → sau sửa; ngưỡng đạt là 0 ± 4 px mọi góc, mọi ô):**

| Ảnh | Trước (nếu H-A đúng, tính từ mô phỏng) | Sau |
|---|---|---|
| `fx8_owner.jpg` 1632x2912 | góc x ≈ 465: −16; x ≈ 617: +16 (đo −14/+16); mép ±116 | 0 ± 4 |
| `fx8_screen.jpg` 1080x2340 | 0 | 0 ± 4 |
| `fx8_small.jpg` 527x1140 | ≤ 1 (không phân biệt được) | 0 ± 4 |
| `fx8_34.jpg` 3024x4032 | −47 / +48; mép ±338 | 0 ± 4 |
| `fx8_square.jpg` 1600x1600 | −88 / +90; mép ±630 | 0 ± 4 |
| `fx8_pano.jpg` 4000x1000 | −575 / +590 | 0 ± 4 |
| Dock, mức Ép thấp, `fx8_small.jpg` | trái/phải −7,5/−9,0 px, trên/dưới −3,8/−6,7 | 0 ± 4 |
Ghi cả `corr`. Nếu "trước" khác bảng này nhiều (ví dụ log cho thấy bitmap đã cắt đúng), báo điều phối: nguyên nhân khác H-A.

### C. Cỡ view đổi sau khi nạp — BỎ (chủ dự án/điều phối: không dùng `wm size`, Q4); giữ lại chỉ làm ghi chú tính toán
`adb shell wm size 1080x2000`, đợi 2 s, chụp, đo hai ô ở y ≈ 300 và y ≈ 1800 trên `fx8_screen.jpg`; **trước**: −119 / +136 px (mô phỏng); **sau**: 0 ± 4. Luôn `adb shell wm size reset` (đặt trong `finally`/trap).

### D. Độ mờ và alpha
- σ: với `fx8_small.jpg` và `fx8_screen.jpg`, đo σ ∈ {6, 8, 10, 12, 16, 20, 24} cho NCC cao nhất ở vòng quanh mỗi ô (vòng ngoài icon). Trước: ~20 (small), ~10 (screen); sau: 10 ± 2 cho cả hai.
- Alpha: `fx8_alpha.png` (hàng 90 px xen kẽ alpha 1 và 0,5): chênh trung bình độ sáng vòng ô giữa ảnh chụp (đã hiệu chuẩn tint) và tham chiếu "ảnh vẽ lên đen rồi mờ"; trước ≈ 100/255, sau ≤ 8/255.

### E. D2 — kính Library lúc hiện (trạng thái lạnh, ghi rõ bản debug)
1. Chuẩn bị: trang cuối chỉ có icon (không thư mục, không widget): nếu cần dời/xoá thử, xác định mục thử xoá **từ DB** (không từ màn hình). Ảnh nền: `fx8_owner.jpg`.
2. `adb shell am force-stop ...`, mở Home, vuốt tới trang cuối bằng `input swipe`, đợi 5 s. Quay `adb shell screenrecord --size 540x1170 --bit-rate 8000000 --time-limit 10 /sdcard/fx8_lib.mp4` **chạy nền**, ngay sau đó `adb shell input swipe 900 1200 150 1200 120` (vuốt nhanh), chờ hết 10 s, `adb pull`.
3. Tách khung: `ffmpeg -i fx8_lib.mp4 -vf fps=30 frames/%04d.png`. Lấy `bounds` một ô nhóm Library bằng `uiautomator dump` sau khi đáp. Chạy:
```python
# fx8_pop.py: trung bình RGB của ô theo thời gian; đáp = khung đầu tiên mà vị trí ô ổn định; báo thay đổi sau đáp.
import sys, glob, numpy as np
from PIL import Image
x0, y0, x1, y1 = map(int, sys.argv[1:5])                  # bounds đã quy về 540x1170 (nhân 0,5)
fr = sorted(glob.glob("frames/*.png")); m = np.array([np.asarray(Image.open(f).convert("RGB"), float)[y0:y1, x0:x1].mean((0, 1)) for f in fr])
d = np.abs(np.diff(m, axis=0)).max(1)
for i, v in enumerate(d):
    if v > 3: print(f"khung {i+1}->{i+2} ({(i+1)/30:.2f}s): |dRGB| tối đa {v:.1f}")
```
(Vị trí tuyệt đối của ô đổi lúc trượt, nên đối chiếu thêm: xem video; "đáp" là khung Library ngừng dịch.) **Trước sửa:** có một bước đổi ≥ 3 sau khi đáp, ở ~1,3 s (xác nhận bằng log `FX8TMP` request→ready). **Sau sửa:** không có bước đổi > 3 sau khi đáp, và ô nhóm ở màu cuối ngay khung đầu nhìn thấy.
4. Lặp lại ở trạng thái nóng (đã mở Library một lần) và sau `adb shell am send-trim-memory gambi.global.os26launcher COMPLETE` (cache lạnh lại): với Task 4 phải không còn bước đổi đột ngột (chuyển pha ≤ 150 ms, mỗi khung đổi ≤ 3 nếu lấy mẫu 60 fps).

### F. Hồi quy (ngắn)
Đổi ảnh nền (Cài đặt) rồi về Home: kính cập nhật đúng ảnh mới, không còn phẳng quá 1 s; sáng/tối với ảnh mặc định (`cmd uimode night yes/no`): kính đổi theo; vuốt trang, kéo ô, mở panel thư mục/nhóm: kính bám nền như trước (kiểm bằng ảnh chụp so với trước FX8 ở cùng chỗ); dock ở Ép cao không đổi.

### G. Trả máy
Trả `files/backdrop_image` ban đầu (so md5), khôi phục `databases files no_backup` từ bản sao lưu nếu đã đổi, `force-stop`, so hash từng file, báo cáo đã đổi gì trên máy.

**Kết quả kiểm chứng (Vsmart Live 4, Android 11, Ép cao, Os26 là launcher mặc định, debug, điều phối 2026-11-11 ~15:45, nhánh@`88a9c7a`):**
- **D1 không hồi quy, `FX8.apk`, góc widget trang 1:** lưới ngẫu nhiên 1632x2912: 0/0/0/0 px (dy ≤ 2); lưới ngẫu nhiên 527x1140: −2/0/−2/−2 px; lưới khối màu 1080x2340: 4/6/−6/−4 px (giá trị y hệt trên `FX8-proof.apk` = main nên là do họa tiết tuần hoàn của ảnh thử, không phải hồi quy). Ảnh thật của chủ dự án trên main: mép phải widget 0/−3 px.
- **D2, `FX8-d2log.apk`, ảnh chủ dự án, khởi động lạnh rồi vuốt nhanh vào Library:** `Category.earlyBuild idle width=0 parent=1080x2340 groups=8` ở +165 ms sau sự kiện hiển thị đầu; `BackdropController.render` → `Cache.request` → `Cache.onBuilt` mất 358 ms; Library hiện sau ~11,8 s: `Category.visibility=0`, `onSizeChanged 0x0 -> 1080x2340 sizesEqual=true` (không dựng lại), cả 8 ô nhóm `Drawable.GLASS first draw ... afterFlat=false`. Ô icon của Home vẽ phẳng ~60 ms lúc khởi động lạnh trước `onBuilt` rồi mờ vào (chấp nhận). `registered` tối đa 20, không tăng. Không FATAL.
- **Chưa chạy (đọc code, chủ dự án kiểm khi duyệt PR):** dock Ép thấp (`StaticBlurSurface`), PNG alpha, chuyển pha sau `trim`, σ đo trực tiếp.
- **Đính chính:** số đo D1 ban đầu (k ≈ 1,2) là trên APK cũ trước FX1; D1 đã được FX1 sửa, FX8 chỉ gia cố (ánh xạ qua `imageMatrix`) và đồng nhất độ mờ.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | Plan | Đọc lại toàn bộ chuỗi ảnh nền → kính; mô phỏng sai số; ghép số đo D1 của điều phối (1632x2912) vào bảng; phân tích D2 | (commit plan) | Điều phối duyệt plan; trả lời Q1–Q4 ở mục trên |
| 2026-11-11 | Plan | Cập nhật theo duyệt: PR #39, ngoại lệ `HomeViewModel`, bỏ `wm size` | `d639ff5` | |
| 2026-11-11 | 1 | Code: `BackdropBlurDrawable.prepareShader` và `StaticBlurSurface` dùng `imageMatrix` + cỡ intrinsic; dựng `FX8-proof.apk` (main + log) và `FX8-task1.apk` | `2d36089` | Điều phối đo `FX8-proof.apk`: H-A..H-E đều bị bác, số đo k ≈ 1,2 ban đầu là APK cũ trước FX1 (điều phối cài nhầm); D1-1 đã được FX1 sửa. Task 1 giữ làm gia cố, kiểm "không hồi quy"; tick sau khi điều phối kiểm bản cuối |
| 2026-11-11 | 2 | `BlurredBackdropCache`: bản nhỏ = 25% cỡ view (không theo cỡ bitmap), khóa cache theo (bitmap, cỡ đích), trả bản cũ khi cỡ view đổi; nhân alpha vào RGB trước khi mờ; thêm `prewarm`; báo cả người đã đăng ký khi dựng xong. Build xanh | `8a61571` | Chờ điều phối kiểm (kiểm mờ/alpha ở bài kiểm cuối) |
| 2026-11-11 | 3 | `BackdropController.render` gọi `blurCache.prewarm(bitmap, homeRoot.w, homeRoot.h)` ngay sau `setImageBitmap` (dùng cỡ `home_root` vì `backdropImage` vừa hiện chưa có cỡ). Build xanh | `2053aa7` | Task 3 là Task đo: chờ điều phối đo D2 bằng `FX8-d2log.apk` |
| 2026-11-11 | 4 | `CategoryPageView`: dựng ô khi luồng chính rảnh theo cỡ cha, `Sizes` thành data class, `onSizeChanged` không dựng lại nếu `Sizes` không đổi; `BackdropBlurDrawable`: chuyển pha phẳng→kính 150 ms bằng `saveLayerAlpha`. Build xanh | `88a9c7a` | Dựng `FX8.apk` (sạch, = `88a9c7a`) và `FX8-d2log.apk` (cộng log `FX8TMP` có thời gian, patch ngoài repo)
| 2026-11-11 | 1–4 | Điều phối kiểm Live 4 (~15:45): D1 không hồi quy, D2 kính Library sẵn sàng (số ở "Kết quả kiểm chứng"); tick Task 1–4 | `88a9c7a` | Review đạt, vòng 1/5 |
| 2026-11-11 | 5 | Viết knowledgement, điền bảng bằng chứng, chuyển PR #39 sang Ready | (commit docs) | Chờ điều phối rebase lên `main` (a548b0d, chỉ docs) |

## Câu hỏi đã được trả lời (điều phối, 2026-11-11, theo ủy quyền của chủ dự án)

- **Q1:** có. FX8 được sửa `HomeViewModel.kt` **chỉ** ở các dòng `onScreenSize` / `rememberScreenSize` / nạp lại khi cỡ đổi, chỉ khi log Task 1 cho thấy cỡ ≤ 0 hoặc sai (ngoại lệ do điều phối cấp; FX7 chỉ sửa `onIconLongPressed`, nhánh merge sau rebase).
- **Q2:** sửa kính mờ tại chỗ (ô widget/thư mục/nhóm, pill, `StaticBlurSurface`); giữ khúc xạ ở rìa của kính thật như thiết kế. Chủ dự án khiếu nại về mép widget = kính mờ tại chỗ.
- **Q3:** có: σ ≈ 10 px màn hình nhất quán cho mọi ảnh.
- **Q4:** không `wm size` trên máy của chủ dự án; bỏ phép thử đó.
- Ngoài vùng được phép: `BackdropController.kt` (lời gọi `prewarm`), `CategoryPageView.kt` (phần kính/dựng ô).

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | `BackdropBlurDrawable`: ma trận qua `imageMatrix` (sao chép), khóa dựng lại đủ trường, không `setScale` cũ | Đọc code (`prepareShader`); Live 4: góc widget 0/0/0/0 (1632x2912), −2/0/−2/−2 (527x1140), 4/6/−6/−4 (khối màu 1080x2340, như main) | Đạt |
| 2 | `StaticBlurSurface`: ma trận float, không `Rect` nguyên | Đọc code; dock Ép thấp chưa chạy trên máy | Đọc code; chủ dự án kiểm khi duyệt PR |
| 3 | `BlurredBackdropCache.build`: cỡ theo view, khóa (bitmap, cỡ đích), alpha | Đọc code; σ đo trực tiếp và PNG alpha chưa chạy | Đọc code; chủ dự án kiểm khi duyệt PR |
| 4 | `prewarm`: một lần mỗi (bitmap, cỡ), không giữ nguồn, `trim` đúng | Live 4: `render` → `request` → `onBuilt` 358 ms, một lần; `registered` tối đa 20 không tăng. `trim` + fade chưa chạy | Đạt (trừ `trim`: đọc code) |
| 5 | `CategoryPageView`: dựng sớm, không dựng lại khi `Sizes` không đổi, gỡ `IdleHandler` | Live 4: `earlyBuild` +165 ms, `onSizeChanged 0x0 -> 1080x2340 sizesEqual=true`; gỡ handler: đọc code | Đạt |
| 6 | Chuyển pha phẳng → kính không cấp phát, `setAlpha` GF nhân đúng | Live 4: 8 ô Library `afterFlat=false`; ô Home FLAT ~60 ms rồi mờ vào; chuyển pha sau `trim` chưa chạy | Đạt (trừ `trim`) |
| 7 | Không file ngoài vùng, không log tạm sót | `git diff --stat origin/main...HEAD`: 3 file `core/glass`, `BackdropController.kt`, `CategoryPageView.kt`, plan; `grep -rn FX8TMP app/src` rỗng | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: không.
- `project_structure.md`: mục 5 `core/glass`: kính tại chỗ lấy ánh xạ từ `imageMatrix` của `ImageView` ảnh nền; `BlurredBackdropCache` dựng theo cỡ view và có `prewarm`.
- `room-schema.md`: không.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `docs/lessons/glass.md`), gồm: bất biến "bitmap cùng tỉ lệ view" phải được kiểm ở chỗ vẽ chứ không chỉ ở nguồn; công thức sai số e(x) = x·(bw·s/vw − 1) + dx; kính mờ không được lười dựng bản mờ.
