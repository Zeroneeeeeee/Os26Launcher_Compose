# G2a — Thư mục + App Library (TT#10, TT#11) — Implementation Plan

> **Dành cho agent thực thi:** làm theo đúng quy ước dự án trong `docs/AGENTS.md` — code từng Task, báo cáo, **chờ người dùng xác nhận trên máy thật** rồi mới tick hoàn thành và sang Task sau. Dự án **không có test tự động** (A7) — mọi bước "kiểm chứng" là kiểm chứng tay trên Vsmart Live 4 (máy mặc định, A8); Oppo A3x khi cần số máy mạnh / 344 app / kính thật. Claude không build/cài được từ môi trường của mình — người dùng build (`gradlew.bat assembleDebug`), cài, chạy kịch bản, dán log/kết quả lại. Lệnh viết cho CMD/PowerShell, không viết bash. Đây là mốc LỚN nhất từ M7: **hai phần làm tuần tự, hết Phần A (Thư mục) và được xác nhận mới bắt đầu Phần B (App Library)**.

**Goal:** (A) Người dùng gom app vào thư mục bằng cách kéo icon đè lên icon khác, mở thư mục thành một panel 3×3 (nền phẳng bán trong suốt), mở app từ panel, đổi tên, kéo app ra/vào thư mục; (B) vuốt qua trang cuối mở một trang App Library tự nhóm app theo loại.

**Architecture:** Schema Room đã có sẵn chỗ cho thư mục (`itemType = FOLDER`, `container > 0` = id thư mục cha, `title`, `rank`) nên **không đổi schema, không migration**. `HomePage.items` đổi từ `List<PlacedApp>` sang `List<PlacedItem>` (sealed: `PlacedApp`, `PlacedFolder`); các planner hiện có (`GridInsertPlanner`, `GridRemovePlanner`, `GridReflowPlanner`) chỉ dùng `Slot(itemId, x, y)` nên dùng được nguyên cho thư mục (1×1). Quy tắc dựng/giải tán thư mục viết một chỗ trong `domain/layout` (hàm thuần, dùng chung ghi DB và cập nhật lạc quan của UI, cùng nguyên tắc M7). Panel thư mục là một view trong `activity_home.xml` (không Activity/Dialog), nền phẳng bán trong suốt (kính chuyển sang mốc "Áp kính", chốt 2026-10-30). App Library là một **trang ảo** ở cuối `ViewPager2` (không lưu DB) dựng từ `AppRepository.apps` trừ app ẩn, nhóm bằng một bộ phân loại thuần theo `ApplicationInfo.category`.

**Tech Stack:** View/XML (B1), Room (không đổi schema), Hilt, StateFlow, Canvas tự vẽ như `IconCellView`, `RecyclerView`/`ViewPager2`. Không thêm dependency.

**Spec:** `docs/Launcher-Features.md` TT#10, TT#11; `docs/room-schema.md` (mục thư mục: `container > 0`, `rank`, Q2 icon trong thư mục xếp theo `rank`, mồ côi); `docs/project_structure.md` (Quyết định 3: thư mục là phần tử kính, `GlassBudget`; dòng G2a); plan [M7-edit-mode.md](M7-edit-mode.md) (kéo-thả, edit mode) và [M8b-search.md](M8b-search.md) / [M8c-search-pill.md](M8c-search-pill.md) (tương tác với tìm kiếm và chấm trang).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | ~~App Library là trang cuối của pager~~ **Sửa 2026-10-30 (D17):** App Library là **lớp phủ tách hẳn khỏi `ViewPager2`** (không phải một trang lưới); vuốt từ trang thật cuối sang trái để kéo nó vào. Không lưu DB, không tính là "trang tạm" của kéo-thả. | Người dùng chọn; sửa sau khi thử gắn vào pager (xem D17) |
| D2 | **Ô thư mục đóng và panel mở ra đều nền PHẲNG bán trong suốt ở G2a** (chốt lại 2026-10-30, thay quyết định cũ "đều là kính"). Ô thư mục đóng hiển thị tối đa **9 icon nhỏ** (lưới 3×3). Kính cho ô đóng và panel làm ở mốc "Áp kính" sau G2a. | Người dùng chốt 2026-10-30. Lý do: ô nằm trong `ViewPager2` nên di chuyển khi vuốt/rung/kéo, nền kính chụp một lần sẽ lệch (xem mục "Ghi chú cho mốc Áp kính" cuối file); tier `CLASSIC_GPU` không có máy đo |
| D3 | Thư mục là hàng `launcher_items` `itemType = FOLDER`, `container = HOME`, 1×1, `title`; con có `container = id thư mục`, `rank` = thứ tự. Thư mục **chỉ ở lưới chính** (không vào dock). Kéo thư mục vào dock bị từ chối (icon bay về chỗ cũ). **Sửa 2026-11-21 theo người dùng:** thư mục được nằm trong dock (`container = DOCK`, `rank` = ô dock): thả app vào giữa app dock tạo thư mục tại ô đó, thả vào giữa thư mục dock thì thêm vào, kéo thư mục lưới <-> dock được; D9 trong dock trả app còn lại về đúng ô dock. | Mặc định theo M7 (dock chỉ chứa app); **cần khảo sát lại trên iOS thật** (câu hỏi mở, không làm ở G2a) |
| D4 | **Tạo thư mục (chốt lại 2026-10-30, hành vi iOS):** kéo app A tới ô đang có app 1×1 B → **mặc định là gộp**: ô B phóng nhẹ báo "sẽ gộp", KHÔNG đẩy-dồn; thả lúc đó (ở bất kỳ đâu trong ô) → tạo thư mục tại ô B chứa B (rank 0) rồi A (rank 1), tên mặc định "Thư mục". Chỉ khi giữ ngón tay ở **mép ô** (ngoài vùng giữa 50%) liên tục **≥ 500 ms** mới chuyển sang đẩy-dồn như M7. Nguồn A có thể ở lưới chính hoặc dock (từ dock: app rời dock, dock căn giữa lại). | Giống iOS: tới là gộp trước, cố tình giữ ở mép mới dịch ô. Số 500 ms chỉnh sau |
| D5 | **Thêm vào thư mục có sẵn:** kéo app tới ô thư mục → thêm vào cuối (cùng luật với D4: thả ở bất kỳ đâu trong ô; giữ ở mép ≥ 500 ms mới đẩy-dồn). Thư mục **đầy (9 app = một trang 3×3)** thì từ chối, icon bay về chỗ cũ. | Phân trang thư mục (>9) để sau; G2a chỉ cần **một trang hoạt động tốt**, `FolderPlanner.MAX_ITEMS` tách riêng để mở rộng được |
| D6 | **Mở thư mục:** bấm ô thư mục → nền mờ tối + panel phẳng bán trong suốt ở giữa, tiêu đề trên cùng, lưới 3×3 icon (xếp theo `rank`). Bấm icon = mở app (như Home). Đóng: bấm nền ngoài, Back, nhấn Home, mở app. | **Edit mode:** bấm thư mục vẫn mở panel, icon trong panel hiện **dấu "−"** như icon ở lưới (bấm "−" = `onQuickHideTapped` của M7, rồi áp D9). **Ngoài edit mode:** nhấn giữ icon trong panel ra menu nhấn giữ/shortcut như icon ở lưới (M7). **Thứ tự lớp:** panel + scrim của nó nằm DƯỚI `icon_menu_scrim`/`icon_action_menu` để menu hiện TRÊN panel; menu trong panel gồm AppInfo, HideApp, Uninstall (bỏ EditHome); `IconMenuState.container` nhận id thư mục (> 0) |
| D7 | **Đổi tên:** bấm tiêu đề trong panel → `EditText` tại chỗ; Enter/đóng panel = lưu. Rỗng thì giữ "Thư mục". Tối đa 30 ký tự. | |
| D8 | **Kéo app ra khỏi thư mục:** theo đúng luồng nhấn giữ/kéo của icon ở lưới (M7) — ngoài edit mode nhấn giữ ra menu (D6), trong edit mode nhấn giữ rồi kéo; khi ngón tay rời khỏi panel thì panel đóng và phiên kéo tiếp tục như kéo từ lưới (thả vào lưới = đặt vào ô đó, luật M7). Thả lên chính thư mục khác thì theo D5. | |
| D9 | **Giải tán:** thư mục còn **1 app** → app đó về đúng ô của thư mục, thư mục bị xóa; còn **0 app** → xóa thư mục. Áp cho mọi đường làm giảm con: kéo ra, ẩn, **gỡ app thật**, bắt kịp lúc mở. | Một chỗ viết quy tắc (`domain/layout`) |
| D10 | **App ẩn / gỡ khi đang ở trong thư mục:** ẩn từ menu nhấn giữ trong panel = như ẩn ở lưới (xóa hàng, áp D9); bỏ ẩn đặt app lại vào ô trống đầu tiên của lưới, **không** vào thư mục cũ. | |
| D11 | **Edit mode:** thư mục rung như icon; **không có dấu "−" trên ô thư mục** (dấu "−" chỉ nằm trên icon bên trong panel, D6); xóa thư mục = bỏ/kéo hết app ra (D9). Kéo thư mục như kéo icon (đẩy-dồn, giữa các trang, lật trang). Thư mục không thể thả vào thư mục khác (từ chối). | |
| D12 | **App Library — nguồn:** mọi app đã cài **trừ app ẩn** (cùng tập với tìm kiếm M8b), kể cả app đã có trên Home. **Nhóm** theo `ApplicationInfo.category`: Mạng xã hội (`SOCIAL`), Giải trí (`VIDEO`, `AUDIO`, `IMAGE`), Trò chơi (`GAME`), Năng suất (`PRODUCTIVITY`), Tin tức (`NEWS`), Bản đồ (`MAPS`); `UNDEFINED` và loại khác → **luật dự phòng:** app hệ thống (`FLAG_SYSTEM`) vào "Tiện ích", còn lại vào "Khác". Nhóm rỗng không hiện. | Luật dự phòng theo ghi chú TT#11 trong `Launcher-Features.md`; bảng nhóm điểm xuất phát, chỉnh sau khi xem dữ liệu thật trên Live 4 |
| D13 | **App Library — giao diện:** lưới 2 cột các ô nhóm (ô phẳng bán trong suốt, 4 icon nhỏ 2×2 + tên nhóm, nhóm >4 app thì ô thứ 4 là mini-icon "+N"); bấm ô → panel phẳng liệt kê mọi app trong nhóm (3 cột, cuộn dọc nếu nhiều), bấm = mở app. Chỉ đọc: không kéo-thả/ẩn trong App Library ở G2a. | |
| D14 | **Chỉ báo trang (sửa 2026-10-30):** số chấm = trang thật (kể cả trang tạm), **KHÔNG tính Library**. Library **không mở được** khi edit mode, đang kéo icon, menu nhấn giữ, panel thư mục mở hoặc lớp tìm kiếm mở. Nếu edit mode / kéo / menu / panel thư mục / hỏi hình nền bật lúc Library đang mở thì Library đóng lại; **lớp tìm kiếm thì không** (nó mở ra từ chính thanh tìm kiếm của Library, Back đóng nó trước rồi mới tới Library). | Người dùng chọn |
| D17 | **App Library là lớp phủ ngoài pager (theo mẫu `LauncherOverlay` của Launcher3, chốt 2026-10-30):** `CategoryPageView` nằm trong `activity_home.xml` trên `home_content`, KHÔNG là mục của `HomePagerAdapter`; pager chỉ còn các trang thật nên không có view type thứ hai, không đổi cỡ pager, không cần trần chiều cao cho `IconGridView`. **Tiến độ** `progress` (0 = đóng, 1 = mở hẳn) điều khiển: `translationX` của Library (`width × (1 − progress)`), độ tối của `library_scrim` (cùng màu `search_scrim`), độ mờ của `home_content` (pager + pill + dock, `alpha = 1 − progress`; `INVISIBLE` khi `progress = 1` để không nhận chạm). **Cử chỉ:** `HomeActivity.dispatchTouchEvent` (cùng mẫu vuốt tìm kiếm M8b) bắt vuốt ngang sang trái khi đang ở trang thật cuối và đã đứng yên; kéo theo ngón tay, thả theo vận tốc/quá nửa thì mở, ngược lại đóng, có animation. Khi đang mở: vuốt ngang sang phải ở bất kỳ đâu, Back hoặc nút Home thì đóng. Trạng thái mở/đóng ổn định nằm ở `HomeViewModel` (sống qua `recreate()`), `progress` giữa chừng chỉ ở view. Vuốt xuống tìm kiếm của Home tắt khi `progress > 0`; Library có thanh tìm kiếm riêng (mở lớp tìm kiếm với danh sách đầy đủ a–z, `SearchViewModel.open(showAll = true)`). | Người dùng chọn cách A |
| D15 | Không thêm mục cấu hình nào vào Cài đặt ở G2a (YAGNI); không đổi cờ DataStore. | |
| D16 | ~~Chính sách kính thư mục theo tier (`FolderGlassPolicy`)~~ **Chuyển sang mốc "Áp kính"** (2026-10-30). G2a không thêm `FolderGlassPolicy`, không đổi `GlassBudget`. | Ý tưởng ban đầu (tier → kiểu vẽ ô đóng / số ô kính tối đa / kiểu vẽ panel) giữ trong mục "Ghi chú cho mốc Áp kính" |

