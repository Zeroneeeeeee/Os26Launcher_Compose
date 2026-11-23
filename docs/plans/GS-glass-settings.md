# GS — Kính thật hay kính mờ theo từng phần tử (TT#20, phần kính) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 và Oppo A3x (nhánh này là nhánh duy nhất dùng Oppo trong đợt 1). Lệnh viết cho CMD/PowerShell. Build: `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"`; không bao giờ chạy `gradlew --stop`.

**Nhánh:** `feature/gs-glass-settings` · **Worktree:** `E:\SourceTree\IosLauncher-wt\GS-glass-settings` · **PR:** #10 (draft) · **Bắt đầu:** 2026-11-05 · **Plan duyệt:** 2026-11-05 (chủ dự án, kèm một đổi thiết kế: mức kính "Tùy chỉnh" với một công tắc cho mỗi phần tử, thay cho lựa chọn ba trạng thái; bản plan này đã sửa theo đó)
**Trạng thái:** Đang làm Task 1
**Khóa:** cần Oppo · giữ độc quyền `core/glass/*` trong đợt 1 · không khóa schema (chỉ khóa DataStore)

**Goal:** Trong Cài đặt, **Mức kính** có thêm giá trị thứ tư **Tùy chỉnh** (Tự động / Ép thấp / Ép cao / Tùy chỉnh). Khi và chỉ khi chọn Tùy chỉnh, mỗi phần tử — dock, panel (thư mục + nhóm Library), thanh tìm kiếm (ô nhập + thanh Library), pill — có **một công tắc**: bật = kính thật, tắt = kính mờ. Ba mức còn lại bỏ qua công tắc và giữ **đúng hành vi hiện tại** trên mọi máy. Lần đầu chọn Tùy chỉnh (chưa có cấu hình tùy chỉnh nào được lưu) thì các công tắc được gieo từ thứ máy đang hiển thị, nên lúc đó không đổi gì trên màn hình; sau đó cấu hình tùy chỉnh được nhớ và giữ nguyên khi người dùng sang mức khác rồi quay lại. Máy API < 33 được bật kính thật nhưng có cảnh báo có thể chậm. Đo Oppo khi bật hết để quyết có cần trần số kính thật không.

**Architecture:** `GlassLevelOverride` thêm giá trị `CUSTOM` (giá trị cũ vẫn đọc được). Bốn `GlassElement { DOCK, PANELS, SEARCH_BAR, PILL }`, mỗi phần tử một boolean (`GlassCustom`), lưu bằng bốn khóa DataStore `glass_custom_*`. `GlassPolicy.modeFor(tier, role, realGlassCapable, custom)` vẫn là hàm thuần: `custom` khác null **chỉ khi Mức kính = Tùy chỉnh**, khi đó phần tử có công tắc lấy theo công tắc (bật = thật, tắt = mờ), không hỏi tier hay `realGlassCapable`; `custom = null` chạy đúng bảng hiện tại. Dock — hôm nay quyết định riêng trong `GlassSurfaceFactory` — được đưa vào cùng `GlassPolicy` bằng `GlassRole.DOCK`. `GlassEnvironment` giữ `GlassConfig` (Mức kính + công tắc, công tắc chỉ có nghĩa khi Tùy chỉnh). Đổi cấu hình ở Cài đặt thì `RecreateGuard` dựng lại Home một lần (như Mức kính ở M8a, kiểu icon ở G2b). Kính thật mới (thanh tìm kiếm, pill) đi theo luật của panel: luôn chặn `invalidate()` + vẽ lại đúng một lần khi cần (`captureOnce`); pill đổi `SearchPillView` thành `ViewGroup` để chứa một `RealGlassSurface`, không sửa `activity_home.xml`. Giao diện: không thêm màn mới; khi Mức kính = Tùy chỉnh, một khối "Kính tùy chỉnh" gồm bốn hàng công tắc hiện ở **cuối** màn Cài đặt.

**Tech Stack:** Kotlin, View/XML, DataStore Preferences có sẵn, liquidglass `v2.0.11` đã ghim (không thêm dependency, không đoán version). Không đổi schema Room.
**Spec:** TT#20 trong `docs/Launcher-Features.md` + `docs/plans/GL-glass-pass.md` (Task 7, ghi chú "tier không phải là khả năng"), `docs/plans/AN-polish-animation.md` (Task 9g, Giao thức đo), `docs/plans/G2b-icon-styles.md` (mẫu Cài đặt + `RecreateGuard`), `docs/plans/M8a-settings.md` (Task 4), `docs/LiquidGlass-Android.md`, `docs/project_structure.md` mục 5.

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải (từ code và số đo của dự án)

1. **Quyết định kính thật/mờ đang nằm ở hai nơi khác nhau** (đọc code 2026-11-05). Dock: `GlassSurfaceFactory.create(override)` — tier `LENS` hoặc `CLASSIC_GPU` → `RealGlassSurface`, `CLASSIC_CPU` → `StaticBlurSurface`, **không đi qua `GlassPolicy`**. Các phần tử còn lại: `GlassPolicy.modeFor` — chỉ `FOLDER_PANEL`/`GROUP_PANEL` là `REAL` khi tier `LENS` **và** `realGlassCapable` (API ≥ 33); thanh tìm kiếm, pill, ô thư mục, ô nhóm luôn `STATIC_BLUR`.
2. **Mặc định phụ thuộc máy**: Oppo (API 34) tự động = dock thật + panel thật; Live 4 (API 30) tự động = tất cả mờ, còn máy của chủ dự án đặt `FORCE_HIGH` = dock thật, panel mờ. "Mặc định giữ y như hiện tại" nghĩa là không thể lưu mặc định là một giá trị cố định thật hoặc mờ; vì vậy ba mức Tự động/Ép thấp/Ép cao phải giữ nguyên, và tùy chỉnh theo phần tử là một mức riêng (**Tùy chỉnh**) chứ không chồng lên chúng (chủ dự án chốt 2026-11-05).
3. **Tier suy từ override không phải là khả năng** (GL Task 7, đo trên Live 4 2026-11-04): `FORCE_HIGH` cho tier `LENS` trên máy API 30 làm panel thành kính thật qua đường chụp CPU: S1 Janky **30.7%**, gfx p90 105 / p95 112 ms. Vì vậy `GlassPolicy` nhận `realGlassCapable` làm tham số riêng (API ≥ 33, `GlassTierResolver.isRealGlassCapable`) và công tắc mới không được suy ngược khả năng từ công tắc hay từ tier.
4. **Luật của mọi kính thật mới** (TL1, đo được): `LiquidGlassView` tự `invalidate()` trên mọi `onScrollChanged` của cửa sổ; dock kính thật khi vuốt trang Janky 72,87% → 1,76–1,97% sau `setInvalidationSuppressed`. Panel co/dịch bằng spring phải `setCaptureAtLayoutPosition(true)` (AN Task 9g). `BackgroundGlass` đã làm đúng cả hai cho panel; thanh tìm kiếm và pill phải đi theo cùng luật.
5. **`BackgroundGlass` chưa đủ cho thanh tìm kiếm**: đường `REAL` giả định host là `FrameLayout` và target là con **trực tiếp** (`layoutGlass()` đặt kính ở `target.left/top`), và host tự gọi `layoutGlass()`/`onShown()`. Ô nhập của lớp tìm kiếm là một `LinearLayout` nằm trong `LinearLayout` (có padding inset), và `SearchOverlayView.kt` thuộc `ui/search` của M8d nên **không được sửa**.
6. **Pill là `View` tự vẽ** (kính mờ vẽ trong `onDraw`). Kính thật là một `ViewGroup` (`LiquidGlassView`) nên cần một view riêng nằm dưới nội dung pill.
7. **Thứ gắn lúc dựng View thì dựng lại Activity** (M8a Task 4, G2b): Cài đặt chỉ ghi; `RecreateGuard` ở `HomeActivity.onResume` so giá trị đã áp với giá trị hiện tại. Lỗi dock 0×0 sau `recreate()` đã sửa ở AN Task 9f.
8. **`GlassBudget.maxRealGlass` (LENS 6, CLASSIC 1) không code nào gọi**; số 6 lấy từ demo với nền animate liên tục (AGENTS.md "Hiệu năng đã đo"), còn Home thật phần lớn đứng yên.
9. **Màn Cài đặt** có ~9 hàng trong 3 nhóm, G2d sẽ dựng lại thành dashboard; file Cài đặt dùng chung nên chỉ được thêm.

### Các phương án đã xét

**Quyết định A — mô hình lựa chọn và cách kết hợp với `glass_level_override`**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A1 Mức kính thứ tư "Tùy chỉnh"; công tắc từng phần tử chỉ có nghĩa ở Tùy chỉnh** | `GlassLevelOverride.CUSTOM` + bốn boolean; ba mức cũ bỏ qua công tắc; lần đầu chuyển sang Tùy chỉnh (chưa lưu công tắc nào) thì gieo công tắc từ hiển thị hiện tại; đã lưu thì giữ | Một chỗ quyết định duy nhất (mức kính), nên **không còn tổ hợp mâu thuẫn** như "Ép thấp + dock thật"; ba mức cũ giữ nguyên từng điểm ảnh; không cần giá trị Tự động riêng cho từng phần tử, không cần "Đặt lại" (muốn về mặc định thì chọn mức Tự động); công tắc là boolean nên không có trạng thái thứ ba | Quay lại Tùy chỉnh sau khi ở mức khác có thể đổi màn hình (cấu hình tùy chỉnh được nhớ, D8) | **Chọn** (chủ dự án) |
| A2 Ba trạng thái từng phần tử `AUTO/REAL/BLUR`, lựa chọn rõ ràng thắng Mức kính (đề xuất cũ của agent) | Giữ bốn lựa chọn song song với Mức kính | Mặc định đúng cho mọi máy mà không cần mức mới | Vẫn có tổ hợp lạ ("Ép thấp + dock Kính thật") cần câu chữ giải thích; thêm hàng "Đặt lại"; hai nơi cùng quyết định | Bỏ (chủ dự án chọn A1 2026-11-05) |
| A3 Hai trạng thái từng phần tử, luôn áp bất kể mức kính | Mặc định tính theo máy lúc cài và lưu | Hàng đơn giản | Mặc định đổi theo máy lúc ghi, không phân biệt "người dùng chọn" với "mặc định cũ"; máy chủ dự án (FORCE_HIGH) phải di cư | Bỏ |
| A4 Bỏ `glass_level_override`, chỉ còn công tắc | Gộp | Gọn | Mất ngân sách ô thư mục/ô nhóm theo tier (đã đo, GL Task 6), phải di cư giá trị của chủ dự án | Bỏ |

**Quyết định B — áp thay đổi thế nào**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **B1 `RecreateGuard`: dựng lại Home một lần khi quay từ Cài đặt** | Mở rộng `DockController.glassOverrideChanged()` so cả `GlassConfig` | Một đường duy nhất, đã chạy ở M8a và G2b; dock bắt buộc dựng lại vì `GlassSurface` gắn lúc dựng; không cần sửa `HomeActivity` | Home nháy một lần khi quay lại (đã có với Mức kính và kiểu icon) | **Chọn** |
| B2 Áp trực tiếp qua `GlassEnvironment.addListener` | View tự gắn/gỡ kính khi cấu hình đổi | Không nháy | Dock vẫn phải dựng lại; hai đường áp nghĩa là hai nơi để lệch trạng thái | Bỏ (nhưng các view vẫn nghe listener sẵn có, nên không có trạng thái cũ trong lúc chờ dựng lại) |

