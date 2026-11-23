# Thiết kế Figma (v3, 2026-11-16): đối chiếu với app và danh sách việc

Trạng thái: **đang dùng**. Bản v3 thay bản nháp v2 (2026-11-12). Bảng đối chiếu ở mục 3 đọc trên `main` @ `47728c9` (2026-11-16), thứ tự ưu tiên đã duyệt 2026-11-16 (V4).

**v3 là thiết kế chính thức** (chủ dự án, 2026-11-16, V1): phong cách coi như đã chốt, nhưng còn thiếu một số màn và đang bổ sung. FD2 (giữ kiểu Cài đặt cũ) hết hiệu lực; dựng giao diện v3 bắt đầu từ Components (hàng 1, mục 3). Màn nào thiết kế chưa có thì chờ, không tự vẽ.

## 1. Nguồn và cách đọc

- **Link mới nhất (chủ dự án gửi 2026-11-18):** `https://www.figma.com/design/uauorWdd2Ayg9g4PUuhSCX/Laucher-App--Copy-?node-id=302-1490` (MCP chính thức vẫn báo không có quyền; đọc bằng figma-bridge, màn 3.2 = node `302:2334`). Link v3 cũ: `https://www.figma.com/design/eAYOEhzYIsPIis5bbuGMag/Laucher-App--Copy-?node-id=302-1490`. Node `302:1490` là **cả trang** "v3 · Thiết kế mới", không phải một frame.
- Link cũ: v2 `Dh4freOJnCxewJLhaYqYui` (node `0:1`); `pspxZqs4lKDs3lrVqPC3c8` không đọc được.
- **MCP Figma chính thức không đọc được file v3** (đo 2026-11-16): `get_screenshot`, `get_metadata` báo "you don't have edit access to this file". Trước đó (2026-11-12) gói Starter đã hết lượt gọi.
- **Dùng MCP `figma-bridge`** (`@gethopp/figma-mcp-bridge@0.0.26`, cổng `127.0.0.1:1994`, khai ở `.mcp.json`):
  1. Chủ dự án mở file trong Figma, chạy plugin bridge và **giữ cửa sổ plugin mở**.
  2. Gọi `list_files` lấy `fileKey`. Khóa **đổi mỗi lần kết nối** (2026-11-16 là `unsaved-...`), luôn gọi lại. Khi chỉ một file kết nối thì bỏ trống `fileKey` cũng được.
  3. Bridge **chỉ đọc được trang đang mở**. Muốn đọc trang khác thì nhờ chủ dự án bấm sang trang đó.
  4. `get_document` trả cả trang đang mở (v3: ~2,8 MB JSON, công cụ tự lưu ra file). Lọc bằng Python: duyệt `children`, lấy `id`, `name`, `type`, `bounds`, `characters` của node `TEXT`. Trên Windows gọi `sys.stdout.reconfigure(encoding='utf-8')`.
  5. `get_screenshot` cần **id frame**; truyền id trang báo "No nodes to export". Ảnh trả về dạng base64 trong JSON (frame Home 360×1731 ở scale 1: ~800 KB), công cụ lưu ra file; giải mã bằng Python (`base64.b64decode` trường `exports[0].base64`) ra PNG.
  6. `save_screenshots` chỉ ghi được **bên trong thư mục làm việc của tiến trình bridge**. Phiên Claude Code chạy từ repo thì đó là repo, dùng `build/figma-draft/` (đã gitignore). Phiên claude.ai qua VM (2026-11-16, đo được) bridge chạy ở `C:\Windows\System32` nên mọi đường dẫn bị từ chối ("outputPath must be inside the MCP server working directory"): dùng `get_screenshot` nhiều node một lần (16 node scale 3 ≈ 5,9 MB JSON, công cụ tự lưu ra file), giải mã base64 từng `exports[i]` rồi `device_commit_files` vào `build/figma-v3/`.
