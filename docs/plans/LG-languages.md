# LG — Ngôn ngữ app: màn 1.3, 9 ngôn ngữ, chip ngôn ngữ ở Onboarding 0.1 — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. **Cách chạy plan này (chủ dự án chốt 2026-11-18):** Claude code liền một mạch các Task, tick từng Step khi xong; chủ dự án build, kiểm tay và nghiệm thu cả tính năng một lần, rồi **commit một lần**. Không có test tự động; kiểm tay trên Vsmart Live 4 (Android 11, đường AppCompat lưu ngôn ngữ) và Oppo A3x (Android 14, đường ngôn ngữ theo app của hệ thống). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** 2026-11-18 (chủ dự án trả lời Q1–Q3 trước khi viết plan, bảo làm luôn)
**Trạng thái:** Xong 2026-11-18
**Máy:** Live 4 + Oppo (hai đường lưu ngôn ngữ khác nhau theo API, xem "Vì sao") · **Đổi schema Room:** không

**Goal:** Người dùng chọn được ngôn ngữ của app trong Cài đặt → Ngôn ngữ (Figma 1.3) và ở chip góc trên trái của Onboarding 0.1: "Theo hệ thống" hoặc một trong 9 ngôn ngữ của thiết kế, mỗi ngôn ngữ dịch đủ mọi chuỗi. Máy dùng ngôn ngữ app không có thì app hiện tiếng Anh.

**Architecture:** Ngôn ngữ áp bằng `AppCompatDelegate.setApplicationLocales` (API 33+ hệ thống giữ theo app; API < 33 AppCompat tự lưu nhờ `AppLocalesMetadataHolderService` `autoStoreLocales`). Danh mục 9 ngôn ngữ + đọc/áp ngôn ngữ gom ở `ui/language/AppLanguages.kt`. `values/` thành tiếng Anh, tiếng Việt chuyển sang `values-vi/`. Màn 1.3 là một `SettingsScreen` mới trong khung Cài đặt (hàng tìm kiếm + card radio). Chỗ lấy chuỗi bằng context không phải Activity (Trung tâm điều khiển, thông báo, danh mục cài đặt hệ thống của tìm kiếm, Ứng dụng ẩn) bọc context theo ngôn ngữ app ở API < 33.

**Tech Stack:** AppCompat 1.8.0 (đã có; `setApplicationLocales` + `autoStoreLocales` có từ 1.6.0). Không thêm dependency.

**Spec:** màn 1.3 Ngôn ngữ (Figma `302:1856`), chip ngôn ngữ 0.1 Chào mừng (`302:3681`) trong `Figma-Draft.md` mục 3 hàng 2 và 5; FD6 (`Figma-Draft.md` mục 4: bản dịch do Claude máy dịch, ghi rõ); dự án tham khảo `Multi_Language_Plan.md` (app Magic Hand, chủ dự án gửi 2026-11-18): catalog trong code, `values-in` cho tiếng Indonesia, tắt language split của App Bundle, chỉ đưa ngôn ngữ vào danh sách khi dịch đủ.

## Thiết kế đọc từ Figma (figma-bridge, 2026-11-18)

**1.3 Ngôn ngữ `302:1856`:** nền aurora; Nav Bar (Back "Cài đặt", tiêu đề "Ngôn ngữ"); nội dung lề 16, khoảng cách 20: ô tìm "Tìm ngôn ngữ" (cao 44, bo 22, bề mặt CONTROL), rồi **một card** (bo 20) gồm 10 hàng Radio cao 56: dòng chính 14 = tên bản ngữ, dòng phụ 12 `ds_text_secondary` = tên ngôn ngữ đó **viết bằng ngôn ngữ đang dùng**. Thứ tự:

| # | Dòng chính | Dòng phụ (bản vi) | Tag | Thư mục res |
|---|---|---|---|---|
| 0 | Theo hệ thống | Dùng ngôn ngữ của máy | (rỗng) | — |
| 1 | Tiếng Việt | Tiếng Việt | `vi` | `values-vi` |
| 2 | English | Tiếng Anh | `en` | `values` (mặc định) |
| 3 | Español | Tiếng Tây Ban Nha | `es` | `values-es` |
| 4 | Português | Tiếng Bồ Đào Nha | `pt` | `values-pt` |
| 5 | Bahasa Indonesia | Tiếng Indonesia | `id` | `values-in` |
| 6 | ภาษาไทย | Tiếng Thái | `th` | `values-th` |
| 7 | 日本語 | Tiếng Nhật | `ja` | `values-ja` |
| 8 | 한국어 | Tiếng Hàn | `ko` | `values-ko` |
| 9 | हिन्दी | Tiếng Hindi | `hi` | `values-hi` |

Không có ngôn ngữ RTL nào (chủ dự án 2026-11-18: không làm RTL).

**0.1 chip `302:3681`:** pill bề mặt CONTROL cao 34 (đệm 8/12/8/10, khe 6) ở góc trên trái (x 16, y 34, cùng hàng nút "Bỏ qua"), icon quả địa cầu 18 màu `#7DD3FC` (= `ds_ic_language`), chữ 13 = tên bản ngữ của ngôn ngữ đang dùng.

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** app có 556 chuỗi (530 cần dịch, 26 `translatable="false"`, 1 plurals), hiện chỉ tiếng Việt và nằm ở `values/` (ngôn ngữ dự phòng). Hai máy test khác đường: Live 4 (API 30) không có ngôn ngữ theo app của hệ thống, Oppo (API 34) có. Launcher có nhiều chỗ lấy chuỗi ngoài Activity (service Trung tâm điều khiển vẽ overlay, thông báo thời tiết, ViewModel dùng context ứng dụng); ở API < 33 AppCompat **chỉ** đổi ngôn ngữ cho Activity (đọc tài liệu AppCompat `setApplicationLocales`).
- **Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. `AppCompatDelegate.setApplicationLocales` + `autoStoreLocales` | API chuẩn của Android; 33+ đi qua `LocaleManager`, < 33 AppCompat lưu và áp cho Activity | Đúng hướng dẫn chính thức; API 33+ hiện trong Cài đặt hệ thống → Ngôn ngữ ứng dụng (nhờ `localeConfig`); Activity tự tạo lại | API < 33: context không phải Activity vẫn theo ngôn ngữ máy → phải bọc tay (Task 4) | **Chọn** |
| B. Tự lưu ngôn ngữ (DataStore) + bọc `attachBaseContext` mọi Activity/Service | Tự quản hoàn toàn | Một đường cho mọi API | Không hiện trong Cài đặt hệ thống API 33+; phải sửa mọi Activity; dễ lệch với hệ thống | Không |
| C. Chọn ngôn ngữ mở thẳng màn hệ thống (`Settings.ACTION_APP_LOCALE_SETTINGS`) | Không tự làm màn | Ít code | Chỉ API 33+; không khớp thiết kế 1.3; Live 4 không có | Không |

- **Vì sao chọn A:** là cách tài liệu Android khuyên cho app có bộ chọn ngôn ngữ riêng (đọc tài liệu "Per-app language preferences"), dự án tham khảo đã dùng và chạy được. Phần thiếu của API < 33 khép bằng một hàm bọc context đặt ở **ít chỗ, có danh sách** (Task 4) thay vì sửa mọi Activity.
- **Ngôn ngữ dự phòng = tiếng Anh (chủ dự án Q2):** `values/` thành tiếng Anh, tiếng Việt sang `values-vi/`. Máy tiếng Pháp + "Theo hệ thống" → tiếng Anh.
- **Chạm là đổi ngay** (không có nút xác nhận trong Figma): chạm hàng → `setApplicationLocales` → mọi Activity đang mở tạo lại (Cài đặt, Home phía sau, Onboarding nếu đang mở). Không đổi `configChanges` của Home: Home tạo lại là đường đã có (đổi sáng/tối, `RecreateGuard`).
- **Đánh đổi chấp nhận:**
  - Bản dịch 8 ngôn ngữ là **máy dịch (Claude)**, chưa có người bản ngữ duyệt (FD6). Ghi ở đầu mỗi file `values-xx/strings.xml`.
  - Tên app trên Home/Thư viện/tìm kiếm là nhãn của **hệ thống** (theo ngôn ngữ máy), không theo ngôn ngữ app (đúng như iOS, không sửa).
  - API < 33: process khởi động bởi service hoặc báo thức (chưa mở Activity nào) dùng ngôn ngữ đã lưu ở kho phụ của Task 4; nếu người dùng chưa từng chọn trong app thì theo máy.
  - Font Be Vietnam Pro không có chữ Thái, CJK, Devanagari: hệ thống tự thay font theo từng ký tự. Chiều cao dòng và độ đậm có thể khác (suy luận chưa kiểm chứng; kiểm ở bài kiểm cuối).
  - Tắt language split của App Bundle (như dự án tham khảo): mọi bản dịch nằm trong APK gốc; tăng dung lượng nhỏ (530 chuỗi × 8).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| L1 | Làm đủ 9 ngôn ngữ của Figma trong plan này; dịch máy theo nhóm, ngôn ngữ chỉ vào danh mục khi đã dịch đủ | Chủ dự án 2026-11-18 (Q1); quy tắc lấy từ dự án tham khảo |
