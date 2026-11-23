# Launcher OS: Control Center và App Switcher (phân tích ngược bằng REA)

- **Đối tượng:** `com.babydola.launcherios` 7.0.1 (versionCode 384), cùng bản đã phân tích ở [LauncherOS-LiquidGlass-RE.md](LauncherOS-LiquidGlass-RE.md). `base.apk` kéo từ SM-A176B (Android 16, API 36) ngày 2026-11-10.
- **APK sha256:** `036c408bf7a9ae0e4982511028dba53efdbe876911063bca6606fed2f0fed271` (khớp bản cũ).
- **Cách làm:** chỉ phân tích tĩnh. REA (JADX headless 0.7.1) đọc manifest, quyền và tìm class. Sau đó dùng chính `jadx-core` trong jar của REA decompile cục bộ ra `D:\re-launcheros\out\sources`, gồm package của app và ~16k class bị R8 dời vào package mặc định. `aapt2 dump` để đọc manifest và bảng chuỗi. Không chạy app, không bấm gì trên máy.
- **Evidence REA:** package summary `ev_ea5a7d4473ab…` (parent `ev_9b23731247e4…`); tìm class `controlcenter` `ev_e89484aa981c…`; tìm `Switch` `ev_474408d3028a…`.
- **Ký hiệu:** **[O]** là thấy trực tiếp trong code hoặc tài nguyên đã decompile. **[I]** là suy luận, chưa xác minh.

---

## 1. Tóm tắt

1. **Launcher OS 7.0.1 không có App Switcher (màn đa nhiệm) riêng.** **[O]**
   - Không có class kiểu `RecentsView`/`TaskView` (Quickstep), và không có layout hay chuỗi nào tên "switcher/multitask/app switch".
   - Không gọi `getRecentTasks`, `getAppTasks`, `moveTaskToFront` hay `killBackgroundProcesses`.
   - `UsageStatsManager` chỉ xuất hiện trong một class của SDK bên thứ ba (`C3600u2`, gọi qua `pte`).
   - Quyền `PACKAGE_USAGE_STATS` có trong manifest, nhưng chuỗi giải thích nói rõ là để "gợi ý app".
   - Chuỗi `recents` là của launcher3 gốc.
   - Khi Launcher OS là launcher mặc định, màn đa nhiệm mà người dùng thấy là **của hệ thống** (One UI/ColorOS), không phải của app. **[I]** Đây là kiến trúc chuẩn của Android: chỉ launcher hệ thống mới nhận được Quickstep.
   - Giới hạn tìm kiếm: ~15,5k trên ~17,5k class đã decompile (phần còn lại là class rất lớn, JADX xử lý chậm). Kết luận "không có" chỉ đúng trong phạm vi đó.
2. **Control Center là một thư viện nhúng** (`com.example.controlcenter` cùng UI `com.appsgenz.controlcenter.phone.ios`, phần lõi bị dời vào package mặc định). Thư viện này **vẽ đè lên mọi app**, không chỉ trên Home. **[O]**
   - Một **dải chạm ở mép** (cửa sổ `TYPE_APPLICATION_OVERLAY`) luôn nằm trên màn hình.
   - Vuốt từ dải đó sẽ thêm **cửa sổ panel toàn màn**. Panel bám theo ngón tay, thả ra thì chạy nốt.
   - Process được giữ sống bằng `NotificationListenerService` của launcher. Service này cũng là "chìa khóa" để đọc `MediaSession` cho ô nhạc.
3. **Không có AccessibilityService.** Mọi thứ đi qua overlay, NotificationListener, `WRITE_SETTINGS` và intent mở màn cài đặt hệ thống. **[O]**
4. **Nền panel không blur trực tiếp app phía sau**, mà là ảnh tĩnh. Ảnh này là wallpaper hoặc ảnh người dùng chọn, decode `inSampleSize=4`, thu còn 0,2 lần rồi stack blur bán kính 30 bằng Java. Đây là cùng ý tưởng với `BlurredBackdropCache` của ta. **[O]**

---

## 2. Control Center: kiến trúc

