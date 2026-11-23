# M2 — Danh sách app, icon cache, mở app

> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans` để thực thi từng task. Các bước dùng checkbox `- [ ]`.
>
> **Dự án này KHÔNG có test** (A7). Đừng viết unit test, instrumented test, đừng thêm thư viện test. Vòng TDD được thay bằng **kiểm chứng tay trên máy thật** với lệnh cụ thể và kết quả mong đợi ở mỗi task. Không tick checkbox khi chưa có người xác nhận.

**Goal:** Launcher đọc được danh sách app mở được của máy, có icon ở kích thước cần, mở được app, và tự cập nhật khi người dùng cài, gỡ hoặc cập nhật app.

> **Cập nhật 2026-10-16 sau khi Task 1 chạy:** Review Focus 1 **đã nổ**. `getActivityList` chỉ trả **9** activity trên Oppo A3x (Android 14) trong khi `adb shell cmd package query-activities` thấy đầy đủ. Task 1B (mới) phải chạy xong và chốt cách sửa **trước** Task 2, 3, 4.

**Architecture:** `LauncherApps` là nguồn duy nhất của danh sách app — **không cache danh sách vào DB** (nguyên tắc ở mục 0 của [room-schema.md](../room-schema.md)). `AppRepositoryImpl` biến callback của `LauncherApps` thành một `Flow` nóng dùng chung qua `stateIn`, `ui` chỉ thấy `AppRepository` trong `domain`. Icon vẽ sẵn thành `Bitmap` ở kích thước lưới và giữ trong `LruCache` RAM.

**Tech Stack:** Kotlin, `android.content.pm.LauncherApps` + `UserManager`, coroutines `callbackFlow`/`stateIn`, Hilt, RecyclerView 1.4.0 (thêm ở task 3), ViewBinding. Không Compose (B1).

**Spec:** [../project_structure.md](../project_structure.md) — mục 2 (A1–A7, B1–B8, luật phụ thuộc), mục 3 (`data/apps/`, `domain/model`), mục 9 (mốc M2), mục 10 (R8 — quyền xem danh sách app). Tính năng: [../Launcher-Features.md](../Launcher-Features.md) **TT#2**. Khóa dữ liệu dùng lại ở M4: [../room-schema.md](../room-schema.md) mục 3 (`packageName`, `className`, `userSerial`).

## Global Constraints

Áp cho **mọi task**, không nhắc lại ở từng task:

- `minSdk 26`, `targetSdk 37`, Java 11. API từ 27 trở lên phải có nhánh dự phòng cho 26.
- UI là View/XML, ViewBinding, **không** `findViewById`, **không** Compose (B1).
- **Luật phụ thuộc:** `ui → domain ← data`. `domain` **không** được tham chiếu kiểu nằm ở `data` (bài học M1 Task 1 trong `AGENTS.md`). `android.graphics.Bitmap` trong `domain` được phép vì không có kiểu trung lập nào thay được — ghi comment nói rõ tại chỗ; mọi kiểu hình học khác tự định nghĩa trong `domain/model`.
- **Không khai `QUERY_ALL_PACKAGES`** trong manifest (R8). `LauncherApps` là đường hợp pháp để thấy app mở được và không cần quyền đó.
- Không cache danh sách app, nhãn hay icon vào DataStore hoặc file. Icon chỉ ở RAM trong mốc này.
- Không xóa file ngoài danh sách đã duyệt. `demo/`, `MainActivity`, `activity_main.xml`, `view_extra_glass.xml` giữ đến sau M5. Màn demo phải chạy y như trước.
- Mọi chữ hiển thị vào `strings.xml`.
- Thêm thư viện phải cập nhật `gradle/libs.versions.toml` **và** mục 8 của `project_structure.md` trong cùng task.
- Kiểm chứng tay trên **Vsmart Live 4** (Android 11, SDK 30) là **máy test chính** cho mọi chức năng không liên quan đến liquid glass (chốt 2026-10-26, từ Task 3 trở đi). **Oppo A3x** (Android 14, SDK 34) chỉ dùng khi thật sự cần kính, hoặc khi Live 4 không tái hiện được ca đang xét. Task 1, 1B, 2 đã đo trên cả hai máy trước ngày này; không đo lại.
- Commit message tiếng Anh, `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt.
- Lệnh viết cho PowerShell. Nhớ `adb shell am force-stop gambi.global.os26launcher` trước mỗi lần thử lại trạng thái khởi động (bài học M1 Task 2).

## Review Focus

Năm trường hợp spec hàm ý nhưng đường đi hạnh phúc không chạm tới. Mỗi dòng gắn với một bước kiểm chứng trong task sở hữu nó:

1. ~~**App chưa là launcher mặc định** → danh sách app vẫn phải đầy đủ.~~ **ĐÃ NỔ, đo 2026-10-16 trên Oppo A3x (Android 14):** `getActivityList(null, user)` trả 9 activity (`profiles=1`, `unlocked=true`, `quiet=false`), thiếu Chrome, Dialer, Contacts, MMS, Clock và phần lớn app người dùng. 9 package thấy được — `com.android.settings`, `com.android.vending`, `com.coloros.gallery3d`, `com.oplus.camera`, `com.android.documentsui`, `com.android.stk` ×2, và chính app ×2 — khớp với tập được miễn trừ tự động, trong đó `gallery3d` và `documentsui` gần như chắc chắn đến từ Photo Picker của M1 ("app đã tương tác"). **Đã tra tài liệu 2026-10-19: launcher KHÔNG nằm trong 8 nhóm được miễn trừ tự động, kể cả khi giữ `ROLE_HOME`** — nên giả thuyết "đặt làm mặc định là đủ" gần như chắc chắn sai. Cách sửa và cách kiểm chứng ở **Task 1B**. Suy luận cũ trong tài liệu dự án ("`LauncherApps` được miễn trừ") **sai** — không dùng lại.
2. **Sắp xếp nhãn tiếng Việt** → `String.compareTo` đẩy mọi nhãn bắt đầu bằng `Đ`, `Ă`, `Ư` xuống sau `Z`, vì so sánh theo code point. Phải dùng `Collator` theo locale. (Task 1)
3. **App có icon lạ hoặc không lấy được icon** (`AdaptiveIconDrawable`, drawable không có kích thước nội tại, app vừa bị gỡ giữa lúc đang vẽ) → trả icon mặc định hoặc null, **không** crash, không bitmap 0×0. (Task 2)
4. **Gỡ app trong lúc launcher đang mở và người dùng bấm vào đúng icon đó** → `startMainActivity` phải bắt được lỗi và báo, không crash; danh sách tự bỏ dòng đó. (Task 4)
5. **Nhiều user profile** (work profile): hai app cùng `packageName`/`className` khác `userSerial` → phải là **hai** dòng và bấm vào mở đúng profile. Nếu không có máy nào tạo được work profile thì ghi rõ "chưa kiểm chứng được" cùng lệnh đã thử, không ghi là đạt. (Task 3)

---

## Task 1: Đọc danh sách app

**Files:**
- Create: `.../domain/model/AppItem.kt`
- Create: `.../domain/repository/AppRepository.kt`
- Create: `.../data/apps/LauncherAppsDataSource.kt`
- Create: `.../data/apps/AppRepositoryImpl.kt`
- Create: `.../core/coroutines/ApplicationScope.kt`
- Create: `.../di/AppModule.kt`
- Modify: `.../di/RepositoryModule.kt`
- Modify: `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeUiState.kt` (chỉ để log kiểm chứng, UI thật ở task 3)

**Interfaces:**
- Consumes: `@IoDispatcher` (M0).
- Produces:

```kotlin
// domain/model/AppItem.kt
/** Khóa của một activity mở được. Ba trường này cũng là khóa dùng trong Room ở M4. */
data class ComponentKey(
    val packageName: String,
    val className: String,
    val userSerial: Long
)

data class AppItem(
    val key: ComponentKey,
    val label: String
)

// domain/repository/AppRepository.kt — task này chỉ có `apps`, task 2 thêm icon, task 3 thêm launch
interface AppRepository {
    /** App mở được của mọi user profile, đã sắp theo nhãn theo locale hiện tại. */
    val apps: Flow<List<AppItem>>
}

// core/coroutines/ApplicationScope.kt
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

`userSerial` dùng `UserManager.getSerialNumberForUser()`: `UserHandle` không lưu được và không bền giữa các lần khởi động, serial number thì bền (lý do đầy đủ ở mục 3 của `room-schema.md`).

- [x] **Bước 1: `di/AppModule.kt` cấp một `CoroutineScope` cấp app**

`@Provides @Singleton @ApplicationScope fun provideApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)`.

`SupervisorJob` để một flow lỗi không giết các flow khác dùng chung scope này. Scope này sống bằng tuổi process, không bao giờ cancel — đó là ý muốn, vì `stateIn` ở bước 4 cần nó.

- [x] **Bước 2: `LauncherAppsDataSource`**

`@Singleton class LauncherAppsDataSource @Inject constructor(@ApplicationContext private val context: Context)`, giữ `LauncherApps` và `UserManager` lấy qua `getSystemService`.

Task này cần đúng một hàm: `fun loadApps(): List<AppItem>`. Lặp qua `userManager.userProfiles`, mỗi profile gọi `launcherApps.getActivityList(null, user)`, map sang `AppItem` với `label = info.label.toString()` và `userSerial = userManager.getSerialNumberForUser(user)`.

Bọc trong `try/catch` cho `SecurityException`: trả list rỗng và `Log.e`, không để ném lên. Hàm này chạy chậm (hàng trăm activity) nên **chỉ gọi từ luồng nền**, ghi rõ trong KDoc.

- [x] **Bước 3: Comparator sắp theo nhãn**

Trong `AppRepositoryImpl`: `java.text.Collator.getInstance(Locale.getDefault())`, `strength = Collator.PRIMARY`, dùng làm comparator cho `label`. **`Collator` không thread-safe** → tạo một instance mới trong mỗi lần sắp, không giữ làm field dùng chung.

Đây là Review Focus 2: `sortedBy { it.label }` cho ra thứ tự sai với tiếng Việt.

- [x] **Bước 4: `AppRepositoryImpl`**

`@Singleton class AppRepositoryImpl @Inject constructor(dataSource, @IoDispatcher io, @ApplicationScope scope) : AppRepository`.

`apps` = một flow phát một lần danh sách đã sắp, `.flowOn(io)`, rồi `.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())`. Task 4 sẽ thay nguồn phát bằng flow có cập nhật; cấu trúc `stateIn` giữ nguyên.

`WhileSubscribed(5_000)` để khi người dùng rời launcher 5 giây thì ngừng, quay lại trong 5 giây thì không nạp lại.

- [x] **Bước 5: `@Binds` trong `RepositoryModule`** cho `AppRepositoryImpl` → `AppRepository`.

- [x] **Bước 6: Log tạm trong `HomeViewModel` để kiểm chứng**

Thu `appRepository.apps` trong `viewModelScope`, `Log.d("M2", ...)` số lượng app và **20 nhãn đầu**. Thêm `appCount: Int = 0` vào `HomeUiState` và in vào `home_status`. Cả hai sẽ bị thay bằng UI thật ở task 3.

- [x] **Bước 7: Kiểm chứng trên Oppo A3x** *(đạt sau Task 1B: 344/344)*

```powershell
.\gradlew.bat installDebug
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -s M2
```
Mong đợi: số app khớp với số icon trong danh sách app của máy (đếm tay hoặc đối chiếu `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | findstr /c:"packageName" | find /c /v ""`). `home_status` hiện cùng số đó.

- [x] **Bước 8: Kiểm chứng Review Focus 2 — thứ tự tiếng Việt**

Trong 20 nhãn đầu của log, tìm nhãn bắt đầu bằng `Đ` (máy tiếng Việt thường có "Điện thoại", "Đồng hồ"). Mong đợi: `Đ` nằm ngay sau nhóm `D`, **không** nằm cuối danh sách sau `Z`. Nếu máy đang để tiếng Anh thì đổi sang tiếng Việt trong Cài đặt rồi `force-stop` và thử lại, vì đây là điểm dễ bỏ sót nhất.

- [x] **Bước 9: Kiểm chứng Review Focus 1** — **đã chạy 2026-10-16, KHÔNG ĐẠT.** Chỉ thấy 9 app. Agent đã làm đúng việc: dừng và báo. Toàn bộ việc chẩn đoán và sửa nằm ở **Task 1B** ngay dưới.

- [x] **Bước 10: Commit**

```powershell
git add -A
git commit -m "feat: read launchable apps from LauncherApps"
```

---

## Task 1B: Chốt cách lấy được danh sách app đầy đủ

Task chẩn đoán và sửa, không thêm tính năng.

### Đã tra tài liệu (2026-10-19) — đọc trước khi làm

Bốn phát hiện, có nguồn, làm cơ sở cho thứ tự các bước dưới:

1. **Launcher KHÔNG nằm trong danh sách được miễn trừ tự động.** Tài liệu Android liệt kê đúng 8 nhóm package luôn thấy được: app của chính mình · một số package hệ thống cốt lõi · app đã cài app mình · app gọi `startActivityForResult` vào app mình · app start/bind service của mình · app truy cập content provider của mình · app có provider mà mình được cấp URI permission · app nhận input khi mình là IME. **Không có "launcher mặc định", không có `ROLE_HOME`.** Nguồn: developer.android.com/training/package-visibility/automatic.
2. **Khai `<queries><intent>` MAIN/LAUNCHER là cách sửa đã có người làm được.** Một launcher trên API 34: trước khi sửa thấy ~6 app trong khi máy có 150+; sau khi thêm khối đó thấy **275** app, `dumpsys package` in `queriesIntents=[MAIN, LAUNCHER]`, khớp với 274 activity adb liệt kê. Một ca khác (Samsung, Android 11) cùng triệu chứng "chỉ thấy 5 app hệ thống", cùng cách sửa. Nguồn: github.com/BenjMichel/AI-launcher/pull/1, github.com/TomasGC/Anglerfish/issues/20.
3. **Chính sách Play: launcher không nằm trong danh sách được phép dùng `QUERY_ALL_PACKAGES`.** Nguyên văn use case được phép: "device search, antivirus apps, file managers, and browsers". Phải chứng minh được "a less intrusive method of app visibility" không dùng được; không đạt hoặc không nộp form thì app "may be removed from Google Play". Nguồn: support.google.com/googleplay/android-developer/answer/10158779.
4. **Chẩn đoán trực tiếp:** `adb shell dumpsys package <package>` in `queriesIntents`, cho biết hệ thống đang cho app thấy gì và vì sao — dùng nó thay cho đoán.

**Điểm vẫn chưa có nguồn khẳng định:** cả hai ca ở phát hiện 2 đều gọi `PackageManager.queryIntentActivities`, **không phải** `LauncherApps.getActivityList`. Lọc hiển thị nằm ở tầng PackageManager và `<queries>` là thuộc tính của **app gọi**, không phải của API, nên suy ra `getActivityList` cũng được hưởng — nhưng đây là suy luận, và bước 2 dưới đây là chỗ kiểm chứng nó.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (thêm `<intent>` vào khối `<queries>` đang có)
- Modify sau khi đo: `../AGENTS.md`, `../project_structure.md` (R8), và `../Launcher-Features.md` nếu rơi vào bước 5

### Số đối chứng (làm trước, mỗi máy một lần)

Shell adb có đầy đủ hiển thị package nên dùng làm **trần** để so:

```powershell
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | Measure-Object -Line
```

- [x] **Bước 1: Xem hệ thống đang cho app thấy gì**

```powershell
adb shell dumpsys package gambi.global.os26launcher | Select-String -Pattern "queriesIntents|queriesPackages|forceQueryable"
```
Mong đợi **trước khi sửa**: `queriesIntents` chỉ có 3 action của M1 (`REQUEST_ROLE`, `HOME_SETTINGS`, `SETTINGS`), không có `MAIN`/`LAUNCHER`. Đây là bằng chứng trực tiếp cho nguyên nhân, mạnh hơn mọi suy luận. Ghi lại nguyên văn dòng đó.

- [x] **Bước 2: Thêm `<intent>` MAIN/LAUNCHER vào khối `<queries>` đang có**

```xml
<!-- Android 11+ lọc package theo tầm nhìn: khai intent này để LauncherApps thấy mọi activity mở được.
     Launcher KHÔNG nằm trong 8 nhóm được miễn trừ tự động, kể cả khi đang giữ ROLE_HOME.
     Đây là khai báo hiển thị package, không phải quyền, nên không cần khai gì trên Play Console. -->
<intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent>
```

Cài lại, `adb shell am force-stop gambi.global.os26launcher`, mở app, đo trên **cả hai máy**:

```powershell
adb shell dumpsys package gambi.global.os26launcher | Select-String -Pattern "queriesIntents"
adb logcat -c ; adb logcat -s LauncherAppsDS M2
```

Đạt khi **cả ba** đúng: `queriesIntents` có `MAIN` và `LAUNCHER` · số `apps=` khớp số đối chứng (lệch 1–2 là bình thường, vì app của chính mình có 2 activity mở được và một số ROM ẩn vài activity hệ thống — ghi rõ phần lệch) · Chrome, Dialer, Contacts, MMS, Clock có trong danh sách.

Bước này cũng chính là chỗ kiểm chứng điểm "chưa có nguồn khẳng định" ở trên: nếu `queriesIntents` đã có MAIN/LAUNCHER mà `getActivityList` vẫn thiếu, thì `LauncherApps` **không** hưởng khai báo này và phải sang bước 4.

- [x] **Bước 3: Đo thêm hai trạng thái, vì chúng quyết định UX của M3**

Cùng build đã sửa, đo số app trong hai trạng thái:

| Trạng thái | Cách đặt | Số app |
|---|---|---|
| đang là launcher mặc định | `adb shell cmd package set-home-activity gambi.global.os26launcher/gambi.global.os26launcher.ui.home.HomeActivity` | ? |
| **không** phải mặc định | đặt lại launcher gốc của máy | ? |

Mong đợi: **hai số bằng nhau**, vì `<queries>` là thuộc tính của app gọi, không liên quan tới role. Nếu bằng nhau thì M3 vẽ được lưới icon đầy đủ ngay cả khi người dùng chưa đặt mặc định — không cần thêm trạng thái UI nào. Nếu khác nhau thì báo lại, vì lúc đó M3 phải có trạng thái "danh sách chưa đầy đủ".

- [x] **Bước 4: Chỉ làm nếu bước 2 KHÔNG đạt — đo trần bằng `QUERY_ALL_PACKAGES`** — **bỏ qua, không cần: bước 2 đã đạt (2026-10-19).**

Thêm `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`, đo lại, rồi **xóa ngay** dòng đó. Ô này chỉ trả lời "liệu có cách nào thấy đủ không", không phải phương án đã chọn.

- [x] **Bước 5: Chỉ làm nếu bước 4 cho thấy chỉ `QUERY_ALL_PACKAGES` mới đủ — DỪNG và báo người dùng** — **bỏ qua, không cần: bước 2 đã đạt (2026-10-19).**

**Không tự thêm quyền đó vào manifest.** Theo chính sách Play (phát hiện 3), launcher không nằm trong danh sách use case được phép, và hậu quả xấu nhất là app bị xóa khỏi Play. Đây là quyết định của cấp trên.

Việc của agent: thêm một dòng vào mục 5 "Cần xin ý kiến" của `Launcher-Features.md` với đủ dữ kiện để người khác quyết — số đo từng ô, nguyên văn chính sách, và hai hướng thay thế để cân: (a) chỉ hiện những app thấy được, chấp nhận danh sách thiếu; (b) nộp Permissions Declaration Form và chờ Google trả lời. Rồi dừng Task 1B tại đây.

- [x] **Bước 6: Nếu bước 2 đạt — cập nhật tài liệu**

- `AGENTS.md`: nguyên văn `queriesIntents` trước và sau, số app trước / sau / trần trên **từng máy**, và kết quả bước 3. Ghi rõ một dòng: **`LauncherApps.getActivityList` CÓ chịu lọc hiển thị package, và CÓ hưởng khai báo `<queries><intent>`** — đây là dữ kiện không có trong tài liệu Android lẫn hai ca tham chiếu, cả hai ca đó đều dùng `queryIntentActivities`.
- `project_structure.md` R8: sửa lại cho đúng. Câu cũ "dùng `LauncherApps` (không cần quyền)" đúng về *quyền* nhưng bỏ sót điều kiện *hiển thị package*. Câu mới phải nói: cần `<queries><intent>` MAIN/LAUNCHER, không cần `QUERY_ALL_PACKAGES`, và launcher không được miễn trừ tự động dù giữ `ROLE_HOME`.

- [x] **Bước 7: Hoàn nguyên sửa tạm và commit**

Kiểm tra manifest: `QUERY_ALL_PACKAGES` **không** được còn trong file trừ khi người dùng đã đồng ý ở bước 5.

```powershell
git add -A
git commit -m "fix: declare launcher intent query for package visibility"
```

---

## Task 2: Icon loader và cache RAM

**Files:**
- Create: `.../data/apps/IconCache.kt`, `.../data/apps/IconLoader.kt`
- Modify: `.../data/apps/LauncherAppsDataSource.kt`, `.../data/apps/AppRepositoryImpl.kt`, `.../domain/repository/AppRepository.kt`

**Interfaces:**
- Consumes: `ComponentKey`, `LauncherAppsDataSource` (task 1).
- Produces: thêm vào `AppRepository`:

```kotlin
/**
 * Icon đã vẽ sẵn thành bitmap vuông [sizePx]. null nếu không lấy được.
 * Bitmap do cache sở hữu — người gọi KHÔNG được recycle.
 * (Bitmap là android.graphics, ngoại lệ có chủ ý của luật phụ thuộc: không có kiểu trung lập thay được.)
 */
