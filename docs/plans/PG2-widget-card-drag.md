# PG2 — Kéo thẻ widget từ bảng chọn ra lưới Home (F2, P2-9) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code liền các Task (preferences), chủ dự án build và kiểm một lượt; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được**: chủ dự án chạy `.\gradlew.bat assembleDebug lintDebug installDebug` và gửi lỗi. Kiểm tay trên Oppo PKD130.

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** 2026-11-23
**Trạng thái:** Xong 2026-11-23 (chủ dự án build và kiểm tay đạt trên Oppo PKD130)
**Nhánh:** `feature/pg1-parity` (hoặc nhánh chủ dự án tạo) · **Máy:** Oppo PKD130 · **Đổi schema Room:** không

**Goal:** Trong bảng chọn widget (mở từ menu "Sửa" của Home), nhấn giữ một thẻ widget rồi kéo thì bảng ẩn đi, thẻ thành ảnh widget đúng cỡ ô lưới theo ngón tay, thả xuống ô nào trên lưới Home (kể cả lật trang, trang tạm) thì widget nằm ở đó; như iOS và như bản XML (`WidgetPickerController.CardDragGesture`, G2c Task 13, P2-9).
**Architecture:** Hướng B (chủ dự án chốt 2026-11-23): bảng chọn không còn là cửa sổ `Dialog` riêng mà là lớp phủ trong cây Compose của `HomeActivity`, nên chuỗi chạm bắt đầu trên thẻ đi tiếp được vào `HomeDragState` (phiên kéo "widget mới" chưa có hàng DB). VM đã có `HomeViewModel.onNewWidgetDropped` (G2c Task 13, id tạm, đẩy icon lạc quan, ghi bằng `AddWidgetUseCase`).
**Spec:** `PG-parity-gaps.md` F2; repo XML `WidgetPickerController` (`CardDragGesture`, `beginNewWidgetDrag`, `onNewWidgetDragFinished`); `HomeWidgetPicker.kt:104` ("Chưa port"); JH4 D5.

## Hiện trạng (đọc code 2026-11-23)

- `HomeWidgetPicker.show()` mở `activity.dsDialogs.show { DsBottomSheet { Gallery() } }`; trang chi tiết là sheet thứ hai. `DsBottomSheet` bọc nội dung trong `Dialog` (cửa sổ riêng), tự làm: trượt lên khi mở, kéo xuống để đóng, lớp mờ 60 % nhạt theo độ kéo, Back / chạm lớp mờ để đóng. `HideStatusBarOfSheet` ẩn thanh trạng thái của cửa sổ sheet trong edit mode (C19).
- Cửa sổ riêng giữ cả chuỗi chạm (bài học G2c), nên thẻ không kéo ra lưới được.
- Kéo trên Home: `homeGestures` (gốc `HomeScreen`, pass Initial) gọi `HomeDragState.start/move/drop/cancel`; `HomeDragState` dùng toạ độ gốc cửa sổ; ảnh nổi vẽ ở `HomeDragLayer` trong `HomeScreen`. `HomeDragState` tạo trong `HomeScreen` (không ra ngoài được).
- `drop()` nhánh lưới gọi `actions.onItemDropped(itemId, HOME, pageId, cell)`; `cancelDrop` bay về ô nguồn.

## Vì sao chọn thiết kế này

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| A. Giữ `Dialog`, chuyển chạm sang Activity | Như XML: bảng ẩn nhưng cửa sổ sống, chuyển `MotionEvent` sang `Activity.dispatchTouchEvent`; `HomeDragState` thêm lối nhận sự kiện thô vì Compose bỏ chuỗi chạm thiếu cú nhấn xuống | Không đụng bảng chọn | Hai đường kéo song song (pointerInput và sự kiện thô) dễ lệch hành vi (ngón thứ hai, huỷ, xoay) | Không |
| B. Bảng chọn là lớp phủ trong cửa sổ Home | Tách phần thân sheet khỏi `Dialog`; thẻ có cử chỉ nhấn giữ riêng, sau khi "arm" thì chính `pointerInput` của thẻ lái `HomeDragState` (nút nhận cú nhấn xuống nhận tiếp mọi sự kiện kể cả khi ngón ra ngoài thẻ) | Một đường kéo, một hệ toạ độ; không cần chuyển sự kiện | Phải tự làm những gì `Dialog` lo sẵn (xem Task 1) | **Chọn** (chủ dự án 2026-11-23) |

