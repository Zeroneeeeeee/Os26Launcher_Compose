# Bài học: Icon và danh sách app

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Nạp danh sách app, cache icon, kiểu và bo góc icon.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Danh sách app (M2 Task 1, 1B)

- **`LauncherApps.getActivityList` CÓ chịu lọc hiển thị package (Android 11+) và CÓ hưởng khai báo `<queries><intent>`** (đo 2026-10-19). Tài liệu Android và hai ca tham chiếu trên mạng (BenjMichel/AI-launcher, TomasGC/Anglerfish) đều dùng `PackageManager.queryIntentActivities`, không có ca nào nói về `LauncherApps`, nên đây là dữ kiện đo được của dự án. Suy luận cũ "`LauncherApps` được miễn trừ khỏi lọc" **sai**; launcher cũng không nằm trong 8 nhóm miễn trừ tự động, kể cả khi giữ `ROLE_HOME`.
- **Trước khi sửa** (Oppo A3x, Android 14): `queriesIntents=[REQUEST_ROLE, HOME_SETTINGS, SETTINGS]` → `getActivityList` chỉ trả **9** activity (`settings`, `vending`, `gallery3d`, `oplus.camera`, `documentsui`, `stk` ×2, app mình ×2), trong khi `query-activities` của adb thấy 344.
- **Sau khi sửa:** thêm `<intent><action MAIN/><category LAUNCHER/></intent>` vào `<queries>` → `queriesIntents` có thêm `Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] }`. Kết quả khớp tuyệt đối số của adb: **Oppo A3x 344/344** (9 → 344), **Vsmart Live 4 (Android 11) 87/87**. Hai máy đã đo cả khi là launcher mặc định lẫn khi không (người dùng xác nhận 2026-10-19), số app không đổi: khai `<queries>` là thuộc tính của app gọi, không liên quan role. Nghĩa là M3 vẽ được lưới đầy đủ mà không cần trạng thái "danh sách chưa đủ".
- Không dùng `QUERY_ALL_PACKAGES`. Chính sách Play (support.google.com/googleplay/android-developer/answer/10158779) chỉ liệt kê use case được phép: tìm kiếm thiết bị, diệt virus, quản lý file, trình duyệt; launcher không có tên trong đó. Bước đo trần bằng quyền này (Task 1B bước 4, 5) đã bỏ qua vì bước 2 đạt.
- **Cách đếm đúng số activity của adb:** `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | Select-String "activities found"`. Đừng đếm dòng chứa `packageName` (ra 688 trên Oppo, vô nghĩa) hay `Measure-Object -Line` (ra 262 trên Live 4 trong khi số thật là 87): mỗi activity chiếm nhiều dòng.
- `dumpsys package <package> | Select-String queriesIntents` cho biết hệ thống đang cho app thấy gì; dùng nó trước khi đoán nguyên nhân lọc.
- **Sắp xếp tiếng Việt đạt** với `Collator` (đo trên Live 4, 87 app): `Dịch, Dragon Boy, Drive, Điện thoại, Đồng hồ, Easy Transfer` — `Đ` nằm sau nhóm `D`, trước `E`. Oppo A3x có 344 nhãn, nhiều nhãn bắt đầu bằng dấu cách (vd ` Call Screen Flash Alert`) nên đứng đầu danh sách; chưa quyết có `trim()` nhãn hay không, để M3 xem lúc vẽ lưới.
- **Thời gian nạp (đo 1 lần, chưa tách phần nạp nhãn và phần sắp):** từ lúc `getActivityList` trả về đến lúc có danh sách đã sắp: Oppo A3x 344 app ≈ **7,9 giây** (09:08:47,5 → 09:08:55,5); Live 4 87 app ≈ 1,45 giây lần đầu, 0,4 giây lần hai. Chạy ở luồng nền nên UI không đứng, nhưng M3 sẽ thấy lưới trống rất lâu trên máy nhiều app. Đây là số liệu để quyết định cache đĩa và trạng thái Loading cho Home (đúng điều plan M2 hoãn cho M3).
- `stateIn(initialValue = emptyList())` phát `apps=0` trước, rồi mới phát danh sách thật. Lúc vẽ UI ở M3 phải phân biệt "đang nạp" với "máy thật sự không có app", nếu không sẽ nháy ra trạng thái trống.
- Máy Live 4 vẫn còn bản cài cũ `gambi.global.ioslauncher` (trước khi đổi tên package ngày 2026-10-16) nên danh sách có thêm một app trùng nhãn dễ gây nhầm khi đối chiếu; gỡ tay nếu cần.

