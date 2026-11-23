# FP — Panel thư mục kiểu iOS 26: nền trong, tiêu đề tách ra ngoài, khung vuông — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (thêm Oppo A3x cho kính thật). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** chưa
**Trạng thái:** Q1–Q5 đã trả lời, chờ duyệt plan để bắt đầu Task 1
**Máy:** Live 4 (kính mờ tĩnh), Oppo A3x (kính thật LENS) · **Đổi schema Room:** không

**Goal:** panel thư mục khi mở (expand) đổi sang bố cục giống iOS 26:
1. Nền khung danh sách app trong suốt, nhìn thấy nền phía sau (scrim toàn màn giữ nguyên).
2. Tên thư mục nằm **ngoài** khung danh sách, phía trên, chữ lớn.
3. Khung danh sách thu lại thành **hình vuông** chỉ chứa lưới 3×3.

**Architecture:** chỉ đổi tầng View. `FolderPanelView` (root `FrameLayout`, host của kính) tách tiêu đề thành con riêng nằm ngoài `panel`; `panel` đo vuông trong `onMeasure`. Hoạt ảnh AN (spring `p`, scale từ tâm icon) vẫn áp lên `panel`; tiêu đề có kênh alpha riêng. Màu nền/tint tách token riêng cho thư mục để **không** đổi panel nhóm Library (`CategoryGroupPanelView` đang dùng chung `folder_scrim` + `bg_folder_panel`, G2a B3).
**Tech Stack:** không thêm dependency. Không chạm `core/glass/*` trừ khi Q1 chọn phương án cần role mới (xem Task 3).
**Spec:** TT#10 trong `Launcher-Features.md`; G2a D6/D7 (mở thư mục, đổi tên); GL Task 2/7 (kính panel); AN Task 3 (hoạt ảnh panel từ icon), AN Task 9d (rung trong panel).

## Hiện trạng (đọc docs, 2026-11-18)

- `activity_home.xml`: `folder_scrim` (nền tối) + `folder_panel` nằm ngay trước `icon_menu_scrim` (G2a A2). Menu nhấn giữ hiện trên panel.
- `FolderPanelView`: khung `panel` gồm **tiêu đề ở trên + lưới 3×3** bên trong cùng một khung bo góc `folder_panel_radius`. Tiêu đề bấm được → `EditText` tại chỗ (≤ 30 ký tự, G2a D7). Panel là `DragDropTarget`, chặn các đích khác; kéo icon ra khỏi khung thì panel đóng (G2a A3b).
- Kính (GL): `BackgroundGlass` với `GlassRole.FOLDER_PANEL`. `REAL` khi tier LENS và máy API 33+ (host phải là `FrameLayout`, `layoutGlass()` đặt kính đúng khung `panel` sau `super.onLayout`), còn lại `STATIC_BLUR` (`BackdropBlurDrawable`, tint `gl_panel_tint`), `FLAT` dùng `bg_folder_panel` (`folder_panel_background`).
- Hoạt ảnh (AN): `p` từ `SpringProgress`; `panel` scale/dịch từ tâm ô thư mục (`IconGridView.folderCellScreenRect`), `alpha` panel = clamp(p·2.5), alpha nền tối = p. `BackgroundGlass.applyMotion` áp cùng transform cho kính.
- `CategoryGroupPanelView` (panel nhóm Library) dùng chung `folder_scrim` và `bg_folder_panel`.
- **Chưa đọc code**: `FolderPanelView.kt`, `view_folder_panel.xml`, `BackgroundGlass.kt`, `PanelMotion.kt`, `activity_home.xml` nằm sâu hơn 7 cấp thư mục nên phiên này không mở được. Task 1 đọc code và xác nhận các điểm trên trước khi sửa. Code khác docs thì Claude sửa plan và báo trước khi code.

### Kết quả Task 1 (đọc code 2026-11-18) — chỗ code khác plan

