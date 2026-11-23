# G3b — Widget ảnh (TT#15; một ảnh hoặc bộ ảnh đổi mỗi giờ, chọn ảnh + cắt) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ để so đường mở bộ chọn ảnh và kính; xem Task 2 và Task 5). Lệnh viết cho CMD/PowerShell. Máy thật dùng chung với việc khác của chủ dự án: xác nhận máy rảnh trước mỗi lần cài hoặc chạy script. Agent không tự cài APK, không chạm máy. **Không bao giờ ghi vào log, plan hay commit: URI ảnh, đường dẫn file ảnh, tên file gốc, nội dung ảnh.**

**Nhánh:** `feature/g3b-photo-widget` · **Worktree:** `E:\SourceTree\IosLauncher-wt\G3b-photo-widget` · **PR:** #57 (draft) · **Bắt đầu:** 2026-11-13 · **Plan duyệt:** 2026-11-13 (điều phối; câu hỏi 1–3 gửi chủ dự án theo lô, mặc định = khuyến nghị)
**Trạng thái:** Sẵn sàng review (2026-11-16)
> Mọi trường hợp chính đã qua trên Live 4 (điều phối + chủ dự án kiểm tay 2026-11-16, không thấy lỗi). Chủ dự án quyết 2026-11-13 bỏ các phép đo còn lại của Task 5 (bộ nhớ, Janky, xoay ảnh theo đồng hồ máy) vì các trường hợp chính đã qua; nghi vấn chạm widget trong edit mode không tái hiện được. Log tạm G3BMEAS đã gỡ ở 814ad83. Task 5 bước 2, 3 và bài kiểm Oppo không thực hiện.
**Khóa:** **không khóa schema Room** · không cần Oppo để làm; cần Oppo ở bài kiểm cuối (đường mở bộ chọn ảnh API 34, kính quanh ảnh) · **không sửa `HomeActivity`/`activity_home.xml`** (G3c)
**Mức rủi ro:** trung bình (lớp widget chỉ thêm; phần mới là Activity riêng + màn cắt ảnh có cử chỉ). Model/effort đề xuất: Sonnet 5.5 / **high** (cử chỉ kéo/phóng ảnh trong màn cắt, bộ nhớ bitmap, chạm lớp widget; mục 2.1 của workflow).

**Goal:** Widget **Ảnh** cỡ 2×2 và 4×2 trong bảng "Tiện ích". Một widget hiện **một ảnh** hoặc **một bộ ảnh đổi mỗi giờ đúng đầu giờ** (chỉ khi Home đang hiện, không việc nền). Ảnh lấy từ bộ chọn ảnh của hệ thống (không xin quyền đọc ảnh); app giữ bản sao thu nhỏ riêng trong bộ nhớ app, không sao lưu. Sau khi chọn, người dùng **kéo/phóng để chọn vùng cắt** lấp đầy widget (không bộ lọc); mỗi ảnh trong bộ có vùng cắt riêng theo tỉ lệ widget. Bấm widget mở màn "Ảnh của tiện ích" để thêm, cắt lại, xóa ảnh.
**Architecture:** (1) Domain: `WidgetKind.PHOTO`, mô hình `PhotoWidgetConfig` (danh sách ảnh + vùng cắt chuẩn hóa), `PhotoWidgetRepository` (Flow + thao tác). (2) Data: `PhotoWidgetStore` (`photo_widgets.json`, `noBackupFilesDir`, `AtomicFile`, cùng kiểu `WeatherPlacesStore`) + `PhotoFileStore` (nhập ảnh: đọc EXIF, thu về ≤ 2048 px cạnh dài, nén JPEG, ghi `no_backup/photo_widgets/<itemId>/<photoId>.jpg`). (3) UI widget: `PhotoWidgetRenderer` (vẽ bằng `BitmapShader` + bo góc), `PhotoWidgetHub` (`object` nối repository ↔ widget như `WeatherWidgetHub`), `PhotoBitmapCache` (LRU theo byte, giải mã **ngoài luồng chính** bằng `BitmapRegionDecoder` ở đúng cỡ widget). (4) UI mới: `PhotoWidgetActivity` riêng (màn danh sách + bộ chọn ảnh + màn cắt `PhotoCropView`), mở từ lần bấm widget; **Home biết kết quả qua `PhotoWidgetRepository.widgets` (Flow) → `PhotoWidgetHub` → `WidgetTicker.tickNow()`**, không qua `onActivityResult`.
**Tech Stack:** không dependency mới (`activity-ktx` 1.13.0 đã có `PickMultipleVisualMedia`; `android.media.ExifInterface` của nền tảng như FX1). Không quyền mới. Manifest thêm đúng một `<activity>`.
**Spec:** TT#15 trong `Launcher-Features.md` (đã tách khỏi G3f ngày 2026-11-10; **bỏ bố cục Mosaic** theo chủ dự án 2026-11-13) + `plans/G2c-clock-calendar-widgets.md`, `plans/WX-weather-cities.md` và knowledgement của hai mốc + `knowledgements/FX1-wallpaper-safety.md` + `lessons/widgets.md`, `glass.md`.

---

## Nguồn tài liệu đã đọc cho nhánh này

