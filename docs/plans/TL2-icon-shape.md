| 5 | undefined| Chủ dự án so cỡ ở mục 3: hộp không đổi, muốn icon to hơn → `ICON_SCALE` 0.9 (D10) | Đạt; điều chỉnh theo Q3 || 2 | undefined| Chủ dự án chạy bài kiểm cuối trên Live 4 `519153b` (mục 4–8: thư mục, Library, tìm kiếm, đã ẩn, kéo, nhấn giữ, edit mode): mọi mục đạt; đọc code: mọi nạp icon đi qua `AppRepository.icon` | Đạt (bài kiểm cuối 2026-11-03) || 1 | undefined| Điều phối phóng 4× (nearest-neighbour) ảnh chụp màn hình Live 4 ở `519153b`: góc mịn, không viền sáng/tối; thêm ảnh chụp lưới ở `c58798b` | Đạt (bài kiểm cuối 2026-11-03) || 6 | undefined| Đọc code: `Paint`, `Path`, `BitmapShader` tạo mới mỗi lần `render`, không có trạng thái chung; bitmap tạm recycle trong `finally`. Trên Live 4: ba lượt mở nguội (nạp song song trên `Dispatchers.IO`) không lỗi, không crash | Đạt || 5 | undefined| Hộp icon không đổi (`GridMetrics` không sửa, bitmap vẫn `iconSize` vuông); vùng nhìn thấy adaptive 72dp phóng 1,5 lần lấp đầy hộp. So bằng mắt: chưa | Chờ bài kiểm cuối (mục 3, Q3) || 4 | undefined| Code: nền null → tô trắng, lớp trước null → bỏ qua, legacy rỗng → chỉ tấm nền, icon kín viền → không tấm nền. Trên Live 4: 3 lượt mở nguội không lỗi, một icon legacy đi nhánh tấm nền (`luma=0.35`); các ca còn lại chưa gặp trên máy | Code bảo vệ; chưa gặp ca biên trên máy || 3 | undefined| Lập luận (điều phối xác nhận): bounds nằm trên instance drawable, không phải `ConstantState`, và mỗi lần nạp gọi lại `loadIconDrawable`. Chưa thử hai cỡ liên tiếp trên máy | Đạt theo lập luận; chờ bài kiểm cuối (mục 2) || 2 | undefined| Đọc code: mọi nạp icon app đi qua `AppRepository.icon` → `IconLoader.load` (HomeViewModel, AppLibraryViewModel, SearchViewModel); ảnh kéo và icon nổi là ảnh chụp của `IconCellView`. Trên máy: chưa kiểm từng bề mặt | Đọc code đạt; chờ bài kiểm cuối (mục 4–8) || 1 | undefined| Điều phối xem ảnh chụp lưới trên Live 4 (`c58798b`): góc bo hiện rõ. Cạnh phóng to: chưa kiểm | Một phần; còn mục 1 bài kiểm cuối |# TL2 — Bo góc icon kiểu iOS (TT#12, một phần) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `fix/tl2-icon-shape` · **Worktree:** `E:\SourceTree\IosLauncher-wt\TL2-icon-shape` · **PR:** #1 · **Bắt đầu:** 2026-11-03 · **Plan duyệt:** 2026-11-03
**Trạng thái:** Đã kiểm tay (chủ dự án, 2026-11-03), chờ điều phối kiểm lại cỡ icon + mục 8 rồi merge
**Khóa:** không

**Goal:** Mọi icon app trong launcher (lưới, dock, ô thư mục, panel thư mục, App Library, tìm kiếm, màn "Ứng dụng đã ẩn", icon đang kéo, icon nổi của menu nhấn giữ) hiện đúng một hình bo góc liên tục kiểu iOS, bất kể app đưa vào icon tròn, giọt nước, vuông hay PNG trong suốt. Góp ý gốc của tech lead (2026-11-03): "icon chưa bo góc".

**Architecture:** Hình dạng được **nướng vào bitmap ngay ở nguồn**: `IconLoader.load` giao việc vẽ cho một `IconRenderer` mới gồm hai giai đoạn tách bạch: (1) `flatten` — biến mọi `Drawable` (adaptive hoặc legacy) thành một ảnh vuông đầy kín, (2) `mask` — cắt ảnh đó bằng `IconShape` (đường cong bo góc liên tục, chống răng cưa). Mọi bề mặt hiện icon đã đi qua `AppRepository.icon(key, sizePx)` nên không bề mặt nào phải sửa. Giữa hai giai đoạn là chỗ G2b (tô màu, 3D) sẽ cắm vào sau này; TL2 không làm G2b và không thêm tùy chọn Cài đặt.

**Tech Stack:** `android.graphics` thuần (`Path`, `Canvas`, `Paint` + `PorterDuffXfermode`), `AdaptiveIconDrawable` (API 26, trùng `minSdk = 26`). Không thêm dependency, không đổi schema Room, không thêm khóa DataStore.

**Spec:** TT#12 (phần "hình dạng", chưa gồm tô màu/3D) và TT#20 trong `Launcher-Features.md`; `project_structure.md` dòng `IconLoader.kt` ("vẽ sẵn kiểu icon (TT#12) ra bitmap") — nghĩa là thiết kế gốc của dự án đã định nướng kiểu icon vào bitmap ở loader; TL2 làm đúng hướng đó.

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (đọc code ngày 2026-11-03, nhánh `fix/tl2-icon-shape` từ `origin/main`)

1. **Hiện không có bước tạo hình nào.** `IconLoader.load` lấy `LauncherAppsDataSource.loadIconDrawable(key)` (= `LauncherActivityInfo.getIcon(0)`), đặt `setBounds(0, 0, sizePx, sizePx)`, vẽ lên bitmap ARGB_8888 rồi `IconCache.put`. Với `AdaptiveIconDrawable`, `draw()` tự áp **mặt nạ của OEM** (tròn, giọt nước, bo góc...), nên hình icon phụ thuộc ROM; icon legacy (PNG trong suốt) thì vẽ nguyên hình dạng của nó. Không có chỗ nào bo góc, đúng như tech lead thấy.
2. **Có đúng một đường đi cho icon app.** `AppRepository.icon` (`AppRepositoryImpl.icon` → `IconLoader.load`) được gọi qua `HomeViewModel.loadIcon`, `AppLibraryViewModel.loadIcon`, `SearchViewModel.loadIcon`. Người tiêu thụ: `IconGridView` (ô app, ô thư mục), `DockView`, `FolderPanelView` (lưới con), `CategoryGroupPanelView`, `CategoryPageView` (4 icon nhỏ của ô nhóm), `AppLibraryAdapter` (màn đã ẩn, 48dp), `SearchResultAdapter` (40dp). Ảnh kéo (`DragLayer`) và icon nổi của menu nhấn giữ là **ảnh chụp lại từ `IconCellView`** nên thừa hưởng hình dạng. Không có chỗ nào tự gọi `getIcon` ngoài `IconLoader`. Icon shortcut trong menu nhấn giữ (`ShortcutRepositoryImpl.getShortcuts`, 24dp) là loại khác, ngoài phạm vi.
3. **Bitmap là nguồn chung, nhưng view đang vẽ nó thẳng:** `IconCellView.onDraw` gọi `canvas.drawBitmap(bmp, iconSrc, iconDst, iconPaint)`; ô thư mục vẽ 3×3 icon con bằng cùng bitmap thu nhỏ (nạp ở `c.iconSize`, vẽ nhỏ lại khoảng 1/4). Các `ImageView` trong adapter đặt bitmap đúng kích thước view (không scale).
4. **Cache chỉ trong RAM** (`IconCache`, `LruCache` `maxMemory/8`: 48 MB trên Oppo A3x, 64 MB trên Live 4; đo M2). Không có cache đĩa, nên đổi hình dạng không cần xóa dữ liệu cũ: process mới = cache mới. Khóa hiện là `package/class#userSerial@sizePx`; `evict(packageName)` quét theo tiền tố `package/` và vẫn đúng.
5. **Cold start.** AGENTS.md ghi: thời gian nạp danh sách 344 app trên Oppo ≈ 7,9 giây (đo M2), chưa tách nhãn/sắp xếp; sau tối ưu nhãn ước 1,8–6,0 giây, chưa đo. Đó là **nhãn** (`loadLabel`); icon nạp **lười**, chỉ cho ô đang hiện (≈ 24 ô lưới + 4 ô dock + vài icon con thư mục + thứ mở thêm), không phải 344 icon một lượt. Vì vậy chi phí thêm của việc tạo hình tính theo số icon hiển thị, không theo 344. Nhưng đây là tính toán trên giấy: Task 4 đo lại.
6. **Cỡ icon.** `GridMetrics.measure`: `iconSize` = min(`ICON_MAX_DP`=96, bề rộng ô, chiều cao còn lại) × `ICON_SCALE` (0,75, "CHỈNH TAY", demo polish 2026-10-30 làm icon to hơn trước), làm tròn xuống bội của 4dp. TL2 **không đổi** con số này; bitmap vẫn lấp đầy đúng hộp `iconSize × iconSize`.
7. **Ràng buộc kiến trúc:** `ui → domain ← data`; `ui` không import `data`. Hình dạng cần cho `data` (loader) ngay bây giờ và có thể cần cho `ui` sau này (vd nền ô thư mục, hiện là `drawRoundRect` với `FOLDER_CORNER_RATIO = 0.225` trong `IconCellView.drawFolder`), nên `IconShape` đặt ở `core/` (cả hai tầng được import `core`), không đặt trong `data/`.

