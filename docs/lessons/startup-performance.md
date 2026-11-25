# Bài học: Khởi động và đo hiệu năng

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Số đo nạp danh sách app, cold start, cache nhãn, bản debug so với release, R8.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Nạp danh sách app: đo chi tiết sau khi chia nhỏ (M3 Task 1, 2026-10-26, Live 4, 87 app)

- Tách lần nạp (trước đó M2 chỉ đo gộp): `getActivityList`=32–47ms · đọc nhãn (tuần tự)=601ms ·
  sắp xếp (`Collator`)=4–7ms. Đọc nhãn chiếm phần lớn thời gian — đúng mục tiêu của Task 1.
- Thử `Semaphore` giới hạn số coroutine đọc nhãn song song, đo `readLabels` nhiều lần: tuần tự 601ms
  · Semaphore(8)=504ms · Semaphore(4)=843ms (có app nền, nhiễu) · Semaphore(16)=489ms. Đo lại sau khi
  đóng app nền (điều kiện sạch): Semaphore(4)=456ms · Semaphore(8)=458ms — gần như giống nhau, chênh
  lệch lần đo trước là nhiễu máy, không phải do số luồng.
- **Không đạt mục tiêu giảm ≥50% ms/app** (chỉ giảm ~24% ở điều kiện sạch: ~6,9ms/app → ~5,3ms/app) —
  nghi vấn (suy luận chưa kiểm chứng): đọc nhãn (`ApplicationInfo.loadLabel`) có khoá nội bộ dùng
  chung trong framework/`AssetManager`, nên tăng số coroutine song song không tăng thông lượng tuyến
  tính như một lệnh IPC thuần túy.
- **Chọn `LABEL_CONCURRENCY = 8`** (khuyến nghị gốc của plan): hiệu năng tương đương 4 trên Live 4 (87
  app), nhưng an toàn hơn khi mở rộng sang máy nhiều app hơn.
- **Dự đoán cho Oppo A3x 344 app — đã sửa 2026-10-27, con số 1,8 giây trước đó là quá lạc quan.**
  Phép ngoại suy cũ áp ms/app của Live 4 cho Oppo, nhưng hai máy có chi phí mỗi app khác nhau rõ rệt:
  Live 4 **6,9 ms/app** tuần tự, Oppo **23 ms/app** (7,9 s ÷ 344, đo ở M2) — Oppo đắt gấp ~3,3 lần dù
  CPU mạnh hơn, chưa rõ vì sao (app nhiều hơn và nặng hơn? lớp ColorOS?). Hai cách ngoại suy cho hai
  đáp số rất khác: áp ms/app của Live 4 → ~1,8 s; áp mức giảm 24% lên chính Oppo → 23 × 0,76 × 344
  ≈ **6,0 s**. Khoảng thật nằm giữa 1,8 và 6,0 giây. **Chưa đo được**, nên cold start coi như CHƯA
  giải quyết cho máy nhiều app. Có Oppo thì đo ngay — đây là đầu vào của task phụ cache nhãn sau M6.

## P1 — cold start Oppo A3x: số đo (2026-11-05, APK debug, 345 activity, 5 lượt sau một lượt khởi động)

- **Đo được, mốc tính từ lúc tiến trình bắt đầu, sau `am force-stop` (không phải sau khi khởi động lại máy, nên tệp có thể đã nằm trong cache hệ thống):** `am start -W` TotalTime 1195–1260 ms; bắt đầu nạp ~800 ms; `getActivityList` xong ~940 ms; đọc nhãn xong 6271–6423 ms; sắp xếp ~25 ms; có trang để vẽ 6466–6608 ms.
- **Đọc nhãn chiếm ~5.4 giây, khoảng 84% thời gian tới lúc có lưới (~15.7 ms/app).** Từ lúc cửa sổ hiện tới lúc có lưới, người dùng nhìn màn trống ~5.3 giây. Đáp câu hỏi còn mở ở mục "Nạp danh sách app" (khoảng 1,8–6,0 giây): nằm sát đầu trên.
- Lượt đầu sau khi cài chậm hơn ~0.5 giây (7059 ms), không tính.
- Cách đo: thêm `Log` với `SystemClock.uptimeMillis() - Process.getStartUptimeMillis()` trong worktree tạm, không commit.

