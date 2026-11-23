# FX5 — Icon và nhãn sau khi app cập nhật (A4, B7, B8, B9, C2, C10, C11) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (điều phối chạy qua adb). Lệnh viết cho CMD/PowerShell (phần chạy trên máy là `sh`).

**Nhánh:** `fix/fx5-icons-labels` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX5-icons-labels` · **PR:** #36 (draft) · **Bắt đầu:** 2026-11-11 (đợt 2, cắt từ `main` `2bac922` sau khi FX1–FX4 và FX6 merge) · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review (PR #36 ready)
**Khóa:** không (không đổi schema Room, không thêm khóa DataStore, không đổi Manifest)

**Goal:** (1) App cập nhật icon thì icon mới hiện ở mọi nơi trong vài giây, không cần khởi động lại launcher: Home, dock, thư mục, App Library, tìm kiếm (A4, B7). (2) Kiểu icon Tô màu không còn sửa drawable dùng chung của hệ thống (B8). (3) Đổi ngôn ngữ hệ thống thì nhãn và thứ tự sắp xếp nạp lại ngay trong process đang chạy, và cache nhãn không mắc kẹt ở header cũ (B9). (4) Cache icon trên đĩa xóa theo lần dùng gần nhất và dọn file của APK cũ (C2). (5) App trong hồ sơ công việc có huy hiệu; thêm, bỏ, khóa, mở khóa hồ sơ thì danh sách nạp lại và hàng của hồ sơ đã bỏ được dọn (C10). (6) Gói icon đã bỏ chọn được giải phóng (C11). (7) `IconCache` trả RAM khi process vào danh sách nền, nếu số đo cho thấy đáng (phần thêm từ review FX1).

**Architecture:** Gần như toàn bộ ở `data/apps/*`. Mấu chốt là một **dấu phiên bản APK** (`sourceDir`, đã có sẵn trong `loadApps` và đã dùng cho cache nhãn LC, cache icon đĩa IC) đi vào ba chỗ: `AppItem.versionStamp` (nên `AppItem` mới khác `AppItem` cũ, mọi `DiffUtil` và so sánh trạng thái tự thấy thay đổi), khóa của `IconCache` (RAM) và khóa của các map bitmap trong từng view (`AppIconKey = ComponentKey + stamp`). `IconLoader` lấy stamp từ registry `sourceDirs` của data source, nên chữ ký `loadIcon(key, sizePx)` ở 7 chỗ trong UI **không đổi**. `AppRepositoryImpl` so stamp cũ/mới sau mỗi lần nạp để dọn RAM (và đĩa, Task 6) đúng package vừa đổi, và so tập user để phát sự kiện `REMOVED` giả cho hồ sơ vừa bị bỏ (đi qua `LayoutRepositoryImpl.init` sẵn có, không sửa `data/layout/*`). Một `BroadcastReceiver` đăng ký động (`ACTION_LOCALE_CHANGED` + năm `ACTION_MANAGED_PROFILE_*`) trong `LauncherAppsDataSource` hợp vào luồng nạp lại của `apps`. Huy hiệu hồ sơ công việc vẽ lên **bitmap đã render** bằng `PackageManager.getUserBadgedIcon`. `IconCache` tự đăng ký `ComponentCallbacks2` theo mẫu FX1 dùng cho `GlassEnvironment`.

**Tech Stack:** không thêm thư viện. API dùng: `LauncherApps`, `UserManager`, `PackageManager.getUserBadgedIcon` (API 21), `Drawable.mutate()`, `Context.registerReceiver` qua `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (đúng mẫu `WidgetTicker`), `Context.registerComponentCallbacks`, `File.setLastModified`.

**Spec:** Audit `docs/report/Audit-Report-2026-11-10.md` mục A4, B7, B8, B9, C2, C10, C11 + "Quyết định của chủ dự án (2026-11-11)"; `docs/knowledgements/IC-icon-disk-cache.md` (mục 8: `install -r` chưa từng thử), `LC-label-cache.md`, `G2b-icon-styles.md`, `IP-icon-packs.md`, `TL2-icon-shape.md`; `docs/plans/M2-app-list.md` (hồ sơ công việc hoãn); `docs/lessons/icons-and-app-list.md`, `startup-performance.md`, `permissions-and-device-testing.md`.

## Đã đối chiếu với code hiện tại (`b1af810`; nhánh được cắt sau FX2/FX4 nên số dòng sẽ lệch, tìm theo tên hàm)

| Mục | Điều audit nói | Kết quả đọc code |
|---|---|---|
| A4 | Ba view Home giữ `HashMap<ComponentKey, Bitmap>` riêng, chỉ xóa khi đổi cỡ icon | Đúng: `IconGridView.kt:258`, `bindAllCells` `:1146-1187`; `DockView.kt:117`, `:483-503`; `FolderPanelView.kt:389` (`AppsGrid`), `bindAll` `:455-461`. `IconGridView` còn dùng chung map cho icon con của thư mục (`bindFolderCell` `:1236-1260`). Riêng `FolderPanelView.AppsGrid` **không** có `retainAll` (map chỉ bị xóa khi đổi cỡ) |
| A4 | `AppItem` mới bằng `AppItem` cũ nên adapter không bind lại | Đúng, và **còn hai tầng chặn**: (1) `HomePagerAdapter.Diff`, `DockView.setItems` (`items == this.items`), `AppsGrid.setApps` (`shown == this.apps`) so bằng `==` của data class nên không bind lại; (2) ba adapter có `if (holder.boundKey == key && holder.icon.drawable != null) return` (`SearchResultAdapter` dòng 75, `CategoryGroupPanelView.Adapter` `:290`, `AppLibraryAdapter.bindApp` `:91`): **dù** `DiffUtil` báo nội dung đổi, hàng cùng key vẫn giữ icon cũ. Vì vậy chỉ thêm stamp vào `AppItem` là chưa đủ, phải sửa điều kiện này (Task 3) |
| A4 | Xóa cache: `AppRepositoryImpl.kt:48` | Đúng: `onEach` chạy cho mọi sự kiện package, **trước** debounce, chỉ `iconCache.evict`. Không có gì báo cho view |
| A4 | `CategoryPageView` (trang Library trong Home) | Không có map icon: mỗi lần `render(groups)` nạp lại icon qua `iconLoader` (`CategoryPageView.kt:452`, `:472`), nên chỉ cần `IconCache` đúng khóa là tile Library tự mới khi `groups` phát lại (mà nó phát lại vì `AppItem` đổi) |
| B7 | `sourceDirs` làm mới cuối `loadApps`; `load()` trong khoảng đó đọc đĩa theo `sourceDir` cũ rồi `cache.put` | Đúng: `IconLoader.kt:29-47`, `LauncherAppsDataSource.kt:104-107`. Thêm: registry cập nhật **trước** khi danh sách mới được phát, nên với `AppItem` mang stamp thì "stamp của view" luôn ≤ "stamp của registry" (không có chiều ngược lại), đủ để khóa RAM nhất quán (xem "Vì sao") |
| B8 | `foreground.setColorFilter` không `mutate()` | Đúng và **là chỗ duy nhất** gắn `ColorFilter` lên một `Drawable` trong `data/apps` (`IconRenderer.kt:285`; `grep colorFilter\|mutate` chỉ thấy dòng này và `paint.colorFilter` ở `tint()` là của `Paint` cục bộ). Thêm: `foregroundIsGlyph(foreground)` (`:260`) cũng vẽ lớp này **trước** khi gắn bộ lọc, nên `mutate()` phải đặt ngay khi lấy `drawable.foreground`, không chỉ trước `setColorFilter`. Chưa tái hiện: lần thử G2b/IC "Mặc định → Tô màu → 3D → Mặc định" trên Live 4 trúng cache đĩa nên có thể che lỗi (Task 4 Step 0 thử tái hiện có xóa cache đĩa) |
| B9 | Locale đổi không nạp lại nhãn/sắp xếp | Đúng: `apps` chỉ phát khi có sự kiện package; `HomeActivity` không có `locale` trong `configChanges` (Manifest `:108`) nên Activity dựng lại, `HomeViewModel` sống sót. **Sắc thái cần ghi:** `apps` là `WhileSubscribed(5_000)` và các flow phía trên cũng thế; đổi ngôn ngữ qua màn Cài đặt thường mất hơn chục giây nên flow dừng và `onStart { emit(Unit) }` nạp lại khi quay về, tức đường thường dùng có thể đã đúng. Ca lỗi là đổi nhanh khi Home còn hiện (công cụ `app_process` của audit) hoặc quay lại trong < ~10 s (suy luận từ chuỗi 5 s + 5 s, chưa đo) |
| B9 | `LabelCache.load()` header lệch trả `null` không đặt `onDisk = null` | Đúng: `LabelCache.kt:148`; cả nhánh `catch` (`:159-166`) cũng không đặt. `scheduleSave` bỏ qua khi `entries == onDisk` (`:172`), nên nhãn mới trùng nhãn cũ (ví dụ `en-US` → `en-GB`) thì **không ghi lại** và header kẹt ở locale cũ. Tái hiện được trên Live 4 bằng cặp `en-US` ↔ `en-GB` (Task 8) |
| B9 | Danh mục Cài đặt của tìm kiếm | Đã locale-aware (`SystemSettingsRepositoryImpl` so `built.locale == tag`), không thuộc FX5 |
| C2 | `get` không chạm file; xóa theo `lastModified` | Đúng: `IconDiskCache.kt:63-87`, `trimNow` `:138-149`. **Giới hạn của "LRU theo lần đọc"** (audit không nói): trong chính phiên làm vượt trần, icon Home được ghi *đầu tiên* vẫn là file cũ nhất vì chưa có lần đọc trúng nào; LRU bảo vệ từ phiên khởi động kế tiếp (Task 6 ghi rõ và đo) |
| C10 | Icon work không huy hiệu | Đúng: `loadIconDrawable` trả `getIcon(0)` (không huy hiệu, `LauncherAppsDataSource.kt:130`); `grep getUserBadgedIcon` trong `app/src` rỗng. Khóa RAM và tên file đĩa đã có `userSerial` nên icon work/cá nhân không đè nhau |
| C10 | Không theo dõi thêm/bỏ hồ sơ | Đúng: `grep registerReceiver` chỉ thấy `WidgetTicker`. Thêm: khi hồ sơ bị bỏ, `apps` nạp lại (nếu có trigger) thì `assemble` **ẩn** hàng của user đó (không có app sống) nhưng hàng vẫn nằm trong DB giữ ô (giống ô ma A3); `catchUp` chỉ chạy lúc khởi động. `removePackages(names, serial)` dùng được cho việc dọn mà không đụng `data/layout/*` nếu có sự kiện `REMOVED` |
| C11 | `parsed` giữ `PackData` mọi gói đã chọn | Đúng: `IconPackManager.kt:39-40`, `:93-96`; `IconStyleSource` đã có `previous`/`new` trong `.onEach` (`:75-78`) nên có đủ thông tin gói trước và gói mới |
| Thêm | Không có `onTrimMemory` | Đúng (`grep onTrimMemory\|ComponentCallbacks` trong `app/src/main` rỗng; FX1 sẽ thêm một cái trong `GlassEnvironment`). `IconCache` là `LruCache` cỡ `maxMemory/8` (Live 4: 64 MB) |

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- Lỗi A4 có **hai tầng** (cache riêng của view, và các điểm chặn bind lại ở bảng trên), nên "xóa đúng khóa" ở một chỗ không đủ.
- Ràng buộc: file nóng độc quyền không đụng (`HomeActivity`, `HomeViewModel`, `LayoutReconciler`, `core/glass/*`, `DragHelper`); `data/layout/*` thuộc FX3; `ui/search/*`, `ShortcutRepositoryImpl` thuộc FX4 (merge trước); `IconGridView`/`DockView`/`FolderPanelView` do FX2 vừa sửa phần rung (merge trước). Chỉ được sửa dòng map icon ở các view, không đụng phần rung.
- Số đo sẵn có: `sourceDir` đổi mỗi lần cài trên Android 11 và 14 (LC, đo được với APK của mình, `docs/lessons/startup-performance.md`); icon từ đĩa giải mã ~1,5 ms/icon, cửa sổ icon bản release ~53 ms cho 35 icon (IC); Live 4 chạy Android 11, tức `LauncherApps.Callback` có `onPackageChanged` cho cập nhật (M2).
- `install -r` bằng APK thật **chưa từng thử** trước audit (IC mục 8); audit đã tái hiện A4 bằng APK tự dựng trên Live 4.

**A4 + B7 — dấu phiên bản đi vào đâu.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `versionStamp = sourceDir` trong `AppItem` + khóa RAM + khóa map của view** | `loadApps` gắn `sourceDir` vào `AppItem`; `IconCache` thêm stamp vào khóa; view dùng `AppIconKey(ComponentKey, stamp)` | Không trạng thái mới, không tín hiệu phải giữ đồng bộ (đúng bài học M7 "UI là hàm của dữ liệu" và LC "khóa luôn từ hệ thống"); `sourceDir` đã đọc sẵn nên không IPC thêm; sửa B7 từ gốc (khóa RAM mới); `retainAll(liveKeys)` tự dọn khóa stamp cũ | `AppItem` đổi nên `apps` phát thêm một lần mỗi khi app cập nhật (chủ đích); không bắt được thay đổi icon mà `sourceDir` không đổi (cập nhật chủ đề icon của ROM, RRO: giới hạn đã biết của IC) | **Chọn** |
| B. `lastUpdateTime` của `PackageInfo` làm stamp | Cùng khung với A nhưng stamp lấy từ `getPackageInfo` | Đổi ngay cả khi cài đè mà `sourceDir` không đổi (nếu có) | Thêm IPC mỗi package (hoặc một lệnh lấy toàn bộ `PackageInfo`, nặng hơn trên Oppo 345 app); LC/IC đã dùng `sourceDir` nên hai dấu lệch nhau | Dự phòng: chỉ dùng nếu Task 1 Step 0 cho thấy `sourceDir` **không** đổi khi `install -r` trên Live 4 |
| C. Tín hiệu "icon package đổi" | Data layer phát `Flow<Set<ComponentKey>>` sau `loadApps`; ba view và ba adapter đăng ký, xóa khóa rồi bind lại | `AppItem` không đổi nên không có lần phát thừa | Thêm API xuyên tầng và 6 người nghe; tín hiệu có thể đến trước hoặc sau danh sách mới (đua kiểu B7); view không đang thu thì mất tín hiệu; không sửa gốc B7 (khóa RAM vẫn không có dấu). Launcher3 làm kiểu này (`IconCache.updateIconsForPkg` xóa rồi nạp lại theo gói, đọc code `android11-release`) nhưng nó có cache đĩa riêng và model riêng | Không |
| D. Truyền stamp qua tham số `icon(key, stamp, sizePx)` | Bỏ registry, mọi lời gọi tự mang stamp | Tường minh nhất, hết lệch registry/danh sách | Đổi chữ ký ở 7 chỗ trong `HomeViewModel` (FX1/FX7), `SearchViewModel` (FX4), `AppLibraryViewModel`, `HomePagerAdapter`, hai controller; vùng file chen với ba nhánh khác | Không (khả thi sau này nếu registry gây lỗi) |

*Vì sao A:* hai tầng chặn của A4 đều biến mất khi dữ liệu đổi (mọi chỗ so bằng `==` hay theo khóa đều tự thấy), không phải nhớ gọi "xóa" ở sáu nơi. Lệch registry/danh sách chỉ theo một chiều vô hại: registry được cập nhật trước khi danh sách mới phát, nên view cầm `AppItem` cũ (stamp S0) có thể nhận bitmap đã render theo stamp mới S1 và cất dưới khóa S0; khi `AppItem` S1 tới, khóa S1 miss rồi nạp lại từ `IconCache` (đã có). Chiều ngược lại (view nhận icon cũ dưới khóa mới) không xảy ra vì khóa mới chỉ tồn tại sau khi registry đã mới. Nguồn: đọc code (`LauncherAppsDataSource.loadApps` thứ tự `putAll` rồi mới trả danh sách); chứng minh bằng log tạm ở Task 1.

*Đánh đổi:* `AppItem.versionStamp` mang chuỗi ~80 ký tự (dùng chung một tham chiếu với `sourceDirs`, ~30 KB cho 345 app). Xét lại sang B nếu Task 1 Step 0 cho thấy `sourceDir` đứng yên khi cập nhật.

**B9 — đổi locale kích hoạt nạp lại bằng gì.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Receiver `ACTION_LOCALE_CHANGED` thành flow, hợp vào luồng nạp lại của `apps`** | `callbackFlow` trong data source, `merge` với luồng sự kiện package trước `debounce` | Nằm trọn trong `data/apps/*` (vùng FX5); cùng receiver với năm broadcast hồ sơ công việc của C10; Launcher3 làm đúng vậy (`LauncherModel.onBroadcastIntent`: "If we have changed locale we need to clear out the labels in all apps/workspace." rồi `forceReload()`, đọc code `android11-release`) | Thứ tự giữa broadcast và việc process nhận cấu hình mới: suy luận là cấu hình đến trước (hai lời gọi oneway tới cùng process, cấu hình gửi trước broadcast) và debounce 200 ms thêm đệm; kiểm bằng Task 8 | **Chọn** |
| B. So `Locale.getDefault()` với thẻ của lần nạp cuối khi `onCreate`/`onResume` | `HomeViewModel` hỏi repository mỗi lần Activity dựng lại | Không cần receiver; chắc chắn thấy locale mới (Activity dựng sau khi cấu hình đã áp) | Phải sửa `HomeViewModel`/`HomeActivity` (độc quyền của FX1/FX2/FX7); chỉ chạy khi Activity được dựng, không bắt được đổi khi Home đang hiện ở chế độ nhiều cửa sổ | Không |
| C. `ComponentCallbacks2.onConfigurationChanged` so `newConfig.locales` | Đăng ký theo mẫu FX1 | Chạy trong process sau khi cấu hình đã áp: không đua | Cơ chế thứ hai ngoài receiver (C10 vẫn cần receiver cho hồ sơ); gọi cho mọi đổi cấu hình (xoay, sáng/tối) nên phải so thẻ locale | Dự phòng: nếu Task 8 cho thấy broadcast đến trước cấu hình |

*Đánh đổi:* nếu locale đổi mà `Locale.getDefault()` chưa kịp đổi lúc nạp, cả nhãn và Collator ở locale cũ và nằm đó tới sự kiện sau. Dự phòng nằm ở bảng; không chặn bằng vòng chờ vì có thể treo khi locale không đổi.

**B8 — `mutate()` hay `saveLayer`.** `mutate()` (chọn): đúng cách tài liệu `Drawable.mutate()` dành cho việc "sửa một drawable mà không ảnh hưởng drawable khác dùng chung `ConstantState`"; một dòng; `AdaptiveIconDrawable`, `BitmapDrawable`, `VectorDrawable` đều tự cài `mutate()`. `saveLayer` với `Paint` mang bộ lọc: không phụ thuộc cài đặt `mutate()` của từng loại drawable, nhưng tốn một lớp đệm ngoài màn hình mỗi icon (144–180 px, mỗi lần nạp) và đổi đường vẽ đã đo ở G2b/TL2. Chọn `mutate()`, `saveLayer` giữ làm dự phòng nếu một app có drawable tự định nghĩa không `mutate()` đúng (chưa gặp). Nguồn: đọc tài liệu; hành vi dùng chung `Paint` của `BitmapDrawable.BitmapState` là suy luận từ mã AOSP (chưa kiểm trên máy, xem Task 4 Step 0).

**C10 — huy hiệu vẽ ở đâu.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `getUserBadgedIcon` trên bitmap đã render** | Sau khi `IconRenderer` ra bitmap cuối, bọc bằng `BitmapDrawable`, gọi `packageManager.getUserBadgedIcon(drawable, user)` rồi lấy bitmap | Giữ nguyên đường vẽ adaptive/Tô màu/3D/gói icon vì huy hiệu đến cuối cùng; dùng đúng hình huy hiệu của hệ thống (đổi theo ROM, theo loại hồ sơ); user chính trả icon nguyên (không cần hỏi hệ thống) | Hình huy hiệu do hệ thống quyết (kích thước, vị trí); Launcher3 làm kiểu này (nhớ lại từ `BaseIconFactory.createBadgedIconBitmap`, **chưa xác minh trong phiên này**: URL mã nguồn trả 404) | **Chọn** |
| B. `LauncherActivityInfo.getBadgedIcon` trước khi render | Lấy drawable đã có huy hiệu từ framework | Một dòng | `getBadgedDrawable` của framework dựng một `BitmapDrawable` phẳng (suy luận từ mã AOSP): mất cấu trúc `AdaptiveIconDrawable` mà `IconRenderer` cần (hai lớp, Tô màu theo foreground), nên icon work sẽ vẽ bằng nhánh legacy (co nhỏ trên tấm nền) và khác icon cá nhân | Không |
| C. Tự vẽ huy hiệu bằng drawable của app | Thêm vector vào `res/drawable`, vẽ góc dưới phải | Không phụ thuộc API | Không đúng hình hệ thống, phải có tài nguyên mới và chọn kích thước; mỗi ROM một kiểu | Không |

Khóa cache: icon work đã nằm khóa riêng theo `userSerial` ở RAM và đĩa nên không cần đổi khóa RAM; tên file đĩa thêm hậu tố `|badge1` **chỉ khi** user khác user chính, để file cũ không huy hiệu của hồ sơ không bị dùng lại mà cache của user chính **không mất** (so với nâng `DIR_PREFIX` lên `v2` làm nguội cache của mọi người).

**C10 — theo dõi hồ sơ và dọn hàng.** Receiver chỉ làm một việc: **kích hoạt nạp lại**. Việc nhận ra "hồ sơ đã bị bỏ" không đọc `EXTRA_USER` (sau khi bỏ, `getSerialNumberForUser` có thể không trả được số) mà so **tập `userSerial` của lần nạp trước với lần này**, rồi xác nhận bằng `userManager.userProfiles` (loại hồ sơ đang gỡ dở, còn hồ sơ ở chế độ yên lặng). *Sửa 2026-11-11 sau kiểm Live 4:* nguồn thứ hai (`getUserForSerialNumber`) vẫn trả user lúc đang gỡ dở nên không bao giờ kết luận được, đã bỏ. User biến mất → `AppRepositoryImpl` phát `PackageEvent(REMOVED, tên package của user đó, serial)` qua `packageEvents`; `LayoutRepositoryImpl.init` đã nghe `packageEvents` và gọi `removePackages` (xóa hàng, dọn `hidden_apps`, dồn ô). Phương án thay thế: thêm `LayoutReconciler.removeUser(serial)` (sạch hơn, một câu `DELETE`) nhưng nằm ở `data/layout/*` của FX3, nên để thành **câu hỏi mở Q-A** cho điều phối; mặc định là sự kiện giả. Chế độ yên lặng (tạm dừng hồ sơ, `AVAILABLE`/`UNAVAILABLE`) **không** xóa gì: user vẫn có trong `userProfiles`, hàng chỉ ẩn khi danh sách không còn app (hành vi `assemble` sẵn có) và hiện lại khi nạp lại.

**C2 — LRU thật.** (a) `setLastModified(now)` khi đọc trúng, tối đa một lần mỗi file mỗi process, thực hiện trên luồng ghi (không nằm trên đường găng của cold start) — **chọn**. (b) Một file chỉ mục thời điểm truy cập riêng: chính xác hơn nhưng thêm một file phải giữ nguyên tử và nhất quán với thư mục. (c) Nâng trần 8 MB: chủ dự án đã duyệt 8 MB (2026-11-06), nâng là quyết định của chủ dự án và chỉ che vấn đề. Giới hạn của (a) ghi ở bảng đối chiếu: không cứu được icon Home trong đúng phiên làm vượt trần lần đầu. Phần "xóa file của dấu cũ" thực hiện khi stamp đổi (Task 6) bằng `IconDiskCache.evict(package)` có sẵn, chạy **trước** khi danh sách mới phát nên mọi file ghi theo stamp mới nằm sau lệnh xóa trong hàng đợi của luồng ghi đơn.

**C11 — quên gói cũ.** Gọi một hàm mới `IconPackManager.retainOnly(packKey)` khi `IconStyleSource` đổi gói (đã có `previous`/`new`); phương án khác (giới hạn `parsed` ở 1–2 mục kiểu LRU, hoặc `SoftReference`) phức tạp hơn mà không chính xác hơn, vì ý nghĩa của `parsed` là "gói đang dùng". Một nạp icon đang bay theo kiểu cũ có thể phân tích lại gói cũ sau khi bị quên (đua hẹp): hậu quả là một gói nằm lại tới lần đổi gói kế, chấp nhận.

**Trim `IconCache` (phần thêm).** Tài liệu `ComponentCallbacks2` (đọc 2026-11-11): từ API 34 hệ thống chỉ còn gửi `TRIM_MEMORY_UI_HIDDEN` (20) và `TRIM_MEMORY_BACKGROUND` (40); mẫu trong tài liệu giải phóng "tài nguyên dựng lại nhanh" ở `>= BACKGROUND`. Chọn: `evictAll()` ở `>= BACKGROUND`, **không** làm gì ở `UI_HIDDEN` (nút Home/mở app khác gửi mức này liên tục; xóa ở đó làm nguội cache mỗi lần quay về Home). Các view giữ bitmap riêng của ô đang có nên `evictAll` chỉ trả phần icon không còn ô nào cầm (Library, tìm kiếm, cỡ icon khác); audit C3 đo mở Library thêm ~15 MB Native không giảm sau khi đóng. Mẫu đăng ký: **`IconCache` tự đăng ký** (`@ApplicationContext` + `registerComponentCallbacks` trong `init`, đúng mẫu FX1 cho `GlassEnvironment`), không đụng `LauncherApp` hay Manifest, singleton sống suốt process nên không gỡ. Lợi ích **chưa đo**: Task 7 đo `dumpsys meminfo` trước/sau và **điều phối quyết giữ hay bỏ Task** (dưới 3 MB Native thì bỏ).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Dấu phiên bản = `sourceDir`, đưa vào `AppItem.versionStamp`, khóa `IconCache` và khóa map view (`AppIconKey`) | Claude đề xuất 2026-11-11; cơ sở: LC/IC đo `sourceDir` đổi mỗi lần cài; chờ duyệt |
| D2 | Chữ ký `loadIcon(key, sizePx)` giữ nguyên; `IconLoader` lấy stamp từ registry `sourceDirs` | Đề xuất; giữ ngoài vùng của FX1/FX4/FX7 |
| D3 | Receiver động một cái cho locale và năm broadcast hồ sơ; không khai trong Manifest | Đề xuất; process không chạy thì lần khởi động sau tự nạp (header LC khác locale → đọc đủ; hồ sơ → `catchUp`) |
| D4 | Hồ sơ bị bỏ: nhận ra bằng so tập `userSerial`, dọn qua `PackageEvent.USER_REMOVED` giả đi vào `LayoutReconciler.removeUser` (vòng 2: `REMOVED` theo package bỏ sót hàng) | Đề xuất; thay bằng `removeUser` nếu điều phối cho phép chạm `data/layout/*` (Q-A) |
| D5 | Huy hiệu vẽ lên bitmap đã render; chỉ icon (không đổi nhãn) | Đề xuất; nhãn xem câu hỏi Q1 |
| D6 | Trim: `evictAll` ở `>= TRIM_MEMORY_BACKGROUND`, giữ hay bỏ theo số đo | Đề xuất; điều phối quyết sau Task 7 |
| D7 | Phạm vi không có Task "bắt buộc kiểm tay" theo mục 4.2 | Không có giả định chỉ máy thật mới trả lời mà sai thì phải làm lại nhiều Task (xem từng Task); mọi phép đo do **điều phối** chạy và quyết |

## Ngoài phạm vi / ghi để sau

- Icon của **shortcut** (`ShortcutRepositoryImpl.iconOf`, `getShortcutIconDrawable`) của app hồ sơ công việc không có huy hiệu: file thuộc FX4, và shortcut chỉ hiện khi là launcher mặc định. Ghi để sau.
- Nhãn "Chrome (công việc)" qua `UserManager.getBadgedLabelForUser`: xem câu hỏi Q1.
- Icon "xám" của hồ sơ ở chế độ yên lặng; huy hiệu cho hồ sơ nhân bản/riêng tư (Android 15+, máy thử là Android 11).
- Thay đổi icon mà `sourceDir` không đổi (chủ đề icon của ROM, RRO, icon động theo ngày của app Lịch): giới hạn đã biết từ IC.
- Cập nhật app qua cửa hàng Play (thay vì `adb install -r`): cùng cơ chế `PackageInstaller`, suy luận chưa đo (LC đã ghi).
- `LabelCache`: không kiểm lại nhãn nền khi app đổi nhãn mà không cập nhật (D7 của LC giữ nguyên).
- A3 (app đổi tên activity khởi động): FX3. FX5 chỉ cần không làm xấu thêm: khi `className` đổi, `AppItem` mới có key mới, view coi là app mới.

## Vùng file

Đường dẫn từ `app/src/main/java/gambi/global/os26launcher/`.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/AppItem.kt` | Sửa: thêm `versionStamp`, `iconKey`; thêm `AppIconKey` | Không (A4 liệt kê trong vùng FX5) |
| `data/apps/LauncherAppsDataSource.kt` | Sửa: gắn stamp vào `AppItem`; thêm luồng trigger (receiver) và hàm kiểm tập user; `getUserBadgedIcon` | Không |
| `data/apps/IconCache.kt` | Sửa: stamp trong khóa; `ComponentCallbacks2` | Không |
| `data/apps/IconLoader.kt` | Sửa: stamp từ registry; huy hiệu; hậu tố tên file đĩa | Không |
| `data/apps/IconDiskCache.kt` | Sửa: chạm `lastModified` khi đọc trúng | Không |
| `data/apps/IconRenderer.kt` | Sửa: `mutate()` (một vài dòng trong `tintGlyphTile`) | Không |
| `data/apps/AppRepositoryImpl.kt` | Sửa: hợp trigger vào `apps`; so stamp/tập user sau `loadSorted`; `packageEvents` hợp sự kiện giả | Không |
| `data/apps/LabelCache.kt` | Sửa: `onDisk = null` khi header lệch và khi lỗi đọc | Không |
| `data/apps/IconPackManager.kt`, `data/apps/IconStyleSource.kt` | Sửa: `retainOnly`, gọi khi đổi gói | Không |
| `ui/home/view/IconGridView.kt` | Sửa **hẹp, chỉ dòng map icon**: khai báo `iconCache`, `bindAllCells` (tra, `retainAll`), `bindFolderCell`, `loadIconFor` | Dùng chung với FX2 (đã merge trước); **không đụng phần rung** |
| `ui/home/view/DockView.kt` | Sửa hẹp: `iconCache`, `bindAllCells`, `loadIconFor` | Dùng chung với FX2 (đã merge) |
| `ui/home/view/FolderPanelView.kt` | Sửa hẹp: chỉ `AppsGrid` (`iconCache`, `bindAll`, `load`) | Dùng chung với FX2 (đã merge), FX7 (sau) |
| `ui/home/view/CategoryGroupPanelView.kt` | Sửa hẹp: `Adapter.onBindViewHolder` (`boundKey` + stamp) | Không |
| `ui/library/AppLibraryAdapter.kt` | Sửa hẹp: `bindApp` | Không |
| `ui/search/SearchResultAdapter.kt` | Sửa hẹp **ba dòng**: khóa gán icon của hàng `SearchResult.App` thêm stamp | Dùng chung với FX4 (đã merge trước); không đụng `SearchViewModel` |
| `domain/model/PackageEvent.kt` | Sửa (Task 10 vòng 2): thêm `Type.USER_REMOVED` | Không |
| `data/layout/LayoutReconciler.kt`, `data/layout/LayoutRepositoryImpl.kt`, `data/layout/db/HiddenAppDao.kt` | Sửa (Task 10 vòng 2): `removeUser(serial)`, nhánh `USER_REMOVED` trong `init`, `deleteByUser`. Điều phối cho phép 2026-11-11 (FX3 đã merge, không nhánh nào đang sửa); chỉ thêm, không đổi hàm có sẵn, không đổi schema Room | Độc quyền FX3 (đã hết hiệu lực) |
| `docs/knowledgements/FX5-icons-labels.md` | Tạo (Task 11) | Không |
| `docs/report/FX5-icons-labels.md` | Sửa (nhật ký, kết quả) | Không |

Không đụng: `HomeActivity.kt`, `HomeViewModel.kt`, `core/glass/*`, `core/ui/DragHelper.kt`, `data/layout/*` (đọc để hiểu, không sửa; nếu cần phải dừng và báo, xem Q-A), `data/search/*`, `ShortcutRepositoryImpl.kt`, `domain/repository/*` (không đổi interface), `res/*` (không thêm chuỗi), `AndroidManifest.xml`, `app/build.gradle.kts`.

## Global Constraints

- Không code trước khi plan được duyệt. Mỗi Task: build `gradlew.bat assembleDebug` xanh trong worktree (nhớ chép `local.properties`), commit tiếng Anh không dấu (`fix:`/`perf:`/`refactor:`/`chore:`), một hoặc vài commit mỗi Task, cập nhật Nhật ký, dừng và báo. Stage file bằng đường dẫn tường minh (không `git add docs` hay `-A`).
- Không thêm test, không thêm thư viện. Không merge `main` vào nhánh (mục 11 của quy trình). Không cài APK, không chạy adb: điều phối làm. Máy Live 4 dùng chung với chủ dự án: điều phối xác nhận máy rảnh trước khi cài hay chạy script.
- Luật `ui → domain ← data`: `AppItem`/`AppIconKey` ở `domain` chỉ có `String` và kiểu domain, không `android.*`. UI không import `data`.
- Không đổi chữ ký `AppRepository`, không đổi schema Room, không thêm khóa DataStore, không đổi Manifest.
- Mọi lời gọi hệ thống nặng ở luồng nền (`io`); receiver chỉ `trySend`, không làm việc nặng.
- Log tạm có tag `FX5`, chỉ tên package và số đếm (không dữ liệu cá nhân), gỡ ở Task 11 bằng một commit riêng.
- Mọi số đo ghi rõ máy (Live 4), **bản debug**, ngày; không nói "người dùng thấy" từ số debug (bài học "Đừng đo khởi động trên bản debug"). Không so số giữa hai phiên cách xa nhau.
- Bẫy đã ghi trong lessons: (1) `IconGridView`/`DockView`/`FolderPanelView` giữ bitmap riêng nên `IconCache.clear()` không làm mới Home (G2b); (2) thêm một chiều vào khóa cache thì kiểm đủ ba tầng RAM/đĩa/view, và chỉ nối khóa mới khi khác mặc định để cache cũ còn dùng được (IP); (3) bitmap trong cache chỉ đọc, không `recycle` (IC); (4) alpha và trạng thái ô là hàm của dữ liệu (M7), không thêm cờ "đã bind".
- Comment code tiếng Việt theo quy ước hiện có, ghi mã mục (`A4`, `B9`...).

## Review Focus

1. **Mọi nơi giữ icon theo khóa** đều dùng khóa có stamp: `IconCache`, ba map view, `bindFolderCell`, ba adapter + `SearchResultAdapter`. `grep "iconCache\[" ` trong `ui/home/view` không còn lần nào tra bằng `.key` trần.
2. **Không đổi chữ ký `loadIcon`** và không file nào ngoài Vùng file; phần sửa trong ba view lưới chỉ là dòng map icon (đối chiếu `git diff main --stat` với phần FX2 đã merge).
3. `AppItem` equality: sau khi thêm `versionStamp`, danh sách `apps` **không phát thêm** khi không có gì đổi (stamp ổn định giữa hai lần nạp) và **phát đúng một lần** khi app cập nhật. Xem log `FX5 apps emitted`.
4. Thứ tự trong `loadSorted`: `loadApps` xong (registry mới) → so stamp → `iconCache.evict`/`iconDiskCache.evict` → trả danh sách. Không có đường nào ghi icon stamp mới trước lệnh xóa đĩa.
5. B8: `mutate()` đặt trước **cả** `foregroundIsGlyph` lẫn `setColorFilter`; không còn `setColorFilter` nào lên drawable lấy từ framework.
6. B9: receiver đăng ký/hủy đúng theo vòng đời flow (`awaitClose`), `RECEIVER_NOT_EXPORTED`; `LabelCache.load()` đặt `onDisk = null` ở **mọi** đường trả `null` (header lệch, sai version, lỗi đọc).
7. C10: hàng của **user chính** không bao giờ bị `REMOVED` giả; sự kiện giả chỉ cho user mà hệ thống xác nhận mất bằng hai nguồn; chế độ yên lặng không xóa hàng. Huy hiệu chỉ áp cho user khác user chính và không lọt vào cache của user chính (khóa RAM có `userSerial`, tên đĩa có hậu tố).
8. C2: `setLastModified` chạy trên luồng ghi, tối đa một lần mỗi file mỗi process, không nằm trên đường trả bitmap; lỗi quyền/IO chỉ `Log.w`.
9. C11: `retainOnly` giữ gói đang dùng; đổi gói không để lại `PackData` cũ (đo bằng log số mục `parsed`).
10. Trim (nếu giữ): `evictAll` chỉ ở `>= BACKGROUND`; callback chạy trên main thread an toàn với `LruCache` (đã đồng bộ nội bộ).
11. Toàn nhánh: không log tạm còn sót (`grep FX5`), không `Log` chứa nhãn app hay tên danh bạ.

---

## Công cụ kiểm (điều phối chạy, PowerShell, bản debug)

Máy: Vsmart Live 4 (serial `BGB00153427`, Android 11, Os26Launcher là launcher mặc định). Trước mỗi lượt: xác nhận chủ dự án không dùng máy và Os26Launcher đang ở trước (`dumpsys activity activities`); script chạm kiểm lại trước mỗi thao tác. Đặt `$P = "gambi.global.os26launcher"`, `$T = "dev.fx5.icontest"`, `$B = "$env:USERPROFILE\fx5-backup"`, `$W = "$env:USERPROFILE\fx5-test"` (ngoài repo).

**Sao lưu (trước mọi việc; quy trình DD/FX3):** `>` của PowerShell 5.1 ghi UTF-16 làm hỏng file nhị phân, lệnh ra nhị phân đi qua `cmd /c`.
- **S1.** `adb shell am force-stop $P`; `adb shell run-as $P ls databases files no_backup cache`.
- **S2.** `adb shell run-as $P sh -c "find databases files no_backup -type f | sort | xargs sha256sum" > $B\before.txt`
- **S3.** `cmd /c "adb exec-out run-as $P tar cf - databases files no_backup > %USERPROFILE%\fx5-backup\state.tar"`
- **S4.** `tar -tvf $B\state.tar` đủ file của S1; so `adb shell run-as $P sh -c "tar cf - databases files no_backup | sha256sum"` với `Get-FileHash -Algorithm SHA256 $B\state.tar`; khác thì làm lại S3. Chụp thêm `adb shell pm path $P` và `adb pull` APK đang cài để trả đúng bản; ghi `adb shell getprop persist.sys.locale` (cần trả `vi-VN`).
- **Khôi phục R1–R3 (cuối buổi):** `am force-stop`; `run-as $P rm -rf databases files no_backup`; `adb push $B\state.tar /data/local/tmp/fx5_state.tar`; `adb shell "cat /data/local/tmp/fx5_state.tar | run-as $P tar xf -"`; `adb shell rm /data/local/tmp/fx5_state.tar`; chạy lại S2 ra `$B\after.txt`, `Compare-Object (Get-Content $B\before.txt) (Get-Content $B\after.txt)` phải rỗng. Cache icon (`cache/`) không nằm trong sao lưu: tự dựng lại, vô hại. Gỡ app thử và hồ sơ công việc (bên dưới). Locale về `vi-VN`.

**Dựng APK thử có icon** (khóa debug, cùng khóa cho v1 và v2; `hasCode="false"`; build-tools `E:\SDK_Components\build-tools\35.0.1`, `android.jar` `E:\SDK_Components\platforms\android-36`; *chưa chạy thử trong phiên viết plan*, nếu một cờ `aapt2` không chạy thì điều phối báo lại, không đổi cách khi chưa thử):
```powershell
Add-Type -AssemblyName System.Drawing
$BT = "E:\SDK_Components\build-tools\35.0.1"; $AJ = "E:\SDK_Components\platforms\android-36\android.jar"
function New-IconPng($path, $color) {
  $bmp = New-Object System.Drawing.Bitmap 192,192
  $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear($color); $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
function New-Fx5Apk($ver, $color, $out) {
  $d = "$W\icontest-v$ver"; New-Item -ItemType Directory -Force "$d\res\mipmap-xxhdpi" | Out-Null
  New-IconPng "$d\res\mipmap-xxhdpi\ic.png" $color
  @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$T"
    android:versionCode="$ver" android:versionName="$ver">
  <application android:hasCode="false" android:label="FX5 Icon" android:icon="@mipmap/ic">
    <activity android:name="android.app.Activity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>
"@ | Set-Content -Encoding utf8 "$d\AndroidManifest.xml"
  & "$BT\aapt2.exe" compile --dir "$d\res" -o "$d\res.zip"
  & "$BT\aapt2.exe" link -o "$d\u.apk" --manifest "$d\AndroidManifest.xml" -I $AJ -R "$d\res.zip" --min-sdk-version 26 --target-sdk-version 30
  & "$BT\zipalign.exe" -f -p 4 "$d\u.apk" "$d\a.apk"
  & "$BT\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out $out "$d\a.apk"
}
New-Fx5Apk 1 ([System.Drawing.Color]::Red)  "$W\fx5-icon-v1.apk"
New-Fx5Apk 2 ([System.Drawing.Color]::Blue) "$W\fx5-icon-v2.apk"
```
Cài `adb install -r $W\fx5-icon-v1.apk`, cập nhật `adb install -r $W\fx5-icon-v2.apk`, gỡ `adb uninstall $T`. Không bấm mở app thử.

**Đọc màu của ô trên Home** (nhãn ẩn nhưng có `content-desc`): lấy toạ độ từ `uiautomator dump`, chụp `adb exec-out screencap -p` (qua `cmd /c` ra file), đọc điểm ảnh giữa ô:
```powershell
function Get-NodeCenter($desc) {  # trả "x y" của node có content-desc chứa $desc
  adb shell uiautomator dump /sdcard/fx5.xml | Out-Null
  [xml]$x = (adb exec-out cat /sdcard/fx5.xml)
  $n = $x.SelectNodes("//node[contains(@content-desc,'$desc')]") | Select-Object -First 1
  if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { [int](($matches[1]+$matches[3])/2), [int](($matches[2]+$matches[4])/2) }
}
function Get-PixelAt($png, $x, $y) { $b=[System.Drawing.Bitmap]::FromFile($png); $c=$b.GetPixel($x,$y); $b.Dispose(); $c }
```
Màu "đỏ" = R > 200 và G, B < 60; "xanh" = B > 200 và R, G < 60 (icon legacy nằm trên tấm nền, điểm giữa ô là màu icon). Nhìn bằng mắt ảnh chụp là phép kiểm chính; điểm ảnh chỉ để ghi số.

**Log:** `adb logcat -c`, thao tác, `adb logcat -d -v time -s FX5 AndroidRuntime`; `adb logcat -b crash -d` kiểm sập. **Mở app và kiểm đang ở trước:** `adb shell am start -n $P/.ui.home.HomeActivity`.

**Đổi locale** (công cụ `app_process` của audit mục B9, đợt 3: gọi `updatePersistentConfiguration` với `Configuration.setLocales(...)`; dựng lại bằng `javac` + `d8` nếu không còn, để ở scratchpad, **không vào repo**): `adb push fx5-locale.dex /data/local/tmp/` rồi `adb shell "CLASSPATH=/data/local/tmp/fx5-locale.dex app_process /system/bin FxLocale en-US"`. **Bẫy đã biết:** lần đổi sang một ngôn ngữ mới, Play cài `config.<lang>` cho nhiều app và các sự kiện package đó **vô tình** nạp lại nhãn, che lỗi. Vì vậy kiểm theo chiều mà gói ngôn ngữ đã có sẵn: sau một lần `vi-VN` → `en-US` (chờ Play xong, `dumpsys package` `lastUpdateTime` hết đổi), các lần `en-US` ↔ `vi-VN` sau đó là sạch.

**Hồ sơ công việc** (cách đã chạy được trên Live 4 ở M2): `adb shell pm create-user --profileOf 0 --managed Work` (in ra id, ví dụ 10), `adb shell am start-user 10`, kiểm `adb shell pm list users`; gỡ `adb shell pm remove-user 10`. Dự phòng nếu hồ sơ tạo kiểu này không có huy hiệu hoặc không bắn broadcast: Test DPC của Google (cài, tạo hồ sơ, kiểm, gỡ hồ sơ rồi gỡ Test DPC), chủ dự án đã cho phép. Lưu ý: tạo hồ sơ làm `placeNewApps` đặt hàng chục icon mới vào bố cục thật; đó là lý do phải sao lưu và khôi phục DB.

---

### Task 1: A4 + B7 — dấu phiên bản trong `AppItem` và khóa `IconCache`
**Bắt buộc kiểm tay:** không — giả định "`sourceDir` đổi khi `install -r`" đã đo được (LC, Android 11 và 14), Step 0 vẫn kiểm lại trên APK thử và quyết có cần phương án B không (không có Task sau xây trên giả định này mà không thể đổi rẻ).
**Files:** `domain/model/AppItem.kt`, `data/apps/LauncherAppsDataSource.kt`, `data/apps/IconCache.kt`, `data/apps/IconLoader.kt`, `data/apps/AppRepositoryImpl.kt`
**Interfaces:** Produces `AppItem.versionStamp: String` (mặc định `""`), `AppItem.iconKey: AppIconKey`, `data class AppIconKey(val key: ComponentKey, val stamp: String)`; `IconCache.get/put(key, stamp, sizePx, style[, bitmap])`. Consumes `LauncherAppsDataSource.sourceDirOf`.
- [x] **Step 0 (điều phối, trước khi code, bản `main` đã có FX2/FX4): đo nền.** Sao lưu S1–S4, dựng APK thử, cài v1, mở Home: ô "FX5 Icon" **đỏ**. `install -r` v2, chờ 6 s, **không** `force-stop`: Home **vẫn đỏ** (tái hiện A4), tìm "fx5" ra hàng **xanh** (hàng mới bind). Ghi `adb shell pm path $T` trước và sau `install -r`: đường dẫn **phải khác** (xác nhận `sourceDir` đổi). Nếu **không** khác thì dừng và báo: chuyển sang phương án B (`lastUpdateTime`) trước khi sang Step 1. Gỡ v2, cài lại v1 để dùng cho Step sau.
- [x] **Step 1:** `AppItem.kt`: thêm `val versionStamp: String = ""` (comment: dấu vân tay APK, đổi khi app cập nhật, rỗng nếu không biết) và trong thân class `val iconKey: AppIconKey = AppIconKey(key, versionStamp)` (thuộc tính thân không vào `equals`); thêm `data class AppIconKey(val key: ComponentKey, val stamp: String)` cùng file.
- [x] **Step 2:** `LauncherAppsDataSource.loadApps`: truyền `versionStamp = stamp` khi dựng `AppItem` (biến `stamp` đã có, cùng chuỗi `sourceDir`). Không đổi thứ tự `sourceDirs.putAll` rồi `retainAll`.
- [x] **Step 3:** `IconCache`: `cacheKey(key, stamp, sizePx, style)` = `"$pkg/$cls#$user!$stamp@$size~..."` (stamp ngay sau `#user`, trước `@size`, để `evict(packageName)` theo tiền tố `"$pkg/"` không đổi); `get`/`put` nhận `stamp`. Cập nhật KDoc.
- [x] **Step 4:** `IconLoader.load`: đọc `val stamp = dataSource.sourceDirOf(key).orEmpty()` **một lần** ngay sau `style`, dùng cho `cache.get`, `cache.put` (cả hai chỗ), và `diskName` (thay tham số `sourceDir` bằng `stamp`; điều kiện dùng đĩa là `stamp.isNotEmpty()`). Không đọc `sourceDirOf` lần hai trong cùng lượt nạp.
- [x] **Step 5:** `AppRepositoryImpl`: giữ `private val lastStamps = java.util.concurrent.atomic.AtomicReference<Map<ComponentKey, String>>(emptyMap())`. Trong `loadSorted`, sau `dataSource.loadApps()`: tính tập package có khóa mà stamp cũ (khác rỗng) ≠ stamp mới, `iconCache.evict(pkg)` cho từng package đó, rồi `lastStamps.set(...)`. (Dọn đĩa thêm ở Task 6.) Log tạm: `FX5 stamp changed pkg=<package>`, `FX5 apps emitted n=<size>` sau khi sắp xếp.
- [x] **Step 6:** build xanh; commit `fix: key icon caches by apk version stamp`.
- [x] **Step 7: Kịch bản kiểm (Live 4, điều phối):** (a) cài v1 → Home đỏ; mở trang App Library (vuốt hết các trang Home), tile "FX5 Icon" đỏ; `install -r` v2 chờ 6 s, **trang Library vẫn đang hiện**: tile Library **xanh** không cần thao tác (trang Library dựng lại vì `groups` phát lại; kiểm B7 vì không có map riêng). (b) Mở tìm kiếm gõ "fx5" rồi `install -r` v1 (đảo về) lúc lớp tìm kiếm đang mở: hàng đổi sang đỏ trong vài giây **hoặc**, nếu hàng còn giữ icon, ghi lại đó (Task 3 sửa). (c) Home vẫn **đỏ** sau `install -r` v2: **mong đợi ở Task này** (map view chưa sửa; Task 2). (d) Log: `FX5 stamp changed pkg=dev.fx5.icontest` đúng **một** lần mỗi lần `install -r`; `FX5 apps emitted` đúng một lần mỗi lần cài, không phát khi chỉ vuốt Home. (e) `logcat -b crash` rỗng.

### Task 2: A4 — map icon của Home, dock, thư mục dùng `AppIconKey`
**Bắt buộc kiểm tay:** không — là cùng một sửa cơ học ở ba view; lỗi chạm/kéo-thả không phát sinh vì không đổi xử lý chạm.
**Files:** `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt`, `ui/home/view/FolderPanelView.kt` (chỉ dòng map icon, **không đụng phần rung do FX2 sửa**)
**Interfaces:** Consumes `AppItem.iconKey` (Task 1).
- [x] **Step 1:** `IconGridView`: `iconCache: HashMap<AppIconKey, Bitmap>`. `bindAllCells`: `iconCache[placed.app.iconKey]`; `liveKeys: HashSet<AppIconKey>` thêm `placed.app.iconKey` và `previewApps.forEach { it.iconKey }`. `bindFolderCell`: `preview.map { iconCache[it.iconKey] }`, `missing = preview.filter { it.iconKey !in iconCache }`, gán `iconCache[app.iconKey] = bitmap` (loader vẫn gọi bằng `app.key`), và hai lần dựng lại `visiblePreview(current).map { iconCache[it.iconKey] }`. `loadIconFor(index, key, cells)` đổi thành `loadIconFor(index, app: AppItem, cells)`: kiểm lại `current.app.iconKey == app.iconKey`, gán `iconCache[app.iconKey]`.
- [x] **Step 2:** `DockView`: cùng cách (`iconCache`, `bindAllCells`, `liveKeys`, `loadIconFor`).
- [x] **Step 3:** `FolderPanelView.AppsGrid`: `iconCache: HashMap<AppIconKey, Bitmap>`, `bindAll` tra theo `app.iconKey`, `load(index, app, c)` gán `iconCache[app.iconKey]` và kiểm `apps.getOrNull(index)?.iconKey != app.iconKey`. Thêm `retainAll` theo `apps.mapTo(HashSet()) { it.iconKey }` ở cuối `bindAll` (map này trước đây không bao giờ bị dọn trừ khi đổi cỡ).
- [x] **Step 4:** build xanh; `git diff main -- ui/home/view` chỉ chứa dòng map icon (không có thay đổi ở `Jiggler`, `setEditMode`, attach/detach); commit `fix: key home view icon maps by apk version stamp`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** cài v1. Đặt "FX5 Icon" vào **dock** (kéo bằng `adb shell input draganddrop` thử trước; nếu không ăn thì sửa DB bản sao theo mẫu FX3 hoặc nhờ chủ dự án kéo một lần), và **một app khác cùng thư mục** để tạo thư mục chứa FX5 (cùng cách). Cần thấy đủ bốn chỗ **đỏ**: ô trên lưới, ô dock, icon mini trên ô thư mục, ô trong panel thư mục mở ra. `install -r` v2, chờ 6 s, **không** `force-stop`, **không** đổi trang: cả bốn chỗ **xanh** (chụp ảnh từng chỗ; mở panel thư mục sau khi cài để kiểm map của `AppsGrid`). Lặp lại một lần nữa với `install -r` v1 (đảo xanh → đỏ). Log: không có `FX5 stamp changed` thừa.
- [x] **Step 6 (hồi quy):** đổi kiểu icon (Mặc định → Tô màu → Mặc định) vẫn đúng (map xóa theo kích thước/`RecreateGuard`); kéo-thả một icon trong lưới và vào/ra thư mục vẫn mượt, không icon trống kéo dài (map `retainAll` không làm rơi icon); vào/thoát edit mode.

### Task 3: A4 — ba adapter và `SearchResultAdapter` bind lại khi stamp đổi
**Bắt buộc kiểm tay:** không.
**Files:** `ui/search/SearchResultAdapter.kt`, `ui/home/view/CategoryGroupPanelView.kt`, `ui/library/AppLibraryAdapter.kt`
- [x] **Step 1:** `CategoryGroupPanelView.Adapter`: thêm `var boundStamp: String? = null` vào `Holder`; điều kiện giữ icon thành `holder.boundKey == key && holder.boundStamp == app.versionStamp && holder.icon.drawable != null`; khi nạp gán `boundStamp`, và lúc gán bitmap kiểm `holder.boundKey == key && holder.boundStamp == stamp`; `onViewRecycled` đặt `boundStamp = null`.
- [x] **Step 2:** `AppLibraryAdapter.bindApp` (`AppHolder`): làm y hệt.
- [x] **Step 3:** `SearchResultAdapter.onBindViewHolder`: khóa gán icon của hàng app = `result.stableId` nối `#` và `result.item.versionStamp` khi `result is SearchResult.App`, các loại khác giữ `stableId` (đổi đúng dòng `val key = ...`; `holder.boundKey` đã là `String?`). Không đụng `SearchViewModel`, `Diff`, hay phần danh bạ/Cài đặt của FX4.
- [x] **Step 4:** build xanh; commit `fix: rebind adapter icons when apk version stamp changes`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** (a) mở một nhóm Library (panel `CategoryGroupPanelView`) chứa "FX5 Icon" (nếu app thử không có nhóm hiển thị, phân loại là `UNDEFINED`: tìm nhóm "Khác"/tương ứng), để panel **mở**, `install -r` v2: icon trong panel **xanh** sau vài giây không cần đóng panel. (b) Mở tìm kiếm, gõ "fx5" (hàng đỏ), để lớp tìm kiếm mở, `install -r` v2: hàng đổi sang **xanh**, các hàng khác không chớp. (c) `AppLibraryActivity` chỉ có mã (nếu không có đường mở từ UI thì ghi "đọc code"). (d) Hồi quy: cuộn nhanh danh sách tìm kiếm (icon không nhảy sai hàng, bài học M2), mở/đóng nhóm.
- [x] **Step 6: Kiểm A4 trọn vẹn (điều phối, gộp vào bài kiểm cuối):** v1 → v2 → v1 liên tiếp, `force-stop` không cần; Home/dock/thư mục/panel/Library/tìm kiếm đều theo kịp trong ≤ ~6 s mỗi lần.

### Task 4: B8 — `mutate()` cho foreground trong kiểu Tô màu
**Bắt buộc kiểm tay:** không.
**Files:** `data/apps/IconRenderer.kt`
- [x] **Step 0 (điều phối, trên `main`, thử tái hiện; không bắt buộc ra kết quả dương):** (1) đặt kiểu icon Mặc định, `force-stop`, mở Home, chụp `A0`. (2) Đổi sang Tô màu (Cài đặt → kiểu biểu tượng; lấy toạ độ bằng `uiautomator`), chờ Home dựng lại. (3) **Xóa cache đĩa icon** trong khi process còn sống: `adb shell run-as $P sh -c "rm -rf cache/icons-v1-*/*"` (RAM đã bị xóa khi đổi kiểu). (4) Đổi lại Mặc định, chụp `A1`. Nếu `A1` có icon **bị nhuộm** so với `A0` (nhất là app có foreground PNG), B8 tái hiện: ghi tên app. Không thấy thì ghi "không tái hiện trên Live 4 (có thể GC đã dọn cache yếu của framework)" và vẫn làm Task vì rẻ.
- [x] **Step 1:** `tintGlyphTile`: `val foreground = drawable.foreground?.mutate() ?: return false` (đặt **trước** `foregroundIsGlyph`), comment B8 giải thích vì sao (`BitmapState.mPaint` dùng chung với bản `newDrawable` khác của cùng app).
- [x] **Step 2:** `grep -n "setColorFilter" data/apps` chỉ còn dòng này; build xanh; commit `fix: mutate foreground drawable before tinting`.
- [x] **Step 3: Kịch bản kiểm (Live 4):** lặp lại Step 0 với bản của nhánh: `A1` phải **giống `A0`** (so từng ô bằng mắt và số điểm ảnh của vài icon); đổi sang Tô màu rồi 3D rồi Mặc định: không icon nào lẫn màu; Tô màu vẫn ra "ô tối + hình một màu" như G2b.

