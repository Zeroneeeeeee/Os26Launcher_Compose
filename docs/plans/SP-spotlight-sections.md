# SP — Spotlight chia section, Thư viện chỉ tìm app (TT#9) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Chủ dự án cho làm liền các Task (2026-11-26) rồi build/kiểm một thể; tick Task khi chủ dự án báo ổn. Không có test tự động; kiểm tay trên Oppo PKD130 (kính + số đo). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (thiết kế duyệt trong chat cùng ngày)
**Trạng thái:** Xong 2026-11-26 (Task 5 chờ chủ dự án build + nhìn lại; chưa build trong phiên VM)
**Máy:** Oppo PKD130 (Android 14; kính thật + đo) · **Đổi schema Room:** không

**Goal:** Thư viện chỉ tìm app; Spotlight (ô "Tìm kiếm" / vuốt xuống ở Home) khi đang gõ hiện các section cuộn được: hàng 4 app → Phím tắt → Danh bạ → Cài đặt → Đề xuất tìm kiếm.
**Architecture:** Chỉ mục giữ nguyên (một `SearchIndex` cho 4 nguồn). `SearchViewModel.compute` nhánh Thư viện lọc còn app; nhánh Spotlight tách kết quả theo loại rồi `SpotlightLayoutBuilder.buildTypingState` dựng danh sách có tiêu đề section. `SearchOverlay` vẽ Spotlight đang gõ bằng `Column` + `verticalScroll`, mỗi section một thẻ kính thật (A); công tắc đo `flatSearchCards` ép thẻ dưới về nền phẳng (B) để so số đo.
**Tech Stack:** không thêm dependency.
**Spec:** yêu cầu chủ dự án 2026-11-26 (chat), backlog B2; `Launcher-Features.md` TT#9; `lessons/search.md` (M8d, G3a, CP).

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** hai màn tìm kiếm đang lẫn vai trò: Thư viện tìm cả shortcut / danh bạ / Cài đặt, Spotlight chỉ ra app + web. Spotlight không cuộn (số hàng tính vừa khít trên bàn phím, `SearchCapacity.calculateTypingCapacity`), nên muốn thêm section phải cho cuộn. Mỗi thẻ là kính thật `dsGlass(CARD)`; JD D9 đo ~15 bề mặt kính thật làm màn cuộn giật 38 % trên Oppo, chỉ card còn 10 %. Spotlight mới có tối đa 5 thẻ cuộn.
- **Các phương án đã xét (nền thẻ khi cuộn):**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Mọi section là thẻ kính thật | `Card(backdrop)` như thẻ app hiện tại | Đồng nhất kiểu liquid glass | ≤ 5 lớp kính vẽ lại khi cuộn; phải đo trên Oppo | **Chọn** (chủ dự án 2026-11-26); trượt luật QĐ 10 (Janky 7,5 %) nhưng chủ dự án giữ A sau khi xem số đo |
| B. Chỉ hàng app kính thật, section dưới nền phẳng `search_card_bg` | `Card(backdrop = null)` cho thẻ dưới | Nhẹ nhất | Hàng app và thẻ dưới lệch kiểu, thẻ phẳng đục trên ảnh sáng | Mặc định tạm 2026-11-26, chủ dự án đổi lại A cùng ngày; còn là công tắc đo `flatSearchCards` |
| C. `LazyColumn` mỗi hàng một item | Ghép thẻ bo góc qua nhiều item | Chỉ dựng hàng đang thấy | Danh sách ≤ ~17 hàng nên không lợi; ghép thẻ qua item rắc rối | Không |

