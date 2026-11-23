# TF — Sửa 5 lỗi sau đợt test 2026-11-23 — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: một Task một lúc, báo cáo, chờ chủ dự án xác nhận rồi mới tick và sang Task kế. Dự án không viết test UI; Task 5 được thêm unit test JVM cho `LayoutReconciler` (ngoại lệ 2026-11-19). Lệnh viết cho PowerShell.

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** (chờ) · **Trạng thái:** Mới rà code, chưa sửa dòng nào.
**Khóa:** Task 5 đổi schema Room (v2 → v3, Migration viết tay) · Task 4 có thể đổi mặc định `ControlCenterConfig` (phải sửa `docs/config-schema.md` cùng commit).

## Tóm tắt rà soát

| # | Lỗi | Nguyên nhân | Nhãn | Task |
|---|---|---|---|---|
| 1 | Dấu "−" của widget lệch vào giữa widget | Ô lưới bọc item bằng `Box(propagateMinConstraints = true)`. `HomeRemoveBadge` dùng `requiredSize(48dp)`: ràng buộc min = cả widget mâu thuẫn với 48 dp nên Compose **căn giữa** khối 48 dp trong vùng widget, rồi mới cộng `offset`. Ô app không lỗi vì dấu nằm trong `Box` con không truyền min. Today cũng gọi y hệt (`HomeToday.kt:494`) | đọc code | 1 |
| 2 | Nút "Thêm tiện ích" (thêm widget) chưa có chức năng | Đọc code thấy cả ba đường đều đã nối: menu "Sửa" → `widgetPicker.show()` (`HomeActivity.kt:329`), viên "+" của Today → `show(forToday = true)` (`:470`), nút xanh trong trang chi tiết → `onAddWidget`/`addTodayWidget`. **Chưa thấy điểm gãy**, cần tái hiện | chưa rõ | 2 |
| 3 | Vùng vuốt Control Center trùng thanh hệ thống | Dải mép đặt ở `y = statusBarHeight()` (`ControlCenterOverlay.kt:324`). `statusBarHeight()` dùng `getInsets(statusBars())`, trả **0 khi thanh trạng thái đang ẩn** (edit mode của Home, app toàn màn hình) và **không tính tai thỏ**. Dải chỉ được đặt lại ở `relayout()` (xoay màn, đổi cấu hình), nên đo lúc thanh ẩn thì dải dính mép trên, đè lên vùng kéo thông báo của hệ thống | đọc code, suy luận chưa kiểm chứng trên máy | 3 |
| 4 | Nền Control Center trong suốt | Mặc định `background = NONE`, `blurBackground = true` (`ControlCenterConfig.kt:9-10`): panel không có ảnh nền, chỉ nhờ hệ thống làm mờ app phía sau (`FLAG_BLUR_BEHIND`), lớp tối chỉ `#26000000` (15 %). Máy tắt blur xuyên cửa sổ (tiết kiệm pin, ROM, API < 31) thì lớp tối `#99000000` (60 %), vẫn thấy rõ app phía sau. Thiết kế hiện tại chủ ý trong suốt, không phải lỗi vẽ | đọc code | 4 |
| 5 | App xóa khỏi MH chính không còn trong Library / tìm kiếm | "Xóa khỏi Màn hình chính" gọi thẳng `onQuickHideTapped` / `hideAppInFolder` → `HideAppUseCase` → `app_overrides.hidden = 1` (`RemoveAppConfirm.kt:20,39,50`). Đó chính là **Ẩn app**: Library và tìm kiếm lọc theo `hidden` (`SearchViewModel.kt:103-106`). Dự án chưa có trạng thái "không ở MH chính nhưng vẫn hiện". Không thể chỉ xóa hàng `launcher_items` vì `selectFresh` coi "chưa có hàng" là app mới và `catchUp` sẽ đặt lại (`LayoutReconciler.kt:845-851`) | đọc code | 5 |

## Task 1 — Dấu "−" của widget về đúng góc

