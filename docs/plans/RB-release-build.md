# RB — Bản release đúng nghĩa: R8 + baseline profile (TT#--) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động và không thêm thư viện test; kiểm chứng tay trên máy thật. **Agent thực thi không đụng máy thật** (không `adb`, không cài APK): mọi lệnh trên máy do điều phối chạy. Lệnh viết cho PowerShell.

**Nhánh:** `build/rb-release-build` · **Worktree:** `E:\SourceTree\IosLauncher-wt\RB-release-build` · **PR:** # (draft) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09
**Trạng thái:** Xong, chờ merge (đo + hồi quy)
**Khóa:** không schema Room · không file nóng độc quyền · cần Oppo A3x (đo cold start) + điều phối giữ máy (xem `phone-availability`)

**Goal:** dựng được một bản release thật sự dùng để đo và dùng thử: R8 bật (thu nhỏ + tối ưu mã, thu nhỏ tài nguyên nếu DSL cho phép), có keep rule đủ để không vỡ chức năng, có baseline profile viết tay để ART biên dịch sẵn đường khởi động, và có quy trình ký bằng khóa debug + cài đè lên bản debug an toàn.
**Architecture:** không đổi mã ứng dụng. Chỉ chạm cấu hình build: `app/build.gradle.kts` (bật tối ưu), `app/src/main/keepRules/rules.keep` (keep rule), `app/src/main/baseline-prof.txt` (profile viết tay). Quy trình ký/cài ghi vào knowledgement, không thêm `signingConfig` vào build file.
**Tech Stack:** AGP 9.3.3, Gradle 9.5.0 (wrapper), KGP 2.2.10 built-in (AGP mang theo, chưa ghi đè; nguồn: AGENTS.md "Build và toolchain", `gradle/libs.versions.toml`, `gradle-wrapper.properties`), KSP 2.3.11, Hilt 2.60.1, Room 2.8.5, minSdk 26 / targetSdk 37. **Không có dependency mới**: `androidx.profileinstaller:1.4.0` đã có sẵn gián tiếp (đo 2026-11-09 bằng `gradlew --offline :app:dependencyInsight --configuration releaseRuntimeClasspath --dependency profileinstaller`: kéo vào qua `androidx.activity:activity:1.13.0`; Google Maven hiện liệt kê bản mới nhất là `1.4.1`, đã đọc `dl.google.com/android/maven2/androidx/profileinstaller/group-index.xml`). Xem câu hỏi mở số 1.
**Spec:** AGENTS.md mục "Build và toolchain" (dòng `optimization { enable = false }`) và mục "B1, CL, IC và bài học đo bản debug" (debug chậm gấp ~4,4 lần release: icon cuối 2822 ms so với 637 ms; mở cửa sổ ~1250 ms so với ~410 ms).

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- Số đo khởi động trên bản debug không phản ánh thứ người dùng thấy (AGENTS.md, đo 2026-11-09): debug `debuggable`, dexopt `run-from-apk`, `cmd package compile -m speed -f` lên debug chỉ đạt `verify`. Muốn đo hay tối ưu tiếp phải có bản release thật.
- Bản release hiện tại không đại diện cho sản phẩm: R8 tắt (`optimization { enable = false }`), không có baseline profile, `app/src/main/keepRules/rules.keep` chỉ là mẫu của AGP.
- Ràng buộc dự án: **không test, không thư viện test** (AGENTS.md, chốt 2026-10-16); chưa có khóa ký thật; máy test dùng chung với việc khác của chủ dự án; Android Studio và Gradle của bản chính không được quét vào worktree.
- Dữ kiện đã đọc cho phần rủi ro R8 (nguồn: đọc code + mở AAR trong cache Gradle, 2026-11-09):
  - Thư viện kính `liquidglass:v2.0.11` có `proguard.txt` **chỉ gồm 4 dòng `-dontwarn`** (appcompat, material, viewpager2, recyclerview), **không có keep rule nào**. Nó chứa `.so` JNI nối theo **tên hàm**: chuỗi trong `libnativegauss.so` có `Java_com_example_blur_NativeGauss_*`, `Java_com_example_liquidglass_NativeChromaticDispersion_chromaticDispersionInplace`, `Java_com_example_liquidglass_NativeChromaticAberration_chromaticAberrationInplace`, không thấy `RegisterNatives`. Đổi tên/xóa các class đó thì `UnsatisfiedLinkError` ngay khi vẽ kính. **Đây là rủi ro R8 thật duy nhất mà app phải tự giữ.**
  - `AppWindows` của thư viện dùng reflection lên `android.view.WindowManagerGlobal.mViews` (class framework, R8 không đổi tên) và tự bắt lỗi (log `WindowManagerGlobal.mViews unavailable`) nên không vỡ cứng.
  - Mã app: không có `Class.forName`, `getDeclaredMethod`, kotlin-reflect, Gson/JSON. Chỉ có `View::class.java.name` trong `SearchPillView` (class framework, dùng cho trợ năng). Custom View trong XML (8 view: `DockView`, `DragLayer`, `FolderPanelView`, `IconActionMenu`, `SearchPillView`, `CategoryPageView`, `CategoryGroupPanelView`, `SearchOverlayView`) được AAPT2 tự sinh keep rule (`aapt_rules.txt`); Activity và `LauncherApp` trong manifest cũng vậy.
  - Room (`LauncherDatabase_Impl` nạp theo tên), Hilt/Dagger, DataStore preferences (protobuf-lite), AppCompat, Material, ViewPager2, RecyclerView đều là thư viện AndroidX/Google có consumer rule trong AAR. **Phải kiểm bằng `configuration.txt`, không tin trí nhớ** (xem Task 1).