- **Vì sao chọn:** chủ dự án chọn A, kèm điều kiện đo bằng script trên Oppo, không ổn thì về B. Đo 2026-11-26 (Oppo PKD130, bản `perf`, 3 vòng): A Janky 7,5 %, P95 25 ms, P99 28,3 ms; B Janky 0,04 %, P95 10,7 ms, P99 12,3 ms; cả hai `Slow33` = 0 → A trượt điều kiện Janky (> B + 1 điểm) → đổi sang B; chủ dự án xem lại và **chọn A** (đồng nhất kính, kiểm tay không thấy giật).
- **Đánh đổi chấp nhận:** Spotlight đang gõ không còn đóng bằng vuốt lên khi danh sách cuộn được (xem Quyết định 9).

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| 1 | Tìm kiếm trong Thư viện chỉ ra app: bỏ shortcut, danh bạ, Cài đặt hệ thống, hàng mời cấp quyền danh bạ, shortcut "top hit". Ô trống giữ như cũ (A–Z khi mở từ trang Thư viện, còn lại Gần đây / Hay dùng) | Chủ dự án 2026-11-26 (ô trống: Claude đề xuất, chủ dự án duyệt) |
| 2 | Spotlight ô trống giữ nguyên | Chủ dự án |
| 3 | Spotlight đang gõ: hàng 4 app (không tiêu đề) → Phím tắt → Danh bạ → Cài đặt → Đề xuất tìm kiếm; section không có kết quả thì ẩn cả tiêu đề | Chủ dự án; vị trí Cài đặt: Claude đề xuất, chủ dự án duyệt |
| 4 | Mỗi section tối đa 3 kết quả, không "Xem thêm"; Đề xuất tìm kiếm = 3 gợi ý (lịch sử rồi gợi ý mạng) + hàng chữ đã gõ | Claude đề xuất, chủ dự án duyệt |
| 5 | Phím tắt: shortcut của app đứng đầu xếp trước, rồi shortcut khớp nhãn; Phím tắt / Danh bạ / Cài đặt từ 2 ký tự (luật cũ `SearchKind.minQueryLength`) | Claude đề xuất, chủ dự án duyệt |
| 6 | Hàng mời cấp quyền danh bạ chuyển sang section Danh bạ của Spotlight (từ 2 ký tự, như cũ); không tính vào 3 | Claude đề xuất, chủ dự án duyệt |
| 7 | Kéo danh sách thì bàn phím hạ; đổi truy vấn thì cuộn về đầu | Claude đề xuất, chủ dự án duyệt |
| 8 | Danh bạ, shortcut, danh mục Cài đặt chỉ nạp khi mở Spotlight (Thư viện không nạp nữa) | Claude, theo QĐ 1 (tên danh bạ không vào RAM khi không cần) |
| 9 | Vuốt lên quá 300 px để đóng: giữ ở ô trống; lúc đang gõ chỉ còn khi nội dung không cuộn được (vừa khít màn) | Claude quyết khi viết plan: không thì kéo để cuộn sẽ đóng Spotlight. Đóng vẫn bằng Hủy / Back / Home |
| 10 | Đo A/B: công tắc `flatSearchCards` (bản `perf`), kịch bản `spotlight` trong `tools/perf/measure-home.ps1`. Giữ A nếu Janky % của A ≤ B + 1 điểm, khung > 33 ms (`Slow33`) của A ≤ 2 × B, và chủ dự án nhìn không thấy giật; không thì đổi mặc định sang B | Chủ dự án duyệt 2026-11-26. **Kết quả:** số đo chọn B, chủ dự án quyết giữ A (2026-11-26); `flatSearchCards` giữ làm công tắc đo |

## Câu hỏi cho chủ dự án

Không còn (chủ dự án 2026-11-26: "không còn gì để chốt").

## Ngoài phạm vi / ghi để sau

