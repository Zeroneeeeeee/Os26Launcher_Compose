# RR — Hồi phục hướng màn hình (TT#5) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (không cần Oppo: lỗi không thuộc kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `fix/rr-rotation-recovery` · **Worktree:** `E:\SourceTree\IosLauncher-wt\RR-rotation-recovery` · **PR:** #43 (draft) · **Bắt đầu:** 2026-11-12 · **Plan duyệt:** 2026-11-12 (điều phối, chủ dự án ủy quyền)
**Trạng thái:** Chờ review (Task 1, 2, 3, 5 xong và đã kiểm trên Live 4; chờ điều phối review và bài kiểm cuối của chủ dự án)
**Khóa:** không (không đổi schema, không cần Oppo)

**Goal:** Launcher luôn về **hướng tự nhiên của máy** (đứng trên điện thoại) và bố cục đúng, bất kể màn hình đang ở hướng nào lúc Home được dựng, được resume hay được gọi về bằng nút Home: từ game ngang, video ngang, Gần đây, khởi động nguội khi app ngang đang chạy, process bị giết khi app ngang đang chạy. Khi cửa sổ Home thật sự đổi cỡ (xoay trên máy lớn, gập/mở, đổi cỡ màn), mọi thứ phụ thuộc cỡ màn được dựng lại đúng một lần, sau khi cỡ đã ổn định.

**Architecture:** Hai phần độc lập. (1) **Yêu cầu hướng:** đổi `android:screenOrientation="locked"` thành `"nosensor"` trong manifest (không code). (2) **Đường hồi phục:** controller mới `ui/home/controller/WindowSizeRecovery.kt` nghe đổi cỡ `home_root` và `onConfigurationChanged`, chạy việc rẻ ngay, gom việc đắt (giải mã lại ảnh nền, dựng lại bản mờ) tới khi cỡ đứng yên ~250 ms; `HomeViewModel` nhận cỡ đã ổn định và giải mã lại ảnh nền nếu cỡ cửa sổ toàn màn khác cỡ ảnh đang giữ. Không đụng `domain`/`data`, không đụng `core/glass`, không đụng pager/cử chỉ.

**Tech Stack:** View/XML, API có sẵn (`Activity.onConfigurationChanged`, `isInMultiWindowMode`, `View.OnLayoutChangeListener`, `Handler.postDelayed`). Không thêm dependency.

**Spec:** TT#5 trong `Launcher-Features.md` ("Back/Home, insets, xoay màn hình, sáng/tối"); quyết định M5 "khóa theo hướng tự nhiên lúc khởi động, không `portrait` cứng" ([M5-orientation-theme.md](M5-orientation-theme.md) Task 1); `project_structure.md` mục 4 (vì sao `configChanges` có `orientation|screenSize`: tạo lại Activity bắt mọi kính chụp lại nền); lỗi điều phối tái hiện trên Live 4 ngày 2026-11-12 (mục "Lỗi" bên dưới).

## Lỗi (điều phối tái hiện, Vsmart Live 4, Android 11, 1080x2340, tự xoay BẬT, 2026-11-12)

Mở game ngang `com.example.baocaoltgamethu35/.MainActivity` → bấm Home → launcher ở **ngang** (screencap 2340x1080): ảnh nền kéo giãn, lưới/widget/dock bị ép thành dải ngang; `dumpsys` cho `mLastOrientationSource=HomeActivity`. Cách điều phối gỡ: `accelerometer_rotation 0` + `user_rotation 0` + `force-stop` + HOME, rồi bật lại tự xoay.

## Nguyên nhân

**1. `screenOrientation="locked"` ghim hướng đang có, không phải hướng tự nhiên** (đọc tài liệu + đọc nguồn AOSP theo trí nhớ, khớp với `dumpsys` của điều phối).
- Tài liệu `ActivityInfo.SCREEN_ORIENTATION_LOCKED`: "khóa hướng ở góc xoay hiện tại, bất kể là gì". Trong `DisplayRotation.rotationForOrientation` của AOSP, nhánh `LOCKED` trả `lastRotation` (góc xoay hiện tại của màn).
- Khi game ngang đang chạy, màn ở góc 90°. Bấm Home: `HomeActivity` thành nguồn quyết định hướng (`mLastOrientationSource=HomeActivity`), xin `LOCKED` → hệ thống giữ 90°. Vì vậy Home ở ngang, và ở ngang mãi tới khi có gì đó đổi góc xoay khi Home không phải nguồn quyết định. Đó cũng là lý do cách gỡ của điều phối cần `user_rotation 0` + `force-stop`.
- M5 đã chọn `locked` với mô tả "giữ đúng hướng hiện tại lúc Activity khởi động" — đúng khi Home luôn khởi động ở đứng, sai khi Home được gọi về lúc màn đang ngang. M5 Task 1 chỉ kiểm chiều ngược lại (mở Demo kính rồi về Home khi Home đang đứng).

**2. Không có đường hồi phục cho thứ tính MỘT lần theo cỡ màn** (đọc code 2026-11-12). Cần chỉnh một ý trong mô tả lỗi: thiếu `onConfigurationChanged` **không** có nghĩa là cây view không layout lại. Cửa sổ đổi cỡ thì `ViewRootImpl` vẫn đo và đặt lại toàn bộ cây view (bằng chứng: M5 Task 2/4 split-screen co lưới và dock đúng nhờ `OnLayoutChangeListener` và `IconGridView` bind lại khi `Cells` đổi). Thứ **không** được hồi phục là những thứ chỉ tính một lần:

| Thứ | Tính ở đâu | Vì sao cũ sau khi đổi cỡ |
|---|---|---|
| Bitmap ảnh nền | `HomeViewModel.onScreenSize` (chỉ lần layout đầu, cờ `screenSizeHandled`) → `PickedImageSource/DefaultBackdropSource.load(reqW, reqH)` cắt vùng giữa **theo tỉ lệ màn lúc đó** (`BitmapScaler.cropRegion`) | Khởi động nguội ở ngang → bitmap 2340x1080; về đứng thì `centerCrop` phóng một dải hẹp lên → mờ, sai bố cục ảnh. Không có đường nào giải mã lại theo cỡ mới |
| Bản mờ của kính tại chỗ | `BlurredBackdropCache` khóa theo (danh tính bitmap, cỡ view) | Đúng hình học (ánh xạ qua `imageMatrix`) nhưng là bản mờ của bitmap sai tỉ lệ ở trên |
| Kính thật của dock | `LiquidGlassView` chụp một lần (`enableDynamicBackground = false`), chụp lại khi ảnh nền đổi (`DockController.onBackdropChanged`) hoặc dock đổi cỡ | Dock không đổi cỡ (ví dụ cùng chiều rộng) mà ảnh nền dưới đã dịch thì giữ ảnh chụp cũ |
| `StaticBlurSurface` (mức CLASSIC) | `cropMatrix` chỉ xóa khi chính `drawView` đổi cỡ | `backdrop_image` đổi cỡ mà dock không đổi cỡ → vùng cắt lệch |
| Menu nhấn giữ icon | `IconMenuController` đặt vị trí theo icon và `homeRoot` **lúc mở** | Đổi cỡ khi menu mở → menu và icon nổi ở chỗ cũ |
| Lớp phủ App Library | `AppLibraryController.applyLibraryProgress` đặt `translationX = homeRoot.width * (1 - p)` mỗi lần tiến độ đổi | Giá trị cũ chỉ lộ ra nếu đổi cỡ đúng lúc đang trượt (p ở giữa); p = 0 thì view `GONE`, p = 1 thì `translationX = 0`, `home_content` `INVISIBLE`. **Không cần sửa** (xem "Vì sao") |

