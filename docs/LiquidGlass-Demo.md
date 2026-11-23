# Demo LiquidGlass trên MainActivity (dự án XML/View)

Mục tiêu: kiểm tra nhanh thư viện `LiquidGlass-Android` có chạy ổn trong Os26Launcher hay không, trước khi dựng launcher thật.
Tài liệu tham chiếu: [LiquidGlass-Android.md](LiquidGlass-Android.md).

Gốc dự án: `E:\SourceTree\IosLauncher`. Dự án dùng `AppCompatActivity` + XML + theme `Theme.Material3.DayNight.NoActionBar`, không còn Compose.

## Trạng thái hiện tại của dự án

| Hạng mục | Trạng thái |
|---|---|
| JitPack trong `settings.gradle.kts` | Đã có |
| Dependency `com.github.QWEA0:liquidglass:v2.0.11` | Đã có |
| `appcompat`, `material`, `viewpager2`, `constraintlayout` | Đã có (đủ cho các widget cần chúng, xem tài liệu chính) |
| `demo/DemoBackdropView.kt` | Đã hoàn thiện (bước 1), thêm `lightMode` (bước 6) |
| `res/layout/activity_main.xml` | `FrameLayout`: nền demo, ảnh nền, kính, dòng trạng thái, bảng điều khiển; thêm `view_extra_glass.xml` cho kính phụ |
| `MainActivity.kt` | Đã gắn kính, bảng điều khiển, ảnh nền (chọn ảnh, lưu bản sao, áp dụng wallpaper), thử nhiều kính, công tắc ẩn kính |
| `demo/BackdropSource.kt`, `PickedImageSource.kt`, `SystemWallpaperSource.kt`, `WallpaperApplier.kt` | Đã có (nguồn ảnh nền và áp dụng wallpaper, xem "Quyết định đã chốt") |

Bước 0–7 đã xong và đã chạy trên hai máy (Oppo A3x Android 14, Vsmart Live 4 Android 11). **Demo đã đóng (2026-10-16)** sau khi đo lại tiêu chí 4, xem "Đo lại tiêu chí 4" ở mục Kết quả chạy thử. Các việc chưa làm (nền mờ tĩnh, đo nền tĩnh 4b, mốc 5 đến 8) chuyển sang giai đoạn launcher thật, xem "Mốc còn lại" gần cuối tài liệu.

## Demo này làm gì

- Nền là lưới "icon" màu sắc trôi ngang liên tục (tự vẽ bằng code, không cần ảnh). Có nhiều chi tiết nên nhìn thấy rõ khúc xạ.
- Giữa màn hình là viên kính `LiquidGlassView` (280×96dp, hình viên thuốc) có chữ bên trong.
- Một dòng trạng thái ở trên cùng cho biết SDK của máy và tầng pipeline đang chạy.
- Chạm vào kính để đổi vật liệu `REGULAR` ↔ `CLEAR`.

---

## Bước 0 — Gradle (đã xong, đây là phần để tham chiếu)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`app/build.gradle.kts` (phần dependencies liên quan):

```kotlin
implementation(libs.androidx.viewpager2)
implementation("com.github.QWEA0:liquidglass:v2.0.11")
```

**Giải thích**

- `FAIL_ON_PROJECT_REPOS` cấm khai báo kho Maven trong `build.gradle.kts` của từng module. JitPack vì vậy chỉ được đặt ở `settings.gradle.kts`, nếu không Gradle báo lỗi khi sync.
- Tọa độ `com.github.QWEA0:liquidglass` là group và artifact do JitPack phục vụ. Dạng có `.Liquid-Glass-Android` ở giữa group không tồn tại.
- `v2.0.11` được ghim cố định (không dùng `latest`) để tránh API đổi ngầm giữa các lần build.
- `viewpager2` là bắt buộc nếu sau này dùng `LiquidGlassTabLayoutMediator`, vì thư viện khai báo nó là `compileOnly`. Tương tự `appcompat` và `material` cần cho `LiquidGlassDialogBuilder`. Demo này chưa dùng tới nhưng đã sẵn.
- Dự án không có plugin `kotlin-android` vì AGP 9 đã tích hợp Kotlin sẵn.

---

## Bước 1 — Hoàn thiện `DemoBackdropView.kt`

Đường dẫn: `app/src/main/java/gambi/global/os26launcher/demo/DemoBackdropView.kt`

```kotlin
package os26launcher.demo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class DemoBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tileSize = 64f * density
    private val pitch = 88f * density
    private val radius = 16f * density

    // Mẫu màu/chữ lặp lại sau 4 cột -> vòng lặp trôi liền mạch
    private val period = 4

    private val bgPaint = Paint()
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 26f * density
    }
    private val rect = RectF()
    private val hsv = floatArrayOf(0f, 0.65f, 0.95f)
    private val letter = CharArray(1)
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF141E30.toInt(), 0xFF4B2A7B.toInt(), 0xFFE0566B.toInt()),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val shift = progress * pitch * period
        val inset = (pitch - tileSize) / 2f
        val cols = (width / pitch).toInt() + period + 2
        val rows = (height / pitch).toInt() + 2
        val fm = textPaint.fontMetrics
        val textOffset = -(fm.ascent + fm.descent) / 2f

        for (row in 0 until rows) {
            val top = row * pitch + inset
            for (i in 0 until cols) {
                val left = i * pitch - shift + inset
                val col = i % period

                hsv[0] = ((col * 3 + row * 5) % 12) * 30f
                tilePaint.color = Color.HSVToColor(hsv)
                rect.set(left, top, left + tileSize, top + tileSize)
                canvas.drawRoundRect(rect, radius, radius, tilePaint)

                letter[0] = 'A' + (col + row * period) % 26
                canvas.drawText(letter, 0, 1, rect.centerX(), rect.centerY() + textOffset, textPaint)
            }
        }
    }
}
```

**Giải thích code**

Mục đích của view này: tạo một nền **nhiều chi tiết và luôn chuyển động**, để nhìn thấy rõ kính khúc xạ và cập nhật theo nền. Nền phẳng hoặc gradient mượt sẽ không thấy khúc xạ.

*Khai báo*

- `@JvmOverloads constructor(context, attrs)`: bắt buộc có constructor nhận `AttributeSet` thì mới khai báo được view này trong XML (LayoutInflater gọi constructor đó).
- `density`: hệ số quy đổi dp → px. Mọi kích thước viết theo dp rồi nhân `density` để hiển thị đồng đều trên mọi mật độ màn hình.
- `tileSize = 64dp`: cạnh mỗi ô màu. `pitch = 88dp`: khoảng cách từ ô này tới ô kế tiếp (bao gồm khe hở 24dp). `radius = 16dp`: bo góc ô.
- `period = 4`: màu và chữ lặp lại sau mỗi 4 cột. Đây là mấu chốt để vòng lặp animation liền mạch (xem phần `onDraw`).
- Các đối tượng `Paint`, `RectF`, `FloatArray`, `CharArray` được tạo **một lần ở đây** và tái sử dụng. `onDraw` chạy mỗi frame, nếu cấp phát đối tượng trong đó sẽ gây giật do GC.
- `hsv = [0, 0.65, 0.95]`: màu tính theo HSV, chỉ đổi thành phần hue (độ màu), giữ nguyên độ bão hòa 65% và độ sáng 95%.
- `progress`: tiến độ animation từ 0 đến 1.

*`animator`*

- `ValueAnimator.ofFloat(0f, 1f)` chạy 12 giây một vòng, lặp vô hạn, `LinearInterpolator` để tốc độ trôi đều.
- Mỗi lần giá trị đổi: lưu vào `progress` rồi `invalidate()` để yêu cầu vẽ lại. Đây chính là lý do kính **phải** bật `enableDynamicBackground`: nền thay đổi từng frame.

*`onSizeChanged`*

- Chạy khi view biết kích thước. Tạo `LinearGradient` chéo từ góc trên trái đến góc dưới phải, 3 màu: xanh đêm → tím → hồng. Gradient phụ thuộc kích thước nên phải tạo ở đây, không tạo được trong constructor.
- `0xFF141E30.toInt()`: literal hex lớn hơn `Int` trong Kotlin nên là `Long`, cần `.toInt()`.

*`onAttachedToWindow` / `onDetachedFromWindow`*

- Chỉ chạy animation khi view đang hiển thị trên window, và hủy khi gỡ ra. Tránh rò rỉ animator và tốn pin khi activity đã đóng.

*`onDraw`*

- Bước 1: tô nền gradient toàn bộ view.
- `shift = progress * pitch * period`: độ dịch sang trái. Trong một vòng animation, nội dung trôi đúng `period` (= 4) ô. Vì màu và chữ của ô thứ `i` chỉ phụ thuộc `i % 4`, nên khung hình ở `progress = 1` giống hệt khung hình ở `progress = 0`, tức là loop không bị giật khi quay về đầu.
- `inset = (pitch - tileSize) / 2`: căn ô vào giữa mỗi "ô lưới" `pitch`.
- `cols = width / pitch + period + 2`: số cột cần vẽ. Cộng thêm `period` vì nội dung dịch tối đa 4 ô, và cộng 2 để dư biên, tránh thấy khoảng trống ở mép phải.
- `rows = height / pitch + 2`: số hàng, cộng 2 để dư biên dưới.
- `textOffset = -(ascent + descent) / 2`: `drawText` vẽ theo đường baseline, không theo tâm chữ. Phép tính này bù lại để chữ nằm đúng giữa ô theo chiều dọc.
- Trong vòng lặp:
  - `left = i * pitch - shift + inset`: vị trí ngang, trừ `shift` nên ô trôi sang trái.
  - `col = i % period`: chỉ số cột trong chu kỳ, quyết định màu và chữ.
  - `hsv[0] = ((col*3 + row*5) % 12) * 30f`: hue nằm trong 12 mức cách nhau 30° (cả vòng màu 360°). Hàng khác nhau lệch màu bằng `row*5`, tạo hoa văn đa sắc.
  - `Color.HSVToColor(hsv)` đổi sang màu ARGB, đặt vào `tilePaint`.
  - `drawRoundRect` vẽ ô bo góc, `drawText(letter, 0, 1, ...)` vẽ một chữ cái (A–Z) ở giữa ô. Dùng `CharArray(1)` thay vì `String` để không cấp phát mỗi lần.

*Sửa so với file hiện có trong dự án*: `onDraw` cũ chỉ gọi `super.onDraw`, nên chưa có gì được vẽ. Biến `titleSize` đổi tên thành `tileSize` (tile = ô). Nếu giữ tên cũ thì phải đổi đồng bộ ở mọi chỗ dùng.

---

## Bước 2 — `res/layout/activity_main.xml`

