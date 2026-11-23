# HT — Công tắc Màn hình chính: chấm trang, nút tìm kiếm, vuốt xuống (màn 3.1 một phần, 3.3 một phần) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ ca kính thật của pill). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/ht-home-toggles` · **Worktree:** `E:\SourceTree\IosLauncher-wt\HT-home-toggles` · **PR:** #52 (draft) · **Bắt đầu:** 2026-11-13 (trên `main` b76f4bb) · **Plan duyệt:** 2026-11-13 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Code xong 4 Task (đều qua review điều phối); Live 4 một phần đã chạy (điều phối, 2026-11-13); chờ bài kiểm tay của chủ dự án và ca Oppo kính thật
**Khóa:** không (không Room, không khóa schema). Cần Oppo: chỉ một lượt ngắn cho ca pill kính thật (xem "Kính của pill").

**Goal:** Ba công tắc cho Màn hình chính, áp ngay khi quay về Home, không cần khởi động lại launcher: (1) "Vuốt xuống để tìm kiếm", (2) "Nút Tìm kiếm trên màn hình" (hai hàng mới ở đầu màn 3.3 Cài đặt tìm kiếm, mục "Cách mở"), (3) "Hiện chấm trang" (màn 3.1 Lướt màn hình, tạo mới ở nhánh này, chỉ có hàng này). Mặc định giữ đúng hành vi hiện tại (cả ba bật). **"Lướt vòng" đã bỏ** (chủ dự án, 2026-11-13, xem mục "Lướt vòng"): không có hàng, không có Task.
**Architecture:** Một repository nhỏ `HomeTogglesRepository` (cùng mẫu `SearchPreferencesRepository` và WU) giữ ba khóa DataStore. Màn Cài đặt ghi qua `HomeTogglesViewModel`. Phía Home, **một controller mới `HomeTogglesController`** thu flow bằng `lifecycleScope` rồi đẩy giá trị vào hai chỗ đang sở hữu hành vi tương ứng: `SearchPillView` (nút tìm kiếm và chấm) và `SearchOverlayController` (cổng cử chỉ vuốt xuống). Không dựng lại Activity: cả ba thứ chỉ là cờ lúc chạy.
**Tech Stack:** View/XML, DataStore Preferences có sẵn, Hilt. Không dependency mới.
**Spec:** `docs/Figma-Draft.md` mục 3 (màn 3.1, 3.3), `Launcher-Features.md` TT#9; plan [M8b](M8b-search.md), [M8c](M8c-search-pill.md), [PL](PL-dots-and-launch.md), [AP](AP-applib-animations.md), [LB](LB-library-back-swipe.md), `docs/report/FX7-home-gestures.md`; plan và knowledgement [G2d](G2d-settings-hub.md) (công thức thêm màn/hàng).

## Vì sao chọn thiết kế này

### Vấn đề cần giải (đọc code, 2026-11-13)

1. **Pill và chấm là MỘT view.** `SearchPillView` (M8c) là viên nang cố định 144x32dp trong vùng cao 48dp; đứng yên nó là ô "Tìm kiếm", đang lướt / edit mode / kéo-thả (`HomePagerController.requestPillMode`) nó làm mờ chéo sang hàng chấm (PL: chấm đổi màu theo vị trí liên tục). Kính của pill (`BackdropBlurDrawable`, hoặc `RealGlassSurface` khi chọn Tùy chỉnh) vẽ cho viên nang. Chấm hiện nay **chỉ có khi lướt/sửa/kéo** (M8c D1, chủ dự án chấp nhận 2026-10-30), không phải luôn hiện như dòng mô tả của bản nháp.
2. **Vuốt xuống tìm kiếm** nằm ở `SearchOverlayController.onTouch`, chạy trong `HomeTouchRouter.dispatch` sau Library. `SearchSwipeDetector` chỉ QUAN SÁT; điều kiện bật (`enabled`) truyền theo từng sự kiện (đã loại edit mode, menu nhấn giữ, thư mục, Library không rảnh, tìm kiếm đang mở). Vùng bắt đầu: dưới thanh trạng thái + 48dp tới đỉnh dock (M8b D4).
3. **(Đã bỏ) Lướt vòng đụng App Library.** Library là lớp phủ ngoài pager (G2a D17). Vuốt trái ở trang cuối là cử chỉ mở Library (`LibraryOverlayController`, ứng viên `OPEN`, `canOpen()` đòi pager đứng yên hoặc đang trượt nốt tới trang cuối); vuốt phải ở trang cuối là lùi trang; vuốt phải trong Library đóng nó (LB). FX7 mất bốn vòng và LB một vòng riêng cho đúng cử chỉ này (spring ngắt được, `GRAB`, đích trượt `settleTargetPage`). `ViewPager2` không có chế độ vòng.
4. **Home nhận cài đặt thế nào hôm nay (đọc code).** Ba đường: (a) `HomeViewModel` thu `settingsRepository.settings` (lưới, nhãn) vào `HomeUiState`; (b) `GlassEnvironment` (singleton) thu `settings`, báo listener cho view; (c) `RecreateGuard` ở `onResume` dựng lại Activity cho thứ gắn lúc dựng View (mức kính, kiểu icon). Bốn công tắc của HT không gắn lúc dựng View nên không cần (c).

### Chỗ lưu: kho riêng, không thêm vào `SettingsKeys`/`LauncherSettings` (trả lời điều phối)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| 1. Thêm vào `LauncherSettings` + `SettingsKeys` + `SettingsRepositoryImpl` + `SettingsUiState` + `SettingsViewModel` | Hướng "thêm trường cuối" của G2d | Home đã đọc `LauncherSettings`; một nguồn | Năm file dùng chung mà WU (đã chọn kho riêng), AB, RN cùng chạm vùng đó, rebase dễ xung đột; `settings` phát lại cho MỌI khóa nên bộ thu phải tự `map` + `distinctUntilChanged` (như `GlassEnvironment`); đổi cả `SettingsViewModel` (đã có 7 phụ thuộc) | Không |
| 2. Thêm hai khóa vào `SearchPreferences` | Có sẵn màn và ViewModel | Ít file | `SearchViewModel` thu cả `SearchPreferences` và dựng lại chỉ mục khi nó đổi (đọc code `SearchViewModel.kt:100`): gạt công tắc cử chỉ sẽ dựng lại chỉ mục tìm kiếm vô ích; trộn "nguồn kết quả" với "cách mở"; không có chỗ cho công tắc chấm trang | Không |
| **3. Kho riêng `HomeTogglesRepository` + `HomeTogglesKeys` trên cùng `DataStore<Preferences>`** | Cùng mẫu M8d (`SearchPreferencesRepository`/`SearchPrefsKeys`) và WU (`WeatherPreferencesRepository`/`WeatherPrefsKeys`) | Toàn file mới (domain, data, di, một ViewModel); không đụng năm file chung; flow nhỏ có `distinctUntilChanged` nên Home chỉ phản ứng khi một trong bốn công tắc đổi; PT/LO sau này thêm công tắc cùng chỗ | Một interface + một lớp + một module; Home không đọc qua `HomeUiState` (không cần: không có gì phải sống qua `recreate()`, mỗi Activity mới đọc lại từ DataStore) | **Chọn** |