## LC — cache nhãn app: bài học (2026-11-05, Oppo A3x + Vsmart Live 4, APK debug)

- **Đo được, tới lúc có trang để vẽ:** Oppo `main` 6212–6545 ms, cache ấm 1565–1648 ms, không cache 6414–6417 ms; Live 4 `main` 1528–1642 ms, cache ấm 1141–1184 ms, không cache 1745 ms (một lượt). Mục tiêu trung vị 1500 ms trên Oppo trượt khoảng 80 ms; phần ~1.6 giây còn lại chưa tách giai đoạn, nên nút cổ chai kế tiếp chưa biết.
- **"Khóa luôn từ hệ thống, cache chỉ trả lời nhãn":** tránh cả lớp lỗi app ma và phát hai lần. Nên dùng lại kiểu này cho cache khác.
- **`sourceDir` là dấu vân tay rẻ:** đổi mỗi lần cài trên Android 11 và 14 (đo được với APK của mình), không cần IPC thêm. App hệ thống cập nhật qua store nằm dưới `/data/app/` (46 gói trên Oppo, 31 trên Live 4). Cập nhật thật qua store chưa đo, chỉ suy luận.
- **Script Git Bash đừng gọi `cmd.exe //c` khi đặt `MSYS_NO_PATHCONV=1`:** `//c` không được đổi nên `cmd` mở dấu nhắc rồi treo im lặng. Build Gradle thì chạy bằng PowerShell.
- **`adb` trong vòng lặp `while read` ăn mất stdin:** vòng lặp dừng sau lần đầu mà không báo lỗi. Dùng `for`, hoặc `adb ... < /dev/null`, rồi đọc lại trạng thái sau khi đổi.
- **Hai APK cùng kích thước không có nghĩa là cùng bản:** so hash và tìm tên lớp trong dex trước khi đo.
- **"Có trang để vẽ" không phải "người dùng thấy xong" (đo được, Oppo, 4 lượt, cache ấm, mốc từ lúc tiến trình bắt đầu):** có trang 1559–1610 ms, nhãn vẽ xong 1744–1792 ms, icon đầu 1761–1805 ms, icon cuối 2407–2450 ms (22 icon). Chủ dự án bấm giờ bằng mắt được ~3 giây; số đó gồm cả thời gian trước khi tiến trình chạy, nên hai số khớp nhau. Đo thời gian khởi động thì phải log ở `onDraw`, không dừng ở trạng thái. Sau nhãn, phần lớn nhất còn lại là ~800 ms trước khi bắt đầu nạp và ~650 ms nạp icon.
- **Live 4 quay màn hình được:** từ lúc bắt đầu mở tới khi lưới hiện ~1.6–1.75 giây (`screenrecord`, 4 lượt; một lượt còn thay đổi nhỏ tới 3.0 giây).

## Đừng đo khởi động trên bản debug (tách từ «B1, CL, IC và bài học đo bản debug (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Bài học lớn: đừng đo thời gian khởi động trên bản debug rồi nói là cái người dùng thấy.** Cùng mã, Oppo A3x: icon cuối vẽ xong 2822 ms (debug) so với 637 ms (release), gấp ~4,4 lần; mở cửa sổ ~1250 ms so với ~410 ms. Bản debug `debuggable` và `dexopt status=run-from-apk`; `cmd package compile -m speed -f` lên bản debug chỉ đạt `verify`. Muốn thử biên dịch trước phải dựng bản release (`assembleRelease`, `zipalign`, `apksigner` bằng `~/.android/debug.keystore`, cài `adb install -r -d`). Cấu hình release hiện `optimization.enable = false` (R8 tắt) và chưa có baseline profile. IC trên release vẫn tiết kiệm ~140 ms (22%).

## RB — số đo bản release (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **RB — số đo (đo được, Oppo, `am start -W` tới khung đầu, trung vị 5 lượt):** debug 1198 ms · release không R8 422-471 ms · release R8 + profile 405-409 ms; APK 11,2 MB (debug) / 7,96 MB / 2,75 MB. Khác biệt lớn là debug so với release; R8 + profile thêm 15-65 ms và giảm ~65% kích thước.

