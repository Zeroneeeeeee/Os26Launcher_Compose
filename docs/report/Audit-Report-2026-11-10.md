# Báo cáo rà soát mã nguồn — 2026-11-10

**Trạng thái (2026-11-12): HOÀN TẤT.** 38/39 mục đã sửa, kiểm và merge (FX1–FX8, PR #31–#39; docs #37, #41, #42). Mục còn lại duy nhất: **C15** (khóa OpenWeatherMap trong APK), chủ dự án quyết hoãn tới trước khi phát hành, đã ghi vào mục "Trước khi phát hành lên cửa hàng" của `AGENTS.md`.

**Bản rà:** `main` tại `b1af810` (sau commit reformat). Kiểm trên máy bằng APK debug.
**Máy:** Vsmart Live 4 (Android 11, 1080x2340, mức kính Ép cao, Os26Launcher **không** là launcher mặc định).
**Cách làm:** chia mã nguồn thành 5 vùng (dữ liệu bố cục; app/icon/cache; UI Home và kéo-thả; kính/ảnh nền/widget/thời tiết; tìm kiếm/cài đặt/App Library), mỗi vùng một người rà đọc code và lần theo luồng gọi. Điều phối đọc lại các mục nặng, kiểm thêm manifest, cấu hình build, log, và kiểm trên Live 4 các mục có thể tái hiện an toàn.
**Dữ liệu máy:** sao lưu `databases/`, `files/`, `no_backup/` (8 file, so khớp md5) trước khi kiểm; mỗi lần sửa dữ liệu để tái hiện đều trả lại từ bản sao lưu và so khớp lại cả 8 file. Chế độ tối của máy trả về như cũ (bật).
**Phạm vi:** chỉ báo cáo, **chưa sửa gì trong app**.
**Đợt 2 (từ 15:10):** chủ dự án đặt Os26Launcher làm launcher mặc định trên Live 4; đã sao lưu lại (bản 2) và kiểm thêm các ca cần nút Home thật, shortcut, bộ nhớ khi dùng lâu. Từ đợt này không chạy lại ca làm sập (A1) vì sẽ khóa màn hình chính của máy.
**Đợt 3 (từ 15:28, chủ dự án giao):** kiểm B9, A4, B6. Đổi ngôn ngữ hệ thống bằng một công cụ nhỏ chạy qua `app_process` (gọi `updatePersistentConfiguration` như màn Ngôn ngữ của Cài đặt), trả lại `vi-VN` sau khi kiểm. A4 và B6 dùng một app thử tự dựng (`dev.audit.icontest`, không có code, chỉ có icon; v1 đỏ, v2 xanh), không đụng app của chủ dự án; đã gỡ sau khi kiểm. Sao lưu bản 3 trước khi cài app thử, trả DB sau khi gỡ, 8 file khớp.

Nhãn kiểm chứng dùng trong báo cáo (theo `AGENTS.md`):
- **Đo được** — đã tái hiện hoặc đo trên Live 4 hôm nay.
- **Đọc code** — lần theo code thấy chắc chắn, chưa tái hiện trên máy.
- **Suy luận** — dựa trên hành vi framework hoặc thời điểm, chưa kiểm chứng.

## Kết luận

- **37 mục**: 5 cao, 12 trung bình, 20 thấp. 11 mục đã tái hiện hoặc đo trên Live 4 (A1, A2, A3, A4, B2, B6, B9, B10, C16, C19, C20); C1 đã thử nhưng không thấy trên Live 4.
- **Nặng nhất — A1:** chọn một ảnh nền rất lớn (ảnh toàn cảnh) làm launcher **sập ngay lúc mở, lần nào cũng sập**. Nếu Os26Launcher là launcher mặc định, người dùng kẹt tới khi xóa dữ liệu app. Đã tái hiện: `Canvas: trying to draw too large(144000000bytes) bitmap`.
- **A2:** vào rồi thoát edit mode, launcher tiếp tục chiếm **~8% một nhân CPU mãi mãi**, kể cả khi đã ra ngoài (đo được: 247 tick/30 s ở nền, so với 2 tick/60 s lúc bình thường). Mỗi lần dựng lại Home trong edit mode còn giữ lại Activity cũ, mỗi bản **~40 MB bitmap**: sau một lúc dùng bình thường (đổi cài đặt từ edit mode) đo được 3 Activity và Native Heap 113 MB, khởi động lại chỉ còn 27 MB.
- **A3, A4:** hai lỗi khi app khác được cập nhật: đổi tên activity khởi động làm mất ô dock/vị trí và để lại ô ma (đã mô phỏng trên máy); icon trên Home không đổi theo icon mới.
- Những phần **đã kiểm và ổn**: CPU lúc rảnh, bộ nhớ (151 MB PSS), toàn vẹn DB, log không chứa dữ liệu cá nhân, DataStore, khớp tiếng Việt không dấu, hủy kéo-thả, quyền và try/catch quanh intent. Xem mục cuối.

## Checklist theo ưu tiên (cao → thấp)

### Cao
- [x] **A1** — Ảnh nền đã chọn không thu về cỡ màn hình → sập lúc mở, lặp lại mãi (crash · **đo được**) — ✅ FX1 #31, kiểm lại Live 4 2026-11-11, đã merge
- [x] **A2** — Animator rung của edit mode không bao giờ dừng → tốn CPU/pin mãi, rò Activity khi dựng lại (pin, rò bộ nhớ · **đo được**) — ✅ FX2 #33, kiểm lại Live 4 2026-11-11, đã merge
- [x] **A3** — App đổi tên activity khởi động khi cập nhật → ô ma trên Home/dock/thư mục, icon nhân đôi (mất dữ liệu bố cục · **đo được** bằng mô phỏng DB) — ✅ FX3 #32, kiểm lại Live 4 2026-11-11, đã merge
- [x] **A4** — Icon trên Home, dock, thư mục, App Library, tìm kiếm không đổi khi app cập nhật icon (lỗi · **đo được**) — ✅ FX5 #36 (Home, tìm kiếm đổi màu ngay sau `install -r`), kiểm lại Live 4 2026-11-11, đã merge
- [x] **A5** — Ảnh nền đã chọn giữ trong RAM to gấp 4–10 lần cần thiết (hiệu năng, bộ nhớ · **đọc code**) — ✅ FX1 #31, kiểm lại Live 4 2026-11-11, đã merge

### Trung bình
- [x] **B1** — `ApplicationScope` không có handler: lỗi lạ ở thời tiết hoặc ở flow bố cục làm sập cả launcher (crash · **đọc code**) — ✅ FX3 #32 (flow bố cục) + FX6 #35 (handler của scope app, thời tiết): lỗi thử nghiệm không còn làm sập process, kiểm lại Live 4 2026-11-11, đã merge
- [x] **B2** — Đổi sáng/tối không nạp lại ảnh nền mặc định (lỗi · **đo được**) — ✅ FX1 #31, kiểm lại Live 4 2026-11-11, đã merge
- [x] **B3** — Thu nhỏ lưới đua với cài app mới → icon nằm ngoài lưới → thả icon làm sập (crash, hiếm · **đọc code**) — FX3 #32: đã sửa, đọc code + review độc lập; chủ dự án kiểm đổi lưới khi duyệt PR — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (đua giữa đổi cỡ lưới và cài app, không ép được bằng thao tác thường)
- [x] **B4** — Gỡ nhiều app liền nhau → app đã gỡ được đặt lại thành ô ma (mất dữ liệu bố cục · **đọc code**) — FX3 #32: đã sửa, đọc code + review độc lập (đua không ép được trên máy) — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (đua khi gỡ hàng loạt, không ép được bằng thao tác thường)
- [x] **B5** — Trang và item quan sát bằng hai flow riêng → UI nhận bố cục nửa cũ nửa mới sau mỗi lần ghi (lỗi, hiệu năng · **đọc code**) — ✅ FX3 #32, kiểm lại Live 4 2026-11-11, đã merge
- [x] **B6** — State đổi giữa lúc vuốt trang → pager giật về trang cũ hoặc bỏ hoạt ảnh (lỗi UI · **đo được**) — ✅ FX7 #38 (không còn giật về giữa lúc vuốt), kiểm lại Live 4 2026-11-11, đã merge
- [x] **B7** — Cache icon trong RAM có thể khóa lại icon cũ ngay sau khi app cập nhật (lỗi · **đọc code**) — FX5 #36: đã sửa (khóa theo dấu phiên bản), đọc code — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (đua lúc app cập nhật, không ép được bằng thao tác thường)
- [x] **B8** — Kiểu icon Tô màu sửa drawable dùng chung của hệ thống (không `mutate()`) → icon kiểu khác bị nhuộm màu (lỗi · **suy luận**) — FX5 #36: đã sửa (`mutate()`), đọc code — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (cần xóa cache icon đúng lúc, không tái hiện bằng thao tác thường)
- [x] **B9** — Đổi ngôn ngữ hệ thống không nạp lại nhãn app và thứ tự sắp xếp (lỗi · **đo được**) — ✅ FX5 #36 (vi↔en, nhãn đổi trong 6 s không cần khởi động lại), kiểm lại Live 4 2026-11-11, đã merge
- [x] **B10** — Nút Home không đóng edit mode, bảng chọn widget, bảng thời tiết (lỗi · **đo được**; chủ dự án 2026-11-11: Home và rời launcher đều thoát edit mode) — ✅ FX2 #33, kiểm lại Live 4 2026-11-11, đã merge
- [x] **B11** — Tìm kiếm dựng lại toàn bộ chỉ mục nhiều lần mỗi phiên; chậm khi có nhiều danh bạ (hiệu năng · **đọc code**, chi phí ngoại suy) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **B12** — Hàng "Launcher mặc định" không làm gì sau khi người dùng chọn "Không hỏi lại" (lỗi · **suy luận**) — ✅ FX6 #35: kết quả hủy tức thì (< 352 ms) mở màn Ứng dụng màn hình chính; kiểm lại Live 4 2026-11-11 bằng role giả. Trên Live 4 "Không hỏi lại" không chặn hộp thoại nên ca thật không tái hiện được, đã merge

### Thấp
- [x] **C1** — App Library chớp "Đang tải" và dựng lại mọi ô khi quay về Home sau hơn 5 giây (UI, hiệu năng · **đọc code**; đã thử trên Live 4, không thấy) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C2** — Cache icon trên đĩa xóa icon trang Home trước tiên khi đầy (hiệu năng khởi động · **đọc code**) — ✅ FX5 #36 (LRU đĩa giữ icon Home), kiểm lại Live 4 2026-11-11, đã merge
- [x] **C3** — Không trả bộ nhớ khi hệ thống thiếu RAM; cache blur giữ ảnh nền cũ (bộ nhớ · **đọc code**) — ✅ FX1 #31, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C4** — Nhập ảnh nền bỏ qua kết quả ghi file; Home có thể không nạp ảnh mới (lỗi · **đọc code**) — FX1 #31: đã sửa, chưa kiểm trên máy (chủ dự án kiểm nhập ảnh khi duyệt PR) — ✅ chủ dự án kiểm tay trên Live 4 2026-11-12: đạt (chọn ảnh rồi Back ngay, Home hiện ảnh mới)
- [x] **C5** — Ở mức kính CLASSIC, dock blur ảnh nền lần thứ hai trên luồng riêng (hiệu năng · **đọc code**) — ✅ FX1 #31, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C6** — Danh bạ ở lại trong RAM và vẫn được đọc lại khi rời launcher lúc ô tìm kiếm còn mở (riêng tư, hiệu năng · **đọc code**) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C7** — Truy vấn số điện thoại chạy mỗi phím gõ và không hủy được (hiệu năng · **đọc code**) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C8** — Mỗi hàng shortcut trong tìm kiếm nạp tới 4 icon để dùng 1 (hiệu năng · **đọc code**) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C9** — Danh mục Cài đặt và chỉ mục tìm kiếm dựng ngay lúc khởi động, kể cả khi không mở tìm kiếm (khởi động · **đọc code**) — ✅ FX4 #34, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C10** — Hồ sơ công việc: icon không có huy hiệu, thêm/bỏ hồ sơ không được theo dõi (UX, độ bền · **đọc code** / **suy luận**) — ✅ FX5 #36 (huy hiệu hồ sơ công việc; gỡ hồ sơ xóa đủ 30/30 hàng), kiểm lại Live 4 2026-11-11, đã merge
- [x] **C11** — Mọi gói icon từng chọn ở lại bộ nhớ tới khi tắt process (rò bộ nhớ · **đọc code**) — FX5 #36: đã sửa, đọc code (máy không có hai gói icon) — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (cần cài hai gói icon, Live 4 không có)
- [x] **C12** — Nhấn giữ hai app trong cùng thư mục liền nhau có thể hiện shortcut của app trước (lỗi, hiếm · **đọc code**) — FX7 #38: đã sửa, đã merge 2026-11-12; chủ dự án kiểm khi duyệt PR — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (cần shortcut nạp chậm hơn một lần nhấn giữ)
- [x] **C13** — Tên thư mục đang gõ bị mất khi Activity dựng lại (mất dữ liệu nhỏ · **đọc code**) — ✅ FX2 #33, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C14** — Mở menu nhấn giữ lúc icon vừa thả còn đang đáp → icon đó tàng hình (lỗi, hiếm · **đọc code**) — FX7 #38: đã sửa, đã merge 2026-11-12; chủ dự án kiểm khi duyệt PR — ✅ đóng 2026-11-12 theo chủ dự án: đã sửa và đã merge, kiểm bằng đọc code + review độc lập (cần mở menu đúng lúc icon đang đáp (~0,3 s))
- [ ] **C15** — Khóa OpenWeatherMap nằm nguyên văn trong APK (bảo mật · **đọc code**; **hoãn** tới trước khi phát hành, chủ dự án 2026-11-11)
- [x] **C16** — Rung edit mode tốn ~50% một nhân CPU trên Live 4 khi đang rung (hiệu năng · **đo được**) — đóng sau khi đo (FX2 #33): ticker chung không giảm CPU (trung vị 428 vs 428 tick/10 s), giữ animator từng ô
- [x] **C17** — Mỗi lần ghi bố cục chạy hai lượt assemble và render (hiệu năng · **đọc code**, đi cùng B5) — ✅ FX3 #32, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C18** — Phiên kéo bỏ qua ngón thứ hai: nhấc ngón đầu, icon nhảy sang ngón kia (UI · **đọc code**) — FX7 #38: đã sửa, đã merge 2026-11-12; chủ dự án kiểm hai ngón khi duyệt PR — ✅ chủ dự án kiểm tay trên Live 4 2026-11-12: đạt (kéo icon, thêm ngón thứ hai, nhấc ngón đầu: icon thả đúng chỗ ngón đầu)
- [x] **C19** — Mở bảng chọn widget trong edit mode thì thanh trạng thái hiện lại, đè lên thanh nút edit mode (UI · **đo được**) — ✅ FX2 #33, kiểm lại Live 4 2026-11-11, đã merge
- [x] **C20** — App mới lấp vào ô trống giữa trang; gỡ app đó thì cả trang bị dồn, icon người dùng xếp bị xê dịch (thiết kế · **đo được**; chủ dự án 2026-11-11: app mới đặt sau icon cuối cùng) — ✅ FX3 #32, kiểm lại Live 4 2026-11-11, đã merge

### Phát hiện sau đợt 1 (chủ dự án, 2026-11-11)
- [x] **D1** — Nền của kính lệch so với mảng ảnh nền phía sau ở mép widget với ảnh nền khác tỉ lệ màn (ảnh 1632x2912 của chủ dự án). **Đính chính 2026-11-11:** các lần đo lệch (k ≈ 1,2, mép phải widget −54…−69 px) chạy trên bản build CŨ (trước FX1) do điều phối cài nhầm; máy chủ dự án cũng đang chạy bản cũ đó. Trên `main` thật (723affd, đã có FX1 cắt ảnh theo tỉ lệ màn): lưới 1632x2912 khớp 0 px ở mọi góc widget qua 6 lần khởi động lạnh, ảnh của chủ dự án khớp 0…−3 px ở mép phải. ✅ Đã sửa bởi FX1 #31. FX8 vẫn ánh xạ kính theo `imageMatrix` (phòng thủ) và làm đều độ mờ
- [x] **D2** — Vuốt nhanh từ trang cuối sang App Library không mượt (chủ dự án; số đo đầu tiên chạy nhầm trên bản build cũ). **Đo lại trên `main` 723affd (2026-11-11, log FX7D2):** (H4) lần đầu hiện Library, dựng lại toàn bộ ô trên luồng chính đúng lúc chuyển: `tookMs=223`; (H3) tốc độ ngón tay không truyền vào hoạt ảnh: vuốt 30 ms `vx=25000 px/s` nhưng hoạt ảnh `8347 px/s` (khựng), vuốt 80 ms `vx=1875` nhưng hoạt ảnh `8330` (giật); (H1) cử chỉ khi pager còn trượt không thử được bằng adb (mỗi lệnh `input` tốn 0,3–0,6 s). Sửa: FX8 #39 dựng ô Library sẵn lúc rảnh (đã đo: không dựng lại khi hiện, ô vẽ kính ngay khung đầu); FX7 #38 truyền vận tốc vào lò xo (H3) và nhận cử chỉ khi pager đang trượt về trang cuối (H1). **Chủ dự án 2026-11-11: chưa đạt** ở tốc độ thật (trang 1 → Library dưới 2 s): trên `main` lần vuốt đến khi pager còn trượt bị từ chối, phải vuốt thêm. FX7 vòng 2 sửa (nhận cử chỉ theo trang đích lúc đang trượt, mang vận tốc ngón tay). **Đo lại 2026-11-12** bằng công cụ bơm cử chỉ có mốc thời gian như ngón tay thật (công cụ cũ làm dồn mốc thời gian khi app bận nên báo "mất cú vuốt" giả): FX7 vòng 2 mở Library ở lần vuốt thứ 4 (lý tưởng) trong 18/18 lần. Vòng 3 (bù cú vuốt) không cần, hoàn tác. FX7 vòng 4: hoạt ảnh Library ngắt được giữa chừng; vuốt qua lại trang cuối ↔ Library 18/18, trang 1 → Library 4 lần vuốt 18/18 (`main` 0/18); chủ dự án thử trên Live 4 và đồng ý 2026-11-12. ✅ FX7 #38 + FX8 #39, đã merge

Đường dẫn file dưới đây tính từ `app/src/main/java/gambi/global/os26launcher/`. Số dòng ứng với `b1af810`.

---

## Mức cao

### A1. Ảnh nền đã chọn không thu về cỡ màn hình → launcher sập lúc mở, lặp lại mãi

- **Mức:** cao (nặng nhất trong báo cáo). **Loại:** crash. **Nhãn:** đo được.
- **Vị trí:** `data/backdrop/PickedImageSource.kt:149-155` (`decodeScaled`), `:41-44` (`load`), `:50-67` (`import` lưu ảnh quá cỡ); vẽ ở `ui/home/controller/BackdropController.kt:61`.
- **Chuyện gì xảy ra:** `decodeScaled` chỉ dùng `inSampleSize` (lũy thừa 2) và dừng ngay khi một cạnh sắp nhỏ hơn màn hình; không có bước thu nhỏ cuối như `DefaultBackdropSource.kt:61-69`. Ảnh quá cỡ được lưu thành `files/backdrop_image` và giải mã lại đúng cỡ đó mỗi lần mở. Android từ chối vẽ bitmap trên 100 MB (khoảng 26,2 MP ARGB_8888) và ném `RuntimeException` trên luồng chính. Process khởi động lại, `HomeViewModel.loadBackdropFromDisk` nạp lại cùng file → sập lại. Vì là app HOME, người dùng kẹt tới khi xóa dữ liệu app.
- **Ví dụ:** ảnh toàn cảnh 12000x3000 trên Live 4 (vùng Home 1080x2100): 3000/2 = 1500 < 2100 nên `sample = 1` → 36 MP ≈ 144 MB. Ảnh 108 MP ngang trên máy 1080x2400 cũng vượt ngưỡng.
- **Đã tái hiện (Live 4, 2026-11-10):** thay `files/backdrop_image` bằng JPEG 12000x3000 (782 KB), mở Home → `FATAL EXCEPTION: main … Canvas: trying to draw too large(144000000bytes) bitmap` tại `RecordingCanvas.throwIfCannotDraw` ← `BitmapDrawable.draw`. Đã trả file cũ, md5 khớp.
- **Liên quan:** `core/glass/BlurredBackdropCache.kt:68-73` chỉ bắt `Exception`; `OutOfMemoryError` trên luồng `GlassBlur` cũng giết process.
- **Hướng sửa:** sau khi giải mã, thu về cỡ "cover" bằng `max(reqW/w, reqH/h)` như `DefaultBackdropSource.load`, ở cả `import` lẫn `load` (file cũ quá cỡ trên máy người dùng cũng được chữa). Thêm trần pixel (vd ≤ 2 lần số pixel màn hình). Bắt `OutOfMemoryError` khi nhập ảnh và báo lỗi; `BlurredBackdropCache` bắt `Throwable`. Sửa A1 cũng xong A5.

### A2. Animator rung của edit mode không bao giờ dừng → tốn CPU và pin mãi, rò Activity khi dựng lại

- **Mức:** cao. **Loại:** pin, rò bộ nhớ. **Nhãn:** đo được (CPU, số Activity); cơ chế đọc code.
- **Vị trí:** `ui/home/view/Jiggler.kt:26-40` (mỗi ô một `ValueAnimator` lặp vô hạn, listener giữ view); `ui/home/view/IconGridView.kt:728-744` (`onDetachedFromWindow` không gọi `jiggler.stop`, chỉ `setEditMode(false)` ở `:397-405` dừng); `ui/home/view/DockView.kt:370-378` cũng thiếu. `FolderPanelView.kt:553-559` thì có dừng khi detach, nên đây là chỗ sót.
- **Chuyện gì xảy ra:**
  - Trang nằm trong cache của RecyclerView (đã vuốt qua) bị đưa vào pool mà không bind lại, nên `setEditMode(false)` không tới được → animator của trang đó chạy tiếp sau khi thoát edit mode.
  - Activity bị hủy khi đang ở edit mode (đổi sáng/tối, đổi kiểu icon/mức kính qua `RecreateGuard`) → animator của trang và dock cũ vẫn được handler hoạt ảnh tĩnh giữ → cả `HomeActivity` cũ không được thu hồi.
  - Android 11 không tạm dừng animator khi app ở nền.
- **Đo trên Live 4 (debug, kính Ép cao):**

  | Lúc đo | CPU của process |
  |---|---|
  | Home bình thường, rảnh | 5 tick / 15 s (mỗi tick 10 ms) |
  | Ở nền, bình thường | 2 tick / 60 s |
  | Đang rung (edit mode) | 501 tick / 10 s ≈ 50% một nhân (xem C16) |
  | Đã thoát edit mode (Back), Home rảnh | 253 tick / 30 s ≈ 8% một nhân, toàn bộ ở luồng chính, 0 khung hình được vẽ |
  | Sau đó bấm Home ra launcher hệ thống | 247 tick / 30 s ≈ 8% một nhân, kéo dài tới khi process bị tắt |

  Kịch bản: vào edit mode (nhấn giữ khe giữa hai ô), vuốt sang trang 2 rồi về, thoát edit mode.
  Rò Activity: vào edit mode rồi đổi chế độ tối hai lần → `dumpsys meminfo` báo `Activities: 3` (trước đó 1), vẫn 3 sau khi thử ép GC (`kill -10` qua `run-as`; chưa xác nhận GC đã chạy). PSS 151 → 163 MB.
  Đối chứng (đợt 2): **ngoài** edit mode, đổi chế độ tối hai lần → vẫn `Activities: 1`, Native Heap không tăng. Vậy rò chỉ xảy ra khi dựng lại lúc đang ở edit mode.
  Trong lúc dùng thật (đợt 2, sau khi chủ dự án đặt làm launcher mặc định và đổi vài cài đặt; nút "Cài đặt" chỉ có trong edit mode, đổi kiểu icon/mức kính thì `RecreateGuard` dựng lại Home): process có `Activities: 3`, **Native Heap 113 MB**, PSS 186 MB. Khởi động lại sạch: 1 Activity, Native Heap 27 MB. Mỗi Activity rò giữ khoảng 40 MB bitmap.
- **Hướng sửa:** trong `IconGridView` và `DockView`: dừng jiggler ở `onDetachedFromWindow`, chạy lại ở `onAttachedToWindow` nếu `editMode` (trang trong cache được gắn lại mà không bind). Tốt hơn: một ticker chung cho cả lưới thay vì 24 animator (cũng giảm C16). Sau khi sửa, đo lại đúng bảng trên.

### A3. App đổi tên activity khởi động khi cập nhật → ô ma, mất vị trí, icon nhân đôi

- **Mức:** cao. **Loại:** mất dữ liệu bố cục. **Nhãn:** đo được bằng mô phỏng DB; cơ chế đọc code.
- **Vị trí:** `data/layout/LayoutReconciler.kt:189-206` (`catchUp` chỉ hỏi package còn cài không), `:656-661` (`placeNew` so cả `ComponentKey`), `:627-636` (giới hạn dock đếm cả hàng ma), `:328-335`, `:364-365` (đếm thư mục theo hàng DB); `data/layout/LayoutRepositoryImpl.kt:88` (chỉ REMOVED đụng DB), `:228` (`assemble` bỏ hàng không có app sống).
- **Chuyện gì xảy ra:** app P lưu `className = .SplashActivity`; bản cập nhật đổi activity khởi động thành `.MainActivity` (hoặc app có `activity-alias` để người dùng đổi icon). Lần nạp danh sách sau: khóa mới không có trong DB nên được đặt vào ô trống đầu tiên; hàng cũ không còn app sống nhưng package vẫn cài nên **không bao giờ bị xóa** và cũng không được vẽ.
  - Vị trí người dùng xếp bị mất; còn lại một ô trống không kéo đi được.
  - Ở dock, hàng ma vẫn chiếm một trong 4 slot → kéo icon thứ 4 vào thì DB từ chối, icon bật về không lời giải thích.
  - Trong thư mục, hàng ma tính vào `MAX_ITEMS` và chặn việc giải tán thư mục 1 app.
  - App đã ẩn hiện lại trên Home (`hidden_apps` cũng khóa theo className).
- **Đã mô phỏng (Live 4):** sửa trong bản sao DB hàng Chrome ở dock thành `className = …OldMain` (không tồn tại), mở Home. Kết quả: dock chỉ còn 3 icon; DB có hai hàng Chrome: hàng cũ `id 3` vẫn ở dock, hàng mới `id 119` ở trang 2 ô (0,4). Đã trả DB, md5 khớp.
- **Khác với giới hạn đã ghi (Q11):** ở đây cùng (package, user) có một activity khởi động sống chưa được đặt, chứng tỏ package đang bật, nên chuyển hàng an toàn, không sợ xóa nhầm app bị tắt tạm.
- **Hướng sửa:** trong `placeNewInTransaction` (và `catchUp` trước đó), gom các hàng không có app sống theo (package, userSerial). App mới nào trùng (package, user) với hàng mồ côi thì **sửa `className` của hàng cũ** thay vì chèn hàng mới (giữ vị trí, slot dock, thư mục). Làm tương tự cho `hidden_apps`. Launcher3 xử lý ca này cùng kiểu.

### A4. Icon không đổi khi app cập nhật icon

- **Mức:** cao (người dùng thấy rõ, mọi app cập nhật icon đều dính). **Loại:** lỗi. **Nhãn:** đo được (Home); adapter đọc code.
- **Đã tái hiện (đợt 3, Live 4, launcher mặc định):** cài app thử v1 (icon đỏ) → launcher đặt vào trang 2 ô (0,4). `adb install -r` bản v2 (icon xanh, `versionCode=2`), đợi 6 s → ô trên Home **vẫn đỏ**. Tìm "audit" → hàng kết quả hiện **xanh** (hàng mới bind nên lấy icon mới). `am force-stop` rồi mở lại → ô trên Home xanh. Vậy lỗi nằm đúng ở map icon riêng của view Home.
- **Vị trí:** map riêng của từng view: `ui/home/view/IconGridView.kt:258`, `:1146-1149`, `:1165-1177`, `:1187`; `ui/home/view/DockView.kt:117`, `:483-503`; `ui/home/view/FolderPanelView.kt:389`, `:455-461`. Adapter: `ui/search/SearchResultAdapter.kt:75`, `ui/home/view/CategoryGroupPanelView.kt:290`, `ui/library/AppLibraryAdapter.kt:91`. Xóa cache: `data/apps/AppRepositoryImpl.kt:48`. `domain/model/AppItem.kt:10-17` không có trường phiên bản.
- **Chuyện gì xảy ra:** ba view Home giữ `HashMap<ComponentKey, Bitmap>` riêng, chỉ xóa khi cỡ icon đổi hoặc khóa rời khỏi danh sách. Khi app cập nhật, chỉ cache dùng chung bị xóa; view không được báo. Lần bind sau thấy `iconCache[key] != null` nên dùng lại bitmap cũ, không gọi `loadIconFor`. `AppItem` mới bằng `AppItem` cũ nên các adapter cũng không bind lại. Icon cũ ở lại tới khi process chết hoặc đổi kiểu icon. Plan IC mục 8 đã ghi "chưa thử `install -r`".
- **Cách thử lại sau khi sửa:** dựng hai APK thử không code (manifest `hasCode="false"`, activity `android.app.Activity`, icon PNG khác màu) bằng `aapt2 compile/link` + `zipalign` + `apksigner` với khóa debug; cài v1, `install -r` v2, xem ô trên Home, dock (kéo vào dock), thư mục (gộp vào thư mục).
- **Hướng sửa:** đưa dấu phiên bản (vd `sourceDir` hoặc `lastUpdateTime`, IC đã dùng trong tên file đĩa) vào `AppItem` và vào khóa cache của view/adapter, để APK mới thành khóa mới. Cách này sửa luôn B7. Cách khác: phát tín hiệu "icon package đổi" sau khi `loadApps` xong, view xóa đúng khóa và bind lại ô.

### A5. Ảnh nền đã chọn giữ trong RAM to gấp 4–10 lần cần thiết

- **Mức:** cao (cùng gốc và cùng cách sửa với A1). **Loại:** bộ nhớ, hiệu năng. **Nhãn:** đọc code.
- **Vị trí:** như A1, thêm `data/backdrop/BackdropRepositoryImpl.kt:69`, `data/backdrop/WallpaperApplier.kt:46`, `:56`, `PickedImageSource.kt:178` (`applyOrientation` có lúc giữ hai bản).
- **Chuyện gì xảy ra:** ảnh ngang 12 MP (4032x3024) trên máy 720x1604 cho `sample = 1` → Home giữ bitmap 48,8 MB suốt đời process, thay vì khoảng 4,6 MB. Bitmap này còn là texture GPU được thu nhỏ khoảng 4 lần mỗi khung hình (không mipmap), và là nguồn của kính thật. "Đặt làm hình nền hệ thống" giải mã thêm một bản đầy đủ khi bản của Home còn sống.
- **Cách đo:** `adb shell dumpsys meminfo gambi.global.os26launcher` (dòng Native Heap và Graphics) với ảnh mặc định và với một ảnh ngang đã chọn; `dumpsys gfxinfo` khi vuốt trang.
- **Hướng sửa:** cùng bước thu nhỏ ở A1.

---

## Mức trung bình

### B1. `ApplicationScope` không có handler: lỗi lạ ở thời tiết hoặc flow bố cục làm sập launcher

- **Mức:** trung bình. **Loại:** crash, độ bền. **Nhãn:** đọc code (đường lan lỗi chắc chắn; nguyên nhân kích hoạt là suy luận, xác suất thấp).
- **Vị trí:** `di/AppModule.kt:25` (`CoroutineScope(SupervisorJob() + dispatcher)`, không `CoroutineExceptionHandler`); `data/weather/WeatherRepositoryImpl.kt:88-93`, `:148-159` (chỉ `catch (e: WeatherException)`); `ui/weather/WeatherSheetViewModel.kt:80-89`, `:115-119`; `data/layout/LayoutRepositoryImpl.kt:62-80`.
- **Chuyện gì xảy ra:** `SupervisorJob` không chặn lỗi chưa bắt đi tới handler của luồng → process chết.
  - Thời tiết: `MalformedURLException` từ `URL(...)` ở `OpenWeatherMapProvider.kt:97` (nằm ngoài try của `WeatherHttp`), `IllegalArgumentException` từ `roundToInt()` khi JSON có `"NaN"`, lỗi runtime lạ của HTTP stack hãng máy. Chạy theo nhịp phút của widget thời tiết.
  - Bố cục: `seedIfEmpty`, `ensureFitsGrid`, `catchUp`, `placeNewApps` chạy trong `shareIn(ApplicationScope)` không có catch. Lỗi Room (vd `SQLiteFullException` khi máy gần đầy bộ nhớ) làm sập; `catchUp` chạy lại mỗi lần mở nên có thể sập lặp lại. `removePackages` đã được bọc try/catch (`:90-94`), nhưng flow chính thì chưa.
- **Hướng sửa:** thêm `CoroutineExceptionHandler` ghi log vào `ApplicationScope`. `doRefresh`: `catch (e: CancellationException) { throw e } catch (e: Exception)` → map sang `SERVER`/`PARSE`; đưa việc dựng URL vào trong try. Bọc các lời gọi reconciler trong flow bố cục bằng try/catch, vẫn phát bố cục đã assemble.

### B2. Đổi sáng/tối không nạp lại ảnh nền mặc định

- **Mức:** trung bình. **Loại:** lỗi. **Nhãn:** đo được.
- **Vị trí:** `ui/home/controller/BackdropController.kt:76-81` → `ui/home/HomeViewModel.kt:165-170`.
- **Chuyện gì xảy ra:** `configChanges` không có `uiMode` nên đổi sáng/tối dựng lại `HomeActivity`. Ở `onResume` đầu của Activity mới, `homeRoot.width/height` còn bằng 0 (layout chạy sau `onResume`) → `reloadBackdropIfChanged` thoát ở `width <= 0` trước khi so `backdropVersion()`. `onScreenSize` cũng không chạy vì `screenSizeHandled` đã `true` (ViewModel sống sót).
- **Đã tái hiện (Live 4):** tạm cất ảnh đã chọn, mở Home (tối, ảnh mặc định tối), `cmd uimode night no` → Home dựng lại theo theme sáng (icon thanh trạng thái đổi màu) nhưng **vẫn ảnh nền tối**. Đã trả chế độ tối và ảnh, md5 khớp. Lần đạt ghi trong `WP-default-wallpaper.md` (Oppo 2026-11-09) có lẽ nhờ một lần `onResume` sau đó.
- **Hướng sửa:** ViewModel nhớ cỡ màn hình cuối từ `onScreenSize` và dùng khi `width/height` bằng 0; hoặc gọi nạp lại trong `homeRoot.doOnLayout`.

### B3. Thu nhỏ lưới đua với cài app mới → icon ngoài lưới → thả icon làm sập

- **Mức:** trung bình (sập, nhưng hiếm). **Loại:** crash. **Nhãn:** đọc code.
- **Vị trí:** `domain/usecase/ChangeGridPresetUseCase.kt:24-26` (reflow DB và ghi DataStore là hai bước); `data/layout/LayoutReconciler.kt:65`, `:134-139`, `:683-699` (`placeNew` đọc `currentGrid()` từ DataStore); `domain/layout/GridInsertPlanner.kt:128-134` (giả định chỉ tràn đúng 1 item); `ui/home/HomeViewModel.kt:577-578` (planner chạy đồng bộ trong `onItemDropped`, không try).
- **Chuyện gì xảy ra:** người dùng đổi 4x6 → 4x5. `reflowTo(4x5)` nhả mutex; trước khi DataStore ghi xong, một app mới được cài hoặc được bỏ ẩn → `placeNewApps` đọc cỡ cũ 4x6 và đặt app ở hàng 5 (ngoài lưới 4x5). `ensureFitsGrid` chỉ chạy lúc khởi động nên icon đó nằm ngoài lưới suốt đời process. Sau đó kéo icon từ trang khác thả lên ô có icon của trang đó → `toShift` có 16 phần tử, `free[20]` ném `ArrayIndexOutOfBoundsException` trên luồng chính.
- **Hướng sửa:** chạy cả việc đổi cỡ lưới trong mutex của reconciler (reflow + ghi DataStore trong một `mutex.withLock`). Cho `GridInsertPlanner` phòng thủ: bỏ qua ô ngoài `0 until columns*rows`, xử lý nhiều item tràn như `planWidget`.

### B4. Gỡ nhiều app liền nhau → app đã gỡ được đặt lại thành ô ma

- **Mức:** trung bình. **Loại:** mất dữ liệu bố cục. **Nhãn:** đọc code (thời điểm là suy luận).
- **Vị trí:** `data/apps/LauncherAppsDataSource.kt:51-53`; `data/apps/AppRepositoryImpl.kt:46-57`; `data/layout/LayoutRepositoryImpl.kt:75`, `:86-96`; `data/layout/LayoutReconciler.kt:149-167`, `:654-661`.
- **Chuyện gì xảy ra:** gỡ A → nạp lại danh sách, `getActivityList` còn có B. Trong lúc đọc nhãn, B bị gỡ → luồng REMOVED xóa hàng của B. Danh sách cũ phát ra → `placeNewApps` thấy B "mới" → chèn lại B. Lần nạp sau B biến mất khỏi danh sách nhưng hàng vẫn ở DB thành ô trống tới lần khởi động sau. Gặp khi gỡ hàng loạt (Cài đặt chọn nhiều, Play "Quản lý app", app dọn rác).
- **Hướng sửa:** trong `placeNewApps` (trong mutex, ngoài transaction, giống `catchUp`), lọc app mới bằng `appRepository.isPackageInstalled(pkg, serial)`, thường chỉ 0–1 app nên rẻ. Hoặc giữ tập "vừa gỡ" ngắn hạn do `removePackages` ghi.

### B5. Trang và item quan sát bằng hai flow riêng → UI nhận bố cục nửa cũ nửa mới

- **Mức:** trung bình. **Loại:** lỗi, hiệu năng. **Nhãn:** đọc code (có hai lần phát và lần đầu bị trộn là chắc; hiệu ứng nhìn thấy là suy luận).
- **Vị trí:** `data/layout/LayoutRepositoryImpl.kt:69-77` (`combine(pageDao.observeAll(), itemDao.observeAll(), …)`), `ui/home/HomeViewModel.kt:111-118`.
- **Chuyện gì xảy ra:** một transaction sửa cả `home_pages` và `launcher_items` làm cả hai flow truy vấn lại độc lập; `combine` phát (trang mới, item cũ) hoặc ngược lại trước trạng thái đúng. Ví dụ thả icon lên trang ma (`PageId.NEW`): nếu item tới trước, item trỏ tới trang chưa có → biến mất, pager co lại và trang hiện tại bị kẹp; khi trang tới, pager vẫn kẹp. Khớp giả thuyết (2) chưa xác nhận trong `docs/lessons/settings-and-data.md` dòng 52. Đi kèm C17.
- **Hướng sửa:** một flow vô hiệu hóa duy nhất: `db.invalidationTracker.createFlow("launcher_items", "home_pages")` → `db.withTransaction { pageDao.getAll() to itemDao.getAll() }`, rồi mới combine với `apps`.

### B6. State đổi giữa lúc vuốt trang → pager giật về trang cũ hoặc bỏ hoạt ảnh

- **Mức:** trung bình. **Loại:** lỗi UI. **Nhãn:** đo được.
- **Đã tái hiện (đợt 3, Live 4):** đứng ở trang 2, vuốt chậm sang trái 2,5 s (`input swipe 900 1300 150 1300 2500`), 0,8 s sau `pm disable-user` app thử để state đổi giữa lúc kéo; quay màn hình 30 khung/s và đo độ lệch ngang của trang từng khung. Trang theo ngón tay 0 → 400 px (khung 31–69), **khung 70 nhảy về 0** khi ngón tay vẫn đang kéo, rồi lại theo ngón tay từ 0 (khung 74–99) trước khi nhả. Độ trễ ~1,5 s từ lúc tắt app tới lúc giật khớp với debounce 200 ms cộng thời gian nạp lại danh sách app.
- **Vị trí:** `ui/home/controller/HomePagerController.kt:169`, `:177-195`.
- **Chuyện gì xảy ra:** `render()` chạy mỗi lần `uiState` đổi; callback của `submitList` gọi `setCurrentItem(savedPage, false)`. Khi pager không rảnh (đang kéo hoặc đang trượt về), ViewPager2 cắt ngang bằng `scrollToPosition` → trang giật về dưới ngón tay hoặc nhảy thẳng tới đích. `savedPage` đọc trước khi diff chạy nền nên có thể đưa người dùng về trang vừa rời. Kích hoạt khi: một app trên lưới được cập nhật hoặc gỡ ở nền, ảnh nền nạp xong ngay sau khởi động, DB phản hồi sau khi tạo thư mục hoặc trang mới.
- **Hướng sửa:** bỏ qua bước đặt lại khi `homePager.scrollState != SCROLL_STATE_IDLE`; đọc `currentItem` lúc callback chạy; tốt nhất là khôi phục theo `pageId` và chỉ khi chỉ số ra ngoài khoảng.

### B7. Cache icon trong RAM có thể khóa lại icon cũ ngay sau khi app cập nhật

- **Mức:** trung bình. **Loại:** lỗi. **Nhãn:** đọc code (phụ thuộc thời điểm).
- **Vị trí:** `data/apps/IconLoader.kt:29`, `:33-38`, `:47`; `data/apps/LauncherAppsDataSource.kt:104-107`, `:119`; `data/apps/AppRepositoryImpl.kt:47-55`; `data/apps/IconCache.kt:25-28`.
- **Chuyện gì xảy ra:** khi CHANGED, mục RAM bị xóa ngay nhưng `sourceDirs` chỉ được làm mới ở cuối `loadApps` (sau debounce 200 ms, rồi 150–600 ms). `load()` trong khoảng đó đọc đĩa theo `sourceDir` cũ và `cache.put` lại icon cũ. Khóa RAM không có dấu phiên bản nên icon cũ ở lại tới khi bị LRU đẩy ra. Gặp khi App Library hoặc tìm kiếm đang mở lúc app cập nhật.
- **Hướng sửa:** đưa dấu phiên bản vào khóa RAM (cùng cách A4), hoặc ở cuối `loadApps` so `sourceDirs` cũ/mới và xóa lại các khóa đổi dấu.

### B8. Kiểu icon Tô màu sửa drawable dùng chung của hệ thống

- **Mức:** trung bình. **Loại:** lỗi. **Nhãn:** suy luận (đọc mã nguồn framework, chưa thử trên máy).
- **Vị trí:** `data/apps/IconRenderer.kt:285-296` (`foreground.setColorFilter(...)`, không có `mutate()` trong file), `data/apps/LauncherAppsDataSource.kt:130`.
- **Chuyện gì xảy ra:** lớp foreground của `AdaptiveIconDrawable` từ `getIcon(0)` chia sẻ `ConstantState` với cache icon của framework. Với foreground là PNG, `setColorFilter` ghi vào `Paint` dùng chung. Nếu ngay sau đó render kiểu Mặc định/3D cho cùng app (trước khi GC dọn cache yếu của framework), foreground vẫn bị nhuộm và bitmap sai được lưu vào cả cache RAM lẫn cache đĩa tới lần app cập nhật sau.
- **Hướng sửa:** gọi `drawable.mutate()` trước `setColorFilter`, hoặc không đụng drawable mà vẽ foreground trong `canvas.saveLayer(null, Paint().apply { colorFilter = … })`.

### B9. Đổi ngôn ngữ hệ thống không nạp lại nhãn app và thứ tự sắp xếp

- **Mức:** trung bình. **Loại:** lỗi. **Nhãn:** đọc code.
- **Vị trí:** `data/apps/AppRepositoryImpl.kt:46-57`, `:87-94`; `data/apps/LabelCache.kt:53`, `:77`; manifest (`locale` không có trong `configChanges`).
- **Chuyện gì xảy ra:** đổi ngôn ngữ dựng lại Activity nhưng `HomeViewModel` sống sót và flow `apps` chỉ phát lại khi có sự kiện package. Nhãn Home/dock, App Library, chỉ mục tìm kiếm và thứ tự Collator giữ ngôn ngữ cũ, có thể hàng giờ. Phụ: khi `LabelCache.load()` gặp header lệch ngôn ngữ, nó trả `null` mà không đặt `onDisk = null`; nếu nhãn mới trùng map cũ thì không ghi lại, và mọi lần nạp sau trong process đó đọc lại toàn bộ nhãn (~5 s trên Oppo debug).
- **Đã tái hiện (đợt 3, Live 4):** đọc nhãn ô trên Home qua `uiautomator` (nhãn ẩn trên màn hình nhưng có trong content-desc).
  - `vi-VN` → `en-US`: nhãn đổi sang "Calculator", "Phone", "Messages", "Camera" **trong cùng process**. Nguyên nhân: ngay sau khi đổi, Play cài gói ngôn ngữ `config.en` cho khoảng 12 app (`lastUpdateTime` 15:32:41–15:33:28); các sự kiện package đó vô tình kích hoạt nạp lại.
  - `en-US` → `vi-VN` (gói `config.vi` đã có sẵn, không có sự kiện package): sau 30 s Home **vẫn** "Calculator", "Phone", "Messages", "Camera"; header `app_label_cache.json` vẫn `en-US`. `am force-stop` rồi mở lại thì về "Máy tính", "Điện thoại", "Tin nhắn", "Máy ảnh", cache về `vi-VN`.
  - Vậy lỗi có thật và thường gặp nhất khi đổi về một ngôn ngữ đã dùng trước đó.
- **Hướng sửa:** gộp một tín hiệu đổi locale vào luồng nạp lại (receiver `ACTION_LOCALE_CHANGED` thành flow, hoặc so `Locale.getDefault()` với thẻ của lần nạp cuối khi `onCreate`). Trong `LabelCache.load()`, đặt `onDisk = null` khi header lệch.

### B10. Nút Home không đóng edit mode, bảng chọn widget, bảng thời tiết

- **Mức:** trung bình. **Loại:** lỗi (kẹt UI), làm A2 nặng thêm. **Nhãn:** đo được (edit mode và bảng chọn widget); bảng thời tiết đọc code. **Cần chủ dự án quyết:** Home có thoát edit mode không (iOS có).
- **Đã tái hiện (đợt 2, launcher mặc định):** vào edit mode, bấm phím Home hai lần → thanh nút edit mode vẫn hiện, icon vẫn rung. Mở "Tiện ích" rồi bấm Home → bảng "Thêm tiện ích" vẫn mở (thêm một cửa sổ của app, chụp màn hình thấy rõ). Back đóng được bảng.
- **Vị trí:** `ui/home/HomeActivity.kt:186-196` (`onNewIntent` đóng menu icon, tìm kiếm, Library, thư mục, nhưng không đóng edit mode, `WidgetPickerController.dialog`, `BottomSheetDialog` của `WeatherSheetController`).
- **Chuyện gì xảy ra:** quy tắc của HB (`docs/knowledgements/HB-home-closes-menu.md` dòng 25): mọi lớp tạm phải đóng cả khi Back lẫn Home. Back đóng được hai bảng (vì là dialog), Home thì không. Rời launcher khi đang ở edit mode → rung chạy ở nền (A2).
- **Hướng sửa:** trong nhánh Home của `onNewIntent`, đóng bảng chọn widget (hủy luôn phiên kéo của nó) và bảng thời tiết. Nếu chủ dự án đồng ý, gọi `viewModel.exitEditMode()` ở đó hoặc ở `onStop`.

### B11. Tìm kiếm dựng lại toàn bộ chỉ mục nhiều lần mỗi phiên

- **Mức:** trung bình. **Loại:** hiệu năng. **Nhãn:** đọc code (nguồn kích hoạt); chi phí ngoại suy từ số đo M8d.
- **Vị trí:** `ui/search/SearchViewModel.kt:145-161`, `:200-224`; `domain/search/SearchIndex.kt:26-33`; `domain/search/AppSearchMatcher.kt:23-30`.
- **Chuyện gì xảy ra:** bất kỳ nguồn nào trong 5 nguồn đổi đều dựng lại cả chỉ mục, chuẩn hóa lại mọi nhãn (NFD + 2 regex). Một phiên: mở (nạp tới 5000 danh bạ) → dựng; làm mới shortcut → dựng; đóng (xóa danh bạ) → dựng; quay về Home sau hơn 5 s → dựng. M8d đo được ~0,2 ms mỗi nhãn (Oppo, có lẽ debug); 2000–5000 danh bạ ≈ 0,4–1 s mỗi lần dựng. Máy thử chỉ có 12–17 danh bạ nên chưa ai thấy.
- **Hướng sửa:** chuẩn hóa mỗi nguồn một lần và giữ lại (mảng đã chuẩn hóa theo nguồn hoặc memo raw → normalized). Chuẩn hóa tên danh bạ trên IO trong `loadNames`. Thêm đường tắt ASCII trong `normalize` (bỏ Normalizer và regex khi mọi ký tự < 128) hoặc thay regex bằng một vòng lặp ký tự.

### B12. Hàng "Launcher mặc định" không làm gì sau "Không hỏi lại"

- **Mức:** trung bình. **Loại:** lỗi. **Nhãn:** suy luận (theo hành vi PermissionController của AOSP; chưa thử vì trạng thái từ chối khó trả lại trên máy dùng chung).
- **Vị trí:** `ui/settings/SettingsActivity.kt:69-70` (callback rỗng), `:546-558`; `data/system/DefaultLauncherRepositoryImpl.kt:45-51` (luôn ưu tiên `createRequestRoleIntent(ROLE_HOME)`).
- **Chuyện gì xảy ra:** từ chối hai lần với "Không hỏi lại" → `RequestRoleActivity` đóng ngay với `RESULT_CANCELED`. App không chuyển sang `ACTION_HOME_SETTINGS` nên hàng (và hàng gợi ý "shortcut cần launcher mặc định" trong khối Tìm kiếm) bấm không có gì xảy ra.
- **Hướng sửa:** trong callback kết quả, nếu `resultCode != RESULT_OK` và chưa là launcher mặc định thì mở `Settings.ACTION_HOME_SETTINGS` (trong try/catch), cuối cùng là `ACTION_SETTINGS`.

---

## Mức thấp

### C1. App Library chớp "Đang tải" và dựng lại mọi ô khi quay về sau hơn 5 giây
- **Nhãn:** đọc code. **Vị trí:** `domain/usecase/GetCategoryGroupsUseCase.kt:31` (`.onStart { emit(null) }`), `ui/home/HomeViewModel.kt:90-91` (`WhileSubscribed(5_000)`), `ui/home/view/CategoryPageView.kt:271-284`, `ui/home/controller/AppLibraryController.kt:135-141`; cùng kiểu ở `ui/search/SearchViewModel.kt:79-81`.
- **Chuyện gì xảy ra:** mở app từ Library (Library không đóng), ở ngoài hơn 5 s, Back về → flow khởi động lại, `onStart` phát `null` → trang Library thành vòng quay rồi dựng lại mọi ô, kính và icon, đúng lúc hoạt ảnh quay về Home.
- **Đã thử (đợt 2, Live 4):** mở Library, chuyển sang app Cài đặt 9 s, Back về, quay màn hình và xem từng khung (~40 khung/s): không thấy vòng quay, không ô nào thiếu icon. Có lẽ truy vấn Room về trước khung hình đầu trên máy này. Giữ mục ở mức thấp; nên thử lại trên Oppo (345 app).
- **Hướng sửa:** bỏ `.onStart { emit(null) }` (giá trị đầu `null` của `stateIn` đã đủ cho lần nạp đầu), hoặc bỏ qua `null` sau lần có dữ liệu đầu tiên. Làm tương tự trong `SearchViewModel`.

### C2. Cache icon trên đĩa xóa icon trang Home trước tiên
- **Nhãn:** đọc code (ảnh hưởng tới khởi động chưa đo). **Vị trí:** `data/apps/IconDiskCache.kt:63-87` (`get` không chạm file), `:127-128`, `:138-149` (xóa theo `lastModified`).
- **Chuyện gì xảy ra:** trần 8 MB, xóa file cũ nhất theo thời điểm ghi; file ghi đầu tiên chính là icon Home/dock lúc khởi động lạnh. Mỗi cỡ icon cache riêng (lưới, dock, Library, tìm kiếm, mini, nhóm), ~13 KB/file → ~630 file. Oppo (344 app) có thể vượt sau khi duyệt Library và tìm kiếm → lần khởi động sau lỡ đúng icon Home (phần IC tăng tốc).
- **Hướng sửa:** khi đọc trúng, `file.setLastModified(now)` (mỗi file tối đa một lần mỗi process) để xóa theo LRU thật. Tùy chọn: xóa file của dấu cũ khi `sourceDir` đổi.

### C3. Không trả bộ nhớ khi hệ thống thiếu RAM; cache blur giữ ảnh nền cũ
- **Nhãn:** đọc code (ảnh hưởng tới việc bị hệ thống tắt là suy luận). **Vị trí:** `core/glass/BlurredBackdropCache.kt:21-22`, `:34-35`, `:110-111`; cả mã nguồn không có `onTrimMemory`/`ComponentCallbacks2`.
- **Chuyện gì xảy ra:** singleton giữ tham chiếu mạnh `smallSource`, `failedSource`, `pendingSource` tới ảnh nền đầy đủ; sau khi đổi ảnh hoặc lỗi dựng, ảnh cũ sống tới khi process chết. Ở nền, Home vẫn giữ ảnh nền (tới ~48 MB, xem A5) cùng một hai bản blur.
- **Đo được (đợt 2, Live 4):** launcher ở nền, `am send-trim-memory … COMPLETE` → Graphics 18,6 → 0 MB (HWUI tự trả), Native Heap chỉ 113 → 106 MB: app không tự trả bitmap nào. Mở App Library thêm ~15 MB Native và ~14 MB Graphics, đóng Library không giảm (có thể là cache có chủ đích, nhưng cũng không được trả khi thiếu RAM).
- **Hướng sửa:** giữ các nguồn bằng `WeakReference` hoặc so theo id; bỏ bitmap blur nhỏ khi `TRIM_MEMORY_BACKGROUND` trở lên (dựng lại khi cần); tùy chọn bỏ ảnh nền khi `TRIM_MEMORY_COMPLETE` và nạp lại ở `onResume`.

### C4. Nhập ảnh nền bỏ qua kết quả ghi file; Home có thể không nạp ảnh mới
- **Nhãn:** đọc code. **Vị trí:** `ui/settings/SettingsViewModel.kt:152-176`, `data/backdrop/PickedImageSource.kt:188-198`, `ui/home/HomeViewModel.kt:165-170`.
- **Chuyện gì xảy ra:** kết quả `compress()` và `renameTo()` bị bỏ qua; máy đầy bộ nhớ thì báo thành công nhưng file cụt hoặc không có → lần sau Home rơi vào `Error` (nền đen). Home chỉ nạp lại ở `onResume` khi `lastModified` đổi; bấm Back trước khi đổi tên xong thì Home giữ ảnh cũ tới lần resume sau, và sự kiện `AskWallpaperTarget` bị mất vì scope của Settings bị hủy.
- **Hướng sửa:** kiểm hai giá trị trả về, lỗi thì ném `IOException`. Home theo dõi phiên bản ảnh qua `StateFlow` của repository thay vì chỉ hỏi ở `onResume`, hoặc chạy việc nhập trong scope của app.

### C5. Ở mức kính CLASSIC, dock blur ảnh nền lần thứ hai
- **Nhãn:** đọc code. Trên Live 4 (Ép cao, dock dùng kính thật) chỉ thấy 1 luồng `GlassBlur`, nên chưa thấy trên máy. **Vị trí:** `ui/home/controller/DockController.kt:32`, `:78`, `:96`; `core/glass/StaticBlurSurface.kt:125`.
- **Chuyện gì xảy ra:** dock có `BlurredBackdropCache()` riêng, blur lại đúng ảnh mà `GlassEnvironment.blurCache` đã blur (78,8 ms CPU mỗi lần trên debug, theo bài học GL), giữ thêm một bitmap, và mỗi lần Activity dựng lại tạo thêm executor và luồng mới.
- **Hướng sửa:** truyền `glassEnv.blurCache` vào `setBackdropSource`, bỏ `dockGlassCache`.

### C6. Danh bạ ở lại RAM và vẫn được đọc lại khi rời launcher lúc ô tìm kiếm còn mở
- **Nhãn:** đọc code. **Vị trí:** `ui/search/SearchViewModel.kt:365-393`, `data/search/ContactsRepositoryImpl.kt:116-132`, `ui/home/HomeActivity.kt:198-201`.
- **Chuyện gì xảy ra:** chỉ nút Home đóng tìm kiếm. Rời launcher bằng thông báo hoặc Gần đây → `isOpen` vẫn `true`, tới 5000 tên ở lại RAM, `ContentObserver` vẫn đăng ký và mỗi thay đổi danh bạ (đồng bộ, nhật ký cuộc gọi) đọc lại toàn bộ tên ở nền. Vượt ý định "tên chỉ ở RAM khi lớp tìm kiếm đang mở".
- **Hướng sửa:** ở `ON_STOP`, gọi `stopContactsWatch()` và xóa `contactEntries` (giữ `isOpen` và chuỗi tìm); `ON_RESUME → refreshContacts()` sẵn có sẽ nạp lại.

### C7. Truy vấn số điện thoại chạy mỗi phím gõ và không hủy được
- **Nhãn:** đọc code. **Vị trí:** `ui/search/SearchViewModel.kt:330`, `:343-348`; `data/search/ContactsRepositoryImpl.kt:80-114`.
- **Chuyện gì xảy ra:** `mapLatest` hủy coroutine nhưng `ContentResolver.query` chặn vẫn chạy hết; gõ nhanh "nguyen" xếp hàng ~5 truy vấn.
- **Hướng sửa:** truyền `CancellationSignal` vào `query` và hủy khi coroutine bị hủy; cache `contactId → số` trong phiên, xóa khi `close()`.

### C8. Mỗi hàng shortcut trong tìm kiếm nạp tới 4 icon để dùng 1
- **Nhãn:** đọc code. **Vị trí:** `ui/search/SearchViewModel.kt:487-489`; `data/apps/ShortcutRepositoryImpl.kt:38-59`, `:87-99`.
- **Chuyện gì xảy ra:** mỗi lần bind hàng shortcut gọi `getShortcuts(key, sizePx)`: một IPC, rồi tới 4 lần `getShortcutIconDrawable` (IPC + giải mã) và 4 lần vẽ, giữ 1. Không có cache; 3 hàng top-hit có thể là 12 lần nạp icon mỗi lần hiện lại.
- **Hướng sửa:** thêm `shortcutIcon(key, id, sizePx)` truy vấn bằng `setShortcutIds(listOf(id))` và chỉ vẽ icon đó, kèm `LruCache` theo `(pkg, id, user, size)`.

### C9. Danh mục Cài đặt và chỉ mục tìm kiếm dựng ngay lúc khởi động
- **Nhãn:** đọc code (chi phí khởi động chưa đo; nếu đo thì đo bản release). **Vị trí:** `ui/search/SearchViewModel.kt:107-118`; `data/search/SystemSettingsRepositoryImpl.kt:54-82` (~25 mục, ~50 IPC PackageManager); `ui/home/HomeActivity.kt:87`, `:176`.
- **Chuyện gì xảy ra:** giành CPU và binder với việc nạp danh sách app lúc khởi động lạnh, kể cả khi người dùng không mở tìm kiếm; chỉ mục còn dựng lại khi danh mục về.
- **Hướng sửa:** dựng danh mục khi `open()` lần đầu hoặc sau khung hình đầu; chỉ combine `searchIndex` sau lần mở đầu tiên.

### C10. Hồ sơ công việc: icon không huy hiệu, thêm/bỏ hồ sơ không được theo dõi
- **Nhãn:** đọc code (huy hiệu); suy luận (sự kiện hồ sơ). **Vị trí:** `data/apps/LauncherAppsDataSource.kt:130`, `:142-189`; `data/apps/AppRepositoryImpl.kt:46-57`.
- **Chuyện gì xảy ra:** app nhân bản trong hồ sơ công việc hiện hai lần cùng icon và nhãn, dễ mở hoặc gỡ nhầm (bài học M2 đã hoãn việc này). Bỏ hồ sơ không có `onPackageRemoved` nên icon công việc vẫn hiện tới lần khởi động sau.
- **Hướng sửa:** phủ huy hiệu hệ thống cho khóa có `userSerial` khác user chính (`packageManager.getUserBadgedIcon`). Đăng ký `ACTION_MANAGED_PROFILE_ADDED/REMOVED/AVAILABLE/UNAVAILABLE/UNLOCKED` để nạp lại và dọn hàng của user bị bỏ.

### C11. Mọi gói icon từng chọn ở lại bộ nhớ
- **Nhãn:** đọc code. **Vị trí:** `data/apps/IconPackManager.kt:39-40`, `:93-96`, `:114`; `data/apps/IconStyleSource.kt:66-68`.
- **Chuyện gì xảy ra:** `parsed` giữ `PackData` (Resources/AssetManager và map ~3500 mục) cho mọi gói từng chọn; đổi A → B → C → không dùng vẫn giữ cả ba.
- **Hướng sửa:** khi `IconStyleSource` đổi gói, gọi `iconPacks.forget(góiTrước)`, hoặc chỉ giữ mục của gói hiện tại.

### C12. Nhấn giữ hai app trong cùng thư mục liền nhau có thể hiện shortcut của app trước
- **Nhãn:** đọc code (hiếm: cần app đầu nạp shortcut chậm hơn một lần nhấn giữ). **Vị trí:** `ui/home/view/FolderPanelView.kt:165-166` (`itemId = -1L` cho mọi app trong thư mục), `ui/home/HomeViewModel.kt:305-311`, `ui/home/controller/IconMenuController.kt:177-179`.
- **Hướng sửa:** khớp thêm `key` và `container`, hoặc dùng mã yêu cầu tăng dần.

### C13. Tên thư mục đang gõ bị mất khi Activity dựng lại
- **Nhãn:** đọc code. **Vị trí:** `ui/home/view/FolderPanelView.kt:84-99` (`EditText` tạo bằng code, không id), `:158`, `:298-303`.
- **Chuyện gì xảy ra:** đang gõ tên thư mục mà máy đổi sáng/tối (theo lịch hoặc từ Cài đặt nhanh) → thư mục mở lại với tên cũ.
- **Hướng sửa:** gọi `commitTitle()` trong `onDetachedFromWindow` của panel, hoặc ở `HomeActivity.onStop`.

### C14. Mở menu nhấn giữ lúc icon vừa thả còn đang đáp → icon đó tàng hình
- **Nhãn:** đọc code (thường chỉ gặp khi hoạt ảnh bị làm chậm). **Vị trí:** `ui/home/controller/IconMenuController.kt:171-175` (gọi thẳng `dragLayer.startDrag`), `ui/home/view/DragLayer.kt:100-107`, `:114-116`, `core/ui/DragHelper.kt:223-231`, `:241-246`.
- **Chuyện gì xảy ra:** `startDrag` hủy hoạt ảnh đáp và bỏ luôn callback hoàn tất, nên `settlingItemId` không được xóa → ô đó giữ alpha 0 tới phiên kéo sau.
- **Hướng sửa:** trước `startDrag`, kết thúc phần đáp đang chờ (`DragHelper.finishSettleNow()`), hoặc để `cancelSettle()` chạy callback đã lưu.

### C15. Khóa OpenWeatherMap nằm nguyên văn trong APK
- **Nhãn:** đọc code. Khóa **không** nằm trong repo (`local.properties` bị gitignore). **Vị trí:** `app/build.gradle.kts:11-16`, `:33`; `di/WeatherModule.kt:33`.
- **Chuyện gì xảy ra:** khóa là chuỗi hằng trong dex; R8 chạy với `-dontobfuscate`, `strings` hoặc jadx đọc ra ngay. Người lấy được khóa có thể dùng hết hạn mức miễn phí → mọi người dùng nhận lỗi 429.
- **Hướng sửa:** trước khi phát hành, gọi qua một proxy nhỏ giữ khóa (`WeatherModule` đã ghi sẵn phương án này, chỉ đổi `baseUrl`); tối thiểu là giới hạn khóa, đặt cảnh báo hạn mức, xoay khóa định kỳ.

### C16. Rung edit mode tốn ~50% một nhân CPU khi đang rung
- **Nhãn:** đo được (Live 4, debug, kính Ép cao: 501 tick / 10 s). **Vị trí:** `ui/home/view/Jiggler.kt:26-40`.
- **Chuyện gì xảy ra:** mỗi ô một `ValueAnimator` riêng, mỗi khung hình đổi `rotation` của từng ô; kính thật trên Live 4 có thể góp thêm phần vẽ lại. Chưa đo bản release, chưa tách phần của kính.
- **Hướng sửa:** một ticker chung cho cả lưới (cùng hướng A2); đo lại bằng bản release, so kính thật với kính mờ.

### C17. Mỗi lần ghi bố cục chạy hai lượt assemble và render
- **Nhãn:** đọc code. Hệ quả hiệu năng của B5: mỗi lần ghi chạm hai bảng phát hai lần, mỗi lần một lượt `assemble`, DiffUtil và render. Sửa B5 là xong.

### C18. Phiên kéo bỏ qua ngón thứ hai
- **Nhãn:** đọc code. Trong lúc kéo, sự kiện của ngón khác bị bỏ qua; nhấc ngón đầu trước thì icon nhảy sang vị trí ngón kia. Không gây kẹt. Có thể theo `pointerId` của ngón bắt đầu kéo.

### C19. Mở bảng chọn widget trong edit mode thì thanh trạng thái hiện lại, đè lên thanh nút
- **Nhãn:** đo được (đợt 2, Live 4, ảnh chụp). **Vị trí:** `ui/home/controller/WidgetPickerController.kt` (dialog), `ui/home/controller/SystemBarsController.kt` (ẩn thanh trạng thái theo `editMode`).
- **Chuyện gì xảy ra:** trong edit mode thanh trạng thái bị ẩn để thanh nút "Cài đặt / Tiện ích / Xong" nằm sát mép trên (TL1). Mở "Tiện ích" thì cửa sổ dialog không mang cờ ẩn, thanh trạng thái hiện lại và giờ, biểu tượng đè lên nút "Cài đặt", "Tiện ích".
- **Hướng sửa:** áp cùng cách ẩn thanh hệ thống cho cửa sổ của dialog (`WindowInsetsControllerCompat` trên `dialog.window`), hoặc để thanh nút tránh vùng thanh trạng thái khi dialog mở.

### C20. App mới lấp ô trống giữa trang; gỡ đi thì cả trang bị dồn
- **Nhãn:** đo được (đợt 3, Live 4). **Cần chủ dự án quyết** (đây là hành vi đã thiết kế, M7 Task 8 `compactPageAfterRemoval`, không phải lỗi code). **Vị trí:** `data/layout/LayoutReconciler.kt` (`placeNew` chọn ô trống đầu tiên; `compactPageAfterRemoval`).
- **Chuyện gì xảy ra:** bố cục của chủ dự án có một ô trống ở trang 2 (0,4). Cài app thử → app được đặt đúng vào ô trống đó. Gỡ app → trang 2 bị dồn: Memrise (1,4) → (0,4), Meet (2,4) → (1,4), Máy tính (0,5) → (3,4). Bố cục người dùng tự để trống bị xáo chỉ vì một app được cài rồi gỡ (đã trả DB từ bản sao lưu 3).
- **Hướng (nếu muốn đổi):** app mới chỉ đặt vào ô sau item cuối cùng của trang (hoặc trang cuối) như iOS, không lấp lỗ giữa trang; hoặc khi gỡ chỉ dồn nếu ô vừa trống nằm trước một item được đặt tự động.

---

## Đã kiểm và ổn

- **CPU khi rảnh** (Live 4, chưa từng vào edit mode): 5 tick / 15 s ở Home, 2 tick / 60 s ở nền (chỉ `Profile Saver`). `WidgetTicker` dừng khi Home không hiện.
- **Bộ nhớ:** 151 MB PSS sau khởi động (Java 23 MB, Native 26 MB, Graphics 44 MB), 1 Activity, 73 view.
- **DB bố cục trên Live 4:** 97 item, 4 trang, 0 ô chồng, 0 ô ngoài lưới 4x6, 0 item mồ côi, 0 app trùng, mọi thư mục ≥ 2 app, `integrity_check = ok`.
- **Ghi DB:** mọi lần ghi đi qua mutex của `LayoutReconciler`, các bước nhiều lệnh nằm trong `withTransaction`; schema v1, `exportSchema = true`, không có migration phá dữ liệu; không truy cập DB trên luồng chính.
- **Log:** không có tên, số điện thoại; chỉ số đếm, thời gian, tên package.
- **DataStore:** một file, một singleton; khóa không trùng.
- **Tìm kiếm:** khớp tiếng Việt không dấu đúng (cả ư/ơ, đ, bàn phím NFD); gõ nhanh không làm kết quả cũ đè kết quả mới.
- **Intent và quyền:** mọi lần mở app, shortcut, Cài đặt, danh bạ đều có try/catch; đọc shortcut kiểm `hasShortcutHostPermission`.
- **Kéo-thả:** `ACTION_CANCEL`, phiên cũ ở `ACTION_DOWN` mới, hủy khi Activity bị hủy đều xử lý đúng.
- **Thời tiết:** chỉ HTTPS; giới hạn tần suất gọi đúng; `CoarseLocationSource` dọn listener; `WeatherStore` nuốt lỗi file hỏng.
- **Cache icon trên đĩa và cache nhãn:** ghi nguyên tử (file tạm rồi đổi tên), khóa có user.
- **Manifest:** chỉ `HomeActivity` được export; `<queries>` thay cho `QUERY_ALL_PACKAGES`.
- **Shortcut khi là launcher mặc định (đợt 2):** menu nhấn giữ Chrome ở dock hiện đúng "Thẻ ẩn danh mới", "Thẻ mới". Tìm "chrome" không kèm shortcut là đúng vì máy đang tắt mục shortcut trong cài đặt Tìm kiếm (`search_shortcuts = false` trong DataStore).
- **Dựng lại Home ngoài edit mode (đợt 2):** đổi chế độ tối hai lần → vẫn 1 Activity, không rò.

## Quyết định của chủ dự án (2026-11-11)

| Mục | Quyết định |
|---|---|
| Phạm vi | Sửa **tất cả** mục không xung đột nhau, theo đợt; tối đa 4 agent chạy cùng lúc, điều phối xếp việc |
| Plan | Điều phối rà và **tự duyệt** plan nếu không có gì sai; chỉ câu hỏi thuộc quyền chủ dự án mới chuyển lên |
| Kiểm và merge | Sau mỗi bản sửa, điều phối kiểm lại trên Live 4 (sao lưu dữ liệu trước) và tick checklist; chủ dự án kiểm lần cuối và merge PR |
| B10 | Nút Home **và** rời launcher (thông báo, Gần đây, `onStop`) đều thoát edit mode, như iOS. Bảng chọn widget và bảng thời tiết đóng khi bấm Home |
| C20 | App mới đặt **sau icon cuối cùng** (trang cuối còn chỗ), không lấp ô trống giữa trang. Gỡ app vẫn dồn lại ô app đó để lại |
| C15 | **Hoãn tới trước khi phát hành**: không sửa code đợt này; ghi vào mục "Trước khi phát hành" của `AGENTS.md`. Chủ dự án có thể giới hạn hoặc xoay khóa trên trang OWM |
| Kiểm trên Live 4 | Được làm, có sao lưu và trả lại: danh bạ **chỉ dùng dữ liệu giả** (chủ dự án 2026-11-11: không cấp `READ_CONTACTS`, không chèn vào danh bạ máy; dùng nguồn danh bạ giả tạm trong bản debug, gỡ trước PR); được tạm bật mục shortcut trong Cài đặt tìm kiếm (C8); tạm đổi launcher mặc định và từ chối "Không hỏi lại" (B12); hồ sơ công việc tạm bằng Test DPC (C10); APK thử tự dựng (A3, A4, C20) |

## Kế hoạch sửa

Mỗi nhánh một plan trong thư mục này (`docs/report/<ID>-<slug>.md`), theo mẫu ở `MultiAgent-Workflow.md` mục 7. Gom theo vùng file để không hai nhánh cùng sửa file độc quyền (mục 5.2).

| ID | Nhánh | Mục | Vùng file chính (★ = độc quyền) | Đợt |
|---|---|---|---|---|
| FX1 | `fix/fx1-wallpaper-safety` | A1, A5, B2, C3, C4, C5 (+ tách `decodeScaled` vào `core/bitmap`) | `data/backdrop/*`, ★`core/glass/*`, `BackdropController`, `DockController`, ★`HomeViewModel` (phần ảnh nền), `SettingsViewModel` | 1 |
| FX2 | `fix/fx2-edit-mode-home` | A2, B10, C13, C16, C19 | `Jiggler`, `IconGridView`, `DockView`, `FolderPanelView`, ★`HomeActivity`, `WidgetPickerController`, `WeatherSheetController`, `SystemBarsController` | 1 |
| FX3 | `fix/fx3-layout-reconciler` | A3, B3, B4, B5, C17, C20, B1 (phần flow bố cục) | ★`LayoutReconciler`, `LayoutRepositoryImpl`, ★`data/layout/db/*`, `ChangeGridPresetUseCase`, `GridInsertPlanner` | 1 |
| FX4 | `fix/fx4-search-library` | B11, C1, C6, C7, C8, C9 | `ui/search/*`, `data/search/*`, `domain/search/*`, `GetCategoryGroupsUseCase`, `ShortcutRepositoryImpl` | 1 |
| FX5 | `fix/fx5-icons-labels` | A4, B7, B8, B9, C2, C10, C11 | `data/apps/*`, `AppItem`, map icon trong `IconGridView`/`DockView`/`FolderPanelView`, các adapter | 2 (sau FX2, FX4) |
| FX6 | `fix/fx6-robustness-settings` | B1 (scope app, thời tiết), B12 (+ interface cho `CoarseLocationSource`) | `di/AppModule`, `data/weather/*`, `ui/weather/*`, `data/system/*`, `ui/settings/SettingsActivity` | 2 |
| FX7 | `fix/fx7-home-gestures` | B6, C12, C14, C18, D2 (chuyển cảnh trang cuối → Library) | `HomePagerController`, `IconMenuController`, ★`DragHelper`, `DragLayer`, `FolderPanelView`, ★`HomeViewModel` | 2 (sau FX1, FX2) |

| FX8 | `fix/fx8-glass-backdrop-match` | D1, D2 (kính Library vẽ lại sau khi đáp) | ★`core/glass/*`, `CategoryPageView` (phần kính) | 2 |

C15 không có nhánh (hoãn). FX1 làm A1 đầu tiên: sửa nhỏ, chặn lỗi duy nhất làm người dùng kẹt hoàn toàn.

## Nhật ký kiểm trên máy (2026-11-10, Live 4)

| Giờ | Việc | Kết quả |
|---|---|---|
| 14:47 | Sao lưu `databases/`, `files/`, `no_backup/` | 8 file, md5 khớp |
| 14:50 | Đo CPU rảnh và ở nền, `dumpsys meminfo` | Xem "Đã kiểm và ổn" |
| 14:55 | A1: thay ảnh nền bằng JPEG 12000x3000, mở Home | Sập `trying to draw too large(144000000bytes)`; trả file, md5 khớp |
| 14:56 | B2: cất ảnh, `cmd uimode night no` | Ảnh nền vẫn tối; trả chế độ tối và ảnh, md5 khớp |
| 14:58 | A3: sửa className hàng Chrome ở dock trong bản sao DB, mở Home | Dock 3 icon, hai hàng Chrome trong DB; trả DB, 8 file khớp |
| 15:00 | A2: vào/thoát edit mode, đo CPU; đổi tối hai lần trong edit mode | Bảng số ở A2; `Activities: 3`. Chế độ tối trả về bật, đã `force-stop` app |
| 15:10 | Đợt 2: chủ dự án đặt làm launcher mặc định; sao lưu bản 2 | 8 file, md5 khớp; DataStore khác bản 1 (thay đổi của chủ dự án) |
| 15:12 | B10: edit mode + phím Home; bảng "Tiện ích" + phím Home | Cả hai không đóng; thấy C19 |
| 15:14 | Shortcut: tìm "chrome", nhấn giữ Chrome ở dock | Menu có shortcut; tìm kiếm tắt shortcut theo cài đặt |
| 15:16 | C1: Library → app Cài đặt 9 s → Back, quay màn hình | Không thấy vòng quay |
| 15:19 | C3: `send-trim-memory COMPLETE` khi ở nền; đo bộ nhớ theo bước | Số ở C3; trước đó process có 3 Activity, Native 113 MB (A2) |
| 15:21 | Đối chứng A2: đổi tối hai lần ngoài edit mode | 1 Activity, không rò. Chế độ tối trả về bật |
| 15:31 | B9: `en-US` rồi về `vi-VN` bằng công cụ `app_process` | Chiều về `vi-VN` giữ nhãn tiếng Anh; ngôn ngữ trả `vi-VN`, công cụ đã xóa khỏi máy |
| 15:37 | Sao lưu bản 3; cài app thử v1 | 8 file khớp; app ở trang 2 ô (0,4) |
| 15:38 | A4: `install -r` v2, tìm kiếm, khởi động lại | Home đỏ, tìm kiếm xanh, sau khởi động lại xanh |
| 15:40 | B6: vuốt chậm + `pm disable-user` app thử, quay màn hình; `pm enable` lại | Trang giật về 0 giữa lúc kéo |
| 15:41 | Gỡ app thử; so DB với bản 3; trả DB | 3 icon bị dồn (C20); sau khi trả, 8 file khớp bản 3, Home chạy, không crash |