- Tìm tệp, "Xem thêm" trong section, đổi Spotlight ô trống.
- Nhãn tên dưới widget (B8), bố cục lần đầu (B3) — plan riêng.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `domain/search/SearchResult.kt` (`Section` thêm `SHORTCUTS`, `CONTACTS`, `SETTINGS`, `WEB`) | Sửa | 1 |
| `domain/search/SpotlightLayoutBuilder.kt` (`buildTypingState` theo section) | Sửa | 1 |
| `ui/search/SearchViewModel.kt` (Thư viện chỉ app; Spotlight tách theo loại; nạp danh bạ khi mở Spotlight) | Sửa | 1 |
| `res/values*/strings.xml` (9 file: 4 tiêu đề section) | Sửa | 1 |
| `ui/search/compose/SearchOverlay.kt` (cuộn, thẻ theo section, tiêu đề, bàn phím, cử chỉ đóng) | Sửa | 2 |
| `domain/search/SearchCapacity.kt` (bỏ `calculateTypingCapacity`) | Sửa | 2 |
| `core/perf/PerfSwitches.kt` (`flatSearchCards`), `core/perf/HomePerfLog.kt` (ngữ cảnh `spot`) | Sửa | 2 |
| `tools/perf/measure-home.ps1` (kịch bản `spotlight`) | Sửa | 3 |
| `docs/Launcher-Features.md`, `docs/lessons/search.md`, `docs/plans/BACKLOG.md`, `docs/plans/README.md`, `docs/AGENTS.md` (dòng đo hiệu năng) | Sửa | 4 |

## Global Constraints

- Luật phụ thuộc `ui → domain ← data`; `SpotlightLayoutBuilder` thuần Kotlin, không import Android.
- App ẩn không lộ ở bất kỳ section nào: nguồn app và shortcut đã lọc theo `visibleApps`, giữ nguyên.
- Tên danh bạ chỉ ở RAM khi lớp tìm kiếm mở (G3a): `close()` / `onHostStopped()` xóa như cũ.
- Công tắc ở 3.3 Cài đặt tìm kiếm (danh bạ, Cài đặt hệ thống, phím tắt) tắt nguồn nào thì section đó biến mất.
- Chuỗi mới đủ 9 ngôn ngữ; giữ CRLF khi sửa file từ VM.
- Không viết test (quy ước dự án).

## Review Focus

1. Kéo để cuộn danh sách Spotlight không đóng Spotlight; ô trống vẫn vuốt lên để đóng.
2. Đổi truy vấn liên tục: danh sách về đầu, không nhảy lệch; bàn phím chỉ hạ khi người dùng kéo (không phải khi `scrollTo(0)`).
3. Thư viện không còn hàng nào ngoài app (kể cả hàng mời danh bạ khi chưa cấp quyền) và không nạp danh bạ.
4. Danh bạ chưa cấp quyền: section Danh bạ chỉ có hàng mời; bấm "x" thì section biến mất; cấp quyền xong quay lại thì có tên.
5. Truy vấn 1 ký tự: chỉ hàng app + Đề xuất tìm kiếm; mất mạng: Đề xuất chỉ còn lịch sử + hàng chữ đã gõ.

---

### Task 1: Dữ liệu theo section (domain + ViewModel + chuỗi)
**Bắt buộc kiểm tay:** không.
**Files:** xem Vùng file, Task 1.
**Interfaces:** Produces `SearchResult.Section.{SHORTCUTS, CONTACTS, SETTINGS, WEB}`; `SpotlightLayoutBuilder.buildTypingState(query: String, apps: List<SearchResult.App>, shortcuts: List<SearchResult.Shortcut>, contacts: List<SearchResult.Contact>, contactsPrompt: SearchResult.ContactsPermissionPrompt?, settings: List<SearchResult.Setting>, historyQueries: List<String>, remoteSuggestions: List<String>, columns: Int): BuildResult` — trả `[App ≤ columns] [Header(SHORTCUTS) Shortcut ≤ 3] [Header(CONTACTS) Contact ≤ 3 + prompt] [Header(SETTINGS) Setting ≤ 3] [Header(WEB) WebSuggestion ≤ 3 + TYPED]`; hằng `SECTION_MAX = 3`, `WEB_SUGGESTIONS_MAX = 3`.
- [x] **Step 1:** `Section` thêm 4 giá trị; `buildTypingState` mới (bỏ `maxAppRows`, `maxWebRows`).
- [x] **Step 2:** `SearchViewModel.compute`: Thư viện `index.search(q).filterIsInstance<SearchResult.App>()`, không prompt, không top-hit. Spotlight gõ: tách kết quả theo loại, shortcut = shortcut của app đầu (`shortcutsByApp`, khi truy vấn ≥ 2 ký tự) rồi shortcut khớp nhãn (không trùng), prompt khi truy vấn ≥ 2 ký tự.
- [x] **Step 3:** `Inputs.prompt` chỉ cho Spotlight; `open()`: Spotlight gọi `refreshContacts()` + `refreshShortcuts()` + `ensureSettingsCatalog()`, Thư viện thì không; `refreshContacts()` chỉ nạp khi lớp mở ở Spotlight.
- [x] **Step 4:** Chuỗi `search_section_shortcuts`, `search_section_contacts`, `search_section_settings`, `search_section_web` ở 9 file.

