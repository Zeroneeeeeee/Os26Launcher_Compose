package gambi.global.os26launcher.core.glass.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * MP (đo A17 2026-11-25): lớp nền kính cho một TRANG trượt cả khối (Thư viện, Today).
 *
 * [LayerBackdrop] của Backdrop phụ thuộc toạ độ cửa sổ: mỗi khung trang dịch `translationX`, `onGloballyPositioned` của từng bề mặt kính ghi lại
 * toạ độ (neverEqualPolicy) nên mỗi ô ghi lại lớp offscreen + blur + lens mỗi khung (atrace: ~11 lớp 439 px/khung, p50 73 ms khi vuốt Thư viện).
 * Tắt kính lúc trượt thì mượt nhưng ô đổi hẳn giao diện lúc bắt đầu / dừng (nháy, chủ dự án thấy 2026-11-25).
 *
 * Ở đây mỗi bề mặt kính ([dsGlass]) lấy mẫu nền theo vị trí LAYOUT của nó trong trang ([pinnedBackdropOrigin]), không theo vị trí trên màn:
 * trang dịch đi thì lớp kính đã ghi chỉ bị dời theo (như hardware layer của trang Launcher3), không ghi lại; cuộn / layout đổi thì ghi lại.
 * Lúc trang nằm yên (dịch 0) kết quả trùng kính thật. Trong lúc trượt nội dung nền dưới kính đi theo ô (nền đã mờ nên khó thấy).
 *
 * Điều kiện: gốc trang và lớp nền [layer] trùng vị trí khi trang nằm yên (cùng fillMaxSize trong một Box).
 * Người dùng khác ngoài [dsGlass] vẫn nhận kính thật như [layer] (lớp này uỷ quyền).
 */
@Stable
class PagePinnedBackdrop(val layer: LayerBackdrop) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    internal var origin: LayoutCoordinates? = null

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        with(layer) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

/** Đặt SAU `graphicsLayer` (dịch/co) của trang, để vị trí ô tính trong trang, không gồm phép dịch của trang. */
fun Modifier.pinnedBackdropOrigin(backdrop: PagePinnedBackdrop): Modifier = onPlaced { backdrop.origin = it }

/** Lớp nền riêng của một bề mặt kính trong trang [page]; không phụ thuộc toạ độ nên trang dịch đi không làm nó ghi lại. */
internal class PinnedTileBackdrop(private val page: PagePinnedBackdrop) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    private var coordinates: LayoutCoordinates? = null

    /** Vị trí trong trang; là state nên cuộn / layout đổi thì ô ghi lại lớp kính. */
    private var offset by mutableStateOf(Offset.Unspecified)

    fun place(c: LayoutCoordinates) {
        coordinates = c
        offset = positionInPage(c) ?: Offset.Unspecified
    }

    private fun positionInPage(c: LayoutCoordinates): Offset? {
        val o = page.origin ?: return null
        if (!o.isAttached || !c.isAttached) return null
        return o.localPositionOf(c, Offset.Zero)
    }

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        // Gốc trang có thể được đặt sau ô ở lần đầu: tính lại lúc vẽ.
        val o = offset.takeIf { it.isSpecified } ?: this@PinnedTileBackdrop.coordinates?.let { positionInPage(it) } ?: return
        translate(-o.x, -o.y) { drawLayer(page.layer.graphicsLayer) }
    }
}
