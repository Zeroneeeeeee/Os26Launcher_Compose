# G3d — Trung tâm điều khiển toàn hệ thống (TT#21) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau. Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính hoặc ColorOS). Lệnh viết cho CMD/PowerShell. **Không commit khi chủ dự án chưa xem code và chưa bảo.**

**Nhánh:** `feature/g3d-control-center` · **Worktree:** `D:\Workspace\Os26Launcher-wt\G3d-control-center` · **PR:** chưa · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (chủ dự án chọn phạm vi toàn hệ thống, có ô nhạc, làm trước AS)
**Trạng thái:** Đã code Task 1–8 (2026-11-11, đủ chức năng bản gốc theo RE mục 5), build xanh, chưa commit, **chưa chạy trên máy nào**. Chờ kiểm tay Task 4, 7, 8.
**Khóa:** không đổi schema Room. Thêm quyền và một service vào `AndroidManifest.xml`. Không sửa `core/glass/*`, `HomeActivity.kt` hay `activity_home.xml`.

**Goal:** giống Launcher OS. Một dải chạm mảnh ở mép trên bên phải nằm trên **mọi app** (cả Home). Vuốt xuống từ dải đó thì một bảng điều khiển kiểu iOS kéo xuống theo ngón tay, thả ra thì chạy nốt, nền là ảnh nền mờ. Bảng có các ô:
- Wi-Fi, Bluetooth, Dữ liệu di động, Máy bay;
- trình phát nhạc;
- khóa xoay, Không làm phiền;
- thanh độ sáng và âm lượng;
- lối tắt Đèn pin, Hẹn giờ, Máy tính, Máy ảnh.

**Architecture:**
- **domain:** `ControlCenterState` và cổng `SystemControlsPort` (đọc trạng thái, nhận lệnh, trả `ControlResult`). Cổng `ControlCenterPrefsRepository` giữ công tắc bật. `ControlPermission` liệt kê các quyền đặc biệt.
- **data:**
  - `AndroidSystemControls` hiện thực bằng `WifiManager`, `BluetoothAdapter`, `CameraManager`, `AudioManager`, `Settings.System`, `NotificationManager`, `MediaSessionManager`;
  - `ControlCenterPrefsStore` dùng một DataStore riêng `control_center`, nên không đụng file cài đặt chung;
  - `AndroidControlPermissions` kiểm tra quyền.
- **ui/controlcenter:**
  - `ControlCenterService` kế thừa `NotificationListenerService` và là `@AndroidEntryPoint`. Service này giữ process và là `ComponentName` cho `MediaSessionManager`, như `LauncherNotificationService` của app mẫu.
  - `ControlCenterOverlay` quản lý hai cửa sổ `TYPE_APPLICATION_OVERLAY`: dải mép `EdgeTriggerView` và panel `ControlCenterView`.
  - `ControlCenterMotion` tính hoạt ảnh theo tiến độ.
  - `ControlCenterSettingsActivity` là màn bật tính năng và cấp quyền.
- **Nền:** ảnh nền của launcher (`BackdropRepository`) thu nhỏ rồi làm mờ một lần. API 31+ thêm `RenderEffect` blur. Không blur trực tiếp app phía sau, giống app mẫu.

**Tech Stack:** Kotlin, View/Canvas, `WindowManager`, `PathInterpolator`, `GestureDetector`. Không dependency mới.
**Spec:** TT#21 trong `Launcher-Features.md`. Đầu vào phân tích ngược: [LauncherOS-ControlCenter-AppSwitcher-RE.md](../LauncherOS-ControlCenter-AppSwitcher-RE.md) mục 2–3. File RE nằm ở thư mục chính, chưa commit lên `main`.

---

## Đầu vào: Launcher OS làm thế nào (tóm tắt từ RE)

