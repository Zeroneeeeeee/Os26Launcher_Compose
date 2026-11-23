# Bài học: Home, pager và hoạt ảnh

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Launcher mặc định, inset, trang, thư viện ứng dụng, hoạt ảnh, tách controller.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## Launcher mặc định (M1 Task 3)

- **Đường mở màn chọn launcher mặc định (đo 2026-10-16):** cả Oppo A3x (Android 14) và Vsmart Live 4 (Android 11) đều chạy đường 1, `RoleManager.createRequestRoleIntent(ROLE_HOME)`; hai đường dự phòng `ACTION_HOME_SETTINGS` và `ACTION_SETTINGS` chưa máy nào cần dùng. Đo bằng log tạm tag `M1` (`adb logcat -s M1`) trên cả hai máy, đều in `set-default path = RoleManager`.
- **Dòng log chỉ in khi chính `HomeActivity` chạy `onResume`.** Khi launcher gốc của máy đang là mặc định, bấm Home mở launcher gốc, code của mình không chạy nên không có log; đó là bình thường, không phải lỗi. Muốn thử lại từ trạng thái "chưa mặc định", mở app từ icon trong launcher gốc.
- Mở màn xin role bằng `registerForActivityResult(StartActivityForResult())`. Việc `startActivity` thường làm màn này tự đóng là suy luận chưa kiểm chứng (chưa thử), nhưng cách `StartActivityForResult` đã chạy đúng trên cả hai máy.
- Android 11+ cần `<queries>` trong manifest cho 3 action (`android.app.role.action.REQUEST_ROLE`, `android.settings.HOME_SETTINGS`, `android.settings.SETTINGS`) để `resolveActivity()` thấy màn hệ thống. Chưa đo xem thiếu `<queries>` thì có ẩn nút oan hay không (suy luận từ tài liệu package visibility).
- Trạng thái mặc định phải làm mới ở `onResume`, không phải `onCreate` (đã đo: đổi launcher khác rồi quay lại thì dòng trạng thái cập nhật đúng).
- Bấm Back ở màn hình chính khi chưa có callback: ban đầu ghi nhận không thấy gì, nhưng người dùng báo lại trên Oppo A3x (Android 14): đang là launcher mặc định, bấm Home rồi Back thì app như đóng rồi mở lại, lặp mỗi lần Back. Nguyên nhân (suy luận): Back ở Activity gốc kiêm launcher đẩy task ra nền, hệ thống mở lại HOME tức chính app. **Cách xử lý đã kiểm chứng 2026-10-16 trên Oppo A3x và Vsmart Live 4:** `onBackPressedDispatcher.addCallback(this) { }` ở `onCreate`, hết vòng lặp. Mọi launcher đều cần callback rỗng này.
- Hai icon trong danh sách app khi demo còn sống: **"OS26 Launcher"** (`HomeActivity`, giữ HOME) và **"Demo kính"** (`MainActivity`, nhãn từ `demo_activity_label`). Xóa icon thứ hai khi xóa `MainActivity` sau M5.

## Inset và layout màn hình chính (M1 Task 5)

- **Quan sát 2026-10-27 (M3 Task 5, Live 4):** thanh điều hướng hiện màu trắng đặc, không trong suốt/thấy ảnh nền xuyên qua. Không phải quy hồi của M3 — `applySystemBarInsets()` chỉ cộng margin, không đổi window/system bar config. Thuộc đúng phạm vi "đo lại inset, system bar styling" của **M5**, chưa xử lý ở M1-M3. Nghi vấn ban đầu: theme Material3 `DayNight` (sáng) + cách ROM Vinsmart (Android 11) tự vẽ thanh điều hướng khi chưa tùy biến `isNavigationBarContrastEnforced`/scrim — cần đo lại kỹ ở M5, đây là suy luận chưa kiểm chứng.

- Root `FrameLayout` không padding (B2). Inset `systemBars()` áp thành **margin** cho view con (`home_status` top, `btn_set_default` bottom) bằng `ViewCompat.setOnApplyWindowInsetsListener` trên root, trả `insets` nguyên vẹn.
- Margin gốc đọc từ layout một lần rồi mỗi lần cộng inset vào giá trị gốc đó; cộng thẳng vào margin hiện tại sẽ dồn tăng mỗi lần inset đổi.
- `enableEdgeToEdge()` gọi trước `setContentView`; listener đặt sau khi có binding.
- Đo trên Oppo A3x và Vsmart Live 4 (2026-10-16): `home_status` không bị thanh trạng thái che, nút không bị thanh điều hướng che, ảnh nền phủ kín. Xoay màn và sáng/tối thuộc M5.

## "Demo kính" không mở được từ lưới launcher (M3 Task 5, 2026-10-27)

- Bấm icon "Demo kính" (`MainActivity`) từ lưới của chính launcher không phản hồi; nếu launcher đang
  chạy ngầm, bấm icon đó từ nơi khác chỉ đưa launcher lên trước thay vì mở đúng app. **Nguyên nhân:**
  `MainActivity` không khai `android:taskAffinity` nên mặc định trùng affinity (= tên package) với
  Task của `HomeActivity` (`singleTask` + `clearTaskOnLaunch=true`) — hệ thống "nhận nhầm" Task
  launcher đang có sẵn thay vì mở `MainActivity`. **Fix:** `android:taskAffinity=""` cho `.MainActivity`.
  Giải thích đầy đủ cơ chế Task/taskAffinity/launchMode: `docs/knowledgements/android-task-affinity.md`.
- Không phải lỗi mới của M3 — có từ M1, chỉ giờ mới lộ ra vì M3 là lần đầu có lưới icon thật để bấm thử.
  Bài học chung: 2 Activity `LAUNCHER` cùng package luôn phải khai `taskAffinity` tường minh nếu không
  muốn chung Task với nhau.

## M5 — khóa xoay, system bar, polish lưới+dock

