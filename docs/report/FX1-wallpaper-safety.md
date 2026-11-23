# FX1 — An toàn ảnh nền (A1, A5, B2, C3, C4, C5) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ điều phối chạy phép đo và quyết (chủ dự án đã ủy quyền việc duyệt cho điều phối ngày 2026-11-11, nên không phải điểm dừng của chủ dự án); cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x không cần ở đợt này). Lệnh viết cho CMD/PowerShell (script adb ở cuối viết cho Git Bash vì có chuyển hướng nhị phân). **Điều phối chạy mọi lệnh adb; agent thực thi không cài APK, không chạm máy.**

**Nhánh:** `fix/fx1-wallpaper-safety` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX1-wallpaper-safety` · **PR:** #31 (draft) · **Bắt đầu:** 2026-11-11 · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review PR (Task 1-6 xong, đã kiểm máy trừ nhập qua bộ chọn ảnh)
**Khóa:** không (không đổi schema Room, không thêm thư viện, không thêm chuỗi)

**Goal:** Chọn một ảnh nền lớn (ảnh toàn cảnh, ảnh 108 MP) không còn làm launcher sập lặp lại; ảnh nền Home giữ trong RAM đúng bằng cỡ và tỉ lệ vùng Home (≈ 9 MB cho 1080x2100, với mọi ảnh vào); kính tại chỗ lấy đúng đoạn nền nằm sau nó; đổi sáng/tối nạp lại ảnh mặc định đúng; nhập ảnh nền không bỏ qua lỗi ghi file và Home luôn nhận ảnh mới kể cả khi người dùng bấm Back giữa chừng; cache blur không giữ ảnh nền cũ và trả bộ nhớ khi hệ thống sắp tắt app; dock ở mức kính CLASSIC dùng chung bộ đệm blur.

**Architecture:** Bộ thu ảnh dùng chung `core/bitmap/BitmapScaler` (tính vùng cắt `centerCrop`, cỡ đầu ra, `inSampleSize`, trần pixel). **File lưu giữ nguyên toàn ảnh** (đã xoay đúng chiều, thu về trần pixel khi nhập). `load(reqW, reqH)` chỉ giải mã **vùng giữa có tỉ lệ bằng màn** bằng `BitmapRegionDecoder` + `inSampleSize` rồi thu nốt về đúng cỡ: không bao giờ cấp phát cả ảnh gốc, và bitmap của Home có cùng tỉ lệ với `ImageView` nên kính (vốn kéo giãn bitmap vừa view) khớp đúng. Repository bắt `OutOfMemoryError` ở mọi đường giải mã và phát một `StateFlow` phiên bản ảnh nền sau khi nhập xong; `HomeViewModel` thu flow đó và nhớ cỡ màn cuối. `BlurredBackdropCache` giữ nguồn bằng tham chiếu yếu và có `trim()` gọi từ `ComponentCallbacks2` đăng ký trong `GlassEnvironment`.

**Tech Stack:** không thêm gì. `BitmapFactory`, `BitmapRegionDecoder` (framework, API 10+), `android.media.ExifInterface` (đã dùng), `ComponentCallbacks2`, `StateFlow`.
**Spec:** không có TT riêng. Nguồn việc: `docs/report/Audit-Report-2026-11-10.md` mục A1, A5, B2, C3, C4, C5 và mục "Quyết định của chủ dự án (2026-11-11)". Liên quan: `docs/plans/WP-default-wallpaper.md`, `GL-glass-pass.md`, `DG-dock-glass-refresh.md`, `M1-home-activity.md`, `project_structure.md` mục 3 (`core/bitmap/` đã dự kiến `BitmapScaler`, `ExifReader`, `TransparentTrimmer`).

## Hiện trạng đã kiểm lại với code (b1af810, code chưa đổi)

| Mục | Khẳng định của báo cáo | Kiểm với code | Kết luận |
|---|---|---|---|
| A1 | `decodeScaled` chỉ dùng `inSampleSize`, không thu nốt | `PickedImageSource.kt:149-155`: vòng `while (w/(sample*2) >= reqW && h/(sample*2) >= reqH)` rồi `decodeStream` rồi `applyOrientation`; không có `createScaledBitmap`. `load` (`:42-45`) và `import` (`:51-68`) đều đi qua đó | Đúng. Ảnh 12000x3000 trên vùng Home 1080x2100: `3000/2 = 1500 < 2100` nên `sample = 1` → 36 MP ≈ 144 MB > 100 MB (giới hạn `RecordingCanvas.throwIfCannotDraw`) |
| A1 | `import` lưu ảnh quá cỡ | `save(bmp)` (`:188-198`) nén chính bitmap vừa giải mã | Đúng (JPEG 12000x3000 → file 36 MP) |
| A1 | `BlurredBackdropCache` chỉ bắt `Exception` | `BlurredBackdropCache.kt:68-73` `catch (e: Exception)` trên luồng `GlassBlur` | Đúng. `OutOfMemoryError` là `Error` nên đi thẳng tới handler của luồng → process chết |
| A1 (thêm) | — | `HomeViewModel.loadBackdropFromDisk` (`:173-191`) gọi `loadBackdrop` trong `viewModelScope.launch` không try/catch; `BackdropRepositoryImpl.loadBackdrop` (`:45-47`) không bắt gì | **Chỗ hở thứ hai:** `OutOfMemoryError` lúc `load` ở mỗi lần khởi động cũng làm sập lặp lại (launcher mặc định thì kẹt). Plan bắt ở repository |
| A5 | Ảnh ngang 12 MP giữ 48,8 MB | 4032x3024 trên vùng 1080x2100: `sample = 1` → 12,19 MP x 4 = 48,8 MB. Đúng | Đúng. `WallpaperApplier.kt:46,56` nhận bitmap đã nạp rồi tạo thêm một bản cỡ màn (`flattenCenterCrop`): sau sửa, bản nạp đã bằng cỡ màn nên không còn là vấn đề, không cần sửa `WallpaperApplier` |
| B2 | `homeRoot.width` bằng 0 ở `onResume` đầu, thoát ở `width <= 0` | `BackdropController.onResume` (`:76-81`) truyền `homeRoot.width/height`; `HomeViewModel.reloadBackdropIfChanged` (`:165-171`) thoát ở `width <= 0` trước khi so version. `onScreenSize` (`:154-159`) thoát vì `screenSizeHandled` | Đúng (đọc code; đo trên Live 4 ngày 2026-11-10). Chưa xác nhận bằng log rằng `DefaultBackdropSource.isNight()` đọc đúng uiMode mới tại thời điểm đó: Task 3 Step 1 kiểm |
| C3 | Cache giữ tham chiếu mạnh nguồn | `BlurredBackdropCache.kt:21,34,35` `smallSource`, `failedSource`, `pendingSource` đều là `Bitmap?` mạnh. **Thêm:** `BackdropBlurDrawable.kt:55-56` `lastDrawable`/`lastSource` cũng mạnh, giữ ảnh nền cũ trong mọi ô thư mục/pill/widget chưa vẽ lại | Đúng, và còn một chỗ giữ nữa ngoài báo cáo |
| C3 | Không có `onTrimMemory` | `grep onTrimMemory\|ComponentCallbacks` trong `app/src/main` rỗng | Đúng |
| C4 | Bỏ qua kết quả `compress()`/`renameTo()` | `PickedImageSource.kt:188-198` đúng như vậy | Đúng |
| C4 | Back trước khi nhập xong thì Home giữ ảnh cũ, mất sự kiện | `SettingsViewModel.onImagePicked` (`:152-177`) chạy trong `viewModelScope`; thoát Settings hủy scope. `withContext(io)` trong repo là khối chặn nên vẫn ghi xong file, nhưng không ai báo Home. Home chỉ so `backdropVersion()` ở `onResume` | Đúng |
| C5 | Dock có `BlurredBackdropCache()` riêng | `DockController.kt:32,78,96` | Đúng. Mỗi lần Activity dựng lại tạo thêm một executor `GlassBlur` không bao giờ đóng (luồng nằm mãi) |

**Phát hiện thêm, nay được giải quyết bằng chính thiết kế của Task 1 (không còn Task 7):** `BackdropBlurDrawable.prepareShader` (`:183`) và `StaticBlurSurface.computeCrop` (`:134-143`) ánh xạ bitmap mờ **kéo giãn vừa đúng cỡ view ảnh nền**, trong khi `backdrop_image` là `ImageView` `centerCrop` (`activity_home.xml:18`). Hai cách chỉ khớp khi tỉ lệ bitmap bằng tỉ lệ view. Ảnh mặc định 1320x2868 (tỉ lệ 0,460) so với vùng Home 1080x2100 (0,514) lệch ~12%; ảnh chọn ngang lệch nhiều hơn nhiều: kính tại chỗ (ô thư mục, pill, panel, dock CLASSIC) hiển thị nền khác chỗ nó đang đứng. Đọc code, chưa đo. Kính thật (`RealGlassSurface`) chụp chính view nên không dính. Khi bitmap của Home có đúng tỉ lệ view thì phép kéo giãn trở thành đúng, nên không phải sửa `core/glass` cho việc này.

### Người dùng của bitmap do `load` trả về (kiểm trước khi đổi)

| Nơi dùng | Dùng thế nào | Cần ảnh không cắt? | Hệ quả |
|---|---|---|---|
| `HomeViewModel.loadBackdropFromDisk` → `BackdropController` → `backdrop_image` (`centerCrop`) | Hiển thị; cũng là nguồn cho `BlurredBackdropCache`, `BackdropBlurDrawable`, `StaticBlurSurface`, `RealGlassSurface` (chụp view) | **Không** | Bitmap cắt có tỉ lệ bằng view → `centerCrop` hiển thị trọn bitmap, hình nhìn thấy **không đổi**; kính tại chỗ khớp |
| `BackdropRepositoryImpl.applyAsWallpaper` → `source.load(width, height)` → `WallpaperApplier.flattenCenterCrop` | Cắt `centerCrop` về `width x height` rồi `WallpaperManager.setBitmap` | **Không** (nó tự cắt đúng vùng đó) | `width/height` do Settings (cỡ màn) hoặc Home (cỡ `homeRoot`) truyền vào, nên `load` cắt đúng vùng nó sẽ cắt; kết quả nét hơn trước (không qua trần cạnh). Không cần sửa `WallpaperApplier` |
| `BackdropRepositoryImpl.importBackdrop` → giá trị trả về | `SettingsViewModel` chỉ kiểm `null` | — | Đường nhập **không** cắt (xem dưới). Chữ ký giữ nguyên `(uri, reqW, reqH)`; hai tham số không còn dùng cho file lưu (giữ để khỏi sửa Settings, ngoài vùng) |
| `DefaultBackdropSource.load` (ảnh mặc định, từ tài nguyên) | Qua cùng `loadBackdropFromDisk` | **Không** | Cũng cắt theo tỉ lệ vùng Home (cắt trong bộ nhớ sau khi giải mã, ảnh chỉ ~1,3 MP nên không cần `BitmapRegionDecoder`); hình nhìn thấy không đổi, kính khớp hơn |
| Màn Cài đặt | Không hiển thị ảnh nền (chỉ `hasBackdrop()`/nhãn) | — | Không bị ảnh hưởng |

Kết luận: **không nơi nào cần ảnh không cắt từ `load`**. Chỉ **file lưu** (`files/backdrop_image`) phải giữ nguyên toàn ảnh, để hình nền hệ thống và vùng Home đổi cỡ (vd chia đôi màn hình) về sau vẫn cắt được từ ảnh đầy đủ.

## Vì sao chọn thiết kế này

**Vấn đề cần giải:**
1. Launcher là app HOME: lỗi ở đường nạp ảnh nền lúc khởi động thì sập lặp, người dùng kẹt tới khi xóa dữ liệu (A1, đã tái hiện 2026-11-10 trên Live 4).
2. Ngưỡng 100 MB của canvas (≈ 26,2 MP ARGB_8888) chỉ là một trong ba giới hạn: còn heap/native và kích thước texture GPU tối đa.
3. Ảnh đã lưu trong `files/backdrop_image` của người dùng hiện tại có thể đã quá cỡ, nên chỉ sửa `import` là chưa đủ, `load` cũng phải "chữa" ảnh cũ **mà không cấp phát cả ảnh**.
4. Kính tại chỗ giả định bitmap cùng tỉ lệ view (phát hiện thêm ở trên).
5. `HomeViewModel` và `core/glass/*` là file nóng độc quyền của FX1 đợt này; `HomeActivity`, `BackdropController` thuộc vùng FX2 nên cố ý không sửa.
6. `minSdk 26`: không dùng được API 28+ không kèm đường lui.

### Quyết định 1 — cách đưa ảnh nền về cỡ màn (A1 + A5)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Thu về "cover" rồi chặn bằng hai trần (bản plan đầu) | `inSampleSize` + `createScaledBitmap` như `DefaultBackdropSource.load`; đích = `min(cover, 1, trần pixel, trần cạnh 4096)` | Ít mã, cùng cơ chế đã chạy cho ảnh mặc định | Bộ nhớ vẫn 13-27 MB cho ảnh thường, cao hơn nhiều so với cần (≈ 9 MB); toàn cảnh bị chặn bởi trần cạnh nên mềm (phóng ~2 lần); **bitmap giữ tỉ lệ ảnh, không phải tỉ lệ màn → kính tại chỗ vẫn lệch**; đỉnh tạm khi giải mã có thể tới ~4 lần cỡ đích | Không |
| B. Cắt vùng giữa theo tỉ lệ màn khi `load`, giải mã bằng `BitmapRegionDecoder` + `inSampleSize`, thu nốt về cỡ màn; **file lưu giữ nguyên toàn ảnh (có trần pixel khi nhập)** | Quyết định của điều phối ở vòng 1 | Bộ nhớ ≈ `reqW x reqH x 4` cho mọi ảnh (≈ 9 MB trên 1080x2100); không bao giờ cấp phát cả ảnh gốc (đỉnh tạm chỉ cỡ vùng cắt ở mức lấy mẫu); ảnh cũ quá cỡ được chữa ở `load`; bitmap cùng tỉ lệ view nên kính khớp (bỏ Task 7 cũ); toàn cảnh nét (vùng giữa giải mã gần nguyên độ phân giải); `applyAsWallpaper` cũng nhận ảnh nét | `BitmapRegionDecoder` chỉ chắc chắn với JPEG/PNG (file lưu **luôn** là JPEG hoặc PNG, do `save` ghi) và PNG có thể chậm hơn (cần đo); thêm một đường giải mã (cần đường lui) | **Có** |
| C. `ImageDecoder.setTargetSize` + `setCrop` | Giải mã thẳng tới cỡ đích | Gọn | API 28+, `minSdk 26` nên vẫn cần nhánh `BitmapFactory`: hai đường để kiểm | Không |
| D. Chỉ từ chối ảnh quá lớn | Quá ngưỡng thì báo lỗi | Rất nhỏ | Không chữa ảnh đã lưu trên máy người dùng; từ chối ảnh toàn cảnh hợp lệ | Không |

**Vì sao B (cơ sở):** (1) báo cáo A1 đề "sau khi giải mã, thu về cỡ cover... ở cả `import` lẫn `load`"; B làm điều đó mạnh hơn: kết quả đúng cỡ màn chứ không chỉ "cover" (đọc code + phép tính). (2) Về EXIF: `import` đã **xoay đúng chiều trước khi lưu** và `compress` không ghi EXIF, còn `load` luôn truyền `ORIENTATION_NORMAL` (`PickedImageSource.kt:44`). Nên `load` làm việc trên ảnh đã thẳng và **không cần ánh xạ vùng cắt qua phép xoay**; phần xoay chỉ có ở `import`, nơi không cắt vùng. Không cần đường lui "giải mã cả ảnh rồi cắt khi orientation ≠ 0". (3) Đường lui khi `BitmapRegionDecoder.newInstance`/`decodeRegion` ném lỗi hoặc trả null (vd file lạ): giải mã cả ảnh với `inSampleSize` bị chặn bởi `MAX_DECODE_PIXELS`, cắt vùng giữa trong bộ nhớ, thu nốt. (4) Sửa luôn lệch kính mà không đụng `core/glass` (đọc code; xác nhận bằng ảnh thử có vạch màu ở bài kiểm).

**Chi tiết thuật toán (`BitmapScaler`, hằng số là đề xuất, sửa được):**
- `MAX_SAVED_PIXELS = 16_000_000`: trần pixel cho **file lưu** khi nhập (≈ 64 MB nếu giải mã, dưới ngưỡng 100 MB của canvas). **Không** có trần cạnh: file lưu không bao giờ lên GPU, chỉ vùng cắt cỡ màn lên GPU nên không còn ràng buộc texture 4096.
- `MAX_DECODE_PIXELS = 25_000_000`: trần pixel của **một lần giải mã** (≈ 100 MB). Nó chặn đỉnh tạm của `import` (bắt buộc vì `inSampleSize` chỉ có lũy thừa 2, giải mã có thể lớn gấp 4 cỡ đích) và của đường lui.
- `cropRegion(srcW, srcH, reqW, reqH): Rect`: vùng giữa có tỉ lệ `reqW:reqH`, lớn nhất trong ảnh (nguồn rộng hơn thì giữ chiều cao, hẹp hơn thì giữ chiều rộng), làm tròn, căn giữa.
- `outputSize(region, reqW, reqH)`: `s = min(1, reqW / regionW)`; không phóng to (như `DefaultBackdropSource` hiện tại: ảnh nhỏ hơn màn thì giữ nguyên, `ImageView` phóng khi vẽ); tỉ lệ vẫn bằng tỉ lệ màn.
- `sampleSizeFor(w, h, dstW, dstH, maxDecodePixels)`: lũy thừa 2 lớn nhất sao cho `w/s >= dstW && h/s >= dstH`; nếu ảnh giải mã vẫn vượt `maxDecodePixels` thì tăng `s` gấp đôi cho tới khi hết vượt.
- `savedSize(srcW, srcH)`: cỡ lưu khi nhập = ảnh đã xoay, thu về `MAX_SAVED_PIXELS` nếu vượt, không phóng to.
- `scaleTo(bmp, w, h)`: `createScaledBitmap(..., filter = true)`, recycle nguồn khi khác.

**Số dự báo (phép tính, Live 4, vùng Home 1080x2100, ARGB_8888). Ảnh đặt thẳng vào `files/backdrop_image` (đường `load`):**

| Ảnh | Trước (bitmap giữ lâu trong RAM) | Sau (cỡ giữ lâu) | Đỉnh tạm lúc giải mã vùng giữa |
|---|---|---|---|
| 12000x3000 | 36 MP = 144 MB → **sập** | 1080x2100 = **9,07 MB** | vùng 1543x3000 = 18,5 MB (sample 1) |
| 3024x4032 | 12,19 MP = 48,8 MB | **9,07 MB** | vùng 2073x4032 = 33,4 MB |
| 4032x3024 | 48,8 MB | **9,07 MB** | vùng 1555x3024 = 18,8 MB |

**Qua bộ chọn ảnh (đường `import`):** ảnh 4032x3024 và 3024x4032 (12,2 MP ≤ 16 MP) lưu **nguyên cỡ**; sau đó Home nạp 9,07 MB như trên. Toàn cảnh 12000x3000 (36 MP): `sampleSizeFor` buộc `sample = 2` (vì 36 MP > `MAX_DECODE_PIXELS`) → lưu 6000x1500 (9 MP; đỉnh tạm khi nhập 36 MB), Home nạp vùng giữa 771x1500 = **4,6 MB** (không phóng to nên nhỏ hơn cỡ màn, `ImageView` phóng ~1,4 lần: nét hơn thiết kế A, vẫn kém ảnh đặt thẳng vào `files/`). Nếu muốn nét hơn cho ảnh toàn cảnh mới nhập thì nâng `MAX_DECODE_PIXELS`, đổi lại đỉnh tạm cao hơn (xét lại nếu đo thấy an toàn).

**Đánh đổi chấp nhận:** (1) ảnh toàn cảnh cực lớn nhập qua bộ chọn mất một nửa độ phân giải mỗi chiều so với gốc (do trần giải mã); ảnh cũ trên máy người dùng thì được chữa ở độ nét tối đa; (2) file lưu có thể tới 16 MP (đĩa, không phải RAM); (3) `BitmapRegionDecoder` thêm một đường giải mã, có đường lui; thời gian giải mã PNG cần đo trên Live 4 (Task 1 Step 6); (4) vùng Home đổi cỡ sau khi nạp (chia đôi màn hình, không dựng lại Activity) làm bitmap lệch tỉ lệ view tạm thời: kính lệch như hiện nay, `ImageView` vẫn đúng; không làm ở đây; (5) không nén RGB_565 hay bitmap HARDWARE: 565 gây banding trên nền chuyển sắc; HARDWARE không đọc được pixel cho bước blur của `BlurredBackdropCache`.

### Quyết định 2 — cách Home biết có ảnh mới (C4)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Hỏi lại ở `onResume` (hiện tại) | So `backdropVersion()` mỗi lần resume | Không đổi gì | Back trước khi nhập xong thì file ghi sau lần resume cuối → Home giữ ảnh cũ cho tới lần resume sau (báo cáo C4) | Không |
| B. Chạy việc nhập trong `ApplicationScope` | `SettingsViewModel` giao việc cho scope toàn app | Việc không bị hủy | Phải sửa `SettingsViewModel` + vẫn cần cách báo Home; hai scope để lo | Không |
| C. `StateFlow<Long>` phiên bản ở repository + việc ghi chạy `NonCancellable` | Repository (singleton) tự phát phiên bản mới sau khi ghi xong; `HomeViewModel` thu và nạp lại | Không phụ thuộc vòng đời màn nào; Home cập nhật tại chỗ; `SettingsViewModel` không cần sửa; đúng hướng báo cáo gợi ý | Thêm một thành viên vào interface `BackdropRepository` | **Có** |

`onResume` vẫn giữ (lưới an toàn cho thay đổi không đi qua repository, vd người dùng xóa file bằng adb, và cho đổi sáng/tối của ảnh mặc định, vốn không phải sự kiện của repository). Cơ sở: đọc code (`withContext(io)` là khối chặn nên file vẫn được ghi xong khi scope bị hủy; kotlinx.coroutines: kết quả `withContext` bị hủy nhưng tác dụng phụ bên trong vẫn xảy ra, nên việc phát version phải nằm **trong** khối).

### Quyết định 3 — cách sửa B2

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. ViewModel nhớ cỡ màn cuối | `onScreenSize` và `reloadBackdropIfChanged` ghi `lastW/lastH` khi hợp lệ; thiếu thì dùng giá trị nhớ | Chỉ sửa `HomeViewModel` (vùng FX1); cũng là cỡ cho bộ thu flow ở Task 4 | Giả định cỡ không đổi giữa hai lần dựng Activity (đúng: `screenSize`/`orientation` nằm trong `configChanges` nên đổi cỡ không dựng lại) | **Có** |
| B. `doOnLayout` trong `BackdropController.onResume` | Chờ layout xong rồi gọi | Cỡ luôn là cỡ thật | Sửa `BackdropController`; thêm một lần nạp sau layout, nháy lâu hơn | Dự phòng |
| C. Thêm `uiMode` vào `configChanges` | Không dựng lại Activity khi đổi sáng/tối | Không còn vấn đề | Theme, tài nguyên `-night`, mọi view đã dựng không tự đổi; phải sửa `HomeActivity` + manifest (vùng FX2); phá hành vi đã duyệt ở WP (D8) | Không |

### Quyết định 4 — trả bộ nhớ khi hệ thống thiếu RAM (C3)

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ tham chiếu yếu, không trả gì khi trim | `WeakReference` cho nguồn trong cache và drawable | Không có nguy cơ nháy | Không "trả bộ nhớ khi hệ thống thiếu RAM" như yêu cầu | Một phần (là nền của B) |
| B. A + bỏ blur nhỏ chỉ ở mức `TRIM_MEMORY_COMPLETE` | `ComponentCallbacks2` trong `GlassEnvironment`; `trim()` xóa bản nhỏ và báo người giữ nhả | Đúng yêu cầu; chỉ chạy khi app sắp bị tắt, nên nháy kính một-hai khung khi quay lại là chấp nhận được (cold start cũng có chuyển nền phẳng → kính) | Lợi ích nhỏ: bản blur chỉ ~0,57 MB (270x525x4, phép tính từ `SMALL_FACTOR 0,25`, `MAX_SMALL_SIDE 640`). Đừng kỳ vọng số lớn | **Có** |
| C. B + bỏ ảnh nền chính ở mức COMPLETE, nạp lại ở `onResume` | Giải phóng ≈ 9 MB bitmap chính | Lợi ích thêm | **Nháy đen/ảnh trống khi quay về:** `home_root` có `@color/black`, ảnh chính nạp lại bất đồng bộ nên ít nhất một số khung đen; launcher là app người dùng quay lại thường xuyên. Phần GPU (18,6 → 0 MB) hệ thống đã tự trả (đo 2026-11-10); sau Task 1 bitmap chính chỉ còn ≈ 9 MB | Không |

**Vì sao không bỏ ảnh nền chính:** quay về Home mà thấy nền đen dù chỉ vài khung là lỗi nhìn thấy được, còn lợi ích sau Task 1 chỉ ≈ 9 MB trên ~150 MB PSS, và hệ thống (HOME_APP_ADJ) hiếm khi đưa launcher mặc định tới cuối hàng LRU. **Điều phối đã chốt đồng ý ở vòng 1.** Xét lại nếu đo thấy launcher bị tắt thường xuyên vì thiếu RAM.

### Quyết định 5 — C5
Chỉ một phương án hợp lý: truyền `glassEnv.blurCache` (đã dùng chung bởi ô thư mục, pill, panel, ô nhóm) vào `setBackdropSource` rồi bỏ `dockGlassCache`. Cơ sở: đọc code; `BlurredBackdropCache` đã thiết kế nhiều người xin cùng một nguồn ("nhiều ô cùng xin: chỉ MỘT lần dựng cho mỗi bitmap nguồn", `:65-66`).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | FX1 gồm A1, A5, B2, C3, C4, C5, làm A1 đầu tiên | Chủ dự án 2026-11-11 ("sửa tất cả mục không xung đột"), điều phối xếp việc |
| D2 | Bitmap Home = vùng giữa theo tỉ lệ màn, ≤ `reqW x reqH`, giải mã bằng `BitmapRegionDecoder`; **file lưu không cắt**, nhập thì chặn `MAX_SAVED_PIXELS = 16 MP`; giải mã chặn `MAX_DECODE_PIXELS = 25 MP`. Bỏ trần cạnh 4096 và bỏ Task 7 (căn kính) | **Điều phối quyết, vòng 1 (2026-11-11)**; hai hằng số do agent đề xuất, phép đo Task 1 xác nhận |
| D3 | Không bỏ ảnh nền chính khi trim | Điều phối đồng ý ở vòng 1 |
| D4 | Chỉ trim ở `TRIM_MEMORY_COMPLETE` | Đề xuất của agent |
| D5 | Lỗi nhập ảnh dùng lại chuỗi `settings_backdrop_failed`, không thêm chuỗi | Điều phối quyết ở vòng 1 (chữ "Không đọc được ảnh này" hơi lệch khi nguyên nhân là hết bộ nhớ, chấp nhận) |
| D6 | Back giữa lúc nhập: không hiện hộp thoại "Áp làm wallpaper" (ảnh nền trong app vẫn đổi) | Điều phối chấp nhận ở vòng 1; **đây đã là hành vi hiện tại** (scope Settings bị hủy nên sự kiện `AskWallpaperTarget` đã mất từ trước), FX1 không làm nó tệ hơn |
| D7 | Không thêm test, thư viện, schema, chuỗi | `AGENTS.md` |
| D8 | "Bắt buộc kiểm tay" của Task 1 là phép đo do điều phối chạy và quyết | Chủ dự án ủy quyền việc duyệt cho điều phối 2026-11-11 |
| D9 | Nhập ảnh JPEG có EXIF bình thường (không xoay) và không cần cắt viền trong suốt: **chép nguyên byte** vào `backdrop_image` (giới hạn kích thước file, vd ≤ 30 MB; quá thì đi đường giải mã có trần như cũ). Ảnh toàn cảnh nhờ vậy vẫn nét trên Home vì `load` giải mã theo vùng từ bản gốc | Điều phối 2026-11-11, khi duyệt plan; làm trong Task 1 |

## Ngoài phạm vi / ghi để sau
- Căn kính theo `centerCrop` bằng cách sửa ánh xạ trong `core/glass`: **bỏ** (đã giải quyết bằng tỉ lệ bitmap, D2). Vẫn có dòng kiểm trong Review Focus.
- **Ghi chú cho FX5:** cache RAM lớn nhất không nằm ở `core/glass` mà ở `IconCache` (`LruCache` cỡ `maxMemory/8`, `data/apps/*`, vùng FX5): nên xét `trimMemory` ở đó khi nhận `ComponentCallbacks2`.
- Ghi đè lại `files/backdrop_image` quá cỡ sau khi `load` (chữa vĩnh viễn, đỡ tốn đĩa). Không cần cho bộ nhớ (vì `load` đã chữa); không làm để tránh ghi file trên đường nạp (rủi ro đua với `import`).
- Nạp lại ảnh khi vùng Home đổi cỡ mà không dựng lại Activity (chia đôi màn hình): nay bitmap lệch tỉ lệ view tạm thời, như hiện nay.
- `PickedImageSource.lastImportInfo`/`describe` không còn nơi dùng (đọc code: không có tham chiếu ngoài file). Không xóa; xin phép xóa riêng.
- Bỏ hai tham số `reqW/reqH` của `importBackdrop` (nay không dùng): cần sửa `SettingsViewModel`/`BackdropRepository`, ngoài vùng; để dọn sau.
- `SystemWallpaperSource` (phương án B đang gác) có thể có cùng lỗi giải mã; chưa nối vào đâu, không đụng.
- Nút "Gỡ ảnh nền", chọn ảnh từ bộ iOS 27 (WP đã ghi).
- Số đo thời gian: nếu đo thì ghi rõ debug/release; số bộ nhớ không phụ thuộc loại build.

## Vùng file

| File | Tạo / Sửa | Task | Nóng (độc quyền / dùng chung) |
|---|---|---|---|
| `core/bitmap/BitmapScaler.kt` | Tạo | 1 | — |
| `core/bitmap/ExifReader.kt` | Tạo | 2 | — |
| `core/bitmap/TransparentTrimmer.kt` | Tạo | 2 | — |
| `data/backdrop/PickedImageSource.kt` | Sửa | 1, 2, 4 | — (FX1) |
| `data/backdrop/DefaultBackdropSource.kt` | Sửa | 2 | — (FX1) |
| `data/backdrop/BackdropRepositoryImpl.kt` | Sửa | 1, 4 | — (FX1) |
| `domain/repository/BackdropRepository.kt` | Sửa (thêm `backdropVersions`) | 4 | dùng chung nhỏ; chỉ `BackdropRepositoryImpl` hiện thực |
| `core/glass/BlurredBackdropCache.kt` | Sửa | 1 (một dòng `catch`), 6 | **độc quyền** `core/glass/*` |
| `core/glass/BackdropBlurDrawable.kt` | Sửa | 6 | **độc quyền** |
| `core/glass/StaticBlurSurface.kt` | Sửa | 6 | **độc quyền** |
| `core/glass/GlassEnvironment.kt` | Sửa | 6 | **độc quyền** |
| `ui/home/HomeViewModel.kt` | Sửa, **chỉ phần ảnh nền** (khối `:142-191`, thêm một `init` thu flow đặt **sau** các khai báo trường) | 3, 4 | **độc quyền** |
| `ui/home/controller/DockController.kt` | Sửa | 5 | FX1 theo bảng "Kế hoạch sửa" của báo cáo |

**Không sửa** (cố ý): `ui/home/HomeActivity.kt`, `ui/home/controller/BackdropController.kt`, `IconGridView`, `DockView`, `FolderPanelView`, `Jiggler` (FX2); `data/layout/*` (FX3); `ui/search/*`, `data/search/*` (FX4); `ui/settings/SettingsViewModel.kt`, `SettingsActivity.kt` (không cần: đường nhập ảnh vẫn trả `Bitmap?`, lỗi trả null thì Settings đã hiện `settings_backdrop_failed`); `data/backdrop/WallpaperApplier.kt` (không cần, xem bảng người dùng của bitmap); `res/values/strings.xml`.

## Global Constraints
- Luật `ui → domain ← data`: `domain/repository/BackdropRepository` chỉ thêm kiểu `StateFlow<Long>` (kotlinx, được phép); `core/bitmap` chỉ import `android.graphics`/`android.media.ExifInterface`, không biết feature nào.
- Không thêm test, thư viện, schema Room, chuỗi, màu, dimens.
- Mọi giải mã/thu ảnh chạy trên `@IoDispatcher` (đã đúng, không đổi). Không giữ `Bitmap` bằng tham chiếu mạnh trong singleton mà không có lý do (C3).
- **File lưu không bị cắt** và luôn là JPEG/PNG đã xoay đúng chiều (không EXIF): `load` dựa vào điều này.
- Trạng thái của `BlurredBackdropCache` chỉ đụng từ main thread; luồng `GlassBlur` chỉ đọc bitmap nguồn (ràng buộc cũ của GL Task 8, giữ nguyên).
- Trong `HomeViewModel`, khối `init` mới phải đặt **sau** khai báo `screenSizeHandled`, `shownBackdropVersion`, `lastScreenW/H`, `backdropJob` vì `viewModelScope` chạy `Dispatchers.Main.immediate` (cùng cảnh báo ở `:93-97`).
- Không `git merge origin/main` vào nhánh (`MultiAgent-Workflow.md` mục 11); lấy `main` mới thì báo điều phối.
- Không xóa file; không sửa tài liệu chung (mục 5.4).
- Log tạm dùng tiền tố `FX1`, **xóa trước khi báo xong Task** có chứa nó (grep `FX1` trong `app/src` phải rỗng).
- Build `.\gradlew.bat assembleDebug` xanh mỗi Task; chủ dự án chạy bản **debug** nên số bộ nhớ/thời gian ghi rõ debug.

## Review Focus
1. `BitmapScaler`: `cropRegion` luôn nằm trong ảnh và có tỉ lệ bằng `reqW:reqH` (sai số làm tròn ≤ 1 px); không bao giờ phóng to; bitmap trả về từ `load` ≤ `reqW x reqH`; `import` không để file lưu vượt `MAX_SAVED_PIXELS`; một lần giải mã không vượt `MAX_DECODE_PIXELS`; mọi bitmap trung gian bị recycle đúng, không recycle bitmap đang trả về; `BitmapRegionDecoder` luôn được `recycle()` (try/finally).
2. `load` chữa ảnh cũ quá cỡ bằng giải mã vùng, không cấp phát cả ảnh, không ghi file; có đường lui khi `BitmapRegionDecoder` lỗi.
3. Không còn đường `OutOfMemoryError` nào thoát ra luồng IO/`GlassBlur` mà không được bắt: `loadBackdrop`, `loadDefaultBackdrop`, `importBackdrop`, `applyAsWallpaper`, `BlurredBackdropCache`.
4. `save`: `compress`/`renameTo` được kiểm; thất bại dọn `.tmp` và **không** đụng file cũ; lỗi → `IOException` → `importBackdrop` trả null.
5. `importBackdrop` phát version **bên trong** khối `NonCancellable` và chỉ khi thành công.
6. `HomeViewModel`: một `backdropJob` (hủy lần nạp trước khi nạp mới); `shownBackdropVersion` vẫn gán đồng bộ trước khi launch; `init` mới nằm sau các trường.
7. `reloadBackdropIfChanged` dùng cỡ nhớ khi `width <= 0`; đường ảnh người dùng và ảnh mặc định (`backdropIsDefault`, chặn prompt wallpaper) không đổi.
8. `BlurredBackdropCache`/`BackdropBlurDrawable`: không còn tham chiếu mạnh tới bitmap nguồn nằm ngoài vòng đời dựng; so sánh danh tính qua `WeakReference.get() === source`; `trim()` chỉ chạy ở main thread và không đụng `pendingSource` đang dựng.
9. `DockController` không còn `BlurredBackdropCache()`; `StaticBlurSurface` dùng cache chung vẫn hủy lời xin khi tách cửa sổ.
10. Ảnh mặc định (WP) sau khi cắt theo tỉ lệ vùng Home: hình nhìn thấy không đổi (so ảnh chụp ở vùng không có kính).
11. **Căn kính:** kiểm với một ảnh chọn có tỉ lệ khác màn (ảnh ngang có vạch màu): ô thư mục/pill/panel/dock CLASSIC hiển thị đúng đoạn nền nằm sau nó (không còn lệch kéo giãn như bản `main`).

---

### Task 1: A1 + A5 — `load` giải mã vùng giữa theo tỉ lệ màn; file lưu có trần; chặn OOM
**Bắt buộc kiểm tay:** có — (mục 4.2) phép đo dùng để nghiệm thu thiết kế, do **điều phối chạy và quyết** (không phải điểm dừng của chủ dự án): (i) bộ nhớ Home ≈ `reqW x reqH x 4` cho mọi ảnh; (ii) giả định chỉ máy thật trả lời: `BitmapRegionDecoder` trên Live 4 với JPEG lớn và với PNG có alpha (đúng không, nhanh cỡ nào); (iii) hai hằng số D2. Nếu (ii) sai thì phải đổi thiết kế trước các Task sau.
**Files:** Tạo `core/bitmap/BitmapScaler.kt`. Sửa `data/backdrop/PickedImageSource.kt`, `data/backdrop/BackdropRepositoryImpl.kt`, `core/glass/BlurredBackdropCache.kt` (một dòng).
**Interfaces:** Produces `object BitmapScaler { MAX_SAVED_PIXELS; MAX_DECODE_PIXELS; cropRegion(srcW, srcH, reqW, reqH): Rect; outputSize(regionW, regionH, reqW, reqH): android.util.Size; sampleSizeFor(w, h, dstW, dstH, maxDecodePixels): Int; savedSize(srcW, srcH): android.util.Size; scaleTo(bmp, w, h): Bitmap }`. Consumes `PickedImageSource` (`load`, `import`, `decodeScaled`).
- [x] **Step 1: `BitmapScaler`** theo "Chi tiết thuật toán" ở mục "Vì sao chọn thiết kế này". Nguồn tham khảo: `DefaultBackdropSource.load` (`:44-71`).
- [x] **Step 2: `PickedImageSource.load(reqW, reqH)`.** Đọc cỡ file (`inJustDecodeBounds`); `region = cropRegion(...)`; `out = outputSize(region)`; `sample = sampleSizeFor(region.w, region.h, out.w, out.h, MAX_DECODE_PIXELS)`; `BitmapRegionDecoder.newInstance(file.path, false)` (try/finally `recycle()`); `decodeRegion(region, Options(inSampleSize = sample))`; `scaleTo(out)`. Nếu `newInstance`/`decodeRegion` ném `IOException`/`IllegalArgumentException` hoặc trả null: **đường lui** `decodeStream` với `sampleSizeFor` (cỡ cả ảnh) rồi cắt vùng giữa trong bộ nhớ (`Bitmap.createBitmap` theo `region / sample`) và `scaleTo`. `load` vẫn không ghi file. `reqW/reqH <= 0` thì coi là không có yêu cầu cắt (trả cỡ lưu bị chặn bởi `MAX_DECODE_PIXELS`).
- [x] **Step 3: `PickedImageSource.import(uri, ...)`.** Giữ đọc `orientation` (`ExifInterface`) và `bounds`; `target = savedSize(w, h)` trên cỡ **sau xoay** (đổi chỗ chiều theo EXIF); `sample = sampleSizeFor(w, h, target.w, target.h, MAX_DECODE_PIXELS)`; `decodeStream(inSampleSize)`; **thu trước** về `target` nếu bitmap lớn hơn (đổi chỗ chiều vì chưa xoay); **xoay sau** (`applyOrientation` trên bản nhỏ); `trimTransparentBorders` như cũ; `save`. **Không cắt theo màn.** `reqW/reqH` không còn dùng.
- [x] **Step 4: bắt `OutOfMemoryError` ở repository.** `BackdropRepositoryImpl`: `loadBackdrop`/`loadDefaultBackdrop` → bắt `OutOfMemoryError`, `Log.w`, trả null (Home vào `Error`, nền đen, **không sập**: tránh sập lặp lúc khởi động); `importBackdrop` → null (Settings hiện `settings_backdrop_failed`, D5); `applyAsWallpaper` → `Result.failure(IOException("không đủ bộ nhớ"))`. Bắt ở đây chứ không ở `HomeViewModel` vì bắt một chỗ cho mọi người gọi và `HomeViewModel` chỉ được sửa phần ảnh nền. Thêm log tạm `FX1` kích thước bitmap sau `loadBackdrop` (xóa ở Step 7).
- [x] **Step 5: `BlurredBackdropCache`.** `catch (e: Exception)` ở `:70` thành `catch (e: Throwable)` (kết quả vẫn `null` → `failedSource`, kính vẽ phẳng).
- [x] **Step 6:** `.\gradlew.bat assembleDebug` xanh.
- [x] **Step 7: Kịch bản kiểm (Live 4)** (điều phối đã đo (a), (b), (d) ngày 2026-11-11, đạt; (c) nhập qua bộ chọn và PNG/EXIF hoãn sang bài kiểm cuối) — dùng các script ở mục "Bài kiểm" bên dưới; điều phối ghi số, gỡ log `FX1`:
  (a) **A1 chặn sập (chỉ chạy trên bản đã sửa):** đặt ảnh 12000x3000 vào `files/backdrop_image`, `force-stop`, mở Home: không sập, Home hiện vùng giữa **nét**, bitmap `1080x2100` (log `FX1`), `logcat` không có `OutOfMemory`, `FATAL`, `too large`.
  (b) **A5 số bộ nhớ:** với bản `main` rồi bản nhánh, cùng ảnh 3024x4032 và 4032x3024: `Native Heap`, `Graphics`, `TOTAL PSS`; dự báo: Native Heap giảm ≈ 40 MB (48,8 → 9,07 MB) cho cả hai; Graphics giảm cùng bậc (texture cùng cỡ bitmap; nếu không thấy đổi thì ghi lại, không coi là lỗi). Ảnh 12000x3000 chỉ có số "sau" (≈ +9 MB so với ảnh trống).
  (c) **Nhập qua bộ chọn ảnh:** chọn ảnh toàn cảnh JPEG, ảnh ngang 4:3 và `fx1_trim.png` (PNG có alpha → file lưu là PNG, kiểm đường `BitmapRegionDecoder` với PNG): không sập; `ffprobe` file lưu: toàn cảnh ≈ 6000x1500, 4:3 giữ 4032x3024, PNG đúng cỡ đã cắt viền; Home hiện đúng, đo thời gian từ lúc Home resume tới lúc ảnh hiện (log `FX1`) để xem PNG có chậm bất thường không; bấm "Không áp dụng" ở hộp thoại wallpaper.
  (d) **Căn kính (Review Focus 11):** chọn `fx1_bars.jpg` (ảnh ngang có vạch màu dọc): dock (CLASSIC) / pill / ô thư mục hiển thị đúng màu vạch nằm sau chúng; so với bản `main` (lệch).
  (e) **Chỉ được code bảo vệ, chưa tái hiện được:** nhánh `OutOfMemoryError` và đường lui khi `BitmapRegionDecoder` lỗi (không có cách an toàn ép trên máy chủ dự án): ghi đúng như vậy ở bảng bằng chứng.

### Task 2: Tách `ExifReader`, `TransparentTrimmer`; `DefaultBackdropSource` dùng `BitmapScaler` (refactor + cắt ảnh mặc định theo tỉ lệ)
**Bắt buộc kiểm tay:** không (điều phối so ảnh chụp trước/sau).
**Files:** Tạo `core/bitmap/ExifReader.kt`, `core/bitmap/TransparentTrimmer.kt`. Sửa `data/backdrop/PickedImageSource.kt`, `data/backdrop/DefaultBackdropSource.kt`.
**Interfaces:** Produces `ExifReader.readOrientation(InputStream): Int`, `ExifReader.swapsAxes(orientation): Boolean`, `ExifReader.applyOrientation(Bitmap, Int): Bitmap`; `TransparentTrimmer.trim(Bitmap): Bitmap`. Consumes `BitmapScaler` (Task 1).
- [x] **Step 1: `ExifReader`** — chuyển nguyên `readOrientation`, đoạn `swap` (`:142-145`) thành `swapsAxes`, và `applyOrientation` (`:158-181`). Thân hàm giữ nguyên chữ. `describe(orientation)` (chuỗi tiếng Việt cho `lastImportInfo`, không ai dùng) **ở lại** `PickedImageSource` (xem "Ngoài phạm vi").
- [x] **Step 2: `TransparentTrimmer`** — chuyển nguyên `trimTransparentBorders` (`:78-108`) cùng hằng `ALPHA_THRESHOLD` và chú thích quyết định 5a.
- [x] **Step 3: `PickedImageSource`** gọi ba hàm mới; xóa phần đã chuyển (xóa **mã đã chuyển**, không xóa file). Hành vi không đổi so với cuối Task 1.
- [x] **Step 4: `DefaultBackdropSource.load`** dùng `BitmapScaler`: `cropRegion` trên cỡ `bounds`; `sampleSizeFor` theo vùng và cỡ đầu ra; `decodeResource(inSampleSize, inScaled = false)`; cắt vùng (chia theo `sample`) bằng `Bitmap.createBitmap`; `scaleTo`. Cắt **trong bộ nhớ** vì ảnh chỉ ~1,3 MP (không cần `BitmapRegionDecoder`). Hình nhìn thấy không đổi (`centerCrop` cũng hiển thị đúng vùng này); khác biệt duy nhất là bitmap có tỉ lệ bằng vùng Home nên kính khớp hơn.
- [x] **Step 5:** `assembleDebug` xanh.
- [x] **Step 6: Kịch bản kiểm (Live 4)** — (a) `fx1_trim.png`: nhập qua bộ chọn, Home phủ kín không lộ đen ở viền (như trước); (b) một ảnh chụp dọc từ camera (EXIF xoay 90/270): hiển thị đúng chiều; (c) xóa `files/backdrop_image` để về ảnh mặc định, `cmd uimode night yes/no` mỗi bên: ảnh chụp màn hình khớp bản `main` ở vùng không có kính (`ffmpeg` so dải khoảng trống trên cùng; chấp nhận sai khác ≤ 1-2 px do làm tròn); `mem` ≈ 9 MB bitmap thay vì ≈ 10,1 MB (1080x2346) của bản cũ; (d) kính: ảnh mặc định, ô thư mục/pill khớp nền hơn bản `main` (ghi, không bắt buộc).

### Task 3: B2 — đổi sáng/tối nạp lại ảnh mặc định
**Bắt buộc kiểm tay:** không (điều phối chạy script `cmd uimode`).
**Files:** Sửa `ui/home/HomeViewModel.kt` (phần ảnh nền).
**Interfaces:** Consumes `BackdropRepository.backdropVersion()`. Produces `lastScreenW/H`, `backdropJob` (dùng tiếp ở Task 4).
- [x] **Step 1: xác nhận nguyên nhân bằng log tạm** `Log.d("FX1", "reload v=$version shown=$shownBackdropVersion w=$width h=$height")` ở đầu `reloadBackdropIfChanged`. Điều phối chạy kịch bản (a) bên dưới **trên bản chưa sửa B2** và gửi log: kỳ vọng thấy `w=0` ở lần `onResume` đầu sau dựng lại. Nếu log cho thấy `w>0` mà `v == shown`, thì nguyên nhân là `isNight()` (cấu hình ứng dụng chưa cập nhật): dừng, báo điều phối, chuyển sang phương án B của quyết định 3 hoặc truyền night từ Activity (sẽ cần `BackdropController`).
- [x] **Step 2: nhớ cỡ.** Thêm `private var lastScreenW = 0; lastScreenH = 0`. Trong `onScreenSize` (cả nhánh thoát sớm) và `reloadBackdropIfChanged`, nếu `width > 0 && height > 0` thì ghi `lastScreenW/H`. `reloadBackdropIfChanged` dùng `val w = if (width > 0) width else lastScreenW` (tương tự `h`) và chỉ thoát khi `!screenSizeHandled || w <= 0 || h <= 0`.
- [x] **Step 3: một lần nạp tại một thời điểm.** `private var backdropJob: Job?`; mọi nơi `viewModelScope.launch { loadBackdropFromDisk(...) }` (cả `onScreenSize`) thành `backdropJob?.cancel(); backdropJob = viewModelScope.launch { ... }`, để nạp sau luôn thắng nạp trước (hai lần nạp chồng nhau là đúng kịch bản của Task 4).
- [x] **Step 4:** `assembleDebug` xanh. Gỡ log `FX1`.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — kịch bản (4) của "Bài kiểm": xóa `files/backdrop_image` (đã sao lưu), `cmd uimode night no`: Home chuyển sang ảnh sáng trong ≤ ~1 giây **mà không cần bấm gì thêm** (đo bằng `YAVG` của ảnh chụp: sáng cao hơn tối rõ rệt); `night yes` về tối. Trước khi sửa (báo cáo): vẫn tối. Chú ý **không bấm Home/khóa màn hình giữa chừng** vì `onResume` thứ hai tự chữa lỗi này và che mất nó.

### Task 4: C4 — nhập ảnh an toàn; Home nhận ảnh mới kể cả khi Back sớm
**Bắt buộc kiểm tay:** không (điều phối chạy script; mục (b) cần thao tác thời gian, điều phối làm).
**Files:** Sửa `domain/repository/BackdropRepository.kt`, `data/backdrop/BackdropRepositoryImpl.kt`, `data/backdrop/PickedImageSource.kt`, `ui/home/HomeViewModel.kt`.
**Interfaces:** Produces `BackdropRepository.backdropVersions: StateFlow<Long>`. Consumes `lastScreenW/H`, `backdropJob` (Task 3), `reloadBackdropIfChanged`.
- [x] **Step 1: `PickedImageSource.save` kiểm kết quả.** `compress(...)` trả `false` → xóa `.tmp`, ném `IOException("không nén được ảnh nền")`; `renameTo(file)` trả `false` → xóa `.tmp`, ném `IOException("không đổi tên được file ảnh nền")`. Bọc phần ghi trong `try`, `finally` dọn `.tmp` khi chưa đổi tên thành công. File cũ **không bị đụng** khi lỗi (ghi tmp rồi đổi tên đã đảm bảo).
- [x] **Step 2: interface + repository.** `BackdropRepository`: `val backdropVersions: StateFlow<Long>` (chú thích: phát lại `backdropVersion()` sau mỗi lần nhập thành công; ảnh mặc định đổi sáng/tối **không** phát qua đây). `BackdropRepositoryImpl`: `private val _versions = MutableStateFlow(backdropVersion())`; `importBackdrop` chạy `withContext(io + NonCancellable)`; thành công (`source.import` khác null) thì `_versions.value = backdropVersion()` **trong khối**. Bắt `IOException`/`SecurityException`/`OutOfMemoryError` như Task 1.
- [x] **Step 3: `HomeViewModel` thu flow.** Ngay sau khai báo `shownBackdropVersion`/`lastScreenW/H`/`backdropJob` (xem Global Constraints): `init { viewModelScope.launch { backdropRepository.backdropVersions.collect { reloadBackdropIfChanged(0, 0) } } }` (dùng cỡ nhớ). Giá trị đầu của `StateFlow` đến khi `screenSizeHandled == false` nên bị bỏ qua đúng; `onScreenSize` lo lần nạp đầu. Giữ nguyên `BackdropController.onResume → reloadBackdropIfChanged` (lưới an toàn, và đường của ảnh mặc định).
- [x] **Step 4:** `assembleDebug` xanh. Cập nhật chú thích của `reloadBackdropIfChanged` và `importBackdrop` cho đúng.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) chọn ảnh bình thường qua Cài đặt: Home đổi như trước (hồi quy M8a Task 5), kính dock chụp lại; (b) kịch bản (5) của "Bài kiểm" (Back sớm): sau khi nhập xong, **không chạm máy**, Home tự đổi sang ảnh mới (trước khi sửa: giữ ảnh cũ tới lần resume sau; đo nếu điều phối muốn có số "trước"); (c) hai lần chọn liên tiếp trong vài giây: Home kết thúc ở ảnh **sau**, không nhấp nháy về ảnh trước; (d) **chỉ được code bảo vệ:** nhánh `compress`/`renameTo` thất bại (không tạo được tình huống đầy bộ nhớ an toàn trên máy chủ dự án).

### Task 5: C5 — dock CLASSIC dùng chung `glassEnv.blurCache`
**Bắt buộc kiểm tay:** không.
**Files:** Sửa `ui/home/controller/DockController.kt`.
**Interfaces:** Consumes `GlassEnvironment.blurCache`.
- [x] **Step 1:** bỏ `import BlurredBackdropCache` và `dockGlassCache`; hai chỗ `setBackdropSource(binding.backdropImage, dockGlassCache)` (`:78`, `:96`) dùng `glassEnv.blurCache`. Cập nhật chú thích đầu lớp ("Sở hữu `dockGlassCache`...") và chú thích M6 Task 2/3 trên dòng bỏ.
- [x] **Step 2:** `assembleDebug` xanh.
- [x] **Step 3: Kịch bản kiểm (Live 4, đổi tạm Mức kính sang FORCE_LOW)** — đếm luồng `GlassBlur` bằng `ps -T`: trước khi sửa: sau khởi động có 2 (cache chung + cache dock), mỗi lần Home dựng lại (đổi sáng/tối) thêm 1 luồng không bao giờ mất; sau khi sửa: luôn 1. Dock vẫn mờ đúng (so ảnh chụp với bản `main` cùng ảnh nền: sai khác ≈ 0 khi ảnh nền có tỉ lệ bằng màn; với ảnh khác tỉ lệ thì bản mới đúng hơn, xem Task 1(d)). Sau kiểm **trả Mức kính về FORCE_HIGH** (khôi phục từ bản sao lưu `files/datastore`).

### Task 6: C3 — không giữ ảnh nền cũ; trả bộ nhớ ở `TRIM_MEMORY_COMPLETE`
**Bắt buộc kiểm tay:** không (số đo và ảnh chụp do điều phối; nháy kính nếu có là điều kiện nghiệm thu của điều phối).
**Files:** Sửa `core/glass/BlurredBackdropCache.kt`, `core/glass/BackdropBlurDrawable.kt`, `core/glass/StaticBlurSurface.kt`, `core/glass/GlassEnvironment.kt`.
**Interfaces:** Produces `BlurredBackdropCache.trim()`, `SmallListener.onSmallDropped()` (phương thức có thân rỗng mặc định, `fun interface` vẫn hợp lệ).
- [x] **Step 1: tham chiếu yếu trong cache.** `smallSource`, `failedSource`, `pendingSource` thành `WeakReference<Bitmap>?`; mọi so sánh `source === x` thành `x?.get() === source`. Lambda dựng trong `executor.execute` vẫn giữ `source` mạnh trong lúc dựng (cần thiết, ngắn). `smallBitmap` giữ nguyên (bản blur ~0,57 MB).
- [x] **Step 2: tham chiếu yếu trong `BackdropBlurDrawable`.** Thay `lastDrawable: Drawable?` và `lastSource: Bitmap?` bằng một `WeakReference<Drawable>`; ở `prepareShader`, khi cần xin bản nhỏ thì lấy bitmap từ `view.drawable` hiện tại thay vì từ `lastSource`. So sánh danh tính `drawable !== lastDrawableRef.get()`.
- [x] **Step 3: `trim()` + người giữ nhả.** `BlurredBackdropCache.trim()`: đặt `smallBitmap`, `smallSource`, `failedSource` về null (không đụng `pendingSource` đang dựng, không đụng `waiters`), rồi gọi `onSmallDropped()` cho `registered`. `BackdropBlurDrawable.onSmallDropped`: `smallBitmap = null; shader = null; fillPaint.shader = null` (không `invalidate`, vì đang ở nền; lần vẽ sau sẽ xin lại qua đường sẵn có). `StaticBlurSurface`: đăng ký vào cache trong `setBackdrop` (`blurred?.register(this)`) và `onSmallDropped` đặt `blurSource = null; cropSrc = null`. Lưu ý: `notifyBackdropChanged` gọi `onSmallReady` của mọi `registered` nên `StaticBlurSurface` được báo thêm khi đổi ảnh; vô hại (chỉ `invalidate`).
- [x] **Step 4: `ComponentCallbacks2` trong `GlassEnvironment`.** Trong `init`, `context.registerComponentCallbacks(object : ComponentCallbacks2 { onTrimMemory(level) { if (level >= TRIM_MEMORY_COMPLETE) blurCache.trim() } ... })` (`context` là Application; callback chạy ở main thread). Singleton sống suốt process nên không cần gỡ. Hằng `TRIM_MEMORY_COMPLETE` có thể bị cảnh báo `deprecation` ở compileSdk 37: dùng `@Suppress("DEPRECATION")` có chú thích (suy luận chưa kiểm chứng: Android 15+ có thể không còn gửi mức này; Live 4 chạy Android 11 vẫn gửi). **Không** đụng ảnh nền chính (quyết định 4, D3).
- [x] **Step 5:** `assembleDebug` xanh.
- [x] **Step 6: Kịch bản kiểm (Live 4)** — kịch bản (7) của "Bài kiểm": (i) `am send-trim-memory ... COMPLETE` khi Home ở nền: không sập; `Native Heap` giảm vài trăm KB tới ~1 MB (dự báo từ cỡ bản blur; nếu không thấy giảm thì ghi lại, không coi là lỗi); (ii) quay về Home: ô thư mục/pill/dock hiện kính lại trong ≤ ~2 giây, **không kẹt ở nền phẳng** (ảnh chụp ngay sau và 2,5 giây sau; sai khác của vùng kính phải về ≈ 0); (iii) đổi ảnh nền A → B rồi ép GC (`kill -10` qua `run-as`): `Native Heap` còn xấp xỉ một ảnh (trước khi sửa có thể còn cả ảnh A do `lastSource` của các ô ở trang đệm; dự báo, không bắt buộc đạt số); (iv) chỉ được code bảo vệ: giữ ảnh cũ bằng tham chiếu yếu khi GC (không có cách đo trực tiếp).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (serial `BGB00153427`, Android 11, 1080x2340, mức kính hiện tại FORCE_HIGH, **Os26Launcher là launcher mặc định**). **Bản build:** debug của nhánh (và debug của `main@b1af810` cho các số "trước" của A5). Chỉ chạy khi chủ dự án đã nói máy đang rảnh (phone-availability); đầu mỗi lượt kiểm Os26Launcher đang ở trước hoặc bố cục khác bản sao lưu thì **dừng và hỏi**. Mọi số ghi rõ **debug**. Điều phối chạy mọi bước.

**Rủi ro riêng:** máy là launcher mặc định, nên lỗi sập-lặp lại khóa máy chủ dự án. Quy tắc: **ảnh 12000x3000 chỉ đặt vào `files/backdrop_image` khi bản đã cài là bản đã sửa Task 1.** Lệnh thoát hiểm nếu Home sập lặp (chỉ chạy được trên bản debug):
```
adb shell run-as gambi.global.os26launcher rm files/backdrop_image
adb shell am force-stop gambi.global.os26launcher
adb shell input keyevent KEYCODE_HOME
```

**Chuẩn bị** (Git Bash; `MSYS_NO_PATHCONV=1` cho đường dẫn thiết bị; thư mục sao lưu **ngoài repo**):
```
export MSYS_NO_PATHCONV=1
PKG=gambi.global.os26launcher
BK=/e/SourceTree/IosLauncher-backup/FX1          # ngoài repo, không bao giờ git add
mkdir -p $BK && cd $BK

# 1. Sao lưu CẢ databases/ files/ no_backup/ (bài học DG: phải cả files/, chứa ảnh nền đã chọn)
adb shell am force-stop $PKG
adb exec-out run-as $PKG tar cf - databases files no_backup > before.tar
mkdir -p before && tar xf before.tar -C before
( cd before && find . -type f | sort | xargs md5sum ) > before.md5
wc -l before.md5                                # ghi số file; phải >= 8 (audit: 8 file)
adb shell cmd uimode night                      # ghi lại: Night mode: yes (chủ dự án dùng chế độ tối)

# 2. Ảnh thử (ffmpeg có trong PATH; testsrc2 có chi tiết nên cỡ file thật)
ffmpeg -y -f lavfi -i testsrc2=size=12000x3000:rate=1 -frames:v 1 -q:v 5 fx1_pano.jpg
ffmpeg -y -f lavfi -i testsrc2=size=12000x3000:rate=1 -frames:v 1 fx1_pano.png    # PNG: nhập chậm, cho mục 5
ffmpeg -y -f lavfi -i testsrc2=size=3024x4032:rate=1 -frames:v 1 -q:v 3 fx1_portrait.jpg
ffmpeg -y -f lavfi -i testsrc2=size=4032x3024:rate=1 -frames:v 1 -q:v 3 fx1_land.jpg
ffmpeg -y -f lavfi -i smptebars=size=4032x3024:rate=1 -frames:v 1 -q:v 3 fx1_bars.jpg   # vạch màu dọc: kiểm căn kính
ffmpeg -y -f lavfi -i "testsrc2=size=600x1200:rate=1,format=rgba,pad=1080:2340:240:570:color=black@0" -frames:v 1 fx1_trim.png

# 3. Chép ảnh vào máy để (i) đặt thẳng vào files/ và (ii) chọn qua bộ chọn ảnh
adb push fx1_pano.jpg fx1_pano.png fx1_portrait.jpg fx1_land.jpg fx1_bars.jpg fx1_trim.png /sdcard/Pictures/
for f in fx1_pano.jpg fx1_pano.png fx1_portrait.jpg fx1_land.jpg fx1_bars.jpg fx1_trim.png; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/$f
done
```
Đặt một ảnh thẳng vào `files/backdrop_image` (không đi qua bộ chọn; dùng cho "load" và cho số bộ nhớ):
```
put() { adb exec-in run-as $PKG sh -c 'cat > files/backdrop_image' < "$1"; adb shell run-as $PKG ls -l files/backdrop_image; }
adb shell am force-stop $PKG; put fx1_land.jpg; adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```
(Nếu `exec-in ... cat` lỗi trên máy: `adb push` vào `/data/local/tmp` rồi `run-as $PKG cp /data/local/tmp/x files/backdrop_image`.)

Đo bộ nhớ (chờ Home yên ~10 giây sau khi mở, lấy lần đo thứ hai):
```
mem() { adb shell dumpsys meminfo $PKG | grep -E "Native Heap|Dalvik Heap|Java Heap|Graphics|TOTAL|Activities|Views"; }
```
Chụp màn hình (Git Bash, **không** dùng PowerShell cho nhị phân): `adb exec-out screencap -p > shot.png`. Độ sáng vùng giữa: `ffmpeg -v error -i shot.png -vf "crop=200:200:440:1000,signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 | grep YAVG`.

**Các mục kiểm theo thứ tự thao tác:**
- **0. Đường cơ sở `main`** (chỉ để lấy số "trước" của A5; cài debug `main@b1af810`): ảnh mặc định (xóa `files/backdrop_image` sau khi đã sao lưu): `mem`; rồi `put fx1_portrait.jpg` → `mem`; rồi `put fx1_land.jpg` → `mem`. Cùng bản này: chọn `fx1_bars.jpg` qua bộ chọn, chụp dock/pill/thư mục (thấy lệch vạch màu: mốc "trước" của căn kính). **Không** đặt `fx1_pano` ở bản này. Ghi các dòng số.
- **1. Cài bản nhánh (đã build Task 1-6).** Cùng ba ảnh, cùng lệnh `mem`: kỳ vọng Native Heap giảm ≈ 40 MB cho cả ảnh dọc và ngang (48,8 → 9,07 MB), Graphics giảm cùng bậc; ghi số thật vào plan (D2).
- **2. A1 (Task 1a):** `put fx1_pano.jpg`, mở Home: không sập; `adb logcat -d | grep -iE "too large|uploaded into a texture|OutOfMemory|FATAL"` rỗng; Home hiện phần giữa ảnh, nét; log `FX1` cho bitmap 1080x2100; `mem` ≈ +9 MB bitmap so với nền trống (dự báo).
- **3. Nhập qua bộ chọn (Task 1c, 1d, 2, 4a):** Cài đặt → Ảnh nền → chọn lần lượt `fx1_pano.jpg`, `fx1_land.jpg`, `fx1_bars.jpg`, `fx1_trim.png`, một ảnh chụp dọc từ camera (EXIF xoay). Mỗi lần: không sập, Home đổi, hộp thoại wallpaper → **"Không áp dụng"** (tuyệt đối không áp lên màn khóa/màn chính hệ thống của chủ dự án). Sau mỗi lần: `adb exec-out run-as $PKG cat files/backdrop_image > got.bin; ffprobe -v error -show_entries stream=width,height got.bin` so với dự báo (toàn cảnh ≈ 6000x1500, 4:3 giữ 4032x3024); PNG viền trong suốt phủ kín; ảnh camera đúng chiều; với `fx1_bars.jpg`: dock/pill/thư mục hiển thị đúng màu vạch sau chúng (Review Focus 11).
- **4. B2 (Task 3):** `adb shell run-as $PKG rm files/backdrop_image; force-stop; mở Home`; `YAVG` ở chế độ tối (ghi, vd T). `adb shell cmd uimode night no`; **không chạm máy**, chờ 3 giây, chụp: `YAVG` phải cao hơn T rõ rệt (ảnh Blue sáng). `cmd uimode night yes`, chờ 3 giây, chụp: về ≈ T. Trước khi sửa B2: ảnh vẫn tối ở bước `no` (báo cáo). Khi cần log: `adb logcat -s FX1` (log tạm ở Task 3 Step 1 chỉ có ở bản kiểm nguyên nhân).
- **5. C4 Back sớm (Task 4b):** đưa Home về ảnh `fx1_land.jpg` đã có, rồi trong Cài đặt → Ảnh nền chọn `fx1_pano.png` (nhập vài giây), và **ngay khi bộ chọn đóng** gửi `adb shell input keyevent KEYCODE_BACK` hai lần để ra Home. **Không chạm máy** ~15 giây, chụp: Home phải là ảnh `fx1_pano.png`. (Điều phối có thể thử trước trên bản `main` để lấy số "trước": Home giữ `fx1_land`.) Sau đó chọn hai ảnh liên tiếp cách nhau vài giây (4c): Home cuối cùng là ảnh sau.
- **6. C5 (Task 5):** Cài đặt → Mức kính → mức tương ứng FORCE_LOW; Home dựng lại. `adb shell 'ps -T -p $(pidof gambi.global.os26launcher)' | grep -c GlassBlur`: kỳ vọng 1 (bản `main`: 2, và +1 mỗi lần `cmd uimode night no/yes` làm Home dựng lại). Chụp màn hình dock so với bản `main` cùng ảnh nền. **Trả Mức kính về FORCE_HIGH.**
- **7. C3 (Task 6):** mở Home, để một thư mục ở trang hiện tại (đã có sẵn trong bố cục của chủ dự án nếu có; nếu không, kiểm bằng pill/dock). Ra app khác (`am start -a android.settings.SETTINGS`), chờ 5 giây, `mem`; `adb shell am send-trim-memory $PKG COMPLETE`, chờ 3 giây, `mem` (kỳ vọng giảm vài trăm KB tới ~1 MB; Graphics có thể về 0 do hệ thống). `adb shell input keyevent KEYCODE_HOME`: chụp ngay (~300 ms) và sau 2,5 giây: vùng kính hồi lại, ảnh nền chính không đen. Tùy chọn (Task 6 Step 6 iii, ảnh A → B + GC): đổi ảnh nền từ `fx1_portrait.jpg` sang `fx1_land.jpg`, rồi `adb shell run-as $PKG kill -10 $(adb shell pidof $PKG)` và `mem`.
- **Hồi quy:** các dòng Cài đặt khác; đổi mức kính (Home dựng lại không sập); hộp thoại wallpaper vẫn hiện sau khi chọn ảnh (hồi quy M8a Task 5); ảnh mặc định ở cả hai chế độ sáng/tối; kéo-thả icon, mở thư mục, vuốt sang App Library vẫn bình thường trên nền mới; `adb logcat -d -b crash` rỗng suốt lượt kiểm.

**Khôi phục (bắt buộc, cuối mỗi lượt):**
```
adb shell am force-stop $PKG
adb shell cmd uimode night yes                   # chủ dự án dùng chế độ tối
adb exec-in run-as $PKG tar xf - < $BK/before.tar   # khôi phục databases/ files/ no_backup/
adb exec-out run-as $PKG tar cf - databases files no_backup > $BK/after.tar
mkdir -p $BK/after && tar xf $BK/after.tar -C $BK/after
( cd $BK/after && find . -type f | sort | xargs md5sum ) > $BK/after.md5
diff $BK/before.md5 $BK/after.md5                # phải rỗng; nếu khác: làm lại bước khôi phục (bài học: exec-in từng cụt, exit 0)
adb shell rm /sdcard/Pictures/fx1_*              # xóa ảnh thử khỏi máy rồi quét lại media
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures
```
Cuối cùng: cài lại bản debug mà chủ dự án đang dùng nếu khác bản kiểm, xác nhận Home mở, ảnh nền đã chọn của chủ dự án trở lại, chế độ tối bật, mức kính FORCE_HIGH.

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Vsmart Live 4, debug, 2026-11-11 13:40-13:46 (điều phối), `fix/fx1-wallpaper-safety@f22b3a9` (APK `FX1.apk`; sau đó chỉ gỡ log `FX1` và sửa tài liệu), Os26 là launcher mặc định, kính Ép cao, logcat không có FATAL suốt lượt kiểm:
- **A1:** `fx1_pano.jpg` 12000x3000 đặt thẳng vào `files/backdrop_image`: không sập, `1080x2340 bytes=10108800 ms=119`, Native 38,9 MB, Graphics 51,8 MB.
- **A5:** (số Task 1, bản `b1af810` so với FX1) ảnh dọc/ngang Native 72 → 35-37 MB, Graphics 91 → 52 MB.
- **B2:** ảnh mặc định (đã xóa `backdrop_image`), độ sáng dải giữa: tối 122,2 → `cmd uimode night no` 134,9 → `yes` 122,2; log `reloadBackdropIfChanged v=-1 shown=-2 arg=0x0 used=1080x2340` (đường dùng cỡ nhớ đã chạy).
- **C5:** mức kính FORCE_LOW: 1 luồng `GlassBlur`; sau hai lần Home dựng lại (`night no/yes`) vẫn 1.
- **C3:** launcher ở nền, `send-trim-memory COMPLETE`: Native 28,3 → 22,9 MB, Graphics 13,6 → 0 MB; quay về Home sau 0,3 s và 2,5 s: kính và ảnh nền có mặt, không sập.
- **Căn kính:** `fx1_bars.jpg` (Task 1): kính ô thư mục/widget/dock khớp vạch màu phía sau.
- **Chưa chạy trên máy (chủ dự án kiểm khi duyệt PR, vì bộ chọn ảnh hiện thư viện ảnh riêng của chủ dự án):** nhập qua bộ chọn (chép nguyên JPEG D9, EXIF xoay, PNG cắt viền), C4 Back sớm, hai lần chọn liên tiếp. Danh sách ở mục "Chủ dự án kiểm khi duyệt PR" trong knowledgement.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | Plan | Viết plan, kiểm lại từng khẳng định của báo cáo với code `b1af810` | chưa commit | Chờ điều phối rà |
| 2026-11-11 | Plan | Sửa plan theo góp ý vòng 1 của điều phối: bitmap Home = vùng giữa đúng tỉ lệ màn giải mã bằng `BitmapRegionDecoder` (file lưu không cắt, có trần pixel), bỏ trần cạnh 4096 và bỏ Task 7, thêm bảng người dùng của bitmap, cập nhật bảng số A5 (≈ 9 MB), chốt Q2/Q3/Q4, thống nhất lời "bắt buộc kiểm tay" là phép đo do điều phối quyết, thêm ghi chú IconCache cho FX5 | chưa commit | Plan vòng 1/5 |
| 2026-11-11 | 1 | Code `BitmapScaler`; `PickedImageSource.load` giải mã vùng giữa bằng `BitmapRegionDecoder` (đường lui: giải mã bị chặn pixel rồi cắt); `import` chép nguyên byte JPEG khi EXIF bình thường và ≤ 30 MB (D9), còn lại giải mã có trần, thu trước xoay sau; bắt `OutOfMemoryError` ở repository (load, load mặc định, import, apply wallpaper) và `Throwable` ở `BlurredBackdropCache`; log tạm `FX1` (kích thước/thời gian load và import). Build xanh | `b98b59a` | Chờ điều phối đo (Task 1 bắt buộc kiểm tay). Log `FX1` còn trong `BackdropRepositoryImpl`, sẽ gỡ sau phép đo |
| 2026-11-11 | 1 (đo) | Điều phối đo Live 4 (debug, 12:05-12:09; Os26 mở tay vì launcher mặc định hiện là launcher gốc nên vùng Home là 1080x2340). **Đạt.** Ảnh chủ dự án: Native 27,0 MB, Graphics 43,8 MB, PSS 139 MB. Bản `b1af810`: `fx1_portrait` (3024x4032) Native 72,4 / Graphics 90,9 / PSS 230 MB; `fx1_land` (4032x3024) 72,3 / 90,1 / 229 MB. Bản FX1-task1: portrait Native 37,2 / Graphics 51,8 / PSS 170 MB, log `1080x2340 bytes=10108800 ms=160`; land 35,3 / 51,8 / 142 MB, `1080x2339 ms=213`; `fx1_pano.jpg` 12000x3000: **không sập**, tiến trình sống, `1080x2339 ms=133`, logcat không có FATAL/OOM/too large; `fx1_bars.jpg` 1600x1600: `738x1600 bytes=4723200 ms=33`, kính ô thư mục/widget/dock khớp vạch màu phía sau (ảnh chụp). Chưa chạy: nhập qua bộ chọn (đường chép nguyên JPEG D9, EXIF xoay, PNG cắt viền) → bài kiểm cuối. Giữ `MAX_DECODE_PIXELS` 25 MP, `MAX_SAVED_PIXELS` 16 MP, trần chép 30 MB. `1080x2339` do làm tròn: `outputSize` trả đúng cỡ yêu cầu khi lệch ≤ 1 px | `b98b59a` | Tick Task 1 theo điều phối. Log `FX1` giữ tới Task đóng |
| 2026-11-11 | 2 | Tách `ExifReader`, `TransparentTrimmer` vào `core/bitmap/`; `DefaultBackdropSource` cắt vùng giữa theo tỉ lệ vùng Home bằng `BitmapScaler` (cắt trong bộ nhớ); `outputSize` trả đúng cỡ yêu cầu khi lệch ≤ 1 px. Build xanh | `7420547`, `bd26c6a` | Chờ điều phối review; kịch bản kiểm Task 2 gộp vào bài kiểm cuối
| 2026-11-11 | 3 | B2: `HomeViewModel` nhớ cỡ màn cuối (`lastScreenW/H`), `reloadBackdropIfChanged` dùng cỡ nhớ khi `width <= 0`; một `backdropJob` (hủy lần nạp trước). Log tạm `FX1 reloadBackdropIfChanged v=… shown=… arg=… used=…` thay cho Step 1 (xác nhận nguyên nhân: kỳ vọng `arg=0x0 used=1080x…` ở onResume đầu sau `cmd uimode night`). Build xanh | `e0ec220` | Chờ review; kịch bản gộp bài kiểm cuối
| 2026-11-11 | 4 | C4: `save` kiểm `compress`/`renameTo` (dọn `.tmp`, giữ file cũ); `BackdropRepository.backdropVersions` (StateFlow) phát trong khối `NonCancellable` của `importBackdrop`; `HomeViewModel` thu flow (`init` đặt sau các trường) rồi `reloadBackdropIfChanged(0, 0)`. Build xanh | `905f902` | Chờ review; Back sớm khi nhập `fx1_pano.png` kiểm ở bài kiểm cuối
| 2026-11-11 | 5 | C5: `DockController` dùng `glassEnv.blurCache`, bỏ `dockGlassCache`. Build xanh | `a2d8092` | Chờ review; đếm luồng `GlassBlur` ở bài kiểm cuối
| 2026-11-11 | 6 | C3: `WeakReference` cho nguồn trong `BlurredBackdropCache` và drawable ảnh nền trong `BackdropBlurDrawable`; `BlurredBackdropCache.trim()` + `SmallListener.onSmallDropped()` (cả `BackdropBlurDrawable` và `StaticBlurSurface`, cái sau đăng ký qua `setBackdrop`); `GlassEnvironment` đăng ký `ComponentCallbacks2`, chỉ trim ở `TRIM_MEMORY_COMPLETE`; không bỏ ảnh nền chính. Build xanh | `ac02111` | Chờ review; `send-trim-memory COMPLETE` ở bài kiểm cuối
| 2026-11-11 | 6 (sửa) | Review vòng 1: (1) `BackdropBlurDrawable.prepareShader` reset bản nhỏ/shader khi drawable ảnh nền là null (ref yếu đã bị GC thì `null !== null` không đủ); (2) `importBackdrop` tuần tự hóa bằng `Mutex` (hai lần nhập chồng nhau cùng dùng `.tmp`; ảnh chọn sau thắng); (3) giữ `MutableStateFlow(backdropVersion())` khởi tạo ngay (hai lệnh stat file khi dựng Hilt, cùng chi phí `backdropVersion()` mà `HomeViewModel` đã gọi trên main), không đổi. Tick Task 2-6 (đã qua review). Build xanh | `9e50cc3`, `f22b3a9` | Vòng 1/5 cho Task 6 và Task 4; chờ bài kiểm cuối
| 2026-11-11 | Đóng | Kiểm máy cuối của điều phối (Live 4, debug, 13:40-13:46, `f22b3a9`): A1, A5, B2, C5, C3, căn kính đạt, không FATAL; nhập qua bộ chọn ảnh để chủ dự án kiểm khi duyệt PR. Gỡ log `FX1`; điền bảng bằng chứng, kết quả kiểm chứng; viết knowledgement; build xanh | `315f1de` | Chờ review PR

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | `BitmapScaler` đúng vùng cắt, trần, recycle | `fx1_pano` 12000x3000 → `1080x2340 bytes=10108800`; `fx1_bars` 1600x1600 → `738x1600` (tỉ lệ bằng vùng Home); `outputSize` snap ≤ 1 px (Task 1 follow-up `7420547`); recycle và trần: review mã (điều phối + reviewer độc lập) | Đạt |
| 2 | `load` chữa ảnh cũ bằng giải mã vùng, có đường lui | `fx1_pano` đặt thẳng vào `files/`: không sập, `ms=119`, Native 38,9 MB; đường lui khi `BitmapRegionDecoder` lỗi | Đạt; đường lui chỉ code bảo vệ |
| 3 | Không còn OOM thoát ra | Không ép được OOM an toàn trên máy chủ dự án | Chỉ code bảo vệ (review mã) |
| 4 | `save` kiểm kết quả | Không tạo được tình huống đầy bộ nhớ an toàn | Chỉ code bảo vệ (review mã) |
| 5 | Version phát trong `NonCancellable` | Cần chọn ảnh qua bộ chọn (thư viện riêng của chủ dự án) | Chủ dự án kiểm khi duyệt PR |
| 6 | `backdropJob`, thứ tự `init`, `Mutex` nhập | `init` đặt sau các trường (review mã); `backdropJob` chạy ở B2; hai lần chọn liên tiếp cần bộ chọn | Đạt (B2, review mã); hai lần chọn: chủ dự án kiểm |
| 7 | B2 dùng cỡ nhớ | Độ sáng dải giữa 122,2 → 134,9 → 122,2; log `arg=0x0 used=1080x2340` | Đạt |
| 8 | Tham chiếu yếu, `trim`, reset khi ảnh nền gỡ | `send-trim-memory COMPLETE`: Native 28,3 → 22,9 MB, Graphics 13,6 → 0 MB, quay về 0,3 s/2,5 s kính và ảnh nền có mặt, không sập; tham chiếu yếu và reset ảnh gỡ: review mã | Đạt (trim); tham chiếu yếu chỉ code bảo vệ |
| 9 | Dock dùng cache chung | FORCE_LOW: 1 luồng `GlassBlur`, sau hai lần Home dựng lại vẫn 1 | Đạt |
| 10 | Ảnh mặc định cắt theo tỉ lệ, hình không đổi | Ảnh mặc định hiển thị đúng ở cả sáng và tối (B2, độ sáng); chưa so ảnh chụp từng điểm với `main` | Đạt về chức năng; so khung hình chi tiết: chủ dự án nhìn khi duyệt |
| 11 | Căn kính với ảnh khác tỉ lệ màn | `fx1_bars.jpg`: kính ô thư mục/widget/dock khớp vạch màu phía sau (ảnh chụp) | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: không đổi (sửa lỗi).
- `project_structure.md`: mục 3 `core/bitmap/`: `BitmapScaler.kt`, `ExifReader.kt`, `TransparentTrimmer.kt` từ `[di trú]` thành `[xong FX1]`; sửa dòng "Phần giải mã chưa tách" ở bảng "Vai trò từng folder" (đã tách); `BackdropRepository` thêm `backdropVersions`; ghi hằng `MAX_SAVED_PIXELS` (16 MP), `MAX_DECODE_PIXELS` (25 MP), ngưỡng chép nguyên JPEG 30 MB (D9) và quy ước "file lưu không cắt, bitmap Home là vùng giữa theo tỉ lệ vùng Home".
- `room-schema.md`: không đổi.
- `AGENTS.md` / `lessons/`: xem "Bài học cho AGENTS.md" trong knowledgement. Đề xuất sẵn: (`settings-and-data`) "Dòng M8a Task 5: 'đường chọn ảnh ngay tại Home cũng cập nhật biến này' đã cũ, đường đó không còn; Home biết ảnh mới qua `backdropVersions`"; (`glass`) "kính tại chỗ kéo giãn bitmap vừa view nên bitmap nền phải cùng tỉ lệ view (cắt theo `centerCrop` khi nạp); bộ đệm blur chỉ ~0,57 MB, đừng kỳ vọng nhiều khi trim"; (`startup-performance` hoặc `home-pager-animation`) "launcher mặc định: đặt ảnh quá cỡ vào `files/` chỉ trên bản đã sửa, luôn có lệnh thoát hiểm `rm files/backdrop_image`"; (`icons-and-app-list`) "`BitmapRegionDecoder` giải mã vùng của ảnh lớn mà không cấp phát cả ảnh".
