# M5 — Khóa xoay, sáng/tối, system bar, polish lưới+dock

> **Trạng thái: XONG 2026-10-27** (đo trên Vsmart Live 4). Chưa thử tay: dock đủ 4 icon thẳng cột với lưới (Chrome rời dock từ M4 nên dock đang 3 icon; Task 4 Bước 5 mục 2, phần 4 icon) — kiểm lại khi dock có lại đủ 4 icon. Phép đo 4b không thuộc M5 (ở M6, A9).

> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans`. Các bước dùng checkbox `- [ ]`.
>
> **Dự án này KHÔNG có test** (A7). Đừng viết unit test, instrumented test, đừng thêm thư viện test. Kiểm chứng tay trên máy thật, mỗi task có hành động và kết quả mong đợi. Không tick checkbox khi chưa có người xác nhận.
>
> **KHÔNG dùng kính ở mốc này** (A9 — M6). **KHÔNG kéo-thả** (M7). **KHÔNG thêm cài đặt Sáng/Tối riêng** — theo đúng theme hệ thống (chốt khi brainstorm M5).

**Goal:** Launcher khóa xoay màn hình (giữ hướng tự nhiên lúc khởi động), tự đổi sáng/tối đúng theo hệ thống, thanh trạng thái/điều hướng trong suốt và đổi màu icon theo theme, và lưới icon không còn bị dock che hàng cuối (6 hàng icon + 1 hàng dock, không chồng lên nhau).

**Architecture:** Không có component mới. Sửa `AndroidManifest.xml` (khóa orientation), `HomeActivity.kt` (system bar styling + cập nhật Cells dock khi kích thước đổi mà không có state mới), `activity_home.xml` (tách `home_pager`/`bottom_bar` theo tỉ lệ `layout_weight` 6:1), `GridMetrics.kt` (thêm hằng số trọng số dock). Toàn bộ nằm trong `ui/home/` và `core/ui/` — không đụng `domain`/`data`.

**Tech Stack:** View/XML, `WindowInsetsControllerCompat` (androidx core), `Window.isNavigationBarContrastEnforced`/`isStatusBarContrastEnforced` (API 29+, có nhánh dự phòng cho 26-28). Không Compose (B1).

**Spec:** Thiết kế thống nhất trong phiên brainstorm trước plan này (không có file spec riêng — dự án gộp thiết kế vào thẳng file plan, như M1–M4). Tài liệu liên quan: [`../Launcher-Features.md`](../Launcher-Features.md) TT#5 ("Back/Home, insets, xoay màn hình, sáng/tối" — phần Back+inset đã Xong ở M1, còn xoay+sáng/tối cho M5); [`../AGENTS.md`](../AGENTS.md) mục "Inset và layout màn hình chính" (quan sát nav bar trắng đặc trên Vsmart Live 4, 2026-10-27, ghi rõ thuộc phạm vi M5); [`../project_structure.md`](../project_structure.md) mục 2 (A1–A10, luật phụ thuộc), mục 4 (manifest dự kiến).

## Global Constraints

- `minSdk 26`, `targetSdk 37`, Java 11. API 29+ dùng được, phải có nhánh dự phòng đúng cho 26-28 (kiểm `Build.VERSION.SDK_INT`).
- View/XML, ViewBinding, không Compose (B1). Không `LiquidGlassView` (A9).
- **Luật phụ thuộc:** mọi thay đổi nằm trong `ui/home/` và `core/ui/`, không chạm `domain`/`data`.
- **Máy test mặc định Vsmart Live 4** (A8) — M5 không liên quan kính, không cần Oppo A3x trừ khi Live 4 không tái hiện được ca đang xét.
- Mọi chữ hiển thị vào `strings.xml` (không task nào ở đây thêm chữ mới, nhưng nếu phát sinh phải tuân).
- Không xóa file ngoài phạm vi đã duyệt. `demo/`, `MainActivity`, `activity_main.xml`, `view_extra_glass.xml` **vẫn giữ đến sau M5 này** — nghĩa là M5 xong mới được đề xuất xóa (project_structure.md mục 3 ghi "xóa icon Demo kính sau M5"); việc xóa demo KHÔNG nằm trong phạm vi plan này, chỉ ghi chú để không quên.
- Commit message tiếng Anh, `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt.
- Lệnh viết cho PowerShell. `adb shell am force-stop gambi.global.os26launcher` trước mỗi lần thử lại trạng thái khởi động.

