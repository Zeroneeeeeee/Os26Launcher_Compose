# GL — Áp kính (phần còn lại của G2a, TT#9, TT#10, TT#11) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính — nhánh này là nhánh duy nhất dùng Oppo trong đợt 0a). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/gl-glass-pass` · **Worktree:** `E:\SourceTree\IosLauncher-wt\GL-glass-pass` · **PR:** #3 · **Bắt đầu:** 2026-11-03 · **Plan duyệt:** 2026-11-03
**Trạng thái:** Xong 2026-11-04: chủ dự án kiểm cuối đạt (trừ lỗi đổi ảnh nền, dời sang nhánh hoạt ảnh), merge PR #3.
**Khóa:** cần Oppo · `core/glass/*` (độc quyền) · không khóa schema

**Goal:** Áp kính cho bảy phần tử còn phẳng sau G2a: panel thư mục, ô thư mục đóng, panel nhóm App Library, ô nhóm App Library, thanh tìm kiếm của Library, thanh tìm kiếm của lớp tìm kiếm (M8b), pill tìm kiếm (M8c). **Đo Janky cạnh nhau trước/sau** trên Vsmart Live 4 và Oppo A3x, chọn mức kính từng phần tử theo số đo, và phần tử nào vượt ngân sách của tier thì rơi về nền phẳng như cũ (FLAT) mà không vỡ giao diện.

**Architecture:** Ba điểm khác với cách dock đang làm (xem "Vì sao chọn thiết kế này"):
1. Phần tử **di chuyển** (ô thư mục trong `ViewPager2`, ô nhóm trong `ScrollView` nằm trong lớp phủ trượt) dùng **kính tại chỗ**: một `Drawable` (`BackdropBlurDrawable`, mới, trong `core/glass`) tự vẽ lát cắt ảnh nền đã làm mờ cho đúng vị trí **hiện tại** của view trên màn hình, tính lại mỗi lần vẽ; view chủ chỉ cần `invalidate()` khi cha cuộn. Không thêm `View` anh em vào `IconGridView`, không cần `LiquidGlassView` cho ô.
2. Phần tử **đứng yên** (panel, thanh tìm kiếm, pill) cũng dùng `BackdropBlurDrawable` làm mặc định cho mọi tier; riêng **hai panel** trên tier LENS sau này nâng lên kính thật `RealGlassSurface` (Task 7), vì đó là bề mặt lớn, đứng yên, mỗi lúc chỉ một cái.
3. Một hàm thuần `GlassPolicy` (tier + vai trò → `GlassMode { REAL, STATIC_BLUR, FLAT }`) cộng ngân sách `GlassBudget` mở rộng quyết định ai được kính; nơi vẽ không bao giờ tự kiểm `Build.VERSION`. `GlassEnvironment` (Hilt singleton, lấy qua `EntryPoint`) giữ tier hiện tại và bộ đệm làm mờ để các view lấy được mà **không phải sửa `HomeActivity`**.

Dock giữ nguyên (`RealGlassSurface` hoặc `StaticBlurSurface` qua `GlassSurfaceFactory`), không động vào.

**Tech Stack:** View/XML, Canvas (`BitmapShader` + `drawRoundRect`), Hilt `EntryPoint`, thư viện `com.github.QWEA0:liquidglass:v2.0.11` (đã ghim; **không thêm dependency**). API thư viện dùng ở plan này đã đọc thẳng từ AAR trong cache Gradle (`javap`, 2026-11-03), không đoán: `LiquidGlassView.backdropSource`, `enableDynamicBackground`, `cornerRadius`, `glassTint`, `material`, `collectFrameStats`, `debugApiLevelCap`, `effectiveApiLevel`, `accessibilityMode`.

**Spec:** ghi chú "Áp kính" cuối `docs/plans/G2a-folders-library.md` (nguồn của phạm vi), Task A5 Step 1 và "Việc để sau" của Task B4 cùng file; `docs/plans/M6-glass-layer.md` (tier, `GlassBudget`, cách đo Janky Task 2); `docs/LiquidGlass-Demo.md` Quyết định 3 (bảng phần tử kính theo tier, người dùng chấp nhận 2026-10-15); `Launcher-Features.md` TT#9, TT#10, TT#11.

## Vì sao chọn thiết kế này

### Vấn đề cần giải

Tám sự thật của dự án (nhãn nguồn cạnh từng dòng):

1. `StaticBlurSurface.computeCrop()` chỉ cắt nền **một lần** theo vị trí màn hình lúc vẽ đầu; `RealGlassSurface` để `enableDynamicBackground = false` (đọc code). Dock đứng yên nên đủ; ô trong trang vuốt thì lệch (AGENTS.md, mục "Viết docs", 2026-10-30).
2. Ô thư mục đóng **vừa di chuyển vừa bị xoay**: nằm trong `ViewPager2` (trượt khi vuốt), `Jiggler` xoay **chính `IconCellView`** ±5° (đọc code; TL1 đang giảm biên độ), và lúc kéo thì `snapshotWithoutBadge()` gọi `draw()` của đúng ô đó để làm ảnh bay theo tay (đọc code). Thứ gì nằm **ngoài** ô (view anh em) sẽ không xoay theo và không có mặt trong ảnh kéo.
3. Ô nhóm App Library nằm trong `ScrollView`, và cả lớp Library trượt bằng `translationX` 220 ms khi mở/đóng (đọc code, D17).
4. Số đo cũ, cả hai là **đo được**: Live 4, 1 kính ở dock, vuốt trang 15–20 s: không kính Janky 0.10%, `RealGlassSurface` 10.29%, `StaticBlurSurface` 2.13% (M6 Task 2). Demo, nền animate mọi frame: Live 4 42.7 fps với 1 kính và 16.4 fps với 6; Oppo giữ ~60 fps đến 6 kính. Chưa từng đo **nhiều** `StaticBlurSurface`, và chưa từng đo kính trong lúc vuốt/mở panel. **Cập nhật 2026-11-03 (điều phối đo trên Live 4, bản TL1 probe cùng cách vẽ với `main`):** khi vuốt trang "trước" đã là Janky 72.87% (p50 19 ms, p95 32 ms, 60 Hz), UI thread tốn ~14.4 ms (p50) ở `DrawStart→SyncQueued` mỗi khung cuộn; Home đứng yên thì 0 khung. Nghĩa là các con số M6 (0.10%/2.13%/10.29%) là số của cảnh đứng yên, không so trực tiếp được với kịch bản vuốt. TL1 đang tìm cái gì ghi lại display list mỗi khung cuộn; nếu TL1 sửa gốc rễ thì số "trước" của GL đổi và phải đo lại.
5. `GlassBudget`: LENS = 6, CLASSIC = 1 (dành cho dock). Tier `CLASSIC_GPU` (API 31–32) không có máy đo.
6. Kính thật `enableDynamicBackground = false` **không** tự chụp lại khi bitmap nền đổi, phải bật động 1 giây (`pulseGlassRefresh`, M8a Task 5, **đo được** trên Oppo). Thêm: các panel và lớp tìm kiếm nằm dưới lớp tối `search_scrim` `#CC000000` (80% đen) với chữ trắng, nên kính ở đó phải có tint tối đủ để chữ đạt AA (đọc code; tương phản chưa đo).

7. **`LiquidGlassView` tự `invalidate()` mỗi lần cây view cuộn (điều phối + TL1 Task 1b chứng minh bằng simpleperf và `javap`, 2026-11-03):** kính đăng ký `OnScrollChangedListener` trên `ViewTreeObserver` của nền và gọi `invalidate()` vô điều kiện, kể cả khi `enableDynamicBackground = false`; mỗi khung cuộn kính chụp lại nền (~65% CPU app trên Live 4 khi dock là `RealGlassSurface`, ví dụ `FORCE_HIGH`). Với `StaticBlurSurface` cùng kịch bản vuốt cho Janky 2.34%, p50 10 ms, p95 13 ms. TL1 Task 2b thêm `GlassSurface.setInvalidationSuppressed(Boolean)` (mặc định no-op), lớp con `LiquidGlassView` trong `RealGlassSurface` ghi đè `invalidate()`, `DockView` chuyển cờ, `HomeActivity` bật theo trạng thái cuộn pager. **Hệ quả cho GL:** hai panel kính thật ở Task 7 sẽ dính cùng cơ chế mỗi khi có thứ gì cuộn trong cửa sổ (ScrollView của Library, danh sách panel nhóm) nên Task 7 phải dùng lại đúng API `setInvalidationSuppressed` đó (xem Task 7). Kính tại chỗ (`BackdropBlurDrawable`) không dùng `LiquidGlassView` nên không dính.

8. **Sau khi TL1 và TL2 merge vào `main` (đọc code sau rebase, 2026-11-03):** (a) `GlassSurface.setInvalidationSuppressed(Boolean)` (mặc định no-op), `RealGlassSurface.SuppressibleGlassView` và `DockView.setGlassInvalidationSuppressed` đã có; `HomeActivity` bật theo `ViewPager2` scroll state (khoảng dòng 617). (b) Pager nay dùng `TunedPagerSnapHelper` (cuộn snap bằng `LinearSmoothScroller` → `RecyclerView` cuộn) và `PagerFlipAnimator` (`beginFakeDrag/fakeDragBy`, ViewPager2 chuyển thành cuộn của `RecyclerView`): cả hai vẫn phát `RecyclerView.OnScrollListener.onScrolled`, nên `HomePagerAdapter.glassScrollListener` vẫn nhận (đọc code; xác nhận trên máy ở phiên đo). (c) `home_content` có `topReserve` hằng (26dp trên Live 4) và không còn bị đẩy xuống ở edit mode, nên bố cục đổi là layout → ô/pill vẽ lại, lát cắt nền đúng. (d) TL2 đổi `GridMetrics.ICON_SCALE` = 0.9 và `IconLoader`/`IconRenderer`/`IconShape` (icon thu nhỏ trong ô thư mục được bo góc tự động); không chạm file của GL.

### Phương án cho bài toán "phần tử kính di chuyển" (ô thư mục, ô nhóm)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Ô để phẳng, chỉ phần tử đứng yên có kính** | Panel, thanh tìm kiếm, pill có kính; ô thư mục và ô nhóm giữ nền phẳng hiện tại | Rủi ro thấp nhất, không đụng `IconGridView`, không lo lệch nền | Bỏ hai phần tử mà chủ dự án đã đưa vào phạm vi (ô thư mục là phần tử kính chính của iOS 26); nền "kính" chỉ có chỗ đứng yên | Không (làm **phương án lùi** nếu D thất bại ở Task 1) |
| **B. Kính là view anh em, tính lại vùng cắt khi `SCROLL_STATE_IDLE`, phẳng lúc đang di chuyển** (hướng 1 của ghi chú G2a) | Thêm `StaticBlurSurface`/`RealGlassSurface` làm con của `IconGridView` dưới mỗi ô thư mục; ẩn kính, hiện nền phẳng khi pager rời IDLE, hiện lại và `computeCrop()` lại khi IDLE | Tái dùng nguyên `GlassSurface` đã đo; không tốn gì lúc vuốt | (1) Mỗi lần vuốt kính **nhấp tắt/bật** đúng lúc mắt đang theo dõi chuyển động. (2) Edit mode rung liên tục nên ô **không bao giờ** có kính trong edit mode. (3) `Jiggler` xoay ô mà kính không xoay (nhược điểm của view anh em, mục 2 ở trên). (4) Ảnh kéo `snapshotWithoutBadge()` không chứa kính. (5) Thêm con vào `IconGridView` làm lệch chỉ số "con thứ i = ô thứ i" mà kéo-thả dựa vào, y như `DockView` phải thêm `contentOffset` ở M6; `IconGridView` là file nhiều bẫy nhất của dự án | Không |
| **C. `RealGlassSurface` thật với `enableDynamicBackground = true` chỉ lúc di chuyển** (hướng 2) | Bật động khi pager/edit/kéo, tắt khi IDLE | Hình ảnh trung thực (khúc xạ thật) trên LENS | Chỉ chạy được LENS: Live 4 là 16.4 fps với 6 kính động. Vẫn là view anh em nên dính các nhược điểm (3), (4), (5) của B. Hai đường mã (LENS động, CLASSIC phẳng) | Không (có thể xét lại cho riêng LENS sau khi có số đo, xem Câu hỏi mở 3) |
| **D. Kính tại chỗ: `Drawable` tự tính vùng cắt theo vị trí hiện tại mỗi lần vẽ** | Ô tự vẽ nền bằng `BackdropBlurDrawable`: `BitmapShader` của bitmap mờ nhỏ + `drawRoundRect`, ma trận shader suy từ `getLocationOnScreen()` của view và của ảnh nền ngay lúc vẽ. Cha cuộn thì gọi `invalidate()` ô (pager: `RecyclerView.OnScrollListener` đặt trong `HomePagerAdapter`; Library: `ScrollView` scroll listener + ghi đè `setTranslationX`) | Bám nền đúng lúc vuốt, rung, trượt Library (không nhấp tắt/bật); rung/xoay đi cùng ô; ảnh kéo có kính (vì nằm trong `draw()` của ô); không thêm view, `IconGridView` không đổi cấu trúc; chạy mọi tier; chi phí lúc đứng yên bằng 0 (display list tái dùng) | **Không có khúc xạ thật** (là kiểu `StaticBlurSurface` mà chủ dự án đã chấp nhận ở M6); lúc vuốt mỗi ô thư mục hiển thị bị ghi lại display list mỗi frame (suy luận **chưa đo**, N ≤ ~12 ô, chi phí ước vài chục micro-giây mỗi ô); phụ thuộc scroll listener của pager (TL1 đang làm mượt chuyển trang, phải kiểm lại sau khi rebase) | **Chọn**, với Task 1 là phép đo chứng minh/bác bỏ trước khi xây tiếp |

### Phương án cho phần tử đứng yên (panel, thanh tìm kiếm, pill)

| Phương án | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|
| Dùng lại `StaticBlurSurface` (view) cho mọi chỗ | Đã đo (2.13%) | `FrameLayout` + view con, cần `measure()` đúng (bẫy M6); `onDraw` cấp phát `Path()` mỗi lần vẽ và `clipPath` mỗi frame (đọc code); với `SearchPillView` và `EditText` phải bọc view | Không dùng thêm; **dock giữ nguyên** |
| `BackdropBlurDrawable` làm `background` / vẽ trong `onDraw` | Một primitive cho cả phần tử di chuyển lẫn đứng yên, cùng một cách tính vùng cắt; không cấp phát lúc vẽ; hợp với view tự vẽ như `SearchPillView`; ảnh nền đổi thì phát hiện bằng so sánh danh tính bitmap lúc vẽ nên **không cần sửa `HomeActivity.render()`** | Thêm một primitive thứ hai song song với `StaticBlurSurface` (ghi nhận nợ gộp sau, không làm ở GL) | **Chọn mặc định** |
| `RealGlassSurface` cho mọi tier | Đẹp nhất | Live 4 10.29% Janky chỉ với 1 kính (đo được) | Chỉ trên LENS, chỉ cho hai panel (Task 7) |

### Vì sao chọn D (và đo trước khi tin D)

- **Dựa trên đọc code, không phải "thường làm vậy":** D là phương án duy nhất không trượt ở cả bốn ràng buộc của phần tử di chuyển (trượt, xoay do `Jiggler`, ảnh kéo, chỉ số con của `IconGridView`). Khi bắt đầu từ ràng buộc chứ không từ `GlassSurface` có sẵn, B và C đều hỏng ở mục (3)–(5).
- **Dựa trên số đo của dự án:** Live 4 chỉ chịu nổi tới mức `StaticBlurSurface` (2.13%); chủ dự án đã chấp nhận mức đó bằng mắt (M6, 2026-10-28). D cùng kiểu vẽ nên cùng quỹ chi phí, nhưng thêm N ô ghi lại mỗi frame lúc vuốt chưa được đo ở đâu cả. **Vì vậy Task 1 là phép đo bắt buộc** (mục 4.2 Workflow: phép đo hiệu năng dùng để chọn phương án) trên đúng ca xấu nhất (12 ô thư mục hiển thị cùng lúc khi vuốt giữa hai trang, và edit mode rung) *trước khi* làm panel, thanh, pill, ô nhóm.
- **Quy tắc quyết định từ số đo** (xem D10, đã cập nhật 2026-11-03): so với "trước" (cùng APK, mặt nạ kính = 0, cùng phiên đo). Kịch bản có vuốt trên Live 4 chấm theo Δp50 ≤ +1 ms và Δp95 ≤ +4 ms thời gian khung (Janky đã bão hoà 72.87% ở "trước" nên chỉ tham khảo); kịch bản không vuốt chấm theo Janky +2.0 điểm % và Δp95 ≤ +4 ms; Oppo Janky +1.0 điểm %. Task 1 thử 0 / 6 / 12 ô hiển thị cùng lúc (K = 0 / 3 / 6 mỗi trang): đạt cả 12 thì D nguyên; chỉ đạt 6 thì giới hạn số ô có kính mỗi trang (`GlassBudget.maxLiteGlassCellsPerPage`); không đạt cả 6 thì lùi về **A** cho ô thư mục trên tier đó (tier khác vẫn D nếu đạt).
- **Thứ tự phần tử theo giá trị / rủi ro** (khác thứ tự phải giữ khi ngân sách chật):

| Thứ tự làm | Phần tử | Lý do |
|---|---|---|
| Task 1 (thử đầu tiên) | Ô thư mục đóng | Ca xấu nhất quyết định cả thiết kế: nhiều ô, trượt, rung, kéo |
| Task 2 | Panel thư mục, panel nhóm | Bề mặt lớn, đứng yên, mỗi lúc một cái, hiệu ứng thấy rõ nhất; mốc để thử tint/tương phản |
| Task 3 | Thanh tìm kiếm M8b, thanh Library, pill M8c | Nhỏ, đứng yên, `SearchPillView` kích thước cố định nên không `requestLayout` (M8c) |
| Task 4 | Ô nhóm Library | ≤ 8 ô, di chuyển theo `ScrollView` + `translationX`, tái dùng đúng cơ chế ô thư mục |
| Task 7 | Panel thành kính thật trên LENS | Làm cuối vì cần Oppo và không nên chặn các Task kia |

  **Thứ tự rơi về FLAT khi vượt ngân sách** (ưu tiên giữ kính từ trên xuống): panel > pill và hai thanh tìm kiếm > ô nhóm Library > ô thư mục vượt `maxLiteGlassCellsPerPage` (theo thứ tự đọc của trang).

### Đánh đổi chấp nhận, khi nào xét lại

- Trên Oppo (LENS), ô thư mục, ô nhóm, thanh và pill là **kính mờ tĩnh** (cùng kiểu với dock trên Live 4), không có khúc xạ thật; chỉ dock và hai panel là kính thật. Xét lại (Câu hỏi mở 3) nếu chủ dự án muốn khúc xạ thật trên LENS: lúc đó mở thêm một phương án C **chỉ cho LENS** sau khi có số đo.
- Hai primitive song song (`StaticBlurSurface` cho dock, `BackdropBlurDrawable` cho phần còn lại). Gộp là việc sau GL.
- Tất cả phần tử đang có chữ trắng trên lớp tối 80%: kính ở đó có tint tối nên **trông tối hơn iOS thật**. Nếu chủ dự án muốn nhạt dần lớp tối (`search_scrim`/`folder_scrim`) thì đó là thay đổi thiết kế riêng, không làm trong GL (Câu hỏi mở 1).

## Quyết định đã chốt

"Chủ dự án chốt" chỉ ghi khi đã có tin nhắn của chủ dự án; mục còn lại là **đề xuất của agent, chờ duyệt**.

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Phạm vi: bảy phần tử nêu ở Goal; mốc "Áp kính" là việc cuối của G2a | Chủ dự án chốt 2026-11-03 (qua điều phối); ghi chú "Áp kính" cuối plan G2a |
| D2 | **Đo trước, chọn sau**; đo cạnh nhau trước/sau theo cách M6 Task 2, kịch bản của G2a A5 Step 1 | Chủ dự án chốt 2026-10-30 (hoãn A5 Step 1 sang đây) |
| D0 | Chủ dự án trả lời Câu hỏi mở (2026-11-03): (1) giữ lớp tối 80% `search_scrim` + kính tint tối, làm nhạt lớp tối là việc sau, ngoài GL; (2) CHẤP NHẬN ô thư mục/ô nhóm về FLAT trên tier CLASSIC nếu số đo buộc; (3) kính thật chỉ dock + hai panel (LENS), thanh và pill giữ kính mờ tĩnh, bỏ bước kính thật tuỳ chọn, `SearchPillView` không đổi cấu trúc; (4) phủ `CLASSIC_GPU` bằng `debugApiLevelCap = 32` trên Oppo + lập luận theo cấu trúc, không mượn máy Android 12; (5) chấp nhận ngưỡng D10 như đề xuất | Chủ dự án chốt 2026-11-03 (qua điều phối). Các câu hỏi mở ở trên coi như đã trả lời |
| D3 | Phần tử di chuyển dùng kính tại chỗ `BackdropBlurDrawable` (phương án D) | Đã duyệt cùng plan 2026-11-03. Task 1 là cổng: đo không đạt thì đổi sang A và báo lại chủ dự án |
| D4 | Phần tử đứng yên dùng `BackdropBlurDrawable` mặc định mọi tier; chỉ hai panel nâng lên `RealGlassSurface` và chỉ trên LENS (Task 7). Thanh và pill không dùng kính thật ở GL | Chủ dự án chốt 2026-11-03 (Câu hỏi mở 3) |
| D5 | `CLASSIC_GPU` dùng **cùng chính sách với `CLASSIC_CPU`** (không thêm kính thật ngoài dock). Lý do: `GlassBudget` CLASSIC = 1 đã dành cho dock; số đo duy nhất của tier classic là Live 4 | Chủ dự án chốt 2026-11-03 (Câu hỏi mở 4) |
| D6 | Nền phẳng hiện tại giữ nguyên làm chế độ `FLAT` (không xóa `bg_folder_panel`, `bg_category_tile`, `bg_category_search`, `bg_search_field`, màu `folder_background`, `search_pill_bg`) | Quy ước "không tự xóa file"; FLAT phải trông y như hôm nay |
| D7 | Phần tử có chữ trắng trên `search_scrim` dùng tint tối cố định (`GlassStyle` trong `colors.xml`, khối `gl_`), không dùng `enableAdaptiveTint` | Đề xuất; tương phản AA đo ở Task 8 |
| D8 | Không thêm khóa DataStore hay mục Cài đặt; dùng `glass_level_override` sẵn có (Tự động / Ép thấp / Ép cao) | Giống D15 của G2a |
| D9 | Không đổi `DockView`, `GlassSurfaceFactory.create()` cho dock, `HomeActivity`, `activity_home.xml` (xem Vùng file) | Điều phối giao 2026-11-03: TL1 giữ hai file nóng |
| D10 | Ngưỡng "đạt" của số đo, so với "trước" (`off`) trong CÙNG phiên đo. **Kịch bản có vuốt (S2, S3s) trên Live 4:** Δp50 thời gian khung ≤ +1 ms và Δp95 ≤ +4 ms (Janky chỉ tham khảo vì "trước" đã bão hoà ở 72.87%); **kịch bản không vuốt (S1, S3i, S4–S6) trên Live 4:** Janky +2.0 điểm % và Δp95 ≤ +4 ms; **Oppo:** Janky +1.0 điểm %, cộng báo p50/p95 (nếu "trước" trên Oppo cũng bão hoà thì áp quy tắc p50/p95 như Live 4 và báo điều phối) | Chủ dự án chốt ngưỡng ban đầu 2026-11-03; **điều phối đổi cách chấm kịch bản vuốt 2026-11-03** sau khi đo "trước" = Janky 72.87% (p50 19 ms, p95 32 ms), ~14.4 ms ở `DrawStart→SyncQueued` mỗi khung cuộn. Cơ sở cũ: M6 chấp nhận `StaticBlurSurface` ở +2.03 điểm %. **Ngoại lệ (chủ dự án, 2026-11-04):** hai panel kính thật trên LENS (Oppo, S1) vượt ngưỡng (Janky +2.15 điểm, p95 +8.6 ms: đúng 1 khung ~24 ms mỗi lần mở); chấp nhận, xem Task 7 |
| D11 | **App Library co theo kích thước cửa sổ** (Task 10): ô nhóm, icon, thanh tìm kiếm, nhãn nhân hệ số `s = clamp(min(1, rộng/REF_W, cao/REF_H), FLOOR, 1)` (tham chiếu 360 × 640 dp, `s = 1` ở toàn màn hình Live 4), có sàn; danh sách vẫn cuộn, không ép cả lưới vừa cửa sổ | Chủ dự án chốt hướng 2026-11-03 (bài kiểm TL1; điều phối làm rõ "co theo cửa sổ, vẫn cuộn" sau vòng rà 1/5). Công thức, tham chiếu và các sàn là đề xuất của agent, chờ duyệt plan bổ sung |
| D12 | **Nhãn icon Home co theo icon, ẩn khi quá nhỏ** (Task 11); layout Live 4 bình thường (icon 48dp, có nhãn) giữ nguyên từng điểm ảnh | Chủ dự án chốt 2026-11-03 (bài kiểm TL1). Cách làm (co nhãn trước, ẩn dưới sàn 8sp) và các ngưỡng là đề xuất của agent, chờ duyệt plan bổ sung |

## Câu hỏi mở (chỉ chủ dự án trả lời được)

