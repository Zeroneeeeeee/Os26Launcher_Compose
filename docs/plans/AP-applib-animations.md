# AP — Hoạt ảnh App Library: panel nhóm ở tâm màn hình và vuốt Home ↔ Library — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x khi cần kính thật). Lệnh viết cho PowerShell.

**Nhánh:** `feature/ap-applib-animations` · **Worktree:** `E:\SourceTree\IosLauncher-wt\AP-applib-animations` · **PR:** (draft, xem knowledgement) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (chủ dự án cho làm ngay, "khó mô tả nếu không có video, làm rồi chỉnh sau")
**Trạng thái:** Task 5 (vòng 2, video iOS 27) đã code, chờ kiểm tay. Task 1–3 xong trên Oppo, chờ chủ dự án chỉnh hằng số; Task 4 (Library chạy dưới thanh điều hướng) đã code, chờ kiểm tay
**Khóa:** không (không đụng schema)

**Goal:** (1) Panel nhóm của App Library mở/đóng ở TÂM màn hình: panel co 75% → 100% và hiện dần, App Library phía sau co 100% → 75% và mờ dần; đóng là đảo ngược. (2) Vuốt từ trang Home cuối sang App Library (và ngược lại) theo ngón tay: Home trượt trái và mờ, App Library trượt vào và hiện dần.
**Architecture:** Không thêm thư viện. Dùng lại `SpringProgress`, `PanelMotion`, `BackgroundGlass.applyMotion` của AN và `LibraryOverlayController` của G2a B2. Mọi hằng chỉnh tay gom ở một object `ui/home/view/AppLibraryMotion`.
**Tech Stack:** androidx.dynamicanimation 1.1.0 (đã có), View property animation.
**Spec:** AN (`plans/AN-polish-animation.md`, `knowledgements/AN-polish-animation.md`), G2a Phần B (`plans/G2a-folders-library.md`), TL1 Task 2b (dock kính).

## Phát hiện quan trọng khi đọc code (lệch so với đề bài)

App Library **không phải trang của `ViewPager2`**. Từ G2a B2 (D17) nó là lớp phủ ngoài pager (`library_view`), do `LibraryOverlayController` bắt cử chỉ ở `dispatchTouchEvent` rồi tiếp quản (gửi `ACTION_CANCEL` giả cho pager); một số `progress` 0..1 điều khiển trượt/tối nền/mờ Home trong `AppLibraryController.applyLibraryProgress`. Vì vậy:
- **Không dùng `ViewPager2.PageTransformer`** (không có trang Library để biến đổi, và nó sẽ áp cho mọi cặp trang). Phần 2 sửa thẳng `applyLibraryProgress`, vốn đã "tương tác theo ngón tay".
- Hiện trạng: Home chỉ mờ (`homeContent.alpha = 1 - p`) và đứng yên; Library trượt vào KHÔNG mờ. Yêu cầu mới: Home trượt trái + mờ, Library mờ vào.

## Vì sao chọn thiết kế này

**Vấn đề cần giải:** hai chuyển động phải theo spring/tiến độ có sẵn, ngắt giữa chừng được, chặn chạm đúng lúc, không làm kính thật chụp lại mỗi khung (AN Task 9: `setCaptureAtLayoutPosition`, `captureOnce`), không động tới kính dock khi pager cuộn (TL1 Task 2b), không đổi cỡ layout của `IconGridView` (bài học M5: pager đổi kích thước làm lưới tính lại Cells).

### Phần 1 — panel nhóm ở tâm màn hình

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Thêm chế độ `centered` cho `PanelMotion` + callback tiến độ; `AppLibraryController` gộp co/mờ cho `library_view` | Panel co quanh tâm (pivot mặc định), `glass.applyMotion(scale, 0, 0, alpha)` như cũ; Library phía sau nhận scale/alpha từ cùng `p` | Dùng nguyên spring, ngắt giữa chừng, kính thật co như texture, một nguồn `p` cho cả hai lớp; `FolderPanelView` không đổi | `BackdropBlurDrawable` chỉ bù scale của chính owner, không bù scale của tổ tiên: kính mờ trong ô/thanh tìm kiếm của Library lệch nhẹ so với nền khi Library co (xem rủi ro) | Có |
| B. Chạy hai animator riêng (panel và Library) | Library tự có `SpringProgress` riêng | Tách bạch | Hai spring lệch pha khi ngắt giữa chừng; thêm trạng thái; đảo chiều không đồng bộ | Không |
| C. `ViewPropertyAnimator`/`ValueAnimator` thời lượng cố định | Đường cong theo thời gian | Dễ chỉnh thời lượng | Mất giữ vận tốc khi đảo chiều (bài học AN mục 2); không giống phần còn lại của AN | Không |