## Danh sách app: kết quả đo và bẫy (M2 Task 2-4, 2026-10-26)

- **`IconCache` (`maxMemory/8`)**: Oppo A3x `maxMemory=402653184`, `maxSize=50331648` (48 MB); Vsmart Live 4 `maxMemory=536870912`, `maxSize=67108864` (64 MB). Icon 144x144 = 82944 byte, nên 48 MB chứa được hơn 500 icon cỡ này. Icon thử: 10/10 có, 0 null trên cả hai máy.
- **`LauncherApps.Callback` bắn gì (quan sát log trên Live 4, Android 11):** `pm uninstall -k --user 0` → `onPackageRemoved` rồi `onPackageChanged`; `cmd package install-existing` → `onPackageAdded`; `pm disable-user` và `pm enable` → `onPackageChanged`. Danh sách 87 ↔ 86 tự cập nhật sau khoảng 0,3 giây (debounce 200 ms). Phải phủ cả 7 callback vì cập nhật/bật-tắt app chỉ đi qua `onPackageChanged`. **Chưa thử `adb install -r` bằng APK thật**; đường cập nhật mới được giả lập bằng disable/enable.
- **Work profile:** `adb shell pm create-user --profileOf 0 --managed Work` chạy được trên Vsmart Live 4 (user id 10). Sau khi tạo/start user, hệ thống tự nhân đôi mọi app có thể nhân đôi; danh sách hiện hai dòng cùng nhãn, không có huy hiệu để phân biệt. Nhớ gỡ user tạm (`pm remove-user 10`). Huy hiệu work cho icon là việc từ M3 trở đi.
- **Bẫy RecyclerView: icon nhảy sai dòng khi cuộn nhanh.** Nạp icon là suspend chạy sau khi dòng đã bị tái dùng cho app khác. Cách sửa trong `AppListAdapter`: mỗi holder giữ một `Job`, hủy ở `onBindViewHolder` và `onViewRecycled` trước khi nạp mới. Lưới ở M3 phải theo cùng nguyên tắc.
- **Bấm vào app vừa gỡ:** `LauncherApps.startMainActivity` ném `ActivityNotFoundException` hoặc `SecurityException`; `AppRepositoryImpl.launch` bắt và trả `false`, `HomeViewModel` phát `AppLaunchFailed` để hiện Toast. Probe với user không tồn tại (serial 9999) và package không tồn tại đều trả `false`, không crash.

## TL2 — bo góc icon: bài học (2026-11-03, Vsmart Live 4)

- `Canvas.drawPath` với `PorterDuff.Mode.DST_IN` KHÔNG xóa điểm ảnh ngoài path (góc vẫn đục). Cắt hình đúng: tô path bằng `BitmapShader` lên bitmap trong suốt mới, hoặc dùng mặt nạ phủ cả hình chữ nhật (đọc tài liệu + suy luận; kết quả đo `cornerOpaque=0`).
- Vẽ hai lớp adaptive tự đặt bounds (lớp 108dp, phóng `sizePx/4` mỗi phía) + cắt bằng `BitmapShader` rẻ hơn vẽ `AdaptiveIconDrawable` nguyên: trung bình ~3 ms so với ~5 ms mỗi icon 180px; riêng bước cắt ~0,75 ms (3 lượt mở nguội, một lượt nhiễu cả hai đường).
- Trên Live 4 ~95% icon là adaptive; legacy hiếm.
- Lưới có nhãn bị chặn theo CHIỀU CAO: `GridMetrics.measure` lấy min(bề rộng ô, chiều cao còn lại) × `ICON_SCALE` rồi làm tròn xuống bội 4dp. 0.75 và 0.8 đều cho 44dp (đo 128px); 0.9 cho 52dp. Bước làm tròn 4dp nuốt thay đổi nhỏ: đo icon thật trên máy sau mỗi lần đổi hệ số, đừng suy từ bề rộng ô.
- Bản trước TL1: vào edit mode icon nhỏ đi vì `home_content` bị đẩy xuống 64dp làm ô thấp lại (TL1 gỡ).
- Đo trên máy: Home mặc định của Live 4 là launcher Vinsmart; `adb shell input keyevent HOME` mở launcher đó, không phải Os26Launcher. Mở app bằng `am start -n gambi.global.os26launcher/.ui.home.HomeActivity`.