**Quyết định C — pill (phải nói trong hay ngoài, và chi phí)**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| C1 Ngoài phạm vi (pill luôn mờ) | Bỏ hàng pill | Không rủi ro | Thiếu một trong bốn phần tử chủ dự án yêu cầu | Bỏ |
| **C2 `SearchPillView` đổi từ `View` sang `FrameLayout`, tự chứa một `RealGlassSurface`** | Giữ tên class và tham số XML (`wrap_content`), kính thật là con đầu tiên đặt đúng khung viên nang; nội dung vẽ sau con trong `dispatchDraw` | Không sửa `activity_home.xml` (hot, độc quyền); `HomePagerController`, `SearchOverlayController` không đổi vì vẫn gọi `setPageCount/setSelected/setDotsMode/onSearchClick` | Phải giữ nguyên hành vi: chạm (`onInterceptTouchEvent = true` để con không nuốt chạm), `PressScale` (co cả con), TalkBack (con `importantForAccessibility = no`), thứ tự vẽ; chi phí: ~80 dòng, rủi ro hồi quy pill ở chế độ Tự động nên phải so ảnh với `main` | **Chọn** |
| C3 Thêm kính vào `activity_home.xml` làm anh em của pill | Một `RealGlassSurface` nằm sau pill | Không đổi class pill | `activity_home.xml` là file độc quyền, đồng bộ hai view về vị trí/ẩn/hiện | Bỏ |

**Quyết định D — trần số kính thật (`GlassBudget.maxRealGlass`)**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| D1 Trần cứng theo ưu tiên (dock > panel > thanh > pill), vượt thì rơi về mờ | Gọi `maxRealGlass` khi quyết định | Chặn từ gốc | Rơi im lặng nghĩa là Cài đặt nói "Kính thật" mà màn hình vẽ mờ (đúng loại trạng thái mâu thuẫn bị cấm); trần CLASSIC = 1 vô hiệu hóa chính tính năng trên Live 4 | Bỏ |
| D2 Trần mềm: đếm số phần tử đang bật thật, cảnh báo khi vượt | Giữ hàm, dùng cho cảnh báo | Có con số | Số 6 là của demo nền animate, chưa phải của Home; khi bật hết trên Oppo ≤ 4 kính cùng hiện (xem dưới) nên không bao giờ kích hoạt | Bỏ |
| **D3 Không áp trần; đo Oppo "bật hết", xóa hàm chết khi có số** | Số kính thật cùng hiện bị chặn bởi cấu trúc; kiểm bằng đo | Không tạo trạng thái giả; không giữ số không có cơ sở | Nếu đo không đạt ở một phần tử thì phải xử lý riêng phần tử đó (cảnh báo riêng hoặc báo chủ dự án), không phải trần chung | **Chọn** |

Số kính thật **cùng hiện và cùng được vẽ** khi bật hết (đọc code, kiểm lại ở Task 6): Home đứng yên = dock + pill (2); panel thư mục mở = +1 (3); lớp tìm kiếm mở = +1 ô nhập (3); Library mở = +1 thanh Library (3), mở thêm panel nhóm = 4. Panel/lớp phủ đang ẩn là `GONE` nên không vẽ. Tối đa **4 ≤ 6**. Trường hợp xấu nhất để đo là cảnh Library + panel nhóm với bốn kính (Task 6, cảnh G4).

**Quyết định E — giao diện Cài đặt**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| E1 Màn con riêng (Activity mới) | `GlassElementsActivity` + ViewModel + Manifest | Cô lập hoàn toàn | Thêm Activity/ViewModel/Manifest cho bốn công tắc | Bỏ (đề xuất cũ) |
| **E2 Khối "Kính tùy chỉnh" ở cuối màn Cài đặt, chỉ hiện khi Mức kính = Tùy chỉnh** | Một `Header` + bốn `SettingsRow.Switch` (Dock, Panel, Thanh tìm kiếm, Pill), dựng bằng một hàm riêng trong khối `// GS` | Không thêm Activity/Manifest/ViewModel; mặc định không thấy gì (không rối); dùng loại hàng `Switch` sẵn có; dời sang dashboard G2d bằng cách chuyển một hàm + các `ID_GLASS_CUSTOM_*` | Khối nằm xa hàng "Mức kính" (adapter nhóm các hàng giữa hai `Header`, nên không chèn giữa nhóm Giao diện được) | **Chọn** |
| E3 Bốn công tắc ngay dưới hàng Mức kính | Chèn giữa nhóm Giao diện | Gần hàng Mức kính | Phải chèn vào giữa danh sách hàng đã có và đẩy "Kiểu icon" xuống (sửa thứ đã có, rối với G2b) | Bỏ |

### Vì sao chọn như vậy
- **A1** là yêu cầu của chủ dự án và cũng là cách ít tổ hợp nhất: chỉ có một biến quyết định (Mức kính), công tắc chỉ đọc được khi biến đó là Tùy chỉnh. Bài học GL "tier suy từ override không phải khả năng" được giữ: công tắc không bao giờ suy `realGlassCapable`, và Tùy chỉnh không dùng tier để quyết định bốn phần tử có công tắc.
- **B1** vì dock (thứ duy nhất không áp được tại chỗ) buộc phải dựng lại, nên dùng một đường cho cả bốn là ít chỗ lệch nhất. Cơ chế đã được kiểm ở M8a Task 4 và G2b.
- **C2** vì C3 chạm file độc quyền và C1 bỏ yêu cầu; chi phí của C2 được nêu rõ ở bảng và có kịch bản so ảnh với `main` ở Task 4.
- **D3** vì D1 tạo đúng trạng thái mâu thuẫn mà đề bài cấm, và D2 dựa trên một con số không có cơ sở đo cho Home thật.
- **E2** vì nó rẻ nhất (không Activity) và chỉ lộ ra khi cần.

### Đánh đổi chấp nhận, khi nào xét lại
- Người dùng tự gánh hậu quả khi bật kính thật trên máy yếu (có cảnh báo, không chặn). Xét lại nếu số đo Task 6 cho thấy một phần tử thật sự không dùng nổi trên API < 33: khi đó thêm cảnh báo mạnh hơn riêng phần tử đó, hoặc báo chủ dự án để chặn.
- Kính thật của thanh tìm kiếm/pill sẽ khác kính mờ về hình (khúc xạ, không chung tint) trên nền có lớp tối `search_scrim` 80% — đã chấp nhận cho panel ở GL (D0 câu 1); chủ dự án xem bằng mắt ở bài kiểm cuối.
- Mỗi lần đổi mức hoặc công tắc, Home bị dựng lại một lần.
- Cấu hình tùy chỉnh được nhớ (D8): quay lại Tùy chỉnh từ mức khác hiện đúng công tắc đã lưu, có thể khác với màn hình lúc đó. Không có cách xóa cấu hình đã lưu (không có hàng "Đặt lại").

## Quyết định đã chốt
| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Bốn phần tử có công tắc: `DOCK`, `PANELS` (`FOLDER_PANEL`+`GROUP_PANEL`), `SEARCH_BAR` (`SEARCH_BAR`+`LIBRARY_SEARCH_BAR`), `PILL` (`SEARCH_PILL`) | Phạm vi do điều phối giao 2026-11-05; chủ dự án duyệt gộp hai thanh vào một hàng (2026-11-05) |
| D2 | Ô thư mục (`FOLDER_CELL`) và ô nhóm Library (`GROUP_TILE`) **không có công tắc**, luôn `STATIC_BLUR` theo ngân sách hiện có. Ở Tùy chỉnh, ngân sách của chúng theo tier của mức Tự động | Giới hạn 1 của điều phối: kính thật ở đó cần view riêng, phá rung, ảnh kéo và kéo-thả |
| D3 | `GlassLevelOverride` thêm `CUSTOM`; công tắc `GlassCustom` (boolean × 4) **chỉ áp khi mức = `CUSTOM`**; ba mức cũ bỏ qua công tắc và giữ đúng hành vi hiện tại | Chủ dự án chốt 2026-11-05 ("add new setting row call custom") |
| D4 | Công tắc **không hỏi tier, không hỏi `realGlassCapable`**; `realGlassCapable` chỉ dùng cho nhánh mức cũ và cho cảnh báo | Bài học GL Task 7 (AGENTS.md "Tier suy từ override không phải khả năng") |
| D5 | API < 33 (`!realGlassCapable`): **bật** một công tắc thì hiện hộp thoại cảnh báo có thể chậm (Vẫn bật / Hủy; Hủy thì công tắc về tắt); tắt không cảnh báo; chỉ cảnh báo, không chặn. Cảnh báo mạnh hơn cho panel (số đo 30.7%) | Giới hạn 2 của điều phối; chủ dự án: "chỉ cảnh báo" (2026-11-05). Câu chữ chốt ở Task 7 theo số đo Task 6 |
| D6 | `GlassRole.DOCK` mới; dock đi qua `GlassPolicy` (mức cũ: tier ≠ `CLASSIC_CPU` → thật, y như `GlassSurfaceFactory` hôm nay) | Đề xuất của agent: gom một nơi quyết định |
| D7 | Pill **trong phạm vi**: `SearchPillView` thành `FrameLayout`, không sửa `activity_home.xml`. Chi phí: ~80 dòng + hồi quy pill (Quyết định C) | Chủ dự án duyệt 2026-11-05 |
| D8 | **Gieo công tắc chỉ khi chưa có cấu hình tùy chỉnh:** lần đầu chuyển sang Tùy chỉnh (chưa có khóa `glass_custom_*` nào trong DataStore, `LauncherSettings.glassCustomSaved = false`), công tắc = chế độ mà từng phần tử đang có dưới mức vừa rời (Bảng 1: bật nếu đang thật), nên lúc đó màn hình không đổi. Từ đó cấu hình được nhớ: sang mức khác rồi quay lại thì **giữ nguyên công tắc đã lưu** (có thể đổi màn hình, đúng ý một cài đặt được nhớ). Gieo trước, ghi mức sau | Điều phối đính chính 2026-11-05 ("seed only when no custom setup exists yet"; thay cho cách đọc "gieo mỗi lần" của agent) |
| D9 | Luật kính thật mới: luôn `setInvalidationSuppressed(true)` + `captureOnce()` khi hiện/ảnh nền đổi/khung đổi; panel có `setCaptureAtLayoutPosition(true)`; dock giữ chặn theo cuộn pager như TL1 | Bắt buộc theo TL1 và AN Task 9g |
| D10 | Áp bằng `RecreateGuard` (mở rộng `DockController.glassOverrideChanged`, KHÔNG sửa `HomeActivity`); `GlassConfig` chuẩn hóa công tắc về "tắt hết" khi mức ≠ Tùy chỉnh nên đổi công tắc ngoài Tùy chỉnh không dựng lại Home | Quyết định B1 |
| D11 | Không áp trần số kính thật; xóa `GlassBudget.maxRealGlass` (hàm chết) khi phép đo bật hết trên Oppo đạt, ghi số đo vào KDoc | Giới hạn 3 của điều phối; chủ dự án: không trần, xóa khi đo bật hết đạt (2026-11-05) |
| D12 | Cài đặt: khối "Kính tùy chỉnh" (Header + 4 `Switch`) ở **cuối** màn, chỉ hiện khi mức = Tùy chỉnh; không Activity mới, không hàng "Đặt lại" (về mặc định = chọn mức Tự động) | Chủ dự án: bỏ "Đặt lại" (2026-11-05); E2 là đề xuất của agent |
| D13 | Không móc debug trong mã. Đặt cấu hình khi đo bằng cách nối bản ghi protobuf vào tệp DataStore qua `run-as` (Task 1 Step 0); nếu không chạy thì quay về công tắc tạm theo mẫu AN, gỡ trước khi đóng | Bài học AN ("công tắc đo tạm không được vào main") |
| D14 | Thanh tìm kiếm của Library làm **sau** ô nhập (Task 5, có cổng): nếu kính thật lúc Library trượt không đạt, công tắc "Thanh tìm kiếm" chỉ áp cho ô nhập và nhãn hàng ghi rõ | Chủ dự án: gồm cả hai, có cổng ở Task 5 (2026-11-05) |

