# TL1 — Chỉnh Home theo góp ý tech lead (chuyển trang, độ rung, thanh nút edit mode) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `fix/tl1-home-polish` · **Worktree:** `E:\SourceTree\IosLauncher-wt\TL1-home-polish` · **PR:** #2 · **Bắt đầu:** 2026-11-03 · **Plan duyệt:** 2026-11-03
**Trạng thái:** Đang code — Task 1, 1b, 2, 2b đã qua review; Task 1–5 đạt review; Task 6 code xong, chờ phiên kiểm máy gộp
**Khóa:** không (không đổi schema Room, không thêm khóa DataStore). Giữ độc quyền `HomeActivity.kt` + `activity_home.xml` trong đợt 0a.

**Goal:** Ba góp ý của tech lead (2026-11-03) về màn Home: (1) chuyển trang chậm hơn và mượt hơn, kiểu iOS — **trước hết là vuốt tay (99% cách đổi trang)**, rồi tới lật có chủ đích; (2) icon rung nhẹ hơn khi vào chế độ chỉnh sửa; (3) hai nút Cài đặt / Xong của edit mode nổi phía trên như viên pill, **không đẩy lưới xuống** khi vào/ra chỉnh sửa.

**Architecture:** Không thêm tầng mới. (1) Hai hằng chung `PageMotion` (thời lượng + đường cong) dùng cho cả hai đường: `TunedPagerSnapHelper` (thay `PagerSnapHelper` nội bộ của ViewPager2 để chỉnh đoạn thả tay sau vuốt/fling) và `PagerFlipAnimator` (FakeDrag, cho lật có chủ đích). (2) Sửa một hằng số trong `Jiggler`. (3) Bỏ cơ chế "đẩy `home_content` xuống lúc edit mode"; `edit_toolbar` thành hai pill (nhìn 28dp, vùng chạm 48dp); chừa một khoảng đệm cố định theo inset, không phụ thuộc edit mode; (tùy quyết định chủ dự án) ẩn thanh trạng thái khi edit mode như iOS với mọi margin tính từ `getInsetsIgnoringVisibility`.

**Tech Stack:** View/XML, `ViewPager2 1.1.0`, `RecyclerView 1.4.0`, `material 1.14.0` (đã có, đọc `gradle/libs.versions.toml` ngày 2026-11-03). Không thêm dependency.