| L2 | Không làm ngôn ngữ RTL | Chủ dự án 2026-11-18 |
| L3 | Ngôn ngữ dự phòng là tiếng Anh: `values/` tiếng Anh, `values-vi/` tiếng Việt | Chủ dự án 2026-11-18 (Q2) |
| L4 | Làm luôn chip ngôn ngữ ở Onboarding 0.1; chạm mở màn 1.3 (khung Cài đặt) | Chủ dự án 2026-11-18 (Q3); Claude chọn cách mở |
| L5 | Tiếng Bồ Đào Nha: tag `pt`, thư mục `values-pt`, viết theo **Bồ Đào Nha Brazil** (thị trường lớn hơn); Figma chỉ ghi "Português" | Claude đề xuất 2026-11-18, **cần chủ dự án xác nhận** (mục Câu hỏi) |
| L6 | Tiếng Indonesia: tag `id` ở catalog và `locales_config.xml`, thư mục `values-in` | Bài học dự án tham khảo (tên lịch sử, chắc mọi API) |
| L7 | Hàng Ngôn ngữ ở trang chủ Cài đặt hiện tên bản ngữ của ngôn ngữ đang chọn, hoặc "Theo hệ thống" | Claude, 2026-11-18 (Figma vẽ "Tiếng Việt") |
| L8 | Ô tìm lọc theo tên bản ngữ, tên đã dịch và tag; không phân biệt hoa thường và dấu; không khớp gì thì hiện một dòng "Không tìm thấy ngôn ngữ" | Claude, 2026-11-18 |
| L9 | `bundle { language { enableSplit = false } }` | Theo dự án tham khảo và tài liệu Android cho app có bộ chọn ngôn ngữ riêng |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Dịch tới đâu? | Đủ 9, theo nhóm | Đủ 9; bỏ RTL (2026-11-18) |
| Q2 | Ngôn ngữ dự phòng? | Tiếng Anh | Tiếng Anh (2026-11-18) |
| Q3 | Chip Onboarding làm luôn? | Làm luôn | Làm luôn (2026-11-18) |
| Q4 | Português viết theo Brazil hay Bồ Đào Nha? | Brazil (L5) | Chưa trả lời lúc đóng plan: giữ Brazil (L5); đổi thì chỉ sửa `values-pt` |

## Ngoài phạm vi / ghi để sau
- Chuỗi tiếng Việt viết cứng trong Kotlin chỉ dùng cho log/lỗi nội bộ (`require`, `IOException`, `BackdropSource.name`) **không** dịch. Chuỗi cứng **hiện ra màn hình** thì chuyển vào `strings.xml` (Task 2 liệt kê).
- Nội dung `about_config.xml` (`PLACEHOLDER`, không dịch, đã có việc trước phát hành).
- Người bản ngữ duyệt bản dịch.