## Bảng chân trị

**Quy tắc duy nhất:** nếu Mức kính ≠ Tùy chỉnh thì tra Bảng 1 theo (Mức kính, lớp máy) và **bỏ qua công tắc**; nếu Mức kính = Tùy chỉnh thì phần tử có công tắc = **thật khi công tắc bật, mờ khi tắt**, bất kể tier và lớp máy.

Lớp máy theo `effectiveApiLevel` của thư viện (không phải `Build.VERSION`): **A** = API ≥ 33 (Oppo A3x), **B** = API 31–32 (chưa có máy thật), **C** = API ≤ 30 (Vsmart Live 4). `realGlassCapable`: A đúng, B và C sai. Tier: Tự động → A `LENS`, B `CLASSIC_GPU`, C `CLASSIC_CPU`; Ép thấp → `CLASSIC_CPU`; Ép cao → `LENS`; **Tùy chỉnh → tier của Tự động** (chỉ còn dùng cho ngân sách ô thư mục/ô nhóm).

**Bảng 1 — Tự động / Ép thấp / Ép cao (phải giống hệt hành vi hiện tại, `main@b106797`; công tắc bị bỏ qua)**

| Mức kính | Lớp máy | Dock | Panel | Thanh tìm kiếm | Pill |
|---|---|---|---|---|---|
| Tự động | A | Thật | Thật | Mờ | Mờ |
| Tự động | B | Thật | Mờ | Mờ | Mờ |
| Tự động | C | Mờ | Mờ | Mờ | Mờ |
| Ép thấp | A, B, C | Mờ | Mờ | Mờ | Mờ |
| Ép cao | A | Thật | Thật | Mờ | Mờ |
| Ép cao | B, C | Thật | Mờ | Mờ | Mờ |

Dòng "Ép cao, C" là máy của chủ dự án hôm nay (dock thật, mọi thứ khác mờ). Nguồn: `GlassSurfaceFactory.create` + `GlassPolicy.modeFor` + `GlassTierResolver` (đọc code); "Ép cao, C" đã đo ở GL Task 7.

**Bảng 2 — Tùy chỉnh (độc lập lớp máy)**

| Công tắc | Chế độ | Cảnh báo khi bật |
|---|---|---|
| Bật | Thật | Có nếu `!realGlassCapable` (lớp B, C); không có ở lớp A |
| Tắt | Mờ | Không |

**Bảng 3 — Gieo công tắc (D8), chỉ khi chưa có cấu hình tùy chỉnh được lưu:** công tắc = ô tương ứng của Bảng 1 cho (mức vừa rời, lớp máy). Ví dụ Live 4 đang Ép cao → Dock bật, ba công tắc còn lại tắt; Oppo đang Tự động → Dock và Panel bật, Thanh và Pill tắt; đang Ép thấp → tắt hết. Vì vậy lần đầu vào Tùy chỉnh màn hình không đổi. Các lần sau (đã lưu công tắc) vào Tùy chỉnh dùng đúng công tắc đã lưu, không gieo lại; chuyển Tùy chỉnh → mức cũ: công tắc giữ trong DataStore nhưng bị bỏ qua.

**Tổ hợp trông lạ và cách phân giải** (không còn tổ hợp mâu thuẫn: chỉ một biến quyết định)

| Tổ hợp | Kết quả | Vì sao không mâu thuẫn |
|---|---|---|
| "Ép thấp + dock thật" | **Không tồn tại** | Công tắc chỉ có ở Tùy chỉnh; muốn dock thật chọn Tùy chỉnh |
| Tùy chỉnh, tắt hết trên Oppo | Mọi phần tử mờ, kể cả dock | Hợp lệ: công tắc là chế độ cuối cùng |
| Tùy chỉnh, bật Panel trên Live 4 | Panel thật (đường CPU) có cảnh báo | Người dùng đã xác nhận cảnh báo; không phụ thuộc Ép cao |
| Công tắc bật nhưng chưa có ảnh nền | Mờ (nền phẳng) | Giữ nguyên fallback hiện có của `BackgroundGlass`: kính thật cần `backdrop_image` có ảnh |
| Ô thư mục/ô nhóm ở Tùy chỉnh | Luôn mờ, theo ngân sách tier của Tự động | D2: không có công tắc |
| Chế độ trợ năng (độ tương phản cao, tiết kiệm pin) | Thư viện tự xử lý (`accessibilityMode = AUTO`) | Ở tầng thư viện, ngoài chính sách |
| Giá trị `glass_level_override` đã lưu từ trước (`AUTO`/`FORCE_LOW`/`FORCE_HIGH`) | Đọc như cũ | `CUSTOM` chỉ được thêm vào enum; đọc bằng `entries.find` (tên lạ/null → `AUTO`) |
| APK cũ đọc tệp có `CUSTOM` (hạ phiên bản) | Rơi về `AUTO` | Cùng cơ chế `find`; khóa `glass_custom_*` lạ bị bỏ qua |

`GlassBudget.maxLiteGlassCellsPerPage(tier, realGlassCapable)` và `maxLiteGlassTiles` không đổi.

## Đã trả lời (chủ dự án, 2026-11-05)
1. "Thanh tìm kiếm" gồm cả ô nhập lớp tìm kiếm lẫn thanh Library, Library làm sau có cổng ở Task 5 (D14).
2. Pill nằm trong phạm vi (D7).
3. Thay bằng mức "Tùy chỉnh" (D3); không còn "lựa chọn rõ ràng thắng Mức kính".
4. API < 33: chỉ cảnh báo khi bật (D5).
5. Không trần; xóa `maxRealGlass` khi phép đo bật hết đạt (D11).
6. Không có hàng "Đặt lại" (D12).
7. Oppo dưới 1 giờ; hàng đợi Oppo: LC rồi GS; điều phối chạy máy, agent không cài APK (Task 6).

**Đính chính D8 (điều phối 2026-11-05):** gieo chỉ khi chưa có cấu hình tùy chỉnh; cấu hình đã lưu được giữ khi sang mức khác rồi quay lại (có thể đổi màn hình).

## Ngoài phạm vi / ghi để sau
- Kính thật cho ô thư mục và ô nhóm Library (D2).
- Đưa dock sang luật "luôn chặn + `captureOnce`" như panel (hôm nay dock chỉ chặn khi pager cuộn); có thể rẻ hơn khi lớp phủ mở, nhưng đổi hành vi dock đã đo, để mốc riêng nếu Task 6 cho thấy cần.
- Đổi tên/mô tả "Mức kính" cho khớp mô hình mới (đổi chuỗi đã có trong `strings.xml` và hộp thoại hiện có là sửa thứ đã tồn tại); để G2d khi dựng lại Cài đặt.
- Dọn `HomeViewModel.currentGlassLevelOverride()` (sẽ không còn nơi gọi sau Task 1): `HomeViewModel` thuộc file độc quyền của G2c nên chỉ ghi vào "Đề xuất cập nhật tài liệu chung".
- **Giới hạn đã biết (review độc lập, 2026-11-05; không sửa):** lần đầu chuyển từ "Ép thấp" sang "Tùy chỉnh" trên máy API ≥ 33 mà một trang có hơn 6 thư mục thì tier đổi từ `CLASSIC_CPU` sang `LENS` (D2: tier của Tùy chỉnh = tier của Tự động), nên ngân sách ô thư mục tăng 6 → 24 và các thư mục từ thứ 7 trở đi chuyển từ nền phẳng sang kính mờ. Trái chữ "vào Tùy chỉnh không đổi màn hình" nhưng hiếm và chỉ ảnh hưởng ô thư mục (không có công tắc).