Những thứ đã tự hồi phục (không đụng): `Cells` của lưới (`IconGridView.onMeasure` + bind lại), `Cells` của dock (listener ở `HomeActivity`), `CategoryPageView`/`SearchPillView`/`IconCellView`/`WidgetPreviewView` (`onSizeChanged`), vùng mép kéo của `DragHelper` (listener ở `HomeTouchRouter`), inset (listener inset), bảng chọn widget và bảng thời tiết (`BottomSheetDialog` là cửa sổ riêng, tự đổi cỡ).

**Bố cục dữ liệu không phụ thuộc cỡ màn** (đọc code): `GridPreset` quyết số cột/hàng, các planner trong `domain/layout` chỉ dùng cột/hàng, `GridMetrics` chỉ tính pixel. Nên xoay/đổi cỡ **không bao giờ** được ghi DB hay reflow; Review Focus 6 giữ điều này.

## Vì sao chọn thiết kế này

### Vấn đề cần giải
- Điện thoại (Live 4, Oppo A3x): Home phải luôn đứng, kể cả khi bị gọi về lúc màn đang ngang và khi tự xoay đang tắt nhưng `user_rotation` là ngang.
- Không được hồi quy M5: split-screen co lưới/dock (M5 Task 2/4), đổi sáng/tối tạo lại Activity (M5 Review Focus 1), Home không xoay theo cảm biến khi người dùng cầm ngang máy ở Home.
- `targetSdk = 37` (`app/build.gradle.kts`): trên màn **sw ≥ 600dp** chạy Android 16+, hệ thống bỏ qua yêu cầu hướng cố định và không còn cách opt-out từ API 37 (đọc tài liệu "Android 16 behavior changes", mục "Ignore orientation, resizability, and aspect ratio restrictions"). Nên trên máy lớn, Home **sẽ** chạy ngang dù manifest nói gì; đường hồi phục là bắt buộc, không phải phụ.
- Giải mã ảnh nền và dựng bản mờ đắt (dựng bản mờ đo 78,8 ms trên Oppo cho 1224x1632 → 306x408, GL Task 8; làm nóng ~358 ms trên Live 4, FX8 — `lessons/glass.md`). Cỡ cửa sổ có thể đi qua vài giá trị tạm trong một lần chuyển cảnh → phải gom, không làm theo từng giá trị.
- File nóng độc quyền `HomeActivity.kt`, `HomeViewModel.kt`; nhánh song song `fix/lb-library-back-swipe` có thể đụng `HomeActivity` và các file pager/cử chỉ.

### Phần 1 — Yêu cầu hướng

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `nosensor`** (manifest) | `android:screenOrientation="nosensor"` | AOSP: `NOSENSOR` không theo cảm biến và **không** bị khóa xoay của người dùng áp lên (`user_rotation`), rơi về `ROTATION_0` = hướng tự nhiên của máy (đọc nguồn `DisplayRotation`, theo trí nhớ — Task 1 kiểm trên máy). Đúng ý M5 "hướng tự nhiên, không `portrait` cứng": máy tự nhiên ngang (một số tablet) thì ngang. Là cách Launcher3 của AOSP làm khi tắt "cho xoay Home" (đọc nguồn `RotationHelper.notifyChange` trên googlesource, 2026-11-12: nhánh cuối `activityFlags = SCREEN_ORIENTATION_NOSENSOR`). **Không** phải "hướng cố định" (`ActivityInfo.isFixedOrientation` chỉ gồm các giá trị portrait/landscape) nên không vào chế độ letterbox/size-compat ở split-screen | Phụ thuộc ROM tôn trọng đúng AOSP (Vsmart Android 11 chưa kiểm) → Task 1 bắt buộc kiểm tay | **Chọn** |
| B. `portrait` | `android:screenOrientation="portrait"` | Trên điện thoại cho kết quả như A | Trái quyết định M5. Là hướng cố định: Android 12+ có thể letterbox Activity hướng cố định trong vùng hiển thị ngang/multi-window (đọc tài liệu letterbox, chưa đo trên máy dự án) → rủi ro hồi quy split-screen của M5. Tablet tự nhiên ngang bị ép đứng (Android ≤ 15) | Không |
| C. `requestedOrientation` trong code | Đặt ở `onCreate`/`onResume`, có thể đổi theo máy (vd `UNSPECIFIED` khi là tablet như Launcher3) | Linh hoạt, cho tablet xoay tự do | Thêm code vào file nóng; giá trị manifest vẫn áp trước khi code chạy (khởi động nguội vẫn đi qua giá trị manifest); chỉ đáng làm nếu chủ dự án muốn tablet xoay (D8: bỏ) | Không (Task 4 bỏ) |
| D. Giữ `locked`, tự "chữa" ở `onResume` | Thấy màn ngang thì đổi tạm `requestedOrientation` để ép WM tính lại | — | Chữa triệu chứng của chính giá trị sai; nháy hai lần xoay; không có tài liệu bảo đảm | Không (chỉ là phương án lui nếu Task 1 cho thấy ROM bỏ qua `nosensor`) |
| E. Bỏ `orientation|screenSize` khỏi `configChanges` | Để Activity tự tạo lại khi xoay | Tự dựng lại mọi thứ | Không sửa được việc ghim hướng; tạo lại = mọi kính chụp lại nền (lý do của `project_structure.md` mục 4), mất trang hiện tại và lớp phủ đang mở (M5 Review Focus 2) | Không |