**Vì sao A:** đảo chiều giữa chừng của spring giữ vị trí và vận tốc (đọc tài liệu + AN, đo được ở AN Task 3), và một `p` duy nhất làm cho panel với Library luôn đối nghịch chính xác. Đánh đổi: nhánh `centered` làm `PanelMotion` có hai chế độ.

**Lớp Library là một khối:** thanh tìm kiếm là con của `CategoryPageView` nên co/mờ theo `library_view` mà không code thêm.

### Phần 2 — vuốt Home ↔ Library

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Sửa `applyLibraryProgress`: `home_pager.translationX`, mờ `homeContent` theo dải, mờ `library_view` theo dải | Cùng `progress` đang theo ngón tay | Không đổi kiến trúc, theo ngón tay và có spring/animator thả sẵn; pager thường không bị đụng | Home phải trượt đồng thời một `translationX` mới | Có |
| B. `PageTransformer` trên `ViewPager2` + thêm trang Library vào pager | Library là trang pager | Đúng nghĩa "cặp trang" | Đây chính là kiến trúc G2a đã bỏ sau khi phải vá liên tiếp (AGENTS.md "Màn không phải lưới thì đừng làm một mục của ViewPager2 lưới"). Không | Không |
| C. Chỉ mờ, không trượt Home | Như hiện trạng + mờ Library | Ít rủi ro nhất | Không đúng yêu cầu "trượt trái" | Không |

**Dock và chấm trang (quyết định):** mờ cùng Home theo cùng dải nhưng **không trượt** (chỉ `home_pager` trượt). Lý do: dock là kính thật/mờ tại chỗ, dịch nó cần xử lý vị trí chụp nền; để đứng yên thì kính dock không đổi vị trí nên không vi phạm quy tắc TL1 (không invalidate kính dock theo cuộn: ở đây pager không cuộn, `SCROLL_STATE` không đổi nên cờ chặn không bị đụng). `homeContent.alpha` vốn đã mờ cả khối từ G2a nên giữ. Giả định về iOS (dock biến mất khi sang App Library) chưa kiểm chứng.

## Quyết định đã chốt
| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Làm ngay không chờ duyệt plan | Chủ dự án, 2026-11-10 |
| 2 | Panel nhóm mở/đóng ở tâm, 75% ↔ 100%, mờ; Library ngược lại | Tech lead, đề bài AP |
| 3 | Không `PageTransformer`; sửa `applyLibraryProgress` | Library là lớp phủ ngoài pager (G2a D17) |
| 4 | Dock và chấm trang mờ cùng Home, không trượt | Mục trên |
| 5 | Đang đóng panel vẫn không chặn chạm (đảo chiều bằng bấm ô); đang mở và đã mở thì chặn | Giữ hành vi AN Task 4; xem Review Focus |
| 6 | Mọi hằng ở `AppLibraryMotion` | Chủ dự án tự chỉnh sau khi xem |

## Ngoài phạm vi / ghi để sau
- Bù scale tổ tiên trong `BackdropBlurDrawable` cho kính mờ trong Library đang co (nếu chủ dự án thấy lệch).
- Icon con bay ra kiểu iOS; panel thư mục (`FolderPanelView`) không đổi.
- Chỉnh thời lượng thả cử chỉ (`LibraryOverlayController.MAX_SETTLE_MS`), chưa đụng.