- **Cửa sổ:**
  - dải mép: `type 2038`, flags `NOT_FOCUSABLE|NOT_TOUCH_MODAL|LAYOUT_IN_SCREEN|LAYOUT_NO_LIMITS` (808), gravity TOP|END;
  - panel: `type 2038`, flags `NOT_TOUCH_MODAL|LAYOUT_IN_SCREEN|LAYOUT_NO_LIMITS|FULLSCREEN` (1824), focusable nên nhận được Back. Panel chỉ `addView` khi bắt đầu kéo và `removeView` khi đóng.
- **Giữ process:** `NotificationListenerService` của launcher gọi `ControlNotificationService.onCreate/c()/a()`. Điều kiện hiện dải: công tắc bật, có quyền đọc thông báo, `canDrawOverlays`, được miễn tối ưu pin.
- **Cử chỉ trên dải:** khóa trục sau 11 px; `onScroll` gửi `dy` để panel bám tay; thả tay thì mở hoặc đóng nếu quãng đi > 50 px theo chiều vuốt, còn lại bật về.
- **Hoạt ảnh:**
  - nền có `alpha = clamp(6·dy/H)`;
  - ô hiện khi vượt 110 px, rồi dịch `(dy−110)·speed/3` với `speed` 1,45 hoặc 1,6;
  - thả tay thì chạy nốt bằng `PathInterpolator(0.16, 0.545, 0.455, 1)`, 150–730 ms.
- **Mở activity từ panel:** đóng panel trước rồi mới `startActivity(NEW_TASK)`.

## Vì sao chọn thiết kế này

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ trong Home | Panel là view trong `activity_home.xml`, vuốt từ dưới thanh trạng thái | Không quyền đặc biệt, không service | Chỉ mở được ở Home | Không (chủ dự án 2026-11-10) |
| B. Toàn hệ thống như Launcher OS | Hai cửa sổ overlay, NotificationListener giữ process | Mở được từ mọi app, giống app mẫu | Bốn quyền đặc biệt. ROM có thể giết service. Panel không dùng được kính tại chỗ của Home | **Chọn** (chủ dự án 2026-11-10) |
| C. Ô Quick Settings | `TileService` | Rẻ | Không có panel kiểu iOS | Không |

**Vì sao làm B theo cách này:**
- **Dùng NotificationListener làm service chủ**, không dùng foreground service: hệ thống tự bind và tự khởi động lại NotificationListener sau khi người dùng cấp quyền đọc thông báo, mà quyền này ô nhạc vốn phải có. Foreground service sẽ phải giữ một thông báo thường trực, và từ API 34 cần khai loại foreground service.
- **Panel là view thuần**, không biết mình nằm trong cửa sổ nào. Sau này muốn có bản "chỉ trong Home" thì chỉ cần thêm host.
- **Nền dùng ảnh nền của launcher**, không dùng `WallpaperManager`: wallpaper hệ thống cần `READ_EXTERNAL_STORAGE`/`READ_WALLPAPER_INTERNAL` (B6, Quyết định 1 của demo). Ảnh nền của launcher thì app đã có sẵn.