| Việc | Điều đọc được | Nhãn |
|---|---|---|
| Bộ chọn ảnh | `PickVisualMedia`/`PickMultipleVisualMedia` (activity ≥ 1.7.0) dùng bộ chọn hệ thống khi có; không có thì **tự gọi `ACTION_OPEN_DOCUMENT`** (API ≥ 19). Bộ chọn hệ thống có trên Android 11 (API 30) trở lên **nếu máy nhận Modular System Components qua Google System Updates**; máy cũ hơn, hoặc Android Go 11/12, có Google Play services thì dùng bản backport (cài tự động khi khai `ModuleDependencies` trong manifest; **dự án chưa khai**). Khi rơi về `ACTION_OPEN_DOCUMENT`, **giới hạn số ảnh `maxItems` bị bỏ qua** | đọc tài liệu (https://developer.android.com/training/data-storage/shared/photopicker, 2026-11-13) |
| Quyền truy cập URI | Hệ thống cấp quyền đọc cho tới khi máy khởi động lại hoặc app dừng; muốn lâu hơn phải `takePersistableUriPermission`. Quyết định: **không giữ URI**, chép ngay sang bản sao riêng | đọc tài liệu (cùng trang) |
| Giới hạn số ảnh | Nền tảng giới hạn số ảnh tối đa qua `MediaStore.getPickImagesMaxLimit()`; nơi không có bộ chọn hệ thống thì giới hạn này bị bỏ qua. **Hệ quả: app phải tự cắt danh sách trả về xuống đúng số còn lại** | đọc tài liệu (cùng trang) |
| Đường bộ chọn thật sự dùng trên Live 4 (API 30) so với Oppo (API 34) | **Chưa biết.** Live 4 có thể dùng bộ chọn hệ thống, bản backport, hoặc rơi về `ACTION_OPEN_DOCUMENT`. Hành vi chọn một ảnh qua `PickVisualMedia` đã chạy ở Cài đặt → Ảnh nền (M8a) nhưng chưa ai ghi lại đường nào; **chọn nhiều ảnh chưa ai thử** | suy luận chưa kiểm chứng → Task 2 đo |
| Bitmap ảnh nền (FX1) | `BitmapRegionDecoder` giải mã đúng vùng mà không cấp phát cả ảnh (JPEG 12000×3000 ~120 ms, bản debug, Live 4); `inSampleSize` chỉ lũy thừa 2 nên phải thu nốt; trần giải mã `MAX_DECODE_PIXELS` 25 MP; file lưu ≤ 16 MP; `BitmapScaler`/`ExifReader` dùng lại được nguyên văn | đo được + đọc code (`core/bitmap`, `knowledgements/FX1-wallpaper-safety.md`) |
| Cache RAM không phải nơi giữ bộ nhớ chính | Chủ sở hữu thật của bitmap là view; xóa cache chỉ giảm ~1 MB khi view giữ cùng bitmap (FX5). Hệ quả: cache của widget ảnh phải **nhỏ**, và renderer giữ đúng một bitmap hiện tại | đo được (`lessons/startup-performance.md`) |
| EXIF | Ghi lại bằng `Bitmap.compress` thì **không mang theo EXIF** (kể cả vị trí GPS). Khác ảnh nền (D9 chép nguyên byte JPEG): ảnh widget **không** chép nguyên byte | suy luận từ cách `Bitmap.compress` hoạt động, Task 1 kiểm bằng cách đọc EXIF file đã lưu |

## Hiện trạng đọc từ code (2026-11-13, `main` @ d920c5a)

| # | Chỗ | Hiện trạng | Hệ quả cho G3b |
|---|---|---|---|
| 1 | `domain/model/WidgetKind` | enum `CLOCK`, `CALENDAR`, `WEATHER`; `id` chuỗi lưu ở cột `className` của hàng `itemType = 4`; `sizes` mỗi loại; `fromId` bỏ mã lạ | Thêm `PHOTO("photo", 2×2, 4×2)`, không đổi schema |
| 2 | `ui/widget/WidgetRenderer` | `prepare(...)` mỗi phút (và khi cỡ đổi), `draw` không cấp phát, `bindItem(itemId)` ở **mỗi** lần bind (ô tái dùng, WX); `WidgetRenderers.create` là `when` trên `WidgetKind` | Thêm nhánh `PHOTO`; renderer biết `itemId` |
| 3 | `IconCellView.drawWidget` | Vẽ nền (kính tại chỗ hoặc phẳng, bo `min(w,h)*0.12`) rồi `renderer.draw(canvas)`. Cỡ widget = `spanX*cellWidth + (spanX-1)*gutterX` × tương tự, **cao hơn tỉ lệ ô vì `cellHeight` gồm chỗ nhãn**: tỉ lệ thật không đúng 1:1 / 2:1 và đổi theo cỡ lưới và máy | Vùng cắt phải lưu **chuẩn hóa** và chịu lệch tỉ lệ nhỏ (D2); màn cắt lấy tỉ lệ thật từ widget đã vẽ |
| 4 | `WidgetTicker` | Nhịp ở ranh giới phút, chỉ khi có view widget đang gắn **và** cửa sổ hiện; `tickNow()` làm mới ngay; có `TIME_SET`/`TIMEZONE_CHANGED`; không có widget hiện thì không `Handler`/receiver | Đủ để đổi ảnh đầu giờ mà **không** việc nền: ảnh suy từ giờ hiện tại ở mỗi `prepare` |
| 5 | `WeatherWidgetHub` (WX) | `object` nối repository ↔ widget, cổng "đã nạp", `tappedWidgetId` truyền id lần bấm qua `object` | Mẫu cho `PhotoWidgetHub` |
| 6 | Bấm widget | `IconGridView` (dòng 526): `setOnClickListener { if (!editMode) onWidgetClick(kind, itemId) }` → `HomePagerAdapter` → `HomePagerController` (nhánh `WEATHER` đặt `tappedWidgetId` rồi gọi `onWeatherClick`; còn lại `viewModel.onWidgetTapped(kind)`). **Trong edit mode nuốt chạm**; nhấn giữ widget ngoài edit mode = vào edit mode | Bấm ngoài edit mode → thêm một nhánh ở `HomePagerController` (D5) |
| 7 | `HomeViewModel.onWidgetTapped` (dòng 745) | `when (kind)` **biểu thức, vét cạn** trên `WidgetKind` | **Thêm `PHOTO` vào enum làm hỏng biên dịch tới khi thêm một nhánh ở đây.** `HomeViewModel.kt` là file độc quyền (workflow 5.2) → xem "Vùng file" và mục "Mâu thuẫn với brief" |
| 8 | `WidgetPickerController` | Duyệt `WidgetKind.entries`, mỗi loại một hàng thẻ (mỗi cỡ một thẻ, bản xem trước bằng chính renderer với `sample = true`); `kindNameRes` là `when` vét cạn; kéo thẻ thả lên lưới đã chạy cho mọi loại (ảnh kéo = chính widget `itemId = 0`) | Thêm một dòng ở `kindNameRes`; hai thẻ (2×2, 4×2) hiện tự động. Bản xem trước/ảnh kéo (`itemId = 0`, `sample = true`) **không đọc kho ảnh** |
| 9 | `RemoveWidgetUseCase` | `layoutRepository.removeItem` rồi `weatherPlaces.forgetWidget(itemId)` | Thêm một dòng `photoWidgets.forget(itemId)` (xóa file thật) |
| 10 | `launcher_items.id` | `AUTOINCREMENT`, mọi đường ghi đã đọc ở WX D1: không đường nào xóa rồi tạo lại hàng widget với id mới | Khóa theo `itemId` an toàn, **không cần đọc lại**; chỉ có hai nơi tạo widget (`addWidget`, `addWidgetAt`) |
| 11 | `AndroidManifest.xml` | `allowBackup="true"`; `backup_rules.xml`/`data_extraction_rules.xml` còn mẫu; `SettingsActivity` là Activity phụ mẫu (`exported=false`, `nosensor`); `HomeActivity` `singleTask` + `clearTaskOnLaunch` | `noBackupFilesDir` tự nằm ngoài sao lưu (như WX). Activity mới theo mẫu `SettingsActivity` |
| 12 | `PickedImageSource` (ảnh nền) | Đã dùng `PickVisualMedia` + chép sang `filesDir`; EXIF xoay xử lý; chép nguyên byte JPEG khi không xoay (D9) | Tái dùng `ExifReader`/`BitmapScaler`; **không** chép nguyên byte (D2, riêng tư) |

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (ràng buộc thật)

1. **URI của bộ chọn ảnh chỉ tạm** (đọc tài liệu): phải chép ngay; sửa lại vùng cắt sau này cần ảnh nguồn còn đó chứ không chỉ kết quả đã cắt.
2. **Mỗi widget cần dữ liệu riêng và nặng** (vài MB ảnh) trong khi `launcher_items` không có chỗ cấu hình riêng; schema khóa (brief, "không đổi Room").
3. **Bộ nhớ:** nhiều widget ảnh cùng hiện (một trang có thể 6–8 ô). 4×2 trên màn 1080 px rộng ≈ 1000×480 px ≈ 1,9 MB ARGB_8888; 2×2 ≈ 0,9 MB (tính từ công thức `GridMetrics`, chưa đo). Bài học FX1/FX5: bitmap nặng giữ ở view, không nhân bản ở cache.
4. **Không việc nền** (chủ dự án): đổi ảnh đầu giờ chỉ khi Home hiện. `WidgetTicker` đã có đúng nhịp đó.
5. **Lớp widget và file độc quyền:** G3c đang cấu trúc lại `IconGridView/IconCellView/WidgetPickerController` và độc quyền `HomeActivity`; G3b merge trước nên chỉ **thêm**.
6. **Riêng tư:** ảnh cá nhân; không log URI/đường dẫn; EXIF (vị trí) không được mang theo; không vào sao lưu đám mây.
7. **Giải mã không được chạy trên luồng chính** (`prepare` gọi trên luồng chính mỗi phút, và mỗi lần `tickNow`).

### D1. Ảnh và cấu hình lưu ở đâu

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Cột Room mới (`config`) + file ảnh | Cấu hình đi cùng hàng widget | Đồng bộ với bố cục | **Khóa schema + migration + `app/schemas/2.json` + file độc quyền** (`LauncherItemDao`, `data/layout/*`); brief cấm đổi schema; ảnh vẫn phải nằm ngoài Room | Không |
| B. `filesDir` (có sao lưu tự động) | Như ảnh nền | Đơn giản | `allowBackup="true"` đẩy ảnh cá nhân lên sao lưu đám mây, lệch ý "không sao lưu" của chủ dự án; khôi phục sang máy khác có ảnh nhưng hàng widget có thể khác id | Không |
| **C. `noBackupFilesDir`: `photo_widgets.json` (cấu hình) + `photo_widgets/<itemId>/<photoId>.jpg`; ánh xạ theo `itemId`** | Cùng mẫu WX D1 (`WeatherPlacesStore`) | **Không khóa schema, không file độc quyền**; không sao lưu (đúng ý chủ dự án); id không bao giờ dùng lại nên khóa an toàn; dọn gọn bằng xóa thư mục | Khôi phục máy: bố cục khôi phục nhưng ảnh không → widget về trạng thái "chưa có ảnh" (an toàn); phải tự dọn file khi xóa widget gián tiếp (D8) | **Chọn** |

**Vì sao C:** khớp tiền lệ WX D1 đã được duyệt và đã kiểm tay; ảnh nặng nên dọn **file thật** là bắt buộc (khác WX chỉ dọn một dòng JSON). **Đánh đổi:** cấu hình không theo bản sao lưu bố cục (chấp nhận, như WX). Khi nhiều loại widget cần cấu hình thì xét cột `config` một lần cho tất cả (đã ghi ở bài học WX).

### D2. Lưu ảnh nguồn + vùng cắt, hay chỉ lưu ảnh đã cắt

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ lưu ảnh đã cắt ở cỡ cố định (ví dụ 2×2 → 720², 4×2 → 1440×720) | Cắt ngay khi người dùng bấm Xong | Giải mã lúc vẽ rẻ nhất; đơn giản | **Không sửa lại vùng cắt được** (mất phần ngoài vùng; muốn lấy lại phải chọn ảnh lại từ bộ chọn); cỡ cố định không khớp mọi màn (độ nét/tốn bộ nhớ); tỉ lệ widget thật lệch tỉ lệ danh nghĩa (hiện trạng 3) | Không |
| **B. Lưu một bản sao nguồn thu nhỏ (cạnh dài ≤ 2048 px, JPEG ~90, đã xoay đúng chiều, không EXIF) + vùng cắt chuẩn hóa `(l,t,r,b)` ∈ [0,1] + tỉ lệ `aspect` lúc cắt; vẽ bằng giải mã vùng ở đúng cỡ widget** | Giống FX1 (file giữ toàn ảnh, giải mã chỉ vùng cần) | **Cắt lại được bất cứ lúc nào** không cần chọn lại; mỗi ảnh có vùng cắt riêng; cỡ giải mã theo widget thật (nét vừa đủ, không thừa RAM); lệch tỉ lệ nhỏ giữa lúc cắt và lúc vẽ xử lý bằng cách "phủ kín" (co cạnh dài của vùng quanh tâm) | Tốn đĩa ~0,3–0,8 MB/ảnh (ví dụ 10 ảnh ≈ 3–8 MB/widget, suy luận, đo ở Task 1); giải mã vùng mỗi lần đổi ảnh/đổi cỡ (làm nền, có cache) | **Chọn** |
| C. B + cache đĩa ảnh đã cắt | Thêm file đã cắt cạnh nguồn | Vẽ nhanh | Thêm một tầng cache cần vô hiệu hóa khi cắt lại/đổi cỡ lưới; chưa có số đo cho thấy cần | Không (xét lại nếu Task 5 đo giải mã vùng quá chậm) |

**Vì sao B:** yêu cầu "mỗi ảnh một vùng cắt, sửa sau" buộc phải giữ nguồn; hạ nguồn về 2048 px (≥ 1,9× cỡ widget 4×2 trên màn 1080 px, đủ nét kể cả khi phóng tối đa trong màn cắt) giữ đĩa nhỏ. **Không chép nguyên byte** như ảnh nền: bỏ EXIF/GPS và chặn ảnh lớn. **Đánh đổi:** xử lý lệch tỉ lệ: nếu tỉ lệ widget lúc vẽ lệch tỉ lệ lúc cắt thì vùng thật sự hiển thị co lại một chút quanh tâm (thường vài %, đo ở Task 2/5 trên Live 4 với 3 cỡ lưới).

### D3. Đổi ảnh đầu giờ và bộ nhớ

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Lưu chỉ số "ảnh hiện tại" + giờ đã đổi, đổi bằng bộ hẹn giờ | `Handler`/`AlarmManager`/WorkManager đầu giờ | Đổi chính xác kể cả khi Home ẩn | **Việc nền** (trái chốt chủ dự án); `AlarmManager` cần xử lý quyền chính xác và Doze | Không |
| **B. Không lưu trạng thái: ảnh hiện tại = `floorMod(giờTreoTường, n)` ở mỗi `prepare`** (`giờTreoTường` = số giờ từ epoch theo giờ địa phương, tính từ `ZonedDateTime` đã đưa vào `prepare`) | `WidgetTicker` đã gọi `prepare` mỗi phút khi Home hiện và ngay khi cửa sổ hiện lại; `TIME_SET`/đổi múi giờ làm mới ngay | **Không việc nền, không trạng thái cần lưu**; quay về Home sau 3 giờ thấy ảnh đúng giờ; nhiều widget cùng bộ đổi cùng lúc | Thêm/xóa ảnh làm chỉ số nhảy (chấp nhận); đổi múi giờ làm đổi ảnh (chấp nhận, đúng nghĩa "đầu giờ địa phương"); **không phát được hiệu ứng chuyển** nếu không so ảnh trước/sau (làm ở Task 5 nếu rẻ) | **Chọn** |
| C. Lưu chỉ số, tăng khi `prepare` thấy giờ mới | Có trạng thái | Chỉ số ổn định khi thêm ảnh | Phải ghi file mỗi giờ; phân xử khi nhiều widget; chạm đĩa trong luồng vẽ | Không |

**Bộ nhớ (mọi phương án giống nhau):** `PhotoBitmapCache` là `LruCache` theo byte, trần `min(24 MB, maxMemory/16)`, khóa `(photoId, cropVersion, w, h)`; renderer **giữ một tham chiếu mạnh** tới bitmap đang vẽ nên cache chỉ phục vụ việc ô được tái dùng và quay lại trang; trang ẩn/ô tách khỏi cửa sổ thả tham chiếu (cache tự đẩy bớt); `onTrimMemory` ≥ `TRIM_MEMORY_BACKGROUND` gọi `evictAll`. Giải mã chạy trên một `Executor` đơn luồng riêng (nối tiếp: không ba widget cùng cấp phát 2 MB một lúc); `prepare` chỉ tra cache: có thì dùng, chưa có thì vẽ ảnh cũ (hoặc nền trống) và xếp một lệnh giải mã; xong thì `WidgetTicker.tickNow()`. `prepare` không bao giờ chặn.

### D4. Màn cắt ảnh

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `PhotoCropView` tự viết** | Một `View`: khung cắt cố định theo tỉ lệ widget, **ảnh** kéo/phóng bên dưới (`ScaleGestureDetector` + kéo một ngón), kẹp để khung luôn nằm trong ảnh, tỷ lệ phóng tối thiểu = vừa phủ khung | Không dependency; kiểm soát tỉ lệ thật, vùng cắt chuẩn hóa đúng dạng D2 cần; ~250 dòng | Phải tự xử lý cử chỉ (cử chỉ mới → Task bắt buộc kiểm tay) | **Chọn** |
| B. Thư viện cắt (ví dụ uCrop) | Thêm dependency | Có sẵn | Dependency mới + phải kiểm giấy phép/phiên bản (AGENTS.md: không đoán version); UI của họ khó khớp giao diện Os26, đòi quyền ghi file ra ngoài; xung đột với D2 (muốn vùng chuẩn hóa chứ không phải file đã cắt) | Không |
| C. Intent cắt của hệ thống (`com.android.camera.action.CROP`) | Gọi app ngoài | Không code UI | Không còn trên nhiều ROM (Android 11+ gần như không có); không điều khiển được tỉ lệ/đầu ra | Không |

### D5. Người dùng sửa ảnh của widget về sau thế nào (câu hỏi cho chủ dự án; có khuyến nghị)

Ràng buộc từ code: **nhấn giữ widget ngoài edit mode = vào edit mode** (`onFolderLongPress`); **trong edit mode chạm vào widget bị nuốt** (để không thoát edit mode); icon "−" là xóa. Widget **mới thêm chưa có ảnh** buộc phải có một cách chạm để chọn ảnh lần đầu.

| Phương án | Hành vi | Ưu | Nhược / rủi ro |
|---|---|---|---|
| **A. Bấm widget (ngoài edit mode) → mở màn "Ảnh của tiện ích" (thêm, cắt lại, xóa ảnh)** | Cùng mẫu WX D2-A (chủ dự án đã duyệt: bấm widget → bảng); widget trống: màn mở thẳng bộ chọn ảnh | **Chỉ một nhánh ở `HomePagerController` (cạnh nhánh `WEATHER`), không đổi `IconGridView`**; một cách duy nhất cho cả lần đầu và sửa; không phụ thuộc app Thư viện ảnh của ROM | Người dùng quen "bấm ảnh → mở Thư viện" (như iOS) không có đường đó |
| B. Bấm → mở app Thư viện ảnh; sửa ảnh chỉ trong edit mode | Giống iOS; sửa = vào edit mode, bấm widget | Hành vi quen thuộc | Mở app Thư viện khác nhau theo ROM (Live 4, Oppo: phải đo như B1 Đồng hồ/Lịch, có thể rơi vào hộp chọn ứng dụng); **chạm trong edit mode đang bị nuốt**: phải sửa `IconGridView` dòng 526 (file G3c cấu trúc lại) → xung đột với nhánh song song; widget trống vẫn cần đường riêng |
| C. Chỉ trong edit mode: bấm widget → màn sửa; ngoài edit mode: không làm gì | Đúng kiểu "Edit Widget" | Không tranh chấp với mở app | Phải vào edit mode chỉ để chọn ảnh lần đầu; vẫn phải sửa `IconGridView` như B |
| D. A, cộng nút "Mở Thư viện ảnh" trong màn sửa | A + đường tới Thư viện | Có cả hai | Thêm việc đo mở Thư viện theo ROM; để mốc sau nếu chủ dự án muốn |

**Khuyến nghị: A trong v1** (lý do: ít chạm lớp widget nhất, không tranh `IconGridView` với G3c, nhất quán với WX đã duyệt, và là đường duy nhất phải có anyway cho widget trống). **Câu hỏi 1.**

### D6. Home biết kết quả của Activity riêng thế nào

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. `startActivityForResult` / `registerForActivityResult` ở `HomeActivity` | Home nhận kết quả | Quen thuộc | **Sửa `HomeActivity`** (độc quyền G3c); Home có thể bị hủy khi Activity kia mở | Không |
| **B. Repository + Flow (như WX): Activity ghi qua `PhotoWidgetRepository`; `PhotoWidgetHub` (`object`, nối ở `LauncherApp.onCreate`) thu `widgets` rồi `WidgetTicker.tickNow()`; renderer đọc hub ở `prepare`** | Không có kênh trả kết quả; Activity chỉ `finish()` | Không đụng Home; chạy đúng cả khi Home bị tạo lại hay Activity bị hệ thống hủy; cùng mẫu đã kiểm (WX) | Cần cổng "đã nạp" (bài học WX); mỗi lần ghi phát lại cả map (nhỏ) | **Chọn** |

### D7. Cấu trúc Activity riêng và luồng chọn → cắt

Một `PhotoWidgetActivity` (`@AndroidEntryPoint`, extra `itemId`, `exported=false`, `nosensor`, theo `SettingsActivity`) với **một** `PhotoWidgetViewModel` giữ trạng thái `List` (danh sách ảnh đã lưu) và `Crop` (hàng đợi ảnh đang cắt). Hai "màn" là hai view cùng Activity (danh sách / cắt), không phải hai Activity: kết quả chọn nhiều ảnh và hàng đợi cắt sống qua xoay màn hình và `ViewModel`, và `registerForActivityResult` đăng ký một lần lúc dựng. Luồng:

1. Vào: widget chưa có ảnh → chạy bộ chọn ngay; có ảnh → hiện danh sách.
2. Bộ chọn trả `n` URI (đã cắt xuống số chỗ còn lại, D9): **chép ngay** từng ảnh sang `no_backup/photo_widgets/<itemId>/staging/` (thu ≤ 2048 px, xoay đúng chiều, nén JPEG) trên IO, trước khi làm gì khác (URI chỉ tạm).
3. Hàng đợi cắt: mỗi ảnh một màn cắt với vùng mặc định là **vùng giữa** đúng tỉ lệ ("Xong" ngay là dùng mặc định); "Xong" cam kết ảnh đó (đổi tên khỏi `staging`, ghi vào `photo_widgets.json`), rồi sang ảnh kế; "Bỏ ảnh này" xóa bản staging của ảnh đó. Mỗi cam kết ghi ngay nên người dùng thoát giữa chừng vẫn giữ ảnh đã cam kết; ảnh chưa cam kết còn ở staging bị dọn lần sau (Task 6).
4. Sửa ảnh có sẵn: bấm ô ảnh → màn cắt với vùng cắt hiện có.

### D8. Dọn dữ liệu khi widget bị xóa

`RemoveWidgetUseCase` (đường duy nhất người dùng xóa widget: nút "−" → `HomeViewModel.onRemoveWidget`) gọi `photoWidgets.forget(itemId)` xóa thư mục `photo_widgets/<itemId>` + mục JSON. **Widget bị mất gián tiếp** (hiếm: trang bị dồn?) hoặc `staging` bỏ dở: **quét mồ côi** một lần mỗi lần mở app (`PhotoWidgetRepository.sweep(validIds)`: xóa thư mục không thuộc widget `PHOTO` nào trong bố cục + mọi `staging`), vì ảnh nặng nên không chấp nhận mục mồ côi như WX. Nguồn `validIds` lấy từ `LayoutRepository.pages` (domain, đã có), không từ Room trực tiếp.

### D9. Giới hạn

Tối đa **10 ảnh mỗi widget** (câu hỏi 2: chủ dự án chưa nêu số; 10 ảnh ≈ ≤ 8 MB đĩa, chu kỳ đổi 10 giờ). Bộ chọn cắt danh sách trả về xuống số chỗ còn lại (cả khi bộ chọn bỏ qua `maxItems`). Ảnh đầu vào quá lớn: giải mã bị chặn bởi `BitmapScaler.MAX_DECODE_PIXELS`; không đọc được (hỏng, định dạng lạ, tệp bị xóa giữa chừng) thì báo một Toast chung và bỏ ảnh đó, không dừng cả lô. **Không** hỗ trợ GIF động/video (bộ chọn mở `ImageOnly`).

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Một ảnh hoặc bộ ảnh đổi mỗi giờ đúng đầu giờ, **chỉ khi Home hiện, không việc nền**; không Mosaic ở v1 | Chủ dự án, 2026-11-13 |
| 2 | Cỡ 2×2 và 4×2 | Chủ dự án, 2026-11-13 |
| 3 | Chọn vùng cắt (kéo/phóng) lấp đầy widget, không bộ lọc; mỗi ảnh một vùng cắt theo tỉ lệ widget | Chủ dự án, 2026-11-13 |
| 4 | Ảnh từ bộ chọn hệ thống, **không** `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`; app giữ bản sao thu nhỏ riêng, không sao lưu | Chủ dự án, 2026-11-13 |
| 5 | Pick/crop chạy trong Activity riêng; Home biết kết quả qua Flow (D6) | Chủ dự án (không sửa `HomeActivity`), 2026-11-13; thiết kế: agent G3b |
| 6 | Widget layer chỉ thêm, đánh dấu file dùng chung | Brief điều phối, 2026-11-13 |
| 7 | Lưu ngoài Room (D1-C), `itemId` làm khóa | Mẫu WX D1; agent G3b đề xuất, chờ duyệt |
| 8 | Nguồn thu nhỏ + vùng cắt chuẩn hóa (D2-B); ảnh đổi theo giờ treo tường, không lưu trạng thái (D3-B); màn cắt tự viết (D4-A) | Agent G3b đề xuất, chờ duyệt |
| 9 | Bấm widget (ngoài edit mode) → màn "Ảnh của tiện ích" (D5-A) | Chủ dự án duyệt đúng khuyến nghị, 2026-11-13 |
| 10 | Tối đa 10 ảnh mỗi widget (`PhotoWidgetConfig.MAX_PHOTOS`) | Chủ dự án duyệt, 2026-11-13 |
| 11 | Back khi còn ảnh chưa cắt: hỏi "Bỏ N ảnh chưa cắt?", giữ ảnh đã cắt | Chủ dự án duyệt, 2026-11-13 |
| 12 | Quét mồ côi: chạy ngoài luồng chính, `itemId` lấy qua repository (mọi id trong bố cục), **không bao giờ chạm thư mục có `itemId` còn tồn tại**; ảnh nháp bỏ dở của widget còn tồn tại được dọn bằng `clearStaging(itemId)` khi Activity mở lúc không có phiên cắt | Điều phối, 2026-11-13 |
| 13 | Không thêm khai báo backport bộ chọn ảnh (Play services) vào manifest trừ khi Task 3 đo cho thấy cần (khi đó hỏi điều phối) | Điều phối, 2026-11-13 |

## Ngoài phạm vi / ghi để sau

- Bố cục Mosaic, bộ lọc, xoay/lật tay, thứ tự sắp xếp lại (v1: theo thứ tự thêm), hiệu ứng chuyển ảnh (làm ở Task 5 **chỉ nếu** rẻ và không cấp phát trong `draw`).
- Mở app Thư viện khi bấm widget (D5-D), nút "Làm mới ngay", chọn chu kỳ khác 1 giờ, cỡ 4×4 hay cỡ khác (mọi cỡ phải ≤ 4×2, `WidgetKind`).
- Nhập GIF/video, ảnh từ đám mây ngoài bộ chọn, sao lưu ảnh widget.
- Khai `ModuleDependencies` trong manifest để cài bộ chọn backport (tài liệu Android): **chưa làm**, chỉ làm nếu Task 2 cho thấy Live 4 rơi về `ACTION_OPEN_DOCUMENT` và chủ dự án muốn bộ chọn đồng nhất; cần quyết riêng (thêm một khai báo `<service>` vào manifest).
- Tránh vẽ kính phía sau ảnh đục (tiết kiệm GPU): cần sửa `IconCellView` của G3c; ghi sang G3c/mốc sau nếu đo thấy tốn.

## Vùng file

Đường dẫn tính từ `app/src/main/java/gambi/global/os26launcher/` (trừ ghi khác). **ĐỘC QUYỀN** = file nóng chỉ một nhánh được sửa (workflow 5.2). **DÙNG CHUNG** = chỉ thêm, nhánh merge sau rebase.

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/photo/PhotoWidgetConfig.kt` | Tạo | — |
| `domain/photo/PhotoWidgetRepository.kt` | Tạo | — |
| `data/photo/PhotoWidgetStore.kt`, `PhotoFileStore.kt`, `PhotoWidgetRepositoryImpl.kt` | Tạo | — |
| `di/PhotoWidgetModule.kt` | Tạo | — |
| `ui/photo/PhotoWidgetActivity.kt`, `PhotoWidgetViewModel.kt`, `PhotoCropView.kt`, `PhotoGridAdapter.kt` | Tạo | — |
| `res/layout/activity_photo_widget.xml`, `item_photo_widget_thumb.xml` | Tạo | — |
| `ui/widget/PhotoWidgetRenderers.kt`, `PhotoWidgetHub.kt`, `PhotoBitmapCache.kt` | Tạo | — |
| `domain/model/WidgetKind.kt` | Sửa (thêm `PHOTO`) | **DÙNG CHUNG, lớp widget** (G3c đọc mã loại) |
| `ui/widget/WidgetRenderer.kt` | Sửa (một nhánh `PHOTO` trong `create`) | **DÙNG CHUNG, lớp widget** |
| `ui/home/controller/WidgetPickerController.kt` | Sửa (một dòng `kindNameRes`) | **DÙNG CHUNG, lớp widget; G3c cấu trúc lại** (G3b merge trước) |
| `ui/home/controller/HomePagerController.kt` | Sửa (một nhánh `PHOTO` ở `onWidgetClick`) | **DÙNG CHUNG, lớp widget; G3c có thể chạm** |
| `ui/home/HomeViewModel.kt` | Sửa **một dòng** (`WidgetKind.PHOTO -> true` ở `when` của `onWidgetTapped`) | **ĐỘC QUYỀN theo workflow 5.2; ngoại lệ điều phối cho phép, 1 dòng (2026-11-13)**; không có cách tránh vì `when` vét cạn |
| `domain/usecase/RemoveWidgetUseCase.kt` | Sửa (thêm tham số + một dòng) | DÙNG CHUNG (WX đã đụng) |
| `LauncherApp.kt` | Sửa (tiêm repository + một dòng nối `PhotoWidgetHub.init` + đăng ký `onTrimMemory`) | DÙNG CHUNG (WX đã đụng) |
| `AndroidManifest.xml` | Sửa (thêm một `<activity>`) | DÙNG CHUNG (append) |
| `res/values/strings.xml` | Sửa (khối `ph_` ở CUỐI file) | DÙNG CHUNG (append) |
| `res/values/dimens.xml` | Sửa (khối `ph_` ở cuối, nếu cần) | DÙNG CHUNG (append) |
| `docs/plans/G3b-photo-widget.md`, `docs/knowledgements/G3b-photo-widget.md` | Tạo | — |

**Không đụng:** `HomeActivity`, `activity_home.xml`, `HomeUiState`, `IconCellView`, `IconGridView`, `HomePagerAdapter`, `WidgetTicker`, `data/layout/*`, `core/glass/*`, `DragHelper`, Room, `app/schemas/*`, `SettingsKeys`/`SettingsRepositoryImpl`, `libs.versions.toml`, `app/build.gradle.kts`.

## Mâu thuẫn với brief (đọc từ code)

1. **`HomeViewModel.kt` bị chạm một dòng.** Brief nói chỉ `HomeActivity`/`activity_home.xml` thuộc G3c; nhưng workflow 5.2 xếp `HomeViewModel.kt` vào file độc quyền, và `onWidgetTapped` có `when (kind)` vét cạn: thêm `PHOTO` vào `WidgetKind` không biên dịch được nếu thiếu nhánh. Đề xuất: điều phối cho phép **một dòng** này (không đổi hành vi nào khác) hoặc, nếu G3c cũng sửa chính hàm này, điều phối tự thêm nhánh khi rebase. Agent **không** sửa trước khi được phép; Task 2 dừng ở đúng điểm này nếu chưa có phép.
2. **Brief: "one picker card".** Thực tế `WidgetPickerController` duyệt `WidgetKind.entries` và mỗi cỡ một thẻ nên loại mới tự có **hai** thẻ (2×2, 4×2) trong một mục "Ảnh" mà chỉ cần thêm một dòng tên. Không có thẻ riêng nào phải dựng thêm.

## Global Constraints

- Layering `ui → domain ← data`; `domain` không biết `Uri`/`Bitmap`/`Context` (kiểu của nền tảng chỉ ở `data` và `ui`); interface chỉ nhận/trả kiểu domain.
- Chữ hiển thị ở `strings.xml`, khối `ph_` ở cuối (tiếng Việt); số đo ở `dimens.xml`; hằng thuật toán (độ nén, cạnh dài tối đa, trần cache) đặt `object`/`companion` có chú thích CHỈNH TAY.
- **Quyền riêng tư:** không `Log` URI, đường dẫn, tên tệp, tọa độ EXIF, kích thước ảnh gốc kèm tên; log chỉ ghi tên lớp ngoại lệ và số lượng. Tên file ảnh lưu là số tự tăng/uuid, không lấy tên gốc. Kiểm bằng `grep` toàn diff khi đóng.
- Không dịch vụ nền, không `AlarmManager`/WorkManager/`Handler` riêng (chỉ `WidgetTicker` sẵn có).
- `prepare` không chặn, không I/O; `draw` không cấp phát (GL Task 6); mọi `Paint`/`Path`/`Shader` dựng trong `prepare`.
- Ghi file nguyên tử (`AtomicFile`, hoặc ghi `.tmp` rồi đổi tên); file hỏng/sai version → coi như không có, không crash.
- `OutOfMemoryError` khi giải mã được bắt và coi như lỗi ảnh (không sập Home).
- Không đổi schema; không dependency mới; không quyền mới.

## Review Focus

1. **Lớp widget chỉ thêm:** `git diff --stat` chỉ chạm các file ở bảng "Vùng file"; `IconCellView`/`IconGridView`/`HomeActivity` không có trong diff.
2. **`prepare` không giải mã, không I/O;** giải mã trên Executor đơn luồng; kết quả đến muộn sau khi widget đã đổi `itemId`/ảnh bị bỏ (kiểm khóa cache có `photoId` + `cropVersion`, không trả bitmap sai widget).
3. **Ô tái dùng:** `bindItem(itemId)` đặt lại trạng thái (ảnh, bitmap, shader) ở mỗi lần bind; ô từng là widget ảnh rồi thành app/thư mục không giữ bitmap (bài học A1a).
4. **Đổi ảnh đầu giờ không việc nền:** không `Handler`/receiver mới; không có widget ảnh hiện thì `WidgetTicker.registeredCount` không tăng vì G3b.
5. **Dọn file:** xóa widget xóa thư mục thật; quét mồ côi không xóa nhầm thư mục của widget đang có; `staging` bỏ dở bị dọn.
6. **Màn cắt:** vùng cắt luôn nằm trong ảnh, tỉ lệ khung đúng tỉ lệ widget thật, phóng tối thiểu = phủ khung, vùng chuẩn hóa lưu/đọc khớp nhau (round-trip), `requestDisallowInterceptTouchEvent` khi kéo.
7. **Riêng tư:** không URI/đường dẫn trong log; ảnh lưu không còn EXIF; không `takePersistableUriPermission`; thư mục nằm trong `noBackupFilesDir`.
8. **Số ảnh vượt giới hạn:** bộ chọn bỏ qua `maxItems` (đường `ACTION_OPEN_DOCUMENT`) vẫn bị cắt đúng số chỗ.
9. **Bộ nhớ nhiều widget:** trần cache, `evictAll` khi `onTrimMemory`, renderer không giữ nhiều hơn một bitmap.
10. **Ảnh kéo từ bảng "Tiện ích" và bản xem trước** (`itemId = 0`/`sample`) không đọc kho ảnh và không đăng ký việc giải mã.

---

### Task 1: Dữ liệu và kho ảnh (không UI)
**Bắt buộc kiểm tay:** không (không có giao diện; đọc code + build).
**Files:** `domain/model/photo/PhotoWidgetConfig.kt`, `domain/photo/PhotoWidgetRepository.kt`, `data/photo/*`, `di/PhotoWidgetModule.kt`, `domain/usecase/RemoveWidgetUseCase.kt` (DÙNG CHUNG).
**Interfaces:** Consumes `core/bitmap/BitmapScaler`, `ExifReader`, `LayoutRepository.pages`. Produces `PhotoWidgetRepository`: `widgets: StateFlow<Map<Long, PhotoWidgetConfig>>`, `awaitLoaded()`, `config(itemId)` (đọc rẻ, luồng chính), `suspend importPhoto(itemId, source): ImportResult` (nhận một nguồn trừu tượng do `ui` bọc `Uri` thành `InputStream` provider để `domain` không biết `Uri`), `commitCrop(itemId, photoId, crop)`, `removePhoto(itemId, photoId)`, `forget(itemId)`, `sweep(validItemIds)`. `PhotoWidgetConfig(photos: List<PhotoRef(id, crop: CropRect(l,t,r,b), aspect, version)>)`.
- [x] **Step 1:** Mô hình domain + interface (chữ ký chỉ kiểu domain).
- [x] **Step 2:** `PhotoWidgetStore` (`photo_widgets.json`, version 1; `AtomicFile`; hỏng/sai version = rỗng; khóa không phải số bị bỏ từng mục như `WeatherPlacesStore.readWidgets`; tối đa 10 ảnh mỗi widget khi đọc).
- [x] **Step 3:** `PhotoFileStore`: nhập ảnh = đọc EXIF (`ExifReader`) → `BitmapScaler.sampleSizeFor` + thu về ≤ 2048 px cạnh dài (không phóng to) → xoay → nén JPEG → ghi `.tmp` rồi đổi tên vào `staging/`; `commit` đổi tên khỏi `staging`; `delete`; `deleteWidget(itemId)`; hằng `MAX_SIDE_PX = 2048`, `JPEG_QUALITY = 90` (CHỈNH TAY). Bắt `OutOfMemoryError`/`IOException`/`IllegalArgumentException` → `ImportResult.Failed`.
- [x] **Step 4:** `PhotoWidgetRepositoryImpl` (`@Singleton`, `@ApplicationScope`, I/O trên `IoDispatcher`, `Mutex`, cổng `awaitLoaded`), `PhotoWidgetModule` (`@Binds`).
- [x] **Step 5:** `RemoveWidgetUseCase` gọi `photoWidgets.forget(itemId)` sau `weatherPlaces.forgetWidget` (widget loại khác: no-op).
- [x] **Step 6: Kịch bản kiểm** — chỉ build xanh (`gradlew.bat assembleDebug`) + điều phối đọc code; kiểm EXIF/GPS của file lưu và kích thước đĩa ở Task 3 (khi đã có đường nhập thật) bằng `adb shell run-as` trên bản debug **sau khi điều phối xác nhận máy rảnh**.

---

### Task 2: Lớp widget: loại `PHOTO`, renderer, hub, thẻ trong bảng, đường bấm
**Bắt buộc kiểm tay:** không (làm cùng Task 3 mới thấy đủ; Task này cho trạng thái "chưa có ảnh" và đường mở màn). **Điều kiện bắt đầu:** điều phối đã cho phép (2026-11-13) sửa **một dòng** `HomeViewModel.kt`, chỉ nhánh `when` cho `PHOTO`.
**Files (DÙNG CHUNG, thêm thôi):** `WidgetKind.kt`, `WidgetRenderer.kt`, `WidgetPickerController.kt`, `HomePagerController.kt`, `HomeViewModel.kt` (một dòng), `LauncherApp.kt`, `AndroidManifest.xml`; mới: `PhotoWidgetRenderers.kt`, `PhotoWidgetHub.kt`, `PhotoBitmapCache.kt`, khối chuỗi `ph_`.
**Interfaces:** Consumes Task 1. Produces `WidgetKind.PHOTO`, `PhotoWidgetHub` (`init`, `configOf(itemId)`, `bitmapFor(...)`, `reportSize(itemId, w, h)`/`sizeOf(itemId)`), `PhotoWidgetActivity.intent(context, itemId)` (Task 3 dựng nội dung; Task này có thể là khung rỗng chỉ `finish()` để build).
- [x] **Step 1:** `WidgetKind.PHOTO("photo", 2×2, 4×2)`; `WidgetRenderers.create` nhánh `PHOTO`; `kindNameRes` (`ph_name` "Ảnh"); `onWidgetTapped` nhánh `PHOTO -> true` (một dòng, đã được phép).
- [x] **Step 2:** `PhotoWidgetHub` (`object`, mẫu `WeatherWidgetHub`): `init(repo, scope)` thu `widgets` → `WidgetTicker.tickNow()`; cổng `loaded`; `tappedWidgetId` không cần (id đi trong extra của Intent). `LauncherApp.onCreate`: một dòng `PhotoWidgetHub.init(...)` + `registerComponentCallbacks` (`onTrimMemory` → `PhotoBitmapCache.trim`).
- [x] **Step 3:** `PhotoBitmapCache` (LRU theo byte, D3) + Executor đơn luồng giải mã vùng (`BitmapRegionDecoder` như `PickedImageSource.decodeRegion`, `inSampleSize` + `scaleTo`, đường lui giải mã cả ảnh có chặn pixel).
- [x] **Step 4:** `PhotoWidgetRenderer(context, medium, sample)`: `bindItem(itemId)` đặt lại trạng thái; `prepare` chọn ảnh = `floorMod(giờ, n)`, tra cache, xếp giải mã nếu chưa có, dựng `BitmapShader` + `RectF` bo góc `min(w,h) * 0.12` (cùng tỉ lệ `IconCellView.WIDGET_CORNER_RATIO`, ghi chú nguồn); `reportSize`; `draw` chỉ `drawRoundRect` bằng shader; trạng thái **chưa có ảnh** (và `sample`/`itemId = 0`): biểu tượng ảnh + chữ "Chạm để chọn ảnh" (vẽ bằng `newWidgetTextPaint`); `contentDescription` ("Ảnh, N ảnh" / "Ảnh, chưa có ảnh, chạm để chọn").
- [x] **Step 5:** `HomePagerController` nhánh `PHOTO` trong `onWidgetClick`: `context.startActivity(PhotoWidgetActivity.intent(context, itemId))`.
- [x] **Step 6:** manifest thêm `<activity android:name=".ui.photo.PhotoWidgetActivity" android:exported="false" android:screenOrientation="nosensor" />` ngay dưới `SettingsActivity`.
- [x] **Step 7: Kịch bản kiểm (Live 4)** — (a) bảng "Tiện ích" có mục "Ảnh" với thẻ 2×2 và 4×2 (bản xem trước vẽ trạng thái trống); (b) kéo thẻ lên lưới và bấm thẻ đều thêm được widget; (c) bấm widget ngoài edit mode mở khung Activity rồi Back về Home; trong edit mode bấm widget vẫn bị nuốt; (d) thêm 2 widget ảnh + đồng hồ + thời tiết: Home vẫn mượt, kéo-thả, đổi cỡ lưới 4×6 → 4×5 không mất widget; (e) `grep` diff: không file ngoài vùng.

---

### Task 3: Activity riêng: danh sách, bộ chọn ảnh, nhập ảnh
**Bắt buộc kiểm tay:** **có** — giả định chỉ máy thật trả lời được (mục 4.2): **đường bộ chọn nhiều ảnh trên Live 4 (API 30) so với Oppo (API 34)** (bộ chọn hệ thống, backport hay `ACTION_OPEN_DOCUMENT`; `maxItems` có bị bỏ qua không; URI có đọc được lúc chép không). Task 4 (crop) và hàng đợi nhiều ảnh xây trên kết quả này.
**Files:** `ui/photo/PhotoWidgetActivity.kt`, `PhotoWidgetViewModel.kt`, `PhotoGridAdapter.kt`, layouts, khối `ph_`.
**Interfaces:** Consumes Task 1–2. Produces màn danh sách ảnh của widget; hàng đợi ảnh vừa nhập (cắt ở Task 4: tạm cam kết ngay với vùng giữa mặc định cho tới khi Task 4 có).
- [x] **Step 1:** Activity theo mẫu `SettingsActivity` (toolbar Material, inset hệ thống, `nosensor`); `itemId` từ extra (thiếu/≤ 0 thì `finish()`); `PhotoWidgetViewModel` (Hilt, `SavedStateHandle` giữ `itemId`).
- [x] **Step 2:** `registerForActivityResult(PickMultipleVisualMedia(maxItems = cap))` đăng ký lúc dựng (cap = `10`, nhưng bộ chọn yêu cầu `maxItems > 1` nên khi còn đúng **1** chỗ dùng `PickVisualMedia`); `ImageOnly`; kết quả cắt xuống số chỗ còn lại; **chép ngay** từng ảnh qua `importPhoto` (tuần tự, tiến độ đơn giản, hủy được); lỗi → một Toast chung (`ph_import_failed`) và bỏ ảnh đó.
- [x] **Step 3:** Danh sách (lưới 3 cột, `RecyclerView` + `GridLayoutManager`): ô thumbnail (giải mã nhỏ ≤ 200 px từ nguồn, cache nhỏ riêng, giải mã nền), nút ✕ xóa (hộp xác nhận nhẹ), ô "+ Thêm ảnh" (ẩn khi đủ 10); dòng chữ "Đổi ảnh mỗi giờ" khi ≥ 2 ảnh. Widget chưa có ảnh: mở thẳng bộ chọn lúc vào (một lần mỗi lần vào, không lặp sau xoay: cờ trong `ViewModel`); hủy chọn thì màn trống hiện nút "Chọn ảnh".
- [x] **Step 4:** Sau cam kết, repository phát `widgets`; Back/`finish()` về Home; Home vẽ ảnh nhờ hub (không có kênh kết quả).
- [x] **Step 5: Kịch bản kiểm (Live 4; Oppo cho dòng (a))** — (a) **ghi lại đường bộ chọn** (xem `adb logcat -s ActivityTaskManager` hoặc `dumpsys activity activities` lấy tên component bộ chọn, **không** in URI) trên cả hai máy: bộ chọn hệ thống / GMS / `DocumentsUI`; chọn 1, chọn 3, chọn vượt số chỗ (ví dụ 12 trên đường bỏ qua `maxItems`): app giữ đúng số chỗ; (b) ảnh dọc (EXIF xoay 90°), ảnh PNG có alpha, ảnh panorama 12000×3000, ảnh hỏng: không sập, ảnh đúng chiều; (c) widget có 3 ảnh hiện ảnh đầu (vùng giữa) trên Home ngay khi Back về; (d) xóa ảnh, thêm ảnh, xóa widget bằng "−": thư mục `photo_widgets/<id>` biến mất (`adb shell run-as gambi.global.os26launcher ls no_backup/photo_widgets`, chỉ liệt kê, không đọc nội dung); (e) EXIF của file đã lưu không còn thẻ GPS; (f) dung lượng đĩa mỗi ảnh; (g) `force-stop`, mở lại: ảnh còn nguyên.
- [x] **Kịch bản gộp Task 2 + Task 3 (điều phối chạy, Live 4, bản debug; xác nhận máy rảnh; sao lưu dữ liệu trước)** — (1) `adb logcat -c`, rồi `adb logcat -s PhotoWidget:I` ở cửa sổ riêng; (2) bấm widget Ảnh trống: bộ chọn tự mở; dòng log `pickerRoute=PHOTO_PICKER|OPEN_DOCUMENT api=.. slots=.. mode=..` (chỉ tuyến, không URI); khi bộ chọn đang mở chạy `adb shell dumpsys activity activities | findstr /i "topResumedActivity mResumedActivity"` để lấy tên linh kiện (bộ chọn hệ thống / GMS / DocumentsUI), ghi lại cả hai máy (Oppo cho dòng này); (3) chọn 1 ảnh, 3 ảnh, rồi (đã có 3) chọn 9 ảnh: phải ra thông báo "tối đa 10" và đúng 10 ảnh; đủ 10 ảnh thì ô "+ Thêm ảnh" biến mất; (4) ảnh dọc (EXIF xoay), PNG có alpha (nền trắng), panorama 12000x3000, một tệp hỏng đổi tên .jpg: không sập, các ảnh còn lại vào; (5) Home: widget hiện ảnh đầu (vùng giữa), không chớp, 2x2 và 4x2; (6) kiểm file chỉ qua tên thư mục và kích thước, KHÔNG in nội dung: `adb shell "run-as gambi.global.os26launcher ls -l no_backup/photo_widgets"` rồi `ls -l no_backup/photo_widgets/<itemId>` (mỗi ảnh thường vài trăm KB, không có thư mục `staging` sau khi xong); EXIF: `adb shell "run-as gambi.global.os26launcher grep -c -a -E 'Exif|GPS' no_backup/photo_widgets/<itemId>/<tên>.jpg"` chỉ in SỐ ĐẾM, kỳ vọng 0 (file nén lại, không APP1/Exif); (7) xóa 1 ảnh bằng ✕ rồi xác nhận: file biến mất khỏi thư mục, widget đổi; (8) xóa widget bằng "−": `ls no_backup/photo_widgets` không còn thư mục của itemId đó (chứng minh dọn file); (9) `force-stop`, mở lại: ảnh còn nguyên; (10) hồi quy Task 2: các mục 1-8 của danh sách Task 2.
- [x] **Kịch bản Task 4 (điều phối chạy trên Live 4, bản debug, ảnh thử tạo sẵn, không dùng ảnh của chủ dự án)** — (1) Thêm widget Ảnh 2x2, bấm, chọn 3 ảnh: màn "Cắt ảnh 1/3" hiện khung bo góc đúng tỉ lệ widget, vùng ngoài khung tối; (2) kéo một ngón và chụm hai ngón: mượt, khung không bao giờ ra ngoài ảnh, không phóng nhỏ hơn mức vừa phủ khung, phóng tối đa 4 lần; (3) "Xong" ngay (không chạm) = vùng giữa; Home hiện ảnh đầu ngay khi cam kết (chưa cần thoát màn); (4) ảnh 2 chọn vùng góc trái trên, ảnh 3 vùng khác: sau khi xong, Home đổi ảnh bằng cách chờ đầu giờ hoặc đổi giờ máy (Task 5) — tạm thời kiểm từng ảnh bằng cách để ảnh đó là ảnh đầu (xóa các ảnh trước) hoặc xem ô "cắt lại"; (5) so vùng chọn trên Home với khung (ảnh có vạch màu): khớp, lệch tối đa vài % do tỉ lệ widget thật; lặp với 4x2 và với lưới 4x6 → 4x5; (6) "Bỏ ảnh này" ở ảnh 1/3: sang "Cắt ảnh 1/2" (đếm lại), ảnh bị bỏ không còn trong `no_backup/photo_widgets/<id>/staging` (`run-as ls`, chỉ tên); (7) Back khi còn 2 ảnh chưa cắt: hộp "Bỏ 2 ảnh chưa cắt?"; "Tiếp tục cắt" ở lại; "Bỏ" về danh sách, ảnh đã cam kết còn, staging rỗng; (8) bấm một thumbnail: "Cắt lại ảnh" mở đúng vùng đã lưu; chỉnh rồi "Xong": Home đổi vùng; "Hủy" không đổi; (9) ảnh dọc/panorama/ảnh nhỏ hơn khung: khung luôn được phủ; (10) xoay ngang rồi dọc (Settings > bật xoay tự động chỉ ảnh hưởng Activity này nếu có; Activity khai `nosensor` nên có thể không xoay) hoặc chia đôi màn hình: vùng cắt đang chỉnh giữ nguyên; (11) nhấn Home giữa lúc đang cắt: Activity đóng, mở lại không còn staging, không sập; (12) hồi quy Task 3.

---

### Task 4: Màn cắt `PhotoCropView`
**Bắt buộc kiểm tay:** **có** — cử chỉ chạm mới (kéo một ngón + phóng hai ngón trong một `View` tự viết) mà review code khó thấy (bài học M7); chủ dự án chốt "sửa vùng cắt" là yêu cầu cốt lõi.
**Files:** `ui/photo/PhotoCropView.kt`, `PhotoWidgetActivity.kt`, `PhotoWidgetViewModel.kt`, layouts, khối `ph_`.
**Interfaces:** Consumes Task 3 (hàng đợi), `PhotoWidgetHub.sizeOf(itemId)` (tỉ lệ thật; mặc định `spanX:spanY` nếu chưa biết, ghi chú: tỉ lệ thật khác danh nghĩa, hiện trạng 3). Produces `commitCrop(...)` với `CropRect` chuẩn hóa + `aspect`.
- [x] **Step 1:** `PhotoCropView`: nạp ảnh nguồn ở cỡ màn (giải mã nền, `inSampleSize`); khung cắt cố định giữa màn theo `aspect`, vùng ngoài khung làm tối; ảnh kéo (một ngón) và phóng (`ScaleGestureDetector`, tâm theo ngón); tỉ lệ tối thiểu = vừa phủ khung, tối đa = nguồn tại 1:1 hoặc 4× tỉ lệ tối thiểu (CHỈNH TAY, chọn giá trị nhỏ hơn); kẹp để khung luôn nằm trong ảnh (kể cả sau phóng và xoay màn hình: view dựng lại từ `CropRect` của `ViewModel`); `requestDisallowInterceptTouchEvent(true)` khi kéo; xuất `CropRect` chuẩn hóa theo ảnh nguồn.
- [x] **Step 2:** Hàng đợi: tiêu đề "Ảnh i/N", nút "Xong" (cam kết, sang ảnh kế; xong ảnh cuối thì về danh sách), "Bỏ ảnh này" (xóa bản staging của ảnh đó). **Back** trong lúc còn ảnh chưa cắt: hộp xác nhận "Bỏ N ảnh chưa cắt?" (Bỏ / Tiếp tục cắt) — hành vi mặc định đề xuất, ghi ở câu hỏi 3.
- [x] **Step 3:** Sửa ảnh có sẵn: bấm ô thumbnail → màn cắt với vùng hiện có; "Xong" cập nhật `crop` và `version` (đổi khóa cache, widget vẽ lại).
- [x] **Step 4:** Màn hình xoay/đổi cấu hình/thu nhỏ cửa sổ: trạng thái trong `ViewModel`, không mất vùng cắt đang chỉnh.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) kéo và phóng mượt, không giật, không kéo được khung ra ngoài ảnh, phóng không nhỏ hơn mức phủ khung; (b) "Xong" ngay (không chạm) dùng vùng giữa; (c) vùng chọn trên Home khớp với khung đã chọn (so bằng mắt, ảnh có vạch màu), cả 2×2 và 4×2, đổi cỡ lưới 4×6 → 4×5 vẫn đúng (lệch vài % chấp nhận, ghi số đo); (d) ảnh rất dọc/rất ngang, ảnh nhỏ hơn khung (không phóng to quá mức vẫn phủ khung); (e) bộ 3 ảnh, mỗi ảnh vùng khác nhau: đúng từng ảnh; (f) Back giữa chừng: ảnh đã cam kết còn, chưa cam kết bị bỏ sau khi xác nhận; (g) xoay màn hình (bật khóa xoay tắt/bật) giữ vùng cắt; (h) Home nhấn giữa lúc đang cắt: Activity bị đóng (`clearTaskOnLaunch`), không sập; lần mở sau không còn `staging` (Task 6).

---

### Task 5: Đổi ảnh đầu giờ, bộ nhớ, nhiều widget
**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng dùng để chọn phương án (mục 4.2): giải mã vùng có đủ nhanh và bộ nhớ có chịu được 6–8 widget ảnh không quyết định giữ D2-B hay thêm cache đĩa D2-C.
**Files:** `PhotoWidgetRenderers.kt`, `PhotoWidgetHub.kt`, `PhotoBitmapCache.kt` (hiệu chỉnh), ghi số đo vào plan.
**Interfaces:** Consumes Task 2–4. Produces bằng chứng số đo; có thể thêm hiệu ứng chuyển ảnh nếu rẻ.
- [x] **Step 1:** Rà `prepare`/`draw`: không cấp phát trong `draw`; ảnh đến muộn cho widget đã đổi `itemId` bị bỏ; ô tái dùng (widget ảnh → app → widget ảnh) không rò bitmap; cache theo byte; `onTrimMemory`.
- [ ] **Step 2 (tùy chọn, chỉ nếu rẻ):** chuyển ảnh mờ dần ~300 ms khi `prepare` thấy chỉ số đổi (hai `Paint` alpha, không cấp phát trong `draw`); bỏ nếu thấy giật.
- [ ] **Step 3: Kịch bản kiểm (Live 4; Oppo cho dòng (e))** — (a) **đổi ảnh đầu giờ**: bộ 3 ảnh, mở Home ngay trước 0 phút của một giờ và nhìn ảnh đổi ở đúng phút (hoặc đặt giờ máy thủ công sang trước đầu giờ rồi chờ: `TIME_SET` làm mới ngay; ghi cách làm; chủ dự án quyết có chỉnh giờ máy không); rời Home 2 giờ rồi quay lại: ảnh đúng giờ hiện tại; (b) **không việc nền**: không có widget ảnh hiện (ở Thư viện/app khác) thì `dumpsys` không thấy báo thức/receiver của G3b (`dumpsys alarm | findstr gambi`, `WidgetTicker.registeredCount` qua log tạm đã gỡ); (c) **bộ nhớ**: `dumpsys meminfo gambi.global.os26launcher` (Graphics + Native) với 0, 1, 4, 8 widget ảnh (2×2 và 4×2 lẫn lộn), bản **debug và ghi rõ là debug** (debug khởi động chậm hơn release ~4 lần, ghi rõ); so với 0 widget; ghi mức tăng mỗi widget; (d) **giải mã**: đo ms mỗi lần giải mã vùng 2048 px → cỡ widget bằng log tạm (không đường dẫn, gỡ trước merge); (e) **kính**: widget ảnh trên Oppo (kính thật tại chỗ nằm dưới ảnh đục): `dumpsys gfxinfo gambi.global.os26launcher` Janky so với không có widget ảnh khi cuộn trang; (f) vuốt trang liên tục với 8 widget: không giật, không chớp ô trống; (g) kéo-thả widget ảnh, đổi cỡ lưới: ảnh không chớp.

---

### Task 6: Dọn dữ liệu, độ bền, riêng tư
**Bắt buộc kiểm tay:** không (điều phối chạy trong bài kiểm cuối).
**Files:** `PhotoWidgetRepositoryImpl.kt`, `PhotoFileStore.kt`, `LauncherApp.kt`/`PhotoWidgetHub.kt` (nối quét), plan/knowledgement.
- [x] **Step 1:** Quét mồ côi (D8): sau khi `LayoutRepository.pages` phát lần đầu, `sweep(existingItemIds)` (mọi `itemId` trong bố cục) xóa thư mục và mục cấu hình của widget không còn tồn tại, **không đụng thư mục có id còn tồn tại** (staging của chúng dọn bằng `clearStaging` ở Task 3); **không quét khi bố cục chưa nạp** (tránh xóa nhầm toàn bộ); một lần mỗi lần khởi động tiến trình.
- [x] **Step 2:** File ảnh thiếu/hỏng (người dùng xóa tay, đĩa lỗi): ảnh đó bị bỏ qua khi vẽ (không lỗi), nếu tất cả hỏng thì widget về trạng thái "chưa có ảnh"; màn danh sách hiện ô lỗi để xóa.
- [x] **Step 3:** `grep` toàn diff tìm `Log.` kèm biến liên quan URI/đường dẫn/tên; rà `takePersistableUriPermission` (không có); rà thư mục lưu (`noBackupFilesDir`).
- [x] **Step 4: Kịch bản kiểm (Live 4, bản sao dữ liệu trước)** — (a) xóa widget bằng "−": thư mục mất; (b) trỏ một thư mục giả `photo_widgets/999999` (bằng `run-as`): khởi động lại thì bị quét; (c) thư mục của widget có thật không bị quét; (d) xóa một file ảnh bằng tay: widget vẫn vẽ ảnh còn lại, không sập; (e) mở Activity rồi nhấn Home giữa lúc cắt, mở lại: không còn `staging`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: **Vsmart Live 4** (xác nhận rảnh; sao lưu `files/` và `no_backup/` trước khi thử, so mã băm sau khi khôi phục) cho mọi mục; **Oppo A3x** chỉ cho: đường bộ chọn ảnh API 34 (Task 3a), kính quanh ảnh (Task 5e). Dữ liệu chuẩn bị: 6 ảnh gồm ảnh ngang, ảnh dọc EXIF xoay, PNG có alpha, panorama, ảnh nhỏ (≤ 300 px), một tệp hỏng đặt tên `.jpg`.
Thứ tự: thêm widget ảnh (bảng + kéo thẻ) → bấm, chọn ảnh, cắt → hiện đúng trên Home → thêm ảnh/cắt lại/xóa ảnh → bộ nhiều ảnh đổi đầu giờ → nhiều widget và bộ nhớ → đổi cỡ lưới, kéo trang → xóa widget và dọn file → khởi động lại.
Hồi quy: đồng hồ, lịch, thời tiết (chọn địa điểm vẫn hoạt động); kéo-thả widget/icon; edit mode, "−" xóa widget; bảng "Tiện ích" kéo thẻ; Cài đặt → Ảnh nền vẫn chọn được ảnh; bấm widget đồng hồ/lịch mở app như cũ.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** (điền khi kiểm)

### Bài kiểm gộp Task 2–6 (điều phối chạy MỘT phiên trên Live 4; Oppo cho mục B2 và E7; Task 5 đo ở cuối)

**Chuẩn bị:** máy rảnh (xác nhận); sao lưu `files/` và `no_backup/` của app rồi so mã băm khi khôi phục; cài APK debug của nhánh (ghi `git rev-parse --short HEAD`); đẩy ảnh thử tạo sẵn vào một thư mục tạm của máy (dọc có EXIF xoay 90°, PNG có alpha, panorama 12000×3000, ảnh nhỏ ≤ 300 px, tệp hỏng đặt tên .jpg, vài ảnh có vạch màu/ô số để so vùng cắt) — không dùng ảnh của chủ dự án; mở sẵn hai cửa sổ `adb logcat -c` rồi `adb logcat -s PhotoWidget:I G3BMEAS:D` (log chỉ chứa tuyến bộ chọn, chỉ số ảnh, thời gian giải mã, byte cache; không URI/đường dẫn).

**A. Task 2: lớp widget (hồi quy cùng lúc)**
1. Edit mode → Tiện ích: có mục "Ảnh" với thẻ 2×2 và 4×2 (xem trước trạng thái trống, chữ vừa khung).
2. Bấm thẻ 2×2: thêm widget trống ("Chạm để chọn ảnh"). Kéo thẻ 4×2 thả lên lưới: ảnh kéo giống widget, thả tạo được.
3. Ngoài edit mode bấm widget: mở màn "Ảnh của tiện ích". Trong edit mode bấm widget: vẫn bị nuốt. Đồng hồ, lịch, thời tiết bấm như cũ.
4. Kéo-thả widget ảnh, đổi cỡ lưới 4×6 → 4×5 và ngược lại: widget không mất; vuốt trang với 2 widget ảnh + đồng hồ + thời tiết: mượt như trước.

**B. Task 3: chọn và nhập ảnh**
1. Bấm widget ảnh trống: bộ chọn tự mở; log `PhotoWidget: pickerRoute=… api=… slots=… mode=…`. Lúc bộ chọn đang mở: `adb shell dumpsys activity activities | findstr /i "topResumedActivity mResumedActivity"` lấy tên linh kiện bộ chọn. Ghi cả hai máy (B2 = Oppo A3x làm lại đúng bước này).
2. Chọn 1 ảnh; rồi 3 ảnh; rồi khi đã có 3 ảnh chọn 9 ảnh: toast "tối đa 10", đúng 10 ảnh, ô "+ Thêm ảnh" biến mất. Thử chọn vượt số lượng bằng cách giữ ô chọn của bộ chọn (đường bỏ qua `maxItems` nếu có).
3. Ảnh dọc EXIF xoay (đúng chiều), PNG có alpha (nền trắng), panorama (không sập), ảnh nhỏ, tệp hỏng (toast "Không nhập được một số ảnh", các ảnh khác vẫn vào).
4. File (chỉ tên thư mục và kích thước, KHÔNG in nội dung): `adb shell "run-as gambi.global.os26launcher ls -l no_backup/photo_widgets"` và `ls -l no_backup/photo_widgets/<itemId>` (mỗi ảnh vài trăm KB, không còn `staging` khi xong). EXIF: `adb shell "run-as gambi.global.os26launcher grep -c -a -E 'Exif|GPS' no_backup/photo_widgets/<itemId>/<tên>.jpg"` chỉ in SỐ ĐẾM, kỳ vọng 0.
5. Xóa một ảnh bằng ✕ và xác nhận: file mất, widget đổi. Xóa widget bằng "−": thư mục `<itemId>` biến mất khỏi `ls no_backup/photo_widgets`. `force-stop`, mở lại: ảnh còn nguyên.

**C. Task 4: màn cắt**
1. Chọn 3 ảnh: "Cắt ảnh 1/3": khung bo góc đúng tỉ lệ widget, ngoài khung tối. Kéo một ngón, chụm hai ngón: mượt; khung không ra ngoài ảnh; không nhỏ hơn mức phủ khung; phóng tối đa ~4×.
2. "Xong" ngay = vùng giữa, Home hiện ảnh ngay. Ảnh 2, 3 chọn vùng khác nhau (xem từng ảnh trên Home bằng cách xóa ảnh trước hoặc "Cắt lại").
3. So vùng trên Home với khung (ảnh vạch màu): khớp trong vài %; lặp với 4×2 và sau đổi lưới 4×6 → 4×5 (ghi số lệch).
4. "Bỏ ảnh này" ở 1/3: sang "1/2"; `ls …/staging` không còn tệp của ảnh bị bỏ.
5. Back khi còn 2 ảnh chưa cắt: hộp "Bỏ 2 ảnh chưa cắt?"; "Tiếp tục cắt" ở lại; "Bỏ" về danh sách, ảnh đã cam kết còn, `staging` rỗng.
6. Bấm thumbnail: "Cắt lại ảnh" mở đúng vùng đã lưu; chỉnh rồi "Xong" Home đổi; "Hủy" không đổi.
7. Ảnh rất dọc, panorama, ảnh nhỏ: khung luôn được phủ. Chia đôi màn hình hoặc đổi cỡ cửa sổ giữa lúc cắt: vùng đang chỉnh được giữ.
8. Nhấn Home giữa lúc cắt: Activity đóng, không sập; mở lại không còn `staging`.

**D. Task 6: dọn dữ liệu, file hỏng, riêng tư**
1. Quét mồ côi: `adb shell "run-as gambi.global.os26launcher mkdir -p no_backup/photo_widgets/999999"` rồi tạo một tệp giả không phải ảnh trong đó (`sh -c 'echo x > no_backup/photo_widgets/999999/a.jpg'`); `force-stop`, mở Home: thư mục 999999 bị xóa, thư mục của widget có thật còn nguyên và ảnh còn hiện. Thư mục tên không phải số (ví dụ `abc`) không bị chạm.
2. File hỏng: với widget 3 ảnh, `run-as … rm no_backup/photo_widgets/<itemId>/<một tệp>.jpg`: widget vẫn vẽ các ảnh còn lại, không sập; xóa hết tệp ảnh: widget về trạng thái "Chạm để chọn ảnh"; bấm vào: màn danh sách còn ô trống có ✕ để xóa.
3. Riêng tư: `adb logcat -d | findstr /i "content:// file:// /data/user /storage"` không có dòng nào từ tag PhotoWidget/G3BMEAS/PhotoFileStore/PhotoWidgetStore; thư mục ảnh nằm dưới `no_backup/` (không có trong `files/`).

**E. Task 5: đổi ảnh đầu giờ, bộ nhớ, nhiều widget (đo ở CUỐI, ghi bản debug)**
1. Chuẩn bị widget 3 ảnh (A, B, C có vùng cắt khác nhau); xem log `G3BMEAS: rotate idx=…`.
2. Đầu giờ: Cài đặt → Ngày giờ → tắt tự động, đặt giờ máy 14:58 rồi mở Home; đến 15:00 (hoặc đặt 14:59:30) ảnh đổi đúng ở phút 00 (log `rotate idx` đổi, ảnh đổi); đặt giờ máy sang 2 giờ sau khi Home đang ở nền (mở app khác) rồi quay về: ảnh đúng giờ mới; khôi phục giờ tự động sau khi đo.
3. Không việc nền: để Home ở nền (mở app khác) 2–3 phút: không có dòng `G3BMEAS` mới; `adb shell dumpsys alarm | findstr gambi` không có báo thức của tính năng này.
4. Bộ nhớ (bản debug, nói rõ): `adb shell dumpsys meminfo gambi.global.os26launcher` (Graphics, Native Heap, TOTAL) với 0, 1, 4, 8 widget ảnh (2×2 và 4×2 lẫn lộn, cùng một trang và hai trang); ghi mức tăng mỗi widget; đọc `G3BMEAS: decode ms=… bmpKB=… cacheKB=… entries=…` để lấy thời gian giải mã và byte cache; cache không vượt trần min(24 MB, heap/16).
5. Cuộn trang nhanh với 8 widget ảnh: không chớp ô trống, không giật; kéo-thả và đổi cỡ lưới: ảnh không chớp.
6. Rời Home lâu rồi `adb shell am send-trim-memory gambi.global.os26launcher COMPLETE`: cache được nhả, quay lại Home ảnh giải mã lại không sập.
7. **Oppo A3x (kính thật):** `adb shell dumpsys gfxinfo gambi.global.os26launcher reset`, vuốt trang qua lại 20 lần với 0 widget ảnh, đọc Janky frames; lặp với 8 widget ảnh; so tỉ lệ Janky (nói rõ bản debug; kính nằm dưới ảnh đục có thể tốn GPU: ghi kết quả để quyết định có xin G3c bỏ vẽ kính dưới ảnh).
8. Gỡ log tạm `G3BMEAS` (tìm `TẠM (G3b Task 5`) trước khi đóng nhánh.


## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-13 | Plan | Viết plan, chưa code | 038d276 | Điều phối duyệt 2026-11-13; quét mồ côi chạy ngoài luồng chính, đọc id qua repository, không đụng thư mục còn itemId; Task bắt buộc kiểm tay do điều phối chạy, agent viết kịch bản; không thêm khai báo backport nếu đo chưa cần |
| 2026-11-13 | 1 | Dữ liệu và kho ảnh: model, PhotoWidgetRepository, PhotoWidgetStore, PhotoFileStore, module Hilt, dọn ở RemoveWidgetUseCase | (xem git log) | assembleDebug xanh; chưa có UI nên kiểm bằng đọc code; chờ review |
| 2026-11-13 | 2 | Lớp widget: WidgetKind.PHOTO, PhotoWidgetRenderer (trống / ảnh theo giờ), PhotoWidgetHub, PhotoBitmapCache, thẻ trong bảng, bấm mở PhotoWidgetActivity (khung), HomeViewModel đúng 1 nhánh when | (xem git log) | assembleDebug xanh; chờ review + kiểm máy |
| 2026-11-13 | 3 | Activity: danh sách ảnh, PickMultipleVisualMedia(10) cắt xuống số chỗ còn lại, chép ngay vào staging rồi cam kết vùng giữa, xóa ảnh có xác nhận, clearStaging khi vào, log tuyến bộ chọn | (xem git log) | assembleDebug xanh; Task 3 bắt buộc kiểm tay (tuyến bộ chọn) do điều phối chạy cùng Task 2 |
| 2026-11-13 | 4 | Màn cắt: PhotoCropView (kéo/phóng, khung bo góc đúng tỉ lệ, kẹp trong ảnh, phóng tối đa 4x), hàng đợi cắt, Xong / Bỏ ảnh này / Hủy, Back hỏi "Bỏ N ảnh chưa cắt?", bấm thumbnail để cắt lại | (xem git log) | assembleDebug xanh; Task 4 bắt buộc kiểm tay (cử chỉ mới) do điều phối chạy |
| 2026-11-13 | 5-6 | Task 6: quét mồ côi sau khi bố cục nạp (một lần mỗi lần khởi động, ngoài luồng chính, chỉ xóa thư mục id không còn trong bố cục), ảnh hỏng/mất được bỏ qua và hỏng hết thì về trạng thái trống, grep riêng tư. Task 5 (phần code): log đo tạm G3BMEAS (debug-only: thời gian giải mã, byte cache, chỉ số ảnh đổi giờ), đánh dấu "TẠM (G3b Task 5" để gỡ; không làm hiệu ứng chuyển ảnh (tùy chọn, bỏ) | (xem git log) | assembleDebug xanh; số đo Task 5 chờ phiên máy; bài kiểm gộp Task 2-6 ghi ở mục "Bài kiểm gộp" |
| 2026-11-13 | 3 (sửa) | Sửa lỗi đo trên Live 4: `BitmapFactory.decodeStream` với `inJustDecodeBounds` luôn trả null nên `importToStaging` loại MỌI ảnh hợp lệ ngay ở bước đọc cỡ (staging rỗng, không lỗi). Nay chỉ kiểm mở được luồng và bounds > 0. Thêm log tạm G3BMEAS (bước hỏng, số ảnh chọn/nhập) | (xem git log) | assembleDebug xanh; chờ chạy lại B-D |
| 2026-11-13 | 4 (sửa) | Màn cắt: vùng TRONG khung bị tối như ngoài (đo trên Live 4). Lỗ của lớp tối nay dựng bằng hướng ngược (ngoài CW + trong CCW, WINDING) thay vì EVEN_ODD; ô thumbnail nay vuông 1:1 bằng ConstraintLayout (FrameLayout match_parent trong RecyclerView ra ô cao thấp không đều) | (xem git log) | assembleDebug xanh; chờ kiểm lại |
| 2026-11-16 | Kiểm máy | Điều phối kiểm trên Live 4 (API 30, ảnh thử): tuyến bộ chọn = PHOTO_PICKER (module Google `PhotoPickerActivity`) | 814ad83 | Chọn nhiều ảnh qua bộ chọn hệ thống chạy được; module trên API 30 chỉ liệt kê album Camera/Screenshots/Downloads |
| 2026-11-16 | 3 | Lỗi nhập ảnh đã sửa ở 0353297: chọn 5 ảnh, vào staging 4, hỏng 1 (tệp hỏng) | 0353297 | Đúng kỳ vọng: tệp hỏng bị bỏ, các ảnh khác vẫn vào |
| 2026-11-16 | 3 | File lưu: EXIF xoay 6 thành 1200x1600 đứng đúng chiều; panorama thành 2048x512; PNG alpha trải lên nền trắng; đếm EXIF/GPS = 0 | 814ad83 | Đo bằng `run-as` chỉ tên và kích thước, không in nội dung |
| 2026-11-16 | 5 | Giải mã vùng 52 ms, bitmap 945 KB ở 504x480 (một mẫu, bản debug) | 814ad83 | Đo bộ nhớ nhiều widget và Janky bỏ theo quyết định 2026-11-13 |
| 2026-11-16 | 4 (kiểm) | e5bed27: lớp tối chỉ ngoài khung; thumbnail vuông 3 cột. "Bỏ ảnh này" đếm lại; Back hiện "Bỏ 2 ảnh chưa cắt?"; cắt lại và Hủy; xóa ảnh (3 thành 2 tệp); force-stop giữ ảnh | e5bed27 | Đạt |
| 2026-11-16 | 5 (kiểm) | Đổi ảnh theo giờ: lúc 17h widget hiện ảnh 2/2 (floorMod giờ, 2 ảnh); edit mode bấm "-" xóa widget và thư mục ảnh của nó | 814ad83 | Đạt; nghi vấn chạm trong edit mode không tái hiện |
| 2026-11-16 | Đóng | Gỡ log tạm G3BMEAS; chuyển Sẵn sàng review | 814ad83 | Chủ dự án quyết bỏ phép đo còn lại 2026-11-13 |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Câu hỏi cho chủ dự án (khuyến nghị đặt trong ngoặc)
1. **Bấm widget ảnh làm gì?** [Khuyến nghị: mở màn "Ảnh của tiện ích" để thêm/cắt lại/xóa ảnh (D5-A); mở Thư viện ảnh để mốc sau nếu muốn.]
2. **Số ảnh tối đa mỗi widget?** [Khuyến nghị: 10.]
3. **Bấm Back khi còn ảnh chưa cắt?** [Khuyến nghị: hỏi "Bỏ N ảnh chưa cắt?"; ảnh đã cắt xong luôn được giữ.]

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: mục 15 → Xong (không Mosaic; một ảnh hoặc bộ ảnh đổi mỗi giờ; cắt theo tỉ lệ widget); thêm ghi chú: widget ảnh lưu ở `no_backup/photo_widgets/`.
- `project_structure.md`: dòng G3b trong bảng đợt; "Tệp thêm ở G3b": `ui/photo/`, `ui/widget/PhotoWidget*`, `data/photo/`, `domain/photo/`, `domain/model/photo/`, `di/PhotoWidgetModule`.
- `room-schema.md`: **không đổi**; thêm một dòng trong mục "cấu hình ngoài Room theo `itemId`" nói widget ảnh cũng dùng (cùng WX).
- `AGENTS.md` / `lessons/`: xem mục "Bài học cho AGENTS.md" trong knowledgement (điều phối đưa vào `widgets.md`, `permissions-and-device-testing.md`).
