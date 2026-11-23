# PG1 — Bù phần còn thiếu so với bản XML, đợt 1 (D1, G1, A1, F3, F4) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Chủ dự án cho code một mạch cả 5 Task (preferences: task không quá lớn thì làm liền, chủ dự án build/kiểm một thể); tick từng Task chỉ khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được** (JDK 11, không mạng Maven, không `adb`): chủ dự án chạy `.\gradlew.bat assembleDebug` và `lintDebug`, gửi lỗi. Kiểm tay trên Oppo PKD130 (kính, Android 14) và máy Android 11 (F4, đường không kính).

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** 2026-11-23 (chủ dự án trả lời Q1–Q3)
**Trạng thái:** Xong 2026-11-23 (chủ dự án build và kiểm tay đạt)
**Nhánh:** `feature/pg1-parity` · **Máy:** Oppo PKD130 + máy Android 11 · **Đổi schema Room:** không

**Goal:** Đóng 5 mục của `plans/PG-parity-gaps.md`: chép bài học từ repo XML (D1); lướt nhanh sang Thư viện / Today như bản XML (G1); icon trên lưới trượt / thu / phình khi bố cục đổi (A1); kiểu chuyển cảnh áp cho thư mục và màn nhóm Thư viện (F3); ẩn cài đặt kính mà máy / thư viện kính không hỗ trợ (F4).
**Architecture:** Chỉ tầng `ui` (Compose) và tài liệu. Không đổi `domain`/`data`, không thêm khóa config, không thêm dependency.
**Spec:** `PG-parity-gaps.md` mục D1, G1, A1, F3, F4; code gốc ở repo XML `E:\SourceTree\IosLauncher` (`HomePagerController`, `HomeTouchRouter`, `IconGridView`, `LeavingIcons`, `PanelMotion`) và plan/knowledgement FX7, TV, AN, TR của repo đó.

## Vì sao chọn thiết kế này

- **G1 — đọc trạng thái pager trực tiếp thay vì chép trong `SideEffect`.** `HomePagerInfo` hiện chép `currentPage`/`isScrollInProgress` trong `SideEffect`, chỉ cập nhật khi `HomeScreen` dựng lại. Cổng mới cần `targetPage` và "tay đang kéo pager", hai giá trị đổi liên tục khi cuộn. `PagerState` là snapshot state đọc được từ luồng chính bất kỳ lúc nào, nên `HomePagerInfo` giữ tham chiếu `PagerState` + `State<Boolean>` đang kéo (`interactionSource.collectIsDraggedAsState()`) và tính lúc `ACTION_DOWN`.

| Phương án G1 | Ưu | Nhược | Chọn? |
|---|---|---|---|
| A. Giữ tham chiếu `PagerState`, tính cổng lúc chạm | Luôn đúng giá trị hiện tại, ít code | `HomePagerInfo` giữ đối tượng Compose (cùng vòng đời `HomeScreen`) | **Chọn** |
| B. Chép thêm `targetPage`/đang kéo trong `SideEffect` | Không đổi kiểu dữ liệu | Có thể cũ khi cuộn mà không dựng lại (đúng ca lướt nhanh) | Không |

- **A1 — đặt item theo vị trí tuyệt đối có hoạt ảnh, khoá theo `itemId`.** Dock và panel thư mục Compose đã làm vậy (`animateIntOffsetAsState` theo slot) và trượt đúng khi dữ liệu đổi. Lưới chỉ có độ dời xem trước tương đối, nên dữ liệu đổi là nhảy. Làm giống dock thay vì chép FLIP của View (chụp vị trí trước `setItems`): Compose không có bước "trước khi đổi dữ liệu"; một `Animatable` vị trí theo `itemId` tự nối tiếp từ chỗ đang hiện, gồm cả lúc thả sau xem trước (không giật vì vị trí đích trùng ô xem trước).

