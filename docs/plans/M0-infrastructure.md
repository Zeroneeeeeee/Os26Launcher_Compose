# M0 — Hạ tầng DI, ViewBinding, DataStore

Trạng thái: **xong toàn bộ M0.1–M0.4** — tạo và duyệt 2026-10-16.
Mốc M0 trong [../project_structure.md](../project_structure.md) mục 9. Tài liệu kiến trúc: mục 2 (A2, A4, A5), mục 3 (cây thư mục), mục 10 (R1, R9).

## Mục tiêu

Dựng xong hạ tầng để từ M1 trở đi viết tính năng mà không phải dừng lại sửa build. **Không đổi một pixel nào của giao diện.** Demo hiện tại phải chạy y như trước sau mỗi phần.

Thứ tự 4 phần đặt theo rủi ro giảm dần: phần nặng rủi ro nhất đi trước, để nếu nó vỡ thì vỡ lúc chưa có gì phụ thuộc vào nó.

| Phần | Nội dung | Rủi ro chính |
|---|---|---|
| M0.1 | KSP + Hilt, `LauncherApp`, chứng minh graph chạy | **R1 + R9** — Hilt với AGP 9 built-in Kotlin chưa ai xác nhận |
| M0.2 | Bật ViewBinding | Không |
| M0.3 | lifecycle + DataStore + `SettingsRepository` tối thiểu | Thấp |
| M0.4 | Dọn code tạm, cập nhật docs | Không |

## Điểm bắt đầu

Commit "docs: add architecture, Room schema and AGENTS; rename to os26launcher" — build xanh, package `gambi.global.os26launcher`, 7 dependency, không có test.

## Hai chỗ sửa phạm vi so với project_structure.md

1. **Bỏ việc "tạo khung package rỗng".** Git không theo dõi thư mục rỗng và Kotlin không cần thư mục tồn tại trước. Mỗi package sẽ ra đời cùng file đầu tiên của nó. Cây thư mục ở mục 3 vẫn là đích đến, chỉ là không dựng trước.
2. **Không viết lại `MainActivity` demo theo ViewBinding.** Đó là 343 dòng code sẽ bị xóa sau M5 (điểm 4 mục 11). Bật cờ `viewBinding` ở M0.2 và để `HomeActivity` ở M1 là file đầu tiên dùng nó; demo giữ `findViewById` cho tới khi bị xóa.

---

## M0.1 — KSP + Hilt

Mục tiêu duy nhất: **build xanh và app chạy** với một graph Hilt thật. Chưa cần nó hữu ích.

### Sửa `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.3.3"
ksp = "2.2.10-2.0.2"
hilt = "2.60.1"
coreKtx = "1.19.1"
appcompat = "1.8.0"
material = "1.14.0"
activityKtx = "1.13.0"
viewpager2 = "1.1.0"
constraintlayout = "2.2.2"

[libraries]
# … các dòng hiện có giữ nguyên …
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

`ksp = "2.2.10-2.0.2"` khớp KGP 2.2.10 mà AGP 9.3.3 mang theo (đã đo), và cũng là mức sàn AGP 9 tự nâng lên nếu khai thấp hơn.

### Sửa `build.gradle.kts` (gốc)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

### Sửa `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// … phần android { } giữ nguyên …

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation("com.github.QWEA0:liquidglass:v2.0.11")

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
```

**Không** phải sửa `settings.gradle.kts`: `pluginManagement` đang lọc `google()` theo regex `com\.google.*`, nên `com.google.devtools.ksp` và `com.google.dagger.hilt.android` sẽ được hỏi ở `google()` trước, không thấy thì rơi xuống `mavenCentral()` và `gradlePluginPortal()` — cả hai đều đã khai. Cần xác nhận lại ở bước build vì đây là suy luận từ cấu hình, chưa chạy thử.

### File mới: `LauncherApp.kt`

`app/src/main/java/gambi/global/os26launcher/LauncherApp.kt`

```kotlin
package gambi.global.os26launcher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LauncherApp : Application()
```

### File mới tạm thời: `di/HiltSmokeCheck.kt`

`app/src/main/java/gambi/global/os26launcher/di/HiltSmokeCheck.kt`

```kotlin
package gambi.global.os26launcher.di

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TẠM THỜI — chỉ để chứng minh graph Hilt chạy end-to-end ở M0.1.
 * Sẽ bị xóa ở M0.3 khi đã có [SettingsRepository] làm bằng chứng thật.
 */
