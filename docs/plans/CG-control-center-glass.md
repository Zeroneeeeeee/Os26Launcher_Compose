# CG — Kính Liquid Glass cho Trung tâm điều khiển — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Không test tự động; kiểm bằng ảnh chụp trên Samsung SM-A176B (Android 16) với ảnh nền nhiều chi tiết. Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-20 · **Plan duyệt:** (chờ)
**Trạng thái:** Chờ duyệt plan
**Máy:** Samsung SM-A176B (chủ dự án mở panel bằng tay; lệnh vuốt giả qua adb không kích hoạt dải mép, đo được 2026-11-20) · **Đổi schema Room:** không

**Goal:** Panel và các khối của Trung tâm điều khiển trông như kính iOS 26: thấy nội dung phía sau (mờ), viền sáng mảnh, lớp tô nhạt hơn, bo góc thống nhất.
**Architecture:** Chỉ sửa tầng `ui/controlcenter` (View, không Compose). Không đụng `ui/home/compose/`, `HomeActivity`.
**Spec:** `plans/G3d-control-center.md` (thiết kế gốc), yêu cầu chủ dự án 2026-11-20 "giống kính hơn".

## Hiện trạng (đọc code 2026-11-20)

- Nền panel (`ControlCenterView.setBackdrop`): `ImageView` chứa ảnh nền launcher (hoặc ảnh tự chọn) đã làm mờ sẵn (`PanelBackdrop`, 72 px rộng + box blur) + `RenderEffect` 24 dp, phủ `cc_scrim` #73000000 (45% đen). Chế độ "Không": không ảnh, phủ `cc_scrim_strong` 60% đen lên app phía sau, **không làm mờ** app phía sau.
- Mọi khối dùng `CcGlassDrawable`: tô `cc_tile_bg` #2EFFFFFF (18% trắng) + viền 1 dp gradient chéo. Không có ánh sáng bề mặt (specular), không có bóng trong. Đây là kính "giả" (không lấy mẫu nền), nhưng vì nền ngay dưới đã là ảnh mờ nên lớp tô trong mờ vẫn để lộ nền.
- Bo góc lệch nhau: ô 1×1 tròn, khối 2×2 bo 20% cạnh ngắn, ô 2×1 và thanh trượt hình viên thuốc, thẻ trang Nhạc/bảng chi tiết bo 32 dp cố định, `cc_tile_radius` 22 dp (ô vuông, gần như không dùng).
- Cửa sổ panel là `TYPE_APPLICATION_OVERLAY` (vẽ đè app khác) nên không đọc được điểm ảnh của app phía sau; cách duy nhất để làm mờ thật thứ đang ở phía sau là **làm mờ xuyên cửa sổ** của hệ thống (API 31+: `FLAG_BLUR_BEHIND` + `blurBehindRadius`), chỉ chạy khi `WindowManager.isCrossWindowBlurEnabled` (ROM tắt được, tắt khi tiết kiệm pin).

## Vì sao chọn thiết kế này

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Làm mờ xuyên cửa sổ + vẽ lại khối | Chế độ "Không" + công tắc Làm mờ bật: cửa sổ panel bật `FLAG_BLUR_BEHIND`, bán kính tăng theo tiến độ mở; khối vẽ lại kiểu kính (tô nhạt + ánh sáng đỉnh + viền + bóng đáy) | Giống iOS nhất (thấy đúng màn đang mở, mờ thật); chi phí do SurfaceFlinger chịu | Chỉ API 31+ và máy bật blur; máy không hỗ trợ rơi về lớp tối như cũ | **Có** |
| B. Mỗi khối lấy mẫu ảnh nền mờ (như `BackdropBlurDrawable`) có phóng/khúc xạ | Truyền bitmap nền vào từng khối | Có hiệu ứng khúc xạ mép | Không áp được cho chế độ "Không"; thêm ~15 lệnh vẽ bitmap; nền dưới khối đã mờ nên lợi ích nhìn thấy nhỏ | Không (ghi để sau) |
| C. Chỉ chỉnh màu (tô, scrim) | Đổi vài màu | Rẻ | Không giải quyết "không thấy phía sau" ở chế độ "Không"; chưa có ánh sáng bề mặt | Không (là một phần của A) |