- **Sàn kích thước icon (`ICON_MIN_DP = 48dp`) và sàn "ẩn cả lưới nếu quá hẹp" (`cellWidthRaw < 48dp` → `measure()` trả null) là hai việc khác nhau, dù dùng cùng một hằng số từ M3.** Kiểm chứng tay Task 2 trên split-screen (2026-10-27) lộ ra: `cellHeightRaw` co nhỏ dần nhưng icon không co theo được vì bị kẹp cứng ở sàn 48dp (vốn đặt ra cho vùng chạm accessibility ở M3), nên icon tràn ra ngoài ô/đè lên dock — khác hẳn trường hợp `cellWidthRaw` quá hẹp (ẩn cả lưới, hành vi cũ không đổi). Người dùng chọn bỏ sàn accessibility khỏi phép tính kích thước icon, chỉ giữ một sàn kỹ thuật rất nhỏ (`ICON_SAFETY_MIN_DP = 8dp`) để icon không co về đúng 0px sau khi làm tròn theo `ROUND_UNIT_DP` (tránh crash `Bitmap.createBitmap`/`createScaledBitmap` với kích thước ≤ 0 — xem `IconLoader.load()`). Bài học: khi một hằng số dùng cho nhiều mục đích khác nhau (accessibility vs. chống crash), đổi hằng số cho một mục đích phải tách biệt khỏi mục đích còn lại bằng hằng số riêng, không gộp chung — nếu không sẽ phải đoán lại ý nghĩa mỗi lần đổi.

- **Ô con của `IconGridView` phải bind lại khi `Cells` đổi, không chỉ lần đo đầu (M5 Task 2, 2026-10-27, đo trên Live 4).** Triệu chứng: kéo thanh chia split-screen thì pitch hàng co đúng nhưng icon lưới đứng nguyên cỡ, trong khi icon dock co đúng. Nguyên nhân (đọc code, rồi đã xác nhận bằng test): `IconGridView.onMeasure` cập nhật `cells` mỗi lần đo nên `onLayout` đặt vị trí đúng, nhưng `bindAllCells` chỉ chạy khi `needsBind` (lần đầu), nên `IconCellView` giữ `Cells` cũ (iconSize, cellWidth, nhãn) và bitmap nạp theo size cũ. Dock không bị vì listener ở `HomeActivity` gọi `dockView.setItems` (bind lại) mỗi lần `home_root` đổi kích thước. Sửa: `if (needsBind || measured != previous) bindAllCells(measured)` — `Cells` là data class nên so theo giá trị, không bind thừa, không lặp vô hạn qua `requestLayout`. Nguyên tắc chung: view con giữ dữ liệu suy ra từ kích thước (cell, icon size) thì phải cập nhật khi kích thước cha đổi, đừng chỉ bind ở lần đầu.

- **System bar tràn viền (M5 Task 3, 2026-10-27, người dùng xác nhận trên Live 4, Android 11):** quan sát cũ "thanh điều hướng trắng đặc" ở mục M1 Task 5 đã xử lý bằng `applySystemBarStyling()` trong `HomeActivity`: tắt `isNavigationBarContrastEnforced`/`isStatusBarContrastEnforced` (chỉ API 29+, có nhánh `SDK_INT >= Q`) và đặt `isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` theo `uiMode`. Đổi theme làm Activity tái tạo nên hàm tự chạy lại. Giả thuyết ROM Vinsmart tự vẽ nền riêng không xảy ra. Giới hạn đã biết: màu icon theo theme hệ thống chứ không theo độ sáng ảnh nền.

- **Dock có hàng riêng, không suy chiều cao dock từ `Cells` (M5 Task 4, 2026-10-27, người dùng xác nhận trên Live 4).** Plan gốc chia 6:1 giữa pager và `bottom_bar` (gói indicator + dock) với chiều cao dock = `cellHeight` suy ra từ pager: chiều cao `bottom_bar` do layout quyết định còn nội dung lại đòi `cellHeight` + indicator 6dp + 12dp + margin 16dp + inset, nên dock tràn lên hàng 6 khoảng 25dp (tính từ code, chưa đo trên máy vì phương án bị thay trước khi code). Cách đúng: `LinearLayout` dọc, pager weight 6 (= `DEFAULT_ROWS`), dock weight 1; chiều cao dock do layout cấp, icon dùng `Cells` của pager và nằm giữa dock (thừa khoảng 9dp do lưới có 56dp padding + gutter chia cho 6 hàng) nên không bao giờ tràn. Nguyên tắc: không để kích thước của view A suy ra từ Cells trong khi layout lại quyết định kích thước của A.
- **Ẩn pager bằng `INVISIBLE`, không `GONE`, nếu một view khác đo kích thước từ nó (M5 Task 4).** Bài học M3 Task 5 (dock khoá 0×0 vì `home_pager` bị `GONE` lúc Loading) trước đây được né bằng cách đo theo `home_root`; giờ pager/indicator/dock ẩn bằng `INVISIBLE` nên pager giữ kích thước đã layout, `Cells` của dock đo thẳng từ `home_pager` (cùng công thức với `IconGridView`, khớp icon dock với icon lưới) và listener đổi kích thước đặt trên `home_pager`. `INVISIBLE` vẫn được measure/layout, không nhận chạm.
- **Căn giữa dock theo nội dung thật, không theo hộp ô (M5 Task 4).** Hộp ô (`cellHeight`) luôn chừa chỗ cho nhãn 2 dòng nên căn theo hộp sẽ lệch lên trên khi nhãn 1 dòng. `IconCellView.contentHeight()` (icon + khoảng cách + chiều cao `StaticLayout`) cho `DockView.onLayout` lấy max rồi căn giữa; theo chiều ngang nhóm n ô nằm giữa với mọi n 0–4, nền dock giữ nguyên chiều rộng (A10) và dock trống vẫn là viên rỗng. Ràng buộc: `home_dock_margin_horizontal` <= `GridMetrics.PADDING_DP` (8dp) để dock đủ 4 icon còn thẳng cột với lưới. **Chưa thử tay phần 4 icon** (Chrome rời dock từ M4, dock đang 3 icon): đủ 4 icon thẳng cột mới chỉ là suy luận từ công thức; kiểm lại khi dock có lại đủ 4 icon.

## M7 Task 9 — Thư viện ứng dụng: bài học triển khai

- **Đổi thiết kế giữa chừng thì ghi vào plan ngay** (list có header -> 2 tab loại trừ nhau): ngữ nghĩa mục "Tất cả" đổi thành
  "Chưa ẩn", Step kiểm chứng phải đọc theo nghĩa mới.
- **Trạng thái UI phải sống qua tạo lại Activity (đổi theme) thì để ở ViewModel** (tab đang chọn), không ở view.
- **Tab dựng từ dữ liệu:** chỉ dựng lại khi danh sách mục đổi; `select()` chương trình vẫn bắn listener nên handler phải idempotent.
- **ViewModel cần chuỗi hiển thị thì dùng `@ApplicationContext` + string resource**, model domain giữ `id` ổn định để UI
  quyết định hành vi (không so tiêu đề đã dịch).