- Ảnh v2 đã lưu ở `E:\SourceTree\IosLauncher\build\figma-draft\` (không nằm trong git). Ảnh v3 (15 thành phần + màn 1.3), 24 icon SVG và JSON section Components đã lưu ở `build\figma-v3\` từ 2026-11-16 (DS3), cũng không nằm trong git.

## 2. Cấu trúc file v3

4 trang: **v3 · Thiết kế mới** (`302:1490`), v2 · Thiết kế (`0:1`), v1 · Bản cũ (`228:2323`), Asset & thử nghiệm (`228:2324`). Chỉ trang v3 đã đọc.

| Section / frame | Node | Nội dung |
|---|---|---|
| v3 · UI | `302:1491` | Home cài đặt (`302:1492`, 360×1731) + 20 màn 1.1–6.3; 2.2 và 5.2 có thêm bản "Popup chọn" |
| v3 · Components | `302:3330` | Toggle, Radio, Chip, Segment, Segmented, Button, Badge, Row, icon, Slider, Page Indicator, Nav Bar, Bottom Sheet, Dialog, Onboarding Top Bar |
| v3 · Onboarding | `302:3673` | **Mới so với v2:** 0.0 Splash, 0.1 Chào mừng (+ Phương án B), 0.2 Chọn kiểu giao diện, 0.3 Chọn hình nền, 0.4 Đặt làm màn hình chính, 0.5 Hoàn tất |
| 0.1 Chào mừng_test ads | `302:3835` | Màn Chào mừng có ô quảng cáo 360×140 |

Danh sách màn và chữ của 1.1–6.3 **giống v2** (đối chiếu chữ 2026-11-16), trừ 6.1 nay vẽ hộp thoại đè lên Home. Khác biệt chính là phong cách hình ảnh: nền tối `#07080E` có 4 mảng màu loang; card kính bo 20–24 (smoothing 0.6, background blur 30, 4 inner shadow, 2 drop shadow, vệt sáng ở góc); ô icon 32 bo 8 có màu riêng từng hàng; hàng cao 56; font Be Vietnam Pro (20 Bold tiêu đề, 14 hàng, 13 giá trị, 12/11 chú thích); nút chính gradient `#3B57E8 → #6440E6` cao 36, bo 18.

## 3. Đối chiếu v3 với app, xếp theo ưu tiên

**Cách tính % hoàn thành:** số mục **chức năng** của màn đã có trong app / tổng mục chức năng trong thiết kế, làm tròn 5%. **Không tính giao diện v3**: từ RS (2026-11-18, `plans/RS-settings-reskin.md`) Cài đặt, Ứng dụng ẩn, Tinh chỉnh kính và hộp thoại Cài đặt đã dựng bằng bộ thành phần v3 (`core/designsystem`); Home, widget, Trung tâm điều khiển chưa (Trung tâm điều khiển chỉ mượn khung Cài đặt). Phần giao diện tính riêng ở hàng 1. Số là ước lượng của Claude từ đọc code, chưa kiểm trên máy.

**Cách xếp ưu tiên:** (1) nền giao diện và trải nghiệm lần đầu, (2) diện mạo cốt lõi của launcher, (3) hoàn thiện Cài đặt, (4) đã quyết để sau hoặc bị Android giới hạn, (5) nhóm 6.x (chủ dự án dời xuống cuối, V4), (6) quảng cáo (chưa làm, V3). Claude đề xuất, chủ dự án duyệt 2026-11-16. Nhóm 6.x vẫn còn việc **bắt buộc trước phát hành**: thay `PLACEHOLDER` ở `about_config.xml` (xem `AGENTS.md`).

Nguồn: đọc code `ui/settings/*` (bảng `SettingsHubLayout`, đăng ký màn ở `SettingsScreens`), `res/values/strings.xml`, `about_config.xml`, `MultiAgent-Workflow.md` mục 10. Không tính thay đổi chưa commit ở thư mục làm việc (widget ảnh G3b) và nhánh `feature/g3c-appwidget-host` chưa merge.