Nguồn: đọc code. Lệch nhẹ so với brief ("append khóa vào `SettingsKeys`"): cố ý, cùng lý do WU3. Khóa nằm ở `data/settings/HomeTogglesKeys.kt` (file mới, không phải `SettingsKeys.kt`).

### Phía Home: controller đọc flow, không qua `HomeViewModel`/`HomeUiState`

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Thêm trường vào `HomeUiState` + `HomeViewModel` | Theo đường (a) | Một nguồn state, `render()` áp | Hai file độc quyền thêm; `HomeViewModel` đã 1025 dòng; phát lại `render()` toàn bộ chỉ vì một cờ; cờ không phải state nghiệp vụ cần sống qua `recreate()` | Không |
| B. Singleton `HomeTogglesEnvironment` kiểu `GlassEnvironment` (EntryPoint) | View/controller tự hỏi | Không sửa `HomeActivity` | Thêm singleton toàn cục có listener phải gỡ đúng; ba nơi tự lấy từ EntryPoint, khó thấy luồng | Không |
| **C. `HomeTogglesController` (ui/home/controller), dựng ở `onCreate` như các controller R1** | Thu `repository.toggles` bằng `lifecycleScope`, gọi `searchPill.setOptions(...)`, `search.swipeDownEnabled = ...` | Theo đúng mẫu R1 ("controller là lớp Kotlin thường, nối ở `onCreate`"); luồng đọc được từ một file; `HomeActivity` chỉ thêm ~4 dòng | Cần sửa `HomeActivity` (độc quyền), ít dòng | **Chọn** |

Thu **không** gắn `repeatOnLifecycle(STARTED)`: Home bị Settings che nên ở trạng thái `STOPPED`; thu liên tục thì giá trị mới (DataStore đã commit trong Settings) áp lúc Home còn khuất, lúc quay về khung đầu đã đúng, không nháy. Cùng tinh thần `GlassEnvironment` (áp lúc Home khuất). Trước emission đầu dùng mặc định (= hành vi hiện tại), nên cờ chưa biết không làm hỏng gì; `SearchPillView` vốn `INVISIBLE` cho tới khi lưới `Ready` (hàng trăm ms), DataStore đã cache trả về trước đó nhiều.

### Bốn tổ hợp pill × chấm (định nghĩa hành vi)

"Đang hoạt động" = pager đang lướt/trượt, edit mode, hoặc đang kéo icon (đúng `pillWanted` hôm nay). Mọi thứ nằm trong `SearchPillView`; layout không đổi (mục "Chỗ trống").

| Nút tìm kiếm | Chấm | Lúc đứng yên | Lúc đang hoạt động |
|---|---|---|---|
| Bật | Bật | Viên nang kính "Tìm kiếm", bấm mở tìm kiếm | **Như hôm nay (M8c + PL):** làm mờ chéo sang chấm theo ngón tay, không bấm được |
| Bật | Tắt | Như trên | **Không đổi sang chấm.** Viên nang vẫn "Tìm kiếm" nhưng không bấm được (giữ luật D3 của M8c: edit mode/kéo không mở tìm kiếm); mô tả trợ năng vẫn "Tìm kiếm" |
| Tắt | Bật | **Chấm trần luôn hiện** (không viên nang, không kính), vị trí chấm theo `position` như PL; không bấm | Như đứng yên (chấm luôn hiện) |
| Tắt | Tắt | Không vẽ gì, không nhận chạm, trợ năng bỏ qua | Như đứng yên |

- **Vì sao chấm trần luôn hiện khi nút tìm kiếm tắt** (chủ dự án/điều phối chốt 2026-11-13): M8c D1 (chấm chỉ khi lướt) là hệ quả của việc chấm lồng trong pill chiếm cùng chỗ; khi pill tắt, chỗ đó trống nên dòng mô tả bản nháp "chỉ báo vị trí trang ở cuối màn hình" thành đúng. Chấm trần tránh luôn việc phải làm kính cho viên nang chỉ để chứa chấm (xem "Kính của pill"). Rủi ro: chấm trắng trên nền sáng khó thấy (suy luận chưa kiểm chứng; trước M8c chấm trần đã dùng và được chấp nhận, nhưng M8c đặt chấm vào nền tối); giảm bằng vòng tối mờ dưới mỗi chấm, kiểm ở Task 3 trên ảnh nền sáng.
- **Chỗ trống khi cả hai tắt** (chốt 2026-11-13): giữ nguyên dải cao 48dp (view vẫn `VISIBLE` nhưng không vẽ, không nhận chạm). Lý do: `home_pager` có weight 6, dock weight 1, pill cao cố định 48dp; bỏ dải đó (`GONE`) làm pager cao hơn và `GridMetrics.Cells` đổi, bind lại cả lưới và dock mỗi lần gạt công tắc, đúng vùng đã từng sinh lỗi dock 0x0 (M3 Task 5, M5 Task 4, AN Task 9f). Giữ dải = không đổi bố cục, không đổi cỡ icon. Cái mất: 48dp khoảng đệm giữa hàng icon cuối và dock (giống iOS có chấm); xét lại nếu chủ dự án muốn lấy lại chỗ (làm riêng vì động tới `GridMetrics`).
- **Cổng chạm:** `isClickable = nútTìmKiếmBật && !đangHoạtĐộng`; `onInterceptTouchEvent` chỉ nuốt khi nút bật; `PressScale` và lớp nhấn chỉ chạy khi bấm được. Nút tắt thì chạm vào dải đó không có phản ứng.

### Kính của pill: có làm hỏng làm mới kính không? Có cần Oppo không?

