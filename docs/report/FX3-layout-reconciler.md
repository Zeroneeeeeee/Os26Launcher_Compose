# FX3 — Bộ điều hợp bố cục (layout reconciler) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (máy thật của chủ dự án, Os26Launcher là launcher mặc định) do **điều phối** chạy bằng adb; agent thực thi không cài APK (mục 11). Lệnh viết cho PowerShell.

**Nhánh:** `fix/fx3-layout-reconciler` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX3-layout-reconciler` · **PR:** #32 (draft) · **Bắt đầu:** 2026-11-11 · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review (Task 1-8 xong, kiểm Live 4 đã chạy một phần, xem "Kết quả kiểm chứng")
**Khóa:** không (không đổi schema Room; xem "Vì sao chọn thiết kế này" mục schema và Global Constraints)

**Goal:** sửa các lỗi của bộ điều hợp bố cục mà báo cáo rà soát 2026-11-10 tìm ra: A3 (app đổi tên activity khi cập nhật để lại ô ma), B3 (đổi cỡ lưới đua với cài app, planner kéo-thả sập khi có item ngoài lưới), B4 (gỡ nhiều app liền nhau, app đã gỡ bị đặt lại), B5 + C17 (hai flow Room phát bố cục nửa cũ nửa mới, mỗi lần ghi render hai lần), C20 (app mới đặt **sau icon cuối cùng**, không lấp ô trống giữa trang) và phần flow bố cục của B1 (lỗi Room không được làm sập process).
**Architecture:** mọi thay đổi nằm ở `data/layout` và hai planner thuần của `domain/layout`. Một đường duy nhất để "đặt app chưa có hàng": chuẩn bị (đọc, hỏi hệ thống, gom đổi tên) **dưới mutex, ngoài transaction**, rồi một transaction ghi. Bố cục UI đọc qua **một** flow vô hiệu hóa (`invalidationTracker.createFlow` trên hai bảng) và một transaction đọc.
**Tech Stack:** Room 2.8.5 và Kotlin coroutines đã có; không thêm thư viện. `InvalidationTracker.createFlow(vararg tables, emitInitialState)` có sẵn ở `room-runtime` 2.8.5 (đọc source `room-runtime-android-2.8.5-sources.jar` trong cache Gradle, 2026-11-11).
**Spec:** các mục A3, B3, B4, B5, C17, C20, B1 (phần bố cục) trong `docs/report/Audit-Report-2026-11-10.md`; mục "Quyết định của chủ dự án (2026-11-11)"; `docs/room-schema.md` (Q7, Q8, Q10, Q11).

**Đã đối chiếu code hiện tại (2026-11-11, `b1af810`):** mọi vị trí dòng trong báo cáo cho A3, B3, B4, B5 đúng. Phát hiện thêm khi đọc, ảnh hưởng tới thiết kế:
1. `setHidden(hidden = false)` (`LayoutReconciler.kt:447-451`) cũng gọi `placeNewInTransaction`, nên quy tắc đặt chỗ của C20, lọc cài đặt của B4 và gom đổi tên của A3 đều chạm tới nó (xem quyết định D4).
2. `GridInsertPlanner.plan` còn được gọi ở `HomeViewModel.planGridOptimistic` (dòng ~916, chạy trên luồng chính, không try) và `IconGridView.kt:653` (preview kéo). **Chữ ký `plan` giữ nguyên**, planner phải tự an toàn.
3. `ensureFitsGrid` chỉ coi là "ngoài lưới" khi `gridX + spanX > columns || gridY + spanY > rows`. Hàng **trùng ô** (hai item cùng một ô, vẫn nằm trong lưới) không bị chữa lúc khởi động mà vẫn làm số item của trang vượt `columns*rows`, nên là cách tái hiện B3 mà không cần đua (Task 4).
4. `reflowToGrid` chỉ có một nơi gọi (`ChangeGridPresetUseCase`); `SettingsViewModel` gọi use case trong `NonCancellable`, nên đổi use case thành lớp mỏng không đụng `ui/`.

---

## Vì sao chọn thiết kế này

**Vấn đề cần giải (ràng buộc thật):**
- `LayoutReconciler.kt` là file nóng độc quyền; mọi hàm ghi đã đi qua `mutex` và `db.withTransaction`; các lệnh hỏi hệ thống (chặn) phải nằm **ngoài** transaction (bài học M4: không giữ khóa ghi DB lúc chờ hệ thống).
- Schema Room đang ở v1 và `app/schemas/1.json` đã commit; dự án không có test migration (A7), nên tránh đổi schema.
- Mọi quyết định A3 phải giữ an toàn của Q11/Review Focus 2: **không bao giờ xóa hàng** chỉ vì app vắng mặt khỏi danh sách (app bị tắt, khóa, thẻ nhớ tháo vẫn vắng mặt mà còn cài).
- Bài học `drag-and-layout.md` (M7 Task 6): bố cục của UI phải đi chung một cơ chế `pendingMoves`/`latestDb*`; emission DB nửa vời là nguồn lỗi "icon chớp/biến mất".

### A3 — app đổi tên activity khởi động khi cập nhật

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A3-1. Sửa `className` của hàng mồ côi (re-point) | Trong bước đặt app mới: app sống mới (cùng package + user) mà có hàng/`hidden_apps` mồ côi (khóa không còn app sống) thì `UPDATE className` cho hàng cũ thay vì chèn hàng mới | Giữ nguyên vị trí, slot dock, thư mục (vì `id`, `container`, `rank`, `pageId`, ô đều giữ); chỉ ghi giá trị, không đổi schema; không bao giờ xóa hàng | Ghép sai khi một package có nhiều activity khởi động cùng đổi tên (xem dưới); app tạm bị tắt một alias rồi bật alias khác sẽ bị coi là đổi tên | **Chọn** |
| A3-2. Xóa hàng mồ côi khi cùng package có app sống chưa đặt, rồi đặt app mới như thường | Đơn giản | Mất đúng cái báo cáo muốn giữ: vị trí người dùng xếp, slot dock, chỗ trong thư mục | Không | Không |
| A3-3. Khóa theo package thay vì `ComponentKey` | Không còn lệch khi đổi className | Triệt để | Đổi nghĩa khóa ở mọi nơi (`ComponentKey` dùng khắp app), có thể đổi schema (cần khóa schema, migration không có test); app nhiều activity khởi động (Q3, G2a) mất phân biệt | Không |
| A3-4. Ghép theo nhãn hoặc icon | Đoán "cùng app" | Không | Nhãn đổi theo ngôn ngữ/cập nhật, icon đổi khi cập nhật (A4): không ổn định | Không |

**Vì sao A3-1:** Launcher3 (launcher gốc AOSP) xử lý cùng kiểu: khi `PackageUpdatedTask` (OP_UPDATE) thấy component của item không còn resolve được thì **đặt lại intent của item về launch intent của package** chứ không xóa item (đọc tài liệu: `PackageUpdatedTask.java`, `updateWorkspaceItemIntent`, android.googlesource.com, đọc 2026-11-11; phần còn lại của file có xóa item đã khuyết trong lần đọc tóm tắt nên không dựa vào nó). Báo cáo A3 đã đo bằng mô phỏng DB: dock còn 3 icon, hai hàng Chrome (đo được, Live 4, 2026-11-10). Điểm an toàn Q11 được giữ vì điều kiện để re-point là **có app sống chưa đặt cùng (package, user)**: bằng chứng package đang bật. Gói bị tắt, khóa hay trên thẻ nhớ đã tháo không có app sống nên không bao giờ đủ điều kiện, hàng ở nguyên như hôm nay (`catchUp` vẫn chỉ xóa khi `isPackageInstalled` trả false).

**Ghép nhiều-với-nhiều:** gom theo (package, user): danh sách `className` mồ côi (theo thứ tự `id` hàng, rồi `hidden_apps`) ghép tuần tự với danh sách app sống chưa đặt (theo thứ tự nhãn của `apps`). Ghép nhầm giữa hai activity của cùng một app là vô hại (cùng app, cùng icon gốc); không ghép thì sinh lại đúng lỗi A3. Dư app mới (nhiều hơn mồ côi) thì đặt như app mới (C20); dư mồ côi thì để nguyên (giống Q11).

### B5 + C17 — một flow cho hai bảng

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| B5-1. `createFlow("launcher_items", "home_pages")` + một transaction đọc | Một flow vô hiệu hóa cho cả hai bảng; mỗi lần phát đọc `pageDao.getAll()` và `itemDao.getAll()` trong một `db.withTransaction`, rồi `combine` với `apps` | Mỗi snapshot nhất quán (một transaction đọc); một lần ghi chạm cả hai bảng phát **một** lần (xem source `InvalidationTracker`: so phiên bản mọi bảng đã theo dõi trong cùng một lần làm mới, gom tên bảng đổi vào một `emit`); không đổi schema, không đổi DAO | `withTransaction` lấy kết nối ghi nên đọc chờ sau ghi đang chạy (vài chục hàng, chấp nhận); cần chứng minh "một phát mỗi lần ghi" bằng số đo (Task 1 và 2) | **Chọn** |
| B5-2. Giữ hai flow, thêm `debounce`/`sample` trong `combine` | Nuốt trạng thái giữa | Thay đổi nhỏ nhất | Dựa vào thời gian chứ không đảm bảo; mỗi lần thả icon chịu thêm độ trễ đúng lúc UI cần emission DB nhanh để hội tụ (`scheduleReconcile` 400 ms, M7); `debounce` ngắn vẫn lọt trạng thái nửa cũ khi DB chậm | Không |
| B5-3. Một `@Query` JOIN hoặc POJO `@Relation` | Room tự quan sát cả hai bảng trong một transaction | Không cần `InvalidationTracker` | Item ở dock và con của thư mục có `pageId = 0`, không khớp trang nào: JOIN/Relation làm rơi chúng, phải thêm UNION và POJO riêng; trang trống phải sống độc lập (room-schema mục 2); nhiều SQL hơn để review | Không |
| B5-4. Suy trang từ item | Bỏ `home_pages` khỏi flow | Một bảng | Trang trống không tồn tại được (room-schema mục 2) | Không |

**Vì sao B5-1:** là chính cách tài liệu Room mô tả cho truy vấn nhiều bước: KDoc của `InvalidationTracker.createFlow` trong `room-runtime` 2.8.5 có ví dụ `createFlow("Artist").map { ... đọc DAO ... }` (đọc tài liệu, source thư viện, 2026-11-11). Khớp giả thuyết (2) chưa xác nhận trong `lessons/settings-and-data.md` (mục M8a Task 6, "hai luồng Room về lệch nhau") và nghi vấn số 1 của hàng MV ở `MultiAgent-Workflow.md` mục 10. Plan **không** khẳng định lỗi kéo-thả của MV được sửa; chỉ loại một nguyên nhân.

### Các quyết định nhỏ hơn (so sánh ngắn)

- **C20 (đã chốt hành vi, chọn cách đặt):** *Trang cuối + sau icon cuối* (chọn) so với *quét ngược các trang tìm trang còn chỗ trống bất kỳ* (không chọn: vẫn lấp ô trống giữa trang ở trang trước, trái ý chủ dự án) và *giữ "ô trống đầu tiên"* (hành vi cũ, kiểu Launcher3: `WorkspaceItemSpaceFinder.findSpaceForItem` duyệt các màn hình theo thứ tự, màn đầu có ô trống thì dùng, không theo dõi "item cuối", chỉ tạo màn mới khi hết chỗ; đọc 2026-11-11). Hệ quả: Q7 trong `room-schema.md` đổi nghĩa (ghi ở "Đề xuất cập nhật tài liệu chung").
- **B3 (đổi cỡ lưới nguyên tử):** *Cả đổi cỡ và ghi DataStore trong một `mutex.withLock` của reconciler* (chọn) so với *use case giữ khóa riêng* (không chọn: hai khóa không loại được đua với `placeNewApps`) và *đưa cỡ lưới vào Room* (không chọn: đổi schema, cần khóa schema và migration không có test). Thứ tự ghi an toàn của M8a (thu nhỏ: DB trước; mở rộng: DataStore trước) giữ nguyên, chỉ đổi nơi chạy.
- **Schema:** mọi thay đổi DAO là `@Query UPDATE`; không đụng entity, `version`, `exportSchema`. File `app/schemas/1.json` phải **không đổi** (Review Focus 1).

**Đánh đổi chấp nhận:**
- Re-point có thể ghép chéo hai activity của cùng một app (vô hại, xem trên). Xét lại nếu gặp app có nhiều activity khởi động mà mỗi cái có icon khác nhau trên Home.
- Hàng mồ côi **đã tồn tại** từ trước khi sửa (có hàng cũ mồ côi và hàng mới đã đặt) không được dọn (xem "Ngoài phạm vi").
- Chọn B5-1 chấp nhận `getAll()` trong `withTransaction` thay vì kết nối đọc: đổi lấy code ngắn và quen thuộc trong dự án (đã dùng `withTransaction` khắp `LayoutReconciler`).

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | C20: app mới cài đặt **sau icon cuối cùng**, ở trang cuối nếu còn chỗ, không thì tạo trang mới; không bao giờ vào ô trống giữa trang. Gỡ app vẫn dồn ô app đó để lại (`compactPageAfterRemoval`, `deleteAndCompact` giữ nguyên) | Chủ dự án, 2026-11-11 (báo cáo, mục "Quyết định") |
| D2 | Dock mặc định (DD): không đổi. Dock chỉ được chọn lúc seed (`seedIfEmpty` → `pickDefaultDock`); `placeNew*` chỉ đặt vào lưới chính, app cài sau **không bao giờ** vào dock hay lấp khe dock (Q10 giữ). Seed không dùng quy tắc mới (nó xếp liền từ ô đầu nên không có khe) | Agent thực thi suy ra từ code, chờ điều phối duyệt |
| D3 | Thư mục: con của thư mục là hàng `container > 0`, đã "đã biết" nên không bao giờ bị đặt lại; app mới không vào thư mục. Hàng `FOLDER` ở lưới chính tính như một **icon** khi xác định "icon cuối cùng"; widget (span > 1) **không** tính là icon nhưng vẫn chiếm ô, app mới nhảy qua ô bị widget phủ. A3 re-point hàng con trong thư mục giữ nguyên `container` và `rank` | Đề xuất, chờ duyệt |
| D4 | App được **bỏ ẩn** (`setHidden(false)`) theo cùng quy tắc C20 (sau icon cuối). Không dùng re-point A3 cho bỏ ẩn (app người dùng chủ động gọi lại không được chiếm hàng mồ côi của app khác). Lọc `isPackageInstalled` của B4 áp cả đường này | Đề xuất; **cần chủ dự án xác nhận** (câu hỏi 1) Điều phối duyệt 2026-11-11: theo đúng quyết định C20 của chủ dự án ("sau icon cuối", như iOS). |
| D5 | "Trang cuối còn chỗ" nghĩa là trang có rank lớn nhất và còn ô tự do có chỉ số đọc **lớn hơn** chỉ số của icon cuối cùng. Hết chỗ → tạo trang mới ngay cả khi trang trước còn khe giữa trang. Trang cuối trống (do người dùng kéo hết icon đi) vẫn là trang cuối, app mới vào (0,0) | Đề xuất; **cần chủ dự án xác nhận** (câu hỏi 2) Điều phối duyệt 2026-11-11: theo đúng quyết định C20 của chủ dự án ("sau icon cuối", như iOS). |
| D6 | `addWidget`/`addWidgetAt` (G2c: widget lấy hình chữ nhật trống đầu tiên) **không đổi**; C20 chỉ nói về app | Chủ dự án chốt G2c 2026-11-05; ngoài phạm vi C20 |
| D7 | Không đổi schema Room, không khóa schema | Agent thực thi; kiểm bằng `git diff --stat app/schemas` rỗng |
| D8 | ~~Giữ log đếm `assemble`/`snapshot`~~ **Đã gỡ khi đóng nhánh** (2026-11-11, theo điều phối): chỉ là công cụ đo B5/C17 trong lúc kiểm. Giữ các dòng log vận hành cùng kiểu `reflow to ...` đã có trong file: `changeGrid A->B done`, `placeNew: fresh=N grid=CxR`, `placeNew: skipped N not installed`, `repoint ...` | Điều phối, 2026-11-11 |

## Ngoài phạm vi / ghi để sau

- Phần `ApplicationScope` (handler) và thời tiết của B1: thuộc FX6. Plan này chỉ bọc các lời gọi reconciler trong flow bố cục và thêm `.catch` cho flow đó.
- Dọn hàng mồ côi đã có sẵn (cùng package đã có hàng cũ mồ côi **và** hàng mới đã đặt): re-point chỉ chạy khi app mới còn "chưa có hàng". Dọn ca cũ cần quyết định xóa, vướng Q11; chưa làm.
- Q11 (package bật nhưng không có activity khởi động): giữ là hạn chế đã biết.
- `HomeViewModel` (FX1/FX7), `HomeActivity` và `ui/home/view/*` (FX2), `data/apps/*` (FX5): không sửa. Planner được sửa để call site hiện tại tự an toàn.
- Hoàn tác gỡ app, hay tùy chọn "giữ ô trống khi gỡ": không làm (D1 giữ dồn).
- Ghép nhầm khi một package đổi tên **cả** activity hiện trên Home **và** activity đang ẩn: re-point ghép tuần tự (hàng theo `id`, rồi `hidden_apps`) nên có thể đổi chéo giữa hàng hiện và hàng ẩn. Chấp nhận (điều phối 2026-11-11), ghi ở "Chỗ cần soi" của knowledgement.
- Hạn chế đã biết của kiểm tay: đua giữa đổi cỡ lưới và cài app (B3) và gỡ hai app liền nhau (B4) **không ép được xác định** trên máy thật; bằng chứng là đọc code cộng log thứ tự cộng thử nhiều lần.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/data/layout/LayoutReconciler.kt` | Sửa | **Độc quyền** (FX3 giữ) |
| `.../data/layout/db/LauncherItemDao.kt` | Sửa: thêm `@Query UPDATE` đổi `className` | **Độc quyền** (`db/*`) |
| `.../data/layout/db/HiddenAppDao.kt` | Sửa: thêm `@Query UPDATE` đổi `className` | **Độc quyền** (`db/*`) |
| `.../data/layout/LayoutRepositoryImpl.kt` | Sửa: flow bố cục (B5, B1), `changeGridPreset` | Thường |
| `.../domain/layout/GridInsertPlanner.kt` | Sửa: phòng thủ item ngoài lưới, nhiều item tràn | Thường (chữ ký `plan` giữ nguyên; `HomeViewModel`, `IconGridView` gọi) |
| `.../domain/layout/NewAppPlacementPlanner.kt` | **Tạo** (hàm thuần, C20) | Thường |
| `.../domain/usecase/ChangeGridPresetUseCase.kt` | Sửa: thành lớp mỏng gọi `layoutRepository.changeGridPreset` | Thường |
| `.../domain/repository/LayoutRepository.kt` | Sửa: đổi tên `reflowToGrid` → `changeGridPreset` và chú thích; **ngoài vùng ghi trong bảng "Kế hoạch sửa"**, điều phối cho phép 2026-11-11 (chỉ một hàm, nơi gọi duy nhất nằm trong vùng) | Thường |
| `docs/knowledgements/FX3-layout-reconciler.md` | Tạo (knowledgement, theo mục 8) | — |
| `docs/report/FX3-layout-reconciler.md` | Sửa (Nhật ký, bảng bằng chứng) | — |

**Không sửa:** `ui/home/HomeViewModel.kt` (FX1), `ui/home/HomeActivity.kt` và `ui/home/view/*` (FX2), `ui/search/*` (FX4), `data/apps/*` (chỉ gọi `AppRepository.isPackageInstalled` đã có), `ui/settings/*`, `data/layout/db/LauncherDatabase.kt` và các Entity, `app/schemas/*`.

## Global Constraints

- **Không đổi schema Room** (không đổi Entity, `version`, `exportSchema`); `app/schemas/1.json` giữ nguyên byte. Nếu thực thi thấy buộc phải đổi: dừng, báo điều phối xin khóa schema (mục 5.3).
- Mọi hàm công khai của `LayoutReconciler` giữ quy ước: lấy `mutex` đúng một lần, **không gọi hàm công khai khác** (Mutex không tái nhập); phần dùng chung là hàm `private ...InTransaction`. Lệnh chặn hỏi hệ thống (`isPackageInstalled`) chỉ ở **ngoài** `db.withTransaction`, trong `mutex`.
- `currentGrid()` đọc đúng một lần ngay sau khi lấy mutex, trước `withTransaction` (bài học M8a Task 2).
- Không để `CancellationException` bị nuốt: `catch (e: CancellationException) { throw e }` trước `catch (e: Exception)`.
- Log: tag `LayoutReconciler`/`LayoutRepository`, chỉ số đếm và tên package; không tên người, không số điện thoại.
- Comment trong code bằng tiếng Việt như phần còn lại của file; commit message tiếng Anh, dạng `fix: ...`, không dấu.
- Sau mỗi Task: `gradlew.bat assembleDebug` xanh trong worktree (nhớ chép `local.properties`).
- Không `git merge origin/main` (mục 11). Không thêm test, không thêm thư viện.
- Máy dùng chung với việc khác của chủ dự án: điều phối xác nhận máy trống trước mọi lần cài hoặc chạy script (memory "Phone availability").

## Review Focus

1. Schema: `git diff --stat -- app/schemas app/src/main/java/gambi/global/os26launcher/data/layout/db/LauncherDatabase.kt` rỗng; DAO chỉ thêm `@Query UPDATE`.
2. Quy ước mutex: grep `mutex.withLock` cho thấy mọi hàm công khai lấy một lần; không hàm công khai nào gọi hàm công khai khác; `isPackageInstalled` không nằm trong `db.withTransaction`.
3. `placeNewApps`, `catchUp`, `setHidden(false)` cùng đi qua một đường chuẩn bị rồi một transaction; không còn nơi nào đặt app bằng "ô trống đầu tiên".
4. Re-point không bao giờ xóa hàng, chỉ chạy khi có app sống chưa đặt cùng (package, user); `catchUp` vẫn chỉ xóa khi `isPackageInstalled == false` (Q11 giữ).
5. `GridInsertPlanner.plan` cho kết quả **y hệt cũ** khi mọi item nằm trong lưới và tràn đúng một item (so từng nhánh; đây là chỗ không có test đỡ), và không ném `ArrayIndexOutOfBounds` khi có item ngoài lưới hoặc nhiều item tràn.
6. Flow bố cục: một nguồn `createFlow` cho hai bảng; không còn `pageDao.observeAll()`/`itemDao.observeAll()` trong `layout`; mọi lời gọi reconciler trong flow được bọc; `CancellationException` được ném lại.
7. `ChangeGridPresetUseCase` không còn ghi DataStore trực tiếp; thứ tự ghi (thu nhỏ: DB trước, mở rộng: DataStore trước) giữ đúng của M8a.
8. Plan không sửa file ngoài "Vùng file" (kể cả `ui/*`).

---

### Task 1: Log đếm và bọc flow bố cục (B1, phần flow bố cục)
**Bắt buộc kiểm tay:** không — không thuộc loại nào của mục 4.2 (không migration, không cử chỉ mới, không phép đo chọn phương án). Điều phối vẫn chạy kiểm adb bên dưới vì kiểm này đồng thời là **số đo nền** cho B5 trước khi sửa.
**Files:** `data/layout/LayoutRepositoryImpl.kt`
**Interfaces:** Consumes: `LayoutReconciler.seedIfEmpty/ensureFitsGrid/catchUp/placeNewApps` (không đổi). Produces: log `assemble #n pages=P items=I apps=A` (cấp `d`, tag `LayoutRepository`).
- [x] **Step 1:** thêm bộ đếm `AtomicInteger` và một dòng `Log.d(TAG, "assemble #$n pages=${pages.size} items=${items.size} apps=${apps.size}")` ở đầu `assemble`. Chỉ có dòng log này ở bước đo nền; chưa đổi flow.
- [x] **Step 2:** thêm hàm nội bộ `runReconcile(name, block)`: `try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(TAG, "$name failed", e) }`. Bọc `seedIfEmpty`, `ensureFitsGrid`, `catchUp` trong `flow { }` và `placeNewApps` trong `onEach`. Lệnh đọc snapshot (có try riêng) do Task 2 thêm; ở Task này flow vẫn là `combine` của hai `observeAll`.
- [x] **Step 3 (sửa sau review điều phối, vòng 1/5):** thay `.catch` cuối bằng `retryWithBackoff` (`retryWhen`, trễ 500 ms → 5 s, log mỗi lần thử lại, không bắt `CancellationException`) giữa `.flowOn(io)` và `.shareIn(...)`: `.catch` cuối làm flow chết hẳn và Home đứng hình vì `HomeViewModel` thu cả đời process nên `WhileSubscribed` không khởi động lại upstream. `seedIfEmpty` **không** bọc `runReconcile` (lỗi seed đi lên `retryWithBackoff`, không chạy `catchUp`/`placeNewApps` trên DB rỗng).
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)** — xem phần "Quy trình sao lưu và khôi phục" và "Công cụ kiểm" bên dưới.
  - (a) Cài APK của commit này, mở Home: bố cục như cũ, `adb logcat -b crash` rỗng, logcat có dòng `assemble #1 ...`.
  - (b) **Số đo nền B5/C17:** `adb logcat -c`, rồi Cài đặt → cỡ lưới 4x6 → 4x5 (xác nhận). Ghi lại các dòng `assemble` (kèm `-v time`): mong đợi **từ 2 dòng trở lên** cho một lần đổi (đổi cỡ ghi cả `home_pages` và `launcher_items` trong một transaction); ghi số dòng, `pages=`, `items=` của từng dòng. Đổi lại 4x6 và ghi tiếp. Đây là bằng chứng "trước".
  - (c) **Ép lỗi Room (tùy chọn, nếu ép được):** `force-stop`; `run-as ... chmod 444 databases/launcher.db*`; cài app thử `dev.fx3.b4a` (xem công cụ) để `placeNewApps` phải ghi; mở Home. Mong đợi: logcat có `placeNewApps failed` (cấp `e`), **không** `FATAL EXCEPTION`; bố cục vẫn hiện. Trả quyền `chmod 664`, gỡ app thử, khôi phục DB từ bản sao lưu. Không ép được lỗi (ROM mở DB ở chế độ khác) thì ghi "không ép được", bằng chứng B1 là đọc code (Review Focus 6).

---

### Task 2: Một flow cho hai bảng (B5, C17)
**Bắt buộc kiểm tay:** không — kiểm bằng log đếm, không phải cử chỉ mới hay quyết định chọn phương án (B5-1 đã chọn bằng source Room; số đo xác nhận chứ không dùng để chọn).
**Files:** `data/layout/LayoutRepositoryImpl.kt`
**Interfaces:** Consumes: `LauncherDatabase` (inject thêm vào constructor; đã là singleton của Hilt), `HomePageDao.getAll`, `LauncherItemDao.getAll`. Produces: `pages`/`dock` như cũ (vẫn `distinctUntilChanged`).
- [x] **Step 1:** inject `private val db: LauncherDatabase`. Thêm `private class DbSnapshot(val pages: List<HomePageEntity>, val items: List<LauncherItemEntity>)` và `private fun dbSnapshots(): Flow<DbSnapshot> = db.invalidationTracker.createFlow("launcher_items", "home_pages").mapNotNull { try { db.withTransaction { DbSnapshot(pageDao.getAll(), itemDao.getAll()) } } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(TAG, "read snapshot failed", e); null } }`. Dòng log `snapshot #n pages=P items=I` ở đây (cùng bộ đếm tách riêng) để thấy một lần ghi → một snapshot.
- [x] **Step 2:** trong `layout`, thay `combine(pageDao.observeAll(), itemDao.observeAll(), apps...)` bằng `combine(dbSnapshots(), apps.filter{...}.onEach{ runReconcile(...) { placeNewApps(it) } }) { snap, apps -> assemble(snap.pages, snap.items, apps) }`. `itemDao.getAll()` không có `ORDER BY` còn `observeAll()` sắp theo `pageId, gridY, gridX`, và `assemble` giữ thứ tự item của DB khi dựng từng trang (chỉ trang được `sortedBy rank`, con thư mục `sortedBy rank`). Để kết quả y hệt, `DbSnapshot` sắp `items` bằng `sortedWith(compareBy({ it.pageId }, { it.gridY }, { it.gridX }))` trước khi `assemble`; đọc lại `assemble` xem còn chỗ nào dựa vào thứ tự khác không.
- [x] **Step 3:** bỏ import không dùng; `observeAll()` của hai DAO còn dùng ở `hiddenApps` (hidden) nên **giữ nguyên DAO**, không xóa.
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)**
  - (a) `adb logcat -c`; đổi cỡ lưới 4x6 → 4x5: mong đợi **đúng 1 dòng** `snapshot` và **1 dòng** `assemble` cho lần đổi (trước: từ 2). Đổi lại 4x5 → 4x6: cũng 1.
  - (b) Thả icon lên trang tạm (ghost page ở cuối, edit mode) rồi thả xong: 1 snapshot (cùng transaction tạo `home_pages` và đặt item). Kéo icon giữa hai trang bình thường: 1 snapshot.
  - (c) Hồi quy ngắn: vuốt qua mọi trang, mở/đóng thư mục, ẩn rồi bỏ ẩn một app, mở Home sau `force-stop`: bố cục y hệt ảnh/DB trước khi thử; `logcat -b crash` rỗng.
  - (d) Dán số dòng trước/sau vào Nhật ký.

---

### Task 3: Đổi cỡ lưới nguyên tử (B3, phần 1)
**Bắt buộc kiểm tay:** không — loại lỗi đua không ép được; kiểm bằng log thứ tự và hồi quy đổi cỡ lưới của M8a.
**Files:** `data/layout/LayoutReconciler.kt`, `data/layout/LayoutRepositoryImpl.kt`, `domain/repository/LayoutRepository.kt` (điều phối cho phép), `domain/usecase/ChangeGridPresetUseCase.kt`
**Interfaces:** Consumes: `SettingsRepository.setGridPreset/settings` (reconciler đã inject), `reflowInTransaction`. Produces: `LayoutRepository.changeGridPreset(target: GridPreset)` (thay `reflowToGrid`).
- [x] **Step 1:** trong `LayoutReconciler` thay `reflowTo(preset)` bằng `changeGridPreset(target)`: một `mutex.withLock { val current = currentGrid(); if (target == current) return@withLock; if (target.capacity < current.capacity) { db.withTransaction { reflowInTransaction(target) }; settingsRepository.setGridPreset(target) } else { settingsRepository.setGridPreset(target); db.withTransaction { reflowInTransaction(target) } } }`. Log `changeGrid $current->$target done`. `ensureFitsGrid` giữ nguyên.
- [x] **Step 2:** `LayoutRepository` và `LayoutRepositoryImpl`: đổi `reflowToGrid` → `changeGridPreset(target)` (chú thích nói rõ: ghi cả cài đặt, cả hai nằm trong một vùng khóa của reconciler). `ChangeGridPresetUseCase.invoke(target) = layoutRepository.changeGridPreset(target)`; đưa KDoc về thứ tự ghi sang `LayoutReconciler.changeGridPreset`, use case giữ một dòng trỏ tới đó. `SettingsViewModel` không đổi.
- [x] **Step 3:** thêm vào `placeNew*` một dòng log có `grid=${grid.columns}x${grid.rows}` (Task 5 sẽ dùng) để thứ tự với `changeGrid done` nhìn thấy được.
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)**
  - (a) Đổi 4x6 → 4x5 → 5x6 → 4x6: mỗi lần bố cục dồn đúng như M8a (so số trang và tổng số icon theo DB: `SELECT COUNT(*) FROM launcher_items WHERE container=-100`, không mất icon; `SELECT COUNT(*) FROM launcher_items WHERE container=-100 AND (gridX+spanX>C OR gridY+spanY>R)` = 0 với C, R của cỡ vừa đặt). DataStore khớp cỡ (mở Cài đặt xem hàng cỡ lưới).
  - (b) **Thử đua (cố gắng, không xác định):** chạy trong một lệnh PowerShell: bấm xác nhận đổi 4x6 → 4x5 (qua `uiautomator` tìm tọa độ nút) và đồng thời `adb install -r` APK thử `dev.fx3.b4a` bằng `Start-Job`. Lặp 3 lần (mỗi lần gỡ app thử). Sau mỗi lần: không icon nào ngoài lưới (truy vấn (a)), không crash, app thử nằm trong lưới. Logcat cho thấy `changeGrid ... done` xuất hiện **trước** `placeNew: ... grid=4x5` (nếu cài chạm trúng khoảng đó) hoặc sau `grid=4x6` trước khi bắt đầu. Ghi "không ép được đua" nếu cả 3 lần cài rơi ngoài cửa sổ.
  - (c) Trả cỡ lưới 4x6 (đã khôi phục ở cuối bằng sao lưu).

---

### Task 4: Planner phòng thủ item ngoài lưới và nhiều item tràn (B3, phần 2)
**Bắt buộc kiểm tay:** không — kéo-thả không phải cử chỉ mới; kịch bản ép lỗi dưới đây thay thế.
**Files:** `domain/layout/GridInsertPlanner.kt`
**Interfaces:** Consumes: `PageGeometry` (`internal`, cùng module). Produces: `GridInsertPlanner.plan` chữ ký **không đổi**; không còn ném `ArrayIndexOutOfBoundsException` với dữ liệu lệch.
- [x] **Step 1:** thêm hàm private `isInside(slot, columns, rows) = slot.x in 0 until columns && slot.y in 0 until rows` (chỉ số đọc `y*columns+x` sẽ không còn đụng nhau khi `x >= columns`). Trong `plan`: tập `icons` (dòng `inPage.filter { !PageGeometry.isWidget(it) }`) lọc thêm `isInside`; item ngoài lưới **không bị chạm** (không chiếm ô, không bị dịch; `ensureFitsGrid` chữa lúc khởi động). Làm tương tự trong `planWidget` (`icons`) và `insertBlock` (`icons`).
- [x] **Step 2:** thay phần tràn trong `plan`: `val capacity = (free.size - (k + 1)).coerceAtLeast(0); val shifted = toShift.take(capacity); val spill = toShift.drop(capacity)`. `spill` là **khối có thứ tự** (có thể nhiều hơn 1) đẩy sang trang kế bằng cùng vòng `while (block.isNotEmpty()) { pageIndex++ ...; block = insertBlock(...) }` của `planWidget`. Với trường hợp tràn đúng 1 item (đường duy nhất hiện nay), `insertBlock` với khối một phần tử cho kết quả y hệt vòng lặp cũ (KDoc `insertBlock` ghi sẵn; **đối chiếu từng nhánh** khi viết: ô đầu của dãy tự do, người đứng sẵn dồn sát, trang đầy widget không còn ô tự do sang trang kế). Viết một bảng đối chiếu ngắn vào Nhật ký.
- [x] **Step 3:** kiểm `HomeViewModel.planGridOptimistic` (dòng ~916) và `IconGridView.kt:653` vẫn biên dịch (chữ ký không đổi); không sửa chúng.
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy) — tái hiện B3 không cần đua, bằng hàng trùng ô** (xem "Công cụ kiểm" để biết lệnh sửa DB):
  - (a) **Chuẩn bị:** `force-stop`, chọn một trang đầy 24 icon (4x6) ở trang N và một icon nguồn ở trang khác M. Sửa DB bản sao: chèn thêm **hai** hàng trùng ô (sao chép hai hàng app trên trang N sang cùng `pageId`, `gridX`, `gridY`, container `-100`) để trang N có 26 hàng, hàng trùng ở ô có chỉ số lớn hơn 0 (không phải ô (0,0)). Đẩy DB lên máy, mở Home.
  - (b) **Trước sửa (nếu điều phối muốn bằng chứng đỏ, cài commit Task 3):** vào edit mode, kéo icon nguồn từ trang M thả lên ô (0,0) của trang N. Mong đợi crash `ArrayIndexOutOfBoundsException` ở `GridInsertPlanner` (luồng chính, `HomeViewModel.planGridOptimistic`). `adb logcat -b crash` lưu lại.
  - (c) **Sau sửa (commit Task 4):** cùng thao tác: không crash; icon nguồn ở (0,0) trang N; icon tràn sang trang N+1 (hoặc trang mới) theo thứ tự đọc; `SELECT COUNT(*)` item trang N không vượt 26 và không ném lỗi trong `LayoutReconciler.moveItem` (DB). 
  - (d) Hồi quy: kéo thường trên trang đầy bình thường (24 icon, không hàng trùng): kết quả giống trước sửa (so với DB sao lưu hoặc quan sát một lần: icon cuối trang tràn sang ô (0,0) của trang kế).
  - (e) Khôi phục DB từ sao lưu.

---

### Task 5: Một đường đặt app: chuẩn bị ngoài transaction, lọc app đã gỡ (B4)
**Bắt buộc kiểm tay:** không — không thuộc loại nào của mục 4.2.
**Files:** `data/layout/LayoutReconciler.kt`
**Interfaces:** Consumes: `AppRepository.isPackageInstalled(pkg, serial)` (đã có, chặn). Produces: hàm private `selectFresh(apps: List<AppItem>): List<AppItem>` (ngoài transaction); `placeNewInTransaction(apps, fresh, grid, allowRepoint)` (Task 6, 7 mở rộng).
- [x] **Step 1:** thêm `selectFresh(apps)`: đọc `itemDao.getAll()` và `hiddenAppDao.getAll()` (DAO suspend, ngoài transaction, an toàn vì mọi ghi đều qua `mutex` đang giữ), tính `fresh = apps.filter { key !in known && key !in hidden }` y như `placeNewInTransaction` hiện hành, rồi với mỗi (package, userSerial) khác nhau của `fresh` hỏi `appRepository.isPackageInstalled(pkg, serial)` một lần (cache trong hàm); bỏ app của package trả `false` và `Log.d("placeNew: skipped N not installed")`.
- [x] **Step 2:** `placeNewApps(apps)` = `mutex.withLock { grid = currentGrid(); fresh = selectFresh(apps); if (fresh.isEmpty()) return; db.withTransaction { placeNewInTransaction(fresh, grid) } }`. Trong `placeNewInTransaction` vẫn **tính lại** `known`/`hidden` từ DB trong transaction và lọc `fresh` thêm một lần (an toàn tuyệt đối; idempotent). `catchUp` gọi `selectFresh(apps)` **cùng chỗ** với `gone` (ngoài transaction), rồi truyền `fresh` vào `placeNewInTransaction`. `setHidden(false)`: lấy app, `selectFresh(listOf(app))`, rồi transaction (chưa có re-point).
- [x] **Step 3:** log gộp `placeNew: fresh=F skippedNotInstalled=K grid=CxR`.
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)**
  - (a) Hồi quy cài/gỡ: cài `dev.fx3.b4a` và `dev.fx3.b4b` (cùng lúc): hai app vào lưới, không trùng ô, `placeNew: fresh=2`. Mở lại Home sau `force-stop`: không đổi.
  - (b) **Thử đua gỡ liền nhau (cố gắng):** `adb shell "pm uninstall dev.fx3.b4a & pm uninstall dev.fx3.b4b & wait"`; lặp 3 lần (cài lại giữa các lần). Sau mỗi lần: `SELECT COUNT(*) FROM launcher_items WHERE packageName LIKE 'dev.fx3.b4%'` = 0 và không hàng ma nào ở trang (trước khi sửa hàng có thể quay lại); log có thể có `skipped N not installed` nếu cửa sổ đua trúng. Ghi số lần trúng.
  - (c) Bỏ ẩn một app qua màn Thư viện: app hiện ở lưới như cũ.

---

### Task 6: Đặt app mới sau icon cuối cùng (C20)
**Bắt buộc kiểm tay:** không — hành vi đặt chỗ, kiểm bằng DB; chủ dự án kiểm cảm nhận ở bài kiểm cuối.
**Files:** `domain/layout/NewAppPlacementPlanner.kt` (tạo), `data/layout/LayoutReconciler.kt`
**Interfaces:** Consumes: `GridInsertPlanner.PageSlots/Slot`, `PageGeometry.freeIndices`. Produces: `NewAppPlacementPlanner.plan(pages: List<GridInsertPlanner.PageSlots>, count: Int, columns: Int, rows: Int): List<Spot>`, `Spot(pageIndex: Int, x: Int, y: Int)`; `pageIndex >= pages.size` là trang mới thứ `pageIndex - pages.size`.
- [x] **Step 1:** viết planner thuần (không `android.*`): trang cuối = phần tử cuối của `pages` (theo rank do người gọi sắp); `lastIconIndex` = max `y*columns+x` của slot **không phải widget** nằm **trong lưới** (thư mục tính như icon), `-1` nếu không có; ứng viên = các chỉ số tự do (`PageGeometry.freeIndices`, nhảy qua ô widget phủ) lớn hơn `lastIconIndex`, tăng dần; lấy lần lượt cho `count` app; hết ứng viên thì sang trang mới (mọi ô tự do, từ 0), tiếp tục. `pages` rỗng: coi như một trang trống mới. KDoc mô tả D1/D3/D5.
- [x] **Step 2:** trong `placeNewInTransaction`, thay vòng "tìm ô trống đầu tiên" (`search@ for page in pages` và `occupied` theo `cellKey`) bằng gọi planner với ảnh chụp trang từ `itemDao.getAll()` (HOME, theo `pageId`, `toSlot()`), tạo `pageDao.insert` cho các trang mới theo `Spot.pageIndex`, rồi `itemDao.insertAll`. Xóa `cellKey`/`CELL_KEY_STRIDE` nếu không còn dùng. Thứ tự app = thứ tự `fresh` (đã theo nhãn).
- [x] **Step 3:** `compactPageAfterRemoval`, `deleteAndCompact`, `seedIfEmpty`, `addWidget*` **không đổi** (D1, D2, D6). Cập nhật KDoc của `placeNewApps` (không còn "ô trống đầu tiên (Q7)").
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)**
  - (a) **Trước khi thử:** pull DB (Công cụ kiểm), in bố cục HOME theo trang: `SELECT p.rank, i.gridX, i.gridY, i.packageName FROM launcher_items i JOIN home_pages p ON p.id=i.pageId WHERE i.container=-100 ORDER BY p.rank, i.gridY, i.gridX`. Chọn một trang có **khe giữa trang** (báo cáo ghi trang 2 ô (0,4)) làm "bẫy" và ghi icon cuối + trang cuối.
  - (b) Cài `dev.fx3.b4a`: hàng mới nằm ở **trang cuối**, ô ngay sau icon cuối (hoặc trang mới nếu trang cuối không còn ô sau icon cuối); **không** ở khe giữa trang. Cài thêm `b4b`: ngay sau `b4a`.
  - (c) Gỡ `b4a` rồi `b4b`: mọi hàng khác **không đổi** so với (a) (so từng hàng; báo cáo C20 đo trước sửa có 3 icon bị dồn). Khác với (a) chỉ ở hai app thử.
  - (d) Bỏ ẩn (D4): ẩn một app qua dấu "−", mở Thư viện → "Đã ẩn" → bấm app: nó về **sau icon cuối** (không phải ô vừa trống). Ghi vị trí.
  - (e) Hồi quy: cài/gỡ khi trang cuối đầy (nếu có): tạo trang mới; gỡ app duy nhất trên trang mới: trang cuối trống bị xóa (hành vi `deleteTrailingEmptyPages` cũ).

---

### Task 7: Re-point hàng mồ côi khi app đổi tên activity (A3)
**Bắt buộc kiểm tay:** không — không thuộc loại nào của mục 4.2 (không migration, không cử chỉ mới); kiểm đầu-cuối bằng `adb install -r`.
**Files:** `data/layout/LayoutReconciler.kt`, `data/layout/db/LauncherItemDao.kt`, `data/layout/db/HiddenAppDao.kt`
**Interfaces:** Consumes: `selectFresh` (Task 5). Produces: `LauncherItemDao.renameComponent(packageName, oldClass, newClass, userSerial): Int` và `HiddenAppDao.renameComponent(...)`, cả hai `@Query UPDATE`; `placeNewInTransaction(apps, fresh, grid, allowRepoint)`.
- [x] **Step 1:** hai `@Query`: `UPDATE launcher_items SET className = :newClass WHERE packageName = :packageName AND className = :oldClass AND userSerial = :userSerial AND itemType = 0` và `UPDATE hidden_apps SET className = :newClass WHERE packageName = :packageName AND className = :oldClass AND userSerial = :userSerial`. Không đụng Entity.
- [x] **Step 2:** trong transaction, trước khi đặt chỗ, khi `allowRepoint` (đúng cho `placeNewApps` và `catchUp`, sai cho `setHidden(false)`): từ `rows`, `hidden` và tập khóa sống `live = apps.mapTo(HashSet()) { it.key }` (`apps` đầy đủ, nên `placeNewInTransaction` nhận cả `apps` lẫn `fresh`) gom `orphan: Map<(pkg, serial), LinkedHashSet<className>>` từ các hàng `ITEM_TYPE_APP` (theo `id`) rồi hàng `hidden_apps` có khóa **không** thuộc `live`. Với mỗi (pkg, serial) có cả `orphan` lẫn app trong `fresh`, ghép tuần tự `className` mồ côi với app `fresh` (theo thứ tự `fresh`); mỗi cặp gọi hai `renameComponent` (item, hidden) và loại app đó khỏi `fresh`. Log `repoint $pkg $old -> $new items=$n hidden=$m` (số hàng DAO trả về).
- [x] **Step 3:** giữ nguyên `catchUp` (chỉ xóa theo `isPackageInstalled`); thêm vào KDoc của `catchUp` mục "A3: hàng mồ côi của package còn cài được re-point ở `placeNewInTransaction`; Q11 không đổi". Không xóa hàng nào ở đây.
> **Điều phối (2026-11-11), app thử A3:** để app thử mở được và đổi tên giống thật, dựng manifest có `<activity android:name="android.app.Activity" android:exported="false"/>` và một `<activity-alias>` có bộ lọc LAUNCHER trỏ `targetActivity="android.app.Activity"`; v1 đặt tên alias `.OldMain`, v2 đặt `.NewMain`. Như vậy ComponentName đổi giữa hai bản mà app vẫn chạy được nếu lỡ bấm. Điều phối tự dựng và chạy aapt2 (công thức đã dùng ở đợt rà).
- [ ] **Step 4: Kịch bản kiểm (Live 4, điều phối chạy)** — dùng app thử `dev.fx3.renametest` (v1 activity khởi động `.OldMain`, v2 đổi thành `.NewMain`, xem "Công cụ kiểm"). **Không bấm mở app thử** (không có code).
  - (a) **Lưới:** cài v1; ghi hàng `SELECT id,container,pageId,gridX,gridY,rank,className FROM launcher_items WHERE packageName='dev.fx3.renametest'` (1 hàng, `className = dev.fx3.renametest.OldMain`). `adb install -r` v2; đợi 6 giây; pull DB. **Mong đợi (sau sửa):** vẫn **1 hàng**, cùng `id`, cùng trang/ô/`rank`, `className = dev.fx3.renametest.NewMain`; icon trên Home giữ chỗ. **Trước sửa (số đo nền, cài commit Task 6):** 2 hàng (`OldMain` còn nguyên, `NewMain` ở ô mới).
  - (b) **Dock:** gỡ app thử, khôi phục DB, cài v1 lại; `force-stop`; sửa DB bản sao: `DELETE FROM launcher_items WHERE container=-101 AND rank=3; UPDATE launcher_items SET container=-101,pageId=0,gridX=3,gridY=0,rank=3 WHERE packageName='dev.fx3.renametest'`; đẩy DB lên, mở Home (app thử ở ô dock thứ 4); `install -r` v2. **Mong đợi:** dock vẫn 4 icon, ô thứ 4 là app thử, DB 1 hàng `NewMain`, chụp màn hình (`screencap`) kèm. Kiểm kéo thêm một icon vào dock không bị từ chối oan vì hàng ma (nếu dock có chỗ).
  - (c) **`hidden_apps`:** gỡ app thử, khôi phục DB, cài v1; `force-stop`; sửa DB: `DELETE FROM launcher_items WHERE packageName='dev.fx3.renametest'; INSERT INTO hidden_apps VALUES('dev.fx3.renametest','dev.fx3.renametest.OldMain',0)`; mở Home; `install -r` v2. **Mong đợi:** `hidden_apps` có `NewMain` (không còn `OldMain`), `launcher_items` **không** có hàng nào của app thử (nó vẫn ẩn, không hiện lại ở lưới); màn Thư viện → "Đã ẩn" liệt kê app.
  - (d) **Q11 không đổi:** `pm disable-user --user 0 dev.fx3.renametest` (v2 đã đặt), mở Home: hàng còn nguyên (không xóa), icon biến; `pm enable`: icon về đúng ô.
  - (e) **Gỡ thật vẫn dọn:** `adb uninstall dev.fx3.renametest`: hàng và `hidden_apps` biến mất, dồn ô như cũ.

---

### Task 8: Đóng nhánh
**Bắt buộc kiểm tay:** không.
**Files:** `docs/knowledgements/FX3-layout-reconciler.md`, `docs/report/FX3-layout-reconciler.md`
- [ ] **Step 1:** viết knowledgement theo mục 8 (Mermaid `sequenceDiagram` cho: cài app → `placeNewApps` → chuẩn bị ngoài transaction → ghi → `createFlow` → một snapshot; `sequenceDiagram` cho cập nhật app có đổi tên activity; `flowchart` quy tắc đặt chỗ C20 và ghép re-point). Bài học cho AGENTS.md (chủ đề `drag-and-layout`, `settings-and-data`).
- [ ] **Step 2:** điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung". Báo điều phối rebase (mục 11), build xanh, chuyển PR sang Ready.

---

## Công cụ kiểm (điều phối chạy, PowerShell, bản debug)

Máy dùng chung: xác nhận chủ dự án không dùng máy trước khi cài hoặc chạy. Đặt `$P = "gambi.global.os26launcher"`, `$B = "$env:USERPROFILE\fx3-backup"` (ngoài repo), `$W = "$env:USERPROFILE\fx3-test"`.

**Sao lưu (trước mọi việc), theo quy trình DD đã dùng:** bẫy PowerShell 5.1: `>` ghi UTF-16 làm hỏng file nhị phân, lệnh ra nhị phân phải qua `cmd /c`.
- **S1.** `adb shell am force-stop $P`; `adb shell run-as $P ls databases files no_backup` (xem có những file nào, kiểm `run-as`).
- **S2.** `adb shell run-as $P sh -c "find databases files no_backup -type f | sort | xargs sha256sum" > $B\before.txt`
- **S3.** `cmd /c "adb exec-out run-as $P tar cf - databases files no_backup > %USERPROFILE%\fx3-backup\state.tar"`
- **S4.** `tar -tvf $B\state.tar` (đủ file của S1); so `adb shell run-as $P sh -c "tar cf - databases files no_backup | sha256sum"` với `Get-FileHash -Algorithm SHA256 $B\state.tar` (khác thì làm lại S3, không sang bước sau).
- **Khôi phục R1–R3** (cuối buổi kiểm, và giữa các kịch bản cần DB sạch): `adb shell am force-stop $P`; `adb shell run-as $P rm -rf databases files no_backup`; `adb push $B\state.tar /data/local/tmp/fx3_state.tar`; `adb shell "cat /data/local/tmp/fx3_state.tar | run-as $P tar xf -"`; `adb shell rm /data/local/tmp/fx3_state.tar`; chạy lại S2 ra `$B\after.txt` và `Compare-Object (Get-Content $B\before.txt) (Get-Content $B\after.txt)` phải rỗng. Gỡ hết app thử (`adb uninstall dev.fx3.renametest`, `dev.fx3.b4a`, `dev.fx3.b4b`). Mở Home, so với ảnh chụp trước khi thử. Chỉ xóa `state.tar` khi chủ dự án xác nhận bố cục đúng.

**Đọc DB trên máy dev:**
```powershell
function Get-FxDb($dir) {
  New-Item -ItemType Directory -Force $dir | Out-Null
  foreach ($f in 'launcher.db','launcher.db-wal','launcher.db-shm') {
    cmd /c "adb exec-out run-as $P cat databases/$f > $dir\$f"
  }
}
function Q($dir, $sql) {
  python -I -c "import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);[print(r) for r in c.execute(sys.argv[2])]" "$dir\launcher.db" $sql
}
```
(Phải lấy đủ ba file vì WAL, bài học M4 Task 4.)

**Sửa DB bản sao rồi đẩy lên** (kịch bản Task 4 và Task 7b, c): `adb shell am force-stop $P`; `Get-FxDb $W\db`; sửa bằng Python `sqlite3` (`execute`, `commit`, rồi `PRAGMA wal_checkpoint(TRUNCATE)`, đóng kết nối); `adb push $W\db\launcher.db /data/local/tmp/fx3.db`; `adb shell "run-as $P sh -c 'cat /data/local/tmp/fx3.db > databases/launcher.db; rm -f databases/launcher.db-wal databases/launcher.db-shm'"`; `adb shell rm /data/local/tmp/fx3.db`; mở Home.

**Dựng APK thử không code** (chủ dự án cho phép APK tự dựng; khóa debug, cùng khóa cho v1 và v2 để `install -r` cập nhật được). Build-tools `E:\SDK_Components\build-tools\35.0.1`, `android.jar` `E:\SDK_Components\platforms\android-36`.
```powershell
$BT = "E:\SDK_Components\build-tools\35.0.1"; $AJ = "E:\SDK_Components\platforms\android-36\android.jar"
function New-FxApk($pkg, $label, $ver, $cls, $out) {
  $d = "$W\$pkg-v$ver"; New-Item -ItemType Directory -Force $d | Out-Null
  @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg"
    android:versionCode="$ver" android:versionName="$ver">
  <application android:hasCode="false" android:label="$label">
    <activity android:name="$cls" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>
"@ | Set-Content -Encoding utf8 "$d\AndroidManifest.xml"
  & "$BT\aapt2.exe" link -o "$d\u.apk" --manifest "$d\AndroidManifest.xml" -I $AJ --min-sdk-version 26 --target-sdk-version 30
  & "$BT\zipalign.exe" -f -p 4 "$d\u.apk" "$d\a.apk"
  & "$BT\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out $out "$d\a.apk"
}
New-FxApk "dev.fx3.renametest" "FX3 Rename" 1 ".OldMain" "$W\fx3-rename-v1.apk"
New-FxApk "dev.fx3.renametest" "FX3 Rename" 2 ".NewMain" "$W\fx3-rename-v2.apk"
New-FxApk "dev.fx3.b4a" "FX3 B4A" 1 ".Main" "$W\fx3-b4a.apk"
New-FxApk "dev.fx3.b4b" "FX3 B4B" 1 ".Main" "$W\fx3-b4b.apk"
```
Cài: `adb install -r $W\fx3-rename-v1.apk` (cập nhật: `adb install -r $W\fx3-rename-v2.apk`). Không bấm mở app thử (class không tồn tại). Ba tham số của `aapt2 link` đã dùng được ở bản chính; nếu một cờ không chạy trên build-tools này, điều phối báo lại, không đổi sang cách khác khi chưa thử.

**Log:** `adb logcat -c`, thao tác, `adb logcat -d -v time -s LayoutRepository:D LayoutReconciler:D`; `adb logcat -b crash -d` để kiểm sập.

**Mở app và kiểm đang ở trước:** `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity`; trước mỗi thao tác chạm kiểm Os26Launcher đang ở trước.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11, Os26Launcher là launcher mặc định). **Dữ liệu:** bố cục thật của chủ dự án (sao lưu bằng S1–S4), bản debug của nhánh, bốn APK thử. **Cuối buổi: khôi phục R1–R3 và gỡ app thử** vì cài và gỡ app thử đổi bố cục của chủ dự án.

Thứ tự theo thao tác tự nhiên:
1. Sao lưu S1–S4 và ghi bố cục HOME theo trang (truy vấn ở Task 6a) cùng số item.
2. (Nếu cần "trước") số đo nền B5 trên commit Task 1: đổi cỡ 4x6 → 4x5 → 4x6, ghi số dòng `assemble`. Rồi cài nhánh hoàn chỉnh.
3. Task 2: đổi cỡ lưới, một dòng `snapshot` và `assemble` mỗi lần; thả lên trang tạm; kéo giữa hai trang.
4. Task 3: đổi 4x6 → 4x5 → 5x6 → 4x6, truy vấn không icon ngoài lưới; thử đua cài app khi đổi cỡ (cố gắng).
5. Task 6: cài `b4a`, `b4b` ở trạng thái bố cục thật; kiểm vị trí sau icon cuối, không vào khe; gỡ, so bố cục.
6. Task 5: gỡ liền hai app thử (cố gắng 3 lần).
7. Task 6d: ẩn rồi bỏ ẩn một app; vị trí sau icon cuối.
8. Task 7a–e: cài v1, `install -r` v2 (lưới, dock, hidden, disable/enable, gỡ).
9. Task 4: dựng trang 26 hàng, kéo icon vào ô (0,0); hồi quy kéo thường.
10. Task 1c: ép lỗi Room (tùy chọn, làm cuối vì có thể làm hỏng DB tạm).
11. Hồi quy chung: vuốt mọi trang, mở/đóng thư mục, edit mode, thêm widget (vẫn vào ô trống đầu tiên, D6), `force-stop` rồi mở lại; `logcat -b crash` rỗng suốt buổi.
12. Khôi phục R1–R3, kiểm hash khớp.

**Kết quả kiểm chứng (Vsmart Live 4, Android 11, bản debug, Os26Launcher là launcher mặc định; 2026-11-11 14:01-14:08; `FX3.apk` = nhánh@81e3ce7; điều phối chạy bằng adb, có sao lưu và trả lại):**

| # | Ca | Kết quả (đo được) |
|---|---|---|
| 1 | Khởi động | `snapshot #1 pages=4 items=97` và `assemble #1`, không `FATAL` |
| 2 | B5/C17: cài hai app thử | Mỗi lần cài `placeNew: fresh=1 grid=4x6`; **một** dòng `snapshot` cho mỗi lần ghi DB (`snapshot #2` rồi `#3` cho hai lần cài). Các dòng `assemble` thừa đến từ lần phát danh sách app (đúng dự kiến) |
| 3 | C20: bố cục chủ dự án có khe giữa trang (trang 1: ô (4,2), (4,3), (5,0)); trang cuối (rank 3) có icon cuối ở (x0, y4) | `dev.fx3.b4a` và `b4b` vào (x1, y4) và (x2, y4) trang rank 3, ngay sau icon cuối, không lấp khe nào. Gỡ cả hai: bản dump toàn bố cục **giống hệt** trước khi cài |
| 4 | A3 lưới: v1 (alias `.OldMain`) | Hàng `id 123`, rank 3, ô (1,4). `install -r` v2 (`.NewMain`): **cùng `id 123`, cùng ô**, `className = ...NewMain`; log `repoint dev.fx3.renametest ...OldMain -> ...NewMain items=1 hidden=0` |
| 5 | Q11 | `pm disable-user`: hàng giữ nguyên; `pm enable`: cùng hàng, cùng ô. Gỡ app: bố cục giống hệt lúc đầu |

**Chưa chạy trên máy (ghi rõ):** đổi cỡ lưới qua giao diện (đua B3), gỡ hai app liền nhau (đua B4), tái hiện B3 bằng hàng trùng ô, A3 biến thể dock và `hidden_apps`, ép lỗi Room (B1). Mức bảo đảm của các ca này: **đọc code + review độc lập** (người review kiểm sự tương đương của planner với trường hợp tràn một item và các đường mutex); riêng đổi cỡ lưới 4x6 -> 4x5 -> 4x6 là việc **chủ dự án kiểm khi duyệt PR** (không icon nào mất hoặc chồng nhau). Các dòng log đếm `assemble`/`snapshot` dùng cho số đo trên đã được gỡ ở commit đóng nhánh.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | — | Viết plan (chưa có code, chưa có git) | — | Chờ điều phối rà |
| 2026-11-11 | 1 | `runReconcile` bọc 4 lời gọi reconciler trong flow bố cục, `.catch` trước `shareIn`, log `assemble #n`. Build xanh | e9adc02 | Chờ review; số đo nền B5 chạy trên commit này |
| 2026-11-11 | 2 | `dbSnapshots()` = `invalidationTracker.createFlow` + một `withTransaction` đọc hai bảng, item sắp `pageId, gridY, gridX` như `observeAll` cũ; `combine` còn 2 nguồn; log `snapshot #n`. Build xanh | 959f36c | Không cần gì ngoài plan. `observeAll()` của hai DAO còn nguyên (không dùng nữa trong repo) |
| 2026-11-11 | 3 | `LayoutReconciler.changeGridPreset` (reflow + ghi DataStore trong một mutex, giữ thứ tự M8a); `reflowToGrid` đổi tên `changeGridPreset`; use case thành lớp mỏng; log `changeGrid ... done`. Build xanh | 46606c0 | Chú thích `GridPreset.kt` (ngoài vùng) còn nhắc `ChangeGridPresetUseCase` chọn thứ tự: đề xuất điều phối sửa sau |
| 2026-11-11 | 4 | `GridInsertPlanner.plan`: `isInside` loại item ngoài lưới; phần tràn là khối có thứ tự qua `insertBlock` (cũng ở `planWidget`, `insertBlock`). Build xanh | e126637 | Đối chiếu nhánh với bản cũ: tràn đúng 1 item thì `take(capacity)` = n-1 item đầu và `drop` = item cuối y như `toShift.dropLast(1)`/`last()`; vòng lặp cũ cho item tràn (`targetIndex = 0`) = `insertBlock` khối 1 phần tử: ô tự do đầu tiên (`k = 0`), có icon đứng đó thì khối + mọi icon dồn sát, trang kín widget thì sang trang kế; trang mới tạo ở cùng điểm (`pageIndex == working.size`) |
| 2026-11-11 | 5 | `selectFresh` (đọc DAO, lọc `isPackageInstalled` trên `io`, ngoài transaction) dùng chung bởi `placeNewApps`, `catchUp`, `setHidden(false)`; `placeNewInTransaction` lọc lại. Thêm inject `@IoDispatcher` vào `LayoutReconciler`. Build xanh | cbc1927 | `placeNewApps` không mở transaction nữa khi không có app mới |
| 2026-11-11 | 6 | `NewAppPlacementPlanner` (hàm thuần) + `placeNewInTransaction` dùng nó; bỏ `cellKey`/`CELL_KEY_STRIDE`. Build xanh | a2395fd | |
| 2026-11-11 | 7 | `renameComponent` ở hai DAO; `repointOrphans` trong `placeNewInTransaction` (`allowRepoint` đúng cho `placeNewApps`/`catchUp`, sai cho bỏ ẩn); KDoc `catchUp`. Build xanh; `app/schemas` không đổi | a6a3254 | Dừng sau Task 7 chờ điều phối chạy kiểm Live 4; APK `E:\SourceTree\IosLauncher-wt\apk\FX3.apk` |
| 2026-11-11 | 1, 2 | Sửa theo review điều phối (vòng 1/5): `.catch` cuối đổi thành `retryWithBackoff`; `seedIfEmpty` không bọc, lỗi seed đi lên để chạy lại cả flow; flow snapshot cũng `retryWithBackoff` (lần đọc đầu hỏng thì thử lại thay vì chờ lần ghi sau); sửa chú thích `GridPreset.kt`. Tick Task 1-7 (các Step code; Step 4 kịch bản Live 4 chưa chạy nên chưa tick). Build xanh | 81e3ce7 | |
| 2026-11-11 | 8 | Đóng nhánh: gỡ log đếm `assemble`/`snapshot` (và `AtomicInteger`), giữ log vận hành `changeGrid`, `placeNew`, `repoint`; điền kết quả kiểm Live 4 (FX3.apk@81e3ce7, điều phối), bảng bằng chứng, đề xuất tài liệu chung; hoàn chỉnh knowledgement | 206201b | Chờ review; Step 1-2 của Task 8 để điều phối tick |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Không đổi schema | `git diff --stat origin/main -- app/schemas .../LauncherDatabase.kt` rỗng; DAO chỉ thêm `@Query UPDATE` (đo được) | Đạt |
| 2 | Quy ước mutex, IPC ngoài transaction | Đọc code: `isPackageInstalled` chỉ ở `selectFresh` (trong `withContext(io)`, ngoài `withTransaction`) và `catchUp` (ngoài transaction); hàm công khai lấy mutex một lần, không gọi nhau; review độc lập không thấy deadlock hay tái nhập | Đạt (đọc code + review độc lập) |
| 3 | Một đường đặt app | Đọc code: `placeNewApps`, `catchUp`, `setHidden(false)` đều qua `selectFresh` rồi `placeNewInTransaction`; không còn `cellKey`/vòng ô trống đầu tiên. Máy: ca 2, 3, 4 ở trên | Đạt |
| 4 | Re-point không xóa, Q11 giữ | Máy: ca 4 (re-point giữ `id` và ô), ca 5 (disable/enable giữ hàng, gỡ vẫn dọn). Chưa chạy trên máy: biến thể dock và `hidden_apps` | Đạt (lưới, Q11); dock/hidden: đọc code |
| 5 | Planner y hệt cũ khi không lệch, không AIOOBE khi lệch | Đọc code + review độc lập (tương đương nhánh tràn một item; không AIOOBE). Chưa tái hiện hàng trùng ô trên máy | Chờ chủ dự án kiểm khi duyệt (kéo icon vào trang đầy); đọc code đạt |
| 6 | Một flow cho hai bảng, bọc reconciler | Máy: ca 1, 2 (`snapshot` một lần mỗi lần ghi); `retryWithBackoff` thay `.catch` cuối sau review. B1 ép lỗi chưa chạy | Đạt (B5); B1: đọc code + review độc lập |
| 7 | Use case mỏng, thứ tự ghi M8a | Đọc code: use case chỉ chuyển tiếp; thứ tự thu nhỏ DB trước, mở rộng DataStore trước. Chưa đổi cỡ lưới trên máy | Chờ chủ dự án kiểm: đổi 4x6 -> 4x5 -> 4x6, không icon mất hoặc chồng |
| 8 | Không sửa ngoài vùng | `git diff --stat origin/main`: chỉ `data/layout`, `domain/layout`, `domain/usecase/ChangeGridPresetUseCase`, `domain/repository/LayoutRepository` (được cho phép), `domain/model/GridPreset.kt` (chú thích, được cho phép) và docs; không có `ui/` | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `room-schema.md`: Q7 đổi từ "ô trống đầu tiên" thành "sau icon cuối cùng, trang cuối còn chỗ, không thì trang mới" (chủ dự án 2026-11-11); thêm một dòng "Cập nhật FX3": re-point `className` khi app cập nhật đổi tên activity (không đổi schema, Q11 giữ), đổi cỡ lưới chạy trong mutex của reconciler. Đoạn "Phân biệt bắt buộc giữa gỡ và tạm không khả dụng" giữ.
- `Launcher-Features.md`: đã grep, không dòng nào mô tả app mới vào "ô trống đầu tiên"; dòng TT#13 (widget lấy ô trống đầu tiên) **giữ**. Không cần sửa trừ khi điều phối muốn ghi hành vi C20 vào một dòng TT#.
- `project_structure.md`: thêm `domain/layout/NewAppPlacementPlanner.kt` vào cây thư mục; ghi mục FX3 vào bảng mốc.
- `MultiAgent-Workflow.md` mục 10: dòng MV, nghi vấn (1) đã có bản sửa ở FX3 (B5), chưa kết luận lỗi MV hết; thêm dòng FX3 khi mở nhánh.
- `lessons/drag-and-layout.md` dòng "ẩn ... quay lại ô trống đầu tiên" (M7 Task 8) và `lessons/settings-and-data.md` mục M4: là bài học lịch sử, nên thêm một dòng trỏ tới FX3 (nơi đặt app mới nay là sau icon cuối).
- `AGENTS.md` / `lessons/`: xem "Bài học cho AGENTS.md" trong knowledgement. Dự kiến: (`drag-and-layout`) planner thuần phải chịu dữ liệu lệch vì UI gọi nó trên luồng chính; (`settings-and-data`) hai flow Room không chung transaction thì dùng `invalidationTracker.createFlow` trên nhiều bảng và đọc một transaction; ghi dữ liệu ở hai nơi (DataStore và Room) phải chung một vùng khóa khi có đường đọc khác ở giữa; hàng hai-ô cùng ô (trùng) là cách tái hiện lỗi planner mà không cần đua.
