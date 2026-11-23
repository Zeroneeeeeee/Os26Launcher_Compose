# EP — Tự dọn trang trống trên Home — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm chứng tay trên Vsmart Live 4. Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-17 · **Plan duyệt:** 2026-11-17 (chủ dự án duyệt theo khuyến nghị Q1–Q5)
**Trạng thái:** Task 2–3 đã code (chưa build, chưa kiểm tay), chờ chủ dự án build và kiểm theo Task 3 Step 5
**Máy:** Live 4 (không đụng kính) · **Đổi schema Room:** không

**Goal:** trang Home không còn item nào (không icon, thư mục, widget) thì tự bị xóa vào thời điểm chủ dự án chốt (mặc định: khi thoát edit mode), luôn giữ ít nhất 1 trang. Mở lại quyết định đã hủy ngày 2026-11-06 (`plans/CL-small-fixes.md`, mục "Quyết định đã bỏ").
**Architecture:** một hàm thuần ở `domain` chọn trang cần xóa, dùng chung cho cập nhật lạc quan ở `HomeViewModel` và cho transaction ở `LayoutReconciler`. Khuôn này giống `GridRemovePlanner` (bài học "Ẩn là dồn", `lessons/drag-and-layout.md`). Ghi DB qua `launchLayoutWrite`, trong `mutex` của `LayoutReconciler`.
**Tech Stack:** không thêm dependency.
**Spec:** TT#8 (edit mode) trong `Launcher-Features.md`; `room-schema.md` mục 2 (`home_pages`) và Q8; luật M7 "trang trống KHÔNG tự xóa" (bị thay bởi plan này nếu được duyệt).

## Hiện trạng (đọc docs, 2026-11-17)

- Trang là một hàng `home_pages(id, rank)` và tồn tại độc lập với item (`room-schema.md` mục 2). Vì vậy trang trống tồn tại được.
- Chỉ có một đường xóa trang: gỡ app thật (`deleteAndCompact`) gọi `deleteTrailingEmptyPages`, xóa các trang trống **ở cuối** và giữ ≥ 1 trang (sửa M8c ngày 2026-10-30).
- Đổi cỡ lưới (`reflowTo`) tạo/xóa trang cuối cho khớp, giữ ≥ 1 trang.
- Những đường **không** xóa trang: ẩn app (dấu "−", menu), kéo hết item ra khỏi một trang, trang trống ở giữa sau khi gỡ app.
- Ghost page chỉ có ở UI (`ghostPage`, `PageId.NEW`) và không ghi DB, nên không liên quan.
- **Chưa đọc code**: `LayoutReconciler.kt`, `HomeViewModel.kt`, `HomePageDao.kt` nằm sâu hơn 7 cấp thư mục nên phiên này không mở được. Task 1 đọc code và xác nhận các điểm trên trước khi sửa. Nếu code khác docs, Claude sửa plan và báo trước khi code.

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** người dùng kéo hoặc ẩn hết icon của một trang thì trang trống ở lại mãi, phải vuốt qua. iOS dọn trang trống (người dùng khảo sát trên iPhone XS Max, iOS 18). Hành vi trên iOS 26 **chưa kiểm chứng**: tìm trên mạng ngày 2026-11-17 chỉ thấy iOS 26 có màn "Edit Pages" để ẩn hoặc xóa trang bằng tay, không thấy tài liệu Apple nói khi nào trang trống tự mất.
- **Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Dọn khi thoát edit mode** | `exitEditMode` (Xong, Back, chạm khoảng trống) xóa mọi trang không có item | Giống iOS 18 đã khảo sát. Không chạy giữa lúc kéo nên không đụng ghost, cascade, lật trang. Một điểm gọi duy nhất | Ẩn app ngoài edit mode (menu nhấn giữ) chỉ dọn ở lần thoát edit mode sau | **Khuyến nghị** |
| B. Dọn ngay khi trang vừa trống | Sau mỗi lần thả/ẩn, xóa trang nguồn nếu trống | Trang mất ngay | Trang nguồn thường trống ngay lúc đang kéo qua trang khác; xóa giữa phiên kéo làm `ViewPager2` đổi chỉ số và tháo view (bài học M7). Phải chặn nhiều ca | Không |
| C. Chỉ dọn trang cuối | Mở rộng `deleteTrailingEmptyPages` cho cả đường ẩn/kéo | Ít thay đổi nhất, giữ luật M7 cho trang giữa | Trang trống ở giữa vẫn còn, đúng ca hay gặp nhất | Không (trừ khi chủ dự án muốn giữ trang giữa) |
| D. Xóa bằng tay ở màn sắp xếp trang | Như "Edit Pages" của iOS 26 | Đúng iOS 26 nhất, người dùng tự quyết | Màn mới, cử chỉ mới, là cả một tính năng. Không giải ca "trang trống tự còn lại" | Ghi để sau |