@Singleton
class HiltSmokeCheck @Inject constructor() {
    fun log() = Log.d("M0", "Hilt graph OK")
}
```

### Sửa `AndroidManifest.xml`

Thêm `android:name=".LauncherApp"` vào thẻ `<application>`:

```xml
<application
    android:name=".LauncherApp"
    android:allowBackup="true"
    … giữ nguyên các dòng còn lại …
```

### Sửa `MainActivity.kt` (3 dòng, tạm thời)

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var smokeCheck: HiltSmokeCheck

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smokeCheck.log()
        // … phần còn lại giữ nguyên …
```

Ba thứ này chứng minh ba tầng khác nhau, nên cần cả ba: `@HiltAndroidApp` chứng minh **KSP chạy** (sinh `Hilt_LauncherApp`), `@AndroidEntryPoint` chứng minh **Hilt Gradle plugin biến đổi bytecode được** (đây là chỗ R9 sẽ nổ nếu nổ), `@Inject` chứng minh **graph giải được phụ thuộc lúc chạy**.

### Kiểm chứng M0.1

```powershell
.\gradlew.bat clean assembleDebug
```

Nếu xanh, kiểm tra KSP có thật sự sinh code:

```powershell
Get-ChildItem -Recurse app\build\generated\ksp -Filter "Hilt_*.java" | Select-Object FullName
```

Cài lên Oppo A3x rồi xem log:

```powershell
adb logcat -c ; adb logcat -s M0
```

Đạt khi: build xanh · thấy file `Hilt_LauncherApp.java` và `Hilt_MainActivity.java` · logcat in `Hilt graph OK` · demo chạy y như trước (kính, bảng điều khiển, chọn ảnh, áp wallpaper).

### Nếu M0.1 đỏ

Xử lý theo thứ tự, **không nhảy bước**, và báo lại lỗi trước khi làm bước 2 trở đi:

| Triệu chứng | Nguyên nhân khả dĩ | Xử lý |
|---|---|---|
| `Plugin [id: 'com.google.devtools.ksp'] was not found` | `pluginManagement` không rơi được xuống mavenCentral như suy luận ở trên | Thêm `mavenCentral()` không điều kiện, hoặc nới regex của `google()` |
| Lỗi nhắc `metadata version` hoặc `compiled with a newer Kotlin` | **R9** — Dagger 2.60 build bằng Kotlin 2.3.21, dự án chạy KGP 2.2.10 | Nâng KGP qua `buildscript` classpath ở file build gốc + nâng KSP cho khớp; đo lại số bằng `gradlew.bat buildEnvironment` |
| Hilt plugin báo lỗi liên quan `builtInKotlin` / kapt | **R1** — Hilt chưa test với built-in Kotlin của AGP 9 | Hạ Hilt về 2.59.1. Vẫn vỡ thì bỏ Hilt, đổi sang `AppContainer` thủ công: thay `di/` bằng một `AppContainer.kt` giữ trong `LauncherApp`, phần còn lại của cây thư mục ở mục 3 **không đổi** |
| `OutOfMemoryError` / Gradle daemon chết khi KSP chạy | `org.gradle.jvmargs=-Xmx2048m` hơi chật khi có annotation processor | Nâng lên `-Xmx4096m` trong `gradle.properties` |
| Build xanh lần đầu, lần sau lỗi lạ | Xung đột với `configuration-cache=true` | Thử `.\gradlew.bat assembleDebug --no-configuration-cache` để khoanh vùng, rồi báo lại |

---

## M0.2 — Bật ViewBinding

### Sửa `app/build.gradle.kts`

```kotlin
android {
    namespace = "gambi.global.os26launcher"
    // … giữ nguyên …

    buildFeatures {
        viewBinding = true
    }
}
```

Không thêm dependency nào. Không sửa `MainActivity` (lý do ở mục "sửa phạm vi" phía trên).

### Kiểm chứng M0.2

```powershell
.\gradlew.bat assembleDebug
Get-ChildItem -Recurse app\build\generated\data_binding_base_class_source_out -Filter "ActivityMainBinding.java" | Select-Object FullName
```

