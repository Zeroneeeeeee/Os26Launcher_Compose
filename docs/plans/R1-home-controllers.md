# R1 — Tách `HomeActivity` thành các controller (không đổi hành vi) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `refactor/r1-home-controllers` · **Worktree:** `E:\SourceTree\IosLauncher-wt\R1-home-controllers` · **PR:** #5 (draft) = **R1a** (Task 1–6); **R1b** (Task 7–11) là PR riêng mở sau khi R1a merge · **Bắt đầu:** 2026-11-04 · **Plan duyệt:** 2026-11-04 (chủ dự án, gồm cả việc chia hai PR)
**Trạng thái:** Plan đã duyệt 2026-11-04. R1a đã merge (#5). R1b (PR #7, draft): Task 7–11 xong (code + build xanh), chờ điều phối chạy hồi quy cử chỉ và bài kiểm cuối
**Khóa:** không (không đổi schema Room, không thêm khóa DataStore). Giữ độc quyền `HomeActivity.kt` (R1a rồi R1b); `activity_home.xml` **không được sửa** trong plan này nên khóa file đó đã nhả cho AN (điều phối, vòng 1/5).

**Goal:** `HomeActivity.kt` (962 dòng, 37 thành viên: trạng thái, callback Back, listener) thu về lớp "keo" khoảng 200–250 dòng; mọi nghiệp vụ UI chuyển vào các controller nhỏ, mỗi cái một mối quan tâm. **Hành vi giữ nguyên từng chi tiết**, kể cả lỗi đã biết (kính không vẽ lại khi đổi ảnh nền ở Cài đặt, ghi ở AN). Đích thực sự: nhánh AN (hoạt ảnh) sẽ thêm các hook nhỏ cho mở/đóng thư mục, panel nhóm Library, bấm ô nhóm Library và làm mới kính khi đổi ảnh nền — các hook đó phải nằm trong file controller nhỏ, không phải `HomeActivity`.

**Chia hai PR (điều phối chốt vòng 1/5):** **R1a = Task 1–6** (Back, thư mục, Library, dock + `RecreateGuard`, ảnh nền, pager) merge trước để mở khóa AN và G2b; **R1b = Task 7–11** (menu icon, tìm kiếm, bộ định tuyến cử chỉ, thanh hệ thống, dọn Activity) làm tiếp từ `main` sau R1a, nhánh `refactor/r1b-home-controllers`. Sau R1a `HomeActivity` còn khoảng 520 dòng (vẫn đúng hành vi, build xanh); sau R1b còn khoảng 220.

**Architecture:** Controller là class Kotlin thuần trong package mới `ui/home/controller/`, dựng ở `onCreate` sau `setContentView`, nhận `ActivityHomeBinding` (hoặc đúng các view nó cần), `HomeViewModel` và, cho thứ cần lấy từ chỗ khác, **lambda hoặc view truyền thẳng** vào constructor. `Host` interface chỉ dùng khi có từ ba lời gọi trở lên (duy nhất `HomeBackController`, cùng mẫu `LibraryOverlayController.Host` của G2a B2). Mỗi controller có tối đa bốn lối vào, đặt tên giống nhau: `setup()` (thay các `setupX()` cũ), `render(state: HomeUiState)` (thay đoạn tương ứng của `render()`), và các móc vòng đời gọi tay từ Activity (`onResume`, `onStop`, `onDestroy`, `onNewIntent`). Controller chỉ tham chiếu nhau **một chiều, không vòng** (xem "Thứ tự phụ thuộc"); thứ chung đi qua `HomeScreenState` (giữ `ui` và `search` mới nhất) hoặc qua lambda do `HomeActivity` nối. Activity giữ nguyên **thứ tự gọi** của `render()`, của `dispatchTouchEvent`, của đăng ký Back và của dọn dẹp `onStop`/`onDestroy`, vì cả bốn thứ tự đều là một phần hành vi.

**Tech Stack:** Không thêm dependency, không đổi tài nguyên (không `strings.xml`/`dimens.xml`/`colors.xml`/layout mới).

**Spec:** Nợ "Tách `HomeActivity` (~940 dòng)" ghi ở mục "Demo polish" của `AGENTS.md`; đợt 0b và dòng R1 ở `MultiAgent-Workflow.md` mục 9; bài học liên quan trong `AGENTS.md`: "M7 — trạng thái kéo-thả KHÔNG được nằm trong view của RecyclerView/ViewPager2", bẫy `requestDisallowInterceptTouchEvent` (mục M7 Task 5), "G2a B2: thứ tự Back", "M8b Task 2" (cử chỉ ở `dispatchTouchEvent`, insets, `onNewIntent`), "Inset và layout màn hình chính" (B2, margin chứ không padding root), TL1 (inset bỏ qua visibility).

## Vì sao chọn thiết kế này

Nhãn nguồn: **[code]** = đọc `HomeActivity.kt` và file liên quan trong worktree này, 2026-11-04; **[docs]** = bài học ghi trong `AGENTS.md`; **[suy luận]** = chưa kiểm chứng trên máy.

### Vấn đề cần giải

1. **`HomeActivity` gom 11 mối quan tâm vào 37 thành viên** **[code]**: pager và chấm trang (`setupHomePager`, `requestPillMode`, `onDragEdge`), kéo-thả và lật mép (`dispatchTouchEvent`, `dragTouchTakenOver`, `pendingFlipToNewPage`), menu nhấn giữ icon (`renderIconMenu`, `pendingMenuDrag`, `iconMenuSnapshot`), panel thư mục, App Library và panel nhóm, lớp tìm kiếm (`dispatchWithSearchSwipe`, `searchTouchSwallowed`), dock và kính (`setupDock`, `appliedGlassOverride`, `dockGlassCache`), ảnh nền và hộp thoại wallpaper, insets và thanh hệ thống, edit mode và toolbar, bảy `OnBackPressedCallback`. Mọi tính năng mới đều chạm file này (đó là lý do nó nằm trong danh sách file nóng độc quyền, `MultiAgent-Workflow.md` mục 5.2).
2. **AN phải chờ.** Theo đợt 0b, AN làm phần trong view trước; phần nối vào Activity "chờ R1 merge rồi sửa trong controller mới". Nên thứ tự Task của R1 phải đặt các controller AN cần lên trước, và R1 phải merge sớm.
3. **Bốn thứ tự ngầm là hành vi** **[code][docs]**: (a) Back dùng LIFO của `OnBackPressedDispatcher` (đăng ký sau được ưu tiên trước; tìm kiếm → menu → panel thư mục → edit mode → panel nhóm → Library → callback rỗng); (b) `render()` gọi lần lượt ảnh nền → hộp thoại → pager → dock → visibility → edit mode → menu → thư mục → Library → pill; (c) `dispatchTouchEvent` kiểm `pendingMenuDrag`, rồi phiên kéo, rồi Library, rồi vuốt tìm kiếm; (d) `onDestroy` huỷ lật trang, đóng hộp thoại, gỡ `DragLayer`, gỡ hẹn giờ pill. Tách sai thứ tự = đổi hành vi mà build vẫn xanh.
4. **Bẫy cũ không được tái phạm** **[docs]**: trạng thái kéo-thả là dữ liệu (`HomeUiState.draggingItemId`), không để trong view của `ViewPager2`; `Activity.dispatchTouchEvent` phải nhận mọi chạm TRƯỚC cây view khi đang có phiên kéo (ô nguồn có thể bị `ViewPager2` tháo khỏi cây); `requestDisallowInterceptTouchEvent` do ô icon gọi, không liên quan tới Activity nhưng cho thấy các view bên dưới giả định Activity tiếp quản đúng chuỗi.
5. **Không có test tự động** (quy ước dự án), nên phương án nào cũng phải kiểm được bằng tay theo danh mục hồi quy ở cuối plan, và mỗi Task đủ nhỏ để đối chiếu bằng mắt (`git diff --color-moved`).

### Các phương án đã xét

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Controller Kotlin thuần + lambda (Host chỉ cho Back)** | Class thường trong `ui/home/controller/`, Activity dựng và nối, giữ thứ tự gọi | Không đổi XML, không đổi vòng đời; thứ tự gọi nằm ở một chỗ nên giữ nguyên hành vi được; đã có tiền lệ chạy tốt: `LibraryOverlayController` (G2a B2, mượn mẫu `LauncherOverlay` của Launcher3), `SearchSwipeDetector`, `PagerFlipAnimator`; mỗi controller là một Task độc lập, revert được riêng | Vẫn còn "keo" ở Activity (thứ tự render, móc vòng đời, lambda nối); controller giữ view nên phải gỡ ở `onDestroy`; trình biên dịch không bắt được sai thứ tự | **Chọn** |
| B. Fragment cho từng phần (thư mục, Library, tìm kiếm…) | Mỗi mảng là một `Fragment` với layout riêng | Tách vòng đời rõ | `activity_home.xml` là MỘT `FrameLayout` với thứ tự z đan xen (`home_content` < `library_view` < `category_group_panel` < … < `folder_panel` < menu < tìm kiếm < `drag_layer`) **[code]**; mỗi Fragment cần container riêng nên phải xếp lại cả layout, và `backdrop_image` là nguồn cho kính của dock và các panel (`setBackdropSource(binding.backdropImage, …)`) **[code]**. Sau `recreate()` FragmentManager khôi phục cả view state trong cây `ViewPager2`/`RecyclerView`: đúng loại rủi ro của bài học M7 **[docs]**. Phần lớn (cử chỉ ở `dispatchTouchEvent`, Back) vẫn nằm ở Activity nên không giải được vấn đề chính. Chi phí XML + vòng đời lớn gấp nhiều lần | Không |
| C. View gốc tùy biến (`HomeRootLayout`) | Chuyển `dispatchTouchEvent`/insets/Back vào một `FrameLayout` con | Gần dữ liệu view | Đổi thẻ gốc của `activity_home.xml`; chuyển cử chỉ từ `Activity.dispatchTouchEvent` sang `View.dispatchTouchEvent` đổi thứ tự so với DecorView (**[suy luận]**, không có lý do gì phải mạo hiểm); View không có `lifecycleScope`, `ViewModel`, Back dispatcher nên phải truyền vào, rốt cuộc vẫn cần lớp keo | Không |
| D. Hàm mở rộng `HomeActivity.xxx()` chia ra nhiều file | Kotlin không có partial class, nên chỉ chuyển dòng chữ | Rẻ nhất | 37 thành viên trạng thái vẫn ở Activity; AN vẫn phải sửa `HomeActivity` để thêm trạng thái; không giảm bề mặt va chạm | Không |
| E. Controller `@ActivityScoped` do Hilt tiêm | `@Inject constructor` | Nhất quán với `GlassSurfaceFactory` | Hilt tiêm ở `super.onCreate()`, trước `setContentView`, mà controller cần `binding`; phải làm hai pha `attach(binding)` với trường `lateinit`/null **[code]**. Duy nhất `GlassSurfaceFactory` là thứ Activity thật sự cần tiêm, truyền tay một tham số là đủ | Không |

### Vì sao chọn A, và ranh giới

- Dự án đã có ba ví dụ chạy được cho đúng mẫu này **[code]**: `LibraryOverlayController` (tách cử chỉ + tiến độ khỏi Activity bằng `Host`), `SearchSwipeDetector`, `PagerFlipAnimator`. Mọi chỗ khác chỉ nối thêm.
- Ranh giới cắt theo **mối quan tâm** chứ không theo tầng, để mỗi hook của AN chỉ chạm một file: mở/đóng thư mục (`FolderPanelController`), Library và panel nhóm (`AppLibraryController`), ảnh nền đổi → làm mới kính (`BackdropController` + `DockController`).
- Dùng lambda thay vì để controller gọi nhau tùy ý: tránh vòng phụ thuộc (pager cần menu cho `onLongPressIcon`; Library cần pager cho `canOpen`; tìm kiếm cần Library và inset cho vùng bắt đầu vuốt). `Host` interface chỉ giữ cho Back, nơi có sáu hành động đóng.
- Móc vòng đời gọi **tay từ Activity theo đúng thứ tự cũ**, không dùng `LifecycleObserver`: observer chạy ở `super.onDestroy()` còn thân `onDestroy` hiện chạy TRƯỚC `super`, đổi thứ tự dọn **[code]**.

### Đánh đổi chấp nhận

- Thêm 12 file nhỏ và một ít lambda nối; đổi lại AN, G2c, M8d… không phải mở `HomeActivity`.
- Chỉ chuyển nguyên văn, **không "tiện tay" sửa** (kể cả code dư như `HomeViewModel.onImagePicked`, comment mồ côi cạnh `onCreate`). Dọn dẹp để sau; xét lại nếu review thấy cấu trúc cản việc đọc.
- Khi nào xét lại: nếu sau R1 vẫn cần thêm trạng thái dùng chung giữa hơn ba controller thì `HomeScreenState` nên nâng thành một lớp trạng thái UI riêng (mẫu MVI nhẹ); chưa cần bây giờ (YAGNI).

## Danh sách controller (ước lượng; Task 11 ghi số đo thật)

**Vì sao tổng ~1.090 dòng chứ không phải ~1.280 như bản trước.** Bản trước cộng ước lượng "dư" cho KDoc và `Host`, chưa tính từ dòng thật. Bản này tính từ phạm vi dòng cần chuyển trong `HomeActivity.kt` hiện tại: khoảng 740 dòng mã + chú thích (962 → ~220), cộng chi phí cố định của mỗi file mới (package + import ~15 dòng, khai báo class + constructor ~6 dòng) cho 12 file, khoảng 250 dòng. Nghĩa là phần phình ra so với 740 là **import và constructor**, không phải mã mới; các `Host` interface đã bỏ (xem dưới). Chú thích lịch sử (`M7 Task 10`…) giữ nguyên văn theo D4 nên không cắt được.

| Controller | PR | Nhận từ `HomeActivity` | ~dòng |
|---|---|---|---|
| `HomeScreenState` | R1a | hai trường `ui`, `search`. Giữ vì 5 controller đọc cả hai (Library `canOpen`, pill, Search `enabled` và pill click, IconMenu `anchor`, Router); thay thế 2 lambda × 5 constructor | 12 |
| `HomeBackController` | R1a | 6 callback Back + callback rỗng, thứ tự đăng ký; `setXxx(Boolean)` bật/tắt. **Duy nhất có `Host`** (6 hành động đóng) | 70 |
| `FolderPanelController` | R1a | `setupFolderPanel`, `renderFolderPanel`, `closeFolder()`, đóng khi nhấn Home | 60 |
| `AppLibraryController` | R1a | `libraryController`, `setupLibrary`, `renderLibrary`, `renderCategoryPanel`, `canOpenLibrary`, `applyLibraryProgress`, nhận `categoryGroups`, `libraryRendered` | 130 |
| `DockController` | R1a | `setupDock`, `updateDockCells`, `appliedGlassOverride`, `glassOverrideChanged()`, `dockGlassCache`, `lastDockItems` | 100 |
| `RecreateGuard` | R1a | thân `recreateIfGlassOverrideChanged` đổi thành danh sách kiểm tra đăng ký được (xem hook G2b) | 35 |
| `BackdropController` | R1a | phần ảnh nền của `render()`, `shownBackdrop`, hộp thoại wallpaper, `reloadBackdropIfChanged`, `labelRes()` | 85 |
| `HomePagerController` | R1a | `setupHomePager`, `pagerFlip`, `onDragEdge`, danh sách trang/ghost trong `render()`, `requestPillMode` + `pillToSearch` | 150 |
| `IconMenuController` | R1b | `renderIconMenu`, `onIconLongPress`, `iconMenuSnapshot`, cờ vẽ, listener scrim, `pendingMenuDrag`, `startDragFromMenu` | 150 |
| `SearchOverlayController` | R1b | `renderSearch`, `handleSearchEvent`, `searchSwipeDetector`, `dispatchWithSearchSwipe`, `searchStartZone`, pill click | 110 |
| `HomeTouchRouter` | R1b | thân `dispatchTouchEvent`, `dragTouchTakenOver`, nối `DragHelper` (attach/detach, giới hạn mép) | 90 |
| `SystemBarsController` | R1b | `applySystemBarInsets`, `applySystemBarStyling`, `statusBarTopPx`, `editToolbarClearancePx`, `applyStatusBarForEditMode` | 95 |
| **Tổng** | | R1a ~640 · R1b ~445 | ~1.090 |
| `HomeActivity` (còn lại) | | sau R1a ~520 · sau R1b ~220: `onCreate` nối controller, `render()` theo thứ tự cũ, `handleEvent`, nút Xong/Cài đặt và `editToolbar.isVisible`, móc vòng đời | |

**Đã cắt so với bản trước:** `EditModeController` (~50 dòng) bỏ hẳn: nút Xong/Cài đặt chỉ là hai `setOnClickListener` và một `isVisible` (4 dòng, ở lại Activity), phần đáng tách (ẩn thanh trạng thái khi edit) gộp vào `SystemBarsController` vì cùng dùng `WindowInsetsControllerCompat` và inset bỏ qua visibility. `Host` của Folder, Library, Dock, Pager, IconMenu, Search, Router bỏ: mỗi cái chỉ chuyển tiếp 1–3 lời gọi, thay bằng lambda hoặc view/`binding` truyền thẳng (ví dụ pager gọi `binding.dockView.setGlassInvalidationSuppressed(...)` đúng như code cũ, không cần `setDockGlassSuppressed`; vùng bắt đầu vuốt tìm kiếm đọc `binding.dockView.getLocationOnScreen` trực tiếp). `HomeScreenState` giữ vì thay được số lambda nói trên; nếu review thấy nó không kiếm được chỗ thì xóa và để mỗi controller giữ bản `lastState` riêng từ `render(state)`.

**Thứ tự phụ thuộc (một chiều):** `HomeBackController` là lá (Folder, Library, IconMenu, Search, `HomeActivity` gọi `setXxx`). `HomePagerController` nhận lambda `onIconLongPress` (Activity nối tới `IconMenuController` từ Task 7). `AppLibraryController` nhận lambda `isPagerSettledOnLastPage` (trỏ sang `HomePagerController` từ Task 6). `SearchOverlayController` nhận lambda `isLibraryIdle` và `statusBarTopPx`. `HomeTouchRouter` giữ tham chiếu IconMenu, Library, Search, Pager (không ai giữ Router). Không vòng.

## Hook của AN và G2b nằm ở đâu (đối chiếu với mốc cắt R1a = Task 1–6)

| # | Hook | File sửa (không phải `HomeActivity`) | Điểm vào | Task | Trong R1a? |
|---|---|---|---|---|---|
| H1 | AN: thư mục mở/đóng có hoạt ảnh | `controller/FolderPanelController.kt` | `render(state)`: so `openFolderId` null ↔ id thay cho `binding.folderPanel.render(...)`; Back, nhấn Home, bấm nền đều qua `closeFolder()` | 2 | Có |
| H2 | AN: **vị trí ô thư mục vừa bấm** (làm điểm gốc hoạt ảnh) | `controller/HomePagerController.kt` | `setup()`, tham số `onFolderClick = viewModel::openFolder` của `HomePagerAdapter` (hôm nay chỉ có id). AN đổi chữ ký ở `HomePagerAdapter`/`IconGridView` (ngoài vùng R1) rồi sửa lambda này | 6 | Có. **Đây là lý do cắt R1a ở Task 6, không phải Task 5** |
| H3 | AN: panel nhóm Library mở/đóng | `controller/AppLibraryController.kt` | `renderCategoryPanel(state)` | 3 | Có |
| H4 | AN: bấm ô nhóm Library (3/4 ô) | `controller/AppLibraryController.kt` | `setup()`, chỗ gán `binding.libraryView.onGroupClick` | 3 | Có |
| H5 | AN: làm mới kính khi đổi ảnh nền, mọi phần tử kính | `controller/BackdropController.kt` (+ `DockController.kt`) | `BackdropController.addBackdropListener(BackdropListener)` (ba loại sự kiện `LOADED/REPLACED/CLEARED`); `DockController.onBackdropChanged` đã đăng ký thay cho `setBackdropSource` + `pulseGlassRefresh` trong `render()`. AN thêm listener cho panel, pill, tìm kiếm ở file riêng của AN | 4, 5 | Có |
| H6 | G2b: `recreateIfIconStyleChanged` | `controller/RecreateGuard.kt` (mới, Task 4) | `RecreateGuard.add(check)`; check kính của dock do `DockController.glassOverrideChanged()` sở hữu và đăng ký. Check kiểu icon thêm một `add { … }` | 4 | Có |

Kiểm lại từng hook với mốc cắt: không hook nào cần Task 7–11. Cử chỉ vuốt mở/đóng Library (`onTouch`) vẫn nằm ở `dispatchTouchEvent` của Activity đến Task 9 nhưng AN chỉ cần tiến độ (`applyLibraryProgress`, Task 3), không cần router. `onIconLongPress` còn ở Activity trong R1a, các controller R1a nhận qua lambda nên R1b đổi nguồn mà không sửa controller.

**`recreateIfGlassOverrideChanged` thuộc về ai.** Cờ `appliedGlassOverride` được gán ngay sau khi `setupDock()` đọc cài đặt, nên `DockController` sở hữu điều kiện (`glassOverrideChanged()`: `applied != null && applied != viewModel.currentGlassLevelOverride()`). Việc *chạy* kiểm tra ở `onResume` + gọi `recreate()` chuyển vào `RecreateGuard` (Task 4, **trong R1a**): `RecreateGuard.onResume()` chạy một `lifecycleScope.launch`, duyệt các check đã đăng ký theo thứ tự, gặp check đầu tiên đổi thì `recreate()` đúng một lần. Với một check (như hôm nay) hành vi y hệt bản cũ; thứ tự trong `onResume` của Activity giữ: `recreateGuard.onResume()` → `reloadBackdropIfChanged` → `refreshDefaultLauncher`. Chỗ cho kiểm tra thứ hai của G2b: một dòng `recreateGuard.add { … }` (xem bảng dưới).

### Hợp đồng với AN và G2b (điều phối chốt 2026-11-04)

1. **Hook ảnh nền (nhãn của AN: H1).** `BackdropController.addBackdropListener(BackdropListener)` phát **ba loại sự kiện**: `LOADED` (lần nạp ảnh đầu), `REPLACED` (đổi ảnh khi đang có ảnh), `CLEARED` (ảnh bị gỡ). Không chỉ `replacing`. `fun interface BackdropListener { fun onBackdropChanged(change: BackdropChange) }`, `enum class BackdropChange { LOADED, REPLACED, CLEARED }`. `DockController` giữ nguyên hai việc cũ của dock: `setBackdropSource(...)` khi `LOADED`/`REPLACED`, `pulseGlassRefresh()` chỉ khi `REPLACED`; `CLEARED` dock không làm gì (như code cũ).
2. **Hook bấm app trong Library (AN: H2).** `AppLibraryController.setup()` là chỗ AN thêm `libraryView.onAppClick`.
3. **Hợp đồng view (AN: H3).** Đóng panel vẫn là `render(null)` (không đổi thành bật/tắt visibility). Giữ nguyên các id `home_root`, `backdrop_image`, `library_view`, `folder_panel`, `category_group_panel`, `drag_layer`. Không bọc panel hay `library_view` vào container có clip hoặc scale.
4. **`RecreateGuard`** (Task 4) cho check một dòng của G2b.
5. **Vị trí ô thư mục (H2 của bảng trên):** AN lấy rect phía view (`IconGridView`), nên chữ ký `onFolderClick` **không đổi**; Task 6 vẫn nằm trong R1a vì `HomePagerController` là chỗ AN có thể cần chạm.

### Dòng nối cần thêm vào `HomeActivity` cho AN và G2b (điều phối xếp thứ tự)

Sau R1a `HomeActivity` vẫn bị R1b khóa. R1a để sẵn một khối cuối phần nối controller trong `onCreate`, có comment `// Dòng nối cho AN/G2b: mỗi hook một dòng, thêm ở đây`; R1b không sửa dòng nào trong khối đó nên rebase hai chiều chỉ xung đột nếu hai nhánh cùng thêm một dòng liền kề.

| Ai | Dòng thêm vào `HomeActivity` | Ghi chú |
|---|---|---|
| AN H1–H4 | 0 | Sửa hoàn toàn trong `FolderPanelController`, `AppLibraryController`, `HomePagerController`. Nếu AN dựng thêm một collaborator (ví dụ bộ điều khiển hoạt ảnh) và truyền vào constructor thì tối đa 1 dòng sửa chỗ dựng controller tương ứng |
| AN H5 | 1 | `backdrop.addBackdropListener(<đối tượng làm mới kính của AN>)` |
| G2b H6 | 1 | `recreateGuard.add { <check kiểu icon> }`; giá trị "đã áp" của kiểu icon do G2b tự giữ ở chỗ nó chọn (không phải trong `HomeActivity`) |


## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Controller thuần + lambda (`Host` chỉ cho Back, D12), không Fragment/View/Hilt | Bảng phương án ở trên |
| D2 | Package `ui/home/controller/`; `LibraryOverlayController`, `PagerFlipAnimator`, `SearchSwipeDetector` giữ nguyên chỗ cũ | Chuyển file = xóa + tạo, trái quy ước "không tự xóa file"; thêm package mới thì không phải xóa gì. Điều phối chốt đúng package này (vòng 1/5) |
| D3 | Không sửa `HomeViewModel`, `HomeUiState`, `HomeEvent`, `activity_home.xml`, tài nguyên | Đã đối chiếu: mọi hàm Activity gọi ở ViewModel đều có sẵn |
| D4 | Chuyển nguyên văn, giữ nguyên chú thích lịch sử (`M7 Task 10`, `G2a B2`…) | Để `git blame` và `git diff --color-moved` còn đọc được |
| D5 | Thứ tự gọi `render()`, `dispatchTouchEvent`, đăng ký Back, dọn dẹp giữ nguyên | Review Focus 1–4 |
| D6 | Mỗi Task một commit, thông điệp `refactor: extract <Name> from HomeActivity` | Revert riêng được |
| D7 | Không dùng `LifecycleObserver` cho controller | Đổi thứ tự dọn `onDestroy` |
| D8 | Chia hai PR: R1a = Task 1–6 (PR #5), R1b = Task 7–11 (PR mới, nhánh `refactor/r1b-home-controllers` từ `main` sau khi R1a merge) | Điều phối, vòng 1/5. R1a unblock AN và G2b |
| D9 | Điều phối tự chạy S1–S18 bằng `adb` sau R1a và sau Task 9 (và bài kiểm cuối), trừ khi chủ dự án phản đối | Điều phối, vòng 1/5 |
| D10 | Dùng package `ui/home/controller/`, không chuyển file có sẵn | Điều phối, vòng 1/5 |
| D11 | Khóa `activity_home.xml` nhả cho AN | Điều phối, vòng 1/5 (R1 không sửa file này) |
| D12 | `Host` interface chỉ cho `HomeBackController`; còn lại lambda/view truyền thẳng | Điều phối yêu cầu giảm boilerplate, vòng 1/5 |
| D13 | `RecreateGuard` thay `recreateIfGlassOverrideChanged`, nằm trong R1a, để G2b thêm check kiểu icon bằng một dòng | Điều phối, vòng 1/5 |
| D14 | Hợp đồng hook đã thống nhất với AN và G2b (2026-11-04), xem mục "Hợp đồng với AN và G2b" ngay dưới | Điều phối |

## Câu hỏi mở

1. Chủ dự án có phản đối việc điều phối tự cài và chạy `adb` (D9)? Mặc định: không phản đối.
2. Lệnh nhấn Home giả lập, `input draganddrop`, tên sự kiện `wm_create_activity` chưa kiểm trên Live 4: điều phối xác nhận khi chụp baseline (xem "Baseline cần chụp").

## Ngoài phạm vi / ghi để sau

- Sửa lỗi đã biết (kính không vẽ lại sau đổi ảnh nền; bấm ô nhóm Library mở panel thay vì mở app): để AN. R1 phải giữ nguyên, kể cả lỗi.
- Dọn code dư: `HomeViewModel.onImagePicked`, comment mồ côi "Màn chọn launcher mặc định…" trước `onCreate`, import thừa nếu có. Ghi để sau (có thể gộp vào Task 11 nếu điều phối đồng ý, không bắt buộc).
- Đổi `HomeUiState`/ViewModel sang MVI, thêm `HomeController` interface chung, lazy-inject: không làm.
- Chuyển `LibraryOverlayController` sang package mới: xem D2.

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/HomeActivity.kt` | Sửa (thu nhỏ dần) | **Độc quyền** (R1a rồi R1b giữ; AN và G2b chỉ thêm tối đa 1 dòng mỗi hook sau R1a, xem bảng dòng nối) |
| `ui/home/controller/HomeScreenState.kt` | Tạo (R1a) | — |
| `ui/home/controller/HomeBackController.kt` | Tạo | — |
| `ui/home/controller/FolderPanelController.kt` | Tạo | — |
| `ui/home/controller/AppLibraryController.kt` | Tạo | — |
| `ui/home/controller/DockController.kt` | Tạo | — |
| `ui/home/controller/BackdropController.kt` | Tạo | — |
| `ui/home/controller/HomePagerController.kt` | Tạo | — |
| `ui/home/controller/IconMenuController.kt` | Tạo | — |
| `ui/home/controller/SearchOverlayController.kt` | Tạo | — |
| `ui/home/controller/HomeTouchRouter.kt` | Tạo | — |
| `ui/home/controller/SystemBarsController.kt` | Tạo | — |
| `ui/home/controller/RecreateGuard.kt` | Tạo (R1a, Task 4) | — |
| `docs/plans/R1-home-controllers.md`, `docs/knowledgements/R1-home-controllers.md` | Tạo | — |

**Không đụng:** `activity_home.xml` (khóa đã nhả cho AN), `HomeViewModel.kt`, `HomeUiState.kt`, `HomePagerAdapter.kt` (AN có thể đổi chữ ký `onFolderClick`, xem H2), `LibraryOverlayController.kt`, `PagerFlipAnimator.kt`, `SearchSwipeDetector.kt`; và vùng của nhánh khác: `data/apps/IconRenderer|IconLoader|IconCache`, `ui/settings/*`, `data/settings/*` (G2b); `IconGridView`, `IconCellView`, `DragLayer`, `core/ui/DragHelper.kt`, `FolderPanelView`, `CategoryPageView`, `CategoryGroupPanelView`, `BackgroundGlass` (AN). Controller chỉ **gọi** API công khai của các view/`DragHelper`, không sửa chữ ký. Cần sửa file ngoài vùng thì dừng và báo.

## Global Constraints

- **Không đổi hành vi.** Mỗi Task: dòng bị xóa khỏi `HomeActivity` phải xuất hiện nguyên văn (trừ tiền tố `binding.`/`state.`/tên đối tượng) trong controller. Kiểm bằng `git diff --color-moved=dimmed-zebra HEAD~1`.
- Không thêm dependency, không đổi schema, không thêm test, không thêm/sửa tài nguyên, không xóa file.
- Không đổi chữ ký công khai của view hay `DragHelper`.
- Mỗi Task: `gradlew.bat assembleDebug` xanh trong worktree, một commit, push, ghi Nhật ký.
- Không `LifecycleObserver`; không controller nào giữ tham chiếu view sau `onDestroy` (xem Review Focus 5).
- Hằng số `private companion` chuyển theo controller sở hữu nó (`DRAG_EDGE_ZONE_DP`, `PILL_IDLE_DELAY_MS`, `SEARCH_*`, `LIBRARY_FLING_DP_PER_SEC`, `EMPTY_ZONE`).
- Lệnh `adb` mở app: `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity` (Home của Live 4 là launcher gốc; `KEYCODE_HOME` không mở Os26Launcher, xem `AGENTS.md`). Nhấn Home giả lập cho `onNewIntent`: `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n gambi.global.os26launcher/.ui.home.HomeActivity` (**chưa kiểm trên Live 4**, thử ở Task 2).

## Review Focus

1. **Thứ tự Back:** bảy đăng ký đúng thứ tự cũ (callback rỗng → Library → panel nhóm → edit mode → thư mục → menu → tìm kiếm); mỗi `isEnabled` được đặt đúng chỗ cũ, gồm `closeFolderOnBack` bật theo "thư mục tồn tại trong `pages`" chứ không chỉ `openFolderId != null`.
2. **Thứ tự `render()`:** `lastState` gán đầu tiên; ảnh nền → hộp thoại → danh sách trang → dock → visibility → edit mode → menu → thư mục → Library → pill; `renderCategoryPanel` vẫn cũng chạy từ nhánh `categoryGroups`.
3. **Thứ tự `dispatchTouchEvent`:** `pendingMenuDrag` → phiên kéo (`ACTION_DOWN` sót, tiếp quản có `ACTION_CANCEL` giả một lần, MOVE/UP/CANCEL) → `LibraryOverlayController.onTouch` → vuốt tìm kiếm → `super`.
4. **Dọn dẹp:** `onStop` (`pagerFlip.cancel()`), `onDestroy` (huỷ lật, gỡ listener và đóng `wallpaperDialog`, `DragHelper.detachDragVisual()`, gỡ `pillToSearch`) cùng thứ tự cũ, trước `super`.
5. **Rò rỉ:** controller là con của Activity, không đưa vào `object`/singleton; `DragHelper.edgeListener` vẫn bị huỷ qua `detachDragVisual()`.
6. **Không sửa** file ngoài vùng; `activity_home.xml`, `HomeViewModel`, `HomeUiState` không có trong diff.
7. **`recreate()`:** `libraryRendered` (lần render đầu không animation), `appliedGlassOverride` null-vs-đã-biết, `pendingMenuDrag` không sống qua recreate (đúng như cũ).
8. Hook AN đúng chỗ ở bảng "Hook của AN nằm ở đâu".
9. `HomeActivity` sau R1a ~520 dòng, sau R1b ≤ ~250 dòng và chỉ còn: nối controller, `render()` theo thứ tự, `handleEvent`, móc vòng đời.
10. `Host` interface chỉ có ở `HomeBackController`; không controller nào có `Host` chỉ chuyển tiếp 1–3 lời gọi.
11. `RecreateGuard`: một check cho kết quả y hệt `recreateIfGlassOverrideChanged` cũ (không `recreate()` khi `appliedGlassOverride == null`, không lặp sau recreate); nhiều check thì `recreate()` đúng một lần.
12. Hợp đồng view (đóng panel = `render(null)`; id và cây view không đổi; không bọc container clip/scale) và listener ảnh nền ba loại sự kiện đúng "Hợp đồng với AN và G2b".
13. Khối "Dòng nối cho AN/G2b" có sẵn ở cuối phần nối controller trong `onCreate`.

---

## Phần R1a — PR #5 (Task 1–6)

### Task 1: Nền tảng — `HomeScreenState` + `HomeBackController`
**Bắt buộc kiểm tay:** không (tách thuần, không cử chỉ mới).
**Files:** Tạo `controller/HomeScreenState.kt`, `controller/HomeBackController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `HomeScreenState { var ui: HomeUiState; var search: SearchUiState }` (Activity gán ở đầu `render()` và `renderSearch()`; thay `lastState`/`lastSearchState`). `HomeBackController(activity, host)` với `Host { closeSearch(); closeIconMenu(); closeFolder(); exitEditMode(); closeCategoryGroup(); closeLibrary() }` và `setSearchOpen/setIconMenuOpen/setFolderOpen/setEditMode/setCategoryGroupOpen/setLibraryOpen(Boolean)` (chỉ gán `isEnabled`).
Consumes: `HomeViewModel`, `SearchViewModel`.
- [x] **Step 1:** Tạo `HomeScreenState`; thay mọi `lastState`/`lastSearchState` trong Activity bằng `screen.ui`/`screen.search`.
- [x] **Step 2:** Chuyển 6 `OnBackPressedCallback` + callback rỗng vào `HomeBackController`, giữ **đúng thứ tự `addCallback`**. Các hàm `render*` trong Activity gọi `back.setXxx(...)` thay cho `xxxOnBack.isEnabled = ...`. Hành động đóng thư mục (`commitTitle()` + `closeFolder()`) tạm ở Activity, chuyển ở Task 2.
- [x] **Step 3:** Build `gradlew.bat assembleDebug` xanh; commit `refactor: extract HomeBackController from HomeActivity`; push; Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S6 (a)–(g): Back khi Home trống không thoát app; edit mode, thư mục, menu, tìm kiếm, Library, panel nhóm, và các chồng đóng đúng lớp trên cùng trước.

### Task 2: `FolderPanelController` (hook H1)
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/FolderPanelController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `FolderPanelController(binding.folderPanel, viewModel, scope, back, onIconLongPress: (IconPress) -> Unit)`; `setup()`, `render(state)` (thay `renderFolderPanel`, gọi `back.setFolderOpen`), `closeFolder()` = `commitTitle()` + `viewModel.closeFolder()`. `onIconLongPress` tạm trỏ về hàm còn ở Activity đến Task 7. `HomeBackController.Host.closeFolder` và nhánh `onNewIntent` đều gọi `folder.closeFolder()`.
Consumes: `HomeBackController`, `FolderPanelState`, `PlacedFolder`.
- [x] **Step 1:** Chuyển `setupFolderPanel`, `renderFolderPanel`.
- [x] **Step 2:** Giữ nguyên: thư mục biến mất (giải tán, ẩn hết app) khi `gridStatus == Ready` thì `viewModel.closeFolder()`; mở app từ panel đóng panel luôn (D6 G2a).
- [x] **Step 3:** Build, commit `refactor: extract FolderPanelController from HomeActivity`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S7, S6(c)(g), S12 (theme đổi khi thư mục mở), thử lệnh nhấn Home giả lập cho `onNewIntent`.

### Task 3: `AppLibraryController` (hook H3, H4)
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/AppLibraryController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `AppLibraryController(binding, viewModel, scope, screen, back, searchOpen: (showAll: Boolean) -> Unit, isPagerSettledOnLastPage: () -> Boolean)`; `setup()` (thay `setupLibrary`, gồm `onGroupClick`), `render(state)` (thay `renderLibrary`), `renderCategoryPanel(state)`, `onCategoryGroups(groups)` (thân nhánh `categoryGroups.collect`), `onTouch(ev)` (uỷ quyền `LibraryOverlayController.onTouch`, vẫn do `dispatchTouchEvent` của Activity gọi đến Task 9), `progress`, `isAnimatingOrDragging`, `closeIfOpen()` (cho `onNewIntent`). `isPagerSettledOnLastPage` gộp `!pagerScrolling && homePager.currentItem == itemCount - 1`, tạm trỏ về Activity, đổi sang `HomePagerController` ở Task 6; `DragHelper.currentSession` đọc thẳng.
Consumes: `LibraryOverlayController` (giữ nguyên chỗ cũ), `HomeScreenState`, `HomeBackController`.
- [x] **Step 1:** Chuyển `libraryController` (by lazy), `setupLibrary`, `renderLibrary`, `renderCategoryPanel`, `canOpenLibrary`, `applyLibraryProgress`, `lastCategoryGroups`, `libraryRendered`, `LIBRARY_FLING_DP_PER_SEC`. Hai callback Back bật qua `back.setLibraryOpen/setCategoryGroupOpen`.
- [x] **Step 2:** Giữ nguyên D14 (đóng Library khi edit mode/kéo/menu/thư mục/hỏi hình nền bật), "đang kéo/animate thì để yên", render đầu sau recreate không animation.
- [x] **Step 3:** Build, commit `refactor: extract AppLibraryController from HomeActivity`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S8, S6(e), S12 (recreate khi Library và panel nhóm mở), S9 phần "vuốt xuống không mở tìm kiếm khi Library đang kéo".

### Task 4: `DockController` + `RecreateGuard` (hook H5 một nửa, H6)
**Bắt buộc kiểm tay:** không (chạm kính nên chạy thêm S11 trên Live 4; kiểm chéo Oppo A3x nếu điều phối thấy cần).
**Files:** Tạo `controller/DockController.kt`, `controller/RecreateGuard.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `DockController(binding.dockView, binding.backdropImage, viewModel, scope, glassSurfaceFactory, resources, onIconLongPress: (IconPress) -> Unit)`; `setup()` (thay `setupDock`), `render(state)` (các dòng dock trong `render()`: `lastDockItems`, `lastDraggingItemId`, `updateDockCells`, `setEditMode`), `updateDockCells()`, `onBackdropChanged(change: BackdropChange)` (thay hai dòng `setBackdropSource` + `pulseGlassRefresh`: `setBackdropSource` khi `LOADED`/`REPLACED`, pulse chỉ khi `REPLACED`), `suspend fun glassOverrideChanged(): Boolean`. `RecreateGuard(scope, recreate: () -> Unit)` với `add(check: suspend () -> Boolean)` và `onResume()`.
Consumes: `GlassSurfaceFactory` (Activity vẫn `@Inject`, truyền tay), `BlurredBackdropCache`, `GridMetrics`, `HomeScreenState`.
- [x] **Step 1:** Chuyển `setupDock`, `updateDockCells`, `appliedGlassOverride`, `dockGlassCache`, `lastDockItems`, `lastDraggingItemId`. Listener `homePager.addOnLayoutChangeListener` (kích thước đổi → `updateDockCells`) vẫn do Activity nối.
- [x] **Step 2:** Giữ nguyên ngữ nghĩa `appliedGlassOverride: GlassLevelOverride?`: `null` đến khi coroutine đọc xong, gán NGAY sau khi đọc (M8a Task 4). `glassOverrideChanged()` = `applied != null && applied != viewModel.currentGlassLevelOverride()`.
- [x] **Step 3:** Tạo `RecreateGuard`; `onCreate` đăng ký `recreateGuard.add { dock.glassOverrideChanged() }`; `onResume` gọi `recreateGuard.onResume()` ở vị trí của `recreateIfGlassOverrideChanged()` cũ. Thêm khối "Dòng nối cho AN/G2b" (comment trống) cuối phần nối controller.
- [x] **Step 4:** Build, commit `refactor: extract DockController and RecreateGuard from HomeActivity`, push, Nhật ký.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — S15, S11 phần kính dock (Live 4 đang FORCE_HIGH), S12 (đổi mức kính trong Cài đặt rồi về Home: `recreate()` đúng MỘT lần, không lặp; mở Home lần đầu không `recreate()` thừa), S13 (Cells dock co đúng khi split-screen).

### Task 5: `BackdropController` (hook H5)
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/BackdropController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `BackdropController(binding.backdropImage, binding.homeRoot, viewModel, activity)`; `render(state)` (phần đầu `render()`: ảnh nền và hộp thoại), `onResume()` (thay `viewModel.reloadBackdropIfChanged(...)`), `onDestroy()` (đóng `wallpaperDialog` như cũ), `addBackdropListener(BackdropListener)` với `enum class BackdropChange { LOADED, REPLACED, CLEARED }`, `fun interface BackdropListener { fun onBackdropChanged(change: BackdropChange) }`; `internal fun WallpaperTarget.labelRes()` (dùng chung với `handleEvent`).
Consumes: `DockController.onBackdropChanged` đăng ký qua `addBackdropListener` trong `onCreate`.
- [x] **Step 1:** Chuyển `shownBackdrop`, đoạn ảnh nền của `render()`, `showWallpaperDialog`, `wallpaperDialog`, `labelRes`. Hành vi cũ giữ nguyên: `LOADED` = `shownBackdrop == null`, `REPLACED` = `shownBackdrop != null`, `CLEARED` = nhánh `else if (shownBackdrop != null)`.
- [x] **Step 2:** `onResume()` của Activity: `recreateGuard.onResume()` → `backdrop.onResume()` → `refreshDefaultLauncher()`.
- [x] **Step 3:** Build, commit `refactor: extract BackdropController from HomeActivity`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S11, S1. **Lỗi đã biết "kính không vẽ lại sau đổi ảnh" phải còn y nguyên** (so với ảnh chụp baseline; không sửa ở R1).

> Task 5 chưa phải mốc cho AN: hook H2 (vị trí ô thư mục) còn cần `HomePagerController` ở Task 6.

### Task 6: `HomePagerController` (pager, lật mép, pill; hook H2) và đóng R1a
**Bắt buộc kiểm tay:** không (Task nhiều cử chỉ nhất; điều phối chạy S1–S18 bằng `adb` khi R1a qua review, D9).
**Files:** Tạo `controller/HomePagerController.kt`. Sửa `HomeActivity.kt`. Tạo `docs/knowledgements/R1-home-controllers.md` (phần R1a).
**Interfaces:**
Produces: `HomePagerController(binding, viewModel, scope, screen, onIconLongPress: (IconPress) -> Unit)`; `setup()` (thay `setupHomePager`; `onFolderClick = viewModel::openFolder` giữ nguyên chữ ký, đặt riêng một dòng để AN thay), `render(state)` (phần `displayPages` + `submitList` + `setPageCount` + visibility lưới/pill/dock + `setEditMode` cho adapter), `onDragEdge(direction)`, `requestPillMode()`, `isSettledOnLastRealPage`, `isScrolling`, `onStop()`/`onDestroy()` (`pagerFlip.cancel()`; `searchPill.removeCallbacks(pillToSearch)`). Gọi `binding.dockView.setGlassInvalidationSuppressed(...)` trực tiếp như code cũ.
Consumes: `HomePagerAdapter`, `PagerFlipAnimator`, `TunedPagerSnapHelper`, `PageMotion`, `DragHelper.refresh()`.
- [x] **Step 1:** Chuyển nguyên `setupHomePager` (tắt `itemAnimator`, `clipChildren=false` hai cấp, `onFlingListener=null` trước khi gắn `TunedPagerSnapHelper`, `OnPageChangeCallback` ba hàm), `pendingFlipToNewPage`, `lastPagerIdleMs`, `pagerScrolling`, `pillWanted`, `pillToSearch`.
- [x] **Step 2:** `isPagerSettledOnLastPage` của `AppLibraryController` trỏ sang `HomePagerController`; `DragHelper.edgeListener` vẫn do Activity gán (`::onDragEdge` → `pager.onDragEdge`) đến Task 9.
- [x] **Step 3:** Giữ nguyên thứ tự trong callback `submitList`: lật sang trang tạm nếu `pendingFlipToNewPage && ghostPage`, nếu không và không đang lật thì `setCurrentItem(savedPage.coerceIn(...), false)`.
- [x] **Step 4:** Build, commit `refactor: extract HomePagerController from HomeActivity`, push, Nhật ký.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — S2, S5, S4 (kéo về đúng ô cũ không mất icon), S17, S18 (so với baseline đo 3 lần).
- [x] **Step 6: Đóng R1a** (đã: knowledgement phần R1a, rebase lên `main`, `git diff` so khớp với `76c7032`; còn: Review Focus, chờ điều phối chạy S1–S18, chuyển PR sang Ready) — viết knowledgement phần R1a (sơ đồ `sequenceDiagram` cho `render()` và hook AN/G2b; Task 11 bổ sung R1b); rebase lên `main`, build xanh; điền Review Focus 1–8, 10–12; mục "Đề xuất cập nhật tài liệu chung"; chuyển PR #5 sang Ready; báo điều phối chạy S1–S18.

## Phần R1b — PR riêng (Task 7–11), mở sau khi R1a merge

Nhánh `refactor/r1b-home-controllers` từ `main` mới (điều phối tạo worktree). Dùng chung plan này; Nhật ký ghi tiếp. AN và G2b có thể đã thêm dòng vào khối "Dòng nối cho AN/G2b": R1b không sửa khối đó.

### Task 7: `IconMenuController`
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/IconMenuController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `IconMenuController(binding, viewModel, screen, back)`; `setup()` (listener `iconMenuScrim`, `iconActionMenu.onAction/onShortcut`), `render(menu: IconMenuState?)` (thay `renderIconMenu`), `onIconLongPress(press)`, `onTouchPrelude(ev)` (thân bước `pendingMenuDrag` đầu `dispatchTouchEvent`).
Consumes: `DragHelper.currentSession`, `IconGridView.beginDragForItem`, `DockView.beginDragForItem`, `HomeBackController.setIconMenuOpen`. Các lambda `onIconLongPress` của Folder, Dock, Pager đổi sang `iconMenu::onIconLongPress` (một dòng mỗi nơi trong `onCreate`).
- [x] **Step 1:** Chuyển `iconMenuSnapshot`, `iconMenuShown`, `iconMenuPositioned`, `PendingMenuDrag`, `startDragFromMenu`, listener scrim (kể cả `scrimDownX/Y`), logic `onIcon && menu.container <= 0L`.
- [x] **Step 2:** Giữ nguyên "Đường B": menu đóng vì icon vừa được kéo đi thì KHÔNG `endDrag()` khi `DragHelper.currentSession != null`.
- [x] **Step 3:** Build, commit `refactor: extract IconMenuController from HomeActivity`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S14, S6(d)(g), S12 (recreate khi menu mở: menu đóng, không crash).

### Task 8: `SearchOverlayController`
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/SearchOverlayController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `SearchOverlayController(binding, searchViewModel, screen, back, isLibraryIdle: () -> Boolean, statusBarTopPx: () -> Int)`; `setup()` (adapter kết quả, `onQueryChanged`, `onDismissRequested`, `searchPill.onSearchClick` với điều kiện cũ), `render(state: SearchUiState)`, `handleEvent(SearchEvent)`, `onTouch(ev, dispatchCancel: (MotionEvent) -> Unit): Boolean` (thân `dispatchWithSearchSwipe`), `closeIfOpen()`. Vùng bắt đầu vuốt đọc `binding.dockView.getLocationOnScreen` trực tiếp.
Consumes: `SearchSwipeDetector`, `HomeScreenState`.
- [x] **Step 1:** Chuyển `searchSwipeDetector`, `searchTouchSwallowed`, `searchStartZone`, `EMPTY_ZONE`, `SEARCH_SWIPE_TRIGGER_DP`, `SEARCH_START_TOP_OFFSET_DP`. `libraryView.onSearchClick` vẫn ở `AppLibraryController.setup()` (gọi `searchOpen(true)`).
- [x] **Step 2:** Build, commit `refactor: extract SearchOverlayController from HomeActivity`, push, Nhật ký.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — S9, S6(e)(f).

### Task 9: `HomeTouchRouter` (thứ tự cử chỉ + kéo-thả)
**Bắt buộc kiểm tay:** không theo mục 4.2 (không có cử chỉ mới, chỉ chuyển chỗ); **rủi ro cao nhất của R1**: điều phối chạy S4, S5, S8, S9, S14, S17, S18 bằng `adb` ngay khi Task qua review (D9).
**Files:** Tạo `controller/HomeTouchRouter.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `HomeTouchRouter(binding, iconMenu, library, search, pager)` với `fun dispatch(ev: MotionEvent, dispatchToViewTree: (MotionEvent) -> Boolean): Boolean`; `setup()` (thay `DragHelper.attachDragVisual`, `edgeListener = pager::onDragEdge`, listener `homeRoot` đặt `edgeLeftLimit/edgeRightLimit`), `onDestroy()` (`DragHelper.detachDragVisual()`). `HomeActivity.dispatchTouchEvent(ev) = touchRouter.dispatch(ev) { super.dispatchTouchEvent(it) }`.
Consumes: `IconMenuController.onTouchPrelude`, `AppLibraryController.onTouch`, `SearchOverlayController.onTouch`, `HomePagerController.onDragEdge`, `DragHelper`.
- [x] **Step 1:** Chuyển thân `dispatchTouchEvent` nguyên văn, **đúng thứ tự** (Review Focus 3), `dragTouchTakenOver`, `DRAG_EDGE_ZONE_DP`.
- [x] **Step 2:** Mọi chỗ gửi `ACTION_CANCEL` giả (tiếp quản Library, vuốt tìm kiếm, kéo-thả) vẫn đi qua `dispatchToViewTree` và `recycle()` đúng như cũ.
- [x] **Step 3:** Build, commit `refactor: extract HomeTouchRouter from HomeActivity`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — S4, S5, S8, S9, S14, S17 (bấm Home giữa lúc kéo rồi mở lại: phiên kéo cũ bị huỷ ở chạm kế, không kẹt), S18.

### Task 10: `SystemBarsController`
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `controller/SystemBarsController.kt`. Sửa `HomeActivity.kt`.
**Interfaces:**
Produces: `SystemBarsController(binding, window, resources)` với `setup()` (thay `applySystemBarInsets` + `applySystemBarStyling`), `render(editMode: Boolean)` (thay `applyStatusBarForEditMode`), `statusBarTopPx`, `editToolbarClearancePx()`.
Consumes: `GridMetrics.PADDING_DP`, dimens `home_edit_pill_*`. Nút Xong/Cài đặt, `editToolbar.isVisible` và `back.setEditMode` ở lại `render()` của Activity.
- [x] **Step 1:** Chuyển nguyên `applySystemBarInsets` (margin của `home_content`, KHÔNG padding root, B2; inset bỏ qua visibility của TL1; `topReserve`; chỉ gán khi đổi; `libraryView.setPadding(0, bars.top, 0, bars.bottom)`; trả `insets` nguyên vẹn) và `applySystemBarStyling` (nhánh `SDK_INT >= Q`, đảo dấu theo night mode), `applyStatusBarForEditMode` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, `hide/show statusBars`).
- [x] **Step 2:** Build, commit `refactor: extract SystemBarsController from HomeActivity`, push, Nhật ký.
- [ ] **Step 3: Kịch bản kiểm (Live 4)** — S3, S10 (lưới KHÔNG dịch: bounds icon hàng đầu bằng nhau, đo bằng `uiautomator dump`), S12 (màu icon thanh hệ thống đúng sau đổi theme), S13 (thanh điều hướng đáy, split-screen).

### Task 11: Dọn `HomeActivity` + knowledgement phần R1b
**Bắt buộc kiểm tay:** không.
**Files:** Sửa `HomeActivity.kt`, `docs/knowledgements/R1-home-controllers.md`.
- [x] **Step 1:** `HomeActivity` chỉ còn nối + `render()` + `handleEvent` + móc vòng đời; xoá import thừa, comment mồ côi; đo số dòng thật của từng controller và ghi vào Nhật ký (đối chiếu bảng ước lượng); không đổi hành vi.
- [x] **Step 2:** Bổ sung knowledgement: `sequenceDiagram` cho `dispatchTouchEvent` (router → menu → kéo → Library → tìm kiếm), `stateDiagram-v2` các lớp Back, bảng "controller nào giữ gì".
- [x] **Step 3:** Build, commit `refactor: slim HomeActivity to controller wiring` và `docs: complete R1 knowledgement`, push, Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — chạy lại toàn bộ danh mục (bài kiểm cuối).

---


## Danh mục hồi quy (dùng cho mọi Task và cho bài kiểm cuối)

**Chuẩn bị (Live 4, Android 11):** ít nhất 2 trang icon, một thư mục ≥ 3 app, dock đủ 4 app, một ảnh nền, Library có vài nhóm. **Baseline chụp MỘT lần từ `origin/main@76c7032`** (mục "Baseline cần chụp" ngay dưới), rồi so từng Task với baseline. Mở app: `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity`; `adb shell am force-stop gambi.global.os26launcher` giữa các ca cần khởi động lạnh.

| # | Ca | Cách làm / điều phải thấy |
|---|---|---|
| S1 | Khởi động | Lạnh: vòng tải rồi lưới, dock, pill ô tìm kiếm; không nháy cỡ lưới |
| S2 | Pager | Vuốt fling và thả chậm giữa trang; chấm trang đúng; pill về ô tìm kiếm ~600 ms sau khi dừng |
| S3 | Edit mode | Nhấn giữ icon → nhấn giữ lại icon nổi (hoặc nhấn giữ thư mục) vào edit mode; pill Cài đặt/Xong nổi, icon rung, bấm chỗ trống hoặc Xong thoát; Cài đặt mở màn Cài đặt |
| S4 | Kéo-thả | Kéo icon sang ô khác cùng trang; vào/ra dock; thả lên icon khác tạo thư mục; kéo về đúng ô cũ không mất icon; thả ra ngoài vùng hợp lệ trở về chỗ cũ |
| S5 | Lật mép + trang tạm | Giữ icon sát mép phải/trái 24 dp lật trang, không lật dồn; ở trang thật cuối giữ mép phải tạo trang tạm rồi lật sang; kéo ngược bỏ trang tạm; thả lên trang tạm tạo trang thật; huỷ kéo không để lại trang |
| S6 | Thứ tự Back | (a) Home trống: Back không thoát app. (b) Edit mode → thoát. (c) Thư mục mở (đã sửa tên) → đóng và lưu tên. (d) Menu icon → đóng. (e) Library → đóng; Library + panel nhóm → đóng panel rồi Library; Library + tìm kiếm (từ thanh tìm kiếm Library) → đóng tìm kiếm rồi Library. (f) Tìm kiếm từ Home → đóng. (g) Menu mở từ icon trong thư mục → đóng menu rồi thư mục |
| S7 | Thư mục | Mở, đổi tên, bấm app trong panel (mở app + đóng panel), dấu "−" ẩn app, thư mục còn 1 app thì giải tán và panel đóng, nhấn Home khi mở (lệnh `am start -a MAIN -c HOME -n …`) đóng panel |
| S8 | Library | Mở bằng vuốt ngang từ trang thật cuối; không mở ở trang giữa/edit mode/trang tạm/đang lướt; vuốt đóng; fling nhanh; panel nhóm; bấm app trong panel; thanh tìm kiếm Library mở tìm kiếm `showAll` |
| S9 | Tìm kiếm | Vuốt xuống từ vùng hợp lệ (cách thanh trạng thái ≥ 48 dp, trên dock); bấm pill; không mở khi edit mode/menu/thư mục/Library/trang tạm; sau khi mở, phần chuỗi chạm còn lại không lật trang; gõ + bấm kết quả mở app; app mở lỗi chỉ Toast, lớp vẫn mở |
| S10 | Ẩn thanh trạng thái | Vào edit mode: thanh trạng thái ẩn, lưới không dịch (bounds icon hàng đầu y hệt trước đó); ra: thanh hiện; vuốt từ mép trên khi edit hiện thanh tạm rồi tự ẩn |
| S11 | Đổi ảnh nền | Cài đặt → đổi ảnh nền → về Home: ảnh mới; hộp thoại hỏi áp wallpaper (xoá cờ DataStore để hỏi lại): chọn đích/bỏ qua/chạm ngoài/Back đều đóng và báo `onWallpaperPromptDismissed`, Toast kết quả. Kính dock không vẽ lại sau đổi ảnh là lỗi ĐÃ BIẾT, phải còn y baseline |
| S12 | `recreate()` | `adb shell cmd uimode night yes/no` khi: Home thường, edit mode, thư mục mở, Library mở (có/không panel nhóm), tìm kiếm mở, menu icon mở (đóng, không crash); đổi mức kính trong Cài đặt rồi về Home: recreate đúng một lần, không lặp |
| S13 | Split-screen | Vào/ra split-screen (từ Recents): Cells dock và icon lưới co/giãn đúng, vùng vuốt tìm kiếm đúng, mép lật trang đúng, Library đúng bề rộng |
| S14 | Menu nhấn giữ | Nhấn giữ icon (lưới, dock): scrim, icon nổi, menu shortcut; icon hàng cuối/dock thì menu lật lên; chạm scrim đóng; nhấn giữ icon nổi vào edit mode; giữ tay rồi di vượt slop kéo luôn |
| S15 | Dock | Mở app từ dock; kéo vào/ra dock; dock có edit mode; đổi cỡ lưới ở Cài đặt, icon dock cùng cỡ icon lưới, không nhãn |
| S16 | Trạng thái lưới | Vòng tải → lưới; (nếu có thể) trạng thái rỗng |
| S17 | Vòng đời | Bấm Home (sang launcher gốc) giữa lúc kéo rồi mở lại: phiên kéo cũ bị huỷ ở chạm kế, không kẹt; mở app rồi quay lại giữa lúc lật mép: lật huỷ ở `onStop` |
| S18 | Hiệu năng (cảm quan) | `adb shell dumpsys gfxinfo gambi.global.os26launcher reset` rồi vuốt trang 10 lần và kéo icon 5 lần, đọc Janky frames; không tệ hơn ngưỡng đo từ 3 lần baseline |

Máy: **Vsmart Live 4** (Android 11); kính trên Live 4 đang ở mức FORCE_HIGH. **Oppo A3x** chỉ khi điều phối cần kiểm chéo kính (Task 4/5).

## Baseline cần chụp từ `origin/main@76c7032` (điều phối chụp cùng agent)

Agent không cài APK (mục 11) nên điều phối chụp; đây là danh sách chính xác agent cần để so sánh:

1. **APK baseline:** build debug từ `main` tại `76c7032` (chưa có R1/AN/G2b), lưu `baseline-76c7032.apk`, ghi `git rev-parse --short HEAD`. Cài mọi bản bằng `adb install -r` để Room, DataStore và ảnh nền giữ nguyên giữa baseline và nhánh (cùng khóa debug, không đổi schema). Cùng package nên chạy tuần tự, không song song.
2. **Dữ liệu cố định:** 2–3 trang icon, một thư mục ≥ 3 app (ghi tên), dock 4 app, một ảnh nền, cỡ lưới mặc định, mức kính hiện tại (FORCE_HIGH). Sao lưu `databases/launcher.db` và file DataStore bằng `run-as` để khôi phục khi hỏng. Ghi `wm size`, `wm density`, font scale, night mode, `getprop ro.build.version.release`.
3. **Mỗi ca S1–S18:** ảnh chụp `adb exec-out screencap -p > S<n>-<bước>.png` ở từng bước của ca. Thêm: S3, S10, S14, S15 → `adb shell uiautomator dump` (so `bounds` icon hàng đầu, dock, pill, toolbar). S6 → sau mỗi Back chụp màn hình và `adb shell dumpsys window | findstr mCurrentFocus` (app còn foreground không). S12 → số lần dựng lại từ `adb logcat -b events -d` (sự kiện `wm_create_activity`/`wm_destroy_activity`, **chưa kiểm trên Live 4**).
4. **S18:** `adb shell dumpsys gfxinfo gambi.global.os26launcher reset`, rồi 10 lần `adb shell input swipe 900 1200 150 1200 250` xen kẽ chiều ngược và 5 lần kéo icon; ghi `Janky frames` và percentile 50/90/95/99. **Chạy baseline 3 lần** để biết nhiễu thật; ngưỡng "không tệ hơn" = giá trị xấu nhất trong 3 lần baseline.
5. **Kéo-thả bằng lệnh:** cần biết `adb shell input` có `draganddrop` không (chạy `adb shell input` đọc usage). Không có thì S4, S5, S14 (phần kéo) do người thao tác tay; điều phối báo trước.
6. **Lỗi đã biết ở S11:** chụp dock ngay sau khi đổi ảnh nền, trước khi chạm hay lật trang, làm chuẩn "lỗi còn y nguyên".
7. **Log:** sau mỗi ca `adb logcat -d -b crash` (phải rỗng) và `adb logcat -d *:E | findstr os26launcher` để thấy lỗi mới phát sinh.
8. **Lệnh chưa kiểm:** nhấn Home giả lập (S7), `adb shell cmd uimode night yes|no` (S12): ghi vào Nhật ký lệnh nào chạy được trên Live 4.

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Chạy S1–S18 theo thứ tự thao tác tự nhiên: S1 → S2 → S14 → S3 → S4 → S5 → S10 → S7 → S8 → S9 → S6 (a–g) → S15 → S11 → S12 → S13 → S17 → S18. **Mốc chạy bằng `adb` của điều phối (D9):** toàn bộ S1–S18 sau R1a (Task 6), S4/S5/S8/S9/S14/S17/S18 sau Task 9, và toàn bộ ở bài kiểm cuối R1b. So từng ca với baseline `origin/main@76c7032`. Chỉ chấp nhận khác biệt khi chính là lỗi đã biết ghi ở S11.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-04 | — | Viết plan | `0758938` (`docs: add R1 plan`) | Chờ điều phối rà; PR #5 mở dạng draft |
| 2026-11-04 | — | Chỉnh plan sau vòng rà 1/5: R1a = Task 1–6 / R1b = Task 7–11; thêm `RecreateGuard` (hook G2b) vào Task 4; bỏ `EditModeController` và các `Host` chỉ chuyển tiếp; giải thích số dòng; liệt kê dòng nối cho AN/G2b; thêm mục Baseline | `docs: revise R1 plan after review` | Plan vòng 1/5. Thiết kế controller đã duyệt; `activity_home.xml` nhả cho AN |
| 2026-11-04 | — | Chủ dự án duyệt plan (gồm chia hai PR); thêm hợp đồng hook với AN/G2b (listener ảnh nền 3 loại sự kiện, `libraryView.onAppClick` ở `AppLibraryController.setup()`, hợp đồng view) | `docs: approve R1 plan and record AN/G2b hook contract` | Bắt đầu code R1a. Build: chạy `gradlew.bat --stop` sau mỗi build |
| 2026-11-04 | 1 | `HomeScreenState` + `HomeBackController`: 7 đăng ký Back giữ thứ tự cũ; `lastState`/`lastSearchState` → `screen.ui`/`screen.search` | `95aba92` | Build xanh. Vòng 1/5. Ghi chú: các `gradlew --stop` của nhánh khác giết build đang chạy (lỗi "stop command received"), chạy lại là xanh |
| 2026-11-04 | 2 | `FolderPanelController` (`setup`, `render`, `closeFolder`); Back và `onNewIntent` đều qua `folder.closeFolder()` | `ab17687` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 3 | `AppLibraryController` (giữ `LibraryOverlayController`, `setup`, `render`, `onCategoryGroups`, `closeIfOpen`, `onTouch`); `isPagerSettledOnLastPage` tạm là lambda ở Activity (Task 6 đổi nguồn) | `a0747b5` | Build xanh. Vòng 1/5. `HomeActivity` 962 → khoảng 800 dòng |
| 2026-11-04 | 3 (tick) | Điều phối review Task 1–3 đạt (thứ tự đăng ký Back khớp bản gốc) | — | Dùng `-Dorg.gradle.daemon.idletimeout=120000`, không `--stop` nữa |
| 2026-11-04 | 4 | `DockController` + `RecreateGuard` (`recreateGuard.add(dock::glassOverrideChanged)`), `BackdropChange`/`BackdropListener` khai báo sớm; khối comment "Dòng nối cho AN/G2b" trong `onCreate`. `dockView.setEditMode` và visibility dock ở lại `render()` của Activity (giữ thứ tự cũ), không vào `DockController.render` | `0576629` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 5 | `BackdropController` (ảnh nền, hộp thoại wallpaper, `onResume`, `onDestroy`, `labelRes()`), listener 3 loại sự kiện `LOADED/REPLACED/CLEARED`; `DockController` đăng ký | `8589913` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 6 | `HomePagerController`: `setup`, `renderPages`, `renderGridStatus`, `setEditMode`, `requestPillMode`, `onDragEdge`, `cancelFlip`, `onDestroy`, `isSettledOnLastPage`. **Lệch nhẹ so với plan:** `render(state)` tách thành 4 hàm gọi ở đúng chỗ cũ trong `render()` (giữa chúng còn `dock.render`, `back.setEditMode`) để giữ thứ tự; `onFolderClick = viewModel::openFolder` giữ nguyên | `9ca610f` | Build xanh. Vòng 1/5. `HomeActivity` 962 → 557 dòng; controller R1a tổng ~770 dòng (ước lượng ban đầu ~640) vì import/constructor/chú thích giữ nguyên văn. So khớp dòng với `76c7032` bằng script: không có dòng nào bị mất; khác biệt chỉ là tiền tố (xem knowledgement mục 6) |
| 2026-11-04 | — | Rebase lên `origin/main` (3aaad60, chỉ docs), push `--force-with-lease`; hash commit trong bảng này là hash SAU rebase | — | PR #5 vẫn draft |
| 2026-11-04 | R1a đóng | Điều phối chạy hồi quy trên Live 4, chủ dự án cho merge PR #5; Task 7–11 sang nhánh `refactor/r1b-home-controllers` | (docs) | Xong R1a |
| 2026-11-04 | R1b mở | Worktree `R1b-home-controllers`, nhánh `refactor/r1b-home-controllers` từ `main` 7030768; PR #7 (draft) | — | Khối "Dòng nối cho AN/G2b" không đụng |
| 2026-11-04 | 7 | `IconMenuController` (`setup`, `render`, `onIconLongPress`, `onTouchPrelude`, `startDragFromMenu`); `onIconLongPress` của pager/folder/dock nối sang `iconMenu::onIconLongPress` | `dcf126f` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 8 | `SearchOverlayController` (`setup`, `render`, `handleEvent`, `onTouch(ev, dispatchToViewTree)`, `closeIfOpen`); lambda `isLibraryIdle`, `statusBarTopPx` (tạm đọc từ Activity đến Task 10) | `c5b69db` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 9 | `HomeTouchRouter` (`dispatch`, `setup`, `onDestroy`); `HomeActivity.dispatchTouchEvent` còn một dòng. Thứ tự: `iconMenu.onTouchPrelude` → phiên kéo → `library.onTouch` → `search.onTouch` → `super`. Không đụng `DragHelper`/view của AN | `d3d2e76` | Build xanh. Vòng 1/5. So khớp dòng với `7030768` bằng script: chỉ khác tiền tố (`super.dispatchTouchEvent` → `dispatchToViewTree`, `binding`, tham số). Dòng "Dòng nối cho AN/G2b" giữ nguyên |
| 2026-11-04 | 9 (review) | Điều phối chạy hồi quy cử chỉ trên `d21bdb5` (đang chạy khi Task 10–11 làm tiếp) | — | Kết quả sẽ ghi vào bảng bằng chứng |
| 2026-11-04 | 10 | `SystemBarsController` (`setup`: insets rồi styling, `render(editMode)`, `statusBarTopPx`); `editToolbarClearancePx` đi theo. Nút Xong/Cài đặt và `editToolbar.isVisible` ở lại `render()` của Activity | `f8bd5a2` | Build xanh. Vòng 1/5 |
| 2026-11-04 | 11 | Dọn `HomeActivity`: bỏ 11 import thừa, comment mồ côi, dòng trống; không đụng khối "Dòng nối cho AN/G2b". Số dòng thật (`wc -l`): `HomeActivity` 203; `HomeBackController` 103, `HomeScreenState` 13, `FolderPanelController` 57, `AppLibraryController` 142, `DockController` 111, `RecreateGuard` 31, `BackdropController` 114, `HomePagerController` 199, `IconMenuController` 164, `SearchOverlayController` 121, `HomeTouchRouter` 94, `SystemBarsController` 120; tổng controller 1.269 (ước lượng 1.090: import/constructor/chú thích giữ nguyên văn). Knowledgement phần R1b | (xem commit) | Build xanh. Vòng 1/5. Không còn `HomeActivity` > 250 dòng (Review Focus 9 đạt) |
| 2026-11-04 | R1b đóng | Điều phối chạy hồi quy trên Live 4 | (docs) | Chờ merge PR #7 |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Hành vi so với `main` (R1a, Live 4, 2026-11-04) | Điều phối chạy 32 bước (khởi động, vuốt trang, menu, edit mode, thư mục, lớp tìm kiếm, Library, panel nhóm, thứ tự Back, intent Home, `recreate()` bằng đổi sáng/tối) trên `main` và nhánh, so ảnh từng bước | Đạt: giống từng điểm ảnh, trừ icon đang rung và bong bóng chat của hệ thống; không crash |
| 2 | Kéo-thả | Dock ra lưới và về, tạo thư mục bằng kéo dài, lật mép qua trang rồi thả về chỗ cũ (0 điểm ảnh khác) | Đạt |
| 3 | Hiệu năng vuốt | Xen kẽ `main`/nhánh, khi máy ổn định: Janky 0.6–0.9% cả hai | Đạt |
| 4 | Một khung lạ | Một ảnh chụp ~3 giây sau đổi tối sang sáng: dock còn 2/4 app, không kính. 12 lần chụp lặp lại không tái hiện; R1 không đụng nguồn dữ liệu dock. Chưa chứng minh được nguyên nhân | Ghi nhận, theo dõi |
| 5 | Đổi ảnh nền, split-screen | Chủ dự án thử trên máy rồi cho merge (2026-11-04) | Đạt |
| 6 | Chưa chạy ở R1a | S16 trạng thái rỗng, S17 vòng đời giữa lúc kéo, TalkBack; kiểm ở bài cuối của R1b | Chưa kiểm |
| 7 | R1b: thứ tự `dispatchTouchEvent`, Back, `onDestroy` (Review Focus 1, 3, 4) | So khớp dòng với `main@7030768`: chỉ khác tiền tố; thứ tự `HomeTouchRouter.dispatch` = thân cũ; `onDestroy`: huỷ lật → hộp thoại → `touchRouter.onDestroy` → hẹn giờ pill | Đạt (code); chờ hồi quy trên máy |
| 8 | R1b: kích thước (Review Focus 9) | `HomeActivity` 203 dòng; khối "Dòng nối cho AN/G2b" nguyên vẹn | Đạt |
| 9 | R1b so với `main` (Live 4, `e623952`, 2026-11-04) | 44 bước, cùng trạng thái bố cục: 41 bước giống từng điểm ảnh; 3 bước khác đều là lỗi dock 0x0 có từ trước R1 | Đạt, không crash |
| 10 | Vuốt tìm kiếm, Home giữa lúc kéo (S17), lật mép, kéo về chỗ cũ; Library không mở khi edit mode | Nằm trong 44 bước; Library kiểm riêng 2 lần | Đạt |
| 11 | Nhấn giữ mở menu | Giữ có rung nhẹ như ngón tay thật: ít nhất 6/6 mở menu. Giữ đứng yên qua `adb` không đáng tin, `main` cũng 0/8 | Đạt |
| 12 | Lỗi dock 0x0 (dòng 4) | `DockView.onMeasure` đo 0x0 khi `cells == null`; bản trước R1 cũng lỗi (lần dựng lại 23). Không do R1; sửa ở AN Task 9f | Đóng |
| 13 | Chưa chạy | S16 trạng thái rỗng, TalkBack | Chưa kiểm |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `project_structure.md`: thêm `ui/home/controller/` vào cây thư mục cùng danh sách 12 file (kể cả `RecreateGuard`), đổi dòng `HomeActivity.kt`; bảng mốc ghi R1a/R1b xong.
- **Sửa số dòng sai:** `MultiAgent-Workflow.md` (mục 2.1, 5.2, 9) ghi "922 dòng" và `AGENTS.md` (Demo polish) ghi "~940"; số đo thật trước R1 là **962 dòng** (`wc -l` ở `76c7032`). Đổi thành số thật sau khi tách, hoặc bỏ số.
- `AGENTS.md`: sửa nợ "Tách `HomeActivity`" ở mục "Demo polish" thành đã xong; các câu trỏ `HomeActivity.setupHomePager`/`HomeActivity.updateDockCells`/`dispatchTouchEvent`/`pendingMenuDrag` đổi sang tên controller mới (mục M7 Task 5, M7 Task 10, Demo polish #2–#4, M8b Task 2, M8a Task 4); bài học mới xem "Bài học cho AGENTS.md" trong knowledgement.
- `MultiAgent-Workflow.md`: mục 10 thêm dòng R1b; mục 5.2 bỏ `HomeActivity.kt` khỏi danh sách "độc quyền" hoặc hạ xuống "dùng chung có kiểm soát" (còn ~220 dòng), thêm `ui/home/controller/*` vào vùng nên khai; mục 9 và 10 cập nhật R1.
- `Launcher-Features.md`, `room-schema.md`: không đổi.
