# PL — Chấm trang mượt hơn và mở app từ icon (TT#..) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính hoặc so animation mở app). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/pl-dots-and-launch` · **Worktree:** `E:\SourceTree\IosLauncher-wt\PL-dots-and-launch` · **PR:** #28 (draft) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (phạm vi: chỉ chấm trang; phần animation mở app hoãn)
**Trạng thái:** Task 1 đã code, chờ kiểm tay Task 2
**Khóa:** không (không đụng schema Room)

**Goal:** Chấm trang trong ô tìm kiếm: chấm trang cũ mờ dần tắt, chấm trang mới sáng dần lên, cả hai chạy theo ngón tay (tương tác, đảo ngược được), hàng chấm vẫn trượt theo vuốt như "ổ quay số" hiện nay; không còn chấm sáng trượt từ ô sang ô.
**Architecture:** Đổi cách vẽ ở `SearchPillView.drawDots` cộng một hàm trọng số thuần số học `PageDotsLayout.weightOf`.
**Tech Stack:** không thư viện mới.
**Spec:** yêu cầu trực tiếp của chủ dự án; liên quan M8c (hàng chấm ổ quay số, D10) và `docs/lessons/home-pager-animation.md`.

> **Phạm vi đã thu hẹp (chủ dự án, 2026-11-10):** chỉ làm phần 1 (chấm trang). Phần 2 (animation mở app từ icon) HOÃN; toàn bộ nghiên cứu giữ ở mục "Ngoài phạm vi / ghi để sau" bên dưới.

## Vì sao chọn thiết kế này

### Phần 1 — chấm trang

- **Vấn đề cần giải (đọc code):** `SearchPillView.drawDots` vẽ mọi chấm bằng `dotPaint` (alpha cố định `dotBaseAlpha`) rồi vẽ THÊM một vòng tròn sáng `activeDotPaint` tại `PageDotsLayout.activeSlot(position, count)`. Vòng sáng này trượt theo `position` (= `index + offset` từ `HomePagerController.onPageScrolled`) nên nhìn như một viên bi chạy giữa các chấm. Chủ dự án muốn hiệu ứng chéo mờ (cross-fade) thay cho viên bi. Giữ nguyên: cửa sổ 5 ô, hàng chấm trượt theo `windowStart`, chấm sát mép co 0.6 rồi biến mất (`scaleAt`).
- **Phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Trọng số tuyến tính theo khoảng cách | Mỗi chấm i có `w_i = max(0, 1 − abs(i − position))`; màu = lerp(màu mờ, màu sáng, w_i). Bỏ vòng sáng riêng. | Đơn giản, không trạng thái; tương tác và đảo ngược được tự nhiên vì chỉ phụ thuộc `position`; tại số nguyên đúng một chấm có w = 1, còn lại 0 nên dừng chính xác; tổng trọng số hai chấm kề luôn = 1 nên không có lúc tối cả hai | Giữa hai trang cả hai chấm ở 50% (đúng cross-fade, nhưng không có điểm nhấn rõ) | **Chọn làm mặc định** |
| B. Trọng số có easing (smoothstep) | `w' = smoothstep(w)` | Chuyển mềm ở hai đầu | Tổng hai chấm kề khác 1 (hơi tối giữa chừng); thêm một hằng phải chỉnh | Giữ làm công tắc hằng `DOT_FADE_EASED`, mặc định tắt; so tay |
| C. Animator thời gian khi trang đổi | Nghe `onPageSelected`, chạy fade 150 ms | Dễ nhìn | KHÔNG theo ngón tay, không đảo ngược được khi kéo nửa chừng, trái yêu cầu; thêm trạng thái animator | Không |
| D. Giữ viên bi, chỉ đổi màu | Đổi màu viên bi theo offset | Sửa ít | Vẫn trượt; không đúng yêu cầu | Không |

- **Vì sao chọn A (kèm công tắc B):** thỏa đúng yêu cầu "theo offset, đảo ngược được" mà không thêm trạng thái; `position` đã là nguồn duy nhất (nhãn nguồn: đọc code). Easing là câu hỏi thị giác chỉ máy thật trả lời nên để công tắc thay vì đoán.
- **Quy tắc:**
  - Màu: lerp ARGB giữa màu chấm thường (`dotPaint`, alpha `dotBaseAlpha`) và màu chấm sáng (`activeDotPaint`), nhân thêm alpha chuyển chế độ `progress` như cũ (chấm chỉ hiện khi pill ở chế độ chấm).
  - Bán kính: mọi chấm cùng `dotRadius * scaleAt(...)`. Vòng sáng cũ luôn bán kính đầy đủ; nay chấm sáng cũng chịu `scaleAt`. Suy từ code: ở các vị trí nguyên chấm sáng không nằm ô mép bị co (Task 1 xác nhận bằng số); giữa lúc vuốt nó dịch ra/vào ô mép nên có thể thấy "lớn lên". Nếu máy thật thấy lạ thì thêm cờ giữ bán kính đầy đủ cho chấm sáng.
  - Hiệu năng: `dispatchDraw` đã vẽ lại mỗi frame vuốt; thêm vài phép nhân cho ≤ vài chục chấm. Không cấp phát trong lúc vẽ (tự lerp từng kênh ARGB, không tạo object).