**Spec:** Góp ý tech lead 2026-11-03 (không có TT# trong `Launcher-Features.md`; ghi ở bảng đợt 0a của `MultiAgent-Workflow.md` mục 9) + plan [M7-edit-mode.md](M7-edit-mode.md) (edit mode, jiggle, thanh công cụ) + `AGENTS.md` mục "Inset và layout màn hình chính", "M6", "M7", "Demo polish", "G2a B2".

## Vì sao chọn thiết kế này

Nhãn nguồn: **[bytecode]** = tôi chạy `javap` trên jar thật trong cache Gradle (`recyclerview-1.4.0`, `viewpager2-1.1.0`) ngày 2026-11-03; **[tài liệu]** = đọc API/tài liệu; **[suy luận]** = từ công thức/mã nguồn AndroidX tôi nhớ hoặc từ hình học trong code, **chưa đo**; Task 1 đo lại trước khi tin.

### 1. Chuyển trang

#### Vấn đề: ba đường chuyển trang, ba bộ thời lượng khác nhau

Phần lớn người dùng đổi trang bằng vuốt tay. Đọc bytecode cho thấy vuốt tay đi qua **hai đường khác nhau** tùy cách nhấc tay, và lật có chủ đích đi qua đường thứ ba:

| Đường | Khi nào | Ai tạo cuộn | Thời lượng / đường cong | Nguồn |
|---|---|---|---|---|
| 1. Fling | Nhấc tay khi còn vận tốc đủ lớn (đa số lần vuốt) | `RecyclerView.onFlingListener` = `PagerSnapHelperImpl` (lớp con package-private của `PagerSnapHelper`, ViewPager2 giữ trong field **private** `mPagerSnapHelper`) → `SnapHelper.snapFromFling` → `createScroller` | `PagerSnapHelper$1`: `calculateSpeedPerPixel = 100f/densityDpi`, `calculateTimeForScrolling = min(100, ...)`, rồi `onTargetFound` dùng `calculateTimeForDeceleration` (chia ~0,3356, interpolator giảm tốc của `LinearSmoothScroller`). Tức **tối đa ~300 ms, thường 100–200 ms** khi thả gần đích | **[bytecode]** các hằng 100 và 100f; phép chia 0,3356 **[suy luận]** |
| 2. Nhấc tay chậm / đứng yên (không fling) | Kéo một nửa rồi thả | `SnapHelper.mScrollListener` bắt `SCROLL_STATE_IDLE` → `snapToTargetExistingView()` → `RecyclerView.smoothScrollBy(dx, dy)` **2 tham số** | `ViewFlinger.computeScrollDuration`: `(\|d\|/chiềuRộng + 1) × 300f` ms, chặn 2000, đường cong `sQuinticInterpolator` (ease-out bậc 5) → **300–600 ms** | **[bytecode]** `ldc 300.0f`, `sipush 2000`, gọi `smoothScrollBy(II)V`; quintic **[suy luận]** |
| 3. Lật có chủ đích (`setCurrentItem(.., true)`) | Kéo icon chạm mép; lật sang trang trống | `RecyclerView.smoothScrollToPosition` → `LinearSmoothScroller` mặc định | 25 ms/inch rồi chia 0,3356: màn 1080px ở 320dpi ≈ 250 ms, ở 480dpi ≈ 170 ms | **[bytecode]** gọi `smoothScrollToPosition`; số **[suy luận]** |

Hệ quả: **đường 1 (phổ biến nhất) là đường nhanh nhất và gắt nhất**; đường 2 chậm hơn gần gấp đôi; hai đường không cùng cảm giác. Đó khả năng cao là điều tech lead gọi "hơi nhanh, chưa mượt". iOS lướt trang cỡ 350–500 ms mọi trường hợp (cảm nhận chung, Apple không công bố — **[suy luận]**).

"Chưa mượt" còn có thể là **giật khung hình** lúc lướt (trang kề bind giữa chừng — ViewPager2 mặc định không giữ sẵn trang kề — **[tài liệu]** `setOffscreenPageLimit`; `SearchPillView.setSelected` gọi `invalidate()` mỗi khung ở `onPageScrolled`). Chưa ai đo trên Live 4; đo ở Task 1 trước khi vá.

#### Hai sự thật về ViewPager2 quyết định phương án (đều **[bytecode]**)

1. `ViewPager2` tạo `PagerSnapHelperImpl`, gọi `attachToRecyclerView`, rồi cất vào field private `mPagerSnapHelper`; **không có getter/setter công khai**. `PagerSnapHelperImpl` chỉ ghi đè `findSnapView` (trả `null` khi `isFakeDragging`).
2. `SnapHelper.setupCallbacks()` ném `IllegalStateException("An instance of OnFlingListener already set.")` nếu RV đã có `OnFlingListener` — nên muốn gắn helper khác thì phải `recyclerView.onFlingListener = null` trước. Nhưng helper cũ đã `addOnScrollListener(mScrollListener)` (field private, chỉ gỡ bằng `old.attachToRecyclerView(null)` — cần chính instance cũ, tức reflection). **Gắn helper mới đè lên mà không có reflection ⇒ helper cũ thành "xác sống"**: listener của nó vẫn chạy ở mỗi `IDLE`. Xác sống chỉ làm gì khi `snapToTargetExistingView` thấy view chưa thẳng hàng; sau khi cuộn của ta xong thì view đã thẳng hàng ⇒ `dx == dy == 0` ⇒ không gọi `smoothScrollBy` (nhánh điều kiện trước lời gọi trong bytecode). Với nhấc tay không fling (đường 2) cả hai helper đều gọi `smoothScrollBy(dx, dy)` mặc định cùng một `dx` ⇒ lần gọi sau chỉ khởi động lại cùng một cuộn (vô hại, **[suy luận]**; kiểm ở Task 2).

Thêm: `ScrollEventAdapter.onScrollStateChanged` chỉ phát `onPageSelected` khi trạng thái adapter đang ở "kéo" (`isInAnyDraggingState`) hoặc do `setCurrentItem`/FakeDrag báo trước (`notifyProgrammaticScroll`, `notifyBeginFakeDrag`) **[bytecode]**. Hệ quả: **cuộn bất kỳ bắt đầu từ lúc người dùng đang kéo thì mọi bộ cuộn đều an toàn với callback của trang** (đường 1 và 2), còn `smoothScrollBy` thẳng từ trạng thái rảnh thì **không** phát `onPageSelected`. Điều này loại phương án G cho lật có chủ đích (xem dưới).

#### Các phương án đã xét — vuốt tay (đường 1 + 2)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `TunedPagerSnapHelper`**: lớp con `PagerSnapHelper`, ghi đè `createScroller` | RV bên trong `homePager`: `onFlingListener = null`, rồi `TunedPagerSnapHelper(pager).attachToRecyclerView(rv)`. Scroller là `LinearSmoothScroller` giữ `calculateSpeedPerPixel` của `PagerSnapHelper` cho pha tìm đích, nhưng `onTargetFound` gọi `action.update(dx, dy, PageMotion.durationMs(\|dx\|/rộng), PageMotion.CURVE)` với `dx` từ `calculateDistanceToFinalSnap` (công khai). Ghi đè `findSnapView` như `PagerSnapHelperImpl` (null khi `isFakeDragging`) | Không reflection; dùng đúng API công khai `SnapHelper`/`LinearSmoothScroller`/`Action.update`; phủ đường 1 (phổ biến nhất); callback trang không đổi (xem trên); ~40 dòng | Xác sống (ở trên) — đường 2 vẫn do mặc định xử lý (300–600 ms quintic) nên **không chỉnh được đường 2**; giữa hai đường có thể còn lệch cảm giác. Giảm lệch bằng cách chọn `CURVE` cùng họ quintic và `durationMs` cùng dải | **Chọn** |
| **B. Tự giữ toàn bộ việc thả tay**: `OnFlingListener` riêng + `OnScrollListener` riêng gọi `RecyclerView.smoothScrollBy(dx, 0, interpolator, duration)` (overload 4 tham số có thật **[bytecode]**) cho cả fling lẫn nhấc tay chậm | Phủ cả đường 2 | Helper xác sống (không gỡ được nếu không reflection) cũng bắt `IDLE` và gọi `smoothScrollBy(dx, dy)` mặc định **cùng lúc** với listener của ta ⇒ hai bộ cuộn cạnh tranh (bên sau phải `stopScroll` + khởi động lại; `stopScroll` bắn thêm `IDLE` ⇒ đệ quy trạng thái). `onFling` trả `true` để RV ở `DRAGGING` đến khi `smoothScrollBy` đặt `SETTLING` (**[bytecode]** `setScrollState`) — ranh giới mong manh. Phải tự viết lại logic chọn trang đích (`findTargetSnapPosition`) | Không (chỉ nếu A không đủ **và** chấp nhận reflection, xem C) |
| **C. A + reflection gỡ helper cũ** | Đọc field private `mPagerSnapHelper` của `ViewPager2`, gọi `attachToRecyclerView(null)` trên nó (công khai), rồi gắn A. Có thể thêm listener `IDLE` riêng cho đường 2 (như B) vì không còn xác sống | Sạch hơn A, phủ cả đường 2 | Field private của thư viện: vỡ khi đổi tên/nâng version (`viewpager2` đang pin 1.1.0), phải `try/catch` + dự phòng về A. `app/build.gradle.kts` không có cấu hình `minify` nên R8 không đổi tên field hôm nay (đọc file build), nhưng sẽ vỡ nếu bật sau | Dự phòng. Chỉ mở khi sau Task 2 chủ dự án thấy nhấc-tay-chậm lệch cảm giác so với fling |
| **D. Bỏ `ViewPager2`, dùng `RecyclerView` + `PagerSnapHelper` riêng** | Toàn quyền | Không xác sống | Mất `ScrollEventAdapter` (callback trang, `currentItem`, FakeDrag, state restore); viết lại wiring `HomePagerAdapter`, `setupHomePager`, kéo-thả mép trang, ghost page, pill — chạm đúng vùng bài học M7 | Không |
| **E. Giữ mặc định** | Không làm gì | Không rủi ro | Giữ nguyên điều tech lead phàn nàn (đường 1 ≤ ~300 ms) | Không |

**Giá trị mục tiêu (hằng chỉnh tay, đặt trong `PageMotion`, KDoc ghi rõ là hằng chỉnh tay):**
- Đường cong: `PathInterpolator(0.22f, 1f, 0.36f, 1f)` = easeOutQuint, **cùng họ** với `sQuinticInterpolator` mà đường 2 đã dùng ⇒ hai đường trông cùng "kiểu".
- Thời lượng: `durationMs(fraction) = MIN_MS + (FULL_MS - MIN_MS) × fraction` với `fraction = \|dx\| / chiềuRộngTrang` kẹp [0,1]; **`FULL_MS = 450`, `MIN_MS = 300`** (vòng 2/5 của Task 2: đo trên Live 4 cho thấy bản 220/400 không chậm hơn mặc định với đoạn còn lại ~30%; sàn 300 ms ngang đường nhấc-tay-chậm mặc định). Khoảng chỉnh tay khi thử: `FULL_MS` 400–500. Vì easeOutQuint dồn phần lớn chuyển động vào đầu, 400 ms cảm giác ngắn hơn 400 ms tuyến tính; đó là lý do không chọn dưới 350.
- Con số là **chỉnh tay theo cảm nhận trên Live 4, không phải suy ra từ đặc tả iOS** (Apple không công bố).

#### Lật có chủ đích (kéo icon chạm mép; lật sang trang trống)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| F. `setCurrentItem(.., true)` như cũ | — | Không code | 170–250 ms phụ thuộc dpi, khác hẳn đường 1/2 | Không |
| G. `RecyclerView.smoothScrollBy(dx, 0, interp, duration)` thẳng (một cơ chế duy nhất với B) | Gọn | — | **[bytecode]**: từ trạng thái rảnh, `ScrollEventAdapter` không phát `onPageSelected` (adapter không ở trạng thái kéo, chưa `notifyProgrammaticScroll`) ⇒ `onGhostPageLeft`, `searchPill.setSelected`, `currentItem` lệch | Không — vì thế **không gộp được thành một cơ chế duy nhất** |
| H. `PagerFlipAnimator` dùng `FakeDrag` (`beginFakeDrag`/`fakeDragBy`/`endFakeDrag`, API công khai **[bytecode]**) với `ValueAnimator` dùng đúng `PageMotion.CURVE` + `durationMs(1f)` | Đi qua `notifyBeginFakeDrag`/`notifyEndFakeDrag` nên callback trang đúng; ease-out kết thúc vận tốc ≈ 0 ⇒ `endFakeDrag` rơi vào `snapToPage` | `beginFakeDrag()` trả `false` khi RV đang `isDragging` (**[bytecode]**) ⇒ dự phòng `setCurrentItem(.., true)`; phải hủy khi `submitList` đặt lại trang; dấu của `fakeDragBy` kiểm trên máy | **Chọn**, chung `PageMotion` với A ⇒ đường 1 và 3 cùng cảm giác (đường 2 xem trên) |

#### Rủi ro của A + H và kịch bản hồi quy

| Rủi ro | Vì sao | Kịch bản hồi quy (Live 4) |
|---|---|---|
| **M7 kéo-thả và lật mép** | Phiên kéo do `Activity.dispatchTouchEvent` giữ, pager chỉ nhận `ACTION_CANCEL` giả (AGENTS M7 Task 5/7). A không đụng chạm, chỉ đổi bộ cuộn sau thả; nhưng `DragHelper.refresh()` chạy ở `SCROLL_STATE_IDLE` và `onPageSelected` xử lý ghost page ⇒ thời lượng dài hơn kéo dài khoảng trước khi `refresh` | (1) Kéo icon sát mép phải/trái: lật ~400 ms, thả đúng ô; (2) giữ mép lâu (lặp 500 ms): không lật chồng/kẹt; (3) kéo ra ngoài trang cuối → trang trống tạm → kéo ngược: `onGhostPageLeft` đúng; (4) thả icon ngay lúc đang lật |
| **App Library mở từ trang cuối (G2a B2)** | Không phải overscroll của pager: là cử chỉ tiếp quản trong `dispatchTouchEvent` (`libraryController.onTouch`), điều kiện `canOpenLibrary()` đòi `!pagerScrolling` và `currentItem` = trang cuối (đọc code `HomeActivity`). Vuốt đến trang cuối rồi vuốt tiếp ngay: settle dài hơn thì cửa sổ bị từ chối dài hơn (≤ ~400 ms thay vì ≤ ~300 ms) | (1) Vuốt tới trang cuối, **chờ yên** rồi vuốt trái: Library mở; (2) vuốt tới trang cuối và **vuốt trái ngay** lúc đang settle: không mở nhầm, không kẹt, vuốt lần 2 mở được; (3) ở trang cuối, kéo nửa chừng rồi thả: về trang cuối, không mở Library |
| **`requestDisallowInterceptTouchEvent`** | Bài học M7 bẫy 2: ô icon gọi cờ này khi bắt đầu kéo để pager không giành chuỗi chạm. A chỉ đổi bộ cuộn **sau khi RV đã nhận chuỗi**, không đụng `onInterceptTouchEvent`. Rủi ro còn lại: cờ kẹt ở `true` sẽ chặn vuốt trang — độc lập với A nhưng dễ nhầm là lỗi của A | Sau mỗi lần nhấn giữ/kéo icon rồi thả: vuốt trang vẫn chạy; nhấn giữ icon rồi vuốt ngang ngay (trước khi vượt slop kéo) vẫn đổi trang bình thường |
| **Xác sống (helper cũ)** | Hai `SnapHelper` cùng nghe `IDLE` | Nhấc tay chậm giữa hai trang (đường 2) nhiều lần: trang luôn dừng thẳng hàng, không giật hai lần; vuốt liên tiếp 3 trang không kẹt `SETTLING` |
| **`onPageSelected` đúng một lần** | Xem phần trên | Mọi lần đổi trang đều cập nhật pill/chấm đúng |

#### Giật khung hình khi lướt — kết quả đo Task 1 đổi ưu tiên

Số đo Task 1 (điều phối chạy trên Live 4, build `f158c84`, 2026-11-03) cho thấy **thời lượng settle không ngắn bất thường** (fling median ~258 ms, nhấc chậm ~300 ms) nhưng **gần như mọi khung hình khi lướt đều trễ**: 1917 khung, Janky 1397 (**72,87%**), p50/p90/p99 = 19/30/44 ms ở 60 Hz. Phân rã theo giai đoạn (120 khung): **DrawStart→SyncQueued (luồng UI ghi display list) p50 14,4 ms / p90 15,5 ms**; input + animation 0,5; measure/layout 0,1; issue draw 2,0 (p90 4,1). GPU p50 15 ms. Đây mới là thứ làm "chưa mượt". Ngưỡng 2,13% của bản nháp **bỏ**: số M6 là cảnh đứng yên, không so được với cảnh vuốt.

`offscreenPageLimit` (cũ là Task 7) nhắm vào bind/inflate, không phải chi phí ghi display list lặp mỗi khung, nên **không còn là hướng đầu tiên**; chỉ xét lại nếu Task 1b chỉ ra bind trang kề. Khi cuộn ngang một `RecyclerView`, các `RenderNode` con lẽ ra chỉ bị dịch vị trí; 14 ms ghi lại mỗi khung nghĩa là có view bị vô hiệu hóa (hoặc vẽ nặng) mỗi khung. Ứng viên đọc từ code, **chưa kiểm chứng**:

| Ứng viên | Chạy mỗi khung cuộn? (đọc code) | Cách kiểm ở Task 1b |
|---|---|---|
| `SearchPillView.setSelected` → `invalidate()` + `onDraw` (nhãn, chấm, `measureText`) | Có: `onPageScrolled` gọi mỗi khung | Đếm + đo `SearchPillView.onDraw`, đo `onPageScrolled+pill.setSelected` |
| `IconCellView.onDraw` (bitmap, `StaticLayout.draw`, dấu "−") | Chỉ nếu ô bị vô hiệu hóa | Đếm số lần `onDraw` trong lúc cuộn (khác 0 = bị ghi lại) |
| `IconGridView.dispatchDraw` / `DockView.dispatchDraw` | Có nếu cha bị vô hiệu hóa | Đo thời gian cả cây con |
| `StaticBlurSurface.drawView.onDraw` (dock: cấp phát `RectF`/`Path`, `clipPath`, `drawBitmap`) | Chỉ nếu `invalidate` | Đếm + đo |
| Ảnh nền `backdrop_image` (`centerCrop`, bitmap lớn) và cha `clipChildren=false` | Có thể | Đối chiếu tổng các đoạn đo được với `DRAW_DURATION` hệ thống: phần thiếu = chi phí nằm ngoài các view tự vẽ ở trên |

Bước đo (Task 1b) so tổng thời gian các đoạn ở trên với `DRAW_DURATION` trung bình. Nếu tổng đoạn << 14 ms thì chi phí nằm ở nơi khác (ảnh nền, cây view trung gian); dùng `atrace` để xem tiếp. **Không lên kế hoạch sửa `IconCellView.kt`, `IconGridView.kt`, `HomePagerAdapter.kt` (vùng file của GL) trước khi điều phối sắp xếp thứ tự với GL.**

### 2. Độ rung

`Jiggler`: `JIGGLE_DEGREES = 5f` (±5°), nửa chu kỳ 160 ms, `LinearInterpolator` + `REVERSE`, pha ngẫu nhiên mỗi view. Apple không công bố số; các bản dựng lại rung iOS phổ biến dùng ±1,5–2,5° **[suy luận, chưa đo trên iPhone]**. Chọn `JIGGLE_DEGREES = 2f` (giữ chu kỳ + interpolator); `IconGridView` và `DockView` dùng chung nên cả hai cùng đổi. Đổi interpolator/chu kỳ/biên độ riêng từng icon vượt phạm vi góp ý ⇒ ghi để sau. **Điều phối đã duyệt phần này nguyên văn (vòng 1/5).**

### 3. Thanh nút edit mode

#### Vấn đề

`applyEditToolbarSpace()` cộng `home_edit_toolbar_space` (64dp) vào `topMargin` của `home_content` khi `editMode` bật. `IconGridView` và `DockView` tính `Cells` từ chiều cao pager (`GridMetrics.measure`) nên **icon/ô co và bind lại** mỗi lần vào/ra edit mode — đúng điều tech lead thấy. Hình học (đọc từ code):
- Icon hàng đầu ở **y = 0 của ô** (`IconCellView` trả `PointF(iconLeft, 0f)`), ô đầu ở `paddingTop = 8dp` (`GridMetrics.PADDING_DP`) ⇒ mép trên icon hàng đầu = `bars.top + 8dp`.
- Dấu "−" có cạnh `iconSize × BADGE_RATIO (0,5)`, tâm đúng góc trên-trái icon ⇒ nhô lên `iconSize/4`. Icon ~56–60dp (ô ≈ 80dp × `ICON_SCALE` 0,75, làm tròn 4dp) ⇒ nhô ~14–15dp (dùng chặn trên **16dp**). Mép trên dấu "−" ô (0,0) ≈ `bars.top − 8dp`: **cao hơn cả đáy thanh trạng thái**.
- **Chiều cao thanh trạng thái Live 4: chưa biết.** AGENTS.md không ghi; Android thường 24–32dp (chưa đo). Task 1 ghi `bars.top` thật. Bảng dưới tính cho 24 / 28 / 32 / 40dp.
- **Điều tôi chưa xét ở bản nháp đầu:** thanh trạng thái hệ thống **vẽ đè lên cửa sổ app** (đồng hồ bên trái, pin/sóng bên phải) và SystemUI thường **giành chạm** ở vùng đó cho cử chỉ kéo thanh thông báo **[suy luận, chưa đo]**. Pill đặt trong thanh trạng thái hiện hữu vừa bị chữ đè, vừa có thể không nhận được chạm. Đây là lý do iOS ẩn thanh trạng thái khi vào jiggle mode. Task 1 có bước đo thật.

#### Phương án

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| L. Overlay thuần, không chừa chỗ | Bỏ push; pill đè đầu lưới | Lưới không đổi | Pill đè icon hàng đầu và dấu "−" ô (0,0) (hình học trên) | Không |
| M. **Thanh trạng thái hiện + khoảng đệm cố định** | Bỏ push. `home_content.topMargin = base + bars.top + topReserve`, áp mọi lúc | Không gì dịch khi vào/ra edit mode; đúng dự phòng đã ghi ở AGENTS "Demo polish" | Pill đè đồng hồ/pin; có thể không nhận chạm (Task 1 xác nhận); mất chiều cao lưới vĩnh viễn (bảng dưới) | Dự phòng (nếu chủ dự án không muốn ẩn thanh và Task 1 chứng minh pill nhận chạm) |
| **P. Ẩn thanh trạng thái khi edit mode (như iOS) + khoảng đệm cố định** | `WindowInsetsControllerCompat.hide(statusBars())` khi `editMode` bật, `show(statusBars())` khi tắt; `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. **Mọi margin tính từ `insets.getInsetsIgnoringVisibility(systemBars())`** (cả `home_content`, `edit_toolbar`, `libraryView.setPadding`, `statusBarTopPx`) nên ẩn thanh **không** đổi margin nào | Pill thế chỗ thanh trạng thái đúng iOS; không đè chữ hệ thống; không phụ thuộc việc SystemUI có giành chạm | Phải chuyển 4 chỗ dùng `bars.top` sang `getInsetsIgnoringVisibility` (API 30 có sẵn; API 26–29 qua `WindowInsetsCompat` dựa trên stable insets **[tài liệu, chưa đo]**; Live 4 là API 30). Vuốt từ mép trên làm thanh hiện tạm (transient) đè pill — hành vi chuẩn Android, chấp nhận được. Thêm một vòng relayout lúc bật/tắt | **Đề xuất của tôi** — chờ chủ dự án (câu hỏi 1) |
| N. Pill ở đáy | Ngược iOS | — | Sai yêu cầu | Không |
| O. Cho lưới tràn dưới thanh trạng thái | — | — | Icon bị thanh che; trái inset đã chốt | Không |

**P so với M theo bốn điểm điều phối yêu cầu:**
- **Khoảng đệm còn cần cho dấu "−":** như nhau. Ẩn thanh không đổi hình học của lưới (lưới vẫn bắt đầu dưới `bars.top` ổn định), nên `topReserve` giữ nguyên. P không tiết kiệm chiều cao.
- **Mất chiều cao lưới:** như nhau; số đã tính lại dưới đây vì vùng chạm phải 48dp (bản nháp đầu ghi 10–17dp là con số cho vùng chạm 28dp).
- **Nhấp nháy / callback inset khi bật–tắt:** M không đổi inset nên không có. P: `hide`/`show` phát `WindowInsetsAnimation` + `onApplyWindowInsets` nhiều lần; vì mọi margin lấy từ `getInsetsIgnoringVisibility` nên giá trị không đổi giữa các khung; khi gán lại `LayoutParams` **bỏ qua nếu bằng giá trị cũ** để không gây `requestLayout` thừa. Nguy cơ còn lại: màn nháy lúc thanh trượt ra/vào; đo ở Task 6.
- **Luật B2 (inset là margin, không padding root):** cả M và P giữ — vẫn `updateLayoutParams` trên `home_content`/`edit_toolbar`; `setPadding` chỉ ở `libraryView` (view con, đã có từ trước, không phải root). Không thêm padding lên `home_root`.

#### Vùng chạm pill ≥ 48dp

Ba cơ chế: (i) `MaterialButton` `insetTop`/`insetBottom` (hộp view lớn, nền vẽ nhỏ); (ii) `TouchDelegate` trên cha; (iii) cha có padding bọc nút. **Chọn (i)**: là chính cơ chế Material mặc định (nền 40dp + inset 6dp mỗi phía = hộp 52dp), chạm trên toàn hộp view, không thêm view/listener. (ii) bị loại: `TouchDelegate` chỉ nhận chạm trong biên của cha, nên hộp mở rộng vẫn phải nằm trong cha ⇒ cùng hình học, thêm code; (iii) thêm một lớp view mà không giải quyết gì thêm.

Hộp chạm 48dp phải nằm **hoàn toàn trên màn hình** (phần vượt mép trên không ai chạm được), nên: `boxTop = 0`, nền pill 28dp ở giữa hộp (`insetTop = insetBottom = 10dp`) ⇒ pill nhìn ở `[10, 38]dp`, hộp `[0, 48]dp`. Hộp **không được chồng lên dấu "−" ô (0,0)** (hộp nằm trên lưới nên sẽ nuốt chạm của dấu "−"; hộp Xong có thể chạm dấu "−" của cột cuối). Do đó:

`topReserve = max(0, 48dp + khe 2dp + 16dp (nhô dấu "−") − 8dp (paddingTop lưới) − bars.top) = max(0, 58dp − bars.top)`

| `bars.top` (giả định; của Live 4 chưa biết) | `topReserve` (hộp chạm 48dp) | Nếu chỉ chạm 28dp (đã bị loại) |
|---|---|---|
| 24dp | **34dp** | 18dp |
| 28dp | **30dp** | 14dp |
| 32dp | **26dp** | 10dp |
| 40dp | **18dp** | 2dp |
| ≥ 58dp | 0 | 0 |

So với 64dp bị đẩy xuống ở edit mode hiện tại: nhỏ hơn nhưng **cố định mọi lúc**. Tác động lên cỡ icon: `GridMetrics.measure` lấy `min(ICON_MAX, cellWidth, cellHeight − nhãn − khe) × 0,75`; ở lưới 4 cột, icon bị chặn bởi **chiều rộng** (cellWidth ≈ 80dp), không phải chiều cao, nên hạ vài chục dp chiều cao lưới **chủ yếu thu khoảng cách hàng** (mỗi hàng ~`reserve/6` ≈ 4–6dp), cỡ icon khả năng không đổi **[suy luận từ `GridMetrics.measure`; Task 1 ghi `iconSize` và `cellHeight` thật]**.

**Khuyến nghị cho câu hỏi 1:** phương án P (ẩn thanh trạng thái) + `topReserve` cố định với hộp chạm 48dp. Giá trên Live 4 = `58 − bars.top` dp (30–34dp nếu thanh 24–28dp); con số chính xác có sau Task 1.

### Đánh đổi chấp nhận

- `PageMotion` là hằng chỉnh tay; đường 2 (nhấc tay chậm) vẫn do mặc định xử lý (300–600 ms), có thể lệch nhẹ cảm giác so với fling. Nếu chủ dự án thấy lệch: mở phương án C (reflection, có dự phòng).
- Xác sống `SnapHelper` cũ chấp nhận được (lập luận ở trên, kiểm ở Task 2).
- Home thấp hơn `topReserve` mọi lúc để pill chạm 48dp không nuốt dấu "−".
- P đổi hành vi thanh trạng thái khi edit mode (như iOS) và thêm nhánh chuyển insets sang "bỏ qua visibility".

## Quyết định đã chốt

Chủ dự án duyệt plan ngày 2026-11-03 (qua điều phối). Các dòng D1–D5 là đề xuất agent đã được duyệt cùng plan; D6, D7 do chủ dự án quyết.

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Vuốt tay: `TunedPagerSnapHelper` (phương án A) là Task lõi; `PageMotion`: easeOutQuint `PathInterpolator(0.22, 1, 0.36, 1)`, `FULL_MS = 450`, `MIN_MS = 300` (nâng ở Task 2 vòng 2/5), hằng chỉnh tay (400–500) | Đề xuất agent 2026-11-03, sửa theo rà vòng 1/5; cơ sở: bảng ba đường chuyển trang |
| D2 | Lật có chủ đích: `PagerFlipAnimator` (FakeDrag) dùng chung `PageMotion`; không gộp được vào một cơ chế vì `ScrollEventAdapter` không phát `onPageSelected` cho `smoothScrollBy` thẳng | Đề xuất agent; **[bytecode]** |
| D3 | Ngưỡng Janky 2,13% bỏ (cảnh đứng yên của M6, không so được). Nguyên nhân giật = chi phí ghi display list ~14 ms/khung (Task 1); nguyên nhân gốc xác định ở Task 1b rồi mới chọn cách sửa. `offscreenPageLimit` hạ xuống thử nghiệm phụ | Đề xuất agent theo số đo Task 1, 2026-11-03 |
| D4 | `JIGGLE_DEGREES` 5f → 2f | Điều phối duyệt vòng 1/5 |
| D5 | Bỏ `applyEditToolbarSpace`/`home_edit_toolbar_space`; pill nhìn 28dp, hộp chạm 48dp bằng `insetTop/insetBottom` 10dp; `topReserve = max(0, 58dp − bars.top)` cố định | Đề xuất agent; điều phối yêu cầu chạm ≥ 48dp |
| D6 | **Ẩn thanh trạng thái khi edit mode (phương án P) = CÓ**; chấp nhận `topReserve` cố định (hộp chạm 48dp, pill nhìn 28dp, Home thấp hơn `max(0, 58dp − bars.top)` mọi lúc). Task 6 không còn có điều kiện, vẫn bắt buộc kiểm tay (kiểm nháy) trong phiên máy với điều phối | Chủ dự án 2026-11-03 (câu hỏi 1) |
| D7 | Phương án C (reflection gỡ helper cũ của ViewPager2) **được phép**, chỉ khi kiểm máy Task 2 cho thấy nhấc-tay-chậm lệch cảm giác so với fling; phải bọc `try/catch` (thiếu field thì giữ hành vi mặc định) và ghi vào Nhật ký | Chủ dự án 2026-11-03 (câu hỏi 2) |

## Câu hỏi cho chủ dự án (đã trả lời 2026-11-03: câu 1 = ẩn thanh + chấp nhận `topReserve`; câu 2 = cho phép phương án C có điều kiện, xem D6, D7)

1. **Edit mode có ẩn thanh trạng thái như iOS (phương án P) không?** Khuyến nghị: có. Chi phí chung cho cả P và M: Home thấp hơn `58 − bars.top` dp mọi lúc để pill chạm 48dp không đè dấu "−" hàng đầu (thanh 24dp → 34dp, 28dp → 30dp, 32dp → 26dp; **chiều cao thanh trạng thái Live 4 chưa biết**, Task 1 ghi). Muốn đỡ mất chiều cao hơn thì phải chấp nhận pill chạm 28dp (reserve 18 / 14 / 10dp) — đã bị loại theo yêu cầu điều phối, chỉ nêu để chủ dự án biết giá.
2. Nếu sau Task 2 vuốt tay đã đúng ý nhưng nhấc-tay-chậm (đường 2) lệch cảm giác so với fling, có đồng ý mở phương án C (reflection field private của `ViewPager2`, có dự phòng) không?

## Ngoài phạm vi / ghi để sau

- Bo góc icon (nhánh TL2); kính (nhánh GL), kể cả cho pill.
- Đổi interpolator/chu kỳ rung, biên độ riêng từng icon.
- Bỏ `invalidate()` mỗi khung của `SearchPillView.setSelected` (báo GL nếu số đo chỉ ra pill gây giật).
- Hiệu ứng xuyên trang (`PageTransformer`): iOS không có, không thêm.
- RTL: dự án chưa hỗ trợ; `PagerFlipAnimator` và `TunedPagerSnapHelper` chỉ kiểm LTR, ghi chú trong code.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt` | Sửa: `setupHomePager` (gắn `TunedPagerSnapHelper`, `PagerFlipAnimator`), `onDragEdge`, nhánh lật trang tạm trong `render()`, `applyEditToolbarSpace`/`editToolbarSpacePx`/`editSpaceApplied`, `applySystemBarInsets` (+ `getInsetsIgnoringVisibility` nếu chọn P), chỗ ẩn/hiện thanh trạng thái theo `editMode`; (tạm) log `TL1`; (tùy) `offscreenPageLimit` | **Nóng, độc quyền** (nhánh này giữ trong đợt 0a). GL chỉ thêm một đoạn hẹp ở hàm gắn kính (~dòng 740–775); tôi không đụng đoạn đó và không reformat |
| `app/src/main/res/layout/activity_home.xml` | Sửa: khối `edit_toolbar` + comment của nó | **Nóng, độc quyền**. GL chỉ động khối panel thư mục/nhóm |
| `app/src/main/java/gambi/global/os26launcher/ui/home/PageMotion.kt` | **Tạo** (hằng chung) | Không |
| `app/src/main/java/gambi/global/os26launcher/ui/home/TunedPagerSnapHelper.kt` | **Tạo** | Không |
| `app/src/main/java/gambi/global/os26launcher/ui/home/PagerFlipAnimator.kt` | **Tạo** | Không |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurface.kt`, `core/glass/RealGlassSurface.kt`, `ui/home/view/DockView.kt` | Sửa, chỉ thêm (Task 2b): `setInvalidationSuppressed`, lớp con `SuppressibleGlassView`, chuyển tiếp ở `DockView` | **Vùng GL — ngoại lệ hẹp do điều phối cấp 2026-11-03** (TL1 merge trước GL, GL rebase). Không reformat |
| `app/src/main/java/gambi/global/os26launcher/ui/home/LibraryOverlayController.kt` | Sửa (Task 9): kiểm lại `canOpen()` khi chốt kéo | **Ngoại lệ do điều phối cấp 2026-11-03** (không nhánh nào khác đụng) |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/Jiggler.kt` | Sửa: `JIGGLE_DEGREES` + KDoc | Không (dùng chung `IconGridView`, `DockView`; không sửa hai file đó) |
| `app/src/main/res/values/dimens.xml` | Sửa: khối riêng cuối file cho pill + hằng `topReserve`; xóa `home_edit_toolbar_space` | Dùng chung có kiểm soát |
| `app/src/main/res/values/strings.xml` | Không sửa | Dùng chung |
| `docs/plans/TL1-home-polish.md`, `docs/knowledgements/TL1-home-polish.md` | Tạo | Không |

Không đụng: `IconLoader`/`IconCache` (TL2), `core/glass/*`, `FolderPanelView`, `CategoryGroupPanelView`, `SearchPill*` (GL), `IconGridView`, `DockView`, `HomePagerAdapter`, `LibraryOverlayController`, `DragHelper`.

## Global Constraints

- Không padding lên root layout; inset áp thành margin của view con (AGENTS "Inset và layout màn hình chính"); margin gốc đọc một lần, tính lại từ margin gốc mỗi lần inset đổi, không cộng dồn.
- Trạng thái kéo-thả là dữ liệu, không nằm trong view của RecyclerView/ViewPager2; cử chỉ kéo-thả do `Activity.dispatchTouchEvent` giữ (AGENTS M7). `PagerFlipAnimator`/`TunedPagerSnapHelper` không giữ trạng thái kéo-thả, không đụng `onInterceptTouchEvent`/`requestDisallowInterceptTouchEvent`.
- Không reformat, không di chuyển code ngoài khu toolbar/insets/pager/jiggle của `HomeActivity` để GL rebase sạch.
- Chuỗi hiển thị trong `strings.xml`; kích thước trong `dimens.xml`; hằng thuật toán (thời lượng animation) trong `PageMotion` kèm KDoc ghi "hằng chỉnh tay".
- Không xóa file; không thêm test; không cài APK trừ khi được bảo. Không reflection trừ khi mở phương án C (cần duyệt).
- Commit message tiếng Anh, kết thúc bằng dòng `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Không còn nơi nào cộng gì vào `home_content.topMargin` theo `editMode` (grep `editToolbarSpace`, `editSpaceApplied`, `home_edit_toolbar_space` ra rỗng).
2. Vào/ra edit mode: `Cells` của `IconGridView` và `DockView` không đổi (lưới không dịch, không bind lại); nếu chọn P: không một margin nào đổi khi thanh trạng thái ẩn/hiện.
3. `TunedPagerSnapHelper`: `onFlingListener = null` trước khi gắn (tránh `IllegalStateException`), `findSnapView` trả `null` khi `isFakeDragging`, không reflection, vuốt tay không bao giờ kẹt `SETTLING`.
4. `PagerFlipAnimator`: hủy đúng khi `submitList` gọi `setCurrentItem(..., false)`, khi activity dừng/hủy, khi `beginFakeDrag()` trả false (dự phòng), khi gọi chồng.
5. `onPageSelected` đúng một lần; `onGhostPageLeft`, lật sang trang tạm (`pendingFlipToNewPage`), `DragHelper.refresh()` ở `IDLE`, `canOpenLibrary()` còn hoạt động.
6. Pill: nhìn 28dp, hộp chạm 48dp, không chồng lên dấu "−" ô (0,0) và hàng đầu ở `P4X5`, `P4X6`, `P5X6`.
7. Không còn log tạm `TL1` trong bản cuối.
8. Task 2b: `invalidate()` bù lúc IDLE chấp nhận một khung chụp lại nền (p99 28 ms); kiểm kính dock vẫn đúng sau đổi hình nền / lật mép / tách đôi.
9. Task 3: `render()` không đặt lại trang (`setCurrentItem(.., false)`) khi `pagerFlip.isFlipping`, vì kéo giả giữ `currentItem` ở trang cũ đến lúc settle.

---

### Task 1: Đo nền trên Live 4 (không đổi hành vi) — ĐÃ QUA REVIEW (2026-11-03)
**Bắt buộc kiểm tay:** có — mục 4.2 workflow: "phép đo hiệu năng dùng để chọn phương án" (có cần `offscreenPageLimit` không; dải thời lượng thật của hai đường thả tay; chiều cao thanh trạng thái; pill có nhận chạm trong vùng thanh hiện hữu không → chọn M hay P).
**Files:** `HomeActivity.kt` (log tạm `TL1`), `activity_home.xml`/`dimens.xml` (chỉ trong commit tạm cho bước thăm dò).
**Interfaces:** Consumes `ViewPager2.OnPageChangeCallback`, `statusBarTopPx`. Produces: bảng số đo trong Nhật ký.
- [x] **Step 1:** Commit riêng `chore: add temporary TL1 paging and inset logs`: trong `onPageScrollStateChanged` ghi `TL1 state=<n> t=<uptimeMillis>`; trong `applySystemBarInsets` ghi `TL1 bars.top=<px>/<dp>`; khi `Cells` có thì ghi `TL1 icon=<dp> cellH=<dp>`; trong click của `btn_settings`/`btn_done` ghi `TL1 tap settings|done`.
- [x] **Step 2:** Commit tạm `chore: temporary toolbar probe`: đặt `edit_toolbar` đúng hình học dự kiến (nền pill 28dp ở `[10,38]dp`, hộp 48dp, chưa có `topReserve`) để thăm dò chạm. Hai commit tạm sẽ được gỡ ở Task 8.
- [x] **Step 3:** Build `gradlew.bat assembleDebug` trong worktree, báo điều phối.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — chủ dự án chạy nguyên khối dưới đây (PowerShell, trong thư mục worktree). Fling `DRAGGING→SETTLING→IDLE` kỳ vọng ≤ ~300 ms [bytecode]; số đo thật quyết định `FULL_MS` ban đầu. Pill trong thanh hiện hữu: bấm 5 lần mỗi nút, nhận 5/5 ⇒ M khả thi; nhận < 5/5 ⇒ buộc P (hoặc dời pill xuống dưới thanh).

```powershell
# 1) Cài (trong worktree) và ghi commit đã cài
git rev-parse --short HEAD
.\gradlew.bat installDebug

# 2) Chuẩn bị: dừng app, xóa log, mở Home, đưa số đo về 0
adb shell am force-stop gambi.global.os26launcher
adb logcat -c
adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity
adb shell dumpsys gfxinfo gambi.global.os26launcher reset

# 3) Kịch bản vuốt (cần >= 3 trang; tổng ~25 s). Lấy kích thước màn hình
$wm = (adb shell wm size) -replace '.*: ',''
$w,$h = $wm.Trim().Split('x') | ForEach-Object { [int]$_ }
$y = [int]($h*0.45); $xa = [int]($w*0.85); $xb = [int]($w*0.15); $xm = [int]($w*0.45)
# 10 vong fling trai-phai (swipe nhanh 120 ms)
1..10 | ForEach-Object {
  adb shell input swipe $xa $y $xb $y 120
  Start-Sleep -Milliseconds 800
  adb shell input swipe $xb $y $xa $y 120
  Start-Sleep -Milliseconds 800
}
# 5 vong nhac tay cham (swipe 1500 ms ve gan giua man hinh, khong fling)
1..5 | ForEach-Object {
  adb shell input swipe $xa $y $xm $y 1500
  Start-Sleep -Milliseconds 1200
  adb shell input swipe $xm $y $xa $y 1500
  Start-Sleep -Milliseconds 1200
}

# 4) Doc ket qua
adb shell dumpsys gfxinfo gambi.global.os26launcher framestats | Select-String "Total frames rendered|Janky frames|50th|90th|95th|99th|Number Missed Vsync"
adb logcat -s TL1 -d > tl1-log-swipe.txt
```

Sau đó thủ công trên máy rồi chạy `adb logcat -c` **trước** bước này và `adb logcat -s TL1 -d > tl1-log-manual.txt` **sau**: (a) nhấn giữ icon để vào edit mode; bấm **Cài đặt** 5 lần (Back về edit mode sau mỗi lần), rồi bấm **Xong** (vào lại edit mode, bấm Xong, lặp 5 lần), ghi mỗi lần nhận hay không; (b) vào edit mode, kéo một icon sát mép phải giữ 1 s, rồi kéo ra ngoài trang cuối để sinh trang trống. Gửi điều phối: hai file log, các dòng `Total frames rendered` / `Janky frames` / phân vị, ảnh chụp màn hình lúc ở edit mode, và kết quả nhận chạm 5/5 hay không của từng nút.

**Kết quả Task 1 (điều phối chạy trên Vsmart Live 4, build `f158c84`, 2026-11-03):**
- Máy: 1080×2340, density 3,0, một chế độ hiển thị 60 Hz (vsync 16,67 ms). `bars.top` = 96 px = **32 dp** (thanh trạng thái là vùng cutout 96 px). Lưới: icon **60 dp**, `cellH` 80 dp, pager 1080×1656 px (lần layout đầu log icon 44 dp rồi ổn định ở 60). ⇒ `topReserve = max(0, 58 − 32)` = **26 dp**.
- Settle fling (20 lần, SETTLING→IDLE): 224–307 ms, median ~258 ms. Nhấc chậm (10 lần): kéo ~1335 ms rồi SETTLING→IDLE 295–305 ms, phẳng ~300 ms. Khớp dự đoán đọc bytecode (fling ≤ ~300 ms).
- `gfxinfo` cả kịch bản: 1917 khung, Janky 1397 (72,87%), p50/p90/p95/p99 = 19/30/32/44 ms; Missed Vsync 10, Slow UI thread 72, Slow bitmap uploads 1, Slow issue draw 5; GPU p50/p90/p95 = 15/21/25 ms. Home đứng yên 10 s: 0 khung.
- Phân rã 120 khung (p50/p90 ms): total 20,0/31,1; input+animation 0,5/0,9; measure/layout 0,1/0,2; **DrawStart→SyncQueued 14,4/15,5**; sync/upload 0,2/0,2; issue draw 2,0/4,1; Swap→FrameCompleted 0,9/8,3.
- Thăm dò chạm pill trong thanh trạng thái: bỏ qua (D6 đã chốt ẩn thanh nên không đổi quyết định). Kiểm lật mép + ghost page dời sang Task 3.
- Lưu ý cho Task 6: 96 px có thể nằm ở **inset cutout** chứ không chắc ở inset `statusBars()`. Khi ẩn thanh trạng thái, phải kiểm trên máy xem `getInsetsIgnoringVisibility(systemBars())` hay inset cutout còn giữ 96 px; `topReserve` cần tính theo chiều cao vùng trên cùng mà nội dung thật sự phải tránh.

### Task 1b: Tìm chi phí ghi display list khi cuộn (chỉ điều tra, chưa sửa) — ĐÃ QUA REVIEW (2026-11-03)
**Bắt buộc kiểm tay:** có — phép đo hiệu năng dùng để chọn phương án (mục 4.2); điều phối tự chạy trên Live 4.
**Files (tạm, một commit để `git revert` ở Task 8):** tạo `ui/home/Tl1Prof.kt`; chỉnh tạm `HomeActivity.kt`, `view/IconCellView.kt`, `view/IconGridView.kt`, `view/DockView.kt`, `view/SearchPillView.kt`, `core/glass/StaticBlurSurface.kt`. (File của GL: chỉ gói thân `onDraw`/thêm `dispatchDraw` gọi `super`, không đổi hành vi, sẽ bị revert.)
- [x] **Step 1:** Đọc code: lúc cuộn `onPageScrolled` chạy mỗi khung và gọi `SearchPillView.setSelected` → `invalidate()`; `onPageScrollStateChanged` chạy theo đổi trạng thái (không mỗi khung); `IconCellView.onDraw` chỉ chạy khi display list của ô bị ghi lại; `StaticBlurSurface` chỉ `invalidate` ở `setBackdrop`/`setCornerRadius`.
- [x] **Step 2:** `Tl1Prof`: `section(name){}` = `android.os.Trace` + cộng dồn ns/số lần; bộ nghe `FrameMetrics` (DRAW/LAYOUT/ANIM/TOTAL, số khung > 16,7 ms); `reset()` khi pager `DRAGGING`, `dump()` khi `IDLE` in một dòng `TL1P ...`.
- [x] **Step 3:** Gói `IconCellView.onDraw`, `SearchPillView.onDraw`, `StaticBlur.onDraw`, `IconGridView.dispatchDraw`, `DockView.dispatchDraw`, `onPageScrolled+pill.setSelected`.
- [x] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)** — dùng khối lệnh Task 1 (phần 10 fling + 5 nhấc chậm), rồi `adb logcat -s TL1 -d | findstr TL1P`. Đọc từng lần vuốt: `frames`, `avg_ms[draw=...]`, và từng đoạn (`n`, `sum`, `avg`). Diễn giải: (a) `IconCellView.onDraw n>0` trong lúc cuộn ⇒ ô bị ghi lại mỗi khung; (b) `SearchPillView.onDraw` sum so với `draw`; (c) `IconGridView.dispatchDraw` + `DockView.dispatchDraw` sum ≈ `draw` ⇒ chi phí nằm trong cây con; (d) tổng đoạn << `draw` ⇒ chi phí ở nơi khác (ảnh nền, cây trung gian) ⇒ dùng `atrace`.
- [x] **Step 5 (tùy):** (điều phối đã dùng perfetto + simpleperf thay atrace) xem sâu: `adb shell atrace -t 12 -b 16384 -a gambi.global.os26launcher gfx view sched freq -o /data/local/tmp/tl1.atrace` (vuốt trong 12 giây đó; nếu section `TL1 ...` không hiện thì `adb shell am force-stop gambi.global.os26launcher` rồi mở lại app sau khi đặt `-a`), rồi `adb pull /data/local/tmp/tl1.atrace`. Có thể thử thêm `adb shell setprop debug.hwui.show_dirty_regions true` (khởi động lại app) để thấy vùng bị vô hiệu hóa mỗi khung.

**Kết quả Task 1b (điều phối, Live 4, build `b78aa24`):**
- `TL1P`, ~23 khung mỗi lần vuốt: `FrameMetrics` draw ≈ 13,5 ms/khung nhưng các đoạn đo được nhỏ: `SearchPillView.onDraw` n≈24, ~4 ms tổng; `onPageScrolled+pill.setSelected` ~9 ms tổng; `IconCellView.onDraw` 0,2–0,9 ms tổng; `IconGridView.dispatchDraw` n=1. ⇒ trang và ô **không** bị ghi lại; chi phí nằm chỗ khác.
- Perfetto (atrace lỗi trên kernel máy này, perfetto có cấu hình `atrace_apps` chạy được): `Record View#draw()` trung bình 8,9 ms/116 khung, không đoạn nào của `Tl1Prof` giải thích.
- simpleperf (`--app -g`, 6 s lúc vuốt): ~65–67% mẫu CPU của app nằm trong chuỗi `ThreadedRenderer.updateRootDisplayList → … → View.draw → LiquidGlassView.draw → onDraw → drawGlassEffect → renderGlassEffectSync → EnhancedBlurEffect.captureBackdrop → SkCanvas::onDrawImageRect`.
- Máy có `glass_level_override = FORCE_HIGH` (chủ dự án đặt) ⇒ tier LENS ⇒ `RealGlassSurface`. Đổi tạm sang AUTO (`StaticBlurSurface`) và chạy lại kịch bản: **Janky 2,34%**, 1966 khung, p50/p90/p95/p99 = 10/12/13/20 ms, Slow UI thread 13, `TL1P` draw ~0,6–1,2 ms/khung (`SearchPillView.onDraw` ~0,3 ms/khung). FORCE_HIGH: 72,87%, p50 19 ms. Đã trả về FORCE_HIGH.
- **Kết luận:** vuốt trang mượt ở tier mặc định của Live 4; giật khi dock là kính thật **và** `LiquidGlassView` bị vẽ lại mỗi khung cuộn. Ảnh hưởng người dùng thật: mọi máy tier LENS (API 33+, vd Oppo A3x) mặc định dùng `RealGlassSurface`.

**Step 6 (đọc code + `javap` jar `liquidglass-v2.0.11`, 2026-11-03): vì sao `LiquidGlassView` của dock bị vẽ lại mỗi khung cuộn**

(a) Chuỗi vô hiệu hóa, có bằng chứng bytecode:
1. `ViewPager2` → `RecyclerView.dispatchOnScrolled(int,int)` gọi `View.onScrollChanged(IIII)` **[bytecode `recyclerview-1.4.0`]** (offset 32 trong `dispatchOnScrolled`). Mỗi khung cuộn.
2. `View.onScrollChanged` đặt `mAttachInfo.mViewScrollChanged`; ở lần traversal kế `ViewRootImpl` gọi `ViewTreeObserver.dispatchOnScrollChanged()` cho **mọi** `OnScrollChangedListener` của cửa sổ **[tài liệu/AOSP, không javap được framework]**.
3. `LiquidGlassView` có field `backdropScrollListener: ViewTreeObserver.OnScrollChangedListener` và `registeredBackdropObserver` **[bytecode]**. `registerBackdropScrollListener()` đăng ký nó vào `ViewTreeObserver` của `backdropSource` (ở đây `backdrop_image`) mỗi khi có `backdropSource` và view đã attach, **không kiểm tra `enableDynamicBackground`** (gọi từ `onAttachedToWindow` và từ setter `backdropSource`).
4. Thân listener (`backdropScrollListener$lambda$1`) chỉ có `invalidate()` vô điều kiện **[bytecode]**.
5. `LiquidGlassView.draw` → `onDraw` → `drawGlassEffect` → `renderGlassEffectSync`. Ở đó, `enableDynamicBackground = false` chỉ làm bước tính chữ ký ảnh nền (`computeBackdropSignature`) thay cho việc luôn coi là "đổi" để quyết định có **làm mờ lại** không; còn **chụp ảnh nền** (`customBackdropCapture` hoặc `EnhancedBlurEffect.captureBackdrop`) vẫn chạy mỗi lần vẽ **[bytecode: `renderGlassEffectSync` chụp trước nhánh `enableDynamicBackground` ở offset 395]**. Khớp với simpleperf (`captureBackdrop → onDrawImageRect` chiếm ~65%).

Hệ quả: bất kỳ cuộn nào trong cửa sổ (pager lưới) làm dock kính thật chụp lại nền trên CPU mỗi khung. Giả thuyết "pill `invalidate` lan sang dock" (ứng viên 1 ở bảng trên) **bị bác**: `invalidate()` chỉ làm bẩn view đó và cha của nó, không phải view anh em; số đo cũng cho thấy pill chỉ ~0,3 ms/khung. `pulseGlassRefresh`/`render()` không chạy mỗi khung cuộn.

(b) Phương án sửa và vùng file:

| Phương án | Cách làm | Vùng file | Ưu | Nhược |
|---|---|---|---|---|
| S1. Chặn `invalidate()` của kính thật trong lúc pager cuộn | Lớp con `LiquidGlassView` trong `RealGlassSurface` ghi đè `invalidate()` (lệnh gọi từ listener là `invokevirtual invalidate()V`, ghi đè được; thư viện không `final`): nếu cờ `suppressWhileScrolling` bật thì bỏ qua. `GlassSurface` thêm `setInvalidationSuppressed(Boolean)` (mặc định no-op, `StaticBlurSurface` không cần); `DockView` chuyển tiếp; `HomeActivity.onPageScrollStateChanged` bật khi state ≠ IDLE, tắt khi IDLE | `core/glass/GlassSurface.kt`, `RealGlassSurface.kt`, `ui/home/view/DockView.kt` (**vùng GL**) + 2–3 dòng trong `HomeActivity.onPageScrollStateChanged` (**vùng TL1**) | Dock tĩnh trước ảnh nền tĩnh nên display list cũ vẫn đúng; không sửa thư viện; áp dụng chung cho mọi kính thật khác về sau | Mất invalidate thật đến đúng lúc đang cuộn (vd `pulseGlassRefresh` giữa lúc lướt): xử lý bằng một `invalidate()` bù khi về IDLE nếu cờ "có yêu cầu bị chặn" ngoài listener; nếu không phân biệt được thì chấp nhận một lần vẽ lại lúc IDLE (~một khung) |
| S2. Gỡ listener khỏi kính thật | Đặt `backdropSource = null` lúc cuộn | như S1 | — | Mất nền kính tức thì: loại |
| S3. Tier mặc định cho LENS là `StaticBlurSurface` | Sửa `GlassSurfaceFactory` | `core/glass` (vùng GL) | Đơn giản | Mất kính thật trên máy mạnh, quyết định sản phẩm |
| S4. Ngăn pager phát `onScrollChanged` | Không khả thi: `RecyclerViewImpl` của ViewPager2 là lớp nội bộ, `View.onScrollChanged` không có hook | — | — | Loại |
| S5. Chụp lại nền hiệu quả hơn trong thư viện | Sửa `liquidglass` | AAR bên ngoài | — | Không sửa được |

Đề xuất: **S1** (chủ yếu ở vùng GL; điều phối chuyển cho GL). Phần của TL1 chỉ là gọi cờ từ `onPageScrollStateChanged` (khối pager) sau khi GL merge API. Kiểm trên Live 4 với FORCE_HIGH: kỳ vọng Janky về cỡ 2%, `TL1P` draw ~1 ms, chuỗi `captureBackdrop` biến mất khỏi simpleperf.

### Task 2: `PageMotion` + `TunedPagerSnapHelper` (vuốt tay — Task lõi) — ĐÃ QUA REVIEW (vòng 2/5, 2026-11-03)
**Bắt buộc kiểm tay:** không (hồi quy đủ trong bài kiểm cuối; chủ dự án có thể chọn kiểm sớm vì đây là phần tech lead quan tâm nhất).
**Files:** tạo `ui/home/PageMotion.kt`, `ui/home/TunedPagerSnapHelper.kt`; sửa `HomeActivity.setupHomePager`.
**Interfaces:** Produces `PageMotion.CURVE`, `PageMotion.durationMs(fraction: Float): Int`; `TunedPagerSnapHelper(pager: ViewPager2) : PagerSnapHelper`. Consumes `SnapHelper.createScroller`, `calculateDistanceToFinalSnap`, `LinearSmoothScroller`, `RecyclerView.SmoothScroller.Action.update`.
- [x] **Step 1:** Viết `PageMotion` (KDoc: hằng chỉnh tay, dải thử 350–500, vì sao easeOutQuint).
- [x] **Step 2:** Viết `TunedPagerSnapHelper`: `findSnapView` null khi `pager.isFakeDragging`; `createScroller` trả `null` nếu layout manager không phải `ScrollVectorProvider` (như `PagerSnapHelper`), nếu không trả `LinearSmoothScroller` ghi đè `calculateSpeedPerPixel` (100f/dpi, giữ pha tìm đích như cũ) và `onTargetFound` gọi `action.update(dx, dy, PageMotion.durationMs(abs(dx)/pager.width), PageMotion.CURVE)`.
- [x] **Step 3:** `setupHomePager()`: dùng RV bên trong (đã có dòng `getChildAt(0) as RecyclerView`), `rv.onFlingListener = null`, `TunedPagerSnapHelper(binding.homePager).attachToRecyclerView(rv)`. Ghi chú trong code về helper cũ "xác sống" và vì sao vô hại.
- [x] **Step 4:** Build xanh, commit `feat: tune swipe-release page settle with TunedPagerSnapHelper`, cập nhật Nhật ký.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) vuốt nhanh (fling) qua từng trang: dừng mượt ~400 ms, không giật cục cuối; (b) nhấc tay chậm giữa hai trang: về trang gần hơn, không giật hai lần; (c) vuốt liên tiếp 3 trang: không kẹt; (d) chạm giữa lúc đang settle: bắt được trang, kéo tiếp bình thường; (e) so cảm giác fling và nhấc-chậm: có lệch rõ không (báo điều phối để mở phương án C); (f) lặp phép đo log `TL1` để xác nhận thời lượng settle fling khớp `durationMs`.

