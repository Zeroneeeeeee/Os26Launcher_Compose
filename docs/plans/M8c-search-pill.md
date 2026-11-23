# M8c — Pill tìm kiếm lồng chấm trang (TT#9) — Implementation Plan

> **Dành cho agent thực thi:** làm theo đúng quy ước dự án trong `docs/AGENTS.md` — code từng Task, báo cáo, **chờ người dùng xác nhận trên máy thật** rồi mới tick hoàn thành và sang Task sau. Dự án **không có test tự động** (A7) — mọi bước "kiểm chứng" là kiểm chứng tay trên Vsmart Live 4 (máy mặc định, A8); Oppo A3x chỉ khi cần. Claude không build/cài được từ môi trường của mình — người dùng build (`gradlew.bat assembleDebug`), cài, chạy kịch bản, dán log/kết quả lại. Lệnh viết cho CMD/PowerShell, không viết bash.

**Goal:** Thay hàng chấm trang trên dock bằng một viên pill: lúc đứng yên là ô "Tìm kiếm" (bấm để mở lớp tìm kiếm của M8b); khi đang lướt trang nó co lại thành các chấm chỉ trang, dừng lướt một lúc thì nở lại thành ô tìm kiếm.

**Architecture:** `SearchPillView` (custom View, tự vẽ) thay `PageIndicatorView` trong `activity_home.xml`, giữ nguyên hai hàm cũ `setPageCount` / `setSelected(index, offset)` nên `HomeActivity` ít phải đổi. Một biến `progress` (0 = ô tìm kiếm, 1 = chấm) chạy bằng `ValueAnimator`; mọi thay đổi chỉ là vẽ lại (không `requestLayout` từng khung). Chế độ chấm/ô suy ra trong `HomeActivity` từ trạng thái lướt của `ViewPager2`, edit mode và phiên kéo-thả.

**Tech Stack:** View/XML (B1), Canvas tự vẽ như `PageIndicatorView`/`IconCellView`, `ValueAnimator`. Không thêm dependency, không đổi schema Room/DataStore.

**Spec:** `docs/Launcher-Features.md` TT#9 + `docs/project_structure.md` (dòng M8c: "thành chấm khi đang lướt, về ô tìm kiếm khi dừng") + plan [M8b-search.md](M8b-search.md) (lớp tìm kiếm mà pill mở). Hành vi chi tiết bên dưới là **giả định của tôi từ mô tả đó** — mục "Quyết định đã chốt" đánh dấu ô nào cần bạn xác nhận.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Đứng yên: pill hiện **kính chữ "Tìm kiếm" + biểu tượng kính lúp**, **không** hiện chấm. Đang lướt: pill co thành **chỉ các chấm** (chấm active trượt theo `offset` như hiện nay). | ✔ Đã chấp nhận (2026-10-30, người dùng kiểm (a) Task 1 trên Live 4 và không chọn phương án thay): đứng yên không còn chấm nào, nên lúc đứng yên không thấy mình ở trang mấy. Phương án thay: đứng yên vẫn có chấm nhỏ dưới ô tìm kiếm |
| D2 | Thời điểm đổi: vào chế độ chấm ngay khi `ViewPager2` rời `SCROLL_STATE_IDLE`; về ô tìm kiếm **sau 600ms** kể từ khi `IDLE` (hủy hẹn nếu lại lướt). Hoạt họa 250ms, `DecelerateInterpolator`. | Số 600ms/250ms là điểm xuất phát, chỉnh sau khi xem trên máy |
| D3 | **Chế độ chấm giữ nguyên** (không nở về ô tìm kiếm) khi: edit mode, đang có phiên kéo-thả (kể cả lật trang tự động và trang tạm). Trong các lúc đó chạm vào pill **không** mở tìm kiếm. | Giống iOS: chỉnh sửa thì thấy chấm |
| D4 | Bấm pill (ở chế độ ô tìm kiếm) = `SearchViewModel.open()`, cùng lớp tìm kiếm của M8b. Cử chỉ vuốt xuống giữ nguyên. | |
| D5 | Giao diện: viên nang phẳng, nền đen 60% (`#99000000`), chữ/biểu tượng/chấm active trắng, chấm không active trắng 50%. **Không dùng kính** ở M8c (xem Ngoài phạm vi). | Đen 60% để chữ trắng đạt tương phản AA trên ảnh nền sáng; đo lại trên máy (Task 1 Step 4). ✔ Đã chấp nhận độ đậm hiện tại (2026-10-30) |
| D6 | Kích thước: pill cao 32dp, vùng chạm cao 48dp (view cao 48dp, pill vẽ giữa). Rộng khi là ô: 144dp. Rộng khi là chấm: bề rộng hàng chấm + 24dp, tối thiểu 48dp. Hàng chấm quá dài (nhiều trang) thì pill rộng tối đa bằng bề rộng view trừ lề 16dp ×2. | Hiện hàng chấm cao ~6dp + lề 8dp trên/dưới; thay bằng view cao 48dp thì pager co lại vài dp (xem Review Focus) |
| D7 | Trợ năng: `contentDescription` = "Tìm kiếm" ở chế độ ô; "Trang X trên Y" ở chế độ chấm (cập nhật khi đổi trang). Chế độ ô là nút bấm được; chế độ chấm không nhận bấm. | |
| D8 | Một trang duy nhất vẫn hiện một chấm khi ở chế độ chấm (như hiện nay `setPageCount(1)`). 0 trang: view ẩn như `page_indicator` cũ (INVISIBLE khi lưới chưa `Ready`). | |
| D9 | `PageIndicatorView.kt` không còn dùng sau khi thay: **báo bạn tên file và lý do để bạn quyết xóa** (Task 3), không tự xóa. | Quy ước dự án |