1. **Lớp tối và chữ:** giữ lớp tối 80% (`search_scrim`) dưới panel/tìm kiếm/Library và dùng kính tint tối (an toàn về chữ, trông tối hơn iOS), hay sau khi có kính thì làm nhạt lớp tối (thay đổi thiết kế riêng, sau GL)?
2. **Chấp nhận phần tử phẳng:** nếu số đo bắt buộc, có chấp nhận ô thư mục/ô nhóm trở về phẳng (phương án A) trên tier CLASSIC, với thứ tự rơi như "Vì sao chọn thiết kế này"?
3. **Kính thật trên Oppo:** chỉ dock và hai panel là kính thật, còn ô/thanh/pill là kính mờ tĩnh — đủ chưa, hay muốn thêm kính thật cho thanh/pill (Task 7 Step 3, tốn cấu trúc) hoặc cho ô thư mục (phương án C riêng LENS)?
4. **`CLASSIC_GPU` (API 31–32):** chấp nhận phủ bằng cấu trúc + `debugApiLevelCap = 32` trên Oppo (không phản ánh tốc độ thật), hay mượn một máy Android 12 để đo S1–S3?
5. **Ngưỡng "đạt" (D10):** Live 4 +2.0 điểm % Janky và +4 ms ở 95th, Oppo +1.0 điểm % — đồng ý hay chỉnh?

## Ngoài phạm vi / ghi để sau

- Kính cho widget (G2c), dock (giữ nguyên), icon app, nút trên thanh edit mode, hộp thoại.
- Gộp `StaticBlurSurface` và `BackdropBlurDrawable` thành một; đưa dock sang primitive mới.
- Kính theo độ sáng nền / `enableAdaptiveTint`, đổi màu chữ theo nền (`setAppearanceListener`). Pill, thanh, ô dùng chữ trắng + tint tối.
- Tôn trọng "high text contrast" hệ thống cho phần tử `BackdropBlurDrawable` (thư viện chỉ lo phần `LiquidGlassView`; `StaticBlurSurface` của dock cũng chưa có). Ghi lại để quyết riêng.
- Làm nhạt lớp tối `search_scrim`/`folder_scrim` cho giống iOS hơn (Câu hỏi mở 1).
- Làm mờ nền thật sự phía sau panel (backdropSource = `home_content`): thử mắt ở Task 7 nếu muốn, không cam kết.
- Mượn máy Android 12 (API 31–32) để đo `CLASSIC_GPU` thật.

## Vùng file

`core/glass/*` là **độc quyền của GL** (Workflow mục 5.2). Hai file nóng của TL1 chỉ khai dự phòng.

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `core/glass/BackdropBlurDrawable.kt` | Tạo | độc quyền GL |
| `core/glass/GlassMode.kt` (enum `GlassMode`), `GlassRole.kt` (enum `GlassRole`), `GlassPolicy.kt`, `GlassStyle.kt` | Tạo | độc quyền GL |
| `core/glass/GlassEnvironment.kt` (+ `GlassEntryPoint`) | Tạo | độc quyền GL |
| `core/glass/GlassDebug.kt` | Tạo (tạm, xóa ở Task 9) | độc quyền GL |
| `core/glass/GlassSurface.kt`, `RealGlassSurface.kt`, `ui/home/view/DockView.kt` | **Đã do TL1 Task 2b sửa và merge** (2026-11-03): `setInvalidationSuppressed`, `SuppressibleGlassView`, `setGlassInvalidationSuppressed`. GL đã rebase; Task 7 chỉ thêm (`setTint`, `collectFrameStats`, dùng lại cờ chặn), không viết lại | TL1 (đã merge) → GL |
| `core/ui/GridMetrics.kt` | Sửa (Task 11: `Cells.labelScale/labelHeightPx/innerGapPx`, `showLabels` hiệu lực) | không nóng; TL2 chỉ đổi `ICON_SCALE`, đã merge |
| `ui/home/view/CategoryPageView.kt`, `core/ui/LibraryLayoutMetrics.kt` | Sửa / Tạo ở Task 10 (cỡ ô/thanh co theo cửa sổ; hàm thuần `LibraryLayoutMetrics`) | không nóng |
| `core/glass/BlurredBackdropCache.kt` (+ `small()`), `GlassBudget.kt (+ ngân sách ô/tile), `GlassSurface.kt` (+ `setTint`), `RealGlassSurface.kt` (+ `setTint`, `collectFrameStats = false`), `StaticBlurSurface.kt` (nhận `setTint` no-op), `GlassSurfaceFactory.kt` (chỉ móc debug `debugApiLevelCap`, Task 8) | Sửa | độc quyền GL |
| `ui/home/view/IconCellView.kt` | Sửa (`bindFolder` nhận kính, `drawFolder`) | không nóng; G2b/G2c/R1 sau này cũng đụng → GL merge trước |
| `ui/home/view/IconGridView.kt` | Sửa (cấp kính theo `GlassPolicy`, `invalidateGlass()`) | không nóng; nhiều bẫy kéo-thả (AGENTS.md M7), chỉ thêm, không đổi logic có sẵn |
| `ui/home/HomePagerAdapter.kt` | Sửa (`onAttachedToRecyclerView` + scroll listener) | không nóng; rủi ro chạm TL1 thấp, kiểm lại sau rebase |
| `ui/home/view/BackgroundGlass.kt` | Tạo (Task 2 dưới tên `BackgroundGlass`, đổi tên ở Task 3; helper gắn kính tại chỗ làm `background` của khung panel, ô nhập tìm kiếm, thanh Library) | không nóng |
| `ui/home/view/FolderPanelView.kt`, `CategoryGroupPanelView.kt` | Sửa | không nóng |
| `ui/home/view/CategoryPageView.kt`, `res/layout/view_category_page.xml`, `res/layout/item_category_group.xml` | Sửa | không nóng |
| `ui/home/view/SearchPillView.kt` | Sửa | không nóng |
| `ui/search/SearchOverlayView.kt`, `res/layout/view_search_overlay.xml` (thêm `id` cho khung ô nhập) | Sửa | không nóng |
| `res/values/colors.xml`, `res/values/dimens.xml` | Sửa (khối `gl_` ở **cuối** file) | dùng chung có kiểm soát |
| `ui/home/HomeActivity.kt` | **Dự kiến KHÔNG sửa.** Hunk dự phòng, chỉ khi bắt buộc: (a) `setupDock()` khối `lifecycleScope.launch { ... }`, khoảng dòng 770–775 (gắn thêm vào cùng chỗ đang gọi `glassSurfaceFactory.create`); (b) `render()` khoảng dòng 793–796 (chỗ gọi `pulseGlassRefresh`). Ngoài hai chỗ này không đụng | **độc quyền TL1**, GL merge sau TL1 và tự rebase |
| `res/layout/activity_home.xml` | **Dự kiến KHÔNG sửa.** Dự phòng: chỉ khối `folder_panel`, `category_group_panel`, `library_view` | **độc quyền TL1** |
| `data/apps/IconLoader.kt`, `IconCache`, `ui/home/view/Jiggler.kt` | **Không đụng** | TL2 (icon), TL1 (rung) |
| `ui/home/view/DockView.kt`, `HomeViewModel.kt`, `HomeUiState.kt` | **Không đụng** | độc quyền / không cần |

## Global Constraints

- `domain` không import `android.*`; `ui` không import `data`. `core/glass` được phép import `android.view` + thư viện kính và `domain/model`, `domain/repository` (đã có tiền lệ `GlassLevelOverride`).
- `GlassPolicy` là hàm thuần, không import `android.*` (chỉ enum và `Int`), để review bằng đọc.
- **Không đổi schema Room, không thêm khóa DataStore, không thêm dependency.**
- Mọi chữ ở `strings.xml`; màu ở `colors.xml`; kích thước ở `dimens.xml`; tiền tố `gl_` và thêm thành khối liền ở **cuối** file tài nguyên.
- Lưới 8dp, vùng chạm ≥ 48dp, tương phản chữ WCAG AA (mục tiêu; số đo ở Task 8).
- `FLAT` phải **y hệt** giao diện hiện tại (không thay đổi điểm ảnh của đường phẳng).
- Không tạo bitmap cỡ màn hình mới: bộ đệm của kính tại chỗ chỉ là bitmap nhỏ 8% (suy luận từ `BlurredBackdropCache.DOWNSCALE_FACTOR = 0.08`), dùng chung một bản cho mọi phần tử.
- Không cấp phát trong `draw()` của `BackdropBlurDrawable` (Matrix, Paint, RectF dùng lại).
- Không làm hỏng: kéo-thả/lật trang/trang tạm/"−"/menu nhấn giữ (M7), tạo/giải tán thư mục (G2a), vuốt xuống tìm kiếm, pill, mở Library bằng vuốt, đổi mức kính (M8a), đổi ảnh nền (M8a).
- Log tạm dùng tiền tố `GL`; **xóa ở Task 9**, grep `GL` + `GlassDebug` phải rỗng sau đó.
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự, kết thúc bằng dòng `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- **Lệch nền:** ô thư mục/ô nhóm có kính **bám** ảnh nền khi vuốt trang, khi rung (edit mode), khi Library trượt vào/ra, khi cuộn ô nhóm; ảnh thư mục đang bay theo tay có kính.
- **Chi phí lúc vuốt:** `invalidateGlass()` chỉ chạy cho view đang gắn và đang hiển thị; không rò listener (gỡ ở `onDetachedFromRecyclerView`, `onDetachedFromWindow`); không `invalidate()` thừa khi không có ô nào có kính.
- **Ảnh nền đổi / chưa có ảnh:** đổi ảnh ở Cài đặt → mọi phần tử đổi theo mà không cần `HomeActivity`; chưa có ảnh (`backdrop_image` GONE, drawable null) → rơi về `FLAT`, không crash, không ô trong suốt.
- **Vòng đời:** `recreate()` khi panel đang mở (đổi sáng/tối, đổi mức kính) — nợ kiểm tay từ G2a; tier đổi lúc chạy; split-screen đổi kích thước.
- **FLAT fallback:** ép `FORCE_LOW` (và, trong lúc đo, mặt nạ debug 0, nay đã gỡ) → giao diện y hệt `main`.
- **Tương phản:** chữ trắng (tiêu đề, nhãn, hint) đọc được trên ảnh nền rất sáng lẫn rất tối.
- **Panel kính thật (Task 7):** `alpha` fade 150 ms của `FolderPanelView` không làm kính chớp/đen; bàn phím sửa tên không làm kính chụp lại sai; dock không tệ đi sau khi tắt `collectFrameStats`.
- **Panel REAL và cuộn (Task 7):** chặn `invalidate()` đúng lúc (xem Task 7 Step 1b); đo CPU mở/đóng panel và cuộn ScrollView Library với panel kính thật ẩn.
- **Task 10/11 (cửa sổ thấp):** bình thường `s = 1` và `labelScale = 1` nên ảnh chụp trước/sau giống hệt; cửa sổ thấp không còn nhãn to cạnh icon nhỏ; Library co mà vẫn cuộn; kính ô nhóm bám nền sau khi đổi cỡ; `wm size` luôn được đặt lại.
- **Rebase TL1:** hunk `HomeActivity` (nếu có) đúng như khai; scroll listener của pager vẫn nhận sự kiện sau khi TL1 làm mượt chuyển trang.
- **Alpha của `BackdropBlurDrawable`:** `setAlpha` hiện chỉ áp cho `fillPaint` (tint, viền, màu phẳng bỏ qua alpha). Panel fade bằng `alpha` của **view** nên chưa ảnh hưởng; nếu bất kỳ nơi nào làm hoạt họa alpha của chính drawable thì sửa ở Task 2 (áp alpha cho cả bốn paint).
- **Hồi quy kéo-thả:** `IconGridView` chỉ số con không đổi; mọi nhánh bind ô thư mục vẫn đặt `alpha` theo dữ liệu (bài học A1b).

---

### Task 1: Nền tảng "kính tại chỗ" + ô thư mục thử nghiệm + phép đo sớm

**Trạng thái (2026-11-03):** code đã qua review điều phối; **phép đo Step 6 hoãn** tới khi TL1 Task 2b (sửa `LiquidGlassView` tự `invalidate()` khi cuộn) merge (**đã merge 2026-11-03, GL đã rebase; phiên đo gộp với Task 5 Live 4**), vì đo lúc này (máy có `glass_level_override = FORCE_HIGH`, dock là kính thật) bị chi phối bởi dock chụp lại nền mỗi khung, và Home của chủ dự án chưa có 6 thư mục mỗi trang. Task 2 được làm song song (panel đứng yên, không phụ thuộc cổng này). Nếu phép đo sau này cho kết quả "không đạt", Task 2 (panel) không bị ảnh hưởng.
**Bắt buộc kiểm tay:** có — mục 4.2 (phép đo hiệu năng dùng để chọn phương án): Task 3–4 xây trên giả định "ghi lại N ô mỗi frame lúc vuốt thì Live 4 vẫn chịu được và kính bám nền đúng". Sai thì đổi sang phương án A và làm lại các Task sau. Máy: Live 4 (Oppo để Task 5).
**Files:** Create `core/glass/{BackdropBlurDrawable,GlassMode,GlassRole,GlassPolicy,GlassStyle,GlassEnvironment,GlassDebug}.kt`; Modify `core/glass/{BlurredBackdropCache,GlassBudget}.kt`, `ui/home/view/{IconCellView,IconGridView}.kt`, `ui/home/HomePagerAdapter.kt`, `res/values/{colors,dimens}.xml` (khối `gl_` cuối file).
**Interfaces:**
- Consumes: `GlassTier`, `GlassTierResolver`, `SettingsRepository.settings`, `@ApplicationScope CoroutineScope`, `R.id.backdrop_image`, `PlacedFolder`.
- Produces:
  - `enum class GlassMode { REAL, STATIC_BLUR, FLAT }`, `enum class GlassRole { FOLDER_CELL, FOLDER_PANEL, GROUP_TILE, GROUP_PANEL, SEARCH_BAR, LIBRARY_SEARCH_BAR, SEARCH_PILL }`.
  - `object GlassPolicy { fun modeFor(tier: GlassTier, role: GlassRole): GlassMode }` — bảng điểm xuất phát (xem Task 6): mọi vai trò `STATIC_BLUR` trừ khi `GlassBudget` hay số đo nói khác; `REAL` chưa dùng ở Task 1.
  - `GlassBudget.maxLiteGlassCellsPerPage(tier): Int` (tạm = 24, chốt ở Task 6), `maxLiteGlassTiles(tier): Int` (tạm = 8).
  - `BlurredBackdropCache.small(source: Bitmap): Bitmap` (bitmap thu nhỏ 8%, **không** phóng lại; cache theo danh tính bitmap, tách riêng `blur()` hiện có để dock không đổi).
  - `class BackdropBlurDrawable(owner: View, backdrop: () -> ImageView?, cache: BlurredBackdropCache, style: GlassStyle)` với `cornerRadiusPx`, `draw(canvas)` tính ma trận mỗi lần vẽ từ `owner.getLocationOnScreen` và `backdrop.getLocationOnScreen`; thiếu ảnh nền thì vẽ nền phẳng `style.flatColor`.
  - `class GlassEnvironment @Inject (@Singleton)`: `fun tier(): GlassTier` (đồng bộ: lúc khởi tạo suy ra tier với `GlassLevelOverride.AUTO` bằng đúng phép dò của `GlassSurfaceFactory`; sau đó thu `settings` để cập nhật), `fun modeFor(role)` (= `GlassPolicy` + mặt nạ debug), `val blurCache`, `fun addListener/removeListener(() -> Unit)`; `companion fun from(context): GlassEnvironment` qua `EntryPointAccessors.fromApplication`.
  - `GlassDebug` (tạm): đọc **một lần mỗi tiến trình** tệp `files/gl_mask` nếu có, gồm các số cách nhau bằng dấu cách: `<mặt nạ> <K> <cap>` — mặt nạ: bit i bật = vai trò thứ i (thứ tự khai trong `GlassRole`) được kính, bit tắt = FLAT; K: số ô thư mục có kính tối đa mỗi trang (ghi đè `GlassBudget` để thử 0/6/12); cap (Task 8): `debugApiLevelCap`. Không có tệp = theo `GlassPolicy`.
  - `IconCellView.bindFolder(title, previews, cells, glass: BackdropBlurDrawable?)`; `IconGridView.invalidateGlass()`.