**Kết quả Task 2 vòng 2 và Task 2b (điều phối đo Live 4, build `2d9d895`, FORCE_HIGH):**
- Task 2: `tuned settle` được log 30 lần cho 30 lần vuốt ⇒ scroller của ta chạy. Fling: fraction ~0,36–0,44, duration tính 353–365 ms; SETTLING→IDLE đo 300–318 ms (trạng thái SETTLING trễ ~50 ms so với lúc scroller bắt đầu). Nhấc chậm: 350–353 ms. Chuỗi trạng thái `1,2,0` mỗi lần (không snap đôi). Hai đường cùng quanh 300–360 ms; cảm giác do chủ dự án phán ở lượt kiểm cuối.
- Task 2b: 2042 khung, **Janky 1,76%** (trước 72,87%), p50/p90/p95/p99 = 6/8/10/28 ms, Slow UI thread 28; tốt hơn cả AUTO/StaticBlur (2,34%, p50 10 ms). Ảnh chụp dock sau khi vuốt: kính nguyên vẹn, không đen/trắng.
- Chưa kiểm trên máy (đưa vào lượt kiểm cuối): đổi hình nền (`pulseGlassRefresh`), lật mép ở edit mode, tách đôi màn hình.
- Ghi chú: p99 28 ms có thể là khung `invalidate()` bù lúc IDLE; chấp nhận vì không còn gì chuyển động. Nếu chủ dự án thấy khựng ở cuối lượt vuốt: trì hoãn bù bằng `post`.

