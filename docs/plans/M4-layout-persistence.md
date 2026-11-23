# M4 — Lưu bố cục bằng Room

> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans`. Các bước dùng checkbox `- [ ]`.
>
> **Dự án này KHÔNG có test** (A7). Đừng viết unit test, instrumented test, đừng thêm thư viện test. Kiểm chứng tay trên máy thật, mỗi task có lệnh và kết quả mong đợi. Không tick checkbox khi chưa có người xác nhận.
>
> **KHÔNG dùng kính ở mốc này** (A9). **KHÔNG kéo-thả** (M7). **KHÔNG cache nhãn app vào DB** (room-schema Q6 — task phụ P1 sau M6).

**Goal:** Bố cục lưới và dock được lưu xuống Room, sống qua lần khởi động lại, và tự đồng bộ khi người dùng cài hoặc gỡ app — kể cả khi việc đó xảy ra lúc launcher không chạy.

**Architecture:** Ba bảng Room theo [room-schema.md](../room-schema.md). DB lưu **vị trí**, không lưu danh sách hay nhãn app — nhãn vẫn lấy sống từ `AppRepository` và được ghép vào vị trí lúc đọc. `LayoutReconciler` là nơi duy nhất chứa thuật toán seed / thêm / xóa / dồn ô, tách khỏi `LayoutRepositoryImpl` để đọc được.

**Tech Stack:** Room 2.8.5 (`room-runtime`, `room-ktx`, `room-compiler` qua KSP 2.3.11), coroutines `Flow` + `withTransaction`, Hilt, View/XML. Không Compose (B1).

**Spec:** [../room-schema.md](../room-schema.md) — toàn bộ, đặc biệt mục 0 (DB lưu gì / không lưu gì), mục 3 (từng trường), mục 5 (ví dụ dữ liệu), **mục 7 (Q6–Q9, chốt 2026-10-27)**. Kiến trúc chung: [../project_structure.md](../project_structure.md) mục 2 (A1–A9, luật phụ thuộc), mục 3 (`data/layout/`), mục 6, mục 9 (mốc M4). Tính năng: [../Launcher-Features.md](../Launcher-Features.md) **TT#4**.

## Global Constraints

- `minSdk 26`, `targetSdk 37`, Java 11. API từ 27 trở lên phải có nhánh dự phòng cho 26.
- View/XML, ViewBinding, không Compose (B1). Không `LiquidGlassView` (A9).
- **Luật phụ thuộc:** `ui → domain ← data`. `domain` không tham chiếu kiểu ở `data`, không import `android.*` trừ `Bitmap`. Entity và DAO **chỉ** nằm trong `data/layout/db`, không lọt ra `domain` hay `ui`.
- **`exportSchema = true`**, file schema commit vào `app/schemas/`. **Không bao giờ** `fallbackToDestructiveMigration()`. Mỗi lần đổi entity là một `Migration` viết tay — dự án không có test đỡ migration (A7).
- **Máy test mặc định Vsmart Live 4** (A8). Mốc này không cần Oppo A3x.
- Mọi chữ hiển thị vào `strings.xml`.
- Không xóa file ngoài danh sách ở Task 5. `demo/`, `MainActivity`, `activity_main.xml`, `view_extra_glass.xml` giữ đến sau M5; màn demo phải chạy y như trước.
- Thêm thư viện phải cập nhật `libs.versions.toml` **và** mục 8 của `project_structure.md` trong cùng task.
- Commit message tiếng Anh, `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt.
- Lệnh viết cho PowerShell. `adb shell am force-stop gambi.global.os26launcher` trước mỗi lần thử lại trạng thái khởi động.

## Review Focus

Năm trường hợp spec hàm ý nhưng đường đi hạnh phúc không chạm tới:

1. **Gỡ app khi launcher KHÔNG chạy** → lần mở sau phải dọn hàng mồ côi và dồn ô, không hiện ô rỗng, không crash khi nhãn không tìm được. (Task 4)
2. **App tạm không khả dụng** (`pm disable-user`, app trên thẻ nhớ bị tháo, app bị suspend) → **giữ nguyên hàng**, chỉ không vẽ; khả dụng lại thì về **đúng chỗ cũ**. Xóa hàng ở đây là mất vị trí vì một lý do tạm thời (room-schema mục 7). (Task 4)
3. **Hàng mồ côi `container > 0`** mà không có hàng thư mục tương ứng → bỏ qua khi nạp kèm `Log.w`, không ném. Thư mục là G2 nhưng đường code phải chịu được ngay, vì DB cho phép trạng thái đó tồn tại (room-schema mục 3). (Task 2)
4. **DB rỗng nhưng `first_run_done = true`** (người dùng xóa dữ liệu app, hoặc seed bị ngắt giữa) → vẫn phải seed lại, không hiện lưới trống vĩnh viễn. Vì vậy điều kiện seed là **số hàng trong DB**, không phải cờ trong DataStore. (Task 2)
5. **Cài nhiều app liên tiếp trong vài giây** → các lần reconcile chạy chồng nhau không được tạo hàng trùng hay làm mất app. (Task 3)

