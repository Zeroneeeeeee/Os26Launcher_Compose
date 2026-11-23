# PG — Việc còn thiếu so với bản XML (parity gaps) — danh sách việc

> Lập 2026-11-23 khi chuyển hẳn sang repo Compose (`AGENTS.md` mục "Dự án hiện hành"). Nguồn: đối chiếu plan hai repo, mục "Open" của chuỗi plan Compose (JC→JX, TF), `report/Audit-Report-2026-11-10.md` của repo XML, so code `domain`/`data`/`core` từng file và đọc thẳng các điểm nghi ngờ. **Mọi mục là đọc code, chưa kiểm trên máy** (trừ G2: chủ dự án gặp trên máy).
> Đây là danh sách để chọn việc, không phải plan: mỗi mục khi làm thì viết plan riêng (hoặc thêm Task vào plan đang mở) theo `Workflow.md`. Làm xong mục nào thì tick và ghi plan/commit.

**Trạng thái:** Mở · **Cập nhật:** 2026-11-23 (gộp mã lần 1)

## 1. Tính năng

- [x] ~~**F1 — RA: "Xóa khỏi Màn hình chính" khác "Ẩn app"**~~ — **bỏ khỏi danh sách** (chủ dự án 2026-11-23): đang triển khai ở `TF-test-fixes.md` Task 5. Lưu ý giữ ở đó: schema v3 khớp `IosLauncher/app/schemas/.../3.json`, cập nhật `room-schema.md`/`config-schema.md`.
- [ ] **F2 — Kéo thẻ widget từ bảng chọn ra lưới Home** (P2-9). `HomeWidgetPicker.kt:104` ghi "Chưa port". Bảng chọn đang là cửa sổ riêng (`dsDialogs` → `Dialog`) nên chuỗi chạm không tới lưới. **Chốt hướng B** (chủ dự án 2026-11-23): vẽ bảng chọn thành lớp phủ trong cây Compose của Home, nhấn giữ thẻ thì cùng chuỗi chạm đi vào `HomeDragState` (phiên widget mới, VM đã có hàm thả widget chưa có hàng DB, G2c Task 13). Phải tự làm thay `Dialog`: Back đóng bảng, lớp tối + chạm ngoài để đóng, hoạt ảnh mở/đóng, thứ tự lớp với Thư viện/Today, bảng mở từ Today (`forToday`), ẩn thanh trạng thái trong edit mode (C19). Cử chỉ mới: bắt buộc kiểm tay.
- [ ] **F3 — Kiểu chuyển cảnh chưa áp cho thư mục và Thư viện.** Thư mục chỉ theo thời lượng, luôn kiểu Mượt (JH3b); Thư viện không đọc `TransitionMotion`. XML áp FADE/SLIDE/ZOOM/NONE qua `PanelMotion`.
- [ ] **F4 — Ẩn cài đặt kính mà máy / thư viện không hỗ trợ** (gộp F4 + G3 cũ; chủ dự án 2026-11-23: coi là giới hạn của thư viện, ẩn hẳn thay vì làm). Backdrop: dưới API 31 không có hiệu ứng nào, API 31–32 có làm mờ nhưng không khúc xạ (`DsGlass.kt`). Đề xuất khi làm:
  - "Mức kính" (Tự động/Thấp/Cao/Tùy chỉnh) và công tắc kính từng phần tử (dock, panel, thanh tìm kiếm, pill, menu): Compose không đọc (JX Q3) → ẩn ở mọi máy, cùng hộp cảnh báo API < 33 (`gs_warn_*`).
  - Màn "Tinh chỉnh kính" (`GlassTuningActivity`): API < 31 ẩn Độ mờ, Khúc xạ, Độ bóng, chỉ giữ Bo góc (bo góc vẫn áp cho mọi bề mặt `DsCorners`); API 31–32 ẩn Khúc xạ.
  - Kiểm lại hàng vào màn ở Cài đặt hub còn ý nghĩa không khi chỉ còn Bo góc.
  - Ghi chú: kính mờ tĩnh kiểu XML (`BackdropBlurDrawable`, `BlurredBackdropCache`, vẫn còn trong repo) làm được dưới API 31 nếu sau này muốn; chưa làm.
