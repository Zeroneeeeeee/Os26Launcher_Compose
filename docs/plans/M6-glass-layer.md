# M6 — Lớp kính (liquid glass) cho dock + đo 4b

> **Trạng thái: XONG 2026-10-28** (đo trên Oppo A3x + Vsmart Live 4).
>
> **For agentic workers:** REQUIRED SUB-SKILL: dùng `superpowers:subagent-driven-development` (khuyến nghị) hoặc `superpowers:executing-plans`. Các bước dùng checkbox `- [ ]`.
>
> **Dự án này KHÔNG có test** (A7). Đừng viết unit test, instrumented test, đừng thêm thư viện test. Kiểm chứng tay trên máy thật, mỗi task có hành động và kết quả mong đợi. Không tick checkbox khi chưa có người xác nhận.
>
> **Đây là mốc kính DUY NHẤT** (A9) — mọi việc liên quan `core/glass` gom hết vào đây, không rải sang mốc khác. **Không kéo-thả** (M7). **Không làm UI cài đặt** cho `glass_level_override` (UI thật ở M8/TT#20) — Task 3 chỉ dựng khóa dữ liệu.

**Goal:** Dock (`DockView`) hiện kính thật (`LiquidGlassView`) thay cho nền phẳng tạm, tự chọn đúng pipeline theo tier máy (`GlassTier`), có khóa ép mức kính (`glass_level_override`) sẵn trong dữ liệu, và có số đo fps/Janky thật trên cảnh đứng yên của màn hình chính (phép đo "4b") để tự tin chốt `GlassBudget` cho tier thấp. Chỉ wire kính vào **dock** — pill tìm kiếm/thư mục/widget chưa có UI nên không dựng khung ưu tiên ngân sách cho chúng (YAGNI, đã chốt lúc brainstorm).

**Architecture:** Package mới `core/glass/` (ngoại lệ được phép import `android.view` + thư viện `com.example.liquidglass`, theo luật phụ thuộc ở `project_structure.md` mục 2). Thêm 1 khóa cài đặt mới (`glass_level_override`) xuyên qua `data/settings` → `domain/model/LauncherSettings` → `domain/repository/SettingsRepository`. **Không** dựng `ResolveGlassPlanUseCase`/khung ưu tiên ngân sách nhiều phần tử (chốt phạm vi lúc brainstorm) — chỉ dock. `DockView` đổi từ tự vẽ nền phẳng (`GradientDrawable`) sang nhận một `GlassSurface` từ `HomeActivity` và add làm con đầu tiên; các `IconCellView` vẫn là con tiếp theo của chính `DockView` (vẽ sau kính nên nằm trên kính, không cần là con *bên trong* `LiquidGlassView`).

**Tech Stack:** Thư viện `com.github.QWEA0:liquidglass:v2.0.11` (đã có trong dự án, không thêm gì mới). Không Compose (B1).

**Spec:** Thiết kế thống nhất trong phiên brainstorm trước plan này (gộp vào thẳng file plan, theo quy ước dự án như M1–M5). Tài liệu liên quan: [`../LiquidGlass-Android.md`](../LiquidGlass-Android.md) (API thư viện — đọc mục 4, 7, 8, 10 trước khi code Task 1); [`../project_structure.md`](../project_structure.md) mục 2 (A9, A10), mục 5 (interface `GlassSurface` gốc); [`../Launcher-Features.md`](../Launcher-Features.md) TT#6; [`../AGENTS.md`](../AGENTS.md) mục "Thư viện LiquidGlass" + "Hiệu năng đã đo (demo)".

## Global Constraints

- `minSdk 26`, `targetSdk 37`, Java 11. View/XML, ViewBinding, không Compose (B1).
- **Luật phụ thuộc:** mọi file mới nằm trong `core/glass/`. Phần đổi `domain`/`data` chỉ là khóa cài đặt override (không có usecase mới).
- **Máy test:** Task 1 và Task 3 cần **Oppo A3x** (A8 — mốc duy nhất cần máy này, gộp đúng một lần mượn). Task 2 đo trên **Vsmart Live 4**.
- AAR chỉ có `.so` cho `arm64-v8a`/`armeabi-v7a` — không test bằng emulator x86 (R6).
- A10: dock giữ nguyên chiều rộng bất kể số icon (0–4), **không** tự ẩn khi trống — kính dock cũng vậy, không ẩn viên kính rỗng.
- `enableDynamicBackground = false` cho kính dock (A10 — dock và ảnh nền đứng yên, chụp một lần là rẻ nhất). Giới hạn đã biết: nếu người dùng đổi ảnh nền trong lúc đang ở Home, kính dock có thể không tự chụp lại ngay — chấp nhận, không sửa ở M6.
- Commit message tiếng Anh, `<type>: <imperative>`, dòng đầu ≤ 72 ký tự, không dấu tiếng Việt. Lệnh viết cho PowerShell. `adb shell am force-stop gambi.global.os26launcher` trước mỗi lần thử lại trạng thái khởi động.
- **Không đụng `demo/`** ở Task 1–3 (cảnh animate của demo còn cần đối chứng tới khi đo xong 4b). Dọn demo là Task 4, sau cùng.

## Review Focus

Bốn trường hợp spec hàm ý nhưng đường đi hạnh phúc không chạm tới:

1. **Ảnh nền đổi khi app đang foreground** (người dùng chọn ảnh mới trong lúc Home đang mở) + `enableDynamicBackground=false` → **đo được trên Oppo A3x (2026-10-28): kính KHÔNG đóng băng vĩnh viễn, nhưng có độ trễ khúc xạ lại nhìn thấy được bằng mắt thường** (chưa đo ms cụ thể) trước khi bám đúng ảnh mới. **Chấp nhận không sửa ở M6** (giống cách M5 chấp nhận việc trang lưới reset khi đổi theme) — ghi lại để không bị coi là bug bỏ sót. (Task 1)
2. **Dock 0 icon vẫn phải hiện viên kính rỗng** (A10) — đừng "cải tiến" bằng cách ẩn `glassSurface.view` khi `items.isEmpty()`. (Task 1)
3. **Split-screen/gập màn hình đổi kích thước `DockView`** — `glassSurface.view` phải được `layout()` lại full-size mỗi lần `DockView.onLayout` chạy (không chỉ lần đầu), giống bài học M5 Task 2 "view con giữ dữ liệu suy ra từ kích thước cha phải cập nhật mỗi lần cha đổi size". (Task 1)
4. **`glass_level_override` đổi khi app đang chạy** (test tạm bằng cách sửa DataStore qua code/adb vì chưa có UI) — vì `GlassSurfaceFactory` (Task 3) chỉ được gọi một lần ở `HomeActivity.setupDock()` (`onCreate`), đổi override lúc đang chạy **không áp dụng ngay** tới khi Activity tái tạo (đổi theme, xoay — dù M5 đã khóa xoay) hoặc kill-restart app. Hạn chế đã biết, chấp nhận không sửa ở M6 (UI thật + áp dụng động là việc của M8). (Task 3)

---

## Task 1: `GlassSurface` + `RealGlassSurface` + nối vào `DockView`

**Files:**
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurface.kt`
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/RealGlassSurface.kt`
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/BlurredBackdropCache.kt` (stub trống — Task 2 mới điền nội dung nếu cần, xem Task 2)
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt`

**Interfaces:**
- Produces: `GlassSurface` (interface), `DockView.setGlassSurface(surface: GlassSurface, cornerRadiusPx: Float)`, `DockView.setBackdropSource(source: View?)`.
- Consumes: `com.example.liquidglass.LiquidGlassView` (đã có dependency).

- [x] **Bước 1: `GlassSurface.kt` + stub `BlurredBackdropCache.kt`**

```kotlin
// core/glass/GlassSurface.kt
package gambi.global.os26launcher.core.glass

import android.view.View
import android.view.ViewGroup

/** Một "bề mặt kính" bất kể cách vẽ thật (LiquidGlassView) hay card mờ tĩnh. */
interface GlassSurface {
    /** View thật sẽ gắn vào layout; nội dung (icon) vẽ SAU nó trong cùng parent, không cần là con bên trong. */
    val view: ViewGroup
    /** Nguồn nền để khúc xạ hoặc để cắt vùng mờ. [blurred] chỉ StaticBlurSurface dùng, null với kính thật. */
    fun setBackdrop(source: View?, blurred: BlurredBackdropCache?)
    /** Bật khi nền hoặc chính kính đang di chuyển (B3). Dock tĩnh (A10) nên gọi với false. */
    fun setDynamic(dynamic: Boolean)
    fun setCornerRadius(px: Float)
    /** Gọi khi nền sáng/tối đổi, để UI đổi màu chữ. */
    fun setAppearanceListener(listener: ((isOverLight: Boolean) -> Unit)?)
}
```

```kotlin
// core/glass/BlurredBackdropCache.kt — stub Task 1, điền ở Task 2 chỉ nếu RealGlassSurface đo không
// đạt trên tier CLASSIC (xem Task 2). Khai trước để chữ ký GlassSurface.setBackdrop ổn định ngay từ đầu.
package gambi.global.os26launcher.core.glass

class BlurredBackdropCache
```

- [x] **Bước 2: `RealGlassSurface.kt`**

```kotlin
// core/glass/RealGlassSurface.kt
package gambi.global.os26launcher.core.glass

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

class RealGlassSurface(context: Context) : GlassSurface {

    private val glassView = LiquidGlassView(context).apply {
        material = GlassMaterial.REGULAR
        // A10: dock và nền đứng yên -> chụp một lần là rẻ nhất (mục 10 LiquidGlass-Android.md).
        enableDynamicBackground = false
    }

    override val view: ViewGroup get() = glassView

    override fun setBackdrop(source: View?, blurred: BlurredBackdropCache?) {
        // blurred không dùng ở đây -- chỉ StaticBlurSurface cần.
        glassView.backdropSource = source
    }

    override fun setDynamic(dynamic: Boolean) {
        glassView.enableDynamicBackground = dynamic
    }

    override fun setCornerRadius(px: Float) {
        glassView.cornerRadius = px
    }

    override fun setAppearanceListener(listener: ((Boolean) -> Unit)?) {
        glassView.glassAppearanceListener = listener
    }
}
```

- [x] **Bước 3: Sửa `DockView.kt` — bỏ nền phẳng, thêm `glassSurface` làm con đầu tiên**

Bỏ `init { background = GradientDrawable(...) }` và hằng số `BACKGROUND_COLOR`. Thêm:

```kotlin
private var glassSurface: GlassSurface? = null
// Có glassSurface thì nó chiếm vị trí con số 0; mọi phép tính theo "icon thứ i" phải cộng offset này.
private val contentOffset: Int get() = if (glassSurface != null) 1 else 0

fun setGlassSurface(surface: GlassSurface, cornerRadiusPx: Float) {
    glassSurface?.view?.let { removeView(it) }
    glassSurface = surface
    surface.setCornerRadius(cornerRadiusPx)
    addView(surface.view, 0)
    requestLayout()
}

fun setBackdropSource(source: View?) {
    glassSurface?.setBackdrop(source, blurred = null)
}

private fun iconCellAt(index: Int): IconCellView? = getChildAt(index + contentOffset) as? IconCellView
```

Sửa các chỗ đang giả định "con thứ i = icon thứ i" sang dùng `contentOffset`/`iconCellAt`:
- `setItems()`: hai vòng `while` so `childCount` với `items.size` phải trừ/cộng `contentOffset` (`childCount - contentOffset < items.size` / `> items.size`); khi xóa dư, `removeViewAt(childCount - 1)` giữ nguyên (icon luôn ở cuối).
- `wireClickListeners()`, `bindAllCells()`, `loadIconFor()`: đổi `getChildAt(index) as IconCellView` → `iconCellAt(index)`.
- `onLayout()`: đầu hàm thêm `glassSurface?.view?.layout(0, 0, width, height)` (Review Focus 3 — co theo đúng kích thước `DockView` mỗi lần layout, không chỉ lần đầu); các dòng còn lại đổi `getChildAt(i) as? IconCellView` → `iconCellAt(i)`.
- `onMeasure()`: không đổi (logic đo kích thước dock không liên quan icon/glass).

Giữ nguyên hằng `CORNER_RADIUS_DP = 28` (chuyển thành `const val CORNER_RADIUS_DP = 28` public ở companion object để `HomeActivity` đọc được).

- [x] **Bước 4: Sửa `HomeActivity.setupDock()` — tạo kính, gắn backdrop**

```kotlin
private fun setupDock() {
    binding.dockView.bindIconLoader(lifecycleScope, viewModel::loadIcon)
    binding.dockView.onAppClick = { item, rect -> viewModel.onAppClicked(item, rect) }

    // M6 Task 1: tạm luôn dùng kính thật để chứng minh tích hợp chạy được trước (Task 3 thay
    // RealGlassSurface(this) bằng kết quả của GlassSurfaceFactory theo tier máy).
    val cornerRadiusPx = DockView.CORNER_RADIUS_DP * resources.displayMetrics.density
    binding.dockView.setGlassSurface(RealGlassSurface(this), cornerRadiusPx)
    binding.dockView.setBackdropSource(binding.backdropImage)
}
```

- [x] **Bước 5: Kiểm chứng tay trên Oppo A3x**

1. `gradlew.bat installDebug`, mở launcher (đã chọn một ảnh nền có chi tiết — ảnh phẳng/gradient sẽ không thấy khúc xạ, mục 4 bước 3 của `LiquidGlass-Android.md`).
2. Quan sát dock: kỳ vọng thấy khúc xạ/tán sắc thật (so với nền phẳng bán trong cũ), bo góc viên thuốc đúng như trước.
3. Dock 0, 1, 2, 3, 4 icon (dùng lại cách giả lập đã dùng ở M5 Task 4 Bước 5 mục 3): nền kính giữ nguyên chiều rộng, viên kính rỗng không ẩn khi 0 icon.
4. Kéo chia split-screen: dock co theo, kính không vỡ/lệch khỏi khung dock.
5. Mở màn chọn ảnh nền, đổi sang ảnh khác trong lúc Home đang hiện (không rời Home) — ghi nhận kính có cập nhật ngay hay giữ ảnh cũ tới khi mở lại app (Review Focus 1, không phải lỗi cần sửa, chỉ cần xác nhận hành vi thật khớp hạn chế đã ghi).

Chưa tick nếu chưa người dùng xác nhận bằng mắt trên máy thật.

- [x] **Bước 6: Commit**

```powershell
git add app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurface.kt app/src/main/java/gambi/global/os26launcher/core/glass/RealGlassSurface.kt app/src/main/java/gambi/global/os26launcher/core/glass/BlurredBackdropCache.kt app/src/main/java/gambi/global/os26launcher/ui/home/view/DockView.kt app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt
git commit -m "feat: render dock with real liquid glass surface"
```

---

## Task 2: Đo 4b trên Vsmart Live 4 (cảnh đứng yên) — quyết định có cần `StaticBlurSurface` không

**Mục tiêu đo:** số đo demo cũ (Live 4: 42.7fps/1 kính, 16.4fps/6 kính) là cảnh **xấu nhất** — nền động mọi frame + `enableDynamicBackground=true`. Dock của mình đứng yên + `enableDynamicBackground=false` (Task 1 đã làm đúng), nên chi phí thật trên màn hình chính có thể rẻ hơn nhiều (tác giả thư viện đo ~0.06ms CPU/frame cho pipeline Lens khi đứng yên — mục 10 `LiquidGlass-Android.md`; CLASSIC pipeline không có số tương tự nên phải tự đo). Task này đo số đó trước khi quyết có cần xây `StaticBlurSurface` hay không.

**Files:** không có file mới ở bước đo. Chỉ cần dock đã dùng `RealGlassSurface` từ Task 1 (không đổi code).

- [x] **Bước 1: Đo trên Vsmart Live 4**

Quy trình giống mục "Hiệu năng đã đo" của `AGENTS.md`:

```powershell
adb shell dumpsys gfxinfo gambi.global.os26launcher reset
# để màn hình chính đứng yên, không chạm, ~30 giây
adb shell dumpsys gfxinfo gambi.global.os26launcher
```

Đo với dock đang hiện (1 kính thật, ảnh nền có chi tiết, không tương tác). Ghi lại: fps trung bình, Janky frames (dòng cách tính mới theo AGENTS.md — bỏ ngưỡng GPU p99, không đáng tin trên Oppo và có thể cũng không đáng tin trên Live 4).

- [x] **Bước 2: Báo số đo, chờ bạn chốt**

Claude đưa số thật đo được ở Bước 1. Bạn tự đánh giá bằng số + cảm giác mượt/giật bằng mắt (theo đúng câu trả lời khảo sát: không đặt ngưỡng trước). Hai nhánh:

- **Nếu ổn** → Task 2 coi như xong, `GlassBudget` cho CLASSIC = 1 (một dock, không cần đo thêm vì M6 chỉ có đúng một kính), **không cần làm `StaticBlurSurface`** ở M6. Bỏ qua Addendum dưới đây, sang thẳng Task 3 với nhánh factory chỉ có `RealGlassSurface`.
- **Nếu giật** → làm Addendum dưới đây trước khi sang Task 3.

**Quyết định (2026-10-28, người dùng chốt):** `RealGlassSurface` trên Live 4 giật rõ (Janky 10.29%, median +9ms so với baseline) — **đi theo nhánh Addendum**. Sau khi sửa 1 bug (xem AGENTS.md mục M6 Task 2 — `glassSurface.view` cần `.measure()` trước `.layout()` vì nó là `ViewGroup` có con), `StaticBlurSurface` đo lại: Janky 2.13%, 50th=5ms, 90th=9ms, 95th=11ms, 99th=24ms — rẻ hơn rõ rệt so với kính thật, chấp nhận được bằng mắt. **Chốt: `StaticBlurSurface` dùng cho tier CLASSIC_GPU/CLASSIC_CPU, `RealGlassSurface` dùng cho tier LENS** — Task 3 ghép hai nhánh này qua `GlassSurfaceFactory` (đặt `STATIC_BLUR_BUILT = true`).

- [ ] **Bước 3: Commit** (chỉ nếu có đổi gì — bước đo không sửa code nên có thể không cần commit riêng)

### Addendum (chỉ làm nếu Bước 2 kết luận "giật"): `StaticBlurSurface` + `BlurredBackdropCache`

**Files:**
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/StaticBlurSurface.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/core/glass/BlurredBackdropCache.kt` (điền nội dung thật, thay thân rỗng của Task 1)

`BlurredBackdropCache` làm mờ bitmap nền một lần (box blur bằng cách thu nhỏ rồi phóng to lại với lọc bilinear — không cần `RenderEffect`, chạy được từ API 26 theo B7; chất lượng/hệ số thu nhỏ chỉnh lại sau khi xem trên máy, suy luận chưa kiểm chứng về hệ số đẹp mắt):

```kotlin
package gambi.global.os26launcher.core.glass

import android.graphics.Bitmap

class BlurredBackdropCache {
    private var sourceBitmap: Bitmap? = null
    private var blurredBitmap: Bitmap? = null

    fun blur(source: Bitmap): Bitmap {
        if (source === sourceBitmap) blurredBitmap?.let { return it }
        val scale = DOWNSCALE_FACTOR
        val small = Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1), true
        )
        val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        sourceBitmap = source
        blurredBitmap = result
        return result
    }

    private companion object {
        const val DOWNSCALE_FACTOR = 0.08f
    }
}
```

`StaticBlurSurface` là `FrameLayout` cắt đúng vùng của bitmap mờ theo vị trí dock trên màn hình (tính một lần khi `setBackdrop` nhận bitmap mới, dùng `getLocationOnScreen` + toạ độ của bitmap nguồn), bo góc theo `cornerRadius`, phủ thêm tint mờ + viền sáng 1px để gần cảm giác kính hơn là chỉ "ảnh mờ cắt ra":

```kotlin
package gambi.global.os26launcher.core.glass

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.FrameLayout

