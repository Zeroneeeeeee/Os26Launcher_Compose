# Bài học: Kéo-thả và bố cục lưới

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Custom ViewGroup của lưới, trạng thái kéo-thả, dock/lưới/trang, menu nhấn giữ, bố cục và cỡ lưới.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Custom ViewGroup cho lưới icon: bẫy đã gặp (M3 Task 2-3-5)

- **`MeasureSpec` nằm trong `android.view.View`, không phải `android.view.ViewGroup`.** Import
  `android.view.ViewGroup.MeasureSpec` biên dịch lỗi `Unresolved reference`; dùng
  `import android.view.View.MeasureSpec` trong `IconGridView`/`DockView` (cả hai đều extends
  `ViewGroup` nhưng vẫn phải import từ `View`).
- **`Paint.textAlign` và `Layout.Alignment` của `StaticLayout` không được dùng cùng lúc.**
  `IconCellView` vẽ nhãn bằng `StaticLayout.Builder` với `setAlignment(Layout.Alignment.ALIGN_CENTER)`;
  nếu `TextPaint` dùng để dựng `StaticLayout` đó còn set thêm `textAlign = Paint.Align.CENTER`, hai cơ
  chế căn giữa đè lên nhau, chữ bị vẽ lệch quanh gốc x=0 (một phần bị "nuốt" ra vùng âm). Cách đúng:
  chỉ dùng `Layout.Alignment` của `StaticLayout`, không set `textAlign` trên `Paint`.
- **Giữ trang đang xem khi `ListAdapter.submitList` đổi dữ liệu** (ViewPager2 + `DiffUtil`): lưu
  `viewPager.currentItem` TRƯỚC khi gọi `submitList`, đặt lại trong callback `submitList(list) { ... }`
  (chạy sau khi `DiffUtil` áp xong), `setCurrentItem(..., false)` để không animate. Không làm vậy thì
  mỗi lần danh sách app đổi (cài/gỡ app), `ViewPager2` tự nhảy về trang 0.
- **Đọc `.width`/`.height` của 1 view ngay trong cùng lần code vừa đổi `visibility` của chính view đó
  (hoặc view cha) là không an toàn** — view vừa chuyển từ `GONE` sang hiện lại CHƯA kịp layout lại
  trong cùng lần gọi, `.width`/`.height` vẫn là giá trị cũ (có thể là 0). Gặp ở M3 Task 5: `DockView`
  nhận `GridMetrics.Cells` tính từ `home_pager.width/height`, nhưng `home_pager` bị ẩn tạm lúc
  `GridStatus.Loading` — dock khoá vĩnh viễn ở kích thước 0 sau khi chuyển sang `Ready` (không còn lần
  render nào sau để tính lại). Sửa bằng cách đo theo 1 view KHÔNG bị ẩn/hiện theo trạng thái (ở đây là
  `home_root`, luôn hiện, cùng kích thước vì `home_pager` là `match_parent` bên trong nó).
- **Icon tự vẽ bằng `Canvas` (như `IconCellView`) vẫn cần cùng kỷ luật hủy `Job` nạp icon bất đồng bộ
  như `RecyclerView` thường** (xem bẫy "icon nhảy sai dòng" ở M2 Task 3): mỗi cell giữ 1 `Job`, hủy
  trước khi nạp mới, kiểm tra lại `ComponentKey` còn khớp trước khi `bind()` kết quả trả về (đề phòng
  cell đã bị tái dùng cho app khác).

## Đọc DragHelper trước khi viết plan kéo-thả (tách từ «Viết docs»)

- **Trước khi viết plan có kéo-thả mới, đọc `DragHelper`/`IconGridView.onDragMove`** (2026-10-30): `onDragMove` thoát sớm khi ô không đổi và ngón tay đứng yên không phát sự kiện nào, nên mọi hành vi theo thời gian giữ (dwell) phải dùng `Handler.postDelayed`; mọi target đã đăng ký (lưới, dock) vẫn nhận hover dù có lớp phủ nằm trên chúng.

## M7 — chế độ chỉnh sửa & kéo-thả (Task 3-5, 2026-10-28, đo trên Vsmart Live 4)