## Ngoài phạm vi M8c / ghi để sau

- Pill dùng **kính thật** (`GlassSurface`): hình dạng đổi kích thước làm kính phải chụp lại nền (đắt, xem "Dock: chiều rộng cố định" trong `project_structure.md`), và `GlassBudget` CLASSIC = 1 đã dùng cho dock. Nếu muốn kính, làm thành mốc riêng kèm đo Janky trên Live 4 như M6.
- Chấm tách riêng cho trang tạm (E2), chấm có dấu "+" ở trang cuối, chấm thu nhỏ khi quá nhiều trang.
- Cử chỉ vuốt lên từ pill, giữ pill để xem gợi ý.
- Dashboard, gợi ý/app dùng gần đây, danh mục Cài đặt hệ thống (xem M8b).

## Global Constraints

- `domain` không import `android.*`; `ui` không import `data` trực tiếp. M8c chỉ đụng tầng `ui`.
- Mọi chữ hiển thị ở `strings.xml` (tiếng Việt); màu ở `colors.xml`; kích thước ở `dimens.xml`; lưới 8dp, vùng chạm ≥ 48dp, tương phản WCAG AA.
- Tên chuỗi/dimens/màu dùng tiền tố `search_pill_`.
- Trạng thái hiển thị suy ra từ dữ liệu/State, `SearchPillView` chỉ giữ thứ phục vụ vẽ (`progress`, số trang, chỉ số + offset), không giữ bản sao nghiệp vụ (bài học M7 Task 5).
- Không làm hỏng: vuốt ngang chuyển trang, kéo-thả và lật trang tự động, dấu "−", menu nhấn giữ, cử chỉ vuốt xuống của M8b, dock/kính.
- Không đổi schema Room, không thêm khóa DataStore.
- Commit message tiếng Anh, `<type>: <imperative>` ≤ 72 ký tự.

## Review Focus

