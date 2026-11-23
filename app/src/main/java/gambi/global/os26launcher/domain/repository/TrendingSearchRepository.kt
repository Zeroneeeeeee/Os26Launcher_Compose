package gambi.global.os26launcher.domain.repository

/** Các cụm tìm kiếm đang thịnh hành (hot) ở quốc gia của máy, hiện ở ô trống của Spotlight. */
interface TrendingSearchRepository {
    /** Tối đa vài chục cụm, nóng nhất trước. Lỗi mạng / không có mạng: danh sách cũ còn hạn, hoặc rỗng. Không ném lỗi. */
    suspend fun trending(): List<String>
}
