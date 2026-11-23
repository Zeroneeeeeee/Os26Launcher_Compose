# CL — Sửa nhỏ: ô kéo kẹt ẩn và dọn lint — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Dự án **không viết test tự động** (AGENTS.md, chốt 2026-10-16): kiểm chứng bằng chạy tay trên máy thật. **Agent thực thi không dùng máy thật**: `adb install` và mọi thao tác kịch bản do điều phối chạy. Tối đa 5 vòng mỗi Task. Lệnh viết cho PowerShell.

**Nhánh:** `fix/cl-small-fixes` · **Worktree:** `E:\SourceTree\IosLauncher-wt\CL-small-fixes` · **PR:** # (draft) · **Bắt đầu:** 2026-11-06 · **Plan duyệt:** 2026-11-06 (chủ dự án: "just do 1 and 2")
**Trạng thái:** Code xong (Task 1, 2, 3 Step 1, 4), build và lint xanh; chờ điều phối kiểm Live 4 (Task 3 Step 2). Làm và kiểm được trên Vsmart Live 4; **không cần Oppo**.
**Khóa:** không schema Room · không `HomeViewModel`/`LayoutReconciler` · không đụng nhánh B1/IC (khác file)

**Goal:** hai việc nhỏ gộp một nhánh. (1) Sửa lỗi đã biết của G2c: kéo item qua từ 5 trang rồi thả bị từ chối thì ô gốc kẹt ẩn. (2) Dọn các cảnh báo lint an toàn từ báo cáo hồi quy 2026-11-05.

## Quyết định đã bỏ: tự dọn trang trống (2026-11-06)
Chủ dự án đề xuất rồi **hủy** logic "tự dọn trang trống". Lý do: khảo sát iOS trước đó chạy trên iPhone XS Max (iOS 18), chưa chắc iOS 26 giống; giữ nguyên quyết định M7 "trang trống KHÔNG tự xóa" (và sửa lỗi 2026-10-30 "trang trống ở giữa mất"). Nếu cấp trên yêu cầu thì làm lại sau; bản thiết kế và nghiên cứu đã có trong lịch sử git của nhánh này (commit `0f0d155`, `eeb8f41`) và trong tin nhắn điều phối. **Không sửa** `exitEditMode`, `pruneEmptyPages`, `LayoutReconciler`, `deleteTrailingEmptyPages`.

## Việc 1: Ô kéo kẹt ẩn sau thả bị từ chối (G2c, giới hạn đã biết)

- **Hiện tượng (G2c knowledgement, mục 11):** kéo item qua ≥ 5 trang làm `RecyclerView` tái dùng view nguồn; thả bị từ chối thì view của trang gốc đã bind lại giữa lúc kéo, giữ `hiddenItemId` cũ nên ô đó kẹt ẩn tới lần bind kế (cuộn đi rồi quay lại). Có từ trước G2c.
- **Nguyên nhân gốc (đọc code):** "đang kéo icon nào" là **dữ liệu** (`HomeUiState.draggingItemId` → `setItems(..., hiddenItemId)`), alpha là hàm của dữ liệu đó. Khi `draggingItemId` về `null`, view chỉ cập nhật alpha nếu lần `setItems` đó thực sự chạy cho **đúng view đã tái dùng**.
- **Cách sửa (chốt ở Task 1 sau khi tái hiện và đọc mã):** khi phiên kéo kết thúc (thả bị từ chối, hủy, hay thả xong), ép mọi `IconGridView`/`DockView` đang gắn vào pager áp lại alpha theo `hiddenItemId = null`. Không đổi luồng kéo-thả. Hai hướng: (a) trong `HomePagerAdapter`/`HomePagerController`, khi `draggingItemId` chuyển từ khác-null sang null thì gọi `restoreDraggedAlpha()` trên mọi holder đang bind; (b) `onViewRecycled`/`onBindViewHolder` luôn đặt lại `hiddenItemId` theo state mới nhất. Task 1 chọn theo mã thật, ghi lý do.
- **Tái hiện:** cần ≥ 5 trang. Live 4 có 4 trang; thêm trang bằng cách kéo icon tới mép trang cuối (ghost) và thả; **dọn lại bố cục như cũ khi xong** (sao lưu trước bằng `run-as tar cf - databases files/datastore no_backup`, trả lại sau). **Nếu không tái hiện được** trong 5 lượt: báo điều phối; sửa theo đọc code và kiểm sau sửa rằng kéo qua 5 trang rồi thả ra ngoài lưới không để ô nào ẩn.

