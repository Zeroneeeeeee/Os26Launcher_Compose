# AB — Mục "Liên hệ với chúng tôi" (6.1 Đánh giá, 6.2 Ứng dụng khác, 6.3 Chính sách bảo mật) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/ab-about-section` · **Worktree:** `E:\SourceTree\IosLauncher-wt\AB-about-section` · **PR:** #49 · **Bắt đầu:** 2026-11-12 · **Plan duyệt:** 2026-11-13 (điều phối)
**Trạng thái:** Đã kiểm Live 4 một phần (2026-11-13), chờ điều phối review toàn nhánh và chủ dự án kiểm các mục còn lại (Tasks 1–4 qua review điều phối 2026-11-13)
**Khóa:** không (không đổi schema Room, không cần Oppo)

**Goal:** Ba mục cuối của hub Cài đặt (G2d) có nội dung thật: (6.1) hộp thoại đánh giá có hàng sao (chỉ để minh họa), nút "Gửi đánh giá" luôn mở trang Google Play của app bất kể số sao; (6.2) danh sách ứng dụng khác của nhà phát triển (toàn chỗ giữ chỗ, ẩn hẳn khỏi hub nếu chưa có app nào khi phát hành); (6.3) chính sách bảo mật mở trong trình duyệt. Mọi nội dung chưa có (URL chính sách, tên nhà phát triển, danh sách app) là **chỗ giữ chỗ có đánh dấu** và một task Gradle làm `bundleRelease` thất bại chừng nào còn chỗ giữ chỗ (quyết định FD5). Link Play ghép từ `applicationId`.

**Architecture:** Giữ nguyên khung G2d. 6.2 và 6.3 là hai lớp `SettingsScreen` mới (đúng công thức "thêm màn"). 6.1 không phải màn: thêm **một bảng "hành động không màn"** cạnh bảng màn trong `SettingsScreens`; hub gặp id nằm trong bảng đó thì chạy hành động tại chỗ (hộp thoại trên hub) thay vì `ctx.open`. Mở link ngoài (Play, trình duyệt) đi qua một helper `AboutLinks` dùng `Intent` thuần, không thêm thư viện.

**Tech Stack:** Views/XML, Material 1.14.0 (`MaterialAlertDialogBuilder`, đã có), `RatingBar` của platform. **Không thêm dependency mới** (xem "Vì sao chọn thiết kế này" để biết hai thư viện đã xét và bỏ).

**Spec:** bản nháp Figma 6.1–6.3 trong `docs/Figma-Draft.md` mục 3 và quyết định FD2, FD5, FD9; hub: `plans/G2d-settings-hub.md`, `knowledgements/G2d-settings-hub.md`. Không có mục TT# riêng trong `Launcher-Features.md`.

## Vì sao chọn thiết kế này

**Vấn đề cần giải** (đọc code 2026-11-12):
- Hub khai sẵn `RATE`, `MORE_APPS`, `PRIVACY` là `HubTarget.Screen`; mục tự bật khi màn được đăng ký trong `SettingsScreens`; `ctx.open(id)` luôn `startActivity(SettingsActivity)` với màn tương ứng (G2d).
- Công thức G2d: không sửa bảng hub của mục bản nháp.
- FD2: giữ kiểu hàng/danh sách hiện tại, không dựng kiểu hình ảnh của bản nháp.
- `SettingsRow.Value` chỉ có tiêu đề và giá trị một dòng; `SettingsRow.Footer` là dòng chữ nhỏ cuối danh sách.
- Manifest đã có `INTERNET`; app không có WebView hay `androidx.browser` trong dependency (đọc `app/build.gradle.kts`, `libs.versions.toml`).
- Có sẵn mẫu cổng kiểm trước phát hành: `checkNoStoreTestWallpaper` (`app/build.gradle.kts` cuối file, nối vào `bundleRelease`).

### A. Cách mô hình hóa "Đánh giá" (hành động, không phải màn)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A1. Bảng hành động không màn** | `SettingsScreens` thêm bảng `actions: Map<SettingsScreenId, (SettingsScreenContext) -> SettingsAction>`; `isRegistered` tính cả bảng này; `SettingsHubScreen.onValueClicked` thấy id thuộc bảng hành động thì tạo và chạy hành động (hộp thoại trên hub), giữ tham chiếu để `onDestroy` dismiss | Không sửa `SettingsHubLayout` (mục RATE vẫn `HubTarget.Screen(RATE)`, enum đã có); không Activity trống; nhánh sau cần hành động khác chỉ thêm một dòng; mục tự bật như các màn | Hub thêm ~10 dòng (nhánh `if`, một trường, `onDestroy`); "màn" có hai bảng nên người đọc phải biết cả hai | **Có** |
| A2. Kiểu đích mới `HubTarget.Action` | Thêm `data class Action(val run: ...)` vào sealed interface, sửa mục RATE trong `SettingsHubLayout`, sửa `isAvailable` và `onValueClicked` | Kiểu rõ ràng ở bảng hub | Sửa bảng hub (trái công thức G2d, mục bản nháp phải khai sẵn); đổi sealed interface nên mọi `when` phải thêm nhánh; nhánh khác đang rebase lên hub có thể xung đột | Không |
| A3. Màn trong suốt | Đăng ký `RATE` thành một `SettingsScreen` rỗng, dựng hộp thoại ngay lúc dựng | Không sửa gì ngoài đăng ký | Mở một `SettingsActivity` thứ hai (hiện nền trống/nháy trước hộp thoại), thêm một mục back stack; Back/hủy hộp thoại phải `finish()` thêm; xoay/dựng lại Activity dựng lại hộp thoại ngoài ý muốn (vì màn xác định bởi extra) | Không |

Chọn A1: ít đụng khung nhất, không đổi hành vi hai bảng đã có, và lỗi "dựng lại Activity mở lại hộp thoại" của A3 không xảy ra (hành động chỉ chạy khi bấm).