- [ ] **Step 1: `BlurredBackdropCache.small()` + `GlassStyle` + `BackdropBlurDrawable`.** Vẽ bằng `Paint` có `BitmapShader` (CLAMP) trên bitmap nhỏ, bật lọc bitmap; ma trận: bitmap nhỏ → (÷ hệ số thu nhỏ, ÷ tỉ lệ bitmap/ảnh nền giống `computeCrop()` cũ) → tọa độ view ảnh nền → trừ vị trí của owner. Sau đó lớp tint (`GlassStyle.tint`), viền sáng mảnh 1dp, bo góc bằng `drawRoundRect` (không `clipPath`). `setBounds` quyết định vùng vẽ, nên dùng được cả làm `background` lẫn vẽ trong `onDraw` của view tự vẽ (pill). Phát hiện ảnh nền đổi bằng so danh tính `BitmapDrawable.bitmap`, không cần callback từ ngoài.
- [ ] **Step 2: `GlassMode`/`GlassRole`/`GlassPolicy`/`GlassBudget`/`GlassEnvironment`/`GlassDebug` + khối `gl_` trong `colors.xml`/`dimens.xml`.** `GlassEnvironment` thu `settings` trong `@ApplicationScope`, khi tier đổi thì báo listener ở main thread. Đường `FLAT` không đụng gì.
- [ ] **Step 3: Ô thư mục.** `IconCellView.drawFolder` vẽ `glass` (nếu có) thay `folderBgPaint`; `bindFolder` nhận `glass`, `bind` (ô app) đặt `glass = null` (bài học A1a: ô được tái dùng giữa app và thư mục, phải xóa trạng thái vẽ cả hai chiều). `IconGridView.bindFolderCell` hỏi `GlassEnvironment.modeFor(FOLDER_CELL)`, cấp tối đa `GlassBudget.maxLiteGlassCellsPerPage(tier)` ô đầu theo thứ tự đọc (y, x) của trang; còn lại `FLAT`. `alpha` vẫn theo `hiddenItemId` ở mọi nhánh.
- [ ] **Step 4: Kéo `invalidate()` theo cha cuộn.** `HomePagerAdapter.onAttachedToRecyclerView` thêm `RecyclerView.OnScrollListener.onScrolled` → duyệt con là `IconGridView` đang hiển thị → `invalidateGlass()` (chỉ chạm ô có kính); gỡ ở `onDetachedFromRecyclerView`. `IconGridView.onAttachedToWindow/onDetachedFromWindow` quản lý việc tìm `backdrop_image` (`rootView.findViewById(R.id.backdrop_image)`) và listener `GlassEnvironment`.
- [ ] **Step 5: Build** `gradlew.bat assembleDebug` xanh; không còn cảnh báo cấp phát trong `draw`.
- [ ] **Step 6: Kịch bản kiểm + ĐO (Live 4, bắt buộc; điều phối cài APK và chạy khối lệnh dưới, agent không cài).** Phiên đo này gộp **Task 1 Step 6 và phần Live 4 của Task 5** (cùng một khối lệnh, cùng một lần cài). Ghi `adb shell wm size` và `adb shell dumpsys display | findstr /i fps` vào báo cáo.

  **Chuẩn bị Home trên Live 4 (điều phối dựng, chủ dự án đã đồng ý):** **mỗi trong hai trang đầu có 6 thư mục** (tổng 12; hai app mỗi thư mục là đủ; Home có thể còn các trang sau, Live 4 thường 3–4 trang; điền số trang thật vào `$Pages`), **để tên mặc định "Thư mục"** (khối lệnh tìm ô thư mục bằng chữ này), ảnh nền nhiều chi tiết, đang ở trang 1, không edit mode, không panel; Library có đủ nhóm (máy có 85 app là đủ); máy cắm sạc, độ sáng cố định. Khối lệnh **tự tìm toạ độ ô thư mục** bằng `uiautomator dump` (ô thư mục có `content-desc` = tên thư mục); không tìm thấy thì dùng `$folderX/$folderY` tự điền.

  **Vì sao đổi cách chấm (điều phối đo 2026-11-03):** khi vuốt trang, "trước" đã là Janky 72.87% (nguyên nhân gốc đã tìm ra và đã sửa ở TL1 Task 2b: kính thật của dock tự `invalidate()` mỗi khung cuộn; với `FORCE_HIGH` cũ). Sau TL1 2b số "trước" sẽ khác hẳn, **nhưng quy tắc chấm giữ nguyên theo D10 (vuốt: Δp50, Δp95 thời gian khung; Janky chỉ tham khảo)** vì Janky vẫn dễ bão hoà khi vuốt. **Mức kính của phiên đo (điều phối chốt):** phiên đo D10 chạy với Live 4 **tạm đặt ở `Tự động`** (tier thật của máy → dock là `StaticBlurSurface`), rồi khôi phục `Ép cao` của chủ dự án sau phiên. Ghi `glass_level_override` lúc đo vào báo cáo. Muốn đo thêm `Ép cao` thì chạy lần hai với thư mục `$out` khác.

  **(a) Khối lệnh PowerShell duy nhất** (dán nguyên khối). Đổi `$Part` để chọn phần: `'T1'` = chỉ Task 1 Step 6 (cấu hình `off`/`k3`/`k6` × S2, S3i, S3s); `'T5'` = chỉ phần Live 4 của Task 5 (cấu hình `off`/`all` × S1–S6 + S3i, S3s); `'BOTH'` (mặc định) = cả hai, không chạy trùng; `'O2'` = pha O2 của Task 7 (`lite` / `real` / `lite2`); `'OPPO'` = phần Oppo của Task 5 (cấu hình `off` / `all` với K = 24 / `off2` lặp "trước" ở cuối phiên). Mỗi cấu hình × kịch bản chạy `$runs` = 3 lần. Tệp trong thư mục `gl-measure` trên Desktop: `<cfg>-<kịch bản>-<lần>.txt` (nguyên bản `dumpsys gfxinfo`), `...-framestats.txt` (các lần lấy `framestats`), `summary.csv`, `folders.txt` (toạ độ ô thư mục tìm được) và `gl-ui.xml` (lần dump cuối). `framestats` chỉ giữ ~120 khung nên khối lệnh lấy mẫu sau MỖI thao tác; khử trùng theo `IntendedVsync`; khung `Flags` ≠ 0 bị loại. Kịch bản: **S1** mở/đóng panel thư mục 20 lần; **S2** vuốt qua lại trang 1–2, 20 vòng; **S3i** edit mode đứng yên 20 giây (rung; vào edit mode bằng `Enter-Edit` trên ô app `$appX/$appY`); **S3s** edit mode + vuốt 20 vòng; **S4** mở/đóng Library 15 lần rồi cuộn lưới ô nhóm 15 lần; **S5** mở/đóng panel nhóm 15 lần; **S6** mở/đóng lớp tìm kiếm 10 lần (vuốt xuống ở giữa màn hình; Back hai lần: lần đầu có thể chỉ hạ bàn phím).

  ```powershell
  $pkg = 'gambi.global.os26launcher'
  $Part = 'BOTH'; $runs = 3; $ManualFolder = $false; $Pages = 2   # $Pages = số trang Home THẬT hiện có (Library chỉ mở được từ trang thật cuối)
  $folderTitle = 'Thư mục'; $folderX = 270; $folderY = 600   # $folderX/Y chỉ dùng khi $ManualFolder = $true
  $appX = 144; $appY = 774   # tâm một ô APP thường (không phải thư mục) ở trang 1: dùng để vào edit mode (xem Enter-Edit)
  $out = "$env:USERPROFILE\Desktop\gl-measure"   # đổi thư mục đầu ra ở đây nếu cần
  New-Item -ItemType Directory -Force $out | Out-Null
  $wh  = ((adb shell wm size) -replace '.*:\s*','').Trim().Split('x') | ForEach-Object { [int]$_ }
  $W = $wh[0]; $H = $wh[1]
  $y = [int]($H*0.45); $xa = [int]($W*0.85); $xb = [int]($W*0.15)
  # ---- cấu hình: tên, nội dung files/gl_mask (mặt nạ K), các kịch bản. Mặt nạ: bit0 ô thư mục, 1 panel thư mục, 2 ô nhóm,
  # ---- 3 panel nhóm, 4 ô nhập tìm kiếm, 5 thanh Library, 6 pill (127 = tất cả). K = số ô thư mục có kính mỗi trang.
  $all  = 'S1','S2','S3i','S3s','S4','S5','S6'; $swp = 'S2','S3i','S3s'
  $plan = switch ($Part) {
    'T1'   { @(@{n='off';m='0 0';s=$swp}, @{n='k3';m='1 3';s=$swp}, @{n='k6';m='1 6';s=$swp}) }
    'T5'   { @(@{n='off';m='0 0';s=$all}, @{n='all';m='127 6';s=$all}) }
    'O2'   { @(@{n='lite';m='127 24 0 1';s=@('S1','S5','S2','S3i','S6')}, @{n='real';m='127 24 0 0';s=@('S1','S5','S2','S3i','S6')}, @{n='lite2';m='127 24 0 1';s=@('S1','S5','S2')}) }   # Task 7: kính thật so với kính mờ tĩnh của hai panel (trường thứ tư = forceLite), lặp lite ở cuối
    'OPPO' { @(@{n='off';m='0 0';s=$all}, @{n='all';m='127 24';s=$all}, @{n='off2';m='0 0';s=($swp + 'S1')}) }   # LENS: K = 24 (chưa giới hạn), lặp "trước" ở cuối phiên
    default{ @(@{n='off';m='0 0';s=$all}, @{n='k3';m='1 3';s=$swp}, @{n='k6';m='1 6';s=$swp}, @{n='all';m='127 6';s=$all}) }
  }
  $csv = "$out\summary.csv"
  if (-not (Test-Path $csv)) { 'cfg,scenario,run,total,janky,janky_pct,gfx_p50,gfx_p90,gfx_p95,gfx_p99,missed_vsync,slow_ui,slow_issue,fs_frames,fs_total_p50,fs_total_p95,fs_rec_p50,fs_rec_p95' | Out-File $csv -Encoding ascii }
  $script:fs = @()
  function Start-Cfg($c) {
    if ($c.m) { adb shell "run-as $pkg sh -c 'echo $($c.m) > files/gl_mask'" | Out-Null }
    adb shell am force-stop $pkg | Out-Null
    adb shell am start -n "$pkg/.ui.home.HomeActivity" | Out-Null
    Start-Sleep 12   # Oppo (344 app) khởi động nguội mất ~10 giây
  }
  function Dump-UI { adb shell uiautomator dump /sdcard/gl-ui.xml | Out-Null; adb pull /sdcard/gl-ui.xml "$out\gl-ui.xml" | Out-Null; [xml](Get-Content "$out\gl-ui.xml" -Encoding UTF8 -Raw) }
  function Center($n) { $m = [regex]::Match($n.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); @([int](([int]$m.Groups[1].Value+[int]$m.Groups[3].Value)/2), [int](([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2)) }
  function Grab-FS { $script:fs += ((adb shell dumpsys gfxinfo $pkg framestats) | Out-String) }
  function Tap($x,$y) { adb shell input tap $x $y | Out-Null }
  # Vào edit mode: `input swipe x y x y 900` KHÔNG được launcher nhận là nhấn giữ (điều phối kiểm trên Live 4). Dùng một lần kéo-thả
  # tại chỗ trên ô app thường: DOWN, giữ 1 giây, kéo nhẹ, về chỗ cũ, UP; kết quả: edit mode bật, không đổi bố cục. Cần Android 10+.
  function Enter-Edit {
    adb shell input motionevent DOWN $appX $appY | Out-Null; Start-Sleep -Milliseconds 1000
    adb shell input motionevent MOVE ($appX+36) ($appY-34) | Out-Null
    adb shell input motionevent MOVE ($appX+6) ($appY-4) | Out-Null
    adb shell input motionevent MOVE $appX $appY | Out-Null; Start-Sleep -Milliseconds 300
    adb shell input motionevent UP $appX $appY | Out-Null; Start-Sleep 1
  }
  function Back { adb shell input keyevent 4 | Out-Null }
  function Swipe($x1,$y1,$x2,$y2,$ms) { adb shell input swipe $x1 $y1 $x2 $y2 $ms | Out-Null }
  function Swipes($n) { 1..$n | ForEach-Object {
    Swipe $xa $y $xb $y 250; Start-Sleep -Milliseconds 600; Grab-FS; Start-Sleep -Milliseconds 100
    Swipe $xb $y $xa $y 250; Start-Sleep -Milliseconds 600; Grab-FS; Start-Sleep -Milliseconds 100 } }
  function Pct($a,$p) { if ($a.Count -eq 0) { return '' }; $s = $a | Sort-Object; [math]::Round($s[[int][math]::Floor($p*($s.Count-1))],2) }
  function FS-Stats {
    $seen = @{}; $tot = @(); $rec = @()
    foreach ($blob in $script:fs) {
      $cols = $null
      foreach ($line in ($blob -split "`n")) {
        $l = $line.Trim()
        if ($l.StartsWith('Flags,')) { $cols = $l.Split(','); continue }
        if ($cols -eq $null -or $l -notmatch '^\d+,') { continue }
        $v = $l.Split(',')
        if ([int64]$v[[array]::IndexOf($cols,'Flags')] -ne 0) { continue }
        $iv = [int64]$v[[array]::IndexOf($cols,'IntendedVsync')]; if ($seen.ContainsKey($iv)) { continue }; $seen[$iv] = 1
        $done = [int64]$v[[array]::IndexOf($cols,'FrameCompleted')]
        $ds = [int64]$v[[array]::IndexOf($cols,'DrawStart')]; $sq = [int64]$v[[array]::IndexOf($cols,'SyncQueued')]
        $tot += ($done-$iv)/1e6; $rec += ($sq-$ds)/1e6
      }
    }
    @($tot.Count,(Pct $tot 0.5),(Pct $tot 0.95),(Pct $rec 0.5),(Pct $rec 0.95))
  }
  function Save-Stats($cfg,$sc,$run) {
    $t = (adb shell dumpsys gfxinfo $pkg) | Out-String
    $t | Out-File "$out\$cfg-$sc-$run.txt" -Encoding ascii
    ($script:fs -join "`n=====`n") | Out-File "$out\$cfg-$sc-$run-framestats.txt" -Encoding ascii
    function G($p) { $m = [regex]::Match($t,$p); if ($m.Success) { $m.Groups[1].Value } else { '' } }
    $j = [regex]::Match($t,'Janky frames: (\d+) \(([\d.]+)%\)')
    $s = FS-Stats
    ($cfg,$sc,$run,(G 'Total frames rendered: (\d+)'),$j.Groups[1].Value,$j.Groups[2].Value,(G '50th percentile: (\d+)ms'),(G '90th percentile: (\d+)ms'),(G '95th percentile: (\d+)ms'),(G '99th percentile: (\d+)ms'),(G 'Number Missed Vsync: (\d+)'),(G 'Number Slow UI thread: (\d+)'),(G 'Number Slow issue draw commands: (\d+)'),$s[0],$s[1],$s[2],$s[3],$s[4]) -join ',' | Out-File $csv -Append -Encoding ascii
    $script:fs = @()
  }
  function Begin-Measure { adb shell dumpsys gfxinfo $pkg reset | Out-Null; $script:fs = @() }
  function To-LastPage { 1..($Pages-1) | ForEach-Object { Swipe $xa $y $xb $y 250; Start-Sleep -Milliseconds 1000 } }   # từ trang 1 sang trang thật cuối
  function Open-Library { Swipe $xa $y $xb $y 250; Start-Sleep -Milliseconds 1200 }   # đang ở trang thật cuối -> mở Library
  function Run-Scenario($sc,$cfg,$run) {
    Start-Cfg $cfg
    switch ($sc) {
      'S1'  { Begin-Measure; 1..20 | ForEach-Object { Tap $fx $fy; Start-Sleep -Milliseconds 700; Grab-FS; Back; Start-Sleep -Milliseconds 600; Grab-FS } }
      'S2'  { Begin-Measure; Swipes 20 }
      'S3i' { Enter-Edit; Start-Sleep 1; Begin-Measure; 1..20 | ForEach-Object { Start-Sleep -Milliseconds 950; Grab-FS } }
      'S3s' { Enter-Edit; Start-Sleep 1; Begin-Measure; Swipes 20 }
      'S4'  { To-LastPage; Begin-Measure
              1..15 | ForEach-Object { Open-Library; Grab-FS; Back; Start-Sleep -Milliseconds 700; Grab-FS }
              Open-Library
              1..15 | ForEach-Object { Swipe ([int]($W*0.5)) ([int]($H*0.75)) ([int]($W*0.5)) ([int]($H*0.35)) 250; Start-Sleep -Milliseconds 400; Grab-FS
                                        Swipe ([int]($W*0.5)) ([int]($H*0.35)) ([int]($W*0.5)) ([int]($H*0.75)) 250; Start-Sleep -Milliseconds 400; Grab-FS } }
      'S5'  { To-LastPage; Open-Library
              $lab = (Dump-UI).SelectNodes('//node') | Where-Object { $_.'resource-id' -like '*category_label' } | Select-Object -First 1
              if ($lab) { $c = Center $lab } else { $c = @([int]($W*0.25),[int]($H*0.30)) }
              Begin-Measure
              1..15 | ForEach-Object { Tap $c[0] $c[1]; Start-Sleep -Milliseconds 800; Grab-FS; Back; Start-Sleep -Milliseconds 600; Grab-FS } }
      'S6'  { Begin-Measure
              1..10 | ForEach-Object { Swipe ([int]($W*0.5)) ([int]($H*0.45)) ([int]($W*0.5)) ([int]($H*0.75)) 300; Start-Sleep -Milliseconds 900; Grab-FS
                                       Back; Start-Sleep -Milliseconds 400; Back; Start-Sleep -Milliseconds 500; Grab-FS } }
    }
    Save-Stats $cfg.n $sc $run
  }
  # ---- tìm ô thư mục ở trang 1 (một lần, trước khi vào cấu hình đầu tiên)
  Start-Cfg @{n='find';m=$null}
  $fn = $null
  if ($ManualFolder) { $fx = $folderX; $fy = $folderY; $fn = $true } else {
    foreach ($try in 1..5) {
      $fn = (Dump-UI).SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $folderTitle } | Select-Object -First 1
      if ($fn) { break }; Start-Sleep 3
    }
    if (-not $fn) { throw "Khong thay o thu muc '$folderTitle' o trang 1 sau 5 lan thu: kiem tra bo cuc, hoac dat `$ManualFolder = `$true va `$folderX/`$folderY" }
    $p = Center $fn; $fx = $p[0]; $fy = $p[1]
  }
  "folder centre = $fx,$fy (found=$([bool]$fn))" | Out-File "$out\folders.txt" -Encoding ascii
  Write-Host "folder centre = $fx,$fy (found=$([bool]$fn))"
  # ô app thường để vào edit mode (Enter-Edit): ô clickable có content-desc, không phải thư mục, tâm nằm trong 25%-65% chiều cao
  $ac = (Dump-UI).SelectNodes('//node') | Where-Object { $_.clickable -eq 'true' -and $_.'content-desc' -and $_.'content-desc' -ne $folderTitle } | ForEach-Object { $c2 = Center $_; [pscustomobject]@{x=$c2[0]; y=$c2[1]} } | Where-Object { $_.y -gt $H*0.25 -and $_.y -lt $H*0.65 } | Select-Object -First 1
  if ($ac) { $appX = $ac.x; $appY = $ac.y }
  "app cell = $appX,$appY (auto=$([bool]$ac))" | Out-File "$out\folders.txt" -Append -Encoding ascii
  foreach ($cfg in $plan) { foreach ($sc in $cfg.s) { foreach ($run in 1..$runs) { Run-Scenario $sc $cfg $run } } }
  adb shell "run-as $pkg sh -c 'rm -f files/gl_mask'" | Out-Null; adb shell am force-stop $pkg | Out-Null
  Write-Host "Done. Send $csv and folder $out"
  ```
  Ghi chú: `0 0` làm mọi phần tử về phẳng (= "trước"); đo cùng một APK nhánh GL cho mọi cấu hình. Cột `fs_*` là số liệu chính của kịch bản có vuốt (S2, S3s) và là số tham khảo bổ sung cho còn lại; `fs_rec_*` là giai đoạn `DrawStart→SyncQueued`; cột `gfx_*`/`janky*` là số của `dumpsys gfxinfo` (chính cho S1, S3i, S4–S6). Nếu `fs_frames` thấp hơn nhiều so với `total` thì lần lấy mẫu bị hụt khung (khung > 120 giữa hai lần lấy): báo agent để rút ngắn khoảng lấy mẫu. **Chưa chạy thử trên máy** (agent không có máy): nếu một kịch bản không điều hướng đúng (không mở được Library/panel), gửi lại `gl-ui.xml` và dòng lỗi để agent sửa toạ độ. Ở S4–S5 Library chỉ mở được khi đang ở **trang thật cuối**: khối lệnh vuốt `$Pages − 1` lần từ trang 1 để tới đó, nên `$Pages` phải khớp số trang thật (S2/S3 vẫn chỉ vuốt qua lại giữa trang 1 và 2).

  **(b) Tính hợp lệ của "trước":** cài APK `main` (build ở thư mục chính), chạy lại cùng khối lệnh chỉ với `$plan = @(@{n='main';m=$null;s=@('S2')})` (bỏ phần `Start-Cfg`/`gl_mask` của `m=$null` tự được qua), so với `off-S2`; chênh p50 trong nhiễu (≈ ±1 ms) thì "trước" = `off`. Nếu chênh lớn: dừng, báo agent.
  **(c) Bằng mắt (không cần khối lệnh):** kính ô thư mục bám nền khi vuốt chậm (không "trôi theo ô"); khi rung vẫn bám; kéo một thư mục: ảnh bay theo tay có kính; đổi ảnh nền ở Cài đặt rồi về Home: ô đổi theo; xoá ảnh nền (`rm files/backdrop_image` theo AGENTS.md): ô về phẳng, không crash; đổi Cài đặt → Mức kính → Ép thấp / Ép cao: ô vẫn kính, không crash; panel thư mục và panel nhóm hiện kính, chữ đọc được; ô nhóm Library bám nền khi Library trượt vào và khi cuộn.
  **(d) Đối chiếu ngưỡng D10 (cập nhật 2026-11-03).** Mọi so sánh với `off` **trong cùng một phiên đo**. **S2 và S3s (có vuốt):** qua nếu Δp50 thời gian khung ≤ +1 ms **và** Δp95 ≤ +4 ms; báo thêm Δ `DrawStart→SyncQueued` (p50, p95) và Janky để tham khảo. **S1, S3i, S4, S5, S6 (không vuốt trang):** qua nếu Janky tăng ≤ 2.0 điểm % và Δp95 ≤ +4 ms. Ghi **kết luận Task 1** (cấu hình `k3`/`k6` so với `off`, S2/S3i/S3s): giữ D (K = 6, 12 ô hiển thị) / giới hạn K = 3 / lùi về A cho ô thư mục. Kết luận Task 5 (cấu hình `all` so với `off`) quyết bảng `GlassPolicy`. Nếu `all` không đạt thì chạy thêm các cấu hình cô lập (`panels` mặt nạ `10`, `lib` mặt nạ `36`, `search` mặt nạ `80`) bằng cách sửa `$plan`. **Dừng, chờ chủ dự án xác nhận kết luận.** Task 2–4 đã code với giả định phương án D đạt; nếu kết luận buộc hạ phần tử nào về FLAT thì sửa ở Task 6.
- [ ] **Step 7: Commit** `feat: add in-place glass drawable and glass folder cells` (một hay hai commit, theo Step 1–4 / Step 5).

---

### Task 2: Panel thư mục và panel nhóm ✅ qua review điều phối 2026-11-03 (hình ảnh trên máy gộp vào bài kiểm cuối)

**Bắt buộc kiểm tay:** không (mặc định). Máy: Live 4.
**Files:** Modify `ui/home/view/FolderPanelView.kt`, `ui/home/view/CategoryGroupPanelView.kt`, `res/values/colors.xml` (`gl_panel_*`).
**Interfaces:** Consumes `GlassEnvironment`, `BackdropBlurDrawable`, `GlassRole.FOLDER_PANEL/GROUP_PANEL`. Produces: không giao diện mới.

- [x] **Step 1: Hai panel** — khung `panel` đổi `background` từ `bg_folder_panel` sang `BackdropBlurDrawable` (tint tối `gl_panel_tint`, bo `folder_panel_radius`) khi `modeFor(...) != FLAT`; `FLAT` giữ `bg_folder_panel`. Lắng nghe `GlassEnvironment` để đổi qua lại khi tier đổi (gỡ listener ở `onDetachedFromWindow`). Panel có `alpha` fade 150 ms (`FolderPanelView`), kiểm kính không chớp.
- [x] **Step 2: Kịch bản kiểm (Live 4)** — (a) mở panel thư mục: khung hiện kính mờ thấy ảnh nền, tiêu đề và nhãn đọc được trên ảnh sáng và ảnh tối; (b) đổi tên (bàn phím hiện): khung không đổi; (c) kéo icon ra khỏi panel: panel đóng đúng như G2a; (d) panel nhóm trong Library: khung ôm nội dung, cuộn danh sách dài không làm vùng cắt lệch (khung đứng yên); (e) `FORCE_LOW` và bỏ ảnh nền: `FLAT` y hệt cũ; (f) đổi sáng/tối khi panel mở: panel còn, kính vẫn có (nợ kiểm tay G2a).
- [x] **Step 3: Commit** `feat: render folder and group panels with static glass`.

---

### Task 3: Thanh tìm kiếm (M8b, Library) và pill (M8c) ✅ qua review điều phối 2026-11-03 (hình ảnh trên máy gộp vào bài kiểm cuối)

**Bắt buộc kiểm tay:** không. Máy: Live 4.
**Files:** Modify `ui/search/SearchOverlayView.kt`, `res/layout/view_search_overlay.xml` (đặt `id` cho `LinearLayout` khung ô nhập), `ui/home/view/CategoryPageView.kt` + `res/layout/view_category_page.xml` (thanh `category_search`), `ui/home/view/SearchPillView.kt`.
**Interfaces:** Consumes như Task 2 + `GlassRole.SEARCH_BAR/LIBRARY_SEARCH_BAR/SEARCH_PILL`.

- [x] **Step 1: Hai thanh** — `background` của khung ô nhập (`bg_search_field`) và `category_search` (`bg_category_search`) thành `BackdropBlurDrawable` (tint tối, bo 24dp) khi không `FLAT`. Thanh Library nằm trong lớp trượt: kính phải bám khi lớp trượt, xem Task 4 về `setTranslationX`.
- [x] **Step 2: Pill** — thay `canvas.drawRoundRect(... pillPaint)` trong `SearchPillView.onDraw` bằng `glass.setBounds(pill rect); glass.draw(canvas)` khi không `FLAT`. Chế độ chấm ↔ ô vẫn chỉ là `invalidate()` (không `requestLayout`, đúng D5/M8c). Lớp nhấn `pressPaint` và chữ/chấm vẽ sau như cũ.
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) pill đứng yên là kính, chữ "Tìm kiếm" rõ; (b) vuốt trang: pill co thành chấm, vẫn kính, không giật; (c) mở lớp tìm kiếm: ô nhập là kính, gõ tiếng Việt có dấu bình thường, nút xóa hiện/ẩn đúng; (d) mở Library: thanh tìm kiếm Library là kính; (e) cỡ chữ hệ thống lớn: chữ không bị cắt vì thay nền; (f) `FLAT` y hệt cũ.
- [x] **Step 4: Commit** `feat: render search bars and pill with static glass`.

---

### Task 4: Ô nhóm App Library ✅ qua review điều phối 2026-11-03 (hình ảnh trên máy gộp vào bài kiểm cuối; đo thiết kế ở Task 5 S4)

**Bắt buộc kiểm tay:** không. Máy: Live 4.
**Files:** Modify `ui/home/view/CategoryPageView.kt`, `res/layout/item_category_group.xml` (nếu cần id), `res/values/colors.xml`.
**Interfaces:** Consumes `GlassRole.GROUP_TILE`, `GlassBudget.maxLiteGlassTiles(tier)`.

- [x] **Step 1: Ô nhóm** — `category_tile` (GridLayout) đổi `background` sang `BackdropBlurDrawable` khi không `FLAT` (tối đa `maxLiteGlassTiles`, ô vượt FLAT theo thứ tự thêm).
- [x] **Step 2: Bám nền khi trượt/cuộn** — `ScrollView` đặt scroll listener (`setOnScrollChangeListener`) → `invalidate()` các ô có kính; `CategoryPageView` ghi đè `setTranslationX(x)` gọi `super` rồi `invalidate()` các ô và thanh tìm kiếm (không cần `HomeActivity`/`LibraryOverlayController`); `onSizeChanged` đã dựng lại ô (giữ).
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) vuốt mở Library theo ngón tay: ô nhóm bám nền suốt chuyển động; (b) cuộn dọc lưới ô nhóm: bám; (c) 8 nhóm trở lên (Live 4 đang có đủ nhóm): đủ ô kính, không ô trong suốt; (d) cài/gỡ app khi Library mở: dựng lại ô, kính còn; (e) bấm ô nhóm: panel nhóm hiện đúng (Task 2); (f) đóng Library: không rò (xoay/recreate thử một lần).
- [x] **Step 4: Commit** `feat: render library group tiles with static glass`.

---

### Task 5: Đo Janky trước/sau trên hai máy và chốt chính sách

**Bắt buộc kiểm tay:** có — mục 4.2 (phép đo hiệu năng dùng để chọn phương án). Mọi Task sau (Task 6 chốt hằng số, Task 7 nâng kính thật) dựa vào bảng đo này. Máy: **Live 4 và Oppo A3x** (Oppo chỉ cắm một buổi này).
**Files:** `docs/plans/GL-glass-pass.md` (điền bảng đo ở dưới). Không code.

- [ ] **Step 0 (phần Live 4):** chạy trong **cùng phiên đo với Task 1 Step 6** bằng khối lệnh ở Task 1 Step 6 với `$Part = 'BOTH'` (cấu hình `off` và `all`, kịch bản S1–S6 + S3i/S3s; S3 tách thành S3i và S3s). Phần Oppo chạy riêng khi Oppo được cắm; cùng khối lệnh (đặt `$Pages` theo số trang thật của Oppo).
- [ ] **Step 1: Chuẩn bị dữ liệu giống nhau trên hai máy** — cùng ảnh nền chi tiết; 12 thư mục (2 trang × 6); Library có đủ nhóm; Oppo 344 app. Ghi `adb shell wm size`, tần số quét (`adb shell dumpsys display | findstr /i "fps"`), pin/nhiệt (cắm sạc, độ sáng cố định).
- [ ] **Step 2: Ma trận đo.** "Trước" = mặt nạ 0; "sau" = mặt nạ đủ bảy vai trò; thêm đo **cô lập từng vai trò** nếu "sau" vượt ngưỡng (để biết vai trò nào gây ra). Mỗi ô của bảng đo 3 lần, ghi trung vị. Kịch bản (script `adb shell input` để lặp lại, tọa độ chỉnh theo `wm size`):

  | Mã | Kịch bản (G2a A5 Step 1 + mở rộng) | Cách chạy |
  |---|---|---|
  | S1 | Mở/đóng panel thư mục liên tiếp (20 lần) với 6 thư mục ở trang hiện tại | `input tap` ô thư mục, chờ 600 ms, `input keyevent 4` (Back) |
  | S2 | Lướt Home có ≥ 6 thư mục: 12 thư mục hiển thị cùng lúc khi vuốt giữa hai trang, 30 giây (có cả pill: điều phối/TL1 đo `SearchPillView.onDraw` ≈ 0.3 ms/khung trước khi có kính; kính pill phải nằm trong phép đo này) | vòng `input swipe` như Task 1 |
  | S3 | Edit mode rung 20 giây, 12 thư mục | `input swipe x y x y 800` giữ để vào edit mode, đứng yên |
  | S4 | Mở/đóng Library (20 lần) rồi cuộn lưới ô nhóm 15 giây (ô nhóm Task 4 di chuyển theo `translationX` + `ScrollView`: đo riêng ở đây, đây là phép đo thiết kế của Task 4) | `input swipe` từ trang cuối; cuộn dọc |
  | S5 | Mở/đóng panel nhóm (20 lần) | `input tap` ô nhóm + Back |
  | S6 | Mở/đóng lớp tìm kiếm (10 lần) và pill đứng yên ↔ chấm (vuốt trang) | `input swipe` xuống từ giữa màn hình; `input keyevent 4` |

- [ ] **Step 3: Điền bảng và ghi kết luận.** Mẫu bảng (điền thật, giữ trong plan):

  | Máy | Mã | Cấu hình | Janky % (3 lần) | Trung vị | 50th | 90th | 95th | 99th | Đạt D10? |
  |---|---|---|---|---|---|---|---|---|---|
  | Live 4 | S2 | trước | | | | | | | — |
  | Live 4 | S2 | sau (12 ô) | | | | | | | |
  | … | | | | | | | | | |

  Với mỗi vai trò × tier ra một dòng quyết định: `STATIC_BLUR` / giới hạn `K` ô mỗi trang / `FLAT`. Dán bảng quyết định vào mục Nhật ký. **Dừng, chờ chủ dự án xác nhận bảng và ngưỡng trước khi sang Task 6.**
- [ ] **Step 4: Nếu "sau" vượt ngưỡng**, thử theo thứ tự (mỗi thử đo lại S2/S3): (i) bỏ phần phụ thuộc chi phí đã nhìn thấy (vd. phóng hệ số lọc, bỏ viền), (ii) giảm `maxLiteGlassCellsPerPage`, (iii) hạ ô thư mục về `FLAT` khi panel đang mở, (iv) `FLAT` cả ô thư mục trên tier đó (về phương án A cho ô). Ghi từng thử.

#### Kết quả phiên đo Live 4 (2026-11-03, APK `562e6ce`, mức kính Tự động, 12 thư mục = 6 trang 1 + 6 trang 2, Home 3 trang)

Nguồn: `summary.csv` + tệp `framestats` của điều phối (phiên bị hệ điều hành dừng vì thiếu bộ nhớ sau **48/60 lượt**; thiếu `all` × S3s, S4, S5, S6). Số trung bình 3 lượt; "tổng" là `FrameCompleted − IntendedVsync` từ `framestats`, "rec" là `DrawStart→SyncQueued`.

| Kịch bản | Cấu hình | Janky % | gfx p50/p95 | tổng p50/p95 (ms) | rec p50/p95 (ms) | D10 |
|---|---|---|---|---|---|---|
| S1 mở/đóng panel thư mục | off | 8.91 | 11.0/17.3 | 12.21/18.81 | 0.09/1.89 | — |
| | all | **0.26** | 8.0/12.0 | 9.01/13.31 | 0.07/1.59 | đạt (tốt hơn) |
| S2 vuốt trang | off | 1.14 | 8.0/14.0 | 9.36/16.05 | 0.46/1.05 | — |
| | k3 | 0.99 | 7.0/13.3 | 8.61/15.02 | 0.61/2.27 | đạt |
| | k6 | 1.17 | 8.0/14.0 | 8.96/15.47 | 0.84/2.89 | đạt (Δp50 −0.4, Δp95 −0.6) |
| | all | 1.22 | 8.0/14.0 | 9.08/15.65 | 0.91/3.00 | đạt (Δp50 −0.3, Δp95 −0.4) |
| S3i rung đứng yên | off | 0.28 | 11.0/13.0 | 12.56/14.91 | 0.11/0.14 | — |
| | k3 | 0.47 | 10.0/13.0 | 12.03/14.67 | 0.10/0.14 | đạt (+0.19 điểm) |
| | k6 | **4.66** | 11.3/16.0 | 13.13/17.58 | 0.10/0.14 | **không đạt** (+4.4 điểm, Δp95 +2.7) |
| | all | **8.11** | 12.0/17.0 | 13.73/18.79 | 0.11/0.15 | **không đạt** (+7.8 điểm, Δp95 +3.9) |
| S3s rung + vuốt | off | 1.73 | 8.3/14.0 | 9.90/15.36 | 0.11/0.38 | — |
| | k3 | 1.66 | 9.0/14.0 | 10.05/15.66 | 0.11/1.54 | đạt |
| | k6 | 5.18 | 9.3/16.3 | 10.59/17.66 | 0.11/2.04 | đạt theo quy tắc vuốt (Δp50 +0.69, Δp95 +2.3), Janky +3.5 điểm |
| S4 Library mở + cuộn | off | 2.13 | 5.0/12.3 | 6.06/14.28 | 0.30/1.31 | chỉ có "trước" |
| S5 panel nhóm | off | 34.45 | 13.3/34.0 | 13.29/33.96 | 1.06/2.86 | chỉ có "trước" (xem dưới) |
| S6 lớp tìm kiếm | off | 4.76 | 7.0/16.3 | 8.37/17.45 | 0.08/2.21 | chỉ có "trước" |

**Đọc số (agent, từ `framestats` từng giai đoạn):**
1. **Vuốt (S2) không có vấn đề.** Kính ô thư mục tại chỗ làm giai đoạn ghi display list tăng (rec p50 0.46 → 0.84–0.91 ms, p95 1.05 → 2.9–3.0 ms: đúng chi phí `invalidateGlass()` mỗi khung cuộn), nhưng tổng thời gian khung **không tăng** (p50 −0.3…−0.4 ms, p95 −0.4…−0.6 ms; nhiễu giữa các lượt cỡ ±0.5 ms). 12 ô kính hiển thị cùng lúc khi vuốt giữa hai trang vẫn đạt D10 → phương án D (kính tại chỗ) được xác nhận cho ca vuốt.
2. **Rung đứng yên (S3i) chi phí KHÔNG nằm ở ghi display list và KHÔNG do vẽ lại mỗi khung.** `rec` ở S3i là 0.10–0.11 ms ở cả bốn cấu hình, nghĩa là không có gì `invalidate()` ô trong lúc rung (đúng như lý thuyết: `View.setRotation` chỉ đổi thuộc tính RenderNode; `invalidateGlass()` chỉ gọi khi pager cuộn). Phần tăng nằm ở **luồng RenderThread, giai đoạn `IssueDrawCommandsStart→SwapBuffers`**: off 7.50 ms (trung bình) → k3 7.47 → k6 8.35 → all 8.85; p95 9.13 → 9.57 → 11.12 → 12.01. Tức ~+0.85 ms (k6) và +1.35 ms (all) mỗi khung, đủ đẩy đuôi qua 16.7 ms nên Janky nhảy bậc dù p50 chỉ tăng ~0.6–1.2 ms ("trước" nằm sát ngưỡng: p95 14.9 ms).
3. **Nguyên nhân chi tiết: suy luận chưa kiểm chứng.** Mỗi ô kính vẽ 3 lệnh (`drawRoundRect` với `BitmapShader`, một lớp tint, một viền) mà ô lại đang xoay (±5°) nên các lệnh không gộp được và thêm đổi trạng thái shader. Hiện tượng không tuyến tính: k3 (3 ô) không tốn gì thêm (nằm trong nhiễu), k6 (6 ô) +0.85 ms; chênh lệch `all` − `k6` (+0.5 ms) có thể là kính pill (pill ở chế độ chấm vẫn nằm trong khung hình), chưa tách riêng được. Cần một phép đo cô lập để biết (xem "Đo bổ sung").
4. **S3s** (rung + vuốt): theo quy tắc vuốt k3 và k6 đều đạt (Δp50 ≤ +0.7, Δp95 ≤ +2.3), nhưng Janky k6 +3.5 điểm; cùng nguyên nhân với S3i.
5. **S1: kính tốt hơn nền phẳng rõ rệt** (Janky 8.91% → 0.26%, tổng p50 12.2 → 9.0 ms; giai đoạn `IssueDraw→Swap` 5.3 → 3.8 ms, `anim+traversal` 1.87 → 1.46 ms). Chưa giải thích được; nghi ngờ yếu tố thứ tự (`off` S1 là lượt đo đầu tiên của cả phiên, ngay sau lần `uiautomator dump`) hơn là nền phẳng thật sự đắt hơn. Cần chạy lại `off` S1 muộn hơn trong phiên (xem "Đo bổ sung"). Dù vậy kính **không làm xấu** S1, nên panel thư mục đạt.
6. **S5 không hỏng, chỉ là phép đo rất ngắn.** `gl-ui.xml` cho thấy Library đang mở và `category_label` ("Mạng xã hội") nằm ở [183,774][405,830], tức khối lệnh nhắm đúng ô. Chỉ có ~60 khung vì `CategoryGroupPanelView.render()` hiện/ẩn bằng `visibility` **không hoạt hoạ** (khác panel thư mục có fade 150 ms), nên mỗi lần mở/đóng chỉ tạo 1–2 khung; ~20 khung Janky ≈ một khung dựng lớp `RecyclerView` + nạp icon mỗi lần mở (≈ 15 lần mở), tổng p95 34 ms. Số này là chi phí **mở panel nhóm** ở trạng thái "trước", không phải lỗi script. So sánh "trước/sau" cho S5 vẫn cần lượt `all` (thiếu).
7. **Tier**: tất cả số trên là Live 4 (`CLASSIC_CPU`, dock `StaticBlurSurface`). Chưa có số Oppo.

**Kết luận Task 1 (đề xuất, chờ chủ dự án xác nhận):** giữ phương án **D** (kính tại chỗ) cho ô thư mục, cả 12 ô hiển thị khi vuốt (K = 6 mỗi trang) đạt D10 trên Live 4; **riêng edit mode (rung) cần xử lý**: xem đề xuất Task 6.

**Đo bổ sung (đề xuất, ~25 phút, điều phối/chủ dự án quyết có chạy không):**
`$plan = @(@{n='all';m='127 6';s=@('S3s','S4','S5','S6')}, @{n='pill';m='64 0';s=@('S3i')}, @{n='k6b';m='1 6';s=@('S3i')}, @{n='off2';m='0 0';s=@('S1','S3i')})` (3 lượt mỗi ô).
- `all × S3s, S4, S5, S6`: lấp phần thiếu của ma trận (S4 quyết số `maxLiteGlassTiles` và kính thanh Library; S6 kiểm kính ô nhập tìm kiếm).
- `pill × S3i` (chỉ pill có kính, không ô): tách xem pill có phải thủ phạm của `all − k6` ở S3i không.
- `k6b × S3i`: lặp lại `k6` muộn hơn trong phiên để loại yếu tố thứ tự/nhiệt.
- `off2 × S1, S3i`: lặp lại "trước" muộn hơn trong phiên để kiểm giả thuyết thứ tự ở S1 và độ lặp của S3i.

---

#### Chuẩn bị phiên Oppo A3x (Task 5 phần Oppo, Task 7, Task 8; Oppo chỉ cắm một buổi)

Dùng **cùng khối lệnh** ở Task 1 Step 6 (a), chỉnh `$Part` và `$Pages`; mọi toạ độ là tỉ lệ theo `wm size` hoặc tìm bằng `uiautomator dump` nên không phải sửa tay (ô thư mục và một ô app thường để vào edit mode tự tìm; `folders.txt` ghi kết quả; Oppo có `Enter-Edit` qua `input motionevent`, Android 14 hỗ trợ). Oppo 720x1600, tần số quét ghi vào báo cáo (`dumpsys display | findstr /i fps`; nếu 90 Hz thì ngưỡng Janky của `gfxinfo` tính theo khung ~11 ms, nên so "trước/sau" trong cùng phiên, không so với Live 4).

**Dữ liệu cần dựng trên Oppo (344 app):** 6 thư mục ở mỗi trong hai trang đầu (tên mặc định "Thư mục", hai app mỗi thư mục); ảnh nền nhiều chi tiết; đang ở trang 1; điền `$Pages` = số trang Home **thật** của Oppo (với 344 app có thể vài chục; mỗi lần vào Library `To-LastPage` vuốt `$Pages − 1` lần nên mỗi lượt S4/S5 sẽ lâu hơn; nếu Oppo nhiều trang, rút gọn bằng cách để Home 3-4 trang bằng cách ẩn bớt hoặc chấp nhận thời gian). Mức kính: **Tự động** (tier LENS thật của Oppo); ghi `glass_level_override` lúc đo.

| Pha | Khi nào | Mục tiêu | Cấu hình / kịch bản | Qua khi |
|---|---|---|---|---|
| O1 | Ngay (build hiện tại, panel còn kính mờ tĩnh) | Task 5 phần Oppo: chốt `GlassBudget` LENS (hiện 24 ô/trang, chưa đo) | `$Part = 'OPPO'`: `off` và `all` (127 / K = 24) × S1-S6, S3i, S3s; `off2` lặp "trước" S1-S3 ở cuối | D10 Oppo: Janky +1.0 điểm; nếu "trước" bão hoà khi vuốt thì Δp50 ≤ +1 ms và Δp95 ≤ +4 ms (báo điều phối); S4-S6 và S1 theo Janky |
| O2 | Sau khi Task 7 được code và review | Hai panel kính thật trên LENS so với kính mờ tĩnh; dock sau `collectFrameStats = false` | Task 7 thêm trường thứ tư của `gl_mask` (`forceLite` = 1 ép panel dùng kính mờ tĩnh): cấu hình `lite` (`127 24 0 1`) và `real` (`127 24 0 0`), kịch bản S1, S5 (mở/đóng panel), S2 và S3i (đo dock), S6; lặp `lite` ở cuối | S1/S5: `real` so với `lite` theo Janky +1.0 điểm Oppo; dock S2/S3i không tệ hơn; ghi p50/p95 |
| O3 | Sau Task 8 | Phủ `CLASSIC_GPU` (D5): `debugApiLevelCap = 32` | Trường thứ ba của `gl_mask` (`cap`) = 32: cấu hình `cap32` (`127 24 32`), chạy S1, S2, S3i, S4; kiểm bằng mắt panel/ô/thanh/pill | Không crash, đúng chế độ; số Janky chỉ làm tham khảo (Oppo mạnh hơn máy Android 12 thật) |

**Kiểm bằng mắt kèm theo (Oppo):** panel kính thật khúc xạ ở mép, chữ đọc được trên ảnh sáng/tối, fade 150 ms không chớp/đen; bàn phím sửa tên không làm kính đổi; đổi ảnh nền rồi mở panel thì kính hiện ảnh mới; `FORCE_LOW` đưa panel về kính mờ tĩnh; dock không chớp khi vuốt (cờ chặn `invalidate()` của TL1); ô thư mục/ô nhóm/thanh/pill kính mờ tĩnh trông thống nhất với dock thật bên cạnh (có chấp nhận được không là câu hỏi cho chủ dự án).


#### Kết quả pha O1 (Oppo A3x PKD130, 2026-11-03)

Máy: API 34, 720x1604, density 320, `font_scale` 0.9, mức kính Tự động = tier LENS (không có tệp cài đặt), 13 trang Home, 12 thư mục (6 trang 1 + 6 trang 2), build `30f662f`, 3 lượt mỗi ô. Dữ liệu thô: `summary.csv` + `framestats` của điều phối. Số khung ≈ 2 lần Live 4 (S1 ≈ 740 khung) nên màn hình chạy trên 60 Hz. Nền ổn định (`off` và `off2` ở cuối phiên khớp nhau, S2 `off` 0.79% và `off2` 1.01%) nên **không có trôi máy trên Oppo**.

| Kịch bản | Cấu hình | Janky % | gfx p50 / p95 | tổng p50 / p95 (ms) | rec p50 / p95 (ms) | D10 Oppo (+1.0 điểm) |
|---|---|---|---|---|---|---|
| S1 panel thư mục | off / all | 0.23 / 0.72 | 8.7 / 14.3 → 9.0 / 15.3 | 9.64 / 15.49 → 10.06 / 16.62 | 0.24 / 1.24 → 0.23 / 1.35 | đạt (+0.49) |
| **S2 vuốt** | off | 0.79 | 9.0 / 13.0 | 9.85 / 13.68 | 0.76 / 1.08 | — |
| | off2 (cuối phiên) | 1.01 | 9.0 / 13.0 | 9.89 / 13.65 | 0.76 / 1.06 | — |
| | k6 (mặt nạ 1, K = 6) | **2.73** | 13.0 / 21.0 | 13.75 / 21.35 | **4.78 / 6.02** | **không đạt** (+1.9 điểm; Δp50 +3.9 ms, Δp95 +7.7 ms) |
| | all (127, K = 24) | **2.49** | 12.7 / 20.3 | 13.25 / 20.89 | **4.71 / 5.94** | **không đạt** (+1.7 điểm; Δp50 +3.4, Δp95 +7.2 ms) |
| S3i rung đứng yên | off / all | 0.12 / 0.17 | p50 ~20 / p95 ~27 (cả hai) | 21.47 / 30.62 → 21.82 / 29.23 | 0.74 / 0.91 (cả hai) | đạt (+0.05); p50 ~20 ms ở cả hai cấu hình là đặc tính của "trước" (rung chạy khung dài, Janky vẫn thấp), không phải kính |
| **S3s rung + vuốt** | off / off2 | 0.09 / 0.10 | 11.0 / 14.7 | 11.22 / 15.03 | 0.51 / 0.82 | — |
| | all | **2.45** | 13.0 / 22.0 | 13.66 / 22.84 | 0.41 / **5.09** | **không đạt** (+2.35 điểm) |
| S4 Library | off / all | 1.00 / 0.96 | 10.7 / 14.0 → 11.0 / 14.0 | 11.23 / 14.84 → 11.70 / 14.88 | 0.89 / 1.30 → 1.15 / 3.43 | đạt |
| S5 panel nhóm | off / all | 7.58 / 7.89 | 9 / 34 → 10 / 36 | ~207 khung | 0.60 / 4.38 → 0.59 / 4.46 | đạt (không suy giảm đo được) |
| S6 lớp tìm kiếm | off / all | 5.59 / 5.59 | 10.2 / 20.5 → 11 / 21 | 11.01 / 21.24 → 11.43 / 21.19 | 0.23 / 2.32 → 0.23 / 2.36 | đạt |

**Kết luận O1:** panel, Library, ô nhóm, thanh tìm kiếm, pill **đạt** trên LENS; **ô thư mục trong lúc pager cuộn không đạt** (S2 và S3s). `k6` (chỉ ô thư mục) tái hiện cùng mức tăng của `all`, nên chi phí là **ô thư mục khi pager cuộn**, không phải phần tử khác. Chi phí nằm ở giai đoạn **ghi display list**: `rec` p50 0.76 → 4.7 ms (Live 4: 0.46 → 0.84 ms).

**simpleperf trên Oppo** (mặt nạ 1 6, 6 giây vuốt như S2, `--children`; điều phối):
- `IconCellView.onDraw` 21.4% mẫu, trong đó `IconCellView.drawFolder` 11.1% (vẽ lại 9 icon nhỏ `drawBitmap` + nhãn mỗi lần `invalidate()`) và `BackdropBlurDrawable.draw` 6.7% (`applyBackdropShader` 3.5%: `View.getLocationOnScreen` 1.9%, `BitmapShader.createNativeInstance` 0.6% vì `setLocalMatrix` mỗi khung tạo lại shader gốc).
- `IconGridView.invalidateGlass` 0.8%.
- `LauncherAppsDataSource.loadIconDrawable` 11.4% (trang bind khi vuốt; có từ trước, không do kính).
- **80% mẫu nằm dưới `ExecuteSwitchImplAsm`**: APK debug chạy Java chủ yếu bằng trình thông dịch trên máy này, nên chi phí Java tuyệt đối bị thổi phồng so với bản release. Vì chủ dự án chạy bản `installDebug` hằng ngày, chi phí vẫn **có thật với chủ dự án**; nhưng cần một phép đo gần release trước khi chốt hằng số (xem 6c, bước 0).
- Vì sao Live 4 rẻ hơn: chưa rõ (khác chế độ ART/JIT?) — suy luận chưa kiểm chứng.

**Gốc rễ (đọc từ simpleperf + code):** `invalidateGlass()` làm cả `IconCellView` ghi lại display list mỗi khung cuộn, nghĩa là vẽ lại **9 icon nhỏ + nhãn (`StaticLayout`)** chứ không chỉ lớp kính. Phần nội dung này không đổi khi cuộn.

**Ghi chú script (điều phối, đã áp vào khối lệnh):** trên Oppo khởi động nguội với 344 app mất ~10 giây nên `Start-Cfg` chờ 5 giây làm lần tìm thư mục đầu thất bại và rơi về toạ độ Live 4 (bấm trúng một app). Đã đổi: chờ 12 giây, thử lại tối đa 5 lần khi tìm ô thư mục, và dừng nếu không thấy (`$ManualFolder` để dùng toạ độ tự điền).

### Task 6: Áp chính sách đã đo — `GlassPolicy`, `GlassBudget`, FLAT fallback ✅ qua review điều phối 2026-11-03 (ô thư mục 6/trang CLASSIC, 24 LENS chờ Oppo; ô nhóm 8)

**Bắt buộc kiểm tay:** không. Máy: Live 4.
**Files:** Modify `core/glass/{GlassPolicy,GlassBudget,GlassEnvironment}.kt`, các view đã sửa ở Task 1–4 (chỉ nếu bảng đo đòi hạ vai trò nào đó).
**Interfaces:** Produces bảng cuối cùng của `GlassPolicy.modeFor(tier, role)` và `GlassBudget.maxLiteGlassCellsPerPage(tier)` / `maxLiteGlassTiles(tier)` theo bảng đo.

**Đề xuất Task 6 (từ bảng đo Live 4 ở Task 5; chờ điều phối và chủ dự án duyệt trước khi code):**

Các phương án cho "kính ô thư mục trong edit mode" (tier CLASSIC, đo ở Live 4):

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| P1. Giữ kính cả lúc rung, giới hạn K = 3 ô mỗi trang | `maxLiteGlassCellsPerPage(CLASSIC) = 3` | Đạt D10 ở cả S2, S3i, S3s (S3i +0.19 điểm) | **Một trang có 3 ô kính + 3 ô phẳng cạnh nhau** (ví dụ 6 thư mục) trông không đồng bộ ở mọi lúc, không chỉ lúc rung; bỏ phí 6 ô vuốt vẫn đạt | Không |
| **P2. Kính mọi ô (K = 6 mỗi trang, số đã đo) bình thường; ô thư mục tạm FLAT trong edit mode (đang rung/kéo)** | `IconGridView` coi `editMode` là điều kiện: `glassFolderIds` rỗng khi `editMode` trên tier CLASSIC, rebind riêng ô thư mục khi vào/ra edit mode; LENS để quyết sau số đo Oppo | Loại hẳn chi phí rung (S3i/S3s về như "trước"); ngoài edit mode vẫn đủ 6 ô/trang (S2 đạt với 12 ô cùng lúc); cùng lúc các icon khác đang rung nên người dùng ít để ý ô đổi nền; luật rõ ràng, dễ review | Ô đổi kính ↔ phẳng một lần khi vào/ra edit mode (có thể làm cross-fade ngắn nếu thấy gắt); logic thêm một điều kiện ở `IconGridView` | **Chọn** (mặc định đề xuất) |
| P3. Tối ưu số lệnh vẽ rồi đo lại | Gộp tint vào `ColorFilter` của shader, bỏ viền khi đang xoay (3 lệnh → 1–2), đo lại K = 6 ở S3i | Nếu đạt thì giữ kính cả khi rung (đẹp nhất) | Nguyên nhân chưa được kiểm chứng; có thể không đủ; cần thêm một lượt đo | Thử **trước** P2 nếu điều phối muốn (một Task nhỏ + 10 phút đo); không đạt thì P2 |
| P4. Đổi toàn bộ sang FLAT trong edit mode cho mọi phần tử kính trên Home (ô, pill) | Mặt nạ theo `editMode` | Đơn giản hơn nữa | Pill cũng đổi nền khi vào edit mode; chỉ cần nếu phép đo cô lập cho thấy pill góp phần (`all − k6` ở S3i) | Chỉ khi `pill × S3i` cho thấy pill đắt |

Chính sách đề xuất khi code (Task 6): `GlassBudget.maxLiteGlassCellsPerPage(CLASSIC_CPU/CLASSIC_GPU) = 6` (số đã đo, S2 đạt ở 12 ô cùng lúc); ô thư mục FLAT khi `editMode` trên CLASSIC; LENS giữ chưa giới hạn cho tới khi có số đo Oppo (S2/S3i/S3s với K = 6); `maxLiteGlassTiles` giữ 8 và các vai trò panel/thanh/pill giữ `STATIC_BLUR` (panel đã đạt ở S1; thanh/ô nhóm/pill còn thiếu `all × S4, S6` và `pill × S3i`, hoàn tất trước khi chốt). Chốt cuối cùng cần: (i) quyết P2 hay thử P3 trước, (ii) có chạy "Đo bổ sung" không.

**Quyết định của điều phối (2026-11-03): thử P3 trước, P2 làm phương án lùi.** Task 6 tách thành 6a (P3, nhỏ) rồi 6b (P2 nếu 6a không đạt) rồi chốt hằng số.

#### Task 6a: giảm số lệnh vẽ của mỗi ô kính (P3) ✅ qua review và qua phép đo lại 2026-11-03
**Bắt buộc kiểm tay:** có, vì là phép đo hiệu năng dùng để chọn P3 hay P2 (mục 4.2); điều phối chạy lại S3i và S3s cho `k6` và `all` (Live 4, mức kính Tự động) và so với `off` cùng phiên. **Đạt nếu** `k6` ở S3i có Janky ≤ `off` + 2.0 điểm % (và Δp95 ≤ +4 ms); S3s theo quy tắc vuốt.
**Files:** Modify `core/glass/BackdropBlurDrawable.kt`.
- [x] **Step 1: Tint gộp vào lệnh tô, bỏ viền khi xoay.** Lớp tint (`tintPaint`, một `drawRoundRect` riêng) thay bằng `PorterDuffColorFilter(tint, SRC_OVER)` gắn vào chính `fillPaint` (cùng kết quả: màu tint phủ lên điểm ảnh của shader); `draw()` thoát sau lệnh tô khi `owner.rotation != 0f` nên ô đang rung (`Jiggler`) chỉ còn **một** lệnh vẽ thay vì ba. Ô đứng thẳng (không rung) vẫn có viền. `setColorFilter` từ ngoài không còn tác dụng (bộ lọc dành cho tint).
- [x] **Step 2: Kịch bản kiểm (Live 4)** — (a) Home đứng yên: ô thư mục giống trước về tint/viền (chênh lệch duy nhất có thể thấy: mép bo mượt hơn vì không còn hai lớp AA chồng); (b) vào edit mode: ô rung, viền biến mất khi rung, thoát edit mode viền trở lại; (c) panel, thanh, pill, ô nhóm (không xoay) vẫn có viền và tint như Task 2-4; (d) điều phối đo lại S3i và S3s cho `k6`, `all`, `off` cùng phiên.
- [x] **Step 3: Commit** `perf: cut glass drawing to one op per jiggling cell`.
- **Task 6b (P2: ô thư mục FLAT trong edit mode) BỎ QUA** vì 6a đạt. Chỉ lấy lại nếu Oppo hoặc một máy khác cho thấy chi phí rung trở lại.

**Kết quả đo lại 6a (điều phối, Live 4, mức kính Tự động, build `2f9134a`, cùng một phiên, 3 lượt mỗi ô; dữ liệu nối vào cùng `summary.csv`):**

| Cấu hình | S3i rung đứng yên (Janky %, 3 lượt) | S3s rung + vuốt | Đạt D10? |
|---|---|---|---|
| `off6a` (lượt đo cuối phiên) | **0.49** (0.23 / 0.77 / 0.46), p50 11 / p95 14 | (bản cũ `off`: 1.73, p50 8.3 / p95 14) | — |
| `k6a` (6 ô/trang) | **0.46** (0.54 / 0.39 / 0.46), p50 11 / p95 13 | **2.12** (2.74 / 2.37 / 1.25), p50 8 / p95 14 | đạt (S3i ≈ nền, S3s trong +0.4 điểm) |
| `all6a` (127, K = 6) | **0.52** (0.38 / 0.47 / 0.71), p50 11 / p95 13 | **2.02** (2.53 / 1.58 / 1.94), p50 9 / p95 14.3 | đạt |

Bản cũ (562e6ce, ba lệnh mỗi ô): `k6` S3i 4.66%, `all` S3i 8.11%. Một lệnh vẽ mỗi ô đang rung đã đưa chúng về bằng nền; xác nhận giả thuyết "chi phí rung nằm ở số lệnh vẽ trên RenderThread".

**Lấp chỗ thiếu của phiên trước (bản cũ `562e6ce`, `all` = 127 / K = 6):**
- **S3s** (bản cũ): 4.73 / 5.56 / 4.78% — đã được 6a thay thế (bảng trên).
- **S4 Library mở + cuộn:** 4.53 / 3.05 / 3.16% → trung bình 3.58% so với `off` 2.13% (+1.45 điểm), p95 14 so với 12.3 ms (+1.7). **Đạt D10** (ô nhóm 8 ô + thanh Library).
- **S5 panel nhóm:** 36.7 / 48.3 / 46.8% so với `off` 34.4%, chỉ ~60 khung mỗi lượt nên 1 khung ≈ 1.7 điểm: **không kết luận được** theo Janky (p95 31.7 so với 34.0 ms). Coi là "không thấy suy giảm đo được". Chi phí mở panel nhóm (≈ 1 khung Janky mỗi lần mở: dựng `RecyclerView` + nạp icon) có sẵn từ trước, không do kính.
- **S6 lớp tìm kiếm:** 4.60 / 9.89 / 10.04% → trung bình 8.18% so với `off` 4.76% (**+3.4 điểm, không đạt Janky**), p95 18 so với 16.3 ms (+1.7, nằm trong ngưỡng +4). Lượt 1 bằng nền, lượt 2-3 cao: dấu hiệu nhiễu trạng thái máy (xem cảnh báo bên dưới). Giai đoạn `IssueDraw→Swap` +0.7 ms (2.91 → 3.62), ghi display list không đổi.
- **pill × S3i** (mặt nạ 64): 0.26% (0.16 / 0.39 / 0.23), p50 10 / p95 13 → **pill không tốn gì** thêm.

**Cảnh báo trôi trạng thái máy (bài học cho phép đo):** `off6a` S3i lượt đầu phiên đọc 0.00% (p50 8 / p95 10 ms), một lượt `off6a` sau đó đọc 0.49% (p50 11 / p95 14); `k6b` S3i lượt 1 là 79% (ngoại lai, bản cũ) và lượt 2-3 là 2.24 / 5.26%. Máy trôi theo bộ điều tốc CPU hoặc nhiệt. **Quy tắc:** chỉ so sánh trong cùng một phiên, và chạy lại cấu hình "trước" ở cuối phiên (đã làm: `off6a` lượt cuối). Hệ quả với số trước đó: kết luận S1 "kính tốt hơn phẳng" (8.91% so với 0.26%) **không đáng tin**: `off6a` S1 trong phiên 6a cũng cho 2.87 / 10.44 / 9.90%, tức dao động lớn giữa các lượt cùng một cấu hình, nên coi S1 là "kính không làm xấu hơn", không phải "kính nhanh hơn". Tương tự S6: lượt `off` đo ở đầu phiên cũ, `all` đo sau đó, nên +3.4 điểm có thể là trôi máy.

**Đề xuất S6 (đã chạy, xem kết quả ngay bên dưới):** không đổi code trước khi có phép đo ghép cặp. Chạy trong cùng một phiên, xen kẽ, `$plan = @(@{n='off6';m='0 0';s=@('S6')}, @{n='nofield';m='111 6';s=@('S6')}, @{n='all6';m='127 6';s=@('S6')}, @{n='off6b';m='0 0';s=@('S6')})` (3 lượt mỗi ô; `111` = tất cả trừ bit 4 là ô nhập tìm kiếm M8b). Quy tắc quyết: nếu `all6` so với `off6`/`off6b` đạt D10 thì bỏ qua; nếu không đạt mà `nofield` đạt thì hạ `SEARCH_BAR` về FLAT trên CLASSIC (ô nhập nhỏ, rẻ nhưng nằm trong khung hình đang có hoạt hoạ fade và bàn phím); nếu `nofield` cũng không đạt thì chi phí nằm ở các phần tử Home bên dưới lớp tối (ô thư mục 2 lệnh vẽ mỗi ô khi đứng thẳng) và hướng sửa là hạ chúng về FLAT khi lớp tìm kiếm đang mở (cần cờ "Home bị che", dự kiến qua `GlassEnvironment`), không phải đổi ô nhập.


**Kết quả S6 ghép cặp (điều phối, Live 4, build `78221f8`, mức kính Tự động, cùng một phiên, 3 lượt mỗi ô, xen kẽ):**

| Cấu hình | Janky % (3 lượt) | Trung bình | gfx p50 / p95 | tổng p50 / p95 (ms) |
|---|---|---|---|---|
| `off6` | 13.57 / 14.89 / 18.99 | 15.82 | 10.3 / 20 | 11.9 / 23.3 |
| `nofield` (111) | 15.18 / 20.08 / 18.32 | 17.86 | 11 / 20.3 | 12.3 / 23.5 |
| `all6` (127) | 17.37 / 12.36 / 22.69 | 17.47 | 11 / 20.3 | 12.2 / 23.4 |
| `off6b` | 14.73 / 15.83 / 15.62 | 15.39 | 10.7 / 20 | 12.0 / 21.9 |

Nền = trung bình `off6` và `off6b` = 15.6%. `all6` hơn **+1.87 điểm**, Δp95 ≈ +0.3 ms → **đạt D10**. `nofield` nằm trong nhiễu của `all6` nên ô nhập tìm kiếm **không** phải nguồn chi phí. **Không đổi code cho S6; `SEARCH_BAR` giữ `STATIC_BLUR`.** Cảnh báo trôi máy lặp lại: nền S6 ở phiên này cao gấp ~3 lần phiên đầu (4.76% → 15.6%), máy nóng sau ~2 giờ chạy; vì vậy kết luận "+3.4 điểm" của phiên đầu là nhiễu trạng thái, phương pháp ghép cặp + lặp "trước" ở cuối phiên mới đáng tin.

#### Task 6 (chốt hằng số) — đã code: `GlassBudget.maxLiteGlassCellsPerPage` = 6 trên CLASSIC (đã đo), 24 trên LENS (chưa đo, chờ Oppo), `maxLiteGlassTiles` = 8 (S4 đạt); `GlassPolicy` giữ `STATIC_BLUR` mọi vai trò; không FLAT phần tử nào; SEARCH_BAR chờ S6 ghép cặp

- [x] **Step 1:** Ghi hằng số theo kết luận Task 5; comment cạnh mỗi hằng số trỏ tới số đo (ngày, máy). (Câu về tổng kính **thật** đồng thời trên LENS, dock 1 + panel 1 = 2 ≤ 6, chuyển sang Task 7 vì kính thật của panel mới vào ở đó.)
- [x] **Step 2:** ~~Khi panel mở, ô phía sau tự hạ `FLAT`~~ **Không làm**: Task 5 không cho thấy cần (S1 đạt).
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) đúng bảng quyết định: vai trò nào `FLAT` thì thấy phẳng y như cũ; (b) `FORCE_LOW` ↔ `Tự động` (Cài đặt → Home tạo lại): chuyển đúng; (c) thư mục vượt `K` ô: ô vượt phẳng, ô trong `K` có kính, thứ tự đọc đúng, kéo ô thay đổi thứ tự không làm ô nhấp nháy kính.
- [x] **Step 4: Commit** `feat: apply measured glass policy and budget per tier`.

#### Task 6c: ô thư mục không ghi lại nội dung tĩnh mỗi khung cuộn ✅ qua review và qua phép đo lại trên Oppo 2026-11-03 (Live 4: kiểm lại S2/S3i + ảnh chụp FLAT khi Live 4 nối lại; hình ảnh lúc nghỉ và ảnh kéo: bài kiểm cuối)

**Vấn đề (O1, Oppo):** S2 và S3s không đạt D10; `rec` p50 0.76 → 4.7 ms; `invalidateGlass()` ghi lại cả `IconCellView` gồm 9 icon nhỏ và nhãn mỗi khung cuộn (`drawFolder` 11.1% + phần còn lại của `onDraw` ~10% mẫu, kính chỉ 6.7%).
**Bắt buộc kiểm tay:** có — mục 4.2 (phép đo hiệu năng quyết định giữ kính ô thư mục trên LENS hay hạ K/FLAT). Máy: Oppo (S2, S3s với `k6` và `all`, so với `off` cùng phiên) và Live 4 (S2, S3i để chắc không tệ hơn).
**Files (dự kiến):** Modify `ui/home/view/IconCellView.kt` (bộ đệm nội dung ô thư mục), `core/glass/BackdropBlurDrawable.kt` (đường vẽ gọn hơn). Không đụng `IconGridView`/`HomePagerAdapter`.

**Các phương án:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Bộ đệm bitmap cho nội dung tĩnh của ô thư mục** | Khi kính tại chỗ bật: vẽ 9 icon nhỏ + nhãn **một lần** vào một `Bitmap` ARGB cỡ ô (trong `bindFolder`/`onSizeChanged`, huỷ khi nội dung đổi); `onDraw` = lệnh vẽ kính + **một** `drawBitmap`. Tắt kính (FLAT) thì giữ đường vẽ cũ nên FLAT vẫn y hệt `main` | Ghi lại còn ~2 lệnh/ô thay vì ~12 + `StaticLayout`; giảm cả số lệnh trên RenderThread (rung); mọi API 26+ (không cần `RenderNode`); ảnh chụp lúc kéo (canvas phần mềm) vẫn đúng | Mỗi ô một bitmap (~0.2 MB, 12 ô ≈ 2.4 MB); khi rung (xoay ±) bitmap được lọc nên chữ hơi mềm hơn vẽ trực tiếp (lúc đứng yên vẽ 1:1 nên không đổi); phải huỷ bộ đệm đúng chỗ (đổi icon, tiêu đề, cỡ, `labelScale`) | **Chọn** |
| B. Bộ đệm `RenderNode` (API 29+) | Ghi nội dung tĩnh vào `RenderNode`, `onDraw` gọi `drawRenderNode` | Giữ chữ sắc nét khi xoay; không cấp bitmap | Chỉ API 29+ (minSdk 26: máy Android 8-9 ở tier CLASSIC_CPU vẫn ghi lại mỗi khung); RenderThread vẫn phát lại đủ ~12 lệnh; API ít dùng trong dự án | Không (dự phòng nếu chữ mềm khi rung bị chê) |
| C. Vẽ kính ở `IconGridView.onDraw` (ô không bị invalidate) | Cha vẽ mọi lớp kính trước các con; ô giữ display list | Không ghi lại ô khi cuộn | Phải tự đồng bộ xoay (Jiggler), alpha 0 lúc kéo, dịch chuyển preview; ảnh chụp lúc kéo mất kính — đúng các lý do đã loại phương án view anh em ở "Vì sao chọn thiết kế này" | Không |
| D. Giảm tần suất `invalidate()` (mỗi 2 khung, hoặc khi dịch ≥ 1 texel mờ ≈ 12 px) | Bỏ qua một phần sự kiện cuộn | Giảm chi phí tỉ lệ | Chỉ giảm một phần; lát cắt nền trễ vài khung (thấy được trên ảnh nền tương phản cao) | Chỉ làm phụ nếu A không đủ |
| E. Hạ K trên LENS (3 ô/trang) | `GlassBudget` | Dễ | Một trang 3 ô kính + 3 ô phẳng (đã bị loại vì không đồng bộ) và vẫn có thể không đạt | Không |

**Bổ sung nhỏ (cùng task, từ simpleperf):** đường vẽ của `BackdropBlurDrawable` gọn hơn trên máy thông dịch: giữ **một** `BitmapShader` cố định với ma trận chỉ phụ thuộc cỡ (không `setLocalMatrix` mỗi khung ⇒ không tạo lại shader gốc, `createNativeInstance` 0.6%) và đặt vị trí bằng `canvas.translate` (dịch toạ độ hình để shader lấy mẫu đúng chỗ); nhớ danh tính `Drawable` của ảnh nền để bỏ qua ép kiểu + `cache.small()` khi không đổi. `getLocationOnScreen` (1.9%) giữ nguyên (đúng với mọi cha trượt/xoay).

**Bước 0 (điều phối làm song song, không cần code):** đo gần release để biết phần nào là do trình thông dịch: trên Oppo `adb shell cmd package compile -m speed -f gambi.global.os26launcher` rồi chạy lại S2 `off`/`k6`/`off` (3 lượt, ghép cặp). Lưu ý: bản debuggable biên dịch vẫn mang cờ debuggable. Nếu muốn bản không debuggable thật, agent làm một bản tạm cục bộ (không commit) với `isDebuggable = false` kèm `GlassDebug` đọc thêm `/data/local/tmp/gl_mask` (vì `run-as` không dùng được); chỉ làm nếu điều phối yêu cầu.

**Kỳ vọng (suy luận, chưa đo):** mẫu `onDraw` của ô giảm từ ~21% xuống ~6% ⇒ `rec` p50 trên Oppo từ ~4.7 ms xuống ~1.2-1.5 ms (nền 0.76), có thể đạt D10 (Janky +1.0 điểm) hoặc gần; không hứa trước. **Nếu A + bổ sung vẫn không đạt:** thêm D (giảm tần suất), rồi E hoặc FLAT trong lúc cuộn (ô thư mục FLAT khi pager không IDLE trên LENS) như phương án lùi.

- [x] **Step 0:** (điều phối) phép đo gần release như trên.
- [x] **Step 1:** `IconCellView`: bộ đệm bitmap nội dung ô thư mục (chỉ khi `folderGlass != null`; huỷ khi `bindFolder`/`bind`/`onSizeChanged`/`onDetachedFromWindow`; `snapshotWithoutBadge` và canvas phần mềm dùng đúng bộ đệm hoặc đường cũ).
- [x] **Step 2:** `BackdropBlurDrawable`: shader cố định + `canvas.translate`, nhớ danh tính drawable ảnh nền.
- [x] **Step 3: Kịch bản kiểm** (Oppo xong; Live 4 và bằng mắt còn lại) — Live 4: ô thư mục trông giống trước ở trạng thái nghỉ (tint, viền, icon nhỏ, nhãn), không đổi khi vào/ra edit mode, ảnh bay lúc kéo thư mục có kính + nội dung, đổi icon (cài/gỡ app trong thư mục) làm mới ô, thư mục 1-9 app; Oppo + Live 4: điều phối đo lại S2 và S3s `k6`/`all`/`off`/`off2`; kiểm FLAT vẫn giống hệt `main` (ảnh chụp).
- [x] **Step 4: Commit** `perf: cache folder cell content so glass redraws stay cheap`.

---

**Đã code (commit `bf987b1`):** `IconCellView`: khi `folderGlass != null`, nội dung tĩnh (9 icon nhỏ + nhãn) vẽ một lần vào bitmap ARGB_8888 **cỡ view** (cỡ view, không phải cỡ nội dung, để không cắt bóng chữ), bằng chính hai hàm vẽ trực tiếp (`drawFolderIcons`, `drawLabel`); `onDraw` = kính (`drawFolderBackground`) + một `drawBitmap`. Đường không kính giữ nguyên thứ tự lệnh cũ (nền phẳng → icon nhỏ → icon app → dấu → nhãn), nên FLAT vẫn y hệt `main`. **Huỷ bộ đệm (đặt null, không `recycle()` vì display list đang chờ trên RenderThread có thể còn giữ bitmap):** `bind` và `bindFolder` (đổi dữ liệu, icon nạp xong gọi lại `bindFolder`, nhãn, `labelScale`, `Cells`), `onSizeChanged`, `onDetachedFromWindow`, và khi tắt kính (`bindFolder` với `glass = null`). Dark/light và `recreate()` tạo view mới nên không còn bộ đệm cũ. Không bao giờ vẽ icon cũ vì mọi thay đổi nội dung đều đi qua `bind*`. Bộ nhớ: bề rộng x cao ô x 4 byte, khoảng 0.2-0.3 MB mỗi ô thư mục có kính (12 ô ≈ 2.4-3.6 MB). Ảnh chụp lúc kéo (canvas phần mềm) dùng cùng bộ đệm. Chưa kiểm trên máy.
`BackdropBlurDrawable`: `BitmapShader` cố định (ma trận chỉ đặt khi ảnh nền hoặc cỡ view ảnh nền đổi), vị trí đặt bằng `canvas.translate(-t)` + hình chữ nhật dịch `+t` (`t` = vị trí owner trừ vị trí ảnh nền trên màn hình), nhớ danh tính `Drawable` của ảnh nền để bỏ ép kiểu và `cache.small()`; `getLocationOnScreen` giữ (hai lần mỗi lần vẽ).

**Kết quả (điều phối, Oppo A3x, tier LENS):**
- **Step 0** (build `30f662f` sau `cmd package compile -m speed -f`), S2: `sp_off` 0.75%, `sp_k6` **2.29%** (`rec` p50 4.57 ms), `sp_off2` 0.90% ⇒ biên dịch trước (AOT) **không** loại bỏ chi phí, tức đó là công việc vẽ thật, không chỉ do trình thông dịch.
- **6c** (build `038368a`, 3 lượt mỗi ô):

| Cấu hình | S2 Janky | S2 gfx p50 / p95 | S2 rec p50 / p95 | S3s Janky | S3s rec p95 |
|---|---|---|---|---|---|
| `c_off` | 1.03% | 9 / 13 | 0.77 / 1.06 | — | — |
| `c_k6` | **1.54%** | 10 / 15.3 | 2.09 / 3.00 | — | — |
| `c_all` (127, K = 24) | **1.53%** | 10 / 15 | 2.11 / 3.02 | **0.43%** | 2.19 |
| `c_off2` | 0.92% | 9 / 12.3 | 0.76 / 1.08 | 0.12% | 0.84 |

Nền ≈ 0.98%. `k6` và `all` hơn +0.55 điểm (quy tắc Oppo ≤ +1.0), Δp50 ≈ +1 ms, Δp95 +2…3 ms ⇒ **đạt**; S3s +0.31 điểm ⇒ **đạt**. `rec` p50 giảm từ 4.7 xuống 2.1 ms: lỗi O1 đã khắc phục. Còn lại cho 6c: kiểm lại trên Live 4 (S2, S3i, ảnh chụp FLAT so với `main`) khi nối lại máy, và hình ảnh lúc nghỉ + ảnh kéo bằng mắt ở bài kiểm cuối.

### Task 7: Hai panel thành kính thật trên tier LENS (xong; ngoại lệ D10 do chủ dự án chấp nhận 2026-11-04)

**Bắt buộc kiểm tay:** không (đưa vào bài kiểm cuối). Máy: **Oppo A3x**.
**Files:** Modify `ui/home/view/FolderPanelView.kt`, `ui/home/view/CategoryGroupPanelView.kt`, `core/glass/{GlassSurface,RealGlassSurface,StaticBlurSurface,GlassPolicy}.kt`.
**Interfaces:** Consumes `RealGlassSurface`, `GlassRole`. Produces `GlassSurface.setTint(argb)` (mặc định no-op; `RealGlassSurface` đặt `glassTint`).

- [x] **Step 0:** `GlassDebug` thêm trường thứ tư `forceLite` (1 = hai panel dùng kính mờ tĩnh dù tier LENS) để đo cạnh nhau `real` / `lite` trong cùng phiên Oppo (pha O2); `GlassBudget` ghi chú tổng kính **thật** đồng thời tối đa trên LENS (dock 1 + một panel = 2 ≤ 6).
- [x] **Step 1:** `GlassPolicy`: `FOLDER_PANEL`, `GROUP_PANEL` → `REAL` khi tier = `LENS`. View: khi `REAL`, thêm `RealGlassSurface.view` làm con **đầu tiên** của panel view (dưới `panel`), `backdropSource` = `backdrop_image`, `enableDynamicBackground = false`, `cornerRadius` = `folder_panel_radius`, `setTint` tối; `onMeasure` đo kính `EXACTLY` theo khung `panel` và `onLayout` đặt kính đúng khung (bẫy M6: `StaticBlurSurface`/ViewGroup cần `measure()`); `panel.background = null`. Mỗi lần panel hiện: `setDynamic(true)` 1 giây rồi `false` (cách `pulseGlassRefresh`) để chụp đúng nền hiện tại (bài học M8a Task 5).
- [x] **Step 1b: Dùng lại API chặn `invalidate()` của TL1 Task 2b.** Mỗi `RealGlassSurface` của panel phải được `setInvalidationSuppressed(true)` khi panel đứng yên mà có thứ khác cuộn (vuốt trang Home, cuộn ScrollView Library, cuộn danh sách panel nhóm), nếu không kính chụp lại nền mỗi khung cuộn như dock (xem sự thật số 7). Quy tắc chặn: panel đang ẩn (GONE/INVISIBLE hoặc alpha 0) thì luôn chặn; panel đang hiện thì chặn trừ lúc vừa mở (xung nhịp `setDynamic(true)` 1 giây). API thật (đã merge, đọc sau rebase): `GlassSurface.setInvalidationSuppressed(Boolean)` (mặc định no-op), `RealGlassSurface` ghi đè bằng `SuppressibleGlassView.setSuppressed`. `DockView` chuyển cờ qua `setGlassInvalidationSuppressed`; `HomeActivity` đặt theo trạng thái cuộn pager. Hai panel không nằm trong `DockView` nên tự giữ `RealGlassSurface` và tự đặt cờ (ẩn → true; hiện → false chỉ trong xung nhịp mở panel); **dùng lại API, không viết lại**.
- [x] **Step 2:** `RealGlassSurface`: đặt `collectFrameStats = false` (tài liệu thư viện: "Tắt ở production"; **đọc tài liệu, chưa đo**). Áp cho cả dock → **đo dock trước/sau** ở Step 4.
- ~~Step 3 (kính thật cho thanh/pill)~~ **Bỏ** (chủ dự án 2026-11-03): thanh và pill giữ kính mờ tĩnh, `SearchPillView` không đổi cấu trúc.
- [x] **Step 4: Kịch bản kiểm (Oppo)** (đo (b) xong qua O2 vòng 1-3; các mục mắt (a), (c), (d), (f) nằm trong bài kiểm cuối) — (a) mở panel thư mục/nhóm: kính thật khúc xạ ở mép, chữ đọc được, fade 150 ms không chớp/đen; (b) mở-đóng 20 lần (S1, S5): Janky trước/sau theo ngưỡng D10 Oppo; (c) bàn phím sửa tên: kính không đổi; (d) đổi ảnh nền rồi mở panel: kính hiện ảnh mới; (e) dock: Janky đứng yên/vuốt trang trước/sau khi tắt `collectFrameStats`; (f) `FORCE_LOW` trên Oppo: panel quay về `STATIC_BLUR`, không crash.
- [x] **Step 5: Commit** `feat: render folder and group panels with real glass on LENS`.

---

**Đã code (commit `400aef2`):**
- `GlassPolicy`: `FOLDER_PANEL`, `GROUP_PANEL` → `REAL` khi tier = `LENS`, còn lại `STATIC_BLUR`; `GlassEnvironment.modeFor` hạ `REAL` về `STATIC_BLUR` khi `GlassDebug.forceLite == 1`; `GlassDebug` có trường thứ tư `forceLite`.
- `GlassSurface.setTint(argb)` (mặc định no-op), `RealGlassSurface.setTint` đặt `glassTint`, và `RealGlassSurface` đặt `collectFrameStats = false` (áp cả cho dock: **đọc tài liệu, chưa đo**; đo dock ở O2 S2/S3i).
- `BackgroundGlass`: chế độ `REAL` (host phải là `FrameLayout`, tức hai panel): thêm `RealGlassSurface.view` làm con đầu tiên của host (`LayoutParams(0, 0)`), `panel.background = null`, bo góc `folder_panel_radius`, tint `gl_panel_tint`, `backdropSource` = `backdrop_image`; `layoutGlass()` (host gọi sau `super.onLayout`) đo `EXACTLY` và đặt kính đúng khung `panel`. **Quy tắc `invalidate()`:** kính thật luôn `setInvalidationSuppressed(true)` (cuộn bất kỳ view nào trong cửa sổ không làm nó chụp lại nền), trừ xung nhịp 1 giây lúc panel vừa hiện (`onShown()`: bỏ chặn + `setDynamic(true)`, sau 1 giây `endPulse` đặt lại `setDynamic(false)` + chặn); `onHidden()` huỷ xung nhịp. Chưa có ảnh nền (`backdrop_image.drawable == null`) thì dùng kính mờ tĩnh (phẳng), và `onShown()` chọn lại khi ảnh nền đã có. API chặn của TL1 được dùng lại, không viết lại.
- `FolderPanelView`/`CategoryGroupPanelView`: `onLayout` gọi `layoutGlass()`, `render` gọi `onShown()`/`onHidden()` khi panel hiện/ẩn.
- `FORCE_HIGH` (Ép cao) trên máy classic (Live 4) cũng ra tier LENS nên panel là kính thật chạy pipeline classic CPU (nặng: lựa chọn rõ ràng của người dùng). Không đo.
- Không hạ thấp: kính thật của dock giữ nguyên; `GlassBudget` ghi chú tổng kính thật đồng thời trên LENS tối đa 2 (dock + một panel).

**Kết quả O2 (điều phối, Oppo, build `042f8f5`, 3 lượt mỗi ô; S6 bỏ qua vì không có panel):**

| Cấu hình | S1 khung | S1 Janky | S1 gfx p50 / p95 | S1 fs p95 | S5 khung | S5 Janky | S5 gfx p50 / p95 |
|---|---|---|---|---|---|---|---|
| `lite` | 743 | 0.49% | 9 / 14.7 | 16.1 | 207 | 7.42% | 10 / 36 |
| **`real`** | **2559** | 0.78% | **15 / 20** | **25.1** | **1781** | 0.97% | **15 / 19.7** |
| `lite2` | 741 | 0.36% | 9 / 15 | 16.3 | — | — | — |

Dock (`collectFrameStats = false`): `lite2` S2 1.55%, p50 10 / p95 15.3, rec 2.1 / 3.0, giống `c_all` S2 cũ (1.53%) ⇒ **không đổi đo được, giữ.** **Đọc số:** kính thật thêm ~1800 khung mỗi 20 lần mở (~90 khung mỗi lần mở) = đúng xung nhịp 1 giây `setDynamic(true)`, mỗi khung ~15 ms; Janky % thấp chỉ vì các khung dư làm loãng (số khung Janky tuyệt đối ở S1: 3.7 → 20); p95 +5 ms (gfx), +9 ms (framestats) ⇒ **không đạt D10** (+4 ms) và tốn pin (~1 giây vẽ liên tục mỗi lần mở).

**Vòng 2/5: bỏ xung nhịp động, vẽ lại một lần.** Đọc bytecode `LiquidGlassView` v2.0.11 (`javap -c`, 2026-11-03):
- `drawGlassEffect` gọi `tryDrawLensGlass` (API 33+) → `GlassLensRenderer.draw(...)` lấy nền **ở mỗi lần vẽ kính** (không giữ bitmap nền); nếu `enableDynamicBackground` thì sau lần vẽ gọi `invalidate()` (vòng vẽ liên tục: đây chính là 90 khung dư). Không thì không có gì tự vẽ lại.
- Đường classic (`renderGlassEffectSync`): khi không động, nó vẫn chụp nền và so `computeBackdropSignature` với `lastBackdropHash`, chỉ chạy lại phần làm mờ khi chữ ký đổi, nên cũng lấy nền hiện tại ở mỗi lần vẽ kính.
- Hệ quả: hiện tượng M8a ("dock mờ ảnh cũ tới khi có sự kiện") là do **không có lần vẽ lại kính nào**, không phải kính giữ bitmap cũ. Panel mới hiện luôn được vẽ (đổi visibility, layout, alpha), nên chỉ cần **một** lần vẽ lại sau khi hiện để chắc chắn lấy nền hiện tại; không cần chế độ động, kể cả "tối đa 2 khung".
**Sửa (`f2eea97`):** `BackgroundGlass` bỏ `setDynamic`/`endPulse`/`PULSE_MS`; `onShown()` chọn lại kính thật/kính mờ rồi `host.post(captureOnce)`; `captureOnce()` = bỏ chặn → `invalidate()` → chặn lại ngay (lệnh vẽ đã lên lịch, không khung dư); `FolderPanelView` gọi thêm `captureOnce()` khi hoạt họa hiện (`withEndAction`) kết thúc; `onHidden()` chỉ huỷ lệnh chờ. Kính vẫn luôn bị chặn `invalidate()` (quy tắc TL1). **Mục tiêu đạt (điều phối):** `real` S1 trong +4 ms p95 so với `lite`, Janky trong +1.0 điểm, số khung Janky tuyệt đối gần `lite`; khung S1 `real` ≈ `lite` (~743).

**Kết quả vòng 2 (điều phối, Oppo, build `0155b95`, S1, 3 lượt mỗi ô):**

| Cấu hình | Số khung | Janky (tuyệt đối) | gfx p50 / p95 | fs p95 |
|---|---|---|---|---|
| `r2_lite` | 738 | 0.59% (4.3) | 9 / 14.7 | 15.9 |
| `r2_real` | 743 | **2.74% (20.3)** | 11 / **23.3** | 24.1 |

Xung nhịp động đã hết (số khung bằng `lite`), nhưng **vẫn không đạt** quy tắc Oppo: Janky +2.15 điểm, p95 +8.6 ms. Mẫu hình đúng **1 khung Janky mỗi lần mở** (20 khung Janky cho 20 lần mở): khung đầu tiên vẽ panel kính thật chậm (~25 ms). **Dừng ở đây, chờ chủ dự án (hôm nay chủ dự án nghỉ); chưa code vòng 3.**

**Phân tích chuẩn bị cho vòng 3 (agent, 2026-11-03, từ `framestats` của vòng 2 và bytecode `LiquidGlassView`/`GlassLensRenderer` v2.0.11; chưa kiểm chứng bằng đo thêm):**
- **Giai đoạn nào chậm.** Toàn bộ khung S1 (độ lệch so với `lite`): `SyncQueued→IssueDraw` (đồng bộ lên RenderThread) p95 0.94 → **5.9 ms**, p99 1.98 → 8.5; `SwapBuffers→FrameCompleted` (chờ GPU) p95 5.3 → **13.9 ms**, p99 7.1 → 20.4; ghi display list (`rec`), traversal và `IssueDraw→Swap` gần như không đổi. Tức chi phí nằm ở **RenderThread/GPU**, không ở UI thread. Các khung chậm nhất của `real` có `swap` 11-15 ms (và vài khung `issue` 19 ms) trong khi `lite` ở 3.5-6.7 ms.
- **Giải thích khớp bytecode:** mỗi `LiquidGlassView` có một `GlassLensRenderer` riêng với hai `RenderNode` (`renderNode` gắn `RenderEffect` AGSL, `layerNode` dùng `setUseCompositingLayer(true)`); mỗi lần vẽ nó ghi lại (`recordBackdrop`) vùng nền dưới kính vào `renderNode`, `RuntimeShader` AGSL tạo MỘT lần cho mỗi renderer (lúc vẽ đầu) và bị `release()` khi `onDetachedFromWindow`. Panel chỉ bị `GONE` (không detach) nên shader không biên dịch lại mỗi lần mở; nhiều khả năng khung đầu mỗi lần mở trả chi phí **cấp lại lớp compositing** (`sync` tăng) và các khung hoạt họa mờ dần chạy shader khúc xạ + làm mờ trên cả vùng panel **bên trong lớp alpha của host** (`FolderPanelView` mờ dần bằng `alpha` của view nên cũng có lớp trung gian phủ cả màn hình) nên `swap` (GPU) tăng.
- **Các hướng cho vòng 3, đánh giá theo bytecode/số đo (chưa code):**
  1. **Khởi động trước (pre-warm) kính thật:** chỉ trị chi phí **một lần** (biên dịch `RuntimeShader` ở lần vẽ đầu của view); bytecode cho thấy shader bị giữ giữa các lần mở nên **không** trị được chi phí lặp lại mỗi lần mở. Giá trị thấp.
  2. **Giữ kính gắn sẵn giữa các lần mở:** đã là thực tế hiện nay (`BackgroundGlass` chỉ tạo một lần, panel `GONE` không detach); không thêm gì.
  3. **Bỏ lớp alpha của host khi dùng kính thật** (không mờ dần `alpha` của cả `FolderPanelView`, chỉ mờ dần nền tối/scrim hoặc hiện ngay): nhắm đúng chỗ nghi ngờ (lớp trung gian phủ cả màn hình bao quanh một `RenderEffect`). Rủi ro: đổi hoạt họa mở panel thư mục (panel nhóm vốn không có fade).
  4. **Giảm công việc GPU của kính:** tắt các hiệu ứng không cần cho panel (`enableChromaticDispersion`, `enableSensorHighlight`, độ mờ/`bevelWidth`/`refractionHeight` nhỏ hơn) vì vùng kính rộng; cần thử đo.
  5. **Chấp nhận một khung chậm mỗi lần mở** (đánh đổi của chủ dự án): +2.15 điểm Janky, p95 +8.6 ms ở S1 trên Oppo; khung chậm rơi vào đầu hoạt họa mờ dần 150 ms nên khó thấy.
  6. **Giữ panel là kính mờ tĩnh trên LENS** (đổi một dòng ở `GlassPolicy`): không rủi ro, mất vẻ khúc xạ thật ở panel.
- **Đề xuất của agent:** thử 3 + 4 (rẻ, cùng nhắm vào chi phí GPU/lớp) và đo lại S1 `real`/`lite`; nếu vẫn không đạt thì 5 hoặc 6 theo ý chủ dự án. Quyết định thuộc chủ dự án.

**Ghi chú Task 7 sau review cuối (2026-11-04): tier không phải là khả năng.** Live 4 (API 30) với `FORCE_HIGH` của chủ dự án, build `e4cba37`: S1 **Janky 30.7%, gfx p90 105 / p95 112 ms, rec p95 92 ms** (4-5 khung ~100 ms mỗi lần mở/đóng; S2 vẫn 0.5%). `FORCE_HIGH` cho tier LENS dù máy chỉ có đường chụp bằng CPU, nên `GlassPolicy` cấp `REAL` cho panel. Sửa: `GlassTierResolver.isRealGlassCapable(effectiveApiLevel)` (API 33+, cùng ngưỡng nhánh AUTO) và `GlassEnvironment` truyền `realGlassCapable` vào `GlassPolicy.modeFor(tier, role, realGlassCapable)` (vẫn hàm thuần, không đọc API bên trong) và `GlassBudget.maxLiteGlassCellsPerPage(tier, realGlassCapable)`: `REAL` cho `FOLDER_PANEL`/`GROUP_PANEL` cần tier LENS **và** máy có đường Lens; ngân sách 24 ô chỉ khi tier LENS **và** có đường Lens (nền đo 24 là Oppo), còn lại K = 6. Dock dưới `FORCE_HIGH` vẫn là kính thật (đúng lựa chọn của chủ dự án, S2 đạt). Chờ chủ dự án đo lại S1 trên Live 4 với `FORCE_HIGH`.

**Vòng 3 và quyết định (2026-11-04).** Hai thử nghiệm bật riêng bằng trường `variant` của `gl_mask` (bit 0 = panel thư mục hiện/ẩn tức thì, bỏ lớp `alpha` của host; bit 1 = kính nhẹ chỉ cho panel). Đo Oppo, build `e7d9021`, S1, 20 lần mở mỗi lượt, 3 lượt:

| Cấu hình (`127 24 0 <forceLite> <variant>`) | Số khung | Khung Janky | gfx p50 / p95 |
|---|---|---|---|
| `lite` (`... 1 0`) | 741 | 1.7 | 9 / 14 |
| `real`, variant 0 | 746 | 21.3 | 11 / 23.3 |
| bit 0 (hiện/ẩn tức thì, bỏ lớp alpha của host) | 60 | 20 | 24 / 27 |
| bit 1 (kính nhẹ: bỏ dispersion/sensor/edgeSoftness, blur/bevel/khúc xạ nhỏ) | 741 | 20.7 | 10.3 / 22.3 |
| bit 0 + 1 | 60 | 20 | 23 / 26 |

Không cách nào khử được khung chậm: mọi biến thể còn đúng ~20 khung Janky (~24 ms) cho 20 lần mở. Vậy **lớp alpha của host và tải GPU của lens (dispersion, sensor, độ mềm vành, blur/bevel) không phải nguyên nhân**; chi phí là của chính khung đầu tiên vẽ kính thật (nhiều khả năng cấp lớp compositing/RenderEffect cho vùng panel). Khung bit 0 chỉ có 60 vì không còn khung hoạt họa 150 ms nên 20 khung Janky thành 33%: số khung Janky tuyệt đối không đổi. **Quyết định chủ dự án:** giữ kính thật cho hai panel trên LENS, có fade 150 ms và đủ hiệu ứng; chấp nhận một khung trễ mỗi lần mở panel làm **ngoại lệ ghi nhận của D10** (Oppo S1: Janky +2.15 điểm, p95 +8.6 ms so với kính mờ tĩnh; rơi vào đầu fade 150 ms nên khó thấy). Mã vòng 3 đã gỡ (revert `f7162c3`), panel chạy như vòng 2.

### Task 8: Phủ tier `CLASSIC_GPU`, tương phản và hồi quy

**Bắt buộc kiểm tay:** không (đưa vào bài kiểm cuối). Máy: Oppo + Live 4.
**Files:** Modify `core/glass/{GlassSurfaceFactory,GlassEnvironment,GlassDebug}.kt` (móc debug), `res/values/colors.xml` (chỉnh tint theo mắt).

- [x] **Step 1: Cách phủ `CLASSIC_GPU` (không có máy API 31–32).** **Phần mã đã xong (2026-11-04):** `GlassDebug.cap` (trường thứ ba của `gl_mask`, > 0 = giả lập API <= cap; 0 hoặc bỏ trống = tắt) được áp bằng `GlassDebug.applyApiCap` cho phép dò tier của `GlassEnvironment` và `GlassSurfaceFactory` VÀ cho chính `LiquidGlassView` của `RealGlassSurface` (dock), vì `debugApiLevelCap` là thuộc tính của từng view; `GlassDebug.ensureLoaded` đọc tệp đúng một lần trước phép dò. Lệnh O3 trên Oppo: `0 0 32 0` (trước: mọi phần tử FLAT, dock classic GPU) và `127 6 32 0` (sau: K = 6 như `CLASSIC_GPU`), kịch bản S1, S2, S3i, S4, 3 lượt; **Kết quả O3 (điều phối, Oppo, build `3b9a2dd`, 3 lượt mỗi ô, log xác nhận `apiCap=32`):** đạt D10 ở cả bốn kịch bản (Janky tăng tối đa +0.14 điểm, p95 +2.7 ms ở S2).

| Kịch bản | `o3_off` (`0 0 32 0`) Janky, p95 | `o3_all` (`127 6 32 0`) Janky, p95 |
|---|---|---|
| S1 | 0.31%, 14 ms | 0.45%, 14.7 ms |
| S2 | 1.42%, 13 ms | 1.56%, 15.7 ms |
| S3i | 0.14%, 26-34 ms | 0.20%, 21-34 ms |
| S4 | 0.95%, 14 ms | 0.94%, 14 ms |

Lần lặp `o3_off2` bỏ qua vì nền S1/S2 khớp các phiên trước. Giới hạn: Oppo mạnh hơn máy Android 12 thật nên chỉ chứng minh đường mã `CLASSIC_GPU` chạy đúng (dock classic GPU, panel và ô kính mờ tĩnh), không đại diện số Janky. **`CLASSIC_GPU`: chưa đo trên máy thật** (Câu hỏi mở 4 giữ nguyên). Sửa vỡ ô của kính mờ tĩnh (`small()` mượt hơn) được chủ dự án xác nhận bằng mắt trên Oppo (`blur-before-after.png`): panel mượt, không còn ô vuông. (i) **Theo cấu trúc:** D5 — tier này chạy đúng đường `STATIC_BLUR`/`FLAT` đã đo trên Live 4 (HW chậm nhất đã có), cộng dock `RealGlassSurface` như hôm nay; không thêm kính thật nào. (ii) **Chạy thử đường thật trên Oppo:** móc debug (tạm) `GlassDebug` đặt `LiquidGlassView.debugApiLevelCap = 32` trước khi dò `effectiveApiLevel` trong `GlassSurfaceFactory` và `GlassEnvironment` → Oppo resolve ra `CLASSIC_GPU` và dock chạy pipeline classic GPU (blur + saturation) của thư viện. Kiểm: không crash, panel/ô/thanh/pill đúng chế độ, Janky ghi lại làm tham khảo. **Giới hạn:** Oppo mạnh hơn máy Android 12 thật nên số Janky không đại diện; chỉ chứng minh đường mã chạy và hình ảnh đúng. (iii) **Mượn máy Android 12 nếu chủ dự án có** (Câu hỏi mở 4) thì đo S1–S3 một lần và thay bằng số thật. Tất cả ghi vào mục Nhật ký, nếu chỉ có (i)–(ii) thì ghi rõ "CLASSIC_GPU: chưa đo trên máy thật".
- [x] **Step 1b: Dựng `small()` ngoài main thread (2026-11-04).** Đo trên Oppo (build `2d4e8ba`): `1224x1632 -> 306x408` mất **78.8 ms** trên main thread, 487 KB (khoảng 5 khung rớt lúc khởi động và mỗi lần đổi ảnh nền; vượt ngưỡng 30 ms). Sửa: `BlurredBackdropCache.smallOrRequest(source, listener)` trả bản nhỏ nếu đã có cho đúng bitmap nguồn, còn không trả null và dựng MỘT lần trên một luồng đơn (`GlassBlur`, ưu tiên thấp); nhiều ô cùng xin không dựng lại; kết quả về main thread, bỏ (và `recycle`) nếu bitmap nguồn đã đổi; lỗi dựng thì ghi nhớ và vẽ phẳng, không thử lại mỗi khung. `BackdropBlurDrawable` vẽ nền phẳng (`GlassStyle.flat`) tới khi xong rồi `owner.invalidate()` qua `SmallListener` (cache giữ tham chiếu yếu; `BackgroundGlass.detach()` gọi `cancelPending()`). Bitmap nguồn chỉ được ĐỌC ở luồng nền: ảnh nền không bị `recycle` sau khi vào state (các `recycle` trong `data/backdrop` chỉ là bitmap trung gian trước khi trả ảnh), và `build` vẫn bọc `try/catch`. Log `GL` thời gian dựng giữ lại (tạm). Chờ số đo Live 4.
**Live 4 chạy lại (điều phối, 2026-11-04, build `ce213e3`, kính Tự động, máy nguội 31-32 °C, một phiên, 3 lượt mỗi ô): ĐẠT.**

| Cấu hình (`gl_mask`) | S2 Janky (3 lượt) | S2 khung p50 / p95 | S3i Janky | S3i khung p95 |
|---|---|---|---|---|
| `f_off` | 0.51 / 0.30 / 2.84% | 4.4 / 7.0 ms | 0.08% | 9.7 ms |
| `f_k6` | 1.92 / 0.66 / 0.41% | 4.9 / 9.0 ms | 0.00% | 9.95 ms |
| `f_all` | 0.51 / 0.56 / 0.61% | 4.8 / 8.8 ms | 0.03% | 10.0 ms |

Số tuyệt đối thấp hơn nhiều so với 2026-11-03 (khung p50 ~8 -> ~5 ms) vì lần trước máy đang nóng: đúng bài học "máy trôi theo thời gian" (so sánh trong cùng phiên). `small()` trên Live 4: `720x899 -> 180x224` mất 22.7 ms, chạy trên luồng `GlassBlur`. Kiểm FLAT (`gl_mask 0 0`) so với `main` `99b405d`: Home và App Library giống từng điểm ảnh (y 110-2220). Panel mượt trên Live 4.
- [ ] **Step 2: Tương phản (chủ dự án xét ở lượt kiểm cuối, chưa đổi màu)** *(ước tính tính tay 2026-11-04, chưa nhìn bằng mắt: kính mờ tĩnh của panel = ảnh nền mờ phủ `gl_panel_tint` 40% đen, nên nền trắng tinh ra xám ~153, chữ trắng đạt tỉ số ~2.9:1, dưới 4.5:1 cho nhãn nhỏ; cần tint >= ~54% (alpha >= 0x8A) để đạt 4.5:1 trên ảnh trắng. Thanh/pill (`gl_bar_tint` 50%): ~4.0:1 trên ảnh trắng. Chưa đổi màu nào: chỉnh sau khi xem ảnh sáng thật; đề xuất điểm xuất phát `gl_panel_tint` = `#8C000000`.)*
  - **Dòng kiểm cho lượt cuối:** với ảnh nền trắng/vàng nhạt, chữ tiêu đề + nhãn icon trong panel và chữ trong thanh/pill còn đọc được? Hằng số hiện tại trong `res/values/colors.xml`: `gl_panel_tint` = `#66000000` (panel), `gl_bar_tint` = `#80000000` (thanh tìm kiếm, pill), `gl_cell_tint` = `#26FFFFFF` (ô thư mục/ô nhóm). Ứng viên nếu panel chưa đủ: `gl_panel_tint` = `#8C000000`. Không đổi màu chữ; không đạt thì tăng tint. — chữ tiêu đề/nhãn/hint trên kính với ảnh sáng (trắng, vàng nhạt) và ảnh tối, cả Live 4 và Oppo; chỉnh `gl_*_tint` đến khi đọc được. Ghi tỉ số tương phản ước tính bằng mắt/công cụ nếu có; không đạt thì tăng tint, không đổi màu chữ.