| # | Màn / thành phần | Tính năng theo thiết kế | Đã làm | Chưa làm | Làm ngoài thiết kế | % |
|---|---|---|---|---|---|---|
| 1 | **Components** (`302:3330`) | 15 thành phần (mục 2) làm nền cho mọi màn; token màu, chữ, bo góc, hiệu ứng kính | **DS3 (xong 2026-11-17):** token màu/chữ/khoảng cách/bo góc, font Be Vietnam Pro, bề mặt kính xấp xỉ bằng drawable, 15 thành phần + 24 icon trong `core/designsystem`, catalog debug (`DsCatalogActivity`). Giống Figma ở mức nhìn, chênh tiểu tiết (không blur nền, không bóng đen, inset shadow xấp xỉ bằng gradient: đánh đổi đã chấp nhận). **RS (2026-11-18):** đã áp vào Cài đặt, Ứng dụng ẩn, Tinh chỉnh kính, hộp thoại Cài đặt | Áp vào Home, widget, Trung tâm điều khiển (chưa có plan) | Ô tìm `DsSearchField`, thanh tiến độ, trạng thái disabled/pressed (suy ra từ token, quyết định 5 của DS3) | 96 |
| 2 | **Onboarding** 0.0–0.5 (`302:3673`) | Splash; Chào mừng (chip ngôn ngữ); chọn kiểu giao diện (4 theme); chọn hình nền; đặt làm màn hình chính (2 bước); Hoàn tất (tóm tắt lựa chọn) | **OB ✅ (2026-11-17, `plans/OB-onboarding.md`):** đủ 6 bước theo Figma: splash hệ thống (logo + quầng sáng), 0.1 phương án A, 0.2 bốn thẻ theme (áp theme CF), 0.3 chọn hình nền, 0.4 đặt mặc định (về thẳng 0.5 khi hệ thống dựng Home mới), 0.5 tóm tắt chạm để sửa; cờ `first_run_done`; Bỏ qua giữ phần đã chọn | 0.3 dùng **5 ảnh cài sẵn** (WI 2026-11-18, bỏ ô "Theo giao diện"; ảnh thử Apple) thay thư viện (chờ G3f); nút cuối 0.5 "Vào Cài đặt" (WI). Chip ngôn ngữ 0.1 làm ở LG (2026-11-18, mở màn 1.3) | Đồng hồ thật trên thẻ theme; lệnh debug `OB_RESET` | 90 |
| 3 | **Home cài đặt** (`302:1492`) | Tiêu đề; card "Màn hình chính đang chọn" có ảnh xem trước, nhãn "Chưa đặt làm mặc định", nút "Đặt làm mặc định"; 6 nhóm, 20 mục; giá trị bên phải (Tiếng Việt, °C, Tự động); "Phiên bản 1.0.0" | 6 nhóm đúng thứ tự (G2d); **RS:** giao diện v3, ô icon màu từng hàng, card Launcher hiện tại (ảnh + tên theme, badge + nút khi chưa mặc định); mục chưa có màn mờ "Sắp có"; phiên bản `1.0.0` | Giá trị bên phải của Thời tiết / Vị trí (Ngôn ngữ có từ LG) | Số app đang ẩn ở hàng Ứng dụng ẩn; hàng Trung tâm điều khiển (ô icon tạm) | 85 |
| 4 | **1.1 Màn hình chính mặc định** (`302:1738`) | 4 theme có ảnh xem trước (Liquid Glass 27, Classic 26, Minimal, Dark Pro "MỚI"); "Áp dụng & đặt làm mặc định" | **RS Task 6 (2026-11-18):** đủ theo Figma: 4 thẻ theme của OB 0.2, áp theme CF, mở hộp thoại launcher mặc định | — | Đã mặc định: nút "Áp dụng", ẩn chú thích | 100 |
| 5 | **1.3 Ngôn ngữ** (`302:1856`) | Ô tìm; Theo hệ thống; 9 ngôn ngữ | **LG ✅ (2026-11-18, `plans/LG-languages.md`):** đủ theo Figma: ô tìm (bỏ dấu, theo tên bản ngữ/tên dịch/tag), card radio Theo hệ thống + 9 ngôn ngữ, chạm là đổi; `values/` thành tiếng Anh (dự phòng), 8 ngôn ngữ còn lại máy dịch đủ 530 chuỗi; `locales_config` | Người bản ngữ duyệt bản dịch | Ngôn ngữ app cho Trung tâm điều khiển, thông báo, danh mục tìm cài đặt ở API < 33 | 100 |
| 6 | **2.3 Bộ biểu tượng** (`302:2172`) | Kiểu: Mặc định, Liquid Glass, Tint màu (theo hình nền), Fluffy 3D; hình dạng: Bo góc, Tròn, Vuông, Giọt nước; thanh Kích thước; thanh Bo góc | Mặc định, Tint, 3D | Kiểu Liquid Glass; tint theo màu hình nền; 4 hình dạng (squircle cố định); kích thước; bo góc (mã IS) | Gói icon bên thứ ba (Nova/ADW); 5 màu tint cố định | 35 |
| 7 | **2.1 Liquid Glass** (`302:2020`) | Xem trước; 4 thanh trượt (trong suốt, mờ, khúc xạ, viền sáng); màu ánh kính; áp dụng cho Dock / Widget / Thư mục / Tìm kiếm / Thư viện; tự giảm trên máy yếu | Bật kính theo phần tử ở mức Tùy chỉnh (dock, panel, thanh tìm kiếm, pill, menu icon); mức "Tự động" theo cấu hình máy | Xem trước; 4 thanh trượt; màu ánh kính; chip Widget và Thư viện (mã GV) | Mức kính Tự động/Thấp/Cao/Tùy chỉnh; cảnh báo máy API < 33; kính cho menu nhấn giữ icon và pill | 30 |
| 8 | **2.2 Hình nền** (`302:3232`, popup `302:2090`) | Danh mục (Tất cả, Liquid, Trừu tượng, Thiên nhiên, Tối); tự đổi mỗi ngày; chọn ảnh của bạn; lưới ảnh nhãn "MỚI"; popup đặt cho Màn hình chính / Khóa / Cả hai | Chọn ảnh của bạn; popup đặt Home / Khóa / Cả hai (**RS:** bottom sheet v3); lưới 5 ảnh cài sẵn (**WI**, ảnh thử Apple), chạm → xác nhận → nền ảnh, ô chọn có viền | Thư viện theo danh mục, nhãn MỚI, tự đổi (G3f, chặn: chưa có nguồn ảnh được phép) | Ảnh nền mặc định khi chưa chọn (ảnh thử iOS 27, phải xóa trước phát hành) | 45 |
| 9 | **1.2 Chung** (`302:1800`) | Ẩn thanh điều hướng; tự sắp xếp biểu tượng; hiệu ứng mờ; cỡ chữ linh hoạt + thanh % + xem trước | Tự sắp xếp và mờ **luôn bật** (chưa có công tắc) | Ẩn thanh điều hướng (NB); 2 công tắc; cỡ chữ (TS) | Cỡ lưới; hiện/ẩn tên app (màn đổi tên "Cài đặt chung") | 10 |
| 10 | **5.1 Ứng dụng ẩn** (`302:2648`) | Mô tả; khóa vân tay/PIN; mở danh sách bằng vuốt 2 ngón; ô tìm; danh sách mọi app có công tắc ẩn; "N đang ẩn" | Ẩn khỏi Home, thư viện, tìm kiếm (ẩn từ menu nhấn giữ icon); màn danh sách đã ẩn để bỏ ẩn (**RS:** khung v3, card) | Khóa (HL); cử chỉ 2 ngón; ô tìm; danh sách mọi app kèm công tắc | — | 40 |
| 11 | **3.2 Thư viện ứng dụng** (`302:2334`) | Bật/tắt thư viện; kiểu Thư mục danh mục / Danh sách A–Z; tự phân loại; Gợi ý & Mới thêm; nơi đặt app mới cài | **LO ✅ (2026-11-18, `plans/LO-library-settings.md`):** công tắc bật thư viện; kiểu Thư mục / A–Z (danh sách chia chữ cái + cột chữ cái kéo nhanh) | Tự phân loại, Gợi ý & Mới thêm (đã code, **tạm ẩn** theo chủ dự án); nơi đặt app mới (bỏ, cần lưu dấu "chỉ ở thư viện") | Cột chữ cái kéo nhanh ở A–Z; nhóm mở toàn màn hình mọc từ ô (AP) | 60 |
| 12 | **3.1 Lướt màn hình** (`302:2267`) | Xem trước; 6 hiệu ứng trang; lướt vòng; hiện chấm trang | Công tắc chấm trang (HT) | 6 hiệu ứng + xem trước (PT). Lướt vòng **bỏ** (chủ dự án 2026-11-13: xung đột vuốt qua trang cuối mở Thư viện) | Chấm trang mờ dần theo ngón tay (PL) | 15 |
| 13 | **5.2 Thay đổi biểu tượng** (`302:3294`, popup `302:2697`) | Lưới app có nhãn "Đã đổi"; bottom sheet chọn ảnh từ thư viện, Khôi phục, Lưu | — | Toàn bộ (mã CI); màn chưa đăng ký | — | 0 |
| 14 | **3.3 Cài đặt tìm kiếm** (`302:2414`) | Vuốt xuống; nút tìm trên Dock; tìm trong Ứng dụng, Danh bạ, Cài đặt hệ thống, Tệp, Gợi ý từ web; công cụ tìm kiếm web; xóa lịch sử | Vuốt xuống, nút tìm (công tắc, HT); ứng dụng, danh bạ, cài đặt hệ thống; xóa lịch sử | Tìm tệp (để sau, FD7); gợi ý web; chọn công cụ tìm kiếm | Tìm danh bạ theo số (CP); phím tắt app trong kết quả; Gợi ý & app gần đây | 65 |
| 15 | **1.4 Thời tiết** (`302:1900`) | Thẻ hiện tại + theo giờ; °C/°F; địa điểm theo dõi + thêm; thông báo dự báo hằng ngày, ngày mai, cảnh báo thời tiết xấu | °C/°F (WU); tối đa 3 địa điểm (WX); thông báo hằng ngày và ngày mai (WN); **RS:** card xem trước (tên vị trí hiện tại, số liệu mẫu), °C/°F dạng segmented, thứ tự theo Figma | Số liệu thật cho card xem trước; cảnh báo thời tiết xấu (bỏ lúc này) | Widget thời tiết, mỗi widget chọn địa điểm; sheet thời tiết mở từ widget | 75 |
| 16 | **1.5 Vị trí** (`302:1971`) | Ghi chú riêng tư; trạng thái quyền + "Cấp quyền"; Tự động (GPS) / Chọn thủ công + tìm thành phố | Đủ các mục (WX); **RS:** Tự động / Thủ công là hai hàng radio có mô tả như Figma; "Tự động" dùng vị trí thô, cập nhật im lặng khi đi xa > 5 km | GPS chính xác (đang vị trí thô, cố ý) | Nút "Cập nhật vị trí" | 90 |
| 17 | **5.3 Thay đổi tên ứng dụng** (`302:2776`) | Danh sách app, hiện "Tên gốc"; sửa tại chỗ; Khôi phục; Lưu | Danh sách, tên gốc, khôi phục, lưu (RN) | Sửa tại chỗ trong hàng (app sửa qua hộp thoại) | — | 90 |
| 18 | **4.2 Hiệu ứng ứng dụng** (`302:2580`) | Mở/đóng app: Phóng từ biểu tượng, Mờ dần, Trượt lên, Bật nảy; tốc độ; thu nhỏ khi chạm; rung nhẹ khi mở | — | Toàn bộ (mã AE). **Giới hạn Android:** chỉ làm được hoạt ảnh **mở** bằng API công khai; đóng về icon và Bật nảy do hệ thống quyết (`plans/PL-dots-and-launch.md`) | — | 0 |
| 19 | **4.1 Hiệu ứng chuyển cảnh** (`302:2522`) | Kiểu Mượt / Mờ dần / Trượt / Phóng to / Không có cho thư mục, tìm kiếm, thư viện, Cài đặt nhanh; tốc độ; giảm chuyển động | — (lò xo cố định) | Toàn bộ (mã TR) | — | 0 |
| 20 | **3.4 Huy hiệu thông báo** (`302:2459`) | Banner xin quyền đọc thông báo; hiện huy hiệu; kiểu Số / Chấm; bật tắt theo app | — | Toàn bộ; chưa có `NotificationListenerService`. **Để sau** (FD7) | — | 0 |
| 21 | **6.3 Chính sách bảo mật** (`302:2899`) | Nội dung tải từ web hiện trong app (dữ liệu thu thập, quyền, quảng cáo, liên hệ, ngày cập nhật); "Mở trong trình duyệt" | Mở trang chính sách bằng trình duyệt (AB5) | Hiện nội dung trong app; **URL thật** (đang `PLACEHOLDER`, chặn `bundleRelease`) | — | 50 |
| 22 | **6.1 Đánh giá ứng dụng** (`302:2958`) | Hộp thoại chọn sao; 4–5 sao mở Google Play, 1–3 sao mở form góp ý; "Để sau" / "Gửi đánh giá" | Hộp thoại, "Để sau", "Gửi đánh giá" mở Google Play | Chia theo số sao và form góp ý (AB3: sao chỉ minh họa, mọi đánh giá mở Play) | — | 80 |
| 23 | **6.2 Tải ứng dụng khác** (`302:2824`) | Tên nhà phát triển; 1 app nổi bật; danh sách app có mô tả, điểm, nút Tải | Màn đủ bố cục, mở Google Play; ẩn hẳn khi `about_more_apps_enabled` = false | Nội dung thật (đang `PLACEHOLDER`); mô tả và điểm từng app | — | 75 |
| 24 | **0.1 Chào mừng_test ads** (`302:3835`) | Màn Chào mừng có ô quảng cáo | — | **Chưa làm quảng cáo** (V3); chưa có SDK quảng cáo | — | 0 |

