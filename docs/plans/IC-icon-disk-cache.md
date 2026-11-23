# IC — Cache icon xuống đĩa và đo lại nút cổ chai icon — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Dự án **không viết test tự động** (AGENTS.md, chốt 2026-10-16): kiểm chứng bằng đo và chạy tay. **Agent thực thi không dùng máy thật**: `adb install` và mọi kịch bản đo do điều phối chạy. Tối đa 5 vòng mỗi Task. Lệnh viết cho PowerShell.

**Nhánh:** `feature/ic-icon-disk-cache` · **Worktree:** `E:\SourceTree\IosLauncher-wt\IC-icon-cache` · **PR:** # (draft) · **Bắt đầu:** 2026-11-06 · **Plan duyệt:** chờ chủ dự án
**Trạng thái (2026-11-06):** Plan đã được chủ dự án duyệt (trần 8 MB, cache mọi kích thước icon). Task 2–3 (mã) xong, build xanh; Task 1 (đo pha), Task 4 (đo) và Task 5 phần "Bằng chứng Review Focus" do điều phối làm. Phát hiện 4 trong plan đã lỗi thời: `IconCache.evict` **đã được gọi** ở `AppRepositoryImpl.apps` (M4); IC chỉ thêm xóa đĩa khi REMOVED. Ghi chú cũ: Task 1 (đo pha) và Task 2–3 (mã) làm được trên Live 4; **số đo chốt trên Oppo A3x làm khi máy về**.
**Khóa:** cần Oppo (đo chốt) + Live 4 (không tệ hơn) · không khóa schema Room · không file nóng độc quyền

**Goal:** Sau LC, trên Oppo A3x (345 app) lưới sẵn sàng ở ~1,6 s nhưng **icon cuối cùng chỉ vẽ xong ~2,4 s** sau khi tiến trình bắt đầu (chủ dự án đo bằng mắt ~3 s). Khoảng ~0,8 s đó là nạp 24 ô + dock: mỗi icon hôm nay đi `getActivityList(package)` (IPC) → `getIcon` (nạp drawable, giải mã resource) → `IconRenderer.render` (vẽ, cắt). Lưu **bitmap đã render** xuống đĩa để lần khởi động sau chỉ đọc một file nhỏ, bỏ cả ba bước trên.

## Vì sao chọn thiết kế này

### Vấn đề (nhãn nguồn)
1. **Đo (LC, Oppo):** pages sẵn sàng ~1,6 s, icon cuối ~2,4 s. Live 4: ~1,2 s. Khoảng trễ icon trên Oppo ~0,8 s cho ~28 icon, tức ~30 ms mỗi icon dù các ô nạp song song (`IconGridView.loadIconFor`: mỗi ô một coroutine, `Dispatchers.IO`).
2. **Đọc code:** `IconLoader.load` = RAM cache → `dataSource.loadIconDrawable` (`launcherApps.getActivityList(pkg, user)` rồi `getIcon(0)`) → `renderer.render`. `IconCache` chỉ ở RAM nên **mỗi cold start làm lại toàn bộ**.
3. **Suy luận chưa kiểm chứng:** nút cổ chai là `getIcon`/giải mã resource có khóa trong framework (như đã thấy với nhãn, M3 Task 1: song song 4/8/16 luồng chỉ nhanh hơn ~24%). Nếu đúng thì chạy song song không cứu được và chỉ bỏ hẳn bước đó mới cứu. **Task 1 đo để biết trước khi tin.**
4. **Phát hiện khi đọc code:** `IconCache.evict(packageName)` được khai báo nhưng **không có chỗ nào gọi**. Icon trong RAM không bị bỏ khi app cập nhật. Hôm nay vô hại vì RAM chết cùng tiến trình; **với cache đĩa thì sẽ thành lỗi thật** (icon cũ sống mãi), nên IC phải có dấu vân tay (xem dưới).

### Phương án đã xét

