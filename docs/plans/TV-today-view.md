# TV — Màn widget bên trái trang 1 (kiểu "Today View") — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Kiểm chứng tay trên Oppo PKD130 (Android 14); Samsung SM-A176B khi chủ dự án cho phép. Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-21 · **Plan duyệt:** 2026-11-21 (Q1–Q8 theo khuyến nghị)
**Trạng thái:** Chờ kiểm tay Task 3 + 4
**Máy:** Oppo PKD130 (có kính + cử chỉ mới) · **Đổi schema Room:** không (dùng giá trị `container` mới, xem D2)

**Goal:** Ở trang 1 của Home, vuốt sang phải (nội dung đi sang phải, như lật về "trang 0") mở một màn widget dọc kiểu iOS: ô tìm kiếm ở trên, danh sách widget cuộn dọc, nút "Sửa" ở cuối. Vuốt trái (hoặc Back/Home) đóng, về trang 1.

**Architecture:** Lớp phủ ngoài pager, điều khiển bằng một `progress` 0..1 — đúng mẫu App Library (bài học G2a B2 trong `lessons/home-pager-animation.md`: màn không phải lưới thì đừng làm một trang của pager). Cử chỉ chạy ở `HomeActivity.dispatchTouchEvent` như `LibraryOverlayController`, nhưng ngược chiều và chỉ khi đứng yên ở trang thật đầu tiên. Widget của màn lưu trong bảng `launcher_items` với `container = TODAY (-102)`, thứ tự theo `rank`; vẽ lại bằng `WidgetRenderer`/`HomeAppWidgetCell` đang có.

**Tech Stack:** Không thêm dependency.
**Spec:** Chưa có màn này trong `Figma-Draft.md` v3 (theo quy ước "màn nào thiết kế chưa có thì chờ, không tự vẽ" → cần chủ dự án quyết, câu hỏi Q1). Tham chiếu hành vi: Today View của iOS.

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** thêm một "trang" bên trái trang 1 mà không phá kéo-thả, trang tạm (ghost page), chỉ báo trang, `HomePagerInfo.currentPage` và cử chỉ App Library đang dựa vào chỉ số trang của `HorizontalPager`.
- **Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Trang 0 của `HorizontalPager` | Chèn một trang đặc biệt trước `pages` | Vuốt tự nhiên, ít code cử chỉ | Lệch chỉ số trang ở mọi chỗ (`HomeDragState`, ghost page, `pageScrollRequests`, chấm trang, `settledOnLastPage`); kéo icon tự lật sang trang widget; đúng cái bẫy G2a B2 đã ghi | Không |
| B. Lớp phủ + `progress` (mẫu App Library) | Controller cử chỉ riêng ở Activity, Home trượt sang phải, màn widget trượt vào từ trái | Không đụng pager và kéo-thả; đã có mẫu chạy ổn (`LibraryOverlayController`, spring ngắt được) | Thêm một controller cử chỉ thứ hai, phải phân xử với Library và pager | **Chọn** |