### Task 2b: Chặn `invalidate()` của kính thật khi pager cuộn (S1) — ĐÃ QUA REVIEW (2026-11-03)
**Bắt buộc kiểm tay:** có — sửa dựa trên giả định chỉ máy thật trả lời được (kính thật có vẽ lại đúng sau khi chặn, số Janky giảm; mục 4.2). Điều phối đo trên Live 4 với `FORCE_HIGH`.
**Ngoại lệ vùng file (điều phối cấp 2026-11-03):** được sửa tối thiểu, chỉ thêm, trong vùng GL: `core/glass/GlassSurface.kt`, `core/glass/RealGlassSurface.kt`, `ui/home/view/DockView.kt`. GL sẽ rebase lên TL1.
**Files:** `GlassSurface.kt` (thêm `setInvalidationSuppressed(Boolean)`, mặc định no-op), `RealGlassSurface.kt` (lớp con `SuppressibleGlassView : LiquidGlassView` ghi đè `invalidate()`; khi hết chặn thì `invalidate()` bù một lần nếu có lệnh bị bỏ), `DockView.kt` (`setGlassInvalidationSuppressed` chỉ chuyển tiếp), `HomeActivity.onPageScrollStateChanged` (bật khi state ≠ IDLE, tức DRAGGING/SETTLING, tắt khi IDLE).
- [x] **Step 1:** `GlassSurface.setInvalidationSuppressed` mặc định `{}`; `StaticBlurSurface` không đổi.
- [x] **Step 2:** `RealGlassSurface`: `SuppressibleGlassView` (cờ `suppressed`, cờ `droppedInvalidate`); `setInvalidationSuppressed` chuyển xuống view.
- [x] **Step 3:** `DockView.setGlassInvalidationSuppressed`; `HomeActivity` gọi từ `onPageScrollStateChanged`. Kéo giả (`PagerFlipAnimator`, kéo icon chạm mép) cũng đi qua `DRAGGING` (`notifyBeginFakeDrag` → `dispatchStateChanged`) và `setCurrentItem(.., true)` đi qua `SETTLING`, nên được phủ; `setCurrentItem(.., false)` không đi qua trạng thái nào và cũng không cần chặn.
- [x] **Step 4: Kịch bản kiểm (Live 4, FORCE_HIGH)** — (a) khối lệnh Task 1: Janky kỳ vọng ~2%, `TL1P` draw ~1 ms/khung, chuỗi `captureBackdrop` biến mất khỏi simpleperf; (b) kính dock vẫn đúng sau mỗi lần vuốt (không bị đen/trắng/mất nền); (c) đổi hình nền trong Cài đặt rồi về Home: kính dock cập nhật theo (đường `pulseGlassRefresh`); (d) vào edit mode, kéo icon chạm mép (kéo giả) rồi thả: dock vẫn đúng; (e) xoay / tách đôi màn hình: kính dock vẽ lại đúng.
**Rủi ro đã biết:** mỗi lần vuốt sẽ có một `invalidate()` bù khi về IDLE (vì listener luôn bị bỏ ít nhất một lần), tức một lần chụp lại nền (~14 ms) ngay sau khi dừng: một khung, lúc không còn gì chuyển động. Nếu thấy khựng ở cuối, thử trì hoãn bù bằng `post`.