### Task 5: C11 — quên gói icon đã bỏ chọn
**Bắt buộc kiểm tay:** không.
**Files:** `data/apps/IconPackManager.kt`, `data/apps/IconStyleSource.kt`
- [x] **Step 1:** `IconPackManager.retainOnly(packKey: String?)`: trong `synchronized(lock)` giữ lại duy nhất khóa `packKey` (null hoặc rỗng: xóa hết); log tạm `FX5 parsed packs=<n>`.
- [x] **Step 2:** `IconStyleSource` `.onEach { new -> ... }`: nếu `previous?.pack != new.pack` thì `iconPacks.retainOnly(new.pack.ifEmpty { null })` (sau khi `previous` đã lấy, trước khi gán `previous = new`). Giữ nguyên `iconCache.clear()` và lời gọi `forget(sel.pack)` hiện có (gói cập nhật).
- [x] **Step 3:** build xanh; commit `fix: forget parsed icon pack data when switching packs`.
- [x] **Step 4: Kịch bản kiểm (Live 4):** máy cần ≥ 2 gói icon (ở Oppo có "iOS 26 - icon pack"; Live 4 có thể chưa cài, nếu chỉ có 0 hoặc 1 gói thì **điều phối báo** và để chủ dự án cho phép cài một gói thứ hai từ Play, hoặc dùng chuỗi `Không dùng` ↔ gói duy nhất). Chọn gói A rồi B rồi "Không dùng": log `FX5 parsed packs=` phải là 1, 1, 0 (trước nhánh sửa: 1, 2, 2). Icon vẫn đúng ở mỗi bước; chọn lại A thì phân tích lại một lần (đúng).

