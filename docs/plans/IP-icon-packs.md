# IP — Hỗ trợ gói biểu tượng (icon pack) của bên thứ ba — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Dự án **không viết test tự động**. **Agent thực thi không dùng máy thật** (không adb, không cài APK): mọi bước "bắt buộc kiểm tay" do điều phối/chủ dự án chạy. Tối đa 5 vòng mỗi Task. Lệnh viết cho PowerShell.

**Nhánh:** `feature/ip-icon-packs` · **Worktree:** `E:\SourceTree\IosLauncher-wt\IP-icon-packs` · **PR:** (draft, số điền sau khi mở) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** chủ dự án duyệt sẵn cho lần chạy này (2026-11-10: viết plan rồi làm ngay)
**Trạng thái:** Code xong Task 1–4, chờ kiểm tay Task 2 và bài kiểm cuối (do điều phối)
**Khóa:** không khóa schema Room (chỉ thêm một khóa DataStore) · không file nóng độc quyền · cần máy thật + một gói icon do chủ dự án cài

**Goal:** Chủ dự án muốn "áp bộ icon iOS cho app". Icon app của Apple có bản quyền và là nhãn hiệu, nên **không đóng gói bất kỳ icon Apple hay icon của bên thứ ba nào trong APK**. Thay vào đó launcher hỗ trợ **icon pack** theo chuẩn mà Nova, ADW, Lawnchair đang dùng: người dùng tự cài một app icon pack (ví dụ gói phong cách iOS miễn phí trên Google Play), chọn nó ở Cài đặt, launcher vẽ icon app từ gói đó.

**Architecture:** `IconPackRepository` (domain, giao diện) ← `IconPackManager` (data): tìm gói đã cài bằng `queryIntentActivities`, đọc `appfilter.xml` qua `createPackageContext`, cache bản đã phân tích theo `package@versionCode`. `IconLoader` thêm một bước "tra gói" trước đường cũ; `IconRenderer` thêm hai hàm vẽ (icon gói, icon dự phòng ghép từ iconback/iconmask/iconupon/scale). Khóa lựa chọn nằm trong DataStore; `IconStyleKey` mang thêm `pack = "pkg@versionCode"` nên cache RAM, cache đĩa IC và `RecreateGuard` của Home tự phân biệt.
**Tech Stack:** không thư viện mới. `XmlPullParser` (`android.util.Xml`), `PackageManager`, DataStore Preferences sẵn có.
**Spec:** chưa có TT# riêng trong `Launcher-Features.md` (tính năng mới do chủ dự án yêu cầu 2026-11-10; điều phối thêm TT# sau). Liên quan: TL2 (hình icon), G2b (kiểu icon), IC (cache đĩa), LC.

## Lưu ý pháp lý (không đóng gói icon Apple)

Icon ứng dụng và logo của Apple là tác phẩm có bản quyền và nhãn hiệu của Apple; icon của gói bên thứ ba là tác phẩm của tác giả gói. Launcher **không chứa, không tải, không nhúng** bất kỳ tệp ảnh nào như vậy: mọi ảnh đọc lúc chạy từ gói mà **người dùng tự cài** từ cửa hàng, giống cách Nova/ADW/Lawnchair vận hành. Launcher chỉ là bộ đọc. Tên gói gợi ý trong tài liệu chỉ là tên để người dùng tự tìm, không kèm tệp. (Không phải tư vấn pháp lý; chủ dự án tự cân nhắc khi phát hành lên cửa hàng.)

## Vì sao chọn thiết kế này

### Vấn đề cần giải
1. Yêu cầu "icon iOS" mà không được mang artwork Apple vào APK (pháp lý ở trên).
2. Đường icon hiện tại có ba tầng cache: RAM (`IconCache`, khóa gồm kiểu), đĩa (`IconDiskCache`, tên file chứa hash của class/user/size/style/tint/`sourceDir`), và các view giữ bitmap riêng (G2b: `IconGridView`, `DockView`, `FolderPanelView`; đổi kiểu đi qua `RecreateGuard`). Một lựa chọn mới ảnh hưởng cả ba, nếu quên một tầng thì hiện icon cũ. **Nguồn: đọc code + AGENTS.md mục G2b, IC.**
3. Oppo A3x có ~345 app; nạp icon trang đầu hôm nay ~650 ms (LC, đo). Phân tích `appfilter.xml` (có thể hàng nghìn mục) không được chặn đường này nhiều. **Nguồn: AGENTS.md mục LC.**
4. Không đổi schema Room, không thư viện mới, không `QUERY_ALL_PACKAGES`.

