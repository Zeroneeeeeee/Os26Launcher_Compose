# Bài học: Quyền, hiển thị app và kiểm trên máy thật

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Quyền runtime, `<queries>`, app mặc định ở dock, bẫy khi đo/cài trên Oppo và Live 4.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Dock mặc định (M3 Task 4, 2026-10-26/27, đo trên Vsmart Live 4)

- **Giả thuyết "khối MAIN/LAUNCHER của M2 đủ cho mọi `resolveActivity`" chỉ đúng một nửa.** PHONE
  (`Intent.ACTION_DIAL`) và MESSAGES (`ACTION_MAIN` + `CATEGORY_APP_MESSAGING`) tìm ra app mà
  **không cần** khai `<queries>` riêng — hai action này nằm trong nhóm được Android tự động miễn trừ
  lọc tầm nhìn package. BROWSER (`ACTION_VIEW` kèm `http://`, `CATEGORY_BROWSABLE`) và CAMERA
  (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`) thì **không** nằm trong nhóm đó: `resolveActivity`
  trả `null` dù máy có cài trình duyệt/app máy ảnh thật, phải khai thêm 2 khối `<intent>` cho đúng 2
  role này trong `<queries>` (xem `AndroidManifest.xml`). Kết luận chung: **đừng giả định cả nhóm
  intent "liên quan launcher" được miễn trừ như nhau — mỗi action phải đo riêng.**
- **`resolveActivity(MATCH_DEFAULT_ONLY)` có thể trả về activity "trung gian" khác với activity
  LAUNCHER mà `LauncherApps.getActivityList()` liệt kê**, dù cùng app. Ví dụ đo được: Chrome trả
  `com.google.android.apps.chrome.IntentDispatcher` (activity xử lý deep-link `VIEW`), app máy ảnh
  Vsmart trả `com.bq.camera3.common.StillCaptureManagerActivity` (activity chụp ảnh), không phải
  activity icon màn hình chính của hai app đó. PHONE/MESSAGES tình cờ trả đúng activity LAUNCHER nên
  ban đầu tưởng nhầm so cả `ComponentKey` (package + class) là đủ. **Cách đúng:** `PickDefaultDockUseCase`
  chỉ so theo `packageName` để tìm `AppItem` tương ứng trong `apps` (nguồn từ `LauncherApps`), rồi mở
  app qua `ComponentKey` LAUNCHER thật của `AppItem` đó — không bao giờ launch thẳng component mà
  `resolveActivity` trả về.
- Kết quả đo trên Vsmart Live 4 (87 app, 2026-10-27): PHONE → `com.vinsmart.android.dialer`,
  MESSAGES → `com.vinsmart.messaging`, BROWSER → `com.android.chrome` (Chrome), CAMERA →
  `com.vinsmart.camera`. Không cần dùng tới đường bù "app đầu bảng chữ cái" trên máy này sau khi sửa
  2 điểm trên.
- Quy trình debug đã dùng, khớp đúng thứ tự AGENTS.md quy định: rải `Log.d` dọc `DefaultAppResolver`
  (in nguyên trạng `resolveActivity` trả về) và `PickDefaultDockUseCase` (phân biệt rõ "resolver trả
  null" với "resolver trả component nhưng không khớp app nào trong `apps`") — nhờ log phân biệt được
  hai trường hợp này mà lần sửa thứ hai (so theo packageName) đúng ngay, không phải đoán vòng hai.

## Script báo exit 0 sai (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Một script báo "exit 0" vì `$LASTEXITCODE` cũ** khi lệnh không tồn tại (thư mục bản sao thiếu `gradlew.bat`): kiểm file có tồn tại và có APK mới trước khi tin kết quả build.

## Cài lại bản debug làm mất launcher mặc định (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Cài lại bản debug nhiều lần có thể làm Oppo mất launcher mặc định** (nhấn Home hiện hộp chọn launcher; role HOME vẫn là `com.android.launcher`): báo chủ dự án, không tự chọn thay.

## DD — resolveActivity trả hộp chọn (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **DD — `resolveActivity` trả hộp chọn khi chưa đặt mặc định** (`android/com.android.internal.app.ResolverActivity`, có máy là `com.android.intentresolver`): lọc theo tiền tố `android.permission` không bắt được. Nhận diện hộp chọn bằng package + tên lớp, và kết quả phải nằm trong `queryIntentActivities`. Lấy app mặc định thật trước: `TelecomManager.getDefaultDialerPackage` và `Telephony.Sms.getDefaultSmsPackage` trả đúng từ trong app, không cần `<queries>` thêm (đo Oppo).

## DD — số đo shell khác số đo trong app (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Số đo từ shell khác số đo trong app:** `cmd package resolve-activity` trên Oppo cho hộp chọn, còn trong app `resolveActivity` trả Chrome. Kết luận phải dựa trên log trong app.

## DG — sao lưu dữ liệu app trước khi thử (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Sao lưu dữ liệu app trước khi thử phải lấy CẢ thư mục `files/`** (ảnh nền chọn nằm ở `files/backdrop_image`) và so hash từng file sau khi khôi phục, kể cả `-wal`/`-shm`: một lần `tar xf` qua `run-as` ra file cụt mà vẫn exit 0.

## RB — bản release không run-as được (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Bản release không `run-as` được:** sao lưu dữ liệu TRƯỚC khi cài release; quay lại bằng `adb install -r -d` bản debug (cùng khóa) rồi khôi phục. Bản release để lại file `files/profileinstaller_profileWrittenFor_lastUpdateTime.dat`, vô hại.

## G3a — từ chối vĩnh viễn cần cờ đã xin (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Phân biệt "từ chối vĩnh viễn" cần cờ "đã xin"**: `shouldShowRequestPermissionRationale == false` cũng đúng trước lần xin đầu. Đo: Android 11 (Live 4) và Android 14 (Oppo) đều đổi sang "mở Cài đặt" sau hai lần từ chối.

## G3a — quyền runtime từ controller (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Quyền runtime từ controller:** tháo `ContextWrapper` lấy `ComponentActivity`, đăng ký `activityResultRegistry` trong `onCreate`; đọc lại quyền ở ON_RESUME khi quay về từ Cài đặt hệ thống.

## G3a — ColorOS chặn pm grant (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **ColorOS chặn `pm grant` qua adb** (thiếu `GRANT_RUNTIME_PERMISSIONS`): cấp quyền khi thử bằng giao diện Cài đặt (lựa chọn tên "Luôn luôn"). Thu hồi quyền khi app đang chạy: Android khởi động lại tiến trình, không crash.

## G3a — danh bạ thử trên máy thật (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Danh bạ thử trên máy thật:** tạo bằng `content insert` vào `raw_contacts` với `account_type`/`account_name` rỗng (tài khoản máy, không đồng bộ), nhớ id rồi xóa bằng `content delete ...raw_contacts/<id>?caller_is_syncadapter=true`. App Danh bạ/Quay số của máy có thể tự log tên và lưu lịch sử tìm số.

## FX1, FX2, FX4, FX6 — kiểm trên máy thật (2026-11-11, Vsmart Live 4)

- **Ảnh nền quá cỡ khi Os26Launcher là launcher mặc định làm máy sập lặp** (FX1, 2026-11-11): chỉ đặt ảnh thử trên bản đã sửa và luôn sẵn lệnh thoát hiểm `rm files/backdrop_image` (chỉ chạy với bản debug). Bộ chọn ảnh hiện thư viện riêng của chủ dự án nên điều phối không tự chọn ảnh qua giao diện.
- **Tick CPU theo luồng bằng `/proc/<pid>/task/*/stat`** (FX2, đo được, 2026-11-11): script `ticks.sh` không cần `top` hay `simpleperf`; giữ `ticks.sh` ngoài repo, đẩy bằng `adb push`.
- **Kiểm tính năng danh bạ khi cấm cấp quyền thật** (FX4, 2026-11-11): dùng nguồn giả gắn thế qua Hilt trong một commit tạm có dấu (`FX4_TEMP_FAKE_CONTACTS`) rồi `git revert`; ghi rõ phần không kiểm được (provider thật, `CancellationSignal` thật).
- **`RESULT_CANCELED` của hộp thoại xin vai trò Home không phân biệt "người dùng hủy" với "hệ thống không hiện"** (FX6, đo được trên Live 4 debug, 2026-11-11): phân biệt bằng thời gian. Không hiện: 49-176 ms; Hủy thật nhanh nhất ~1309 ms; ngưỡng 352 ms. Trên Live 4 + PermissionController Google (Android 11) "Không hỏi lại" không tái hiện được (hộp thoại role vẫn hiện), nên cách sửa cho ca đó là suy luận.
- **Cách thử "không hiện hộp thoại" an toàn** (FX6, đo được, 2026-11-11): xin một role không tồn tại (`createRequestRoleIntent("android.app.role.X")`) trả `RESULT_CANCELED` tức thì (`code=0` sau 49-176 ms, không có UI), không đụng trạng thái từ chối thật nên không phải xóa dữ liệu PermissionController.
- **Bản vá thử có cổng theo cờ file trong `files/`** (một lần, tự xóa; FX6, 2026-11-11) cho phép ép đường lỗi mà không có vòng sập trên launcher mặc định.
- **`cmd role add-role-holder/get-role-holders --user 0 android.app.role.HOME`** (FX6, đo được trên Live 4, 2026-11-11): đặt và kiểm launcher mặc định qua adb.

## FX5 — hồ sơ công việc và ngôn ngữ (2026-11-11, Vsmart Live 4)

- **Tạo hồ sơ công việc tạm** (FX5, đo được, 2026-11-11): `pm create-user --profileOf 0 --managed Work` + `am start-user`, gỡ bằng `pm remove-user`. Tạo hồ sơ làm launcher đặt ~30 icon mới vào bố cục thật nên phải sao lưu và trả DB.
- **Đổi ngôn ngữ bằng công cụ `app_process`** (FX5, đo được, 2026-11-11): lần đổi sang ngôn ngữ mới bị Play cài gói `config.<lang>` che lỗi; kiểm chiều đã có gói và cặp `en-US` ↔ `en-GB`.

## Điều phối 2026-11-11 — cài APK, sao lưu, cử chỉ nhanh (Vsmart Live 4)

- **Kiểm mọi APK gọi là "main" trước khi đo** (đo được, 2026-11-11): grep trong dex một chuỗi chỉ mã mới có, và so md5 với APK đã cài trước đó. Git Bash `cmd //c "gradlew.bat ..."` từng trả về mà không build gì.
- **Sau khi chủ dự án merge, cài bản main thật dựng lại** (đo được, 2026-11-11), không bao giờ cài lại APK "gốc" trước đó.
- **Không mở `launcher.db` của bản sao lưu bằng sqlite tại chỗ** (đo được, 2026-11-11): sqlite checkpoint WAL và đổi byte. Chép ra chỗ khác trước, hoặc khôi phục từ tar.
- **Khôi phục dữ liệu app khi Os26 tạm thời không phải launcher mặc định** (đo được, 2026-11-11): `cmd role add-role-holder --user 0 android.app.role.HOME <launcher khác>`; nếu không Home khởi động lại giữa lúc ghi.
- **`adb shell input` tốn 0,3–0,6 s mỗi lệnh** (đo được, 2026-11-11) nên không tái hiện được cử chỉ nhiều vuốt nhanh. Dùng bộ bơm trong tiến trình `E:\SourceTree\IosLauncher-wt\tools\Swiper.java` (`app_process` + `InputManager.injectInputEvent`, MOVE mỗi 8 ms). **Bộ bơm phải đóng dấu mỗi sự kiện ở giờ đã lên lịch (`down + k·8 ms`) và bơm ASYNC** (đo được, FX7, 2026-11-12): giờ lúc bơm + `WAIT_FOR_FINISH` biến một khung nặng của app thành khoảng hở >= 40 ms giữa hai mẫu, `VelocityTracker` xóa lịch sử và pager thấy vận tốc 0 (xem mục FX7 bên dưới).

## FX7 — đo cử chỉ trên máy thật (2026-11-11 tới 2026-11-12, Vsmart Live 4)

- **Bộ bơm cử chỉ làm hỏng phép đo nếu đóng dấu giờ lúc bơm** (đo được, 2026-11-12): `tools/Swiper.java` phải đóng dấu mỗi sự kiện ở giờ đã lên lịch (`down + k·8 ms`) và bơm ASYNC (đã ghi ở mục "Điều phối 2026-11-11" ở trên). Bộ cũ (`WAIT_FOR_FINISH`, giờ lúc bơm) tạo "cú vuốt trang bị mất" mà app không có; đã đốt một vòng (`PageFlingAssist`, hoàn tác) vì tin nhầm số đo đó. Khi số đo cử chỉ nhanh lạ, kiểm bộ bơm trước khi sửa app.
- **`adb shell input swipe 30 ms` chỉ có 1-2 `MOVE`** (đo được, 2026-11-11): khung đầu `p = 0,24`, là artifact; dùng 80/120 ms và tay thật. Mỗi lệnh `input` tốn 0,3-0,6 s nên không dựng được "hai flick cách 120 ms"; `input` không có đa điểm (dùng `sendevent` hoặc tay chủ dự án).
- **Số đo cử chỉ: `screenrecord` + tương quan chéo** (đo được, 2026-11-11) ra độ dời trang từng khung; hook làm chậm gắn nhãn `FX7TMP` trong patch ngoài repo để tái hiện lỗi cửa sổ hẹp.
- **`git pull --rebase` trên nhánh đã push viết lại lịch sử** (2026-11-11): dùng `reset --hard origin/<nhánh>` để quay lại.

## RN — đổi tên app (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/RN-rename-apps.md](../knowledgements/RN-rename-apps.md)

- **`permissions-and-device-testing`** (làm việc với agent, không phải máy) — Trong Git Bash, **hai heredoc `<<'EOF'` lồng nhau** (một cho `python`, một cho `cat`) làm cả lệnh lỗi "unexpected EOF" và không áp gì cả; viết file bằng công cụ Write/Edit hoặc dùng dấu kết thúc khác nhau (đo được, 2026-11-13). Công cụ Edit đòi đã `Read` file trong cùng phiên; file Kotlin của dự án là CRLF và Edit giữ đúng CRLF (đo được).

## WU — đơn vị °C/°F (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/WU-weather-units.md](../knowledgements/WU-weather-units.md)

- (2026-11-13, đo được trong phiên làm việc) Bash heredoc dài chứa tiếng Việt có thể báo `unexpected EOF` và không chạy gì; dùng Write/Edit. Build lỗi `compileDebugJavaWithJavac` không kèm lỗi Kotlin (cache tăng dần cũ) hết khi chạy `:app:compileDebugKotlin --rerun`. Chủ đề: `permissions-and-device-testing` hoặc AGENTS.md mục môi trường.

## AB — môi trường máy dev (2026-11-13). Nguồn: [knowledgements/AB-about-section.md](../knowledgements/AB-about-section.md)

- **(2026-11-13, môi trường, AB)** Trên máy dev Windows, `/tmp` của Git Bash trỏ tới `%TEMP%` dùng chung với các phiên khác: file script có sẵn ở đó (vd `ed.py`) có thể của phiên khác. Đặt script tạm ở scratchpad của phiên, hoặc đặt tên có tiền tố nhánh.
- **(2026-11-13, môi trường, AB)** Build sạch lần đầu trong worktree mới mất khoảng 5 đến 6 phút (`assembleDebug` 5m44s, `assembleRelease` 6m15s, đo được); các lần sau 12 đến 40 giây.

## CP — dữ liệu thử cho quyền nhạy cảm (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/CP-contacts-phone.md](../knowledgements/CP-contacts-phone.md)

- **Dữ liệu thử cho tính năng cần quyền nhạy cảm: nguồn giả ở source set `debug` + Hilt `@Binds` theo build type** (`src/debug` và `src/release` mỗi nơi một `ContactsModule`, gỡ binding khỏi `main`). Mã giả không biên dịch vào release; kiểm bằng cách tìm tên lớp trong dex. Tốt hơn cờ `BuildConfig.DEBUG` (dựa vào R8 cắt) và commit tạm rồi revert (như FX4).
- **Receiver chỉ debug nhận `adb shell am broadcast`:** shell uid không phải uid app nên cần `exported="true"`; gắn `android:permission="android.permission.DUMP"` để app khác không bật được. Đo được trên Live 4.
- **(2026-11-13, đo được, Live 4)** Thu hồi quyền bằng `pm revoke` trên Android 11 để lại cờ `USER_SET`; `pm clear-permission-flags` không có ở API 30. Ghi lại trạng thái quyền trước khi cấp tạm để báo đúng cho chủ dự án.
- **(2026-11-13, quy trình)** Danh bạ thật chỉ được đọc khi chủ dự án cho phép rõ ràng và trong phạm vi họ nói (lần này: một số điện thoại); kịch bản chỉ in hàng khớp số đó, các hàng khác chỉ đếm, không chụp màn hình, không bấm gọi/nhắn.

## WX — thời tiết nhiều địa điểm (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/WX-weather-cities.md](../knowledgements/WX-weather-cities.md)

- (2026-11-13, đo được, Live 4) **Kiểm bộ đệm "đã cũ N phút" phải chừa lề cho nhịp phút:** đặt tuổi 1h59 rồi chờ nhịp phút ~70 giây thì tuổi thật là 2h01 và làm mới chạy, bị đọc nhầm là lỗi. Dùng 1h55 (hoặc ngưỡng − 5 phút) và đọc log lý do (`why=… attemptMin=…`) trước khi kết luận. Chủ đề đề xuất: `permissions-and-device-testing`.
- (2026-11-13, đo được, Live 4) Lấy vị trí một lần bằng `LocationManager` ở API 30 ra tên nơi sau khoảng 4,8 giây; `dumpsys location` thấy yêu cầu mạng thêm rồi gỡ sau khoảng 2 giây (không sót). Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, đo được trong phiên làm việc) Trong Git Bash của Claude Code: heredoc dài chứa tiếng Việt báo `unexpected EOF` và không chạy gì (dùng Write/Edit); `sed -i 'N a\\\\n...'` chèn chữ `n` thật ở đầu dòng; công cụ Edit/Write yêu cầu đã Read file đó trong phiên. Chủ đề: `permissions-and-device-testing` hoặc AGENTS.md mục môi trường.

## WN — thông báo thời tiết (2026-11-13/16, Live 4 + Oppo). Nguồn: [knowledgements/WN-weather-notifications.md](../knowledgements/WN-weather-notifications.md)

- (2026-11-13, **đo được**, Live 4) **Job `JobScheduler` cần mạng khi màn hình tắt có thể bị hoãn tới lúc bật màn hình** (17 phút 42 giây, `dumpsys jobscheduler` hiện "idle … RUNNABLE"): việc cần xong ngay lúc báo thức nổ phải làm trong receiver (`goAsync`, vài giây), job chỉ là đường lùi. Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, **đo được**) `setAndAllowWhileIdle` nổ đúng giờ (+3 phút 14 giây) và nổ cả khi `deviceidle force-idle` (Doze sâu), receiver chạy ngay (37 ms tới lúc ghi kết quả). Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, **đo được**) Receiver `@AndroidEntryPoint` bằng Kotlin không gọi được `super.onReceive` (`Abstract member cannot be accessed directly`): dùng `@EntryPoint` + `EntryPointAccessors.fromApplication`; tên hàm trong các `@EntryPoint` cùng `SingletonComponent` phải khác nhau (trùng tên khác kiểu trả về là lỗi biên dịch Hilt `Found conflicting entry point declarations`). Chủ đề: `settings-and-data` hoặc AGENTS.md mục môi trường.
- (2026-11-13, **đo được**) Báo thức mất khi `force-stop` và tự có lại khi tiến trình tạo (`rearm()` ở `LauncherApp`); `adb install -r` giữ báo thức và gửi `MY_PACKAGE_REPLACED`. Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, **đo được**) Chuỗi báo thức tự nối: đặt báo thức kế tiếp **trước** khi làm việc, và ở nhánh "vừa nổ" dùng hàm riêng (không dùng hàm đặt lại chung vì ân hạn trả "ngay" làm báo thức nổ lặp). Chủ đề: `settings-and-data`.
- (2026-11-13, **chưa kết luận**) `service call alarm 3 s16 <múi giờ>` đổi `persist.sys.timezone` nhưng không thấy `TIMEZONE_CHANGED` tới app: thử đổi múi giờ bằng giao diện Cài đặt. Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, đọc tài liệu) Android 13+: app cài mới có thông báo tắt, tự quyết lúc xin quyền; kiểm `areNotificationsEnabled()` trước khi tải dữ liệu cho thông báo; kênh tạo lười, mức quan trọng không đổi được bằng code. Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, đọc tài liệu bên thứ ba) dontkillmyapp.com không có dữ liệu báo thức trên Oppo A3x (chỉ F1S/ColorOS 6): đừng suy ra hành vi ColorOS hiện tại từ đó. Chủ đề: `permissions-and-device-testing`.
- (2026-11-13, **đo được**) Sau `am force-stop`, broadcast tường minh (`-p`) không tới app cho đến khi app chạy lại: script kiểm phải mở app trước khi gửi broadcast debug. Chủ đề: `permissions-and-device-testing`.

## G3b — ảnh thử trên máy (2026-11-13, Live 4). Nguồn: [knowledgements/G3b-photo-widget.md](../knowledgements/G3b-photo-widget.md)

- 2026-11-13 [đo được, Live 4 API 30] Ảnh đẩy bằng `adb push` nằm ở `is_pending=1` nên bộ chọn không thấy; chạy `adb shell content call --uri content://media/none --method scan_volume --arg external_primary`. Module Google Photo Picker trên API 30 chỉ hiện các album Camera/Screenshots/Downloads; để ảnh thử vào một trong các thư mục đó. Chủ đề `permissions-and-device-testing`.

## G3c — widget của app khác (2026-11-13/16, Live 4 + Oppo). Nguồn: [knowledgements/G3c-appwidget-host.md](../knowledgements/G3c-appwidget-host.md)

- (P, 2026-11-13, **đo được**) Broadcast tường minh `-p <package>` không tới app đang ở trạng thái stopped (sau `force-stop`) cho tới khi app được mở lại; công cụ đo debug qua `AW_PROBE` cần app đang chạy.
- (P, 2026-11-13, **đo được** Live 4) Lựa chọn "Luôn cho phép" của hộp thoại gắn widget được cấp theo package và sống qua cài đè; muốn thấy lại hộp thoại thì dùng widget của app khác chưa từng cho phép.
- **(2026-11-18, suy luận chưa kiểm chứng, PB Task 4) Ẩn thanh điều hướng bằng `hide(navigationBars())` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`:** theo tài liệu và bài viết về Android 12, máy dùng cử chỉ ở Android 10–11 có thể phải vuốt 2 lần (lần đầu chỉ hiện thanh tạm) mới về Gần đây; Android 12+ cải thiện cử chỉ khi thanh ẩn. Live 4 (Android 11) chủ dự án kiểm đạt 2026-11-18; Oppo (Android 14) chưa đo, số lần vuốt chưa ghi. Hộp thoại là cửa sổ riêng có thể làm thanh hiện lại: Home áp lại ở `onResume` và `onWindowFocusChanged(true)`.

## JS + JX — toàn bộ UI sang Compose, gỡ lớp View (2026-11-20, Samsung SM-A176B). Nguồn: [knowledgements/JS-JX-compose-all-screens.md](../knowledgements/JS-JX-compose-all-screens.md)

- **Git Bash đổi đường dẫn `/data/...` trong tham số `adb`** (quan sát): đặt `MSYS_NO_PATHCONV=1` trước lệnh `adb` có đường dẫn trên máy.
- **`adb shell input swipe` từ mép trên không mở được CC panel trên Samsung** (quan sát): hệ thống bắt cú vuốt; nhờ người mở tay.
- **Không bấm HOME/BACK ở gốc Home khi app chưa là launcher mặc định** (quan sát): rơi về launcher của chủ máy.

## Khóa sinh trắc / khóa màn hình (HL, 2026-11-20) (chép từ repo XML)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (và `DEVICE_CREDENTIAL` riêng) chỉ dùng được từ API 30. API 26–29: hộp `BIOMETRIC_STRONG` kèm nút phủ định, bấm nút thì mở `KeyguardManager.createConfirmDeviceCredentialIntent` (`HiddenAppsAuthenticator`).
- Màn xác thực của hệ thống ở API 26–29 là Activity khác: màn gọi bị `onStop`. Khóa lại ở `onStop` phải bỏ qua lúc đang xác thực (`inProgress`), nếu không xác thực xong lại bị khóa ngay.
- Có hộp với `DEVICE_CREDENTIAL` thì không được đặt nút phủ định (thư viện ném lỗi).
- Máy không có khóa màn hình (`KeyguardManager.isDeviceSecure() == false`): không khóa được; app này cho vào thẳng và ẩn công tắc, giữ lựa chọn để đặt lại khóa máy là có hiệu lực.

## PM — biết khi nào quyền "bị chặn" (2026-11-26, đọc tài liệu Android + code cũ, chưa đo trên máy)

- Android không có API "người dùng đã chặn quyền". Từ Android 11, từ chối hai lần là hệ thống tự trả "từ chối" mà không hiện hộp; chạm ra ngoài hộp
  KHÔNG tính là từ chối. `shouldShowRequestPermissionRationale` = true sau lần từ chối đầu, = false cả khi chưa hỏi lần nào lẫn khi đã bị chặn.
- Cách dùng (plan PM, `core/permission/PermissionRequester`): sau kết quả từ chối, nếu rationale trước HOẶC sau là true thì ghi cờ "đã từ chối rõ
  ràng" (SharedPreferences `permission_denials`). Lần bấm sau: cờ bật + rationale false = bị chặn → hộp "Cấp quyền" của app (mở Thông tin ứng
  dụng). Cấp quyền thì xóa cờ. Nhờ vậy lần chạm ra ngoài đầu tiên không bị nhầm thành "bị chặn", và lần từ chối thứ hai không bật hộp riêng ngay.
- Cách cũ (`contactsAsked` ghi TRƯỚC khi hỏi + "rationale trước và sau đều false") nhầm lần chạm ra ngoài đầu tiên thành bị chặn.
- Mọi chỗ xin Vị trí / Danh bạ phải đi qua `PermissionRequester` (không tự `registerForActivityResult` + mở Cài đặt).
