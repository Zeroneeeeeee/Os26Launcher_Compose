# Launcher Android hoạt động như thế nào — lý thuyết nền

Tài liệu lý thuyết tổng quát, không gắn với 1 bug cụ thể (khác với [android-task-affinity.md](android-task-affinity.md) — tài liệu đó là ca bệnh, tài liệu này là giải phẫu). Mục tiêu: hiểu **vì sao** code của dự án ở M1–M3 được viết như vậy, trước khi học thêm ở M4+ (lưu bố cục, widget, kéo-thả...).

Một launcher Android, về bản chất, chỉ là **một app bình thường** xin được đứng vào vai trò đặc biệt "màn hình chính" (HOME). Không có API launcher riêng biệt — nó dùng lại đúng những API mà app thường dùng (Activity, PackageManager...), chỉ khác ở **cách khai báo** và **một vài API dành riêng cho việc liệt kê app của người khác**.

## 1. Vì sao hệ thống biết app này là launcher

Mọi launcher phải có 1 Activity với intent-filter:

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

Đây chính là intent-filter của **mọi app thường** cũng có (để icon app hiện trong launcher của ai đó!) — khác biệt duy nhất của launcher so với "app thường có icon" là nó còn khai thêm category `android.intent.category.HOME`:

```xml
<category android:name="android.intent.category.HOME" />
<category android:name="android.intent.category.DEFAULT" />
```

Khi bấm nút Home, Android gửi `Intent(ACTION_MAIN, CATEGORY_HOME)`. Nếu có nhiều app khai category này (nhiều launcher cài cùng lúc), hệ thống hỏi người dùng chọn 1 làm **mặc định** — đó chính là màn hình `RoleManager.createRequestRoleIntent(ROLE_HOME)` mà `HomeActivity.onSetDefaultClicked()` gọi tới. "Default Launcher" không phải một cờ đặc biệt trong Manifest — nó là **một lựa chọn runtime của hệ thống**, lưu trong `RoleManager` (Android 10+) hoặc bảng preferred-activity cũ hơn. `viewModel.refreshDefaultLauncher()` ở `onResume()` đơn giản là hỏi lại `RoleManager.isRoleHeld(ROLE_HOME)` mỗi lần quay lại app, vì người dùng có thể vừa đổi lựa chọn này trong Settings.

**Launcher không cần quyền hệ thống đặc biệt để tồn tại** — bất kỳ app nào khai đúng intent-filter đều "ứng cử" được. Thứ launcher cần mà app thường không cần, là quyền **nhìn thấy danh sách app khác** (mục 2).

## 2. Vì sao launcher dùng `LauncherApps`, không dùng `PackageManager.getInstalledApplications()`

Một app thường gọi `getInstalledApplications()` sẽ KHÔNG thấy đầy đủ app khác, kể từ Android 11 (package visibility, mục 3). Nhưng dù bỏ qua giới hạn đó, API này vẫn sai công cụ cho việc launcher cần làm, vì 2 lý do:

- **Một app có thể có nhiều "icon màn hình chính".** Ví dụ app có 2 activity đều khai `CATEGORY_LAUNCHER` (hiếm nhưng hợp lệ — ví dụ app có chế độ "Work" và "Personal" riêng icon). `getInstalledApplications()` trả về *app* (1 dòng/package), không trả về *activity nào được bấm khi chạm icon*.
- **Multi-user / hồ sơ công việc (work profile).** Một package có thể cài trên nhiều "người dùng" khác nhau trong cùng máy (chủ máy + hồ sơ công ty do MDM quản lý) — launcher cần hiện **2 icon riêng** cho cùng 1 app, phân biệt theo `UserHandle`.

`LauncherApps` (từ API 21) được thiết kế đúng cho việc này:

- `getActivityList(packageName, user): List<LauncherActivityInfo>` — trả về đúng **những activity có icon màn hình chính**, cho một `UserHandle` cụ thể. Dự án gọi với `packageName = null` để lấy *tất cả* app của 1 user.
- `LauncherActivityInfo` gói sẵn `ComponentName`, `UserHandle`, và có hàm `getLabel()`/`getIcon(density)` riêng — khác `ApplicationInfo.loadLabel()`/`loadIcon()` của `PackageManager`, vì phải tính đúng theo *activity*, không phải theo *app*, và theo đúng *user* đó (icon của work-profile app có thêm badge góc dưới, launcher không cần tự vẽ badge — `LauncherActivityInfo.getBadgedIcon()` đã làm sẵn).
- `getProfiles(): List<UserHandle>` — liệt kê user hiện có (chủ máy + mọi work profile). Launcher phải lặp qua **từng profile** rồi gọi `getActivityList` riêng cho từng cái. Dự án hiện (M3) chỉ xử lý `Process.myUserHandle()` — work profile để M4+.
- `register/unregisterCallback` — nhận thông báo cài/gỡ/cập nhật app theo thời gian thực (`onPackageAdded`, `onPackageRemoved`, `onPackageChanged`), thay cho việc tự `registerReceiver` broadcast `PACKAGE_ADDED`/`REMOVED` (cách cũ, vẫn hoạt động nhưng `LauncherApps` xử lý đúng theo user profile hơn). Đây là nguồn phát `AppRepository.apps: Flow<List<AppItem>>` mà `HomeViewModel.init` collect.
- `startMainActivity(component, user, sourceBounds, options)` — cách launcher **mở app người khác đúng chuẩn**, thay vì tự dựng `Intent` rồi `startActivity()`. Nó tự thêm đúng cờ (`FLAG_ACTIVITY_NEW_TASK`...) và xử lý đúng `UserHandle` (mở app ở work profile cần API khác với mở app ở profile chính, `startMainActivity` che giấu sự khác biệt này).

`ComponentKey` trong domain layer (`packageName + className + userSerial`) chính là bản tự định nghĩa, gọn hơn `(ComponentName, UserHandle)` của Android — dùng `userSerial` (số ổn định, từ `UserManager.getSerialNumberForUser`) để so sánh/lưu trữ được thay vì giữ `UserHandle` (object runtime, không nên lưu lâu dài/serialize).

## 3. Package visibility (Android 11+) — vì sao có khối `<queries>`

Từ Android 11 (API 30), một app **không tự động thấy** mọi app khác qua `PackageManager`/`LauncherApps` nữa, trừ khi khai báo trước app đó (hoặc 1 tập hợp theo intent) trong `<queries>` ở Manifest. Đây là giới hạn bảo mật chống app thu thập danh sách app cài trên máy để fingerprint người dùng.

**Ngoại lệ quan trọng cho launcher**: app có `CATEGORY_HOME` + đang là launcher mặc định được **tự động miễn trừ hoàn toàn** khỏi giới hạn này — nó luôn thấy đủ mọi app, không cần khai `<queries>` gì cho mục đích liệt kê qua `LauncherApps.getActivityList()`. Đây là lý do `AppRepository`/`LauncherApps` của dự án **không cần** `<queries>` để liệt kê app ra lưới.

Vậy khối `<queries>` trong `AndroidManifest.xml` của dự án dùng để làm gì? Để một thứ khác hẳn: **`PackageManager.resolveActivity(intent, ...)`** — tìm xem *app nào* xử lý một Intent cụ thể (ví dụ "app nào xử lý ACTION_DIAL" để chọn app Phone cho dock). Hàm `resolveActivity` **không** được miễn trừ như launcher-liệt-kê ở trên — nó vẫn bị giới hạn visibility bình thường, trừ khi:

- Intent đó thuộc nhóm **tự động miễn trừ** (danh sách cứng của hệ thống): gồm `ACTION_DIAL`/`ACTION_DIAL` nhóm gọi điện, `CATEGORY_APP_MESSAGING`... — đây là lý do vai trò PHONE và MESSAGES của dock hoạt động **không cần** khai `<queries>`.
- Intent **không** thuộc nhóm đó (như `ACTION_VIEW` + `BROWSABLE`, hay `INTENT_ACTION_STILL_IMAGE_CAMERA`) — bắt buộc phải khai trước trong `<queries>` dạng `<intent>` khớp action/category/data, nếu không `resolveActivity` luôn trả `null` dù app đó có cài. Đây chính là lỗi BROWSER/CAMERA gặp ở M3 Task 4, fix bằng cách thêm 2 khối `<intent>`.

