package gambi.global.os26launcher.core.glass

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.model.GlassCustom
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.config.AppearanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Điểm vào để các view (ô thư mục, panel, thanh tìm kiếm, pill, ô nhóm) hỏi "mình được vẽ kính kiểu nào" mà KHÔNG phải
 * sửa `HomeActivity` (GL, plan "Vì sao chọn thiết kế này"). Lấy qua [from] (Hilt EntryPoint).
 *
 * [tier] được suy NGAY khi khởi tạo với `GlassLevelOverride.AUTO` (theo `Build.VERSION.SDK_INT`, JX) để view không phải chờ DataStore; sau đó thu `settings` và báo [addListener] khi mức kính đổi.
 */
@Singleton
class GlassEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appearance: AppearanceRepository,
    private val tierResolver: GlassTierResolver,
    @ApplicationScope scope: CoroutineScope
) {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    // JX Q2 (chủ dự án 2026-11-20): QWEA0 đã gỡ; mức API dò bằng Build.VERSION.SDK_INT (cùng giá trị trên máy test).
    private val probeApiLevel: Int = android.os.Build.VERSION.SDK_INT

    /** Máy có đường Lens thật (API 33+), độc lập với mức kính người dùng chọn; xem [GlassTierResolver.isRealGlassCapable]. */
    val realGlassCapable: Boolean = tierResolver.isRealGlassCapable(probeApiLevel)

    /** Bộ đệm bitmap mờ nhỏ dùng chung cho mọi kính tại chỗ. */
    val blurCache = BlurredBackdropCache()

    /** GS: mức kính + công tắc người dùng đặt. Khởi tạo mặc định, cập nhật khi cài đặt phát hoặc khi [readConfig]. */
    @Volatile
    var config: GlassConfig = GlassConfig.of(GlassLevelOverride.AUTO, GlassCustom.NONE)
        private set

    @Volatile
    var tier: GlassTier = tierResolver.resolve(probeApiLevel, GlassLevelOverride.AUTO)
        private set

    /** GT: thông số kính dùng chung hiện tại; mọi bề mặt kính đọc ở đây lúc dựng (`BackdropBlurDrawable(..., look)`, `GlassSurface.setLook`). */
    val look: GlassLook get() = config.look

    init {
        // FX1 (C3): trả bản blur khi hệ thống sắp tắt app. Chỉ ở TRIM_MEMORY_COMPLETE (app ở cuối hàng LRU): quay về lúc đó có thể thấy kính
        // vẽ phẳng một-hai khung, chấp nhận được; ở mức nhẹ hơn thì không đụng vì Home hay được quay lại. Ảnh nền chính KHÔNG bị bỏ (tránh nền
        // đen khi quay về). Application context nên callback sống suốt process.
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            @Suppress("DEPRECATION") // mức nền có thể không còn được gửi từ Android 15 (chưa kiểm chứng); Android 11 của Live 4 vẫn gửi
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) blurCache.trim()
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {}
        })
        scope.launch {
            appearance.config.map { GlassConfig.of(it.glassLevel, it.glassCustom, it.glassLook) }.distinctUntilChanged()
                .collect { apply(it) }
        }
    }

    private fun apply(next: GlassConfig) {
        if (next == config) return
        config = next
        tier = tierResolver.resolve(probeApiLevel, next.override)
        // GT: độ mờ đổi -> bản nhỏ dựng lại ở lần xin kế tiếp. Bộ đệm chỉ đụng từ main thread; đang ở main (readConfig lúc dựng Home)
        // thì đặt NGAY để lần vẽ đầu của view mới không xin phải bản cũ.
        val radius = GlassTuning.blurRadiusPx(next.look)
        if (Looper.myLooper() == Looper.getMainLooper()) blurCache.blurRadiusPx = radius
        main.post {
            blurCache.blurRadiusPx = radius
            listeners.forEach { it() }
        }
    }

    /**
     * GS: đọc cấu hình hiện tại từ cài đặt MỘT lần (đã commit) và cập nhật chính singleton này. Dock đọc bằng hàm này lúc dựng, và
     * `RecreateGuard` gọi lại trước `recreate()` nên view dựng lại không thể đọc phải cấu hình cũ.
     */
    suspend fun readConfig(): GlassConfig {
        val s = appearance.config.first()
        val read = GlassConfig.of(s.glassLevel, s.glassCustom, s.glassLook)
        apply(read)
        return read
    }

    /**
     * GS: công tắc "gieo" khi người dùng chuyển từ mức [level] sang Tùy chỉnh: mỗi phần tử bật nếu đang là kính thật dưới [level]
     * trên máy này, nên vào Tùy chỉnh không đổi gì trên màn hình.
     */
    fun seedFor(level: GlassLevelOverride): GlassCustom {
        val seedTier = tierResolver.resolve(probeApiLevel, level)
        fun real(role: GlassRole) =
            GlassPolicy.modeFor(seedTier, role, realGlassCapable, null) == GlassMode.REAL
        return GlassCustom(
            dock = real(GlassRole.DOCK),
            panels = real(GlassRole.FOLDER_PANEL),
            searchBar = real(GlassRole.SEARCH_BAR),
            pill = real(GlassRole.SEARCH_PILL),
            iconMenu = real(GlassRole.ICON_MENU) // SM: như panel, để vào Tùy chỉnh không đổi gì trên màn hình
        )
    }

    /** Chế độ của [role] trên tier hiện tại (GS: và công tắc, nếu mức là Tùy chỉnh). */
    fun modeFor(role: GlassRole): GlassMode =
        GlassPolicy.modeFor(tier, role, realGlassCapable, config.activeCustom)

    /** Số ô thư mục có kính tối đa mỗi trang. */
    fun cellsPerPageLimit(): Int = GlassBudget.maxLiteGlassCellsPerPage(tier, realGlassCapable)

    /** GI Task 4: số icon app có tấm kính mờ tối đa mỗi trang; 0 nếu chế độ kính đang FLAT. */
    fun iconPlatesPerPage(): Int =
        if (modeFor(GlassRole.FOLDER_CELL) == GlassMode.FLAT) 0 else GlassBudget.maxIconPlatesPerPage(tier, realGlassCapable)

    private val backdropListeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * AN Task 10: ảnh nền của Home vừa đổi (nạp đầu, thay, xoá). `HomeActivity` (controller ảnh nền) gọi MỘT lần mỗi lần đổi nội dung
     * `backdrop_image`. Mọi `BackdropBlurDrawable` tự vẽ lại qua [blurCache]; kính thật của panel nghe qua [addBackdropListener]. Main thread.
     */
    fun notifyBackdropChanged() {
        blurCache.notifyBackdropChanged()
        backdropListeners.forEach { it() }
    }

    fun addBackdropListener(listener: () -> Unit) {
        backdropListeners.add(listener)
    }

    fun removeBackdropListener(listener: () -> Unit) {
        backdropListeners.remove(listener)
    }

    /** Gọi từ main thread; nhớ [removeListener] ở `onDetachedFromWindow`. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    companion object {
        fun from(context: Context): GlassEnvironment =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                GlassEntryPoint::class.java
            ).glassEnvironment()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlassEntryPoint {
    fun glassEnvironment(): GlassEnvironment
}