### Task 6: C2 — cache đĩa xóa theo lần dùng gần nhất, dọn file của APK cũ
**Bắt buộc kiểm tay:** không (đo nhẹ do điều phối; quyết định thiết kế đã chốt, không phụ thuộc số đo).
**Files:** `data/apps/IconDiskCache.kt`, `data/apps/AppRepositoryImpl.kt`
- [x] **Step 0 (điều phối, trên `main` trước khi code): dựng ca "icon Home bị xóa trước".** Mở Home để icon Home có file; `run-as` tạo ~700 file đệm (tên `junk_<n>.png`, mỗi file ~13000 byte, tổng ~9 MB, **mtime mới hơn** file icon Home), rồi gây ghi mới bằng `install -r` app thử v1 (icon mới → `write` → `trimNow`). Kết quả mong đợi trên `main`: số file thật của các package ở Home **giảm** (bị xóa vì cũ nhất), file đệm còn. Ghi danh sách `ls -l --full-time` trước/sau. (Lệnh tạo: `D=$(ls -d cache/icons-v1-*)`, vòng `for i in $(seq 1 700); do head -c 13000 /dev/zero > $D/junk_$i.png; done` trong `run-as sh -c`.)
- [x] **Step 1:** `IconDiskCache`: `private val touched = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()`. Trong `get`, sau khi giải mã thành công (`bmp != null`) và nếu `touched.add(name)`: `scope.launch(writer) { try { file.setLastModified(System.currentTimeMillis()) } catch (e: Exception) { Log.w(...) } }`. `write` thành công cũng `touched.add(name)` (file vừa ghi đã mới). Không chạm file trên luồng gọi.
- [x] **Step 2:** `AppRepositoryImpl.loadSorted` (chỗ Task 1 đã so stamp): với mỗi package có stamp đổi, thêm `iconDiskCache.evict(pkg)` ngay sau `iconCache.evict(pkg)` và **trước** `lastStamps.set`/trả danh sách (thứ tự: hàng đợi của luồng ghi đơn bảo đảm mọi `put` theo stamp mới nằm sau lệnh xóa).
- [x] **Step 3:** build xanh; commit `perf: evict icon disk cache by last use`.
- [x] **Step 4: Kịch bản kiểm (Live 4):** (a) Lặp Step 0 nhưng chèn một lần khởi động lại giữa chừng: tạo file đệm (mtime = bây giờ), **`force-stop` rồi mở Home** (icon Home đọc trúng từ đĩa và được chạm, mtime mới hơn file đệm sau ≥ 2 s), rồi `install -r` v1 để gây ghi: file đệm bị xóa **trước**, file icon Home còn nguyên. (b) Nêu rõ **giới hạn**: nếu không khởi động lại giữa chừng (như Step 0) icon Home vẫn có thể bị xóa; ghi kết quả đúng như thấy. (c) `install -r` v2 sau khi đã có file icon của v1: file `dev.fx5.icontest__*.png` của stamp cũ biến mất (đếm bằng `ls | grep dev.fx5`), chỉ còn file mới. (d) Cold start sanity (debug, 5 lượt `am force-stop` + `am start -W`, so `TotalTime` với `main` xen kẽ): không chậm hơn ngoài nhiễu; ghi rõ là số debug.