- **Hai bẫy touch KHÁC NHAU, dễ nhầm là cùng một lỗi, gặp liên tiếp khi tự viết kéo-thả tay (không
  dùng `View.startDragAndDrop`):**
  1. **"Ai NHẬN được sự kiện" (Task 3).** Gắn listener lên `home_root` (root `FrameLayout`, là ANH EM
     của `ViewPager2`, không phải cha/con) thì KHÔNG BAO GIỜ thấy sự kiện chạm — `ViewPager2`/
     `RecyclerView` bên trong nó che gần hết màn hình nên nhận chạm trước. Và: Android chỉ gửi tiếp
     `ACTION_MOVE`/`UP` của 1 cử chỉ cho view đã trả `true` ở `ACTION_DOWN`; `DragLayer` (overlay
     trong suốt, cố ý `dispatchTouchEvent` luôn trả `false` để không chặn chạm) vì vậy không bao giờ
     là nơi tự bắt gesture. **Kết luận:** việc theo dõi long-press/drag phải đặt đúng ở view THẬT SỰ
     nhận được chạm trong cây — ở đây là `IconCellView`/`IconGridView` (con của `ViewPager2`).
  2. **"Ai GIÀNH lại một cử chỉ đang chạy" (Task 5).** Sau khi sửa đúng bẫy 1 — `IconCellView` đã
     nhận `ACTION_DOWN` và đang nhận `ACTION_MOVE` bình thường — kéo icon vẫn bị "nhả" giữa đường,
     sự kiện lại biến thành vuốt chuyển trang. Nguyên nhân: `ViewPager2` (qua `RecyclerView`) tự
     `onInterceptTouchEvent` **ở MỌI sự kiện của MỌI ViewGroup cha**, không chỉ `ACTION_DOWN` — thấy
     di ngang đủ xa là giành luôn `ACTION_MOVE`/`UP` về phía nó, bất kể con đã "nhận" `DOWN` hay chưa.
     **Fix:** đúng lúc quyết định bắt đầu kéo thật (vượt `scaledTouchSlop`), gọi
     `cell.parent?.requestDisallowInterceptTouchEvent(true)` — cờ này tự lan lên MỌI tổ tiên (gồm
     `ViewPager2`), chặn `onInterceptTouchEvent` của chúng cho hết cử chỉ đang chạy. Phải trả lại
     `false` ở `ACTION_UP`/`ACTION_CANCEL` (gọi vô điều kiện, vô hại nếu chưa từng chặn) để vuốt
     chuyển trang bình thường (lúc không kéo) không bị ảnh hưởng.
  - **Bài học chung:** "con đã nhận được `DOWN`" (bẫy 1) và "cha không giành lại được giữa đường"
    (bẫy 2) là HAI điều kiện độc lập, phải đúng CẢ HAI mới kéo được trong một cây View có
    `ViewPager2`/`RecyclerView`/bất kỳ `ViewGroup` nào tự `onInterceptTouchEvent` theo hướng vuốt.
    Thiếu bẫy 2 thì mọi dấu hiệu (rung, bitmap kéo hiện ra, preview đẩy-dồn) vẫn "mượt" lúc mới bắt
    đầu — chỉ lộ ra khi di tay đủ xa theo đúng hướng mà view cha đang lắng nghe (ở đây là ngang, vì
    `ViewPager2` mặc định orientation ngang).

## M7 — trạng thái kéo-thả KHÔNG được nằm trong view của RecyclerView/ViewPager2 (Task 5, 2026-10-29, Vsmart Live 4)

- **Triệu chứng:** kéo icon A sang ô mới (ổn), rồi kéo A về ĐÚNG ô cũ -> A biến mất (vẫn trong edit mode; thoát
  edit mode thì hiện lại đúng chỗ). DB, thuật toán dồn dây chuyền, `DiffUtil` đều đúng — chỉ UI sai.
- **Nguyên nhân gốc:** `RecyclerView` (nền của `ViewPager2`) mặc định có `DefaultItemAnimator`. Mỗi lần nội dung 1
  item đổi (`submitList` với `areContentsTheSame=false`, hoặc `notifyItemRangeChanged` KHÔNG payload) nó cấp
  **ViewHolder/view MỚI** cho item đó rồi cross-fade; view cũ về pool. Log thật: trang 4 luân phiên giữa 2
  `IconGridView` (`view=187642979` <-> `234742015`). Trạng thái "đang chờ dữ liệu mới" (giữ trong field của view) nằm
  ở view này, còn dữ liệu mới lại được bind vào view kia -> so sánh với trạng thái cũ của lần kéo trước, trùng
  nhau đúng lúc kéo về ô cũ -> giữ ẩn mãi.
- **Cách tìm ra (đáng nhớ):** thêm `System.identityHashCode(view)` vào log ở `onBindViewHolder`/`setItems`/
  `beginDragFor`. Hai lần sửa trước (theo chỉ số view con, rồi theo itemId + vị trí) đều sai vì cùng một giả định
  ngầm: "view đang vẽ trang này sẽ còn là view đó ở lần cập nhật sau".
- **Cách sửa (3 lớp):** (A) `RecyclerView.itemAnimator = null` + `setHasStableIds(true)` với `getItemId = pageId`
  (`HomeActivity.setupHomePager`, `HomePagerAdapter`); (B) trạng thái kéo là DỮ LIỆU: `HomeUiState.draggingItemId`,
  `IconGridView.setItems(..., hiddenItemId)` và `bindAllCells` đặt `alpha` theo dữ liệu, không suy luận "còn cũ hay
  mới"; (C) cập nhật lạc quan: `HomeViewModel.onItemDropped` phát ngay danh sách đã áp `GridInsertPlanner` (hàm thuần
  trong `domain/layout`, cùng thuật toán với `LayoutReconciler`) rồi mới ghi DB — tham khảo `LauncherModel` của
  Launcher3 (cập nhật model đồng bộ, ghi DB bất đồng bộ).
- **Quy tắc rút ra:** mọi trạng thái cần sống qua một lần cập nhật dữ liệu phải nằm ở dữ liệu (ViewModel/UiState),
  không ở view của `RecyclerView`/`ViewPager2`. Khi một bug "chỉ xảy ra ở lần thao tác thứ 2" hoặc "thoát chế độ rồi
  tự hết", nghi ngờ ngay việc view bị thay, và log `identityHashCode` trước khi đoán tiếp.
- **Điểm mở đã có kết luận (2026-10-29, đóng M7): chấp nhận rủi ro, không sửa code.** (1) Emission DB cũ về muộn có thể làm UI
  nháy ngược tối đa 1 khung hình: `scheduleReconcile()` (400ms) luôn áp `latestDbPages` mới nhất nên UI hội tụ về DB; chưa từng
  quan sát được trên máy thật. (2) Trang mới do cascade mang `pageId` tạm âm tới khi DB trả id thật; lúc đó id ổn định của trang
  đổi một lần, chưa thấy triệu chứng. **Xét lại khi:** người dùng báo icon nháy sau khi thả, hoặc trang mới bị mất vị trí cuộn/animation.

