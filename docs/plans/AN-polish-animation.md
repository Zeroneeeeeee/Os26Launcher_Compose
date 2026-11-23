# AN — Hoạt ảnh kiểu iOS (mở thư mục/panel từ icon, trượt icon, nhấn co, ô Library, kính khi kéo) + sửa lỗi kính không vẽ lại khi đổi ảnh nền — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 và Oppo A3x (nhánh này cần CẢ HAI máy vì rủi ro chính là kính thật của panel trên Oppo). Lệnh viết cho CMD/PowerShell. **Không sửa `HomeActivity.kt` và `activity_home.xml` cho tới khi R1 merge** (xem mục "Phase A / Phase B").

**Nhánh:** `feature/an-polish-animation` · **Worktree:** `E:\SourceTree\IosLauncher-wt\AN-polish-animation` · **PR:** #6 (draft) · **Bắt đầu:** 2026-11-04 · **Plan duyệt:** 2026-11-04 (chủ dự án)
**Trạng thái:** Mã Task 1–11 xong ; đã đo trên Live 4 và Oppo và chủ dự án kiểm bằng cảm giác (2026-11-05), không có bản ghi kịch bản từng ý; Task 9g đóng (chủ dự án xác nhận); Task 9h đóng không sửa (chấp nhận, nguyên nhân chưa tìm ra); Task 12 còn mở: chuyển PR sang Ready for review, merge và đồng bộ tài liệu chung (điều phối)
**Khóa:** cần Oppo + Live 4 · `core/ui/DragHelper.kt`, `core/ui/DragDropTarget.kt` (độc quyền, đã giao) · **xin xác nhận thêm:** `core/glass/*` (độc quyền theo mục 5.2; plan sửa 4 file nhỏ, xem "Vùng file") · không khóa schema

**Goal:** Chuyển động kiểu iOS cho Home và App Library: (1) thư mục mở ra từ chính icon của nó và đóng thu về icon; (2) panel nhóm của Library mở từ ô nhóm và đóng về ô; (3) icon trượt vào chỗ mới khi di chuyển/thả app hoặc thư mục; (4) phần tử kính (trừ dock, đã có hiệu ứng của thư viện) co nhẹ khi đang giữ; (5) ô nhóm Library: bấm icon lớn mở app ngay, bấm cụm nhỏ mới mở panel; (6) khi kéo icon lên icon khác để tạo thư mục: hiện một lớp kính phía sau icon đích, to hơn icon một chút, bỏ hiệu ứng phóng icon cũ; kéo lên thư mục có sẵn thì thư mục phóng ngay và không tạo kính mới; (7) sửa lỗi dời từ GL: đổi ảnh nền trong Cài đặt rồi về Home thì mọi phần tử kính phải vẽ lại. Mọi chuyển động là **spring** (AndroidX DynamicAnimation) và **ngắt được giữa chừng**: bấm lại hoặc đóng khi đang chạy thì đảo chiều từ vị trí và vận tốc hiện tại.

**Architecture:** Một lớp nhỏ `SpringProgress` (core/ui) bọc `SpringAnimation` với một số thực 0..1; mỗi view sở hữu spring của mình và đặt **đích** theo dữ liệu (`openFolderId` / `openCategoryGroup` vẫn ở `HomeViewModel`, view chỉ đuổi theo, đúng mẫu "trạng thái ổn định ở ViewModel, tiến độ giữa chừng ở view" của B2 trong AGENTS.md). Panel không đổi layout: nó được laid-out ở chỗ cuối cùng rồi co/dịch bằng thuộc tính transform từ hình chữ nhật của icon/ô gốc; hình chữ nhật gốc view tự tìm (sổ đăng ký ô thư mục, `library_view`), nên **không cần `HomeActivity`**. Kính tại chỗ (`BackdropBlurDrawable`) được dạy cắt nền đúng khi view bị scale; kính thật của panel có chế độ lùi (kính mờ tĩnh trong lúc chuyển động, kính thật khi dừng) quyết định bằng phép đo ở Task 3. Kính hover khi tạo thư mục là một `BackdropBlurDrawable` do `IconGridView` vẽ trong `dispatchDraw` (trước các con), không thêm view. Icon trượt dùng "FLIP" (ghi vị trí nhìn thấy trước khi layout, spring về 0 sau layout) trong `IconGridView`/`DockView`, ảnh bay lúc thả có hiệu ứng "hạ cánh" do `DragLayer` thực hiện, trạng thái "đang hạ cánh" nằm ở `DragHelper` (object, không nằm trong view bị tái dùng).

**Tech Stack:** View/XML, `androidx.dynamicanimation:dynamicanimation:1.1.0` (dependency mới duy nhất; số version **đọc thật** từ `https://dl.google.com/android/maven2/androidx/dynamicanimation/dynamicanimation/maven-metadata.xml` ngày 2026-11-04: `release` = `1.1.0`, cập nhật 2025-04-09, không có bản mới hơn; không dùng `dynamicanimation-ktx`), thư viện kính `liquidglass:v2.0.11` (đã ghim). Không Compose, không Room.

**Spec:** chưa có TT# riêng trong `Launcher-Features.md` (AN là mã Claude đặt, `MultiAgent-Workflow.md` mục 9); phạm vi là các quyết định của chủ dự án 2026-11-04 (bảng "Quyết định đã chốt"). Tài liệu liên quan: `plans/G2a-folders-library.md` (D4/D5: gộp trước, mép giữ ≥ 500 ms mới đẩy-dồn; D13 panel nhóm; D17 lớp phủ Library), `knowledgements/GL-glass-pass.md` (kính tại chỗ, bộ đệm ô thư mục, panel kính thật), `knowledgements/TL1-home-polish.md`.

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật, có nhãn nguồn)

1. **Hiện không có hoạt ảnh nào ở các chỗ cần** (đọc code): `FolderPanelView.render` mờ dần 150 ms (`FADE_MS`, toàn bộ view gồm cả nền tối); `CategoryGroupPanelView.render` chỉ đổi `visibility`, **không có fade** (khác mô tả trong đề bài: GL ghi "hiện/ẩn không hoạt hoạ"); icon bị đẩy-dồn khi kéo dịch bằng `translationX/Y` đặt thẳng (`IconGridView.applyPreview/clearPreview`, `DockView.applyPreview`), nên nhảy cóc; sau khi thả, `onLayout` đặt ô ở vị trí mới ngay và `DragLayer.endDrag()` xoá ảnh bay tức thì; hiệu ứng gộp hiện nay là `animate().scaleX/Y(1.12f)` 120 ms trên chính ô đích (`FOLDER_INTENT_SCALE`, `IconGridView` dòng 667).
2. **Bài học M7 (AGENTS.md):** trạng thái kéo-thả KHÔNG được nằm trong view của `RecyclerView`/`ViewPager2` (view bị thay bất cứ lúc nào, `IconGridView.setItems` tái dùng ô theo chỉ số, "ô con thứ i = item thứ i" là giả định cứng của kéo-thả và `onLayout`). Mọi thứ sống qua một lần cập nhật dữ liệu phải ở dữ liệu hoặc ở object ngoài view. Hoạt ảnh thuần mỹ thuật có thể ở view (mất thì chỉ mất hiệu ứng), nhưng không được quyết định gì về đúng/sai của UI.
3. **Chi phí kính (GL, đo được):** chi phí nằm ở số lệnh vẽ trên RenderThread (mỗi ô kính ba lệnh: +0.85 ms/6 ô); ô thư mục có bộ đệm bitmap nội dung tĩnh (`folderContent`); `BackdropBlurDrawable` tính vị trí cắt nền bằng `getLocationOnScreen` **mỗi lần ghi display list** — thay đổi thuộc tính transform (`scaleX`, `translationX`…) của RenderNode **không** ghi lại display list, nên một view đang scale/dịch bằng spring sẽ mang lát cắt nền cũ nếu không `invalidate()` (suy luận từ cách RenderNode hoạt động, **chưa đo** trên dự án; Task 2/3 kiểm bằng mắt). Máy trôi theo nhiệt độ: chỉ so trong cùng một phiên, xen kẽ, lặp "trước" ở cuối.
4. **Panel là kính thật (REAL) chỉ trên máy `realGlassCapable` (API 33+, tức Oppo A3x)** (đọc `GlassPolicy`, `e6a0149`): Live 4 (API 30) luôn là kính mờ tĩnh dù `FORCE_HIGH`. Panel kính thật trên Oppo trễ một khung mỗi lần mở (ngoại lệ D10 của GL, chủ dự án chấp nhận). Co/dịch một `LiquidGlassView` bằng spring **chưa từng đo**; thư viện còn `invalidate()` theo mọi `onScrollChanged` của cửa sổ (đã chặn bằng `setInvalidationSuppressed`). Đây là rủi ro chính của AN (mục "Rủi ro chính").
5. **Giới hạn đo (đề bài):** Live 4 và Oppo ghép cặp trước/sau trong một phiên; Janky +2.0 điểm (+1.0 trên Oppo); p95 +4 ms. Hoạt ảnh nào cũng "thêm khung vẽ so với không có hoạt ảnh", nên với cảnh có chuyển động "trước" là bản `main` (fade 150 ms / nhảy cóc), "sau" là bản AN; cảnh đứng yên và vuốt trang phải **không đổi** (spring đứng yên = 0 chi phí).
6. **File nóng:** R1 giữ `HomeActivity.kt` + `activity_home.xml` độc quyền; `HomeViewModel`/`HomeUiState` là file nóng (AN **không cần** sửa, xem mục Phase). G2b giữ `IconRenderer/IconLoader/IconCache/ui/settings/*/data/settings/*`. AN giữ `DragHelper.kt` + `DragDropTarget.kt`.
7. **Hiện trạng ô Library (đọc `item_category_group.xml`, `CategoryPageView.addTile`):** mỗi ô là khung 2×2 gồm **bốn ô bằng nhau** `category_mini_icon_size`; ≤ 4 app thì hiện thẳng từng app, > 4 app thì 3 icon + một ô chữ "+N" (số app còn lại) ở vị trí thứ tư; **chưa có cụm gồm các icon mini**. Cả ô là một `LinearLayout` clickable mở panel. **Thiết kế của chủ dự án (2026-11-04):** ô thứ tư của nhóm > 4 app là **một lưới nhỏ 2×2 icon mini, ô cuối là "+N" khi nhiều app hơn chỗ chứa**; bấm icon mini mở app đó ngay, bấm "+N" mở panel; bấm bất kỳ icon lớn nào mở app ngay; nhóm ≤ 4 app: cả bốn ô mở app ngay, không có "+N"; phần còn lại của ô (khe, nhãn) mở panel.

### 1. Cách mở/đóng panel từ icon

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Biến đổi transform của panel đã layout sẵn, một spring tiến độ `p`** | Panel layout ở chỗ cuối; `scale = lerp(s0, 1, p)`, dịch sao cho tâm đi từ tâm icon tới tâm màn hình, `alpha` panel = clamp(p·2.5), nền tối (`scrim`) = p. `p` do một `SpringProgress`, đích 1 khi dữ liệu có panel, 0 khi không | Không relayout mỗi khung (rẻ); một con số duy nhất nên đảo chiều là đổi đích; chuyển sang kính mờ/thật theo `p` dễ; mở lại giữa chừng liền mạch | Nội dung (icon, chữ, EditText) co theo nên rất nhỏ ở đầu (che bằng alpha); bo góc co theo scale (che bằng alpha); kính thật phải theo cùng transform (rủi ro chính) | **Chọn** |
| B. Shared-element: `MaterialContainerTransform` / Transition framework (đã có trong `material` 1.14) | Transition giữa hai view | Có sẵn, đẹp | Dựa vào `ValueAnimator` + interpolator, **không đảo chiều giữa chừng có vận tốc**; Transition `ChangeBounds` relayout mỗi khung; vẽ một "container" riêng nên chen kính thật không được; không phải spring (trái quyết định 1) | Không |
| C. Animate kích thước thật (đổi `LayoutParams` mỗi khung) | Panel to dần | Nội dung luôn đúng cỡ | Relayout + đo `EditText`/lưới 3×3/kính mỗi khung, kính thật phải đo lại mỗi khung; đắt nhất | Không |
| D. `ViewAnimationUtils.createCircularReveal` | Cắt hình tròn mở rộng | Một lệnh | Là clip chứ không phải co giãn từ icon; không spring; clip trên kính thật chưa đo | Không |

**Vì sao A:** (đọc code) hai panel đã là `FrameLayout` full-screen chứa `panel` ở giữa, laid-out một lần; transform thuộc tính là con đường rẻ nhất của View system (không relayout, không ghi lại display list của nội dung); và là phương án duy nhất mà "đảo chiều từ nơi đang đứng" chỉ là đổi đích của một số thực. Đánh đổi: kính phải được xử lý riêng (mục "Rủi ro chính").

### 2. Cách ngắt giữa chừng

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Spring đổi đích (`animateToFinalPosition`) trên một số thực, view chỉ đuổi theo dữ liệu** | `render(state)` gọi `spring.animateTo(if (state != null) 1 else 0)`; vị trí và vận tốc giữ nguyên khi đổi đích | Đảo chiều liền mạch đúng nghĩa vật lý; không có mã đặc biệt cho "bấm lại khi đang đóng"; trạng thái đúng/sai (mở hay đóng) vẫn là dữ liệu ở ViewModel | Phải giữ nội dung hiển thị (ảnh chụp state cuối) trong lúc đóng vì state đã `null`; cần xử lý mở thư mục KHÁC khi đang đóng (snap rồi mở) | **Chọn** |
| B. `ValueAnimator`/`ObjectAnimator` + `reverse()` | Đảo animator đang chạy | Không thêm thư viện | Vận tốc đứt đoạn (đổi chiều là nhảy), thời lượng đảo = phần đã chạy chứ không theo vật lý; trái quyết định "spring" | Không |
| C. Điều khiển bằng ngón tay (kéo để đóng như `LibraryOverlayController`) | Tiến độ theo cử chỉ | Tự nhiên nếu có vuốt | Chủ dự án **không** yêu cầu vuốt-để-đóng; thêm cử chỉ mới đụng chuỗi chạm đã nhiều bẫy | Không (ngoài phạm vi) |

Spring của DynamicAnimation chỉ chạy trên main thread; nghe `ValueAnimator.areAnimatorsEnabled()` (API 26 = minSdk) — người dùng tắt hoạt ảnh thì `SpringProgress` snap thẳng tới đích (**đọc tài liệu**, chưa kiểm trên máy).

### 3. Cách vẽ kính hover khi tạo thư mục

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Vẽ trong `IconCellView.onDraw` của ô đích (cờ `hoverGlass`) | Ô tự vẽ kính trước icon | Đi cùng ô | `snapshotWithoutBadge()` (ảnh kéo/ảnh nổi menu) chụp cả kính nếu không thêm cờ tắt; ô tái dùng giữa app/thư mục nên thêm một trạng thái nữa phải dọn (bài học A1a); lẫn với bộ đệm nội dung ô thư mục; kính to hơn ô nên phụ thuộc `clipChildren=false` của mọi cha | Không |
| B. Thêm một `View` kính làm con của `IconGridView` | `RealGlassSurface`/`StaticBlurSurface` | Dùng lại `GlassSurface` | **Phá "con i = ô i"** (`onLayout`, `setItems` cắt `childCount`, kéo-thả); `StaticBlurSurface` chỉ cắt nền một lần, lệch khi trang lật; kính thật quá đắt | Không |
| C. `ViewOverlay` của `IconGridView` | `overlay.add(drawable)` | Không thêm con | Overlay vẽ SAU các con nên kính **che icon** (phải ở phía sau icon đích) | Không |
| **D. `IconGridView.dispatchDraw` vẽ một `BackdropBlurDrawable` trước `super.dispatchDraw`** | Biên = hình vuông quanh icon đích × `HOVER_GLASS_SCALE`, chủ sở hữu drawable là chính lưới | Không thêm con (giữ "con i = ô i"); không lọt vào ảnh chụp ô nào; không đụng bộ đệm ô thư mục; chỉ 1 phần tử tại một lúc (tối đa 2 lệnh vẽ: tô + viền, so với 24 ô rung); vẽ phía sau icon vì đi trước các con; spring vào/ra bằng `SpringProgress`. `dispatchDraw` được gọi kể cả khi view `willNotDraw` | Phải tự `invalidate()` lưới khi hover (spring) và khi pager cuộn; viền tắt khi đang spring để bớt lệnh | **Chọn** |

Kính hover luôn là kính mờ tĩnh (`BackdropBlurDrawable`, vai trò `FOLDER_CELL`), không bao giờ là kính thật, mọi tier (tier `FLAT` thì tô màu phẳng `folder_background`).

### 4. Vùng chạm của ô nhóm Library

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Mỗi ô icon (`FrameLayout` bọc `ImageView`) có click riêng; ô thứ tư chứa thêm bốn `FrameLayout` con (mỗi con một góc phần tư) cũng có click riêng; ô cha giữ click mở panel** | Khung 2×2 vốn đã là 4 `FrameLayout`; ô thứ tư thêm lưới 2×2 icon mini; gắn `setOnClickListener` mở app cho ô có app; ô "+N", ô trống, khe, nhãn rơi xuống click của ô cha | Sửa vừa (layout thêm id và lưới mini, `CategoryPageView.addTile` gắn listener); Android tự phân phát chạm đúng con trước cha; TalkBack có từng đích với `contentDescription` riêng | Tối đa 8 view clickable thêm mỗi ô (8 ô: ~64 view, không đáng kể); **đích chạm mini nhỏ** (xem bên dưới) | **Chọn** |
| B. Một `OnTouchListener` trên cả ô, tính hình học rồi suy ra ô nào | Tự hit-test | Một listener | Phải tự làm pressed/ripple/a11y; dễ lệch với cỡ co theo cửa sổ (`LibraryLayoutMetrics`) | Không |
| C. Viết lại ô thành custom `View` vẽ tay (như `IconCellView`) | Một view/ô | Gọn view | Viết lại cả async icon, a11y ảo (`ExploreByTouchHelper`), cỡ co; quá lớn so với việc cần làm | Không |

Quy tắc (chủ dự án chốt 2026-11-04): với nhóm gồm N app: **N ≤ 4** → bốn ô lớn là app (mở app ngay), không cụm, không "+N". **N ≥ 5** → ba ô lớn đầu là app 1–3; ô thứ tư là cụm mini 2×2 chứa app 4..N: nếu R = N − 3 ≤ 4 thì R icon mini (ô còn lại trống, không "+N"); nếu R > 4 thì 3 icon mini (app 4–6) và ô thứ tư của cụm là "+(R − 3)" (= N − 6). Bấm icon lớn hoặc icon mini → mở app; bấm "+N", khe, đệm, nhãn, ô trống → mở panel. (N = 4 là trường hợp biên: hiểu "4 app trở xuống mở thẳng cả bốn ô" nên ô thứ tư là icon lớn thứ tư, không phải cụm; báo chủ dự án nếu muốn khác.) Nếu `onAppClick` chưa được nối (Phase B chưa xong) thì mọi chạm rơi về `onGroupClick` như hiện nay, nên Phase A không làm hỏng hành vi cũ.

**Vùng chạm của icon mini (cần nêu cỡ):** ở `s = 1` trên máy 360 dp (ô nhóm ~144 dp, ô lớn `mini` = (144 − 2·12 − 4·4)/2 = 52 dp) mỗi icon mini nằm trong một góc phần tư của ô lớn thứ tư, nên **vùng chạm tối đa ~26 × 26 dp** (mở rộng tới sát biên góc phần tư; không thể rộng hơn vì sẽ chồng icon mini bên cạnh). Dưới 48 dp (khuyến nghị trợ năng) nhưng là mức lớn nhất khung 2×2 cho phép; khi `LibraryLayoutMetrics` co (`s < 1`, sàn `MIN_TILE_DP`/mini ≥ 24 dp) vùng chạm co theo, tới ~12 dp. Chấp nhận (chủ dự án thiết kế); "+N" cũng là một góc phần tư nên chạm trượt sang mở panel vô hại. Kính ô (`BackdropBlurDrawable` trên khung 2×2) và `LibraryLayoutMetrics` không đổi: lưới mini là con của ô lớn thứ tư và lấy cỡ từ `Sizes.mini`.