- [x] **F5 — Logo app mới** — **xong** (chủ dự án, commit `22e436e`). Claude kiểm 2026-11-23: 20 file `mipmap-*` + `values/ic_launcher_background.xml` trùng md5 với repo XML; `drawable/ic_launcher_background.xml` khác nhưng không dùng (adaptive icon trỏ `@color/ic_launcher_background`).
- [x] ~~**GX — Tùy chỉnh kính đầy đủ**~~ — **bỏ** (chủ dự án 2026-11-23): code XML viết cho kính View QWEA0, Compose dùng Backdrop.

## 2. Lỗi cử chỉ, hiệu năng

- [ ] **G1 — Lướt nhanh liên tục không sang được Thư viện / Today** (gộp G1, G2, O1 cũ).
  - Cổng mở `HomePagerInfo.settledOnLastPage`/`settledOnFirstPage` đòi `!scrolling`. XML (FX7 D2, TV-1) cho mở cả khi pager đang trượt nốt tới đúng trang cuối/đầu (đích lấy từ vị trí cuộn), chỉ từ chối khi tay đang kéo pager; đo Live 4 18/18. Compose: dùng `pagerState.targetPage` + trạng thái đang kéo.
  - Thiếu "trượt nốt" khi Thư viện/Today tiếp quản giữa lúc pager trượt (XML `finishSettleOnLastPage`/`finishSettleOnFirstPage`, `HomeTouchRouter.kt:82,95`): thiếu thì Home có thể kẹt ở trang áp chót khi đóng Thư viện.
  - Cú vuốt mất chưa giải thích ở JH2 có thể chính là lỗi này: kiểm lại sau khi sửa.
- [x] ~~**G2 — Kéo app (edit mode) sang trang tạm rồi kéo về trang cuối: pager dừng lệch**~~ — **bỏ khỏi danh sách** (chủ dự án 2026-11-23: người khác xử lý). Ghi chẩn đoán để người làm tham khảo (chủ dự án gặp 2026-11-23). Điểm gãy nghi (đọc code, chưa đo): `HomeScreen.kt` thu `pagerState.currentPage` và gọi `onGhostPageLeft()` ngay khi trang hiện tại đổi về trang thật. Trong lúc `HomeDragState.flipTo` đang `animateScrollToPage` từ trang tạm về, `currentPage` đổi ở giữa đường → VM bỏ trang tạm → `pages.size` giảm khi hoạt ảnh còn chạy → pager bị kẹp vị trí cuộn và dừng không đúng mép trang. (XML cũng bỏ ở `onPageSelected` nhưng `ViewPager2` chịu được danh sách đổi giữa lúc cuộn.) Hướng: chỉ bỏ trang tạm khi pager đã đứng yên (`!isScrollInProgress`) và đang ở trang thật; hoặc sau khi `flipTo` xong.
- [ ] **G3 — Hiệu năng: số kính mỗi trang và lướt trang** (gộp G4, G7 cũ). Kính thật trên mọi thẻ/widget khi API ≥ 31, không hạ theo máy, `GlassBudget` không màn Compose nào gọi (JD D9: ~15 bề mặt kính thật = 38 % khung giật trên Oppo). Lướt trang trên Samsung p90 +5 ms so với bản View (JH1/JH2/JH4 "follow-up plan"). Đo trước rồi mới quyết.
- [ ] **G4 — Kính mờ tĩnh cho ô thư mục, pill tìm kiếm, icon kiểu "Kính"** (gộp G5, G6 cũ). Hiện phẳng: `HomeFolderPlate` `.background(folder_background)`, pill `.background(search_pill_bg)`, icon "Kính" mất tấm kính phía sau (GI Task 4). Không dùng kính thật (giới hạn hiệu năng ở G3); dùng kính mờ tĩnh như XML, rẻ, không vướng D9.