- **Vì sao chọn A:** điểm thoát edit mode là lúc bố cục đã ổn định: không còn phiên kéo, không có ghost, `pendingMoves` của lần thả cuối đã được xếp hàng (suy luận, kiểm ở Task 1). Hàm thuần dùng chung hai phía cho cập nhật lạc quan và DB ra cùng kết quả, nên không nháy (bài học `GridRemovePlanner`).
- **Đánh đổi chấp nhận:** đảo lại luật M7 và quyết định ngày 2026-11-06. Người dùng muốn giữ một trang trống để làm "trang chỉ có ảnh nền" sẽ không giữ được, trừ khi có công tắc (Câu hỏi Q3). Xét lại khi có số liệu iOS 26 thật.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | "Trang trống" = trang không có hàng `launcher_items` nào ở `container = HOME` trỏ tới `pageId` đó (icon, thư mục, widget đều tính là có item) | Claude đề xuất 2026-11-17, theo `room-schema.md` mục 2 |
| D2 | Luôn giữ ≥ 1 trang. Mọi trang đều trống thì giữ trang có `rank` nhỏ nhất | Cùng luật với `deleteTrailingEmptyPages`, `reflowTo` |
| D3 | Không đụng dock, `hidden_apps`, item trong thư mục | Ngoài phạm vi trang |
| D4 | Không đổi schema Room. `rank` của các trang còn lại giữ nguyên (thứ tự đọc theo `rank` vẫn đúng khi có khe). Task 1 kiểm xem có chỗ nào giả định `rank` liền nhau không | Claude đề xuất, chờ Task 1 xác nhận |
| D5 | Pager đang đứng ở trang bị xóa thì về trang thật gần nhất **bên trái**. Không còn trang bên trái thì về trang đầu | Claude đề xuất |
| D6 | Không viết test tự động | `AGENTS.md` |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Dọn vào lúc nào: khi thoát edit mode (A), ngay khi trang trống (B), hay chỉ trang cuối (C)? | A | A (chủ dự án, 2026-11-17) |
| Q2 | Có dọn cả trang trống ở giữa không? (Sửa M8c ngày 2026-10-30 từng coi "trang giữa bị mất" là lỗi, nhưng lỗi đó là do gỡ app dồn cả màn hình, không phải do dọn trang trống) | Có | Có (chủ dự án, 2026-11-17) |
| Q3 | Có cần công tắc trong Cài đặt ("Tự xóa trang trống", mặc định bật) không? | Không, làm sau nếu có người dùng phàn nàn | Không (chủ dự án, 2026-11-17) |
| Q4 | Gỡ app thật làm trống một trang ở giữa: dọn luôn lúc đó, hay chờ lần thoát edit mode sau? | Dọn luôn: đổi `deleteTrailingEmptyPages` ở `deleteAndCompact` sang hàm dọn mới, để mọi đường cho cùng một kết quả (bài học "rà MỌI đường ghi", `lessons/settings-and-data.md`) | Dọn luôn (chủ dự án, 2026-11-17) |
| Q5 | Ẩn app ngoài edit mode (menu nhấn giữ > Ẩn) làm trống trang: dọn luôn hay chờ lần thoát edit mode sau? | Dọn luôn, vì lúc đó không có phiên kéo | Dọn luôn (chủ dự án, 2026-11-17) |

## Ngoài phạm vi / ghi để sau

- Màn sắp xếp, ẩn, xóa trang bằng tay (phương án D, như "Edit Pages" của iOS 26).
- Hoạt ảnh riêng cho trang biến mất (hiện tại pager cập nhật danh sách như khi xóa ghost).
- Kiểm chứng hành vi iOS 26 trên máy thật.

## Vùng file