## M7 Task 6 — kéo-thả dock ↔ lưới: bài học triển khai

- **Mọi nguồn kéo/đích thả phải có id DB.** Dock ban đầu chỉ là `List<AppItem>` (không có `itemId`) nên không kéo được;
  thêm `DockApp(itemId, app)`. Khi thêm 1 loại container mới vào kéo-thả, kiểm model hiển thị đã mang id hàng DB chưa.
- **Thuật toán chèn viết 1 lần ở `domain/layout` (hàm thuần), dùng cho cả ghi DB lẫn cập nhật lạc quan** —
  `GridInsertPlanner` (lưới) và `DockInsertPlanner` (dock). Viết hai bản là hai nơi lệch nhau.
- **Mọi nguồn trạng thái UI đổi bởi 1 lần thả (pages VÀ dock) phải đi chung 1 cơ chế `pendingMoves`/`latestDb*`** và
  được phát trong CÙNG 1 lần `_uiState.update`; nếu để dock tự collect riêng thì emission DB cũ ghi đè trạng thái lạc quan.
- **Nhiều loại đích thả: để `DragHelper` tự chọn target dưới ngón tay** (`registerTarget` + `containsScreenPoint`, view
  tự đăng ký lúc attach/detach), đừng để nguồn kéo truyền `listOf(this)`.
- **OnTouchListener gắn lại giữa cử chỉ làm mất trạng thái DOWN.** `render()` gọi `setItems` liên tục -> `DockView` bỏ qua
  nếu nội dung/kích thước không đổi (chỉ cập nhật alpha theo `hiddenItemId`) và gắn cử chỉ 1 lần lúc tạo ô. Ngược lại,
  "phải nhấn giữ lại mới kéo được" từng chỉ là tác dụng phụ của việc view bị bind lại; tính năng dựa vào tác dụng phụ
  sẽ vỡ ngay khi tối ưu — phải làm thành quy tắc tường minh.
- **Quy tắc cử chỉ kéo (người dùng chốt):** trong edit mode kéo = edit mode đã bật từ lúc chạm + giữ quá
  `getLongPressTimeout()` + di vượt slop; vuốt nhanh vẫn lật trang (trang đầy icon thì mọi điểm chạm đều rơi vào icon).
  Luồng cuối (Task 10): nhấn giữ -> Shortcut (nền mờ + menu) -> giữ tay di vượt slop -> edit mode + kéo luôn.

## M7 Task 7 — lật trang khi kéo + trang tạm: bài học triển khai

- **Phiên kéo phải do `Activity.dispatchTouchEvent` nhận chạm, không phải `OnTouchListener` của ô nguồn.** ViewPager2 tháo
  view trang khỏi cây khi lật (chắc chắn từ 2 trang trở lên) -> ô nguồn nhận `ACTION_CANCEL` và thả sai chỗ. Activity đứng
  trước cả cây view nên không dính; lần tiếp quản đầu gửi 1 `ACTION_CANCEL` giả để view dọn trạng thái. Kết thúc phiên báo
  qua `DragSession.onFinished` (chỉ dùng dữ liệu/callback, không giả định view nguồn còn sống). `ACTION_DOWN` mới khi còn
  phiên sót phải huỷ phiên cũ, nếu không Activity nuốt mọi chạm.
- **Target dưới ngón tay chỉ tự cập nhật khi có `ACTION_MOVE`.** Lật trang xong mà tay đứng yên thì phải gọi
  `DragHelper.refresh()` (từ `SCROLL_STATE_IDLE`).
- **Trang tạm (ghost) chỉ ở UI, không ghi DB lúc preview.** Hàng `home_pages` chỉ tạo khi thả LÊN trang đó (sentinel
  `PageId.NEW`, cùng transaction với việc đặt item); kéo ngược/thả chỗ khác/huỷ thì bỏ ghost. Dùng cùng `pageId` cho ghost
  và trang lạc quan để view không bị thay lúc thả.

## M7 Task 8 — dấu "−" ẩn nhanh: bài học triển khai

- **Rebind view KHÔNG được xóa nội dung đã có rồi nạp lại bất đồng bộ.** `bindAllCells` bind `icon = null` + nạp async khiến
  mọi ô trống icon vài khung hình mỗi lần `setItems` (ẩn/dồn/thả) — nhìn như icon "chớp". Giữ cache bitmap theo key trong view
  (xóa khi `iconSize` đổi, chỉ giữ key đang hiển thị) và chỉ nạp key chưa có.
- **Mọi nơi "đặt app chưa có hàng" phải bỏ qua `hidden_apps`.** Ẩn = xóa placement + ghi `hidden_apps`; nếu `placeNewInTransaction`
  không lọc, app ẩn bị coi là app mới và quay lại ô trống đầu tiên (= cuối trang sau khi dồn) ở lần quét kế tiếp. *(Lịch sử: từ FX3, 2026-11-11, app mới và app bỏ ẩn đặt **sau icon cuối**, không còn "ô trống đầu tiên"; xem mục FX3 ở cuối file.)*
- **Ẩn là dồn, kéo ra là để trống (Ruling G2):** một hàm thuần (`GridRemovePlanner`) dùng chung cho DB và cập nhật lạc quan,
  đúng nguyên tắc `GridInsertPlanner`. Khuôn ghi chung `launchLayoutWrite` cho mọi thao tác ghi bố cục từ UI.