## Vùng file

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `app/src/main/java/.../ui/language/AppLanguages.kt` | Tạo | catalog + đọc/áp + bọc context |
| `app/src/main/res/xml/locales_config.xml` | Tạo | |
| `app/src/main/AndroidManifest.xml` | Sửa | `localeConfig`, service `AppLocalesMetadataHolderService` |
| `app/build.gradle.kts` | Sửa | tắt language split |
| `app/src/main/res/values/strings.xml` | Sửa | thành tiếng Anh |
| `app/src/main/res/values-vi/strings.xml` | Tạo | tiếng Việt (chuyển từ `values/`) |
| `app/src/main/res/values-{es,pt,in,th,ja,ko,hi}/strings.xml` | Tạo | Task 6, 7 |
| `ui/settings/LanguageSettingsScreen.kt` | Tạo | màn 1.3 |
| `ui/settings/SettingsRow.kt`, `SettingsAdapter.kt`, `SettingsScreen.kt`, `SettingsHubScreen.kt`, `SettingsCardDecoration.kt` | Sửa | hàng tìm kiếm, radio chữ động, đăng ký màn, giá trị hàng trang chủ |
| Chỗ dùng context không phải Activity (Task 4) | Sửa | danh sách ở Task 4 |
| `ui/onboarding/OnboardingPages.kt`, `res/layout/onboarding_welcome.xml` | Sửa | chip |
| `docs/config-schema.md`, `docs/Figma-Draft.md`, `docs/project_structure.md`, `docs/lessons/settings-and-data.md`, `docs/AGENTS.md` | Sửa | Task 8 |
| `ui/library/AppLibraryViewModel.kt`, `AppLibraryActivity.kt`, `data/backdrop/WallpaperApplier.kt`, `BackdropRepositoryImpl.kt`, `ui/settings/WeatherPreviewMock.kt`, `SettingsActivity.kt` | Sửa | Task 2, 3, 4 |

## Global Constraints
- Không đổi tên khóa chuỗi (`name`); mọi `values-xx` đủ đúng bộ khóa cần dịch của `values/`, cùng thứ tự; không chép chuỗi `translatable="false"` sang `values-xx`.
- Giữ nguyên placeholder (`%1$s`, `%d`, `%%`), `\n`, `\'`, `\"`, thẻ XML, ký tự `…`, `·`, `°`, tên riêng (Liquid Glass, OS26 Launcher, Google Play, OpenWeatherMap, tên theme "Liquid Glass 27", "Classic 26", "Minimal", "Dark Pro"). Dấu nháy đơn trong bản dịch (es, pt, id…) phải escape `\'`.
- Thuật ngữ thống nhất cho mọi ngôn ngữ: Home/Màn hình chính = màn hình chính của launcher; Dock; Widget/Tiện ích; Thư viện ứng dụng (App Library); Thư mục; Kính (liquid glass); Hình nền; Trung tâm điều khiển (Control Center). Dùng từ mà Android/iOS của ngôn ngữ đó đang dùng.
- Luật phụ thuộc: `AppLanguages` ở `ui/language` (dùng AppCompat, là việc của tầng UI); `data/*` không import `ui`: chỗ ở `data` cần chuỗi theo ngôn ngữ app nhận context đã bọc qua hàm ở `core` (xem Task 4).
- Mỗi ngôn ngữ chỉ vào `AppLanguages.all` và `locales_config.xml` khi file của nó đã đủ.

## Review Focus
1. API < 33 (Live 4): chọn ngôn ngữ → mọi Activity đổi; Trung tâm điều khiển, thông báo thời tiết, danh mục cài đặt trong tìm kiếm, Ứng dụng ẩn cũng đổi (Task 4).
2. API 33+ (Oppo): Cài đặt hệ thống → Ứng dụng → OS26 Launcher → Ngôn ngữ hiện 9 ngôn ngữ; đổi ở đó thì màn 1.3 tick đúng.
3. Chạm ngôn ngữ: Cài đặt tạo lại, vẫn đứng ở màn 1.3, hàng đã chọn được tick; Home phía sau tạo lại không mất trang, không lỗi bố cục.
4. "Theo hệ thống" trên máy tiếng Việt → tiếng Việt; máy ngôn ngữ ngoài danh sách → tiếng Anh.
5. Bản dịch: đủ khóa, placeholder khớp, không vỡ build (`\'`), không tràn chữ ở chỗ hẹp (nút, pill, ô lưới, Trung tâm điều khiển).
6. Onboarding: chạm chip → chọn ngôn ngữ → Back về đúng bước 0.1 bằng ngôn ngữ mới; không mở lại Onboarding hai lần.