| Phương án A1 | Ưu | Nhược | Chọn? |
|---|---|---|---|
| A. Vị trí tuyệt đối có hoạt ảnh theo `itemId` (như dock) | Một cơ chế cho xem trước + dữ liệu đổi; giống dock/thư mục | Mọi item có một `Animatable` (chỉ chạy khi vị trí đổi); phải tắt khi đổi trang/cỡ lưới/lần dựng đầu | **Chọn** |
| B. Chép FLIP của `IconGridView` | Giống hệt XML | Cần "trước khi đổi dữ liệu", trái mô hình Compose; hai cơ chế chồng nhau | Không |

- **F3 — chép đúng bảng kiểu của `PanelMotion`** (Mượt = co từ phần tử gốc như hiện tại; Mờ dần = chỉ alpha; Trượt = trồi từ đáy; Phóng to = từ 0,85 tại tâm; Không có = nhảy thẳng), đọc kiểu một lần lúc bắt đầu mở/đóng. Một hàm thuần dùng chung cho panel thư mục và màn nhóm Thư viện.
- **F4 — chỉ ẩn hàng, không xoá khóa config.** Khóa vẫn đọc/ghi như cũ (`config-schema.md` ghi "không có UI trong Compose"), để lúc nối kính Compose vào mức kính (nếu có) không phải migration.

## Quyết định đã chốt

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Làm 5 mục D1, G1, A1, F3, F4 trong một plan, code một mạch | Chủ dự án 2026-11-23 |
| 2 | F4: ẩn cài đặt không hỗ trợ thay vì làm kính cho máy cũ; "Mức kính" + công tắc kính từng phần tử ẩn ở mọi máy; API 31–32 ẩn Khúc xạ | Chủ dự án duyệt 2026-11-23 |
| 3 | Kính mờ tĩnh cho Android 8–11 gộp vào G4 (không thuộc PG1) | Chủ dự án 2026-11-23 |
| 4 | F2 theo hướng B, plan riêng sau PG1 | Chủ dự án 2026-11-23 |
| 5 | F4 (Q1): Android 8–11 giữ mọi thanh còn tác dụng: **Độ bóng + Bo góc**; ẩn Độ mờ, Khúc xạ | Chủ dự án 2026-11-23 |
| 6 | F4 (Q2): hàng "Liquid Glass" ở Cài đặt mở thẳng màn Tinh chỉnh kính; **không đổi hành vi trên máy API ≥ 31**. Cơ sở (đọc code 2026-11-23): không chỗ nào ngoài `ui/settings/` gọi `GlassEnvironment.modeFor` / đọc mức kính hay công tắc từng phần tử, nên ẩn các hàng này không đổi gì cách vẽ; 4 thanh của màn Tinh chỉnh kính trên API ≥ 33 giữ nguyên | Chủ dự án 2026-11-23 |
| 7 | A1 (Q3): app biến mất do gỡ / ẩn / bỏ khỏi Home thì thu về 0 tại chỗ. App **kéo vào thư mục** vẫn thu nhỏ nhưng **không thu để biến mất**: ảnh kéo hạ cánh co về đúng ô icon nhỏ của nó trong icon thư mục (vị trí + cỡ ô mini, giữ alpha 1), rồi icon thư mục hiện nó. Ô mini ngoài 9 ô xem trước (thư mục > 9 app) thì giữ cách cũ (co + mờ) | Chủ dự án 2026-11-23; cách hiểu của Claude, chủ dự án xem lại khi kiểm |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | **Đính chính F4:** anh duyệt "Android 8–11 chỉ giữ Bo góc", nhưng đọc code thì **Độ bóng** vẫn có tác dụng dưới API 31 (`DsSurfaceDrawable` đọc `look.gloss` cho viền sáng của thẻ tĩnh). | Android 8–11 giữ **Độ bóng + Bo góc**, ẩn Độ mờ, Khúc xạ | Giữ mọi thứ Android 8–11 còn dùng được (QĐ 5) |
| Q2 | Ẩn "Mức kính" + công tắc thì màn "Liquid Glass" chỉ còn một hàng "Tinh chỉnh kính". | Hàng "Liquid Glass" ở Cài đặt mở thẳng màn Tinh chỉnh kính; giữ màn cũ trong code (không xoá file) | Đồng ý, không đổi hành vi máy cao hơn (QĐ 6) |
| Q3 | A1: icon rời đi có thu về 0 khi **kéo sang trang khác** / vào thư mục bằng kéo không? | Không: chỉ thu khi item biến mất khỏi bố cục mà không phải do kéo (gỡ, ẩn, bỏ khỏi Home); như XML (`hiddenItemId`, `settlingItemId` được loại) | Kéo vào thư mục vẫn thu nhỏ nhưng không thu để biến mất (QĐ 7) |

