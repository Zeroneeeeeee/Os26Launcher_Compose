# SM — Menu nhấn giữ icon theo mẫu giao diện launcher mẫu: kính, chữ nhỏ hơn, kích thước và vị trí (TT#7) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`: code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Dự án **không viết test tự động** (AGENTS.md, chốt 2026-10-16). **Agent thực thi không dùng máy thật**: cài APK, chụp màn hình, `uiautomator dump`, `gfxinfo` đều do điều phối chạy. Máy kiểm: **chỉ Oppo A3x**; Vsmart Live 4 đang bận dự án khác, bước nào ghi Live 4 thì **chỉ khi chủ dự án cho dùng Live 4**. Lệnh viết cho PowerShell.

**Nhánh:** `feature/sm-shortcut-menu-glass` · **Worktree:** `E:\SourceTree\IosLauncher-wt\SM-shortcut-menu-glass` · **PR:** chưa mở · **Bắt đầu:** 2026-11-09 · **Plan duyệt:** 2026-11-09 (chủ dự án duyệt theo đề xuất, trừ điểm 6 và 7 đã đổi)
**Trạng thái:** Xong mã và đo Oppo; chờ chủ dự án xem lần cuối (cảm giác hoạt ảnh, công tắc khi có ảnh nền)
**Khóa:** không khóa schema DB (thêm một khóa DataStore boolean, mặc định tắt = kính mờ) · cần Oppo (kiểm kính, hoạt ảnh) · giữ file độc quyền `core/glass/*` (`GlassRole.kt`, `GlassStyle.kt`, `GlassPolicy.kt`, `GlassEnvironment.kt`)

**Goal:** Làm lại menu nhấn giữ icon theo **mẫu giao diện** của launcher mẫu `com.liquid.os.launcher.smart` (kiểu Launcher3) trên Oppo: bảng kính mờ bo góc lớn, hẹp và thấp hơn, chữ nhỏ hơn, mọc ngay dưới icon (thiếu chỗ thì lật lên trên), mép menu thẳng mép icon theo nửa màn hình icon đang đứng, **có hoạt ảnh mở/đóng** (co từ phía icon + mờ) và **công tắc kính riêng** trong khối Tùy chỉnh của Cài đặt. **Nội dung hàng giữ của ta** (shortcut của app nếu có, Thông tin ứng dụng, Ẩn ứng dụng, Sửa màn hình chính, Gỡ cài đặt).
**Architecture:** Không đổi tầng `domain`/`data`, không đổi `HomeUiState`/`HomeViewModel`/`HomeActivity`/`activity_home.xml`. Kính dùng lại hệ thống GL/GS: thêm `GlassRole.ICON_MENU` (`STATIC_BLUR` ở mọi mức kính; riêng mức Tùy chỉnh đi qua công tắc riêng `GlassElement.ICON_MENU`: bật = `STATIC_BLUR`, tắt = `FLAT`, Task 4), màu riêng trong `GlassStyle` lấy từ resource có bản `values-night`, gắn bằng `BackgroundGlass` như panel thư mục. Luật đặt vị trí là một hàm thuần mới `IconMenuPlacement` (không import `android.*`), `IconMenuController` gọi nó; `IconPress` mang thêm khung icon và đáy nhãn để đặt chính xác.
**Tech Stack:** Kotlin, View/XML. Không thêm dependency. Icon đầu hàng (Task 3) là vector XML lấy path từ Material Symbols (Apache 2.0).
**Spec:** TT#7 trong `Launcher-Features.md` (menu nhấn giữ, xong ở M7 Task 10) + yêu cầu chủ dự án 2026-11-09 (bảng hàng đợi ở `MultiAgent-Workflow.md` mục 9, dòng SM).

---

## Số đo đầu vào (điều phối đo trên Oppo A3x, 2026-11-09)

Oppo A3x 720x1604 px, density 320 = **2 px/dp**. Hai menu mở bằng nhấn giữ icon ở cùng ô lưới (hàng 3, cột 3). Nguồn: `uiautomator dump` + ảnh chụp (`sample_menu.*`, `ours_menu.*` trong scratchpad của điều phối). Agent đo lại trên ảnh (PIL) phần chữ và bo góc.

| Thông số | Mẫu (đo được) | Của ta hiện tại (đo được + đọc code) |
|---|---|---|
| Rộng bảng | 400 px = **200dp** (`deep_shortcuts_container` [98,640]-[498,1132]) | 500 px = 250dp (`icon_menu_width`) |
| Hàng | 6 hàng, mỗi hàng 70 px = **35dp**, khe giữa hai hàng 8 px = **4dp** | 4 hàng 96 px = 48dp (`icon_menu_row_height`), không khe |
| Đệm trong bảng | 16 px = **8dp** bốn phía (hàng [114..482]) | 0 (hàng sát mép), chữ thụt 16dp |
| Icon đầu hàng | 40 px = **20dp**, cách mép hàng 16 px = 8dp; chữ bắt đầu x≈187 (cách mép bảng ≈ 44dp) | Không có (chỉ hàng shortcut có bitmap 24dp) |
| Cỡ chữ | Chữ "T" cao **20 px**, "h" 21 px; "Thông tin ứng dụng" rộng 238 px | `textSize 16sp` (`view_icon_action_menu_item.xml`); "T" cao **20 px**, "h" 22 px; cùng chuỗi rộng 246 px |
| Bo góc | **≈ 48 px = 24dp** (đo đường cong góc trên-trái: lõm 18 px ở dy=10, 9 px ở dy=20, 3,5 px ở dy=30 khớp hình tròn r=48 px) | 14dp (`icon_menu_corner_radius`) |
| Khoảng cách icon → menu | Đỉnh menu 640, đáy ô icon (gồm nhãn) 621: **≈ 10dp** | 8dp tính từ đáy ô (`icon_menu_gap`) |
| Căn ngang | Icon nằm nửa phải: **mép phải menu (498) thẳng mép phải hình icon** (icon vẽ ≈ [385..501]), không thẳng mép ô (526) | Menu căn giữa theo tâm icon rồi kẹp lề 16dp (ra [188..688]) |
| Nền | Kính mờ sáng (bản mờ của ảnh nền; app mẫu báo blur trực tiếp có thể không chạy trên Android 13+ nên dùng ảnh nền đã chọn) | Phẳng tối đục `#F22C2C2E`, `elevation 8dp` |
| Nền phía sau | Tối đi, icon được nhấn vẫn sáng | `icon_menu_scrim` `#99000000`, icon nổi vẽ ở `DragLayer`: giống mẫu |
| Hàng phá hủy | "Xóa ứng dụng" màu đỏ, kèm icon trừ-trong-vòng, **hàng cuối** khi menu ở dưới icon | "Gỡ cài đặt" màu đỏ, hàng cuối |

**Kết luận về cỡ chữ (đo được):** chữ của mẫu **gần như bằng chữ của ta** (cao chữ "T" bằng nhau 20 px, rộng chuỗi kém 3%, tức ≈ 15,5sp nếu ta là 16sp; cùng phông hệ thống vì độ rộng từng chữ cái trùng nhau). Cảm giác "chữ mẫu nhỏ hơn" chủ yếu đến từ bảng hẹp hơn 50dp và hàng thấp hơn 13dp. Xem D4.

---

## Vì sao chọn thiết kế này

### Vấn đề cần giải
1. **Chủ dự án (2026-11-09):** học mẫu giao diện của launcher mẫu (kính, chữ nhỏ hơn, kích thước và vị trí như mẫu), nội dung hàng giữ của ta.
2. **Hệ thống kính đã có (đọc code):** `GlassPolicy.modeFor(tier, role, realGlassCapable, custom)` quyết `REAL`/`STATIC_BLUR`/`FLAT`; `GlassStyle.forRole` cho màu; `BackgroundGlass` gắn kính làm `background` (kính mờ tại chỗ `BackdropBlurDrawable`) hoặc thêm `RealGlassSurface` dưới target (chỉ khi host là `FrameLayout`). `core/glass/*` là file nóng độc quyền.
3. **Bài học hiệu năng (AGENTS.md, đo được):** kính thật của panel trên Oppo tốn **đúng một khung ~24 ms mỗi lần mở** và không khử được (GL Task 7, ngoại lệ D10 chủ dự án chấp nhận cho panel); `enableDynamicBackground = true` sinh vòng vẽ liên tục; kính mờ tại chỗ chỉ là một `drawRoundRect` với `BitmapShader` của bản mờ nhỏ đã dựng sẵn ngoài main thread (GL Task 8), gần như không tốn.
4. **Theme:** app dùng `Theme.Material3.DayNight` theo chế độ tối của hệ thống (đọc code: không có công tắc theme riêng trong Cài đặt; `SystemBarsController` đọc `uiMode`). Đổi chế độ làm Activity tạo lại, menu tự đóng (M7 Task 10). Hiện **không có** `values-night/colors.xml`; menu luôn tối.
5. **Người gọi (đọc code):** ba nơi phát `IconPress` cho `IconMenuController.onIconLongPress`: `IconGridView.reportLongPress` (lưới Home qua `HomePagerAdapter`/`HomePagerController`), `DockView.reportLongPress` (dock qua `DockController`), `FolderPanelView` (icon trong panel thư mục qua `FolderPanelController`). Cả ba là `IconCellView`, icon vẽ ở **đỉnh ô, căn giữa ngang**, nhãn bên dưới (`IconCellView.onDraw`, `contentHeight()`). `IconPress.bounds` hôm nay là **khung ô**, không phải khung hình icon, nên không căn được mép menu theo mép icon như mẫu.
6. **Hiện không có hoạt ảnh mở/đóng menu** (đọc code `IconMenuController.render`: scrim và menu chỉ đổi `isVisible`).

### Phương án đã xét

**(1) Kính cho menu**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **K1. Role mới `ICON_MENU`, luôn kính mờ tĩnh** | `GlassRole.ICON_MENU(element = null)` rơi vào nhánh `else -> STATIC_BLUR` có sẵn của `GlassPolicy`; `GlassStyle` thêm nhánh màu riêng (sáng/tối theo resource); `BackgroundGlass(host = menu, target = menu)` | Đúng "kính mờ từ ảnh nền" của mẫu; không tốn khung khi mở (một lệnh vẽ, bản mờ dùng chung đã có); chạy như nhau trên mọi tier, kể cả Live 4; màu riêng không đụng panel | Không khúc xạ như kính thật; công tắc "Panel" của mức Tùy chỉnh không áp cho menu; chạm `core/glass` (2 file, chỉ thêm) | **Chọn** |
| K2. Role mới theo nhánh panel | `ICON_MENU(GlassElement.PANELS)` vào nhánh `FOLDER_PANEL, GROUP_PANEL` của `GlassPolicy`: kính thật trên LENS + `realGlassCapable`, mờ tĩnh nơi khác; Tùy chỉnh theo công tắc "Panel" | Kính khúc xạ trên Oppo; người dùng tắt/bật cùng panel | **Một khung ~24 ms mỗi lần mở** (số đo GL Task 7) đúng lúc icon vừa nổi, trái yêu cầu "không tốn khung khi mở"; menu không có fade 150 ms để che như panel; `IconActionMenu` là `LinearLayout` nên phải bọc thêm `FrameLayout` host; khúc xạ ở viền làm méo chữ hàng sát mép | Không (ghi để sau) |
| K3. Dùng lại `GlassRole.FOLDER_PANEL` | `BackgroundGlass(..., GlassRole.FOLDER_PANEL, ...)` | Không chạm `core/glass` | Kéo theo kính thật của panel (nhược điểm K2) **và** màu panel tint tối cố định `gl_panel_tint`, không theo sáng/tối; ai đổi luật panel sau này đổi luôn menu mà không biết | Không |
| K4. Blur riêng bằng `RenderEffect`/`PopupWindow` | Tự làm mờ phần màn hình sau menu | Blur cả icon và chữ phía sau (giống iOS hơn) | `RenderEffect` chỉ API 31+ (Live 4 API 30), làm mờ chính view chứ không phải nền phía sau; `PopupWindow` mất chung toạ độ/z-order với scrim và `DragLayer` (bài học M7 Task 10); viết lại thứ GL đã có | Không |

