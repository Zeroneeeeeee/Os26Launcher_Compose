# Bài học: Tìm kiếm

Nằm trong bộ bài học của dự án (mục lục: [README.md](README.md); quy ước chung: [../AGENTS.md](../AGENTS.md)). Nội dung: Lõi tìm kiếm, lớp tìm kiếm, pill, trợ năng, mở rộng tìm kiếm, danh bạ.
Mỗi mục giữ nguyên ngày, nhãn nguồn (đo được / đọc code / đọc tài liệu / suy luận chưa kiểm chứng) và thẻ mốc như lúc ghi; xếp theo thứ tự thời gian. Plan và knowledgement gốc của mốc nằm ở [../plans/](../plans/) và [../knowledgements/](../knowledgements/) (tên file bắt đầu bằng thẻ mốc, vd `GL-glass-pass.md`). Bài học mới: điều phối thêm vào cuối file này.

## M8b Task 1 — lõi tìm kiếm: bài học (2026-10-30)

- **Bỏ dấu tiếng Việt:** `Normalizer.NFD` + xóa `\p{Mn}` là chưa đủ vì `đ/Đ` không phân rã; phải thay tay `đ` -> `d` SAU `lowercase(Locale.ROOT)` (lúc đó `Đ` đã thành `đ`). Chuẩn hóa cả nhãn lẫn truy vấn bằng cùng một hàm `normalize` (domain thuần, dùng `java.text.Normalizer` của JDK).
- **Khớp đầu từ viết là `label.contains(" $q")`** trên nhãn đã chuẩn hóa (một khoảng trắng): truy vấn nhiều từ như `may anh` vẫn khớp đúng ranh giới từ. Thứ tự cùng hạng lấy từ thứ tự đầu vào (`sortedBy` ổn định) nên kết quả theo bảng chữ cái của `AppRepository.apps`.
- **`AppRepository.apps` phát rỗng trước giá trị thật:** `.filter { isNotEmpty() }` rồi `.onStart { emit(null) }` để phân biệt "chưa nạp" (LOADING) với "không có app".
- **Kiểm chứng logic thuần bằng log tạm một lần (tag `M8B`) khi dự án không có test tự động:** ghi nhiều truy vấn mẫu cùng lúc, đối chiếu với đặc tả, xóa log khi sang giao diện.

## M8b Task 2 — cử chỉ vuốt xuống + khung lớp tìm kiếm: bài học (2026-10-30)

- **Cử chỉ toàn màn hình nhận ở `Activity.dispatchTouchEvent`, tách hình học ra lớp thuần (`SearchSwipeDetector`) chỉ QUAN SÁT:** chuỗi chạm vẫn đi xuống cây view cho tới khi detector trả true; lúc đó gửi `ACTION_CANCEL` giả (cùng mẫu tiếp quản kéo-thả M7), mở lớp, đặt cờ nuốt tới `UP/CANCEL`. Điều kiện bật (`enabled`) truyền theo TỪNG sự kiện để trạng thái đổi giữa chừng (menu nhấn giữ vừa mở) hủy theo dõi.
- **Vùng bắt đầu cử chỉ tính theo tọa độ màn hình lúc cần** (`dockView.getLocationOnScreen`, inset thanh trạng thái lưu từ listener insets) thay vì lưu cứng: không hỏng khi split-screen/gập màn hình.
- **Đẩy chữ từ state vào `EditText` chỉ khi mở/đóng, không mỗi lần render:** state đến trễ một nhịp sẽ ghi đè chữ vừa gõ thêm. Dùng cờ `applyingState` để `TextWatcher` không phát ngược `onQueryChanged` khi ghi từ state.
- **Lớp phủ tự xử lý insets** (`ViewCompat.setOnApplyWindowInsetsListener` trên chính nó): đệm trên theo thanh trạng thái, đáy danh sách theo `max(ime, navigationBar)` — inset `ime` tới được lớp con dù `adjustNothing` (API ≥ 30). `onNewIntent` của `HomeActivity` (singleTask) là chỗ bắt nhấn Home khi đang ở Home; androidx.activity 1.13 dùng chữ ký `onNewIntent(intent: Intent)` không null.

## M8b Task 3 — danh sách kết quả + mở app: bài học (2026-10-30)