### Task 3: `PagerFlipAnimator` cho lật có chủ đích — ĐÃ QUA REVIEW (2026-11-03; kiểm máy gộp vào phiên Task 6)
**Bắt buộc kiểm tay:** không.
**Files:** tạo `ui/home/PagerFlipAnimator.kt`; sửa `HomeActivity.onDragEdge` và nhánh lật trang tạm trong `render()`.
**Interfaces:** Produces `PagerFlipAnimator(pager)`: `flipTo(target: Int)`, `cancel()`. Consumes `PageMotion`, `ViewPager2.beginFakeDrag/fakeDragBy/endFakeDrag/isFakeDragging`.
- [x] **Step 1:** `flipTo(target)` chỉ chạy khi `target` kề `currentItem`; đang lật thì bỏ lệnh; `!pager.beginFakeDrag()` ⇒ `pager.setCurrentItem(target, true)`. `ValueAnimator.ofFloat(0f, 1f)` với `duration = PageMotion.durationMs(1f)` và `interpolator = PageMotion.CURVE`; mỗi khung `fakeDragBy(-(fraction − prev) × pager.width × dir)` (dấu đúng LTR, kiểm trên máy); kết thúc/hủy gọi `endFakeDrag()` nếu còn `isFakeDragging`.
- [x] **Step 2:** Thay hai chỗ `setCurrentItem(.., true)` (`onDragEdge` hai nhánh; `render()` lật sang trang tạm) bằng `flipTo`; trước `setCurrentItem(.., false)` ở `render()` gọi `cancel()`; `cancel()` trong `onStop`/`onDestroy` đúng chỗ đã có.
- Ghi chú triển khai: (i) thêm `onStop` gọi `cancel()` (chưa có sẵn); (ii) `render()` bỏ nhánh `setCurrentItem(.., false)` khi `pagerFlip.isFlipping` — nếu không, mọi lần `render` giữa lúc lật (đặt lại về `currentItem` cũ) sẽ hủy lượt lật; (iii) `flipTo` dùng `PageMotion.FULL_MS` + `PageMotion.CURVE`.
- [x] **Step 3:** Build xanh, commit `feat: ease edge-flip and new-page flips with PagerFlipAnimator`, cập nhật Nhật ký.
- [x] **Step 4: Kịch bản kiểm (Live 4, dời sang phiên kiểm gộp ở Task 6)** — (a) kéo icon sát mép phải: lật ~450 ms, giống cảm giác vuốt tay, thả đúng ô; (b) giữ mép lâu (lặp 500 ms): không kẹt/chồng; (c) kéo ra ngoài trang cuối: trang trống → lật vào; kéo ngược: trang tạm biến mất; (d) mép trái ở trang 0: không làm gì; (e) pill/chấm trang đúng sau khi lật; (f) vuốt tay sau đó bình thường.

