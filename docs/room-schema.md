# Thiết kế dữ liệu Room — giải thích từng trường

Trạng thái: **đã hiện thực ở M4** (2026-10-27) — tạo 2026-10-16. Thuộc mốc M4 trong [project_structure.md](project_structure.md). **Schema v2 (CF, 2026-11-17):** bảng `hidden_apps` thay bằng `app_overrides` (mục 4). Bản đồ mọi lựa chọn người dùng (kể cả ngoài Room): [config-schema.md](config-schema.md).
File này thay thế và mở rộng mục 6 của `project_structure.md`.

---

## 0. Database lưu cái gì, và không lưu cái gì

| Lưu trong Room | Không lưu trong Room |
|---|---|
| **Vị trí** của từng thứ trên màn hình: item nào ở trang nào, ô nào, trong thư mục nào, thứ tự ra sao | **Danh sách app của máy** — luôn lấy sống từ `LauncherApps` |
| Thư mục người dùng tạo (tên, vị trí) | **Nhãn app** và **icon** — lấy từ hệ thống, cache trong RAM/đĩa (`IconCache`), không phải DB |
| Tùy chỉnh theo từng app: ẩn, tên riêng, icon riêng (`app_overrides`, từ v2) | Cài đặt launcher (cỡ lưới, kiểu icon…) — DataStore |
| Số trang và thứ tự trang | Ảnh nền — file trong `filesDir` |

Đây là quyết định quan trọng nhất: **DB không phải bản sao của danh sách app.** Nếu cache danh sách app vào DB thì mỗi lần cài/gỡ/cập nhật app lại phải đồng bộ hai nguồn, và sai lệch giữa chúng là loại bug khó thấy nhất của launcher (icon mồ côi, app đã gỡ vẫn hiện). Cách này đổi lại: mỗi lần mở app phải lọc bỏ những hàng trỏ tới app không còn tồn tại — một phép `filter` rẻ, làm một lần khi nạp.

Vì sao Room chứ không phải 1 file JSON (nhắc lại cơ sở của quyết định A3):

| Lý do | Cụ thể |
|---|---|
| Ghi từng phần | Thả 1 icon = `UPDATE` 1 hàng. JSON phải ghi lại cả bố cục mỗi lần thả |
| Truy vấn theo package | Gỡ app = `DELETE WHERE packageName = ?`. JSON phải nạp hết, lọc, ghi lại |
| Flow | `@Query` trả `Flow<List<...>>` → UI tự cập nhật khi DB đổi, không cần tự phát sự kiện |
| Migration có kiểu | Đổi schema là một `Migration` được compiler kiểm tra, có file schema để so sánh |

---

## 1. Hình dạng tổng thể: một bảng phẳng

```
home_pages                 launcher_items
┌──────────┐               ┌──────────────────────────────────┐
│ id       │◄──── pageId ──│ container  ← -101 dock, hoặc     │
│ rank     │               │              id của thư mục       │
└──────────┘               │ pageId     ← trang nào (nếu home)│
                           │ gridX,gridY,spanX,spanY, rank    │
     hidden_apps           │ itemType, packageName, className │
┌──────────────┐           │ userSerial, title, shortcutId,   │
│ packageName  │           │ appWidgetId                      │
│ className    │           └──────────────────────────────────┘
│ userSerial   │                     ▲         │
└──────────────┘                     └─────────┘
                              thư mục là một hàng, và
                              con của nó có container = id của nó
```

**Một bảng cho mọi loại item** (app, thư mục, shortcut, widget) thay vì mỗi loại một bảng. Lý do:

1. **Kéo-thả là thao tác nóng nhất của launcher, và nó không phân biệt loại.** Kéo một app từ trang 2 vào dock, hay kéo một thư mục sang trang 3, hay kéo một widget — tất cả đều là "đổi chỗ". Với một bảng phẳng, mọi trường hợp là **một câu `UPDATE` trên một hàng** (đổi `container`, `pageId`, `gridX`, `gridY`, `rank`). Nếu tách bảng theo loại thì kéo một app từ trang vào thư mục thành `DELETE` ở bảng này + `INSERT` ở bảng kia, phải bọc transaction, và mỗi cặp nguồn–đích là một đường code riêng.
2. **Hình học của ô là như nhau cho mọi loại.** App, thư mục, widget đều chiếm một hình chữ nhật `(gridX, gridY, spanX, spanY)` trong cùng một lưới. Tách bảng là nhân bản 4 cột này lên 4 chỗ, và logic kiểm tra chỗ trống phải hỏi cả 4 bảng.
3. **Thư mục vừa là item vừa là chỗ chứa.** Nó chiếm một ô trên trang (nên phải nằm trong bảng item), đồng thời chứa item khác. Bảng phẳng giải quyết bằng cách tự trỏ: thư mục là một hàng `id = 57`, các app trong nó có `container = 57`. Một cơ chế duy nhất cho "cái gì nằm trong cái gì".
4. Đây cũng là cách Launcher3 (launcher gốc của AOSP) làm, bảng `favorites` — một thiết kế đã chạy trên hàng tỉ máy.

