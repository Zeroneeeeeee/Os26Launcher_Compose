package gambi.global.os26launcher.domain.model

/** M8d Task 4 — một shortcut của app (động hoặc manifest) trong chỉ mục tìm kiếm. [appLabel] hiện ở dòng phụ. */
data class ShortcutEntry(
    val key: ComponentKey,
    val id: String,
    val label: String,
    val appLabel: String
)