- [ ] **Step 3: Hồi quy toàn bộ** theo danh sách hồi quy của "Bài kiểm cả tính năng" (kéo-thả M7, tạo/giải tán thư mục, tìm kiếm, Library, đổi mức kính, đổi ảnh nền).
- [ ] **Step 4: Commit** `fix: tune glass tint and cover classic gpu tier`.

---

> **Thứ tự thực hiện:** Task 10 và 11 (yêu cầu bổ sung của chủ dự án sau bài kiểm TL1, 2026-11-03) được đánh số theo thứ tự giao việc nhưng **làm trước Task 9** (Task 9 là Task đóng nhánh).

### Task 10: App Library co theo kích thước cửa sổ (split-screen) ✅ đã qua review và đã kiểm trên máy 2026-11-03 (Live 4: cỡ bình thường giống hệt `main`; `wm size 1080x1150`/`1080x900` đúng ý; split-screen thật chủ dự án kiểm ở bài kiểm cuối)

**Nguồn:** góp ý của chủ dự án từ bài kiểm TL1 (2026-11-03). Chủ dự án quyết: **ô nhóm (và icon, thanh tìm kiếm của chúng) co theo bề rộng và chiều cao cửa sổ như lưới Home, có cỡ tối thiểu hợp lý; danh sách vẫn cuộn được** (không bắt cả lưới phải nằm gọn trong cửa sổ).
**Bắt buộc kiểm tay:** không (bố cục, không phải đo hiệu năng hay cử chỉ mới). Máy: Live 4, mô phỏng cửa sổ thấp bằng `adb shell wm size 1080x1150` (**luôn đặt lại** `adb shell wm size reset` sau khi kiểm); split-screen thật do chủ dự án kiểm ở bài kiểm cuối (agent không vào được split-screen thật qua adb trên máy này).
**Files:** Create `core/ui/LibraryLayoutMetrics.kt`; Modify `ui/home/view/CategoryPageView.kt`. (Khi code: `REF_W/REF_H/FLOOR` và các sàn là hằng thuật toán trong `LibraryLayoutMetrics`, miễn trừ khỏi `dimens.xml` giống `GridMetrics`, nên `dimens.xml` và hai layout XML **không** bị sửa; mọi giá trị khi `s = 1` lấy từ các `dimens` `category_*`/`search_field_*` sẵn có.)
**Interfaces:** Consumes `CategoryGroupApps`, các dimens `category_*` hiện có. Produces: hàm thuần `LibraryLayoutMetrics` (cạnh view hoặc trong `core/ui`, không import `android.*`): từ bề rộng và chiều cao cửa sổ (dp) trả về hệ số `s` và các cỡ đã nhân `s` (icon nhỏ, đệm ô, khe, chiều cao thanh tìm kiếm, `marginTop` của `ScrollView`, cỡ chữ nhãn).