## Ngoài phạm vi / ghi để sau

- F2 (plan riêng, hướng B), G3, G4 (gồm kính mờ tĩnh Android 8–11), K1 (chủ dự án kiểm).
- A1 cho màn Today (đã có hoạt ảnh riêng).
- Nối kính Compose vào mức kính / công tắc từng phần tử.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `docs/lessons/drag-and-layout.md`, `icons-and-app-list.md`, `widgets.md`, `settings-and-data.md`, `permissions-and-device-testing.md`, `home-pager-animation.md`, `glass.md`, `README.md` | Sửa | 1 |
| `ui/home/compose/HomeScreen.kt` (`HomePagerInfo`) | Sửa | 2 |
| `ui/home/HomeActivity.kt` (`finishTakeover` gọi trượt nốt) | Sửa | 2 |
| `ui/home/compose/library/HomeLibraryState.kt`, `ui/home/compose/today/HomeTodayState.kt` (chỉ nếu đổi chữ ký callback) | Sửa | 2 |
| `ui/home/compose/HomeGridPage.kt` (vị trí có hoạt ảnh, thu/phình) | Sửa | 3 |
| `ui/home/compose/HomeDock.kt`, `ui/home/compose/HomeFolderPanel.kt` (thu/phình) | Sửa | 3 |
| `ui/home/compose/PanelStyleMotion.kt` (hàm thuần kiểu chuyển cảnh) | Tạo | 4 |
| `ui/home/compose/HomeEnterExit.kt` (`EnterExitTracker`, `popScale`, `rememberSlideOffset`) | Tạo | 3 |
| `ui/home/compose/HomeDragState.kt` (`dropMerge` hạ cánh vào ô nhỏ, QĐ 7) | Sửa | 3 |
| `ui/home/compose/HomeFolderPanel.kt`, `ui/home/compose/library/HomeLibrary.kt` | Sửa | 4 |
| `ui/settings/LiquidGlassSettingsScreen.kt`, `ui/settings/GlassTuningActivity.kt`, `ui/settings/SettingsHubScreen.kt` (theo Q2) | Sửa | 5 |
| `docs/config-schema.md` (ghi chú khóa không còn UI), `docs/plans/PG-parity-gaps.md` (tick), `docs/AGENTS.md`, `docs/lessons/*` | Sửa | cuối |

## Global Constraints

- Luật phụ thuộc `ui → domain ← data`; không đụng `domain`/`data`.
- Mọi hoạt ảnh mới tôn trọng `TransitionMotion` (`isInstant` = nhảy thẳng, `duration()`), và "Giảm chuyển động" của hệ thống như chỗ khác đang làm.
- Không chuỗi mới (nếu cần thì đủ 9 file `strings.xml`).
- Giữ CRLF khi sửa file từ VM (`AGENTS.md` FM-1). Không viết `/*` trong comment Kotlin.
- Không có test UI; unit test JVM chỉ khi có hàm thuần đáng chụp (F3 `PanelStyleMotion` nếu cần, theo ngoại lệ của chuỗi Compose).

## Review Focus

