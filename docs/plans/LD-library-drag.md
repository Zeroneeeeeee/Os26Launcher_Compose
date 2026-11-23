# LD — Kéo app từ App Library ra Màn hình chính — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: một Task một lúc, báo cáo, chờ chủ dự án xác nhận rồi mới tick và sang Task kế. Không viết test UI. Lệnh viết cho PowerShell. Máy kiểm: Samsung SM-A176B (chủ dự án cho phép 2026-11-23), Oppo A3x khi cần đo hiệu năng.

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** 2026-11-23 · **Trạng thái:** Task 1–3 xong (chủ dự án đã thử Task 2, 3). Task 4 xong code, đã cài lên SM-A176B (2026-11-23), chờ kiểm tay.
**Phụ thuộc:** TF (cột `offHome`, Room v3) đã có trong cây làm việc. LD **không** đổi schema.

## Hành vi đích (chủ dự án chốt 2026-11-23)

1. Nhấn giữ một app trong Library (ô lớn của nhóm, ô trong màn nhóm, hàng A–Z):
   - **Giữ yên** → Library vào **chế độ sửa**: icon rung, mỗi icon có dấu "−". Bấm "−" → hộp hỏi **gỡ cài đặt** (hai nút: "Xóa ứng dụng" đỏ / "Hủy") → trình gỡ của hệ thống. Chạm chỗ trống hoặc Back → thoát chế độ sửa.
   - **Kéo đi** (giữ đủ lâu rồi trôi quá ngưỡng ra khỏi chỗ cũ) → Library đóng (trượt về Home), Home **vào edit mode**, icon nổi tiếp tục bám ngón tay trên lưới Home với đầy đủ hành vi kéo hiện có (đẩy-dồn, lật trang ở mép, trang tạm cuối, thả vào dock).
2. Thả trên lưới / dock → tạo **hàng mới** cho app ở đúng chỗ thả (đẩy icon như kéo thường). App đã có trên Home vẫn tạo **thêm bản trùng** (chủ dự án chọn). App đang `offHome` (TF) thì bỏ cờ.
3. Thả ra ngoài lưới / dock đầy / trang từ chối → icon nổi mờ dần, không ghi gì. Home vẫn ở edit mode.

## Rà soát (đọc code 2026-11-23)

| Điểm | Hiện trạng | Hệ quả cho LD |
|---|---|---|
| Cử chỉ Library | `HomeActivity.dispatchTouchEvent` → `HomeLibraryState.onTouch` → `LibraryOverlayController` (vuốt mở/đóng). Ô app chỉ có `combinedClickable` (TF: nhấn giữ = hộp "Thêm vào MH chính") | Nhấn giữ / kéo phải nhận ở **Activity** (thấy mọi MotionEvent), không ở `pointerInput` của ô |
| Vòng đời `HomeLibrary` | `if (swipe <= 0f && panelP <= 0f) return` (`HomeLibrary.kt:143`): Library đóng hẳn thì gỡ khỏi composition | `pointerInput` trong Library sẽ bị huỷ giữa lúc kéo khi Library đóng → xác nhận phải định tuyến chạm ở Activity |
| `HomeLibraryState.render` | Đang mở mà `editMode` / `draggingItemId` bật thì `setLibraryOpen(false)` | Dùng luôn để đóng Library khi bắt đầu kéo ra |
| Kéo-thả Home | `HomeDragState` (tạo bằng `remember` trong `HomeScreen.kt:145`), cử chỉ ở gốc `HomeScreen` (`homeGestures`). Phiên kéo xoay quanh `itemId` đã có hàng DB; thả → `actions.onItemDropped(itemId, …)` → `moveItem` | Cần loại item mới "chưa có hàng" và đường thả riêng |
| Tiền lệ "thả thứ chưa có hàng" | `LayoutReconciler.addWidgetAt` (G2c Task 13): `GridInsertPlanner` với id tạm, trang tạm `PageId.NEW`, một transaction | Viết `addAppAt` cùng khuôn |
| Ảnh nổi | `HomeDragLayer` vẽ trong `HomeScreen`, **dưới** lớp Library | Lúc kéo trong Library (trước khi Library đóng xong) ảnh nổi bị Library che → chuyển `HomeDragLayer` lên trên Library |
| Hộp gỡ cài đặt | `showRemoveAppDialog` có thêm nút "Xóa khỏi MH chính" (không đúng cho Library) | Thêm `showUninstallAppDialog` (cùng thẻ `showCard`) |