```
LauncherApplication.onCreate
   a82.f = { ctx -> sr2(ctx) }            // factory ControlNotificationService
   a82.e = LauncherNotificationService     // dùng làm ComponentName cho MediaSessionManager
        │
LauncherNotificationService (NotificationListenerService, BIND_NOTIFICATION_LISTENER_SERVICE)
   onCreate            → sr2.onCreate()
   onListenerConnected → sr2.c()    : tạo cn8, thêm dải mép nếu đủ điều kiện
   onStartCommand(i)   → sr2.b(i)   : lệnh qua extra "data_id_notification"
   onListenerDisconnected → sr2.a() : gỡ cửa sổ
        │
sr2 (điều phối)
   ├─ MediaSessionManager.getActiveSessions(component) + OnActiveSessionsChangedListener
   │     → chọn MediaController, cập nhật ô nhạc (title/artist/art/position/volume)
   └─ cn8 (quản lý cửa sổ) ──────────────────────────────────────────────┐
        t = fvd   dải mép     window o: type 2038, flags 808               │
        v = mrd   panel       window m: type 2038, flags 1824, toàn màn    │
        u = yia   tooltip hướng dẫn lần đầu (Lottie)                       │
        n         lớp phủ    window n: flags 824 (NOT_TOUCHABLE), toàn màn │ [I] Night Shift
        BroadcastReceiver: AIRPLANE_MODE, WIFI_STATE, BT STATE, WIFI_AP_STATE, CONFIGURATION
        ContentObserver: Settings.System.screen_brightness
```

### 2.1 Điều kiện bật **[O]**
Dải mép chỉ được thêm khi đủ cả bốn điều kiện sau (`sr2.c()`, `CustomPositionActivity`, `PermissionNewActivity`):
- pref `ena_control_center` = true (công tắc trong `ControlMainActivity`);
- có quyền NotificationListener (`enabled_notification_listeners` chứa app);
- `Settings.canDrawOverlays()`;
- được **miễn tối ưu pin** (`PowerManager.isIgnoringBatteryOptimizations`), và trên MIUI có thêm AppOps 10008 (tự khởi động).

Chuỗi hướng dẫn người dùng: "Allow … displaying over any other apps", "Allow … to control Music Player", "Allow … to keep working in the background".

### 2.2 Lệnh qua `data_id_notification` **[O]**
| Mã | Việc | Người gửi |
|---|---|---|
| 10, 18 | `cn8.c()`: cập nhật vị trí, kích thước, màu dải mép | màn chỉnh vị trí |
| 11 | bật lại hoặc tắt dải mép theo điều kiện 2.1 | `ControlMainActivity` |
| 13 | Night Shift | `NightShiftActivity` |
| 14 | dựng lại danh sách ô tùy chỉnh (`mrd.e()` + `u()`) | `CustomControlActivity` |
| 16, 20, 23, 24 | nạp lại nền panel / phiên media | `BackgroundActivity`, `mrd` |
| 21/22, 101/102 | ẩn/hiện dải mép | không tìm thấy nơi gửi bằng hằng số **[I]** |

### 2.3 Dải mép và cử chỉ **[O]**
- Vị trí `orientation_view`: 1 = mép phải dọc (TOP\|END), 2 = góc dưới phải, 3 = mép trên bên trái, khác = mép trên bên phải. Bề dày, độ dài và màu do người dùng chỉnh (`nf7.E` = `[dày, dài, màu]`), `y` lệch theo cài đặt.
- Bộ nhận chạm `vhc` dùng `GestureDetector`:
  - `onDown`: chưa khóa trục.
  - `onScroll`: vượt **11 px** thì khóa trục (dọc hay ngang tùy trục trội). Sau đó gọi `f(dy)` hoặc `e(dx)` mỗi khung, panel bám ngón tay.
  - `onFling`: quãng đi **> 50 px** và vận tốc cùng chiều thì mở/đóng (`d()`/`a()`/`j()`/`h()`), nếu không thì `onCancel()` (bật về).
  - Nhấn đúp: `g()`. `ACTION_CANCEL`: `i()`.

### 2.4 Panel `mrd` **[O]**
- **Trang:** `VerticalViewPager` (trang dọc) cùng `VerticalPageIndicator` ở mép.
- **Khối kết nối `brd`:** Máy bay, Dữ liệu di động, Bluetooth, Đồng bộ, Wi-Fi, Điểm phát. Màu bật: xanh dương `#D90020D3` (BT, Wi-Fi), cam `#D9CA4D02` (máy bay), xanh lá `#D9237D12` (điểm phát).
- **Ô khác:**
  - nhạc nhỏ `ktd` và nhạc lớn `ntd` (ảnh album, tên app phát, thanh tiến độ, âm lượng);
  - thanh độ sáng (`#FFD60A`) và thanh âm lượng (`#64D2FF`);
  - khóa xoay `atd`, Tập trung/Không làm phiền `wud`, thời gian tắt màn `yeb`.
