# WP — Ảnh nền mặc định để thử (iOS 27, xóa trước bản cửa hàng) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/wp-default-wallpaper` · **Worktree:** `E:\SourceTree\IosLauncher-wt\WP-default-wallpaper` · **PR:** #24 (draft) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09 (chủ dự án; sửa Q4 cùng ngày: áp dụng cả debug và release)
**Trạng thái:** Xong, chờ merge (kiểm Oppo đạt; Live 4 chưa kiểm)
**Khóa:** không (không đổi schema Room, không thêm thư viện) · cần Oppo cho kiểm kính (một lượt)

**Goal:** Khi người dùng chưa chọn ảnh nền, Home hiện ảnh mặc định lấy từ bộ hình nền iOS 27 ("Blue" khi sáng, "Dark Blue" khi tối, theo night mode hệ thống) ở **cả bản debug và release** (tech lead thử trên APK release). Ảnh chỉ để thử và **phải xóa trước khi đưa lên cửa hàng**; một task Gradle chặn `bundleRelease` (AAB cửa hàng) cho tới khi xóa.

**Architecture:** Một lớp `DefaultBackdropSource` ở `main` đọc ảnh từ tài nguyên `drawable-nodpi` / `drawable-night-nodpi` (cùng tên `default_wallpaper_ios27`, hệ thống chọn sáng/tối theo qualifier `night`), giải mã ở cỡ màn. `BackdropRepositoryImpl` dùng nó làm dự phòng khi không có `files/backdrop_image`. Home nạp qua đường nạp ảnh nền sẵn có nên mọi `BackdropChange` (kể cả `LOADED`) vẫn đi qua `captureOnce` của DG. Đổi sáng/tối được bắt bằng "dấu phiên bản" của ảnh mặc định (-1 sáng, -2 tối) mà `reloadBackdropIfChanged` ở `onResume` đã so sẵn, nên không cần sửa `HomeActivity`.

**Tech Stack:** không thêm gì. `BitmapFactory` + `Resources`; task Gradle tự viết trong `app/build.gradle.kts`.
**Spec:** không có TT riêng; liên quan M8a Task 5 (ảnh nền) và AN/DG (làm mới kính khi đổi nền) trong `AGENTS.md`.

> **CẢNH BÁO BẢN QUYỀN.** Hai ảnh là hình nền iOS 27 của Apple, **không có giấy phép**, tech lead cho dùng **chỉ để thử, xóa trước khi lên cửa hàng** (2026-11-09). Hai file cần xóa trước bản cửa hàng:
> - `app/src/main/res/drawable-nodpi/default_wallpaper_ios27.jpg` (Blue, sáng)
> - `app/src/main/res/drawable-night-nodpi/default_wallpaper_ios27.jpg` (Dark Blue, tối)
>
> Sau khi xóa: đặt `DefaultBackdropSource.isAvailable()` = `false` và bỏ các tham chiếu `R.drawable.default_wallpaper_ios27` trong file đó (build sẽ báo lỗi nếu quên). Việc xóa do chủ dự án/tech lead làm (agent không tự xóa file). **Store guard:** `gradlew bundleRelease` thất bại với thông báo `Remove the Apple iOS 27 test wallpaper before a store build: app/src/main/res/drawable*-nodpi/default_wallpaper_ios27.jpg ...` khi các file còn tồn tại.