## Review Focus

Năm trường hợp spec hàm ý nhưng đường đi hạnh phúc không chạm tới:

1. **Đổi Dark/Light khi app đang foreground** → `uiMode` không có trong `configChanges` hiện tại (đã xác nhận đọc `AndroidManifest.xml`) nên Activity tự tái tạo — đây là hành vi hệ thống, không phải code của ta, nhưng phải đo xem icon system bar và nền có đổi đúng ngay sau tái tạo, không giữ icon tối trên nền sáng hay ngược lại. (Task 3)
2. **Trang lưới hiện tại bị reset về 0 sau khi Activity tái tạo do đổi theme** — `ViewPager2.currentItem` không sống qua tái tạo vì đây là Activity tái tạo thật (khác với đổi danh sách app, nơi `savedPage` trong `render()` chỉ giữ trang trong CÙNG instance). Đây là **hạn chế đã biết, chấp nhận không sửa ở M5** (đổi theme là hành động hiếm, không như xoay) — ghi lại ở đây để không bị coi là bug bỏ sót, không tạo task riêng.
3. **Split-screen/gập màn hình đổi kích thước mà không có state mới từ ViewModel** → `render()` chỉ chạy lại khi `uiState` phát giá trị mới; nếu người dùng kéo thanh chia màn hình mà không có sự kiện app nào xảy ra, `dock_view` vẫn giữ `Cells` cũ (icon dock sai cỡ so với lưới mới). (Task 2)
4. **Thiết bị API 26-28** không có `Window.isNavigationBarContrastEnforced`/`isStatusBarContrastEnforced` (thêm từ API 29) → gọi trực tiếp sẽ không tồn tại ở API cũ hơn, phải bọc `if (Build.VERSION.SDK_INT >= 29)`, không crash trên API 26-28. (Task 3)
5. **Màn hình quá nhỏ sau khi chia 6:1** (ví dụ split-screen 1/3 màn hình) có thể làm `GridMetrics.measure()` trả `null` (ô < 48dp) cho lưới chính NHƯNG dock (tính riêng theo tỉ lệ 6/7 của `home_root`, không phải theo `home_pager` thật) vẫn có thể đo ra dương — phải không crash khi `gridCells` null, giữ đúng hành vi cũ của `IconGridView`/`DockView` (ẩn 0×0), không hiện dock lệch cỡ so với lưới trống. (Task 4)

---

## Task 1: Khóa hướng xoay

Không đổi hành vi đổi theme (manifest hiện tại đã đúng — không có `uiMode` trong `configChanges`, xác nhận bằng đọc file, không cần sửa).

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Không có interface Kotlin nào thay đổi — chỉ thuộc tính manifest.

- [x] **Bước 1: Thêm `android:screenOrientation="locked"` vào khai báo `.ui.home.HomeActivity`**

```xml
<activity
    android:name=".ui.home.HomeActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:clearTaskOnLaunch="true"
    android:resizeableActivity="true"
    android:screenOrientation="locked"
    android:configChanges="keyboard|keyboardHidden|navigation|screenSize|smallestScreenSize|screenLayout|orientation"
    android:windowSoftInputMode="adjustNothing"
    android:enableOnBackInvokedCallback="true">
```

`"locked"` (API 18+) giữ đúng hướng hiện tại lúc Activity khởi động, không theo cảm biến — khác `"portrait"` cứng, đúng quyết định đã chốt ("khóa theo hướng tự nhiên của màn hình").

- [x] **Bước 2: Kiểm chứng tay trên Vsmart Live 4**

