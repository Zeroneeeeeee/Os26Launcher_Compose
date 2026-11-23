# B1 — Widget Đồng hồ/Lịch mở thẳng app hệ thống, không hiện hộp chọn app — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Dự án **không viết test tự động** (AGENTS.md, chốt 2026-10-16): kiểm chứng bằng lệnh `adb` và chạy tay. **Agent thực thi không dùng máy thật**: điều phối chạy mọi lệnh `adb`. Tối đa 5 vòng mỗi Task. Lệnh viết cho PowerShell.

**Nhánh:** `fix/b1-clock-chooser` · **Worktree:** `E:\SourceTree\IosLauncher-wt\B1-clock-chooser` · **PR:** # (draft) · **Bắt đầu:** 2026-11-06 · **Plan duyệt:** chủ dự án duyệt 2026-11-06
**Trạng thái:** Task 2 (code) xong và build xanh; Task 4 mới có khung knowledgement. Chờ Task 1 và Task 3 (điều phối, máy thật). **Chỉ merge sau khi chủ dự án xác nhận trên Oppo** (Oppo đang không có hôm nay).
**Khóa:** cần Oppo để xác nhận cuối · không đụng schema · không file nóng độc quyền

**Goal:** Bấm widget Đồng hồ trên Oppo A3x hiện hộp chọn app (`ResolverActivity`) vì `AlarmClock.ACTION_SHOW_ALARMS` có 3 trình xử lý (`com.coloros.alarmclock`, `com.babydola.launcherios`, `org.fossify.clock`) và máy chưa đặt mặc định. Mở thẳng app Đồng hồ của hệ thống, như Live 4 đang làm. Làm luôn cho widget Lịch, vì cùng cơ chế (`openCalendar`).

## Vì sao chọn thiết kế này

**Nguyên nhân (đo, regression 2026-11-05):** `openClock` gọi `context.startActivity(Intent(ACTION_SHOW_ALARMS))`. Khi có nhiều trình xử lý và chưa có mặc định, hệ thống hiện hộp chọn. Live 4 chỉ có một trình xử lý nên không lỗi. `openCalendar` có cùng dạng (`makeMainSelectorActivity(CATEGORY_APP_CALENDAR)`), nên có cùng nguy cơ trên máy nhiều app Lịch (suy luận từ code, chưa đo).

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| **A. Hỏi danh sách trình xử lý, chỉ nhận app hệ thống, mở bằng component cụ thể** (cách M8d làm cho Cài đặt, `SystemSettingsRepositoryImpl`) | Không bao giờ hiện hộp chọn; đã chạy tốt trên cả hai máy ở M8d; không thêm quyền; mọi ROM có app Đồng hồ hệ thống | Cần `queryIntentActivities` (đã thấy được nhờ `<queries>` MAIN/LAUNCHER; Task 1 xác nhận) | **Chọn** |
| B. Chỉ dùng danh sách tên package đã biết (`CLOCK_PACKAGES`) | Đơn giản | Danh sách là suy luận, thiếu ROM nào là hỏng; Oppo đổi tên package giữa các đời ColorOS/OxygenOS | Giữ làm dự phòng, không làm đường chính |
| C. Bắt người dùng đặt mặc định | Không code | Đẩy việc cho người dùng; hộp chọn vẫn hiện lần đầu | Không |
| D. Tôn trọng mặc định của người dùng nếu có, ngược lại chọn app hệ thống | Giữ lựa chọn của người dùng | Cần `resolveActivity` rồi so với danh sách | **Chọn kèm A** (đúng như M8d: `preferred` nếu là app hệ thống, không thì cái hệ thống đầu tiên) |

**Thứ tự quyết định khi bấm:** (1) mặc định do người dùng đã chọn, nếu là app hệ thống; (2) trình xử lý hệ thống đầu tiên không phải `ResolverActivity`; (3) `CLOCK_PACKAGES`; (4) hiện lỗi như hôm nay (hàm trả `false`). Lịch dùng cùng thứ tự, thay (3) bằng URI thời gian của Calendar provider như hôm nay.