- [ ] Step 1: `HomeEditMode.kt` `HomeRemoveBadge`: bỏ ảnh hưởng ràng buộc của cha bằng `.wrapContentSize(Alignment.TopStart, unbounded = true)` đặt **trước** `.offset` (hoặc `Modifier.align(TopStart)` + `wrapContentSize`). Sửa một chỗ trong hàm dùng chung nên cả Home (`HomeGridPage.kt:177`) lẫn Today (`HomeToday.kt:494`) đều đúng; ô app không đổi vì cha của nó không truyền min.
- [ ] Step 2: `assembleDebug` xanh.
- [ ] Step 3 (chủ dự án, Oppo): edit mode Home, widget 2×2, 4×2, 4×4 và widget của app khác: dấu "−" ở góc trên-trái, bấm được, hỏi xác nhận. Today: như vậy. Icon app: dấu không xê dịch so với trước.

## Task 2 — Luồng thêm widget ("Thêm tiện ích")

Chủ dự án xác nhận 2026-11-23: "Thêm tiện ích" là luồng thêm widget. Theo `AGENTS.md` mục "Khi gặp lỗi": đọc code chưa thấy điểm gãy nên chưa sửa, cần biết hiện tượng trước.

Luồng hiện có (đọc code 2026-11-23):

1. Home edit mode → "Sửa" → mục "Thêm tiện ích" (`HomeScreen.kt:380`) → `widgetPicker.show()` (`HomeActivity.kt:329`). Today edit mode → viên "+" → `show(forToday = true)` (`HomeActivity.kt:470`).
2. Bảng chọn (`HomeWidgetPicker`): bấm một ô gợi ý / một app → trang chi tiết.
3. Trang chi tiết: nút xanh "Thêm tiện ích" → `HomeViewModel.onAddWidget` → `LayoutReconciler.addWidget` (`LayoutReconciler.kt:1029`): đặt vào chỗ trống đầu tiên từ trang đang xem, hết chỗ thì tạo trang mới ở cuối → `HomeEvent.WidgetAdded` → pager cuộn tới trang đó. Widget của app khác đi `startAppWidget` (xin quyền bind + cấu hình).
4. **Chưa làm:** nhấn giữ thẻ trong bảng chọn rồi kéo ra lưới Home (ghi ở `HomeWidgetPicker.kt:104`, JH4 D5). Nếu chủ dự án nhấn giữ / kéo thẻ như iOS thì sẽ không có gì xảy ra: đây là tính năng còn thiếu, không phải lỗi.

- [ ] Step 1 (chủ dự án): cho biết dừng ở bước nào:
  - (a) bấm "Thêm tiện ích" trong menu "Sửa" / viên "+" mà bảng chọn không mở;
  - (b) bảng mở, bấm ô widget mà trang chi tiết không mở;
  - (c) bấm nút xanh, bảng đóng nhưng không thấy widget (có thể nằm ở trang khác / trang mới, hoặc có Toast lỗi);
  - (d) nhấn giữ / kéo thẻ widget ra màn hình thì không được (mục 4).
- [ ] Step 2: (a)–(c): rải `Log.d(TAG_TF, …)` dọc đường bị gãy: `HomeEditMenu` → `widgetPicker.show` → `dsDialogs.show` → `openDetail` → `action.invoke` → `onAddWidget` → `addWidget` (trả `pageId` / `null`) → `HomeEvent` → `pageScrollRequests`. Chủ dự án chạy lại, gửi `adb logcat -s TAG_TF`. Sửa đúng chỗ gãy (ghi lý do vào plan), gỡ log.
- [ ] Step 2': (d): tách thành plan riêng "kéo thẻ widget từ bảng chọn ra lưới" (cần lưới Compose nhận phiên kéo từ cửa sổ bảng chọn, đụng `HomeDragState`/`homeGestures`; `HomeViewModel.addWidgetAt` + `LayoutReconciler.addWidgetAt` đã có sẵn). Không làm trong TF.
- Nghi vấn sẽ kiểm trước nếu là (c): widget được đặt sang trang khác / trang mới nhưng pager không cuộn tới (`pageScrollRequests` phát lúc trang mới chưa có trong `uiState`); `addWidget` trả `null` (cỡ không hợp lệ với lưới hiện tại) → chỉ Toast `menu_action_failed`.