**Hiện trạng (đọc code `CategoryPageView`):** cỡ ô và icon nhỏ chỉ suy từ **bề rộng** (`miniIconSizePx()` = `(rộng − 2·category_page_padding)/2 − …`), thanh tìm kiếm cố định `search_field_height` 48dp + `search_field_margin_top` 8dp (vùng `category_search_area_height` 64dp), nhãn 12sp, khe/đệm cố định (`category_tile_gap` 10dp, `category_tile_padding` 12dp, `category_mini_icon_gap` 4dp). Nên cửa sổ thấp thì ô giữ nguyên cỡ và chỉ cuộn. Ở toàn màn hình Library **đã** không vừa trong một màn hình khi có nhiều nhóm (từ khoảng 10 nhóm tức 5 hàng trở lên) và cuộn như hiện nay.

**Vì sao chọn thiết kế này (≥ 2 phương án):**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Co vừa đủ để cả lưới nằm gọn trong cửa sổ ("fit-all", như một trang Home luôn điền đầy) | Số hàng = ⌈số nhóm / 2⌉; cạnh ô theo chiều cao = (chiều cao khả dụng − vùng thanh) / số hàng | Không cần cuộn khi nhóm ít | **Trái quyết định của chủ dự án** ("co theo cửa sổ, danh sách vẫn cuộn"); và **làm hỏng "bình thường không đổi"**: nhiều nhóm thì cạnh theo chiều cao nhỏ hơn cỡ hiện nay ngay ở toàn màn hình, nên ô co cả khi bình thường; kết quả phụ thuộc số nhóm (cỡ ô nhảy khi cài/gỡ app) | Không |
| **B. Hệ số co theo kích thước tham chiếu** | `s = clamp(min(1, rộng_dp / REF_W, cao_dp / REF_H), FLOOR, 1)` với `REF_W` × `REF_H` = cỡ cửa sổ dọc đầy đủ mà các `dimens` hiện tại được thiết kế (điểm xuất phát 360 × 640 dp; vùng Library của Live 4 toàn màn hình ≈ 360 × 740 dp nên `s = 1`). Mọi kích thước (ô, icon nhỏ, khe, đệm, thanh, chữ nhãn) nhân `s` rồi kẹp sàn; **danh sách vẫn cuộn** | Đúng quyết định của chủ dự án; toàn màn hình `s = 1` nên **không đổi điểm ảnh**; `s` chỉ phụ thuộc cửa sổ, không phụ thuộc số nhóm nên cỡ ô ổn định khi cài/gỡ app; tiên đoán được và là hàm thuần dễ review | `REF_W/REF_H` là con số tự đặt (suy luận từ cỡ máy thử, chỉnh theo mắt); co cả khi nội dung ít nhóm (chấp nhận, giống Home co theo cửa sổ chứ không theo số icon) | **Chọn** |
| C. Chuyển lưới sang nhiều cột khi cửa sổ thấp | Thêm cột thay vì co | Giữ ô lớn | Đổi hình học (2 cột là thiết kế D13), phức tạp với panel nhóm | Không |

