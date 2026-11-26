# AG — Gom nhóm app vào thư mục từ Cài đặt → Chung (B3) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Chủ dự án cho làm liền (2026-11-26) rồi build/kiểm một thể; tick Task khi chủ dự án báo ổn. Không có test tự động; kiểm tay trên Oppo PKD130. Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (thiết kế duyệt trong chat)
**Trạng thái:** Xong 2026-11-26 (mở lại 13:42 cho Task 4–5; chủ dự án yêu cầu đóng plan 14:22)
**Máy:** Oppo PKD130 · **Đổi schema Room:** không (thư mục `itemType = 1` + cột `title` đã có)

**Goal:** Màn Cài đặt → Chung có mục "Gom nhóm ứng dụng" gồm 6 hàng (Google, Mạng xã hội, Trò chơi, Giải trí, Tiện ích, Ứng dụng của hãng); bấm một hàng mở bottom sheet xem trước thư mục (trang 3×3, tối đa 15 trang) rồi "Gom nhóm" để gom các app đang nằm phẳng trên Home vào thư mục, các ô còn lại dồn lên.
**Architecture:** Domain thuần `AppGroupPlanner` (nhận diện app theo nhóm + chọn ứng viên theo thứ tự trang/đọc). `LayoutRepository.groupIntoFolder` (reconciler, dưới `mutex` + một transaction): tạo hoặc dùng lại thư mục, chuyển app vào, dồn từng trang bằng `GridRemovePlanner`, gỡ trang trống. UI: `AppGroupsViewModel` (Hilt) đọc `LayoutRepository.pages` + `dock`; `GeneralSettingsScreen` thêm hàng; `AppGroupSheet` (`DsBottomSheet` + `HorizontalPager`).
**Tech Stack:** không thêm dependency.
**Spec:** yêu cầu chủ dự án 2026-11-26 (chat), backlog B3 (thay `IosLauncher/docs/plans/FL-first-run-layout.md` phần thư mục gợi ý + bước Onboarding).

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** FL muốn trang 1 có thư mục gợi ý như app mẫu. Chủ dự án bỏ bước Onboarding, chuyển thành hành động trong Cài đặt dùng được cho mọi bố cục (giải luôn B5 ở mức gom nhóm). `ApplicationInfo.category` hay vắng (đọc tài liệu: trường tùy chọn của nhà phát triển) nên cần danh sách gói dựng sẵn cho Google / mạng xã hội / app của hãng.
- **Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| 1. Hàng hành động + sheet xem trước | Bấm → xem → "Gom nhóm" áp một lần | Dùng cho mọi bố cục, người dùng thấy trước | Đụng bố cục người dùng (có xác nhận) | **Chọn** (chủ dự án) |
| 2. Hàng công tắc gom tự động lâu dài | App cài sau tự vào thư mục | Tự động | Phải sửa đường đặt app mới (FX3), trạng thái tắt/bật trên bố cục đã sửa | Không |
| 3. Bước "Sắp xếp màn hình chính" trong Onboarding (FL Q6) | Chọn nhóm trước khi seed | Đẹp ngay lần đầu | Figma chưa có màn, thêm bước, cần đường dựng lại | Không (chủ dự án) |

- **Ô trống sau khi gom:** (a) để trống / (b) dồn lại → **(b)** (chủ dự án).
- **Đánh đổi chấp nhận:** tên thư mục ghi theo ngôn ngữ lúc gom (cột `title`), đổi ngôn ngữ sau không dịch lại; không hoàn tác bằng nút (kéo app ra khỏi thư mục như thường).

## Mở lại 2026-11-26 — gom nhóm trong bố cục lần đầu

Chủ dự án (13:42): "việc gom nhóm này làm tự động như là 1 phần bố cục mặc định của app ngay từ khi mở đầu". Trang 1 = 2 widget mặc định + app launcher (như WF) → thư mục app tiện ích chính của máy → thư mục Google → các thư mục nhóm còn lại; app không thuộc thư mục nào nằm phẳng; 4 app ở dock không vào thư mục. Cài đặt → Chung lúc mới cài: mọi hàng đã gom (mờ), người dùng phá bố cục thì sáng lại. Hướng làm: **A** (seed dựng bản nháp có thư mục; B = seed phẳng rồi gọi `groupIntoFolder` bị loại vì thư mục rải theo A–Z, không dồn lên đầu trang 1).

