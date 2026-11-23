# GP — Nhấn giữ khoảng trống để vào edit mode (TT#8) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `fix/gp-gap-long-press` · **Worktree:** `E:\SourceTree\IosLauncher-wt\GP-gap-long-press` · **PR:** # (draft) · **Bắt đầu:** 2026-11-05 · **Plan duyệt:** 2026-11-05
**Trạng thái:** Chờ review (Task 1-2 đã code, build xanh)
**Khóa:** không (không đổi schema, không thêm khóa DataStore, không chạm file nóng độc quyền)

**Goal:** Nhấn giữ vào khoảng trống của một trang Home (chỗ không có icon/thư mục) thì vào edit mode, như iOS. Phải chạy cả trên trang không có icon, vì đó là lối ra duy nhất khi đã ẩn hết app (nút Cài đặt chỉ có trong edit mode).

**Architecture:** Một `OnLongClickListener` gắn lên chính `IconGridView` (từ bên ngoài, ở `HomePagerAdapter`), dùng cơ chế nhấn giữ có sẵn của `View` nên rung và thời gian giữ y hệt nhấn giữ icon; một `OnTouchListener` đi kèm chỉ để hủy nhấn giữ khi ngón tay trôi quá slop. Khi lưới ở trạng thái `Empty`, `HomePagerController` vẫn cho pager và dock hiện (hiện đang ẩn) để lưới rỗng nhận được chạm và app trong dock còn mở được; pill vẫn ẩn. `IconGridView.kt` không bị sửa.

**Tech Stack:** View/XML (B1). Không dependency mới.

**Spec:** TT#8 trong `Launcher-Features.md` (edit mode) + dòng "giới hạn lối vào Cài đặt chỉ có trong edit mode" (M8a) ghi ở mục "Ghi để sau" của file đó; bảng đợt 1 trong `MultiAgent-Workflow.md`.

## Vì sao chọn thiết kế này

**Vấn đề cần giải (đã đọc từ code, 2026-11-05):**
1. Hiện chỉ ô icon/thư mục nhận nhấn giữ. `IconGridView` đã `setOnClickListener { onEmptyAreaClick }` (chạm khoảng trống = thoát edit mode) nên nó đã nhận chạm; chỉ thiếu long click.
2. **Trang rỗng không nhận được chạm.** `HomeViewModel.gridStatusFor` trả `Empty` khi mọi trang không có item (ẩn hết app là vậy; cũng là khi mọi app đã nằm trong dock). `HomePagerController.renderGridStatus` khi đó đặt `homePager`, `searchPill`, `dockView` đều `INVISIBLE`, chỉ hiện dòng "Không có app nào để hiển thị". View `INVISIBLE` không nhận chạm, nên chỉ thêm long click ở lưới thì **lối ra vẫn chết**.
3. Dữ liệu vẫn còn trang: ẩn app không xóa trang (`LayoutReconciler.setHidden` + `deleteTrailingEmptyPages` luôn giữ ≥ 1 trang), nên trang rỗng tồn tại trong adapter, chỉ đang bị ẩn.
4. Nút Cài đặt (`btn_settings`) nằm trong `edit_toolbar`, hiện theo `state.editMode` và không phụ thuộc `gridStatus` (`HomeActivity.render`); thanh trạng thái ẩn khi edit mode và `SystemBarsController` lấy inset kiểu "bỏ qua visibility". Vậy vào được edit mode ở trạng thái `Empty` là mở được Cài đặt.
5. Bẫy cử chỉ của dự án (AGENTS.md): `ViewPager2` chặn chạm ở mọi sự kiện của mọi cha (M7 Task 3-5); `LibraryOverlayController` chỉ hỏi `canOpen()` lúc DOWN (TL1, đã sửa bằng hỏi lại lúc MOVE); `SearchSwipeDetector` chỉ quan sát.
6. `View` tự hủy nhấn giữ **chỉ khi ngón tay rời khỏi view cộng slop** (đọc AOSP `View.onTouchEvent`, ACTION_MOVE; **chưa kiểm trên máy**). `IconGridView` là cả trang, nên với nó trôi tay trong trang không hủy. Phải tự hủy; `View.cancelLongPress()` là API công khai đúng cho việc này.

**Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Long click của `View` trên lưới + `cancelLongPress()` khi vượt slop** | `GapLongPressGesture.install(grid, onLongPress)` gắn `setOnLongClickListener` + `setOnTouchListener` (chỉ quan sát, trả `false`) từ `HomePagerAdapter.onCreateViewHolder` | Rung `LONG_PRESS` và thời gian giữ do `View` làm, y hệt ô icon (cũng `setOnLongClickListener` trả `true`). `View` tự chặn click sau khi đã nhấn giữ nên thả tay **không** thoát edit mode. Chỉ cha-của-ô: chạm trúng icon đã do ô nhận, lưới không thấy → không trùng với menu icon. `IconGridView.kt` không đổi (G2c giữ nó). Hình học (tự vượt slop) nằm gọn trong 1 file mới ~40 dòng | Phải nhớ trả `false` ở listener chạm, nếu trả `true` sẽ nuốt luôn click/long click. Hai listener phải gắn cùng chỗ | **Chọn** |
| B. Runnable/`GestureDetector` tự viết trong `OnTouchListener` | Tự đếm giờ, tự rung, gọi thẳng `enterEditMode` | Kiểm soát hoàn toàn slop | `View` không biết đã nhấn giữ → khi thả tay vẫn bắn `onClick` = `onEmptyAreaClick` = **thoát edit mode vừa vào**. Phải tự gửi ACTION_CANCEL giả hoặc nuốt UP rồi dọn trạng thái pressed. Nhiều code hơn cho cùng hiệu quả | Không |
| C. Bộ nhận ở mức Activity (như `SearchSwipeDetector`, trong `HomeTouchRouter`) | Quan sát DOWN/MOVE/UP trước cây view, hẹn giờ | Chạy được cả khi pager ẩn, không cần sửa trạng thái hiển thị | Không biết điểm chạm trúng icon hay khoảng trống nếu không tự dò hình học các ô → trùng với menu icon, thư mục, dock, và sau này widget của G2c. Thêm một cơ chế song song với nhấn giữ icon | Không |
| D. Thêm `onGapLongPress` vào `IconGridView` (override `onTouchEvent`) | Sửa trực tiếp lưới | Gọn về mặt API | `IconGridView` là nơi G2c làm widget; sửa ở đó dễ xung đột. GP phải xong trước G2c | Không |

Với trạng thái `Empty` (cần thêm một quyết định, độc lập với A–D):

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **E1. Pager vẫn hiện khi `Empty`** | `renderGridStatus`: `homePager` hiện khi `Ready` **hoặc** `Empty` | ít dòng, cùng đường chạm với trạng thái `Ready`, chạm khoảng trống thoát edit mode cũng dùng được ở `Empty`. Không còn chớp ẩn/hiện khi ẩn nốt app cuối trong edit mode | Pager hiện nhưng pill và dock vẫn ẩn (giữ như cũ). Nếu DB không có trang nào (xem Ngoài phạm vi) thì không có lưới để nhận chạm | **Chọn** |
| E2. View trong suốt phủ pager ở `activity_home.xml` | Thêm lớp bắt nhấn giữ riêng cho `Empty` | Chạy kể cả 0 trang | `activity_home.xml` là file nóng độc quyền (G2c có thể cần); thêm cơ chế thứ hai song song với A | Không |
| E3. Dùng phương án C chỉ cho `Empty` | Bộ nhận Activity bật khi `Empty` | Không đổi hiển thị | Hai cơ chế cho một cử chỉ | Không |

