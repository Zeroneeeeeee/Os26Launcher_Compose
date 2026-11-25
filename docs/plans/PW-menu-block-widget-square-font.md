# PW — Menu nhấn giữ chặn chạm, widget tự vẽ vuông kiểu iOS, font Inter cho widget — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án: "thêm 2 task polish mới này vào và làm luôn"; chốt font Inter; giữ ngoại lệ nhấn giữ lại icon có menu)
**Trạng thái:** Xong 2026-11-25 (Task 1–4; chủ dự án build và kiểm đạt)
**Nhánh:** `an` · **Đổi schema Room:** không · **Nguồn:** `BACKLOG.md` B1 + yêu cầu chủ dự án 2026-11-25

**Goal:** (1) Menu nhấn giữ icon đang mở thì chặn mọi cử chỉ mới tới lưới (như app mẫu: chạm ngoài = đóng menu, chạm trong = hàng của menu); vẫn giữ: nhấn giữ lại chính icon đang có menu → "Sửa Màn hình chính". (2) Widget tự vẽ 2 × 2 luôn là ô vuông, 4 × 2 cao bằng 2 × 2, như iOS. (3) Chữ của widget tự vẽ giống SF Pro của iOS nhất có thể.

## Hiện trạng và nguyên nhân (đọc code 2026-11-25)

- **Menu:** `Modifier.homeGestures` (`HomeDragGesture.kt`) gắn trên `Box` gốc của `HomeScreen`, xét ở pass Initial nên thấy mọi chạm kể cả khi `HomeIconMenu` đang mở; nó dò icon dưới ngón theo toạ độ (`drag.hitTest`) rồi gọi `onIconLongPressed` → mở menu mới. Chạm nhẹ thì lớp phủ menu (`clickable` → `onDismiss`) đã chặn đúng.
- **Widget không vuông:** ô lưới Home có chiều cao = icon + khe trong + nhãn tên app, chiều rộng = icon + khe ngang (`GridMetrics.measure`) → vùng 2 × 2 cao hơn rộng khoảng 30 dp (lưới 4 × 6 có nhãn); tắt nhãn thì ngược lại. Today: khối widget cao `2 × cellHeight` (Nam, 2026-11-24).
- **Font:** chữ widget dùng font hệ thống + `isFakeBoldText` (đậm giả); thời tiết dùng Be Vietnam Pro. SF Pro không nhúng được (giấy phép Apple chỉ cho nền tảng Apple).

## Quyết định

| # | Quyết định | Ai chốt |
|---|---|---|
| D1 | Khi menu mở, `homeGestures` bỏ qua mọi cử chỉ mới trừ nhấn giữ lại chính icon đang có menu (`menuOwner`) | Chủ dự án (giữ ngoại lệ) |
| D2 | Không đổi lưới app; thẻ widget vuông trong vùng ô của nó, neo trên (phần thừa ở đáy là chỗ nhãn hàng dưới, sau này đặt nhãn tên widget như iOS); tắt nhãn → vuông theo chiều cao, căn giữa ngang. Today: khối cao = bề rộng widget 2 × 2 | Chủ dự án hỏi "cách nào bền vững, thẩm mỹ hơn trong mọi trường hợp" → Claude chọn (đổi lưới phụ thuộc cỡ lưới / bật tắt nhãn, làm lệch toàn bộ bố cục icon) |
| D3 | Font Inter (SIL OFL): chữ nhỏ Inter Semibold (như SF Pro Text Semibold), số lớn Inter Display Medium (như SF Pro Display Medium), nhiệt độ lớn Inter Display Light; số dùng `tnum` | Chủ dự án chốt Inter |

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `ui/home/compose/HomeDragGesture.kt` | Sửa | 1 |
| `ui/home/compose/HomeWidgetCell.kt` (`widgetCardFrame`, tham số `gapXPx`), `HomeGridPage.kt` (truyền khe, dấu "−" bám thẻ), `HomeDragLayer.kt` (ảnh kéo + bóng theo thẻ), `today/HomeToday.kt` (`blockHeight`) | Sửa | 2 |
| `res/font/inter_regular.ttf`, `inter_semibold.ttf`, `inter_display_light.ttf`, `inter_display_medium.ttf` (Inter 4.1, cắt còn Latin + tiếng Việt, ~115 KB mỗi file), `docs/third_party/Inter-OFL.txt` | Tạo | 3 |
| `ui/widget/WidgetText.kt` (`WidgetFont`, `WidgetFonts`, `newWidgetTextPaint(font, tabular)`), `ClockWidgetRenderers.kt`, `CalendarWidgetRenderers.kt`, `PhotoWidgetRenderers.kt`, `WeatherWidgetRenderers.kt` | Sửa | 3 |
| `core/ui/GridMetrics.kt` (khe hàng khi tắt nhãn) | Sửa | 4 |

---

### Task 1: Menu nhấn giữ chặn chạm
**Bắt buộc kiểm tay:** có.
- [x] Step 1: đầu `homeGestures`: `drag.menuOwner != null` và chạm không trúng chính icon có menu → bỏ qua cả cử chỉ (không tiêu thụ, lớp phủ menu tự xử lý).
- [x] Step 2: Kịch bản kiểm: mở menu icon A; nhấn giữ icon B (cả icon nằm dưới thẻ menu) → không mở menu B, nhả tay thì menu A đóng; chạm nhẹ ngoài → đóng; chạm hàng trong menu → chạy mục; chạm chỗ trống trong thẻ menu → không gì; nhấn giữ lại icon A → vào chế độ sửa, giữ rồi kéo → kéo A; vuốt xuống khi menu mở → không mở tìm kiếm.

