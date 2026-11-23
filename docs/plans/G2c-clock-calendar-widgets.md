# G2c — Widget tự vẽ: đồng hồ và lịch trên lưới Home (TT#13) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ ở Task 6, xem "Khóa"). Lệnh viết cho CMD/PowerShell. **Không viết code trước khi plan được duyệt, và GP phải merge trước khi code (rebase lên GP).**

**Nhánh:** `feature/g2c-clock-calendar-widgets` · **Worktree:** `E:\SourceTree\IosLauncher-wt\G2c-clock-calendar-widgets` · **PR:** #12 (draft) · **Bắt đầu:** 2026-11-05 · **Plan duyệt:** 2026-11-05 (chủ dự án)
**Trạng thái:** Mã và kiểm tay xong tại `3e82320` (chủ dự án đạt hai lần kiểm tay; điều phối đo và kiểm kịch bản). Còn: rebase cuối và merge (chủ dự án, điều phối). Chưa chạy: split-screen, nút nguồn, cuộc gọi.
**Khóa:** **khóa schema, dạng nhẹ** (đặt nghĩa cho `itemType = 4`; KHÔNG đổi cột, KHÔNG tăng `version`, KHÔNG có `Migration`, `app/schemas/*/1.json` không đổi — xem D1) · cần Oppo ở Task 6 (trùng với GS, xem câu hỏi Q6) · file độc quyền cần giữ: `HomeViewModel.kt`, `HomeUiState.kt` (dự kiến không sửa), `LayoutReconciler.kt`, `data/layout/db/*`, `core/ui/DragHelper.kt`; xin thêm một đoạn hẹp ở `HomeActivity.kt` + `activity_home.xml` (xem "Vùng file").

**Goal:** Người dùng thêm được widget **Đồng hồ** và **Lịch** tự vẽ lên các trang Home từ edit mode (nút "+" mở bảng chọn), kéo di chuyển như icon (icon xung quanh tự nhường chỗ), xóa bằng dấu "−", và chúng đổi đúng mỗi phút / khi đổi múi giờ / ngày / ngôn ngữ / 12-24 giờ. Bốn biến thể đầu: Đồng hồ 2×2 (kim), Đồng hồ 4×2 (số), Lịch 2×2 (thứ + ngày), Lịch 4×2 (ngày + lưới tháng nhỏ). Không có thời tiết (đóng băng), không có sự kiện lịch (cần quyền), không AppWidgetHost (G3c).

**Architecture:** (1) Một hàng `launcher_items` mới `itemType = 4` (`ITEM_TYPE_BUILTIN_WIDGET`): `className` = mã loại (`"clock"`, `"calendar"`), `spanX/spanY` = kích thước; không cột mới. (2) Mọi planner của lưới (`GridInsertPlanner`, `GridRemovePlanner`, `GridReflowPlanner`) được tổng quát hóa theo **dãy ô tự do S** (các ô không bị widget phủ, theo thứ tự đọc): icon vẫn chèn/dồn như cũ nhưng đi dọc S; widget là vật cản cố định. Với trang không có widget, S = mọi ô nên kết quả **phải trùng từng bit** với planner hiện tại (chứng minh bằng harness tạm ở Task 1). (3) Widget được vẽ bởi **chính `IconCellView`** ở chế độ thứ ba (`bindWidget`), nội dung do các `WidgetRenderer` thuần Canvas trong `ui/widget/` vẽ, nên mọi cơ chế đã tinh chỉnh ở AN (FLIP theo `itemId`, rung, ảnh chụp lúc kéo, spring độ dời, dấu "−") dùng lại nguyên. (4) Cập nhật bằng một `WidgetTicker` dùng chung, chỉ chạy khi có widget nhìn thấy, canh theo ranh giới phút. (5) Nền widget là kính tại chỗ (`BackdropBlurDrawable`, vai trò `FOLDER_CELL` có sẵn, màu tự dựng bằng `GlassStyle(...)`), **không sửa `core/glass`**; có hay không kính do phép đo ở Task 6 quyết.

**Tech Stack:** View/XML, `java.time` (minSdk 26 có sẵn), `android.text.format.DateFormat`. Không thêm dependency.

**Spec:** TT#13 trong `Launcher-Features.md` ("Widget tự vẽ: đồng hồ, lịch", không gồm thời tiết) + `room-schema.md` mục 3 (`itemType`, `spanX/spanY`) + `MultiAgent-Workflow.md` mục 9 dòng G2c. Tài liệu liên quan đã đọc: `plans/AN-polish-animation.md` (kéo-thả, hover, đẩy-dồn 500 ms, đẩy-dồn dính ở ô neo, FLIP, ảnh bay hạ cánh, kính hover), `plans/GP-gap-long-press.md` (nhánh `fix/gp-gap-long-press`, bản chưa merge).

---

## Hiện trạng đọc từ code (2026-11-05, `b106797`) — mọi đường bố cục gặp item nhiều ô

Đây là bảng gốc để plan không phải đoán. Cột "Hậu quả nếu thêm widget mà không sửa" đều là đọc code, chưa chạy.

| # | Chỗ | Hiện làm gì với `spanX/spanY` | Hậu quả nếu có hàng widget mà không sửa | Task sửa |
|---|---|---|---|---|
| 1 | `LauncherItemEntity`, `LauncherDatabase` (v1) | Có `itemType` Int, `spanX/spanY`, `className`, `appWidgetId`; `ITEM_TYPE_WIDGET = 3` đã dành cho AppWidgetHost (`room-schema.md`: bắt buộc `packageName` + `appWidgetId`) | — (xem D1) | 3 |
| 2 | `LayoutRepositoryImpl.assemble` | `else -> Unit` cho loại lạ: hàng widget bị **bỏ qua im lặng**, không vẽ | Widget lưu được mà không hiện | 3 |
| 3 | `GridInsertPlanner.plan` | Chỉ 1×1: `Slot(itemId, x, y)` không có span; chỉ số đọc = `y*columns + x`. **KDoc nói "dịch lên đúng 1 bậc" nhưng CODE dồn đuôi liên tiếp** (`r = targetIndex + 1 + i`: occupant và mọi icon sau xếp sát nhau từ `targetIndex+1`, khe sau ô đích mất) và `applyPreview` của `IconGridView` làm y hệt. Tối đa 1 icon tràn mỗi trang | Widget bị coi là 1 ô; icon chồng lên phần phủ của widget | 1, 2 |
| 4 | `GridRemovePlanner.plan` | Mọi icon sau icon bị gỡ lùi đúng 1 chỉ số đọc, **giữ khe** (khác chèn); chỉ nhận `columns` | Icon lùi vào ô widget phủ | 1 |
| 5 | `GridReflowPlanner.plan` + `LayoutReconciler.reflowInTransaction` | Planner trải phẳng, nén khe. Reconciler **thoát sớm, không làm gì** nếu có hàng `spanX>1 \|\| spanY>1` (`Log.w "multi-cell item present, reflow skipped"`) | Đổi cỡ lưới có widget → icon ngoài lưới còn nguyên, `ensureFitsGrid` lặp lại mỗi lần mở app | 2, 3 |
| 6 | `LayoutReconciler.ensureFitsGrid` | Chỉ kiểm `gridX >= columns \|\| gridY >= rows` (không cộng span) | Widget `x=1,spanX=4` ở lưới 5 cột, đổi sang 4 cột: không bị coi là ngoài lưới | 3 |
| 7 | `LayoutReconciler.deleteAndCompact` (gỡ app thật) | Nếu **bất kỳ** hàng HOME nào có span > 1 thì **bỏ dồn cho mọi trang** (`Log.w`), chỉ xóa trang cuối trống | Có một widget ở trang 1 là gỡ app ở trang 3 không dồn nữa | 3 |
| 8 | `LayoutReconciler.compactPageAfterRemoval` (ẩn app) | **Không có chốt**: dùng `GridRemovePlanner` với slot 1×1 | Widget bị "dồn" như icon | 1, 3 |
| 9 | `LayoutReconciler.placeNewInTransaction` | Tập ô đã chiếm **có tính span** (`for dy, dx`) | Đúng; không phải sửa | — |
| 10 | `LayoutReconciler.moveToHomeInTransaction` | Dựng `Slot` không span; không biết span của item đến từ dock/thư mục | Widget chèn thành 1×1 | 3 |
| 11 | `removePackages` / `catchUp` / `componentKeyOrNull` / `deleteAppsAndFixFolders` | Lọc `itemType == ITEM_TYPE_APP` | Đúng với loại 4; không phải sửa | — |
| 12 | `FolderPlanner.Participant`, `toParticipant()` | `isApp` + `isSingleCell`; `canCreate/canAdd` từ chối nếu không phải app 1×1 | Đúng: widget không phải đích gộp cũng không phải nguồn gộp; không phải sửa DB | — |
| 13 | `IconGridView` | (a) mỗi con = một item, **ô con thứ i = item thứ i**; (b) `onLayout` cho mọi ô đúng `cellWidth × cellHeight`; (c) `cellAt` trả ô dưới ngón tay; `desiredHover` tìm occupant theo **đúng tọa độ neo**; (d) `applyPreview` tự tính chuỗi dồn bằng số học chỉ số đọc; (e) khoảng 20 chỗ `as? IconCellView`; (f) `mergeable` = app 1×1 hoặc thư mục (đã loại loại lạ); (g) `project_structure.md` ghi "B8 span chưa làm" cho file này, và code chỉ đọc `spanX/spanY` đúng một chỗ (điều kiện `mergeable`, dòng 667) | (b)(c)(d) sai với widget; (f) tự đúng | 5, 7, 8 |
| 14 | `DragHelper.DragSession` + `DockView.onDrop` | Chỉ có cờ `isFolder`; dock chỉ từ chối `isFolder` | Thả widget vào dock: `DockView.onDrop` không từ chối nên ảnh bay hạ cánh, rồi DB từ chối (`moveToDockInTransaction` trả false vì không phải app) và UI nháy về DB | 8 |
| 15 | `HomeViewModel.planGridOptimistic` | `GridInsertPlanner.Slot` không span; **vị trí tới `pos = src.position.copy(x,y)` giữ span** | Chỉ cần truyền span vào `Slot` | 4 |
| 16 | `HomeViewModel.removeAndCompact` | `GridRemovePlanner` chỉ `columns` | Cần `rows` + span | 4 |
| 17 | Trang tạm (`ghostPage`, `PageId.NEW`) | Chung cho mọi item; ghost chỉ tạo khi trang cuối còn item khác item đang kéo | Hoạt động với widget không đổi | — |
| 18 | **Quy tắc trang trống** | Trang chỉ bị xóa ở: (i) `deleteTrailingEmptyPages` — **chỉ các trang CUỐI** trống, ≥ 1 trang, sau gỡ app thật; (ii) re-flow đổi cỡ lưới (`surplus`); (iii) seed. Ẩn app ("−") và `removeItem` **không** xóa trang trống | Hàng widget còn trên trang thì `remaining` tính nó nên trang không bị xóa (đúng). Gỡ widget bằng "−" để lại trang trống đúng như ẩn app | — (D9) |
| 19 | `Jiggler` | Hằng 2° cho mọi view | Widget 4×2 xoay 2° là góc lệch ~7 dp: quá mạnh | 5 |
| 20 | `LeavingIcons.add(cell: IconCellView, iconSize)` | Thu về 0 quanh tâm **icon** (`top + iconSize/2`) | Widget thu quanh sai tâm | 5 |
| 21 | `IconCellView` | Hai chế độ (app/thư mục); tái dùng giữa loại item đòi đặt lại MỌI trạng thái (bài học A1a, AGENTS.md) | Thêm chế độ thứ ba phải xóa trạng thái widget trong `bind`/`bindFolder` | 5 |
| 22 | `GridMetrics.measure` | Trả `null` (ẩn cả lưới) khi `cellWidthRaw < 48dp`; split-screen đổi **kích thước ô**, KHÔNG đổi số cột/hàng (preset) | Widget luôn vừa lưới về số ô; chỉ nội dung phải co theo | 5 |
| 23 | `GlassBudget.maxLiteGlassCellsPerPage` | Classic 6 ô/trang (đo Live 4), LENS 24 (chưa đo Oppo) — tính theo ô thư mục nhỏ | Widget lớn hơn nhiều một ô; chưa có số | 6 |
| 24 | GP (`fix/gp-gap-long-press`) | `OnLongClickListener` trên **`IconGridView`** gắn từ `HomePagerAdapter`; chỉ nhận khi chạm KHÔNG trúng ô con | Widget là ô con → nhận chạm trước, nhấn giữ widget không kích hoạt "khoảng trống" | rebase |

**Hai phát hiện cần chủ dự án biết:** (a) KDoc của `GridInsertPlanner` mô tả "dịch 1 bậc" nhưng code (và preview AN) **dồn sát đuôi**; plan này lấy **code** làm chuẩn và đề xuất sửa KDoc ở Task 1 (đó là file của plan nên sửa được). (b) Hiện KHÔNG có đường nào tạo ra hàng nhiều ô, nên các chỗ #5–#8 chưa từng chạy với dữ liệu thật; chúng là mã chết đã được "bảo vệ bằng cảnh báo".

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật, có nhãn nguồn)

1. **Lưới chỉ từng chứa item 1×1** (đọc code, bảng trên). Mỗi planner dựa vào chỉ số đọc `y*columns + x`; widget phá giả định đó ở cả 3 planner, 4 đường ghi DB và bản sao lạc quan ở `HomeViewModel`.
2. **Hai nơi tính cùng một thứ phải trùng nhau** (bài học M7 Task 6: "thuật toán chèn viết 1 lần ở `domain/layout`, dùng cho cả DB lẫn lạc quan"; AN: preview kéo tự tính chuỗi dồn riêng trong `IconGridView.applyPreview` — đây là bản thứ ba, đã trùng với planner chỉ vì cùng công thức).
3. **Trạng thái kéo-thả không nằm trong view của RecyclerView/ViewPager2** (M7), `requestDisallowInterceptTouchEvent` lúc quyết định kéo (M7), phiên kéo do `Activity.dispatchTouchEvent` nhận (M7 Task 7). Widget kéo qua đúng `IconDragGesture` + `DragHelper` nên các điều kiện này được thừa hưởng, không viết lại.
4. **AN đã chốt nhiều hành vi tinh** (không được làm hỏng): vùng giữa icon 0.9/1.1 để gộp; đẩy-dồn ở mép chỉ sau khi ngón tay đứng yên 500 ms (±8 dp); đẩy-dồn đã chạy thì dính ở ô neo; ảnh bay hạ cánh; FLIP theo `itemId`; icon thu về 0 khi gỡ. Mọi thay đổi ở `IconGridView` phải giữ hành vi này cho trang **không có** widget.
5. **Chi phí kính (đo được, GL):** ô thư mục kính +0.85 ms (6 ô) đến +1.35 ms (tất cả) ở `IssueDrawCommandsStart→SwapBuffers`; trên Oppo cùng code `rec` p50 tăng 0.76 → 4.7 ms khi 6 ô kính bị `invalidate()` mỗi khung cuộn (APK debug). `invalidate()` ghi lại **cả** view, nên phần nội dung tĩnh đắt phải đệm. Kính thật (`LiquidGlassView`) trong `ViewPager2`: Live 4 `FORCE_HIGH` S1 Janky 30.7% (đo được). Widget 4×2 phủ diện tích tương đương 8 ô icon.
6. **Cập nhật theo thời gian phải không tốn gì khi không ai nhìn** (launcher chạy suốt ngày): không receiver/handler khi không có widget hoặc khi cửa sổ không hiện.
7. **Rotation khóa nhưng split-screen đổi kích thước ô** (M5 Task 2, 4): view giữ dữ liệu suy từ kích thước phải bind lại khi `Cells` đổi; không suy kích thước view A từ chính nội dung A.
8. **Không có test tự động** (A7): logic thuần chỉ được chứng minh bằng harness log tạm một lần (tiền lệ M8b Task 1, tag riêng, xóa sau).
9. **File nóng:** GP merge trước và gắn long-click lên `IconGridView` từ ngoài; M8d và GS không chạm `ui/home`. `core/glass` độc quyền của GS trong đợt này.

### D1. Lưu widget thế nào (schema)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `itemType = 4` (BUILTIN_WIDGET), `className` = mã loại, `spanX/spanY` = cỡ, `packageName = null`, `appWidgetId = null`** | Không đổi cột, không `Migration`; thêm hằng + một nhánh `assemble` | Mọi chỗ lọc theo `ITEM_TYPE_APP` (#11) tự bỏ qua widget; `ITEM_TYPE_WIDGET = 3` giữ nguyên nghĩa cho G3c (có `appWidgetId` phải `deleteAppWidgetId` khi xóa — nếu trộn thì G3c phải phân nhánh theo `appWidgetId == null`); build cũ (main) đọc hàng loại 4 qua `else -> Unit`, không crash | Theo `MultiAgent-Workflow.md` 5.3 đặt nghĩa cho `itemType` mới **vẫn là khóa schema** dù không có DDL; cấu hình từng widget (vd thành phố của đồng hồ) không có chỗ lưu | **Chọn** |
| B. Dùng `itemType = 3` với `appWidgetId = null` cho widget tự vẽ | Không hằng mới | Một giá trị | Hai nghĩa dưới một số (đúng loại nhầm lẫn mà `room-schema.md` tránh bằng `appWidgetId` nullable); G3c phải luôn phân nhánh | Không |
| C. Cột mới `widgetConfig TEXT` + `Migration 1→2` + `1.json`/`2.json` | Có chỗ lưu cấu hình | Mở rộng sau dễ | Dự án không có test đỡ migration (Q9, A7); cần kịch bản cài-đè đầy đủ; chưa có widget nào cần cấu hình trong phạm vi này | Không (xét lại khi có widget cần cấu hình) |
| D. Bảng `widgets` riêng | Tách | Sạch | Kéo-thả mất tính "một câu `UPDATE` một hàng" (`room-schema.md` mục 1); logic chỗ trống phải hỏi hai bảng | Không |

