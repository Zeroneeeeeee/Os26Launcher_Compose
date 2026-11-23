package gambi.global.os26launcher.ui.home.controller

/**
 * SM — luật đặt menu nhấn giữ icon theo mẫu popup kiểu Launcher3. Hàm thuần (không `android.*`), mọi kích thước bằng px, toạ độ
 * trong `home_root`.
 *
 * - Ngang: icon ở nửa trái màn hình thì mép TRÁI menu thẳng mép trái HÌNH icon, nửa phải thì mép PHẢI thẳng mép phải hình icon;
 *   rồi kẹp trong vùng an toàn.
 * - Dọc: ưu tiên mọc dưới nhãn cách [Input.gap]; thiếu chỗ thì lật lên trên (đáy menu cách đỉnh icon [Input.gap]); cả hai thiếu thì
 *   chọn phía nhiều chỗ hơn và kẹp (có thể đè icon, chỉ khi menu cao hơn nửa vùng an toàn).
 *
 * Số đo mẫu (Oppo A3x 720x1604, 2 px/dp): ô hàng 3 cột 3, hình icon [396..500] px -> nửa phải -> menu rộng 400 px ra [100..500]
 * (mẫu đo được [98..498]).
 */
internal object IconMenuPlacement {
    data class Input(
        val rootWidth: Int,
        val rootHeight: Int,
        val insetLeft: Int,
        val insetTop: Int,
        val insetRight: Int,
        val insetBottom: Int,
        val iconLeft: Int,
        val iconTop: Int,
        val iconRight: Int,
        /** Đáy nhãn (bằng đáy icon nếu không có nhãn). */
        val contentBottom: Int,
        val menuWidth: Int,
        val menuHeight: Int,
        val gap: Int,
        val margin: Int
    )

    /** [alignEnd]: mép phải menu thẳng mép phải icon (icon ở nửa phải). [above]: menu nằm trên icon. */
    data class Result(val left: Int, val top: Int, val above: Boolean, val alignEnd: Boolean)

    fun place(i: Input): Result {
        val minX = i.insetLeft + i.margin
        val maxX = i.rootWidth - i.insetRight - i.margin - i.menuWidth
        val minY = i.insetTop + i.margin
        val maxY = i.rootHeight - i.insetBottom - i.margin - i.menuHeight

        val alignEnd = (i.iconLeft + i.iconRight) / 2 >= i.rootWidth / 2
        val wantedLeft = if (alignEnd) i.iconRight - i.menuWidth else i.iconLeft
        val left = if (maxX < minX) minX else wantedLeft.coerceIn(minX, maxX)

        val belowTop = i.contentBottom + i.gap
        val aboveTop = i.iconTop - i.gap - i.menuHeight
        return when {
            belowTop <= maxY -> Result(left, belowTop, above = false, alignEnd = alignEnd)
            aboveTop >= minY -> Result(left, aboveTop, above = true, alignEnd = alignEnd)
            else -> {
                val roomBelow = maxY + i.menuHeight - i.contentBottom
                val roomAbove = i.iconTop - minY
                val above = roomAbove > roomBelow
                val raw = if (above) aboveTop else belowTop
                val top = if (maxY < minY) minY else raw.coerceIn(minY, maxY)
                Result(left, top, above, alignEnd)
            }
        }
    }
}