- **Ô tùy chỉnh:** lấy từ danh sách `nf7.v()` (người dùng chọn ở `CustomControlActivity`).
  - Loại 1 là đèn pin, loại 5 là hẹn giờ, loại 7 là một loại riêng, còn lại là ô lối tắt.
  - Bấm ô lối tắt thì **đóng panel trước rồi mới mở**: `cn8.h()` chạy hành động đã ghi sẵn sau khi `removeView`.
  - Mã hành động: -1000 mở app, 20 hẹn giờ, 30 máy tính, 40 máy ảnh, 70 tiết kiệm pin, 80 ghi âm, 90 Cài đặt, 110 ghi chú.

### 2.5 Hoạt ảnh **[O]**
- **Nền** (`eqd`, ảnh blur): `alpha = clamp(6·dy / chiềuCao, 0, 1)`, tức đậm hẳn khi kéo được 1/6 màn hình.
- **Từng ô** (`m41.g(dy)`):
  - dưới **110 px** thì ẩn (alpha→0, scale→0,8, 100 ms);
  - vượt 110 px thì hiện ra (alpha 0→1, scale 0,8→1, 250 ms), rồi `translationY = (dy − 110)·speed / 3`;
  - `speed` là 1,45 hoặc 1,6 tùy chỉ số ô, nên các ô **chạy lệch nhau** như cao su.
- **Thả tay để mở** (`m41.d()`, `mrd.j()`): `translationY → 0`, `alpha → 1`, `scale → 1` với `PathInterpolator(0.16, 0.545, 0.455, 1.0)`. Thời lượng = `translationY·730/700`, kẹp **150–730 ms**. Có rung nếu bật `vibration_control`.
- **Đóng** (`mrd.h()`): mọi ô `e()` (mờ + co, 100 ms), khối kết nối và khối nhạc thu lại, rồi gỡ cửa sổ.
- **Mỗi lần bắt đầu kéo mở**, đọc lại trạng thái thật một lần: điểm phát (reflection `getWifiApState`), xoay (`accelerometer_rotation`), DND (`getCurrentInterruptionFilter`), độ sáng (`screen_brightness`·100/255).

### 2.6 Bật/tắt từng chức năng **[O]**
| Ô | API ≤ ngưỡng | API mới | Quyền |
|---|---|---|---|
| Wi-Fi | < 29: `setWifiEnabled` trên thread | ≥ 29: đóng panel, mở `Settings.Panel.ACTION_WIFI` | `CHANGE_WIFI_STATE` |
| Bluetooth | < 33: `adapter.enable()/disable()` | ≥ 33: intent `REQUEST_ENABLE` / `REQUEST_DISABLE` | `BLUETOOTH_CONNECT` (31+) |
| Dữ liệu di động | — | `Settings.Panel.ACTION_INTERNET_CONNECTIVITY`, dự phòng `DATA_USAGE_SETTINGS` | — |
| Máy bay | — | `AIRPLANE_MODE_SETTINGS` | — |
| Điểm phát | — | `TETHER_SETTINGS`, dự phòng `WIRELESS_SETTINGS` | — |
| Đồng bộ | — | `SYNC_SETTINGS` | `READ/WRITE_SYNC_SETTINGS` |
| Độ sáng, xoay, tắt màn | ghi `Settings.System` | như cũ | `WRITE_SETTINGS` (người dùng cấp) |
| Không làm phiền | `NotificationManager` | như cũ | `ACCESS_NOTIFICATION_POLICY` |
| Đèn pin | `FlashlightController` (`CameraManager`) | như cũ | — **[I]** |
| Nhạc | `MediaController.getTransportControls()` play/pause/next/prev | như cũ | NotificationListener |
| Chế độ tối | mở `DARK_THEME_SETTINGS`, dự phòng `DISPLAY_SETTINGS` | như cũ | — |

---

## 3. Áp dụng cho Os26Launcher

