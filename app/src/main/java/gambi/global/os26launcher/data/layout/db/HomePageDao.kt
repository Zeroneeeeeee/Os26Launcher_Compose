package gambi.global.os26launcher.data.layout.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HomePageDao {

    @Query("SELECT * FROM home_pages ORDER BY rank")
    fun observeAll(): Flow<List<HomePageEntity>>

    @Query("SELECT * FROM home_pages ORDER BY rank")
    suspend fun getAll(): List<HomePageEntity>

    @Insert
    suspend fun insert(page: HomePageEntity): Long

    @Query("DELETE FROM home_pages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