### Task 2: Spotlight cuộn được (UI) + công tắc đo
**Bắt buộc kiểm tay:** có — cử chỉ chạm/kéo mới (cuộn vs vuốt đóng), `Workflow.md` mục 3.
**Files:** xem Vùng file, Task 2.
**Interfaces:** Consumes Task 1; Produces `PerfSwitches.flatSearchCards: Boolean` (`FLAT_SEARCH_CARDS = "flatSearchCards"`), `HomePerfLog.spotlightScrolling: Boolean` (in `spot=1/0` trong dòng `SwFrame`).
- [x] **Step 1:** `SpotlightResults` lúc gõ: `Column(verticalScroll(scroll))`; đoạn `Segment.Rows` gom các hàng liền nhau (shortcut / danh bạ / prompt / Cài đặt) vào một thẻ; `HeaderRow` cho 4 section mới; ô trống giữ như cũ (không cuộn).
- [x] **Step 2:** `ScrollState` nâng lên `SearchOverlay`: cử chỉ vuốt lên để đóng chỉ theo dõi khi chưa gõ hoặc `!scroll.canScrollForward && !scroll.canScrollBackward` lúc chạm xuống (QĐ 9). `DragInteraction.Start` của scroll → ẩn bàn phím; đổi truy vấn → `scrollTo(0)`.
- [x] **Step 3:** `SearchContent` bỏ tính sức chứa lúc gõ (`calculateTypingCapacity` gỡ khỏi `SearchCapacity`); ô trống giữ `calculateEmptyCapacity`.
- [x] **Step 4:** `flatSearchCards`: thẻ không phải hàng app vẽ `Card(backdrop = null)`; `HomePerfLog.spotlightScrolling` theo `scroll.isScrollInProgress`.
- [x] **Step 5: Kịch bản kiểm (Oppo)** — (a) gõ "ca": hàng app, Phím tắt, Danh bạ, Cài đặt, Đề xuất đúng thứ tự, mỗi section ≤ 3; (b) kéo lên: cuộn, bàn phím hạ, Spotlight không đóng; nếu sau khi bàn phím hạ mà nội dung vừa khít màn thì cú vuốt lên kế tiếp (> 300 px) sẽ đóng (QĐ 9), chủ dự án xem có chấp nhận không; (c) gõ thêm chữ: về đầu; (d) xóa hết chữ: ô trống như cũ, vuốt lên quá 300 px đóng; (e) gõ 1 ký tự: chỉ app + Đề xuất; (f) Thư viện: gõ "ca" chỉ ra app; (g) tắt "Tìm trong danh bạ" ở Cài đặt → section Danh bạ biến mất; (h) bấm một shortcut / danh bạ / mục Cài đặt / gợi ý: mở đúng, lớp đóng.