- **Không bỏ thân sheet khỏi composition khi kéo:** nếu gỡ bảng, `pointerInput` của thẻ bị huỷ giữa chừng (bài học JH2: cử chỉ sống lâu hơn một vùng phải ở chỗ không bị gỡ). Bảng chỉ mờ về 0 và bỏ lớp mờ, đóng hẳn sau khi thả.
- **Ảnh nổi dùng lại `DragItem.Widget`** với một `PlacedWidget` tạm (id tạm âm, cỡ = cỡ thẻ trên lưới), nên `HomeDragLayer`, xem trước đẩy-dồn, chặn chồng widget, neo điểm giữ (P2-7), lật mép, trang tạm chạy như kéo widget có sẵn. Phiên mang thêm cờ "widget mới" để `drop`/`cancel` rẽ nhánh.

## Quyết định đã chốt

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Hướng B: bảng chọn vẽ trong cửa sổ Home | Chủ dự án 2026-11-23 |
| 2 | Chỉ widget tự vẽ (Đồng hồ, Lịch, Thời tiết, Ảnh) kéo được, như XML; widget của app khác vẫn bấm "Thêm tiện ích" (cần luồng cấp quyền / cấu hình của hệ thống) | Claude đề xuất (Q2) |
| 3 | Bảng mở từ Today (`forToday`) không kéo được (Today có bố cục riêng), như XML | Như XML |
| 4 | `DsBottomSheet` (dùng ở chỗ khác) giữ nguyên hành vi: tách phần thân thành `DsBottomSheetLayer` dùng chung, `DsBottomSheet` = `Dialog { DsBottomSheetLayer }` | Claude |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Kéo được từ đâu? | Cả ô gợi ý ở trang danh sách và khung xem trước ở trang chi tiết (như XML) | **Chỉ khung xem trước ở trang chi tiết** (chủ dự án 2026-11-23); ô gợi ý ở trang danh sách giữ bấm để mở chi tiết |
| Q2 | Widget của app khác có kéo được không? | Chưa (QĐ 2); ghi để sau | Như XML: không kéo (repo XML không gán `dragKind` cho widget app; không thiếu quyền Android, vướng luồng gắn/cấu hình không đồng bộ) — chủ dự án xác nhận 2026-11-23 |
| Q3 | Thả không được nhận (ô bị widget khác chiếm, thả ra ngoài lưới, thả vào dock)? | Ảnh co + mờ tại chỗ rồi mất, không thêm gì, **đóng bảng** (như XML `onNewWidgetDragFinished` đóng cả hai bảng dù nhận hay không) | Như XML |
| Q4 | Bảng ẩn thế nào khi bắt đầu kéo? | Mờ về 0 trong ~150 ms cùng lớp mờ nền (bảng vẫn giữ chỗ, không nhận chạm mới); thả xong đóng hẳn | Đồng ý |
| Q5 | Bàn phím của ô tìm kiếm trong bảng: trước đây cửa sổ `Dialog` tự xử lý, giờ trong cửa sổ Home. | Giữ như hiện tại (ô tìm nằm đỉnh bảng nên luôn thấy; bàn phím che phần danh sách dưới); kiểm tay, xấu thì sửa | Đồng ý |

## Ngoài phạm vi / ghi để sau

- Kéo widget của app khác (QĐ 2); kéo thẻ vào Today.
- Kính thật cho bảng chọn (vẫn `DsSurface.SHEET` tĩnh như hiện tại).

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `core/designsystem/compose/DsDialogs.kt` (`DsBottomSheetLayer` tách từ `DsBottomSheet`) | Sửa | 1 |
| `ui/home/compose/HomeWidgetPicker.kt` (trạng thái mở/chi tiết thành state, `@Composable Render()`, bỏ `dsDialogs` và `HideStatusBarOfSheet`; cử chỉ thẻ) | Sửa | 1, 3 |
| `ui/home/HomeActivity.kt` (gọi `widgetPicker.Render()` trên cùng, `dragActions.onNewWidgetDropped`) | Sửa | 1, 2 |
| `ui/home/compose/HomeDragState.kt` (`startNewWidget`, cờ phiên, nhánh `drop`/`cancelDrop`, `HomeDragActions.onNewWidgetDropped`) | Sửa | 2 |
| `ui/home/compose/HomeScreen.kt` (`HomePagerInfo.drag` để bảng chọn lái phiên kéo) | Sửa | 2 |
| `docs/plans/PG-parity-gaps.md`, `docs/lessons/*`, `docs/Launcher-Features.md` (dòng P2-9 nếu có) | Sửa | cuối |