**Multi-window/split-screen:** yêu cầu hướng của Activity không áp khi ở multi-window (đọc tài liệu), nên A không đổi hành vi split-screen của M5. **Máy sw ≥ 600dp, Android 16+:** `nosensor` không nằm trong danh sách giá trị bị bỏ qua của tài liệu (danh sách chỉ có các giá trị portrait/landscape), nhưng OEM có thể bật "bỏ qua yêu cầu hướng" ở cấp vùng hiển thị (suy luận chưa kiểm chứng, dự án không có máy sw ≥ 600dp). Dù thế nào, đường hồi phục ở Phần 2 làm Home bố cục đúng ở ngang (lưới co theo `GridMetrics`, ảnh nền giải mã theo tỉ lệ ngang). Thiết kế riêng cho bố cục ngang (đổi số cột, dock dọc) **ngoài phạm vi**.

### Phần 2 — Đường hồi phục

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **F. Controller hồi phục có gom** | `WindowSizeRecovery`: nghe `home_root` đổi cỡ + `onConfigurationChanged`; việc rẻ (đóng menu nhấn giữ) chạy ngay; việc đắt chờ cỡ đứng yên `SETTLE_MS` (250 ms, chỉnh theo số đo Task 1) và Activity đang resumed, rồi chạy **một lần**: báo `HomeViewModel.onWindowSizeSettled` (giải mã lại ảnh nền nếu cần) + làm mới kính | Chỉ đụng thứ thật sự cũ; tái dùng đường "ảnh nền đổi" đã có (FX1/AN/DG) cho phần kính; cỡ tạm trong chuyển cảnh không gây việc đắt; split-screen kéo thanh chia không giải mã lại (xem dưới) | Thêm một khoảng ~250 ms + thời gian giải mã mà ảnh nền còn sai tỉ lệ (chỉ ở ca khởi động nguội lúc ngang hoặc xoay thật trên máy lớn) | **Chọn** |
| G. `recreate()` khi hướng đổi | `onConfigurationChanged` → `recreate()` (như `RecreateGuard`) | Rất ít code, chắc chắn sạch | Mọi kính chụp lại; mất trang hiện tại và lớp phủ; cỡ tạm → nhiều lần tạo lại; ViewModel sống sót nên **ảnh nền vẫn sai tỉ lệ** (vẫn phải sửa VM) | Không |
| H. Mỗi view tự lo trong `onSizeChanged` | Thêm hồi phục vào từng view/controller | Không cần chỗ trung tâm | Rải vào nhiều file nóng (`core/glass/*`, pager, Library) và chồng vùng file với LB; không có chỗ gom nên dễ làm lặp việc đắt | Không |

**Vì sao chọn F, chi tiết:**
- **Chỉ giải mã lại ảnh nền khi cỡ cửa sổ toàn màn khác cỡ ảnh đang giữ, và không ở multi-window.** Split-screen hôm nay không giải mã lại (ảnh toàn màn + `centerCrop` cho kết quả chấp nhận được, M5 đã kiểm); giữ nguyên để không hồi quy và không giải mã theo từng bước kéo thanh chia. Ra khỏi split-screen thì cỡ quay về đúng cỡ ảnh → không làm gì. So sánh với cỡ mà **ViewModel** đã dùng để giải mã (sống qua `recreate`), không với trạng thái riêng của controller: Activity mới dựng lại do đổi sáng/tối có lần layout đầu bằng đúng cỡ đó → không giải mã thừa (giữ M5 Review Focus 1 và FX1 B2).
- **Chống làm lặp việc đắt:** (1) gom bằng `postDelayed` hủy-đặt-lại trên `home_root`, chỉ chạy khi Activity resumed (Activity dừng thì không layout, `onResume` kiểm lại); (2) `HomeViewModel.launchBackdropLoad` đã hủy lần nạp trước, ảnh mới nhất thắng (FX1); (3) bản mờ chỉ dựng lại khi bitmap mới vào `backdrop_image`, theo đường `BackdropController.render` → `prewarm` → `BackdropListener` (dock chụp lại một lần, `GlassEnvironment.notifyBackdropChanged()` cho kính tại chỗ và kính panel) — đúng một lần mỗi lần đổi cỡ ổn định. Không đổi `core/glass`. Rủi ro còn lại: `StaticBlurSurface.computeCrop` xin bản mờ theo cỡ view mới cho bitmap cũ, nên một lần xoay có thể dựng thêm tối đa một bản mờ cho cỡ tạm (kết quả cũ bị bỏ ở `onBuilt`); chấp nhận, đo ở bài kiểm (mục 4) và chỉ sửa `core/glass` nếu thấy giật.
- **Không giải mã lại thì vẫn làm mới kính** (rẻ: chỉ `invalidate`/chụp lại một lần): kính dock chụp lại, kính tại chỗ vẽ lại, pill chụp lại.
- **App Library không cần sửa** (đọc code, bảng ở mục Nguyên nhân): giá trị `translationX` cũ không bao giờ lộ ra ở p = 0 hoặc p = 1, và hoạt ảnh tính lại theo `homeRoot.width` mỗi khung. Nhờ vậy RR **không** đụng `AppLibraryController`, `LibraryOverlayController`, pager hay cử chỉ (vùng của LB).
- **Menu nhấn giữ đóng khi cỡ đổi** (gọi `viewModel.closeIconMenu()`, cùng đường với Back/Home của HB) thay vì đặt lại vị trí: vị trí menu suy từ icon nguồn mà icon cũng vừa dịch; đóng là hành vi iOS khi xoay và không phải sửa `IconMenuController`.

