package gambi.global.os26launcher.domain.model

/** GS: các phần tử có công tắc kính thật/mờ ở mức kính [GlassLevelOverride.CUSTOM] (SM thêm [ICON_MENU]). Ô thư mục và ô nhóm Library không có. */
enum class GlassElement { DOCK, PANELS, SEARCH_BAR, PILL, ICON_MENU }

/** GS: một công tắc cho mỗi [GlassElement]: true = kính thật, false = kính mờ. Mặc định tắt hết (SM: kể cả [iconMenu]). */
data class GlassCustom(
    val dock: Boolean = false,
    val panels: Boolean = false,
    val searchBar: Boolean = false,
    val pill: Boolean = false,
    val iconMenu: Boolean = false
) {
    operator fun get(element: GlassElement): Boolean = when (element) {
        GlassElement.DOCK -> dock
        GlassElement.PANELS -> panels
        GlassElement.SEARCH_BAR -> searchBar
        GlassElement.PILL -> pill
        GlassElement.ICON_MENU -> iconMenu
    }

    fun with(element: GlassElement, on: Boolean): GlassCustom = when (element) {
        GlassElement.DOCK -> copy(dock = on)
        GlassElement.PANELS -> copy(panels = on)
        GlassElement.SEARCH_BAR -> copy(searchBar = on)
        GlassElement.PILL -> copy(pill = on)
        GlassElement.ICON_MENU -> copy(iconMenu = on)
    }

    companion object {
        val NONE = GlassCustom()
    }
}