suspend fun icon(key: ComponentKey, sizePx: Int): Bitmap?
```

**Quyết định phạm vi:** mục 3 của spec nói `IconCache` là "LruCache trong RAM + cache đĩa". **M2 chỉ làm RAM.** Cache đĩa cần theo dõi `lastUpdateTime`/`versionCode` để biết khi nào icon cũ, tức thêm một nguồn dữ liệu sai lệch — và chưa ai đo được nó có cần không. Quyết định ở M3, sau khi đo thời gian hiện lưới icon lần đầu trên Vsmart Live 4. Nếu dưới một nhịp vẽ thì không làm cache đĩa.

- [x] **Bước 1: `IconCache`**

`@Singleton class IconCache @Inject constructor()`, bọc một `LruCache<String, Bitmap>`.

- Khóa: `"${key.packageName}/${key.className}#${key.userSerial}@$sizePx"` — có `sizePx` vì lưới và danh sách có thể hỏi hai kích thước khác nhau cho cùng một app.
- Dung lượng tính theo **byte**, không theo số lượng: `maxSize = (Runtime.getRuntime().maxMemory() / 8).toInt()`, `sizeOf = bitmap.allocationByteCount`.
- `fun get(...)`, `fun put(...)`, `fun clear()`. `clear()` để M3 gọi khi đổi kiểu icon (TT#12) hoặc đổi cỡ lưới.

- [x] **Bước 2: `LauncherAppsDataSource.loadIconDrawable(key: ComponentKey): Drawable?`**

Tìm `LauncherActivityInfo` khớp `key` rồi gọi `getIcon(0)` (0 = dùng density của màn hình). Trả null nếu không tìm thấy activity — app có thể đã bị gỡ ngay trước lời gọi này (Review Focus 3).

- [x] **Bước 3: `IconLoader`**

`@Singleton class IconLoader @Inject constructor(dataSource, cache)` với `suspend fun load(key: ComponentKey, sizePx: Int): Bitmap?`: tra cache → thiếu thì lấy drawable, vẽ vào `Bitmap.createBitmap(sizePx, sizePx, ARGB_8888)` qua `Canvas` với `setBounds(0, 0, sizePx, sizePx)` → `put` vào cache.

Ba chỗ dễ sai, bắt buộc xử lý:
- `sizePx <= 0` → trả null ngay, **không** gọi `createBitmap` (ném `IllegalArgumentException`).
- Drawable không có kích thước nội tại (`intrinsicWidth <= 0`) vẫn vẽ đúng vì ta tự `setBounds` — đừng dùng `intrinsicWidth` làm kích thước bitmap.
- Bọc `try/catch` cho `OutOfMemoryError` và `Exception`: trả null, `Log.w`. Một app có icon lỗi không được làm sập cả màn hình chính.

- [x] **Bước 4: `AppRepositoryImpl.icon()`** gọi `iconLoader.load` trong `withContext(io)`.

- [x] **Bước 5: Log tạm kiểm chứng**

Trong `HomeViewModel`, sau khi có danh sách app: nạp icon `sizePx = 144` cho **10 app đầu**, `Log.d("M2", ...)` mỗi icon kích thước thật và `allocationByteCount`, cộng thêm một dòng tổng: `maxMemory`, `maxSize` của cache, số icon nạp được, số icon null.

- [x] **Bước 6: Kiểm chứng trên cả hai máy — Review Focus 3** *(đạt 2026-10-19: Oppo 10/10, Live 4 10/10 icon 144x144, null=0; probe 4 ca key không tồn tại đều null, không crash)*

```powershell
adb shell am force-stop gambi.global.os26launcher
adb logcat -c ; adb logcat -s M2
```
Mong đợi: 10 dòng icon đều `144x144`, không dòng nào `0x0`, số null bằng 0 hoặc có lý do rõ ràng trong log. Không crash, `adb logcat -b crash` trống.

Thử thêm trường hợp app vừa bị gỡ: trong lúc app đang mở, `adb uninstall <một app bất kỳ>` rồi `force-stop` và mở lại — nếu `loadIconDrawable` trả null thì phải thấy `Log.w`, không thấy crash.

- [x] **Bước 7: Commit**

```powershell
git add -A
git commit -m "feat: load and cache app icons in memory"
```

---

## Task 3: Danh sách tạm trên màn hình chính và mở app

UI của task này là **tạm**, để mắt người nhìn thấy kết quả của task 1 và 2. Lưới icon thật là M3 và sẽ thay nó. Ghi comment `// TẠM: xóa ở M3, thay bằng IconGridView` ở đầu mọi file và khối XML của task này.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (thêm RecyclerView)
- Create: `.../ui/debug/AppListAdapter.kt`, `res/layout/item_debug_app.xml`
- Modify: `res/layout/activity_home.xml`, `.../ui/home/HomeActivity.kt`, `.../ui/home/HomeViewModel.kt`, `.../ui/home/HomeUiState.kt`
- Modify: `.../data/apps/LauncherAppsDataSource.kt`, `.../data/apps/AppRepositoryImpl.kt`, `.../domain/repository/AppRepository.kt`
- Create: `.../domain/model/ScreenRect.kt`

