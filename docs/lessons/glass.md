# Bài học: Kính (liquid glass)

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Thư viện LiquidGlass, mức kính, áp kính theo phần tử, làm mới kính khi đổi nền, số đo hiệu năng kính.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Thư viện LiquidGlass (`com.github.QWEA0:liquidglass:v2.0.11`)

- Lỗi tích hợp số 1: quên `enableDynamicBackground = true` khi nền hoặc kính di chuyển → kính trông như đóng băng.
- Kính chỉ chụp được thứ **trong cây View của app**, và phải là view em đứng **sau** nền trong một parent chồng lớp (`FrameLayout`). Wallpaper hệ thống không nằm trong cây View → phải tự đưa ảnh vào một `ImageView`.
- **Không** áp padding system-bar lên root của layout có kính: padding làm nền co lại. Áp inset cho từng view con cần tránh thanh hệ thống.
- Kính không khúc xạ kính khác; kính nằm trong vùng chụp của kính khác bị ẩn tạm.
- Đọc tier bằng `LiquidGlassView.effectiveApiLevel`, **không** bằng `Build.VERSION.SDK_INT`: AGSL có thể lỗi driver và thư viện tự rơi vĩnh viễn về pipeline classic.
- Custom View dùng trong XML phải có constructor nhận `AttributeSet` (bài học từ `DemoBackdropView`).
- `pressScale > 1` cần `clipChildren="false"` ở parent, không thì nút sát mép bị cắt khi nhấn.
- Kính trong Dialog/popup phải set `backdropSource = activity.findViewById(android.R.id.content)`, và **không** viết `glass.apply { backdropSource = findViewById(...) }` — trong `apply`, `findViewById` gọi trên chính kính và trả null ngầm.
- AAR chỉ có `.so` cho `arm64-v8a` và `armeabi-v7a` → emulator x86/x86_64 có thể `UnsatisfiedLinkError`. Test trên máy thật hoặc emulator arm64.

## Hiệu năng đã đo (demo, 2026-10-15)

- Oppo A3x (Android 14, tier 34, lens pipeline): giữ ~60 fps đến 6 kính với nền animate mọi frame.
- Vsmart Live 4 (Android 11, tier 30, classic CPU): 42.7 fps với 1 kính, 16.4 fps với 6 kính, cùng cảnh đó.
- Hai số trên là **trường hợp xấu nhất** (nền animate liên tục + `enableDynamicBackground = true`). Màn hình chính thật phần lớn đứng yên → phải đo lại trên màn chính (phép đo "4b") trước khi chốt số kính cho máy tier thấp.
- Chỉ số dùng để đánh giá: fps trung bình, Missed Vsync, Janky frames (dòng cách tính mới). **Bỏ** ngưỡng GPU p99 < 10 ms — không đáng tin trên Oppo (18–24 ms mà không giật).
- Quy trình đo: `adb shell dumpsys gfxinfo gambi.global.os26launcher reset` → để app chạy cảnh đó ~30 giây → chạy lại lệnh không có `reset`. Ghi rõ cảnh đo (có ảnh nền hay không, mấy kính).

## Kính trên view trong ViewPager2 (tách từ «Viết docs»)

- **Kính (`GlassSurface`) trên view nằm trong `ViewPager2` cần xử lý riêng** (2026-10-30, đọc code, suy luận chưa đo): `StaticBlurSurface.computeCrop()` cắt nền một lần theo vị trí màn hình lúc vẽ đầu, `RealGlassSurface` để `enableDynamicBackground = false`; dock đứng yên nên đủ, nhưng ô trong trang vuốt/rung/kéo sẽ lệch nền. Đừng gắn kính cho ô trong lưới mà không có phương án làm mới vùng cắt (xem ghi chú "Áp kính" cuối `plans/G2a-folders-library.md`).

## M6 — lớp kính (Task 1, 2026-10-28, đo trên Oppo A3x)

- **`enableDynamicBackground = false` + đổi nội dung `backdropSource` (set lại bitmap khác qua `setImageBitmap` trên cùng `ImageView`, không đổi object `View`) → kính KHÔNG đóng băng vĩnh viễn như suy luận ban đầu, nhưng có độ trễ khúc xạ lại nhìn thấy được bằng mắt thường** trước khi bám đúng ảnh mới (đo được, chưa đo ra số ms cụ thể). Khác với suy luận lúc viết plan M6 (nghĩ là "chụp một lần" nghĩa là đóng băng vĩnh viễn tới khi `backdropSource` được set lại bằng tay) — thực tế thư viện có tự làm mới theo một nhịp nào đó dù `enableDynamicBackground=false`, chỉ là không ngay lập tức. Nếu sau này cảm giác độ trễ này gây khó chịu cho tính năng đổi ảnh nền thật (hiện chưa có UI đổi ảnh khi đã có ảnh, chỉ test được bằng cách xoá `files/backdrop_image` qua `adb run-as` rồi mở lại), có thể cần gọi `enableDynamicBackground = true` trong một khoảng ngắn ngay sau khi đổi ảnh rồi trả lại `false` — chưa cần làm ở M6, ghi lại để biết hướng xử lý nếu M8 (màn đổi ảnh nền) cần.