- **Danh sách có icon nạp async trong Holder:** mỗi Holder 1 Job, huỷ khi recycle/bind lại, kiểm key trước khi gán; tắt
  `itemAnimator` khi hàng biến mất liên tục để không nháy.

## G2a Phần B — App Library (B2): bài học (2026-10-30)

- **Màn không phải lưới thì đừng làm một mục của `ViewPager2` lưới.** Bản đầu gắn Library làm trang cuối của pager và phải vá liên tiếp: view type thứ hai, `areItemsTheSame` theo `index` làm Library bị coi là trang lưới mới khi thêm trang, ẩn dock + pill bằng GONE làm pager nở ra nên `IconGridView` tính lại Cells, rồi lại thêm trần chiều cao. Hướng đúng (mẫu `LauncherOverlay` của Launcher3): lớp phủ ngoài pager, một `progress` 0..1 điều khiển trượt/tối nền/mờ Home. Bài học chung: nếu phải vá lần thứ hai cho cùng một lựa chọn kiến trúc thì dừng và đề xuất phương án khác cho người dùng.
- **Cử chỉ tiếp quản trong `dispatchTouchEvent`:** quan sát `DOWN` để lập "ứng viên" (đủ điều kiện), `MOVE` mới quyết (ngang trội + đúng chiều + vượt slop; dọc trội thì bỏ để cuộn/tìm kiếm xử lý; sai chiều thì bỏ để pager lật trang), lúc tiếp quản gửi MỘT `ACTION_CANCEL` giả rồi nuốt phần còn lại; `UP` dùng `VelocityTracker` (fling hoặc quá nửa quãng). Dùng `rawX/rawY` vì view con dịch chuyển.
- **Trạng thái ổn định ở ViewModel, tiến độ giữa chừng ở view:** `libraryOpen` sống qua `recreate()`; render đầu sau recreate dựng thẳng ở trạng thái đã lưu không animation; đang kéo/animate thì `render` để yên, cử chỉ kết thúc tự báo lại qua `onSettled` (tránh vòng lặp state ↔ animation).
- **Thứ tự Back:** `OnBackPressedCallback` đăng ký sau được ưu tiên trước. Callback mới thuộc lớp "nền" (đóng Library) đăng ký TRƯỚC những callback đã có.
- **`@dimen` chỉ gán được cho thuộc tính nhận dimension** (kích thước, padding, margin, `textSize`). Thuộc tính float (`android:shadowRadius`, `alpha`, `layout_weight`) không nhận tài nguyên `<dimen>`: AAPT báo lỗi kiểu không tương thích. Dùng số thẳng hoặc đặt bằng code (`setShadowLayer(resources.getDimension(...), ...)`).
- **Tính cỡ icon nhỏ theo bề rộng thật:** `render()` có thể chạy trước khi view đo xong; dựng ô khi `width > 0` và dựng lại trong `onSizeChanged`.
- **Phân loại app:** xem mục B1 ở trên (category do nhà phát triển khai báo, app tự cài thường là UNDEFINED).
- **Panel chỉ đọc không nên tái dùng panel có tương tác (G2a B3):** `FolderPanelView` gắn chặt với kéo-thả, sửa tên, dấu "−", `DragDropTarget`; bọc chế độ "chỉ đọc" lên đó khó hơn viết view nhỏ (`CategoryGroupPanelView`: scrim + khung + `RecyclerView` lưới 3 cột). Dùng chung tài nguyên nhìn (`folder_scrim`, `bg_folder_panel`, dimens) là đủ để hai panel giống nhau và áp kính một lần. `RecyclerView` có trần chiều cao (ghi đè `onMeasure` với `AT_MOST`) cho panel ôm nội dung khi ít, cuộn khi nhiều.
- **Panel dẫn xuất từ trạng thái, không lưu trong view (G2a B3):** `openCategoryGroup` ở `HomeViewModel` + danh sách nhóm hiện có; nhóm hết app thì đóng panel (cùng mẫu `renderFolderPanel`). Đóng Library xóa luôn `openCategoryGroup` trong cùng một lần cập nhật state để không để panel mồ côi.

## Demo polish (2026-10-30): bài học