## RB — kiểm keep rule bằng file (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Kiểm keep rule bằng file, không bằng trí nhớ:** `app\build\outputs\mapping\release\configuration.txt` (cấu hình cuối), `seeds.txt` (được giữ), `usage.txt` (bị gỡ). Thư viện kính không mang keep rule cho JNI.

## RB — profileinstaller (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **`androidx.profileinstaller` thường có sẵn gián tiếp** (1.4.0 qua `activity`): kiểm bằng `dependencyInsight` trước khi thêm.

## RB — đo khởi động lạnh khi là launcher mặc định (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Đo khởi động lạnh khi app là launcher mặc định:** mở app khác lên trên rồi mới `force-stop`; `force-stop` khi Home đang ở trên làm hệ thống tự mở lại Home và lượt đo không còn lạnh.

## FX1, FX4 — ảnh nền lớn, chỉ mục tìm kiếm (2026-11-11, Vsmart Live 4, bản debug)

- **`BitmapRegionDecoder` giải mã vùng giữa của ảnh lớn mà không cấp phát cả ảnh** (đo được, 2026-11-11): JPEG 12000x3000 nạp trong ~120 ms, bitmap 1080x2340 = 10,1 MB. Ảnh dọc/ngang 12 MP giữ trong RAM 72 → 35-37 MB Native, Graphics 91 → 52 MB so với bản `b1af810`.
- **Chuẩn hóa tìm kiếm** (đo được, 2026-11-11): với 2000 danh bạ một lần dựng cả chỉ mục ~114 ms (0,05 ms/nhãn), thấp hơn nhiều so với ngoại suy 0,2 ms/nhãn của M8d (Oppo); dựng riêng nguồn danh bạ ~9-15 ms sau khi chuẩn hóa một lượt.
- **"Chưa dựng gì trước khi người dùng cần" kiểm bằng log, không bằng `TotalTime` của bản debug** (đọc code, 2026-11-11): log đo tạm ghi vào tag riêng và gỡ bằng grep dấu; số đo debug không nói thay trải nghiệm người dùng.

## FX5 — cache icon (2026-11-11, Vsmart Live 4)

- **Cache RAM của launcher không phải nơi giữ bộ nhớ chính** (FX5, đo được, 2026-11-11): `evictAll` 10 MB của `IconCache` ở nền chỉ giảm Native Heap ~1 MB vì map bitmap của view giữ cùng các bitmap. Đừng thêm trim cho cache khi chủ sở hữu thật là view.
- **`IconDiskCache.trim` chỉ chạy khi ghi** (FX5, đo được, 2026-11-11): thư mục có thể vượt trần lúc nghỉ; LRU theo lần đọc bảo vệ từ phiên sau, không bảo vệ trong phiên làm vượt trần.

## RN — đổi tên app (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/RN-rename-apps.md](../knowledgements/RN-rename-apps.md)

- Thêm một nguồn vào đường nạp `apps` bằng `combine` và một file DataStore riêng: `labels#1` (đọc file) xong trước `loaded#1` (nạp từ hệ thống) 73–131 ms, `apps#1` chỉ sau `loaded#1` 13–31 ms gồm cả phép sắp; một lần phát `apps` mỗi lần khởi động (đo được, Live 4, debug, 6 lần, 0 tên riêng, 2026-11-13). Chưa đo với nhiều tên riêng và trên Oppo.
- **Build sạch lần đầu trong worktree mới mất 6 phút 32 giây** (`assembleDebug`, máy dev Windows, 2026-11-13, đo được; AGENTS.md ghi "chưa đo"); các lần build tăng dần sau đó 43 giây đến 4 phút 09 giây tùy file đổi (đo được).

## PG4 G3 — đo lướt trang Home Compose: bản `perf` so với bản debug (2026-11-23, Oppo PKD130 + Vsmart Live 4). Nguồn: [plans/PG4-home-perf-and-device-checks.md](../plans/PG4-home-perf-and-device-checks.md)

