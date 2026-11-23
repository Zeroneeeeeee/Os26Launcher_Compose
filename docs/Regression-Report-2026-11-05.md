# Báo cáo hồi quy — 2026-11-05

**Bản kiểm:** `main` sau đợt 1 (GP, LC, G2c, GS, M8d) cộng nhánh dọn mã `chore/cl-cleanup-unused` (`717a69e`, PR #13), APK debug.
**Máy:** Vsmart Live 4 (Android 11, mức kính Ép cao) và Oppo A3x (Android 14, mức kính Ép cao, 345 app). Hai máy đều không đặt Os26Launcher làm launcher mặc định.
**Cách chạy:** script tự động trên máy thật. Mỗi lần chạm đều kiểm Os26Launcher đang ở trước, kết quả đọc từ giao diện và từ DB. Dữ liệu sao lưu trước khi chạy, trả lại sau khi chạy và đã so khớp với bản sao lưu.

## Kết luận

- **36 mục kiểm trên mỗi máy.** Live 4: 35 đạt, 1 bỏ qua. Oppo: 34 đạt, 1 lỗi, 1 bỏ qua. Không có crash, bố cục không có ô chồng hay ô ngoài lưới.
- **1 lỗi thật, mức trung bình:** B1, bấm widget Đồng hồ trên Oppo hiện hộp chọn app.
- Hai mục trên Live 4 (G2, N2) lúc đầu không đạt nhưng là do script, chạy lại đã đạt. Xem mục "Không phải lỗi".

## Lỗi

### B1. Bấm widget Đồng hồ mở hộp chọn app thay vì app Đồng hồ (Oppo)

- **Mức:** trung bình. Không crash, không mất dữ liệu; người dùng phải chọn thêm một bước.
- **Máy:** Oppo A3x. Live 4 mở đúng app Đồng hồ.
- **Cách tái hiện:** thêm widget Đồng hồ, thoát edit mode, bấm widget.
- **Kết quả:** hiện `ResolverActivity` của hệ thống. Mong đợi: mở thẳng app Đồng hồ của máy.
- **Nguyên nhân (đo được):** `AlarmClock.ACTION_SHOW_ALARMS` có 3 trình xử lý trên máy này: `com.coloros.alarmclock`, `com.babydola.launcherios`, `org.fossify.clock`.
- **Hướng sửa (đề xuất, chưa làm):** dùng lại cách của M8d với mục Cài đặt: chỉ nhận trình xử lý hệ thống và mở đúng component; không có thì mới dùng danh sách app Đồng hồ đã biết. Sửa ở `LauncherAppsDataSource.openClock`. Nên kiểm luôn widget Lịch trên máy có nhiều app lịch.

## Kết quả chi tiết

| Mã | Tính năng | Live 4 | Oppo | Ghi chú |
|---|---|---|---|---|
| A1 | Khởi động: có lưới, pill, dock | Đạt | Đạt | 22 và 28 ô |
| A2 | Cold start: thời gian mở cửa sổ | Đạt | Đạt | 1093 ms và 1162 ms |
| B1 | Vuốt đổi trang và vuốt về | Đạt | Đạt | |
| C1 | Tìm kiếm: mở bằng pill | Đạt | Đạt | |
| C2 | Tìm kiếm: vuốt xuống mở, Back đóng | Đạt | Đạt | |
| C3 | Tìm kiếm: có kết quả và không có kết quả | Đạt | Đạt | |
| C4 | Tìm kiếm: khớp không dấu tiếng Việt | Đạt | Đạt | |
| C5 | Tìm kiếm: mục Cài đặt mở đúng màn Wi-Fi | Đạt | Đạt | Không hiện hộp chọn trên Oppo |
| C6 | Tìm kiếm: mở app, hiện trong Gần đây | Đạt | Đạt | |
| D1 | Nhấn giữ icon hiện menu | Đạt | Đạt | |
| E1 | Edit mode: nhấn giữ thư mục vào, Back ra | Đạt | Đạt | |
| E2 | Edit mode: nút Xong thoát | Đạt | Đạt | |
| E3 | Edit mode: bấm khoảng trống thoát | Bỏ qua | Bỏ qua | Đã kiểm ở GP |
| F1 | Thư mục: mở và đóng panel | Đạt | Đạt | |
| G1 | Kéo icon sang ô trống, lưu DB | Đạt | Đạt | |
| G2 | Kéo app lên app tạo thư mục | Đạt | Đạt | Live 4 đạt 3/3 khi chạy lại, xem mục dưới |
| H1 | Dấu "−" ẩn app, dồn ô | Đạt | Đạt | Không có ô chồng |
| I1 | App Library: mở và panel nhóm | Đạt | Đạt | |
| I2 | App Library: thanh tìm kiếm mở danh sách | Đạt | Đạt | |
| I3 | App Library: đóng về Home | Đạt | Đạt | |
| J1 | Widget: mở sheet, bấm thẻ thêm widget | Đạt | Đạt | |
| J2 | Widget: bấm mở app Đồng hồ | Đạt | **Lỗi** | B1 |
| J3 | Widget: "−" hỏi xác nhận, Xóa gỡ widget | Đạt | Đạt | |
| K1 | Cài đặt: mở từ edit mode, đủ hàng | Đạt | Đạt | |
| K2 | Cài đặt: Ứng dụng đã ẩn mở được | Đạt | Đạt | |
| K3 | Cài đặt: công tắc nhãn | Đạt | Đạt | Chỉ kiểm bấm được, không kiểm nhãn trên Home |
| K4 | Cài đặt: Kiểu icon; Mức kính có Tùy chỉnh | Đạt | Đạt | |
| K5 | Cài đặt: khối Tìm kiếm đủ hàng | Đạt | Đạt | |
| L1 | Đổi theme dựng lại Home, dock còn kích thước | Đạt | Đạt | |
| M1 | Toàn vẹn bố cục sau bài kiểm | Đạt | Đạt | 0 ô chồng, 0 ô ngoài lưới |
| M2 | Không có crash | Đạt | Đạt | |
| N1 | Nhấn giữ khoảng trống vào edit mode | Đạt | Đạt | |
| N2 | Kéo widget từ sheet vào lưới | Đạt | Đạt | Live 4 đạt khi thả ở vùng không có widget, xem mục dưới |
| N3 | Thả widget từ sheet lên dock: không thêm | Đạt | Đạt | |
| N4 | Library: bấm icon lớn mở app | Đạt | Đạt | |
| N5 | Không có crash ở phần bổ sung | Đạt | Đạt | |

## Không phải lỗi

- **G2, Live 4, 4 lần đầu không tạo thư mục:** icon đổi chỗ thay vì gộp. `input motionevent` chậm vài trăm ms mỗi lệnh trên máy này, nên ngón tay giả dừng hơn 500 ms ở mép ô và kích hoạt đẩy-dồn dính của AN. Di thẳng tới tâm icon thì đạt 3/3. Chủ dự án cũng đã kiểm tay đạt.
- **N2, Live 4, lần đầu không thêm widget:** vị trí thả nằm trên widget Đồng hồ có sẵn nên bị từ chối, đúng thiết kế. Thả ở vùng khác thì thêm được.
- **Lỗi `OSError` lần chạy đầu:** do script mở lại file DB trên Windows, không liên quan app. Đã sửa script và chạy lại.

## Lint (`lintDebug`)

- `MissingPermission` ở `SystemWallpaperSource.kt:28`: báo lỗi nhưng code đã bắt `SecurityException` và trả `null`. Không phải lỗi chạy; đọc wallpaper hệ thống có thể không lấy được trên Android mới.
- `CutPasteId` ở `CategoryPageView.kt:234`: tra cùng một view hai lần, vô hại.
- `ExifInterface` ở `PickedImageSource.kt`: nên đổi sang bản AndroidX khi có dịp.
- Còn lại là gợi ý kiểu mã (`UseKtx`, `Overdraw`, `ClickableViewAccessibility`, `DrawAllocation`), chưa xử lý.

## Đã dọn

- `git worktree`: chỉ còn thư mục chính. Worktree và nhánh của GP, LC, G2c, GS, M8d và nhánh dọn mã đã xóa cả ở local và GitHub. Không có stash. Thư mục làm việc sạch.
- PR #13 (đã merge) bỏ `HomeViewModel.onImagePicked`, `HomeViewModel.currentGlassLevelOverride`, `widget_picker_corner`, `widget_add_failed_description`. Mỗi mục đã `grep` trong `app/src`.
- **Cố ý giữ:** `home_edit_pill_height`, plan TL1 ghi là giữ để tài liệu hóa.

## Vấn đề đã biết (không mới)

- **Khởi động Oppo:** icon cuối vẽ xong khoảng 2.4 giây từ lúc tiến trình bắt đầu, chủ dự án đo bằng mắt khoảng 3 giây. Việc tiếp theo: IC (cache icon).
- **Panel nhóm trên Live 4:** chưa đạt độ mượt của panel thư mục, nguyên nhân chưa rõ; chủ dự án chấp nhận.
- **Icon hiện trễ:** lần mở panel nhóm đầu tiên sau khởi động nguội trên Live 4, nhãn hiện trước icon.
- **Tìm kiếm:** p95 mỗi phím 7–9 ms, có đột biến 22–43 ms, chưa đạt mục tiêu 4 ms.
- **G2c:** kéo qua từ 5 trang trở lên rồi bị từ chối có thể để ô gốc ẩn tới lần bind sau.
- **GS:** lần đầu vào Tùy chỉnh từ Ép thấp trên API 33+ với hơn 6 thư mục một trang, thư mục thứ 7 trở đi đổi từ phẳng sang kính.
- **Re-flow:** đổi cỡ lưới qua lại không trả widget về đúng ô cũ, đúng thiết kế M8a.

## Chưa kiểm trong bài này

**Đã kiểm trong ngày trên bản trước đó, chưa chạy lại trên bản cuối:** mức kính Tùy chỉnh và kính thật (Oppo, Live 4), đổi cỡ lưới có widget, shortcut trong tìm kiếm khi là launcher mặc định, xóa lịch sử mở app, ngắt giữa lúc kéo widget, Home trống.

**Chưa kiểm:**
- Đổi ảnh nền qua bộ chọn ảnh của hệ thống, đặt launcher mặc định qua hộp thoại hệ thống.
- Gỡ và cài app thật (`removePackages`), work profile.
- Split-screen, TalkBack, phím nguồn hoặc cuộc gọi lúc đang kéo.
- Kéo icon vào dock và ra khỏi dock, kéo qua nhiều trang, kéo app ra khỏi thư mục, đổi tên thư mục, bỏ ẩn app.
- Áp một kiểu icon cụ thể và xem kết quả.
- Múi giờ, 12/24 giờ, sang ngày mới trên widget.