### Phương án đã xét

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Hỗ trợ icon pack chuẩn ADW/Nova** | Tìm gói bằng intent, đọc `appfilter.xml`, vẽ icon từ gói | Không đóng gói artwork (hợp pháp); dùng được hàng nghìn gói sẵn có (kể cả gói iOS miễn phí); chuẩn lâu đời, tài liệu công khai | Phải xử lý gói viết ẩu (XML hỏng, drawable thiếu); `<queries>` thêm vài intent; cần máy thật + gói thật để kiểm | **Chọn** |
| B. Tự vẽ bộ "icon giống iOS" cho vài app phổ biến, đóng trong APK | Vector/PNG tự thiết kế | Không cần gói ngoài | Dễ bị coi là bản sao thương hiệu Apple; chỉ phủ vài app; phải bảo trì; rủi ro bị gỡ khỏi cửa hàng | Không |
| C. Đóng gói icon của một gói mã nguồn mở có giấy phép phù hợp | Chép tệp vào `res/` | Chạy ngay | Giấy phép từng icon thường không rõ (nhiều gói mô phỏng icon Apple); APK to; vẫn rủi ro thương hiệu | Không |
| D. Chỉ hỗ trợ Adaptive Icon theme / monochrome của Android | Dùng `getMonochrome()` | Không cần gói | Chỉ API 33+, không cho ra icon iOS; trùng việc G2b "Tô màu" | Không (đã có G2b) |

### Các quyết định thiết kế bên trong A

**Thứ tự giải icon** (`pack` = gói đang chọn):
1. Chưa chọn gói ("Không dùng") → đường cũ y nguyên (TL2 + G2b), không chạm gì.
2. Có gói, app **có mục** `<item component=...>` khớp `pkg/class` → icon của gói, vẽ vừa khít hộp rồi **cắt bằng hình TL2** (xem dưới).
3. Có gói, app **không có mục** → nếu gói khai `iconback`/`iconmask`/`iconupon` (gói iOS thường khai để app lạ vẫn đồng bộ) thì **ghép icon dự phòng** theo thuật toán chuẩn; nếu gói không khai gì thì dùng **icon gốc + hình TL2 + kiểu G2b** (đường cũ).
4. Mục có `drawable` mà gói thiếu file hoặc giải mã lỗi → coi như không có mục, rơi xuống bước 3.

**Vì sao dự phòng của gói chứ không luôn dùng icon gốc của mình:** gói iOS mà chỉ đổi một phần app còn phần còn lại giữ icon Android gốc thì lưới lộn xộn; người làm gói chính là người định nghĩa nền/mặt nạ cho "app lạ". Đây là điều Nova/ADW làm (nguồn: gist `MajeurAndroid/a51869e826b9a283a173b65e923857f8` và tài liệu Lawnchair, ghi ở knowledgement). Gói không khai thì không có gì để ghép, nên dùng icon gốc.

**Tương tác với TL2 và G2b:**
- *TL2 (hình bo góc):* icon gói **vẫn bị cắt bằng `IconShape.DEFAULT`**. Lý do: lưới đồng nhất hình, và gói ghi sai viền/để lại góc đục vẫn đẹp. Hệ quả chấp nhận: gói có hình khác squircle (tròn, vuông) sẽ bị bo như hình của launcher. Ghi ở "Đánh đổi".
- *G2b (Tô màu, 3D):* với icon lấy từ gói (cả mục lẫn dự phòng ghép) **bỏ qua Tô màu và 3D**: gói đã định nghĩa diện mạo, tô màu đè lên làm hỏng nó. Hai kiểu đó vẫn áp cho icon rơi về đường cũ (bước 3 nhánh không khai). Hàng "Kiểu icon" vẫn hiện, không ẩn, để đơn giản (không thêm trạng thái UI).
- Cách khác đã cân nhắc: không cắt hình, vẽ nguyên icon gói. Bỏ vì nhiều gói có nền vuông đục sẽ lệch với phần còn lại của lưới.