- **Dấu vẽ trong `IconCellView` thì ảnh chụp lúc kéo phải tắt dấu** (`snapshotWithoutBadge`); vùng chạm 48dp nhưng hình 24dp,
  tâm kẹp trong biên ô để không bị `clipChildren` cắt.

## M7 Task 10 — menu nhấn giữ icon: bài học triển khai

- **Gỡ app bằng `ACTION_DELETE` cần `REQUEST_DELETE_PACKAGES` (target SDK >= 28), nếu không hệ thống IM LẶNG bỏ qua:** `startActivity`
  không ném lỗi, hộp thoại không hiện, lỗi chỉ ở log system. Triệu chứng "bấm mà không phản ứng" -> kiểm quyền này trước.
- **Domain không chứa `Intent`:** gỡ cài đặt / thông tin ứng dụng đặt ở `AppRepository` (data thực thi qua `Context`/`LauncherApps`),
  không tạo use case trong `domain`. "Thông tin ứng dụng" dùng `LauncherApps.startAppDetailsActivity` (đúng user profile).
- **Shortcut chỉ đọc được khi launcher là MẶC ĐỊNH** (`hasShortcutHostPermission`); lấy cờ động + manifest (Camera/Chat dùng manifest),
  không lấy chỉ-ghim; bọc `try/catch` trả rỗng để menu vẫn có mục cố định.
- **Menu nhấn giữ = trạng thái trong `HomeUiState` (`iconMenu`), bitmap nổi ở tầng view.** Menu hiện sau khi shortcut nạp xong (scrim
  + icon nổi hiện ngay) để khỏi nhảy vị trí; Activity bị tạo lại mất bitmap -> tự đóng menu. Dùng View thường trong `home_root`
  (không `PopupWindow`) để cùng toạ độ/z-order với scrim và `DragLayer`.
- **Chuyển Shortcut -> kéo (đường B) phải nguyên tử:** `onDragStarted` đặt `draggingItemId`, `iconMenu = null`, `editMode = true` trong
  CÙNG 1 lần phát; khi menu đóng mà đang có phiên kéo thì KHÔNG `DragLayer.endDrag()` (đang vẽ icon của phiên kéo).
- **View bị ẩn giữa cử chỉ không đáng làm điểm tựa cho cử chỉ tiếp theo:** nhấn giữ icon nổi (scrim) -> edit mode -> kéo luôn được làm bằng
  `pendingMenuDrag` ở `HomeActivity.dispatchTouchEvent` + `beginDragForItem` của lưới/dock, rồi nhập vào đường tiếp quản của Task 7.
- **Lỗi không tái hiện được:** đặt log theo luồng (tag riêng), nếu người dùng không tái hiện lại được thì gỡ log và ghi điểm đặt log vào plan
  để bật lại khi gặp lần nữa — không giữ log tạm trong code.

## M7 Task 11 — đóng mốc: bài học

- **Thêm trạng thái "ẩn" thì rà MỌI chỗ tạo/đặt item:** `placeNewInTransaction`, `seedIfEmpty`, `catchUp` đều phải lọc `hidden_apps`; mọi chỗ xóa dữ liệu theo package
  (`removePackages`, `catchUp`) phải dọn luôn bảng phụ, và dọn TRƯỚC các lệnh return sớm (app ẩn không còn hàng placement nên "không có gì để xóa" không có nghĩa là không có gì để dọn).
- **"0 hàng = máy mới" hết đúng khi người dùng xóa được dữ liệu của chính mình:** dùng dữ liệu đã có (`hidden_apps`) làm dấu thay vì thêm cờ mới khi đường duy nhất tới trạng thái đó đã rõ.
- **Đóng mốc = rà lại Review Focus + các "M7 phải xem lại" rải trong tài liệu (room-schema, project_structure), không chỉ tick đủ Task.**

- **Bằng chứng kiểm chứng Review Focus M7 (đọc code, 2026-10-29):** (a) cascade tạo đúng 1 trang mới: `GridInsertPlanner` chỉ tạo
  trang khi tràn quá trang cuối, `moveToHomeInTransaction` chèn đúng `plan.newPages` (+1 nếu `PageId.NEW`); (b) `Mutex` dùng chung cho cả 7
  điểm ghi của `LayoutReconciler` (kể cả `packageEvents`); (c) icon kéo nằm trên kính vì `DragLayer` là tầng z cao nhất, đã test tay trên Oppo.
  Chưa có unit test riêng cho (a) và (b): nếu sửa planner/reconciler thì nên thêm.

## M8a Task 2 — bỏ cỡ lưới cứng: bài học triển khai (2026-10-29)

- **Cỡ lưới đến từ cài đặt (DataStore) là bất đồng bộ:** `gridStatus` chỉ được `Ready` khi CẢ cài đặt LẪN bố cục đã phát lần đầu, nếu không sẽ nháy 4×6 rồi mới ra cỡ đã lưu.
- **Mọi thuộc tính ảnh hưởng cách bind view con phải nằm trong `GridMetrics.Cells` (data class):** `showLabels` thêm vào `Cells` để bật/tắt nhãn tự bind lại ở `IconGridView`/`DockView` (cùng bài học M5 Task 2).
- **Đổi metrics không đổi nội dung trang thì DiffUtil không báo:** `HomePagerAdapter.setGridMetrics` phải `notifyItemRangeChanged` thủ công.
- **`LayoutReconciler`: đọc `currentGrid()` đúng một lần ngay sau khi lấy mutex, TRƯỚC `db.withTransaction`** (không giữ khóa ghi DB lúc chờ DataStore), rồi truyền `grid` xuống các hàm `...InTransaction`.
- **Bỏ giá trị mặc định cứng ở use case/hằng số để compiler chỉ ra mọi chỗ còn sót;** sau đó `grep DEFAULT_COLUMNS|DEFAULT_ROWS|GridMetrics()` phải rỗng.

