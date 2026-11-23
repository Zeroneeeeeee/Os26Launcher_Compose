package gambi.global.os26launcher.data.layout.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Một trang màn hình chính. Tồn tại độc lập với việc có item hay không (trang trống sống được). */
@Entity(tableName = "home_pages")
data class HomePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rank: Int // thứ tự trang, 0 là trang đầu
)