## G2b — kiểu icon: bài học (2026-11-04, Vsmart Live 4)

- Tô màu đẹp cần tách hình khỏi nền: bỏ `background` của `AdaptiveIconDrawable`, vẽ `foreground` một màu trên cùng một ô tối. Lọc màu cả ảnh đã phẳng (bản đầu) cho ô lúc sáng lúc tối, chủ dự án không chấp nhận.
- `IconGridView`, `DockView`, `FolderPanelView` giữ `HashMap` bitmap riêng nên `IconCache.clear()` không làm mới Home; đổi kiểu đi qua `RecreateGuard` (giữ trang và edit mode vì ViewModel sống qua `recreate()`).
- Nhánh có lịch sử merge lẫn commit trùng: dựng lại từng commit mã lên `main` mới bằng `git diff A B -- app | git apply --3way`, thêm docs một lần cuối, so từng file với bản sao lưu rồi mới `--force-with-lease=<nhánh>:<hash cũ>`. Khi `git merge` xung đột, đừng so tree: chưa có commit mới nên so sánh luôn "giống".
- Sau khi push, PR trên GitHub có thể chậm cập nhật đầu nhánh; `gh pr merge` báo "Head branch is out of date" thì chờ rồi thử lại.
- **Script trên máy thật:** kiểm Os26Launcher đang ở trước trước MỖI thao tác và dừng nếu không; bong bóng chat có thể che màn hình bất cứ lúc nào.

## IC — cache icon đĩa (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **IC — cache icon đĩa:** `BitmapFactory.decodeFile` **chấp nhận file PNG cụt** và trả bitmap rỗng, nên phải kiểm chữ ký PNG và chunk IEND trước khi giải mã, nếu không icon trống và file hỏng nằm lại. Chọn PNG vì giải mã ~1,5 ms/icon (WebP lossless 0,9 ms nhưng cần API 30, ảnh thô 0,4 ms nhưng lớn gấp 4 lần: 43 KB/icon 104 px). Tên file chứa `sourceDir` nên cập nhật app tự trượt cache, không cần xóa chủ động.

## IP — gói icon bên thứ ba (2026-11-10, Oppo A3x)

- **Không đóng gói icon của Apple** (bản quyền/nhãn hiệu): hỗ trợ gói icon kiểu Nova/ADW, người dùng cài gói từ Google Play rồi chọn trong Cài đặt → "Gói biểu tượng".
- **Chuẩn không có đăng ký trung tâm thì liệt kê từng intent và khớp `<queries>` từng dòng** (`org.adw.launcher.THEMES`, `com.novalauncher.THEME`, ...): thiếu một dòng = gói không hiện, không báo lỗi. Đo được: gói "iOS 26 - icon pack" (`com.debojyoti.ios16_icpack`) tìm thấy trong 72 ms, `appfilter.xml` 3.459 mục đọc trong 250 ms (debug); gói này không có iconback/mask nên app không có trong gói giữ icon gốc.
- **Thêm một chiều vào khóa cache thì kiểm đủ ba tầng:** RAM (`IconCache`), đĩa (`diskName`), view (`RecreateGuard`); chỉ nối khóa mới khi khác mặc định để cache cũ vẫn dùng được. FX5 (đo được trên Live 4, 2026-11-11) bổ sung: dấu phiên bản APK phải có ở **mọi** tầng (RAM, đĩa, map của view) và dữ liệu hiển thị phải mang dấu đó để so `==` tự phát hiện đổi; chỉ "xóa đúng khóa" ở một chỗ không đủ vì còn các điểm chặn bind lại (`boundKey`, `==` trong `setItems`).
- **Lựa chọn trỏ tới thứ có thể biến mất (gói bị gỡ) phải có đường thoát:** `IconStyleSource` ghi lại "Không dùng".
- **`list + if (c) a else b + x`** nối `x` vào nhánh `else`: đặt ngoặc quanh `if`.