**Khóa cache (IC):** `IconStyleKey` thêm `pack: String = ""` bằng `"<pkg>@<versionCode>"`. `IconCache.cacheKey` và `IconLoader.diskName` đều nối `pack` vào khóa/chuỗi băm. Về "Không dùng" → `pack = ""` → khóa trùng khóa cũ nên icon gốc đã cache còn nguyên. Gói cập nhật → `versionCode` đổi → khóa mới. Hai gói khác nhau → khóa khác. **Nguồn: đọc code `IconLoader.diskName`, `IconCache.cacheKey`.**

**Vì sao không bump `DIR_PREFIX` (`icons-v1-`):** chỉ nối thành phần `pack` vào chuỗi băm khi nó không rỗng; khi rỗng chuỗi thô **giữ y hệt** nên cache đĩa đã có không mất.

**Chọn gói lưu ở đâu:** DataStore `icon_pack` (chuỗi, vắng = không dùng). Không đổi Room. Lưu tên package; `versionCode` đọc lúc chạy từ `PackageManager` (nên cập nhật gói được thấy ngay).

**Khi gói bị gỡ/cập nhật lúc đang chạy:** `IconStyleSource` lắng nghe `observeChanges()` của `LauncherAppsDataSource` (callback `LauncherApps` báo mọi package, không chỉ launcher; cần gói hiển thị theo `<queries>`; **suy luận chưa kiểm chứng trên ROM thật**). Gỡ → ghi lại `icon_pack = null` ("Không dùng"), xóa cache RAM. Cập nhật → khóa đổi (versionCode), xóa cache RAM và bản gói đã phân tích. Home tự dựng lại ở `onResume` qua `RecreateGuard`. Nếu callback không tới, `HomeViewModel.currentIconStyleKey()` đọc `versionCode` thật nên lần `onResume` kế tiếp vẫn phát hiện khác biệt (lưới an toàn thứ hai).

**Phân tích `appfilter.xml`:** chạy ở luồng IO của `IconLoader` (đã ở `@IoDispatcher`), một lần cho mỗi `package@versionCode` (khóa `synchronized`: các coroutine nạp icon song song chờ nhau thay vì phân tích N lần). Giữ `Map<String,String>` component → tên drawable (không nạp bitmap trước; chỉ tra `getIdentifier` khi cần). Tolerant: `XmlPullParserException`, mục thiếu thuộc tính, component sai đều bỏ qua mục đó. Log chỉ đếm và ms, thẻ `IP`.

**Vị trí `appfilter.xml`:** thử `res/xml/appfilter` → `res/raw/appfilter` → `assets/appfilter.xml` (tài liệu Lawnchair liệt kê đúng ba chỗ này).

### Đánh đổi chấp nhận
- Gói không phải hình squircle bị bo theo TL2. Xét lại nếu chủ dự án muốn "tôn trọng hình gói": thêm công tắc.
- Chọn gói thì Tô màu/3D vô hiệu với icon gói (hàng vẫn hiện). Xét lại nếu muốn ẩn hàng khi có gói.
- Chưa hỗ trợ lịch/đồng hồ động (`<calendar>`, `<dynamic-clock>`): chỉ `<item>` tĩnh.
- Lần nạp đầu sau khi chọn gói chậm hơn (phân tích + render, cache đĩa rỗng cho khóa mới); các lần sau nhanh nhờ IC.

### Mục tiêu hiệu năng trung thực
Agent không đo được. Mục tiêu cho điều phối: Oppo, release build, cache ấm, **chưa chọn gói**: trang đầu không chậm hơn `main` (đường cũ chỉ thêm một phép so chuỗi rỗng). Có gói: log `IP parse` cho ms phân tích; kỳ vọng < ~300 ms cho gói ~3000 mục (**suy luận chưa kiểm chứng**).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Không đóng gói artwork Apple hay của bên thứ ba | Chủ dự án, 2026-11-10 |
| D2 | Chuẩn ADW/Nova: `appfilter.xml` + intent discovery | Chọn A |
| D3 | Thứ tự: mục gói → dự phòng gói → icon gốc (TL2+G2b) | Như trên |
| D4 | Icon gói cắt bằng hình TL2, bỏ qua Tô màu/3D | Như trên |
| D5 | Khóa DataStore `icon_pack`; không Room | Không khóa schema |
| D6 | `IconStyleKey.pack` vào khóa RAM + hash đĩa; rỗng thì chuỗi thô giữ nguyên | Cache cũ còn dùng |
| D7 | Gói bị gỡ → tự về "Không dùng" | |
| D8 | Chỉ liệt kê gói tìm được `appfilter.xml` | Loại gói chủ đề GO/Smart không hợp |