## Task 3 — Dải vuốt CC không đè thanh hệ thống

- [ ] Step 1: `ControlCenterOverlay.statusBarHeight()`: API 30+ dùng `getInsetsIgnoringVisibility(statusBars() or displayCutout())`, lấy `max` với `displayCutout` (tai thỏ cao hơn thanh trạng thái trên vài máy). Dưới 30 giữ dimen hệ thống, thêm `DisplayCutout.safeInsetTop` (API 28+) khi có.
- [ ] Step 2: đặt lại dải khi inset đổi: nghe `setOnApplyWindowInsetsListener` trên `edge` (hoặc gọi `relayout()` ở `onConfigurationChanged` của Service, kiểm cái nào đã có), để dải không giữ số đo cũ.
- [ ] Step 3 (chủ dự án quyết): có cần chừa thêm khe dưới thanh trạng thái không (vd 4–8 dp) để vuốt kéo thông báo của hệ thống không lỡ trúng dải. Mặc định đề xuất: 0, chỉ sửa số đo.
- [ ] Step 4 (Oppo + Samsung): dải nằm ngay dưới thanh trạng thái ở Home thường, Home edit mode (thanh ẩn), app toàn màn hình, máy có tai thỏ, xoay ngang. Vuốt trên thanh trạng thái chỉ mở bảng thông báo của hệ thống.

## Task 4 — Nền Control Center đục hơn

Đây là quyết định thiết kế, cần chủ dự án chọn (xem câu hỏi cuối plan). Phương án đề xuất:

- [ ] **A (đề xuất):** đổi mặc định `ControlCenterConfig.background` thành `WALLPAPER` (ảnh nền launcher đã làm mờ + lớp tối 30 %, không phụ thuộc blur xuyên cửa sổ, đục trên mọi máy). Người dùng cũ đã lưu khóa vẫn giữ lựa chọn của họ; người chưa đổi (khóa vắng) nhận mặc định mới. Sửa `docs/config-schema.md` cùng commit.
- [ ] **B:** giữ mặc định `NONE` nhưng nâng lớp tối: `cc_scrim_blur` `#26000000` → khoảng `#59000000`, `cc_scrim_strong` `#99000000` → khoảng `#CC000000`. Số cuối chỉnh theo ảnh chụp trên Oppo.
- [ ] Step chung: ảnh nền nạp bất đồng bộ (`ControlCenterPresenter.onPanelOpening`), khung đầu lúc kéo chưa có ảnh nên chỉ thấy lớp tối. Nếu chọn A: nạp sẵn ảnh khi Service khởi động / ảnh nền đổi, không đợi lúc kéo.
- [ ] Kiểm (Oppo, Samsung, máy ảo API 30): kéo mở CC trên Home và trên app nền sáng; chữ và ô đọc rõ; bật tiết kiệm pin (tắt blur) vẫn đục.

## Task 5 — "Xóa khỏi MH chính" ≠ "Ẩn app"

Hành vi đích (như iOS): xóa khỏi MH chính thì icon rời lưới / thư mục / dock, app **vẫn** ở Library và tìm kiếm. "Ẩn app" giữ nguyên nghĩa cũ (biến mất khỏi mọi nơi).