## FX5 — icon đổi theo cập nhật app, huy hiệu hồ sơ (2026-11-11, Vsmart Live 4)

- **`adb install -r` APK tự dựng đổi `sourceDir` trên Android 11** (FX5, đo được, 2026-11-11): `pm path` khác trước/sau; icon Home không đổi trước FX5 là lỗi tái hiện được.
- **`Drawable` lấy từ framework dùng chung `ConstantState` (và `Paint`) với mọi bản của cùng app** (FX5, đọc tài liệu/mã AOSP, 2026-11-11): gọi `mutate()` trước mọi `setColorFilter`/sửa trạng thái, kể cả trước lần vẽ thăm dò.
- **Huy hiệu hồ sơ vẽ lên bitmap cuối bằng `PackageManager.getUserBadgedIcon`, không lên drawable gốc** (FX5, 2026-11-11): khóa cache đã tách theo `userSerial` nên chỉ cần hậu tố tên file đĩa.

## RN — đổi tên app (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/RN-rename-apps.md](../knowledgements/RN-rename-apps.md)

- Dữ liệu người dùng tự đặt cho một app (tên riêng) áp bằng **lớp phủ sau `loadApps`** ở `AppRepositoryImpl`, không ghi vào cache nhãn LC (cache bị xóa theo locale/fingerprint/stamp) và không vào cache icon (khóa không chứa nhãn). Một chỗ áp là đủ vì mọi nơi hiển thị đều đọc `AppRepository.apps` (đọc code, 2026-11-13). Khóa theo `ComponentKey` gồm `userSerial` để hồ sơ công việc tách riêng (đọc code).
- Với tên riêng, `a11yLabel` của app hồ sơ phải tính lại từ **tên mới** bằng `getUserBadgedLabel`; hạn chế A3 (đổi activity khi cập nhật thì mất tên riêng) được chấp nhận và ghi rõ (đọc code, 2026-11-13; chưa kiểm trên máy).

## FS — icon thư mục co còn ~36dp vì trần dock (2026-11-20) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **Triệu chứng:** trên Tecno Spark 40 (màn 360dp, density 3) icon trong panel thư mục chỉ ~36dp, ô ~83dp, khe trống lớn.
- **Gốc:** trần "icon ≤ 0,8 × chiều cao dock" (polish 2026-11-19) trong `GridMetrics.measure` suy chiều cao dock từ `heightPx / PAGER_WEIGHT`,
  tức giả định `heightPx` là chiều cao pager Home. `FolderPanelView`, `RecentsWidgetView` cũng gọi `measure` với kích thước của riêng
  chúng nên trần tính ra 840 / 6 × 0,8 = 112px → 108px. Số tính khớp ảnh chụp từng pixel.
- **Sửa:** trường `GridMetrics.capToDock` (mặc định `false`), chỉ `GridMetrics.from(preset, …)` (đường pager/dock/Today/widget picker,
  đều đo bằng kích thước pager) đặt `true`.
- **Bài học:** hằng/công thức trong lớp dùng chung mà dựa vào ngữ cảnh của một nơi gọi (ở đây: "heightPx là pager") phải bật theo
  cờ của nơi gọi đó, không áp mặc định cho mọi nơi gọi. Khi thêm luật vào `GridMetrics`, grep mọi chỗ `GridMetrics(` / `.measure(` trước.