### B. "Đánh giá": Play In-App Review hay hộp thoại tự làm + intent Play

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **B1. Hộp thoại tự làm + intent Play** | `MaterialAlertDialogBuilder` + `RatingBar` (minh họa); "Gửi đánh giá" luôn mở trang Play (`market://details?id=<applicationId>`, dự phòng `https://play.google.com/store/apps/details?id=`) bất kể số sao (quyết định AB3) | Khớp bản nháp về hình (sao, hai nút); không dependency; chạy cả máy không có Play (dự phòng web, sau đó báo lỗi); hành vi xác định; không chia nhánh theo sao nên không có rủi ro lọc đánh giá | Không gửi đánh giá ngay trong app (người dùng phải sang Play); trong bản debug/chưa phát hành, Play báo "không tìm thấy app" (đo khi kiểm: xem Task 2) | **Có** |
| B2. Play In-App Review (`com.google.android.play:review-ktx`) | `ReviewManager.requestReviewFlow` rồi `launchReviewFlow` | Người dùng đánh giá không rời app | (1) Tài liệu Android nói rõ **không nên có nút kích hoạt API** vì hạn mức theo thời gian: lần bấm sau có thể không hiện gì, và cho trường hợp này khuyên **chuyển hướng sang Play Store**; (2) app **không được hỏi gì trước thẻ đánh giá** (kể cả "Bạn thấy app thế nào?"), mà bản nháp lại có bước chọn sao + chia nhánh; (3) không tùy biến được thẻ; (4) kéo thêm thư viện Play (bản đã kiểm có trên Google Maven: `review-ktx` 2.0.0, 2.0.1, 2.0.2) và chỉ chạy trên máy có Play; (5) bản dựng chưa lên Play không thử được | Không |

(đọc tài liệu: developer.android.com/guide/playcore/in-app-review, các mục "Quotas" và "When to request", 2026-11-12). Kết luận: B2 mâu thuẫn trực tiếp với thiết kế bản nháp; B1 là cách chính thống cho một mục cố định trong Cài đặt.

**Rủi ro chính sách:** đã loại bỏ theo quyết định của chủ dự án 2026-11-13 (AB3): mọi số sao đều đi tới Google Play, không chia nhánh, không có email góp ý. Hàng sao chỉ là minh họa.

### C. "Chính sách bảo mật": WebView, Custom Tab hay mở trình duyệt

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| WebView trong app | Activity mới chứa `WebView` tải URL | Giống bản nháp (nội dung nằm trong màn) | Không có sẵn trong dự án nhưng là thành phần platform (không thêm dependency); khung Cài đặt là RecyclerView nên cần **Activity + layout + manifest riêng**, phá công thức G2d; phải tự xử lý offline (trang lỗi, nút thử lại), tải chậm, JS/DOM storage tắt, `onReceivedSslError`, mở link ra ngoài, Back trong lịch sử trang; trên máy thiếu/vô hiệu WebView thì lỗi; thêm diện tích tấn công và chi phí bảo trì cho một trang chữ tĩnh | Không |
| Custom Tab (`androidx.browser`) | `CustomTabsIntent.Builder().build().launchUrl(...)`, dự phòng `ACTION_VIEW` | Giao diện liền mạch, trình duyệt tự lo mạng/offline | Thêm dependency `androidx.browser`: bản **1.10.0 có trên Google Maven (đo 2026-11-12, đọc `group-index.xml`)**, nhưng tôi **chưa kiểm** tương thích với AGP 9.3.3 / compileSdk 37, nên chỉ thêm khi chủ dự án muốn | Giai đoạn sau (xem Q2) |
| **Mở trình duyệt bằng `ACTION_VIEW`** | Màn 6.3 gồm giải thích ngắn + hàng "Mở trong trình duyệt"; bắt `ActivityNotFoundException` và báo lỗi | Không dependency, không quyền mới (`INTERNET` vốn đã có và không cần cho cách này), trình duyệt lo offline và chứng chỉ; nội dung chính sách do trang web quyết, không phải build lại app khi chính sách đổi; khớp công thức G2d (một file màn) và FD2; nút "Mở trong trình duyệt" của bản nháp là đúng việc này | Người dùng rời app; không có nội dung trong màn (bản nháp có phần "nội dung tải từ web" nằm trong màn) | **Có** |

Offline: với ACTION_VIEW, khi không có mạng người dùng thấy trang lỗi của trình duyệt, app không phải xử lý thêm. Màn 6.3 hiện dòng chữ nói rõ "Chính sách được mở trên trình duyệt của máy" để không bất ngờ. (suy luận từ cách làm, chưa kiểm chứng trên máy tắt mạng; kịch bản kiểm ở Task 3.)

### D. Nơi đặt chỗ giữ chỗ và cách kiểm

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| **File riêng `res/values/about_config.xml`** (`translatable="false"`, mọi giá trị giữ chỗ chứa chuỗi `PLACEHOLDER`) | Chỗ duy nhất cần điền trước khi phát hành; task Gradle quét đúng một file; không xung đột với `strings.xml` của nhánh khác; resource nên dùng được từ code/XML | Một file mới trong `res/values` | **Có** |
| Khối cuối `strings.xml` | Một file chuỗi | Trộn chữ giao diện và cấu hình, task phải đoán chuỗi nào là giữ chỗ | Không |
| `buildConfigField` đọc `local.properties`/biến môi trường (như khóa OWM) | Giữ ngoài git | Quên đặt thì ra giá trị rỗng, **không báo lỗi**; thêm cấu hình cho từng máy build | Không |

Task Gradle `checkNoAboutPlaceholders`, mô phỏng `checkNoStoreTestWallpaper`: `@InputFile` là `about_config.xml`, đọc text, tìm mọi `<string name="...">giá trị</string>` có giá trị chứa `PLACEHOLDER`, ném `GradleException` liệt kê tên chuỗi; `bundleRelease` `dependsOn`; `assembleRelease`/`assembleDebug` không bị ảnh hưởng. Không dùng `Project` lúc chạy (tương thích configuration cache).