## Global Constraints

- `ui → domain ← data`; không đổi `domain`/`data`, không khóa config, không dependency mới, không chuỗi mới (nếu cần: đủ 9 file `strings.xml`).
- `DsBottomSheet` ở các chỗ khác (thời tiết, ảnh nền...) không đổi hành vi.
- Không tạo đối tượng `@RememberInComposition` lúc dựng ngoài `remember` (bài học PG1).
- Giữ CRLF; không viết `/*` trong comment Kotlin.

## Review Focus

1. Bảng chọn trong cửa sổ Home vẫn đủ: trượt lên khi mở, kéo xuống / Back / chạm lớp mờ để đóng, trang chi tiết đè lên trang danh sách, nút Home và thoát edit mode đóng bảng (B10), thanh trạng thái ẩn trong edit mode (C19), thứ tự lớp trên Thư viện / Today.
2. Cử chỉ thẻ: bấm nhanh vẫn mở chi tiết / thêm; vuốt trước khi giữ đủ lâu thì cuộn danh sách / lật pager chi tiết; giữ rồi nhả không di thì không làm gì; giữ rồi di quá slop thì bắt đầu kéo.
3. Phiên widget mới: không gọi `onItemDropped` với id tạm; thả hợp lệ gọi `onNewWidgetDropped` đúng trang (cả `PageId.NEW` của trang tạm); thả không hợp lệ gọi `onDragCancelled` và không để `draggingItemId` treo.
4. Ngón thứ hai, huỷ cử chỉ (cuộc gọi đến, Home khi đang kéo) không để phiên kéo hay bảng treo.
5. Widget mới thay id tạm bằng id thật không thu rồi phình (A1 coi là thay tại chỗ).

---

### Task 1: Bảng chọn là lớp phủ trong cửa sổ Home (chưa kéo)
**Bắt buộc kiểm tay:** không (đổi nơi vẽ; kiểm cùng bài cuối)
- [x] Step 1: `DsDialogs.kt`: tách thân `DsBottomSheet` (lớp mờ, khối sheet, trượt lên, kéo xuống để đóng, tay nắm, tiêu đề, nút đóng) thành `DsBottomSheetLayer(onDismiss, title, showClose, hidden: Float = 0f, content)` không có `Dialog`, có `BackHandler`; `DsBottomSheet` = `Dialog { DsBottomSheetLayer(...) }` như cũ (giữ `setDimAmount(0f)`, `LocalDsBackdrop = null`).
- [x] Step 2: `HomeWidgetPicker`: `open`, `forToday`, `detail` thành state; `show()`/`closeIfOpen()`/`onEditMode()` giữ chữ ký; `@Composable fun Render()` vẽ `DsBottomSheetLayer { Gallery() }` và, nếu có, lớp chi tiết đè lên; bỏ `HideStatusBarOfSheet` (cùng cửa sổ Home, edit mode đã ẩn thanh trạng thái).
- [x] Step 3: `HomeActivity`: gọi `widgetPicker.Render()` sau `SearchOverlay`, trước `dsDialogs.Render()`.
- [x] Step 4: Kịch bản kiểm (Oppo) — mở bảng từ menu "Sửa" và từ viên "+" của Today; tìm, cuộn, mở chi tiết, vuốt các cỡ, "Thêm tiện ích"; đóng bằng kéo xuống, Back (chi tiết đóng trước), chạm lớp mờ, nút Home, thoát edit mode; thanh trạng thái không hiện lại trong edit mode.

### Task 2: Phiên kéo "widget mới" trong `HomeDragState`
**Bắt buộc kiểm tay:** không (kiểm cùng Task 3)
- [x] Step 1: `HomeDragSession` thêm `newWidget: WidgetKind?`; `HomeDragState.startNewWidget(kind, size, tempId, posRoot)`: `DragItem.Widget(PlacedWidget(tempId, GridPosition(0, 0, spanX, spanY), kind))`, cỡ ảnh = cỡ ô lưới theo span, tâm ảnh tại ngón tay, `actions.onDragStarted(tempId)`.
- [x] Step 2: `drop()` nhánh lưới: phiên widget mới → `actions.onNewWidgetDropped(kind.id, spanX, spanY, tempId, page.pageId, cell)` rồi hạ cánh như cũ; `cancelDrop`: phiên widget mới → `actions.onDragCancelled()`, ảnh co + mờ tại chỗ (không bay về đâu).
- [x] Step 3: `HomeDragActions.onNewWidgetDropped` + nối trong `HomeActivity` → `viewModel.onNewWidgetDropped`; `HomeScreen` gán `pagerInfo.drag = drag` (gán/bỏ cùng `DisposableEffect` của PG1).

