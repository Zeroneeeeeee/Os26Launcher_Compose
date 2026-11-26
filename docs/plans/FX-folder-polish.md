# FX — Bảng thư mục: cỡ icon, nền mờ dưới Android 12, kéo ra không dư ảnh (B12, B16, B17, B18) — Implementation Plan

> Làm theo `docs/AGENTS.md`. Chủ dự án cho làm một mạch sau TR (2026-11-26 16:09), mỗi Task một commit; build/kiểm một thể sau cùng (VM không có
> Android SDK: commit chỉ được rà bằng đọc code). Không có test tự động.

**Bắt đầu:** 2026-11-26 · **Plan duyệt:** 2026-11-26 (chốt trong chat)
**Trạng thái:** Đang làm
**Bằng chứng:** video `Screen_recording_20261126_153649.mp4` (máy Android 11, nhãn tắt), ảnh cắt ở `tools/perf/out/vid/`.

**Goal:** Icon trong thư mục cỡ ổn định và nằm giữa ô dù bật/tắt nhãn; kéo app ra khỏi thư mục thì ảnh kéo co dần về cỡ icon Home; bắt đầu kéo không còn icon nổi của menu sót lại; Android dưới 12 thì Home phía sau bảng thư mục cũng mờ.
**Architecture:** Chỉ tầng Compose của Home (`HomeFolderPanel`, `HomeDragState`, `HomeIconMenu`, `HomeScreen`). Không đổi `GridMetrics`, không đổi dữ liệu.
**Spec:** chủ dự án 2026-11-26 (chat + video), backlog B12, B16, B17, B18.

## Nguyên nhân (đọc code + video)

| # | Lỗi | Nguyên nhân |
|---|---|---|
| B12 | Tắt nhãn: icon trong thư mục dồn lên trên; cột đầu trang kế ló vào mép phải bảng | `GridMetrics(3, 3, showLabels = false)`: icon bằng bề ngang ô nhưng ô vẫn cao như cũ, `HomeAppCell` căn `TopCenter`. Bước trang = `side` (cạnh lưới) trong khi bảng cắt theo khung gồm cả đệm `folder_panel_padding` |
| B18 | Icon thư mục to hơn icon Home (tắt nhãn ~92dp so với Home 4 cột ~60dp, máy 392dp) | Bảng tự đo icon theo lưới 3×3 riêng (cơ chế từ bản XML, không phải quyết định thiết kế; xem lịch sử FP/FS trong chat 2026-11-26). Tắt nhãn thì icon chiếm cả chỗ nhãn |
| B17 | Bắt đầu kéo từ menu nhấn giữ: icon cũ còn ~2 khung | `HomeIconMenu` vẽ icon nổi tại chỗ icon thật, không mờ theo `progress`, chỉ mất khi hoạt ảnh đóng menu xong; icon nổi trong thư mục lại vẽ theo cỡ icon Home |
| B16 | Android 11: Home sau bảng thư mục không mờ | `Modifier.blur` chỉ chạy API ≥ 31; dưới đó Home chỉ tối 15 % |
| (kèm) | Kéo app ra khỏi thư mục: ảnh kéo giữ cỡ ô thư mục tới lúc thả rồi mới co | `floatSize` đặt theo ô nguồn lúc bắt đầu; rời bảng chỉ `closeFolder()`; `settle(..., endScale = 1f)` giữ cỡ ảnh nổi |

## Quyết định đã chốt (chủ dự án, chat 2026-11-26)

| # | Quyết định |
|---|---|
| 1 | Icon thư mục luôn giữ cỡ "có nhãn" của lưới 3×3 (~64dp), không theo cỡ icon Home (Home 5×6 thì icon Home ~52dp, thư mục không bé theo) |
| 2 | Kéo app ra khỏi bảng: ảnh kéo co dần (lò xo) về cỡ icon Home |
| 3 | Không thêm "giữ trên icon thư mục để mở" (chiều phóng to khi đưa vào thư mục): không có đường đó trong app |

## Ruling (Claude tự quyết)

| # | Ruling | Vì sao | Sai thì tốn |
|---|---|---|---|
| R1 | Tắt nhãn: dùng `Cells` của lưới có nhãn, đẩy `paddingTop` xuống `(cellHeight − iconSize) / 2` | Không đổi `GridMetrics` (đang có B9 test hỏng, nhiều nơi dùng); hit-test (`slotAt`, `slotIconRect`) đọc cùng `Cells` nên tự khớp | Một hàm nhỏ trong `HomeFolderPanel` |
| R2 | Bước trang của bảng = cạnh khung (`side + 2 × padding`) thay cạnh lưới | Trang kế bắt đầu ngoài khung nên không ló; `FolderPanelGeometry.pageWidth` đã là tham số | Trả lại `side` |
| R3 | Icon nổi của menu lấy cỡ theo chính khung neo (`anchor`) | Neo là khung icon thật (Home, dock hay ô thư mục) nên luôn đúng cỡ, không cần biết menu mở từ đâu | Truyền cỡ riêng cho thư mục |
| R4 | Ẩn icon nổi của menu khi đang có phiên kéo (`drag.session != null`); đóng menu bình thường giữ như cũ | Đóng menu bình thường thì icon nổi che icon thật đang bị tối; tắt ngay sẽ thấy icon nháy tối | Ẩn ngay mọi lúc menu đóng |
| R5 | Ảnh kéo: co bằng `floatScale` (không đổi `floatSize`); mọi `settle` của app tính `endScale = rect.width / floatSize.width` | `floatSize` đổi tức thì còn `floatScale.snapTo` chạy ở coroutine nên có thể giật một khung; tính tỉ lệ thì đích thả nào cũng đúng cỡ | Đổi `floatSize` có hoạt ảnh |
| R6 | Dưới API 31: phủ toàn màn lát cắt của lớp ảnh chụp đã mờ (`folderFrosted`, có sẵn cho kính bảng) + tối `FOLDER_DIM`, alpha theo `folderProgress`; ảnh chụp chưa xong thì chỉ tối như cũ | Không thêm bộ làm mờ mới; ảnh chụp đã có ở PG3 | Bỏ lớp phủ |