- Mặc định pill là `STATIC_BLUR` (`BackdropBlurDrawable`, đọc `GlassPolicy`: mọi vai trò mờ tĩnh trừ dock/panel/menu). Chỉ mức kính **Tùy chỉnh** + công tắc pill bật mới thành `RealGlassSurface`. Trên hai máy test, mờ tĩnh là đường chính.
- Thiết kế **không** tạo đường mới cho kính: khi nút tìm kiếm tắt, `applyGlass()` đặt `pillGlass = null` và `setRealActive(false)` (con kính thật `GONE`, đường đã có); khi bật lại, `applyGlass()` dựng lại drawable hoặc `setRealActive(true)` → `fresh` → `scheduleCapture()` (đường "hiện lại" đã có từ GS Task 4). Nghe `GlassEnvironment` và `backdropListener` vẫn gọi `applyGlass()` nên đổi ảnh nền khi pill tắt không để lại ảnh chụp cũ: lần bật lại chụp mới. Không dùng alpha trên con kính thật (chỉ bật/tắt visibility), tránh đường chưa kiểm của `LiquidGlassView` với alpha.
- Chấm trần (nút tắt, chấm bật) không dùng kính nên không có gì để làm mới.
- **Oppo: có, một lượt ngắn, chỉ ca kính thật** (mục 4.2 không áp, đây là bẫy GL/DG/GF từng chỉ lộ trên Oppo): Tùy chỉnh + pill bật → tắt nút tìm kiếm → đổi ảnh nền → bật lại → ảnh chụp pill phải khớp nền (cách đo ở knowledgement DG mục 7). Live 4 chạy được Tùy chỉnh (đường chụp CPU) nên chạy trước ở đó. Mờ tĩnh mặc định không cần Oppo. Nhãn: suy luận từ code, chưa đo.

### Vuốt xuống bị tắt: làm gì thay, tương tác với cử chỉ dọc khác

- **Không làm gì.** Cử chỉ chỉ là cổng `enabled` của `SearchOverlayController.onTouch`; tắt thì `zone = EMPTY_ZONE` và `SearchSwipeDetector` không theo dõi, chuỗi chạm đi xuống cây view như một cú vuốt dọc bình thường (pager không cuộn dọc, không icon nào nhận) — cùng hành vi mọi lúc detector đã đang bị chặn (edit mode...). Thanh thông báo hệ thống không đổi: M8b D4 vốn đã chừa mép trên (thanh trạng thái + 48dp) cho hệ thống nên vuốt từ mép vẫn kéo thông báo. Launcher bên thứ ba tự mở thanh thông báo ở giữa màn hình phải dùng API ẩn (`StatusBarManager.expandNotificationsPanel` qua reflection, suy luận từ tài liệu, chưa kiểm chứng trên targetSdk 37): **không làm** (câu hỏi 3).
- **Các cử chỉ dọc/ngang khác vì sao không bị đụng** (đọc code): detector chỉ QUAN SÁT và đã nằm SAU `iconMenu.onTouchPrelude`, phiên kéo icon, và `library.onTouch` trong `HomeTouchRouter`; thêm một điều kiện `&& swipeDownEnabled` vào `enabled` không đổi thứ tự. Widget thời tiết mở bảng bằng `BottomSheetDialog` (cửa sổ riêng, chạm không qua `dispatchTouchEvent` của Activity); edit mode, menu nhấn giữ, thư mục, nhóm Library, Library đang kéo/animate đều đã nằm trong `enabled`. Không có cử chỉ dọc nào khác trên Home ngoài vuốt xuống tìm kiếm.
- **Controller sở hữu sau R1:** `SearchOverlayController` (`ui/home/controller`). `HomeActivity.dispatchTouchEvent` → `HomeTouchRouter.dispatch` → `search.onTouch`. HT thêm một thuộc tính `swipeDownEnabled` vào controller đó, do `HomeTogglesController` đặt.

### Lướt vòng: đã bỏ (chủ dự án, 2026-11-13)

Cùng một cú vuốt trái ở trang cuối không thể vừa mở Library vừa về trang đầu (`LibraryOverlayController` ứng viên `OPEN`; FX7 và LB đã mất nhiều vòng cho đúng cử chỉ này; `ViewPager2` không có chế độ vòng). Các phương án đã xét: A bỏ; B chỉ khi Thư viện tắt (chờ LO); C vòng qua Library (đụng `LibraryOverlayController`, rủi ro cao); D vòng ngược từ trang 1. **Chủ dự án chọn A: bỏ.** Màn 3.1 chỉ có "Hiện chấm trang"; PT thêm hiệu ứng trang sau bằng cách sửa `SwipeSettingsScreen`. Nếu sau này muốn lại lướt vòng, xét lại B (sau LO) hoặc nhánh riêng.

### Vì sao chọn thiết kế này (tóm)

Kho riêng + controller đọc flow cho đúng điều phối đã dặn ("ưu tiên đọc flow trong controller") và giữ số file độc quyền ở mức tối thiểu; tổ hợp pill/chấm quyết định trong **một view** vì nó đã là một view và đã có cơ chế chế độ; không đổi bố cục nên không chạm `GridMetrics`.

### Đánh đổi chấp nhận

- Chấm trần có thể khó thấy trên nền rất sáng (xét lại: thêm viên nang). Dải 48dp trống khi tắt cả hai (xét lại: lấy lại chỗ, nhánh riêng). Không có lướt vòng.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| HT1 | Mặc định: vuốt xuống BẬT, nút tìm kiếm BẬT, chấm BẬT | Claude đề xuất 2026-11-13; khớp hành vi hiện tại và công tắc của bản nháp (ảnh `v2-31`, `v2-33`) |
| HT2 | Kho riêng `HomeTogglesRepository` + `HomeTogglesKeys`, không sửa `SettingsKeys`/`LauncherSettings` | Claude đề xuất 2026-11-13; xem bảng; cùng WU3 |
| HT3 | `HomeTogglesController` đọc flow; không `HomeViewModel`/`HomeUiState`/`activity_home.xml` | Claude đề xuất 2026-11-13 |
| HT4 | Không dựng lại Activity, áp ngay | Đọc code: cờ lúc chạy |
| HT5 | Cả hai nút tìm kiếm tắt vẫn được phép (không chặn, không cảnh báo) | Claude đề xuất (câu hỏi 4): Cài đặt vào được qua edit mode, Library có thanh tìm kiếm riêng |
| HT6 | Chữ hàng bám bản nháp, sửa một chỗ: "Vuốt xuống ở giữa màn hình chính" (bản nháp ghi "bất kỳ đâu"; thật ra mép trên và dock bị loại, M8b D4) | Claude đề xuất 2026-11-13 |
| HT7 | Màn 3.1 tạo ở nhánh này chỉ có MỘT hàng ("Hiện chấm trang"); sáu hiệu ứng trang thuộc PT | Brief điều phối 2026-11-13; ô giữ chỗ trong `SettingsScreens` ghi `(PT)`: PT sửa `SwipeSettingsScreen`, không đăng ký lại |
| HT8 | **Bỏ "Lướt vòng"** (không hàng, không Task, không khóa) | Chủ dự án 2026-11-13 (câu hỏi 1, phương án A) |
| HT9 | Chấm trần luôn hiện khi nút tìm kiếm tắt; vuốt xuống tắt = không làm gì; cho phép tắt cả hai lối mở tìm kiếm không cảnh báo; giữ dải 48dp khi tắt cả hai; vòng tối dưới chấm quyết ở Task 3 trên ảnh nền sáng | Điều phối 2026-11-13 (câu hỏi 2-5, theo khuyến nghị) |
| HT10 | Task 4 không đánh dấu "bắt buộc kiểm tay": chỉ thêm một điều kiện vào cử chỉ đã có, không phải cử chỉ mới (mục 4.2); bài kiểm cuối bao quát | Điều phối 2026-11-13 |

