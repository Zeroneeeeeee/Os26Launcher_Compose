# DG — Kính dock không làm mới khi nền đổi từ "không có" sang ảnh — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo điều phối, qua review mới tick; cả tính năng kiểm tay một lần ở cuối. Dự án **không viết test tự động**. **Agent thực thi không dùng máy thật** (không adb): cài APK, chụp màn hình do điều phối chạy. Lệnh viết cho PowerShell.

**Nhánh:** `fix/dg-dock-glass-refresh` · **Worktree:** `E:\SourceTree\IosLauncher-wt\DG-dock-glass-refresh` · **PR:** #19 (draft) · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09 (chủ dự án: áp cách sửa của kính thư mục)
**Trạng thái:** Xong, chờ chủ dự án merge
**Khóa:** không khóa schema DB, không thêm cài đặt; chỉ sửa `ui/home` (không chạm `core/glass/*`)

**Goal:** Khi ảnh nền đổi từ **không có** sang một ảnh, kính dock (kính thật) phải hiện màu của ảnh mới ngay, không phải đợi chuyển trang hay chạm.
**Architecture:** Dock làm giống kính panel/thư mục (AN Task 10, GS Task 3): mỗi lần `backdrop_image` đổi nội dung, dock (a) gắn lại nguồn kính (`setBackdropSource`, cần cho `StaticBlurSurface` tính lại vùng cắt), rồi (b) chụp lại kính **đúng một lần** (`DockView.captureGlassOnce()`: bỏ chặn, `invalidate()`, trả về trạng thái chặn hiện tại). Không bật chế độ động. Xóa `pulseGlassRefresh` và `GLASS_PULSE_MS` (M8a Task 5).
**Tech Stack:** Kotlin, View/XML. Không thêm dependency.

---

## Số đo đầu vào (điều phối, 2026-11-09)

- Oppo A3x (mức kính Cao, dock = kính thật): đổi nền từ không có sang ảnh -> ảnh nền hiện trong 0,3 s, dải dock giữ (36,36,36) hơn 8 s không chạm; vuốt một trang thì dock mới đúng màu. Ảnh -> ảnh khác: dock làm mới trong 1,5 s (đường pulse).
- Kiểm toàn bộ trên hai máy (Oppo A3x Android 14, Vsmart Live 4 Android 11), ba phép đổi (none->ảnh, ảnh->ảnh, ảnh->none), so từng phần tử ngay sau khi đổi với sau khi ép vẽ lại: **chỉ dock cũ và chỉ ở none->ảnh** (sai khác trung bình tuyệt đối: Oppo 52, Live 4 7,7). Thanh tìm kiếm (pill) và ô thư mục: 0 ở mọi ca. ảnh->none: dock rơi về phẳng đúng. Panel thư mục, menu icon, lớp tìm kiếm đúng ở lần mở đầu sau none->ảnh.
- Nguyên nhân (đọc code): `BackdropController` gửi `LOADED` (không phải `REPLACED`) khi trước đó chưa hiện gì; `DockController` chỉ gọi `pulseGlassRefresh()` cho `REPLACED`; kính thật của dock có `enableDynamicBackground = false` nên không tự chụp lại.

## Vì sao chọn thiết kế này

| | A. Pulse cả khi `LOADED` | B. Chụp một lần mỗi lần đổi (chọn, như kính thư mục) |
|---|---|---|
| Sửa bug | Có (thêm điều kiện) | Có |
| Cơ chế | Bật `enableDynamicBackground` 1 s: thư viện `invalidate()` sau MỖI lần vẽ -> vòng vẽ liên tục (đã đo ở AN: ~90 khung dư mỗi lần bật 1 s; xem KDoc `BackgroundGlass`) | Một `invalidate()`, không khung dư |
| Đúng giờ | Phụ thuộc cửa sổ 1 s: ảnh tới muộn hơn thì lỡ | Chụp trong khung vẽ kế tiếp sau `setImageBitmap` |
| Nhất quán | Hai cơ chế (dock pulse, panel captureOnce) | Một cơ chế cho mọi kính thật |
| Chi phí khởi động | `LOADED` đầu app thêm 1 s vòng vẽ nếu dock có kính | `invalidate()` gộp vào khung vẽ đầu: coi như 0 |

**Vì sao B:** chủ dự án chốt dùng cách của panel; B cũng rẻ hơn (không vòng vẽ liên tục) và không phụ thuộc thời gian.