1. `gradlew.bat installDebug`, mở launcher, để tự nhiên ở hướng đứng.
2. Xoay máy vật lý sang ngang, giữ vài giây.
3. Mở Demo kính (`MainActivity`, không ảnh hưởng bởi khóa này) rồi quay lại Home bằng nút Home — xác nhận Home vẫn đứng.

Kết quả mong đợi: màn hình chính **không xoay** theo cảm biến ở bước 2. Chưa tick nếu chưa người dùng xác nhận.

- [x] **Bước 3: Commit** (người dùng tự commit trên máy, theo quy ước AGENTS.md)

```powershell
git add app/src/main/AndroidManifest.xml
git commit -m "feat: lock home screen orientation to natural rotation"
```

---

## Task 2: Dock tự cập nhật Cells khi kích thước đổi không qua state mới

**Files:**
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt`

**Interfaces:**
- Consumes: `GridMetrics().measure(width, height, density): GridMetrics.Cells?` (đã có).
- Produces: `HomeActivity.updateDockCells()` (private) — Task 4 sẽ sửa lại đúng hàm này để áp tỉ lệ 6/7, không viết hàm mới.

- [ ] **Bước 1: Thêm field lưu dock items mới nhất, tách phần tính Cells cho dock khỏi `render()` thành hàm riêng**

```kotlin
// Field mới, cạnh shownBackdrop
private var lastDockItems: List<AppItem> = emptyList()

private fun updateDockCells() {
    val gridCells = GridMetrics().measure(
        binding.homeRoot.width, binding.homeRoot.height, resources.displayMetrics.density
    )
    binding.dockView.setItems(lastDockItems, gridCells)
}
```

Trong `render(state)`, thay đoạn hiện có:

```kotlin
val gridCells = GridMetrics().measure(
    binding.homeRoot.width, binding.homeRoot.height, resources.displayMetrics.density
)
binding.dockView.setItems(state.dock, gridCells)
```

bằng:

```kotlin
lastDockItems = state.dock
updateDockCells()
```

- [ ] **Bước 2: Lắng nghe đổi kích thước của `home_root` để gọi lại `updateDockCells()`**

Thêm trong `onCreate()`, sau `applySystemBarInsets()`:

```kotlin
binding.homeRoot.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
    val sizeChanged = (right - left != oldRight - oldLeft) || (bottom - top != oldBottom - oldTop)
    if (sizeChanged) updateDockCells()
}
```

Không cần gỡ listener ở `onDestroy()` — `home_root` bị hủy cùng Activity, không rò ra ngoài.

- [x] **Bước 3: Kiểm chứng tay trên Vsmart Live 4**

1. Mở launcher, vào chế độ chia đôi màn hình (kéo từ Recents hoặc giữ nút Recents) với một app khác.
2. Kéo thanh chia để launcher co nhỏ lại rồi lớn lại, không thao tác gì khác trong app.

Kết quả mong đợi: icon dock giữ đúng tỉ lệ với icon lưới ở mọi kích thước (không bị méo/lệch cỡ) trong lúc kéo. Chưa tick nếu chưa người dùng xác nhận.

- [x] **Bước 4: Commit**

```powershell
git add app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt
git commit -m "fix: recompute dock cell size on home root resize"
```

### Addendum (phát sinh khi kiểm chứng tay Bước 3, 2026-10-27): bỏ sàn 48dp của icon

Kiểm chứng tay Bước 3 lộ ra vấn đề ngoài phạm vi ban đầu: ở split-screen hẹp, icon KHÔNG co nhỏ mượt theo
`cellHeight` — các hàng bị cắt/chồng lên nhau. Nguyên nhân: `GridMetrics.measure()` kẹp `iconSizeRaw` ở sàn
`ICON_MIN_DP = 48` (quyết định đã chốt ở M3 cho vùng chạm accessibility, xem `M3-icon-grid.md` mục 165), nên khi
`cellHeightRaw` co xuống dưới mức 48dp + nhãn + khoảng cách, icon không co tiếp theo được, tràn ra ngoài ô.

**Ruling (người dùng quyết định, 2026-10-27):** bỏ sàn `ICON_MIN_DP` (48dp) khỏi phép tính kích thước icon, cho
icon co tự do theo tỉ lệ còn lại của ô. Sàn 48dp cũ chỉ còn tác dụng ở điều kiện `cellWidthRaw < minIconPx` (ẩn
cả lưới khi quá hẹp theo chiều rộng — không đổi). Thêm hằng số kỹ thuật mới `ICON_SAFETY_MIN_DP = 8` chỉ để
icon không bao giờ co về đúng 0px sau khi làm tròn (tránh crash `Bitmap.createBitmap`/`createScaledBitmap`),
không còn mang ý nghĩa accessibility 48dp nữa. Đã thực hiện trực tiếp trong `GridMetrics.kt` (cùng phạm vi file
với Task 4 Bước 1) — xem `GridMetrics.kt` sau đoạn `ICON_MAX_DP` và trong `measure()`.

**Chi phí nếu quyết định này sai:** nếu sau này cần giữ sàn 48dp cho máy có màn hình thường (không split-screen)
vì lý do accessibility, sẽ cần thêm điều kiện theo kích thước màn hình thật (không chỉ theo `cellHeightRaw`) —
việc undo chỉ là đổi `safetyMinIconPx` lại thành `minIconPx` ở một dòng, không ảnh hưởng kiến trúc.

- [x] **Kiểm chứng tay lại Bước 3 trên Vsmart Live 4 (split-screen) sau khi bỏ sàn 48dp**

Kết quả mong đợi: icon co nhỏ mượt theo tỉ lệ khi kéo thanh chia hẹp dần, không còn hàng bị cắt/chồng lên dock.
Chưa tick nếu chưa người dùng xác nhận.

---

## Task 3: System bar trong suốt, đổi màu icon theo sáng/tối

**Files:**
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt`