---

## Task 1: Room, 3 bảng, schema v1

Không đổi hành vi. Sau task này app vẫn dựng bố cục như M3; DB tồn tại nhưng chưa ai đọc.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `.../data/layout/db/LauncherItemEntity.kt`, `HomePageEntity.kt`, `HiddenAppEntity.kt`
- Create: `.../data/layout/db/LauncherItemDao.kt`, `HomePageDao.kt`
- Create: `.../data/layout/db/LauncherDatabase.kt`
- Create: `.../di/DatabaseModule.kt`
- Create (do Room sinh, phải commit): `app/schemas/gambi.global.os26launcher.data.layout.db.LauncherDatabase/1.json`

**Interfaces:**
- Consumes: Hilt + KSP (M0).
- Produces: 3 entity, 2 DAO, `LauncherDatabase`, và các hằng container.

```kotlin
// data/layout/db/LauncherItemEntity.kt
@Entity(
    tableName = "launcher_items",
    indices = [Index("container", "pageId"), Index("packageName")]
)
data class LauncherItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: Long,          // CONTAINER_HOME | CONTAINER_DOCK | id thư mục (>0)
    val pageId: Long = 0,         // chỉ có nghĩa khi container = CONTAINER_HOME
    val gridX: Int = 0,
    val gridY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val rank: Int = 0,            // thứ tự trong dock hoặc trong thư mục
    val itemType: Int,            // ITEM_TYPE_APP | FOLDER | SHORTCUT | WIDGET
    val packageName: String? = null,
    val className: String? = null,
    val userSerial: Long = 0,
    val title: String? = null,
    val shortcutId: String? = null,
    val appWidgetId: Int? = null
) {
    companion object {
        const val CONTAINER_HOME = -100L
        const val CONTAINER_DOCK = -101L
        const val ITEM_TYPE_APP = 0
        const val ITEM_TYPE_FOLDER = 1
        const val ITEM_TYPE_SHORTCUT = 2
        const val ITEM_TYPE_WIDGET = 3
    }
}

// data/layout/db/HomePageEntity.kt
@Entity(tableName = "home_pages")
data class HomePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rank: Int
)

// data/layout/db/HiddenAppEntity.kt — chưa có UI (M7), tạo từ v1 để tránh migration (Q9)
@Entity(tableName = "hidden_apps", primaryKeys = ["packageName", "className", "userSerial"])
data class HiddenAppEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long
)
```

DAO — **không dùng tham số có giá trị mặc định** trong `@Query` (Room sinh code từ chữ ký, giá trị mặc định của Kotlin không tới được SQL); truyền hằng từ phía gọi:

```kotlin
// data/layout/db/LauncherItemDao.kt
@Dao
interface LauncherItemDao {
    @Query("SELECT * FROM launcher_items WHERE container = :container ORDER BY pageId, gridY, gridX")
    fun observeByContainer(container: Long): Flow<List<LauncherItemEntity>>

    @Query("SELECT * FROM launcher_items WHERE container = :container ORDER BY rank")
    fun observeByRank(container: Long): Flow<List<LauncherItemEntity>>

    @Query("SELECT * FROM launcher_items") suspend fun getAll(): List<LauncherItemEntity>
    @Query("SELECT COUNT(*) FROM launcher_items") suspend fun count(): Int
    @Insert suspend fun insertAll(items: List<LauncherItemEntity>): List<Long>
    @Update suspend fun updateAll(items: List<LauncherItemEntity>)
    @Query("DELETE FROM launcher_items WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
}

// data/layout/db/HomePageDao.kt
@Dao
interface HomePageDao {
    @Query("SELECT * FROM home_pages ORDER BY rank") fun observeAll(): Flow<List<HomePageEntity>>
    @Query("SELECT * FROM home_pages ORDER BY rank") suspend fun getAll(): List<HomePageEntity>
    @Insert suspend fun insert(page: HomePageEntity): Long
    @Query("DELETE FROM home_pages WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
}
```

- [x] **Bước 1: Thêm Room vào version catalog và module**

```toml
[versions]
room = "2.8.5"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
```