### Task 3: Đo A/B trên Oppo
**Bắt buộc kiểm tay:** có — phép đo hiệu năng chọn phương án (`Workflow.md` mục 3).
**Files:** `tools/perf/measure-home.ps1`.
**Interfaces:** Consumes `flatSearchCards` (Task 2).
- [x] **Step 1:** Kịch bản `spotlight`: sau `Start-Home`, mở Spotlight bằng vuốt xuống giữa màn (`-OpenY1/-OpenY2`, mặc định 0,30 → 0,60 chiều cao), `input text -Query` (mặc định `ca`), chờ log `shortcut index` (≤ 10 giây) và `-ResultsMs` (mặc định 2500), kéo ngắn 200 px để hạ bàn phím, rồi mới reset `gfxinfo`; mỗi vòng `-Cycles` lần kéo lên rồi kéo xuống trong vùng kết quả (0,45 → 0,15 chiều cao); logcat lưu như cũ, thêm tag `SwSpot`: cột `SpotClosed` > 0 (Spotlight bị vuốt đóng giữa lượt) thì script cảnh báo, bỏ số lượt đó.
- [x] **Step 2: Chủ dự án đo:** `.\gradlew.bat installPerf` rồi `powershell -ExecutionPolicy Bypass -File .\tools\perf\measure-home.ps1 -Scenario spotlight -Configs full,flatSearchCards -Cycles 5`; máy có danh bạ khớp "ca" (hoặc đổi `-Query`), launcher là mặc định (để có shortcut), bật vuốt xuống tìm kiếm.
- [x] **Step 3:** Áp luật QĐ 10, ghi số vào Nhật ký; nếu về B: đổi mặc định trong `Card` (thẻ dưới không kính), giữ công tắc đảo thành `realSearchCards` để đo lại. (Đã làm rồi hoàn lại: chủ dự án chọn A.)

### Task 5: Kính cho "Gần đây" và thanh tìm kiếm Today (chủ dự án 2026-11-26)
**Bắt buộc kiểm tay:** không (đổi bề mặt, xem bằng mắt).
**Files:** `ui/search/compose/SearchOverlay.kt`, `ui/home/compose/today/HomeToday.kt`.
- [x] **Step 1:** Spotlight ô trống: lưới "Gần đây" nằm trong `Card` kính như hàng app lúc gõ (`enabled = true`); tiêu đề "Gần đây" vẫn ngoài thẻ; sức chứa ô trống không đổi (thẻ không thêm đệm).
- [x] **Step 2:** `TodaySearchBar`: `dsGlass(CONTROL, Pill, realGlass = true, lensScale = PILL_LENS_SCALE)` như viên "+" / "Xong" của Today; thanh cố định trên khung cuộn nên một lớp kính không vẽ lại khi cuộn.
- [ ] **Step 3: Kịch bản kiểm (Oppo)** — (a) mở Spotlight ô trống: "Gần đây" trong thẻ kính, Thu gọn / Hiện thêm vẫn đúng số hàng; (b) vuốt sang Today: thanh tìm kiếm là kính (ảnh nền sáng thấy rõ), bấm mở Spotlight, giữ thì co nhẹ.