## GP — nhấn giữ khoảng trống: bài học (2026-11-05, Vsmart Live 4)

- **Nhấn giữ cho một view chiếm cả trang phải tự hủy khi tay trôi** (đọc AOSP): `View` chỉ hủy khi tay ra khỏi view cộng slop, nên `GapLongPressGesture` gọi `cancelLongPress()` khi vượt `scaledTouchSlop` hoặc có ngón thứ hai. Listener chạm chỉ quan sát, luôn trả `false`, để View vẫn chặn click sau nhấn giữ. Đo được: ngón tay đi xa liên tục (`input swipe` 1000 px trong 1.5 giây) không vào edit mode.
- **`GridStatus.Empty` từng ẩn cả pager, pill và dock:** view `INVISIBLE` không nhận chạm, nên thêm lối vào mới phải kiểm cả trạng thái hiển thị của view nhận cử chỉ. Dock cũng bị ẩn oan: app trong dock không mở được khi lưới trống.
- **`input motionevent` khởi động chậm vài trăm ms mỗi lệnh trên Live 4 (quan sát; suy luận về nguyên nhân):** `DOWN` rồi `MOVE` ở hai lệnh liền nhau có thể tới sau mốc nhấn giữ, kết quả trông như lỗi "trôi tay không hủy". Kiểm chuyển động trong lúc giữ thì dùng một lệnh `input swipe` dài.
- **Thử bố cục đặc biệt mà không đụng dữ liệu chủ dự án:** `run-as ... tar cf -` sao lưu `databases` + `files/datastore`, sửa bản sao bằng `sqlite3`, đẩy vào qua `/data/local/tmp`, thử xong giải nén lại rồi so số hàng và tổng vị trí với bản sao lưu.

## CL — ô kéo kẹt ẩn (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **CL — ô kéo kẹt ẩn:** kéo qua ≥ 5 trang làm `RecyclerView` tái dùng view nguồn; thả bị từ chối chỉ trả alpha cho view cũ. Sửa bằng cách ép mọi trang đang gắn đọc lại `draggingItemId` SAU KHI `submitList` áp xong (không phải ngay khi hết kéo, vì sẽ hiện ô cũ chớp lên khi thả thành công). Alpha luôn là hàm của dữ liệu.

## FX3 — bộ điều hợp bố cục (2026-11-11, Vsmart Live 4)

- **Planner thuần được gọi trên luồng chính phải chịu được dữ liệu lệch** (đọc code, 2026-11-11): item ngoài lưới, nhiều hơn `columns*rows` item. Hàng trùng ô là cách tái hiện lỗi planner mà không cần đua.
- **"Sau icon cuối" là lựa chọn có chủ ý của dự án, không phải mặc định của Android** (đọc tài liệu AOSP, 2026-11-11): Launcher3 đặt app mới vào ô trống đầu tiên (`WorkspaceItemSpaceFinder.findSpaceForItem`, duyệt các màn hình theo thứ tự, chỉ tạo màn mới khi hết chỗ). Dự án đặt sau icon cuối (như iOS), xem `room-schema.md` Q7.

## FX7 — phiên kéo và hợp đồng hạ cánh (2026-11-11, Vsmart Live 4)

- **Hợp đồng hoạt ảnh có callback "xong" phải gọi callback cả khi bị hủy** (đọc code, 2026-11-11), hoặc ghi rõ ai dọn. Người gọi đi vòng qua lớp điều phối (`IconMenuController` → `DragLayer`) là chỗ hợp đồng vỡ (menu mở chen vào hạ cánh: `DragLayer.cancelSettle` → `finishSettle` → `onDragSettled`).
- **Phiên kéo ghi nhận `pointerId` lúc bắt đầu** (đọc code, 2026-11-11): `ev.rawX/rawY` đọc ngón ở chỉ số 0, đổi khi ngón đầu nhấc; `getRawX(int)` cần API 29. Quy tắc sản phẩm (Q1): nhấc ngón đang kéo = thả tại đó, ngón còn lại bị bỏ qua tới hết cử chỉ.
- **Khôi phục trang theo `pageId`, không theo chỉ số, và chỉ khi pager rảnh** (đo bytecode, 2026-11-11): xem `home-pager-animation.md` mục FX7.

## G3c — widget của app khác (2026-11-13/16, Live 4 + Oppo). Nguồn: [knowledgements/G3c-appwidget-host.md](../knowledgements/G3c-appwidget-host.md)

- (D, 2026-11-13, **đọc code**) `ViewGroup` chỉ gọi `OnTouchListener`/`OnLongClickListener` của chính nó khi không con nào nhận chạm; ô chứa widget phải tự theo dõi ở `dispatchTouchEvent` và hủy con rồi nhấn giữ khi hết hạn.