### Đánh đổi chấp nhận
- Home không bao giờ xoay trên điện thoại (như M5). Ở khởi động nguội lúc màn đang ngang, có thể thấy ~0,3-0,6 s ảnh nền phóng sai tỉ lệ trước khi bản đúng về; kính có thể vẽ phẳng rồi hiện mờ dần (150 ms, FX8) như khi đổi ảnh nền.
- Đổi cỡ đúng lúc đang kéo icon: không xử lý riêng (`DragHelper` là file nóng; trên điện thoại chỉ xảy ra khi gập/mở hoặc đổi cỡ màn bằng lệnh). Ghi ở Ngoài phạm vi.
- Xét lại khi: dự án hỗ trợ tablet thật (cần bố cục ngang riêng), hoặc đo thấy bản mờ dựng lặp gây giật.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Manifest `screenOrientation="nosensor"` thay `locked` | Claude đề xuất 2026-11-12; cơ sở: AOSP `DisplayRotation` + Launcher3 `RotationHelper`; điều phối duyệt 2026-11-12 (chủ dự án ủy quyền); chờ Task 1 xác nhận trên Live 4 |
| D7 | `SettingsActivity` và `AppLibraryActivity` cũng `screenOrientation="nosensor"` (chỉ manifest) | Chủ dự án, 2026-11-12 (Q2). Làm ở Task 1 |
| D8 | Bỏ Task 4 (màn lớn sw ≥ 600dp): ngoài phạm vi, ghi là giới hạn đã biết | Chủ dự án, 2026-11-12 (Q1) |
| D9 | Chấp nhận ~0,3-0,6 s ảnh nền sai tỉ lệ ở ca khởi động nguội lúc ngang | Chủ dự án, 2026-11-12 (Q4) |
| D2 | Giữ `configChanges` như cũ (`orientation|screenSize|smallestScreenSize|screenLayout`): không tạo lại Activity khi xoay | Giữ quyết định `project_structure.md` mục 4 |
| D3 | Việc đắt chỉ chạy sau khi cỡ đứng yên `SETTLE_MS` (khởi điểm 250 ms) và Activity resumed | Claude đề xuất; số chốt sau phép đo Task 1 |
| D4 | Ảnh nền giải mã lại chỉ khi cỡ cửa sổ toàn màn ≠ cỡ đã giải mã, không ở multi-window | Claude đề xuất; giữ hành vi split-screen M5 |
| D5 | Menu nhấn giữ đóng khi cỡ đổi; các lớp phủ khác (thư mục, tìm kiếm, Library, edit mode, bảng widget/thời tiết) giữ nguyên, tự bố cục lại | Chủ dự án chốt 2026-11-12 (Q3): chỉ đóng menu nhấn giữ |
| D6 | Không viết unit test (quy ước dự án, `AGENTS.md` + `MultiAgent-Workflow.md` mục 11). Thay bằng kịch bản `adb` lặp có kiểm tự động trong bài kiểm (mục 1-3) | Chủ dự án chốt 2026-11-12 (Q5): theo quy ước, không test |

## Ngoài phạm vi / ghi để sau
- **Giới hạn đã biết — màn lớn (sw ≥ 600dp, D8):** không làm gì riêng. Trên Android 16+ hệ thống có thể bỏ qua yêu cầu hướng và Home chạy ngang; đường hồi phục vẫn giải mã ảnh nền đúng tỉ lệ và lưới co theo `GridMetrics`, nhưng inset trái/phải (thanh điều hướng bên cạnh) **không** được áp và không có bố cục ngang riêng (số cột, dock dọc, Library ngang). Làm khi dự án nhắm tablet.
- Đổi cỡ khi đang kéo icon (`DragHelper`), khi menu nhấn giữ đang kéo icon nổi.
- Chế độ cửa sổ tự do / desktop windowing.
- Xoay 180° (`nosensor` không bao giờ cho).

## Vùng file