Thay toàn bộ nội dung (root đổi từ `ConstraintLayout` sang `FrameLayout`, vẫn giữ id `main`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <!-- 1. Nền: phải đứng TRƯỚC kính -->
    <os26launcher.demo.DemoBackdropView
        android:id="@+id/backdrop"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 2. Kính -->
    <com.example.liquidglass.LiquidGlassView
        android:id="@+id/glass"
        android:layout_width="280dp"
        android:layout_height="96dp"
        android:layout_gravity="center"
        app:cornerRadius="999dp"
        app:glassMaterial="regular"
        app:refractionHeight="12dp"
        app:bevelWidth="24dp"
        app:dispersionStrength="0.12"
        app:sensorHighlight="true"
        app:adaptiveTint="true">

        <TextView
            android:id="@+id/glassLabel"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="Regular"
            android:textColor="#FFFFFF"
            android:textSize="20sp" />
    </com.example.liquidglass.LiquidGlassView>

    <!-- 3. Dòng trạng thái, nằm trên cùng và ngoài vùng kính -->
    <TextView
        android:id="@+id/status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|center_horizontal"
        android:layout_marginTop="72dp"
        android:background="#66000000"
        android:padding="8dp"
        android:textColor="#FFFFFF"
        android:textSize="13sp" />
</FrameLayout>
```

**Giải thích code**

*Vì sao root là `FrameLayout` thay vì `ConstraintLayout`*

- Kính **chụp lại những gì được vẽ trước nó trong view cha trực tiếp**. `FrameLayout` xếp các con chồng lên nhau theo thứ tự khai báo, đơn giản và đúng với ví dụ chính thức của thư viện. Dùng `ConstraintLayout` vẫn được, nhưng với demo thì không có lý do để thêm ràng buộc.
- Giữ `android:id="@+id/main"` để các đoạn code cũ tham chiếu `R.id.main` vẫn chạy.

*Thứ tự các phần tử (quan trọng)*

- `DemoBackdropView` khai báo **trước** → được vẽ trước, nằm phía sau. Kính khai báo sau → vẽ chồng lên và chụp nền ở bên dưới. Đổi ngược thứ tự thì kính chụp được "không gì cả".
- `status` khai báo sau cùng để luôn nằm trên kính và không bị chụp vào hiệu ứng.

*Các thuộc tính của kính*

- `layout_gravity="center"`: đặt kính giữa màn hình. Kích thước 280×96dp.
- `cornerRadius="999dp"`: bo góc lớn hơn nửa chiều cao nên thành hình viên thuốc. Hình SDF dùng để tính khúc xạ cũng bám theo giá trị này.
- `glassMaterial="regular"`: vật liệu ưu tiên dễ đọc. Giá trị còn lại là `clear` (trong hơn, dùng trên media).
- `refractionHeight="12dp"`, `bevelWidth="24dp"`: lấy theo ví dụ chính thức. `bevelWidth` là độ rộng "độ dày" của mép kính, `refractionHeight` là độ lệch lấy mẫu tại mép. Mặc định của thư viện lớn hơn nhiều (160px / 48px) và được tự giới hạn theo kích thước view nhờ `adaptiveLensScale`.
- `dispersionStrength="0.12"`: viền quang phổ ở mép. Trên 0.25 sẽ giống cầu vồng hơn là kính.
- `sensorHighlight="true"`: điểm sáng ở mép di chuyển theo cảm biến trọng lực khi nghiêng máy (chỉ có ở API 33+).
- `adaptiveTint="true"`: kính tự chỉnh màu theo độ sáng nền phía sau (API 33+).
- Namespace `app:` đến từ `xmlns:app="http://schemas.android.com/apk/res-auto"` ở đầu file. Thiếu dòng này, các thuộc tính của kính không được nhận.

*Phần tử con*

- `LiquidGlassView` là một `FrameLayout`, nên `TextView` đặt bên trong được vẽ **trên** hiệu ứng kính. `glassLabel` có id để đổi chữ và đổi màu từ code.

---

## Bước 3 — `MainActivity.kt`

```kotlin
package os26launcher

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val glass = findViewById<LiquidGlassView>(R.id.glass)
        val label = findViewById<TextView>(R.id.glassLabel)
        val status = findViewById<TextView>(R.id.status)

        // Đẩy dòng trạng thái xuống dưới thanh trạng thái hệ thống.
        // Không đặt padding lên root, để nền vẫn phủ kín dưới các thanh hệ thống.
        val gap = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            status.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = bars.top + gap
            }
            insets
        }

        // BẮT BUỘC: nền đang trôi nên phải bật, nếu không kính sẽ đứng hình
        glass.enableDynamicBackground = true

        // Tầng pipeline đang dùng: >= 33 là lens pipeline (khúc xạ SDF đầy đủ)
        status.text = "SDK ${Build.VERSION.SDK_INT} · pipeline tier ${glass.effectiveApiLevel}"

        // Đổi màu chữ khi nền phía sau kính chuyển sáng/tối
        glass.glassAppearanceListener = { isOverLight ->
            label.setTextColor(if (isOverLight) Color.BLACK else Color.WHITE)
        }

        // Chạm để đổi vật liệu REGULAR <-> CLEAR
        var clear = false
        glass.setOnClickListener {
            clear = !clear
            glass.material = if (clear) GlassMaterial.CLEAR else GlassMaterial.REGULAR
            label.text = if (clear) "Clear" else "Regular"
        }
    }
}
```

**Giải thích code**

*Khung Activity*

- `AppCompatActivity` + `setContentView(R.layout.activity_main)`: hoạt động View/XML thông thường. Theme `Theme.Material3.DayNight.NoActionBar` của dự án đã tương thích.
- `enableEdgeToEdge()`: cho nội dung vẽ tràn dưới thanh trạng thái và thanh điều hướng, giống launcher thật.

*Xử lý system bars*

- Code mẫu mặc định của template đặt `setPadding` lên root. Ở đây **không làm vậy**: padding lên root sẽ làm nền bị co lại, hở một dải dưới thanh trạng thái. Thay vào đó chỉ đẩy `status` xuống bằng `topMargin = bars.top + 16dp`.
- `insets.getInsets(WindowInsetsCompat.Type.systemBars())` cho biết chiều cao thanh trạng thái (`top`) và thanh điều hướng (`bottom`).
- `return insets` giữ nguyên insets, không "ăn" hết, để các view khác (nếu có) vẫn nhận được.
- `updateLayoutParams<ViewGroup.MarginLayoutParams>` là hàm của `core-ktx`, sửa margin rồi tự yêu cầu layout lại.

*Phần kính*

- `findViewById<LiquidGlassView>(R.id.glass)`: tham chiếu tới view kính khai báo trong XML. `LiquidGlassView` import từ `com.example.liquidglass` (package của thư viện, không phải package của dự án).
- `enableDynamicBackground = true`: mặc định `false` nghĩa là nền chỉ được chụp **một lần**. Nền demo luôn trôi nên phải bật. Đây là lỗi tích hợp phổ biến nhất của thư viện.
- `effectiveApiLevel`: thuộc tính chỉ đọc, cho biết tầng pipeline thực sự dùng (nhỏ nhất giữa SDK máy và mức giới hạn debug). Hiển thị ra màn hình để biết ngay máy có chạy lens pipeline hay không.
- `glassAppearanceListener`: callback gọi mỗi khi nền sau kính đổi từ sáng sang tối hoặc ngược lại (`isOverLight`). Dùng để đổi màu chữ cho dễ đọc. Với `LiquidGlassButton` thì thư viện tự làm việc này khi bật `adaptiveTint`, nhưng `TextView` thường thì phải tự xử lý.
- `setOnClickListener`: `LiquidGlassView` là `FrameLayout` nên nhận click bình thường. Vì `TextView` bên trong không tự clickable, cú chạm đi tới kính. Mỗi lần chạm đổi `material` giữa `GlassMaterial.REGULAR` và `GlassMaterial.CLEAR` (đúng chính tả enum, sai là không build được) và đổi chữ.
- Biến `clear` là biến cục bộ được lambda "bắt" lại (closure) và sửa trực tiếp, Kotlin cho phép với `var`.

---

## Bước 4 — Chạy và kiểm tra

**Thiết bị:** máy thật Android 13+ (API 33+) để thấy đúng hiệu ứng. Máy thấp hơn vẫn chạy nhưng chỉ có blur kèm lệch màu. Nếu dùng emulator, ưu tiên ảnh arm64 (AAR chỉ có thư viện native cho arm, chưa kiểm chứng việc emulator x86 có lỗi hay không).

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 1 | Sync và build | Không lỗi resolve artifact, không lỗi enum |
| 2 | App mở được, không crash | Thấy lưới ô màu trôi ngang và viên kính ở giữa |
| 3 | Dòng trạng thái | `pipeline tier` từ 33 trở lên trên máy Android 13+ |
| 4 | Rìa kính | Có vòng nén các ô màu dọc rìa, viền hơi ngả màu (tán sắc) |
| 5 | Kính theo kịp nền | Khi lưới trôi qua kính, phần khúc xạ đổi liên tục, không đứng hình |
| 6 | Chạm vào kính | Kính co lại nhẹ (press), chữ đổi `Regular` ↔ `Clear` |
| 7 | Nghiêng máy | Highlight ở rìa dịch chuyển |
| 8 | Chữ đổi màu | Khi nền sau kính đổi sáng/tối, chữ chuyển trắng ↔ đen |
| 9 | Độ mượt | Không giật rõ rệt; đo bằng `adb shell dumpsys gfxinfo os26launcher framestats` |
| 10 | Logcat | Không có exception, không có cảnh báo AGSL compile lỗi |
| 11 | Nền kín dưới system bars | Lưới ô màu phủ cả dưới thanh trạng thái, không hở viền |

### Thử chỉnh tham số (tuỳ chọn)

Thêm vào `onCreate`, sau `enableDynamicBackground`:

```kotlin
glass.dispersionStrength = 0.3f       // viền cầu vồng rõ hơn
glass.refractionFalloff = 0f          // kiểu vát phẳng cũ, bẻ cong dàn đều
glass.setGlassTint(Color.CYAN, 0.25f) // kính màu, 0.25 là độ đậm
glass.debugApiLevelCap = 32           // so sánh với pipeline classic (chỉ để debug, đừng để lại)
```

**Giải thích:** `dispersionStrength` điều khiển viền màu ở rìa. `refractionFalloff` từ 0 đến 4: càng cao thì vòng nén càng mảnh và dồn sát mép; `0` là kiểu cũ trải đều. `setGlassTint(color, strength)` nhuộm màu cho kính với độ đậm 0–1. `debugApiLevelCap` giả lập tầng API thấp để xem pipeline cũ trông ra sao ngay trên máy mới.

### Nếu có sự cố

| Triệu chứng | Làm gì |
|---|---|
| Kính đứng hình | Kiểm tra `enableDynamicBackground = true` đã chạy |
| Kính trong, không thấy gì | Nền phải đứng trước kính trong cùng `FrameLayout` (đúng thứ tự trong XML) |
| Màn hình trống, không có lưới | `onDraw` của `DemoBackdropView` còn chưa viết (bước 1) |
| Không thấy khúc xạ | Xem dòng trạng thái: tier < 33 thì máy không hỗ trợ lens pipeline |
| Không resolve được thư viện | Kiểm tra JitPack ở `settings.gradle.kts` |
| Lỗi inflate `DemoBackdropView` | Constructor phải có tham số `AttributeSet` và package trong XML phải khớp |
| Crash khi mở trên emulator | Thử máy thật hoặc emulator arm64 |
| Lỗi khác | Copy nguyên stacktrace từ Logcat |

---

## Bước 5 — Bảng điều khiển tham số (để hoàn tất mục 4, 7, 8, 10)

Sau lần chạy đầu, các mục 4, 7, 8 chưa chắc nên thêm một bảng điều khiển ở cuối màn hình để thay đổi tham số ngay lúc đang chạy, không phải build lại. Bảng gồm 2 thanh trượt (tán sắc, độ mờ) và 3 công tắc (Sensor, Adaptive, Tint). Dòng trạng thái phía trên còn hiển thị thêm `nền: sáng` hoặc `nền: tối`.

Các file thay đổi: `res/layout/activity_main.xml` (thêm khối `controls` phía dưới) và `MainActivity.kt` (viết lại). `DemoBackdropView.kt` không đổi.

### 5.1 Khối mới trong `activity_main.xml`

Thêm vào **cuối** `FrameLayout`, sau `TextView` `status`:

```xml
<!-- 4. Bảng điều khiển tham số, nằm dưới cùng, ngoài vùng kính -->
<LinearLayout
    android:id="@+id/controls"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:layout_margin="16dp"
    android:background="#99000000"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView android:id="@+id/txtDispersion" ... android:text="Dispersion: 0.12" />
    <SeekBar android:id="@+id/seekDispersion" ... android:max="100" android:progress="12" />

    <TextView android:id="@+id/txtBlur" ... android:text="Blur: 0.062" />
    <SeekBar android:id="@+id/seekBlur" ... android:max="300" android:progress="62" />

    <LinearLayout ... android:orientation="horizontal">
        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/swSensor" ... android:checked="true" android:text="Sensor" />
        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/swAdaptive" ... android:checked="true" android:text="Adaptive" />
        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/swTint" ... android:text="Tint" />
    </LinearLayout>
</LinearLayout>
```

Dấu `...` là các thuộc tính lặp lại (kích thước, màu chữ `#FFFFFF`, cỡ chữ `12sp`). Bản đầy đủ nằm trong file `activity_main.xml` của dự án.

**Giải thích code**

- Bảng đặt **sau** kính trong `FrameLayout` nên được vẽ chồng lên trên. Nó nằm sát đáy (`layout_gravity="bottom"`), cách xa viên kính ở giữa màn hình nên không lọt vào vùng kính chụp nền.
- Nền `#99000000` là màu đen mờ (alpha 0x99 ≈ 60%) để chữ trắng đọc được trên lưới ô nhiều màu.
- `seekDispersion`: `max=100`, `progress=12` → giá trị `12 / 100 = 0.12`, trùng mặc định của demo.
- `seekBlur`: `max=300`, `progress=62` → `62 / 1000 = 0.062`, gần mặc định `0.0625` của thư viện. Dải trượt 0 đến 0.3.
- `MaterialSwitch` là công tắc của Material 3. Dự án đã có `material` và dùng theme `Theme.Material3`, nên dùng được ngay. Ba công tắc chia đều chiều ngang nhờ `layout_width="0dp"` và `layout_weight="1"`.
- `swSensor` và `swAdaptive` bật sẵn (khớp `sensorHighlight` và `adaptiveTint` trong XML của kính), `swTint` tắt sẵn.

### 5.2 `MainActivity.kt` (bản đầy đủ)

```kotlin
package os26launcher

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Trạng thái nền sau kính, hiển thị trên dòng trạng thái (kiểm tra mục 8)
    private var backdropText = "nền: ?"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val glass = findViewById<LiquidGlassView>(R.id.glass)
        val label = findViewById<TextView>(R.id.glassLabel)
        val status = findViewById<TextView>(R.id.status)
        val controls = findViewById<ViewGroup>(R.id.controls)

        val txtDispersion = findViewById<TextView>(R.id.txtDispersion)
        val seekDispersion = findViewById<SeekBar>(R.id.seekDispersion)
        val txtBlur = findViewById<TextView>(R.id.txtBlur)
        val seekBlur = findViewById<SeekBar>(R.id.seekBlur)
        val swSensor = findViewById<MaterialSwitch>(R.id.swSensor)
        val swAdaptive = findViewById<MaterialSwitch>(R.id.swAdaptive)
        val swTint = findViewById<MaterialSwitch>(R.id.swTint)

        fun updateStatus() {
            status.text = "SDK ${Build.VERSION.SDK_INT} · pipeline tier ${glass.effectiveApiLevel} · $backdropText"
        }

        val density = resources.displayMetrics.density
        val statusGap = (16 * density).toInt()
        val controlsGap = (16 * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            status.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = bars.top + statusGap
            }
            controls.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bars.bottom + controlsGap
            }
            insets
        }

        glass.enableDynamicBackground = true
        updateStatus()

        glass.glassAppearanceListener = { isOverLight ->
            label.setTextColor(if (isOverLight) Color.BLACK else Color.WHITE)
            backdropText = if (isOverLight) "nền: sáng" else "nền: tối"
            updateStatus()
        }

        var clear = false
        glass.setOnClickListener {
            clear = !clear
            glass.material = if (clear) GlassMaterial.CLEAR else GlassMaterial.REGULAR
            label.text = if (clear) "Clear" else "Regular"
        }

        seekDispersion.onChange { progress ->
            val v = progress / 100f
            glass.dispersionStrength = v
            txtDispersion.text = String.format(Locale.US, "Dispersion: %.2f", v)
        }

        seekBlur.onChange { progress ->
            val v = progress / 1000f
            glass.blurAmount = v
            txtBlur.text = String.format(Locale.US, "Blur: %.3f", v)
        }

        swSensor.setOnCheckedChangeListener { _, checked ->
            glass.enableSensorHighlight = checked
        }

        swAdaptive.setOnCheckedChangeListener { _, checked ->
            glass.enableAdaptiveTint = checked
        }

        swTint.setOnCheckedChangeListener { _, checked ->
            if (checked) glass.setGlassTint(Color.CYAN, 0.25f) else glass.glassTint = Color.TRANSPARENT
        }
    }
}

/** Rút gọn việc lắng nghe SeekBar: chỉ cần quan tâm giá trị progress. */
private fun SeekBar.onChange(block: (Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = block(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })
}
```

(Bản trên bỏ bớt các dòng chú thích của bản trong dự án cho gọn, logic giống hệt.)

**Giải thích code**

- `backdropText` và `updateStatus()`: gom mọi thông tin thành một dòng. Mỗi khi nền sau kính đổi sáng/tối, `glassAppearanceListener` cập nhật cả màu chữ lẫn dòng trạng thái. Đây là cách **nhìn thấy** mục 8: nếu dòng trạng thái đổi mà chữ không đổi (hoặc ngược lại), có vấn đề ở callback.
- `fun updateStatus()` khai báo ngay trong `onCreate` (hàm cục bộ), dùng được các biến `glass`, `status` ở trên nó.
- Insets: trước đây chỉ đẩy `status` xuống dưới thanh trạng thái. Giờ thêm `controls` nâng lên trên thanh điều hướng bằng `bottomMargin = bars.bottom + 16dp`, nền vẫn phủ kín màn hình vì không đặt padding lên root.
- `SeekBar.onChange`: hàm mở rộng (extension) gọn đại. `OnSeekBarChangeListener` có 3 hàm bắt buộc phải ghi, nhưng ta chỉ dùng `onProgressChanged`. Hàm này đặt `private` ở cấp file nên chỉ dùng trong `MainActivity.kt`.
- `String.format(Locale.US, ...)`: ép dấu chấm thập phân. Nếu để theo locale máy (ví dụ tiếng Việt), số sẽ hiển thị dạng `0,12`.
- `glass.dispersionStrength = v`: gán trực tiếp lên thuộc tính của kính, không cần gọi hàm vẽ lại. Kính đang `enableDynamicBackground = true` nên tự vẽ lại mỗi frame.
- `glass.blurAmount = v`: có thể **không tác động** lên lens pipeline (README chỉ nói đây là tham số của pipeline classic). Đây chính là điều cần kiểm tra bằng thanh trượt.
- `swSensor` bật `enableSensorHighlight` (mục 7), `swAdaptive` bật `enableAdaptiveTint` (liên quan mục 8), `swTint` nhuộm xanh lơ độ đậm 0.25. Tắt tint bằng `glassTint = Color.TRANSPARENT` (alpha 0 = không nhuộm).