**Interfaces:**
- Produces:

```kotlin
// domain/model/ScreenRect.kt
/** Vùng trên màn hình, toạ độ pixel. Tự định nghĩa thay vì dùng android.graphics.Rect để domain không phụ thuộc framework. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

// thêm vào AppRepository
/** Mở app. false nếu app không còn tồn tại hoặc hệ thống từ chối. [source] là vùng của icon, để hệ thống vẽ animation mở app từ đúng chỗ. */
fun launch(key: ComponentKey, source: ScreenRect?): Boolean
```
- Produces trên ViewModel: `fun onAppClicked(item: AppItem, source: ScreenRect?)`, và `HomeUiState.apps: List<AppItem>` thay cho `appCount` của task 1.

- [x] **Bước 1: Thêm RecyclerView**

`libs.versions.toml`: `recyclerview = "1.4.0"` và `androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }`; `implementation(libs.androidx.recyclerview)`.

Trong `project_structure.md` mục 8.2, dòng RecyclerView ghi mốc **M3** — sửa thành M2 và chuyển dòng đó sang 8.1 cùng lý do (danh sách tạm của M2 dùng trước).

- [x] **Bước 2: `LauncherAppsDataSource.startMainActivity(key, source: Rect?): Boolean`**

Dùng `launcherApps.startMainActivity(componentName, user, sourceBounds, null)` — **không** dùng `context.startActivity`. Đây là API dành cho launcher: nó xử lý đúng user profile và cho hệ thống dựng animation từ `sourceBounds`.

Bắt `ActivityNotFoundException` và `SecurityException` → trả false, `Log.w`. Đổi `ScreenRect` của `domain` thành `android.graphics.Rect` **ở đây**, trong tầng `data`.

- [x] **Bước 3: `item_debug_app.xml` và `AppListAdapter`**

Mỗi dòng: `ImageView` 48dp + `TextView` nhãn, chiều cao dòng tối thiểu 48dp (chuẩn vùng chạm ở mục 7 của spec). `ListAdapter` + `DiffUtil` so theo `ComponentKey`.

Nạp icon trong `ViewHolder`: `suspend` nên cần một scope gắn với vòng đời item. Dùng `viewHolder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope` hoặc truyền một `CoroutineScope` vào adapter từ Activity và **cancel job cũ** trong `onViewRecycled` — nếu không, cuộn nhanh sẽ gán icon của dòng cũ vào dòng mới.

- [x] **Bước 4: Gắn vào `activity_home.xml` và `HomeActivity`**

`RecyclerView` id `debug_app_list`, nửa dưới màn hình, nền đen 50% để đọc được trên ảnh nền, `visibility="gone"` mặc định. Một `MaterialButton` id `btn_toggle_app_list` bật/tắt — để còn nhìn được ảnh nền và nút đặt mặc định của M1.

`HomeViewModel.onAppClicked` gọi `appRepository.launch`, false thì phát một `HomeEvent` mới (`AppLaunchFailed`) để Activity hiện Toast.

- [x] **Bước 5: Kiểm chứng trên Vsmart Live 4 (máy test chính)** *(đạt 2026-10-26: icon và nhãn đúng, cuộn nhanh không nhảy dòng, bấm mở đúng app, Back về launcher)*

Bật danh sách: mong đợi mỗi dòng có icon đúng và nhãn đúng, cuộn mượt, cuộn nhanh **không** thấy icon nhảy sai dòng. Bấm một dòng → app tương ứng mở. Bấm Back → về launcher.