**Nguồn / ghi công (chỉ để thử):** Apple iOS 27 wallpapers, lấy qua bài của idownloadblog.com (https://www.idownloadblog.com/2026/06/10/ios-27-wallpapers/), JPG gốc, không mã hóa lại, tải 2026-11-09:

| Ảnh | URL | Kích thước | SHA-256 |
|---|---|---|---|
| Blue (sáng) | `https://media.idownloadblog.com/wp-content/uploads/2026/06/iOS-27-wallpaper-Blue.jpg` | 990.051 byte (0,94 MiB), JPEG progressive 1320x2868 | `4cac0ea0287b17b06fd7aba1f2088cdffa9dc15b6939a42b81b0c0d90788d142` |
| Dark Blue (tối) | `https://media.idownloadblog.com/wp-content/uploads/2026/06/iOS-27-wallpaper-Dark-Blue.jpg` | 1.095.508 byte (1,04 MiB), JPEG progressive 1320x2868 | `8b95d7af3c163b6bf715b9039a6db87ccf1bb311dfc04871b3f2f1d95350df10` |

Bản quyền thuộc Apple Inc.; không có giấy phép dùng trong sản phẩm phát hành. `file` xác nhận cả hai là JPEG thật.

## Hiện trạng (đọc code, 2026-11-09)

- "Người dùng đã chọn ảnh" = tồn tại file `files/backdrop_image` (`PickedImageSource.hasSaved()`, qua `BackdropRepository.hasBackdrop()`). Chọn ảnh chỉ qua Cài đặt > "Ảnh nền" (Photo Picker). Không có nút gỡ ảnh; gỡ = xóa file bằng tay.
- `SystemWallpaperSource` **chưa nối vào đâu** (phương án B đang gác, cần quyền "Tất cả tệp"), nên không có chuyện mặc định tranh với wallpaper hệ thống.
- Trước WP, chưa có ảnh thì `HomeViewModel.loadBackdropFromDisk` đặt `Empty`, `BackdropController` ẩn `backdrop_image`, Home hiện nền **đen** (`home_root` có `@color/black`).
- Cài đặt dòng "Ảnh nền" trước WP: "Đã có ảnh" / "Chưa chọn".
- `onScreenSize` nạp ảnh một lần; `reloadBackdropIfChanged` (onResume, qua `BackdropController.onResume`) nạp lại khi `backdropVersion()` đổi.
- `HomeActivity` **không** khai `uiMode` trong `configChanges`: đổi sáng/tối khi Home mở = Activity tạo lại, `HomeViewModel` giữ nguyên và **giữ bitmap cũ**. Xử lý bằng dấu phiên bản (xem dưới).
- Mọi lần `BackdropController.render` đổi bitmap → `notifyChanged(LOADED|REPLACED)` → `DockController.onBackdropChanged` (captureOnce, bài học DG) + `GlassEnvironment.notifyBackdropChanged`. Ảnh mặc định đi qua đúng đường này.
- Hộp thoại "Áp dụng làm wallpaper" (`evaluateWallpaperPrompt`) bật khi `backdropStatus == Ready`; nếu không chặn, ảnh mặc định làm nó hiện và `applyAsWallpaper` thất bại vì không có file.

## Vì sao chọn thiết kế này

**Vấn đề cần giải:**
1. Ảnh có bản quyền, tech lead cần thử trên **APK release**, nhưng ảnh **không được lên cửa hàng**. Cần một ràng buộc máy kiểm tra, không dựa vào trí nhớ.
2. Kính thật chỉ lấy nền ở lần vẽ kính (DG): đổi nền phải chụp lại.
3. Đổi sáng/tối khi Home mở tạo lại Activity nhưng ViewModel giữ bitmap cũ.
4. Bộ nhớ: Oppo A3x 720x1604; ảnh gốc 1320x2868 giải mã đầy đủ ≈ 15 MB.

**Các phương án đã xét (nơi đặt ảnh và cách chặn):**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Ảnh trong `src/debug` + bản release trả null | Tách theo source set | Release không bao giờ chứa ảnh | **Trái yêu cầu mới:** tech lead thử trên APK release | Bỏ (bản plan đầu, trước khi chủ dự án đổi Q4) |
| B. Ảnh trong `src/main/res` + **store guard Gradle** chặn `bundleRelease` | Một lớp ở `main`; task kiểm file tồn tại | Debug và release đều có ảnh để thử; quên xóa thì AAB cửa hàng không build được; `assembleRelease`/`assembleDebug` không bị ảnh hưởng | APK release (không phải AAB) VẪN chứa ảnh; guard chỉ chặn đường AAB | **Có** |
| C. Như B nhưng chỉ ghi chú, không guard | Không thêm task | Ít mã nhất | Dễ quên, ảnh không phép lên cửa hàng | Không |
| D. Tải ảnh lúc chạy từ URL | Không có ảnh trong repo/APK | Không chứa ảnh | Cần quyền INTERNET (app chưa có), mã mạng, phụ thuộc site ngoài | Không |

**Chọn ảnh theo sáng/tối:**

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| 1. Một ảnh (Blue) mọi chế độ | Đơn giản, 1 MB | Tối vẫn sáng chói; khác iOS | Không |
| 2. Blue (sáng) + Dark Blue (tối), qualifier `drawable-night-nodpi` cùng tên | Giống iOS; hệ thống tự chọn | +1 MB; phải xử lý Activity tạo lại | **Có** |
| 3. Cho người dùng chọn trong bộ iOS 27 (7 ảnh) | Linh hoạt | Thêm UI, ~7 MB ảnh bản quyền | Để sau |

**Cách bắt đổi sáng/tối:**

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| i. Thêm tham số `isNight` vào `reloadBackdropIfChanged` và `HomeActivity` | Rõ ràng | Sửa thêm file nóng, đổi chữ ký hàm | Không |
| ii. `backdropVersion()` trả khóa ảnh mặc định (-1 sáng, -2 tối) khi chưa chọn ảnh | Không sửa `HomeActivity`; dùng nguyên cơ chế so version ở onResume; sau recreate, onResume tự nạp lại | Version "âm" là quy ước ngầm (có ghi chú ở interface) | **Có** |

**Vì sao chọn B + 2 + ii (cơ sở: đọc code + yêu cầu chủ dự án 2026-11-09):** ảnh phải nằm trong `main` để APK release có ảnh; guard bảo đảm AAB (đường duy nhất lên cửa hàng) không build được khi ảnh còn. Hệ thống chọn ảnh theo `night`; dấu phiên bản tận dụng cơ chế sẵn có nên đụng ít file nóng nhất.

**Quyết định kỹ thuật chính:**
- *"Chưa chọn":* đúng một điều kiện, không có `files/backdrop_image`. Có file thì ảnh người dùng luôn thắng. Gỡ file thì Home quay về mặc định ở lần `onResume` kế (version thành -1/-2, khác version đã hiển thị).
- *Cỡ giải mã:* `inScaled=false`, `inSampleSize` lũy thừa 2 (không nhỏ hơn màn), rồi `createScaledBitmap` về cỡ "cover" của màn, giữ tỉ lệ. Oppo 720x1604: 1320x2868 thành ≈738x1604 (≈4,7 MB thay ≈15 MB). Đỉnh tạm lúc giải mã vẫn ≈15 MB (chấp nhận). Chạy ở luồng IO của repository.
- *Hộp thoại wallpaper:* `HomeUiState.backdropIsDefault`; `evaluateWallpaperPrompt` return khi true.
- *Cài đặt:* ba trạng thái dùng chung mọi bản build: "Đã có ảnh" / "Mặc định (iOS 27, bản thử)" / "Chưa chọn" (chỉ còn khi `isAvailable()` = false, tức sau khi gỡ ảnh bản thử).
- *Store guard:* task `checkNoStoreTestWallpaper` (lớp `CheckNoStoreTestWallpaper` trong `app/build.gradle.kts`), đầu vào khai báo là hai file ảnh (`ConfigurableFileCollection`, `PathSensitivity.NONE`), không đọc `Project` lúc chạy nên an toàn với configuration cache. Nối bằng `tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(...) }`.

**Đánh đổi chấp nhận:** (1) APK debug và release đều +≈2 MB. (2) Đổi sáng/tối có thể nháy ảnh cũ một nhịp (chủ dự án chấp nhận 2026-11-09). (3) Guard chỉ chặn `bundleRelease`; ai build APK release rồi tự đưa lên cửa hàng thì không bị chặn (cửa hàng dùng AAB). (4) Ảnh mặc định không áp làm wallpaper hệ thống.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Ảnh ở `app/src/main/res/drawable-nodpi/` và `drawable-night-nodpi/`, tên `default_wallpaper_ios27.jpg` | Chủ dự án 2026-11-09 (Q4 đổi: áp cả debug lẫn release) |
| D2 | Sáng = Blue, tối = Dark Blue, theo night mode hệ thống | Chủ dự án duyệt Q1 2026-11-09 |
| D3 | "Chưa chọn" = không có `files/backdrop_image`; ảnh người dùng luôn thắng | Đọc code |
| D4 | Một lớp `DefaultBackdropSource` ở `main`, không tách debug/release | Chủ dự án (Q4) |
| D5 | Ảnh mặc định không kích hoạt hộp thoại "Áp dụng làm wallpaper" | Tránh lỗi `applyAsWallpaper` |
| D6 | Giữ JPG gốc, không mã hóa lại | Yêu cầu chủ dự án |
| D7 | Không dùng `SystemWallpaperSource` | Đang gác (B6) |
| D8 | Nháy ảnh cũ khi đổi sáng/tối được chấp nhận | Chủ dự án (Q2) |
| D9 | Nhãn Cài đặt: "Mặc định (iOS 27, bản thử)" ở mọi bản build | Chủ dự án (Q3) |
| D10 | Store guard chặn `bundleRelease`; `assembleDebug`/`assembleRelease` vẫn chạy | Chủ dự án (Q4) |
| D11 | Xóa ảnh trước bản cửa hàng do chủ dự án/tech lead làm; WP merge sau G3e | Chủ dự án (Q5, Q6) |

## Ngoài phạm vi / ghi để sau
- Cho người dùng chọn trong bộ iOS 27 (Mud, Gold, Ink, Dark-Ink, Brown, Dark-Brown) ở Cài đặt.
- Nút "Gỡ ảnh nền" trong Cài đặt.
- Ảnh mặc định có giấy phép (hoặc gradient trung tính) cho bản cửa hàng.

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `data/backdrop/DefaultBackdropSource.kt` | Tạo | độc quyền |
| `app/src/main/res/drawable-nodpi/default_wallpaper_ios27.jpg`, `drawable-night-nodpi/default_wallpaper_ios27.jpg` | Tạo | độc quyền |
| `app/build.gradle.kts` (task `checkNoStoreTestWallpaper`, thêm cuối file) | Sửa | dùng chung (chỉ thêm cuối) |
| `data/backdrop/BackdropRepositoryImpl.kt`, `domain/repository/BackdropRepository.kt` (`hasDefaultBackdrop`, `loadDefaultBackdrop`, `backdropVersion` cho ảnh mặc định) | Sửa | dùng chung nhỏ |
| `ui/home/HomeViewModel.kt`, `ui/home/HomeUiState.kt` (`backdropIsDefault`, nhánh dự phòng, chặn prompt) | Sửa | **nóng, dùng chung với G3e**: sửa tối thiểu; nếu G3e vào trước thì merge `origin/main` (merge thường) |
| `ui/settings/SettingsViewModel.kt`, `SettingsUiState.kt`, `SettingsActivity.kt` (nhãn) | Sửa | dùng chung nhỏ |
| `app/src/main/res/values/strings.xml` (`settings_backdrop_default` ở cuối) | Sửa | dùng chung (chỉ thêm cuối) |

`HomeActivity.kt` và `BackdropController.kt` **không** sửa.

## Global Constraints
- Ảnh bản quyền chỉ để thử; `R.drawable.default_wallpaper_ios27` chỉ được tham chiếu trong `DefaultBackdropSource.kt` (có comment nêu hai file và store guard).
- Không thêm thư viện, không test, không đổi schema.
- Mọi thay đổi bitmap Home đi qua `BackdropController.render` để kế thừa `captureOnce`.
- Giải mã ở luồng IO, đúng cỡ màn.
- Guard an toàn với configuration cache; `assembleDebug`/`assembleRelease` phải xanh.
- Không tự cài APK lên máy (điều phối cài và kiểm).

## Review Focus
1. `R.drawable.default_wallpaper_ios27` chỉ xuất hiện trong `DefaultBackdropSource.kt` (grep).
2. `HomeViewModel`: đường ảnh người dùng không đổi; ảnh mặc định không bật `evaluateWallpaperPrompt`.
3. `backdropVersion()`: ảnh chọn thắng; không có ảnh chọn thì -1/-2 theo night mode; đổi sáng/tối làm `reloadBackdropIfChanged` nạp lại.
4. Bộ nhớ: bitmap mặc định ≈ cỡ màn.
5. Guard: `bundleRelease` thất bại khi file còn, qua khi file bị xóa; `assembleRelease` xanh; cache cấu hình dùng lại được.

---

### Task 1: Nguồn mặc định + nối vào đường nạp ảnh nền
**Bắt buộc kiểm tay:** có — kết quả là hình ảnh và kính; đổi sáng/tối và việc chụp lại kính chỉ nhìn được trên máy.
**Files:** theo bảng Vùng file.
**Interfaces:** Consumes `BackdropRepository` / `HomeViewModel` hiện có. Produces `DefaultBackdropSource`, `BackdropRepository.hasDefaultBackdrop()` / `loadDefaultBackdrop()`, `HomeUiState.backdropIsDefault`.
- [x] **Step 1:** `DefaultBackdropSource` (giải mã ở cỡ màn, chọn sáng/tối).
- [x] **Step 2:** Repository: dự phòng và dấu phiên bản -1/-2.
- [x] **Step 3:** `HomeViewModel`/`HomeUiState`: nhánh mặc định, `backdropIsDefault`, chặn prompt.
- [x] **Step 4:** Cài đặt: nhãn ba trạng thái, chuỗi `settings_backdrop_default`.
- [x] **Step 5:** Hai ảnh vào `src/main/res` (JPG gốc; SHA-256 ở bảng nguồn).
- [x] **Step 6:** Build xanh: `.\gradlew.bat assembleDebug lintDebug assembleRelease`.
- [ ] **Step 7: Kịch bản kiểm (Live 4, rồi Oppo A3x)** — xem "Bài kiểm cả tính năng" (điều phối cài và chạy).

### Task 2: APK release chứa ảnh + store guard hoạt động
**Bắt buộc kiểm tay:** không (chạy lệnh, đọc kết quả).
- [x] **Step 1:** `aapt2 dump resources` trên cả hai APK có `drawable/default_wallpaper_ios27` (debug: hai cấu hình `nodpi` và `night-nodpi`; release: tài nguyên có mặt, đường dẫn file bị rút gọn). Liệt kê zip: release có `res/aJ.jpg` (1.095.508 B) và `res/ac.jpg` (990.051 B).
- [x] **Step 2:** `.\gradlew.bat bundleRelease` thất bại ở `:app:checkNoStoreTestWallpaper` với thông báo "Remove the Apple iOS 27 test wallpaper before a store build: ..." (lần 2 dùng lại configuration cache vẫn thất bại đúng). Không tạo ra file AAB.
- [x] **Step 3:** tạm dời hai ảnh ra ngoài repo, `.\gradlew.bat checkNoStoreTestWallpaper` xanh, rồi trả ảnh về chỗ cũ (SHA-256 khớp, `git status` đúng).
- [x] **Step 4:** `assembleRelease` và `assembleDebug` xanh khi ảnh còn.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (chính) cho mọi thứ trừ kính; Oppo A3x cho kính dock/pill/thư mục (một lượt). Điều phối cài và kiểm. Ghi rõ APK **debug** hay **release** khi báo số đo (release start-up nhanh hơn ~4 lần trên Oppo).

**Chuẩn bị (mỗi máy):** sao lưu **cả** `files/` và DB bằng `run-as`, so hash từng file sau khi khôi phục (bài học DG).

**Các mục kiểm theo thứ tự:**
- a. **Trạng thái sạch:** xóa `files/backdrop_image` (`run-as ... rm files/backdrop_image`), mở Home: "Blue" phủ kín, không viền đen, không méo. Cài đặt "Ảnh nền" ghi "Mặc định (iOS 27, bản thử)". Không hiện hộp thoại "Áp dụng làm wallpaper".
- b. **Sáng/tối khi Home mở:** `adb shell cmd uimode night yes` rồi `no`: Home đổi sang "Dark Blue" rồi về "Blue"; không crash; ghi có nháy ảnh cũ không (đã chấp nhận).
- c. **Kính sau mỗi lần đổi nền (DG):** sau mục a và sau mỗi lần ở mục b, dock, pill, thư mục mở khớp ảnh mới: chụp Home 2,5 s sau resume không chạm, so với ảnh sau khi ép vẽ lại; sai khác trung bình phải 0 (script ở knowledgement DG mục 7). Oppo (kính thật) là máy chính cho mục này.
- d. **Chọn ảnh rồi gỡ:** chọn ảnh trong Cài đặt: Home đổi, Cài đặt "Đã có ảnh", hộp thoại wallpaper hiện như cũ (hồi quy M8a Task 5), bấm "Không áp dụng". Xóa `files/backdrop_image` bằng tay, quay về Home: lại ảnh mặc định đúng chế độ, kính chụp lại.
- e. **Ảnh đã chọn thắng:** với ảnh đã chọn, bật/tắt chế độ tối: ảnh người dùng giữ nguyên.
- f. **Bộ nhớ:** `adb shell dumpsys meminfo gambi.global.os26launcher` ở trạng thái mục a; ghi Graphics và Java Heap, đối chiếu ≈4,7 MB bitmap trên Oppo.
- g. **Khởi động lại:** kill app, mở lại: ảnh mặc định hiện đúng, không nháy đen kéo dài.
- h. **Guard:** Task 2 (đã chạy; điều phối chạy lại nếu muốn).
- **Hồi quy:** các dòng Cài đặt khác, đổi mức kính, đổi cỡ lưới, thư mục/menu icon vẫn bình thường trên nền mới.
- **Khôi phục:** trả lại `files/` và DB từ bản sao lưu, so hash.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa có (chờ điều phối).

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-09 | Plan | Viết plan (bản đầu: ảnh chỉ ở debug) | `aa478cf` | Chờ duyệt |
| 2026-11-09 | Plan | Chủ dự án duyệt, đổi Q4: áp dụng cả debug và release, ảnh ở `main`, thêm store guard | commit "update WP plan" | Bỏ tách debug/release |
| 2026-11-09 | 1-2 | Code, tải ảnh (990.051 B / 1.095.508 B, SHA-256 ở bảng nguồn), guard, build xanh | commit "add default wallpaper" | Debug 12.128.798 B, release unsigned 10.046.616 B |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Tham chiếu ảnh chỉ ở `DefaultBackdropSource.kt` | grep `default_wallpaper_ios27` trong `app/src` | |
| 2 | Ảnh chọn không đổi, không prompt cho mặc định | | |
| 3 | `backdropVersion()` -1/-2 + nạp lại khi đổi sáng/tối | | |
| 4 | Bitmap ≈ cỡ màn | | |
| 5 | Guard đúng hai chiều | Task 2 | |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: ghi "ảnh nền mặc định iOS 27 (bản thử, xóa trước bản cửa hàng)".
- `project_structure.md`: thêm `DefaultBackdropSource`, hai tài nguyên ảnh và task `checkNoStoreTestWallpaper`; nhắc xóa trước bản cửa hàng.
- `room-schema.md`: không đổi.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement.
