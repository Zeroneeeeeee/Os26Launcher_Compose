# WF — Kéo widget từ trang danh sách của bảng chọn, và bố cục lần đầu có widget Đồng hồ + Lịch — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code liền các Task, chủ dự án build và kiểm một lượt; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** _(chờ)_
**Trạng thái:** Chờ chủ dự án duyệt (Q1–Q5)
**Nhánh:** `an` · **Máy:** Oppo PKD130, Vsmart Live 4 · **Đổi schema Room:** không

**Goal:** (1) Nhấn giữ một widget tự vẽ ngay ở trang danh sách của bảng chọn (lúc bảng vừa bật lên) cũng kéo ra lưới được, đúng cỡ của ô đó — trước đây (PG2 Q1) chỉ kéo được từ khung xem trước ở trang chi tiết. (2) Máy mới (chưa chỉnh gì) vào Home thấy trang 1: widget Đồng hồ 2×2 và Lịch 2×2 ở đầu, icon app launcher của mình ở ô đầu tiên ngay sau hai widget, rồi các app khác.
**Spec:** yêu cầu chủ dự án 2026-11-25; `PG2-widget-card-drag.md` (phiên kéo "widget mới", `pickerCardDrag`); repo XML `docs/plans/FL-first-run-layout.md` (tạm dừng; WF chỉ lấy phần widget + app launcher, không lấy widget Pin, thư mục gợi ý, bước Onboarding). Mục 2 trong yêu cầu (bảng chọn không bật lên ở một số máy) chủ dự án đã sửa.

## Hiện trạng (đọc code 2026-11-25)

- **Bảng chọn widget** (`HomeWidgetPicker.kt`): trang danh sách (`Gallery`) có hàng ô gợi ý `FeaturedCell` (Đồng hồ, Lịch, Thời tiết, Ảnh, cùng 2×2; mở từ Today thêm "App gần đây" 4×2, `kind = null`), bấm mở trang chi tiết. Cử chỉ kéo `Modifier.pickerCardDrag(kind, size)` (PG2) chỉ gắn ở khung xem trước của trang chi tiết; phiên kéo `HomeDragState.startNewWidget` nhận sẵn `kind` + cỡ.
- **Bố cục lần đầu** (`LayoutReconciler.seedIfEmpty`, chạy khi `launcher_items` rỗng: máy mới hoặc xoá dữ liệu): dock 4 ô (`pickDefaultDock`), còn lại `BuildDefaultLayoutUseCase` xếp app phẳng theo tên, không widget. App launcher của mình (`LauncherEntryActivity`, mở Cài đặt) rơi vào chỗ theo tên. Widget tự vẽ là hàng `itemType = 4` (`ITEM_TYPE_BUILTIN_WIDGET`), `className = kind.id` ("clock", "calendar"), `spanX/spanY` (như `addWidget`).

## Vì sao chọn thiết kế này

- **Kéo từ trang danh sách:** dùng lại nguyên `pickerCardDrag` (giữ đủ lâu trong slop mới "arm", vuốt trước đó thì nhường cuộn / bấm vẫn mở chi tiết), chỉ gắn thêm vào `FeaturedCell` có `kind` (widget tự vẽ). Không thêm đường kéo mới.
- **Bố cục lần đầu:** chỉ đổi phần dựng bản nháp (`BuildDefaultLayoutUseCase` nhận thêm ô bị widget chiếm và app được ghim đầu) và phần ghi (`seedIfEmpty` ghi thêm 2 hàng widget trên trang 1). Không đổi luồng khởi động, không schema mới; chỉ áp khi DB rỗng nên người đã có bố cục không bị đụng.

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Kéo từ trang danh sách: những ô nào? | Mọi ô widget tự vẽ (Đồng hồ, Lịch, Thời tiết, Ảnh) đúng cỡ ô (2×2). Ô "App gần đây" (chỉ khi mở từ Today) và bảng mở từ Today không kéo (như PG2) | |
| Q2 | "Chưa chỉnh gì" hiểu là DB bố cục rỗng (cài mới / xoá dữ liệu app)? | Có: chính là lúc seed hiện tại; người đã có bố cục không đổi | |
| Q3 | Vị trí 2 widget? | Trang 1, hàng trên cùng: Đồng hồ (0,0) bên trái, Lịch (2,0) bên phải | |
| Q4 | Lưới 5 cột: hai widget chiếm cột 0–3, cột 4 ở hai hàng trên trống. App launcher đặt ở đâu? | Ô trống đầu tiên theo thứ tự đọc **sau** hai widget = hàng ngay dưới widget, cột 0 (như lưới 4 cột); ô (4,0), (4,1) lấp bằng app tiếp theo A–Z. Hoặc: chừa trống | |
| Q5 | Lưới quá nhỏ cho 2 widget 2×2 cạnh nhau (dưới 4 cột)? | Không xảy ra: preset nhỏ nhất 4×5; vẫn kiểm bằng `kind.allows` + cỡ lưới, không vừa thì seed như cũ không widget | |