| File | Tạo / Sửa | Nóng | Task |
|---|---|---|---|
| `app/src/main/AndroidManifest.xml` | Sửa (`nosensor` cho `HomeActivity`, `SettingsActivity`, `AppLibraryActivity` + chú thích) | Dùng chung có kiểm soát | 1 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt` | Sửa (~10 dòng: dựng controller, `onConfigurationChanged`, `onResume`) | **Độc quyền** — chồng khả năng với LB | 1, 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/WindowSizeRecovery.kt` | Tạo | Không | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/HomeViewModel.kt` | Sửa (một hàm mới + hai trường cỡ đã giải mã) | **Độc quyền** | 3 |
| `docs/plans/RR-rotation-recovery.md` | Tạo | — | mọi Task |
| `docs/knowledgements/RR-rotation-recovery.md` | Tạo | — | từ Task 2 |

**Không đụng:** `core/glass/*`, `core/ui/DragHelper.kt`, `ui/home/controller/HomePagerController.kt`, `AppLibraryController.kt`, `LibraryOverlayController.kt`, `HomeTouchRouter.kt`, `PageMotion.kt`, `TunedPagerSnapHelper.kt`, `ui/home/view/*`, `data/*`, `res/layout/*`.

**Xếp lịch với `fix/lb-library-back-swipe`:** RR không đụng file pager/cử chỉ/Library. Chồng duy nhất có thể có là `HomeActivity.kt` (độc quyền). Đề xuất: Task 1 chỉ thêm một override `onConfigurationChanged` (log tạm) và sửa manifest → nên làm sau khi LB xong phần `HomeActivity`, hoặc điều phối cho phép hai nhánh cùng thêm (thay đổi của RR chỉ là thêm dòng, không sửa dòng cũ; nhánh merge sau rebase). Nếu LB không sửa `HomeActivity` thì chạy song song được. Điều phối quyết.

## Global Constraints
- `minSdk 26`, `targetSdk 37`. API dùng đều có từ API 24 (`isInMultiWindowMode`) hoặc sớm hơn.
- Luật phụ thuộc `ui → domain ← data`: mọi thay đổi trong `ui/home`; `HomeViewModel` không nhận `Configuration`, chỉ nhận `(width, height, inMultiWindow)`.
- `HomeActivity`: thứ tự là hành vi (R1). Controller mới dựng **sau** `backdrop`, `dock`, `iconMenu` (dùng chúng), gắn listener sau `systemBars.setup()`; `onConfigurationChanged` gọi `super` trước.
- Không ghi DB, không reflow, không đổi `HomeUiState` vì đổi cỡ (Review Focus 6).
- Mỗi lần cỡ ổn định: giải mã ảnh nền tối đa một lần.
- Không thêm chữ hiển thị (không cần `strings.xml`).
- Log chẩn đoán tạm (tag `RR`) chỉ sống tới Task 5; gỡ trước khi đóng, so `git diff` như bài học AN ("công tắc đo tạm không được vào `main`").
- Commit tiếng Anh `<type>: <imperative>`, `git add` đường dẫn cụ thể, không `-A`, không thư mục.
- Máy (điều phối cho phép 2026-11-12): chỉ Live 4, luôn `adb -s BGB00153427`, `MSYS_NO_PATHCONV=1` trong Git Bash. Máy dùng chung với LB: giữ khóa `E:/SourceTree/IosLauncher-wt/live4.lock` (`mkdir` nguyên tử, ghi tên nhánh vào `owner`) cho đúng một phiên cài + kiểm, nhả ngay sau đó, không giữ khi code. Trước khi kiểm xác nhận APK trên máy là của nhánh (md5 `base.apk` kéo về = APK local). `install -r` (giữ dữ liệu); không gỡ, không xóa dữ liệu. Ghi và trả lại `accelerometer_rotation`/`user_rotation`, `wm size reset`, `cmd uimode night` như cũ; kết thúc phiên bằng Home, trang 1, không app ngang ở trước. Dữ liệu trông sai thì dừng, báo điều phối (không tự khôi phục).

## Review Focus
1. **Khởi động nguội khi màn đang ngang** (force-stop Home lúc game ngang chạy, rồi Home): lần layout đầu có thể ở ngang → `onScreenSize` giải mã ngang → sau khi về đứng phải giải mã lại đúng một lần, lần nạp ngang (nếu chưa xong) bị hủy, không chồng hai bitmap. (Task 3)
2. **Đổi sáng/tối (tạo lại Activity):** không giải mã lại ảnh nền, không chạy việc đắt; lần layout đầu của Activity mới không bị coi là "đổi cỡ". (Task 2, 3)
3. **Split-screen kéo thanh chia:** không giải mã lại theo từng bước; lưới/dock co như M5; ra khỏi split-screen không giải mã lại. (Task 2, 3)
4. **Cỡ tạm trong chuyển cảnh Home** (game ngang → Home): không crash, không để trạng thái cũ, không dựng bản mờ nhiều lần. Đếm log `RR` và số lần `settle`. (Task 1, 2)
5. **Hẹn giờ `postDelayed` không rò** khi Activity bị hủy (gỡ ở `onDestroy`), và không chạy trên Activity đã dừng. (Task 2)
6. **Đổi cỡ không đụng dữ liệu bố cục:** không gọi hàm ghi nào của `LayoutRepository`, `HomeUiState.pages` không đổi. (Task 2, 3)
7. **Tự xoay tắt + `user_rotation` ngang:** Home vẫn đứng (`NOSENSOR` không chịu khóa xoay của người dùng). (Task 1)
8. **Home không xoay khi người dùng cầm ngang máy ở Home** (giữ M5 Task 1). (Task 1)

---

### Task 1: Yêu cầu hướng `nosensor` + log chẩn đoán chuỗi cấu hình
**Bắt buộc kiểm tay:** **có** — mục 4.2 loại 1: Task 2-3 xây trên hai giả định chỉ máy thật trả lời được: (a) ROM Vsmart Android 11 tôn trọng `nosensor` cho Activity HOME như AOSP (về góc 0 khi Home được gọi về lúc màn ngang); (b) Home thấy chuỗi cấu hình/cỡ nào trong lúc chuyển cảnh (có cỡ ngang tạm hay không, cách nhau bao nhiêu ms) — số này chốt `SETTLE_MS`. Sai (a) thì phải đổi Phần 1 (phương án D hoặc C).
**Files:** `AndroidManifest.xml`, `HomeActivity.kt`
**Interfaces:** Consumes: không. Produces: không (log tạm).
- [x] **Step 1:** Manifest: `android:screenOrientation="nosensor"` cho `HomeActivity`, `SettingsActivity`, `AppLibraryActivity` (D7); sửa chú thích của `HomeActivity` trỏ tới plan này (vì sao không `locked`/`portrait`).
- [x] **Step 2:** Log tạm tag `RR`: override `onConfigurationChanged(newConfig)` (gọi `super` trước) in `orientation`, `screenWidthDp`x`screenHeightDp`, `isInMultiWindowMode`, `SystemClock.uptimeMillis()`; listener đổi cỡ `home_root` in cỡ mới + thời điểm; `onResume`/`onStop` in một dòng. Đánh dấu `// RR-TEMP` để gỡ ở Task 5.
- [x] **Step 3:** `.\gradlew.bat assembleDebug` xanh. Commit `fix: request natural orientation for home`.
- [x] **Step 4: Kịch bản kiểm (Live 4, bản debug — ghi rõ debug vì debug chậm hơn release)** — điều phối chạy, gửi lại log `adb logcat -s RR` + kết quả `dumpsys`:
  - (a) Tái hiện lỗi gốc 5 lần (mục 1 bài kiểm cuối): Home đứng mỗi lần.
  - (b) Tự xoay tắt + `user_rotation 1`, bấm Home: Home đứng.
  - (c) Cầm ngang máy ở Home (tự xoay bật): Home không xoay.
  - (d) Từ log: liệt kê chuỗi cỡ `home_root` mà Home thấy khi về từ game ngang (có cỡ 2340x1080 tạm không, khoảng cách tới cỡ cuối). Chốt `SETTLE_MS` = max(250, khoảng đó + 100) ms.
  - Nếu (a) hỏng: dừng, báo điều phối; đề xuất phương án D/C trước khi làm tiếp.
**Kết quả Task 1 (đo được, Live 4, bản debug `5c43928`, md5 APK trên máy = APK local `9c7a7dcc…`, 2026-11-12, agent RR chạy, khóa máy `live4.lock`):**
- Trước khi cài (bản trên máy là `locked`): game → Home cho `SurfaceOrientation: 1`, screencap 2340x1080 — tái hiện đúng lỗi.
- (a) Game ngang → Home, 5/5 lần: `SurfaceOrientation: 0`, screencap 1080x2340, lưới/dock/ảnh nền bình thường (`dumpsys input`).
- (b) `accelerometer_rotation 0` + `user_rotation 1`: Home đứng; game ngang → Home: đứng. Trả lại `1`/`0`.
- Khởi động nguội (force-stop Home lúc game ngang ở trước, rồi Home): đứng; lần layout đầu `root 1080x2340 old 0x0` — **không** có khung ngang tạm.
- Gần đây (game ngang → Gần đây → Home): đứng.
- D7: với `user_rotation 1` (khóa ngang), mở Cài đặt từ edit mode và "Ứng dụng đã ẩn" (`AppLibraryActivity`): cả hai đứng (`SurfaceOrientation: 0`, 1080x2340).
- (d) Chuỗi cấu hình: trong mọi ca trên Home **không nhận `onConfigurationChanged` nào và `home_root` không đổi cỡ**; chỉ `onStop`/`onResume` (`o=1` = đứng). Tức là với `nosensor`, hệ thống xoay màn về 0 **trước** khi Home hiện, Home dừng thì không nhận cấu hình ngang. `SETTLE_MS` giữ 250 ms (không có cỡ tạm nào để vượt qua); đường hồi phục chỉ chạy khi cửa sổ thật sự đổi cỡ (split-screen, `wm size`, máy lớn).
- (c) Cầm ngang máy ở Home: không giả lập được bằng `adb` (cảm biến thật) → để chủ dự án kiểm tay ở bài kiểm cuối mục 7. Video theo cảm biến (mục 2) cũng cần cầm máy thật.

### Task 2: `WindowSizeRecovery` — gom đổi cỡ, làm mới kính, đóng menu nhấn giữ
**Bắt buộc kiểm tay:** không.
**Files:** tạo `ui/home/controller/WindowSizeRecovery.kt`; sửa `HomeActivity.kt`.
**Interfaces:**
- Consumes: `ActivityHomeBinding` (`homeRoot`, `dockView`, `searchPill`), `HomeViewModel.closeIconMenu()`, `GlassEnvironment.from(ctx).notifyBackdropChanged()`, `DockView.captureGlassOnce()`, `SearchPillView.refreshGlass()`.
- Produces (internal):
  ```kotlin
  internal class WindowSizeRecovery(
      private val binding: ActivityHomeBinding,
      private val isResumed: () -> Boolean,
      private val isInMultiWindow: () -> Boolean,
      private val onSettled: (width: Int, height: Int, inMultiWindow: Boolean) -> Unit, // Task 3 nối vào ViewModel
      private val onResizedNow: () -> Unit // việc rẻ: đóng menu nhấn giữ
  ) {
      fun setup()                     // gắn OnLayoutChangeListener lên home_root
      fun onConfigurationChanged()    // đặt lại hẹn giờ (cấu hình đổi có thể tới trước layout)
      fun onResume()                  // chạy settle nếu có đổi cỡ trong lúc dừng
      fun onDestroy()                 // gỡ hẹn giờ + listener
  }
  ```
- [x] **Step 1:** Listener `home_root`: bỏ qua lần layout đầu (old = 0) và khi cỡ không đổi. Cỡ đổi → `onResizedNow()` (chỉ khi `screen.ui.iconMenu != null` mới gọi `closeIconMenu`, xử lý ở lambda của `HomeActivity`), đánh dấu `pending`, `removeCallbacks` + `postDelayed(settle, SETTLE_MS)`.
- [x] **Step 2:** `settle`: nếu chưa resumed thì giữ `pending` (để `onResume` chạy); nếu cỡ hiện tại ≤ 0 thì bỏ. Ngược lại: `onSettled(w, h, isInMultiWindow())`, rồi làm mới kính: `dockView.captureGlassOnce()`, `GlassEnvironment.notifyBackdropChanged()`, `searchPill.refreshGlass()`. (Nếu Task 3 giải mã lại ảnh nền thì đường `BackdropListener` còn làm mới thêm lần nữa khi bitmap mới tới — đúng ý, vì nền đã khác.)
- [x] **Step 3:** Nối vào `HomeActivity`: dựng sau `backdrop`/`dock`/`iconMenu`, `setup()` sau `systemBars.setup()`; `onConfigurationChanged` → `recovery.onConfigurationChanged()`; `onResume` → `recovery.onResume()`; `onDestroy` → `recovery.onDestroy()`. `onSettled` tạm thời chỉ log `RR` (Task 3 nối VM).
- [x] **Step 4:** Build xanh. Commit `feat: add window size recovery for home`. Bắt đầu knowledgement (mục 1-3, sơ đồ `sequenceDiagram` game ngang → Home → settle).
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) `wm size 1080x1500` rồi `wm size reset` khi Home đứng yên: log đúng một `settle` mỗi lệnh, kính dock/pill/ô thư mục khớp nền; (b) cùng việc đó khi menu nhấn giữ đang mở: menu đóng, không còn lớp tối; (c) khi thư mục / tìm kiếm / Library / edit mode đang mở: không crash, lớp phủ bố cục lại đúng cỡ mới; (d) split-screen kéo thanh chia: nhiều lần đổi cỡ, chỉ một `settle` sau khi thả.

### Task 3: Giải mã lại ảnh nền theo cỡ cửa sổ đã ổn định
**Bắt buộc kiểm tay:** không.
**Files:** `HomeViewModel.kt`, `HomeActivity.kt` (một dòng nối `onSettled`).
**Interfaces:**
- Produces: `fun HomeViewModel.onWindowSizeSettled(width: Int, height: Int, inMultiWindow: Boolean)`.
- [x] **Step 1:** VM nhớ cỡ của lần giải mã gần nhất (`decodedW/decodedH`, gán **đồng bộ** trong `launchBackdropLoad`, cùng lý do với `shownBackdropVersion`).
- [x] **Step 2:** `onWindowSizeSettled`: bỏ qua nếu `inMultiWindow` hoặc cỡ ≤ 0 (trước cả `rememberScreenSize`, để cỡ split-screen không vào `lastScreenW/H` — review điều phối, `fix: keep the full-screen size while in split screen`); rồi `rememberScreenSize(w, h)`; bỏ qua nếu `!screenSizeHandled`, hoặc `(w, h) == (decodedW, decodedH)`; ngược lại `launchBackdropLoad(w, h)` (hủy lần nạp đang chạy, ảnh mới nhất thắng). Không đổi `shownBackdropVersion`.
- [x] **Step 3:** `HomeActivity`: `onSettled = viewModel::onWindowSizeSettled`.
- [x] **Step 4:** Build xanh. Commit `fix: reload backdrop when home window size settles`. Knowledgement mục 4-6.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) khởi động nguội lúc game ngang (mục 4 bài kiểm cuối): ảnh nền đúng tỉ lệ đứng sau < 1 s, log một lần giải mã lại; (b) `wm size 2340x1080` (màn hình dạng ngang ở góc 0) rồi `wm size reset`: mỗi lần một lần giải mã, kính khớp; (c) đổi sáng/tối: không log giải mã lại do RR; (d) split-screen vào/ra: không giải mã lại.

**Kết quả Task 2 + 3 (đo được, Live 4, bản debug `56d6ad0`, md5 trên máy = local `98193899…`, 2026-11-12):**
- Hồi quy lỗi gốc: game ngang → Home 3/3 đứng, không có dòng `settle` nào (Home không đổi cỡ).
- `wm size 1080x1500` → `reset` → `wm size 2340x1080` → `reset`: mỗi lệnh đúng **một** `config`, một `root` đổi cỡ, một `settle` sau ~250 ms (vd `root` 14:53:23.567 → `settle` 23.827), một `backdrop reload` (2340→1500, 1500→2340, 2340→2160x1080, 2160x1080→2340). Ảnh nền đúng tỉ lệ ở cả khung ngang, kính khớp nền; về lại đứng như cũ.
- Menu nhấn giữ (Drive) đang mở + `wm size`: menu và lớp tối đóng (D5).
- Thư mục / tìm kiếm (có bàn phím) / edit mode / App Library đang mở + `wm size` rồi `reset`: không crash, lớp phủ bố cục lại theo cỡ mới, giữ trạng thái; mỗi lần một `settle` + một `reload`. Vuốt đóng Library sau đó về đúng trang cuối.
- Sáng/tối (`cmd uimode night no` rồi `yes`): Activity dựng lại, lần layout đầu `old 0x0`, **không** có `settle`/`reload` (RF2).
- Split-screen (`am start --windowingMode 3` Cài đặt hệ thống): Home `mw=true` 1080x1974, một `settle`, **không** `reload`; ra khỏi split: `settle 1080x2340 mw=false`, **không** `reload` (cỡ bằng cỡ đã giải mã) (RF3). Không kéo được thanh chia bằng `input swipe` (Android 11 ở chế độ dock thu nhỏ khi Home là nửa dưới) — kéo thanh chia để chủ dự án kiểm tay.
- Khởi động nguội lúc game ngang (force-stop + Home): lần layout đầu đã là `1080x2340`, không `reload`. Ca "lần layout đầu ở ngang" của RF1 **không xảy ra** trên Live 4 với `nosensor`; chỉ được code bảo vệ.
- Không có `FATAL EXCEPTION` trong logcat suốt phiên.


### Task 4: Màn lớn (sw ≥ 600dp) — **BỎ** (chủ dự án 2026-11-12, D8)
Không làm. Giới hạn đã biết ghi ở "Ngoài phạm vi". Nếu làm sau: inset trái/phải (`systemBars` + `displayCutout`) thành margin ngang của `home_content` trong `SystemBarsController`, và (tùy quyết định) `requestedOrientation = UNSPECIFIED` khi màn thật sw ≥ 600dp như Launcher3 `isTablet(realBounds)`.

### Task 5: Gỡ log tạm, hoàn tất tài liệu
**Bắt buộc kiểm tay:** không.
- [x] **Step 1:** Gỡ mọi dòng `// RR-TEMP`; `git grep -n "RR-TEMP"` rỗng; `git diff <commit Task 3> HEAD -- app` chỉ còn phần gỡ log.
- [x] **Step 2:** Build xanh, commit `chore: remove RR diagnostic logs`. Knowledgement mục 7-8, Review Focus evidence, mục "Đề xuất cập nhật tài liệu chung".

**Kết quả Task 5 (đo được, Live 4, bản debug `76a3372`, md5 `e365942f…`, 2026-11-12):** `git grep RR-TEMP -- app` rỗng; `git diff 56d6ad0 76a3372 -- app` chỉ gồm các dòng log bị gỡ và chú thích `SETTLE_MS`. `assembleDebug lintDebug` xanh; lint chỉ cảnh báo `DiscouragedApi` ("Fixed screen orientations will be ignored ... from Android 16") ở ba dòng `screenOrientation` — đúng giới hạn D8. Phiên kiểm ngắn: game → Home 5/5 đứng, `wm size 2340x1080`/`reset` bình thường, không crash.


---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11), Os26Launcher là launcher mặc định, bản **debug** của nhánh (ghi `git rev-parse --short HEAD`). Xác nhận máy rảnh trước khi cài. **Dữ liệu:** có ảnh nền (ảnh người dùng chọn, không phải ảnh mặc định, để thấy rõ tỉ lệ), ít nhất 2 trang, 1 thư mục, 1 widget; game ngang `com.example.baocaoltgamethu35` đã cài. Ghi lại giá trị tự xoay ban đầu và **trả lại** sau khi kiểm:
```powershell
adb shell settings get system accelerometer_rotation
adb shell settings get system user_rotation
```
Kiểm hướng (dùng sau mỗi mục):
```powershell
adb shell dumpsys window displays | Select-String -Pattern 'mRotation=|mLastOrientationSource|mCurrentAppOrientation'
adb exec-out screencap -p > rr.png   # ảnh 1080x2340 = đứng
```

1. **Lỗi gốc, lặp 5 lần (tự xoay bật):** `adb shell am start -n com.example.baocaoltgamethu35/.MainActivity`, chờ game ngang, `adb shell input keyevent KEYCODE_HOME`. Mong đợi: `mRotation=0` (hoặc `ROTATION_0`), screencap 1080x2340, lưới/dock/ảnh nền bình thường. Kiểm tự động (thay cho unit test, D6):
   ```powershell
   1..5 | ForEach-Object {
     adb shell am start -n com.example.baocaoltgamethu35/.MainActivity | Out-Null; Start-Sleep 3
     adb shell input keyevent KEYCODE_HOME; Start-Sleep 2
     $r = adb shell dumpsys window displays | Select-String 'mRotation=' | Select-Object -First 1
     "lan $_ : $r"
   }
   ```
   Đạt khi cả 5 dòng là góc 0.
2. **Video/app theo cảm biến:** mở một app xoay theo cảm biến (Chrome hoặc YouTube toàn màn), cầm ngang cho app xoay, bấm Home khi vẫn cầm ngang → Home đứng.
3. **Gần đây:** từ game ngang, mở Gần đây, rồi về Home (bấm Home hoặc vuốt) → Home đứng.
4. **Khởi động nguội lúc ngang:** game ngang đang chạy, `adb shell am force-stop gambi.global.os26launcher`, bấm Home → Home đứng; ảnh nền đúng tỉ lệ sau < 1 s; không crash.
5. **Process bị giết lúc app ngang chạy:** game ngang đang chạy, `adb shell am kill gambi.global.os26launcher` (chỉ giết process "an toàn để giết"; kiểm bằng `adb shell pidof gambi.global.os26launcher` rỗng — ROM có thể không coi process HOME là an toàn, khi đó ghi lại và coi mục 4 là đủ), bấm Home → như mục 4.
6. **Khóa xoay của người dùng:** `adb shell settings put system accelerometer_rotation 0`, `adb shell settings put system user_rotation 1`, bấm Home → Home đứng (RF7). Trả lại giá trị ban đầu.
7. **Cầm ngang ở Home** (tự xoay bật): Home không xoay (RF8, hồi quy M5 Task 1).
8. **Đổi cỡ khi lớp phủ đang mở** (`adb shell wm size 1080x1500`, quan sát, `adb shell wm size reset`): lần lượt với (a) edit mode, (b) thư mục mở, (c) tìm kiếm mở (có bàn phím), (d) App Library mở, (e) panel nhóm của Library mở, (f) menu nhấn giữ icon mở → (f) menu đóng; (a)-(e) giữ nguyên, bố cục lại đúng cỡ, không crash, kính khớp nền. Cuối cùng chắc chắn đã `wm size reset`.
9. **Màn dạng ngang ở góc 0:** `adb shell wm size 2340x1080` → Home bố cục trong khung ngang, ảnh nền giải mã lại một lần (log), không crash; `wm size reset` → về như cũ, giải mã lại một lần.
10. **Hồi quy split-screen (M5):** vào split-screen với một app, kéo thanh chia nhỏ/lớn: lưới và dock co cùng cỡ như trước (so ảnh với `main`), không giải mã lại ảnh nền; ra khỏi split-screen: không giải mã lại.
11. **Hồi quy sáng/tối:** `adb shell cmd uimode night yes` rồi `no` khi Home ở trước: Activity tạo lại, icon thanh hệ thống đúng màu, ảnh nền đúng (ảnh mặc định đổi sáng/tối theo WP nếu dùng ảnh mặc định), không giải mã thừa do RR.
12. **Cài đặt và Thư viện (D7):** cầm ngang máy (tự xoay bật), mở Cài đặt từ edit mode, rồi mở Thư viện từ Cài đặt → cả hai đứng; Back về Home → Home đứng.
13. **Hồi quy Home khi đang ở Home (HB/B10):** mở thư mục/tìm kiếm/Library/edit mode, bấm Home → đóng như trước.
14. **Đo nhẹ (debug):** đếm dòng log giải mã lại và `settle` trong mục 1, 4, 10 — mục 1: 0 lần giải mã lại (Home vốn đứng); mục 4: đúng 1; mục 10: 0.

**Đã kiểm bằng `adb` (agent, Live 4, 2026-11-12, `56d6ad0` và `76a3372`):** mục 1, 3, 4, 6, 8, 9, 11, 12 (Cài đặt/Thư viện dưới khóa xoay ngang), một phần 10 (vào/ra split). **Còn cho chủ dự án kiểm tay** (cần cảm biến hoặc ngón tay thật): mục 2 (video/app theo cảm biến), 7 (cầm ngang ở Home), 10 (kéo thanh chia split-screen), 13 (Home đóng lớp phủ, hồi quy), và nhìn bằng mắt chuyển cảnh game → Home.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** —

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | Plan | Điều tra nguyên nhân, viết plan | `c4e7f6c` | Draft PR #43 |
| 2026-11-12 | Plan | Ghi quyết định chủ dự án Q1-Q5 (D6-D9), thêm `nosensor` cho Cài đặt/Thư viện, bỏ Task 4, quy tắc dùng máy | `ec9a4f8` | Plan duyệt (điều phối, chủ dự án ủy quyền) |
| 2026-11-12 | Task 1 | Manifest `nosensor` (Home, Cài đặt, Thư viện) + log tạm `RR`; kiểm Live 4: 5/5 đứng, khóa xoay ngang, khởi động nguội, Gần đây, Cài đặt/Thư viện đều đứng; không có cấu hình ngang tạm | `5c43928` | Task bắt buộc kiểm tay: đạt (agent kiểm theo cho phép của điều phối); (c) chờ chủ dự án |
| 2026-11-12 | Plan | Ghi kết quả Task 1 | `46204ef` | |
| 2026-11-12 | Task 2 | `WindowSizeRecovery` (gom đổi cỡ 250 ms, làm mới kính, đóng menu nhấn giữ), nối vào `HomeActivity` | `fd33257` | Kiểm chung phiên với Task 3 (một lần giữ máy) |
| 2026-11-12 | Task 3 | `HomeViewModel.onWindowSizeSettled` + `decodedW/H`: giải mã lại ảnh nền khi cỡ toàn màn đổi, bỏ qua multi-window | `56d6ad0` | Live 4: mỗi lần đổi cỡ đúng 1 `settle` + 1 `reload`; split/sáng-tối 0 `reload` |
| 2026-11-12 | Task 5 | Gỡ log tạm, ghi số đo cạnh `SETTLE_MS`; knowledgement | `76a3372` | Live 4 smoke: 5/5 đứng |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Khởi động nguội lúc ngang | Live 4: force-stop lúc game ngang → Home: lần layout đầu đã đứng (`root 1080x2340 old 0x0`), không `reload`. Đường "layout đầu ở ngang → giải mã lại" chỉ được code bảo vệ (`onWindowSizeSettled` + hủy `backdropJob`) | Đạt (ca ngang không tái hiện được) |
| 2 | Đổi sáng/tối | Live 4: `cmd uimode night no/yes`: không `settle`, không `reload` | Đạt |
| 3 | Split-screen | Live 4: vào split `settle mw=true` không `reload`; ra split không `reload`. Kéo thanh chia: chưa kiểm (không giả lập được) | Một phần |
| 4 | Cỡ tạm trong chuyển cảnh | Live 4: về từ game ngang/Gần đây/khởi động nguội Home không nhận cấu hình ngang nào; `wm size` một `settle` mỗi lần | Đạt |
| 5 | Hẹn giờ không rò | Đọc code: `onDestroy` gỡ hẹn giờ + listener; `runSettle` không chạy khi chưa resumed. Sáng/tối dựng lại Activity không lỗi | Đạt (đọc code + kiểm) |
| 6 | Không đụng dữ liệu bố cục | Đọc code: `WindowSizeRecovery`/`onWindowSizeSettled` không gọi `LayoutRepository`, không đổi `HomeUiState.pages`; sau mọi lần `wm size` bố cục trang 1 giữ nguyên (ảnh chụp) | Đạt |
| 7 | Khóa xoay người dùng | Live 4: `accelerometer_rotation 0` + `user_rotation 1`: Home, Cài đặt, "Ứng dụng đã ẩn" đều đứng | Đạt |
| 8 | Cầm ngang ở Home | Cần cảm biến thật, chưa kiểm | Chờ chủ dự án |

## Câu hỏi cho chủ dự án (đã trả lời 2026-11-12)
- **Q1 — Máy lớn:** bỏ Task 4, ghi giới hạn đã biết (D8).
- **Q2 — Cài đặt và Thư viện:** có, `nosensor` cho cả hai, chỉ manifest (D7, Task 1).
- **Q3 — Lớp phủ khi đổi cỡ:** chỉ đóng menu nhấn giữ (D5).
- **Q4 — Ảnh nền sai tỉ lệ 0,3-0,6 s ở khởi động nguội lúc ngang:** chấp nhận (D9).
- **Q5 — Test tự động:** theo quy ước, không test; dùng kịch bản `adb` lặp (D6).

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md` TT#5: "khóa xoay (`screenOrientation="nosensor"`, RR thay `locked`): Home luôn về hướng tự nhiên, kể cả khi được gọi về lúc màn ngang; cửa sổ đổi cỡ thì ảnh nền và kính dựng lại sau khi cỡ ổn định".
- `project_structure.md` mục 4: thêm `android:screenOrientation="nosensor"` vào khối manifest dự kiến + một gạch đầu dòng vì sao không `locked`/`portrait`; sửa câu "`GridMetrics` tính lại khi `onConfigurationChanged`" thành mô tả thật (cây view tự layout lại; `WindowSizeRecovery` lo ảnh nền/kính/menu).
- `plans/M5-orientation-theme.md`: ghi chú ở Task 1 trỏ tới RR (không sửa lịch sử, chỉ thêm dòng).
- `lessons/home-pager-animation.md` và `lessons/permissions-and-device-testing.md`: xem mục 8 của [knowledgement RR](../knowledgements/RR-rotation-recovery.md).
