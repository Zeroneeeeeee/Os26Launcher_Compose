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

    private val flags: Set<String> by lazy { if (BuildConfig.PERF_SWITCHES) read() else emptySet() }

    val noRealGlass: Boolean get() = NO_REAL_GLASS in flags
    val noFrosted: Boolean get() = NO_FROSTED in flags
    val noWidgets: Boolean get() = NO_WIDGETS in flags

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