### 5. Icon trượt vào chỗ mới

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. "FLIP" trong `IconGridView`/`DockView`: ghi vị trí nhìn thấy theo `itemId` trước khi bind, sau `onLayout` đặt `translation = cũ − mới` rồi spring về 0; xem trước (preview) đẩy-dồn đổi thành spring tới độ dời đích** | Làm đúng cho mọi nguyên nhân đổi vị trí (kéo-thả, dồn sau ẩn "−", thả vào thư mục); khớp "con i = ô i" vì khoá theo `itemId` chứ không theo chỉ số; mất hiệu ứng (khi view bị thay) thì chỉ mất hiệu ứng, không sai dữ liệu | Phải giữ độ dời xem trước sau khi thả (không reset về 0) để FLIP xuất phát từ vị trí nhìn thấy; cần chặn trượt khi đổi trang/đổi `Cells`/lần bind đầu; có đường dọn nếu `setItems` không tới | **Chọn** |
| B. `RecyclerView`+`ItemAnimator`/`LayoutTransition` | Dùng animator có sẵn | Có sẵn | Lưới là `ViewGroup` tự đặt; `RecyclerView` mức pager đã bị tắt `itemAnimator` có chủ đích (bài học M7) | Không |
| C. Animate theo từng sự kiện (vd `onDrop` tự `animate().translation`) | Mỗi nơi gọi tự làm | Đơn giản từng chỗ | Hai nguồn đổi vị trí (preview, thả/dồn/ẩn) nên sót chỗ; ô tái dùng sai item | Không |

### 6. Kính thật của panel trong lúc chuyển động (rủi ro chính, quyết định bằng đo ở Task 3)

| Chế độ | Cách làm | Ưu | Nhược / rủi ro |
|---|---|---|---|
| **S1. Co/dịch kính thật bằng đúng transform của panel** | `RealGlassSurface.view` nhận cùng `scale/translation/pivot` với `panel`; không `invalidate()` kính trong lúc chạy (nội dung kính là lát cắt chụp ở vị trí cuối, co theo như một texture); `captureOnce()` khi dừng | Một hình, không "nhảy" | Chưa biết `LiquidGlassView` có tự chụp lại khi bị transform không; có thể đắt |
| **S2. Kính mờ tĩnh trong lúc chuyển động, kính thật khi dừng** | Khi `p < 1` hoặc đích = 0: `RealGlassSurface` ẩn, `panel.background` = `BackdropBlurDrawable` (cắt nền đúng khi scale, `invalidate()` mỗi khung); khi `p` chạm 1 và đích = 1: bật kính thật + `captureOnce()`, bỏ nền mờ | Rẻ tối đa lúc chuyển động; Live 4 vốn chỉ có đường này nên không đổi | Một "bước nhảy" nhìn thấy giữa kính mờ và kính thật lúc dừng (có thể làm mềm bằng crossfade ngắn) |
| S3. Bỏ kính thật của panel, kính mờ tĩnh ở mọi tier | `GlassPolicy` panel = `STATIC_BLUR` | Đơn giản nhất | Đảo ngược ngoại lệ D10 của GL (chủ dự án giữ kính thật); **đã được chủ dự án cho phép trước (2026-11-04)** làm khi S1 và S2 đều không đạt trên Oppo, giữ hoạt ảnh co/dịch, không dừng hỏi lại |

Quy tắc chọn (đo ở Task 3, Oppo, ghép cặp với `main`): S1 nếu đạt giới hạn; không thì S2; không thì S3 (đã duyệt trước: kính mờ cho hai panel trên Oppo, giữ hoạt ảnh; ghi vào Nhật ký rằng việc này đảo ngược ngoại lệ D10 của GL).

### Đánh đổi chấp nhận

- Một thư viện mới (nhỏ, AndroidX, ổn định 1.1.0).
- Panel co từ icon nên nội dung co theo (không có hoạt ảnh icon-con-bay-ra-từ-ô-thư-mục kiểu iOS; để sau).
- Kính hover là kính mờ tĩnh, không khúc xạ; kính panel trên Oppo có thể thành mờ tĩnh trong lúc chuyển động (S2).
- Số spring (độ cứng, giảm chấn) là điểm xuất phát chỉnh tay, chưa đo cảm giác (câu hỏi mở 3).

---

## Rủi ro chính: kính thật co/dịch trong lúc spring mở panel (Oppo)

- **Giả thuyết cần kiểm (Task 3):** thay đổi transform của `LiquidGlassView` bằng thuộc tính View không ghi lại display list nên không kích hoạt chụp lại nền, chi phí ≈ ghép một layer. Nhưng thư viện có `OnScrollChangedListener` và logic chụp riêng (đọc `javap` v2.0.11 lúc GL); chưa biết nó có phản ứng với `onSizeChanged`/`invalidate` do transform không. Mọi điều này **suy luận chưa kiểm chứng** cho tới Task 3.
- **Đo sớm (Task 3, bắt buộc kiểm tay):** trước khi làm panel nhóm, ô Library, hover hay đo tổng: dựng mở/đóng thư mục bằng spring với cả S1 và S2 sau một hằng tạm, đo trên Oppo (và Live 4) vòng mở/đóng 20 lần, ghép cặp với `main` (fade 150 ms): Janky %, Δp50/Δp95 `FrameCompleted − IntendedVsync` (khử trùng theo `IntendedVsync`, lấy mẫu `framestats` định kỳ vì chỉ giữ ~120 khung), số khung trễ ở chính khung đầu tiên (đã biết 1 khung trễ mỗi lần mở, chỉ báo nếu tăng). Hằng chọn chế độ bị gỡ khi chốt (không để móc debug, bài học GL Task 9).
- **Phương án lùi:** S2, rồi S3 (cần chủ dự án). Dự phòng mềm hơn: giữ kính thật nhưng không co (chỉ nền tối + nội dung co), đo thêm nếu cả S1 và S2 đều khó coi.

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Phong cách chuyển động: spring vật lý kiểu iOS, thư viện AndroidX DynamicAnimation | Chủ dự án, 2026-11-04 (version 1.1.0 đọc thật, xem Tech Stack) |
| 2 | Ngắt được: thư mục/panel bấm lại hoặc đóng giữa chừng thì đảo chiều từ chỗ đang đứng | Chủ dự án, 2026-11-04 |
| 3 | Icon trượt vào chỗ khi di chuyển app/thư mục (reflow lúc kéo và lúc thả) | Chủ dự án, 2026-11-04 |
| 4 | Thư mục mở từ icon của nó, đóng về icon | Chủ dự án, 2026-11-04 |
| 5 | Panel nhóm Library mở từ ô nhóm, đóng về ô | Chủ dự án, 2026-11-04 |
| 6 | Hiệu ứng nhấn: phần tử kính co nhẹ khi đang giữ, như dock | Chủ dự án, 2026-11-04; điều phối 2026-11-04: dock co là của thư viện, AN không cài lại, `pressScale` mặc định 0.95f xác nhận bằng `javap` |
| 7 | Ô nhóm Library: bấm icon lớn mở app ngay; bấm cụm nhỏ mở panel | Chủ dự án, 2026-11-04; **thiết kế chi tiết 2026-11-04:** ô thứ tư (nhóm ≥ 5 app) là lưới 2×2 icon mini có "+N" ở góc cuối khi nhiều app hơn chỗ chứa; bấm icon mini mở app, bấm "+N" mở panel; nhóm ≤ 4 app mở app ngay cả bốn ô |
| 8 | Hover tạo thư mục: kính phía sau icon đích, to hơn icon một chút, bỏ phóng icon cũ; kéo lên thư mục có sẵn: phóng thư mục ngay, không tạo kính mới | Chủ dự án, 2026-11-04 |
| 9 | Sửa lỗi GL: mọi phần tử kính phải vẽ lại khi ảnh nền đổi | Chủ dự án, 2026-11-04 |
| 10 | Ngoài phạm vi: hoạt ảnh mở app (Android lo) | Chủ dự án, 2026-11-04 |
| D1 | Mở/đóng panel = transform của panel đã layout, một `SpringProgress` tiến độ 0..1 (phương án 1A) | Đề xuất của agent, chờ duyệt |
| D2 | Ngắt = đổi đích của spring; trạng thái mở/đóng vẫn là dữ liệu ở ViewModel (phương án 2A) | Đề xuất của agent, chờ duyệt |
| D3 | Nguồn của hoạt ảnh do view tự tìm (sổ đăng ký ô thư mục; `library_view` qua `rootView`), không cần `HomeActivity` | Đề xuất của agent; theo tiền lệ `rootView.findViewById(R.id.backdrop_image)` của `BackdropBlurDrawable` |
| D4 | Kính hover vẽ trong `IconGridView.dispatchDraw` (phương án 3D); luôn kính mờ tĩnh | Đề xuất của agent, chờ duyệt |
| D5 | Vùng chạm ô nhóm: click riêng trên từng ô icon, ô cha giữ click mở panel (phương án 4A) | Đề xuất của agent, chờ duyệt |
| D6 | Icon trượt = FLIP theo `itemId` + spring cho preview; "hạ cánh" của ảnh bay do `DragLayer`, trạng thái ở `DragHelper` | Đề xuất của agent, chờ duyệt |
| D7 | Báo ảnh nền đổi qua `BlurredBackdropCache` (mọi `BackdropBlurDrawable` tự vẽ lại) + `GlassEnvironment.notifyBackdropChanged()` (kính thật của panel) | Đề xuất của agent; cần một dòng ở `HomeActivity` (Phase B) |
| D8 | Chế độ kính panel trong lúc chuyển động chọn bằng phép đo (S1 → S2 → S3 kính mờ cho hai panel, đã duyệt trước) | Đề xuất của agent; S3 do chủ dự án duyệt trước 2026-11-04 |
| D9 | Mọi spring tôn trọng "tắt hoạt ảnh" của hệ thống (`ValueAnimator.areAnimatorsEnabled()`), huỷ ở `onDetachedFromWindow` | Đề xuất của agent |
| 11 | **Duyệt plan** AN | Chủ dự án, 2026-11-04 |
| 12 | Ô thứ tư của ô nhóm Library là lưới mini 2×2 + "+N" (xem dòng 7); đích chạm mini ~26 × 26 dp ở `s = 1` | Chủ dự án, 2026-11-04 (trả lời câu hỏi mở 1) |
| 13 | Hiệu ứng nhấn co chỉ cho: ô thư mục, ô nhóm Library, pill, thanh tìm kiếm Library; icon app thường KHÔNG co; mức 0.95 | Chủ dự án, 2026-11-04 (trả lời câu hỏi mở 2) |
| 14 | Giá trị spring, kính hover 1.25× cạnh icon, phóng thư mục 1.12×: dùng giá trị khởi điểm trong plan, giữ làm hằng chỉnh tay; chủ dự án phán cảm giác | Chủ dự án, 2026-11-04 (trả lời câu hỏi mở 3) |
| 15 | Trượt khi ẩn bằng "−"/cài–gỡ app: có (FLIP áp cho mọi đổi vị trí trên cùng trang) | Chủ dự án, 2026-11-04 (câu hỏi mở 4) |
| 16 | Ảnh bay lúc thả hạ cánh vào ô; thả vào thư mục thì thu nhỏ rồi mờ: có | Chủ dự án, 2026-11-04 (câu hỏi mở 5) |
| 17 | Nếu S1 và S2 đều không đạt trên Oppo: kính mờ tĩnh cho hai panel, giữ hoạt ảnh co/dịch; KHÔNG dừng hỏi lại; ghi vào Nhật ký rằng việc này đảo ngược ngoại lệ D10 của GL | Chủ dự án, 2026-11-04 (câu hỏi mở 6) |
| 18 | **Đổi D4/D5 của G2a (chủ dự án quyết 2026-11-04):** chỉ hiện kính / phóng thư mục khi ngón tay ở VÙNG GIỮA icon đích và vùng giữa được nới rộng; thả ở vùng giữa = tạo thư mục / thêm vào thư mục, thả ngoài vùng giữa = chèn đẩy-dồn M7. Bỏ đường "thả nhanh ở mép vẫn gộp" và bỏ giữ 500 ms ở mép | Chủ dự án, 2026-11-04 ("thấy gì thì được nấy"); chi tiết ở Task 9b |
| 19 | ~~App rời lưới chính để vào thư mục thì dồn trang nguồn như ẩn/gỡ app~~ **ĐÃ HOÀN TÁC ở Task 9c (chủ dự án, 2026-11-04):** app vào thư mục (thêm hoặc tạo) lại để ô nguồn TRỐNG, không dồn (luật M7 / G2a như trước). Lý do: chưa xác nhận iOS 26 làm vậy, và dồn mâu thuẫn với logic di chuyển thường (vốn có thể để lại khe trống) | Chủ dự án, 2026-11-04 |
| 20 | Đẩy-dồn ở mép chỉ bắt đầu khi ngón tay đã dừng: chờ 500 ms và đếm lại nếu ngón tay dời quá 8 dp | Chủ dự án, 2026-11-04 (Task 9c) |
| 21 | Đã đẩy-dồn thì ở lại trong ô neo (không kính/phóng/hàng về) tới khi ngón tay rời ô; muốn gộp phải tới một icon thật tại chỗ nhìn thấy của nó | Chủ dự án, 2026-11-04 (Task 9e) |

## Câu hỏi mở (đã được chủ dự án trả lời hết ngày 2026-11-04; kết quả ở các dòng 7, 11–17 của bảng trên)

1. **"Cụm nhỏ" của ô nhóm:** hiện ô thứ tư (khi nhóm > 4 app) là chữ "+N", chưa phải cụm gồm các icon mini như iOS. (a) Giữ "+N" và coi nó là "cụm nhỏ" (mở panel)? (b) Vẽ lại ô thứ tư thành 2×2 icon mini thật (nằm ngoài phạm vi trừ khi chủ dự án muốn)? Và nhóm ≤ 4 app: mở thẳng app cho cả 4 ô (đề xuất) hay vẫn mở panel?
2. **Nhấn co cho phần tử nào?** Đề xuất: ô thư mục trên Home, ô nhóm Library (khi chạm cụm/vùng nền, còn chạm icon thì icon co), pill tìm kiếm, thanh tìm kiếm Library. Icon app thường (không phải kính) có co khi giữ không? Mức co: `LiquidGlassView` v2.0.11 mặc định `enablePressEffect = true`, `pressScale = 0.95f` (**đọc `javap`**, 2026-11-04: khởi tạo trong constructor; `animatePress/animateScale` tự `setScaleX/Y` bằng `ValueAnimator` trong `onTouchEvent`). Dock giữ hiệu ứng của thư viện, AN **không** cài lại cho dock; chưa kiểm trên máy dock có thật sự nhận chạm (icon nằm trên kính) và co bao nhiêu. Phần tử AN tự co dùng 0.95 cho khớp — giữ 0.95 hay 0.92?
3. **Cảm giác spring:** mức nảy khi mở panel (không nảy / nảy nhẹ), nhanh–chậm, kích cỡ kính hover (đề xuất 1.25× cạnh icon), mức phóng thư mục khi hover (giữ 1.12×?). Các số là điểm xuất phát chỉnh tay; agent chỉnh theo mắt trên máy nhưng "đúng cảm giác" chỉ chủ dự án phán.
4. **Trượt khi ẩn bằng "−" / cài–gỡ app:** FLIP làm mọi lần đổi vị trí trên cùng trang đều trượt (kể cả dồn sau khi ẩn app). Giữ nguyên (tự nhiên) hay chỉ trượt khi kéo-thả?
5. **Hạ cánh của ảnh bay lúc thả:** ảnh bay trượt về đúng ô (đề xuất); thả vào thư mục thì thu nhỏ vào ô thư mục rồi mờ. Giữ?
6. **Nếu S1 và S2 đều không đạt trên Oppo** (xác suất thấp): chấp nhận kính mờ tĩnh cho hai panel (đảo ngược D10 của GL) hay giữ kính thật nhưng panel không co (chỉ fade)?

## Ngoài phạm vi / ghi để sau

- Hoạt ảnh mở app (Android điều khiển); hoạt ảnh icon con bay ra từ ô thư mục kiểu iOS (panel hiện chỉ co/dịch cả khối).
- Hoạt ảnh nhấc icon lên khi bắt đầu kéo (lift/phóng 1.1×) và hoạt ảnh vào/ra edit mode.
- Lớp phủ Library (`LibraryOverlayController`, `DecelerateInterpolator`) và lớp tìm kiếm giữ nguyên, không đổi sang spring.
- Phân trang thư mục, hoạt ảnh lật trang (TL1 đã chỉnh).
- Gộp `StaticBlurSurface` (dock) và `BackdropBlurDrawable` (nợ GL, không làm ở AN).
- Bước "giữ bản mờ cũ tới khi bản mới dựng xong" khi đổi ảnh nền (hiện vẽ phẳng ~80 ms rồi mờ): chỉ làm nếu Task 11 thấy nháy khó chịu.

## Vùng file

| File | Tạo / Sửa | Nóng | Ghi chú |
|---|---|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Sửa (thêm 1 dòng version + 1 thư viện + 1 `implementation`) | dùng chung có kiểm soát | chỉ thêm |
| `core/ui/SpringProgress.kt` | Tạo | không | Task 1 |
| `core/ui/DragHelper.kt`, `core/ui/DragDropTarget.kt` | Sửa | **độc quyền (đã giao cho AN)** | Task 9 (hạ cánh): thêm `settleRect`/`settlingItemId`/`onDragSettled` mặc định rỗng |
| `core/glass/BackdropBlurDrawable.kt` | Sửa | **độc quyền (`core/glass/*`) — điều phối xác nhận 2026-11-04: R1 và G2b không giữ** | Task 2 (cắt nền khi scale), Task 10 (đăng ký nhận báo ảnh nền đổi) |
| `core/glass/BlurredBackdropCache.kt` | Sửa | như trên | Task 10: danh sách người nghe yếu + `notifyBackdropChanged()` |
| `core/glass/GlassEnvironment.kt` | Sửa | như trên | Task 10: `notifyBackdropChanged()` + kênh nghe cho kính thật |
| `ui/home/view/BackgroundGlass.kt` | Sửa | không | Task 3 (chế độ chuyển động S1/S2), Task 10 (kính thật chụp lại khi ảnh nền đổi) |
| `ui/home/view/FolderPanelView.kt` | Sửa | không | Task 3 |
| `ui/home/view/IconGridView.kt` | Sửa | không (nhưng nhiều bẫy: GL/M7) | Task 3 (sổ đăng ký + `folderCellScreenRect`), 7, 8, 9 |
| `ui/home/view/IconCellView.kt` | Sửa | không | Task 6 (nhấn co), 7 (một spring scale cho hover/nhấn), 8 (spring độ dời) |
| `ui/home/view/DockView.kt` | Sửa | không | Task 8, 9 (cùng mẫu với lưới) |
| `ui/home/view/DragLayer.kt` | Sửa | không | Task 9 |
| `ui/home/view/CategoryGroupPanelView.kt` | Sửa | không | Task 4 |
| `ui/home/view/CategoryPageView.kt` | Sửa | không | Task 4 (`tileScreenRect`), 5, 6 |
| `ui/home/view/SearchPillView.kt` | Sửa | không | Task 6 |
| `res/layout/item_category_group.xml` | Sửa (thêm id/`clickable` cho 4 ô icon) | không | Task 5 |
| `res/values/strings.xml` | Sửa (khối riêng ở cuối) | dùng chung có kiểm soát | Task 5: nhãn trợ năng "+N" |
| `res/layout/activity_home.xml` | Không cần sửa; điều phối đã nhả cho AN (R1 không đụng, R1 giữ nguyên id và cây view = H3) | — | — |
| `ui/home/HomeActivity.kt` (và controller mới của R1) | **KHÔNG sửa trước khi R1 merge**; Phase B chỉ thêm đúng hook H1, H2 | **độc quyền của R1** | Task 11 |
| Xác nhận của điều phối (2026-11-04) | `core/glass/BackdropBlurDrawable.kt`, `BlurredBackdropCache.kt`, `GlassEnvironment.kt` được phép sửa; `ui/home/view/*` (kể cả `BackgroundGlass`) không ai khác giữ trong đợt 0b | | |
| `ui/home/HomeViewModel.kt`, `ui/home/HomeUiState.kt` | **Không cần sửa** (file nóng); nếu thấy cần thì dừng và báo | độc quyền | — |
| `IconRenderer`, `IconLoader`, `IconCache`, `ui/settings/*`, `data/settings/*` | Không đụng | của G2b | — |