1. G1: cổng từ chối khi tay đang kéo pager (kể cả cú vuốt đổi trang thường), chấp nhận khi pager đang trượt nốt tới đúng trang cuối/đầu; trang tạm (ghost) không tính là trang cuối.
2. G1: trượt nốt chỉ chạy sau `ACTION_CANCEL` giả và không đánh nhau với cú bám trang của pager; không chạy khi pager đã ở đúng trang.
3. A1: không trượt ở lần dựng đầu, khi đổi trang, đổi cỡ lưới, xoay màn, `recreate()`; item đang kéo / đang hạ cánh không bị kéo theo hoạt ảnh vị trí.
4. A1: "thay tại chỗ" (tạo thư mục, thư mục giải tán) không thu rồi phình.
5. F3: kiểu chốt lúc bắt đầu mở/đóng, không đổi giữa chừng; Không có = nhảy thẳng ở cả mở và đóng.
6. F4: điều kiện theo `Build.VERSION.SDK_INT` khớp `DsGlass.kt` (≥ 31 làm mờ, ≥ 33 khúc xạ); khóa config không bị xoá hay đặt lại.

---

### Task 1: D1 — chép bài học từ repo XML
**Bắt buộc kiểm tay:** không
- [x] Step 1: so từng file `docs/lessons/*` hai repo, chép các mục chỉ có ở XML (FM, DA, TV, RA trong `drag-and-layout.md` và mục mới ở các file khác) vào cuối file chủ đề đúng, giữ nguyên ngày + nhãn nguồn + thẻ mốc; thêm một dòng đầu mục "(chép từ repo XML 2026-11-23, viết cho bản View: phần nói về class View chỉ để tham khảo)".
- [x] Step 2: cập nhật số mục ở `lessons/README.md`.

### Task 2: G1 — lướt nhanh sang Thư viện / Today
**Bắt buộc kiểm tay:** có — cử chỉ chạm (`Workflow.md` mục 3).
- [x] Step 1: `HomePagerInfo` giữ `pagerState: PagerState?` và `dragged: State<Boolean>` (gán trong `HomeScreen`, bỏ khi rời composition). `settledOnLastPage`/`settledOnFirstPage` = không đang kéo và (đang cuộn ? `targetPage` : `currentPage`) là trang thật cuối / đầu.
- [x] Step 2: `HomePagerInfo.finishSettle(page)` do `HomeScreen` cấp (launch `animateScrollToPage` trong scope của nó, đường cong `PageMotion` như vuốt tay). `HomeActivity.finishTakeover` khi `TAKEOVER` từ Thư viện → trượt nốt tới trang thật cuối; từ Today → trang đầu; bỏ qua nếu đã đúng trang.
- [x] Step 3: Kịch bản kiểm (Oppo) — (a) từ trang 1, 4 cú vuốt trái thật nhanh liên tiếp: Thư viện mở ở cú cuối, 10 lần; (b) ngược lại, từ trang cuối vuốt phải nhanh về Today, 10 lần; (c) đóng Thư viện sau (a): Home ở trang cuối, không kẹt áp chót; (d) vuốt chậm từng trang và kéo icon chạm mép vẫn lật trang như cũ; (e) Home một trang: trái mở Thư viện, phải mở Today.