### Các phương án đã xét

**A. Nơi tạo hình**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A1. Nướng vào bitmap ở loader** | `IconRenderer` vẽ + cắt trong `IconLoader.load`, cache lưu bitmap đã cắt | Một chỗ duy nhất; mọi bề mặt (7 chỗ nạp, 2 ảnh chụp) tự có; không sửa `HomeActivity`/`activity_home.xml`/panel nên không đụng TL1 và GL; G2b (tô màu, 3D) vốn **buộc** phải xử lý từng điểm ảnh ở nguồn nên dùng chung được; khớp ý định ghi sẵn ở `project_structure.md` | Chi phí CPU mỗi icon nạp tăng (đo ở Task 4); bitmap có góc trong suốt nên mọi chỗ vẽ lên nền cần chấp nhận alpha (đã đúng: ARGB_8888) | **Chọn** |
| A2. Cắt trong từng view | `clipPath`/`ViewOutlineProvider`/`Paint` shader trong `IconCellView`, `ImageView` của 3 adapter, `CategoryPageView`... | Không tốn CPU lúc nạp | Phải sửa ≥ 6 file view, trong đó panel thư mục/nhóm thuộc vùng GL, `IconCellView` hay bị TL1/GL chạm; dễ sót bề mặt mới (đúng loại lỗi AGENTS.md ghi: tái dùng view giữa các loại item); `clipPath` lúc vẽ lên ảnh thu nhỏ của ô thư mục phải làm lại cho từng icon con; G2b vẫn phải làm xử lý điểm ảnh ở nguồn nên không tránh được việc đụng loader | Bỏ |

**B. Với icon adaptive**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| B1. Dựa mặt nạ OEM | Vẽ `AdaptiveIconDrawable` như hiện nay rồi cắt thêm bo góc | Không đụng lớp nền/lớp trước | OEM có thể là hình tròn hoặc giọt nước: cắt thêm hình bo góc **không thêm được gì** (tròn nằm gọn trong hình bo góc) → hình dạng không đồng nhất, đúng thứ iOS không có; hình phụ thuộc ROM | Bỏ |
| **B2. Tự vẽ hai lớp vào đường dẫn của mình** | Lấy `getBackground()` và `getForeground()` của `AdaptiveIconDrawable`, tự `setBounds` rồi vẽ tuần tự lên bitmap, sau đó cắt bằng `IconShape` | Hình đồng nhất bất kể ROM; hai lớp vốn là ảnh đầy kín 108dp, vùng nhìn thấy là 72dp ở giữa (tài liệu Android về adaptive icon), nên chỉ cần phóng lớp lên 108/72 = 1,5 lần quanh tâm là vùng nhìn thấy lấp đầy hộp icon → **cùng cỡ nhìn thấy như bây giờ** | Phụ thuộc `getBackground()`/`getForeground()` (có thể null); đặt `bounds` trực tiếp lên drawable con (phải kiểm là không làm bẩn bản dùng chung; Task 2 kiểm) | **Chọn** |

**C. Hình dạng**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| C1. Bo góc tròn thường | `drawRoundRect` bán kính ≈ 22,4% cạnh | Đơn giản nhất; chính là thứ ô thư mục đang dùng | Chỗ nối giữa cạnh thẳng và cung tròn có độ cong nhảy bậc nên nhìn "cứng" hơn góc iOS | Dùng làm **mốc so sánh** (tham số `smoothing = 0`) |
| C2. Siêu elip `|x|^n + |y|^n = 1` (n ≈ 5) | Công thức một dòng | Không có cạnh thẳng nên nhìn phồng, khác iOS (suy luận chưa kiểm chứng, Task 1 so bằng mắt) | Bỏ |
| **C3. Bo góc "liên tục" dựng bằng Bézier** | `Path` gồm cạnh thẳng + mỗi góc là hai đường cong Bézier bậc 3 nối với một cung tròn ngắn ở giữa; hai tham số: `cornerRatio` (bán kính/cạnh) và `smoothing` (0 = bo tròn thường, 1 = trơn tối đa) | Đúng bản chất "continuous corner" của iOS (độ cong tăng dần, không nhảy bậc); một hàm cho cả C1 (smoothing = 0) lẫn C3 nên A/B trên máy rất rẻ; `Path` dựng một lần mỗi icon, chi phí không đáng kể | Cần dựng đúng công thức (Task 1 đối chiếu bằng cách vẽ chồng với C1 ở 512px) | **Chọn** |

Giá trị khởi điểm: `cornerRatio = 0,2237` (tỉ lệ thường được trích từ lưới icon iOS 1024px, bán kính ≈ 229px; **chưa đối chiếu với tài liệu Apple**, nên đặt thành hằng số "CHỈNH TAY" như `ICON_SCALE`), `smoothing = 0,6` (mức mặc định của chức năng corner smoothing trong Figma; agent mở lại bài viết gốc của Figma về corner smoothing và ghi link vào Nhật ký trước khi viết công thức, plan này chưa mở lại nguồn). Cả hai là hằng số chỉnh tay. Task 1 kiểm công thức Bézier bằng tính toán (không cần chủ dự án); việc **nhìn** và chọn `smoothing` 0 hay 0,6 là mục quyết định trong bài kiểm cuối của cả tính năng.