| Phương án | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|
| **A. Cache bitmap đã render, mỗi icon một file PNG trong `cacheDir/icons-v1-<fp>/`** | Bỏ cả IPC + giải mã + vẽ ở lần nóng; PNG giữ alpha (góc trong suốt, TL2); giải mã 1 file ~vài ms; `cacheDir` đúng nghĩa "xóa được", không vào Auto Backup; xóa thư mục là sạch | Dung lượng ≤ vài MB; phải xử lý vô hiệu hóa | **Chọn** |
| B. Một file gộp (atlas/blob) | Ít file, một lần đọc | Khó cập nhật từng icon, ghi lại cả blob; phức tạp hơn lợi ích ở ~30–350 mục | Không |
| C. Chỉ tăng song song / ưu tiên icon trang 1 | Không đĩa | Nếu có khóa framework (suy luận 3) thì vô ích; vẫn trả ~30 ms/icon mỗi cold start | Chỉ làm nếu Task 1 cho thấy nghẽn là hàng đợi, không phải `getIcon` |
| D. Bỏ `getActivityList(pkg)` mỗi icon bằng cách giữ `LauncherActivityInfo` từ `loadApps` | Bỏ 1 IPC/icon, không đĩa | Giữ đối tượng framework dễ lỗi thời; không bỏ `getIcon` và vẽ (phần nặng nhất, suy luận) | Không là đường chính; xét như bước phụ nếu Task 1 chỉ ra IPC là phần lớn |
| E. WebP lossless | Nhỏ hơn PNG | `WEBP_LOSSLESS` chỉ API 30+; minSdk 26 | Không (PNG chạy mọi API) |

**Vì sao PNG mỗi file, tên theo hash:** tên file = `<packageName>__<hash(className,user,size,style,tint,sourceDir)>.png`. `sourceDir` là dấu vân tay đã chứng minh đổi mỗi lần cài/cập nhật (LC Task 1, đo trên cả hai máy). Cập nhật app → tên khác → miss → render lại, **không cần chủ động xóa**; file cũ bị dọn khi vượt trần. Tiền tố `packageName__` cho phép `evict(packageName)` xóa theo tiền tố, và nối luôn `IconCache.evict` đang bị bỏ rơi. Kiểu icon (G2b) và kích thước đã nằm trong khóa RAM nên đưa vào hash luôn; đổi kiểu rồi đổi lại thì icon cũ còn dùng được.

**Vô hiệu hóa:**

| Tình huống | Phát hiện | Xử lý |
|---|---|---|
| App cập nhật | `sourceDir` đổi → hash đổi | Miss, render lại, file cũ mồ côi bị dọn khi vượt trần |
| App gỡ | Khóa không còn trong `apps`; (nếu có) `evict(package)` khi sự kiện REMOVED | Không ai hỏi nữa; dọn theo trần/`evict` |
| Cập nhật hệ điều hành (icon app hệ thống đổi, `sourceDir` /system không đổi) | Tên thư mục chứa `Build.FINGERPRINT` (hash ngắn) | Thư mục khác, xóa thư mục cũ lúc khởi tạo |
| Đổi mật độ màn hình / cỡ icon | `sizePx` nằm trong hash | Miss tự nhiên |
| File hỏng/cụt | `BitmapFactory.decodeFile` trả null hoặc ném | Xóa file đó, rơi về render, ghi lại |
| Đổi chủ đề icon của ROM (ColorOS) mà không đổi `sourceDir` | **Không phát hiện được rẻ** | Giới hạn đã biết; người dùng xóa dữ liệu app hoặc đổi kiểu icon trong Cài đặt thì làm mới (xem Ngoài phạm vi: nút làm mới) |

**Dấu vân tay `sourceDir` lấy ở đâu mà không thêm IPC:** `LauncherAppsDataSource.loadApps()` đã có `info.applicationInfo.sourceDir` cho từng activity (LC dùng làm `stamp`). Thêm một registry nhỏ trong bộ nhớ `key → sourceDir` (cập nhật mỗi lần `loadApps`). `IconLoader` chỉ hỏi icon cho khóa đã có trong `apps` nên registry luôn có sẵn. Khóa không có trong registry → không dùng đĩa (đường cũ).

**Ghi:** ngoài đường găng. Sau `render`, `cache.put` RAM như cũ, rồi gửi vào hàng đợi một luồng ghi (`limitedParallelism(1)`) để không tranh CPU với nạp icon: ghi tmp `.tmp` rồi `renameTo`. Trần tổng dung lượng 8 MB (dự kiến: ~350 icon × ~10 KB PNG ≈ 3,5 MB; Task 1 đo thực tế), dọn file cũ nhất theo `lastModified` khi vượt.