### Task 3: A1 — hoạt ảnh khi bố cục lưới / dock / thư mục đổi
**Bắt buộc kiểm tay:** có — hoạt ảnh gắn với kéo-thả.
- [x] Step 1: `HomeGridPage`: đặt item bằng vị trí tuyệt đối có hoạt ảnh theo `itemId` (lò xo `ReorderSpring` khi xem trước, `SlideSpring` 800/0,8 khi dữ liệu đổi, như panel thư mục); lần dựng đầu, đổi trang, đổi cỡ lưới = đặt thẳng. Item đang kéo / đang hạ cánh không chạy hoạt ảnh vị trí.
- [x] Step 2: thu / phình (XML `LeavingIcons`, `popIn`): item biến mất khỏi trang (không do kéo, không "thay tại chỗ") được vẽ tiếp và thu về 0 tại tâm icon (~200 ms); các item sau trượt sau khi thu xong; item mới xuất hiện (không do kéo, không thay tại chỗ) phình từ 0; widget đầu tiên trên trang trống phình. Áp cho lưới, dock, panel thư mục.
- [x] Step 2b (QĐ 7): `HomeDragState.dropMerge`: hạ cánh vào ô mini của app trong icon thư mục (thư mục có sẵn: ô = số app hiện có; tạo thư mục mới: ô 1, app đích ô 0), cỡ = cạnh ô mini / cạnh icon, alpha 1; ô ≥ 9 thì giữ `MERGE_END_SCALE` + mờ. Hình học lấy từ `HomeFolderPlate` (`FOLDER_PADDING_RATIO`, `FOLDER_GAP_RATIO`, `FOLDER_GRID`).
- [x] Step 3: Kịch bản kiểm (Oppo) — (a) edit mode, thả nhanh icon lên ô có icon khác (< 0,5 s): các icon trượt, không nhảy; (b) dấu "−" xoá app khỏi lưới / dock / thư mục: icon thu về 0, icon sau trượt lên sau đó; (c) cài app mới: icon phình ở cuối trang cuối; (d) bỏ ẩn app; (e) tạo thư mục và giải tán thư mục (kéo app cuối ra): không thu-phình ở ô thay tại chỗ; kéo app vào thư mục có sẵn và tạo thư mục mới: ảnh co vào đúng ô nhỏ trong icon thư mục, không mờ biến mất; (f) thoát edit mode dọn trang trống; (g) đổi cỡ lưới, xoay màn: không trượt lung tung; (h) vuốt trang trong lúc icon đang trượt.

### Task 4: F3 — kiểu chuyển cảnh cho thư mục và màn nhóm Thư viện
**Bắt buộc kiểm tay:** không (hoạt ảnh, kiểm cùng bài cuối)
- [x] Step 1: `PanelStyleMotion.kt`: hàm thuần `(style, p, panelRect, originRect?, hostHeight) → (scale, tx, ty, alpha)` theo bảng của `PanelMotion` (hằng `ZOOM_START_SCALE` 0,85, `ALPHA_RAMP` 2,5, `MIN_START_SCALE` 0,05, `FALLBACK_START_SCALE` 0,9 như XML).
- [x] Step 2: `HomeFolderPanel` và `GroupPanel` (`HomeLibrary`) đọc `TransitionMotion.style` một lần lúc bắt đầu mở/đóng; spring theo `TransitionMotion.spring/duration`; `isInstant` = `snapTo`.
- [x] Step 3: Kịch bản kiểm (Oppo) — Cài đặt → Hiệu ứng chuyển cảnh, lần lượt 5 kiểu: mở/đóng thư mục, mở/đóng màn nhóm Thư viện; đóng giữa chừng lúc đang mở; bật Giảm chuyển động của hệ thống.

### Task 5: F4 — ẩn cài đặt kính không hỗ trợ
**Bắt buộc kiểm tay:** không (đổi hiển thị), nhưng cần máy Android 11 để nhìn
- [x] Step 1: `LiquidGlassSettingsScreen`: bỏ hàng "Mức kính", khối công tắc kính từng phần tử và hộp cảnh báo API < 33 khỏi danh sách (giữ code đọc/ghi khóa). Theo Q2: hàng "Liquid Glass" ở hub mở thẳng `GlassTuningActivity`.
- [x] Step 2: `GlassTuningActivity`: API < 31 bỏ thanh Độ mờ, Khúc xạ (giữ Độ bóng, Bo góc theo Q1); API 31–32 bỏ thanh Khúc xạ. Điều kiện bằng `Build.VERSION.SDK_INT` khớp `DsGlass.kt`.
- [x] Step 3: `config-schema.md`: ghi chú các khóa mức kính / công tắc từng phần tử "không có UI trong bản Compose (PG1 F4)".
- [x] Step 4: Kịch bản kiểm — Oppo (Android 14): hàng Liquid Glass mở thẳng Tinh chỉnh kính, đủ 4 thanh, kính Home/dock/thư mục y như trước khi sửa; máy Android 11: chỉ Độ bóng + Bo góc, kéo thanh thì thẻ xem trước đổi viền / góc.

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Gộp từ kịch bản các Task (máy, thứ tự thao tác):
- Oppo PKD130: từ trang 1 lướt 4 cú thật nhanh sang Thư viện; lướt nhanh ngược về Today, mỗi chiều 10 lần; đóng Thư viện thì Home ở trang cuối. Vuốt chậm từng trang, kéo icon chạm mép vẫn lật trang.
- Oppo PKD130, edit mode: thả nhanh icon lên ô có icon khác; xóa bằng dấu "−" ở lưới, dock, trong thư mục; kéo app vào thư mục; tạo rồi giải tán thư mục; đổi cỡ lưới.
- Oppo PKD130: lần lượt 5 kiểu chuyển cảnh với thư mục và màn nhóm Thư viện.
- Máy Android 11: màn Tinh chỉnh kính chỉ còn Độ bóng và Bo góc.

