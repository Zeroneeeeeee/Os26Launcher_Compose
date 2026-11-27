package gambi.global.os26launcher.core.perf

import android.util.Log
import gambi.global.os26launcher.BuildConfig

/**
 * PG4 — công tắc ĐO hiệu năng, chỉ có tác dụng ở build type `perf` ([BuildConfig.PERF_SWITCHES]); bản `debug` / `release` luôn rỗng.
 *
 * Bật bằng thuộc tính hệ thống rồi khởi động lại app (đọc MỘT lần, lần đầu hỏi):
 * `adb shell setprop debug.os26.perf noRealGlass+noFrosted` → `adb shell am force-stop gambi.global.os26launcher`. Chuỗi rỗng / " " = tắt hết.
 * Đọc bằng lệnh `getprop` (không dùng API ẩn `SystemProperties`). Dùng bởi `tools/perf/measure-home.ps1`.
 */
object PerfSwitches {
    private const val PROP = "debug.os26.perf"
    private const val TAG = "PerfSwitches"

    /** Không cung cấp nền cho kính THẬT trên Home (dock, widget, menu): bề mặt về tĩnh như dưới API 31 không có nền mờ. */
    const val NO_REAL_GLASS = "noRealGlass"

    /** Không cung cấp lớp nền mờ tĩnh của PG3 (ô thư mục, pill; dưới API 31 cả dock, widget, card): bề mặt vẽ màu phẳng. */
    const val NO_FROSTED = "noFrosted"

    /** Bỏ widget (tự vẽ và của app khác) khỏi các trang Home khi vẽ (chỉ hiển thị, không đụng dữ liệu). */
    const val NO_WIDGETS = "noWidgets"

    /** SW: tắt ảnh chụp kính dock (`CachedGlass`): dock vẽ kính thật sống như trước SW, để so số đo. */
    const val NO_GLASS_CACHE = "noGlassCache"

    /** SW: pager chỉ dựng sẵn 1 trang mỗi bên như trước SW, để so số đo. */
    const val NO_PREBUILD = "noPrebuild"

    /** SW: vẽ cả trang dựng sẵn đang nằm ngoài màn (như trước Task 3b), để so số đo. */
    const val NO_OFFSCREEN_SKIP = "noOffscreenSkip"

    /**
     * SP: Spotlight lúc gõ vẽ các thẻ dưới hàng app (lối tắt, danh bạ, Cài đặt, đề xuất) nền phẳng, không kính thật (phương án B), để so
     * số đo. Mặc định là A (kính thật): chủ dự án chọn A 2026-11-26 dù đo Oppo PKD130 A Janky 7,5 %, P95 25 ms so với B 0,04 %, 10,7 ms.
     */
    const val FLAT_SEARCH_CARDS = "flatSearchCards"

    /** LI: tắt kính thật sau icon kiểu Kính (chỉ còn tấm nướng sẵn trong bitmap), để so số đo. */
    const val NO_ICON_GLASS = "noIconGlass"

    /** LI: kính sau icon dùng ảnh chụp thay cho kính tự vẽ bằng shader trên ảnh nền đã mờ sẵn, để so số đo. */
    const val NO_ICON_SHADER = "noIconShader"

    /** HW: màn Cài đặt không làm nóng ảnh nền / bản mờ của Home (`HomeWarmup`), để so số đo lúc bấm "Trải nghiệm". */
    const val NO_HOME_WARMUP = "noHomeWarmup"

    /**
     * SM: Home đọc lại trạng thái cuộn trong composition như trước SM (HomeScreen dựng lại lúc bắt đầu / dừng vuốt, HomeEditContext mới mỗi lần,
     * pill dựng lại mỗi khung), để so số đo.
     */
    const val NO_SCROLL_FIX = "noScrollFix"

    private val flags: Set<String> by lazy { if (BuildConfig.PERF_SWITCHES) read() else emptySet() }

    val noRealGlass: Boolean get() = NO_REAL_GLASS in flags
    val noFrosted: Boolean get() = NO_FROSTED in flags
    val noWidgets: Boolean get() = NO_WIDGETS in flags
    val noGlassCache: Boolean get() = NO_GLASS_CACHE in flags
    val noPrebuild: Boolean get() = NO_PREBUILD in flags
    val noOffscreenSkip: Boolean get() = NO_OFFSCREEN_SKIP in flags
    val flatSearchCards: Boolean get() = FLAT_SEARCH_CARDS in flags
    val noIconGlass: Boolean get() = NO_ICON_GLASS in flags
    val noIconShader: Boolean get() = NO_ICON_SHADER in flags
    val noHomeWarmup: Boolean get() = NO_HOME_WARMUP in flags
    val noScrollFix: Boolean get() = NO_SCROLL_FIX in flags

    private fun read(): Set<String> = try {
        val process = ProcessBuilder("getprop", PROP).redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        value.split('+', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet().also { Log.i(TAG, "perf switches: $it") }
    } catch (e: Exception) {
        Log.w(TAG, "read $PROP failed", e)
        emptySet()
    }
}