**Tổng:** trung bình 24 hàng ~36% (tăng từ ~32% nhờ hàng 1 lên 96 sau DS3); riêng Home + 20 màn cài đặt ~36%. Màn chưa đăng ký (hiện "Sắp có"): 3.2, 3.4, 4.1, 4.2, 5.2 (1.1 xong ở RS Task 6).

**App có nhưng không có màn nào trong thiết kế:** widget đồng hồ, lịch, thời tiết (G2c, G3e), widget ảnh (G3b, đang làm); chế độ sửa Home, thư mục, kéo-thả; menu nhấn giữ icon kiểu kính (SM); dock tự chọn app mặc định (DD); cache nhãn và icon (LC, IC). Thiết kế cần bổ sung màn cho các phần này nếu muốn đồng bộ phong cách.

**Bẫy tên:** `ui/library/AppLibraryActivity.kt` là màn **Ứng dụng ẩn**, không phải Thư viện ứng dụng.

## 4. Quyết định của chủ dự án (2026-11-16)

| # | Quyết định |
|---|---|
| V1 | **v3 là thiết kế chính thức.** Còn thiếu một số màn, đang bổ sung; phong cách khả năng không đổi. FD2 hết hiệu lực |
| V2 | **Onboarding làm đủ 6 bước** (0.0–0.5). Màn Chào mừng dùng **phương án A**, khác biệt chỉ là asset |
| V3 | **Chưa làm quảng cáo** (frame "0.1 Chào mừng_test ads") |
| V4 | **Thứ tự ưu tiên mục 3 duyệt**, riêng nhóm 6.x (6.3, 6.1, 6.2) dời xuống cuối, ngay trước màn test ads |