**Đánh đổi chấp nhận:** (1) hộp thoại đánh giá không gửi đánh giá ngay trong app; (2) chính sách bảo mật không hiện trong màn; (3) hành động và màn là hai bảng đăng ký; (4) hub có thêm một điều kiện ẩn mục theo cấu hình (xem AB4). Xét lại khi: chủ dự án muốn nội dung chính sách trong màn (Q2), hoặc có hơn một hành động không màn khác.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| FD2 | Giữ kiểu hiển thị hiện tại | Chủ dự án, 2026-11-12. 6.2 dùng hàng `Value` không có biểu tượng/mô tả/điểm sao |
| FD5 | 6.x dùng chỗ giữ chỗ, có bước kiểm trước phát hành | Chủ dự án, 2026-11-12 |
| FD9 | `versionName = "1.0.0"`, chân trang phiên bản có từ G2d | Đã làm ở G2d |
| AB1 | Không có hàng "Liên hệ" riêng | Bản nháp mục 3, "Liên hệ với chúng tôi" chỉ là tên nhóm |
| AB2 | Gói Play lấy từ `BuildConfig.APPLICATION_ID` (`gambi.global.os26launcher`, không có suffix cho bản debug) | Đọc `app/build.gradle.kts`; không là chỗ giữ chỗ |
| AB3 | Sao chỉ để minh họa; "Gửi đánh giá" luôn mở Google Play, mọi số sao; bỏ nhánh 1–3 sao, email góp ý, chỗ giữ chỗ email và dòng gợi ý hai nhánh | Chủ dự án, 2026-11-13 (Q1/Q4). Sao tùy chọn, nút luôn bật: khuyến nghị của điều phối |
| AB4 | Nếu khi phát hành chưa có ứng dụng khác thì mục 6.2 **ẩn** (không mờ): `bool about_more_apps_enabled` trong `about_config.xml`; hub bỏ qua mục khi tắt; cổng `checkNoAboutPlaceholders` nhắc lựa chọn này và bỏ qua chuỗi `about_more_app_*` khi tắt | Chủ dự án, 2026-11-13 (Q3). Cách làm: đề xuất của điều phối, chọn vì chỉ sửa một điều kiện trong `SettingsHubScreen.rows()`, không đụng bảng hub |
| AB5 | 6.3 chỉ mở trình duyệt (`ACTION_VIEW`); Custom Tab (`androidx.browser`) là lựa chọn sau | Điều phối, 2026-11-13 (Q2) |
| AB6 | 6.2 không có biểu tượng, mô tả, điểm sao (FD2) | Điều phối, 2026-11-13 (Q5) |

## Ngoài phạm vi / ghi để sau

- Gửi đánh giá ngay trong app (In-App Review), Custom Tab, WebView (Custom Tab là lựa chọn sau, AB5).
- Biểu tượng, mô tả ngắn, điểm sao của từng app ở 6.2, thẻ "Nổi bật" kiểu bản nháp (FD2 giữ kiểu hàng hiện tại). Hàng 6.2 chỉ có tên và nút "Tải".
- Tải danh sách app từ web (6.2 là danh sách tĩnh trong `about_config.xml`).
- Bản dịch EN: chuỗi mới chỉ có tiếng Việt như phần còn lại (LG làm sau).
- Việc điền nội dung thật (URL chính sách, tên nhà phát triển, danh sách app): của chủ dự án trước khi phát hành.

## Vùng file