**Các phương án đã xét.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **(A) Baseline profile viết tay, wildcard** + R8 | `src/main/baseline-prof.txt` với `HSPL`/`L` wildcard cho package của app và thư viện kính; AGP tự đóng vào APK; `profileinstaller` (đã có sẵn) áp lên bản sideload | Không thêm module, không thêm thư viện test, hợp quy tắc "không test"; làm trong vài chục dòng; sửa/đọc được bằng mắt; hợp với app nhỏ (189 file Kotlin) nơi hầu hết mã đều nằm trên đường khởi động | Thiếu chính xác: có thể biên dịch dư các hàm không nóng (odex to hơn, cài chậm hơn một chút); không biết hàm nào *thật sự* nóng; mỗi lần đổi cấu trúc package phải xem lại | **Chọn** |
| (B) Profile sinh bằng Macrobenchmark (`BaselineProfileRule`) | Thêm module `benchmark`/`baselineprofile`, chạy trên máy, AGP sinh profile | Chính xác nhất, chỉ chứa hàm thật sự chạy | Cần thư viện test/benchmark (`androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator`) và một module kiểu test: **trái quy tắc "không test, không thư viện test"** (AGENTS.md 2026-10-16); cần máy root/userdebug hoặc emulator để chạy ổn định; máy test đang dùng chung | Không (xem lại nếu chủ dự án đổi quy tắc) |
| (C) Chỉ R8, không profile | Bật tối ưu, bỏ qua profile | Đơn giản nhất, ít rủi ro nhất | R8 không giải quyết JIT/interpreter ở lần chạy đầu và sau mỗi lần cài; dexopt của bản sideload thường chỉ `verify` cho tới khi hệ thống biên dịch nền; startup vẫn chậm hơn mức có thể | Không (nhưng đây là điểm lùi an toàn nếu Task 2 không cải thiện số đo) |

**Vì sao chọn (A).** (1) Số đo của dự án cho thấy khoảng chênh lớn nhất là *biên dịch*, không phải mã (debug 2822 ms so với release 637 ms, AGENTS.md, đo thật) — baseline profile đánh đúng chỗ này. (2) (B) vi phạm quy tắc đã chốt; (A) không. (3) App nhỏ, đường khởi động rộng (Hilt, Room, DataStore, RecyclerView, ViewPager2, kính) nên wildcard cả package là xấp xỉ chấp nhận được; chi phí sai (biên dịch dư) chỉ là kích thước odex và thời gian dexopt, không phải lỗi chức năng. (4) `profileinstaller` đã nằm sẵn trong đồ thị phụ thuộc nên không cần dependency mới (nguồn: `dependencyInsight`, đo 2026-11-09). (5) Có điểm lùi: gỡ `baseline-prof.txt` thì về phương án (C).