### Task 3: Cử chỉ nhấn giữ thẻ → kéo
**Bắt buộc kiểm tay:** có — cử chỉ chạm/kéo-thả mới (`Workflow.md` mục 3).
- [x] Step 1: `Modifier.pickerCardDrag(kind, size)` trên **khung xem trước của trang chi tiết** (Q1; widget tự vẽ, không phải `forToday`): giữ `longPressTimeout` trong slop → rung, "arm", từ đây tiêu thụ sự kiện (bấm không thêm, danh sách không cuộn); di quá slop sau khi arm → `drag.startNewWidget` (toạ độ đổi sang gốc bằng `localToRoot`), bảng mờ về 0 (Q4); mỗi lần di → `drag.move`; nhấc ngón đó → `drag.drop()`; huỷ → `drag.cancel()`; xong (nhận hay không) → đóng cả hai bảng (Q3). Di quá slop trước khi arm → nhường cuộn.
- [x] Step 2: Kịch bản kiểm (Oppo) — (a) mở chi tiết Đồng hồ, giữ khung xem trước 2×2 rồi kéo tới ô trống trang 1, thả: widget ở đó, bảng đóng; giữ ô gợi ý ở trang danh sách thì không kéo; (b) kéo tới ô có icon: icon bị đẩy (xem trước), thả nhanh trước 0,5 s: icon trượt (A1); (c) kéo chạm mép phải trang cuối: trang tạm, thả: trang mới có widget; (d) thả lên widget khác / ngoài lưới / vào dock: không thêm gì, bảng đóng; (e) từ trang chi tiết, kéo cỡ 4×2; (f) vuốt dọc danh sách và vuốt ngang trang chi tiết vẫn cuộn; bấm nhanh vẫn mở chi tiết / thêm; giữ rồi nhả không di thì không gì; (g) đang kéo đặt ngón thứ hai; đang kéo bấm Home.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Gộp từ kịch bản các Task (Oppo PKD130, thứ tự thao tác):
- Mở bảng chọn từ menu "Sửa" và từ viên "+" của Today; tìm, cuộn, mở chi tiết, vuốt các cỡ, "Thêm tiện ích"; đóng bằng kéo xuống, Back (chi tiết đóng trước), chạm lớp mờ, nút Home, thoát edit mode. Mở một sheet khác (chọn địa điểm thời tiết): vẫn đóng bằng Back, chạm ngoài, kéo xuống.
- Trang chi tiết Đồng hồ: giữ khung xem trước rồi kéo tới ô trống, ô có icon (thả chậm và thả nhanh), mép phải trang cuối (trang tạm), widget khác / ngoài lưới / dock; kéo cỡ 4×2.
- Vuốt dọc danh sách, vuốt ngang trang chi tiết, bấm nhanh, giữ rồi nhả không di.
- Đang kéo: đặt ngón thứ hai (chạm rồi nhấc); bấm Home.

