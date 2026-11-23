# Hệ thống thiết kế v3 (DS3): bài học

File chủ đề cho việc làm với `core/designsystem` và token `ds_*`. Bài học chung hơn nằm ở [../AGENTS.md](../AGENTS.md), không chép lại ở đây: tên giá trị `<enum>` không được là từ khóa Java (`new` → `new_item`), không viết `/*` trong comment Kotlin, kịch bản kiểm không bắt so hai màn cùng lúc, số đo chỉnh được trong Cài đặt không ghi là hằng (mục "Viết docs" và "Kiểm chứng trên máy thật"). Plan: [../plans/DS3-design-system-v3.md](../plans/DS3-design-system-v3.md).

## DS3 — hệ thống thiết kế v3: bài học (2026-11-16 đến 2026-11-17, Vsmart Live 4)

Nhãn nguồn: **đo được** (lỗi build/lint hoặc kiểm trên máy do chủ dự án báo) · **đọc code/tài liệu** · **suy luận chưa kiểm chứng**.

- **Attr chỉ có từ API 28 không được đặt trong `<style>`.** `android:accessibilityHeading` trong `Widget.Os26.Ds.SectionHeader` làm `lintDebug` báo `NewApi` với `minSdk 26` (đo được, 2026-11-16). Gỡ khỏi style, gọi `ViewCompat.setAccessibilityHeading(view, true)` trong code của view dùng nó (phủ API 26–27, đọc tài liệu). Ở layout XML lint thường chỉ cảnh báo chứ không báo lỗi (suy luận chưa kiểm chứng); `DsNavBar`, `DsDialog`, `DsBottomSheet` vẫn gọi `ViewCompat` thêm cho chắc.
- **Nút Material3 tô đè bề mặt kính.** Style nút Material3 có `backgroundTint`, che bề mặt `DsSurfaceDrawable`. `DsButton` dùng `defStyleAttr = 0` để không nhận style đó (đọc code, Task 4).
- **Font nhiều độ đậm trên API 26–27.** `android:textFontWeight` không chọn được 300/600 từ một họ font ở API 26–27, nên kiểu chữ trỏ thẳng từng file ttf; chiều cao dòng dùng attr `lineHeight` của Material, không dùng `android:lineHeight` (chỉ từ API 28). 4 file Be Vietnam Pro tổng 546 756 byte (đo, Task 1).
- **Vùng chạm 48 dp khi hình nhìn thấy nhỏ hơn.** `applyDsSurface(..., insetVerticalPx)` bọc nền bằng `InsetDrawable` (view cao 48, hình 30–40), outline và `clipToOutline` theo phần nhìn thấy. Chủ dự án kiểm bằng "Hiện ranh giới bố cục": chạm sát mép vẫn ăn, đạt (2026-11-16).
- **Gradient XML chỉ có tối đa 3 điểm dừng, `layer-list` không đặt bề rộng theo %.** Đường kẻ có hai đầu mờ cần 4 điểm, nên `DsDividerDrawable` vẽ bằng code và Task 6 gán bằng code, không dùng `@drawable` (đọc tài liệu).
- **Đổi icon Figma sang VectorDrawable.** `get_screenshot` định dạng SVG rồi đổi bằng script: chỉ giữ thẻ `<path>` nét, bỏ `filter` (bóng), `clip-path` và `stroke-opacity`, đặt mọi nét `#FFFFFFFF` để `tint` tô màu. `ds_ic_close` giữ cỡ gốc 20 dp (viewport 20), không ép 24. Hiện thực: 24 file `ds_ic_*.xml` (Task 3).
- **Thành phần tự đăng ký bo góc qua `applyDsSurface`.** Hàm này nghe `DsCorners` khi view gắn vào cửa sổ và gỡ khi tách; thành phần không tự `addListener`, tránh rò. Bề mặt `CARD`/`CONTROL`/`SHEET` còn theo thanh "Độ bóng" của kính. Đổi cả hai thanh rồi mở lại catalog: card/hộp thoại/sheet đổi, ô icon và PILL không đổi (kiểm trên máy, đạt).
- **Hàng có công tắc/radio phải là một mục trợ năng.** Con `isClickable = false`, `isFocusable = false`, `importantForAccessibility = no`; hàng tự báo `Switch`/`RadioButton`, `checkable`, `checked`. `setOnCheckedChangeListener` chỉ chạy khi người dùng bấm hàng, không chạy khi code đặt `isChecked` (cùng bài học M8a). Radio không tự loại trừ nhau: màn chứa bỏ chọn hàng khác (catalog làm mẫu). TalkBack kiểm bởi chủ dự án, đạt.
- **Hộp thoại và bottom sheet.** Cả hai inflate layout với một `FrameLayout` làm parent (`attachToRoot = false`) để view giữ `LayoutParams` wrap_content khi `setContentView` (suy luận từ code Material/AppCompat, chưa kiểm chứng riêng). Style `Widget.Os26.Ds.BottomSheet` đặt `backgroundTint` trong suốt và `android:elevation` 0 để bóng mặc định của sheet không lộ qua nền (suy luận chưa kiểm chứng). Bo hai góc trên của sheet: chủ dự án xem so ảnh Figma `312:2681`, không báo lệch.
- **Tên attr tiến độ Material 1.14.0.** `trackStopIndicatorSize`, `indicatorTrackGapSize`, `trackCornerRadius` chưa đối chiếu AAR, nhưng build vẫn xanh sau Task 5 nên AAPT chấp nhận (suy từ build xanh).
- **Chưa đo ở DS3:** dung lượng APK release (chưa build release; bản debug trên máy 14,97 MB) và việc `DsCatalogActivity` vắng trong release; chạy trên máy API 26–27 (chỉ có lint, không có máy).