1. **Không có `view_folder_panel.xml`.** `FolderPanelView` dựng view bằng code: `panel` (`LinearLayout` dọc, nền `bg_folder_panel`) chứa `titleView` (một `EditText` duy nhất, luôn sửa được, không có cặp `folder_title`/`folder_title_edit`) + `AppsGrid` (ViewGroup riêng, đo bằng `GridMetrics(3, 3)`). `panel` là con duy nhất của root (cộng kính thật chèn ở vị trí 0 khi REAL). → Task 2 sửa trong `FolderPanelView.kt`, không có XML.
2. **Lưới đã vuông sẵn**: `onMeasure` tính `side = min(maxSide, rộng − 2·margin − 2·padding, cao − …)`, lưới `side × side`, `panel` rộng `side + 2·padding`; khung cao hơn rộng chỉ vì có tiêu đề bên trong. Bỏ tiêu đề ra là khung vuông. Dimens hiện có (chỉ `FolderPanelView` dùng): `folder_panel_margin` 24dp, `folder_panel_padding` 12dp, `folder_panel_max_size` 360dp, `folder_panel_radius` 28dp, `folder_title_height` 48dp, `folder_title_text_size` 20sp → **đổi giá trị tại chỗ, không tạo tên mới** (`folder_panel_margin` = 40dp, `folder_panel_padding` = 24dp, `folder_panel_max_size` = 336dp, `folder_title_text_size` = 28sp, thêm `folder_title_gap` = 24dp).
3. **`CategoryGroupPanelView` KHÔNG dùng chung `folder_scrim`, `bg_folder_panel` hay kính**: nó có scrim `BackdropBlurDrawable` riêng, `PanelMotion(glass = null)`, role `GROUP_PANEL` không còn dùng. Chỗ chung duy nhất: nhánh `GlassStyle.forRole(FOLDER_PANEL, GROUP_PANEL)` (`core/glass/GlassStyle.kt`) lấy `gl_panel_tint`. → Task 3 tách nhánh `FOLDER_PANEL` riêng với màu mới `folder_panel_glass_tint` (sửa 1 chỗ trong `core/glass`, cần thiết vì tint của cả STATIC_BLUR lẫn REAL đều lấy từ `GlassStyle.forRole`; `BackgroundGlass` không có `setTint` riêng). `bg_folder_panel`/`folder_panel_background` chỉ thư mục dùng → đổi giá trị tại chỗ.
4. **Scrim thư mục là `BlurredSnapshotScrim`**: ảnh chụp màn Home đã làm mờ + `folder_scrim` × `DIM_WITH_BLUR` (0.4). Nghĩa là Home phía sau đã thấy được (mờ). Q1 giữ nguyên scrim, nên không đụng.
5. **Kính**: `layoutGlass()`/`placeGlass()` đặt kính theo khung `target` (= `panel`), `applyMotion` áp cùng transform → đổi cỡ `panel` thì kính theo, không sửa `BackgroundGlass.kt`.
6. **Chạm và kéo**: root `setOnClickListener` = đóng; `panel` `isClickable` (chạm trong khung không đóng). `containsScreenPoint` dùng khung `panel` (đã nhân scale). Tiêu đề ra ngoài `panel` thì Q3/Q4 tự đúng; `EditText` tiêu đề để rộng `WRAP_CONTENT` (có min-width) để chạm vùng trống hai bên tiêu đề vẫn là "ngoài khung" → đóng.
7. **Hoạt ảnh**: `PanelMotion` đã có tham số `onProgress: (Float) -> Unit` gọi mỗi khung → tiêu đề fade/dịch qua callback này, **không sửa `PanelMotion.kt`**.
8. **Nhãn icon đã có bóng đổ** (`IconCellView`, `icon_label_shadow_radius`) → Task 3 Step 3 chỉ kiểm, có thể không cần sửa.

## Vì sao chọn thiết kế này

- **Vấn đề cần giải:** panel hiện tại là một khung tối chứa cả tiêu đề, cao hơn rộng, che Home. iOS 26: tên thư mục chữ lớn trôi trên nền, khung kính vuông chỉ chứa icon, Home phía sau vẫn thấy được (mờ).
- **(1) Nền khung danh sách trong suốt (Q1 đã chốt):** chỉ đổi khung `panel`; `folder_scrim` giữ nguyên. Khung vẫn là kính theo `GlassPolicy` nhưng tint nhạt hẳn (token mới `folder_panel_glass_tint`), để thấy ảnh nền qua khung; `FLAT` dùng màu bán trong suốt nhạt tương ứng. Lưu ý kỹ thuật: kính lấy mẫu từ **ảnh nền**, không từ icon Home, nên qua khung thấy ảnh nền (mờ theo kính), không thấy icon Home phía sau. Nếu chủ dự án muốn thấy cả icon Home qua khung thì phải bỏ kính, chỉ để màu bán trong suốt — chốt bằng mắt ở Task 3 Step 5.

- **(2) Tiêu đề ngoài khung:** tiêu đề là con của root `FolderPanelView`, nằm trên `panel`, căn giữa ngang. Không scale theo `panel` khi mở (scale chữ lớn từ icon nhỏ sẽ nhòe và giật); chỉ fade theo `p` + dịch nhẹ lên. Giống iOS: tên xuất hiện sau khi khung đã bung.
- **(3) Khung vuông:** `panel` = lưới 3×3 + đệm đều 4 cạnh, cạnh = `min(rộng khả dụng − 2 × lề, trần)`. Thư mục ít hơn 9 app vẫn giữ khung vuông đủ 3×3, ô trống để trống (như iOS), để kéo-thả trong panel có chỗ thả và khung không đổi cỡ khi thêm/bớt app (chốt ở Q2).
- **Đánh đổi chấp nhận:** panel nhóm Library giữ giao diện cũ (khung có tiêu đề bên trong, cuộn dọc) vì danh sách dài không vừa khung vuông. Hai panel bắt đầu khác nhau; nếu muốn đồng bộ thì làm plan riêng (Q5).

## Đặc tả thiết kế (8dp grid, Material 3 + iOS 26)