**Vì sao A:** lý do trên cộng nguyên tắc "chỉ thêm cột nếu thiếu nó buộc migration cho tính năng đã nằm trong kế hoạch" (`room-schema.md` mục 3, `spanX/spanY`): kích thước đã có cột, loại widget là một chuỗi ngắn nên `className` đủ. **Đánh đổi:** `className` mang nghĩa "mã loại widget" thay vì "tên lớp" khi `itemType = 4` (ghi vào `room-schema.md` qua đề xuất). **Xét lại khi:** có widget cần cấu hình riêng từng bản thể (đồng hồ thành phố) → phương án C ở một nhánh riêng.

### D2. Tổng quát hóa planner thế nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Dãy ô tự do S** | S = các ô trang không bị widget phủ, theo thứ tự đọc. Icon chèn/gỡ/dồn **y như cũ nhưng theo chỉ số trong S**; widget cố định. Chèn widget: icon bị phủ + mọi icon sau điểm neo xếp sát vào S ở chỗ còn lại (cùng luật "dồn sát đuôi" của chèn icon), tràn thì sang trang kế | Trang không widget: S = mọi ô → **trùng bit** planner cũ (chứng minh bằng harness); một hàm cho DB, lạc quan và preview; giữ nguyên luật khe của M7 | Hình học chèn widget phải viết tay kỹ (Task 2); đuôi trang dồn sát (nhất quán với chèn icon, khác "giữ khe" của gỡ icon) | **Chọn** |
| B. Mỗi lần đổi, xếp lại **cả trang** quanh widget (dồn sát tất cả icon) | Một hàm "pack" | Rất đơn giản | Mất khe người dùng chủ ý để (Ruling M7 "kéo icon đi chỗ khác để lại ô trống", Q8); đổi hành vi M7 cho cả người không dùng widget | Không |
| C. Widget chỉ ở "vùng widget" cố định (hàng 0–1 mỗi trang) | Planner icon giữ nguyên, chỉ trừ hai hàng | Rẻ nhất | Không giống iOS; phải khóa trang; không tạo được bố cục "widget ở giữa" | Không |
| D. Ghi N hàng giữ chỗ 1×1 cho các ô widget phủ | Planner cũ không đổi | Không sửa planner | Hàng giả trong DB: `catchUp`/`reflow`/hidden phải biết bỏ; kéo widget = N UPDATE; dễ mồ côi | Không |

**Vì sao A:** giữ cùng một hàm thuần cho 3 nơi (bài học M7 Task 6) và cho phép chứng minh "không hồi quy" bằng so sánh với planner hiện có trên trang ngẫu nhiên không widget (Task 1) — điều B/C/D không cho. **Đánh đổi chấp nhận:** widget **không** thể chồng widget (kéo widget lên widget bị từ chối, ảnh bay về — D8); thêm widget không đẩy icon (D8). **Xét lại khi:** chủ dự án muốn đổi chỗ hai widget bằng kéo-thả.