## Task 1 — Dữ liệu: đặt app mới tại ô chỉ định

- [x] `LayoutReconciler.addAppAt(key, targetContainer, targetPageId, cell): Boolean` theo khuôn `addWidgetAt`: lưới → `GridInsertPlanner.plan` với id tạm `NEW_APP_PLAN_ID` (âm, khác `NEW_WIDGET_PLAN_ID`), trang tạm `PageId.NEW`, tạo trang tràn, chèn hàng `ITEM_TYPE_APP`, dời icon bị đẩy; dock → chèn ở slot, dồn rank, từ chối khi dock đầy (cùng luật `moveToDockInTransaction`). Cùng transaction: `hiddenAppDao.setOffHome(…, false)`. Không kiểm "đã có trên Home" (cho phép trùng).
- [x] `LayoutRepository.addAppAt` + `AddAppAtUseCase` (cùng file `RemoveFromHomeUseCase.kt`); `HomeViewModel.onLibraryAppDropped(app, container, pageId, cell)` (không lạc quan, như `addWidgetAt`; DB phát lại bố cục; bị từ chối chỉ ghi log, lỗi thì Toast `MenuActionFailed`).
- [x] `compileDebugKotlin` xanh (2026-11-23). Chưa có đường UI gọi tới (Task 4), nên chưa kiểm được trên máy.

## Task 2 — Chế độ sửa của Library (giữ yên)

- [x] `HomeLibraryState.editing` (state). Tạm thời bật bằng `onLongClick` của ô (Task 3 chuyển lên Activity). Tắt khi: chạm chỗ trống (`detectTapGestures` trên trang Library, ô tiêu thụ cú chạm của mình trước), Back (`BackHandler` đăng ký SAU `libraryOpen` nên được hỏi trước), Library đóng (`render`).
- [x] Ô app (ô lớn, ô màn nhóm, hàng A–Z): `homeJiggle(editing)` trên icon + `LibraryRemoveBadge` (dùng `HomeRemoveBadge`) ở góc trên-trái icon; ở chế độ sửa bấm icon không mở app.
- [x] `showUninstallAppDialog(activity, label, onDelete)` trong `RemoveAppDialog.kt` (tiêu đề `remove_app_title`, câu mới `library_uninstall_message`, nút "Xóa ứng dụng" / "Hủy"); "−" → hộp → `viewModel.uninstallApp(key)`.
- [x] Bỏ hộp "Thêm vào MH chính" của TF và cả chuỗi `addToHome` / `offHomeApps` (VM, use case, repository, reconciler, `observeOffHome`); 2 chuỗi `library_add_to_home*` thay bằng `library_uninstall_message` ở 9 file. `room-schema.md` sửa theo.
- [ ] Kiểm (chủ dự án, SM-A176B): giữ yên → rung + "−" trên ô lớn, màn nhóm, A–Z; bấm "−" → hộp → Hủy / Xóa; bấm icon khi đang rung không mở app; chạm trống, Back → thoát; đóng Library rồi mở lại → không còn rung. (Cài 2026-11-23, Claude chưa tự mở Library vì thao tác adb mở nhầm app cá nhân.)

## Task 3 — Nhận nhấn giữ / kéo trong Library ở Activity

- [x] `LibraryDragGesture` (`ui/home/compose/library/`, MotionEvent + Handler): gọi trong `HomeActivity.dispatchTouchEvent` **trước** `library.onTouch`. DOWN trên vùng một app (`HomeLibraryState.appAt`: Library mở hẳn, màn nhóm đứng yên; vùng do ô ghi qua `Modifier.libraryHit` bằng `boundsInWindow` đã cắt theo khung cuộn, gỡ khi ô rời composition; hàng A–Z: cả hàng) → chờ `longPressTimeout`; trôi quá slop trước đó → bỏ (cuộn / vuốt đóng Library như cũ).
- [x] Đủ giờ, chưa trôi: rung, `enterEditing()`, gửi `ACTION_CANCEL` qua `window.superDispatchTouchEvent` (Handler chạy ngoài `dispatchTouchEvent`) và vào cả hai controller vuốt; nuốt các sự kiện còn lại. `onLongClick` tạm của Task 2 đã bỏ (ô chỉ còn `clickable`).
- [x] Sau đó ra khỏi khung ô nở thêm 16 dp → `Host.onDragOut` (một lần), rồi `onDragMove` / `onDragEnd`. **Task 3: ba hàm này rỗng** (kéo ra chỉ ở lại chế độ sửa); Task 4 nối. Nhấc tay trong khung → chỉ ở lại chế độ sửa.
- [x] Ở chế độ sửa: nhấn giữ app khác cũng đi cùng đường (hit-test không phụ thuộc `editing`).
- [ ] Kiểm (chủ dự án, SM-A176B): bấm nhanh vẫn mở app; cuộn A–Z, vuốt ngang đóng Library không bị giữ nhầm; giữ yên → rung + chế độ sửa, nhấc tay không mở app; giữ trên ô lớn / màn nhóm / hàng A–Z; giữ rồi kéo ra ngoài ô: chưa có gì (chờ Task 4). Cài 2026-11-23.