**Đánh đổi chấp nhận:** máy API < 31 hoặc ROM tắt blur xuyên cửa sổ (có thể gồm Oppo PKD130) vẫn thấy lớp tối không mờ ở chế độ "Không" (giữ như cũ). Kính khối vẫn là kính vẽ (không khúc xạ thật).

## Thay đổi đề xuất

1. **Nền panel, chế độ "Không" + Làm mờ bật (API 31+, máy bật blur):** cửa sổ panel thêm `FLAG_BLUR_BEHIND`, `blurBehindRadius` = `progress × 40 dp` (cập nhật theo hoạt ảnh mở/đóng, chỉ khi bán kính đổi ≥ 2 px để không gọi `updateViewLayout` mỗi khung), scrim nhẹ hơn (`cc_scrim_blur` ~ #33000000) để thấy màu app/Home phía sau. Nghe `addCrossWindowBlurEnabledListener`: hệ thống tắt blur giữa chừng thì quay về scrim đậm cũ. Chế độ ảnh nền/ảnh tự chọn giữ nguyên ảnh mờ, chỉ hạ scrim #73 → #4D để nền sáng và rõ màu hơn.
2. **`CcGlassDrawable` vẽ lại kiểu Liquid Glass** (cùng API, mọi khối dùng chung nên đổi một chỗ):
   - tô nhạt hơn: `cc_tile_bg` #2EFFFFFF → #1FFFFFFF (12%);
   - ánh sáng đỉnh: gradient dọc trắng 14% → 0 ở 45% chiều cao (giả mặt kính cong bắt sáng);
   - viền: 1 dp, sáng hơn ở góc trên trái (#B3FFFFFF) và dưới phải (#59FFFFFF), mờ ở hai cạnh giữa (giữ cách cũ, tăng tương phản);
   - bóng trong đáy: gradient đen 0 → 10% ở 25% đáy, để khối có độ dày;
   - khối "bật" (nền trắng/xanh) giữ màu đặc như iOS, vẫn có viền + ánh sáng đỉnh.
   Số lệnh vẽ mỗi khối: 2 → 3 (tô + ánh sáng đỉnh gộp bằng `ComposeShader` nếu được, viền, bóng đáy). ~15 khối, không vẽ lại liên tục (chỉ khi đổi trạng thái), nên chi phí không đáng kể (suy luận chưa đo).
3. **Bo góc thống nhất (đồng tâm như iOS 26):** khối chữ nhật bo bằng **nửa cạnh ô 1×1** (= bán kính ô tròn), nên 2×2, 1×2 (thanh trượt dọc giữ viên thuốc), thẻ trang Nhạc, bảng chi tiết, khối trang Mạng dùng cùng một bán kính tính từ cỡ ô thay vì 20% / 32 dp cố định. Truyền bán kính từ `Grid` (đã biết cỡ ô) qua một biến của `ControlCenterView`.

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Làm mờ thật thứ đang ở phía sau chỉ có ở chế độ nền "Không". Có đổi **mặc định** chế độ nền từ "Ảnh nền" sang "Không" (giống iOS: thấy màn đang mở, mờ) không? Đổi mặc định chỉ ảnh hưởng người chưa từng chọn. | Đổi sang "Không" (khóa vắng = mặc định mới; sửa `config-schema.md`) | |
| Q2 | Bo góc đồng tâm (mục 3) làm khối 2×2 tròn hơn hẳn hiện nay (~25% thay vì 20%). Đồng ý? | Đồng ý | |
| Q3 | Có nối khối CC với các thanh Kính trong Cài đặt (Bóng/Bo góc) không, hay để CC riêng? | Để riêng (ghi để sau): CC là cửa sổ khác, không dùng `GlassEnvironment` | |

## Ngoài phạm vi / ghi để sau
- Khúc xạ thật ở mép khối (phương án B), kính Backdrop Compose cho CC.
- Nối thanh Kính trong Cài đặt vào CC.
- Oppo PKD130: kiểm `isCrossWindowBlurEnabled` sau.

## Vùng file

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `ui/controlcenter/CcGlassDrawable.kt` | Sửa | ánh sáng đỉnh, bóng đáy, viền |
| `ui/controlcenter/ControlCenterView.kt` | Sửa | scrim theo chế độ, bán kính khối từ `Grid` |
| `ui/controlcenter/ControlCenterOverlay.kt` | Sửa | `FLAG_BLUR_BEHIND`, bán kính theo tiến độ, listener |
| `ui/controlcenter/ControlCenterMotion.kt` | Sửa (nếu cần) | báo tiến độ cho overlay |
| `ui/controlcenter/ControlCenterPresenter.kt` | Sửa | báo overlay chế độ blur-behind |
| `ui/controlcenter/ControlTileView.kt`, `ControlSliderView.kt`, `ConnectivityBlockView.kt`, `MediaViews.kt`, `PanelParts.kt` | Sửa | bán kính thống nhất |
| `res/values/colors.xml`, `res/values/dimens.xml` | Sửa | màu/dimen mới ở cuối khối `cc_` |
| `domain/model/controlcenter/ControlCenterConfig.kt`, `docs/config-schema.md` | Sửa (nếu Q1 = đổi) | mặc định chế độ nền |

## Review Focus
- `blurBehindRadius` chỉ đặt khi API 31+ và `isCrossWindowBlurEnabled`; không gọi `updateViewLayout` mỗi khung.
- Không đổi hành vi chạm, hoạt ảnh, phân trang.
- Chữ trắng vẫn đọc được trên nền sáng (scrim nhẹ hơn).

---

### Task 1: Nền panel làm mờ thật (blur-behind) + scrim
**Bắt buộc kiểm tay:** có — chỉ máy thật trả lời được ROM Samsung có bật blur xuyên cửa sổ cho cửa sổ overlay không.
- [ ] Overlay: cờ + bán kính theo tiến độ mở/đóng; listener bật/tắt.
- [ ] View: scrim theo chế độ (blur thật / ảnh / không blur).
- [ ] Kiểm (Samsung): chế độ "Không", mở panel trên Home (ảnh nền nhiều chi tiết) và trên một app → thấy màn phía sau mờ; đóng → không còn mờ sót.

### Task 2: Kính khối + bo góc thống nhất
**Bắt buộc kiểm tay:** không
- [ ] `CcGlassDrawable` vẽ mới; màu mới.
- [ ] Bán kính đồng tâm cho mọi khối chữ nhật.
- [ ] Kiểm (Samsung): ảnh chụp trang chính, trang Nhạc, trang Mạng, bảng độ sáng; chế độ "Ảnh nền" và "Không".

---

## Bài kiểm cả tính năng
Máy Samsung SM-A176B, ảnh nền nhiều chi tiết. (1) Chế độ "Không" + Làm mờ: mở trên Home, trên một app; (2) chế độ "Ảnh nền"; (3) tắt Làm mờ; (4) bật/tắt ô Wi-Fi, DND (khối bật); (5) trang Nhạc, Mạng, bảng độ sáng, bảng hẹn giờ. Hồi quy: kéo mở/đóng, vuốt đổi trang, Back.
**Kết quả kiểm chứng (máy, ngày):**

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-20 | — | Đọc code, viết plan | Chờ duyệt |
| 2026-11-20 | — | Chủ dự án duyệt theo khuyến nghị (Q1 đổi mặc định "Không", Q2 đồng ý, Q3 để riêng) | |
| 2026-11-20 | 1, 2 | Blur xuyên cửa sổ + scrim theo chế độ; `CcGlassDrawable` thêm ánh sáng đỉnh/bóng đáy, tô 12%; 2×2 bo 25%, thẻ lớn 38 dp; mặc định nền `NONE`. Build xanh, cài Samsung | Thẻ lớn dùng dimen 38 dp (≈ nửa ô trên màn 360–420 dp) thay vì tính từ cỡ ô. Máy chủ dự án đã lưu `WALLPAPER` nên đổi mặc định không áp |
| 2026-11-20 | 3 (thêm) | Chủ dự án: vuốt "tù". Kéo mở theo tay trực tiếp (bỏ hoạt ảnh hiện 250 ms), hiện đủ ở 30% chiều cao, thả tay ở nửa quãng (15%) là mở (trước 20%); trang chính kéo xuống = đóng, nền + khối mờ dần theo tay, thả quá 12% chiều cao hoặc vuốt nhanh thì đóng; đóng mờ 180/240 ms (trước 100/200) | Chủ dự án chọn "thả tay ở 50% là mở" |