- **Chuyển trang bằng code:** `setCurrentItem(i, true)` làm ViewPager2 bắn `onPageScrolled` liên tục qua các trang trung gian nên nhảy xa N trang sẽ lần lượt sáng các chấm trung gian (tự nhiên, như cuộn). `setCurrentItem(i, false)` bắn một lần ở đích nên chấm đích sáng ngay, không fade (chấp nhận, ghi rõ). Đổi số trang (`setPageCount`: thêm/xóa trang, trang ma, edit mode): `position` có thể vượt `count − 1` trong một frame nên kẹp khi tính; `invalidate` như cũ. Mô tả TalkBack (`updateDescription`, `position.roundToInt()`) không đổi.
- **Đánh đổi chấp nhận:** mất điểm nhấn "viên bi"; nếu giữa hai trang quá nhạt thì xét easing B.

## Quyết định đã chốt
| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Chấm trang cross-fade theo `position`, không viên bi trượt; tuyến tính mặc định, smoothstep sau `DOT_FADE_EASED = false` | Chủ dự án, 2026-11-10 |
| 2 | Hàng chấm vẫn trượt theo vuốt (cửa sổ ổ quay số giữ nguyên) | Chủ dự án, 2026-11-10 |
| 3 | Animation mở app: HOÃN, không làm trong PL | Chủ dự án, 2026-11-10 |

## Ngoài phạm vi / ghi để sau
- **Animation mở app từ icon (hoãn).** Nghiên cứu giữ lại dưới đây để làm sau. Nếu làm: Task gồm `LaunchAnimation.kt`, hằng chọn, 2 dòng ở `HomeActivity`, so trên Live 4 + Oppo A3x (bắt buộc kiểm tay).
- Animation về home theo icon (cần quyền chữ ký/privileged, không khả thi cho launcher bên thứ ba).
- Animation từ icon cho `startShortcut` (cần truyền rect từ menu).
- `AppLibraryActivity` (`source = null`) và `startFirstInstalled`: vùng nhánh AP.
- Công tắc ẩn trong Cài đặt (xung đột IP).

### Nghiên cứu animation mở app (để dành)

