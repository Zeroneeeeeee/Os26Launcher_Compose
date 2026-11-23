# M7 — Chế độ chỉnh sửa & menu nhấn giữ icon — Implementation Plan

> **Dành cho agent thực thi:** làm theo đúng quy ước dự án trong `docs/AGENTS.md` — code từng Task, báo cáo, **chờ người dùng xác nhận trên máy thật** rồi mới tick hoàn thành và sang Task sau. Dự án **không có test tự động** (A7) — mọi bước "kiểm chứng" dưới đây là kiểm chứng tay trên Vsmart Live 4 (máy mặc định, A8) hoặc Oppo A3x (chỉ khi liên quan kính dock). Claude không build/cài được từ môi trường của mình (không JDK/adb) — người dùng build (`gradlew.bat assembleDebug`), cài, chạy kịch bản, dán log/kết quả lại.

**Goal:** Hoàn thiện TT#7 (menu nhấn giữ icon: shortcut, thông tin app, gỡ cài đặt) và TT#8 (chế độ chỉnh sửa: kéo-thả, ẩn/xóa icon khỏi trang) theo đúng mốc M7 đã chốt trong `project_structure.md`.

**Architecture:** Kéo-thả tự viết tay kiểu Launcher3 (Hướng A đã duyệt) — `DragLayer` là overlay trong suốt trên cùng `activity_home.xml`, vẽ icon đang kéo; `DragHelper` là coordinator Kotlin thuần theo dõi phiên kéo; `IconGridView`/`DockView` tự hit-test ô bằng `GridMetrics` qua interface `DragDropTarget`. Mọi quyết định nghiệp vụ (đẩy-dồn, giới hạn dock, ghi DB) nằm ở `domain/usecase`, gọi qua `HomeViewModel`, giữ đúng luật `ui → domain ← data`. Nhấn giữ icon: menu TT#7 hiện ngay, di tay quá ngưỡng slop thì menu đóng và chuyển thành kéo thật.

**Tech Stack:** View/XML (B1), MVVM + StateFlow (A4), Room (A3), Hilt (A2), `LauncherApps` (ShortcutQuery, uninstall), `liquidglass` v2.0.11 (không đổi, chỉ hiển thị icon kéo *trên* kính, không chạm vào `core/glass`).

**Spec:** thiết kế đã duyệt qua hội thoại brainstorm 2026-10-28 (xem tóm tắt quyết định ở mục "Global Constraints" dưới).

## Global Constraints