**Đánh đổi chấp nhận:**
- Dải mép chiếm một vùng chạm nhỏ ở mép trên mọi app.
- Process sống nền, nên tốn RAM một chút. Pin không tốn thêm: không có vòng lặp, receiver chỉ đăng ký khi panel mở.
- Mỗi ROM mỗi khác, nên phải kiểm tay.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Phạm vi B, toàn hệ thống | Chủ dự án, 2026-11-10 |
| D2 | Dải mép: góc trên bên phải, rộng 35% màn hình, cao 22 dp, đặt ngay dưới thanh trạng thái (`y = statusBarHeight`). Có vạch mờ 4 dp để thấy chỗ vuốt | Claude đề xuất. Overlay luôn nằm dưới thanh trạng thái, nên chạm trong thanh trạng thái vẫn mở bảng thông báo của hệ thống. Chỉnh vị trí như app mẫu để sau |
| D3 | Mở/đóng: thả tay khi đã kéo ≥ 1/5 chiều cao, hoặc fling xuống > 50 dp, thì mở; còn lại đóng. Chạy nốt bằng `PathInterpolator(0.16, 0.545, 0.455, 1)`, `duration = clamp(remaining·730/700, 150, 730)` ms | Học từ RE mục 2.5 |
| D4 | Đóng: chạm vùng trống, vuốt lên trên panel, Back, nút Home (broadcast `CLOSE_SYSTEM_DIALOGS` lý do `homekey`) | Như app mẫu. Broadcast `homekey` là suy luận **[I]**, kiểm ở Task 4 |
| D5 | Wi-Fi: API < 29 bật/tắt trực tiếp; ≥ 29 đóng panel rồi mở `Settings.Panel.ACTION_WIFI` | Giới hạn Android |
| D6 | Bluetooth: < 31 bật/tắt trực tiếp; 31–32 bật/tắt trực tiếp khi có `BLUETOOTH_CONNECT`; ≥ 33 bật bằng `ACTION_REQUEST_ENABLE`, tắt bằng intent `android.bluetooth.adapter.action.REQUEST_DISABLE` (hộp xác nhận của hệ thống, như app mẫu); cả hai cần `BLUETOOTH_CONNECT`, ROM thiếu action thì mở Cài đặt Bluetooth | `enable()/disable()` không còn tác dụng với target 33+ (đọc tài liệu). Sửa 2026-11-11 theo góp ý chủ dự án: trước đó tắt luôn mở Cài đặt |
| D7 | Dữ liệu di động: `Settings.Panel.ACTION_INTERNET_CONNECTIVITY`. Máy bay: `ACTION_AIRPLANE_MODE_SETTINGS` | Không có API công khai |
| D8 | Độ sáng và khóa xoay cần `WRITE_SETTINGS`. Không làm phiền cần cấp quyền Không làm phiền. Chạm ô khi thiếu quyền thì đóng panel rồi mở màn cài đặt CC tại đúng hàng quyền đó | Quyền đặc biệt |
| D9 | Có ô nhạc ở bản đầu | Chủ dự án, 2026-11-10 |
| D10 | Ô là tấm phẳng mờ (`cc_tile_bg`), bo 22 dp, trên nền ảnh mờ phủ tối. Không kính thật vì overlay không nằm trong cây view của Home | Rẻ, giống app mẫu (`#99000000`) |
| D11 | Bật/tắt trong màn `ControlCenterSettingsActivity`, mở từ Cài đặt của launcher. Mặc định **tắt**, vì phải cấp quyền mới chạy được | Claude đề xuất |
| D12 | Miễn tối ưu pin: mở `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (danh sách), **không** khai `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Chính sách Google Play hạn chế quyền đó (đọc tài liệu). Chỉ khuyên dùng, không bắt buộc |

## Ngoài phạm vi / ghi để sau

- Ẩn dải khi app toàn màn hình (app mẫu có "Hide Edge"); dải ở góc dưới (vướng cử chỉ điều hướng).
- Ô "Chụp màn hình" của app mẫu: không thấy hành động nào trong code đã đọc (RE mục 5.3), và app thường không chụp được màn hình app khác nếu không có AccessibilityService. Không làm.
- Kho ảnh nền tải từ máy chủ, ngôn ngữ riêng, hướng dẫn Lottie, quảng cáo của app mẫu.
- Blur trực tiếp app phía sau bằng `FLAG_BLUR_BEHIND` (API 31+, chỉ chạy được khi máy bật cross-window blur).
- **Đã kéo vào phạm vi (chủ dự án 2026-11-11, "clone tất cả"):** chọn và sắp xếp ô, 3 trang dọc, đổi nền, Night Shift, ghi màn hình, Điểm phát, chỉnh dải mép. Xem Task 8.

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `domain/model/controlcenter/*` (`ControlTile`, `ControlCenterState`, `ControlResult`, `ControlPermission`, `MediaState`) | Tạo | — |
| `domain/controlcenter/SystemControlsPort.kt`, `ControlCenterPrefsRepository.kt`, `ControlPermissionsPort.kt` | Tạo | — |
| `data/controlcenter/*` (`AndroidSystemControls`, `ControlCenterPrefsStore`, `AndroidControlPermissions`, `MediaSessionSource`, `BackdropBlur`) | Tạo | — |
| `di/ControlCenterModule.kt` | Tạo | — |
| `ui/controlcenter/*` (`ControlCenterService`, `ControlCenterOverlay`, `EdgeTriggerView`, `EdgeGestureTracker`, `ControlCenterView`, `ControlTileView`, `ControlSliderView`, `MediaTileView`, `ControlCenterMotion`, `ControlCenterPresenter`, `ControlCenterSettingsActivity`, `SystemIntents`) | Tạo | — |
| `AndroidManifest.xml` | Sửa: quyền, service, activity, `<queries>` | dùng chung |
| `ui/settings/SettingsActivity.kt`, `SettingsRow.kt` | Sửa: một hàng mở màn cài đặt CC | dùng chung |
| `res/values/strings.xml`, `values/colors.xml`, `values/dimens.xml`, `res/drawable/ic_cc_*.xml`, `res/layout/activity_control_center_settings.xml` | Sửa (khối cuối, tiền tố `cc_`) / Tạo | dùng chung |