Cái giá phải trả, nói thẳng: **nhiều cột để trống.** Một hàng app có `appWidgetId = null`, `shortcutId = null`; một hàng thư mục có `packageName = null`. Đổi lại là code kéo-thả đơn giản. Với vài trăm hàng thì dung lượng không phải vấn đề.

---

## 2. Bảng `home_pages` — vì sao cần một bảng chỉ có 2 cột

```kotlin
@Entity(tableName = "home_pages")
data class HomePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rank: Int                       // thứ tự trang, 0 là trang đầu
)
```

Bản thiết kế đầu của tôi không có bảng này — trang chỉ là một số `pageIndex` trên mỗi item. Sửa lại vì hai thứ không làm được:

| Vấn đề | Với `pageIndex` trên item | Với bảng `home_pages` |
|---|---|---|
| **Trang trống** | Không tồn tại được. Trang = "có item nào mang pageIndex này". Vào chế độ chỉnh sửa, người dùng vuốt sang tạo trang mới rồi chưa kịp thả icon vào → trang biến mất | Trang là một hàng, tồn tại độc lập với việc có item hay không |
| **Đổi thứ tự trang** | Kéo trang 3 lên đầu = `UPDATE` **mọi item** của mọi trang | `UPDATE` 1 hàng `rank` trong `home_pages`. Item không bị chạm |

Giá: thêm 1 bảng 2 cột, và khi xóa trang phải xử lý item trong đó (dọn hoặc dồn sang trang khác) trong một `@Transaction`.

Hệ quả cho `launcher_items`: trường trang là **`pageId: Long`** (trỏ vào `home_pages.id`), **không phải `pageIndex: Int`**. Lý do giống hệt dòng "đổi thứ tự trang" ở trên: nếu item giữ số thứ tự thì reorder trang là viết lại toàn bộ item; nếu item giữ id thì không.

---

## 3. Bảng `launcher_items` — từng trường

```kotlin
@Entity(
    tableName = "launcher_items",
    indices = [
        Index("container", "pageId"),      // truy vấn chính: nạp 1 trang / 1 thư mục / dock
        Index("packageName")               // cài, gỡ, cập nhật app
    ]
)
data class LauncherItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: Long,
    val pageId: Long = 0,
    val gridX: Int = 0,
    val gridY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val rank: Int = 0,
    val itemType: Int,
    val packageName: String? = null,
    val className: String? = null,
    val userSerial: Long = 0,
    val title: String? = null,
    val shortcutId: String? = null,
    val appWidgetId: Int? = null
)
```

### `id: Long` — khóa chính, tự tăng

Vì sao cần id riêng thay vì khóa tổ hợp (ví dụ `packageName + className + userSerial`):

- Thư mục **không có** component nào để làm khóa.
- Một app có thể xuất hiện **nhiều lần**: iOS 26 cho phép cùng một app ở nhiều trang, và dock thường trùng với một icon trên trang. Khóa theo component sẽ cấm việc đó.
- Thư mục cần một con số để con nó trỏ vào (`container = id`). Khóa tổ hợp không dùng được cho việc này.

### `container: Long` — item này nằm ở đâu

| Giá trị | Nghĩa |
|---|---|
| `-100` (`CONTAINER_HOME`) | nằm trên một trang màn hình chính; trang nào thì xem `pageId` |
| `-101` (`CONTAINER_DOCK`) | nằm trong dock; thứ tự xem `rank` |
| `-102` (`CONTAINER_TODAY`) | TV (2026-11-21): màn widget bên trái trang 1. Chỉ widget (`itemType` 3, 4); thứ tự xem `rank`; `pageId`, `gridX/gridY` = 0; `spanX/spanY` là cỡ widget. Không đổi schema |
| `> 0` | nằm trong thư mục có `id` bằng số đó; thứ tự xem `rank` |

Vì sao **một cột** thay vì hai cột `containerType: Int` + `folderId: Long?` (có khóa ngoại):

1. "Chuyển item sang chỗ khác" là **một phép gán**, không phải hai. Hai cột cho phép trạng thái vô nghĩa tồn tại được (`containerType = DOCK` mà `folderId` vẫn còn giá trị cũ) — đúng loại bug chỉ lộ ra sau vài lần kéo-thả.
2. Câu truy vấn nạp nội dung giống nhau cho mọi chỗ chứa: `SELECT * FROM launcher_items WHERE container = :c`. Dock, thư mục, trang đều dùng chung một hàm DAO.
3. Khóa ngoại `onDelete = CASCADE` nghe hay nhưng **sai hành vi**: xóa một thư mục **không** nên xóa các app trong đó — chúng phải được dồn ra trang hoặc bỏ khỏi bố cục tùy cách người dùng xóa. Nên dù có FK thì vẫn phải viết tay logic đó, FK không giúp gì mà còn chặn thứ tự ghi.