### Task 7: `IconCache` trả RAM khi vào danh sách nền (có điều kiện) — **BỎ theo số đo 2026-11-11, đã revert (`e04acb9`)**
**Bắt buộc kiểm tay:** không — điều phối chạy phép đo và **quyết giữ hay bỏ Task**; chỉ ảnh hưởng chính Task này.
**Files:** `data/apps/IconCache.kt`
- [x] **Step 0 (điều phối, trên bản sau Task 6): đo trước.** Mở Home, mở App Library cuộn hết, mở tìm kiếm gõ vài chữ, về Home. `adb shell dumpsys meminfo $P` ghi `Native Heap`, `Graphics`, `Activities` (bảng bộ nhớ của audit C3 là mốc). Bấm Home ra launcher hệ thống (process ở nền) rồi `adb shell am send-trim-memory $P BACKGROUND`, đo lại (trên `main` hầu như không đổi: audit C3 113 → 106 MB sau `COMPLETE`).
- [x] **Step 1:** `IconCache` nhận `@ApplicationContext context: Context` (Hilt `@Inject constructor`); `init { context.registerComponentCallbacks(object : ComponentCallbacks2 { onTrimMemory(level) { if (level >= TRIM_MEMORY_BACKGROUND) { log tạm; cache.evictAll() } }; onConfigurationChanged {}; onLowMemory {} }) }`. Singleton sống suốt process nên không gỡ (cùng mẫu FX1). Không `@Suppress` vì `TRIM_MEMORY_BACKGROUND` không bị deprecate. Log tạm: `FX5 IconCache trim level=<n> size=<bytes> -> 0`.
- [x] **Step 2:** build xanh; commit `perf: drop icon ram cache when process goes to background list`.
- [x] **Step 3: Kịch bản kiểm (Live 4):** lặp Step 0: `send-trim-memory BACKGROUND` → log có dòng trim; `send-trim-memory HIDDEN` → **không** có dòng trim (cache không bị xóa khi chỉ ẩn UI). Số `Native Heap` sau trim so với trước. Về Home: icon vẫn đủ (nạp lại từ đĩa, không trống kéo dài), không crash.
- [x] **Step 4 (quyết định của điều phối):** nếu `Native Heap` giảm < 3 MB sau trim ở kịch bản có mở Library và tìm kiếm thì **bỏ Task** (`git revert` commit Step 2, ghi lý do vào Nhật ký và knowledgement); ngược lại giữ.

