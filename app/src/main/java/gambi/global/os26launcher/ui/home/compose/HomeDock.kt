package gambi.global.os26launcher.ui.home.compose

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.ScreenRect

/**
 * JH1 Task 4 — dock (bản Compose của `DockView`). Kính thật CARD (JH1 D4, H1) rộng hết vùng dock, cao `icon + 2 × 20 dp` (không vượt vùng dock),
 * bo 28 dp, căn giữa theo chiều dọc. Icon cùng cỡ icon lưới ([cells] đo từ vùng pager, ép không nhãn), chia đều khoảng trống (space-evenly)
 * bất kể 0–4 icon; dock trống vẫn là viên kính rỗng (A10).
 *
 * JH2: vị trí ô tự tính (không dùng `Row`) để xem trước khi kéo: [HomeDragState.dockPreview] là thứ tự mới có một chỗ trống, mỗi icon trượt
 * (lò xo SLIDE) tới ô của nó trong thứ tự đó (`DockView` 422-460). Icon đang kéo bị ẩn.
 */
@Composable
internal fun HomeDock(
    dock: List<DockApp>,
    cells: GridMetrics.Cells?,
    icons: HomeIcons,
    edit: HomeEditContext,
    onAppClick: (AppItem, ScreenRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { widthPx = it.width }, contentAlignment = Alignment.CenterStart) {
        val glassHeight = if (cells != null) with(density) { cells.iconSize.toDp() } + GLASS_PADDING_VERTICAL * 2 else null
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (glassHeight != null) Modifier.height(glassHeight) else Modifier.fillMaxSize())
                .align(Alignment.Center)
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(CORNER_DP, glass = true))
        )
        if (cells != null && widthPx > 0) {
            val dockCells = cells.copy(showLabels = false)
            val order = edit.drag.dockPreview ?: dock.map { it.itemId }
            dock.forEach { item ->
                key(item.itemId) {
                    val index = order.indexOf(item.itemId)
                    val count = order.size
                    val gap = (widthPx - count * cells.cellWidth) / (count + 1f)
                    val x = if (index < 0) 0 else (gap + index * (cells.cellWidth + gap)).toInt()
                    val animatedX by animateIntOffsetAsState(
                        IntOffset(x, 0),
                        ReorderSpring, // FM-3: cùng tốc độ với lưới Home và bảng thư mục
                        label = "dockSlot"
                    )
                    val hidden = index < 0 || edit.drag.isHidden(item.itemId)
                    val merge = edit.drag.mergeTargetId == item.itemId
                    val cellModifier = Modifier
                        .offset { animatedX }
                        .width(with(density) { cells.cellWidth.toDp() })
                        .graphicsLayer { alpha = if (hidden) 0f else 1f }
                        .homeJiggle(edit.editMode && !hidden)
                    val folder = item.folder
                    val app = item.app
                    if (folder != null) {
                        Box(cellModifier, propagateMinConstraints = true) { // ô thư mục rộng đúng ô dock: badge đúng góc icon
                            HomeFolderCell(
                                folder, dockCells, icons,
                                zoomed = merge && folder.appCount < FolderPlanner.MAX_ITEMS,
                                badges = edit.badges,
                                hiddenChildId = edit.drag.session?.item?.itemId, // P2-6: app con đang kéo ra khỏi bảng
                                onOpen = { edit.onOpenFolder(item.itemId) }
                            )
                        }
                    } else if (app != null) {
                        HomeAppCell(
                            app = app,
                            cells = dockCells,
                            icons = icons,
                            modifier = cellModifier,
                            editMode = edit.editMode,
                            badges = edit.badges,
                            hoverPlate = merge,
                            onRemove = { edit.onRemoveApp(item.itemId) },
                            onClick = onAppClick
                        )
                    }
                }
            }
        }
    }
}

// Cùng số với DockView (CORNER_RADIUS_DP, GLASS_PADDING_VERTICAL_DP).
private const val CORNER_DP = 28f
private val GLASS_PADDING_VERTICAL = 20.dp