| Bố cục trang 1 (lưới 4×6) | |
|---|---|
| hàng 0–1 | Đồng hồ 2×2 · Lịch 2×2 |
| hàng 2 | OS26 Launcher · ▣ Tiện ích · ▣ Google · ▣ Mạng xã hội |
| hàng 3… | ▣ Trò chơi · ▣ Giải trí · ▣ Ứng dụng của hãng · app phẳng A–Z (tràn trang sau) |
| dock | Điện thoại · Tin nhắn · Trình duyệt · Máy ảnh (không đổi) |

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Không có bước Onboarding; lần seed đầu giữ như hiện tại (không tự gom) | Chủ dự án 2026-11-26 |
| 2 | Mục "Gom nhóm ứng dụng" trong Cài đặt → Chung, mỗi nhóm một hàng (tiêu đề + số app); bấm mở sheet xem trước | Chủ dự án |
| 3 | 6 nhóm: Google, Mạng xã hội, Trò chơi, Giải trí, Tiện ích, Ứng dụng của hãng; không làm Mua sắm / Ngân hàng (thiếu loại hệ thống, phải giữ danh sách theo nước) | Claude đề xuất, chủ dự án duyệt ("nghĩ thêm hạng mục khả thi") |
| 4 | Chỉ lấy app nằm phẳng trên lưới Home (`PlacedApp` của `pages`); không đụng dock, không lấy app trong thư mục; app ẩn không có trong bố cục nên không lọt | Claude đề xuất, chủ dự án duyệt |
| 5 | App thuộc nhiều nhóm (vd YouTube: Google + Giải trí) vào nhóm được gom trước; gom nhóm sau không kéo nó ra (nó không còn phẳng) | Claude đề xuất, chủ dự án duyệt |
| 6 | Có thư mục (lưới hoặc dock) trùng tên nhóm theo ngôn ngữ hiện tại (so không phân biệt hoa thường, bỏ khoảng trắng hai đầu) thì gom thêm vào đó, sheet hiện app sẽ thêm, nút "Thêm N ứng dụng"; không thì tạo thư mục mới đặt ở ô của app đầu tiên (thứ tự trang rồi thứ tự đọc) | Claude quyết khi viết plan (chủ dự án: "còn gì cần chốt không? nếu không thì làm luôn") |
| 7 | Hàng bật khi: chưa có thư mục và ≥ 2 app, hoặc có thư mục và ≥ 1 app; không thì mờ. Giá trị: số app ("0 ứng dụng", "1 ứng dụng"…), riêng có thư mục mà hết app để thêm thì "Đã gom" | Claude đề xuất, chủ dự án duyệt (< 2 app thì mờ); lúc code bỏ chữ "Chưa đủ ứng dụng" vì số app đã nói rõ |
| 8 | Dồn lại = dồn trong từng trang theo thứ tự đọc (như ẩn app, `GridRemovePlanner`); widget đứng yên; không kéo app từ trang sau sang; trang hết item thì gỡ (`deleteEmptyPages`) | Chủ dự án chọn (b); phạm vi "trong từng trang": Claude quyết |
| 9 | Sheet: tiêu đề = tên thư mục + số app; thẻ 3×3 mỗi trang, vuốt ngang, chấm trang; tối đa 15 trang = 135 app (`FolderPlanner.MAX_ITEMS`, trừ app thư mục đã có), app dư ở lại lưới; nút "Hủy" + "Gom nhóm"; chỉ xem, không bỏ chọn từng app | Chủ dự án duyệt |
| 10 | Thư mục "Ứng dụng của hãng" đặt tên theo `Build.MANUFACTURER` (vd "OPPO", "Samsung"); hàng ghi "Ứng dụng của OPPO" | Claude quyết |
| 11 | Gom xong: đóng sheet, Toast "Đã gom N ứng dụng vào “Tên”"; bố cục đã đổi (0 app gom được) thì Toast báo không gom được | Claude quyết |
| 12 | **Thay QĐ 1:** seed (DB bố cục rỗng) tự gom nhóm; thứ tự trên trang 1: widget, app launcher, các thư mục, rồi app phẳng A–Z; dock không vào thư mục | Chủ dự án 2026-11-26 13:42 |
| 13 | **Một thư mục "Tiện ích"** gồm app tiện ích chính (Danh bạ, Ghi chú, Tin nhắn, Cài đặt, Thư viện ảnh, Máy ảnh…) + app hệ thống lặt vặt (Đồng hồ, Máy tính, Tệp, Ghi âm…). Thay luật "app lõi không vào nhóm nào": app lõi chỉ vào Tiện ích. "Nhật ký" = app Ghi chú | Chủ dự án chọn (a) 2026-11-26 13:48 |
| 14 | Thứ tự nhóm (seed và hàng Cài đặt): Tiện ích, Google, Mạng xã hội, Trò chơi, Giải trí, Ứng dụng của hãng; ở seed app khớp nhiều nhóm vào nhóm đứng trước | Claude quyết (Tiện ích, Google đầu theo chủ dự án) |
| 15 | Seed: nhóm < 2 app không thành thư mục (app nằm phẳng); mỗi thư mục ≤ `MAX_ITEMS` | Claude, cùng luật QĐ 7 |
| 16 | Tên nhóm một nguồn chung (`AppGroupTitleProvider`, ngôn ngữ app lúc gọi) cho seed và Cài đặt, nên ngay sau seed hàng Cài đặt tìm thấy thư mục cùng tên → "Đã gom" (mờ); sáng lại khi có app phẳng thuộc nhóm (kéo ra khỏi thư mục, giải tán thư mục, cài app mới) | Claude, đúng ý "phá bố cục mới sáng lại" |
| 17 | CH Play (`com.android.vending`) vào thư mục Google (trước đây loại) | Claude quyết |


