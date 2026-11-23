# GF — Hai việc theo sau của kính: kính trang cuối sai sau khi vuốt Library, và nền mờ cho màn nhóm — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối; Task "bắt buộc kiểm tay" chờ điều phối kiểm.

**Nhánh:** `fix/gf-glass-follow-ups` · **Worktree:** `E:\SourceTree\IosLauncher-wt\GF-glass-follow-ups` · **PR:** draft (xem PR của nhánh) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (chủ dự án duyệt sẵn cả hai việc + việc chặn vuốt)
**Trạng thái:** đã code cả ba Task, chờ điều phối kiểm tay
**Khóa:** không (không đụng schema Room)

**Goal:** (1) Ô thư mục, widget, pill ở trang Home cuối giữ kính đúng sau khi vuốt sang App Library rồi về. (2) Màn nhóm của Library dùng ảnh nền mờ làm nền trên mọi máy. (3) Vuốt rời Library bị bỏ qua khi màn nhóm đang mở/đang chuyển động.
**Architecture:** một tag view `R.id.glass_ignore_translation` + hàm `GlassLocation.locationOnScreen`; `BackdropBlurDrawable` làm nền màn nhóm; một cổng `canClose()` trong `LibraryOverlayController.Host`.
**Tech Stack:** không thư viện mới.
**Spec:** yêu cầu của điều phối; liên quan AP (vuốt Library, màn nhóm vòng 2), DG (làm mới kính dock), GL (kính tại chỗ).

## Vì sao chọn thiết kế này

### Việc 1 — kính ô thư mục/widget trang cuối

- **Nguyên nhân (đọc code):** từ AP, `applyLibraryProgress` dịch cả `home_content` (`translationX = -W·p`). `BackdropBlurDrawable.prepareShader` chọn lát cắt nền bằng `owner.getLocationOnScreen − backdrop.getLocationOnScreen` mỗi lần vẽ; hàm đó cộng cả phép dịch của tổ tiên. Ô vẽ lại giữa lúc trượt (`home_content` INVISIBLE/VISIBLE ở p = 1, hoặc invalidate bất kỳ) lấy lát cắt ở chỗ đã dịch (lệch tới một bề rộng màn, có khi ngoài bản mờ) rồi giữ ảnh sai khi dừng vì không ai vẽ lại. Dock bị đúng lỗi này và đã sửa ở AP bằng `setCaptureIgnoringAncestorTransforms`.
- **Phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| A. Bỏ phép dịch của tổ tiên được gắn tag | Đi ngược cây cha, trừ `translationX/Y` của view có tag; tag đặt trên `home_content` | Mọi lần vẽ đều đúng (không phụ thuộc thời điểm); không cần vẽ lại mỗi khung lúc trượt; không cấp phát; không đụng dịch riêng của ô (push-preview), scale/xoay, cuộn pager | Kính "dính" vào ô lúc trượt (lát cắt nền trượt cùng ô) thay vì trượt trên nền đứng yên; chỉ thấy trong ~0,3 s và khớp lại khi dừng | Có |
| B. Chỉ làm mới khi Home về chỗ | Khi p = 0 invalidate mọi ô kính | Rất ít code | Mỗi khung trượt vẫn có thể vẽ sai (nhấp nháy, vệt CLAMP); đường vẽ khác (xoay màn hình giữa chừng) vẫn có thể giữ sai | Không (giữ làm lớp phụ) |
| C. Vẽ lại mọi ô kính mỗi khung lúc trượt (như cuộn pager) | `invalidateGlass()` cho mọi trang trong `onProgress` | Kính đúng vật lý (trượt trên nền) | Tốn khung trên Live 4 (kính FORCE_HIGH); ô chưa vẽ lại đúng lúc vẫn sai | Không |

  Chọn A + làm mới khi về chỗ (B) làm lớp phụ, đúng yêu cầu điều phối. Library (`library_view`) KHÔNG gắn tag: `CategoryPageView.setTranslationX` đã invalidate thanh tìm kiếm và ô nhóm mỗi lần dịch, nên lát cắt luôn đúng vị trí hiện tại (kính trượt trên nền đúng vật lý) và dừng đúng; gắn tag sẽ làm mất hành vi đó.
