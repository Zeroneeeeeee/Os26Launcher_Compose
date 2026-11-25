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
import gambi.global.os26launcher.core.glass.compose.CachedGlass
import gambi.global.os26launcher.core.perf.HomePerfLog
import gambi.global.os26launcher.core.perf.PerfSwitches
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
    modifier: Modifier = Modifier,
    /** SW Task 3: danh tính ảnh nền (đổi thì chụp lại kính dock). */
    glassKey: Any? = null,
    /** SW Task 3: true khi Home đang trượt so với ảnh nền (Today / Thư viện): kính dock vẽ sống. Nên đọc state đã gộp. */
    glassLive: () -> Boolean = { false }
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    // 2026-11-23 (iOS-style): neo kính + icon ở ĐÁY vùng dock (không còn giữa), để lề dưới card = home_dock_margin_bottom, bằng lề ngang
    // (home_dock_margin_horizontal) — trước đó căn giữa vùng dock chia khoảng trống thừa lên trên VÀ dưới, lề dưới thực tế lớn hơn lề ngang.
    Box(modifier.onSizeChanged { widthPx = it.width }, contentAlignment = Alignment.BottomCenter) {
        // 2026-11-23 (iOS-style): lề trên/dưới của hàng icon trong card cũng = X/2 (như lề dock với màn — câu hỏi trước), không còn hằng
        // GLASS_PADDING_VERTICAL cố định.
        val glassHeight = if (cells != null) with(density) { cells.iconSize.toDp() + (cells.iconSpacingX / 2f).toDp() * 2 } else null
        // Khối kính + icon đo đúng [glassHeight] rồi neo ĐÁY vùng dock (outer Box), giữ nguyên căn giữa icon trong khối này (contentAlignment
        // CenterStart như trước) — nhờ vậy icon vẫn ở giữa card theo chiều cao, chỉ khối chung tịnh tiến xuống đáy.
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (glassHeight != null) Modifier.height(glassHeight) else Modifier.fillMaxSize()),
            contentAlignment = Alignment.CenterStart
        ) {
        // SW Task 3 (C', chủ dự án 2026-11-25): dock và ảnh nền sau nó đứng yên khi lướt trang, nên kính thật vẽ MỘT lần rồi giữ thành ảnh;
        // chỉ vẽ lại khi đổi ảnh nền / thông số kính / cỡ dock, và vẽ sống trong lúc Home trượt theo Today / Thư viện. Kính tách khỏi lớp icon
        // (Box riêng) nên icon rung / kéo / huy hiệu không làm kính vẽ lại. Công tắc đo `noGlassCache` tắt ảnh chụp (bản `perf`).
        CachedGlass(
            DsSurface.CARD,
            DsCorner.Fixed(CORNER_DP, glass = true),
            cacheKey = glassKey,
            modifier = Modifier.fillMaxSize(),
            live = glassLive,
            enabled = !PerfSwitches.noGlassCache,
            onLiveDraw = if (HomePerfLog.enabled) HomePerfLog::dockGlassLiveDraw else null,
            onCaptured = if (HomePerfLog.enabled) { s -> HomePerfLog.dockGlassCaptured(s.width, s.height) } else null
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
                    // 2026-11-23 (chuẩn iOS, đồng bộ với lưới): dùng CHUNG pitch (icon + X) của lưới Home phía trên (cells.iconSpacingX) —
                    // icon dock LUÔN cùng cỡ & cùng khe với lưới, không tự chia lại gap theo chiều rộng/số app của dock (khác trước, hay lệch
                    // cỡ dock với lưới). Cả khối [count] icon được CĂN GIỮA bằng cách dịch cả khối, không đổi khe giữa icon. Khớp
                    // HomeDragState.dockSlotRects/slotFor (cùng công thức).
                    val pitch = cells.iconSize + cells.iconSpacingX
                    val groupWidth = count * cells.iconSize + (count - 1).coerceAtLeast(0) * cells.iconSpacingX
                    val startX = (widthPx - groupWidth) / 2f
                    val x = if (index < 0) 0 else (startX + index * pitch - (cells.cellWidth - cells.iconSize) / 2f).toInt()
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
}

/** PG1 A1: một ô dock kèm chỉ số (so "thay tại chỗ" theo ô). */
private data class DockSlot(val item: DockApp, val index: Int)

// Bo góc cùng số với DockView (CORNER_RADIUS_DP); "Bo góc" của kính nhân lên qua DsCorner.Fixed(glass = true).
private const val CORNER_DP = 28f