Bật export schema trong `android { defaultConfig { ... } }`:
```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```
`room-ktx` là bắt buộc, không chỉ để tiện: `withTransaction` dạng `suspend` nằm ở đó, và Task 3 với Task 4 đều cần nó.

- [x] **Bước 2: Ba entity đúng như khối Interfaces ở trên**

Lý do từng trường nằm ở mục 3 của `room-schema.md` — đọc trước khi sửa bất cứ trường nào. Hai điểm hay bị làm sai: `pageId` là **id** của `home_pages` chứ không phải chỉ số trang, và `container` là **một** cột mang ba nghĩa chứ không phải cặp `containerType` + `folderId`.

- [x] **Bước 3: Hai DAO và `LauncherDatabase`**

```kotlin
@Database(
    entities = [LauncherItemEntity::class, HomePageEntity::class, HiddenAppEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherItemDao(): LauncherItemDao
    abstract fun homePageDao(): HomePageDao
}
```

- [x] **Bước 4: `di/DatabaseModule.kt`**

`@Provides @Singleton` cho `LauncherDatabase` bằng `Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db").build()`, và `@Provides` cho từng DAO.

**Không** gọi `fallbackToDestructiveMigration()`. **Không** `allowMainThreadQueries()`.

- [x] **Bước 5: Build và kiểm chứng DB có thật**

```powershell
.\gradlew.bat assembleDebug
Get-ChildItem -Recurse app\schemas -Filter "1.json" | Select-Object FullName
```
Mong đợi: build xanh và có file `1.json`. Mở file đó xem đủ 3 bảng và các index — đây là bằng chứng schema đúng, mạnh hơn đọc code.

Cài rồi kiểm tra file DB được tạo:
```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb shell run-as gambi.global.os26launcher ls -l databases
```
Mong đợi: có `launcher.db`. Nếu chưa có thì bình thường — Room tạo DB khi có truy vấn đầu tiên, mà Task 1 chưa ai truy vấn. Trong trường hợp đó ghi "chưa có, đúng dự kiến" và chuyển sang Task 2.

Màn hình chính phải chạy **y như M3** (lưới 4 trang, dock 4 ô). Không được đổi gì.

- [x] **Bước 6: Commit** (nhớ `git add app/schemas`)

```powershell
git add -A
git commit -m "feat: add Room database with layout schema v1"
```

---

## Task 2: Seed bố cục lần đầu và đọc từ DB

**Files:**
- Create: `.../domain/repository/LayoutRepository.kt`
- Create: `.../data/layout/LayoutRepositoryImpl.kt`
- Create: `.../data/layout/LayoutReconciler.kt`
- Modify: `.../di/RepositoryModule.kt`, `.../ui/home/HomeViewModel.kt`

**Interfaces:**
- Consumes: `AppRepository.apps` (M2), `BuildDefaultLayoutUseCase`, `PickDefaultDockUseCase`, `HomePage`, `PlacedApp`, `AppItem`, `ComponentKey` (M3), `GridMetrics.DEFAULT_COLUMNS/DEFAULT_ROWS/DOCK_SLOTS`.
- Produces:

```kotlin
// domain/repository/LayoutRepository.kt
interface LayoutRepository {
    /** Lưới theo trang, đã ghép nhãn từ danh sách app sống. Phát lại khi DB hoặc danh sách app đổi. */
    val pages: Flow<List<HomePage>>
    /** Dock theo rank, đã ghép nhãn. */
    val dock: Flow<List<AppItem>>
}

// data/layout/LayoutReconciler.kt
@Singleton
class LayoutReconciler @Inject constructor(/* 2 DAO, database, 2 usecase */) {
    /**
     * Dựng bố cục nếu DB rỗng. Idempotent: gọi nhiều lần không tạo hàng trùng.
     * Chạy toàn bộ trong một transaction.
     */
    suspend fun seedIfEmpty(apps: List<AppItem>)
}
```

**Quyết định ghi rõ:** điều kiện seed là `launcherItemDao.count() == 0`, **không** phải cờ `first_run_done` trong DataStore. Cờ đó dùng cho việc hỏi áp wallpaper (M1) và có thể `true` trong khi DB rỗng (Review Focus 4). Hai thứ không liên quan nhau, đừng gộp.

**Cảnh báo cho M7, ghi lại ngay để không ai quên:** cách này tự chữa được DB rỗng, nhưng nó đúng **chỉ khi người dùng chưa thể tự làm rỗng lưới**. Từ M7, nếu người dùng xóa hết icon khỏi màn hình chính thì `count() == 0` sẽ hiểu đó là máy mới và seed lại toàn bộ app — phá đúng việc họ vừa làm. Khi M7 mở đường xóa icon, điều kiện seed phải đổi thành một **dấu đã-seed nằm trong DB** (một hàng trong bảng riêng, hoặc một hàng `home_pages` luôn tồn tại), không phải đếm item. Đừng đổi ở M4: lúc này chưa có đường nào làm rỗng lưới nên đếm item vẫn là cách đơn giản và đúng.