## Ngoài phạm vi G2a / ghi để sau

- Thư mục nhiều trang (> 9 app), thư mục cỡ lớn kiểu iOS 26, dấu "−" trên ô thư mục, thư mục trong dock, thư mục lồng nhau.
- Kéo-thả/ẩn/xóa ngay trong App Library; "Mới thêm gần đây"; gợi ý thông minh theo thói quen.
- Tìm kiếm theo tên thư mục / hiển thị thư mục trong kết quả tìm kiếm (tìm kiếm vẫn trả app).
- Kính cho ô nhóm của App Library (ô nhóm vẫn là ô phẳng ở G2a; panel nhóm dùng chính sách panel của D16); kiểu icon (G2b); widget (G2c).
- Khảo sát hành vi thư mục trong dock trên iOS thật (câu hỏi mở).
- TalkBack đầy đủ cho kéo-thả vào thư mục (chỉ đặt nhãn mô tả cơ bản).

## Global Constraints

- `domain` không import `android.*`; `ui` không import `data` trực tiếp; mọi truy cập đi qua interface `domain/repository`.
- Mọi chữ hiển thị ở `strings.xml` (tiếng Việt); màu ở `colors.xml`; kích thước ở `dimens.xml`; lưới 8dp, vùng chạm ≥ 48dp, tương phản WCAG AA. Tiền tố tên: `folder_` (thư mục), `category_page_` (App Library, tránh trùng `library_*` của màn Thư viện ứng dụng M7).
- Màn/khối mới định nghĩa đủ trạng thái: Default, Empty (thư mục không còn app, nhóm rỗng), Loading (danh sách app chưa nạp), Error (mở app thất bại → Toast `app_launch_failed`), Disabled (ô đầy không nhận thả).
- **Không đổi schema Room và không thêm khóa DataStore.** Nếu phát hiện cần đổi: dừng, báo người dùng trước (quy ước migration thử tay trong `room-schema.md`).
- Mọi ghi bố cục đi qua `LayoutReconciler` (`mutex` + transaction); quy tắc dựng/giải tán thư mục viết một chỗ trong `domain/layout`, dùng chung ghi DB và UI lạc quan (bài học M7).
- Không làm hỏng: kéo-thả/lật trang/trang tạm của M7, dấu "−", menu nhấn giữ, vuốt xuống tìm kiếm và pill (M8b/M8c), đổi cỡ lưới re-flow (M8a), dồn trong trang khi gỡ app, dock/kính.
- Trạng thái hiển thị suy ra từ `HomeUiState`/dữ liệu; view không giữ bản sao nghiệp vụ lâu dài (bài học M7 Task 5).
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự.

## Review Focus

