# B13 — Widget của app khác vừa khít ô (bỏ padding mặc định, thu nhỏ khi tràn)

> Mục backlog: B13 "Widget của app khác lấy kích cỡ sai" (chủ dự án 2026-11-26). Bắt đầu 2026-11-27. Trạng thái: **Xong 2026-11-27** (chủ dự án kiểm tay, không còn lỗi).

## Hiện trạng đo được (2026-11-27, Vsmart Live 4 API 30, density 3)

Widget Pulsar `com.rhmsoft.play.MusicWidget`, cỡ 4×1. Provider khai min 250×40dp, minResize 180×40dp, `resizeMode=3`, không có `targetCell`.

| Tầng | Số đo | Kết luận |
|---|---|---|
| Chọn ô (`AppWidgetSizePlanner`) | ô 73,3×77dp, gutter 8/4dp → 4×1 | Đúng (`ceil(258/81,3)=4`, `ceil(44/81)=1`), Launcher3 cũng xếp 1 hàng |
| Host view | 952×231px = 317×77dp, padding **24px = 8dp mỗi cạnh** | Padding mặc định do `AppWidgetHostView.setAppWidget` tự đặt |
| Nội dung | 904×183px = 301×61dp | Hàng nút điều khiển ở đáy bị cắt (ảnh) |
| Options hệ thống giữ | min = max = 301×61dp | `updateAppWidgetSize` tự trừ padding mặc định khỏi cỡ ta đưa |

Sau Task 1: nội dung phủ 317×77dp, provider nhận 317×77, nhưng vẫn cắt một ít ở đáy: layout thật của Pulsar cao hơn 77dp, còn hàng lưới kiểu iOS của ta thấp hơn hàng Launcher3 (`minHeight` 40dp của provider thấp hơn layout thật).

## Quyết định

- D1 (chủ dự án 2026-11-27): thẻ kính của ô là khung, widget phủ sát thẻ (bỏ padding mặc định).
- D2 (chủ dự án 2026-11-27): một cách chung cho **mọi** widget (không kiểm hết được từng loại): widget tràn ô thì thu đều theo tỉ lệ cho vừa, không cắt. Không đổi chiều cao hàng lưới.
- D3: provider vẫn nhận cỡ ô thật (317×77), không nhận cỡ hộp ảo: provider có layout theo cỡ (API 31+) vẫn chọn theo ô thật.

## Task

### Task 1 — bỏ padding mặc định, bù cỡ báo cho provider (Xong 2026-11-27)

- `core/appwidget/OsAppWidgetHostView.kt`: `setAppWidget` gọi `super` rồi `setPadding(0, 0, 0, 0)`.
- `ui/widget/host/HostedWidgetViews.kt` `onSized`: cộng `getDefaultPaddingForWidget` (làm tròn như AOSP) vào cỡ dp trước `updateAppWidgetSize`.
- Đo được trên Live 4: `padPx=0,0,0,0`, `childPx=952x231`, `opts min=max=317x77`.
- Chưa đo số: Oppo API 34 (bản `updateAppWidgetSize(Bundle, List<SizeF>)`), log gửi lại chỉ có Live 4. Nếu sau này thấy widget trên API 31+ nhận cỡ dư 16dp (bản S+ không trừ padding) thì bỏ phần bù trên S+.

### Task 2 — thu nhỏ cho vừa ô (Xong 2026-11-27)

`OsAppWidgetHostView.onLayout`:
1. Gọi `super.onLayout` (API 31+: chọn `RemoteViews` theo cỡ, thay view lỗi khi layout ném lỗi).
2. Đặt lại con trong **hộp ảo** `ô / s` theo luật `FrameLayout` (lề, `layout_gravity`), bắt đầu `s = 1`.
3. Đo vùng phủ = hợp khung của mọi view con cháu đang `VISIBLE` (không đi vào `AdapterView`: ListView / GridView / StackView tràn là do cuộn). Tràn quá 1dp thì `s *= hộp / vùng phủ`, lặp tối đa 3 lần, không dưới `MIN_SCALE = 0.6`.
4. `scaleX = scaleY = s`, pivot ở góc trên trái của ô. Không tràn thì `s = 1`, bố trí như `FrameLayout` thường.

Chạm: `ViewGroup` tự biến đổi toạ độ chạm theo `scale` của con. Ảnh kéo (`HomeAppWidgetDragPreview`) chụp `View.draw` nên có cả phần thu.

Giới hạn đã biết:
- Chữ bị cắt bên trong một `TextView` cao cố định không đo được (khung `TextView` không tràn).
- Dưới 0.6 vẫn cắt phần còn lại.
- Mỗi lần layout chạy lại từ `s = 1` (tối đa 4 lần đo / đặt cho widget tràn): widget cập nhật dày (đồng hồ giây, tiến trình nhạc) cần xem log có dày không.

### Task 3 — gỡ log tạm, bài học, đóng mục (Xong 2026-11-27)

- Gỡ log tag `B13` ở `AppWidgetRepositoryImpl`, `HomeWidgetPicker`, `AppWidgetFlowController`, `HostedWidgetViews`, `OsAppWidgetHostView` (3 file đầu về nguyên như trước B13). Bản gỡ log chưa build lại trong VM: chủ dự án build trước khi push.
- Bài học vào `docs/lessons/widgets.md`; đánh dấu Xong ở plan, `BACKLOG.md`, `README.md`.

## Kiểm (chủ dự án)

1. `.\gradlew.bat installDebug`, `adb logcat -c`, mở Home có widget Pulsar, thêm 2–3 widget app khác (Lịch Vsmart, Chrome, Gmail, YouTube...).
2. `adb logcat -d -v time -s B13 > b13.txt`. Dòng `fit` cho biết `scale`, số lần lặp, `ratio` (< 1 = vẫn cắt).
3. Nhìn: Pulsar hiện đủ hàng nút; widget vừa ô không đổi; bấm nút / mở app trên widget thu nhỏ trúng chỗ; kéo widget thu nhỏ thì ảnh kéo đúng.
4. Lặp trên Oppo API 34: thêm kiểm `sizes=[…]` của dòng `laidOut`.

## Nhật ký

- 2026-11-27: đặt log `B13` dọc luồng (planner → options lúc gắn → cỡ đẩy → cỡ nội dung thật). Log + ảnh Live 4 chỉ ra padding mặc định (Task 1), rồi hàng lưới thấp hơn layout thật của provider (Task 2).
- 2026-11-27: Task 1 kiểm trên Live 4: hết viền đôi, log khớp (`padPx=0`, nội dung 317×77, options 317×77), còn cắt nhẹ ở đáy → Task 2.
- 2026-11-27: chủ dự án kiểm Task 2: "không còn vấn đề gì nữa". Gỡ log, ghi bài học (`lessons/widgets.md`, mục B13), đóng mục.