## RS — đổi giao diện Cài đặt sang v3: bài học (2026-11-17 đến 2026-11-18, Vsmart Live 4)

Plan: [../plans/RS-settings-reskin.md](../plans/RS-settings-reskin.md). Hai bài học chung (grep màn dùng chung layout/adapter; `Dialog.dismiss()` gọi dismiss listener sau) ở [../AGENTS.md](../AGENTS.md).

- **Card nhóm trong RecyclerView vẽ bằng `ItemDecoration`, không ghép nền từng hàng.** Một `DsSurfaceDrawable` cho cả nhóm, vẽ ở `onDraw` dưới các hàng; nền từng hàng làm gradient/mép sáng lặp và lộ vết nối (đọc code). Để cuộn không dựng lại shader: cache drawable theo vị trí đầu nhóm, bounds luôn ở gốc (0, 0), dịch canvas tới vị trí nhóm; chỉ `setBounds` khi cỡ đổi. Nhóm bị cắt ở mép danh sách thì kéo dài card ra ngoài 2 × bán kính để góc bo không hiện giữa chừng. Hàng đầu/cuối nhóm cắt gợn nhấn bằng outline bo theo card, kéo dài outline quá mép không bo một đoạn bằng bán kính (`SettingsCardDecoration.clipToGroup`). Chủ dự án kiểm cuộn 100+ app ở Đổi tên: mượt, không lệch (2026-11-18).
- **Dấu chọn kiểu Figma có hai lớp.** `onb_check_circle` chỉ là tròn `ds_accent`; dấu tích trắng là `onb_ic_check` vẽ đè ở giữa (14 dp trong 24 dp). Đặt mỗi lớp tròn làm `src` thì chỉ thấy chấm xanh (đo được: chủ dự án báo ở ô ảnh nền 2.2, 2026-11-18). Dùng lại cách của `ThemeCardView`.
- **Nội dung tùy ý của `DsDialog` phải inflate với parent `FrameLayout` (`attachToRoot = false`).** Inflate với `null` làm mất `layout_width="match_parent"`: ô nhập Đổi tên co hẹp, sao Đánh giá lệch trái (đọc code; sửa trước khi kiểm).
- **`DsDialog` chỉ có hai nút.** Nút trung tính cũ ("Khôi phục tên gốc") chuyển thành `DsButton` phụ trong nội dung; nút chính lấy bằng `dialog.findViewById(R.id.ds_dialog_positive)` để bật/tắt theo nội dung ô nhập.
- **"Theo giao diện" không có ảnh xem trước đúng.** Nền thật của ô đó là màu đặc của theme; ảnh xem trước theme là cả màn Home có icon, dùng làm ô sẽ khác nền được đặt. Lưới ô giữ chỗ ở 2.2 chỉ giữ 5 màu cố định (chủ dự án chốt 2026-11-18).