G3d không sửa `HomeActivity.kt` hay `activity_home.xml` (file độc quyền). App Switcher (nhánh AS) đã bị chủ dự án bỏ ngày 2026-11-11, nên không còn nhánh nào chồng file với G3d.

## Global Constraints

- `ui` không import `data`. Intent mở màn hệ thống dựng ở `ui/controlcenter/SystemIntents.kt` từ `ControlResult.OpenSystemPanel(kind)`.
- Mọi `addView`/`removeView`/`updateViewLayout` bọc `try/catch` (`BadTokenException`, `IllegalArgumentException`): ROM có thể thu quyền vẽ đè bất cứ lúc nào.
- Receiver, observer và callback media chỉ đăng ký khi panel đang mở. Lúc nghỉ chỉ có dải mép.
- Service không giữ tham chiếu tới Activity. Mọi view của overlay dựng bằng context của service.
- Ghi `Settings.System` chỉ khi `Settings.System.canWrite()`. Luôn bắt `SecurityException`.
- Chuỗi, màu, dimens tiền tố `cc_`, đặt thành khối liền ở cuối file tài nguyên.

## Review Focus

1. Không quyền vẽ đè, hoặc không quyền đọc thông báo, thì không crash và không thêm cửa sổ.
2. Đóng panel thì gỡ hết receiver và callback; tắt công tắc thì gỡ cả dải mép.
3. Dải mép không chặn chạm ngoài vùng của nó (`NOT_TOUCH_MODAL`), và không lấy focus.
4. Xoay màn hình (máy cho xoay) và đổi kích thước thì dải mép đặt lại đúng chỗ.
5. Không nhánh nào gọi API bị chặn theo API level.

---

### Task 1: Domain, data, quyền, DI
**Bắt buộc kiểm tay:** không
- [ ] **Step 1:** Model ở `domain/model/controlcenter/`. Cổng ở `domain/controlcenter/`.
- [ ] **Step 2:** `ControlCenterPrefsStore` dùng DataStore riêng `control_center`, khai bằng delegate cấp file (bài học `DataStoreModule`). Khóa `enabled` mặc định false.
- [ ] **Step 3:** `AndroidControlPermissions`: `canDrawOverlays`, NotificationListener đã bật (`NotificationManagerCompat.getEnabledListenerPackages`), `isIgnoringBatteryOptimizations`, `Settings.System.canWrite`, `isNotificationPolicyAccessGranted`, `BLUETOOTH_CONNECT` (31+).
- [ ] **Step 4:** `AndroidSystemControls`: đọc trạng thái một lần (`snapshot()`) và `observe()` (callbackFlow trên broadcast và `ContentObserver`, chỉ chạy khi có collector). Lệnh theo D5–D8.
- [ ] **Step 5:** Manifest:
  - `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS` (cả hai có `tools:ignore="ProtectedPermissions"` nếu lint đòi);
  - `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`;
  - `BLUETOOTH`/`BLUETOOTH_ADMIN` kèm `maxSdkVersion="30"`, `BLUETOOTH_CONNECT`;
  - `ACCESS_NOTIFICATION_POLICY`.