| Phần tử | Giá trị đề xuất | Token / tên resource |
|---|---|---|
| Lề ngang của khung | 24dp mỗi bên (40dp làm icon quá bé, sửa 2026-11-18) | `folder_panel_margin` |
| Cạnh khung vuông | cạnh lưới `min(parentWidth − 2 × 24dp − 2 × đệm, 360dp)` + 2 × đệm | `folder_panel_max_size` = 360dp |
| Đệm trong khung | 16dp đều 4 cạnh (24dp làm icon quá bé) | `folder_panel_padding` |
| Ô lưới | `(cạnh − 2 × 24dp) / 3`, icon giữ cỡ hiện tại của `IconCellView`, nhãn bên dưới như cũ | (tính trong `onMeasure`) |
| Bo góc khung | giữ `folder_panel_radius` hiện tại; Task 1 đo và đề xuất ~ 36–40dp nếu cần cho khung vuông lớn hơn | `folder_panel_radius` |
| Vị trí khung | căn giữa màn theo chiều dọc (trong vùng trừ inset hệ thống) | — |
| Tiêu đề | 34sp, đậm 700, trắng, 1 dòng, cắt "…", bóng đổ `#66000000` bán kính 4dp; **căn trái, chữ thẳng mép nội dung lưới** (ảnh mẫu iOS 26 chủ dự án gửi 2026-11-18; trước là 28sp căn giữa) | đặt bằng code + `folder_title_*` |
| Khoảng tiêu đề → khung | 16dp (trước 24dp, sửa theo ảnh mẫu) | `folder_title_gap` |
| Vùng chạm tiêu đề | cao ≥ 48dp | — |
| Ô đổi tên | `EditText` cùng vị trí, cùng kiểu chữ, nền pill kính mờ nhẹ khi đang sửa (để biết đang ở chế độ sửa) | `bg_folder_title_edit` |
| Scrim | giữ nguyên | (không đổi) |
| Tint kính khung danh sách | nhạt hơn `gl_panel_tint`; giá trị chốt bằng mắt ở Task 3 trên ảnh nền sáng và tối, chữ nhãn icon vẫn đạt WCAG AA (nhãn có bóng đổ như ở Home) | `folder_panel_glass_tint` |

**Trạng thái màn:**
- Default: tiêu đề + khung vuông 3×3.
- Empty: không xảy ra (D9 giải tán khi còn 1 app, panel tự đóng) — giữ nguyên.
- Loading: không có (dữ liệu có sẵn khi mở).
- Error: mở app thất bại → Toast (giữ G2a).
- Edit mode: icon rung + dấu "−" như cũ; tiêu đề vẫn đổi tên được.
- Disabled: không áp dụng.

**Ánh xạ Figma → code:** frame `FolderPanel` (Auto Layout dọc, gap 24) = root `FolderPanelView`; con 1 `FolderTitle` (Text) = `folder_title`/`folder_title_edit`; con 2 `FolderGrid` (Auto Layout lưới 3×3, padding 24, fixed square) = `panel`. Tên layer khớp id XML.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Chỉ đổi panel thư mục ở Home. Panel nhóm Library giữ nguyên | Claude đề xuất 2026-11-18; tách token riêng để không ảnh hưởng |
| D2 | Không đổi dữ liệu, ViewModel, Room. `FolderPanelState` giữ nguyên | |
| D3 | Kính vẫn theo `GlassPolicy` hiện có (REAL/STATIC_BLUR/FLAT); `layoutGlass()` đặt kính theo khung vuông mới | |
| D4 | Hoạt ảnh AN giữ cho `panel`; tiêu đề chỉ fade + dịch | |
| D5 | Không viết test tự động | `AGENTS.md` |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | "Nền trong suốt" là phần nào? | — | **Nền của khung danh sách app** trong suốt; scrim toàn màn giữ nguyên (chủ dự án, 2026-11-18) |
| Q2 | Thư mục ít hơn 9 app: giữ vuông hay co theo hàng? | Giữ vuông | Giữ vuông (chủ dự án, 2026-11-18) |
| Q3 | Chạm ngoài khung danh sách thì sao? | Đóng | Mọi chạm ngoài khung danh sách đều đóng panel (chủ dự án, 2026-11-18). Ngoại lệ duy nhất: chạm trúng chữ tiêu đề vẫn mở đổi tên (G2a D7) — **giả định**, chủ dự án xác nhận khi review Task 4 |
| Q4 | Ranh giới "kéo ra khỏi panel"? | Khung danh sách | Chỉ cần ra khỏi khung danh sách; tiêu đề không ảnh hưởng (chủ dự án, 2026-11-18) |
| Q5 | Đổi panel nhóm Library? | Không | Chưa cần (chủ dự án, 2026-11-18) |

## Ngoài phạm vi / ghi để sau

- Phân trang thư mục > 9 app (iOS có nhiều trang trong panel).
- Panel nhóm Library kiểu mới (Q5).
- Ô thư mục đóng trên lưới (giữ nguyên).

## Vùng file

Đường dẫn tính từ `app/src/main/`.