Đạt khi: build xanh và thấy `ActivityMainBinding.java` được sinh. Không cần cài máy vì không đổi gì lúc chạy.

---

## M0.3 — lifecycle + DataStore + `SettingsRepository`

### Thêm vào `libs.versions.toml`

```toml
[versions]
lifecycle = "2.11.0"
datastore = "1.2.1"

[libraries]
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

```kotlin
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.datastore.preferences)
```

**Không** thêm `kotlinx-coroutines-android`: lifecycle 2.11.0 đã mang nó theo. Chỉ khai tường minh nếu sau này có xung đột version, và khi đó phải đo số thật trước.

### Phạm vi `LauncherSettings` ở M0: chỉ 2 khóa

Mục 6 của project_structure.md liệt kê 13 khóa cài đặt, nhưng **M0 chỉ làm 2 khóa không cần quyết định gì thêm**:

| Khóa | Vì sao có ngay ở M0 |
|---|---|
| `first_run_done` | Dùng được làm bằng chứng ghi–đọc bền qua lần khởi động lại |
| `default_launcher_prompt_shown` | M1 cần nó ngay (Quyết định 2: chỉ hỏi áp wallpaper **một lần**) |

Các khóa còn lại (`grid_columns`, `icon_style`, `glass_level_override`…) ra đời cùng tính năng của chúng, vì giá trị mặc định của chúng là quyết định thiết kế chưa chốt.

### File mới

`core/coroutines/Dispatchers.kt`

```kotlin
package gambi.global.os26launcher.core.coroutines

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
```

`di/DispatcherModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

`di/DataStoreModule.kt`

```kotlin
private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.launcherDataStore
}
```

`preferencesDataStore` phải là delegate ở **cấp file**, không đặt trong `object`, nếu không sẽ tạo nhiều instance cho cùng một file và DataStore ném lỗi lúc chạy.

`domain/model/LauncherSettings.kt`

```kotlin
data class LauncherSettings(
    val firstRunDone: Boolean = false,
    val defaultLauncherPromptShown: Boolean = false
)
```

`domain/repository/SettingsRepository.kt`

```kotlin
interface SettingsRepository {
    val settings: Flow<LauncherSettings>
    suspend fun setFirstRunDone(done: Boolean)
    suspend fun setDefaultLauncherPromptShown(shown: Boolean)
}
```

`data/settings/SettingsKeys.kt`

```kotlin
internal object SettingsKeys {
    val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    val DEFAULT_LAUNCHER_PROMPT_SHOWN = booleanPreferencesKey("default_launcher_prompt_shown")
}
```

`data/settings/SettingsRepositoryImpl.kt`

```kotlin
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<LauncherSettings> = dataStore.data
        .catch { e ->
            // File hỏng hoặc lỗi đọc: trả mặc định thay vì để Flow chết và kéo cả màn hình theo
            if (e is IOException) {
                Log.w(TAG, "Không đọc được settings, dùng mặc định", e)
                emit(emptyPreferences())
            } else throw e
        }
        .map { prefs ->
            LauncherSettings(
                firstRunDone = prefs[SettingsKeys.FIRST_RUN_DONE] ?: false,
                defaultLauncherPromptShown = prefs[SettingsKeys.DEFAULT_LAUNCHER_PROMPT_SHOWN] ?: false
            )
        }

    override suspend fun setFirstRunDone(done: Boolean) {
        dataStore.edit { it[SettingsKeys.FIRST_RUN_DONE] = done }
    }

    override suspend fun setDefaultLauncherPromptShown(shown: Boolean) {
        dataStore.edit { it[SettingsKeys.DEFAULT_LAUNCHER_PROMPT_SHOWN] = shown }
    }

    private companion object { const val TAG = "SettingsRepo" }
}
```

`di/RepositoryModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
```

Scope nằm ở `@Singleton` trên chính `SettingsRepositoryImpl`, nên `@Binds` không cần scope lần nữa.

### Sửa `MainActivity.kt` (tạm thời, thay chỗ `HiltSmokeCheck`)

```kotlin
@Inject lateinit var settingsRepository: SettingsRepository

// trong onCreate, sau super.onCreate:
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        settingsRepository.settings.collect { Log.d("M0", "settings = $it") }
    }
}
lifecycleScope.launch { settingsRepository.setFirstRunDone(true) }
```

