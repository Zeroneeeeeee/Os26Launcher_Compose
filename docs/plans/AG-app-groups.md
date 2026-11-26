# AG — Gom nhóm app vào thư mục từ Cài đặt → Chung (B3) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Chủ dự án cho làm liền (2026-11-26) rồi build/kiểm một thể; tick Task khi chủ dự án báo ổn. Không có test tự động; kiểm tay trên Oppo PKD130. Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (thiết kế duyệt trong chat)
**Trạng thái:** Đang làm Task 1–2 (phiên VM không build được)
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

## Nhận diện nhóm (`AppGroupPlanner.matches`)

| Nhóm | Luật |
|---|---|
| GOOGLE | gói bắt đầu `com.google.` hoặc `com.android.chrome`, TRỪ app lõi: `com.google.android.dialer`, `.contacts`, `.apps.messaging`, `.deskclock`, `.calculator`, `.calendar`, `.GoogleCamera`, `.apps.nbu.files`, `.documentsui`, `.packageinstaller`, `.permissioncontroller`, `.gms`, `.gsf`, `com.android.vending` (không phải tiền tố google nhưng ghi rõ) |
| SOCIAL | `AppCategory.SOCIAL` hoặc gói trong danh sách: Facebook (`com.facebook.katana`, `.orca`, `.lite`, `.mlite`), Zalo `com.zing.zalo`, TikTok (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`), Instagram `com.instagram.android`, Threads `com.instagram.barcelona`, Telegram (`org.telegram.messenger`, `org.thunderdog.challegram`), WhatsApp (`com.whatsapp`, `com.whatsapp.w4b`), X `com.twitter.android`, Discord `com.discord`, Viber `com.viber.voip`, Snapchat `com.snapchat.android`, LinkedIn `com.linkedin.android`, Pinterest `com.pinterest`, Reddit `com.reddit.frontpage`, LINE `jp.naver.line.android`, KakaoTalk `com.kakao.talk`, WeChat `com.tencent.mm`, Skype `com.skype.raider` |
| GAMES | `AppCategory.GAME` |
| ENTERTAINMENT | `AppCategory.ENTERTAINMENT` (video / audio / ảnh) và không phải app hệ thống (khỏi lấy Thư viện ảnh / Nhạc của máy) |
| UTILITIES | app hệ thống và loại `OTHER` / `UNDEFINED` (cùng luật nhóm "Tiện ích" của Thư viện, `AppCategoryGrouper`) |
| MANUFACTURER | gói bắt đầu một tiền tố hãng: `com.coloros.`, `com.oppo.`, `com.heytap.`, `com.oplus.`, `com.realme.`, `com.oneplus.`, `com.samsung.`, `com.sec.`, `com.miui.`, `com.xiaomi.`, `com.mi.`, `com.vivo.`, `com.bbk.`, `com.iqoo.`, `com.huawei.`, `com.hihonor.`, `com.vinsmart.`, `com.vsmart.`, `com.transsion.`, `com.motorola.`, `com.hmdglobal.`, `com.asus.`, `com.sonymobile.`, `com.lge.` |

App launcher của mình (`BuildConfig.APPLICATION_ID`) không vào nhóm nào (truyền `selfPackage`). **Sau review (2026-11-26):** app lõi của máy không vào nhóm nào, kể cả Tiện ích / Ứng dụng của hãng: `GOOGLE_CORE` + `com.android.settings`, `com.android.vending`, `com.android.mms`, `com.android.camera2` + gói có một đoạn tên là `dialer`, `contacts`, `incallui`, `mms`, `messaging`, `camera`, `settings` (vd `com.samsung.android.dialer`, `com.oplus.camera`). MANUFACTURER chỉ nhận app hệ thống (app hãng khác tải từ CH Play không vào thư mục của hãng máy). Máy Google (Pixel): ẩn hàng "Ứng dụng của hãng" vì trùng nhóm Google.

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
- [ ] **Step 1:** `AppGroupKind`, `AppGroupPlanner` theo bảng "Nhận diện nhóm".
- [ ] **Step 2:** `LayoutReconciler.groupIntoFolder`: lọc lại `itemIds` còn là app `CONTAINER_HOME`, sắp theo hạng trang / `gridY` / `gridX`; `folderId` khác null thì phải là thư mục ở lưới hoặc dock, nhận tối đa `MAX_ITEMS - số con`; null thì cần ≥ 2, chèn thư mục `title` ở ô app đầu; chuyển app (rank nối tiếp); dồn từng trang theo QĐ 8 (xử lý app bị gỡ từ cuối lên đầu theo thứ tự đọc, mỗi bước `GridRemovePlanner.plan` trên slot hiện thời); `deleteEmptyPages`; log `groupIntoFolder moved=… pages=…`.
- [ ] **Step 3:** `LayoutRepository` + `LayoutRepositoryImpl` ủy quyền.

### Task 2: Cài đặt → Chung + sheet xem trước
**Bắt buộc kiểm tay:** không (kiểm ở bài cuối).
**Interfaces:** Consumes Task 1. Produces `AppGroupsViewModel.groups: StateFlow<List<AppGroupState>?>` với `AppGroupState(kind, title: String, apps: List<PlacedApp>, folderId: Long?, enabled: Boolean)`; `AppGroupsViewModel.apply(state): Unit` (sự kiện kết quả qua `results: Flow<GroupResult>`).
- [ ] **Step 1:** `AppGroupsViewModel` (Hilt): `combine(layout.pages, layout.dock)` → 6 `AppGroupState`; tên nhóm lấy từ chuỗi (context app), MANUFACTURER theo `Build.MANUFACTURER`; thư mục trùng tên theo QĐ 6; `apply` chặn chạm đúp (cờ đang ghi).
- [ ] **Step 2:** `GeneralSettingsScreen`: `Header` "Gom nhóm ứng dụng" + 6 `Value` (`titleText`, giá trị = số app / "Chưa đủ ứng dụng" / "Đã gom", `enabled` theo QĐ 7) + `Footer` mô tả; `observe()` thu `groups`; bấm hàng mở `AppGroupSheet`.
- [ ] **Step 3:** `AppGroupSheet`: `DsBottomSheet(title = "Tên · N ứng dụng")`; `HorizontalPager` trang 3×3 (icon + nhãn, nạp bằng `AppRepository.icon`), chấm trang khi > 1 trang; "Hủy" + "Gom nhóm" / "Thêm N ứng dụng"; Toast kết quả.
- [ ] **Step 4:** Chuỗi 9 ngôn ngữ.
- [ ] **Step 5: Kịch bản kiểm (Oppo)** — (a) Cài đặt → Chung thấy 6 hàng với số app; (b) bấm "Mạng xã hội": sheet đúng app, vuốt trang nếu > 9; (c) "Gom nhóm": về Home, thư mục ở ô app đầu, các app còn lại dồn lên, widget đứng yên, trang trống biến mất; (d) mở lại Cài đặt: hàng "Mạng xã hội" giờ "Đã gom" (mờ); cài thêm một app mạng xã hội rồi bấm: "Thêm 1 ứng dụng", vào đúng thư mục cũ; (e) "Hủy" / vuốt sheet xuống: không đổi gì; (f) nhóm < 2 app: hàng mờ; (g) app ẩn không có trong sheet nào.

### Task 3: Docs
- [ ] `Launcher-Features.md`, `lessons/settings-and-data.md`, `room-schema.md`, `BACKLOG.md` (B3 → Đã xong, B5 ghi phần đã có), `plans/README.md`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Task 2 Step 5 (a)–(g) + hồi quy: kéo app vào / ra thư mục được gom, đổi tên thư mục, đổi cỡ lưới sau khi gom.
**Kết quả kiểm chứng (máy, ngày):**

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-26 | 1–2 | Review độc lập (agent): không thấy lỗi biên dịch chắc chắn; Pager không cần opt-in (BOM 2026.09.00, `HomeWidgetPicker` dùng y vậy). Sửa: loại app lõi khỏi mọi nhóm, MANUFACTURER cần app hệ thống, ẩn hàng hãng trên máy Google, tên hàng hãng không lồng chữ khi thiếu `Build.MANUFACTURER`, `apply` bắt lỗi DB (Toast thay vì crash) | Để lại (minor): thư mục gần đầy 135 thì số "Thêm N" có thể lệch số thật vì UI không đếm app con tạm không khả dụng; thư mục đầy hiện "Đã gom"; mục hiện trễ một nhịp lúc mở màn |
| 2026-11-26 | 1–2 | Code: `AppGroupKind`, `AppGroupPlanner`; `LayoutReconciler.groupIntoFolder` + `compactAfterBulkRemoval` (dồn từng trang bằng `GridRemovePlanner`, gỡ trang vừa hết item, không đụng trang trống có sẵn); `LayoutRepository.groupIntoFolder`; `AppGroupsViewModel` (tên nhóm do màn đưa vào để đúng ngôn ngữ app); `AppGroupSheet` (`HorizontalPager` 3×3, chấm trang, Hủy / Gom nhóm / Thêm N); `GeneralSettingsScreen` thêm mục; 15 chuỗi × 9 ngôn ngữ (số đếm dùng `plurals`, đọc bằng `resources.getQuantityString` vì dự án chưa dùng `pluralStringResource`) | Chưa build. Ghi chú: màn Chung thu `LayoutRepository.pages`; nếu DB bố cục còn rỗng (chưa từng mở Home) thì lần thu đầu chạy seed như khi mở Home, cùng bản seed nên vô hại |
| 2026-11-26 | — | Đọc `BuildDefaultLayoutUseCase`, `LayoutReconciler` (`seedIfEmpty`, `createFolder`, `addToFolder`, `reflowInTransaction`, `compactPageAfterRemoval`, `deleteEmptyPages`), `GridRemovePlanner`, `FolderPlanner`, `AppCategory*`, `GeneralSettingsScreen`, `ChangeIconSettingsScreen` / `ChangeIconSheet`, `DsBottomSheet`; thiết kế duyệt trong chat; viết plan | Chủ dự án cho làm liền |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Tài liệu chung đã cập nhật (điền ở bước 7)