- [x] **Bước 6: Kiểm chứng trên Oppo A3x — TÙY CHỌN** (đổi 2026-10-26). Không bắt buộc vì M2 chưa có kính. Chỉ làm nếu muốn thử danh sách dài: Oppo có 344 app so với 87 ở Live 4, nên cuộn nhanh dễ lộ lỗi icon nhảy sai dòng hơn. Với Live 4, chú ý thêm `adb logcat -s M2` xem có dòng nào báo thiếu bộ nhớ khi nạp icon. **Bỏ qua** (2026-10-26, người dùng không cần).

- [x] **Bước 7: Kiểm chứng Review Focus 5 — work profile** *(đạt 2026-10-26 trên Live 4: `adb shell pm create-user --profileOf 0 --managed Work` → `Success: created user id 10`; danh sách ra hai dòng cho app có ở cả hai profile. Người dùng quan sát: ngay sau khi tạo/start user, hệ thống tự nhân đôi mọi app cho phép nhân đôi sang profile, nên không cần `install-existing` thủ công. Chưa tự đo bằng `dumpsys` việc bấm mỗi dòng mở đúng profile.)*

Thử tạo work profile:
```powershell
adb shell pm create-user --profileOf 0 --managed Work
```
Lệnh này **thường thất bại** trên ROM thương mại. Nếu thất bại: ghi vào báo cáo là **chưa kiểm chứng được**, kèm nguyên văn lỗi, và ghi vào `AGENTS.md` ở task 5. **Không** ghi là đạt.

Nếu tạo được: cài một app vào profile đó, mong đợi danh sách có **hai** dòng cùng nhãn khác `userSerial`, bấm mỗi dòng mở đúng profile.

- [x] **Bước 8: Commit**

```powershell
git add -A
git commit -m "feat: show temporary app list and launch apps"
```

---

## Task 4: Tự cập nhật khi cài, gỡ, cập nhật app

**Files:**
- Modify: `.../data/apps/LauncherAppsDataSource.kt`, `.../data/apps/AppRepositoryImpl.kt`, `.../data/apps/IconCache.kt`

**Interfaces:**
- Produces: `LauncherAppsDataSource.fun observeChanges(): Flow<Unit>` — phát một lần mỗi khi danh sách app có thể đã đổi. `AppRepository.apps` giữ nguyên chữ ký, chỉ đổi nguồn phát.

- [x] **Bước 1: `observeChanges()` bằng `callbackFlow`**

Bọc `LauncherApps.Callback`, `registerCallback(callback, handler)` với `Handler(Looper.getMainLooper())`, `awaitClose { launcherApps.unregisterCallback(callback) }`.

Phải phủ **cả 6** callback, mỗi cái `trySend(Unit)`: `onPackageAdded`, `onPackageRemoved`, `onPackageChanged`, `onPackagesAvailable`, `onPackagesUnavailable`, `onPackagesSuspended` / `onPackagesUnsuspended`. Thiếu `onPackageChanged` là bỏ mất trường hợp **cập nhật** app (icon và nhãn đổi mà danh sách không đổi) — đó là trường hợp hay gặp nhất trong ba cái.

Thêm `.conflate()` hoặc debounce ~200 ms: cài một app bắn nhiều callback liền nhau, không cần nạp lại danh sách nhiều lần.

- [x] **Bước 2: Nối vào `apps`**

`observeChanges().onStart { emit(Unit) }.map { dataSource.loadApps().sortedWith(collator) }.flowOn(io).stateIn(...)` — giữ nguyên `stateIn` và `WhileSubscribed(5_000)` của task 1.

- [x] **Bước 3: Dọn icon cũ khi app cập nhật**

`onPackageChanged` và `onPackageRemoved` phải bỏ icon của package đó khỏi `IconCache`, nếu không app vừa cập nhật vẫn hiện icon cũ cho tới khi process chết.

Thêm `fun evict(packageName: String)` vào `IconCache` (quét khóa theo tiền tố `"$packageName/"`) và gọi từ `LauncherAppsDataSource` hoặc `AppRepositoryImpl` — chọn một chỗ, ghi comment nói tại sao ở đó.

- [x] **Bước 4: Kiểm chứng — cài và gỡ, trên Vsmart Live 4** *(đạt 2026-10-26 bằng `pm uninstall -k --user 0` + `cmd package install-existing` trên Máy tính: danh sách 87 → 86 → 87 tự cập nhật, không cần mở lại app)*

Mở launcher, bật danh sách tạm, **để nguyên màn hình đó**, rồi từ máy tính:
```powershell
adb install <đường dẫn apk bất kỳ>
adb uninstall <package vừa cài>
```
Mong đợi: danh sách tự thêm rồi tự bớt dòng đó, **không** cần mở lại app, không cần `force-stop`.

- [x] **Bước 5: Kiểm chứng — cập nhật app** *(đạt một phần 2026-10-26: giả lập bằng `pm disable-user` / `pm enable` → `onPackageChanged`, danh sách 86 ↔ 87. CHƯA thử `adb install -r` bằng APK thật, và chưa quan sát trực tiếp `IconCache.evict` có xóa icon cũ.)*

Cài lại cùng APK đè lên bản đang có (`adb install -r`) và xem log: mong đợi có phát `observeChanges`, icon được nạp lại (`IconCache.evict` có tác dụng), nhãn cập nhật nếu APK đổi nhãn.