| File | Tạo / Sửa | Ghi chú |
|---|---|---|
| `java/.../ui/home/view/FolderPanelView.kt` | Sửa | Tách tiêu đề khỏi `panel`; `onMeasure`/`onLayout` đo khung vuông + đặt tiêu đề; alpha tiêu đề theo `p`; vùng chạm (Q3); `containsScreenPoint` theo khung (Q4) |
| `res/layout/view_folder_panel.xml` | Sửa | Đưa `folder_title` + `folder_title_edit` ra ngoài `panel` |
| `res/layout/activity_home.xml` | Sửa nếu cần | Màu `folder_scrim` thành token riêng (không đụng scrim nhóm Library nếu dùng chung view) |
| `res/values/dimens.xml` | Sửa | `folder_panel_margin_h`, `folder_panel_max_side`, `folder_panel_padding`, `folder_title_gap` |
| `res/values/colors.xml` (+ `values-night` nếu có) | Sửa | `folder_scrim_color`, `folder_panel_glass_tint` |
| `res/values/styles.xml` hoặc `themes.xml` | Sửa | `TextAppearance.Os26.FolderTitle` |
| `res/drawable/bg_folder_title_edit.xml` | Tạo | Nền pill khi đang sửa tên |
| `java/.../ui/home/view/BackgroundGlass.kt` | Sửa nếu cần | Chỉ khi cần đặt tint riêng cho thư mục mà API hiện tại (`setTint`) chưa đủ. Task 1 kiểm |
| `java/.../ui/home/view/PanelMotion.kt` | Sửa nếu cần | Thêm kênh alpha/dịch cho tiêu đề |
| `docs/plans/FP-folder-panel-square.md`, `docs/knowledgements/FP-folder-panel-square.md` | Tạo | |

## Global Constraints

- Không đổi `CategoryGroupPanelView` và resource nó dùng (sao chép token sang tên mới trước khi đổi giá trị).
- Kính thật: giữ quy tắc chụp một lần (không bật `enableDynamicBackground`), bài học `lessons/glass.md`.
- Không `requestLayout` mỗi khung khi hoạt ảnh (AN, phương án A: chỉ transform).
- Không đẩy chữ từ state vào `EditText` mỗi lần render (bài học M8b).
- `ui` không import `data`. Không thêm log vĩnh viễn. Commit tiếng Anh, docs tiếng Việt.

## Review Focus

1. Khung vuông đúng ở dọc, split-screen, cỡ chữ hệ thống lớn; không vượt màn.
2. Tiêu đề nằm ngoài khung, đọc được trên ảnh nền sáng và tối; đổi tên vẫn chạy (Enter, đóng panel, rỗng → "Thư mục", ≤ 30 ký tự).
3. Kính (Oppo REAL, Live 4 STATIC_BLUR, FLAT) khớp đúng khung vuông khi đứng yên và trong lúc bung/thu.
4. Hoạt ảnh mở/đóng từ icon vẫn mượt; tiêu đề không nháy, không bị scale.
5. Kéo icon ra khỏi panel, kéo trong panel, "−" ở edit mode, menu nhấn giữ trên panel: như cũ.
6. Panel nhóm Library không đổi gì.

---

### Task 1: Đọc code và chốt chỗ sửa (không đổi code)
**Bắt buộc kiểm tay:** không.
**Files:** chỉ đọc `FolderPanelView.kt`, `view_folder_panel.xml`, `activity_home.xml`, `BackgroundGlass.kt`, `PanelMotion.kt`, `CategoryGroupPanelView.kt`, `colors.xml`, `dimens.xml`.
- [x] **Step 1:** Xác nhận cấu trúc view hiện tại của panel (tiêu đề, lưới, `panel` là con nào của root), cách đo lưới 3×3 và cỡ ô.
- [x] **Step 2:** Liệt kê resource dùng chung với `CategoryGroupPanelView` (`folder_scrim`, `bg_folder_panel`, `gl_panel_tint`, `folder_panel_radius`...) để tách token.
- [x] **Step 3:** Xác nhận `BackgroundGlass.layoutGlass()`/`applyMotion` lấy khung từ `panel` (đổi cỡ `panel` là kính theo) và có `setTint` cho cả STATIC_BLUR lẫn REAL.
- [x] **Step 4:** Xác nhận `containsScreenPoint` và vùng bắt chạm nền dùng khung nào (cho Q3, Q4).
- [x] **Step 5:** Ghi kết quả vào Nhật ký. Code khác plan thì sửa plan, báo chủ dự án trước Task 2.

### Task 2: Bố cục — tiêu đề ra ngoài, khung vuông
**Bắt buộc kiểm tay:** có (nhìn bố cục, nhanh).
**Files:** `view_folder_panel.xml`, `FolderPanelView.kt`, `dimens.xml`, `styles.xml`/`themes.xml`, `bg_folder_title_edit.xml`.
- [x] **Step 1:** XML: `folder_title` + `folder_title_edit` thành con của root, đứng trên `panel`; `panel` chỉ còn lưới.
- [x] **Step 2:** `onMeasure`: cạnh vuông theo đặc tả; lưới 3×3 chia đều trong đệm 24dp; giữ vuông khi < 9 app (nếu Q2 = giữ vuông).
- [x] **Step 3:** `onLayout`: khung căn giữa dọc, tiêu đề đặt trên cách 24dp; gọi `layoutGlass()` sau `super.onLayout` như cũ.
- [x] **Step 4:** Kiểu chữ tiêu đề + nền pill khi sửa tên.
- [x] **Step 5:** Build `.\gradlew.bat assembleDebug` (phiên không build được thì ghi "chưa build").
- [x] **Step 6: Kiểm (Live 4)** — (a) thư mục 9 app, 4 app, 2 app: khung vuông, tiêu đề phía trên; (b) đổi tên: rỗng, 30 ký tự, tiếng Việt có dấu; (c) xoay/split-screen (nếu bật): khung co, không tràn; (d) cỡ chữ hệ thống lớn nhất: tiêu đề cắt "…", không đè khung.