## Nhận diện nhóm (`AppGroupPlanner.matches`)

| Nhóm | Luật |
|---|---|
| GOOGLE | **Mở lại 2026-11-26:** dịch vụ Google: gói `com.google.*`, `com.android.chrome`, `com.android.vending` (CH Play, QĐ 17), TRỪ app lõi của Google (`GOOGLE_CORE`: Điện thoại, Danh bạ, Tin nhắn, Đồng hồ, Máy tính, Lịch, Máy ảnh, Tệp, `documentsui`, `packageinstaller`, `permissioncontroller`, `gms`, `gsf`) — các app lõi đó vào UTILITIES |
| SOCIAL | `AppCategory.SOCIAL` hoặc gói trong danh sách: Facebook (`com.facebook.katana`, `.orca`, `.lite`, `.mlite`), Zalo `com.zing.zalo`, TikTok (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`), Instagram `com.instagram.android`, Threads `com.instagram.barcelona`, Telegram (`org.telegram.messenger`, `org.thunderdog.challegram`), WhatsApp (`com.whatsapp`, `com.whatsapp.w4b`), X `com.twitter.android`, Discord `com.discord`, Viber `com.viber.voip`, Snapchat `com.snapchat.android`, LinkedIn `com.linkedin.android`, Pinterest `com.pinterest`, Reddit `com.reddit.frontpage`, LINE `jp.naver.line.android`, KakaoTalk `com.kakao.talk`, WeChat `com.tencent.mm`, Skype `com.skype.raider` |
| GAMES | `AppCategory.GAME` |
| ENTERTAINMENT | `AppCategory.ENTERTAINMENT` (video / audio / ảnh) và không phải app hệ thống (khỏi lấy Thư viện ảnh / Nhạc của máy) |
| UTILITIES | **Mở lại 2026-11-26 (QĐ 13):** (1) app lõi — `GOOGLE_CORE`, `com.android.settings`, `com.android.mms`, `com.android.camera2`, gói có đoạn tên `dialer` / `contacts` / `incallui` / `mms` / `messaging` / `camera` / `settings` / `notes` / `note` hoặc đoạn bắt đầu `gallery` (app lõi CHỈ vào nhóm này); (2) app hệ thống không phải dịch vụ Google, không thuộc danh sách mạng xã hội, là `com.android.*` hoặc có đoạn tên `clock` / `alarmclock` / `deskclock` / `calculator` / `calculator2` / `filemanager` / `myfiles` / `files` / `fileexplorer` / `soundrecorder` / `recorder` / `calendar` / `weather` / `weather2` / `compass`. Không lấy mọi app hệ thống "không khai loại" (review: phần lớn app cài sẵn của hãng như vậy, Tiện ích thành bãi chứa, thư mục hãng không đủ app) |
| MANUFACTURER | gói bắt đầu một tiền tố hãng: `com.coloros.`, `com.oppo.`, `com.heytap.`, `com.oplus.`, `com.realme.`, `com.oneplus.`, `com.samsung.`, `com.sec.`, `com.miui.`, `com.xiaomi.`, `com.mi.`, `com.vivo.`, `com.bbk.`, `com.iqoo.`, `com.huawei.`, `com.hihonor.`, `com.vinsmart.`, `com.vsmart.`, `com.transsion.`, `com.motorola.`, `com.hmdglobal.`, `com.asus.`, `com.sonymobile.`, `com.lge.` |

App launcher của mình (`BuildConfig.APPLICATION_ID`) không vào nhóm nào (truyền `selfPackage`). **Sau review (2026-11-26, đã thay bằng QĐ 13 khi mở lại):** ~~app lõi của máy không vào nhóm nào, kể cả Tiện ích / Ứng dụng của hãng: `GOOGLE_CORE` + `com.android.settings`, `com.android.vending`, `com.android.mms`, `com.android.camera2` + gói có một đoạn tên là `dialer`, `contacts`, `incallui`, `mms`, `messaging`, `camera`, `settings` (vd `com.samsung.android.dialer`, `com.oplus.camera`). MANUFACTURER chỉ nhận app hệ thống (app hãng khác tải từ CH Play không vào thư mục của hãng máy). Máy Google (Pixel): ẩn hàng "Ứng dụng của hãng" vì trùng nhóm Google.~~

## Ngoài phạm vi / ghi để sau

- Gom tự động cho app cài sau; hoàn tác bằng nút; bỏ chọn từng app trong sheet.
- Nhóm Mua sắm / Ngân hàng; bước Onboarding (FL Q6); dồn app sang trang khác.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `domain/model/AppGroupKind.kt`, `domain/category/AppGroupPlanner.kt` | Tạo | 1 |
| `domain/repository/LayoutRepository.kt`, `data/layout/LayoutRepositoryImpl.kt`, `data/layout/LayoutReconciler.kt` (`groupIntoFolder`) | Sửa | 1 |
| `ui/settings/AppGroupsViewModel.kt`, `ui/settings/AppGroupSheet.kt` | Tạo | 2 |
| `ui/settings/GeneralSettingsScreen.kt` | Sửa | 2 |
| `res/values*/strings.xml` (9 file) | Sửa | 2 |
| `docs/Launcher-Features.md`, `docs/lessons/settings-and-data.md`, `docs/room-schema.md` (ghi chú `title` của thư mục gom), `docs/plans/BACKLOG.md`, `docs/plans/README.md` | Sửa | 3 |

## Global Constraints

- Luật phụ thuộc `ui → domain ← data`; `AppGroupPlanner` thuần Kotlin.
- Mọi ghi bố cục qua `LayoutReconciler.mutex` + một `db.withTransaction`; kiểm lại hàng trong transaction (UI có thể cũ).
- Thư mục ≤ `FolderPlanner.MAX_ITEMS`; thư mục mới cần ≥ 2 app.
- App ẩn không lộ: chỉ đọc app từ `pages` (đã bỏ app ẩn).
- Chuỗi mới đủ 9 ngôn ngữ (số đếm dùng `plurals`); giữ CRLF khi sửa file từ VM.

## Review Focus

1. Trang có widget: dồn không đụng widget, app bước qua ô bị widget phủ (`GridRemovePlanner` theo dãy ô tự do).
2. Gom hết app của một trang (không còn item) → trang bị gỡ, Home không còn trang trống ở giữa; trang đầu không bị gỡ khi là trang duy nhất.
3. Bố cục đổi giữa lúc sheet mở (cài/gỡ app, Home đang sửa): transaction chỉ lấy hàng còn là app phẳng trên lưới; < 2 thì không tạo thư mục.
4. Thư mục trùng tên nằm ở dock: thêm vào đó được; thư mục đã đầy 135: không thêm, báo.
5. Nhấn "Gom nhóm" hai lần nhanh: chỉ một lần ghi.

---

### Task 1: Domain + ghi bố cục
**Bắt buộc kiểm tay:** không (kiểm ở bài cuối).
**Interfaces:** Produces `enum class AppGroupKind { GOOGLE, SOCIAL, GAMES, ENTERTAINMENT, UTILITIES, MANUFACTURER }`; `AppGroupPlanner.matches(kind: AppGroupKind, app: AppItem, selfPackage: String): Boolean`; `AppGroupPlanner.candidates(kind, pages: List<HomePage>, selfPackage): List<PlacedApp>` (thứ tự `HomePage.index`, rồi `y`, rồi `x`); `LayoutRepository.groupIntoFolder(title: String, itemIds: List<Long>, folderId: Long?): Int` (số app đã chuyển; 0 = không ghi gì).
- [x] **Step 1:** `AppGroupKind`, `AppGroupPlanner` theo bảng "Nhận diện nhóm".
- [x] **Step 2:** `LayoutReconciler.groupIntoFolder`: lọc lại `itemIds` còn là app `CONTAINER_HOME`, sắp theo hạng trang / `gridY` / `gridX`; `folderId` khác null thì phải là thư mục ở lưới hoặc dock, nhận tối đa `MAX_ITEMS - số con`; null thì cần ≥ 2, chèn thư mục `title` ở ô app đầu; chuyển app (rank nối tiếp); dồn từng trang theo QĐ 8 (xử lý app bị gỡ từ cuối lên đầu theo thứ tự đọc, mỗi bước `GridRemovePlanner.plan` trên slot hiện thời); `deleteEmptyPages`; log `groupIntoFolder moved=… pages=…`.
- [x] **Step 3:** `LayoutRepository` + `LayoutRepositoryImpl` ủy quyền.

### Task 2: Cài đặt → Chung + sheet xem trước
**Bắt buộc kiểm tay:** không (kiểm ở bài cuối).
**Interfaces:** Consumes Task 1. Produces `AppGroupsViewModel.groups: StateFlow<List<AppGroupState>?>` với `AppGroupState(kind, title: String, apps: List<PlacedApp>, folderId: Long?, enabled: Boolean)`; `AppGroupsViewModel.apply(state): Unit` (sự kiện kết quả qua `results: Flow<GroupResult>`).
- [x] **Step 1:** `AppGroupsViewModel` (Hilt): `combine(layout.pages, layout.dock)` → 6 `AppGroupState`; tên nhóm lấy từ chuỗi (context app), MANUFACTURER theo `Build.MANUFACTURER`; thư mục trùng tên theo QĐ 6; `apply` chặn chạm đúp (cờ đang ghi).
- [x] **Step 2:** `GeneralSettingsScreen`: `Header` "Gom nhóm ứng dụng" + 6 `Value` (`titleText`, giá trị = số app / "Chưa đủ ứng dụng" / "Đã gom", `enabled` theo QĐ 7) + `Footer` mô tả; `observe()` thu `groups`; bấm hàng mở `AppGroupSheet`.
- [x] **Step 3:** `AppGroupSheet`: `DsBottomSheet(title = "Tên · N ứng dụng")`; `HorizontalPager` trang 3×3 (icon + nhãn, nạp bằng `AppRepository.icon`), chấm trang khi > 1 trang; "Hủy" + "Gom nhóm" / "Thêm N ứng dụng"; Toast kết quả.
- [x] **Step 4:** Chuỗi 9 ngôn ngữ.
- [x] **Step 5: Kịch bản kiểm (Oppo)** — (a) Cài đặt → Chung thấy 6 hàng với số app; (b) bấm "Mạng xã hội": sheet đúng app, vuốt trang nếu > 9; (c) "Gom nhóm": về Home, thư mục ở ô app đầu, các app còn lại dồn lên, widget đứng yên, trang trống biến mất; (d) mở lại Cài đặt: hàng "Mạng xã hội" giờ "Đã gom" (mờ); cài thêm một app mạng xã hội rồi bấm: "Thêm 1 ứng dụng", vào đúng thư mục cũ; (e) "Hủy" / vuốt sheet xuống: không đổi gì; (f) nhóm < 2 app: hàng mờ; (g) app ẩn không có trong sheet nào.

### Task 4: Luật nhóm mới + tên nhóm dùng chung
**Bắt buộc kiểm tay:** không (kiểm ở bài cuối).
**Interfaces:** Produces `AppGroupKind` thứ tự `UTILITIES, GOOGLE, SOCIAL, GAMES, ENTERTAINMENT, MANUFACTURER`; `AppGroupPlanner.seedGroups(apps: List<AppItem>, selfPackage: String): List<Pair<AppGroupKind, List<AppItem>>>` (theo thứ tự nhóm, mỗi app một nhóm, bỏ nhóm < 2, giữ thứ tự [apps]); `interface AppGroupTitleProvider { fun title(kind: AppGroupKind): String }` (domain/category), bản thật `data/apps/AppGroupTitleProviderImpl` (`AppLocaleContext.wrap`, hãng = `Build.MANUFACTURER`), bind ở `RepositoryModule`.
- [x] **Step 1:** `matches`: app lõi (`isCore`: danh sách gói + đoạn tên `dialer`, `contacts`, `incallui`, `mms`, `messaging`, `camera`, `settings`, `notes`, `note`, đoạn bắt đầu `gallery`) chỉ thuộc UTILITIES; UTILITIES = lõi, hoặc app hệ thống không phải dịch vụ Google có loại `OTHER` / `UNDEFINED` / `PRODUCTIVITY`; GOOGLE = dịch vụ Google (`com.google.*`, Chrome, CH Play) trừ `GOOGLE_CORE`; các nhóm khác loại app lõi.
- [x] **Step 2:** `seedGroups`; `AppGroupTitleProvider` + impl + binding; `AppGroupsViewModel` lấy tên từ provider (bỏ `setTitles`); `GeneralSettingsScreen` bỏ `folderTitle` / `brandName`.

### Task 5: Seed có thư mục
**Bắt buộc kiểm tay:** có — bố cục lần đầu phụ thuộc danh sách app thật của máy (Oppo, dock theo vai trò), `Workflow.md` mục 3.
**Interfaces:** Consumes Task 4. `BuildDefaultLayoutUseCase(..., folders: List<Pair<String, List<AppItem>>> = emptyList())`: sau app ghim là các thư mục (bản nháp `PlacedFolder`, `itemId = 0`, `childIds` rỗng), rồi app phẳng; thành viên thư mục trùng dock bị bỏ, thư mục còn < 2 app thì trả app về phẳng.
- [x] **Step 1:** `BuildDefaultLayoutUseCase` theo trên.
- [x] **Step 2:** `LayoutReconciler.seedIfEmpty`: `seedGroups` trên app không ở dock, tên từ `AppGroupTitleProvider`; ghi hàng thư mục (`title`) + app con (`container = id thư mục`, `rank` theo thứ tự) cùng transaction; log `seedIfEmpty … folders=N`.
- [x] **Step 3: Kịch bản kiểm (Oppo)** — gỡ cài đặt / xóa dữ liệu app → mở Home: (a) trang 1 Đồng hồ + Lịch, launcher, Tiện ích, Google, rồi các thư mục khác, rồi app phẳng; (b) dock 4 app không nằm trong thư mục nào; (c) mở Tiện ích có Danh bạ / Cài đặt / Thư viện ảnh / Ghi chú…; (d) Cài đặt → Chung: các hàng có thư mục đều "Đã gom" (mờ); nhóm không đủ 2 app lúc seed thì không có thư mục, hàng ghi số app; kéo một app ra khỏi thư mục Google (thư mục còn ≥ 2 app, nếu không thư mục tự giải tán theo D9) → hàng Google sáng "1 ứng dụng" → gom lại vào đúng thư mục; (e) cài thêm một game khi đã có thư mục Trò chơi → hàng Trò chơi sáng "Thêm 1"; (f) Tiện ích không chứa chợ ứng dụng / chủ đề của hãng (vào "Ứng dụng của hãng").

### Task 3: Docs
- [x] `Launcher-Features.md`, `lessons/settings-and-data.md`, `room-schema.md`, `BACKLOG.md` (B3 → Đã xong, B5 ghi phần đã có), `plans/README.md`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Task 2 Step 5 (a)–(g) + hồi quy: kéo app vào / ra thư mục được gom, đổi tên thư mục, đổi cỡ lưới sau khi gom.
**Kết quả kiểm chứng (máy, ngày):** Chủ dự án build và yêu cầu đóng plan 2026-11-26 14:22 (Task 1–2 đã merge PR #6, Task 4–5 commit `dc2aa1e`); không gửi số liệu kiểm chi tiết. `testDebugUnitTest`: 4 test `GridMetricsTest` hỏng có từ trước (B9), không liên quan.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-26 | 4–5 | Mở lại plan. Code: `AppGroupKind` đổi thứ tự (Tiện ích đầu); `AppGroupPlanner.matches` (app lõi chỉ vào Tiện ích, thêm Ghi chú / Thư viện ảnh, CH Play vào Google, Tiện ích nhận cả loại `PRODUCTIVITY`) + `seedGroups`; `AppGroupTitleProvider` (domain) + `AppGroupTitleProviderImpl` (data, `AppLocaleContext.wrap`, tên hãng từ `Build.MANUFACTURER`) bind ở `RepositoryModule`; `AppGroupsViewModel` lấy tên từ provider (bỏ `setTitles`), `GeneralSettingsScreen` bỏ `folderTitle` / `brandName`; `BuildDefaultLayoutUseCase(folders = …)` đặt thư mục ngay sau app ghim; `seedIfEmpty` ghi thư mục + app con, log `folders=N`. Nhánh `an` tạo lại từ `master` (`9bed07c`) | Chưa build. Ruling: Tiện ích nhận thêm app hệ thống loại `PRODUCTIVITY` (vd Lịch, Ghi âm của hãng) — sai thì vài app năng suất cài sẵn vào Tiện ích thay vì nằm phẳng / thư mục hãng. Ruling: đoạn tên gói `note`/`notes`/`camera`/`gallery*` coi là app lõi cả khi không phải app hệ thống — app ghi chú / máy ảnh tải về cũng vào Tiện ích |
| 2026-11-26 | 4–5 | Review độc lập (agent): không lỗi biên dịch thấy được, seed đúng (mỗi app một chỗ, dock bị loại, app con được `catchUp` coi là đã có hàng). Sửa: **(Important)** UTILITIES chỉ nhận app lõi + app hệ thống `com.android.*` / có đoạn tên tiện ích (đồng hồ, máy tính, tệp, ghi âm, lịch, thời tiết, la bàn), không còn nhận mọi app hệ thống `OTHER`/`UNDEFINED`/`PRODUCTIVITY` (bỏ ruling `PRODUCTIVITY` ở dòng trên); tên nhóm đọc lại khi đổi ngôn ngữ (`AppGroupsViewModel.onScreenLocale`, màn gọi với `AppLocaleContext.localeKey`); kịch bản kiểm ghi điều kiện; bảng "Nhận diện nhóm" cập nhật | Để lại (minor): đổi ngôn ngữ sau seed thì thư mục seed mang tên cũ, hàng Cài đặt không còn khớp (đánh đổi đã chấp nhận); `title()` gọi `AppLocaleContext.wrap` mỗi lần (hàm đã cache context theo tag) |
| 2026-11-26 | — | Merge `origin/master` vào `an` (`522db9e`; conflict duy nhất `PerfSwitches.kt`, giữ cả `flatSearchCards` lẫn `noIconGlass` / `noIconShader`). Chủ dự án chạy `assembleDebug :app:testDebugUnitTest`: build xanh, 108/112 test đạt; 4 test `GridMetricsTest` hỏng có từ trước (backlog B9), không liên quan AG | Chủ dự án: bỏ qua B9 |
| 2026-11-26 | 1–2 | Review độc lập (agent): không thấy lỗi biên dịch chắc chắn; Pager không cần opt-in (BOM 2026.09.00, `HomeWidgetPicker` dùng y vậy). Sửa: loại app lõi khỏi mọi nhóm, MANUFACTURER cần app hệ thống, ẩn hàng hãng trên máy Google, tên hàng hãng không lồng chữ khi thiếu `Build.MANUFACTURER`, `apply` bắt lỗi DB (Toast thay vì crash) | Để lại (minor): thư mục gần đầy 135 thì số "Thêm N" có thể lệch số thật vì UI không đếm app con tạm không khả dụng; thư mục đầy hiện "Đã gom"; mục hiện trễ một nhịp lúc mở màn |
| 2026-11-26 | 1–2 | Code: `AppGroupKind`, `AppGroupPlanner`; `LayoutReconciler.groupIntoFolder` + `compactAfterBulkRemoval` (dồn từng trang bằng `GridRemovePlanner`, gỡ trang vừa hết item, không đụng trang trống có sẵn); `LayoutRepository.groupIntoFolder`; `AppGroupsViewModel` (tên nhóm do màn đưa vào để đúng ngôn ngữ app); `AppGroupSheet` (`HorizontalPager` 3×3, chấm trang, Hủy / Gom nhóm / Thêm N); `GeneralSettingsScreen` thêm mục; 15 chuỗi × 9 ngôn ngữ (số đếm dùng `plurals`, đọc bằng `resources.getQuantityString` vì dự án chưa dùng `pluralStringResource`) | Chưa build. Ghi chú: màn Chung thu `LayoutRepository.pages`; nếu DB bố cục còn rỗng (chưa từng mở Home) thì lần thu đầu chạy seed như khi mở Home, cùng bản seed nên vô hại |
| 2026-11-26 | — | Đọc `BuildDefaultLayoutUseCase`, `LayoutReconciler` (`seedIfEmpty`, `createFolder`, `addToFolder`, `reflowInTransaction`, `compactPageAfterRemoval`, `deleteEmptyPages`), `GridRemovePlanner`, `FolderPlanner`, `AppCategory*`, `GeneralSettingsScreen`, `ChangeIconSettingsScreen` / `ChangeIconSheet`, `DsBottomSheet`; thiết kế duyệt trong chat; viết plan | Chủ dự án cho làm liền |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Trang có widget: dồn không đụng widget | Đọc code: `compactAfterBulkRemoval` dùng `GridRemovePlanner` (widget không dịch); review độc lập xác nhận | Đạt (đọc code) |
| 2 | Gom hết app của một trang → gỡ trang, giữ ≥ 1 trang | `EmptyPagePrunePlanner` với `used` = trang không bị đụng + trang còn hàng | Đạt (đọc code) |
| 3 | Bố cục đổi giữa lúc sheet mở | `groupIntoFolder` lọc lại hàng trong transaction, < 2 thì 0 | Đạt (đọc code) |
| 4 | Thư mục trùng tên ở dock / đầy 135 | Tìm thư mục ở lưới rồi dock; nhận tối đa `MAX_ITEMS - số con` | Đạt (đọc code); thư mục đầy hiện "Đã gom" (minor, để lại) |
| 5 | Nhấn "Gom nhóm" hai lần | Cờ `writing` trong `AppGroupsViewModel.apply` + sheet đóng ngay | Đạt (đọc code) |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `Launcher-Features.md`: TT#10 (Thư mục) thêm AG.
- `lessons/settings-and-data.md`: mục AG (4 bài học).
- `room-schema.md`: `title` của thư mục gom sẵn / gom từ Cài đặt.
- `project_structure.md`: tệp AG (provider tên nhóm).
- `plans/BACKLOG.md`: B3 → Đã xong (AG), B5 ghi phần đã có; `plans/README.md`: AG Xong.
