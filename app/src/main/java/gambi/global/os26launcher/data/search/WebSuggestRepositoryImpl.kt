package gambi.global.os26launcher.data.search

import android.util.Log
import gambi.global.os26launcher.domain.repository.WebSuggestRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Singleton
class WebSuggestRepositoryImpl @Inject constructor() : WebSuggestRepository {

    override fun suggest(query: String): Flow<List<String>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || isPhoneNumber(trimmed)) {
            emit(emptyList())
            return@flow
        }
        val suggestions = withTimeoutOrNull(1500L) {
            fetchRemoteSuggestions(trimmed)
        } ?: emptyList()
        emit(suggestions)
    }.flowOn(Dispatchers.IO)

    private fun isPhoneNumber(q: String): Boolean {
        val digits = q.filter { it.isDigit() }
        return digits.length >= 7 || (q.startsWith("+") && digits.length >= 5)
    }

    private fun fetchRemoteSuggestions(q: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&ie=utf-8&oe=utf-8&q=$encoded")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1200
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (connection.responseCode != 200) return emptyList()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(text)
            // Firefox client format: [query, [suggestion1, suggestion2, ...]]
            if (jsonArray.length() > 1) {
                val list = jsonArray.getJSONArray(1)
                val out = ArrayList<String>()
                for (i in 0 until list.length()) {
                    if (out.size >= MAX_SUGGESTIONS) break
                    out.add(list.getString(i))
                }
                out
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("WebSuggest", "fetch failed", e)
            emptyList()
        }
    }

    private companion object {
        /** Google trả tối đa 10 gợi ý. */
        const val MAX_SUGGESTIONS = 10
    }
}
