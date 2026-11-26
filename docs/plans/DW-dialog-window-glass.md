# DW — Khung mờ thừa quanh hộp thoại; nút "+" / "Xong" của Today dạng kính — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án chọn cách 1 cho hộp thoại; giao thêm nút của Today)
**Trạng thái:** Xong 2026-11-25 (Task 1–2; chủ dự án build và kiểm đạt)
**Nhánh:** `an` · **Đổi schema Room:** không

**Goal:** (1) Mọi `DsAlertDialog` (Cài đặt: cỡ lưới, đổi tên app, đánh giá…) chỉ có MỘT thẻ kính, không còn khung mờ có tint lớn hơn thẻ phía sau (ảnh chủ dự án 2026-11-25, hộp "Cỡ lưới"). (2) Viên "+" và "Xong" ở chế độ sửa của Today dùng kính thật như "Sửa" / "Xong" của Home.

## Nguyên nhân (đọc code + mã nguồn Android)

- `DsAlertDialog` (`core/designsystem/compose/DsDialogs.kt`) là cửa sổ `Dialog` riêng; kính = blur nền cửa sổ của hệ thống (`DialogWindowGlass`, `setBackgroundBlurRadius` + nền `GradientDrawable` có tint). Comment cũ giả định cửa sổ "ôm vừa nội dung", nhưng với `usePlatformDefaultWidth = false` Compose cho cửa sổ rộng hết màn (ảnh: khung bo góc tràn hai bên, thẻ 312 dp ở giữa chỉ còn viền).
- Không thu được vùng mờ về khung thẻ trong cửa sổ rộng: `DecorView` đặt lớp mờ dưới nền gốc trong cùng `LayerDrawable` (bằng cả nền), nền chỉ cho bán kính bo góc qua `getOutline`.
- Bảng kéo từ dưới lên (`DsBottomSheet`), bảng chọn widget, thẻ xoá (`RemoveAppOverlay`) không dùng đường này.

## Phương án (chủ dự án chọn 1)

| # | Cách | Đánh đổi |
|---|---|---|
| **1** | `usePlatformDefaultWidth = true`: cửa sổ ôm vừa thẻ → vùng mờ = thẻ | Hoạt ảnh mở đổi 1,1 → 1 thành 0,95 → 1 (thẻ to hơn cửa sổ bị cắt mép). Máy bảng / màn ngang: bề rộng tối thiểu của hộp thoại (65 %) có thể > 312 dp → lộ khung lại (chưa kiểm) |
| 2 | Đưa hộp thoại vào cây Compose của màn (như thẻ xoá) | Sửa nhiều màn, màn không có lớp nền thì thẻ đặc |
| 3 | Làm mờ cả màn phía sau | Cả màn nhoè, khác kiểu thẻ xoá |

## Vùng file

| File | Sửa | Task |
|---|---|---|
| `core/designsystem/compose/DsDialogs.kt` (`usePlatformDefaultWidth = true`, `ENTER_SCALE` 0,95), `core/glass/compose/DialogGlass.kt` (comment) | Sửa | 1 |
| `ui/home/compose/today/HomeToday.kt` (`TodayPill`) | Sửa | 2 |

### Task 1: Hộp thoại không còn khung mờ thừa
- [x] Step 1: `DsAlertDialog`: `usePlatformDefaultWidth = true`; hoạt ảnh mở từ 0,95.
- [x] Step 2: Kịch bản kiểm (Oppo, Android 12+): mở các hộp thoại của Cài đặt (Cỡ lưới, đổi tên app, chọn ngôn ngữ, đánh giá…): chỉ một thẻ kính, vùng mờ trùng thẻ; mở / đóng không cắt mép; chạm ngoài thẻ đóng; xoay ngang (nếu màn cho xoay) xem có lộ khung. Live 4 (Android 11): thẻ nền đặc như cũ.

### Task 2: Nút "+" / "Xong" của Today dạng kính
- [x] Step 1: `TodayPill`: `dsGlass(DsSurface.CONTROL, DsCorner.Pill, realGlass = true, lensScale = 0.5)` thay nền `search_scrim`, như `EditPill` của Home.
- [x] Step 2: Kịch bản kiểm: vào chế độ sửa của Today, hai viên giống viên của Home (kính, viền sáng), chữ đọc được trên mọi ảnh nền; dưới API 31 kính mờ tĩnh.

## Nhật ký

| Ngày | Task | Việc | Kết quả |
|---|---|---|---|
| 2026-11-25 | — | Chủ dự án kiểm đạt ("ok rồi đấy") | Đóng |
| 2026-11-25 | 1–2 | Ảnh chủ dự án (hộp "Cỡ lưới"); đề xuất thu nền cửa sổ về khung thẻ → đọc mã nguồn `DecorView` thấy không được, đưa 3 cách; chủ dự án chọn 1. Code Task 1–2 | Chờ build + kiểm |
