# G2d — Cài đặt dạng trung tâm có nhóm (settings hub) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không cần: nhánh này không đụng kính, chỉ đổi cấu trúc màn Cài đặt). Lệnh viết cho CMD/PowerShell. Agent không tự cài APK.

**Nhánh:** `feature/g2d-settings-hub` · **Worktree:** `E:\SourceTree\IosLauncher-wt\G2d-settings-hub` · **PR:** #47 (draft) · **Bắt đầu:** 2026-11-12 · **Plan duyệt:** 2026-11-12 (chủ dự án; Q1 chọn hiện mờ, xem Quyết định 6)
**Trạng thái:** Chờ review (đã rebase lên `main` 60ccaee, chủ dự án kiểm tay đạt 2026-11-12)
**Khóa:** không (không đổi schema Room, không thêm khóa DataStore, không cần Oppo)

**Goal:** Dựng lại màn Cài đặt thành **trang chủ Cài đặt** (nhóm theo bản nháp Figma) và **một màn con cho mỗi nhóm mục**; chuyển các cài đặt đang có vào đúng màn con; số phiên bản đọc lúc chạy ở cuối trang chủ; `versionName` thành `1.0.0`. **Không thêm tính năng mới**, **giữ nguyên kiểu hiển thị hàng/danh sách hiện tại**.

**Architecture:** Giữ **một** `SettingsActivity` làm *khung* (toolbar, inset, danh sách, nạp trạng thái, điều phối vòng đời) và nhận extra `EXTRA_SCREEN` để biết dựng màn nào. Mỗi màn là một lớp nhỏ `SettingsScreen` tự dựng danh sách hàng, tự xử lý bấm, tự giữ hộp thoại và bộ chọn của nó; khung tái dùng nguyên `SettingsAdapter`/`SettingsRow`/`SettingsViewModel`. Trang chủ cũng là một `SettingsScreen`. Đi từ trang chủ vào màn con = `startActivity` cùng lớp với extra khác (Back của hệ thống quay về trang chủ). Danh sách nhóm/mục của trang chủ nằm ở **một bảng khai báo** theo đúng thứ tự bản nháp; mục nào chưa có màn thì hiện mờ với giá trị "Sắp có" và tự bật khi màn được đăng ký.

**Tech Stack:** Kotlin, Views/XML, Hilt, DataStore. Không dependency mới, không đổi Room, không đổi `HomeActivity`.

