# FX2 — Edit mode và nút Home (A2, B10, C13, C16, C19) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (điều phối chạy qua adb). Lệnh viết cho CMD/PowerShell (phần chạy trên máy là `sh`).

**Nhánh:** `fix/fx2-edit-mode-home` · **Worktree:** `E:\SourceTree\IosLauncher-wt\FX2-edit-mode-home` · **PR:** #33 · **Bắt đầu:** 2026-11-11 · **Plan duyệt:** 2026-11-11 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Chờ review
**Khóa:** không (không đổi schema Room, không đổi `HomeViewModel`)

**Goal:** (1) Rung của edit mode chỉ chạy khi view đang gắn, đang hiện và đang ở edit mode; Activity dựng lại trong edit mode không còn rò. (2) Nút Home và việc rời launcher bằng bất kỳ cách nào khác đều thoát edit mode; Home đóng thêm bảng chọn widget và bảng thời tiết. (3) Tên thư mục đang gõ không mất khi Activity dựng lại. (4) Bảng chọn widget mở trong edit mode không làm thanh trạng thái hiện lại. (5) Quyết định bằng số đo có nên thay animator từng ô bằng một ticker chung (C16).

**Architecture:** Toàn bộ nằm ở tầng `ui/home`. `Jiggler` nhận `host: View` và có một hàm `sync(active, views)` là nơi duy nhất quyết định rung hay dừng: chạy khi `active && host.isAttachedToWindow && host.isShown && host.windowVisibility == VISIBLE`. `IconGridView`, `DockView`, `AppsGrid` (trong `FolderPanelView`) gọi `sync` ở mọi chỗ trạng thái có thể đổi (đổi edit mode, `setItems`, attach, detach, đổi độ hiện). `HomeActivity` thêm ba việc: Home đóng thêm hai bảng và thoát edit mode (`onNewIntent`), `onStop` lưu tên thư mục rồi thoát edit mode (trừ khi đang dựng lại do đổi cấu hình), và nối cách ẩn thanh trạng thái cho cửa sổ bảng chọn widget. Không đổi `HomeViewModel` (dùng `exitEditMode()` có sẵn).

**Tech Stack:** không thêm thư viện. API dùng: `View.onVisibilityAggregated` / `onWindowVisibilityChanged` (có từ API 24, minSdk 26), `Choreographer.FrameCallback` (chỉ ở Task 2, nếu số đo chọn), `WindowInsetsControllerCompat` (đã dùng ở `SystemBarsController`), `Activity.isChangingConfigurations`.

**Spec:** Audit `docs/report/Audit-Report-2026-11-10.md` mục A2, B10, C13, C16, C19 + "Quyết định của chủ dự án (2026-11-11)" (B10); `docs/knowledgements/HB-home-closes-menu.md` (mọi lớp phủ tạm đóng cả ở Back và Home); `docs/knowledgements/TL1-home-polish.md` (thanh trạng thái ẩn ở edit mode); `docs/lessons/home-pager-animation.md`, `drag-and-layout.md`, `glass.md`.

## Đã đối chiếu với code hiện tại (`b1af810`, chưa đổi)

| Mục | Điều audit nói | Kết quả đọc code |
|---|---|---|
| A2 | `Jiggler` tạo một `ValueAnimator` vô hạn mỗi ô, listener giữ view | Đúng: `Jiggler.kt:26-40`. `start()` luôn gọi `stop()` (đặt `rotation = 0`) rồi tạo animator mới với pha ngẫu nhiên mới |
| A2 | `IconGridView.onDetachedFromWindow` không dừng rung | Đúng: `IconGridView.kt:728-744`; chỉ `setEditMode(false)` (`:397-405`) dừng. `setItems` gọi `startJiggle()` vô điều kiện khi `editMode` (`:473`), kể cả khi view chưa gắn |
| A2 | `DockView` thiếu tương tự | Đúng: `DockView.kt:370-378`; `setItems` `:253` |
| A2 | `FolderPanelView.AppsGrid` có dừng khi detach | Đúng: `FolderPanelView.kt:553-559`; `syncJiggle` `:371-373` |
| A2 | Trang trong cache của `RecyclerView` bị đưa vào pool mà không bind lại | Khớp: `HomePagerAdapter.setEditMode` chỉ `notifyItemRangeChanged` (`:145-149`); view đã detach nhận `setEditMode(false)` chỉ khi được bind lại. Adapter đã có `onViewAttachedToWindow` riêng cho trường hợp "gắn lại từ cache không qua bind" (`:117-121`) |
| B10 | `onNewIntent` không đóng edit mode, bảng widget, bảng thời tiết | Đúng: `HomeActivity.kt:186-196`. `onStop` (`:198-201`) chỉ `pager.cancelFlip()` |
| B10 | (phát hiện thêm) `exitEditMode()` từ `onStop` **không đủ** để dừng rung các trang lưới | `render()` gọi `pager.setEditMode` → `notifyItemRangeChanged`: việc bind lại (nơi `IconGridView.setEditMode(false)` chạy) nằm ở lượt layout của `RecyclerView`, mà cửa sổ đã dừng thì không layout (suy luận từ `ViewRootImpl`, chưa kiểm). Dock thì dừng ngay vì `render()` gọi `dockView.setEditMode` trực tiếp. Vì vậy cổng "đang hiện" của Task 1 là phần bắt buộc của B10 ở nền |
| B10 | `WidgetPickerController` đã tự đóng khi thoát edit mode | Đúng: `onEditMode(false) → dismiss()` (`:56-58`). Gọi `exitEditMode()` đã đủ cho bảng widget nếu `render()` chạy; vẫn thêm đóng tường minh ở Home (như `search.closeIfOpen()`), vì không phụ thuộc thứ tự render |
| C13 | Tên đang gõ chỉ lưu khi mất focus hoặc `closeFolder()` | Đúng: `FolderPanelView.kt:158`, `:305-319`; `onDetachedFromWindow` (`:298-303`) không lưu. `commitTitle()` là hàm public; `onTitleCommitted` đi tới `HomeViewModel.renameFolder` → `launchLayoutWrite` |
| C19 | Dialog là cửa sổ riêng, không mang cờ ẩn thanh trạng thái | Đúng: `WidgetPickerController.show()` dựng `PickerSheet` (`BottomSheetDialog`) không đặt gì cho thanh hệ thống; `SystemBarsController.applyStatusBarForEditMode` chỉ dùng `WindowInsetsControllerCompat(window, binding.root)` của Activity (`:46-57`) |
| C16 | Chi phí rung nằm ở đâu | GL (`lessons/glass.md`): chi phí rung của ô kính nằm ở RenderThread (`IssueDrawCommandsStart→SwapBuffers`), ghi display list chỉ 0,1 ms. Audit A2: 253 tick/30 s (≈ 8% một nhân) cho các animator "mồ côi" không vẽ khung nào ≈ chi phí riêng của animator ở luồng chính. Đó là cận trên cho phần ticker có thể cắt |

## Vì sao chọn thiết kế này

**Vấn đề cần giải.**
- A2 có hai nguyên nhân độc lập: animator không dừng khi view rời cửa sổ (Activity bị hủy → rò `HomeActivity` ~40 MB bitmap mỗi bản; trang trong cache → ~8% một nhân mãi mãi) và animator không dừng khi cả Activity ra nền (Android 11 không tạm dừng animator, audit A2).
- B10 làm A2 nặng thêm: chủ dự án muốn rời launcher là thoát edit mode, nhưng `render()` không đủ để dừng rung các trang lưới lúc cửa sổ đã dừng (bảng trên).
- Ràng buộc: file độc quyền chỉ `HomeActivity.kt`; không đụng `HomeViewModel` (FX1) và `core/glass/*` (FX1); FX5 sẽ sửa map icon trong ba view lưới nên chỉ sửa phần rung ở đó; bẫy chạm của AGENTS (`requestDisallowInterceptTouchEvent`, trạng thái nằm trong view của ViewPager2) không bị đụng vì không đổi xử lý chạm.
- C16 chưa biết ticker có đáng không: phần lớn chi phí rung có thể nằm ở RenderThread (GL), mà ticker chỉ cắt phần ở luồng chính.