**Quyết định `-dontobfuscate` ở lần đầu: GIỮ (không đổi tên).** Lý do: (a) thứ làm bản release nhanh là thu nhỏ + tối ưu + profile, đổi tên chỉ giảm vài chục KB dex và không giúp tốc độ đáng kể; (b) đổi tên là nguồn rủi ro lớn nhất của R8 (reflection, JNI theo tên, class đọc theo tên trong profile) trong khi app là launcher sideload cá nhân, không có yêu cầu giấu mã; (c) stack trace và tên class trong `dumpsys`/perfetto vẫn đọc được, giúp chủ dự án đo tiếp (AGENTS.md có nhiều bài học đo bằng tên class); (d) wildcard trong baseline profile viết theo tên gốc nên không phải lo R8 viết lại profile. Cách làm: dòng `-dontobfuscate` trong `rules.keep`. Xét lại sau khi hồi quy qua (Task 4): nếu muốn thử, gỡ dòng này ở một commit riêng và chạy lại hồi quy; chưa nằm trong phạm vi RB.

**Đánh đổi chấp nhận.** Bản release to hơn bản có đổi tên một chút; profile có thể biên dịch dư; ký bằng khóa debug nên **không phát hành** được (chỉ dùng thử). Khi nào xét lại: khi có khóa ký thật (đổi quy trình ký), khi có yêu cầu giảm kích thước/giấu mã (bật obfuscate), khi chủ dự án cho phép module benchmark (chuyển sang B).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Bật R8 cho release; viết keep rule cho mọi thứ cần | Chủ dự án, 2026-11-09 |
| 2 | Baseline profile **phương án A** viết tay, không module benchmark, không thư viện test | Chủ dự án, 2026-11-09; hợp quy tắc AGENTS.md 2026-10-16 |
| 3 | Bản thử ký bằng khóa debug, cài đè bản debug bằng `adb install -r -d` | Chủ dự án, 2026-11-09 |
| 4 | Điều phối đo cold start (trước/sau) và chạy hồi quy đầy đủ trên release; agent thực thi không đụng máy | Chủ dự án, 2026-11-09 |
| 5 | Lần đầu giữ `-dontobfuscate` | Chủ dự án duyệt 2026-11-09 (lý do ở trên) |
| 6 | Cách ký: ký tay bằng `apksigner` + `~/.android/debug.keystore`, **không** thêm `signingConfig` vào build file | Chủ dự án duyệt 2026-11-09 (xem Task 3) |

## Ngoài phạm vi / ghi để sau
- Khóa ký thật, `versionCode`/`versionName` cho phát hành, Play Console: chưa có khóa nên bỏ.
- Obfuscation (bỏ `-dontobfuscate`) và rút gọn keep rule cho sát hơn.
- Macrobenchmark/profile sinh tự động (phương án B): chỉ xét nếu quy tắc "không test" được nới.
- Nâng `profileinstaller` lên 1.4.1 hay ghim bản: xem câu hỏi mở.
- Đo lại các mục hiệu năng khác (kính, kéo-thả) trên release: chỉ làm phần cần cho hồi quy, không thành bài đo mới.

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `app/build.gradle.kts` | Sửa (khối `buildTypes.release.optimization`) | Dùng chung có kiểm soát |
| `app/src/main/keepRules/rules.keep` | Sửa (thay nội dung mẫu bằng rule thật) | Không |
| `app/src/main/baseline-prof.txt` | Tạo | Không |
| `docs/plans/RB-release-build.md` | Tạo (file này) | Không |
| `docs/knowledgements/RB-release-build.md` | Tạo khi đóng (gồm quy trình build/ký/cài/quay lại debug) | Không |

Không sửa mã Kotlin, layout, manifest, `libs.versions.toml`, schema Room, và không đụng `HomeActivity`/`HomeViewModel`/`LayoutReconciler`/`core/glass/*`/`DragHelper`. Nếu cần sửa ngoài vùng này (ví dụ một rule cần `@Keep` trong mã), agent dừng và báo.