### Task 4: Docs
- [x] `Launcher-Features.md` TT#9, `lessons/search.md` (mục SP), `BACKLOG.md` (B2 → Đã xong), `plans/README.md`, `AGENTS.md` (dòng đo: công tắc `flatSearchCards`, kịch bản `spotlight`).

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Một Task kiểm tay duy nhất là Task 2 Step 5 (a)–(h) + số đo Task 3; không gộp thêm. Hồi quy: Spotlight ô trống (Gần đây thu gọn / mở rộng, thịnh hành), Thư viện ô trống (A–Z từ trang Thư viện, Gần đây / Hay dùng).
**Kết quả kiểm chứng (máy, ngày):** Oppo PKD130, 2026-11-26: chủ dự án kiểm tay đạt (bản A); đo A/B như "Vì sao chọn"; chủ dự án giữ A. Task 5 chờ nhìn lại sau build.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-26 | — | Đọc `SearchViewModel`, `SpotlightLayoutBuilder`, `SearchCapacity`, `SearchOverlay`, `PerfSwitches`, `HomePerfLog`, `measure-home.ps1`; thiết kế duyệt trong chat; viết plan | Chủ dự án cho làm liền |
| 2026-11-26 | 1–3 | Code: `Section` + `buildTypingState` theo section; VM (Thư viện chỉ app, Spotlight tách loại, `spotlightShortcuts` thay `withTopHitShortcuts`, danh bạ / lối tắt / danh mục Cài đặt chỉ nạp khi mở Spotlight, mở Thư viện thì xóa danh bạ khỏi RAM); 4 chuỗi × 9 ngôn ngữ (vi: "Lối tắt" theo chữ sẵn có của app); `SearchOverlay` cuộn + thẻ `Segment.Rows` + cử chỉ đóng QĐ 9 + hạ bàn phím khi kéo; gỡ `calculateTypingCapacity`; `flatSearchCards`, `HomePerfLog.spotlightScrolling` (`spot=` trong `SwFrame`), `SwSpot`; kịch bản `spotlight` | Chưa build. Review độc lập (agent): không lỗi biên dịch thấy được; sửa theo review: script hạ bàn phím + chờ lối tắt + phát hiện vuốt đóng (Important), toạ độ icon kết quả giữ ngoài snapshot (`IconCoords`, khỏi `boundsInWindow` + ghi State mỗi khung cuộn), effect đo chỉ chạy khi `HomePerfLog.enabled`. Đánh đổi để lại: section tới muộn cùng truy vấn (lối tắt dựng xong sau) đẩy nội dung xuống khi đang cuộn. Task 4 (docs chung) làm ở bước chốt, sau khi có số đo A/B |
| 2026-11-26 | 2, 3 | Chủ dự án build, kiểm tay Task 2 đạt; đo `-Scenario spotlight -Configs full,flatSearchCards -Cycles 5` (Oppo PKD130, 3 vòng + lượt lặp): full Janky 7,25–7,77 %, P95 25, P99 27–29 ms, `SlowLog` 243–324; flatSearchCards Janky 0–0,13 %, P95 10–11, P99 12–13 ms, `SlowLog` 12–19; `Slow33` = 0, `SpotClosed` = 0 mọi lượt; nhiệt 31–32 °C | QĐ 10 → **B**: `lowerBackdrop = null` khi gõ trừ khi bật `realSearchCards` (thay `flatSearchCards`); Task 4 docs |
| 2026-11-26 | 3, 5 | Chủ dự án xem số đo, quyết giữ A: hoàn lại `flatSearchCards` (mặc định kính thật mọi thẻ). Task 5: "Gần đây" ở ô trống trong thẻ kính, thanh tìm kiếm Today kính thật | Chưa build |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Kéo cuộn không đóng; ô trống vuốt lên đóng | Task 2 Step 5 (b), (d) + cột `SpotClosed` = 0 ở cả 7 lượt đo | Đạt |
| 2 | Đổi truy vấn về đầu; bàn phím chỉ hạ khi kéo | Task 2 Step 5 (b), (c); code: `LaunchedEffect(state.query) { scrollTo(0) }`, bàn phím theo `DragInteraction.Start` | Đạt |
| 3 | Thư viện chỉ app, không nạp danh bạ | Task 2 Step 5 (f); code: `refreshContacts` cần `searchMode == SPOTLIGHT`, `open(LIBRARY)` xóa danh bạ | Đạt |
| 4 | Danh bạ chưa cấp quyền / nút "x" / cấp quyền xong | Task 2 Step 5 (g); hàng mời đi theo `Inputs.prompt` chỉ ở Spotlight | Đạt (chủ dự án báo test pass) |
| 5 | Truy vấn 1 ký tự; mất mạng | Task 2 Step 5 (e); builder: Lối tắt / Danh bạ / Cài đặt cần ≥ 2 ký tự, web luôn có hàng chữ đã gõ | Đạt |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `Launcher-Features.md`: TT#9 thêm dòng SP.
- `lessons/search.md`: mục SP (Spotlight chia section, cử chỉ đóng, số đo kính khi cuộn).
- `AGENTS.md`: dòng đo hiệu năng thêm kịch bản `spotlight`, công tắc `flatSearchCards`, tag `SwSpot`.
- `plans/BACKLOG.md`: B2 chuyển xuống "Đã xong"; `plans/README.md`: dòng SP.