### Task 8: B9 — đổi ngôn ngữ nạp lại nhãn và thứ tự; `LabelCache` không kẹt header
**Bắt buộc kiểm tay:** không — ca đua broadcast/cấu hình (dự phòng C ở bảng B9) do Step 0/Step 5 trả lời; nếu sai chỉ đổi cơ chế kích hoạt trong cùng một Task.
**Files:** `data/apps/LauncherAppsDataSource.kt`, `data/apps/AppRepositoryImpl.kt`, `data/apps/LabelCache.kt`
**Interfaces:** Produces `LauncherAppsDataSource.observeReloadTriggers(): Flow<Unit>`.
- [x] **Step 0 (điều phối, trên `main`): đo nền.** Công cụ locale. (1) `vi-VN` → `en-US` (lần đầu: Play cài gói, chờ xong), rồi `en-US` → `vi-VN` khi Home đang hiện: đọc nhãn bằng `uiautomator` (content-desc): giữ tiếng Anh (tái hiện B9). Header `run-as $P cat no_backup/app_label_cache.json | head -c 200` còn `en-US`. (2) `en-US` → `en-GB` (nhãn trùng): header **kẹt** `en-US` sau khi `force-stop`+mở lại (tái hiện nhánh `onDisk`). Trả `vi-VN`.
- [x] **Step 1:** `LabelCache.load()`: ở nhánh trả `null` do header lệch (và version sai) đặt `onDisk = null` trước `return null`; trong `catch` cũng đặt `onDisk = null`. Comment B9.
- [x] **Step 2:** `LauncherAppsDataSource.observeReloadTriggers()`: `callbackFlow` đăng ký `BroadcastReceiver` bằng `ContextCompat.registerReceiver(context, receiver, filter, RECEIVER_NOT_EXPORTED)` (mẫu `WidgetTicker`) với `ACTION_LOCALE_CHANGED` và `ACTION_MANAGED_PROFILE_ADDED/REMOVED/AVAILABLE/UNAVAILABLE/UNLOCKED` (receiver nghe cả sáu action ngay từ Task này để khỏi sửa hai lần; việc dọn hàng hồ sơ là của Task 10, ở Task này cả sáu chỉ kích hoạt nạp lại); `onReceive` → `trySend(Unit)`; `awaitClose { unregisterReceiver }` bọc try/catch `IllegalArgumentException`. Log tạm: `FX5 trigger action=<action>`.
- [x] **Step 3:** `AppRepositoryImpl.apps`: `merge(dataSource.observeChanges().onEach{...}.map { Unit }, dataSource.observeReloadTriggers())` thay cho `.map { Unit }` đứng riêng, trước `debounce`. `packageEvents` **không** đổi ở Task này.
- [x] **Step 4:** build xanh; commit `fix: reload app labels and order on locale change`.
- [x] **Step 5: Kịch bản kiểm (Live 4):** (a) `en-US` → `vi-VN` khi Home hiện (gói đã có sẵn): trong ≤ ~3 s nhãn đổi sang "Máy tính", "Điện thoại", "Tin nhắn", "Máy ảnh" **không** `force-stop`; log `FX5 trigger action=android.intent.action.LOCALE_CHANGED`; thứ tự trong App Library đúng bảng chữ cái tiếng Việt (ví dụ "Đồng hồ" sau nhóm D). Header cache `vi-VN`. (b) `vi-VN` → `en-US` → nhãn tiếng Anh, thứ tự tiếng Anh. (c) `en-US` → `en-GB`: sau `force-stop` + mở lại header là `en-GB` (và log đếm cho thấy lần khởi động đó **không** đọc đủ nhãn: cache hợp lệ). (d) Nếu nhãn **chưa** đổi sau (a) dù có log trigger: nghi broadcast đến trước cấu hình, ghi lại và chuyển sang phương án C ở bảng (báo điều phối). (e) Hồi quy: sự kiện package thường (cài/gỡ app thử) vẫn nạp lại đúng một lần.
- [x] **Step 6:** trả `vi-VN`; xác nhận `getprop persist.sys.locale`.