- [ ] **Step 6:** Build xanh.

### Task 2: `ControlCenterView`: bố cục và vẽ
**Bắt buộc kiểm tay:** không
- [ ] **Step 1:** Lưới 4 cột: `cell = (W − 2·24dp − 3·14dp)/4`, có giới hạn bề rộng tối đa 420 dp và canh giữa.
  - hàng 1–2: khối kết nối 2×2 và khối nhạc 2×2;
  - hàng 3: khóa xoay, Không làm phiền, thanh độ sáng 1×2, thanh âm lượng 1×2;
  - hàng 4: Đèn pin, Hẹn giờ, Máy tính, Máy ảnh.
- [ ] **Step 2:** Nền gồm `ImageView` ảnh mờ (API 31+ thêm `RenderEffect` blur 40 px) và lớp `cc_scrim`. Alpha nền do `ControlCenterMotion` đặt.
- [ ] **Step 3:** `render(state)` cập nhật ô, không dựng lại view. Màu ô bật: Wi-Fi và Bluetooth `#0A84FF`, Máy bay `#FF9F0A`, Dữ liệu `#30D158`, ô tròn khác nền trắng icon đen.
- [ ] **Step 4:** Kịch bản kiểm: gộp vào Task 4.

### Task 3: Màn cài đặt Control Center
**Bắt buộc kiểm tay:** không
- [ ] **Step 1:** `ControlCenterSettingsActivity` gồm:
  - công tắc "Bật Trung tâm điều khiển";
  - các hàng quyền, mỗi hàng có trạng thái "Đã cấp/Chưa cấp" và chạm thì mở đúng màn hệ thống: Vẽ trên ứng dụng khác, Truy cập thông báo, Bỏ tối ưu pin (khuyên dùng), Ghi cài đặt hệ thống (cho độ sáng, khóa xoay), Không làm phiền.
  - `onResume` đọc lại quyền.
- [ ] **Step 2:** Bật công tắc khi thiếu quyền bắt buộc (vẽ đè, đọc thông báo) thì vẫn ghi `enabled = true`, nhưng hiện dòng "Cần cấp 2 quyền bên dưới".
- [ ] **Step 3:** Cài đặt của launcher thêm hàng "Trung tâm điều khiển" ở nhóm Chung, giá trị "Bật/Tắt", chạm thì mở màn trên.

### Task 4: Service, cửa sổ overlay, cử chỉ, hoạt ảnh
**Bắt buộc kiểm tay:** **có**.
1. Cử chỉ chạm mới (mục 4.2).
2. Giả định chỉ máy thật trả lời được: ROM của Vsmart (Android 11) và ColorOS (Android 14) có giữ `ControlCenterService` sống sau khi vuốt bỏ launcher khỏi đa nhiệm, sau khi khởi động lại máy, và có nhận chạm ở dải ngay dưới thanh trạng thái không. Nếu sai thì phải đổi cách giữ process hoặc D2 trước khi làm Task 5–7.
- [ ] **Step 1:** `ControlCenterService` (NotificationListener, `@AndroidEntryPoint`):
  - `onListenerConnected`: theo dõi `prefs.enabled` và quyền vẽ đè, bật hoặc gỡ overlay;
  - `onListenerDisconnected`, `onDestroy`: gỡ overlay;
  - `onConfigurationChanged`: đặt lại dải mép.
- [ ] **Step 2:** `ControlCenterOverlay`: dải mép theo D2 (flags như app mẫu). Panel toàn màn chỉ thêm khi bắt đầu kéo, gỡ khi đóng xong.
- [ ] **Step 3:** `EdgeGestureTracker`: khóa trục sau slop. Kéo dọc xuống thì gọi `onDrag(dy)`, panel bám tay. Thả tay thì quyết theo D3 dựa trên quãng và vận tốc.
- [ ] **Step 4:** `ControlCenterMotion`:
  - nền `alpha = clamp(6·dy/H)`;
  - ô hiện khi `dy > 40 dp` (alpha và scale 0,8→1, 250 ms), rồi `translationY = −(1 − p)·hàngOffset + lệch theo speed` (1,45/1,6 xen kẽ);
  - chạy nốt theo D3.