## JC — design system trong Compose (2026-11-19, Samsung SM-A176B Android 16). Nguồn: [knowledgements/JC-compose-pilot.md](../knowledgements/JC-compose-pilot.md)

- **`lineHeight` của `ds_type.xml` không có tác dụng trên các màn XML hiện tại** (đo được: dòng phụ đề màn 1.1 cách 51 px, đúng khoảng dòng tự nhiên của font, không phải 17sp × 0,9 = 43 px). Kiểu chữ Compose (`DsTextStyles`) cố ý không đặt `lineHeight` và bật `includeFontPadding` để khớp; xem lại cả hai phía ở plan JD.
- **"Chữ đậm" của hệ thống (`font_weight_adjustment` = 300):** họ font Compose phải khai đủ 4 độ đậm và mỗi kiểu ghi rõ `fontWeight`, nếu không chữ không đậm lên. Khi đã khai đủ, Compose làm đậm mọi chữ còn màn XML chỉ làm đậm chữ semibold — chưa thống nhất (đo được).
- **Nhúng view DS bằng `AndroidView`** (`DsNavBarView`, `DsButtonView`, `DsBadgeNewView`): `factory` gắn listener một lần và gọi lambda mới nhất qua `rememberUpdatedState`; `update` đặt chữ, `isEnabled`, variant. Hình kính DS3 giữ nguyên (đo được).
- **Trợ năng:** `Modifier.selectable(role = RadioButton)` đã gộp chữ con; thêm `semantics { contentDescription }` ở cạnh nó tạo nút thứ hai trùng khung (đo bằng `uiautomator dump`).
- **Kiểm Compose bằng `uiautomator dump`:** cây liệt kê cả chữ của các trang pager dựng sẵn ngoài màn; xác định trang đang hiện bằng ảnh chụp.

## JD — thành phần Compose (2026-11-19, Oppo PKD130 Android 14). Nguồn: [knowledgements/JD-compose-design-system.md](../knowledgements/JD-compose-design-system.md)

- **Thành phần nhận kéo đặt trong vùng cuộn phải chờ touch slop đúng hướng** (đo được): thanh trượt đặt giá trị lúc chạm xuống làm cú vuốt dọc đổi giá trị 52 → 16. Kéo dùng `awaitHorizontalTouchSlopOrCancellation`, chạm dùng `detectTapGestures` (bị cuộn giành thì không tính).
- **`SideEffect` không theo dõi state** (đo được): độ mờ cửa sổ hộp thoại đặt trong `SideEffect` chỉ chạy một lần ở 0. Đẩy giá trị hoạt ảnh ra API ngoài Compose bằng `snapshotFlow` trong `LaunchedEffect`.
- **Hiệu ứng nhấn chia hai modifier** (đọc code): co cả phần tử phải đứng đầu chuỗi, lớp phủ phải đứng sau phần cắt góc; một modifier không làm được cả hai.
- **Đo vị trí trong `onGloballyPositioned` là toạ độ bên trong phần đệm của cha**: lớp vẽ đặt trước `padding` phải cộng lại đệm (đường kẻ hàng lệch 12 px, đo được).
- **Hàm mở rộng phải import** (`composed`, `colorControls`...): gọi bằng tên đầy đủ không biên dịch.
- **Test cử chỉ bằng adb** (đo được): `input tap` chạy song song gộp thành một cú chạm; DOWN→UP ở chỗ khác mà không có MOVE bị Compose tính là chạm; `screencap` (~250 ms) không bắt được hoạt ảnh lò xo; `uiautomator dump` không in vai trò (Switch/RadioButton) và cờ heading, và liệt kê cả trang pager dựng sẵn ngoài màn; chữ có "&" nằm trong XML dạng `&amp;`.
- **Lint Compose đáng nghe** (đo được): `ConfigurationScreenWidthHeight`, `LocalContextResourcesRead` → lấy cỡ cửa sổ từ `LocalWindowInfo.current.containerSize`; `UnusedBoxWithConstraintsScope` làm `lintDebug` đỏ.