### Task 2: Widget tự vẽ vuông
**Bắt buộc kiểm tay:** có (lưới 4 × 5, 4 × 6, 5 × 6; bật / tắt nhãn).
- [x] Step 1: `widgetCardFrame(width, height, spanX, gapX)`; `HomeWidgetCell(gapXPx)` vẽ thẻ trong khung đó (lưới Home, ảnh kéo); dấu "−" và bóng ảnh kéo theo khung thẻ.
- [x] Step 2: Today: `blockHeight = 2 × cellWidth + gutterX` của ô bố cục Today (khối vuông, xếp hàng sát như cũ).
- [x] Step 3: Kịch bản kiểm: Đồng hồ / Lịch / Thời tiết / Ảnh / "App gần đây" 2 × 2 vuông, 4 × 2 cao bằng 2 × 2 ở Home và Today; thẻ thẳng mép trên với icon cùng hàng; dấu "−" ở góc thẻ; kéo widget: ảnh kéo và bóng đúng thẻ; tắt nhãn: thẻ vuông căn giữa.

### Task 3: Font Inter cho widget
**Bắt buộc kiểm tay:** có (tiếng Việt có dấu, tiếng Anh; một ngôn ngữ không Latin rơi về font hệ thống).
- [x] Step 1: thêm 4 file font (cắt bằng `pyftsubset`, giữ `kern`, `liga`, `calt`, `ccmp`, `locl`, `mark`, `mkmk`, `tnum`, `case`) + giấy phép.
- [x] Step 2: `WidgetFont` / `WidgetFonts` (nạp một lần), `newWidgetTextPaint` nhận font thay `bold`; áp cho đồng hồ số, lịch nhỏ / vừa, ảnh, thời tiết.
- [x] Step 3: Kịch bản kiểm: so widget Lịch với ảnh iOS (thứ / tháng Semibold, số ngày Medium); giờ đổi phút không nhảy bề ngang; chữ có dấu tiếng Việt đủ, không vỡ.

### Task 4: Tắt nhãn thì các hàng giữ vị trí như khi có nhãn
**Bắt buộc kiểm tay:** có.
**Vì sao (chủ dự án 2026-11-25, ảnh 5×6 tắt nhãn):** commit `5d96e78` của anh Nam (khe hàng = 0,7 × khe ngang, ô khít icon + nhãn) làm lưới tắt nhãn dồn lên nửa trên, đáy trống ~200 dp. Đã xét: (a) luật iOS "khoảng hàng = khoảng cột" — không làm được khi có nhãn (hàng có nhãn đã cao hơn khoảng cột); (b) icon tự to ra khi dư chỗ dọc — không được, cỡ icon bị bề ngang chặn; (c) rải đều cho đầy trang — mất ý "hàng gần nhau theo tỉ lệ khe ngang" của anh Nam. Chốt: giữ nguyên toàn bộ cách tính của anh Nam khi có nhãn; tắt nhãn thì cộng chỗ của nhãn vào khe hàng (giống "icon lớn" của iOS). Cấu hình mặc định 4×6 có nhãn không đổi.
- [x] Step 1: `GridMetrics.measure` (nhánh `capToDock` + `topPaddingOverridePx`): `!showLabels` → `labelSlotPx = fullInnerGapPx + fullLabelHeightPx` cộng vào tổng chiều cao hàng khi tính trần khe và vào `renderGutterY`. Nhãn bị ẩn tự động (màn quá thấp) không cộng.
- [x] Step 2: Kịch bản kiểm: 4×6 và 5×6, bật / tắt nhãn ở Cài đặt: tắt nhãn thì icon ở đúng chỗ như khi bật (chỉ mất chữ), không dồn lên; kéo thả icon, widget vào ô đúng; widget tự vẽ gần vuông.

## Ngoài phạm vi

- Nhãn tên dưới widget (iOS có; dùng phần đáy thẻ vuông để trống) — ghi `BACKLOG.md`.
- Widget của app khác (AppWidgetHost) giữ khung theo ô.
- Font của phần còn lại của app (design system dùng Be Vietnam Pro) không đổi.

## Nhật ký

| Ngày | Task | Việc | Kết quả |
|---|---|---|---|
| 2026-11-25 | 1–3 | Chủ dự án giao B1 + 2 việc polish; chốt Inter (SF Pro không nhúng được), giữ ngoại lệ icon có menu; Claude chọn thẻ vuông thay vì đổi lưới (D2). Code Task 1–3 | Chờ build + kiểm |
| 2026-11-25 | — | Chủ dự án build, kiểm đạt: "ok rồi đấy, giữ bản này" | Đóng |
| 2026-11-25 | 4 | Chủ dự án thấy lưới tắt nhãn dồn cục (ảnh 5×6); phân tích 3 hướng, chốt giữ cách của anh Nam + tắt nhãn giữ vị trí hàng. Kéo `origin/master` (commit `d843fb9` của anh dvha): `git stash` → `merge --ff-only` → `stash pop`, gộp tay conflict `HomeDragLayer.kt` (giữ nhánh `PlacedAppWidget` của anh dvha + `gapXPx`). Code Task 4 | Chờ build + kiểm |