## Vùng file
| File | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/view/AppLibraryMotion.kt` | Tạo | không |
| `ui/home/view/PanelMotion.kt` | Sửa (thêm `centered`, `spec`, `onProgress`) | dùng chung hai panel, `FolderPanelView` giữ mặc định |
| `ui/home/view/CategoryGroupPanelView.kt` | Sửa | độc quyền |
| `ui/home/controller/AppLibraryController.kt` | Sửa | độc quyền |
| `docs/plans/AP-applib-animations.md`, `docs/knowledgements/AP-applib-animations.md` | Tạo | không |

## Global Constraints
Không đổi schema, không thêm thư viện/test, không đụng `AGENTS.md`/tài liệu chung. Hoạt ảnh hệ thống tắt thì `SpringProgress.animateTo` thành `snapTo` (panel); phần vuốt dùng `ValueAnimator` nên tự theo `animator_duration_scale`.

## Review Focus
1. `PanelMotion.apply`: nhánh `centered` gọi `glass.applyMotion(scale, 0, 0, alpha)`; kính thật KHÔNG `invalidate()` mỗi khung (Task 9 của AN giữ nguyên).
2. `AppLibraryController.applyLibraryLayer`: một chỗ duy nhất đặt alpha/scale của `library_view`, gộp vuốt và panel (tránh hai nơi ghi đè nhau).
3. Chặn chạm: `CategoryGroupPanelView.dispatchTouchEvent` (đang đóng trả false). Có nên chặn hẳn khi đóng không? Xem kiểm tay.
4. `home_pager.translationX` được đặt về 0 khi `p = 0` (nếu không, Home kẹt lệch sau khi đóng Library).
5. Khôi phục sau `recreate()` khi panel đang mở: `snapTo(1)` phải co Library ngay (callback `onMotionProgress` trong `apply`).

---

### Task 1: Hằng chỉnh tay và panel nhóm ở tâm
**Bắt buộc kiểm tay:** có — cảm giác chuyển động, kính (mục 4.2).
**Files:** `AppLibraryMotion.kt`, `PanelMotion.kt`, `CategoryGroupPanelView.kt`.
**Interfaces:** Produces `AppLibraryMotion` (hằng + `ramp`), `PanelMotion(centered, spec, onProgress)`, `CategoryGroupPanelView.onMotionProgress`.
- [x] Step 1: `AppLibraryMotion` gom hằng (scale 0.75, dải mờ, spring, dải vuốt).
- [x] Step 2: `PanelMotion` thêm nhánh `centered`; `CategoryGroupPanelView` dùng, bỏ `setOrigin(tile)`.
- [x] Step 3: build xanh.
- [~] Step 4 (Oppo đã kiểm 2026-11-10 phần mở/đóng và Library co lại khi đóng; chưa kiểm: Live 4, xoay màn hình, tắt hoạt ảnh, ngắt giữa chừng, nhảy kính từng khung): Kịch bản kiểm (Live 4, bắt buộc kiểm tay): bấm ô nhóm → panel phình từ giữa, Library co lùi; bấm nền/Back → ngược lại; bấm lại giữa chừng; mở rồi đóng nhanh liên tiếp; tắt "remove animations" thì không hoạt ảnh; xoay màn hình khi panel mở. Oppo: kính thật không giật/nhảy ảnh lúc dừng.

### Task 2: Library co/mờ phía sau panel
**Bắt buộc kiểm tay:** có (gộp Task 1).
**Files:** `AppLibraryController.kt`.
- [x] Step 1: `onMotionProgress` → `panelProgress`; `applyLibraryLayer()` đặt alpha/scale cho `library_view`.
- [x] Step 2: build xanh.
- [~] Step 3 (Oppo: Library co/mờ ngược lại khi đóng đúng; chưa kiểm chạm lúc đang mở): kiểm: thanh tìm kiếm co/mờ cùng Library; chạm lúc panel đang mở không lọt xuống Library.

### Task 3: Vuốt Home ↔ Library
**Bắt buộc kiểm tay:** có — vuốt tay.
**Files:** `AppLibraryController.kt`.
- [x] Step 1: `applyLibraryProgress`: `home_pager.translationX`, mờ `homeContent` và `library_view` theo dải.
- [x] Step 2: build xanh.
- [x] Step 3 (Oppo 2026-11-10: kéo ~30% và ~60%, thả hoàn tất vào Library, 3 vòng khứ hồi không lỗi; chưa kiểm riêng vuốt về và Home trở lại đúng chỗ bằng mắt ngoài số đo): kiểm: kéo chậm từ trang Home cuối, nhìn Home trượt trái mờ dần, Library hiện dần; thả giữa chừng (nửa quãng, fling); vuốt ngược; trang Home thường vuốt như cũ; dock không giật khi vuốt; sau đóng Library, Home trở lại đúng chỗ (không lệch).

### Task 4: Library chạy xuống đáy màn hình, dưới thanh điều hướng
**Bắt buộc kiểm tay:** có — nhìn dải mờ trên máy thật, cả điều hướng cử chỉ và 3 nút, cả hai hướng (mục 4.2).
**Yêu cầu (chủ dự án, 2026-11-10):** nội dung cuộn của Library (lưới ô nhóm) vẽ sát đáy vật lý, sau thanh điều hướng; phần dưới thanh hơi mờ/dịu (kiểu iOS); hàng cuối vẫn cuộn lên khỏi thanh; thanh tìm kiếm và inset trên không đổi.
**Why — phương án:**
| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| A. Dải gradient alpha (trong suốt → đen) cao bằng inset đáy + 24dp, một `View` | Một lệnh vẽ, không chụp nền, không phụ thuộc API; chỉ tồn tại trong `library_view` (đóng thì GONE, chi phí 0) | Làm tối chứ không làm nhòe thật | Có |
| B. Kính mờ tĩnh (`BackdropBlurDrawable`) làm dải | Đúng nghĩa "frosted" | Mờ nền (ảnh) chứ không mờ nội dung Library nằm TRÊN nó; thêm một ô kính vào ngân sách kính và vẽ lại khi cuộn | Không |
| C. `RenderEffect` blur lên phần đáy của lớp cuộn | Nhòe đúng nội dung | Chỉ API 31+, Live 4 là API 30; blur nội dung cuộn tốn mỗi khung | Không |
Chọn A: rẻ nhất, chạy mọi tier; nội dung "dịu" dần vào màu tối cùng họ với scrim của Library.
**Files:** `view_category_page.xml` (thêm `category_nav_fade`), `CategoryPageView.kt` (`setBottomInset`, `availableHeight`), `SystemBarsController.kt` (padding đáy 0, báo inset), `AppLibraryMotion.kt` (hằng).
**Hằng:** `NAV_FADE_EXTRA_DP` = 24 (dải cao thêm ngoài inset), `NAV_FADE_MAX_ALPHA` = 0.6 (độ đen ở mép đáy; 0 = tắt).
**Thiết kế:** padding đáy của `library_view` = 0 nên `scroll` tràn tới đáy; đệm đáy của `scroll` = inset thanh điều hướng với `clipToPadding=false` (đã có) nên hàng cuối cuộn lên trên thanh. Cỡ ô vẫn tính trên chiều cao TRỪ inset đáy (`availableHeight`) nên ô không co/giãn. Inset đáy lấy từ `getInsetsIgnoringVisibility(systemBars)` như trước (cử chỉ và 3 nút đều đi qua đường này; hướng ngang đổi inset thì listener tự gọi lại).
- [x] Step 1: code, build xanh.
- [ ] Step 2: Kịch bản kiểm (bắt buộc kiểm tay): mở Library, cuộn: nội dung thấy được dưới/qua thanh điều hướng với dải tối dần; cuộn hết cỡ thì hàng cuối nằm trên thanh; điều hướng cử chỉ và 3 nút; xoay ngang (inset đổi, ô không nhảy cỡ); thanh tìm kiếm và đỉnh không đổi; đóng/mở Library và panel nhóm không thấy dải; Home không đổi.

### Task 5: Round 2 — iOS 27 video (group screen full screen, mở từ ô, Library nhoè phía sau, bỏ "+N")
**Bắt buộc kiểm tay:** có — cảm giác chuyển động, nhoè, hai máy (Live 4 API 30 không nhoè; Oppo API 34 có nhoè).
**Nguồn:** `docs/animation_app_lib.mp4` (tech lead, iOS 27), khung 30 fps. Chủ dự án chốt 2026-11-10: bỏ khung kính, màn nhóm toàn màn hình.
**Why — mỗi quyết định gắn với khung nào:**
| Quyết định | Khung | Ghi chú |
|---|---|---|
| Ô Library không có chữ "+N": góc thứ tư là lưới 2x2 tối đa 4 icon nhỏ | #21–#41 (Library lúc nghỉ) | Nhóm ít app hơn thì hiện đúng số có |
| Mở ~0.27 s, có lố nhẹ rồi yên | #41 chạm → #49 yên | Spring 450 / giảm chấn 0.82 (thời gian yên lý thuyết ~0.23 s, lố ~1%) |
| Tiêu đề + lưới mọc ra TỪ ô được bấm (gốc = ô, không phải tâm), co/dịch từ ô tới chỗ cuối | #42–#44 icon hiện trên ô; #45 ~65%; #47 ~85%; #49 100% | `PanelMotion` với `setOrigin(tileScreenRect)`, đóng về đúng ô đã nhớ lúc mở |
| Panel hiện dần rất nhanh ở đầu | #42–#43 | `GROUP_PANEL_FADE_END = 0.3` |
| Library phía sau mờ + nhoè, KHÔNG giữ nguyên cỡ: thu nhẹ (~0.93 ở #42, ~0.9 ở #43, ~0.81 ở #44, đo bằng bề rộng thanh tìm kiếm) rồi tan hết ở #46–#47 | #42–#47 | Lệch so với bản đọc đầu ("không co"): `LIBRARY_BEHIND_END_SCALE = 0.85`, fade 0→0.7 |
| Thanh tìm kiếm mờ nhanh nhất (cùng lớp Library) | #44–#45 | Một lớp, không code riêng |
| Không khung kính; tiêu đề lớn đậm góc trái, lưới 4 cột nhãn nhỏ | #49–#69 | Chủ dự án quyết 2026-11-10; kính GROUP_PANEL không còn dùng ở màn này |
| Đóng là đảo ngược chính xác | #69–#77 | Cùng spring đảo chiều; Library nhoè gỡ về 0 khi yên |
**Files:** `CategoryPageView.kt`, `item_category_group.xml`, `strings.xml` (bỏ "+N"); `CategoryGroupPanelView.kt` (màn toàn màn hình, nhớ gốc ô), `PanelMotion.kt` (`glass` nullable, `fadeEnd`, `scrimMaxAlpha`), `AppLibraryMotion.kt`, `AppLibraryController.kt` (`applyLibraryBlur`), `SystemBarsController.kt` (inset cho màn nhóm), `dimens.xml`.
**Hằng:** `GROUP_PANEL_FADE_END` 0.3 · `LIBRARY_BEHIND_END_SCALE` 0.85 · `LIBRARY_BEHIND_FADE_START/END` 0 / 0.7 · `LIBRARY_BEHIND_BLUR_DP` 16 · `BLUR_STEP_PX` 1 · `GROUP_DIM_WITH_BLUR` 0.35 · `GROUP_DIM_NO_BLUR` 0.65 · `GROUP_PANEL_SPRING` (450, 0.82).
**Nhoè:** `RenderEffect.createBlurEffect` đặt lên `library_view` chỉ API 31+, bán kính tuyến tính theo `p` của panel (lượng tử hoá 1 px để không tạo effect mới mỗi khung), `setRenderEffect(null)` khi `p = 0`; `CategoryGroupPanelView.onSettled` luôn báo `p = 0` khi đóng hẳn. API < 31 (Live 4): chỉ thu nhẹ + mờ dần + nền đen đậm hơn (0.65 thay 0.35); chưa đo trên máy.
- [x] Step 1: bỏ "+N", 4 icon nhỏ (commit `03466a8`)
- [x] Step 2: mở từ ô + Library thu/mờ/nhoè (`5677aa9`), màn nhóm toàn màn hình (`6c5c3cf`); build xanh
- [ ] Step 3 (bắt buộc kiểm tay): kịch bản dưới
**Kịch bản kiểm:** (a) Live 4 và Oppo: nhóm có từ 8 app chỉ có 4 icon nhỏ ở góc thứ tư, không thấy "+N"; nhóm 5 app có 2 icon nhỏ. (b) Bấm cụm icon nhỏ của một nhóm ở cột phải: tiêu đề + lưới mọc ra từ đúng ô ấy, Library thu nhẹ + nhoè (Oppo) / chỉ mờ (Live 4), nền tối; yên sau ~0.27 s. (c) Bấm vùng trống / Back: ngược lại, lưới co vào đúng ô; Library hết nhoè, thanh tìm kiếm nét. Lặp 10 lần mở/đóng rồi kiểm Library không còn mờ. (d) Bấm lại giữa chừng (đảo chiều). (e) Nhóm nhiều app (hơn 4 hàng): cuộn lưới, hàng cuối nằm trên thanh điều hướng; bấm app mở đúng app. (f) Xoay ngang, đổi cỡ chữ, bật "remove animations" (không hoạt ảnh, trạng thái cuối vẫn nhoè/mờ đúng).

---

## Bài kiểm cả tính năng (điều phối gộp)
Live 4 (`FORCE_HIGH` kính mờ tĩnh) và Oppo A3x (kính thật). Chuẩn bị: có ảnh nền, vài app đủ nhóm. Số đo `gfxinfo` ở knowledgement mục "Số đo".
**Kết quả kiểm chứng (Oppo A3x, Android 14, APK debug a0d2ed9, 2026-11-10, điều phối):** vuốt ở ~30% Home đã trượt trái và icon Meet đã mất, dock còn; ở ~60% Library trượt vào bán trong suốt, dock và pill của Home mờ; thả thì hoàn tất vào Library. Panel nhóm: khi đóng thấy panel co/mờ và Library lớn lại từ ~75% và hiện dần (đúng cross-fade yêu cầu); khi mở nhanh hơn tốc độ screencap nên chưa bắt được khung giữa. Số đo ở knowledgement mục 8. Không crash. Chưa kiểm: Live 4, xoay màn hình, tắt hoạt ảnh, ngắt giữa chừng, nhảy kính thật từng khung.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | Plan | Viết plan, phát hiện Library là lớp phủ ngoài pager | (commit docs) | Chủ dự án cho làm ngay |
| 2026-11-10 | 1–3 | Code | a0d2ed9 | Build xanh |
| 2026-11-10 | 4 | Library chạy dưới thanh điều hướng (gradient đáy) | (commit feat kế) | Chủ dự án thêm; chờ kiểm tay |
| 2026-11-10 | 1–3 | Kiểm Oppo A3x (điều phối), ghi số đo | (commit docs này) | Đạt trên Oppo; chờ chủ dự án xem và chỉnh hằng số |
| 2026-11-10 | 5 | Vòng 2 theo video iOS 27: bỏ "+N", panel mở từ ô, Library nhoè, màn nhóm toàn màn hình (chủ dự án chốt) | `03466a8`, `5677aa9`, `6c5c3cf` | Chờ kiểm tay; thay phần "panel ở tâm 75%" của Task 1–2 |
| 2026-11-10 | Chỉnh sau video tech lead | Vuốt trang cuối ↔ Library: cả lớp Home (trang, pill, dock) trượt cùng Library, KHÔNG mờ (`AppLibraryMotion.SWIPE_FADES = false`); thanh tìm kiếm Library không rộng hơn lưới ô (lề = `hInset + gap`). Kiểm Oppo: thanh thẳng mép ô (52..668 px) | `3a83c4f`, `a2d8c3b` | Điều phối sửa trực tiếp (thay đổi nhỏ) |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1–5 | xem trên | Oppo: không crash, cross-fade đúng; mục 5 (recreate), ngắt giữa chừng, nhảy kính chưa kiểm | Một phần |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: ghi hoạt ảnh panel nhóm ở tâm và vuốt Home↔Library.
- `project_structure.md`: thêm `ui/home/view/AppLibraryMotion.kt`.
- `AGENTS.md`: xem "Bài học" trong knowledgement.