## Vùng file

`ui/home/compose/HomeFolderPanel.kt`, `HomeDragState.kt`, `HomeIconMenu.kt`, `HomeScreen.kt`; docs (`lessons/`, `BACKLOG.md`, `README.md`).

## Global Constraints

- Không đổi `GridMetrics` (dùng chung Home/dock/Today/widget picker).
- Hành vi API ≥ 31 của nền mờ thư mục giữ nguyên.
- Commit message tiếng Anh không dấu, có dòng attribution.

## Review Focus

1. Thư mục nhiều trang (≥ 2): vuốt đổi trang, chấm trang, kéo sắp xếp lật trang ở mép, kéo app tới trang khác: đúng ô, không lệch theo bước trang mới.
2. Bật nhãn: bảng trông như trước (không đổi cỡ, không đổi vị trí).
3. Kéo app trong bảng rồi thả lại trong bảng (sắp xếp): ảnh không co (chỉ co khi rời bảng).
4. Kéo app ra rồi thả vào dock / gộp vào app khác / thả hỏng bay về: cỡ cuối khớp đích, không nhảy.
5. Mở/đóng thư mục liên tục trên Android 11: lớp mờ không kẹt lại sau khi đóng.

---

### Task 1: Cỡ và vị trí icon trong bảng (B12, B18)

**Files:** `HomeFolderPanel.kt`, `HomeIconMenu.kt`.
- [x] Step 1: `cells` của bảng = `GridMetrics(3, 3, showLabels = true).measure(side, side, density)`; người dùng tắt nhãn (hoặc nhãn tự ẩn) thì `copy(showLabels = false, innerGapPx = 0, paddingTop = paddingTop + (cellHeight − iconSize) / 2)` (R1).
- [x] Step 2: `pageStride = panelSide`: vị trí ô, đích cuộn, `folderSwipe(pageWidth)`, vị trí chấm trang, `FolderPanelGeometry(pageWidth = pageStride)` (R2).
- [x] Step 3: `HomeIconMenu` vẽ icon nổi theo cỡ của `anchor` (R3).
- [x] Step 4: Rà Review Focus 1–2 bằng đọc code. Commit `fix: folder icons keep labeled size and stay centered`.

### Task 2: Ảnh kéo co về cỡ Home khi rời bảng

**Files:** `HomeDragState.kt`.
- [ ] Step 1: Nhánh rời bảng (gọi `actions.closeFolder()`): `floatScale.animateTo(LIFT_SCALE × homeIcon / floatSize.width, SETTLE_F)` khi lệch cỡ.
- [ ] Step 2: Hàm `fitScale(s, rect)` (widget giữ 1) dùng cho mọi `settle(..., 1f, 1f)` (dock, lưới, thư mục, bay về) (R5).
- [ ] Step 3: Rà Review Focus 3–4. Commit `fix: dragged folder app shrinks to home icon size`.

### Task 3: Không còn icon nổi của menu khi bắt đầu kéo (B17)

**Files:** `HomeIconMenu.kt`, `HomeScreen.kt`.
- [ ] Step 1: Tham số `hideIcon: Boolean` = `drag.session != null` (R4). Commit `fix: hide icon menu lift copy once a drag starts`.

### Task 4: Nền mờ sau bảng thư mục dưới Android 12 (B16)

**Files:** `HomeScreen.kt`.
- [ ] Step 1: Dưới API 31, ngay trước `HomeFolderPanel`: Box toàn màn `graphicsLayer { alpha = folderProgress }` + `drawPlainBackdrop(folderFrosted, RectangleShape, FrostedNoEffects, onDrawSurface = tối FOLDER_DIM)` khi `folderFrosted.backdropOrNull != null` (R6). Commit `fix: blur home behind folder panel below Android 12`.

### Task 5: Docs

- [ ] `lessons/` (bài học bước trang/cắt khung, ảnh nổi menu), `BACKLOG.md` (B12, B16, B17, B18 → Đã xong khi chủ dự án kiểm), `README.md`. Commit `docs: FX folder polish`.

## Kịch bản kiểm (chủ dự án, sau khi build)

1. Tắt "Hiện tên": mở thư mục → icon ~64dp, nằm giữa ô, không ló cột trang kế; bật lại nhãn → như trước.
2. Home 5×6: icon thư mục vẫn ~64dp.
3. Nhấn giữ app trong thư mục → menu: icon nổi đúng cỡ ô; kéo luôn → không còn icon cũ sót lại.
4. Kéo app ra khỏi thư mục: ảnh co dần khi rời bảng, thả vào lưới / dock không nhảy cỡ.
5. Android 11 (Live 4 / máy trong video): mở thư mục → Home phía sau mờ; đóng → hết mờ.

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-26 | 1 | `folderCells` (lưới có nhãn, tắt nhãn thì đẩy `paddingTop` nửa phần ô thừa); `pageStride = panelSide` cho vị trí ô, cuộn, vuốt, chấm, `FolderPanelGeometry.pageWidth`; icon nổi của menu theo cỡ khung neo | Rà bằng đọc code, chưa build |
