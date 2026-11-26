package gambi.global.os26launcher.ui.home.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.LocalIconGlassCorner
import gambi.global.os26launcher.core.glass.compose.iconGlassPlate
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import gambi.global.os26launcher.core.glass.compose.glassCornerScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.model.PlacedAppWidget
import gambi.global.os26launcher.domain.model.PlacedWidget
import kotlin.math.roundToInt

/**
 * JH2 Task 3 — ảnh nổi của item đang kéo (bản Compose của `DragLayer`), vẽ trên cùng `HomeScreen`. Không phải ảnh chụp: dựng lại chính nội
 * dung item (icon không nhãn, nền thư mục, widget) ở cỡ [HomeDragState.floatSize], tâm ở [HomeDragState.floatCenter]. Nhấc lên phóng 1,1 kèm
 * bóng mờ (H4, kiểu iOS); thả thì bay (lò xo SETTLE) tới ô đích, vào thư mục thì thu 0,4 và mờ hẳn.
 */
@Composable
fun HomeDragLayer(drag: HomeDragState, cells: GridMetrics.Cells?, icons: HomeIcons) {
    val item = drag.session?.item ?: drag.settlingItem ?: return
    val c = cells ?: return
    val density = LocalDensity.current
    val size = drag.floatSize
    // 2026-11-23: bóng theo thanh "Bo góc" của kính như icon / thư mục / widget (kẹp nửa cạnh ngắn).
    val cornerScale = glassCornerScale()
    // PW: widget tự vẽ chỉ vẽ thẻ vuông kiểu iOS trong vùng ô ([widgetCardFrame]): bóng theo đúng khung thẻ, không theo cả vùng.
    val card = ((item as? DragItem.Widget)?.widget as? PlacedWidget)?.let {
        widgetCardFrame(size.width.toInt(), size.height.toInt(), it.position.spanX, c.gutterX)
    }
    val frameW = card?.width?.toFloat() ?: size.width
    val frameH = card?.height?.toFloat() ?: size.height
    val baseCorner = if (item is DragItem.Widget) widgetRadius(with(density) { frameW.toDp() }, with(density) { frameH.toDp() }) else (c.iconSize * 0.225f / density.density).dp
    val corner = minOf(baseCorner * cornerScale, with(density) { (minOf(frameW, frameH) / 2f).toDp() })
    val cornerPx = with(density) { corner.toPx() }
    val shadowShape = if (card != null) {
        GenericShape { _, _ ->
            addRoundRect(RoundRect(card.left.toFloat(), card.top.toFloat(), card.right.toFloat(), card.bottom.toFloat(), CornerRadius(cornerPx)))
        }
    } else {
        RoundedCornerShape(corner)
    }
    Box(
        Modifier
            .offset {
                val center = drag.floatCenter.value
                IntOffset((center.x - size.width / 2f).roundToInt(), (center.y - size.height / 2f).roundToInt())
            }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .graphicsLayer {
                scaleX = drag.floatScale.value
                scaleY = drag.floatScale.value
                alpha = drag.floatAlpha.value
                shadowElevation = LIFT_SHADOW_DP * density.density * ((drag.floatScale.value - 1f) / 0.1f).coerceIn(0f, 1f)
                shape = shadowShape
                clip = false
            }
    ) {
        CompositionLocalProvider(LocalDsBackdrop provides drag.glassBackdrop) {
        when (item) {
            is DragItem.App -> rememberHomeIcon(icons, item.app, c.iconSize)?.let {
                // LI: icon kiểu Kính bay theo tay vẫn có kính thật phía sau (lấy mẫu drag.glassBackdrop).
                LocalIconGlassCorner.current?.let { corner -> Box(Modifier.fillMaxSize().iconGlassPlate(c.iconSize, corner)) }
                Image(it, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            is DragItem.Folder -> HomeFolderPlate(item.folder, c.iconSize, icons)
            // Widget app (kể cả "không khả dụng"): trước đây rơi qua `as? PlacedWidget` nên ảnh nổi chỉ còn bóng, widget như biến mất khi kéo.
            is DragItem.Widget -> when (val w = item.widget) {
                is PlacedWidget -> HomeWidgetCell(w, gapXPx = c.gutterX) // PW: thẻ vuông như trên lưới
                is PlacedAppWidget -> HomeAppWidgetDragPreview(w)
                else -> Unit
            }
        }
        }
    }
}

private const val LIFT_SHADOW_DP = 12f