**Các phương án đã xét.**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| A. Chỉ vá attach/detach, giữ một `ValueAnimator` mỗi ô | `onDetachedFromWindow` gọi `jiggler.stop`, `onAttachedToWindow` gọi lại nếu `editMode` (đúng "Hướng sửa" của audit) | Nhỏ nhất; đúng mẫu `FolderPanelView` đã có | Không dừng khi Activity ra nền mà view vẫn gắn (cần B10 + bind lại, mà bind lại không chạy lúc cửa sổ dừng); C16 giữ nguyên | **Làm trước** (Task 1), nhưng bổ sung cổng "đang hiện" |
| B. A + cổng "đang hiện" trong `Jiggler.sync` (`isShown`, `windowVisibility`) | Một hàm quyết định duy nhất; `onVisibilityAggregated`/`onWindowVisibilityChanged` gọi lại | Dừng được cả khi cửa sổ bị dừng, không cần HomeActivity biết adapter; dùng được cho cả ba view | Dựa vào việc framework báo ẩn khi Activity dừng (đọc tài liệu `View.onVisibilityAggregated`, chưa kiểm trên Live 4) | **Chọn** = Task 1; kiểm bằng CPU ở nền |
| C. Một ticker `Choreographer` mỗi `Jiggler` thay N animator | Một `FrameCallback`, mỗi khung tính góc của từng ô từ một mốc thời gian; pha ngẫu nhiên giữ theo ô | Giảm N lần dispatch animator, không boxing `Float`; dừng bằng một `removeFrameCallback` nên không thể "mồ côi" | Vẫn N lần `setRotation` mỗi khung; nếu chi phí chủ yếu ở RenderThread thì gần như không đổi tổng CPU | **Thử, quyết bằng số đo** (Task 2); giữ nếu ≥ 15% |
| D. Ticker toàn process (một singleton) | Mọi `Jiggler` dùng một `FrameCallback` chung | Ít callback nhất; pha đồng nhất giữa trang và dock | Vòng đời qua nhiều Activity, phải gỡ callback khi Activity chết (đúng loại rò đang sửa); lợi thêm so với C chỉ vài callback | Không |
| E. Chạy trên RenderThread bằng `ViewPropertyAnimator` ping-pong (`rotation(+2)` rồi `withEndAction` `rotation(-2)`) | Mỗi nửa chu kỳ 160 ms một animator RT; luồng chính chỉ chạm 6 lần/giây/ô | Có thể gần như xóa phần luồng chính | `withEndAction` chạy trên luồng chính: luồng chính nghẽn thì ô đứng ở đầu mút (nhìn thấy); giá trị `rotation` đọc được sai giữa chừng (ảnh kéo/ảnh chụp ô); khó giữ pha và biên độ "y hệt"; hành vi RT-animator trong `ViewPager2` + kính chưa có số đo | Không, trừ khi số đo Task 2 cho thấy luồng chính chiếm phần lớn mà C không đủ (ghi để sau) |
| F. Hạ nhịp cập nhật xuống 30 Hz | Cập nhật mỗi 2 khung | Giảm một nửa | Đổi hình thức (rung giật), trái yêu cầu "nhìn y hệt" | Không |

**B10 — đóng ở đâu.**

| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| `onNewIntent` (Home) + `onStop` (rời launcher), `onStop` bỏ qua khi `isChangingConfigurations` | Khớp quyết định chủ dự án; `onStop` chỉ chạy khi Activity không còn hiện (không bị bắn bởi hộp thoại quyền hay thanh thông báo kéo xuống, vốn chỉ làm `onPause`); đổi chủ đề sáng/tối không thoát edit mode oan | Mở "Cài đặt" từ nút trên thanh edit mode cũng thoát edit mode (câu hỏi mở Q1) | **Chọn** |
| `onPause` | Bắt được nhiều đường hơn | `onPause` chạy cả khi hộp thoại quyền, chế độ nhiều cửa sổ mất focus: thoát edit mode oan (đọc tài liệu vòng đời Activity) | Không |
| Đưa bảng widget và bảng thời tiết vào `HomeUiState` để Home đóng chúng qua state | Đồng nhất với panel thư mục | Cần sửa `HomeViewModel` (FX1/FX7 giữ), đổi hành vi `recreate()`; plan G2c/G3e đã chấp nhận "cửa sổ riêng, mất khi recreate" | Không; đóng tường minh bằng `closeIfOpen()` như `search.closeIfOpen()` |

**C13 — lưu ở đâu.** (a) `HomeActivity.onStop` (chọn): `ViewModel` còn sống nên ghi DB chạy xong qua `viewModelScope`; `onStop` luôn đi trước `onDestroy` kể cả với `recreate()`. (b) `FolderPanelView.onDetachedFromWindow`: chạy lúc cây view bị hủy, khi Activity đang `finish` thì `viewModelScope` có thể bị hủy ngay sau; và dựa vào `EditText` còn đúng văn bản lúc detach (framework không đảm bảo gọi `onFocusChange` lúc hủy cửa sổ: suy luận). (c) Ghi mỗi lần gõ có debounce: nhiều lần ghi DB qua mutex của `LayoutReconciler` cho một ô nhập; không đáng.

**C19 — ẩn thanh trạng thái ở đâu.** (a) Gọi `WindowInsetsControllerCompat(dialog.window, decorView).hide(statusBars())` cho cửa sổ của bảng (chọn): người dùng thấy thanh nút edit mode nguyên vẹn, cùng cách ẩn với Activity, API không deprecated. (b) Cho thanh nút tránh vùng thanh trạng thái khi bảng mở: dịch thanh nút ngược thiết kế TL1 (hai pill sát mép trên). (c) `FLAG_FULLSCREEN` trên cửa sổ bảng: deprecated từ API 30 và không có hành vi "vuốt mép trên để hiện tạm" như Activity.

**Vì sao chọn.** A2 và B10 được chọn theo dòng "Hướng sửa" của audit và theo số liệu A2 (vì 247 tick/30 s ở nền kéo dài đến khi process chết, cần dừng cả ở nền, nên cổng "đang hiện" không phải tùy chọn). C16 được tách riêng thành một phép đo vì GL đã đo rằng chi phí rung nằm ở RenderThread, còn audit A2 cho cận trên của phần animator ở luồng chính (≈ 8% một nhân với ~24–48 animator mồ côi, so với ≈ 50% khi rung): suy luận (chưa đo) là ticker chỉ cắt khoảng 5–10% tương đối và có thể không qua ngưỡng. Không nên làm nặng code bằng ticker rồi mới biết; cũng không nên bỏ ticker mà không có số.