### Task 3: Nền khung danh sách trong suốt
**Bắt buộc kiểm tay:** có (chốt màu bằng mắt).
**Files:** `colors.xml`, `FolderPanelView.kt`, có thể `BackgroundGlass.kt`, `bg_folder_panel` (bản riêng cho thư mục nếu cần).
- [x] **Step 1:** Tách token: `folder_panel_glass_tint`, `folder_panel_flat_color` (bản sao giá trị cũ trước); panel nhóm giữ token cũ. Không đụng `folder_scrim`.
- [x] **Step 2:** Hạ tint kính khung thư mục (STATIC_BLUR và REAL qua `setTint`); `FLAT` dùng màu bán trong suốt nhạt.
- [x] **Step 3:** Bóng đổ cho nhãn icon trong khung để đọc được trên ảnh sáng.
- [x] **Step 4:** Build.
- [x] **Step 5: Kiểm (Live 4, Oppo)** — (a) ảnh nền rất sáng và rất tối: thấy nền qua khung, nhãn đọc được; (b) `Ép thấp`/không ảnh nền (FLAT): vẫn đọc được; (c) chủ dự án chốt mức trong: giữ kính tint nhạt, hay bỏ kính chỉ để màu (xem "Vì sao chọn" mục 1); (d) mở Library → panel nhóm: y hệt trước.

### Task 4: Hoạt ảnh và tương tác
**Bắt buộc kiểm tay:** không (gộp bài kiểm cuối).
**Files:** `FolderPanelView.kt`, `PanelMotion.kt`.
- [ ] **Step 1:** Tiêu đề: ~~`alpha = clamp((p − 0.4) / 0.6)`~~ → fade theo thời gian (chủ dự án 2026-11-18): mở chờ 120ms, hiện 220ms, trồi +8dp về 0; đóng mờ 120ms; không scale. Đóng thì mờ trước khung.
- [ ] **Step 2:** Chạm ngoài khung danh sách = đóng (Q3), trừ chạm trúng chữ tiêu đề = đổi tên. Kéo ra = ra khỏi khung danh sách (Q4, `containsScreenPoint` = khung vuông).
- [ ] **Step 3:** Kiểm rung (`Jiggler`) và dấu "−" vẫn nằm trong khung sau khi đổi cỡ ô.
- [ ] **Step 4:** Build.

---