- [ ] **Step 5:** Đóng theo D4; mở activity theo kiểu "đóng trước mở sau".
- [ ] **Step 6:** Kịch bản kiểm (Live 4 và Oppo):
  - (a) cấp đủ quyền ở màn cài đặt CC; dải mép hiện ở góc trên phải trên Home và trong một app khác;
  - (b) vuốt xuống từ dải: panel bám tay; thả quá 1/5 thì mở, chưa tới thì đóng;
  - (c) chạm vùng trống, Back, nút Home thì đóng;
  - (d) chạm ngoài dải mép trong app khác thì app đó nhận chạm bình thường;
  - (e) vuốt bỏ launcher khỏi đa nhiệm, sau 1 phút dải còn không;
  - (f) khởi động lại máy, dải tự hiện lại không;
  - (g) tắt công tắc thì dải biến mất.

### Task 5: Ô kết nối và lối tắt
**Bắt buộc kiểm tay:** không
- [ ] Chạm ô gọi `toggle()`. Kết quả `OpenSystemPanel` thì đóng panel rồi `startActivity(NEW_TASK)`. `NeedsPermission` thì mở màn cài đặt CC.
- [ ] Lối tắt: `AlarmClock.ACTION_SHOW_TIMERS`, `CATEGORY_APP_CALCULATOR`, `INTENT_ACTION_STILL_IMAGE_CAMERA`. `<queries>` thêm các intent này.
- [ ] Mỗi lần bắt đầu kéo mở thì đọc lại trạng thái, như app mẫu.

### Task 6: Đèn pin, khóa xoay, Không làm phiền, độ sáng, âm lượng
**Bắt buộc kiểm tay:** không
- [ ] Đèn pin dùng `CameraManager.setTorchMode` cùng `TorchCallback`; máy không có đèn flash thì ẩn ô.
- [ ] Khóa xoay ghi `ACCELEROMETER_ROTATION`. Không làm phiền dùng `setInterruptionFilter`.
- [ ] `ControlSliderView` dọc kiểu iOS. Ghi có throttle ~50 ms. Độ sáng tắt chế độ tự động khi kéo.

### Task 7: Trình phát nhạc
**Bắt buộc kiểm tay:** **có**. Giả định chỉ máy thật trả lời được: `getActiveSessions(ControlCenterService)` có trả phiên của các app nhạc phổ biến trên hai ROM không.
- [ ] `MediaSessionSource`: `getActiveSessions` cùng listener, chọn phiên đang phát. Phát `MediaState`.
- [ ] `MediaTileView`: ảnh album, tên bài, nghệ sĩ, nút lùi/phát/tới. Chạm ảnh thì mở app phát.