**Đánh đổi chấp nhận:** thêm ~3–8 MB đĩa cache; lần cài mới (cache rỗng) tốn thêm công ghi nền; icon có thể cũ sau khi ROM đổi chủ đề mà không đổi APK. **Xét lại khi:** Task 1 cho thấy thời gian nằm ở nơi khác (hàng đợi, bind trên luồng chính, kính) thì đổi sang C/D thay vì cache.

### Mục tiêu trung thực
Chưa biết chia nhỏ của 0,8 s. Mục tiêu tạm (**chốt sau Task 1**, chủ dự án duyệt lại): Oppo, cache nóng, icon cuối vẽ xong **trung vị ≤ 1,9 s** (từ ~2,4 s), tệ nhất ≤ 2,1 s; Live 4 không tệ hơn. IC **không** hứa dưới "pages sẵn sàng" (~1,6 s): phần đó thuộc nút cổ chai khác (khởi động tiến trình ~0,8 s, DB, `HomeViewModel`).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Thư mục `context.cacheDir/icons-v1-<hash fingerprint>/`, mỗi icon một file PNG | Chờ duyệt |
| D2 | Tên file `<pkg>__<hash>.png`; hash gồm class, user, size, style, tint, `sourceDir` | Nối được `evict` theo tiền tố |
| D3 | Registry `key → sourceDir` trong RAM, điền từ `loadApps`; không IPC thêm | Không đổi `AppItem` |
| D4 | Ghi nền một luồng, atomic bằng tmp + đổi tên, trần 8 MB dọn theo `lastModified` | Số 8 MB chốt sau Task 1 |
| D5 | Lỗi đĩa (đọc, ghi, hết chỗ) luôn rơi về đường cũ, không crash | |
| D6 | Không test tự động | AGENTS.md |

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `data/apps/IconDiskCache.kt` | Tạo (đọc, ghi nền, dọn, `evict`) | Không |
| `data/apps/IconLoader.kt` | Sửa `load()`: RAM → đĩa → render | Không |
| `data/apps/IconCache.kt` | Sửa nhỏ: nối `evict` với đĩa nếu cần | Không |
| `data/apps/LauncherAppsDataSource.kt` | Sửa `loadApps()` để điền registry (vài dòng). **Nhánh B1 sửa cùng file ở `openClock`/`openCalendar`**: vùng khác nhau | Không |
| `data/apps/AppRepositoryImpl.kt` | Có thể nối gọi `evict` ở sự kiện package (xem Task 3); M8d đã merge nên không còn xung đột | Không |
| `docs/plans/IC-icon-disk-cache.md`, `docs/knowledgements/IC-icon-disk-cache.md` | Tạo | Không |

Không đụng `HomeViewModel`, `IconGridView`, `HomePagerAdapter`, `core/glass`, `data/layout/db`.

## Global Constraints

- Lỗi cache đĩa (thiếu, hỏng, IO, hết chỗ): **luôn** quay về đường hôm nay, không crash, không chặn. Mọi `catch` ghi `Log.w`.
- Bitmap đọc từ đĩa phải **giống hệt** bitmap vừa render (alpha, góc trong suốt): so mắt trên cả ba kiểu icon (Mặc định, Tô màu, 3D) trước và sau.
- Đọc đĩa chạy trên `@IoDispatcher`; ghi chạy trên luồng đơn riêng; không chạm luồng chính.
- Không thêm log thời gian vĩnh viễn (điều phối áp patch đo vào bản sao bỏ đi).
- Không đổi `AppItem`, `AppRepository`, chữ ký `IconLoader.load`.
- Không sửa tài liệu chung. Commit/PR tiếng Anh; docs tiếng Việt. Không `gradlew --stop`.

## Review Focus