- **Mất app khi gộp/giải tán/gỡ:** gộp 2 app, thêm vào thư mục đầy, kéo ra app cuối, ẩn một app trong thư mục, **gỡ thật** một app trong thư mục (và một thư mục chứa toàn app bị gỡ cùng lúc), force-stop giữa lúc ghi → không bao giờ mất app, không để lại thư mục 0/1 app, không để lại hàng mồ côi (`container` trỏ thư mục đã xóa).
- **Kéo-thả mơ hồ:** thả lên mép ô (đẩy-dồn) và giữa ô (gộp), dwell ngắn hơn 500 ms, kéo ngang qua nhiều app mà không dừng, kéo thư mục lên thư mục, kéo thư mục vào dock, kéo app trong dock lên ô app → hành vi đúng và M7 không đổi.
- **Panel thư mục và vòng đời:** mở khi đang ở trang giữa, đổi sáng/tối hoặc mức kính (Home `recreate()`) khi panel đang mở, mở app rồi quay lại, Home/Back/chạm nền, đổi tên rỗng/rất dài/ký tự tiếng Việt có dấu, bàn phím che panel → không rò cửa sổ/bàn phím, không crash, tên không mất khi đóng giữa lúc gõ.
- **Tương tác với tính năng cũ:** re-flow đổi cỡ lưới (M8a) khi có thư mục, gỡ app dồn trong trang, seed lần đầu/`catchUp`, Thư viện ứng dụng M7 (tab Chưa ẩn/Đã ẩn), tìm kiếm M8b (app trong thư mục vẫn tìm và mở được, không bị coi là ẩn).
- **App Library:** máy nhiều app (Oppo A3x 344 app), nhóm toàn `UNDEFINED`, app hệ thống không có category, cài/gỡ app khi đang ở trang Library, vào/ra edit mode và kéo-thả khi đang ở Library, vuốt xuống tìm kiếm và pill từ trang Library, 1 trang Home + Library, chấm trang đúng số.
- **Hiệu năng:** panel mở/đóng mượt trên Live 4, cuộn nhóm dài trên Oppo, Home nhiều thư mục không giật khi lướt trang.

---

# PHẦN A — Thư mục

### Task A1a: Mô hình + dữ liệu + vẽ ô thư mục đóng (chưa có kéo-thả) ✅ xong 2026-10-30

Tách từ A1 cũ (2026-10-30) cho đúng quy ước mốc nhỏ: A1a chỉ đổi mô hình, dữ liệu và cách vẽ; cách tạo thư mục bằng kéo-thả là A1b. Đọc code 2026-10-30: `PlacedApp` chỉ được dùng ở 4 file (`LayoutRepositoryImpl`, `BuildDefaultLayoutUseCase`, `HomeViewModel`, `IconGridView`) nên phạm vi đổi sang `PlacedItem` nhỏ.

**Files:**
- Create: `domain/model/PlacedItem.kt`, `domain/layout/FolderPlanner.kt`, `res/values` (chuỗi `folder_default_title`, màu/dimens ô thư mục)
- Modify: `domain/model/HomePage.kt` (`HomePage.items: List<PlacedItem>`; `PlacedApp` thành implementor), `domain/repository/LayoutRepository.kt` (+ `createFolder`), `data/layout/LayoutRepositoryImpl.kt` (`assemble` dựng `PlacedFolder`, bỏ qua thư mục không còn app), `data/layout/LayoutReconciler.kt` (+ `createFolder`), `domain/usecase/BuildDefaultLayoutUseCase.kt`, `ui/home/HomeViewModel.kt` (kiểu `PlacedItem` ở cập nhật lạc quan), `ui/home/HomePagerAdapter.kt`, `ui/home/view/IconGridView.kt` + `IconCellView.kt` (vẽ ô thư mục)

**Interfaces:**
- Produces:
  - `sealed interface PlacedItem { val itemId: Long; val position: GridPosition }`; `data class PlacedApp(itemId, position, app: AppItem) : PlacedItem` (giữ nguyên tên/trường); `data class PlacedFolder(itemId, position, title: String, previewApps: List<AppItem>, appCount: Int) : PlacedItem` (`previewApps` tối đa 9, theo `rank`).
  - `object FolderPlanner` (hàm thuần): `fun canCreate(...)`, `fun dissolve(remainingChildren: Int): Dissolve` (`KEEP` / `REPLACE_WITH_SINGLE` / `DELETE`), `const val MAX_ITEMS = 9`, `fun centerZone(cell: ...)`.
  - `suspend fun LayoutRepository.createFolder(draggedItemId: Long, targetItemId: Long): Boolean` — `false` nếu từ chối (đích không phải app 1×1 ở lưới chính, nguồn là thư mục; nguồn từ dock được phép, dock bỏ app đó và căn giữa lại như khi ẩn; nguồn nằm trong thư mục khác chưa hỗ trợ đến A3).

- [x] **Step 1: Quy tắc thuần** — `FolderPlanner` + quy tắc từ chối (đích phải là app 1×1 trên lưới chính, nguồn không phải thư mục, nguồn ở lưới chính hoặc dock (từ dock: dock bỏ app và căn giữa lại, sửa 2026-10-30 theo người dùng); nguồn nằm trong thư mục khác để A3 vì cần quy tắc giải tán thư mục cũ). Ghi lại thứ tự `rank`.
- [x] **Step 2: DB** — `LayoutReconciler.createFolder` trong `mutex` + transaction: chèn hàng `FOLDER` tại ô của đích (`gridX/gridY/pageId` của đích), đổi `container` của đích và nguồn sang id thư mục với `rank` 0/1, **không đổi vị trí/ô của các icon khác** (nguồn rời ô cũ để lại khe, luật M7). `LayoutRepositoryImpl.assemble` dựng `PlacedFolder` (bỏ thư mục không còn app khả dụng; hàng mồ côi bỏ qua kèm log như hiện nay).
- [x] **Step 3: Vẽ** — `IconCellView` vẽ ô thư mục (nền bo góc phẳng bán trong suốt + lưới 3×3 icon nhỏ tối đa 9 + nhãn tên). Bấm ô thư mục chưa làm gì (A2 xử lý). `HomeViewModel` và các planner xử lý thư mục như item 1×1.
- [x] **Step 4: Hook debug TẠM** — nhấn giữ nút "Xong" ở edit mode gọi `createFolder` cho hai icon đầu của trang đang hiện (chỉ để có dữ liệu thư mục trước khi có kéo-thả), log tag `G2A`. **Xóa ở A1b Step 5.**
- [x] **Step 5: Kiểm chứng (Live 4)** — build xanh; (a) hook tạo thư mục: ô thư mục hiện 2 icon nhỏ tại ô của icon đích, icon nguồn biến khỏi lưới, không icon nào khác dịch; (b) kill app và mở lại: thư mục còn nguyên; (c) đổi cỡ lưới (Cài đặt): thư mục được xếp lại như icon thường, không mất app; (d) cài/gỡ app khác: thư mục vẫn đúng chỗ, app mới không chui vào thư mục; (e) kéo-thả/dấu "−"/menu nhấn giữ của icon thường vẫn đúng (hồi quy M7); (f) tìm kiếm vẫn tìm và mở được app đang nằm trong thư mục. Dán log `G2A`.
- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit** (`feat: render folders on the home grid`).

---

### Task A1b: Tạo thư mục bằng kéo-thả (dwell) + kéo thư mục ✅ xong 2026-10-30

**Files:**
- Create: `domain/usecase/CreateFolderUseCase.kt`
- Modify: `ui/home/view/IconGridView.kt` (vùng giữa ô, dwell, `onFolderIntent`, kéo thư mục), `ui/home/view/IconCellView.kt` (phóng nhẹ báo "sẽ gộp"), `ui/home/HomePagerAdapter.kt`, `ui/home/HomeViewModel.kt` (cập nhật lạc quan cùng `FolderPlanner`)

**Interfaces:**
- Consumes: `FolderPlanner`, `LayoutRepository.createFolder` (A1a).
- Produces: `IconGridView.onFolderIntent: ((draggedId, targetId) -> Unit)?`.