- **Vì sao chọn:** bài học G2a B2 (đo được trên máy, phải vá nhiều lần với cách A) và JH3c đã có đường cử chỉ ngoài Compose chạy được.
- **Đánh đổi chấp nhận:** hoạt ảnh mở màn widget đi theo spring của App Library, không theo hiệu ứng lật trang của pager (khi có PT/6 hiệu ứng trang thì màn widget không theo các hiệu ứng đó).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Lớp phủ ngoài pager (phương án B) | Claude đề xuất, chờ duyệt |
| D2 | Widget lưu ở `launcher_items`, `container = -102` (`ContainerId.TODAY` + `LauncherItemEntity.CONTAINER_TODAY`, giữ cùng giá trị), `pageId = 0`, `gridX/gridY = 0`, thứ tự theo `rank`, `spanX/spanY` giữ cỡ widget | Không đổi schema; cập nhật `room-schema.md` bảng giá trị `container` |
| D3 | Tái dùng `controller` cử chỉ: tách phần chung của `LibraryOverlayController` thành tham số chiều (mở bằng vuốt trái / vuốt phải) thay vì chép | Giữ hành vi Library y nguyên; Task 2 kiểm hồi quy Library |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Figma v3 chưa có màn này. Tự dựng theo kiểu iOS bằng DS v3 có sẵn, hay chờ thiết kế? | Tự dựng: nền Home mờ + tối, card kính `CARD` cho mỗi widget, ô tìm `DsSearchField` ở trên, nút "Sửa" pill ở cuối | |
| Q2 | Widget nào cho vào màn này? | v1: 4 widget tự vẽ (đồng hồ, lịch, thời tiết, ảnh) + widget app khác (AppWidgetHost). Cỡ: chỉ cỡ ngang hết bề rộng (4×2) và 2×2 xếp đôi theo hàng | |
| Q3 | Lần đầu mở (chưa có widget) hiện gì? | Tạo sẵn Thời tiết 4×2 + Lịch 4×2 (như iOS có sẵn widget) | |
| Q4 | Sửa màn này làm tới đâu ở v1? | Nút "Sửa" → dấu "−" để gỡ, nút "+" mở bảng chọn widget đang có (`WidgetPickerHost`). **Sắp xếp lại bằng kéo để v2** | |
| Q5 | Ô tìm kiếm ở đầu màn? | Có, bấm mở lớp tìm kiếm đang có | |
| Q6 | Có công tắc tắt màn này trong Cài đặt → Lướt màn hình không? | Có, mặc định bật (thêm khóa config, sửa `config-schema.md` cùng commit) | |
| Q7 | Chấm trang có thêm chấm cho màn này không? | Không (iOS không có) | |
| Q8 | Nền màn: mờ ảnh nền như iOS? | Mờ + tối trên API 31+, chỉ tối dưới API 31 (như menu/thư mục hiện nay) | |

## Ngoài phạm vi / ghi để sau

- Kéo để sắp xếp widget, kéo widget giữa màn này và trang Home.
- Smart Stack (xếp chồng, vuốt dọc trong một ô), gợi ý Siri, đổi cỡ tại chỗ.
- Màn widget trên màn khóa.

## Vùng file

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `domain/model/ContainerId.kt` | Sửa | `TODAY = -102L` |
| `data/layout/db/LauncherItemEntity.kt` | Sửa | `CONTAINER_TODAY = -102L` |
| `data/layout/*` (DAO, repository, reconciler) | Sửa | Nạp/ghi/xóa widget của container TODAY; không để reconciler coi là hàng mồ côi |
| `domain/repository/LayoutRepository.kt` + use case thêm/gỡ widget | Sửa | Đích "màn Today" |
| `ui/home/HomeUiState.kt`, `HomeViewModel.kt` | Sửa | `todayWidgets`, `todayOpen`, sửa/gỡ/thêm |
| `ui/home/LibraryOverlayController.kt` | Sửa | Tham số chiều (D3) |
| `ui/home/HomeActivity.kt` | Sửa | Controller thứ hai trong `dispatchTouchEvent`, Back, nút Home đóng màn |
| `ui/home/compose/today/HomeToday.kt` | Tạo | Màn widget Compose |
| `ui/home/compose/HomeScreen.kt` | Sửa | Lớp Home trượt sang phải theo `todayProgress`; `HomePagerInfo.settledOnFirstPage` |
| `ui/settings/SwipeSettingsScreen.kt` + config | Sửa | Công tắc (nếu Q6 = có) |
| `res/values*/strings.xml` (9 file) | Sửa | "Sửa", "Xong", "Thêm tiện ích", chuỗi rỗng |
| `docs/room-schema.md`, `docs/config-schema.md`, `docs/Launcher-Features.md` | Sửa | Bước 7 |

## Global Constraints

- Luật phụ thuộc `ui → domain ← data`; `ContainerId` và hằng số entity giữ cùng giá trị.
- Kính thật chỉ cho `CARD`/`SHEET` (JD D9); màn có nhiều widget thì đo Janky lúc cuộn trên Oppo.
- Chuỗi mới đủ 9 ngôn ngữ, kiểm bằng `gradlew.bat lintDebug`.

## Review Focus

- Phân xử cử chỉ: trang 1 vuốt phải mở Today, không còn "kéo dãn" của pager; trang cuối vuốt trái vẫn mở Library; chỉ có 1 trang thì cả hai cùng hoạt động.
- Đang kéo icon, edit mode, menu/thư mục mở, tìm kiếm mở: không mở được màn Today.
- Hàng `container = -102` không bị `LayoutReconciler`/`PruneEmptyPages` xóa nhầm.

---