**Vì sao chọn A + E1:** A đi theo đúng đường nhấn giữ icon đã chạy ổn từ M7 (cùng `View.performLongClick`, cùng rung), tránh lỗi "thả tay thoát edit mode" mà B sẽ gặp (suy luận từ code `IconGridView.init`, kiểm ở Task 1 kịch bản (a)); không động `IconGridView` (D) và không động file nóng (E2). E1 là sửa nhỏ nhất làm cho A có tác dụng ở đúng ca cần thiết.

**Đánh đổi chấp nhận:** (1) trang ở `Empty` có thể vuốt qua lại giữa các trang rỗng nếu có nhiều hơn một (hiếm: `deleteTrailingEmptyPages` xóa trang rỗng ở cuối) trong khi pill ẩn; (2) lưới thành long-clickable nên TalkBack đọc thêm hành động "nhấn giữ" ở trang — chấp nhận, thậm chí có lợi (TalkBack có lối vào edit mode); (3) nhấn giữ khoảng trống ở **dock và hàng pill** không làm gì (xem D6). Xét lại nếu chủ dự án muốn cả dock.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Nhấn giữ khoảng trống **trang Home** (kể cả trang không icon) → `viewModel.enterEditMode()`. | Chủ dự án chốt 2026-11-05 (đợt 1) |
| D2 | Thời gian giữ = `ViewConfiguration.getLongPressTimeout()` (do `View` lo), y hệt nhấn giữ icon. Rung `HapticFeedbackConstants.LONG_PRESS` do `View.performLongClick` phát khi listener trả `true`. | Đọc code: ô icon cũng `setOnLongClickListener { …; true }` |
| D3 | Ngón tay trôi quá `scaledTouchSlop` (tính từ điểm chạm xuống, `hypot`) hoặc có ngón thứ hai chạm xuống → `cancelLongPress()`. | Cùng slop với `IconDragGesture` |
| D4 | Đã ở edit mode → listener trả `false`: không rung, không làm gì thêm. Thả tay vẫn là click → thoát edit mode **như hôm nay** (hành vi cũ của `onEmptyAreaClick`, không đổi). | Muốn "thoát bằng chạm khoảng trống" khác đi thì hỏi chủ dự án, ngoài GP |
| D5 | Cổng chặn trong `HomePagerController` (không trong ViewModel): chỉ vào khi `!editMode`, `iconMenu == null`, `openFolderId == null`, `draggingItemId == null`, `!search.isOpen`, `DragHelper.currentSession == null`. Không thỏa → trả `false`. | Tránh sửa `HomeViewModel` (độc quyền) |
| D6 | **Nhấn giữ khoảng trống của dock và hàng pill vẫn không làm gì** (như hôm nay). Dock có cơ chế chạm riêng (`DockView`) nên không "miễn phí". | Chủ dự án chốt 2026-11-05 |
| D7 | `renderGridStatus`: `homePager` và `dockView` hiện khi `Ready` hoặc `Empty`; `searchPill` chỉ hiện khi `Ready`; `Loading` vẫn ẩn cả ba. | Chủ dự án chốt 2026-11-05 (dock hiện khi Empty để app trong dock còn mở được) |
| D8 | Không thêm chữ gợi ý trên màn rỗng; **bỏ hẳn** dòng "Không có app nào để hiển thị" (view `grid_empty_message`, dòng hiển thị trong `renderGridStatus`, chuỗi `home_grid_empty`). Home rỗng chỉ còn ảnh nền và dock. | Chủ dự án chốt 2026-11-05 (sau kiểm tay) |

## Ngoài phạm vi / ghi để sau