- [x] **Step 1: Ý định gộp + dwell ở mép (luật D4 đổi 2026-10-30: tới ô app là gộp, giữ ở mép ≥ 500 ms mới đẩy-dồn)** — lưu ý từ đọc code: `IconGridView.onDragMove` thoát sớm khi ô không đổi, và ngón tay đứng yên thì `DragHelper` không phát sự kiện nào, nên dwell **không thể** tính trong `onDragMove`: dùng `Handler.postDelayed(500 ms)` bắt đầu khi vào vùng giữa ô app đích, hủy khi đổi ô / ra vùng giữa / `onDragExit` / thả / `onDetachedFromWindow`. Chỉ ô app 1×1 trên lưới chính là đích hợp lệ.
- [x] **Step 2: Thả** — `onDrop` ở trạng thái folder-intent gọi `onFolderIntent` thay vì đẩy-dồn; ngược lại đẩy-dồn như M7. Nguồn từ dock vẫn gộp được. Thư mục kéo được như icon thường (đẩy-dồn, giữa các trang, lật trang); thả thư mục lên thư mục/app để gộp bị từ chối; thả thư mục vào dock bị từ chối (icon bay về).
- [x] **Step 3: Cập nhật lạc quan** — `HomeViewModel` dùng chính `FolderPlanner` như đường ghi thật.
- [x] **Step 4: Log tạm tag `G2A`** — ghi quyết định mỗi lần thả (`push` / `folder-intent`, ô, itemId) và kết quả `createFolder`. **Xóa ở Task A5.**
- [x] **Step 5: Gỡ hook debug của A1a Step 4.**
- [x] **Step 6: Kiểm chứng (Live 4)** — build xanh; (a) kéo app A tới ô app B (ô B phóng nhẹ) rồi thả: tạo thư mục "Thư mục" tại ô B với hai app; (b) kéo tới ô app rồi thả ngay (kể cả ở mép ô): tạo thư mục; giữ ở mép ô ≥ 500 ms: chuyển sang đẩy-dồn, thả thì chèn như M7; (c) giữ ngón tay đứng yên ở mép ô đúng 500 ms (không rê): icon đích dịch ra (xác nhận timer không phụ thuộc sự kiện chạm); quay lại vùng giữa trước 500 ms thì không dịch; (d) kill app và mở lại: thư mục còn nguyên; (e) kéo thư mục sang ô khác/trang khác/lật trang: di chuyển được; (f) kéo thư mục vào dock: icon bay về; (g) kéo app trong dock lên một ô app rồi thả: tạo thư mục, app đó rời dock (dock căn giữa lại như khi ẩn app); (h) kéo-thả/dấu "−"/menu nhấn giữ của icon thường vẫn đúng. Dán log `G2A`.
- [x] **Step 7: Ghi `docs/AGENTS.md` + Commit** (`feat: create folders by dragging an icon onto another`).

---

### Task A2: Mở thư mục — panel phẳng 3×3, mở app, đổi tên

**Files:**
- Create: `ui/home/view/FolderPanelView.kt`, `res/layout/view_folder_panel.xml`, `res/layout/item_folder_app.xml` (hoặc vẽ bằng `IconCellView`), `domain/usecase/RenameFolderUseCase.kt`
- Modify: `res/layout/activity_home.xml` (thêm `folder_scrim` + `folder_panel` NGAY TRƯỚC `icon_menu_scrim`, tức dưới menu nhấn giữ và dưới `search_overlay`, để menu nhấn giữ hiện TRÊN panel), `ui/home/HomeActivity.kt` (mở/đóng, Back, `onNewIntent`), `ui/home/HomeViewModel.kt`/`HomeUiState.kt` (`openFolderId`), `domain/repository/LayoutRepository.kt` (+ `folderApps(folderId)`, `renameFolder`), `data/layout/LayoutReconciler.kt` (+ `renameFolder`)

**Interfaces:**
- Consumes: `PlacedFolder`, `HomeViewModel.onAppClicked(item, source)`.
- Produces: `HomeUiState.openFolderId: Long?` + `FolderPanelView.render(state: FolderPanelState?)` (`FolderPanelState(folderId, title, apps: List<DockApp>)`), `onAppClick`, `onTitleChanged`, `onDismissRequested`; `LayoutRepository.renameFolder(folderId: Long, title: String)`.

Trạng thái: Default (3×3 có app), Empty (không còn app → panel tự đóng, thư mục bị xóa theo D9), Error (mở app thất bại → Toast), Disabled (không áp dụng).