### Task 4: Giảm biên độ rung — ĐÃ QUA REVIEW (2026-11-03; kiểm máy gộp ở phiên Task 6)
**Bắt buộc kiểm tay:** không. (Điều phối đã duyệt nội dung.)
**Files:** `ui/home/view/Jiggler.kt`.
- [x] **Step 1:** `JIGGLE_DEGREES` `5f` → `2f`; sửa KDoc. Giữ `JIGGLE_DURATION_MS`, `LinearInterpolator`.
- [x] **Step 2:** Build xanh, commit `fix: reduce edit-mode jiggle amplitude to 2 degrees`, cập nhật Nhật ký.
- [x] **Step 3: Kịch bản kiểm (Live 4, dời sang phiên kiểm gộp ở Task 6)** — vào edit mode: icon lưới, dock, thư mục rung nhẹ, không đồng bộ; dấu "−" xoay cùng ô; nhãn đọc được; thoát: icon về thẳng góc 0. Muốn khác thì báo số trong 1,5–2,5.

### Task 5: Bỏ đẩy lưới, pill nổi (nhìn 28dp, chạm 48dp), khoảng đệm cố định — ĐÃ QUA REVIEW CODE (2026-11-03; kiểm máy ở phiên Task 6)
**Bắt buộc kiểm tay:** không.
**Files:** `HomeActivity.kt` (xóa `editSpaceApplied`, `editToolbarSpacePx`, `applyEditToolbarSpace` và lời gọi; sửa `applySystemBarInsets`), `activity_home.xml` (khối `edit_toolbar`), `dimens.xml`.
**Interfaces:** Consumes `bars.top`; Produces dimens `home_edit_pill_height` (28dp), `home_edit_pill_touch` (48dp), `home_edit_pill_gap` (2dp), `home_edit_badge_overhang` (16dp).
- [x] **Step 1:** `HomeActivity`: xóa ba thành phần "chừa chỗ" và lời gọi `applyEditToolbarSpace(state.editMode)`. Listener insets: `topReserve = max(0, touch + gap + overhang − GridMetrics.PADDING_DP(8dp) − bars.top)` (đọc 8dp từ đúng nguồn hoặc ghi chú rõ nếu chép số); `edit_toolbar` đặt sát mép trên màn hình (`boxTop = 0`, không cộng `bars.top`); `home_content.topMargin = contentBaseTop + bars.top + topReserve`.
- [x] **Step 2:** `activity_home.xml`: `btn_settings`/`btn_done`: `layout_height=@dimen/home_edit_pill_touch`, `minHeight=0`, `insetTop=insetBottom=10dp` (⇒ nền 28dp giữa hộp 48dp), `paddingHorizontal` ~14dp, `textSize=14sp`, `cornerRadius` = nửa chiều cao nền, "Xong" in đậm, `backgroundTint=@color/search_scrim`. Sửa comment khối (bỏ câu "home_content được đẩy xuống ...").
- [x] **Step 3:** `dimens.xml`: xóa `home_edit_toolbar_space`; thêm khối hằng pill ở cuối file; xóa `home_done_margin_top` nếu không còn ai dùng (grep trước).
- [x] **Step 4:** Soát các thay đổi layout khác chỉ có ở edit mode: đọc hôm nay thì `IconGridView.setEditMode`, `DockView.setEditMode`, `HomePagerAdapter.setEditMode` chỉ bật rung + dấu "−"; `SearchPillView` đổi sang chấm nhưng `onMeasure` cố định `touchHeight` ⇒ không dịch. Ghi kết quả vào Nhật ký; thấy chỗ khác thì báo, không tự sửa ngoài vùng.
- [x] **Step 5:** Build xanh, commit `fix: float edit-mode buttons as pills with 48dp touch targets without shifting the grid`, cập nhật Nhật ký.
- Kết quả Step 4 (đọc code): chỉ `IconGridView.setEditMode`, `DockView.setEditMode`, `HomePagerAdapter.setEditMode` (bật rung + dấu "−") và `SearchPillView` (đổi sang chấm, `onMeasure` cố định `touchHeight`) phụ thuộc edit mode; không cái nào đổi padding/margin ⇒ không còn thứ gì khác dịch nội dung. `topReserve` trên Live 4 = (48+2+16−8)dp − 32dp = **26 dp** (tính trong `editToolbarClearancePx() - bars.top`).
- Triển khai: `home_edit_pill_height` (28dp) chỉ để tài liệu hóa, nền thật 28dp = 48dp − 2×`home_edit_pill_inset` (10dp). Đã xóa `home_edit_toolbar_space` và `home_done_margin_top`; `edit_toolbar` không còn margin trên (sát mép trên màn hình).
- Lưu ý Task 8: các dòng log `TL1` và `Tl1Prof` nằm xen trong vùng đã sửa, nên **gỡ tay thay vì `git revert`** (revert `f158c84` sẽ xung đột với XML/`HomeActivity` đã sửa ở Task 5).
- [ ] **Step 6: Kịch bản kiểm (Live 4)** — (a) nhấn giữ icon vào edit mode: lưới/dock đứng yên (chụp trước/sau, chồng ảnh), icon không đổi cỡ; (b) hai pill ở vùng trên cùng, Cài đặt sát trái, Xong sát phải; bấm vào mép trên/dưới nền pill vẫn nhận; (c) không che dấu "−" ô (0,0); chạm dấu "−" ô (0,0) và ô cuối hàng đầu vẫn ẩn app; (d) thử `4×5`, `4×6`, `5×6`; (e) Xong thoát; Cài đặt mở `SettingsActivity`; (f) xoay/tách đôi màn hình; (g) ghi `topReserve` thực và so với bảng.