- [x] **Bước 1: `LayoutReconciler.seedIfEmpty`**

Trong một `db.withTransaction { }`: nếu `count() > 0` thì thoát ngay. Ngược lại:
1. `pickDefaultDock(apps, GridMetrics.DOCK_SLOTS)` → chèn vào `container = CONTAINER_DOCK`, `rank` 0..3.
2. `buildDefaultLayout(apps, dock, DEFAULT_COLUMNS, DEFAULT_ROWS)` → với mỗi `HomePage` tạo một hàng `home_pages` (`rank` = index), rồi chèn các item với `container = CONTAINER_HOME`, `pageId` = id vừa tạo, `gridX`/`gridY` từ `GridPosition`, `itemType = ITEM_TYPE_APP`.
3. `apps` rỗng → chèn **một** hàng `home_pages` rỗng và không có item nào. Lưới trống nhưng có trang, để Task 3 có chỗ đặt app mới.

- [x] **Bước 2: `LayoutRepositoryImpl`**

`@Singleton`, nhận 2 DAO + `AppRepository` + `@IoDispatcher` + `@ApplicationScope`.

`pages` = `combine(pageDao.observeAll(), itemDao.observeByContainer(CONTAINER_HOME), appRepository.apps) { ... }`:
- dựng map `ComponentKey → AppItem` từ danh sách app sống,
- với mỗi trang theo `rank`, lấy các item có `pageId` khớp, ghép nhãn qua map,
- **bỏ** item không tìm được app sống (app đã gỡ hoặc tạm không khả dụng) — chỉ không vẽ, **không** xóa hàng ở đây; việc xóa là của Task 3 và Task 4,
- **bỏ** item có `container > 0` mà không có hàng thư mục cha, kèm `Log.w` (Review Focus 3),
- `.flowOn(io).stateIn(scope, WhileSubscribed(5_000), emptyList())`.

`dock` = tương tự với `observeByRank(CONTAINER_DOCK)`.

- [x] **Bước 3: `HomeViewModel` đọc từ `LayoutRepository`**

Thay chỗ gọi `BuildDefaultLayoutUseCase` và `PickDefaultDockUseCase` trực tiếp bằng `layoutRepository.pages` và `layoutRepository.dock`. Hai usecase đó giờ **chỉ** còn `LayoutReconciler` gọi — đừng xóa chúng.

Gọi `seedIfEmpty(apps)` một lần khi có danh sách app đầu tiên, trong `viewModelScope`.

`gridStatus`: `Loading` cho tới khi có lần phát **thật** đầu tiên từ `pages` (giữ nguyên cách phân biệt với giá trị khởi tạo của `stateIn` mà M3 đã làm — xem `AGENTS.md`); `Empty` khi `pages` rỗng hoặc mọi trang đều không có item; `Ready` còn lại.

- [x] **Bước 4: Log tạm để kiểm chứng**

`Log.d("M4", ...)` sau mỗi lần seed và mỗi lần `pages` phát: số hàng `launcher_items` theo container, số hàng `home_pages`, và 5 item đầu (`packageName`, `pageId`, `gridX`, `gridY`). Xóa ở Task 5.

- [x] **Bước 5: Kiểm chứng trên Live 4 — bố cục sống qua khởi động lại**

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -s M4
```
Đạt khi: lần mở đầu tiên có log seed · lưới **giống y M3** (4 trang, thứ tự chữ cái, dock 4 ô) · `force-stop` rồi mở lại: **không** seed lần hai, lưới giữ nguyên thứ tự · `adb shell run-as gambi.global.os26launcher ls -l databases` có `launcher.db`.

- [x] **Bước 6: Kiểm chứng Review Focus 4 — DB rỗng mà cờ đã bật**

```powershell
adb shell am force-stop gambi.global.os26launcher
adb shell run-as gambi.global.os26launcher rm databases/launcher.db
adb shell run-as gambi.global.os26launcher ls databases
```
Xóa DB nhưng **không** xóa file DataStore (cờ `first_run_done` vẫn `true`). Mở lại app: mong đợi seed lại bình thường và lưới hiện đúng. Nếu lưới trống thì điều kiện seed đang dựa vào cờ — sai, phải sửa.

- [x] **Bước 7: Kiểm chứng Review Focus 3 — hàng mồ côi**

Chèn tay một hàng có `container = 999` (thư mục không tồn tại). Không có sqlite3 trên máy thì tạm thêm một lệnh chèn trong `seedIfEmpty` rồi chạy một lần, xem log, và hoàn nguyên. Đạt khi: hàng đó bị bỏ qua, có `Log.w`, lưới vẫn vẽ bình thường, không crash.

- [x] **Bước 8: Commit**

```powershell
git add -A
git commit -m "feat: persist and read home layout from Room"
```

---

## Task 3: Đồng bộ khi cài và gỡ app

**Files:**
- Create: `.../domain/model/PackageEvent.kt`
- Modify: `.../data/apps/LauncherAppsDataSource.kt`, `.../data/apps/AppRepositoryImpl.kt`, `.../domain/repository/AppRepository.kt`
- Modify: `.../data/layout/LayoutReconciler.kt`, `.../data/layout/LayoutRepositoryImpl.kt`

**Interfaces:**
- Produces:

```kotlin
// domain/model/PackageEvent.kt
data class PackageEvent(val type: Type, val packageNames: List<String>, val userSerial: Long) {
    enum class Type { ADDED, REMOVED, CHANGED, AVAILABLE, UNAVAILABLE, SUSPENDED, UNSUSPENDED }
}