- **Các bề mặt dưới `home_content` đã rà:** ô thư mục và widget (`IconCellView`), pill tìm kiếm (`SearchPillView`, kính mờ và kính thật), dock (`RealGlassSurface` đã sửa ở AP; `StaticBlurSurface` tính lát cắt một lần nên cũng dùng `GlassLocation`), kính hover của `IconGridView` (cùng `BackdropBlurDrawable`).

### Việc 2 — nền màn nhóm

- **Vấn đề:** nền đen 0,35 (API 31+) hoặc 0,65 (API < 31) trông gần như đen trên Live 4.
- **Phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| A. `BackdropBlurDrawable` full màn làm `background` của panel | Cùng bản mờ nhỏ `BlurredBackdropCache.small`, một lệnh `drawRoundRect` full màn có shader; alpha theo `p` | Dùng đúng hạ tầng kính sẵn có; bản mờ dựng MỘT lần (đã có từ kính khác); hết spring thì display list đứng yên, chi phí 0; không phụ thuộc API; không ảnh nền thì tự về màu phẳng | Cần sửa nhỏ `setAlpha` của drawable (xem dưới) | Có |
| B. `StaticBlurSurface` làm view con full màn | Thêm view, `clipPath` + `drawBitmap` | Có sẵn | Thêm view và clipPath; vùng cắt tính một lần, không có alpha riêng | Không |
| C. Giữ `RenderEffect` blur Library (API 31+) cộng nền mờ | Hai lớp | Giống iOS hơn lúc chuyển | Lớp offscreen mỗi khung trên API 31+ mà không thấy được khi nền mờ phủ kín; Live 4 vốn không có | Không |

  **Quyết định về `RenderEffect`:** tắt (hằng `LIBRARY_BEHIND_BLUR_DP = 0f`, mã giữ nguyên để bật lại). Nền mờ phủ kín khi mở hẳn nên nhoè Library vô hình; chi phí lớp offscreen không đáng.
- **Sửa phụ ở `BackdropBlurDrawable.setAlpha`:** alpha của `Paint` chỉ nhân màu shader còn `PorterDuffColorFilter` tint chạy sau, nên ở alpha 0 vẫn thấy nguyên lớp tint (nháy khi fade in). Nay tint và màu phẳng cũng nhân alpha; viền trong suốt bị bỏ lệnh vẽ. Ở alpha 255 hành vi cũ không đổi.
- **Tint:** đen, alpha `GROUP_BG_TINT_ALPHA_DARK = 0.40`, `GROUP_BG_TINT_ALPHA_LIGHT = 0.25` (chữ vẫn trắng như iOS). Màu phẳng khi chưa có ảnh nền: `GROUP_BG_FLAT_DARK/LIGHT`. Tất cả trong `AppLibraryMotion`.

### Việc 3 — chặn vuốt rời Library khi màn nhóm mở

- **Phương án đã xét:** (A) cổng `canClose()` trong `LibraryOverlayController.Host`, hỏi lúc DOWN và lúc MOVE (chọn); (B) chặn ở `HomeTouchRouter` (không bắt được trường hợp màn nhóm mở sau lúc DOWN, và nhân đôi logic). Nguồn sự thật: `AppLibraryController.isGroupOpenOrAnimating = openCategoryGroup != null || panelProgress > 0`. Back và bấm ngoài vẫn đóng nhóm (đi qua `viewModel.closeCategoryGroup`, không qua cử chỉ).

## Vùng file

`core/glass/{GlassLocation (mới), BackdropBlurDrawable, StaticBlurSurface}.kt`, `res/values/ids.xml` (mới), `ui/home/controller/AppLibraryController.kt`, `ui/home/LibraryOverlayController.kt`, `ui/home/view/{SearchPillView, PanelMotion, AppLibraryMotion, CategoryGroupPanelView}.kt`.

## Global Constraints

Không thư viện mới, không test, không xóa file, không đụng docs chung; build `assembleDebug lintDebug` xanh sau mỗi Task.

## Review Focus