- **Nhấp nháy sai lúc không lướt:** `render()` gọi `setCurrentItem(..., false)` ở mỗi lần state đổi (kể cả khi cài/gỡ app) → pill **không** được co thành chấm vì việc đó (chỉ co khi người dùng lướt thật hoặc trong edit mode/kéo-thả).
- **Chuyển trang liên tiếp:** lướt hai lần cách nhau < 600ms, lướt khi hoạt họa đang nở dở, bấm giữa chừng hoạt họa → không giật, không kẹt ở trạng thái lưng chừng, hẹn giờ không chạy sau khi Activity bị hủy.
- **Bấm nhầm / không bấm được:** bấm pill khi đang là chấm, khi edit mode, khi đang kéo → không mở tìm kiếm; bấm khi là ô → mở một lần; vuốt xuống từ chính pill vẫn mở tìm kiếm một lần (không mở hai lần do vừa bấm vừa vuốt).
- **Bố cục:** pill 48dp thay hàng chấm ~22dp làm pager thấp hơn vài dp → lưới 5×6, 4×6, 4×5 vẫn đủ hàng, icon không méo/cắt; dock không dịch; split-screen hẹp và xoay (nếu có) không tràn.
- **Số trang cực đoan:** 1 trang, ~10 trang, trang tạm (ghost) hiện khi kéo ra ngoài trang cuối → hàng chấm vẫn nằm trong pill, chấm active đúng vị trí; chữ "Tìm kiếm" không bị cắt khi cỡ chữ hệ thống lớn.
- **Tương phản:** chữ trắng trên pill đen 60% đọc được trên ảnh nền rất sáng và rất tối; TalkBack đọc "Tìm kiếm" / "Trang X trên Y".

---

### Task 1: `SearchPillView` — vẽ pill, hoạt họa co/nở, thay `page_indicator`

**Files:**
- Create: `ui/home/view/SearchPillView.kt`, `res/drawable/ic_search_pill_magnifier.xml`
- Modify: `res/layout/activity_home.xml` (thay `PageIndicatorView` `page_indicator` bằng `SearchPillView` `search_pill`), `ui/home/HomeActivity.kt` (đổi chỗ dùng `binding.pageIndicator` → `binding.searchPill`, nối trạng thái lướt), `res/values/{strings,colors,dimens}.xml`

**Interfaces:**
- Consumes: `ViewPager2.OnPageChangeCallback.onPageScrolled/onPageScrollStateChanged` (có sẵn trong `setupHomePager`), `HomeUiState.editMode`, `DragHelper.currentSession`.
- Produces:
  - `class SearchPillView : View` với `fun setPageCount(count: Int)`, `fun setSelected(index: Int, offset: Float)` (giữ nguyên ngữ nghĩa của `PageIndicatorView`), `fun setDotsMode(dots: Boolean, animated: Boolean = true)`, `var onSearchClick: (() -> Unit)?`.
  - `HomeActivity.updatePillMode()` private: `dots = pagerScrolling || lastState.editMode || DragHelper.currentSession != null`; vào chấm ngay, về ô tìm kiếm sau 600ms từ lúc `IDLE` (D2, D3), hủy hẹn khi lướt lại và ở `onDestroy`.

- [x] **Step 1: `SearchPillView`** — `onMeasure` cố định `width = max(rộng ô 144dp, rộng khi là chấm)` giới hạn bởi bề rộng cha, `height = 48dp`; `onDraw` vẽ viên nang ở giữa với bề rộng nội suy theo `progress`, chữ + kính lúp alpha `1 - progress`, hàng chấm alpha `progress` (chấm vẽ theo logic hiện có của `PageIndicatorView`). `progress` chạy bằng một `ValueAnimator` 250ms, hủy ở `onDetachedFromWindow`; **không** `requestLayout` trong lúc hoạt họa. `contentDescription` đặt theo D7.
- [x] **Step 2: Layout + HomeActivity** — thay view; `page_indicator` margin 8dp trên/dưới cũ bỏ (view tự cao 48dp). Nối `onPageScrollStateChanged(state)` → `pagerScrolling = state != SCROLL_STATE_IDLE` rồi `updatePillMode()`; `render()` gọi `updatePillMode()` khi `editMode` đổi. Trạng thái `INVISIBLE` khi lưới chưa `Ready` giữ như `pageIndicator` cũ.
- [x] **Step 3: Log tạm tag `M8C`** — ghi mỗi lần `setDotsMode` (dots, animated) kèm nguyên nhân (`scroll`/`edit`/`drag`/`idle-timeout`), để kiểm tra không có lần co nào do `render()` gọi `setCurrentItem`. **Xóa log ở Task 2 sau khi kiểm chứng.**
- [x] **Step 4: Kiểm chứng (Live 4)** — build xanh; (a) đứng yên: thấy pill "Tìm kiếm" ở giữa trên dock, không còn chấm; (b) vuốt ngang chuyển trang: pill co thành chấm, chấm active trượt mượt theo ngón tay, dừng 600ms thì nở lại; (c) lướt lại giữa lúc đang nở: không giật; (d) edit mode: pill ở dạng chấm, thoát edit mode ~600ms sau thì nở; (e) kéo icon sát mép để lật trang: giữ dạng chấm tới khi thả; (f) cài/gỡ/ẩn app (để `render()` chạy): log `M8C` **không** có lần co nào; (g) chụp màn hình pill trên ảnh nền sáng và tối, đo/ước tương phản chữ; (h) lưới 4×5, 4×6, 5×6 (Cài đặt) vẫn đủ hàng, icon không méo, dock không dịch.
- [x] **Step 5: Ghi `docs/AGENTS.md` (nếu có bài học). Chưa commit** — gộp commit ở Task 2 sau khi bấm-pill chạy, hoặc commit riêng nếu người dùng muốn: `feat: add search pill morphing with page dots`.