- Nhấn giữ khoảng trống của dock, của hàng pill, và của lưới trong panel thư mục / App Library.
- **Không có trang nào trong DB** (0 trang): chỉ xảy ra nếu lần dựng bố cục đầu chạy khi máy không có app nào chưa ẩn (`LayoutReconciler` dòng ~100 trả về trước khi tạo trang). Khi đó adapter rỗng, không có lưới để chạm. Hiện không đạt được từ UI (ẩn app luôn giữ ≥ 1 trang). Nếu muốn chịu cả ca này thì cần trang giả khi `Empty` — làm mốc riêng.
- (Đã đưa vào GP theo chủ dự án 2026-11-05) `Empty` chỉ xét các trang lưới, không xét dock: khi mọi app nằm trong dock (comment ở `LayoutReconciler` dòng ~101) thì dock bị ẩn và app trong dock không mở được. Nay dock hiện cả khi `Empty` (D7).
- Hoạt ảnh vào edit mode khác khi vào từ khoảng trống.

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/GapLongPressGesture.kt` | Tạo (~40 dòng) | Không |
| `app/src/main/java/gambi/global/os26launcher/ui/home/HomePagerAdapter.kt` | Sửa: thêm 1 tham số constructor `onGapLongPress: () -> Boolean`, 1 dòng gắn trong `onCreateViewHolder` | Không độc quyền. **G2c có thể sửa file này** (widget): GP chỉ thêm, nhánh merge sau rebase |
| `app/src/main/res/layout/activity_home.xml`, `res/values/strings.xml` | Sửa: xóa `grid_empty_message` và `home_grid_empty` (ngoại lệ nhỏ được chủ dự án duyệt 2026-11-05; G2c sửa phần khác của file) | `activity_home.xml` độc quyền, chỉ đoạn này |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/HomePagerController.kt` | Sửa: truyền `onGapLongPress` ở `setup()` (kèm hàm cổng chặn D5), sửa 1 dòng ở `renderGridStatus` (D7) | Không độc quyền. G2c ít khả năng đụng |
| `docs/plans/GP-gap-long-press.md`, `docs/knowledgements/GP-gap-long-press.md` | Tạo | Không |

**Không đụng:** `IconGridView.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `HomeActivity.kt`, `DragHelper.kt`, dimens/colors (ngoại lệ duy nhất: xóa dòng chữ màn rỗng ở `activity_home.xml` và `strings.xml`). Nếu trong lúc làm thấy cần một trong số đó: dừng và báo (Workflow 5.1).

## Global Constraints

- `domain` không import `android.*`; GP chỉ đụng tầng `ui`.
- Trạng thái "đang ở edit mode" lấy từ `screen.ui` (nguồn là ViewModel); `GapLongPressGesture` chỉ giữ tọa độ DOWN để tính slop, không giữ bản sao nghiệp vụ (bài học M7 Task 5).
- Không làm hỏng: vuốt trang, vuốt xuống mở tìm kiếm, vuốt trái trang cuối mở Library, nhấn giữ icon/thư mục, bấm pill, dock, thoát edit mode (Xong / Back / chạm khoảng trống).
- Listener chạm trên lưới **luôn trả `false`** (chỉ quan sát).
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự. Build: `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"`, không chạy `gradlew --stop`.

## Review Focus

- **Thoát edit mode ngay sau khi vào:** thả tay sau nhấn giữ khoảng trống không được bắn `onEmptyAreaClick` (kịch bản (a)).
- **Slop:** trôi tay vài dp trong lúc giữ phải hủy (nhất là hướng dọc — `View` không tự hủy trong view); vuốt ngang chậm, vuốt xuống tìm kiếm không được vào edit mode.
- **Trùng với nhấn giữ icon:** nhấn giữ trúng icon/thư mục vẫn ra menu/edit mode như cũ, không ra hai lần, không rung hai lần.
- **Library / TL1:** nhấn giữ khoảng trống ở trang thật cuối rồi vuốt trái → vào edit mode, Library **không** mở (`canOpenLibrary` có `!editMode` và `LibraryOverlayController` hỏi lại lúc MOVE), pager lật bình thường.
- **Tìm kiếm:** giữ rồi kéo xuống sau khi đã vào edit mode không mở tìm kiếm (`enabled` có `!editMode`, truyền theo từng sự kiện).
- **Trạng thái `Empty`:** pager hiện, chạm khoảng trống nhận được; pill vẫn ẩn, dock hiện (app trong dock mở được); khởi động lạnh (`Loading`) không chớp pager; ẩn nốt app cuối trong edit mode không chớp.
- **Cổng chặn D5:** menu icon mở, panel thư mục mở, đang kéo → nhấn giữ khoảng trống không vào/không rung.
- **TalkBack:** lưới thành long-clickable, không làm lỗi điều hướng.

