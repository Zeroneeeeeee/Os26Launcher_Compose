package gambi.global.os26launcher.domain.model

/**
 * Giá trị cột `container` của `LauncherItemEntity` cho lưới chính/dock -- domain không import `data.*`
 * nên không tham chiếu `LauncherItemEntity.CONTAINER_HOME`/`CONTAINER_DOCK` trực tiếp được; hai nhóm
 * hằng số này PHẢI giữ cùng giá trị, đổi 1 bên thì đổi bên kia (M7 Task 5, để UI truyền đúng
 * `targetContainer` khi kéo-thả mà không phá luật `ui` không import `data` trực tiếp).
 */
object ContainerId {
    const val HOME = -100L
    const val DOCK = -101L

    /** TV: màn widget bên trái trang 1 (Today). Thứ tự theo `rank`; `pageId`, `gridX/gridY` để 0; `spanX/spanY` giữ cỡ widget. */
    const val TODAY = -102L
}