---

**Kết quả kiểm chứng (Live 4, 2026-10-30):** người dùng xác nhận mọi kịch bản (a)–(h) ổn (pill đứng yên, chuyển chấm khi lướt, ổ quay số, edit mode, kéo-thả, không co nhầm khi `render()`, lưới 3 preset). Độ đậm màu pill giữ nguyên điểm xuất phát.

### Task 2: Bấm pill mở tìm kiếm + trợ năng + dọn log

**Files:**
- Modify: `ui/home/view/SearchPillView.kt`, `ui/home/HomeActivity.kt`

**Interfaces:**
- Consumes: `SearchPillView.setDotsMode/onSearchClick`, `SearchViewModel.open()` (M8b), `lastState.editMode`, `DragHelper.currentSession`.
- Produces: không giao diện mới cho task sau.

- [x] **Step 1: Bấm pill** — `onSearchClick = { if (!lastSearchState.isOpen && !editMode && no drag) searchViewModel.open() }`; trong view chỉ nhận bấm khi `progress` chưa quá nửa về chấm (D3, D7); ripple/trạng thái nhấn nhẹ (đậm nền 10%) thấy được trên nền tối.
- [x] **Step 2: Trợ năng** — `contentDescription` đổi theo chế độ và trang hiện tại (`"Trang X trên Y"` dùng `getString(R.string.search_pill_page_description, x, y)`); `isClickable` đúng theo chế độ; `performClick()` được gọi chuẩn để TalkBack kích hoạt.
- [x] **Step 3: Xóa log tạm `M8C`** của Task 1.
- [x] **Step 4: Kiểm chứng (Live 4)** — (a) bấm pill: lớp tìm kiếm mở, bàn phím hiện (cùng hành vi vuốt xuống); đóng lại pill nở bình thường; (b) pill là chấm (đang lướt/edit mode/kéo icon): bấm không mở; (c) vuốt xuống bắt đầu từ pill: tìm kiếm mở **một** lần; (d) bấm pill rất nhanh hai lần: mở một lần; (e) TalkBack bật: pill đọc "Tìm kiếm", bấm đúp mở tìm kiếm; ở chế độ chấm đọc "Trang X trên Y"; (f) đổi giao diện sáng/tối và xoay màn hình (nếu hỗ trợ) khi pill đang là ô hoặc chấm: dựng lại đúng, không kẹt giữa chừng; (g) kéo-thả, dấu "−", menu nhấn giữ vẫn đúng như trước.
- [x] **Step 5: Ghi `docs/AGENTS.md` + Commit** (`feat: open search from pill and add accessibility labels`).

---