---

### Task 1: `GapLongPressGesture` + gắn vào lưới (trạng thái `Ready`)
**Bắt buộc kiểm tay:** không (chủ dự án chốt 2026-11-05: tự kiểm cử chỉ trên Live 4, gồm trôi tay và ngón thứ hai, trong bài kiểm cuối; agent đi thẳng Task 1 → Task 2 rồi báo một lần). Lý do mục 4.2 vẫn đúng về mặt kỹ thuật: đây là cử chỉ chạm mới và giả thuyết 6 chỉ máy thật trả lời.
**Files:** tạo `ui/home/view/GapLongPressGesture.kt`; sửa `ui/home/HomePagerAdapter.kt`, `ui/home/controller/HomePagerController.kt`.
**Interfaces:** Consumes `HomeViewModel.enterEditMode()`, `HomeScreenState.ui/search`, `DragHelper.currentSession`. Produces `GapLongPressGesture.install(grid: View, onLongPress: () -> Boolean)`; tham số `onGapLongPress: () -> Boolean` của `HomePagerAdapter`.

- [x] **Step 1:** Tạo `GapLongPressGesture` (`internal object`, cùng kiểu `IconDragGesture`): `install` gọi `grid.setOnLongClickListener { onLongPress() }` (trả `true` = đã xử lý → `View` rung và chặn click) và `grid.setOnTouchListener(...)`: DOWN lưu `rawX/rawY`; MOVE nếu `hypot(Δ) > scaledTouchSlop` → `v.cancelLongPress()`; `ACTION_POINTER_DOWN` → `v.cancelLongPress()`; luôn trả `false`.
- [x] **Step 2:** `HomePagerAdapter`: thêm tham số `onGapLongPress: () -> Boolean` (đặt cạnh `onFolderLongPress`), gọi `GapLongPressGesture.install(this, onGapLongPress)` trong `onCreateViewHolder` ngay sau khi tạo `IconGridView`.
- [x] **Step 3:** `HomePagerController.setup()`: truyền `onGapLongPress = ::onGapLongPress`; hàm cổng chặn D5 trả `true` sau khi gọi `viewModel.enterEditMode()`, trả `false` nếu không thỏa.
- [x] **Step 4:** Build xanh (`assembleDebug`), commit `feat: enter edit mode on gap long press`, push, ghi Nhật ký.
- [ ] **Step 5: Kịch bản kiểm (Live 4, bố cục có icon, không edit mode)** — (a) giữ khoảng trống giữa các icon ~1s, không nhúc nhích: rung 1 lần, icon rung, hiện Cài đặt/Xong; **nhả tay không thoát edit mode**. (b) chạm xuống khoảng trống, trôi ~12dp theo mỗi hướng (trên/dưới/trái/phải) rồi giữ 1,5s: không vào edit mode. (c) vuốt ngang chậm giữa các trang, vuốt xuống tìm kiếm, vuốt trái ở trang cuối mở Library: không ra edit mode, vẫn mở/lật bình thường. (d) trang cuối: giữ khoảng trống để vào edit mode, **không nhấc tay**, vuốt trái: Library không mở. (e) giữ icon, giữ thư mục: như cũ. (f) đã ở edit mode: giữ khoảng trống không rung thêm; nhả → thoát edit mode như cũ. (g) bấm pill, dock, Xong, Back như cũ. (h) hai ngón cùng chạm khoảng trống: không vào edit mode.