**Cơ sở:** B bám đúng lời chủ dự án ("như lưới Home" — lưới Home co theo cửa sổ, không theo số icon) và M5 Task 2/4 (view con giữ dữ liệu suy ra từ kích thước thì phải cập nhật khi kích thước cha đổi). Với `wm size 1080x1150` (≈ 360 × 383 dp) `s = min(1, 360/360, 383/640) ≈ 0.6` rồi kẹp sàn. Sàn là điểm xuất phát (suy luận, chỉnh theo mắt): `FLOOR` của `s` ≈ 0.6; cạnh ô ≥ 72 dp, icon nhỏ ≥ 24 dp, thanh ≥ 36 dp, chữ nhãn ≥ 9 sp. **Đánh đổi:** vùng chạm của thanh và ô có thể dưới 48 dp ở cửa sổ rất thấp (Home đã chấp nhận điều này ở M5 Task 2); xét lại nếu chủ dự án muốn giữ 48 dp. Vì `s` phụ thuộc cả bề rộng, split-screen kiểu hai bên (cửa sổ hẹp) cũng co ô theo bề rộng.

- [x] **Step 1: Hàm thuần `LibraryLayoutMetrics`** — đầu vào: bề rộng và chiều cao cửa sổ khả dụng (dp; chiều cao trừ padding hệ thống mà `HomeActivity` đặt bằng `setPadding`); đầu ra: `s` và các cỡ đã nhân `s` rồi kẹp sàn. `REF_W`/`REF_H`/`FLOOR` khai ở một chỗ. `s = 1` thì các giá trị **bằng đúng** `dimens` hiện tại (đọc code: nhánh này không đổi biểu thức nào).
- [x] **Step 2: Nối vào `CategoryPageView`** — `render()`/`addTile()` đọc từ hàm thuần thay vì dimens cố định; `onSizeChanged` dựng lại khi **bề rộng hoặc chiều cao** đổi (hiện chỉ `w != oldw`); thanh tìm kiếm đổi chiều cao và `marginTop` của `ScrollView` theo `s`; `ScrollView` vẫn cuộn. Kính ô nhóm và thanh không cần xử lý riêng (vị trí lấy lúc vẽ) nhưng phải kiểm lại sau khi đổi cỡ.
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) cỡ bình thường: ảnh chụp Library (`adb exec-out screencap -p`) với APK trước và sau Task 10, cùng mặt nạ kính, **giống hệt**; (b) `wm size 1080x1150`: ô nhóm, icon, thanh và chữ co theo, không bị cắt, danh sách cuộn được; (c) `wm size 1080x900` (rất thấp): chạm sàn, cuộn được, bấm ô nhóm mở panel nhóm đúng; (d) mở Library rồi đổi `wm size` qua lại: ô dựng lại đúng, kính ô nhóm và thanh bám nền; (e) cài/gỡ app (đổi số nhóm): cỡ ô không nhảy; (f) `adb shell wm size reset` ở cuối, kiểm lại cỡ bình thường.
- [x] **Step 4: Commit** `feat: scale the app library with the window size`.
- **Vòng 2/5 (lỗi cỡ chữ do kiểm trên máy):** ảnh chụp thư viện ở cỡ bình thường lệch ở gợi ý "Tìm kiếm" của thanh Library: `CategoryPageView` đặt cỡ chữ từ một hằng sp tự quy đổi (và `getDimension` không làm tròn như `TextView`), nên ở `s = 1` không giữ đúng giá trị XML khi máy có `font_scale` ≠ 1 (máy chủ dự án 1.15). **Sửa (`facc271`):** ở `s >= 1` **không đụng** cỡ chữ nào (thanh, nhãn nhóm, chữ "+N"); ở `s < 1` lấy cỡ chữ hiện tại của chính view (từ XML, đã gồm font scale) nhân `s`, sàn bằng sp. `labelTop` cũng chỉ đặt khi `s < 1`. Đọc lại đường mã: không còn hằng sp tự quy đổi.