## 5. Quyết định của chủ dự án (2026-11-12, bản nháp v2)

| # | Quyết định |
|---|---|
| FD1 | **G2d làm trước**: home cài đặt + mỗi nhóm một màn, chuyển hàng đang có vào, chưa thêm tính năng mới |
| FD2 | **Hết hiệu lực 2026-11-16 (V1).** ~~Giữ kiểu hiển thị hiện tại của Cài đặt; chỉ đổi cấu trúc. Chờ thiết kế chính thức rồi mới làm phong cách (kính tối, icon màu từng hàng)~~ |
| FD3 | **Theme (1.1) = bộ cài sẵn**: một lần chọn áp cùng lúc hình nền, kiểu icon, mức kính, hiệu ứng đang có. Không làm bố cục Home riêng cho từng theme |
| FD4 | **Giữ các tính năng app có mà bản nháp không có**, đặt: lưới + hiện tên → Chung; icon pack → Bộ biểu tượng; mức kính Force/Tùy chỉnh → Liquid Glass; nút tìm danh bạ và phím tắt → Cài đặt tìm kiếm |
| FD5 | **6.x dùng chỗ giữ chỗ** trong `strings.xml` (link Play, URL chính sách, email góp ý, tên nhà phát triển), có bước kiểm trước phát hành |
| FD6 | **Làm trong đợt này:** ẩn thanh điều hướng; thời tiết nhiều thành phố + thông báo (dùng khóa OWM hiện tại, giới hạn số lượt gọi; C15 vẫn để trước phát hành); ngôn ngữ **VI + EN trước**, Claude dịch và ghi rõ là bản máy dịch |
| FD7 | **Để sau:** huy hiệu thông báo, tìm tệp, thư viện hình nền (G3f) |
| FD8 | **Thứ tự:** G2d → việc nhanh → việc trung bình (mục 6) |
| FD9 | `versionName` đổi thành `1.0.0` |
| FD10 | ~~Chạy theo `MultiAgent-Workflow.md`: điều phối + tối đa 4 agent mỗi đợt; chủ dự án merge~~ → **Từ 2026-11-16 chạy theo [Workflow.md](Workflow.md)**: một tính năng một lúc, Claude trực tiếp làm, chủ dự án chọn tính năng tiếp theo trong bảng mục 6 và tự commit |