## Global Constraints
- Không test, không thư viện test; không dependency mới trừ khi chủ dự án duyệt (hiện đề xuất: không cần).
- Không `adb`, không cài APK, không `run-as` (điều phối làm). Agent chỉ chạy được `gradlew.bat assembleRelease` và đọc file trong `app\build`.
- Không sửa tài liệu chung (AGENTS.md, MultiAgent-Workflow.md, project_structure.md, Launcher-Features.md, room-schema.md); đề xuất ghi ở cuối plan.
- Không xóa file. Không force-push, không merge, không đánh dấu ready.
- Commit message tiếng Anh, không dấu, kết thúc bằng dòng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Không đoán tên DSL/phiên bản: đọc DSL thật của AGP 9.3.3 trước khi sửa build file; kiểm file/APK mới có thật trước khi tin kết quả build (bài học `$LASTEXITCODE` cũ, AGENTS.md).

## Review Focus
1. `rules.keep`: mỗi rule có comment lý do; không có rule rộng kiểu `-keep class gambi.** { *; }` che mất vấn đề.
2. `-keepclasseswithmembernames class * { native <methods>; }` (hoặc tương đương) có mặt, và `configuration.txt` cho thấy nó nằm trong cấu hình R8 cuối.
3. Các rule "dự kiến không cần" (Hilt, Room, DataStore, custom View, manifest) thật sự đã có trong `configuration.txt` từ consumer rule/`aapt_rules.txt`, không phải do phỏng đoán.
4. `baseline-prof.txt` thật sự có trong APK release (`assets/dexopt/baseline.prof`) và cú pháp wildcard được AGP chấp nhận khi build.
5. Không dependency mới, không đụng file ngoài vùng file.
6. Quy trình ký/cài/quay lại debug đủ để điều phối làm theo mà không phải hỏi, gồm bước sao lưu dữ liệu TRƯỚC khi cài release.

---

### Task 1: Bật R8 và viết keep rule
**Bắt buộc kiểm tay:** có — R8 chỉ vỡ lúc chạy (JNI, nạp theo tên) và build xanh không chứng minh được gì; phải chạy hồi quy chức năng trên bản release (mục 4.2 của MultiAgent-Workflow.md). Phần agent làm được: build xanh và đọc cấu hình; phần kiểm chạy là Task 4.
**Files:** `app/build.gradle.kts`, `app/src/main/keepRules/rules.keep`
**Interfaces:** Consumes: consumer rule của các AAR, `aapt_rules.txt`. Produces: `assembleRelease` chạy R8, file `app\build\outputs\mapping\release\configuration.txt` và `usage.txt` để review.
- [ ] **Step 1: Đọc DSL thật.** Mở `app/build.gradle.kts` (hiện `release { optimization { enable = false } }`) và xác minh trong AGP 9.3.3 (tài liệu chính thức hoặc API của plugin trong cache Gradle) khối `optimization` còn gì: `enable`, `keepRules`, và resource shrinking nằm ở đâu (`shrinkResources` hay gộp trong `enable`). Ghi kết quả vào Nhật ký. Không đoán tên thuộc tính.
- [ ] **Step 2: Bật tối ưu.** `optimization { enable = true }` (và bật thu nhỏ tài nguyên theo đúng DSL tìm được ở Step 1; nếu DSL tự gộp thì chỉ ghi lại). Chưa thêm `signingConfig`.
- [ ] **Step 3: Viết `rules.keep`** (AGP tự gộp mọi file trong `keepRules`). Danh sách rule dự kiến, mỗi rule kèm comment lý do trong file:

| Rule | Lý do |
|---|---|
| `-dontobfuscate` | Giảm rủi ro lần đầu, giữ tên class cho stack trace và đo (xem "Quyết định -dontobfuscate") |
| `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }` | `libnativegauss.so` của thư viện kính nối JNI **theo tên hàm** (`Java_com_example_blur_NativeGauss_*`, `NativeChromaticDispersion`, `NativeChromaticAberration`); thư viện không tự đem keep rule (chỉ có `-dontwarn`). Thiếu rule: `UnsatisfiedLinkError` khi vẽ kính |
| `-keepattributes SourceFile,LineNumberTable` | Giữ số dòng cho stack trace khi báo lỗi trên release |
| (nếu `configuration.txt` cho thấy thiếu) rule giữ `LauncherDatabase_Impl`/`RoomDatabase` | Room nạp `*_Impl` bằng `Class.forName` theo tên; AAR Room thường có sẵn rule này nên chỉ thêm khi **không** thấy trong cấu hình cuối |
| (nếu thiếu) rule Hilt/Dagger, DataStore, custom View | Dự kiến **không cần**: do consumer rule, `aapt_rules.txt` (custom View trong XML, Activity/Application trong manifest) và Hilt sinh mã tĩnh. Chỉ thêm khi chứng minh được bằng `configuration.txt` hoặc lỗi build |
| (nếu cần) `-dontwarn` | Chỉ cho cảnh báo R8 báo thật, kèm lý do từng dòng |