### Task 9: C10 — huy hiệu hồ sơ công việc
**Bắt buộc kiểm tay:** không (mục 4.2 không phủ: không phải migration, cử chỉ, hay phép đo chọn phương án; giả định `getUserBadgedIcon` trả huy hiệu được kiểm ngay ở Step 5 và nếu sai thì chỉ đổi chính Task này sang phương án dự phòng).
**Files:** `data/apps/LauncherAppsDataSource.kt`, `data/apps/IconLoader.kt`
- [x] **Step 0 (điều phối, trên bản sau Task 8): đo nền.** Tạo hồ sơ (`pm create-user --profileOf 0 --managed Work`, `am start-user`), chờ launcher thêm icon hồ sơ vào bố cục (có thể ở trang mới), chụp: app nhân bản **không** huy hiệu (tái hiện C10 phần icon). Ghi id hồ sơ và `userSerial` (`pm list users`, `dumpsys user`).
- [x] **Step 1:** `LauncherAppsDataSource.badgeForUser(icon: Bitmap, userSerial: Long): Bitmap`: nếu `userSerial == mainUserSerial` (`userManager.getSerialNumberForUser(Process.myUserHandle())`, tính một lần, `lazy`) trả `icon` nguyên; ngược lại `user = getUserForSerialNumber(serial) ?: return icon`, `val drawable = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, icon), user)`; nếu `drawable is BitmapDrawable` trả `drawable.bitmap`, không thì vẽ drawable vào `Bitmap` cùng cỡ rồi trả. Bọc `try/catch (e: Exception)` → trả `icon`; `OutOfMemoryError` do `IconLoader` bắt sẵn.
- [x] **Step 2:** `IconLoader.load`: sau khi có bitmap cuối (cả nhánh gói icon lẫn `renderer.render`) gọi `dataSource.badgeForUser(bitmap, key.userSerial)` **trước** `cache.put`/`diskCache.put`, để RAM và đĩa lưu bản có huy hiệu. `diskName`: nối `|badge1` vào chuỗi thô **chỉ khi** `key.userSerial != mainUserSerial` (hàm `dataSource.isWorkUser(serial)` mỏng dùng cùng biến `lazy`), user chính giữ nguyên chuỗi thô (cache cũ còn dùng được).
- [x] **Step 3:** build xanh; commit `feat: badge work profile app icons`.
- [x] **Step 4: Kịch bản kiểm (Live 4):** với hồ sơ đang chạy, mở Home: icon app hồ sơ có **huy hiệu cặp táp** góc dưới phải, icon cá nhân **không**; kiểm ở lưới, dock (kéo một icon hồ sơ vào dock), thư mục mini, App Library, tìm kiếm. Hai bản của cùng một app (cá nhân và công việc) phân biệt được bằng mắt. Tô màu và 3D vẫn đúng (huy hiệu vẽ cuối cùng): chụp ba kiểu cho một app hồ sơ. Đổi kích thước icon (cỡ lưới) vẫn có huy hiệu.
- [x] **Step 5:** nếu **không** thấy huy hiệu: kiểm `dumpsys user` xem hồ sơ có cờ `MANAGED_PROFILE`; thử lại bằng hồ sơ do Test DPC tạo (chủ dự án đã cho phép); nếu vẫn không, ghi kết quả vào Nhật ký và báo điều phối chuyển phương án C của bảng (tự vẽ huy hiệu, cần tài nguyên mới: xin phép ngoài vùng `res/`).
- [x] **Step 6 (hồi quy):** icon user chính không đổi so với trước Task (so ảnh chụp 5 icon); số file icon của user chính trong `cache/icons-v1-*` không tăng sau khi mở Home (tên file đĩa của user chính giữ nguyên chuỗi thô nên vẫn trúng cache cũ), chỉ có file mới cho icon của hồ sơ.

### Task 10: C10 — theo dõi hồ sơ, dọn hàng của hồ sơ đã bỏ
**Bắt buộc kiểm tay:** không.
**Files:** `data/apps/LauncherAppsDataSource.kt`, `data/apps/AppRepositoryImpl.kt`
**Interfaces:** Consumes `observeReloadTriggers` (Task 8). Produces `LauncherAppsDataSource.profileSerials(): Set<Long>`; `packageEvents` phát thêm `PackageEvent(REMOVED, packages, serial)` giả.
- [x] **Step 1:** `LauncherAppsDataSource.profileSerials()`: `userManager.userProfiles.map { userManager.getSerialNumberForUser(it) }.toSet()` (try/catch → `emptySet()` khi lỗi: xem Step 2 vì rỗng = không kết luận). `isUserGone(serial)`: `serial !in profileSerials() && userManager.getUserForSerialNumber(serial) == null`.
- [x] **Step 2:** `AppRepositoryImpl`: `private val syntheticRemovals = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 8)`; `override val packageEvents = merge(dataSource.observeChanges(), syntheticRemovals)`. Trong `loadSorted`, sau khi có danh sách mới: với mỗi `serial` có trong danh sách **cũ** (`lastApps`) mà **không** còn trong danh sách mới, nếu `dataSource.isUserGone(serial)` và `serial` khác user chính (và tập `profileSerials()` không rỗng): `iconCache.evict`/`iconDiskCache.evict` cho các package của user đó và `syntheticRemovals.tryEmit(PackageEvent(REMOVED, packages, serial))`. Cập nhật `lastApps` (có thể chung `lastStamps` của Task 1: lưu `List<AppItem>` thay cho map).
- [x] **Step 3:** log tạm `FX5 profile gone serial=<n> pkgs=<k>`; build xanh; commit `fix: clean layout rows of removed work profiles`.
- [x] **Step 4: Kịch bản kiểm (Live 4, sau Task 9 hồ sơ đã có icon trên Home):** (a) **Quyền đọc DB:** ghi số hàng `launcher_items` có `userSerial = <serial hồ sơ>` (đọc DB bản kéo về, mẫu `Get-FxDb` của FX3). (b) `pm remove-user <id>` khi Home đang hiện: trong ≤ ~3 s log `FX5 trigger action=...MANAGED_PROFILE_REMOVED` rồi `FX5 profile gone serial=... pkgs=k`; icon hồ sơ biến khỏi Home, **không** để lại ô trống kéo dài trong trang (trang dồn như khi gỡ app); số hàng của user hồ sơ trong DB về **0**; mọi icon cá nhân nguyên vẹn (so ảnh chụp trước). (c) Tạo lại hồ sơ (`pm create-user ...`, `start-user`): icon hồ sơ xuất hiện **không cần khởi động lại launcher** (trigger `ADDED`; nếu cần chờ, ghi thời gian). (d) Chế độ yên lặng: nếu có thể (`cmd user` hoặc `am` tương ứng trên Android 11; nếu không có lệnh thì ghi "không kiểm"): bật/tắt không xóa hàng nào, số hàng DB không đổi. (e) `force-stop`, mở lại sau khi bỏ hồ sơ: không còn hàng ma (`catchUp` cũng sẽ dọn nếu Task này bị lọt).
- [x] **Step 5:** khôi phục theo "Công cụ kiểm": `pm remove-user` nếu còn, DB về bản sao lưu, so hash.