## Ngoài phạm vi / ghi để sau

- Sáu hiệu ứng chuyển trang (PT). Công tắc "Bật thư viện ứng dụng" (LO). Lướt vòng (đã bỏ, HT8). Lấy lại 48dp khi tắt cả hai. Mở thanh thông báo hệ thống khi vuốt xuống bị tắt. Các hàng khác của 3.3 ("Tệp", "Gợi ý từ web", "Công cụ tìm kiếm web": FD7/để sau).

## Vùng file

Đường dẫn dưới `app/src/main/java/gambi/global/os26launcher/` trừ khi ghi khác.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/HomeToggles.kt`, `domain/repository/HomeTogglesRepository.kt` | Tạo | không |
| `data/settings/HomeTogglesRepositoryImpl.kt`, `data/settings/HomeTogglesKeys.kt` | Tạo | không |
| `di/HomeTogglesModule.kt` | Tạo | không (file mới, tránh `RepositoryModule` dùng chung) |
| `ui/settings/HomeTogglesViewModel.kt`, `ui/settings/SwipeSettingsScreen.kt` | Tạo | không |
| `ui/settings/SearchSettingsScreen.kt` | Sửa (hai hàng + hai Header + thu flow) | không (chỉ HT sửa) |
| `ui/settings/SettingsScreen.kt` | Sửa MỘT ô `// 3.1 SWIPE (PT)` | dùng chung (thêm một dòng) |
| `ui/settings/SettingsRow.kt` | Sửa (thêm `ID_...` cuối) | dùng chung |
| `res/values/strings.xml` | Sửa (khối `ht_` ở cuối) | dùng chung |
| `ui/home/view/SearchPillView.kt` | Sửa (`setOptions`, bốn tổ hợp, cổng chạm) | không |
| `ui/home/controller/HomeTogglesController.kt` | Tạo | không |
| `ui/home/controller/SearchOverlayController.kt` | Sửa (`swipeDownEnabled`) | không |
| **`ui/home/HomeActivity.kt`** | Sửa ~4 dòng (`@Inject` repository, dựng controller, `setup()`) | **ĐỘC QUYỀN** |

**Độc quyền cần:** chỉ `HomeActivity`, vì phải dựng controller mới ở `onCreate` (thứ tự dựng là hành vi) và tiêm repository. **Không cần:** `HomeViewModel`, `HomeUiState`, `activity_home.xml` (cờ không phải state nghiệp vụ, không đổi layout), `core/glass/*` (chỉ gọi API sẵn có), `DragHelper`, `LayoutReconciler`, `data/layout/db/*`. Không sửa `SettingsKeys.kt`, `SettingsRepositoryImpl.kt`, `LauncherSettings.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsHubScreen.kt`, manifest.

## Global Constraints

- Tầng `ui → domain ← data`: `HomeToggles`/`HomeTogglesRepository` ở `domain` không import `android.*`; `HomeActivity` và controller chỉ biết interface domain.
- Chữ ở `strings.xml`, một khối liền ở **cuối** file, tiền tố `ht_`. Màu/dimens mới (nếu có vòng tối của chấm) ở cuối `colors.xml`/`dimens.xml`, tiền tố `ht_`.
- Không Room, không migration. Khóa DataStore mới: `home_toggle_swipe_down_search`, `home_toggle_search_pill`, `home_toggle_page_dots` (boolean; vắng = mặc định ở HT1). Chỉ thêm, không đổi khóa cũ.
- Flow có `catch` IOException → mặc định và `distinctUntilChanged` (mẫu `SearchPreferencesRepositoryImpl`). ViewModel báo lỗi lưu bằng `Channel` + Toast + dựng lại hàng từ state thật (mẫu `SearchSettingsViewModel.save`).
- Không đổi hành vi khi mọi công tắc ở mặc định. Mọi lớp tạm vẫn tuân quy tắc HB (không thêm lớp phủ mới ở HT).
- Không dựng lại Activity cho HT. Không thêm log vào bản nộp (log tạm có tiền tố `HT` phải xóa và grep lại trước khi báo xong Task).
- Build `gradlew.bat assembleDebug` xanh mới báo xong Task. Commit tiếng Anh `<type>: <imperative>` ≤ 72 ký tự, không dấu, có dòng `Co-Authored-By`. Chỉ `git add` theo đường dẫn tường minh.

## Review Focus

1. **Mặc định = hành vi cũ:** cài mới/xóa dữ liệu, mọi thứ như trước HT (pill, chấm khi lướt, vuốt xuống).
2. **Áp không khởi động lại:** gạt công tắc ở Settings, Back về Home → đổi ngay, không `recreate()` (log số lần `onCreate` hoặc `dumpsys activity` không đổi), không nháy.
3. **Bốn tổ hợp pill × chấm đúng bảng**, kể cả edit mode, kéo icon, trang tạm, 1 trang, nhiều trang, PL (chấm mờ dần theo ngón tay vẫn đúng khi pill tắt).
4. **Layout không đổi** giữa các tổ hợp: cỡ icon, vị trí dock, chiều cao pager giống nhau (ảnh chụp trước/sau cùng trang).
5. **Kính pill:** bật lại nút sau khi đổi ảnh nền vẫn khớp nền (Live 4 mờ tĩnh + Tùy chỉnh; Oppo ca kính thật).
6. **Vuốt xuống tắt:** không mở lớp tìm kiếm ở mọi điểm bắt đầu; bật lại thì mở đúng một lần; **không đổi** vuốt ngang, mở/đóng Library (FX7/LB), kéo icon, menu nhấn giữ, mép trên kéo thông báo hệ thống.
7. **Cả hai nút tìm kiếm tắt:** không có đường mở tìm kiếm từ Home; Library vẫn mở được lớp tìm kiếm bằng thanh của nó; Cài đặt vẫn vào được.
8. **Hồi quy Cài đặt tìm kiếm:** các hàng cũ (hệ thống, lối tắt, danh bạ + gợi ý quyền, gợi ý, xóa lịch sử) hoạt động như G2d; hai Header mới không làm vỡ nhóm bo góc.
9. **Không sửa ngoài vùng:** diff không chạm file ngoài bảng Vùng file; `HomeActivity` chỉ vài dòng nối (đo được: 6 dòng thêm, 0 dòng đổi/xóa).

---

### Task 1: Kho `HomeToggles` (domain, data, DI) và `HomeTogglesViewModel`