**Đánh đổi chấp nhận.** (1) Thoát edit mode khi mở Cài đặt từ thanh nút (Q1). (2) `Jiggler.sync` giữ pha của ô đã rung qua các lần `setItems` (hiện tại mỗi `setItems` đặt lại `rotation = 0` rồi chọn pha mới nên ô giật về 0 sau mỗi lần thả/ẩn): đây là thay đổi nhìn thấy nhỏ, theo hướng tốt hơn (Q4). (3) Nếu số đo Task 2 không đạt ngưỡng thì C16 đóng với kết luận "đã đo, không đáng", và RenderThread là chỗ cần xem tiếp (ngoài vùng của nhánh này). Xét lại khi: kính thật ở Live 4 đổi cách vẽ (FX1), hoặc có bản release để đo lại (số đo ở đây là bản debug).

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Nút Home và rời launcher (thông báo, Gần đây, `onStop`) đều thoát edit mode; bảng chọn widget và bảng thời tiết đóng khi bấm Home | Chủ dự án, 2026-11-11 (audit, mục "Quyết định của chủ dự án") |
| 2 | A2 sửa bằng cổng "gắn + hiện + edit mode" trong `Jiggler.sync`, dùng cho cả ba view | Claude đề xuất, 2026-11-11; cơ sở: audit A2 + bảng đối chiếu code ở trên |
| 3 | C16 quyết bằng số đo: giữ ticker chung nếu tổng tick trong 10 s rung giảm ≥ 15% (trung vị 3 lượt xen kẽ) **và** khoảng min–max của hai bên không chồng nhau; ngược lại `git revert` commit ticker | Claude đề xuất, 2026-11-11; 15% vượt nhiễu đã ghi ở `lessons/glass.md` (máy trôi theo nhiệt độ; chỉ so trong cùng phiên, xen kẽ) |
| 4 | Không đổi `HomeViewModel`, `HomePagerController`, `HomePagerAdapter`, `core/glass/*` | Ràng buộc đợt 1 (audit "Kế hoạch sửa") |
| 5 | **Q1 (điều phối chốt 2026-11-11):** bấm "Cài đặt" trên thanh nút edit mode làm `onStop` nên thoát edit mode. Mặc định của plan: chấp nhận, vì quyết định #1 nói "rời launcher". Phương án khác: cờ `openingSettings` để giữ edit mode khi đi sang `SettingsActivity` | Điều phối chốt theo mặc định của plan, 2026-11-11 (chủ dự án giao điều phối quyết các điểm nhỏ trong phạm vi quyết định B10) |
| 6 | **Q2 (điều phối chốt 2026-11-11):** `onStop` bỏ qua `isChangingConfigurations` (đổi sáng/tối, đổi cỡ chữ, `recreate()` do Cài đặt không thoát edit mode, vì không phải "rời launcher"). Mặc định: bỏ qua | Điều phối chốt theo mặc định của plan, 2026-11-11 (chủ dự án giao điều phối quyết các điểm nhỏ trong phạm vi quyết định B10) |
| 7 | **Q3 (điều phối chốt 2026-11-11):** bảng thời tiết đang mở khi rời bằng Gần đây hoặc thông báo vẫn mở khi quay lại (quyết định chỉ nói Home). Mặc định: giữ nguyên | Điều phối chốt theo mặc định của plan, 2026-11-11 (chủ dự án giao điều phối quyết các điểm nhỏ trong phạm vi quyết định B10) |
| 8 | **Q4 (điều phối chốt 2026-11-11):** giữ pha rung của ô qua các lần `setItems` (tránh giật về 0). Mặc định: giữ. Bỏ được bằng cách xóa bảng pha trong `Jiggler` | Điều phối chốt theo mặc định của plan, 2026-11-11 (chủ dự án giao điều phối quyết các điểm nhỏ trong phạm vi quyết định B10) |

## Ngoài phạm vi / ghi để sau

- Nguyên nhân phần ~45% CPU còn lại khi rung (nghi RenderThread vẽ, kính thật của dock ở `FORCE_HIGH`): chỉ ghi số liệu theo luồng ở Task 2; sửa thuộc FX1 (`core/glass/*`) hoặc một nhánh perf riêng.
- Phương án E (RenderThread animator) và đo bản release: chỉ khi Task 2 cho thấy luồng chính chiếm phần lớn mà ticker chưa đủ.
- Home trong lúc đang có phiên kéo (`DragHelper.currentSession != null`): không đổi hành vi hiện có; `exitEditMode()` không hủy phiên kéo (không thử được ổn bằng adb).
- Đóng lớp tìm kiếm khi `onStop` (C6) thuộc FX4; Back/Home của Library, thư mục, menu giữ nguyên như HB.
- Bảng chọn widget không nằm trong `HomeUiState`, mất khi `recreate()` (đã chấp nhận ở G2c).

## Vùng file