**Kết quả kiểm chứng (Live 4, 2026-10-30):** người dùng xác nhận đạt (a)–(d), (f), (g): bấm pill mở tìm kiếm, chấm không nhận bấm, vuốt xuống từ pill mở một lần, bấm nhanh hai lần mở một lần, đổi sáng/tối ổn, thao tác cũ không đổi. **(e) TalkBack: người dùng chưa kiểm** — ghi nhận ở Task 3.

### Task 3: Đóng M8c — rà Review Focus, đồng bộ tài liệu [HOÀN THÀNH 2026-10-30]

**Files:**
- Modify (tài liệu): `docs/Launcher-Features.md` (TT#9: M8c đã có gì, còn lại gì), `docs/project_structure.md` (dòng M8c ✅, cây `ui/home/view/SearchPillView`, đoạn "Tệp thêm ở M8c"), `docs/AGENTS.md`, file này.

- [x] **Step 1: Rà Review Focus** — bảng bằng chứng như M8b: mỗi dòng nêu kịch bản, máy, kết quả; ca chỉ được bảo vệ bằng code mà chưa ai kiểm tay thì ghi rõ.
- [x] **Step 2: Đồng bộ tài liệu** như danh sách Files; ghi giới hạn đã biết (đứng yên không hiện chấm nếu D1 giữ nguyên; pill chưa dùng kính) và việc để sau (kính cho pill, Dashboard, gợi ý/app gần đây).
- [x] **Step 3: Báo tên file và lý do xóa `PageIndicatorView.kt`** (không còn nơi dùng sau M8c) để người dùng quyết định; chỉ xóa khi được đồng ý, và build lại xanh sau khi xóa.
- [x] **Step 4: Tick và đóng** — M8c chỉ được tick sau khi người dùng xác nhận toàn bộ trên máy thật.
- [x] **Step 5: Commit** (`docs: close M8c and sync project docs`).

### Bằng chứng Review Focus (Task 3 Step 1, ghi 2026-10-30)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Nhấp nháy sai lúc không lướt | Task 1 Step 4 (f), Live 4: cài/gỡ/ẩn app cho `render()` chạy, log `M8C` không có lần chuyển chấm nào do `setCurrentItem(..., false)` | PASS (người dùng xác nhận) |
| 2 | Chuyển trang liên tiếp | Task 1 Step 4 (c): lướt lại giữa lúc chuyển ngược không giật | PASS. **Chưa kiểm tay riêng:** hẹn giờ không chạy sau khi Activity bị hủy (chỉ bảo vệ bằng code: `removeCallbacks(pillToSearch)` ở `onDestroy`) |
| 3 | Bấm nhầm / không bấm được | Task 2 Step 4 (a)–(d): bấm pill mở một lần, chấm không nhận bấm, vuốt xuống từ pill mở một lần, bấm nhanh hai lần mở một lần | PASS |
| 4 | Bố cục | Task 1 Step 4 (h): lưới 4×5, 4×6, 5×6 đủ hàng, icon không méo | PASS. **Chưa kiểm riêng:** split-screen hẹp |
| 5 | Số trang cực đoan | Task 1 Step 4 (b)(b2)(e): ít trang, nhiều trang (chấm sáng ô giữa / ô cuối), kéo icon ra trang tạm | PASS. **Chưa kiểm riêng:** cỡ chữ hệ thống lớn (chữ "Tìm kiếm" chỉ được bảo vệ bằng `TextUtils.ellipsize`) |
| 6 | Tương phản và TalkBack | Task 1 Step 4 (g): người dùng chấp nhận độ đậm pill trên ảnh nền hiện tại | PASS cho màu. **TalkBack chưa kiểm** (người dùng chưa thử, Task 2 (e)) |

**Giới hạn đã biết sau M8c:** đứng yên pill không hiện chấm (D1); pill phẳng, chưa dùng kính; TalkBack, split-screen hẹp, cỡ chữ hệ thống lớn chưa kiểm tay.

**Step 3 (đã thực hiện):** người dùng đồng ý và tự xóa `ui/home/view/PageIndicatorView.kt` cùng dimens `home_indicator_margin_vertical`; build xanh (người dùng xác nhận); grep `app/src` không còn tham chiếu nào ngoài một dòng comment trong `SearchPillView`.
