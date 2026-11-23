package gambi.global.os26launcher.domain.model

/**
 * Loại app theo hệ thống (`ApplicationInfo.category`) đã gom về những giá trị Library cần (G2a B1, D12).
 * `VIDEO`/`AUDIO`/`IMAGE` của hệ thống đều thành [ENTERTAINMENT]; loại khác (kể cả `ACCESSIBILITY`) thành [OTHER].
 * [UNDEFINED] = nhà phát triển không khai báo.
 */
enum class AppCategory { SOCIAL, ENTERTAINMENT, GAME, PRODUCTIVITY, NEWS, MAPS, OTHER, UNDEFINED }

/**
 * Nhóm hiển thị trong App Library. 8 nhóm đầu là nhóm thường, thứ tự khai báo cũng là thứ tự hiển thị (D12, xem [REGULAR]).
 * LO: [SUGGESTED], [RECENTLY_ADDED] (đứng đầu khi bật "Gợi ý & Mới thêm") và [ALL] (thay 8 nhóm khi tắt "Tự phân loại").
 */
enum class CategoryGroup {
    SOCIAL, ENTERTAINMENT, GAME, PRODUCTIVITY, NEWS, MAPS, UTILITIES, OTHER,
    SUGGESTED, RECENTLY_ADDED, ALL;

    companion object {
        /** 8 nhóm theo loại app, đúng thứ tự hiển thị. */
        val REGULAR: List<CategoryGroup> = listOf(SOCIAL, ENTERTAINMENT, GAME, PRODUCTIVITY, NEWS, MAPS, UTILITIES, OTHER)
    }
}

data class CategoryGroupApps(val group: CategoryGroup, val apps: List<AppItem>)

/** LO: nội dung trang App Library theo kiểu hiển thị (màn 3.2). */
sealed interface LibraryContent {
    /** Kiểu "Thư mục danh mục": lưới ô nhóm. Rỗng = Empty. */
    data class Folders(val groups: List<CategoryGroupApps>) : LibraryContent

    /** Kiểu "Danh sách A–Z": mọi app chưa ẩn, đã sắp và chia đoạn theo chữ cái ([sections] nối nhau đúng thứ tự). */
    data class Alphabetical(val sections: List<LetterSection>) : LibraryContent

    data class LetterSection(val letter: String, val apps: List<AppItem>)
}