- **(2026-11-23, đo được) Đừng đánh giá độ mượt của Compose trên bản debug.** Cùng kịch bản lướt trang trên Oppo PKD130: bản debug Janky 36 %, p50 26 ms, p95 106 ms; bản `perf` (cấu hình release: R8 + baseline profile, không debuggable) Janky 0,9–1,3 %, p95 12–13 ms. Cảm giác "Home giật" khi dùng bản debug hằng ngày là do bản debug, không phải kính. Đo bằng bản `perf` (`.\gradlew.bat installPerf`) và `tools/perf/measure-home.ps1`.
- **(2026-11-23, đo được) Kính trên Home trong ngưỡng.** Oppo: kính thật của dock / widget tốn Janky +0,5–0,8 điểm, p95 +1,3–2,3 ms so với bỏ hết kính; kính mờ tĩnh (PG3) và widget gần như không tốn. Live 4 (API 30, không kính thật): kính mờ tĩnh của dock, widget, card, ô thư mục không tốn đo được.
- **(2026-11-23, đo được) Script đo:** `powershell -File ... -Configs a,b,c` truyền MỘT chuỗi "a,b,c" (không tách mảng như khi gọi trong PowerShell) — script phải tự tách. Sau `am force-stop`, app chưa là launcher mặc định thì hộp hỏi "đặt làm mặc định" hiện mỗi lần mở Home và che kịch bản: đặt trước bằng `adb shell cmd package set-home-activity gambi.global.os26launcher/.ui.home.HomeActivity`.
- **(2026-11-23, đọc code) Build type mới dựng bằng `initWith(release)` không có thư mục nguồn `src/release/`:** thứ chỉ nằm ở đó (vd `ContactsModule`) phải có bản ở `src/<buildType>/`, không thì Hilt báo `MissingBinding` lúc `hiltJavaCompile<BuildType>`.

## SW — vuốt trang Home mượt hơn (2026-11-25, Oppo PKD130 15 trang 120 Hz + Vsmart Live 4 4 trang). Nguồn: [plans/SW-home-swipe-smoothness.md](../plans/SW-home-swipe-smoothness.md)

- **(2026-11-25, đo được) Janky trung bình che cú khựng; đếm khung > 33 ms và log từng khung chậm.** `Window.addOnFrameMetricsAvailableListener` (luồng riêng) kèm ngữ cảnh ("vừa dựng trang nào") chỉ ra ngay: ở bản `perf`, khung > 33 ms gần như luôn trùng lúc pager dựng một trang mới (Oppo 95/99 khung).
- **(2026-11-25, đo được) Dựng sẵn mọi trang của pager có lợi khi ít trang, có hại khi nhiều trang.** Live 4 (4 trang): Janky 1,05 → 0,02 %; Oppo (15 trang): 2,35 → 2,99–3,56 %, kể cả khi trang ngoài màn không vẽ (`drawWithContent { if (onScreen) drawContent() }` đỡ một phần): mỗi khung cuộn pager vẫn đặt chỗ mọi trang đã dựng. Chốt: dựng sẵn hết khi ≤ 5 trang, còn lại 1 trang mỗi bên.
- **(2026-11-25, đọc code) `staticCompositionLocalOf` đổi giá trị = dựng lại CẢ cây con.** Pager Home đổi `LocalDsBackdrop` theo trang đang hiện: static thì mỗi lần trang trượt vào màn dựng lại cả trang 24 icon. Giá trị hay đổi dùng `compositionLocalOf`.
- **(2026-11-25, đo được) Vừa cài APK thì máy yếu còn biên dịch nền (theo `.dm` / baseline profile) vài phút: số đo xấu đi ở MỌI cấu hình** (Live 4: 6–10 % so 0–1 %). Trước khi đo: `adb shell cmd package compile -m speed-profile -f <pkg>` (script làm sẵn, `-Compile`). Dấu hiệu nhận ra: cả cấu hình "như cũ" cũng xấu đi.
- **(2026-11-25, đo được) Hai máy đo song song:** `installPerf` cài lên mọi máy đang cắm; mỗi cửa sổ PowerShell chạy script với `-Serial <serial>` (lấy từ `adb devices`).
