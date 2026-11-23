package gambi.global.os26launcher.domain.repository

import kotlinx.coroutines.flow.Flow

interface WebSuggestRepository {
    fun suggest(query: String): Flow<List<String>>
}