## Việc 2: Dọn lint an toàn (báo cáo hồi quy 2026-11-05, mục Lint)

| Mục | Làm gì | Rủi ro |
|---|---|---|
| `CutPasteId` ở `CategoryPageView.kt:234` | Tra `R.id.category_tile` một lần, dùng lại biến `frame` (đã có ở dòng trên) thay vì `findViewById` lần hai | Rất thấp, cùng view |
| `MissingPermission` ở `SystemWallpaperSource.kt:28` | `@SuppressLint("MissingPermission")` kèm chú thích: code bắt `SecurityException` và trả `null` | Không đổi hành vi |
| `ExifInterface` ở `PickedImageSource.kt` | **Không làm** (cần thêm dependency `androidx.exifinterface`, chủ dự án chưa duyệt; ghi vào "Ngoài phạm vi") | — |
| `UseKtx`, `Overdraw`, `ClickableViewAccessibility`, `DrawAllocation` | **Không làm** | Gợi ý kiểu mã; đụng vào view/vẽ nóng thì rủi ro lớn hơn lợi ích |

## Vì sao gộp một nhánh
Hai việc độc lập về file, cùng kiểm được trên Live 4, ít dòng: một lần review, một lần kiểm cuối. Việc 1 đụng file nóng (`IconGridView`, `HomePagerAdapter`, `DockView`) nên có Task và kịch bản riêng; việc lint không được che nó.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | **Không** làm dọn trang trống | Chủ dự án 2026-11-06 |
| D2 | Không thêm dependency; bỏ mục `ExifInterface` | Chưa duyệt |
| D3 | Không sửa `UseKtx`, `Overdraw`, `ClickableViewAccessibility`, `DrawAllocation` | |
| D4 | Không test tự động | AGENTS.md |

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/HomePagerAdapter.kt` hoặc `ui/home/controller/HomePagerController.kt`, `ui/home/view/IconGridView.kt`, `ui/home/view/DockView.kt` | Việc 1: chỉ phần áp lại alpha khi hết kéo | Nóng (`IconGridView`, `HomePagerAdapter`): chỉ thêm đường áp lại alpha, không đổi luồng kéo-thả |
| `ui/home/view/CategoryPageView.kt`, `data/backdrop/SystemWallpaperSource.kt` | Việc 2 | Không |
| `docs/plans/CL-small-fixes.md`, `docs/knowledgements/CL-small-fixes.md` | Tạo / sửa | Không |

Không đổi `HomeViewModel`, `HomeUiState`, schema Room, `LayoutReconciler`, `build.gradle.kts`, `libs.versions.toml`.

## Global Constraints

- Việc 1 không thêm trạng thái mới vào `HomeUiState`, không đổi luồng kéo-thả, ghost, cascade của M7.
- Không đổi luật "không xóa trang trống" ở bất kỳ đường nào.
- Không thêm log thời gian vĩnh viễn. Không sửa tài liệu chung (điều phối cập nhật sau merge). Commit/PR tiếng Anh; docs tiếng Việt. Không `gradlew --stop`.

## Review Focus

1. Việc 1: sau thả bị từ chối qua ≥ 5 trang, không ô nào kẹt ẩn; kéo-thả thường, thả vào thư mục, kéo widget và ẩn/hiện alpha khi thả không đổi (không nháy, không ô hiện sớm trong lúc ảnh còn bay về).
2. Việc 1 áp lại alpha cho cả dock lẫn lưới, và không đụng `DragHelper.settlingItemId`.
3. Việc 2: mỗi đổi lint không đổi hành vi; `lintDebug` hết hai mục `CutPasteId` và `MissingPermission` (các mục còn lại giữ nguyên).
4. Chỉ sửa trong Vùng file.

---

### Task 1: Đọc mã và chốt hướng Việc 1 (không đổi mã)
- [x] **Step 1:** Đọc `HomePagerAdapter`, `IconGridView.setItems`/`restoreDraggedAlpha`/`onViewRecycled`, `DockView`, đường kết thúc phiên kéo (`DragHelper`, `HomeTouchRouter`) và xác nhận cơ chế kẹt ẩn ở trên.
- [x] **Step 2:** Chọn hướng (a) hay (b); ghi lý do và các ca đã xét (hủy, thả từ chối, thả thành công, thả vào thư mục, ghost) vào Nhật ký.

### Task 2: Việc 2 (lint)
- [x] **Step 1:** `CategoryPageView.kt:234`; **Step 2:** `SystemWallpaperSource.kt`; **Step 3:** Build `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"` xanh.