### Task 11: Dọn log tạm, knowledgement, hồi quy cuối
**Bắt buộc kiểm tay:** không.
**Files:** các file có log `FX5`, `docs/knowledgements/FX5-icons-labels.md` (tạo), plan này.
- [x] **Step 1:** gỡ mọi log `FX5` (`grep -rn "FX5" app/src` rỗng) trong một commit `chore: remove temporary FX5 logs`; build xanh; `git diff main --stat` chỉ gồm Vùng file.
- [x] **Step 2:** viết knowledgement theo mẫu mục 8 của quy trình, Mermaid: `sequenceDiagram` cho đường "app cập nhật → `onPackageChanged` → `loadSorted` → `AppItem.versionStamp` mới → view/adapter bind lại → `IconLoader` miss → render"; `flowchart` cho C10 (broadcast/diff tập user → `REMOVED` giả → `removePackages`) và cho B9; bảng "ba tầng chặn của A4"; mục "Chỗ cần soi" nêu giới hạn C2 và đua locale.
- [ ] **Step 3 (hồi quy cuối, gộp vào bài kiểm):** vuốt mọi trang, mở/đóng thư mục, edit mode, kéo-thả, đổi kiểu icon, đổi cỡ lưới, tìm kiếm, Library; `logcat -b crash` rỗng suốt buổi.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11, Os26Launcher là launcher mặc định), APK **debug** của nhánh. **Dữ liệu:** bố cục thật của chủ dự án (sao lưu S1–S4), APK thử v1 đỏ/v2 xanh, hồ sơ công việc tạm, công cụ đổi locale. **Cuối buổi:** khôi phục R1–R3, gỡ app thử và hồ sơ, locale `vi-VN`, so hash.

Thứ tự theo thao tác tự nhiên:
1. Chuẩn bị: xác nhận máy rảnh, S1–S4, dựng APK thử, ghi trạng thái (locale, danh sách `pm list users`).
2. A4/B7: cài v1 (đỏ); đặt vào lưới, dock, thư mục; mở Library và tìm kiếm; `install -r` v2 rồi v1 rồi v2 khi từng bề mặt đang hiện; không `force-stop`.
3. B8: Mặc định → Tô màu → xóa cache đĩa → Mặc định (so ảnh).
4. C2: ca file đệm có khởi động lại; đếm file theo package sau `install -r`.
5. Trim (nếu giữ): `send-trim-memory BACKGROUND` / `HIDDEN`, số `dumpsys meminfo`.
6. B9: `en-US` ↔ `vi-VN` (gói đã có), rồi `en-US` → `en-GB`; header cache; thứ tự Library.
7. C11: đổi gói icon A → B → không (nếu máy có ≥ 2 gói).
8. C10: tạo hồ sơ, huy hiệu ở mọi bề mặt, `pm remove-user`, hàng DB về 0, tạo lại.
9. Hồi quy chung (Task 11 Step 3) và `logcat -b crash` rỗng.
10. Khôi phục.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa chạy.

## Câu hỏi mở