class StaticBlurSurface(context: Context) : GlassSurface {
    private val container = FrameLayout(context)
    private var cornerRadiusPx = 0f
    private var croppedBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * context.resources.displayMetrics.density
        color = 0x33FFFFFF
    }

    private val drawView = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val bmp = croppedBitmap ?: return
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val path = Path().apply { addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawColor(0x1A000000) // tint mờ
            canvas.restore()
            canvas.drawPath(path, borderPaint)
        }
    }

    init {
        container.addView(drawView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    override val view = container

    override fun setBackdrop(source: View?, blurred: BlurredBackdropCache?) {
        // Cắt vùng tương ứng vị trí container trên màn hình từ bitmap mờ của backdrop.
        // Chi tiết tính toán vùng cắt (getLocationOnScreen của source và của container) viết lúc
        // code theo đúng kích thước thật đo được trên máy, không đoán trước ở đây.
    }

    override fun setDynamic(dynamic: Boolean) { /* không áp dụng, luôn tĩnh */ }
    override fun setCornerRadius(px: Float) { cornerRadiusPx = px; drawView.invalidate() }
    override fun setAppearanceListener(listener: ((Boolean) -> Unit)?) { /* chưa làm — không có adaptive tint thật */ }
}
```

- [x] Kiểm chứng tay trên Live 4: `StaticBlurSurface` không giật hơn rõ rệt so với nền phẳng cũ (so bằng dumpsys + cảm giác tay), trông "mờ kiểu kính" chấp nhận được (không cần đẹp bằng kính thật).
- [ ] Commit riêng cho Addendum nếu có làm.

---

## Task 3: `GlassTierResolver` + `GlassBudget` + `GlassSurfaceFactory` + khóa `glass_level_override`

**Files:**
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/GlassTier.kt`
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/GlassTierResolver.kt`
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/GlassBudget.kt`
- New: `app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurfaceFactory.kt`
- New: `app/src/main/java/gambi/global/os26launcher/domain/model/GlassLevelOverride.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/data/settings/SettingsKeys.kt`, `SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/domain/model/LauncherSettings.kt`, `domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt` (thay `RealGlassSurface(this)` cứng của Task 1 bằng `GlassSurfaceFactory`)

- [x] **Bước 1: `GlassLevelOverride` + khóa cài đặt**

```kotlin
// domain/model/GlassLevelOverride.kt
package gambi.global.os26launcher.domain.model

enum class GlassLevelOverride { AUTO, FORCE_LOW, FORCE_HIGH }
```

`SettingsKeys.kt` thêm: `val GLASS_LEVEL_OVERRIDE = stringPreferencesKey("glass_level_override")`.

`LauncherSettings.kt` thêm field: `val glassLevelOverride: GlassLevelOverride = GlassLevelOverride.AUTO`.

`SettingsRepository.kt` thêm: `suspend fun setGlassLevelOverride(value: GlassLevelOverride)`.

`SettingsRepositoryImpl.kt`: đọc bằng `GlassLevelOverride.entries.find { it.name == prefs[SettingsKeys.GLASS_LEVEL_OVERRIDE] } ?: GlassLevelOverride.AUTO` (không dùng `valueOf` trực tiếp để không crash nếu giá trị cũ không khớp enum); hàm set ghi `.name` vào DataStore.

- [x] **Bước 2: `GlassTier.kt` + `GlassTierResolver.kt`**

```kotlin
// core/glass/GlassTier.kt
package gambi.global.os26launcher.core.glass

enum class GlassTier { LENS, CLASSIC_GPU, CLASSIC_CPU }
```

```kotlin
// core/glass/GlassTierResolver.kt
package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassLevelOverride

/**
 * KHÔNG tự đọc PowerManager/high-contrast ở đây: thuộc tính `accessibilityMode` (mặc định AUTO) của
 * chính LiquidGlassView đã tự tôn trọng high-contrast/tiết kiệm pin ở tầng thư viện (mục 7
 * LiquidGlass-Android.md) -- đọc lại ở lớp này là trùng lặp hai tầng logic.
 */
class GlassTierResolver {
    fun resolve(effectiveApiLevel: Int, override: GlassLevelOverride): GlassTier = when (override) {
        GlassLevelOverride.FORCE_LOW -> GlassTier.CLASSIC_CPU
        GlassLevelOverride.FORCE_HIGH -> GlassTier.LENS
        GlassLevelOverride.AUTO -> when {
            effectiveApiLevel >= 33 -> GlassTier.LENS
            effectiveApiLevel >= 31 -> GlassTier.CLASSIC_GPU
            else -> GlassTier.CLASSIC_CPU
        }
    }
}
```

- [x] **Bước 3: `GlassBudget.kt`**

```kotlin
// core/glass/GlassBudget.kt
package gambi.global.os26launcher.core.glass

/**
 * M6 chỉ có đúng 1 kính (dock) nên ngân sách chưa thật sự bị chạm tới -- chỉ có ý nghĩa từ M7+ khi
 * thêm pill tìm kiếm/thư mục/widget. Số LENS=6 lấy từ demo (AGENTS.md "Hiệu năng đã đo"). Số CLASSIC
 * = 1 (chốt 2026-10-28 sau Task 2: `StaticBlurSurface` đo Janky 2.13% trên Live 4 --
 * xem AGENTS.md mục M6 Task 2 -- một dock vẫn là đúng 1 kính, không cần đo thêm cho tới M7+).
 */
object GlassBudget {
    private const val LENS_MAX = 6
    private const val CLASSIC_MAX = 1

    fun maxRealGlass(tier: GlassTier): Int = when (tier) {
        GlassTier.LENS -> LENS_MAX
        GlassTier.CLASSIC_GPU, GlassTier.CLASSIC_CPU -> CLASSIC_MAX
    }
}
```

- [x] **Bước 4: `GlassSurfaceFactory.kt`**

```kotlin
// core/glass/GlassSurfaceFactory.kt
package gambi.global.os26launcher.core.glass

import android.content.Context
import com.example.liquidglass.LiquidGlassView
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import javax.inject.Inject

class GlassSurfaceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tierResolver: GlassTierResolver
) {
    /** Tạo một LiquidGlassView tạm chỉ để đọc effectiveApiLevel -- rẻ, không cần add vào layout. */
    fun create(override: GlassLevelOverride): GlassSurface {
        val probeApiLevel = LiquidGlassView(context).effectiveApiLevel
        val tier = tierResolver.resolve(probeApiLevel, override)
        // Nhánh StaticBlurSurface chỉ tồn tại nếu Task 2 Addendum đã làm (xem Task 2 Bước 2). Nếu
        // Task 2 kết luận "ổn", nhánh dưới đổi thành `else -> RealGlassSurface(context)` cho cả 3 tier.
        return when (tier) {
            GlassTier.LENS, GlassTier.CLASSIC_GPU -> RealGlassSurface(context)
            GlassTier.CLASSIC_CPU -> if (STATIC_BLUR_BUILT) StaticBlurSurface(context) else RealGlassSurface(context)
        }
    }

    private companion object {
        // Task 2 Addendum đã làm và đo đạt (2026-10-28) -- xem quyết định ở Task 2.
        const val STATIC_BLUR_BUILT = true
    }
}
```

`GlassModule.kt` (Hilt, `di/`) không cần thiết — `GlassSurfaceFactory` chỉ có constructor injection đơn giản (`@ApplicationContext` + `GlassTierResolver` không cần `@Provides` vì có constructor `@Inject` trần, Hilt tự biết cách tạo cả hai).

- [x] **Bước 5: Sửa `HomeActivity.setupDock()` — dùng factory thay cho `RealGlassSurface(this)` cứng của Task 1**

```kotlin
@Inject lateinit var glassSurfaceFactory: GlassSurfaceFactory
// ...
private fun setupDock() {
    binding.dockView.bindIconLoader(lifecycleScope, viewModel::loadIcon)
    binding.dockView.onAppClick = { item, rect -> viewModel.onAppClicked(item, rect) }

    val cornerRadiusPx = DockView.CORNER_RADIUS_DP * resources.displayMetrics.density
    // M6 Task 3: đọc override hiện tại để tạo đúng GlassSurface. Chưa phản ứng khi override đổi lúc
    // app đang chạy (Review Focus 4) -- đọc giá trị một lần ở đây là đủ cho M6.
    lifecycleScope.launch {
        val override = viewModel.currentGlassLevelOverride() // hàm mới, đọc 1 lần từ SettingsRepository.settings
        binding.dockView.setGlassSurface(glassSurfaceFactory.create(override), cornerRadiusPx)
        binding.dockView.setBackdropSource(binding.backdropImage)
    }
}
```

(`HomeViewModel.currentGlassLevelOverride()`: `suspend fun currentGlassLevelOverride() = settingsRepository.settings.first().glassLevelOverride` — đọc 1 lần, không `collect`.)

- [x] **Bước 6: Kiểm chứng tay**

1. Trên Oppo A3x: dock vẫn hiện kính thật như Task 1 (tier LENS qua factory, không khác gì trước).
2. Sửa tạm giá trị `glass_level_override` bằng cách gọi `settingsRepository.setGlassLevelOverride(...)` từ một nút debug tạm (hoàn nguyên sau khi test, giống cách M5 Task 4 Bước 5 mục 3 giả lập dock) hoặc qua `adb shell run-as ... cat files/datastore/launcher_settings.preferences_pb` để xác nhận giá trị ghi đúng — thử cả 3 giá trị, kill app rồi mở lại, xác nhận đúng loại `GlassSurface` tương ứng xuất hiện trên cả hai máy.
3. Trên Vsmart Live 4: tier CLASSIC qua factory khớp đúng nhánh đã quyết ở Task 2 (Real hoặc Static).

**Kết quả (2026-10-28, người dùng xác nhận):** đo bằng cách tạm ghi `glass_level_override` qua một hàm debug tạm trong `HomeViewModel`/`HomeActivity` (gỡ ngay sau khi test, không còn trong code) vì file DataStore là protobuf nhị phân, không sửa tay qua `adb shell cat/echo` được.
- Oppo A3x (tier tự nhiên LENS), ép `FORCE_LOW` → dock đổi đúng sang `StaticBlurSurface` (card mờ).
- Vsmart Live 4 (tier tự nhiên CLASSIC_CPU), ép `FORCE_HIGH` → dock đổi đúng sang `RealGlassSurface` (kính thật).
- Cả hai máy đều cần mở app 2 lần (hoặc build+cài lại) mới thấy đúng: lần mở đầu `setupDock()` đọc giá trị CŨ vì lệnh ghi override chạy bất đồng bộ (`viewModelScope.launch`) chưa kịp commit trước khi `currentGlassLevelOverride()` đọc trong cùng `onCreate` -- đây đúng là hạn chế đã ghi ở Review Focus 4 (override đổi lúc đang chạy không áp dụng ngay, chỉ áp dụng từ lần `onCreate` kế tiếp), không phải bug.

Chưa tick nếu chưa người dùng xác nhận.

- [x] **Bước 7: Commit**

```powershell
git add app/src/main/java/gambi/global/os26launcher/core/glass/GlassTier.kt app/src/main/java/gambi/global/os26launcher/core/glass/GlassTierResolver.kt app/src/main/java/gambi/global/os26launcher/core/glass/GlassBudget.kt app/src/main/java/gambi/global/os26launcher/core/glass/GlassSurfaceFactory.kt app/src/main/java/gambi/global/os26launcher/domain/model/GlassLevelOverride.kt app/src/main/java/gambi/global/os26launcher/domain/model/LauncherSettings.kt app/src/main/java/gambi/global/os26launcher/domain/repository/SettingsRepository.kt app/src/main/java/gambi/global/os26launcher/data/settings/SettingsKeys.kt app/src/main/java/gambi/global/os26launcher/data/settings/SettingsRepositoryImpl.kt app/src/main/java/gambi/global/os26launcher/ui/home/HomeActivity.kt
git commit -m "feat: pick glass surface by tier via factory and settings override"
```

---


## Task 4: Dọn demo

Xóa (chờ bạn duyệt tên file trước khi xóa, theo quy ước dự án — Claude không tự xóa):

| File | Lý do |
|---|---|
| `app/src/main/java/gambi/global/os26launcher/MainActivity.kt` | Demo kính, hết vai trò đối chứng sau khi M6 có số đo 4b thật |
| `app/src/main/res/layout/activity_main.xml` | Layout của `MainActivity` demo |
| `app/src/main/res/layout/view_extra_glass.xml` | Layout kính phụ của demo |
| `app/src/main/java/gambi/global/os26launcher/demo/DemoBackdropView.kt` | Nền giả để đo hiệu năng demo, không còn cần sau 4b |
| Khối `<activity>` của `MainActivity` trong `AndroidManifest.xml` | Theo cùng việc xóa `MainActivity` |
| String `demo_activity_label` trong `strings.xml` | Nhãn icon "Demo kính", không còn activity để gắn |

- [x] **Bước 1: Kiểm tham chiếu chéo** — tìm còn chỗ nào import/dùng các file trên ngoài chính chúng (đặc biệt `demo/DemoBackdropView.kt` — xác nhận không còn ai dùng ngoài `activity_main.xml` và `MainActivity.kt`).
- [x] **Bước 2: Báo tên file + lý do, chờ bạn duyệt** (đã lập bảng trên, xác nhận lại trước khi xóa thật).
- [x] **Bước 3: Xóa sau khi bạn đồng ý, kiểm `gradlew.bat assembleDebug` xanh**
- [x] **Bước 4: Kiểm chứng tay** — launcher chỉ còn đúng 1 icon trong danh sách app ("OS26 Launcher"), không còn "Demo kính".
- [x] **Bước 5: Commit**

```powershell
git commit -m "chore: remove glass demo after M6 measurement is done"
```

---

## Task 5: Rà soát và đồng bộ tài liệu dự án sau M6

**Lý do:** `docs/project_structure.md` và `docs/Launcher-Features.md` được viết TRƯỚC khi code M6 tồn tại (2026-10-16), nên còn nhiều đoạn mô tả dự kiến đã lệch với thứ thật đã làm (ví dụ: có `GlassModule.kt` trong cây thư mục nhưng Task 3 xác nhận không cần; `ResolveGlassPlanUseCase` chưa làm vì phạm vi M6 chỉ có dock; `GlassTierResolver` không tự đọc high-contrast/tiết kiệm pin như mô tả, dựa vào `accessibilityMode=AUTO` sẵn có của thư viện).

**Files:**
- Modify: `docs/project_structure.md`
- Modify: `docs/Launcher-Features.md`

- [x] **Bước 1: Soát toàn bộ 2 file**, tìm các đoạn còn ghi "chưa làm"/"dự kiến"/mô tả sai lệch về lớp kính, dock, hoặc các file `core/glass`/`di/` so với code thật sau M6 Task 1-4.
- [x] **Bước 2: Cập nhật `project_structure.md`** — cây thư mục (mục 3: `GlassModule.kt` không làm, các file `core/glass` đã xong, `ResolveGlassPlanUseCase` chưa làm ở M6, comment cũ của `DockView.kt`, mục `demo/` đã xóa), mục 5 "Lớp kính" (mô tả `GlassTierResolver`, số đo 4b, `ResolveGlassPlanUseCase`), bảng mốc M6 ở mục 9, rủi ro R3/R4 ở mục 10, bảng đề nghị xóa ở mục 11.
- [x] **Bước 3: Cập nhật `Launcher-Features.md`** — mục 1 (ràng buộc đã chốt: nền mờ tĩnh, số đo 4b), TT#3 (dock), TT#6 (hình nền + kính dock theo tier).
- [x] **Bước 4: Commit**

```powershell
git add docs/project_structure.md docs/Launcher-Features.md docs/plans/M6-glass-layer.md
git commit -m "docs: sync project docs with what M6 actually built"
```