**Giả định cần điều phối xác nhận:** (1) không nhánh nào khác (R1, G2b) sửa `ui/home/view/*` và `core/glass/*` trong đợt 0b; (2) R1 giữ nguyên id `home_root`, `backdrop_image`, `library_view`, `folder_panel`, `category_group_panel`, `drag_layer` và vị trí của chúng trong cây (AN tìm view bằng `rootView.findViewById`).

## Phase A / Phase B (cho R1)

| Phase | Làm khi nào | Task |
|---|---|---|
| **A — chỉ trong view, code ngay** | Từ khi plan được duyệt, song song với R1 | Task 1–10 |
| **B — nối vào `HomeActivity`** | Sau khi R1 merge, sửa trong controller mới của R1 (AN rebase lên `main`) | Task 11 (và Task 12 đo/đóng) |

**Hook cần R1 đặt chỗ (AN chỉ thêm đúng các dòng này ở Phase B):**

| Hook | Ở đâu hôm nay | Cần gì | Nếu thiếu thì sao |
|---|---|---|---|
| **H1: báo ảnh nền đổi** | Điều phối chốt với R1: `BackdropController.addBackdropListener` (một dòng trong `HomeActivity`); hôm nay là `HomeActivity.render()` khoảng dòng 820–840 (`if (shownBackdrop !== bitmap)` và nhánh xoá ảnh) | AN đăng ký listener gọi `GlassEnvironment.from(this).notifyBackdropChanged()`; R1 phải bắn listener cho MỌI lần đổi (lần nạp đầu, thay ảnh, xoá ảnh). Nếu hook nằm tại chỗ đổi `backdropImage`: ngay sau `backdropImage.setImageBitmap(...)` / `setImageDrawable(null)`: `GlassEnvironment.from(this).notifyBackdropChanged()`. Gọi cho MỌI lần đổi (lần nạp đầu, thay ảnh, xoá ảnh), không chỉ `replacing`. Giữ nguyên `dockView.setBackdropSource(...)` và `pulseGlassRefresh()` | Lỗi 9 còn nguyên (kính không vẽ lại) |
| **H2: mở app từ ô nhóm** | `AppLibraryController.setup()` của R1 (hôm nay `setupLibrary()`, khoảng dòng 666–677) | `binding.libraryView.onAppClick = { app, rect -> viewModel.onAppClicked(app, rect) }`. KHÔNG đóng panel nào (không có panel mở lúc bấm ô) | Phase A tự lùi về hành vi cũ: bấm ô nào cũng mở panel |
| **H3: giữ nguyên hợp đồng view** | Mọi nơi gọi `folderPanel.render(...)`, `categoryGroupPanel.render(...)`, `DragHelper.attachDragVisual(dragLayer)` | (a) Đóng panel = gọi `render(null)` như hôm nay (KHÔNG tự ẩn bằng `visibility`); (b) giữ id view ở trên; (c) không bọc panel/`library_view` vào container có `clipChildren=true` hay `scale` khác | Hoạt ảnh đóng bị cắt hoặc không tìm thấy icon gốc (lùi về mờ dần ở giữa màn hình) |

`HomeViewModel`/`HomeUiState` (file nóng): **AN không đụng**. `openFolderId`, `openCategoryGroup`, `draggingItemId`, `onAppClicked` đã đủ.

## Global Constraints

- Không schema Room, không test tự động, không thêm thư viện ngoài `dynamicanimation:1.1.0`. Không `HomeActivity.kt`/`activity_home.xml`/`HomeViewModel.kt`/`HomeUiState.kt` trước khi R1 merge; hai file sau không bao giờ (xin phép nếu cần).
- **Hoạt ảnh là hàm của dữ liệu, không phải nguồn sự thật** (bài học M7): đích của spring suy từ `openFolderId`/`openCategoryGroup`/`hiddenItemId`; mất view giữa chừng chỉ làm mất hiệu ứng, không làm sai dữ liệu hay làm item biến mất. Trạng thái cần sống qua `setItems` nằm ở `DragHelper` (object) hoặc ở dữ liệu, không nằm trong `IconCellView`.
- Mọi `SpringProgress` phải `cancel()` ở `onDetachedFromWindow` và không giữ tham chiếu view ra ngoài vòng đời. Đứng yên = không spring nào chạy (chi phí 0).
- Kính: không thêm `View` kính vào `IconGridView` (giữ "con i = ô i"); view bị scale/dịch bằng spring mà có `BackdropBlurDrawable` thì phải `invalidate()` mỗi khung và drawable phải cắt nền đúng theo scale (Task 2); kính thật không `invalidate()` mỗi khung (chặn như hiện nay).
- Không để móc debug trong code (chốt phép đo xong thì gỡ hằng chọn chế độ). Bình luận trong code bằng tiếng Việt, tên và commit bằng tiếng Anh.
- Dependency chỉ thêm, không đổi version hiện có. Không đụng `IconRenderer/IconLoader/IconCache/ui/settings/*/data/settings/*`.

## Review Focus

1. Mở rồi đóng thư mục/panel nhanh liên tiếp, hoặc bấm lại khi đang đóng: không nháy, không kẹt (`visibility` đúng khi dừng), không nhân đôi panel; recreate (xoay máy) khi panel mở dựng thẳng ở trạng thái mở, không chạy hoạt ảnh.
2. `containsScreenPoint`/`blocksOtherTargets` của `FolderPanelView` đúng trong lúc đóng (không nhận thả khi `shown=false`).
3. Cắt nền đúng khi scale ≠ 1 (Task 2) và nhánh scale = 1 **không đổi lệnh vẽ** so với GL (ảnh chụp Home đứng yên trước/sau giống hệt).
4. Kính thật của panel: chế độ đã chọn, không có `invalidate()` mỗi khung của `LiquidGlassView`, `captureOnce()` sau khi dừng; không khung dư.
5. Hover glass: không thêm con vào `IconGridView`; không có trong ảnh chụp kéo/menu; huỷ sạch khi `onDragExit`, thả, đổi ô, hết 500 ms ở mép, `onDetachedFromWindow`; không còn `FOLDER_INTENT_SCALE`/`animate().scaleX` trên ô app đích.
6. FLIP: khoá theo `itemId`; không trượt khi đổi trang (`pageId` khác), đổi `Cells`, lần bind đầu; kéo-thả M7 (kéo về đúng ô cũ, kéo hai lần liên tiếp, lật trang) không biến mất icon; có đường dọn độ dời tồn nếu `setItems` không tới.
7. Hạ cánh: ô gốc ẩn đúng đến khi ảnh bay chạm đích (qua `DragHelper.settlingItemId`, không qua view); phiên kéo mới trong lúc đang hạ cánh kết thúc hạ cánh ngay.
8. Ô nhóm Library: bấm icon mở app, bấm "+N"/vùng khác mở panel, a11y từng đích; `onAppClick == null` thì lùi về mở panel.
9. Lỗi 9: đổi ảnh nền (cả lần đầu, thay ảnh, xoá ảnh) → ô thư mục, ô nhóm, thanh, pill, panel đang mở, dock đều vẽ lại không cần chạm; hai máy.
10. Đo: Live 4 và Oppo ghép cặp trong một phiên; cảnh đứng yên/vuốt/rung không đổi so với `main` (spring đứng yên = 0).

---

## Giao thức đo (dùng cho Task 3, 11 và 12; điều phối chạy máy)

- **Máy:** Live 4 (mặc định `AUTO`, panel kính mờ tĩnh; thêm một lượt `FORCE_HIGH` cho cảnh panel) và Oppo A3x (panel kính thật). Mở app bằng `am start -n gambi.global.os26launcher/.ui.home.HomeActivity` (Home mặc định của máy là launcher gốc; `input keyevent HOME` không mở Os26Launcher).
- **Ghép cặp trong MỘT phiên:** "trước" = APK `main` (`origin/main`), "sau" = APK nhánh; xen kẽ, lặp "trước" ở cuối (bài học GL: máy trôi theo nhiệt độ; Live 4 nóng/nguội đổi p50 8 → 5 ms). Mỗi cảnh: `dumpsys gfxinfo gambi.global.os26launcher reset`, chạy kịch bản, đọc Janky % và `framestats` (lấy mẫu định kỳ, khử trùng theo `IntendedVsync`, chỉ dòng `^\d+,`).
- **Cảnh:** C1 mở/đóng thư mục ×20 (`input tap` vào ô thư mục rồi `input tap` vùng tối, nghỉ 700 ms); C2 mở/đóng panel nhóm ×20; C3 kéo hover (`input motionevent` DOWN–giữ 1 s–MOVE tới ô đích–giữ 1 s–di ra ×10, 6 thư mục trên trang, edit mode rung); C4 kéo-thả reflow ×10 (kéo qua hàng, thả); C5–C7 hồi quy cảnh GL: đứng yên 30 s, vuốt trang qua lại, rung edit mode. `input swipe x y x y 900` không phải nhấn giữ với launcher này: dùng `input motionevent`.
- **Giới hạn đạt (đề bài):** Janky +2.0 điểm % (Oppo +1.0), Δp95 ≤ +4 ms; C5–C7 phải không đổi (±nhiễu đã biết). C1–C4 so với "trước" của chính cảnh đó (fade / nhảy cóc), nên có thể kém hơn đôi chút vì thực sự có thêm khung vẽ; vượt giới hạn thì dừng, báo con số, đề xuất phương án lùi.

---

> **Quy ước tick khi đóng nhánh (2026-11-05):** ô được tick chỉ khi việc đã làm thật (bước viết mã/đo có commit hoặc số đo ghi trong Nhật ký). Các bước "Kịch bản kiểm" nhiều ý KHÔNG được tick nếu không có bản ghi kết quả từng ý: chủ dự án kiểm bằng cảm giác ở phiên kiểm tay, chưa ghi lại từng ca; mỗi dòng chưa tick nói lý do. Task 9g và 9h xem phần riêng.

### Task 1: Dependency + `SpringProgress` (xong 2026-11-04, chờ review điều phối)