- [ ] **Step 4: Build.** `.\gradlew.bat assembleRelease` (PowerShell, từ thư mục worktree). Kiểm có APK mới (`app\build\outputs\apk\release\*.apk`, so thời gian) trước khi báo xanh. Đọc `app\build\outputs\mapping\release\configuration.txt` để xác nhận các rule Room/Hilt/DataStore/native có mặt; đọc `usage.txt` (cái bị gỡ) để soi xem lớp nào của app/thư viện kính bị loại bất ngờ. Ghi kết quả vào Nhật ký. APK này chưa ký đủ để cài; không cài.
- [ ] **Step 5: Kịch bản kiểm (điều phối gộp vào Task 4).** Cài release (quy trình Task 3) → mở Home → vào chế độ kính thật: không crash `UnsatisfiedLinkError`; icon, dock, folder hiện đủ.

---

### Task 2: Baseline profile viết tay
**Bắt buộc kiểm tay:** có — hiệu quả chỉ đo được trên máy thật bằng cold start và `dumpsys package dexopt` (trạng thái biên dịch), không thể suy ra từ build xanh.
**Files:** `app/src/main/baseline-prof.txt` (mới)
**Interfaces:** Consumes: tên package/class thật của app. Produces: `assets/dexopt/baseline.prof` trong APK release; `profileinstaller` (đã có sẵn) ghi profile cho ART trên máy sideload.
- [ ] **Step 1: Xác định đường khởi động từ mã thật**: `LauncherApp` (Hilt), `HomeActivity` và các controller của Home, `IconGridView`, `IconLoader` (cache icon), `LauncherAppsDataSource`, Room (`LauncherDatabase`, DAO), DataStore, kính (`core/glass`, `com.example.liquidglass.*`). Đọc code và `docs/knowledgements/` các mốc P1, LC, IC để lấy đúng tên; không đoán tên.
- [ ] **Step 2: Viết file** với wildcard rộng cho package app, dạng (cú pháp ART/profgen, cần xác nhận khi build):
  - `HSPLgambi/global/os26launcher/**;->**(**)**` (hot + startup + post-startup, mọi method của app)
  - `Lgambi/global/os26launcher/**;` (nạp sẵn class)
  - cùng kiểu cho `com/example/liquidglass/**` và `com/example/blur/**`, và dagger/hilt sinh mã (`Ldagger/**`, `Lhilt_aggregated_deps/**` nếu có trong APK)
  - Không liệt kê thư viện AndroidX (RecyclerView, ViewPager2, Room, DataStore, lifecycle...): nhiều bản đã kèm profile riêng, AGP gộp tự động. Nếu số đo thấy chưa đủ thì thêm, có ghi lý do.
  Mỗi nhóm dòng có comment ngắn (`#`) nói nó phủ phần nào của khởi động.
- [ ] **Step 3: Build** `.\gradlew.bat assembleRelease`, xác nhận cú pháp được chấp nhận (không lỗi profgen/`mergeArtProfile`) và APK có `assets/dexopt/baseline.prof` (mở zip kiểm, ví dụ bằng `Expand-Archive` ra thư mục tạm trong scratchpad hoặc `jar tf`). Ghi kích thước file profile vào Nhật ký.
- [ ] **Step 4: Kịch bản kiểm (điều phối, Oppo A3x).** Sau cài release: mở app một lần (profileinstaller ghi profile), rồi (a) ép biên dịch theo profile `adb shell cmd package compile -m speed-profile -f gambi.global.os26launcher` (hoặc chờ dexopt nền), (b) `dumpsys package gambi.global.os26launcher | findstr /i "status"` phải ra `speed-profile` (không còn `verify`), (c) đo cold start (Task 4). Nếu số đo không cải thiện: báo điều phối, cân nhắc thu hẹp/mở rộng wildcard hoặc lùi về phương án C.

---

