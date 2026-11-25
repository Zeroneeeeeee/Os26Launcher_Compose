package gambi.global.os26launcher.ui.home.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.PlacedWidget
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.ui.home.compose.today.TodayRecentsCard

/**
 * WF Task 1 — dữ liệu cho widget "App gần đây" trên lưới Home (cùng nguồn với thẻ của Today: `HomeViewModel.todayApps`). `HomeActivity` cung cấp qua
 * [LocalHomeRecents] quanh `HomeScreen` và lớp ảnh kéo, để `HomeWidgetCell` không phải đổi chữ ký ở mọi chỗ gọi. [apps] null = đang tải.
 */
@Immutable
internal class HomeRecents(
    val apps: List<AppItem>?,
    val icons: HomeIcons,
    val showLabels: Boolean,
    val onAppClick: (AppItem, ScreenRect?) -> Unit
)

/** null = không có nguồn (thẻ vẽ trống, không bấm được). */
internal val LocalHomeRecents = compositionLocalOf<HomeRecents?> { null }

/**
 * WF Task 1 — ô "App gần đây" trên lưới Home: chính [TodayRecentsCard] (kính thật CARD, 4 app ở 2 × 2, 8 app ở 4 × 2). [interactive] false (edit mode,
 * ảnh kéo): ô app không bấm được, mọi chạm đi tiếp cho cử chỉ của lưới (nhấn giữ để kéo, như widget khác).
 */
@Composable
internal fun HomeRecentsWidget(widget: PlacedWidget, interactive: Boolean) {
    val source = LocalHomeRecents.current ?: return
    TodayRecentsCard(
        columns = if (widget.position.spanX >= 4) 4 else 2,
        apps = source.apps,
        icons = source.icons,
        showLabels = source.showLabels,
        interactive = interactive,
        onAppClick = source.onAppClick
    )
}