// thêm vào AppRepository
/** Sự kiện package có kiểu. Khác `apps`: ở đây cần biết GỠ hay chỉ TẠM KHÔNG KHẢ DỤNG. */
val packageEvents: Flow<PackageEvent>

// thêm vào LayoutReconciler
/** Đặt các app mới vào ô trống đầu tiên (Q7). Tạo trang mới nếu hết chỗ. */
suspend fun placeNewApps(apps: List<AppItem>)
/** Xóa hàng của các package đã gỡ rồi dồn ô (Q8). */
suspend fun removePackages(packageNames: List<String>, userSerial: Long)
```

`LauncherAppsDataSource.observeChanges()` đổi kiểu từ `Flow<List<String>>` sang `Flow<PackageEvent>` — mọi callback đang có đều đã biết kiểu của mình, chỉ là M2 bỏ thông tin đó đi.

**Thuật toán, hai nửa:**

- **Ô trống đầu tiên** (Q7): quét trang theo `rank`, trong trang quét `gridY` rồi `gridX`, tìm ô chưa ai chiếm; hết mọi trang thì tạo hàng `home_pages` mới với `rank` kế tiếp. App mới xếp theo thứ tự nhãn giữa chúng với nhau.
- **Dồn ô** (Q8): sau khi xóa, đọc lại toàn bộ item `container = CONTAINER_HOME`, sắp theo (rank trang, `gridY`, `gridX`), rồi gán lại tuần tự vào các ô từ trang đầu. Xóa những hàng `home_pages` cuối không còn item nào, nhưng **luôn giữ ít nhất một trang**.

- [x] **Bước 1: `PackageEvent` và `observeChanges` có kiểu**

Mỗi callback của `LauncherApps.Callback` map sang đúng `Type`. Giữ `conflate`/debounce đang có, nhưng **không** gộp hai sự kiện khác kiểu thành một — gộp `ADDED` với `REMOVED` là mất thông tin.

- [x] **Bước 2: `placeNewApps` và `removePackages`, mỗi hàm một `withTransaction`**

- [x] **Bước 3: Chống chạy chồng (Review Focus 5)**

Bọc mọi lời gọi vào `LayoutReconciler` bằng một `kotlinx.coroutines.sync.Mutex` cấp lớp. Transaction của Room **không** đủ: hai lần `placeNewApps` song song đều có thể đọc cùng một "ô trống đầu tiên" trước khi lần nào ghi xong, và kết quả là hai app chồng một ô. Mutex làm các lần reconcile xếp hàng; mỗi lần vẫn nhanh (vài chục hàng).

- [x] **Bước 4: Nối sự kiện vào reconciler**

Trong `LayoutRepositoryImpl`, thu `appRepository.packageEvents` trên `@ApplicationScope`: `ADDED` → `placeNewApps(danh sách app mới)`; `REMOVED` → `removePackages(...)`; các kiểu còn lại → **không làm gì** với DB (icon và nhãn tự cập nhật theo đường của M2).

- [x] **Bước 5: Kiểm chứng trên Live 4 — cài app mới**

Mở launcher, **để nguyên màn hình**, rồi:
```powershell
adb install <apk bất kỳ>
```
Đạt khi: icon app mới xuất hiện ở **ô trống đầu tiên** (nếu lưới đang đầy 4 trang thì ở trang mới thứ 5) · không icon nào khác bị dịch chỗ · `force-stop` rồi mở lại: vẫn ở đúng ô đó.

- [x] **Bước 6: Kiểm chứng — gỡ app giữa lưới**

Chọn một app đang ở **giữa** trang 2 (nhớ vị trí nó và hai app liền sau):
```powershell
adb uninstall <package>
```
Đạt khi: ô đó không để lỗ, hai app liền sau **dịch lên một ô** · nếu trang cuối còn đúng 1 app và app đó dịch lên trang trước thì trang cuối bị xóa · `force-stop` rồi mở lại: thứ tự giữ nguyên như vừa thấy.

- [x] **Bước 7: Kiểm chứng Review Focus 5 — cài liên tiếp**

Cài 3 APK trong vòng vài giây:
```powershell
adb install a.apk ; adb install b.apk ; adb install c.apk
```
Đạt khi: đúng 3 icon mới, **không** app nào chiếm cùng một ô, không app nào mất. Kiểm bằng log số hàng `launcher_items` trước và sau: phải tăng đúng 3.

> **Ghi nhận khi thực hiện (2026-10-27, Live 4, đo được):** không làm đúng lệnh trên. Gỡ cả 3 app cùng lúc rồi cài lại cùng lúc: `calculator` (app hệ thống) bằng `cmd package install-existing`, `chess2d` và `handar` bằng `adb install -r -t` từ APK đã sao lưu (hai APK này là testOnly nên bắt buộc `-t`). Gỡ: 3 dòng `remove:`, `rows` 87 → 84. Cài lại: `rows` về 87, ba app ở `p4(0,2)`, `p4(1,2)`, `p4(2,2)`, không trùng ô, không mất app, `force-stop` rồi mở lại vẫn giữ nguyên. Một lần `placeNew: added=2` đã đặt luôn `handar` trước khi sự kiện `ADDED` của nó tới; sự kiện đến sau không sinh thêm hàng nào.
>
> **Giới hạn (suy luận chưa kiểm chứng):** APK 67–69 MB cài mất 5–6 giây nên các sự kiện đến cách nhau xa, chưa chứng minh được hai lần reconcile chạy chồng trong cùng mili giây. `Mutex` mới được thử với chuỗi sự kiện thực tế, chưa bị thử dưới tranh chấp cực đoan. Chi tiết và bẫy khi kích thích cài/gỡ: `AGENTS.md`, mục "Đồng bộ bố cục khi cài và gỡ app".

- [x] **Bước 8: Commit**

```powershell
git add -A
git commit -m "feat: reconcile layout on package install and removal"
```

---

## Task 4: Bắt kịp khi launcher không chạy, và app tạm không khả dụng

Hai trường hợp này là lý do M4 không thể chỉ dựa vào sự kiện: sự kiện chỉ đến khi app đang chạy.

**Files:**
- Modify: `.../data/apps/LauncherAppsDataSource.kt`, `.../domain/repository/AppRepository.kt`, `.../data/apps/AppRepositoryImpl.kt`
- Modify: `.../data/layout/LayoutReconciler.kt`, `.../data/layout/LayoutRepositoryImpl.kt`

**Interfaces:**
- Produces:

```kotlin
// thêm vào AppRepository
/**
 * Package này còn được CÀI cho user đó hay không, kể cả khi đang bị tắt, tạm khóa hay trên thẻ nhớ đã tháo.
 * Dùng để phân biệt ĐÃ GỠ với TẠM KHÔNG KHẢ DỤNG. (Tên cũ trong plan là `isPackageEnabled`, đã đổi 2026-10-27, xem dưới.)
 */