**D. Kỹ thuật cắt**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **D1. Vẽ nội dung vào bitmap tạm, rồi tô `IconShape.path` lên bitmap kết quả bằng `BitmapShader`** | `content` = bitmap tạm `sizePx²` chứa ảnh kín đã `flatten`; `out` = bitmap kết quả (trong suốt khi vừa tạo); `Paint(ANTI_ALIAS_FLAG)` với `shader = BitmapShader(content, CLAMP, CLAMP)`; `canvas(out).drawPath(path, paint)`; `content.recycle()` ngay | **Đúng với góc:** `out` chỉ có điểm ảnh bên trong hình, mọi điểm ngoài hình (4 góc) giữ nguyên alpha = 0 vì không có gì được vẽ lên đó; cạnh mịn nhờ độ phủ chống răng cưa của `drawPath` | Cần thêm một bitmap tạm cùng cỡ mỗi lần render (≈ 83 KB ở 144px, ARGB_8888; sống trong một lần gọi, thu hồi ngay, không vào cache); thêm một lượt raster | **Chọn** (mặc định) |
| D1'. Vẽ nội dung lên `out`, rồi xóa phần ngoài hình bằng `path.fillType = INVERSE_WINDING` + `PorterDuff.Mode.CLEAR` (hoặc `DST_OUT`) | Một bitmap, thêm một lượt vẽ | Không cần bitmap thứ hai | Đúng về lý thuyết nhưng việc `CLEAR`/`DST_OUT` giữ chống răng cưa ở cạnh trên Canvas phần mềm **tôi chưa kiểm chứng**; sai thì để lại viền cứng hoặc viền đục. Chỉ rẻ hơn về bộ nhớ tạm | Bỏ (xét lại nếu bitmap tạm gây áp lực GC ở Task 4) |
| ~~D1''. `drawPath` với Paint `DST_IN` lên bitmap đã có nội dung~~ | | | **Sai, đã loại ở vòng rà 1:** `drawPath` chỉ chạm các điểm ảnh nằm trong hình học của path; điểm ngoài path (4 góc) không bị chạm nên vẫn đục — hình chỉ "trông" như bo góc ở mép chứ góc vẫn có màu | Bỏ |
| D2. `canvas.clipPath(path)` trước khi vẽ | Cắt cứng trước khi vẽ nội dung | Một bước, không bitmap thứ hai | Cạnh clip trên Canvas phần mềm (bitmap) có nguy cơ không chống răng cưa (kiến thức Android phổ biến, **chưa kiểm chứng trên máy**) | Bỏ; chỉ xét lại nếu D1 lỗi |
| D3. Mặt nạ dựng sẵn mỗi cỡ (`ALPHA_8`) phủ **toàn hình chữ nhật**, rồi blit `DST_IN` lên `out` | Cache mặt nạ theo `sizePx` (cỡ dùng trong app chỉ vài loại: lưới, 56/48/40/32dp...), `canvas.drawBitmap(mask, 0, 0, paintDstIn)` | **Đúng với góc** vì bitmap mặt nạ phủ cả hình chữ nhật (góc có alpha 0 trong chính mặt nạ, `DST_IN` nhân nên góc về 0); không cần bitmap tạm mỗi lần; rẻ hơn dựng `Path` + raster mỗi icon | Thêm một cache nhỏ (≈ 20 KB ở 144px, ALPHA_8, một bản mỗi cỡ) | **Dự phòng**: chỉ làm nếu Task 4 cho thấy D1 vượt ngưỡng |

**Vì sao D1 mà không phải D1':** D1 là mẫu bo góc ảnh phổ biến và đúng theo cấu trúc (kết quả chỉ chứa điểm ảnh trong hình), nên không phụ thuộc hành vi của chế độ hòa trộn ở cạnh; cái giá là một bitmap tạm sống trong một lần gọi. Đánh đổi bộ nhớ/cấp phát: mỗi lần `render` cấp thêm 1 bitmap `sizePx²` (tạm), nạp trên nhiều coroutine IO song song nên đỉnh tạm thời ≈ (số luồng IO đang chạy) × ≈ 83 KB, so với `IconCache` 48–64 MB là không đáng kể (tính toán, không đo); Task 4 theo dõi GC. Nếu không chịu được, đi D3 (không bitmap tạm).

**E. Icon legacy (PNG không phải adaptive, kể cả `BitmapDrawable`, `VectorDrawable`, `LayerDrawable`)**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| E1. Chỉ cắt hình bo góc | Vẽ nguyên rồi cắt | Đơn giản | Icon trong suốt (logo trên nền trong) nằm lơ lửng với góc trống; kích thước nhìn thấy không đều | Bỏ |
| **E2. Tấm nền + co cho vừa** | Đo ảnh mẫu 64×64: nếu **kín viền** (tự mang nền, đã lấp đầy) → vẽ kín hộp, không tấm nền; ngược lại → tô tấm nền **trắng** (đổi sang xám đậm `#FF2C2C2E` nếu độ sáng trung bình phần có hình > 0,85, tránh logo trắng trên nền trắng), co phần hình sao cho cạnh dài của hộp bao điểm ảnh đục bằng `0,70 × sizePx`, đặt giữa | Mọi icon có cùng "dấu chân" hình bo góc và cỡ gần đều; chi phí 1 bitmap mẫu 16 KB tạm thời | Ba ngưỡng cảm tính (kín viền ≥ 90% viền đục, 0,70, 0,85) cần chỉnh bằng mắt; màu tấm nền là lựa chọn thẩm mỹ của chủ dự án (xem Câu hỏi mở) | **Chọn** |
| E3. Tấm nền màu chủ đạo (lấy từ icon) | Tính màu trung bình/chủ đạo rồi làm gradient | Đẹp hơn trên icon một màu | Thêm thuật toán màu, cùng chi phí quét điểm ảnh; dễ ra màu bùn với icon nhiều màu | Để sau (có thể đặt cạnh G2b) |

### Vì sao chọn phương án này

- **A1 + B2 + C3 + D1 (bitmap tạm + `BitmapShader`) + E2** vì: (i) chỉ có một đường nạp icon (mục "Vấn đề" 2), nên nướng ở nguồn là cách duy nhất không đụng `HomeActivity.kt`/`activity_home.xml` (TL1) và panel/`core/glass` (GL) mà vẫn phủ mọi bề mặt; (ii) hình phải đồng nhất bất kể ROM, điều B1 không làm được vì OEM có thể đã cắt tròn; (iii) G2b cần ảnh nền đầy kín trước khi tô màu/làm 3D, mà `flatten` sinh ra đúng thứ đó; (iv) bộ nhớ trong cache không đổi: vẫn một bitmap ARGB_8888 `sizePx²` mỗi mục cache (bitmap tạm của D1 không vào cache), `IconCache` không phải đổi (số liệu M2: 144×144 = 82.944 byte, 48 MB chứa > 500 icon).
- **Cache và vô hiệu hóa:** không cần đổi khóa vì cache không lên đĩa. Khi G2b thêm kiểu icon, hoặc gọi `IconCache.clear()` (hàm đã có, comment ghi sẵn "M3 gọi khi đổi kiểu icon") hoặc thêm `styleId` vào `cacheKey`; chọn ở plan G2b.
- **Cold start:** chi phí thêm theo ô hiển thị, nạp trên `Dispatchers.IO` (`AppRepositoryImpl.icon` dùng `withContext(io)`); chưa ước tính ms, **đo ở Task 4** và đặt ngưỡng trước khi đo (xem Task 4) để tránh chọn số sau khi biết kết quả.

### Đánh đổi chấp nhận