## 6. Mã tính năng và đợt

Mã do Claude (vai điều phối lúc đó) đặt 2026-11-12. Trạng thái tới 2026-11-16 ở `MultiAgent-Workflow.md` mục 9 và 10; **từ 2026-11-16 trạng thái ghi thẳng ở bảng này** (dấu ✅ kèm ngày) và ở `project_structure.md` mục 9. Cột "Đợt" giờ chỉ là nhóm độ lớn, không phải lượt chạy song song.

| Đợt | ID | Tính năng | Màn |
|---|---|---|---|
| **FD-0** ✅ | **G2d** `settings-hub` (xong 2026-11-12, PR #47) | Home cài đặt + màn con theo nhóm, chuyển hàng đang có, số phiên bản `1.0.0` | Home, 1.2, 2.1, 2.2, 2.3, 3.3, 5.1 |
| **FD-1** ✅ (xong 2026-11-13, PR #49–#52) | AB `about-section` | Phiên bản, chính sách bảo mật, hộp thoại đánh giá, ứng dụng khác (chỗ giữ chỗ) | 6.1–6.3 |
| | RN `rename-apps` | Đổi tên app, khôi phục tên gốc | 5.3 |
| | WU `weather-units` | °C/°F | 1.4 (một phần) |
| | HT `home-toggles` | Công tắc chấm trang, vuốt xuống tìm kiếm, nút tìm kiếm; lướt vòng | 3.1 (một phần), 3.3 (một phần) |
| **FD-2** (trung bình) | CI `custom-icons` | Đổi icon bằng ảnh | 5.2 |
| | IS `icon-shape-size` | Hình dạng và kích thước icon | 2.3 |
| | GV `glass-sliders` | Thanh trượt kính, màu ánh kính, áp dụng cho | 2.1 |
| | AE `app-open-effects` | Hiệu ứng mở app (trong giới hạn mục 4), thu nhỏ khi chạm, rung | 4.2 |
| | LO `library-options` | Tùy chọn Thư viện ứng dụng | 3.2 |
| | HL `hidden-apps-lock` | Khóa vân tay/PIN cho Ứng dụng ẩn | 5.1 |
| | TS `text-size` | Cỡ chữ tên app (linh hoạt theo máy, thanh %) | 1.2 |
| **FD-3** (lớn) | TH `theme-presets` | Theme = bộ cài sẵn (FD3) | 1.1 |
| | NB `nav-bar-hide` | Ẩn thanh điều hướng | 1.2 |
| | WX `weather-cities` ✅ (2026-11-13, PR #55) | Nhiều thành phố (tối đa 3); màn 1.5 Vị trí. Thông báo làm ở WN (#58, 2026-11-16). Còn thiếu ở 1.4: thẻ "hiện tại + theo giờ"; cảnh báo thời tiết xấu bỏ lúc này | 1.4, 1.5 |
| | LG `languages` | Chọn ngôn ngữ, `values-en`, `locales_config` | 1.3 |
| | PT `page-transitions` | Hiệu ứng lướt trang | 3.1 |
| | TR `transitions` | Kiểu và tốc độ chuyển cảnh, giảm chuyển động | 4.1 |
| Để sau | — | Huy hiệu (3.4), tìm tệp (3.3), thư viện hình nền (2.2, G3f) | |

Từ 2026-11-16 làm nối tiếp từng tính năng ([Workflow.md](Workflow.md)), thứ tự trong cùng nhóm do chủ dự án chọn. (Trước đó: mỗi đợt tối đa 4 nhánh không chung file độc quyền, `MultiAgent-Workflow.md` mục 2 và 5.) **DS3** ✅ (2026-11-17): hàng 1 mục 3 (Components), plan `plans/DS3-design-system-v3.md`; chưa áp vào màn nào.