---

### Task 11: Nhãn icon Home co theo icon trong cửa sổ thấp ✅ đã qua review và đã kiểm trên máy 2026-11-03 (Live 4: cỡ bình thường giống hệt `main`; `wm size 1080x1150`/`1080x900`: nhãn ẩn, icon co; split-screen thật chủ dự án kiểm ở bài kiểm cuối)

**Nguồn:** góp ý của chủ dự án từ bài kiểm TL1 (2026-11-03). Chủ dự án quyết: **nhãn co theo, hoặc ẩn khi icon dưới cỡ tối thiểu, để icon và nhãn luôn tỉ lệ; layout Live 4 bình thường (icon 48dp, có nhãn) giữ nguyên từng điểm ảnh.**
**Bắt buộc kiểm tay:** không. Máy: Live 4 với `wm size 1080x1150` (đặt lại sau khi kiểm); split-screen thật ở bài kiểm cuối.
**Files:** Modify `core/ui/GridMetrics.kt` (hiện không nhánh nào khác đang sửa; TL2 chỉ đổi `ICON_SCALE`), `ui/home/view/IconCellView.kt`; kiểm `ui/home/view/FolderPanelView.kt` (`AppsGrid` dùng cùng `GridMetrics`) và `DockView` (ép `showLabels = false`).
**Interfaces:** Consumes `GridMetrics.measure`. Produces: `GridMetrics.Cells` thêm `labelScale: Float` (1f khi bình thường) và `innerGapPx: Int` (khe icon→nhãn đã co) và `showLabels` trở thành giá trị **hiệu lực** (người dùng bật nhãn **và** còn đủ chỗ); `Cells` là data class nên đổi thì `IconGridView.bindAllCells`/`DockView.setItems` tự bind lại (bài học M5 Task 2, M8a Task 2).

**Hiện trạng (đọc code):** `GridMetrics.measure` trừ **cố định** `LABEL_HEIGHT_DP` = 20dp và `INNER_GAP_DP` = 4dp khỏi chiều cao ô để ra chỗ cho icon; chữ nhãn lấy từ `R.dimen.icon_label_text_size` (10sp) và không đổi theo cỡ ô. Cửa sổ thấp thì icon co về cỡ rất nhỏ (điều phối quan sát icon ≈ 12dp ở `wm size 1080x1150`) trong khi nhãn giữ cỡ cũ, trông hỏng.

**Vì sao chọn thiết kế này (≥ 2 phương án):**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ ẩn nhãn khi icon nhỏ hơn ngưỡng | `measure` tính icon có nhãn; nếu `iconSize < ngưỡng` thì tính lại không nhãn | Đơn giản | Nhãn nhảy từ "đầy" sang "mất" đột ngột; cửa sổ vừa phải mất nhãn dù còn chỗ cho nhãn nhỏ | Không |
| B. Chỉ co nhãn theo icon | `labelScale = iconSize / cỡ tham chiếu` cho chữ, chiều cao nhãn và khe | Mượt, tỉ lệ | Cửa sổ rất thấp thì chữ ≈ 4sp không đọc được | Không |
| **C. Co nhãn trước, ẩn khi quá nhỏ** | `labelScale = clamp(iconSize / LABEL_FULL_ICON_DP, 0, 1)`; nếu `labelScale < LABEL_MIN_SCALE` (0.8, chữ dưới 8sp) thì ẩn nhãn (`showLabels` hiệu lực = false) và tính lại icon không nhãn; cỡ chữ, `LABEL_HEIGHT_DP` và `INNER_GAP_DP` đều nhân `labelScale` | Cả hai ý của chủ dự án; `labelScale = 1` từ `LABEL_FULL_ICON_DP` trở lên nên layout bình thường **không đổi**; chữ không bao giờ nhỏ hơn sàn đọc được | Tính icon hai lượt (có nhãn → hệ số → tính lại), thêm một trường vào `Cells` | **Chọn** |

**Cơ sở:** C đúng lời chủ dự án; mọi con số đo trên Live 4 (icon 48dp bình thường) cho `labelScale = 1` nếu `LABEL_FULL_ICON_DP` ≤ 48 (điểm xuất phát đề xuất 44dp, suy luận chưa đo), nên không đổi layout bình thường. Sàn đọc được điểm xuất phát: chữ nhãn tối thiểu 8sp, tức `LABEL_MIN_SCALE` = 0.8 (10sp · 0.8); dưới đó ẩn nhãn. Chỉnh theo mắt. **Đánh đổi:** hai lượt tính nên cần giữ hàm thuần và đọc kỹ; nhãn co nghĩa là dock (không nhãn) không đổi.

- [x] **Step 1: `GridMetrics.measure`** — tính cỡ icon với nhãn đầy đủ (như hôm nay); nếu `iconSize >= LABEL_FULL_ICON_DP` thì trả `labelScale = 1f` và đúng các số cũ (kiểm bằng đọc code: nhánh này không đổi biểu thức nào). Ngược lại `labelScale = iconSize / LABEL_FULL_ICON_DP`; nếu `labelScale < LABEL_MIN_SCALE` (chữ 10sp · `labelScale` dưới 8sp) thì tính lại icon không nhãn, `showLabels` hiệu lực false; còn lại tính lại icon với chiều cao nhãn và khe đã nhân `labelScale` (một lượt, không lặp). `Cells` thêm `labelScale`, `labelHeightPx`, `innerGapPx` để view khỏi tính lại từ hằng số.
- [x] **Step 2: `IconCellView`** — cỡ chữ = `icon_label_text_size · labelScale` (đặt trước khi dựng `StaticLayout`, vì `bind`/`bindFolder` dựng lại nhãn mỗi lần `Cells` đổi); `contentHeight()` và vị trí nhãn dùng `cells.innerGapPx` (bằng `INNER_GAP_DP · density` khi `labelScale = 1`, nên không đổi điểm ảnh); bóng chữ (`icon_label_shadow_radius`) nhân theo `labelScale` với sàn 1px.
- [x] **Step 3: Rà nơi dùng** — `grep` mọi chỗ đọc `showLabels`/`LABEL_HEIGHT_DP`/`INNER_GAP_DP`/`icon_label_text_size`: `IconGridView`, `DockView` (ép `Cells.copy(showLabels = false)`), `FolderPanelView.AppsGrid` (cùng `GridMetrics`, nên panel thư mục cũng co nhãn), Cài đặt hiển thị nhãn. Không đổi hành vi khi người dùng tắt nhãn.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — (a) cỡ bình thường: ảnh chụp Home và panel thư mục trước/sau Task 11 **giống hệt** (cùng APK trước Task 11, cùng mặt nạ kính), Cài đặt bật và tắt nhãn; (b) `wm size 1080x1150`: icon nhỏ, nhãn co tỉ lệ hoặc ẩn, không còn nhãn to bên cạnh icon nhỏ, không nhãn đè lên hàng dưới; (c) `wm size 1080x900`: nhãn ẩn, icon lấy lại phần chỗ nhãn; (d) mở panel thư mục ở cửa sổ thấp: lưới 3×3 trong panel cũng nhất quán; (e) kéo-thả, dấu "−", thư mục vẫn đúng ở cửa sổ thấp; (f) `adb shell wm size reset`, kiểm lại cỡ bình thường.
- [x] **Step 5: Commit** `fix: scale or hide home icon labels in short windows`.

---

### Task 9: Dọn, knowledgement, rebase, đóng nhánh

**Bắt buộc kiểm tay:** không. Máy: Live 4 (build xanh sau rebase, một vòng thử nhanh).
**Files:** Delete (qua sửa): `core/glass/GlassDebug.kt` và mọi chỗ gọi (grep `GlassDebug`, `GL`); Create `docs/knowledgements/GL-glass-pass.md`; Modify `docs/plans/GL-glass-pass.md` (tick, bằng chứng Review Focus, đề xuất cập nhật tài liệu chung).

- [x] **Step 1: Xóa mã debug tạm** (commit `f988ad7`: xóa `core/glass/GlassDebug.kt`, vì là file do chính nhánh tạo ở Task 1, cùng mọi chỗ đọc `gl_mask` ở `GlassEnvironment`, `GlassSurfaceFactory`, `RealGlassSurface` và log `GL` của `small()`; chính sách `GlassPolicy` và ngân sách K = 6 CLASSIC / 24 LENS / ô nhóm 8 giữ nguyên; `grep GlassDebug`, `gl_mask`, `"GL"` trong `app/src` rỗng). Quy ước gốc: `GlassDebug.kt` là file do chính nhánh tạo ra ở Task 1 (không phải file có sẵn của dự án); vẫn báo tên file + lý do cho điều phối khi báo cáo Task, theo quy ước "không tự xóa". Sau khi xóa: `grep GlassDebug`, `grep "\"GL\""` rỗng; `GlassPolicy` giữ bảng đã chốt.
- [x] **Step 2: Knowledgement** (đủ `sequenceDiagram`, `flowchart`, `stateDiagram-v2`, mục bài học; chỉ còn ghi chú "móc debug đã gỡ") (mẫu mục 8 Workflow) — có ít nhất một `sequenceDiagram` (vuốt trang → `RecyclerView.onScrolled` → `IconGridView.invalidateGlass` → `IconCellView.onDraw` → `BackdropBlurDrawable.draw`), một `flowchart` (tier + vai trò + ngân sách → `GlassMode`), một `stateDiagram-v2` (panel: FLAT ↔ STATIC_BLUR ↔ REAL theo tier đổi), mục "Bài học cho AGENTS.md".
- [x] **Step 3: Rebase lên `main`** (không cần: `origin/main` vẫn là `99b405d` = nền của nhánh; Live 4 chạy lại S2/S3i/FLAT trên build `ce213e3`, xem Task 8 và bảng bằng chứng) sau khi TL1 (và TL2) merge; xử lý xung đột; build xanh; **kiểm lại scroll listener của pager** (TL1 làm mượt chuyển trang) và hunk `HomeActivity` nếu có đúng như khai ở Vùng file; chạy lại S2 một lần trên Live 4.
- [~] **Step 4 (bảng bằng chứng và đề xuất tài liệu chung đã điền 2026-11-04; còn chờ lượt kiểm mắt của chủ dự án, điều phối review toàn nhánh và chuyển PR sang Ready, KHÔNG chuyển ở đây): Điền bảng bằng chứng Review Focus và "Đề xuất cập nhật tài liệu chung", báo điều phối review toàn nhánh, chuyển PR sang Ready.**
- [x] **Step 5: Commit** (`chore: remove temporary glass debug hooks` = `f988ad7`; knowledgement và plan đi theo các commit `docs:` của Task 7-9).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Live 4 (mặc định, đường `STATIC_BLUR`) và Oppo A3x (kính thật + `CLASSIC_GPU` giả lập bằng `debugApiLevelCap`). **Dữ liệu cần chuẩn bị:** ảnh nền nhiều chi tiết (và một ảnh rất sáng, một ảnh rất tối để thử tương phản); 12 thư mục trên 2 trang (6 mỗi trang); Library có đủ nhóm; Oppo giữ 344 app.

Danh sách chép nhanh cho chủ dự án (bản cuối, 12 mục; mỗi mục kèm Task liên quan). Chuẩn bị: ảnh nền nhiều chi tiết (và một ảnh rất sáng, một ảnh rất tối); 12 thư mục trên 2 trang; Library có đủ nhóm.

1. Home đứng yên: dock, pill "Tìm kiếm", ô thư mục là kính mờ, chữ rõ; nền mờ của dock (máy CLASSIC) mịn, không còn ô vuông sau icon. (Task 1, 3, 8)
2. Vuốt giữa hai trang chậm và nhanh: ô thư mục bám nền, không nhấp nháy; pill thành chấm rồi về ô. (Task 1, 3)
3. Nhấn giữ vào edit mode: ô rung, kính đi cùng ô; kéo một thư mục (ảnh bay có kính); kéo app đè app tạo thư mục mới; kéo app vào thư mục. (Task 1, hồi quy A1b/A3)
4. Bấm thư mục: panel kính (Oppo: kính thật khúc xạ, fade 150 ms, một khung trễ chấp nhận được), sửa tên bằng bàn phím, kéo icon ra, đóng bằng Back và chạm nền. (Task 2, 7)
5. Vuốt trái ở trang cuối: Library trượt, ô nhóm và thanh tìm kiếm bám nền; cuộn ô nhóm; mở panel nhóm rồi Back. (Task 2-4, 7)
6. Vuốt xuống giữa màn hình: lớp tìm kiếm, ô nhập là kính, gõ tiếng Việt; thanh tìm kiếm Library mở cùng lớp đó. (Task 3)
7. Cài đặt, Mức kính: Ép thấp / Ép cao / Tự động; Home tạo lại; mỗi mức đúng chế độ (`FLAT` / kính mờ tĩnh / kính thật), không crash. (Task 6, 7)
8. Cài đặt, đổi ảnh nền (cả ảnh rất sáng và rất tối): dock, ô thư mục, pill, panel đổi theo; chữ vẫn đọc được. Ghi lại nơi chữ khó đọc: ứng viên là `gl_panel_tint` `#8C000000`. (Task 1, 7, 8)
9. Xóa ảnh nền (`rm files/backdrop_image` theo AGENTS.md): mọi phần tử về nền phẳng như cũ, không crash. (Task 1)
10. Đổi sáng/tối khi panel thư mục đang mở (nợ kiểm tay G2a). (Task 2)
11. Cửa sổ thấp: `adb shell wm size 1080x1150` rồi `1080x900` (`wm size reset` để trả lại): Home co icon và nhãn (hoặc ẩn nhãn), Library co ô nhóm/thanh và vẫn cuộn. (Task 10, 11)
12. **Chia đôi màn hình thật** (kéo thanh chia lên xuống khi panel thư mục mở, khi Library mở, ở Home): không crash, kính theo đúng vị trí, bố cục co. Cuối cùng xem bằng mắt: kính mờ tĩnh mượt (không ô vuông), kính thật của dock/panel trên Oppo không lệch nền. (Task 2, 7, 10, 11)

Số đo cuối đã xong: Oppo O2 (kính thật) và O3 (`CLASSIC_GPU`, cap 32); Live 4 chạy lại S2/S3i + FLAT so với `main` (2026-11-04, đạt). Việc còn lại là mắt chủ dự án theo 12 mục trên (móc `gl_mask` đã gỡ ở Task 9, nên không còn đo theo cấu hình; dùng Cài đặt, Mức kính để thử các chế độ).

**Hồi quy:** kéo-thả/lật trang/trang tạm/"−"/menu nhấn giữ (M7); tạo, thêm, kéo ra, giải tán thư mục (G2a); app trong thư mục tìm được và mở được; Library vuốt mở/đóng và Back đúng thứ tự; pill bấm mở tìm kiếm; đổi cỡ lưới re-flow; dock kéo-thả.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** số đo: Oppo O2/O3 và Live 4 (2026-11-04, `ce213e3`) đạt hoặc đã ghi ngoại lệ (xem bảng bằng chứng). Bài kiểm mắt 12 mục của chủ dự án: chưa có.