**Vì sao K1:** mẫu dùng đúng kiểu "bản mờ của ảnh nền" (đo được: hộp thoại của app mẫu nói live blur có thể không chạy trên Android 13+ và dùng ảnh nền đã chọn), và đó chính là `BackdropBlurDrawable` của GL. Yêu cầu "kính không được tốn khung khi mở" loại K2 bằng số đo đã có (GL Task 7: khung trễ ~24 ms không khử được bằng bỏ lớp alpha hay giảm tải shader). Role riêng (không dùng lại `FOLDER_PANEL`) để màu sáng/tối của menu không đụng panel và luật panel sau này không lan sang menu. Vì `GlassPolicy` đã trả `STATIC_BLUR` cho mọi role ngoài dock và hai panel, **`GlassPolicy.kt` không phải sửa**; chỉ thêm một giá trị enum và một nhánh màu.

**Mức kính và Tùy chỉnh (GS):** menu là `STATIC_BLUR` ở mọi mức (Tự động, Ép thấp, Ép cao, Tùy chỉnh với mọi tổ hợp công tắc), giống ô thư mục và ô nhóm (role không có công tắc). Không có ảnh nền thì `BackdropBlurDrawable` tự vẽ màu phẳng `GlassStyle.flat` (đường có sẵn). `GlassMode.FLAT` không còn được `GlassPolicy` trả cho role nào; `BackgroundGlass` vẫn nhận `flatRes` cho đủ hợp đồng.

**(2) Kích thước và vị trí**

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| **P1. Luật popup kiểu Launcher3 (theo số đo mẫu)** | Rộng 200dp; mọc dưới icon cách đáy nhãn 10dp; icon ở nửa trái thì mép trái menu thẳng mép trái hình icon, nửa phải thì mép phải thẳng mép phải; không đủ chỗ dưới thì lật lên trên (đáy menu cách đỉnh icon 10dp) và **đảo thứ tự hàng**; kẹp trong vùng an toàn (inset hệ thống + 8dp) | Khớp số đo mẫu tới vài px (ô hàng 3 cột 3: ta tính ra [100..500], mẫu [98..498]); menu không che icon; hàm thuần dễ review | Cần khung hình icon (sửa `IconPress` và 3 nơi tạo) | **Chọn** |
| P2. Giữ luật hiện tại (căn giữa theo tâm icon, kẹp lề 16dp), chỉ đổi rộng | Một dòng | Ít sửa | Không giống mẫu: menu lệch khỏi icon, mép không thẳng; chủ dự án yêu cầu "vị trí như mẫu" | Không |
| P3. Căn theo khung ô (`IconPress.bounds` có sẵn) | Không sửa `IconPress` | Ít sửa hơn P1 | Ô rộng 80dp còn hình icon 52dp: mép menu lệch ~14dp so với mép icon (mẫu thẳng mép icon); khoảng cách dọc tính từ đáy ô (có phần thừa dưới nhãn) thành ~22dp thay vì 10dp | Không |

**Thứ tự hàng khi lật lên trên (D6):** đảo, để hàng shortcut (gần với app nhất) luôn **sát icon** và "Gỡ cài đặt" (phá hủy) luôn **xa icon nhất** ở cả hai chiều. Cơ sở: Launcher3 `ArrowPopup.reorderAndShow` đảo thứ tự con khi popup nằm trên icon (đọc mã nguồn AOSP theo trí nhớ, **chưa kiểm lại trên app mẫu**, xem Task 2 Step 0); hàng phá hủy xa điểm ngón tay vừa nhấc giảm bấm nhầm (suy luận). Nếu không đảo, khi lật lên "Gỡ cài đặt" nằm ngay trên icon, sát ngón tay.

**Chọn mép theo nửa màn hình, không theo "vừa thì căn trái" của Launcher3:** với bảng 200dp trên màn 360dp, hai luật cho cùng kết quả ở mọi cột của lưới 4 cột và dock (tính từ tọa độ đo: icon cột 2 có mép trái ≈ 110dp, 110 + 200 < 360 nên cả hai luật đều căn trái; cột 3 mép trái ≈ 198dp, 198 + 200 > 360 nên cả hai đều căn phải). Luật nửa màn hình đối xứng và dễ giải thích cho người kiểm.

**(3) Cỡ chữ và icon đầu hàng**

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| Chữ 16sp (giữ) | Bằng mẫu theo số đo | Chủ dự án yêu cầu nhỏ hơn | Không |
| **Chữ 15sp** | Nhỏ hơn hiện tại ~6%, sát mẫu (mẫu ≈ 15,5sp theo bề rộng chuỗi); đọc tốt trong hàng 35dp | Khác biệt nhỏ; cảm giác nhỏ chủ yếu nhờ bảng hẹp + hàng thấp | **Chọn** (hỏi lại chủ dự án, câu hỏi 1) |
| Chữ 14sp | Nhỏ rõ (−12,5%) | Nhỏ hơn cả mẫu theo số đo | Phương án dự phòng nếu chủ dự án muốn nhỏ rõ |
| **Có icon đầu hàng (Task 3 riêng)** | Đúng mẫu (mọi hàng có icon 20dp); hàng shortcut **đã có** icon bitmap nên menu có shortcut sẽ thẳng hàng chữ (không có icon thì chữ hàng shortcut và hàng cố định lệch nhau 28dp); chi phí 4 file vector, không dependency | Thêm 4 tài nguyên; path phải lấy đúng nguồn | **Chọn**, tách thành Task 3 để chủ dự án bỏ được khi duyệt (câu hỏi 2) |
| Không icon | Ít việc (chủ dự án từng nói "có thể chưa cần icon") | Menu có shortcut lệch chữ; khác mẫu | Không (trừ khi chủ dự án bỏ Task 3) |

**(4) Hộp chạm của hàng:** mẫu có hàng 35dp và khe 4dp. Ta làm **hộp chạm 39dp liền nhau** (hàng 35dp + 2dp mỗi phía), nền nhấn là hình bo góc thụt 2dp trên/dưới (`<inset>` trong `<ripple>`), nên **nhìn y như mẫu** nhưng không có khe chết giữa hai hàng. 39dp vẫn dưới mức khuyến nghị 48dp của Material; đánh đổi chấp nhận để giống mẫu (Launcher3 cũng dùng hàng thấp hơn 48dp).

**(5) Hoạt ảnh (đổi 2026-11-09):** menu hiện một lần đúng chỗ sau khi shortcut nạp xong (giữ), nhưng giờ có hoạt ảnh.

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| **A1. Spring (`SpringProgress`) co + mờ quanh góc gần icon** | Cùng phong cách và hạ tầng AN; đổi đích giữa chừng giữ vận tốc (đóng giữa lúc mở không giật); chi phí 0 khi đứng yên | Kính mờ tĩnh bị co phải `invalidate()` mỗi khung (menu nhỏ 200 x ~170dp, rẻ hơn panel) | **Chọn** |
| A2. `ObjectAnimator`/`ViewPropertyAnimator` với interpolator | Đơn giản, thời lượng chính xác | Khác phong cách AN; đảo chiều giữa chừng phải tự xử lý; hủy khó sạch hơn | Không |
| A3. A1 + so le hàng (stagger) | Giống iOS hơn | 4-8 spring thêm, hàng co theo bảng cha nên phải bù; "nếu rẻ" -> không rẻ | Không |
| A4. Co + mờ cả scrim | Mượt hơn | Scrim chặn chạm; để nó nấn ná thì chạm bị nuốt khi đóng | Không (scrim tức thì) |

**Điểm xoay và hướng:** điểm xoay đặt ở góc gần icon nhất (theo `above` và mép đã căn của `IconMenuPlacement`), nên lật lên thì mọc từ đáy menu lên. `BackdropBlurDrawable.drawScaled` (AN Task 2) đã vẽ đúng lát nền cho owner bị co.

### Đánh đổi chấp nhận
- Menu không có kính thật khúc xạ trên Oppo (panel có). Xét lại khi chủ dự án muốn hiệu ứng khúc xạ cho menu và chấp nhận một khung trễ mỗi lần mở (K2).
- Công tắc "Panel" của mức Tùy chỉnh không điều khiển menu; menu có công tắc riêng (D12) với nghĩa khác (kính mờ / phẳng).
- Hộp chạm 39dp < 48dp.
- Tài nguyên cũ `icon_menu_*` (màu, dimens) không sửa tại chỗ vì luật "dùng chung có kiểm soát: chỉ thêm" (MultiAgent-Workflow mục 5.2); giá trị mới ở khối `shortcut_menu_*` cuối file. Các mục cũ không còn ai dùng sẽ được liệt kê để chủ dự án duyệt dọn (không tự xóa).