### Task 1: Dữ liệu widget của màn Today
**Bắt buộc kiểm tay:** không
**Files:** `ContainerId.kt`, `LauncherItemEntity.kt`, `data/layout/*`, `LayoutRepository.kt`, `HomeUiState.kt`, `HomeViewModel.kt`
- [ ] Step 1: Hằng `TODAY`; DAO đọc theo `container = -102` sắp theo `rank`.
- [ ] Step 2: Repository: `observeTodayWidgets()`, `addTodayWidget(kind/appWidgetId, span)`, `removeTodayWidget(id)` (giải phóng appWidgetId như đường gỡ hiện có).
- [ ] Step 3: Tạo sẵn widget mặc định lần đầu (Q3) bằng cờ config.
- [ ] Step 4: `HomeUiState.todayWidgets`; build xanh.

### Task 2: Cử chỉ mở/đóng
**Bắt buộc kiểm tay:** có — cử chỉ chạm mới (`Workflow.md` mục 3)
**Files:** `LibraryOverlayController.kt`, `HomeActivity.kt`, `HomeScreen.kt`
- [ ] Step 1: Tham số chiều cho controller; Library giữ nguyên hành vi.
- [ ] Step 2: `HomePagerInfo.settledOnFirstPage`; controller Today chỉ mở khi đứng yên ở trang 0, không edit mode/kéo/menu/thư mục/tìm kiếm.
- [ ] Step 3: Lớp Home trượt sang phải theo `todayProgress`; Back và nút Home đóng.
- [ ] Step 4: Kịch bản kiểm: mở/đóng chậm, nhanh (fling), ngắt giữa spring; Library ở trang cuối vẫn chạy; Home 1 trang.

### Task 3: Giao diện màn Today
**Bắt buộc kiểm tay:** không
**Files:** `ui/home/compose/today/HomeToday.kt`, `HomeScreen.kt`
- [ ] Step 1: Nền mờ/tối theo `progress` (Q8), inset trên/dưới.
- [ ] Step 2: Ô tìm kiếm (Q5) → mở tìm kiếm.
- [ ] Step 3: Danh sách cuộn dọc: 4×2 hết bề rộng, 2×2 xếp đôi; vẽ bằng `WidgetRenderer` / `HomeAppWidgetCell`; bấm widget như ngoài Home.
- [ ] Step 4: Kịch bản kiểm: cuộn dọc không mở/đóng màn; đo Janky cuộn trên Oppo.