- Vào edit mode: nhấn giữ **bất kỳ icon nào**; thoát: nút "Xong" cố định **hoặc** tap vùng trống/Home/Back.
- Dấu xóa nhanh "**−**" ở **góc trên-trái** icon khi đang edit mode, bấm là ẩn (không cần mở menu).
- Kéo-thả hoạt động cả hai chiều **dock ↔ lưới chính**; dock tối đa 4 ô (A10), **không cho thả khi đầy** (icon tự bay về vị trí cũ, không ghi DB).
- Kéo icon ra sát mép màn hình trong edit mode tự lật trang.
- Thả vào ô đã có icon khác (lưới chính) → **đẩy-dồn** icon kia sang ô trống gần nhất cùng trang. Nếu trang đã đầy hẳn (24/24 ô `4×6`) thì **không chặn thả** — icon bị đẩy ra tiếp tục **cascade sang ô (0,0) của trang kế tiếp** (áp lại đúng quy tắc đẩy-dồn ở trang đó); hết trang kế thì **tự tạo 1 trang mới ở cuối** để nhận nó. Việc này chỉ tính **lúc thả tay** (không có live preview xuyên trang khi đang kéo — chỉ trang hiện tại mới thấy preview đẩy-dồn khi đang kéo).
- Kéo hết icon ra khỏi 1 trang khiến trang đó **trống hẳn vẫn KHÔNG tự xóa** (đã khảo sát trên iPhone XS Max — hành vi thật của iOS).
- Thả đè lên icon khác **không tạo thư mục** — thư mục là việc của G2 (TT#10), ngoài phạm vi M7.
- Chỉ kéo **từng icon một**, không chọn nhiều.
- Gỡ app bằng hệ thống khi đã có bố cục tự kéo: **giữ hành vi dồn ô cũ** của M4 (không đổi sang giữ ô trống) — theo quyết định của chủ dự án.
- "Ẩn icon khỏi trang" (dấu "−", sau này cả menu TT#7) = ghi `hidden_apps` + xóa placement + **DỒN trong cùng trang** (chốt lại 2026-10-29, thay bản cũ "không dồn ô"): mọi icon đứng SAU icon bị ẩn theo thứ tự đọc (`y` rồi `x`) dịch về trước đúng 1 bậc — đối xứng với chèn ở `GridInsertPlanner` (icon đầu trang kế KHÔNG kéo sang), không xóa trang trống (đồng nhất với bullet cascade phía trên). Kéo icon ra khỏi chỗ cũ vẫn để lại ô trống (không dồn) — đó là thao tác khác. Có 1 màn **"Thư viện ứng dụng"** tối giản trong M7: 2 mục **"Tất cả"** (mọi app, bấm vào thì mở app) và **"Đã ẩn"** (bấm vào thì bỏ ẩn, `UnhideAppUseCase` đặt lại vào ô trống đầu tiên, tái dùng cơ chế `placeNewApps`) — **không có** nút ẩn/xóa trực tiếp trên màn này (đúng cơ chế app mẫu). Thiết kế để TT#11/G2 (App Library đầy đủ, có phân loại) mở rộng thêm mà không phải viết lại.
- Menu nhấn giữ icon (TT#7) gồm đủ 3 mục: shortcut động, thông tin ứng dụng, gỡ cài đặt.
- Mọi thao tác ghi `launcher_items`/`hidden_apps` từ M7 phải đi qua cùng khóa (`Mutex`) với `LayoutReconciler` (M4) để không đụng race với sự kiện cài/gỡ app chạy nền.
- Không phá luật phụ thuộc: `domain` không import `android.*` (trừ lý do ghi rõ); `ui` không import `data` trực tiếp.

## Review Focus

- Thả icon vào **dock** đã đầy hẳn (4/4) → icon phải bay về đúng vị trí cũ, không mất khỏi bố cục, không ghi DB sai. (Lưới chính KHÔNG có ca "không cho thả" — xem Global Constraints: trang đầy thì cascade sang trang kế/trang mới, không bao giờ chặn thả.)
- Thả vào trang lưới cuối cùng đã đầy hẳn và liên tục kéo thêm nhiều icon khác vào cùng trang đó → mỗi lần cascade phải tạo đúng 1 trang mới khi cần (không tạo dư trang rỗng, không tái sử dụng nhầm trang đã tồn tại).
- Đúng lúc đang kéo tay thì có sự kiện cài/gỡ app khác chạy ở `packageEvents` (nền) → không được ghi đè/làm hỏng bố cục của nhau (kiểm bằng `Mutex` chung).
- App không hỗ trợ shortcut động, hoặc `LauncherApps.getShortcuts()` ném `SecurityException`/ngoại lệ trên ROM lạ → menu vẫn hiện được 2 mục còn lại, không crash.
- Kéo icon đi qua vùng dock (có kính) → icon đang kéo (trong `DragLayer`) phải luôn hiện rõ **trên** cả `RealGlassSurface` và `StaticBlurSurface`, không bị kính "chụp nhầm" làm mờ hay biến mất.
- Ẩn một app rồi gỡ cài đặt thật app đó (app không còn tồn tại) → hàng trong `hidden_apps` phải được dọn theo sự kiện `PACKAGE_REMOVED`, không để sót hàng mồ côi hiện sai trong màn "Thư viện ứng dụng".

---

### Task 1: Domain + Data — nền tảng di chuyển/ẩn item [HOÀN THÀNH 2026-10-28]

> **2 ruling phát sinh lúc code (khác bản kế hoạch gốc ở dưới, giữ lại để biết lý do):**
> - **Ruling 1 — không dùng `rank` cho lưới chính.** Đọc lại `LayoutReconciler`/`LayoutRepositoryImpl` (M4) mới thấy `rank` chỉ có ý nghĩa cho **dock**; thứ tự lưới chính luôn là `gridX`/`gridY` thuần. Vì vậy **không** thêm `itemsInContainer`/`updatePlacement`/`deleteItem` vào `LauncherItemDao` như kế hoạch gốc — tái dùng nguyên `itemDao.getAll()/updateAll()/deleteByIds()` đã có, và thuật toán chèn/đẩy-dồn viết thẳng trong `LayoutReconciler` theo đúng kiểu quét ô `placeNewInTransaction` đã có.
> - **Ruling 2 — không dùng `withLayoutLock` lộ ra ngoài, không tái dùng `compactHome`.** `compactHome()` (M4, dùng cho gỡ app thật) làm reflow toàn lưới **và xóa trang trống cuối** — ngược quyết định M7 "không tự xóa trang trống". `removeItem()`/`setHidden()` mới chỉ xóa đúng 1 hàng, giữ `Mutex` **private** như cũ (không expose `withLayoutLock`), các hàm mới (`moveItem`/`removeItem`/`setHidden`) tự `mutex.withLock { ... }` bên trong `LayoutReconciler`, không đổi API ra ngoài.
>
> Hai ruling trên đã khiến hành vi "trang đầy" implement đúng quyết định **cascade** đã chốt ở vòng khảo sát iOS thứ 2 (không phải "không cho thả" như mô tả cũ trong Global Constraints/Review Focus/Task 5 dưới đây — các mục đó **chưa được đồng bộ lại** sau vòng khảo sát đó, xem đề xuất cập nhật ở cuối Task 1).
>
> **Đổi thuật toán "đẩy-dồn" 2026-10-28 (sau khi Task 5 code+test tay lần đầu):** phần thuật toán
> "occupant nhảy tới Ô TRỐNG GẦN NHẤT" ở trên — tuy đã code đúng như mô tả và qua 5/5 PASS ở Step 8 —
> **bị thay** bằng dồn dây chuyền (occupant + mọi icon sau nó theo thứ tự đọc dịch lên 1 bậc), sau khi
> khảo sát thêm trên iPhone XS Max cho thấy hành vi thật khác với giả định ban đầu. 5/5 PASS ở Step 8
> chỉ còn đúng cho bản thuật toán CŨ — đã sửa `insertIntoPage` sang thuật toán mới tại thời điểm Task 5,
> KHÔNG dựng lại bộ test debug cũ để re-verify (harness đã xoá sau Step 8); việc re-verify giao cho
> chính Task 5 Step 3 (kéo-thả tay thật), vì `onItemDropped → MoveItemUseCase → insertIntoPage` là
> CÙNG một đường gọi. Xem chi tiết thuật toán mới tại doc-comment của `insertIntoPage` trong code.

**Files (thực tế đã code):**
- Create: `app/src/main/java/gambi/global/os26launcher/data/layout/db/HiddenAppDao.kt` — `observeAll()`, `insert(entity)`, `delete(packageName, className, userSerial)`
- Modify: `app/src/main/java/gambi/global/os26launcher/data/layout/db/LauncherDatabase.kt` — thêm `abstract fun hiddenAppDao(): HiddenAppDao` (không đổi `version`, entity đã có từ schema v1)
- Modify: `app/src/main/java/gambi/global/os26launcher/di/DatabaseModule.kt` — `provideHiddenAppDao`
- Modify: `app/src/main/java/gambi/global/os26launcher/data/layout/LayoutReconciler.kt` — thêm `hiddenAppDao` vào constructor; thêm `moveItem()`, `removeItem()`, `setHidden()` (public, tự khóa `mutex`) + helper private `insertIntoPage()` (đệ quy, đẩy-dồn/cascade sang trang kế hoặc tạo trang mới), `firstFreeCell()`, `moveToDockInTransaction()`
- Modify: `app/src/main/java/gambi/global/os26launcher/domain/repository/LayoutRepository.kt` — thêm `hiddenApps`, `moveItem(...)`, `removeItem(...)`, `setHidden(...)`
- Modify: `app/src/main/java/gambi/global/os26launcher/data/layout/LayoutRepositoryImpl.kt` — thêm `hiddenAppDao` vào constructor, implement 4 thành viên mới (chuyển tiếp xuống `LayoutReconciler`)
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/MoveItemUseCase.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/HideAppUseCase.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/UnhideAppUseCase.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/GetHiddenAppsUseCase.kt`

**Interfaces (thực tế):**
- Produces: `LayoutRepository.moveItem(itemId: Long, targetContainer: Long, targetPageId: Long?, targetCell: GridPosition): Boolean` — dock: `false` nếu đầy (không ghi gì); lưới chính: luôn `true`, tự cascade khi trang đầy (đẩy 1 item sang ô (0,0) trang kế, hết trang kế thì tạo trang mới ở cuối)
- Produces: `LayoutRepository.removeItem(itemId: Long)` — xóa 1 hàng, **không dồn ô, không xóa trang trống**
- Produces: `LayoutRepository.hiddenApps: Flow<List<AppItem>>`, `LayoutRepository.setHidden(key: ComponentKey, userSerial: Long, hidden: Boolean)`
- Produces: `MoveItemUseCase`, `HideAppUseCase`, `UnhideAppUseCase`, `GetHiddenAppsUseCase` — operator `invoke` mỏng, gọi thẳng `LayoutRepository`
- Consumes: `LayoutReconciler.placeNewInTransaction(...)` nội bộ (tái dùng trong `setHidden(hidden=false)`, coi app bỏ ẩn như app mới)

- [x] **Step 1–7 (gộp theo thực tế, xem Files/Interfaces phía trên):** viết `HiddenAppDao` + đăng ký DB/Hilt, thêm `moveItem/removeItem/setHidden` vào `LayoutReconciler` theo 2 Ruling ở trên, lộ qua `LayoutRepository`/`LayoutRepositoryImpl`, thêm 4 usecase mỏng.

- [x] **Step 8: Kiểm chứng tay (Vsmart Live 4)** — thêm tạm nút debug ở `HomeActivity` (góc trên-trái, dưới `home_status`) gọi 1 hàm tạm `LayoutRepository.debugRunM7Test()` tự chạy đủ 5 kịch bản (move trong trang; cascade sang trang kế có sẵn; cascade tạo trang mới; dock đầy bị từ chối; ẩn+bỏ ẩn round-trip), hiện kết quả PASS/FAIL qua `AlertDialog` (không dùng Toast vì nhiều dòng). Kịch bản 2/3/4 dùng item nhân bản tạm từ 1 app thật, tự dọn ở `finally`; kịch bản 1/5 dùng item thật, tự khôi phục lại vị trí/trạng thái cũ sau khi kiểm. **Kết quả người dùng báo: 5/5 PASS.** Đã xóa sạch nút debug + hàm `debugRunM7Test()` + sự kiện `HomeEvent.M7DebugResult` + string `home_debug_m7` ngay sau khi xác nhận (diff cuối cùng trong git chỉ còn code production, không còn gì tạm).

- [x] **Step 9: Commit** (xem Files phía trên cho danh sách đầy đủ)

**Cập nhật 2026-10-28 (sau khi Task 1 pass 5/5 trên máy thật):** đã đồng bộ lại Global Constraints, Review Focus, Task 5 (Step 3), Task 9 (đổi tên + redesign thành "Thư viện ứng dụng" 2 mục Tất cả/Đã ẩn) theo đúng quyết định cuối của vòng khảo sát iOS lần 2 (cascade; không tự xóa trang trống); đã xóa placeholder Task 11, nội dung gộp vào Global Constraints + Task 1. M7 còn 10 Task.

---

### Task 2: UI — hit-test ô trong `GridMetrics` [HOÀN THÀNH 2026-10-28]

**Files (thực tế):**
- Modify: `app/src/main/java/gambi/global/os26launcher/core/ui/GridMetrics.kt` — thêm `columns`/`rows` vào `Cells` (để `cellAt` tự quy đổi mà không cần `GridMetrics` gốc) + hàm `Cells.cellAt(px, py)`

**Interfaces:**
- Produces: `GridMetrics.Cells.cellAt(px: Float, py: Float): GridPosition?` — gộp cả phần gutter vào ô đứng trước nó (không có vùng chết), vì hàm chạy liên tục trong lúc kéo tay (Task 5+); `null` nếu điểm nằm ngoài vùng lưới/padding hoặc quá biên cột/hàng cuối.

- [x] **Step 1: `cellAt(px, py)`** — dùng `cellWidth/cellHeight/gutterX/gutterY/paddingLeft/paddingTop` đã có trong `Cells`, cộng `columns/rows` mới thêm, quy đổi ngược tọa độ pixel về `(gridX, gridY)`.

- [x] **Step 2: Kiểm chứng tay (Live 4)** — thêm tạm `IconGridView.onInterceptTouchEvent` (luôn trả `false`, không chặn click) log `cellAt(ev.x, ev.y)` ra logcat tag `M7DebugCellAt` mỗi lần `ACTION_MOVE`. Người dùng vuốt qua các ô, log khớp đúng icon đang hiện ở từng vị trí. Đã xóa log tạm + import thừa ngay sau khi xác nhận (diff cuối cùng của `IconGridView.kt` = rỗng).

- [x] **Step 3: Commit** (commit `c8a9825`)

---

### Task 3: `DragLayer` + `DragHelper` — khung kéo (chưa nối icon thật) [HOÀN THÀNH 2026-10-28]

> **Ruling phát sinh lúc code:** `DragHelper` ở `core/ui` không nên import ngược `DragLayer` (ở
> `ui/home/view`, ngược hướng core→feature) — thêm interface tối giản `DragHelper.DragVisual` (nested)
> mà `DragLayer` implement; `HomeActivity` nối bằng `DragHelper.attachDragVisual`/`detachDragVisual`.
>
> **Phát hiện lúc kiểm chứng tay (quan trọng cho Task 4/5):** `home_root` (root `FrameLayout`) KHÔNG
> nhận được chạm trực tiếp vì `ViewPager2` trong `home_content` giữ luồng chạm cho chính nó trước, chạm
> không bao giờ lan tới listener gắn trên `home_root`. Và: Android chỉ gửi tiếp `ACTION_MOVE`/`UP` của
> 1 cử chỉ cho view đã **trả `true`** ở `ACTION_DOWN` — `DragLayer.dispatchTouchEvent` trả `false` cố
> định (đúng thiết kế, để không chặn chạm) nên **không** phải nơi để tự bắt gesture thật. Việc này xác
> nhận đúng thiết kế đã chốt trong plan: long-press + theo dõi `ACTION_MOVE`/slop PHẢI làm ở
> `IconCellView`/`IconGridView` (nơi chạm thật sự đi tới và tự `return true` để giữ cử chỉ) — Task 4
> Step 3 và Task 5 Step 2 — tuyệt đối không thử bắt gesture ở `home_root` hay ở `DragLayer`.

**Files (thực tế đã code):**
- Create: `app/src/main/java/gambi/global/os26launcher/core/ui/DragDropTarget.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/core/ui/DragHelper.kt` — gồm cả `DragVisual` (ruling trên) và `DragSession`
- Create: `app/src/main/java/gambi/global/os26launcher/ui/home/view/DragLayer.kt`
- Modify: `app/src/main/res/layout/activity_home.xml` — thêm `<DragLayer>` làm con cuối cùng (z-order cao nhất) của root `FrameLayout`

**Interfaces (thực tế):**
- Produces: `DragLayer.startDrag(bitmap, startX, startY)`, `DragLayer.moveTo(x, y)`, `DragLayer.endDrag()` — implement `DragHelper.DragVisual`
- Produces: `DragDropTarget { onDragEnter(); onDragMove(x, y); onDragExit(); onDrop(x, y): Boolean }`
- Produces: `DragHelper.DragSession(itemId, sourceContainer, sourcePageId, source)`, `DragHelper.attachDragVisual(visual)`/`detachDragVisual()`, `beginDrag(session, bitmap, x, y)`, `updatePosition(x, y, targetsUnderPoint)`, `drop(x, y, target): Boolean`, `currentSession: DragSession?` (đọc được, `private set`)

- [x] **Step 1: `DragLayer`** — `ViewGroup` rỗng `clipChildren=false`, `setWillNotDraw(false)` (ViewGroup mặc định bỏ `onDraw` nếu không có background), `dispatchTouchEvent` trả `false` cố định, `onDraw` vẽ bitmap căn giữa tại toạ độ đang giữ.

- [x] **Step 2: `DragHelper`** — `object`, giữ `currentSession`; `updatePosition` phát `onDragEnter`/`onDragExit` đúng 1 lần mỗi lượt đổi target hover, `onDragMove` cho mọi target hiện tại; `drop` luôn gọi `onDragExit` cho các target đang hover trước khi dọn session.

- [x] **Step 3: Nối khung vào `activity_home.xml`** — đã thêm `DragLayer` cuối cây View.

- [x] **Step 4: Kiểm chứng tay (Live 4)** — ban đầu gắn thử listener lên `home_root` → không nhận được chạm (xem Ruling/Phát hiện ở trên), sửa sang gắn trực tiếp vào `DragLayer` qua 1 property debug tạm (`debugRawTouchListener`, kèm `dispatchTouchEvent` trả `true` tạm thời khi có listener để giữ nguyên cử chỉ) + `Handler.postDelayed` tự dò long-press trong `HomeActivity`. Người dùng xác nhận: ô vuông đỏ bay theo tay mượt, hiện rõ trên kính dock khi quét qua. Đã gỡ sạch property debug + `dispatchTouchEvent` quay lại `return false` cố định + đoạn test ở `HomeActivity` (diff cuối cùng của `DragLayer.kt`/`HomeActivity.kt` chỉ còn đúng phần khung thật, không còn gì tạm).

- [x] **Step 5: Commit** (commit `e4c08d3`)

---

### Task 4: Vào/thoát chế độ chỉnh sửa [HOÀN THÀNH 2026-10-28]

> **2 việc nhẹ làm thêm sau khi test pass trên máy thật (theo yêu cầu người dùng, không đổi
> Files/Interfaces):** góc lắc `JIGGLE_DEGREES` tăng gấp đôi 2.5°→5° (dễ thấy hơn khi test); click vào
> icon trong lúc `editMode=true` bị chặn (`cell.setOnClickListener { if (editMode) return@... ; ... }`
> trong `IconGridView.wireClickListeners()`) — đúng hành vi iOS: chạm icon lúc đang rung không mở app.

**Files:**
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeUiState.kt` — thêm `val editMode: Boolean = false`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeViewModel.kt` — thêm `fun enterEditMode()`, `fun exitEditMode()`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt` — nút "Xong", bắt tap vùng trống + `onBackPressedDispatcher` thoát edit mode khi đang bật
- Modify: `app/src/main/res/layout/activity_home.xml` — thêm `btn_done` (ẩn khi `editMode=false`)
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconGridView.kt` — hiệu ứng rung (jiggle) khi `editMode=true`

**Interfaces:**
- Consumes: `HomeUiState.editMode` (Task 5–8 đọc field này để quyết định có cho kéo/hiện badge không)
- Produces: `HomeViewModel.enterEditMode()`, `HomeViewModel.exitEditMode()`

- [x] **Step 1: `HomeUiState.editMode`** và 2 hàm trong `HomeViewModel` cập nhật `StateFlow`.

- [x] **Step 2: Jiggle** — `IconGridView` khi nhận `editMode=true` chạy `ValueAnimator` xoay nhẹ (±5°,
  thực tế tăng gấp đôi so với kế hoạch gốc ±2-3° — xem ghi chú đầu Task) lặp vô hạn trên mọi
  `IconCellView` con, lệch pha ngẫu nhiên nhỏ mỗi icon để không đồng bộ cứng; dừng animator khi
  `editMode=false`.

- [x] **Step 3: Nhấn giữ icon → `enterEditMode()`** — tạm nối trong `IconGridView.wireClickListeners()`
  qua `cell.setOnLongClickListener { onLongPressIcon?.invoke(); true }`, `HomePagerAdapter` nối
  `onLongPressIcon = viewModel::enterEditMode` (chưa nối menu TT#7 — để Task 10).

- [x] **Step 4: Thoát** — `btn_done.setOnClickListener { viewModel.exitEditMode() }`;
  `IconGridView.onEmptyAreaClick` (chạm vùng trống trong trang, không rơi vào `IconCellView` nào) gọi
  `exitEditMode()` — nối qua `setOnClickListener` thường trên chính `IconGridView`, không phải trên
  `home_root` (xem phát hiện ở Task 3: click đơn giản không có vấn đề `ViewPager2` nuốt touch như
  cử chỉ kéo); `onBackPressedDispatcher` dùng 1 `OnBackPressedCallback` riêng (`enabled=false` ban
  đầu), đăng ký SAU callback rỗng cũ (M1) để được ưu tiên khi bật (LIFO) — bật/tắt theo
  `uiState.editMode` trong `render()`.

- [x] **Step 5: Kiểm chứng tay (Live 4)** — nhấn giữ 1 icon: toàn lưới rung, nút "Xong" hiện. Bấm
  "Xong": rung dừng. Lặp lại, lần này tap vùng trống: rung dừng. Lặp lại, bấm Back: rung dừng (không
  thoát app/launcher). **Người dùng xác nhận ổn**, kèm 2 yêu cầu nhẹ (xem ghi chú đầu Task) đã làm
  luôn trong cùng Task vì không đổi Files/Interfaces.

- [x] **Step 6: Commit**

---

### Task 5: Kéo-thả thật trong 1 trang [HOÀN THÀNH 2026-10-29]

> **Ruling phát sinh lúc code** — `PlacedApp`/`HomePage` (M3) chưa từng mang `itemId`/`pageId` thật,
> chỉ có `position`/`app`/`index`: `MoveItemUseCase.invoke(itemId, ...)` cần id hàng DB thật mà UI
> trước đó không hề có. Đã thêm `itemId: Long` vào `PlacedApp` và `pageId: Long` vào `HomePage` (domain
> model M3, nay mở rộng cho M7) — `LayoutRepositoryImpl.assemble()` điền từ `LauncherItemEntity.id`/
> `HomePageEntity.id` thật; `BuildDefaultLayoutUseCase` (dựng layout NHÁP trước khi ghi DB lần đầu, M3)
> điền `0L` cho cả hai vì giá trị đó không bao giờ được đọc lại ở đường seed (xem comment tại đó). Cũng
> thêm `domain/model/ContainerId.kt` (hằng `HOME=-100L`/`DOCK=-101L`, trùng giá trị với
> `LauncherItemEntity.CONTAINER_HOME/DOCK` ở data) để `IconGridView` (ui) truyền đúng `targetContainer`
> mà không phải import `data.*` (giữ luật `ui` không import `data` trực tiếp) — khác chữ số `-100` viết
> thẳng trong kế hoạch gốc ở Step 2 dưới đây.

**Files (thực tế, bổ sung so với bản gốc):**
- Modify: `app/src/main/java/gambi/global/os26launcher/domain/model/HomePage.kt` — thêm `itemId`/`pageId` (xem Ruling)
- Create: `app/src/main/java/gambi/global/os26launcher/domain/model/ContainerId.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/domain/usecase/BuildDefaultLayoutUseCase.kt`, `app/src/main/java/gambi/global/os26launcher/data/layout/LayoutRepositoryImpl.kt` — điền 2 trường mới
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconGridView.kt` — implement `DragDropTarget`, nối `DragHelper` thật (thay đoạn test tạm Task 3)
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeViewModel.kt` — thêm `fun onItemDropped(...)` gọi `MoveItemUseCase` (inject thêm vào constructor)
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomePagerAdapter.kt`, `HomeActivity.kt` — truyền `onItemDropped`/`page.pageId` xuống, gắn/gỡ `DragHelper.attachDragVisual(binding.dragLayer)` ở `onCreate`/`onDestroy`

**Interfaces (thực tế):**
- Consumes: `DragHelper` (Task 3), `MoveItemUseCase` (Task 1), `GridMetrics.cellAt` (Task 2), `ContainerId.HOME`
- Produces: `HomeViewModel.onItemDropped(itemId: Long, targetContainer: Long, targetPageId: Long?, cell: GridPosition): Unit` (Task 6 dùng lại khi nối dock) — **không suspend**, tự `viewModelScope.launch` nội bộ vì `DragDropTarget.onDrop` là hàm đồng bộ

- [x] **Step 1: `IconGridView implements DragDropTarget`** — `onDragMove` quy đổi toạ độ màn hình về cục bộ rồi gọi `cellAt(x,y)`; đổi ô thì `applyPreview()` dịch tạm (`translationX/Y`, chưa ghi DB) **occupant + MỌI icon sau nó theo thứ tự đọc (dồn dây chuyền, xem Ruling đổi thuật toán ở Task 1)** lên 1 bậc — khớp `LayoutReconciler.insertIntoPage` thật; icon cuối chuỗi tràn ra ngoài 24/24 ô thì ẩn tạm (`alpha=0`, không preview xuyên trang, đúng Global Constraints — cascade chỉ tính lúc thả); `onDragExit` gọi `clearPreview()` trả mọi icon preview về vị trí/alpha gốc. `onDrop` luôn trả `true` (lưới chính không có ca "không cho thả") và gọi `onItemDropped` fire-and-forget.

- [x] **Step 2: Nhấn giữ + di tay quá slop → kéo thật** — theo dõi qua `View.OnTouchListener` gắn thêm trên mỗi `IconCellView` (song song với `OnClickListener`/`OnLongClickListener` đã có — `OnTouchListener` chạy TRƯỚC `onTouchEvent`, không thay thế, nên click/long-click bình thường không bị ảnh hưởng tới khi slop chưa vượt); vượt `scaledTouchSlop` lúc `editMode=true` thì `beginDragFor()` chụp bitmap từ `cell.draw(Canvas(bitmap))`, set `cell.alpha = 0f` (ẩn icon gốc), gọi `DragHelper.beginDrag(DragSession(itemId, sourceContainer = ContainerId.HOME, sourcePageId = pageId, source = this), bitmap, x, y)`. `ACTION_UP`/`CANCEL` gọi `DragHelper.drop(x, y, this)` rồi phục hồi `alpha = 1f` — luôn phục hồi dù thả thành công hay bị từ chối (lưới chính thực tế không bao giờ từ chối, nhưng giữ đường lui cho trường hợp thả ra ngoài vùng lưới hợp lệ, trả `targetCell == null`).

> **2 phát hiện lúc kiểm chứng tay (2026-10-28), đã sửa:**
> - **`ViewPager2` giành lại cử chỉ đang chạy giữa đường** (khác hẳn phát hiện "ai NHẬN được sự kiện" ở
>   Task 3) — xem mục riêng trong `docs/AGENTS.md` ("Ai GIÀNH lại một cử chỉ đang chạy"). Fix:
>   `cell.parent?.requestDisallowInterceptTouchEvent(true)` đúng lúc `beginDragFor()`, trả lại `false`
>   vô điều kiện ở `ACTION_UP`/`CANCEL`.
> - **Icon "chớp" hiện lại ở ô CŨ một khung hình trước khi nhảy sang ô mới** sau khi thả thành công —
>   do `endDragVisualState()` phục hồi `alpha=1` NGAY trong khi DB còn đang ghi bất đồng bộ. Fix: thả
>   THÀNH CÔNG thì giữ `alpha=0`, để đúng lần `bindAllCells()` kế tiếp (dữ liệu mới sau khi DB ghi
>   xong) tự trả `alpha=1`; thả BỊ TỪ CHỐI (ra ngoài vùng hợp lệ) mới phục hồi ngay tại chỗ.
>
> **Hành vi cố ý giữ lại (không fix):** nhấn giữ 1 lần (rung xuất hiện) rồi vuốt đi NGAY trong cùng 1
> lượt chạm (chưa thả tay) vẫn bị hiểu thành vuốt chuyển trang — do `enterEditMode()` đổi qua
> `StateFlow` cần 1 vòng round-trip bất đồng bộ mới tới được field `editMode` cục bộ của
> `IconGridView`, nên field đó có thể CHƯA kịp `true` nếu di tay ngay trong cùng cử chỉ. Phải nhấc tay
> rồi nhấn giữ LẦN THỨ HAI (field đã chắc chắn đồng bộ xong) mới kéo được. Giữ nguyên theo yêu cầu chủ
> dự án: khi Task 10 nối menu TT#7 thật, lượt nhấn giữ đầu tiên sẽ hiện menu (CHƯA vào edit mode, vuốt
> trong lúc này tạm thời chưa có ý nghĩa gì), lượt nhấn giữ thứ hai (đang hiện menu) mới ẩn menu + vào
> edit mode — khớp đúng cấu trúc "2 lượt chạm" đang có, chỉ đổi tác dụng của lượt đầu.

> **Phát hiện 2026-10-29 qua log chẩn đoán (tag `M7Drag`) — cách "ẩn icon bằng `alpha=0` trong view rồi
> đợi dữ liệu mới trả lại" ở Step 2 KHÔNG bền, thay bằng Step 3–7 dưới đây:**
> - Triệu chứng: kéo icon A sang ô mới (ổn), rồi kéo A về đúng ô cũ -> A biến mất (vẫn trong edit mode,
>   thoát edit mode thì hiện lại đúng chỗ). DB và thuật toán dồn dây chuyền đều ĐÚNG (log `insertIntoPage`,
>   `VM pages emit`); `DiffUtil` cũng báo đúng `same=false`.
> - Nguyên nhân gốc: trạng thái "đang chờ dữ liệu mới" (`draggingItemId`/`draggingOriginalPosition`) nằm
>   TRONG `IconGridView`, nhưng `RecyclerView` (nền của `ViewPager2`) dùng animation đổi item mặc định
>   (`DefaultItemAnimator`) -> mỗi lần nội dung 1 trang đổi (không payload) nó cấp **ViewHolder/`IconGridView`
>   MỚI** cho trang đó rồi cross-fade, view cũ về pool. Log: trang 4 luân phiên giữa 2 view (`view=187642979`
>   <-> `234742015`); lần kéo 2 thả xong thì dữ liệu mới rơi vào view còn giữ trạng thái pending CŨ của lần kéo 1
>   (`dragOrig=(3,1)` trùng đúng vị trí mới) -> `stillPending=true` -> giữ `alpha=0` mãi.
> - Bài học: **mọi trạng thái cần sống qua một lần cập nhật dữ liệu không được nằm trong view** (view là hàm
>   của dữ liệu, `RecyclerView` có quyền thay view bất cứ lúc nào). Tham khảo: `LauncherModel` (Launcher3) cập nhật
>   model trong bộ nhớ ĐỒNG BỘ rồi mới ghi DB bất đồng bộ; `DragController` báo nguồn kéo kết quả thả bằng
>   cờ `accepted` rõ ràng (`onDropCompleted`) thay vì suy luận từ dữ liệu.
> - Hướng chọn (đã duyệt 2026-10-29): A (Step 3) + B (Step 4) + C (Step 5–6). Không chọn D (tự viết pager).
>   Mỗi step xong là DỪNG chờ người dùng test tay rồi mới tick/sang step sau.

- [x] **Step 3 (hướng A): Tắt animation đổi item + stable IDs** *(hoàn thành 2026-10-29)* — `HomeActivity.setupHomePager()`:
  `(binding.homePager.getChildAt(0) as RecyclerView).itemAnimator = null`; `HomePagerAdapter`:
  `setHasStableIds(true)` + `override fun getItemId(position) = getItem(position).pageId`. Mục tiêu: đổi nội
  dung trang thì dùng lại ĐÚNG holder cũ (không còn 2 view song song, không cross-fade cả trang icon).
  Chỉ giảm nguy cơ, chưa phải fix gốc. **Kiểm chứng:** chạy lại kịch bản kéo xuôi/ngược của WPS, đọc log
  `M7Drag` xem `view=` của trang 4 có CỐ ĐỊNH không (log chẩn đoán còn nguyên tới Step 7).
  **Kết quả (log `m7drag.log`, Vsmart Live 4):** `view=195033295` CỐ ĐỊNH suốt cả phiên (vào/thoát edit mode + 4 lượt
  kéo liên tiếp gồm kéo xuôi, kéo ngược về ô cũ, đè occupant); mọi lần `bindAllCells` đều `stillPending=false`, icon
  không còn biến mất. Xác nhận nguyên nhân là 2 `IconGridView` song song do animation đổi item. Step 4–6 vẫn giữ để
  gỡ phụ thuộc vào việc RecyclerView có tái dùng holder hay không và sửa khung hình chớp ở ô cũ.

- [x] **Step 4 (hướng B): Trạng thái kéo là DỮ LIỆU, không phải thuộc tính view** *(đã code + người dùng test tay OK 2026-10-29)* — bỏ cặp field
  `draggingItemId`/`draggingOriginalPosition` trong `IconGridView`; `HomeUiState` thêm `draggingItemId: Long?`.
  `IconGridView.setItems(items, pageId, metrics, hiddenItemId: Long?)` và `bindAllCells()` đặt
  `alpha = if (placed.itemId == hiddenItemId) 0f else 1f` cho MỌI ô theo đúng dữ liệu (không suy luận "còn cũ hay
  mới"). `HomePagerAdapter` giữ field `draggingItemId` do `HomeActivity.render()` gán TRƯỚC `submitList`
  (không `notify` thêm để khỏi rebind giữa lúc đang kéo); `IconGridView` thêm callback `onDragStarted(itemId)` /
  `onDragEnded()` nối tới `HomeViewModel.onDragStarted/onDragCancelled`. Thả đúng ô cũ (không đổi gì, DB không
  phát lại) thì trả `alpha=1` NGAY, không chờ. Thả bị từ chối/CANCEL: trả `alpha=1` + `onDragCancelled()`.
  Thả thành công: `HomeViewModel.onItemDropped` xóa `draggingItemId` rồi ghi DB như cũ — lần bind kế tiếp
  (dữ liệu mới) tự ra đúng alpha vì `hiddenItemId = null`. **Kiểm chứng:** kịch bản WPS xuôi/ngược trong
  ảnh lỗi + kéo đè icon khác + thả đúng ô cũ + thả ra ngoài lưới.

- [x] **Step 5 (hướng C1): Tách thuật toán dồn dây chuyền thành hàm thuần ở `domain`** *(đã code + người dùng test tay OK 2026-10-29 — dự án KHÔNG dùng unit test, chỉ kiểm thử thủ công; đã bỏ `GridInsertPlannerTest` và dependency JUnit)* — tạo
  `domain/layout/GridInsertPlanner.kt` (thuần Kotlin, không `android.*`):
  `plan(pages: List<PageSnapshot>, movingItemId, targetPageId, targetCell, columns, rows): InsertPlan` trả danh
  sách vị trí cuối cùng đổi (`itemId, pageId, x, y`) + cờ cần tạo trang mới; cùng đúng thuật toán đã chốt ở
  Task 1 (item TRƯỚC ô đích giữ nguyên, occupant + mọi item sau dịch +1 theo thứ tự đọc, tràn 24/24 thì
  cascade ô (0,0) trang kế/trang mới). `LayoutReconciler.insertIntoPage` gọi hàm này rồi chỉ ghi DB theo kế hoạch
  (hết lặp thuật toán); `IconGridView.applyPreview` giữ nguyên (preview chỉ cần trong 1 trang, không cascade).
  ~~**Unit test JVM** (`GridInsertPlannerTest`)~~ (bỏ: dự án chỉ kiểm thử thủ công) — các ca cần kiểm tay: ô đích trống; đè occupant (kéo xuôi/ngược); item trước ô đích giữ
  nguyên; trang đầy -> cascade sang trang kế; không còn trang kế -> tạo trang mới; di chuyển giữa 2 trang khác
  nhau; kéo về đúng ô cũ không đổi gì. **Kiểm chứng:** lặp lại kịch bản tay Task 5 để chắc `LayoutReconciler` sau refactor không đổi hành vi.

- [x] **Step 6 (hướng C2): Optimistic update ở `HomeViewModel`** *(đã code + người dùng test tay OK 2026-10-29)* — `onItemDropped` gọi `GridInsertPlanner.plan`
  trên `_uiState.value.pages` hiện tại, áp ngay vào danh sách `pages` và xóa `draggingItemId` TRONG CÙNG MỘT lần
  `_uiState.update` (icon xuất hiện ở ô mới tức thì, không còn khung hình ô cũ), rồi mới `launch` ghi DB.
  Chống nháy ngược (emission DB cũ về muộn sau emission lạc quan): đếm `pendingMoves` ở main thread; trong lúc
  `pendingMoves > 0` bỏ qua emission `layoutRepository.pages` nhưng LUÔN lưu `latestDbPages`; ghi DB xong thì
  giảm đếm, về 0 thì áp `latestDbPages` mới nhất (DB là nguồn sự thật cuối). **Review Focus:** thả liên tiếp 2
  lần rất nhanh; app bị kill giữa lúc ghi; `moveItemUseCase` ném lỗi (phải hoàn lại `latestDbPages`, không để UI
  lệch DB — ghi log + thông báo nhẹ). **Kiểm chứng:** kéo liên tiếp nhanh, đè icon, cascade trang đầy; đối chiếu
  DB sau cùng (pull DB như Task 1) khớp UI.

- [x] **Step 7: Dọn log chẩn đoán** *(đã gỡ toàn bộ log `M7Drag` + ghi `docs/AGENTS.md` 2026-10-29; `m7drag.log` đã được người dùng xóa)* — gỡ mọi `Log.d("M7Drag"...)`/`dumpItems()`/`dumpAlpha()`/hằng `TAG` tạm
  trong `IconGridView`, `HomePagerAdapter` (kể cả đoạn log trong `Diff`), `HomeViewModel`, `LayoutReconciler`;
  báo người dùng tên file `m7drag.log` ở thư mục gốc dự án để người dùng quyết định xóa (không tự xóa). Ghi kinh
  nghiệm vào `docs/AGENTS.md`: "trạng thái sống qua cập nhật dữ liệu không được nằm trong view
  RecyclerView/ViewPager2; `DefaultItemAnimator` cấp holder mới cho mỗi lần đổi item không payload".

- [x] **Step 8: Kiểm chứng tay cuối (Live 4)** — kéo 1 icon sang ô trống trong cùng trang: icon tới đúng ô, DB cập nhật
  (pull DB như Task 1). Kéo icon A thả đúng lên ô icon B: **B và mọi icon sau B dồn đúng 1 bậc theo thứ tự đọc**
  (không còn "ô trống gần nhất"), A chiếm chỗ B. Kéo A về lại ô cũ (đã trống): A hiện đúng ô cũ, không biến mất.
  Kéo 1 icon vào 1 trang đã đầy hẳn (24/24): thả **vẫn nhận**, icon bị đẩy ra cascade đúng sang ô (0,0) trang kế (hoặc
  tạo trang mới) — đọc DB xác nhận cả trang nguồn và trang nhận đều đúng, không mất/trùng app. **[Đã kiểm ở Task 6 Step 7d (PASS 2026-10-29); ghi chú hoãn cũ: Task 6 Step 7d (kéo từ dock vào trang đầy là cách test được sớm nhất; Task 7 Step 3 chỉ kiểm lại bằng lật trang): ca cascade vào trang đầy chưa test tay được vì kéo trong cùng trang không bao giờ tràn; chỉ test được khi thả từ dock (Task 6) hoặc từ trang khác (Task 7). Logic `GridInsertPlanner` mới chỉ kiểm bằng bản port Python 6/6.]** Thả ra ngoài vùng lưới:
  icon về chỗ cũ tức khắc.

- [x] **Step 9: Commit**

---

### Task 6: Kéo-thả dock ↔ lưới chính [HOÀN THÀNH 2026-10-29]

> **Phát hiện khi đọc code 2026-10-29 (trước khi code, cần duyệt):** bản Task 6 gốc chỉ có "DockView implements
> DragDropTarget" là **thiếu**. Đọc `DockView`, `LayoutRepositoryImpl`, `LayoutReconciler`, `HomeViewModel` thấy 5 điểm
> phải xử lý, nếu không sẽ lặp lại đúng lỗi "icon biến mất/nháy" của Task 5:
> 1. `LayoutRepository.dock` là `Flow<List<AppItem>>` — **không có `itemId`**, nên dock không thể làm nguồn kéo
>    (`DragSession.itemId` cần id hàng DB). Phải đổi model.
> 2. `HomeViewModel.planOptimistic` trả `null` khi item không nằm trên lưới (kéo từ dock) và chỉ biết `pages` →
>    thả dock↔lưới sẽ chờ DB, tức nháy ô cũ 1 khung hình (lỗi Task 5). Dock phải có optimistic update riêng.
> 3. Collector `layoutRepository.dock` ghi thẳng vào `_uiState`, **không** qua cơ chế `pendingMoves/latestDbPages`
>    → emission DB cũ về muộn có thể ghi đè trạng thái lạc quan. Phải gộp dock vào cùng cơ chế.
> 4. `DockView` hiện chỉ là nơi hiển thị: chưa có edit mode, chưa có cử chỉ bắt đầu kéo (long-press + slop nằm
>    trong `IconGridView`), chưa có `hiddenItemId`. Không được copy-paste cử chỉ sang `DockView` (hai bản sẽ lệch
>    nhau) → tách dùng chung.
> 5. `LayoutReconciler.moveToDockInTransaction` có thuật toán chèn dock viết inline; ViewModel cần đúng thuật toán
>    đó cho optimistic → tách thành hàm thuần (cùng bài học `GridInsertPlanner`: 1 nguồn sự thật cho DB và UI).
>
> Lợi ích phụ: kéo icon từ dock vào **trang 2/3 đang đầy 24/24** chính là ca cascade chưa test được ở Task 5 —
> nên Task 6 Step 7 gánh luôn ca đó (không cần đợi Task 7).

**Decisions đã chốt (người dùng duyệt 2026-10-29 bằng "làm task 6 trước"):**
- D1. Dock cũng **rung (jiggle)** trong edit mode như iOS — dùng lại animator đã có ở `IconGridView`/`IconCellView`
  nếu tách được gọn; nếu không gọn thì bỏ rung dock, chỉ cần kéo được (ghi lại là việc còn nợ).
- D2. Thả vào dock khi đã đủ 4 icon và item **không** thuộc dock → từ chối ngay ở `DockView.onDrop` (trả `false`,
  không ghi gì, icon bay về chỗ cũ). Sắp xếp lại trong dock đầy vẫn cho phép (item đang kéo không tính vào 4).
- D3. Preview trong dock: coi dock như nhóm căn giữa gồm `n+1` ô, các icon sẵn có trượt ngang (`translationX`) sang vị
  trí của nhóm mới; ô đích = slot dock gần tay nhất. Không đổi chiều rộng nền kính (A10).
- D4. Kéo ra khỏi dock không dồn lại dock (giữ Q10); `rank` còn lỗ cũng được vì dock luôn đọc theo `rank` rồi căn
  giữa theo số lượng.

**Files:**
- Create: `domain/layout/DockInsertPlanner.kt` — hàm thuần, không `android.*`.
- Modify: `domain/model/HomePage.kt` (thêm `DockApp(itemId, app)`), `domain/repository/LayoutRepository.kt`
  (`dock: Flow<List<DockApp>>`), `data/layout/LayoutRepositoryImpl.kt` (map dock kèm `e.id`),
  `data/layout/LayoutReconciler.kt` (`moveToDockInTransaction` gọi `DockInsertPlanner`).
- Modify: `ui/home/HomeUiState.kt` (`dock: List<DockApp>`), `ui/home/HomeViewModel.kt`, `ui/home/HomeActivity.kt`.
- Create: `ui/home/view/IconDragGesture.kt` — cử chỉ long-press + slop → bắt đầu kéo, tách từ `IconGridView`.
- Modify: `ui/home/view/IconGridView.kt` (dùng lại `IconDragGesture`), `ui/home/view/DockView.kt`
  (implement `DragDropTarget` + làm nguồn kéo).

**Interfaces:**
- Produces: `DockInsertPlanner.plan(dockIds: List<Long>, movingId: Long, slot: Int, maxSlots: Int): List<Long>?`
  — trả danh sách id dock theo thứ tự mới (`rank` = chỉ số), hoặc `null` nếu từ chối (dock đầy và `movingId` không
  thuộc dock). `slot` được kẹp vào `0..size`.
- Consumes: `GridInsertPlanner.plan(...)` (Task 5) cho nhánh dock→lưới; `DragHelper`/`DragSession`/`DragDropTarget`
  (Task 3); `HomeViewModel.onDragStarted/onDragCancelled/onItemDropped` (Task 5).
- `HomeViewModel.onItemDropped(itemId, targetContainer, targetPageId, cell)` giữ nguyên chữ ký; bên trong phân 4 nhánh
  theo (nguồn, đích) = (lưới|dock) × (lưới|dock). Nguồn lấy từ `DragSession.sourceContainer` hoặc tra `itemId` trong
  `pages`/`dock` hiện tại.

> **Ruling G1 — luồng cử chỉ icon 3 trạng thái (2026-10-29, người dùng chỉnh lại sau khi test Task 6):**
> `Bình thường` --nhấn giữ icon--> `Shortcut` (icon được nhấn nổi lên, MỌI thứ còn lại mờ đi, hiện menu như ảnh mẫu)
> --(A) nhấn giữ lại đúng icon đó, HOẶC (B) cứ giữ tay và di vượt slop (bắt đầu kéo)--> `Edit mode` (rung, "Xong";
> với (B) icon được kéo luôn trong cùng lần chạm). Vào edit mode KHÔNG đi thẳng từ lần nhấn giữ đầu; (B) giống iOS thật
> và vẫn không nhạy với vuốt trang vì kéo chỉ bắt đầu SAU khi đã giữ đủ lâu để mở Shortcut.
> Trong edit mode, kéo icon cần: (1) edit mode đã bật từ lúc ngón tay chạm xuống, (2) giữ yên quá
> `ViewConfiguration.getLongPressTimeout()` (rung nhẹ báo "arm"), (3) rồi di vượt slop. Vuốt nhanh trên icon vẫn lật
> trang (quan trọng khi trang đầy icon). Phần (1)–(3) ĐÃ code ở `IconDragGesture` (Task 6). Phần 3 trạng thái
> `Bình thường → Shortcut → Edit` thuộc **Task 10** (cần menu + lớp làm mờ); **cho tới khi Task 10 xong, tạm giữ hành vi
> Task 4: nhấn giữ lần đầu vào edit mode luôn** — đây là hành vi TẠM, không phải thiết kế cuối. Nhấn giữ khi đã ở edit
> mode không gọi lại `onLongPressIcon`.
>
> **Đã code Step 1–6 và người dùng test tay Step 7 PASS (a–g) 2026-10-29; Step 8 (Oppo A3x) OK.** Phát sinh khi code (ngoài kế hoạch gốc, ghi lại để duyệt):
> - `DragDropTarget` thêm `containsScreenPoint(x, y)`; `DragHelper` có sổ đăng ký target (`registerTarget/unregisterTarget`, view tự đăng ký lúc attach/detach) và tự chọn target dưới ngón tay: `updatePosition(x, y)` / `dropAt(x, y)` (bỏ chữ ký cũ truyền `listOf(this)`). Cần vì giờ có 2 loại target (lưới, dock) và nguồn kéo không còn tự biết target nào đang ở dưới tay.
> - Tách `Jiggler` (rung dùng chung lưới + dock) cùng với `IconDragGesture` (Step 4).
> - `DockView.setItems` bỏ qua hoàn toàn nếu nội dung/kích thước không đổi (chỉ cập nhật alpha khi `hiddenItemId` đổi) để dock không nháy icon và không reset pha rung mỗi lần `render()`. Cử chỉ kéo của dock gắn 1 lần lúc tạo ô, không gắn lại mỗi `setItems`.
> - `HomeViewModel.onItemDropped` giờ đọc kết quả `moveItemUseCase` (false -> `publishFromDb()`); reconcile so cả pages lẫn dock.
> - **Chưa build được trên máy tác giả (không có Android SDK)** — người dùng build lần đầu có thể gặp lỗi biên dịch nhỏ, báo log là sửa.

- [x] **Step 1: Model + repo** — thêm `DockApp(itemId: Long, app: AppItem)`; `LayoutRepositoryImpl` build dock từ
  `(e.rank to DockApp(e.id, app))`; đổi kiểu `dock` ở `LayoutRepository`, `HomeUiState`, `HomeActivity.lastDockItems`,
  `DockView.items`. Chưa đổi hành vi. **Kiểm chứng:** app build + chạy, dock hiển thị y như cũ (chưa kéo).

- [x] **Step 2: `DockInsertPlanner` (hàm thuần)** — `plan(dockIds, movingId, slot, maxSlots)`: bỏ `movingId` khỏi
  danh sách (nếu có); còn `>= maxSlots` thì `null`; chèn tại `slot.coerceIn(0, size)`. `LayoutReconciler.moveToDock`
  gọi hàm này rồi ghi `container=DOCK, pageId=0, gridX=i, gridY=0, rank=i` theo danh sách trả về (bỏ logic inline).
  **Không viết unit test** (dự án chỉ kiểm tay). **Kiểm chứng tay ở Step 7.**

- [x] **Step 3: `HomeViewModel` optimistic cho dock** — (a) thêm `latestDbDock` và để collector `layoutRepository.dock`
  đi chung cơ chế `pendingMoves` với `pages` (đang pending thì chỉ lưu, không phát); `scheduleReconcile`/`publishPages`
  so và ép lại **cả pages lẫn dock**. (b) `onItemDropped` phân nhánh, mỗi nhánh tính ra `(pages', dock')` rồi
  `_uiState.update` **1 lần** cùng `draggingItemId = null`:
  - lưới→lưới: như Task 5.
  - lưới→dock: bỏ item khỏi `pages` (ô cũ để trống, KHÔNG dồn), chèn vào `dock` bằng `DockInsertPlanner`; `null` thì
    không đổi gì (UI vốn đã từ chối ở `DockView.onDrop`, đây là lớp chặn thứ hai).
  - dock→lưới: bỏ khỏi `dock`, chạy `GridInsertPlanner` trên `pages` với item đến từ ngoài lưới (planner đã coi
    "không có ở đâu" = chèn mới; `planOptimistic` cần nhận thêm `PlacedApp` dựng từ `DockApp` thay vì chỉ tra trong
    `pages`).
  - dock→dock: `DockInsertPlanner` trên `dock`.
  Lỗi ghi DB: trả cả `latestDbPages` và `latestDbDock`. **Review Focus:** thả dock→lưới ngay sau lưới→dock (hai
  `pendingMoves` chồng nhau); emission DB về giữa chừng; cascade tạo trang mới từ dock (pageId tạm âm, cùng cảnh báo Task 5).

- [x] **Step 4: `IconDragGesture` dùng chung** — tách khỏi `IconGridView` đoạn `OnTouchListener` (theo dõi slop khi
  `editMode=true` → `beginDragFor`) thành lớp/hàm nhận `(cell, itemId, sourceContainer, sourcePageId, source, callbacks)`.
  `IconGridView` đổi sang dùng nó, **hành vi lưới giữ nguyên** (kiểm lại kịch bản Task 5 Step 8 phần trong 1 trang).
  **Kiểm chứng:** kéo trong lưới y như trước, không hồi quy.

- [x] **Step 5: `DockView` thành nguồn kéo** — thêm `editMode` (HomeActivity gán từ `state.editMode`), `hiddenItemId`
  (nhận từ `state.draggingItemId`, đặt `alpha=0f` đúng theo dữ liệu mỗi lần bind — cùng quy tắc "trạng thái kéo là dữ
  liệu"), callback `onDragStarted/onDragCancelled` nối `HomeViewModel`. Nếu D1 gọn thì rung icon dock khi `editMode`.
  `DockView` **không** nằm trong RecyclerView nên không dính lỗi view bị thay, nhưng vẫn đi theo `hiddenItemId` cho thống nhất.

- [x] **Step 6: `DockView implements DragDropTarget`** — `onDragMove`: quy đổi toạ độ màn hình → slot (theo nhóm
  `n+1` ô căn giữa, D3) rồi preview bằng `translationX`; `onDragExit`/`onDrop` xoá preview. `onDrop`: nếu dock đã
  `>= DOCK_SLOTS` và `session.sourceContainer != DOCK` → trả `false` (D2); ngược lại gọi
  `onItemDropped(itemId, ContainerId.DOCK, null, GridPosition(x = slot, y = 0))` và trả `true`. Đăng ký target
  với `DragHelper` giống cách `IconGridView` đang làm; kiểm `IconGridView.onDrop`/`onDragMove` không giả định nguồn
  luôn là lưới (đặc biệt nhánh "thả đúng ô của chính mình = no-op").

- [x] **Step 7: Kiểm chứng tay — Vsmart Live 4** (đọc DB bằng cách pull như Task 1 để đối chiếu):
  a. Lưới → dock còn trống (3/4): vào đúng slot, icon cũ ở lưới để lại ô trống (không dồn), DB `container=-101`, `rank` liên tục.
  b. Dock → lưới (ô trống): dock còn 2 icon, **không tự lấp** (Q10), dock căn giữa lại; icon hiện đúng ô lưới.
  c. Dock → lưới, thả đè icon: các icon sau dồn +1 theo thứ tự đọc (thuật toán Task 5).
  d. **Cascade (ca hoãn từ Task 5 Step 8):** vào edit mode, vuốt sang trang 2 (đang 24/24), kéo icon từ dock lên thả vào
     giữa trang: 1 icon cuối bị đẩy sang ô (0,0) trang 3 (cũng đầy → tiếp tục đẩy sang trang 4/tạo trang mới nếu cần).
     Đối chiếu DB: không mất/trùng app, mọi trang ≤ 24.
  e. Dock → dock (đổi chỗ): thứ tự đổi đúng, icon không biến mất; kéo về đúng slot cũ = no-op.
  f. Lấp đủ 4 rồi kéo icon từ lưới vào: **không cho thả**, icon bay về chỗ cũ, DB không đổi. Đổi chỗ trong dock đầy vẫn được.
  g. Thả ra ngoài mọi vùng: icon về chỗ cũ tức khắc. Thả liên tiếp nhanh lưới↔dock 5–6 lần: không nháy ngược, UI khớp DB.

- [x] **Step 8: Kiểm chứng tay — Oppo A3x (kính dock)** *(người dùng xác nhận OK 2026-10-29)* — lặp Step 7a/b/e, quan sát bằng mắt: icon đang kéo
  (`DragLayer`) luôn hiện rõ phía trên dock, không bị `RealGlassSurface` làm mờ/biến mất khi đi qua vùng kính.

- [x] **Step 9: Ghi `docs/AGENTS.md` + Commit** — bài học mới (nếu có: dock phải có id; mọi nguồn trạng thái UI đi chung
  một cơ chế `pendingMoves`); đánh dấu ca cascade ở Task 5 Step 8 là đã kiểm ở đây.

---

### Task 7: Tự lật trang khi kéo ra mép + trang mới tạm ("ghost page") [HOÀN THÀNH 2026-10-29]

> **Phát hiện khi đọc code 2026-10-29 (trước khi code, cần duyệt):** bản Task 7 gốc (chỉ `DragHelper` đo mép + `setCurrentItem`)
> sẽ lỗi vì 3 lý do có thật trong code hiện tại:
> 1. **View nguồn bị tháo khỏi cây khi lật trang.** Cử chỉ kéo hiện chạy bằng `OnTouchListener` của chính ô nguồn
>    (`IconDragGesture`). Lật sang trang kế thì RecyclerView của ViewPager2 cache/tháo `IconGridView` của trang nguồn
>    (chắc chắn khi lật ≥ 2 trang) -> ô nguồn nhận `ACTION_CANCEL` -> `dropAt` thả sai chỗ, kéo chết giữa chừng. Phải
>    chuyển việc nhận MOVE/UP của phiên kéo lên **`HomeActivity.dispatchTouchEvent`** (nhận mọi chạm trước mọi view,
>    không phụ thuộc view nào còn sống). Ghi chú: Task 3 từng thất bại khi gắn listener lên `home_root` vì con tiêu thụ
>    chạm trước; `Activity.dispatchTouchEvent` thì đứng TRƯỚC cả cây view nên không dính lỗi đó.
> 2. **Target dưới ngón tay không tự cập nhật sau khi lật.** `DragHelper.updatePosition` chỉ chạy khi có `ACTION_MOVE`;
>    giữ tay đứng yên ở mép rồi trang lật xong thì không có MOVE nào -> trang mới không nhận `onDragEnter`, không preview.
>    `DragHelper` phải nhớ điểm cuối và có `refresh()` gọi sau mỗi lần lật trang xong.
> 3. **Kéo qua mép phải ở trang cuối cần có trang để rơi vào**, nhưng người dùng chưa chắc thả xuống đó (yêu cầu của
>    người dùng 2026-10-29: tránh ghi DB ngay lúc preview, nếu người dùng quay lại trang cũ thì KHÔNG được thừa 1 trang trống).

**Decisions (đã chốt với người dùng 2026-10-29):**
- E1. Giữ ngón tay trong ~24dp sát mép trái/phải > 500ms thì lật 1 trang; vẫn giữ ở mép thì lặp lại mỗi 500ms (kéo liên tục qua nhiều trang). Rời mép thì hủy hẹn giờ.
- E2. **Trang mới tạm (ghost page) chỉ tồn tại trong UI/`HomeUiState`, KHÔNG ghi DB lúc preview.** Giữ mép phải ở trang thật cuối cùng -> hiện thêm 1 trang trống tạm ở cuối (tối đa 1) rồi lật sang. Chỉ khi **thả icon LÊN trang tạm đó** mới tạo hàng `home_pages` (cùng transaction với việc đặt item). Ghost biến mất ngay khi: (a) người dùng kéo ngược về 1 trang thật (kể cả chưa thả), (b) thả ở nơi khác (dock, ngoài vùng, trang thật), (c) hủy/CANCEL. Không có ghost bên trái (trang đầu không có gì ở trước).
- E3. Thả lên ghost: item đặt đúng ô đang hover (khe trống cho phép như các trang khác).

**Files:**
- Modify: `core/ui/DragHelper.kt` — `DragSession.onFinished`, nhớ điểm cuối + `refresh()`, theo dõi mép (`EdgeDirection`, `edgeListener`).
- Modify: `ui/home/HomeActivity.kt` — `dispatchTouchEvent` chuyển chạm cho phiên kéo; nhận `onEdgeHover` -> lật trang; vẽ ghost; dọn ghost.
- Modify: `ui/home/view/IconDragGesture.kt`, `IconGridView.kt`, `DockView.kt` — nguồn kéo chỉ còn "arm + begin", không tự nhận MOVE/UP nữa; truyền `onFinished` vào `DragSession`.
- Modify: `ui/home/HomeUiState.kt` (`ghostPage: Boolean`), `ui/home/HomeViewModel.kt` (tạo/xóa ghost, thả lên ghost), `ui/home/HomePagerAdapter.kt` (nhận danh sách trang đã gồm ghost).
- Create: `domain/model/PageId.kt` — `object PageId { const val NEW = Long.MIN_VALUE }` (sentinel "trang mới ở cuối" cho `moveItem`; cũng là `pageId` của ghost trong UI).
- Modify: `data/layout/LayoutReconciler.kt` — `moveToHomeInTransaction`: nếu `targetPageId == PageId.NEW` thì chèn `HomePageEntity(rank = last+1)` TRƯỚC rồi dùng id đó làm trang đích (cùng transaction, cùng `mutex`).

**Interfaces:**
- Produces: `enum class EdgeDirection { LEFT, RIGHT }`; `DragHelper.edgeListener: ((EdgeDirection?) -> Unit)?`; `DragHelper.refresh()`; `DragSession.onFinished: (accepted: Boolean) -> Unit`; `PageId.NEW`.
- Consumes: `DragHelper.registerTarget/containsScreenPoint/updatePosition/dropAt` (Task 6), `GridInsertPlanner` (trang trống = chỉ đặt vào ô hover), `HomeViewModel.onDragStarted/onDragCancelled/onItemDropped` (Task 5/6).

> **Đã code Step 1–4 (2026-10-29), người dùng test tay Step 5 OK, đã tick.** Chi tiết cài đặt khác/ngoài plan để duyệt:
> - Trang tạm dùng `pageId = PageId.NEW` cả trong UI lẫn lúc thả lạc quan (id ổn định nên RecyclerView không thay view ngay khi thả); DB trả id thật thì đổi 1 lần.
> - Ghost chỉ được tạo khi trang thật cuối còn icon NGOÀI icon đang kéo (không chồng thêm trang trống lên một trang vốn đã trống).
> - `HomeActivity.dispatchTouchEvent` gửi 1 `ACTION_CANCEL` giả xuống cây view ở lần tiếp quản đầu; `ACTION_DOWN` mới khi còn phiên kéo sót (Activity bị tạm dừng giữa chừng) sẽ huỷ phiên cũ.
> - `IconDragGesture` không còn tham số `finished`; kết thúc phiên qua `DragSession.onFinished` do Activity gọi.
> - Chưa build trên máy tác giả (không có Android SDK) — báo log nếu lỗi biên dịch.

- [x] **Step 1: Phiên kéo do Activity nhận chạm** — `DragSession` thêm `onFinished`; `IconDragGesture` sau khi `begin` thì thôi tự xử lý MOVE/UP (chỉ giữ phần arm + begin, hủy hẹn giờ arm); `HomeActivity.dispatchTouchEvent`: khi `DragHelper.currentSession != null` thì MOVE -> `updatePosition`, UP/CANCEL -> `dropAt` rồi `session.onFinished(accepted)`, trả `true` (lần đầu tiếp quản gửi 1 `ACTION_CANCEL` giả cho `super` để các view dọn trạng thái pressed/long-press). Không đổi hành vi kéo trong 1 trang. **Kiểm chứng:** lặp lại kịch bản Task 5/6 (trong trang, lưới↔dock) — y như cũ, kể cả thả liên tiếp nhanh.

- [x] **Step 2: `DragHelper` theo dõi mép + `refresh()`** — nhớ `(lastX, lastY)` mỗi `updatePosition`; `refresh()` gọi lại logic hover tại điểm đã nhớ. Theo dõi mép: `x` trong ~24dp từ mép màn hình liên tục > 500ms (`Handler`, hủy khi rời mép/hết phiên) -> `edgeListener?.invoke(LEFT|RIGHT)`; sau mỗi lần phát lại hẹn 500ms tiếp nếu vẫn ở mép (E1); rời mép -> `edgeListener?.invoke(null)`. `detachDragVisual()` phải hủy hẹn giờ (không rò Handler).

- [x] **Step 3: `HomeActivity` lật trang** — nhận `edgeListener`: `home_pager.setCurrentItem(current ± 1, true)` nếu còn trang (kể cả ghost); không lật ở trang đầu bên trái. Gọi `DragHelper.refresh()` khi pager về `SCROLL_STATE_IDLE` sau lật (Phát hiện #2). Đang kéo thì chặn người dùng tự vuốt pager (đã `requestDisallowInterceptTouchEvent` từ Task 5; xác nhận vẫn đúng khi Activity nhận chạm).

- [x] **Step 4: Ghost page** — `HomeViewModel.onDragEdgeBeyondLastPage()` đặt `ghostPage = true` (chỉ khi đang kéo, chưa có ghost, hover mép phải ở trang cuối thật); `HomeActivity.render` gửi `state.pages + HomePage(index = n, pageId = PageId.NEW, items = emptyList())` cho adapter khi `ghostPage`, và cập nhật `pageIndicator` số trang. Dọn ghost: khi `onPageSelected` là trang thật trong lúc `ghostPage` (quay ngược), khi `onItemDropped` đích khác ghost, `onDragCancelled`, hết phiên. Khi xóa ghost mà pager đang đứng ở ghost -> `setCurrentItem(n - 1, false)` sau khi danh sách áp xong. `onItemDropped(targetPageId = PageId.NEW)`: nhánh lạc quan dựng trang thật tạm bằng pageId âm như cascade (Task 5) rồi `moveItem(PageId.NEW)`; reconciler tạo trang thật. **Review Focus:** thả nhanh ngay lúc vừa lật xong; DB trả trang mới nhưng UI còn ghost (không được hiện 2 trang trống); đổi cấu hình khi đang có ghost (ghost là state phiên kéo, phiên kéo hủy khi Activity hủy -> ghost phải về `false`).

- [x] **Step 5: Kiểm chứng tay (Live 4)** (đọc DB bằng pull như Task 1):
  a. Kéo icon sát mép phải giữ > 0.5s: lật sang trang kế, icon vẫn bám tay; giữ tiếp: lật tiếp (liên tục). Thả ở trang mới: ghi đúng trang đó.
  b. Dồn dây chuyền xuyên trang: kéo từ trang 1 thả đè 1 icon ở trang 2 (đang 24/24): các icon sau dồn +1, **cascade** đúng: 1 icon tràn sang ô (0,0) trang 3 (cũng đầy -> tràn tiếp), DB không mất/trùng app, trang nguồn còn 23.
  c. Kéo xuyên ≥ 2 trang (nguồn bị cache/tháo): icon vẫn bám tay, thả đúng trang đích, không thả sớm.
  d. **Ghost:** ở trang cuối thật, giữ mép phải: hiện thêm 1 trang trống, lật sang. **Kéo ngược về trang thật rồi thả:** trang trống biến mất, DB KHÔNG có hàng `home_pages` mới. Thả LÊN ghost: DB có trang mới chứa icon. Thả ra dock / ngoài vùng từ ghost: ghost biến mất, DB không đổi.
  e. Kéo từ dock sang ghost và từ ghost... (dock -> ghost) cũng đúng.
  f. Kéo sát mép trái ở trang đầu: không lật, không lỗi. Vuốt trang thường (không kéo icon) vẫn bình thường.

- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit** — bài học: phiên kéo phải do Activity nhận chạm; ghost chỉ ở UI; `refresh()` sau lật.

---

### Task 8: Dấu "−" xóa nhanh [HOÀN THÀNH 2026-10-29]

> **Phát hiện khi đọc code 2026-10-29 (trước khi code, cần duyệt):**
> 1. **Ruling G2 — ẩn icon thì DỒN (người dùng chốt 2026-10-29):** Task 1 đã cài `setHidden` KHÔNG dồn (chỉ để lại ô
>    trống) — sai ý chủ dự án. Đổi: ẩn 1 icon thì mọi icon SAU nó trong cùng trang (thứ tự đọc `y` rồi `x`) dịch về trước
>    đúng 1 bậc, đối xứng với chèn ở `GridInsertPlanner` (ẩn rồi chèn lại đúng ô cũ -> về nguyên trạng); icon đầu trang kế
>    KHÔNG kéo sang; trang trống KHÔNG tự xóa. Kéo icon ra khỏi chỗ cũ vẫn để lại ô trống (thao tác khác, giữ như Task 5).
>    Logic nằm ở hàm thuần mới `GridRemovePlanner` (domain), dùng chung cho DB (`LayoutReconciler.setHidden`) và cập nhật
>    lạc quan (`HomeViewModel`) — cùng nguyên tắc Task 5/6. Vì sửa ở `setHidden` nên menu "Ẩn ứng dụng" (Task 10) tự đúng theo.
> 2. **Dock cũng có dấu "−"** (đề xuất, vì dock dùng cùng `IconCellView` và iOS cũng hiện dấu trên icon dock): dock luôn
>    đọc theo `rank` rồi căn giữa nhóm nên ẩn 1 icon dock tự thấy "dồn" (các icon còn lại căn giữa lại), không cần planner
>    riêng. `setHidden` đã gỡ placement bất kể container.
> 3. **Dấu "−" nằm trong `IconCellView` nhưng ảnh chụp lúc kéo (`IconDragGesture.snapshot`) KHÔNG được chứa nó** — iOS không
>    hiện dấu trên icon đang bay. Snapshot phải tắt dấu tạm thời khi chụp.
> 4. **Vùng chạm:** hình dấu 24dp nhưng vùng chạm tối thiểu 48dp (chuẩn). Vùng chạm 48dp tâm tại dấu, **cắt theo biên ô**
>    (chạm ngoài view không tới được). Chạm vào dấu chỉ tính là "bấm" khi DOWN và UP cùng nằm trong vùng và không di quá slop.
>    Kéo icon bắt đầu từ vùng dấu vẫn hoạt động bình thường (Ruling G1: giữ rồi di); lúc đó không bao giờ có UP tới ô nên không ẩn nhầm.
> 5. **Cần cập nhật lạc quan** như Task 5/6: nếu chờ DB phát lại thì icon còn nguyên 1 nhịp sau khi bấm. Dùng chung cơ chế
>    `pendingMoves`/`latestDb*`; tách 1 hàm `launchLayoutWrite {}` dùng cho cả `onItemDropped` lẫn `onQuickHideTapped` (không viết hai bản try/catch/finally).
> 6. **Rủi ro khi test: chưa có đường khôi phục app đã ẩn trên UI cho tới Task 9** (màn "Thư viện ứng dụng", mục "Đã ẩn").
>    Test Task 8 chỉ nên ẩn 1–2 app ít quan trọng; muốn trả lại trước Task 9 phải xóa hàng trong `hidden_apps` bằng tay
>    (hoặc xóa dữ liệu app = mất cả bố cục).

**Files:**
- Create: `domain/layout/GridRemovePlanner.kt` — hàm thuần, không `android.*` (Ruling G2).
- Modify: `data/layout/LayoutReconciler.kt` — `setHidden(hidden = true)` gọi `GridRemovePlanner` sau khi xóa placement (cùng transaction, cùng `mutex`).
- Modify: `ui/home/view/IconCellView.kt` — vẽ dấu "−" góc trên-trái icon khi bật; bắt chạm riêng vùng dấu; `snapshot` không có dấu.
- Modify: `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt`, `ui/home/view/IconDragGesture.kt` (chỉ phần `snapshot`) — bật/tắt dấu theo `editMode`, nối callback.
- Modify: `ui/home/HomePagerAdapter.kt`, `ui/home/HomeActivity.kt` — truyền `onQuickHide` xuống lưới + dock.
- Modify: `ui/home/HomeViewModel.kt` — `onQuickHideTapped(itemId)`, inject `HideAppUseCase`, tách `launchLayoutWrite`.
- Modify: `res/values/dimens.xml` (`icon_badge_size`, `icon_badge_hit_size`), `res/values/colors.xml` (`icon_badge_fill`, `icon_badge_glyph`).

**Interfaces:**
- Consumes: `HideAppUseCase(key: ComponentKey, userSerial: Long)` (Task 1; `userSerial` lấy từ `key.userSerial`).
- Produces: `GridRemovePlanner.plan(slots: List<GridInsertPlanner.Slot>, removedItemId: Long, columns: Int): List<GridRemovePlanner.Move(itemId, x, y)>` — chỉ item thực sự đổi chỗ (rỗng nếu item bị ẩn không có ở trang / đứng cuối); `IconCellView.setBadgeVisible(Boolean)`, `IconCellView.onBadgeClick: (() -> Unit)?`; `HomeViewModel.onQuickHideTapped(itemId: Long)` — tra `ComponentKey` từ `pages`/`dock` theo `itemId`.

> **Đã code + test tay PASS + commit (2026-10-29).** Người dùng chốt: dock CÓ dấu "−"; test bằng cách ẩn 1–2 app ít quan trọng (chưa khôi phục được tới Task 9). Chi tiết khác/ngoài plan:
> - `GridRemovePlanner.plan` chỉ nhận `columns` (không cần `rows`).
> - `IconCellView.snapshotWithoutBadge()` thay cho việc `IconDragGesture.snapshot` tự `draw`; vùng chạm dấu loại trừ trường hợp giữ quá thời gian long-press rồi nhả (đang "arm" kéo) để không ẩn nhầm.
> - Hàm chung `HomeViewModel.launchLayoutWrite(optimistic, write, failureLog)`; `onItemDropped` đã chuyển sang dùng nó (hành vi cũ giữ nguyên) — cần kiểm lại kéo-thả nhanh liên tiếp ở Step 5 (g).
> - Chưa có TalkBack cho dấu "−" (không có node ảo) — ghi nợ cho M8+.
> - Sửa sau test: icon "chớp" khi ẩn do `bindAllCells` bind `icon = null` rồi nạp lại bất đồng bộ -> thêm cache bitmap theo key ở `IconGridView`/`DockView`. Đồng thời `placeNewInTransaction` giờ bỏ qua app trong `hidden_apps` (trước đó app ẩn sẽ bị đặt lại vào ô trống đầu tiên ở lần quét sau).

- [x] **Step 1: Vẽ dấu** — `IconCellView.onDraw`: sau khi vẽ icon, nếu `badgeVisible` thì `drawCircle` (màu `icon_badge_fill`) + gạch ngang trắng (`icon_badge_glyph`). Tâm dấu: `x = max(r, iconLeft + 0.25 * badgeSize)`, `y = r` (r = bán kính) để cả hình tròn luôn nằm trong biên ô, không bị `clipChildren` của `IconGridView` cắt. Kích thước 24dp (`icon_badge_size`). `IconGridView.setEditMode` + `setItems` và `DockView.setEditMode` + `setItems` gọi `setBadgeVisible(editMode)` cho mọi ô (ô mới tạo cũng đúng). `IconDragGesture.snapshot` bật cờ `suppressBadge` khi `draw` rồi trả lại. **Kiểm chứng:** vào edit mode: mọi icon (lưới + dock) có dấu; thoát: mất; icon bay theo tay không có dấu.

- [x] **Step 2: Bắt chạm** — `IconCellView.onTouchEvent`: DOWN ghi `downInBadge` (điểm nằm trong `RectF` vùng chạm 48dp tâm tại dấu, cắt theo biên ô, và `badgeVisible`); UP gọi `onBadgeClick` nếu `downInBadge` và UP vẫn trong vùng, di chuyển < `scaledTouchSlop`; CANCEL/MOVE xa thì hủy. Gọi `super.onTouchEvent` như cũ (không phá click/long-click). `IconGridView`/`DockView` đặt `cell.onBadgeClick = { onQuickHide?.invoke(itemId) }` khi wire; `HomePagerAdapter`/`HomeActivity` nối `onQuickHide = viewModel::onQuickHideTapped` cho lưới và dock.

- [x] **Step 3: `GridRemovePlanner` + sửa `LayoutReconciler.setHidden`** — `plan(page, removedItemId, columns, rows)`: lấy các item của trang trừ `removedItemId`; item có chỉ số đọc `y*columns+x` LỚN HƠN chỉ số của item bị ẩn dịch về `chỉ số - 1` (ô trước nó theo thứ tự đọc); item trước giữ nguyên; trả danh sách item đổi chỗ. `setHidden(hidden=true)`: sau `deleteByIds` tính plan trên trang chứa placement vừa xóa (chỉ khi `container = HOME`; dock không cần) rồi `itemDao.updateAll` theo plan, trong CÙNG transaction. Không đụng `removeItem` (giữ hành vi không dồn của nó, hiện chưa ai gọi) và không đụng `compactHome`/`deleteAndCompact` (đường gỡ app thật, Q8). Cập nhật chú thích `removeItem/setHidden` cho khớp. **Không viết unit test** (manual-only). **Kiểm chứng:** ở Step 5.

- [x] **Step 4: `HomeViewModel.onQuickHideTapped`** — tra `PlacedApp`/`DockApp` theo `itemId` -> `ComponentKey`; phát NGAY `pages`/`dock` đã bỏ item và (với lưới) đã dồn bằng ĐÚNG `GridRemovePlanner` (item vẫn sắp theo `(y, x)` như `observeAll`) trong 1 lần `_uiState.update`, rồi `launchLayoutWrite { hideAppUseCase(key, key.userSerial) }`. `launchLayoutWrite(block)` = `pendingMoves++` / try-catch-finally / `scheduleReconcile()` / `publishFromDb()` khi lỗi — dùng chung với `onItemDropped` (refactor, hành vi cũ giữ nguyên). Bấm nhanh liên tiếp nhiều dấu: mỗi lần cộng `pendingMoves`, phát lạc quan từ trạng thái hiện tại nên không ghi đè nhau. Bấm dấu khi đang kéo: không xảy ra (Activity đang nhận chạm), nhưng bỏ qua nếu `draggingItemId != null` cho chắc.

- [x] **Step 5: Kiểm chứng tay (Live 4)** (đọc DB bằng pull như Task 1; ẩn 1–2 app ít quan trọng, xem cảnh báo #6):
  a. Vào edit mode: mọi icon lưới + dock có dấu "−" ở góc trên-trái; thoát edit mode: dấu mất.
  b. Bấm dấu 1 icon trong lưới: icon biến mất NGAY (không nháy), **các icon SAU nó cùng trang dồn lên 1 bậc theo thứ tự đọc**, icon đầu trang kế KHÔNG bị kéo sang, không cần thoát edit mode. DB: `hidden_apps` có hàng mới, `launcher_items` mất placement, tọa độ các icon dồn khớp UI. Ẩn icon cuối trang: không icon nào đổi. Trang có khe trống (do kéo-thả trước đó): khe giữ nguyên, chỉ icon sau vị trí ẩn dịch -1.
  c. Bấm vào thân icon (không phải dấu) trong edit mode: không có gì xảy ra (không mở app, không ẩn).
  d. Bấm dấu icon trong dock: dock căn giữa lại số icon còn lại, không tự lấp icon từ lưới lên.
  e. Bấm nhanh liên tiếp 3 dấu khác nhau: cả 3 biến mất, UI khớp DB sau đó, không có icon "sống lại" 1 nhịp.
  f. Nhấn giữ rồi kéo từ vùng dấu: vẫn kéo bình thường, không ẩn nhầm; icon bay không có dấu.
  h. Hồi quy kéo-thả (vì đã refactor `launchLayoutWrite`): kéo trong trang, lưới↔dock, thả liên tiếp nhanh 5–6 lần: vẫn ổn, không nháy ngược.
  g. Thoát edit mode rồi vào lại: app đã ẩn vẫn không xuất hiện; icon còn lại có dấu như cũ.

- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit**

---

### Task 9: Màn "Thư viện ứng dụng" (tối giản M7, chỗ mở rộng cho TT#11/G2) [HOÀN THÀNH 2026-10-29]

> Đổi tên + đổi thiết kế so với bản kế hoạch gốc ("màn App đã ẩn" bản list đơn) theo quyết định cuối sau khi
> khảo sát app mẫu: màn này có **2 mục** — "Tất cả" (mọi app, bấm vào thì **mở app**) và "Đã ẩn" (bấm vào thì
> **bỏ ẩn**) — và **không có** nút ẩn/xóa trực tiếp trên màn này (ẩn chỉ làm được từ dấu "−" ở Home, Task 8).
> Dùng 1 shape `AppLibrarySection(title, apps)` dùng chung cho cả 2 mục để TT#11/G2 (phân loại tự động) nối
> thêm mục mới mà không phải viết lại layout/adapter.

> **Đã code Step 1–4 + test tay PASS (2026-10-29); Step 6 commit xong.** Chi tiết khác/ngoài plan:
> - `AppLibrarySection` có thêm `id` (`ID_ALL`/`ID_HIDDEN`) để UI biết hành vi khi bấm mà không so sánh tiêu đề đã dịch; `AppLibraryViewModel` lấy tiêu đề từ string resource (`@ApplicationContext`).
> - **Đổi theo yêu cầu (2026-10-29): dạng 2 TAB** thay vì list có header — tab "Chưa ẩn" (app chưa ẩn, bấm = mở app) và tab "Đã ẩn" (bấm = bỏ ẩn), loại trừ nhau; tiêu đề tab kèm số app. Tab đang chọn lưu ở ViewModel. (Khác plan gốc: mục "Tất cả" không còn chứa app đã ẩn.) Step 5 bên dưới đọc theo nghĩa này.
> - Adapter chỉ hiển thị 1 mục; hàng `Empty` ("Chưa ẩn app nào" / "Không còn app nào chưa ẩn"). `item_app_library_section_header.xml` đã xóa (không còn tham chiếu, người dùng đồng ý).
> - Nút vào: `HomeActivity` gom "Thư viện" + "Xong" vào `edit_toolbar` (LinearLayout ngang, cùng vị trí cũ của "Xong"); inset/visibility áp lên `edit_toolbar`.
> - Giao diện Material 3 thường (chưa liquid glass); `RecyclerView.itemAnimator = null`.
> - Chưa build trên máy tác giả — báo log nếu lỗi biên dịch.

**Files:**
- Create: `app/src/main/java/gambi/global/os26launcher/domain/model/AppLibrarySection.kt` — `data class AppLibrarySection(val title: String, val apps: List<AppItem>)`
- Create: `app/src/main/java/gambi/global/os26launcher/ui/library/AppLibraryActivity.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/ui/library/AppLibraryViewModel.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/ui/library/AppLibraryAdapter.kt` — adapter đa `section` (header + list app mỗi section), KHÔNG có action ẩn/xóa trên item
- Create: `app/src/main/res/layout/activity_app_library.xml`
- Create: `app/src/main/res/layout/item_app_library_section_header.xml`
- Create: `app/src/main/res/layout/item_app_library_app.xml`
- Modify: `app/src/main/AndroidManifest.xml` — khai `AppLibraryActivity` (`exported=false`)
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt` — 1 lối vào màn này trong edit mode (vd nút nhỏ cạnh "Xong")

**Interfaces:**
- Consumes: `AppRepository.apps` (mục "Tất cả"), `GetHiddenAppsUseCase` (mục "Đã ẩn"), `UnhideAppUseCase` (Task 1)

- [x] **Step 1: `AppLibraryViewModel`** — `uiState: StateFlow<List<AppLibrarySection>>` ghép `AppRepository.apps` (section "Tất cả", sắp theo nhãn) và `GetHiddenAppsUseCase()` (section "Đã ẩn"); `fun onAppTapped(app: AppItem, inHiddenSection: Boolean)` — nếu `inHiddenSection` gọi `UnhideAppUseCase`, ngược lại `AppRepository.launch(app.key, source = null)` (mở app như Home).

- [x] **Step 2: `AppLibraryAdapter`** — `RecyclerView.Adapter` 2 kiểu item (header section, app row); app row chỉ có icon + tên, không có nút phụ (không ẩn/xóa trực tiếp ở màn này).

- [x] **Step 3: `AppLibraryActivity`** — layout list + trạng thái rỗng cho mục "Đã ẩn" khi không có app nào ("Chưa ẩn app nào") theo đúng bảng trạng thái màn hình đã định nghĩa cho các màn khác (Default/Empty) ở `project_structure.md` mục 7. Mục "Tất cả" luôn có nội dung (máy luôn có ít nhất launcher chính nó cộng app hệ thống).

- [x] **Step 4: Lối vào từ `HomeActivity`** — 1 nút nhỏ hiện cùng lúc với "Xong" trong edit mode, mở `AppLibraryActivity` bằng `startActivity`.

- [x] **Step 5: Kiểm chứng tay (Live 4)** — ẩn 2 app bằng dấu "−" (Task 8), mở "Thư viện ứng dụng": mục "Tất cả" vẫn thấy đủ mọi app (kể cả 2 app vừa ẩn), mục "Đã ẩn" thấy đúng 2 app đó. Bấm vào 1 app ở mục "Tất cả" (chưa ẩn): app mở lên như bấm ở Home. Bấm vào 1 app ở mục "Đã ẩn": app đó biến khỏi mục "Đã ẩn", quay lại Home thấy app xuất hiện ở ô trống đầu tiên (theo đúng `placeNewApps`/cascade đã test ở Task 1).

- [x] **Step 6: Commit**

---

### Task 10: Menu nhấn giữ icon (TT#7) [HOÀN THÀNH 2026-10-29]

> **Chia mốc (người dùng duyệt 2026-10-29):** 10a data (Step 1, 2, 5) -> 10b trạng thái `iconMenu` + scrim + icon nổi (Step 4 phần đầu) -> 10c nội dung menu + hành động (Step 3 + 4) -> 10d đường B kéo ra khỏi menu + Step 6 test tay.
>
> **10a đã code (2026-10-29), chờ người dùng xác nhận — chưa tick Step 1/2/5.** Quyết định khác plan gốc:
> - Truy vấn shortcut: động + **manifest** (`FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST`), bỏ "chỉ-ghim" (plan gốc ghi sai tên cờ và thiếu manifest — Camera/Chat dùng shortcut manifest). Tối đa 4, theo `rank`, chỉ shortcut `isEnabled`.
> - `getShortcuts(key, iconSizePx)` có thêm tham số kích thước icon; chưa phải launcher mặc định -> `SecurityException` -> trả rỗng.
> - Gỡ cài đặt và Thông tin ứng dụng đặt ở `AppRepository` (`requestUninstall`, `openAppInfo`; thực thi ở `LauncherAppsDataSource`), KHÔNG tạo `UninstallAppUseCase` trong domain (cần `Intent`/`Context`, domain không được `android.*`). "Thông tin ứng dụng" dùng `LauncherApps.startAppDetailsActivity` (đúng user profile) thay vì `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
> - Mốc này chưa có UI nên chưa test tay được; chỉ cần build qua. Shortcut chỉ đọc được khi launcher là mặc định.
>
> **10a build PASS (2026-10-29). 10b đã code, chờ test tay:** `HomeUiState.iconMenu: IconMenuState?` (itemId, container, key, anchor); `HomeViewModel.onIconLongPressed/closeIconMenu`; `IconGridView`/`DockView` báo `IconPress` (kèm bitmap ô icon, không dấu "−") thay vì gọi thẳng `enterEditMode`; `HomeActivity.renderIconMenu`: scrim (`icon_menu_scrim`, chặn chạm, chạm = đóng) + icon nổi vẽ bằng `DragLayer.startDrag` + `IconActionMenu` (View thường, không PopupWindow — cần cùng toạ độ/z-order với scrim) đặt dưới icon, lật lên trên nếu thiếu chỗ; Back đóng menu. Mốc này các mục menu mới chỉ ĐÓNG menu; bitmap nổi lưu ở Activity (không trong state) nên Activity bị tạo lại thì menu tự đóng.
> **10c đã code (chờ test tay):** `IconMenuState.shortcuts` (null = đang nạp: scrim + icon nổi hiện ngay, menu chờ nạp xong mới hiện để khỏi nhảy vị trí; rỗng = app không có shortcut); `HomeViewModel.onIconLongPressed` nạp shortcut bất đồng bộ (`GetAppShortcutsUseCase`); `IconMenuAction` (AppInfo/HideApp/EditHome/Uninstall) + `onIconMenuAction` (đóng menu trước, rồi: mở thông tin ứng dụng / `onQuickHideTapped` / `enterEditMode` / gỡ cài đặt); `onIconMenuShortcut` -> `LaunchShortcutUseCase`; thất bại -> `HomeEvent.MenuActionFailed` (Toast). Shortcut chỉ có khi launcher là mặc định. Menu: shortcut (icon 24dp) -> đường kẻ -> 4 mục cố định.
> **Lỗi 10c (người dùng báo): "Gỡ cài đặt" không phản ứng.** Luồng: menu -> `onIconMenuAction(Uninstall)` -> `AppRepository.requestUninstall` -> `startActivity(ACTION_DELETE)` trả thành công nhưng không có hộp thoại. Điểm gãy duy nhất tìm được: manifest thiếu `REQUEST_DELETE_PACKAGES` (bắt buộc từ target SDK 28; thiếu thì hệ thống lặng lẽ bỏ qua, không exception — [CommonsWare](https://commonsware.com/blog/2018/03/09/request-delete-packages-for-realz.html) mô tả cho `ACTION_UNINSTALL_PACKAGE`, riêng `ACTION_DELETE` chưa tìm được tài liệu xác nhận trực tiếp nên chờ test). Đã thêm quyền vào manifest. Nếu vẫn không hiện: lọc logcat tag `UninstallerActivity`/`PackageInstaller` để lấy log lỗi phía system.
> **10d đã code (chờ test tay):** `IconDragGesture` không còn đòi edit mode ở lúc DOWN (`canDrag` -> `isEditMode`, chỉ còn quyết định rung khi arm): lúc chưa edit mode, lần giữ đủ `longPressTimeout` vừa mở menu vừa arm; di tay vượt slop khi chưa nhấc tay -> `begin` -> phiên kéo bình thường. `HomeViewModel.onDragStarted` đặt `iconMenu = null` + `editMode = true` trong cùng 1 lần phát (đã ở edit mode thì không đổi). `HomeActivity.renderIconMenu(null)` không `endDrag()` khi `DragHelper.currentSession != null` (DragLayer đang vẽ icon của phiên kéo). Vuốt nhanh (chưa giữ đủ lâu) vẫn lật trang.
> **Bổ sung 10d (người dùng yêu cầu 2026-10-29):** menu đang mở, nhấc tay rồi NHẤN GIỮ LẠI đúng vào icon đang nổi (trong `anchor`) -> đóng menu + vào edit mode (cùng đường với mục "Sửa màn hình chính"). Nhấn giữ chỗ khác trên nền mờ không làm gì (nhấc tay = chạm nền = đóng menu). Cài ở `HomeActivity` trên scrim (OnTouchListener ghi toạ độ DOWN + OnLongClickListener).
> **Mở rộng (người dùng yêu cầu):** nhấn giữ icon nổi vào edit mode xong, nếu ngón tay CHƯA nhấc mà di vượt slop thì kéo luôn icon đó. `HomeActivity` giữ `pendingMenuDrag` (itemId, container, toạ độ DOWN); `dispatchTouchEvent` thấy MOVE vượt slop thì gọi `IconGridView/DockView.beginDragForItem` rồi đi tiếp vào đường tiếp quản của phiên kéo (Task 7). Không dựa vào scrim làm touch target vì scrim bị ẩn ngay khi vào edit mode.
> **Lỗi không tái hiện được (người dùng tình cờ gặp 2026-10-29):** "kéo đi bị nhận là chuyển trang và lộ chính icon cần kéo đi bên dưới". Không có cách tái hiện nên không xác định được điểm gãy; log tạm `M7DRAG` đã gỡ. **Ghi nhận là nợ theo dõi:** nếu gặp lại, bật lại log ở các điểm: `IconDragGesture` (armed/begin/swipe-before-armed), `HomeActivity.dispatchTouchEvent` (takeover/drop), pager `onPageSelected`/`onPageScrollStateChanged` lúc có phiên kéo, `IconGridView.endDragVisualState`, rồi ghi lại bước tái hiện.
> Kiểm chứng 10c: Camera/Chat hiện shortcut, bấm mở đúng; app không shortcut chỉ còn 4 mục; Thông tin ứng dụng mở màn hệ thống; Ẩn ứng dụng -> icon biến + dồn như Task 8 (kiểm cả icon dock); Sửa màn hình chính -> vào edit mode rung; Gỡ cài đặt mở hộp thoại hệ thống (bấm Hủy).
> Kiểm chứng 10b: nhấn giữ icon lưới/dock -> nền mờ, icon sáng nổi đúng chỗ, menu 4 mục hiện cạnh icon (dock thì lật lên trên); KHÔNG vào edit mode, lưới không rung; chạm nền/Back/bấm 1 mục -> đóng sạch (icon nổi biến mất, không sót scrim); sau đó vào edit mode bằng cách khác (chưa có đường nào ngoài menu ở mốc này) — bỏ qua.

**Files:**
- Create: `app/src/main/java/gambi/global/os26launcher/domain/model/AppShortcutItem.kt` — `data class AppShortcutItem(val id: String, val shortLabel: String, val icon: Bitmap?)`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/repository/ShortcutRepository.kt` — interface
- Create: `app/src/main/java/gambi/global/os26launcher/data/apps/ShortcutRepositoryImpl.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/GetAppShortcutsUseCase.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/LaunchShortcutUseCase.kt`
- Create: `app/src/main/java/gambi/global/os26launcher/domain/usecase/UninstallAppUseCase.kt` — dùng `Intent.ACTION_DELETE` (`data/system`, không cần repository riêng vì chỉ là 1 `startActivity`)
- Create: `app/src/main/java/gambi/global/os26launcher/ui/home/menu/IconActionMenu.kt` — `PopupWindow`/`ListPopupWindow` tùy biến, hiện shortcut + 2 mục cố định
- Create: `app/src/main/res/layout/view_icon_action_menu_item.xml`
- Modify: `app/src/main/java/gambi/global/os26launcher/di/RepositoryModule.kt` — bind `ShortcutRepository`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconCellView.kt` — nối luồng long-press → menu / kéo theo slop

**Interfaces:**
- Produces: `ShortcutRepository.getShortcuts(componentKey: ComponentKey): List<AppShortcutItem>` (bắt mọi `Exception`, trả `emptyList()`), `ShortcutRepository.startShortcut(componentKey: ComponentKey, shortcutId: String)`
- Consumes: `DragHelper.beginDrag(...)` (Task 5) khi slop vượt ngưỡng trước khi menu kịp hiện

- [x] **Step 1: `ShortcutRepositoryImpl`** — bọc `LauncherApps.getShortcuts(ShortcutQuery().setQueryFlags(MATCHED_PINNED or MATCHED_DYNAMIC), ...)`, `try/catch` toàn bộ trả rỗng nếu lỗi (Review Focus #3); `startShortcut` gọi `LauncherApps.startShortcut(...)`.

- [x] **Step 2: `UninstallAppUseCase`** — `Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))`, `startActivity` từ `Context` được inject (không cần `<queries>` thêm — action nhắm thẳng tới package installer hệ thống, không qua `resolveActivity`).

- [x] **Step 3: `IconActionMenu`** — `PopupWindow` định vị cạnh icon (dùng `showAsDropDown`), danh sách gồm: từng `AppShortcutItem` (nếu có) + "Thông tin ứng dụng" + "Gỡ cài đặt"; mỗi dòng dùng `view_icon_action_menu_item.xml` (icon + label).

- [x] **Step 4: Luồng 3 trạng thái `Bình thường → Shortcut → Edit` (Ruling G1)** *(thiết kế đã duyệt 2026-10-29: CHỈ đường B; menu = shortcut động + Thông tin ứng dụng + Ẩn ứng dụng + Sửa màn hình chính + Gỡ cài đặt, bỏ "Chọn"; làm mờ = scrim tối đơn giản, blur thật để sau. Các ý "đường A"/"hoặc" bên dưới coi là BỎ)* —
  - **Trạng thái là dữ liệu trong `HomeUiState`** (bài học Task 5: không để trong view): `iconMenu: IconMenuState?(itemId, container, anchorRect)`; `null` = không ở Shortcut. `editMode` giữ nguyên. Hai trạng thái loại trừ nhau.
  - **Nhấn giữ lần 1 (Bình thường, trên icon):** `viewModel.onIconLongPressed(itemId, container, anchorRect)` -> `iconMenu = ...`, **KHÔNG** `enterEditMode()` (thay chỗ nối tạm ở Task 4 Step 3). Rung nhẹ. Lần chạm này không kéo được.
  - **Lớp làm mờ:** 1 view scrim phủ toàn `home_root` phía trên lưới/dock/page indicator (mờ tối, không chặn cử chỉ nào ngoài việc chạm = đóng), và icon được nhấn vẽ lại NỔI phía trên scrim bằng đúng cơ chế chụp bitmap + `DragLayer` của Task 3 (tái dùng, không tạo lớp vẽ thứ hai); menu (`IconActionMenu`) định vị cạnh icon theo `anchorRect`.
  - **Rời Shortcut vào Edit (đã chốt: CHỈ đường B, giống iOS):** KHÔNG nhấc tay, di vượt slop -> `iconMenu = null` + `enterEditMode()` + `beginDrag` ngay trong cùng lần chạm. Cần `IconDragGesture` có chế độ "đã arm sẵn" — lần nhấn giữ MỞ Shortcut (lúc `canDrag()` còn false) vẫn đặt `armed = true`, để MOVE vượt slop sau đó đi thẳng vào `begin` (hiện chỉ arm khi edit mode đã bật từ lúc DOWN, xem Task 6 Ruling G1). Menu đóng ngay khi bắt đầu kéo. Vào edit mode còn có đường qua mục menu "Sửa màn hình chính". *(Đường A — nhấn giữ lần 2 — đã BỎ.)*
  - **Đóng Shortcut:** chạm ra ngoài menu/icon, nút Back, bấm 1 mục menu, hoặc xoay/đổi kích thước màn hình (`iconMenu` giữ theo ViewModel nhưng `anchorRect` cũ vô hiệu -> đóng).
  - Trong edit mode (đã bật từ trước): kéo cần giữ-lại rồi di như G1 (đã code ở Task 6). Vuốt nhanh vẫn lật trang.
  - **Đã chốt (2026-10-29):** menu = shortcut động + `Thông tin ứng dụng` + `Ẩn ứng dụng` (`HideAppUseCase`) + `Sửa màn hình chính` (= vào edit mode, đóng menu) + `Gỡ cài đặt`; bỏ `Chọn`. Làm mờ = scrim tối đơn giản, blur thật để sau.
  - **Tiến độ Step 4 theo mốc:** 10a–10d xong, người dùng test tay PASS (2026-10-29).
  - **Review Focus:** nhấn giữ lần 2 rất sát lần 1 (double long-press nhanh); xoay màn hình khi đang Shortcut; menu tràn mép màn hình/đáy (lật lên trên nếu thiếu chỗ); app không có shortcut động (menu chỉ còn mục cố định).

- [x] **Step 5: "Thông tin ứng dụng"** — `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))`.

- [x] **Step 6: Kiểm chứng tay (Live 4)** — nhấn giữ 1 icon rồi nhấc tay ngay tại chỗ: menu hiện đủ mục (kiểm cả app có shortcut động như Camera/Chat và app không có), lưới **không** rung dạng edit mode lộn xộn trong khi menu đang mở (menu che mất phần đó, nhưng xác nhận bấm "Gỡ cài đặt"/"Thông tin ứng dụng" mở đúng màn hệ thống). Nhấn giữ rồi kéo tay đi luôn (không nhấc tay tại chỗ): menu không hiện/tự đóng ngay, chuyển thẳng sang kéo như Task 5.

- [x] **Step 7: Commit**

### Task 11: Đóng M7 — dọn nợ `hidden_apps`, chống seed lại, đồng bộ tài liệu [HOÀN THÀNH 2026-10-29]

> Thêm 2026-10-29 sau khi rà đóng M7 (không phải Task 11 của kế hoạch gốc đã xóa, xem ghi chú cuối file). Oppo A3x: người dùng xác nhận vẫn test trên Oppo suốt các Task 8–10, dock kính + scrim/icon nổi không có gì bất thường -> không cần mục kiểm riêng.

**Files:**
- Modify: `data/layout/db/HiddenAppDao.kt` — `deleteByPackage(packageName, userSerial)`.
- Modify: `data/layout/LayoutReconciler.kt` — `removePackages` dọn `hidden_apps` của package bị gỡ (trước khi return sớm); `catchUp` dọn hàng `hidden_apps` của app đã gỡ lúc launcher không chạy (chung phép hỏi `isPackageInstalled` với các hàng layout); `seedIfEmpty` chỉ dựng từ app KHÔNG bị ẩn.
- Modify (tài liệu): `docs/Launcher-Features.md` (dòng 7, 8), `docs/project_structure.md` (dòng M7 + cây file), `docs/room-schema.md` (`hidden_apps` đã có UI, ghi chú Q8/seed).

- [x] **Step 1: Dọn `hidden_apps` khi gỡ thật (Review Focus)** — Điểm chạm: (a) sự kiện `PACKAGE_REMOVED` lúc launcher chạy -> `removePackages`; (b) gỡ lúc launcher không chạy -> `catchUp`. App tạm không khả dụng (suspend/disable/rút thẻ) KHÔNG bị dọn: `catchUp` chỉ xóa khi `isPackageInstalled == false`. **Test tay PASS (2026-10-29).**
- [x] **Step 2: Chống seed lại sau khi ẩn hết app** — `seedIfEmpty`: bố cục chỉ dựng từ app không ẩn; không còn app nào thì không dựng gì (bỏ ẩn sau đó tự tạo trang qua `placeNewInTransaction`). Máy mới (`hidden_apps` rỗng) hành vi như cũ. Dùng `hidden_apps` làm dấu thay vì thêm cờ vì trong M7 cách duy nhất để `launcher_items` về 0 hàng là ẩn hết. **Test tay PASS (2026-10-29).** *Giới hạn đã biết:* ẩn HẾT app rồi thoát edit mode và khởi động lại thì Home trống và không còn icon nào để nhấn giữ vào edit mode/"Thư viện" (ghi nợ M8+, cực hiếm).
- [x] **Step 3: Kiểm chứng tay (Live 4)** — (a) ẩn 1 app bằng "−", rồi gỡ thật app đó (Cài đặt hoặc menu "Gỡ cài đặt"): hàng `hidden_apps` biến mất (tab "Đã ẩn" không còn nó); cài lại app -> xuất hiện ở ô trống đầu tiên của Home như app mới. (b) gỡ thật 1 app đã ẩn lúc launcher không chạy (kill launcher, gỡ, mở lại): tab "Đã ẩn" không có app mồ côi. (c) app thường vẫn đúng: gỡ 1 app chưa ẩn -> dồn ô như M4. (d) bộ máy seed: xóa dữ liệu app (máy mới) -> seed bình thường (dock + lưới mặc định). (Ca "ẩn hết app rồi khởi động lại" không cần thử.)
- [x] **Step 4: Đồng bộ tài liệu & đóng M7** — cập nhật 3 file tài liệu ở trên cho khớp thực tế M7 (kéo-thả, dấu "−" dồn trang, Thư viện ứng dụng 2 tab, menu nhấn giữ + shortcut, quyết định cascade/ghost page), đánh dấu M7 hoàn thành trong `project_structure.md`; sửa dòng "M7 còn 10 Task" ở cuối file này thành 11.
- [x] **Step 5: Commit**

**Nợ chuyển sang M8+ (chốt khi đóng M7, 2026-10-29):**
- Ẩn HẾT app rồi khởi động lại: Home trống và không còn đường vào edit mode (cần lối vào Thư viện ứng dụng không phụ thuộc icon).
- Dấu "−" chưa hỗ trợ TalkBack (contentDescription/hành động thay thế cho kéo-thả).
- Dock đủ 4 icon thẳng cột với lưới: chưa thử tay.

---

> **Task 11 (kế hoạch gốc) đã bị xóa:** nội dung của nó (tự xóa trang trống / tự tạo trang mới khi cascade) đã có quyết định cuối sau khảo sát iPhone XS Max và được gộp thẳng vào Global Constraints + Task 1 (`LayoutReconciler.insertIntoPage`/`moveItem`) — không còn là task riêng, không chặn gì nữa. M7 có **11 Task** (1–11; Task 11 hiện tại là "Đóng M7", thêm sau).