### Task 3: Quy trình ký, cài đè và quay lại debug (tài liệu)
**Bắt buộc kiểm tay:** không (chỉ là tài liệu; điều phối chạy thật ở Task 4 và sẽ sửa tài liệu nếu lệnh sai).
**Files:** nội dung ghi vào plan (mục này) và sang `docs/knowledgements/RB-release-build.md` khi đóng. Không sửa file shared.
**Interfaces:** Consumes: APK từ Task 1-2. Produces: quy trình điều phối chạy được.
- [ ] **Step 1: Chọn cách ký và giải thích.** Hai cách:
  - (1) `signingConfig` trỏ vào khóa debug trong `release` của `build.gradle.kts`: một lệnh, AGP tự zipalign + ký. Nhược: đưa khóa debug vào cấu hình release **vĩnh viễn**, sau này dễ quên và phát hành nhầm bản ký debug; đụng file dùng chung.
  - (2) Ký tay sau build: `assembleRelease` ra APK chưa ký, rồi `zipalign` + `apksigner` bằng `~/.android/debug.keystore` (đúng cách AGENTS.md đã mô tả ở mục "B1, CL, IC"). Không đổi build file, không rò khóa debug vào cấu hình, dễ thay bằng khóa thật sau này.
  **Chọn (2).** Cả hai cùng chữ ký với bản debug, nên cài đè giữ nguyên dữ liệu.
- [ ] **Step 2: Lệnh (điều phối chạy; đường dẫn build-tools là ví dụ, lấy số thật từ SDK của máy, không đoán)** PowerShell:
  1. Dựng: `.\gradlew.bat assembleRelease`; kiểm có `app\build\outputs\apk\release\app-release-unsigned.apk` mới.
  2. Căn: `zipalign -p -f 4 app-release-unsigned.apk app-release-aligned.apk`
  3. Ký: `apksigner sign --ks $env:USERPROFILE\.android\debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out app-release.apk app-release-aligned.apk`; kiểm `apksigner verify --print-certs app-release.apk` có cùng chữ ký với bản debug đang cài.
  4. **Sao lưu dữ liệu TRƯỚC khi cài** (bản release không `debuggable` nên `run-as` hỏng sau đó): `adb exec-out run-as gambi.global.os26launcher tar cf - databases files/datastore no_backup > backup.tar` (giống CL), hoặc `adb backup`/chụp màn hình bố cục. Hỏi chủ dự án máy nào còn rảnh (`phone-availability`).
  5. Cài đè: `adb install -r -d app-release.apk` (versionCode vẫn 1 nên `-r` đủ, `-d` phòng trường hợp bị coi là hạ cấp).
  6. Sau cài, Oppo có thể mất launcher mặc định (hộp chọn hiện khi nhấn Home): báo chủ dự án, **không tự chọn** (bài học 2026-11-09).
- [ ] **Step 3: Quay lại bản debug.** Dựng `.\gradlew.bat assembleDebug`, `adb install -r -d app\build\outputs\apk\debug\app-debug.apk` (cùng chữ ký debug nên không cần gỡ, dữ liệu giữ nguyên, `run-as` dùng lại được). Nếu cài từ chối vì chữ ký khác: gỡ app (mất dữ liệu) rồi cài lại, khôi phục từ bản sao lưu bằng `run-as ... tar xf -` — chỉ làm khi chủ dự án đồng ý.

---