Đường dẫn dưới `app/src/main/` trừ khi ghi khác. **Không có file độc quyền (mục 5.2).** Không sửa `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `LayoutReconciler`, `data/layout/db/*`, `core/glass/*`, `DragHelper`, `DragDropTarget`; không đổi schema Room; không sửa `AndroidManifest.xml`, `libs.versions.toml`, `SettingsKeys.kt`.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `java/gambi/global/os26launcher/ui/settings/SettingsScreen.kt` | Sửa: interface `SettingsAction`, bảng `actions`, `isRegistered`/`createAction`; thay 2 dòng giữ chỗ 6.2 và 6.3 (6.1 là dòng giữ chỗ trong bảng `actions`, không phải bảng `factories`) | Dùng chung (`ui/settings/*`) |
| `java/.../ui/settings/SettingsHubScreen.kt` | Sửa: nhánh chạy hành động, trường giữ hành động, `onDestroy` | Dùng chung |
| `java/.../ui/settings/RateAction.kt` | Tạo | AB riêng |
| `java/.../ui/settings/AboutLinks.kt` | Tạo: mở Play, mở URL | AB riêng |
| `java/.../ui/settings/MoreAppsSettingsScreen.kt` | Tạo | AB riêng |
| `java/.../ui/settings/PrivacySettingsScreen.kt` | Tạo | AB riêng |
| `res/values/about_config.xml` | Tạo: chỗ giữ chỗ (URL, tên nhà phát triển, danh sách app ứng dụng khác) và công tắc `about_more_apps_enabled` | AB riêng |
| `res/values/strings.xml` | Sửa: **một khối cuối file** tiền tố `about_` | Dùng chung (khối riêng) |
| `res/layout/dialog_rate.xml` | Tạo | AB riêng |
| `app/build.gradle.kts` (đường dẫn từ gốc repo, không dưới `app/src/main/`) | Sửa: task `checkNoAboutPlaceholders` + nối vào `bundleRelease` (thêm vào cuối file) | Dùng chung |
| `docs/plans/AB-about-section.md`, `docs/knowledgements/AB-about-section.md` | Tạo | AB riêng |

Xung đột dự kiến: `SettingsScreen.kt` và `strings.xml` cùng bị các nhánh RN/WU/HT sửa (mỗi nhánh một dòng/khối khác). Các dòng giữ chỗ cách nhau một dòng trống nên rebase bình thường; thay đổi `isRegistered` là chỗ duy nhất tôi sửa mã có sẵn trong file đó.

## Global Constraints

- Layering `ui → domain ← data`: toàn bộ code mới nằm ở `ui/settings` và `res`; không cần `domain`/`data` (không có trạng thái lưu, không có logic nghiệp vụ). Helper `AboutLinks` chỉ dùng `Context`/`Intent` ở tầng `ui`.
- Chữ giao diện ở `strings.xml`; giá trị cấu hình (URL, tên, gói app, công tắc ẩn mục) ở `about_config.xml` với `translatable="false"`.
- Không dependency mới, không quyền mới, không đổi manifest. Không in URL ra log.
- Mở link luôn bọc `try/catch` `ActivityNotFoundException`/`SecurityException` và báo bằng `Toast` (đúng mẫu `DefaultLauncherAction`, `SearchOverlayController`); không bao giờ crash khi máy không có Play hay trình duyệt.
- Hộp thoại do hành động giữ, gỡ listener và dismiss trong `onDestroy` (bài học G2d).
- Không sửa tài liệu chung (mục 5.4); không xóa file; không test; không cài APK khi chưa được bảo.
- Nhãn nguồn cho mọi khẳng định mới trong knowledgement: đo được / đọc tài liệu / suy luận chưa kiểm chứng.

## Review Focus

1. **Hub:** mục "Đánh giá ứng dụng" bật, mở hộp thoại ngay trên hub, không mở Activity mới (task stack vẫn một `SettingsActivity`). Mục Ứng dụng khác, Chính sách bảo mật mở màn con và Back về hub. Các mục khác của hub không đổi hành vi.
2. **Hộp thoại:** nút "Gửi đánh giá" luôn bật, sao tùy chọn; mọi số sao (kể cả 0) đều mở Play; không có đường email; "Để sau" và Back/chạm ngoài không làm gì; bấm nhanh hai lần chỉ hiện một hộp thoại; xoay/đổi giao diện tối-sáng khi hộp thoại đang mở không crash và không rò (`WindowLeaked`).
3. **Link:** máy không có Play hoặc không có trình duyệt: Toast, không crash.
4. **Cổng phát hành:** `gradlew.bat bundleRelease` thất bại với thông điệp liệt kê đúng các chuỗi còn `PLACEHOLDER` (và nhắc có thể đặt `about_more_apps_enabled` = false nếu chưa có app khác); `assembleRelease` và `assembleDebug` vẫn chạy; thay hết giá trị giữ chỗ, hoặc tắt công tắc, thì cổng qua (thử trên bản sao tạm, không commit).
5. **Không thêm gì ngoài vùng file**, không dependency/quyền mới (soi `git diff main` ở `libs.versions.toml`, `AndroidManifest.xml`).
6. **Hàng đủ kiểu cũ:** FD2, không thêm kiểu hàng hay style mới ngoài `SettingsRow` hiện có.

---

### Task 1: Chỗ giữ chỗ và cổng kiểm trước phát hành
**Bắt buộc kiểm tay:** không
**Files:** `res/values/about_config.xml` (tạo), `app/build.gradle.kts` (sửa)
**Interfaces:** Produces — các tài nguyên `about_privacy_url`, `about_developer_name`, `about_more_apps_enabled` (bool), `about_more_app_N_name`/`about_more_app_N_package`; task Gradle `checkNoAboutPlaceholders`. Consumes — mẫu `checkNoStoreTestWallpaper`.

- [x] **Step 1:** Tạo `about_config.xml` (chuỗi `translatable="false"`; một `<bool name="about_more_apps_enabled">true</bool>`): bình luận đầu file giải thích quy ước ("giá trị chứa `PLACEHOLDER` làm `bundleRelease` thất bại; đổi thành giá trị thật trước khi phát hành; nếu chưa có ứng dụng khác nào thì đặt `about_more_apps_enabled` = false để ẩn mục và bỏ qua các chuỗi `about_more_app_*`"). Giá trị mặc định: `about_privacy_url` = `https://example.com/PLACEHOLDER-privacy-policy`, `about_developer_name` = `[PLACEHOLDER Tên nhà phát triển]`, sáu bộ `about_more_app_N_name` (`[PLACEHOLDER Tên ứng dụng N]`) và `about_more_app_N_package` (`com.example.PLACEHOLDER.appN`), N = 0 là ứng dụng nổi bật, 1–5 như bản nháp. Dùng `example.com` (tên miền dành riêng) để bản debug không dẫn tới trang thật. Không có chuỗi "play link" và không có email: link Play ghép từ `BuildConfig.APPLICATION_ID` (AB2), 6.1 không có đường góp ý (AB3). Công tắc mặc định `true` để màn 6.2 hiện và kiểm được ở bản debug.
- [x] **Step 2:** Thêm vào cuối `app/build.gradle.kts` lớp `CheckNoAboutPlaceholders` (abstract, `@InputFile @PathSensitive(NONE)` cho file cấu hình, `@TaskAction` đọc text; đọc `<bool name="about_more_apps_enabled">`; so khớp regex `<string\s+name="([^"]+)"[^>]*>([^<]*)</string>`, gom tên các chuỗi có giá trị chứa `PLACEHOLDER`, **bỏ qua các chuỗi `about_more_app_*` khi công tắc là false**; ném `GradleException` liệt kê tên + đường dẫn file + hướng dẫn, trong đó nói rõ "hoặc đặt `about_more_apps_enabled` = false nếu chưa có ứng dụng khác"), task `checkNoAboutPlaceholders` (group `verification`), và `tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(checkNoAboutPlaceholders) }`. Giữ cạnh khối WP có sẵn, comment tiếng Anh như khối đó.
- [x] **Step 3: Kiểm (máy dev, không cần điện thoại)** — (a) `gradlew.bat checkNoAboutPlaceholders` thất bại, thông điệp liệt kê đủ các chuỗi và nhắc công tắc; (b) `gradlew.bat assembleDebug` xanh; (c) `gradlew.bat assembleRelease` không bị cổng chặn; (d) trên bản sao tạm của `about_config.xml` (KHÔNG commit): đổi hết giá trị `PLACEHOLDER` thì cổng qua; hoàn lại rồi đặt `about_more_apps_enabled` = false thì cổng vẫn báo URL và tên nhà phát triển nhưng không báo `about_more_app_*`; hoàn lại file; (e) chạy lại task lần hai thấy "Reusing configuration cache".

### Task 2: Hành động "Đánh giá" (hộp thoại sao + mở Play) và ẩn mục 6.2 theo cấu hình
**Bắt buộc kiểm tay:** không
**Files:** `SettingsScreen.kt`, `SettingsHubScreen.kt` (sửa); `RateAction.kt`, `AboutLinks.kt`, `res/layout/dialog_rate.xml` (tạo); `strings.xml` (khối `about_`)
**Interfaces:** Produces — `interface SettingsAction { fun run(); fun onDestroy() {} }`, `SettingsScreens.actions`, `SettingsScreens.isAction(id)`, `SettingsScreens.createAction(id, ctx)`, `AboutLinks.openPlay(activity, packageName)`, `openUrl(activity, url)`. Consumes — `SettingsScreenContext.activity`, `BuildConfig.APPLICATION_ID`.

- [x] **Step 1:** Trong `SettingsScreen.kt`: thêm `SettingsAction` và bảng `actions` (một ô `// 6.1 RATE (AB)` thay bằng `SettingsScreenId.RATE to ::RateAction`). `isRegistered(id)` = `id in factories || id in actions`; thêm `isAction(id)` và `createAction`. Dòng giữ chỗ 6.1 trong `factories` giữ nguyên (không đăng ký ở đây vì RATE không phải màn); sửa chú thích dòng đó để người sau không đăng ký nhầm.
- [x] **Step 2:** `SettingsHubScreen`: trường `private var action: SettingsAction?`; `onValueClicked` nhánh `HubTarget.Screen` kiểm `SettingsScreens.isAction(t.id)` trước khi `ctx.open`: hub dựng hành động một lần (`createAction`), giữ trong `actions: HashMap<SettingsScreenId, SettingsAction>` và **gọi `run()` mỗi lần chạm** (`RateAction.run()` tự bỏ qua khi hộp thoại của nó còn mở); `onDestroy` gọi `onDestroy()` của mọi hành động đã dựng. **Ẩn mục theo cấu hình (AB4), thay đổi nhỏ nhất:** trong `rows()` vòng lặp bỏ qua mục (không tạo hàng) khi `isHiddenByConfig(entry)`, định nghĩa riêng trong `SettingsHubScreen`: `entry.target == HubTarget.Screen(SettingsScreenId.MORE_APPS) && !activity.resources.getBoolean(R.bool.about_more_apps_enabled)`. Không sửa `SettingsHubLayout`, `HubEntry`, `HubTarget`, adapter hay màn khác; nhóm "Liên hệ" vẫn còn Đánh giá và Chính sách nên không bao giờ rỗng. `onValueClicked` không cần chặn thêm vì hàng không tồn tại. Ghi chú trong code vì sao có hai nhánh này.
- [x] **Step 3:** `AboutLinks.kt`: (a) `openPlay`: thử `Intent(ACTION_VIEW, "market://details?id=<pkg>")`, nếu `ActivityNotFoundException` thì `https://play.google.com/store/apps/details?id=<pkg>`, nếu vẫn lỗi thì Toast `about_open_failed`; (b) `openUrl`: `ACTION_VIEW` với URI, bắt lỗi, Toast; chỉ chấp nhận URL bắt đầu `https://`. Không có hàm soạn email.
- [x] **Step 4:** `dialog_rate.xml` + `RateAction`: `MaterialAlertDialogBuilder(activity)` tiêu đề "Bạn thấy ứng dụng thế nào?", nội dung "Đánh giá của bạn giúp chúng tôi làm launcher tốt hơn.", view có `RatingBar` (5 sao, `stepSize=1`, `rating=0`), **không có dòng gợi ý hai nhánh**. **Sao chỉ để minh họa, tùy chọn (khuyến nghị điều phối): nút "Gửi đánh giá" luôn bật**, không gắn gì với số sao. Nút "Để sau" (negative, không làm gì) và "Gửi đánh giá" (positive): `AboutLinks.openPlay(activity, BuildConfig.APPLICATION_ID)`. `dialog` giữ ở trường, null khi dismiss; `run()` bỏ qua nếu đã có hộp thoại; `onDestroy` gỡ listener và dismiss. Chuỗi `about_rate_*` trong `strings.xml`.
- [ ] **Step 5: Kịch bản kiểm (Live 4, điều phối chạy)** — (a) hub: "Đánh giá ứng dụng" không mờ; bấm → hộp thoại, task stack vẫn một `SettingsActivity` (`adb shell dumpsys activity activities | findstr SettingsActivity`); (b) "Gửi đánh giá" bật ngay cả khi chưa chạm sao; (c) 0, 2 và 5 sao đều mở cùng một đích Play (bản debug chưa lên Play: Play báo không tìm thấy app, ghi rõ hiện gì; kiểm máy không có Play nếu có); (d) "Để sau", Back, chạm ngoài: đóng, không mở gì; (e) bấm hàng hai lần liên tiếp: một hộp thoại; (e2) mở hộp thoại, chọn "Để sau", bấm hàng lần nữa: hộp thoại phải mở lại (lặp lại sau "Gửi đánh giá" rồi Back về); (f) đổi giao diện tối/sáng khi hộp thoại mở: không crash, log không có `WindowLeaked`; (g) mục "Tải ứng dụng khác": đặt tạm `about_more_apps_enabled` = false (không commit) thì hàng biến mất khỏi hub, các hàng khác của nhóm không đổi; hoàn lại thì hàng quay lại.

### Task 3: Màn "Chính sách bảo mật"
**Bắt buộc kiểm tay:** không
**Files:** `PrivacySettingsScreen.kt` (tạo), `SettingsScreen.kt` (thay dòng giữ chỗ 6.3), `strings.xml`
**Interfaces:** Consumes — `AboutLinks.openUrl`, `R.string.about_privacy_url`.

- [x] **Step 1:** `PrivacySettingsScreen : SettingsScreen`, `titleRes = settings_hub_privacy`. `rows`: `Header` ("Chính sách bảo mật" nếu cần, hoặc không có header đầu như `SearchSettingsBlock` sau G2d: tiêu đề màn đã đủ; chốt khi làm theo ảnh chụp thật), một `SettingsRow.Value` "Mở trong trình duyệt" (giá trị: tên miền rút từ `about_privacy_url`, ví dụ `example.com`), `Footer` "Chính sách được tải từ trang web và mở trên trình duyệt của máy." `onValueClicked` → `AboutLinks.openUrl(activity, getString(about_privacy_url))`.
- [x] **Step 2:** Đăng ký `SettingsScreenId.PRIVACY to ::PrivacySettingsScreen` thay dòng giữ chỗ.
- [ ] **Step 3: Kịch bản kiểm (Live 4, điều phối chạy)** — (a) hub → "Chính sách bảo mật" mở màn, hiện hàng và chân trang, Back về hub; (b) bấm hàng mở trình duyệt với URL placeholder; (c) tắt mạng rồi bấm: trình duyệt tự báo lỗi mạng, launcher không crash, quay lại màn bằng Back; (d) nếu máy cho phép, vô hiệu hóa trình duyệt: Toast.

### Task 4: Màn "Tải ứng dụng khác"
**Bắt buộc kiểm tay:** không
**Files:** `MoreAppsSettingsScreen.kt` (tạo), `SettingsScreen.kt` (thay dòng giữ chỗ 6.2), `strings.xml`
**Interfaces:** Consumes — `AboutLinks.openPlay`, `about_more_app_*`, `about_developer_name`.

- [x] **Step 1:** `MoreAppsSettingsScreen`, `titleRes = settings_hub_more_apps`. Danh sách tĩnh trong lớp: `MoreApp(@StringRes name, @StringRes packageRes)` cho 6 mục (ứng dụng nổi bật + 5). `rows`: `Header` "Nổi bật" + `Value` ứng dụng 0 (giá trị "Tải"); `Header` "Ứng dụng khác" + 5 `Value` (giá trị "Tải"); `Footer` "Từ %1$s" với `about_developer_name`. `id` hàng ổn định `more_app_<N>`. `onValueClicked` → `AboutLinks.openPlay(activity, getString(packageRes))`.
- [x] **Step 2:** Đăng ký `SettingsScreenId.MORE_APPS to ::MoreAppsSettingsScreen`.
- [ ] **Step 3: Kịch bản kiểm (Live 4, điều phối chạy)** — (a) màn mở từ hub, hiện nhóm "Nổi bật" và 5 hàng, chân trang tên nhà phát triển giữ chỗ; (b) bấm hàng mở Play/chuyển hướng tới trang gói placeholder (Play báo không tìm thấy; ghi nhận); (c) Back về hub; (d) xoay/đổi tối-sáng giữ nguyên màn.

### Task 5: Dọn, knowledgement, bằng chứng
**Bắt buộc kiểm tay:** không
**Files:** `docs/knowledgements/AB-about-section.md` (tạo), plan này (bảng bằng chứng, nhật ký, đề xuất tài liệu chung), rà `strings.xml`
- [x] **Step 1:** Rà không còn chuỗi/ID mồ côi (`grep` các `about_`, các dòng giữ chỗ), đọc lại `git diff main` đối chiếu Vùng file.
- [x] **Step 2:** Viết knowledgement theo mẫu mục 8 (sơ đồ Mermaid: bấm hàng RATE → hành động → hộp thoại → Play; ẩn mục 6.2 theo cấu hình; mở 6.2/6.3; cổng Gradle), mục "Bài học cho AGENTS.md".
- [x] **Step 3:** Điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung"; không rebase (điều phối quyết định khi nào).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: Vsmart Live 4 (Android 11), bản debug của nhánh `feature/ab-about-section` (ghi commit đã cài khi báo kết quả). Dữ liệu cần chuẩn bị: không. Nếu có thể, thêm một lượt với máy không có Google Play. Điều phối cài; agent không cài.
Lưu ý: bản debug chưa lên Play nên mọi liên kết Play (mục 2 và 4) dẫn tới trang "không tìm thấy app" hoặc trang web tương ứng; đó là đúng. Cần ghi lại máy hiện app Play hay trình duyệt.

Thứ tự thao tác:
1. Mở Cài đặt từ chế độ sửa Home, cuộn tới nhóm "Liên hệ với chúng tôi": ba mục "Đánh giá ứng dụng", "Tải ứng dụng khác", "Chính sách bảo mật" đều **không mờ**; chân trang "Phiên bản 1.0.0".
2. **Đánh giá:** bấm hàng, hộp thoại hiện ngay trên hub, không có màn mới (`adb shell dumpsys activity activities | findstr SettingsActivity` chỉ một Activity).
   - "Gửi đánh giá" bật ngay cả khi chưa chạm sao.
   - Thử 0 sao, 2 sao, 5 sao rồi "Gửi đánh giá": cả ba mở cùng một đích Play. Back về.
   - "Để sau", Back, chạm ngoài hộp thoại: đóng, không mở gì.
   - Mở hộp thoại, "Để sau", bấm hàng lần nữa: hộp thoại phải mở lại (lặp lại sau "Gửi đánh giá" rồi Back).
   - Bấm hàng hai lần thật nhanh: một hộp thoại.
   - Đổi giao diện tối/sáng khi hộp thoại đang mở: không crash, logcat không có `WindowLeaked`.
3. **Chính sách bảo mật:** bấm hàng, màn mở, có hàng "Mở trong trình duyệt" (giá trị `example.com`) và chân trang. Bấm hàng: trình duyệt mở `https://example.com/PLACEHOLDER-privacy-policy`. Back về màn rồi về hub. Tắt mạng, bấm lại: trình duyệt tự báo lỗi mạng, launcher không crash.
4. **Tải ứng dụng khác:** bấm hàng, màn hiện nhóm "Nổi bật" (1 hàng), nhóm "Ứng dụng khác" (5 hàng), mỗi hàng "Tải", chân trang "Từ [PLACEHOLDER Tên nhà phát triển]". Bấm hàng bất kỳ: Play (hoặc web) mở trang gói `com.example.PLACEHOLDER.appN` tương ứng. Back về. Xoay/đổi tối-sáng: màn giữ nguyên.
5. **Ẩn mục theo cấu hình:** đặt tạm `about_more_apps_enabled` = false trong `about_config.xml` (không commit), cài lại: hàng "Tải ứng dụng khác" biến mất khỏi hub, hai hàng còn lại của nhóm không đổi. Hoàn lại.
6. **Hồi quy:** các màn Chung, Liquid Glass, Hình nền, Bộ biểu tượng, Cài đặt tìm kiếm và "Ứng dụng đã ẩn" mở và Back bình thường; hàng "Launcher mặc định" vẫn đúng; mục chưa có màn (vd "Thời tiết", "Ngôn ngữ") vẫn mờ "Sắp có" và không bấm được.
7. **Cổng phát hành (máy dev, không cần điện thoại):** `gradlew.bat checkNoAboutPlaceholders` thất bại; `assembleDebug`, `assembleRelease` xanh.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):**
- Mục 7 (máy dev, Windows, 2026-11-13): đạt, xem Task 1 ở Nhật ký. Chưa chạy `bundleRelease` thật.
- Mục 1, 2 (một phần), 3 (một phần), 4 (một phần) trên Vsmart Live 4, bản debug 0124a70, md5 11ef5f0f, điều phối chạy 2026-11-13, dữ liệu của chủ dự án đã sao lưu và khôi phục sau đó: đạt, không crash, không `WindowLeaked`. Chi tiết ở dòng Nhật ký 2026-11-13 "Live 4" và bảng bằng chứng.
- **Chưa kiểm, đưa vào bài kiểm tay của chủ dự án:** tắt mạng rồi mở chính sách (mục 3); đổi giao diện tối/sáng khi hộp thoại đánh giá mở (mục 2); 0 sao và 5 sao, bấm hai lần nhanh, nút bật khi chưa chạm sao (mục 2); `about_more_apps_enabled` = false rồi dựng lại (mục 5); hồi quy các màn cũ (mục 6); máy không có Play hoặc không có trình duyệt; `bundleRelease` thật.

## Câu hỏi cho chủ dự án

Không còn câu hỏi mở. Đã trả lời 2026-11-13 (chủ dự án quyết Q1/Q3/Q4, điều phối quyết Q2/Q5): xem AB3–AB6 ở mục "Quyết định đã chốt".

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | — | Viết plan | — | Chờ điều phối rà |
| 2026-11-13 | — | Điều phối duyệt plan; cập nhật theo quyết định của chủ dự án (AB3–AB6): bỏ email và nhánh 1–3 sao, thêm công tắc ẩn mục 6.2 | — | Plan duyệt 2026-11-13 |
| 2026-11-13 | 1 | Tạo `about_config.xml` và task `checkNoAboutPlaceholders` nối vào `bundleRelease`. Kiểm máy dev: (a) task thất bại, liệt kê 14 chuỗi; (e) lần hai "Reusing configuration cache"; (d) bỏ hết PLACEHOLDER thì qua, đặt `about_more_apps_enabled`=false thì chỉ còn báo URL và tên nhà phát triển; (b) `assembleDebug` xanh; (c) `assembleRelease` xanh, không bị cổng chặn; `bundleRelease --dry-run` có `checkNoAboutPlaceholders` trong đồ thị task. Bản sao tạm đã hoàn lại (so khớp byte). | (xem git log) | Task 1, vòng 1/5: qua review điều phối 2026-11-13. Chưa chạy `bundleRelease` thật (chưa ký/tốn thời gian), chỉ `--dry-run` |
| 2026-11-13 | 2 | `SettingsAction` + bảng `SettingsScreens.actions` (RATE), `RateAction`, `AboutLinks` (openPlay, openUrl), `dialog_rate.xml`, khối `about_` trong `strings.xml`; hub: chạy hành động tại chỗ, `onDestroy`, `isHiddenByConfig` cho MORE_APPS. `assembleDebug` xanh. Chưa cài máy (điều phối kiểm Step 5). | (xem git log) | Task 2, vòng 1/5 |
| 2026-11-13 | 2 | Sửa lỗi review: hub không đặt lại `action` sau khi hộp thoại đóng nên chạm lần sau không làm gì. Nay giữ hành động theo id trong `actions` và gọi `run()` mỗi lần chạm; `onDestroy` dismiss mọi hành động. Thêm bước kiểm (e2). | (xem git log) | Task 2, vòng 2/5: qua review điều phối 2026-11-13 |
| 2026-11-13 | 3 | `PrivacySettingsScreen`: hàng "Mở trong trình duyệt" (giá trị là tên miền của `about_privacy_url`) mở URL qua `AboutLinks.openUrl`, chân trang giải thích; đăng ký `PRIVACY` thay dòng giữ chỗ; 2 chuỗi `about_privacy_*`. Không header đầu (tiêu đề màn đã đủ). `assembleDebug` xanh. Chưa cài máy. | (xem git log) | Task 3, vòng 1/5: qua review điều phối 2026-11-13 |
| 2026-11-13 | 4 | `MoreAppsSettingsScreen`: nhóm "Nổi bật" (app 0) và "Ứng dụng khác" (app 1-5), giá trị "Tải", chân trang "Từ <tên nhà phát triển>"; bấm hàng mở Play của gói tương ứng (`AboutLinks.openPlay`); đăng ký `MORE_APPS`; 4 chuỗi `about_more_apps_*`. `assembleDebug` xanh. Chưa cài máy. | (xem git log) | Task 4, vòng 1/5: qua review điều phối 2026-11-13 |
| 2026-11-13 | 5 | Viết `knowledgements/AB-about-section.md` (3 sơ đồ Mermaid), gộp bài kiểm cả tính năng (7 mục), điền bảng bằng chứng (dòng Live 4 ghi 'chưa kiểm'), đề xuất cập nhật tài liệu chung. Rà `grep`: 11 chuỗi `about_*` trong `strings.xml` đều có tham chiếu; `@+id/rating` khai báo nhưng chưa dùng (vô hại). Không rebase. | (xem git log) | Task 5, vòng 1/5 |
| 2026-11-13 | Live 4 | Điều phối kiểm trên Vsmart Live 4, bản debug 0124a70, md5 11ef5f0f, dữ liệu chủ dự án đã sao lưu và khôi phục. (1) Hub: ba mục bật, chân trang "Phiên bản 1.0.0". (2) Đánh giá: hộp thoại trên hub, task stack một `SettingsActivity`; "Để sau" đóng và bấm lại mở lại; 2 sao + "Gửi đánh giá" mở `com.android.vending` (Play "Không tìm thấy mục nào"), Back về hub. (3) Chính sách: "Mở trong trình duyệt · example.com" + chân trang; hàng mở Chrome (`ChromeTabbedActivity`) trên URL giữ chỗ, Back về. (4) Ứng dụng khác: "Nổi bật" 1 hàng, "Ứng dụng khác" 5 hàng "Tải", chân trang "Từ [PLACEHOLDER Tên nhà phát triển]"; hàng 2 mở Play, Back về. (5) Không crash, không `WindowLeaked`. **Chưa kiểm:** tắt mạng, đổi tối/sáng khi hộp thoại mở, `about_more_apps_enabled`=false, `bundleRelease` thật. | (xem git log) | Kết quả điều phối báo; chưa kiểm là việc của chủ dự án |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Hub: Đánh giá bật và mở hộp thoại tại chỗ; 6.2/6.3 mở màn, Back về hub; mục khác không đổi | Đọc code: `SettingsScreens.isRegistered` tính `actions`; `SettingsHubScreen.onValueClicked` rẽ `isAction`. **Đo được trên Live 4 (điều phối, 2026-11-13, debug 0124a70, md5 11ef5f0f):** ba mục bật (không "Sắp có"), chân trang "Phiên bản 1.0.0"; Đánh giá mở hộp thoại trên hub, task stack vẫn một `SettingsActivity`; Chính sách bảo mật và Tải ứng dụng khác mở màn và Back về hub. Hồi quy các màn cũ: chưa kiểm | Đạt trên máy cho 6.1–6.3; hồi quy các màn cũ chưa kiểm |
| 2 | Hộp thoại: nút luôn bật, mọi sao tới Play, "Để sau"/Back không làm gì, mở lại được, một hộp thoại khi bấm nhanh, không `WindowLeaked` | Đọc code `RateAction` (`dialog != null` guard, `onDestroy`). **Đo được trên Live 4 (2026-11-13, debug 0124a70, md5 11ef5f0f):** hộp thoại đủ tiêu đề, nội dung, 5 sao, "Để sau"/"Gửi đánh giá"; "Để sau" đóng và bấm hàng lần nữa **mở lại** (xác nhận sửa lỗi vòng 2); 2 sao + "Gửi đánh giá" mở `com.android.vending` (Play báo "Không tìm thấy mục nào", đúng với bản debug), Back về hub; logcat không có `WindowLeaked`, không crash. **Chưa kiểm:** 0 và 5 sao, nút bật khi chưa chạm sao, bấm hai lần thật nhanh, "Gửi đánh giá" rồi mở lại, Back/chạm ngoài, đổi giao diện tối/sáng khi hộp thoại mở | Đạt phần đã đo; phần còn lại chưa kiểm |
| 3 | Link: máy không có Play/trình duyệt thì Toast, không crash | Đọc code `AboutLinks` (bắt `ActivityNotFoundException` và `SecurityException`, Toast `about_open_failed`). **Đo được trên Live 4 (2026-11-13, debug 0124a70, md5 11ef5f0f):** Chính sách mở Chrome (`ChromeTabbedActivity`) trên URL giữ chỗ, Back về màn; hàng "Tải" thứ 2 mở Play, Back về. **Chưa kiểm:** máy không có Play, không có trình duyệt (Toast), tắt mạng | Đường thành công đạt; đường lỗi chỉ bảo vệ bằng code, chưa kiểm |
| 4 | Cổng phát hành: `bundleRelease` thất bại và liệt kê đúng chuỗi; assemble vẫn chạy; qua khi hết PLACEHOLDER hoặc tắt công tắc | Đo được (máy dev, 2026-11-13): task thất bại với 14 chuỗi; bỏ hết PLACEHOLDER trên bản sao tạm thì qua; `about_more_apps_enabled`=false chỉ còn báo 2 chuỗi; `assembleDebug` và `assembleRelease` xanh; `bundleRelease --dry-run` có task. **Chưa chạy `bundleRelease` thật** | Đạt (trừ `bundleRelease` thật chưa chạy) |
| 5 | Không ngoài vùng file, không dependency/quyền mới | `git diff main --stat`: không đụng `AndroidManifest.xml`, `gradle/libs.versions.toml`, file độc quyền; `app/build.gradle.kts` chỉ thêm cuối file | Đạt |
| 6 | Không thêm kiểu hàng/style ngoài `SettingsRow` hiện có (FD2) | Đọc code: chỉ `Header`, `Value`, `Footer` có sẵn; layout chỉ thêm `dialog_rate.xml` (hộp thoại) | Đạt (đọc code) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: ghi 6.1–6.3 (trạng thái, hạn chế đã biết: chưa gửi đánh giá trong app, chính sách mở trình duyệt, danh sách ứng dụng khác là chỗ giữ chỗ).
- `project_structure.md`: thêm các file `ui/settings/RateAction.kt`, `AboutLinks.kt`, `MoreAppsSettingsScreen.kt`, `PrivacySettingsScreen.kt`, `res/values/about_config.xml`, `res/layout/dialog_rate.xml`; ghi bảng `SettingsScreens.actions`.
- `room-schema.md`: không.
- `AGENTS.md` mục "Trước khi phát hành lên cửa hàng": thêm mục **"Thay chỗ giữ chỗ mục Liên hệ (AB)"**: sửa mọi giá trị chứa `PLACEHOLDER` trong `app/src/main/res/values/about_config.xml` (URL chính sách, tên nhà phát triển, danh sách ứng dụng khác), hoặc đặt `about_more_apps_enabled` = false nếu chưa có ứng dụng khác (mục 6.2 ẩn hẳn khỏi hub); task `checkNoAboutPlaceholders` làm `bundleRelease` thất bại chừng nào còn; điền URL chính sách vào Play Console (app dùng INTERNET, vị trí, danh bạ).
- `AGENTS.md` / `lessons/`: xem "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `lessons/settings-and-data.md`).