### D3. Widget được vẽ bằng gì

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Chế độ thứ ba của `IconCellView`, nội dung do `WidgetRenderer` (Canvas) vẽ** | `bindWidget(renderer, cells, spanX, spanY, glass)`; `onDraw` ủy quyền | Giữ "ô con i = item i" và mọi `as? IconCellView` (20 chỗ, #13e) nên **không đụng** code AN đã tinh chỉnh; FLIP, rung, spring độ dời, ảnh chụp kéo (`View.draw`), dấu "−", `IconDragGesture.install` dùng nguyên; một View mỗi widget (lý do M3: "KHÔNG ghép ImageView + TextView") | `IconCellView` (467 dòng) thêm một chế độ: phải xóa trạng thái widget ở `bind`/`bindFolder` (bài học A1a); vài nhánh theo `widget != null` ở pivot, tâm dấu, `onSizeChanged` | **Chọn** |
| B. Lớp `WidgetCellView : View` riêng + interface `GridCell` | Tách sạch | Mỗi lớp một việc | Phải đổi ~20 chỗ `as? IconCellView` trong `IconGridView` (mã AN đã điều chỉnh) sang interface; rủi ro hồi quy lớn hơn lợi ích | Không (xét lại khi G3b/G3c cần loại ô thứ tư) |
| C. Ghép `TextClock` + `TextView`/`AnalogClock` thật làm con của lưới | View framework tự cập nhật | Ít code vẽ | Phá "con i = ô i" (`setItems`, `onLayout`, kéo-thả); mỗi `TextClock` tự đăng ký receiver riêng; `AnalogClock` deprecated từ API 31; ảnh chụp kéo phức tạp | Không |

### D4. Cập nhật theo thời gian

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `WidgetTicker` (object, sổ đăng ký như `FolderOrigins`)**: widget đăng ký khi attach **và** cửa sổ hiện; `Handler` hẹn tại ranh giới phút kế (`60000 − now % 60000`); receiver `TIME_SET` + `TIMEZONE_CHANGED` chỉ đăng ký khi có ≥ 1 widget hiện | 0 chi phí khi không có widget hay khi tắt màn/vào app khác; 1 nhịp cho mọi widget; chạy lại ngay ở `onResume`/khi hiện lại; tiền lệ sổ đăng ký `FolderOrigins`/`DragHelper.registerTarget` | Phải tự xử lý `TimeZone.setDefault(null)` khi đổi múi giờ (suy luận: `ZoneId.systemDefault()` cache), đọc lại 12/24h mỗi nhịp (`DateFormat.is24HourFormat`) | **Chọn** |
| B. `ACTION_TIME_TICK` | Hệ thống bắn mỗi phút | Đúng ranh giới | Phải đăng ký động và giữ receiver suốt thời gian chạy; vẫn phải xử lý hiện/ẩn; không rẻ hơn A | Không |
| C. `TextClock` mỗi widget | Có sẵn | Ít code | Xem D3-C | Không |
| D. Cập nhật mỗi giây (kim giây) | Kim giây mượt | Giống iOS | `invalidate()` mỗi giây suốt ngày trên view lớn; chi phí GL (mục 5) nhân lên | Không (xét lại nếu chủ dự án muốn kim giây) |

Định dạng: `DateFormat.is24HourFormat(context)`, `DateFormat.getBestDateTimePattern(locale, "j:mm")` / `"EEEE d MMMM"`; thứ trong tuần: `WeekFields.of(locale).firstDayOfWeek` (tiếng Việt → thứ Hai) và `DayOfWeek.getDisplayName(TextStyle.SHORT/NARROW, locale)`; chữ hoa chữ đầu bằng `replaceFirstChar` (locale `vi` trả tên tháng/thứ chữ thường — **suy luận chưa kiểm chứng**, kiểm ở Task 5). Đổi ngôn ngữ làm `HomeActivity` tạo lại (đọc `AndroidManifest.xml`: `configChanges` của `HomeActivity` không có `locale` hay `layoutDirection`), nên view và định dạng được dựng mới.

### D5. Nền phía sau widget (chi phí kính)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Kính tại chỗ `BackdropBlurDrawable`** (vai trò `FOLDER_CELL` → `GlassPolicy` trả `STATIC_BLUR` mọi tier, FLAT khi `modeFor == FLAT`), `GlassStyle(...)` tự dựng bằng màu của plan; mỗi widget tính 1 phần của ngân sách ô trang | Cùng ngôn ngữ thị giác với ô thư mục; `core/glass` không sửa; phần đệm nội dung chỉ thêm nếu đo cần | Diện tích lớn gấp 4–8 ô; `invalidate()` mỗi khung cuộn (xem mục 5); chưa có số đo trên diện tích lớn | **Mặc định, quyết bằng đo Task 6** |
| B. Nền phẳng bán trong suốt (`GlassStyle.flat`) | `drawRoundRect` một màu | Rẻ nhất, không `invalidate()` theo cuộn | Mất vẻ kính; khác ô thư mục | **Lùi về nếu A không đạt** |
| C. Kính thật `LiquidGlassView` sau mỗi widget | `RealGlassSurface` | Khúc xạ thật | Đã đo: tier classic S1 Janky 30.7%; trong `ViewPager2` cần xử lý riêng (AGENTS.md mục "Viết docs"); phá "con i = ô i" | Không |
| D. Không nền; chữ trắng bóng đổ trên ảnh nền | Vẽ chữ | Rẻ | Chữ khó đọc trên ảnh sáng | Không |

**Vì sao thứ tự A → B:** A là nhất quán với toàn bộ app (GL), B là lùi an toàn đã có tiền lệ (`GlassMode.FLAT`). Tiêu chí đạt ở Task 6 dùng chuẩn D10 của GL (`plans/GL-glass-pass.md`), so với cùng cảnh không widget trong CÙNG phiên đo: cảnh có vuốt Δp50 ≤ +1 ms và Δp95 ≤ +4 ms; cảnh không vuốt Janky ≤ +2.0 điểm % và Δp95 ≤ +4 ms; Oppo Janky ≤ +1.0 điểm %. Màu chữ: Home luôn chữ trắng + bóng (nhãn icon, `values` không có biến thể `-night` cho `gl_cell_*`), nên widget cũng chữ trắng bất kể giao diện sáng/tối của hệ thống; riêng ảnh nền rất sáng thì tint tối hơn (`gl_bar_tint` #80000000) là phương án chỉnh tay (câu hỏi Q2).

### D6. Người dùng thêm widget bằng đường nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Nút "Tiện ích" (pill) trong `edit_toolbar` → bảng chọn dạng bottom sheet** | Bảng liệt kê bốn biến thể với bản xem trước thật (cùng renderer), bấm một thẻ để thêm | Giống iOS (nút "+" ở edit mode → thư viện widget); chỉ có trong edit mode nên không thêm cử chỉ mới; Edit mode đã có `edit_toolbar` hai pill | Phải sửa `activity_home.xml` (file độc quyền) + một dòng nối ở `HomeActivity`; bottom sheet là cửa sổ riêng: mất khi `recreate()` (chấp nhận như hộp thoại wallpaper, M8a) | **Chọn** |
| B. Dòng trong Cài đặt | `SettingsActivity` thêm hàng | Không đụng file độc quyền | Thêm widget phải rời Home, không biết đang ở trang nào | Không |
| C. Menu khi nhấn giữ khoảng trống (GP) | Mở menu thay vì vào edit mode | Cử chỉ có sẵn | Đổi hành vi GP đã duyệt; thêm cử chỉ cho thứ vốn là hành động của edit mode | Không |
| D. Kéo từ App Library | Library là nguồn | Kéo-thả một lượt | Library chỉ đọc (G2a); thêm nguồn kéo mới | Không |

**Đặt chỗ khi thêm (D8):** tìm hình chữ nhật trống đầu tiên (không đẩy icon) từ trang đang xem, rồi các trang sau, rồi quay lại trang đầu, rồi tạo trang mới ở cuối. Không bao giờ đẩy icon khi *thêm*; đẩy chỉ xảy ra khi người dùng *kéo* widget (người dùng chọn chỗ).

### Đánh đổi chấp nhận

- Widget không chồng widget; thêm widget luôn tìm chỗ trống hoặc tạo trang mới (khác iOS: iOS đẩy icon sang trang kế).
- Không có kim giây, không có sự kiện lịch, không cấu hình từng widget, không đổi cỡ widget sau khi thêm (xóa rồi thêm lại).
- Trang chứa widget: icon dồn sát đuôi khi chèn gần widget (cùng luật chèn hiện có).
- `IconCellView` lớn thêm; đổi lại không động mã AN.

### Rủi ro chính

1. **Hồi quy kéo-thả của AN** do thay `applyPreview` bằng planner (Task 7). Giảm bằng harness trùng bit (Task 1) + kịch bản hồi quy AN đầy đủ ở Task 8 (bắt buộc kiểm tay).
2. **Chi phí kính diện tích lớn** (Task 6 đo, có đường lùi B).
3. **Hình học chèn widget** (Task 2) — phần dễ sai nhất về logic; bảng ca viết trước, điều phối rà trước khi code.
4. **`ViewPager2` + widget có `StaticLayout` dựng lại mỗi phút**: không dựng trong `onDraw`; dựng lúc tick/bind (bài học M3 `IconCellView`).

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Phạm vi: widget tự vẽ đồng hồ + lịch; không thời tiết; không AppWidgetHost | Chủ dự án, `Launcher-Features.md` TT#13, G3c tách riêng |
| 2 | G2c giữ khóa schema nếu cần; chỉ 1 nhánh giữ khóa | Điều phối, `MultiAgent-Workflow.md` đợt 1 |
| 3 | D1: `itemType = 4`, `className` = mã loại, không cột/migration | Agent đề xuất 2026-11-05, chờ duyệt (xin khóa dạng nhẹ, Q1) |
| 4 | D2: planner theo dãy ô tự do S, trùng bit với planner cũ khi không có widget | Agent đề xuất, chờ duyệt |
| 5 | D3: widget là chế độ thứ ba của `IconCellView` | Agent đề xuất, chờ duyệt |
| 6 | D4: `WidgetTicker` canh phút, chỉ chạy khi có widget hiện; không kim giây | Agent đề xuất, chờ duyệt |
| 7 | D5: kính tại chỗ mặc định, nền phẳng làm đường lùi, quyết bằng đo Task 6; không sửa `core/glass` | Agent đề xuất; **CHỐT GIỮ KÍNH** theo số đo Task 6 (Live 4 và Oppo, 2026-11-05), không cần bộ đệm nội dung |
| 8 | D6: nút "Tiện ích" ở `edit_toolbar` + bottom sheet; bản đầu bốn biến thể (Đồng hồ 2×2, 4×2; Lịch 2×2, 4×2) | Agent đề xuất, chờ duyệt (Q1, Q8) |
| 9 | D7 — dấu "−" trên widget = **xóa widget** (xóa hàng; không ẩn, không gỡ cài đặt) **sau hộp thoại xác nhận** (như iOS), để lại trang trống nếu là widget cuối (cùng luật ẩn app) | **ĐỔI theo chủ dự án 2026-11-05 (Q4):** bản đề xuất đầu là xóa tức thì; nay có hộp thoại xác nhận |
| 10 | D8 — kéo widget lên widget khác: từ chối (ảnh bay về); thêm widget lấy chỗ trống đầu tiên hoặc trang mới và không bao giờ đẩy icon | Chủ dự án xác nhận 2026-11-05 (Q5) |
| 11 | D9 — luật trang trống không đổi: chỉ trang cuối trống mới bị xóa ở gỡ app thật / đổi cỡ lưới | Đọc code, bảng #18 |
| 12 | Nhấn giữ widget (chưa edit mode) = vào edit mode (giống ô thư mục, chưa có menu) | Agent đề xuất |
| 13 | Kim phút/giờ, không kim giây; nhịp cập nhật mỗi phút | Agent đề xuất (Q9) |
| 14 | Cỡ tối đa của mọi widget ≤ 4×2 để vừa cả ba preset (nhỏ nhất 4×5) → "widget không còn vừa" không xảy ra khi đổi cỡ lưới; hàng có span lớn hơn lưới (dữ liệu lỗi/tương lai) bị kẹp khi re-flow | Agent đề xuất, đọc `GridPreset` |
| 15 | Khóa schema dạng nhẹ (`itemType = 4`, không migration) được cấp; cả bốn biến thể đầu ship (Q1) | Chủ dự án, 2026-11-05 |
| 16 | Widget giữ cùng cách xử lý thị giác với ô thư mục (kính, chữ trắng); xét lại nếu kiểm bằng mắt không đạt (Q2) | Chủ dự án, 2026-11-05 |
| 17 | Bấm widget (ngoài edit mode) mở app Đồng hồ / Lịch của máy; Task 11 nằm trong phạm vi (Q3) | Chủ dự án, 2026-11-05 |
| 18 | Pill "Tiện ích" cạnh "Cài đặt"; không kim giây; widget rung 1° (Q8, Q9, Q12) | Chủ dự án, 2026-11-05 (giữ đề xuất của plan) |
| 19 | Task 6 là phép đo do điều phối chạy (Live 4 trước; Oppo xếp sau LC, GS), KHÔNG dừng chờ chủ dự án; Task 8 là lần kiểm tay duy nhất của chủ dự án (Q6) | Chủ dự án + điều phối, 2026-11-05 |
| 20 | Điều phối cài APK harness (Task 1, 2) và gửi logcat; executor không cài (Q10). Được sửa đoạn hẹp của `HomeActivity.kt` và `activity_home.xml` (Q11) | Điều phối, 2026-11-05 |
| 21 | Code harness tạm không vào commit tới `main`: harness nằm trong commit riêng "tạm" rồi bị `git revert`/gỡ trước khi tick Task; hoặc không commit (chỉ working tree) | Điều phối, 2026-11-05 |

## Ngoài phạm vi / ghi để sau

- Thời tiết (đóng băng), sự kiện lịch (cần `READ_CALENDAR`), AppWidgetHost và widget của app khác (G3c), widget ảnh (G3b), cấu hình từng widget (thành phố), đổi cỡ widget tại chỗ, đổi chỗ hai widget bằng kéo, đống widget (stack), kim giây.
- Widget trong dock hoặc trong thư mục: không (dock/thư mục chỉ nhận app).
- Menu nhấn giữ widget ("Sửa widget", "Xóa widget"): chỉ có dấu "−" ở bản đầu.
- (Task 11 nay nằm trong phạm vi: bấm widget mở app Đồng hồ/Lịch của máy, Q3 = có.)
- TalkBack cho dấu "−" (nợ từ M7) vẫn chưa làm; riêng widget có `contentDescription` đúng.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `domain/model/PlacedItem.kt` (thêm `PlacedWidget`) | Sửa | Không nóng |
| `domain/model/WidgetKind.kt` (enum mã loại + cỡ cho phép) | Tạo | Không |
| `domain/layout/PageGeometry.kt` (dãy S, ô bị phủ, hình chữ nhật) | Tạo | Không |
| `domain/layout/GridInsertPlanner.kt`, `GridRemovePlanner.kt`, `GridReflowPlanner.kt` | Sửa | Không (domain thuần) |
| `domain/layout/WidgetPlacementPlanner.kt` (hình chữ nhật trống đầu tiên, kẹp neo, kiểm chồng widget) | Tạo | Không |
| `domain/repository/LayoutRepository.kt` (thêm `addWidget`) | Sửa | Không |
| `domain/usecase/AddWidgetUseCase.kt`, `RemoveWidgetUseCase.kt` | Tạo | Không |
| `data/layout/db/LauncherItemEntity.kt` (hằng `ITEM_TYPE_BUILTIN_WIDGET = 4`, KHÔNG đổi cột) | Sửa | **Độc quyền (`data/layout/db/*`)** — khóa schema |
| `data/layout/LayoutReconciler.kt` | Sửa | **Độc quyền** |
| `data/layout/LayoutRepositoryImpl.kt` (`assemble`, `addWidget`) | Sửa | Không (cùng thư mục, cẩn thận) |
| `ui/home/HomeViewModel.kt` (planner có span, `onAddWidget`, `onRemoveWidget`) | Sửa | **Độc quyền** |
| `ui/home/HomeUiState.kt` | Dự kiến **không sửa** (bảng chọn là cửa sổ riêng, không vào state) | **Độc quyền** (giữ chỗ) |
| `ui/home/HomeEvent.kt` (`WidgetAdded(pageId)` để pager cuộn tới) | Sửa | Không |
| `core/ui/DragHelper.kt` (`DragSession`: `isWidget`, `spanX/spanY`) | Sửa | **Độc quyền** |
| `ui/home/view/IconCellView.kt` (chế độ widget) | Sửa | Không (G2b/AN đã merge) |
| `ui/home/view/IconGridView.kt` (kích thước ô theo span, loại ô, hover/preview theo planner, drop) | Sửa | Không độc quyền, **GP đã merge trước; rebase** |
| `ui/home/view/DockView.kt` (từ chối `isWidget`) | Sửa (1 điều kiện) | Không |
| `ui/home/view/Jiggler.kt` (biên độ theo view), `LeavingIcons.kt` (tâm thu theo ô widget) | Sửa nhỏ | Không |
| `ui/home/HomePagerAdapter.kt` | Sửa nhỏ (truyền `onWidgetLongPress` nếu cần) | Không độc quyền; GP đã thêm 1 tham số; rebase |
| `ui/home/controller/WidgetPickerController.kt` | Tạo | Không |
| `ui/widget/WidgetRenderer.kt`, `ClockWidgetRenderer.kt`, `CalendarWidgetRenderer.kt`, `WidgetFormat.kt`, `WidgetTicker.kt`, `WidgetPickerSheet.kt` | Tạo | Không |
| `res/layout/view_widget_picker.xml`, `item_widget_option.xml` | Tạo | Không |
| `res/layout/activity_home.xml` (thêm pill `btn_add_widget` vào `edit_toolbar`) | Sửa (~12 dòng) | **Độc quyền — xin phép** (đường lùi: dựng pill bằng code trong `WidgetPickerController`, không đụng XML) |
| `ui/home/HomeActivity.kt` (một dòng nối ở khối "Dòng nối cho AN/G2b" + `onDestroy` đóng bảng chọn) | Sửa (~4 dòng) | **Độc quyền — xin phép** |
| `res/values/strings.xml`, `dimens.xml`, `colors.xml` (khối `widget_` ở cuối) | Sửa | Dùng chung (chỉ thêm) |
| `docs/plans/G2c-…md`, `docs/knowledgements/G2c-…md` | Tạo | — |
| **Không đụng:** `core/glass/*` (GS), `SearchPillView`, `ui/settings/*`, `data/settings/*`, `domain/search`, `ui/search`, `AndroidManifest.xml` (trừ Task 11 nếu chủ dự án duyệt), `app/schemas/*`, `gradle/libs.versions.toml`, shared docs | — | — |

## Global Constraints

- Luật `ui → domain ← data`. Mọi planner và `WidgetKind`/`WidgetPlacementPlanner` ở `domain` là Kotlin thuần, **không** `android.*`. Chuỗi hiển thị (tên widget trong bảng chọn, `contentDescription`) ở `strings.xml`; định dạng ngày giờ ở `ui/widget` (cần `Locale`/`DateFormat`).
- **Không đổi schema Room**: không cột, không bảng, không `version`, không `Migration`; `git diff --stat -- app/schemas` rỗng ở mọi commit. Số `4` đã ghi xuống DB thì không đổi nghĩa (`LauncherItemEntity` KDoc).
- **Trang không có widget phải cho kết quả y hệt trước** (planner trùng bit; preview, FLIP, hover, đẩy-dồn dính, ảnh bay, thu về 0 không đổi hành vi). Mọi chỗ quyết định theo `PlacedWidget` đi sau kiểm loại.
- Trạng thái kéo-thả ở dữ liệu (M7): không giữ "đang kéo widget" ở field của view ngoài những gì `IconGridView` đã giữ; `DragSession` mang `isWidget/spanX/spanY` do `beginDragFor` đặt từ dữ liệu.
- `requestDisallowInterceptTouchEvent` đã nằm trong `IconDragGesture`; widget dùng lại, không thêm đường chạm mới. Mọi `OnTouchListener`/click của ô tái dùng phải đặt lại khi ô đổi loại (bài học A1a); `IconCellView.bind`/`bindFolder` xóa trạng thái widget.
- Không `onDraw` cấp phát (`Paint`, `Path`, `StaticLayout`, `Rect`): dựng ở `bindWidget`/`tick` (bài học GL Task 6). Tự quy đổi cỡ chữ từ kích thước ô, **không** từ `sp` (bài học GL Task 11, `font_scale`).
- `WidgetTicker`: đăng ký/hủy đối xứng ở `onAttachedToWindow`/`onWindowVisibilityChanged`/`onDetachedFromWindow`; không giữ tham chiếu `View` của Activity đã hủy (`object` sống xuyên Activity, như `DragHelper`). Không receiver nào khi 0 widget hiện.
- Hằng chỉnh tay (cỡ chữ, tỉ lệ kim, biên độ rung widget, ngưỡng ẩn dòng phụ) đặt trong `companion object` và ghi "CHỈNH TAY".
- Mọi hằng/biến mới trong `res` đặt thành khối liền `widget_` ở cuối file (Workflow mục 3).
- Không thêm test, không thêm thư viện, không đoán version, không cài APK khi chưa được bảo, không xóa file, không sửa shared docs; log chẩn đoán dùng tag `G2C` và xóa trước khi tick (grep tiền tố + import `Log`).
- Công tắc/seed tạm của Task 3, 5, 6 **không vào `main`**: gỡ ở Task 9 và so `git diff <commit đã kiểm> HEAD -- app` (bài học AN).

## Review Focus

1. **Trùng bit khi không có widget:** `GridInsertPlanner`/`GridRemovePlanner`/`GridReflowPlanner` mới trả đúng kết quả của bản cũ trên N trang ngẫu nhiên không widget (harness Task 1), gồm tràn trang và trang mới.
2. **Hình học widget:** mọi ca trong bảng Task 2 (chèn lên ô trống/đầy, tràn nhiều icon sang trang kế, ô đích bị widget phủ, neo bị kẹp ra khỏi lưới, chồng widget → từ chối).
3. **Mọi đường ghi DB biết span:** `moveItem`, `reflowInTransaction` (không còn thoát sớm), `ensureFitsGrid` (cộng span), `deleteAndCompact` (chốt theo từng trang), `compactPageAfterRemoval`, `placeNewInTransaction` (đã đúng). Không còn `spanX > 1` guard nào chặn cả lưới.
4. **Lạc quan khớp DB:** sau mỗi thao tác với widget, `scheduleReconcile` không làm UI nhảy (so `pages` UI và DB).
5. **Hồi quy AN:** vùng giữa/gộp thư mục, đẩy-dồn 500 ms, đẩy-dồn dính, kính hover, ảnh bay hạ cánh, thu về 0 khi gỡ, FLIP, trên trang **có** và **không có** widget.
6. **Widget không phải đích gộp / nguồn gộp**; không vào dock; không vào thư mục.
7. **Kính/chi phí:** số đo Task 6 (Live 4; Oppo khi có chỗ) cảnh vuốt, rung, đứng yên, tick phút; quyết định kính hay phẳng có số.
8. **Ticker:** 0 receiver/handler khi không có widget hoặc khi Home không hiện; đổi múi giờ, ngày, ngôn ngữ, 12/24h cập nhật đúng; không rò `View`.
9. **Split-screen / gập:** widget co theo ô, dòng phụ ẩn khi quá thấp, không tràn ô; `Cells` đổi thì bind lại.
10. **Dấu "−" của widget xóa hàng widget sau hộp thoại xác nhận** (không ghi `hidden_apps`, không gỡ app; Hủy thì không đổi gì); trang chỉ có widget vẫn tồn tại sau khi xóa widget (cùng luật ẩn app).
11. **Không sửa file ngoài vùng khai;** `git diff --stat` không chạm `core/glass`, `app/schemas`, shared docs.
12. **Công tắc/seed tạm đã gỡ** (diff rỗng so với commit đã kiểm).

---

### Task 1: Planner nhận biết span — chèn và gỡ theo dãy ô tự do S (pha A, chưa có UI)
**Bắt buộc kiểm tay:** không (chứng minh logic bằng harness log tạm; thuộc phần tự kiểm của executor). Cần điều phối cài APK harness lên Live 4 và gửi logcat (mở rộng quyết định 7 của Workflow cho "log harness", Q10); không có máy thì dừng ở Step 4.
**Files:** `domain/layout/PageGeometry.kt` (tạo), `GridInsertPlanner.kt`, `GridRemovePlanner.kt` (sửa); gọi tạm từ `HomeActivity.onCreate` hoặc `LauncherApp` (công tắc tạm, xóa ở Step 6).
**Interfaces:**
- Consumes: `GridInsertPlanner.Slot/PageSlots/Placement/Plan` hiện có.
- Produces: `Slot(itemId, x, y, spanX = 1, spanY = 1)` (giá trị mặc định → mọi nơi gọi cũ biên dịch nguyên); `GridInsertPlanner.plan(pages, movingItemId, targetPageIndex, targetX, targetY, columns, rows, movingSpanX = 1, movingSpanY = 1): Plan` (từ chối = `Plan.rejected = true`, placements rỗng; ĐỔI so với bản duyệt, điều phối đồng ý 2026-11-05, vì kiểu nullable làm vỡ hai nơi gọi cũ); `GridRemovePlanner.plan(slots, removedItemId, columns, rows)`; `PageGeometry.freeCells(slots, columns, rows): List<Cell>` (S theo thứ tự đọc), `covered(slot)`, `isWidget(slot)` (= span > 1).
- [x] **Step 1: Viết bảng ca** vào mục "Nhật ký" của plan (agent tự tính tay, điều phối rà trước khi code). Tối thiểu: (a) bốn ca ví dụ dưới đây; (b) trang đầy 24 icon + chèn; (c) trang có 2 widget; (d) ô đích nằm trong vùng phủ widget; (e) tràn sang trang kế khi S nhỏ hơn 24; (f) chèn vào trang kế cũng có widget ở (0,0).
  - *Ca W1* (4×6): widget Z 2×2 ở (0,0); icon a(2,0) b(3,0) c(2,1) d(3,1) e(0,2) f(1,2). **Gỡ a** → b(2,0) c(3,0) d(2,1) e(3,1) f(0,2) (đi dọc S, giữ khe như cũ).
  - *Ca W2:* cùng trang, **chèn x vào (3,0)** (đang là b, seq 1) → x(3,0); đuôi dồn sát từ seq 2: b(2,1) c(3,1) d(0,2) e(1,2) f(2,2); a giữ (2,0).
  - *Ca W3:* **chèn x vào (1,1)** (ô bị Z phủ) → ô đích được đổi thành ô S đầu tiên có chỉ số đọc lớn hơn (2,1) (đang là c) → x(2,1); c(3,1) d(0,2) e(1,2) f(2,2).
  - *Ca W4 (chèn widget, Task 2):* xem Task 2.
- [x] **Step 2: `PageGeometry` + `GridInsertPlanner` theo S.** Với `Slot` span > 1 là widget: cố định. Icon: chỉ số `seq` = vị trí trong S. Chèn icon: nếu ô đích (đã ánh xạ về S như W3) trống thì chỉ đặt; nếu có occupant thì occupant và mọi icon có `seq` lớn hơn xếp **sát nhau** từ `seq(đích)+1` (đúng luật code hiện tại, không phải KDoc); phần vượt `|S|` (tối đa 1 icon) sang trang kế tại ô S đầu tiên (lặp, có thể tạo trang). Sửa KDoc của `GridInsertPlanner` cho đúng với code ("dồn sát đuôi").
- [x] **Step 3: `GridRemovePlanner` theo S** (mọi icon `seq` lớn hơn lùi 1 trong S, **giữ khe**; gỡ widget không dồn gì). Cập nhật 3 nơi gọi hiện có chỉ ở chỗ truyền thêm `rows` (xem Task 3, 4; ở Task này chỉ cần biên dịch: dùng tham số mặc định tạm hoặc truyền đúng `rows` ngay).
- [x] **Step 4: Harness tạm (tag `G2C`)**: (i) giữ **bản sao nguyên văn của planner cũ** trong file tạm; sinh ≥ 2000 trang ngẫu nhiên không widget (cỡ 4×5, 4×6, 5×6, mật độ 0–100%, có khe, có tràn), mỗi trang chọn nguồn (trong trang / từ dock / từ trang khác) và ô đích ngẫu nhiên, so `Plan` mới và cũ — **phải bằng nhau từng phần tử**; tương tự cho `GridRemovePlanner` cũ/mới. In `G2C equal=N diff=0`. (ii) chạy bảng ca ở Step 1, in `PASS`/`FAIL` từng ca với kết quả thực.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) điều phối cài APK harness, `adb logcat -s G2C` sau khi mở app: dòng `G2C equal=… diff=0` và mọi ca `PASS`; (b) mở Home bình thường vẫn hoạt động y như trước (planner mới chạy đường thật khi kéo-thả: kéo một icon sang ô đã có icon rồi sang trang khác, ẩn một icon bằng "−": giống hệt trước).
- [x] **Step 6: Gỡ harness + bản sao planner cũ**, grep `G2C`/import `Log`; build xanh; commit `feat: make grid insert and remove planners aware of multi-cell items`.

### Task 2: Planner chèn widget, đặt chỗ đầu tiên và re-flow nén (pha A)
**Bắt buộc kiểm tay:** không (harness như Task 1).
**Files:** `domain/layout/WidgetPlacementPlanner.kt` (tạo), `GridInsertPlanner.kt`, `GridReflowPlanner.kt` (sửa), `domain/model/WidgetKind.kt` (tạo).
**Interfaces:**
- Produces: `WidgetKind { CLOCK("clock"), CALENDAR("calendar") }` với `fromId(String)`, `sizes` + `allows(spanX, spanY)` (CLOCK: 2×2, 4×2; CALENDAR: 2×2, 4×2; tất cả ≤ 4×2, D14); `WidgetPlacementPlanner.firstFit(pages, spanX, spanY, startPageIndex, columns, rows): Placement?` (null → cần tạo trang); `GridInsertPlanner.plan` nhận `movingSpanX/Y` > 1: kẹp neo vào `[0, columns−spanX] × [0, rows−spanY]`, **từ chối (null)** nếu hình chữ nhật chồng widget khác; `GridReflowPlanner.plan` đóng gói theo thứ tự đọc.
- [x] **Step 1: Bảng ca chèn widget** (viết tay trước, điều phối rà), tối thiểu:
  - *Ca W4* (4×6): icon a(0,0) b(1,0) c(2,0) d(3,0) e(0,1) f(1,1); thả widget Z 2×2 ở neo (0,0). Hình chữ nhật {(0,0),(1,0),(0,1),(1,1)} phủ a,b,e,f. Đuôi (mọi icon có chỉ số đọc ≥ chỉ số neo) xếp sát vào S' theo thứ tự đọc: a(2,0) b(3,0) c(2,1) d(3,1) e(0,2) f(1,2).
  - *Ca W5:* trang 4×6 đầy 24 icon + widget 4×2 ở neo (0,0): S' = 16 ô; 16 icon đầu theo thứ tự đọc vào S'; **8 icon cuối tràn** sang trang kế, chèn theo khối có thứ tự tại ô S đầu tiên của trang đó (đuôi trang kế dồn sát tiếp theo luật Task 1, lặp nếu tràn nữa).
  - *Ca W6:* neo (3,5) với widget 2×2 → kẹp thành (2,4).
  - *Ca W7:* thả widget Z 4×2 lên widget Y 2×2 → `rejected = true` (từ chối); thả widget lên vùng chỉ có ô trống → không icon nào đổi chỗ.
  - *Ca W8:* kéo chính widget Z trong trang (dịch 1 ô sang phải): Z bị gỡ khỏi chỗ cũ trước khi tính (ô cũ để trống, không dồn).
  - *Ca R1 (re-flow):* 5×6 → 4×6 có widget 4×2 ở (1,0): kết quả đặt ở (0,0), icon trải theo S.
  - *Ca R2:* widget 4×2 và 22 icon, 4×5 → trang cần 2 trang: widget trước (theo chỉ số đọc ban đầu), icon lấp quanh/tràn; `totalPages` đúng.
  - *Ca R3:* widget có span lớn hơn lưới (dữ liệu lỗi) bị kẹp theo lưới và `Log.w`.
  - *Ca F1 (`firstFit`):* trang đầy icon, trang kế còn trống → trả trang kế; mọi trang đầy → `null`.
- [x] **Step 2: `GridInsertPlanner` cho widget** theo hình học đã chốt ở D2: gỡ nguồn khỏi chỗ cũ; kẹp neo; từ chối nếu chồng widget; nếu hình chữ nhật không phủ icon nào thì chỉ đặt; nếu phủ ≥ 1 icon thì **đuôi** (mọi icon có chỉ số đọc ≥ chỉ số neo, không kể widget) xếp sát vào S' từ ô S' đầu tiên có chỉ số đọc ≥ chỉ số neo; icon vượt `|S'|` (có thể nhiều) tràn theo khối sang trang kế (đuôi trang kế dồn sát theo cùng luật; tạo trang nếu cần).
- [x] **Step 3: `GridReflowPlanner` đóng gói:** duyệt item theo thứ tự đọc cũ (trang, y, x, id); mỗi item đặt vào **vị trí đầu tiên còn vừa** của trang hiện tại (quét từ ô (0,0)): icon 1 ô, widget hình chữ nhật; hết chỗ thì sang trang kế và tiếp tục; icon sau widget được phép lấp khe cạnh widget trong CÙNG trang, không lùi về trang trước. Không widget → kết quả y hệt bản cũ (thêm vào harness so bản cũ).
- [x] **Step 4: `WidgetPlacementPlanner.firstFit`:** quét hình chữ nhật trống (ô không bị icon hay widget phủ) đầu tiên theo thứ tự đọc, từ `startPageIndex`, vòng qua các trang sau rồi các trang trước; không tìm thấy → `null` (người gọi tạo trang cuối).
- [x] **Step 5: Harness (tag `G2C`)** chạy bảng ca Step 1 + so sánh `GridReflowPlanner` cũ/mới trên trang ngẫu nhiên không widget; thêm tính chất: sau mọi `plan` không item nào chồng ô, mọi item trong lưới, tổng icon bảo toàn.
- [x] **Step 6: Kịch bản kiểm (Live 4)** — điều phối cài APK harness, `adb logcat -s G2C`: mọi `PASS`, `equal … diff=0`, `invariants ok`. Gỡ harness, grep tiền tố. Commit `feat: add widget insert, first-fit placement and packing reflow planners`.

### Task 3: Dữ liệu — hàng widget, mọi đường ghi biết span (khóa schema nhẹ)
**Bắt buộc kiểm tay:** không (nhưng xem Bài kiểm cả tính năng mục "cài đè" bắt buộc theo 5.3).
**Files:** `data/layout/db/LauncherItemEntity.kt`, `data/layout/LayoutReconciler.kt`, `data/layout/LayoutRepositoryImpl.kt`, `domain/model/PlacedItem.kt`, `domain/repository/LayoutRepository.kt`, `domain/usecase/AddWidgetUseCase.kt`, `RemoveWidgetUseCase.kt`.
**Interfaces:**
- Produces: `ITEM_TYPE_BUILTIN_WIDGET = 4`; `PlacedWidget(itemId, position (có span), kind: WidgetKind)`; `LayoutRepository.addWidget(kind, spanX, spanY, preferredPageId): Long?` (trả id trang đặt, hoặc null nếu lỗi); `RemoveWidgetUseCase` gọi `removeItem`.
- [x] **Step 1:** hằng + KDoc `LauncherItemEntity` (bất biến: loại 4 có `className` = mã loại, `packageName/appWidgetId = null`, `container = HOME`); `assemble`: nhánh loại 4 → `PlacedWidget` nếu `WidgetKind.fromId(className)` và cỡ nằm trong `allowedSizes`, ngược lại `Log.w` bỏ qua (không xóa hàng).
- [x] **Step 2: `LayoutReconciler`** (trong `mutex` + transaction như hiện tại): (a) mọi chỗ dựng `Slot` truyền span; (b) `moveToHomeInTransaction` truyền `movingSpanX/Y` của hàng đang di chuyển, xử lý `plan == null` → trả `false` (UI bay về); (c) `reflowInTransaction` bỏ thoát sớm, dùng `GridReflowPlanner` mới, ghi lại `gridX/gridY/pageId` (và kẹp span nếu lớn hơn lưới, ghi `spanX/spanY`); (d) `ensureFitsGrid` coi `gridX + spanX > columns || gridY + spanY > rows` là ngoài lưới; (e) `deleteAndCompact`: bỏ chốt toàn cục, dồn **theo từng trang** bằng `GridRemovePlanner` có span (trang không widget cho kết quả cũ); (f) `compactPageAfterRemoval` truyền span và `rows`; (g) hàm mới `addWidget(kind, spanX, spanY, preferredPageId)`: `firstFit` từ trang ưu tiên, không có chỗ thì tạo trang cuối (cùng transaction), insert hàng loại 4, trả pageId. Gỡ widget = `removeItem` (đã có, không dồn).
- [x] **Step 3: Seed tạm (công tắc debug, xóa ở Task 9):** khi `BuildConfig.DEBUG` và một hằng tạm, chèn 1 đồng hồ 2×2 + 1 lịch 4×2 vào trang đầu nếu chưa có widget (để kiểm Task 3–8 trước khi có bảng chọn). Ghi rõ điểm đặt trong Nhật ký.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — cài từ worktree khi được bảo; (a) `adb shell am force-stop gambi.global.os26launcher` rồi mở lại; kéo 3 file DB (`launcher.db`, `-wal`, `-shm`, `adb exec-out run-as … cat …`, AGENTS.md mục M4 Task 4) và đọc bằng `sqlite3` của Python (`python -I`): có đúng 2 hàng `itemType = 4`, `className` là `clock`/`calendar`, span đúng, `packageName`/`appWidgetId` null; (b) hàng loại 4 không làm `catchUp` xóa gì (log `LayoutReconciler`); (c) `adb shell cmd package` gỡ rồi cài một app trên trang có widget: dồn đúng theo S (đối chiếu vị trí trong DB); (d) hiện chưa có UI vẽ widget: ô trống ở chỗ widget là đúng (`assemble` đã trả `PlacedWidget` nhưng `IconGridView` chưa biết vẽ — Task 5; nếu `IconGridView` ném khi gặp loại lạ thì thêm nhánh bỏ qua tạm).
- [x] **Step 5: Commit** `feat: store built-in widget rows and make layout writes span-aware`.

### Task 4: ViewModel — lạc quan có span, thêm/xóa widget
**Bắt buộc kiểm tay:** không.
**Files:** `ui/home/HomeViewModel.kt`, `ui/home/HomeEvent.kt`.
**Interfaces:** Consumes: `AddWidgetUseCase`, `RemoveWidgetUseCase`, planner mới. Produces: `HomeViewModel.onAddWidget(kind, spanX, spanY, preferredPageIndex)`, `onRemoveWidget(itemId)` (do `onQuickHideTapped` gọi khi item là `PlacedWidget`), `HomeEvent.WidgetAdded(pageId)`.
- [x] **Step 1:** mọi chỗ dựng `GridInsertPlanner.Slot` (`planGridOptimistic`, `removeAndCompact`) truyền span và `rows`; `planGridOptimistic` truyền span của item đang kéo (lấy từ `PlacedItem.position`), kết quả `rejected` (từ chối) → trạng thái lạc quan **không đổi** và `draggingItemId = null` (DB sẽ trả false → `publishFromDb`). Thứ tự item trong trang vẫn sắp `(y, x)`.
- [x] **Step 2: `onRemoveWidget`:** `launchLayoutWrite` lạc quan bỏ item khỏi trang (không dồn), ghi `RemoveWidgetUseCase`. `onQuickHideTapped` rẽ nhánh theo loại item (widget → `onRemoveWidget`; app → như cũ).
- [x] **Step 3: `onAddWidget`:** không lạc quan (DB nhanh; tránh id tạm khác id thật gây thu-rồi-phình, xem bảng #13/`planEnterExit`); `viewModelScope.launch { addWidgetUseCase(...) }` rồi `_events.trySend(WidgetAdded(pageId))`. Lỗi → `Log.e` + `MenuActionFailed`.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — (a) build xanh, mở app: bố cục cũ không đổi; (b) (chưa có UI) không có hành vi nào mới ngoài seed của Task 3; (c) đọc code: không còn `Slot(` thiếu span (`grep`).
- [x] **Step 5: Commit** `feat: carry widget spans through optimistic layout updates`.

### Task 5: Vẽ widget — renderer, ticker, `IconCellView` chế độ widget
**Bắt buộc kiểm tay:** không (hiển thị; gộp vào bài kiểm cuối).
**Files:** `ui/widget/*` (tạo), `ui/home/view/IconCellView.kt`, `IconGridView.kt`, `Jiggler.kt`, `LeavingIcons.kt`, `res/values/{strings,dimens,colors}.xml` (khối `widget_`).
**Interfaces:** Produces: `WidgetRenderer { fun prepare(width, height, density, now: ZonedDateTime, locale, is24h); fun draw(canvas); fun contentDescription(): String }`; `IconCellView.bindWidget(renderer, cells, spanX, spanY, glass)`; `WidgetTicker.register(view)/unregister(view)`.
- [x] **Step 1: Renderer** (không cấp phát trong `draw`): `ClockWidgetRenderer` 2×2 (mặt tròn, 12 vạch, kim giờ `(h%12 + m/60)·30°`, kim phút `m·6°`, không kim giây) và 4×2 (giờ lớn theo `is24h`, dòng ngày); `CalendarWidgetRenderer` 2×2 (thứ ngắn, số ngày lớn, tháng) và 4×2 (trái: thứ + ngày; phải: lưới tháng 7 cột, hôm nay tô nổi, đầu tuần theo `WeekFields.of(locale)`). Cỡ chữ và kim suy từ `min(width, height)` (không từ `sp`); dòng phụ và lưới tháng ẩn khi cạnh ngắn dưới ngưỡng (hằng CHỈNH TAY, tương tự `LABEL_MIN_SCALE`). Chữ trắng + bóng (cùng nhãn icon). Ghi `Locale`/`TextStyle` dùng.
- [x] **Step 2: `WidgetTicker`:** sổ `LinkedHashSet<IconCellView>`; `register` khi cửa sổ hiện, `unregister` khi ẩn/detach; `Handler.postDelayed` tại ranh giới phút kế (tính lại từ `System.currentTimeMillis()` mỗi nhịp); `BroadcastReceiver` `ACTION_TIME_CHANGED` + `ACTION_TIMEZONE_CHANGED` đăng ký bằng `ContextCompat.registerReceiver` (`RECEIVER_NOT_EXPORTED`) chỉ khi có ≥ 1 view đang đăng ký, hủy khi về 0; nhịp = gọi `tick()` mỗi view (đọc lại `is24h`, `TimeZone.setDefault(null)` khi đổi múi giờ). `IconCellView` tự `register`/`unregister` ở `onAttachedToWindow`/`onWindowVisibilityChanged`/`onDetachedFromWindow` chỉ khi đang ở chế độ widget.
- [x] **Step 3: `IconCellView` chế độ widget:** `bindWidget` xóa `icon`, `folderPreviews`, `folderGlass`, `folderContent` (bài học A1a) và đặt `widget*`; `bind`/`bindFolder` xóa trạng thái widget; `onDraw` ủy quyền (nền kính/phẳng + `renderer.draw`), vẫn vẽ dấu "−" khi `badgeVisible`; pivot co/phóng ở **tâm widget**; tâm dấu "−" lùi vào trong góc (~r/2) để không tràn khỏi mép màn hình ở cột 0; `invalidateGlass()` cũng làm mới kính widget; `contentDescription` đặt lại mỗi tick (chỉ khi chuỗi đổi). Nền kính: `BackdropBlurDrawable(owner, backdrop, cache, GlassStyle(tint, border, flat))` với màu `widget_glass_*` (khởi điểm = `gl_cell_*`), bán kính theo `min(w,h)·0.12`; FLAT khi `glassEnv.modeFor(FOLDER_CELL) == FLAT`.
- [x] **Step 4: `IconGridView`:** (a) `setItems` bảo đảm loại view con khớp loại item (xóa `IconCellView` cũ và thêm mới vẫn là `IconCellView` — cùng lớp nên chỉ cần `bindWidget` vs `bind`; không đổi số con); (b) `onLayout` đặt kích thước ô theo span: rộng `spanX·cellWidth + (spanX−1)·gutterX`, cao `spanY·cellHeight + (spanY−1)·gutterY`; (c) `bindAllCells` nhánh `PlacedWidget` → `bindWidget`, `pressFeedback = false`, `alpha` theo `hiddenItemId/settlingItemId` (bài học: luôn từ dữ liệu), không nạp icon; (d) `setEditMode` bật dấu "−" cả với `PlacedWidget`; `onQuickHide` gọi với `itemId` (VM rẽ nhánh); (e) nhấn giữ widget (chưa edit mode) → `onFolderLongPress` (vào edit mode), `IconDragGesture.install` cho widget; (f) `computeGlassFolderIds` trừ phần widget đã dùng khỏi ngân sách thư mục (ghi rõ cách đếm, xem Task 6); (g) `LeavingIcons.add` nhận tâm thu (`pivotY` = giữa ô với widget); (h) `Jiggler` nhận hệ số biên độ theo view (widget 0.5 → 1°, CHỈNH TAY).
- [x] **Step 5: Kịch bản kiểm (Live 4)** (seed của Task 3) — (a) hai widget hiện đúng chỗ, đúng cỡ, icon quanh chúng đúng ô; (b) giờ khớp đồng hồ hệ thống; chờ qua ranh giới phút: đổi đúng khoảng 0–1 s sau (đọc `adb logcat -s G2C` tick log tạm); (c) Cài đặt hệ thống → bật/tắt 24 giờ → quay lại Home: dạng giờ đổi ngay (onResume); (d) Cài đặt → Ngôn ngữ: Tiếng Việt ↔ English: Home tạo lại, thứ/tháng đúng ngôn ngữ, chữ đầu viết hoa, tuần bắt đầu thứ Hai ở vi (kiểm bằng mắt đối chiếu lịch máy); (e) đổi múi giờ sang Tokyo (UI hệ thống): giờ widget đổi ngay khi quay về Home, không cần mở lại; (f) qua 00:00 (hoặc đổi ngày tay): ngày đổi; (g) giao diện hệ thống sáng/tối: widget giữ chữ trắng đọc được trên cả ảnh nền tối và sáng (chụp màn hình hai ảnh nền); (h) vào/ra edit mode: dấu "−" hiện ở góc trên-trái widget (không bị cắt ở cột 0), widget rung nhẹ; (i) chia đôi màn hình (split-screen) trên Live 4: widget co theo ô, dòng phụ biến mất khi quá thấp, không tràn; về toàn màn hình thì trở lại; (j) bật/tắt nhãn icon trong Cài đặt: widget không đổi cỡ; (k) tắt màn hình 2 phút rồi bật: đúng giờ ngay (không trễ tới nhịp kế); (l) khi không có widget nào (xóa seed): không có receiver/handler (log tạm `register=0`).
- [x] **Step 6: Commit** `feat: draw clock and calendar widgets in the home grid`.

### Task 6: Đo kính và chi phí của widget — chọn kính hay nền phẳng
**Bắt buộc kiểm tay:** **có, nhưng là phép đo do điều phối chạy (quyết định 7, không dừng chờ chủ dự án; Live 4 trước, Oppo xếp sau LC và GS)** — lý do (mục 4.2): phép đo hiệu năng dùng để chọn phương án (D5 A hay B); các Task sau (kéo, bảng chọn) đều dựng trên kết quả này.
**Files:** `ui/home/view/IconCellView.kt` (cờ chọn kính/phẳng, một hằng tạm trong lúc đo), có thể bộ đệm nội dung widget nếu số đo đòi.
- [x] **Step 1: Cấu hình đo** (hằng tạm, gỡ khi chốt): `W0` không widget (xóa seed); `W1` 1 đồng hồ 2×2 kính; `W2` 2 widget (2×2 + 4×2) kính; `W3` như W2 nền phẳng; `W4` như W2 và edit mode (rung); `W5` như W2 và `invalidate()` mỗi khung cuộn bị tắt (kiểm chi phí `invalidateGlass`).
- [x] **Step 2: Cảnh đo** (Live 4, `FORCE_HIGH` đúng cấu hình chủ dự án, mức kính ghi rõ; cùng một phiên, xen kẽ, lặp `W0` cuối phiên — AGENTS.md bài học GL/AN): S1 vuốt qua lại 4 trang 30 giây; S2 đứng yên 30 giây qua một ranh giới phút (đo chi phí tick); S3 edit mode rung 30 giây; S4 mở/đóng panel thư mục có widget phía sau. Mỗi cảnh: `dumpsys gfxinfo gambi.global.os26launcher reset` rồi đọc `framestats` (lấy mẫu định kỳ, khử trùng `IntendedVsync`); ghi tổng khung, khung > 33 ms, p50/p95, Janky, `DrawStart→SyncQueued` và `IssueDrawCommandsStart→SwapBuffers`.
- [x] **Step 3: Oppo A3x** (nếu được chỗ, Q6): lặp W0/W2/W3 ở S1, S3. (Oppo đo `rec` 10× đắt hơn Live 4 cho cùng mã — GL.)
- [x] **Step 4: Quyết** theo ngưỡng D10 của GL (xem D5) so với `W0` trong cùng phiên. Đạt → kính (A) và ghi số vào D5; không đạt → nền phẳng (B), hoặc thêm bộ đệm nội dung (bitmap một lần mỗi phút, như `folderContent` của GL Task 6c) rồi đo lại một lượt trước khi lùi về B. Gỡ hằng tạm. Điền số vào "Nhật ký" và D5.
- [x] **Step 5: Kịch bản kiểm** — điều phối tự cài và chạy các lệnh đo (quyết định 7 của Workflow); chủ dự án xem mắt: (a) ảnh nền sáng và tối: chữ widget đọc được; (b) vuốt trang: kính không lệch ảnh nền, không giật cảm nhận; (c) tới quyết định kính/phẳng. Kết quả vào bài kiểm cuối.
- [x] **Step 6: Commit** `feat: choose the widget background from measured frame cost`.

### Task 7: Kéo icon quanh widget — preview theo planner, ô đích bị phủ, đẩy-dồn dính
**Bắt buộc kiểm tay:** không riêng (dừng ở Task 8; xem lý do: Task 7 thay phần tính preview chứ không thêm cử chỉ, nhưng được kiểm cùng bài của Task 8 vì lỗi loại này review khó thấy; điều phối có thể nâng thành bắt buộc nếu thấy rủi ro).
**Files:** `ui/home/view/IconGridView.kt`.
- [x] **Step 1: Thay `applyPreview` bằng planner:** gọi `GridInsertPlanner.plan` trên slot của trang (có span) để lấy chuỗi dịch; các item có `Placement` thuộc **trang này** → `springOffsetTo((x − x₀)·pitchX, (y − y₀)·pitchY)`; item tràn sang trang khác → `alpha = 0` (như cũ); widget không bao giờ bị dịch trong preview icon. Trang không widget: hành vi y hệt (đã chứng minh ở Task 1; so ảnh chụp trước/sau ở Step 5).
- [x] **Step 2: Ô đích bị phủ:** `desiredHover` tìm occupant bằng **hình chữ nhật chứa ô** (không chỉ tọa độ neo); ô nằm trong vùng phủ widget → PUSH tại ô S ánh xạ (cùng hàm `PageGeometry.resolveInsertCell` mà DB dùng), không bao giờ MERGE (widget không `mergeable`, đã đúng #13f). `onDrop` dùng cùng ô ánh xạ.
- [x] **Step 3: Đẩy-dồn dính (AN 9e) với ô ánh xạ:** neo là ô S đã ánh xạ; giữ nguyên luật 500 ms / ±8 dp.
- [x] **Step 4: FLIP/gỡ/thêm với trang có widget:** `planEnterExit`, `captureSlideSnapshot`, `onLayout` đã khóa theo `itemId` nên không đổi; kiểm riêng ca gỡ app ở trang có widget.
- [x] **Step 5: Kịch bản kiểm (Live 4, seed của Task 3)** — (a) trang có widget 2×2 ở góc trên trái: kéo một icon từ trang khác thả lên ô nằm **dưới** widget và lên ô **bị widget phủ**: icon vào ô S gần nhất, icon sau nhường chỗ; (b) kéo icon vào mép ô cạnh widget, giữ 0.5 s: đẩy-dồn đi vòng qua widget (không icon nào chạy vào vùng widget); (c) rê qua giữa icon cạnh widget rồi ra: kính hover/phóng thư mục như AN; (d) kéo một icon lên widget (giữ lâu): không kính, không phóng, không tạo thư mục; thả: icon vào ô gần nhất ngoài widget; (e) ẩn một icon bằng "−" ở trang có widget: các icon sau lùi theo S, widget đứng yên; (f) hồi quy trang không widget: lặp lại chuỗi AN 9b–9e (vùng giữa, đẩy 500 ms, dính ở ô neo, ảnh bay hạ cánh, thu về 0 khi gỡ).

### Task 8: Kéo widget — cử chỉ mới, hover, thả, từ chối, ghost page
**Bắt buộc kiểm tay:** **có** — lý do (mục 4.2): cử chỉ chạm/kéo-thả mới (widget là nguồn kéo lớn, điểm neo ≠ ngón tay), loại lỗi review code khó thấy (bài học M7); Task 9 (bảng chọn) và 10 chỉ nên xây trên khi cử chỉ này đạt.
**Files:** `core/ui/DragHelper.kt`, `ui/home/view/IconGridView.kt`, `DockView.kt`, `IconCellView.kt` (ảnh chụp).
- [x] **Step 1: `DragSession`** thêm `isWidget`, `spanX`, `spanY` (mặc định 1×1/false); `beginDragFor` đặt từ `PlacedWidget`.
- [x] **Step 2: Điểm dò (probe point):** với phiên widget, mọi tính toán ô dùng điểm tâm ô đầu-trên-trái của ảnh kéo (`ngón tay − (rộng/2, cao/2) + (cellWidth/2, cellHeight/2)`) rồi `cellAt` + kẹp neo; phiên icon dùng ngón tay như cũ. `desiredHover` bỏ qua MERGE khi `isWidget` (như `isFolder`), vẫn PUSH theo ô neo đã kẹp; luật dính 9e dùng điểm dò.
- [x] **Step 3: Preview:** planner nhận span của item kéo (Task 2): icon bị phủ trượt đi vòng sang ô S còn lại (cùng spring), hiển thị sau 500 ms như AN (quyết định 20, 21); thả: `pendingSettle` = hình chữ nhật widget ở neo; từ chối (chồng widget) → `onDrop` trả `false` (ảnh bay về).
- [x] **Step 4: Dock và vùng ngoài:** `DockView.onDrop` từ chối `isWidget` như `isFolder`; thả ngoài lưới → hủy kéo (alpha trả lại).
- [x] **Step 5: Ghost page & lật trang:** kéo widget tới mép phải trang cuối (có item khác) → trang tạm; thả lên đó → hàng `home_pages` tạo cùng transaction; kéo ngược → bỏ ghost (đã đúng #17, chỉ kiểm).
- [x] **Step 6: Kịch bản kiểm (Live 4)** — (a) vào edit mode, nhấn giữ widget rồi kéo: ảnh widget bay theo ngón tay, ô gốc ẩn, các icon quanh không giật; (b) thả widget lên khoảng trống: widget vào đúng chỗ (neo = góc trên-trái của ảnh), icon không đổi; (c) thả lên vùng có icon (giữ yên 0.5 s trước): icon bị phủ trượt đi vòng, thả: đúng như preview, kill app rồi mở lại: bố cục khớp DB; (d) thả lên widget khác: ảnh bay về chỗ cũ, không đổi gì; (e) thả sát mép phải/dưới: neo kẹp, widget không tràn lưới; (f) kéo qua trang khác bằng cách giữ ở mép (lật trang): thả trang kia; trang cuối: ghost; thả lên ghost: trang mới tạo; kéo ngược: ghost biến mất, không còn hàng `home_pages` thừa (kéo 3 file DB); (g) thả widget lên dock: bay về; (h) kéo icon qua widget (đã kiểm ở Task 7) vẫn đúng; (i) hồi quy AN 9b–9e; (j) kéo-thả bằng menu nhấn giữ icon (đường B) vẫn đúng; (k) nhấn giữ widget **ngoài** edit mode: vào edit mode, rung, sau đó kéo luôn một lần chạm; (l) nhấn giữ khoảng trống (GP) vẫn vào edit mode khi chạm ngoài widget và **không** khi chạm trúng widget; (m) vuốt ngang trên widget lật trang bình thường (không bị widget nuốt).
- [x] **Step 7: Commit** `feat: drag widgets across pages with icons moving out of the way`.

### Task 9: Thêm widget — nút "Tiện ích", bảng chọn, gỡ seed tạm
**Bắt buộc kiểm tay:** không (chạm đơn giản; gộp vào bài kiểm cuối).
**Files:** `ui/home/controller/WidgetPickerController.kt`, `ui/widget/WidgetPickerSheet.kt`, `res/layout/view_widget_picker.xml`, `item_widget_option.xml`, `activity_home.xml`, `HomeActivity.kt`, `HomeViewModel.kt`, `LayoutReconciler.kt` (gỡ seed), `res/values/*`.
- [x] **Step 1: Pill `btn_add_widget`** trong `edit_toolbar` ngay sau "Cài đặt" (cùng kiểu `TonalButton`, `search_scrim`, chữ trắng, chuỗi "Tiện ích"). Nếu chủ dự án không cho sửa `activity_home.xml`: dựng bằng code trong `WidgetPickerController` (`binding.editToolbar.addView(pill, 1)`).
- [x] **Step 2: Bảng chọn:** `BottomSheetDialog` (Material 1.14, có sẵn) chứa lưới thẻ: mỗi thẻ là một `WidgetKind × cỡ`, có bản xem trước vẽ bằng **chính renderer** (cùng mã, cỡ nhỏ), tên và cỡ ("Đồng hồ · 2×2"); bấm một thẻ → `viewModel.onAddWidget(kind, spanX, spanY, pager.currentItem)`, đóng bảng; thoát edit mode hoặc `onDestroy` thì đóng bảng (cửa sổ riêng nên không giữ qua `recreate()`, chấp nhận như hộp thoại wallpaper của M8a).
- [x] **Step 3a: Xác nhận xóa (Q4):** `onQuickHide` của `PlacedWidget` mở `AlertDialog` ("Xóa tiện ích?", "Xóa" / "Hủy") thay vì gọi thẳng `onRemoveWidget`; đóng khi thoát edit mode, `onDestroy`.
- [x] **Step 3: Sự kiện `WidgetAdded(pageId)`:** `HomeActivity.handleEvent` → pager cuộn tới trang đó (không animation nếu cùng trang). Widget mới phình từ 0 (đã có `popIn` của AN 9d cho item mới).
- [x] **Step 4: Gỡ seed tạm của Task 3** và mọi công tắc đo; `git diff <commit Task 8> HEAD -- app` chỉ còn phần Task 9.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) edit mode → "Tiện ích": bảng hiện bốn thẻ có xem trước thật (giờ hiện tại); (b) bấm "Đồng hồ 2×2": widget xuất hiện ở hình chữ nhật trống đầu tiên của trang đang xem, phình ra; (c) trang đầy icon: widget vào trang kế còn chỗ, hoặc trang mới cuối; pager cuộn tới đó; (d) thêm 4 widget liên tiếp, không chồng nhau; (e) "−" trên widget: hộp thoại xác nhận (Hủy: không đổi gì; Xóa: widget thu về 0 và biến mất; trang chỉ có widget đó vẫn còn, trang trống); (f) thêm lại sau khi xóa; (g) xoay `recreate()` bằng đổi chủ đề khi bảng mở: bảng đóng, không crash, edit mode còn; (h) Back khi bảng mở: đóng bảng, vẫn trong edit mode.
- [x] **Step 6: Commit** `feat: add a widget picker to edit mode`.

### Task 10: Kiểm các đường bố cục với widget — đổi cỡ lưới, gỡ app, split-screen, recreate
**Bắt buộc kiểm tay:** không (kịch bản, sửa lỗi nếu có; gộp vào bài kiểm cuối).
**Files:** chỉ sửa lỗi phát hiện (trong vùng đã khai).
- [x] **Step 1 (một phần, xem Nhật ký Task 10): Kịch bản (Live 4, dữ liệu: 4 trang, 2 widget 2×2 + 4×2 ở các trang khác nhau, một trang chỉ có widget)** — (a) Cài đặt → cỡ lưới 4×6 → 4×5 (xác nhận): không icon nào mất (đếm hàng loại 0 trước/sau bằng DB), widget vẫn vừa lưới, không chồng, `ensureFitsGrid` không chạy lại ở lần mở sau (log `LayoutReconciler` không có `outside`); 4×5 → 5×6 và lặp; (b) ép lệch DataStore/DB (`run-as … rm files/datastore/launcher_settings.preferences_pb`, AGENTS.md M8a Task 6) với widget có `spanX = 4`: `ensureFitsGrid` sắp lại, không mất item; (c) gỡ một app ở trang có widget và ở trang không widget: dồn đúng; gỡ app cuối của trang có widget: trang không bị xóa; (d) bỏ ẩn một app (Cài đặt → Ứng dụng đã ẩn): vào ô trống đầu tiên, không đè lên widget; (e) cài app mới khi mọi trang đầy: trang mới ở cuối; (f) chia đôi màn hình rồi kéo thanh chia: widget co theo, dòng phụ ẩn/hiện đúng ngưỡng, không tràn, kéo-thả trong split-screen vẫn đúng; (g) `recreate()` (đổi chủ đề) giữ trang, edit mode, widget; (h) cài đè từ `main` sang nhánh và ngược lại (xem Bài kiểm cuối).
- [x] **Step 2 (không có lỗi nào cần sửa):** sửa lỗi (nếu có), commit `fix: …` theo từng lỗi; ghi nhận lỗi một-lần-không-tái-hiện bằng log tag `G2C` (AGENTS.md M8a Task 6 mẫu) rồi gỡ.

### Task 11: bấm widget mở app Đồng hồ / Lịch (chủ dự án chốt Q3 = có, 2026-11-05)
**Bắt buộc kiểm tay:** không.
**Files:** `IconGridView.kt` (`onWidgetClick`), `HomeViewModel.kt`/`AppRepository` (mở Intent), có thể `AndroidManifest.xml` (khối `<queries>` nếu cần `resolveActivity`; ưu tiên `startActivity` trong `try/catch ActivityNotFoundException` để khỏi đụng manifest).
- [x] **Step 1:** bấm widget (ngoài edit mode) → Đồng hồ: `AlarmClock.ACTION_SHOW_ALARMS`; Lịch: `Intent.ACTION_VIEW` với `content://com.android.calendar/time/<now>`; không có app thì Toast `MenuActionFailed`.
- [ ] **Step 2 (chưa có bản ghi kết quả): Kịch bản kiểm (Live 4)** — (a) bấm đồng hồ/lịch: app tương ứng mở; (b) trong edit mode bấm widget: không mở; (c) máy không có app lịch: Toast, không crash.
- [x] **Step 3: Commit** `feat: open the system clock or calendar when a widget is tapped`.

### Task 12: Đóng nhánh
**Bắt buộc kiểm tay:** không.
- [x] **Step 1 (2026-11-05):** knowledgement hoàn chỉnh (`docs/knowledgements/G2c-clock-calendar-widgets.md`, sơ đồ khớp luồng thật gồm 3.5 kéo thẻ từ bảng chọn; mục 8 điền kết quả Task 1, 2, 3, 6, 8, 10 và một phần Task 13, ca chưa chạy ghi rõ; mục 9 bài học). Còn lại: điền kết quả lần kiểm tay thứ hai sau khi chủ dự án chạy.
- [ ] **Step 2 (chờ chủ dự án, không viết lại lịch sử):** rebase `main` mới nhất (GP, M8d, GS đã merge), build xanh, `git diff --stat` kiểm Review Focus 11–12; điền "Bằng chứng Review Focus", "Kết quả kiểm chứng", "Đề xuất cập nhật tài liệu chung".
- [ ] **Step 3:** chuyển PR sang Ready for review sau khi điều phối review toàn nhánh.

---
### Task 13: Hoạt ảnh còn thiếu và kéo widget từ bảng chọn xuống lưới (chủ dự án yêu cầu 2026-11-05 sau bài kiểm tay đạt)
**Bắt buộc kiểm tay:** có (lần kiểm tay thứ hai của chủ dự án) — lý do (mục 4.2): cử chỉ kéo-thả mới, và chuyển giao chạm giữa hai cửa sổ chỉ máy thật mới trả lời được.
**Files:** `core/ui/DragHelper.kt` (`DragSession`: `source` nullable, `newWidgetKind`, `returnRect`; `drop` bay về), `ui/home/view/IconGridView.kt` (press co, popIn trang trống, `returnRectOf`, nhánh thả widget mới), `ui/home/controller/WidgetPickerController.kt` (cử chỉ thẻ, `PickerSheet`, ảnh), `ui/home/HomePagerAdapter.kt` + `HomePagerController.kt` (nối `onNewWidgetDrop`), `ui/home/HomeViewModel.kt` (`onNewWidgetDropped`), `domain/repository/LayoutRepository.kt` + `data/layout/LayoutRepositoryImpl.kt` + `LayoutReconciler.kt` (`addWidgetAt`), `domain/usecase/AddWidgetUseCase.kt` (nạp chồng). Không sửa schema, không sửa `core/glass`.

**Kiểm toán hoạt ảnh (đọc code 2026-11-05, trước và sau Task 13).** Mọi hoạt ảnh dùng lại mảnh của AN: `SpringProgress`, FLIP theo `itemId`, `LeavingIcons`, `popIn`, spring `PRESS`/`SETTLE`/`SLIDE`/`EXIT`. Không thêm kiểu chuyển động mới.

| # | Trạng thái | Trước Task 13 | Sau Task 13 |
|---|---|---|---|
| 1 | Thêm bằng chạm, trang đang xem còn chỗ | Có: widget phình từ 0 (`popIn`, vì `growIds`) | Giữ |
| 2 | Thêm lên trang đang trống | **Không** (`canSlide` đòi trang đã có item, để lần nạp đầu không phình cả lưới) | **Thêm:** trang trống nhận một trang toàn widget thì cũng `popIn`; trang trống nhận icon vẫn không |
| 3 | Thêm tạo trang mới | Pager cuộn có hoạt ảnh (`setCurrentItem(.., true)`); widget hiện sẵn trong trang mới (như mọi trang vừa bind) | Giữ |
| 4 | Cuộn tới trang sau khi thêm | Có | Giữ (đã sửa chờ danh sách áp xong ở review) |
| 5 | Xóa widget | Có: thu về 0 quanh tâm widget (`LeavingIcons`, spring `EXIT`) sau hộp thoại xác nhận | Giữ |
| 6 | Icon khép lại sau khi xóa widget | Không có và không cần: xóa để lại ô trống (cùng luật ẩn app, `removeItem` không dồn) nên không icon nào dịch | Giữ, ghi rõ là chủ ý |
| 7 | Nhấc lên (pick up) | Ảnh kéo hiện ngay tại ngón tay, ô gốc ẩn; icon và thư mục cũng không có hiệu ứng nhấc | Giữ giống nhau, không thêm |
| 8 | Giữ ngón tay (press) | **Không** (widget `pressFeedback = false`) trong khi ô thư mục co 0.95 (quyết định 13 của AN: phần tử kính co) | **Thêm:** widget co như ô thư mục (spring `PRESS`) |
| 9 | Đẩy và trượt icon quanh widget khi kéo | Có: `springOffsetTo` từ planner, FLIP lúc thả | Giữ |
| 10 | Thả được nhận | Có: ảnh bay hạ cánh vào cả hình chữ nhật (spring `SETTLE`) | Giữ |
| 11 | Thả bị từ chối bay về chỗ cũ | **Không**: `DragHelper.drop` gọi `endDrag()` (ảnh biến mất tức thì) với MỌI item; chú thích cũ ghi "bay về" là sai | **Thêm cho mọi item kéo từ lưới (icon, thư mục, widget):** `DragSession.returnRect` + `DragHelper.drop` hạ cánh về chỗ cũ bằng đúng spring `SETTLE`, ô gốc ẩn tới khi bay xong; cũng áp cho "thả đúng ô cũ". Không có chỗ nhìn thấy (đã lật sang trang khác) thì như cũ |
| 12 | Bảng chọn mở/đóng | `BottomSheetDialog` mặc định (trượt lên/xuống) | Giữ; khi bắt đầu kéo thẻ: mờ dần 120 ms (`PickerSheet.startForwarding`) |
| 13 | Vào/ra edit mode | Rung (`Jiggler`, 1° cho widget); dấu "−" và thanh công cụ hiện/ẩn tức thì, y như icon | Giữ giống nhau |
| 14 | Nội dung widget đổi mỗi phút | Không hoạt ảnh (chủ ý: giống widget iOS thường) | Giữ |
| 15 | Thả widget kéo từ bảng chọn | (chưa có) | Ảnh bay hạ cánh vào hình chữ nhật, ô mới ẩn tới khi bay xong (`settlingItemId` = id tạm), đổi sang id thật không thu rồi phình |

**Vì sao chọn thiết kế kéo từ bảng chọn.**
- **Vấn đề cần giải:** (1) chuỗi chạm bắt đầu trong cửa sổ của `BottomSheetDialog` chỉ được giao tiếp cho cửa sổ đó; đóng nó giữa chừng thì chuỗi bị CANCEL và Activity không nhận MOVE/UP (đọc tài liệu input Android; bài học M7 Task 7: phiên kéo phải do `Activity.dispatchTouchEvent` nhận). (2) Widget chưa có hàng DB nên mọi chỗ dùng `itemId` (ẩn ô gốc, `hiddenItemId`, kế hoạch, lạc quan) phải chịu được một item không tồn tại. (3) Giữ nguyên hành vi kéo của AN (hover 500 ms, đẩy-dồn dính, lật trang, ghost page, từ chối chồng widget, dock từ chối).

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Giữ bảng, ẩn nó và chuyển tiếp chạm** | `PickerSheet.dispatchTouchEvent` chuyển mọi sự kiện tới `Activity.dispatchTouchEvent` khi đang kéo; bảng mờ đi (không làm tối nền) nhưng cửa sổ còn sống tới lúc thả, rồi `onFinished` đóng | Giữ giao diện bảng chủ dự án đã kiểm; thêm ~120 dòng; đúng đường tiếp quản `HomeTouchRouter` của mọi phiên kéo khác (M7 Task 7) | Phụ thuộc cửa sổ bảng còn nhận chuỗi chạm (suy luận từ tài liệu, **chưa kiểm trên máy**); cửa sổ trong suốt phủ Home trong lúc kéo | **Chọn** |
| B. Thay bảng bằng lớp phủ trong `home_root` | View thường, cùng cửa sổ Activity nên chạm thuộc Activity, không cần chuyển giao | Chắc nhất về chạm (cùng cách menu nhấn giữ icon, M7 Task 10); không cửa sổ thừa | Viết lại bảng (inset, Back theo LIFO của `HomeBackController`, hoạt ảnh trượt bằng spring): ~250 dòng và đổi giao diện đã được kiểm | Dự phòng nếu A hỏng ở máy |
| C. Chỉ bấm để thêm, không kéo | Giữ như Task 9 | Không rủi ro | Không đáp ứng yêu cầu chủ dự án | Không |
| D. `View.startDragAndDrop` của framework | Hệ thống lo chạm | Chuyển giao chạm miễn phí | Dự án đã bỏ ở M7 (ảnh kéo, lật trang, hover tự viết); phải viết lại mọi target | Không |

**Cách phiên kéo biểu diễn widget chưa có hàng:** `DragSession` thêm `newWidgetKind` (mã loại, null = item có sẵn) và dùng `itemId` là id TẠM âm (`-(2^40) - n`, không đụng id hàng thật > 0 hay id thư mục tạm `-targetId`). `source` nullable (chưa nơi nào đọc nó), `returnRect = null` (không có chỗ cũ để bay về: thả không được nhận thì ảnh biến mất, không hàng nào được thêm). Hover, đẩy-dồn và thả của `IconGridView` dùng lại nhánh widget của Task 8 (`isWidget` + span) vì item không có trong `items` nên mọi phép tra theo id đều rỗng.

**Cách thả tạo hàng:** `IconGridView.onDrop` gọi `onNewWidgetDrop` thay vì `onItemDropped` → `HomeViewModel.onNewWidgetDropped`: lạc quan thêm một `PlacedWidget` mang id tạm vào trang đích rồi chạy `planGridOptimistic` (cùng hàm với kéo widget có sẵn) để đẩy icon; ghi bằng `AddWidgetUseCase` → `LayoutReconciler.addWidgetAt` trong `mutex` và MỘT transaction: lập kế hoạch bằng `GridInsertPlanner.plan` với id kế hoạch `Long.MIN_VALUE` (không trùng hàng nào), tạo trang tạm nếu thả lên ghost page và trang tràn nếu kế hoạch đòi, chèn hàng widget ở chỗ kế hoạch ghi, dời các icon bị đẩy. Chồng widget khác: từ chối, không ghi gì (trang tạm chỉ được tạo sau khi kế hoạch được nhận). Đối chiếu DB đổi id tạm thành id thật, lưới coi là thay tại chỗ cùng ô nên không thu rồi phình.

**Đánh đổi chấp nhận:** thả bằng kéo ĐƯỢC đẩy icon (người dùng chọn chỗ, như kéo widget có sẵn); thêm bằng chạm vẫn không bao giờ đẩy (quyết định 10, Q5). Ảnh kéo của widget mới là nền phẳng bán trong suốt (không phải kính vì chưa gắn vào cửa sổ), nên khi hạ cánh chuyển sang kính. **Xét lại khi:** hand test cho thấy chuyển tiếp chạm không ổn định trên máy thật → phương án B.

- [x] **Step 1:** nối chạm: `PickerSheet` + `CardDragGesture` (nhấn giữ rồi di vượt slop), ảnh đúng cỡ ô, phiên `DragSession` với `newWidgetKind`, `HomeViewModel.onDragStarted`. **Đã kiểm trên máy (Oppo A3x, `g2c-task13-clean.apk`, điều phối, 2026-11-05):** nhấn giữ thẻ Đồng hồ 2×2 rồi kéo lên bằng `input motionevent`: bảng mờ đi, widget bám ngón tay, icon nhường chỗ; chuyển tiếp chạm từ cửa sổ bảng hoạt động, giữ phương án A.
- [x] **Step 2 (kiểm tay đạt, xem Nhật ký):** thả tạo hàng (`onNewWidgetDropped`, `addWidgetAt`), hủy sạch khi thả ngoài lưới / lên dock / chồng widget (không hàng nào được thêm, `onDragCancelled`). **Đã kiểm:** MỘT lần thả được nhận trên Oppo (kịch bản `input motionevent`): widget hạ cánh ở ô đã snap, kính bật, vẫn ở edit mode, không crash. **Chưa kiểm:** thả bị từ chối, hủy (ngoài lưới, dock, chồng widget), ghost page, hồi quy ảnh bay về của icon và thư mục; để cho kiểm của điều phối và lần kiểm tay thứ hai của chủ dự án.
- [x] **Step 3:** hoạt ảnh thiếu: popIn trang trống, press co widget, ảnh bay về khi thả bị từ chối hoặc về đúng ô cũ.
- [x] **Step 4: Kịch bản kiểm (Live 4, lần kiểm tay thứ hai của chủ dự án)** — (a) edit mode, "Tiện ích", nhấn giữ thẻ Đồng hồ 2×2 ~0.5 s (rung nhẹ) rồi di: bảng mờ đi, widget đúng cỡ ô bám ngón tay, vẫn ở edit mode; (b) thả lên chỗ trống: widget bay vào ô, hàng được tạo, kill app mở lại vẫn còn; (c) giữ yên 0.5 s trên vùng có icon: icon trượt đi vòng, thả: đúng như xem trước; (d) thả lên widget khác, lên dock, ngoài lưới (pill, thanh trạng thái): ảnh biến mất, KHÔNG hàng nào được thêm (đếm hàng `itemType = 4` trước và sau); (e) lật trang bằng cách giữ ở mép, ghost page ở trang cuối: thả lên đó tạo trang mới; kéo ngược ghost biến mất; (f) bấm nhanh một thẻ vẫn thêm vào chỗ trống đầu tiên; nhấn giữ rồi nhả không di: không thêm gì; (g) vuốt dọc bảng khi chưa giữ đủ lâu: bảng cuộn bình thường, không kéo; (h) thả icon ra ngoài lưới, hoặc kéo widget có sẵn lên widget khác: ảnh bay về chỗ cũ (không biến mất tức thì); thả đúng ô cũ: ảnh bay vào ô; (i) thêm widget lên trang đang trống: widget phình từ 0; (j) nhấn giữ widget: co nhẹ như ô thư mục; (k) hồi quy: kéo icon và thư mục thường (thả được, bị từ chối, lên thư mục), lật trang khi kéo.
- [x] **Step 5: Commit** `feat: drag a widget from the picker onto the grid and animate rejected drops`.


## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: Vsmart Live 4 (API 30), mức kính chủ dự án đang dùng (`FORCE_HIGH`, ghi rõ); Oppo A3x chỉ cho phần kính nếu Task 6 đã đo ở đó. Dữ liệu: bố cục thật có thư mục, dock, ≥ 3 trang. Mở: `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity`. Thứ tự dự kiến (điều phối sắp lại theo thao tác):
1. **Cài đè (khóa schema, 5.3):** cài `main` trước, tạo thư mục, đổi vài vị trí, ẩn một app; cài nhánh G2c lên trên: bố cục nguyên vẹn. Thêm 2 widget. Cài `main` đè lại: Home mở bình thường, widget không hiện, không crash, icon đúng chỗ (hàng loại 4 bị bỏ qua); cài lại G2c: widget còn đó. (Kiểm CUỐI cùng trong vòng kiểm vì giữ khóa schema — Workflow mục 6.)
2. Bảng chọn, thêm bốn biến thể (Task 9), vị trí đặt đầu tiên, trang đầy.
3. Hiển thị: giờ/ngày đúng; sáng/tối; vi/en; 12/24h; đổi múi giờ; qua ranh giới phút và nửa đêm; tắt/bật màn hình (Task 5).
4. Kéo widget: chỗ trống, chỗ có icon, widget khác, mép/kẹp, lật trang, ghost, dock (Task 8).
5. Kéo icon quanh widget: ô dưới/phủ, đẩy-dồn đi vòng, không gộp thư mục lên widget (Task 7).
6. "−" xóa widget (có xác nhận); nhấn giữ vào edit mode; vuốt ngang trên widget lật trang; nhấn giữ khoảng trống (GP).
7. Cỡ lưới 4×5/4×6/5×6, split-screen, gỡ/cài app, bỏ ẩn (Task 10).
8. Hồi quy: AN (hover, đẩy 500 ms, dính ô neo, hạ cánh, thu về 0), thư mục, Library, tìm kiếm, kính panel.
9. Đo (điều phối): số đo Task 6.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Task 8 (kéo widget, Task 7, hồi quy AN): chủ dự án đạt mọi mục, Vsmart Live 4, 2026-11-05, `42105ff`. Task 13 (kéo thẻ từ bảng chọn, hoạt ảnh, ảnh bay về, hồi quy): chủ dự án đạt, Live 4, 2026-11-05, `37157fe`. Bản sửa review 2 (`3e82320`): điều phối kiểm bằng kịch bản (Back, Recents, recreate khi đang kéo: không hàng nào được thêm). Task 6 (kính, Live 4 và Oppo) và Task 10 (cỡ lưới, "−", recreate, gỡ app mô phỏng, Oppo): xem Nhật ký. Chưa chạy: split-screen, nút nguồn, cuộc gọi, giới hạn đã biết số 5.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-05 | Plan | Đọc `MultiAgent-Workflow`, `AGENTS.md`, `room-schema`, `project_structure`, `Launcher-Features` TT#13, plan AN và GP; đọc code planner, reconciler, repository, ViewModel, `IconGridView`, `IconCellView`, `DragHelper`, `GlassPolicy/Budget/Environment`. Viết plan | — | Chờ điều phối rà và chủ dự án duyệt. Chưa viết code |
| 2026-11-05 | Plan | Chủ dự án duyệt plan; ghi Q1–Q12 (Q4 đổi: xác nhận trước khi xóa widget; Task 11 vào phạm vi) | 88e48f2 | Duyệt 2026-11-05 || 2026-11-05 | 1 | `PageGeometry` (dãy ô tự do S), `GridInsertPlanner` / `GridRemovePlanner` đi dọc S, `Slot` có span, `GridRemovePlanner.plan` nhận `rows` (3 nơi gọi chỉ thêm đối số). KDoc `GridInsertPlanner` sửa cho khớp code (dồn sát đuôi). Harness tạm KHÔNG commit (`G2cHarness*.kt` + dòng gọi trong `LauncherApp`) | 8dcfaf9 | Điều phối cài harness trên Live 4: `T1 widget-free equality: insert N=3000 diff=0 ; remove diff=0`; PASS W1, W1b, W2, W3, E, E2, F, INV W2/W3. Chờ review để tick || 2026-11-05 | 2 | `GridInsertPlanner` nhánh widget (kẹp neo, từ chối chồng widget, đuôi dồn sát vào ô tự do, khối tràn sang trang kế), `GridReflowPlanner` đóng gói vị trí đầu tiên còn vừa (+ `spanChanges`), `WidgetPlacementPlanner.firstFit`, `WidgetKind`. **Đổi so với bản duyệt:** từ chối biểu diễn bằng `Plan.rejected` thay vì `Plan?` (điều phối đồng ý). Harness T2 tạm cũng KHÔNG commit | (xem commit Task 2) | Harness Live 4: reflow equality N=3000 diff=0; random widget pages N=4000 widgetDrops=556 rejected=265 violations=0; 25 PASS. Tick 2026-11-05 |
| 2026-11-05 | 1 | `PageGeometry` (dãy ô tự do S), `GridInsertPlanner` / `GridRemovePlanner` đi dọc S, `Slot` có span, `GridRemovePlanner.plan` nhận `rows` (3 nơi gọi chỉ thêm đối số). KDoc `GridInsertPlanner` sửa cho khớp code (dồn sát đuôi). Harness tạm KHÔNG commit | 8dcfaf9 | Live 4 (điều phối cài harness): `T1 widget-free equality: insert N=3000 diff=0 ; remove diff=0`; PASS W1, W1b, W2, W3, E, E2, F, INV W2/W3. Tick 2026-11-05 |
| 2026-11-05 | 2 | `GridInsertPlanner` nhánh widget (kẹp neo, từ chối chồng widget, đuôi dồn sát vào ô tự do, khối tràn sang trang kế), `GridReflowPlanner` đóng gói vị trí đầu tiên còn vừa (+ `spanChanges`), `WidgetPlacementPlanner.firstFit`, `WidgetKind`. **Đổi so với bản duyệt:** từ chối biểu diễn bằng `Plan.rejected` thay vì `Plan?` (điều phối đồng ý). Harness tạm KHÔNG commit | 79c1407 | Live 4: reflow equality N=3000 diff=0; random widget pages N=4000 widgetDrops=556 rejected=265 violations=0; 25 PASS, 0 FAIL. Tick 2026-11-05 |
| 2026-11-05 | 3 | `ITEM_TYPE_BUILTIN_WIDGET = 4`, `PlacedWidget`, `assemble` đọc hàng loại 4 (bỏ qua mã/cỡ lạ, không xóa hàng), `LayoutReconciler`: `Slot` mang span ở mọi chỗ, `moveItem` truyền span và trả false khi widget bị từ chối, `reflowInTransaction` bỏ thoát sớm (+ ghi span bị kẹp), `ensureFitsGrid` cộng span, `deleteAndCompact` bỏ chốt toàn lưới, `addWidget` (hình chữ nhật trống đầu tiên, không đẩy icon), `AddWidgetUseCase`/`RemoveWidgetUseCase`; `IconGridView` tạm bỏ qua ô widget. Seed tạm (`LauncherApp.kt`, KHÔNG commit) | e9c22b5 | Live 4 (seed APK, dữ liệu thật của chủ dự án): đồng hồ page id 1 (0,4) 2×2; lịch page id 6 (0,0) 4×2 trên trang mới vì không còn hình chữ nhật 4×2 trống; đúng 2 hàng `itemType=4`, `className` đúng, `packageName`/`appWidgetId` NULL, `user_version` 1, 0 ô chồng, `app/schemas` không đổi; không có xóa của `catchUp`; `main` cài đè mở bình thường. Bước gỡ app thật dời sang kiểm sau Task 5 bằng dấu "−". Tick 2026-11-05 |
| 2026-11-05 | merge | Merge `origin/main` (GP đã merge) vào nhánh bằng merge thường, không xung đột; build xanh. Điều phối: vòng rebase cuối do chủ dự án làm lúc đóng nhánh | 118bc7c | |
| 2026-11-05 | 4 | `HomeViewModel`: `Slot` mang span ở `removeAndCompact` và `planGridOptimistic`; `planGridOptimistic` trả null khi planner `rejected` (UI không đổi, rồi về DB); `onRemoveWidget(itemId)` (lạc quan bỏ widget, không dồn, không xóa trang; gọi SAU xác nhận ở Task 9); `onAddWidget(kind, spanX, spanY, preferredPageIndex)` (không lạc quan, phát `HomeEvent.WidgetAdded(pageId)`); `HomeEvent.WidgetAdded`; `HomeActivity.handleEvent` thêm một nhánh `WidgetAdded -> return` (Task 9 thay bằng cuộn pager). **Đổi so với plan:** `onQuickHideTapped` KHÔNG rẽ nhánh sang xóa widget (vì Q4 đòi xác nhận): nó bỏ qua widget như cũ, hộp thoại ở Task 9 gọi `onRemoveWidget` | 9cb74be | Build xanh. Điều phối nhận Task 4 (kể cả việc `onQuickHideTapped` không đụng widget), tick 2026-11-05 |
| 2026-11-05 | 5 | `ui/widget/`: `WidgetRenderer` (+ `WidgetRenderers.create`), `ClockAnalogRenderer` (2×2: mặt, 12 vạch, kim giờ/phút, không kim giây), `ClockDigitalRenderer` (4×2), `CalendarSmallRenderer` (2×2), `CalendarMediumRenderer` (4×2, lưới tháng, đầu tuần theo `WeekFields`), `WidgetFormat` (giờ theo 12/24, ngày theo locale, viết hoa chữ đầu), `WidgetText` (bút chữ, `fitWidth`, `baselineAround`), `WidgetTicker` (nhịp theo ranh giới phút, receiver TIME_SET + TIMEZONE_CHANGED chỉ khi có widget hiện). `IconCellView` chế độ widget (`bindWidget`, `tickWidget`, `drawWidget`, nền kính `BackdropBlurDrawable` với `GlassStyle` tự dựng hoặc phẳng, tâm co ở giữa widget, dấu "−" lùi vào góc, `jiggleScale`). `IconGridView`: ô theo span, `bindWidgetCell`, kính trừ ngân sách ô thư mục, dấu "−" cho widget, nhấn giữ = vào edit mode, `LeavingIcons` thu quanh tâm widget. `Jiggler` biên độ 0.5. Strings/colors khối `widget_`. Chưa kéo được, chưa xóa được widget (Task 8, 9) | 260a803 | Build xanh. Chờ điều phối kiểm trên Live 4 (sau phép đo đang chạy) |
| 2026-11-05 | 7 | `IconGridView`: `applyPreview` lấy kết quả THẲNG từ `GridInsertPlanner.plan` (một trang, tràn sang trang kế thì ô alpha 0 như cũ) thay vì tự tính chuỗi dồn bằng chỉ số đọc; `resolveInsertCell` (ô bị widget phủ -> ô tự do kế sau, cùng `PageGeometry` với DB và ViewModel) dùng ở `desiredHover` (PUSH chỉ khi ô đã ánh xạ có icon, không phải widget) và `onDrop` (ô thả, ô hạ cánh, kiểm "thả đúng ô cũ"); đẩy-dồn dính giữ nguyên vì neo là ô đã ánh xạ. Widget không bao giờ là đích gộp (đã đúng: `mergeable`). Task 7 chưa có bản ghi kiểm tay riêng: gộp vào lượt kiểm kéo-thả của Task 8 (kể cả hồi quy AN 9b–9e trên trang không widget) | 77b53e8 | Build xanh. Seed tạm nay thêm đủ 4 biến thể |
| 2026-11-05 | 8 | `DragHelper.DragSession` thêm `isWidget`, `spanX`, `spanY`; `IconGridView.beginDragFor` đặt từ `PlacedWidget` và `IconDragGesture.install` của widget gọi `beginDragFor` thật. Ảnh kéo căn GIỮA theo ngón tay nên neo = ô gần góc trên-trái của ảnh nhất (`widgetAnchor`, kẹp vào lưới); điểm dò (`widgetProbeX/Y` = tâm ô trên-trái của ảnh) thay ngón tay cho ngưỡng dính ±8 dp; `desiredHover` của widget: không bao giờ gộp, PUSH khi hình chữ nhật phủ icon và không chồng widget khác; `applyPreview` truyền span; `onDrop`: neo (hoặc ô neo đẩy-dồn đang dính), từ chối khi chồng widget khác (trả false, ảnh kéo biến mất và widget hiện lại chỗ cũ), hạ cánh vào cả hình chữ nhật (`widgetRectOnScreen`); `DockView` từ chối và không xem trước dồn dock cho widget. Ghost page và lật trang dùng đường chung, không đổi | 354b3d3 | Build xanh. **Bắt buộc kiểm tay (chủ dự án)**, gộp cả Task 7 và hồi quy AN. Chờ lượt kiểm |
| 2026-11-05 | 9 | `activity_home.xml`: pill `btn_add_widget` ("Tiện ích") ngay sau "Cài đặt". `WidgetPickerController` (bảng chọn `BottomSheetDialog` dựng bằng code: mỗi loại một hàng, hai thẻ 2×2 và 4×2 theo tỉ lệ bề rộng 1:2, mỗi thẻ là `WidgetPreviewView` vẽ bằng chính renderer của widget; bấm thẻ -> `viewModel.onAddWidget(kind, spanX, spanY, homePager.currentItem)`; đóng khi thoát edit mode và `onDestroy`). `IconGridView.confirmRemoveWidget` (`MaterialAlertDialogBuilder` "Xóa tiện ích?" / "Xóa" / "Hủy", đóng khi lưới tách cửa sổ) -> `onWidgetRemove` qua `HomePagerAdapter` và `HomePagerController` -> `HomeViewModel.onRemoveWidget`. `HomeActivity`: dựng/đóng controller, `WidgetAdded` -> `scrollToPage` (chờ tối đa 1 s cho tới khi trang có trong state). Seed tạm gỡ (`LauncherApp.kt` về bản commit); cờ phẳng chỉ còn là diff đã lưu ngoài cây. **Đổi so với plan:** không tạo `view_widget_picker.xml` / `item_widget_option.xml` (bảng chọn dựng bằng code, ít file hơn, dimens/strings vẫn ở khối `widget_`) | 1e9fce2 | Build xanh, APK sạch (không seed, không cờ). Chờ kiểm trên Live 4 |
| 2026-11-05 | fix (Task 5) | **Vệt ngang trong kính widget 4×2 (Live 4, trang vuốt tới).** Nguyên nhân (đọc code, điều phối xác nhận sau khi sửa): `IconGridView.invalidateGlass()` thoát sớm khi trang không có thư mục (`glassFolderIds` rỗng), nên widget trên trang vuốt tới giữ lát cắt nền ghi lúc trang còn nằm ngoài màn hình (lệch ngang đúng một bề rộng màn hình); `BackdropBlurDrawable` lấy `getLocationOnScreen` lúc ghi display list, lát cắt rơi ngoài bản mờ, shader CLAMP kéo cột cuối thành vệt ngang. Sửa: `hasWidgets` trong `setItems`, `invalidateGlass` chỉ thoát sớm khi không thư mục kính và không widget. Không sửa `core/glass`. Hệ quả cho Task 6: số đo cuộn trước bản sửa đánh giá thấp chi phí kính widget, phải đo lại | ab27de6 | Điều phối xác nhận trên Live 4: kính 4×2 mềm đúng |
| 2026-11-05 | fix (Task 5) | **Chữ phụ khó đọc trên vùng ảnh nền sáng.** Giữ vai trò kính `FOLDER_CELL` (quyết định Q2). `widget_text_dim` 70% → 90% trắng; bóng chữ lấy bán kính `icon_label_shadow_radius` (4dp, trước chỉ 2 px) và 70% đen (nhãn icon 60%). Chưa tint kính đậm hơn | 4423c04 | Chờ điều phối kiểm mắt; nếu chưa đủ thì hỏi chủ dự án về tint đậm hơn |
| 2026-11-05 | 11 | `AppRepository.openClock()` / `openCalendar()` (data: `AlarmClock.ACTION_SHOW_ALARMS`; Lịch: `makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_CALENDAR)` rồi dự phòng URI `content://com.android.calendar/time/<ms>`; `startActivity` trong `try/catch` nên KHÔNG đụng manifest). `HomeViewModel.onWidgetTapped(kind)` (Toast chung `MenuActionFailed` nếu không có app). `IconGridView.onWidgetClick` qua `HomePagerAdapter`/`HomePagerController`; trong edit mode bấm widget vẫn bị nuốt | c36f1b0 | Build xanh. Chờ kiểm trên Live 4 |
| 2026-11-05 | review | Bốn điểm của review độc lập, đã đối chiếu code: (1) ĐÚNG: `HomeActivity.scrollToPage` chạy cùng lần phát `uiState` với `render` trong khi `submitList` diff ở luồng nền, nên `setCurrentItem` bị kẹp rồi callback đặt lại trang cũ. Sửa: `HomePagerController.scrollToPage(pageId)` cuộn ngay nếu `adapter.currentList` đã có trang, ngược lại giữ `pendingScrollPageId` (hết hạn 3 giây) và cuộn trong callback `submitList` (như `pendingFlipToNewPage`); bỏ coroutine chờ ở `HomeActivity`. (2) ĐÚNG: `popIn`/`retargetScale` đặt tâm tường minh và không bị đặt lại khi ô tái dùng: thêm `centerPivotIfIdle()` ở `bind`, `bindFolder`, `bindWidget`, `onSizeChanged` (bỏ qua khi đang giữ, phóng hoặc co để khỏi giật giữa cử chỉ). (3) ĐÚNG: receiver `TIME_SET`/`TIMEZONE_CHANGED` thêm `scheduleNext()`. (4) ĐÚNG: tên tháng và thứ dùng `*_STANDALONE`. (5) `ACTION_SHOW_ALARMS` cần `SET_ALARM`: theo hiểu biết của tôi (suy luận chưa kiểm chứng) KHÔNG cần quyền cho hành động hiển thị; thêm dự phòng `startFirstInstalled` mở app đồng hồ quen thuộc qua `LauncherApps` nếu `startActivity` thất bại (danh sách package là suy luận) | 53688ec | Build xanh. Chờ điều phối thử trên Live 4 |
| 2026-11-05 | 8 (kiểm tay) | Chủ dự án chạy bài kiểm kéo-thả widget (kèm Task 7 và hồi quy AN) trên Vsmart Live 4: **đạt mọi mục** | 42105ff | Điều phối báo 2026-11-05. Ghi vào "Kết quả kiểm chứng" |
| 2026-11-05 | merge | Merge `origin/main` (GP, LC) vào nhánh; một xung đột ở `LauncherAppsDataSource.kt` (cả `openClock/openCalendar` của G2c và lớp `Loaded` của LC): giữ cả hai | 70dd930 | Build xanh |
| 2026-11-05 | 6 | Phép đo do điều phối chạy, bản `g2c-fix-flag-seed4.apk` (đã sửa vệt ngang), cảnh vuốt "S2", mỗi cấu hình 2 lượt. **Live 4 (`FORCE_HIGH`), 2 widget trên trang vuốt:** kính so với phẳng (thứ tự kính, phẳng, kính, phẳng, kính): janky 2.19–5.42% so với 2.95–4.94%; gfx p95 14–16 so với 15–16 ms; khung > 33 ms 2–5 so với 2–3: cùng dải. `main` so với bản widget (`g2c-task11-clean.apk`, xen kẽ): janky 1.17–4.22% so với 1.83–4.68%; gfx p95 11–16 so với 13–16 ms; khung > 33 ms 2–3 so với 2–5: trong nhiễu (lượt 1 thấp hơn lượt 2 ở cả hai bản). **Oppo A3x, trường hợp xấu nhất** (bố cục thử 4 widget trên hai trang vuốt: cặp 2×2 trang 1, cặp 4×2 trang 2; thứ tự main, kính, phẳng, kính, phẳng, main): janky main 0.90–0.94%, phẳng 1.32–1.41%, kính 1.36–1.44%; framestats p95 13.7–14.1 / 14.9–15.2 / 16.0–16.1 ms; khung > 33 ms 2–3 / 3–5 / 8–11; record p95 2.3 / 2.2–2.3 / 2.9–3.1 ms. `main` bỏ qua hàng widget và bố cục thử ít item hơn nên `main` là cảnh nhẹ hơn, coi là sàn. Kính so với `main`: +0.5 điểm janky, +2.1 ms p95, trong ngưỡng D10 của GL (+1.0, +4). Kính so với phẳng: +1 ms p95, khoảng +5 khung > 33 ms mỗi lượt ~3100 khung. **Quyết (D5): GIỮ KÍNH** (phương án A), không cần bộ đệm bitmap nội dung widget, không thêm cờ vào mã. Chưa đo: cảnh rung S3 cộng vuốt có widget, cảnh đứng yên | (không có mã: cờ đo chưa từng vào commit) | Task 6 đóng 2026-11-05 |
| 2026-11-05 | 10 | Điều phối chạy trên Oppo A3x (`g2c-task13-clean.apk`, bố cục thử 4 widget ở trang 1 và 2, 326 item, 14 trang). **Đổi cỡ lưới qua Cài đặt thật, đối chiếu DB sau mỗi lần:** 4×6 → 4×5: 326 item, 17 trang, chồng 0, ngoài lưới 0; 4×5 → 5×6: 326 item, 11 trang, chồng 0, ngoài 0; 5×6 → 4×6: 326 item, 14 trang, chồng 0, ngoài 0, widget vẽ đúng ở 5×6. **Ẩn bằng "−" một icon ở trang có widget:** 325 item, ẩn 29 → 30, các icon sau lùi dọc ô tự do, cả bốn widget đứng yên. **Recreate** bằng `cmd uimode night yes` khi bảng chọn đang mở: bảng đóng, vẫn ở edit mode, 0 `WindowLeaked`, không crash, theme đã khôi phục. Ghi nhận: sau vòng đi-về cỡ lưới widget 4×2 thứ hai nằm ở (0,3) thay vì (0,2) ban đầu: đúng thiết kế (re-flow một chiều, nén khe, D1–D3 của M8a) | (không có mã) | **Gỡ app mô phỏng qua `catchUp` (Oppo, điều phối, sau đó):** app đầu trang 1 trỏ tới package không tồn tại, nên `catchUp` xóa hàng và chạy `deleteAndCompact`: id app trang 1 [31,32,33,45,370,47,249,34,35,36] → [32,33,45,370,47,249,34,35,36,398], mỗi icon lùi một ô, hai widget 2×2 đứng yên ở y=4, chồng 0, ngoài lưới 0 (398: app thật bị đẩy, `placeNew` đặt lại); không phải gỡ thật, `removePackages` chưa kích hoạt. **Chưa chạy:** split-screen (để lần kiểm tay thứ hai), `ensureFitsGrid` sau khi ép lệch, hộp thoại "−" mở lúc recreate. Không phát hiện lỗi |
| 2026-11-05 | 13 | Kiểm toán hoạt ảnh (bảng trong Task 13) và kéo widget từ bảng chọn. Mã: `DragSession` (`source` nullable, `newWidgetKind`, `returnRect`), `DragHelper.drop` bay về chỗ cũ, `IconGridView` (press co cho widget, popIn trang trống, `returnRectOf`, nhánh `onNewWidgetDrop`), `WidgetPickerController` (`CardDragGesture`, `PickerSheet` chuyển tiếp chạm, ảnh widget đúng cỡ ô), `HomeViewModel.onNewWidgetDropped`, `LayoutReconciler.addWidgetAt`, `AddWidgetUseCase` nạp chồng | 37157fe | Build xanh. Oppo A3x (điều phối, một lần thả được nhận): chuyển tiếp chạm từ cửa sổ bảng hoạt động, giữ phương án A. Còn mở: thả bị từ chối, hủy, ghost page, ảnh bay về cho icon và thư mục, hồi quy |
| 2026-11-05 | review 2 (Task 13) | Năm điểm của review độc lập trên `37157fe`, đối chiếu code: (1) ĐÚNG: `HomeTouchRouter` xử lý `ACTION_CANCEL` như thả, nên phiên kéo bị ngắt vẫn ghi (kể cả tạo hàng widget mới). Sửa: CANCEL = hủy cho MỌI phiên (`DragHelper.drop(x, y, null)` + `onFinished(false)`); item có sẵn giữ chỗ cũ (ảnh bay về) vì ghi một lần di chuyển người dùng chưa chốt nguy hiểm hơn việc không ghi. (2) ĐÚNG: đóng bảng giữa lúc kéo (Back, ngón thứ hai) để phiên mở. Sửa: `cancelDragOf(sheet)` ở dismiss listener và ở `dismiss()` của controller (hủy phiên widget mới, `onFinished(false)`). (3) ĐÚNG: `detachDragVisual` xóa phiên không báo `onFinished`. Sửa: gọi `onFinished(false)` SAU khi dọn trạng thái, bọc `try/catch`; thứ tự `HomeActivity.onDestroy` an toàn (`touchRouter.onDestroy` chạy khi view còn sống, `ViewModel` sống qua recreate nên `onDragCancelled` có tác dụng). (4) ĐÚNG, nhỏ: id tạm đổi sang id thật giữa lúc bay làm ô thật hiện cạnh ảnh. Sửa: `IconGridView.settlingNewWidgetCell` giữ ẩn ô neo vừa thả tới `onDragSettled`. (5) **Ghi là giới hạn đã biết, không sửa:** kéo qua ≥ 5 trang có thể tái dùng view nguồn; sau thả bị từ chối, view trang gốc được bind lại giữa lúc kéo giữ `hiddenItemId` cũ nên ô kẹt ẩn tới lần bind kế (cuộn đi rồi quay lại). Lý do không sửa: bản sửa gọn (xóa `hiddenItemId` khi `draggingItemId` về null) phụ thuộc thứ tự giữa phát `uiState` và `settlingItemId` trong lần thả được nhận, đã được AN chỉnh kỹ, không đáng rủi ro hồi quy ngay trước lần kiểm tay | (xem commit review 2) | Build xanh. Lần kiểm tay thứ hai chạy trên `37157fe`: mục chạm tới xem báo cáo |
| 2026-11-05 | 13 (kiểm tay lần 2) | Chủ dự án chạy lần kiểm tay thứ hai trên `37157fe` (Vsmart Live 4): kéo thẻ từ bảng chọn, các hoạt ảnh mới, ảnh bay về cho thả bị từ chối, hồi quy: **đạt** | 37157fe | Điều phối báo 2026-11-05 |
| 2026-11-05 | review 2 (kiểm bằng kịch bản) | Điều phối kiểm các bản sửa của review 2 bằng kịch bản trên Live 4, bản `g2c-review2-clean.apk`, không nhờ chủ dự án lặp lại: **Back khi đang kéo thẻ** (4 widget, 101 item trước và sau: không thêm hàng, ảnh biến mất, icon về chỗ cũ, giữ edit mode); **Recents khi đang kéo**: như trên; **đổi giao diện (recreate) khi đang kéo**: như trên, bảng mở lại bình thường, không crash. **Chưa chạy:** nút nguồn, cuộc gọi, split-screen, mắt thường cho ca "không hiện ô đôi lúc hạ cánh", giới hạn đã biết số 5 | 3e82320 | Đóng plan tại commit này; việc còn lại: rebase cuối và merge (chủ dự án, điều phối) |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Trùng bit khi không có widget | Harness Live 4 (Task 1, 2): insert 3000 trang, remove và reflow (3000) khác biệt 0 | Đạt (mức hàm); cảm giác kéo xem dòng 5 |
| 2 | Hình học widget | Bảng ca W1–W8, R1–R3, F1 (25 PASS) + 4000 trang ngẫu nhiên có widget (556 lần thả widget, 265 từ chối, 0 vi phạm bất biến) | Đạt (mức hàm) |
| 3 | Mọi đường ghi DB biết span | Đọc code; Task 3 trên dữ liệu thật: 2 hàng loại 4 đúng, 0 ô chồng. Oppo (Task 10, bố cục thử 4 widget, 326 item): đổi cỡ lưới 4×6 → 4×5 → 5×6 → 4×6 qua Cài đặt thật, mỗi lần 326 item, chồng 0, ngoài lưới 0 (`reflowInTransaction`); ẩn bằng "−" ở trang có widget: các icon sau lùi dọc ô tự do, widget đứng yên (`compactPageAfterRemoval`). **Gỡ app MÔ PHỎNG qua `catchUp`** (Oppo, điều phối): DB thử trỏ app đầu trang 1 tới package không tồn tại, `catchUp` coi là đã gỡ (`deleteAppsAndFixFolders` → `deleteAndCompact`, đúng vòng dồn theo từng trang đã bỏ chốt toàn lưới): hàng đó mất, id app của trang [31,32,33,45,370,47,249,34,35,36] → [32,33,45,370,47,249,34,35,36,398], mỗi icon lùi một ô, hai widget 2×2 đứng yên ở y=4, chồng 0, ngoài lưới 0 (398 là app thật bị đẩy, được `placeNew` đặt lại). Đây KHÔNG phải gỡ thật: `removePackages` chưa được kích hoạt. **Chưa chạy:** `ensureFitsGrid` sau khi ép lệch | Đạt; chỉ `removePackages` (phần xóa hàng theo package) và `ensureFitsGrid` chưa chạy |
| 4 | Lạc quan khớp DB | Task 8 (`42105ff`) và lần kiểm tay thứ hai (`37157fe`, kéo thẻ từ bảng chọn): chủ dự án đạt. Review 2: Back, Recents, recreate khi đang kéo: không hàng nào được thêm (4 widget, 101 item trước và sau) | Đạt |
| 5 | Hồi quy AN | Task 8: chủ dự án đạt (kể cả Task 7 và chuỗi AN 9b–9e, `42105ff`); lần kiểm tay thứ hai (`37157fe`, sau khi Task 13 đổi `DragHelper.drop`): chủ dự án đạt, gồm ảnh bay về và hồi quy | Đạt |
| 6 | Widget không là đích gộp / nguồn gộp, không vào dock, không vào thư mục | Đọc code (`mergeable` loại widget, `FolderPlanner.canCreate/canAdd` đòi `isApp`, `DockView.onDrop` và `moveToDockInTransaction` từ chối) và chủ dự án đạt bài kiểm kéo-thả Task 8 (`42105ff`) | Đạt |
| 7 | Kính / chi phí | Task 6 (điều phối, Live 4 và Oppo A3x, cảnh vuốt S2, xem dòng Nhật ký Task 6): Live 4 kính so với phẳng cùng dải (janky 2.19–5.42% so với 2.95–4.94%, p95 14–16 so với 15–16 ms); Oppo xấu nhất kính so với `main` +0.5 điểm janky, +2.1 ms p95, trong D10 (+1.0, +4). Chưa đo: rung S3 cộng vuốt, cảnh đứng yên | Đạt cho cảnh vuốt; rung và đứng yên chưa đo |
| 8 | Ticker | Đọc code; Live 4: giờ khớp, ngày, thứ, tháng, tuần T2 đúng (ảnh). Chưa ghi: đổi múi giờ, 12/24 giờ, tắt màn hình, số receiver khi 0 widget | Một phần |
| 9 | Split-screen / gập | Chưa chạy (Task 10 không gồm split-screen). Chỉ có code bảo vệ: `IconCellView.onSizeChanged` dựng lại theo cỡ, dòng phụ ẩn theo dp, bài học M5 | Chưa kiểm tay, nên gộp một lượt vào lần kiểm thứ hai |
| 10 | Dấu "−" xóa hàng widget (xác nhận trước), trang chỉ có widget còn lại | Chủ dự án đạt trong lần kiểm Task 8/9 (Live 4); recreate khi bảng chọn mở: Oppo, 0 `WindowLeaked`. Hộp thoại "−" mở lúc recreate: chưa chạy (cùng mẫu đóng ở `onDetachedFromWindow`) | Đạt; một ca chỉ có code bảo vệ |
| 11 | Không sửa file ngoài vùng khai | `git diff --stat origin/main...HEAD` (2026-11-05): không chạm `core/glass`, `app/schemas`, shared docs; có thêm `LauncherAppsDataSource`/`AppRepository(Impl)` cho Task 11 (đã khai trong Task 11 là `AppRepository`) | Đạt |
| 12 | Seed và cờ tạm đã gỡ | `LauncherApp.kt` chỉ có commit hạ tầng M0; không có commit nào chứa seed, `G2cFlat` hay `G2cHarness`; cây sạch | Đạt |

## Câu hỏi chỉ chủ dự án trả lời được (kèm khuyến nghị)

- **Q1 — Khóa schema dạng nhẹ và phạm vi bản đầu.** Hai việc: (i) cho phép nhánh này giữ khóa schema chỉ để đặt nghĩa `itemType = 4` (không migration, không đổi `1.json`)? **Khuyến nghị: có**, vì 5.3 coi việc đặt nghĩa `itemType` mới là khóa, và rẻ vì không có DDL. (ii) Bản đầu bốn biến thể (Đồng hồ 2×2/4×2, Lịch 2×2/4×2)? **Khuyến nghị: cả bốn** — hai cỡ cùng một renderer, chi phí tăng ít; nếu muốn cắt thì cắt 4×2 trước.
- **Q2 — Vẻ ngoài và độ tương phản.** Home luôn chữ trắng + kính trắng nhạt (cùng ô thư mục). Trên ảnh nền sáng chữ widget lớn có thể khó đọc. **Khuyến nghị:** làm kính mặc định, chỉnh tint đậm hơn (kiểu thanh tìm kiếm) nếu bạn thấy mờ khi xem Task 6; không làm bản "widget sáng" theo chủ đề hệ thống.
- **Q3 — Bấm vào widget có mở app Đồng hồ/Lịch không?** **Khuyến nghị: có** (iOS làm vậy, Task 11 nhỏ, không đổi manifest); nếu không thì bỏ Task 11.
- **Q4 — ĐÃ TRẢ LỜI (chủ dự án, 2026-11-05): ĐỔI** — dấu "−" trên widget hiện hộp thoại xác nhận trước khi xóa, như iOS (plan gốc đề xuất xóa tức thì). Chuỗi: tiêu đề "Xóa tiện ích?", nút "Xóa" / "Hủy" (khối `widget_` của strings.xml). Hộp thoại là cửa sổ riêng nên đóng khi thoát edit mode hoặc `onDestroy`. Làm ở Task 9 (cùng bảng chọn).
- **Q5 — Chồng widget và đẩy icon.** Bản đầu: kéo widget lên widget khác bị từ chối; *thêm* widget không đẩy icon mà tìm chỗ trống/trang mới (iOS đẩy icon sang trang kế). **Khuyến nghị: giữ như plan**; đẩy khi thêm là thêm nhánh planner, làm sau nếu cần.
- **Q6 — Máy Oppo.** Quy tắc "tối đa 1 nhánh cần Oppo" mà GS cũng cần. Task 6 cần Oppo để biết kính diện tích lớn trên đường Lens (Oppo đắt gấp ~10× ở ghi display list). **Khuyến nghị:** đo Live 4 trước (quyết kính/phẳng), Oppo đo trong một khung giờ điều phối sắp sau GS hoặc do điều phối tự chạy; nếu Oppo chưa có thì ghi "chưa đo Oppo" vào Review Focus 7 và để mặc định kính nhưng giữ cờ lùi.
- **Q7 — Lịch không có sự kiện, tuần bắt đầu theo ngôn ngữ (vi → thứ Hai).** **Khuyến nghị: đồng ý** (sự kiện cần `READ_CALENDAR` và một nhánh quyền riêng).
- **Q8 — Vị trí và tên nút.** Pill "Tiện ích" cạnh "Cài đặt" (bên trái toolbar), cùng kiểu. **Khuyến nghị: giữ**; nếu muốn "+" tròn ở góc trái như iOS thì đổi `activity_home.xml` gọn hơn nhưng khác kiểu hai pill hiện có.
- **Q9 — Không kim giây.** **Khuyến nghị: không có**, vì `invalidate()` mỗi giây suốt ngày trên view lớn; thêm sau nếu muốn (Task riêng + đo).
- **Q10 — Cài APK để chạy harness log (Task 1, 2).** Planner là logic thuần nhưng dự án không có test; cách chứng minh duy nhất là chạy harness log trên máy. **Xin điều phối cài và gửi logcat** (mở rộng quyết định 7 từ "script đo" sang "harness log"), hoặc chủ dự án cho phép executor cài đúng hai lần.
- **Q11 — Khoản xin sửa file độc quyền ngoài danh sách đã giao:** `HomeActivity.kt` (~4 dòng) và `activity_home.xml` (~12 dòng) cho nút "Tiện ích". Đường lùi không đụng XML đã ghi ở Task 9.
- **Q12 — Biên độ rung widget (1° so với 2° của icon) và có rung widget không.** **Khuyến nghị: rung 1°** (rung 2° trên widget 4×2 lệch góc ~7 dp trông quá mạnh); chủ dự án phán cảm giác.

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#13 → **Xong** (G2c, ngày), ghi giới hạn: bốn biến thể, không kim giây, không sự kiện lịch, không cấu hình, không đổi cỡ tại chỗ, không đổi chỗ hai widget (kéo widget lên widget bị từ chối); bấm widget mở app Đồng hồ/Lịch của máy; "−" trên widget hỏi xác nhận; nút "Tiện ích" ở edit mode; thêm vào mục 4 "Ý tưởng": đẩy icon khi thêm widget, đổi cỡ widget, widget thành phố (cần cột `widgetConfig`), kim giây.
- `project_structure.md`: cây thư mục — `ui/widget/` (đã dự kiến) liệt kê file thật; `domain/model/WidgetKind`, `PlacedWidget`; `domain/layout/PageGeometry`, `WidgetPlacementPlanner`; bảng mốc dòng G2c → ✅; mục "Tệp thêm ở G2c".
- `room-schema.md`: thêm dòng `itemType = 4` (BUILTIN_WIDGET: `className` = mã loại, `spanX/spanY` = cỡ, `packageName/appWidgetId = null`) vào bảng mục 3; nhắc `className` có hai nghĩa theo `itemType`; sửa mô tả `GridInsertPlanner` "dịch 1 bậc" cho khớp code ("dồn sát đuôi") ở phần liên quan; Q8 thêm "widget không dồn"; chỉ ra `ITEM_TYPE_WIDGET = 3` vẫn dành cho AppWidgetHost (G3c).
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement. Ứng viên: KDoc planner sai so với code; "bảng đường bố cục gặp item nhiều ô" làm mẫu rà soát khi thêm loại item; ticker sổ đăng ký chỉ chạy khi hiện; hằng tạm đo phải gỡ và diff rỗng.
- `MultiAgent-Workflow.md`: mục 9 dòng G2c cập nhật cột Schema ("Có — khóa nhẹ, không migration"); mục 5.3 làm rõ "đặt nghĩa cho `itemType` mới" có cần migration hay không.

## Câu trả lời của chủ dự án (2026-11-05) — bản ghi

Q1 cấp khóa schema nhẹ + cả bốn biến thể · Q2 giữ như ô thư mục, xét lại nếu kiểm mắt không đạt · Q3 có (Task 11 vào phạm vi) · **Q4 ĐỔI: có hộp thoại xác nhận khi bấm "−" trên widget** · Q5 xác nhận (thêm widget lấy chỗ trống hoặc trang mới, không đẩy icon, không chồng) · Q6 hàng đợi Oppo: LC, GS, rồi Task 6; điều phối chạy, đo Live 4 trước · Q7 đồng ý · Q8, Q9, Q12 giữ đề xuất · Q10 điều phối cài harness và gửi logcat, executor không cài · Q11 cấp sửa đoạn hẹp `HomeActivity.kt` và `activity_home.xml`.