## Bổ sung chép từ repo XML (ở bản XML nằm cuối mục «G3c — widget của app khác (2026-11-13/16, Live 4 + Oppo). Nguồn: [knowledgements/G3c-appwidget-host.md](../knowledgements/G3c-appwidget-host.md)»)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-19, đo được trên Live 4, P2-5) Mở rộng vùng nhấn giữ ra ngoài lưới thì phải mở rộng cả chạm để thoát edit mode.** Lưới có cặp nhấn giữ (vào edit mode, `GapLongPressGesture`) + chạm (thoát, `IconGridView.onEmptyAreaClick`). Cài nhấn giữ lên `home_content`/`home_root` mà quên chạm làm vùng hai bên pill thành vùng chết trong edit mode. Vùng nền nhận chạm "rơi xuống" khi không view con nào nhận: view con không clickable (như pill ở dạng chấm) cũng để chạm rơi xuống nền.
- **(2026-11-19, chủ dự án thấy trên Live 4, P2-6) Đích gộp thư mục mà tự dịch chỗ thì phải có độ trễ trước khi dịch.** Bản đầu của dock xem trước chèn-dồn NGAY khi ngón tay vào dock. Dock chưa đủ ô thì cả hàng dàn lại, icon đích trượt khỏi ngón tay trước khi ngón tay tới vùng giữa. Bật gộp lại `clearPreview()` nên hàng trượt về, đích lại chạy khỏi ngón tay: gộp và đẩy-dồn giật qua lại. Cách sửa: mọi đích kéo-thả dùng chung máy trạng thái của lưới (`NONE/MERGE/PUSH`, gộp chờ 100 ms, đẩy-dồn chờ 500 ms và ngón tay phải dừng, đẩy vào icon đang bị đẩy thì giữ hàng).
- **(2026-11-19, chủ dự án góp ý, P2-7) Ngưỡng lật mép cho vật lớn phải có cả luật ngón tay.** Chỉ dùng "tâm ảnh ra ngoài mép" thì người giữ sát mép phải widget không bao giờ lật được sang phải (tâm ảnh luôn ở bên trái ngón tay). Dùng "tâm ảnh ra ngoài mép **hoặc** ngón tay vào dải mép".
- **(2026-11-19, chủ dự án thấy trên Live 4, P2-6) Ảnh kéo là ảnh chụp cả ô (icon + nhãn), nên hạ cánh theo tâm ô chỉ đúng khi ô nguồn và ô đích cao bằng nhau.** Dock không nhãn, lưới có nhãn: thả lưới vào dock thì icon rơi lệch lên nửa chiều cao nhãn, nhãn hiện rồi biến mất (dư ảnh); gộp thư mục co ảnh 0,4 thì nhãn co theo, lộ trên icon đích. Cách làm: đích đưa hình vuông icon (`DropSettle.iconAligned`), `DragHelper` cộng độ lệch icon trong ảnh × `endScale`; ảnh kéo có bản không nhãn cùng cỡ, đổi sang khi vào trạng thái gộp hoặc trên dock, giữ nguyên trạng thái đó suốt lúc hạ cánh.
- **(2026-11-19, P2-6) Tô phẳng màu bán trong suốt đè lên kính thì thành mảng trắng.** Hover trong dock phải dùng cùng kính mờ (`BackdropBlurDrawable`, vai `FOLDER_CELL`) như lưới; chỉ tier FLAT mới tô phẳng.
- **(2026-11-19, P2-6) "Thay tại chỗ" của dock so theo quan hệ id, không theo ô.** Lưới bỏ thu/phình khi item cũ và mới cùng ô. Dock dàn lại mỗi lần thêm/bớt nên chỉ số đổi; hai item là cùng một chỗ khi chung id (bản thân hoặc app con): app -> thư mục, thư mục -> app con còn lại, thư mục id tạm -> id thật. Item mới trượt (FLIP) từ chỗ nhìn thấy của item cũ.

## FM — thư mục nhiều trang và sắp xếp trong thư mục: bài học (2026-11-20, chủ dự án kiểm trên máy). Nguồn: [plans/FM-folder-pages.md](../plans/FM-folder-pages.md) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-20, FM-2, đọc mã nguồn Android) Nhiều trang trong một ViewGroup: đặt các trang cạnh nhau và cuộn bằng `scrollX`, không cần ViewPager.** Trong `dispatchDraw`/`onDraw` toạ độ đã tính cuộn (`updateDisplayListIfDirty` dịch `-mScrollX` trước khi vẽ), nên vùng cắt phải là `scrollX .. scrollX + width`; chạm và `getLocationOnScreen` của con cũng tự tính cuộn. Giữ khung đứng yên thì kính và hoạt ảnh mở/đóng của khung không phải đụng tới.
- **(2026-11-20, FM-2) Con vẽ tràn ra đệm của cha bị cắt bởi `clipToPadding` của cha, không phải `clipChildren`.** Muốn vẽ chấm trang trong đệm dưới của khung panel phải đặt `panel.clipToPadding = false`, rồi tự cắt nội dung bằng `clipRect` để trang bên cạnh không lộ ra ngoài khung.
- **(2026-11-20, FM-2) Vuốt đổi trang và giữ-để-kéo không giành nhau nhờ `IconDragGesture`.** Lưới chỉ cướp chạm (`onInterceptTouchEvent`) khi vuốt ngang quá slop; icon đã "giữ đủ lâu" thì tự gọi `requestDisallowInterceptTouchEvent(true)` nên lưới không cướp được nữa. Lưới một trang không nhận chạm, giữ nguyên hành vi cũ.
- **(2026-11-20, chủ dự án góp ý, FM-3) Xem trước sắp xếp phải có độ trễ và trượt chậm.** Dời ngay mỗi lần ngón qua ô mới thì cả lưới nhảy theo khi kéo lướt; điểm kéo phải ở yên trên một ô 250 ms rồi mới dời. Tốc độ dời dùng chung `SpringProgress.Spec.REORDER` (độ cứng 300, chủ dự án tự chỉnh từ 350) cho lưới Home, dock và thư mục để cảm giác đồng bộ.
- **(2026-11-20, FM-3) `SpringProgress.animateTo(newSpec = …)` đổi spec VĨNH VIỄN cho spring đó.** Hàm dùng chung (`IconCellView.springOffsetTo`) phải truyền spec mỗi lần (mặc định SLIDE), nếu không một lần trượt chậm sẽ làm mọi lần trượt sau của ô đó cũng chậm.
- **(2026-11-20, FM-3) Thứ tự trong thư mục chỉ biết các app nhìn thấy.** Reconciler điền lại các chỗ của app nhìn thấy theo thứ tự mới, app không khả dụng (đã gỡ, tạm khóa) giữ chỗ cũ, rồi mới đánh lại `rank`; không đánh `rank` 0..n-1 thẳng theo danh sách UI.