## Ngoài phạm vi / ghi để sau
- `<calendar>`, `<dynamic-clock>`; icon gói cho shortcut; chọn icon riêng từng app; ẩn hàng Kiểu icon khi có gói; gói themed monochrome (Lawnchair `THEMED_ICON`).
- Tải gói từ trong launcher (không bao giờ làm).

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/IconPackInfo.kt`, `domain/repository/IconPackRepository.kt` | Tạo | Không |
| `domain/model/IconStyle.kt` | Sửa: `IconStyleKey.pack` | Không |
| `domain/model/LauncherSettings.kt`, `domain/repository/SettingsRepository.kt`, `data/settings/SettingsKeys.kt`, `data/settings/SettingsRepositoryImpl.kt` | Sửa: trường + khóa `icon_pack` | Không |
| `data/apps/IconPackManager.kt` | Tạo | Không |
| `data/apps/IconRenderer.kt`, `IconLoader.kt`, `IconCache.kt`, `IconStyleSource.kt` | Sửa | Không |
| `di/RepositoryModule.kt` | Sửa: bind | Dùng chung (một dòng) |
| `AndroidManifest.xml` | Sửa: `<queries>` | Dùng chung (thêm khối) |
| `ui/settings/*` | Sửa: hàng + hộp thoại | Dùng chung (thêm) |
| `ui/home/HomeViewModel.kt` | Sửa: `currentIconStyleKey` | Dùng chung (một hàm) |
| `res/values/strings.xml` | Sửa: chuỗi mới | Dùng chung (thêm) |
| `docs/plans/IP-icon-packs.md`, `docs/knowledgements/IP-icon-packs.md` | Tạo | Không |

## Global Constraints
- Lỗi gói (XML hỏng, drawable thiếu, SecurityException, OOM): **luôn** rơi về đường icon gốc, không crash, `Log.w` thẻ `IP`.
- Không giữ `Drawable`/`Bitmap` của gói lâu hơn một lần vẽ (chỉ giữ map tên); icon gói chỉ ghi vào cache đĩa IC của ta (`cacheDir`, xóa được).
- Log chỉ đếm và ms, tag `IP`; không log tên icon hay danh sách app.
- Không `QUERY_ALL_PACKAGES`. Phân tích không chạy trên luồng chính.

## Review Focus
1. Khóa cache: chọn gói, đổi gói, về "Không dùng", gói cập nhật đều không hiện icon lệch (RAM, đĩa, view).
2. Không lưu trạng thái "gói đã chọn nhưng đã gỡ".
3. Đường không gói: hành vi và khóa đĩa không đổi so với `main`.
4. `<queries>` đủ cho Android 11+ mà không nới thành `QUERY_ALL_PACKAGES`.
5. Phân tích chịu XML hỏng; chỉ phân tích một lần dù nạp song song.

---

### Task 1: Dữ liệu và cài đặt (chưa đổi hành vi)
**Bắt buộc kiểm tay:** không
**Files:** `IconPackInfo.kt`, `IconPackRepository.kt`, `IconStyle.kt`, `LauncherSettings.kt`, `SettingsRepository*.kt`, `SettingsKeys.kt`
- [x] **Step 1:** mô hình, khóa DataStore, `setIconPack`.
- [x] **Step 2:** `assembleDebug lintDebug` xanh.

### Task 2: Tìm và phân tích gói
**Bắt buộc kiểm tay:** **có** — giả định chỉ máy thật trả lời được (mục 4.2): ROM có cho `queryIntentActivities` thấy gói qua `<queries>` không, gói thật của chủ dự án khai intent nào và `appfilter.xml` nằm ở đâu. Sai thì Task 3–4 phải làm lại.
**Files:** `IconPackManager.kt`, `RepositoryModule.kt`, `AndroidManifest.xml`
- [x] **Step 1:** `<queries>` cho các intent (knowledgement mục 3).
- [x] **Step 2:** `installedPacks()`, `keyOf()`, phân tích, log `IP`.
- [ ] **Step 3: Kịch bản kiểm (Live 4 hoặc Oppo)** — (a) cài một gói, mở Cài đặt, hàng "Gói biểu tượng" liệt kê gói; (b) logcat `IP`: `discover packs=N ms=..`; (c) chọn gói, `IP parse items=N ms=..` xuất hiện.

### Task 3: Giải icon từ gói và khóa cache
**Bắt buộc kiểm tay:** không (gộp bài kiểm cuối)
**Files:** `IconRenderer.kt`, `IconLoader.kt`, `IconCache.kt`, `IconStyleSource.kt`
- [x] **Step 1:** `renderPackIcon`, `renderPackFallback` (DST_OUT cho mask).
- [x] **Step 2:** `IconLoader.load` theo D3; `pack` vào khóa RAM và hash đĩa.
- [x] **Step 3:** `IconStyleSource`: khóa theo gói, lắng nghe gỡ/cập nhật.
- [ ] **Step 4: Kịch bản kiểm** — (a) chọn gói: lưới, dock, thư mục, Thư viện, tìm kiếm đổi icon không cần khởi động lại; (b) "Không dùng": icon gốc trở lại; (c) app không có trong gói: dự phòng; (d) gỡ gói đang chọn: về "Không dùng".

### Task 4: Hàng Cài đặt
**Bắt buộc kiểm tay:** không
**Files:** `SettingsRow.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsActivity.kt`, `HomeViewModel.kt`, `strings.xml`
- [x] **Step 1:** hàng "Gói biểu tượng" (giá trị = tên gói hoặc "Không dùng"), hộp thoại chọn một.
- [x] **Step 2:** `HomeViewModel.currentIconStyleKey` thêm `pack` để `RecreateGuard` dựng lại Home.
- [ ] **Step 3: Kịch bản kiểm** — như Task 3 (a)(b); mở lại Cài đặt hàng hiển thị đúng.

### Task 5: Knowledgement và đóng
**Bắt buộc kiểm tay:** không
- [x] `docs/knowledgements/IP-icon-packs.md`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Live 4 (chức năng), Oppo A3x (số đo; release build). Dữ liệu: một gói icon phong cách iOS do chủ dự án cài.
1. Chưa chọn gói: icon như `main` (hồi quy TL2, G2b, cache IC).
2. Cài gói → Cài đặt → "Gói biểu tượng" → liệt kê → chọn → Home dựng lại, mọi chỗ có icon đổi.
3. Đổi kiểu icon (Tô màu/3D) khi có gói: icon gói không đổi; app rơi về đường cũ vẫn theo kiểu.
4. "Không dùng" → icon gốc, không lệch cache.
5. Gỡ gói đang chọn → về "Không dùng".
6. Chọn lại gói lần hai (cache đĩa ấm): icon hiện nhanh; log `IP` không phân tích lại trong cùng tiến trình.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | Plan | Viết plan | xem `git log` | chủ dự án duyệt sẵn |
| 2026-11-10 | 1–5 | Code + knowledgement | xem `git log` | build xanh |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Khóa cache | Đọc code; chưa kiểm tay | Chờ điều phối |
| 2 | Không lưu gói đã gỡ | `IconStyleSource` ghi lại null; chưa kiểm tay | Chờ điều phối |
| 3 | Đường không gói không đổi | `diskName` giữ chuỗi thô khi `pack` rỗng | Đọc code |
| 4 | `<queries>` | Khai theo intent; chưa kiểm trên máy | Chờ điều phối |
| 5 | XML hỏng, một lần phân tích | `try/catch` + `synchronized`; chưa kiểm tay | Chờ điều phối |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: thêm TT# "Gói biểu tượng bên thứ ba".
- `project_structure.md`: thêm `IconPackManager`, `IconPackRepository`, `IconPackInfo`.
- `room-schema.md`: không đổi.
- `AGENTS.md`: xem mục "Bài học" trong knowledgement.
