# FX7 — Cử chỉ Home: pager, menu icon, kéo-thả, chuyển Home → Library (B6, C12, C14, C18, D2) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ điều phối kiểm (chủ dự án giao điều phối chạy kiểm bằng adb, 2026-11-11); cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng trên Vsmart Live 4. Lệnh viết cho CMD/PowerShell (phần chạy trên máy là `sh`).

**Nhánh:** `fix/fx7-home-gestures` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX7-home-gestures` · **PR:** #38 (draft) · **Bắt đầu:** 2026-11-11 (đợt 2, trên `main` 2bac922) · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án; Q1 giữ mặc định). **Sửa plan vòng 1/5 (2026-11-11):** thêm D2, chờ điều phối rà
**Trạng thái:** Đang làm Task 1 (B6); D2 (Task 5-8) chờ điều phối duyệt bản sửa plan
**Khóa:** không (không đổi schema Room; giữ độc quyền `core/ui/DragHelper.kt` và `ui/home/HomeViewModel.kt` trong đợt 2)

**Goal:** (1) B6: pager không bao giờ bị `setCurrentItem` khi đang kéo hoặc đang trượt; trang đang xem được khôi phục theo `pageId`, và chỉ khi pager đã rảnh. (2) C14: mở menu nhấn giữ lúc ảnh hạ cánh còn bay không để ô vừa thả kẹt alpha 0. (3) C12: kết quả shortcut tải chậm của app A không hiện trong menu của app B cùng thư mục. (4) C18: một phiên kéo chỉ theo đúng ngón đã bắt đầu kéo; ngón thứ hai bị bỏ qua; nhấc ngón kéo thì thả tại chỗ ngón đó. (5) **D2 (chủ dự án, 2026-11-11):** vuốt nhanh từ trang Home cuối sang App Library mượt như vuốt chậm: Home và Library đi cùng nhau theo một tiến độ, vận tốc thả tay được mang vào hoạt ảnh kết thúc, cử chỉ không bị mất khi pager còn đang trượt, và việc nặng ở luồng chính (bố cục Library lần đầu hiện) không rơi vào đúng khung chuyển tay.

**Architecture:** Bốn sửa nhỏ, độc lập, mỗi sửa một file chính, không đổi chữ ký công khai nào và không thêm trường vào `HomeUiState`. `HomePagerController` giữ `selectedPageId` (cập nhật ở `onPageSelected`) và một hàm `settlePageSelection()` là nơi duy nhất gọi `setCurrentItem` cho việc "đặt lại trang sau khi danh sách đổi" và "cuộn tới trang vừa thêm widget"; hàm chỉ chạy khi `scrollState == IDLE`, nếu không thì đặt cờ `restorePending` và chạy lại ở lần `IDLE` kế. `DragLayer.cancelSettle()` gọi nốt callback hoàn tất đã lưu (hợp đồng "hạ cánh bị hủy thì vẫn báo xong" nằm ở lớp sở hữu spring, không ở từng nơi gọi). `HomeViewModel.onIconLongPressed` so thêm `container` và `key` khi áp kết quả shortcut. `HomeTouchRouter` nhớ `pointerId` của ngón bắt đầu kéo và đọc toạ độ theo đúng chỉ số của ngón đó. **D2** giữ kiến trúc lớp phủ của G2a D17 (Library không phải trang của pager) và chỉ sửa chỗ chuyển tay: `LibraryOverlayController` (nhận cử chỉ, kể cả khi pager còn `SETTLING` về trang cuối; kết thúc bằng spring có vận tốc ban đầu từ `VelocityTracker` thay cho `ValueAnimator` thời lượng cố định), `AppLibraryController` (áp Home và Library từ cùng một `p`, đã đúng; thêm làm nóng bố cục Library một lần lúc rảnh để lần hiện đầu tiên không phải đo/dựng ô), `HomePagerController` (chỉ cung cấp trạng thái pager cho cổng `canOpen`).

**Tech Stack:** không thêm thư viện. API dùng: `ViewPager2.getScrollState()` (có từ 1.0.0; dự án 1.1.0), `MotionEvent.getPointerId/findPointerIndex/getActionIndex`, `MotionEvent.getRawX(int)` (API 29; minSdk 26 nên có nhánh dự phòng `getX(i) + (rawX - getX(0))`, kiểm `SDK_INT`).

**Spec:** Audit `docs/report/Audit-Report-2026-11-10.md` mục B6, C12, C14, C18 + "Quyết định của chủ dự án (2026-11-11)" và "Kế hoạch sửa" (FX7: đợt 2, sau FX1 và FX2); `docs/lessons/home-pager-animation.md` (TL1: ba đường đổi trang của ViewPager2, FakeDrag; HB; AN), `docs/lessons/drag-and-layout.md` (M7: hai bẫy chạm, trạng thái kéo-thả là dữ liệu, `requestDisallowInterceptTouchEvent`; CL; GP); plan M7 Task 7 và 10 (phiên kéo do `Activity.dispatchTouchEvent` tiếp quản; menu nhấn giữ), AN Task 1 và 9 (`SpringProgress` giữ vận tốc; hạ cánh, `settlingItemId`), AP (`docs/knowledgements/AP-applib-animations.md`, `docs/plans/AP-applib-animations.md`: Library là lớp phủ ngoài pager, một `progress` điều khiển trượt, `AppLibraryMotion`), G2a D14/D17, TL1 (ba đường đổi trang của pager, kính dock chặn khi cuộn), GF (kính Library), SM (menu icon), PL (chấm trang), GP (ngón thứ hai hủy nhấn giữ khoảng trống); audit mục D2 ("Phát hiện sau đợt 1"); hàng MV ở `MultiAgent-Workflow.md` mục 10.

## Đã đối chiếu với code hiện tại (`b1af810`, chưa đổi; số dòng đã đọc lại trên `main`)

| Mục | Điều audit nói | Kết quả đọc code |
|---|---|---|
| B6 | `renderPages` đọc `savedPage` trước `submitList`, callback gọi `setCurrentItem(savedPage, false)` | Đúng: `HomePagerController.kt:169` (`val savedPage = binding.homePager.currentItem`), `:177-195` (callback). Nhánh `!pagerFlip.isFlipping` (`:187`) chỉ chặn lượt lật do chính app gây ra, không chặn vuốt tay hay trượt về |
| B6 | ViewPager2 cắt ngang khi không rảnh | **Đo bytecode `viewpager2-1.1.0-runtime.jar`:** `setCurrentItemInternal` thoát sớm chỉ khi `item == mCurrentItem && isIdle()` hoặc `item == mCurrentItem && smoothScroll`; ca `item == mCurrentItem`, đang kéo, `smoothScroll=false` đi tiếp tới `RecyclerView.scrollToPosition(item)`. Khớp mô tả "giật về 0 dưới ngón tay" của audit |
| B6 | Kích hoạt khi danh sách app đổi | **Rộng hơn audit nói (đọc nguồn AndroidX, chưa kiểm trên máy):** `AsyncListDiffer.submitList` gọi `commitCallback` **ngay** khi danh sách mới `==` danh sách cũ, nên **mọi** lần `uiState` đổi giữa lúc vuốt (không cần danh sách trang đổi) đều chạy `setCurrentItem(currentItem, false)` lúc không rảnh. `HomeActivity.render` gọi `renderPages` ở mỗi lần phát (`HomeActivity.kt:225`) |
| B6 | `getItemId = pageId`, `Diff.areItemsTheSame` theo `index` | Đúng: `HomePagerAdapter.kt:140`, `:184`. Hai quy ước định danh khác nhau cùng tồn tại (id ổn định cho RecyclerView, `index` cho DiffUtil); vì vậy khôi phục theo `pageId` phải tự tìm lại chỉ số, không dựa vào vị trí |
| C12 | `itemId = -1` cho mọi app trong thư mục; kết quả shortcut so `menu.itemId == itemId` | Đúng: `FolderPanelView.kt:165-166`; `HomeViewModel.kt` `onIconLongPressed` (đo lại trên `main` 2bac922: dòng so `menu.itemId == itemId` ở `:344`; số `:305-311` của audit đã lệch ~+35 dòng vì FX1). `ComponentKey` là data class (`domain/model/AppItem.kt:4`) nên so bằng `==` được. Trong thư mục không có hai ô cùng `ComponentKey` |
| C14 | `startDrag` hủy hạ cánh và bỏ callback | Đúng: `IconMenuController.kt:174` gọi thẳng `binding.dragLayer.startDrag(...)` (không qua `DragHelper.beginDrag`, nơi có `finishSettle()` ở `DragHelper.kt:159`); `DragLayer.kt:100-107` `cancelSettle()` đặt `settleOnEnd = null` (dòng 104) với chú thích "DragHelper tự dọn", nhưng đường menu không qua `DragHelper`. `settlingItemId` (`DragHelper.kt:225`) chỉ được xóa ở `finishSettle` (`:242-247`) |
| C14 | Ô kẹt alpha 0 tới phiên kéo sau | Khớp: `IconGridView.kt:171`, `:217`, `:1169`, `:1221`, `:1239` và `DockView.kt:206`, `:493` đặt alpha 0 khi `itemId == DragHelper.settlingItemId`; `onDragSettled` (`IconGridView.kt:151`) là chỗ duy nhất trả alpha theo dữ liệu |
| C18 | Sự kiện ngón khác bị bỏ qua, nhấc ngón đầu thì icon nhảy | **Cơ chế chính xác:** `HomeTouchRouter.dispatch` đọc `ev.rawX/rawY` (`:82`, `:84`, `:70`, `:91`) = ngón ở **chỉ số 0**. `ACTION_POINTER_DOWN/UP` rơi vào `when` không nhánh nào (không làm gì) nên ngón thứ hai bị bỏ qua đúng; nhưng khi ngón đầu nhấc (`ACTION_POINTER_UP`, chỉ số 0), ngón còn lại thành chỉ số 0 và các `ACTION_MOVE` kế tiếp kéo icon sang ngón đó; `ACTION_UP` cuối thả tại ngón đó |
| C18 | Menu → kéo (`pendingMenuDrag`) | `IconMenuController.onTouchPrelude` dùng `ev.rawX` ở `ACTION_MOVE` nhưng nhánh `else` (`ACTION_POINTER_DOWN/UP`, `ACTION_UP`, `ACTION_CANCEL`, `ACTION_DOWN`) xóa `pendingMenuDrag`, nên ngón thứ hai chạm xuống thì chỉ còn edit mode, không kéo luôn. Không cần sửa |
| C18 | Kéo bắt đầu từ bảng chọn widget | `PickerSheet.dispatchTouchEvent` chuyển nguyên `MotionEvent` của cửa sổ dialog tới `Activity.dispatchTouchEvent` (`WidgetPickerController.kt:316-317`), tức cũng đi qua `HomeTouchRouter`. `rawX/rawY` là toạ độ màn hình (không phụ thuộc cửa sổ) nên cùng quy tắc |
| D2 | Home và Library không đi cùng nhau | **Theo thiết kế thì cùng nhau:** `AppLibraryController.applyLibraryProgress` đặt `libraryView.translationX = W*(1-p)` và `homeContent.translationX = -W*HOME_SLIDE_FRACTION*p` (`HOME_SLIDE_FRACTION = 1`) từ **cùng một `p`** (`AppLibraryController.kt:~155-168`). Vậy phần Home "dịch ~100 px rồi đứng" của số đo không thể do hai công thức; chỉ có thể là `p` nhảy (H2), một chuyển động riêng của pager nằm trong `homeContent` (H1), hoặc khung bị nuốt (H4) |
| D2 | Cổng nhận cử chỉ | `LibraryOverlayController.onTouch`: `ACTION_DOWN` chỉ đặt `candidate = OPEN` khi `host.canOpen()` (`:~72-78`), và `ACTION_MOVE` hủy ứng viên nếu `canOpen()` đổi sang false (`:~108-112`). `canOpenLibrary()` đòi `isPagerSettledOnLastPage()` = `!pagerScrolling && currentItem == last` (`AppLibraryController.kt:~146`, `HomePagerController.kt:53`). **Chạm tới lúc pager còn `SETTLING` về trang cuối (fling liên tiếp) thì Library không nhận**; cử chỉ rơi xuống pager (RecyclerView chặn ngay khi `SETTLING`: đọc nguồn `onInterceptTouchEvent`, chưa kiểm trên máy) |
| D2 | Hoạt ảnh kết thúc | `animateTo` dùng `ValueAnimator`: thời lượng `260 ms × quãng còn lại` (tối thiểu 90 ms), `DecelerateInterpolator`; vận tốc `VelocityTracker` chỉ dùng để chọn mở/đóng (`vx <= -600 dp/s`), **không** vào hoạt ảnh (`LibraryOverlayController.kt:~150-190`). Tốc độ đầu của `DecelerateInterpolator` = 2 × quãng / thời lượng, thường không khớp tốc độ ngón tay |
| D2 | Lần hiện đầu của Library | `library_view` là `visibility="gone"` trong `activity_home.xml:~68`; `applyLibraryProgress` đặt `isVisible = p > 0f` (`:161`) nên **lần chạm tay đầu tiên** là lần đo/bố cục đầu tiên của cả cây Library; `CategoryPageView.render` chưa biết cỡ thì chờ `onSizeChanged` dựng lại ô (kể cả kính tại chỗ từng ô, `GlassBudget`). Đọc code; chưa đo (H4) |
| D2 | Khung dài ở mỗi lần pager dừng | `HomePagerController.onPageScrollStateChanged(IDLE)` bỏ chặn kính dock (`setGlassInvalidationSuppressed(false)`, `DockView.kt:148`) và kính thật tự chụp nền trên CPU mỗi lần vẽ (TL1). Ứng viên cho khung 69-89 ms ở mỗi lần dừng (suy luận, H5); nếu đúng thì phần sửa thuộc `core/glass` (FX8), không phải FX7 |
| Lệch số dòng | Audit ghi `IconMenuController.kt:171-179` | Thực tế `startDrag` ở `:174`, đoạn `menu.shortcuts ?: return` ở `:177-179`; khoảng 171-179 vẫn đúng |

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- B6 là lỗi **đo được** (audit đợt 3, Live 4): trang theo ngón 0 → 400 px, khung 70 nhảy về 0 khi ngón còn đang kéo. Nguyên nhân kép: gọi `setCurrentItem` lúc không rảnh (cắt ngang bằng `scrollToPosition`) và đọc `savedPage` trước khi diff nền chạy xong (có thể đưa người dùng về trang vừa rời). Trigger rộng hơn "danh sách app đổi": mọi lần `uiState` phát (bảng trên). FX3 giảm số lần phát (một lần mỗi lần ghi) nhưng không sửa chỗ gọi sai.
- Ràng buộc: chữ ký `HomePagerController` không đổi; pager có ba đường đổi trang (kéo, trượt về, FakeDrag của `PagerFlipAnimator`) và `onPageSelected` chỉ phát cho đường kéo, `setCurrentItem` và FakeDrag (`lessons/home-pager-animation.md` TL1); `PageId.NEW`/id tạm âm đổi thành id thật một lần sau khi thả lên trang mới (`lessons/drag-and-layout.md` M7 "điểm mở"), và layout nháp có `pageId = 0` (`HomePage.kt`), nên `pageId` không phải khóa duy nhất tuyệt đối.
- **D2 (chủ dự án, 2026-11-11):** "logic ở đâu đó xử lý chưa tốt chuyển pager → App Library khi vuốt nhanh; vuốt thường và chậm không sao; cảm nhận rõ khi vuốt". **Cảnh báo về số đo:** các số dưới đây (và trong audit D2) do điều phối quay trên một **bản cũ ngày 2026-11-10 (trước FX1-FX6) cài nhầm**, không phải `main` hiện tại. Mã AP đã có trong bản đó và chủ dự án cũng cảm nhận được lỗi trên đó nên D2 nhiều khả năng còn, nhưng **mọi số "trước" phải đo lại trên `main` thật (`main-723affd.apk`)**; các số bên dưới chỉ dùng để gợi ý hướng. Số đo cũ (Live 4, debug, bản cũ, flick 30 ms từ trang 1): Home dịch ~100 px (một lần đứng 2 khung), Library nhảy ~360 px trong một khung rồi đáp trong ~2 khung; mỗi lần pager dừng có một khung dài 69-89 ms; quanh chỗ chuyển tay có chuỗi 147 + 79 + 38 ms. **Chênh lệch giữa vuốt nhanh và chậm nằm ở ba chỗ:** (a) ai đang giữ cử chỉ khi chạm tới lúc pager còn trượt; (b) hoạt ảnh kết thúc chỉ biết quãng, không biết vận tốc; (c) việc nặng ở luồng chính lần đầu Library hiện. Phần kính Library nhạt rồi đầy, và vẽ lại ~1,3 s sau khi đáp, là của **FX8** (`core/glass`), không sửa ở đây.
- C18/C14/C12 là lỗi hiếm (audit: đọc code); không có đường tái hiện tự nhiên bằng `input`; cần hook tạm hoặc `sendevent` (bảng "Bài kiểm").
- Bẫy chạm của AGENTS/`lessons` phải còn nguyên: không đổi `requestDisallowInterceptTouchEvent`, không đặt trạng thái kéo-thả vào view của `RecyclerView`/`ViewPager2`, phiên kéo vẫn do `Activity.dispatchTouchEvent` tiếp quản.

**Các phương án đã xét cho B6.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ bỏ qua khi không rảnh | `if (scrollState != IDLE) return` trong callback; giữ `savedPage` theo chỉ số (đúng nghĩa đen "Hướng sửa" của audit) | Nhỏ nhất (3 dòng); hết giật | Nếu danh sách trang đổi giữa lúc kéo (trang bị xóa/chèn trước trang hiện tại) thì không bao giờ khôi phục lại; chỉ số `savedPage` vẫn có thể sai vì đọc trước diff | Không đủ một mình |
| B. Hoãn tới `IDLE` + khôi phục theo `pageId` (chọn) | `selectedPageId` cập nhật ở `onPageSelected`; callback gọi `settlePageSelection()`: không rảnh thì đặt `restorePending`, lần `IDLE` kế chạy; khi chạy tìm lại trang theo `pageId` (duy nhất trong danh sách), không thấy thì kẹp `currentItem`; chỉ `setCurrentItem` khi chỉ số đích khác `currentItem` | Hết giật; giữ đúng trang người dùng khi danh sách đổi; không còn `savedPage` đọc sớm; cùng hàm cho "cuộn tới trang widget mới" (G2c), nên không còn đường `setCurrentItem` nào chạy lúc không rảnh | Thêm hai trường và một hàm trong controller (~35 dòng); nếu pager kẹt ở `DRAGGING` (FakeDrag không kết thúc) thì khôi phục hoãn mãi (không tệ hơn hiện tại) | **Chọn** |
| C. Hoãn cả `submitList` tới `IDLE` | Gom state, chỉ áp danh sách khi rảnh | Không bao giờ có diff giữa lúc kéo | Danh sách dưới ngón tay cũ trong suốt lúc kéo (có thể giây); `draggingItemId`, trang tạm (ghost) và alpha ô trễ so với phần còn lại của `render()` (dock, pill, panel dùng state mới): lệch nhau, đúng loại lỗi "nửa cũ nửa mới" của B5 | Không |
| D. Bỏ hẳn bước khôi phục, tin `setHasStableIds(true)` | Xóa `setCurrentItem` trong callback | Ít code nhất | `lessons/drag-and-layout.md` (M3 Task 3) ghi ViewPager2 từng tự nhảy về trang 0 khi danh sách đổi; chưa kiểm lại sau khi có id ổn định và `itemAnimator = null` (M7), và `Diff` vẫn so theo `index`. Bỏ mà sai là lỗi nặng hơn B6 | Không (xét lại nếu số đo cho thấy ViewPager2 tự giữ đúng trang) |

**Các phương án đã xét cho C18.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Theo `pointerId` của ngón bắt đầu; nhấc ngón đó = thả tại đó (chọn) | `HomeTouchRouter` lấy `dragPointerId = ev.getPointerId(0)` ở sự kiện đầu tiên thấy phiên; `MOVE` đọc toạ độ ở `findPointerIndex(dragPointerId)`; `POINTER_DOWN` bỏ qua; `POINTER_UP` của đúng ngón đó = `dropAt` tại vị trí ngón đó rồi nuốt phần còn lại của cử chỉ tới `UP`/`CANCEL` | Đúng nghĩa "chỉ theo ngón bắt đầu"; chỉ sửa một file, không chạm `DragHelper`; `POINTER_UP` của ngón khác bị bỏ qua nên chạm nhầm bằng ngón thứ hai không làm mất phiên | Nhấc ngón kéo mà ngón kia còn chạm thì thả luôn (hành vi chủ động, Q1); cần nuốt phần còn lại để cây view không nhận `MOVE` không có `DOWN` | **Chọn** |
| B. Chuyển giao sang ngón còn lại (như `ScrollView`/`RecyclerView`: active pointer) | Nhấc ngón kéo thì đổi `dragPointerId` sang ngón còn lại, nhớ độ lệch để icon không nhảy | "Tiếp tục" mượt, không thả bất ngờ | Thêm trạng thái độ lệch; kéo icon bằng ngón khác với ngón đã arm/giữ là hành vi lạ (iOS thả khi nhấc ngón kéo: suy luận, chưa kiểm); lỗi chuyển giao khó thấy khi review | Không (xét lại nếu chủ dự án muốn, Q1) |
| C. Hủy phiên khi ngón thứ hai chạm xuống (như `GapLongPressGesture` D3, `SearchSwipeDetector`, Library) | `POINTER_DOWN` → `drop(null)` + `onFinished(false)` | Rất đơn giản, có tiền lệ trong dự án | Bàn tay/cạnh lòng bàn tay chạm vào màn lúc kéo là hủy phiên (ảnh bay về chỗ cũ): kéo dài đường dài (nhiều trang) rất dễ mất công; trái nội dung audit ("bỏ qua ngón thứ hai") | Không |
| D. Đưa `pointerId` vào `DragHelper.updatePosition/dropAt` | `DragHelper` nhận `MotionEvent` | Một nơi biết hết | `DragHelper` (`core/ui`) hiện chỉ nhận toạ độ, không biết `MotionEvent` (ruling Task 3); mở rộng API sẽ đụng cả `WidgetPickerController`, `IconDragGesture` | Không |

**D2 — các giả thuyết và cách điều phối xác nhận trên Live 4.** Chưa có giả thuyết nào được đo; Task 5 xác nhận trước khi sửa. Log tạm gắn nhãn `FX7TMP` (tag `FX7D2`, mỗi dòng có `SystemClock.uptimeMillis()`), nằm trong patch ngoài repo, không commit.

| # | Giả thuyết (khác biệt giữa vuốt nhanh và vuốt chậm) | Cơ sở | Cách xác nhận (log tạm + đo) |
|---|---|---|---|
| H1 | Fling liên tiếp: chạm mới tới khi pager còn `SETTLING` về trang cuối, `canOpen()` false nên Library không nhận (hoặc `MOVE` đầu hủy ứng viên); RecyclerView chặn cử chỉ, pager dừng giữa trang rồi không đi đâu: "Home dịch ~100 px rồi đứng" | Bảng đối chiếu D2 (cổng nhận cử chỉ) | Log ở `ACTION_DOWN` và mọi `cancelCandidate()`: `t, pagerState (0/1/2), currentItem, last, canOpen, lý do hủy`. Kịch bản: hai flick cách nhau 120/200/300 ms (một lệnh `adb shell` có `sleep`). Xác nhận nếu các lần Library không mở đều có `pagerState=2` ở DOWN, và lần cách ≥ 400 ms luôn mở |
| H2 | Flick 30 ms của `input swipe` chỉ có 1-2 `MOVE`: `p` nhảy 0 → ~0,33 trong một khung (Library "nhảy 360 px") là **artifact của cách đo**, không phải cử chỉ tay thật (tay thật ~8-16 ms mỗi `MOVE`, ≤ 50 px) | `setProgress(baseProgress - dx / W)` ở `MOVE` đầu | Log mỗi `MOVE` khi kéo: `t, rawX, p, historySize`. Đếm số `MOVE` trong ca 30 ms; lặp bằng `input swipe ... 120` và flick tay của chủ dự án; so độ nhảy `p` lớn nhất giữa hai khung. Nếu tay thật không nhảy thì bỏ ca 30 ms khỏi tiêu chí đạt (giữ để so) |
| H3 | Hoạt ảnh kết thúc bắt đầu ở tốc độ khác ngón tay (`2 × quãng / thời lượng` so với `vx`): đứt vận tốc, nhìn như "đáp trong 2 khung" | Bảng đối chiếu D2 (hoạt ảnh kết thúc) | Log ở `ACTION_UP`: `t, vx (px/s), p, open, durationMs, v0 = 2·\|1-p\|/duration·W`. Xác nhận nếu `v0` lệch `\|vx\|` quá 30% ở các lần "giật" |
| H4 | Lần hiện đầu của Library đo/dựng cả cây (và ô kính) trên luồng chính, rơi đúng khung chuyển tay: khung 147 ms | `library_view` `gone` từ đầu; `onSizeChanged` dựng lại ô | Log `CategoryPageView.onSizeChanged` và `render` (`t`, `System.nanoTime` đầu-cuối). `force-stop`, mở Home, vuốt tới Library (lần đầu) rồi quay về và vuốt lần hai. Xác nhận nếu `onSizeChanged` đầu tiên trùng khung dài ở lần đầu (và `gfxinfo framestats` cho thấy thời gian ở `PERFORM_TRAVERSALS`→`DRAW_START`, luồng chính) và lần hai không có |
| H5 | Khung dài 69-89 ms mỗi lần pager dừng là chụp lại kính dock lúc bỏ chặn (hoặc `DragHelper.refresh`/`requestPillMode`), không phải chuyển cảnh Library | `onPageScrollStateChanged(IDLE)` | `framestats` tách phần luồng chính và RenderThread quanh mỗi lần dừng; đổi mức kính dock sang "Tắt" trong Cài đặt (trả lại sau) và đo lại: khung dài biến mất = kính (chuyển cho FX8, ghi vào knowledgement, không sửa ở FX7) |
| H6 | Cú `ACTION_CANCEL` giả lúc tiếp quản làm pager đổi trạng thái (DRAGGING/SETTLING → IDLE, `searchPill.setDotsMode`, `DragHelper.refresh`) cùng khung với lần hiện Library | `HomeTouchRouter.dispatch` (TAKEOVER) | Log `onPageScrollStateChanged` (`t, state`) cạnh log tiếp quản; xác nhận nếu có chuyển trạng thái trong ±1 khung quanh `libraryView.isVisible = true` |

Sau Task 5 điều phối chốt giả thuyết nào đúng; Task 6-8 chỉ làm phần có giả thuyết tương ứng được xác nhận (H3 → Task 6, H1 → Task 7, H4 → Task 8). H5 và phần kính của H4 chỉ ghi nhận.

**Các phương án đã xét cho D2.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ đưa vận tốc vào hoạt ảnh kết thúc | `SpringProgress` (AN) với `startVelocity = -vx / W`, thay `ValueAnimator` cố định | Nhỏ (một file); sửa H3 | Không sửa H1 (cử chỉ bị mất khi pager còn trượt) và H4 (khung dài lần đầu) | Một phần của B |
| B. A + nhận cử chỉ khi pager `SETTLING` về trang cuối + làm nóng bố cục Library một lần | `canOpen` chấp nhận `currentItem == last && scrollState != DRAGGING`; `library_view` thành `INVISIBLE` (đã đo/bố cục) ở lần rảnh đầu tiên sau `Ready` thay vì `GONE` | Sửa H1, H3, H4 mà không đổi kiến trúc G2a D17; mỗi sửa một Task, mỗi Task có số đo riêng | Làm nóng tốn một lần đo/bố cục lúc rảnh sau khởi động (cần đo khởi động, Task 9); nhận cử chỉ lúc `SETTLING` để pager dừng lệch tới lần snap của SnapHelper khi `ACTION_CANCEL` giả tới (suy luận: xem Review Focus 9) | **Chọn** |
| C. Đưa Library về làm trang của `ViewPager2` | Pager tự lo vuốt, vận tốc, lockstep | Một cơ chế duy nhất cho mọi vận tốc | Đã thử và bỏ ở G2a B2 (`lessons/home-pager-animation.md`): view type thứ hai, `areItemsTheSame`, dock/pill, trần chiều cao; vá lần hai thì dừng | Không |
| D. Tự dựng pager overscroll (pager báo overscroll ở trang cuối, lái `p` bằng offset) | Không có "hai bên giao cử chỉ" | Không có bước chuyển tay | Viết lại cả pager/scroller; kính dock và chấm trang đang gắn vào sự kiện của pager (TL1/PL) | Không |
| E. Làm nóng bằng `INVISIBLE` ngay `onCreate` | Library được đo cùng khung đầu của Activity | Đơn giản nhất | Cộng vào đường khởi động lạnh (`lessons/startup-performance.md`); đo được là phải đo release | Không; thay bằng `IdleHandler` một lần (Task 8) |

**Vì sao chọn (B6).** Bytecode ViewPager2 xác nhận cơ chế (bảng đối chiếu) và audit đo được kết quả (400 → 0 px). Phương án B là phương án nhỏ nhất vẫn đúng cả khi danh sách trang đổi; A một mình sẽ xóa triệu chứng đo được nhưng để lại lỗi "kẹt sai trang" mà audit B6 (hướng sửa "tốt nhất là khôi phục theo `pageId`") và B5/MV nghi vấn (1) mô tả (pager bị kẹp khi trang tạm biến mất rồi hiện lại). Phương án C có rủi ro đúng kiểu đã gặp ở B5 nên bỏ. D chưa có số đo.

**Vì sao chọn (C18).** A chỉ sửa đúng chỗ đọc toạ độ (`HomeTouchRouter`), không đổi hợp đồng của `DragHelper` (D), không thêm trạng thái chuyển giao (B), và không hủy phiên vì chạm nhầm (C). Nhấc ngón kéo = thả đúng với cách hiểu thông thường của "đang kéo bằng ngón này"; chủ dự án có thể đổi sang B bằng quyết định Q1.

**Vì sao chọn (D2).** Chủ dự án mô tả đúng ba biến thể của một lỗi chuyển tay: vuốt thường/chậm không sao (cử chỉ bắt đầu khi pager đã IDLE, nhấc tay chậm nên vận tốc nhỏ và `p` thường đã quá nửa), vuốt nhanh sai. Mã hiện tại có đúng ba chỗ phụ thuộc vận tốc (bảng giả thuyết) và AP đã chốt kiến trúc lớp phủ có `progress` duy nhất, nên cách ít rủi ro nhất là giữ `p` duy nhất (lockstep đã đúng theo thiết kế) và sửa từng chỗ chuyển tay, đo từng chỗ. `SpringProgress` đã có (AN Task 1: đổi đích giữ vận tốc, tự snap khi tắt hoạt ảnh hệ thống) nên không thêm lớp mới. Không chọn C vì G2a đã chứng minh vá lần hai không tới đâu.

**C14 và C12 (một câu mỗi chỗ).**
- C14: hai chỗ có thể sửa: (A) `DragHelper.finishSettleNow()` công khai gọi ở `IconMenuController` trước `startDrag` (đúng chữ audit); (B) `DragLayer.cancelSettle()` chạy callback đã lưu (chọn). Chọn B vì lỗi sinh ra do **một lớp bỏ quên hợp đồng ở chỗ hủy** và một nơi gọi đi vòng qua `DragHelper`; sửa ở nơi giữ spring thì mọi đường hủy (hiện tại và sau này) đều dọn đúng, không thêm API công khai, và `finishSettle()` là idempotent (`:242-247`) nên đường `DragHelper.beginDrag` (đã gọi `finishSettle()` trước) không đổi. KDoc `DragVisual.settleTo` (`DragHelper.kt:26-29`) đang nói "KHÔNG gọi `onEnd`" phải sửa theo.
- C12: so thêm `container` và `key` (và `itemId`) thay vì mã yêu cầu tăng dần: không cần trường mới trong `IconMenuState` (`HomeUiState.kt` là file độc quyền), không đổi `FolderPanelView` (hàng con không có id ở tầng UI), và kết quả cũ của **cùng** app mở lại là dữ liệu đúng. Mã yêu cầu tăng dần chặt hơn nhưng phải thêm trường ở state hoặc trường riêng ở ViewModel; không đáng cho lỗi hiếm.

**Đánh đổi chấp nhận.** (1) C14: ảnh hạ cánh bị cắt ngang khi menu mở (ô hiện ngay), chỉ xảy ra khi người dùng làm được trong chừng hạ cánh (~0,3 s ở tốc độ thường, nên thực tế rất hiếm). (2) B6: khôi phục theo `pageId` có thể nhảy trang **sau khi** pager rảnh (không animation) nếu trang người dùng đang xem đổi chỉ số; đó là hành vi đúng (giữ đúng trang), nhưng người dùng thấy một bước nhảy nhỏ thay vì trang trượt lệch (xảy ra rất hiếm). (3) C18: ngón thứ hai không bao giờ cuộn trang hay làm gì trong lúc kéo. (4) D2: spring kết thúc có thể quá `p = 1` một chút (tới hạn thì rất ít) nên `p` kẹp `0..1`; làm nóng Library tốn một lần đo/bố cục lúc rảnh sau khởi động và giữ cây view Library trong bố cục (không vẽ). (5) Số đo B6 là bản **debug**; chênh lệch debug/release không ảnh hưởng tới hình dạng lỗi nhưng ảnh hưởng độ trễ từ `pm disable-user` tới lúc giật. Xét lại khi: FX3 merge và vẫn còn giật (nghĩa là trigger không phải danh sách), hoặc chủ dự án muốn chuyển giao ngón (Q1).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | FX7 sửa B6, C12, C14, C18; đợt 2, sau FX1 và FX2; giữ độc quyền `DragHelper.kt`, `HomeViewModel.kt` | Chủ dự án, 2026-11-11 (audit, "Kế hoạch sửa") |
| 2 | B6: khôi phục theo `pageId`, chỉ khi `scrollState == IDLE`, hoãn tới `IDLE` | Claude đề xuất, 2026-11-11; cơ sở: audit B6 + bytecode `ViewPager2.setCurrentItemInternal` |
| 3 | C14: sửa ở `DragLayer.cancelSettle()` (chạy callback đã lưu); không thêm API vào `DragHelper`, chỉ sửa KDoc | Claude đề xuất, 2026-11-11; cơ sở: bảng đối chiếu C14 |
| 4 | C12: so `itemId`, `container`, `key`; không sửa `FolderPanelView`, không thêm trường vào `IconMenuState` | Claude đề xuất, 2026-11-11 |
| 5 | **Q1 (điều phối chốt 2026-11-11: giữ mặc định):** nhấc ngón đang kéo = thả tại chỗ ngón đó, ngón còn lại bị bỏ qua tới hết cử chỉ. Phương án khác: chuyển giao sang ngón còn lại (phương án B của C18) | Claude đề xuất, 2026-11-11; câu hỏi hành vi UI, thuộc quyền chủ dự án |
| 6 | Hook đo tạm gắn nhãn `FX7TMP` (làm chậm hạ cánh, làm chậm tải shortcut), giữ trong một patch ngoài repo; không commit; `grep FX7TMP` rỗng khi đóng nhánh | Claude đề xuất, 2026-11-11; cùng cách với FX2 (`JIG`) |
| 8 | **D2:** giữ lớp phủ Library (G2a D17/AP), một `p` duy nhất; sửa theo giả thuyết đã xác nhận: vận tốc vào spring (H3), nhận cử chỉ khi pager `SETTLING` về trang cuối (H1), làm nóng bố cục Library bằng `IdleHandler` một lần (H4). Không sửa kính (FX8) | Claude đề xuất, 2026-11-11; chủ dự án mô tả lỗi (vuốt nhanh), audit D2 (đo) |
| 9 | Task 5 (xác nhận giả thuyết) chạy trước khi sửa; Task 6-8 chỉ làm phần có giả thuyết được xác nhận | Claude đề xuất, 2026-11-11; đúng quy ước `AGENTS.md` (nguyên nhân chưa rõ thì rải log trước khi sửa) |
| 10 | Tiêu chí đạt D2 (Live 4, debug, so cùng phiên với `main-hook.apk`): Library mở được ở mọi lần fling kể cả khi pager còn `SETTLING`; độ dời Library và Home khớp nhau từng khung (chênh ≤ 8 px); không còn khung > 100 ms quanh chỗ chuyển tay; không tệ hơn ở vuốt chậm và ở số khung > 33 ms. Ca 30 ms chỉ dùng để so nếu H2 xác nhận là artifact | Claude đề xuất, 2026-11-11 |
| 7 | Không sửa `IconMenuController`, `FolderPanelView`, `IconGridView`, `DockView`, `IconDragGesture`, `HomeActivity`, `HomeUiState`, `HomePagerAdapter` | Ràng buộc đợt 1/2 (vùng file của FX2, FX5); không cần thiết |

## Ngoài phạm vi / ghi để sau

- Nhiều ngón **trước khi** có phiên kéo (`IconDragGesture`: giữ icon rồi nhấc ngón đầu khi ngón hai còn chạm; `GapLongPressGesture`, `LibraryOverlayController`, `SearchSwipeDetector` đã tự hủy khi có `POINTER_DOWN`). Không sửa; `IconDragGesture` nhận sự kiện đã tách theo ngón (`splitMotionEvents`: suy luận, chưa kiểm).
- Nghi vấn MV (hàng 388 của mục 10, hoãn từ 2026-11-09): FX7 **chạm** (1) trạng thái nửa vời của hai flow Room (B6 giảm tác hại phía pager nhưng không sửa gốc, gốc là B5/FX3), (4) id tạm (`PageId.NEW`, âm): hàm khôi phục theo `pageId` phải chịu được id đổi (không thấy thì kẹp chỉ số, bảng "Quyết định"), (5) ô hạ cánh kẹt alpha 0: C14 đóng **thêm một đường** làm ô kẹt (menu mở lúc hạ cánh), không phải cơ chế "trang bị tách" của nghi vấn (5). **Không** chạm (2) `DragHelper.unregisterTarget` không gọi `onDragExit`, (3) hàng DB không vẽ được. Điều phối ghi lại vào hàng MV sau khi merge.
- `ACTION_CANCEL`/`POINTER_UP` do hệ thống (cử chỉ điều hướng, thông báo) trong lúc kéo: giữ hành vi hiện có (`drop(null)` + `onFinished(false)`).
- Khôi phục trang sau `recreate()` (ViewPager2 tự lưu `currentItem`) giữ nguyên.
- Hỗ trợ RTL của `PagerFlipAnimator`: ngoài phạm vi (đã ghi ở KDoc của nó).
- **Ranh giới D2:** FX8 giữ `core/glass/*` và việc vẽ kính của Library (kính ô nhạt/rỗng ở các khung đầu, vẽ lại ~1,3 s sau khi đáp; `CategoryPageView.setTranslationX` chụp kính khi `translationX == 0`, `BackgroundGlass`, `GlassEnvironment`); FX5 giữ map icon trong `IconGridView`/`DockView`/`FolderPanelView` và `data/apps/*`. FX7 không sửa các vùng đó; nếu giả thuyết H4/H5 chỉ ra phần kính thì ghi vào knowledgement và báo điều phối chuyển FX8. Chỉ thêm **log tạm** vào `CategoryPageView` (không commit).
- Khung dài 69-89 ms mỗi lần pager dừng (H5): nếu là kính dock thì thuộc FX8; FX7 chỉ cung cấp số đo.
- Chiều đóng Library (vuốt phải): cùng `LibraryOverlayController` nên hưởng sửa vận tốc; không có tiêu chí đo riêng.

## Vùng file

| File (từ `app/src/main/java/gambi/global/os26launcher/`) | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/controller/HomePagerController.kt` | Sửa: trường `selectedPageId`, `restorePending`; `onPageSelected` (`:104-109`) ghi `selectedPageId`; `onPageScrollStateChanged` (`:111-123`) gọi lại khôi phục ở `IDLE`; `scrollToPage` (`:131-143`) và `renderPages` (`:165-197`) gọi `settlePageSelection()` thay cho `setCurrentItem` trực tiếp | Không (FX2 cam kết không đụng; FX5/FX6 không liên quan) |
| `ui/home/view/DragLayer.kt` | Sửa: `cancelSettle()` (`:100-107`) chạy callback đã lưu | Không |
| `core/ui/DragHelper.kt` | Sửa **chỉ KDoc** của `DragVisual.settleTo` (`:26-29`). Không đổi code | **Độc quyền** (FX7 giữ đợt 2) |
| `ui/home/HomeViewModel.kt` | Sửa gọn: điều kiện ở `onIconLongPressed` (`:305-311`, một dòng). **Không** đụng khối ảnh nền (`:142-191`, của FX1) | **Độc quyền** (FX7 giữ đợt 2, sau khi FX1 merge và rebase) |
| `ui/home/controller/HomeTouchRouter.kt` | Sửa: `dispatch` (`:49-98`), thêm `dragPointerId`, `swallowUntilGestureEnd` và hai hàm đọc toạ độ theo chỉ số | Không |
| `ui/home/LibraryOverlayController.kt` | Sửa (D2, Task 6-7): `animateTo` (`SpringProgress` có vận tốc đầu), `onTouch` (ứng viên không còn đòi pager IDLE, theo `Host.canOpen`) | Không (AP đã merge, không nhánh nào khác giữ) |
| `ui/home/controller/AppLibraryController.kt` | Sửa (D2, Task 7-8): `canOpenLibrary`, `applyLibraryProgress` (`INVISIBLE` thay `GONE` sau khi làm nóng), làm nóng một lần bằng `IdleHandler` | Không |
| `ui/home/view/AppLibraryMotion.kt` | Sửa (D2, Task 6): thêm `SWIPE_SETTLE_SPRING` (và hằng liên quan) | Không |
| `ui/home/controller/HomePagerController.kt` (thêm) | D2 Task 7: định nghĩa lại `isSettledOnLastPage` = `currentItem == last && scrollState != DRAGGING` (KDoc), giữ tên để không đụng `HomeActivity` | Không |
| `docs/knowledgements/FX7-home-gestures.md` | Tạo (Task 10) | Không |
| `docs/report/FX7-home-gestures.md` | Sửa (nhật ký, kết quả) | Không |

Chỉ **log tạm không commit** (nhãn `FX7TMP`): `ui/home/view/CategoryPageView.kt` (thuộc vùng kính/Library của FX8), `ui/home/controller/HomeTouchRouter.kt` (log tiếp quản; chỉ sửa thật nếu H6 xác nhận). Không đụng: `core/glass/*`, `BackgroundGlass`, `GlassEnvironment` (FX8), `ui/home/HomeActivity.kt` (không cần đổi: `isPagerSettledOnLastPage` giữ nối cũ), `ui/home/controller/IconMenuController.kt`, `ui/home/view/FolderPanelView.kt`, `IconGridView.kt`, `DockView.kt`, `IconDragGesture.kt`, `ui/home/HomeActivity.kt`, `HomeUiState.kt`, `HomePagerAdapter.kt`, `core/ui/SpringProgress.kt` (chỉ hook tạm, không commit), `core/glass/*`, `data/*`, `res/*`, `AndroidManifest.xml`. Cần file ngoài vùng thì dừng và báo. Rebase lên `main` sau khi FX1 và FX2 merge trước khi bắt đầu code (điều phối làm, MultiAgent-Workflow mục 11); FX1 sửa phần ảnh nền của `HomeViewModel`, vùng cách xa `:305-311`.

## Global Constraints

- Không code trước khi plan được duyệt. Mỗi Task: build `gradlew.bat assembleDebug` xanh trong worktree, commit tiếng Anh không dấu (`fix:`), một hoặc vài commit mỗi Task, cập nhật Nhật ký, dừng và báo. Stage theo đường dẫn, không `git add docs` hay `-A`.
- Không thêm test, không thêm thư viện. Không merge `main` vào nhánh. Không cài APK, không chạy adb: điều phối làm (agent chỉ dựng APK ra thư mục tạm ngoài repo khi điều phối cần bản có hook tạm).
- Giữ bẫy chạm trong `AGENTS.md`/`lessons/drag-and-layout.md`: không đổi `requestDisallowInterceptTouchEvent`; không đặt trạng thái kéo-thả vào view của `RecyclerView`/`ViewPager2` (`selectedPageId` ở controller, sống theo Activity, không ở view); `HomeActivity.dispatchTouchEvent` vẫn chỉ gọi `touchRouter.dispatch`; phiên kéo vẫn do Activity tiếp quản và gửi **một** `ACTION_CANCEL` giả xuống cây view.
- B6: sau Task 1 không còn lời gọi `setCurrentItem` nào chạy khi `scrollState != IDLE` **trong controller này** (trừ `PagerFlipAnimator`, có chủ đích). `grep -n "setCurrentItem" ui/home/controller/HomePagerController.kt` chỉ còn trong `settlePageSelection()`.
- `getRawX(int)` cần API 29: mọi chỗ dùng phải có nhánh `SDK_INT` (minSdk 26), không để lint `NewApi` làm build đỏ.
- Hook tạm: chỉ trong cây làm việc, nhãn `FX7TMP`, nằm trong một patch ngoài repo (`%TEMP%\fx7-hooks.patch`); gỡ bằng `git apply -R`/`git checkout -- <file>` trước mỗi commit; `grep -rn FX7TMP app/src` phải rỗng ở mọi commit.
- D2: `p` vẫn là nguồn duy nhất của vị trí Library và Home (mọi ghi vào `translationX`, alpha, visibility của `library_view`/`home_content` đi qua `applyLibraryProgress`/`applyLibraryLayer`); không thêm đường `animate().translationX(...)` vì `CategoryPageView.setTranslationX` chỉ chụp kính khi nhận thuộc tính (ghi chú `:514-531`). `SpringProgress` chỉ dùng trên main thread; hủy khi Activity hủy (đúng cách `ValueAnimator` cũ được hủy). Không đổi thứ tự nhận chạm của `HomeTouchRouter` (DOWN → Library → tìm kiếm → cây view).
- Bản đo là **debug**; luôn ghi bản nào, `git rev-parse --short HEAD` của từng APK. Không so số giữa hai phiên cách xa nhau.

## Review Focus

1. Không còn `setCurrentItem` nào trong `HomePagerController` chạy khi pager không rảnh; `settlePageSelection()` là nơi duy nhất (grep). `PagerFlipAnimator` không đổi.
2. `selectedPageId` cập nhật ở `onPageSelected` (đủ ba đường: kéo, `setCurrentItem`, FakeDrag) và sau mỗi lần khôi phục; tìm lại trang theo `pageId` chỉ khi **duy nhất** trong danh sách (layout nháp có `pageId = 0`); không thấy hoặc không duy nhất thì kẹp chỉ số `currentItem` vào `0..lastIndex`; chỉ `setCurrentItem(target, false)` khi `target != currentItem`.
3. Hoãn đúng: `restorePending` đặt khi không rảnh, chạy ở `IDLE` (nên `post` để không chạy giữa lúc RecyclerView xử lý đổi trạng thái cuộn), không lặp vô hạn (lần `IDLE` do chính khôi phục gây ra thấy `restorePending == false`). `pendingScrollPageId` (G2c) vẫn có TTL 3 giây và chỉ được tiêu thụ khi rảnh.
4. Nhánh `pendingFlipToNewPage` (kéo icon ra mép phải, trang tạm) **không** bị chặn bởi cổng rảnh (đúng như cũ), không đổi thứ tự so với nhánh cuộn tới trang widget và nhánh khôi phục.
5. `DragLayer.cancelSettle()`: lấy callback ra biến cục bộ, đặt `settleOnEnd = null` **trước** khi gọi (tránh gọi hai lần khi callback chạm lại `DragLayer`), gọi sau khi đã đặt lại `settling`, `drawScale`, `alpha`. `startDrag` vẫn đặt `bitmap` mới **sau** `cancelSettle()`. `finishSettle()` idempotent (`DragHelper.kt:242-247`). Không còn chú thích "KHÔNG gọi onEnd" sai nghĩa.
6. C12: kết quả shortcut chỉ áp khi `menu.itemId`, `menu.container`, `menu.key` đều khớp tham số của lần gọi; đóng menu rồi mở lại **cùng** app vẫn nhận được shortcut (kết quả đến sau cùng áp đúng).
7. C18: `dragPointerId` đặt ở sự kiện đầu tiên thấy phiên và xóa khi phiên kết thúc **và** khi `session == null` ở đầu `dispatch`; `ACTION_POINTER_DOWN` bị nuốt (không rơi xuống cây view, không gọi `updatePosition`); `POINTER_UP` của ngón khác không làm gì; nhánh `ACTION_DOWN` mới khi còn phiên sót, nhánh `ACTION_CANCEL` giữ nguyên; sau thả ở `POINTER_UP`, phần còn lại của cử chỉ bị nuốt tới `UP`/`CANCEL`/`DOWN` (không rơi vào `library.onTouch` hay `search.onTouch` vì chúng chờ `DOWN`).
8. D2 Task 6: dấu vận tốc đúng (`p` tăng khi vuốt trái: `pVelocity = -vx / W`), `p` kẹp `0..1`, `onSettled` gọi một lần ở đích, `isAnimatingOrDragging` đúng cả khi spring chạy; tắt hoạt ảnh hệ thống thì snap (hành vi `SpringProgress`); `DOWN` lúc spring đang chạy giữ hành vi cũ ("bỏ qua chạm khi đang animate") trừ khi plan Task 6 ghi khác.
9. D2 Task 7: `canOpen` không để Library mở khi `DRAGGING` hoặc FakeDrag (`pagerFlip`), không mở khi `currentItem != last` hoặc có ghost page; `ACTION_CANCEL` giả vẫn là **một** lần; sau tiếp quản pager về đúng lưới (không đứng lệch giữa hai trang) vì SnapHelper snap ở `IDLE`; trang cuối vẫn lật về trang trước được khi vuốt phải.
10. D2 Task 8: `INVISIBLE` (không phải `GONE`) chỉ sau khi làm nóng, `libraryView` không nhận chạm khi `INVISIBLE`; `IdleHandler` gỡ khi Activity hủy và chạy một lần; không làm lại ở mỗi `render`; đo khởi động lạnh không tệ hơn (Task 9).
11. Không file nào ngoài vùng; `DragHelper.kt` chỉ đổi KDoc; không đổi chữ ký công khai nào; không log tạm còn sót.

---

### Task 1: B6 — pager không bị `setCurrentItem` lúc không rảnh; khôi phục theo `pageId`

**Bắt buộc kiểm tay:** **có** — cử chỉ chạm (vuốt) và hoạt ảnh pager: loại lỗi mà review code khó thấy và đã gây hồi quy nhiều lần (mục 4.2 của MultiAgent-Workflow.md; bài học M7). Điều phối kiểm bằng adb trước khi sang Task 2 (chủ dự án giao điều phối, 2026-11-11).
**Files:** `HomePagerController.kt` (chỉ file này).
**Interfaces:** Consumes: `ViewPager2.scrollState`, `HomePagerAdapter.currentList`. Produces: không đổi chữ ký công khai (`renderPages`, `scrollToPage`, `setup` giữ nguyên).

- [x] **Step 1 (baseline, chưa code):** điều phối chụp lại B6 trên `main` theo mục "Bài kiểm" B6-a và B6-b để có số "trước" cùng phiên với số "sau" (audit đã có một lần đo; không dùng lại số cũ).
- [x] **Step 2: trường và theo dõi.** Thêm `private var selectedPageId: Long? = null` và `private var restorePending = false`. Trong `onPageSelected` ghi `selectedPageId = homePagerAdapter.currentList.getOrNull(position)?.pageId` (giữ nguyên giá trị cũ nếu `getOrNull` trả null). Chú thích B6: id theo `HomePage.pageId`, không phải chỉ số.
- [x] **Step 3: `settlePageSelection()`.** Hàm riêng (private) làm đúng ba việc, theo thứ tự: (1) danh sách rỗng thì thoát; (2) `scrollState != SCROLL_STATE_IDLE || pagerFlip.isFlipping` thì đặt `restorePending = true` rồi thoát; (3) rảnh: `restorePending = false`; nếu `consumePendingScroll(list)` trả chỉ số thì `setCurrentItem(index, true)` (G2c) rồi thoát; ngược lại tính `target`: chỉ số duy nhất của `selectedPageId` trong danh sách, nếu không có hoặc không duy nhất thì `currentItem.coerceIn(0, lastIndex)`; cập nhật `selectedPageId = list[target].pageId`; chỉ khi `target != currentItem` thì `setCurrentItem(target, false)`.
- [x] **Step 4: nối vào `renderPages`.** Bỏ `val savedPage` (`:169`). Trong callback của `submitList`: giữ `syncDragAlpha()`; giữ nhánh `pendingFlipToNewPage && state.ghostPage` (gọi `pagerFlip.flipTo(last)` như cũ, **không** qua cổng rảnh); các trường hợp còn lại gọi `settlePageSelection()`. Giữ thứ tự: nhánh lật tới trang tạm đứng trước nhánh khôi phục, và nhánh cuộn tới trang widget (nay nằm trong `settlePageSelection`) vẫn ưu tiên hơn khôi phục.
- [x] **Step 5: nối `scrollToPage` và `IDLE`.** `scrollToPage`: khi trang đã có, gọi `settlePageSelection()` sau khi đặt `pendingScrollPageId` (thay `setCurrentItem(.., true)` trực tiếp; nhớ giữ nhánh "chưa có trang thì chờ"). `onPageScrollStateChanged`: ở `IDLE`, nếu `restorePending` thì `binding.homePager.post { settlePageSelection() }` (sau `DragHelper.refresh()` và `requestPillMode()` hiện có, không đổi thứ tự chúng).
- [x] **Step 6:** `grep -n "setCurrentItem" ui/home/controller/HomePagerController.kt` chỉ còn trong `settlePageSelection()`. Build `assembleDebug` xanh. Commit `fix: restore the viewed page by id and only when the pager is idle`.
- [x] **Step 7: Kịch bản kiểm (Live 4, APK = commit Task 1; "Bài kiểm" B6-a đến B6-f):** theo bảng; ngưỡng đạt: (a) khi ngón còn trên màn, độ dời giữa hai khung liền nhau không giảm quá 30 px (trước sửa: ~400 px ở khung 70); (b) khi trigger rơi vào lúc trượt về, chuyển động liên tục tới đích, không nhảy; (c)-(f) hồi quy.

---

### Task 2: C14 — hạ cánh bị hủy vẫn báo xong

**Bắt buộc kiểm tay:** **có** — chạm/kéo-thả (mục 4.2): trạng thái alpha của ô sau khi menu mở chen vào hạ cánh chỉ thấy trên máy; kiểm bằng hook làm chậm hạ cánh (điều phối).
**Files:** `DragLayer.kt`, `DragHelper.kt` (chỉ KDoc).
**Interfaces:** Consumes: `DragVisual.settleTo(.., onEnd)`. Produces: hợp đồng mới: `onEnd` của `settleTo` được gọi đúng một lần, kể cả khi hạ cánh bị `startDrag`/`endDrag` hủy giữa chừng.

- [x] **Step 1: `DragLayer.cancelSettle()`.** Khi đang `settling`: hủy spring, lấy `settleOnEnd` ra biến cục bộ, đặt `settleOnEnd = null`, `settling = false`, `drawScale = 1f`, `bitmapPaint.alpha = 255`, rồi gọi callback đã lấy (trong `try`/không làm hỏng `startDrag`: lỗi trong callback không được chặn việc hiện ảnh mới; dùng `try { } catch (e: Exception) { Log.w }` như `DragHelper.detachDragVisual`). Thay chú thích `// DragHelper tự dọn...` bằng chú thích C14 nói rõ lý do.
- [x] **Step 2: KDoc `DragHelper.DragVisual.settleTo`** (`:26-29`): đổi câu "KHÔNG gọi [onEnd]: [DragHelper] tự dọn" thành hợp đồng mới (hủy giữa chừng thì vẫn gọi `onEnd` một lần; `onEnd` của `DragHelper` là `finishSettle()`, idempotent). Không đổi code trong `DragHelper.kt`.
- [x] **Step 3:** Build xanh. Commit `fix: report a cancelled drop settle so the landing cell is not left hidden`.
- [x] **Step 4: Kịch bản kiểm (Live 4, APK = commit Task 2 + hook làm chậm hạ cánh; "Bài kiểm" C14-a, C14-b):** (a) ô vừa thả **hiện lại** (alpha 1) khi menu của icon khác mở chen vào hạ cánh, và vẫn đúng sau khi đóng menu (trước sửa: ô trống tới phiên kéo sau); (b) hồi quy: thả bình thường (không menu) ảnh vẫn hạ cánh trượt vào ô và ô hiện đúng lúc; kéo thả liên tiếp hai lần nhanh không có ô kẹt ẩn.

---

### Task 3: C12 — kết quả shortcut tải chậm không hiện sai app trong thư mục

**Bắt buộc kiểm tay:** **có** — menu nhấn giữ là cử chỉ (mục 4.2); lỗi chỉ lộ khi tải chậm hơn một lần nhấn giữ nên cần hook `delay` để tái hiện (điều phối).
**Files:** `HomeViewModel.kt` (một điều kiện).
**Interfaces:** Consumes: `IconMenuState(itemId, container, key, ...)`. Produces: không đổi.

- [x] **Step 1:** `onIconLongPressed` (`:305-311`): trong `_uiState.update` của coroutine nạp shortcut, đổi điều kiện `menu != null && menu.itemId == itemId` thành so thêm `menu.container == container && menu.key == key`; thêm chú thích C12 (hàng con trong thư mục luôn có `itemId = -1`, nên `itemId` một mình không phân biệt hai app). Không đụng khối ảnh nền của FX1, không đổi chữ ký.
- [x] **Step 2:** Build xanh. Commit `fix: apply shortcut results only to the menu that requested them`.
- [x] **Step 3: Kịch bản kiểm (Live 4, APK = commit Task 3 + hook `delay`; "Bài kiểm" C12-a, C12-b):** (a) trong thư mục có Chrome và một app không có shortcut: nhấn giữ Chrome, đóng menu, nhấn giữ app kia trước khi kết quả của Chrome về: menu app kia **không** hiện "Thẻ ẩn danh mới"/"Thẻ mới" (trước sửa: hiện); (b) hồi quy: nhấn giữ Chrome ở dock và ở thư mục vẫn hiện đúng shortcut của Chrome; nhấn giữ rồi đóng rồi nhấn giữ lại **cùng** Chrome vẫn hiện shortcut.

---

### Task 4: C18 — phiên kéo chỉ theo ngón đã bắt đầu kéo

**Bắt buộc kiểm tay:** **có** — cử chỉ chạm đa điểm (mục 4.2). Hai ngón không dựng được bằng `input`; điều phối thử `sendevent` trên Live 4, nếu không được thì chủ dự án kiểm bằng tay (bảng "Bài kiểm" C18).
**Files:** `HomeTouchRouter.kt`.
**Interfaces:** Consumes: `DragHelper.updatePosition/dropAt/drop`, `DragSession.onFinished`. Produces: không đổi.

- [x] **Step 1: trường.** `private var dragPointerId = NO_POINTER` (`-1`) và `private var swallowUntilGestureEnd = false`. Hai hàm riêng đọc toạ độ theo chỉ số ngón: `rawXAt(ev, index)`, `rawYAt(ev, index)`: `SDK_INT >= Q` dùng `ev.getRawX(index)`; ngược lại `ev.getX(index) + (ev.rawX - ev.getX(0))` (độ lệch cửa sổ không đổi trong một sự kiện).
- [x] **Step 2: đầu `dispatch`.** Sau `iconMenu.onTouchPrelude(ev)`: nếu `swallowUntilGestureEnd`: `ACTION_DOWN` thì bỏ cờ và đi tiếp; `UP`/`CANCEL` thì bỏ cờ và `return true`; còn lại `return true`. Khi `DragHelper.currentSession == null` thì đặt `dragPointerId = NO_POINTER` (như cách `dragTouchTakenOver` đã được đặt lại).
- [x] **Step 3: nhánh có phiên.** Khi `dragPointerId == NO_POINTER` thì `dragPointerId = ev.getPointerId(0)` (ngón ở chỉ số 0 của sự kiện đầu tiên thấy phiên; đây là ngón mà `begin(event.rawX, …)` đã dùng, bảng đối chiếu). Giữ nguyên `ACTION_DOWN` (phiên sót) và `dragTouchTakenOver` + `ACTION_CANCEL` giả. Thay `when (ev.actionMasked)`: 
  - `MOVE`: `index = ev.findPointerIndex(dragPointerId)`; `index >= 0` thì `updatePosition(rawXAt, rawYAt)`; ngược lại bỏ qua.
  - `POINTER_DOWN`: bỏ qua.
  - `POINTER_UP`: nếu `ev.getPointerId(ev.actionIndex) == dragPointerId` thì `dropAt(rawXAt(actionIndex), rawYAt(actionIndex))`, `session.onFinished(accepted)`, đặt `swallowUntilGestureEnd = true`; ngón khác thì bỏ qua.
  - `UP`: như cũ (`dropAt(ev.rawX, ev.rawY)` + `onFinished`); chỉ còn một ngón nên là ngón kéo.
  - `CANCEL`: như cũ.
  Chú thích C18 nêu quy tắc (chỉ theo `pointerId` đã bắt đầu; nhấc ngón đó = thả; ngón khác bị bỏ qua).
- [x] **Step 4:** Build xanh. Commit `fix: follow only the pointer that started the drag session`.
- [x] **Step 5: Kịch bản kiểm (Live 4, APK = commit Task 4; "Bài kiểm" C18-a đến C18-d):** (a) hai ngón, nhấc ngón đầu trước: icon thả tại chỗ ngón đầu (trước sửa: nhảy sang ngón hai); (b) hai ngón, ngón hai chạm rồi nhấc: không ảnh hưởng, icon vẫn theo ngón đầu; (c) hồi quy một ngón: kéo giữa hai trang (lật mép), vào dock, lên thư mục (gộp), thả ra ngoài (bay về), kéo bắt đầu từ menu (đường B), kéo widget từ bảng chọn; (d) `ACTION_CANCEL` (kéo xuống thanh thông báo giữa lúc kéo): hủy như cũ.

---

### Task 5: D2 — xác nhận giả thuyết (đo, chưa sửa)

**Baseline = `main` 723affd** (APK điều phối: `E:\SourceTree\IosLauncher-wt\apk\main-723affd.apk`; thêm log D2 thì dựng `main-d2.apk` từ đúng commit đó). Không dùng lại số đo cũ của audit D2 (bản cũ trước FX1-FX6).

**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng dùng để chọn phương án (mục 4.2 của MultiAgent-Workflow.md): kết quả quyết Task 6-8 làm gì. Điều phối đo trên Live 4 (chủ dự án giao).
**Files:** chỉ log tạm (`FX7TMP`, không commit): `LibraryOverlayController.kt`, `CategoryPageView.kt`, `HomePagerController.kt`, `HomeTouchRouter.kt`.
**Interfaces:** Produces: `%TEMP%\fx7-d2-hooks.patch`, `main-d2.apk` (`main` 723affd + log) để đo baseline, bảng H1-H6 có "xác nhận / bác bỏ" kèm số.

- [x] **Step 1: log tạm** (một patch ngoài repo, áp lên `main` rồi lên từng commit D2): theo cột "Cách xác nhận" của bảng H1-H6 (tag `FX7D2`, `uptimeMillis`): `ACTION_DOWN`/`cancelCandidate` (`pagerState`, `currentItem`, `canOpen`, lý do), `MOVE` khi kéo (`rawX`, `p`, `historySize`), `ACTION_UP` (`vx`, `p`, `open`, `durationMs`, `v0`), `CategoryPageView.onSizeChanged`/`render` (thời gian), `onPageScrollStateChanged` (state). Build `assembleDebug`, chép APK ra thư mục tạm; **gỡ patch trước mọi commit** (`grep -rn FX7TMP app/src` rỗng).
- [x] **Step 2: Kịch bản đo (Live 4, debug, kính Ép cao; mục "Bài kiểm" D2-a đến D2-f) trên `main-d2.apk`:** quay màn hình (`screenrecord`, 30 khung/s) và đo độ dời Library và Home từng khung bằng script tương quan chéo (như B6; hai dải: dải trên mép phải của Library và dải trên Home), kèm `dumpsys gfxinfo gambi.global.os26launcher framestats` (`reset` trước mỗi ca; khử trùng theo `IntendedVsync`; tách thời gian luồng chính và RenderThread của các khung > 33 ms) và `logcat -s FX7D2`.
- [x] **Step 3:** điều phối điền bảng H1-H6 (xác nhận/bác bỏ, số) vào Nhật ký và chốt Task 6-8; giả thuyết nào bị bác bỏ thì Task tương ứng bị bỏ (không làm "cho chắc").

---

**Kết quả Task 5 (điều phối, Live 4, debug, `main-d2.apk` = `main` 723affd + log D2, 2026-11-11 ~16:00):**

| # | Kết quả | Số liệu |
|---|---|---|
| H1 | **Không kiểm được bằng adb** | Mỗi lệnh `input` tốn 0,3-0,6 s nên dù có `sleep 0,12` giữa hai flick, mọi `DOWN` đều thấy `pagerState=0 cur=3 settledFn=true`. Ngón tay thật có thể flick trong ~150 ms và chủ dự án nói chỉ vuốt nhanh mới lỗi, nên **vẫn làm Task 7** (nhỏ) và thêm vào bài kiểm của chủ dự án: "hai flick nhanh liên tiếp từ trang áp chót" |
| H2 | **Artifact của `input swipe` 30 ms** | `TAKEOVER dx=-261` rồi khung đầu `p=0,24`. Bỏ ca 30 ms khỏi tiêu chí đạt (giữ để so); dùng 80/120 ms và tay chủ dự án |
| H3 | **Xác nhận** | Flick 30 ms: `UP vx=-25000 p=0,54 durMs=118 v0=8347 px/s` (hoạt ảnh chậm gấp ~3 lần ngón: "đứng"). Flick 80 ms: `vx=-1875 p≈0,10-0,14 durMs≈225 v0≈8330` (hoạt ảnh nhanh gấp ~4 lần ngón: "nhảy"). Vận tốc không được mang vào hoạt ảnh → Task 6 |
| H4 | **Xác nhận trên `main`; Task 8 BỎ** | Khởi động lạnh, flick đầu vào Library: `LIBRARY_FIRST_VISIBLE` → `LIB_SIZE_START w=1080 h=2340 oldw=0` → `LIB_SIZE_END tookMs=223` trên luồng chính đúng lúc chuyển tay. **FX8 (PR #39) đã chuyển việc dựng ô Library sang idle handler trước lần hiện đầu**: trên bản FX8 điều phối đo `Category.onSizeChanged 0x0 -> 1080x2340 sizesEqual=true` (không dựng lại) và ô kính vẽ ngay khung đầu. Để không động vào `CategoryPageView`/bố cục Library hai lần, FX7 **không làm Task 8** (làm nóng bằng `INVISIBLE`/`IdleHandler`); đo lại H4 sau khi FX8 merge |
| H5 | Chưa đo | Thuộc FX8 (kính dock lúc bỏ chặn) |
| H6 | Không có bằng chứng | Không đo thấy chuyển trạng thái pager cùng khung với lần hiện Library |

### Task 6: D2 — vận tốc thả tay vào hoạt ảnh kết thúc (H3)

**Bắt buộc kiểm tay:** **có** — cử chỉ vuốt (mục 4.2); kiểm bằng adb.
**Files:** `LibraryOverlayController.kt`, `AppLibraryMotion.kt`.
**Interfaces:** Consumes: `SpringProgress.animateTo(target, startVelocity, newSpec)`. Produces: `AppLibraryMotion.SWIPE_SETTLE_SPRING` (tới hạn hoặc gần tới hạn, không nảy quá).

- [x] **Step 1:** `LibraryOverlayController`: thay `ValueAnimator` bằng một `SpringProgress` (`onUpdate = setProgress` có kẹp `0..1`, `onSettled` = `settle(open)`); ở `ACTION_UP` truyền `startVelocity = -vx / width` (đơn vị `p` mỗi giây). `snapTo(animate = true)` dùng cùng spring với vận tốc 0. `isAnimatingOrDragging` = đang kéo hoặc `spring.isRunning`. Hủy spring khi Activity hủy.
- [x] **Step 2:** `AppLibraryMotion`: thêm `SWIPE_SETTLE_SPRING = SpringProgress.Spec(stiffness = 600f, dampingRatio = 1f)` (điểm xuất phát chưa đo cảm giác, ghi KDoc như các hằng AP).
- [x] **Step 3:** Build xanh. Commit `fix: carry the release velocity into the Library swipe settle`.
- [x] **Step 4: Kịch bản kiểm (Live 4; D2-b, D2-c, D2-e):** log `UP` cho thấy hoạt ảnh bắt đầu ở tốc độ gần `|vx|`; Library và Home vẫn khớp từng khung; vuốt chậm không tệ hơn; vuốt về (đóng) cũng không đứt vận tốc.

---

### Task 7: D2 — nhận cử chỉ Library khi pager còn trượt về trang cuối (H1)

**Bắt buộc kiểm tay:** **có** — chạm/cử chỉ (mục 4.2).
**Files:** `HomePagerController.kt`, `AppLibraryController.kt`, `LibraryOverlayController.kt` (chỉ nếu `MOVE` cần đổi).
**Interfaces:** Consumes: `Host.canOpen()`. Produces: `isSettledOnLastPage` mới nghĩa: `currentItem == last && scrollState != DRAGGING` (kể cả `SETTLING`, không kể `pagerFlip`).

- [x] **Step 1:** `HomePagerController.isSettledOnLastPage`: định nghĩa lại như trên, cập nhật KDoc (giữ tên, không đụng `HomeActivity`). **Phát hiện khi làm:** chạm lúc pager `SETTLING` thì RecyclerView chặn ngay và đưa pager sang `DRAGGING`, nên `canOpen()` gọi lại ở `ACTION_MOVE` sẽ hủy ứng viên oan; vì vậy `AppLibraryController.onTouch` ghi `pagerQualifiedAtDown = isPagerSettledOnLastPage()` **chỉ ở `ACTION_DOWN`** (router gọi trước khi cây view thấy DOWN) và `canOpenLibrary()` dùng cờ đó; các điều kiện khác (menu, kéo icon, panel, tìm kiếm) vẫn kiểm lại ở `MOVE`. `LibraryOverlayController.onTouch` không có chỗ nào khác đòi pager IDLE.
- [x] **Step 2:** Xác minh bằng log Task 5 rằng sau `TAKEOVER` + `ACTION_CANCEL` giả, pager về `IDLE` đúng lưới (SnapHelper); nếu pager đứng lệch thì dừng và báo điều phối (không tự thêm `stopScroll`/`scrollToPosition`).
- [x] **Step 3:** Build xanh. Commit `fix: let the Library swipe start while the pager settles on the last page`.
- [x] **Step 4: Kịch bản kiểm (Live 4; D2-a, D2-d):** hai flick cách 120/200/300 ms: Library mở ở mọi lần (trước: có lần không); vuốt phải ở trang cuối vẫn lật về trang trước; kéo icon (edit mode) ở trang cuối không bị Library cướp (TL1 Task 9).

---

**Task 7 — vòng 2/5 (2026-11-11, chủ dự án từ chối FX7):** "ở tốc độ nhanh phải vuốt 2 lần ở trang cuối mới mở được App Library; từ trang 1 tới App Library chưa tới 2 giây kể cả hai cú vuốt đó". Điều phối dựng bộ bơm `tools/Swiper.java` (một tiến trình, `MOVE` mỗi 8 ms, khoảng nghỉ chính xác) và đo lại ở tốc độ của chủ dự án: 6 × `900,1300,150,1300,80,250` từ trang 1 (4 trang, ~2,1 s). Kết quả log `canOpen` mỗi `DOWN` (`FX7D2`): `main`: #1 cur=0 state0, #2 cur=0 state2, #3 cur=1 state2, #4 cur=2 state2, #5 cur=3 state2 `settledFn=false` (từ chối), #6 cur=3 state0 → `TAKEOVER`: 2 cú vuốt ở trang cuối (tái hiện đúng lời chủ dự án). Bản FX7 Task 7 vòng 1: #4 cur=2 state2 → từ chối (`cand=NONE`), #5 cur=3 state2 → mở: vẫn phí một cú vuốt. **Nguyên nhân (đo được):** `currentItem` TRỄ trong lúc `SETTLING`: DOWN #4 thấy cur=2 dù ba cú vuốt đầu đã nhắm tới trang cuối (cur ở DOWN #2 còn 0 sau cú vuốt đầu; chưa rõ vì sao `onPageSelected` tới trễ tới vậy, nghi bản debug chậm khung; sửa không dựa vào `currentItem`).

- [x] **Step 5 (vòng 2): đích của lần trượt thay `currentItem`.** `HomePagerController`: `onPageScrolled` tính `progress = position + positionOffset` và, khi `scrollState == SETTLING`, `settleTargetPage` = làm tròn lên nếu `progress` đang tăng, xuống nếu giảm; xóa về -1 ở mỗi lần đổi trạng thái cuộn. `isSettledOnLastPage` = không `DRAGGING` và (đang `SETTLING` có đích thì đích == trang cuối, ngược lại `currentItem == trang cuối`).
- [x] **Step 6 (vòng 2): không kẹt ở trang áp chót.** Chạm lúc pager còn trượt đã dừng cú trượt đó ngay ở `DOWN` (RecyclerView chặn), rồi `ACTION_CANCEL` giả đưa về `IDLE` và SnapHelper căn về trang GẦN NHẤT (có thể áp chót). Nên sau `TAKEOVER`, `HomeTouchRouter` gọi `HomePagerController.finishSettleOnLastPage()`: `post { if (currentItem != last) setCurrentItem(last, true) }`, để pager trượt nốt tới trang cuối bên dưới lớp Home đang trượt đi (ngoại lệ có chủ đích của quy tắc B6, ghi trong KDoc).
- [x] **Step 7 (vòng 2): Kịch bản kiểm (Live 4, `Swiper`; D2-a):** chuỗi 6 × `900,1300,150,1300,D,G` từ trang 1 với `D` ∈ {60, 80, 120}, `G` ∈ {150, 250, 350}: Library mở ở **cú vuốt đầu tiên** ở trang cuối, 5/5 lần mỗi tổ hợp; log `canOpen` ở DOWN thứ tư/năm thấy `pagerState=2` mà vẫn `canOpen=true`; sau khi đóng Library, pager ở trang cuối (không kẹt ở áp chót); không tệ hơn ở vuốt chậm và vuốt phải.

---

### Task 7b: D2 — bộ đo vận tốc phải thấy cả cử chỉ (vòng 2)

**Bắt buộc kiểm tay:** **có** — cử chỉ vuốt (mục 4.2).
**Files:** `LibraryOverlayController.kt`.

**Vì sao:** log `UP` của cả `main` và FX7 vòng 1 cho `vx = 0.0` (`startVelocityPerSec = -0.0`) ở bộ bơm `Swiper`, nên spring bắt đầu từ 0 và Library mở "chết" sau cú vuốt nhanh. Bộ đo đã được nuôi từ `DOWN` (khi chọn ứng viên) và từng `MOVE`; nguyên nhân (suy luận từ nguồn `VelocityTracker`, khớp số đo) là bộ đo xóa lịch sử khi hai mẫu cách nhau ≥ 40 ms (coi con trỏ đã dừng): luồng chính kẹt 126-223 ms lúc Library hiện lần đầu (dựng ô, H4) nên `Swiper` (WAIT_FOR_FINISH, giờ lúc bơm) bơm `MOVE` cuối và `UP` cùng một mốc sau khi kẹt, còn một mẫu sau khi xóa → 0. Ngón tay thật có giờ vật lý nên ít bị; FX8 bỏ cú kẹt này. Sửa để không phụ thuộc vào đó.

**Kết luận sau vòng 3 (2026-11-12):** `vx = 0.0` ở vòng 1 là do cùng lỗi công cụ (bộ bơm cũ đóng dấu giờ lúc bơm và chờ app xử lý xong, nên một lần kẹt luồng chính > 40 ms xóa lịch sử của `VelocityTracker`). Với bộ bơm mới (giờ phần cứng, ASYNC) và ngón tay thật thì `VelocityTracker` không bị xóa. Phần dự phòng `lastVx` (`264cd08`) **giữ lại** vì bản đã kiểm 18/18 có nó và nó vô hại: chỉ chạy khi bộ đo trả 0 mà `UP` cách `MOVE` cuối < 24 ms, tức không đổi gì với ngón thật (ngón đứng lại rồi nhấc thì `UP` cách `MOVE` cuối lâu hơn). Không còn là sửa lỗi, chỉ là gia cố nhỏ; bỏ đi cũng được nếu muốn gọn.

- [x] **Step 1:** `trackMove(ev)`: mọi `MOVE` (cả trước và sau `TAKEOVER`) đưa vào bộ đo, tính `computeCurrentVelocity` và nhớ `lastVx` (khác 0) cùng `lastMoveEventTime`; reset ở `DOWN` khi chọn ứng viên. Ở `ACTION_UP`: nếu bộ đo trả `vx == 0` mà `UP` cách `MOVE` cuối `< 24 ms` (ngón không đứng lại) thì dùng `lastVx`. Giữ `p` liên tục ở `TAKEOVER` (không đổi).
- [x] **Step 2: Kịch bản kiểm (Live 4, `Swiper` 80 ms 750 px):** log `UP`: `vxPx` ≈ -9000 px/s và `startVelocityPerSec` ≠ 0; hồi quy: vuốt chậm (800 ms) không dùng `lastVx` (`vx` thật), ngón đứng lại rồi nhấc (`vx` ≈ 0 thật) không bị thổi phồng.

---

### Task 7c: D2 — cú vuốt trang nhanh bị mất: lỗi do công cụ bơm, không phải app (vòng 3/5, ĐÃ HOÀN TÁC)

**Kết quả cuối (điều phối, 2026-11-12):** giả thuyết bên dưới là ĐÚNG: cú vuốt trang bị mất là **lỗi của bộ bơm cử chỉ**, không phải của app. Điều phối dựng lại `tools/Swiper.java` với giờ gần như phần cứng (mỗi sự kiện đóng dấu `down + k·8 ms`, bơm ASYNC để luồng chính bận không làm dồn mốc giờ) và đo lại ở tốc độ chủ dự án (6 × 750 px từ trang 1, `D` 60/80/120 ms × `G` 150/250 ms × 3 lần): **`FX7-r2-d2log.apk` (vòng 2, KHÔNG có `PageFlingAssist`) mở Library ở cú vuốt thứ 4 được 18/18 lần, mọi cú vuốt trang đều tiến một trang**; `FX7-r3-d2log.apk` 12/12 trước khi máy thứ hai được gắn vào và các lần còn lại bị ngắt (không phải lỗi bản dựng). Quyết định: `PageFlingAssist` không cần và thêm rủi ro (hoàn tất trang giả lập); commit `c2e84bf` được hoàn tác bằng `c72d8ed`. Phân tích dưới đây giữ lại làm hồ sơ.


**Bắt buộc kiểm tay:** **có** — cử chỉ vuốt (mục 4.2).
**Files:** `ui/home/controller/PageFlingAssist.kt` (mới), `HomePagerController.kt`, `HomeTouchRouter.kt`.

**Số đo vòng 2 (27 lần `Swiper`, điều phối):** Library mở ở cú vuốt thứ 4 (lý tưởng) 10/27, thứ 5 12/27, thứ 6 5/27. Phần phí nằm ở cú vuốt TRANG thường: log mỗi lần có `CANCEL_CANDIDATE fromLine=145 cand=NONE` ~95-120 ms sau `DOWN`; ví dụ `r_80_250_1` mất cú #1 (từ nghỉ, tiến độ 0 → 0,008) và #3 (`DOWN` bắt pager đang trượt ở 0,998, kết thúc 1,015).

**Giả thuyết của điều phối (đã kiểm trong code, SAI):** router gửi `ACTION_CANCEL` giả hoặc nuốt `UP` thật khi bỏ ứng viên Library nên pager không tính fling. Kiểm: `CANCEL_CANDIDATE` ở `LibraryOverlayController.cancelCandidate()` (dòng 145 của bản log = nhánh `ACTION_UP`/`ACTION_CANCEL` khi `!dragging`; in `cand=NONE` vì ở trang 1-3 `canOpen()` là false nên chưa từng có ứng viên) chỉ dọn bộ đo của Library rồi trả `Result.PASS`. `ACTION_CANCEL` giả chỉ được gửi ở `TAKEOVER` (`HomeTouchRouter`) và khi vuốt xuống mở tìm kiếm (`SearchOverlayController.onTouch`); không nhánh nào nuốt `UP` khi Library không tiếp quản. Pager luôn nhận đúng luồng sự kiện thật.

**Nguyên nhân tìm được (suy ra từ số đo + nguồn `VelocityTracker`/`RecyclerView`; CHƯA có log `EV` xác nhận trực tiếp, bản `FX7-r3-d2log.apk` ghi thêm để xác nhận):**
- Cú vuốt TRANG không đi qua mã của Library/router: `RecyclerView` của `ViewPager2` tự tính fling từ `VelocityTracker` của nó. `VelocityTracker` xóa lịch sử khi hai mẫu cách nhau >= 40 ms (cùng cơ chế đã làm `vx = 0` ở Task 7b). Khi `RecyclerView` thấy vận tốc 0 thì `SnapHelper` căn theo VỊ TRÍ: cú vuốt nhanh chưa qua nửa trang bật về trang cũ, khớp "vuốt chậm thì ổn".
- Tương quan trong 27 log (cột "tiến độ" tại `DOWN` kế tiếp, thời gian `DOWN` → `UP`): cú đầu từ nghỉ (`IDLE` → `DRAGGING`) luôn kẹt (`DOWN` → `UP` 134-170 ms cả khi `D` = 60 ms) và MẤT ở 16/18 lần với `D` = 60/80 nhưng chỉ 1/9 với `D` = 120 (còn đủ `MOVE` sau chỗ kẹt). Các cú sau (bắt pager đang trượt) mất 3 lần, đều `D` = 80 và `DOWN` → `UP` >= 111 ms; tương quan này yếu (cú đúng cũng có lúc 116-129 ms), nên ghi là suy luận.
- Vì sao `Swiper` lộ ra: `injectInputEvent(WAIT_FOR_FINISH)` chờ app xử lý xong và lấy giờ lúc bơm; một khung nặng (bản debug, vừa `DRAGGING`) > 40 ms làm khoảng hở giữa hai mẫu, `MOVE` cuối và `UP` cùng mốc sau đó. Ngón tay thật giữ giờ phần cứng (sự kiện gộp có `history`) nên không tạo khoảng hở này; **chưa rõ** cú mất của chủ dự án trên máy thật có cùng cơ chế hay không (cần log `EV`: `evT` so `t`, `hist`).

- [x] **Step 1:** Bỏ phần dở của vòng 3 lần trước (gửi lại `MOVE` gộp lần hai xuống cây view: gửi một sự kiện hai lần là rủi ro và không giải quyết mất vận tốc). Không còn gì của nó trong nhánh.
- [x] **Step 2 (ĐÃ HOÀN TÁC bằng c72d8ed):** `PageFlingAssist` (mới): nuôi một `VelocityTracker` y hệt (cùng sự kiện, cùng thuật toán) để biết trước pager sẽ không fling (`|vx|` < `scaledMinimumFlingVelocity`), cộng ước lượng vận tốc nhấc ngón riêng (`releaseVelocity`: quãng từ mẫu cuối về mẫu gần nhất cách >= 30 ms, giờ thật của hai mẫu, KHÔNG xóa lịch sử khi có khoảng hở; ngón dừng thật thì hai mẫu cùng chỗ ra ~0). Báo hoàn tất khi: pager đang `DRAGGING` ở `UP`; tracker < ngưỡng fling; `|dx|` >= 2 slop và ngang trội; vận tốc ước >= 400 dp/s cùng chiều tay; trang gần nhất vẫn là trang lúc `DOWN` (đã qua nửa trang thì `SnapHelper` tự sang); có trang kề. Cú vuốt pager tự fling được thì không bị đụng.
- [x] **Step 3 (ĐÃ HOÀN TÁC bằng c72d8ed):** `HomeTouchRouter`: nuôi `PageFlingAssist` với mọi sự kiện trên nhánh không-phiên-kéo sau khi Library trả `PASS`; `reset()` khi Library tiếp quản, phiên kéo icon, `swallowUntilGestureEnd`. Sau khi `search.onTouch` đã chuyển `UP` thật cho cây view thì gọi `complete()` → `HomePagerController.completeFling` = `setCurrentItem(from ± 1, smooth)` (qua `ScrollEventAdapter`: `onPageSelected` và trạng thái cuộn đúng; lúc đó pager đang căn về trang cũ nên cú trượt sang trang kề nối tiếp ngay).
- [x] **Step 4:** Build xanh. Commit `fix: finish a fast page swipe when the pager loses its velocity`.
- [x] **Step 5 (kết quả: bơm giờ phần cứng, bản vòng 2 không có `PageFlingAssist` đã đạt 18/18, nên bước hoàn tác; xem đầu Task 7c). Kịch bản ban đầu (ma trận 27 lần, bản `FX7-r3.apk`):** mọi cú vuốt trang tiến đúng một trang; Library mở ở cú vuốt thứ 4 >= 26/27; vuốt chậm, vuốt phải, đóng Library không đổi. Bản `FX7-r3-d2log.apk`: dòng `ASSIST_UP`/`ASSIST_COMPLETE` cho thấy `trackerVx` ~0 và `releaseVx` lớn ở các cú vốn mất; `EV` cho thấy khoảng hở `evT`.

**Rủi ro đã biết (chưa kiểm trên máy):** (1) `setCurrentItem(smooth)` dùng hoạt ảnh mặc định của `ViewPager2` (ngắn hơn `PageMotion` ~450 ms) cho các cú được hỗ trợ; (2) nếu cú mất do cơ chế khác (không phải `VelocityTracker`), `PageFlingAssist` không kích hoạt (tracker nhân bản báo vận tốc lớn) và ma trận không đổi: khi đó cần log `EV`.

---

### Task 7d: D2 — spring mở/đóng Library ngắt được (vòng 4/5)

**Bắt buộc kiểm tay:** **có** — cử chỉ vuốt qua lại giữa trang cuối và Library.
**Files:** `LibraryOverlayController.kt`.

**Vì sao (chủ dự án: "chuyển qua lại giữa trang cuối và App Library vẫn chưa tốt"):** điều phối tái hiện bằng `Swiper` mới: từ trang cuối, vuốt 80 ms trái/phải xen kẽ; cách 400 ms đúng 6/6, cách 250 ms thì cú #2 (phải, đóng) bị rơi: `DOWN` rơi vào lúc spring mở của cú #1 còn chạy (tới ~t+360 ms) mà `onTouch(DOWN)` có luật `if (settling) return PASS` (giữ từ Task 6), nên không có ứng viên, không `TAKEOVER`, không `UP`; mọi cú sau lệch một nhịp (cú trái kế tiếp bị coi là sai chiều khi đang mở). Đọc lại log `tog_250.log` khớp: `DOWN` thứ hai không được ghi (log đặt sau luật đó), khoảng 675 ms giữa hai `DOWN` được ghi = hai chu kỳ 335 ms.

- [x] **Step 1:** `Candidate.GRAB`: `DOWN` lúc `settling` không còn bị bỏ qua mà thành ứng viên GRAB (nếu `canClose()`). Spring KHÔNG bị đụng ở `DOWN`: nó chạy tiếp tới khi cử chỉ vượt slop, nên chạm nhẹ hay vuốt dọc thì spring tự về đích cũ (Library không kẹt giữa chừng, không cần "dọn về đầu gần nhất"; quyết định: tiếp tục đích đang chạy thay vì về đầu gần nhất, vì đó là ý định của cú vuốt trước).
- [x] **Step 2:** Vượt slop ngang: GRAB nhận CẢ HAI chiều (trái cần `canOpen()`, phải cần `canClose()`, không thì bỏ ứng viên và spring chạy tiếp); tiếp quản = `settling = false`, `spring.cancel()` (không gọi `onSettled`), `baseProgress = progress` hiện tại, kéo tiếp theo `baseProgress - dx / width`; thả tay chạy spring mới với vận tốc mới như mọi cử chỉ. Nếu spring đã tự dừng giữa `DOWN` và lúc vượt slop thì GRAB đổi thành OPEN/CLOSE thường (đúng chiều mới tiếp quản, không cướp cú vuốt phải của pager ở trang cuối). `HomeTouchRouter` không đổi: sau `TAKEOVER` vẫn một `ACTION_CANCEL` + `finishSettleOnLastPage` (pager đã ở trang cuối nên không làm gì); Home và Library cùng một `p` nên giữ khớp.
- [x] **Step 3:** Build xanh. Commit `fix: make the Library open/close spring interruptible`.
- [ ] **Step 4: Kịch bản kiểm (Live 4, `Swiper` mới):** từ trang cuối, trái/phải xen kẽ, cách 150/250/400 ms, `D` 60/80/120 ms: mọi cú vuốt đều đổi trạng thái (18/18 chuỗi); ma trận trang 1 → Library vẫn 18/18; nhấn nhẹ lúc Library đang trượt: Library tới đích cũ, không kẹt. Log `FX7-r4-d2log.apk`: `DOWN cand=GRAB settling=true` rồi `TAKEOVER cand=GRAB` với `p` giữa chừng.

---

### Task 8: D2 — làm nóng bố cục Library một lần lúc rảnh (H4)

**BỎ (điều phối chốt 2026-11-11, sau Task 5):** FX8 (PR #39) đã chuyển việc dựng ô Library sang idle handler trước lần hiện đầu, nên làm nóng ở đây sẽ động vào `CategoryPageView`/bố cục Library hai lần. Các Step dưới đây giữ để tham khảo, **không làm**. Đo lại H4 sau khi FX8 merge; Task 9 vẫn đo khởi động lạnh và lần vuốt đầu.

**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng (mục 4.2): quyết giữ hay bỏ bằng số khung và số đo khởi động.
**Files:** `AppLibraryController.kt`.
**Interfaces:** Consumes: `GridStatus.Ready`. Produces: `library_view` `INVISIBLE` (đã đo/bố cục) thay `GONE` sau khi làm nóng.

- [ ] **Step 1:** Sau khi `gridStatus == Ready` và Home vẽ khung đầu, đăng ký một `MessageQueue.IdleHandler` (một lần): đặt `libraryView.visibility = INVISIBLE` (translationX đã là `W` ở `p = 0`) để lần đo/bố cục/`onSizeChanged` đầu của Library xảy ra lúc rảnh, không ở khung chuyển tay. `applyLibraryProgress`: `p == 0` thì `INVISIBLE` nếu đã làm nóng, ngược lại `GONE` như cũ; `p > 0` thì `VISIBLE`. Gỡ handler khi Activity hủy.
- [ ] **Step 2:** Build xanh. Commit `perf: lay out the Library once while idle so the first swipe does not`.
- [ ] **Step 3: Kịch bản kiểm (Live 4; D2-b, D2-f):** lần vuốt đầu sau `force-stop` không còn khung dài ở `onSizeChanged`; khởi động lạnh (debug, 5 lượt xen kẽ với `main-hook.apk`) không tệ hơn quá nhiễu; `dumpsys meminfo` không tăng đáng kể. Không đạt thì `git revert` commit này.

---

### Task 9: D2 — đo sau sửa

**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng (mục 4.2).
**Files:** không (chỉ đo; log tạm nếu cần).

- [x] **Step 1:** Lặp đúng bộ kịch bản của Task 5 trên APK nhánh (`FX7-d2.apk`, sạch) và trên `main-d2.apk` xen kẽ (`A B A B A B`), cùng phiên: độ dời Library/Home từng khung, `framestats` (tổng khung, khung > 33 ms, p50/p95, khung dài nhất), khởi động lạnh. Điền bảng "trước/sau" vào Nhật ký và knowledgement; đối chiếu tiêu chí đạt (quyết định 10).

---

**Kết quả Task 9 (điều phối, Live 4, debug, 2026-11-11 ~16:20; 4 flick liên tiếp từ trang 1 vào Library, xen kẽ `main-d2.apk` và `FX7-d2log.apk`):**

| Ca | Bản | Log `UP` | Janky | Δp lớn nhất mỗi khung |
|---|---|---|---|---|
| 80 ms | `main` | `vx = -9375`, `v0 = 8331 px/s` (tốc độ cố định) | 37,4 % | 0,232 |
| 80 ms | FX7 | `vxPx = -9374,9`, `startVelocityPerSec = 8,68` (= tốc độ ngón) | 24,8 % | 0,287 |
| 120 ms | `main` | `vx = -6250`, `v0 = 8331 px/s` | 20,0 % | 0,236 |
| 120 ms | FX7 | `vxPx = -6250`, `startVelocityPerSec = 5,79` | 26,7 % | 0,176 |

Phân vị 99 là 150 ms ở cả bốn lượt = lần dựng ô lần đầu của Library mà FX8 (#39) bỏ. **Vận tốc đã được mang vào hoạt ảnh (H3 sửa xong); jank còn lại là của bản debug và phần FX8.** Chưa đo lại khởi động lạnh (Task 8 bỏ) và chưa đo lại sau khi FX8 merge.

---

### Task 10: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không
**Files:** `docs/knowledgements/FX7-home-gestures.md` (tạo), plan này (cập nhật).

- [x] **Step 1:** Viết knowledgement theo mẫu mục 8: `sequenceDiagram` cho "uiState đổi giữa lúc vuốt → `renderPages` → `settlePageSelection` → hoãn → `IDLE` → khôi phục theo `pageId`"; `sequenceDiagram` cho "menu mở chen vào hạ cánh → `DragLayer.cancelSettle` → `finishSettle` → `onDragSettled`"; `flowchart` cho "`HomeTouchRouter.dispatch` theo `pointerId`" (MOVE, POINTER_DOWN, POINTER_UP, UP); bảng số đo trước/sau (B6: độ dời tối đa giữa hai khung, số khung lệch; C14: ảnh chụp ô; C12: nội dung menu; C18: kết quả `sendevent` hoặc "chỉ code + chủ dự án kiểm tay", ghi rõ giới hạn); mục "Bài học cho AGENTS.md" (xem "Đề xuất cập nhật tài liệu chung").
- [x] **Step 2:** Điền "Bằng chứng Review Focus" và "Kết quả kiểm chứng"; điều phối rebase, build xanh, rà diff tổng (`git diff main -- app` chỉ nằm trong vùng file, `DragHelper.kt` chỉ KDoc), `grep -rn FX7TMP app/src` rỗng; ghi vào hàng MV (mục 10) điều FX7 đã/không chạm.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11, 1080x2340, Os26Launcher là launcher mặc định, mức kính Ép cao). **Bản:** debug; ghi `git rev-parse --short HEAD` của từng APK. Chủ dự án đã cho điều phối kiểm lại trên Live 4 (2026-11-11); ngay trước mỗi lượt điều phối kiểm máy đang rảnh và sao lưu lại. Hai ngón (C18) là phần duy nhất có thể cần tay chủ dự án.

### A. Chuẩn bị (một lần)

1. Ghi trạng thái ban đầu: `adb shell cmd uimode night` (**trả về đúng như vậy ở cuối**), mức kính ở Cài đặt.
2. Sao lưu dữ liệu app (cùng cách FX2 mục A.2; DG: lấy cả `files/`, so md5 từng file kể cả `-wal`/`-shm`): `cmd /c "adb exec-out run-as gambi.global.os26launcher tar cf - databases files no_backup > %TEMP%\fx7-backup.tar"`, ghi md5 các file (8 file). Cuối bài: `force-stop`, giải nén trả lại, so md5; **lệch thì trả từ bản sao lưu**.
3. Dựng APK (ra `%TEMP%\fx7-apk\`, ngoài repo): `main-hook.apk` (`main` + hook), `branch-hook.apk` (commit cần kiểm + hook), `branch.apk` (commit sạch, cho hồi quy cuối). **Hook tạm** (`%TEMP%\fx7-hooks.patch`, nhãn `FX7TMP`): H2 = `SpringProgress.Spec.SETTLE` độ cứng `600f` → `8f` (hạ cánh kéo dài ~2,7 s); H3 = `delay(4000)` ngay trước `getAppShortcuts(...)` trong `HomeViewModel.onIconLongPressed`. Patch lệch ngữ cảnh sau Task 3 thì tạo lại. D2 có patch log riêng `%TEMP%\fx7-d2-hooks.patch` (Task 5) và hai APK: `main-d2.apk` (`main` + log D2), `FX7-d2.apk` (commit D2 + log D2).
4. APK thử `dev.fx7.icontest`: dựng như audit mục A4 "Cách thử lại" (manifest `hasCode="false"`, activity `android.app.Activity`, icon PNG, `aapt2 compile/link` + `zipalign` + `apksigner` khóa debug); cài bằng `adb install`. Script đo B6 ngoài repo `b6_offset.py` (chạy `python -I`): từ chuỗi khung PNG (`ffmpeg -i b6.mp4 -vf fps=30 frames\f_%03d.png`), mỗi khung lấy dải ngang cao ~400 px qua hàng icon thứ hai, so với dải của khung đứng yên trước khi vuốt bằng tương quan chéo một chiều (dịch -1080..0 px, `numpy`) → độ dời ngang từng khung (px) → in độ dời tối đa và mức giảm lớn nhất giữa hai khung liền nhau.
5. Toạ độ: ảnh chụp (`adb exec-out screencap -p`) và `uiautomator dump` (nhãn ẩn nhưng có `content-desc`; **không đọc được khi icon rung**, `lessons/home-pager-animation.md` R1: ở edit mode dùng ảnh). Ghi toạ độ icon X (ô thả), icon Y (ô khác trên cùng trang), nút "Xong", ô trống, dock. Cử chỉ nhấn giữ bằng `input swipe x y x y 700` (một lệnh duy nhất; `input motionevent` tách lệnh có thể tới sau mốc nhấn giữ, `lessons/drag-and-layout.md` GP).
6. Kéo-thả bằng lệnh (một lệnh `adb shell` chứa chuỗi, `lessons/home-pager-animation.md` PL): `input motionevent DOWN x y; sleep 0.8; input motionevent MOVE (x+4) y; input motionevent MOVE ... ; input motionevent UP x2 y2` (có `MOVE` 1-4 px giữa lúc giữ). Mỗi lệnh `input` mở một tiến trình mất vài trăm ms: đã tính vào các mốc thời gian dưới đây.

### B. Baseline trên `main-hook.apk` (một lượt, xác nhận lỗi còn tái hiện trước khi sửa)

B6-a, B6-b, C14-a, C12-a chạy trên `main-hook.apk`; D2 baseline (D2-a, D2-b, D2-f) chạy trên `main-d2.apk` ở Task 5; C18-a chạy nếu `sendevent` dùng được. Ghi kết quả vào Nhật ký (số "trước" cùng phiên với số "sau").

### C. Sau khi sửa (theo thứ tự thao tác tự nhiên; APK nhánh)

| # | Thao tác (adb) | Kỳ vọng sau sửa | Trước sửa |
|---|---|---|---|
| B6-a | Đứng ở trang 2. Một lệnh: `adb shell "screenrecord --time-limit 7 --bit-rate 6000000 /sdcard/fx7_b6.mp4 & sleep 1; input swipe 900 1300 150 1300 2500 & sleep 0.8; pm disable-user --user 0 dev.fx7.icontest; wait"`; `adb pull`; `pm enable dev.fx7.icontest`; xóa file trên máy; trích khung, chạy `b6_offset.py` | Độ dời tăng đều theo ngón tay tới khi nhả; mức giảm lớn nhất giữa hai khung khi ngón còn trên màn **≤ 30 px**; sau khi nhả trang đổi/quay về đúng như vuốt thường; trang hiện cuối cùng đúng (trang 3 nếu vượt nửa quãng) | Độ dời 0 → ~400 px rồi **về 0 ở khung ~70** (ngón còn kéo), rồi tăng lại |
| B6-b | Như B6-a nhưng `input swipe ... 1500` và mốc `pm disable-user` hiệu chỉnh từ độ trễ đo ở baseline (khoảng 0,5-1,5 s theo audit) để giật rơi vào 0,1-0,3 s sau khi nhả (lúc `SETTLING`) | Chuyển động trượt liên tục tới đích, không nhảy; đích đúng (cùng đích với lượt không có trigger) | Nhảy thẳng tới đích hoặc về 0 giữa chừng trượt (hủy hoạt ảnh) |
| B6-c | Đứng yên ở trang 2 (rảnh), `pm disable-user` rồi `pm enable`; ảnh chụp | Vẫn ở trang 2; chấm trang (pill) đúng trang 2 | Không đổi (hồi quy) |
| B6-d | Edit mode, kéo một icon ra mép phải ở trang cuối (`motionevent`, giữ 1,2 s sát mép) | Pager lật sang trang trống tạm bằng FakeDrag như trước (nhánh `pendingFlipToNewPage`); thả lên trang đó: icon ở trang mới | Không đổi (hồi quy) |
| B6-e | Edit mode → "Tiện ích" → bấm thêm widget ở trang khác trang đang xem | Pager cuộn mượt tới trang chứa widget (G2c Task 9) | Không đổi (hồi quy) |
| B6-f | `cmd uimode night no` rồi `yes` khi đang ở trang 3 | Home dựng lại ở trang 3 (như trước sửa) | Không đổi (hồi quy) |
| C14-a | `branch-hook.apk` (H2). Edit mode, kéo icon X sang ô trống khác cùng trang và nhả; **ngay sau đó** `input tap` nút "Xong", rồi `input swipe Yx Yy Yx Yy 700` (nhấn giữ icon Y); menu mở trong lúc ảnh X còn bay (< 2,7 s); chụp ảnh, `KEYCODE_BACK` đóng menu, chụp ảnh sau 4 s | Ô X **hiện icon** (cắt vùng ô bằng `ffmpeg -vf crop`, so với ảnh trước khi kéo); không có ô trống | Ô X **trống** (alpha 0) tới phiên kéo sau |
| C14-b | Không hook, thả bình thường; kéo thả liên tiếp hai lần nhanh; nhấn giữ → kéo luôn (đường B) | Ảnh hạ cánh trượt vào ô, ô hiện đúng lúc, không ô kẹt ẩn | Không đổi (hồi quy) |
| C12-a | `branch-hook.apk` (H3). Thư mục có Chrome và một app không shortcut (tạo thư mục thử nếu cần; DB sẽ được trả). `input swipe` giữ Chrome trong panel thư mục, `input tap` vùng tối đóng menu, giữ app kia (< 4 s sau lần đầu); chờ 5 s; `uiautomator dump` + ảnh | Menu app kia **không** có "Thẻ ẩn danh mới"/"Thẻ mới"; chỉ các mục cố định (và shortcut của chính nó nếu có) | Menu app kia hiện shortcut của Chrome |
| C12-b | Không hook: nhấn giữ Chrome ở dock; ở thư mục; đóng rồi mở lại cùng Chrome | Luôn hiện "Thẻ ẩn danh mới", "Thẻ mới" (audit đợt 2) | Không đổi (hồi quy) |
| C18-a | Hai ngón (xem "C18 — cách thử và giới hạn"): ngón 1 giữ icon X, kéo tới ô P, ngón 2 chạm xuống chỗ trống, **nhấc ngón 1**, ngón 2 di chuyển, nhấc ngón 2 | Icon X thả tại ô P (chỗ ngón 1 nhấc); di chuyển ngón 2 không làm icon nhảy; nhấc ngón 2 không làm gì | Icon nhảy theo ngón 2 và thả ở chỗ ngón 2 |
| C18-b | Ngón 1 kéo X; ngón 2 chạm rồi nhấc (không di) | Phiên không mất, icon vẫn theo ngón 1 và thả khi nhấc ngón 1 | Không đổi (hồi quy) |
| C18-c | Hồi quy một ngón: kéo giữa hai trang (lật mép), vào dock, lên thư mục (gộp), thả ngoài vùng (bay về), kéo từ menu (đường B), kéo widget từ bảng chọn | Mọi thứ như `main` | — |
| C18-d | Giữa lúc kéo, vuốt xuống từ mép trên (thanh thông báo) | Phiên hủy như trước (ảnh bay về), không kẹt `draggingItemId` | Không đổi (hồi quy) |
| D2-a | Pager về trang cuối (vuốt nhanh từ trang 1), rồi: hai flick Home → Library cách 120/200/300/500 ms (một lệnh `adb shell`: `input swipe 900 1300 150 1300 80; sleep 0.12; input swipe 900 1300 150 1300 80`, thay `sleep`); `logcat -s FX7D2` | Library mở ở mọi lần; log `pagerState=2` ở DOWN vẫn mở | Có lần không mở/đứng giữa (H1) |
| D2-b | Từ trang cuối đứng yên: `input swipe 900 1300 150 1300 30` (và 80, 120, 250 ms), quay màn hình, script độ dời (Library và Home) + `framestats` | Chênh độ dời Library−Home ≤ 8 px mỗi khung; `p` không nhảy quá ~0,25 giữa hai khung ở ca ≥ 80 ms; hoạt ảnh bắt đầu ở tốc độ ngón (log `v0` ≈ `\|vx\|`); không khung > 100 ms quanh chuyển tay | Home ~100 px rồi đứng, Library ~360 px một khung; chuỗi 147 + 79 + 38 ms |
| D2-c | Vuốt chậm (`input swipe ... 800`) và vuốt về (đóng) ở các vận tốc | Không tệ hơn `main` (số khung > 33 ms, p95 trong nhiễu đã đo) | Không đổi (hồi quy) |
| D2-d | Hồi quy cử chỉ: vuốt phải ở trang cuối (lật về trang trước); edit mode kéo icon sát mép phải ở trang cuối (ghost page, TL1 Task 9); nhấn giữ icon ở cột phải trang cuối rồi kéo | Không bị Library cướp; pager không đứng lệch sau khi Library mở rồi đóng | Không đổi |
| D2-e | Đóng Library bằng vuốt phải nhanh, Back, Home | Đóng mượt, Home về đúng chỗ; kính dock chụp lại đúng một lần | Không đổi |
| D2-f | `force-stop`, mở Home, vuốt tới Library **lần đầu** rồi lần hai; khởi động lạnh 5 lượt xen kẽ với `main-hook.apk` (debug) | Lần đầu không có khung dài ở `onSizeChanged`; khởi động lạnh không tệ hơn quá nhiễu | Lần đầu có khung dài (H4) |

Hồi quy rộng: vuốt trang bình thường (nhanh, chậm, fling) và chấm trang; mở/đóng Library (vuốt cuối trang), tìm kiếm (vuốt xuống); HB (Home đóng menu giữ, tìm kiếm, Library, thư mục); mở thư mục, kéo icon ra khỏi thư mục; nhấn giữ khoảng trống vào edit mode (GP); `Activities: 1` sau 5 lần vào/thoát edit mode.

**C18 — cách thử và giới hạn.** `adb shell input` chỉ có một con trỏ (`motionevent` chỉ DOWN/MOVE/UP/CANCEL cho một ngón), nên hai ngón không dựng được bằng `input`. Cách thử đề xuất là `sendevent` ghi thẳng vào thiết bị cảm ứng: `adb shell getevent -lp` tìm thiết bị có `ABS_MT_POSITION_X`, ghi `max` của X/Y (có thể khác 1080x2340) và có `ABS_MT_SLOT` hay không (có = giao thức B). Giao thức B, mỗi gói kết thúc bằng `sendevent $D 0 0 0`: ngón 1 xuống `3 47 0` (slot 0), `3 57 101` (tracking id), `3 53 X`, `3 54 Y`, rồi `1 330 1` nếu thiết bị báo `BTN_TOUCH`; ngón 2 xuống `3 47 1`, `3 57 102`, `3 53 X`, `3 54 Y`; nhấc ngón 1 `3 47 0`, `3 57 -1`; nhấc ngón 2 `3 47 1`, `3 57 -1` rồi `1 330 0`. Giữ 0,8 s sau khi ngón 1 xuống (qua mốc nhấn giữ), di chuyển nhiều bước nhỏ tới ô P, tất cả trong một script `sh` đẩy lên `/data/local/tmp`. **Chưa kiểm trên Live 4** (`adb shell` có được ghi `/dev/input/event*` hay không, giao thức A hay B). Giao thức A (không có `ABS_MT_SLOT`): mỗi ngón gửi `3 53 X` và `3 54 Y` rồi `0 2 0` (`SYN_MT_REPORT`), hết gói `0 0 0`. Nếu `sendevent` không dùng được, phần C18-a/C18-b chỉ có **review code + hồi quy một ngón**; điều phối ghi rõ giới hạn đó vào Nhật ký và nhờ chủ dự án thử hai ngón bằng tay (kịch bản như C18-a/b, khoảng 2 phút) trong bài kiểm cuối. Không coi C18 là "đã đo" nếu chỉ có đọc code.

Cuối bài kiểm: `cmd uimode night yes`, mức kính như ban đầu, `pm enable dev.fx7.icontest` rồi `adb uninstall dev.fx7.icontest`, xóa `/sdcard/fx7_b6.mp4`, tiến trình `sendevent` còn treo (nếu có), trả DB từ bản sao lưu và **so md5 8 file**, `force-stop`, cài lại APK debug của chủ dự án nếu máy từng cài APK khác (kể cả khi `main-hook.apk` còn trên máy).

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Vsmart Live 4 (Android 11, debug, launcher mặc định), 2026-11-11, điều phối. Đạt (đo được): B6-a (không snap-back), B6-c, B6-f, D2 A/B (vận tốc được mang vào hoạt ảnh), chiều đóng Library không crash. Chưa chạy trên máy, chuyển cho chủ dự án: C14 và C12 (hook APK), C18 hai ngón, H1 (hai flick nhanh liên tiếp từ trang áp chót), B6-d (lật trang tạm), B6-e (cuộn tới trang widget); "vuốt nhanh vào App Library từ trang 1 vài lần, Library phải đi theo tốc độ ngón tay". Nhánh `fix/fx7-home-gestures` (mã đến `dc9a9cb`).

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | 2 | `DragLayer.cancelSettle()` lấy `settleOnEnd` ra biến cục bộ, xóa, rồi gọi (try/catch, log `Log.w`) sau khi đặt lại `settling`/`drawScale`/`alpha`; KDoc `DragHelper.DragVisual.settleTo` sửa theo hợp đồng mới (code `DragHelper` không đổi). `assembleDebug` xanh | 5b53adc | Chưa kiểm Live 4 (cần hook H2 làm chậm hạ cánh; gộp bài kiểm cuối C14-a, C14-b) |
| 2026-11-11 | 3 | `HomeViewModel.onIconLongPressed`: kết quả shortcut chỉ áp khi `menu.itemId`, `menu.container`, `menu.key` đều khớp lần gọi (chỉ một điều kiện, không đụng khối ảnh nền của FX1, không đổi chữ ký). `assembleDebug` xanh | 88fc565 | Chưa kiểm Live 4 (cần hook H3 `delay(4000)`; gộp bài kiểm cuối C12-a, C12-b) |
| 2026-11-11 | 4 | `HomeTouchRouter.dispatch`: `dragPointerId` (= `getPointerId(0)` của sự kiện đầu tiên thấy phiên, xóa khi phiên kết thúc/`session == null`); `MOVE` đọc toạ độ theo `findPointerIndex` (`getRawX(int)` API 29, dự phòng `getX(i) + (rawX - getX(0))`); `POINTER_DOWN` bỏ qua; `POINTER_UP` của đúng ngón kéo = `dropAt` tại ngón đó + `onFinished` + `swallowUntilGestureEnd` (nuốt tới `UP`/`CANCEL`, `DOWN` mới thì bỏ cờ); `POINTER_UP` ngón khác bỏ qua; nhánh `DOWN` sót, `UP`, `CANCEL` giữ nguyên. `assembleDebug` xanh | f76382a | Chưa kiểm Live 4 (hai ngón: `sendevent` chưa kiểm, xem "C18 — cách thử và giới hạn"; hồi quy một ngón C18-c, C18-d) |
| 2026-11-11 | 5 | Dựng bản đo D2 (không có code để commit): patch log tạm `FX7TMP`/tag `FX7D2` (5 file: `LibraryOverlayController` DOWN/MOVE/TAKEOVER/UP/`cancelCandidate` kèm số dòng, `AppLibraryController` `canOpen` kèm `pagerState`, `P` mỗi lần `applyLibraryProgress`, `LIBRARY_FIRST_VISIBLE`, `HomePagerController` `PAGER_STATE`, `CategoryPageView` `LIB_SIZE_START/END` kèm thời gian, `HomeTouchRouter` `ROUTER_TAKEOVER`) áp lên **`main` 723affd** → `apk/main-d2.apk`; patch `apk/fx7-d2-hooks.patch`. Thêm: `apk/FX7-tasks1-4.apk` (nhánh, sạch), `apk/FX7-tasks1-4-hooks.apk` và `apk/main-723affd-hooks.apk` (hook H2 làm chậm hạ cánh `SETTLE` 600 → 8, H3 `delay(4000)`; patch `apk/fx7-hooks-H2H3.patch`). Không hook nào được commit | (không có commit mã) | Chờ điều phối chạy Task 5 Step 2-3 (D2-a, D2-b, D2-f trên main-d2.apk) |
| 2026-11-11 | 6 | `LibraryOverlayController`: `ValueAnimator` + `DecelerateInterpolator` thay bằng `SpringProgress` (`AppLibraryMotion.SWIPE_SETTLE_SPRING` = 600/tới hạn); `ACTION_UP` truyền `startVelocity = -vx / width` (kẹp ±12 p/s); `p` kẹp `0..1` ở `onUpdate`; cờ `settling` (chỉ true khi ta chủ động chạy spring, nên `snapTo(progress)` đồng bộ giá trị đầu không phát cập nhật) thay `animator != null` cho `isAnimatingOrDragging` và việc bỏ qua chạm lúc đang animate; `onSettled` gọi một lần. Không hủy spring khi Activity hủy (như `ValueAnimator` cũ; spring yên sau ~0,25 s). `assembleDebug` xanh | dc4b0fc | Chờ kiểm D2-b/D2-c/D2-e trên Live 4 (flick 80/120 ms từ trang 1; log `UP` v0 ≈ |vx|) |
| 2026-11-11 | 7 | `HomePagerController.isSettledOnLastPage` = `scrollState != DRAGGING && currentItem == last` (KDoc D2/H1; tên giữ nguyên). `AppLibraryController.onTouch` ghi `pagerQualifiedAtDown` chỉ ở `ACTION_DOWN`, `canOpenLibrary()` dùng cờ đó (vì chạm lúc `SETTLING` làm RecyclerView chặn và đưa pager sang `DRAGGING`, kiểm lại ở `MOVE` sẽ hủy oan). Các điều kiện D14 khác vẫn kiểm ở `MOVE`. `assembleDebug` xanh | dc9a9cb | H1 không kiểm được bằng adb (xem bảng Task 5): chờ chủ dự án thử tay "hai flick nhanh liên tiếp từ trang áp chót"; hồi quy D2-d/D2-e. Chưa xác nhận pager về đúng lưới sau tiếp quản lúc SETTLING (Review Focus 9) |
| 2026-11-11 | 7 (vòng 2/5) | `HomePagerController`: `settleTargetPage` suy từ `onPageScrolled` (position + offset, chiều đi) khi `SETTLING`, xóa ở mỗi lần đổi trạng thái; `isSettledOnLastPage` dùng đích đó thay `currentItem` (trễ). `finishSettleOnLastPage()` gọi từ `HomeTouchRouter` sau `TAKEOVER` để pager không kẹt ở trang áp chót. `assembleDebug` xanh | 879f13c | Chủ dự án từ chối FX7 (vuốt nhanh vẫn cần 2 cú vuốt ở trang cuối); chờ điều phối đo lại bằng `Swiper` |
| 2026-11-11 | 7b | `LibraryOverlayController`: `trackMove` (mọi `MOVE` vào bộ đo, nhớ `lastVx`/`lastMoveEventTime`, reset ở `DOWN` chọn ứng viên); `ACTION_UP`: bộ đo trả 0 mà `UP` cách `MOVE` cuối < 24 ms thì dùng `lastVx`. `assembleDebug` xanh | 264cd08 | Chờ điều phối đo lại bằng `Swiper` (`vxPx` ≈ -9000, `startVelocityPerSec` ≠ 0) |
| 2026-11-11 | 7, 7b | Dựng bản kiểm vòng 2: `apk/FX7-r2.apk` (nhánh `264cd08`, sạch, không `FX7TMP`), `apk/FX7-r2-d2log.apk` (nhánh + log D2; `canOpen`/`settledFn` ghi thêm `settleTarget`, `progress`, `state`, `cur`; `UP` ghi `startVelocityPerSec`, `vxPx`; patch `apk/fx7-r2-d2-hooks.patch`) | 264cd08 | Chờ điều phối đo bằng `Swiper` (6 × `900,1300,150,1300,D,G`, D 60/80/120, G 150/250/350, 5 lần mỗi tổ hợp) |
| 2026-11-12 | 7c (vòng 3/5) | Điều tra D2 còn lại (27 lần `Swiper` vòng 2): giả thuyết "router hủy/nuốt `UP` khi bỏ ứng viên Library" **sai** (không có `ACTION_CANCEL` ngoài `TAKEOVER`/tìm kiếm); mất cú vuốt trang tương quan với kẹt luồng chính giữa cú vuốt và `VelocityTracker` xóa lịch sử >= 40 ms. Thêm `PageFlingAssist` + `HomePagerController.isDragging/nearestPage/canFlingFrom/completeFling`, nối vào `HomeTouchRouter`. Bỏ phần dở của lần trước (gửi lại `MOVE` gộp). `assembleDebug` xanh | c2e84bf | Chờ điều phối chạy lại ma trận 27 lần |
| 2026-11-12 | 7c | Dựng bản kiểm vòng 3: `apk/FX7-r3.apk` (nhánh `c2e84bf`, sạch, không `FX7TMP`), `apk/FX7-r3-d2log.apk` (nhánh + log D2 như vòng 2, thêm `EV` mỗi sự kiện chạm: `evT`, `hist`, `pagerDragging`; `ASSIST_UP`: `trackerVx`, `releaseVx`, `dx`, `from`, `nearest`; `ASSIST_COMPLETE`; patch `apk/fx7-r3-d2-hooks.patch`) | c2e84bf | Chờ điều phối đo bằng `Swiper` |
| 2026-11-12 | 7d (vòng 4/5) | Spring mở/đóng Library ngắt được: `Candidate.GRAB` (`DOWN` lúc `settling` không bị bỏ qua; spring chạy tiếp tới khi vượt slop rồi bị bắt lấy tại `progress`, hai chiều). Dựng `apk/FX7-r4.apk` (sạch), `apk/FX7-r4-d2log.apk` (log vòng 2 + `TAKEOVER` ghi `cand`/`settling`/`p`; patch `apk/fx7-r4-d2-hooks.patch`). Commit trên nhánh đã rebase cục bộ lên `main` `ea94586`, CHƯA push (chủ dự án force-push một lần ở cuối) | aed1a68 | Chờ điều phối đo xen kẽ trái/phải |
| 2026-11-12 | 7c (vòng 3/5, hoàn tác) | Điều phối bơm lại bằng `Swiper` giờ gần phần cứng + ASYNC: bản vòng 2 (không `PageFlingAssist`) mở Library ở cú vuốt thứ 4 **18/18**, mọi cú vuốt trang tiến một trang; bản vòng 3 12/12 (chạy dở vì gắn thêm máy). Giả thuyết đúng: lỗi do công cụ bơm cử chỉ (đóng dấu giờ lúc bơm + chờ xong làm khoảng hở >= 40 ms xóa `VelocityTracker`), không phải app; `vx = 0.0` ở vòng 1 cùng nguyên nhân. Hoàn tác `c2e84bf` bằng `c72d8ed` (xóa `PageFlingAssist`); giữ `lastVx` của 7b như gia cố vô hại. Tick Task 7, 7b. Dựng `apk/FX7-final.apk` (đỉnh nhánh, sạch) | c72d8ed | Chờ điều phối rebase lên `main` (đừng merge `main` vào nhánh) |
| 2026-11-11 | 6, 7, 9 | **Kiểm cuối Live 4 (điều phối ~16:20):** D2 A/B 4 flick từ trang 1 (bảng Task 9): vận tốc được mang vào hoạt ảnh; B6-c, B6-f đạt; chiều đóng không crash; C14, C12, C18 hai ngón, H1, B6-d, B6-e chưa chạy: chuyển cho chủ dự án. Điều phối review Task 6-7: OK; tick Task 6, 7, 9 | dc9a9cb | Đóng nhánh: knowledgement, bằng chứng Review Focus, `gh pr ready 38` |
| 2026-11-11 | 9 | Dựng bản kiểm cuối: `apk/FX7.apk` (nhánh `dc9a9cb`, sạch, không `FX7TMP`), `apk/FX7-d2log.apk` (nhánh + log D2 đã chỉnh theo spring: `UP` ghi `startVelocityPerSec`/`vxPx`, vẫn có `canOpen`, `MOVE`, `P`, `LIBRARY_FIRST_VISIBLE`, `LIB_SIZE_*`, `PAGER_STATE`, `ROUTER_TAKEOVER`; patch `apk/fx7-d2-hooks-branch.patch`); hook APK C14/C12 giữ từ Task 5 (`FX7-tasks1-4-hooks.apk` chưa gồm Task 6-7, dùng được cho C14/C12). Task 8 bỏ (FX8 #39 đã dời việc dựng ô Library sang idle handler) | (không có commit mã) | Chờ điều phối chạy bài kiểm cuối (B6-c..f, C14/C12 bằng hook APK, D2 trước/sau với flick 80/120 ms từ trang 1; chủ dự án: hai flick nhanh liên tiếp từ trang áp chót, C18 hai ngón) |
| 2026-11-11 | 1 | **Kiểm Live 4 (điều phối, ~16:00, B6-a: trang 2, vuốt 2,5 s, `pm disable-user` app thử lúc 0,8 s, screenrecord 30 khung/s, tương quan dải):** `main` 723affd: độ dời 0 → 316 px, rồi **về 0** khi ngón còn kéo, rồi 0 → 328 (snap-back tái hiện trên `main` thật). `FX7-task1.apk`: độ dời tăng liên tục 0 → 696 px suốt lần kéo (hai khung ngoại lệ là nhiễu tương quan giữa hai trang), rồi dừng ở trang kế; không snap-back; trang cuối đúng. **ĐẠT.** B6-c đến B6-f gộp vào bài kiểm cuối | 8d70eb1 | Tick Task 1 (đã qua review điều phối) |
| 2026-11-11 | 1 | `HomePagerController`: `selectedPageId` (ở `onPageSelected`), `restorePending`, `settlePageSelection()` (chỉ chạy khi `scrollState == IDLE` và không FakeDrag; hoãn tới `IDLE` bằng `post`; khôi phục theo `pageId` duy nhất, không thì kẹp chỉ số; hợp nhất cuộn tới trang widget G2c); bỏ `savedPage`; nhánh lật trang tạm giữ nguyên ngoài cổng rảnh; `pendingScrollIndex` tách khỏi `consumePendingScroll`. `grep setCurrentItem` chỉ còn trong `settlePageSelection`. `assembleDebug` xanh | 8d70eb1 | APK `E:\SourceTree\IosLauncher-wt\apk\FX7-task1.apk`; chờ điều phối kiểm B6-a đến B6-f |
| 2026-11-11 | Plan | **Đính chính số đo D2 (điều phối):** các số của audit D2 (Home ~100 px rồi đứng, Library ~360 px một khung, chuỗi 147 + 79 + 38 ms, khung dài 69-89 ms mỗi lần dừng, kính vẽ lại ~1,3 s) quay trên bản cũ 2026-11-10 (trước FX1-FX6) cài nhầm. D2 có thể vẫn còn (mã AP đã có trong bản đó, chủ dự án cảm nhận trên đó) nhưng mọi số "trước" phải đo lại trên `main` 723affd; Task 5 lấy `main-723affd.apk` làm baseline; giả thuyết H1-H6 giữ nguyên vì dựa trên đọc code `b1af810`/`2bac922`, chưa dựa vào số đo cũ | (commit ghi chú) | Task 5 chưa chạy |
| 2026-11-11 | Plan | **Sửa plan vòng 1/5:** thêm D2 (giả thuyết H1-H6 kèm cách xác nhận, phương án A-E, Task 5-9, vùng file, bài kiểm D2); đọc AP knowledgement/plan, `LibraryOverlayController`, `AppLibraryController`, `AppLibraryMotion`, `CategoryPageView`, `TunedPagerSnapHelper`; đo lại số dòng trên `main` 2bac922 | (commit sửa plan) | Chờ điều phối rà; D2 chọn phương án B |
| 2026-11-11 | Plan | Viết plan, đối chiếu code `b1af810` (đọc `HomePagerController`, `DragLayer`, `DragHelper`, `IconMenuController`, `HomeTouchRouter`, `IconDragGesture`, `HomeViewModel`, bytecode ViewPager2 1.1.0) | — | Chờ điều phối rà; câu hỏi Q1 ở "Quyết định đã chốt" |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Không `setCurrentItem` lúc không rảnh; `settlePageSelection()` là nơi duy nhất | `grep -n setCurrentItem HomePagerController.kt`: chỉ trong `settlePageSelection`; Live 4 B6-a: `main` 0 → 316 px rồi về 0 khi ngón còn kéo, FX7 0 → 696 px liên tục | Đạt (đo được) |
| 2 | `selectedPageId` đủ ba đường; tìm lại theo `pageId` duy nhất; kẹp chỉ số | Đọc code (`onPageSelected`, `settlePageSelection`); B6-c (idle + disable/enable app thử: vẫn trang 2) và B6-f (đổi sáng/tối ở trang 3: vẫn trang 3) đạt trên Live 4 | Đạt (đọc code + đo hồi quy); ca trang bị xóa/chèn trước trang hiện tại chưa thử |
| 3 | Hoãn tới `IDLE` không lặp vô hạn; `pendingScrollPageId` giữ TTL | Đọc code: `restorePending` bỏ khi chạy, `pendingScrollIndex` giữ TTL 3 giây; B6-e (cuộn tới trang widget) chưa chạy trên máy | Chỉ code bảo vệ; B6-e vào danh sách kiểm của chủ dự án |
| 4 | Nhánh lật tới trang tạm không bị chặn | Đọc code: nhánh `pendingFlipToNewPage` đứng trước `settlePageSelection` và không qua cổng rảnh; B6-d chưa chạy trên máy | Chỉ code bảo vệ; B6-d vào danh sách kiểm của chủ dự án |
| 5 | `cancelSettle()` gọi callback một lần, đúng thứ tự; KDoc đã sửa | Đọc code (xóa `settleOnEnd` trước khi gọi, `finishSettle` idempotent); C14 chưa kiểm trên máy (cần hook APK `FX7-tasks1-4-hooks.apk` so với `main-723affd-hooks.apk`) | Chỉ code bảo vệ; vào danh sách kiểm của chủ dự án |
| 6 | C12 so đủ ba trường; mở lại cùng app vẫn có shortcut | Đọc code (`itemId`, `container`, `key`); C12 chưa kiểm trên máy (hook `delay(4000)`) | Chỉ code bảo vệ; vào danh sách kiểm của chủ dự án |
| 7 | C18 `dragPointerId`, nuốt `POINTER_DOWN`, nuốt phần còn lại sau `POINTER_UP` | Đọc code; hai ngón không dựng được bằng `input`, chưa thử `sendevent`; chưa kiểm trên máy | Chỉ code bảo vệ; chủ dự án thử hai ngón bằng tay |
| 8 | D2 Task 6: dấu vận tốc, kẹp `p`, `onSettled` một lần | Live 4: log `UP` `startVelocityPerSec` = 8,68 (vuốt trái `vx = -9375`) và 5,79 (`vx = -6250`): đúng dấu, khớp tốc độ ngón; `main` v0 cố định 8331 px/s | Đạt (đo được) |
| 9 | D2 Task 7: `canOpen` theo trạng thái pager; một `ACTION_CANCEL`; pager về đúng lưới | Đọc code (`pagerQualifiedAtDown` ghi ở `ACTION_DOWN`); H1 không dựng được bằng adb; chiều đóng (vuốt phải nhanh, Home) không crash | Chỉ code bảo vệ phần SETTLING; chủ dự án thử hai flick nhanh liên tiếp từ trang áp chót và kiểm pager không đứng lệch |
| 10 | D2 Task 8: `INVISIBLE` sau làm nóng, `IdleHandler` một lần, khởi động không tệ hơn | Task 8 **bỏ**: FX8 (#39) đã chuyển việc dựng ô Library sang idle handler | Không áp dụng |
| 11 | Không file ngoài vùng; `DragHelper.kt` chỉ KDoc; không `FX7TMP` | `git diff 2bac922 -- app` chỉ gồm `HomePagerController`, `AppLibraryController`, `LibraryOverlayController`, `AppLibraryMotion`, `HomeTouchRouter`, `DragLayer`, `DragHelper` (KDoc), `HomeViewModel` (một điều kiện); `grep -rn FX7TMP app/src` rỗng; `FX7.apk` không chứa `FX7TMP` | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: mục kéo-thả (TT#7) thêm dòng "một phiên kéo chỉ theo ngón đã bắt đầu; nhấc ngón đó thì thả" (nếu Q1 giữ mặc định).
- `project_structure.md`: dòng `HomePagerController.kt` ghi "khôi phục trang theo `pageId`, chỉ khi pager rảnh (`settlePageSelection`)"; dòng `HomeTouchRouter.kt` ghi "theo `pointerId` của phiên kéo"; dòng `DragLayer.kt` ghi hợp đồng `settleTo`/`onEnd`.
- `room-schema.md`: không.
- D2: `lessons/home-pager-animation.md` thêm: (a) cử chỉ tiếp quản của lớp phủ phải nhận cả khi pager còn `SETTLING` về trang cuối; (b) hoạt ảnh kết thúc của lớp phủ nhận vận tốc thả tay (`SpringProgress.startVelocity`), không dùng thời lượng cố định; (c) view lớp phủ `GONE` từ đầu thì lần hiện đầu là lần đo/bố cục đầu (đo được `LIB_SIZE_END tookMs=223` trên luồng chính): FX7 không làm nóng vì FX8 (#39) đã chuyển việc dựng ô Library sang idle handler; (d) flick 30 ms của `input swipe` chỉ có 1-2 `MOVE` nên cho số đo khác tay thật (nếu H2 xác nhận); (e) nhãn kính (H5) chuyển cho FX8.
- Hàng MV (`MultiAgent-Workflow.md` mục 10): ghi FX7 đã/không chạm nghi vấn (1), (4), (5) như mục "Ngoài phạm vi".
- `AGENTS.md` / `lessons/` (đưa vào `home-pager-animation.md` và `drag-and-layout.md`; điều phối chép từ knowledgement):
  - `setCurrentItem(item, false)` của ViewPager2 với `item == currentItem` lúc không rảnh vẫn gọi `scrollToPosition` (đo bytecode 1.1.0): mọi `setCurrentItem` không do người dùng phải chờ `SCROLL_STATE_IDLE`. `AsyncListDiffer` gọi callback ngay khi danh sách bằng nhau nên mọi lần phát state đều là trigger (đọc nguồn, chưa kiểm).
  - Khôi phục vị trí pager theo `pageId`, không theo chỉ số; `pageId` không duy nhất tuyệt đối (layout nháp `0`, id tạm âm, `PageId.NEW`).
  - Hợp đồng hoạt ảnh có callback "xong" phải gọi callback cả khi bị hủy (hoặc phải ghi rõ ai dọn); người gọi đi vòng qua lớp điều phối (`IconMenuController` → `DragLayer`) là chỗ hợp đồng vỡ.
  - Phiên kéo ghi nhận `pointerId` lúc bắt đầu; đọc `ev.rawX/rawY` là đọc ngón ở chỉ số 0 (đổi khi ngón đầu nhấc); `getRawX(int)` cần API 29.
  - Đo cử chỉ: `screenrecord` + tương quan chéo để ra độ dời trang từng khung; hook làm chậm (`FX7TMP`) để tái hiện lỗi cửa sổ hẹp; `adb shell input` không có đa điểm, dùng `sendevent` (chưa kiểm trên Live 4 khi viết plan) hoặc tay chủ dự án.