## DA — dư ảnh icon khi thả vào ô trống của dock / panel thư mục (2026-11-20) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **Triệu chứng:** thả icon vào chỗ trống ở dock hoặc panel thư mục: ô đích hiện sẵn icon kèm dấu "−" trong lúc ảnh kéo còn bay tới,
  thấy hai icon một thoáng. Lưới Home không bị.
- **Gốc (đọc code):** `DragHelper.drop` đặt `settlingItemId` SAU `target.onDrop`. `HomeActivity` thu `uiState` trên `lifecycleScope`
  (`Dispatchers.Main.immediate`), nên trạng thái lạc quan mà `onDrop` phát (item ở chỗ mới, `draggingItemId = null`) được render ngay
  bên trong `onDrop`: `DockView`/`FolderPanelView` tính alpha với cả `hiddenItemId` lẫn `settlingItemId` đều null → ô hiện. Lưới thoát
  vì bind qua `submitList`/DiffUtil của pager, chạy sau khi `drop` xong. Panel thư mục từng vá bằng `post { refreshAlpha() }` nên chỉ
  lóe một frame; dock lộ suốt lúc hạ cánh.
- **Sửa:** đặt `settlingItemId = session.itemId` TRƯỚC `onDrop`; không có hạ cánh thì xóa cờ và báo `onDragSettled` cho mọi phiên
  (không chỉ khi có `returnRect`), vì target có thể đã giữ ô ẩn khi thấy cờ trong `onDrop`.
- **Bài học:** với collector `Main.immediate`, mọi `_uiState.update` gọi từ callback UI là render đồng bộ ngay tại chỗ. Cờ mà render
  đọc phải đặt xong TRƯỚC khi gọi callback có thể phát state.

## TV — Today View: bài học (2026-11-20, chủ dự án kiểm trên máy). Nguồn: [plans/TV-today-view.md](../plans/TV-today-view.md) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-20, TV-3) Chừa đệm để vẽ tràn (dấu "−") thì phải tắt cả `clipToPadding`, không chỉ `clipChildren`.** `ViewGroup` cắt con theo vùng TRONG đệm khi `clipToPadding` = true (mặc định), độc lập với `clipChildren`. Lưới Today có đệm trên cho dấu "−" của hàng đầu nhưng quên tắt `clipToPadding`, nên chỉ hàng đầu bị cắt; các hàng sau đã đúng sau khi tắt `clipChildren`.
- **(2026-11-20, TV-1) Lớp phủ mở bằng cử chỉ ngang dùng chung một bộ điều khiển, chỉ khác chiều.** `LibraryOverlayController` nhận `openSign` (-1 Library vuốt trái, +1 Today vuốt phải); Today gọi sau Library trong `HomeTouchRouter`, mỗi bên chỉ tiếp quản đúng chiều của mình nên Home một trang không giành nhau. Hai lớp không mở chồng: điều kiện mở của bên này đòi bên kia đứng yên ở trạng thái đóng.
- **(2026-11-20, TV-3) Cử chỉ ở mức Activity thấy chạm TRƯỚC cây view.** Kéo đổi chỗ widget trên Today bắt đầu trong view, nhưng cú di ngang đầu tiên đã tới bộ đóng Today trước; phải cho bộ đó hỏi cờ "đang nhấn giữ / đang kéo" của view (`canClose`), không chỉ "đang kéo".
- **(2026-11-20, TV-3) Danh sách cuộn cần nhận nhấn giữ cả ở chỗ trống: `NestedScrollView.isFillViewport` + con giữ chiều cao tối thiểu EXACTLY.** Con tự đo theo nội dung sẽ bỏ qua chiều cao khung, chỗ trống dưới cùng (hoặc cả màn khi danh sách rỗng) không thuộc con nào.
- **(2026-11-20, TV-3) Widget không có hàng `launcher_items` vẫn dùng chung kho đánh theo `itemId` (thời tiết, ảnh) nếu cấp id trong dải riêng xa id Room (2^40).** Quét mồ côi của các kho đó phải bỏ qua dải này, nếu không dữ liệu của widget Today bị xóa mỗi lần khởi động.
- **(2026-11-20, TV-3) Mờ dần một con theo vị trí mà không đụng `alpha` của nó: vẽ con qua `saveLayerAlpha` trong `drawChild`.** `alpha` đang được hoạt ảnh thêm/xóa dùng; ghi đè sẽ giành nhau.