- **Chừa chỗ cho thanh công cụ nổi bằng margin của `home_content` làm Cells của lưới đổi:** `IconGridView` và ô dock tính cỡ từ chiều cao pager, nên đẩy `home_content` xuống ~64dp lúc edit mode làm icon nhỏ đi một chút và bind lại khi vào/ra edit mode. Chấp nhận cho bản demo; nếu thấy giật, thay bằng chừa sẵn vùng đệm cố định ở đỉnh lưới ngay từ đầu (không phụ thuộc edit mode).
- **Gỡ một mục khỏi màn có nhiều mục thì dọn cả dây phụ thuộc:** bỏ tab ở "Ứng dụng đã ẩn" kéo theo bỏ trạng thái tab ở ViewModel, `SavedStateHandle`/extra mở sẵn tab ở Cài đặt, hai chuỗi và nhánh "rỗng" của mục kia ở adapter. Sau khi bỏ, grep tên chuỗi/id đã xóa để chắc không còn tham chiếu (build sẽ vỡ nếu sót).
- **Dùng một màu nền tối cho mọi lớp phủ modal:** `folder_scrim` trỏ `@color/search_scrim` (#CC000000) để panel thư mục, panel nhóm và lớp tìm kiếm cùng độ tối; đổi một chỗ là đổi cả ba.

- Demo polish #2–#4 (icon/nhãn/dấu/dock, 2026-10-30):
  - Nhãn icon (app/thư mục, cả panel thư mục) tối đa 1 dòng (`IconCellView.MAX_LABEL_LINES`). Dock ép `Cells.copy(showLabels = false)` trong `HomeActivity.updateDockCells()`: cùng cỡ icon với lưới nhưng không nhãn và không bị cài đặt "hiển thị nhãn" tác động.
  - Cỡ icon: `GridMetrics.measure` = min(`ICON_MAX_DP`=96, bề rộng ô, chiều cao còn lại) × `ICON_SCALE`; `LABEL_HEIGHT_DP` 32→20 vì nhãn 1 dòng. Bề rộng nhãn = bề rộng ô × `IconCellView.LABEL_WIDTH_RATIO`, căn giữa dưới icon.
  - Dấu "−": cạnh = iconSize × `BADGE_RATIO`, tâm trùng góc trên-trái icon (cũ: 24dp cố định, lệch xuống). Nửa dấu nhô ra ngoài ô nên phải `clipChildren=false` cả chuỗi cha (home_content, ViewPager2, RecyclerView bên trong, IconGridView, DockView, panel thư mục); vùng nhô ra không nhận chạm. `dimen icon_badge_size` đã bỏ.
  - Kính dock ôm icon: cao = iconSize + 2×`GLASS_PADDING_VERTICAL_DP` (12), căn giữa trong DockView (`glassHeight()`); vùng dock vẫn giữ chiều cao cũ (chưa nhường chỗ cho thành phần khác).
  - Nút Cài đặt/Xong ở edit mode: `backgroundTint=@color/search_scrim`, chữ trắng. **TL1 (2026-11-03) đã thay:** hai pill nổi 28dp (hộp chạm 48dp), ẩn thanh trạng thái khi chỉnh sửa, bỏ `home_edit_toolbar_space` (không còn đẩy lưới 64dp), `topReserve` cố định.
  - Hằng số chỉnh tay (giá trị lúc commit): `ICON_SCALE` 0.75 (đổi 0.9 ở TL2, 2026-11-03), `BADGE_RATIO` 0.5, `LABEL_WIDTH_RATIO` 1.0 (người dùng tự chỉnh), `LABEL_HEIGHT_DP` 20, `GLASS_PADDING_VERTICAL_DP` 12.
  - Đã trả nợ (R1, 2026-11-04): `HomeActivity` 962 → ~200 dòng, xem mục R1 cuối file.
- Home không còn trạng thái "chưa có ảnh nền" (chữ + nút Chọn ảnh nền): việc chọn ảnh nằm ở Cài đặt. Khi chưa có ảnh/đọc lỗi, Home chỉ không vẽ nền. `HomeViewModel.onImagePicked` không còn nơi gọi (dư, chờ dọn).

## TL1 — vuốt trang, rung, nút chỉnh sửa: bài học (2026-11-03, Vsmart Live 4)

- `ViewPager2` có ba đường đổi trang với thời lượng khác nhau: fling (`PagerSnapHelper`, ≤ ~300 ms), nhấc tay chậm (`smoothScrollBy` mặc định, `(|d|/rộng+1)×300` ms, quintic), `setCurrentItem(.., true)` (phụ thuộc dpi). Không có API công khai để chỉnh; muốn chỉnh fling thì đặt `onFlingListener = null` rồi gắn `SnapHelper` riêng (helper cũ trong field private vẫn nghe IDLE, vô hại khi đã thẳng hàng). Đọc bytecode.
- `ScrollEventAdapter` chỉ phát `onPageSelected` cho cuộn bắt đầu từ kéo, `setCurrentItem` hoặc FakeDrag; `smoothScrollBy` thẳng từ trạng thái rảnh thì không. Lật có chủ đích dùng FakeDrag; trong lúc kéo giả `currentItem` vẫn là trang cũ, nên mã đặt lại trang không animation phải bỏ qua khi đang lật.
- **Kính thật (`LiquidGlassView`) tự `invalidate()` trên mọi `onScrollChanged` của cửa sổ** (listener đăng ký bất kể `enableDynamicBackground`) và chụp lại nền trên CPU ở mỗi lần vẽ: dock kính thật làm vuốt trang giật (Janky 72,87%, đo). `RealGlassSurface.setInvalidationSuppressed` chặn trong lúc pager cuộn (Janky 1,76–1,97%). Mọi phần tử kính thật mới phải dùng cùng cách chặn. Kính thật là mặc định trên tier LENS (API 33+) và khi `glass_level_override = FORCE_HIGH` (Live 4 của chủ dự án đang đặt như vậy).
- Phương pháp đo khi "không mượt": `dumpsys gfxinfo <pkg> framestats` tách giai đoạn (`DrawStart→SyncQueued` = ghi display list luồng UI); perfetto với `atrace_apps` (atrace cũ lỗi "record-tgid: Out of memory" trên Live 4); simpleperf `--app -g`. Ngưỡng 2,13% của M6 là cảnh đứng yên, không dùng cho cảnh vuốt.
- Pill nổi: hộp chạm ≥ 48dp, nền nhỏ hơn bằng `insetTop/insetBottom`, `paddingVertical=0` để chữ (dấu tiếng Việt, chữ g) không bị cắt; hộp chạm không được chồng dấu "−" hàng đầu nên `topReserve = max(0, 58dp − bars.top)`. Ẩn thanh trạng thái thì lấy inset bằng `getInsetsIgnoringVisibility`, nếu không nội dung sẽ dịch.
- Lỗi có từ G2a: `LibraryOverlayController` chỉ hỏi `canOpen()` lúc DOWN; nhấn giữ icon ở cột phải trang cuối rồi kéo bị Library cướp. Sửa: hỏi lại lúc quyết định kéo.

## R1 — tách `HomeActivity`: bài học (2026-11-04, Vsmart Live 4)

- Các mục cũ trong file này nhắc hàm của `HomeActivity`; sau R1 chúng ở `ui/home/controller/`: `setupHomePager` → `HomePagerController`, `updateDockCells` → `DockController`, `dispatchTouchEvent` → `HomeTouchRouter`, `pendingMenuDrag` → `IconMenuController`, Back → `HomeBackController`, inset và thanh trạng thái → `SystemBarsController`, kiểm tạo lại khi đổi cài đặt → `RecreateGuard`.
- Controller là lớp Kotlin thường, nối ở `onCreate`; `HomeActivity` giữ các thứ tự là hành vi: `render()`, chạm, đăng ký Back (LIFO), dọn ở `onDestroy`. Sai thứ tự vẫn build xanh.
- Hồi quy khi tách: chạy cùng một chuỗi bước trên `main` và trên nhánh **từ cùng trạng thái bố cục** rồi so ảnh từng bước. Bước kéo-thả làm đổi bố cục, nên lượt đầu có thể lệch cả chuỗi; chạy lại `main` sau khi bố cục ổn định.
- Công cụ đo: giữ đứng yên bằng `input motionevent` không đáng tin (`main` 0/8 mở menu); thêm `MOVE` 1 px giữa lúc giữ thì 6/6. `uiautomator dump` không đọc được màn hình khi icon đang rung. `input keyevent HOME` mở launcher mặc định của máy.
- **Lỗi dock 0x0 có từ trước R1:** `recreate()` khiến dock có lúc thu về 0x0 và kẹt tới khi khởi động lại process (thấy 2/4 app, không kính). Dùng `dumpsys activity top` lúc lỗi để đọc bounds thật thay vì đoán. Sửa ở AN Task 9f.

## AN — hoạt ảnh và đo panel: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x)

- **Thời gian ghi display list thấp thì đừng tối ưu phía main thread (đo được):** panel nhóm trên Live 4 có `rec` ~0.4 ms nhưng p95 khung ~35 ms; lớp phần cứng cho danh sách, cho Library, hay cả hai đều không giảm được. Chi phí nằm ở phía vẽ; nguyên nhân chưa tìm ra, các nghi phạm ghi ở plan AN Task 9h.
- **Đo khi thêm hoạt ảnh thì so số khung tuyệt đối, không so %:** bản không có hoạt ảnh ít khung hơn nhiều (panel nhóm Oppo 234 so với 1200), nên % janky của bản mới thấp hơn dù số khung chậm tăng gấp đôi. Ghi cả ba: tổng khung, số khung > 33 ms, p95.
- **So nhiều cấu hình thì xen kẽ và lặp cấu hình gốc giữa chừng (đo được):** một lượt đo 0 rồi 1 tưởng 1 tệ hơn; chạy 2, 0, 3, 0 thì lộ ra máy nóng dần và các giá trị nằm trong cùng dải.
- **Trước khi chạy một lượt đo dài, hỏi chủ dự án có thấy vấn đề bằng mắt không:** panel nhóm "không đạt" theo số nhưng chủ dự án thấy đủ mượt; câu hỏi đó lẽ ra phải đặt trước khi làm công tắc đo.
- **`screenrecord` bị segfault trên Oppo A3x (đo được, rc=139); `screencap` trên máy này quá chậm để bắt spring** (khung thứ ba đã hết spring). Live 4 quay được, dựng khung bằng `ffmpeg -fps_mode passthrough`; `tblend=all_mode=difference` + `signalstats` cho mức đổi từng khung, ảnh hiệu khuếch đại cho biết đổi ở kính hay ở chữ/icon.
- **Số đo một vùng chưa nói đổi ở đâu:** bước đổi lớn ở khung spring cuối trên Live 4 trông giống lỗi kính, nhưng ảnh hiệu cho thấy chỉ là viền icon, chữ và khung; phần trong kính không đổi. Xem ảnh hiệu trước khi kết luận.
- **Kiểm foreground dùng `grep -E 'topResumedActivity|mResumedActivity'`:** Oppo (API 34) không có dòng `mResumedActivity`, chỉ grep dòng đó thì script tự dừng dù app đang ở trước.
- **Đừng đếm số lần vuốt để tới một màn:** số trang đổi và vuốt có thể rơi mất. Vuốt rồi dò `uiautomator dump` tới khi thấy view cần tìm, có trần số lần.
- **Công tắc đo tạm không được vào `main`:** khi bỏ, trả `app/` về đúng commit đã kiểm rồi so `git diff <commit> HEAD -- app` phải rỗng. Với Rebase and merge, commit tạm đã push vẫn đi vào lịch sử nếu không gỡ khỏi nhánh trước.

## SM — hoạt ảnh kết thúc bằng callback ẩn view (tách từ «DD, SM, DG — app mặc định ở dock, menu icon kính, kính dock khi đổi nền (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Hoạt ảnh kết thúc bằng callback ẩn view:** mọi đường "đặt về 0 rồi hiện lại" phải snap TRƯỚC khi đặt `isVisible = true`.

## AP — App Library: vuốt, nhóm kiểu iOS 27, tràn dưới thanh điều hướng (2026-11-10, Oppo A3x + Vsmart Live 4)

- **Đề bài nói "trang của pager" thì kiểm trước xem có đúng là trang không:** App Library là lớp phủ ngoài pager (G2a D17), `PageTransformer` không áp dụng; hiệu ứng vuốt sửa ở `AppLibraryController.applyLibraryProgress`.
- **Một view có nhiều nguồn chuyển động (vuốt và panel) thì gộp ở MỘT hàm ghi thuộc tính** (`applyLibraryLayer`) và lưu từng tiến độ riêng.
- **Vuốt trang Home cuối ↔ Library là trượt cả hai, không mờ** (video tech lead): dịch cả `home_content` (trang, pill, dock); `AppLibraryMotion.SWIPE_FADES = false`.
- **Nhóm Library mở toàn màn hình như iOS 27** (không thẻ kính): tiêu đề lớn trái trên, lưới 4 cột có nhãn, nền mờ/tối; mọc ra từ ô vừa bấm (`tileScreenRect`) ~0,23-0,27 s; Library phía sau co nhẹ (0,85), mờ và blur (`RenderEffect` API 31+, Live 4 API 30 chỉ tối hơn). Ô nhóm không có "+N": góc thứ tư là 2x2 icon nhỏ, **cả cụm là một đích chạm mở nhóm**.
- **Đọc video tham chiếu bằng ffmpeg:** tìm mép màn điện thoại theo cột không đen, cắt từng khung (`crop`), xếp contact sheet theo thời gian; đo kích thước (vd bề rộng thanh tìm kiếm) qua các khung để ra tỉ lệ co.
- **Tràn dưới thanh điều hướng:** padding đáy của view cha về 0, inset thành padding vùng cuộn (`clipToPadding=false`), giữ công thức cỡ ô trên chiều cao đã trừ inset; dải tối dần thay blur vì API 30 không có `RenderEffect`.
- **Số đo (debug):** Oppo vuốt Home↔Library janky 2,2%, mở/đóng panel 7,3%; Live 4 (kính FORCE_HIGH) 21% và 36% (panel kính thật trên Live 4 vốn ~31%).
- **Hằng chỉnh tay đặt cùng một object** (`AppLibraryMotion`), alpha dùng dải (start, end).

## PL — chấm trang trong pill (2026-11-10, Oppo A3x)

- **Hiệu ứng "đi theo ngón tay, đảo ngược được" thì suy trạng thái vẽ trực tiếp từ giá trị liên tục (`position`) trong `onDraw`**, không dùng animator theo thời gian. Chấm trang: mỗi chấm có trọng số `w_i = max(0, 1 − |i − position|)`, màu lerp giữa không sáng và sáng (`PageDotsLayout.weightOf`); bản smoothstep sau cờ `DOT_FADE_EASED`.
- **Thử bằng `input motionevent` phải chạy cả cử chỉ và `screencap` trong MỘT lệnh `adb shell`**: tách thành nhiều lệnh có khoảng trễ làm launcher hiểu là nhấn giữ, trang không trượt.
- **Animation mở app từ icon kiểu iOS cần remote animation**, quyền `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` là `signature|privileged|recents` ("Not for use by third-party applications"); launcher bên thứ ba chỉ có `ActivityOptions.makeScaleUpAnimation`/`makeClipRevealAnimation`. Hoãn (chủ dự án 2026-11-10); nghiên cứu ở plan PL mục "Nghiên cứu animation mở app (để dành)".

## HB — nút Home đóng mọi lớp phủ (2026-11-10, Oppo A3x)

- Mỗi lớp phủ tạm (tìm kiếm, Library, thư mục, nhóm, menu nhấn giữ) phải đóng ở **cả** Back (`HomeBackController`) và `HomeActivity.onNewIntent` (nút Home khi đang ở Home). Thiếu menu nhấn giữ ở `onNewIntent` làm menu mở từ thư mục treo trên Home kèm lớp tối. Thêm lớp phủ mới thì thêm vào cả hai chỗ.


## FX1, FX2 — edit mode, Home và recreate (2026-11-11, Vsmart Live 4)

- **Edit mode cũng là lớp tạm** (đọc code, 2026-11-11): mở rộng quy tắc HB, lớp phủ tạm đóng ở Back **và** Home; edit mode thoát ở Back, Home và `onStop` (trừ khi dựng lại do đổi cấu hình). Bảng chọn widget và bảng thời tiết cũng đóng khi bấm Home.
- **Animator vô hạn trên view phải gắn vào vòng đời hiển thị của view** (đo được trên Live 4, 2026-11-11): gắn + hiện + tính năng bật, không chỉ "bật/tắt tính năng". Trang trong cache của `RecyclerView` và Activity bị hủy đều làm animator mồ côi (8% một nhân kéo dài, `Activities: 3`). `Jiggler.sync` là cổng duy nhất.
- **`notifyItemRangeChanged` không bind lại khi cửa sổ đã dừng** (suy luận từ `ViewRootImpl`, chưa kiểm chứng, 2026-11-11): trạng thái cần dừng lúc `onStop` phải do chính view tự dừng khi ẩn, không qua adapter.
- **Dialog là cửa sổ riêng có `WindowInsetsController` riêng** (đọc tài liệu, 2026-11-11): thanh hệ thống ẩn ở Activity không áp cho dialog.
- **Sau recreate, `homeRoot.width` bằng 0 ở `onResume` đầu** (đọc code, 2026-11-11, FX1): ViewModel sống sót nên nhớ cỡ màn thay vì phụ thuộc cỡ do Activity truyền.

## FX7 — cử chỉ Home và chuyển cảnh trang cuối → Library (D2) (2026-11-11 tới 2026-11-12, Vsmart Live 4)

- **Cú vuốt trang "bị mất" của D2 vòng 3 là lỗi của bộ bơm cử chỉ, không phải của app** (đo được, 2026-11-12): bộ bơm cũ đóng dấu giờ lúc bơm và chờ app xử lý xong (`WAIT_FOR_FINISH`), nên một khung nặng của bản debug (> 40 ms) tạo khoảng hở giữa hai mẫu; `VelocityTracker` của `RecyclerView` xóa lịch sử, pager thấy vận tốc 0 và `SnapHelper` căn theo vị trí (bật về trang cũ khi chưa qua nửa trang). `PageFlingAssist` (vòng 3) đã viết để bù rồi **hoàn tác** (`c72d8ed`): ngón thật giữ giờ phần cứng nên không cần, và nó thêm rủi ro (hoàn tất trang giả lập). Quy tắc bộ bơm: xem `permissions-and-device-testing.md` (`Swiper`: giờ `down + k·8 ms`, ASYNC). Đừng sửa app cho một "lỗi" chỉ xuất hiện với bộ bơm chưa đóng dấu giờ đúng; kiểm bộ bơm trước. Cùng lỗi công cụ đã gây `vx = 0.0` ở vòng 1; phần dự phòng `lastVx` (chỉ chạy khi bộ đo trả 0 mà `UP` cách `MOVE` cuối < 24 ms) giữ lại vì vô hại, không còn là sửa lỗi.
- **Nguyên nhân thật của D2 (1): vuốt mở Library bị từ chối khi pager còn `SETTLING` về trang cuối** (đo được, vòng 2): `currentItem` trễ trong lúc trượt nên không dùng được; dùng đích hạ cánh (`settleTargetPage` suy từ `onPageScrolled`: position + offset + chiều đi, xóa ở mỗi lần đổi trạng thái). Cổng cử chỉ tiếp quản của lớp phủ phải nhận cả lúc pager còn `SETTLING` về trang cuối; sau `TAKEOVER` gọi `finishSettleOnLastPage()` để pager không kẹt giữa trang.
- **Nguyên nhân thật của D2 (2): vận tốc thả tay không đi vào hoạt ảnh Library** (đo được, Live 4): hoạt ảnh kết thúc của lớp phủ nhận vận tốc thả (`SpringProgress.startVelocity`), không dùng thời lượng cố định. Bản thời lượng cố định bắt đầu ở ~8330 px/s bất kể ngón (flick 30 ms: ngón 25000 px/s, hoạt ảnh chậm gấp ~3 lần; 80 ms: ngón 1875-9375 px/s, hoạt ảnh lúc nhanh gấp ~4 lần).
- **Nguyên nhân thật của D2 (3): spring mở/đóng không nhận chạm khi đang chạy** (đo được, vòng 4): luật `if (settling) return PASS` ở `DOWN` làm cú vuốt đảo chiều trong ~360 ms bị rơi và mọi cú sau lệch một nhịp. Sửa: spring ngắt được. `DOWN` lúc spring chạy thành ứng viên `GRAB` (không đụng spring ở `DOWN`, nên chạm nhẹ thì spring tự về đích cũ, Library không kẹt giữa chừng); vượt slop ngang thì `GRAB` nhận cả hai chiều, `spring.cancel()` (không gọi `onSettled`), `baseProgress = progress` hiện tại, kéo tiếp từ đó. Hoạt ảnh có thể bị chạm chen vào phải thiết kế "bắt lấy tại `p` hiện tại" từ đầu.
- **Số nghiệm thu theo tốc độ chủ dự án** (đo được, Live 4, `Swiper` mới, 2026-11-12): từ trang 1 tới Library trong 4 cú vuốt nhanh **18/18** (`main`: 0/18; ma trận 6 × 750 px, `D` 60/80/120 ms × `G` 150/250 ms × 3 lần); trang cuối ↔ Library xen kẽ **18/18** ở khoảng cách 150-400 ms.
- **`setCurrentItem(item, false)` của ViewPager2 với `item == currentItem` lúc không rảnh vẫn gọi `scrollToPosition`** (đo bytecode 1.1.0, 2026-11-11): mọi `setCurrentItem` không do người dùng phải chờ `SCROLL_STATE_IDLE`. `AsyncListDiffer` gọi callback ngay khi danh sách bằng nhau nên mọi lần phát state đều là trigger (đọc nguồn, chưa kiểm).
- **Khôi phục vị trí pager theo `pageId`, không theo chỉ số** (đọc code, 2026-11-11): `pageId` không duy nhất tuyệt đối (layout nháp `0`, id tạm âm, `PageId.NEW`).
- **Cổng nhận cử chỉ theo trạng thái pager chỉ xét ở `ACTION_DOWN`** (đọc nguồn, 2026-11-11): chạm lúc `SETTLING` làm `RecyclerView` chặn và báo `DRAGGING`.
- **View lớp phủ `GONE` từ đầu thì lần hiện đầu là lần đo/bố cục đầu** (đo được, 2026-11-11): `LIB_SIZE_END tookMs=223` trên luồng chính; FX7 không làm nóng vì FX8 (#39) đã chuyển việc dựng ô Library sang idle handler. Nhãn kính (H5) thuộc FX8.

## HT — công tắc Home (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/HT-home-toggles.md](../knowledgements/HT-home-toggles.md)

- **(2026-11-13, đọc code, HT) — `home-pager-animation`:** muốn ẩn một view trong `LinearLayout` có weight mà không đổi cỡ lưới thì giữ `VISIBLE` và không vẽ; `GONE` làm `GridMetrics.Cells` đổi và bind lại lưới + dock.

## CF — áp theme lúc Home đang hiện (2026-11-17, Vsmart Live 4)

- **(2026-11-17, đo được, Live 4, CF Task 4) Bấm Home khi Home đã ở trên cùng chỉ gọi `onNewIntent`, không gọi `onPause`/`onResume`.** Thứ chỉ áp ở `onResume` (`RecreateGuard`: kiểu icon, mức kính) sẽ không áp khi config đổi lúc Home đang hiện (adb, sau này áp theme tại chỗ): `IconCache` đã xóa nên trang chưa dựng/App Library ra kiểu mới, trang đang hiện giữ kiểu cũ. Sửa: `HomeActivity` thu `HomeViewModel.appearanceChanges` (`distinctUntilChanged().drop(1)`) trong `repeatOnLifecycle(RESUMED)` và chạy lại `recreateGuard.onResume()`. Bước kiểm "bấm Home sau lệnh adb" là sai; dùng Đa nhiệm rồi quay lại nếu cần đi qua `onResume`.
- **(2026-11-17, đo được bằng log `CFDBG`, Live 4) Không thêm con / đổi layout params trong `onSizeChanged`.** `onSizeChanged` chạy giữa `layout()`, sau lượt đo: `CategoryPageView` dựng 8 ô ở đó thì ô chưa được đo (grid cao 0, Library trắng chỉ còn thanh tìm kiếm) và `GridLayout` giữ trục cột tính dở (lần sau cột trái giãn hết bề ngang). Chỉ lộ khi lớp phủ đã `VISIBLE` trước lần layout đầu (`recreate()` lúc Library mở): đường thường dựng sớm bằng idle handler nên `onSizeChanged` không dựng. Sửa: `post` việc dựng ra sau lượt layout (`removeCallbacks` trước để không trùng, kiểm lại điều kiện lúc chạy, gỡ ở `onDetachedFromWindow`). Mọi đường mới làm `recreate()` Home phải thử cả lúc Library / panel / tìm kiếm đang mở.

## OB — Onboarding lần đầu, splash, đặt launcher mặc định (2026-11-17, Vsmart Live 4)

- **(2026-11-17, đo được bằng log + `dumpsys activity`, Live 4) Đặt launcher mặc định qua hộp thoại vai trò Home: kết quả KHÔNG về Activity đã mở hộp thoại.** Bấm chọn xong, PermissionController gửi intent HOME, hệ thống dựng `HomeActivity` MỚI trong task Home mới (`type=home`), process giữ nguyên; Activity cũ (task thường) bị bỏ lại. Không chặn được intent này, `finish()` Home mới thì hệ thống mở lại (vòng lặp). Cách xử lý của OB: bật cờ trong process trước khi mở hộp thoại (`OnboardingRuntime`), Home mới đọc cờ để đi tiếp đúng chỗ, và dọn task cũ của app bằng `ActivityManager.appTasks…finishAndRemoveTask()`.
- **(2026-11-17, đo được, Live 4) Không giữ khung hình đầu (OnPreDraw trả false) rồi mở Activity khác CÙNG task.** Hệ thống chuyển cửa sổ khởi động (splash) của Activity chưa vẽ sang Activity mới (`startingMoved=true`) rồi không gỡ (`STARTING_WINDOW_SHOWN` dù đã vẽ xong) → splash đè vĩnh viễn. Lần mở từ icon không lộ, lần Home mở trong task Home mới thì lộ. Cách đúng: để Activity đầu vẽ một khung (vd che nội dung `INVISIBLE`, nền cửa sổ cùng màu splash), mở Activity sau khung đầu (`OnDrawListener` + `post` + ~50 ms).
- **(2026-11-17, đo được, Live 4) Cho Activity con task riêng (`taskAffinity` + `singleTask`) không giải được hai lỗi trên:** Home mới vẫn mở bản Onboarding mới ở bước đầu, vào Đa nhiệm chọn màn cũ thì ra Home cũ. Đã hoàn tác.
- **(2026-11-17, đọc code) Splash ở theme khởi động:** `@mipmap/ic_launcher` của template là icon Android mặc định, đừng dùng cho splash; theme khởi động kế thừa màu thanh hệ thống Material3 (tím) nên phải đặt `statusBarColor`/`navigationBarColor` và cờ icon sáng (`windowLightNavigationBar` cần `values-v27`). API 31+ cắt icon splash theo vòng tròn 192 dp trong khung 288 dp: quầng sáng vẽ sẵn trong ảnh phải tắt trước mép đó.
- **(2026-11-17, quy trình) Xuất ảnh bằng figma-bridge:** `save_screenshots` chỉ ghi được vào thư mục làm việc của server (`C:\Windows\System32`), dùng `get_screenshot` (kết quả lớn tự lưu ra file) rồi giải mã base64. Ảnh có hiệu ứng bóng đổ thì khung xuất rộng hơn node: cắt lại theo bán kính bóng. Muốn bỏ lớp con (dấu chọn, chữ giờ) thì `set_node_visibility` tắt rồi bật lại ngay.

## EF — mất tràn viền sau recreate() trên Tecno Spark 40 (2026-11-18, đo được bằng log `SBDBG`, Tecno KM5n Android 15 + Live 4)

- **Triệu chứng:** sau khi đổi mức kính (hoặc thứ khác đi qua `RecreateGuard` → `recreate()`) rồi về Home, cả màn Home lệch xuống bằng thanh trạng thái và hụt đáy bằng thanh điều hướng, nền không tràn dưới thanh hệ thống. Chỉ Tecno Spark 40 (Android 15, HiOS); Live 4 (Android 11), Oppo A3x (Android 14) không bị. Có từ bản rất cũ (tái hiện ở `f8bc390`), không do DS3/OB/RS.
- **Nguyên nhân (đo được):** `recreate()` giữ lại DecorView cũ (cùng `System.identityHashCode`). Ở lần dựng lại, các view "fitsSystemWindows tùy chọn" của khung tự đệm theo inset như thể cửa sổ không tràn viền: LinearLayout gốc dưới DecorView `pad=80/80`, `home_root` thành `720x1440@0,80`. Inset bị ăn ở đó nên `setOnApplyWindowInsetsListener` của `home_root` không được gọi lần nào (margin 0/0). Vì sao ROM làm vậy: chưa rõ (suy luận: trạng thái tràn viền của ViewRootImpl không được áp lại cho cửa sổ được giữ lại).
- **Đã thử không được:** (A) gọi lại `WindowCompat.setDecorFitsSystemWindows(window, false)` + `requestApplyInsets` sau `setContentView`: không đổi. (B) gắn listener chặn đệm ở LinearLayout gốc: đệm dời xuống `FitWindowsLinearLayout#action_bar_root` của AppCompat. Chặn từng view là chữa triệu chứng.
- **Cách sửa (D, đạt trên Tecno và Live 4):** sau `setContentView` gọi `core/ui/reassertEdgeToEdge(window)`: đặt lại 3 cờ `SYSTEM_UI_FLAG_LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION` cho DecorView rồi `requestApplyInsets`. Các view đệm tùy chọn chỉ đệm khi cửa sổ không có cờ layout tràn viền kiểu cũ, nên một chỗ chặn được mọi tầng. Cờ chỉ đổi layout, không ẩn thanh, không xung đột với `WindowInsetsController` ẩn thanh trạng thái ở edit mode.
- **Quy tắc:** Activity mới nào tự vẽ tràn viền (`enableEdgeToEdge`/`setDecorFitsSystemWindows(false)`) phải gọi `reassertEdgeToEdge(window)` ngay sau `setContentView`, vì từ LG mọi Activity đều bị AppCompat `recreate()` khi đổi ngôn ngữ. Hiện có ở 6 Activity: Home, Settings, Onboarding, AppLibrary, GlassTuning, ThemeSettings.
- **Cách chẩn đoán dùng lại được:** log mã DecorView, vị trí trên màn của `home_root`, và chuỗi cha tới DecorView (lớp, id, padding, `fitsSystemWindows`) ở `onCreate`, 6 lần layout đầu, `onResume`, mỗi lần nhận inset. So lần mở đầu với sau `recreate()` là thấy view nào ăn inset.
- **Lùi bản trên máy thật:** `pm path` + `adb pull` để giữ APK đang lỗi; APK cài từ Android Studio có cờ `testOnly`, cài lại cần `adb install -t`. Hạ Room version (v2 → v1) phải gỡ app trước.

## Bổ sung chép từ repo XML (ở bản XML nằm cuối mục «EF — mất tràn viền sau recreate() trên Tecno Spark 40 (2026-11-18, đo được bằng log `SBDBG`, Tecno KM5n Android 15 + Live 4)»)

> Chép từ repo XML (`E:\SourceTree\IosLauncher`) ngày 2026-11-23 (PG1 Task 1). Bài học viết cho bản View: tên class View (`IconGridView`, `DockView`, `FolderPanelView`, `DragHelper`...) chỉ để tham khảo cơ chế, bản Compose có thể đã làm khác.

- **(2026-11-19, chủ dự án kiểm Live 4, P2-11) Vùng chạm tính theo tỉ lệ view phải đối chiếu với nội dung vẽ bên trong.** Pill chỉ 112 dp; "rìa = 1/4 mỗi đầu" (28 dp) trùng đúng chấm ngoài cùng (±28 dp từ tâm), nên giữ lên chấm mép bị hiểu thành "giữ rìa" và nhảy trang. Rìa phải tính từ cụm chấm đang hiện (ngoài chấm ngoài cùng + nửa bước). Cử chỉ giữ-rồi-kéo: giữ xong **chưa làm gì**, để chuyển động tiếp theo (dời tay / đứng yên thêm) quyết định chế độ.
- **(2026-11-19, P2-11) `requestDisallowInterceptTouchEvent` không chặn được cử chỉ ở cấp Activity.** Library và vuốt mở tìm kiếm đọc chạm trong `HomeTouchRouter` (trước cây view), nên view con muốn giữ cử chỉ phải được router hỏi ở DOWN (ở đây `SearchPillView.claimsTouchAt`).

## PG1 G1 — lướt nhanh sang Thư viện / Today trong Home Compose (2026-11-23, Oppo PKD130). Nguồn: [plans/PG1-parity-batch-1.md](../plans/PG1-parity-batch-1.md)

- **(2026-11-23, đọc code + kiểm tay) Cổng cử chỉ đọc trạng thái pager lúc chạm xuống thì đọc thẳng `PagerState`, đừng chép trong `SideEffect`.** `SideEffect` chỉ chạy khi màn dựng lại, nên `isScrollInProgress`/trang hiện tại có thể cũ đúng lúc lướt nhanh. `PagerState` là snapshot state, đọc ở luồng chính lúc nào cũng được: giữ tham chiếu (gán/bỏ trong `DisposableEffect`) cùng `interactionSource.collectIsDraggedAsState()`.
- **(2026-11-23, kiểm tay) "Pager đứng ở trang N" = đích (`targetPage`) khi đang cuộn, trang hiện tại khi đứng yên; không tính khi tay đang kéo.** Đòi pager đứng yên hẳn (bản đầu của JH3c/TV) làm lại đúng lỗi FX7 D2 của bản View: cú vuốt nhanh tới lúc pager còn trượt nốt bị pager nhận. Kèm theo phải có bước cho pager trượt nốt tới trang đích sau khi lớp phủ tiếp quản (đăng `post` sau `ACTION_CANCEL` giả), nếu không đóng lớp phủ có thể thấy trang áp chót.