**Đánh đổi chấp nhận:** nếu người dùng cài app Đồng hồ bên thứ ba và *muốn* dùng nó, launcher vẫn mở app hệ thống, trừ khi họ đã đặt nó làm mặc định (mục D). Hợp với widget: nó vẽ giờ hệ thống nên mở app hệ thống là hành vi dễ đoán. **Xét lại khi:** có ROM không có app Đồng hồ nào đánh cờ `FLAG_SYSTEM` (đường (3) lo ca này).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Một hàm riêng tư dùng chung `startSystemHandler(probe: Intent)` trong `LauncherAppsDataSource` cho Đồng hồ và Lịch | Tránh chép logic hai lần |
| D2 | Chỉ sửa `LauncherAppsDataSource.kt`; `AppRepository`, `HomeViewModel` không đổi chữ ký | `openClock()`/`openCalendar()` giữ nguyên |
| D3 | Không thêm `<queries>`, không thêm quyền | Task 1 xác nhận trước |
| D4 | Không thêm test tự động | AGENTS.md |

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/data/apps/LauncherAppsDataSource.kt` | Sửa `openClock`, `openCalendar`, thêm `startSystemHandler` (vùng dòng ~227–264) | Không. **Nhánh IC cũng sửa file này ở `loadApps`**: hai vùng khác nhau, rebase sạch |
| `docs/plans/B1-clock-chooser.md`, `docs/knowledgements/B1-clock-chooser.md` | Tạo | Không |

## Global Constraints

- Không bao giờ ném lỗi ra ngoài: mọi `queryIntentActivities`/`startActivity` nằm trong `try/catch (RuntimeException)`, `Log.w` rồi thử bước kế.
- Không chạy trên luồng chính thứ nặng: `queryIntentActivities` là một IPC nhẹ nhưng gọi từ luồng bấm như hôm nay (`startActivity` cũng là IPC); không thêm `withContext`.
- Không đổi hành vi Live 4 (đang đúng).
- Commit/PR tiếng Anh; docs tiếng Việt. Không `gradlew --stop`.

## Review Focus

1. Không còn đường nào gọi `startActivity` với intent chưa có component khi có ≥ 2 trình xử lý.
2. Mặc định người dùng đã đặt (app hệ thống) vẫn được tôn trọng.
3. `ResolverActivity` không bao giờ được chọn làm đích.
4. Dự phòng (3) và (4) vẫn chạy khi không có trình xử lý hệ thống.
5. `openCalendar` trên máy một app Lịch vẫn mở đúng.
6. Chỉ sửa trong Vùng file.

---

### Task 1: Thăm dò trình xử lý trên máy thật (điều phối chạy, không code)
**Bắt buộc kiểm tay:** có — chỉ máy thật nói được `queryIntentActivities` thấy gì (package visibility Android 11+).
- [x] **Step 1 (Live 4, đã làm 2026-11-06):** `adb shell cmd package query-activities --brief -a android.intent.action.SHOW_ALARMS` và `-a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR`. Ghi lại số trình xử lý và cờ hệ thống của từng cái.
- [x] **Step 2 (Oppo, đã làm 2026-11-09):** cùng hai lệnh; kỳ vọng `SHOW_ALARMS` có 3 cái, chỉ `com.coloros.alarmclock` là hệ thống.
- [x] **Step 3 (đã làm 2026-11-09):** `adb shell dumpsys package` cho `babydola.launcherios` và `org.fossify.clock` để chắc chúng **không** đánh cờ hệ thống (nếu có, lọc `FLAG_SYSTEM` không đủ và phải đổi sang lọc theo `FLAG_UPDATED_SYSTEM_APP`/danh sách).
- [ ] **Step 4:** Từ trong app thật, bước (2) thấy được các trình xử lý này không? Task 2 thêm một dòng `Log.d` tạm để đọc số lượng (bỏ trước commit).

### Task 2: `startSystemHandler` + nối vào Đồng hồ và Lịch
**Bắt buộc kiểm tay:** không (kiểm ở Task 3).
**Files:** Sửa `data/apps/LauncherAppsDataSource.kt`.
**Interfaces:** Consumes: `PackageManager`, `FLAG_SYSTEM`. Produces: `openClock()`, `openCalendar()` cùng chữ ký.
- [x] **Step 1:** `private fun startSystemHandler(probe: Intent): Boolean`: `queryIntentActivities(probe, 0)` lọc `FLAG_SYSTEM` và khác `ResolverActivity`; `preferred = resolveActivity(probe, 0)` nếu nằm trong danh sách đã lọc, không thì phần tử đầu; mở bằng `Intent(probe).setComponent(...)` qua `startSystemActivity`. Trả `false` khi rỗng hoặc ném lỗi.
- [x] **Step 2:** `openClock = startSystemHandler(Intent(ACTION_SHOW_ALARMS)) || startFirstInstalled(CLOCK_PACKAGES)`.
- [x] **Step 3:** `openCalendar = startSystemHandler(Intent(ACTION_MAIN).addCategory(CATEGORY_APP_CALENDAR)) || startSystemActivity(URI thời gian)`.
- [x] **Step 4: Build** `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"` xanh.