- [x] **Step 1: Panel** — scrim tối + panel phẳng bán trong suốt bo góc (kính: mốc "Áp kính"), tiêu đề + lưới 3×3 theo `rank`; bố cục tự co theo bề rộng, không vượt màn trong split-screen; hiện/ẩn có fade ngắn; chỉ một panel mở một lúc.
- [x] **Step 2: Mở/đóng** — bấm ô thư mục (cả edit mode) → `openFolderId`; đóng bằng chạm nền, Back (callback panel đăng ký SAU `exitEditModeOnBack` và TRƯỚC `closeIconMenuOnBack`/`closeSearchOnBack`: callback đăng ký sau ưu tiên cao hơn, nên thứ tự đóng là tìm kiếm → menu nhấn giữ → panel → thoát edit mode), nhấn Home, mở app thành công. `openFolderId` ở `HomeViewModel` để sống qua `recreate()`.
- [x] **Step 3: Đổi tên (D7)** — bấm tiêu đề → `EditText` tại chỗ (tối đa 30 ký tự, trim, rỗng → "Thư mục"), lưu khi Enter/đóng panel; không đẩy chữ từ state vào ô mỗi lần render (bài học M8b Task 2).
- [ ] **Step 3b: Hành vi trong panel (D6)** *(2026-10-30: dời sang đầu Task A3 vì "−" và ẩn trong panel phụ thuộc quy tắc giải tán D9 làm ở A3; A2 chỉ có panel, mở app, đổi tên)* — trong edit mode icon trong panel hiện dấu "−" (bấm = ẩn qua `onQuickHideTapped`, áp D9 ở A3); ngoài edit mode nhấn giữ icon ra menu nhấn giữ/shortcut giống icon ở lưới. Menu nằm TRÊN panel; `IconMenuState.container` nhận id thư mục (> 0); menu trong panel gồm AppInfo, HideApp, Uninstall, bỏ EditHome. Panel vẫn mở khi đổi edit mode.
- [x] **Step 4: Kiểm chứng (Live 4)** — (a) bấm thư mục: panel hiện đúng 3×3, tiêu đề đúng; (b) bấm icon: app mở, panel đóng, quay lại Home thư mục vẫn nguyên; (c) chạm nền/Back/Home đóng; (d) đổi tên "Mạng xã hội", để trống, 40 ký tự: lưu đúng/giữ "Thư mục"/cắt 30; kill app và mở lại tên còn; (e) đóng panel giữa lúc đang gõ: tên không mất; (f) đổi sáng/tối khi panel đang mở: panel còn, không rò cửa sổ; (g) mở tìm kiếm (vuốt xuống) khi panel đang mở: tìm kiếm nằm trên, đóng tìm kiếm quay về panel; (h) mở nhiều lần liên tiếp: không chồng panel; (i) edit mode: bấm thư mục mở panel, icon trong panel có "−", ô thư mục bên ngoài không có "−"; (j) ngoài edit mode nhấn giữ icon trong panel: ra menu shortcut như ở lưới, menu hiện TRÊN panel, không bị che.
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: open folders in a glass panel with rename`).

---

### Task A3a: Thêm vào thư mục, giải tán (D9), "−"/ẩn/menu trong panel ✅ xong 2026-10-30

Tách từ A3 cũ (2026-10-30): A3a lo dữ liệu và quy tắc (thêm, giải tán, ẩn trong thư mục) + Step 3b dời từ A2; kéo icon RA khỏi panel là A3b.

**Files:**
- Modify: `domain/layout/FolderPlanner.kt` (`canAdd`), `domain/repository/LayoutRepository.kt`, `data/layout/LayoutRepositoryImpl.kt`, `data/layout/LayoutReconciler.kt` (`addToFolder`, `dissolveFolderIfNeeded`, `setHidden` áp D9), `ui/home/view/IconGridView.kt` (thả vào vùng giữa ô thư mục), `ui/home/view/FolderPanelView.kt` ("−", nhấn giữ), `ui/home/view/IconActionMenu.kt` (bỏ "Sửa Màn hình chính" trong panel), `ui/home/HomeViewModel.kt`, `ui/home/HomeActivity.kt`, `ui/home/HomePagerAdapter.kt`
- Create: `domain/usecase/AddToFolderUseCase.kt`

**Interfaces:**
- Produces: `LayoutRepository.addToFolder(itemId, folderId): Boolean` (`false` khi đầy/không hợp lệ), `HomeViewModel.onAddToFolder`, `HomeViewModel.hideAppInFolder(key)`; `setHidden` giải tán thư mục khi app ẩn là con.

- [x] **Step 1: Thêm vào (D5)** — kéo app (lưới chính hoặc dock) tới ô thư mục → `addToFolder` (cuối, `rank` kế tiếp); ô thư mục phóng nhẹ báo "sẽ thêm", không đẩy-dồn, thả ở bất kỳ đâu trong ô. Cùng luật với tạo thư mục (D4, sửa 2026-10-30): giữ ở MÉP ô ≥ 500 ms mới chuyển sang đẩy-dồn. Thư mục đầy (9) → vẫn ở chế độ này nhưng không phóng, thả thì từ chối, icon bay về. Cập nhật lạc quan cùng quy tắc.
- [x] **Step 2: Giải tán (D9)** — `dissolveFolderIfNeeded` trong cùng transaction với thao tác làm giảm con: 1 app → app về đúng ô thư mục, xóa thư mục; 0 → xóa thư mục. Áp ở A3a cho đường ẩn (`setHidden`); gỡ app thật và bắt kịp là A4.
- [x] **Step 3b: Hành vi trong panel (D6)** — edit mode: icon trong panel hiện "−" (bấm = ẩn qua `hideAppInFolder`, áp D9, thư mục còn ≤ 1 app thì panel đóng); chưa edit mode: nhấn giữ icon ra menu nhấn giữ/shortcut TRÊN panel (`IconMenuState.container` = id thư mục, bỏ mục "Sửa Màn hình chính"; "Ẩn" qua `hideAppInFolder`). Kéo từ menu/panel: A3b.
- [x] **Step 4: Log tạm `G2A`** — `addToFolder` / `dissolve check` / hide trong thư mục (folderId, số con trước/sau). Xóa ở A5.
- [x] **Step 5: Kiểm chứng (Live 4)** — (a) thả app vào giữa ô thư mục có chỗ: vào cuối, ô thư mục hiện thêm icon nhỏ, ô cũ của app để trống; từ dock cũng được (dock căn giữa lại); (b) thư mục 9 app: thả vào ô thì icon bay về; (c) kéo tới mép ô thư mục rồi giữ ≥ 500 ms: đẩy-dồn như M7, thả thì chèn; thả ngay ở mép thì vào thư mục; (d) edit mode: mở panel thấy "−"; bấm "−" ẩn app, thư mục 3 app còn 2; ẩn tới còn 1 app: panel đóng, thư mục biến mất, app về đúng ô thư mục; (e) ngoài edit mode: nhấn giữ icon trong panel ra menu trên panel, không có "Sửa Màn hình chính", "Ẩn" hoạt động như (d), shortcut của app (nếu có) mở được; (f) bỏ ẩn app (Cài đặt/Thư viện): về ô trống đầu tiên của lưới, không về thư mục cũ; (g) kill app và mở lại: dữ liệu khớp; (h) kéo-thả/lật trang/tạo thư mục/menu của icon thường vẫn đúng. Dán log `G2A`.
- [x] **Step 6: Ghi `docs/AGENTS.md` + Commit** (`feat: add to folder and dissolve rule`).

---

### Task A3b: Kéo icon ra khỏi thư mục ✅ xong 2026-10-30

**Files:**
- Modify: `ui/home/view/FolderPanelView.kt` (implement `DragDropTarget`, nhấn giữ + kéo ra, panel đóng khi ngón tay rời panel), `ui/home/view/IconGridView.kt` / `DockView.kt` (chặn hover/thả khi điểm nằm trong vùng panel), `ui/home/HomeActivity.kt` / `HomeViewModel.kt` (phiên kéo bắt đầu từ panel), `data/layout/LayoutReconciler.kt` + `domain/repository/LayoutRepository.kt` (`moveItem` chấp nhận nguồn là con thư mục, áp D9 sau khi ra)

- [x] **Step 1: Kéo ra** — (lưu ý kỹ thuật từ đọc code 2026-10-30: (1) lưới và dock bên dưới panel vẫn là target đã đăng ký trong `DragHelper` nên sẽ hiện preview đẩy-dồn khi ngón tay còn trong panel → phải chặn hover/thả của các target đó khi điểm nằm trong vùng panel; (2) `DragSession.source` có kiểu `DragDropTarget` → panel implement interface này, từ chối mọi thả để icon về chỗ cũ.) Nhấn giữ icon trong panel (kể cả khi chưa edit mode) bắt đầu phiên `DragHelper`; ngón tay rời panel → panel đóng, phiên tiếp tục; kéo tới ô app/thư mục dùng CÙNG luật D4/D5 (gộp trước, giữ ở mép ≥ 500 ms mới đẩy-dồn; từ con thư mục đè lên app vẫn tạo thư mục mới, thư mục cũ giải tán theo D9); thả lên ô trống theo luật M7; thả lên chính panel / lại đúng ô thư mục cũ / ngoài mọi đích → quay về thư mục cũ. Giải tán D9 sau khi ra.
- [x] **Step 2: Log tạm `G2A`** — `moveOut` (folderId, số con trước/sau). Xóa ở A5.
- [x] **Step 3: Kiểm chứng (Live 4)** — (a) mở panel, kéo icon ra ngoài panel thả lên ô trống/ô có icon/trang khác: đúng luật M7, panel đóng; (b) kéo ra tới còn 1 app: thư mục biến mất, app về đúng ô thư mục; kéo ra hết: thư mục xóa, không để lại hàng thừa; (c) kéo thả trở lại chính panel, hoặc kéo ra rồi thả lại đúng ô thư mục cũ: về chỗ cũ, không nhân đôi icon; (d) kéo app ra rồi thả vào một thư mục khác: vào thư mục đó (D5); (e) kill app giữa lúc kéo ra (thử vài lần): mở lại không mất app, không còn thư mục 0/1 app; (f) kéo-thả/lật trang của icon thường vẫn đúng.
- [x] **Step 4: Ghi `docs/AGENTS.md` + Commit** (`feat: drag icons out of folders`).

---

### Task A4: Tích hợp với các luồng cũ (gỡ app, ẩn, bắt kịp, re-flow, tìm kiếm, Thư viện ứng dụng)

**Files:**
- Modify: `data/layout/LayoutReconciler.kt` (`removePackages`, `catchUp`/`deleteAndCompact` biết con của thư mục → D9; `setHidden` với app trong thư mục; `reflowInTransaction`/`GridReflowPlanner` chạy đúng với thư mục; `seedIfEmpty`/`placeNewInTransaction` bỏ qua con thư mục khi tìm ô trống), `data/layout/LayoutRepositoryImpl.kt`, `domain/layout/GridReflowPlanner.kt` (nếu cần), `ui/library/*` (app trong thư mục vẫn thuộc "Chưa ẩn"), `ui/search/SearchViewModel.kt` (chỉ rà, dự kiến không đổi)

**Interfaces:**
- Consumes: `FolderPlanner.dissolve`, `LayoutRepository` (A1a–A3).
- Produces: không giao diện mới; cam kết: mọi đường làm giảm con thư mục đều qua đúng một hàm dùng `FolderPlanner.dissolve`.

- [x] **Step 1: Rà luồng** — liệt kê mọi nơi đang giả định `container` chỉ là HOME/DOCK (đọc `LayoutReconciler` toàn bộ, `LayoutRepositoryImpl`, `HomeViewModel`, `ui/library`), ghi kết quả rà vào `docs/AGENTS.md`.
- [x] **Step 2: Gỡ thật / bắt kịp / ẩn** — app trong thư mục bị gỡ/ẩn: xóa hàng con rồi áp D9; gỡ nhiều app cùng lúc (cùng thư mục, khác thư mục, lẫn app ở lưới); `deleteAndCompact` (dồn trong trang, 2026-10-30) không coi con thư mục là ô của trang.
- [x] **Step 3: Re-flow M8a, seed, app mới, bỏ ẩn** — thư mục được xếp lại như item 1×1 (cùng thứ tự đọc); app mới cài/bỏ ẩn chỉ tìm ô trống trong lưới, không vào thư mục (D10).
- [x] **Step 4: Kiểm chứng (Live 4)** — (a) gỡ thật một app trong thư mục 3 app: thư mục còn 2; gỡ tới còn 1: giải tán; (b) `pm uninstall` lúc launcher không chạy (`force-stop` rồi gỡ rồi mở): kết quả như (a); (c) ẩn app trong thư mục bằng menu nhấn giữ trong panel, rồi bỏ ẩn ở Thư viện ứng dụng: app về ô trống đầu của lưới, không vào thư mục; (d) đổi cỡ lưới 4×6 → 4×5 → 5×6 với 2–3 thư mục: không mất app, thư mục còn nguyên; (e) cài app mới: vào ô trống đầu, không vào thư mục; (f) Thư viện ứng dụng M7 tab "Chưa ẩn" vẫn thấy app trong thư mục; tìm kiếm tìm và mở được app trong thư mục; (g) gỡ app khiến thư mục giải tán đúng cạnh các trang trống ở giữa: trang giữa vẫn giữ (sửa 2026-10-30).
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: keep folders consistent with uninstall, hide and re-flow`).

---

### Task A5: Đo cơ bản và đóng Phần A ✅ xong 2026-10-30 (đo Janky hoãn sang "Áp kính")

**Files:**
- Modify: `ui/home/HomeViewModel.kt`/`HomeActivity.kt`/`LayoutReconciler.kt` (xóa log `G2A`), `docs/plans/G2a-folders-library.md`, `docs/AGENTS.md`, `docs/Launcher-Features.md` (TT#10), `docs/project_structure.md` (cây `folder`, dòng G2a phần A, đoạn "Tệp thêm ở G2a")

- [~] **Step 1 (HOÃN, 2026-10-30, theo quyết định của người dùng): Đo Janky cơ bản** — bỏ khỏi A5 vì thư mục đang nền phẳng, số đo lúc này không dùng để quyết định gì. Gộp vào mốc "Áp kính": đo hai lần cạnh nhau (trước/sau khi áp kính) theo cách của M6 Task 2 (`dumpsys gfxinfo framestats`, ngưỡng tham chiếu 2.13%). Kịch bản: mở/đóng panel liên tiếp; lướt Home khi có ≥ 6 thư mục.
- [x] **Step 2: Xóa log tạm `G2A`** — đã xóa (2026-10-30): `LayoutReconciler`, `HomeViewModel`, `FolderPanelView`, `IconGridView`; grep `G2A` trong `app/src` không còn kết quả. Người dùng đã build lại và thử trên Live 4 (2026-10-30).

  **Bảng bằng chứng Review Focus Phần A** (máy: Vsmart Live 4; "—" = chưa kiểm tay):

  | Mục | Kịch bản | Kết quả |
  |---|---|---|
  | Mất app | gộp 2 app; thêm vào thư mục; kéo ra; ẩn trong panel; giải tán còn 1 app (D9) | Đã xác nhận trên máy (A1b–A3b) |
  | Mất app | gỡ thật app trong thư mục; thư mục toàn app bị gỡ | Đã xác nhận (A4, 7 ca) |
  | Mất app | thư mục đầy 9 app: thêm app thứ 10 | Không thêm được, đúng dự đoán (đã xác nhận) |
  | Kéo-thả mơ hồ | gộp trước, giữ mép ≥ 500 ms mới đẩy-dồn; áp cho đích là app và thư mục; kéo ra thả lên app/thư mục/chính thư mục nguồn | Đã xác nhận (A1b, A3a, A3b) |
  | Kéo-thả mơ hồ | thư mục lên thư mục; thư mục vào dock | Thư mục chỉ dịch sang chỗ khác; kéo vào dock thì bật về chỗ cũ (đã xác nhận) |
  | Panel | mở/đóng, đổi tên, nhấn giữ vào edit mode rồi kéo cùng một lần chạm | Đã xác nhận (A2, A3b) |
  | Panel | mở app rồi quay lại; tên dài/tiếng Việt có dấu | Đã xác nhận; tên bị giới hạn ký tự. `recreate()` khi panel mở (đổi sáng/tối, mức kính): — chưa kiểm tay |
  | Tính năng cũ | gỡ app dồn trong trang; catch-up; bỏ ẩn; re-flow đổi cỡ lưới | Đã xác nhận (A4) |
  | Tính năng cũ | tìm kiếm M8b với app trong thư mục; Thư viện ứng dụng M7 | Đã xác nhận, hoạt động bình thường |
  | Hiệu năng | Janky | Hoãn sang mốc "Áp kính" (xem Step 1) |
- [x] **Step 3: Đồng bộ tài liệu Phần A** (đã cập nhật `Launcher-Features.md` TT#10 và `project_structure.md`, 2026-10-30)
  _Nội dung gốc:_ (TT#10 → **Xong** khi người dùng xác nhận; giới hạn: tối đa 9 app, không "−" trên ô thư mục, không thư mục trong dock, ô và panel nền phẳng). **Chưa đóng mốc G2a.**
- [x] **Step 4: Tick Phần A và Commit** (`docs: close G2a part A (folders)`) — chỉ sau khi người dùng xác nhận trên máy thật.

---

# PHẦN B — App Library

> **Quy ước tên (chốt 2026-10-30):** trang phân nhóm này là bản cải tiến của "Thư viện ứng dụng" hiện có, nên trên giao diện vẫn gọi là App Library. Trong code dùng tiền tố `Category*` (`AppCategory`, `AppCategoryGrouper`, `CategoryGroup`, `CategoryPageView`, chuỗi `library_page_*`, `LibraryOverlayController`) để không trùng `AppLibraryActivity`/`AppLibraryAdapter`/`AppLibraryViewModel`/`AppLibrarySection` của M7 (màn Chưa ẩn / Đã ẩn, giữ nguyên, đã đo). Gộp hoặc đổi tên màn M7 là việc sau G2a, hỏi lại người dùng khi tới lúc.

### Task B1: Phân loại app — `AppCategory` + bộ phân loại + dữ liệu ✅ xong 2026-10-30

**Files:**
- Create: `domain/model/AppCategory.kt`, `domain/category/AppCategoryGrouper.kt`
- Modify: `domain/model/AppItem.kt` (thêm `category: AppCategory = AppCategory.UNDEFINED`, `isSystem: Boolean = false` có giá trị mặc định để không vỡ chỗ dùng cũ), `data/apps/LauncherAppsDataSource.kt` (đọc `applicationInfo.category` và cờ `FLAG_SYSTEM`)

**Interfaces:**
- Produces:
  - `enum class AppCategory { SOCIAL, ENTERTAINMENT, GAME, PRODUCTIVITY, NEWS, MAPS, SYSTEM, OTHER, UNDEFINED }` (`UNDEFINED` chỉ là giá trị thô từ hệ thống).
  - `enum class CategoryGroup(@StringRes-free key)`: `SOCIAL, ENTERTAINMENT, GAME, PRODUCTIVITY, NEWS, MAPS, UTILITIES, OTHER` theo D12.
  - `object AppCategoryGrouper { fun groupOf(category: AppCategory, isSystem: Boolean): CategoryGroup; fun group(apps: List<AppItem>): List<CategoryGroupApps> }` (`CategoryGroupApps(group, apps)`; nhóm rỗng bỏ, thứ tự nhóm cố định, trong nhóm theo thứ tự đầu vào = theo nhãn).

- [x] **Step 1: Mô hình + bộ phân loại thuần** theo bảng D12, ca biên: app không có category và không phải hệ thống → `OTHER`; app hệ thống không category → `UTILITIES`.
- [x] **Step 2: Nguồn dữ liệu** — `LauncherAppsDataSource` điền `category`/`isSystem` khi dựng `AppItem` (API 26+ có `ApplicationInfo.category`).
- [x] **Step 3: Log tạm `G2B`** — ghi một lần số app mỗi nhóm và 10 app đầu của mỗi nhóm kèm `category` thô. **Xóa ở Task B3.**
- [x] **Step 4: Kiểm chứng (Live 4, và Oppo A3x 344 app)** — build xanh; `adb logcat -s G2B`: số app mỗi nhóm hợp lý, không có app thiếu hoặc bị tính hai lần (tổng = số app không ẩn), phần "Khác"/"Tiện ích" không chiếm quá nửa (nếu chiếm, dán log để chỉnh bảng D12). Dán log.
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: classify apps into library groups`).


**Kết quả kiểm chứng (2026-10-30):** Live 4, 85 app: SOCIAL 6, ENTERTAINMENT 5, GAME 5, PRODUCTIVITY 23, NEWS 1, MAPS 1, UTILITIES 10, OTHER 34 (tổng khớp, không thiếu/trùng). Khác + Tiện ích ≈ 52%, hơi quá ngưỡng 50% nhưng do 34 app tự cài chưa khai báo category (`cat=-1`); người dùng **chốt giữ nguyên bảng D12**. Oppo A3x (344 app) **chưa đo**. Lệch so với Interfaces: bỏ `AppCategory.SYSTEM` (suy ra từ `isSystem`). Log `G2B` còn trong `LauncherAppsDataSource`, xóa ở B3.

---

### Task B2: Lớp phủ App Library (tách khỏi pager, D17) ✅ xong 2026-10-30

> **Lịch sử (2026-10-30):** bản đầu gắn Library làm mục thứ hai của `HomePagerAdapter` rồi phải vá liên tiếp (view type riêng, dock + pill GONE làm pager nở ra nên thêm trần chiều cao cho `IconGridView`). Người dùng quyết định tách hẳn (D17). Phần dữ liệu + view (B2a) giữ nguyên; phần gắn vào pager (B2b cũ) **gỡ bỏ** và làm lại theo overlay.

**Files:**
- Giữ (đã code, build xanh): `domain/usecase/GetCategoryGroupsUseCase.kt`, `ui/home/view/CategoryPageView.kt`, `res/layout/view_category_page.xml`, `res/layout/item_category_group.xml`, `res/drawable/bg_category_tile.xml`, `bg_category_search.xml`, `res/values` (chuỗi `library_page_*`, dimens `category_*`), `SearchViewModel.open(showAll)`.
- Gỡ: `PageId.CATEGORIES`; `HomePagerAdapter` (view type thứ hai, `CategoryHolder`, `setCategoryGroups`, `gridMaxHeightPx`, tham số `onLibrarySearchClick`, nhánh `areItemsTheSame` cho Library); `IconGridView.maxHeightPx`; trong `HomeActivity`: `showLibraryPage`, `updateLibraryProgress`, `applyLibraryProgress` (bản cũ), `libraryPageIndex`, `libraryChromeGone`, `setGridMaxHeight`, trần chiều cao trong `updateDockCells`, chấm trang `+1`.
- Create: `ui/home/LibraryOverlayController.kt` (cử chỉ + animation + tiến độ; `HomeActivity` chỉ gọi vào).
- Modify: `res/layout/activity_home.xml` (đặt `CategoryPageView` `library_view` trên `home_content`, dưới `folder_panel`/`icon_menu_scrim`/`search_overlay`/`drag_layer`; giữ `library_scrim` dưới `home_content`), `ui/home/HomeUiState.kt` (`libraryOpen`), `ui/home/HomeViewModel.kt` (`setLibraryOpen`, tự đóng khi vào edit mode/kéo/menu/mở thư mục), `ui/home/HomeActivity.kt`.

**Interfaces:**
- Consumes: `CategoryPageView.render/onSearchClick/onGroupClick`, `HomeViewModel.categoryGroups`, `SearchViewModel.open(showAll = true)`.
- Produces: `LibraryOverlayController(host: Host)` với `Host` cung cấp: chiều rộng, "có được mở không" (D14), nhận `onProgress(p)`, `onSettled(open)`; hàm `onTouch(ev): Boolean` (gọi từ `dispatchTouchEvent`), `snapTo(open, animate)`, `progress`, `isOpen`. `HomeUiState.libraryOpen: Boolean`.

Trạng thái: Đóng (`progress = 0`), Đang kéo/animate (`0 < progress < 1`), Mở (`progress = 1`). Bên trong Library: Default / Empty / Loading như B2a.

- [x] **Step 1: Dữ liệu** — `GetCategoryGroupsUseCase` (đã code).
- [x] **Step 2: Giao diện** — `CategoryPageView`: thanh tìm kiếm trong suốt trên cùng, lưới 2 cột ô nhóm lấp đầy ô (đã code).
- [x] **Step 3 (mốc B2b): Gỡ phần gắn vào pager** theo danh sách "Gỡ"; build xanh, Home hoạt động như trước B2 (không còn trang Library trong pager, chấm trang = trang thật).
- [x] **Step 4 (mốc B2b): Overlay + cử chỉ** — `library_view` trong `activity_home.xml` (ban đầu `INVISIBLE`, padding trên/dưới theo inset hệ thống); `LibraryOverlayController` bắt vuốt ngang sang trái ở trang thật cuối (đã đứng yên, ngón tay bắt đầu trong vùng pager, vượt `touchSlop`, |dx| > |dy|), gửi `ACTION_CANCEL` giả cho cây view rồi nuốt chuỗi chạm (mẫu `dispatchWithSearchSwipe`); `progress = −dx / width`; thả: `VelocityTracker` (vx < −1000 px/s hoặc `progress > 0.5` thì mở, ngược lại đóng), animate 220ms; khi `progress` đổi thì cập nhật `translationX`, `library_scrim.alpha`, `home_content.alpha`; vuốt ngang sang phải khi đang mở thì đóng theo cùng cơ chế. Vuốt xuống tìm kiếm tắt khi `progress > 0`.
- [x] **Step 5 (mốc B2c): Trạng thái và vòng đời** — `HomeUiState.libraryOpen` + `HomeViewModel.setLibraryOpen` (sống qua `recreate()`: dựng lại ở `progress = 1` không animation); Back và `onNewIntent` (nút Home) đóng Library trước các hành vi khác; không mở được và tự đóng khi edit mode / kéo / menu / panel thư mục / lớp tìm kiếm (D14); thanh tìm kiếm của Library mở lớp tìm kiếm phủ lên trên.
- [x] **Step 6 (mốc B2c): Kiểm chứng (Live 4, Oppo)** — (a) từ trang thật cuối vuốt sang trái: Library trượt theo ngón tay, nền tối dần, Home mờ dần; thả quá nửa/fling thì mở, ngược lại đóng lại; (b) ở trang thật không phải trang cuối: vuốt sang trái chỉ lật trang như cũ; (c) Library mở: tràn cả màn hình, không dock/pill; vuốt phải/Back/nút Home đóng; (d) thanh tìm kiếm mở lớp tìm kiếm với danh sách a–z (không app ẩn), gõ lọc như tìm kiếm cũ, Back về Library; (e) cài/gỡ app khi Library mở: nhóm cập nhật, không crash; (f) vào edit mode, kéo icon, mở thư mục, menu nhấn giữ: Library không mở được; (g) đổi sáng/tối khi Library mở: còn mở; (h) chỉ 1 trang Home: vuốt trái mở Library; (i) icon, dock, chấm trang của Home không đổi cỡ/vị trí so với trước G2a Phần B; (j) đang kéo icon sát mép phải ở trang cuối: vẫn tạo trang tạm như cũ, không mở Library.
- [x] **Step 7: Ghi `docs/AGENTS.md` + Commit** (`feat: add library overlay outside the pager`).

---

**Kết quả kiểm chứng (2026-10-30, Vsmart Live 4):** người dùng xác nhận đạt cả 10 ca (a–j). Oppo A3x chưa kiểm tay. Quyết định phát sinh: Library là lớp phủ ngoài pager (D17) sau khi bản gắn vào pager phải vá liên tiếp; Home mờ dần tại chỗ khi Library trượt vào (không trượt cùng); nền tối cùng màu `search_scrim` thay cho bản ảnh mờ; chấm trang không tính Library; lớp tìm kiếm mở từ Library không làm Library đóng.

---

### Task B3: Panel nhóm — bấm ô nhóm mở danh sách app, dọn log ✅ xong 2026-10-30

**Files:**
- Modify: `ui/home/view/FolderPanelView.kt` (chế độ chỉ đọc + danh sách cuộn, hoặc tách `AppGroupPanelView` dùng chung lõi), `ui/home/view/CategoryPageView.kt`, `ui/home/HomeActivity.kt`/`HomeViewModel.kt` (`openCategoryGroup`), `ui/search/SearchViewModel.kt` / `data/apps/LauncherAppsDataSource.kt` (xóa log `G2B`)

**Interfaces:**
- Consumes: `CategoryPageView.onGroupClick`, panel (A2), `HomeViewModel.onAppClicked`.
- Produces: `HomeUiState.openCategoryGroup: CategoryGroup?`.

- [x] **Step 1: Panel nhóm** — tái dùng lõi panel (A2): tiêu đề = tên nhóm (**không sửa được**), lưới 3 cột cuộn dọc khi nhiều app, bấm = mở app; đóng như D6.
- [x] **Step 2: Dọn log tạm `G2B`.**
- [x] **Step 3: Kiểm chứng (Live 4, Oppo)** — (a) bấm ô nhóm: panel hiện đúng app của nhóm, cuộn mượt (Oppo nhóm dài); (b) bấm app: mở, panel đóng; (c) Back/chạm nền/Home đóng; (d) đổi sáng/tối khi panel nhóm mở: còn, không rò cửa sổ; (e) tiêu đề không sửa được; (f) gỡ app khi panel đang mở: danh sách cập nhật hoặc panel đóng nếu nhóm hết app, không crash.
- [x] **Step 4: Ghi `docs/AGENTS.md` + Commit** (`feat: open library groups in a panel`).

---

**Kết quả (2026-10-30, Vsmart Live 4):** người dùng xác nhận đạt cả 6 ca (a–f). Lệch so với Step 1: không tái dùng lõi `FolderPanelView` (397 dòng, đầy logic kéo-thả/sửa tên) mà tách `CategoryGroupPanelView` chỉ đọc, dùng chung nền `folder_scrim` + `bg_folder_panel` để sau này áp kính chung được. Log `G2B` đã xóa. Oppo A3x (nhóm dài) chưa kiểm tay.

---

### Task B4: Đóng G2a — rà Review Focus, đồng bộ tài liệu ✅ xong 2026-10-30

**Files:**
- Modify (tài liệu): `docs/Launcher-Features.md` (TT#10, TT#11 → Xong/Một phần, giới hạn đã biết), `docs/project_structure.md` (dòng G2a ✅, cây `folder/`, `library` (trang ảo), `domain/library`, đoạn "Tệp thêm ở G2a"), `docs/room-schema.md` (mục thư mục: quy tắc dựng/giải tán thực tế, D9–D10), `docs/AGENTS.md`, file này.

- [x] **Step 1: Rà Review Focus** — bảng bằng chứng như M8b/M8c: mỗi dòng nêu kịch bản, máy, kết quả; ca chưa kiểm tay (kể cả Oppo nếu chưa đo, TalkBack) ghi rõ.
- [x] **Step 2: Đồng bộ tài liệu** như danh sách Files; ghi giới hạn đã biết (thư mục ≤ 9 app, không "−" trên thư mục, Library chỉ đọc, bảng nhóm là điểm xuất phát) và việc để sau (thư mục nhiều trang, kéo-thả trong Library, kính cho ô đóng).
- [x] **Step 3: Báo tên file/mục định xóa (nếu có)** để người dùng quyết; không tự xóa.
- [x] **Step 4: Tick và đóng** — G2a chỉ được tick sau khi người dùng xác nhận toàn bộ trên máy thật.
- [x] **Step 5: Commit** (`docs: close G2a and sync project docs`).

**Bảng bằng chứng Review Focus — Phần B** (máy: Vsmart Live 4, Android 11; "—" = chưa kiểm tay):

| Mục | Kịch bản | Kết quả |
|---|---|---|
| Phân loại | 85 app: tổng các nhóm khớp, không thiếu/trùng; app hệ thống không category vào Tiện ích | Đạt (log B1). Khác + Tiện ích ≈ 52% do app tự cài chưa khai báo category; giữ bảng D12 |
| Phân loại | Oppo A3x (344 app) | — chưa đo |
| Lớp phủ | vuốt trái ở trang cuối mở theo ngón tay; thả quá nửa/fling; vuốt phải/Back/Home đóng | Đạt (B2, 10 ca a–j) |
| Lớp phủ | vào edit mode / kéo icon / mở thư mục / menu: không mở được; 1 trang Home + Library; đổi sáng/tối khi mở | Đạt (B2) |
| Lớp phủ | cài/gỡ app khi Library mở | Đạt (B2 ca e) |
| Tìm kiếm | thanh tìm kiếm Library → danh sách a–z không app ẩn, lọc như M8b, Back về Library | Đạt (B2) |
| Panel nhóm | mở đúng app của nhóm, cuộn, bấm app mở được, Back/chạm nền/Home, đổi sáng/tối, tiêu đề cố định, gỡ app khi mở | Đạt (B3, 6 ca a–f) |
| Panel nhóm | nhóm rất dài trên Oppo | — chưa kiểm tay |
| Hiệu năng | Janky (panel, lướt Home nhiều thư mục) | Chưa đo, dời sang mốc "Áp kính" (A5 Step 1) |

**Ca chưa kiểm tay của cả G2a (đưa vào nợ kiểm chứng):** Oppo A3x (344 app, nhóm dài); TalkBack cho thư mục/ô nhóm/thanh tìm kiếm Library; `recreate()` khi panel thư mục đang mở; force-stop giữa lúc ghi thư mục; bàn phím che panel thư mục khi sửa tên; Janky; tier `CLASSIC_GPU` (Android 31–32, chưa có máy đo).

**Giới hạn đã biết:** thư mục tối đa 9 app (không nhiều trang); không có "−" trên ô thư mục; không thả thư mục vào thư mục/dock; ô thư mục, panel, ô nhóm, thanh tìm kiếm Library đều nền phẳng (kính ở mốc "Áp kính"); Library chỉ đọc (không kéo-thả/ẩn); bảng nhóm D12 là điểm xuất phát; Home mờ tại chỗ khi Library trượt vào (không trượt cùng).

**Việc để sau:** thư mục nhiều trang; kéo-thả/ẩn trong Library; "Mới thêm gần đây"; gộp hoặc đổi tên màn Thư viện ứng dụng M7 (`AppLibraryActivity`) với Library mới (hỏi lại người dùng); kính cho ô đóng/panel/ô nhóm/thanh tìm kiếm; đo Janky hai lần cạnh nhau.

**Tên file/mục định xóa:** không có.

---

## Ghi chú cho mốc "Áp kính" (viết 2026-10-30, đọc trước khi viết plan mốc đó)

**Gom vào mốc này:** kính cho ô thư mục đóng và panel thư mục (G2a), panel nhóm + ô nhóm của trang App Library (G2a), thanh tìm kiếm (M8b), pill tìm kiếm (M8c). Đo Janky trên Live 4 và Oppo A3x theo cách M6 Task 2 (ngưỡng tham chiếu: `StaticBlurSurface` 2.13% Janky).

**Vì sao tách khỏi G2a (đọc code 2026-10-30, suy luận chưa kiểm chứng bằng đo):**
- `StaticBlurSurface.computeCrop()` chỉ tính vùng cắt MỘT lần theo vị trí màn hình của view lúc vẽ đầu (`croppedBitmap` được giữ cho tới khi `setBackdrop` hoặc `onSizeChanged`). `RealGlassSurface` để `enableDynamicBackground = false`, cũng chụp nền một lần.
- Dock đứng yên nên đủ dùng. Ô thư mục nằm trong `ViewPager2` nên di chuyển khi vuốt trang, khi rung ở edit mode và khi kéo-thả: nền kính sẽ lệch, hoặc phải bật dynamic (ràng buộc B3), mà bật dynamic cho nhiều ô là đắt.
- Hướng thử: panel (đứng yên) làm kính trước; ô đóng thử tính lại vùng cắt khi `ViewPager2` về `SCROLL_STATE_IDLE` và để phẳng lúc đang lướt/kéo/rung; hoặc bật dynamic chỉ trong lúc di chuyển. Chọn theo số đo.
- Tier `CLASSIC_GPU` (Android 31–32) chưa có máy đo (Oppo A3x = 34, Live 4 = 30): cần mượn máy hoặc chấp nhận để suy luận.
- `GlassBudget`: `CLASSIC` = 1 đã dành cho dock; mọi phần tử kính mới phải đo lại.

**Ý tưởng đã bỏ khỏi G2a, giữ để dùng lại:** `FolderGlassPolicy.planFor(tier)` (hàm thuần, `core/glass`) trả `FolderGlassPlan(cellMode, maxGlassCells, panelMode)` với `FolderGlassMode { REAL, STATIC_BLUR, FLAT }`; `GlassBudget.maxFolderGlassCells(tier)` đã trừ phần dock; ô vượt ngân sách rơi về `FLAT`, ưu tiên theo thứ tự đọc của trang đang hiện; khi panel mở các ô phía sau có thể hạ về `FLAT`. Ô thư mục đóng hiển thị tối đa 9 icon nhỏ (3×3).