## Vùng file
| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `domain/model/GlassCustom.kt` (`GlassElement`, `GlassCustom`) | Tạo | Không |
| `domain/model/GlassLevelOverride.kt` (thêm `CUSTOM`) | Sửa | Không |
| `domain/model/LauncherSettings.kt` (thêm `glassCustom` ở cuối) | Sửa | Dùng chung (chỉ thêm) |
| `domain/repository/SettingsRepository.kt` (thêm `setGlassCustomElement`, `setGlassCustom`) | Sửa | Dùng chung (chỉ thêm) |
| `data/settings/SettingsKeys.kt`, `SettingsRepositoryImpl.kt` (khối GS ở cuối) | Sửa | Dùng chung (chỉ thêm) |
| `core/glass/GlassRole.kt`, `GlassPolicy.kt`, `GlassEnvironment.kt`, `GlassSurfaceFactory.kt`, `GlassTierResolver.kt`, `GlassBudget.kt`; `GlassConfig.kt` | Sửa; tạo `GlassConfig` | **Độc quyền (`core/glass/*`, GS giữ trong đợt 1)** |
| `ui/home/controller/DockController.kt` (so `GlassConfig`, tạo dock qua chính sách) | Sửa | Không (file R1, không nhánh nào trong đợt 1 khai) |
| `ui/home/view/BackgroundGlass.kt` (tổng quát hóa đường `REAL`) | Sửa | Không (file GL/AN) |
| `ui/home/view/SearchPillView.kt` (`View` → `FrameLayout`, kính thật) | Sửa | Không |
| `ui/home/view/CategoryPageView.kt` (chỉ Task 5, nếu qua cổng: móc "đã dừng" để chụp thanh Library) | Sửa có điều kiện | Không (GP/G2c khai `IconGridView`, `HomePagerAdapter`, không phải file này) |
| `ui/settings/SettingsRow.kt` (thêm `ID_GLASS_CUSTOM_*`), `SettingsUiState.kt` (thêm `glassCustom`, `realGlassCapable`), `SettingsViewModel.kt` (ánh xạ + `onGlassCustomChosen` + `onGlassCustomToggled`; thêm tham số `GlassEnvironment`), `SettingsAdapter.kt` (nhánh công tắc), `SettingsActivity.kt` (lựa chọn thứ tư trong hộp thoại Mức kính, nhãn `CUSTOM`, khối công tắc cuối màn, hộp thoại cảnh báo) | Sửa (chỉ thêm, trừ hộp thoại Mức kính nhận thêm một lựa chọn) | Dùng chung (khối GS ở cuối) |
| `res/values/strings.xml` (khối `gs_` ở cuối) | Sửa (chỉ thêm) | Dùng chung |
| **Không đụng (khai rõ):** `HomeActivity.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `activity_home.xml`, `HomePagerController.kt`, `AndroidManifest.xml`, `ui/search/*` (kể cả `SearchOverlayView.kt`, `view_search_overlay.xml`), `domain/search/*`, `IconGridView.kt`, `HomePagerAdapter.kt`, `LayoutReconciler.kt`, `data/layout/db/*`, `DragHelper.kt`, `DragDropTarget.kt`, `colors.xml`/`dimens.xml` (không thêm), mọi file `docs/` dùng chung | — | Của GP/M8d/G2c/LC/điều phối |

Hộp thoại Mức kính hiện có (`showGlassDialog`) nhận thêm lựa chọn thứ tư: đó là chỗ duy nhất sửa mã Cài đặt đã có ngoài việc thêm; không đổi tên, không đổi chuỗi cũ.

Nếu trong lúc làm cần một file ngoài vùng này thì dừng và báo điều phối (MultiAgent-Workflow mục 5.1, 11).

## Global Constraints
- Luật `ui → domain ← data`: `GlassElement`/`GlassCustom` ở `domain` (thuần Kotlin). `core/glass` được import `domain.model` (đã có `GlassLevelOverride`). `GlassPolicy` vẫn **hàm thuần, không import `android.*`**.
- **Công tắc không tra tier, không tra `realGlassCapable`** (D4). Chỉ nhánh mức cũ dùng chúng. Không code nào suy `realGlassCapable` từ override hay từ công tắc.
- **Ba mức cũ không đổi**: Bảng 1 trên mọi (Mức kính × lớp máy); kiểm bằng so ảnh với `main` (Task 1). Công tắc bị bỏ qua khi mức ≠ Tùy chỉnh (`GlassConfig` chuẩn hóa).
- `GlassLevelOverride.CUSTOM` chỉ **thêm cuối** enum; đọc bằng `entries.find` (không `valueOf`), tên lạ/null → `AUTO`. Mọi `when` trên `GlassLevelOverride` phải có nhánh `CUSTOM` (tier của `CUSTOM` = tier của `AUTO`).
- Khóa DataStore boolean `glass_custom_dock`, `glass_custom_panels`, `glass_custom_search_bar`, `glass_custom_pill`; vắng = tắt.
- Mọi kính thật mới: `setInvalidationSuppressed(true)` ngay khi dựng và vẽ lại đúng một lần qua `captureOnce` (không `setDynamic(true)` — AGENTS.md GL, ~90 khung dư); panel co/dịch bằng spring dùng `setCaptureAtLayoutPosition(true)`.
- Cài đặt chỉ **ghi** (suspend, DataStore đã commit) và không `recreate()` hộ Home; chọn đúng mức đang dùng thì không ghi; ghi lỗi → Toast `settings_save_failed` và hàng về giá trị thật (mẫu M8a). Công tắc gieo ghi **trước**, mức `CUSTOM` ghi sau (giữa hai lần ghi mức chưa phải Tùy chỉnh nên không có tác dụng).
- Hộp thoại giữ tham chiếu và `dismiss()` ở `onDestroy`; hộp thoại cảnh báo không cùng lúc với hộp thoại chọn mức; Hủy cảnh báo thì dựng lại hàng (công tắc về tắt).
- `SettingsRow`/`SettingsUiState`/`SettingsViewModel`/`SettingsAdapter`/`SettingsActivity`/`SettingsKeys`/`strings.xml`: **chỉ thêm**, trong khối `// GS` ở cuối; không đổi tên, không đổi chuỗi đã có.
- Chữ hiển thị trong `strings.xml` (tiếng Việt, tiền tố `gs_`); tên file/định danh tiếng Anh; không hardcode chữ trong enum.
- Không thêm test, không thêm thư viện, không đổi schema Room, không đoán version, không xóa file (xóa hàm `maxRealGlass` là sửa nội dung `GlassBudget.kt`), **không cài APK** (điều phối chạy máy, quyết định 7), không `gradlew --stop`; build bằng `.\gradlew.bat assembleDebug -q "-Dorg.gradle.daemon.idletimeout=120000"` và **chỉ build khi cần** (bốn nhánh dùng chung RAM của máy).
- Số đo: so trong **cùng một phiên**, xen kẽ, **lặp cấu hình gốc giữa chừng và ở cuối**, ghi **tổng số khung, số khung > 33 ms, p95** (không chỉ Janky %); không so số tuyệt đối giữa hai phiên (bài học GL, AN).

## Review Focus
1. **Ba mức cũ không đổi**: ảnh chụp Home/dock/panel/lớp tìm kiếm/Library/pill của nhánh (Tự động, Ép thấp, Ép cao) khớp `main` từng điểm ảnh trên cả hai máy; số khung và p95 trong ngưỡng hồi quy (Task 6).
2. **Không có tổ hợp mâu thuẫn**: `GlassPolicy.modeFor` đúng Bảng 1 và Bảng 2 cho mọi (Mức kính × lớp máy × công tắc); công tắc không đọc tier hay `realGlassCapable`; công tắc bị bỏ qua khi mức ≠ Tùy chỉnh.
3. **Giá trị cũ của `glass_level_override` vẫn nạp đúng** (`AUTO`, `FORCE_LOW`, `FORCE_HIGH`; tên lạ → `AUTO`); mọi `when` trên enum có nhánh `CUSTOM`.
4. **Gieo công tắc**: lần đầu chuyển sang Tùy chỉnh từ mỗi mức và mỗi lớp máy không đổi màn hình (so ảnh trước/sau); lần sau giữ đúng công tắc đã lưu (không gieo lại).
5. **Cảnh báo API < 33** hiện đúng khi và chỉ khi **bật** công tắc trên máy `!realGlassCapable`; Hủy đưa công tắc về tắt; không phụ thuộc Ép cao.
6. **Luật kính thật (TL1/AN 9g)** có ở từng phần tử mới: ô nhập, thanh Library (nếu qua cổng), pill; mỗi kính thật luôn bị chặn `invalidate()`; panel `setCaptureAtLayoutPosition(true)`; cuộn trang/cuộn danh sách không làm Janky tăng (Task 6).
7. **Không bề mặt kính nào giữ trạng thái cũ sau khi đổi mức/công tắc**: dock, hai panel, ô nhập, thanh Library, pill đều đúng chế độ mới sau một lần dựng lại; đổi công tắc khi mức ≠ Tùy chỉnh không dựng lại Home.
8. **Hợp với `notifyBackdropChanged`**: đổi ảnh nền (nạp đầu, thay, xóa) làm kính thật của mọi phần tử đang thật chụp lại đúng một lần, kính mờ vẽ lại; thanh/pill chưa có ảnh nền lúc gắn thì chuyển sang thật khi ảnh tới.
9. **Pill giữ nguyên hành vi** ở ba mức cũ: chạm, `PressScale`, chấm trang, TalkBack, thứ tự vẽ, `INVISIBLE` khi chưa sẵn sàng.
10. **`GlassBudget`**: không còn hàm chết; KDoc ghi số đo thật; không có trần ngầm.
11. **Không sửa file ngoài Vùng file**; `git diff --stat origin/main` không chạm `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `AndroidManifest.xml`, `ui/search/*`, `IconGridView`, `HomePagerAdapter`, `LayoutReconciler`, `DragHelper`.
12. **Cài đặt**: khối "Kính tùy chỉnh" chỉ hiện ở mức Tùy chỉnh; chọn đúng mức đang dùng không ghi; ghi lỗi → Toast + hàng đúng; xoay màn hình khi hộp thoại mở không rò cửa sổ.
13. Không móc/công tắc debug còn trong `app/` (`grep` sạch).

---

### Task 1: Mô hình, DataStore, chính sách và dock (chưa có giao diện) — code xong 2026-11-05, build xanh, chờ review điều phối; Step 6 do điều phối chạy máy
**Bắt buộc kiểm tay:** không. Máy: do điều phối chạy (Live 4 so ảnh `main`; Oppo so ảnh `main`, đổi công tắc bằng công cụ nối protobuf).
**Files:** `domain/model/GlassCustom.kt` (tạo), `GlassLevelOverride.kt`, `LauncherSettings.kt`, `SettingsRepository.kt`, `SettingsKeys.kt`, `SettingsRepositoryImpl.kt`, `core/glass/{GlassRole,GlassPolicy,GlassEnvironment,GlassSurfaceFactory,GlassTierResolver}.kt`, `core/glass/GlassConfig.kt` (tạo), `ui/home/controller/DockController.kt`.
**Interfaces:**
- Consumes: `SettingsRepository.settings`, `GlassTierResolver`.
- Produces: `GlassLevelOverride.CUSTOM`; `GlassElement`, `GlassCustom` (`operator get(element)`, `with(element, on)`); `LauncherSettings.glassCustom`; `SettingsRepository.setGlassCustomElement(element, on)` và `setGlassCustom(custom)` (bốn khóa một `edit {}`); `GlassRole.DOCK`, `GlassRole.element: GlassElement?`; `GlassPolicy.modeFor(tier, role, realGlassCapable, custom: GlassCustom?)`; `GlassConfig(override, custom)` (công tắc chuẩn hóa về tắt khi mức ≠ `CUSTOM`); `GlassEnvironment.config`, `readConfig()` (suspend, đọc `first()` và cập nhật chính nó), `realGlassCapable` (công khai), `seedFor(level): GlassCustom`; `GlassSurfaceFactory.create(config)`.
- [x] **Step 0 (công thức ghi ở Nhật ký, agent không chạy máy): Công cụ đặt cấu hình khi đo (không nằm trong mã; điều phối dùng ở Task 6).** Hàm PowerShell nối bản ghi vào tệp DataStore: `force-stop`; kéo `files/datastore/launcher_settings.preferences_pb` bằng `cmd /c "adb exec-out run-as gambi.global.os26launcher cat files/datastore/launcher_settings.preferences_pb > old.pb"` (nhị phân); nối với mỗi cặp khóa/giá trị một bản ghi `0A <len> { 0A <len khóa> <khóa> 12 <len> { <giá trị> } }`, giá trị chuỗi là `2A <len> <bytes>`, boolean là `08 <0|1>`; `adb push` lên `/data/local/tmp`, `run-as ... cp` đè lại. Protobuf cho phép nối: bản ghi sau của cùng khóa đè bản trước. **Điều phối kiểm tool bằng khóa đã có** (`glass_level_override=FORCE_LOW` trên Oppo → dock mờ sau khởi động lạnh) trước khi tin; không chạy thì dùng công tắc tạm theo mẫu AN. *Suy luận từ định dạng DataStore, chưa kiểm chứng.* Agent chỉ ghi công thức vào Nhật ký, không chạy trên máy.
- [x] **Step 1: Domain + DataStore.** `CUSTOM` thêm cuối `GlassLevelOverride`; `GlassElement { DOCK, PANELS, SEARCH_BAR, PILL }`, `GlassCustom` (bốn Boolean, mặc định false). Bốn khóa boolean ở khối `// GS` cuối `SettingsKeys`; đọc trong `settings` map; `setGlassCustomElement` ghi một khóa, `setGlassCustom` ghi bốn khóa trong **một** `edit {}`.
- [x] **Step 2: Chính sách.** `GlassRole.DOCK` và thuộc tính `element` (`DOCK→DOCK`, hai panel→`PANELS`, `SEARCH_BAR`/`LIBRARY_SEARCH_BAR`→`SEARCH_BAR`, `SEARCH_PILL`→`PILL`, `FOLDER_CELL`/`GROUP_TILE`→`null`). `GlassPolicy.modeFor(tier, role, realGlassCapable, custom = null)`: `custom != null` và role có element → `REAL` nếu công tắc bật, ngược lại `STATIC_BLUR`; còn lại như cũ, thêm nhánh `DOCK`: `tier != CLASSIC_CPU` → `REAL`, ngược lại `STATIC_BLUR` (đúng `GlassSurfaceFactory` hôm nay). `GlassTierResolver.resolve`: `CUSTOM` → như `AUTO`.
- [x] **Step 3: Môi trường + factory.** `GlassEnvironment` thu `settings.map { GlassConfig(override, custom) }.distinctUntilChanged()`, cập nhật `tier` (từ mức) và `custom` (null nếu mức ≠ `CUSTOM`), báo listener khi đổi; `readConfig()` đọc một lần và gán cùng cấu hình (để kiểm tra trước `recreate()` làm mới luôn singleton). `modeFor(role)` truyền `custom`. `seedFor(level)` = với mỗi phần tử, `GlassPolicy.modeFor(tier của level, vai đại diện, realGlassCapable, null) == REAL`. `GlassSurfaceFactory.create(config)`: tier từ mức như cũ, hỏi `GlassPolicy.modeFor(tier, DOCK, capable, custom)`: `REAL` → `RealGlassSurface`, `STATIC_BLUR` → `StaticBlurSurface` (giữ `STATIC_BLUR_BUILT`).
- [x] **Step 4: Dock.** `DockController` đọc `GlassEnvironment.from(context).readConfig()` một lần (thay `viewModel.currentGlassLevelOverride()`), lưu `appliedGlass: GlassConfig?` (null cho tới khi đọc xong, như `appliedGlassOverride` hôm nay), `glassOverrideChanged()` trả `readConfig() != applied` (giữ tên để không sửa `HomeActivity`; cập nhật KDoc).
- [x] **Step 5: Build, commit** `feat: add a custom glass level with per-element switches to the glass policy`.
- [ ] **Step 6: Kịch bản kiểm (điều phối chạy máy)** — (a) Live 4 Ép cao và Tự động, Oppo Tự động và Ép thấp: ảnh chụp Home đứng yên nhánh vs `main` khớp từng điểm ảnh (dock, pill); (b) Oppo: nối `glass_level_override=CUSTOM` + `glass_custom_dock=false` → dock mờ; Live 4 Tự động + `CUSTOM` + `glass_custom_dock=true` → dock thật; (c) nối giá trị lạ `glass_level_override=XYZ` → như `AUTO`, không crash; (d) đổi giá trị giữa hai lần mở Home → `RecreateGuard` dựng lại một lần (`logcat -b events | findstr am_create_activity`), dock không 0×0 (`dumpsys activity top`).

---

### Task 2: Mức kính "Tùy chỉnh" và khối công tắc trong Cài đặt — code xong 2026-11-05, build xanh, chờ review điều phối; Step 6 do điều phối chạy qua giao diện Cài đặt
**Bắt buộc kiểm tay:** không. Máy: Live 4 (thấy cảnh báo), Oppo (không cảnh báo) — điều phối chạy.
**Files:** `ui/settings/{SettingsRow,SettingsUiState,SettingsViewModel,SettingsAdapter,SettingsActivity}.kt`, `res/values/strings.xml`.
**Interfaces:** Consumes `SettingsRepository`, `GlassEnvironment` (`realGlassCapable`, `seedFor`). Produces khối cuối màn "Kính tùy chỉnh".
- [x] **Step 1: Hộp thoại Mức kính.** Thêm lựa chọn thứ tư "Tùy chỉnh (chọn kính thật/mờ từng phần tử)" và nhánh `CUSTOM` trong `shortLabelRes`. Chọn `CUSTOM` từ mức khác → `SettingsViewModel.onGlassCustomChosen()`: nếu `!glassCustomSaved` thì ghi `seedFor(mức hiện tại)` (`setGlassCustom`), rồi ghi mức `CUSTOM`; lỗi → Toast. Chọn mức khác → `onGlassOverrideChosen` như cũ. Chọn đúng mức đang dùng → không ghi.
- [x] **Step 2: Khối công tắc.** `buildRows` nối thêm ở **cuối** (khi `glassOverride == CUSTOM`): `Header("header_glass_custom", "Kính tùy chỉnh")` + bốn `SettingsRow.Switch` (`ID_GLASS_CUSTOM_DOCK/PANELS/SEARCH_BAR/PILL`: "Dock", "Panel (thư mục, nhóm Library)", "Thanh tìm kiếm", "Pill tìm kiếm / chấm trang"; bật = kính thật, tắt = kính mờ). `SettingsUiState` thêm `glassCustom`, `realGlassCapable`; `SettingsViewModel` ánh xạ, thêm tham số `GlassEnvironment`.
- [x] **Step 3: Chuyển công tắc + cảnh báo.** `SettingsAdapter` chuyển các id này vào callback công tắc; `SettingsActivity` → `viewModel.onGlassCustomToggled(element, on)`. Bật khi `!realGlassCapable` → hộp thoại cảnh báo ("Vẫn bật"/"Hủy"); chỉ ghi khi "Vẫn bật", Hủy thì dựng lại hàng (`forceRebind`). Tắt hoặc `realGlassCapable` → ghi ngay. Không `recreate()` Home.
- [x] **Step 4: Chuỗi** `gs_` ở cuối `strings.xml`.
- [x] **Step 5: Build, commit** `feat: add the custom glass switches to settings`.
- [ ] **Step 6: Kịch bản kiểm (điều phối chạy máy)** — (a) mặc định không thấy khối công tắc; (b) lần đầu chọn Tùy chỉnh: khối hiện, màn Home không đổi (so ảnh), công tắc khớp Bảng 3 (Live 4 Ép cao: Dock bật còn lại tắt); bật/tắt vài công tắc, sang Tự động rồi quay lại Tùy chỉnh: công tắc đã lưu được giữ; (c) Live 4 bật Pill → cảnh báo; Hủy → công tắc về tắt, không ghi; Vẫn bật → bật; (d) Oppo: cùng thao tác không cảnh báo; (e) chọn lại Tự động/Ép thấp: khối ẩn, Home như `main`; (f) xoay màn khi hộp thoại mở không rò cửa sổ (`logcat | findstr WindowLeaked`); (g) về Home: dựng lại đúng một lần, edit mode/trang giữ nguyên; đổi công tắc rồi về Home dựng lại một lần, đổi lại như cũ thì không dựng lại.

---

### Task 3: Panel và ô nhập lớp tìm kiếm theo công tắc — tổng quát hóa `BackgroundGlass` — code xong 2026-11-05, Kotlin biên dịch xanh, chờ review điều phối; Step 6 do điều phối chạy máy
**Bắt buộc kiểm tay:** không (đưa vào bài kiểm cuối); điều phối kiểm nhanh trên Oppo. Máy: Oppo (chính), Live 4 (đường API < 33).
**Files:** `ui/home/view/BackgroundGlass.kt`. (Panel đã dùng `env.modeFor(role)` nên chỉ cần chính sách mới; ô nhập dùng chính `BackgroundGlass` có sẵn trong `SearchOverlayView`, không sửa file đó.)
**Interfaces:** Consumes `GlassEnvironment.modeFor`, `RealGlassSurface`. Produces đường `REAL` dùng được khi target lồng nhiều tầng và host không gọi hook.
- [x] **Step 1: Vị trí kính.** `layoutGlass()` tính khung của target trong tọa độ host bằng `host.offsetDescendantRectToMyCoords(target, rect)` (thay `target.left/top`), nên đúng cho target lồng và host có padding. Host (`FolderPanelView`, `CategoryGroupPanelView`) vẫn gọi `layoutGlass()` như cũ.
- [x] **Step 2: Tự thấy hook.** `attach()` gắn `host.addOnLayoutChangeListener { layoutGlass() }` (gỡ ở `detach()`) và theo dõi `host.isShown`: chuyển từ ẩn sang hiện thì `onShown()` (idempotent; panel vẫn gọi tay). Nhờ vậy `SearchOverlayView` (`ui/search`, M8d) không phải sửa.
- [x] **Step 3: Ảnh nền tới sau khi gắn.** `backdropListener` đổi từ `captureOnce()` thành `apply()` rồi `captureOnce()`: thanh được gắn khi `backdrop_image` còn chưa có ảnh (sau `recreate()` luôn như vậy) sẽ rơi vào kính mờ, và phải chuyển sang kính thật khi ảnh tới (Review Focus 6). Ảnh bị gỡ (`CLEARED`) → `apply()` rơi về mờ/phẳng như hôm nay.
- [x] **Step 4: Đồng bộ biến đổi.** `invalidateGlass()` ở đường `REAL` sao chép `scaleX/scaleY/translation/alpha` từ target sang view kính thật (như `applyMotion`), để `PressScale` của thanh Library co cả kính.
- [x] **Step 5: Build, commit** `feat: lay out real glass for nested targets and late backdrops`.
- [ ] **Step 6: Kịch bản kiểm** — (a) Oppo, Tùy chỉnh với Panel tắt: mở panel thư mục và panel nhóm → kính mờ; mức Tự động → kính thật như `main` (so ảnh); (b) Oppo, Tùy chỉnh với Thanh tìm kiếm bật: vuốt xuống mở lớp tìm kiếm → ô nhập là kính thật đúng khung (không lệch theo inset trạng thái), gõ tiếng Việt có dấu bình thường, nút xóa hiện/ẩn; đóng/mở nhiều lần không lệch; (c) Thanh tìm kiếm tắt / mức Tự động → như `main`; (d) Live 4 Tùy chỉnh với Panel bật (có cảnh báo qua UI): panel thật hoạt động (giật là dự kiến, số đo ở Task 6); (e) đổi ảnh nền ở Cài đặt rồi về Home: ô nhập thật và panel thật hiện ảnh mới (Review Focus 6); (f) không crash khi mở lớp tìm kiếm lúc chưa có ảnh nền.

---

### Task 4: Pill kính thật — code xong 2026-11-05, Kotlin biên dịch xanh, chờ review điều phối; Step 6 do điều phối chạy máy
**Bắt buộc kiểm tay:** không (đưa vào bài kiểm cuối). Máy: Oppo (chính), Live 4.
**Files:** `ui/home/view/SearchPillView.kt`.
**Interfaces:** Consumes `GlassEnvironment` (`modeFor(SEARCH_PILL)`, `addBackdropListener`), `RealGlassSurface`. Giữ nguyên API công khai: `setPageCount`, `setSelected`, `setDotsMode`, `onSearchClick`.
- [x] **Step 1: Đổi lớp nền.** `SearchPillView : FrameLayout`; `setWillNotDraw(false)`; `onMeasure` vẫn `pillWidth × touchHeight`. `onInterceptTouchEvent` trả `true` (con không nuốt chạm); con `importantForAccessibility = no`; `isClickable`, `PressScale`, accessibility delegate, `contentDescription` giữ nguyên.
- [x] **Step 2: Thứ tự vẽ.** `onDraw` chỉ vẽ nền mờ/phẳng khi không ở `REAL`; `dispatchDraw` = `super.dispatchDraw` (kính thật) rồi lớp nhấn + nội dung (kính lúp/chữ/chấm). Với ba mức cũ kết quả vẽ giống cũ.
- [x] **Step 3: Kính thật.** Khi `modeFor(SEARCH_PILL) == REAL` và `backdrop_image` có ảnh: thêm `RealGlassSurface` làm con 0, `setCornerRadius(pillHeight/2)`, `setTint(gl_bar_tint)`, `setBackdrop(backdrop_image)`, `setInvalidationSuppressed(true)` ngay, `setCaptureAtLayoutPosition(true)`; đặt khung ở giữa view (`onLayout`). `captureOnce()` (cùng ba bước của `BackgroundGlass`) khi: gắn vào cửa sổ, kích thước đổi, ảnh nền đổi (`addBackdropListener`/`removeBackdropListener`), view từ `INVISIBLE` sang hiện. Pill không bao giờ cần chụp lại khi cuộn trang (vị trí và nền đứng yên): luật "luôn chặn" thỏa TL1 với mọi nguồn cuộn, kể cả cuộn pager.
- [x] **Step 4: Dọn.** Gỡ listener và hủy lệnh chờ ở `onDetachedFromWindow`; `GONE` khi không `REAL`.
- [x] **Step 5: Build, commit** `feat: render the search pill with real glass on request`.
- [ ] **Step 6: Kịch bản kiểm** — (a) so ảnh ba mức cũ với `main` trên cả hai máy: chế độ ô tìm kiếm, chấm trang giữa lúc vuốt, đang bấm (lớp nhấn); (b) Oppo Tùy chỉnh với Pill bật: pill thật, chữ "Tìm kiếm" rõ, vuốt trang: co thành chấm vẫn là kính thật, không chớp; bấm → mở lớp tìm kiếm; giữ rồi nhả → co/nở (`PressScale`); (c) edit mode (chấm) và đang kéo icon: không lệch; (d) TalkBack bật: pill đọc "Tìm kiếm"/"Trang X trên Y" như cũ, không thêm nút lạ; (e) đổi ảnh nền: pill thật chụp lại; (f) Live 4 Pill bật có cảnh báo, chạy được.

---

### Task 5: Thanh tìm kiếm của Library (có cổng) — Step 1 làm sẵn (agent không chạy máy được để thử cổng), Kotlin biên dịch xanh; điều phối quyết giữ hay bỏ Step 1 theo cổng
**Bắt buộc kiểm tay:** không (cổng bằng mắt của điều phối trên Oppo, quyết định cuối ở bài kiểm cuối). Máy: Oppo.
**Files:** `ui/home/view/CategoryPageView.kt` (nếu qua cổng).
**Cổng:** Thanh Library nằm trong host trượt bằng `translationX` (`LibraryOverlayController`), nên kính thật chụp ảnh nền theo vị trí tức thời của host. Chạy thử với công tắc Thanh tìm kiếm bật **không sửa mã** trước: nếu lúc Library trượt vào/ra kính thật nhìn thấy nhảy hình, làm Step 1; không đạt sau Step 1 thì áp D14 (công tắc "Thanh tìm kiếm" chỉ áp cho ô nhập; ghi rõ ở nhãn hàng; `LIBRARY_SEARCH_BAR` bỏ khỏi `GlassElement.SEARCH_BAR` trong `GlassRole.element`).
- [x] **Step 1: Móc "đã dừng".** Khi Library dừng ở trạng thái mở (translationX = 0, hook sẵn có `setTranslationX` của `CategoryPageView`), gọi `searchBarGlass.captureOnce()`; lúc đang trượt kính thật co như một texture (ảnh của lần chụp cuối). Đã làm kèm: `SuppressibleGlassView.getLocationOnScreen` (cờ `captureAtLayoutPosition`) bỏ thêm `translationX/Y` của cha trực tiếp, để lần chụp lúc host còn ngoài màn vẫn lấy vùng ảnh nền ở chỗ cuối (host của panel và pill không có translation nên không đổi). Nếu lần mở đầu tiên khung texture sai, thêm lệch vị trí chụp bằng `translationX` của host (đã được tính trong `getLocationOnScreen` của cha: kiểm bằng đo).
- [x] **Step 2: Build, commit** `feat: capture the library search bar glass when the library settles`.
- [ ] **Step 3: Kịch bản kiểm (Oppo)** — (a) Library mở/đóng 20 lần: thanh không nhảy hình; (b) giữ thanh: co nhẹ cả kính (Task 3 Step 4); (c) đổi ảnh nền rồi mở Library: thanh hiện ảnh mới; (d) cuộn lưới ô nhóm: thanh không chụp lại (số khung không tăng, Task 6 S6).

---

### Task 6: Đo — Oppo bật hết (dưới 1 giờ), Live 4 hồi quy và câu chữ cảnh báo — xong 2026-11-05 (Oppo đạt; Live 4 không có ngưỡng, số đo ghi bên dưới); F2 chưa chạy
**Bắt buộc kiểm tay:** có — mục 4.2 của MultiAgent-Workflow.md (phép đo hiệu năng dùng để chọn phương án: quyết trần số kính thật, câu chữ cảnh báo, và Task 5 có giữ thanh Library hay không). Người chạy: điều phối (quyết định 7); agent không cài APK. Máy: **Oppo A3x (hàng đợi: LC trước, GS sau)** và Vsmart Live 4. Đã cắt theo chỉ đạo điều phối 2026-11-05.
**Files:** `docs/plans/GS-glass-settings.md` (bảng đo trong Nhật ký). Không code.

**Giao thức** (kế thừa "Giao thức đo" của AN và bài học GL/AN ở cuối `AGENTS.md`):
- Hai APK: `main` ("trước") và nhánh ("sau"), cùng một phiên; cùng ảnh nền chi tiết, 12 thư mục + Library đủ nhóm, cắm sạc, độ sáng cố định; ghi nhiệt độ pin đầu/cuối. Oppo khởi động nguội ~6,5 giây tới lúc có lưới (P1), chờ 12 giây sau `am start`.
- Đặt cấu hình bằng công cụ Task 1 Step 0, `force-stop` rồi `am start -n gambi.global.os26launcher/.ui.home.HomeActivity`. Mỗi cảnh: `dumpsys gfxinfo gambi.global.os26launcher reset`, chạy, đọc `gfxinfo` + `framestats` lấy mẫu định kỳ, khử trùng theo `IntendedVsync`, chỉ dòng `^\d+,` (hàm `FS-Stats` trong AN).
- **2 lượt mỗi ô.** Báo mỗi ô: **tổng số khung, số khung > 33 ms, gfx p50/p95, framestats total p50/p95, `rec` p50/p95, Janky %**; đọc số khung tuyệt đối trước %.

**Oppo** — thứ tự `main, B0, ALL, B0, main`; `B0` = mức Tự động (= hành vi `main`), `ALL` = Tùy chỉnh với cả bốn công tắc bật. Không có cấu hình Z.

| Cảnh | Nội dung | Phần tử |
|---|---|---|
| G1 | Home đứng yên 30 giây (ghi thêm `top -H -b -n 1` CPU nền, chỉ ghi) | dock, pill |
| G2 | Vuốt trang qua lại 30 giây (GL S2) | dock, pill |
| G4 | Library mở/đóng ×20 rồi panel nhóm mở/đóng ×20 (S4+S5): **4 kính cùng hiện** | dock, pill, thanh Library, panel nhóm |
| G7 | Edit mode rung 20 giây, rồi rung + vuốt (S3i, S3s) | dock, pill |
| F2 | Đổi mức/công tắc 10 lần qua lại Cài đặt ↔ Home: `am_create_activity` tăng đúng 10, dock không 0×0, không rò (`dumpsys meminfo` Views/Activities) | tất cả |

**Live 4** — hồi quy mặc định: `main` vs `L0` (mức Tự động) trên **G2 và G3** (G3 = panel thư mục mở/đóng ×20, GL S1). Câu chữ cảnh báo: `LF` (Ép cao, máy chủ dự án) và `ALL` **một lượt** trên G2. Không cô lập từng phần tử.

**Ngưỡng đạt** (đã duyệt 2026-11-05)
- **Hồi quy mặc định** (nhánh vs `main` cùng phiên): Janky ≤ +0,5 điểm, Δp95 ≤ +1 ms, số khung > 33 ms không tăng quá max(2, 10%), tổng khung không lệch quá 5%. Trượt thì dừng và báo.
- **Oppo bật hết** (`ALL` so `B0`; `B0` đã gồm dock và hai panel thật nên hiệu số cô lập đúng chi phí của pill + thanh): Janky ≤ +1,0 điểm, Δp95 ≤ +4 ms; G1 tổng khung `ALL` ≈ `B0` (±5%; khung dư là dấu hiệu vòng `invalidate()`); số khung > 33 ms trong G2, G7 không tăng. Ngoại lệ D10 của GL (một khung trễ mỗi lần mở panel) nằm sẵn trong `B0`.
- **Live 4 có kính thật (API < 33):** không có ngưỡng; số đo dùng để chọn câu chữ cảnh báo (D5).
- **`GlassBudget`:** `ALL` đạt trên Oppo ⇒ không trần, xóa `maxRealGlass` (D11). Không đạt ⇒ báo điều phối kèm số đo (không tự thêm trần; cô lập bằng cách chạy lại với từng công tắc do điều phối quyết).

- [x] **Step 1: Chuẩn bị (điều phối).** Hai APK, kịch bản dữ liệu, `adb devices` thấy đúng máy, kiểm tool Task 1.
- [x] **Step 2: Hồi quy mặc định** (Oppo G1, G2 và Live 4 G2, G3) — dừng nếu trượt.
- [x] **Step 3: Oppo bật hết** (G1, G2, G4, G7, F2) theo thứ tự trên.
- [x] **Step 4: Live 4** `LF` và `ALL` một lượt trên G2.
- [x] **Step 5: Điền bảng và kết luận** vào Nhật ký (máy, cảnh, cấu hình, tổng khung, > 33 ms, p50/p95, Janky) + kết luận cho D5 và D11. **Dừng, báo điều phối** trước Task 7.

---

### Task 7: Chốt cảnh báo và `GlassBudget`, dọn, knowledgement, rebase, đóng nhánh
**Bắt buộc kiểm tay:** không (gộp vào bài kiểm cuối).
**Files:** `res/values/strings.xml` (câu chữ cảnh báo), `core/glass/GlassBudget.kt`, `docs/knowledgements/GS-glass-settings.md`, plan.
- [x] **Step 1: Cảnh báo.** Đặt câu chữ cảnh báo theo số đo Task 6 (kết quả: giữ nguyên chuỗi `gs_warn_message`, đã nêu số đo panel của Live 4 từ GL) (nêu số cho phần tử đã đo kém, câu chung cho phần còn lại); chưa đo được phần tử nào thì ghi "chưa đo" thay vì suy diễn.
- [x] **Step 2: `GlassBudget`.** Xóa `maxRealGlass` và hằng `LENS_MAX`/`CLASSIC_MAX` (hàm chết), thay bằng KDoc nêu số kính thật cùng hiện tối đa (4) và kết quả đo Oppo; không đổi hàm ngân sách ô thư mục/ô nhóm.
- [x] **Step 3: Dọn.** `grep` không còn công tắc/log debug; nếu từng dùng công tắc tạm thì `git diff <commit đã kiểm> HEAD -- app` rỗng. `grep currentGlassLevelOverride` chỉ còn định nghĩa trong `HomeViewModel` (ghi vào đề xuất cập nhật).
- [x] **Step 4 (bản nháp đã viết đủ mục 4-8, chỉ còn điền số đo Task 6 và bảng bằng chứng): Knowledgement** hoàn chỉnh (Mermaid khớp code), "Bài học cho AGENTS.md".
- [ ] **Step 5: Rebase** lên `main` mới nhất, build xanh, điền "Bằng chứng Review Focus", "Đề xuất cập nhật tài liệu chung", chuyển PR sang Ready for review (sau khi điều phối review toàn nhánh).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Oppo A3x (kính thật) và Vsmart Live 4 (cảnh báo API < 33). Chuẩn bị: ảnh nền chi tiết, vài thư mục, Library có đủ nhóm. Thứ tự gợi ý: (1) ba mức cũ giống `main` (Home, dock, pill, panel, lớp tìm kiếm, Library); (2) Mức kính → Tùy chỉnh: màn hình không đổi, khối "Kính tùy chỉnh" hiện ở cuối; bật/tắt từng công tắc, Home tự dựng lại một lần, nhìn bằng mắt từng bề mặt (tint, chữ đọc được, viền); (3) cảnh báo trên Live 4 khi bật, không có trên Oppo; (4) đổi ảnh nền khi đang bật kính thật; (5) bật hết rồi vuốt, rung, kéo-thả, mở mọi lớp phủ, xoay màn, split-screen; (6) quay về Tự động: khối ẩn, mọi thứ như `main`; (7) hồi quy: dock kéo-thả, menu nhấn giữ, tìm kiếm tiếng Việt có dấu, TalkBack với pill.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa có.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-05 | Plan | Viết plan + knowledgement bản nháp, mở draft PR | `7f787b3` | Chờ điều phối rà |
| 2026-11-05 | Plan | Chủ dự án duyệt, đổi thiết kế sang mức "Tùy chỉnh" + công tắc boolean; sửa plan, rebase lên `main` | `3dee2b8` | Đo Oppo rút còn < 1 giờ; hàng đợi Oppo: LC rồi GS |
| 2026-11-05 | 1 | Domain `GlassCustom`/`GlassElement`, `CUSTOM` cuối enum, bốn khóa boolean; `GlassPolicy.modeFor(..., custom)` + `GlassRole.DOCK`; `GlassConfig`; `GlassEnvironment.readConfig/seedFor`; `GlassSurfaceFactory.create(config)`; `DockController` so `GlassConfig`; nhãn `CUSTOM` ở `SettingsActivity` (để `when` biên dịch; phần còn lại của hộp thoại ở Task 2). `assembleDebug` xanh | `82c56f1` | Công thức nối protobuf (Step 0): bản ghi `0A len {0A len khóa 12 len {2A len chuỗi \| 08 0/1}}` nối vào cuối `files/datastore/launcher_settings.preferences_pb` (kéo bằng `cmd /c "adb exec-out run-as ... cat ... > old.pb"`, push `/data/local/tmp`, `run-as cp`); chưa kiểm trên máy. Điều phối đã review Task 1 từ báo cáo và đi tiếp; Step 6 chạy qua giao diện Cài đặt sau Task 2 (công cụ protobuf chỉ giữ nếu Task 6 thật sự cần) |
| 2026-11-05 | 2 | Hộp thoại Mức kính có lựa chọn thứ tư; `onGlassCustomChosen` (gieo chỉ khi `!glassCustomSaved`), `onGlassCustomToggled`; khối "Kính tùy chỉnh" ở cuối danh sách; cảnh báo API < 33 khi bật (Hủy/đóng → hàng dựng lại, công tắc về tắt); `LauncherSettings.glassCustomSaved`; chuỗi `gs_`. Sửa D8 theo điều phối. `assembleDebug` xanh | (commit Task 2) | Không đổi `HomeActivity`/Manifest. Push thường, không ghi đè lịch sử |
| 2026-11-05 | 3 | `BackgroundGlass`: khung kính thật tính theo chuỗi cha (target lồng nhiều tầng), tự nghe layout của host và tự gọi `onShown` khi host hiện nếu host không gọi hook tay (panel vẫn gọi tay, không chụp đôi), `backdropListener` = `apply()` + `captureOnce()`, `invalidateGlass()` đồng bộ scale/pivot sang kính thật. Chỉ chạy `compileDebugKotlin` (xanh) vì chỉ đổi một file Kotlin, máy đang bận nhiều nhánh | `feat: lay out real glass for nested targets and late backdrops` | Không sửa `SearchOverlayView`/`ui/search/*`. Cho đến Task 4-5, chỉ panel, ô nhập và (khi đổi công tắc) dock thấy kính thật |
| 2026-11-05 | 4 | `SearchPillView` : `FrameLayout` (XML không đổi): `RealGlassSurface` làm con 0 khi `modeFor(SEARCH_PILL) == REAL` và đã có ảnh nền; nền mờ/phẳng vẫn vẽ ở `onDraw`, lớp nhấn + nội dung vẽ ở `dispatchDraw` sau con; `onInterceptTouchEvent = true`, con `importantForAccessibility = no`, luôn chặn `invalidate()` + `captureReal()` một lần khi gắn/đổi cỡ/hiện lại/ảnh nền đổi (`addBackdropListener`). `HomePagerController` không đổi. `compileDebugKotlin` xanh | `feat: render the search pill with real glass on request` | Cần so ảnh ba mức cũ với `main` (Review Focus 9) |
| 2026-11-05 | 5 | `CategoryPageView.setTranslationX`: chụp kính thật của thanh Library một lần khi Library vừa dừng ở translationX = 0; `RealGlassSurface` bỏ `translationX/Y` của cha khi tính vị trí chụp (chỉ đổi với host trượt). `compileDebugKotlin` xanh | `feat: capture the library search bar glass when the library settles` | Agent làm Step 1 sẵn vì không chạy máy được; cổng bằng mắt (Library trượt vào/ra, giữ thanh, đổi ảnh nền) do điều phối chạy trên Oppo; không đạt thì áp D14 (đổi `LIBRARY_SEARCH_BAR` khỏi `GlassElement.SEARCH_BAR`) |
| 2026-11-05 | 7 (chuẩn bị) | Knowledgement mục 4-8 viết xong từ code (chờ số đo Task 6 cho bài học đo được); bản vá `GlassBudget` (xóa `maxRealGlass`, KDoc không trần) soạn sẵn và thử trên bản sao, CHƯA áp vì D11 phụ thuộc phép đo bật hết | `docs: draft the GS knowledgement sections` | Điều phối Step 6 trên Oppo `6f0edd1`: seed, pill/ô nhập thật, Library bar sau 6 lần mở lại, parity Ép cao với `main` đều đạt; chưa kiểm: dots/press pill, Library đang trượt, đổi ảnh nền, không ảnh nền, TalkBack, parity Tự động/Ép thấp, cảnh báo Live 4. Chờ số đo Task 6 để chốt chữ cảnh báo và áp bản vá `GlassBudget` |
| 2026-11-05 | 6 (Oppo) | Điều phối đo Oppo A3x, build `6f0edd1`, thứ tự main, B0, ALL, B0b, main_end, 2 lượt mỗi ô, 33,5-34,1 °C; ảnh chụp xác nhận cấu hình ALL đã áp (pill khác Tự động). Bảng bên dưới. Dữ liệu thô: `scratchpad\an-gs\summary.csv` của điều phối | (không code) | Hồi quy mặc định đạt; ALL so B0 đạt; không cấu hình nào tăng số khung > 33 ms. Chưa chạy: F2 (vòng đổi 10 lần, đếm tạo lại; `am_create_activity` cho 0 trên Oppo nên không đếm được bằng cách đó), phần Live 4 (`main` vs L0 trên G2, G3; LF và ALL một lượt G2) chờ máy |
| 2026-11-05 | 6 (Live 4) | Điều phối đo Live 4 `6f0edd1`, thứ tự main, L0, LF, ALL, L0b, main, 2 lượt mỗi ô, 32,9 → 33,7 °C; UI qua Cài đặt: gieo từ Tự động ra cả bốn công tắc tắt, hộp thoại cảnh báo hiện, Hủy lật công tắc về tắt, Vẫn bật bật được, đủ cho cả bốn công tắc, không crash. Bảng Live 4 bên dưới | (không code) | Không hồi quy mặc định; chênh giữa các lượt lớn hơn chênh giữa cấu hình; giữ chữ cảnh báo. Parity Oppo với `main` ở Tự động và Ép thấp: Home, thư mục, tìm kiếm đều YMAX 0 (cùng Ép cao đã đo trước: ba mức cũ khớp trên Oppo) |
| 2026-11-05 | 7 | Áp bản vá `GlassBudget` (xóa `maxRealGlass`, bỏ `LENS_MAX`/`CLASSIC_MAX`, KDoc nêu không trần + số đo); `grep` không còn công tắc/log debug; `git diff --name-only origin/main...HEAD` không chạm `HomeActivity`/`HomeViewModel`/`HomeUiState`/`activity_home.xml`/Manifest/`ui/search`/`IconGridView`/`HomePagerAdapter`/`LayoutReconciler`/`DragHelper`; điền bằng chứng Review Focus và đề xuất cập nhật tài liệu chung. `assembleDebug` xanh | `refactor: remove the unused real glass cap from GlassBudget` | Chữ cảnh báo giữ nguyên: số Live 4 LF/ALL trên G2 cùng dải với L0 (panel ở ALL không đo, nên cảnh báo vẫn dựa số GL). Không rebase (điều phối quyết với chủ dự án); `git merge origin/main` sạch, không xung đột |
| 2026-11-05 | 3 (sửa sau review độc lập) | (1) `BackgroundGlass`: ghi cỡ và lề kính THẲNG vào `LayoutParams` (không `requestLayout`) thay vì `LayoutParams(0, 0)` + `layout()` thủ công, để mỗi lượt layout của host không đặt kính về 0x0 rồi đặt lại (hai lần đổi cỡ qua `View.setFrame` làm kính chụp lại nền, kể cả khi gõ chữ trong tìm kiếm); nghe `OnGlobalLayoutListener` thay `OnLayoutChangeListener` để `wasShown` về false khi host GONE (host GONE không được layout nên listener cũ không thấy). (2) `SearchPillView`: chụp lại khi dời chỗ không đổi cỡ (`onLayout(changed)`), gom các nguồn chụp vào một lệnh chờ. (3) Ghi giới hạn tier Ép thấp → Tùy chỉnh vào "Ngoài phạm vi". `assembleDebug` xanh | `fix: stop the real glass layout from resetting to zero size on host layouts` | Điều phối kiểm trên Live 4 `f752b95` (xem bảng gõ chữ bên dưới): sửa đúng. Chưa chạy: gõ chữ trên Oppo, kiểm khung kính bằng `dumpsys activity top`, mở lại sau đổi ảnh nền; phải so lại panel Oppo ở mức Tự động vì `placeGlass` đổi. Chạm: panel (cả mức Tự động trên Oppo), ô nhập tìm kiếm, thanh Library, pill |

**Bảng gõ chữ trong tìm kiếm, Vsmart Live 4 (API 30), Tùy chỉnh bật hết so Tự động, gõ 12 ký tự, xen kẽ, 2 lượt mỗi ô (hai giá trị lần lượt), không crash:**

| Bản | Cấu hình | Tổng khung | Khung janky (%) | p95 (ms) |
|---|---|---|---|---|
| cũ `6f0edd1` (`gs.apk`, cùng `BackgroundGlass` với `5f69ce5`) | Tùy chỉnh, bật hết | 74, 70 | 32, 32 (43.2%, 45.7%) | 57, 44 |
| mới `f752b95` | Tùy chỉnh, bật hết | 69, 67 | 5, 7 (7.3%, 10.5%) | 20, 19 |
| mới `f752b95` | Tự động (thanh tắt) | 65, 67 | 2, 4 (3.1%, 6.0%) | 11, 17 |

Đọc: bản cũ chụp lại kính ở mỗi phím (43-46% khung janky, p95 44-57 ms); bản mới còn 7-10% và p95 19-20 ms, gần mức Tự động (3-6%, 11-17 ms). Còn chênh nhỏ giữa bật hết và Tự động (vài khung) vì ô nhập thật tự nó tốn hơn ô mờ, không còn là chụp lại mỗi phím.

**Bảng đo Task 6, Oppo A3x (trung bình 2 lượt):**

| Cảnh | main | B0 (Tự động) | ALL (4 công tắc bật) | B0b | main_end |
|---|---|---|---|---|---|
| G2 vuốt: tổng khung | 3126 | 3084 | 3075 | 3117 | 3119 |
| G2 Janky % | 1.35 | 1.38 | 1.33 | 1.36 | 1.38 |
| G2 framestats p95 (ms) | 15.07 | 15.02 | 15.08 | 14.96 | 14.99 |
| G2 khung > 33 ms | 2.0 | 2.0 | 2.0 | 2.0 | 2.5 |
| G4 Library + panel nhóm: tổng khung | 1200 | 1197 | 1199 | 1204 | 1203 |
| G4 Janky % | 4.46 | 4.55 | 4.42 | 4.49 | 4.33 |
| G4 p95 (ms) | 25.5 | 25.12 | 24.95 | 25.16 | 24.82 |
| G4 khung > 33 ms | 35.0 | 32.5 | 33.0 | 33.5 | 34.0 |
| G7 rung Janky % | 0.21 | 0.18 | 0.22 | 0.22 | 0.18 |
| G7 p95 (ms) | 28.55 | 27.09 | 27.06 | 33.8 | 33.99 |
| G7 khung > 33 ms | 50.5 | 33.5 | 34.0 | 65.5 | 68.0 |

G1 đứng yên: 0 khung ở mọi cấu hình (ALL = B0), tức không có vòng `invalidate()`. G7 cao ở `main`, B0b, main_end là do một lượt đơn lẻ trong ba ô (64, 95, 100 khung > 33 ms), không phải ALL: coi là nhiễu. **Kết luận:** hồi quy mặc định đạt (Janky ≤ +0,5 điểm, Δp95 ≤ +1 ms, tổng khung trong 5%); ALL so B0 đạt (Janky ≤ +1,0 điểm, Δp95 ≤ +4 ms, G1 khung dư = 0, khung > 33 ms trong G2 và G7 không tăng); D11 giữ: không trần, xóa `maxRealGlass`. Phép đo G4 là trường hợp 4 kính cùng hiện (dock, pill, thanh Library, panel nhóm).

**Bảng đo Task 6, Vsmart Live 4 (API 30; 2 lượt mỗi ô, hai giá trị lần lượt; p95 = framestats, ms):**

| Cấu hình | Cảnh | Tổng khung | Janky % | p95 | Khung > 33 ms |
|---|---|---|---|---|---|
| main | G2 vuốt | 1959, 1982 | 0.82, 2.67 | 12.08, 16.47 | 3, 3 |
| L0 (Tự động) | G2 | 1960, 1964 | 1.12, 2.14 | 12.12, 15.48 | 4, 4 |
| LF (Ép cao) | G2 | 1964, 1959 | 2.39, 1.89 | 15.62, 15.34 | 4, 3 |
| ALL (4 công tắc bật) | G2 | 1965, 1883 | 2.09, 3.24 | 15.59, 16.86 | 3, 7 |
| L0b | G2 | 1989, 1988 | 3.92, 3.07 | 17.31, 16.74 | 3, 2 |
| main cuối phiên | G2 | 1971, 1964 | 1.62, 2.04 | 13.24, 15.03 | 6, 3 |
| main | G3 panel thư mục | 893, 904 | 13.55, 10.18 | 24.71, 21.53 | 7, 1 |
| L0 | G3 | 897, 900 | 5.69, 3.67 | 18.69, 17.31 | 7, 0 |
| main cuối phiên | G3 | 894, 898 | 3.69, 5.12 | 17.16, 17.97 | 5, 1 |

Đọc: không hồi quy mặc định; chênh giữa hai lượt cùng cấu hình lớn hơn chênh giữa các cấu hình (lượt `main` G3 đầu là ngoại lệ; `main` cuối phiên khớp L0). LF và ALL trên cảnh vuốt nằm trong cùng dải, một lượt ALL ít khung hơn (1883). **Panel ở ALL không được đo trên Live 4**, đúng là thứ cảnh báo nói tới: chữ cảnh báo giữ số đo cũ của GL (panel kính thật qua đường CPU: S1 Janky 30.7%, p95 112 ms). Dock, thanh tìm kiếm và pill kính thật trên Live 4 khi vuốt không thấy khác dải (suy luận chưa kiểm chứng: vì chúng luôn bị chặn `invalidate()` và chụp một lần).

## Bằng chứng Review Focus (điền khi đóng)
Nhãn: **đo** = số đo/ảnh chụp trên máy do điều phối chạy; **code** = chỉ được code bảo vệ, chưa kiểm tay riêng.

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Ba mức cũ không đổi | Oppo Ép cao: ảnh Home, panel thư mục, tìm kiếm khớp `main` (YMAX 0; một vùng lệch 1 bậc độ sáng); hồi quy số liệu G2/G4/G7 B0 vs main đạt. Oppo Tự động và Ép thấp: Home, thư mục, tìm kiếm khớp `main` (YMAX 0) — cùng Ép cao, cả ba mức cũ khớp trên Oppo. Live 4: số liệu `main` vs L0 trên G2 và G3 không hồi quy (bảng Live 4). Ảnh Live 4 từng mức: không chụp | Đạt (đo, Oppo ba mức; Live 4 bằng số liệu) |
| 2 | Không tổ hợp mâu thuẫn | Đọc `GlassPolicy.modeFor`: `custom` chỉ khác null ở mức Tùy chỉnh (`GlassConfig.activeCustom`); công tắc không đọc tier/`realGlassCapable` | Đạt (code) |
| 3 | Giá trị cũ của `glass_level_override` vẫn nạp; mọi `when` có `CUSTOM` | `entries.find` giữ nguyên, `CUSTOM` thêm cuối; build xanh buộc đủ nhánh `when` (`shortLabelRes`, `GlassTierResolver`) | Đạt (code + build) |
| 4 | Gieo công tắc | Oppo, Ép cao → Tùy chỉnh lần đầu: Dock bật, Panel bật, Thanh tắt, Pill tắt; Home không đổi (chỉ thanh trạng thái/điều hướng khác). Lần sau giữ cấu hình đã lưu: đọc code (`!glassCustomSaved`), chưa kiểm tay | Đạt (đo, một mức/một máy); phần "giữ cấu hình" (code) |
| 5 | Cảnh báo API < 33 | Live 4 qua Cài đặt: hộp thoại hiện khi bật từng công tắc trong cả bốn, Hủy lật công tắc về tắt, Vẫn bật bật được, không crash; Oppo không có cảnh báo (đo). Ép cao không làm mất cảnh báo: đọc code | Đạt (đo) |
| 6 | Luật kính thật (TL1/AN 9g) | G1 đứng yên 0 khung ở ALL (không vòng `invalidate()`); G2 vuốt ALL = B0 (Janky 1.33 so 1.38, p95 15.08 so 15.02, > 33 ms 2.0 so 2.0); pill/ô nhập/thanh Library qua `BackgroundGlass`/`captureReal` luôn chặn `invalidate()` | Đạt (đo, Oppo) |
| 7 | Không bề mặt kính giữ trạng thái cũ | Oppo: Custom Panel tắt/Thanh bật/Pill bật → pill và ô nhập kính thật đúng khung, panel mờ; Library bar đúng sau 6 lần mở lại. Live 4: gieo từ Tự động ra bốn công tắc tắt. Số lần dựng lại đúng một lần: không đếm được trên Oppo (`am_create_activity` = 0); F2 chưa chạy | Một phần (đo); F2 và đếm tạo lại để bài kiểm cuối |
| 8 | Hợp với `notifyBackdropChanged` | Đọc code: `backdropListener` = `apply()` + `captureOnce()`/`captureReal()`. Đổi ảnh nền khi bật kính thật: **chưa kiểm** (để bài kiểm cuối của chủ dự án) | Chỉ code, chưa kiểm tay |
| 9 | Pill giữ hành vi | Oppo: pill thật đúng khung, bấm thanh mở tìm kiếm. Chấm trang trên pill thật, nhấn giữ, TalkBack, ảnh so sánh ba mức cũ: **chưa kiểm** | Một phần |
| 10 | `GlassBudget` | `maxRealGlass` đã xóa, KDoc ghi số đo Oppo bật hết (G4 4 kính cùng hiện) | Đạt (đo + code) |
| 11 | Không sửa file ngoài Vùng file | `git diff --name-only origin/main...HEAD` không chứa `HomeActivity`, `HomeViewModel`, `HomeUiState`, `activity_home.xml`, `AndroidManifest.xml`, `ui/search/*`, `IconGridView`, `HomePagerAdapter`, `LayoutReconciler`, `DragHelper` | Đạt |
| 12 | Cài đặt | Oppo: khối công tắc hiện ở mức Tùy chỉnh, bật/tắt hoạt động. Live 4: khối công tắc và hộp thoại cảnh báo hoạt động. Xoay màn khi hộp thoại mở, ghi lỗi: **chưa kiểm** | Một phần |
| 13 | Không móc debug | `grep` `GsDebug`/log `GS` trong `app/src` rỗng (kết quả khớp `settings_glass_*` là tên chuỗi cũ) | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: TT#20 thêm mức kính "Tùy chỉnh" (công tắc kính thật/mờ cho dock, panel, thanh tìm kiếm, pill; cảnh báo API < 33); TT#6 ghi `GlassPolicy` nhận công tắc người dùng.
- `project_structure.md`: mục 5 cập nhật `GlassPolicy.modeFor` (thêm `custom`), `GlassRole.DOCK`, `GlassConfig`, `GlassLevelOverride.CUSTOM`, xóa dòng "GlassBudget chưa ai dùng"; bảng khóa DataStore thêm `glass_custom_dock`, `glass_custom_panels`, `glass_custom_search_bar`, `glass_custom_pill`; bảng mốc thêm GS.
- `room-schema.md`: không đổi.
- `MultiAgent-Workflow.md`: cập nhật bảng trạng thái (GS) và trả khóa Oppo khi merge.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement. Đề xuất thêm: gỡ `HomeViewModel.currentGlassLevelOverride()` (không còn nơi gọi sau GS; file của G2c); cập nhật dòng "`GlassBudget` ... không code nào gọi" trong mục M6/GL (hàm đã xóa).