## Task 4 — Bàn giao phiên kéo sang Home

- [x] `HomeDragState` vẫn tạo trong `HomeScreen`; `HomeScreen(onDragState = …)` báo lên `HomeActivity.homeDrag` (đổi ít hơn "tạo ở Activity", cùng tác dụng).
- [x] Thay vì loại `DragItem.LibraryApp` mới: `DragItem.App(LIBRARY_DRAG_ID, app)` + cờ `HomeDragSession.fromLibrary` (không phải sửa các `when` theo loại item, ảnh nổi vẽ như app). `HomeDragState.startExternal(app, pos, iconRect)`: không ô nguồn (`sourceContainer = 0`), ảnh nổi từ khung icon trong Library về dưới ngón tay, cỡ icon lưới, phóng `LIFT_SCALE`; `onDragStarted(LIBRARY_DRAG_ID)` bật `draggingItemId` + edit mode → `HomeLibraryState.render` đóng Library và thoát chế độ sửa của nó.
- [x] `LibraryDragGesture.Host` ở `HomeActivity`: `onDragOut` → `startExternal`; `onDragMove` → `move`; `onDragEnd` → `drop` / `cancel`. Cử chỉ đã bị `LibraryDragGesture` nuốt nên Home không nhận chạm trùng.
- [x] `HomeDragState`: `fromLibrary` không gộp (lưới lẫn dock), thả qua `commitDrop` → `onLibraryAppDropped` (VM bỏ `draggingItemId`, `ghostPage` rồi `addAppAt`); thả hỏng → co + mờ tại chỗ (`cancelDrop`).
- [x] `HomeDragLayer` chuyển từ `HomeScreen` lên `HomeActivity`, vẽ sau `HomeLibrary`.
- Rủi ro chưa kiểm: trong ~300 ms Library trượt đóng, Home còn đang trượt vào; nếu `pagerRect` chưa cập nhật theo lúc đó thì thả quá sớm có thể bị coi là ngoài lưới (ảnh mờ đi). Kéo bình thường lâu hơn thế.
- [ ] Kiểm (SM-A176B): kéo app chưa có trên Home ra trang 1, ra trang cuối (lật mép, trang tạm), ra dock còn chỗ / dock đầy (từ chối), thả ra ngoài; kéo app đã có trên Home (ra bản trùng); kéo app `offHome` (TF) → hết cờ; khởi động lại launcher: bố cục giữ đúng.

## Task 5 — Dọn và tài liệu

- [ ] Ghi bài học vào `docs/lessons/drag-and-layout.md` (định tuyến chạm qua Activity khi composable nguồn bị gỡ giữa cử chỉ).
- [ ] Không đổi khóa config, không đổi schema.

## Ngoài phạm vi

- Gộp thư mục khi thả app từ Library lên app / thư mục khác (cần tạo hàng rồi mới gộp; tách plan sau nếu cần).
- Kéo từ kết quả tìm kiếm.
- Kéo nhiều app một lúc.

## Rủi ro

- Cử chỉ mới chen vào trước `LibraryOverlayController`: nhầm giữa "giữ để kéo" và "vuốt ngang đóng Library" / cuộn dọc. Chỉ nhận khi giữ đủ `longPressTimeout` mà chưa trôi quá slop, nên vuốt / cuộn thường không đổi.
- Đưa `HomeDragState` lên Activity đụng `HomeScreen` (nhiều chỗ đọc `drag`): chỉ đổi chỗ tạo, không đổi API.