- **Giới hạn của Android (đã đọc nguồn):**
  1. Animation mở app mượt kiểu Pixel/ColorOS (icon phình thành cửa sổ, cửa sổ thu về icon) dùng **remote animation / shell transition** (`ActivityOptions.makeRemoteAnimation`, `RemoteTransition`). Quyền `android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` khai trong AOSP `frameworks/base/core/res/AndroidManifest.xml` với `protectionLevel="signature|privileged|recents"`, chú thích "@hide ... Not for use by third-party applications" (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml, đọc 2026-11-10). Launcher bên thứ ba không xin được nên **không thể** tự điều khiển animation mở/đóng đồng bộ với cửa sổ app như launcher hệ thống.
  2. Dùng được là API công khai: `ActivityOptions.makeScaleUpAnimation(View source, startX, startY, width, height)` (API 16) và `makeClipRevealAnimation(...)` (API 23). Tài liệu: `startX/startY` **tương đối với `source`**; với scale-up, nếu Intent chưa đặt `sourceBounds` thì hệ thống điền từ đây (https://developer.android.com/reference/android/app/ActivityOptions). `LauncherApps.startMainActivity(component, user, Rect sourceBounds, Bundle opts)`: `sourceBounds` là "Rect chứa vùng icon đã bấm", `opts` là "Options to pass to startActivity" (https://developer.android.com/reference/android/content/pm/LauncherApps). Hiện ta đưa `sourceBounds` nhưng `opts = null`; hệ thống có dựng animation từ `sourceBounds` hay không là tuỳ ROM (chưa đo, suy luận).
  3. Hệ quả: animation MỞ chỉ là scale-up/clip-reveal hệ thống vẽ; ROM (ColorOS) có thể bỏ qua hoặc thay bằng animation riêng. Animation VỀ HOME (app đóng, thu về icon) **không điều khiển được** từ launcher bên thứ ba: do hệ thống/ROM quyết định, không biết vị trí icon của ta. Ghi rõ để không kỳ vọng.
- **Nơi sinh `sourceBounds` (đọc code):** `IconGridView` (lưới Home, cả folder), `DockView`, `CategoryPageView`/`CategoryGroupPanelView` (App Library nhúng trong Home), `SearchResultAdapter` (kết quả tìm kiếm) đều đã tính `ScreenRect` bằng `getLocationOnScreen` rồi gọi `HomeViewModel.onAppClicked(item, rect)` / `SearchViewModel.onResultTapped(result, rect)` → `AppRepositoryImpl.launch(key, ScreenRect?)` → `LauncherAppsDataSource.startMainActivity(key, Rect?)`. **Không cần đổi tầng UI** để lấy rect. Ngoại lệ: `AppLibraryViewModel.onAppClicked` (màn `AppLibraryActivity` riêng) truyền `source = null`; `startFirstInstalled` (dự phòng đồng hồ) truyền `null`.
- **Vấn đề kỹ thuật chính:** `makeScaleUpAnimation` cần một `View` làm hệ toạ độ, mà tầng data không giữ View.

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. View tham chiếu đăng ký từ HomeActivity | Lớp `LaunchOptionsFactory` (`@Singleton`, `@Inject constructor`) giữ `WeakReference<View>` là decor view của `HomeActivity` (đăng ký `onStart`, bỏ `onStop`); `startX = rect.left − loc[0]` (loc = `getLocationOnScreen` của view đó) | Không đổi chữ ký các lớp UI; mọi nơi bấm dùng chung một chỗ; không giữ View mạnh | Thêm singleton có trạng thái; chưa đăng ký thì rơi về không-options | **Chọn** |
| B. Truyền `View` từ UI xuống ViewModel/Repository | Đổi chữ ký `onAppClicked`, `launch` | Trực tiếp | ViewModel/repository giữ View = rò Activity, vi phạm tầng; sửa nhiều file nóng | Không |
| C. Dựng options ở tầng UI rồi truyền `Bundle` | UI tự gọi `ActivityOptions...toBundle()` | Không cần singleton | Sửa mọi nơi bấm + ViewModel + repository (≈10 file, trùng vùng nóng) | Không |
| D. `makeBasic().setLaunchBounds` | Đặt launch bounds | — | Là khung cửa sổ (freeform), không phải animation | Không |

- **So sánh hai hiệu ứng:** scale-up phóng cả cửa sổ từ khung icon (đơn giản, mọi ROM hỗ trợ, nhưng nội dung app có thể bị kéo giãn); clip-reveal "mở" cửa sổ bằng mặt nạ từ khung icon, nội dung không méo, gần cảm giác iOS hơn nhưng ROM có thể xử lý khác. ColorOS (Oppo A3x, Android 14) có tôn trọng hay không là chưa đo, phải so trên máy thật; Live 4 (Android 11) gần AOSP nên là máy tham chiếu.
- **Cách chọn (đề xuất):** hằng `LaunchAnimation { SYSTEM_DEFAULT, SCALE_UP, CLIP_REVEAL }`, `LAUNCH_ANIMATION` ở một object cấu hình, đọc lúc gọi. Đề xuất bước đầu là **hằng debug-time** (đổi thì build lại; chỉ so một lần), KHÔNG làm công tắc ẩn trong Cài đặt vì Settings thuộc nhánh IP đang mở, thêm hàng sẽ xung đột. Nếu chủ dự án muốn đổi không cần build lại: công tắc ẩn DataStore, làm sau khi IP merge (để sau). Chốt xong thì giữ đúng một phương án, xoá enum và hai nhánh còn lại (xoá code, không xoá file).
- **Đánh đổi chấp nhận:** không có animation về home theo icon; hiệu ứng mở khác nhau giữa các ROM.
- **App shortcut và widget:** shortcut (menu nhấn giữ, tìm kiếm) đi qua `ShortcutRepositoryImpl.startShortcut` → `launcherApps.startShortcut(pkg, id, null, null, user)` (sourceBounds và opts đều null): hiện KHÔNG có animation từ icon. `LauncherApps.startShortcut` có nhận bounds/options nhưng tầng UI chưa truyền rect xuống nên để sau. Widget: chạm vào widget do app host của widget xử lý (PendingIntent trong RemoteViews), launcher không kiểm soát animation, không đổi.


## Vùng file
| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `ui/home/view/PageDotsLayout.kt` | Sửa (thêm `weightOf`) | độc quyền |
| `ui/home/view/SearchPillView.kt` | Sửa (`drawDots`, bỏ vòng sáng, màu lerp, hằng `DOT_FADE_EASED`) | độc quyền |
| `docs/knowledgements/PL-dots-and-launch.md` | Tạo | độc quyền |

**Không đụng:** file App Library (AP), `SystemBarsController`, `RealGlassSurface`, `DockView` (AP), pipeline icon và Settings (IP), `HomeActivity`.

## Global Constraints
- Docs tiếng Việt, tên file tiếng Anh; commit tiếng Anh; không test mới, không thư viện mới, không xoá file.
- Hằng đặt tên và gom nhóm (`DOT_FADE_*` trong `SearchPillView`).
- Không cấp phát khi vẽ. `contentDescription`/TalkBack của pill không đổi.
- Không merge `main` vào nhánh; không force-push; không đụng tài liệu chung.

## Review Focus
1. Tại mọi `position` nguyên, đúng một chấm sáng hoàn toàn, mọi chấm khác đúng màu thường.
2. Giữa hai trang, hai chấm kề đổi màu ngược chiều theo offset; vuốt nửa chừng rồi thả về thì đảo ngược mượt.
3. Khi `count > 5`, hàng chấm vẫn trượt theo vuốt, chấm mép co/biến mất như M8c; chấm sáng ở ô giữa không bị co.
4. `setPageCount` giảm lúc `position` lớn (xoá trang, trang ma) không crash, không chấm kẹt.

---

### Task 1: Hàm trọng số chấm và cross-fade trong pill
**Bắt buộc kiểm tay:** không (so nhìn gộp ở Task 2).
**Files:** `PageDotsLayout.kt`, `SearchPillView.kt`.
**Interfaces:** Consumes `position`, `pageCount`. Produces `PageDotsLayout.weightOf(index, position, eased)`.
- [x] **Step 1:** Thêm `weightOf` (tuyến tính; nhánh smoothstep sau `DOT_FADE_EASED = false`).
- [x] **Step 2:** Đổi `drawDots`: lặp mọi chấm, màu = lerp(thường, sáng, w), bán kính `dotRadius * scaleAt`, bỏ vòng sáng riêng; gom hằng.
- [x] **Step 3:** Kiểm bằng số `scaleAt` của chấm sáng tại `position` nguyên: luôn = 1 (chi tiết ở knowledgement mục 6). Build `assembleDebug lintDebug` xanh.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — gộp vào Task 2.

### Task 2: Kiểm tay chấm trang, đối chiếu hai kiểu easing
**Bắt buộc kiểm tay:** **có** — phán đoán thị giác chỉ máy thật trả lời (tuyến tính hay smoothstep; chấm sáng có "lớn lên" ở mép không). Sai thì chỉnh hằng, không phải làm lại. Điều phối làm trên Live 4 với `DOT_FADE_EASED` false rồi true.
- [ ] **Step 1:** Điều phối build debug, cài Live 4 (khi máy rảnh): (a) vuốt chậm giữa hai trang: chấm cũ tối, chấm mới sáng, không viên bi; (b) vuốt nửa chừng rồi trả về; (c) fling nhiều trang; (d) ≤ 5 và > 5 trang; (e) lật trang bằng code (`PagerFlipAnimator`); (f) TalkBack đọc "trang n/N"; (g) thêm/xoá trang, trang ma, edit mode.
- [ ] **Step 2:** Chốt easing, xoá nhánh dư.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
**Máy:** Live 4. **Dữ liệu:** ≥ 7 trang (kèm trang ma) và cấu hình 3 trang. Kịch bản: Task 2 Step 1, cộng hồi quy xoay màn và sáng/tối (màu chấm).
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa có.

## Rủi ro
- Pill: bán kính chấm sáng đổi ở mép khi đang vuốt (tại số nguyên vẫn đầy đủ); có phương án cờ giữ bán kính đầy đủ nếu máy thật thấy lạ.
- Chuyển trang không animation (`setCurrentItem(i, false)`): chấm đích sáng ngay, không fade (chấp nhận).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-10 | — | Viết plan | 9e406f0 | PR #28 |
| 2026-11-10 | 1 | Cross-fade chấm trang; plan thu hẹp còn chấm trang | bdad06a | Build `assembleDebug lintDebug` xanh; chờ Task 2 |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: ghi chấm trang cross-fade.
- `project_structure.md`, `room-schema.md`: không.
- `AGENTS.md` / `lessons/`: xem mục "Bài học" trong knowledgement; ghi thêm giới hạn animation mở app của launcher bên thứ ba (nghiên cứu ở plan).