### Task 6: Ẩn thanh trạng thái khi edit mode (D6: đã chốt CÓ) — code xong, chờ phiên kiểm máy
**Bắt buộc kiểm tay:** có — giả định chỉ máy thật trả lời được (SystemUI/ROM có chuyển insets đúng và không nháy khi `hide`/`show` hay không; mục 4.2).
**Files:** `HomeActivity.kt` (`applySystemBarInsets`, nơi gọi theo `editMode`); `applySystemBarStyling` giữ nguyên.
- [x] **Step 1:** Trong listener insets thay `getInsets(systemBars())` bằng `getInsetsIgnoringVisibility(systemBars())` cho `home_content`, `edit_toolbar`, `libraryView.setPadding`, `statusBarTopPx`; chỉ gán `LayoutParams` khi giá trị đổi.
- [x] **Step 2:** Khi `editMode` đổi: `WindowInsetsControllerCompat(window, binding.root).hide/show(WindowInsetsCompat.Type.statusBars())`; `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Sau recreate (xoay/đổi theme) trạng thái ẩn phải khớp `editMode` của `uiState`.
- [x] **Step 3:** Build xanh, commit `feat: hide status bar while editing, margins from insets ignoring visibility`, cập nhật Nhật ký.
- Triển khai: `applyStatusBarForEditMode(editMode)` gọi từ `render()` ngay sau `editToolbar.isVisible`; `statusBarHiddenByEdit` giữ trạng thái đã áp (đúng cả sau `recreate()` vì `render()` chạy lại). Listener insets dùng `getInsetsIgnoringVisibility(systemBars())` cho `home_content`, `libraryView.setPadding`, `statusBarTopPx`; `edit_toolbar` đã sát mép trên (margin 0, Task 5) nên không phụ thuộc inset; `home_content` chỉ gán `LayoutParams` khi giá trị đổi.
- Giả định cần xác nhận trên máy: (i) `enableEdgeToEdge()` của `activity-ktx 1.13.0` đặt `layoutInDisplayCutoutMode` = `ALWAYS` (API 30) nên ẩn thanh KHÔNG làm cửa sổ bị letterbox dưới vùng cutout 96 px (nếu bị, cả layout dịch 96 px); (ii) inset nào mang 96 px — log tạm `TL1 bars.top=… visibleSystemBars.top=… statusBarsIgnoring.top=… cutout.top=… statusBarHiddenByEdit=…` in mỗi lần insets đổi, đọc ở cả hai trạng thái ẩn/hiện.
- [ ] **Step 4: Kịch bản kiểm (Live 4, phiên kiểm gộp do điều phối chạy)** — (0) `adb logcat -s TL1 -d` quanh một lần vào/ra edit mode: đối chiếu `bars.top`, `visibleSystemBars.top`, `statusBarsIgnoring.top`, `cutout.top` giữa trạng thái ẩn và hiện; (a) vào/ra edit mode 10 lần, **chụp ảnh trước/sau và so từng pixel vùng lưới + dock: không dịch dù 1 px**, không nháy; (b) vuốt từ mép trên khi đang edit: thanh hiện tạm rồi tự ẩn, pill vẫn bấm được; (c) thoát edit mode: thanh trở lại, Library/tìm kiếm đúng vị trí; (d) Home → mở app khác → quay lại giữa edit mode: trạng thái thanh đúng; (e) xoay màn hình.

### Task 7 (có điều kiện): Giật khung hình khi lướt
**Chỉ mở khi** Task 1b chỉ ra chi phí nằm ở bind/inflate trang kề (số đo Task 1 cho thấy nguyên nhân chính là ghi display list, nên hiện chưa có lý do mở). **Bắt buộc kiểm tay:** không (đo lại nằm trong bài kiểm cuối).
- [ ] **Step 1:** `binding.homePager.offscreenPageLimit = 1` trong `setupHomePager` (một dòng).
- [ ] **Step 2:** Build xanh, commit `perf: keep adjacent pages laid out while swiping`, cập nhật Nhật ký.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — lặp phép đo Janky của Task 1 (cùng khối lệnh) và kịch bản kéo-thả Task 3; kiểm ghost page và icon không nhân đôi (bài học M7).

### Task 8: Dọn log tạm, knowledgement, đề xuất tài liệu — gỡ log xong, chờ bài kiểm cuối
**Bắt buộc kiểm tay:** không.
**Files:** `HomeActivity.kt`, `activity_home.xml`/`dimens.xml` (gỡ phần tạm), `docs/knowledgements/TL1-home-polish.md` (tạo, theo mẫu mục 8 workflow; có sơ đồ Mermaid: vuốt tay → `RecyclerView` → `TunedPagerSnapHelper` → `ScrollEventAdapter` → `OnPageChangeCallback`; kéo icon chạm mép → `DragHelper` → `onDragEdge` → `PagerFlipAnimator`; inset → `topReserve`), plan (bảng Review Focus, Đề xuất cập nhật tài liệu chung).
- [x] **Step 1:** Gỡ các phần tạm bằng tay (không `git revert`): xóa `Tl1Prof.kt`, khôi phục `IconCellView`/`IconGridView`/`SearchPillView`/`StaticBlurSurface` về `main`, bỏ `dispatchDraw` tạm của `DockView`, bỏ mọi `Log.d("TL1")` trong `HomeActivity`/`TunedPagerSnapHelper`; grep `Tl1Prof`, `Trace.`, `Log.d("TL1"` trong `app/src` ra rỗng; build xanh.
- [ ] **Step 2:** Knowledgement và bảng bằng chứng đã điền (cảm giác chờ chủ dự án); còn lại: điều phối rebase `main` khi merge, chuyển PR sang Ready sau bài kiểm cuối.

### Task 9: sửa 2 lỗi Library do chủ dự án phát hiện ở bài kiểm cuối — lỗi 1 đã sửa và kiểm máy; lỗi 2 đã hoàn tác (hiểu nhầm), chuyển cho GL
**Lỗi có sẵn từ trước (pre-existing), không phải hồi quy của TL1:** điều phối tái hiện lỗi 1 y hệt trên bản build `main` (nguồn gốc G2a B2). Làm ở TL1 vì TL1 giữ `HomeActivity.kt`.
**Bắt buộc kiểm tay:** không (điều phối kiểm lỗi 1 bằng chuỗi `motionevent`; chủ dự án kiểm lỗi 2 ở bài kiểm lại).
**Ngoại lệ vùng file (điều phối cấp 2026-11-03):** được sửa `ui/home/LibraryOverlayController.kt` (không nhánh nào khác đụng).
**Files:** `ui/home/LibraryOverlayController.kt` (lỗi 1). `HomeActivity.kt` không còn thay đổi nào cho Task 9 sau khi hoàn tác lỗi 2.
- **Lỗi 1:** nhấn giữ icon ở cột phải của trang cuối rồi kéo sang trái ⇒ App Library trượt vào thay vì kéo icon. Nguyên nhân (đọc code): `LibraryOverlayController.onTouch` gọi `host.canOpen()` một lần ở `ACTION_DOWN` (lúc đó chưa có menu/phiên kéo) và giữ `candidate = OPEN`; ở `ACTION_MOVE` nó chốt `dragging = true` mà không kiểm lại, dù nhấn giữ đã làm `iconMenu`/`DragHelper.currentSession` khác rỗng.
- [x] **Step 1:** Ở nhánh chốt kéo (`ACTION_MOVE`), khi `candidate == OPEN` thì kiểm lại `host.canOpen()`; nếu false thì `cancelCandidate()` và trả `PASS` để chuỗi chạm thành kéo icon bình thường. Nhánh `CLOSE` giữ nguyên.
- **Lỗi 2 (đã hoàn tác, hiểu nhầm):** câu của chủ dự án "App lib still responsive when split screen" nghĩa là **bố cục** App Library không co giãn theo cửa sổ tách đôi như lưới chính, **không** phải "tắt cử chỉ Library trong tách đôi" (chủ dự án nói rõ không muốn tắt). Bản sửa đầu (`5e8b124`: `!isInMultiWindowMode` trong `canOpenLibrary`, điều kiện đóng trong `renderLibrary`, `onMultiWindowModeChanged`) đã bị hoàn tác. Việc làm Library responsive nằm ở `CategoryPageView` (vùng GL), **không thuộc TL1**; điều phối chuyển cho GL.
- [x] **Step 2 (hoàn tác):** bỏ `!isInMultiWindowMode` khỏi `canOpenLibrary()`, bỏ `isInMultiWindowMode` khỏi điều kiện đóng ở `renderLibrary`, bỏ override `onMultiWindowModeChanged`. Chỉ còn sửa lỗi 1.
- [x] **Step 3:** Build xanh, commit `fix: keep app library from hijacking icon drags and disable it in split-screen` (lỗi 1 + lỗi 2 cũ) rồi `revert: keep app library available in split-screen` (hoàn tác lỗi 2).
- [x] **Step 4: Kịch bản kiểm (Live 4, điều phối, `c5452de`)** — (a) trang cuối, nhấn giữ icon cột phải rồi kéo trái: thành kéo icon bình thường ở edit mode, thả về chỗ cũ, Library không trượt vào; (b) vuốt trái bình thường ở trang cuối vẫn mở Library. Tách đôi: Library vẫn mở được như cũ (đúng ý chủ dự án); bố cục responsive của Library do GL xử lý.

---

## Bài kiểm cả tính năng (cho chủ dự án; điều phối đã gộp)

**Máy:** Vsmart Live 4 (Android 11). **Cài:** nhánh `fix/tl1-home-polish` (ghi commit: `git rev-parse --short HEAD`), `.\gradlew.bat installDebug`. **Chuẩn bị:** ≥ 3 trang icon; dock có app; lưới `4×6` (mặc định); `glass_level_override` để như đang dùng (kính thật nếu đang `FORCE_HIGH`). Mục 1–3 là điều tech lead góp ý, hãy chấm các mục này trước.

**A. Điều tech lead yêu cầu**
1. **Vuốt trang:** vuốt nhanh (fling) và kéo nửa trang rồi thả chậm, qua 3–4 trang, cả hai chiều. Mong đợi: dừng chậm hơn và mượt hơn trước (khoảng 0,3–0,45 s), không khựng ở cuối, hai cách thả cùng cảm giác. *Chấm: đạt / quá nhanh / quá chậm / giật.*
2. **Rung icon:** nhấn giữ một icon để vào chỉnh sửa. Mong đợi: icon lưới, dock và thư mục rung nhẹ (±2°), lệch pha nhau, dấu "−" xoay cùng ô, nhãn đọc được. *Chấm: đạt / vẫn mạnh / quá yếu.*
3. **Nút chỉnh sửa:** vào chỉnh sửa rồi bấm Xong, lặp 3 lần. Mong đợi: thanh trạng thái biến mất khi chỉnh sửa và hiện lại khi thoát; hai viên **Cài đặt** (trái) và **Xong** (phải) nổi ở mép trên, chữ đủ dấu và không bị cắt; **lưới và dock đứng yên, không nhích**, icon không đổi cỡ. Vuốt từ mép trên: thanh hiện tạm rồi tự ẩn, hai viên vẫn bấm được. Cài đặt mở màn Cài đặt, Back về vẫn ở chỉnh sửa.

**B. Hồi quy (những chỗ dễ bị ảnh hưởng)**
4. **Kéo icon chạm mép:** vào chỉnh sửa, kéo một icon sát mép phải giữ ~3 s rồi mép trái rồi thả. Mong đợi: mỗi lần lật một trang ~0,45 s, nghỉ ~0,5 s rồi mới lật tiếp (không nối đuôi), thả đúng ô, kính dock còn nguyên. Mép trái ở trang đầu không làm gì.
5. **Trang trống tạm:** kéo icon qua mép phải ở trang cuối ⇒ sinh trang trống và lật vào; kéo ngược ⇒ trang tạm biến mất; thả icon vào trang mới được.
6. **App Library từ trang cuối:** vuốt tới trang cuối, chờ yên rồi vuốt trái ⇒ Library mở. Thử vuốt tới trang cuối rồi vuốt trái ngay lúc đang dừng: không được mở nhầm hay kẹt, lần vuốt sau mở được. **(Task 9)** Ở trang cuối, nhấn giữ icon cột phải tới khi menu hiện rồi kéo sang trái: Library **không** được trượt vào, icon kéo được như các trang khác.
7. **Thư mục:** mở thư mục (kính panel còn đúng), kéo app vào/ra thư mục, đóng; rung và dấu "−" trong thư mục bình thường.
8. **Tìm kiếm:** vuốt xuống từ Home mở tìm kiếm; bấm ô Tìm kiếm ở dưới mở được; vuốt trang thì ô đổi thành chấm trang rồi về ô sau ~0,6 s.
9. **Đổi hình nền** trong Cài đặt rồi về Home: kính dock cập nhật theo hình mới (không đen/trắng/mất nền).
10. **Đổi cỡ lưới** `4×5`, `5×6` rồi lặp mục 3 (lưới đứng yên, pill không che dấu "−" của ô đầu tiên; bấm dấu "−" ô đầu vẫn ẩn được app).
11. **Tách đôi màn hình / xoay** (nếu máy hỗ trợ) ở chỉnh sửa: lưới và pill đúng chỗ, kính dock vẽ lại đúng. (Library trong tách đôi vẫn mở được; bố cục Library co giãn theo cửa sổ là việc của nhánh GL, không thuộc bài kiểm này.)
12. **Thoát chỉnh sửa:** Back, Home, chạm vùng trống, Xong đều thoát và thanh trạng thái hiện lại.

**Các hằng chỉnh tay (nếu cảm giác chưa đúng, báo số muốn đổi):**

| Hằng | Giá trị hiện tại | File : dòng | Ảnh hưởng |
|---|---|---|---|
| `PageMotion.FULL_MS` | 450 ms | `ui/home/PageMotion.kt:15` | Thời lượng lật/settle một trang nguyên (dải thử 400–500) |
| `PageMotion.MIN_MS` | 300 ms | `ui/home/PageMotion.kt:18` | Sàn thời lượng khi chỉ còn đoạn ngắn tới đích |
| `PageMotion.CURVE` | easeOutQuint `(0.22, 1, 0.36, 1)` | `ui/home/PageMotion.kt:31` | Đường cong dừng |
| `PageMotion.EDGE_FLIP_REST_MS` | 500 ms | `ui/home/PageMotion.kt:24` | Nghỉ tối thiểu giữa hai lượt lật mép (chu kỳ thực ≈ 1 s vì `DragHelper` lặp mỗi 500 ms) |
| `Jiggler.JIGGLE_DEGREES` | 2° | `ui/home/view/Jiggler.kt:42` | Biên độ rung (dải thử 1,5–2,5) |
| `Jiggler.JIGGLE_DURATION_MS` | 160 ms (không đổi) | `ui/home/view/Jiggler.kt:41` | Nửa chu kỳ rung |
| `home_edit_pill_*` | nền 28dp, chạm 48dp, góc 14dp | `res/values/dimens.xml:74-81` | Kích thước pill; `topReserve` = 58dp − chiều cao thanh trạng thái (26dp trên Live 4) |

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** điều phối đã chạy phần đo/cơ chế trên Live 4 (2026-11-03, `2d9d895` và `bfe3d06`; xem Nhật ký). Phần cảm giác của chủ dự án (mục 1–3, 4–12): chờ bài kiểm cuối.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-03 | — | Đọc workflow/AGENTS/code, đọc bytecode `PagerSnapHelper`/`ViewPager2`/`FakeDrag`, viết plan | `docs: add TL1 plan` | Gửi điều phối |
| 2026-11-03 | — | Sửa plan sau rà: đưa vuốt tay vào Task lõi (A: `TunedPagerSnapHelper`, so với B/C/D/E; đọc bytecode `SnapHelper`/`ScrollEventAdapter`/`ViewFlinger`); pill chạm 48dp (`insetTop/Bottom`) + `topReserve` tính lại; thêm phương án ẩn thanh trạng thái (P); ngưỡng Janky 2,13% theo M6; Task 1 có khối lệnh `adb`; điền PR #2 | `docs: revise TL1 plan after review` | **Plan vòng 1/5.** Phát hiện thêm: App Library mở bằng cử chỉ trong `dispatchTouchEvent`, không phải overscroll của pager |
| 2026-11-03 | 1 | Ghi quyết định chủ dự án (D6 ẩn thanh = CÓ, D7 reflection có điều kiện), đặt Plan duyệt 2026-11-03. Code Task 1: log tạm `TL1` (trạng thái cuộn pager, `bars.top`, `iconSize`/`cellHeight`, tap Cài đặt/Xong) và thăm dò hộp chạm pill (nút cao 48dp, nền 28dp bằng `insetTop/Bottom` 10dp, toolbar sát mép trên). Build xanh | `chore: add temporary TL1 logs and toolbar probe` (+ `docs: record TL1 approval and start knowledgement`) | Task 1 chờ điều phối cài và chạy khối lệnh. Lệch plan: gộp hai commit tạm thành một (gỡ bằng `git revert` ở Task 8). Giữ nguyên lưới đẩy 64dp trong bản thăm dò (chỉ đo chạm và số đo) |
| 2026-11-03 | 1 | Điều phối đo trên Live 4 (`f158c84`); Task 1 qua review | — | Số đo ở mục Kết quả Task 1. **Kết quả đổi ưu tiên:** Janky 72,87%, `DrawStart→SyncQueued` p50 14,4 ms mỗi khung; bỏ ngưỡng 2,13%; thêm Task 1b |
| 2026-11-03 | 1b | Đọc code ứng viên, thêm công cụ đo tạm `Tl1Prof` + gói `onDraw`/`dispatchDraw` các view nghi vấn (commit riêng, revert ở Task 8). Build xanh | `chore: add temporary TL1 draw profiling` | Chờ điều phối chạy đo. Đụng tạm file vùng GL (chỉ đo, không đổi hành vi) |
| 2026-11-03 | 1b | Điều phối đo (`b78aa24`): TL1P/perfetto/simpleperf; so FORCE_HIGH và AUTO. Tôi đọc code + `javap` `liquidglass-v2.0.11`: nguyên nhân là `LiquidGlassView.backdropScrollListener` (`OnScrollChangedListener`) gọi `invalidate()` mỗi khi `RecyclerView.dispatchOnScrolled` phát `onScrollChanged`, rồi `renderGlassEffectSync` chụp lại nền mỗi lần vẽ dù `enableDynamicBackground=false` | — | **Chưa tick 1b.** Đề xuất S1 (chặn `invalidate()` lúc pager cuộn), phần lớn ở vùng GL; chờ điều phối. Chi tiết ở Step 6 |
| 2026-11-03 | 2 | `PageMotion` (easeOutQuint, 220–400 ms theo quãng còn lại) + `TunedPagerSnapHelper` (ghi đè `createScroller`, `findSnapView` null khi FakeDrag), gắn trong `setupHomePager` sau `onFlingListener = null`. Build xanh (lần đầu lỗi `IntArray?` do `calculateDistanceToFinalSnap` nullable, đã sửa) | `feat: tune swipe-release page settle with TunedPagerSnapHelper` | Chờ review. Chạy song song với Task 1b theo lệnh điều phối. Chưa kiểm máy; đường nhấc-tay-chậm giữ mặc định (D7 chỉ mở nếu lệch cảm giác) |
| 2026-11-03 | 1b | Điều phối review: **đạt**, tick | — | Cùng quyết định: TL1 tự làm S1 (Task 2b), cấp ngoại lệ vùng GL hẹp |
| 2026-11-03 | 2 | Điều phối kiểm máy (`e94ea96`, FORCE_HIGH): trạng thái `1,2,0` mỗi lần vuốt (16 lần, không snap đôi); fling SETTLING→IDLE 224–268 ms, median ~258 ms (gần như không đổi vì `adb input swipe` chỉ còn ~30% quãng: 220+180×0,3 ≈ 274 ms); nhấc chậm 298–302 ms (đường mặc định). Sửa vòng 2/5: `MIN_MS` 300, `FULL_MS` 450; log tạm `TL1 tuned settle fraction/dx/duration` trong `onTargetFound` (gỡ ở Task 8) | `feat: slow page settle floor to 300ms and log tuned scroller` | **Task 2, vòng 2/5.** Chờ kiểm lại |
| 2026-11-03 | 2b | S1: `SuppressibleGlassView` (ghi đè `invalidate()`), `GlassSurface.setInvalidationSuppressed`, `DockView.setGlassInvalidationSuppressed`, bật/tắt từ `onPageScrollStateChanged` (≠ IDLE). Build xanh | `perf: suppress dock glass invalidation while the pager scrolls` | Chờ review + đo FORCE_HIGH. Ngoại lệ sửa vùng GL (additive) |
| 2026-11-03 | 2, 2b | Điều phối review: Task 2 (vòng 2/5) và Task 2b **đạt**, tick; số đo ở mục Kết quả Task 2 vòng 2 và Task 2b | — | Janky 72,87% → 1,76%; fling 300–318 ms, nhấc chậm ~350 ms |
| 2026-11-03 | 3 | (điều phối review đạt, tick; kiểm máy gộp ở phiên Task 6; dấu `fakeDragBy` đúng theo tài liệu ViewPager2; `render()` guard + `onStop` được chấp nhận) `PagerFlipAnimator` (FakeDrag + `PageMotion`), thay hai chỗ `setCurrentItem(.., true)` (`onDragEdge`, lật sang trang tạm), `cancel()` ở `onStop`/`onDestroy`, `render()` không đặt lại trang khi đang lật. Build xanh | `feat: ease edge-flip and new-page flips with PagerFlipAnimator` | Chờ review. Dấu của `fakeDragBy` (âm = trang sau, LTR) và kiểm lật mép/ghost page trên máy: chưa làm |
| 2026-11-03 | 4 | `Jiggler.JIGGLE_DEGREES` 5f → 2f (chung `IconGridView`/`DockView`). Build xanh | `fix: reduce edit-mode jiggle amplitude to 2 degrees` | Chờ review. Kiểm máy gộp ở phiên Task 6 |
| 2026-11-03 | 4 | Điều phối review đạt, tick | — | Kiểm máy ở phiên Task 6 |
| 2026-11-03 | 5 | Xóa `editSpaceApplied`/`editToolbarSpacePx`/`applyEditToolbarSpace` và dimen `home_edit_toolbar_space`, `home_done_margin_top`; pill nhìn 28dp (inset 10dp) hộp chạm 48dp sát mép trên; `home_content.topMargin = base + bars.top + topReserve` với `topReserve = max(0, 58dp − bars.top)` (26dp trên Live 4). Build xanh | `fix: float edit-mode buttons as pills with 48dp touch targets without shifting the grid` | Chờ review. Chưa kiểm máy. Log/Tl1Prof sẽ gỡ tay ở Task 8 (không revert) |
| 2026-11-03 | 6 | Insets sang `getInsetsIgnoringVisibility(systemBars())`; `applyStatusBarForEditMode` ẩn/hiện `statusBars()` theo `editMode` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`); chỉ gán margin khi đổi. Log tạm in cả bốn loại inset. Build xanh | `feat: hide status bar while editing, margins from insets ignoring visibility` | Chờ phiên kiểm máy gộp (điều phối). Chưa kiểm: inset nào mang 96 px, cutout mode, nháy khi bật/tắt |
| 2026-11-03 | 5 | Điều phối review code đạt, tick; xác nhận gỡ log `TL1` bằng tay ở Task 8 | — | Kiểm máy ở phiên Task 6 |
| 2026-11-03 | 8 (một phần) | Viết đầy đủ knowledgement (4 luồng Mermaid, bảng file, quyết định, chỗ cần soi, bài học cho AGENTS.md); điền trước bảng Review Focus | `docs: write TL1 knowledgement and pre-fill Review Focus evidence` | Chưa gỡ log `TL1`/`Tl1Prof` vì phiên kiểm máy cần chúng |
| 2026-11-03 | 6 | **Phiên kiểm máy gộp (điều phối, Live 4, FORCE_HIGH)**, sau khi điều phối rebase lên `main` có TL2 (HEAD `68eddc2`). Đạt: log insets khi edit `bars.top=96 visibleSystemBars.top=0 statusBarsIgnoring.top=96 cutout.top=96 statusBarHiddenByEdit=true` ⇒ `systemBars()` bỏ qua visibility đúng, cutout mode không letterbox; vào edit mode không relayout (không có dòng `pager=`), ảnh chụp lưới đứng yên, **0 pixel đổi** giữa Home thường và sau Xong (dưới thanh trạng thái); icon sau TL2 (`ICON_SCALE` 0,9) `icon=48dp cellH=76dp pager=1080x1589`; Xong thoát edit; Cài đặt mở rồi Back về còn edit + thanh ẩn (không cần `onWindowFocusChanged`); vuốt từ mép trên: thanh hiện tạm đè pill, tự ẩn ~3 s; kịch bản vuốt: 2029 khung, Janky 1,97%, p50 9 / p95 13 / p99 23 ms; lật mép phải rồi trái rồi thả: mỗi lượt 446–468 ms, kính dock còn nguyên, rung 2° nhẹ | — | Hai lỗi cần sửa: (1) chữ pill bị cắt đáy; (2) lật mép nối đuôi nhau |
| 2026-11-03 | 6 (sửa lỗi 1) | Chữ pill bị cắt: `TextView` cắt chữ theo hộp trong padding nên padding dọc mặc định của `MaterialButton` (trong hộp 48dp, nền 28dp nhờ inset) lấn vào vùng chữ. Đặt `android:paddingVertical="0dp"` cho cả hai nút (giữ nền 28dp, hộp chạm 48dp) | `fix: remove vertical padding from edit pills and rest between edge flips` | Chờ kiểm lại dấu tiếng Việt (ả, đ) và đáy chữ (g) không bị cắt |
| 2026-11-03 | 3 (sửa lỗi 2) | Lật mép nối đuôi (lượt 450 ms, `DragHelper` lặp 500 ms ⇒ nghỉ ~50 ms): `onDragEdge` bỏ lệnh khi `pagerFlip.isFlipping` hoặc chưa đủ `PageMotion.EDGE_FLIP_REST_MS` (500 ms) kể từ lần pager về IDLE gần nhất (`lastPagerIdleMs` ghi ở `onPageScrollStateChanged`). Không cần sửa `DragHelper`. Do nhịp lặp 500 ms của `DragHelper`, nghỉ thực tế ≈ 550 ms và chu kỳ lật ≈ 1000 ms khi giữ mép | cùng commit trên | Chờ kiểm lại; nếu muốn nhịp dày hơn thì đổi `EDGE_FLIP_REST_MS` (cần sửa `EDGE_HOLD_MS` của `DragHelper` để đi dưới 1000 ms/chu kỳ) |
| 2026-11-03 | 8 | Điều phối kiểm lại hai sửa lỗi trên Live 4 (`bfe3d06`): chữ pill đủ dấu; lật mép chu kỳ ~1 s, không chồng. Gỡ tay mọi công cụ đo tạm (`Tl1Prof`, `Trace`, log `TL1`, các hàm bọc `onDraw`/`dispatchDraw`), khôi phục 4 file của GL/TL2 về `main` (không còn diff ngoài ngoại lệ 2b); điền bảng Review Focus; viết lại "Bài kiểm cả tính năng" (12 mục + bảng hằng chỉnh tay) | `chore: remove TL1 diagnostics` | Build xanh. Chưa rebase/Ready (điều phối làm sau bài kiểm cuối) |
| 2026-11-03 | 9 | Bài kiểm cuối của chủ dự án thấy 2 lỗi Library **có sẵn từ trước** (điều phối tái hiện lỗi 1 trên `main`): (1) nhấn giữ icon cột phải trang cuối rồi kéo trái ⇒ Library trượt vào; (2) Library còn hoạt động khi tách đôi màn hình. Sửa: `LibraryOverlayController` kiểm lại `host.canOpen()` khi chốt kéo (nhánh `OPEN`); `canOpenLibrary()` + `renderLibrary` + `onMultiWindowModeChanged` cho nhiều cửa sổ. Build xanh | `fix: keep app library from hijacking icon drags and disable it in split-screen` | Ngoại lệ sửa `LibraryOverlayController.kt` do điều phối cấp. Chờ kiểm máy |
| 2026-11-03 | 9 | Điều phối kiểm máy lỗi 1 (`c5452de`): đạt (kéo icon bình thường, vuốt trái thường vẫn mở Library). **Lỗi 2 hiểu nhầm:** chủ dự án muốn bố cục Library co giãn theo tách đôi, không muốn tắt Library. Hoàn tác phần lỗi 2 (bỏ `!isInMultiWindowMode`, điều kiện đóng ở `renderLibrary`, `onMultiWindowModeChanged`); giữ sửa lỗi 1. Library responsive thuộc `CategoryPageView` (vùng GL), điều phối chuyển cho GL. Build xanh | `revert: keep app library available in split-screen` | Không còn thay đổi nào ngoài lỗi 1 trong Task 9 |