- **`RecyclerView` nuốt chạm cả ở phần trống bên dưới hàng cuối** (LayoutManager dọc luôn trả true cho DOWN), nên `onClick` của nền không bao giờ nhận được chạm vào vùng đó. Muốn "chạm chỗ trống = đóng" thì gắn `OnItemTouchListener` + `GestureDetector.onSingleTapUp` và kiểm `findChildViewUnder(x, y) == null`; không chặn (trả false) để cuộn/bấm hàng vẫn chạy.
- **Ripple mặc định (`?attr/selectableItemBackground`) theo theme sáng gần như vô hình trên nền tối:** lớp phủ tối dùng ripple riêng `#33FFFFFF` với mask trắng.
- **Bốn trạng thái của một danh sách tìm kiếm loại trừ nhau và suy ra từ `SearchStatus`:** Idle/Empty dùng chung một `TextView` đổi chuỗi, Loading là `ProgressBar`, Results là `RecyclerView`; danh sách được `submitList(emptyList())` khi không ở Results để không giữ hàng cũ bên dưới.
- **Mở app thất bại giữ nguyên lớp (chỉ Toast), thành công mới `close()`:** `close()` nằm trong ViewModel nên truy vấn xóa theo; cuộn danh sách hạ bàn phím để thấy hết kết quả.

## M8c Task 1 — pill tìm kiếm + chấm ổ quay số: bài học (2026-10-30)

- **Pill kích thước cố định thì chuyển chế độ chỉ là vẽ lại:** không `requestLayout`, không đo lại, `ValueAnimator` chỉ đổi `progress` làm mờ chéo hai nội dung. Hủy animator ở `onDetachedFromWindow`; `setDotsMode` không làm gì nếu đã đúng chế độ.
- **Phép tính vị trí hàng chấm tách thành hàm thuần (`PageDotsLayout`) không cấp phát mỗi khung vẽ:** `windowStart = clamp(p − 2, 0, N − 5)`; hệ số thu nhỏ chấm mép nội suy theo `clamp(w, 0, 1)` (trái) và `clamp(N − 5 − w, 0, 1)` (phải) — nếu dùng điều kiện cứng `w > 0` chấm mép nhảy đột ngột ngay khi bắt đầu trượt.
- **Chế độ pill giữ theo "chế độ MONG MUỐN" (`pillWanted`) và chỉ hành động khi nó đổi:** `render()` chạy rất thường xuyên, nếu mỗi lần hủy/đặt lại hẹn giờ 600ms thì pill không bao giờ về ô tìm kiếm. Điều kiện chấm = đang lướt (`state != IDLE`) HOẶC edit mode HOẶC đang kéo (`draggingItemId`/`DragHelper.currentSession`). `render()` gọi `setCurrentItem(..., false)` không phát `SCROLL_STATE` nên không làm pill co nhầm (đã kiểm bằng log `M8C`).

## M8c Task 2 — bấm pill + trợ năng: bài học (2026-10-30)

