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
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(CORNER_DP))
        )
        // PG1 A1: icon rời dock không do kéo (dấu "−", ẩn) thu về 0 tại chỗ cũ; app mới vào dock không do kéo thì phình (AN Task 9d, DockView).
        // "Thay tại chỗ" (thư mục ở dock giải tán còn một app) so theo chỉ số ô.
        val tracker = remember { EnterExitTracker<DockSlot>(idOf = { it.item.itemId }, slotOf = { it.index }) }
        val lastX = remember { HashMap<Long, Int>() }
        tracker.update(dock.mapIndexed { i, item -> DockSlot(item, i) }, animate = edit.drag.session == null && edit.drag.settlingId == null)
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
                    lastX[item.itemId] = x
                    val grow = item.itemId in tracker.growing
                    val cellModifier = Modifier
                        .offset { animatedX }
                        .width(with(density) { cells.cellWidth.toDp() })
                        .then(if (grow) Modifier.popScale(0f, 0.5f) { tracker.growDone(item.itemId) } else Modifier)
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
                                hiddenChildId = edit.drag.session?.item?.itemId ?: edit.drag.settlingId, // P2-6: app con đang kéo ra khỏi bảng; PG1 A1: app đang hạ cánh vào ô nhỏ
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
            // PG1 A1: icon vừa rời dock, vẽ ở ô cũ và thu về 0.
            tracker.leaving.forEach { slot ->
                val id = slot.item.itemId
                key("leaving", id) {
                    val x = lastX[id] ?: 0
                    val leaveModifier = Modifier
                        .offset { IntOffset(x, 0) }
                        .width(with(density) { cells.cellWidth.toDp() })
                        .popScale(1f, 0.5f) {
                            tracker.leaveDone(slot)
                            lastX.remove(id)
                        }
                    val folder = slot.item.folder
                    val app = slot.item.app
                    if (folder != null) {
                        Box(leaveModifier, propagateMinConstraints = true) {
                            HomeFolderCell(folder, dockCells, icons, zoomed = false, badges = edit.badges, hiddenChildId = null, onOpen = {})
                        }
                    } else if (app != null) {
                        HomeAppCell(app = app, cells = dockCells, icons = icons, modifier = leaveModifier, editMode = edit.editMode, badges = edit.badges, onRemove = {}, onClick = { _, _ -> })
                    }
                }
            }
        }
    }
}

/** PG1 A1: một ô dock kèm chỉ số (so "thay tại chỗ" theo ô). */
private data class DockSlot(val item: DockApp, val index: Int)

// Cùng số với DockView (CORNER_RADIUS_DP, GLASS_PADDING_VERTICAL_DP).
private const val CORNER_DP = 28f
private val GLASS_PADDING_VERTICAL = 20.dp