**Kết quả kiểm chứng (máy, ngày):** Đạt — chủ dự án kiểm trên Oppo PKD130, 2026-11-23, sau bản sửa lỗi ngón thứ hai (lớp chặn chạm khi đang kéo). Bấm Home khi đang kéo: huỷ, widget không được thêm (chủ dự án chốt).

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-23 | — | Đọc `HomeWidgetPicker`, `DsBottomSheet`, `HomeDragState`, `homeGestures`, VM `onNewWidgetDropped`, XML `CardDragGesture`; viết plan | Chờ duyệt, Q1–Q5 |
| 2026-11-23 | — | Chủ dự án trả lời Q1 (chỉ trang chi tiết), Q3–Q5; Q2 hỏi thêm về quyền | Chờ chốt Q2 |
| 2026-11-23 | — | Chủ dự án xác nhận Q2 theo XML → plan duyệt | Bắt đầu code |
| 2026-11-23 | 1 | `DsDialogs.kt`: `DsBottomSheetLayer` (thân sheet, `BackHandler`, không Dialog); `DsBottomSheet` = `Dialog(dismissOnBackPress = false, dismissOnClickOutside = false) { DsBottomSheetLayer }` (Back/chạm lớp mờ vẫn đóng có hoạt ảnh như cũ). `HomeWidgetPicker`: `open`/`detail`/`dragging` là state, `Render()` vẽ hai lớp (chi tiết đè lên), bỏ `dsDialogs` và `HideStatusBarOfSheet`; `HomeActivity` gọi `widgetPicker.Render()` sau `SearchOverlay` | Chưa build |
| 2026-11-23 | 2 | `HomeDragState.startNewWidget` (item `DragItem.Widget(PlacedWidget tạm)`, cỡ ô theo span, container nguồn `NEW_WIDGET_CONTAINER`), `HomeDragSession.newWidget`; `drop` nhánh lưới gọi `onNewWidgetDropped`; `cancelDrop` co + mờ tại chỗ; `HomeDragActions.onNewWidgetDropped` nối VM; `HomePagerInfo.drag` gán trong `HomeScreen` | Chưa build |
| 2026-11-23 | 3 | `HomeWidgetPicker.pickerCardDrag` trên khung xem trước trang chi tiết (`DetailPage.dragKind`, widget tự vẽ, không phải Today): giữ đủ lâu → rung, arm, tiêu thụ; di quá slop → `startNewWidget` (toạ độ `localToRoot`), bảng mờ 150 ms; `move`/`drop`/`cancel`; xong đóng hai bảng; id tạm `-(1 shl 40) - n` | Chưa build |
| 2026-11-23 | 3 | Lỗi kiểm tay (g): ngón thứ hai chạm lớp mờ / thân sheet (bảng mờ về 0 nhưng vẫn nhận chạm) → `closeAll` → cử chỉ thẻ bị huỷ → widget mất. Sửa: `Render()` thêm lớp trên cùng khi `dragging`, nuốt mọi ngón mới. Bấm Home khi đang kéo: chủ dự án chốt giữ huỷ (widget không được thêm) | Chờ build lại |
| 2026-11-23 | 1–3 | Chủ dự án build và kiểm tay lại: đạt. Tick các Step, điền bài kiểm và bằng chứng, chép bài học vào `lessons/drag-and-layout.md`, tick F2 ở `PG-parity-gaps.md` | Xong |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Bảng chọn trong cửa sổ Home đủ hành vi cũ | `HomeWidgetPicker.Render()` (hai `DsBottomSheetLayer`, chi tiết đè lên), `closeIfOpen` từ `onNewIntent` / `onStop` / `onEditMode`; `DsBottomSheet` = `Dialog { DsBottomSheetLayer }`; chủ dự án kiểm Task 1 Step 4 | Đạt |
| 2 | Cử chỉ thẻ không phá bấm / cuộn | `pickerCardDrag`: chỉ tiêu thụ sau khi giữ đủ `longPressTimeout` trong slop; di quá slop trước đó thì thoát cử chỉ; chủ dự án kiểm (f) | Đạt |
| 3 | Phiên widget mới không ghi id tạm qua `onItemDropped` | `HomeDragState.drop`: `s.newWidget != null` → `onNewWidgetDropped(..., page.pageId, cell)`; `cancelDrop` gọi `onDragCancelled`; chủ dự án kiểm (a)–(e) | Đạt |
| 4 | Ngón thứ hai, huỷ không để phiên / bảng treo | Lớp chặn chạm trên cùng khi `dragging`; `finally` của cử chỉ: `cancel()` nếu chưa thả, `closeAll()` nếu đã bắt đầu; Home → `closeIfOpen` → huỷ; chủ dự án kiểm (g) sau bản sửa | Đạt |
| 5 | Id tạm thay bằng id thật không thu rồi phình | Chủ dự án kiểm (a), không báo hiện tượng thu/phình | Đạt |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `plans/PG-parity-gaps.md`: tick F2.
- `lessons/drag-and-layout.md`: bài học PG2; `lessons/README.md`: số mục.
- `Launcher-Features.md` dòng 13: ghi chỗ kéo thẻ trong bản Compose (khung xem trước ở trang chi tiết).
- `project_structure.md`, `room-schema.md`, `config-schema.md`: không đổi (không file mới ngoài plan, không đổi schema hay khóa config).
