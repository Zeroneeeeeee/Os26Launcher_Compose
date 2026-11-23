package gambi.global.os26launcher.data.search

import android.os.SystemClock
import android.util.Log
import android.util.Xml
import gambi.global.os26launcher.domain.repository.TrendingSearchRepository
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Tìm kiếm thịnh hành lấy từ RSS "Daily Search Trends" của Google Trends theo quốc gia của máy (`geo` = Locale.country). Chỉ gửi mã
 * quốc gia, không gửi gì của người dùng. Sắp theo lượt tìm ước lượng (`ht:approx_traffic`, vd "2000+") giảm dần; cùng lượt giữ thứ
 * tự của RSS. Giữ trong bộ nhớ [CACHE_MS] để mở ô tìm kiếm nhiều lần không gọi mạng lại.
 */
@Singleton
class TrendingSearchRepositoryImpl @Inject constructor() : TrendingSearchRepository {

    private val mutex = Mutex()
    private var cached: List<String> = emptyList()
    private var cachedAtMs = 0L

    override suspend fun trending(): List<String> = mutex.withLock {
        val now = SystemClock.elapsedRealtime()
        if (cachedAtMs != 0L && now - cachedAtMs < CACHE_MS) return@withLock cached
        val fresh = withContext(Dispatchers.IO) { fetch() }
        if (fresh.isNotEmpty()) {
            cached = fresh
            cachedAtMs = now
        }
        cached
    }

    private fun fetch(): List<String> {
        val geo = Locale.getDefault().country.ifBlank { DEFAULT_GEO }
        return try {
            val connection = (URL("https://trends.google.com/trending/rss?geo=$geo").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            try {
                if (connection.responseCode != 200) return emptyList()
                connection.inputStream.use { parse(it) }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed", e)
            emptyList()
        }
    }

    private class Item(val title: String, val traffic: Int)

    private fun parse(input: java.io.InputStream): List<String> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, "UTF-8")
        }
        val items = ArrayList<Item>()
        var inItem = false
        var title: String? = null
        var traffic = 0
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = null
                        traffic = 0
                    }
                    "title" -> if (inItem) title = parser.nextText().trim()
                    "ht:approx_traffic" -> if (inItem) traffic = parser.nextText().filter { it.isDigit() }.toIntOrNull() ?: 0
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    title?.takeIf { it.isNotEmpty() }?.let { items.add(Item(it, traffic)) }
                }
            }
        }
        // sortedByDescending ổn định: cùng lượt tìm thì giữ thứ tự RSS. Bỏ trùng không phân biệt hoa thường.
        val seen = HashSet<String>()
        return items.sortedByDescending { it.traffic }
            .map { it.title }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_ITEMS)
    }

    private companion object {
        const val TAG = "TrendingSearch"
        const val DEFAULT_GEO = "VN"
        const val TIMEOUT_MS = 3000
        const val CACHE_MS = 30 * 60 * 1000L
        const val MAX_ITEMS = 10
    }
}