| Ý của Launcher OS | Dùng lại? | Ghi chú |
|---|---|---|
| Panel bám tay, nền đậm dần theo `dy`, ô hiện khi vượt ngưỡng, ô lệch tốc độ, thả thì chạy nốt bằng `PathInterpolator(0.16,0.545,0.455,1)` 150–730 ms | **Có** | Học hành vi, viết lại bằng code của ta (không chép code) |
| Nền là ảnh nền mờ tĩnh, không blur trực tiếp | **Có**, đã có sẵn | `BackdropBlurDrawable` + `BlurredBackdropCache` (GL) |
| Ô kết nối mở `Settings.Panel` từ API 29 | **Có** | Ràng buộc của Android, không có cách khác cho app thường |
| Nhạc qua NotificationListener + `MediaSessionManager` | **Có**, tùy chọn | Thêm một quyền đặc biệt; chỉ xin khi người dùng bấm ô nhạc |
| Overlay vẽ đè mọi app + giữ process bằng NotificationListener + miễn tối ưu pin | **Có** (chủ dự án chọn 2026-11-10) | Plan G3d phương án B. Miễn tối ưu pin chỉ mở danh sách hệ thống, không khai `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| App Switcher | Không có gì để học | Đã làm thử (plan AS) rồi **chủ dự án bỏ hẳn ngày 2026-11-11**: chỉ mở được trong Home, không thay được màn đa nhiệm của hệ thống |

## 4. Chưa xác định

- Nơi gửi mã 21/22/101/102 (ẩn/hiện dải mép), vì không thấy hằng số literal.
- Cửa sổ thứ tư (`flags 824`) có đúng là Night Shift không.
- Chi tiết `FlashlightController` và `ScheduleNightShiftManager`, và bố cục lưới chính xác (số cột, kích thước ô theo dp) vì dimens chưa decode.
- ~2k class lớn chưa decompile xong lúc viết, nên kết luận "không có App Switcher" có giới hạn như mục 1.

---

## 5. Danh sách đầy đủ chức năng Control Center (bổ sung 2026-11-11)

Đọc thêm bằng REA (`search_android_classes` `ev_af1260f7…`, `inspect_android_method` `gvd.<init>` `ev_cb9b0903…`, `erd.c` `ev_3b714333…`) và decompile cục bộ 114 lớp còn thiếu bằng `jadx-core` trong jar của REA (`D:\re-launcheros\out2`). Mọi mục dưới đây là **[O]** trừ khi ghi khác.

### 5.1 Panel: 3 trang dọc
`mrd` dùng `VerticalViewPager` với 3 trang, và một `VerticalPageIndicator` ở mép phải có icon từng trang (`ic_page_one`, `ic_page_music`, `ic_page_network`). Kích thước tính theo bề rộng màn W (máy tính bảng dùng W/2): ô nhỏ = 18%·W, khối lớn = 40%·W, khe = W/25.

| Trang | Nội dung |
|---|---|
| 1. Chính | Thanh trạng thái riêng (`gvd`: vạch sóng 2 SIM, tên nhà mạng, icon Wi-Fi, khoảng trống, icon vị trí, icon khóa xoay, icon im lặng, % pin, icon pin). Nút nhỏ tròn (`uud`, `ic_in_app`) mở màn cài đặt Control Center, rồi một vạch ngăn. Lưới: khối kết nối 2×2 (`zqd`: máy bay, dữ liệu, Wi-Fi, Bluetooth) · khối nhạc 2×2 (`ktd`: tên bài, nghệ sĩ, lùi/phát/tới) · khóa xoay (`atd`) · Không làm phiền (`wud`, icon chuông) · thanh độ sáng 1×2 và âm lượng 1×2 (`nud`, màu `#FFD60A` / `#64D2FF`) · ô "Thời gian chờ màn hình" 2×1 (`yeb`, hiện 15s/30s/1m/2m/5m/10m/30m) · tối đa 12 ô tùy chỉnh, 4 ô mỗi hàng |
| 2. Nhạc | Trình phát lớn (`ntd`): ảnh album, tên app đang phát, tên bài/nghệ sĩ chạy chữ, thanh tiến độ, lùi/phát/tới, thanh âm lượng, thiết bị phát ra (tên Bluetooth) |
| 3. Mạng | Khối kết nối mở rộng (`brd`): 6 ô có nhãn và chữ Bật/Tắt: Máy bay, Dữ liệu di động, Bluetooth, Đồng bộ, Wi-Fi, Điểm phát |