### Task 8: Đủ chức năng của Launcher OS (RE mục 5)
**Bắt buộc kiểm tay:** **có**: cử chỉ mới (đổi trang dọc, nhấn giữ, dải cạnh vuốt ngang) và lớp phủ Night Shift (chạm có xuyên qua trên Android 12+ không).
**Files thêm:** `domain/model/controlcenter/ControlCenterConfig.kt`, `domain/controlcenter/MediaControlsPort.kt`, `data/controlcenter/MediaSessionSource.kt`, `ControlCenterBackgroundFileStore.kt`, `ui/controlcenter/MediaViews.kt`, `PanelParts.kt`, `NightShiftOverlay.kt`, `CcForm.kt`, `CustomizeTilesActivity.kt`, `EdgeSettingsActivity.kt`, `NightShiftSettingsActivity.kt`.
- [x] **Cài đặt đầy đủ** (`ControlCenterConfig` trong DataStore `control_center`): bật, dải mép (4 vị trí, độ dài, độ dày, khoảng cách, màu), nền (ảnh nền launcher / ảnh tự chọn / không ảnh, làm mờ), rung khi mở, danh sách ô (tối đa 12, thêm app bất kỳ), app ghi màn hình, Night Shift (bật, độ ấm, lịch).
- [x] **Panel 3 trang dọc** có chỉ báo trang bên phải: Chính / Nhạc / Mạng. Vuốt dọc đổi trang, hai đầu giãn cao su. Back: đóng bảng chi tiết → về trang chính → đóng panel.
- [x] **Trang chính:** thanh trạng thái riêng (nhà mạng, Wi-Fi, khóa xoay, Không làm phiền, % pin, icon pin), nút cài đặt, khối kết nối 2×2, khối nhạc 2×2, khóa xoay, Không làm phiền, thanh độ sáng và âm lượng, ô thời gian chờ màn hình 2×1, lưới ô tùy chỉnh 4 cột. Lưới tự thu nhỏ nếu thiếu chiều cao.
- [x] **Chạm:** ô co 0,96 khi nhấn; nhấn giữ khối kết nối sang trang Mạng, nhấn giữ khối nhạc sang trang Nhạc; nhấn giữ thanh độ sáng mở bảng độ sáng lớn có nút Độ sáng tự động; nhấn giữ âm lượng mở bảng âm lượng lớn; chạm ô thời gian chờ mở bảng chọn 15s…30m.
- [x] **Trang Nhạc:** tên app, ảnh album, tên bài/nghệ sĩ chạy chữ, thanh tiến độ tua được kèm giờ, lùi/phát/tới, thiết bị phát ra (mở bảng âm lượng hệ thống), thanh âm lượng ngang. Dữ liệu thật từ `MediaSessionManager` (Task 7).
- [x] **Trang Mạng:** Máy bay, Dữ liệu di động, Wi-Fi, Bluetooth, Đồng bộ (bật/tắt trực tiếp được, quyền thường `WRITE_SYNC_SETTINGS`), Điểm truy cập (mở Cài đặt chia sẻ mạng).
- [x] **Ô tùy chỉnh:** Đèn pin, Hẹn giờ, Máy tính, Máy ảnh, Ghi màn hình (mở app đã chọn), Chế độ nguồn điện thấp, Ghi âm, Cài đặt, Chế độ tối, Ghi chú (tìm app theo tên gói như app mẫu), Night Shift (bật/tắt lớp phủ), app bất kỳ.
- [x] **Night Shift:** cửa sổ overlay không chạm, màu hổ phách, độ đậm bằng `LayoutParams.alpha` ≤ 0,45 (Android 12+ chỉ cho chạm xuyên khi ≤ 0,8); lịch kiểm mỗi phút khi service sống.
- [ ] **Kịch bản kiểm (Live 4 và Oppo):** (a) mỗi trang mở đúng, đổi trang bằng vuốt và bằng chấm; (b) nhấn giữ từng khối; (c) từng ô tùy chỉnh mở đúng màn; (d) màn Tùy chỉnh điều khiển: thêm, bỏ, kéo đổi thứ tự, thêm app, đủ 12 ô thì báo; (e) dải mép đổi ngay khi chỉnh ở màn Dải vuốt mở, dải cạnh vuốt vào trong mở được; (f) nền ba chế độ, tắt làm mờ; (g) Night Shift bật tay và theo lịch, chạm vẫn xuyên qua lớp phủ; (h) phát nhạc ở app khác: hai khối nhạc đúng bài, tua được; (i) trên máy Android 15+ (SM-A176B Android 16): chạm ô mở màn hệ thống từ trong một app khác có mở được không; Back đóng panel được không.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