**Phase:** A · **Bắt buộc kiểm tay:** không (không có hành vi nhìn thấy; kiểm bằng build)
**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`; Create `core/ui/SpringProgress.kt`.
**Interfaces:** Produces: `SpringProgress(spec, onUpdate, onSettled)` với `value`, `target`, `isRunning`, `animateTo(target)` (đổi đích giữ vị trí và vận tốc), `snapTo(value)`, `cancel()`; `SpringProgress.Spec` (độ cứng, giảm chấn, mức thay đổi nhìn thấy) cùng vài preset: `PANEL` (mở/đóng), `PRESS`, `HOVER`, `SLIDE`, `SETTLE`.

- [x] **Step 1:** thêm `dynamicanimation = "1.1.0"` vào `[versions]`, `androidx-dynamicanimation` vào `[libraries]`, `implementation(libs.androidx.dynamicanimation)` vào `app/build.gradle.kts`. Chạy `gradlew.bat assembleDebug` để chứng minh giải được dependency.
- [x] **Step 2:** viết `SpringProgress` bọc `SpringAnimation(FloatValueHolder)` + `SpringForce`: `animateTo` đang chạy thì `animateToFinalPosition` (giữ vận tốc), chưa chạy thì `setStartValue(value)` rồi `start()`; `setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)`; `onSettled` gọi từ `addEndListener` khi không bị huỷ; `snapTo` = huỷ rồi đặt giá trị và gọi `onUpdate`+`onSettled`; **nếu `!ValueAnimator.areAnimatorsEnabled()` thì `animateTo` = `snapTo`** (D9). Preset là điểm xuất phát chỉnh tay (độ cứng/giảm chấn: `PANEL` ~400/0.85, `PRESS` ~1500/0.75, `HOVER` ~900/0.6, `SLIDE` ~800/0.8, `SETTLE` ~600/0.8); ghi rõ "chưa đo cảm giác" trong KDoc.
- [x] **Step 3: Kịch bản kiểm (cả hai máy)** — (a) build xanh, app mở bình thường (`am start`), không crash khi khởi động; (b) kiểm bằng đọc code: không có chỗ nào gọi từ luồng khác main (DynamicAnimation ném `AndroidRuntimeException`).
- [x] **Step 4: Commit** `build: add dynamic animation and a spring progress helper`.

---

### Task 2: Kính tại chỗ cắt nền đúng khi view bị scale (xong 2026-11-04, chờ review điều phối)

**Phase:** A · **Bắt buộc kiểm tay:** không (nền tảng cho Task 3/7; hình ảnh kiểm ở các Task đó; riêng nhánh scale = 1 kiểm bằng ảnh chụp)
**Files:** Modify `core/glass/BackdropBlurDrawable.kt` (`core/glass/*` độc quyền — xin xác nhận).
**Interfaces:** Consumes `owner.scaleX/scaleY`, `pivotX/pivotY`. Produces: `BackdropBlurDrawable.draw` đúng khi owner có scale ≠ 1 (và vẫn **cùng lệnh vẽ** khi scale = 1).

**Vì sao:** `getLocationOnScreen` của view đã scale trả toạ độ góc trên-trái *của hình đã co*, còn drawable vẽ trong toạ độ cục bộ chưa co rồi canvas co lại: điểm cục bộ `p` hiện ở `L' + s·p` nhưng lát cắt lấy ở `L' + p` (lệch `(1−s)·p`, tới ~6 dp ở mép khi s = 1.12 trên ô 52 dp; ở panel co từ 0.14 thì lệch to). Sửa: khi `s ≠ 1`, vẽ trong "không gian nền": `canvas.scale(1/s)`, `translate(-t)`, hình chữ nhật = `s·rect + t`, bán kính và độ dày viền nhân `s` (shader vẫn cố định, không `setLocalMatrix` mỗi khung — giữ tối ưu GL Task 6c).

- [x] **Step 1:** thêm nhánh `if (owner.scaleX != 1f || owner.scaleY != 1f)` trong `draw`; nhánh `s == 1` giữ **nguyên văn** hai lệnh hiện có. Chấp nhận scale đồng đều (không xoay đồng thời với scale khác 1 trong AN).
- [ ] **Step 2: Kịch bản kiểm (Live 4)** — (a) ảnh chụp Home đứng yên (`adb exec-out screencap -p`) với APK `main` và APK nhánh, cùng ảnh nền và cùng trạng thái: **giống hệt**; (b) edit mode rung + vuốt trang: không đổi (cùng số lệnh vẽ: đọc code); (c) phần scale ≠ 1 kiểm bằng mắt ở Task 3 và Task 7. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 3: Commit** `feat: make in-place glass crop correctly when the owner is scaled`.

---

### Task 3: Thư mục mở từ icon, đóng về icon, ngắt được — và phép đo kính thật ✅ qua đo 2026-11-04: chọn S1 (co kính thật cùng panel); công tắc tạm đã gỡ

**Phase:** A · **Bắt buộc kiểm tay: CÓ** (điều phối chạy phép đo và tự kiểm cơ học; chủ dự án chỉ xem bằng mắt panel co ở lượt cuối) — lý do (mục 4.2): đây là **phép đo hiệu năng dùng để chọn phương án** (chế độ kính S1/S2/S3) và các Task sau (4, 11) xây trên kết luận; sai thì phải làm lại nhiều Task.
**Files:** Modify `ui/home/view/FolderPanelView.kt`, `ui/home/view/BackgroundGlass.kt`, `ui/home/view/IconGridView.kt`.
**Interfaces:** Consumes `SpringProgress` (Task 1), cắt nền khi scale (Task 2), `FolderPanelState`. Produces: `IconGridView.folderCellScreenRect(itemId): ScreenRect?` (và sổ đăng ký ô gắn/tháo theo vòng đời view, giống `DragHelper.registerTarget`); `BackgroundGlass.applyMotion(...)`.

- [x] **Step 1: Sổ đăng ký + toạ độ icon gốc.** `IconGridView` đăng ký/hủy đăng ký ở `onAttachedToWindow/onDetachedFromWindow` vào một sổ tĩnh (tham chiếu yếu hoặc hủy chắc chắn ở detach, không giữ view của Activity đã hủy — cùng quy tắc với `DragHelper.registerTarget`). `FolderPanelView` hỏi sổ khi bắt đầu mở và khi bắt đầu đóng (vị trí icon có thể đã đổi); không tìm thấy thì lùi về mờ dần ở giữa (`s0 = 0.9`).
- [x] **Step 2: Chuyển `render` sang spring.** `render(state)` đặt `SpringProgress.animateTo(1 hoặc 0)`; `visibility = VISIBLE` khi đích = 1; về `GONE` chỉ khi đã dừng ở 0 và đích vẫn 0. Giữ bản sao `FolderPanelState` cuối để vẽ trong lúc đóng (chỉ là bộ đệm hiển thị, đích vẫn do dữ liệu). `p → ` scale/dịch/alpha như phương án 1A; nền tối = `p` (thay `setBackgroundColor`/`alpha` của cả view bằng alpha của nền tối riêng); `shown` = đích (không phải `visibility`) để `containsScreenPoint` đúng. Mở thư mục **khác** khi `p > 0.01`: `snapTo(0)` rồi mở. Lần `render` đầu có state sau recreate: `snapTo(1)`, không hoạt ảnh. Hủy spring ở `onDetachedFromWindow`.
- [x] **Step 3: Kính trong lúc chuyển động.** `BackgroundGlass.applyMotion(scale, tx, ty, pivot)` đặt cùng transform cho `panel` và `RealGlassSurface.view`; thêm công tắc **tạm** để đo hai chế độ (`PanelGlassMotion { SCALE_REAL, BLUR_DURING_MOTION }`, chọn bởi lớp tạm `AnDebug` đọc `files/an_panel_mode` qua `run-as`, giống `gl_mask` của GL; chỉ ở commit này, gỡ ở Step 5); cả hai đều `invalidate()` nền mờ mỗi khung nếu đang dùng `BackdropBlurDrawable`, và không bao giờ bỏ chặn `invalidate()` của kính thật; `captureOnce()` khi dừng ở 1 (thay `withEndAction` của fade 150 ms).
- [x] **Step 4: Phép đo kính thật (điều phối chạy máy; agent không cài APK).** Ghép cặp trong MỘT phiên theo thứ tự: **(1)** cài APK `main`, chạy `$Phase='MAIN'; $Tag='main1'`; **(2)** cài APK nhánh (commit Task 3 Step 3), chạy `$Phase='AN'` (cấu hình `s1` rồi `s2`; khối lệnh ghi `files/an_panel_mode` = 1 hoặc 2 bằng `run-as`); **(3)** cài lại APK `main`, chạy `$Phase='MAIN'; $Tag='main2'` (lặp "trước" ở cuối, bài học GL về máy trôi theo nhiệt độ). Làm trên Live 4 (một lượt `AUTO`, một lượt Cài đặt mức kính `FORCE_HIGH`) và Oppo. Ghi `wm size`, tần số quét, độ sáng/sạc vào báo cáo. Kịch bản: **C1** mở/đóng thư mục 20 lần; **C1i** mở rồi đóng rồi mở lại giữa chừng 15 lần (tap, 120 ms, Back, 120 ms, tap, 900 ms, Back); **S2** vuốt trang 20 vòng (hồi quy, phải không đổi). Cột `fs_gt33` = số khung có `FrameCompleted − IntendedVsync` > 33 ms (đại diện "khung trễ" của lần mở đầu; Oppo đã biết ~1 khung trễ mỗi lần mở, nên chỉ báo nếu tăng so với `main`).

  ```powershell
  $pkg = 'gambi.global.os26launcher'
  $Phase = 'MAIN'; $Tag = 'main1'; $runs = 3   # $Phase: 'MAIN' = APK main (không có chế độ); 'AN' = APK nhánh, chạy cấu hình s1 rồi s2. $Tag chỉ dùng khi MAIN: main1 / main2
  $folderTitle = 'Thư mục'; $out = "$env:USERPROFILE\Desktop\an-measure"
  New-Item -ItemType Directory -Force $out | Out-Null
  $wh = ((adb shell wm size) -replace '.*:\s*','').Trim().Split('x') | ForEach-Object { [int]$_ }
  $W = $wh[0]; $H = $wh[1]; $y = [int]($H*0.45); $xa = [int]($W*0.85); $xb = [int]($W*0.15)
  $plan = if ($Phase -eq 'MAIN') { @(@{n=$Tag; m=$null}) } else { @(@{n='s1'; m='1'}, @{n='s2'; m='2'}) }   # m = nội dung files/an_panel_mode: 1 = S1 co kính thật, 2 = S2 kính mờ trong lúc chuyển động
  $scn = 'C1','C1i','S2'
  $csv = "$out\summary.csv"
  if (-not (Test-Path $csv)) { 'cfg,scenario,run,total,janky,janky_pct,gfx_p50,gfx_p95,fs_frames,fs_total_p50,fs_total_p95,fs_gt33,fs_rec_p50,fs_rec_p95' | Out-File $csv -Encoding ascii }
  $script:fs = @()
  function Start-Cfg($c) {
    if ($c.m) { adb shell "run-as $pkg sh -c 'echo $($c.m) > files/an_panel_mode'" | Out-Null }
    adb shell am force-stop $pkg | Out-Null
    adb shell am start -n "$pkg/.ui.home.HomeActivity" | Out-Null
    Start-Sleep 12   # Oppo (344 app) khởi động nguội ~10 giây
  }
  function Dump-UI { adb shell uiautomator dump /sdcard/an-ui.xml | Out-Null; adb pull /sdcard/an-ui.xml "$out\an-ui.xml" | Out-Null; [xml](Get-Content "$out\an-ui.xml" -Encoding UTF8 -Raw) }
  function Center($n) { $m = [regex]::Match($n.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); @([int](([int]$m.Groups[1].Value+[int]$m.Groups[3].Value)/2), [int](([int]$m.Groups[2].Value+[int]$m.Groups[4].Value)/2)) }
  function Grab-FS { $script:fs += ((adb shell dumpsys gfxinfo $pkg framestats) | Out-String) }
  function Tap($x,$yy) { adb shell input tap $x $yy | Out-Null }
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
    @($tot.Count,(Pct $tot 0.5),(Pct $tot 0.95),@($tot | Where-Object { $_ -gt 33 }).Count,(Pct $rec 0.5),(Pct $rec 0.95))
  }
  function Save-Stats($cfg,$sc,$run) {
    $t = (adb shell dumpsys gfxinfo $pkg) | Out-String
    $t | Out-File "$out\$cfg-$sc-$run.txt" -Encoding ascii
    ($script:fs -join "`n=====`n") | Out-File "$out\$cfg-$sc-$run-framestats.txt" -Encoding ascii
    function G($p) { $m = [regex]::Match($t,$p); if ($m.Success) { $m.Groups[1].Value } else { '' } }
    $j = [regex]::Match($t,'Janky frames: (\d+) \(([\d.]+)%\)'); $s = FS-Stats
    ($cfg,$sc,$run,(G 'Total frames rendered: (\d+)'),$j.Groups[1].Value,$j.Groups[2].Value,(G '50th percentile: (\d+)ms'),(G '95th percentile: (\d+)ms'),$s[0],$s[1],$s[2],$s[3],$s[4],$s[5]) -join ',' | Out-File $csv -Append -Encoding ascii
    $script:fs = @()
  }
  function Begin-Measure { adb shell dumpsys gfxinfo $pkg reset | Out-Null; $script:fs = @() }
  function Run-Scenario($sc,$cfg,$run) {
    Start-Cfg $cfg
    switch ($sc) {
      'C1'  { Begin-Measure; 1..20 | ForEach-Object { Tap $fx $fy; Start-Sleep -Milliseconds 900; Grab-FS; Back; Start-Sleep -Milliseconds 800; Grab-FS } }
      'C1i' { Begin-Measure; 1..15 | ForEach-Object { Tap $fx $fy; Start-Sleep -Milliseconds 120; Back; Start-Sleep -Milliseconds 120; Tap $fx $fy; Start-Sleep -Milliseconds 900; Grab-FS; Back; Start-Sleep -Milliseconds 800; Grab-FS } }
      'S2'  { Begin-Measure; Swipes 20 }
    }
    Save-Stats $cfg.n $sc $run
  }
  Start-Cfg @{n='find';m=$null}
  $fn = $null
  foreach ($try in 1..5) { $fn = (Dump-UI).SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $folderTitle } | Select-Object -First 1; if ($fn) { break }; Start-Sleep 3 }
  if (-not $fn) { throw "Khong thay o thu muc '$folderTitle' o trang 1: kiem tra bo cuc (can it nhat 1 thu muc o trang 1)" }
  $p = Center $fn; $fx = $p[0]; $fy = $p[1]
  "folder centre = $fx,$fy; wm size = $W x $H" | Out-File "$out\folders.txt" -Encoding ascii
  foreach ($cfg in $plan) { foreach ($sc in $scn) { foreach ($run in 1..$runs) { Run-Scenario $sc $cfg $run } } }
  adb shell "run-as $pkg sh -c 'rm -f files/an_panel_mode'" | Out-Null; adb shell am force-stop $pkg | Out-Null
  Write-Host "Done. Send $csv and folder $out"
  ```
  Ghi chú: **chưa chạy thử trên máy** (agent không có máy). Chế độ chuyển động đọc từ `files/an_panel_mode` do một lớp **tạm** `AnDebug` (chỉ tồn tại ở commit Task 3 Step 3, không bao giờ vào `main`); `main` không đọc tệp này nên `MAIN` bỏ qua. Nếu không tìm thấy thư mục, gửi `an-ui.xml`. Chọn chế độ theo "Giao thức đo": S1 nếu đạt, không thì S2, không thì S3 (kính mờ cho hai panel trên Oppo, đã duyệt trước, quyết định 17). Cũng ghi: (i) bằng mắt: panel co từ icon có trông đúng không ở S1, có "nhảy" lúc đổi sang kính thật ở S2 không; (ii) agent đọc `javap` `LiquidGlassView` v2.0.11 xem `onDraw/dispatchDraw/onSizeChanged` có phản ứng với transform không (chép kết luận vào Nhật ký).
- [x] **Step 5: Gỡ ngay lớp `AnDebug` và công tắc chọn chế độ**, giữ chế độ đã chọn, commit riêng; `grep AnDebug|an_panel_mode` phải trống (kiểm lại ở Task 12 Step 3).
- [ ] **Step 6: Kịch bản kiểm (Live 4 rồi Oppo)** — (a) bấm thư mục: panel lớn dần từ icon, nền tối dần; bấm vùng tối: thu về icon; (b) bấm thư mục rồi bấm vùng tối **ngay khi đang mở** (giữa chừng): đảo chiều mượt từ chỗ đang đứng, không giật về đầu; (c) đóng giữa chừng rồi bấm lại icon: mở tiếp từ chỗ đang đứng; (d) bấm thư mục A, đóng, ngay lập tức bấm thư mục B: B mở đúng từ icon B, không dư ảnh A; (e) mở thư mục, Back: cùng hoạt ảnh đóng; (f) trong panel bấm một app: panel thu về icon trong lúc app mở; (g) xoay màn hình khi panel mở: panel dựng sẵn ở trạng thái mở, không chạy hoạt ảnh; (h) kéo icon ra khỏi panel (A3b): panel đóng khi ngón tay rời khung, phiên kéo tiếp tục, thả vào lưới đúng luật M7 (hồi quy kéo-thả); (i) sửa tên thư mục: bàn phím vẫn hoạt động, tên lưu khi đóng; (j) thang "Giao thức đo" C1 + C5–C7 hồi quy; (k) tắt hoạt ảnh trong Tuỳ chọn nhà phát triển (Animator duration scale = off): panel hiện/ẩn tức thì, không kẹt. Ghi kết quả đo và chế độ chọn vào Nhật ký. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 7: Commit** `feat: open and close the folder panel from its icon with a spring`.

---

### Task 4: Panel nhóm Library mở từ ô nhóm (xong 2026-11-04, chờ review điều phối)

**Phase:** A · **Bắt buộc kiểm tay:** không (cùng cơ chế và chế độ kính đã chốt ở Task 3; đo gộp ở Task 12)
**Files:** Modify `ui/home/view/CategoryGroupPanelView.kt`, `ui/home/view/CategoryPageView.kt`.
**Interfaces:** Consumes Task 1–3. Produces: `CategoryPageView.tileScreenRect(group): ScreenRect?` (map `CategoryGroup → khung 2×2` dựng trong `addTile`; `CategoryPageView` tìm bằng `rootView.findViewById(R.id.library_view)`, tiền lệ `backdrop_image`).

- [x] **Step 1:** `CategoryPageView` giữ map group → view khung 2×2 của từng ô (hiện chỉ `glassTiles` cho ô có kính); `tileScreenRect` trả khung toạ độ màn hình (đã gồm `translationX` của lớp Library).
- [x] **Step 2:** `CategoryGroupPanelView.render(group, apps)` đi cùng khuôn của Task 3 (spring tiến độ, giữ bản sao nội dung cuối trong lúc đóng, không `submitList(emptyList())` cho tới khi dừng, `snapTo(1)` lần `render` đầu sau recreate, dùng chế độ kính đã chốt). Panel nhóm **không có fade hôm nay** (xem Vấn đề 1) nên đây là hoạt ảnh mới hoàn toàn.
- [ ] **Step 3: Kịch bản kiểm (Live 4, Oppo)** — (a) mở Library, bấm ô nhóm có "+N": panel lớn dần từ ô, đóng thu về ô; (b) mở/đóng giữa chừng, đảo chiều mượt; (c) bấm app trong panel: mở app, panel thu về ô; (d) cuộn danh sách panel nhóm nhiều app: vẫn cuộn, kính không lệch; (e) Back đóng panel rồi Back đóng Library đúng thứ tự cũ; (f) gỡ app khiến nhóm hết app khi panel mở: panel đóng không treo; (g) đổi cỡ cửa sổ (`wm size`, Live 4): toạ độ ô gốc đúng. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 4: Commit** `feat: open and close the library group panel from its tile with a spring`.

---

### Task 5: Vùng chạm ô nhóm Library (xong 2026-11-04, chờ review điều phối; H2 ở Task 11)

**Phase:** A (Phase B nối hook H2) · **Bắt buộc kiểm tay:** không (chạm đơn, không phải cử chỉ kéo mới; kiểm cuối tính năng)
**Files:** Modify `res/layout/item_category_group.xml`, `ui/home/view/CategoryPageView.kt`, `res/values/strings.xml`.
**Interfaces:** Consumes `CategoryGroupApps.apps`. Produces: `CategoryPageView.onAppClick: ((AppItem, ScreenRect) -> Unit)?`.

- [x] **Step 1:** trong `item_category_group.xml` cho bốn `FrameLayout` bọc icon thêm id (`category_slot_0..3`) và `clickable=true`; ô cha giữ nguyên click mở panel (D5).
- [x] **Step 2:** `addTile`: quy tắc N / R ở mục 4 ("Vì sao chọn"). N ≤ 4: ô `i` có app → click mở app. N ≥ 5: ô 0–2 là app 1–3 mở app; ô 3 hiện lưới mini 2×2 (con của ô 3, bốn `FrameLayout` góc phần tư, mỗi góc một `ImageView` mini + `TextView` "+N" tái dùng `category_overflow`): góc có app → click mở app (`onAppClick?.invoke(app, rect)`; rect lấy từ `ImageView`), góc "+N" và góc trống không clickable (rơi xuống ô cha → panel). Icon mini nạp bằng cùng `iconLoader` với cỡ `Sizes.mini / 2` (trừ khe), huỷ cùng `iconJobs`. `onAppClick == null` thì không gắn click riêng (lùi về `onGroupClick`). `contentDescription` từng icon (nhãn app) và "+N" ("N app khác", chuỗi mới ở khối cuối `strings.xml`). Kính ô và `LibraryLayoutMetrics` giữ nguyên.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — (a) nhóm ≥ 8 app (cụm có "+N"): bấm ô icon 1/2/3 mở đúng app ngay; bấm từng icon mini mở đúng app; bấm "+N" mở panel; bấm khe/nhãn mở panel; (a2) nhóm 5–7 app: cụm chỉ có icon mini, không "+N"; bấm mini mở app, bấm góc trống mở panel; (b) nhóm ≤ 4 app: bấm từng ô mở đúng app, bấm vùng trống trong ô mở panel; (b2) đo cỡ vùng chạm icon mini bằng `uiautomator dump` (bounds) và ghi cỡ dp thực vào Nhật ký; (c) cuộn Library bằng vuốt dọc bắt đầu từ ô icon hoặc icon mini: vẫn cuộn, không mở app nhầm; (d) vuốt ngang đóng Library bắt đầu từ ô icon: vẫn đóng (hồi quy `LibraryOverlayController`); (e) TalkBack: từng ô và icon mini đọc đúng nhãn; (f) trước khi Phase B nối H2: bấm ô nào cũng mở panel như hiện nay (lùi an toàn); (g) `wm size 1080x1150`: ô co, cụm mini co theo, vẫn bấm được; (h) kính ô nhóm còn nguyên. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 4: Commit** `feat: open apps directly from library tile icons`.

---

### Task 6: Hiệu ứng nhấn co cho phần tử kính (xong 2026-11-04, chờ review điều phối)

**Phase:** A · **Bắt buộc kiểm tay:** không (hình ảnh; kiểm cuối)
**Files:** Modify `ui/home/view/IconCellView.kt`, `ui/home/view/CategoryPageView.kt`, `ui/home/view/SearchPillView.kt`.
**Interfaces:** Consumes `SpringProgress.PRESS`. Produces: `IconCellView` có MỘT spring scale chung (đích = hover 1.12 / nhấn `PRESS_SCALE` / 1; chỉ ô thư mục có nhấn co, ô app thường không), để hover (Task 7) và nhấn không tranh nhau `scaleX/Y`.

- [x] **Step 0:** không đụng dock (`LiquidGlassView` đã có `enablePressEffect`/`pressScale = 0.95f`, xác nhận bằng `javap`); chỉ kiểm bằng mắt ở Step 4(f).
- [x] **Step 1: `IconCellView` (chỉ khi là ô thư mục, không edit mode):** trong `onTouchEvent` `DOWN` → đích nhấn `PRESS_SCALE` (0.95 = mặc định của thư viện, đã kiểm bằng `javap`; câu hỏi mở 2; dock không đụng), `UP/CANCEL` → về 1. Pivot = tâm icon (không phải tâm ô, vì icon nằm ở phần trên của ô, nhãn bên dưới). Giữ nguyên chuỗi chạm: `IconDragGesture` là `OnTouchListener` trả `false`, nên `onTouchEvent` vẫn nhận (đã đúng hôm nay với huy hiệu "−"). Khi `invalidateGlass` cần thì `invalidate()` mỗi khung của spring (ô có kính). `bindAllCells` của lưới đang `animate().cancel(); scaleX = 1f`: đổi sang huỷ spring của ô.
- [x] **Step 2: Ô nhóm Library:** chạm vào vùng nền ô, "+N", khe, ô trống → cả khung 2×2 (phần tử kính) co; chạm vào icon lớn hoặc icon mini → KHÔNG co (chủ dự án: icon app thường không co); dùng listener cảm ứng bọc ngoài `OnClickListener` (không nuốt click).
- [x] **Step 3: `SearchPillView` và thanh tìm kiếm Library:** thay/bổ sung lớp tô `pressPaint` bằng scale spring (giữ lớp tô nếu chủ dự án muốn); `SearchPillView` đã có `isPressed`.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — (a) giữ ngón trên ô thư mục: co 5%, nhả: về cỡ cũ với chút nảy, panel mở; (b) giữ rồi kéo (edit mode): ô co khi giữ rồi biến mất khi kéo (alpha 0), không kẹt co khi thả về; (c) giữ ô thư mục lâu (vào edit mode): hết kẹt co sau khi vào edit mode; (d) pill và thanh Library co khi giữ; ô nhóm: icon co riêng, vùng nền co cả khung; (e) kính trên ô co vẫn bám nền (nhìn khung co, không lệch lát nền); (f) dock không đổi. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 5: Commit** `feat: shrink glass elements while pressed with a spring`.

---

### Task 7: Hover tạo thư mục — kính phía sau icon đích; phóng thư mục có sẵn (code xong 2026-11-04; kiểm tay gộp với Task 8)

**Phase:** A · **Bắt buộc kiểm tay: CÓ, GỘP với Task 8 thành MỘT phiên của chủ dự án sau Task 8** (mục 4.2 cho phép; điều phối kịch bản hoá bằng `adb shell input motionevent` và tự kiểm cơ học sau Task 7, chủ dự án chỉ phán cảm giác một lần; Task 8 làm ngay sau khi Task 7 qua review, không dừng chờ) — lý do (mục 4.2): đổi hành vi của cử chỉ **kéo-thả** (D4/D5 của G2a) và vẽ kính trong lúc ô đang rung; lỗi loại này review code khó thấy (bài học M7), và có phép đo hiệu năng cảnh C3.
**Files:** Modify `ui/home/view/IconGridView.kt`, `ui/home/view/IconCellView.kt`.
**Interfaces:** Consumes `SpringProgress.HOVER`, `BackdropBlurDrawable` (Task 2). Produces: không đổi API ngoài; `FOLDER_INTENT_SCALE`/`FOLDER_INTENT_ANIM_MS` bị xoá.

- [x] **Step 1: Bỏ hiệu ứng phóng icon cũ:** `armFolderIntent`/`releaseArmedScale` không còn `animate().scaleX/Y` cho ô **app** đích.
- [x] **Step 2: Kính hover (đích là app 1×1):** `IconGridView` giữ một `BackdropBlurDrawable` (tạo lười, vai trò `FOLDER_CELL`, chủ sở hữu = chính lưới; tier `FLAT` thì tô `folder_background`), `hoverProgress` bằng `SpringProgress.HOVER` (vào: 0→1 với chút nảy; ra: 1→0), biên = hình vuông quanh icon đích cạnh `iconSize × HOVER_GLASS_SCALE` (1.25, hằng chỉnh tay), bán kính = 0.225 × cạnh. Vẽ trong `dispatchDraw` **trước** `super.dispatchDraw` (kính phía sau icon, trước mọi con); chỉ vẽ khi `hoverProgress > 0`; đang spring thì bỏ viền (bớt lệnh vẽ). Lưới `invalidate()` mỗi khung của spring và khi pager cuộn (nối vào `invalidateGlass()`). **Không** `addView`, không đụng `childCount`.
- [x] **Step 3: Phóng thư mục có sẵn (đích là thư mục chưa đầy):** ô thư mục đích dùng spring scale của ô (Task 6), đích 1.12, pivot tâm icon, **phóng ngay khi vào ô** (không dwell, không kính mới); thư mục đầy: không phóng, không kính (như hôm nay). Khi `cancelFolderIntent`/mép ≥ 500 ms (`switchToPush`)/thả/`onDragExit`/`onDetachedFromWindow`: hover glass và phóng về 0, sạch hết.
- [ ] **Step 4: Kịch bản kiểm (Live 4, rồi Oppo cho kính; `input motionevent` cho cảnh C3)** — (a) kéo app A lên app B: kính hiện sau B, to hơn icon, B không phóng, A bay phía trên; thả: tạo thư mục (A2 hồi quy); (b) kéo A rời B không thả: kính thu lại; kéo qua nhiều ô không dừng: kính chạy theo, không dư; (c) kéo lên ô thư mục có chỗ: thư mục phóng ngay, **không** có kính; thả: thêm vào; (d) thư mục đầy: không phóng, thả: A bay về; (e) giữ ở MÉP ô B ≥ 500 ms: kính thu lại, đẩy-dồn như M7 (D4); quay vào giữa trước 500 ms: không đẩy; (f) trong edit mode (ô rung): kính và phóng vẫn đúng chỗ; (g) kéo từ dock lên app: tạo thư mục, từ con thư mục (panel) lên app khác: tạo thư mục mới (A3b); (h) kéo thư mục lên app: không gộp, không kính; (i) lật trang bằng kéo sát mép khi đang hover: kính biến mất sạch; (j) ảnh bay lúc kéo không chứa kính; menu nhấn giữ không chứa kính; (k) hồi quy kéo-thả/lật trang/trang tạm/"−" (M7); (l) cảnh C3 và C5–C7 theo "Giao thức đo": Janky ≤ +2.0 (Oppo +1.0), Δp95 ≤ +4 ms. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 5: Commit** `feat: show a glass behind the drop target when creating a folder`.

---

### Task 8: Icon trượt khi xem trước đẩy-dồn và khi bố cục đổi (xong 2026-11-04; kiểm tay chung với Task 7)

**Phase:** A · **Bắt buộc kiểm tay: CÓ (một phiên chung cho Task 7 và 8)** — lý do (mục 4.2): chạm vào đường kéo-thả bài M7 đã vá nhiều lần (view bị thay, độ dời tồn); phải chạy thật.
**Files:** Modify `ui/home/view/IconCellView.kt`, `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt`.
**Interfaces:** Consumes `SpringProgress.SLIDE`. Produces: `IconCellView.springOffsetTo(dx, dy)`, `setOffsetImmediate(dx, dy)`, `currentOffset` (hai spring cho `translationX/Y`, độc lập với `rotation` của `Jiggler`).

- [x] **Step 1: Preview thành spring.** `IconGridView.applyPreview` / `clearPreview` và `DockView.applyPreview` / `clearPreview` đổi `translationX/Y = …` thành `springOffsetTo(…)`; giữ nguyên thuật toán chọn ô (dồn dây chuyền, ô tràn trang `alpha=0`).
- [x] **Step 2: FLIP khi bố cục đổi.** Trước khi bind, `setItems` ghi `visualPos[itemId] = (left + translationX, top + translationY)` của từng con; chỉ khi `pageId` không đổi, `Cells` không đổi và không phải lần bind đầu thì đặt `pendingSlide`; trong `onLayout` sau khi `cell.layout(...)`, với ô có `itemId` trong `pendingSlide` và khác vị trí: `setOffsetImmediate(cũ − mới)` rồi `springOffsetTo(0, 0)`. Ô mới (không có vị trí cũ) hiện tại chỗ; ô bị bỏ biến mất như hôm nay. Khoá theo `itemId`, **không** theo chỉ số con.
- [x] **Step 3: Thả được nhận thì GIỮ độ dời xem trước.** `IconGridView.onDrop` trả `true` / `endDragVisualState(accepted = true)` không reset độ dời của ô bị đẩy (hôm nay `clearPreview()` gọi ngay) để FLIP xuất phát từ vị trí nhìn thấy; thả không được nhận/hủy: `springOffsetTo(0,0)` (ô trượt về). **Đường dọn:** nếu 300 ms sau thả được nhận mà không có `setItems` nào tới thì spring mọi độ dời tồn về 0 (VM có thể phát lại danh sách y hệt hoặc không phát).
- [x] **Step 4: Dock:** `DockView.onLayout` tính lại vị trí căn giữa nên dùng cùng mẫu FLIP; **đọc `DockView.setItems/onLayout` kỹ trước khi sửa** (dock căn giữa lại khi app rời/thêm) — nếu khác biệt lớn thì chỉ làm phần preview và ghi lại vào Nhật ký.
- [x] **Step 5: `bindAllCells` huỷ spring offset** của ô khi gán lại item cho ô (ô tái dùng theo chỉ số), `setEditMode`/`Jiggler` không đụng offset.
- [ ] **Step 6: Kịch bản kiểm (Live 4; `input motionevent` cho C4)** — (a) kéo icon qua một hàng: các icon bị đẩy trượt mượt sang ô bên cạnh và trượt lại khi ngón tay rời; (b) thả: icon bị đẩy KHÔNG nhảy về ô cũ rồi mới trượt, mà đi thẳng vào vị trí mới (đây là mục chính của Task); (c) kéo một icon về **đúng ô cũ** rồi thả, rồi kéo lần nữa: không biến mất (bài học M7); (d) kéo icon hai lần liên tiếp thật nhanh; (e) bấm "−" ẩn một app: các icon sau trượt dồn lên (câu hỏi mở 4); (f) kéo icon sang trang khác (lật trang): ô trên trang mới không trượt từ vị trí trang cũ; (g) kéo app từ lưới xuống dock và ngược lại: dock căn giữa lại mượt, không nhảy; (h) đổi cỡ lưới trong Cài đặt: không trượt lung tung khi bind lại toàn bộ; (i) xoay màn hình: không trượt; (j) ô tràn trang (trang đầy 24/24): icon cuối ẩn đúng như M7; (k) thả vào thư mục: không để lại ô dịch tồn; (l) cảnh C4 + C5–C7 theo "Giao thức đo". **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 7: Commit** `feat: slide icons into place when the grid reflows`.

---

### Task 9: Hạ cánh của ảnh bay lúc thả (xong 2026-11-04, chờ review điều phối)

**Phase:** A · **Bắt buộc kiểm tay:** không (nằm trên đường đã được Task 8 kiểm; kiểm lại trong bài kiểm cuối. Lý do không đánh dấu: cử chỉ không đổi, chỉ thêm hoạt ảnh sau khi `DragHelper.drop` đã quyết xong)
**Files:** Modify `core/ui/DragHelper.kt`, `core/ui/DragDropTarget.kt`, `ui/home/view/DragLayer.kt`, `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt`.
**Interfaces:** Produces: `DragDropTarget.dropSettleRect(): ScreenRect?` (mặc định `null`), `DragDropTarget.onDragSettled()` (mặc định rỗng); `DragHelper.settlingItemId: Long?`; `DragVisual.endDrag()` giữ nguyên, thêm `settleTo(rect, endScale, endAlpha, onEnd)` (vì `HomeActivity` gọi `dragLayer.endDrag()` trực tiếp ở dòng ~715 cho menu nên không đổi chữ ký cũ).

- [x] **Step 1:** `IconGridView.onDrop` / `DockView.onDrop` khi nhận thả (`true`) ghi `dropSettleRect` = khung ô đích trên màn hình (ô mới của item) hoặc, với thả vào thư mục/tạo thư mục, khung ô thư mục kèm `endScale ≈ 0.4`, `endAlpha = 0`.
- [x] **Step 2:** `DragHelper.drop`: nếu target nhận và có `dropSettleRect` thì đặt `settlingItemId = session.itemId` và gọi `dragVisual.settleTo(...)` (spring từ vị trí ngón tay tới đích, `SpringProgress.SETTLE`); không thì `endDrag()` tức thì như hôm nay. Khi hạ cánh xong: xoá `settlingItemId`, `registeredTargets.forEach { it.onDragSettled() }`.
- [x] **Step 3:** `bindAllCells` (lưới) và `DockView` đặt `alpha` ô = 0 nếu `itemId == hiddenItemId || itemId == DragHelper.settlingItemId` (**ngoài view**, không mất khi ô bị tái dùng); `onDragSettled()` đặt lại alpha theo dữ liệu. Phiên kéo mới (`beginDrag`) hoặc `detachDragVisual` kết thúc hạ cánh ngay (huỷ spring, xoá `settlingItemId`, gọi `onDragSettled`).
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — (a) thả icon vào ô trống: ảnh trượt về ô và dừng khớp, không thấy hai bản icon, không nháy; (b) thả vào ô có icon (đẩy-dồn): ảnh hạ cánh vào ô, các icon khác đã trượt (Task 8); (c) thả vào thư mục / tạo thư mục: ảnh thu nhỏ và mờ vào ô thư mục; (d) thả ra ngoài vùng hợp lệ/hủy: icon quay về chỗ cũ như hôm nay (không hạ cánh); (e) bắt đầu kéo icon khác ngay khi icon trước đang hạ cánh: không kẹt ô ẩn; (f) kill app giữa lúc hạ cánh, mở lại: icon hiện đủ; (g) kéo vào dock và ra khỏi dock: hạ cánh đúng ô dock; (h) hồi quy kéo-thả M7. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 5: Commit** `feat: settle the dragged icon into its drop cell with a spring`.

---

### Task 9b: Vùng giữa để tạo thư mục (thấy gì thì được nấy) + dồn trang khi app vào thư mục (xong 2026-11-04; PHẦN DỒN TRANG ĐÃ HOÀN TÁC Ở TASK 9c; độ trễ đẩy-dồn đổi 150 → 500 ms ở Task 9c)

**Nguồn:** hai thay đổi do chủ dự án quyết 2026-11-04 trước bài kiểm tay (quyết định 18, 19). **Bắt buộc kiểm tay:** có, gộp vào phiên kiểm của Task 7 và 8 (cử chỉ kéo-thả, mục 4.2). **Phase:** A (không đụng HomeActivity).
**Files:** Modify `domain/layout/FolderPlanner.kt` (hàm thuần, hằng vùng giữa), `ui/home/view/IconGridView.kt` (máy trạng thái hover), `data/layout/LayoutReconciler.kt` (`createFolder`, `addToFolder`), `ui/home/HomeViewModel.kt` (cập nhật lạc quan `onFolderIntent`, `onAddToFolder`; file nóng, được điều phối cho phép vì R1 và G2b không đụng). Không đổi schema Room.

**Sửa 1 — vùng giữa và quy tắc "thấy gì thì được nấy".**
- Trong vùng giữa của icon đích: sau `HOVER_ARM_MS` hiện kính (đích là app) hoặc phóng thư mục (đích là thư mục); thả = tạo thư mục / thêm vào thư mục. Ngoài vùng giữa: không kính, không phóng, thả = chèn đẩy-dồn M7. Không còn đường nào mà thả ở mép vẫn gộp; `FolderPlanner.DWELL_MS` (500 ms ở mép) bị bỏ.
- **Cỡ vùng giữa (hằng chỉnh tay, `FolderPlanner`):** tính theo ICON (hình vuông cạnh `iconSize`, tâm là tâm icon), không theo cả ô có nhãn. **Cũ:** rộng 0.5 bề rộng ô, cao 0.5 cạnh icon, tức ~45 × 26 dp trên Live 4 (ô ~90 dp, icon 52 dp; ước tính, chưa đo bằng uiautomator) = 0.5 bề rộng ô × 0.29 chiều cao ô (0.87 × 0.5 cạnh icon). **Mới:** vào khi trong hình vuông cạnh `CENTER_ENTER_FRACTION` = 0.9 cạnh icon (~47 × 47 dp = 0.52 bề rộng ô × 0.62 chiều cao ô; diện tích gấp ~1.8 lần cũ), ra khi vượt hình vuông cạnh `CENTER_EXIT_FRACTION` = 1.1 cạnh icon (~57 dp). Khoảng trễ 0.9 / 1.1 để kính không nhấp nháy ở biên. Đo theo icon vì người dùng nhắm vào hình icon, còn ô có nhãn và khe nên vùng "giữa ô" lệch so với thứ nhìn thấy.
- **Hành vi ở mép (ô có item nhưng ngoài vùng giữa): xem trước đẩy-dồn SAU `PUSH_ARM_MS` = 150 ms**, không ngay lập tức. Lý do: kéo nhanh ngang qua một hàng icon, mỗi ô đi qua sẽ bật rồi tắt cả chuỗi đẩy (một loạt icon trượt ra vào) và kính; độ trễ ngắn bắt được cú lướt (vùng giữa ~47 dp ở 600 dp/s mất ~80 ms, nên cũng cần `HOVER_ARM_MS` = 100 ms cho kính/phóng) mà vẫn đủ nhanh khi dừng thật sự. Rời trạng thái đang hiện thì bỏ NGAY (không chờ). Trong thời gian chờ không hiện gì, và thả lúc đó = chèn (đúng thứ nhìn thấy). Ô trống: không làm gì. Cả hai độ trễ là hằng chỉnh tay.
- Giữ nguyên các quy tắc cũ: thư mục không gộp vào thư mục; thư mục đầy: ở vùng giữa không phóng, thả bị từ chối và icon bay về; thả lại đúng thư mục nguồn của app con = hủy kéo ra.

**Sửa 2 — dồn trang khi app vào thư mục (ĐÃ HOÀN TÁC ở Task 9c, đoạn này chỉ còn để lưu vết).** `LayoutReconciler.addToFolder` gọi `compactPageAfterRemoval` (cùng logic ẩn/gỡ, `GridRemovePlanner`) khi nguồn ở lưới chính; `createFolder` tính `planCompaction` TRƯỚC khi chèn hàng thư mục để thư mục mới nằm ở ô của đích SAU khi dồn (nếu app nguồn đứng trước đích cùng trang thì đích và các icon sau dịch lên 1 bậc). Nguồn ở trang khác thì dồn trang của nguồn; nguồn từ dock không dồn (dock tự căn giữa); nguồn là con của một thư mục khác không chiếm ô lưới nên không dồn (kéo app RA khỏi thư mục không đổi hành vi). Cập nhật lạc quan ở `HomeViewModel` dùng đúng `removeAndCompact` (= `GridRemovePlanner`) và lấy vị trí thư mục mới từ trang đã dồn; icon trượt bằng FLIP của Task 8. Trang nguồn thành trống: giữ như ẩn app (không xóa trang).
**Đã kiểm bằng đọc code (chưa có máy):** (1) nguồn trước đích cùng trang → thư mục ở ô dịch, DB và UI cùng ô; (2) nguồn sau đích → đích không đổi ô; (3) nguồn trang khác → đích không đổi, trang nguồn dồn; (4) thêm vào thư mục nằm sau nguồn → ô thư mục dịch lên ở cả DB (`compactPageAfterRemoval` ghi lại hàng thư mục) và UI (`removeAndCompact` đổi vị trí `PlacedFolder`, rồi cập nhật con bằng `it`, không dùng bản `folder` cũ); (5) nguồn dock → không dồn; (6) nguồn con thư mục (`fromChild`) → không dồn, vẫn `dissolveFolderIfNeeded`; (7) thư mục đầy / không hợp lệ → DB từ chối trước khi dồn, UI đã về DB.

- [x] **Step 1:** `FolderPlanner`: bỏ `DWELL_MS`/`CENTER_ZONE_FRACTION`, thêm `CENTER_ENTER_FRACTION`, `CENTER_EXIT_FRACTION`, `isInCenterZone(dx, dy, iconSize, wasInside)`.
- [x] **Step 2:** `IconGridView`: máy trạng thái `HoverMode { NONE, MERGE, PUSH }` (`desiredHover`, `evaluateHover`, `applyHover`, `clearShownHover`), `HOVER_ARM_MS`, `PUSH_ARM_MS`; `onDragMove` đánh giá mỗi lần chạm (không còn thoát sớm theo ô); `onDrop` chỉ gộp khi đang ở trạng thái MERGE hiển thị.
- [x] **Step 3:** `LayoutReconciler.createFolder`/`addToFolder` và `HomeViewModel.onFolderIntent`/`onAddToFolder` dồn trang.
- [ ] **Step 4: Kịch bản kiểm (Live 4, gộp vào phiên Task 7+8)** — (a) kéo app A tới icon B: ngón tay vào giữa icon B (~nửa icon) → kính hiện sau ~0.1 s, thả = thư mục; (b) kéo vào mép/khe/nhãn của B → không kính, các icon đẩy-dồn sau ~0.15 s, thả = chèn như M7 (KHÔNG tạo thư mục, kể cả thả rất nhanh); (c) lướt nhanh qua cả hàng icon không dừng: không kính/đẩy nhấp nháy; (d) rê qua biên vùng giữa chậm: kính không nhấp nháy (khoảng trễ); (e) vùng giữa của thư mục: phóng, thả = thêm; mép thư mục: không phóng, thả = chèn; thư mục đầy ở giữa: không phóng, thả = bay về; (f) A đứng trước B cùng trang, tạo thư mục: các icon sau A dồn lên, thư mục nằm ở ô B mới, trượt mượt (FLIP); (g) A ở trang khác với B: trang của A dồn, B không đổi; (h) thêm vào thư mục nằm sau A: dồn, ô thư mục dịch; (i) A từ dock: dock căn giữa lại, lưới không dồn; (j) kéo app RA khỏi thư mục thả lên app/thư mục khác: hành vi như trước; (k) kill app và mở lại: bố cục khớp (DB = UI); (l) kéo thư mục lên app: không gộp, đẩy-dồn như M7. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 5: Commit** `feat: make folder creation follow what the hover shows and compact the source page`.

---

### Task 9c: Hoàn tác dồn trang khi app vào thư mục + đẩy-dồn ở mép chờ ngón tay dừng (xong 2026-11-04, chờ review điều phối)

**Nguồn:** hai quyết định của chủ dự án sau khi kiểm tra iOS (2026-11-04; quyết định 19, 20). **Bắt buộc kiểm tay:** có, gộp vào phiên kiểm Task 7+8+9b. **Files:** `data/layout/LayoutReconciler.kt` và `ui/home/HomeViewModel.kt` (khôi phục NGUYÊN trạng thái trước Task 9b: không còn diff nào của Sửa 2), `ui/home/view/IconGridView.kt`.

1. **Hoàn tác Sửa 2 của Task 9b hoàn toàn.** Thêm vào thư mục có sẵn hoặc tạo thư mục mới lại để ô nguồn TRỐNG, không dồn (cả DB lẫn cập nhật lạc quan). Lý do chủ dự án: chưa xác nhận iOS 26 làm vậy; và dồn mâu thuẫn với logic di chuyển thường (vốn có thể để lại khe trống). `git checkout 9d2e479 -- LayoutReconciler.kt HomeViewModel.kt` (commit trước Task 9b), nên hai file này không còn khác biệt nào so với trước Sửa 2. Giữ nguyên Sửa 1 (vùng giữa, thấy gì thì được nấy).
2. **Đẩy-dồn ở mép chờ ngón tay dừng.** Chủ dự án: trên đường tới tâm icon luôn có một hoạt ảnh đẩy ngắn nhưng nhìn thấy được. `PUSH_ARM_MS` 150 → **500 ms** (đề xuất đầu 300 ms, chủ dự án chốt 500); hẹn giờ đếm lại mỗi khi ngón tay dời hơn `PUSH_SETTLE_DP` = **8 dp** kể từ lúc bắt đầu đếm (neo là vị trí ngón tay lúc bắt đầu/đếm lại), nên đẩy-dồn chỉ chạy khi ngón tay thật sự đứng ở mép. Dùng `postDelayed` (ngón tay đứng yên thì không có sự kiện chạm). Cả hai là hằng chỉnh tay trong `IconGridView`. Độ trễ kính ở vùng giữa vẫn 100 ms.

- [x] **Step 1:** khôi phục `LayoutReconciler.kt`, `HomeViewModel.kt` về trước Task 9b.
- [x] **Step 2:** `IconGridView.evaluateHover`: neo `pushAnchorX/Y`, đếm lại khi dời > 8 dp; `PUSH_ARM_MS = 300`.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — (a) kéo app A từ xa vào giữa icon B theo đường thẳng không dừng: không thấy icon nào bị đẩy trên đường đi; (b) kéo vào mép/khe của B rồi giữ yên ~0.5 s: đẩy-dồn mới chạy; (c) rê chậm qua mép liên tục (dời > 8 dp mỗi lần): không đẩy cho tới khi dừng; (d) kéo app vào thư mục (thêm / tạo): ô nguồn TRỐNG, các icon khác không dồn, giống trước Task 9b; (e) kill và mở lại: bố cục khớp DB. **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 4: Commit** `feat: wait for the finger to settle before pushing icons and revert source page compaction`.

---

### Task 9d: Icon thu về 0 khi bị gỡ/ẩn, phình từ 0 khi mới cài; icon trong panel thư mục rung ở chế độ chỉnh sửa (xong 2026-11-04, chờ review điều phối)

**Nguồn:** phản hồi kiểm tay của chủ dự án trên `87f58ae` (2026-11-04). **Bắt buộc kiểm tay:** không (hình ảnh, không đổi cử chỉ; gộp vào bài kiểm cuối). **Files:** `ui/home/view/LeavingIcons.kt` (mới), `IconGridView.kt`, `DockView.kt`, `FolderPanelView.kt` (`AppsGrid`), `IconCellView.kt` (`popIn`), `core/ui/SpringProgress.kt` (preset `EXIT`).

1. **Gỡ/ẩn/cài.** Icon bị ẩn bằng "−" hoặc gỡ cài đặt thu về 0 TẠI TÂM ICON của nó (spring tới hạn, không nảy, ~200 ms), rồi các icon sau mới trượt (chờ `EXIT_SLIDE_DELAY_MS` = 180 ms). App mới cài / bỏ ẩn phình từ 0 tại ô của nó (`popIn`, có chút nảy). Áp cho lưới, dock và panel thư mục (panel: chỉ thu + trượt, không phình). **Cơ chế:** ô được dựng lại ở `setItems` và "con i = ô i", nên icon rời đi KHÔNG là con của lưới: `LeavingIcons` chụp ảnh ô (`snapshotWithoutBadge`) TRƯỚC khi đổi dữ liệu, khoá theo itemId (chỉ những id có trong danh sách cũ mà không có trong danh sách mới) và vẽ riêng trong `dispatchDraw` bằng một spring. **Bỏ qua:** lần bind đầu, `recreate()` (view mới), đổi cỡ lưới (`metrics` hoặc `Cells` đổi), đổi trang (`pageId` khác), item đang bị kéo / đang hạ cánh (đã có hiệu ứng riêng ở Task 9). **Thay tại chỗ không hoạt ảnh kép:** item bị gỡ và item mới xuất hiện CÙNG ô (thư mục giải tán D9: con thế vào ô thư mục; tạo thư mục: thư mục thế vào ô đích, kể cả id tạm âm → id thật) thì cả hai bỏ qua. Ca còn thiếu đã biết: app bị đẩy sang trang kế do trang đầy (cascade) sẽ thu lại trên trang cũ, hiếm.
2. **Rung trong panel thư mục.** `AppsGrid` dùng `Jiggler` dùng chung: rung khi `editMode`, dừng khi tắt chế độ hoặc khi panel đóng hẳn (`onSettled` ở 0) hoặc tách khỏi cửa sổ; mở lại panel lúc đang ở chế độ chỉnh sửa thì khởi động lại (`syncJiggle(true)` ở nhánh mở). Rung chỉ đổi `rotation` của từng ô nên không tranh với spring mở panel (transform ở `panel`, không phải ô). Ảnh chụp lúc kéo ra không chứa góc xoay (`View.draw` không áp transform của chính nó), cùng cách với lưới.
3. **Ghi nhận, không đổi code:** thả vào ô đang có icon thì khe được lấp sau ô đích là luật của `GridInsertPlanner`, chốt theo khảo sát iPhone 2026-10-28; giữ.

- [x] **Step 1:** `LeavingIcons`, `EXIT`, `popIn`; nối vào `IconGridView`/`DockView`/`AppsGrid` (so danh sách cũ/mới, trượt trễ).
- [x] **Step 2:** rung trong `AppsGrid`.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — (a) edit mode, bấm "−" trên một app: icon thu về 0 tại chỗ rồi các icon sau trượt; (b) gỡ cài đặt một app (hoặc ẩn từ Cài đặt rồi về Home): thu rồi trượt; (c) cài app mới / bỏ ẩn một app: ô phình từ 0; (d) dock: "−" và bỏ ẩn tương tự; (e) mở panel thư mục ở edit mode: icon trong panel rung; bấm "−" trong panel: thu rồi trượt; tắt edit mode / đóng panel: ngừng rung; mở lại: rung lại; kéo một icon ra khỏi panel khi đang rung: vẫn kéo được; (f) hồi quy: kéo-thả, tạo/thêm thư mục (không thu rồi phình ở ô đích), thư mục giải tán còn một app (không hoạt ảnh kép), đổi cỡ lưới / xoay (không thu/phình), mở app lần đầu (bind đầu không phình). **[chưa tick: không có bản ghi kết quả từng ý; chủ dự án kiểm bằng cảm giác ở phiên kiểm tay]**
- [x] **Step 4: Commit** `feat: shrink removed icons and grow new ones, jiggle icons in the folder panel`.

---

### Task 9e: Đã đẩy-dồn thì ở lại trong ô neo (xong 2026-11-04; Oppo đạt 2026-11-04: không đẩy trước 500 ms, đẩy sau đó, ở lại khi ngón tay vào giữa khe, thả vào đúng khe)

**Nguồn:** quyết định của chủ dự án sau bài kiểm tay (2026-11-04, quyết định 21). **Bắt buộc kiểm tay:** có, gộp vào phiên kiểm kéo-thả. **Files:** `ui/home/view/IconGridView.kt`.

**Luật mới của máy trạng thái hover.** Khi xem trước đẩy-dồn (PUSH) đã chạy, nó Ở LẠI chừng nào ngón tay còn trong ô nơi nó được kích hoạt (ô neo), kể cả khi ngón tay vào giữa ô: không kính, không phóng, hàng icon không nhảy về; thả ở đó = đặt icon vào khe vừa mở (ô thả là ô neo, không phải ô dưới ngón tay, để khoảng trễ ở biên không làm lệch). Muốn tạo thư mục phải đưa ngón tay tới một icon thật ở chỗ mới của nó. Hàng chỉ về khi ngón tay rời ô neo sang ô khác (khoảng trễ `PUSH_ANCHOR_HYST_DP` = 8 dp mở rộng ô neo mỗi phía để ngón tay ở biên không lật qua lại). Rời sang ô trống hoặc đích khác thì theo luật thường: MERGE cần vùng giữa của một icon, sau 100 ms.

**Đích gộp theo vị trí NHÌN THẤY.** `desiredHover` không còn tra "item ở ô layout dưới ngón tay" mà duyệt các icon gộp được (app 1×1, thư mục) và lấy cái có tâm nhìn thấy (layout + độ dời đang trượt) gần ngón tay nhất trong vùng giữa. Nên một icon đang bị đẩy sang ô khác vẫn là đích gộp tại chỗ mới của nó, thư mục cũng vậy. Khi đích gộp là icon ĐANG BỊ ĐẨY, hàng được GIỮ nguyên (không kéo icon đích ngược khỏi ngón tay); chọn thứ khác thì hàng về. Hạ cánh vào thư mục dùng vị trí nhìn thấy của đích.

- [x] **Step 1:** `fingerInCell`, neo PUSH, duyệt đích gộp theo vị trí nhìn thấy, `keepRow`, ô thả = ô neo, hạ cánh theo vị trí nhìn thấy.
- [ ] **Step 2: Kịch bản kiểm (Live 4)** — (a) kéo vào mép ô B rồi giữ >0.5 s: hàng đẩy; di ngón tay vào GIỮA ô B (khe): không kính, không phóng, hàng không về; thả: icon nằm đúng khe đó; (b) từ trạng thái (a) di sang ô kế (icon bị đẩy đang nằm ở đó): kính hiện sau 0.1 s trên icon đó tại chỗ nhìn thấy của nó, thả = tạo thư mục; cùng với thư mục (phóng, thả = thêm); (c) từ (a) rời sang ô trống: hàng về; (d) ngón tay ở biên ô neo: không lật qua lại; (e) tới thẳng giữa icon (chưa đẩy): kính như Task 9b (không đẩy trên đường đi). **[chưa tick: (a) đạt trên Oppo 2026-11-04 (Nhật ký 9e); (b)–(e) không có bản ghi]**
- [x] **Step 3: Commit** `feat: keep the push preview while the finger stays in its anchor cell`.

---

### Task 9f: Dock kẹt 0×0 sau `recreate()` ở chế độ chỉnh sửa (lỗi có từ trước AN, điều phối truy ra trên Live 4; xong 2026-11-04, điều phối đã dựng lại 40 lần: không kẹt)

**Nguồn:** điều phối bắt được: bật/tắt `cmd uimode night yes/no` lúc đang ở edit mode, dock có thể co về 0×0 và kẹt tới khi khởi động lại tiến trình (2 trong 4 icon hiện sai chỗ, không kính). Tái hiện cả ở bản trước R1 (3aaad60, dựng lại lần 23) nên không do R1. `dumpsys activity top`: `DockView ... 24,1733-24,1733`, kính 0,0-0,0, trong khi pager cao 1589 như thể dock chỉ cao 289. **Bắt buộc kiểm tay:** không (điều phối stress-test).
**Nguyên nhân (đọc code):** `DockView.onMeasure` gọi `setMeasuredDimension(0, 0)` khi `cells == null`. Cells đến từ listener layout của pager (`DockController.updateDockCells` → `DockView.setItems` → `requestLayout()`) NGAY TRONG một lượt layout, nên yêu cầu layout đó có thể bị mất và phép đo 0×0 kẹt lại.
**Sửa (`DockView`):** (1) `onMeasure` luôn lấy kích thước từ spec (chiều cao do trọng số layout cấp, không phụ thuộc cells) và luôn đo view kính (cao bằng dock khi chưa có cells); chỉ bind icon khi có cells. (2) `setItems` gọi `requestLayout()` qua `post` nếu `isInLayout`.
**Đã kiểm "có gì dựa vào đường 0×0 không":** chỉ có ghi chú M5 Task 4 / Review Focus 5 ("chia đôi màn hình nhỏ hết mức: lưới/dock có thể tự ẩn 0×0 như hành vi cũ"). Hành vi mới ở cửa sổ quá nhỏ (`GridMetrics.measure` trả null): dock vẫn giữ chỗ do layout cấp và chỉ vẽ viên kính, không icon (`onLayout` với cells null đã không đặt icon nào và đặt kính phủ kín), không crash; lưới chính vẫn tự ẩn như cũ. `needsBind` được xử lý lại khi cells về. Chưa kiểm trên máy với split-screen nhỏ hết mức (điều phối).
- [x] **Step 1:** sửa `onMeasure` và `setItems` trong `DockView`.
- [ ] **Step 2: Kịch bản kiểm (điều phối)** — vào edit mode, bật/tắt `cmd uimode night yes/no` 40 lần: dock luôn đủ 4 icon, đúng chỗ, có kính; chia đôi màn hình nhỏ hết mức rồi trả lại: dock hồi phục. **[chưa tick trọn: phần 40 lần dựng lại đạt trên Live 4, không lần nào kẹt 0×0 (trước khi sửa hỏng ở lần 11 và lần 23); phần chia đôi màn hình không có ghi nhận riêng]**
- [x] **Step 3: Commit** `fix: keep the dock measured from its specs when cells are not ready`.

---

### Task 9g: Kính thật của panel không đổi hình khi spring dừng (xong 2026-11-04; đóng 2026-11-05 bằng mắt chủ dự án trên Oppo)

**Nguồn:** chủ dự án: kính panel khi mở thư mục "mắt thường thấy nó đổi từ cũ sang mới khi hoạt ảnh xong". Ảnh chụp giữa chừng của điều phối cho thấy ảnh nền ở vị trí khác với khung đã dừng. **Bắt buộc kiểm tay:** không (điều phối chụp ảnh và đo trên Oppo). **Files:** `core/glass/RealGlassSurface.kt`, `ui/home/view/BackgroundGlass.kt`, `FolderPanelView.kt`, `CategoryGroupPanelView.kt`, `PanelMotion.kt` (chỉ chú thích).
**Nguyên nhân đã xác nhận bằng `javap` (`GlassLensRenderer.draw`/`recordBackdrop`, `BackdropCapture`, v2.0.11):** đường Lens đặt cửa sổ lấy nền = `glassView.getLocationOnScreen() − backdropSource.getLocationOnScreen()` rồi ghi nền với `translate(margin − x, margin − y)`; cỡ cửa sổ là cỡ view CHƯA co. `getLocationOnScreen()` tính cả scale/dịch của chính view. Lúc spring chạy, view kính đã co/dịch về icon, nên lần chụp do `onShown` đặt lịch (chạy lúc p còn nhỏ) lấy nhầm vùng ảnh nền quanh icon rồi co nhỏ ảnh đó: hình lúc co là "ảnh nền thu nhỏ ở sai chỗ". Kính bị chặn `invalidate()` nên hình sai đứng nguyên đến lúc dừng, khi `captureOnce()` chụp lại đúng vùng (scale 1): bước nhảy. Giả thuyết "kính giữ ảnh cũ do bị chặn" chỉ đúng một nửa: ảnh cũ (lần dừng trước) là ảnh đúng, bị hình sai thay ngay sau khi hiện.
**Đường kính mờ tĩnh (`BackdropBlurDrawable`, Live 4 mặc định và máy cũ): đọc code thì không lỗi.** `drawScaled` vẽ trong "không gian ảnh nền": điểm cục bộ p hiện ở `L' + s·p` và lấy nền ở `L' + s·p`; công thức liên tục tới `s = 1` (trùng nhánh không co), bán kính và viền nhân `s` bằng đúng phép co của RenderNode, owner `invalidate()` mỗi khung. Không sửa. Kiểm bằng số (không cần máy): panel 600x400 cuối ở (240,500), pivot giữa, s = 0.5, nền ở (0,0): góc đã co L' = (390,600); `drawScaled` vẽ hình (390..690, 600..800) trong không gian nền, canvas co 1/s rồi RenderNode co s nên hiện đúng ở (390..690, 600..800) trên màn hình và lấy mẫu nền đúng ở chính toạ độ đó, giống nhánh s = 1 (nền ở đúng chỗ của nó). Live 4 trả lời "FORCE_HIGH": `GlassPolicy` chỉ cho panel kính thật khi tier LENS VÀ máy có đường Lens (API 33+), nên Live 4 (API 30) luôn dùng đường này ở cả AUTO lẫn FORCE_HIGH. Chưa kiểm bằng ảnh chụp: điều phối so ảnh Live 4 ở Step 2 (a); nếu vẫn thấy đổi hình ở Live 4 thì nguyên nhân KHÔNG phải vị trí lát cắt (xem Nhật ký).
**Sửa:** `RealGlassSurface.setCaptureAtLayoutPosition(true)`, bật trong `BackgroundGlass.setRealActive` (chỉ kính của hai panel): `SuppressibleGlassView.getLocationOnScreen` báo vị trí theo chỗ ĐÃ LAYOUT (vị trí cha + `left/top` − scroll của cha), bỏ phép biến đổi của chính view kính (của cha vẫn tính). Mọi lần chụp đều lấy ảnh của chỗ cuối; lúc co, kính co như texture của chính ảnh sẽ hiện lúc dừng, nên không đổi hình khi dừng và khi bắt đầu đóng. Do đó bỏ `captureOnce()` lúc spring dừng ở "mở" (Folder và CategoryGroup): chụp lại là một khung vẽ kính dư đúng lúc dừng; đổi khung kính (danh sách nhóm nạp muộn) vẫn tự vẽ lại vì `layout()` mới không đi qua `invalidate()` bị chặn. Giữ nguyên: kính vẫn chặn `invalidate()` lúc chuyển động, không `setDynamic`, dock không đổi.
**Đánh đổi:** lúc đang co, hình trong panel là ảnh của khung cuối thu nhỏ (như nội dung panel), không phải ảnh nền thật dưới vị trí hiện tại. Nếu mắt thấy không ổn thì phương án tiếp theo là cho kính vẽ lại mỗi khung theo vị trí thật (đắt, KHÔNG làm: xem chi phí 90 khung/lần mở của vòng động 1 giây).
**Chi phí dự kiến:** 0 khung thêm lúc chuyển động; một lần vẽ kính ít hơn S1 (không còn lần ở lúc dừng). Kỳ vọng janky tuyệt đối ≤ S1 (22.3 trên 20 lần mở), p95 ≤ 17 ms; giới hạn Oppo Janky +1.0 điểm, p95 +4 ms so với `main`.

- [x] **Step 1:** đọc `javap`, sửa `RealGlassSurface`, `BackgroundGlass`, bỏ chụp lại lúc dừng; `assembleDebug` xanh.
- [x] **Step 2: Kiểm trên Oppo — đóng bằng mắt chủ dự án.** Chủ dự án báo lần đầu thấy kính đổi hình lúc mở xong trên Oppo, sửa xong kiểm lại và xác nhận "hoạt động đúng" (không còn đổi hình). Loạt ảnh chụp cách quãng của điều phối KHÔNG kết luận được: `screenrecord` bị segfault trên Oppo và `screencap` quá chậm để bắt đúng khung ~100/250 ms. Đo C1 ×20 trên Oppo: AN 24.7 khung janky so với `main` 23, gfx p95 17 ms so với 24 ms (đạt giới hạn). Các ý (b)–(e) cũ (đóng, đảo chiều giữa chừng, panel nhóm, đổi ảnh nền khi panel mở) không có bản ghi riêng; chỉ có xác nhận chung của chủ dự án. Live 4 (kính mờ tĩnh) không đổi mã và không bị bước nhảy này (xem "Đường kính mờ tĩnh" ở trên, suy từ code, chưa chụp ảnh).
- [x] **Step 3: Commit** `fix: capture panel glass at its final position so it does not change when the spring settles`.

---
### Task 9h: Panel nhóm Library trên Live 4 nặng hơn panel thư mục — ĐÓNG, chấp nhận, không sửa mã (2026-11-05)

**Nguồn:** điều phối đo Live 4 (API 30, `FORCE_HIGH` nên panel là kính mờ tĩnh), 15 lần mở/đóng, 3 lượt: panel nhóm AN khoảng 685 khung, 245–354 khung janky, gfx p95 23–27 ms, 32–66 khung > 33 ms; `main` 73 khung, khoảng 50 janky, gfx 22/60 ms, 15 khung > 33 ms (`main` không có hoạt ảnh cho panel nhóm nên chỉ có 1–2 khung dựng nội dung mỗi lần mở); panel THƯ MỤC AN cùng máy khoảng 900 khung, 3.7 janky, gfx 5/8 ms, 0 khung > 33 ms. **Bắt buộc kiểm tay:** không. **Kết quả:** chủ dự án chấp nhận ("nhìn đủ mượt" trên Live 4); KHÔNG sửa mã. `app/` ở cuối nhánh giống hệt `f616627` (mã đã kiểm).
**Đo vòng 1 (điều phối, Live 4 `FORCE_HIGH`, cảnh C2, 15 lần mở/đóng, 3 lượt cho mỗi giá trị, thứ tự 0, 1, 2, 0, 3, 0 để kiểm soát nhiệt) — thử lớp phần cứng:**

| Giá trị `an_group_layer` | Janky | gfx p95 (ms) | framestats p95 (ms) | Khung > 33 ms | record p50 (ms) |
|---|---|---|---|---|---|
| 0, lượt 1 (không lớp) | 272–354 | 23–27 | 32–37 | 32–66 | 0.34–0.44 |
| 1 (lớp phần cứng cho danh sách app) | 336–399 | 28–36 | 37–43 | 67–104 | 0.37–0.43 |
| 2 (lớp phần cứng cho Library) | 302–333 | 25–36 | 35–42 | 49–91 | 0.38–0.50 |
| 0, lượt 2 | 245–319 | 25–26 | 34–36 | 41–61 | 0.38–0.43 |
| 3 (cả hai lớp) | 196–318 | 25–26 | 34–35 | 42–51 | 0.39–0.48 |
| 0, lượt 3 | 285–346 | 25–26 | 35 | 40–55 | 0.35–0.45 |

Tổng khung khoảng 685 ở mọi giá trị. Giá trị 3 nằm trong nhiễu của giá trị 0; giá trị 1 tệ hơn. **Oppo, giá trị 0 so với `main`:** AN khoảng 1200 khung, 51–58 janky, 33–36 khung > 33 ms, gfx p95 23 ms; `main` khoảng 234 khung, 17–25 janky, 17–18 khung > 33 ms, gfx p95 53 ms (khoảng 2 khung chậm mỗi lần mở so với 1 của `main`, nhưng p95 thấp hơn nhiều). Không crash, không dừng bảo vệ trên cả hai máy.
**Kết luận:** thời gian ghi lệnh (record) chỉ khoảng 0.4 ms nên luồng chính và việc ghi display list KHÔNG phải chi phí; lớp phần cứng cho danh sách và cho Library không giúp, nên phát lại hai phần đó cũng không phải phần chính. Chi phí nằm ở phía vẽ (RenderThread/GPU) và **chưa tìm ra nguyên nhân**. Lớp phần cứng đã gỡ khỏi mã.
**Giả thuyết còn lại (đọc code, CHƯA kiểm chứng, để dành nếu sau này cần):** (1) diện tích khung panel nhóm lớn gấp khoảng 2–3 lần panel thư mục (cao tới màn trừ lề) mà chi phí nền kính tỉ lệ với diện tích; (2) `BackdropBlurDrawable.drawScaled` đổi hình học MỖI KHUNG (canvas co `1/s`, hình `s·rect + t`) cho cả lệnh đổ `BitmapShader` lẫn viền `STROKE`, nên Skia không dùng lại được kết quả theo hình (vd. mặt nạ phủ dựng bằng CPU) giữa các khung; đứng yên thì dùng lại được; Oppo (kính thật, không có drawable và viền này) mượt, khớp giả thuyết này; (3) nền tối toàn màn đổi alpha mỗi khung và `saveLayer` alpha của panel ở nửa đầu spring (cả hai panel đều có); (4) panel nhóm không `clipChildren=false` và có thêm `libraryScrim` toàn màn. Cách kiểm nếu cần: simpleperf RenderThread của một lần spring panel nhóm so với panel thư mục (tìm `GrSoftwarePathRenderer`/`SkScan`/`SkRasterPipeline`), hoặc công tắc cô lập từng nghi phạm.
**Công tắc cô lập (một bit mỗi nghi phạm: nền tối, shader kính, viền kính, alpha panel, danh sách, Library) đã được viết nhưng CHƯA TỪNG ĐƯỢC ĐO** (chủ dự án quyết định dừng trước khi chạy); không có kết quả cô lập nào. Công tắc và mọi móc debug (`AnDebug`, `debugMode`, `debugIsolate`) KHÔNG nằm trong nhánh; `grep AnDebug|an_group|debugIsolate` trong `app/src` trống.

- [x] **Step 1:** đọc code loại trừ: không có `invalidate()` thừa của ô Library mỗi khung, ghi lệnh rẻ (rec ≈ 0.4 ms); liệt kê khác biệt hai panel (giả thuyết ở trên).
- [x] **Step 2: Đo vòng 1 (điều phối)** — lớp phần cứng cho danh sách / Library: không giá trị nào thắng (bảng trên).
- [ ] **Step 3: Đo cô lập vòng 2** — KHÔNG làm: chủ dự án chấp nhận hiện trạng; công tắc chưa đo, đã gỡ.
- [x] **Step 4: Dọn:** mọi móc tạm (`an_group_layer`, `an_group_iso`, `AnDebug`, `debugMode`, `debugIsolate`) đã gỡ; `app/` ở cuối nhánh giống hệt `f616627`.

---

### Task 10: Báo mọi phần tử kính vẽ lại khi ảnh nền đổi (phần trong view) (xong 2026-11-04, chờ review điều phối; kiểm được sau hook H1 ở Task 11)

**Phase:** A (kiểm được chỉ sau Phase B hook H1) · **Bắt buộc kiểm tay:** không (sửa lỗi có kịch bản rõ; kiểm ở Task 11)
**Files:** Modify `core/glass/BlurredBackdropCache.kt`, `core/glass/BackdropBlurDrawable.kt`, `core/glass/GlassEnvironment.kt`, `ui/home/view/BackgroundGlass.kt` (`core/glass/*` — xin xác nhận).
**Interfaces:** Produces: `BlurredBackdropCache.notifyBackdropChanged()` + đăng ký người nghe yếu cho mỗi `BackdropBlurDrawable`; `GlassEnvironment.notifyBackdropChanged()` (main thread) gọi cache và các người nghe kênh mới (`addBackdropListener`).

**Nguyên nhân (đọc code):** `BackdropBlurDrawable.prepareShader` phát hiện ảnh đổi bằng `view.drawable !== lastDrawable` **lúc vẽ**, nhưng thay ảnh `backdrop_image` chỉ `invalidate()` chính `ImageView`; ô thư mục, ô nhóm, thanh, pill không được vẽ lại nên không tới được dòng đó. `StaticBlurSurface.setDynamic` là no-op; panel kính thật chỉ chụp lại ở `onShown()`.

- [x] **Step 1:** `BackdropBlurDrawable` tự đăng ký (tham chiếu yếu) với `cache` ngay khi tạo và gỡ ở `cancelPending()`; `BlurredBackdropCache.notifyBackdropChanged()` gọi `owner.invalidate()` của từng drawable còn sống (qua `onSmallReady()` đã có). Mọi `BackdropBlurDrawable` (ô thư mục, ô nhóm, thanh Library, ô tìm kiếm, pill, panel ở chế độ mờ tĩnh) được phủ **không cần mã riêng ở từng view**.
- [x] **Step 2:** `GlassEnvironment.notifyBackdropChanged()` = `blurCache.notifyBackdropChanged()` + người nghe kênh mới; `BackgroundGlass` đăng ký kênh đó ở `attach()` và, khi dùng kính thật và đang hiện, gọi `captureOnce()`.
- [x] **Step 3: Kịch bản kiểm:** chỉ build xanh; kiểm hành vi ở Task 11.
- [x] **Step 4: Commit** `feat: let glass redraw when the backdrop image changes`.

---

> **Đợi R1 merge vào `main`, rebase nhánh AN lên `main` (build xanh), rồi làm Task 11 và 12 trong các controller mới của R1.**

---

### Task 11: Phase B — nối hook H1, H2 và kiểm lỗi kính + ô nhóm ✅ hook nối xong 2026-11-04 sau khi rebase lên main có R1a; kiểm trên máy đạt (`dfac13c`): icon lớn mở Chrome, icon mini mở Gmail, "+N" mở panel, đổi ảnh nền không chạm vẫn làm mới ô thư mục, pill và dock (lỗi 9 đã sửa)

**Phase:** B · **Bắt buộc kiểm tay:** không (sửa lỗi có kịch bản; tổng hợp vào bài kiểm cuối). Điều phối được tự đo/cài theo quyết định chủ dự án 2026-11-03 (mục 12.7 Workflow) nên không dừng chờ.
**Files:** Modify `BackdropController` (đăng ký listener, một dòng trong `HomeActivity`) và `AppLibraryController.setup()` của R1 (tên theo điều phối 2026-11-04; AN ghi tên file thật vào Nhật ký).
**Interfaces:** Consumes Task 5 (`CategoryPageView.onAppClick`), Task 10 (`GlassEnvironment.notifyBackdropChanged`).

- [x] **Step 1: H1:** `GlassEnvironment.from(this).notifyBackdropChanged()` sau mỗi lần đổi nội dung `backdropImage` (nạp đầu, thay, xoá), giữ `setBackdropSource` + `pulseGlassRefresh` cho dock.
- [x] **Step 2: H2:** `libraryView.onAppClick = { app, rect -> viewModel.onAppClicked(app, rect) }` trong chỗ R1 đặt `setupLibrary`.
- [x] **Step 3: Kiểm hợp đồng view (H3) sau R1:** id view, `render(null)` để đóng, không container cắt: mở/đóng thư mục và panel nhóm vẫn bay từ/về đúng icon/ô.
- [ ] **Step 4: Kịch bản kiểm (Live 4 và Oppo)** — (a) **lỗi 9:** vào Cài đặt đổi ảnh nền, quay về Home: ô thư mục, pill, dock, thanh Library, ô nhóm (mở Library) đều đổi sang nền mới **mà không cần chạm hay vào edit mode**; hai lần liên tiếp, và đổi sang một ảnh rất sáng/tối; (b) mở panel thư mục/panel nhóm sau khi đổi ảnh: kính đúng nền mới (Oppo: kính thật); (c) xoá ảnh nền (`rm files/backdrop_image` theo AGENTS.md rồi `force-stop`): mọi phần tử về nền phẳng, không crash; (d) panel đang mở lúc đổi ảnh (đổi qua Cài đặt không đạt được; kiểm bằng cách mở Cài đặt từ edit mode): ghi chú nếu không tái hiện; (e) Library: bấm icon lớn trong ô nhóm mở app thật (không panel), bấm "+N" mở panel (hoàn tất Task 5). **[chưa tick: (a) và (e) đạt ở `dfac13c` (Task 11), (b)–(d) không có bản ghi]**
- [x] **Step 5: Commit** `fix: redraw all glass when the wallpaper changes` và `feat: wire library tile app clicks`.

---

### Task 12: Đo tổng, chỉnh tay, dọn, knowledgement, đóng nhánh

**Phase:** B · **Bắt buộc kiểm tay: CÓ (đo)** — lý do (mục 4.2): phép đo hiệu năng cuối để chốt giới hạn của đề bài (Janky +2.0/+1.0, Δp95 +4 ms) và chốt số spring theo mắt chủ dự án; điều phối chạy máy, chủ dự án xem cảm giác.
**Files:** `core/ui/SpringProgress.kt` (hằng preset), các file view (chỉ hằng), `docs/knowledgements/AN-polish-animation.md` (tạo), plan này.

- [x] **Step 1: Đo ghép cặp đủ bộ** theo "Giao thức đo" (C1–C7) trên Live 4 và Oppo, một phiên, "trước" lặp lại ở cuối; điền bảng vào Nhật ký. Đã đo theo từng cảnh (bảng "Bằng chứng"); KHÔNG đo: cảnh kéo C3/C4 (dựa trên cảm giác của chủ dự án) và Live 4 `AUTO`.
- [ ] **Step 2: Chỉnh tay hằng** (spring `PANEL/PRESS/HOVER/SLIDE/SETTLE`, `PRESS_SCALE`, `HOVER_GLASS_SCALE`, phóng thư mục) theo cảm giác chủ dự án; ghi giá trị cuối vào KDoc. **[chưa tick: chủ dự án chấp nhận các giá trị spring hiện tại ("nhìn đủ mượt"), không chỉnh tay thêm; chưa ghi giá trị cuối vào KDoc]**
- [x] **Step 3: Dọn:** kiểm `grep AnDebug|an_panel_mode|PanelGlassMotion` trống (công tắc tạm đã gỡ ở Task 3 Step 5, không được tồn tại trong `main`), `grep` `FOLDER_INTENT`, `animate()` của hover cũ, mọi `Log` tạm; kiểm không còn `Handler`/spring rò sau `onDetachedFromWindow`. **[2026-11-05: `grep AnDebug|an_panel_mode|PanelGlassMotion|FOLDER_INTENT|an_group|debugIsolate` trong `app/src` trống, mã AN không có `Log` tạm. Điều phối đọc lại bằng code (không đo rò trên máy): `DockView`, `IconGridView` và ô lưới của `FolderPanelView` gỡ hết callback trong `onDetachedFromWindow`, spring được hủy qua `motion.cancel()`, `settleSpring.cancel()`, `hoverSpring.cancel()`; `DragHelper` không đổi trong nhánh này]**
- [x] **Step 4: Knowledgement** `docs/knowledgements/AN-polish-animation.md` theo mẫu mục 8 Workflow, có `sequenceDiagram` (mở thư mục từ icon: `FolderPanelView.render` → `SpringProgress` → `BackgroundGlass.applyMotion` → `BackdropBlurDrawable`), `stateDiagram-v2` (đóng/mở/ngắt giữa chừng), `flowchart` (chế độ kính trong lúc chuyển động theo tier).
- [ ] **Step 5: Rebase lên `main`, build xanh, rà Review Focus, điền bảng bằng chứng, chuyển PR sang Ready for review.** **[chưa tick: đã rebase lên `main` có R1 + G2b (nền `f616627`), build xanh, bảng bằng chứng đã điền; còn chuyển PR #6 sang Ready for review, merge và đồng bộ tài liệu chung (điều phối)]**
- [x] **Step 6: Commit** `docs: add AN knowledgement and close the plan`. Commit thực tế: `docs: close AN plan with measured results`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Live 4 (mặc định; `AUTO` và thêm `FORCE_HIGH` cho panel) và Oppo A3x (kính thật). **Dữ liệu cần chuẩn bị:** ảnh nền nhiều chi tiết (và một ảnh sáng, một ảnh tối để đổi ảnh nền); ít nhất 6 thư mục trên 2 trang (1 thư mục 9 app đầy); Library có nhóm > 4 app và nhóm ≤ 4 app; Oppo giữ 344 app. Mở app: `am start -n gambi.global.os26launcher/.ui.home.HomeActivity`.

Thứ tự thao tác tự nhiên (điều phối sắp lại khi gộp):
1. Home đứng yên và vuốt trang: không đổi so với `main` (kính bám nền, không giật). (Hồi quy)
2. Bấm thư mục: panel lớn dần từ icon; bấm vùng tối: thu về icon; mở–đóng nhanh, đóng–mở lại giữa chừng; Back; bấm app trong panel. (Task 3)
3. Edit mode (rung): nhấn giữ; ô thư mục co khi giữ (Task 6).
4. Kéo app lên app khác: kính hiện sau icon đích, to hơn một chút, không phóng icon; thả → tạo thư mục; kéo lên thư mục: thư mục phóng ngay, không kính; thư mục đầy: không phóng; giữ ở mép 500 ms: đẩy-dồn. (Task 7)
5. Kéo icon qua hàng: các icon trượt mượt; thả: icon vào chỗ, các icon khác đã nằm đúng; kéo về đúng ô cũ; bấm "−"; kéo giữa lưới và dock; lật trang khi kéo. (Task 8, 9)
6. Library: vuốt mở; bấm icon lớn trong ô nhóm mở app ngay; bấm "+N"/nền ô mở panel nhóm từ ô, đóng về ô; mở–đóng giữa chừng; cuộn Library và panel. (Task 4, 5, 6, 11)
7. Pill tìm kiếm và thanh tìm kiếm Library co khi giữ. (Task 6)
8. Cài đặt đổi ảnh nền, về Home: mọi phần tử kính (ô thư mục, pill, thanh, ô nhóm, dock, panel) đổi sang nền mới ngay, không cần chạm; xoá ảnh nền: về phẳng. (Task 10, 11)
9. Tắt hoạt ảnh (Animator duration scale = off): mọi thứ vẫn hoạt động, không kẹt. (Task 1)
10. Xoay màn hình khi panel mở; chia đôi màn hình thật khi panel/Library mở.
11. Oppo: panel thư mục và panel nhóm mở/đóng với kính thật, nhận xét mắt về "bước nhảy" kính mờ → thật (nếu S2), một khung trễ đã biết.

**Hồi quy:** kéo-thả/lật trang/trang tạm/"−"/menu nhấn giữ (M7); tạo, thêm, kéo ra, giải tán thư mục (G2a); tìm kiếm; Library vuốt mở/đóng và Back đúng thứ tự; đổi cỡ lưới re-flow; dock kéo-thả; đổi mức kính Cài đặt.

**Kết quả kiểm chứng:** xem "Bằng chứng Review Focus" bên dưới (đo ghép cặp theo cảnh, 2026-11-04 và 2026-11-05, nhánh@`f616627` cho mã). Kiểm tay cả tính năng theo danh sách trên KHÔNG được ghi lại từng ý: chủ dự án kiểm bằng cảm giác.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-04 | — | Viết plan (bước 1–2 workflow); đọc code và tài liệu; xác nhận version `dynamicanimation` 1.1.0 từ Google Maven | `docs: add AN plan` | Chờ rà plan |
| 2026-11-04 | — | Sửa theo review điều phối: gộp điểm dừng kiểm tay Task 7 và 8 thành một phiên; Task 3 có khối PowerShell đo (kiểu GL) và công tắc tạm `AnDebug` gỡ ngay Step 5; nhấn co: không cài lại cho dock, `pressScale` 0.95f xác nhận bằng `javap`; header PR #6; ghi xác nhận vùng file và hook H1/H2 theo R1 | `docs: revise AN plan after review` | Rà plan vòng 1/5 |
| 2026-11-04 | — | Chủ dự án duyệt plan; ghi trả lời 6 câu hỏi vào Quyết định 11–17; cập nhật ô thứ tư của ô nhóm Library (lưới mini 2×2 + "+N", vùng chạm mini ~26×26 dp), nhấn co chỉ 4 phần tử, S3 đã duyệt trước | `docs: record AN plan approval and owner answers` | Bắt đầu code Phase A |
| 2026-11-04 | 1 | `dynamicanimation:1.1.0` (libs.versions.toml + app/build.gradle.kts); `core/ui/SpringProgress` (animateTo đổi đích giữ vận tốc, snapTo, cancel, preset PANEL/PRESS/HOVER/SLIDE/SETTLE, snap khi tắt hoạt ảnh); `assembleDebug` xanh | (commit Task 1) | Chờ review. Chưa kiểm trên máy (không có hành vi nhìn thấy) |
| 2026-11-04 | 2 | `BackdropBlurDrawable.draw` có nhánh scale ≠ 1 (`drawScaled`: `canvas.scale(1/s)`, `translate(-t)`, hình = s·rect + t, bán kính và viền nhân s); nhánh scale = 1 giữ nguyên văn hai lệnh cũ (chỉ thêm 2 phép so sánh float); `assembleDebug` xanh | (commit Task 2) | Chờ review. Kiểm bằng mắt khi có scale thật ở Task 3 và 7; ảnh chụp Home đứng yên trước/sau do điều phối chụp (nhánh s = 1 không đổi lệnh vẽ: đọc code) |
| 2026-11-04 | 3 (Step 1–3) | `IconGridView.folderIconScreenRect` + `FolderOrigins` (sổ lưới đang gắn); `FolderPanelView` mở/đóng bằng `SpringProgress.PANEL` (scale/dịch từ icon, alpha, nền tối `ColorDrawable`; đích = `shown`; chạm đi xuống khi đang đóng; snap khi render đầu sau recreate; snap về 0 khi mở thư mục khác lúc đang đóng); `BackgroundGlass.applyMotion/setMoving` + chế độ S1/S2; `AnDebug` TẠM (`files/an_panel_mode`: 1 = S1, 2 = S2); `assembleDebug` xanh; knowledgement bản đầu | (commit Task 3) | Chờ điều phối chạy khối đo ở Step 4. Chưa kiểm trên máy. Daemon bị `--stop` của nhánh khác giết 3 lần: retry thì xanh |
| 2026-11-04 | 3 (Step 4, điều phối đo) | Oppo, build `cacf95d`, thứ tự main1 → s1 → s2 → main2, 3 lượt. Trung bình C1 (20 lần mở/đóng): main1 745 khung, 22.3 khung janky, gfx p50/p95 11/23.3, >33 ms 2.3; **s1** 1655 khung, 22.3 janky, 10/17, >33 ms 2.7; s2 2098 khung, 21 janky, 10/13.7, >33 ms 4.3; main2 744 khung, 21.3 janky, 11/23.3, 2.3. C1i (đảo chiều giữa chừng): janky main1 31.7 / s1 16 / s2 15.7 / main2 31, p95 22.7 / 15 / 14 / 22.7. Nền ổn định (main1 ≈ main2). Số khung khác nhau 2× nên so SỐ KHUNG JANKY TUYỆT ĐỐI, không so %. **Quyết định: S1** (đạt giới hạn, số khung janky bằng `main`, vẫn còn đúng một khung trễ mỗi lần mở như đã biết, p95 tốt hơn 6 ms); S2 mượt hơn chút ở p95 nhưng nhiều khung > 33 ms hơn và có bước nhảy kính thật/mờ lúc dừng. Live 4 luôn là kính mờ tĩnh: kiểm ở Task 12 | — | S3 không cần |
| 2026-11-04 | 3 (Step 5) | Gỡ `AnDebug`, `PanelGlassMotion`, `setMoving`, kính mờ thay thế; giữ S1 (`applyMotion` co kính thật cùng panel, `captureOnce` khi dừng); `grep AnDebug|an_panel_mode|PanelGlassMotion` trống; `assembleDebug` xanh | (commit Step 5) | Task 3 tick |
| 2026-11-04 | 4 | Tách `PanelMotion` (spring p, scale/dịch từ phần tử gốc, alpha, nền tối, kính) dùng chung; `FolderPanelView` chuyển sang dùng nó; `CategoryGroupPanelView` mở/đóng bằng spring từ ô nhóm (`CategoryPageView.tileScreenRect`, map group → khung 2×2 dựng trong `addTile`), nội dung giữ tới khi dừng, chạm đi xuống khi đang đóng, snap lần render đầu; `assembleDebug` xanh | (commit Task 4) | Chờ review. Chưa kiểm trên máy |
| 2026-11-04 | 5 | `item_category_group.xml`: id cho 4 ô lớn (`category_slot_0..3`) và cụm 2×2 trong ô thứ tư (`category_cluster`, `category_mini_0..3`, "+N" là `category_overflow` trong góc cuối); `CategoryPageView.addTile`: nhóm ≤ 4 app = 4 ô lớn mở app; ≥ 5 app = 3 ô lớn + cụm mini (R ≤ 4 thì R icon mini, ngược lại 3 icon + "+(R−3)"); mỗi đích có click riêng, rect từ ImageView, `onAppClick == null` thì lùi về `onGroupClick` (kiểm tại lúc bấm); contentDescription từng đích; chuỗi `library_tile_more_apps`; vùng chạm mini = nửa ô lớn (`mini / 2`, ~26 dp ở s = 1 trên máy 360 dp, sẽ đo bằng uiautomator ở bài kiểm); `assembleDebug` xanh | (commit Task 5) | Chờ review. Chưa kiểm trên máy. Kính ô và `LibraryLayoutMetrics` không đổi (kích thước lấy từ `Sizes.mini`) |
| 2026-11-04 | 6 | `PressScale` (spring PRESS 0.95, không nuốt chạm) cho khung 2×2 của ô nhóm (chỉ phần KHÔNG phải icon), pill (chỉ chế độ ô tìm kiếm), thanh tìm kiếm Library; `IconCellView`: một spring scale chung (`scaleSpring`), `pressFeedback` chỉ bật cho ô thư mục, pivot ở tâm icon, `resetScale()` thay `animate().cancel(); scaleX = 1` trong `bindAllCells`; `SpringProgress` thêm `initialValue` và `newSpec`; `BackdropBlurDrawable` coi canvas phần mềm (ảnh chụp ô) như scale = 1; `assembleDebug` xanh | (commit Task 6) | Chờ review. Chưa kiểm trên máy. Dock không đụng. Icon app thường không co (quyết định 13) |
| 2026-11-04 | 7 | Bỏ `FOLDER_INTENT_SCALE`/`animate().scaleX` cho ô đích; `IconGridView` vẽ kính hover trong `dispatchDraw` trước các con (`BackdropBlurDrawable` chủ sở hữu = lưới, vai trò `FOLDER_CELL`, tier FLAT thì tô `folder_background`; cạnh = icon × 1.25; phình từ 0.6 → 1 bằng `SpringProgress.HOVER`; mờ theo p); đích là thư mục chưa đầy: `IconCellView.setHoverZoom(true)` (1.12×, pivot tâm icon, chung spring scale với nhấn co), không kính; thư mục đầy: không phóng; `invalidateGlass()` vẽ lại lưới khi hover và pager cuộn; huỷ ở `onDetachedFromWindow`/`cancelFolderIntent`; `assembleDebug` xanh | (commit Task 7) | Đổi so với plan: viền kính hover vẽ cả lúc đang spring (chỉ thêm 1 lệnh, tránh viền nhảy hiện) thay vì bỏ viền. Chưa kiểm trên máy; kiểm tay gộp với Task 8 |
| 2026-11-04 | 8 | `IconCellView`: hai spring `SLIDE` cho translationX/Y (`springOffsetTo`, `setOffsetImmediate`); `IconGridView`: preview đẩy-dồn thành spring, FLIP theo `itemId` (chụp vị trí nhìn thấy trong `setItems`, áp trong `onLayout`; bỏ qua khi đổi trang/đổi Cells/bind đầu; bỏ item đang kéo; giữ nguyên ô không đổi chỗ để không phá xem trước đang chạy), thả được nhận thì GIỮ độ dời xem trước (`clearPreview(keepOffsets = true)`) + `staleOffsetCleanup` 300 ms; `DockView`: cùng mẫu theo chiều ngang (đã đọc `setItems`/`onLayout`: dock căn giữa nhóm nên FLIP áp được); `assembleDebug` xanh | (commit Task 8) | Chờ kiểm tay gộp Task 7+8 (chủ dự án). Chưa kiểm trên máy |
| 2026-11-04 | 9 | `DragDropTarget.dropSettle()`/`onDragSettled()` (mặc định rỗng) + `DropSettle(rect, endScale, endAlpha)`; `DragHelper.drop`: thả được nhận có `dropSettle` thì `settlingItemId` = item và `DragVisual.settleTo(...)` (spring SETTLE: ảnh trượt từ ngón tay tới tâm ô đích, thu về 0.4 và mờ khi vào thư mục), `beginDrag`/`detachDragVisual` kết thúc hạ cánh ngay; `DragLayer` hạ cánh bằng `SpringProgress` (scale/alpha nội suy); lưới và dock đặt alpha ô = 0 nếu `itemId == hiddenItemId || settlingItemId` (state ở object, không ở view) và hiện lại ở `onDragSettled`. Sửa theo quan sát của điều phối (ảnh chụp 250 ms): kính hover bám vị trí NHÌN THẤY của ô đích (layout + độ dời đang trượt) thay vì vị trí layout; `assembleDebug` xanh | (commit Task 9) | Chờ review. Chưa kiểm trên máy |
| 2026-11-04 | 10 | `BlurredBackdropCache.register/notifyBackdropChanged` (người nghe yếu suốt đời); `BackdropBlurDrawable` tự đăng ký khi tạo (onSmallReady → owner.invalidate nếu còn gắn); `GlassEnvironment.notifyBackdropChanged()` + `add/removeBackdropListener`; `BackgroundGlass` nghe kênh đó và gọi `captureOnce()` cho kính thật đang hiện; `assembleDebug` xanh | (commit Task 10) | Chưa kiểm được: cần hook H1 của Task 11 (R1 chưa merge). Hệ quả đã biết: lúc ảnh mới đang dựng bản mờ (~80 ms) kính vẽ phẳng một lúc rồi mờ (hành vi GL cũ), xét lại ở Task 11/12 nếu nháy |
| 2026-11-04 | 9b | Thêm Task 9b theo yêu cầu chủ dự án (quyết định 18, 19). `FolderPlanner`: vùng giữa tính theo icon, vào 0.9 / ra 1.1 cạnh icon (cũ 0.5 bề rộng ô × 0.5 cạnh icon); `IconGridView`: máy trạng thái hover (kính/phóng sau 100 ms ở vùng giữa, đẩy-dồn sau 150 ms ở ngoài vùng giữa, rời trạng thái đang hiện thì bỏ ngay), bỏ giữ 500 ms và bỏ đường thả ở mép vẫn gộp; `LayoutReconciler.createFolder/addToFolder` và `HomeViewModel.onFolderIntent/onAddToFolder` dồn trang nguồn (`planCompaction`/`removeAndCompact`), thư mục mới nằm ở ô đích sau khi dồn; `assembleDebug` xanh; không đổi schema | (commit Task 9b) | Chờ kiểm tay gộp Task 7+8. Chưa kiểm trên máy; 7 ca đã kiểm bằng đọc code (xem Task 9b) |
| 2026-11-04 | 9c | Hoàn tác Sửa 2 của Task 9b: khôi phục `LayoutReconciler.kt` và `HomeViewModel.kt` về trước Task 9b (app vào thư mục lại để ô nguồn trống, không dồn); quyết định 19 gạch bỏ + lý do. `IconGridView`: `PUSH_ARM_MS` 150 → 500 ms, đếm lại khi ngón tay dời > `PUSH_SETTLE_DP` = 8 dp kể từ lúc bắt đầu đếm; kính vùng giữa vẫn 100 ms; `assembleDebug` xanh | (commit Task 9c) | Chờ kiểm tay gộp Task 7+8+9b. Chưa kiểm trên máy |
| 2026-11-04 | 9d | `LeavingIcons` (ảnh ô chụp trước `setItems`, vẽ riêng trong `dispatchDraw`, spring `EXIT` tới hạn) + `IconCellView.popIn`; lưới/dock: so id cũ/mới, bỏ qua bind đầu, đổi cỡ, đổi trang, item đang kéo/hạ cánh và thay tại chỗ cùng ô (D9, tạo thư mục); các icon sau trượt sau 180 ms; panel thư mục (`AppsGrid`): thu + trượt khi ẩn bằng "−" và rung bằng `Jiggler` ở edit mode (dừng khi tắt hoặc panel đóng hẳn, khởi động lại khi mở); `assembleDebug` xanh | (commit Task 9d) | Chờ review. Chưa kiểm trên máy. Luật khe sau ô đích của `GridInsertPlanner` giữ nguyên (khảo sát 2026-10-28) |
| 2026-11-04 | 9e | `IconGridView.desiredHover`: PUSH đang hiện thì ở lại khi ngón tay còn trong ô neo (mở rộng `PUSH_ANCHOR_HYST_DP` = 8 dp); đích gộp tìm theo vị trí NHÌN THẤY (layout + độ dời) trong vùng giữa, gần ngón tay nhất; đích đang bị đẩy thì giữ hàng (`keepRow`), `armFolderIntent` không `clearPreview` cho đích đang bị đẩy; `onDrop` dùng ô neo làm ô thả khi PUSH đang hiện; hạ cánh vào thư mục theo vị trí nhìn thấy; `assembleDebug` xanh | (commit Task 9e) | Chờ kiểm tay. Chưa kiểm trên máy |
| 2026-11-04 | 11 (hook) | Rebase lên `origin/main` (7030768, có R1a): 18 commit, KHÔNG xung đột; `--force-with-lease` lên nhánh của mình. **H2:** `AppLibraryController.setup()` thêm `libraryView.onAppClick = { app, rect -> viewModel.onAppClicked(app, rect) }` (icon lớn và icon mini mở app; chỉ "+N", khe, nhãn mở panel). **H1:** `HomeActivity.onCreate`, khối "Dòng nối cho AN/G2b": `backdrop.addBackdropListener(BackdropListener { GlassEnvironment.from(this).notifyBackdropChanged() })` (bắn cho LOADED, REPLACED và CLEARED trong `BackdropController.render`) + import `GlassEnvironment`: đây là sửa `HomeActivity` duy nhất của AN. Đã kiểm bằng đọc: không controller nào của R1 tự ẩn panel bằng `visibility` (panel vẫn đóng bằng `render(null)`), id view giữ nguyên; `assembleDebug` xanh | (commit Task 11) | Chờ điều phối: kiểm ảnh nền (lỗi 9) và ô nhóm trên máy, rồi đo Task 12 |
| 2026-11-04 | 9f | `DockView.onMeasure`: không còn `setMeasuredDimension(0, 0)` khi `cells == null` (kích thước theo spec, đo cả view kính, chỉ bind icon khi có cells); `setItems`: `requestLayout()` qua `post` nếu `isInLayout`; ghi nhận ghi chú M5 về dock tự ẩn 0×0 ở cửa sổ quá nhỏ đã đổi (dock giữ chỗ, chỉ viên kính); `assembleDebug` xanh | (commit Task 9f) | Chờ điều phối stress-test 40 lần dựng lại. Task 11 đã tick (kiểm máy `dfac13c` đạt) |
| 2026-11-04 | 9g | Sửa bước nhảy ảnh kính khi spring dừng (Oppo). Nguyên nhân (`javap` v2.0.11): `GlassLensRenderer` lấy cửa sổ nền theo `LiquidGlassView.getLocationOnScreen()` (tính cả scale/dịch), nên lần chụp lúc đang co lấy nhầm vùng ảnh nền; `captureOnce()` lúc dừng chụp đúng vùng. Sửa: `SuppressibleGlassView.getLocationOnScreen` báo vị trí đã layout khi bật `setCaptureAtLayoutPosition(true)` (chỉ kính panel, bật ở `BackgroundGlass.setRealActive`), bỏ `captureOnce()` lúc dừng ở Folder/CategoryGroup; đường kính mờ tĩnh đọc code không lỗi. Chi phí dự kiến 0 khung lúc chuyển động, bớt một lần vẽ kính lúc dừng; `assembleDebug` xanh | (commit Task 9g) | Chờ điều phối chụp ảnh ~100/250 ms/dừng và đo C1 ×20 trên Oppo. Chưa kiểm trên máy |
| 2026-11-05 | 9g (đóng) | Chủ dự án thấy kính đổi hình lúc mở xong trên Oppo, kiểm lại sau sửa và xác nhận đúng. Ảnh chụp cách quãng của điều phối không kết luận được (`screenrecord` segfault trên Oppo, `screencap` quá chậm). C1 ×20 Oppo: AN 24.7 janky so với `main` 23, p95 17 so với 24 ms | `c332bd3` | Đóng. Live 4 (kính mờ tĩnh) không đổi mã |
| 2026-11-05 | 9h (đóng, không sửa) | Live 4 `FORCE_HIGH`: panel nhóm AN ≈ 685 khung, 245–354 janky, p95 23–27 ms, 32–66 khung > 33 ms (`main` 73 khung, ≈ 50 janky, 15 khung > 33 ms; panel thư mục AN 900 khung, 3.7 janky, 5/8 ms). Thử lớp phần cứng cho danh sách và Library: không giá trị nào thắng (bảng ở Task 9h). Record ≈ 0.4 ms nên chi phí ở phía vẽ, nguyên nhân chưa tìm ra. Chủ dự án chấp nhận ("nhìn đủ mượt"); công tắc cô lập viết nhưng chưa từng đo, đã gỡ; `app/` giống hệt `f616627` | — | Đóng, không sửa mã. Giả thuyết còn lại ở Task 9h |
| 2026-11-05 | 12 (phần) | Điền bảng bằng chứng, danh sách chưa đo, mục đã biết chưa sửa; tick theo việc đã làm | `docs: close AN plan with measured results` | PR Ready, merge, đồng bộ tài liệu chung: điều phối |

## Bằng chứng Review Focus (điền khi đóng, 2026-11-05)

### Số đo ghép cặp (điều phối; AN so với `main`)

Cấu hình kính: **Live 4 (API 30) chạy `FORCE_HIGH`** (panel là kính mờ tĩnh `BackdropBlurDrawable`, dock kính thật qua đường chụp CPU); **Oppo A3x** chạy kính thật cho hai panel. Dùng số khung janky TUYỆT ĐỐI khi số khung khác nhau nhiều lần (AN có hoạt ảnh spring nên nhiều khung hơn `main`).

| Máy | Cảnh | `main` | AN | Nhận xét |
|---|---|---|---|---|
| Oppo | C1: mở/đóng thư mục ×20 | 23 khung janky, gfx p95 24 ms | 24.7 khung janky, gfx p95 17 ms | trong giới hạn (Janky +1.0 điểm, p95 +4 ms); p95 tốt hơn |
| Oppo | S2: vuốt trang | 1.6% | 1.6% | bằng nhau |
| Oppo | Panel nhóm Library (C2) | ≈ 234 khung, 17–25 janky, 17–18 khung > 33 ms, gfx p95 53 ms | ≈ 1200 khung, 51–58 janky, 33–36 khung > 33 ms, gfx p95 23 ms | AN khoảng 2 khung chậm mỗi lần mở so với 1 của `main`, nhưng p95 thấp hơn nhiều |
| Live 4 (`FORCE_HIGH`) | C1: mở/đóng thư mục | 3.3 janky | 3.7 janky, gfx p95 6–10 ms | gần bằng |
| Live 4 (`FORCE_HIGH`) | S2: vuốt trang | — | 0.5%, kèm một lượt ngoại lệ 2.08% | |
| Live 4 (`FORCE_HIGH`) | Rung edit mode | 0.08% | 0.08% | bằng nhau; lượt chạy đầu sau khi cài ≈ 95% ở CẢ HAI bản (ngoại lệ, không do AN) |
| Live 4 (`FORCE_HIGH`) | Panel nhóm Library (C2) | 73 khung, ≈ 50 janky, gfx 22/60 ms, 15 khung > 33 ms | ≈ 685 khung, 245–354 janky (giá trị 0), gfx p95 23–27 ms, 32–66 khung > 33 ms | KHÔNG đạt mục tiêu "gần panel thư mục" (gfx 5/8 ms); chấp nhận bởi chủ dự án, nguyên nhân chưa tìm ra (Task 9h) |

Phép đo kính thật Oppo ở Task 3 (S1 so với S2) xem Nhật ký. Mã đo ở cuối nhánh là `f616627`.

### CHƯA đo

- Cảnh kéo C3 (kính hover) và C4 (kéo-thả reflow): chỉ dựa trên cảm giác của chủ dự án, không có số.
- Live 4 ở chế độ kính `AUTO`: mọi số của Live 4 ở trên là `FORCE_HIGH`.
- Công tắc cô lập nghi phạm của Task 9h (chưa từng chạy).

### Mục đã biết, KHÔNG sửa

- Lần mở panel nhóm đầu tiên sau khởi động nguội trên Live 4: vài khung đầu hiện nhãn mà chưa có icon, và Messenger đến muộn một khung. Các lần mở sau chưa kiểm. (Có thể do nạp icon bất đồng bộ trong `Adapter.onBindViewHolder` lúc `submitList`; chưa điều tra.)
- Panel nhóm Library trên Live 4 nặng hơn panel thư mục (Task 9h).

### Theo dòng Review Focus

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Mở/đóng nhanh, ngắt giữa chừng, recreate | Không có bản ghi từng ca; chủ dự án kiểm bằng cảm giác | không ghi lại |
| 2 | `containsScreenPoint`/`blocksOtherTargets` lúc đóng | Đọc code (Task 3); không có bản ghi chạy máy | không ghi lại |
| 3 | Cắt nền đúng khi scale ≠ 1; nhánh scale = 1 không đổi lệnh vẽ | Đọc code và tính tay (Task 2, Task 9g); ảnh chụp Home đứng yên trước/sau không được ghi lại | chưa có ảnh |
| 4 | Kính thật của panel: chế độ, không `invalidate()` mỗi khung | S1 chọn bằng đo Oppo (Task 3); `javap` v2.0.11 và sửa vị trí chụp ở Task 9g; chủ dự án xác nhận không còn đổi hình trên Oppo | đạt |
| 5–7 | Kính hover, FLIP, hạ cánh | Kiểm bằng cảm giác chủ dự án; Oppo đạt Task 9e; không đo C3/C4 | không có số |
| 8 | Ô nhóm Library: bấm icon, "+N", a11y | `dfac13c`: icon lớn mở Chrome, icon mini mở Gmail, "+N" mở panel; a11y không ghi lại | một phần |
| 9 | Lỗi 9: đổi ảnh nền, mọi phần tử kính vẽ lại | `dfac13c` trên máy: ô thư mục, pill, dock làm mới không cần chạm | đạt |
| 10 | Đo Live 4 và Oppo ghép cặp; cảnh đứng yên/vuốt/rung không đổi | Bảng số đo ở trên (S2 và rung bằng `main`) | đạt, trừ panel nhóm Live 4 (chấp nhận) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: ghi AN (hoạt ảnh spring, kính hover, bấm icon lớn trong ô nhóm Library) nếu chủ dự án muốn có TT# riêng.
- `project_structure.md`: thêm `core/ui/SpringProgress`; `IconGridView` có sổ đăng ký ô thư mục và vẽ kính hover trong `dispatchDraw`; `DragHelper.settlingItemId`; `GlassEnvironment.notifyBackdropChanged`; `BlurredBackdropCache` có người nghe yếu cho `BackdropBlurDrawable`; mục "Thư viện" thêm `dynamicanimation:1.1.0`.
- `room-schema.md`: không đổi.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement; ứng viên: "thay đổi thuộc tính transform không ghi lại display list nên kính tại chỗ phải `invalidate()` và cắt nền theo scale", "FLIP theo itemId cho ViewGroup tái dùng con theo chỉ số", "version AndroidX lấy từ `maven-metadata.xml` của Google Maven".
- `MultiAgent-Workflow.md` mục 9: cập nhật dòng AN (phạm vi đã mở rộng: kính hover, ô nhóm, lỗi ảnh nền).
