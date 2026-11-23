package gambi.global.os26launcher.ui.home.controller

/**
 * SW — luật bề rộng menu nhấn giữ icon. Hàm thuần (không `android.*`), mọi kích thước bằng px.
 *
 * Rộng = `clamp(contentWidth, minWidth, maxWidth)` rồi không vượt [safeWidth] (rộng root trừ inset trừ 2 lề). Cỡ chữ nhỏ/vừa thì
 * chữ vừa trong [minWidth] nên bảng giữ đúng 200dp như launcher mẫu; cỡ chữ lớn thì bảng nở tới [maxWidth]; hàng dài hơn nữa vẫn
 * cắt "...". Màn quá hẹp (safeWidth < minWidth) thì safeWidth thắng để bảng không tràn mép.
 */
internal object IconMenuWidth {
    fun fit(contentWidth: Int, minWidth: Int, maxWidth: Int, safeWidth: Int): Int =
        contentWidth.coerceIn(minWidth, maxOf(minWidth, maxWidth)).coerceAtMost(maxOf(safeWidth, 0))
}