Đường dẫn tính từ `app/src/main/java/gambi/global/os26launcher/`.

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `domain/layout/EmptyPagePrunePlanner.kt` (thư mục chốt ở Task 1 theo chỗ đặt `GridRemovePlanner`) | Tạo | Hàm thuần: nhận danh sách trang (id, rank, có item hay không), trả danh sách id cần xóa theo D1, D2 |
| `data/layout/LayoutReconciler.kt` | Sửa | Thêm `pruneEmptyPages()` (hoặc sửa hàm cùng tên nếu đã có, xem Task 1) chạy trong `mutex` + transaction. Q4 = có thì `deleteAndCompact` gọi hàm này thay `deleteTrailingEmptyPages` |
| `data/layout/db/HomePageDao.kt` | Sửa nếu thiếu | `deleteByIds(ids)` |
| `domain/repository/LayoutRepository.kt`, `data/layout/LayoutRepositoryImpl.kt` | Sửa | Thêm `pruneEmptyPages()` |
| `domain/usecase/PruneEmptyPagesUseCase.kt` | Tạo | Theo khuôn các use case bố cục khác |
| `ui/home/HomeViewModel.kt` | Sửa | `exitEditMode()`: bỏ trang trống khỏi `pages` trong cùng lần `_uiState.update` với `editMode = false`, rồi `launchLayoutWrite { pruneEmptyPagesUseCase() }`. Q5 = có thì gọi thêm sau ẩn app ngoài edit mode |
| `ui/home/controller/HomePagerController.kt` | Sửa nếu cần | Áp D5 sau khi danh sách mới áp xong (commit callback của `submitList`, bài học CL) |
| `docs/plans/EP-empty-page-cleanup.md`, `docs/knowledgements/EP-empty-page-cleanup.md` | Tạo | |

## Global Constraints

- Không xóa trang khi `draggingItemId != null`, `ghostPage == true` hoặc `DragHelper.currentSession != null`.
- Cập nhật lạc quan và DB dùng **cùng** `EmptyPagePrunePlanner`.
- Mọi ghi DB qua `launchLayoutWrite` (giữ `pendingMoves`/`latestDb*`, bài học M7).
- `ui` không import `data` (luật `ui → domain ← data`).
- Không thêm log vĩnh viễn. Commit message tiếng Anh, docs tiếng Việt.

## Review Focus

1. Kéo hết icon khỏi trang giữa rồi bấm Xong: trang mất, các trang khác không đổi chỗ icon, chấm trang đúng số, không nháy.
2. Không xóa trang nào trong lúc kéo, kể cả khi trang nguồn vừa trống và đang có ghost.
3. Luôn còn ≥ 1 trang; ẩn hết app rồi thoát edit mode vẫn vào lại được edit mode (GP, `GridStatus.Empty`).
4. Pager đứng ở trang bị xóa thì về đúng trang theo D5, không văng ra trang cuối.
5. Kill app mở lại: số trang trong DB khớp UI (`home_pages` không còn hàng trống).
6. Trang chỉ có widget hoặc chỉ có thư mục **không** bị xóa.

---

### Task 1: Đọc code và chốt chỗ sửa (không đổi code)
**Bắt buộc kiểm tay:** không.
**Files:** chỉ đọc `LayoutReconciler.kt`, `HomePageDao.kt`, `LauncherItemDao.kt`, `HomeViewModel.kt` (`exitEditMode`, `launchLayoutWrite`, `onQuickHideTapped`, nhánh ghost), `HomePagerController.kt`, `GridRemovePlanner.kt`.
**Interfaces:** Consumes không. Produces không.
- [ ] **Step 1:** Tìm `pruneEmptyPages` (plan CL ghi tên này): đã có chưa, ở đâu, đang làm gì. Xác nhận `deleteTrailingEmptyPages` chỉ được gọi ở `deleteAndCompact` và `reflowTo`.
- [ ] **Step 2:** Xác nhận không chỗ nào giả định `rank` liền nhau (D4): `assemble()`, tạo trang mới `rank = last+1`, chỉ số pager.
- [ ] **Step 3:** Xác nhận lúc `exitEditMode` chạy thì không còn phiên kéo hay ghost, và cách `pendingMoves` xếp hàng với lần ghi mới.
- [ ] **Step 4:** Ghi kết quả vào Nhật ký. Code khác plan thì sửa plan và báo chủ dự án trước Task 2.