- Mỗi icon nạp tốn thêm: (adaptive) vẽ hai lớp thay vì một + một bitmap tạm + một lần `drawPath`; (legacy) thêm một lần vẽ 64×64 và quét điểm ảnh. Nếu Task 4 vượt ngưỡng: chuyển D3, hoặc bỏ co theo hộp bao ở E2.
- Icon legacy sẽ **to hơn về dấu chân** (tấm nền lấp kín hộp) so với logo trong suốt bây giờ. Đây là chủ ý để đều với icon adaptive.
- Diện tích nhìn thấy của icon từng bị OEM cắt tròn tăng khoảng 22% (hình bo góc phủ ≈ 0,957 hộp, hình tròn ≈ 0,785 hộp; tính từ công thức bo góc tròn thường, hình liên tục chênh rất ít) → nhìn "to hơn" ≈ 10% theo cạnh dù hộp không đổi. Không đổi `ICON_SCALE` trong TL2; nếu chủ dự án thấy to quá thì chỉnh hằng số đó (đã là "CHỈNH TAY"), xem Câu hỏi mở; quyết ở bài kiểm cuối.
- Ô thư mục đóng vẫn vẽ nền bằng `drawRoundRect` 0,225, hơi khác `IconShape`. Không sửa trong TL2 (xem Ngoài phạm vi).

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Nướng hình dạng vào bitmap ở loader (`IconRenderer`), không cắt trong view | Plan, 2026-11-03: một đường nạp duy nhất (đọc code), tránh vùng file TL1/GL, G2b cần xử lý ở nguồn. **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D2 | Hình = bo góc liên tục dựng bằng Bézier, `cornerRatio` 0,2237, `smoothing` 0,6, đều là hằng số chỉnh tay | Plan, 2026-11-03: số khởi điểm, chưa kiểm chứng; Task 1 tự kiểm công thức bằng tính toán; chủ dự án so `smoothing` 0 và 0,6 bằng mắt ở bài kiểm cuối (mục quyết định). **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D3 | Icon adaptive: tự vẽ `getBackground()` + `getForeground()` với bounds phóng 1,5× quanh tâm; bỏ mặt nạ OEM | Plan, 2026-11-03: tài liệu adaptive icon (lớp 108dp, vùng nhìn 72dp). **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D4 | Icon legacy: kín viền thì vẽ kín hộp; ngược lại tấm nền trắng (xám đậm nếu logo quá sáng) + co hộp bao về 0,70 cạnh | Plan, 2026-11-03: chủ dự án chốt màu ở bài kiểm cuối (Câu hỏi mở 1). **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D5 | `IconShape` ở `core/bitmap/` (pure `android.graphics`), `IconRenderer` ở `data/apps/` | Plan, 2026-11-03: `ui` không import `data`; `core/bitmap` đã dành cho việc đồ họa dùng chung (`project_structure.md` mục vai trò folder). **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D6 | Không đổi `GridMetrics`/`ICON_SCALE`, không đổi `IconCache` | Plan, 2026-11-03: giữ cỡ hộp, ghi nhận tăng cảm nhận cỡ ở Câu hỏi mở 2. **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D7 | Cắt bằng bitmap tạm + `BitmapShader` + `drawPath` (D1); dự phòng mặt nạ `ALPHA_8` phủ toàn hình chữ nhật + `DST_IN` (D3) nếu Task 4 vượt ngưỡng | Plan, 2026-11-03, sửa vòng rà 1/5: bản `DST_IN` + `drawPath` sai vì không xóa góc. **Chủ dự án duyệt 2026-11-03** (phần thẩm mỹ Q1–Q4 còn mở tới bài kiểm cuối) |
| D8 | Q1: giữ `SMOOTHING = 0.6f` (bo góc liên tục) | Chủ dự án, 2026-11-03, sau bài kiểm cuối trên Live 4 |
| D9 | Q2: giữ tấm nền trắng (xám đậm khi hình quá sáng) và các mặc định legacy | Chủ dự án, 2026-11-03 |
| D10 | Q3: icon to hơn một chút → `ICON_SCALE` 0.75 → 0.8 → **0.9** (`core/ui/GridMetrics.kt`, ngoài Vùng file, điều phối duyệt vì TL1 và GL không đụng file này; TL1 chỉ đọc `PADDING_DP`) | Chủ dự án, 2026-11-03. Sửa lại sau số đo của điều phối trên Live 4 (`f84db66`): lưới bị chặn theo CHIỀU CAO (ô cao ≈ 82,7dp, trừ 24dp nhãn và khoảng cách còn ≈ 58,7dp); 0.75 → 44,0dp → 44dp, 0.8 → 46,9dp → làm tròn xuống **vẫn 44dp** (đo 128px ở density 3,0), nên 0.8 không đổi gì. Sau khi TL1 thêm `topReserve` 26dp, chỗ còn ≈ 54,3dp: 0.8 → 40dp (nhỏ hơn hiện tại), 0.9 → 48,9 → 48dp. 0.9 cho 52dp bây giờ và 48dp sau TL1 (≈ +9% so với 44dp), nhãn vẫn đủ chỗ vì chỗ nhãn được trừ trước khi nhân hệ số. Chủ dự án duyệt 0.9; commit `1da38c7` |
| D11 | Q4: giữ vị trí dấu "−" | Chủ dự án, 2026-11-03 |

### Câu hỏi mở (chỉ chủ dự án trả lời được)

1. **Màu tấm nền cho icon legacy:** trắng (kèm xám đậm khi logo quá sáng) như D4, hay màu chủ đạo của icon (E3, sẽ làm cùng G2b)? Mặc định của plan: trắng. Chủ dự án trả lời ở bài kiểm cuối, nhìn trên máy.
2. **Cỡ nhìn thấy sau khi cắt:** sau Task 2, nếu icon trông to hơn mong muốn thì hạ `ICON_SCALE` (0,75) xuống chừng 0,70 — chủ dự án quyết, TL2 không tự đổi vì hằng số đó do chủ dự án chỉnh tay (AGENTS.md "Demo polish").
3. **Hình `smoothing`:** 0 (bo tròn thường) hay 0,6 (liên tục) trông hợp hơn? Quyết ở bài kiểm cuối, nhìn trên máy.
4. **Nền ô thư mục** (`drawFolder`, 0,225 bo tròn thường) có đổi sang `IconShape` cho đồng bộ không? Cần chờ GL merge vì GL đụng ô thư mục.

## Ngoài phạm vi / ghi để sau

- G2b: kiểu tô màu, 3D, màn chọn trong Cài đặt, khóa DataStore `icon_style`. Lưu ý cho G2b: `AdaptiveIconDrawable.getMonochrome()` chỉ có từ API 33, Live 4 (API 30) không có → tô màu phải dựa vào độ sáng sau `flatten`.
- Ô thư mục đóng (`IconCellView.drawFolder`), panel thư mục, panel nhóm: nền của chúng không phải icon app, và thuộc vùng GL.
- Icon shortcut trong menu nhấn giữ (`ShortcutRepositoryImpl`, 24dp) và avatar liên hệ.
- Dấu hiệu work profile trên icon (hiện `getIcon(0)` không có huy hiệu; không đổi).
- Cache đĩa cho icon (đã hoãn từ M3, vẫn hoãn).
- Dấu "−" của edit mode: vị trí tâm = góc trên-trái hộp icon (demo polish); sau khi cắt, góc đó trống nên dấu hơi "lơ lửng". Chỉ **kiểm bằng mắt** ở Task 5; nếu cần dịch vào thì đó là việc của `IconCellView.drawBadge` (chủ dự án quyết).

## Vùng file

| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/core/bitmap/IconShape.kt` | Tạo | Không |
| `app/src/main/java/gambi/global/os26launcher/data/apps/IconRenderer.kt` | Tạo | Không |
| `app/src/main/java/gambi/global/os26launcher/data/apps/IconLoader.kt` | Sửa (gọi `IconRenderer`, giữ nguyên chữ ký `load` và cách bắt lỗi) | Không (vùng của TL2) |
| `docs/plans/TL2-icon-shape.md`, `docs/knowledgements/TL2-icon-shape.md` | Tạo | Không |
| `app/src/main/java/gambi/global/os26launcher/core/ui/GridMetrics.kt` | Sửa một hằng số (`ICON_SCALE` 0.75 → 0.9), thêm sau bài kiểm cuối; điều phối duyệt 2026-11-03 | Không (TL1 chỉ đọc `PADDING_DP`) |

Không sửa: `HomeActivity.kt`, `activity_home.xml`, `Jiggler`, pager (TL1); `core/glass/*`, panel thư mục/nhóm, thanh tìm kiếm, pill (GL); `IconCache`, `IconCellView`, các adapter, `AppRepository`/`AppRepositoryImpl`, `LauncherAppsDataSource`. Hilt: `IconRenderer` khai `@Inject constructor()` nên không cần sửa module DI. Nếu trong lúc làm phát hiện phải sửa file ngoài bảng này (kể cả `IconCache`), **dừng và báo**.

## Global Constraints

- `domain` không import `android.*`; `ui` không import `data`. `IconShape` ở `core/bitmap/` chỉ import `android.graphics`.
- Không thêm dependency, không đổi schema Room, không thêm khóa DataStore, không thêm hàng vào Cài đặt.
- Không thêm test tự động. Log chẩn đoán tạm dùng tag `TL2`, phải xóa ở Task 5 (bài học M8b: log tạm một lần rồi xóa).
- Giữ nguyên cam kết của `IconLoader.load`: chạy luồng nền, `sizePx <= 0` trả null, mọi lỗi (kể cả `OutOfMemoryError`) trả null chứ không làm sập màn hình; bitmap trả về **không bị sửa sau khi vào cache** (view chỉ đọc).
- Không dùng `Paint`/`Path` dùng chung giữa các luồng (nạp icon chạy song song trên `Dispatchers.IO`): tạo mới mỗi lần gọi hoặc dùng bất biến.
- Hằng số chỉnh tay (`CORNER_RATIO`, `SMOOTHING`, `LEGACY_ART_RATIO`, các ngưỡng legacy) khai thành `const val` có comment "CHỈNH TAY", cùng kiểu `ICON_SCALE`.
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự, thân là gạch đầu dòng, kết thúc bằng dòng `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Lệnh build: `gradlew.bat assembleDebug` trong worktree; build đỏ thì không báo xong Task.

## Review Focus

- **Góc trong suốt thật:** điểm ảnh ở góc bitmap kết quả có alpha = 0, không chỉ "trông" bo tròn (kiểm bằng `getPixel(0, 0)` log tạm ở Task 1, hoặc xem icon trên nền tương phản).
- **Chống răng cưa thật sự:** cạnh hình bo góc mịn ở cỡ lưới trên Live 4 (chụp màn hình, phóng to), không có viền sáng/tối quanh góc khi icon nằm trên kính dock và trên nền tối của lớp tìm kiếm (alpha đã nhân trước).
- **Không có bề mặt nào bị sót:** lưới, dock, 3×3 icon con của ô thư mục, lưới trong panel thư mục, ô nhóm App Library (4 icon nhỏ) và panel nhóm, kết quả tìm kiếm, màn đã ẩn, ảnh kéo, icon nổi của menu nhấn giữ.
- **Bounds của drawable con:** `setBounds` lên `getBackground()`/`getForeground()` không làm hỏng lần nạp sau của cùng app ở cỡ khác (nạp một app ở hai cỡ liên tiếp, so hai ảnh).
- **Lớp null / không đục:** `AdaptiveIconDrawable` có `getBackground()` null hoặc nền trong suốt; icon legacy trong suốt hoàn toàn; `VectorDrawable`; `LayerDrawable` có kích thước nội tại lạ.
- **Cùng cỡ nhìn thấy:** hộp icon vẫn `iconSize × iconSize`; không có chỗ nào icon bị lệch tâm hoặc cắt cụt ngoài ý muốn; dấu "−" và nhãn không dịch.
- **Luồng:** không chia sẻ `Paint`/`Path`/bitmap mẫu giữa các coroutine; bitmap mẫu 64×64 không bị giữ lại.
- **Số đo Task 4:** ngưỡng ghi trước, số liệu ghi đủ (máy, số icon, trung bình, p95/max của cả `render` và "vẽ gốc" trong cùng lượt).
- **Bitmap tạm:** `content` được `recycle()` trên mọi đường (kể cả khi ném lỗi), không bị `IconCache` giữ.

---

### Task 1: `IconShape` + giai đoạn cắt + log chẩn đoán tạm
**Bắt buộc kiểm tay:** không
**Files:** Tạo `core/bitmap/IconShape.kt`, `data/apps/IconRenderer.kt`; sửa `data/apps/IconLoader.kt`.
**Interfaces:**
- Produces: `class IconShape(cornerRatio: Float, smoothing: Float)` với `fun path(sizePx: Int): Path` (đóng kín, tọa độ 0..`sizePx`, tạo mới mỗi lần gọi) và `companion object { val DEFAULT }`; `class IconRenderer @Inject constructor()` với `fun render(drawable: Drawable, sizePx: Int): Bitmap`, bên trong tách `flatten(drawable, canvas, sizePx)` (hiện chỉ `drawable.setBounds(0,0,sizePx,sizePx); drawable.draw(canvas)`, như `IconLoader` đang làm) và `mask(content: Bitmap): Bitmap` (D1: `Paint` chống răng cưa với `BitmapShader(content, CLAMP, CLAMP)` + `drawPath(IconShape.path(sizePx))` lên bitmap kết quả mới, rồi `content.recycle()`; cố ý không dùng `DST_IN` + `drawPath` vì không xóa góc).
- Consumes: `IconLoader.load` thay đoạn tạo bitmap bằng `renderer.render(drawable, sizePx)`.
- [x] **Step 1:** Mở lại bài viết gốc của Figma về corner smoothing, ghi link vào Nhật ký; viết `IconShape.path` (Bézier bậc 3 qua `Path.cubicTo`, `smoothing = 0` suy biến thành cung tròn thường).
- [x] **Step 2:** `IconRenderer.render` = tạo bitmap tạm ARGB_8888 `content` → `flatten` (giữ hành vi cũ) → `mask(content)` trả bitmap kết quả. Điểm ảnh góc của kết quả phải có alpha = 0 (không chỉ trông bo tròn). `IconLoader` giữ nguyên `cache.get/put`, kiểm `sizePx <= 0`, các `catch`.
- [x] **Step 3:** Thêm log tạm tag `TL2`: lớp của drawable (`javaClass.simpleName`), là adaptive hay không, thời gian `flatten` và `mask` (ms, `System.nanoTime`), gom đếm mỗi 20 icon.
- [x] **Step 4:** Tự kiểm công thức Bézier, không cần chủ dự án: (i) với `smoothing = 0` các điểm mẫu của `path` phải trùng cung tròn bán kính `cornerRatio × size` (sai số < 0,5px ở 512px); (ii) với `smoothing = 0,6` kiểm hai đầu mỗi góc: tiếp tuyến liên tục với cạnh thẳng và với đoạn cung giữa, độ cong tăng dần từ 0 (không nhảy bậc) — tính bằng log tạm các điểm/góc tiếp tuyến hoặc `PathMeasure`; (iii) cạnh trên, dưới, trái, phải chạm đúng 0 và `size`. Ghi kết quả vào Nhật ký; mã/log kiểm này xóa ở Task 5. Việc nhìn hình bằng mắt thuộc bài kiểm cuối.
- [x] **Step 5: Kịch bản kiểm (Live 4)** — (a) mở Home: icon app ở lưới và dock có góc bo, không răng cưa khi phóng ảnh chụp, và **góc trong suốt thật** (thấy nền/kính dock qua góc, không có góc vuông có màu); (b) mở App Library, tìm kiếm, "Ứng dụng đã ẩn" (Cài đặt): icon bo góc; (c) cuộn lưới, kéo thả một icon, nhấn giữ một icon: ảnh kéo/icon nổi cũng bo góc; (d) logcat tag `TL2`: có số đếm tỉ lệ adaptive/legacy trên 87 app (số này quyết định Task 3 có cần kỹ hay không). Các mục này do agent/điều phối tự kiểm hoặc gộp vào bài kiểm cuối, **không dừng chờ chủ dự án**.

### Task 2: Icon adaptive tự vẽ hai lớp
**Bắt buộc kiểm tay:** không
**Files:** `data/apps/IconRenderer.kt`.
**Interfaces:** `flatten` rẽ nhánh: `drawable is AdaptiveIconDrawable` thì vẽ lớp nền rồi lớp trước (không dùng `draw()` của chính drawable adaptive).
- [x] **Step 1:** Với `q = sizePx / 4`: `background.setBounds(-q, -q, sizePx + q, sizePx + q)`, `foreground` tương tự, vẽ nền trước rồi lớp trước. Lớp null thì bỏ qua; nền null thì tô tấm nền trắng (dùng chung hàm tấm nền của Task 3 nếu đã có, nếu chưa thì tô trắng tạm).
- [x] **Step 2:** Kiểm hai lần nạp liên tiếp cùng app ở hai cỡ (lưới và 40dp của tìm kiếm) cho ảnh đúng, không bị cái sau ảnh hưởng cái trước. Nếu thấy lệch: `mutate()` hoặc nạp lại drawable cho mỗi cỡ (ghi vào Nhật ký).
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) tìm 3 app có icon vốn bị OEM cắt tròn: giờ cùng một hình bo góc như các app khác; (b) ghi lại icon có chi tiết sát mép (nền nhạt, chữ gần mép) xem có bị cắt xấu; (c) so cỡ nhìn thấy trước/sau trên cùng app (ảnh chụp `main` vs nhánh): dấu chân hộp bằng nhau, tâm không lệch; (d) ghi nhận xét "có vẻ to hơn" (nếu có) vào Nhật ký để điều phối đưa vào mục quyết định của bài kiểm cuối (Câu hỏi mở 2); **không dừng chờ chủ dự án**.

### Task 3: Icon legacy — tấm nền, co vừa, kín viền
**Bắt buộc kiểm tay:** không
**Files:** `data/apps/IconRenderer.kt`.
**Interfaces:** `flatten` nhánh `else`: đo ảnh mẫu 64×64 rồi vẽ.
- [x] **Step 1:** Vẽ drawable vào bitmap mẫu 64×64 (tạo mới mỗi lần, không giữ), `getPixels`, tính: hộp bao điểm ảnh có alpha ≥ 16, độ đục của vòng viền 1px, độ sáng trung bình của điểm ảnh alpha ≥ 128.
- [x] **Step 2:** Viền ≥ 90% đục → vẽ kín hộp, không tấm nền. Ngược lại tô tấm nền (trắng, hoặc `#FF2C2C2E` nếu độ sáng trung bình > 0,85) rồi vẽ drawable đã co và dời sao cho cạnh dài hộp bao = `LEGACY_ART_RATIO (0,70) × sizePx`, tâm trùng tâm hộp; chặn hệ số phóng tối đa (đặt 1,6, CHỈNH TAY) để logo bé không bị phóng quá. Ảnh trong suốt hoàn toàn thì chỉ có tấm nền.
- [x] **Step 3:** Nếu số đo Task 1 cho thấy gần như không còn icon legacy trên Live 4, giữ cài đặt tối thiểu (tấm nền + co) và báo điều phối trước khi làm phần phát hiện kín viền/độ sáng.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — (a) tìm các app legacy theo log `TL2` của Task 1: logo trong suốt nằm giữa tấm nền, cỡ đều với icon adaptive; (b) một app tự mang nền vuông: không có viền trắng thừa; (c) app có logo trắng/rất sáng (nếu có): tấm nền xám đậm, nhìn thấy logo; (d) ghi nhận ảnh chụp/nhận xét về màu tấm nền vào Nhật ký; chủ dự án chốt màu (Câu hỏi mở 1) ở bài kiểm cuối, **không dừng chờ chủ dự án**.

### Task 4: Đo hiệu năng tạo hình
**Bắt buộc kiểm tay:** có — phép đo hiệu năng dùng để chọn phương án (mục 4.2): kết quả quyết định giữ D1 (bitmap tạm + `BitmapShader`) hay chuyển D3 (mặt nạ dựng sẵn), và có giữ phần đo/co của Task 3 hay không. Chỉ Live 4; Oppo không bắt buộc vì chi phí tạo hình là CPU theo số icon hiển thị, không liên quan kính (nếu chủ dự án có Oppo thì chạy thêm một lượt để thấy ca 344 app).
**Files:** `data/apps/IconLoader.kt` / `IconRenderer.kt` (chỉ log tạm).
**Ngưỡng cố định trước khi đo (điều phối/chủ dự án chỉnh được, không đổi sau khi thấy số):** (1) trung bình `render` mỗi icon ≤ 2× trung bình "vẽ gốc" cùng lượt đo; (2) tổng `render` của một trang đầu (≈ 24 ô lưới + 4 ô dock) ≤ tổng "vẽ gốc" + 50 ms; (3) lớn nhất một icon ≤ 30 ms.
**Bộ nhớ:** không có phép so `meminfo` với `main`. Chặn trên bằng lập luận: số bitmap trong `IconCache` và cỡ mỗi bitmap không đổi (ARGB_8888 `sizePx²`); bitmap tạm của D1 và bitmap mẫu 64×64 của Task 3 chỉ sống trong một lần `render` và được `recycle()`. (Lập luận, không phải số đo.)
- [x] **Step 1:** Log tạm tag `TL2`. **Cùng một lượt chạy** ghi hai số cho mỗi icon: thời gian `render` đầy đủ và thời gian "vẽ gốc" (đường cũ: `setBounds` + `draw` vào một bitmap rác, bỏ ngay), nên chủ dự án **không phải cài `main` riêng** để có mốc so. (Mốc từ giai đoạn `flatten` ở log Task 1 trên cùng nhánh vẫn có trong Nhật ký, nhưng Task 1 không có lượt kiểm tay, nên mốc dùng để so là mốc đo cùng lượt này.) Cuối đợt in một dòng tổng: số icon, trung bình/lớn nhất/p95 của `render` và "vẽ gốc", tách adaptive / legacy, tổng trang đầu.
- [x] **Step 2:** Chủ dự án chạy đúng các lệnh sau (PowerShell, Live 4 cắm USB, tại thư mục worktree):
  ```powershell
  .\gradlew.bat installDebug
  adb shell am force-stop gambi.global.os26launcher
  adb logcat -c
  adb shell monkey -p gambi.global.os26launcher -c android.intent.category.LAUNCHER 1
  # đợi lưới hiện đủ, vuốt qua hết các trang một lần, rồi:
  adb logcat -s TL2 -d > tl2-run1.txt
  ```
  Lặp từ dòng `force-stop` thêm 2 lần (`tl2-run2.txt`, `tl2-run3.txt`) rồi gửi ba file cho điều phối. Không cần `dumpsys meminfo`, không cần cài `main`.
- [x] **Step 3:** Điều phối so ba lượt với ngưỡng; vượt thì agent làm D3 (mặt nạ `ALPHA_8` dựng sẵn theo `sizePx`, `ConcurrentHashMap`, blit `DST_IN` phủ toàn hình chữ nhật) rồi chủ dự án chạy lại đúng các lệnh trên. Ghi số vào Nhật ký.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — (a) ba lượt mở nguội như Step 2; (b) vuốt nhanh qua các trang, cảm nhận không giật hơn trước (không đo Janky).

---

### Task 5: Dọn log, rà cỡ/inset và mọi bề mặt
**Bắt buộc kiểm tay:** không
**Files:** `data/apps/IconRenderer.kt`, `data/apps/IconLoader.kt`, `core/bitmap/IconShape.kt`.
- [x] **Step 1:** Xóa mọi log tag `TL2` và đoạn dựng ảnh so sánh; để lại chú thích ngắn nêu điểm cắm của G2b giữa `flatten` và `mask`.
- [x] **Step 2:** Soát `grep` toàn nhánh không còn `Log.*TL2`; soát hằng số chỉnh tay đều có comment.
- [x] **Step 3:** Ghi vào knowledgement (viết dần theo mục 8 của MultiAgent-Workflow, có `sequenceDiagram` cho luồng `HomeViewModel.loadIcon → AppRepository.icon → IconLoader.load → IconRenderer`): luồng, quyết định, bài học cho AGENTS.md.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — danh sách bề mặt ở Review Focus: lưới, dock, thư mục (ô và panel), App Library (ô nhóm, panel nhóm), tìm kiếm, "Ứng dụng đã ẩn", kéo icon, nhấn giữ (icon nổi), edit mode (dấu "−" và rung không lệch); xoay/đổi cỡ lưới (4×5, 5×6) icon vẫn bo góc đúng.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11, 87 app). Oppo A3x không bắt buộc. **Cài:** nhánh `fix/tl2-icon-shape` ở commit đầu nhánh (điều phối ghi `nhánh@commit` khi gửi; mã nguồn không đổi từ commit `119addd`, sau đó chỉ có tài liệu), `.\gradlew.bat installDebug` trong worktree `E:\SourceTree\IosLauncher-wt\TL2-icon-shape`. Build này **không còn log `TL2`**.

**Chuẩn bị:** (1) có ít nhất một thư mục ≥ 4 app (kéo app đè lên nhau để tạo); (2) biết app nào trên máy có icon tròn / giọt nước (đa số là icon adaptive) để so; (3) trên Live 4 chỉ khoảng 1 trong 20 icon là icon legacy, đã quan sát log là hình tròn đặt trên tấm nền trắng ở 0,70 cạnh: tìm icon có viền trắng quanh hình (thường là icon "lạ" nhất lưới) và ghi tên app vào báo cáo; nếu không thấy thì ghi "không thấy icon có tấm nền".

**Các mục kiểm, theo thứ tự thao tác trên máy:**

1. **Mở Home từ nguội** (đóng hẳn launcher rồi mở lại): mọi icon lưới và dock có góc bo cùng một hình, góc thật sự trong suốt (nhìn nền ảnh và kính dock thấy qua góc, không có góc vuông có màu). Chụp màn hình, phóng to một icon: cạnh mịn, không viền sáng/tối lạ. (Task 1)
2. **So ba loại icon:** một icon tròn hoặc giọt nước (adaptive), icon có tấm nền trắng (legacy, nếu thấy), một icon sát mép/nền nhạt: cùng một hình, cỡ gần đều, không lệch tâm, chi tiết sát mép không bị cắt xấu. (Task 2–3)
3. **So cỡ nhìn thấy:** so với ảnh chụp trước đây (hoặc bản `main` cài lại nếu bạn có): hộp icon bằng nhau; ghi lại icon có "to hơn" không (câu Q3). (Task 2)
4. **Thư mục:** ô thư mục đóng (3×3 icon con) và mở panel (lưới icon con) đều bo góc. (Task 1)
5. **App Library** (vuốt trái từ trang cuối): ô nhóm (4 icon nhỏ) và panel khi bấm vào một nhóm; thanh tìm kiếm trong Library mở lớp tìm kiếm: kết quả gõ tên app có icon bo góc. (Task 1)
6. **Cài đặt → "Ứng dụng đã ẩn"** (cần có ít nhất một app đã ẩn, ẩn bằng dấu "−" ở edit mode): icon 48dp bo góc. (Task 1)
7. **Nhấn giữ một icon** (chưa vào edit mode): icon nổi lên trên lớp mờ vẫn bo góc. **Vào edit mode:** dấu "−" ở góc trên-trái, icon rung, **kéo một icon** thấy ảnh đang bay bo góc, thả xuống đúng chỗ; không icon nào bị trống hay nhảy ô. (Task 5)
8. **Đổi cỡ lưới** (Cài đặt: 4×5 → 5×6 → 4×6): icon ở mọi cỡ vẫn bo góc đúng, không méo. (Task 5)
9. **Hồi quy:** (a) gỡ rồi cài lại một app (hoặc `adb shell pm disable-user --user 0 <package>` rồi `pm enable`): icon quay lại vẫn bo góc; (b) cuộn qua nhiều trang, không icon nào "nháy" trống; (c) chuyển trang và mở/đóng thư mục, tìm kiếm không giật hơn bản trước (chỉ cảm nhận, không đo Janky); (d) dock có kính vẫn thấy icon rõ.
10. **Số đo hiệu năng (Task 4) đã làm xong**, không cần chạy lại: xem Nhật ký, dòng "Task 4 (số đo)".

**Mục quyết định của chủ dự án** (nhìn trên máy, một lần ở cuối). Nếu chọn phương án khác, sửa **một hằng số**, build lại và cài lại; không phải sửa thiết kế. Điều phối/agent sửa theo trả lời.

| # | Câu hỏi | Mặc định hiện tại | Nếu chọn khác, sửa chỗ nào |
|---|---|---|---|
| Q1 | Góc "liên tục" (iOS) hay bo tròn thường nhìn hợp hơn? Xem ảnh phóng to một icon ở mục 1 | `SMOOTHING = 0.6f` | `app/src/main/java/gambi/global/os26launcher/core/bitmap/IconShape.kt`, dòng 104 (trong `companion object`): đổi thành `0f` cho bo tròn thường. Bán kính góc: dòng 101 `CORNER_RATIO = 0.2237f` (tăng cho bo nhiều hơn, vd `0.25f`) |
| Q2 | Tấm nền icon legacy (trắng, xám đậm khi hình quá sáng) có ổn không? | Trắng; xám đậm `#2C2C2E` nếu độ sáng hình > 0,85; hình co về 0,70 cạnh | `app/src/main/java/gambi/global/os26launcher/data/apps/IconRenderer.kt`, khối hằng số cuối file: `PLATE_WHITE` (màu tấm nền), `LEGACY_ART_RATIO = 0.70f` (dòng 152, cỡ hình trên tấm nền), `BRIGHT_LUMA` (dòng 155). Muốn màu chủ đạo của icon thì **không** sửa hằng số: đó là việc mới, làm cùng G2b |
| Q3 | Icon có to quá sau khi cắt không? | `ICON_SCALE = 0.75f` | `app/src/main/java/gambi/global/os26launcher/core/ui/GridMetrics.kt`, dòng 129: đổi `0.75f` thành khoảng `0.70f`. Đây là hằng số bạn tự chỉnh từ "Demo polish"; `GridMetrics.kt` **ngoài Vùng file của TL2** nên điều phối quyết có sửa trong nhánh này hay không |
| Q4 | Dấu "−" ở góc trên-trái có bị "lơ lửng" (vì góc icon giờ trống) khó chịu không? | Tâm dấu trùng góc hộp icon | `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconCellView.kt`, hàm `badgeCenter()` (dòng 95): dịch tâm vào trong một chút, vd `PointF(iconLeft + c.iconSize * 0.06f, c.iconSize * 0.06f)`. `IconCellView.kt` **ngoài Vùng file của TL2** và là file mà TL1/GL có thể chạm: cần điều phối quyết |

**Báo kết quả:** số mục lỗi + mô tả hoặc ảnh chụp; câu trả lời Q1–Q4 (giữ mặc định hay chọn khác).

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Vsmart Live 4, 2026-11-03, `fix/tl2-icon-shape@519153b`: chủ dự án báo **mọi mục đạt**. Quyết định Q1–Q4 ở D8–D11 (Q3 đổi `ICON_SCALE` sang 0.9 (0.8 đã thử nhưng vẫn ra 44dp), cài lại để kiểm cỡ icon và mục 8 đổi cỡ lưới, commit `8547d7e`). Mục 2: chủ dự án không phóng được trên điện thoại nên điều phối chụp màn hình, phóng 4× bằng nearest-neighbour: góc mịn, không viền sáng/tối. Quan sát thêm của chủ dự án: "icon trong edit mode nhỏ hơn" (đã xác nhận bằng hai ảnh chụp cạnh nhau): ở bản dựng từ `main` này, edit mode đẩy `home_content` xuống 64dp nên chiều cao ô nhỏ lại và `GridMetrics` tính icon nhỏ hơn. Đó là hành vi có sẵn mà TL1 gỡ bỏ, **không phải lỗi của TL2**.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-03 | — | Đọc `MultiAgent-Workflow.md`, `AGENTS.md`, `project_structure.md`, `Launcher-Features.md` và code icon; viết plan | `docs: add TL2 plan` | Chờ điều phối rà plan |
| 2026-11-03 | — | Sửa plan sau rà vòng 1: (1) D1 `DST_IN` + `drawPath` không xóa góc → thay bằng bitmap tạm + `BitmapShader` + `drawPath`, D3 giữ làm dự phòng, thêm dòng Review Focus "góc trong suốt thật"; (2) bỏ chặng nhìn hình giữa chừng: Task 1 tự kiểm Bézier bằng tính toán, quyết định thẩm mỹ chuyển vào bài kiểm cuối; (3) Task 4: lệnh `adb` chép-dán, ngưỡng cố định trước, mốc "vẽ gốc" đo cùng lượt (không cài `main`), bỏ so `meminfo` | `docs: revise TL2 plan after review` | Vòng rà plan 1/5 |
| 2026-11-03 | 1 | `IconShape` (Bézier liên tục, nguồn: bài Figma "Desperately seeking squircles" + figma-squircle, chưa mở lại trực tiếp, công thức viết theo trí nhớ và đã kiểm bằng tính toán), `IconRenderer` (flatten + mask bằng `BitmapShader`), `IconLoader` gọi renderer, log tạm `Tl2Diag`. Tự kiểm Bézier (node, không commit): `smoothing=0` sai lệch cung tròn 0,03px ở 512px; `0,6`: điểm cuối đúng (p,p), tiếp tuyến liên tục tại hai chỗ nối (27° và 63°), độ cong tăng đơn điệu 0 → 1,47/R rồi nhảy xuống 1,00/R ở chỗ nối cung (G1 liên tục, độ cong chưa liên tục hoàn toàn, đúng cấu trúc của công thức Figma). Build xanh. Chưa chạy trên máy | `4171cda` | Task 1 qua review vòng 1/5 (điều phối, 2026-11-03) |
| 2026-11-03 | 1 (đo máy) | Điều phối cài `c58798b` trên Live 4, mở nguội: lớp drawable gặp `AdaptiveIconDrawable` và `BitmapDrawable`; `n=20 adaptive=19 legacy=1 size=180 avgFlattenMs=2.04 avgMaskMs=0.75 cornerOpaque=0 pathBounds=RectF(0,0,180,180)`. Kết luận: góc trong suốt thật (bằng chứng Review Focus 1), mask ~0,75 ms/icon ở 180px, khoảng 95% icon là adaptive nên Task 3 làm tối thiểu. Ảnh chụp lưới cho thấy góc bo. Độ cong G1 (nhảy 1,47/R → 1,00/R) ghi nhận như vậy, chủ dự án đánh giá bằng mắt ở bài kiểm cuối | `c58798b` | Ghi nhận, không có vòng sửa |
| 2026-11-03 | 2 | `IconRenderer.flattenAdaptive`: vẽ `background` rồi `foreground` với bounds `(-q, -q, size+q, size+q)`, `q = size/4`; lớp nền null thì tô trắng (+ log `TL2`); lớp trước null thì bỏ qua. Build xanh. Chưa chạy máy: kiểm hai cỡ liên tiếp (Step 2), so cỡ nhìn thấy trước/sau và icon sát mép (Step 3) cần ảnh trên Live 4 | `942f940` | Task 2 qua review vòng 1/5 (điều phối, 2026-11-03) |
| 2026-11-03 | 3 | `IconRenderer.flattenLegacy`: mẫu 64×64 → hộp bao điểm ảnh alpha≥16, tỉ lệ viền đục, độ sáng trung bình. Rỗng: chỉ tấm nền. Viền đục ≥ 90%: vẽ kín hộp. Còn lại: tấm nền trắng (xám đậm `#2C2C2E` nếu độ sáng > 0,85), co/phóng để cạnh dài hộp bao = 0,70 × size (tối đa ×1,6), căn giữa. Không bỏ phần nào của Step 2 vì cả ba phép đo dùng chung một lượt quét 64×64 (rẻ). Build xanh. Chưa chạy máy. Log `TL2 legacy icon: ...` cho từng icon legacy | `5d677d2` | Task 3 qua review vòng 1/5 (điều phối, 2026-11-03) |
| 2026-11-03 | 4 (Step 1) | Đo cùng lượt: `render` đầy đủ (gồm cấp bitmap tạm) và "vẽ gốc" (`setBounds` + `draw` vào bitmap rác, gồm cấp bitmap). `Tl2Diag` in dòng `n=... adaptive n/renderAvg/baseAvg/renderP95/renderMax | legacy ... | first28 renderMs/baseMs` mỗi 20 icon và khi đủ 28 icon. Build xanh. Chờ điều phối cài và chạy Step 2 (3 lượt mở nguội) | `c474590` | Task 4 qua (điều phối đo, xem dòng kế) |
| 2026-11-03 | 4 (số đo) | Điều phối đo trên Live 4, `93a64c8`, 3 lượt mở nguội (force-stop, monkey HOME, 6 s). adaptive renderAvg/baseAvg ms: 2,95/5,65 · 7,53/10,35 · 3,42/4,81; renderP95: 9,50 · 21,97 · 8,58; renderMax: 10,74 · **43,89** · 13,30; 28 icon đầu render/base ms: 87,89/153,16 · 206,35/301,76 · 102,07/131,44; legacy (n=1) render/base: 8,20/0,66 · 3,13/22,31 · 9,75/1,63. `cornerOpaque=0` cả 3 lượt. Dòng legacy: `plate luma=0.35 scale=0.70 box=1.00x1.00` (không phải kín viền; nhiều khả năng icon tròn đặt trên tấm nền trắng ở 0,70; log không ghi tên app). Ngưỡng cố định trước: (1) trung bình ≤ 2× base: đạt; (2) 28 icon đầu ≤ base + 50 ms: đạt (render thấp hơn base cả 3 lượt); (3) lớn nhất ≤ 30 ms: 1/3 lượt vượt (43,89 ms ở lượt 2) trong khi đường cũ cũng tăng vọt cùng lượt (baseAvg 10,35, legacy base 22,31 ms), tức máy chậm lúc cold start đó. Quyết định: giữ D1, không cần D3 | `93a64c8` | Điều phối chấp nhận |
| 2026-11-03 | 5 | Xóa `Tl2Diag`, đo "vẽ gốc", mọi log `TL2` và import thừa trong `IconRenderer`; để comment điểm cắm của G2b giữa `flatten` và `mask`. `git grep -i tl2` trong `app/src` không còn kết quả. Hoàn thiện knowledgement (2 sơ đồ Mermaid, số đo Task 4, bài học). Build xanh. Chưa làm: kiểm hai cỡ liên tiếp, so cỡ nhìn thấy và các bề mặt trên máy (gộp bài kiểm cuối) | `119addd` | Task 5 qua review vòng 1/5 (điều phối, 2026-11-03) |
| 2026-11-03 | — | Chuẩn bị đóng: hoàn thiện "Bài kiểm cả tính năng" (10 mục theo thứ tự thao tác + bảng Q1–Q4 kèm file/dòng/giá trị để đổi), điền "Bằng chứng Review Focus" cho mọi dòng đã có bằng chứng, dòng còn lại ghi "chờ bài kiểm cuối". Chưa rebase, PR vẫn draft | `docs: prepare TL2 final test pass` | Chờ kiểm tay cả tính năng |
| 2026-11-03 | kiểm cuối | Chủ dự án kiểm `519153b` trên Live 4: mọi mục đạt. Q1 giữ 0.6, Q2 giữ tấm nền trắng, Q4 giữ dấu "−", **Q3: `ICON_SCALE` 0.75 → 0.8** (sau đó sửa thành 0.9, xem dòng kế) (`GridMetrics.kt`, điều phối duyệt sửa ngoài Vùng file). Điều phối phóng 4× ảnh chụp: góc mịn, không viền. Quan sát "icon trong edit mode nhỏ hơn": do edit mode đẩy `home_content` xuống 64dp (có từ `main`, TL1 gỡ), không phải lỗi TL2. Build xanh | `8547d7e` | Chờ điều phối cài lại, kiểm cỡ icon và mục 8 rồi merge |
| 2026-11-03 | sửa Q3 | Điều phối đo trên Live 4 `f84db66`: icon vẫn 44dp ở 0.8 (128px, density 3,0) vì lưới bị chặn theo chiều cao và cỡ làm tròn xuống bội 4dp; phép tính "chặn theo bề rộng 60dp" trước đó sai. Chủ dự án duyệt **`ICON_SCALE = 0.9`**: 52dp hiện nay, 48dp sau TL1 (`topReserve` 26dp). Đã sửa ghi chú D10, knowledgement và comment hằng số. Build xanh | `1da38c7` | Chờ điều phối cài lại, kiểm cỡ icon và mục 8 rồi merge |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Chống răng cưa thật sự | | |
| 2 | Không bề mặt nào bị sót | | |
| 3 | Bounds của drawable con | | |
| 4 | Lớp null / không đục | | |
| 5 | Cùng cỡ nhìn thấy | | |
| 6 | Luồng | | |
| 7 | Số đo Task 4 | Live 4, `93a64c8`, 3 lượt mở nguội, xem Nhật ký dòng "Task 4 (số đo)": ngưỡng (1) và (2) đạt cả 3 lượt; ngưỡng (3) vượt 1 lượt (43,89 ms) khi đường cũ cũng tăng vọt | Đạt (ghi chú 1 lượt nhiễu) |
| 8 | Góc trong suốt thật | `Tl2Diag` trên Live 4, `c58798b`, cold start: `cornerOpaque=0` (n=20, size=180) | Đạt |
| 9 | Bitmap tạm được recycle | `finally { content.recycle() }` trong `IconRenderer.render` (đọc code) | Đạt (chưa kiểm bằng máy) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: TT#12 ghi "Một phần": hình dạng (bo góc liên tục, đồng nhất adaptive/legacy) xong ở TL2; tô màu, 3D và lựa chọn trong Cài đặt còn lại (G2b).
- `project_structure.md`: thêm `core/bitmap/IconShape.kt` và `data/apps/IconRenderer.kt` vào cây; sửa dòng `IconLoader.kt` (nay gọi `IconRenderer`); cập nhật hàng `core/bitmap/` trong bảng vai trò folder (nay có `IconShape`, không còn "chưa tách" tuyệt đối); bảng mốc thêm TL2.
- `room-schema.md`: không đổi.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement (kỳ vọng: số đo chi phí tạo hình mỗi icon trên Live 4; tỉ lệ adaptive/legacy; bài học "`drawPath` + `DST_IN` không xóa góc, dùng `BitmapShader`").