- **Máy:** Vsmart Live 4 và Oppo A3x.
- **Chuẩn bị:** một app nhạc đang phát; chưa cấp quyền nào.
- **Mục kiểm:** theo thứ tự cấp quyền → dải mép → mở/đóng → từng ô → nhạc → tắt tính năng.
- **Hồi quy:** Home (tìm kiếm, Library, kéo-thả), bảng thông báo hệ thống khi vuốt trong thanh trạng thái.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | — | Viết plan từ RE; chủ dự án chọn phạm vi B, có ô nhạc, G3d trước AS | chưa commit | Viết lại plan từ phương án A sang B |
| 2026-11-10 | 1–4 | Code domain/data/DI/manifest (T1); `ControlCenterView` cùng các ô, thanh trượt, khối kết nối, ô nhạc tạm (T2); `ControlCenterSettingsActivity` + hàng trong Cài đặt (T3); `ControlCenterService`, `ControlCenterOverlay`, `EdgeGestureTracker`, `ControlCenterMotion`, `ControlCenterPresenter`, `PanelBackdrop` (T4). Lệnh ô (T5, T6) đã nối sẵn qua `AndroidSystemControls` | chưa commit | `assembleDebug` xanh. **Chờ chủ dự án kiểm tay Task 4** (kịch bản (a)–(g)) trước khi làm Task 5–7. Bluetooth: `ACTION_REQUEST_ENABLE` cũng cần `BLUETOOTH_CONNECT` nên hàng quyền này hiện trên mọi máy API 31+ (sửa D6) |
| 2026-11-11 | 5–8 | Chủ dự án thấy bản trước còn thiếu, yêu cầu clone mọi thứ của Control Center gốc bằng REA. RE thêm (RE mục 5, 114 lớp decompile bổ sung). Viết lại panel thành 3 trang, thêm thanh trạng thái, ô thời gian chờ, ô tùy chỉnh, bảng chi tiết, trình phát lớn, trang Mạng, Night Shift, 3 màn cài đặt con, `MediaSessionSource` | chưa commit | Làm Task 5–8 trước khi kiểm tay Task 4, theo yêu cầu chủ dự án (ngoại lệ với quy ước "kiểm tay xong mới sang Task sau"). `assembleDebug` xanh. Ô "Chụp màn hình" không làm (xem Ngoài phạm vi) |
| 2026-11-11 | 8 | Một agent đọc lại code độc lập (không sửa file). Đã sửa: Slider bắt đầu ngoài khoảng (crash khi khôi phục `EdgeSettingsActivity`); Back trên API 33+ dùng `OnBackInvokedCallback` (target 36+ không còn gửi `KEYCODE_BACK`); dải cạnh xin loại trừ cử chỉ Back của hệ thống (tối đa 200 dp); chạm trên thẻ Nhạc/Mạng/thanh trạng thái không còn đóng panel; trang không kẹt giữa chừng khi chạm lúc đang đổi trang; nhấn giữ ảnh và nút của khối nhạc cũng sang trang Nhạc; lưới thu theo chiều cao khi xoay ngang; Không làm phiền trên Android 15+ mở màn hệ thống nếu không tắt được; ô Night Shift lúc đang theo lịch mở màn Night Shift; lỗi âm lượng chỉ báo khi nhả tay; service không còn `START_STICKY`, `onListenerConnected` dọn presenter cũ | chưa commit | `assembleDebug` xanh. **Chưa xác minh:** mở activity từ service ngay sau khi gỡ panel trên Android 15+ (dải mép vẫn còn nên có lẽ được miễn chặn mở nền); Back qua `OnBackInvokedCallback` trên cửa sổ overlay. Thêm hai mục này vào bài kiểm Task 8 |
| 2026-11-11 | — | Chủ dự án bỏ hẳn App Switcher: xoá nhánh `feature/as-app-switcher` và worktree (nhánh chưa có commit). Bỏ mục "Ô Đa nhiệm" khỏi Ngoài phạm vi | chưa commit | Có bản patch dự phòng ở scratchpad của phiên, không nằm trong repo |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#21 sang "Xong" hoặc "Một phần".
- `project_structure.md`: dòng G3d; "Tệp thêm ở G3d" (`ui/controlcenter/`, `data/controlcenter/`, `domain/controlcenter/`, DataStore `control_center`).
- `lessons/`: hành vi giữ NotificationListener theo ROM; vùng chạm dưới thanh trạng thái.