---

### Task 2: Pager hiện khi `Empty` + kiểm lối thoát
**Bắt buộc kiểm tay:** không — gộp vào bài kiểm cuối.
**Files:** sửa `ui/home/controller/HomePagerController.kt` (`renderGridStatus`).
**Interfaces:** Consumes `GridStatus`. Produces không.

- [x] **Step 1:** `renderGridStatus`: `homePager.visibility = if (gridReady || state.gridStatus == GridStatus.Empty) VISIBLE else INVISIBLE`; `dockView` hiện cùng điều kiện với pager (chủ dự án chốt 2026-11-05); `searchPill` giữ `gridReady`. Cập nhật comment.
- [x] **Step 2:** Build xanh, commit `fix: keep the pager and dock visible when the grid is empty`, push, ghi Nhật ký, báo điều phối.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — dữ liệu: ẩn hết app lưới (và dock) bằng dấu "−" hoặc điều phối làm sẵn. (a) Home chỉ còn ảnh nền (không còn dòng chữ), pill ẩn; nếu dock còn app thì dock hiện và mở được app; giữ ở giữa màn hình: rung, vào edit mode, hiện Cài đặt/Xong. (b) Cài đặt → Ứng dụng đã ẩn → bỏ ẩn một app → về Home: app hiện lại, thoát được edit mode. (c) Ở `Empty`: Back, nút Xong, chạm khoảng trống đều thoát edit mode; vuốt xuống không mở tìm kiếm, vuốt trái không mở Library (như cũ). (d) Khởi động lạnh khi có app: không thấy chớp pager lúc `Loading`. (e) Ở edit mode có icon, ẩn nốt app cuối: không chớp, vẫn ở edit mode, rồi giữ/Back/Xong hoạt động. (f) Chỉ ẩn app lưới, giữ app trong dock (ca "mọi app nằm trong dock"): dock hiện, mở được app từ dock; giữ khoảng trống trang vẫn vào edit mode.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Vsmart Live 4 (cài `fix/gp-gap-long-press@<commit>`). Dữ liệu: bố cục thường có ≥ 2 trang, 1 thư mục; sau đó ẩn hết app. Thứ tự: Task 1 (a)-(h) trên bố cục thường → Task 2 (a)-(e) → hồi quy: kéo-thả icon, lật trang khi kéo sát mép, dấu "−", menu nhấn giữ icon, mở/đóng Library, tìm kiếm bằng vuốt xuống và bấm pill.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-05 | — | Viết plan | `docs: add GP plan` | Chờ điều phối rà |
| 2026-11-05 | — | Plan duyệt; sửa: bỏ dừng kiểm tay ở Task 1, dock hiện khi Empty (D6-D8) | `docs: approve GP plan, show dock when empty` | Chủ dự án chốt 2026-11-05 |
| 2026-11-05 | 1, 2 | `GapLongPressGesture`, tham số adapter, cổng chặn; pager + dock hiện khi Empty. Build xanh | xem PR | Chưa kiểm tay (chủ dự án kiểm cả bài trên Live 4) |
| 2026-11-05 | 3 | Bỏ hẳn dòng chữ "Không có app nào để hiển thị" (view, dòng hiển thị, chuỗi; grep xác nhận không nơi nào khác dùng) theo chủ dự án, sau khi chủ dự án kiểm Live 4 đạt ở `539ca5f` | xem PR | Build xanh |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: TT#8 thêm "nhấn giữ khoảng trống trang Home vào edit mode (GP)"; xóa/sửa dòng giới hạn "lối vào Cài đặt chỉ có trong edit mode" ở mục ghi để sau.
- `project_structure.md`: thêm `GapLongPressGesture` vào cây `ui/home/view/`; ghi pager và dock hiện ở `Empty` (pill ẩn).
- `room-schema.md`: không.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement.