**Bắt buộc kiểm tay:** không (chưa có hành vi nhìn thấy).
**Files:** `domain/model/HomeToggles.kt`, `domain/repository/HomeTogglesRepository.kt`, `data/settings/HomeTogglesRepositoryImpl.kt`, `data/settings/HomeTogglesKeys.kt`, `di/HomeTogglesModule.kt`, `ui/settings/HomeTogglesViewModel.kt`.
**Interfaces:**
- Produces: `data class HomeToggles(swipeDownSearch = true, searchPill = true, pageDots = true)`; `interface HomeTogglesRepository { val toggles: Flow<HomeToggles>; suspend fun setSwipeDownSearch(Boolean); setSearchPill(Boolean); setPageDots(Boolean) }`; `HomeTogglesViewModel { uiState: StateFlow<HomeToggles>; failures: Flow<Int>; setSwipeDownSearch/…(Boolean) }`.
- Consumes: `DataStore<Preferences>` (có sẵn ở `DataStoreModule`).
- [x] **Step 1:** Model + interface + khóa + Impl (`@Singleton`, flow `catch`/`distinctUntilChanged`), `@Binds` trong module mới.
- [x] **Step 2:** `HomeTogglesViewModel` (`@HiltViewModel`, mẫu `SearchSettingsViewModel`: `stateIn(WhileSubscribed(5_000), HomeToggles())`, `save {}` bắt lỗi → `R.string.settings_save_failed`).
- [x] **Step 3: Build xanh** (`gradlew.bat assembleDebug`). Chưa có kịch bản máy; kiểm gián tiếp ở Task 2.

### Task 2: Màn 3.1 "Lướt màn hình" và hai hàng "Cách mở" ở 3.3

**Bắt buộc kiểm tay:** không.
**Files:** `ui/settings/SwipeSettingsScreen.kt` (tạo), `ui/settings/SearchSettingsScreen.kt`, `ui/settings/SettingsScreen.kt` (một ô), `ui/settings/SettingsRow.kt` (`ID_HT_SWIPE_DOWN`, `ID_HT_SEARCH_PILL`, `ID_HT_PAGE_DOTS`, `ID_HT_OPEN_HEADER`, `ID_HT_SEARCH_IN_HEADER`), `res/values/strings.xml` (khối `ht_`).
**Interfaces:** Consumes `HomeTogglesViewModel`. Produces màn `SettingsScreenId.SWIPE` đăng ký (mục "Lướt màn hình" ở trang chủ tự bật, không sửa hub).
- [x] **Step 1: `SwipeSettingsScreen`** (`titleRes = settings_hub_swipe`): một hàng `Switch` "Hiện chấm trang" (mô tả "Chỉ báo vị trí trang phía trên Dock"); lấy `HomeTogglesViewModel` bằng `ViewModelProvider(ctx.activity)`, thu `uiState` và `failures` trong `observe()` (gọi `ctx.rerender`), thay ô giữ chỗ `// 3.1 SWIPE (PT)`.
- [x] **Step 2: `SearchSettingsScreen.rows`** = Header "Cách mở" + `Switch` "Vuốt xuống để tìm kiếm" + `Switch` "Nút Tìm kiếm trên màn hình" + Header "Tìm trong" + `SearchSettingsBlock.rows(...)` như cũ; xử lý `onSwitchChanged` cho hai id mới; thu thêm `HomeTogglesViewModel` trong `observe()` (một `launch` nữa trong `coroutineScope` đang có).
- [x] **Step 3: Chuỗi** `ht_*` (tiếng Việt, đủ dấu) ở cuối `strings.xml`. Không xóa chuỗi cũ.
- [ ] **Step 4: Kịch bản kiểm (Live 4, chưa có tác dụng lên Home)** — (a) hub → "Lướt màn hình" mở được, chỉ có hàng "Hiện chấm trang", bật; (b) hub → "Cài đặt tìm kiếm": nhóm "Cách mở" có hai công tắc bật, nhóm "Tìm trong" giữ nguyên các hàng cũ, bo góc đúng hai khối; (c) gạt từng công tắc, Back, vào lại: giữ giá trị; `adb shell am force-stop gambi.global.os26launcher` rồi mở lại Settings qua edit mode: giữ giá trị; (d) hồi quy hàng danh bạ (gợi ý quyền), "Xóa lịch sử" vẫn mở hộp thoại; (e) ngắt lưu (không cần thử, đọc code): lỗi → Toast và công tắc về đúng giá trị.

### Task 3: Nút tìm kiếm và chấm trang trên Home (bốn tổ hợp) + nối controller

**Bắt buộc kiểm tay:** không (hiển thị và cổng bấm, không phải cử chỉ mới). Kiểm cuối bài (mục "Bài kiểm").
**Files:** `ui/home/view/SearchPillView.kt`, `ui/home/controller/HomeTogglesController.kt` (tạo), `ui/home/HomeActivity.kt` (~4 dòng), `res/values/colors.xml`/`dimens.xml` (khối `ht_` nếu cần vòng tối của chấm).
**Interfaces:** Consumes `HomeTogglesRepository.toggles`. Produces `SearchPillView.setOptions(showSearch: Boolean, showDots: Boolean)`; `HomeTogglesController(binding, scope, repository, search)` với `setup()`.
- [x] **Step 1: `SearchPillView.setOptions`** theo bảng bốn tổ hợp: tính lại đích làm mờ chéo (nút bật + chấm bật: như cũ; nút bật + chấm tắt: không bao giờ chuyển sang chấm; nút tắt + chấm bật: đích cố định chấm, không vẽ viên nang/kính/nhãn; cả hai tắt: không vẽ), gọi lại `applyGlass()` và `invalidate()` khi cờ đổi, không animate khi cờ đổi (`animated = false`). `isClickable`, `onInterceptTouchEvent`, `PressScale.enabled`, lớp nhấn và `contentDescription`/`AccessibilityDelegate` theo "Cổng chạm" ở trên; `importantForAccessibility = NO` khi không vẽ gì. `setDotsMode` giữ nghĩa "đang hoạt động" nên `HomePagerController.requestPillMode` **không phải sửa**. Chấm trần: thêm vòng tối mờ dưới mỗi chấm nếu ảnh nền sáng làm chấm mất (quyết lúc kiểm, ghi vào Nhật ký).
- [x] **Step 2: `HomeTogglesController.setup()`**: `scope.launch { repository.toggles.collect { binding.searchPill.setOptions(it.searchPill, it.pageDots) } }` (không `repeatOnLifecycle`, lý do ở mục thiết kế). `HomeActivity`: `@Inject lateinit var homeToggles: HomeTogglesRepository`; dựng controller sau `search`; gọi `setup()` cạnh `pager.setup()`. Đặt đúng thứ tự (thứ tự dựng là hành vi, R1).
- [ ] **Step 3 (chỉ nếu Oppo/Live 4 cho thấy ảnh chụp cũ):** `SearchPillView.onWindowVisibilityChanged(VISIBLE)` → `scheduleCapture()` nếu `realActive`, cho ca đổi công tắc khi Home khuất. Ghi lý do vào Nhật ký; không thêm nếu không cần.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — (a) mặc định, hồi quy M8c/PL: đứng yên pill, vuốt trang thì chấm mờ dần theo ngón, 600 ms sau nở lại pill, edit mode/kéo icon sang trang tạm thì chấm; (b) nút BẬT chấm TẮT: lướt/edit mode/kéo: pill vẫn "Tìm kiếm", không chuyển chấm, edit mode chạm pill không mở tìm kiếm; (c) nút TẮT chấm BẬT: chấm trần luôn hiện, đúng trang khi vuốt, PL fade, edit mode/kéo trang tạm đúng số chấm, chạm dải đó không phản ứng, thử trên ảnh nền sáng và tối (chụp ảnh); (d) cả hai TẮT: dải trống, icon và dock **không dịch** (so ảnh chụp với tổ hợp mặc định, cùng trang); (e) đổi mỗi công tắc ở Settings rồi Back: Home đổi ngay, không thấy `recreate` (đếm `onCreate` qua log tạm), cả khi đổi lúc đang ở trang 3; (f) mức kính Tùy chỉnh + pill kính thật: tắt nút tìm kiếm, đổi ảnh nền, bật lại → khớp nền; mờ tĩnh mặc định cũng khớp; (g) 1 trang và nhiều trang (≥ 7); (h) TalkBack không cần kiểm (ghi "chưa kiểm").