---

### Task 1: Hạ tầng ngôn ngữ
**Bắt buộc kiểm tay:** không (gộp bài kiểm cuối)
**Files:** `ui/language/AppLanguages.kt`, `res/xml/locales_config.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`
**Interfaces:** Produces `AppLanguages.all: List<AppLanguage>`, `AppLanguages.current(): AppLanguage?` (null = theo hệ thống), `AppLanguages.apply(context, tag?)`.
- [x] **Step 1:** `AppLanguage(tag, nativeName, @StringRes localizedName)`; `AppLanguages.all` (lúc đầu chỉ `vi`, `en`); `current()` đọc `AppCompatDelegate.getApplicationLocales()`, khớp tag đầy đủ rồi theo ngôn ngữ (`in`→`id`); `apply(null)` = danh sách rỗng (theo hệ thống).
- [x] **Step 2:** `locales_config.xml` (lúc đầu `vi`, `en`), manifest `android:localeConfig` + service `androidx.appcompat.app.AppLocalesMetadataHolderService` (`enabled=false`, `exported=false`, meta-data `autoStoreLocales=true`).
- [x] **Step 3:** `bundle { language { enableSplit = false } }` trong `android { }`.

### Task 2: Tách chuỗi: tiếng Anh mặc định, tiếng Việt sang `values-vi`
**Bắt buộc kiểm tay:** không
**Files:** `res/values/strings.xml`, `res/values-vi/strings.xml`
- [x] **Step 1:** `values-vi/strings.xml` = mọi chuỗi cần dịch của `values/` (bỏ 26 chuỗi `translatable="false"`), giữ thứ tự, giữ comment.
- [x] **Step 2:** `values/strings.xml` dịch sang tiếng Anh tại chỗ (giữ `name`, giữ chuỗi `translatable="false"`).
- [x] **Step 3:** Thêm khối chuỗi `lang_*` ở cuối (tiêu đề hàng, "Theo hệ thống", "Dùng ngôn ngữ của máy", "Tìm ngôn ngữ", "Không tìm thấy ngôn ngữ", 9 tên ngôn ngữ theo ngôn ngữ đang dùng) ở cả hai file.
- [x] **Step 4:** Chuỗi cứng hiện ra màn hình chuyển vào `strings.xml`: lý do lỗi đặt wallpaper (`WallpaperApplier`), dữ liệu mẫu card thời tiết (`WeatherPreviewMock`: "Nhiều mây"). Tên thành phố mẫu giữ nguyên.
- [x] **Step 5:** Kiểm bằng script: hai file cùng bộ khóa (trừ `translatable="false"`), cùng số placeholder mỗi chuỗi.

### Task 3: Màn 1.3 Ngôn ngữ trong Cài đặt
**Bắt buộc kiểm tay:** không
**Files:** `LanguageSettingsScreen.kt`, `SettingsRow.kt`, `SettingsAdapter.kt`, `SettingsScreen.kt`, `SettingsHubScreen.kt`, `SettingsCardDecoration.kt`, `SettingsActivity.kt` (không cần layout mới: ô tìm là `DsSearchField` dựng bằng code, dòng không có kết quả dùng `SettingsRow.Footer`)
- [x] **Step 1:** `SettingsRow.Radio` thêm `titleText`/`descriptionText` (chuỗi động, như `Value.titleText`); `SettingsRow.Search(id, hint)` (không thuộc card, không chứa chữ gõ để không bind lại khi lọc); không kết quả dùng `SettingsRow.Footer`.
- [x] **Step 2:** Adapter: holder ô tìm dùng `DsSearchField`, báo `onSearchChanged`; không gán lại chữ khi bind nếu chữ đang giống (không mất con trỏ khi danh sách lọc lại).
- [x] **Step 3:** `LanguageSettingsScreen`: hàng tìm + card radio (Theo hệ thống + `AppLanguages.all`), lọc theo L8; chạm hàng chưa chọn → `AppLanguages.apply`. Đăng ký `SettingsScreenId.LANGUAGE`.
- [x] **Step 4:** Trang chủ: giá trị hàng Ngôn ngữ (L7).