1. Hash đổi khi cập nhật app (`sourceDir`), khi đổi kiểu icon, khi đổi `sizePx`; không đổi khi không có gì đổi.
2. Cache hỏng (rỗng, cụt, không phải PNG) không làm chết app và được ghi lại đúng.
3. Bitmap từ đĩa trùng bitmap render (nhìn từng kiểu icon, kể cả góc trong suốt).
4. Ghi nằm ngoài đường găng, atomic, tôn trọng trần dung lượng.
5. Đổi `Build.FINGERPRINT` (mô phỏng) → thư mục cũ bị xóa.
6. Lần cài sạch (cache rỗng) không chậm hơn `main` quá ngưỡng.
7. `IconCache.evict` thật sự được gọi ở sự kiện REMOVED/CHANGED, hoặc quyết định rõ không cần (vì hash đã lo).
8. Chỉ sửa trong Vùng file.

---

### Task 1: Đo pha icon (điều phối chạy; patch trên bản sao bỏ đi)
**Bắt buộc kiểm tay:** có — kết quả quyết định có làm Task 2–3 hay đổi sang phương án C/D.
**Files:** không (patch đo bỏ đi, trong scratchpad).
- [ ] **Step 1 (Live 4, làm được hôm nay):** ghi thời gian cho từng icon của trang 1 + dock: (a) từ lúc ô bind tới lúc `load()` bắt đầu (hàng đợi), (b) `getActivityList(pkg)`, (c) `getIcon(0)`, (d) `render`, (e) lúc bitmap được gán vào `IconCellView`. Ghi trung vị mỗi pha và đường găng.
- [ ] **Step 2 (Live 4):** thử `Semaphore(1)` so với hiện tại để biết có khóa framework không (nếu tổng thời gian không giảm khi tăng song song, suy luận 3 đúng).
- [~] **Step 3 (Oppo, 2026-11-09, một phần):** chỉ đo cửa sổ icon, chưa tách pha `getIcon`/render/IPC. Lặp Step 1. Ghi kích thước thực của icon PNG (để chốt trần D4).
- [ ] **Step 4:** Kết luận: nếu (b)+(c)+(d) ≥ 60% đường găng → làm Task 2–3. Nếu không → dừng, báo điều phối, đổi phương án.

### Task 2: `IconDiskCache` (đọc, ghi nền, dọn; chưa nối)
**Bắt buộc kiểm tay:** không.
**Files:** Tạo `data/apps/IconDiskCache.kt`.
**Interfaces:** Produces: `class IconDiskCache @Inject constructor(@ApplicationContext, @IoDispatcher, @ApplicationScope)`: `fun get(name: String): Bitmap?`, `fun put(name: String, bitmap: Bitmap)` (xếp hàng ghi), `fun evict(packageName: String)`, `fun trim()`.
- [x] **Step 1:** Khởi tạo thư mục `icons-v1-<hash FINGERPRINT>`, xóa thư mục `icons-*` khác.
- [x] **Step 2:** `get`: `BitmapFactory.decodeFile` với `ARGB_8888`; lỗi/null thì xóa file, trả null.
- [x] **Step 3:** `put`: luồng ghi đơn, ghi `name.tmp` bằng `Bitmap.compress(PNG, 100)` rồi `renameTo`; `trim()` khi tổng > trần.
- [x] **Step 4: Build** `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"` xanh.

### Task 3: Nối vào `IconLoader` + registry `sourceDir`
**Bắt buộc kiểm tay:** có — đây là đo cuối của tính năng (Task 4).
**Files:** Sửa `IconLoader.kt`, `LauncherAppsDataSource.kt` (điền registry), có thể `IconCache.kt`, `AppRepositoryImpl.kt`.
- [x] **Step 1:** Registry `ConcurrentHashMap<ComponentKey, String>` cập nhật cuối `loadApps()`.
- [x] **Step 2:** `IconLoader.load`: RAM miss → tên file (có `sourceDir`) → `diskCache.get` → hit thì `cache.put` RAM và trả; miss thì render như cũ, `cache.put` RAM, `diskCache.put`. Không có `sourceDir` thì bỏ qua đĩa.
- [x] **Step 3:** Quyết định `evict` (xong: RAM evict đã có sẵn cho mọi sự kiện; thêm evict đĩa chỉ khi REMOVED; CHANGED do hash lo): gọi ở sự kiện REMOVED/CHANGED (nối vào chỗ `AppRepositoryImpl` xử lý `observeChanges`) hoặc ghi rõ là không cần.
- [x] **Step 4: Build** xanh.