## RA — menu nhấn giữ mở từ lớp phủ (Thư viện, Tìm kiếm): bài học (2026-11-21, Vsmart Live 4). Nguồn: [plans/RA-readd-home.md](../plans/RA-readd-home.md) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-21, đọc code, RA) Dùng lại menu của Home cho nguồn mới thì rà mọi chỗ đang "đóng X khi `iconMenu != null`".** `AppLibraryController.render` (D14) đóng Thư viện khi có menu, nên menu mở từ Thư viện tự đóng chính Thư viện. Nguồn của menu đi trong state (`IconMenuState.source`), các luật cũ chỉ áp cho `HOME`; thêm cả chặn vuốt đóng Thư viện khi menu mở (`canClose`).
- **(2026-11-21, đọc code, RA) Hai lớp phủ trước đây không bao giờ cùng mở thì thứ tự z và thứ tự Back đều phải xét lại khi cho chúng chồng nhau.** `search_overlay` từng nằm trên `icon_action_menu`, và `HomeBackController` đăng ký tìm kiếm sau menu (Back đóng tìm kiếm trước). Menu mở từ kết quả tìm kiếm cần cả hai đảo lại.
- **(2026-11-21, đọc code, RA) Menu từ nguồn không có ô trên Home: `itemId = -1`, mọi nhánh dựa trên `itemId`/`container` (ẩn nhanh, kéo icon nổi, hộp xóa app) phải rẽ theo nguồn.** Ẩn từ Thư viện gọi thẳng `HideAppUseCase`; icon nổi không kéo được; "Gỡ cài đặt" mở thẳng `ACTION_DELETE` (hệ thống luôn hỏi xác nhận).

## PG1 A1 — hoạt ảnh khi bố cục lưới / dock / thư mục đổi trong Compose (2026-11-23, Oppo PKD130). Nguồn: [plans/PG1-parity-batch-1.md](../plans/PG1-parity-batch-1.md)

- **(2026-11-23, đọc code) Chỉ dời theo "ô xem trước" thì dữ liệu đổi là icon nhảy.** Thả nhanh trước `PUSH_ARM_MS`, gỡ/ẩn rồi dồn ô, bỏ ẩn đều đổi vị trí mà không qua xem trước. Cách Compose: vị trí tuyệt đối có hoạt ảnh khoá theo `itemId` (`rememberSlideOffset`), đích = ô xem trước hoặc ô thật; thả sau xem trước thì đích mới trùng ô xem trước nên không giật. Dock và bảng thư mục vốn đặt theo vị trí tuyệt đối nên đã trượt đúng từ trước.
- **(2026-11-23, đọc code) Thu/phình chỉ khi bố cục đổi KHÔNG do kéo.** Đang kéo hoặc ảnh kéo đang hạ cánh (`session`, `settlingId`) thì chính ảnh kéo là hoạt ảnh; "thay tại chỗ" (thư mục tạo/giải tán) so theo ô. App kéo vào thư mục hạ cánh vào đúng ô nhỏ của nó trong icon thư mục và icon thư mục ẩn app đó tới khi hạ cánh xong (`hiddenChildId` gồm cả `settlingId`), không mờ biến mất (chủ dự án chốt).
- **(2026-11-23, lint đo được) Không tạo `Animatable` lúc dựng ngoài `remember`** (lint `RememberInComposition` làm `lintDebug` thất bại). Muốn không cấp `Animatable` cho item đứng yên: lúc dựng chỉ ghi chỗ xuất phát vào biến thường, tạo `Animatable` trong `LaunchedEffect`, giữ trong `mutableStateOf` để hàm dời đọc trong pha layout thấy khi gán/bỏ; khung đầu vẽ ở chỗ xuất phát.

## PG2 — kéo thẻ widget từ bảng chọn ra lưới Home (2026-11-23, Oppo PKD130). Nguồn: [plans/PG2-widget-card-drag.md](../plans/PG2-widget-card-drag.md)

- **(2026-11-23, đọc code) Compose `Dialog` là cửa sổ riêng giữ cả chuỗi chạm, nên kéo từ trong nó ra lưới thì phải bỏ `Dialog`.** Tách thân sheet thành `DsBottomSheetLayer` (lớp mờ, trượt, kéo xuống, `BackHandler`) vẽ trong cây Compose của Home; `DsBottomSheet` = `Dialog { DsBottomSheetLayer }` cho các sheet khác giữ nguyên hành vi. Nút nhận cú nhấn xuống nhận tiếp mọi sự kiện của ngón đó kể cả khi ra ngoài nút, nên `pointerInput` của thẻ lái thẳng `HomeDragState` (một hệ toạ độ gốc qua `localToRoot`).
- **(2026-11-23, đọc code) Cử chỉ sống lâu hơn bảng thì bảng không được rời composition giữa chừng.** Đang kéo: bảng chỉ mờ về alpha 0; gỡ bảng là coroutine cử chỉ bị huỷ, `finally` gọi `drag.cancel()`.
- **(2026-11-23, kiểm tay) `graphicsLayer { alpha = 0f }` vẫn nhận chạm.** Ngón thứ hai chạm lớp mờ (`clickable { close() }`) của bảng đã mờ → đóng bảng → phiên kéo bị huỷ → widget mất. Sửa: khi đang kéo, đặt một lớp trong suốt trên cùng nuốt mọi ngón mới; ngón đang kéo đã gắn với nút của nó từ lúc nhấn xuống nên không qua lớp này.
- **(2026-11-23, đọc code) Kéo item chưa có hàng DB: dùng lại `DragItem` có sẵn với id tạm âm, phiên mang cờ riêng để `drop` / `cancelDrop` rẽ nhánh** (thả = hàm VM riêng, không qua `onItemDropped`; huỷ = co + mờ tại chỗ vì không có ô nguồn). Nhờ vậy xem trước đẩy-dồn, chặn chồng widget, lật mép, trang tạm chạy như kéo widget có sẵn. Nút Home khi đang kéo = huỷ (chủ dự án chốt).