### Task 4: Ngôn ngữ app cho context không phải Activity (API < 33)
**Bắt buộc kiểm tay:** có — đường API < 33 chỉ Live 4 trả lời được (process do service/báo thức dựng lại có đọc được ngôn ngữ đã chọn không).
**Files:** `core/locale/AppLocaleContext.kt` (tạo), `AppLanguages.kt`, và các chỗ sau (đọc code 2026-11-18): `ui/controlcenter/ControlCenterService` (overlay), `ui/weather/notify/WeatherNotifier` + `WeatherNotificationJobService`, `data/search/SystemSettingsRepositoryImpl`, `ui/library/AppLibraryViewModel`.
- [x] **Step 1:** `AppLocaleContext.wrap(context)`: API ≥ 33 trả nguyên (hệ thống đã áp ngôn ngữ theo app cho cả process); API < 33 đọc tag từ kho phụ (SharedPreferences `app_locale`, ghi đồng bộ ở `AppLanguages.apply`) rồi `createConfigurationContext`. Thêm kho phụ vào `config-schema.md`.
- [x] **Step 2:** Áp cho từng chỗ ở danh sách; Trung tâm điều khiển dựng lại view lần mở panel kế tiếp; thông báo cập nhật tên kênh khi tạo lại kênh.

### Task 5: Chip ngôn ngữ ở Onboarding 0.1
**Bắt buộc kiểm tay:** không
**Files:** `onboarding_welcome.xml` hoặc `activity_onboarding.xml`, `OnboardingPages.kt`/`OnboardingActivity.kt`
- [x] **Step 1:** Chip theo Figma `302:3681` ở góc trên trái, chỉ hiện ở bước 0.1 (chỗ nút back của thanh trên).
- [x] **Step 2:** Chạm → mở `SettingsActivity` màn `LANGUAGE`; Back về Onboarding. Kiểm Onboarding tạo lại vẫn ở bước 0.1.

### Task 6: Dịch nhóm Latin: es, pt, id
- [x] **Step 1:** `values-es`, `values-pt`, `values-in` đủ khóa; script kiểm như Task 2 Step 5.
- [x] **Step 2:** Thêm 3 ngôn ngữ vào `AppLanguages.all` và `locales_config.xml`.

### Task 7: Dịch nhóm chữ khác: th, ja, ko, hi
- [x] **Step 1:** `values-th`, `values-ja`, `values-ko`, `values-hi` đủ khóa; script kiểm.
- [x] **Step 2:** Thêm 4 ngôn ngữ vào danh mục và `locales_config.xml`.

### Task 8: Tài liệu
- [x] **Step 1:** `config-schema.md` (kho ngôn ngữ), `Figma-Draft.md` (hàng 1.3, 0.1, 3), `project_structure.md` (`ui/language`, `core/locale`), bài học vào `lessons/settings-and-data.md`.
- [x] **Step 2:** Bài kiểm cả tính năng (dưới), commit message soạn sẵn.

---

## Bài kiểm cả tính năng
**Máy:** Live 4 (Android 11, đường AppCompat) cho mọi mục; Oppo A3x (Android 14) cho mục 2, 7. Bản debug (`gradlew.bat installDebug`). Trước khi kiểm chạy `gradlew.bat lintDebug` (không có `MissingTranslation`, `MissingQuantity`).
**Dữ liệu:** máy để ngôn ngữ hệ thống tiếng Việt; Trung tâm điều khiển đã bật; một app đã ẩn; một widget thời tiết.