fun isPackageInstalled(packageName: String, userSerial: Long): Boolean

// thêm vào LayoutReconciler
/**
 * Bắt kịp những thay đổi xảy ra lúc launcher không chạy: xóa hàng của package KHÔNG còn được cài
 * (rồi dồn ô), và đặt các app mới chưa có hàng nào. Giữ nguyên hàng của package còn cài nhưng
 * tạm không có activity mở được.
 */
suspend fun catchUp(apps: List<AppItem>)
```

`isPackageInstalled` dùng `LauncherApps.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES, user)` rồi xem cờ `ApplicationInfo.FLAG_INSTALLED`; `NameNotFoundException` hoặc cờ tắt hoặc user profile không còn → đã gỡ; mọi lỗi khác → coi là còn cài (giữ hàng). **Không** cần quyền gì thêm ngoài `<queries>` đã có từ M2 (suy luận chưa kiểm chứng: app bị tắt vẫn thấy được qua `<queries>`; Bước 5 sẽ đo).

**Sửa plan 2026-10-27 (đọc tài liệu):** bản đầu của plan dùng `LauncherApps.isPackageEnabled` và gọi nó là "đúng API cho việc này". Sai: tài liệu ghi API này trả `true` chỉ khi package "exists and is enabled", tức là trả `false` cho app bị `pm disable-user` và không phân biệt được với app đã gỡ, nên `catchUp` sẽ xóa oan đúng ca Review Focus 2 muốn bảo vệ. `getApplicationInfo` có từ API 26 (bằng `minSdk`), `FLAG_INSTALLED` từ API 17.

**Quyết định ghi rõ (Review Focus 2):** `catchUp` chỉ hỏi `isPackageInstalled` cho những hàng **không** tìm thấy app sống, không hỏi cho cả 87 hoặc 344 hàng. Vắng mặt khỏi `getActivityList` **không** đồng nghĩa với đã gỡ: app bị `disable`, bị suspend, hay nằm trên thẻ nhớ đã tháo đều vắng mặt mà vẫn còn cài.

**Quyết định về dock — đã đổi 2026-10-27:** app trong dock bị gỡ thì **để trống ô đó**, KHÔNG tự lấp. Dock có thể còn 3, 2, 1 hoặc 0 icon.

Quyết định trước của Claude là tự lấp bằng app dự phòng; người dùng khảo sát trên iPhone XS Max thấy dock iOS **cho phép để trống** nếu kéo hết app ra ngoài, nên tự lấp là tự bày ra thứ người dùng không yêu cầu. Lưu ý về bằng chứng: XS Max dừng ở **iOS 18** (iOS 26 bỏ XS, XS Max, XR), nên đây là hành vi iOS 18 — ngữ nghĩa ô dock thì nhiều năm không đổi, còn hành vi iOS 26 cho đúng ca "gỡ app đang ở dock" thì **chưa ai kiểm chứng trực tiếp**, kể cả tìm tài liệu cũng không ra.

**Hệ quả phải biết và chấp nhận:** tới hết M6 chưa có kéo-thả, nên dock mất icon là mất luôn cho tới M7. Chấp nhận được vì đây là bản nội bộ, và 4 app dock mặc định (điện thoại, tin nhắn, trình duyệt, camera) gần như không ai gỡ.

- [x] **Bước 1: `isPackageInstalled` xuyên ba lớp** (data source → impl → interface `AppRepository`)

- [x] **Bước 2: `catchUp`, trong một `withTransaction` và dưới cùng `Mutex` của Task 3**

Thứ tự bắt buộc: xóa trước, dồn ô, rồi mới đặt app mới. Làm ngược lại thì app mới có thể bị đặt vào ô mà bước dồn sẽ chiếm lại.

- [x] **Bước 3: Gọi `catchUp` một lần mỗi lần mở app**

Trong `LayoutRepositoryImpl`, sau `seedIfEmpty` và khi có danh sách app đầu tiên. Hai hàm phải chạy tuần tự, không song song.

- [x] **Bước 4: Kiểm chứng Review Focus 1 — gỡ app khi launcher không chạy**

```powershell
adb shell am force-stop gambi.global.os26launcher
adb uninstall <package đang ở giữa trang 2>
adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity
```
Đạt khi: lưới đã dồn ô ngay lần mở này · không ô rỗng · log `catchUp` ghi đúng số hàng đã xóa · `logcat -b crash` trống.

- [x] **Bước 5: Kiểm chứng Review Focus 2 — app tạm không khả dụng**

Chọn một app **không phải app hệ thống trọng yếu**, nhớ vị trí ô của nó:
```powershell
adb shell am force-stop gambi.global.os26launcher
adb shell pm disable-user --user 0 <package>
adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity
```
Đạt khi: icon đó **không** hiện, nhưng các icon khác **không** dịch chỗ (hàng vẫn còn trong DB — kiểm bằng log số hàng: không giảm).

Bật lại:
```powershell
adb shell pm enable <package>
```
Đạt khi: icon về **đúng ô cũ**. Nếu nó nhảy xuống cuối thì hàng đã bị xóa oan — sai, phải sửa.

- [x] **Bước 6: Kiểm chứng dock để trống khi app bị gỡ**

Gỡ một app đang nằm trong dock (chọn app dự phòng, đừng gỡ app hệ thống). Đạt khi: dock còn **3** icon, ô trống **không** được lấp tự động, ba icon còn lại **không** dịch chỗ · `force-stop` rồi mở lại: vẫn 3 icon, vẫn đúng chỗ · app bị gỡ không xuất hiện ở lưới.

Nhân đây kiểm luôn ca đối xứng: app trong dock bị `pm disable-user` thì hàng phải **giữ nguyên** (như Review Focus 2), bật lại thì icon về đúng ô dock cũ.

- [ ] **Bước 7: Commit**

```powershell
git add -A
git commit -m "feat: catch up layout changes made while launcher was stopped"
```

---

## Task 5: Dọn log tạm và cập nhật tài liệu

**Files:**
- Modify: `.../data/layout/*`, `.../ui/home/HomeViewModel.kt` (bỏ `Log.d("M4"`)
- Modify: `../project_structure.md`, `../Launcher-Features.md`, `../AGENTS.md`, `../room-schema.md`, `M4-layout-persistence.md` (file này)

- [x] **Bước 1: Bỏ log tạm**

Xóa mọi `Log.d("M4", ...)`. **Giữ** `Log.w`/`Log.e` ở đường xử lý lỗi (hàng mồ côi, không tìm được nhãn) — không có test thì đó là cách duy nhất phát hiện.

- [x] **Bước 2: Kiểm chứng tổng trên Live 4**

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -b crash
```
Đạt khi: mở app ra lưới đúng thứ tự · bấm icon ở lưới và dock đều mở app · `force-stop` + mở lại giữ nguyên bố cục · cài một app rồi gỡ nó, bố cục về như trước · Back không làm gì · ảnh nền phủ kín · màn demo vẫn chạy · `logcat -b crash` trống.

- [x] **Bước 3: Cập nhật tài liệu**

`project_structure.md`: tick M4 ở mục 9 kèm ngày và máy · mục 3 đánh dấu `data/layout/` đã có · mục 8 thêm Room 2.8.5 vào 8.1 và bỏ khỏi 8.2 · mục 6 ghi "đã hiện thực ở M4, xem room-schema mục 7".

`Launcher-Features.md`: TT#4 sang **Xong (M4)**, ghi rõ chưa kéo-thả (M7), chưa có UI ẩn app (M7) dù bảng đã có.

`room-schema.md`: ghi trạng thái đã hiện thực cho 3 bảng, và **số hàng thực tế** trên Live 4 sau seed (bao nhiêu item, bao nhiêu trang) — con số để sau này so khi nghi DB sai.

`AGENTS.md` — những thứ không có ở đâu khác:
- Bẫy Room + KSP + AGP 9 built-in Kotlin nếu có (vị trí `room.schemaLocation`, có phải thêm gì cho configuration cache không).
- `withTransaction` của `room-ktx` so với `runInTransaction`: cái nào dùng được trong `suspend`.
- Vì sao cần `Mutex` ngoài transaction (lý do ở Task 3 bước 3) — đây là thứ dễ bị người sau tưởng là dư.
- Lệnh đã dùng để xem DB trên máy thật, và cách nào không chạy được (có `sqlite3` trên Live 4 không).
- Kết quả trường hợp `pm disable-user`: ROM có bắn `onPackagesUnavailable` hay không, hay chỉ biến mất khỏi `getActivityList`.

- [x] **Bước 4: Commit**

```powershell
git add -A
git commit -m "docs: record M4 findings and update feature status"
```

---

## Checklist

- [x] **Task 1** Room 3 bảng, schema v1 export ra `app/schemas/1.json`, app chạy y như M3
- [x] **Task 2** Seed lần đầu + đọc bố cục từ DB, sống qua `force-stop`, DB rỗng mà cờ bật vẫn seed
- [x] **Task 3** Cài app → ô trống đầu tiên; gỡ app → dồn ô; cài 3 app liên tiếp không trùng ô
- [x] **Task 4** Gỡ app lúc launcher không chạy vẫn dồn đúng; app bị `disable` giữ nguyên chỗ và về đúng ô khi bật lại
- [x] **Task 5** Dọn log tạm, cập nhật 4 tài liệu

## M4 cố tình KHÔNG làm

| Không làm | Thuộc mốc |
|---|---|
| Kéo-thả, chế độ chỉnh sửa, ghi vị trí do người dùng chọn | M7 |
| UI ẩn app (bảng `hidden_apps` có nhưng không ai ghi) | M7 |
| Tạo/xóa/đổi thứ tự trang bằng tay (bảng `home_pages` có nhưng chỉ reconciler ghi) | M7 |
| Thư mục (`container > 0`, `ITEM_TYPE_FOLDER`) | G2 |
| Widget (`appWidgetId`), shortcut (`shortcutId`) | G3 / M7 |
| Cache nhãn app xuống đĩa | task phụ P1, sau M6 |
| `core/glass`, kính cho dock, đo 4b | M6 (A9) |
| Đổi cỡ lưới từ cài đặt, và việc re-flow khi cỡ lưới đổi | M8 |
| Xóa `demo/`, `MainActivity`, `activity_main.xml` | sau M5 |