## JH1 — Home Compose tĩnh (2026-11-19, Oppo PKD130 Android 14). Nguồn: [knowledgements/JH1-compose-home-static.md](../knowledgements/JH1-compose-home-static.md)

- **`weight` của Compose khác `layout_weight` ở chỗ margin** (đo được): `LinearLayout` trừ margin của con TRƯỚC khi chia trọng số; trong Compose, `padding` đặt sau `weight` nằm TRONG phần được chia. Lề đáy 8 dp của dock để trong `weight` làm pager cao thêm 14 px và kính dock thấp đi 12 px. Lề nằm ngoài trọng số thì dùng `Spacer` riêng.
- **Inset của Home View không chỉ là thanh hệ thống** (đo được): đỉnh lưới = `max(thanh trạng thái, chỗ hộp chạm pill edit mode)` (TL1 Task 5). Bản Compose chỉ lấy inset thì lưới cao hơn 33 px. Chép hình học thì đọc code đặt margin (`SystemBarsController`), không chỉ XML.
- **`enableEdgeToEdge()` vẽ lớp xám sau thanh điều hướng 3 nút** (đo được): tắt `isNavigationBarContrastEnforced` như Home View thì mới trong suốt.
- **`produceState` không gắn state với khóa** (đọc code): khi khóa đổi, `value` vẫn là giá trị cũ; producer phải gán lại từ đầu, không được dựa vào `value == null`.
- **Dùng lại phép tính, không chép số đo** (đo được): gọi `GridMetrics.measure` từ Compose cho kết quả trùng điểm ảnh với Home View ngay lần đầu ở phần lưới; mọi chỗ lệch đều ở phần tự dựng lại (inset, trọng số).
- **Hai lệnh Gradle chạy song song làm hỏng KSP** (đo được): `NoSuchFileException` ở file `*_GeneratedInjector.java`, rồi "Storage ... is already registered". Đừng cho subagent và phiên chính cùng build; nếu đã hỏng thì `gradlew --stop` hoặc `--no-daemon`.
- **Đo jank Home: mỗi lần chạy `am start -S` riêng** (đo được): mở xen kẽ hai Activity cùng task làm `gfxinfo` ra 0 khung hoặc số của Activity khác. Hộp thoại "Đặt làm màn hình chính" hiện lại sau mỗi `am start -S`, chặn vuốt (0 khung) và Back không đóng được: tìm nút "Để sau" bằng `uiautomator dump` (đặt `MSYS_NO_PATHCONV=1` trong Git Bash) rồi bấm đúng toạ độ đó. Máy tự khóa giữa lượt cũng ra 0 khung: kiểm `isKeyguardShowing` trước mỗi lượt.

## JH2 — kéo-thả trong Home Compose (2026-11-19, Samsung SM-A176B Android 16). Nguồn: [knowledgements/JH2-compose-home-drag.md](../knowledgements/JH2-compose-home-drag.md)