1. **Màn 1.3:** Cài đặt → hàng Ngôn ngữ hiện "Theo hệ thống" → mở: ô "Tìm ngôn ngữ", card 10 hàng đúng thứ tự Figma, "Theo hệ thống" được chọn. Gõ `tieng`, `nhat`, `ja`, `ไทย`: lọc đúng, bàn phím không đóng, con trỏ không nhảy; gõ `xyz`: dòng "Không tìm thấy ngôn ngữ".
2. **Đổi ngôn ngữ:** chọn English → Cài đặt dựng lại, vẫn ở màn Language, hàng English được chọn, dòng phụ đổi sang tên tiếng Anh ("Vietnamese", "Japanese"…). Back → trang chủ Cài đặt tiếng Anh, hàng Language hiện "English". Lần lượt chọn Español, Português, Bahasa Indonesia, ภาษาไทย, 日本語, 한국어, हिन्दी: mỗi lần toàn bộ chữ đổi. (Oppo) Cài đặt hệ thống → Ứng dụng → OS26 Launcher → Ngôn ngữ: có 9 ngôn ngữ, đổi ở đó thì màn 1.3 tick đúng.
3. **Home phía sau:** sau mỗi lần đổi, bấm Home: Home dựng lại không lỗi bố cục, giữ trang; menu nhấn giữ icon, edit mode (nút Xong/Cài đặt), thư mục mới ("Folder"), App Library (tên nhóm), widget đồng hồ/lịch/thời tiết, ô tìm kiếm đều đúng ngôn ngữ. Tên app là nhãn hệ thống, vẫn theo ngôn ngữ máy (đúng thiết kế).
4. **Ngoài Activity (Live 4, Review Focus 1):** đổi sang 日本語 rồi: kéo mở Trung tâm điều khiển (tên ô, thanh trượt bằng tiếng Nhật); ô tìm kiếm gõ "wi" → mục cài đặt hệ thống tên tiếng Nhật; Cài đặt → Ứng dụng ẩn: tiêu đề nhóm tiếng Nhật; bật thông báo thời tiết rồi đợi/ép một lần gửi: chữ thông báo tiếng Nhật. Khởi động lại máy, chưa mở app, đợi thông báo hoặc kéo Trung tâm điều khiển: vẫn tiếng Nhật.
5. **Theo hệ thống:** chọn "Theo hệ thống" → tiếng Việt (máy tiếng Việt). Đổi ngôn ngữ máy sang Français → app tiếng Anh. Đổi máy sang Português (Brasil) với app "Theo hệ thống" → app tiếng Bồ Đào Nha; màn 1.3 vẫn tick "Theo hệ thống". Trả máy về tiếng Việt.
6. **Onboarding (chip):** gửi `OB_RESET` (hoặc xóa dữ liệu) → mở Onboarding 0.1: chip góc trên trái (quả địa cầu xanh + "Tiếng Việt"), các bước khác không có chip. Chạm chip → màn 1.3 → chọn English → Back: Onboarding ở 0.1 bằng tiếng Anh, chip "English"; đi hết các bước không mở Onboarding hai lần.
7. **Tràn chữ:** ở tiếng Đức không có; soát es, pt, id, hi, th (chữ dài/cao): nút Onboarding, pill nổi edit mode, menu nhấn giữ icon, hàng cài đặt có giá trị bên phải, ô Trung tâm điều khiển, chip ngôn ngữ. Glyph Thái/Hindi/CJK không ra ô vuông trên Live 4.
8. **Hồi quy:** màn Vị trí (radio Tự động/Thủ công) vẫn hiện chữ đúng; lỗi đặt wallpaper (máy không cho) hiện lý do theo ngôn ngữ app; card xem trước thời tiết (1.4) hiện tình trạng theo ngôn ngữ app.