Xóa `di/HiltSmokeCheck.kt` và dòng `smokeCheck.log()` ở bước này — nó đã hết việc. (File tạm này được khai báo trước ở M0.1 kèm thời điểm xóa, nên không cần xin duyệt lại.)

### Kiểm chứng M0.3

Chạy lần 1 trên Oppo, logcat phải in:

```
settings = LauncherSettings(firstRunDone=false, defaultLauncherPromptShown=false)
settings = LauncherSettings(firstRunDone=true,  defaultLauncherPromptShown=false)
```

Kill app rồi mở lại, lần này dòng **đầu tiên** phải là `firstRunDone=true` — đây là bằng chứng ghi xuống đĩa bền qua lần khởi động, không chỉ là state trong RAM.

Đạt khi: hai điều trên đúng · `repeatOnLifecycle` không rò (xoay màn hình: mỗi lần xoay thêm **đúng 1 dòng** log vì Activity được tạo lại; rò là khi lần xoay thứ N in ra N dòng) · demo vẫn chạy như trước.

---

## M0.4 — Dọn và cập nhật docs

1. Xóa phần code tạm trong `MainActivity`: `@Inject settingsRepository`, hai `lifecycleScope.launch`, các `import` không còn dùng. Giữ `@AndroidEntryPoint` — M1 cần nó.
2. Kiểm tra `HiltSmokeCheck.kt` đã bị xóa ở M0.3.
3. Cập nhật `project_structure.md`: chuyển Hilt, KSP, lifecycle, DataStore, ViewBinding từ mục 8.2 "Dự kiến thêm" sang 8.1 "Đã có"; tick M0 ở mục 9; ghi 2 chỗ sửa phạm vi vào mục 9.
4. Cập nhật `AGENTS.md`: kết quả thật của R1/R9 (Hilt có chạy với built-in Kotlin của AGP 9 hay không — đây là thông tin chưa có ở đâu trên mạng), heap cần bao nhiêu, và mọi lỗi đã gặp cùng cách sửa.
5. Commit, tiếng Anh theo quy ước:

```
feat: add Hilt, KSP, ViewBinding and DataStore infrastructure

- wire KSP 2.2.10-2.0.2 and Hilt 2.60.1, add LauncherApp with @HiltAndroidApp
- enable ViewBinding
- add lifecycle 2.11.0 and datastore-preferences 1.2.1
- add SettingsRepository backed by DataStore with 2 keys
- add dispatcher qualifiers and Hilt modules under di/
```

---

## Checklist

Tick khi bạn xác nhận đạt, không tick khi tôi tự thấy xong.

- [x] **M0.1** KSP + Hilt + `LauncherApp` + graph chạy thật — xác nhận 2026-10-16 (build xanh, logcat `Hilt graph OK` trên Oppo A3x). KSP phải dùng `2.3.11` thay vì `2.2.10-2.0.2` đã ghi trong plan: bản cũ lỗi `kotlin.sourceSets` với built-in Kotlin của AGP 9
- [x] **M0.2** ViewBinding bật, binding class được sinh — xác nhận 2026-10-16 (`ActivityMainBinding.java` được sinh)
- [x] **M0.3** DataStore + `SettingsRepository`, ghi–đọc bền qua restart — xác nhận 2026-10-16 (mỗi lần xoay thêm 1 dòng log là đúng, vì Activity được tạo lại)
- [x] **M0.4** dọn code tạm, cập nhật `project_structure.md` + `AGENTS.md`, commit — xác nhận 2026-10-16 (người dùng tự commit)

## M0 cố tình KHÔNG làm

Để không lẫn phạm vi khi review:

| Không làm | Thuộc mốc |
|---|---|
| `HomeActivity`, `intent-filter` HOME | M1 |
| Di trú `demo/` sang `data/backdrop/` | M1 |
| `AppRepository`, `IconCache` | M2 |
| `core/glass`, `GlassTier`, `GlassSurface` | M3 |
| Room, `app/schemas/` | M4 |
| 11 khóa cài đặt còn lại | theo tính năng của chúng |
| Bật lại R8 | mốc riêng trước khi phát hành |
| Bất kỳ thay đổi nào thấy được trên giao diện | — |