Cái giá: không có FK nên DB cho phép hàng mồ côi (con trỏ tới thư mục đã bị xóa). Chống bằng hai lớp: (a) mọi thao tác xóa thư mục đi qua một hàm `@Transaction` duy nhất trong DAO; (b) khi nạp bố cục, bỏ qua hàng có `container > 0` mà không tìm thấy thư mục cha — và vì dự án không có test tự động, đây là chỗ cần **log cảnh báo** để phát hiện khi chạy tay.

**Hiện thực thư mục ở G2a (không đổi schema):** thư mục = hàng `itemType = FOLDER`, `container = HOME`, 1×1; con = hàng `itemType = APP` với `container = id thư mục`, `pageId = 0`, thứ tự theo `rank`. Quy tắc giải tán (D9) chạy TRONG transaction của mọi thao tác làm giảm số con (kéo ra, ẩn, gỡ app, kéo vào thư mục khác): còn ≥ 2 con thì giữ, còn 1 con thì con về đúng ô của thư mục rồi xóa thư mục, hết con thì xóa thư mục (và dồn ô như icon thường nếu do gỡ app). Tối đa 9 con; thư mục không vào dock, không vào thư mục khác. Đếm con theo hàng trong DB nên app tạm không khả dụng vẫn là con. Khi nạp bố cục, con trỏ tới thư mục không tồn tại bị bỏ qua kèm log cảnh báo, thư mục không còn con cũng bị bỏ qua.

### `pageId: Long` — trang nào

- Chỉ có nghĩa khi `container = -100`. Với dock và item trong thư mục thì để `0` và bỏ qua.
- Để `0` thay vì `null`: tránh hàng loạt `?.` trong code và tránh cách SQLite sắp xếp NULL. Bù lại phải ghi rõ bất biến này ở một chỗ (file entity) vì compiler không kiểm tra giúp.
- Lý do dùng id thay vì chỉ số: mục 2.

### `gridX`, `gridY: Int` — ô trong lưới, tính từ góc trên-trái của item

Vì sao toạ độ 2 chiều thay vì một số thứ tự (ví dụ `position = 0..23`):