### Task 4: Sửa (gỡ, thêm) + công tắc
**Bắt buộc kiểm tay:** không
**Files:** `HomeToday.kt`, `HomeViewModel.kt`, `HomeActivity.kt`, `SwipeSettingsScreen.kt`, config, `strings.xml` ×9
- [ ] Step 1: Nút "Sửa"/"Xong"; dấu "−" hỏi xác nhận rồi gỡ.
- [ ] Step 2: Nút "+" mở `WidgetPickerHost` với đích Today; widget app đi đủ đường cấp id → cấu hình → đặt.
- [ ] Step 3: Công tắc (Q6) và chuỗi 9 ngôn ngữ; `lintDebug`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
(điền khi xong)

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-21 | — | Viết plan | Chủ dự án duyệt, Q1–Q8 theo khuyến nghị |
| 2026-11-21 | 1 | `ContainerId.TODAY`/`CONTAINER_TODAY = -102`; cờ `today_seeded` (`AppState`); `LayoutReconciler.seedTodayIfNeeded`/`addTodayWidget`/`addTodayAppWidget`; `LayoutRepository.todayWidgets` (ghép trong `assemble`, theo `rank`); `AddTodayWidgetUseCase`; `HomeUiState.todayWidgets`; sửa `config-schema.md`, `room-schema.md` | `assembleDebug` xanh. Gỡ widget dùng `removeItem`/`RemoveWidgetUseCase` sẵn có. Chưa có UI nên chưa thấy gì trên máy |
| 2026-11-21 | 1 | Chủ dự án xác nhận | |
| 2026-11-21 | 2 | `LibraryOverlayController(opensLeftward)`: dx/vx nhân `dir` về trục mở; `HomeTodayState` (mới, `ui/home/compose/today/`) dùng controller ngược chiều, mở khi đứng yên ở trang 0; `HomeUiState.todayOpen` + `setTodayOpen`; `HomeActivity.dispatchTouchEvent` hỏi Library rồi Today, bên tiếp quản gửi CANCEL giả cho bên kia; Back, phím Home đóng; vuốt xuống tìm kiếm tắt khi Today mở; `HomeScreen` đẩy lớp Home sang phải theo `todayProgress`; `HomePagerInfo.settledOnFirstPage`; `HomeLibraryState` thêm `isActive`/`isTodayActive` để hai bên không mở chồng | `assembleDebug` xanh. Ngoài Vùng file: `HomeTodayState.kt` (tách như `HomeLibraryState`), `HomeLibraryState.kt`. Chưa có màn Today (Task 3): mở hẳn chỉ thấy ảnh nền |
| 2026-11-21 | 2 | Chủ dự án kiểm tay: đạt | |
| 2026-11-21 | 3 | `HomeToday.kt` (mới): nền ảnh nền thu nhỏ làm mờ + lớp tối (cùng cách và độ tối với Library, mọi API, nên Q8 không cần nhánh API 31), ghi làm backdrop riêng; trang trượt vào từ trái; ô tìm kiếm mở tìm kiếm Spotlight; `LazyColumn` hàng widget: rộng ≥ 3 ô hết bề rộng, hẹp xếp đôi, cao theo `spanY` (đơn vị = (nửa cột − khe)/2); 4 widget đầu kính thật, sau đó bề mặt tĩnh; bấm widget đi `onWidgetClick` như Home; lớp phủ là đích chạm nên Home bên dưới không nhận chạm. `HomeLibrary.kt`: `shrinkForBlur` + 4 hằng `SCRIM_*` thành `internal` để dùng chung | `assembleDebug` xanh. Widget thời tiết tự gọi `WeatherWidgetHub.onVisible` từ renderer nên trên Today vẫn được làm mới. Chưa đo Janky cuộn (Step 4) |
| 2026-11-21 | 3 | Chạy thử trên Samsung SM-A176B (chủ dự án cho phép): Today hiện đúng (ô tìm kiếm, Thời tiết + Lịch 4×2), không crash | Không kiểm xong cử chỉ: Home vào edit mode giữa chừng (nhấn giữ có rung lúc 02:19:31; nghi chủ dự án chạm máy cùng lúc), rồi máy khóa. Chưa kết luận |
| 2026-11-21 | 4 | `HomeUiState.todayEditing`; VM `setTodayEditing`/`onAddTodayWidget`/`onAddTodayAppWidget`/`onRemoveTodayWidget` (gỡ lạc quan, dùng `RemoveWidgetUseCase`); `HomeToday`: nút "Sửa" cuối danh sách, chế độ sửa có rung, dấu "−" (hỏi xác nhận, hộp chung với lưới), lớp nuốt chạm trên widget, nút "Tiện ích" + "Xong"; `HomeWidgetPicker.show(toToday)` + `AppWidgetAddRequest.toToday` (luồng widget app ghi vào Today); Back thoát chế độ sửa trước; đóng màn thì thoát sửa; công tắc `home_today_enabled` ở Cài đặt → Lướt màn hình; 3 chuỗi mới × 9 ngôn ngữ (dùng lại `home_done`, `home_add_widget`, `widget_remove_*`); `config-schema.md` | `assembleDebug` + `lintDebug` xanh. Ngoài Vùng file: `HomeWidgetPicker.kt`, `AppWidgetFlowState.kt`, `HomeTogglesViewModel.kt`, `SettingsRow.kt`, `HomeScreenConfigRepositoryImpl.kt`, `ConfigKeys.kt`. Bản dịch là máy dịch |
| 2026-11-27 | TA | Widget app khác trên Today (chủ dự án đổi Q2 ngày 2026-11-20): `TodayWidget` thêm `kindId = "app"` + `appWidgetId`/`provider` (kho `today_widgets` ghi 7 trường); bảng "+" của Today có danh sách app, mỗi provider trang 2 × 2 / 4 × 2 theo `AppWidgetSizePlanner` trên lưới Today; luồng gắn/cấu hình dùng `AppWidgetAddRequest.toToday`, ghi qua `LayoutReconciler.addTodayAppWidget` (cùng mutex với quét mồ côi); quét mồ côi và `resetHost` giữ id của Today; gỡ thì giải phóng id; chế độ sửa có lớp chặn chạm trên host view | `assembleDebug` xanh. Chưa kiểm tay trên máy. `testDebugUnitTest` đỏ sẵn từ trước (`GridMetricsTest` gọi `GridPreset.P5X6` đã bị comment) |