### Task 4: Cổng cử chỉ "Vuốt xuống để tìm kiếm"

**Bắt buộc kiểm tay:** không (điều phối bỏ dấu 2026-11-13: chỉ thêm một điều kiện vào cử chỉ đã có, không phải cử chỉ mới theo mục 4.2). Rủi ro "tắt rồi vẫn mở / bật mà bị chặn / lệch vuốt Library" được bài kiểm cuối bao quát; kịch bản dưới vẫn chạy.
**Files:** `ui/home/controller/SearchOverlayController.kt`, `ui/home/controller/HomeTogglesController.kt`.
**Interfaces:** Produces `SearchOverlayController.swipeDownEnabled: Boolean` (mặc định `true`).
- [x] **Step 1:** thêm `@Volatile var swipeDownEnabled = true`; trong `onTouch` thêm `swipeDownEnabled &&` vào `enabled` (một điều kiện, không đổi thứ tự hay cờ nuốt `searchTouchSwallowed`). `HomeTogglesController` đặt `search.swipeDownEnabled = it.swipeDownSearch`.
- [ ] **Step 2: Kịch bản kiểm (Live 4, bản debug; chủ dự án kiểm tay)** — (a) BẬT (mặc định): vuốt xuống ở giữa màn hình mở lớp tìm kiếm đúng một lần, như trước; (b) TẮT: vuốt xuống ở trang 1, ở giữa, sát dock, trên widget, 10 lần mỗi chỗ: không mở; pager không xê dịch; (c) TẮT rồi vuốt từ mép trên (thanh trạng thái): vẫn kéo thông báo hệ thống; (d) TẮT: vuốt ngang chuyển trang, trang 1 → … → Library (4 cú nhanh), Library → trang 1 (LB), nhấn giữ + kéo icon, "−", menu nhấn giữ, mở thư mục, edit mode: như trước; (e) bật lại ở Settings rồi Back: vuốt xuống mở lại ngay, không khởi động lại; (f) pill vẫn mở tìm kiếm khi chỉ vuốt xuống TẮT; Library vẫn mở lớp tìm kiếm bằng thanh của nó.

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (mặc định, kính Ép cao); Oppo A3x **chỉ** cho ca kính thật của pill (mục 6; xác nhận máy rảnh trước khi cài). **Bản cài:** nhánh `feature/ht-home-toggles` tại commit ghi ở "Kết quả" (ghi md5 APK). **Dữ liệu:** ≥ 4 trang Home (một trang có widget), một ảnh nền sáng và một tối, cài mặc định rồi cài đè.

1. **Mặc định và Cài đặt (Task 2).** Hub → "Lướt màn hình": chỉ hàng "Hiện chấm trang", bật. Hub → "Cài đặt tìm kiếm": nhóm "Cách mở" (hai công tắc bật) rồi nhóm "Tìm trong" với các hàng cũ, hai khối bo góc đúng. Gạt từng công tắc, Back, vào lại: giữ giá trị; `am force-stop` rồi mở lại: giữ giá trị. Hồi quy: hàng danh bạ (gợi ý quyền), "Xóa lịch sử" mở hộp thoại, tắt "Gợi ý" vẫn hỏi xóa.
2. **Mặc định trên Home.** Mọi công tắc bật: pill "Tìm kiếm" đứng yên, chấm mờ dần theo ngón khi vuốt trang, nở lại ~600 ms sau, edit mode/kéo icon sang trang tạm hiện chấm; vuốt xuống mở tìm kiếm đúng một lần; bấm pill mở tìm kiếm.
3. **Bốn tổ hợp pill × chấm, áp ngay sau Back (Task 3).** Với mỗi tổ hợp, gạt ở Cài đặt rồi Back (không khởi động lại): (a) nút BẬT + chấm TẮT: pill luôn "Tìm kiếm", không chuyển chấm khi lướt/edit/kéo, edit mode chạm pill không mở tìm kiếm; (b) nút TẮT + chấm BẬT: chấm trần luôn hiện, đúng trang, mờ dần theo ngón, số chấm đúng khi có trang tạm, 1 trang và ≥ 7 trang, chạm dải đó không phản ứng; chụp ảnh trên nền sáng và tối để quyết `BARE_DOT_HALO`; (c) cả hai TẮT: dải trống; **so ảnh chụp cùng trang với tổ hợp mặc định: icon, dock không dịch**; (d) đổi công tắc khi đang ở trang 3: đổi ngay, không về trang 1, không nháy; số lần `onCreate` của Home không tăng.
4. **Vuốt xuống (Task 4).** BẬT: như trước. TẮT: vuốt xuống ở trang 1, giữa màn hình, sát Dock, trên widget (mỗi chỗ 10 lần): không mở, pager không xê dịch; từ mép trên (thanh trạng thái): vẫn kéo thông báo hệ thống; bật lại ở Cài đặt rồi Back: mở lại ngay. Chỉ vuốt xuống TẮT: pill vẫn mở tìm kiếm; Library vẫn mở lớp tìm kiếm bằng thanh của nó. Cả hai lối mở TẮT: Home không có đường mở tìm kiếm, Cài đặt vẫn vào được qua edit mode.
5. **Hồi quy cử chỉ (cùng bản cài, công tắc vuốt xuống TẮT rồi BẬT).** Vuốt ngang chuyển trang; trang 1 → … → Library bằng 4 cú vuốt nhanh và Library → trang 1 (FX7/LB); nhấn giữ + kéo icon, dấu "−", menu nhấn giữ, mở thư mục, edit mode; kéo icon sang trang tạm.
6. **Kính thật của pill.** Live 4: mức kính Tùy chỉnh + pill kính thật: tắt nút tìm kiếm, đổi ảnh nền, bật lại: ảnh chụp pill khớp nền (cách đo ở knowledgement DG mục 7); mờ tĩnh mặc định cũng khớp. **Oppo (một lượt ngắn):** lặp đúng ca đó trên đường Lens.
7. **Hồi quy chung.** Đổi mức kính (dựng lại Activity) giữ đúng công tắc; xoay/đổi cỡ cửa sổ không đổi công tắc; cài/gỡ app khi Home đang hiện không làm pill co nhầm.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):**