- **Cử chỉ sống lâu hơn một trang phải đặt ở gốc** (đọc code + đo được): `pointerInput` trong trang pager bị huỷ khi trang rời composition. Đặt ở gốc, đọc ở `PointerEventPass.Initial`, chỉ tiêu thụ sau khi đã giữ đủ lâu: lật 5 trang liền mà kéo không mất.
- **Thêm `Box` bọc một composable là đổi cách căn của mọi con** (đo được): `Box` mặc định căn TopStart, nên ô không nhãn (chỉ rộng bằng icon) lệch trái; căn giữa xong thì phần tử phủ (dấu "−") cũng bị kéo vào giữa. Phần tử phủ có toạ độ riêng thì cho `align(TopStart)`.
- **Thử kéo-thả bằng adb:** `input motionevent DOWN/MOVE/UP` trong MỘT lệnh `adb shell "...; sleep 0.9; ..."` giả được giữ rồi kéo; `input swipe` thì không. Chỉ giả được một ngón.
- **Thử trên máy thật của chủ dự án thì sao lưu trước** (đo được): `adb exec-out run-as <gói> tar cf - databases files shared_prefs > bk.tar` (dùng `exec-out`, `adb shell` làm hỏng byte); khôi phục bằng `adb exec-in run-as <gói> tar xf - < bk.tar` sau `am force-stop`; so `launcher.db` từng byte.
- **Snap dài hơn không làm lướt nhẹ hơn** (đo được): tween 450 ms giảm tỉ lệ khung giật nhưng vẽ thêm ~200 khung mỗi lượt và p90 giữ nguyên. Đo p90 / số khung, không chỉ janky %.

## JH3a — nối Activity của Home Compose (2026-11-19, Samsung SM-A176B). Nguồn: [knowledgements/JH3a-compose-home-wiring.md](../knowledgements/JH3a-compose-home-wiring.md)

- **View lớn nhúng tạm vào màn Compose: thêm bằng `addContentView` phía trên `ComposeView`, không dùng `AndroidView` phủ toàn màn** (đọc tài liệu + kiểm máy): View `GONE` trong cây View không nhận chạm; Home Compose bên dưới vẫn nhận bấm, vuốt, nhấn giữ.
- **`internal` lan theo chữ ký** (biên dịch): nhận một kiểu `internal` thì hàm Compose công khai cũng phải `internal`.
- **Kiểm Back khi có bàn phím:** lần Back đầu chỉ đóng bàn phím (IME giữ), lần hai mới tới `BackHandler`. Kịch bản kiểm phải tính hai lần.
- **Hộp thoại "Đặt làm màn hình chính" hiện ở mọi lần resume** khi chưa là launcher mặc định: kịch bản adb phải đóng nó (tìm "Để sau" bằng `uiautomator`) trước mọi cú bấm, nếu không cú bấm chỉ đóng hộp thoại.

## JH3b — menu và thư mục Home Compose (2026-11-19, Samsung SM-A176B). Nguồn: [knowledgements/JH3b-compose-home-menu-folder.md](../knowledgements/JH3b-compose-home-menu-folder.md)

- **Lớp phủ chồng nhau: con khai báo sau ở trên** (đo được): menu của app trong thư mục mở nhưng bị bảng thư mục che vì khai báo trước. Mỗi lớp phủ mới phải nghĩ tới lớp nào có thể mở cùng lúc.
- **Bề mặt DS không phải chỗ nào cũng hợp** (đo được): `DsSurface.SHEET` tối, đặc; thư mục và menu kiểu iOS cần kính sáng → dùng CARD (kính thật) + lớp tint riêng của thành phần (`folder_panel_glass_tint`, `shortcut_menu_glass_tint`).
- **Kiểm đổi tên bằng adb:** `input text` không gõ được dấu tiếng Việt; dùng chữ ASCII ("X1"). Bấm phím Home để thử nhánh lưu ở `ON_STOP`, rồi khởi động lại app (`am start -S`) để chắc đã ghi DB.

## JH3c — Thư viện và widget app trong Home Compose (2026-11-20, Samsung SM-A176B). Nguồn: [knowledgements/JH3c-compose-home-library.md](../knowledgements/JH3c-compose-home-library.md)

- **View cũ nhúng vào màn Compose có thể tìm View khác theo id** (đo được): nền mờ và kính ô nhóm của Thư viện tìm `R.id.backdrop_image` bằng `rootView.findViewById`; không có thì vẽ màu phẳng mà không báo lỗi. Trước khi nhúng một View cũ, grep `findViewById`/`R.id.` trong nó và các drawable nó dùng.
- **Cử chỉ chạy ngoài Compose: `dispatchTouchEvent` + `ACTION_CANCEL` giả** (kiểm máy): cách của `HomeTouchRouter` vẫn đúng với `ComposeView`; Compose huỷ nhấn giữ/cuộn khi nhận CANCEL.
- **Trạng thái Compose cho code ngoài: đối tượng thường ghi trong `SideEffect`**, không cần `State` khi bên đọc chỉ đọc lúc có sự kiện (chạm xuống).

