package gambi.global.os26launcher.domain.repository

interface SearchQueryHistoryRepository {
    suspend fun getHistory(): List<String>
    suspend fun addQuery(query: String)
    suspend fun clearHistory()
}