- `GlassLocation` chỉ trừ tổ tiên có tag (không trừ chính owner).
- Không cấp phát trong `BackdropBlurDrawable.draw`/`prepareShader` (`setAlpha` có tạo một `PorterDuffColorFilter` khi alpha đổi: chỉ ở spring của màn nhóm).
- `setAlpha` mới: `invalidateSelf()` không vòng lặp.
- `canClose()` được gọi cả lúc DOWN và lúc MOVE.

### Task 1: Bỏ phép dịch của `home_content` khỏi lát cắt kính + làm mới khi về chỗ
- [x] `GlassLocation`, tag trên `home_content` (đặt ở `AppLibraryController.setup`), dùng trong `BackdropBlurDrawable` và `StaticBlurSurface`.
- [x] Kính thật của pill bỏ phép biến đổi của tổ tiên; `SearchPillView.refreshGlass()`.
- [x] `refreshHomeGlass()` khi p về 0: dock `captureGlassOnce`, `IconGridView.invalidateGlass()` của các trang đang gắn (ô thư mục + widget + hover), pill.
- **Bắt buộc kiểm tay:** có (điều phối, xem bên dưới).

### Task 2: Nền ảnh mờ cho màn nhóm
- [x] `BackdropBlurDrawable` full màn làm nền; hằng trong `AppLibraryMotion`; `PanelMotion` nhận `Drawable`; tắt nhoè Library.
- **Bắt buộc kiểm tay:** có.

### Task 3: Bỏ qua vuốt rời Library khi màn nhóm mở
- [x] `Host.canClose()`; kiểm lúc DOWN và MOVE. Commit `fix: ignore the Library swipe while a group is open` (làm trước Task 1-2 theo yêu cầu bổ sung của điều phối).
- **Bắt buộc kiểm tay:** có.

## Bài kiểm cả tính năng (điều phối)

1. Đặt một thư mục và một widget ở TRANG CUỐI (chế độ chỉnh sửa, kéo vào trang cuối). Nền kính của chúng là ảnh nền mờ đúng chỗ.
2. Vuốt trái sang Library (chậm và fling), vuốt về, lặp 5 lần; thử dừng giữa chừng rồi vuốt tiếp. Sau khi về: nền kính ô thư mục/widget/pill/dock không lệch, không vệt ngang, không đen. Thử thêm xoay màn hình khi đang ở Library rồi về.
3. Lúc đang trượt: ô kính không nhấp nháy (chấp nhận kính "dính" theo ô).
4. Mở một nhóm trong Library: nền là ảnh nền mờ + tối nhẹ, mờ vào/ra mượt, không nháy tint lúc bắt đầu; thử chế độ sáng/tối; thử khi chưa chọn ảnh nền; Live 4 (không còn gần đen) và Oppo.
5. Khi nhóm đang mở: vuốt phải (rời Library) không làm Home trượt vào; Back và bấm ngoài vẫn đóng; vuốt ngay lúc vừa bấm ô nhóm (đang mở) cũng không; nhóm đóng hẳn rồi vuốt như cũ.
6. Số đo (nếu muốn): gfxinfo khi mở/đóng nhóm Live 4 và Oppo, so với bản `main`.

## Rủi ro

- Kính "dính" ô lúc trượt khác trước một chút (có thể không thấy). Nếu muốn kính trượt trên nền: bỏ tag khỏi `home_content` và vẽ lại mọi ô theo khung (phương án C, tốn).
- Pill kính thật bỏ phép biến đổi của chính pill (nén khi nhấn): kính co như texture (như panel, AN 9g). Chưa xem bằng mắt.

## Nhật ký

- 2026-11-10: viết plan, code Task 1-3 (3 commit code), build xanh.

## Bằng chứng Review Focus (điền khi đóng)

(điều phối điền)

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `lessons/glass.md`: kính mờ tại chỗ chọn lát cắt theo `getLocationOnScreen` nên phải bỏ phép dịch của lớp trượt bằng tag `glass_ignore_translation`; mọi lớp mới trượt chứa kính cần tag hoặc vẽ lại mỗi khung.
- `lessons/glass.md`: alpha của `Paint` không nhân vào `PorterDuffColorFilter` tint (chạy sau); phải nhân tay.
- `lessons/home-pager-animation.md`: cử chỉ có nhiều điều kiện cho phép phải kiểm lại ở MOVE, không chỉ ở DOWN.