### Task 3: Kiểm trên máy
**Bắt buộc kiểm tay:** có.
- [x] **Step 1 (Live 4, điều phối, đã làm 2026-11-06):** thêm widget Đồng hồ và Lịch (nếu chưa có), thoát edit mode, bấm từng cái: mở thẳng app Đồng hồ / Lịch, không hộp chọn, không crash. Hồi quy: kết quả giống trước.
- [x] **Step 2 (Oppo, điều phối, 2026-11-09):** bấm Đồng hồ → mở `com.coloros.alarmclock` ngay. Lặp 3 lần. Bấm Lịch → mở app Lịch hệ thống.
- [ ] **Step 3:** Cài tạm một app đồng hồ thứ hai trên Oppo nếu chưa đủ (đã có 3), đặt `adb shell cmd package set-home-activity`-style không cần; chỉ kiểm: khi người dùng đã chọn mặc định là app hệ thống thì vẫn mở nó.

### Task 4: Knowledgement và đóng
- [ ] Viết `docs/knowledgements/B1-clock-chooser.md` có `flowchart` thứ tự quyết định (1)→(4) và `sequenceDiagram` bấm widget → `HomeViewModel.openWidgetApp` → `AppRepository.openClock` → `startSystemHandler`. Điền "Bằng chứng Review Focus". Cập nhật dòng B1 trong `docs/Regression-Report-2026-11-05.md` sau khi Oppo xác nhận (điều phối làm, ngoài Vùng file).

## Bài kiểm cả tính năng (điều phối)
Live 4 hôm nay (hồi quy: vẫn đúng). Oppo khi máy về: Đồng hồ ×3 và Lịch ×1, không hộp chọn. **Không merge trước khi Oppo đạt.**

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-06 | Plan | Viết plan | `docs: add B1 plan` | Chờ duyệt |
| 2026-11-06 | 2, 4 (khung) | `startSystemHandler`, nối vào `openClock`/`openCalendar`, KDoc; khung knowledgement | xem `git log` | Build `assembleDebug` xanh; Task 1, 3 chờ điều phối |
| 2026-11-06 | 1, 3 (Live 4) | `query-activities`: `SHOW_ALARMS` có 1 trình xử lý (`com.google.android.deskclock`, mặc định), `APP_CALENDAR` có 1 (`com.vinsmart.calendar`). Bản B1 cài lên Live 4: bấm widget Đồng hồ 2 lần mở `DeskClock`, widget Lịch mở `AllInOneActivity`, không hộp chọn, không crash | — | Còn Oppo (Task 1 Step 2-3, Task 3 Step 2-3) |
| 2026-11-09 | 1, 3 (Oppo) | `query-activities`: `SHOW_ALARMS` 3 trình xử lý (`com.coloros.alarmclock` SYSTEM; `com.babydola.launcherios` và `org.fossify.clock` không có cờ SYSTEM, đúng giả định); `APP_CALENDAR` 1 trình xử lý (`com.google.android.calendar`, SYSTEM). Bản B1: bấm widget Đồng hồ 3/3 lượt mở `com.coloros.alarmclock`, bấm widget Lịch 3/3 lượt mở `AllInOneCalendarActivity`, không hộp chọn; chạy `reg.py J1,J2,J3` 3 lần đều đạt | — | **Oppo đạt, sẵn sàng merge.** Step 4 (Log.d tạm) không cần |