## M6 — lớp kính (Task 2, 2026-10-28, đo trên Vsmart Live 4)

- **So sánh 3 cấu hình (cùng kiểu đo: vuốt trang ~15-20s, `dumpsys gfxinfo reset` trước, đọc sau):**
  không kính (baseline) Janky 0.10%/50th=5ms/90th=7ms/95th=8ms/99th=12ms; `RealGlassSurface` Janky
  10.29%/50th=14ms/90th=16ms/95th=20ms/99th=29ms; `StaticBlurSurface` Janky 2.13%/50th=5ms/90th=9ms/
  95th=11ms/99th=24ms. Kết luận: kính thật tốn rõ rệt trên tier CLASSIC (Live 4, Android 11) dù dock
  đứng yên + `enableDynamicBackground=false` (không giống mục 10 `LiquidGlass-Android.md` "~0.06ms/
  frame" — số đó là của pipeline Lens, không áp dụng cho classic). `StaticBlurSurface` rẻ hơn hẳn,
  gần baseline ở 50th/90th, chấp nhận được bằng mắt (quyết định 2026-10-28) — dùng cho tier CLASSIC.

- **`GlassSurface.view` là `ViewGroup` CÓ CON bên trong (ví dụ `StaticBlurSurface`: `FrameLayout`
  chứa `drawView`) thì `DockView.onLayout` gọi thẳng `glassSurface.view.layout(0, 0, width, height)`
  là KHÔNG ĐỦ — con bên trong (`drawView`) bị kẹt ở kích thước 0×0 vĩnh viễn, không vẽ được gì (không
  phải vẽ mờ, là không vẽ gì cả).** Nguyên nhân: `FrameLayout.onLayout()` định vị con theo kích thước
  đã **measure** của con (`child.getMeasuredWidth/Height()`), nhưng `.layout()` gọi trực tiếp không
  kích hoạt `.measure()` của `glassSurface.view` (nên cũng không kích hoạt measure của con nó).
  **`RealGlassSurface` (bọc `LiquidGlassView`) không gặp lỗi này vì nó không có con nào bên trong** —
  tự vẽ theo bounds của chính nó, không phụ thuộc kích thước đã measure của ai. Bài học chung: nguyên
  tắc cũ của `DockView` (gọi `.layout()` trực tiếp cho `IconCellView`, không cần `.measure()`) chỉ đúng
  với **leaf View tự vẽ bằng `width`/`height` của chính nó** — không áp dụng được khi phần tử đó là
  một `ViewGroup` có con, phải thêm `glassSurface.view.measure(MeasureSpec.makeMeasureSpec(width,
  EXACTLY), MeasureSpec.makeMeasureSpec(height, EXACTLY))` trong `onMeasure()` TRƯỚC khi `onLayout()`
  gọi `.layout()`. Sửa trong `DockView.onMeasure()`, nhánh `cells != null`.

- **`setBackdropSource()` gọi một lần ở `setupDock()` (lúc `onCreate`) là chưa đủ cho
  `StaticBlurSurface`:** lúc đó `backdrop_image.drawable` còn null (ảnh nền nạp bất đồng bộ sau,
  qua `render()`) nên `computeCrop()` thất bại im lặng và `croppedBitmap` mãi `null` — không có gì tự
  gọi lại để tính lại khi ảnh thật đã có (khác `RealGlassSurface`, thứ tự này không lộ ra vì kính thật
  không cần biết trước bitmap, chỉ cần `View` reference). Phải gọi lại `dockView.setBackdropSource(...)`
  mỗi khi `render()` thực sự gắn bitmap mới (`shownBackdrop !== bitmap`), không chỉ một lần lúc setup.

## M6 — lớp kính (Task 3, 2026-10-28, đo trên Oppo A3x + Vsmart Live 4)

- **Ghi rồi đọc lại một DataStore key trong CÙNG một lần `onCreate` không thấy giá trị mới ngay** nếu
  việc ghi chạy qua `viewModelScope.launch { repository.setX(...) }` (bất đồng bộ, không chờ) còn việc
  đọc là `settings.first()` chạy gần như đồng thời — `edit()` chưa kịp commit nên lần đọc đầu vẫn ra
  giá trị cũ. Gặp khi test tạm `glass_level_override` bằng hàm debug gọi ngay trước `setupDock()`: phải
  tắt hẳn tiến trình (force-stop hoặc build+cài lại) rồi mở app LẦN THỨ HAI mới thấy đúng giá trị vừa
  ghi. Không phải bug riêng của `GlassSurfaceFactory` — là hệ quả tự nhiên của việc ghi/đọc không đồng
  bộ trong cùng vòng đời; sẽ gặp lại bất kỳ khi nào test tạm kiểu "ghi settings rồi đọc ngay cùng
  onCreate". Cũng chính là lý do `GlassSurfaceFactory` (Task 3) chỉ đọc override MỘT lần ở
  `setupDock()`/`onCreate` — đổi override lúc app đang chạy không áp dụng ngay, phải đợi Activity tái
  tạo hoặc kill-restart (chấp nhận ở M6, UI cài đặt thật + áp dụng động là việc của M8).

## M8a Task 4 — mức kính: bài học triển khai (2026-10-29)

- **Đổi thứ gắn lúc dựng View (GlassSurface của dock) thì dựng lại Activity, không vá tại chỗ:** Cài đặt chỉ GHI (suspend, DataStore đã commit); `HomeActivity.onResume` so giá trị đã áp với giá trị hiện tại rồi `recreate()`. Màn phụ không sở hữu view nên không được tự `recreate()` hộ.
- **Cờ "đã áp" phải phân biệt chưa biết với đã biết:** `appliedGlassOverride: GlassLevelOverride?` null cho tới khi coroutine của `setupDock()` đọc xong và gán NGAY sau khi đọc; `onResume` thấy null thì bỏ qua (tránh `recreate()` vô ích lúc khởi động). Sau `recreate()` giá trị áp = giá trị hiện tại nên không lặp vô hạn.
- **Hộp thoại chọn mức trong Activity:** giữ tham chiếu, `dismiss()` ở `onDestroy`, chọn đúng giá trị hiện tại thì không ghi để Home không bị tạo lại vô ích.
- **Log tạm đếm số lần `onCreate` (tag `M8`) là cách kiểm chứng "đúng một lần tạo lại mỗi lần đổi"; xóa ngay sau khi người dùng xác nhận.**

## GL — áp kính: bài học (2026-11-03 đến 2026-11-04, Vsmart Live 4 + Oppo A3x)

- **Chi phí kính nằm ở số lệnh vẽ trên RenderThread, không ở ghi display list (đo được).** Ô thư mục xoay do `Jiggler` không bị vẽ lại (giai đoạn `DrawStart→SyncQueued` giữ 0.1 ms), nhưng mỗi ô kính ba lệnh (tô shader, tint, viền) làm giai đoạn `IssueDrawCommandsStart→SwapBuffers` tăng +0.85 ms (6 ô) đến +1.35 ms (tất cả), đủ đẩy đuôi p95 qua 16.7 ms. Gộp tint vào `ColorFilter` của lệnh tô và bỏ viền khi owner đang xoay (một lệnh mỗi ô) đưa Janky rung về bằng nền (4.66% → 0.46%; tất cả 8.11% → 0.52%).
- **Kính tại chỗ + `invalidate()` theo cuộn cho kết quả tốt (đo được).** 12 ô kính hiển thị cùng lúc khi vuốt: tổng thời gian khung không tăng (p50 −0.4, p95 −0.6 ms); chi phí chỉ hiện ở ghi display list (0.46 → 0.84 ms p50).
- **Janky không phải thước đo duy nhất.** Khi vuốt, Janky của "trước" đã bão hoà (72.87% với kính thật của dock bật động); dùng Δp50 và Δp95 thời gian khung từ `framestats` (`FrameCompleted − IntendedVsync`) và so giai đoạn `DrawStart→SyncQueued`.
- **`framestats` chỉ giữ ~120 khung gần nhất**: muốn thống kê cả kịch bản phải lấy mẫu định kỳ và khử trùng theo `IntendedVsync`. Dòng `N views` trong cùng đầu ra cũng khớp `^\d`; dùng `^\d+,`.
- **Máy trôi theo thời gian (đo được):** cùng một cấu hình `off6a` đọc 0.00% rồi 0.49%, `off6a` S1 lúc 2.87% lúc 10.44%. Chỉ so sánh trong cùng một phiên, xen kẽ các cấu hình, và chạy lại "trước" ở cuối phiên; đừng kết luận "A tốt hơn B" từ chênh lệch nhỏ giữa hai lượt đo cách xa nhau.
- **`input swipe x y x y 900` không phải nhấn giữ với launcher này; dùng `input motionevent` (DOWN, giữ 1 giây, MOVE nhẹ, về chỗ, UP) để vào edit mode bằng một lần kéo-thả tại chỗ** (Android 10+).
- **`enableDynamicBackground = true` = vòng `invalidate()` liên tục (đo được trên Oppo: ~90 khung dư mỗi lần mở panel):** muốn kính lấy lại nền thì chỉ cần vẽ lại kính MỘT lần (bỏ chặn → `invalidate()` → chặn lại); đừng bật chế độ động "một lúc". (Đọc bytecode v2.0.11: `drawGlassEffect` gọi `invalidate()` cuối mỗi lần vẽ khi động; đường Lens và classic đều lấy nền lúc vẽ.)
- **`LiquidGlassView` tự `invalidate()` mỗi lần cây view cuộn, kể cả khi `enableDynamicBackground = false`** (TL1 Task 1b/2b): chặn bằng `GlassSurface.setInvalidationSuppressed`; kính tại chỗ không dính.
- **Cùng một code, hai máy, chi phí ghi display list khác nhau 10 lần (đo được):** `rec` p50 tăng 0.46 → 0.84 ms ở Live 4 nhưng 0.76 → 4.7 ms ở Oppo khi 6 ô thư mục kính được `invalidate()` mỗi khung cuộn. simpleperf trên Oppo: 80% mẫu nằm ở trình thông dịch (APK debug), `IconCellView.onDraw` 21.4% mà chỉ ~6.7% là kính. Nhớ: (1) `invalidate()` một view ghi lại **cả** view, không chỉ phần thay đổi: phần tĩnh đắt (9 icon + `StaticLayout`) phải được đệm; (2) thử trên cả máy mạnh lẫn máy yếu; (3) APK debug thổi phồng chi phí Java, nhưng chủ dự án chạy bản debug nên vẫn tính.
- **Đừng tự quy đổi sp rồi đặt lại cỡ chữ khi muốn giữ nguyên giao diện (đo được trên máy `font_scale` 1.15):** `TextView` đọc `android:textSize` bằng `getDimensionPixelSize` (làm tròn, đã gồm font scale); tự dựng lại cùng giá trị bằng `TypedValue.applyDimension`/`getDimension` cho số lẻ nên ảnh chụp lệch. Khi hệ số co bằng 1, không đụng cỡ chữ; khi co, lấy `textSize` hiện tại của view rồi nhân hệ số.
- **So sánh ảnh chụp trước/sau (`adb exec-out screencap -p`) là cách bắt hồi quy điểm ảnh**: bắt được lệch cỡ chữ mà đọc code bỏ sót.
- **Phép đo ghép cặp xen kẽ + lặp "trước" ở cuối phiên** cho kết luận đáng tin: S6 phiên đầu +3.4 điểm (nhiễu), ghép cặp +1.87 điểm (đạt) với nền cả phiên cao gấp ~3 lần vì máy nóng.
- **`CategoryGroupPanelView.render()` hiện/ẩn không hoạt hoạ** nên một lượt mở/đóng chỉ tạo 1-2 khung; phép đo Janky % trên ~60 khung rất nhiễu (1 khung ≈ 1.7 điểm).
- **`debugApiLevelCap` của `LiquidGlassView` là thuộc tính của từng view, không phải toàn cục (đọc `javap`):** muốn giả lập API thấp phải đặt cho cả view dò tier lẫn các view kính thật, và đọc cấu hình debug trước phép dò đầu tiên.
- **`Bitmap.scale` thu mạnh một lần không phải là làm mờ** (xem mục Task 7 phía trên): thu bằng các bước chia đôi rồi làm mờ thật; kiểm bằng mắt trên bề mặt lớn, vì ô nhỏ che được lỗi.
- **O3 (`CLASSIC_GPU` giả lập, Oppo): Janky +0.14 điểm tối đa, p95 +2.7 ms (S2), đạt D10; không đại diện máy Android 12 thật** (đo được 2026-11-04).
- **Việc nặng một lần cũng phải ra khỏi main thread (đo được trên Oppo):** box blur 3 lượt trên 306x408 mất 78.8 ms trong APK debug; vẽ nền phẳng rồi `invalidate()` khi xong, nhớ giữ nguồn chỉ-đọc, một lần dựng cho nhiều người xin, bỏ kết quả cũ và dùng tham chiếu yếu để không giữ view.
- **Số đo Live 4 trôi theo nhiệt độ (đo được, 2026-11-04):** chạy lại S2 trên máy nguội (31-32 °C) cho khung p50 ~5 ms so với ~8 ms hôm 2026-11-03 khi máy nóng, cùng một thiết kế. Đừng so số tuyệt đối giữa hai phiên; chỉ so các cấu hình trong CÙNG một phiên (xen kẽ, lặp "trước" ở cuối phiên).
- **Dock CLASSIC có cùng lỗi răng cưa (2026-11-04):** `StaticBlurSurface` dùng `blur()` cũ (thu 8% một bước + phóng cỡ nguồn trên main thread) nên lộ ô vuông sau icon; đã chuyển sang bản nhỏ dùng chung (chia đôi + box blur, ngoài main thread) và vẽ phóng một `Rect` bằng bilinear.
- **Tier suy từ override không phải khả năng (đo được trên Live 4, 2026-11-04):** `FORCE_HIGH` cho tier LENS trên máy API 30, nên panel thành kính thật qua đường chụp CPU: S1 Janky 30.7%, p95 112 ms. Thứ chỉ đúng/đã đo trên đường Lens (kính thật của panel, ngân sách 24 ô) phải cần thêm `realGlassCapable` (API 33+); `GlassPolicy` vẫn là hàm thuần nhận khả năng làm tham số. Dock dưới `FORCE_HIGH` vẫn là kính thật theo ý người dùng.

## GS — kính theo phần tử: bài học (2026-11-05, Oppo A3x + Vsmart Live 4)

- **Con của `FrameLayout` để `LayoutParams(0, 0)` rồi tự `layout()` bị đặt về 0×0 ở mỗi lượt layout của host (đo được):** đổi cỡ đi qua `View.setFrame`, không qua `invalidate()` không đối số nên `SuppressibleGlassView` không chặn được, và kính chụp lại ở mỗi phím gõ. Live 4, gõ trong tìm kiếm: khung janky 32 xuống 5–7, p95 44–57 xuống 19–20 ms sau khi ghi cỡ thẳng vào `LayoutParams`.
- **Phép đo chỉ che đúng cảnh đã chạy:** số Oppo đẹp mà không có cảnh gõ phím, lỗi trên do review độc lập tìm ra. Khi số "đẹp quá", kiểm cấu hình có thực sự áp không (ảnh chụp pill) và hỏi cảnh nào chưa đo.
- **Kính thật chụp một lần gần như không tốn khi cuộn (đo được, Oppo):** bốn phần tử bật hết so với Tự động: vuốt Janky 1.33% so 1.38%, p95 15.08 so 15.02 ms; panel nhóm 4.42% so 4.55%. Chi phí ở lúc chụp lại, không ở lúc vẽ.
- **"Host vừa hiện" phải theo dõi bằng `OnGlobalLayoutListener` của cửa sổ:** host GONE không được layout nên listener của chính nó không thấy lúc ẩn.
- **Đổi view tự vẽ sang `FrameLayout`:** `setWillNotDraw(false)`, tự đặt con trong `onLayout`, vẽ phần phủ trong `dispatchDraw`, chặn chạm ở `onInterceptTouchEvent`. `onVisibilityChanged` có thể chạy trong constructor trước khi trường được khởi tạo.
- **Thêm giá trị vào enum lưu trong DataStore:** thêm cuối, đọc bằng `entries.find` để tên lạ rơi về mặc định. "Đã lưu chưa" dùng `prefs.contains(khóa)`.
- **`am_create_activity` cho 0 trên Oppo A3x:** không đếm được `recreate()` bằng cách này trên máy đó.
- **Việc còn lại:** `HomeViewModel.currentGlassLevelOverride()` không còn nơi gọi, chờ dọn. Dòng "`GlassBudget` chưa ai gọi" ở các mục M6/GL đã cũ, hàm đã xóa.

## SM — công tắc phải tạo khác biệt nhìn thấy (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **SM — công tắc phải tạo khác biệt nhìn thấy:** "kính mờ tint 72%" và "phẳng 95%" gần như giống nhau, chủ dự án tưởng công tắc hỏng. Công tắc menu icon giờ là kính thật / kính mờ như bốn công tắc GS. Khóa `GlassElement` mới: vắng đọc thành TẮT, không đưa vào `glassCustomSaved`.

## SM — chữ trông nhỏ hơn (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **"Chữ trông nhỏ hơn" chưa chắc là cỡ chữ nhỏ hơn:** đo chiều cao glyph trên ảnh chụp trước khi đổi `textSize`; khác biệt cảm nhận đến từ bảng hẹp và hàng thấp.

## SM — bề rộng cố định và font_scale (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Bề rộng cố định + `font_scale` lớn = cắt chữ:** menu 200 dp ổn ở Oppo (`font_scale` 0,9) nhưng cắt nhãn ở Live 4 (1,15). Kiểm giao diện có chữ ở cả hai cỡ chữ (follow-up SW).

## DG — kính thật chỉ lấy nền ở lần vẽ kính (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **DG — kính thật chỉ lấy nền ở lần vẽ kính:** `LiquidGlassView` với `enableDynamicBackground = false` giữ ảnh chụp cũ cho tới lần vẽ sau. Mọi kính thật phải chụp lại đúng một lần ở MỌI `BackdropChange` (cả `LOADED`, không chỉ `REPLACED`) bằng `captureOnce`: bỏ chặn, `invalidate()`, trả cờ chặn về trạng thái cuộn thật (không ép `true`). Đo: dock none→ảnh sai khác 52,0 (Oppo) / 7,7 (Live 4) → 0,0.

## DG — cách đo kính sau khi đổi nền (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Cách đo kính sau khi đổi nền:** chụp Home 2,5 s sau resume không chạm, so với ảnh sau khi ép vẽ lại; sai khác trung bình > 0 là kính cũ. Script ở knowledgement DG mục 7.

## AP — kính thật của view có TỔ TIÊN đang dịch (2026-11-10, Oppo A3x)

- `setCaptureAtLayoutPosition` chỉ bỏ phép biến đổi của chính view kính và cha trực tiếp. Dock nằm trong `home_content`, lớp này trượt khi vuốt sang Library; kính chụp nền theo vị trí đang dịch (một phần ngoài màn = đen) rồi giữ ảnh hỏng khi về chỗ. Sửa: `RealGlassSurface.setCaptureIgnoringAncestorTransforms(true)` (vị trí = vị trí layout trong cửa sổ + gốc cửa sổ), chỉ bật cho dock, và `captureGlassOnce()` khi Home về đúng chỗ (p = 0). Kiểm bằng ảnh chụp dock sau 3 vòng vuốt đi/về.

## GF — kính mờ khi lớp Home trượt, nền nhóm Library (2026-11-10, Oppo A3x + Vsmart Live 4)

- **Kính mờ tại chỗ (`BackdropBlurDrawable`, `StaticBlurSurface`) lấy lát nền theo vị trí màn hình LÚC VẼ, gồm phép dịch của tổ tiên.** Lớp trượt bằng `translation` không làm con vẽ lại, nên lần vẽ giữa chừng để lại lát sai (ô thư mục, widget, pill trên trang cuối sau khi vuốt sang Library: đo được trên Oppo, widget đồng hồ đổi sang mảng tím phẳng). Sửa: `core/glass/GlassLocation.locationOnScreen` trừ phép dịch của tổ tiên gắn tag `R.id.glass_ignore_translation` (đặt trên `home_content`), giữ nguyên phép biến đổi của chính owner (đẩy-dồn, phóng, rung) và vị trí của pager; khi Home về chỗ (p = 0) gọi `AppLibraryController.refreshHomeGlass()` vẽ lại một lần. Kính thật của pill dùng `setCaptureIgnoringAncestorTransforms(true)` như dock.
- **Kiểm bằng ảnh trước/sau ở CÙNG chỗ:** đặt widget lên trang cuối, vuốt sang Library và về 3 lần, chụp trước và sau; trước khi sửa lệch, sau khi sửa giống hệt.
- **`Paint.alpha` không nhân vào `ColorFilter` chạy sau shader:** tint `PorterDuffColorFilter` vẫn hiện ở alpha 0; muốn mờ vào phải nhân alpha vào màu tint.
- **Nền màn nhóm Library = ảnh nền đã làm mờ sẵn** (`BlurredBackdropCache`, một `BackdropBlurDrawable` toàn màn) cho mọi máy: Live 4 (API 30, không `RenderEffect`) hết bị gần đen. Khi nền mờ đã phủ kín thì nhoè `RenderEffect` lớp phía sau là chi phí vô hình (tắt bằng `LIBRARY_BEHIND_BLUR_DP = 0`).
- **Cử chỉ có điều kiện cho phép phải kiểm lúc DOWN và lúc vượt slop:** vuốt rời Library bị chặn khi nhóm đang mở hoặc đang chạy hoạt ảnh mở.


## FX1, FX2 — nền kính theo tỉ lệ view, chi phí rung (2026-11-11, Vsmart Live 4)

- **Bitmap nền phải cùng tỉ lệ view** (đọc code, xác nhận trên Live 4 bằng ảnh vạch màu, 2026-11-11): kính tại chỗ (`BackdropBlurDrawable`, `StaticBlurSurface`) kéo giãn bitmap mờ vừa view nền, còn `ImageView` `centerCrop` thì cắt; nên cắt vùng giữa theo tỉ lệ view lúc nạp. Ảnh mặc định lệch ~12% nên trước FX1 kính lệch nhẹ.
- **Đừng kỳ vọng nhiều từ việc trim cache blur** (đo được trên Live 4, 2026-11-11): bản blur nhỏ chỉ ~0,57 MB. `trim` ở `COMPLETE` giải phóng Native 28,3 → 22,9 MB và Graphics 13,6 → 0 MB nhưng chưa tách được phần do trim blur; phần lớn đến từ nơi khác (hệ thống/HWUI, suy luận chưa kiểm chứng).
- **So `drawable !== ref.get()` với tham chiếu yếu không đủ khi drawable là null** (đọc code, 2026-11-11): `null !== null` là false, phải reset riêng nhánh null.
- **C16: thay một `ValueAnimator` mỗi ô bằng một `Choreographer.FrameCallback` mỗi lưới không giảm CPU rung** (đo được trên Live 4, bản debug, 2026-11-11): trung vị 428 so 428 tick/10 s. Chi phí rung không nằm ở dispatch animator ở luồng chính, khớp mục GL "chi phí kính nằm ở RenderThread"; ticker đã bỏ.

## FX8 — kính khớp nền (2026-11-11, Vsmart Live 4)

- **Lỗi "kính sai nền" tưởng tái hiện được thực ra đo trên APK cũ** (FX8, đo được, 2026-11-11): output build của worktree chính ngày 2026-11-10 bị cài nhầm. Ghi md5/commit của APK đã cài trong mọi phép đo và xác nhận bằng log của đúng bản đang chạy trước khi kết luận nguyên nhân (xem thêm cách kiểm APK ở `permissions-and-device-testing.md`).
- **Kính tại chỗ ánh xạ qua `ImageView.imageMatrix` + cỡ intrinsic, không giả định bitmap cùng tỉ lệ view** (FX8, đọc code, đo được không hồi quy, 2026-11-11): bất biến "bitmap cùng tỉ lệ view" phải kiểm ở chỗ vẽ chứ không chỉ ở nguồn (bổ sung cho mục "Bitmap nền phải cùng tỉ lệ view" của FX1 ở trên). Sai số vị trí e(x) = x·(bw·s/vw − 1) + dx. Dock CLASSIC dùng ma trận float thay `Rect` nguyên (trước lệch tới 4-8 px).
- **Bản mờ nhỏ tính theo cỡ view** (FX8, tính từ code, 2026-11-11): độ mờ tính bằng px màn hình nhất quán; trước đây σ = 9,8·viewW/bitmapW (20 px với ảnh 527x1140).
- **`Bitmap.getPixels` trả màu không premultiply** (FX8, đọc code, 2026-11-11): ảnh có alpha phải nhân alpha vào RGB trước khi mờ để khớp việc `ImageView` vẽ lên nền đen.
- **Kính mờ không được lười dựng bản mờ** (FX8, đo được trên Live 4, 2026-11-11): `library_view` GONE nên ô chỉ được dựng ở lượt layout đầu lúc hiện; dựng sớm bằng `IdleHandler` theo cỡ view cha, và bỏ qua dựng lại ở `onSizeChanged` nếu `Sizes` không đổi. Làm nóng bản mờ ngay khi ảnh nền nạp (`prewarm`: dựng ~358 ms trong khi Library hiện sau ~11,8 s) đã đủ để ô nhóm hiện kính ngay.

## HT — công tắc Home (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/HT-home-toggles.md](../knowledgements/HT-home-toggles.md)

- **(2026-11-13, đọc code, HT) — `glass`:** ẩn pill đi nhánh `GlassMode.FLAT` sẵn có để tái dùng `setRealActive`/`scheduleCapture`, không thêm alpha lên con kính thật (đường chưa kiểm); chạy ca "tắt → đổi nền → bật" trên Oppo khi pill là kính thật.

## JC — kính Backdrop (Kyant0) cho Compose (2026-11-19, Samsung SM-A176B Android 16). Nguồn: [knowledgements/JC-compose-pilot.md](../knowledgements/JC-compose-pilot.md)

- **`layerBackdrop` ghi `drawContent()`, tức phần vẽ nằm SAU nó trong chuỗi modifier** (đo được, đọc mã nguồn `LayerBackdropNode`). Viết `.dsAurora().layerBackdrop(b)` thì lớp ghi rỗng, kính không vẽ gì; đúng là `.layerBackdrop(b).dsAurora()`. Với `Image`/`Pager` làm con của Box có `layerBackdrop` thì đúng sẵn.
- **Lambda truyền cho `drawBackdrop` bị node giữ từ lần dựng đầu** (đo được): giá trị đổi theo cỡ đo (bán kính bo) phải để trong `mutableFloatStateOf` và đọc trong lambda; bắt sẵn giá trị thì kính giữ bán kính 0 (góc vuông).
- **Shape phải là `CornerBasedShape`** (`lens` ném lỗi với shape khác) và độ cao khúc xạ ≤ bán kính góc (đọc mã nguồn).
- **Dưới API 31 `drawBackdrop` vẫn vẽ lại nền nhưng không hiệu ứng**: rẽ sang `DsSurfaceDrawable`, không gọi Backdrop (đọc mã nguồn; nhánh này chưa chạy trên máy thật).
- **Trên nền mượt (aurora) blur/lens gần như vô hình.** Muốn chứng minh kính đang vẽ: tạm thêm `colorControls(brightness = 0.5f)` rồi chụp màn (đo được).
- **Vẽ lớp bề mặt bằng chính `DsSurfaceDrawable` trong `onDrawSurface`**: nền `ds_surface_fill` chỉ đặc 20% nên giữ đúng hình DS3 mà vẫn thấy blur thật phía sau; tắt `highlight`/`shadow` của Backdrop cho viền khớp DS3 (đo được).

## JD — chính sách kính Compose (2026-11-19, Oppo PKD130 Android 14). Nguồn: [knowledgements/JD-compose-design-system.md](../knowledgements/JD-compose-design-system.md)

- **Số bề mặt kính thật trên một màn là chi phí chính trên máy yếu** (đo được, catalog cuộn 20 lần, bản debug): ~15 bề mặt Backdrop 37–40% janky (50th 36 ms); tắt lens 29%; chỉ card có kính 10% (22 ms); tắt hẳn 7,8%. Chủ dự án chốt (JD D9): chỉ `CARD`/`SHEET` có kính thật, control nhỏ vẽ tĩnh.
- **Hộp thoại/sheet Compose ở cửa sổ riêng**: toạ độ lớp backdrop của màn chính không khớp, nên không dùng kính thật trong đó (`LocalDsBackdrop = null`).
- **Kính đọc `GlassLook` trong lambda của `drawBackdrop`** (thư viện theo dõi state đọc trong `effects`/`highlight` và chạy lại) + `rememberGlassLook()` nghe `GlassEnvironment`: kéo thanh trong Cài đặt kính là đổi ngay. `GlassLook.DEFAULT.refraction` = 0 (không phải 50).

## JS + JX — toàn bộ UI sang Compose, gỡ lớp View (2026-11-20, Samsung SM-A176B). Nguồn: [knowledgements/JS-JX-compose-all-screens.md](../knowledgements/JS-JX-compose-all-screens.md)

- **QWEA0 đã gỡ** (JX): mức API của kính dò bằng `Build.VERSION.SDK_INT`; cài đặt "Mức kính" và công tắc kính từng phần tử chưa tác động lên kính Compose (JX Q3, plan riêng).
- **Nền mờ từ ảnh thu nhỏ bị vỡ điểm** (quan sát, JS): ảnh nền 96 px phải vẽ với `FilterQuality.High`, thêm `Modifier.blur(24.dp)` trên API ≥ 31 (`wallpaperSoftBlur()`).
- **Nền CC quá trong** (chủ dự án, JS7b): blur sau panel 48 dp, `cc_blur_behind_radius` 56 dp; chủ dự án chốt "Vừa rồi".

## Bổ sung chép từ repo XML (ở bản XML nằm cuối mục «HT — công tắc Home (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/HT-home-toggles.md](../knowledgements/HT-home-toggles.md)»)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-19, P2-9e) Muốn kính thì menu phải nằm trong cửa sổ Home.** `PopupWindow`/`Dialog` là cửa sổ riêng, không có `backdrop_image` để `BackgroundGlass` lấy mẫu → chỉ được nền phẳng. Menu "Sửa" làm view thêm vào `home_root` + lớp phủ trong suốt bắt chạm ngoài để đóng (cùng cách `IconActionMenu`).
- **(2026-11-19, polish) `View.setBackground` thay padding của view bằng padding của drawable.** Nền `InsetDrawable` (chỉ lề trên/dưới) xóa `paddingHorizontal` trong XML của nút "Sửa"/"Xong" — chỉnh lề mấy lần không đổi gì. Gán nền bằng code thì lưu padding trước, đặt lại sau (`GlassPillBackground`).