Quy tắc chung khi gặp lỗi "tưởng app có cài mà code không thấy": luôn phân biệt **"liệt kê toàn bộ app" (launcher làm được, miễn trừ)** và **"hỏi app nào xử lý 1 Intent cụ thể" (bị giới hạn visibility như app thường, phải khai `<queries>`)**. Xem thêm bẫy thứ 2 của cùng tính năng này (một `resolveActivity` có thể trả về activity "trung gian" khác activity launcher thật) ở `docs/AGENTS.md`, mục "Dock mặc định".

## 4. Task, back stack và vì sao launcher không được như app thường

Người dùng bấm Home rất nhiều lần/phút, và kỳ vọng Home luôn **đúng là Home** — không có "màn hình dở dang" nào bên trong launcher để quay lại bằng Back. Hai thuộc tính Activity làm việc này:

- `launchMode="singleTask"` — chỉ 1 instance `HomeActivity` tồn tại trong toàn hệ thống, mãi mãi là root của Task riêng của nó.
- `android:clearTaskOnLaunch="true"` — mỗi khi Task của launcher được đưa lên lại (ví dụ bấm Home khi đang ở app khác), mọi Activity nằm trên root trong Task đó bị dọn sạch, chỉ còn lại `HomeActivity`.

Hệ quả thiết kế: `onBackPressedDispatcher.addCallback(this) { }` (callback rỗng) ở M1 — vì launcher **không có nơi nào để "lùi lại"**; nếu không chặn, Back sẽ làm hệ thống đưa Task launcher ra nền rồi tự mở lại chính nó (vì nó luôn là HOME), tạo cảm giác "app vừa đóng vừa mở lại" rất khó hiểu cho người dùng.

Cơ chế `taskAffinity` (độc lập với 2 thuộc tính trên, nhưng cùng chi phối việc "Activity nào vào Task nào") được giải thích chi tiết, kèm 1 bug thật đã gặp, ở tài liệu riêng: [android-task-affinity.md](android-task-affinity.md).

## 5. Vẽ icon và tải nhãn — vì sao chạy nền, vì sao song song có giới hạn

`LauncherActivityInfo.getIcon(density)` và `getLabel()` đều là **lệnh I/O/CPU đồng bộ, có thể chậm**: đọc icon nghĩa là decode 1 file ảnh (hoặc dựng icon adaptive 2 lớp foreground/background), đọc nhãn có thể phải load resource string theo locale hiện tại. Gọi trên main thread cho hàng chục/hàng trăm app sẽ giật khung hình rõ rệt lúc mở launcher — đây là lý do toàn bộ việc tải này chạy trong `viewModelScope`/`lifecycleScope` (Coroutines), không gọi trực tiếp lúc bind view.

Việc tải **song song có giới hạn** (dùng `Semaphore(n)` thay vì `Dispatchers.IO` không giới hạn) tránh 2 vấn đề: (1) mở hàng trăm coroutine I/O cùng lúc làm nghẽn thread pool chung của `Dispatchers.IO` (ảnh hưởng mọi I/O khác trong app, không riêng việc tải icon), và (2) một số ROM có hành vi lạ nếu gọi `PackageManager`/`LauncherApps` dồn dập (tài liệu đo cụ thể số liệu trên Vsmart Live 4 ở `docs/AGENTS.md`).

Nhãn (label) và icon tải **tách rời nhau** (không gộp vào 1 coroutine/app) vì tốc độ khác hẳn — nhãn gần như tức thời (đọc string), icon chậm hơn nhiều (decode ảnh) — tách ra để UI hiện nhãn ngay, icon "lấp dần vào" sau, cảm giác mượt hơn so với chờ cả hai cùng xong rồi hiện 1 lần.

## 6. Dựng lưới icon bằng custom `ViewGroup` — vì sao không dùng `GridLayout`/`RecyclerView.GridLayoutManager`

Lưới icon của 1 launcher có vài yêu cầu khó khớp với `GridLayout` sẵn có của Android hoặc `RecyclerView`:

- **Kích thước ô phải tuyệt đối cố định và chia đều theo màn hình** (không phụ thuộc nội dung icon/nhãn dài ngắn) — `GridLayout` co giãn theo content, phải ép bằng `layout_columnWeight` rất cồng kềnh.
- **Icon + nhãn 2 dòng dưới icon** là 1 đơn vị vẽ duy nhất, không phải 2 View lồng nhau — `IconCellView` tự `onDraw()` vẽ cả icon (`Bitmap`) và nhãn (`StaticLayout`) trong 1 lần, nhẹ hơn đáng kể so với `ImageView` + `TextView` lồng trong `FrameLayout` nhân với hàng chục ô.
- **`RecyclerView.GridLayoutManager`** phù hợp cho danh sách *cuộn 1 chiều, số lượng động*. Lưới icon launcher thì ngược lại: **số ô mỗi trang cố định** (ví dụ 4×6), **không cuộn trong trang** — cuộn xảy ra *giữa các trang* (bằng `ViewPager2`), không *trong* 1 trang. Dùng `GridLayoutManager` cho use-case này phải tắt cuộn, ép `wrap_content`... phức tạp hơn tự viết `onMeasure`/`onLayout` thủ công.

Chi phí của việc tự viết: phải tự quản lý `MeasureSpec` đúng (bẫy import `android.view.View.MeasureSpec` thay vì `android.view.ViewGroup.MeasureSpec` — 2 class riêng, cùng tên hàm `makeMeasureSpec`/`getSize`, dễ import sai mà vẫn compile được vì cả 2 cùng có các hàm đó), và phải tự `invalidate()`/`requestLayout()` đúng lúc dữ liệu đổi (không có DiffUtil lo cho mình như `RecyclerView.Adapter`).

## 7. Nhiều trang (`ViewPager2`) và vì sao giữ trang đang xem khi dữ liệu đổi

`ViewPager2` dùng `RecyclerView` bên trong (khác `ViewPager` cũ dùng `PagerAdapter` tự chế) — nên nhận `ListAdapter` + `DiffUtil` như `RecyclerView` thường, và **submit danh sách mới không tự về trang 0** — nhưng **cũng không tự giữ nguyên trang nếu số trang đổi** (ví dụ gỡ 1 app làm số trang từ 4 xuống 3, đang xem trang 3 (index 3, tức trang thứ 4) sẽ lệch ra ngoài phạm vi hợp lệ). Đây là lý do `render()` luôn tự lưu `currentItem` trước khi `submitList`, rồi `coerceIn(0, size - 1)` đặt lại sau khi DiffUtil áp xong (callback `commitCallback` của `submitList`) — nếu không làm bước này, mỗi lần `apps` Flow phát lại (cài/gỡ app bất kỳ lúc nào, không chỉ do người dùng bấm gì) sẽ làm `ViewPager2` **nhảy về trang người dùng không mong muốn**.

## 8. `StateFlow` và phân biệt "giá trị khởi tạo" với "dữ liệu thật đầu tiên"

`stateIn(scope, SharingStarted.Lazily, initialValue)` (hoặc tương tự) luôn phát ra `initialValue` *ngay lập tức* cho bất kỳ ai collect, **trước khi** nguồn dữ liệu thật (ví dụ `LauncherApps` liệt kê xong) kịp phát lần đầu. Nếu `initialValue` là `emptyList()`, và UI chỉ nhìn "`pages.isEmpty()` → hiện màn Empty", UI sẽ **nhấp nháy màn "Không có app nào" trong khoảng thời gian rất ngắn** trước khi dữ liệu thật tới — sai hoàn toàn về ý nghĩa (không phải máy không có app, chỉ là chưa đọc xong).

Giải pháp của dự án là cờ `gridFirstEmissionSeen: Boolean` (không phải field của `StateFlow`, chỉ là biến thường trong `ViewModel`, đổi 1 lần từ `false` sang `true` ngay khi `collect` nhận **lần phát đầu tiên** từ chính nguồn — bất kể giá trị đó có rỗng hay không): `GridStatus.Loading` chỉ hiện khi cờ còn `false`; sau lần phát đầu tiên, `pages.isEmpty()` mới được hiểu là "Empty" thật. Đây là mẫu hình chung cho mọi `Flow` có khả năng phát giá trị-giữ-chỗ trước dữ liệu thật — không riêng launcher.

