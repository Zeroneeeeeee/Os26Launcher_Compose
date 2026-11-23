package gambi.global.os26launcher.ui.home.compose.today

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import gambi.global.os26launcher.domain.today.TodayWidget

/**
 * TV-3 — dữ liệu của lưới Today (phần không vẽ của `TodayGridLayout`, REF 3ab9c20): thứ tự đang hiện (khi kéo: thứ tự xem trước), widget đang
 * rời (vừa xóa), widget vừa thêm (phình ra), phiên kéo đổi chỗ. Sống ở Activity (như `TodayView`) nên danh sách nạp từ ViewModel ngay cả khi
 * Today chưa mở, và vị trí lò xo của từng widget giữ qua các lần mở/đóng.
 *
 * Chỉ đọc/ghi trên main thread.
 */
internal class TodayGridModel {

    /** Thứ tự đang hiện. Đang kéo: thứ tự xem trước (chưa ghi). */
    val entries = mutableStateListOf<TodayWidget>()

    /** Widget vừa bị xóa: vẽ tại [Leaving.at] trong lúc thu nhỏ + mờ, rồi tự gỡ khỏi danh sách này. */
    val leaving = mutableStateListOf<Leaving>()

    class Leaving(val widget: TodayWidget, val at: Offset)

    /** Widget vừa thêm gần nhất (sau lần nạp đầu): `HomeToday` cuộn cho nó lọt vào khung. */
    val lastAdded = mutableStateOf<Long?>(null)

    /** Các widget sau một widget bị xóa chỉ bắt đầu dời lên từ thời điểm này (uptime ms). */
    var slideDelayUntil = 0L
        private set

    /** Bố cục của lượt vẽ gần nhất (toạ độ nội dung khung cuộn), cho dò ô khi chạm / kéo. */
    class Layout(val ids: List<Long>, val slots: List<IntArray>, val blockH: Int, val scrollTop: Int)

    var layout: Layout? = null

    private val positions = HashMap<Long, Animatable<Offset, AnimationVector2D>>()
    private val popIn = HashSet<Long>()
    private var loadedOnce = false
    private var pending: List<TodayWidget>? = null

    // ---- kéo đổi chỗ ----
    val dragId = mutableStateOf<Long?>(null)
    private val dragTopLeftState = mutableStateOf(Offset.Zero)

    /** Góc trên-trái của widget đang kéo (toạ độ nội dung), bám ngón tay. */
    val dragTopLeft: Offset get() = dragTopLeftState.value

    /** Điểm ngón tay (toạ độ nội dung) và cao độ của nó trong khung (cho tự cuộn). */
    var dragFinger = Offset.Zero
        private set
    var fingerViewportY = 0f
        private set
    private var grab = Offset.Zero
    private var orderAtStart: List<Long> = emptyList()

    /** Widget vừa thả: `HomeToday` đặt nó tại [dropAt] rồi mới cho lò xo về ô mới. */
    var dropId: Long? = null
    var dropAt = Offset.Zero
        private set

    /** Danh sách mới từ dữ liệu (null = đang tải: bỏ qua). Đang kéo thì để sau (thả tay sẽ ghi thứ tự, dữ liệu mới về sẽ khớp). */
    fun setWidgets(list: List<TodayWidget>?) {
        if (list == null) return
        if (dragId.value != null) {
            pending = list
            return
        }
        pending = null
        if (list == entries.toList()) return
        val keep = list.mapTo(HashSet()) { it.id }
        val oldIds = entries.mapTo(HashSet()) { it.id }
        var removed = false
        entries.filter { it.id !in keep }.forEach { w ->
            val at = positions.remove(w.id)?.value ?: slotOf(w.id) ?: return@forEach
            leaving.add(Leaving(w, at))
            removed = true
        }
        if (removed) slideDelayUntil = SystemClock.uptimeMillis() + EXIT_SLIDE_DELAY_MS
        if (loadedOnce) {
            val fresh = list.filter { it.id !in oldIds }
            fresh.forEach { popIn.add(it.id) }
            fresh.lastOrNull()?.let { lastAdded.value = it.id }
        }
        loadedOnce = true
        entries.clear()
        entries.addAll(list)
    }