- **View tự vẽ nhận bấm đúng chế độ:** `isClickable = !dotsMode` đổi cùng chế độ, `OnClickListener` kiểm lại `dotsMode`; vuốt xuống bắt đầu từ pill không thành bấm vì cử chỉ của Activity gửi `ACTION_CANCEL` giả xuống view trước (hệ thống hủy trạng thái nhấn).
- **Phản hồi nhấn tự vẽ:** `drawableStateChanged()` → `invalidate()` và vẽ lớp trắng 10% khi `isPressed` (ripple mặc định không áp cho view không có background).
- **`contentDescription` đặt lại mỗi khi trang/chế độ đổi:** `View.setContentDescription` bỏ qua khi chuỗi không đổi nên gọi thường xuyên lúc lướt không phát thêm sự kiện trợ năng. Cần `AccessibilityDelegateCompat` để báo `Button` + `isClickable` đúng chế độ. **Chưa kiểm bằng TalkBack thật.**
- **Log chẩn đoán tạm xóa cùng lúc bỏ tham số "lý do" chỉ phục vụ log** để không để lại API thừa.
- **`IconGridView` tái dùng `IconCellView` giữa các loại item (G2a A1a):** khi một ô đổi từ app sang thư mục (hoặc ngược lại), phải đặt lại MỌI listener (`onClick`, `onLongClick`, `onTouch` của `IconDragGesture`, `onBadgeClick`) và trạng thái vẽ (`bind` xóa `folderPreviews`, `bindFolder` xóa `icon`), nếu không ô thư mục còn giữ hành vi kéo/ẩn của app trước đó.
- **Thêm kiểu item vào `HomePage.items`:** các chỗ duyệt `items` chỉ cần vị trí dùng `PlacedItem.withPosition`; chỗ cần app (`.app`) phải `as? PlacedApp` và quyết định rõ thư mục bị bỏ qua hay từ chối. `LayoutReconciler` chỉ ghi lại hàng `PlacedApp` khi seed; các nhánh còn lại đọc thẳng hàng DB nên chưa xử lý riêng thư mục (làm ở A4).
- **Dwell trong kéo-thả (G2a A1b):** `DragHelper` chỉ phát `onDragMove` khi ngón tay di chuyển và `IconGridView.onDragMove` thoát sớm khi ô không đổi, nên hẹn giờ giữ-yên phải dùng `postDelayed` (hủy khi đổi ô/ra vùng giữa/`onDragExit`/thả/`onDetachedFromWindow`) và phần cập nhật ý định phải chạy TRƯỚC lối thoát sớm. Khi hủy ý định đã kích hoạt phải đặt `previewTargetCell = null` để preview đẩy-dồn tính lại.
- **Ô đang kéo phải ẩn theo dữ liệu ở MỌI nhánh bind (G2a A1b):** nhấn giữ ở chế độ thường làm vào edit mode và bind lại trang NGAY giữa lúc bắt đầu kéo. Nhánh bind nào đặt cứng `alpha = 1f` (như `bindFolderCell` lúc đầu) sẽ làm ô gốc hiện lại, thấy icon bị nhân đôi. Luôn dùng `if (itemId == hiddenItemId) 0f else 1f`.
- **Luật gộp thư mục = gộp trước, đẩy-dồn khi giữ ở mép ≥ 500 ms (G2a A1b, CHỐT 2026-10-30 theo hành vi iOS sau hai lần đổi qua lại; áp cho cả đích là app lẫn đích là thư mục, chung một trạng thái `folderArmedId`):** kéo tới ô app 1x1 là vào chế độ gộp ngay (phóng ô đích, tắt preview đẩy-dồn); hẹn giờ chỉ dùng để chuyển sang đẩy-dồn khi giữ ở mép ô. Chế độ nào cũng phải tự hủy hẹn giờ khi đổi ô/ra khỏi lưới/thả. Đổi lại cần sửa cả D4 trong plan.
- **Panel thư mục (G2a A2):** `FolderPanelState` dẫn xuất từ `openFolderId` + `PlacedFolder` trong `pages` (thư mục ≤ 9 app nên `previewApps` đủ), không có nguồn dữ liệu thứ hai. Id thư mục tạm âm (cập nhật lạc quan sau khi tạo) không mở được panel. Tiêu đề là `EditText` luôn sửa được; chỉ `setText` từ state khi ô không có focus.
- **Giải tán thư mục (G2a A3a, D9):** `dissolveFolderIfNeeded` chạy TRONG transaction của thao tác làm giảm con (đã gắn vào `setHidden`); đếm hàng con trong DB. UI lạc quan không dựng lại app đơn về ô thư mục (không biết id hàng con) — chỉ đóng panel và chờ đối chiếu DB. Thêm đường làm giảm con mới (gỡ app, kéo ra) phải gọi hàm này.
- **Kéo app con ra khỏi thư mục (G2a A3b):** `PlacedFolder.childIds` song song `previewApps` để UI biết id hàng con. Panel là `DragDropTarget` với `blocksOtherTargets = true` (DragHelper chỉ trả panel khi ngón tay còn trong khung) và tự xin đóng ở `onDragExit`, trừ khi vừa `onDrop` (thả lại vào panel giữ panel mở). `moveItem`/`addToFolder` nhận nguồn `container > 0` rồi gọi `dissolveFolderIfNeeded` trong cùng transaction; UI lạc quan dùng `detachChild` khớp quy tắc đó (còn 1 app thì app thay thư mục tại cùng ô TRƯỚC khi lập kế hoạch chèn).
- **Ẩn icon mini của thư mục khi kéo app con ra (G2a A3b):** `hiddenItemId` của `IconGridView` cũng lọc `previewApps` theo `childIds`; vì nội dung trang không đổi nên `HomePagerAdapter.draggingItemId` tự `notifyItemRangeChanged` khi id đổi liên quan tới một app con. Nếu không thì người dùng thấy hai bản (bản bay theo tay và bản trong ô thư mục).
- **Rà soát giả định "container chỉ HOME/DOCK" (G2a A4 Step 1):** `reflowInTransaction`, `ensureFitsGrid`, `placeNewInTransaction`, `seedIfEmpty`, `compactPageAfterRemoval` đã đúng với thư mục (thư mục là hàng HOME 1x1; con `container>0` không chiếm ô và không bị dồn; `placeNewInTransaction` dùng `componentKeyOrNull` trên MỌI hàng nên app nằm trong thư mục không bị coi là mới). Chỗ hỏng duy nhất là đường gỡ app thật (`removePackages`, `catchUp`): xóa hàng con mà không áp D9. Sửa bằng `deleteAppsAndFixFolders`: thư mục mà mọi con bị gỡ được xóa CÙNG lượt để ô của nó được dồn như icon thường; thư mục còn lại thì `dissolveFolderIfNeeded`. Mọi đường làm giảm con của thư mục phải đi qua D9.
- **Log chẩn đoán tạm (G2a A5):** đặt tiền tố riêng (`G2A`) để xóa hàng loạt bằng một lệnh; log nằm trong `.also { }` của lambda `write` phải gỡ cả vỏ `.also` để hàm vẫn trả đúng giá trị. Sau khi xóa, grep lại tiền tố và `import android.util.Log` không còn dùng.
- **Hoãn phép đo hiệu năng khi chưa có đối tượng so sánh:** đo trước khi áp hiệu ứng chỉ có ích nếu sẽ so với số sau; nếu hai lần đo vẫn phải làm sát nhau thì gộp vào mốc áp hiệu ứng (G2a: Janky thư mục dời sang "Áp kính").
- **Phân loại app cho Library (G2a B1):** `ApplicationInfo.category` do nhà phát triển khai báo, app tự cài/thử nghiệm thường trả `-1` (UNDEFINED) nên nhóm dự phòng "Khác" có thể chiếm 40% máy dev (Live 4: 34/85); đừng chỉnh bảng nhóm để chữa việc này. App Google (Chrome, Gmail, Duo) khai báo `SOCIAL`, Google Maps hệ thống khai báo `-1`. Đổi hằng số hệ thống sang enum của domain ở lớp data (`mapPlatformCategory`) để domain không phụ thuộc Android; hàm thuần `AppCategoryGrouper` đảm bảo mỗi app đúng một nhóm. Phép đo log một lần dùng `AtomicBoolean.compareAndSet` để `loadApps` gọi lại nhiều lần không spam.