- **Live 4 một phần, 2026-11-13, điều phối (coordinator),** APK debug build từ code `ff8e773`/`7e7d6aa` (code app không đổi ở `7bf1c64`), md5 `ec9f379e`, kính FORCE_HIGH, dữ liệu thật của chủ dự án (đã sao lưu và khôi phục sau đó), ảnh nền đỏ/tối nhiều chi tiết của chủ dự án. Nhãn: **đo được trên máy** trừ khi ghi khác.
  - **Mục 1 (Task 2 Step 4, phần Cài đặt):** mục "Lướt màn hình" bật được; màn chỉ có "Hiện chấm trang" (bật) kèm mô tả; "Cài đặt tìm kiếm" có nhóm "Cách mở" (hai công tắc, đều bật) và nhóm "Tìm trong" với các hàng cũ; giá trị giữ qua các màn.
  - **Mục 3 (Task 3, bốn tổ hợp, áp khi quay về Home không khởi động lại):** nút TẮT + chấm BẬT: chấm trần luôn hiện, trợ năng "Trang 1 trên 4", vẫn đọc rõ trên ảnh nền đỏ/tối nhiều chi tiết; cả hai TẮT: không vẽ gì, chạm dải đó không mở tìm kiếm; nút BẬT + chấm TẮT: ảnh chụp giữa lúc vuốt (đang thấy trang 1 và 2) vẫn là pill "Tìm kiếm", không chuyển chấm. **Bố cục:** bounds của uiautomator cho mọi ô Home, pager và dock **giống hệt** giữa cả-bật, nút-tắt và cả-hai-tắt (`diff` rỗng).
  - **Mục 4 (Task 4, một phần):** vuốt xuống TẮT: vuốt xuống giữa màn hình không làm gì (không có lớp tìm kiếm), bấm pill vẫn mở tìm kiếm; BẬT lại: vuốt xuống mở tìm kiếm; Back hai lần đóng tìm kiếm (lần đầu chỉ hạ bàn phím, hành vi cũ).
  - **Mục 5 (hồi quy, một phần):** vuốt trái 4 lần vào App Library rồi vuốt phải 4 lần về trang 1 chạy đúng. Logcat không có crash.
  - **Quyết định chấm trần:** giữ `BARE_DOT_HALO = true` (vòng tối nhẹ, chấm vẫn đọc tốt trên ảnh nền này).
- **Chưa kiểm:** ca ảnh nền sáng (chấm trần); kính thật của pill trên **Oppo** (mục 6, cả Live 4 Tùy chỉnh); kéo icon, edit mode, mở thư mục khi công tắc TẮT (mục 3a/3d, 5); hồi quy hàng danh bạ/"Xóa lịch sử" và `am force-stop` giữ giá trị (mục 1); vuốt xuống TẮT ở các điểm bắt đầu khác (sát Dock, trên widget) và từ mép trên (mục 4); cả hai lối mở tìm kiếm TẮT; 1 trang và ≥ 7 trang; số lần `onCreate` (mục 3d). Các mục này nằm trong bài kiểm tay của chủ dự án.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-13 | — | Viết plan | d5660e1 | Câu hỏi 1-5 ở cuối plan |
| 2026-11-13 | — | Điều phối duyệt plan; chủ dự án bỏ lướt vòng; bỏ dấu kiểm tay Task 4 | — | Plan sửa: bỏ hàng và Task lướt vòng |
| 2026-11-13 | 1 | Kho HomeToggles (domain, data, DI) + HomeTogglesViewModel; `assembleDebug` xanh (5 phút 27 giây, build sạch đầu tiên trong worktree) | 99d9f47 | Qua review điều phối 2026-11-13 (vòng 1/5), đã tick |
| 2026-11-13 | 2 | SwipeSettingsScreen (chỉ "Hiện chấm trang") đăng ký ở ô 3.1; SearchSettingsScreen thêm nhóm "Cách mở" (hai công tắc) + Header "Tìm trong"; ID và khối chuỗi `ht_`; `assembleDebug` xanh (2 phút 50 giây). Step 4 (kịch bản Live 4) chưa chạy: không cài APK | 70e5852 | Qua review điều phối 2026-11-13 (vòng 1/5), đã tick. Step 4 (Live 4) do điều phối chạy khi phone rảnh |
| 2026-11-13 | 3 | `SearchPillView.setOptions` (bốn tổ hợp, cổng bấm, a11y, chấm trần + vòng tối `BARE_DOT_HALO` mặc định BẬT, chờ quyết khi kiểm nền sáng), `HomeTogglesController`, `HomeActivity` +4 dòng; `assembleDebug` xanh (4 phút 34 giây). Step 3 (onWindowVisibilityChanged) không thêm: chưa có bằng chứng ảnh chụp cũ. Step 4 chưa chạy: không cài APK | 577568b | Qua review điều phối 2026-11-13 (vòng 1/5), đã tick |
| 2026-11-13 | 4 | `SearchOverlayController.swipeDownEnabled` (một điều kiện `&&` vào `enabled`, không đổi thứ tự hay cờ nuốt); `HomeTogglesController` nhận `search` và đặt cờ; `HomeActivity` đổi đúng một dòng (thêm đối số `search`); `assembleDebug` xanh (3 phút 46 giây). Step 2 (kịch bản Live 4) do điều phối chạy cùng Task 2-3 | ff8e773 | Qua review điều phối 2026-11-13 (vòng 1/5), đã tick |
| 2026-11-13 | Đóng | Viết knowledgement [HT-home-toggles.md](../knowledgements/HT-home-toggles.md); điền bằng chứng Review Focus (đọc code; ca cần máy ghi "Chờ máy"), bài kiểm cả tính năng, đề xuất tài liệu chung. Không rebase | (commit này) | Chờ kết quả Live 4 của điều phối để điền "Kết quả kiểm chứng"; Oppo chờ máy rảnh |
| 2026-11-13 | Kiểm | Live 4 do điều phối chạy (APK md5 `ec9f379e`, code `7e7d6aa`): hai màn Cài đặt hiển thị đúng; ba tổ hợp pill/chấm đúng, bố cục giống hệt (`diff` bounds rỗng); vuốt xuống TẮT/BẬT đúng, pill vẫn mở tìm kiếm; Library 4 vào/4 ra đúng; không crash. Giữ `BARE_DOT_HALO = true` | (commit này) | Chưa kiểm: nền sáng, Oppo kính thật, kéo icon/edit mode/thư mục khi TẮT, các điểm vuốt khác, hồi quy hàng danh bạ; chờ bài kiểm tay của chủ dự án |