**Kết quả kiểm chứng (máy, ngày):** Đạt — chủ dự án kiểm trên Oppo PKD130 (Android 14) và máy Android 11, 2026-11-23, sau bản sửa lint `RememberInComposition`; không thấy vấn đề liên quan PG1.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-23 | — | Đọc code Compose + XML, viết plan | Chờ duyệt, Q1–Q3 |
| 2026-11-23 | — | Chủ dự án trả lời Q1–Q3 → QĐ 5–7; thêm Step 2b cho Task 3 | Bắt đầu code |
| 2026-11-23 | 1 | Chép bài học XML vào 7 file `lessons/` (mục mới FM, DA, TV, RA, FS, HL, RA-v3 + 4 nhóm "bổ sung" P2/polish), mỗi mục có dòng ghi nguồn; sửa 2 hàng `lessons/README.md` theo bản XML. Kiểm: mọi dòng không rỗng của `IosLauncher/docs/lessons/*` đều có ở bản Compose (thiếu 0) | |
| 2026-11-23 | 2 | `HomePagerInfo` giữ `pagerState` + `pagerDragged` + `settleTo` (gán trong `DisposableEffect` của `HomeScreen`); cổng `settledOnLastPage`/`FirstPage` dùng `restingPage()` = đích khi đang cuộn, trang hiện tại khi đứng yên, null khi tay đang kéo. `HomeActivity.settlePagerAfterTakeover` (post sau CANCEL giả) cho pager trượt nốt tới trang thật cuối / đầu, no-op nếu đã ở / đang về đúng trang | Chưa build |
| 2026-11-23 | 3 | `HomeEnterExit.kt`: `EnterExitTracker` (thu/phình, thay tại chỗ theo ô, chờ 180 ms), `popScale` (EXIT 900/1.0, HOVER 900/0.6), `rememberSlideOffset` (vị trí có hoạt ảnh theo `itemId`, không cấp `Animatable` cho item đứng yên). `HomeGridPage`: thay độ dời xem trước của `homeItemDecor` bằng `rememberSlideOffset`; vẽ thêm item đang thu; `SlideSpring` chuyển sang đây (internal). Dock + bảng thư mục: thu/phình (vị trí theo dữ liệu đã trượt sẵn). `HomeDragState.dropMerge`: hạ cánh vào ô nhỏ (`folderMiniRect`), ô ≥ 9 giữ co + mờ; icon thư mục ẩn app đang hạ cánh (`hiddenChildId` thêm `settlingId`) | Chưa build. Dock chưa có độ trễ 180 ms trước khi trượt (vẫn trượt ngay) |
| 2026-11-23 | 4 | `PanelStyleMotion.kt` (`transform`, `scaledSpring`); `HomeFolderPanel` và `GroupPanel` chốt kiểu lúc bắt đầu mở/đóng, Không có = `snapTo`, tốc độ đổi độ cứng spring; Mượt giữ đúng chuyển động cũ của từng panel | Chưa build |
| 2026-11-23 | 5 | `GlassTuningActivity`: API < 33 ẩn Khúc xạ, API < 31 ẩn thêm Độ mờ (giữ Độ bóng, Bo góc); `SettingsHubScreen`: hàng "Liquid Glass" (id `hub_glass`) mở thẳng `GlassTuningActivity`; `LiquidGlassSettingsScreen` chỉ còn hàng Tinh chỉnh kính (hàm `buildGlassCustomRows`, `showGlassDialog` còn trong file, không gọi → cảnh báo unused); `config-schema.md` ghi chú | Chưa build |
| 2026-11-23 | 3 | Build của chủ dự án: Kotlin qua, `lintDebug` lỗi `RememberInComposition` (`HomeEnterExit.kt:139`, `Animatable` tạo lúc dựng). Sửa `rememberSlideOffset`: lúc dựng chỉ ghi chỗ xuất phát, `Animatable` tạo trong `LaunchedEffect`, giữ trong state; khung đầu vẽ ở chỗ xuất phát | Chờ build lại |
| 2026-11-23 | — | Chủ dự án build lại và kiểm tay cả bài: đạt. Chốt plan, cập nhật tài liệu chung | Xong |
| 2026-11-23 | 2 (sau chốt) | Chủ dự án thấy trên máy cũ: vuốt trái thoát Today về trang 1, cú vuốt ngay sau sang trang 2 bị nuốt. Nguyên nhân (đọc code, một điểm): `blockTouches()` của Today gắn ở khung phủ toàn màn, vẫn phủ khi Today đang trượt đóng (t > 0) nên chặn chạm dù bộ điều khiển (LB) đã nhường cho pager; Thư viện không bị vì lớp chặn gắn trên trang đang trượt. Sửa `today/HomeToday.kt`: chuyển `blockTouches()` xuống `TodayPage` sau `graphicsLayer { translationX }` | Chờ build + kiểm trên máy cũ |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | G1: cổng từ chối khi tay kéo pager, nhận khi đang trượt nốt tới trang cuối/đầu; trang tạm không tính | `HomePagerInfo.restingPage()` trả null khi `pagerDragged`, `targetPage` khi đang cuộn; so với `realPageCount - 1` (không gồm ghost); kiểm tay lướt nhanh 10 + 10 lần | Đạt |
| 2 | G1: trượt nốt sau CANCEL giả, không đánh nhau với cú bám trang, no-op khi đã đúng trang | `settlePagerAfterTakeover` đăng `post`; `settleTo` bỏ qua khi đang về đúng trang hoặc đã đứng ở đó (lệch < 1 %); kiểm tay đóng Thư viện ở trang cuối | Đạt |
| 3 | A1: không trượt ở lần dựng đầu, đổi trang, đổi cỡ lưới; item kéo/hạ cánh không theo hoạt ảnh | `rememberSlideOffset` chỉ trượt khi có đích cũ + cùng `geometryKey` + không `snap` (`isHidden`); tracker `remember(page.pageId)`; kiểm tay đổi cỡ lưới, kéo-thả | Đạt |
| 4 | A1: thay tại chỗ không thu rồi phình | `EnterExitTracker` loại item bỏ/thêm cùng ô (`slotOf`); kiểm tay tạo/giải tán thư mục | Đạt |
| 5 | F3: kiểu chốt lúc bắt đầu, Không có nhảy thẳng cả mở và đóng | `style`/`panelStyle` gán trong `LaunchedEffect` trước khi chạy; `isInstant` → `snapTo`; kiểm tay 5 kiểu | Đạt |
| 6 | F4: điều kiện API khớp `DsGlass.kt`, khóa config không bị xoá | `GlassTuningActivity` dùng `TIRAMISU` (lens) / `S` (blur) như `DsGlass`; không sửa `data/config`; kiểm tay Oppo + Android 11 | Đạt |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `plans/PG-parity-gaps.md`: tick D1, G1, A1, F3, F4.
- `config-schema.md`: ghi chú `glassLevel`, `glassCustom` không còn UI (Task 5).
- `lessons/*`: chép bài học repo XML (Task 1); bài học PG1 vào `home-pager-animation.md`, `drag-and-layout.md`, `design-system.md`.
- `Launcher-Features.md`, `project_structure.md`, `room-schema.md`: không đổi (PG1 bù lại hành vi đã có ở bản XML, không thêm tính năng hay schema).