**Nghe qua đâu?** Giữ listener của `BackdropController` (`BackdropListener`) thay vì `GlassEnvironment.addBackdropListener`: (1) `DockController` đã nằm sẵn ở đó và được đăng ký TRƯỚC listener gọi `notifyBackdropChanged`; cả hai chạy đồng bộ ngay sau `setImageBitmap` trong `render()` nên dock chụp sau khi bitmap đã gắn, giống panel; (2) `GlassEnvironment` là singleton của ứng dụng còn dock thuộc Activity, nên nghe qua đó phải thêm đăng ký/gỡ theo vòng đời (panel làm trong `attach/detach`), `DockView` chưa có hook kiểu đó; (3) không phải sửa `core/glass/*` (file độc quyền). Mất gì: dock chỉ nhận tín hiệu từ `BackdropController`; hiện không có nguồn nào khác. Đổi sang đường kia sau này chỉ là đổi nơi đăng ký, cơ chế chụp giống hệt.

**Thứ tự:** `setImageBitmap` đã chạy trước `notifyChanged`; `invalidate()` của kính chỉ lên lịch vẽ ở khung kế tiếp, và `LiquidGlassView` lấy nền ở lần VẼ kính (đọc bytecode, ghi ở `BackgroundGlass`) nên thấy ảnh mới; không cần `post`.

**Kính thật trong lúc pager cuộn (TL1 Task 2b):** `DockView` nhớ cờ chặn do pager đặt (`scrollSuppressed`) và `captureGlassOnce` trả về đúng cờ đó sau khi chụp; nếu đang cuộn thì lệnh vẽ vẫn được lên lịch rồi chặn lại, kính không chụp theo cuộn.

**Mọi `BackdropChange` đều chụp (kể cả `CLEARED`):** đồng nhất, rẻ (một `invalidate()`); ảnh->none vẫn rơi về phẳng đúng như đã đo.

**Mã pulse có xóa không:** có. `grep` cho thấy `pulseGlassRefresh`/`GLASS_PULSE_MS`/`endGlassPulse` chỉ dùng ở `DockController` và `DockView`. `GlassSurface.setDynamic` giữ (interface chung, `StaticBlurSurface` vẫn no-op).

## Vùng file

| File | Tạo / Sửa | Nóng | Task |
|---|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt` | Sửa: thêm `captureGlassOnce`, cờ `scrollSuppressed`; xóa `pulseGlassRefresh`, `endGlassPulse`, `GLASS_PULSE_MS` | Không | 1 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/DockController.kt` | Sửa: `onBackdropChanged` gắn lại nguồn + chụp một lần cho mọi `BackdropChange` | Không | 1 |
| `docs/plans/DG-dock-glass-refresh.md`, `docs/knowledgements/DG-dock-glass-refresh.md` | Tạo | Không | 2 |

**Không sửa:** `core/glass/*`, `BackdropController.kt`, `HomeActivity.kt`, `BackgroundGlass.kt`, `HomePagerController.kt`.

## Global Constraints
- Không test tự động, không dependency mới, không đổi schema.
- Không bật `setDynamic(true)` cho kính dock; một lần chụp là một `invalidate()`.
- Không phá TL1 Task 2b: cờ chặn do pager đặt luôn được trả lại sau khi chụp.
- Chi phí khởi động (`LOADED` đầu tiên) không tăng: không thêm vòng vẽ, không `postDelayed`.
- Commit tiếng Anh, không dấu; `gradlew.bat assembleDebug` xanh trước khi báo xong.
- Agent không cài APK, không dùng adb.

## Review Focus
1. `captureGlassOnce` khớp `BackgroundGlass.captureOnce` (bỏ chặn -> invalidate -> chặn lại) và trả về `scrollSuppressed` thay vì ép `true` (dock đứng yên không bị chặn).
2. Pager đang cuộn khi nền đổi: không có `invalidate()` rò làm kính chụp theo cuộn; lúc hết cuộn `droppedInvalidate` bù một lần như cũ.
3. `onBackdropChanged` chạy sau `setImageBitmap`; `StaticBlurSurface` vẫn tính lại nhờ `setBackdropSource`.
4. Không còn tham chiếu nào tới `pulseGlassRefresh`/`GLASS_PULSE_MS`/`endGlassPulse` (grep).
5. `LOADED` đầu app khi `glassSurface` còn null (dock dựng bất đồng bộ): `captureGlassOnce` thoát êm, `setup()` vẫn gắn nguồn.
6. Không thêm khung dư sau lần chụp (không vòng vẽ liên tục).

## Tasks
- [x] **Task 1 — Chụp một lần cho dock.** `DockView.captureGlassOnce()` + cờ; `DockController.onBackdropChanged` gọi cho mọi `BackdropChange`; xóa mã pulse. Commit `fix: recapture dock glass once on every backdrop change`.
- [x] **Task 2 — Docs.** Plan + knowledgement. Commit `docs: add DG plan and knowledgement`.