### 5.2 Chạm (`jrd`, `erd`, `m41`)
- Chạm xuống một ô: ô co 1→0,96 rồi về (200 ms, lặp ngược 1 lần).
- Chạm vùng trống: đóng panel.
- **Nhấn giữ** khối nhạc thì sang trang 2; nhấn giữ khối kết nối thì sang trang 3; nhấn giữ thanh độ sáng mở **bảng độ sáng lớn** (`fqd`): thanh cao tỉ lệ 18:47, icon mặt trời ở trên, nút "Độ sáng tự động" ở dưới (ghi `screen_brightness_mode`).
- Chạm ô thời gian chờ mở **bảng chọn** (`uvd`) ghi `screen_off_timeout` (cần quyền ghi cài đặt).
- Chạm ô im lặng: đổi `INTERRUPTION_FILTER` giữa ALL và ALARMS, hiện icon im lặng trên thanh trạng thái của panel, thanh âm lượng về 0.
- Trên trang 1 kéo xuống thì các ô giãn như cao su (`g(180+dy)`), nhả tay thì về; trên trang 3 kéo lên thì khối kết nối giãn.

### 5.3 Ô tùy chỉnh (`yq7.type`, `rtd`)
Danh sách lưu ở pref `arr_control` (JSON). Mặc định: Đèn pin, Hẹn giờ, Máy tính, Máy ảnh, Ghi màn hình, Chế độ tối, Pin, Ghi âm, Cài đặt.

| type | Ô | Hành động khi chạm (`cn8.h()` sau khi đóng panel) |
|---|---|---|
| 1 | Đèn pin | `CameraManager.setTorchMode`, không đóng panel |
| 2 | Hẹn giờ | Mở màn Hẹn giờ của app Đồng hồ (`SHOW_TIMERS`) |
| 3 | Máy tính | Mở app máy tính |
| 4 | Máy ảnh | Mở máy ảnh |
| 5 | Ghi màn hình | Mở app ghi màn hình người dùng đã chọn (pref `recorder_app`, màn `RecorderActivity`) |
| 6 | Chụp màn hình | Không thấy hành động nào trong code đã đọc **[I: có thể không làm gì]** |
| 7 | Pin | Icon theo mức pin; mở màn Tiết kiệm pin |
| 8 | Ghi âm | Mở app có tên gói chứa "recor"/"voice" |
| 9 | Cài đặt | Mở Cài đặt |
| 10 | Chế độ tối | Mở `DARK_THEME_SETTINGS` |
| 11 | Ghi chú | Mở app có tên gói chứa "note" |
| 12 | Night Shift | Bật/tắt lớp phủ màu ấm |
| (pkg) | App bất kỳ | Mở app đó |

### 5.4 Màn cài đặt (`ControlMainActivity` và các màn con)
- Bật Control Center (`ena_control_center`).
- **Tùy chỉnh ô** (`CustomControlActivity`): thêm, bỏ, sắp xếp; thêm được app bất kỳ.
- **Nền** (`BackgroundActivity`): `wallpaper_ct` = 1 dùng ảnh (wallpaper, `state_wall` = 3, hoặc ảnh tự chọn, `state_wall` = 4), khác 1 thì không ảnh; công tắc làm mờ `ena_blur` (mặc định bật). Có kho ảnh nền tải từ máy chủ (không làm).
- **Vị trí dải mép** (`CustomPositionActivity`): 4 vị trí `orientation_view` (mép phải trên, mép trên trái, góc dưới phải, mép trên phải), độ dài `width_notification` (mặc định W/3), độ dày `height_notification`, màu `color_notification`, lệch dọc `position_with_vertical`.
- **Night Shift** (`NightShiftActivity`): bật `night_shift`, độ ấm `color_night_shift` (mặc định `#40ffd925`), hẹn giờ `scheduled` từ `time_from` 22:00 tới `time_to` 07:00. Lớp phủ là cửa sổ thứ tư (flags 824 = không chạm, không focus).
- **App ghi màn hình mặc định** (`RecorderActivity`).
- **Rung khi mở** (`vibration_control`).
- Ngôn ngữ, đánh giá, quảng cáo, hướng dẫn Lottie: không thuộc chức năng, không làm.