- [ ] **A1 — Hoạt ảnh khi bố cục lưới / dock / thư mục đổi** (AN Task 8, 9d của repo XML; chủ dự án thấy 2026-11-23: thả nhanh icon lên ô của icon khác, chưa tạo thư mục, các icon nhảy cóc). Đọc code 2026-11-23:
  - Lưới Home chỉ trượt khi có xem trước đẩy-dồn lúc đang kéo (`homeItemDecor` đọc `drag.preview`). Dữ liệu đổi mà không qua xem trước thì icon nhảy thẳng tới chỗ mới: thả nhanh trước `PUSH_ARM_MS`, gỡ/ẩn rồi dồn ô, bỏ ẩn, cài app mới, tạo/giải tán thư mục, dọn trang trống khi thoát edit mode, đổi cỡ lưới. XML làm bằng FLIP trong `IconGridView` (chụp vị trí cũ trước `setItems`, trượt về chỗ mới). Dock và panel thư mục của Compose đã trượt theo dữ liệu (offset theo `itemId`), chỉ lưới thiếu.
  - Icon rời đi (gỡ, ẩn, bỏ khỏi Home) không thu về 0, app mới cài không phình từ 0, widget đầu tiên trên trang trống không phình (XML `LeavingIcons`, `popIn` ở lưới, dock, panel thư mục; các icon sau trượt sau khi thu xong ~200 ms). Compose chỉ màn Today có.
  - Đã có, không thiếu: mở/đóng thư mục từ icon, màn nhóm Thư viện phình từ ô + Thư viện co/mờ phía sau, vuốt Home ↔ Thư viện/Today, kính hover + phóng thư mục, hạ cánh khi thả, rung edit mode (cả trong thư mục), pill đổi dạng, chấm trang, menu nhấn giữ, sắp xếp trong thư mục và dock.

## 3. Kiểm trên máy (việc còn mở của các plan Compose)

- [ ] **K1 — Danh sách kiểm chưa chạy:**
  - JH2: kéo icon ra khỏi trang tạm (RF2), kéo bằng hai ngón (RF3).
  - JH4: vẫn là launcher mặc định sau khi cài bản Compose đè lên (RF1); hai hiện tượng mới gặp một lần — Home mở thẳng vào Thư viện sau khi bỏ qua Onboarding rồi bấm "Trải nghiệm", pager đứng sai trang sau khi áp theme.
  - JH3d: widget có màn cấu hình riêng; thoát chế độ sửa khi bảng chọn widget đang mở.
  - JH3a: tổng huy hiệu trên ô thư mục, tắt huy hiệu, quyền danh bạ.
  - JC/JD: chạy trên Android dưới 12 (máy Android 11 đã dùng 2026-11-23; cũng là chỗ kiểm F4); cỡ chữ lớn nhất + chữ đậm, TalkBack, xoay màn hình.

## 4. Tài liệu

- [ ] **D1 — Chép bài học từ repo XML:** FM, DA, TV, RA trong `IosLauncher/docs/lessons/drag-and-layout.md`; mục mới ở `icons-and-app-list.md`, `widgets.md`, `settings-and-data.md`, `permissions-and-device-testing.md`.

## Không thể migrate

Không có tính năng nào. Giới hạn có thật đều của nền tảng / thư viện kính, bản XML cũng chịu: kính thật cần API 31, khúc xạ cần API 33 (xử lý bằng F4); làm mờ app phía sau Trung tâm điều khiển chỉ từ Android 12 (CC-1, backlog XML); không đọc được hình nền hệ thống từ Android 13.

## Bảng mã cũ → mới (gộp 2026-11-23)

| Cũ | Mới |
|---|---|
| F1, D2 | F1 (bỏ: làm ở TF Task 5) |
| F4, G3 | F4 |
| F5 (GX) | bỏ |
| F6 | F5 (xong `22e436e`) |
| G1, G2, O1 | G1 |
| — (lỗi mới) | G2 (bỏ: người khác xử lý) |
| G4, G7 | G3 |
| G5, G6 | G4 |
| O2–O7 | K1 |
| D1 | D1 |

## Nhật ký

| Ngày | Mục | Ghi chú |
|---|---|---|
| 2026-11-23 | — | Lập danh sách; GX bỏ theo chủ dự án |
| 2026-11-23 | — | Gộp mã (bảng trên); F4 đổi thành "ẩn cài đặt không hỗ trợ"; thêm G2 (lỗi chủ dự án báo) |
| 2026-11-23 | F1, F5 | F1 bỏ (đang làm ở TF Task 5); F5 xong (`22e436e`, đã kiểm) |
| 2026-11-23 | G2 | Bỏ khỏi danh sách: người khác xử lý; giữ phần chẩn đoán |
| 2026-11-23 | F2, A1 | F2 chốt hướng B; thêm A1 (hoạt ảnh bố cục, so code hai repo) |
