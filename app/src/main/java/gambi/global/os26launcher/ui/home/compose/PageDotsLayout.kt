package gambi.global.os26launcher.ui.home.compose

import kotlin.math.abs
import kotlin.math.min

/**
 * M8c — phép tính vị trí hàng chấm kiểu "ổ quay số" (D10), thuần số học, không đụng android.*.
 *
 * Hàng chấm là cửa sổ [SLOTS] ô. Với `position = index + offset` (vị trí trang liên tục) và `count` trang:
 * - `count <= SLOTS`: hàng chấm đứng yên (căn giữa), chấm sáng chạy qua từng chấm.
 * - `count > SLOTS`: ở trang giữa chấm sáng luôn ở ô giữa (ô số 3), cả hàng chấm trượt theo `position`;
 *   gần trang đầu/cuối chấm sáng dịch dần về ô đầu/cuối (trang cuối = ô số 5).
 * Chấm nằm sát mép cửa sổ thu nhỏ dần rồi biến mất khi trượt ra ngoài, chỉ khi PHÍA ĐÓ còn trang ngoài cửa sổ.
 */
object PageDotsLayout {
    const val SLOTS = 5
    private const val CENTER_SLOT = 2f
    private const val EDGE_SCALE = 0.6f

    /** Trang tại ô đầu tiên của cửa sổ (số thực, đổi liên tục theo [position]). */
    fun windowStart(position: Float, count: Int): Float =
        if (count <= SLOTS) 0f else (position - CENTER_SLOT).coerceIn(0f, (count - SLOTS).toFloat())

    /** Ít trang hơn cửa sổ thì dồn nhóm chấm vào giữa. */
    fun centerShift(count: Int): Float = if (count < SLOTS) (SLOTS - count) / 2f else 0f

    /** Ô (số thực, 0 = ô đầu) của chấm trang [index]. */
    fun slotOf(index: Int, position: Float, count: Int): Float =
        index - windowStart(position, count) + centerShift(count)

    /** Ô của chấm sáng. */
    fun activeSlot(position: Float, count: Int): Float =
        position.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat()) - windowStart(
            position,
            count
        ) + centerShift(count)

    /**
     * PL: độ sáng 0..1 của chấm trang [index] ở vị trí liên tục [position]: 1 khi đúng trang, giảm tuyến tính về 0 ở khoảng cách 1 trang.
     * [eased] = true thì làm mượt hai đầu (smoothstep). [position] ngoài khoảng trang vẫn an toàn.
     */
    fun weightOf(index: Int, position: Float, eased: Boolean = false): Float {
        val w = (1f - abs(index - position)).coerceIn(0f, 1f)
        return if (eased) w * w * (3f - 2f * w) else w
    }

    /** Tỉ lệ 0..1 của chấm không sáng đang ở ô [slot]. */
    fun scaleAt(slot: Float, position: Float, count: Int): Float {
        if (count <= SLOTS) return 1f
        val w = windowStart(position, count)
        val hiddenLeft = w.coerceIn(0f, 1f) // 0 = không còn trang bên trái cửa sổ, 1 = còn
        val hiddenRight = (count - SLOTS - w).coerceIn(0f, 1f)
        val left = lerp(1f, edgeScale(slot), hiddenLeft)
        val right = lerp(1f, edgeScale((SLOTS - 1) - slot), hiddenRight)
        return min(left, right)
    }

    /** [d] = khoảng cách (theo ô) từ mép cửa sổ vào trong: ≤ -1 biến mất, 0 = ô mép (EDGE_SCALE), ≥ 1 đầy đủ. */
    private fun edgeScale(d: Float): Float = when {
        d <= -1f -> 0f
        d < 0f -> EDGE_SCALE * (d + 1f)
        d < 1f -> EDGE_SCALE + (1f - EDGE_SCALE) * d
        else -> 1f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