- [ ] Step 1 — Schema: thêm cột `offHome INTEGER NOT NULL DEFAULT 0` vào `app_overrides` (`AppOverrideEntity.offHome: Boolean`). `LauncherDatabase` version 3, `MIGRATION_2_3 = ALTER TABLE app_overrides ADD COLUMN offHome INTEGER NOT NULL DEFAULT 0`, khai trong `DatabaseModule` (không thêm vào danh sách destructive). Commit file schema `app/schemas/.../3.json`. `pruneEmpty` coi `offHome = 1` là khác mặc định. Sửa `docs/room-schema.md`.
- [ ] Step 2 — Reconciler: `selectFresh`, `placeNewInTransaction` và dựng lưới mặc định bỏ qua cả `hidden` lẫn `offHome`. Hàm mới `removeFromHome(key, userSerial)` (trong `mutex`): đặt `offHome = 1`, xóa hàng placement, dồn ô như `setHidden(true)` (tách phần dùng chung ra hàm private, không gọi lồng `mutex`). Hàm `addToHome(key, userSerial)`: xóa cờ, đặt sau icon cuối như bỏ ẩn. `removePackages`/`catchUp`/`renameComponent` dọn và re-point cờ này giống `hidden`.
- [ ] Step 3 — Domain/UI: `RemoveFromHomeUseCase` / `AddToHomeUseCase`. `RemoveAppConfirm.kt` (3 chỗ: dòng 20, 39, 50) nút "Xóa khỏi Màn hình chính" gọi đường mới; mục "Ẩn ứng dụng" của menu nhấn giữ giữ `HideAppUseCase`. Library, tìm kiếm, Recent apps (`RecentAppsRepositoryImpl`) **không** lọc `offHome`.
- [ ] Step 4 — Đường quay lại MH chính (thiếu thì app xóa rồi không đưa lại được): thêm mục "Thêm vào Màn hình chính" vào menu nhấn giữ app trong Library và kết quả tìm kiếm, chỉ hiện khi app đang `offHome`. Chuỗi mới ở đủ 9 file `strings.xml`. Kéo icon từ Library ra lưới: ngoài phạm vi (Library chưa có kéo-thả).
- [ ] Step 5 — Unit test JVM `LayoutReconciler`: (1) `removeFromHome` rồi `catchUp`/`placeNewApps` cùng danh sách app → app không quay lại; (2) `addToHome` → app ở sau icon cuối; (3) gỡ cài rồi cài lại → app về MH chính như app mới. `.\gradlew :app:testDebugUnitTest`.
- [ ] Step 6 (chủ dự án, Oppo) — kiểm migration: cài bản hiện tại → ẩn 1 app, đổi tên 1 app → cài bản mới đè lên → app ẩn vẫn ẩn, tên riêng còn. Rồi: xóa khỏi MH chính 1 app trên lưới, 1 app trong thư mục (thư mục còn 1 app → giải tán), 1 app ở dock → cả ba vẫn ở Library + tìm kiếm, khởi động lại launcher không quay lại; "Thêm vào MH chính" đưa về cuối lưới.
- **Dữ liệu cũ:** app đã "xóa khỏi MH chính" bằng bản cũ đang mang `hidden = 1`, không phân biệt được với app ẩn thật → để nguyên. Người dùng mở Cài đặt > App ẩn để bỏ ẩn. App chưa phát hành nên chấp nhận được (chủ dự án xác nhận).

## Thứ tự đề xuất

Task 1 (nhỏ, chắc) → Task 5 (lỗi mất app, nặng nhất) → Task 3 → Task 4 (sau khi chốt A/B) → Task 2 (sau khi có mô tả tái hiện). Task 2 có thể chen vào bất cứ lúc nào chủ dự án gửi thông tin.

## Câu hỏi cho chủ dự án

1. Task 2: luồng thêm widget dừng ở bước nào (a/b/c/d ở trên)?
2. Task 3: "trùng system bar" là thanh trạng thái phía trên (vuốt mở cả bảng thông báo hệ thống lẫn CC) hay thanh điều hướng phía dưới?
3. Task 4: chọn A (mặc định ảnh nền mờ) hay B (giữ trong suốt, tăng lớp tối)?
4. Task 5: đồng ý thêm cột Room `offHome` (schema v3) và mục "Thêm vào Màn hình chính" ở Library/tìm kiếm?

## Ngoài phạm vi

- Kéo icon từ Library ra MH chính.
- Phân loại lại các app `hidden = 1` cũ.