- [x] **Bước 6: Kiểm chứng Review Focus 4 — bấm vào app vừa bị gỡ** *(đạt 2026-10-26: probe `launch()` với user không tồn tại và package không tồn tại đều trả false; bấm vào app vừa gỡ ra Toast, không crash)*

Đây là cuộc đua, cần làm tay cho nhanh: mở danh sách, `adb uninstall <package>` rồi **bấm ngay** vào dòng đó trước khi danh sách kịp cập nhật. Mong đợi: Toast báo không mở được, **không** crash. Nếu không bắt được nhịp, tạm sửa `launch()` để nhận một `ComponentKey` không tồn tại và xác nhận đường xử lý lỗi, rồi hoàn nguyên.

- [x] **Bước 7: Commit**

```powershell
git add -A
git commit -m "feat: refresh app list on package changes"
```

---

## Task 5: Dọn log tạm và cập nhật tài liệu

**Files:**
- Modify: `.../ui/home/HomeViewModel.kt` (bỏ log tạm của task 1 và 2)
- Modify: `../project_structure.md`, `../Launcher-Features.md`, `../AGENTS.md`, `M2-app-list.md` (file này)

- [x] **Bước 1: Bỏ log tạm**

Xóa các `Log.d("M2", ...)` của task 1 bước 6 và task 2 bước 5. **Giữ** `Log.w`/`Log.e` ở đường xử lý lỗi — chúng là cách duy nhất phát hiện icon lỗi hoặc app biến mất khi dự án không có test.

`debug_app_list` và `AppListAdapter` **giữ lại** đến M3.

- [x] **Bước 2: `project_structure.md`**

- Tick M2 ở mục 9 kèm ngày và máy đã thử.
- Mục 8: chuyển RecyclerView 1.4.0 từ 8.2 sang 8.1, mốc M2.
- Mục 3: đánh dấu `data/apps/` đã có; ghi `IconCache` hiện chỉ có RAM, cache đĩa hoãn tới quyết định ở M3.

- [x] **Bước 3: `Launcher-Features.md`** — TT#2 sang **Xong (M2)**, ghi rõ đã kiểm chứng khi app chưa là launcher mặc định (Task 1, 1B, 2 đã đo cả hai máy; Task 3, 4 đo trên Vsmart Live 4), và trạng thái của work profile (đạt hoặc chưa kiểm chứng được).

- [x] **Bước 4: `AGENTS.md`** — ghi những thứ không có ở đâu khác:

- Kết quả Review Focus 1: `LauncherApps` có thấy đủ app khi chưa là launcher mặc định hay không, trên từng máy. Đây là câu trả lời cho R8 và M4 trở đi sẽ dựa vào nó.
- Số app thực tế và thời gian từ lúc mở app đến lúc danh sách hiện, trên từng máy. M3 cần số này để quyết cache đĩa.
- `maxMemory` và `maxSize` của `IconCache` trên từng máy.
- Callback nào của `LauncherApps.Callback` thực sự bắn khi cài / gỡ / `install -r`, theo quan sát log.
- Lệnh tạo work profile có chạy được hay không, nguyên văn lỗi nếu không.
- Bẫy nào đã gặp với icon nhảy sai dòng khi cuộn RecyclerView.

- [x] **Bước 5: Commit**

```powershell
git add -A
git commit -m "docs: record M2 findings and update feature status"
```

---

## Checklist

- [x] **Task 1** Đọc danh sách app, sắp đúng tiếng Việt — **phần "đủ app" chuyển sang Task 1B**
- [x] **Task 1B** Khai `<queries><intent>` MAIN/LAUNCHER, số app khớp trần trên cả hai máy, không dùng `QUERY_ALL_PACKAGES`
- [x] **Task 2** Icon loader + LruCache RAM, icon lỗi không làm sập
- [x] **Task 3** Danh sách tạm hiện icon và nhãn, bấm mở được app
- [x] **Task 4** Cài / gỡ / cập nhật app thì danh sách và icon tự cập nhật
- [x] **Task 5** Dọn log tạm, cập nhật 4 tài liệu — xác nhận 2026-10-26 (kiểm lại bằng grep: không còn `Log.d("M2"`, manifest không khai `QUERY_ALL_PACKAGES`, 4 tài liệu đã cập nhật, commit 3012cc8)

## M2 cố tình KHÔNG làm

| Không làm | Thuộc mốc |
|---|---|
| Lưới icon, dock, ViewPager2, `GridMetrics` | M3 |
| Cache icon xuống đĩa | quyết định ở M3 sau khi đo |
| Kiểu icon tô màu / 3D (TT#12) | G2 |
| Ẩn app khỏi danh sách (TT#8), bảng `hidden_apps` | M7 |
| Lưu bố cục, Room | M4 |
| Nhấn giữ icon, shortcut, gỡ app từ launcher (TT#7) | M7 |
| Tìm kiếm app (TT#9) | M8 |
| App Library (TT#11) | G2 |
| Xóa `demo/`, `MainActivity` | sau M5 |
| Bất kỳ `LiquidGlassView` nào trên `HomeActivity` | M3 |