    /** Vị trí lò xo của widget [id]; lần đầu thấy thì đặt thẳng ở [initial] (không trượt từ góc). */
    fun positionOf(id: Long, initial: Offset): Animatable<Offset, AnimationVector2D> =
        positions.getOrPut(id) { Animatable(initial, Offset.VectorConverter) }

    /** true một lần cho widget vừa thêm (phình ra tại chỗ). */
    fun consumePopIn(id: Long): Boolean = popIn.remove(id)

    private fun slotOf(id: Long): Offset? {
        val l = layout ?: return null
        val i = l.ids.indexOf(id)
        if (i < 0) return null
        return Offset(l.slots[i][0].toFloat(), l.slots[i][1].toFloat())
    }

    /** Widget có khung chứa [pos] (toạ độ nội dung); khung nới [reach] px về phía trên-trái cho dấu "−" nhô ra góc. */
    fun entryAt(pos: Offset, reach: Float): TodayWidget? {
        val l = layout ?: return null
        val i = l.slots.indices.lastOrNull { i ->
            val s = l.slots[i]
            pos.x >= s[0] - reach && pos.x < s[0] + s[2] && pos.y >= s[1] - reach && pos.y < s[1] + l.blockH
        } ?: return null
        val id = l.ids[i]
        return entries.firstOrNull { it.id == id }
    }

    fun startDrag(w: TodayWidget, down: Offset, pos: Offset, scrollValue: Int) {
        if (dragId.value != null) return
        val topLeft = slotOf(w.id) ?: return
        grab = down - topLeft
        orderAtStart = entries.map { it.id }
        dragId.value = w.id
        onDragMove(pos, scrollValue)
    }

    /** Ngón tay ở [pos] (toạ độ nội dung): ảnh bám theo; widget khác có khung chứa ngón tay thì widget đang kéo lấy chỗ của nó. */
    fun onDragMove(pos: Offset, scrollValue: Int) {
        val id = dragId.value ?: return
        dragFinger = pos
        fingerViewportY = pos.y - scrollValue
        dragTopLeftState.value = pos - grab
        val l = layout ?: return
        val target = l.slots.indices.firstOrNull { i ->
            val s = l.slots[i]
            l.ids[i] != id && pos.x >= s[0] && pos.x < s[0] + s[2] && pos.y >= s[1] && pos.y < s[1] + l.blockH
        } ?: return
        val from = entries.indexOfFirst { it.id == id }
        val to = entries.indexOfFirst { it.id == l.ids[target] }
        if (from < 0 || to < 0 || from == to) return
        val w = entries.removeAt(from)
        entries.add(to, w)
    }

    /** Thả tay: trả thứ tự mới để ghi (null = không kéo, hoặc thứ tự không đổi so với dữ liệu); dữ liệu về trong lúc kéo được áp ngay sau. */
    fun endDrag(): List<Long>? {
        val id = dragId.value ?: return null
        dropId = id
        dropAt = dragTopLeft
        dragId.value = null
        val ids = entries.map { it.id }
        val base = pending?.map { it.id } ?: orderAtStart
        pending?.let { p ->
            pending = null
            // Giữ thứ tự vừa kéo cho tới khi dữ liệu đã ghi về (tránh nháy về thứ tự cũ).
            if (ids != p.map { it.id }) {
                val byId = p.associateBy { it.id }
                val ordered = ids.mapNotNull { byId[it] }
                setWidgets(ordered + p.filter { w -> ordered.none { it.id == w.id } })
            } else {
                setWidgets(p)
            }
        }
        return if (ids != base) ids else null
    }

    companion object {
        /** CHỈNH TAY (`TodayGridLayout.EXIT_SLIDE_DELAY_MS`): các widget sau widget bị xóa bắt đầu dời sau chừng này. */
        const val EXIT_SLIDE_DELAY_MS = 180L
    }
}