**Bài kiểm cuối của chủ dự án (Live 4, 2026-11-04, `d10775c`):** đạt mọi mục, gồm chia đôi màn hình thật. Một lỗi còn lại, chủ dự án quyết dời sang nhánh hoạt ảnh: đổi ảnh nền trong Cài đặt rồi về Home thì kính không vẽ lại (vào edit mode thì đúng). Nguyên nhân đọc từ code: `HomeActivity.render` chỉ gọi `dockView.pulseGlassRefresh()` khi ảnh nền đổi; `StaticBlurSurface.setDynamic` không làm gì và các `BackdropBlurDrawable` (ô thư mục, pill, thanh) không được báo vẽ lại. Hướng sửa: báo mọi phần tử kính vẽ lại khi ảnh nền đổi. Điều phối kiểm thêm trên Oppo bản `d10775c`: panel kính thật đúng, không ảnh nền thì về phẳng, không crash. `wm size` bị Oppo từ chối, phần cửa sổ thấp dựa vào bài kiểm chia đôi màn hình thật của chủ dự án.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-03 | Plan | Đọc `MultiAgent-Workflow`, `AGENTS`, `project_structure`, plan M6/G2a, mã `core/glass`, `DockView`, `FolderPanelView`, `IconCellView`, `CategoryPageView`, `CategoryGroupPanelView`, `SearchPillView`, `SearchOverlayView`; đọc API `LiquidGlassView` từ AAR bằng `javap`; viết plan | (docs: add GL plan) | Chờ điều phối rà. Câu hỏi mở: xem báo cáo |
| 2026-11-03 | Plan | Chủ dự án duyệt plan, trả lời 5 câu hỏi mở (ghi ở D0); Task 7 Step 3 bỏ | — | Trạng thái: Đang code Task 1 |
| 2026-11-03 | 1 | Code `BackdropBlurDrawable`, `GlassEnvironment`+`GlassEntryPoint`, `GlassPolicy`/`GlassMode`/`GlassRole`/`GlassStyle`, `GlassBudget` lite, `GlassDebug` (tạm), `BlurredBackdropCache.small()`; ô thư mục vẽ kính tại chỗ (`IconCellView`, `IconGridView`), scroll listener ở `HomePagerAdapter`; build xanh | 4e16463 | Chờ điều phối review và đo (Step 6). Chưa tick. Vòng 1/5 |
| 2026-11-03 | 1 | Điều phối: review code Task 1 ĐẠT; chờ số đo thật. Cập nhật D10 và Step 6 theo số đo "trước" của điều phối (Janky 72.87% khi vuốt): S2/S3s chấm theo Δp50/Δp95 thời gian khung, khối lệnh thêm parse `framestats` (lấy mẫu sau mỗi lần vuốt, khử trùng `IntendedVsync`). Ghi chú: `invalidateGlass()` chỉ invalidate ô có kính và không cấp phát (giữ). Chưa tick; chưa sang Task 2 | (commit docs) | Chờ số đo Step 6. TL1 đang tìm nguyên nhân ghi display list mỗi khung; nếu sửa trong `IconCellView`/`IconGridView`/`HomePagerAdapter` thì điều phối xếp thứ tự |
| 2026-11-03 | Plan | Điều phối báo nguyên nhân Janky khi vuốt (kính thật của dock tự `invalidate()` mỗi khung cuộn, TL1 Task 2b sửa); ghi vào sự thật số 7, Task 7 Step 1b, Vùng file; hoãn phép đo Task 1 Step 6; cho phép Task 2 chạy song song | — | Task 1 chưa tick |
| 2026-11-03 | 2 | `BackgroundGlass` + nền kính tại chỗ cho `FolderPanelView` và `CategoryGroupPanelView` (FLAT giữ `bg_folder_panel`), style panel tint tối `gl_panel_*`; build xanh | d761af5 | Chờ điều phối review. Chưa tick. Vòng 1/5 |
| 2026-11-03 | 2 | Điều phối: review code ĐẠT, tick Task 2 (hình ảnh trên máy vào bài kiểm cuối) | — | Vòng 1/5 đạt |
| 2026-11-03 | 3 | Đổi `PanelGlass` -> `BackgroundGlass` (nhận `flatRes` + bán kính); kính tại chỗ cho ô nhập M8b (`search_field_box`, id mới), thanh tìm kiếm Library (+ ghi đè `CategoryPageView.setTranslationX` để thanh bám nền khi Library trượt), pill M8c (vẽ trong `onDraw`, không đo lại); style `gl_bar_*` tint 50% đen; build xanh | a032f93 | Chờ điều phối review. Chưa tick. Vòng 1/5 |
| 2026-11-03 | 3 | Điều phối: review code ĐẠT, tick Task 3. Ghi chú điều phối: `library_view` chỉ dịch bằng thuộc tính `translationX` (ValueAnimator của `LibraryOverlayController`), không bằng `ViewPropertyAnimator`; TL1 đổi `home_content` thành `topReserve` hằng số (layout đổi thì view vẽ lại, đúng) | — | Vòng 1/5 đạt |
| 2026-11-03 | 4 | Ô nhóm Library: `CategoryPageView.addTile` gắn `BackdropBlurDrawable` làm nền khung 2x2 (`category_tile`) cho tối đa `GlassBudget.maxLiteGlassTiles` ô đầu (còn lại giữ `bg_category_tile`), danh sách `glassTiles` dựng lại mỗi `render()`; `ScrollView` scroll listener + `setTranslationX` (ghi đè sẵn từ Task 3) báo vẽ lại; listener `GlassEnvironment` render lại khi mức kính đổi; build xanh | 639a10d | Chờ điều phối review. Chưa tick. Vòng 1/5. Đo thiết kế ở Task 5 (S4) |
| 2026-11-03 | 4 | Điều phối: review code ĐẠT, tick Task 4. Task 5-8 phụ thuộc ma trận đo nên dừng code, chờ TL1 merge rồi rebase, build lại, và một buổi trên máy gộp Task 1 Step 6 + Task 5 (điều phối dựng sẵn thư mục trên Live 4) | — | Vòng 1/5 đạt. Chờ điều phối báo TL1 đã merge |
| 2026-11-03 | Rebase | `git rebase origin/main` sạch (12 commit, không xung đột), build xanh. TL1/TL2 đã merge; đọc lại `TunedPagerSnapHelper`/`PagerFlipAnimator` (vẫn phát `onScrolled`), `topReserve`, `ICON_SCALE` 0.9; ghi vào sự thật số 8. Khối lệnh đo viết lại: tự tìm ô thư mục bằng `uiautomator dump`, thêm S1/S4/S5/S6, `$Part` | (xem push) | `--force-with-lease` push sau rebase |
| 2026-11-03 | Plan | Thêm Task 10 (Library co theo cửa sổ) và Task 11 (nhãn Home co theo icon) + D11, D12 theo yêu cầu của chủ dự án từ bài kiểm TL1 | (xem push) | Chờ điều phối rà plan bổ sung; chưa code Task 10-11 |
| 2026-11-03 | Plan | Điều phối rà vòng 1/5: Task 11 duyệt; Task 10 đổi quy tắc co từ "fit-all" sang hệ số tham chiếu `s = clamp(min(1, W/REF_W, H/REF_H), FLOOR, 1)` (đúng quyết định chủ dự án, và `s = 1` ở toàn màn hình); script thêm `$Pages` (điều hướng tới trang thật cuối bằng `$Pages − 1` lần vuốt, không giả định 2 trang); ghi mức kính phiên đo = `Tự động` tạm | (xem push) | Chờ điều phối duyệt Task 10 sửa; chưa code Task 10-11 |
| 2026-11-03 | 10 | `LibraryLayoutMetrics` (hàm thuần, `core/ui`): `s = clamp(min(1, W/360, H/640), 0.6, 1)` và `scaled()` có sàn; `CategoryPageView.computeSizes()/applyChrome()` tính cỡ ô, khe, đệm, icon nhỏ, nhãn, bo góc, thanh tìm kiếm, vùng cuộn theo `s` (vùng nội dung hẹp lại và căn giữa khi `s < 1`); `render()` tính lại mỗi lần, `onSizeChanged` theo cả chiều cao, `setPadding` ghi đè để dựng lại khi chiều cao khả dụng đổi; `s = 1` đặt lại đúng giá trị `dimens` cũ; build xanh | 05a7f84 | Chờ điều phối review. Chưa tick. Vòng 1/5. Chưa kiểm trên máy (ảnh chụp trước/sau ở bước kiểm) |
| 2026-11-03 | 10 | Điều phối: review code ĐẠT, tick Task 10 (kiểm trên máy sau phiên đo) | — | Vòng 1/5 đạt |
| 2026-11-03 | 11 | `GridMetrics.measure`: tính icon với nhãn đầy đủ; `>= LABEL_FULL_ICON_DP` (44dp) thì y hệt cũ, nhỏ hơn thì `labelScale = icon/44dp`, `< LABEL_MIN_SCALE` (0.8, chữ dưới 8sp) thì ẩn nhãn và tính lại icon không nhãn, ngược lại tính lại icon với nhãn/khe đã co (một lượt); `Cells` thêm `labelScale`, `innerGapPx`, `showLabels` hiệu lực; `IconCellView` co chữ + bóng theo `labelScale` (đặt trước khi dựng `StaticLayout`) và dùng `cells.innerGapPx`; script đo: sửa guard `'^\d+,'`, `Enter-Edit` (motionevent) + `$appX/$appY`, `$out` cấu hình được; build xanh | bc7c425 | Chờ điều phối review. Chưa tick. Vòng 1/5. Chưa kiểm trên máy |
| 2026-11-03 | 11 | Điều phối: review code ĐẠT, tick Task 11 là "code đã qua review" (kiểm trên máy sau phiên đo). Dừng, chờ kết quả phiên đo | — | Vòng 1/5 đạt. Chờ điều phối gửi số đo |
| 2026-11-03 | 5 / 1 | Nhận kết quả phiên đo Live 4 (48/60 lượt, `all` thiếu S3s/S4/S5/S6): vuốt đạt D10 ở mọi cấu hình, S3i không đạt ở K = 6 và `all` (chi phí ở RenderThread `IssueDraw→Swap`, không phải ghi display list), S1 kính tốt hơn phẳng, S5 hợp lệ nhưng ngắn (panel nhóm không hoạt hoạ). Ghi bảng + phân tích vào Task 5, đề xuất P1-P4 vào Task 6 (chọn P2: ô thư mục FLAT trong edit mode trên CLASSIC, K = 6) | (xem push) | Chờ điều phối/chủ dự án duyệt đề xuất; chưa code Task 6 |
| 2026-11-03 | 6 | Điều phối chọn thử P3 trước, P2 làm lùi (Task 6 tách 6a/6b). Đo bổ sung đang chạy ở điều phối (`all` × S3s: Janky 4.73/5.56/4.78%, tương đương `k6`, nên các phần tử khác không thêm chi phí đáng kể ở S3s) | — | — |
| 2026-11-03 | 6a | `BackdropBlurDrawable`: tint gộp vào `fillPaint` bằng `PorterDuffColorFilter(tint, SRC_OVER)` (bỏ `tintPaint`), bỏ viền khi `owner.rotation != 0f`: ô rung chỉ còn 1 lệnh vẽ (từ 3); build xanh | ff2699c | Chờ điều phối review + đo lại S3i/S3s `k6`/`all`. Chưa tick. Vòng 1/5 |
| 2026-11-03 | 6a | Điều phối: review ĐẠT và đo lại ĐẠT (`k6a` S3i 0.46%, `all6a` 0.52% so với nền 0.49%; S3s 2.12 / 2.02%); tick 6a, bỏ 6b. Ghi kết quả đo, các phần thiếu (S4 đạt, S5 không kết luận, S6 +3.4 điểm cần ghép cặp, pill = 0), cảnh báo trôi máy, đính chính S1 | — | Vòng 1/5 đạt |
| 2026-11-03 | 6 | `GlassBudget`: ô thư mục 6/trang trên CLASSIC (đo), 24 trên LENS (chưa đo); ô nhóm 8; `GlassPolicy`/`GlassBudget` ghi chú nguồn số đo; không có phần tử nào về FLAT; build xanh | 17bde88 | Chờ điều phối review. Chưa tick. Đề xuất S6 ở trên chờ quyết |
| 2026-11-03 | 6 | Điều phối: review ĐẠT, tick Task 6. S6 ghép cặp đạt (+1.87 điểm so với nền 15.6%, Δp95 +0.3 ms); `SEARCH_BAR` giữ `STATIC_BLUR`, không đổi code | — | Vòng 1/5 đạt |
| 2026-11-03 | 10 (vòng 2) | Điều phối kiểm ảnh chụp: Home bình thường giống hệt `main`; thư viện lệch ở gợi ý thanh tìm kiếm (font scale 1.15). Sửa: ở `s >= 1` không đụng cỡ chữ (thanh, nhãn, "+N"), ở `s < 1` lấy cỡ chữ XML của chính view nhân `s`; build xanh | facc271 | Vòng 2/5. Chờ điều phối chạy lại ảnh chụp. Ghi chú cho chủ dự án: trong cửa sổ thấp (`wm size`) pill và dock giữ cỡ đầy đủ, trông lớn so với icon nhỏ: có thể làm sau, ngoài phạm vi |
| 2026-11-03 | Oppo | Soạn chuẩn bị phiên Oppo: `$Part='OPPO'`, tự tìm ô app thường, pha O1-O3, `gl_mask` trường thứ tư `forceLite` (Task 7 Step 0) | — | Chờ Oppo được cắm |
| 2026-11-03 | 10, 11 | Điều phối kiểm lại trên máy (build `6ef6581`): Home và App Library giống hệt `main` ở cỡ bình thường (0 px lệch trong vùng app, chỉ nút điều hướng hệ thống lệch ~1 px); cửa sổ thấp mô phỏng đúng ý. Tick Task 10, 11 là đã kiểm trên máy; ghi bảng bằng chứng | — | Vòng 2/5 đạt cho Task 10. Chờ Oppo |
| 2026-11-03 | 5 (Oppo O1) | Nhận kết quả pha O1 (Oppo, 3 lượt): panel/Library/ô nhóm/thanh/pill đạt; S2 (+1.7 đến +1.9 điểm, `rec` p50 0.76 → 4.7 ms) và S3s (+2.35 điểm) không đạt, `k6` tái hiện nên do ô thư mục khi cuộn; simpleperf: `IconCellView.onDraw` 21.4% (`drawFolder` 11.1%, `BackdropBlurDrawable.draw` 6.7%), 80% mẫu ở trình thông dịch (APK debug). Ghi bảng + gốc rễ; script: chờ 12 s, thử lại 5 lần, dừng nếu không thấy thư mục; đề xuất Task 6c (bộ đệm bitmap nội dung ô thư mục + đường vẽ gọn) | (xem push) | Chờ điều phối duyệt 6c; chưa code |
| 2026-11-03 | 6c | Điều phối duyệt phương án A. Code: bộ đệm bitmap nội dung ô thư mục khi có kính (huỷ ở bind/bindFolder/onSizeChanged/detach/tắt kính), `BackdropBlurDrawable` shader cố định + `canvas.translate`; FLAT giữ đường cũ; build xanh | bf987b1 | Chờ điều phối review + đo lại (Oppo S2/S3s `k6`/`all`/`off`/`off2`, Live 4 S2/S3i). Chưa tick. Vòng 1/5 |
| 2026-11-03 | 6c | Điều phối: review ĐẠT và đo Oppo ĐẠT (S2 `k6`/`all` +0.55 điểm so với nền 0.98%, `rec` p50 4.7 → 2.1 ms; S3s +0.31 điểm); Step 0 (AOT) không loại bỏ chi phí. Tick 6c; Live 4 và bằng mắt còn lại | — | Vòng 1/5 đạt |
| 2026-11-03 | 7 | Hai panel kính thật trên LENS: `GlassPolicy` REAL cho `FOLDER_PANEL`/`GROUP_PANEL` ở LENS, `GlassDebug.forceLite`, `GlassSurface.setTint`, `RealGlassSurface` (tint, `collectFrameStats = false`), `BackgroundGlass` REAL (con đầu tiên của host, `layoutGlass`, xung nhịp 1 giây, chặn `invalidate()` còn lại), hook ở hai panel; preset script `'O2'`; build xanh | 400aef2 | Chờ điều phối review + pha O2 (Oppo): `lite` / `real` / `lite2` × S1, S5, S2, S3i, S6. Chưa tick. Vòng 1/5 |
| 2026-11-03 | 7 (vòng 2) | O2 `real` không đạt D10 (S1 p95 +5/+9 ms, ~1800 khung dư mỗi 20 lần mở do xung nhịp động 1 giây); dock `collectFrameStats = false` không đổi, giữ. Đọc bytecode: kính lấy nền ở mỗi lần vẽ, chế độ động = vòng `invalidate()` liên tục. Sửa: bỏ xung nhịp, `captureOnce()` một lần sau khi hiện (+ cuối hoạt họa hiện của panel thư mục); build xanh | f2eea97 | Vòng 2/5. Chờ điều phối đo lại O2 (`lite`/`real`/`lite2` × S1, S5) |
| 2026-11-03 | 7 (vòng 2, kết quả) | Đo lại O2 vòng 2 (Oppo, `0155b95`): `r2_real` S1 Janky 2.74% (20.3 khung) so với `r2_lite` 0.59% (4.3), p95 23.3 so với 14.7 ms; số khung bằng nhau (xung nhịp đã hết) nhưng **vẫn không đạt**: đúng 1 khung Janky mỗi lần mở. Ghi bảng + phân tích giai đoạn (chi phí ở `sync`/GPU, p95 `sync` 0.94 → 5.9 ms, `swap` 5.3 → 13.9 ms) và 6 hướng cho vòng 3 (pre-warm giá trị thấp theo bytecode; đề xuất thử bỏ lớp alpha của host + giảm công việc GPU của kính) | (docs) | Chủ dự án nghỉ hôm nay. Dừng, chờ điều phối; chưa code vòng 3 |
| 2026-11-04 | 7 (vòng 3) | Code hai thử nghiệm rẻ, bật riêng bằng trường thứ năm `variant` của `gl_mask` (mặc định 0 = như cũ): bit 0 = `FolderPanelView` hiện/ẩn tức thì khi dùng kính thật (không mờ dần `alpha` của host, lớp tối `folder_scrim` hiện cùng lúc, panel nhóm vốn không có fade); bit 1 = `RealGlassSurface.setLite` chỉ cho hai panel: tắt dispersion + sensor highlight, `edgeSoftness` 0 (3 mẫu thay vì 9 mẫu/điểm ảnh ở vành), `blurAmount` 0 (bán kính mờ 6 -> 4 px), `bevelWidth` 24 px, `refractionHeight` 80 px; dock không đổi. `127 24 0 0 0` = hiện tại, `... 0 0 1` = tùy chọn 3, `... 0 0 2` = tùy chọn 4, `... 0 0 3` = cả hai | `43ed813` | Build xanh, chưa cài/đo (điều phối đo S1 trên Oppo). Cả hai là cờ tạm, gỡ ở Task 9 |
| 2026-11-04 | 7 (vòng 3, kết quả) | Đo vòng 3 (Oppo, `e7d9021`, S1): không biến thể nào khử được khung chậm (~20 khung Janky/20 lần mở ở cả 4 biến thể; `lite` 1.7). Chủ dự án quyết: giữ kính thật + fade 150 ms + đủ hiệu ứng, chấp nhận ngoại lệ D10. Gỡ mã vòng 3 (`variant`, `variantBit`, `setLite`, `skipHostFade`, hiện/ẩn tức thì), ghi D10, plan và knowledgement, tick Task 7 | `f7162c3` | Vòng 3/5 đóng |
| 2026-11-04 | 7 (lỗi mới) | Kính mờ tĩnh bị vỡ ô trên panel lớn (ảnh Oppo của điều phối). Nguyên nhân (suy từ mã, chưa đo lại trên máy): `BlurredBackdropCache.small()` thu thẳng xuống 8% bằng một lần `scale` bilinear (không mipmap, chỉ lấy 4 trên 156 điểm ảnh = răng cưa) rồi GPU phóng ~12x. Sửa: thu bằng các bước chia đôi xuống 25% (trần 640 px cạnh dài) + box blur 3 lượt bán kính 2 một lần mỗi ảnh nền; mỗi khung vẽ không đổi. `blur()` (dock `StaticBlurSurface`) giữ nguyên. Bộ nhớ: bản nhỏ ~66 KB -> ~650 KB trên nguồn 1080x2400 (~230 KB trên 720x1280), cộng ~1.3 MB tạm lúc dựng một lần | (commit này) | Cần mắt chủ dự án xác nhận trên Oppo/Live 4 (`gl_mask 127 24 0 1`) |
| 2026-11-04 | 8 (phần mã) | `GlassDebug.cap` áp thật: `applyApiCap` cho hai phép dò tier và cho `LiquidGlassView` của dock (thuộc tính theo từng view), `ensureLoaded` chạy trước phép dò; bỏ trống/0 = tắt. Ước tính tương phản tính tay (xem Step 2). Hồi quy (Step 3) và tinh chỉnh tint chờ máy | (commit này) | Lệnh O3: `0 0 32 0` (trước) và `127 6 32 0` (sau), S1/S2/S3i/S4; chưa cài, chưa đo |
| 2026-11-04 | 8 (O3) + blur | O3 đạt trên Oppo (`3b9a2dd`, cap 32, 3 lượt): Janky tăng tối đa +0.14 điểm, p95 +2.7 ms (S2); bỏ lặp `o3_off2` vì nền khớp phiên trước; tick Step 1 (`CLASSIC_GPU` chưa đo trên máy thật). Chủ dự án xác nhận bằng mắt `small()` mượt trên Oppo. Thêm log `GL` đo thời gian `small()` (tạm); thêm dòng kiểm tương phản và danh sách 12 mục bài kiểm cuối; hoàn thiện knowledgement trừ số đo lại Live 4 | (commit này) | Task 8 Step 2-3 chờ lượt kiểm cuối; Task 9 chưa bắt đầu |
| 2026-11-04 | 8 (Step 1b) | `small()` dựng ngoài main thread (78.8 ms trên Oppo, vượt 30 ms): `smallOrRequest` + luồng `GlassBlur`, một lần dựng cho nhiều ô, bỏ kết quả cũ khi đổi ảnh nền, `BackdropBlurDrawable` vẽ phẳng rồi `invalidate` khi xong, hủy khi `detach()`; build xanh | (commit này) | Giữ log `GL`; chờ đọc số đo trên Oppo và Live 4 |
| 2026-11-04 | 8 (dock blur) | Dock `StaticBlurSurface` (CLASSIC) lộ ô vuông mờ sau icon (cùng lỗi răng cưa của `blur()`). Sửa: gỡ `BlurredBackdropCache.blur()` (thu 8% một bước + phóng lại cỡ nguồn NGAY trên main thread trong `onDraw`); dock dùng bản nhỏ `smallOrRequest` (chia đôi + box blur, dựng ngoài main thread): `StaticBlurSurface` giữ `Rect` nguồn trong bản nhỏ và vẽ phóng bằng bilinear (`FILTER_BITMAP_FLAG`), tới khi xong chỉ vẽ tint + viền rồi `invalidate` qua `SmallListener`. Hết bitmap cỡ nguồn (~4 MB trên 1080x2400) và hết `createBitmap` mỗi lần tính vùng cắt; không thêm stall main thread (còn bớt đi stall cũ của `blur()`) | (commit này) | Chờ mắt chủ dự án trên Live 4 (AUTO) |
| 2026-11-04 | 8 (Live 4) + 9 | Ghi Live 4 chạy lại (`ce213e3`): S2/S3i đạt, FLAT giống từng điểm ảnh, `small()` 22.7 ms trên `GlassBlur`. Dọn Task 9: xóa `GlassDebug.kt` và mọi chỗ đọc `gl_mask`, bỏ log `GL`; `blur()` cũ đã gỡ ở commit dock; build xanh; không cần rebase (`main` vẫn `99b405d`). Điền bảng bằng chứng (dòng 3, 4, 6, 7, 11, 12), danh sách bài kiểm, đề xuất tài liệu chung | `f988ad7` + commit docs | PR vẫn Draft; chờ lượt kiểm mắt của chủ dự án và review toàn nhánh |
| 2026-11-04 | 7 (review cuối) | Chặn: `FORCE_HIGH` trên Live 4 (API 30) làm panel kính thật qua đường CPU, S1 Janky 30.7%, p95 112 ms. Sửa: `isRealGlassCapable` (API 33+), `GlassPolicy.modeFor` và `GlassBudget.maxLiteGlassCellsPerPage` nhận `realGlassCapable`; panel về kính mờ tĩnh và K = 6 khi máy thiếu đường Lens, dock giữ nguyên; ghi Task 7, bằng chứng dòng 13, đề xuất bài học AGENTS.md | (commit này) | Chờ đo lại Live 4 `FORCE_HIGH` |
| 2026-11-04 | Đóng nhánh | Chủ dự án kiểm cuối đạt; lỗi đổi ảnh nền dời sang nhánh hoạt ảnh (quyết định của chủ dự án); điều phối kiểm thêm trên Oppo bản `d10775c`, rà toàn nhánh, merge | (docs) | Xong |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Lệch nền (vuốt, rung, Library, cuộn, ảnh kéo) | Kiểm bằng mắt riêng chưa ghi nhận; số đo S2/S3s/S4 (Live 4) chứng minh `invalidateGlass()` chạy mỗi khung cuộn (rec p50 0.46 → 0.84 ms ở S2). Còn lại gộp vào bài kiểm cuối | Một phần (số đo); mắt: chờ bài kiểm cuối |
| 2 | Chi phí lúc vuốt, rò listener | Live 4 chạy lại 2026-11-04 (build `ce213e3`, kính Tự động, máy nguội 31-32 °C, một phiên, 3 lượt): S2 và S3i ĐẠT (bảng ở Task 8). Live 4, 2026-11-03: S2 12 ô kính cùng lúc Δp50 −0.4 / Δp95 −0.6 ms (đạt); rung k6 sau 6a Janky 0.46% so với nền 0.49%; S4 +1.45 điểm, S6 ghép cặp +1.87 điểm. Rò listener: chỉ được code bảo vệ (gỡ ở `onDetachedFromWindow`/`onDetachedFromRecyclerView`), chưa ai kiểm tay | Đạt (chi phí); rò listener: chỉ code bảo vệ |
| 3 | Ảnh nền đổi / chưa có ảnh | Đọc mã: chưa có ảnh (`drawable == null`) thì `BackgroundGlass.apply()` chọn kính mờ tĩnh/phẳng và `onShown()` chọn lại; ảnh đổi thì `BackdropBlurDrawable` đổi danh tính drawable, bỏ shader, xin bản nhỏ mới (vẽ phẳng tới khi dựng xong, ngoài main thread). Mắt: bài kiểm cuối mục 8, 9 | Một phần (mã); mắt: chờ bài kiểm cuối |
| 4 | Vòng đời (`recreate()` khi panel mở, tier đổi, split-screen) | Listener `GlassEnvironment` gỡ ở `onDetachedFromWindow`; `BackgroundGlass.detach()` hủy lời xin bản nhỏ; cache chỉ giữ tham chiếu yếu tới listener. Tier đổi (Ép thấp/Ép cao) đi qua `GlassEnvironment.addListener`. Mô phỏng cửa sổ thấp đạt (dòng 10). Mắt: bài kiểm cuối mục 7, 10, 12 | Một phần; split-screen thật + `recreate()`: chờ chủ dự án |
| 5 | FLAT y hệt `main` | Điều phối, Live 4, `font_scale` 1.15, `gl_mask 0 0` (build `6ef6581`, lặp lại trên build `ce213e3`, 2026-11-04, cùng kết quả) so với `main` `99b405d`, cùng bố cục: Home và App Library giống từng điểm ảnh trên toàn vùng ứng dụng (y 110-2220, 0 px vượt ngưỡng); chỉ nút điều hướng hệ thống lệch ~1 px (SystemUI, không thuộc app) | Đạt (Live 4, cỡ bình thường) |
| 6 | Tương phản | Ước tính tay (chưa nhìn): panel kính mờ tĩnh = ảnh nền mờ phủ `gl_panel_tint` `#66000000`, chữ trắng trên ảnh trắng ~2.9:1 (dưới 4.5:1); thanh/pill `#80000000` ~4.0:1. Không đổi màu. Ứng viên: `gl_panel_tint` `#8C000000` | Chờ mắt chủ dự án (bài kiểm cuối mục 8) |
| 7 | Panel kính thật (fade, bàn phím, dock sau `collectFrameStats`) | Oppo S1: `real` so với `lite` còn đúng 1 khung ~24 ms mỗi lần mở (Janky +2.15 điểm, p95 +8.6 ms); vòng 3 (bỏ lớp alpha của host, kính nhẹ) không khử được, chủ dự án chấp nhận ngoại lệ D10, giữ fade 150 ms và đủ hiệu ứng. Dock sau `collectFrameStats = false`: S2 1.55% so với 1.53% trước, không đổi đo được. Fade/bàn phím bằng mắt: chờ | Đạt (số đo, ngoại lệ D10); mắt: chờ bài kiểm cuối mục 4 |
| 8 | Rebase TL1 | Rebase sạch lên `main` (TL1, TL2 đã merge); `HomeActivity.kt` và `activity_home.xml` không có trong diff so với `main` (hunk dự phòng không dùng); `HomePagerAdapter.glassScrollListener` vẫn nhận cuộn với `TunedPagerSnapHelper`/`PagerFlipAnimator` (số đo `rec` ở S2 cho thấy `invalidateGlass()` chạy) | Đạt (Live 4) |
| 9 | Hồi quy kéo-thả | Edit mode vào/ra bằng kéo-thả tại chỗ trong khối lệnh đo (S3i/S3s) chạy được trên cả bản có kính; hồi quy đầy đủ nằm trong bài kiểm cuối | Một phần; chờ bài kiểm cuối |
| 10 | Cửa sổ thấp (Task 10, 11) | Live 4, `wm size 1080x1150` và `1080x900`, đã đặt lại sau khi kiểm: nhãn Home ẩn và icon co; ô nhóm Library co (~0.6), giữ kính, danh sách cuộn được; cỡ bình thường giống hệt `main` (xem dòng 5). Ghi chú cho chủ dự án: pill và dock giữ cỡ đầy đủ trong cửa sổ thấp, trông lớn so với icon nhỏ (có thể làm sau, ngoài phạm vi) | Đạt (mô phỏng); split-screen thật: chờ chủ dự án |
| 11 | CLASSIC_GPU (Task 8) | O3 Oppo (`3b9a2dd`, `debugApiLevelCap` 32, 3 lượt): Janky tăng tối đa +0.14 điểm, p95 +2.7 ms (S2), đạt D10 ở S1/S2/S3i/S4. Không đại diện máy Android 12 thật | Đạt (giả lập); máy thật: chưa đo |
| 12 | Kính mờ tĩnh mượt (`small()` + dock) | `small()` thu bằng chia đôi + box blur, dựng ngoài main thread (Oppo 78.8 ms trên main trước đó; Live 4 `720x899 -> 180x224` 22.7 ms trên `GlassBlur`); dock `StaticBlurSurface` dùng cùng bản nhỏ, hết `blur()` cỡ nguồn. Mắt: panel mượt trên Oppo và Live 4 (`blur-before-after.png`, chủ dự án) | Đạt (panel); dock: chờ mắt sau sửa |
| 13 | Tier từ override không phải khả năng (`FORCE_HIGH` trên API < 33) | Live 4, `FORCE_HIGH`, `e4cba37`: panel kính thật qua đường CPU S1 Janky 30.7%, p95 112 ms. Sửa: `GlassPolicy`/`GlassBudget` nhận `realGlassCapable` (API 33+); panel về kính mờ tĩnh và K = 6 trên máy không có đường Lens, dock giữ kính thật | Đã sửa; chờ đo lại Live 4 `FORCE_HIGH` |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#10 và TT#11 bỏ câu "nền phẳng, chưa kính" và ghi giới hạn thật sau Task 6 (phần tử nào còn FLAT trên tier nào); TT#9 dòng "Còn lại: pill dùng kính" xóa; TT#6 thêm các phần tử kính mới và `CLASSIC_GPU` chưa đo trên máy thật (nếu vẫn vậy).
- `project_structure.md`: mục 5 "Lớp kính" thêm `BackdropBlurDrawable`, `GlassPolicy`, `GlassEnvironment`, `GlassMode`, `GlassRole`, `GlassStyle` và `GlassBudget` mở rộng; cây `core/glass` (mục 3) cập nhật; bảng mốc dòng "Áp kính" đổi thành ✅ kèm link plan; R3 thêm số đo Janky mới; dòng nói "pill chưa dùng kính" ở mục M8c sửa; bỏ "ResolveGlassPlanUseCase" khỏi danh sách chưa làm nếu `GlassPolicy` thay thế (hỏi chủ dự án).
- `room-schema.md`: không đổi.
- `LiquidGlass-Android.md` (đọc `javap` v2.0.11, 2026-11-04): `debugApiLevelCap` là thuộc tính của TỪNG `LiquidGlassView`, không toàn cục; đường Lens: bán kính mờ = (4 + `blurAmount` x 32) x `material.blurScale`; `edgeSoftness` > 0 làm shader lấy 9 mẫu thay vì 3 ở vành khúc xạ; dispersion chỉ đổi lượng khúc xạ từng kênh, không đổi số mẫu; kính lấy nền ở MỖI lần vẽ, `enableDynamicBackground = true` gọi `invalidate()` sau mỗi lần vẽ (vòng liên tục).
- `Launcher-Features.md` (thêm): hai panel là kính thật khúc xạ trên tier LENS với một khung trễ mỗi lần mở được chủ dự án chấp nhận (ngoại lệ D10); `CLASSIC_GPU` chạy thử trên Oppo bằng giả lập API, chưa đo trên máy thật; kính mờ tĩnh (ô, panel, thanh, pill, dock CLASSIC) dùng bản mờ nhỏ dựng ngoài main thread.
- `project_structure.md` (thêm): `BlurredBackdropCache` chỉ còn `smallOrRequest`/`cancel`/`SmallListener` (luồng `GlassBlur`), `StaticBlurSurface` vẽ phóng một `Rect` của bản nhỏ; `GlassDebug` đã xóa; `GlassBudget` ngân sách K = 6 CLASSIC, 24 LENS, ô nhóm 8.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement (thêm: `Bitmap.scale` thu mạnh không phải blur; `debugApiLevelCap` theo view; việc nặng một lần phải ra khỏi main thread; so cấu hình bằng số khung Janky tuyệt đối khi số khung khác nhau; số đo Live 4 trôi theo nhiệt độ). Thêm bài học: **tier suy từ override không phải khả năng** (`FORCE_HIGH` cho tier LENS trên máy API 30; kiểm khả năng thật bằng API hiệu lực của thư viện trước khi cấp thứ chỉ đo/đúng trên đường Lens; giữ hàm chính sách thuần bằng cách truyền khả năng vào). Dự kiến: số đo mới (Janky trước/sau từng phần tử, mỗi máy), bẫy "kính view anh em không xoay theo `Jiggler` và không có trong ảnh kéo", cách đo bằng `adb shell input` lặp lại, giới hạn đo `CLASSIC_GPU`.