**Spec:** `docs/Figma-Draft.md` mục 3–6 (nằm ở docs PR #46, **chưa có trên `main`**: nhánh này phụ thuộc PR đó để link docs đúng) + `docs/plans/M8a-settings.md` (cách dựng màn Cài đặt) + `docs/plans/GS-glass-settings.md` (khối kính tùy chỉnh). Không có mục TT# riêng trong `Launcher-Features.md`.

## Vì sao chọn thiết kế này

### Vấn đề cần giải (đọc code `main` @ `e52a951`, 2026-11-12)

- `SettingsActivity.kt` dài 591 dòng và gom **mọi thứ**: dựng hàng (`buildBaseRows`, `buildGlassCustomRows`), 5 hộp thoại (kính, cảnh báo kính, ảnh nền ba đích, cỡ lưới + xác nhận, kiểu/màu/gói icon), 2 `ActivityResult` launcher (`pickImage`, `requestDefault`), luồng launcher mặc định có đo thời gian (B12/FX6), nhận sự kiện từ ViewModel và ép dựng lại hàng khi hủy/lỗi (`forceRebind`). Thêm 15+ màn nữa (FD-1/FD-2/FD-3 trong `Figma-Draft.md` mục 6) vào đúng file này thì mỗi nhánh đều sửa cùng vài vùng và xung đột liên tục. `ui/settings/*` là vùng "dùng chung có kiểm soát" (`MultiAgent-Workflow.md` mục 5.2): được sửa song song nhưng chỉ nên **thêm**.
- Trước G2d đã có hai ví dụ cho mẫu "khối tự đứng được": `SearchSettingsBlock` (M8d) tự giữ hộp thoại và có `dismiss()`, `buildGlassCustomRows` (GS) có ghi chú "tự chứa, dời được". G2d đi tiếp hướng này.
- `SettingsViewModel` (320 dòng, 7 phụ thuộc) và `SettingsUiState` đã đủ cho mọi hàng hiện có; `SearchSettingsViewModel` đã tách riêng. Không cần tách thêm trong G2d.
- Mọi Activity đã `screenOrientation="nosensor"` (RR), nên xoay máy không dựng lại Activity; dựng lại chỉ còn do đổi giao diện tối/sáng, cỡ chữ, ngôn ngữ hoặc tiến trình bị kill. Hộp thoại đang mở mất khi dựng lại là hành vi hiện có đã chấp nhận (`lessons/settings-and-data.md`, M8a Task 5).
- Bài học đã ghi: màn phụ nhận trạng thái khởi đầu qua **extra của intent**, chỉ chấp nhận giá trị hợp lệ, lạ thì về mặc định (`AppLibraryViewModel`, `lessons/settings-and-data.md` M8a Task 3); trạng thái hệ thống đổi ngoài app (launcher mặc định) thì làm mới ở `onResume`; gỡ `OnCheckedChangeListener` trước khi `isChecked` trong adapter.
- Dự án **không dùng Fragment** (chỉ có một dòng chú thích trong `HomePagerAdapter` nói rõ lý do tránh nó ở Home; không có màn nào dùng Fragment — đọc code).

### Các phương án đã xét

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. Một Activity, nhiều lớp `SettingsScreen` (khung + màn)** | `SettingsActivity` nhận `EXTRA_SCREEN`; mỗi màn là một lớp tự dựng hàng, tự xử bấm, tự giữ hộp thoại/launcher; trang chủ là một màn; vào màn con bằng `startActivity` cùng lớp | Dùng lại gần như nguyên adapter, hàng, ViewModel, layout, manifest. Mô hình giống `SearchSettingsBlock` đã chạy tốt. Màn mới = một file mới + một dòng đăng ký; không đụng manifest, `HomeActivity`, layout. Back/hệ thống Activity quen thuộc. Mỗi Activity là một instance nên trạng thái hộp thoại không lẫn giữa màn | Mỗi lần vào màn con tạo thêm một `SettingsViewModel` (nhiều flow `combine` có thể thừa với màn nhỏ; suy luận chưa kiểm chứng là nhẹ, xem Task 6 bước đo); khung vẫn là file chung nên đổi khung vẫn có thể xung đột, nhưng khung ổn định sau G2d | **Chọn** |
| B. Một Activity + một Fragment cho mỗi màn | `FragmentContainerView`, `FragmentManager` back stack, mỗi màn một `@AndroidEntryPoint` Fragment | Có back stack và vòng đời view riêng chuẩn của Android; launcher đăng ký theo Fragment | Kéo thêm hạ tầng chưa từng dùng trong dự án (Fragment, `FragmentManager`, hiệu ứng chuyển, tiêu đề toolbar theo Fragment, `viewLifecycleOwner` cho collector, inset từng Fragment); dựng lại Activity phải khôi phục back stack (bẫy mới, chưa kiểm chứng); `AppLibraryActivity` vẫn là Activity nên có hai kiểu điều hướng song song. Lợi ích không cần tới: không có trạng thái chia sẻ giữa các màn | Không |
| C. Mỗi màn một Activity (nhiều lớp `XxxSettingsActivity`) | Lớp cơ sở `BaseSettingsActivity` giữ toolbar/inset; mỗi màn con là Activity riêng | Cô lập tuyệt đối giữa màn; không cần registry | Mỗi màn mới phải sửa `AndroidManifest.xml` (file dùng chung), mỗi Activity có `@AndroidEntryPoint` + binding riêng; dễ nhân bản code khung (inset, adapter, collector, ép dựng lại). Hai nhánh cùng thêm màn đều sửa manifest ở cùng chỗ. Cùng lượng code phải di chuyển như A, nhưng lợi ích thêm không đáng | Không |
| D. Một Activity, một danh sách phẳng có nhóm gập/mở (giữ nguyên file, thêm `Header` mới) | Chỉ thêm header và sắp lại thứ tự | Ít việc nhất | Không phải "trang chủ + màn con" mà chủ dự án đã chọn (quyết định 1); 15+ màn nữa dồn vào cùng một file, đúng vấn đề đang cần tránh | Không |

### Vì sao chọn A

- **Phần phải di chuyển ít nhất mà vẫn tách được:** toàn bộ hộp thoại và launcher đã là hàm riêng, độc lập nhau trong file 591 dòng (đọc code); chuyển chúng sang lớp màn là cắt-dán theo nhóm hàng, không đổi logic. ViewModel, adapter, `SettingsRow` và layout giữ nguyên.
- **Giảm xung đột cho các nhánh sau (điều chủ dự án nhấn mạnh):** thêm một màn chỉ cần (1) tạo một file `XxxSettingsScreen.kt`, (2) thay một dòng giữ chỗ trong bảng đăng ký. Vì bảng trang chủ **khai báo sẵn mọi mục của bản nháp theo đúng thứ tự** (chuỗi chữ cũng khai sẵn trong khối chuỗi của G2d), nhánh sau không đụng danh sách trang chủ, không đụng `strings.xml` cho hàng trang chủ, không đụng manifest, không đụng `HomeActivity`. Xem mục "Công thức thêm màn/hàng".
- **Xoay/dựng lại:** vì màn được suy ra từ extra của intent (lạ/thiếu → trang chủ), Activity dựng lại do bất kỳ lý do nào đều dựng đúng màn cũ không cần `onSaveInstanceState` (đọc code, `AppLibraryViewModel` dùng cùng cách). `ActivityResult` launcher phải đăng ký theo thứ tự cố định mỗi lần `onCreate` (khóa đăng ký sinh theo thứ tự): vì màn xác định bởi extra nên thứ tự cố định (xem Review Focus 4).
- **Back:** mỗi màn con là một Activity trên cùng task nên nút Back/mũi tên toolbar (`finish()`) quay đúng về trang chủ; hành vi y hệt `AppLibraryActivity` hiện nay.

### Đánh đổi chấp nhận, khi nào xét lại

- Mở màn con mất thêm một lần tạo Activity (chuyển cảnh mặc định của hệ thống). Chấp nhận: cùng kiểu với mở "Ứng dụng đã ẩn" hiện nay.
- Mỗi Activity có `SettingsViewModel` riêng; trạng thái "đang đổi cỡ lưới" (`applyingGrid`) không chia sẻ giữa hai instance. An toàn nhờ `ChangeGridPresetUseCase`/`LayoutReconciler` tuần tự hóa bằng một `Mutex` (FX3, đọc tài liệu: `lessons/settings-and-data.md`); xem mục "Hành vi khó giữ".
- Khi một màn có trạng thái nặng hoặc cần sống qua nhiều màn thì xét lại: tách ViewModel theo màn (mẫu `SearchSettingsViewModel` đã có) hoặc chuyển sang Fragment (phương án B). Chưa cần trong G2d.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Dựng lại Cài đặt thành trang chủ + một màn con cho mỗi nhóm, theo nhóm và thứ tự bản nháp (Chung · Giao diện · Màn hình nền · Chuyển cảnh · Tùy chỉnh biểu tượng · Liên hệ với chúng tôi); chuyển cài đặt đang có vào; **không thêm tính năng mới** | Chủ dự án, 2026-11-12 (FD1) |
| 2 | **Giữ kiểu hiển thị hiện tại** (kiểu hàng/danh sách bo góc của `SettingsAdapter`). Không dựng nền kính tối, ảnh minh họa theme hay icon màu từng hàng; chờ thiết kế chính thức | Chủ dự án, 2026-11-12 (FD2) |
| 3 | Chỗ ở của các cài đặt hiện có: **Chung (1.2)** = cỡ lưới, hiện nhãn; **Liquid Glass (2.1)** = mức kính + khối kính tùy chỉnh (`buildGlassCustomRows`); **Hình nền (2.2)** = hàng ảnh nền; **Bộ biểu tượng (2.3)** = kiểu icon, màu tô (chỉ khi Tô màu), gói icon; **Cài đặt tìm kiếm (3.3)** = cả `SearchSettingsBlock`; **Ứng dụng ẩn (5.1)** = mở `AppLibraryActivity` kèm số app đang ẩn; **trang chủ** = hàng launcher mặc định (cùng hành vi, gồm dự phòng FX6) + chân trang phiên bản | Chủ dự án, 2026-11-12 (FD4) |
| 4 | `versionName` = `1.0.0`; chân trang đọc lúc chạy, không viết cứng | Chủ dự án, 2026-11-12 (FD9). Cách đọc (đề xuất của agent, Task 2): `BuildConfig.VERSION_NAME`, `buildConfig = true` đã bật ở `app/build.gradle.kts:51` |
| 5 | Kiến trúc: phương án A (một Activity khung + lớp `SettingsScreen` cho mỗi màn), màn chọn bằng extra | Agent đề xuất 2026-11-12; **chờ điều phối rà và chủ dự án duyệt cùng plan** |
| 6 | Mục chưa có màn: **hiện nhưng mờ (không bấm được)**, giá trị "Sắp có"; tự bật khi nhánh của nó đăng ký màn. Gồm cả hai nhóm Chuyển cảnh và Liên hệ và các mục 3.4, 6.1, nên trang chủ hiện đủ bố cục bản nháp | Chủ dự án, 2026-11-12 (trả lời Q1; thay đề xuất "ẩn" của agent) |
| 7 | `versionCode` giữ `1` | Agent giữ nguyên vì chủ dự án chỉ nêu `versionName` (FD9); báo nếu muốn đổi |

### Câu hỏi cho chủ dự án (đã trả lời 2026-11-12)

- **Q1 — Mục chưa có nội dung: ĐÃ CHỐT 2026-11-12 (chủ dự án): hiện mờ.** Mọi mục bản nháp chưa có màn hiện ở trang chủ với `SettingsRow.Value(enabled = false)` và giá trị "Sắp có" (chuỗi mới trong khối `settings_hub_`); hàng mờ không phản hồi chạm. Khi một nhánh đăng ký màn của mình thì mục tự bật, công thức "thêm màn" vẫn là một file cộng một dòng. (Đề xuất ban đầu của agent là ẩn; chủ dự án chọn hiện để thấy đủ bố cục.)
- **Q2 — Chữ của hàng launcher mặc định.** Đề bài ghi "Có/Không" nhưng code hiện dùng "Đang là mặc định" / "Chưa đặt" (`settings_default_yes/no`). **ĐÃ CHỐT (điều phối, 2026-11-12): giữ chuỗi hiện có** (đề bài "Có/Không" là nhầm) (cùng hành vi, không đổi chữ trong một mốc chỉ đổi cấu trúc). Nếu cần đúng "Có/Không" thì đổi hai chuỗi ở khối `settings_hub_`.
- **Q3 — Tên hàng "Ứng dụng đã ẩn" hay "Ứng dụng ẩn" (bản nháp).** **ĐÃ CHỐT (điều phối, 2026-11-12): giữ** **"Ứng dụng đã ẩn"** (`settings_hidden_apps`), khớp tiêu đề thanh công cụ của màn đó (`library_title`) và không phải sửa `AppLibraryActivity`.

## Ngoài phạm vi / ghi để sau

- Nền kính tối, thẻ theme có ảnh, icon màu từng hàng, mũi tên `>` bên phải hàng (quyết định 2). Hàng điều hướng dùng lại `SettingsRow.Value` (chữ giá trị bên phải là mô tả ngắn, rỗng nếu không có).
- Thẻ "Màn hình chính đang chọn" + nút "Đặt làm mặc định" của bản nháp: trang chủ chỉ có **hàng** launcher mặc định (quyết định 3).
- Mọi màn còn trống: 1.1 theme (TH), 1.3 ngôn ngữ (LG), 1.4 thời tiết, 1.5 vị trí (WX), 3.1 lướt màn hình (PT), 3.2 thư viện ứng dụng (LO), 3.4 huy hiệu (để sau), 4.1/4.2 chuyển cảnh (TR, AE), 5.2 đổi icon (CI), 5.3 đổi tên (RN), 6.1–6.3 liên hệ (AB). Bảng trang chủ khai sẵn chỗ và chuỗi; nội dung do nhánh của chúng làm.
- Hộp thoại đánh giá (6.1) là một hộp thoại trên trang chủ chứ không phải màn: nhánh AB quyết định thêm loại mục "hành động" vào bảng (ghi chú để AB; G2d chỉ khai mục kiểu màn và mục kiểu Activity ngoài).
- Tách `SettingsViewModel` theo màn, đổi `Header` đầu mỗi màn con sang dạng tiêu đề lớn (chờ thiết kế).
- Gom khung `SettingsActivity` + `AppLibraryActivity` về cùng một lớp cơ sở: không làm (không cần cho mục tiêu, tránh sửa `AppLibraryActivity`).

## Vùng file

Đường dẫn Kotlin tính từ `app/src/main/java/gambi/global/os26launcher/`, tài nguyên từ `app/src/main/`.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/settings/SettingsScreen.kt` (interface `SettingsScreen`, `SettingsScreenContext`, enum `SettingsScreenId`, bảng đăng ký `SettingsScreens`) | Tạo | Không |
| `ui/settings/SettingsHubScreen.kt` (màn trang chủ + bảng nhóm/mục `SettingsHubLayout`) | Tạo | Không |
| `ui/settings/DefaultLauncherAction.kt` (launcher mặc định + dự phòng FX6, chuyển từ Activity) | Tạo | Không |
| `ui/settings/GeneralSettingsScreen.kt`, `LiquidGlassSettingsScreen.kt`, `WallpaperSettingsScreen.kt`, `IconSetSettingsScreen.kt`, `SearchSettingsScreen.kt` | Tạo | Không |
| `ui/settings/LegacyAllSettingsScreen.kt` (tạm, Task 1 → Task 5) | Tạo rồi xóa (chờ duyệt) | Không |
| `ui/settings/SettingsActivity.kt` | Sửa (thành khung) | **Dùng chung** (`ui/settings/*`) |
| `ui/settings/SettingsAdapter.kt`, `SettingsRow.kt` | Sửa: thêm kiểu hàng `Footer`; điều kiện nhóm coi `Footer` là ngắt nhóm; hằng `ID_*` mới thêm cuối | **Dùng chung** (chỉ thêm) |
| `ui/settings/SearchSettingsBlock.kt` | Sửa nhỏ: bỏ `Header` đầu khối (trùng tên màn) | **Dùng chung** |
| `res/layout/activity_settings.xml` | Sửa nhỏ: đệm đầu danh sách | Không |
| `res/layout/item_settings_footer.xml` | Tạo | Không |
| `res/values/strings.xml` | Sửa: **một khối mới ở cuối**, tiền tố `settings_hub_` | **Dùng chung** (khối cuối) |
| `app/build.gradle.kts` | Sửa một dòng: `versionName = "1.0.0"` | **Dùng chung** |
| `AndroidManifest.xml`, `HomeActivity.kt`, `ui/library/*`, `ui/settings/SettingsViewModel.kt`, `SettingsUiState.kt`, `SettingsEvent.kt`, `SearchSettingsViewModel.kt` | **Không sửa** (mục tiêu; nếu cần sửa thì dừng và báo) | — |

Không đụng file độc quyền nào (`HomeActivity`, `core/glass/*`, v.v.); hiện không có nhánh nào khác chạy.

## Global Constraints

- Tầng `ui → domain ← data`: các lớp `SettingsScreen` chỉ ở `ui/settings`, không import `data`. `BuildConfig` do AGP sinh trong module app, được dùng ở `ui`.
- Mọi chữ hiển thị ở `res/values/strings.xml`; chuỗi mới nằm **một khối liền ở cuối file**, tiền tố `settings_hub_`. Chuỗi cũ giữ nguyên tên (không đổi tên, không xóa chuỗi còn dùng).
- File dùng chung chỉ **thêm** (workflow mục 5.2); thay đổi bắt buộc với code đang có (rút phần chuyển đi khỏi `SettingsActivity`) làm đúng trong Task chuyển nó.
- Mỗi màn: không giữ bản sao dữ liệu nghiệp vụ; hiển thị lấy từ `SettingsUiState` (nguồn thật) như `SettingsViewModel` đã quy định; hàng dựng lại từ trạng thái thật khi lỗi/hủy (`forceRebind`).
- Hộp thoại phải `dismiss()` ở `onDestroy` (gỡ listener trước), một hộp mỗi lúc (chống chạm đúp), như code hiện tại.
- `registerForActivityResult` chỉ gọi lúc dựng (không lười, không trong điều kiện theo trạng thái) và theo thứ tự cố định.
- Không đổi hành vi đang có: cùng chuỗi, cùng thứ tự hàng trong từng màn, cùng hộp thoại. Chỉ đổi *chỗ ở* và *cách điều hướng*.
- Không Room, không khóa DataStore, không test, không dependency mới. Commit message tiếng Anh `<type>: <imperative>` ≤ 72 ký tự, không dấu tiếng Việt, có dòng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Chỉ `git add` theo đường dẫn tường minh.
- Build `gradlew.bat assembleDebug` xanh mới báo xong Task.

## Công thức thêm màn/hàng (các nhánh FD-1/FD-2/FD-3 làm theo)

**Thêm một hàng vào màn có sẵn** (ví dụ GV thêm thanh trượt kính vào 2.1; HT thêm công tắc vào 3.3):
1. Sửa **đúng** lớp màn đó (`LiquidGlassSettingsScreen`, `SearchSettingsScreen`...): thêm hàng vào `rows(state)`, thêm nhánh xử lý vào `onValueClicked`/`onSwitchChanged`, thêm hộp thoại nếu cần.
2. Hàng cần trạng thái mới: thêm trường **cuối** `SettingsUiState` (không đổi trường cũ) và ghi vào `SettingsViewModel`; hoặc màn có ViewModel riêng (mẫu `SearchSettingsViewModel`).
3. Thêm `const val ID_...` **cuối** `SettingsRow.Companion`. Chuỗi mới vào khối của nhánh ở cuối `strings.xml`.
4. Không sửa `SettingsActivity`, trang chủ, manifest.

**Thêm một màn mới** (mục của bản nháp chưa có màn; ví dụ LG làm 1.3 Ngôn ngữ):
1. Tạo `LanguageSettingsScreen.kt` triển khai `SettingsScreen` (`titleRes`, `rows(state)`, `onValueClicked`, `onSwitchChanged`; tùy chọn `onResume`, `onEvent`, `observe`, `onDestroy`). Hộp thoại/launcher của màn nằm trong lớp đó.
2. Trong `SettingsScreens` (bảng đăng ký) **thay dòng giữ chỗ** `// 1.3 LANGUAGE (LG)` bằng một dòng `SettingsScreenId.LANGUAGE to ::LanguageSettingsScreen`. Mục sẽ **tự hiện** ở trang chủ vì `SettingsScreenId.LANGUAGE` đã nằm sẵn trong bảng `SettingsHubLayout` với chữ `R.string.settings_hub_language`.
3. Màn cần ViewModel riêng: tạo `@HiltViewModel`, lấy bằng `ViewModelProvider(context.activity)[Xxx::class.java]` trong constructor của màn.
4. Màn cần bộ chọn/ActivityResult: đăng ký trong `init` của lớp màn (không lười, đã đúng thứ tự vì màn xác định bởi extra).
5. Cần thêm mục nào *ngoài* bảng bản nháp: thêm một giá trị `SettingsScreenId` ở **cuối** enum + một dòng trong nhóm của `SettingsHubLayout` (một mục một dòng, để hai nhánh không dính dòng nhau).
6. Mỗi ô giữ chỗ trong bảng đăng ký cách nhau một dòng trống để hai nhánh thay hai ô khác nhau không xung đột khi rebase.

## Review Focus

1. **Hành vi cũ giữ nguyên sau khi chuyển** (mục "Hành vi khó giữ" bên dưới): mỗi dòng có bằng chứng kiểm tay hoặc đọc code.
2. **Launcher mặc định (B12/FX6):** bấm hàng trên trang chủ vẫn mở hộp thoại vai trò; hủy ngay (hộp không hiện) → mở màn hệ thống dự phòng, người dùng tự bấm Hủy thì ở lại; ROM không có role → thẳng màn hệ thống; lối tắt tìm kiếm ("cần đặt launcher mặc định") trong màn Tìm kiếm gọi **cùng** luồng đó.
3. **Khối kính tùy chỉnh:** chỉ hiện khi mức kính = Tùy chỉnh; đổi mức → hàng hiện/ẩn ngay; cảnh báo kính thật trên Live 4 (API 30 < 33) → Hủy/đóng thì công tắc về tắt.
4. **Launcher đăng ký đúng thứ tự khi dựng lại:** chọn ảnh nền rồi (nếu được) bật/tắt giao diện tối khi bộ chọn đang mở hoặc kill tiến trình → quay về vẫn nhận kết quả hoặc không crash.
5. **Mục chưa có màn hiện mờ đúng:** cả 6 nhóm bản nháp hiện ở trang chủ; mục chưa đăng ký là hàng mờ, giá trị "Sắp có", **chạm không có phản ứng**; mục đã có màn bật bình thường; khi thêm một dòng đăng ký giả vào bảng thử (chỉ khi review, không commit) mục tự bật.
6. **Không còn mã chết:** sau Task 5 `SettingsActivity` không còn hộp thoại/hàm dựng hàng của màn cụ thể.
7. **Không sửa ngoài vùng:** `AndroidManifest.xml`, `HomeActivity.kt`, `ui/library/*`, ViewModel, domain, data không có trong diff.
8. **Số phiên bản thật:** chân trang hiện `1.0.0` đọc từ `BuildConfig`; đổi `versionName` thì chân trang đổi, không có chuỗi `1.0.0` viết cứng trong code/`strings.xml`.

## Hành vi khó giữ sau khi chuyển và cách giữ

| # | Hành vi hiện có (nguồn đọc code) | Rủi ro khi chia màn | Cách giữ |
|---|---|---|---|
| H1 | Dựng lại hàng từ trạng thái thật khi ghi lỗi/hủy: `render(state, forceRebind = true)` gọi từ `SettingsEvent.Message`, từ hộp thoại cảnh báo kính, từ lỗi của `SearchSettingsViewModel` | Màn không còn truy cập `lastState`/`adapter` của Activity | Khung giữ `lastState` + adapter và đưa cho màn một `context.rerender(force)`; màn chỉ gọi hàm đó |
| H2 | Khối kính tùy chỉnh chỉ có khi mức = Tùy chỉnh (`buildGlassCustomRows` trả rỗng nếu khác) và nằm CUỐI danh sách vì adapter nhóm theo `Header` | Ghi chú cũ nói khối phải ở cuối để không chèn giữa nhóm Giao diện; chia màn thì lý do này hết | `LiquidGlassSettingsScreen.rows(state)` = hàng mức kính + (khối tùy chỉnh nếu CUSTOM). Hàm `buildGlassCustomRows`, `glassElementOf`, `onGlassCustomSwitched`, `showGlassWarnDialog`, `showGlassDialog` chuyển nguyên văn. Cập nhật chú thích cũ |
| H3 | Trạng thái quyền Danh bạ (`contactsGranted`) đọc lại ở `onResume`; `SearchSettingsBlock.rows(prefs, isDefaultLauncher, contactsGranted)` | Activity không còn biết quyền; `onResume` khung phải báo cho màn | `SettingsScreen.onResume()` (mặc định rỗng); `SearchSettingsScreen` đọc quyền ở đó, khung gọi `render` lại sau `onResume`. `isDefaultLauncher` vẫn lấy từ `SettingsUiState` của khung |
| H4 | Trạng thái khối Tìm kiếm có ViewModel riêng và dòng lỗi riêng (`searchSettings.uiState/failures`) được thu cùng `repeatOnLifecycle(STARTED)` | Khung không nên biết màn nào có ViewModel nào | `SettingsScreen.observe()` là `suspend` mặc định rỗng; khung chạy `launch { screen.observe() }` trong cùng `repeatOnLifecycle`. `SearchSettingsScreen.observe` thu hai flow đó và gọi `rerender` |
| H5 | Hai `ActivityResult` launcher (`pickImage`, `requestDefault`) đăng ký ở Activity; `roleRequestAt` | Đăng ký trong màn đòi hỏi màn được dựng trong `onCreate`, theo thứ tự cố định | Khung dựng màn trong `onCreate` trước khi vào `STARTED`; launcher đăng ký trong `init` của màn. `DefaultLauncherAction` (dùng ở trang chủ **và** màn Tìm kiếm) được khung dựng một lần và đưa qua `SettingsScreenContext` để không có hai launcher cùng mục đích. Nếu Activity dựng lại giữa chừng, `roleRequestAt = 0` như hiện nay (đã được xử lý "0 = không biết") |
| H6 | Hộp thoại dùng chung một chỗ giữ (`iconStyleDialog` cho kiểu, màu tô, gói icon; `gridDialog` cho chọn rồi xác nhận) | Chia nhỏ mà chia sai chỗ giữ sẽ cho hai hộp chồng nhau hoặc rò cửa sổ | Mỗi màn giữ các biến hộp thoại **của chính nó**, giữ đúng quy ước "hộp đang mở thì không mở hộp mới" và `onDestroy` dismiss (gỡ listener trước). `IconSetSettingsScreen` giữ một chỗ cho cả ba hộp như cũ |
| H7 | `SettingsEvent` (Message / AskWallpaperTarget / WallpaperApplied) đến từ ViewModel và Activity xử lý | Sự kiện thuộc màn nào | Khung xử lý `Message` (Toast + dựng lại); các sự kiện còn lại chuyển qua `SettingsScreen.onEvent(event): Boolean`; `WallpaperSettingsScreen` xử lý `AskWallpaperTarget`/`WallpaperApplied`. Sự kiện đi theo channel của chính ViewModel của Activity đó nên không lẫn màn khác |
| H8 | `applyingGrid` (tiến trình + khóa hàng cỡ lưới) nằm trong ViewModel của Activity | Thoát màn Chung giữa lúc đổi lưới rồi vào lại: ViewModel mới có `applyingGrid = false`, người dùng có thể bấm đổi lần nữa trong lúc lần trước còn chạy | `onGridPresetConfirmed` chạy `NonCancellable` như cũ; hai lần đổi bị tuần tự hóa bởi `Mutex` dùng chung (FX3, đọc tài liệu), và use case chịu được "đã đúng cỡ" nên lần sau là no-op. Kiểm tay ở Task 3 (cố tình thoát và vào lại giữa lúc đổi). Chưa kiểm chứng trên máy trước G2d |
| H9 | Số app ẩn trên hàng "Ứng dụng đã ẩn" và trạng thái launcher mặc định phải cập nhật khi quay lại trang chủ | Trang chủ không còn là Activity bị tạo mới mỗi lần mở | `onResume` của khung vẫn gọi `refreshDefaultLauncher()`; số app ẩn vốn lấy từ flow Room (`GetHiddenAppsUseCase`) nên tự cập nhật khi quay về |
| H10 | Cuộn: màn cũ một danh sách dài với tiêu đề nhóm; màn con ngắn | Hàng đầu sát thanh công cụ vì không còn `Header` đầu | `activity_settings.xml` thêm đệm đầu danh sách (Task 1); `SearchSettingsBlock` bỏ `Header` đầu |

## Bảng nhóm trên trang chủ (khai sẵn theo bản nháp)

Mục **in đậm** có màn/đích trong G2d; mục còn lại khai sẵn, hiện **mờ với giá trị "Sắp có"** và tự bật khi đăng ký màn. Mọi nhóm của bản nháp đều hiện.

| Nhóm (chữ) | Mục (theo thứ tự bản nháp) | Trạng thái sau G2d |
|---|---|---|
| (không tiêu đề) | **Launcher mặc định** (hàng giá trị, bấm = luồng đặt mặc định) | Hiện |
| Chung | 1.1 Màn hình chính mặc định · **1.2 Chung** · 1.3 Ngôn ngữ · 1.4 Thời tiết · 1.5 Vị trí | Chung bật; 1.1, 1.3, 1.4, 1.5 mờ "Sắp có" |
| Giao diện | **2.1 Liquid Glass** · **2.2 Hình nền** · **2.3 Bộ biểu tượng** | Hiện đủ 3 (bật khi màn được chuyển, Task 3 và 4; trước đó mờ) |
| Màn hình nền | 3.1 Lướt màn hình · 3.2 Thư viện ứng dụng · **3.3 Cài đặt tìm kiếm** · 3.4 Huy hiệu thông báo | Cài đặt tìm kiếm bật; 3.1, 3.2, 3.4 mờ "Sắp có" |
| Chuyển cảnh | 4.1 Hiệu ứng chuyển cảnh · 4.2 Hiệu ứng ứng dụng | Hiện, cả hai mục mờ "Sắp có" |
| Tùy chỉnh biểu tượng | **5.1 Ứng dụng đã ẩn** (mở `AppLibraryActivity`, giá trị = số app ẩn) · 5.2 Thay đổi biểu tượng · 5.3 Thay đổi tên | Ứng dụng đã ẩn bật; 5.2, 5.3 mờ "Sắp có" |
| Liên hệ với chúng tôi | 6.1 Đánh giá · 6.2 Tải ứng dụng khác · 6.3 Chính sách bảo mật | Hiện, cả ba mục mờ "Sắp có" |
| (chân trang) | "Phiên bản <versionName>" | Hiện |

Ghi chú: mục 3.4 và 6.1 là mục "để sau"/hộp thoại: khai sẵn, luôn mờ "Sắp có" trong G2d (6.1 do nhánh AB quyết định cách bật). Tên nhóm "Màn hình nền" (bản nháp) thực chất chứa cài đặt trang Home; giữ nguyên chữ của bản nháp.

---

### Task 1: Khung `SettingsScreen` + chuyển nguyên trạng vào một màn tạm (không đổi hành vi) [qua review điều phối 2026-11-12]
**Bắt buộc kiểm tay:** không
**Files:** tạo `ui/settings/SettingsScreen.kt`, `ui/settings/DefaultLauncherAction.kt`, `ui/settings/LegacyAllSettingsScreen.kt`; sửa `ui/settings/SettingsActivity.kt`, `res/layout/activity_settings.xml` (đệm đầu danh sách)
**Interfaces:**
- Consumes: `SettingsViewModel`, `SettingsUiState`, `SettingsRow`, `SettingsAdapter`, `SearchSettingsViewModel`, `SearchSettingsBlock` (nguyên trạng).
- Produces: `SettingsScreen` (`titleRes`; `rows(state)`; `onValueClicked(row)`; `onSwitchChanged(row, checked)`; mặc định rỗng: `onResume()`, `onEvent(event): Boolean`, `suspend observe()`, `onDestroy()`); `SettingsScreenContext` (`activity`, `viewModel`, `rerender(force)`, `open(id)`, `defaultLauncher`); `SettingsScreenId` (enum; Task 1 chỉ có `HUB`, `LEGACY`); `SettingsScreens.create(id, context)` (đăng ký); `SettingsActivity.EXTRA_SCREEN` + `SettingsScreenId.fromExtra(String?)` (lạ/thiếu → màn mặc định); `DefaultLauncherAction` (`request()` chứa luồng `onDefaultLauncherClicked`/`openDefaultLauncherFallback`/`roleRequestAt`/launcher).
- [x] **Step 1:** `DefaultLauncherAction`: chuyển nguyên văn luồng launcher mặc định (kể cả hằng `ROLE_DIALOG_NEVER_SHOWN_MS = 352L` và chú thích B12/FX6). Launcher đăng ký trong `init`.
- [x] **Step 2:** `LegacyAllSettingsScreen`: chuyển **toàn bộ** phần còn lại của `SettingsActivity` (dựng hàng, 5 hộp thoại, `pickImage`, xử lý bấm, sự kiện ảnh nền, khối Tìm kiếm + quyền Danh bạ) thành một lớp triển khai `SettingsScreen`, không đổi logic. `SettingsActivity` thành khung: toolbar, inset, `RecyclerView`, `adapter`, thu `viewModel.uiState/events` trong `repeatOnLifecycle(STARTED)`, `lastState`, `rerender`, chuyển `onResume/onDestroy/events/observe` cho màn. Màn mặc định (extra thiếu) tạm là `LEGACY`.
- [x] **Step 3:** Build `gradlew.bat assembleDebug` xanh; soát `git diff --stat` chỉ chạm các file trong vùng file.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — hồi quy: mở Cài đặt từ chế độ sửa Home; danh sách phẳng **y hệt trước** (cùng hàng, cùng thứ tự). (a) đổi mức kính sang Tùy chỉnh, công tắc hiện/ẩn đúng; (b) đổi cỡ lưới có hộp xác nhận; (c) chọn ảnh nền rồi hộp ba đích; (d) kiểu/màu/gói icon; (e) bật/tắt công tắc tìm kiếm, tắt "Gợi ý" có hộp xác nhận và Hủy thì công tắc về bật; (f) hàng launcher mặc định; (g) Back về Home.

### Task 2: Trang chủ Cài đặt, bảng nhóm, chân trang phiên bản, `versionName` 1.0.0 [qua review điều phối 2026-11-12]
**Bắt buộc kiểm tay:** không
**Files:** tạo `ui/settings/SettingsHubScreen.kt` (màn trang chủ + `SettingsHubLayout`), `res/layout/item_settings_footer.xml`; sửa `SettingsScreen.kt` (mở rộng enum `SettingsScreenId` với **toàn bộ** mục bản nháp; thêm bảng đăng ký có ô giữ chỗ), `SettingsAdapter.kt`, `SettingsRow.kt` (kiểu `Footer`), `SettingsActivity.kt` (màn mặc định = `HUB`; `open(id)` = `startActivity` + extra), `strings.xml` (khối `settings_hub_`: tên mục + tiêu đề nhóm + "Phiên bản %1$s"), `app/build.gradle.kts` (`versionName = "1.0.0"`)
**Interfaces:**
- Consumes: Task 1; `SettingsUiState.isDefaultLauncher/canRequestDefault/hiddenCount`; `AppLibraryActivity`.
- Produces: `SettingsRow.Footer(id, text)`; `SettingsHubLayout.groups` (nhóm → mục; mục = `Screen(id)` hoặc `Activity(class)`); các chuỗi hàng trang chủ cho mọi mục bản nháp (nhánh sau không thêm chuỗi trang chủ).
- [x] **Step 1:** `SettingsRow.Footer` + `item_settings_footer.xml` (chữ nhỏ, màu `?attr/colorOnSurfaceVariant`, căn giữa) + `SettingsAdapter` coi `Footer` là điểm ngắt nhóm khi tính `first/last`; hằng `ID_*` mới ở cuối companion.
- [x] **Step 2:** `SettingsHubScreen.rows(state)`: hàng launcher mặc định (giá trị "Đang là mặc định"/"Chưa đặt", `enabled = canRequestDefault`, bấm = `defaultLauncher.request()`), rồi các nhóm từ `SettingsHubLayout` (mọi mục bản nháp đều hiện; mục chưa đăng ký là `SettingsRow.Value(enabled = false, value = "Sắp có")`, bấm không làm gì, không lọc, không ẩn nhóm; mục đã đăng ký bật tự động), rồi `Footer(getString(settings_hub_version, BuildConfig.VERSION_NAME))`. Mục "Ứng dụng đã ẩn" là kiểu `Activity` (giá trị = `hiddenCount`). Mục `LEGACY` tạm hiện dưới nhóm Giao diện với chữ "Tất cả cài đặt (tạm)" để các cài đặt chưa chuyển vẫn vào được; **bỏ ở Task 5**. Các hàng launcher mặc định và Ứng dụng đã ẩn **bị bỏ khỏi `LegacyAllSettingsScreen`** trong Task này.
- [x] **Step 3:** `versionName = "1.0.0"` (`versionCode` giữ 1). Kiểm không còn chuỗi `1.0.0` viết cứng ngoài `build.gradle.kts`.
- [x] **Step 4:** Build xanh.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — (a) mở Cài đặt: thấy trang chủ gồm hàng launcher mặc định, đủ 6 nhóm của bản nháp (kể cả Chuyển cảnh và Liên hệ), các mục chưa có màn mờ "Sắp có" và chạm không phản ứng, nhóm Giao diện có thêm hàng tạm "Tất cả cài đặt", nhóm Tùy chỉnh biểu tượng có "Ứng dụng đã ẩn" kèm số, chân trang "Phiên bản 1.0.0"; (b) bấm "Tất cả cài đặt" → danh sách cũ (trừ hai hàng đã chuyển), Back về trang chủ; (c) bấm "Ứng dụng đã ẩn" → màn cũ, bỏ ẩn một app, Back: số trên trang chủ giảm ngay; (d) hàng launcher mặc định: làm theo kịch bản B12/FX6 (nếu máy đang là mặc định thì hàng mờ không bấm được); (e) về Home bằng nút Home rồi mở lại: trang chủ.

### Task 3: Màn Chung (1.2) và màn Liquid Glass (2.1) [qua review điều phối 2026-11-12]
**Bắt buộc kiểm tay:** không (chuyển hành vi cũ, không có cử chỉ mới; kính chỉ đổi chỗ công tắc, Live 4 đủ vì Oppo chỉ cần khi đo kính)
**Files:** tạo `GeneralSettingsScreen.kt`, `LiquidGlassSettingsScreen.kt`; sửa `SettingsScreen.kt` (hai ô đăng ký), `LegacyAllSettingsScreen.kt` (rút phần đã chuyển)
**Interfaces:**
- Consumes: Task 2 (mục `GENERAL`, `GLASS` đã khai sẵn trong bảng); `viewModel.onShowLabelsChanged`, `onGridPresetConfirmed`, `onGlassOverrideChosen`, `onGlassCustomChosen`, `onGlassCustomToggled`.
- Produces: `GeneralSettingsScreen` (cỡ lưới + hiện nhãn, hộp chọn và hộp xác nhận lưới); `LiquidGlassSettingsScreen` (mức kính + khối tùy chỉnh + hộp mức + hộp cảnh báo; cập nhật chú thích cũ của `buildGlassCustomRows`).
- [x] **Step 1:** `GeneralSettingsScreen`: chuyển `showGridPresetDialog`, `showGridConfirmDialog`, `GridPreset.shortLabel`, công tắc nhãn. Tiêu đề toolbar = "Chung".
- [x] **Step 2:** `LiquidGlassSettingsScreen`: chuyển `showGlassDialog`, `showGlassWarnDialog`, `onGlassCustomSwitched`, `glassElementOf`, `buildGlassCustomRows`, `shortLabelRes`.
- [x] **Step 3:** Mỗi màn dismiss hộp thoại ở `onDestroy`; build xanh.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — hồi quy (đọc tài liệu: M8a Task 4/6, GS): (a) Chung: đổi cỡ lưới 4×6 → 4×5, hộp xác nhận, Hủy thì không đổi, Áp dụng thì tiến trình rồi Home xếp lại đúng; trả về 4×6; tắt/bật "Hiện nhãn" → nhãn Home đổi sau khi quay về; (b) trong lúc đang đổi lưới bấm Back rồi vào lại Chung và quan sát hàng cỡ lưới (đối chiếu H8: không ra lỗi/icon mất; ghi lại hành vi thật); (c) Liquid Glass: chọn từng mức; chọn Tùy chỉnh → khối công tắc hiện cuối màn (kể cả khối "menu nhấn giữ"); bật một công tắc trên Live 4 → hộp cảnh báo; Hủy/chạm ngoài → công tắc về tắt; "Vẫn bật" → bật; đổi lại mức khác → khối biến mất; về Home thấy kính theo mức (Home tự tạo lại ở `onResume`).

### Task 4: Màn Hình nền (2.2) và màn Bộ biểu tượng (2.3) [qua review điều phối 2026-11-12]
**Bắt buộc kiểm tay:** không
**Files:** tạo `WallpaperSettingsScreen.kt`, `IconSetSettingsScreen.kt`; sửa `SettingsScreen.kt` (hai ô đăng ký), `LegacyAllSettingsScreen.kt` (rút phần đã chuyển)
**Interfaces:**
- Consumes: `viewModel.onImagePicked`, `onWallpaperTargetChosen`, `SettingsEvent.AskWallpaperTarget/WallpaperApplied`, `onIconStyleChosen`, `onIconTintChosen`, `onIconPackChosen`, `installedIconPacks()`.
- Produces: `WallpaperSettingsScreen` (hàng ảnh nền + `pickImage` đăng ký trong `init` + hộp ba đích + `onEvent`); `IconSetSettingsScreen` (kiểu, màu tô chỉ khi `TINTED`, gói icon, một chỗ giữ hộp thoại cho cả ba).
- [x] **Step 1:** `WallpaperSettingsScreen`: chuyển `pickImage`, `showWallpaperDialog`, `WallpaperTarget.labelRes`, nhận `AskWallpaperTarget`/`WallpaperApplied` qua `onEvent` (trả `true` khi đã xử lý). Hàng hiển thị 3 trạng thái giá trị như cũ (Đã có ảnh/Mặc định/Chưa chọn).
- [x] **Step 2:** `IconSetSettingsScreen`: chuyển ba hộp thoại và hai hàm `labelRes`; hàng màu tô chỉ khi `iconStyle == TINTED`; hộp gói icon nạp danh sách nền trong `lifecycleScope` với kiểm `isFinishing/isDestroyed`.
- [x] **Step 3:** Build xanh.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — hồi quy (M8a Task 5, G2b, IP): (a) Hình nền: chọn ảnh → hộp "đặt cho đâu", chọn Màn hình chính → Toast xác nhận, Home đổi nền sau khi quay về; chọn ảnh rồi "Không áp dụng"; hủy bộ chọn không đổi gì; (b) Bộ biểu tượng: đổi kiểu Mặc định/Tô màu/3D; hàng "Màu tô" chỉ hiện ở Tô màu; đổi màu; Home đổi icon; (c) chọn gói icon khi có/không có gói cài (hộp hướng dẫn khi trống); (d) chạm đúp một hàng không mở hai hộp.

### Task 5: Màn Cài đặt tìm kiếm (3.3), gỡ màn tạm, dọn [qua review điều phối 2026-11-12]
**Bắt buộc kiểm tay:** không
**Files:** tạo `SearchSettingsScreen.kt`; sửa `SettingsScreen.kt` (ô đăng ký `SEARCH`, bỏ `LEGACY`), `SettingsHubScreen.kt` (bỏ hàng tạm), `SearchSettingsBlock.kt` (bỏ `Header` đầu), `SettingsActivity.kt` (kiểm không còn mã chết); **xóa `LegacyAllSettingsScreen.kt` (đã duyệt sẵn 2026-11-12)**
**Interfaces:**
- Consumes: `SearchSettingsBlock` (rows/handlesValue/handlesSwitch/onValueClicked/onSwitch/dismiss), `SearchSettingsViewModel`, `DefaultLauncherAction`.
- Produces: `SearchSettingsScreen` (lấy `SearchSettingsViewModel` qua `ViewModelProvider(activity)`, giữ `searchPrefs`, `contactsGranted`, `searchBlock`; `observe()` thu `uiState`/`failures`; `onResume` đọc quyền Danh bạ; mở Cài đặt ứng dụng cấp quyền `openContactsPermissionSettings`).
- [x] **Step 1:** `SearchSettingsScreen` theo bảng H3, H4; `ID_SEARCH_SHORTCUTS_HINT` gọi `defaultLauncher.request()` (cùng luồng với trang chủ).
- [x] **Step 2:** Bỏ mục `LEGACY` khỏi enum/bảng đăng ký/trang chủ. `LegacyAllSettingsScreen.kt` lúc này rỗng/không còn dùng: **xóa file** (chủ dự án đã duyệt sẵn 2026-11-12 vì nhánh vừa tạo vừa xóa file này; ghi tên file + lý do vào Nhật ký và báo cáo).
- [x] **Step 3:** `grep` soát `SettingsActivity.kt` không còn hộp thoại/hàm dựng hàng/`registerForActivityResult` của màn cụ thể (Review Focus 6); soát không còn chuỗi/ID không dùng do chuyển (không xóa chuỗi cũ còn dùng). Bốn chuỗi nhóm cũ hết dùng (`settings_group_general`, `settings_group_home`, `settings_group_appearance`, `settings_search_group`) do nhánh này làm chết nên xóa luôn ở Task 6 (điều phối yêu cầu 2026-11-12, grep xác nhận 0 tham chiếu).
- [x] **Step 4:** Build xanh.
- [ ] **Step 5: Kịch bản kiểm (Live 4)** — hồi quy (M8d Task 6, G3a): (a) mọi công tắc của khối Tìm kiếm vẫn lưu và tác dụng đúng ở lớp tìm kiếm; (b) tắt "Gợi ý và app gần đây": hộp xác nhận, Hủy → công tắc về bật; xác nhận → lịch sử xóa; (c) bật "Lối tắt" khi chưa là launcher mặc định (nếu máy hiện không là mặc định) → hàng gợi ý "cần đặt launcher mặc định" → bấm = luồng launcher mặc định giống trang chủ; (d) bật "Tìm trong danh bạ" khi chưa có quyền → hàng gợi ý → mở Cài đặt ứng dụng; cấp quyền rồi quay lại: hàng gợi ý tự biến mất (đọc lại ở `onResume`); (e) "Xóa lịch sử mở app" có hộp xác nhận.

### Task 6: Đóng nhánh — hồi quy toàn bộ, đo mở màn, knowledgement, rà Review Focus
**Bắt buộc kiểm tay:** không
**Files:** tạo `docs/knowledgements/G2d-settings-hub.md`; cập nhật plan (Nhật ký, Review Focus, kết quả kiểm, đề xuất cập nhật docs chung)
- [x] **Step 1:** Rà từng dòng Review Focus và điền bảng bằng chứng; ca nào chỉ được code bảo vệ mà chưa ai kiểm tay thì ghi rõ.
- [x] **Step 2 (chỉ quan sát, điều phối chạy, không có đồng hồ):** Đo thủ công trên Live 4 (bản debug, ghi rõ build): thời gian từ lúc bấm "Liquid Glass" tới khi danh sách hiện, so với trước G2d (mở Cài đặt cũ từ Home). Mục đích: xác nhận suy luận "tạo thêm `SettingsViewModel` mỗi màn không đáng kể". Nếu chậm rõ rệt (nhìn thấy tiến trình `loading` nhấp nháy lâu) thì ghi lại và đề xuất tách trạng thái, không tự sửa ngoài plan.
- [x] **Step 3:** Knowledgement theo mẫu mục 8 `MultiAgent-Workflow.md` (kèm sơ đồ Mermaid luồng khung → màn; công thức thêm màn/hàng; bài học). Điền "Đề xuất cập nhật tài liệu chung".
- [ ] **Step 4:** Báo điều phối: rebase lên `main` (agent không tự merge `origin/main`), build xanh, chuyển PR sang Ready for review sau review toàn nhánh.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: Vsmart Live 4 (bản debug, cài từ worktree, do chủ dự án/điều phối cài). Nhánh@commit ghi khi cài. Điều phối đã chạy một phần trên Live 4 (kết quả ở "Nhật ký" và "Bằng chứng Review Focus"); bài kiểm tay của chủ dự án tập trung vào **phần chưa ai chạy**. Dữ liệu cần chuẩn bị: một ảnh trong thư viện; vài app đã cài để thấy đổi kiểu/màu icon; (tùy chọn) một gói icon.

**Phần A — làm trên Live 4 (Os26 đang là launcher mặc định):**
1. Từ Home vào chế độ sửa, bấm "Cài đặt": thấy hub, "Phiên bản 1.0.0" ở cuối, mục chưa có màn mờ "Sắp có".
2. **Hình nền:** bấm hàng, chọn một ảnh khác. Hộp "đặt cho đâu" hiện: chọn "Màn hình chính" thì Toast xác nhận; Back về hub rồi về Home, nền Home đã đổi. Làm lại, chọn ảnh khác và bấm "Không áp dụng": nền trong app đổi, wallpaper hệ thống không đổi. Làm lại một lần nữa và bấm Back **ngay khi vừa chọn ảnh** (khi đang nhập) rồi vào lại: ghi lại ảnh nền cuối cùng là cái nào (đối chiếu mục 6 knowledgement, chưa kiểm chứng).
3. **Bộ biểu tượng:** đổi kiểu sang Tô màu: hàng "Màu tô" xuất hiện; chọn một màu; Back về Home: icon đổi màu. Đổi sang 3D rồi về Mặc định: Home đổi tương ứng, hàng "Màu tô" biến mất khỏi màn.
4. **Chung:** tắt "Hiện nhãn ứng dụng": Back về Home, tên dưới icon biến mất; bật lại thì hiện. Đổi cỡ lưới 4×6 → 4×5 (Áp dụng), kiểm Home xếp lại đúng, rồi trả về 4×6.
5. **Liquid Glass:** chọn mức khác (Tự động, Ép thấp, Ép cao): Home theo mức. Chọn Tùy chỉnh: khối công tắc hiện, đổi sang mức khác thì khối biến mất.
6. **Cài đặt tìm kiếm:** bấm "Xóa lịch sử mở app" (hộp xác nhận, xác nhận xong gợi ý "app gần đây" ở lớp tìm kiếm trống).
7. **Ứng dụng đã ẩn:** ẩn một app từ Home, mở hub: số tăng; mở màn, bỏ ẩn, Back: số giảm.
8. Back từ mọi màn con về hub, Back từ hub về Home; không có Activity thừa chồng lên.

**Phần B — cần máy mà Os26 KHÔNG phải launcher mặc định** (đặt launcher khác làm mặc định trước, hoặc máy khác):
9. Hub: hàng "Launcher mặc định" hiện "Chưa đặt" và bấm được. Bấm: hộp thoại chọn launcher của hệ thống hiện; chọn Os26 thì hàng đổi thành "Đang là mặc định" và mờ.
10. Lặp lại, nhưng bấm Hủy thật (đừng chọn): ở lại màn Cài đặt. Nếu máy không hiện hộp thoại (hủy ngay lập tức) thì mở màn "Ứng dụng màn hình chính" của hệ thống (dự phòng FX6).
11. Cài đặt tìm kiếm: bật "Lối tắt ứng dụng" thì có hàng "Lối tắt cần đặt làm launcher mặc định"; bấm vào chạy **cùng** luồng ở mục 9.

**Phần C — hồi quy:** lớp tìm kiếm (các công tắc có tác dụng), widget thời tiết (không đụng), kéo-thả ở Home (không đụng).

**Đã được điều phối chạy (không cần làm lại):** hub 6 nhóm và "Sắp có", chạm mục mờ, Back về hub, màn Chung/Liquid Glass (kể cả cảnh báo API < 33), hộp thoại kiểu icon và gói icon, bộ chọn ảnh mở và Back, hộp xác nhận tìm kiếm, quyền Danh bạ, H8 trên Live 4, không crash.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chủ dự án kiểm tay Phần A, B: **đạt, không phát hiện lỗi** (2026-11-12, `feature/g2d-settings-hub`, bản trước khi đổi tên mục "Chung"). Điều phối đã chạy phần còn lại trên Live 4 (Nhật ký, Task 3 và 5).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | — | Viết plan | — | Chờ điều phối rà |
| 2026-11-12 | — | Điều phối rà, chủ dự án duyệt plan; cập nhật Q1 (hiện mờ "Sắp có"), Q2, Q3, duyệt sẵn xóa file tạm | — | Plan duyệt 2026-11-12 |
| 2026-11-12 | 1 | Code Task 1 (Step 1-3): khung + `DefaultLauncherAction` + `LegacyAllSettingsScreen`; `gradlew.bat assembleDebug` xanh. Lệch plan: enum chỉ có `LEGACY` (`HUB` thêm ở Task 2); chưa thêm đệm đầu danh sách vào `activity_settings.xml` (màn tạm vẫn có Header đầu, để giao diện y hệt; làm khi màn đầu tiên không có Header). Step 4 (kịch bản kiểm) chờ điều phối | 4185f58 | Chờ review Task 1, vòng 0/5 | Qua review điều phối 2026-11-12 (vòng 1/5), chấp nhận 3 điểm lệch.
| 2026-11-12 | 2 | Code Task 2 (Step 1-4): `SettingsHubScreen` + `SettingsHubLayout` (6 nhóm theo bản nháp, mục chưa có màn mờ "Sắp có" và không bấm được), `SettingsRow.Footer`, chân trang `BuildConfig.VERSION_NAME`, `versionName = "1.0.0"`, hàng launcher mặc định và Ứng dụng đã ẩn chuyển từ màn tạm sang trang chủ; hàng tạm "Tất cả cài đặt (tạm)" ở nhóm Giao diện; `gradlew.bat assembleDebug` xanh. Lệch plan: bỏ luôn Header "Chung" của màn tạm (sẽ rỗng sau khi chuyển hai hàng). Step 5 (kịch bản kiểm) chờ điều phối | 52606e4 | Chờ review Task 2, vòng 0/5 | Qua review điều phối 2026-11-12 (vòng 1/5).
| 2026-11-12 | 3 | Code Task 3 (Step 1-3): `GeneralSettingsScreen`, `LiquidGlassSettingsScreen` (đăng ký GENERAL, GLASS; hai mục trang chủ tự bật), rút phần đã chuyển khỏi màn tạm, thêm đệm đầu danh sách 8dp ở `activity_settings.xml`; `gradlew.bat assembleDebug` xanh. Step 4 (kịch bản kiểm, gồm kiểm H8) do điều phối/chủ dự án chạy | 2471431 | Chờ review Task 3, vòng 0/5 | Qua review điều phối 2026-11-12 (vòng 1/5).
| 2026-11-12 | 4 | Code Task 4 (Step 1-3): `WallpaperSettingsScreen`, `IconSetSettingsScreen` (đăng ký WALLPAPER, ICON_SET; hai mục trang chủ tự bật), rút phần đã chuyển khỏi màn tạm (nay chỉ còn khối Tìm kiếm); `gradlew.bat assembleDebug` xanh. Step 4 (kịch bản kiểm) chờ điều phối | db9304d | Chờ review Task 4, vòng 0/5 | Qua review điều phối 2026-11-12 (vòng 1/5).
| 2026-11-12 | 3 (máy) | Kết quả điều phối chạy trên Live 4 (bản debug, APK Task 3): trang chủ đủ 6 nhóm, "Sắp có" ở mục chưa có màn; chạm mục mờ không mở gì (task stack vẫn 1 SettingsActivity); chân trang "Phiên bản 1.0.0"; màn tạm mở được, Back về trang chủ; "Ứng dụng đã ẩn" hiện số 1, mở AppLibraryActivity, Back về trang chủ; Chung mở với tiêu đề "Chung", hộp lưới hiện 4×6 được chọn, Hủy không đổi; Liquid Glass mở có khối tùy chỉnh, tắt rồi bật Dock hiện cảnh báo API < 33, Hủy giữ công tắc tắt, "Vẫn bật" bật; không crash. Chưa chạy: Áp dụng đổi lưới và kiểm H8 (rời màn khi đang đổi lưới rồi vào lại), làm cùng bản Task 5 kèm khôi phục dữ liệu | — | Ghi nhận từ điều phối; đưa vào Bằng chứng Review Focus 1, 2 (một phần), 3, 5, 8 |
| 2026-11-12 | 5 | Code Task 5 (Step 1-4): `SearchSettingsScreen`; bỏ Header đầu của `SearchSettingsBlock`; bỏ `LEGACY` khỏi enum, bảng đăng ký, trang chủ và chuỗi `settings_hub_legacy`; xóa `LegacyAllSettingsScreen.kt` (đã duyệt sẵn 2026-11-12; lý do: màn tạm hết nội dung, nhánh tự tạo ở Task 1); `SettingsActivity` không còn hộp thoại, hàm dựng hàng hay `registerForActivityResult` của màn cụ thể; `gradlew.bat assembleDebug` xanh. Step 5 chờ điều phối | cc26e96 | Chờ review Task 5, vòng 0/5. **Ghi cho knowledgement mục 6 (chỗ cần soi khi review):** rời màn Hình nền khi đang nhập ảnh thì việc nhập bị hủy cùng ViewModel của Activity đó. Giống hành vi rời Cài đặt trước G2d, nhưng nay dễ gặp hơn vì Back chỉ về trang chủ. Chưa kiểm trên máy: suy luận chưa kiểm chứng | Qua review điều phối 2026-11-12 (vòng 1/5).
| 2026-11-12 | 5 (máy) | Kết quả điều phối chạy trên Live 4 (bản debug, APK Task 5, md5 d5e5321f): hub không còn mục tạm, 5 mục bật + "Sắp có" còn lại; Back từ màn con về hub, từ hub về Home, chỉ một SettingsActivity trên hub; Tìm kiếm: tiêu đề đúng, danh sách bắt đầu ở công tắc đầu, tắt "Gợi ý" có hộp xác nhận (Hủy bật lại, bật lên không có hộp), "Tìm trong danh bạ" bật khi chưa có quyền hiện gợi ý, bấm gợi ý mở InstalledAppDetails, Back về, tắt danh bạ ẩn gợi ý, READ_CONTACTS vẫn `granted=false`; gợi ý "cần launcher mặc định" chưa chạy (Os26 đang là mặc định); Bộ biểu tượng: hộp kiểu đúng, hộp gói icon hướng dẫn khi không có gói; Hình nền: giá trị "Đã có ảnh", bộ chọn ảnh mở và Back không đổi gì (chọn và áp ảnh để chủ dự án); Chung: Áp dụng 4×6 → 4×5 chạy; H8: Áp dụng, Back, bấm lại "Chung" trong khoảng 0,7 giây: màn đã hiện giá trị cuối, không tiến trình, không crash (đường Mutex chưa bị thử); Liquid Glass (APK Task 3): cảnh báo API < 33 đúng cả hai chiều; không crash trong logcat; dữ liệu khôi phục từ bản sao lưu (kiểm hash). Quan sát (không bấm giờ): mở từng màn con thấy hàng ở ảnh chụp đầu sau khoảng 0,4 đến 2 giây, không thấy trạng thái tải | — | Ghi nhận từ điều phối; đã đưa vào Bằng chứng Review Focus và knowledgement mục 6 |
| 2026-11-12 | 6 | Task 6: xóa 4 chuỗi nhóm hết dùng (grep 0 tham chiếu, build xanh); viết `docs/knowledgements/G2d-settings-hub.md` (có ghi chú nhập ảnh bị hủy khi rời màn Hình nền, suy luận chưa kiểm chứng); điền Bằng chứng Review Focus, Bài kiểm cả tính năng và Đề xuất cập nhật tài liệu chung; chưa rebase (điều phối làm) | c9e8795 | Chờ điều phối rebase và review toàn nhánh |

| 2026-11-12 | Đóng | Điều phối rebase lên `main` 60ccaee (không xung đột, code app không đổi, build xanh). Chủ dự án kiểm tay đạt. Chủ dự án quyết đổi tên mục 1.2 trên trang chủ từ "Chung" thành "Cài đặt chung" (trùng tên nhóm "Chung" của bản nháp); chỉ đổi chuỗi `settings_hub_general`, tiêu đề màn con đổi theo | — | Do điều phối sửa |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Hành vi cũ giữ nguyên sau khi chuyển (H1-H10) | Điều phối đối chiếu diff các Task 1, 3, 4, 5: chỉ khác `this` thành `activity`, `rerender`, `DefaultLauncherAction` (đọc code). Live 4 (đo được, 2026-11-12): Chung mở đúng, hộp lưới 4×6 được chọn, Hủy không đổi, Áp dụng 4×6 → 4×5 chạy; Liquid Glass có khối tùy chỉnh; Bộ biểu tượng: hộp kiểu liệt kê đúng, hộp gói icon hướng dẫn khi không có gói; Hình nền: bộ chọn ảnh mở, Back không đổi gì; Tìm kiếm: hộp xác nhận tắt "Gợi ý" (Hủy bật lại), gợi ý quyền Danh bạ mở Thông tin ứng dụng và biến mất khi tắt. **Chưa chạy trên máy:** chọn và áp ảnh nền, đổi kiểu và màu icon rồi nhìn Home, hiện nhãn, "Xóa lịch sử" (chỉ code bảo vệ) | Đạt một phần; phần còn lại nằm trong bài kiểm tay |
| 2 | Launcher mặc định (B12/FX6) | Luồng chuyển nguyên văn vào `DefaultLauncherAction` (đọc code, diff được điều phối đối chiếu). **Chưa chạy trên máy:** Os26 đang là launcher mặc định trên Live 4 nên hàng mờ, gợi ý "cần launcher mặc định" không xuất hiện, hộp thoại vai trò và dự phòng chưa chạy lại sau khi chuyển | Chỉ được code bảo vệ; cần kiểm trên máy mà Os26 không là mặc định |
| 3 | Khối kính tùy chỉnh | Live 4 (đo được): mở có khối tùy chỉnh; tắt rồi bật Dock hiện cảnh báo API < 33; Hủy giữ công tắc tắt; "Vẫn bật" bật. Chưa quan sát: đổi mức làm khối biến mất, kính trên Home theo mức | Đạt một phần |
| 4 | Launcher đăng ký đúng thứ tự khi dựng lại | Mở bộ chọn ảnh rồi Back chạy được (đo được, Live 4). Dựng lại Activity khi bộ chọn đang mở, kill tiến trình: chưa chạy | Chỉ được code bảo vệ (suy luận chưa kiểm chứng) |
| 5 | Mục chưa có màn hiện mờ đúng | Live 4 (đo được): hub đủ 6 nhóm, "Sắp có" ở mục chưa có màn, chạm mục mờ không mở gì (task stack vẫn một `SettingsActivity`); mục bật tự động khi màn đăng ký (quan sát ở Task 3, 4, 5: Chung, Liquid Glass, Hình nền, Bộ biểu tượng, Cài đặt tìm kiếm chuyển từ mờ sang bật sau khi đăng ký) | Đạt |
| 6 | Không còn mã chết | `grep` trong `SettingsActivity.kt`: không còn `AlertDialog`, `registerForActivityResult`, hàm dựng hàng; `grep` toàn `app/src`: không còn `LEGACY`/`LegacyAllSettingsScreen`/`settings_hub_legacy`; bốn chuỗi nhóm cũ đã xóa và `grep` 0 tham chiếu; build xanh | Đạt |
| 7 | Không sửa ngoài vùng | `git diff --name-only e52a951 HEAD`: chỉ `app/build.gradle.kts`, `ui/settings/*`, `activity_settings.xml`, `item_settings_footer.xml`, `strings.xml` và file docs của nhánh. Không có `AndroidManifest.xml`, `HomeActivity.kt`, `ui/library/*`, ViewModel/state/event, domain, data | Đạt |
| 8 | Số phiên bản thật | Live 4 (đo được): chân trang "Phiên bản 1.0.0". `grep` `1.0.0`: duy nhất ở `app/build.gradle.kts:29`; chân trang đọc `BuildConfig.VERSION_NAME` | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: TT# Cài đặt (M8a) ghi lại cấu trúc mới: trang chủ + màn con theo nhóm; bảng đối chiếu bản nháp Figma (`Figma-Draft.md` mục 4) cập nhật cột "Đã có" theo chỗ ở mới.
- `project_structure.md`: cây `ui/settings/` (khung `SettingsActivity`; `SettingsScreen.kt` gồm interface, context, enum mã màn và bảng đăng ký; `SettingsHubScreen.kt`; `DefaultLauncherAction`; năm màn con `GeneralSettingsScreen`, `LiquidGlassSettingsScreen`, `WallpaperSettingsScreen`, `IconSetSettingsScreen`, `SearchSettingsScreen`; `SearchSettingsBlock`); dòng G2d trong bảng mốc; `versionName = "1.0.0"` (`versionCode` vẫn 1); chuỗi mới tiền tố `settings_hub_`; ghi chú `SettingsActivity` nhận extra `settings_screen` (thiếu hoặc lạ thì về hub).
- Mục "Công thức thêm màn/hàng" của plan này nên được chép vào `project_structure.md` (hoặc `MultiAgent-Workflow.md` mục 9 cho các nhánh FD-1/2/3) để nhánh sau làm theo.
- `Figma-Draft.md`: đánh dấu G2d xong (mục 6 và bảng đợt); mục 4 cập nhật chỗ ở mới của từng cài đặt (Chung, Liquid Glass, Hình nền, Bộ biểu tượng, Cài đặt tìm kiếm, Ứng dụng đã ẩn); `versionName` đã là `1.0.0`; nhánh FD-1/FD-2 thêm hàng vào màn con theo công thức.
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `lessons/settings-and-data.md`); không có mục nào cần vào `AGENTS.md` chính.
- `MultiAgent-Workflow.md`: bảng trạng thái nhánh (mục 10) ghi G2d xong để FD-1/FD-2 mở được.