### Task 4: Đo và kiểm (điều phối)
**Bắt buộc kiểm tay:** có.
- [x] **Step 1 (Live 4 2026-11-06, Oppo 2026-11-09):** Đo theo bảng "Phép đo" bên dưới, ghép cặp xen kẽ, lặp "trước" ở cuối.
- [ ] **Step 2: Kịch bản hỏng:** thay một file icon bằng rỗng/cụt/rác; xóa thư mục; mỗi lần `force-stop` rồi mở: không crash, icon đúng, file ghi lại.
- [ ] **Step 3: Đổi kiểu icon** Mặc định → Tô màu → 3D → về Mặc định: mỗi kiểu đúng, quay lại kiểu cũ vẫn đúng. **Cập nhật app:** `adb install -r` APK debug của chính dự án rồi mở lại; icon của nó phải render lại (hash đổi).
- [ ] **Step 4:** Live 4 chức năng và không tệ hơn.

### Task 5: Knowledgement và đóng
- [ ] `docs/knowledgements/IC-icon-disk-cache.md` có `flowchart` RAM→đĩa→render, bảng vô hiệu hóa, `sequenceDiagram` cold start. Điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung".

## Phép đo (điều phối; cùng một phiên, xen kẽ, lặp "trước" ở cuối)
`am force-stop` rồi `am start -W`, 5 lượt, bỏ lượt đầu sau cài. Mốc: ms từ lúc tiến trình bắt đầu. Pha: pages sẵn sàng; icon đầu tiên vẽ (trong `onDraw`, **không** dùng "pages ready" thay cho "người dùng thấy gì", bài học LC); icon cuối của trang 1 + dock vẽ; thời gian ghi đĩa (nền, chỉ ghi nhận).

| Ca | Máy | Đạt khi (tạm, chốt sau Task 1) |
|---|---|---|
| Cache nóng | Oppo A3x | icon cuối vẽ xong trung vị ≤ 1,9 s, tệ nhất ≤ 2,1 s (hôm nay ~2,4 s) |
| Cache rỗng (lần đầu) | Oppo A3x | không tệ hơn `main` quá +100 ms |
| Cache nóng | Live 4 | không tệ hơn `main` (+30 ms nhiễu) |
| Cache rỗng | Live 4 | không tệ hơn +50 ms |
| Dung lượng | Cả hai | thư mục icon < 8 MB |

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-06 | Plan | Viết plan | `docs: add IC plan` | Chờ duyệt |
| 2026-11-06 | 2 | `IconDiskCache` (PNG, ghi nền 1 luồng, trần 8 MB, evict theo tiền tố) | `1b3eb8d` | Build xanh |
| 2026-11-06 | 2 | Sửa `get`: kiểm chữ ký PNG và IEND vì decoder nhận PNG cụt (điều phối bắt lỗi trên Live 4) | xem git log | Build xanh |
| 2026-11-06 | 3 | Registry `sourceDir` trong `loadApps`, nối `IconLoader`, evict đĩa khi REMOVED | `2d1f5ba` | Build xanh; chờ Task 1/4 của điều phối |
| 2026-11-09 | 4 (Oppo) | Đo xen kẽ `main` và IC trên Oppo A3x (35 icon): icon cuối xong trung vị 2820 -> 2173 ms (-23%), cửa sổ icon 896 -> 234 ms (-74%), cache rỗng lần đầu +79 ms. **Mục tiêu tạm 1,9 s không đạt về con số tuyệt đối** vì icon đầu chỉ được hỏi từ ~1,9 s; phần IC chịu trách nhiệm đã giảm ~0,66 s | — | Chủ dự án quyết merge hay không; chi tiết ở knowledgement mục Số đo Oppo |
| 2026-11-09 | 4 (release) | Đo thêm bản **release** trên Oppo (xen kẽ `main` và IC, 8 lượt mỗi bên): icon cuối xong 637 -> 497 ms (-22%), cửa sổ icon ~180 -> ~53 ms. Bản debug chậm gấp ~4,4 lần release với cùng mã, nên số "2,4-2,8 s" ở các mục trước là số của bản debug | — | Mục tiêu 1,9 s đặt theo số debug; ở release đã dưới 0,65 s. Chi tiết ở knowledgement |