| File (từ `app/src/main/java/gambi/global/os26launcher/`) | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/HomeActivity.kt` | Sửa: `onNewIntent` (+ `exitEditMode`, đóng hai bảng), `onStop` (+ lưu tên thư mục, thoát edit mode), nối `hideStatusBar` cho bảng widget | **Độc quyền** (FX2 giữ đợt này) |
| `ui/home/view/Jiggler.kt` | Sửa: `host`, `sync`; Task 2 thêm ticker | Không |
| `ui/home/view/IconGridView.kt` | Sửa gọn: dòng 120-121 (khai báo), 397-411 (`setEditMode`, `startJiggle`/`stopJiggle` → `syncJiggle`), 473 (`setItems`), 721-744 (attach/detach), thêm `onVisibilityAggregated` / `onWindowVisibilityChanged`. **Không đụng map icon** (FX5 đợt 2 sửa `iconCache` quanh `:258`, `:1146-1187`) | Dùng chung với FX5 (đợt sau) |
| `ui/home/view/DockView.kt` | Sửa gọn: dòng 57-58, 185-194, 253, 365-378 + hai hàm hiện. Không đụng map icon (`:117`, `:483-503`) | Dùng chung với FX5 |
| `ui/home/view/FolderPanelView.kt` | Sửa gọn: chỉ `AppsGrid` (`Jiggler(this)`, `syncJiggle`, `onDetachedFromWindow`, `:365-373`, `:553-559`). Không đụng map icon (`:389`, `:455-461`) và **không** thêm lưu tên ở detach | Dùng chung với FX5, FX7 |
| `ui/home/controller/WidgetPickerController.kt` | Sửa: `dismiss()` → `closeIfOpen()` public; thêm tham số `hideStatusBar` và gọi sau `sheet.show()` | Không |
| `ui/weather/WeatherSheetController.kt` | Sửa: thêm `closeIfOpen()` | Không (FX6 sửa `ui/weather/*` đợt 2; chỉ thêm một hàm) |
| `ui/home/controller/SystemBarsController.kt` | Sửa: thêm `hideStatusBarOf(window)` dùng chung với `applyStatusBarForEditMode` | Không |
| `docs/knowledgements/FX2-edit-mode-home.md` | Tạo (Task 7) | Không |
| `docs/report/FX2-edit-mode-home.md` | Sửa (nhật ký, kết quả) | Không |

Không đụng: `HomeViewModel.kt`, `HomeUiState.kt`, `HomePagerController.kt`, `HomePagerAdapter.kt`, `core/glass/*`, `core/ui/DragHelper.kt`, `data/*`, `res/*` (không thêm chuỗi mới), `AndroidManifest.xml`. Cần file ngoài vùng thì dừng và báo.

## Global Constraints

- Không code trước khi plan được duyệt. Mỗi Task: build `gradlew.bat assembleDebug` xanh trong worktree, commit tiếng Anh không dấu (`fix:`/`perf:`/`refactor:`), một hoặc vài commit mỗi Task, cập nhật Nhật ký, dừng và báo.
- Không thêm test, không thêm thư viện. Không merge `main` vào nhánh (MultiAgent-Workflow mục 11). Không cài APK, không chạy adb: điều phối làm.
- Giữ bẫy chạm trong `AGENTS.md`/`lessons/drag-and-layout.md`: không đổi `requestDisallowInterceptTouchEvent`, không đặt trạng thái kéo-thả vào view của `RecyclerView`/`ViewPager2`. Trạng thái "đang rung" là hàm của `(editMode, gắn, hiện)`, không phải một cờ "đã bắt đầu" sống sót qua tái dùng view.
- Hình thức rung giữ nguyên: biên độ ±2° (`JIGGLE_DEGREES`), widget nhân `jiggleScale`, tam giác tuyến tính chu kỳ 320 ms (160 ms mỗi chiều), pha ngẫu nhiên trong nửa chu kỳ đầu theo ô.
- `onVisibilityAggregated`/`onWindowVisibilityChanged` có thể bị gọi trước khi trường được khởi tạo (bài học SM, `lessons/glass.md`): các override phải an toàn khi `jiggler` chưa tồn tại hoặc dùng cách khai báo cho phép.
- Bản đo là **debug** (chủ dự án chạy debug); luôn ghi rõ bản nào được đo, và không so số giữa hai phiên cách xa nhau.
- Mọi lớp phủ tạm mới phải đóng ở cả Back và Home (quy tắc HB): bảng widget và bảng thời tiết nay đóng ở Home; Back đã đóng được vì là dialog.

## Review Focus

1. Mọi đường dừng rung đều qua `Jiggler.sync(false, …)`: detach, ẩn, tắt edit mode; không còn `ValueAnimator`/`FrameCallback` nào sống sau khi view detach hoặc Activity dừng. Không `jiggler.start()` rời rạc nào còn trong ba view.
2. Trang gắn lại từ cache (không qua bind) rung lại khi vẫn ở edit mode; trang trong pool không rung; trang bind lại với `editMode=false` về góc 0.
3. `sync` không làm rung mất khi lật trang lúc đang kéo-thả hoặc FakeDrag (lật có chủ đích dùng FakeDrag, `lessons/home-pager-animation.md` mục TL1): trang hiện luôn thỏa `isShown`.
4. `onStop`: thứ tự lưu tên → thoát edit mode → `super.onStop()`; `isChangingConfigurations` đúng nghĩa; không gọi `exitEditMode()` khi đang dựng lại.
5. `closeIfOpen()` của bảng widget hủy phiên kéo đang mở (qua `cancelDragOf` có sẵn) và an toàn khi gọi hai lần (`dismiss` từ `render` và từ `onNewIntent`).
6. Ẩn thanh trạng thái cho cửa sổ bảng không làm `statusBarHiddenByEdit` lệch, không làm lưới/thanh nút dịch (inset vẫn lấy "bỏ qua visibility", TL1 Task 6) và khi bảng đóng thanh vẫn ẩn nếu còn edit mode.
7. Không file nào ngoài vùng; không đổi chữ ký ViewModel; không log tạm còn sót (`grep` tag `JIG`).
8. Phần ticker (Task 2): công thức góc khớp animator cũ (biên độ, chu kỳ, pha), `removeFrameCallback` ở mọi đường dừng, và quyết định giữ/bỏ có số đo kèm theo trong Nhật ký.

---

### Task 1: A2 — cổng "gắn + hiện + edit mode" cho rung (giữ animator từng ô)

**Bắt buộc kiểm tay:** không (điều phối kiểm bằng adb; gộp vào bài kiểm cuối)
**Files:** `Jiggler.kt`, `IconGridView.kt`, `DockView.kt`, `FolderPanelView.kt` (chỉ `AppsGrid`).
**Interfaces:** Consumes: không. Produces: `Jiggler(host: View)`; `Jiggler.sync(active: Boolean, views: List<View>)` (chạy khi `active && host.isAttachedToWindow && host.isShown && host.windowVisibility == View.VISIBLE`, ngược lại dừng, hủy animator, trả `rotation = 0` cho `views`; không làm gì khi đã dừng và không có animator).

- [x] **Step 1: `Jiggler`.** Thêm tham số `host`; thêm `sync` gói `start`/`stop` hiện có (vẫn một `ValueAnimator` mỗi ô ở Task này). `start` chỉ còn gọi từ `sync`. Chưa đổi hình thức rung.
- [x] **Step 2: `IconGridView`.** `Jiggler(this)`; `startJiggle`/`stopJiggle` thành `syncJiggle() = jiggler.sync(editMode, allCells())`. Gọi `syncJiggle()` ở `setEditMode` (thay `if (editMode) start else stop`), cuối `setItems` (thay `if (editMode) startJiggle()`), `onAttachedToWindow`. `onDetachedFromWindow` gọi `jiggler.sync(false, allCells())` **trước** `super` (trong `onDetachedFromWindow` `isAttachedToWindow` còn `true`, nên phải truyền `false` tường minh). Thêm `onVisibilityAggregated(isVisible)` và `onWindowVisibilityChanged(visibility)` gọi `super` rồi `syncJiggle()`.
- [x] **Step 3: `DockView`.** Tương tự với `iconCells()`: `:57-58`, `:185-194`, `:253`, `:365-378`.
- [x] **Step 4: `FolderPanelView.AppsGrid`.** `Jiggler(this)`; `syncJiggle(active)` → `jiggler.sync(active && editMode, cellViews())`; `onDetachedFromWindow` → `jiggler.sync(false, cellViews())`. Không đổi gì khác ở file.
- [x] **Step 5:** `grep -n "jiggler\.\(start\|stop\)"` ở `ui/home/view` chỉ còn trong `Jiggler.kt`. Build `assembleDebug` xanh. Commit `fix: stop edit mode jiggle when the view is detached or hidden`.
- [x] **Step 6: Kịch bản kiểm (Live 4, APK = commit Task 1; mục "Bài kiểm" B và C):**
  (a) vào edit mode, vuốt sang trang 2 rồi về trang 1, bấm "Xong", đo `ticks.sh 30`: **≤ 10 tick** (audit trước khi sửa: 253);
  (b) vào edit mode, mở Cài đặt bằng `am start -a android.settings.SETTINGS` (launcher ra nền **trong edit mode**, B10 chưa làm), đo 30 s ở nền: **≤ 10 tick** (audit: 247). Nếu > 20 tick: cổng không bắt được "Activity dừng"; ghi vào Nhật ký, **không** sửa Task 1, Task 4 dùng phương án dự phòng;
  (c) vào edit mode, đổi `cmd uimode night no` rồi `yes` (hai lần đổi), `dumpsys meminfo`: **`Activities: 1`** (audit: 3), Native Heap tăng ≤ 10 MB so với trước khi đổi;
  (d) vào edit mode, vuốt qua 3 trang rồi quay lại trang đầu: icon ở trang hiện **vẫn rung** (3 ảnh chụp liên tiếp, góc nghiêng khác nhau), dock rung, mở một thư mục thì icon trong panel rung, đóng panel thì dừng;
  (e) Home rảnh, không edit mode, `ticks.sh 15`: ≤ 10 tick (không thụt lùi so với audit: 5).

---

### Task 2: C16 — ticker chung mỗi `Jiggler`, giữ hay bỏ theo số đo

**Bắt buộc kiểm tay:** **có** — phép đo hiệu năng dùng để chọn phương án (mục 4.2 của MultiAgent-Workflow.md): quyết định giữ hay `git revert` commit này chỉ dựa trên số tick đo trên Live 4. Điều phối đo và quyết theo tiêu chí ở Step 4(c) (chủ dự án giao điều phối, 2026-11-11), ghi số vào Nhật ký rồi mới sang Task 3.
**Files:** `Jiggler.kt` (chỉ file này).
**Interfaces:** Consumes: `Jiggler.sync` từ Task 1. Produces: cùng API, bên trong một `Choreographer.FrameCallback` mỗi `Jiggler`.

- [x] **Step 1: Cài ticker (commit riêng).** Trong `Jiggler`: danh sách `(view, phaseMs, startedAtMs)`; mỗi khung `x = (nowMs - startedAtMs + phaseMs) mod 320`, `t = if (x < 160) x/160 else (320 - x)/160`, `rotation = (t - 0.5) * 2 * JIGGLE_DEGREES * jiggleScale` (đúng công thức animator cũ: `ValueAnimator` 0→1 `REVERSE` tuyến tính 160 ms, pha ban đầu ngẫu nhiên trong [0, 160) ms). `nowMs` lấy từ `frameTimeNanos`. Pha và mốc bắt đầu giữ theo ô (`WeakHashMap<View, …>`) qua các lần `sync` (Q4); ô mới nhận pha ngẫu nhiên. `sync(false)` gọi `removeFrameCallback`, xóa danh sách, `rotation = 0`. Không đổi API. Commit `perf: drive edit mode jiggle with one frame callback per grid`.
- [x] **Step 2: Log tạm (chỉ trong cây làm việc, KHÔNG commit):** tag `JIG`, in `nowMs rotation` của ô đầu tiên của trang hiện và của dock mỗi khung trong 1 s, ở **cả hai** bản (commit Task 1 và commit ticker) để so biên độ/chu kỳ. Gỡ bằng `git checkout -- <file>` sau khi đo; Review Focus 7 `grep JIG` phải rỗng.
- [x] **Step 3: Dựng hai APK để đo xen kẽ:** `task1.apk` (commit Task 1) và `ticker.apk` (commit Step 1). Copy vào thư mục tạm ngoài repo.
- [x] **Step 4: Kịch bản kiểm (Live 4, mục "Bài kiểm" D):**
  (a) **Hình thức:** từ log `JIG`, mỗi bản: biên độ đỉnh ≈ 2,0° (widget ≈ 2,0° × `WIDGET_JIGGLE_SCALE`), chu kỳ ≈ 320 ms (đo bằng khoảng giữa hai đỉnh dương liên tiếp, sai số ≤ 1 khung), các ô khác nhau có pha khác nhau (≥ 3 giá trị pha khác nhau trong 6 ô). Lệch → sửa công thức, đo lại;
  (b) **CPU:** ma trận xen kẽ `A(task1) B(ticker) A B A B`, mỗi lượt: `install -r`, `force-stop`, mở Home, vào edit mode, đợi 5 s, `ticks.sh 10` (ghi cả bảng theo luồng: `main`, `RenderThread`, `GlassBlur`, luồng khác), ghi nhiệt độ pin trước mỗi lượt. Ghi vào Nhật ký: 6 số tick và trung vị, min–max mỗi bên;
  (c) **Quyết định (quyết định #3):** giữ ticker nếu trung vị `B ≤ 0,85 × A` **và** `max(B) < min(A)`. Ngược lại: `git revert <commit ticker>` (thêm commit revert, không viết lại lịch sử), Nhật ký ghi "ticker không đáng, số liệu …", và C16 đóng với kết luận ở knowledgement. Kèm bảng theo luồng để chỉ ra phần ở `RenderThread`;
  (d) **Tùy chọn (nếu (b) không phân định được hoặc điều phối muốn tách phần kính):** lặp (b) với mức kính đổi sang "Tắt/Tự động" qua Cài đặt (rồi trả về Ép cao); và/hoặc cùng ma trận trên bản release theo `RB-release-build.md` mục 7. Ghi rõ bản nào, trả về debug sau khi xong.

---

### Task 3: B10 — nút Home thoát edit mode, đóng bảng widget và bảng thời tiết

**Bắt buộc kiểm tay:** không
**Files:** `HomeActivity.kt`, `WidgetPickerController.kt`, `WeatherSheetController.kt`.
**Interfaces:** Consumes: `HomeViewModel.exitEditMode()`. Produces: `WidgetPickerController.closeIfOpen()`, `WeatherSheetController.closeIfOpen()` (public, an toàn khi gọi nhiều lần).

- [x] **Step 1:** `WidgetPickerController`: đổi tên `dismiss()` thành `closeIfOpen()` (public), giữ thân hàm (hủy phiên kéo bằng `cancelDragOf` rồi `dismiss`); `onEditMode` và `onDestroy` gọi hàm mới. `WeatherSheetController`: thêm `fun closeIfOpen() { dialog?.dismiss() }` (listener dismiss sẵn có dọn `dialog`, `collectJob`, `viewModel.onSheetClosed()`; `onDestroy` có thể gọi lại).
- [x] **Step 2:** `HomeActivity.onNewIntent` (nhánh HOME), cạnh các lời gọi HB: thêm `viewModel.exitEditMode()`, `widgetPicker.closeIfOpen()`, `weatherSheet.closeIfOpen()`, kèm chú thích B10 và nhắc quy tắc HB (cả Back và Home). Giữ nguyên thứ tự đóng menu/tìm kiếm/Library/thư mục.
- [x] **Step 3:** Build xanh. Commit `fix: close edit mode, widget picker and weather sheet on Home`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" E):**
  (a) edit mode + `input keyevent KEYCODE_HOME`: thanh nút biến mất, thanh trạng thái hiện lại, icon hết rung (chụp 3 ảnh), `ticks.sh 15` ≤ 10 tick;
  (b) edit mode + bảng "Tiện ích" mở + Home: bảng đóng, edit mode tắt, Back/Home tiếp theo không lỗi;
  (c) không edit mode, bấm widget thời tiết mở bảng, Home: bảng đóng (trước: còn mở, audit B10);
  (d) hồi quy HB: Home đóng menu giữ icon, tìm kiếm, Library, thư mục; Home không làm gì khi Home đang sạch;
  (e) Back vẫn đóng bảng widget và bảng thời tiết như trước.

---

### Task 4: B10 — rời launcher bằng cách khác cũng thoát edit mode (`onStop`)

**Bắt buộc kiểm tay:** không
**Files:** `HomeActivity.kt`.
**Interfaces:** Consumes: `Activity.isChangingConfigurations`, `HomeViewModel.exitEditMode()`. Produces: không.

- [x] **Step 1:** `onStop`: sau `pager.cancelFlip()` và **trước** `super.onStop()`, thêm `if (!isChangingConfigurations) viewModel.exitEditMode()` (B10, Q2). Chú thích: không thoát khi `recreate()` do đổi cấu hình vì đó không phải rời launcher.
- [x] **Step 2 (dự phòng, chỉ làm nếu Step 4(a) trượt về CPU ở nền):** thêm hàm riêng trong `HomeActivity` duyệt các `IconGridView` đang gắn trong `RecyclerView` bên trong `binding.homePager` (`getChildAt(0)`) và gọi `setEditMode(false)` ngay, vì bind lại của adapter không chạy khi cửa sổ đã dừng (bảng đối chiếu, dòng B10). Không đụng `HomePagerAdapter`/`HomePagerController`. (Không cần: kiểm Task 4 Step 4(a) đạt, 0 tick ở trạng thái ổn định.)
- [x] **Step 3:** Build xanh. Commit `fix: leave edit mode when the launcher stops`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" F):**
  (a) edit mode → `am start -a android.settings.SETTINGS` (thay cho bấm thông báo/Gần đây), đo 30 s ở nền: **≤ 10 tick** (audit: 247); quay về bằng `KEYCODE_HOME`: Home **không** ở edit mode, icon không rung;
  (b) edit mode → `KEYCODE_APP_SWITCH` (Gần đây) → chọn lại Os26Launcher: edit mode tắt;
  (c) bấm "Cài đặt" trên thanh nút edit mode, rồi Back về Home: edit mode tắt (Q1: điều phối chốt thoát edit mode);
  (d) edit mode → đổi sáng/tối hai lần: Home dựng lại **vẫn ở edit mode** (`isChangingConfigurations` đúng), `Activities: 1`. Nếu edit mode bị thoát: guard không hoạt động ở `onStop` của `recreate()`; báo điều phối, không tự đổi hướng;
  (e) kéo-thả một icon rồi bấm Home ngay sau khi thả: không crash, không icon tàng hình.

---

### Task 5: C13 — lưu tên thư mục đang gõ khi Activity dừng

**Bắt buộc kiểm tay:** không
**Files:** `HomeActivity.kt`.
**Interfaces:** Consumes: `FolderPanelView.commitTitle()` (public, có sẵn). Produces: không.

- [x] **Step 1:** `onStop`: đầu hàm (trước thoát edit mode) gọi `binding.folderPanel.commitTitle()` kèm chú thích C13: `ViewModel` còn sống ở `onStop`, tên được ghi DB trước khi cây view bị hủy.
- [x] **Step 2:** Build xanh. Commit `fix: commit the folder title being typed when the launcher stops`.
- [x] **Step 3: Kịch bản kiểm (Live 4, "Bài kiểm" G; cần sao lưu DB, có đổi tên thư mục thử):**
  (a) mở thư mục, chạm tiêu đề, gõ `input text FX2test` (bàn phím mở, chưa bấm xong), `cmd uimode night no`: Home dựng lại, thư mục mở lại với tên **FX2test** (trước: tên cũ); `force-stop` rồi mở lại: tên vẫn FX2test;
  (b) cùng bước khi rời bằng `KEYCODE_APP_SWITCH`: tên được lưu;
  (c) hồi quy: gõ rồi bấm Xong trên bàn phím, Back, Home: tên lưu đúng một lần;
  (d) trả tên thư mục về như cũ và trả `cmd uimode night yes`; so DB với bản sao lưu khi kết thúc.

---

### Task 6: C19 — bảng chọn widget không đưa thanh trạng thái trở lại

**Bắt buộc kiểm tay:** không
**Files:** `SystemBarsController.kt`, `WidgetPickerController.kt`, `HomeActivity.kt`.
**Interfaces:** Consumes: `WindowInsetsControllerCompat`. Produces: `SystemBarsController.hideStatusBarOf(window: Window)` (dùng chung hành vi `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` + `hide(statusBars())` với `applyStatusBarForEditMode`); `WidgetPickerController(binding, viewModel, hideStatusBar: (Window) -> Unit)` (lambda, vì `SystemBarsController` là `internal` còn `WidgetPickerController` là `public`).

- [x] **Step 1:** `SystemBarsController`: tách thân nhánh `editMode == true` của `applyStatusBarForEditMode` thành `hideStatusBarOf(window)` (nhận `Window` bất kỳ, dùng `window.decorView`); `applyStatusBarForEditMode` gọi lại hàm này cho cửa sổ Activity. Không đổi hành vi hiện có.
- [x] **Step 2:** `WidgetPickerController.show()`: sau `sheet.show()` gọi `sheet.window?.let(hideStatusBar)` khi `viewModel.uiState.value.editMode`. `HomeActivity` truyền `systemBars::hideStatusBarOf` khi dựng `WidgetPickerController`.
- [x] **Step 3:** Build xanh. Commit `fix: keep the status bar hidden while the widget picker is open`.
- [x] **Step 4: Kịch bản kiểm (Live 4, "Bài kiểm" H):**
  (a) chụp màn hình: edit mode, bấm "Tiện ích": **không** có đồng hồ/biểu tượng thanh trạng thái đè lên các nút "Cài đặt", "Tiện ích" (trước khi sửa, ảnh của APK `main` cho thấy đè; audit C19), thanh nút và lưới không dịch so với trước khi mở bảng;
  (b) Back đóng bảng: thanh trạng thái **vẫn ẩn** (còn edit mode), không chớp hiện lại (chụp 3 ảnh liền sau khi đóng); bấm "Xong": thanh trạng thái hiện lại;
  (c) kéo một thẻ widget từ bảng thả lên lưới (đường G2c Task 13): không đổi hành vi, bảng đóng sau khi thả;
  (d) vuốt từ mép trên khi bảng mở: thanh hiện tạm rồi tự ẩn (cùng cách với Activity).

---

### Task 7: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không
**Files:** `docs/knowledgements/FX2-edit-mode-home.md` (tạo), plan này (cập nhật).

- [x] **Step 1:** Viết knowledgement theo mẫu mục 8: sơ đồ Mermaid của luồng "Home / onStop / detach → `Jiggler.sync`" và luồng cổng "gắn + hiện + edit mode"; bảng số đo (trước/sau cho mọi dòng bảng A2, kết quả ma trận Task 2 kèm bảng theo luồng, ghi rõ bản **debug**); mục "Bài học cho AGENTS.md" (xem "Đề xuất cập nhật tài liệu chung").
- [ ] **Step 2:** Điền "Bằng chứng Review Focus" và "Kết quả kiểm chứng" ở plan; điều phối rebase, build xanh, rà diff tổng (`git diff main -- app` chỉ nằm trong vùng file), kiểm `grep JIG` rỗng.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

**Máy:** Vsmart Live 4 (Android 11, Os26Launcher là launcher mặc định, mức kính Ép cao). **Bản:** debug; ghi `git rev-parse --short HEAD` của từng APK. Hỏi chủ dự án máy có đang rảnh trước khi cài hoặc chạy script. Mọi số CPU trong 10–30 s đo sau khi máy đã ở yên và ghi nhiệt độ pin (`adb shell dumpsys battery | findstr temperature`); không so số giữa hai phiên cách xa nhau.

### A. Chuẩn bị (một lần)

1. Ghi trạng thái ban đầu: `adb shell cmd uimode night` (kỳ vọng `Night mode: yes`; **trả về đúng như vậy ở cuối**), mức kính hiện tại ở Cài đặt (Ép cao).
2. Sao lưu dữ liệu app (theo cách của audit và `lessons/drag-and-layout.md` mục GP): `cmd /c "adb exec-out run-as gambi.global.os26launcher tar cf - databases files no_backup > %TEMP%\fx2-backup.tar"`, ghi md5 của các file (8 file) để so khi trả. Cuối bài kiểm: so lại md5, nếu lệch (chỉ có thể do C13) thì trả từ bản sao lưu.
3. Đẩy script đo lên máy một lần, nội dung `ticks.sh` (do điều phối tạo ngoài repo):

```sh
#!/system/bin/sh
# usage: sh ticks.sh <giay>   (1 tick = 10 ms CPU, utime+stime, mọi luồng của process)
PKG=gambi.global.os26launcher
PID=$(pidof $PKG) || { echo "no pid"; exit 1; }
tot() { set -- $(sed 's/^.*) //' /proc/$PID/stat); echo $(( ${12} + ${13} )); }
snap() {
  for t in /proc/$PID/task/*; do
    set -- $(sed 's/^.*) //' $t/stat 2>/dev/null)
    [ -n "$1" ] && echo "${t##*/} $(tr ' ' '_' < $t/comm) $(( ${12} + ${13} ))"
  done
}
a=$(tot); snap > /data/local/tmp/ticks_t0.txt
sleep $1
b=$(tot); snap > /data/local/tmp/ticks_t1.txt
echo "TOTAL_TICKS $((b - a)) in $1 s"
```

   `adb push ticks.sh /data/local/tmp/ticks.sh`; chạy: `adb shell sh /data/local/tmp/ticks.sh 10`. Bảng theo luồng: `adb shell cat /data/local/tmp/ticks_t0.txt` và `ticks_t1.txt`, lấy hiệu theo `tid` trên máy tính (5 luồng lớn nhất: `main`, `RenderThread`, `GlassBlur`, …).
4. Vào edit mode: nhấn giữ khe trống giữa hai ô như audit A2 (một lệnh `adb shell` chứa `input motionevent DOWN`, `sleep 1.2`, `MOVE` nhẹ, `MOVE` về, `UP`, vì hai lệnh tách rời có thể tới sau mốc nhấn giữ, `lessons/drag-and-layout.md` mục GP). Xác nhận bằng ảnh chụp (`adb exec-out screencap -p`) thấy thanh nút Cài đặt/Tiện ích/Xong. `uiautomator dump` không đọc được khi icon rung (`lessons/home-pager-animation.md` mục R1): dùng ảnh.
5. Bộ ba lệnh dùng nhiều lần: `adb shell dumpsys meminfo gambi.global.os26launcher | Select-String "Activities|Native Heap|TOTAL"`; `adb shell am force-stop gambi.global.os26launcher` + `adb shell input keyevent KEYCODE_HOME` (mở lại Home); `adb install -r <apk>`.

### B. Baseline trên `main` (`b1af810`, một lượt, xác nhận lỗi còn tái hiện ở trạng thái launcher mặc định)

Vào edit mode, vuốt trang 2 rồi trang 1, "Xong", `ticks.sh 30` (kỳ vọng ≈ 250 như audit); đổi sáng/tối hai lần **trong** edit mode: `Activities: 3`. Trả sáng/tối `yes`, `force-stop`.

### C–H. Sau khi sửa (APK nhánh, theo thứ tự thao tác tự nhiên)

| # | Thao tác | Kỳ vọng sau sửa | Trước sửa (audit) |
|---|---|---|---|
| 1 | Home rảnh, `ticks.sh 15` | ≤ 10 tick | 5 |
| 2 | Launcher ở nền (Settings phía trước), không edit mode, `ticks.sh 60` | ≤ 5 tick | 2 |
| 3 | Edit mode, đang rung, `ticks.sh 10` | Task 1: ≈ bản `main` (±15%, không thụt lùi); Task 2: số quyết định | 501 |
| 4 | Edit mode → vuốt trang 2 → trang 1 → "Xong", `ticks.sh 30` | ≤ 10 | 253 |
| 5 | Như 4 rồi `KEYCODE_HOME`, `ticks.sh 30` | ≤ 10 | 247 |
| 6 | Edit mode → `am start -a android.settings.SETTINGS` → `ticks.sh 30` (Task 1: edit mode còn; sau Task 4: edit mode đã thoát) | ≤ 10 | không đo |
| 7 | Edit mode → đổi sáng/tối `no` rồi `yes` → `dumpsys meminfo` | `Activities: 1`, Native Heap tăng ≤ 10 MB; Home còn ở edit mode | `Activities: 3`, PSS 151 → 163 MB |
| 8 | Edit mode → `KEYCODE_HOME` | Edit mode tắt, thanh trạng thái hiện lại | Không đổi |
| 9 | Edit mode → "Tiện ích" → `KEYCODE_HOME` | Bảng đóng, edit mode tắt | Bảng còn mở |
| 10 | Bấm widget thời tiết → `KEYCODE_HOME` | Bảng đóng | Còn mở |
| 11 | Edit mode → "Tiện ích": ảnh chụp | Thanh trạng thái không hiện, thanh nút không đè | Hiện, đè nút |
| 12 | Gõ tên thư mục dở → đổi sáng/tối | Thư mục mở lại với tên đang gõ | Tên cũ |
| 13 | Edit mode → Recents → quay lại | Edit mode tắt | Còn, rung ở nền |

Hồi quy: kéo-thả icon giữa hai trang và vào dock (rung đúng, icon không tàng hình, nhất là sau khi thả: pha rung không giật về 0); mở/đóng thư mục trong edit mode (icon trong panel rung rồi dừng); thêm widget từ bảng bằng bấm và bằng kéo; HB (Home đóng menu, tìm kiếm, Library, thư mục); vuốt trang bình thường sau khi thoát edit mode (không có icon nghiêng); `Activities` sau 5 lần vào/thoát edit mode vẫn là 1.

Cuối bài kiểm: `cmd uimode night yes`, mức kính như ban đầu, tên thư mục như ban đầu, md5 khớp bản sao lưu, `force-stop`, cài lại APK debug của chủ dự án nếu máy từng cài APK khác.

**Kết quả kiểm chứng (Vsmart Live 4, 2026-11-11, `fix/fx2-edit-mode-home`@8c597da cho Task 3–6; Task 1–2 đo ở f38a93d và a1fca8b; bản debug, Os26 là launcher mặc định, kính Ép cao):**

| Mục | Kết quả |
|---|---|
| Task 1 (A2), 12:34–12:42 | Sau edit mode + vuốt trang 2/1 + thoát: 4 tick/30 s (trước 253). Edit mode + Settings ở trước: 16 tick/30 s (trước 247). Đổi sáng/tối hai lần trong edit mode: `Activities: 1` (trước 3), Native Heap 49 MB. Home rảnh 0 tick/15 s. Đang rung 384 tick/10 s |
| Task 2 (C16), A/B xen kẽ 10 s, pin 31,1 → 31,9 °C | A (animator từng ô) 439, 428, 407, trung vị 428; B (ticker) 389, 435, 428, trung vị 428. Không đạt luật #3 → bỏ ticker (revert 3905814) |
| Home rảnh | 0 tick/15 s |
| C19 | Edit mode + "Tiện ích": thanh trạng thái không đè lên "Cài đặt/Tiện ích" (ảnh chụp); sau Back thanh trạng thái vẫn ẩn, thanh nút edit mode vẫn hiện |
| B10, Home | Edit mode + `KEYCODE_HOME`: thanh nút mất, thanh trạng thái hiện lại; 4 tick/15 s |
| B10, rời launcher | Edit mode + Settings ở trước: 23 tick trong 30 s đầu (việc thoát edit mode), sau đó 0 tick/30 s ổn định; về bằng Home: không ở edit mode. Edit mode + Gần đây → Home: không ở edit mode |
| Guard `isChangingConfigurations` | Edit mode + `uimode night no` rồi `yes`: Home vẫn ở edit mode sau mỗi lần dựng lại; `Activities: 1`, Native Heap 47 MB |
| C13 | Gõ tên thư mục `X2t`, `uimode night no`: thư mục mở lại với `X2t`; sau `force-stop` DB hàng id 90 có title `X2t` |
| logcat | Không có FATAL |
| Chưa chạy trên máy | Bảng thời tiết + Home (chủ dự án không có widget thời tiết trên Home), kéo-thả icon rồi bấm Home ngay, kéo thẻ widget từ bảng chọn: **đọc code / chủ dự án kiểm khi duyệt PR** |

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-11 | Plan | Viết plan, đối chiếu code `b1af810` | — | Chờ điều phối rà và chủ dự án duyệt; câu hỏi mở Q1–Q4 ở "Quyết định đã chốt" |
| 2026-11-11 | Plan | Điều phối duyệt plan; Q1–Q4 giữ mặc định; giữ/bỏ ticker (Task 2) do điều phối quyết theo số đo | f99a274 (main) | — |
| 2026-11-11 | 1 | `Jiggler(host).sync`, nối vào `IconGridView`, `DockView`, `AppsGrid`; build xanh | f38a93d | Chờ điều phối kiểm Step 6 (a)–(e); APK `apk\FX2-task1.apk` |
| 2026-11-11 | 2 (Step 1–3) | Ticker `Choreographer` mỗi `Jiggler`; build xanh; APK đo `FX2-task1.apk`, `FX2-ticker.apk` và APK có log `JIG` (`FX2-task1-jig.apk`, `FX2-ticker-jig.apk`; log tạm, không commit) | a1fca8b | Dừng chờ số đo A/B (Step 4) và quyết định giữ/bỏ |
| 2026-11-11 | 1 (kiểm) | Điều phối kiểm Live 4 (debug, 12:34–12:42): (a) 4 tick/30 s (trước: 253); (b) Settings ở trước 16 tick/30 s (trước: 247; dưới ngưỡng 20 nên cổng thấy `Activity` dừng, không cần dự phòng Task 4); (c) `Activities: 1` (trước: 3), Native Heap 49 MB; (e) Home rảnh 0 tick/15 s; rung 384 tick/10 s | f38a93d | Đạt |
| 2026-11-11 | 2 (Step 4) | A/B xen kẽ, 10 s, pin 31,1→31,9 °C: A(task1, animator từng ô) 439, 428, 407, trung vị 428; B(ticker) 389, 435, 428, trung vị 428. Luật quyết định #3 không đạt (cần B ≤ 0,85×A và max(B) < min(A)) → **bỏ ticker** bằng commit revert. C16 đóng với kết luận "đã đo, animator từng ô không phải chi phí chính; nhiều khả năng ở RenderThread/vẽ lại kính". Pha giữ qua `setItems` (Q4) nằm trong commit bị revert nên bỏ theo | a1fca8b, revert 3905814 | Quyết định bằng số đo; log `JIG` chưa chạy vì ticker bị bỏ |
| 2026-11-11 | 3 | `closeIfOpen()` cho bảng widget và bảng thời tiết; `onNewIntent` thêm `exitEditMode()` + hai lời gọi đóng; build xanh | 5f8cbcf | Chờ review và kiểm Step 4 (a)–(e) |
| 2026-11-11 | 4 | `onStop`: `if (!isChangingConfigurations) viewModel.exitEditMode()`; build xanh | 038919f | Chờ kiểm Step 4; Step 2 dự phòng chưa cần (Task 1 (b) = 16 tick/30 s) trừ khi kiểm (a) trượt |
| 2026-11-11 | 5 | `onStop`: `binding.folderPanel.commitTitle()` trước khi thoát edit mode; build xanh | 6c926d4 | Chờ kiểm Step 3 (cần sao lưu DB) |
| 2026-11-11 | 6 | `SystemBarsController.hideStatusBarOf(window)`; `WidgetPickerController(…, hideStatusBar)` gọi sau `show()`; nối ở `HomeActivity`; build xanh | 8c597da | Chờ kiểm Step 4 bằng ảnh chụp |
| 2026-11-11 | 7 (nháp) | Knowledgement `docs/knowledgements/FX2-edit-mode-home.md` (Mermaid, số đo Task 1–2; số Task 3–6 chờ bài kiểm cuối) | (commit docs) | Chờ điền số đo cuối |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Mọi đường dừng rung qua `Jiggler.sync(false, …)` | `grep "jiggler\.\(start\|stop\)"` ngoài `Jiggler.kt` rỗng; đo: 4 tick/30 s sau thoát edit mode, `Activities: 1` | Đạt (đo được) |
| 2 | Gắn lại từ cache rung lại; pool không rung | `onAttachedToWindow` gọi `syncJiggle()`; 4 tick sau vuốt trang 2/1 và thoát. Chưa có ảnh riêng cho "gắn lại từ cache còn rung" | Đạt (đọc code + đo CPU) |
| 3 | Lật trang/FakeDrag không làm mất rung | Trang hiện luôn thỏa `isShown`; không có bước đo riêng | Đọc code; chủ dự án kiểm khi duyệt PR |
| 4 | `onStop`: thứ tự, `isChangingConfigurations` | Lưu tên → thoát edit mode → `super.onStop()`; đo: Home vẫn ở edit mode sau hai lần `uimode`, thoát khi Settings/Gần đây | Đạt (đo được) |
| 5 | `closeIfOpen()` an toàn gọi hai lần, hủy phiên kéo | Bảng widget: `dialog?.let { cancelDragOf; dismiss }` rồi `null`; bảng thời tiết `dialog?.dismiss()`. Không crash ở lần thử Home; thẻ widget đang kéo và bảng thời tiết chưa thử trên máy | Đọc code; chủ dự án kiểm khi duyệt PR |
| 6 | Ẩn thanh trạng thái của bảng không làm lệch trạng thái/inset | Ảnh chụp: không đè nút; sau Back thanh vẫn ẩn, thanh nút vẫn hiện | Đạt (đo được) |
| 7 | Không file ngoài vùng; không log tạm | `git diff origin/main --stat -- app` chỉ 8 file, đều trong vùng; không còn log tag `JIG` (`grep -w JIG app/src` rỗng; các dòng khớp `WIDGET_JIGGLE_SCALE` là hằng cũ); log tạm chỉ ở APK `*-jig.apk`, không commit | Đạt |
| 8 | Ticker: công thức khớp, gỡ callback, quyết định kèm số | Ticker bị bỏ theo số đo (428 so 428 tick/10 s); `Jiggler.kt` về animator từng ô, không còn `Choreographer` | Đạt (quyết định bằng số đo) |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: mục edit mode (TT#7) thêm dòng "nút Home và rời launcher (`onStop`, trừ khi dựng lại do đổi cấu hình) thoát edit mode; bảng chọn widget và bảng thời tiết đóng khi bấm Home" (quyết định chủ dự án 2026-11-11).
- `project_structure.md`: dòng `Jiggler.kt` ghi "một cổng `sync` (gắn + hiện + edit mode)" (ticker `Choreographer` đã thử và bỏ, xem knowledgement mục 5); dòng `WidgetPickerController` ghi `closeIfOpen`/`hideStatusBar`.
- `room-schema.md`: không.
- `AGENTS.md` / `lessons/` (đưa vào `home-pager-animation.md` và `glass.md`; điều phối chép từ knowledgement):
  - HB mở rộng: lớp phủ tạm đóng cả ở Back và Home; **edit mode cũng là lớp tạm**, thoát cả ở Home và `onStop`.
  - Animator vô hạn trên view phải gắn vào vòng đời hiển thị của view (gắn + hiện), không chỉ vào "bật/tắt tính năng": trang trong cache của `RecyclerView` và Activity bị hủy đều làm animator mồ côi (đo được trên Live 4: 8% một nhân, `Activities: 3`).
  - `notifyItemRangeChanged` không bind lại khi cửa sổ đã dừng (suy luận từ `ViewRootImpl`, nếu Task 4 xác nhận bằng số đo thì ghi "đo được"): trạng thái cần dừng lúc `onStop` phải đi đường trực tiếp, không qua adapter.
  - Dialog là cửa sổ riêng có `WindowInsetsController` riêng: thanh hệ thống ẩn ở Activity không áp cho dialog.
  - Kết quả đo C16 (giữ hay bỏ ticker, kèm bảng theo luồng) vào `glass.md` cạnh mục GL "chi phí kính nằm ở RenderThread".
  - Tick CPU theo luồng bằng `/proc/<pid>/task/*/stat` (không cần `top`/`simpleperf`) và script `ticks.sh`.