---

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Thêm `GlassRole.ICON_MENU`; `GlassStyle.forRole` thêm nhánh `ICON_MENU` đọc `shortcut_menu_glass_tint/border/flat`. Task 1 để `element = null` (nhánh `else` của `GlassPolicy` trả `STATIC_BLUR`); **Task 4 đổi thành `ICON_MENU(GlassElement.ICON_MENU)`** và `GlassPolicy` thêm nhánh riêng (D12) | Claude đề xuất 2026-11-09, phương án K1. **Duyệt 2026-11-09**; `GlassPolicy` không còn "không sửa" vì D12 |
| D2 | Màu sáng/tối theo chế độ tối hệ thống bằng `values/colors.xml` + `values-night/colors.xml` (file mới), không đọc `uiMode` trong code. `GlassStyle` và `IconActionMenu` lấy màu từ context của Activity | Claude đề xuất; cơ sở: Activity tạo lại khi đổi `uiMode` (M5 Task 3), menu đóng theo (M7 Task 10) |
| D3 | Kích thước: rộng 200dp, bo 24dp, đệm bảng 8dp ngang / 6dp dọc, hàng chạm 39dp (nhìn 35dp, thụt 2dp), nền nhấn bo 16dp (= 24 − 8, đồng tâm với góc bảng), chữ một dòng, dài thì `…` | Số đo mẫu (bảng trên) |
| D4 | Chữ **15sp** | Đo: mẫu ≈ 15,5sp; chủ dự án muốn nhỏ hơn 16sp. **Duyệt 2026-11-09** |
| D5 | Vị trí theo hàm thuần `IconMenuPlacement.place(...)`: dưới icon cách đáy nhãn 10dp; mép theo nửa màn hình; thiếu chỗ thì lật lên (đáy menu cách đỉnh icon 10dp); cả hai đều thiếu thì chọn phía nhiều chỗ hơn và kẹp; kẹp ngang và dọc trong inset `systemBars | displayCutout` + 8dp | Số đo mẫu; luật Launcher3 |
| D6 | Lật lên trên thì đảo thứ tự hàng (shortcut sát icon, "Gỡ cài đặt" xa icon nhất); đường kẻ vẫn nằm giữa hai nhóm | Launcher3 (chưa kiểm trên mẫu, Task 2 Step 0) + suy luận an toàn |
| D7 | `IconPress` thêm `icon: ScreenRect` (khung hình icon) và `contentBottom: Int` (đáy nhãn, bằng đáy icon nếu không nhãn); một hàm tạo dùng chung từ `IconCellView` cho cả lưới, dock, panel thư mục. `IconMenuState.anchor` (khung ô, dùng cho phép thử chạm icon nổi trên scrim) **giữ nguyên**, hình học đặt menu giữ ở tầng view trong `IconMenuController` cạnh `iconMenuSnapshot` | Không chạm `HomeUiState`/`HomeViewModel` (file nóng); cùng vòng đời với bitmap nổi (Activity tạo lại thì menu đóng) |
| D8 | Bỏ `elevation` của menu | Kính trong mờ + bóng đổ cho viền tối lộ qua (suy luận); và `elevation 8dp` hiện làm menu vẽ **trên** `search_overlay` và `drag_layer` dù đứng trước chúng trong `activity_home.xml` (con có elevation cao hơn được vẽ sau, đọc tài liệu `ViewGroup`), trái chú thích z-order của layout. Mẫu không có bóng rõ (ảnh chụp) |
| D9 | Icon đầu hàng (Task 3): 20dp, cách mép hàng 8dp, cách chữ 8dp; 4 vector Material Symbols (Apache 2.0): `info`, `visibility_off`, `edit` (Sửa màn hình chính), `do_not_disturb_on` (vòng tròn có gạch, như mẫu); tô màu chữ, icon "Gỡ cài đặt" tô màu đỏ phá hủy. Bitmap shortcut xin ở 20dp (trước 24dp) để thẳng cột với icon cố định | **Duyệt 2026-11-09** (Task 3 giữ) |
| D10 | **ĐỔI 2026-11-09:** menu CÓ hoạt ảnh mở/đóng (D13). Giữ scrim `icon_scrim` 60% đen (hiện/ẩn tức thì, không hoạt ảnh) và icon nổi ở `DragLayer` | Chủ dự án (điểm 7): làm ngay trong SM như một Task riêng. Scrim không hoạt ảnh vì nó chặn chạm: đóng thì phải thả chạm ngay |
| D11 | Không test tự động | AGENTS.md |
| D12 | ~~Công tắc "kính mờ / phẳng", mặc định bật~~ **THAY BẰNG D15 (2026-11-09, chủ dự án)**: chủ dự án bật/tắt công tắc không thấy khác biệt (kính mờ tint 72% so với phẳng 95% gần như cùng một bảng sáng) và hỏi "có phải kính thật không" | Lịch sử: Chủ dự án 2026-11-09 (điểm 6), bản đầu |
| D15 | **Công tắc = kính thật / kính mờ như GS (Task 4, sửa 2026-11-09).** `GlassElement.ICON_MENU` (khóa `glass_custom_icon_menu`), `GlassCustom.iconMenu`, **cùng nghĩa bốn công tắc cũ: bật = kính thật (`RealGlassSurface`) nơi máy đủ khả năng, tắt = kính mờ tĩnh**; không còn lựa chọn phẳng ở mức Tùy chỉnh. Ngoài Tùy chỉnh menu đi **cùng nhánh hai panel** của `GlassPolicy` (kính thật khi tier LENS và `realGlassCapable`, kính mờ nơi khác) = phương án K2. **Mặc định:** `seedFor` gieo `iconMenu = real(ICON_MENU)` (đúng thứ đang hiện dưới mức vừa rời, nên vào Tùy chỉnh không đổi màn hình, như panel); khóa vắng đọc thành TẮT (kính mờ) và **không** tính vào `glassCustomSaved`: người dùng đã ở Tùy chỉnh trước bản này giữ menu kính mờ như hành vi trước, không bị gieo lại. Bật công tắc lúc máy không có đường kính thật (API < 33) hỏi cảnh báo như các công tắc kia. Tiêu đề riêng "Menu nhấn giữ icon (bật = kính thật, tắt = kính mờ)". `FLAT` không còn do `GlassPolicy` trả cho menu (chỉ còn là đường dự phòng của `BackgroundGlass`: chưa có ảnh nền thì kính vẽ màu phẳng `shortcut_menu_flat`) | Chủ dự án 2026-11-09: "công tắc = giao cho người dùng chọn" (phương án 2). Đánh đổi đã chấp nhận: một khung ~24 ms mỗi lần mở khi dùng kính thật (GL Task 7), vì là tùy chọn của người dùng; đảo ngược quyết định K1/D12 cũ |
| D16 | **Kính mờ thật sự mờ.** Tint sáng `shortcut_menu_glass_tint` hạ từ `#B8F5F5F7` (72%) xuống `#8CF5F5F7` (**55%**): màu ảnh nền lộ rõ như mẫu. Tối giữ `#8C1C1C1E` (đã 55%). Chữ: nền sáng 55% trên ảnh nền đen tuyền cho nền ~ xám 135 (độ sáng ~0,24), chữ `#1C1C1E` (~0,012) có tương phản ~4,7:1, đủ; trên ảnh sáng nền càng sáng nên càng rõ. Tối 55% trên ảnh nền trắng cho nền ~ xám 130, chữ trắng ~3,9:1 (như trước, không tệ hơn); nếu điều phối thấy khó đọc thì tăng tint tối, không hạ thêm tint sáng | Claude chọn theo yêu cầu điều phối 45-55%; số chỉnh theo mắt trên Oppo khi có ảnh nền |
| D17 | **Kính thật + hoạt ảnh.** `IconActionMenu` là `FrameLayout` (host) chứa `rows` (target của kính); co/dịch/mờ áp lên `rows` và kính thật bằng một `BackgroundGlass.applyMotion` (như `PanelMotion`), kính thật không chụp lại theo khung: `setCaptureAtLayoutPosition(true)` (AN Task 9g) và `invalidate()` bị chặn, chụp MỘT lần sau khi hiện (đường tự chụp của `BackgroundGlass` khi host từ ẩn sang hiện). Co quanh góc gần icon P bằng scale quanh tâm + dịch (P - tâm)(1 - s) vì `applyMotion` không đặt pivot của kính thật. Hủy sạch như D14 (kính thật nằm dưới `rows`, `closing`/snap/cancel không đổi) | Claude, theo yêu cầu điều phối 2026-11-09 |
| D13 | **Hoạt ảnh mở/đóng (Task 5).** Một `SpringProgress` `p` (0 = đóng, 1 = mở) trong `IconMenuMotion`: menu co từ `START_SCALE` = 0,6 lên 1 quanh điểm xoay ở **góc gần icon nhất** (trên-trái/trên-phải khi menu dưới icon, dưới-trái/dưới-phải khi lật lên; trái/phải theo mép đã căn) và alpha từ 0 lên 1 (đầy ở p = 0,4). Mở: spring cứng 700, giảm chấn 0,9 (~220 ms); đóng: cứng 1400, giảm chấn 1,0 (~150 ms). Chỉ đổi `scaleX/scaleY/alpha` của view menu (qua `BackgroundGlass.applyMotion`, có `invalidate()` mỗi khung vì kính mờ tĩnh bị co phải vẽ lại, AN Task 2); menu không có kính thật nên không chụp lại gì; không đổi layout. Không so le hàng (stagger): không rẻ vì hàng co theo bảng cha và thêm 4-8 spring; bỏ | Chủ dự án (điểm 7). Cơ sở: AN dùng spring + transform chứ không `ValueAnimator`; các số là điểm xuất phát chỉnh tay, điều phối chỉnh theo mắt trên Oppo |
| D14 | **Hủy sạch.** Đóng bằng chạm nền/Back/bấm hàng: chạy đóng ~150 ms, trong lúc đó menu **nuốt chạm** (không kích hoạt hàng lần hai, không để chạm lọt xuống). Mở lại giữa lúc đóng: đặt lại về 0 rồi mở. Kéo bắt đầu từ menu (đường A: `startDragFromMenu`; đường B: menu đóng vì kéo, `DragHelper.currentSession != null`): **snap về 0 ngay**, không hoạt ảnh. `onDetachedFromWindow` của menu hủy spring | An toàn vòng đời (AN Task 1: mọi `SpringProgress` được `cancel()` ở detach) |

## Bảng chính sách kính của menu (D15, bản cuối)
`GlassPolicy.modeFor(tier, ICON_MENU, realGlassCapable, custom)`:

| Mức kính | Tier | Công tắc menu | Chế độ |
|---|---|---|---|
| Tự động / Ép thấp / Ép cao | LENS và `realGlassCapable` | (không áp) | `REAL` |
| Tự động / Ép thấp / Ép cao | LENS nhưng máy API < 33, hoặc CLASSIC_GPU, CLASSIC_CPU | (không áp) | `STATIC_BLUR` |
| Tùy chỉnh | mọi tier | bật | `REAL` (không hỏi tier; cảnh báo ở Cài đặt nếu API < 33) |
| Tùy chỉnh | mọi tier | tắt | `STATIC_BLUR` |

`REAL` còn cần host là `FrameLayout` (đã đúng) và có ảnh nền; không có ảnh nền thì `BackgroundGlass` dùng kính tại chỗ vẽ màu phẳng `shortcut_menu_flat`. `GlassPolicy` không trả `FLAT` cho menu.

## Phê duyệt của chủ dự án (2026-11-09)
Duyệt theo đề xuất, **trừ hai điểm đổi**:
1. Cỡ chữ: **15sp**. 2. Icon đầu hàng: **có** (Task 3 giữ). 3. Kính mờ tĩnh, không kính thật. 5. Sáng/tối theo chế độ tối của hệ thống.
4. (không dùng nữa: hoạt ảnh xem điểm 7).
6. **ĐỔI:** menu có **công tắc kính riêng, tách bạch** trong khối Tùy chỉnh, để người dùng bật/tắt kính cho menu. "Nếu phức tạp thì làm thành một task riêng" -> Task 4.
7. **ĐỔI:** menu **phải có hoạt ảnh mở/đóng** kiểu Launcher3/iOS (co từ phía icon + mờ, mở ~200-250 ms, đóng ~150 ms, theo phong cách AN, không chụp lại kính mỗi khung, hủy sạch khi đóng giữa chừng hoặc khi kéo bắt đầu từ menu) -> Task 5.