## 9. Ảnh nền (wallpaper) — vì sao launcher tự vẽ `ImageView`, không dùng `WallpaperManager` hiện có

Android có `WallpaperManager` quản lý ảnh nền **toàn hệ thống** (hiện sau mọi app trong suốt, không riêng launcher). Dự án này (xem M1) đọc/đặt ảnh qua `WallpaperManager.setBitmap()`/`getDrawable()` cho việc *đặt* ảnh nền hệ thống, nhưng **tự vẽ `ImageView` riêng** (`backdrop_image`) trong `HomeActivity` để hiển thị — vì launcher cần kiểm soát hoàn toàn thời điểm/hiệu ứng hiển thị ảnh (ví dụ để tính `centerCrop` đúng theo kích thước view thật — xem `onScreenSize`/`doOnLayout` trong `HomeActivity`), trong khi ảnh nền hệ thống qua `WallpaperManager` chỉ hiện "phía sau" mọi app một cách hoàn toàn thụ động, launcher không có quyền can thiệp cách nó được crop/hiện.

## 10. Vì sao "Default Launcher" ảnh hưởng tới UI (`btn_set_default`, `home_status`)

`canRequestDefaultLauncher`/`isDefaultLauncher` trong `HomeUiState` không phải trạng thái launcher *tự quyết định* — nó luôn đọc lại từ hệ thống (`RoleManager`/`PackageManager`) mỗi lần `onResume()`. Lý do UI cần biết trạng thái này: launcher **chạy được bình thường dù chưa là mặc định** (người dùng có thể mở app này qua icon trong launcher khác để "xem thử" trước khi chọn làm mặc định) — nhưng một số hành vi (ví dụ `clearTaskOnLaunch`, hay việc nhận `ACTION_MAIN`+`CATEGORY_HOME` khi bấm nút Home) **chỉ có ý nghĩa đầy đủ khi đã là mặc định**. Nút "Đặt làm mặc định" tồn tại để dẫn người dùng qua đúng flow hệ thống (`RoleManager.createRequestRoleIntent`), launcher không tự đặt mình làm mặc định được (đây là quyết định của người dùng, qua UI hệ thống, không qua API launcher gọi thẳng).

## Tổng kết — bản đồ API theo chức năng

| Việc cần làm | API chính | Vì sao không dùng API "app thường" |
|---|---|---|
| Biết mình có được chọn là Home không | `RoleManager` (API 29+) | Không phải cờ Manifest, là lựa chọn runtime của người dùng |
| Liệt kê toàn bộ app có icon | `LauncherApps.getActivityList()` | Miễn trừ package-visibility; đúng đơn vị "activity có icon", đúng theo từng `UserHandle` |
| Mở app người khác | `LauncherApps.startMainActivity()` | Tự xử lý đúng cờ Intent + `UserHandle`, launcher không tự dựng `Intent` + `startActivity` thô |
| Tìm app xử lý 1 vai trò (gọi điện, camera...) | `PackageManager.resolveActivity()` | Không miễn trừ visibility như trên — cần `<queries>` trừ vài intent tự miễn trừ |
| Theo dõi cài/gỡ app theo thời gian thực | `LauncherApps.registerCallback()` | Xử lý đúng theo `UserHandle`, thay broadcast receiver thủ công |
| Hiện/đặt ảnh nền | `WallpaperManager` (đặt) + `ImageView` tự vẽ (hiện) | Launcher cần tự kiểm soát hiệu ứng hiển thị, không thụ động như app khác |

Khi gặp 1 hành vi lạ ở M4+ (lưu bố cục, kéo-thả, widget, folder...), câu hỏi đầu tiên nên là: **đây là hành vi của View/ViewModel tự viết (debug như app thường), hay là hành vi đặc thù do launcher đứng ở vai trò HOME (đọc lại mục này trước khi đoán)?**