- **Đã chốt 2026-11-11 (điều phối):** Q1 = chỉ huy hiệu trên icon; nhãn có hậu tố "công việc" **chỉ dùng cho `contentDescription`** (trợ năng) của ô, không đổi nhãn hiển thị, không đổi `LabelCache` (Task 9 thêm việc này); Q-A = sự kiện `REMOVED` giả; Q-B = ngưỡng 3 MB giữ nguyên.
- **Q1 (chỉ chủ dự án trả lời được; đã chốt như trên):** app trong hồ sơ công việc hiện hai lần cùng nhãn ("Chrome" và "Chrome"). Chỉ thêm **huy hiệu trên icon** là đủ, hay thêm cả hậu tố vào nhãn (ví dụ "Chrome (Công việc)", dùng `UserManager.getBadgedLabelForUser`, tên hậu tố do hệ thống dịch)? Mặc định của plan: chỉ icon. **Điều phối chốt 2026-11-11:** chỉ huy hiệu trên icon (như launcher Android phổ biến); nhãn có hậu tố qua `getBadgedLabelForUser` chỉ dùng cho `contentDescription` (trợ năng), không hiện trên màn hình.
- **Q-A (điều phối):** dọn hàng của hồ sơ đã bỏ bằng sự kiện `REMOVED` giả (mặc định, không đụng `data/layout/*`) hay thêm `LayoutReconciler.removeUser(serial)` ở vùng FX3 (sạch hơn, một câu `DELETE`)? Nếu chọn cách sau, FX3 phải merge trước và nhánh này thêm một Task nhỏ. **Điều phối chốt:** dùng `REMOVED` giả (không đụng vùng FX3).
- **Q-B (điều phối):** Task 7 (trim) chỉ giữ khi số đo đạt ngưỡng 3 MB Native Heap, đúng không? Ngưỡng này do Claude đặt, chưa có cơ sở khác. **Điều phối chốt:** đồng ý ngưỡng 3 MB.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | Plan | Viết plan FX5 sau khi đối chiếu code `b1af810` và đọc plan FX1–FX4; thử đọc mã nguồn Launcher3 và tài liệu Android (xem "Vì sao") | chưa commit | Plan vòng 1/5; chưa chạy gì trên máy |
| 2026-11-11 | Đối chiếu main | Đọc lại plan với `main` `2bac922`: `data/apps/*` không đổi ngoài `ShortcutRepositoryImpl` (FX4, không đụng); FX2 đổi `Jiggler` và phần rung ở ba view (map icon nguyên vẹn, sửa theo tên hàm: `bindAllCells`, `bindFolderCell`, `loadIconFor`, `AppsGrid.bindAll/load`); `SearchResultAdapter` không đổi; `LayoutRepositoryImpl` vẫn thu `appRepository.packageEvents` (số dòng khác, cùng chỗ) | | Không cần đổi plan. Mẫu `ComponentCallbacks2` của FX1 sẽ đọc lại ở Task 7 |
| 2026-11-11 | 1 | `AppItem.versionStamp` + `iconKey`/`AppIconKey`; stamp trong khóa `IconCache`, `IconLoader` đọc stamp một lần; `loadSorted` so stamp rồi `iconCache.evict` package đổi; log tạm `FX5` (`stamp changed`, `apps emitted`). Build xanh | 1185182 | Step 0 (đo nền, `pm path`) là việc của điều phối trên máy |
| 2026-11-11 | 2 | Map icon của `IconGridView` (kể cả icon con thư mục), `DockView`, `FolderPanelView.AppsGrid` dùng `AppIconKey`; `loadIconFor(index, app, cells)`; `AppsGrid` thêm `retainAll`. Build xanh, APK `E:\SourceTree\IosLauncher-wt\apk\FX5-task2.apk` | c93115b | Đã kiểm Live 4 (xem dòng kế) |
| 2026-11-11 | 1–2 | Điều phối review và kiểm Live 4 (~15:40, debug, Os26 mặc định). Step 0: `pm path` đổi mỗi lần `install -r` (`~~AvB3…/base.apk` → `~~6hhb…/base.apk`) nên `sourceDir` là stamp hợp lệ, không cần phương án B. Nền `main` 2bac922: v1 đỏ, `install -r` v2, 7 s → ô còn **đỏ** (A4 tái hiện). `FX5-task2.apk`: v1 đỏ (220,30,30); `install -r` v2 7 s không khởi động lại → **xanh** (30,80,230); về v1 đỏ; v2 xanh. Log: đúng một `stamp changed pkg=dev.audit.icontest` và một `apps emitted n=82` mỗi lần cài; không FATAL | | Tick Task 1–2. **Chưa kiểm** dock, mini icon thư mục, panel thư mục, tile Library (cần kéo-thả): gộp vào bài kiểm cuối. Lưu ý máy này app mới đặt vào ô trống giữa trang (rank 2 (3,3)): lỗi riêng, FX3b |
| 2026-11-11 | 3 | `CategoryGroupPanelView.Adapter` và `AppLibraryAdapter` thêm `boundStamp` (giữ icon chỉ khi cùng key và cùng stamp); `SearchResultAdapter` hàng app dùng `stableId#stamp` làm khóa gán icon. Build xanh | e1ac920 | Kiểm Live 4 gộp vào bài kiểm cuối (panel nhóm Library, hàng tìm kiếm) |
| 2026-11-11 | 4 | `tintGlyphTile`: `drawable.foreground?.mutate()` đặt trước `foregroundIsGlyph` và `setColorFilter`; `grep setColorFilter data/apps` chỉ còn một dòng. Build xanh | a17da79 | Kiểm Live 4 (Step 0 thử tái hiện, Step 3) do điều phối |
| 2026-11-11 | 5 | `IconPackManager.retainOnly(packKey)` + gọi từ `IconStyleSource.onEach` khi phần `pack` của kiểu đổi; log tạm `FX5 parsed packs=`. Build xanh | 859fbca | Cần máy có ≥ 2 gói icon để kiểm |
| 2026-11-11 | 6 | `IconDiskCache.get` chạm `lastModified` khi trúng (một lần mỗi file mỗi process, trên luồng ghi; `write` cũng đánh dấu); `loadSorted` gọi thêm `iconDiskCache.evict(pkg)` cho package đổi stamp, trước khi phát danh sách. Build xanh, APK `E:\SourceTree\IosLauncher-wt\apk\FX5-task6.apk` | 3458189 | Chờ điều phối kiểm Task 3–6 (dừng trước Task 7 theo yêu cầu) |
| 2026-11-11 | 3–6 | Điều phối review và kiểm Live 4 (`FX5-task6.apk`, ~15:50, debug). **Task 3:** tìm "audit", hàng app thử ĐỎ (220,30,30); `install -r` v2, 7 s, tìm kiếm vẫn mở → XANH (30,80,230): PASS; panel nhóm Library chưa kiểm (cần app trong nhóm, chủ dự án kiểm). **Task 4 (B8):** không tái hiện/kiểm trên máy (cần đổi kiểu Tô màu trong Cài đặt) → nhãn **đọc code**. **Task 5 (C11):** chưa kiểm (cần hai gói icon) → **đọc code**. **Task 6 (C2):** thư mục cache có 725 file / 9,7 MB (đã vượt trần 8 MB vì `trim` chỉ chạy khi ghi); tạo 700 file đệm mới hơn, `force-stop`, Home, rồi một lần ghi icon qua tìm kiếm: tổng 1425 → 661, đệm 700 → 623, file của package có ở Home 415 → 18 (18 file giữ lại đúng là icon Home nạp sau khởi động lại, tức đã chạm; kích cỡ khác của cùng package cũ hơn đệm nên bị xóa đúng); `install -r` xóa file stamp cũ của app thử; không FATAL | | Tick Task 3–6 (kèm hạn chế trên). Ghi vào knowledgement: `trim` chỉ chạy khi ghi nên thư mục có thể vượt trần lúc nghỉ; giới hạn LRU của phiên đầu đúng như plan |
| 2026-11-11 | 7 | `IconCache` nhận `@ApplicationContext`, đăng ký `ComponentCallbacks2` trong `init` (mẫu `GlassEnvironment`): `evictAll()` ở `>= TRIM_MEMORY_BACKGROUND`, bỏ qua `UI_HIDDEN`; log tạm `FX5 IconCache trim`. Build xanh, APK `E:\SourceTree\IosLauncher-wt\apk\FX5-task7.apk` | 01f0a4c | Giữ hay bỏ do điều phối quyết sau phép đo (ngưỡng 3 MB Native Heap); trong lúc chờ làm tiếp Task 8 |
| 2026-11-11 | 8 | `LauncherAppsDataSource.observeReloadTriggers()` (receiver động, `RECEIVER_NOT_EXPORTED`, sáu action) hợp vào `apps` trước `debounce`; `LabelCache.load()` đặt `onDisk = null` ở mọi đường trả null (version, header, lỗi đọc); log tạm `FX5 trigger action=`. Build xanh | 1eae644 | Kiểm Live 4 do điều phối (công cụ `app_process`) |
| 2026-11-11 | 9 | `IconLoader` vẽ huy hiệu hồ sơ lên bitmap cuối bằng `LauncherAppsDataSource.badgeForUser` (`PackageManager.getUserBadgedIcon`) cho user khác user chính, trước RAM/đĩa; tên file đĩa của hồ sơ có hậu tố `\|badge1`. **Theo Q1:** thêm `AppItem.a11yLabel` (`PackageManager.getUserBadgedLabel`, chỉ cho user khác user chính) dùng làm `contentDescription` ở `IconCellView.bind` và tile Library (`CategoryPageView`); nhãn hiển thị và `LabelCache` không đổi. Build xanh | e10fcc7 | Plan ghi `UserManager.getBadgedLabelForUser`, nhưng hàm đó không có trong API công khai: dùng `PackageManager.getUserBadgedLabel`. Sửa `IconCellView` (một dòng) và `CategoryPageView` (một dòng) ngoài Vùng file đã khai, chỉ để đặt `contentDescription` |
| 2026-11-11 | 10 | `LauncherAppsDataSource.profileSerials()/isUserGone()`; `AppRepositoryImpl.loadSorted` so tập user với lần nạp trước, user vắng và đã mất ở hai nguồn thì phát `PackageEvent(REMOVED, packages, serial)` qua `syntheticRemovals` (hợp vào `packageEvents`); user chưa kết luận được thì nhớ lại hỏi ở lần nạp sau. Không đụng RAM/đĩa icon (cùng tên package còn của user chính). Log tạm `FX5 profile gone`. Build xanh, APK `E:\SourceTree\IosLauncher-wt\apk\FX5-task10.apk` | 2eb8a5a | Chờ điều phối kiểm Task 8–10; dừng trước Task 11
| 2026-11-11 | 7–9 | Điều phối kiểm Live 4 (`FX5-task6/10.apk`, ~15:55, debug). **Task 7: BỎ.** Foreground sau Library+tìm kiếm: Native 67,5 MB, Graphics 64,1 MB. Nền: `HIDDEN` không có dòng trim (đúng); `BACKGROUND` → `IconCache trim level=40 size=10007856 -> 0` nhưng Native 61,7 → 60,7 MB sau 10 s và tín hiệu GC (Graphics không đổi): < 3 MB vì map icon riêng của các view giữ chính các bitmap đó. Theo quy tắc đã chốt: `git revert` | e04acb9 | Ghi số vào knowledgement. **Task 8 (B9): PASS.** vi-VN → en-US (45 s, Play cài gói) → Phone/Messages/Camera; en-US → vi-VN trong 6 s không khởi động lại, content-desc "Điện thoại/Tin nhắn/Máy ảnh"; log `trigger action=android.intent.action.LOCALE_CHANGED`; header `app_label_cache.json` `"locale":"vi-VN"`; đã trả vi-VN. Ba dòng `apps emitted` mỗi lần đổi: chưa rõ nguyên nhân (suy luận: broadcast + sự kiện package do Play cài lại gói ngôn ngữ, mỗi sự kiện qua debounce 200 ms riêng; chưa kiểm). **Task 9 (C10 huy hiệu): PASS.** `pm create-user --profileOf 0 --managed` → user 10 (serial 11), 28 app hồ sơ đặt vào các trang mới (30 hàng serial 11); icon hồ sơ có huy hiệu cặp táp xanh (YouTube, YT Music); content-desc "YouTube làm việc"; icon cá nhân không huy hiệu |  | Tick Task 8, 9 |
| 2026-11-11 | 10 | **FAIL vòng 1/5:** `pm remove-user 10` khi Home hiện: log `trigger ...MANAGED_PROFILE_REMOVED`, rồi `apps emitted n=81` (trước 109) nhưng **không** có `profile gone`; sau 14 s DB còn 30 hàng `(11)` (ô ma, 6 trang). Nguyên nhân (suy luận từ AOSP, chưa kiểm trực tiếp): `getUserForSerialNumber` còn trả user lúc đang gỡ dở nên `isUserGone` trả false, user bị "nhớ lại" chờ lần nạp sau, mà không còn trigger nào. Sửa: `isUserGone` chỉ cần vắng ở `userProfiles` (danh sách không rỗng, không phải user chính; hồ sơ yên lặng vẫn có trong danh sách); thêm log `FX5 users before=… now=… profiles=…` mỗi lần nạp. Build xanh, APK `E:\SourceTree\IosLauncher-wt\apk\FX5-task10b.apk` | d88dbd8 | Chờ điều phối kiểm lại; sau đó Task 11. Điều phối đã trả máy (DB từ bản sao lưu, gỡ user 10, vi-VN) |
| 2026-11-11 | 10 | **Kiểm lại vòng 1 (FX5-task10b, ~16:20): gần đạt.** `trigger ...MANAGED_PROFILE_REMOVED`, `users before=[0, 12] now=[0] profiles=[0]`, `profile gone serial=12 pkgs=28`, `apps emitted n=81`; DB sau 8 s: hàng serial 12 từ 30 xuống **2** (6 → 5 trang); sau `force-stop` và mở lại: 0 hàng, 4 trang (`catchUp` dọn nốt). **Vòng 2/5:** danh sách package của sự kiện không phủ hết hàng của user (nguyên nhân cụ thể chưa biết). Đổi sang xóa theo user: `PackageEvent.Type.USER_REMOVED` (packageNames rỗng), `LayoutReconciler.removeUser(serial)` xóa mọi hàng `userSerial = serial` (không bao giờ serial 0) + `hidden_apps` của user (`HiddenAppDao.deleteByUser`), dồn ô và dọn thư mục như `removePackages`; `LayoutRepositoryImpl.init` xử lý cả hai loại; log tạm `FX5 removeUser serial=… rows=…`. Build xanh, APK E:\SourceTree\IosLauncher-wt\apk\FX5-task10c.apk | 20ae6d7 | Sửa `data/layout/*` được điều phối cho phép (FX3 đã merge). Chờ kiểm lại; sau đó Task 11 |
| 2026-11-11 | 10 | **Kiểm lại vòng 2 (FX5-task10c, ~16:30): PASS.** Hồ sơ user 12 / serial 13 → 30 hàng, 6 trang; `pm remove-user 12`: `trigger …MANAGED_PROFILE_REMOVED`, `users before=[0, 13] now=[0] profiles=[0]`, `profile gone serial=13 pkgs=28`, `apps emitted n=81`, `removeUser serial=13 rows=30`; DB sau 8 s: 0 hàng serial 13, 6 → 4 trang; sau `force-stop` + mở lại vẫn 0; không FATAL. Điều phối đã review diff (reconciler thêm dưới mutex + transaction, chốt serial 0, thư mục/widget không bị đụng) | 20ae6d7 | Chế độ yên lặng (tạm dừng/tiếp tục hồ sơ) chưa thử: **đọc code**. Tick Task 10 |
| 2026-11-11 | 11 | Gỡ mọi log tạm `FX5` (`grep` rỗng, bỏ import `Log` thừa ở `AppRepositoryImpl`); viết knowledgement `docs/knowledgements/FX5-icons-labels.md` (3 sơ đồ Mermaid, số đo trim Task 7, ghi chú trim-chỉ-chạy-khi-ghi của C2, ba dòng `apps emitted` mỗi lần đổi ngôn ngữ, bài học 28 gói so với 30 hàng); điền bảng bằng chứng Review Focus; cập nhật đề xuất tài liệu chung. Build xanh | 95ecf7d (gỡ log), commit docs kế tiếp | Hồi quy cuối (Task 11 Step 3) và bài kiểm cuối do điều phối/chủ dự án. PR #39 (FX8) cũng sửa `CategoryPageView.kt`: có thể phải rebase, chưa làm |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Mọi nơi giữ icon theo khóa có stamp | `grep "iconCache\[" ui/home/view`: chỉ tra bằng `.iconKey`; ba adapter có `boundStamp`, `SearchResultAdapter` dùng `stableId#stamp`; `IconCache` khóa có `!stamp`. Live 4: lưới đỏ → xanh sau `install -r` (Task 2), hàng tìm kiếm đỏ → xanh khi lớp tìm kiếm mở (Task 3). Dock, mini thư mục, panel thư mục, tile/panel Library: chưa kiểm trên máy | Đạt một phần (chờ chủ dự án) |
| 2 | Không đổi chữ ký `loadIcon`, không ngoài Vùng file | Không chữ ký `loadIcon` nào đổi; ngoài vùng ban đầu: hai dòng `contentDescription` (`IconCellView`, `CategoryPageView`) và `data/layout/*` + `PackageEvent` (điều phối cho phép), đã ghi vào Vùng file | Đạt |
| 3 | `apps` phát đúng một lần khi cập nhật, không phát thừa | Live 4 (log tạm, đã gỡ): một `stamp changed` và một `apps emitted` mỗi lần `install -r`; không phát khi vuốt Home | Đạt |
| 4 | Thứ tự loadApps → so stamp → evict → phát | Đọc code `AppRepositoryImpl.loadSorted`; Live 4: file đĩa của stamp cũ biến mất sau `install -r` (Task 6) | Đạt |
| 5 | `mutate()` trước `foregroundIsGlyph` và `setColorFilter` | Đọc code (`grep setColorFilter data/apps`: một dòng, `mutate()` ngay khi lấy `foreground`); không tái hiện lỗi trên máy | Đọc code |
| 6 | Receiver đúng vòng đời; `LabelCache` `onDisk = null` mọi đường `null` | `awaitClose` hủy đăng ký, `RECEIVER_NOT_EXPORTED`; Live 4: đổi `en-US` → `vi-VN` không khởi động lại, nhãn đổi trong 6 s, header `vi-VN`. Cặp `en-US` ↔ `en-GB` chưa thử | Đạt (B9 chính); `onDisk` đọc code |
| 7 | `REMOVED` giả chỉ cho user đã mất; huy hiệu chỉ user khác user chính | Đã đổi thành `USER_REMOVED` + `removeUser` (không bao giờ serial 0). Live 4: `pm remove-user`: `users before=[0, 13] now=[0]`, `removeUser rows=30`, 0 hàng sau 8 s và sau khởi động lại; huy hiệu cặp táp ở app hồ sơ, không ở app cá nhân. Chế độ yên lặng: đọc code | Đạt |
| 8 | `setLastModified` trên luồng ghi, một lần mỗi file mỗi process | Đọc code; Live 4: 700 file đệm, sau `force-stop` + Home + một lần ghi: file icon Home đã đọc được giữ, file đệm và cỡ cũ hơn bị xóa | Đạt |
| 9 | `retainOnly` giữ gói đang dùng | Đọc code; chưa có máy hai gói icon | Đọc code |
| 10 | Trim chỉ ở `>= BACKGROUND` | Task đã bỏ theo số đo (Native Heap 61,7 → 60,7 MB < 3 MB); `git revert` `e04acb9` | Đã bỏ |
| 11 | Không log tạm còn sót | `grep -rn "FX5" app/src` chỉ còn comment mã mục, không `Log.d("FX5"`; commit `95ecf7d` | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: không có thay đổi tính năng người dùng ngoài sửa lỗi; nếu có mục cho TT#12 (kiểu icon) thì ghi thêm "icon đổi theo cập nhật app" và "huy hiệu hồ sơ công việc" (M2 đã hoãn).
- `project_structure.md`: mục 3/6 mô tả `AppItem` thêm `versionStamp`, `iconKey`, `a11yLabel` và `AppIconKey`; `data/apps` thêm luồng trigger hệ thống (locale, hồ sơ); `PackageEvent.Type.USER_REMOVED` và `LayoutReconciler.removeUser`.
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement. Dự kiến, chủ đề `icons-and-app-list`: (1) mọi cache theo khóa cần dấu phiên bản APK ở **mọi** tầng (RAM, đĩa, view) và dữ liệu mang dấu đó để so `==` tự phát hiện đổi; (2) cập nhật app qua `install -r` đã thử trên Live 4 (kết quả Task 1 Step 0); (3) `Drawable` lấy từ framework chung `ConstantState` với `Paint`: `mutate()` trước khi sửa; (4) huy hiệu hồ sơ vẽ lên bitmap cuối. Chủ đề `permissions-and-device-testing`: tạo hồ sơ công việc tạm bằng `pm create-user --managed`, đổi locale bằng `app_process` và bẫy "gói `config.<lang>` che lỗi"; `startup-performance`: LRU của cache đĩa và giới hạn phiên đầu.