## Bài kiểm cả tính năng (điều phối)
**Máy:** Oppo A3x (mức kính Cao, dock = kính thật) và Vsmart Live 4 (Ép cao). **Chuẩn bị:** APK debug của nhánh (ghi nhánh@commit).
Chạy lại kiểm toàn bộ như lúc đo, ba kịch bản, so từng phần tử ngay sau khi đổi với sau khi ép vẽ lại (vuốt trang):
- **A. none -> ảnh:** dải dock sai khác ~0 (trước fix: Oppo 52, Live 4 7,7). Không chạm máy trong 3 s đầu.
- **B. ảnh -> ảnh khác:** dock đúng màu mới ngay (không còn đợi 1,5 s).
- **C. ảnh -> none:** dock rơi về phẳng đúng.
- Pill, ô thư mục, panel thư mục, menu icon, lớp tìm kiếm vẫn đúng (0).
- **Hồi quy TL1 2b:** đổi nền trong lúc đang vuốt trang: không giật; `gfxinfo` khi vuốt không tệ hơn `main`.
- **Khởi động:** mở app lần đầu có ảnh nền: dock đúng màu, không thêm khung chậm so với `main`.

**Kết quả kiểm chứng (đo được, Oppo A3x và Vsmart Live 4, 2026-11-09, APK debug của `17b2e02`):** kiểm toàn bộ theo cách: mỗi phép đổi chụp Home 2,5 s sau resume không chạm, so với sau khi ép vẽ lại (vuốt trang đi rồi về), sai khác trung bình tuyệt đối mỗi vùng 0-255.
- TRƯỚC (main `1fcfd33`): none->ảnh dock Oppo 52,0, Live 4 7,7; mọi ô khác 0,0 (pill, ô thư mục; ảnh->ảnh và ảnh->none đều 0,0).
- SAU (DG): cả hai máy, none->ảnh, ảnh->ảnh, ảnh->none: dock 0,0, pill 0,0, ô thư mục 0,0.
- Cùng ngày, trên main: panel thư mục, menu icon, lớp tìm kiếm đúng ở lần mở đầu sau none->ảnh (0,0); ảnh->none dock rơi về phẳng (36,36,36) trên Oppo.
- Layout và wallpaper của chủ dự án đã khôi phục từ bản sao lưu trên cả hai máy (kiểm hash).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-09 | Plan | Đọc AGENTS, MultiAgent-Workflow, AN/GS/M8a, code dock/backdrop/glass; viết plan | commit `docs: add DG plan and knowledgement` | Chủ dự án chốt dùng cách kính thư mục, không dùng pulse |
| 2026-11-09 | 1 | `captureGlassOnce`, bỏ pulse, `onBackdropChanged` cho mọi thay đổi | commit `fix: recapture dock glass once on every backdrop change` | Build xanh |
| 2026-11-09 | Đo | Điều phối kiểm toàn bộ trên Oppo A3x và Live 4 (APK `17b2e02`): dock 0,0 ở cả ba phép đổi | commit `docs: record DG device results` | Review điều phối: duyệt `53a94ea` |

## Bằng chứng Review Focus
1. `captureGlassOnce` khớp `captureOnce`, trả `scrollSuppressed`: đọc code (53a94ea), điều phối duyệt.
2. Cuộn khi nền đổi: cơ chế `droppedInvalidate` giữ nguyên; chưa có phép đo riêng, chỉ đọc code.
3. Thứ tự sau `setImageBitmap`: đo được, dock 0,0 ngay sau 2,5 s không chạm (Oppo, Live 4, 2026-11-09).
4. Grep không còn `pulseGlassRefresh`/`GLASS_PULSE_MS`/`endGlassPulse`: đọc code (agent thực thi); build xanh.
5. `glassSurface` null lúc `LOADED` đầu: đọc code (thoát êm).
6. Không khung dư: suy luận từ cơ chế (một `invalidate()`), chưa đo `gfxinfo`.

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `AGENTS.md`: dùng MỘT cơ chế làm mới kính cho mọi bề mặt (chụp một lần qua `captureOnce`/`captureGlassOnce` khi nền đổi). Pulse động của M8a Task 5 chỉ phủ `REPLACED` nên bỏ sót `LOADED` sau khi trống (none->ảnh): dock đứng tối tới khi có lần vẽ khác (đo được 2026-11-09: Oppo 52,0, Live 4 7,7). Thêm bất kỳ phần tử kính thật mới nào thì cho nó nghe tín hiệu nền đổi và chụp một lần, không bật chế độ động.