## M8d — mở rộng tìm kiếm: bài học (2026-11-05, Vsmart Live 4 + Oppo A3x)

- **Chuẩn hóa nhãn một lần vào chỉ mục (đo được, Oppo 345 app):** mỗi phím p50 ~5–7 ms so với ~70–80 ms của M8b. p95 vẫn 7–9 ms, có đột biến 22–43 ms chưa rõ nguyên nhân.
- **`<queries>` mở tầm nhìn theo package, không theo action:** đo `resolveActivity` từng action trước khi khai thêm.
- **`ACTION_*_SETTINGS` có thể có app ngoài đăng ký** (Instabridge cho `WIFI_SETTINGS` trên Oppo): chỉ nhận trình xử lý hệ thống và mở đúng component, không để hiện hộp chọn.
- **Dữ liệu riêng tư không để trong DataStore của app khi `allowBackup="true"`:** để ở `noBackupFilesDir`, ghi nguyên tử. Review độc lập phát hiện lịch sử mở app có thể rời máy qua Auto Backup dù giao diện ghi "đã lưu trên máy".
- **Oppo bỏ dòng logcat khi in dồn** (log 30 dòng liên tiếp bị cắt sau khoảng 15 dòng; suy luận về cơ chế): gom thành dòng tóm tắt hoặc ghi ra file.
- **Lấy shortcut cần launcher mặc định:** kiểm quyền trước khi dùng cache, nếu không dòng cũ vẫn hiện sau khi đổi launcher mặc định.
- **Script tìm nút theo tiền tố chữ có thể trúng tiêu đề hộp thoại** ("Xóa lịch sử…" thay vì "Xóa"): tìm đúng nguyên chuỗi và kiểm kết quả sau khi bấm.