### Task 4: Điều phối đo và hồi quy trên release
**Bắt buộc kiểm tay:** có — toàn bộ là chạy tay trên máy thật; R8 chỉ lộ lỗi lúc chạy (mục 4.2). Do **điều phối** làm, không phải agent thực thi.
**Files:** không sửa file; ghi kết quả vào mục Kết quả kiểm chứng và Nhật ký.
**Interfaces:** Consumes: APK từ Task 1-3, máy rảnh.
- [x] **Step 1: Đo "trước".** Dựng release **không** R8/profile (nhánh `main` hoặc commit trước Task 1) theo quy trình Task 3, cài lên Oppo A3x, 5 lượt cold start sau một lượt khởi động: "icon cuối vẽ xong" (cách đo mốc P1/IC) và `am start -W` (`TotalTime`/`WaitTime`). Ghi rõ "bản release, không R8, không profile" (memory `release-vs-debug-startup`).
- [x] **Step 2: Đo "sau".** Cài release có R8 + profile, mở một lần, `cmd package compile -m speed-profile -f` (hoặc chờ dexopt nền), kiểm `status=speed-profile`, đo lại cùng cách, cùng số lượt. Nên đo thêm bản "R8 không profile" nếu dư thời gian để tách đóng góp từng phần.
- [x] **Step 3: Hồi quy đầy đủ trên release** (Vsmart Live 4 cho chức năng không kính; Oppo A3x cho kính). Mỗi mục ghi đạt/không:
  - Home: nạp icon, vuốt trang, dock, nền (wallpaper hệ thống và ảnh chọn), xoay/khóa xoay, system bar.
  - Kéo-thả: kéo icon trong trang, qua trang, dock ↔ lưới, trang tạm, dấu "−", chế độ chỉnh sửa, thả bị từ chối không kẹt ẩn ô.
  - Folder: tạo, mở, kéo vào/ra, đổi tên.
  - Thư viện ứng dụng (App Library) và ẩn/hiện app.
  - Tìm kiếm: pill, vuốt xuống, kết quả, mở app, mở kết quả cài đặt.
  - Cài đặt: cỡ lưới + re-flow, mức kính, kiểu icon, đổi ảnh nền.
  - Widget Đồng hồ và Lịch (mở trình xử lý hệ thống, không hộp chọn).
  - Kính: **real** và **frosted** (ép `FORCE_HIGH` theo mô tả máy thử), kính dock sau đổi nền, menu nhấn giữ icon kính, không crash `UnsatisfiedLinkError` (JNI).
  - Menu nhấn giữ icon: các mục, gỡ cài đặt.
  - App mặc định ở dock (DD): điện thoại, tin nhắn, trình duyệt, máy ảnh.
  - Cài/gỡ app khi launcher đang chạy (đồng bộ bố cục), khởi động lại launcher (catch-up).
  - Dữ liệu: bố cục và Room/DataStore giữ nguyên sau cài đè.
  - `logcat` lọc lỗi: `UnsatisfiedLinkError`, `ClassNotFoundException`, `NoSuchMethodError`, `NoSuchFieldError`, `AndroidRuntime`.
  Lỗi nào do R8: thêm keep rule đúng chỗ (vòng sửa tối đa 5), ghi nguyên nhân vào Nhật ký.