## Ghi chú đổi 2026-11-09 (chiều)
Task 4 ở dưới viết theo D12 (kính mờ / phẳng, mặc định bật); **D15-D17 thay thế các bước đó**: Step 1 (`iconMenu` mặc định `false`), Step 2 (khóa vắng = tắt), Step 3 (`GlassPolicy` chỉ thêm `ICON_MENU` vào nhánh hai panel, không còn nhánh `FLAT`; `seedFor` gieo `real(ICON_MENU)`), Step 4 (tiêu đề "bật = kính thật, tắt = kính mờ"; hỏi cảnh báo API < 33). Task 5 thêm: `IconActionMenu` thành `FrameLayout` + `rows`, co bằng scale + dịch (D17). Bằng chứng Oppo của dòng 3, 4, 10, 11 ở dưới đo trên bản D12 cũ nên cần đo lại.

## Ngoài phạm vi / ghi để sau
- Kính thật (khúc xạ) cho menu (K2).
- Làm mờ thật cả màn hình phía sau menu thay cho scrim tối (TT#7 ghi "chưa blur thật"); đổi độ tối scrim.
- Thêm hàng mới kiểu mẫu ("Chọn", "Giao diện ứng dụng"): nội dung hàng giữ của ta.
- Hỗ trợ RTL cho luật mép (app chỉ tiếng Việt, LTR).
- Dọn các tài nguyên `icon_menu_*` cũ không còn dùng (liệt kê ở Task 6, chờ duyệt).
- Hoạt ảnh scrim, so le hàng khi mở (stagger), icon nổi nhích lên khi menu mở.

---

## Bảng thông số trước / sau

| Thông số | Trước (code hiện tại) | Sau (SM) | Mẫu (đo) |
|---|---|---|---|
| Rộng bảng | 250dp | **200dp** | 200dp |
| Bo góc bảng | 14dp | **24dp** | ≈ 24dp |
| Nền | Phẳng `#F22C2C2E` (tối, mọi chế độ) | **Kính mờ tĩnh** (bản mờ ảnh nền + tint); sáng: tint `#B8F5F5F7`, tối: tint `#8C1C1C1E`; viền 1dp sáng: `#66FFFFFF` / tối: `#33FFFFFF`; không ảnh nền: phẳng `#F2F2F2F7` / `#F22C2C2E` (điểm xuất phát, chỉnh theo mắt trên Oppo) | Kính mờ sáng |
| Bóng | `elevation 8dp` | **Không** | Không thấy rõ |
| Đệm bảng | 0 | **8dp ngang, 6dp dọc** (+ 2dp thụt của hàng = 8dp nhìn thấy) | 8dp |
| Hàng | 48dp, không khe | **Chạm 39dp liền nhau; nhìn 35dp, khe 4dp** | 35dp + khe 4dp |
| Nền nhấn của hàng | `selectableItemBackground` chữ nhật | **Ripple bo 16dp, thụt 2dp trên/dưới** | Bo (Launcher3) |
| Chữ | 16sp, trắng / đỏ `#FF453A`, xuống dòng được | **15sp**, một dòng, `…`; sáng: `#FF1C1C1E` / đỏ `#FFFF3B30`; tối: trắng / đỏ `#FFFF453A` | ≈ 15,5sp, một dòng `…` |
| Thụt chữ | 16dp từ mép bảng | Có icon: **44dp** (8 + 8 + 20 + 8); không icon: 16dp (8 + 8) | ≈ 44dp |
| Icon đầu hàng | Chỉ hàng shortcut, bitmap 24dp, cách chữ 12dp | **20dp, cách mép hàng 8dp, cách chữ 8dp**, mọi hàng (Task 3) | 20dp |
| Đường kẻ shortcut / cố định | 1dp sát mép, `#33FFFFFF` | 1dp, thụt 8dp hai bên, cách trên/dưới 2dp; sáng `#1F000000` / tối `#33FFFFFF` | (mẫu không có shortcut) |
| Khoảng icon → menu | 8dp từ đáy **ô** | **10dp từ đáy nhãn** (dưới) / từ đỉnh **icon** (trên) | ≈ 10dp |
| Căn ngang | Giữa theo tâm icon, kẹp lề 16dp | **Mép thẳng mép hình icon** theo nửa màn hình; kẹp inset + 8dp | Thẳng mép icon |
| Lật lên trên | Có (khi không đủ chỗ dưới), thứ tự giữ nguyên | Có, **đảo thứ tự hàng** | Launcher3 đảo (chưa kiểm) |
| Chiều cao 4 hàng cố định | 192dp | 6 + 4 × 39 + 6 = **168dp** | 6 hàng: 246dp |
| Hoạt ảnh | Không | **Co 0,6 -> 1 + mờ từ góc gần icon; mở ~220 ms, đóng ~150 ms** (D13) | Có (không đo) |
| Công tắc kính | Không | **Có** trong Tùy chỉnh: bật = kính mờ, tắt = phẳng; mặc định bật (D12) | (không có) |

---

## Vùng file

| File | Tạo / Sửa | Nóng | Task |
|---|---|---|---|
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassRole.kt` | Sửa: thêm `ICON_MENU(null)` + KDoc; Task 4 đổi thành `ICON_MENU(GlassElement.ICON_MENU)` | **Độc quyền** (`core/glass/*`) | 1, 4 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassPolicy.kt` | Sửa: nhánh riêng cho `ICON_MENU` ở mức Tùy chỉnh (bật = `STATIC_BLUR`, tắt = `FLAT`) | **Độc quyền** (`core/glass/*`) | 4 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassEnvironment.kt` | Sửa: `seedFor` gieo `iconMenu = true` | **Độc quyền** (`core/glass/*`) | 4 |
| `app/src/main/java/gambi/global/os26launcher/core/glass/GlassStyle.kt` | Sửa: thêm nhánh `ICON_MENU` | **Độc quyền** (`core/glass/*`) | 1 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconActionMenu.kt` | Sửa: kính, đệm, bỏ elevation, màu mới, đường kẻ, `bind(..., reversed)`, icon đầu hàng, `applyMotion`, nuốt chạm khi đóng, hủy spring ở detach | Không | 1, 2, 3, 5 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconMenuMotion.kt` | **Tạo**: spring mở/đóng, điểm xoay, hằng số | Không | 5 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/IconMenuController.kt` | Sửa: rộng mới, cỡ icon shortcut mới, giữ hình học của `IconPress`, gọi `IconMenuPlacement`, chạy mở/đóng, snap khi kéo từ menu | Không | 1, 2, 5 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/controller/IconMenuPlacement.kt` | **Tạo**: hàm thuần đặt vị trí | Không | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconPress.kt` | Sửa: thêm `icon`, `contentBottom`, hàm tạo từ `IconCellView` | Không | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconCellView.kt` | Sửa: lộ cỡ icon (getter chỉ đọc) | Không (nhiều nhánh từng sửa, đổi tối thiểu) | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/IconGridView.kt` | Sửa: `reportLongPress` dùng hàm tạo chung | Không | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt` | Sửa: như trên | Không | 2 |
| `app/src/main/java/gambi/global/os26launcher/ui/home/view/FolderPanelView.kt` | Sửa: như trên | Không | 2 |
| `app/src/main/res/layout/view_icon_action_menu_item.xml` | Sửa: chiều cao, đệm, 15sp, một dòng, nền hàng | Không | 1 |
| `app/src/main/res/drawable/bg_icon_action_menu.xml` | Sửa: màu phẳng mới + bo 24dp (chỉ menu dùng) | Không | 1 |
| `app/src/main/res/drawable/bg_shortcut_menu_row.xml` | **Tạo**: ripple bo 16dp thụt 2dp | Không | 1 |
| `app/src/main/res/drawable/ic_shortcut_menu_info.xml`, `ic_shortcut_menu_hide.xml`, `ic_shortcut_menu_edit_home.xml`, `ic_shortcut_menu_uninstall.xml` | **Tạo**: vector 20dp | Không | 3 |
| `app/src/main/res/values/colors.xml` | Sửa: **thêm** khối `shortcut_menu_*` cuối file | Dùng chung có kiểm soát | 1 |
| `app/src/main/res/values-night/colors.xml` | **Tạo**: bản tối của khối `shortcut_menu_*` | Mới (nhánh sau thêm màu tối vào đây thì coi như dùng chung có kiểm soát) | 1 |
| `app/src/main/res/values/dimens.xml` | Sửa: **thêm** khối `shortcut_menu_*` cuối file | Dùng chung có kiểm soát | 1, 2 |
| `app/src/main/res/values/strings.xml` | Sửa: **thêm** 2 chuỗi `gs_custom_menu_header`, `gs_custom_icon_menu` (khối riêng cuối file) | Dùng chung có kiểm soát | 4 |
| `app/src/main/java/gambi/global/os26launcher/domain/model/GlassCustom.kt` | Sửa: `GlassElement.ICON_MENU`, `GlassCustom.iconMenu` (mặc định `true`) | Không | 4 |
| `app/src/main/java/gambi/global/os26launcher/data/settings/SettingsKeys.kt`, `SettingsRepositoryImpl.kt` | Sửa: khóa `glass_custom_icon_menu`, đọc (mặc định `true`) và `keyOf` | Không | 4 |
| `app/src/main/java/gambi/global/os26launcher/ui/settings/SettingsActivity.kt`, `SettingsRow.kt` | Sửa: hàng công tắc + tiêu đề riêng, `glassElementOf`, bỏ cảnh báo API < 33 cho hàng này | Không | 4 |
| `docs/plans/SM-shortcut-menu-glass.md`, `docs/knowledgements/SM-shortcut-menu-glass.md` | Tạo / cập nhật | Không | mọi Task |

**Không sửa:** `HomeActivity.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `activity_home.xml` (rộng 200dp đặt bằng `LayoutParams.width` trong `IconMenuController`; `layout_width="@dimen/icon_menu_width"` trong XML chỉ còn là giá trị trước lần đặt đầu tiên, ghi vào đề xuất dọn), `BackgroundGlass.kt`, `BackdropBlurDrawable.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`, `core/ui/SpringProgress.kt` (spec mở/đóng khai trong `IconMenuMotion`).
**Chồng lấn với nhánh đang chạy (2026-11-09):** DD chỉ sửa `data/apps/DefaultAppResolver.kt`, `domain` `DefaultAppResolverPort`/`PickDefaultDockUseCase`; RB (build, keep rules). Không nhánh nào chạm `core/glass/*`, `ui/settings`, `data/settings` hay `domain/model/GlassCustom.kt`.

## Global Constraints
- Không test tự động, không dependency mới, không đổi schema.
- Không sửa file ngoài Vùng file; cần thêm thì dừng và báo.
- Tài nguyên dùng chung: **chỉ thêm** khối liền `shortcut_menu_*` ở cuối `colors.xml`/`dimens.xml`; không đổi giá trị `icon_menu_*` cũ.
- Hoạt ảnh: chỉ đổi `scaleX/scaleY/alpha` của view menu bằng `SpringProgress`; không `requestLayout`, không đổi margin theo khung; mọi spring `cancel()` ở `onDetachedFromWindow`; menu đang đóng nuốt chạm.
- Kính của menu (SM, D15): như panel, `GlassPolicy` quyết `REAL` hoặc `STATIC_BLUR`; kính thật chỉ qua `BackgroundGlass` (`RealGlassSurface` con đầu của host, **không** `setDynamic(true)`, chặn `invalidate()`, chụp một lần), kính mờ qua `BackdropBlurDrawable`; không cấp phát trong `draw`. `BackgroundGlass.attach()` ở `onAttachedToWindow`, `detach()` ở `onDetachedFromWindow` của `IconActionMenu` (cân bằng listener, không rò).
- Màu menu lấy từ resource qua context của view (Activity), không hardcode trong Kotlin, không đọc `uiMode` bằng tay.
- `IconMenuPlacement` là hàm thuần (không `android.*`), nhận mọi kích thước bằng px.
- Giữ nguyên mọi hành vi M7 Task 10: menu chờ shortcut nạp xong mới hiện và đặt chỗ **một lần**; chạm scrim đóng; Back đóng; nhấn giữ icon nổi → edit mode, di tay vượt slop → kéo luôn (`pendingMenuDrag`, phép thử chạm dùng `IconMenuState.anchor` = khung ô); trong panel thư mục không có "Sửa màn hình chính"; Activity tạo lại thì menu đóng; đường B (kéo bắt đầu khi menu mở) không tắt `DragLayer`.
- Chữ hàng một dòng, `ellipsize="end"`, cỡ theo `sp` (tôn trọng cỡ chữ hệ thống); hàng `minHeight` 39dp, cao lên được nếu chữ cỡ lớn.
- Commit message tiếng Anh, không dấu tiếng Việt; build `gradlew.bat assembleDebug` xanh trước khi báo xong Task.
- Agent không cài APK, không dùng `adb`. Điều phối chạy mọi bước trên Oppo; Live 4 chỉ khi chủ dự án cho.

## Review Focus
1. **Luật đặt vị trí** (`IconMenuPlacement`): căn mép theo nửa màn hình; dưới trước, lật lên khi thiếu chỗ; cả hai thiếu thì phía nhiều chỗ hơn + kẹp; kẹp ngang/dọc theo inset + 8dp; dùng khung **hình icon** và **đáy nhãn**, không phải khung ô. Ca: 4 góc lưới, cột 2 so cột 3, hàng cuối lưới, dock (luôn lên trên), panel thư mục, menu 8 hàng (4 shortcut).
2. **Đảo thứ tự khi lật:** shortcut sát icon, "Gỡ cài đặt" xa icon nhất ở cả hai chiều; đường kẻ vẫn ở giữa hai nhóm; chiều cao không đổi khi đảo (đo một lần rồi mới quyết phía).
3. **Kính (D15):** `GlassEnvironment.modeFor(ICON_MENU)` đúng bảng chính sách cuối (xem D15): Tự động/Ép thấp/Ép cao theo tier như panel, Tùy chỉnh theo công tắc riêng (bật `REAL`, tắt `STATIC_BLUR`), không phụ thuộc bốn công tắc kia; kính thật không chụp lại theo khung trong lúc co; listener của `BackgroundGlass` gỡ khi detach; không có ảnh nền thì kính vẽ phẳng đúng màu chế độ.
4. **Sáng/tối:** mọi màu menu (tint, viền, phẳng, chữ, đỏ, đường kẻ, nhấn, icon) có bản `values-night`; `GlassStyle.forRole` dùng context của Activity.
5. **Z-order sau khi bỏ elevation:** menu trên scrim và panel thư mục, dưới `search_overlay` và `drag_layer` (đúng chú thích `activity_home.xml`); icon nổi vẫn sáng trên scrim.
6. **Không hồi quy M7 Task 10:** `pendingMenuDrag`, Ẩn ứng dụng trong thư mục, Gỡ cài đặt (`REQUEST_DELETE_PACKAGES`), bấm shortcut mở đúng, menu đóng khi kéo (đường B).
7. **Hộp chạm:** cả bề ngang hàng và cả khe giữa hai hàng đều bấm được (39dp liền nhau), nền nhấn không tràn qua góc bảng.
8. **Hiệu năng:** mở/đóng menu không thêm khung chậm so với `main` (đo `gfxinfo` trên Oppo, Task 4).
9. **Tài nguyên:** chỉ thêm khối cuối file; liệt kê `icon_menu_*` không còn ai dùng.
10. **Công tắc (Task 4, D15):** theo mẫu GS (khóa, `GlassCustom`, `keyOf`, hàng Settings, `glassElementOf`); `seedFor` gieo `real(ICON_MENU)`; khóa vắng = tắt (kính mờ, hành vi cũ) và không làm `glassCustomSaved` thành true; hỏi cảnh báo API < 33 như các hàng khác; công tắc đổi thì menu đổi nền qua listener; các `when` trên `GlassElement` vẫn đầy đủ; nhãn nói "bật = kính thật, tắt = kính mờ".
11. **Hoạt ảnh (Task 5, D17):** góc co đúng ở cả bốn ca (công thức dịch (P - tâm)(1 - s)); kính thật và bảng co cùng một phép, không chụp lại theo khung (không có khung > 33 ms thêm ngoài khung mở); đóng giữa lúc mở và mở lại giữa lúc đóng không giật, không kẹt hiện (`isVisible` về false khi spring dừng ở 0); kéo từ menu snap về 0; chạm trong lúc đóng bị nuốt; `cancel()` ở detach; không `requestLayout` mỗi khung; animator scale = 0 thì tức thì nhưng vẫn đúng.

---

### Task 1: Kính, kích thước, chữ (bảng menu)
**Bắt buộc kiểm tay:** không. Không thuộc loại nào ở mục 4.2: kính mờ tĩnh đã đo ở GL (không cần phép đo để chọn phương án), không có cử chỉ mới. Nhìn bằng mắt gộp vào bài kiểm cuối.
**Files:** `GlassRole.kt`, `GlassStyle.kt`, `IconActionMenu.kt`, `IconMenuController.kt` (chỉ rộng và cỡ icon shortcut), `view_icon_action_menu_item.xml`, `bg_icon_action_menu.xml`, `bg_shortcut_menu_row.xml`, `values/colors.xml`, `values-night/colors.xml`, `values/dimens.xml`.
**Interfaces:** Consumes `BackgroundGlass(host, target, role, flatRes, cornerRadiusPx)`, `GlassEnvironment.modeFor`. Produces `GlassRole.ICON_MENU`, `GlassStyle.forRole(ctx, ICON_MENU)`.

- [ ] **Step 1: Role và màu kính.** `GlassRole` thêm `ICON_MENU(null)` với KDoc: "menu nhấn giữ icon (SM); không có công tắc Tùy chỉnh; luôn `STATIC_BLUR` qua nhánh `else` của `GlassPolicy` (kính thật tốn một khung khi mở, GL Task 7)". `GlassStyle.forRole` thêm nhánh `GlassRole.ICON_MENU -> GlassStyle(tint = shortcut_menu_glass_tint, border = shortcut_menu_glass_border, flat = shortcut_menu_flat)`. Rà `when (role)` khác trong dự án (đọc code 2026-11-09: chỉ `GlassPolicy` và `GlassStyle`, cả hai có `else`).
- [ ] **Step 2: Tài nguyên.** Khối `<!-- SM: menu nhấn giữ icon (kính, theo sáng/tối) -->` cuối `values/colors.xml`: `shortcut_menu_glass_tint` `#B8F5F5F7`, `shortcut_menu_glass_border` `#66FFFFFF`, `shortcut_menu_flat` `#F2F2F2F7`, `shortcut_menu_text` `#FF1C1C1E`, `shortcut_menu_destructive` `#FFFF3B30`, `shortcut_menu_divider` `#1F000000`, `shortcut_menu_row_pressed` `#1A000000`. Tạo `values-night/colors.xml` cùng tên: `#8C1C1C1E`, `#33FFFFFF`, `#F22C2C2E`, `#FFFFFFFF`, `#FFFF453A`, `#33FFFFFF`, `#26FFFFFF`. Khối cuối `dimens.xml`: `shortcut_menu_width` 200dp, `shortcut_menu_radius` 24dp, `shortcut_menu_padding_horizontal` 8dp, `shortcut_menu_padding_vertical` 6dp, `shortcut_menu_row_height` 39dp, `shortcut_menu_row_inset` 2dp, `shortcut_menu_row_radius` 16dp, `shortcut_menu_row_padding_start` 8dp, `shortcut_menu_row_padding_end` 8dp, `shortcut_menu_text_size` 15sp, `shortcut_menu_icon_size` 20dp, `shortcut_menu_icon_padding` 8dp, `shortcut_menu_divider` 1dp, `shortcut_menu_divider_inset` 8dp, `shortcut_menu_divider_margin` 2dp (gap/margin của Task 2 thêm cùng khối). Ghi chú trong XML: màu là điểm xuất phát, chỉnh theo mắt trên Oppo.
- [ ] **Step 3: Drawable.** `bg_icon_action_menu.xml` dùng `@color/shortcut_menu_flat` và `@dimen/shortcut_menu_radius` (chỉ menu dùng file này; đây là `flatRes` của `BackgroundGlass`). Tạo `bg_shortcut_menu_row.xml`: `<ripple android:color="@color/shortcut_menu_row_pressed">` với mask là `<inset insetTop/insetBottom = @dimen/shortcut_menu_row_inset>` bọc `<shape>` bo `@dimen/shortcut_menu_row_radius` (màu đặc bất kỳ, chỉ làm mask).
- [ ] **Step 4: Hàng.** `view_icon_action_menu_item.xml`: `minHeight` 39dp, `paddingStart/End` 8dp, `textSize @dimen/shortcut_menu_text_size`, `singleLine="true"` (hoặc `maxLines=1`), `ellipsize="end"`, `background @drawable/bg_shortcut_menu_row`, `gravity center_vertical|start`, `drawablePadding @dimen/shortcut_menu_icon_padding`. Vẫn là `TextView` gốc (code đang ép kiểu `as TextView`).
- [ ] **Step 5: `IconActionMenu`.** Bỏ `elevation` (D8). `setPadding(8dp, 6dp, 8dp, 6dp)` từ dimens. Thêm `private val glass = BackgroundGlass(this, this, GlassRole.ICON_MENU, R.drawable.bg_icon_action_menu, radiusPx)`, `attach()` trong `onAttachedToWindow`, `detach()` trong `onDetachedFromWindow`. Màu chữ `shortcut_menu_text`/`shortcut_menu_destructive`. Đường kẻ: màu `shortcut_menu_divider`, cao 1dp, `marginStart/End` 8dp, `marginTop/Bottom` 2dp. Bitmap shortcut đặt bounds theo `shortcut_menu_icon_size`, `compoundDrawablePadding` theo `shortcut_menu_icon_padding`. Cập nhật KDoc đầu lớp (kính, SM).
- [ ] **Step 6: `IconMenuController` (tối thiểu).** Xin bitmap shortcut ở `shortcut_menu_icon_size`; `width` lấy từ `shortcut_menu_width` và **ghi cả `LayoutParams.width`** trong `updateLayoutParams` (XML vẫn là 250dp). Giữ luật đặt vị trí cũ tới Task 2.
- [ ] **Step 7: Build** `gradlew.bat assembleDebug` xanh; commit `feat: restyle icon menu as glass panel`; cập nhật Nhật ký; báo điều phối.
- [ ] **Step 8: Kịch bản kiểm (Oppo, điều phối gộp vào bài cuối)** — (a) nhấn giữ icon giữa lưới: bảng rộng ~400 px, bo lớn, nền mờ thấy màu ảnh nền, chữ đọc rõ; (b) chế độ sáng: kính trắng mờ chữ tối; chế độ tối (`adb shell cmd uimode night yes`, hoặc Cài đặt > Hiển thị): kính tối chữ trắng; (c) bấm từng hàng: nền nhấn bo, không tràn góc, đúng hành động; (d) mức kính Tự động / Ép thấp / Ép cao / Tùy chỉnh (tắt và bật "Panel"): menu giống nhau, không crash; (e) mở lớp tìm kiếm không còn bị menu đè (ca hiếm, chỉ đọc code nếu không tái hiện được).

---

### Task 2: Vị trí và thứ tự như mẫu
**Bắt buộc kiểm tay:** không. Chỉ đổi vị trí đặt một view đã có; không cử chỉ chạm/kéo mới (hàng vẫn là click thường, `pendingMenuDrag` giữ nguyên); không giả định chỉ máy thật trả lời được.
**Files:** `IconMenuPlacement.kt` (tạo), `IconMenuController.kt`, `IconActionMenu.kt`, `IconPress.kt`, `IconCellView.kt`, `IconGridView.kt`, `DockView.kt`, `FolderPanelView.kt`, `dimens.xml`.
**Interfaces:** Produces `IconMenuPlacement.place(...)`, `IconPress.icon`, `IconPress.contentBottom`, `IconActionMenu.bind(shortcuts, includeEditHome, reversed)`.

- [ ] **Step 0 (điều phối, không chặn code):** trên Oppo với launcher mẫu, nhấn giữ một icon ở dock (menu phải lật lên trên) và chụp màn hình: ghi vào Nhật ký mẫu có đảo thứ tự hàng không. Nếu mẫu không đảo, D6 vẫn giữ theo lý do an toàn (hàng phá hủy xa ngón tay), báo chủ dự án.
- [ ] **Step 1: Hình học icon.** `IconCellView` thêm getter chỉ đọc `iconSizePx` (= `cells?.iconSize ?: 0`). `IconPress` thêm `icon: ScreenRect` và `contentBottom: Int`, cùng một hàm tạo dùng chung (ví dụ `IconPress.from(cell, itemId, container, key, snapshot)`): `getLocationOnScreen` của ô; `icon.left = x + (cell.width − iconSizePx) / 2`, `icon.top = y`, cạnh = `iconSizePx`; `contentBottom = y + cell.contentHeight()`; `bounds` (khung ô) giữ như cũ. Ba nơi tạo `IconPress` (lưới, dock, panel thư mục) gọi hàm này thay vì tự dựng. Ghi chú: ô app không co khi nhấn (AN Task 6, chỉ ô thư mục co) nên `getLocationOnScreen` không lệch; panel thư mục đã đứng yên lúc nhấn giữ.
- [ ] **Step 2: Hàm thuần `IconMenuPlacement`** (`ui/home/controller/`, không `android.*`). Đầu vào (px): `rootWidth`, `rootHeight`, inset `left/top/right/bottom`, `iconLeft/iconTop/iconRight`, `contentBottom`, `menuWidth`, `menuHeight`, `gap` (10dp), `margin` (8dp). Đầu ra: `left`, `top`, `above: Boolean`. Luật:
  1. `minX = insetLeft + margin`, `maxX = rootWidth − insetRight − margin − menuWidth`; `minY = insetTop + margin`, `maxY = rootHeight − insetBottom − margin − menuHeight`.
  2. Ngang: tâm icon `< rootWidth / 2` thì `left = iconLeft`, ngược lại `left = iconRight − menuWidth`; rồi kẹp vào `[minX, maxX]` (nếu `maxX < minX` thì `left = minX`).
  3. Dọc: `belowTop = contentBottom + gap`, `aboveTop = iconTop − gap − menuHeight`. `belowTop ≤ maxY` thì dưới; không thì `aboveTop ≥ minY` thì trên (`above = true`); không nữa thì chọn phía có nhiều chỗ hơn (`maxY + menuHeight − contentBottom` so với `iconTop − minY`) và kẹp `top` vào `[minY, maxY]` (có thể đè icon; chỉ xảy ra khi menu cao hơn nửa vùng an toàn, ví dụ chia đôi màn hình).
  KDoc ghi số đo mẫu và ví dụ ô hàng 3 cột 3 (ra [100..500] so với mẫu [98..498]).
- [ ] **Step 3: `IconActionMenu.bind(shortcuts, includeEditHome, reversed = false)`**: dựng danh sách hàng (nhóm shortcut, đường kẻ nếu có shortcut, nhóm cố định) rồi thêm theo thứ tự hoặc ngược lại; đường kẻ luôn nằm giữa hai nhóm.
- [ ] **Step 4: `IconMenuController`.** `onIconLongPress` giữ `press.icon` và `press.contentBottom` cạnh `iconMenuSnapshot` (cùng vòng đời, xóa cùng chỗ). `render`: `bind(reversed = false)` → `measure` rộng EXACTLY 200dp → lấy inset `ViewCompat.getRootWindowInsets(binding.homeRoot)?.getInsets(systemBars() or displayCutout())` (null thì 0) → `IconMenuPlacement.place(...)` → nếu `above` thì `bind(reversed = true)` (chiều cao không đổi) → `updateLayoutParams { width; leftMargin; topMargin }` → hiện. Không còn hình học (Activity tạo lại) thì đóng menu như ca thiếu bitmap. Thêm `shortcut_menu_gap` 10dp, `shortcut_menu_margin` 8dp vào khối dimens. Bỏ dùng `icon_menu_gap`/`icon_menu_margin`.
- [ ] **Step 5: Build** xanh; commit `feat: place icon menu like launcher popups`; Nhật ký; báo điều phối.
- [ ] **Step 6: Kịch bản kiểm (Oppo)** — (a) icon cột 1 và cột 2: mép trái menu thẳng mép trái hình icon; cột 3 và cột 4: mép phải thẳng mép phải hình icon (`uiautomator dump`: bounds `icon_action_menu` so với icon); (b) hàng 1–5: menu dưới icon, đỉnh menu cách đáy nhãn ~20 px; (c) hàng cuối lưới và dock: menu lật lên trên, "Gỡ cài đặt" ở **trên cùng**, đáy menu cách đỉnh icon ~20 px; (d) icon trong panel thư mục: menu cạnh icon, không có "Sửa màn hình chính", Ẩn ứng dụng chạy; (e) app có shortcut (launcher phải là mặc định; ví dụ Chrome, Điện thoại): shortcut sát icon ở cả hai chiều, đường kẻ giữa hai nhóm, menu không ra ngoài thanh trạng thái/điều hướng; (f) nhấn giữ icon nổi rồi kéo luôn: vẫn vào edit mode và kéo được (hồi quy M7).

---

### Task 3: Icon đầu hàng (bỏ nếu chủ dự án không duyệt)
**Bắt buộc kiểm tay:** không (tài nguyên hình và một dòng gán drawable).
**Files:** 4 vector `ic_shortcut_menu_*.xml`, `IconActionMenu.kt`.
**Interfaces:** Consumes `bind(...)` của Task 2.

- [ ] **Step 1: Vector.** Lấy path từ Material Symbols (repo `google/material-design-icons`, giấy phép Apache 2.0, kiểu Outlined, weight 400): `info`, `visibility_off`, `edit`, `do_not_disturb_on`. Mỗi file `width/height` 20dp, `viewportWidth/Height` theo SVG gốc, `android:tint` = `@color/shortcut_menu_text` (riêng uninstall `@color/shortcut_menu_destructive`, cả hai có bản tối). Comment đầu file ghi nguồn (tên icon, URL file SVG, giấy phép). Không tự viết path theo trí nhớ.
- [ ] **Step 2: Gán.** `IconActionMenu` gán compound drawable đầu hàng cho bốn hàng cố định (bounds 20dp), cùng `drawablePadding` với hàng shortcut nên chữ mọi hàng thẳng cột (44dp từ mép bảng).
- [ ] **Step 3: Build** xanh; commit `feat: add leading icons to icon menu rows`; Nhật ký; báo.
- [ ] **Step 4: Kịch bản kiểm (Oppo)** — (a) bốn icon đúng hình, đúng màu ở cả sáng và tối, icon gỡ màu đỏ; (b) menu có shortcut: chữ shortcut và chữ hàng cố định thẳng cột; (c) chữ dài nhất ("Thông tin ứng dụng") không bị `…` ở cỡ chữ mặc định.

---

### Task 4: Công tắc kính riêng của menu trong Tùy chỉnh (thêm 2026-11-09, điểm 6)
**Bắt buộc kiểm tay:** không (công tắc boolean theo mẫu GS; nhìn bằng mắt gộp vào bài kiểm cuối).
**Files:** `GlassCustom.kt`, `SettingsKeys.kt`, `SettingsRepositoryImpl.kt`, `GlassRole.kt`, `GlassPolicy.kt`, `GlassEnvironment.kt`, `SettingsActivity.kt`, `SettingsRow.kt`, `strings.xml`.
**Interfaces:** Consumes mẫu GS (`GlassElement`, `GlassCustom`, `keyOf`, `buildGlassCustomRows`, `glassElementOf`). Produces `GlassElement.ICON_MENU`, `GlassCustom.iconMenu`.

**Ý nghĩa và vì sao khác bốn công tắc cũ.** Bốn công tắc GS là "kính thật / kính mờ". Menu không bao giờ là kính thật (D12, K1), nên công tắc của nó là "kính mờ / phẳng", mặc định **bật** (người dùng hiện tại không đổi gì). Để tránh hiểu nhầm, hàng nằm dưới **tiêu đề riêng** "Menu nhấn giữ icon" (không lẫn vào nhóm "bật = kính thật"). Trạng thái tắt dùng `GlassMode.FLAT` vì `BackgroundGlass` đã hỗ trợ sẵn (trả `flatRes` = `bg_icon_action_menu`, màu phẳng theo sáng/tối).

- [ ] **Step 1: Mô hình.** `GlassElement` thêm `ICON_MENU`; `GlassCustom` thêm `iconMenu: Boolean = true`, `get`/`with` thêm nhánh. KDoc nói rõ nghĩa khác (bật = kính mờ, tắt = phẳng). `GlassCustom.NONE` giữ `GlassCustom()` (iconMenu = true; chỉ dùng ngoài Tùy chỉnh nên vô hại).
- [ ] **Step 2: Lưu trữ.** `SettingsKeys.GLASS_CUSTOM_ICON_MENU = booleanPreferencesKey("glass_custom_icon_menu")`; `SettingsRepositoryImpl`: đọc `prefs[...] ?: true`, `keyOf` thêm nhánh (`setGlassCustom` đã lặp qua `GlassElement.entries` nên tự ghi). **Không** thêm khóa vào danh sách `glassCustomSaved` (giữ nghĩa "đã từng có cấu hình bốn công tắc", người dùng cũ không bị gieo lại).
- [ ] **Step 3: Chính sách.** `GlassRole.ICON_MENU(GlassElement.ICON_MENU)`. `GlassPolicy.modeFor`: trong khối `custom != null && element != null`, nếu `element == GlassElement.ICON_MENU` trả `STATIC_BLUR` khi bật, `FLAT` khi tắt; phần tử khác như cũ. KDoc ghi ngoại lệ. `GlassEnvironment.seedFor` thêm `iconMenu = true`. Rà mọi `when` trên `GlassElement`/`GlassRole` (trình biên dịch báo).
- [ ] **Step 4: Cài đặt.** `strings.xml` (khối riêng cuối file): `gs_custom_menu_header` = "Menu nhấn giữ icon (bật = kính mờ, tắt = phẳng)", `gs_custom_icon_menu` = "Kính cho menu nhấn giữ". `SettingsRow.ID_GLASS_CUSTOM_ICON_MENU = "glass_custom_icon_menu"`. `buildGlassCustomRows`: sau bốn hàng cũ thêm `Header("header_glass_custom_menu", ...)` rồi `Switch(...)`. `glassElementOf` thêm nhánh. `onGlassCustomSwitched`: với `ICON_MENU` gọi thẳng `viewModel.onGlassCustomToggled` (không cảnh báo API < 33). Đổi công tắc thì `GlassEnvironment` phát listener và `BackgroundGlass.apply()` của menu chạy lại; Home dựng lại ở `onResume` như các công tắc khác.
- [ ] **Step 5: Build** xanh; commit `feat: add glass switch for icon menu in custom level`; Nhật ký; báo.
- [ ] **Step 6: Kịch bản kiểm (Oppo)** — (a) Cài đặt > Mức kính > Tùy chỉnh: dưới bốn hàng cũ có tiêu đề "Menu nhấn giữ icon" và một công tắc, **đang bật**; (b) tắt: về Home, nhấn giữ icon: bảng phẳng đúng màu sáng/tối, không mờ; bật lại: kính mờ; (c) ngoài Tùy chỉnh (Tự động, Ép thấp, Ép cao): menu luôn kính mờ; (d) bật công tắc không hiện hộp cảnh báo kính thật; (e) bốn công tắc cũ vẫn như trước (hồi quy GS); (f) người dùng đã ở Tùy chỉnh từ trước khi nâng cấp: công tắc mới hiện bật.

---

### Task 5: Hoạt ảnh mở/đóng menu (thêm 2026-11-09, điểm 7)
**Bắt buộc kiểm tay:** không bắt buộc ngay (đo `gfxinfo` và chỉnh cảm giác gộp ở Task 6, điều phối làm).
**Files:** `IconMenuMotion.kt` (tạo), `IconActionMenu.kt`, `IconMenuController.kt`, `IconMenuPlacement.kt` (kết quả có `alignEnd`, thêm từ Task 2).
**Interfaces:** Consumes `SpringProgress`, `BackgroundGlass.applyMotion`, `IconMenuPlacement.place`. Produces `IconMenuMotion`, `IconActionMenu.applyMotion(scale, alpha)`.

- [ ] **Step 1: `IconMenuMotion`.** `internal class IconMenuMotion(menu: IconActionMenu, onClosed: () -> Unit)` giữ một `SpringProgress` `p`: spec mở `(700f, 0.9f)`, spec đóng `(1400f, 1f)` (truyền qua `newSpec` của `animateTo`). Hằng: `START_SCALE = 0.6f`, `ALPHA_RAMP = 2.5f`. `apply(p)`: `scale = START_SCALE + (1 - START_SCALE) * p`, `alpha = (p * ALPHA_RAMP).coerceIn(0, 1)` -> `menu.applyMotion`. `onSettled(v)`: `v <= 0` thì `onClosed()`. Hàm: `open()`, `close()`, `snapClosed()`, `cancel()`, `setPivot(alignEnd, above)` (pivot theo `measuredWidth/Height`).
- [ ] **Step 2: `IconActionMenu`.** `applyMotion` gọi `glass.applyMotion(scale, 0f, 0f, alpha)` (đã `invalidate()` khi nền là kính mờ). Cờ `closing`: `dispatchTouchEvent` trả `true` (nuốt) khi đang đóng. `onDetachedFromWindow`: hủy spring.
- [ ] **Step 3: `IconMenuController`.** `render` nhánh đặt vị trí: sau `updateLayoutParams`, `snapClosed()`, `isVisible = true`, `setPivot`, `open()`. `render(null)` khi menu đang hiện: nếu `DragHelper.currentSession != null` (đường B) `snapClosed()`, ngược lại `close()` (ẩn khi spring dừng ở 0); scrim và icon nổi ẩn tức thì như cũ. `startDragFromMenu` (đường A): `snapClosed()` trước.
- [ ] **Step 4: Build** xanh; commit `feat: animate icon menu open and close`; Nhật ký; báo.
- [ ] **Step 5: Kịch bản kiểm (Oppo)** — (a) nhấn giữ icon hàng 3 cột 3: menu mọc từ góc trên-phải (gần icon) trong ~0,2 s; (b) icon dock (menu lên trên): mọc từ góc dưới; cột 1-2: từ góc trái; (c) chạm nền: co + mờ ~0,15 s rồi biến mất, chạm trong lúc đó không bấm nhầm hàng; (d) đóng giữa lúc mở: không giật, không kẹt hiện; (e) đóng rồi nhấn giữ icon khác ngay: menu mới mở sạch; (f) nhấn giữ icon nổi rồi kéo (đường A) và kéo icon khi menu mở (đường B): menu biến mất ngay; (g) Animator duration scale = tắt: hiện/ẩn tức thì, vẫn đúng; (h) mở/đóng 20 lần: đo `gfxinfo` (Task 6).

---

### Task 6: Kiểm trên Oppo, đo xác nhận, hoàn chỉnh knowledgement
**Bắt buộc kiểm tay:** không (phép đo ở đây là xác nhận, không dùng để chọn phương án).
**Files:** plan, knowledgement.

- [x] **Step 1 (điều phối): ảnh chụp so với mẫu** (đã làm: hàng 3 cột 3, cột 2, dock; chưa: panel thư mục, chế độ tối, 2026-11-09 Oppo). Cùng ô hàng 3 cột 3: `screencap` + `uiautomator dump`; ghi bounds `icon_action_menu`, hàng, so bảng "trước/sau". Chụp thêm cột 2, hàng cuối, dock, panel thư mục; sáng và tối.
- [x] **Step 2 (điều phối): hiệu năng mở/đóng** (đo 2026-11-09, 8 chu kỳ sau 1 khởi động, xen kẽ hai lượt; kết quả ở Bằng chứng dòng 8). Oppo, APK debug, cùng phiên: `main` rồi nhánh rồi `main` lặp (xen kẽ, AGENTS.md GL/AN): `dumpsys gfxinfo gambi.global.os26launcher reset` → mở/đóng menu 20 lần (nhấn giữ bằng `input motionevent` có `MOVE` 1 px khi giữ, R1) → đọc tổng khung, số khung > 33 ms, p95. Đạt nếu số khung > 33 ms và p95 của nhánh không cao hơn `main` quá nhiễu giữa hai lượt `main`.
- [ ] **Step 3 (điều phối, tùy chọn): không ảnh nền.** Sao lưu `files/backdrop_image` qua `run-as`, xóa, mở lại app: menu nền phẳng đúng màu chế độ; khôi phục ảnh, so dung lượng.
- [ ] **Step 4 (điều phối, tùy chọn): cỡ chữ lớn nhất** của hệ thống: chữ một dòng, hàng cao lên nếu cần, không cắt dấu tiếng Việt.
- [ ] **Step 2b (điều phối): hoạt ảnh** (khung đã đo, xem Bằng chứng dòng 8; cảm giác bằng mắt chưa kiểm). Cùng phiên đo, mở/đóng 20 lần có hoạt ảnh: so số khung > 33 ms và p95 với `main` (không hoạt ảnh); quay màn hình kiểm cảm giác, chỉnh `START_SCALE` và độ cứng spring theo mắt (điểm xuất phát chưa đo).
- [ ] **Step 5 (chỉ khi chủ dự án cho dùng Live 4):** chưa kiểm (máy bận). kiểm (a)(b) của Task 1 trên Live 4 (tier CLASSIC_CPU, mức kính chủ dự án đang đặt): kính mờ hiện, mở menu không giật thấy bằng mắt.
- [ ] **Step 6 (agent):** hoàn chỉnh knowledgement theo code thật (sơ đồ khớp tên hàm), liệt kê tài nguyên `icon_menu_*` không còn ai dùng (grep) vào "Đề xuất cập nhật tài liệu chung" để chủ dự án duyệt dọn; commit `docs: complete SM knowledgement`.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
**Máy:** Oppo A3x (Live 4 chỉ khi chủ dự án cho). **Chuẩn bị:** cài APK debug của nhánh (ghi nhánh@commit); Os26Launcher là launcher mặc định (để có shortcut); có ảnh nền; Home có app ở các cột 1–4, hàng cuối, dock, và một thư mục; biết cách bật/tắt chế độ tối.
1. Chế độ sáng. Nhấn giữ icon hàng 3 cột 3: kính trắng mờ, bo lớn, hẹp; mép phải thẳng mép phải icon; mọc ngay dưới nhãn. Icon nổi sáng trên nền tối.
2. Nhấn giữ icon cột 1/2: mép trái thẳng mép trái icon.
3. Nhấn giữ icon hàng cuối và icon dock: menu lật lên trên, "Gỡ cài đặt" ở trên cùng, không che icon.
4. Nhấn giữ app có shortcut (Chrome): shortcut sát icon, có icon đầu hàng, chữ thẳng cột; bấm một shortcut mở đúng.
5. Mở thư mục, nhấn giữ icon trong panel: menu đúng chỗ, không có "Sửa màn hình chính"; bấm "Ẩn ứng dụng" ẩn đúng app.
6. Bấm lần lượt: Thông tin ứng dụng (mở đúng màn hệ thống), Sửa màn hình chính (vào edit mode), Gỡ cài đặt (hộp thoại gỡ hiện, bấm Hủy).
7. Chạm nền tối: menu đóng. Mở lại, bấm Back: menu đóng.
8. Nhấn giữ icon, nhấc tay, nhấn giữ lại icon nổi rồi kéo: vào edit mode, kéo được, thả đúng chỗ.
9. Chuyển chế độ tối: mở lại menu: kính tối, chữ trắng, đỏ dịu hơn.
10. Cài đặt > Mức kính: lần lượt Tự động, Ép thấp, Ép cao, Tùy chỉnh (bật rồi tắt "Panel"): menu không đổi kiểu, không crash, dock/panel vẫn theo đúng lựa chọn.
11. **Công tắc (Task 4):** Mức kính = Tùy chỉnh: có hàng "Kính cho menu nhấn giữ" dưới tiêu đề riêng, mặc định bật. Tắt: menu phẳng; bật: kính mờ. Đổi sang Tự động: menu kính mờ bất kể công tắc.
12. **Hoạt ảnh (Task 5):** menu mọc từ góc gần icon (~0,2 s) và co + mờ khi đóng (~0,15 s) ở cả ca dưới icon và lật lên trên; chạm trong lúc đóng không bấm nhầm; kéo từ menu thì menu biến mất ngay; animator scale tắt thì tức thì.
13. **Hồi quy:** vuốt trang, mở/đóng App Library, tìm kiếm (vuốt xuống), panel thư mục kính thật vẫn như trước; nhấn giữ khoảng trống vào edit mode (GP).

**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** Oppo A3x, 2026-11-09, APK debug của `ff4663b`: vị trí, thứ tự lật, khung hình đo được (xem Bằng chứng). Chưa kiểm: sáng/tối, panel thư mục, cảm giác hoạt ảnh bằng mắt, mẫu có đảo thứ tự hàng không (Task 2 Step 0), công tắc ON/OFF khác nhau thấy được (máy chưa có ảnh nền). Live 4: chưa kiểm (máy bận).

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-09 | Plan | Đọc AGENTS.md, MultiAgent-Workflow, GL/GS (plan + knowledgement), M7 Task 10, TL1, code menu + `core/glass`; đo lại cỡ chữ và bo góc trên ảnh của điều phối (PIL); viết plan + khung knowledgement | commit `docs: add SM plan` | Đã duyệt (xem dòng sau) |
| 2026-11-09 | Plan | **Plan duyệt 2026-11-09**: chủ dự án duyệt theo đề xuất (15sp, icon đầu hàng có, kính mờ tĩnh, sáng/tối theo hệ thống), đổi hai điểm: (6) công tắc kính riêng của menu trong Tùy chỉnh -> Task 4 mới (D12), (7) hoạt ảnh mở/đóng bắt buộc -> Task 5 mới (D13, D14). Task kiểm cũ thành Task 6. Cập nhật quyết định, Vùng file, Review Focus, bài kiểm | commit `docs: approve SM plan and add switch and animation tasks` | `GlassPolicy.kt`, `GlassEnvironment.kt` vào Vùng file; không chồng lấn |
| 2026-11-09 | 1 | Role `ICON_MENU`, màu sáng/tối (`values-night/colors.xml` mới), khối `shortcut_menu_*`, kính qua `BackgroundGlass`, hàng 39dp 15sp, bỏ elevation | commit `feat: restyle icon menu as glass panel` | Build xanh. Lưu ý: `values-night/` đã có `themes.xml`, chỉ thêm `colors.xml` |
| 2026-11-09 | 2 | `IconMenuPlacement` (hàm thuần), `IconPress.from` + khung icon + đáy nhãn cho lưới/dock/panel, `bind(reversed)`, inset | commit `feat: place icon menu like launcher popups` | Build xanh. Kết quả có thêm `alignEnd` cho Task 5. Step 0 (kiểm mẫu có đảo thứ tự) chờ điều phối |
| 2026-11-09 | 3 | 4 vector Material Symbols (path lấy thật từ repo `google/material-design-icons` bằng curl, không viết tay) gán đầu hàng | commit `feat: add leading icons to icon menu rows` | Build xanh |
| 2026-11-09 | 4 | `GlassElement.ICON_MENU`, `GlassCustom.iconMenu` (mặc định bật), khóa `glass_custom_icon_menu`, `GlassPolicy` (bật STATIC_BLUR, tắt FLAT), hàng Settings dưới tiêu đề riêng, không cảnh báo API < 33, seed gieo bật | commit `feat: add glass switch for icon menu in custom level` | Build xanh. Khóa mới không tính vào `glassCustomSaved` |
| 2026-11-09 | Đo | Oppo A3x, APK debug `ff4663b` (điều phối): vị trí/lật đúng số đo, khung hình không thêm janky, công tắc có và chạy; chưa kiểm sáng/tối, panel thư mục, cảm giác hoạt ảnh, công tắc khi có ảnh nền, Task 2 Step 0; Live 4 chưa kiểm (máy bận) | commit `docs: record SM Oppo measurements` | Review điều phối: duyệt, không đổi mã |
| 2026-11-09 | 4, 5 | **Chủ dự án 2026-11-09: công tắc = kính thật/mờ.** Bật/tắt công tắc không thấy khác biệt, hỏi "có phải kính thật không"; quyết định: công tắc cùng nghĩa GS (bật = kính thật, tắt = kính mờ), ngoài Tùy chỉnh menu đi nhánh panel (K2), tint sáng 72% -> 55%. Làm: `GlassPolicy` (thêm vào nhánh panel, bỏ nhánh FLAT), `seedFor`, mặc định `false`, nhãn, cảnh báo API < 33, `IconActionMenu` = `FrameLayout` + `rows`, co bằng scale + dịch, tint | commit `feat: make icon menu glass switch real or frosted like panels` | Build xanh. Cần đo lại trên Oppo: kính thật mở/đóng (khung, giật, đóng giữa lúc mở), khác biệt bật/tắt khi có ảnh nền. D12 bị thay bằng D15-D17 |
| 2026-11-09 | 5 | `IconMenuMotion` (spring co 0,6 -> 1 + mờ quanh góc gần icon, mở 700/0,9, đóng 1400/1), nuốt chạm khi đóng, snap khi kéo từ menu, cancel ở detach | commit `feat: animate icon menu open and close` | Build xanh. Không so le hàng (không rẻ). Số spring chưa đo cảm giác, điều phối chỉnh ở Task 6 |



## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Luật đặt vị trí | đo được, Oppo, `uiautomator`: icon hàng 3 cột 3 (nửa phải) -> menu [100,637]-[500,973], 400 px = 200 dp, mép phải thẳng mép phải icon (mẫu đo [98,640]-[498,1132]); icon hàng 3 cột 2 (nửa trái) -> [220,637]-[620,973], mép trái thẳng mép trái icon; hàng cao 78 px, 4 hàng. Chưa kiểm: panel thư mục, hàng cuối lưới, menu 8 hàng | Đạt (một phần) |
| 2 | Đảo thứ tự khi lật | đo được, Oppo: icon dock (Tin nhắn) -> lật lên [220,1000]-[620,1336], đáy menu cách đỉnh icon 20 px (10 dp), "Gỡ cài đặt" trên cùng (xa icon nhất). Chưa kiểm: mẫu có đảo không (Task 2 Step 0), menu có shortcut | Đạt (một phần) |
| 3 | Kính (mọi mức, Tùy chỉnh) | (đo trên bản D12 cũ, cần đo lại với D15) Oppo: Tùy chỉnh bật/tắt công tắc menu chạy; tắt thì menu vẽ màu phẳng đồng nhất (230,230,234). Chưa kiểm các mức Tự động/Ép thấp; máy chưa có ảnh nền ("Ảnh nền: Chưa chọn") nên mờ và phẳng gần như giống nhau | Một phần |
| 4 | Sáng/tối | chưa kiểm (chỉ thấy chế độ sáng trên ảnh chụp: kính trắng mờ, chữ tối) | chưa kiểm |
| 5 | Z-order sau khi bỏ elevation | ảnh chụp Oppo: icon được nhấn vẫn sáng trên scrim, menu hiện đúng; chưa kiểm với lớp tìm kiếm | Một phần |
| 6 | Không hồi quy M7 Task 10 | chưa kiểm | chưa kiểm |
| 7 | Hộp chạm | chưa kiểm | chưa kiểm |
| 8 | Hiệu năng mở/đóng | đo được, Oppo, `dumpsys gfxinfo`, 8 chu kỳ mở+Back sau 1 khởi động, xen kẽ hai lượt: `main` 112 khung, 8 và 9 khung janky (Slow UI thread), p99 34-36 ms; SM 489 khung, 10 và 9 janky, p99 34 ms. Khoảng 1 khung janky mỗi lần mở đã có ở `main` (khung mở); SM không thêm janky, nhiều khung hơn vì có hoạt ảnh | Đạt |
| 9 | Tài nguyên | chưa kiểm | chưa kiểm |
| 10 | Công tắc (Task 4) | (đo trên bản D12 cũ, cần đo lại với D15) Oppo: Cài đặt có tiêu đề "Menu nhấn giữ icon (bật = kính mờ, tắt = phẳng)" và hàng "Kính cho menu nhấn giữ", mặc định BẬT khi vào Tùy chỉnh từ "Ép cao"; tắt chạy, menu thành màu phẳng. Khác biệt ON/OFF bằng mắt chưa xác nhận (không có ảnh nền) | Một phần |
| 11 | Hoạt ảnh (Task 5) | (khung đo trên kính mờ; kính thật cần đo lại) khung đã đo (dòng 8); cảm giác bằng mắt, đóng giữa lúc mở, kéo từ menu, animator tắt: chưa kiểm | Một phần |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md` (TT#7): menu nhấn giữ là bảng kính mờ theo sáng/tối hệ thống, rộng 200dp, chữ 15sp, có icon đầu hàng (nếu Task 3 được duyệt), đặt theo luật popup kiểu Launcher3 (mép theo nửa màn hình, lật lên trên và đảo thứ tự khi thiếu chỗ). Nền phía sau vẫn là scrim tối, chưa blur thật.
- `project_structure.md`: thêm `ui/home/controller/IconMenuPlacement.kt` (hàm thuần đặt menu); `res/values-night/colors.xml`.
- Dọn sau merge (chờ chủ dự án duyệt, không tự xóa): các mục `icon_menu_*` trong `colors.xml`/`dimens.xml` không còn được tham chiếu (danh sách chốt ở Task 6 Step 6); `activity_home.xml` có thể đổi `layout_width` của `icon_action_menu` sang `@dimen/shortcut_menu_width` khi nhánh nào đang giữ file này tiện sửa.
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement.