## G3a — danh bạ chỉ trong RAM (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **G3a — danh bạ chỉ trong RAM**, không lưu Room/DataStore/file vì `allowBackup="true"`; log chỉ số lượng, không tên/số.

## G3a — ColorOS bỏ qua ORDER BY (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **ColorOS bỏ qua ORDER BY trên `Phone.CONTENT_URI`** (Oppo quay số đầu thay vì số mặc định; Live 4 đúng): chọn số bằng code từ `IS_SUPER_PRIMARY`, `IS_PRIMARY`, `_ID`.

## G3a — bàn phím ảo che phím (tách từ «RB và G3a — bản release R8 và danh bạ trong tìm kiếm (2026-11-09, Oppo A3x + Vsmart Live 4)»)

- **Bàn phím ảo che phím:** một lần `keyevent 4` thừa sẽ đóng lớp tìm kiếm; mở lớp tìm kiếm trên Oppo bằng nút `search_pill`, vuốt xuống có thể trúng icon.

## FX4 — chỉ mục và danh bạ (2026-11-11, Vsmart Live 4)

- **Đường tắt "chỉ ASCII" cho `normalize` vô ích với tên người Việt** (suy luận từ số đo, 2026-11-11): đa số có dấu. Chuẩn hóa nhanh phải dựng bảng tra bằng chính thuật toán cũ và rơi về bản cũ (nguyên chuỗi) khi gặp ký tự lạ. Chỉ mục ghép từ nguồn đã chuẩn hóa một lượt.
- **`stateIn(WhileSubscribed)` giữ giá trị cuối và lọc trùng** (đọc tài liệu, 2026-11-11): `onStart { emit(null) }` trong flow nguồn làm UI thấy `null` mỗi lần flow chạy lại; đặt giá trị "chưa nạp" ở `stateIn`, không ở nguồn.

## HT — công tắc Home (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/HT-home-toggles.md](../knowledgements/HT-home-toggles.md)

- **(2026-11-13, đọc code, HT) — `search`:** cổng cử chỉ vuốt xuống chỉ là một điều kiện trong `enabled` của `SearchSwipeDetector` (detector chỉ quan sát); tắt không đụng thứ tự `HomeTouchRouter` hay các cử chỉ khác. Mép trên vốn thuộc hệ thống (M8b D4) nên thanh thông báo không bị ảnh hưởng.

## CP — tìm danh bạ theo số (2026-11-13, Vsmart Live 4). Nguồn: [knowledgements/CP-contacts-phone.md](../knowledgements/CP-contacts-phone.md)

- **`Phone.CONTENT_URI` không cần `IN`:** một truy vấn toàn bảng rồi ghép theo `contactId` trong RAM; đừng tin thứ tự provider (ColorOS bỏ ORDER BY).
- **Số điện thoại VN:** quy `+84`/`0084` về `0` và bỏ 0 trung gian; chuẩn hóa một lần vào chỉ mục.
- **Đường nóng của chỉ mục:** không sắp xếp toàn bộ hit và không dựng chuỗi trong vòng lặp từng nhãn. Sắp xếp `compareBy().thenBy()` trên ~1000 hit, `Int?` bọc và chuỗi `" $q"` dựng lại mỗi nhãn làm mỗi phím ~18 ms với 2000 người nhiều số (đo được, debug, Live 4); thùng theo (hạng, loại) tối đa `maxResults`, `Int` thay `Int?` và dựng chuỗi một lần đưa p50 xuống ~3–9 ms.
- **Tách thời gian bằng log (tên / số / ghép) trước khi đoán** nguyên nhân chậm.
- **Không tên/số trong log kể cả với dữ liệu giả**; `toString` của data class chứa số phải ghi đè.