- [x] **Step 4: Quay lại bản debug** (Task 3 Step 3) nếu chủ dự án muốn tiếp tục debug; hoặc giữ release và báo.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Oppo A3x (đo khởi động + kính), Vsmart Live 4 (hồi quy chức năng). Chuẩn bị: xác nhận với chủ dự án máy đang rảnh; sao lưu dữ liệu bằng `run-as` TRƯỚC khi cài release; bản "trước" và "sau" đo cùng máy, cùng số lượt, cùng cách đo. Thứ tự: Task 3 (dựng + ký + cài) → Task 2 Step 4 (trạng thái biên dịch) → Task 4 Step 1-2 (đo) → Task 4 Step 3 (hồi quy). Phần hồi quy theo danh sách ở Task 4 Step 3.
**Kết quả kiểm chứng (Oppo A3x, 2026-11-09, `build/rb-release-build@c3e66e9`, điều phối):**
- Đo khởi động lạnh (Os26Launcher là launcher mặc định; đặt Cài đặt hệ thống lên trên rồi `force-stop` để hệ thống không tự mở lại Home; `am start -W`, 5 lượt, trung vị `TotalTime` = đến khung hình đầu): debug 1198 ms (3 lượt) · release không R8, mới cài 422 ms · release không R8 sau `compile -m speed-profile` 471 ms · **RB (R8 + profile), mới cài 409 ms · RB sau `speed-profile` 405 ms** (`status=speed-profile`). APK: 7,96 MB → 2,75 MB. Chưa đo "icon cuối vẽ xong": log tạm `ICT` của IC không còn trong mã, nên chỉ có thời gian tới khung đầu.
- Hồi quy rút gọn trên release (Oppo, ảnh chụp từng bước): Home + dock kính, vuốt trang, menu nhấn giữ (có shortcut app), mở/đóng thư mục (panel kính), vuốt xuống tìm "chrome" (kết quả + shortcut), chế độ chỉnh sửa, Cài đặt, App Library, kéo icon vào ô trống: đều đạt; crash buffer rỗng; không có `UnsatisfiedLinkError`/`ClassNotFoundException`/`NoSuchMethodError` của app.
- Hồi quy trên Vsmart Live 4 (release, cùng APK, 2026-11-09, `font_scale` 1,15, kính FORCE_HIGH): 11/11 đạt — Home + dock kính thật, vuốt trang, menu nhấn giữ, mở thư mục, tìm "chrome", widget Đồng hồ mở `com.google.android.deskclock` và widget Lịch mở `com.vinsmart.calendar` (không hộp chọn), dock "Tin nhắn" mở `com.vinsmart.messaging` (DD), Cài đặt mở và ba hộp chọn Mức kính / Kiểu icon / Cỡ lưới hiện đủ lựa chọn, App Library; crash buffer rỗng. Dòng `NoClassDefFoundError ... AconfigPackage` trong logcat là của `system_server` (pid 597), không phải app.
- Không chạy: đổi giá trị thật của cỡ lưới/kiểu icon/ảnh nền/mức kính (chỉ mở hộp chọn), cài/gỡ app khi đang chạy. Các đường này không dùng reflection hay JNI mới so với phần đã chạy.
- Live 4 quay lại debug, dữ liệu khớp hash (chỉ thêm file đánh dấu của profileinstaller), về launcher Vsmart.
- Quay lại debug: `assembleDebug` của `main`, `adb install -r -d`, khôi phục `run-as tar`; so hash: DB và DataStore khớp bản sao lưu, chỉ thêm `files/profileinstaller_profileWrittenFor_lastUpdateTime.dat` do bản release ghi. Launcher mặc định giữ nguyên.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-09 | Task 1 | DSL AGP 9.3.3: `optimization.enable` gồm cả thu nhỏ tài nguyên; bật; `rules.keep` 3 rule; build xanh; đối chiếu `configuration.txt`/`seeds.txt`/`usage.txt` | `480f3eb` | Room, Hilt, DataStore, custom View, manifest không cần rule tay |
| 2026-11-09 | Task 2 | `baseline-prof.txt` wildcard; build xanh; `assets/dexopt/baseline.prof` có trong APK (2 431 B); expand 10 740 dòng, còn 3 629 sau R8 | `cfdf2ea` | Chờ điều phối đo trên máy |
| 2026-11-09 | Task 3 | Knowledgement + quy trình ký/cài/quay lại | (commit docs) | Tài liệu; điều phối chạy ở Task 4 |
| 2026-11-09 | Task 4 | Điều phối đo cold start 4 cấu hình + hồi quy trên Oppo và Live 4, quay lại debug cả hai máy | — | Đạt |
| 2026-11-09 | Plan | Đọc build file, AAR liquidglass (proguard.txt, `.so`), dependencyInsight cho profileinstaller; viết plan | `docs: add RB plan` | Chờ duyệt; chưa có code |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: không.
- `project_structure.md`: thêm `app/src/main/baseline-prof.txt` và vai trò của `keepRules/rules.keep` (nếu liệt kê file cấu hình build).
- `room-schema.md`: không.
- `AGENTS.md`: sửa dòng "Release build hiện có `optimization { enable = false }`" thành mô tả R8 bật; thêm quy trình build/ký/cài/quay lại debug và bài học keep rule (xem "Bài học cho AGENTS.md" trong knowledgement khi đóng).

## Câu hỏi mở cho chủ dự án (đã trả lời 2026-11-09: giữ profileinstaller 1.4.0 gián tiếp; duyệt -dontobfuscate; duyệt ký tay; điều phối chạy Task 4 trên Oppo sau)
1. `profileinstaller` đã có sẵn bản 1.4.0 (gián tiếp); bản stable mới nhất trên Google Maven là 1.4.1. Muốn **giữ nguyên** (đề xuất, không đụng `libs.versions.toml`) hay khai tường minh `androidx.profileinstaller:profileinstaller:1.4.1` làm dependency trực tiếp để chắc chắn profile được áp khi dependency gián tiếp đổi? Khai trực tiếp là dependency mới, cần duyệt.
2. Duyệt giữ `-dontobfuscate` ở lần đầu (quyết định 5)?
3. Duyệt ký tay bằng `apksigner` thay vì `signingConfig` (quyết định 6)?
4. Máy nào được dùng cho Task 4 và khi nào rảnh?