### 5.3 Cách kiểm tra từng mục

| Mục | Làm gì | Đạt khi |
|---|---|---|
| 4 | Kéo thanh Dispersion từ 0 lên ~0.3 rồi quan sát rìa kính (nên phóng to ảnh chụp) | Viền ở rìa chuyển từ gần như không có sang có dải màu rõ dần. Ngoài ra trên Regular với Dispersion 0, các ô sát rìa bị nén/uốn cong |
| 7 | Bật Sensor, cầm máy và nghiêng qua lại. So sánh với lúc tắt Sensor | Điểm sáng ở rìa dịch chuyển theo hướng nghiêng khi bật; đứng im khi tắt |
| 8 | Quan sát ô sáng (vàng, xanh lá, xanh lơ) trôi qua sau kính và dòng `nền: ...` | Dòng trạng thái đổi giữa `sáng` và `tối`, chữ trong kính đổi trắng ↔ đen tương ứng. Lưu ý: cần đợi vài giây vì độ sáng được lấy mẫu mỗi 350 ms và có làm mượt |
| 10 | Mở Logcat, lọc theo `os26launcher` (hoặc theo mức Error), kéo các thanh trượt và bật tắt công tắc | Không có exception, không có cảnh báo về AGSL hoặc shader |
| Độ mờ | Kéo thanh Blur xuống 0, rồi lên 0.3, quan sát các ô bên dưới kính | Nếu độ mờ thay đổi thì `blurAmount` có tác dụng lên lens pipeline; nếu không đổi, cần tìm cách khác để giảm mờ (xem tài liệu chính, `material`/`Clear`) |

Sau khi thử, ghi kết quả bổ sung vào phần "Kết quả chạy thử" bên dưới.

---

## Bước 6 — Công tắc nền sáng (kiểm chứng mục 8)

Mục 8 chưa đạt vì dòng trạng thái luôn là `nền: ?`. Bước này thêm công cụ để phân biệt hai khả năng: callback không được gọi, hay nền demo chưa bao giờ đổi trạng thái.

Các file thay đổi: `demo/DemoBackdropView.kt`, `res/layout/activity_main.xml`, `MainActivity.kt`. Dưới đây chỉ trích phần mới; bản đầy đủ nằm trong dự án (bản `MainActivity.kt` ở mục 5.2 đã cũ, hãy đọc file trong dự án).

### 6.1 `DemoBackdropView.kt`: thuộc tính `lightMode`

```kotlin
var lightMode: Boolean = false
    set(value) {
        if (field == value) return
        field = value
        hsv[1] = if (value) 0.25f else 0.65f
        hsv[2] = if (value) 1f else 0.95f
        textPaint.color = if (value) 0xFF333333.toInt() else Color.WHITE
        updateShader()
        invalidate()
    }

private fun updateShader() {
    if (width == 0 || height == 0) return
    val colors = if (lightMode) {
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF3C4.toInt(), 0xFFFFE0B2.toInt())
    } else {
        intArrayOf(0xFF141E30.toInt(), 0xFF4B2A7B.toInt(), 0xFFE0566B.toInt())
    }
    bgPaint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
}

override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    updateShader()
}
```

**Giải thích code**

- `lightMode` là thuộc tính có hàm `set` tùy biến. Mỗi lần gán giá trị mới, view tự đổi bộ màu rồi vẽ lại. `field` là biến lưu trữ thật của thuộc tính, `if (field == value) return` tránh làm lại khi giá trị không đổi.
- Chế độ sáng: độ bão hòa chỉ 0.25 và độ sáng 1.0 (màu pastel), gradient từ trắng sang vàng nhạt, chữ đổi sang xám đậm `#333333`. Mục đích là đẩy độ sáng trung bình của nền lên cao hơn hẳn so với nền tối, chắc chắn vượt ngưỡng 0.60 của bộ đo.
- `updateShader()` tách riêng vì phải gọi ở hai nơi: khi view biết kích thước (`onSizeChanged`) và khi đổi chế độ. Câu lệnh `if (width == 0 ...) return` chặn trường hợp `lightMode` được gán trước khi view có kích thước.

### 6.2 `activity_main.xml`: công tắc `swLight`

Đặt trong bảng `controls`, dưới hàng ba công tắc:

```xml
<com.google.android.material.materialswitch.MaterialSwitch
    android:id="@+id/swLight"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:text="Nền sáng (kiểm tra mục 8)"
    android:textColor="#FFFFFF"
    android:textSize="12sp" />
```

**Giải thích:** công tắc thứ tư tràn hàng ngang nên đặt riêng một hàng, chiều rộng `match_parent`.

### 6.3 `MainActivity.kt`: những chỗ thay đổi

```kotlin
// Thêm thành viên của class
private val handler = Handler(Looper.getMainLooper())
private var callbackCount = 0

// Trong onCreate
val backdrop = findViewById<DemoBackdropView>(R.id.backdrop)
val swLight = findViewById<MaterialSwitch>(R.id.swLight)

fun updateStatus() {
    val over = if (glass.isOverLightBackground) "sáng" else "tối"
    status.text = "SDK ${Build.VERSION.SDK_INT} · pipeline tier ${glass.effectiveApiLevel}\n" +
        "nền (đọc): $over · callback: $callbackCount lần"
}

glass.glassAppearanceListener = { isOverLight ->
    callbackCount++
    label.setTextColor(if (isOverLight) Color.BLACK else Color.WHITE)
    updateStatus()
}

handler.post(object : Runnable {
    override fun run() {
        updateStatus()
        handler.postDelayed(this, 500)
    }
})

swLight.setOnCheckedChangeListener { _, checked ->
    backdrop.lightMode = checked
}

// Thêm hàm vòng đời
override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
}
```

**Giải thích code**

- `glass.isOverLightBackground`: thuộc tính chỉ đọc của kính (theo tài liệu thư viện) cho biết hiện tại nền được coi là sáng hay tối. Dòng trạng thái giờ đọc giá trị này trực tiếp, không phụ thuộc callback, nên không còn hiện `?`. Nếu biên dịch lỗi ở dòng này (không tìm thấy thuộc tính), hãy báo lại để điều chỉnh.
- `callbackCount`: đếm số lần callback `glassAppearanceListener` được gọi. Đây là bằng chứng trực tiếp: nếu `nền (đọc)` đổi mà `callback` vẫn 0 lần thì callback có vấn đề; nếu cả hai cùng đổi thì mọi thứ hoạt động.
- `Handler(Looper.getMainLooper())` và `postDelayed`: chạy lại `updateStatus()` mỗi 500 ms trên luồng chính, vì giá trị đọc được có thể đổi mà không có callback. `Runnable` tự đặt lại chính nó bằng `postDelayed(this, ...)`.
- `onDestroy`: `removeCallbacksAndMessages(null)` huỷ vòng lặp khi activity đóng, tránh rò rỉ bộ nhớ.
- `swLight` gán `backdrop.lightMode` theo trạng thái công tắc.
- Chữ trong kính (`label`) vẫn chỉ đổi màu bởi callback, không đổi theo giá trị đọc. Đúng ý: để thấy rõ callback có hoạt động hay không.

### 6.4 Cách đọc kết quả

Bật công tắc "Nền sáng", đợi 2–3 giây (bộ đo lấy mẫu mỗi 350 ms và có làm mượt), rồi tắt đi, đợi tiếp. Quan sát dòng 2 của trạng thái và màu chữ trong kính:

| Quan sát | Ý nghĩa |
|---|---|
| `nền (đọc)` đổi tối → sáng, `callback` tăng, chữ đổi sang đen | Mục 8 **đạt**. Trước đó chỉ là nền demo không đủ sáng để vượt ngưỡng |
| `nền (đọc)` đổi nhưng `callback` vẫn 0 lần | Giá trị đổi nhưng callback không được gọi: lỗi của thư viện hoặc cách dùng callback. Với launcher sẽ tự đọc `isOverLightBackground` để đổi màu chữ |
| `nền (đọc)` vẫn `tối` dù nền trắng xoá | Bộ đo không phản ứng với nền này: có thể đo vùng khác, hoặc không chạy trên pipeline hiện tại. Ghi lại và báo để phân tích tiếp |

---

## Bước 7 — Ảnh nền: wallpaper làm nền cho kính

Vấn đề: kính chỉ chụp được những gì nằm **trong cây View của app**. Wallpaper hệ thống được vẽ bởi một surface khác, nên kính không khuc xạ được nó. Muốn kính khuc xạ wallpaper thì app phải có bản sao ảnh đặt trong cây View (một `ImageView` nằm trước kính).

### 7.1 Tra cứu: app đọc được wallpaper như thế nào

Nguồn: tài liệu `WallpaperManager` trên developer.android.com và commit AOSP "Update read wallpaper methods".

| Phiên bản Android | `WallpaperManager.getDrawable()` và các hàm đọc tương tự |
|---|---|
| 12 trở xuống | Cần quyền `READ_EXTERNAL_STORAGE` |
| 13 | Không đọc trực tiếp được nữa: trả về **wallpaper mặc định của hệ thống** (một số bản 13 ném `SecurityException`) |
| 14 trở lên | **Luôn ném `SecurityException`** |
| Mọi phiên bản | App có quyền `MANAGE_EXTERNAL_STORAGE` ("Quyền truy cập tất cả tệp") vẫn đọc được wallpaper thật |

Commit AOSP nói rõ các hàm `getDrawable`, `peekDrawable`, `getFastDrawable`, `peekFastDrawable`, `getWallpaperFile` đều trả dữ liệu wallpaper mặc định trừ khi app giữ quyền nội bộ `READ_WALLPAPER_INTERNAL` (chỉ dành cho ứng dụng hệ thống) hoặc `MANAGE_EXTERNAL_STORAGE`.

**Hệ quả cho đề tài:** thiết bị thử của bạn là Android 14, nên app launcher không thể đọc wallpaper thật bằng quyền thường. Hai lối đi khả thi:

| Phương án | Cách làm | Ưu | Nhược |
|---|---|---|---|
| A. App tự quản lý ảnh nền | Người dùng chọn ảnh trong app (Photo Picker, không cần quyền); app lưu bản sao để vẽ và làm nền cho kính. Có thể đặt ảnh đó làm wallpaper hệ thống bằng `WallpaperManager.setBitmap` (quyền `SET_WALLPAPER` là quyền thường) để hai bản khớp nhau | Không cần quyền đặc biệt, chạy mọi phiên bản, phù hợp phân phối qua Play | Không theo dõi được nếu người dùng đổi wallpaper ở nơi khác; live wallpaper không dùng được |
| B. Quyền "Tất cả tệp" | App xin `MANAGE_EXTERNAL_STORAGE`, người dùng cấp thủ công trong Cài đặt, sau đó đọc wallpaper thật | Khớp wallpaper thật của người dùng | Quyền nhạy cảm, Google Play hạn chế nghiêm (khó được duyệt cho launcher); chỉ phù hợp phân phối ngoài Play. Chưa kiểm chứng với wallpaper động (live) |

Bước này thử cả hai để có số liệu thật trên máy của bạn.

### 7.2 Thay đổi trong dự án

| File | Thay đổi |
|---|---|
| `AndroidManifest.xml` | Thêm `MANAGE_EXTERNAL_STORAGE` (chỉ để thử phương án B) |
| `res/layout/activity_main.xml` | Thêm `ImageView` `wallpaper` (ẩn sẵn, đặt giữa nền demo và kính); thêm 3 nút và một dòng trạng thái `txtWallpaper` trong bảng điều khiển; chuyển kính lên phía trên màn hình (`top|center_horizontal`, cách đỉnh 170dp) để bảng điều khiển cao hơn không che kính |
| `MainActivity.kt` | Thêm Photo Picker, hàm giải mã ảnh, hàm thử đọc wallpaper hệ thống |

### 7.3 Code chính và giải thích

**Thứ tự View trong `activity_main.xml`**

```xml
<DemoBackdropView android:id="@+id/backdrop" ... />      <!-- nền demo -->
<ImageView android:id="@+id/wallpaper" ... android:scaleType="centerCrop" android:visibility="gone" />
<LiquidGlassView android:id="@+id/glass" ... />          <!-- kính, khai báo sau cùng trong nhóm nền -->
```

- `ImageView` khai báo **sau** nền demo nên vẽ chồng lên nó, và **trước** kính nên kính chụp được ảnh. Khi ẩn (`gone`) thì kính quay về chụp nền demo.
- `scaleType="centerCrop"`: phóng ảnh cho phủ kín màn hình, cắt phần thừa, giống cách wallpaper hiển thị trên màn hình chính.

**Phương án A: chọn ảnh bằng Photo Picker**

```kotlin
private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) loadPickedImage(uri)
}

btnPick.setOnClickListener {
    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
}
```

- `PickVisualMedia` mở trình chọn ảnh của hệ thống. Người dùng chọn một ảnh, app nhận về `Uri` mà **không cần xin quyền đọc ảnh**.
- `registerForActivityResult` phải khai báo khi khởi tạo activity (ở đây là thuộc tính của class), không được gọi muộn trong `onClick`.
- `ImageOnly` giới hạn chỉ chọn ảnh, không chọn video.

```kotlin
private fun decodeScaled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
```

- Ảnh chụp từ camera thường 12 MP trở lên, nếu nạp nguyên kích thước thì tốn bộ nhớ và dễ lỗi. Hàm này đọc kích thước trước (`inJustDecodeBounds = true` chỉ đọc tiêu đề, không tải pixel), rồi chọn `inSampleSize` là lũy thừa của 2 lớn nhất sao cho ảnh vẫn không nhỏ hơn màn hình.
- `openInputStream` phải mở **hai lần** vì luồng đọc xong lần đầu không quay lại đầu được. `use { }` tự đóng luồng.
- Hàm chạy trong `Thread` riêng (`loadPickedImage`) rồi `runOnUiThread` để gán ảnh, tránh đơ giao diện. Demo chưa xử lý xoay theo EXIF, nên ảnh chụp dọc có thể hiển thị nghiêng.

**Phương án B: thử đọc wallpaper hệ thống**

```kotlin
private fun tryReadSystemWallpaper() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        txtWallpaper.text = "Chưa có quyền 'Tất cả tệp'. ..."
        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        return
    }
    try {
        val d = WallpaperManager.getInstance(this).drawable
        ...
    } catch (e: SecurityException) {
        txtWallpaper.text = "Ảnh nền: SecurityException (${e.message})"
    }
}
```