### Task 2: Planner và đường ghi DB
**Bắt buộc kiểm tay:** không (gộp vào bài kiểm cuối).
**Files:** `EmptyPagePrunePlanner.kt`, `LayoutReconciler.kt`, `HomePageDao.kt`, `LayoutRepository.kt`, `LayoutRepositoryImpl.kt`, `PruneEmptyPagesUseCase.kt`.
**Interfaces:** Produces `EmptyPagePrunePlanner.plan(pages): List<Long>`, `LayoutRepository.pruneEmptyPages()`, `PruneEmptyPagesUseCase`.
- [ ] **Step 1:** `EmptyPagePrunePlanner` theo D1, D2.
- [ ] **Step 2:** `LayoutReconciler.pruneEmptyPages()`: trong `mutex`, một transaction: đọc trang và đếm item HOME theo `pageId`, gọi planner, `deleteByIds`.
- [ ] **Step 3:** Nếu Q4 = có: `deleteAndCompact` gọi đường dọn mới thay `deleteTrailingEmptyPages`. Giữ `reflowTo` như cũ.
- [ ] **Step 4:** Repository + use case. Build `.\gradlew.bat assembleDebug` (phiên này không build được thì ghi "chưa build").

### Task 3: Nối vào edit mode và pager
**Bắt buộc kiểm tay:** không (gộp vào bài kiểm cuối).
**Files:** `HomeViewModel.kt`, `HomePagerController.kt`.
**Interfaces:** Consumes `EmptyPagePrunePlanner`, `PruneEmptyPagesUseCase`.
- [ ] **Step 1:** `exitEditMode()`: tính trang cần bỏ bằng planner trên `pages` hiện tại; một lần `_uiState.update` (bỏ trang + `editMode = false`); rồi `launchLayoutWrite { pruneEmptyPagesUseCase() }`. Bỏ qua khi đang kéo (Global Constraints).
- [ ] **Step 2:** Q5 = có: gọi cùng đường sau khi ẩn app ngoài edit mode.
- [ ] **Step 3:** `HomePagerController`: áp D5 trong commit callback của `submitList`. Chấm trang cập nhật theo danh sách mới.
- [ ] **Step 4:** Build.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — (a) bố cục ≥ 3 trang; vào edit mode, kéo hết icon trang 2 sang trang 1/3, bấm Xong: trang 2 mất, chấm còn 2. (b) Như (a) nhưng thoát bằng Back, rồi bằng chạm khoảng trống. (c) Đứng ở trang vừa trống rồi bấm Xong: về trang bên trái. (d) Ẩn hết app trang cuối bằng dấu "−", bấm Xong: trang cuối mất. (e) Trang chỉ có 1 widget, trang chỉ có 1 thư mục: không mất. (f) Ẩn hết app: còn 1 trang, giữ khoảng trống vào lại được edit mode. (g) Kéo icon tới mép phải trang cuối (ghost) rồi kéo ngược, bấm Xong: không có trang thừa. (h) Kill app (`adb shell am force-stop gambi.global.os26launcher`) mở lại: số trang như trước khi kill.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Máy: Vsmart Live 4. Dữ liệu: sao lưu trước (`run-as tar cf - databases files/datastore no_backup`), bố cục ≥ 3 trang có 1 thư mục và 1 widget. Thứ tự: Task 3 (a)–(h); nếu Q4 = có thì gỡ app duy nhất ở một trang giữa: trang mất ngay. Hồi quy: kéo-thả trong trang và qua trang, ghost tạo trang mới khi thả, đổi cỡ lưới, cài app mới (đặt sau icon cuối, FX3), mở/đóng Library, tìm kiếm.
**Kết quả kiểm chứng (máy, ngày):** chưa.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú (lỗi, câu hỏi, đổi kế hoạch) |
|---|---|---|---|
| 2026-11-17 | Plan | Viết plan | Chờ chủ dự án trả lời Q1–Q5 và duyệt. Chưa đọc được code (thư mục quá sâu cho phiên này) |
| 2026-11-17 | Plan | Chủ dự án duyệt theo khuyến nghị Q1–Q5 | |
| 2026-11-17 | Task 1 | Đọc code (đọc code, chưa đổi gì). Khớp plan: chưa có `pruneEmptyPages`; `deleteTrailingEmptyPages` chỉ được gọi ở `deleteAndCompact` (`reflowInTransaction` tự xóa trang thừa bằng `surplus`, không gọi hàm này); `removeItem`/`setHidden`/`moveItem` không xóa trang. D4 đúng: mọi chỗ đọc trang đều `sortedBy { rank }` và đánh `index` lại bằng `mapIndexed` (`LayoutRepositoryImpl.assemble`), trang mới luôn lấy `last.rank + 1`, nên khe `rank` không sao. `HomePageDao.deleteByIds` đã có, **không cần sửa DAO**. `GridRemovePlanner` ở `domain/layout/`, planner mới đặt cùng chỗ. Lúc thả, `onItemDropped` đã đặt `draggingItemId = null, ghostPage = false` trong cùng lần `launchLayoutWrite` | Phát hiện F1–F3 bên dưới, chờ chủ dự án chốt trước Task 2 |
| 2026-11-17 | Task 1 | **F1 — UI và DB đếm "trống" khác nhau.** `assemble` không vẽ hàng app không khả dụng (bị tắt, thẻ nhớ tháo, `LayoutRepositoryImpl.kt:307`) và thư mục không còn con khả dụng (`:282`), nhưng hàng vẫn còn trong DB (Q11). Trang chỉ có những hàng này: UI thấy trống, DB thấy có item. Nếu UI cập nhật lạc quan bằng `pages` còn DB theo D1, trang bị bỏ trên UI rồi hiện lại khi `scheduleReconcile` đối chiếu (nháy). **Đề xuất:** giữ D1 (đếm hàng DB, để bật lại app thì icon về đúng trang cũ) và **bỏ cập nhật lạc quan**: `exitEditMode` chỉ tắt `editMode` rồi ghi DB; emission của Room bỏ trang ngay sau đó. Planner vẫn là hàm thuần ở `domain`, nhưng chỉ phía DB dùng | |
| 2026-11-17 | Task 1 | **F2 — D5 cần code riêng ở pager.** `settlePageSelection` khôi phục theo `selectedPageId`; trang đó bị xóa thì nó giữ `pager.currentItem` (kẹp vào khoảng hợp lệ). Sau khi bỏ một trang ở giữa, chỉ số cũ trỏ vào trang **bên phải**, trái với D5. Task 3 phải nhớ danh sách trước lần `submitList` để tìm trang còn lại gần nhất bên trái | |
| 2026-11-17 | Task 1 | **F3 — `exitEditMode` được gọi cả khi không ở edit mode.** Có 5 chỗ gọi: nút Xong, Back, chạm khoảng trống (`HomePagerController.kt:106`), `HomeActivity.kt:252`, và `HomeActivity.kt:298` (`onStop`, mỗi lần rời launcher). **Đề xuất:** chỉ dọn khi trạng thái trước đó là `editMode = true` và `draggingItemId == null`, để không ghi DB mỗi lần `onStop`. Q5 (ẩn ngoài edit mode) gọi dọn riêng sau `hideAppUseCase` trong `onQuickHideTapped` khi `editMode = false` | |
| 2026-11-17 | Task 1 | Chủ dự án chốt F1–F3 theo đề xuất, cho sửa thẳng trên `main` | |
| 2026-11-17 | Task 2 | Tạo `domain/layout/EmptyPagePrunePlanner.kt` (D1, D2). `LayoutReconciler`: thêm `pruneEmptyPages()` (mutex + transaction); `deleteTrailingEmptyPages` thay bằng `deleteEmptyPages` dùng planner, nên gỡ app cũng xóa trang trống ở giữa (Q4). `LayoutRepository.pruneEmptyPages()`, `PruneEmptyPagesUseCase`. DAO không đổi | Chưa build (chủ dự án tự build) |
| 2026-11-17 | Task 3 | `HomeViewModel.exitEditMode`: dọn qua `launchLayoutWrite` (không lạc quan, F1) chỉ khi trước đó `editMode = true` và `draggingItemId == null` (F3). `onQuickHideTapped` và `onRemoveWidget` ngoài edit mode gọi dọn ngay sau khi ghi (Q5; widget thêm cho nhất quán). `HomePagerController.settlePageSelection`: trang đang xem bị xóa thì về trang còn lại gần nhất bên trái theo danh sách lần trước (`settledPageIds`), bỏ qua `PageId.NEW` (F2, D5) | Chưa build, chưa kiểm tay. Ẩn app trong thư mục (`hideAppInFolder`) không gọi dọn: thư mục còn ≥ 1 con thì trang không trống |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `Launcher-Features.md`: TT#8 thêm "tự dọn trang trống khi thoát edit mode".
- `project_structure.md`: dòng mốc EP ở mục 9; thêm `EmptyPagePrunePlanner`, `PruneEmptyPagesUseCase`.
- `room-schema.md`: mục 2 ("trang tồn tại độc lập với item") ghi thêm luật dọn; Q8 ghi đường gỡ app (nếu Q4 = có).
- `plans/CL-small-fixes.md`: ghi chú "quyết định bỏ ngày 2026-11-06 được mở lại ở EP".
- `lessons/drag-and-layout.md` / `AGENTS.md`: bài học nếu có.