## Bài kiểm cả tính năng (Claude gộp ở bước cuối)
Máy: Live 4 (`AUTO` và `Ép thấp`), Oppo A3x (kính thật). Dữ liệu: ≥ 2 thư mục (9 app và 3 app).
Kịch bản: (a) mở/đóng 10 lần bằng chạm nền, Back, Home: hoạt ảnh từ icon mượt, tiêu đề fade đúng; (b) mở thư mục khác khi đang đóng dở; (c) bấm icon mở app, quay lại; (d) đổi tên, đóng panel giữa lúc gõ: tên không mất; (e) edit mode: "−" ẩn app, rung; (f) kéo icon ra ngoài khung (cả qua vùng tiêu đề) → panel đóng, thả lên Home; (g) nhấn giữ icon trong panel: menu hiện trên panel; (h) đổi sáng/tối khi panel mở (recreate): panel còn, bố cục đúng; (i) đổi ảnh nền rồi mở panel: kính lấy nền mới; (j) Library: panel nhóm không đổi.
Hồi quy: tạo/thêm thư mục bằng kéo, giải tán khi còn 1 app, lớp tìm kiếm mở trên panel.
**Kết quả kiểm chứng (máy, ngày):** chưa.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-18 | Plan | Viết plan | Chờ Q1–Q5 và duyệt. Chưa đọc được code (thư mục quá sâu cho phiên này) |
| 2026-11-18 | Plan | Chủ dự án trả lời Q1–Q5 | Q1: chỉ nền khung danh sách trong suốt, scrim giữ. Sửa mục "Vì sao chọn", đặc tả, Task 3, Task 4 theo câu trả lời |
| 2026-11-18 | 1 | Đọc code, ghi "Kết quả Task 1" | Khác plan: không có XML layout; Library không dùng chung token (chỉ chung nhánh `GlassStyle`); `PanelMotion` đã có `onProgress`; nhãn icon đã có bóng. Vùng file thu hẹp: `FolderPanelView.kt`, `GlassStyle.kt` (1 nhánh), `dimens.xml`, `colors.xml`, `bg_folder_title_edit.xml`. Chưa sửa code. Chờ xác nhận + chọn nhánh |
| 2026-11-18 | 2 | Chủ dự án chọn sửa thẳng trên `main`. Code Task 2 | `FolderPanelView.kt`: tiêu đề thành con của root (rộng vừa chữ, tối đa bằng khung, đặt trong `onLayout` trên khung cách `folder_title_gap`), `panel` chỉ còn lưới, đệm đều, đo vuông; chừa chỗ tiêu đề cả trên và dưới khi co theo chiều cao. Kiểu chữ đặt bằng code (view dựng bằng code, không tạo `TextAppearance`): 28sp, đậm, bóng `folder_title_shadow`. Nền pill `bg_folder_title_edit` khi tiêu đề có focus. `dimens.xml`: margin 40, padding 24, max 336, radius 28→36dp (đề xuất, chốt bằng mắt), thêm `folder_title_gap`/`_padding_h`/`_min_width`/`_shadow_radius`. **Chưa build** (Claude không chạy Gradle). Tiêu đề chưa có hoạt ảnh riêng (Task 4): lúc bung/thu nó hiện đủ ngay |
| 2026-11-18 | 2 | Chủ dự án gửi ảnh mẫu iOS 26 | Tiêu đề căn trái (chữ thẳng mép lưới), 34sp, cách khung 16dp, đệm ngang ô chữ 12dp. Ảnh mẫu còn cho thấy khung kính **sáng** (tint trắng nhạt) + viền sáng mảnh: làm ở Task 3 (thay vì tint tối nhạt). Chấm trang dưới khung: ngoài phạm vi (phân trang). Chưa build |
| 2026-11-18 | 2 | Chủ dự án: "icon bé thế?" | Nguyên nhân: lề 40 + đệm 24 + trần 336dp làm cạnh lưới ~283dp trên Live 4 → ô ~84dp → icon ~52dp (trước FP ~68dp; ước tính theo `GridMetrics`, giả định màn ~411dp). Sửa: lề 24dp, đệm 16dp, trần 360dp → icon ~64dp (ước tính). Tiêu đề căn thẳng mép trái icon cột đầu (`AppsGrid.iconInsetLeft()`), không còn theo mép lưới. Chưa build |
| 2026-11-18 | 2 | Chủ dự án hỏi cách hiện tên thư mục; Claude chụp màn Samsung SM-A176B qua adb | Ảnh: thư mục chưa đặt tên hiện "Thư mục" bằng hint xám mờ `#99FFFFFF` (trông như chữ bị tắt), bóng đổ đậm thành quầng, tiêu đề xa khung. Sửa: hint trắng khi không sửa (xám khi đang sửa), `includeFontPadding = false`, `folder_title_gap` 16→8dp, bóng 4dp `#66` → 2dp `#40`. Chưa build |
| 2026-11-18 | 4 (Step 1, làm trước) | Chủ dự án: tiêu đề xuất hiện khó chịu lúc bung/thu | Trước đó tiêu đề hiện đủ ngay từ p = 0 và còn nguyên tới khi panel GONE. Sửa qua `PanelMotion(onProgress = ::applyTitleMotion)` (không sửa `PanelMotion.kt`): `alpha = ramp(p, 0.4, 1)`, `translationY = (1 − alpha) × 8dp`, không scale. Hằng `TITLE_FADE_START`, `TITLE_RISE_DP` trong `FolderPanelView`. Chưa build |
| 2026-11-18 | 4 | Chủ dự án: "thêm hiệu ứng fade in fade out thử xem" | Bỏ cách bám `p`; tiêu đề fade theo THỜI GIAN bằng `ViewPropertyAnimator`: mở = chờ 120ms rồi hiện trong 220ms (Decelerate) + trồi 8dp; đóng = mờ trong 120ms (Accelerate); recreate (render đầu) hiện ngay; đóng xong / đổi thư mục giữa chừng đặt alpha 0. Hằng `TITLE_FADE_IN_DELAY_MS`, `TITLE_FADE_IN_MS`, `TITLE_FADE_OUT_MS`, `TITLE_RISE_DP`. Chưa build |
| 2026-11-18 | 2 | Chủ dự án: tên thêm padding 8dp (khoảng dọc) | `folder_title_gap` 8 → 16dp. (Lần hiểu nhầm thành lùi ngang đã gỡ.) Chưa build |
| 2026-11-18 | 2 | Chủ dự án: "Ổn, làm Task 3" | Tick Task 1–2 |
| 2026-11-18 | 3 | Code Task 3 (Step 1–3) | `GlassStyle.forRole`: tách nhánh `FOLDER_PANEL` (kính sáng theo ảnh mẫu, không phải tint tối nhạt như plan gốc) khỏi `GROUP_PANEL` (giữ `gl_panel_tint`/`gl_panel_border`/`folder_panel_background`). Màu mới: `folder_panel_glass_tint` `#1FFFFFFF`, `folder_panel_glass_border` `#66FFFFFF`, `folder_panel_flat_color` `#33FFFFFF` (điểm xuất phát, chốt bằng mắt). `bg_folder_panel` (FLAT, chỉ thư mục dùng) = màu flat mới + viền 1dp. Kính thật (REAL) chỉ nhận tint, không có viền vẽ riêng (viền sáng do thư viện kính). Step 3: nhãn icon đã có bóng sẵn, không sửa. `folder_scrim` không đổi. Chưa build |
| 2026-11-18 | 3 | Chủ dự án: "Đạt, làm Task 4" | Tick Task 3 (màu kính giữ giá trị khởi điểm) |
| 2026-11-18 | 4 | Rà Step 2–3 bằng đọc code, không sửa thêm | Step 1 đã làm trước (fade theo thời gian). Step 2: tiêu đề là con của root, ngoài `panel` → chạm vùng trống (kể cả bên phải tiêu đề) rơi xuống `setOnClickListener` của root = đóng; chạm trúng chữ thì `EditText` nhận = đổi tên; `containsScreenPoint` vẫn theo `panel` nên kéo qua vùng tiêu đề = ra khỏi khung = đóng (Q4). Step 3: `grid`/`panel` `clipChildren = false`, icon cột đầu cách mép khung ≥ đệm 16dp + đệm lưới 8dp nên dấu "−" và rung nằm trong khung; `Jiggler` không đổi. Chưa build |
| 2026-11-18 | 3 (làm lại) | Chủ dự án gửi ảnh mẫu thứ hai: nền khung danh sách trong suốt, thấy Home mờ phía sau | Chọn phương án "bỏ kính, chỉ để màu bán trong suốt" (mục "Vì sao chọn" 1, Task 3 Step 5c): kính lấy mẫu ảnh nền nên không thấy icon Home; scrim `BlurredSnapshotScrim` đã là ảnh chụp Home làm mờ. `FolderPanelView` bỏ `BackgroundGlass` (`PanelMotion(glass = null)` co/dịch thẳng `panel`); `bg_folder_panel` = `folder_panel_fill` `#26FFFFFF` + viền 1dp `folder_panel_border` `#66FFFFFF`. Gỡ nhánh `FOLDER_PANEL` đã thêm ở `GlassStyle.kt` (file về như cũ) và 3 màu `folder_panel_glass_tint`/`_glass_border`/`_flat_color`. Role `FOLDER_PANEL` còn trong `core/glass` nhưng thư mục không dùng nữa. Hệ quả: chế độ kính (AUTO/Ép thấp, kính thật) không còn ảnh hưởng khung thư mục. Chưa build |
| 2026-11-18 | 3 | Chủ dự án: "đâu có bỏ kính đâu mà bỏ?" | **Hoàn tác dòng trên**: code về đúng trạng thái Task 3 đã đạt (kính `FOLDER_PANEL` sáng, nhánh `GlassStyle` riêng, 3 màu `folder_panel_glass_*`/`_flat_color`). Yêu cầu: GIỮ kính mà vẫn thấy Home mờ qua khung. Chờ chủ dự án chọn cách |
| 2026-11-18 | 3 | Chủ dự án chọn "kính lấy ảnh Home mờ" | Đọc bytecode `liquidglass` v2.0.11: `GlassLensRenderer` → `BackdropCapture.draw(canvas, source, glass)` vẽ nguồn bằng `View.draw` (không xét visibility/alpha, chỉ cần nguồn gắn cửa sổ). Sửa: (1) `BlurredSnapshotScrim.onSnapshotChanged` báo ảnh mờ tới/bỏ; (2) `BackgroundGlass` thêm tham số `backdropOverride` (nguồn thay `backdrop_image`, null thì dùng ảnh nền), `blurCacheOverride` (bộ đệm mờ riêng, vì `env.blurCache` chỉ giữ một bản nhỏ, dùng chung sẽ tranh với dock/icon) và `refreshBackdrop()`; (3) `FolderPanelView` thêm `snapshotView` (ImageView INVISIBLE, phủ kín, `FIT_XY` như scrim vẽ) giữ ảnh chụp Home mờ, làm nguồn cho kính khung. Ảnh chưa tới (vài chục ms đầu, hoặc lần mở đầu sau khởi động ~0.5s) hay chụp lỗi thì kính lấy ảnh nền như cũ. Không sửa `core/glass`. Chưa build; cần kiểm cả kính thật (Samsung/Oppo) và kính mờ tĩnh (Live 4) |
| 2026-11-18 | 3 | Chủ dự án: "giảm mờ giảm sáng đi" | Hiểu là kính khung thư mục (và nền Home mờ). Mờ: `BlurredSnapshotScrim` thêm tham số `blurDp` (mặc định 14dp như cũ cho menu nhấn giữ/tìm kiếm), thư mục dùng `FOLDER_SNAPSHOT_BLUR_DP` = 8dp; kính thật nhoè ×`GLASS_BLUR_SCALE` 0.5 (`RealGlassSurface.setBlurScale`, sửa nhỏ trong `core/glass`; `BackgroundGlass` thêm `realBlurScale`); kính mờ tĩnh dùng bộ đệm riêng `blurRadiusPx` = 1 (mặc định 2). Sáng: `snapshotView` phủ `colorFilter` = `folder_scrim` × `DIM_WITH_BLUR` (cùng lớp tối của scrim, chỉ kính thật thấy); `folder_panel_glass_tint` `#1F` → `#0FFFFFFF`. Chưa build |
| 2026-11-18 | 3 | Chủ dự án: kính đè lên widget đồng hồ, sang phút mới nền trong kính vẫn cố định | Nguyên nhân: nguồn kính là ảnh chụp Home lúc mở (một lần). Đọc bytecode `BackdropCapture.drawLevel/drawContent`: nguồn là tổ tiên thì thư viện vẽ cả các lớp trên (scrim, panel) nên không dùng `home_root` được. Sửa: `FolderPanelView.LiveHomeBackdrop` (View INVISIBLE, phủ kín) vẽ `backdrop_image` + `home_content` đúng vị trí rồi phủ lớp tối như scrim; `BackgroundGlass` thêm `realBackdropOverride` (nguồn riêng cho kính thật); `liveRefresh` gọi `captureOnce()` mỗi `LIVE_REFRESH_MS` = 1000ms khi panel mở (dừng khi đóng/gỡ). `GLASS_BLUR_SCALE` 0.5 → 1 (nguồn chưa nhoè); `colorFilter` của `snapshotView` bỏ (lớp tối chuyển vào `LiveHomeBackdrop`). Kính mờ tĩnh (Live 4) vẫn dùng ảnh chụp, vẫn cố định. Rủi ro chưa kiểm: vẽ phần mềm cả Home mỗi giây trên main thread (giật?), kính dock lồng trong `home_content` khi bị vẽ trong lúc chụp (suy luận thư viện tự bỏ qua qua `depth`). Nền ngoài khung (scrim) vẫn là ảnh chụp cố định. Chưa build |
| 2026-11-18 | 3 | Chủ dự án: giật chút (chụp mỗi giây); tăng sáng, tăng nhoè chút | Claude thử tìm thư mục trên Samsung qua adb (vuốt 3 trang) để đo, không thấy thư mục nào nên chưa đo được số khung giật. Sửa: bỏ chụp mỗi giây; chụp lại khi `ACTION_TIME_TICK` (mỗi phút, receiver đăng ký lúc panel mở, gỡ lúc đóng/gỡ), chờ `TICK_CAPTURE_DELAY_MS` 100ms cho widget vẽ phút mới. Sáng: lớp tối trên nguồn kính thật × `GLASS_DIM_FRACTION` 0.5; `folder_panel_glass_tint` `#0F` → `#1AFFFFFF`. Nhoè: `GLASS_BLUR_SCALE` 1 → 1.5; kính mờ tĩnh `blurRadiusPx` 1 → 2. Hệ quả: widget khác (không phải đồng hồ) cập nhật giữa phút thì kính chưa thấy tới phút sau. Chưa build |
| 2026-11-18 | 3 | Đo trên Samsung SM-A176B (bản cài 10:43 = bản chụp MỖI GIÂY), panel mở đứng yên trên widget đồng hồ | **Đo được** (`dumpsys gfxinfo`, 10 s): 11 khung = đúng 1 khung/giây do `captureOnce`; 9/11 trễ hạn (p50 19 ms, p95 25 ms, máy 60 Hz hạn 16.7 ms), GPU 12–14 ms/khung, "Slow issue draw commands" 9, UI thread không chậm (DrawStart→SyncQueued ~0.6 ms). Kết luận: giật = mỗi lần kính thật dựng lại (GPU ~13 ms) làm rớt 1 khung mỗi giây; bản chụp theo phút giảm còn 1 khung/phút. Bản theo phút chưa cài, chưa đo |
| 2026-11-18 | 3 | Chủ dự án: "nhoè nữa lên, nhoè hơn màn chính background" | Đọc bytecode `LiquidGlassView`: bán kính nhoè kính Lens = `4 + blurAmount × 32` px, `blurAmount` mặc định 0.0625 (~6px) — hệ số 1.5 trước đó gần như không đổi gì. `GLASS_BLUR_SCALE` 1.5 → 20 (blurAmount 1.25, ~44px; nền chính 8dp ≈ 22px). Kính mờ tĩnh `blurRadiusPx` 2 → 6. GPU mỗi lần chụp có thể tăng (bán kính lớn), chỉ mỗi phút. Chưa build |
| 2026-11-18 | 3 | Chủ dự án: "hơi quá rồi" | `GLASS_BLUR_SCALE` 20 → 12 (~28px, nhỉnh hơn nền chính ~22px); kính mờ tĩnh `blurRadiusPx` 6 → 4. Chưa build |
| 2026-11-18 | 3 | Chủ dự án gửi ảnh mẫu thứ ba: "blur tầm này là đẹp" | Ảnh mẫu: trong khung vẫn nhận ra mảng màu/khối của icon phía sau, nhoè ngang hoặc hơi nhẹ hơn nền ngoài khung. Ước lượng (suy luận, chưa đo): bán kính Lens 28px ≈ σ 16px nếu thư viện dùng `RenderEffect` (σ ≈ 0.58r + 0.5), nền chính σ ~22px → kính hơi nhẹ hơn nền, khớp ảnh mẫu. Giữ `GLASS_BLUR_SCALE` = 12, chờ chụp màn so trên máy |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Tài liệu chung cần cập nhật (khi chốt)
- `Launcher-Features.md`: TT#10 ghi giao diện panel mới.
- `project_structure.md`: dòng mốc FP.
- `Code_Walkthrough.md`, `AGENTS.md`: sau mỗi commit (luật dự án).
- `lessons/glass.md`: bài học nếu đổi khung kính phát sinh lỗi.
