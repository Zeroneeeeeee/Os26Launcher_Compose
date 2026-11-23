package gambi.global.os26launcher.data.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.repository.SearchQueryHistoryRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Singleton
class SearchQueryHistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SearchQueryHistoryRepository {

    private val file: File by lazy {
        File(context.noBackupFilesDir, "search_query_history.json")
    }

    override suspend fun getHistory(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            val json = JSONArray(text)
            val list = ArrayList<String>(json.length())
            for (i in 0 until json.length()) {
                list.add(json.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun addQuery(query: String) = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext
            val current = getHistory().toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            val limited = current.take(20)
            val json = JSONArray(limited)
            file.writeText(json.toString())
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