**Interfaces:**
- Consumes: không có mới.
- Produces: `HomeActivity.applySystemBarStyling()` (private, gọi 1 lần từ `onCreate`).

- [x] **Bước 1: Viết `applySystemBarStyling()` và gọi trong `onCreate()` ngay sau `applySystemBarInsets()`**

```kotlin
import android.content.res.Configuration
import android.os.Build
import androidx.core.view.WindowInsetsControllerCompat

private fun applySystemBarStyling() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, binding.root).apply {
        isAppearanceLightStatusBars = !isNightMode
        isAppearanceLightNavigationBars = !isNightMode
    }
}
```

`isAppearanceLightStatusBars = true` nghĩa là icon TỐI (cho nền sáng); vì vậy đảo dấu với `isNightMode`. Chạy lại được an toàn (không có state tích lũy), vì đây gọi 1 lần ở `onCreate` — Activity tái tạo lại khi đổi theme (Review Focus 1) nên không cần tự lắng nghe đổi `uiMode`.

- [x] **Bước 2: Kiểm chứng tay trên Vsmart Live 4 (Android 11)**

1. Cài bản mới, mở launcher ở chế độ sáng (mặc định hệ thống).
2. Quan sát thanh điều hướng: kỳ vọng **trong suốt, thấy ảnh nền xuyên qua**, không còn trắng đặc (so với quan sát cũ ghi ở AGENTS.md mục M3 Task 5).
3. Bật Dark mode trong Cài đặt hệ thống, mở lại launcher (hoặc để app tái tạo nếu đang mở).
4. Quan sát icon thanh trạng thái/điều hướng đổi màu hợp lý (sáng ↔ tối) theo theme.

Kết quả mong đợi: cả 2 chế độ, thanh hệ thống trong suốt và icon đọc được trên nền. Ghi rõ nếu ROM Vinsmart vẫn tự vẽ nền riêng (suy luận chưa kiểm chứng ở AGENTS.md) — nếu vẫn còn, báo lại để chẩn đoán tiếp theo đúng quy trình AGENTS.md (không tự đoán vòng 2).