### Task 3: Việc 1 (kẹt ẩn)
**Bắt buộc kiểm tay:** có (điều phối chạy).
- [x] **Step 1:** Cài đặt sửa theo hướng đã chốt; build xanh.
- [ ] **Step 2:** Điều phối tái hiện trên bản `main` (trước), rồi bản sửa (sau) trên Live 4 với ≥ 5 trang: kéo một icon qua 5 trang rồi thả ra ngoài lưới/dock bị từ chối; ô gốc phải hiện lại. Hồi quy: kéo thường, thả vào thư mục, kéo widget, kéo vào dock.

### Task 4: Knowledgement và đóng
- [x] `docs/knowledgements/CL-small-fixes.md`: `sequenceDiagram` kết thúc phiên kéo → áp lại alpha, `flowchart` các ca kết thúc (thả thành công / bị từ chối / hủy), bảng lint trước/sau, ghi quyết định bỏ "dọn trang trống". Điền "Bằng chứng Review Focus" và "Đề xuất cập nhật tài liệu chung" (bỏ dòng "giới hạn đã biết" ở G2c khi đã sửa).

## Bài kiểm cả tính năng (điều phối gộp ở cuối)
Live 4: kịch bản Task 3, chạy `reg.py` của hồi quy 2026-11-05 (36 mục) để chắc không hỏng chỗ khác, `lintDebug`. Oppo không cần.

## Ngoài phạm vi / ghi để sau
- Tự dọn trang trống (hủy 2026-11-06, xem trên). ExifInterface sang AndroidX (cần duyệt dependency).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-06 | Plan | Viết plan, bỏ dọn trang trống theo chủ dự án | `docs: add CL plan` + sửa | Duyệt "just do 1 and 2" |
| 2026-11-06 | 1 | Xác nhận cơ chế: view nguồn A bị tái dùng khi kéo >= 5 trang; view B bind lại trang gốc giữ `hiddenItemId` cũ; thả từ chối chỉ trả alpha ở A; `submitList` cùng danh sách không bind lại. Chọn (a) cải biên + (b) nhẹ: `syncDragAlpha()` trong commit callback của `submitList` (sau khi danh sách áp xong nên không hiện sớm ở thả thành công) và `onViewAttachedToWindow` (view cache gắn lại không qua bind). Ca đã xét: hủy, thả từ chối, thả thành công, thả vào thư mục, thả đúng ô cũ, ghost page, dock (không cần: một view duy nhất, `setItems` áp lại alpha mỗi render). Không gọi lúc chuyển non-null sang null vì sẽ chạy trước khi danh sách áp xong | | Chi tiết trong knowledgement |
| 2026-11-06 | 2, 3.1, 4 | Lint: `CutPasteId`, `MissingPermission` sửa. Việc 1: `IconGridView.syncHiddenItem`, `HomePagerAdapter.syncDragAlpha`/`onViewAttachedToWindow`, 1 dòng ở `HomePagerController`. `assembleDebug` và `lintDebug` xanh (hai mục lint hết; 0 errors, 59 warnings). Viết knowledgement | xem git log | Chưa kiểm máy; Task 3 Step 2 do điều phối |