## Câu hỏi cho chủ dự án (đã trả lời 2026-11-13)

1. Lướt vòng: **bỏ** (A). 2. Nút tắt + chấm bật: chấm trần luôn hiện. 3. Vuốt xuống tắt: không làm gì. 4. Tắt cả hai lối mở tìm kiếm: cho phép, không cảnh báo. 5. Tắt cả pill và chấm: giữ dải 48dp. (Chi tiết ở "Quyết định đã chốt" HT8-HT10; hệ quả kỹ thuật ở các mục thiết kế phía trên.)

## Bằng chứng Review Focus (điền khi đóng; tính tới Live 4 2026-11-13, code app `7e7d6aa`)

Nhãn: **đọc code** = chỉ đọc diff, **đo được** = lệnh/log trên máy, **chưa kiểm** = chưa ai chạy (vào bài kiểm tay của chủ dự án). Live 4 do điều phối chạy 2026-11-13 trên APK md5 `ec9f379e` (code app `7e7d6aa`).

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Mặc định = hành vi cũ | Đọc code: `HomeToggles` mặc định `true` cả ba, khóa vắng = mặc định; `swipeDownEnabled = true`; `setOptions` (bật, bật) cho `morphTarget` đúng giá trị cũ. Đo được (Live 4, 2026-11-13): trạng thái cả-bật là mốc so bố cục, Home như cũ | Đạt trên Live 4 bản cài đè; cài mới/xóa dữ liệu: **chưa kiểm** |
| 2 | Áp không khởi động lại | Đọc code: không có `recreate()`; thu bằng `lifecycleScope` không gắn STARTED. Đo được (Live 4): gạt ở Cài đặt, về Home thì đổi ngay ở cả bốn tổ hợp đã thử | Đạt; đếm `onCreate` và "không nháy": **chưa kiểm** riêng |
| 3 | Bốn tổ hợp đúng bảng | Đọc code: `morphTarget`, `searchClickable`, cổng `showSearch`/`showDots` khi vẽ. Đo được (Live 4): nút-tắt+chấm-bật (chấm trần luôn hiện, a11y "Trang 1 trên 4", đọc rõ trên nền đỏ/tối), cả-hai-tắt (không vẽ, chạm không mở tìm kiếm), nút-bật+chấm-tắt (ảnh chụp giữa vuốt vẫn "Tìm kiếm") | Đạt ba tổ hợp; **chưa kiểm:** edit mode, kéo, trang tạm, 1 và ≥ 7 trang, nền sáng, PL khi nút tắt |
| 4 | Layout không đổi | Đọc code: `setOptions` không `requestLayout`. Đo được (Live 4): bounds uiautomator của mọi ô Home, pager và dock giống hệt giữa cả-bật, nút-tắt, cả-hai-tắt (`diff` rỗng) | Đạt |
| 5 | Kính pill | Đọc code: tắt nút đi nhánh FLAT sẵn có; `setRealActive`/`scheduleCapture`/`captureReal`/`placeGlass` không có trong diff | **Chưa kiểm** (cần Live 4 Tùy chỉnh và **Oppo**) |
| 6 | Vuốt xuống tắt | Đọc code: một điều kiện `&&` ở `enabled`, router và cờ nuốt không đổi. Đo được (Live 4): TẮT: vuốt giữa màn hình không mở, pill vẫn mở tìm kiếm; BẬT lại: mở; Library 4 vuốt vào/ra đúng | Đạt một phần; **chưa kiểm:** các điểm bắt đầu khác, mép trên, kéo icon, menu, edit mode, thư mục |
| 7 | Cả hai lối mở tắt | Đọc code: Library vẫn gọi `openSearchShowAll`; Cài đặt mở từ edit mode (không phụ thuộc công tắc) | **Chưa kiểm** |
| 8 | Hồi quy Cài đặt tìm kiếm | Đọc code: `SearchSettingsBlock` không đổi. Đo được (Live 4): hai nhóm "Cách mở"/"Tìm trong" hiện đúng, các hàng cũ còn, giá trị giữ qua các màn | Đạt phần hiển thị; hàng danh bạ (gợi ý quyền), "Xóa lịch sử", force-stop: **chưa kiểm** |
| 9 | Không sửa ngoài vùng | Đo được: `git diff origin/main --stat` chỉ 16 file code/tài nguyên trong bảng Vùng file (+ plan); `HomeActivity` 6 dòng thêm, 0 đổi/xóa; không đụng `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `core/glass/*`, `SettingsKeys`, manifest | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#9 (tìm kiếm) thêm "Cách mở có công tắc: vuốt xuống, nút trên màn hình (HT)"; ghi chú chấm trang (màn 3.1) có công tắc "Hiện chấm trang"; "Lướt vòng" của bản nháp **không làm** (chủ dự án, 2026-11-13, xung đột với cử chỉ mở Library).
- `project_structure.md`:
  - Cây `domain/model/`: `HomeToggles.kt`; `domain/repository/`: `HomeTogglesRepository.kt`; `data/settings/`: `HomeTogglesRepositoryImpl.kt`, `HomeTogglesKeys.kt`; `di/`: `HomeTogglesModule.kt`.
  - `ui/settings/`: `HomeTogglesViewModel.kt`, `SwipeSettingsScreen.kt` (ô `3.1 SWIPE` đã đăng ký; PT thêm hiệu ứng trang bằng cách sửa lớp này).
  - `ui/home/controller/`: `HomeTogglesController.kt` (thu flow, đặt `searchPill.setOptions` và `SearchOverlayController.swipeDownEnabled`).
  - Dòng M8c/`SearchPillView`: thêm `setOptions` và bốn tổ hợp; khóa DataStore `home_toggle_swipe_down_search`, `home_toggle_search_pill`, `home_toggle_page_dots` (boolean, vắng = bật).
  - Bảng mốc mục 9: dòng HT.
- `MultiAgent-Workflow.md` mục 9/10: dòng HT (nhánh, PR #52, bỏ lướt vòng); `Figma-Draft.md` mục 6: HT chỉ còn 3 công tắc.
- `room-schema.md`: không đổi (không Room).
- `AGENTS.md` / `lessons/`: xem mục 8 của [knowledgement](../knowledgements/HT-home-toggles.md) (chủ đề `settings-and-data`, `home-pager-animation`, `search`, `glass`).