## JH4 — chuyển Home sang Compose (2026-11-20, Samsung SM-A176B). Nguồn: [knowledgements/JH4-compose-home-switchover.md](../knowledgements/JH4-compose-home-switchover.md)

- **Không đổi tên Activity có bộ lọc HOME** (đọc tài liệu): hệ thống lưu launcher mặc định theo tên thành phần. Thay nội dung lớp, đổi tên bản cũ.
- **`finish()` trong `onCreate` vẫn chạy hết vòng đời** (đọc code): Activity nhiều `lateinit` cần cờ chặn trong mọi callback.
- **`git mv` rồi sửa nội dung lớn thì git ghi là xoá + thêm** (quan sát): đổi tên `HomeActivity` → `ViewHomeActivity` trong cùng commit với `HomeActivity` mới làm git không nhận ra rename. Muốn giữ lịch sử thì commit rename riêng trước, rồi mới đặt lớp mới vào tên cũ.

## JS + JX — toàn bộ UI sang Compose, gỡ lớp View (2026-11-20, Samsung SM-A176B). Nguồn: [knowledgements/JS-JX-compose-all-screens.md](../knowledgements/JS-JX-compose-all-screens.md)

- **Chứng minh danh sách xoá bằng build thật trong worktree bỏ đi** (đo, JX): xoá, gỡ dependency, đổi theme trong `git worktree` tạm, chạy `assembleDebug lintDebug testDebugUnitTest`; danh sách đưa duyệt là danh sách đã xanh.
- **Đổi theme cha Material3 → AppCompat phải gỡ thuộc tính chỉ Material có** (đo, JX): `colorOnPrimary`, `colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `bottomSheetStyle`, cha `ThemeOverlay.Material3.*`, `TextAppearance.Material3.*`; thiếu thì AAPT lỗi link.
- **Lint Compose** (đo, JS): dùng `stringResource(...)` thay `LocalContext.current.getString(...)`; locale dùng `androidx.compose.ui.text.intl.Locale.current.platformLocale`.
- **`scrollToItem` làm `isScrollInProgress` = true** (đo, JS4): ẩn bàn phím chỉ khi có `DragInteraction.Start`, không theo `isScrollInProgress`.
- **Cửa sổ dialog có thanh trạng thái riêng** (quan sát, JS3): ẩn thanh trạng thái trên chính cửa sổ dialog nếu nó che nội dung.
- **ComposeView trong overlay của Service** (đo, JS7b): tự cấp `Lifecycle`/`SavedState`/`ViewModelStore` owner và `Recomposer` riêng; tạm dừng frame clock khi panel đóng.

## PG1 F3, F4 — kiểu chuyển cảnh cho panel, cài đặt kính theo API (2026-11-23, Oppo PKD130 + máy Android 11). Nguồn: [plans/PG1-parity-batch-1.md](../plans/PG1-parity-batch-1.md)

- **(2026-11-23, đọc code) Thiết lập ở Cài đặt mà bản Compose không đọc thì ẩn hàng, giữ khóa.** "Mức kính" và công tắc kính từng phần tử chỉ điều khiển kính View cũ; kiểm bằng grep mọi chỗ ngoài `ui/settings/` gọi `GlassEnvironment.modeFor` (không có) trước khi ẩn, để chắc ẩn không đổi hành vi máy cao. Thanh nào còn tác dụng theo API phải đối chiếu `DsGlass.kt` (blur API 31, lens API 33) và `DsSurfaceDrawable` (Độ bóng vẫn có tác dụng dưới API 31).
- **(2026-11-23, đọc code) Trong `object` có hàm tên `spring`, gọi `spring(...)` của Compose phải ghi đủ tên gói hoặc đổi tên hàm** — gọi trần sẽ gọi đệ quy chính hàm đó (đã đổi thành `PanelStyleMotion.scaledSpring`).