- `Environment.isExternalStorageManager()` (từ Android 11) cho biết app đã được cấp quyền "Tất cả tệp" chưa. Nếu chưa, mở thẳng màn hình cài đặt quyền của app (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`) để người dùng bật công tắc. Quyền này **không** hiện hộp thoại xin quyền thông thường.
- Khi có quyền, `WallpaperManager.getInstance(this).drawable` trả wallpaper thật (theo tài liệu). Nếu vẫn lỗi thì bắt `SecurityException` và hiển thị thông báo thay vì làm app crash.
- Trên Android 13 trở xuống đọc thật sự có thể khác (xem bảng 7.1), nhưng máy thử của bạn là Android 14.
- Manifest thêm `MANAGE_EXTERNAL_STORAGE` kèm `tools:ignore="ScopedStorage"` để tắt cảnh báo lint. Đây chỉ là quyền dùng thử; nếu chọn phương án A thì nên xoá quyền này khỏi manifest.

### 7.4 Cách thử và điều cần quan sát

| Thí nghiệm | Thực hiện | Ghi lại |
|---|---|---|
| A. Chọn ảnh | Bấm "Chọn ảnh", chọn một ảnh nhiều chi tiết (phong cảnh, ảnh có chữ). Làm thử thêm với ảnh sáng và ảnh tối | Kính có khuc xạ ảnh như khuc xạ lưới ô màu không; dòng `nền (đọc)` đổi theo độ sáng của ảnh không; có bị đơ không |
| B1. Xin quyền | Bấm "Đọc wallpaper hệ thống", app mở Cài đặt, bật quyền "Tất cả tệp" cho app | Quyền bật được không; màn hình Cài đặt có hiện đúng app không |
| B2. Đọc wallpaper | Quay lại app, bấm nút lần nữa | Dòng `txtWallpaper` ghi gì: kích thước ảnh, `null` hay `SecurityException`. Hình hiển thị có **đúng là wallpaper đang dùng** hay chỉ là wallpaper mặc định của hãng. Nếu wallpaper của bạn là live wallpaper thì ghi riêng kết quả |
| C. Hiệu năng | Chạy `adb shell dumpsys gfxinfo os26launcher reset`, để app với ảnh nền chạy ~30 giây, rồi chạy lại lệnh không có `reset` | Janky frames, GPU p50/p99. So với mốc lần đo 2 (GPU p50 3 ms, p99 4 ms) |

### 7.5 Khi nào coi là xong demo để sang phát triển chức năng chính

Demo là một "spike": mục đích là gỡ các rủi ro lớn trước khi đầu tư vào launcher thật, không phải làm cho đẹp. Dưới đây là đề xuất tiêu chí (số ngưỡng là đề xuất của mình, bạn có thể điều chỉnh):

| # | Tiêu chí | Trạng thái |
|---|---|---|
| 1 | Checklist 1–11 đạt trên máy mục tiêu | Đã xong (Android 14) |
| 2 | Chốt hệ UI là View/XML | Đã xong |
| 3 | **Chốt nguồn nền cho kính** (phương án A hoặc B) và xác nhận kính khuc xạ ảnh thật đẹp, không đơ | Danh sách đã thu gọn ở trên. Đã thử A và B trên Android 14, cả hai chạy được (xem "Kết quả ảnh nền (bước 7)"). Đã chốt phương án A (xem "Quyết định đã chốt"); B giữ làm tuỳ chọn sau. Đo hiệu năng với ảnh nền đã làm, xem "Kết quả đo đối chứng kính hiện/ẩn" |
| 4 | **Thử nhiều kính cùng lúc** (ví dụ dock + thanh tìm kiếm + 2 thư mục = 4–6 kính) trên máy thật. Đề xuất đạt khi janky dưới 5% và GPU p99 dưới 10 ms | **Đã đo** (Oppo Android 14, Live 4 Android 11), xem "Kết quả thử nhiều kính". Oppo giữ ~60 fps đến 6 kính, janky dưới 0,25%; ngưỡng GPU p99 không đạt (24/20/18 ms) nhưng số GPU trên máy này không đáng tin. Live 4 không đạt. Ngưỡng chưa được xác nhận lại. **Cập nhật 2026-10-15: sẽ đo lại tiêu chí này (ước tính ~10 phút), xong thì đóng demo (Quyết định 6)** **Cập nhật 2026-10-16: đã đo lại, xem "Đo lại tiêu chí 4" ở mục Kết quả chạy thử; demo đã đóng** |
| 5 | **Quyết định xử lý máy cũ**: `minSdk 26` nhưng khuc xạ SDF chỉ có từ Android 13. Chọn: chấp nhận blur đơn giản trên Android 8–12, hoặc nâng `minSdk` | **Đã chốt (2026-10-15): giữ `minSdk 26`**, máy tier thấp dùng bản giảm kính (xem Quyết định 4) |
| 6 | Có phương án dự phòng nếu 3 hoặc 4 thất bại | Đã nêu hướng (nền mờ tĩnh ở máy tier thấp, Quyết định 3 và 4, 5b); chưa hiện thực, chưa kiểm chứng. **Cập nhật 2026-10-15: không chặn đóng demo, sẽ thử nghiệm trong lúc làm chức năng chính (Quyết định 6)** |

Phương án dự phòng nếu thư viện không đáp ứng: giữ cây View, thay kính bằng blur đơn giản (`RenderEffect` blur trên một `View` nền) kèm viền sáng vẽ tay; hoặc chuyển sang thư viện khác. Chưa kiểm chứng khả năng của các phương án này.

Khi đạt 3, 4, 5 (và 6 đã có kế hoạch) thì demo xong. Cập nhật 2026-10-15: tiêu chí 6 chuyển sang làm cùng chức năng chính, demo đóng sau khi đo lại tiêu chí 4 (xem Quyết định 6). Nhánh mã demo (`demo/DemoBackdropView.kt`, bảng điều khiển trong `MainActivity`) nên giữ tách biệt để xóa hay tái sử dụng khi bắt đầu chức năng chính.

---

## Quyết định đã chốt (2026-10-15)

Sau khi đọc kết quả bước 7, hai quyết định sau đã được thống nhất. Chúng chưa được hiện thực trong mã nguồn (xem "Việc cần làm tiếp" bên dưới).

### Quyết định 1: Nguồn nền cho kính là phương án A

- Làm phương án A trước: app tự quản lý ảnh nền (người dùng chọn ảnh bằng Photo Picker, app giữ bản sao và đưa vào `ImageView` đứng trước kính).
- Phương án B (quyền "Tất cả tệp") đã chạy được trên Android 14, nhưng nhược điểm của nó (Google Play hạn chế, live wallpaper) **chưa kiểm chứng xem có khắc phục được không**. Vì vậy B tạm gác lại, không loại bỏ.
- Để sau này cắm B mà không sửa launcher: tách phần code đọc wallpaper hệ thống ra sau một interface nhỏ (ví dụ `BackdropSource`).
- Xóa `MANAGE_EXTERNAL_STORAGE` khỏi manifest chính, để số đo hiệu năng phản ánh đúng bộ quyền sẽ phát hành.

### Quyết định 2: Kết hợp hai cách dùng ảnh nền, theo việc app có là launcher mặc định hay không

| Trạng thái | Hành vi |
|---|---|
| App **chưa** là launcher mặc định (mặc định) | App tự vẽ ảnh nền của riêng nó. Không đụng vào wallpaper hệ thống |
| App **đã** là launcher mặc định | Hỏi người dùng một lần có muốn áp dụng ảnh làm wallpaper hệ thống không. Cũng có một mục trong cài đặt để áp dụng lại sau |

Hai bên dùng chung phần lõi: launcher luôn tự vẽ ảnh trong cây View để kính khúc xạ. Phần "áp dụng làm wallpaper hệ thống" chỉ là một lời gọi `setBitmap` thêm vào, nên không làm đổi cách kính hoạt động.

**Hộp thoại áp dụng wallpaper** có 3 lựa chọn do người dùng tự chọn, kèm một lựa chọn từ chối:

| Nút | Cờ truyền vào `WallpaperManager.setBitmap` |
|---|---|
| Màn hình chính | `FLAG_SYSTEM` |
| Màn khóa | `FLAG_LOCK` |
| Cả hai | `FLAG_SYSTEM or FLAG_LOCK` |
| Không áp dụng | (không gọi gì) |

Nguyên tắc:

- Không tự ghi đè wallpaper khi vừa phát hiện app thành mặc định. Luôn hỏi người dùng.
- Nếu người dùng chuyển sang launcher khác, app không tự khôi phục wallpaper cũ, chỉ ngừng cập nhật.
- Quyền `SET_WALLPAPER` là quyền thường, không cần xin lúc chạy.

**Điểm kỹ thuật cần xử lý (suy từ API chuẩn, chưa chạy thử trên máy):**

1. Cắt ảnh về đúng tỉ lệ màn hình (hoặc truyền `visibleCropHint`) trước khi `setBitmap`, nếu không wallpaper hệ thống tự cắt kiểu khác và lệch với bản launcher vẽ bằng `centerCrop`.
2. `setBitmap` ghi xuống bộ nhớ nên chạy ngoài luồng chính (như hàm giải mã ảnh hiện có), rồi báo kết quả bằng `runOnUiThread`.
3. Kiểm tra `WallpaperManager.isWallpaperSupported()` và `isSetWallpaperAllowed()` trước khi cho bấm. Chưa biết từng hãng xử lý cờ `FLAG_LOCK` ra sao, nên phải thử trên máy thật.
4. Phát hiện launcher mặc định: `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)` từ Android 10; với Android 8–9 thì kiểm tra `Intent` HOME có trỏ về package của app không. Kiểm tra trong `onResume`. **Phần này để sang giai đoạn launcher thật**, vì cần app khai báo `intent-filter` HOME trong manifest thì mới đặt làm mặc định được (chưa kiểm tra manifest hiện tại).

**Hạn chế đã biết:** nếu người dùng đổi wallpaper ở Cài đặt hoặc app khác thì ảnh trong launcher bị lệch. Hướng xử lý đơn giản là nhắc người dùng chọn lại ảnh trong app. Việc bắt sự kiện đổi wallpaper (ví dụ `addOnColorsChangedListener`) chưa kiểm chứng, và trên Android 14 app không đọc được ảnh mới nếu không có quyền "Tất cả tệp".

### Quyết định 3: Giảm số kính hiển thị trên Android thấp (2026-10-15)

Cơ sở: "Kết quả thử nhiều kính" (Live 4 tụt từ 42.7 xuống 16.4 fps khi tăng từ 1 lên 6 kính, Oppo giữ ~60 fps đến 6 kính).

- **Quyết định (người dùng):** trên máy Android thấp, hiển thị ít kính hơn so với Android 13+.
- **"Android thấp" tạm hiểu là** máy chỉ có classic pipeline (tier dưới 33, tức Android 12 trở xuống). Mới đo một máy tier 30 (Android 11); Android 12 (API 31–32, classic GPU) chưa có số đo nào.
- **Điểm chưa chốt:** dock và thanh tìm kiếm trên Android thấp. Phép đo hiện tại là trường hợp xấu nhất (nền demo animate mọi frame, `enableDynamicBackground = true`), còn màn hình chính thật phần lớn đứng yên; theo tài liệu thư viện, mặc định `false` thì nền chỉ chụp một lần. Giả thuyết (chưa kiểm chứng): với nền tĩnh, kính đứng yên rẻ hơn nhiều, và chỉ cần bật chế độ động trong lúc vuốt trang hoặc mở thư mục. Nếu đo nền tĩnh vẫn tụt thì Android thấp dùng nền mờ tĩnh cho tất cả.

Danh sách phần tử kính của iOS 26 và đề xuất giữ/ẩn. **Đây là đề xuất của Claude; người dùng đã chấp nhận nguyên bảng (2026-10-15).** Nguồn: các bài tổng hợp về iOS 26 (MacRumors và một số trang khác), không phải tài liệu Apple trực tiếp; chưa kiểm tra iOS 27.

| Phần tử trên iOS | Vai trò | Android 13+ | Android thấp |
|---|---|---|---|
| Dock | Nền trong mờ của hàng app yêu thích (1 kính rộng) | Kính thật | Ứng viên giữ duy nhất, chưa chốt; không đạt thì nền mờ tĩnh |
| Nút/thanh tìm kiếm trên home | Pill tìm kiếm (1 kính nhỏ) | Kính thật | Nền mờ tĩnh |
| Thư mục đóng | Ô thư mục kính mờ, đổi tông theo hình nền (0 đến nhiều) | Kính thật nếu tổng kính trong phạm vi đã đo (đến 6), phần dư dùng nền tĩnh | Nền mờ tĩnh |
| Thư mục đang mở | Khung chứa app khi mở thư mục (hiểu biết của Claude, chưa tra; 1 kính, tồn tại ngắn) | Kính thật | Có thể giữ nếu đo thấy không giật; không thì nền tĩnh |
| Widget | Khung kính phía sau widget; widget bên thứ ba do app khác vẽ | Nền tĩnh mặc định | Nền tĩnh |
| Icon app | Hiệu ứng thủy tinh nhiều lớp, có kiểu "clear"; là hình vẽ sẵn, không phải kính realtime (20+ mỗi trang) | Vẽ sẵn thành bitmap, không dùng `LiquidGlassView` | Như Android 13+ |
| App Library | Ô thư mục trong mờ + thanh tìm kiếm | Thanh tìm kiếm kính thật, ô thư mục nền tĩnh | Nền tĩnh |

Không thuộc phạm vi launcher (giao diện hệ thống hoặc app khác): màn hình khóa (đồng hồ, widget, nút nhanh), thông báo và trình phát nhạc, Control Center, tab bar trong các app như Safari.

Ghi chú: hiệu ứng ánh sáng theo nghiêng máy (dock, thư mục, nút tìm kiếm) tương ứng `enableSensorHighlight`, theo tài liệu thư viện chỉ có từ API 33 nên máy cũ tự bỏ qua. iOS 26.1 beta có công tắt làm kính đục hơn, nên nền mờ tĩnh ở máy cũ là kiểu hiển thị người dùng iOS đã quen.

### Quyết định 4: Giữ `minSdk 26` (2026-10-15)

- **Quyết định (người dùng):** giữ `minSdk` như hiện tại (26), không nâng, kể cả cho dòng Android cũ.
- **Cơ sở:** bảng ở Quyết định 3 cho máy tier dưới 33 dùng ít kính (hoặc không kính) và nền mờ tĩnh, còn Android 13+ chạy đủ kính. Vì máy cũ không phải gánh nhiều kính realtime, `minSdk` không còn là công cụ để tránh giật; mức hiệu ứng được chọn lúc chạy theo `effectiveApiLevel` (tier). Số đo ủng hộ cách tách này: Android 11 (Live 4) còn 42.7 fps với 1 kính và 16.4 fps với 6 kính trong cảnh xấu nhất, còn Android 14 (Oppo) giữ ~60 fps đến 6 kính (xem "Kết quả thử nhiều kính").
- **Điều kiện và rủi ro chưa xử lý:**
  1. Bản "nền mờ tĩnh" cho máy tier thấp chưa được hiện thực nên chưa kiểm chứng. `RenderEffect` blur chỉ có từ API 31, nên API 26–30 cần cách khác (ví dụ ảnh nền làm mờ sẵn một lần); chưa thử.
  2. Chỉ có một máy cho nhóm cũ (Android 11). Android 8–9 và Android 12 chưa có số đo, tạm xếp vào nhóm thấp theo bảng pipeline của thư viện (API 24–30 classic CPU, API 31–32 classic GPU). Máy Android 8–9 đời thấp có thể chậm hơn Live 4.
  3. Việc dock và thanh tìm kiếm có giữ kính thật trên máy thấp hay không phụ thuộc phép đo nền tĩnh (4b). Kết quả 4b không làm đổi quyết định `minSdk`.

### Quyết định 5: Ảnh có vùng trong suốt và nền mờ tĩnh (2026-10-15)

**5a. Ảnh có vùng trong suốt (đã chốt 2026-10-15: giữ cắt viền trong suốt như code hiện tại).** Phóng ảnh từ tâm sao cho một chiều khớp màn hình, cắt phần thừa ở chiều còn lại: ảnh dọc (cao hơn rộng) thì khớp chiều ngang, cắt chiều dọc thừa; ảnh ngang thì ngược lại.

**Trạng thái mã (cập nhật 2026-10-15, kiểm tra bằng đọc code, chưa chạy):** `PickedImageSource.trimTransparentBorders` đã cắt viền trong suốt khi nhập ảnh. Pixel có alpha lớn hơn 16 được coi là nội dung; một hàng hoặc cột được giữ nếu có ít nhất 1% pixel đục; chỉ cắt viền ngoài, vùng trong suốt bên trong giữ nguyên. Lưu ý:

- Hàm chạy **sau** bước giảm kích thước ảnh, nên ảnh sau khi cắt có thể nhỏ hơn màn hình và bị `centerCrop` phóng lên (mờ hơn).
- Bản sao đã lưu trước khi có hàm này (ví dụ PNG 656x1217 đã thử) chưa được cắt, phải chọn lại ảnh mới có hiệu lực.
- Doc chưa có kết quả chạy thử hàm này trên máy thật, và chưa thử ảnh JPG đối chứng.
- Hướng thay thế (lấp phần hở bằng bản làm mờ của chính ảnh) chưa làm; người dùng chọn giữ cắt viền, không làm hướng này (2026-10-15).

Làm rõ của Claude (chưa được xác nhận):

- Điều kiện nên là so tỉ lệ ảnh với tỉ lệ màn hình (tức `centerCrop`: hệ số = max(rộng màn / rộng ảnh, cao màn / cao ảnh)), không so ngang hay dọc của riêng ảnh. Ví dụ ảnh PNG 656x1217 là ảnh dọc, nhưng khớp chiều ngang 720 px thì chỉ cao khoảng 1336 px trong khi màn hình cao 1604 px, vẫn hở khoảng 268 px.
- Phép phóng chỉ loại được vùng trong suốt nếu trước đó cắt bỏ viền trong suốt (tìm khung bao của các pixel đục). Nếu không, dải trong suốt nằm bên trong ảnh vẫn còn.
- Cái giá: với ảnh trên, khung đục ước tính khoảng 656x890 (suy từ hai dải ~13% màn hình trong ảnh chụp, đây là ước lượng), nên phải phóng khoảng 1,8 lần và chỉ thấy khoảng 61% chiều ngang. Ảnh có viền trong suốt lớn sẽ bị cắt nhiều và mờ hơn.
- Hướng thay thế nếu muốn giữ nguyên bức ảnh: đặt ảnh vừa khít rồi lấp phần hở bằng bản làm mờ của chính ảnh (hoặc một màu lấy từ rìa).

**5b. Nền mờ tĩnh cho máy tier thấp.**

- Ý của người dùng: vẽ một card nền tĩnh, render icon bên trong; chạm vào thì card phóng dần từ tâm ra thành card lớn (kiểu thư mục mở).
- Đề xuất cải tiến của Claude (chờ xác nhận):
  1. Làm mờ **một lần** toàn bộ ảnh nền (thu nhỏ rồi blur ở luồng nền, làm lại khi đổi ảnh hoặc đổi kích thước màn hình). Mỗi card chỉ vẽ đúng vùng tương ứng của ảnh mờ đó, cắt theo bo góc, rồi phủ lớp tint và viền sáng mảnh. Không chụp nền theo từng kính, không cần `RenderEffect`, chạy được từ API 26. App tự giữ ảnh nền (Quyết định 1) nên làm được.
  2. Khi card di chuyển hoặc mở rộng, đổi vùng cắt theo vị trí card để nền mờ luôn khớp với thứ nằm sau.
  3. Khi mở: animate **khung** (trái, trên, phải, dưới) từ khung card tới khung đích thay vì scale cả View (scale làm méo icon và vùng nền); icon di chuyển riêng tới vị trí cuối; phủ dần ảnh nền mờ toàn màn hình cùng lớp tối để phía sau mờ đi.
  4. Có thể tính độ sáng trung bình của vùng dưới card một lần để chọn màu chữ, thay cho callback chậm của kính.
  5. Kiến trúc: một lớp card chung với hai cách vẽ (kính thật `LiquidGlassView` cho tier cao, card tĩnh cho tier thấp); tier quyết định cách vẽ. Có thể thử kính thật cho thư mục đang mở ở máy thấp nếu 4b cho thấy nền tĩnh nhẹ.
- **Giới hạn đã biết:** nền mờ chỉ phản ánh wallpaper, không phản ánh icon hay widget nằm sau card. So với kính thật thì mất khuc xạ, tán sắc và highlight theo cảm biến.
- **Chưa kiểm chứng:** toàn bộ hướng này chưa hiện thực, chưa đo. Bộ nhớ ước tính: ảnh mờ thu nhỏ 1/4 của 720x1604 là 180x401 pixel, khoảng 0,29 MB.

### Quyết định 6: Cách đóng demo (2026-10-15)

- **Quyết định (người dùng):** đo lại tiêu chí 4 (ước tính ~10 phút), làm xong thì đóng demo.
- **Tiêu chí 6 (phương án dự phòng) không chặn đóng demo.** Sẽ thử nghiệm khi làm chức năng chính của app (hướng nền mờ tĩnh ở Quyết định 5b).
- **Cách đo lại tiêu chí 4 (người dùng chấp nhận đề xuất của Claude, 2026-10-15):** bỏ ngưỡng GPU p99 dưới 10 ms vì không đáng tin trên Oppo (24/20/18 ms dù không giật). Thay bằng ba chỉ số: fps trung bình, Missed Vsync và Janky frames (dòng cách tính mới, không phải dòng "legacy"). Vì không đặt ngưỡng số, các ngưỡng đề xuất ở mục 7.5 (janky dưới 5%, GPU p99 dưới 10 ms) không còn dùng làm điều kiện đạt. Live 4 chỉ in một dòng Janky nhiều khả năng theo cách tính cũ, nên trên máy đó chỉ dùng fps và Missed Vsync. **Đã chốt (người dùng): không đặt ngưỡng số cho fps và Missed Vsync, chỉ ghi số đo rồi đánh giá.** Cấu hình đo đã chốt (người dùng xác nhận 2026-10-15): giống "Kết quả thử nhiều kính" (nền demo, không ảnh, 1/4/6 kính, Oppo và Live 4).
- Các mốc 4 đến 8 trong "Mốc còn lại để đóng demo" không chặn đóng demo. Riêng đo nền tĩnh (4b): người dùng chốt đo sau, trên màn hình chính thật của launcher. Các mốc 5 đến 8: người dùng chốt giữ nguyên, làm khi có dịp.

### Việc cần làm tiếp (thứ tự đề xuất)

| # | Việc | Trạng thái |
|---|---|---|
| 1 | Dọn demo theo A: bỏ `MANAGE_EXTERNAL_STORAGE`, tách code đọc wallpaper hệ thống (B) sau interface | **Đã build và chạy** (app chạy trên Oppo và Live 4 sau thay đổi này). Quyền đã gỡ khỏi manifest, nút "Đọc wallpaper hệ thống" đã bỏ khỏi layout. Interface `demo/BackdropSource.kt`; B nằm trong `demo/SystemWallpaperSource.kt` (không nối vào giao diện) |
| 2 | Hoàn thiện A: chép ảnh đã chọn vào `filesDir` và nạp lại khi mở app (Uri của Photo Picker chỉ tạm thời); xử lý xoay theo EXIF; điều tra lỗi ảnh chỉ phủ một dải giữa màn hình bằng cách thử ảnh JPG thay vì PNG có alpha | **Đã build và chạy.** `demo/PickedImageSource.kt` lưu bản sao (PNG nếu có alpha, JPEG nếu không), xoay theo EXIF, nạp lại khi mở app. Dòng trạng thái ảnh nền giờ ghi thêm `có alpha` hoặc `không alpha`, và `EXIF: ...` khi vừa chọn ảnh. **Kết quả thử EXIF:** ảnh chụp từ camera máy thử (Android 14) hiển thị đúng chiều trong app, dòng trạng thái ghi `EXIF: không xoay`. Camera máy này xoay sẵn pixel trước khi lưu nên không thử được nhánh EXIF. Trên Vsmart Live 4 (Android 11), ảnh chụp cho `EXIF: xoay 270°` và hiển thị đúng chiều trong app: **nhánh xoay 270° đạt** trên một ảnh. Các giá trị còn lại (90°, 180°, lật) chưa thử (có thể dùng bộ ảnh mẫu `recurser/exif-orientation-examples`). **Việc điều tra ảnh bị cụt:** ảnh PNG đã chọn (656x1217, trạng thái ghi `có alpha`) bị hở hai dải trên và dưới (mỗi dải khoảng 13% chiều cao màn hình) để lộ lưới ô màu của nền demo. Khớp với giả thuyết ảnh có vùng trong suốt ở hai đầu; chưa thử ảnh JPG đối chứng. Hệ quả cho launcher: ảnh có vùng trong suốt sẽ làm lộ thứ nằm sau (và kính sẽ khúc xạ thứ đó), cần quyết định cách xử lý (chưa chốt; các hướng: tô nền đặc phía sau ảnh, hoặc cảnh báo người dùng, hoặc làm phẳng ảnh lên một màu khi nhập). Cập nhật 2026-10-15: code đã cắt viền trong suốt khi nhập ảnh, xem "Quyết định 5" mục 5a, phần trạng thái mã |
| 3 | Thêm nút "Áp dụng làm wallpaper" vào demo, mở hộp thoại 3 lựa chọn ở trên, để thử trên Android 14 từng đích (chính, khóa, cả hai) và xem ảnh có khớp với app không | **Đã build và thử trên máy thật: nút hoạt động ổn với cả ba đích (chính, khóa, cả hai), theo người dùng.** Nút `Áp dụng wallpaper` mở hộp thoại 3 lựa chọn kèm "Không áp dụng". `demo/WallpaperApplier.kt` cắt ảnh centerCrop về đúng kích thước màn hình rồi `setBitmap`; vùng trong suốt được tô đen. Manifest thêm `SET_WALLPAPER`. Chưa ghi lại máy nào đã thử và wallpaper có khớp với ảnh trong app không |
| 4 | Đo hiệu năng với ảnh nền (thí nghiệm C) và thử 4–6 kính cùng lúc (tiêu chí 4 ở mục 7.5), đo gộp một lần. Mốc so sánh: lần đo 2 (GPU p50 3 ms, p99 4 ms) | Đã đo xong trên hai máy: kính hiện/ẩn và 1–6 kính; phần còn lại là 4b. Đối chứng kính hiện/ẩn với **một** kính trên Live 4 và Oppo A3x (xem "Kết quả đo đối chứng kính hiện/ẩn"). **Thử 4–6 kính: đã đo trên Oppo và Live 4, xem "Kết quả thử nhiều kính".** Đã đo Live 4 có ảnh 12 MP và nền demo (ảnh làm nặng thêm nhưng không phải nguyên nhân chính); Oppo ẩn kính đã đo lại với 6 kính (nền demo): vẫn GPU hai nhóm (xem "Kết quả thử nhiều kính"). Công tắc `Ẩn kính` đã build và dùng được. Quy trình: `adb shell dumpsys gfxinfo os26launcher reset`, để app chạy cảnh đó ~30 giây, rồi chạy lại lệnh không có `reset`; làm hai lần (kính hiện, kính ẩn) trên cả Android 14 và Android 11, ghi lại cảnh đo (có ảnh nền hay không) |
| 4b | Đo nền tĩnh trên Live 4 (và Oppo nếu cần): thêm công tắt dừng animation nền và công tắt `enableDynamicBackground`, đo 1 kính (kích thước gốc) với nền đứng yên. Mục đích: xem kính ở trạng thái màn hình chính thật có còn tụt fps như trường hợp xấu nhất đã đo không, để chốt dock và thanh tìm kiếm trên Android thấp | Chưa làm (cần thêm code vào demo). **Lưu ý:** ảnh nền hiện chỉ đè lên `DemoBackdropView`, lưới vẫn vẽ và animate bên dưới (theo `MainActivity.kt`, `applyBackdrop` chỉ hiện `wallpaperView`), nên các lần đo "có ảnh" chưa phải nền tĩnh; 4b cần ẩn hẳn nền demo hoặc dừng animator. Theo đề xuất của Claude, 4b **không chặn** việc đóng demo, có thể đo trên màn hình chính thật. **Cập nhật 2026-10-15: người dùng chốt đo sau, trên màn hình chính thật của launcher** |
| 5 | Ghi phương án dự phòng nếu bước 4 không đạt (tiêu chí 6) | Hướng đã nêu: nền mờ tĩnh thay kính ở máy tier thấp (Quyết định 3 và 4). Đã có hướng cách làm (xem "Quyết định 5"): card nền tĩnh lấy từ một ảnh nền làm mờ sẵn một lần; `RenderEffect` blur chỉ có từ API 31 nên không dùng làm đường chung. Chưa hiện thực, chưa thử |
| 6 | Quyết định `minSdk` / xử lý máy cũ (tiêu chí 5, của người dùng) | **Đã chốt: giữ `minSdk 26`** (Quyết định 4), máy tier thấp dùng bản giảm kính (Quyết định 3) |
| 7 | Giai đoạn launcher thật: thêm `intent-filter` HOME, phát hiện launcher mặc định, hỏi áp dụng wallpaper lần đầu và mục trong cài đặt | Sau khi đóng demo |

---

## Kết quả chạy thử (2026-10-15)

Thiết bị: Android 14 (SDK 34), máy thật. Dòng trạng thái trong app ghi `SDK 34 · pipeline tier 34`, nghĩa là đang chạy **lens pipeline đầy đủ** (từ 33 trở lên).

### Checklist

| # | Kiểm tra | Kết quả | Nguồn |
|---|---|---|---|
| 1 | Sync và build | Đạt | App chạy được |
| 2 | Mở app, không crash | Đạt | Ảnh chụp màn hình |
| 3 | Dòng trạng thái tier >= 33 | Đạt (tier 34) | Ảnh chụp màn hình |
| 4 | Vòng nén và tán sắc ở rìa | **Đạt** (sau bước 5). Khi ô màu đi qua rìa kính, một phần màu bám ở rìa, tách nhẹ và đi theo ô | Người dùng quan sát trực tiếp |
| 5 | Kính theo kịp nền | Đạt | Người dùng xác nhận |
| 6 | Chạm đổi vật liệu | Đạt (ảnh đang ở chế độ `Clear`) | Người dùng xác nhận, ảnh chụp |
| 7 | Nghiêng máy, highlight dịch chuyển | **Đạt.** Bật Sensor và nghiêng máy thì có viền trắng chạy dọc rìa kính, tắt Sensor thì biến mất | Người dùng quan sát trực tiếp |
| 8 | Chữ đổi màu theo nền sáng/tối | **Đạt** (sau bước 6). Lần đầu dòng trạng thái luôn `nền: ?` vì nền demo tối không đổi trạng thái; với công tắc nền sáng thì callback hoạt động. Callback chỉ tăng khi trạng thái đổi hẳn, bật tắt nhanh thì không tăng liên tục (xem bên dưới) | Người dùng quan sát trực tiếp |
| 9 | Độ mượt | Đạt (xem số đo bên dưới) | `dumpsys gfxinfo` |
| 10 | Logcat | **Đạt.** Không thấy lỗi | Người dùng báo lại, chưa đính kèm log |
| 11 | Nền kín dưới system bars | Đạt | Ảnh chụp: ô màu hiện cả dưới thanh trạng thái và thanh điều hướng |

Ghi chú: ở mục 3–8, người dùng nhận xét "tôi nghĩ là ổn" nhưng chỉ chắc chắn mục 3, 5, 6 (có thể thêm 4). Các mục còn lại cần kiểm tra bằng bảng điều khiển tham số (bước 5).

### Số đo hiệu năng (`adb shell dumpsys gfxinfo os26launcher`)

| Chỉ số | Lần đo 1 (4915 frame, ~82 giây) | Lần đo 2 (2739 frame, ~46 giây) |
|---|---|---|
| Janky frames | 6 (0.12%) | 0 (0.00%) |
| Frame time p50 / p90 / p99 | 24 / 25 / 38 ms | 14 / 15 / 16 ms |
| GPU p50 / p90 / p99 | 4 / 6 / 7 ms | 3 / 3 / 4 ms |
| Missed Vsync | 2 | 0 |
| Slow UI thread | 3 | 0 |
| Slow issue draw commands | 5 | 0 |

Nhận xét:

- Cả hai lần đều giữ 60 fps. Hạn của màn 60 Hz là 16.6 ms, GPU chỉ dùng 3–4 ms (p50) cho một kính 280x96dp với nền chuyển động.
- Lần đo 1 có frame time p50 24 ms nhưng profile cho thấy mỗi frame chờ cấp buffer khoảng 10.6–11 ms (`DequeueBufferDuration`), nên đó là độ trễ xếp hàng, không phải chi phí vẽ. Con số "Janky frames (legacy)" 63–75% ở lần đo 1 vì vậy không phản ánh giật thật. Đây là suy luận từ vài dòng profile.
- Lần đo 2 sạch hơn hẳn. Chưa rõ nguyên nhân chênh lệch (có thể do lần 1 đo ngay sau khi khởi động), nên lấy **lần đo 2 làm mốc**.
- Phạm vi: một kính, một máy Android 14. Chưa phản ánh launcher thật (nhiều kính, wallpaper, icon).

### Quan sát từ ảnh chụp màn hình (chế độ `Clear`)

- Kính có viền sáng mảnh ở rìa trái và rìa phải, vùng bên trong làm mờ các ô R, S, T khá mạnh.
- Rìa dưới bên trái ô R hơi biến dạng, có thể là khúc xạ.
- Độ mờ mạnh hơn nhiều so với kính iOS 26 thật (iOS giữ nội dung phía sau rõ hơn). Chế độ `Clear` có lớp làm tối nên chưa chắc đây là mặc định của `Regular`.
- Dòng trạng thái và nền hiển thị đúng, ô màu phủ kín dưới các thanh hệ thống.

### Kết quả sau bảng điều khiển (bước 5)

- **Mục 4, 7, 10: đạt.** Người dùng mô tả đúng các hiệu ứng mà thư viện hứa: phần màu bám và tách ở rìa (khúc xạ kèm tán sắc) và viền sáng chạy theo cảm biến.
- **Mục 8: ban đầu chưa đạt, sau bước 6 đã đạt.** Dòng trạng thái từng luôn là `nền: ?` vì callback `glassAppearanceListener` không được gọi trong suốt quá trình thử. Sau khi thêm công tắc nền sáng, người dùng xác nhận mục 8 đạt, nên nguyên nhân là nền demo tối không đủ sáng để đổi trạng thái, không phải lỗi thư viện.
- **Hành vi callback:** bật/tắt nền sáng nhiều lần thì `callback` **không tăng liên tục**. Giải thích (theo tài liệu thư viện, chưa đọc mã nguồn để xác nhận): bộ đo lấy mẫu khoảng 350 ms một lần, làm mượt bằng trung bình trượt (EMA), và dùng ngưỡng lệch (0.60 để sang sáng, 0.45 để về tối). Bật tắt nhanh thì độ sáng chưa kịp vượt ngưỡng đã quay lại nên trạng thái không đổi và không có callback. Mỗi lần trạng thái đổi hẳn thì callback tăng đúng 1. Hệ quả cho launcher: màu chữ đổi theo callback sẽ chậm hơn nền một chút; cần phản ứng tức thì tự đọc `isOverLightBackground`.
- **Độ mờ (`blurAmount`): hoạt động tốt.** Người dùng báo kéo thanh Blur càng cao thì càng mờ, nên tham số này **có tác dụng trên lens pipeline** (Android 14). Tức là muốn kính trong hơn giống iOS 26 thì hạ giá trị này xuống là được.

Phân tích mục 8 (chưa kiểm chứng, dựa trên tài liệu của thư viện):

- Theo `docs/LIQUID_GLASS_V2.md`, callback chỉ gọi khi độ sáng nền **đổi trạng thái** sáng/tối, và bộ đo dùng ngưỡng lệch (0.60 để chuyển sang sáng, 0.45 để chuyển về tối). Không có đổi trạng thái thì không có callback, và dòng `nền: ?` không được gán giá trị ban đầu.
- Tài liệu cũng nói bộ đo thu nhỏ **view cha** xuống 24x24 để ước lượng độ sáng. Nếu đúng là cả view cha (không chỉ vùng dưới kính) thì nền demo là lưới đều, trôi tuần hoàn, độ sáng trung bình gần như không đổi và không bao giờ vượt ngưỡng. Đây là giả thuyết, chưa được xác minh.
- Có thể đọc trạng thái hiện tại qua thuộc tính chỉ đọc `glass.isOverLightBackground` (theo tài liệu), không cần chờ callback.
- Cách kiểm chứng đề xuất: thêm một công tắc đổi nền demo sang nền sáng (ví dụ gradient trắng/vàng nhạt) để ép trạng thái sáng/tối phải đổi, đồng thời đọc `isOverLightBackground` để hiển thị giá trị ban đầu. Chưa làm trong dự án.

### Kết quả ảnh nền (bước 7)

Thiết bị: Android 14 (SDK 34), tier 34. Nguồn: ba ảnh chụp màn hình (10:45–10:46), xếp theo thứ tự chụp.

| Thí nghiệm | Kết quả | Nguồn |
|---|---|---|
| A. Chọn ảnh (Photo Picker) | **Chạy được.** Dòng trạng thái ghi `ảnh đã chọn 656x1217`, ảnh nằm sau kính, phần ảnh dưới kính bị làm mờ, nền đọc là `sáng` | Ảnh chụp 1 |
| B. Đọc wallpaper hệ thống | **Chạy được trên Android 14.** Trả về ảnh 720x1604, không có `SecurityException`, dòng trạng thái ghi `wallpaper hệ thống 720x1604`. Theo code, nhánh đọc chỉ chạy khi `Environment.isExternalStorageManager()` là `true`, nên quyền "Tất cả tệp" đã được cấp. Khớp với bảng 7.1: Android 14 chỉ đọc được khi có quyền này | Ảnh chụp 2, code `MainActivity.kt` |
| Ẩn ảnh | Quay về lưới ô màu, nền đọc là `tối` | Ảnh chụp 3 |
| C. Hiệu năng với ảnh nền | **Chưa đo** (chưa chạy `dumpsys gfxinfo`) | — |

Quan sát và nhận xét:

- **Kính chụp được ảnh đặt trong cây View.** Cả ảnh chọn lẫn wallpaper đều làm nền cho kính được, đúng nguyên lý ở mục 4 của tài liệu chính. Chưa xác nhận wallpaper thật có "đẹp" hay không, đó là đánh giá của bạn.
- **Nền mượt thì khúc xạ ít lộ.** Wallpaper tím (ảnh 2) ít chi tiết nên hiệu ứng chủ yếu là lớp mờ và ánh hồng nhạt; có vẻ đường cong của wallpaper bị uốn ở rìa phải kính nhưng ảnh chụp tĩnh khó khẳng định. Phù hợp với cảnh báo "nền phẳng thì không thấy khúc xạ" ở tài liệu chính.
- **Ảnh đã chọn chỉ phủ một dải giữa màn hình** (khoảng y = 370 đến 1235 px), phía trên và dưới vẫn lộ lưới ô màu. Không phải lỗi bố cục: `ImageView` là `match_parent` + `centerCrop` (đã kiểm tra file layout). Nhiều khả năng file ảnh có vùng trong suốt ở trên và dưới (PNG có alpha); suy đoán dựa trên phép tính: ảnh 656x1217 phóng `centerCrop` lên màn 720x1604 có hệ số khoảng 1.318, một vùng vuông giữa ảnh sẽ rộng khoảng 865 px, gần bằng chiều cao dải quan sát được. **Chưa kiểm chứng**, cần xem lại file ảnh gốc hoặc thử với ảnh JPG.
- **Wallpaper hệ thống 720x1604 trùng đúng độ phân giải màn hình**, nên phủ kín cả dưới thanh trạng thái và thanh điều hướng, không hở viền.
- **Đã xác nhận (người dùng): đây đúng là wallpaper đang đặt ở màn hình chính**, không phải wallpaper mặc định của hãng. Phương án B hoạt động đúng trên máy thử (Android 14, quyền "Tất cả tệp"). Vẫn chưa thử với live wallpaper.
- **Callback khớp với mô tả ở mục 8.** Số callback là 3 ở ảnh 1 và ảnh 2 (cả hai đều nền sáng, chuyển từ ảnh này sang ảnh kia không đổi trạng thái nên không tăng), rồi 4 ở ảnh 3 khi về nền tối. Bắt đầu từ trạng thái tối thì số lẻ là sáng, số chẵn là tối, đúng với giá trị đọc ở cả ba ảnh.
- **Ảnh 1 có cả dải tối lẫn dải sáng nhưng vẫn đọc là `sáng`.** Chưa đủ dữ kiện để kết luận bộ đo độ sáng lấy mẫu vùng nào.
- **Chưa kết luận được về giật hay đơ**, vì ảnh chụp tĩnh và chưa đo hiệu năng. Riêng ảnh nền tĩnh thì việc kính "đơ" khó lộ ra, nên cần đo `gfxinfo` thay vì chỉ nhìn.

### Kết quả trên Vsmart Live 4 (Android 11, SDK 30)

Nguồn: người dùng quan sát và `dumpsys gfxinfo` (một lần đo, không `reset` trước). Cảnh đo (có ảnh nền hay không, có thao tác gì) chưa được ghi lại.

| Quan sát | Kết quả |
|---|---|
| Pipeline | `pipeline tier 30`: classic pipeline |
| Regular và Clear | Gần như không khác nhau. Khớp với tài liệu chính: `material` là thuộc tính 33+ nên bị bỏ qua ở tier thấp |
| Vòng nén và tán sắc ở rìa | Người dùng thấy rõ hơn hẳn Android 14. Nguyên nhân chưa rõ. Tài liệu nói `dispersionStrength` chỉ có ở 33+, còn classic có `aberrationIntensity` (mặc định 2) và `displacementScale` (mặc định 70). Suy đoán: hiệu ứng thấy được đến từ các tham số classic này, chưa kiểm chứng (dự đoán: kéo thanh Dispersion trên máy này sẽ không đổi gì) |
| Cảm nhận mượt | Người dùng thấy mượt ("tôi nghĩ thế"), nhưng số đo không ủng hộ, xem bảng dưới |

So với mốc Android 14 (lần đo 2):

| Chỉ số | Android 14 (lần đo 2) | Android 11 |
|---|---|---|
| Số frame / thời gian | 2739 / ~46 giây (≈ 60 fps) | 953 / ~21 giây (≈ 45 fps) |
| Janky frames | 0 (0.00%) | 782 (82.06%) |
| Frame time p50 / p90 / p99 | 14 / 15 / 16 ms | 38 / 46 / 53 ms |
| GPU p50 / p90 / p99 | 3 / 3 / 4 ms | 15 / 15 / 18 ms |
| Missed Vsync | 0 | 23 |
| Slow UI thread | 0 | 273 |

Ghi chú cách tính: thời gian ~21 giây lấy từ `Uptime` trừ `Stats since` trong kết quả `gfxinfo`; fps trung bình = số frame chia thời gian, chỉ đúng nếu app ở foreground suốt khoảng đó và nền luôn chuyển động.

Nhận xét:

- Frame time gồm cả thời gian chờ buffer (xem nhận xét ở lần đo 1), và "Janky frames" kiểu legacy cũng không đáng tin tuyệt đối. Tuy vậy hai dấu hiệu độc lập cho thấy nhẹ hơn hẳn mức Android 14: GPU p50 15 ms (gấp khoảng 5 lần) và fps trung bình khoảng 45 thay vì 60.
- GPU 15 ms dồn vào đúng một nấc (672/953 frame), cho thấy chi phí khá cố định mỗi frame. Chưa biết nguồn: kính, nền demo (nhiều ô bo góc và chữ vẽ mỗi frame) hay ảnh nền. Cần đo đối chứng (ví dụ ẩn kính) mới kết luận được.
- Hệ quả tạm cho tiêu chí 5 (`minSdk`): tổng GPU của cảnh demo (một kính cộng nền) đã gần hết ngân sách 16.6 ms trên máy này, nên launcher nhiều kính trên máy Android cũ nhiều khả năng cần phương án nhẹ hơn. Chưa kết luận: chỉ một máy, một lần đo, chưa tách nguồn chi phí.

### Kết quả đo đối chứng kính hiện/ẩn (2026-10-15)

Cách đo: `dumpsys gfxinfo` theo quy trình ở bảng "Việc cần làm tiếp" (#4), **một** kính, mỗi máy hai lần (kính hiện, rồi kính ẩn bằng công tắc `Ẩn kính`). Thời gian đo = `Uptime` trừ `Stats since`; fps = số frame / thời gian đo (chỉ đúng nếu app ở foreground suốt khoảng đó). **Cảnh đo:** Live 4 có ảnh nền là ảnh tự chụp 3000x4000 (người dùng cho biết); Oppo A3x có ảnh tự chụp 1224x1632 (người dùng cho biết, khớp với ~8 MB image texture); vị trí thanh trượt chưa ghi. Hai máy **không cùng cảnh** (ảnh 12 MP so với 2 MP, pipeline và màn hình cũng khác), nên không so số đo giữa hai máy được; chỉ so được kính hiện với kính ẩn trên cùng một máy.

| Chỉ số | Live 4 (Android 11, tier 30): kính hiện | Live 4: kính ẩn | Oppo A3x 5G (Android 14): kính hiện | Oppo A3x 5G: kính ẩn |
|---|---|---|---|---|
| Thời gian đo | 42.5 s | 39.1 s | 32.4 s | 36.6 s |
| Số frame | 1613 | 2349 | 1944 | 2364 |
| fps trung bình | **37.9** | **60.0** | **60.0** | **64.7** |
| Missed Vsync | 470 | 0 | 0 | 0 |
| Slow UI thread | 930 | 0 | 0 | 3 |
| Frame time p50 / p90 / p99 | 44 / 53 / 61 ms | 18 / 19 / 20 ms | 21 / 24 / 30 ms | 23 / 34 / 36 ms |
| GPU p50 / p90 / p99 | 21 / 21 / 22 ms | 22 / 23 / 24 ms | 5 / 6 / 9 ms | 7 / 22 / 24 ms |
| Janky frames | 1613 (100%) | 2272 (96.72%) | 0 (0.00%) | 5 (0.21%) |

Ghi chú: máy Android 11 chỉ in một dòng "Janky frames", nhiều khả năng là cách tính cũ, không so trực tiếp được với dòng "Janky frames" (cách tính mới) của máy Android 14. Ở cả hai máy, dòng "legacy" và "Janky" cao bất thường dù fps đạt 60 (Live 4 không kính: 96.72% janky nhưng 60.0 fps), nên **không dùng tỉ lệ janky để kết luận**, dùng fps, Missed Vsync và Slow UI thread.

Nhận xét:

- **Live 4, kính làm tụt fps rõ rệt.** Có kính: 37.9 fps, 470 Missed Vsync, 930 Slow UI thread. Ẩn kính: 60.0 fps, 0 và 0. Với một kính, Android 11 mất khoảng 22 fps (~37%) trong lần đo này.
- **Live 4, GPU gần như không đổi** (p50 21 ms khi hiện, 22 ms khi ẩn). Cùng với Slow UI thread 930 so với 0, suy luận: chi phí của kính classic pipeline hiện ra ở luồng UI/CPU chứ không ở GPU. **Chưa kiểm chứng**, cần Perfetto hoặc systrace để chắc.
- **Số GPU trên Live 4 không đọc tuyệt đối được:** GPU p50 22 ms (vượt 16.6 ms) mà vẫn đạt 60 fps khi ẩn kính, nên con số có thể gồm thời gian chờ do các frame chồng nhau (suy đoán).
- **Cảnh Live 4 có ảnh tự chụp 3000x4000 (người dùng xác nhận)**, nên không so được với lần đo Live 4 trước (GPU p50 15 ms, ~45 fps; cảnh khác, không ghi lại) và không so thẳng được với Oppo (ảnh 1224x1632 nhỏ hơn nhiều, pipeline và màn hình cũng khác). Image texture 58–61 MB trên Live 4 so với khoảng 8 MB trên Oppo khớp với kích thước ảnh nạp đủ độ phân giải (1224x1632x4 byte ≈ 8.0 MB cho Oppo). Đọc code `PickedImageSource.decodeScaled`: `inSampleSize` chỉ tăng khi ảnh sau khi thu vẫn không nhỏ hơn màn hình ở **cả hai** chiều. Ảnh 3000x4000 thu 2 lần còn 1500x2000, nên nếu chiều cao màn hình Live 4 lớn hơn 2000 px thì `inSampleSize` giữ ở 1 và ảnh được nạp đủ 12 MP (khoảng 46 MB ở ARGB_8888), khớp với con số texture. **Chưa xác nhận:** cần độ phân giải màn hình Live 4 (`adb shell wm size`) và kích thước ảnh ở dòng trạng thái.
- **Hệ quả cho số đo Live 4:** so sánh kính hiện/ẩn vẫn hợp lệ vì cùng một cảnh. Nhưng con số tuyệt đối (38 fps, GPU ~21 ms) là của cảnh có ảnh 12 MP, không đại diện cho "một kính trên nền demo". Đã đo lại Live 4 với nền demo, xem mục "Đo lại Live 4 với nền demo" bên dưới.
- **Oppo A3x, kính hiện rất nhẹ.** 60.0 fps, 0 janky (cách tính mới), GPU p50 5 ms, p99 9 ms, 0 Missed Vsync, 0 Slow UI thread. Một kính trên Android 14 (tier 34) nằm trong ngưỡng đề xuất p99 dưới 10 ms của tiêu chí 4, nhưng ngưỡng đó dành cho 4–6 kính, chưa thử.
- **Oppo A3x, lần ẩn kính không sạch, không dùng để suy ra chi phí kính.** GPU chia hai nhóm: khoảng 66% frame dưới 9 ms, khoảng 30% từ 17 đến 25 ms, và 9 frame ở nấc cuối histogram. Frame time p90 lên 34 ms, có 5 janky, 3 Slow UI thread. Ẩn kính lẽ ra làm GPU giảm mà ở đây p90 tăng từ 6 lên 22 ms. fps trung bình 64.7 vượt 60 nên màn hình không bị khóa ở 60 Hz trong lần này. Nghi vấn (chưa kiểm chứng): GPU hạ xung khi tải nhẹ, hoặc tần số quét đổi giữa chừng. Cần đo lặp lại.

Hệ quả:

- **Tiêu chí 5 (`minSdk`):** dữ liệu mới củng cố hướng "máy Android cũ cần phương án nhẹ hơn hoặc nâng `minSdk`", vì chỉ một kính đã kéo Live 4 xuống khoảng 38 fps. Đo lại với nền demo cho kết quả cùng hướng (42 fps so với 60 fps, xem mục "Đo lại Live 4 với nền demo"). Giới hạn: chỉ một máy cũ và một kính; độ phân giải màn hình Live 4 chưa ghi lại nên chưa tách được ảnh hưởng của số pixel. Quyết định vẫn của bạn.
- **Tiêu chí 4:** vì một kính trên Android 14 còn rất nhẹ, mức chịu tải thật chỉ lộ ra khi thử 4–6 kính. Chưa làm.

#### Đo lại Live 4 với nền demo

Người dùng gửi số đo này sau khi được đề nghị đo với nền demo (ẩn ảnh). **Đã xác nhận (người dùng): ảnh đã ẩn khi đo.** Image texture vẫn 49–63 MB trong bộ nhớ GPU vì đây là bộ nhớ đệm (phần lớn `purgeable`), không có nghĩa ảnh còn hiển thị. App được khởi động lại (pid khác).

| Chỉ số | Live 4, ảnh 12 MP: kính hiện | Live 4, ảnh 12 MP: kính ẩn | Live 4, nền demo: kính hiện | Live 4, nền demo: kính ẩn |
|---|---|---|---|---|
| Thời gian đo | 42.5 s | 39.1 s | 35.2 s | 37.1 s |
| Số frame | 1613 | 2349 | 1485 | 2227 |
| fps trung bình | **37.9** | **60.0** | **42.2** | **60.0** |
| Missed Vsync | 470 | 0 | 28 | 0 |
| Slow UI thread | 930 | 0 | 532 | 0 |
| Frame time p50 / p90 / p99 | 44 / 53 / 61 ms | 18 / 19 / 20 ms | 38 / 46 / 48 ms | 5 / 5 / 7 ms |
| GPU p50 / p90 / p99 | 21 / 21 / 22 ms | 22 / 23 / 24 ms | 15 / 15 / 19 ms | 12 / 12 / 13 ms |

Nhận xét:

- **Kính vẫn là nguyên nhân chính làm tụt fps trên Live 4.** Nền demo: 42.2 fps khi có kính so với 60.0 fps khi ẩn; frame time p50 38 ms so với 5 ms; Slow UI thread 532 so với 0. GPU chỉ chênh 3 ms (15 so với 12 ms). Cùng hướng với cặp đo có ảnh: chi phí kính classic pipeline phần lớn nằm ngoài GPU (luồng UI/CPU hoặc đồng bộ). Chưa xác định chính xác, cần Perfetto.
- **Ảnh 12 MP làm nặng thêm nhưng không phải nguyên nhân chính.** Kính ẩn: GPU 22 xuống 12 ms, frame time 18 xuống 5 ms, nhưng cả hai đều 60 fps. Kính hiện: 37.9 lên 42.2 fps, Missed Vsync 470 xuống 28, Slow UI thread 930 xuống 532. Tức giả thuyết "chi phí kính phụ thuộc mạnh vào độ nặng của ảnh nền" chỉ đúng một phần nhỏ (khoảng 4 fps).
- **Lần đo Live 4 đầu tiên** (GPU p50 15 ms, ~45 fps, 82% janky, cảnh không ghi) rất gần lần này (GPU p50 15 ms, 42.2 fps). Suy đoán: lần đó cũng là kính cộng nền demo.
- Kính ẩn có frame time p50 5 ms nhưng GPU p50 12 ms, lại cho thấy số GPU trên Android 11 không đọc tuyệt đối được (xem nhận xét ở trên).
- Chưa so được Live 4 với Oppo: pipeline khác (tier 30 so với 34), ảnh khác (không ảnh hoặc 12 MP so với 2 MP), màn hình khác.

#### Thử 4–6 kính (quy trình)

Thay đổi: file mới `res/layout/view_extra_glass.xml` (kính phụ, cùng thuộc tính với kính chính), `activity_main.xml` thêm thanh trượt "Số kính" (1–6) trong bảng điều khiển, `MainActivity.kt` thêm `setGlassCount`. Dòng trạng thái dòng 1 ghi thêm `kính: N`. Thanh trượt, công tắc và nút Ẩn kính giờ áp dụng lên **tất cả** kính. Code đã build và chạy trên Oppo A3x và Vsmart Live 4.

| Số kính | Bố cục |
|---|---|
| 1 | Kính gốc 280x96dp ở giữa phía trên, đúng mốc đo cũ |
| 2–6 | Lưới 2 cột x 3 hàng, mỗi kính 156x72dp, bắt đầu cách đỉnh 116dp. Kính chính chiếm ô đầu và thu về 156x72dp. Các kính không chồng lên nhau |

Lưu ý khi đọc số đo:

- Từ 1 lên 2, kính chính đổi kích thước (280x96 sang 156x72), nên 1 kính (lớn) không so thẳng với 2–6 kính (nhỏ). Để xem độ tăng theo số lượng, so 2, 4, 6 kính với nhau.
- Tổng diện tích kính: 6 kính 156x72dp = 67 392 dp², khoảng 2.5 lần kính gốc (26 880 dp²). Kích thước kính thật của launcher (dock rộng, thanh tìm kiếm, thư mục) chưa biết, nên đây chỉ là một cấu hình thử.
- Mọi kính dùng cùng cấu hình; Sensor và Adaptive bật mặc định, nên mỗi kính có cảm biến và bộ đo độ sáng riêng (theo tài liệu chính, mỗi 350 ms); chi phí đó nằm trong số đo. `collectFrameStats` để mặc định `true` cho mọi kính (tài liệu khuyên tắt ở production).
- Các kính phụ chỉ có chữ cố định màu trắng, không đổi màu theo nền.

Quy trình đo trên Oppo A3x:

1. Giữ cảnh như lần đo trước (ảnh 1224x1632 đang hiển thị, thanh trượt mặc định) để so được với mốc 1 kính: 60.0 fps, GPU p50 5 ms, p99 9 ms (kính hiện).
2. Với mỗi số kính (đề xuất 1, 4, 6): đặt số kính, chờ ~10 giây cho ổn định, chạy `adb shell dumpsys gfxinfo os26launcher reset`, để app chạy ~30 giây không chạm, rồi chạy lại lệnh không có `reset`. Ghi cảnh và thời gian đo (`Uptime` trừ `Stats since`).
3. Thêm một lần ẩn kính ở 6 kính để tách chi phí. Lần ẩn kính trên Oppo trước đã nhiễu, nên đo lặp lại nếu được.

Ngưỡng đề xuất (từ tiêu chí 4 ở mục 7.5, là đề xuất chưa được xác nhận): janky dưới 5% và GPU p99 dưới 10 ms. Cần đọc thêm fps và Missed Vsync, vì tỉ lệ janky đã thấy là không phản ánh đúng trên Android 11.

#### Kết quả thử nhiều kính (2026-10-15)

**Cảnh đo:** nền demo mặc định (ảnh nền ẩn, người dùng xác nhận), thanh trượt mặc định, Sensor và Adaptive bật. Bố cục: 1 kính = 280x96dp ở giữa phía trên; 2–6 kính = lưới 2x3 ô 156x72dp. Mỗi lần `gfxinfo` khoảng 32–36 giây; fps = số frame chia (`Uptime` trừ `Stats since`). Mỗi điểm chỉ đo một lần. Trên Live 4 (API 30), Sensor và Adaptive thuộc nhóm 33+ nên bị bỏ qua (theo tài liệu thư viện).

**Vsmart Live 4 (Android 11, tier 30):**

| Số kính | Số frame | fps | Missed Vsync | Slow UI thread | Frame time p50 / p99 | GPU p50 / p99 |
|---|---|---|---|---|---|---|
| 1 (280x96dp) | 1566 | 42.7 | 53 | 630 | 40 / 53 ms | 15 / 16 ms |
| 4 | 787 | 24.5 | 782 | 783 | 77 / 89 ms | 18 / 19 ms |
| 6 | 562 | 16.4 | 562 | 562 | 117 / 150 ms | 13 / 22 ms |
| 6, ẩn kính | 2139 | 60.0 | 0 | 0 | 12 / 17 ms | 17 / 22 ms |

Ghi chú: Live 4 chỉ in một dòng "Janky frames": 100% ở cả ba lần có kính, 2,20% khi ẩn kính.

**Oppo A3x 5G (Android 14, tier 34):**

| Số kính | Số frame | fps | Janky (cách tính mới) | Missed Vsync | Slow UI thread | Frame time p50 / p99 | GPU p50 / p99 |
|---|---|---|---|---|---|---|---|
| 1 (280x96dp) | 2077 | 60.7 | 4 (0,19%) | 0 | 4 | 23 / 36 ms | 5 / 24 ms |
| 4 | 2155 | 61.1 | 4 (0,19%) | 0 | 2 | 26 / 38 ms | 8 / 20 ms |
| 6 | 2047 | 60.0 | 1 (0,05%) | 0 | 1 | 28 / 36 ms | 10 / 18 ms |
| 6, ẩn kính | 2183 | 63.8 | 5 (0,23%) | 0 | 1 | 23 / 36 ms | 7 / 24 ms |

Ghi chú: dòng "Janky frames (legacy)" trên Oppo là 83–97% ở cả bốn lần dù fps đạt 60, nên không dùng. Số view trong cây là 34, 40, 44 cho 1, 4, 6 kính (mỗi kính thêm hai view), khớp với code.

**Nhận xét Live 4:**

- Chu kỳ frame (1000 chia fps): 23,4 ms (1 kính), 40,8 ms (4 kính), 61 ms (6 kính). Từ 1 lên 4 kính tăng 17,4 ms (khoảng 6 ms mỗi kính), từ 4 lên 6 kính tăng 20,1 ms (khoảng 10 ms mỗi kính). Nên nói chi phí khoảng 6–10 ms mỗi kính; con số thô vì chu kỳ bị lượng tử hóa theo Vsync và điểm 1 kính khác kích thước với các điểm còn lại.
- Từ 4 kính trở lên, gần như mọi frame đều trễ Vsync và chậm ở luồng UI. Ẩn kính (cùng 44 view) thì về 60 fps, 0 Missed Vsync, nên chi phí là của kính, không phải của các view phụ.
- GPU không tăng theo số kính (p50 15, 18, 13 ms; ẩn kính 17 ms; p90 lúc 6 kính 22 ms so với 19 ms khi ẩn). Chi phí nhiều khả năng nằm ở CPU và luồng UI, khớp với việc tài liệu thư viện ghi API 24–30 chạy classic CPU (C++/NEON). Chưa đọc mã nguồn, chưa dùng Perfetto để xác nhận.
- Lần đo 1 kính lặp lại khá khớp lần trước (42.7 so với 42.2 fps).
- Mốc ẩn kính không cố định: lần này frame time p50 12 ms (GPU 17 ms), lần ẩn kính trước là 5 ms (GPU 12 ms). Cả hai đều 60 fps nên không đổi kết luận; nguyên nhân chưa biết.

**Nhận xét Oppo:**

- Giữ ~60 fps đến 6 kính, janky dưới 0,25%, 0 Missed Vsync, Slow UI thread không quá 4. Frame time p50 tăng nhẹ (23 lên 28 ms), p99 gần như không đổi (36–38 ms).
- GPU chia hai nhóm (4–7 ms và 17–24 ms). Tỉ lệ frame GPU từ 15 ms trở lên: 39% (1 kính), 19% (4), 5% (6), 40% (ẩn kính). Càng ít việc thì càng nhiều frame chậm, và hiện tượng lặp lại ở cả hai lần ẩn kính. Suy đoán: GPU hạ xung khi tải nhẹ (chưa đọc xung GPU). Vì vậy **GPU p99 không dùng làm ngưỡng được** trên máy này; nên dùng fps, Missed Vsync và Janky (cách tính mới).
- fps lúc ẩn kính là 63.8, vượt 60, nên tần số quét có thể thay đổi (suy đoán).
- fps bị khóa theo tần số quét nên **không cho biết còn dư bao nhiêu**. Chưa thử quá 6 kính. Muốn biết giới hạn thì tăng số kính lên 10–12 hoặc đọc các cột pha trong `framestats`.

**Hạn chế chung:**

- Mỗi điểm đo một lần; chỉ một máy cho mỗi nhóm.
- Nền demo animate mọi frame. Ảnh nền hiện chỉ đè lên lưới (lưới vẫn vẽ bên dưới), nên **nền tĩnh chưa từng được đo** (việc 4b).
- Mọi kính cùng cấu hình; kính phụ chỉ có chữ cố định; kích thước không phải cỡ kính thật của launcher.
- Giả thuyết chưa kiểm chứng: tài liệu thư viện ghi mỗi kính có một lần chụp nền riêng, nên chi phí classic có thể tỉ lệ với số kính nhân độ phức tạp của nền. Lưới demo (ước tính khoảng 100 ô bo góc kèm chữ) nặng hơn một wallpaper nhiều, nên số Live 4 có thể là cận trên so với launcher thật. Phép đo 4b sẽ trả lời.

**Kết luận tạm:**

- Android 14 (tier 34): 6 kính giữ ~60 fps trong cảnh xấu nhất; chưa biết giới hạn trên.
- Android 11 (tier 30): không giữ được 60 fps ngay cả với 1 kính khi nền chuyển động (42.7 fps); 4–6 kính còn 16–25 fps. Khớp với Quyết định 3 (giảm kính ở máy thấp) và Quyết định 4 (giữ `minSdk 26`).
- Ngưỡng GPU p99 dưới 10 ms của tiêu chí 4 không đáng tin trên Oppo (24/20/18 ms dù không giật). Người dùng đã chấp nhận đổi sang fps, Missed Vsync và Janky (2026-10-15); số đo lại ở "Đo lại tiêu chí 4" bên dưới.

### Đo lại tiêu chí 4 (2026-10-16)

**Cảnh đo:** theo Quyết định 6: nền demo, không ảnh, 1/4/6 kính, thêm một lần ẩn kính (ghi nhận là ở 6 kính, như quy trình đã nêu); Oppo A3x (Android 14) và Vsmart Live 4 (Android 11). Mỗi điểm đo một lần, `gfxinfo` sau `reset`; fps = số frame chia (`Uptime` trừ `Stats since`). Không đặt ngưỡng số, chỉ ghi số đo rồi đánh giá. Số trong bảng do người dùng dán từ `dumpsys`; thời gian đo và fps do Claude tính lại.

**Oppo A3x 5G (Android 14, tier 34):**

| Số kính | Số frame | Thời gian | fps | Janky (cách tính mới) | Missed Vsync | Slow UI thread | Frame time p50 / p99 | GPU p50 / p99 |
|---|---|---|---|---|---|---|---|---|
| 1 | 1720 | 28,7 s | 60,0 | 1 (0,06%) | 0 | 1 | 21 / 36 ms | 5 / 18 ms |
| 4 | 1848 | 29,3 s | 63,2 | 3 (0,16%) | 0 | 2 | 25 / 36 ms | 7 / 19 ms |
| 6 | 1883 | 29,7 s | 63,5 | 9 (0,48%) | 0 | 9 | 30 / 38 ms | 10 / 20 ms |
| 6, ẩn kính | 1946 | 29,8 s | 65,4 | 1 (0,05%) | 0 | 0 | 22 / 30 ms | 6 / 14 ms |

**Vsmart Live 4 (Android 11, tier 30):**

| Số kính | Số frame | Thời gian | fps | Missed Vsync | Slow UI thread | Frame time p50 / p99 | GPU p50 / p99 |
|---|---|---|---|---|---|---|---|
| 1 | 1212 | 30,3 s | 40,0 | 93 | 533 | 40 / 133 ms | 15 / 16 ms |
| 4 | 734 | 30,0 s | 24,5 | 734 | 734 | 77 / 89 ms | 18 / 21 ms |
| 6 | 418 | 25,3 s | 16,5 | 418 | 418 | 117 / 129 ms | 12 / 22 ms |
| 6, ẩn kính | 1823 | 30,4 s | 60,0 | 0 | 0 | 9 / 11 ms | 22 / 24 ms |

Ghi chú: Live 4 chỉ in một dòng "Janky frames" (100% ở ba lần có kính, 0,00% khi ẩn kính), nên chỉ dùng fps và Missed Vsync. Trên Oppo, dòng "Janky frames (legacy)" là 72–96% dù không giật, không dùng. Lần 6 kính trên Live 4 chỉ đo 25 giây.

**Đánh giá:**

- **Oppo (tier 34):** không có dấu hiệu tụt đến 6 kính. Janky tối đa 0,48%, Missed Vsync luôn 0, Slow UI thread tối đa 9. Frame time p50 tăng từ 21 lên 30 ms, còn p99 gần như không đổi (36 đến 38 ms); GPU p99 tăng nhẹ từ 18 lên 20 ms. Kết quả khớp lần đo sớm cùng ngày. fps 63 đến 65 vượt 60 nên tần số quét có thể thay đổi (suy đoán). Vẫn chưa biết giới hạn trên (chưa thử quá 6 kính).
- **Live 4 (tier 30):** lặp lại lần đo sớm (24,5 và 16,5 fps ở 4 và 6 kính; 40,0 so với 42,7 fps ở 1 kính). Chỉ với 1 kính đã còn 40 fps và 93 Missed Vsync (7,7% số frame); từ 4 kính trở lên gần như mọi frame trễ Vsync. Chu kỳ frame (1000 chia fps): 25,0 ms (1 kính), 40,8 ms (4), 60,6 ms (6), 16,7 ms khi ẩn kính. Từ 1 lên 4 kính tăng khoảng 5 ms mỗi kính, từ 4 lên 6 kính khoảng 10 ms mỗi kính (số thô, một lần đo mỗi điểm). Ẩn kính thì về 60 fps và 0 Missed Vsync, nên chi phí là của kính.
- **GPU p99:** trên Oppo, kiểu hai nhóm GPU của lần trước (nhiều frame 15 ms trở lên khi tải nhẹ) không lặp lại đầy đủ: lúc ẩn kính chỉ 0,4% frame từ 15 ms trở lên, so với 40% lần trước. Vậy GPU p99 dao động giữa các lần đo; suy đoán GPU hạ xung chưa được xác nhận. Trên Live 4, GPU p50 lúc ẩn kính là 22 ms trong khi frame time 9 ms, nên số GPU của máy này cũng không dùng được. Việc bỏ ngưỡng GPU p99 vẫn hợp lý.
- **Kết luận:** phép đo lại ủng hộ Quyết định 3 (giảm kính trên Android thấp) và Quyết định 4 (giữ `minSdk 26`): trong cảnh xấu nhất (nền animate mọi frame) Android 14 giữ được 6 kính, còn Android 11 không giữ được ngay cả 1 kính. Phép đo nền tĩnh (4b) vẫn chưa làm, sẽ đo trên màn hình chính thật của launcher.

**Demo đã đóng (2026-10-16)** sau khi đo lại tiêu chí 4, theo Quyết định 6. Việc chưa làm chuyển sang giai đoạn launcher thật: nền mờ tĩnh (tiêu chí 6), đo nền tĩnh (4b) và các mốc 5 đến 8.

### Mốc còn lại để đóng demo và chuyển sang app đầy đủ (cập nhật 2026-10-15)

Các mục của checklist cũ (tán sắc, cảm biến, chữ đổi màu, logcat, độ mờ) đã xong, xem "Kết quả sau bảng điều khiển (bước 5)". Ảnh nền, đo hiệu năng và nhiều kính cũng đã làm. Còn lại theo đề xuất của Claude (người dùng chưa xác nhận từng mốc):

**Nên xong trước khi chuyển:**

| # | Mốc | Vì sao | Trạng thái |
|---|---|---|---|
| 1 | Thử nút "Áp dụng wallpaper" trên máy thật, đủ ba đích (chính, khóa, cả hai), tối thiểu Oppo (Android 14) và Live 4 (Android 11) | Là hành vi của Quyết định 2; chưa biết từng hãng xử lý `FLAG_LOCK` ra sao | **Xong:** người dùng báo nút hoạt động ổn với cả ba đích; chưa ghi máy cụ thể |
| 2 | Chốt cách xử lý ảnh có vùng trong suốt; thử một ảnh JPG đối chứng để xác nhận nguyên nhân hai dải hở | Ảnh PNG có alpha làm lộ thứ nằm sau (kính khúc xạ luôn thứ đó); ảnh hưởng cách nhập ảnh trong launcher | **Đã chốt (2026-10-15): giữ cắt viền trong suốt** (xem "Quyết định 5", mục 5a). **Cập nhật 2026-10-15:** code đã cắt viền trong suốt khi nhập ảnh (`trimTransparentBorders`), doc chưa có kết quả chạy thử; chưa thử ảnh JPG đối chứng |
| 3 | Ghi cách làm nền mờ tĩnh cho máy tier thấp, nhất là API 26–30 (không có `RenderEffect`) | Quyết định 4 (giữ `minSdk 26`) dựa vào bản giảm kính này, mà nó chưa được hiện thực hay thử | **Đã có hướng** (Quyết định 5, mục 5b); chưa hiện thực, chưa thử. Cập nhật 2026-10-15: làm cùng chức năng chính, không chặn đóng demo (Quyết định 6) |

**Không chặn, có thể làm trong lúc dựng launcher:**

| # | Mốc | Ghi chú |
|---|---|---|
| 4 | Đo nền tĩnh (4b) trên Live 4 | Quyết dock và thanh tìm kiếm giữ kính thật trên máy thấp hay không; có thể đo trên màn hình chính thật. Cần ẩn nền demo hoặc dừng animator, vì ảnh nền hiện chỉ đè lên lưới. **Cập nhật 2026-10-15: người dùng chốt đo sau, trên màn hình chính thật của launcher, không đo trong demo** |
| 5 | Đo Android 12 (API 31–32) và Android 8–9 nếu có máy | Chưa có số đo, hiện chỉ suy từ bảng pipeline của thư viện |
| 6 | Thử EXIF 90°, 180°, lật bằng bộ ảnh mẫu | Mới đạt 270° trên một ảnh |
| 7 | Đo với cỡ kính thật của launcher (dock rộng...) | Bố cục thử dùng ô 156x72dp |
| 8 | Live wallpaper với phương án B | B đang gác; chỉ khi quan tâm |

**Giai đoạn launcher thật** (đã nằm ở bảng "Việc cần làm tiếp", việc 7): `intent-filter` HOME, phát hiện launcher mặc định, hỏi áp dụng wallpaper lần đầu và mục trong cài đặt.

---

## Tiêu chí để sang bước tiếp theo

Xem tiêu chí chi tiết ở mục 7.5. Tóm tắt: checklist 1–11 đạt (đã xong), nguồn nền đã chốt phương án A (xem "Quyết định đã chốt"), đã thử nhiều kính và đo hiệu năng trên hai máy, đã chốt giữ `minSdk 26` (Quyết định 4). Còn lại các mốc ở "Mốc còn lại để đóng demo và chuyển sang app đầy đủ".

Cần thử riêng ở bước sau (không nằm trong demo này):

1. Lấy hình nền thật (wallpaper) làm nền cho kính: đã thử ở bước 7, cả hai cách (chọn ảnh, đọc wallpaper với quyền "Tất cả tệp") chạy được trên Android 14; đã chốt phương án A, còn phải đo hiệu năng.
2. Nhiều kính cùng lúc (dock, thanh tìm kiếm, thư mục): đã đo 1–6 kính trên Oppo (Android 14) và Live 4 (Android 11), xem "Kết quả thử nhiều kính". Còn thử với cỡ kính thật của launcher.
3. Hành vi trên máy Android 8–12 (`minSdk 26`, classic pipeline): đã đo một máy Android 11. Android 8–9 và Android 12 chưa có số đo.