## Ngoài phạm vi / ghi để sau

- Widget Pin, thư mục gợi ý (Google / Mạng xã hội / Trò chơi), bước gợi ý trong Onboarding (phần còn lại của FL bản XML).
- Lối "Sắp xếp lại màn hình chính" cho người đã có bố cục.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `ui/home/compose/HomeWidgetPicker.kt` (`FeaturedCell` gắn `pickerCardDrag`) | Sửa | 1 |
| `domain/usecase/BuildDefaultLayoutUseCase.kt` (ô bị chiếm trên trang 1, app ghim đầu) | Sửa | 2 |
| `data/layout/LayoutReconciler.kt` (`seedIfEmpty` ghi 2 widget + ghim app launcher) | Sửa | 2 |
| `docs/Launcher-Features.md`, `docs/room-schema.md` (nếu cần), `lessons/*` | Sửa | 3 |

## Global Constraints

- `ui → domain ← data`: tên gói của app (`context.packageName`) truyền từ data xuống use case.
- Mọi ghi bố cục qua `LayoutReconciler.mutex`, một transaction (như `seedIfEmpty` hiện tại); seed vẫn tôn trọng app ẩn / bỏ khỏi Home.
- Không chuỗi mới; giữ CRLF; không viết `/*` trong comment Kotlin.

## Review Focus

1. Bấm ô gợi ý vẫn mở chi tiết; vuốt dọc danh sách vẫn cuộn; giữ rồi nhả không di thì không gì; giữ rồi kéo thì widget đúng cỡ theo ngón tay.
2. Seed mới: 2 widget không chồng nhau, không chồng app; mọi app (trừ dock, app ẩn) có đúng một ô; app launcher ở ô đầu tiên sau widget; trang tràn sang trang 2 đúng.
3. `catchUp` / đặt app mới ngay sau seed không đặt lại app đã có hàng; widget không bị coi là thiếu.

---

### Task 1: Kéo widget từ trang danh sách
**Bắt buộc kiểm tay:** có.
- [ ] Step 1: `FeaturedCell` có `kind` và bảng không mở từ Today: gắn `pickerCardDrag(kind, f.size)` (cùng cử chỉ với khung xem trước của trang chi tiết).
- [ ] Step 2: Kịch bản kiểm: mở bảng từ menu "Sửa", giữ ô Đồng hồ ở trang danh sách rồi kéo ra lưới (ô trống, ô có icon, mép trang cuối); bấm nhanh ô vẫn mở chi tiết; vuốt dọc danh sách vẫn cuộn; mở từ "+" của Today: không kéo.

### Task 2: Bố cục lần đầu có Đồng hồ + Lịch và app launcher đầu trang
**Bắt buộc kiểm tay:** có (xoá dữ liệu app hoặc gỡ cài lại).
- [ ] Step 1: `BuildDefaultLayoutUseCase`: nhận `reservedFirstPage` (các hình chữ nhật bị widget chiếm trên trang 1) và `pinnedFirst` (khoá app đặt ô đầu tiên); điền app vào các ô trống còn lại theo thứ tự đọc (Q4).
- [ ] Step 2: `seedIfEmpty`: nếu lưới đủ chỗ (Q5), ghi 2 hàng `ITEM_TYPE_BUILTIN_WIDGET` (`clock` 2×2 tại (0,0), `calendar` 2×2 tại (2,0)) trên trang 1 cùng transaction; ghim app launcher của mình (`context.packageName`, app hiển thị trong danh sách) vào ô đầu tiên.
- [ ] Step 3: Kịch bản kiểm: `adb shell pm clear gambi.global.os26launcher` (hoặc gỡ cài lại) → qua Onboarding → Home trang 1: Đồng hồ + Lịch ở hàng trên, app launcher ngay dưới ở ô đầu, app khác A–Z, dock như cũ; đổi lưới 5×6 rồi xoá dữ liệu lần nữa (Q4); bấm widget mở đúng app; cài thêm một app: nằm cuối trang cuối như cũ.

### Task 3: Docs
- [ ] Step 1: `Launcher-Features.md` (bố cục lần đầu, kéo từ trang danh sách), `PG2-widget-card-drag.md` ghi chú Q1 đã mở rộng, bài học vào `lessons/`.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
_(điền khi xong)_
**Kết quả kiểm chứng (máy, ngày):**

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-25 | — | Chủ dự án yêu cầu 3 việc; mục "bảng chọn không bật ở một số máy" chủ dự án tự sửa. Đọc `HomeWidgetPicker` (`Gallery`, `FeaturedCell`, `pickerCardDrag`), `LayoutReconciler.seedIfEmpty`, `BuildDefaultLayoutUseCase`, `WidgetKind`, `LauncherItemEntity` (`ITEM_TYPE_BUILTIN_WIDGET`), FL bản XML; viết plan | Chờ duyệt Q1–Q5 |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Tài liệu chung đã cập nhật (điền ở bước 7)