1. **Widget và thư mục chiếm nhiều ô.** Một widget 2×2 không diễn tả được bằng một số thứ tự. Mà nếu đợi đến G3 mới thêm thì phải migration.
2. **Lưới đổi kích thước được** (cài đặt `grid_preset`, TT#20; ở M8a thay cho cặp `grid_columns`/`grid_rows`). Với toạ độ, khi lưới từ 4×6 thành 5×6 thì mọi icon giữ nguyên hàng/cột, chỉ có cột thứ 5 trống ra — dễ hiểu với người dùng. Với số thứ tự, mọi icon bị xô lệch sang chỗ khác và bố cục trông như bị trộn.
3. iOS xếp icon theo hàng rồi tới cột, có khoảng trống ở giữa được (từ iOS 18). Toạ độ biểu diễn được khoảng trống; số thứ tự thì không, trừ khi thêm hàng "ô trống" giả.

**Lỗ hổng cần quyết (xem mục 6):** khi lưới **thu nhỏ** (5 cột → 4 cột) thì icon ở cột thứ 5 đi đâu. **Đã chốt và làm ở M8a (2026-10-30):** xem "Cập nhật M8a" ở mục 6.

### `spanX`, `spanY: Int` — kích thước tính theo ô, mặc định 1×1

- Có mặt ngay từ M4 dù widget mới làm ở G3, vì thêm cột sau là migration, và ràng buộc B8 đã nói ô lưới cần span.
- Thư mục hiện vẽ 1×1, nhưng nếu sau này muốn thư mục lớn (iOS 26 có thư mục rộng hơn ô icon) thì không phải đổi schema.
- Nguyên tắc chung cho việc thêm cột "để dành": **chỉ thêm cột mà nếu thiếu sẽ buộc migration cho tính năng đã nằm trong kế hoạch** (span, `userSerial`). Không thêm cột cho ý tưởng chưa vào khung (ví dụ icon riêng cho từng item — mục 4 của Launcher-Features chưa có).

### `rank: Int` — thứ tự trong chỗ chứa một chiều

- Dùng cho **dock** (một hàng) và **nội dung thư mục** (danh sách phân trang 3×3 kiểu iOS).
- Vì sao không dùng luôn `gridX` cho dock: `gridX` mang nghĩa "cột trong lưới của trang". Dock không nằm trong lưới đó, và nếu sau này dock thành 2 hàng hoặc có số ô khác lưới thì nghĩa của `gridX` bị lệch. Một trường riêng giữ nghĩa sạch.
- Trong thư mục, `rank` + cỡ trang thư mục suy ra được vị trí, nên không cần toạ độ. Nếu sau này muốn cho phép đặt icon tự do trong thư mục thì `gridX/gridY` đã có sẵn, chưa dùng.
- Khi chèn vào giữa: dồn `rank` của các item sau lên 1 trong cùng một `@Transaction`. Không dùng `rank` kiểu số thực (0.5) — nó tránh được việc dồn nhưng tích lũy sai số và khó đọc khi debug.

### `itemType: Int` — loại item

| Giá trị | Loại | Các trường bắt buộc kèm theo |
|---|---|---|
| `0` | APP | `packageName`, `className`, `userSerial` |
| `1` | FOLDER | `title` |
| `2` | SHORTCUT (deep shortcut, TT#7) | `packageName`, `shortcutId`, `userSerial` |
| `3` | WIDGET (AppWidgetHost, TT#19, G3c) | `packageName` + `className` = provider, `userSerial` = hồ sơ của provider, `appWidgetId`, `spanX`, `spanY` |
| `4` | BUILTIN_WIDGET (widget tự vẽ, TT#13, G2c) | `className` = mã loại (`clock`, `calendar`, `weather`, `photo` G3b, `recents` WF 2026-11-25: "App gần đây" trên lưới Home, `battery` BW 2026-11-25: widget Pin 2 × 2), `spanX`, `spanY`; `packageName` và `appWidgetId` null |

Cấu hình riêng của từng widget tự vẽ (WX 2026-11-13: địa điểm của widget thời tiết; G3b 2026-11-16: danh sách ảnh và vùng cắt của widget ảnh, `no_backup/photo_widgets.json`) **không nằm trong Room**: lưu ở `no_backup/weather_places.json`, khóa theo `launcher_items.id`. An toàn vì `id` là `AUTOINCREMENT` (không dùng lại) và không đường ghi nào xóa-rồi-tạo-lại hàng widget (đã đọc mọi đường, xem plan WX mục D1). Khi nhiều loại widget cần cấu hình thì xét một cột `config` + migration chung.

Vì sao `Int` chứ không phải enum Kotlin lưu qua TypeConverter: số đã ghi xuống DB thì **không được đổi nghĩa**, còn tên enum trong code thì sẽ bị đổi khi refactor. Lưu `Int` tách số khỏi tên, và bảng chuyển đổi nằm ở một chỗ duy nhất trong `LauncherItemEntity`. Nếu lưu tên enum, một lần đổi tên class là một lần dữ liệu người dùng đọc không ra.

### `packageName`, `className: String?` — component để mở app

- **Hai cột tách riêng**, không gộp thành một chuỗi `"pkg/cls"`. Vì truy vấn thường xuyên nhất của vòng đời launcher là theo **package**: app bị gỡ (`DELETE WHERE packageName = ?`), app cập nhật (đổi icon), app bị disable. Có index trên `packageName` thì các việc này là một câu lệnh; nếu gộp chuỗi thì phải `LIKE 'pkg/%'` — không dùng được index và sai khi tên package là tiền tố của package khác.
- `null` với thư mục.

### `userSerial: Long` — hồ sơ người dùng, mặc định 0 (chủ máy)

- Dùng `UserManager.getSerialNumberForUser()`. `UserHandle` **không lưu được** xuống DB và không bền giữa các lần khởi động; serial number là con số hệ thống đảm bảo bền và duy nhất.
- Vì sao có ngay từ M4 dù chưa làm work profile: app trong work profile có **cùng `ComponentName`** với bản cá nhân. Không có cột này thì hai app đó là cùng một hàng dưới góc nhìn của mọi logic so sánh/dedupe, và phát hiện ra lúc đã có người dùng thật thì phải migration cộng với sửa mọi chỗ so sánh. Một cột `Long` mặc định 0 là bảo hiểm rẻ.

### `title: String?` — tên thư mục, hoặc nhãn do người dùng đổi

- AG (2026-11-26): thư mục do bố cục lần đầu gom sẵn hoặc do Cài đặt → Chung → "Gom nhóm ứng dụng" tạo có `title` = tên nhóm theo ngôn ngữ app lúc tạo (vd "Tiện ích", "Google", "OPPO"); Cài đặt nhận ra thư mục đã gom bằng cách so tên này (không phân biệt hoa thường). Không có cột đánh dấu riêng.

- `null` = dùng nhãn hệ thống trả về.
- Vì sao **không** lưu nhãn app vào đây cho mọi hàng: nhãn phụ thuộc **ngôn ngữ máy** và đổi khi app cập nhật. Lưu lại là tự tạo dữ liệu cũ — người dùng đổi ngôn ngữ hệ thống mà icon vẫn tên cũ.

### `shortcutId: String?` — id của deep shortcut (TT#7)

- Chỉ có nghĩa với `itemType = 2`, đi kèm `packageName` và `userSerial` — ba thứ này là bộ khóa mà `LauncherApps.startShortcut()` cần.
- Shortcut phải được **pin** (`pinShortcuts`) mới dùng lại được sau khi khởi động, nên khi nạp bố cục phải đối chiếu với danh sách shortcut còn được pin và bỏ hàng không còn hợp lệ.

### `appWidgetId: Int?` — id widget do hệ thống cấp (TT#19)

- `null` cho mọi loại khác. Dùng nullable thay vì `0` vì `AppWidgetManager.INVALID_APPWIDGET_ID` chính là `0`, nên `0` vừa là "không có" vừa là "id không hợp lệ" — hai nghĩa chồng nhau.
- Id này do `AppWidgetHost.allocateAppWidgetId()` cấp và **phải được giải phóng** (`deleteAppWidgetId`) khi xóa widget, nếu không hệ thống rò rỉ id. `deleteAppWidgetId` là lệnh binder nên **không nằm trong transaction Room** được: G3c (2026-11-16) ghi nhận id trong transaction xóa hàng, giải phóng ngay sau commit trong cùng vùng khóa của `LayoutReconciler`, và lần quét khởi động (`getAppWidgetIds()` trừ id trong DB và id đang trong luồng thêm) là lưới an toàn. Id sống qua `pm clear`, nên khi DB rỗng thì `deleteHost`.

---

## 4. Bảng `app_overrides` (v2, CF 2026-11-17; thay `hidden_apps` của v1)

```kotlin
@Entity(tableName = "app_overrides", primaryKeys = ["packageName", "className", "userSerial"])
data class AppOverrideEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val hidden: Boolean = false,          // 5.1 Ứng dụng ẩn
    val customLabel: String? = null,      // 5.3 Thay đổi tên (trước CF: DataStore `app_labels`)
    val customIconPath: String? = null,   // 5.2 Thay đổi biểu tượng (chưa làm), file trong `files/custom_icons/`
    val offHome: Boolean = false          // v3 (TF): "Xóa khỏi Màn hình chính" — không ở Home nhưng KHÔNG ẩn
)
```

**v2 → v3 (TF, 2026-11-23):** thêm cột `offHome INTEGER NOT NULL DEFAULT 0` bằng `LauncherDatabase.MIGRATION_2_3` (viết tay, `ALTER TABLE`). Trước v3, "Xóa khỏi Màn hình chính" ghi `hidden = 1` nên app biến khỏi Library và tìm kiếm. Từ v3: `hidden` = ẩn khỏi mọi nơi; `offHome` = chỉ không có trên Home (vẫn ở Library, tìm kiếm, app gần đây). Bộ đặt app mới (`selectFresh`, `placeNewInTransaction`, dựng lưới mặc định) bỏ qua cả hai cờ. `LayoutReconciler.removeFromHome` ghi cờ và gỡ hàng placement; kéo app từ Library ra Home (`addAppAt`, LD) tạo hàng mới và bỏ cờ. App đã "xóa khỏi MH chính" bằng bản cũ vẫn mang `hidden = 1` (không phân biệt được với app ẩn): bỏ ẩn ở Cài đặt > App ẩn.

**Một hàng = một app có ít nhất một tùy chỉnh.** Ghi xong mà mọi cột về mặc định (`hidden = 0`, `offHome = 0`, hai cột kia null) thì xóa hàng (`pruneEmpty`). Ví dụ: bỏ ẩn một app đã đổi tên thì hàng còn (giữ tên); khôi phục tên của app không ẩn thì hàng mất.

**Ai đọc/ghi:** `HiddenAppDao` (cột `hidden`; giữ tên và chữ ký hàm của bản v1 nên `LayoutReconciler` không đổi), `AppOverrideDao` (cột `customLabel`, dùng bởi `AppLabelRepositoryImpl`). SQLite của API 26 (3.18) không có UPSERT: ghi một cột = `INSERT OR IGNORE` hàng mặc định + `UPDATE`, trong `@Transaction`.

**Dọn dẹp:**

| Sự kiện | Việc | Ở đâu |
|---|---|---|
| Gỡ app thật | Xóa cả hàng của package (mọi tùy chỉnh) | `HiddenAppDao.deleteByPackage` trong transaction gỡ app của `LayoutReconciler`; `AppLabelRepository.removePackage` cũng xóa tên (thừa nhưng vô hại) |
| Bỏ hồ sơ công việc | Xóa mọi hàng của user | `HiddenAppDao.deleteByUser`; `AppLabelRepository.removeUser` |
| App đổi activity khởi động khi cập nhật | `UPDATE className`: mọi tùy chỉnh đi theo | `HiddenAppDao.renameComponent` (FX3 A3) |

Vì sao là bảng riêng chứ không phải cột trên `launcher_items`:

- Tùy chỉnh là tính chất của **app**, không phải của một ô trên màn hình. Một app bị ẩn thường **không có** hàng nào trong `launcher_items` (nó chưa từng được đặt, hoặc đã bị bỏ khỏi bố cục); một app có thể có nhiều hàng (lưới, dock, thư mục) mà tên riêng chỉ có một.
- Nếu dùng cột thì phải tạo hàng "ảo" không có vị trí chỉ để mang cờ — và mọi truy vấn nạp trang phải nhớ loại chúng ra.
- Khóa chính tổ hợp (component + user) đúng với bản chất "mỗi app nhiều nhất một lần".

**Đổi v1 → v2 không có Migration** (chủ dự án chốt 2026-11-17, app chưa phát hành): `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)` xóa cả 3 bảng khi cài đè lên bản v1 (bố cục Home dựng lại mặc định). Từ v2 trở đi mọi đổi schema phải có Migration viết tay.

---

## 5. Ví dụ dữ liệu thật

Bố cục: trang 1 có Chrome và một thư mục "Mạng xã hội" (chứa Facebook, Zalo); dock có Điện thoại; trang 2 trống (vừa tạo).

`home_pages`

| id | rank |
|---|---|
| 1 | 0 |
| 2 | 1 |

`launcher_items`

| id | container | pageId | gridX | gridY | spanX | spanY | rank | itemType | packageName | className | title |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 10 | -100 | 1 | 0 | 0 | 1 | 1 | 0 | 0 APP | com.android.chrome | ...Main | null |
| 11 | -100 | 1 | 1 | 0 | 1 | 1 | 0 | 1 FOLDER | null | null | Mạng xã hội |
| 12 | **11** | 0 | 0 | 0 | 1 | 1 | **0** | 0 APP | com.facebook.katana | ...Main | null |
| 13 | **11** | 0 | 0 | 0 | 1 | 1 | **1** | 0 APP | com.zing.zalo | ...Main | null |
| 14 | **-101** | 0 | 0 | 0 | 1 | 1 | **0** | 0 APP | com.android.dialer | ...Main | null |

Đọc bảng này: hàng 12 và 13 có `container = 11` nên chúng nằm trong thư mục ở hàng 11, thứ tự theo `rank`. Hàng 14 có `container = -101` nên ở dock. Trang id 2 không có item nào — vẫn tồn tại vì có hàng trong `home_pages`.

Kéo Facebook ra khỏi thư mục, thả vào ô (2,0) của trang 1:

```sql
UPDATE launcher_items SET container = -100, pageId = 1, gridX = 2, gridY = 0, rank = 0 WHERE id = 12
```

Một hàng, một câu lệnh. Đây là lý do chính của cả thiết kế.

**Số hàng thực tế trên Vsmart Live 4 sau seed** (đo lúc kiểm thử Task 4, 2026-10-27): `rows=87` — `byContainer={-101=4, -100=83}` (4 item dock, 83 item trên 4 trang home), `pagesRows=4` (4 trang trong `home_pages`). Dùng con số này làm mốc so sánh khi nghi DB lệch — số hàng giảm dần sau các lượt gỡ app test trong phiên này (85, 86 là do app test đã bị gỡ/khôi phục khác số ban đầu, không phải lệch DB).

---

## 6. Điểm cần bạn quyết

| # | Câu hỏi | Đề xuất của tôi |
|---|---|---|
| Q1 | **Lưới thu nhỏ thì icon tràn đi đâu?** Người dùng đổi từ 5 cột về 4 cột trong cài đặt, các icon ở cột thứ 5 không còn chỗ | **[ĐÃ THAY ở M8a: re-flow toàn bộ theo thứ tự đọc, nén khe — xem "Cập nhật M8a" bên dưới bảng]** Bản đầu: dồn vào ô trống gần nhất trên cùng trang theo thứ tự trên→dưới, trái→phải; hết chỗ thì sang trang sau; hết trang thì tạo trang mới. **Không bao giờ xóa item.** Cảnh báo trước khi đổi cỡ lưới |
| Q2 | **Icon trong thư mục có cần đặt tự do không?** | Không — dùng `rank`, thư mục tự xếp kín như iOS. `gridX/gridY` để sẵn nếu sau này đổi ý |
| Q3 | **Một app được xuất hiện nhiều lần trên bố cục không?** | Có (iOS 26 cho phép, và dock thường trùng icon trên trang). Schema đã hỗ trợ sẵn; chỉ cần không thêm unique index theo component |
| Q4 | **Có chặn hai item trùng ô ở tầng DB không?** (unique index trên `container, pageId, gridX, gridY`) | Không. Trong lúc kéo-thả có trạng thái trung gian trùng ô, và index đó không chặn được va chạm của item nhiều ô (span). Việc kiểm tra chỗ trống nằm ở `MoveItemUseCase` |
| Q5 | **Lần đầu mở launcher thì bố cục ban đầu là gì?** | Tự xếp toàn bộ app đã cài theo thứ tự chữ cái, điền từ trang 1; dock lấy 4 app có `intent` phổ biến (điện thoại, tin nhắn, trình duyệt, camera) nếu tìm thấy, không thì để trống |

## 7. Quyết định bổ sung đã chốt (2026-10-27, trước khi viết plan M4)

Bốn điểm mục 0–6 để mở, chốt trước khi triển khai:

| # | Câu hỏi | Quyết định | Hệ quả |
|---|---|---|---|
| Q6 | Cache nhãn app vào DB để chữa cold start? | **Không ở M4.** Nguyên tắc mục 0 giữ nguyên: DB lưu **vị trí**, không lưu danh sách/nhãn app. Việc này tách thành **một task phụ ngoài luồng, làm sau M6** (lúc đó có Oppo A3x để đo thật) | Schema v1 không có bảng cache; thêm sau không ảnh hưởng 3 bảng hiện tại **Cập nhật 2026-11-05:** đã làm ngoài Room bằng file JSON, xem LC (`plans/LC-label-cache.md`). |
| Q7 | Cài app mới thì icon đứng đâu? | **Sau icon cuối cùng** (đổi ở FX3, 2026-11-11, chủ dự án): ở trang cuối nếu còn ô tự do sau icon cuối, không thì tạo trang mới; không bao giờ vào ô trống giữa trang. Trước FX3: ô trống đầu tiên, quét từ trang 1 | Không xáo bố cục người dùng đã sắp (từ M7). Giống iOS; **không** giống Launcher3 (`WorkspaceItemSpaceFinder` dùng ô trống đầu tiên). Gỡ app vẫn dồn ô đó (`compactPageAfterRemoval`) |
| Q8 | Gỡ app thì ô đó xử lý thế nào? | **Dồn**: các icon sau dịch lên lấp ô trống | Không để lỗ hổng giữa lưới. Một lần gỡ ghi lại nhiều hàng — chấp nhận được. **Đã xem lại ở M7 (2026-10-29) — giữ nguyên dồn:** plan M7 chốt giữ hành vi dồn cho gỡ app và áp cả cho thao tác ẩn (Ruling G2); kéo icon đi chỗ khác thì để lại ô trống. Tùy chọn "giữ ô trống" không làm, chỉ xét lại nếu người dùng phàn nàn. **Sửa 2026-10-30 (M8c):** gỡ app THẬT cũng chỉ dồn TRONG TỪNG TRANG (cùng `GridRemovePlanner` với thao tác ẩn); trước đó `compactHome` dồn cả màn hình từ trang đầu làm mất bố cục "mỗi app một trang" và xóa trang trống ở giữa. Chỉ trang CUỐI không còn item mới bị xóa (≥ 1 trang) |
| Q9 | Schema v1 gồm bảng nào? | **Cả 3 bảng** (`launcher_items`, `home_pages`, `hidden_apps`) ngay từ v1, dù `hidden_apps` chưa có UI (M7) và trang trống chưa tạo được (M7) | Tránh `Migration` viết tay về sau — mà dự án không có test để đỡ migration (A7) |
| Q10 | App trong dock bị gỡ thì sao? | **Để trống ô đó, không tự lấp.** Dock được phép còn 3, 2, 1 hoặc 0 icon | Khớp hành vi dock của iOS (người dùng khảo sát trên iPhone XS Max / iOS 18: kéo hết app ra thì dock trống được). Tự lấp là tự bày ra thứ người dùng không yêu cầu, và M7 sẽ phải bỏ đi. Hành vi iOS 26 cho đúng ca này **chưa kiểm chứng trực tiếp** |
| Q11 | Package đã cài + enable nhưng **không resolve được activity LAUNCHER** nào (`query-activities -a MAIN -c LAUNCHER` rỗng) thì hàng trong DB xử lý sao? | **Chấp nhận là hạn chế đã biết, không code thêm ở M4** (hướng 1). `catchUp`/`isPackageInstalled()` chỉ phân biệt được "còn cài" vs "đã gỡ hẳn" qua cờ `FLAG_INSTALLED`; không phân biệt được ca này với "tạm không khả dụng" (Q8/Q10) mà không có rủi ro xóa nhầm hàng của app chỉ đang disable-user/suspend/rút thẻ nhớ | Hàng đó **ở lại DB vĩnh viễn**, ô lưới bỏ trống vì không có app sống để vẽ icon — giống hệt biểu hiện "tạm không khả dụng" nhưng không tự hồi phục. Đo được 1 ca thật: `gambi.global.ioslauncher` (id=40) trên Live 4. M7+ nếu cần dọn ca này thì phải thêm một nguồn kiểm tra khác (ví dụ gọi `getActivityList` trực tiếp trong `catchUp` thay vì chỉ `isPackageInstalled`), chưa làm ở M4 |

**Cập nhật M7 (2026-10-29):** (1) `seedIfEmpty` chỉ dựng bố cục từ app KHÔNG nằm trong `hidden_apps`, không còn app nào thì không dựng gì — đây là "dấu đã-seed" tối thiểu cho ca người dùng ẩn hết app (trong M7 cách duy nhất để `launcher_items` về 0 hàng); `placeNewInTransaction` cũng bỏ qua app đang ẩn. (2) Hàng `hidden_apps` được dọn khi gỡ THẬT app (`removePackages`) và khi bắt kịp lúc mở (`catchUp`, cùng phép `isPackageInstalled`), để cài lại app không bị coi là "đã ẩn". (3) Ẩn icon trong lưới **dồn** trong cùng trang (Q8 áp lại cho thao tác ẩn); kéo icon đi chỗ khác để lại ô trống. Giới hạn: ẩn HẾT app rồi khởi động lại thì Home trống và không có đường vào edit mode (ghi nợ M8+).

**Cập nhật M8a (2026-10-30):** (1) Cỡ lưới nằm ở **DataStore** (`grid_preset`), **không đổi schema Room**; `launcher_items`/`home_pages` vẫn chỉ lưu tọa độ. (2) Q1 được giải bằng **re-flow theo thứ tự đọc** (`GridReflowPlanner`, `LayoutReconciler.reflowTo`) chứ không phải "dồn vào ô trống gần nhất": mọi icon lưới chính trải phẳng theo (trang, `y`, `x`), **nén hết khe trống**, chia lại theo cỡ mới, tạo/xóa trang cuối cho khớp (luôn còn ≥ 1 trang); một chiều, có hộp xác nhận, không hoàn tác. Hệ quả: dù lưới **mở rộng** (4×6 → 5×6) icon cũng được xếp lại chứ không giữ nguyên hàng/cột như điểm 2 ở trên mô tả ban đầu. Không đụng dock và `hidden_apps`; có item `spanX/spanY > 1` thì bỏ qua (giống `compactHome`). (3) Thứ tự ghi cài đặt/DB do `ChangeGridPresetUseCase` chọn theo bao hàm của ba preset (thu nhỏ: DB trước; mở rộng: cài đặt trước) để icon luôn nằm trong lưới hợp lệ giữa hai bước. (4) `LayoutReconciler.ensureFitsGrid()` chạy lúc khởi động (sau seed, trước `catchUp`): nếu có icon HOME ngoài lưới của cỡ hiện hành (DataStore và DB lệch) thì re-flow. (5) Mọi hàm của `LayoutReconciler` giờ đọc cỡ lưới từ cài đặt **một lần, trong mutex, trước transaction** (`currentGrid()`) thay cho hằng 4×6.

**Cập nhật FX3 (2026-11-11):** (1) Q7 đổi nghĩa (xem bảng): app mới, cả app được bỏ ẩn, đặt **sau icon cuối** (`NewAppPlacementPlanner`); widget (`addWidget`/`addWidgetAt`, G2c) vẫn lấy hình chữ nhật trống đầu tiên. (2) App cập nhật đổi tên activity khởi động: hàng cũ mồ côi được **re-point** `className` sang activity mới (`@Query UPDATE` ở `LauncherItemDao`/`HiddenAppDao`) thay vì để ô ma; **không đổi schema**, `app/schemas/1.json` giữ nguyên; Q11 giữ. Hàng mồ côi đã có sẵn từ trước FX3 không được dọn. (3) Đổi cỡ lưới và ghi DataStore `grid_preset` chạy trong một `mutex.withLock` của `LayoutReconciler` (thứ tự an toàn của M8a giữ nguyên). (4) UI đọc bố cục qua một flow `invalidationTracker.createFlow` trên nhiều bảng, đọc trong một transaction, nên không còn trạng thái nửa cũ nửa mới.

**Điều kiện seed lại (liên quan Q7, Q8 và M7):** việc dựng bố cục lần đầu kiểm tra **số hàng trong DB**, không kiểm cờ `first_run_done` của DataStore — hai thứ nằm ở hai nơi lưu khác nhau và có thể lệch nhau (sao lưu/phục hồi, DB hỏng, seed bị ngắt giữa). Dựa vào số hàng thì mọi trường hợp DB rỗng đều tự chữa ở lần mở kế tiếp. **Nhưng từ M7**, khi người dùng xóa được icon khỏi màn hình chính, "0 hàng" không còn đồng nghĩa với "máy mới": lúc đó phải đổi sang một dấu đã-seed nằm **trong DB**, nếu không việc người dùng dọn sạch lưới sẽ bị hiểu là máy mới và bị seed lại.

**Phân biệt bắt buộc giữa "gỡ" và "tạm không khả dụng"** (suy ra từ Q8, ghi rõ để không ai hiểu lẫn): chỉ `onPackageRemoved` mới xóa hàng và dồn ô. `onPackagesUnavailable` / app bị suspend / app trên thẻ nhớ bị tháo thì **giữ nguyên hàng**, chỉ không vẽ icon đó; khi khả dụng lại nó về đúng chỗ cũ. Xóa hàng trong trường hợp này là làm mất vị trí người dùng vì một lý do tạm thời.

---

Ngoài ra `exportSchema = true`, schema commit vào `app/schemas/`, **không bao giờ** `fallbackToDestructiveMigration()`, và vì dự án không có test tự động thì mỗi lần đổi schema phải thử tay: cài bản cũ → tạo bố cục có thư mục và dock → cài bản mới lên trên → kiểm tra bố cục còn nguyên.

**Cập nhật FX5 (2026-11-11):** hồ sơ bị gỡ được dọn **theo user**: thêm `HiddenAppDao.deleteByUser` và `LayoutReconciler.removeUser` (nhận `PackageEvent.Type.USER_REMOVED`). Chỉ thêm truy vấn, **không đổi schema** (`app/schemas/1.json` giữ nguyên).