**Kết quả kiểm chứng (máy, ngày):** 2026-11-18, chủ dự án build `assembleDebug lintDebug installDebug` xanh (sau 2 lần sửa ở Nhật ký), cài lên máy và xác nhận ổn, đóng plan. Các mục chưa ghi riêng kết quả (mục 4 khởi động lại máy, mục 7 soát tràn chữ từng ngôn ngữ) coi là chưa kiểm chi tiết: gặp lỗi thì mở việc sửa riêng.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú (lỗi, câu hỏi, đổi kế hoạch) |
|---|---|---|---|
| 2026-11-18 | — | Đọc Figma 1.3 + 0.1 bằng figma-bridge, viết plan | Chủ dự án trả lời Q1–Q3 trước khi viết; Q4 còn mở |
| 2026-11-18 | 1–8 | Code liền một mạch theo yêu cầu chủ dự án: `AppLanguages`, `AppLocaleContext`, `locales_config`, manifest, tắt language split; `values/` tiếng Anh + `values-vi`; màn 1.3 (`LanguageSettingsScreen`, `SettingsRow.Search`); bọc context cho Trung tâm điều khiển, thông báo thời tiết, danh mục cài đặt hệ thống, lý do lỗi wallpaper; tiêu đề Ứng dụng ẩn chuyển sang Activity; chip 0.1; 7 bản dịch máy (es, pt Brazil, id, th, ja, ko, hi); `tools/check_translations.py`; tài liệu chung | **Khác plan:** (1) không tạo layout `item_settings_search/empty`: ô tìm dựng bằng code, dòng trống dùng `Footer`; (2) `AppLocaleContext` đặt ở `core/locale` để `data/*` dùng được; (3) `AppLibraryViewModel` bỏ `@ApplicationContext` (chuỗi dựng trong VM sẽ giữ ngôn ngữ cũ sau recreate); (4) `weather_attribution(_url)` đánh dấu `translatable="false"`; (5) plurals es/pt thêm `many`. Bản dịch sinh bằng script từ file tên→chữ (giữ thứ tự, comment), kiểm đủ khóa/placeholder/escape. **Chưa build.** Q4 (Português) còn mở |
| 2026-11-18 | build | Chủ dự án build: `Unclosed comment` ở `AppLocaleContext.kt:51` + 14 lỗi `Unresolved reference AppLocaleContext` kéo theo | Nguyên nhân: KDoc có `data/*` (Kotlin lồng comment), lặp bẫy DS3 Task 3 trong AGENTS.md. Sửa thành `data/...`; quét lại `/*` trong mọi dòng mới |
| 2026-11-18 | lint | `lintDebug` báo 9 lỗi `MissingTranslation`, cả 9 là chuỗi `probe_*` ở `src/debug/res/values/probe_strings.xml` (widget đo G3c, chỉ có ở bản debug) | Lúc dịch chỉ quét `src/main`. Sửa: `translatable="false"` cho 9 chuỗi. Bài học: thêm ngôn ngữ phải quét mọi source set (`src/debug`, `src/release`) |
| 2026-11-18 | commit | Chủ dự án bỏ `tools/check_translations.py` trước khi commit `c68fe87` | Sửa các chỗ trỏ tới script trong `AGENTS.md`, `lessons/settings-and-data.md`, plan này: kiểm bản dịch bằng `lintDebug` (`MissingTranslation`). Dòng Nhật ký cũ giữ nguyên |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | API < 33: Activity và chỗ ngoài Activity đổi ngôn ngữ | Đọc code: `AppLocaleContext.wrap` ở Trung tâm điều khiển, `WeatherNotifier`, `SystemSettingsRepositoryImpl`, `WallpaperApplier`/`BackdropRepositoryImpl`; tiêu đề Ứng dụng ẩn đặt ở Activity. Chủ dự án xác nhận ổn trên máy | Đạt (kiểm sơ bộ) |
| 2 | API 33+: ngôn ngữ trong Cài đặt hệ thống | `locales_config.xml` 9 tag + `android:localeConfig` | Đạt theo code; chưa ghi kết quả Oppo |
| 3 | Chạm ngôn ngữ: Cài đặt dựng lại đúng màn, Home không lỗi | Extra `settings_screen` giữ màn; Home đi đường recreate có sẵn | Đạt (chủ dự án xác nhận) |
| 4 | Theo hệ thống / ngoài danh sách → tiếng Anh | `values/` tiếng Anh; `AppLanguages.effective()` | Đạt theo code |
| 5 | Bản dịch đủ khóa, placeholder, escape | Script kiểm (chạy trong phiên, không đưa vào repo) OK 8 file; `lintDebug` xanh | Đạt |
| 6 | Onboarding chip | Chip chỉ hiện ở `OnboardingStep.WELCOME`; ViewModel giữ bước qua recreate | Đạt theo code |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `Figma-Draft.md`: hàng 1.3 (100%), 0.1 (chip ngôn ngữ), Home cài đặt (giá trị Ngôn ngữ), danh sách màn "Sắp có"
- `project_structure.md`: đoạn "Tệp thêm ở LG"
- `config-schema.md`: kho ngôn ngữ (hệ thống) + kho phụ SharedPreferences `app_locale`
- `lessons/settings-and-data.md`: mục LG (7 bài học); `AGENTS.md`: quy ước 9 ngôn ngữ
- `room-schema.md`, `Launcher-Features.md`: không đổi