## Bằng chứng Review Focus (điền khi đóng)

Nguồn "đo" = điều phối chạy trên Vsmart Live 4 ngày 2026-11-03 (build `f158c84`, `b78aa24`, `e94ea96`, `2d9d895`, `bfe3d06`; các bản sau rebase lên `main` có TL2). Cảm giác (mượt, nhẹ, đúng ý) là phần chủ dự án chấm ở bài kiểm cuối.

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Không còn cộng top margin theo `editMode` | grep `editToolbarSpace`, `editSpaceApplied`, `home_edit_toolbar_space`, `home_done_margin_top`, `applyEditToolbarSpace`, `homeContentTopBase` trong `app/src` ra rỗng | Đạt (code) |
| 2 | `Cells` không đổi khi vào/ra edit mode; margin không đổi khi ẩn thanh | Log insets khi edit: `bars.top=96 visibleSystemBars.top=0 statusBarsIgnoring.top=96 cutout.top=96 statusBarHiddenByEdit=true`; không có dòng `pager=` khi vào edit; **0 pixel đổi** giữa Home thường và sau Xong; icon sau TL2 `icon=48dp cellH=76dp pager=1080x1589` | Đạt (đo) |
| 3 | `TunedPagerSnapHelper` gắn đúng, không reflection, không kẹt `SETTLING` | Code: `onFlingListener = null` trước attach, không reflection. Đo: 30 lần log `tuned settle` cho 30 lần vuốt, chuỗi trạng thái `1,2,0` mỗi lần (16+ lần, không snap đôi); fling SETTLING→IDLE 300–318 ms (tính 353–365 ms), nhấc chậm 350–353 ms | Đạt (đo); cảm giác: chờ chủ dự án |
| 4 | `PagerFlipAnimator` hủy/dự phòng/chồng lệnh đúng | Code: `isFlipping` chặn chồng; `onStop`/`onDestroy` hủy; `render()` bỏ đặt lại trang khi đang lật. Đo: lật mép phải rồi trái rồi thả, mỗi lượt DRAGGING→IDLE 446–468 ms, không chồng, layout nguyên vẹn sau khi thả; giữ 2,6 s mỗi mép: nghỉ IDLE→lượt kế 545/570/531/543 ms (chu kỳ ~1 s) | Đạt (đo); chiều lật đúng |
| 5 | `onPageSelected`, ghost page, `DragHelper.refresh()`, `canOpenLibrary()` | `onPageSelected`/trạng thái đúng (đo, chuỗi `1,2,0`). Ghost page và Library ở trang cuối khi settle dài hơn: nằm trong bài kiểm cuối mục 5–6 | Chờ chủ dự án (mục 5, 6) |
| 6 | Pill 28dp nhìn / 48dp chạm, không che dấu "−" ô (0,0) ở 3 cỡ lưới | Tính từ code: `topReserve` 26dp trên Live 4 (`bars.top` = 32dp). Đo: Xong thoát edit, Cài đặt mở và Back về còn edit + thanh ẩn; chữ "Cài đặt" (ả, đ) và "Xong" (g) không bị cắt sau khi bỏ padding dọc. Ba cỡ lưới và chạm dấu "−" ô đầu: bài kiểm cuối mục 10 | Một phần (đo); còn mục 10 |
| 7 | Không còn log `TL1`/`Tl1Prof` | grep `TL1`-log, `Tl1Prof`, `Trace.` trong `app/src` ra rỗng (chỉ còn chữ "TL1" trong chú thích mô tả code thật) | Đạt (code) |
| 8 | Task 2b: `invalidate()` bù lúc IDLE; kính dock đúng | Đo Live 4 FORCE_HIGH: Janky 72,87% → 1,76% (`2d9d895`), p50 19 → 6 ms; sau rebase TL2 + các sửa: 2029 khung, Janky 1,97%, p50 9 / p95 13 / p99 23 ms; ảnh dock sau vuốt và sau lật mép còn kính. Đổi hình nền, tách đôi: bài kiểm cuối mục 9, 11 | Đạt (đo) trừ mục 9, 11 |
| 9 | Task 3: `render()` không đặt lại trang khi `isFlipping` | Đọc diff; lật mép không bị hủy giữa chừng trong đo | Đạt |
| 11 | Task 9 lỗi 1: Library không cướp chuỗi chạm đã thành nhấn giữ | Code: `canOpen()` kiểm lại ở nhánh chốt kéo (`OPEN`) của `LibraryOverlayController`. Lỗi tái hiện trên `main` (pre-existing). Đo (điều phối, `c5452de`): nhấn giữ icon cột phải trang cuối rồi kéo trái ⇒ kéo icon bình thường; vuốt trái thường vẫn mở Library | Đạt (đo) |
| 10 | Rung 2° | Một hằng số; ảnh chụp edit mode: rung nhẹ | Cảm giác: chờ chủ dự án (mục 2) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: không có TT# mới; ghi góp ý tech lead 2026-11-03 đã xử lý.
- `project_structure.md`: thêm `ui/home/PageMotion.kt`, `TunedPagerSnapHelper.kt`, `PagerFlipAnimator.kt`; sửa mô tả thanh edit mode (pill nổi, không đẩy lưới).
- `room-schema.md`: không đổi.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement (dự kiến: ba đường chuyển trang của ViewPager2 và thời lượng của từng đường; helper cũ "xác sống" khi thay `PagerSnapHelper`; `ScrollEventAdapter` chỉ phát `onPageSelected` cho cuộn bắt đầu từ kéo hoặc FakeDrag; sửa dòng "Demo polish" về `home_edit_toolbar_space`; hộp chạm 48dp vs dấu "−").