- [x] **Bước 3: Commit**

```powershell
git add app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt
git commit -m "fix: make system bars transparent and theme-aware"
```

---

## Task 4: Dock có hàng riêng, căn giữa dock, nút mặc định lên góc phải, polish margin

> **Bản viết lại 2026-10-27 (người dùng duyệt).** Bản cũ dùng `bottom_bar` gói indicator + dock với tỉ lệ 6:1 và `DOCK_ROW_WEIGHT`; chiều cao dock suy ra từ `Cells` trong khi chiều cao `bottom_bar` do layout quyết định, cộng indicator 6dp + 12dp + margin 16dp + inset thì dock tràn lên hàng 6 khoảng 25dp. Bản này bỏ sự phụ thuộc đó: chiều cao dock do layout (weight) quyết định, icon dock dùng `Cells` của pager và nằm giữa dock, nên không bao giờ tràn.

**Thiết kế đã duyệt:**
- `home_content` (LinearLayout dọc) thay `bottom_bar`: `home_pager` weight 6 (= `GridMetrics.DEFAULT_ROWS`), `page_indicator` wrap_content, `dock_view` weight 1. Margin top/bottom của `home_content` = inset thanh hệ thống (B2: inset áp cho view con, không padding root). Inset top cũng sửa luôn lỗi hàng 1 đè dưới thanh trạng thái.
- `Cells` của dock đo từ chính `home_pager` (cùng công thức với `IconGridView`). Pager và dock ẩn bằng `INVISIBLE` (không phải `GONE`) khi Loading/Empty để pager giữ kích thước đã layout (bài học M3 Task 5 — xem AGENTS.md). Listener đổi kích thước chuyển từ `home_root` sang `home_pager`.
- Dock căn giữa: nhóm n icon nằm giữa chiều rộng dock (n bất kỳ, 0–4); theo chiều dọc căn theo nội dung thật (icon + khoảng cách + nhãn), không theo hộp ô. Nền dock giữ nguyên chiều rộng (A10). Việc này **chưa có ở task nào trước** (`DockView.onLayout` cũ đặt icon từ `paddingLeft` sang phải).
- Nút "Đặt làm launcher mặc định" chuyển lên `top|end` (vẫn là tạm). Nó đè lên icon góc trên phải hàng 1 cho tới khi launcher là mặc định — chấp nhận vì chỉ hiện khi chưa là mặc định.
- Margin/padding: số đo mới vào `dimens.xml` làm giá trị khởi điểm, chỉnh lại sau khi xem trên máy.

**Files:**
- Modify: `app/src/main/res/layout/activity_home.xml`, `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt`, `.../view/IconCellView.kt`
- Không đổi `GridMetrics.kt` (không còn `DOCK_ROW_WEIGHT`).

**Interfaces:**
- Consumes: `GridMetrics().measure(...)` (đã có), `IconCellView.bind(...)`.
- Produces: `IconCellView.contentHeight(): Int` (icon + khoảng cách + chiều cao nhãn, 0 nếu chưa bind) — `DockView.onLayout` dùng để căn giữa theo chiều dọc.

- [x] **Bước 1: Thêm số đo mới vào `dimens.xml`**

```xml
<dimen name="home_indicator_margin_vertical">8dp</dimen>
<dimen name="home_dock_margin_horizontal">8dp</dimen>
<dimen name="home_dock_margin_bottom">8dp</dimen>
<dimen name="home_set_default_margin_top">8dp</dimen>
<dimen name="home_set_default_margin_end">16dp</dimen>
```

`home_dock_margin_horizontal` ≤ padding lưới (8dp = `GridMetrics.PADDING_DP`): với dock đủ 4 icon, nhóm icon căn giữa vẫn thẳng cột với lưới phía trên. Lớn hơn 8dp thì nhóm icon rộng hơn dock và làm lệch.

- [x] **Bước 2: Dựng lại `activity_home.xml`**

Thay `home_pager` và `bottom_bar` bằng:

```xml
<LinearLayout
    android:id="@+id/home_content"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- weight 6 = GridMetrics.DEFAULT_ROWS, dock weight 1: chỉnh cùng nhau nếu đổi số hàng -->
    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/home_pager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="6" />

    <gambi.global.os26launcher.ui.home.view.PageIndicatorView
        android:id="@+id/page_indicator"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="@dimen/home_indicator_margin_vertical"
        android:layout_marginBottom="@dimen/home_indicator_margin_vertical" />

    <gambi.global.os26launcher.ui.home.view.DockView
        android:id="@+id/dock_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginHorizontal="@dimen/home_dock_margin_horizontal"
        android:layout_marginBottom="@dimen/home_dock_margin_bottom" />

</LinearLayout>
```

Đổi `btn_set_default`: `layout_gravity="top|end"`, `layout_marginTop="@dimen/home_set_default_margin_top"`, `layout_marginEnd="@dimen/home_set_default_margin_end"`, bỏ `layout_marginBottom`. Các view khác (`backdrop_image`, `home_status`, `grid_loading`, `grid_empty_message`, `empty_state`) giữ nguyên.

- [x] **Bước 3: Sửa `HomeActivity`**

- `applySystemBarInsets()`: bỏ `bottomBar` và margin dưới của `btnSetDefault`; thêm inset top cho `btnSetDefault`, inset top + bottom cho `homeContent` (cùng pattern margin gốc + inset).
- Thay listener của `homeRoot` bằng listener trên `homePager` (cùng điều kiện `sizeChanged`).
- `updateDockCells()` đo từ `binding.homePager.width/height` (không còn `homeRoot`).
- `render()`: pager, indicator, dock đổi `isVisible` thành `View.VISIBLE`/`View.INVISIBLE` theo `gridReady`.

- [x] **Bước 4: `IconCellView.contentHeight()` và `DockView` căn giữa**

`DockView.onMeasure` dùng chiều cao do layout cấp (không còn `c.cellHeight`); `cells == null` vẫn đo 0×0 như cũ (Review Focus 5). `onLayout`: nếu không có icon thì không đặt gì (nền dock vẫn hiện — A10, **không** ẩn dock khi trống); `startX = (width - groupWidth) / 2` với `groupWidth = n*cellWidth + (n-1)*gutterX`; `top = (height - maxContentHeight) / 2`; mỗi ô `layout(left, top, left+cellWidth, top+maxContentHeight)`.

- [x] **Bước 5: Kiểm chứng tay trên Vsmart Live 4**

1. Hàng 1 không còn nằm dưới thanh trạng thái; hàng 6 hiện đủ icon + nhãn, dock không che.
2. Icon dock cùng cỡ icon lưới, dòng cột thẳng với lưới khi dock đủ 4 icon; icon nằm giữa dock theo cả hai chiều.
3. Giả lập dock có 3, 2, 1, 0 icon (tạm sửa `PickDefaultDockUseCase` hoặc cắt `state.dock` trong `render()`, hoàn nguyên sau): nhóm icon luôn ở giữa, nền dock giữ nguyên chiều rộng (A10).
4. Nút đặt mặc định ở góc trên phải, dưới thanh trạng thái (đổi launcher mặc định sang app khác để nút hiện).
5. Split-screen kéo thanh chia nhỏ hết mức (Review Focus 5): không crash, dock không lệch cỡ so với lưới; lưới/dock có thể tự ẩn (0×0) như hành vi cũ.
6. Xem tổng thể margin/padding mới; ghi lại chỗ nào cần chỉnh `dimens.xml`.

Chưa tick nếu chưa người dùng xác nhận bằng mắt trên máy thật.

- [x] **Bước 6: Commit**

```powershell
git add app/src/main/res/layout/activity_home.xml app/src/main/res/values/dimens.xml app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt app/src/main/java/gambi/global/os26launcher/ui/home/view/IconCellView.kt
git commit -m "feat: give dock its own row, center dock icons, polish margins"
```

