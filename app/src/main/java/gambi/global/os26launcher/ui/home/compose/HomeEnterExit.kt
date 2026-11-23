package gambi.global.os26launcher.ui.home.compose

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * PG1 A1 — hoạt ảnh khi danh sách item của MỘT vùng (một trang lưới, dock, bảng thư mục) đổi không qua kéo-thả. Bản Compose của AN Task 9d
 * ở repo XML (`IconGridView.planEnterExit`, `LeavingIcons`, `IconCellView.popIn`):
 * - item biến mất (gỡ, ẩn, bỏ khỏi Home) được vẽ tiếp ở chỗ cũ và thu về 0 quanh tâm icon rồi mất; các item sau trượt SAU [EXIT_SLIDE_DELAY_MS];
 * - item mới xuất hiện (cài app, bỏ ẩn) phình từ 0;
 * - "thay tại chỗ" (thư mục giải tán: con thế vào ô thư mục; tạo thư mục: thư mục thế vào ô đích) không thu rồi phình: so theo [slotOf].
 *
 * Không chạy khi: lần dựng đầu của vùng, [update] với `animate = false` (đổi cỡ lưới, đang kéo hoặc ảnh kéo đang hạ cánh: lúc đó chính ảnh kéo
 * là hoạt ảnh — app kéo vào thư mục co vào ô nhỏ của nó, QĐ 7 của plan PG1), trang trống nhận icon đầu tiên (chỉ widget đầu tiên mới phình,
 * G2c Task 13). Gọi [update] ở đầu hàm Composable của vùng, TRƯỚC khi vẽ item; idempotent theo identity của danh sách.
 */
internal class EnterExitTracker<T : Any>(
    private val idOf: (T) -> Long,
    private val slotOf: (T) -> Any,
    private val isWidget: (T) -> Boolean = { false }
) {
    private var last: List<T>? = null

    /** Item đang thu (vẽ ở chỗ cũ). Đọc ngay sau [update] trong cùng lần dựng; lần dựng lại do [leaveDone] đọc bản mới. */
    var leaving: List<T> = emptyList()
        private set

    /** id item vừa xuất hiện, phình từ 0 ở lần dựng đầu của chính nó. */
    var growing: Set<Long> = emptySet()
        private set

    /** Mốc (uptime ms) trước đó các item khác chờ rồi mới trượt (để icon rời đi thu xong trước). */
    var slideDelayUntil = 0L
        private set

    private var version by mutableIntStateOf(0)

    fun update(items: List<T>, animate: Boolean) {
        version // đọc để lần dựng lại sau [leaveDone] đi qua đây
        if (items === last) return
        val prev = last
        last = items
        if (prev == null || !animate) return
        val newIds = items.mapTo(HashSet(), idOf)
        val oldIds = prev.mapTo(HashSet(), idOf)
        val addedAll = items.filter { idOf(it) !in oldIds }
        val removedAll = prev.filter { idOf(it) !in newIds }
        val addedSlots = addedAll.mapTo(HashSet(), slotOf)
        val removedSlots = removedAll.mapTo(HashSet(), slotOf)
        val leave = removedAll.filter { slotOf(it) !in addedSlots }
        // Vùng trống nhận item đầu tiên: chỉ phình khi đều là widget (nạp ban đầu / bind trang không phình cả lưới).
        val grow = if (prev.isEmpty() && addedAll.any { !isWidget(it) }) emptyList()
        else addedAll.filter { slotOf(it) !in removedSlots }
        if (leave.isNotEmpty()) {
            leaving = leaving.filter { l -> leave.none { idOf(it) == idOf(l) } } + leave
            slideDelayUntil = SystemClock.uptimeMillis() + EXIT_SLIDE_DELAY_MS
        }
        if (grow.isNotEmpty()) growing = growing + grow.map(idOf)
    }

    fun leaveDone(item: T) {
        leaving = leaving.filter { idOf(it) != idOf(item) }
        version++
    }

    fun growDone(id: Long) {
        growing = growing - id
    }

    companion object {
        /** AN Task 9d — HẰNG CHỈNH TAY (như `IconGridView.EXIT_SLIDE_DELAY_MS`): item sau trượt sau chừng này ms kể từ lúc icon rời đi bắt đầu thu. */
        const val EXIT_SLIDE_DELAY_MS = 180L
    }
}

/** `SpringProgress.Spec.EXIT` (900 / 1.0): icon rời đi thu về 0. */
internal val ExitSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.01f)

/** `SpringProgress.Spec.HOVER` (900 / 0.6): app mới phình từ 0 (như `IconCellView.popIn`). */
internal val PopInSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 900f, visibilityThreshold = 0.01f)

/**
 * PG1 A1 — tỉ lệ thu/phình quanh [pivotY] (phần của chiều cao ô: tâm icon cho app/thư mục, 0.5 cho widget). [start] 0 = phình từ 0 tới 1,
 * 1 = thu từ 1 về 0; xong thì gọi [onDone] một lần.
 */
@Composable
internal fun Modifier.popScale(start: Float, pivotY: Float, onDone: () -> Unit): Modifier {
    val scale = remember { Animatable(start) }
    LaunchedEffect(Unit) {
        scale.animateTo(if (start < 0.5f) 1f else 0f, if (start < 0.5f) PopInSpring else ExitSpring)
        onDone()
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin(0.5f, pivotY)
    }
}

/**
 * PG1 A1 — vị trí có hoạt ảnh của một item (khoá theo `key(itemId)` của nơi gọi), bản Compose của FLIP ở AN Task 8 (`IconGridView`): khi đích
 * [target] (ô thật, hoặc ô xem trước đẩy-dồn lúc đang kéo) đổi giữa hai lần dựng, item trượt từ chỗ đang thấy tới đích mới, kể cả khi đổi do dữ
 * liệu (thả nhanh lên ô có icon khác trước khi kịp xem trước, gỡ/ẩn rồi dồn ô, bỏ ẩn, dọn trang trống). Thả sau khi đã xem trước: đích mới trùng
 * ô xem trước nên không có bước nhảy. Trả độ dời so với [base] (chỗ layout đặt item) để đọc trong pha layout, hoặc null khi đứng yên (không cấp
 * `Animatable` cho item đứng yên, giữ nhẹ khi cuộn trang).
 *
 * [snap]: đặt thẳng (item đang kéo / đang hạ cánh, hoặc đổi cỡ lưới — đổi [geometryKey]). [previewing]: lò xo REORDER (300 / 0.85) như xem
 * trước; còn lại SLIDE (800 / 0.8) như dock và bảng thư mục. [delayUntil]: chờ icon rời đi thu xong (chỉ khi không xem trước).
 */
@Composable
internal fun rememberSlideOffset(
    base: IntOffset,
    target: IntOffset,
    geometryKey: Any,
    snap: Boolean,
    previewing: Boolean,
    delayUntil: () -> Long
): (() -> IntOffset)? {
    val holder = remember { SlideHolder() }
    val last = holder.lastTarget
    val sameGeometry = holder.geometryKey == geometryKey
    holder.lastTarget = target
    holder.geometryKey = geometryKey
    // Bắt đầu trượt: lúc dựng chỉ ghi chỗ xuất phát (chỗ đang thấy). `Animatable` tạo trong LaunchedEffect bên dưới, không tạo lúc dựng
    // (lint RememberInComposition: lần dựng có thể chạy lại hoặc bị bỏ). Khung đầu, trước khi effect chạy, vẽ ở chỗ xuất phát nên không giật.
    if (last != null && last != target && sameGeometry && !snap && holder.anim == null && holder.from == null) {
        holder.from = last
    }
    // Đứng yên: không cần Animatable; vẫn dời tĩnh tới ô xem trước nếu đích khác chỗ layout (xem trước đẩy-dồn đang hiện).
    if (holder.anim == null && holder.from == null) return if (target == base) null else ({ target - base })
    LaunchedEffect(holder, target, snap, sameGeometry) {
        val anim = holder.anim ?: Animatable(holder.from ?: target, IntOffset.VectorConverter).also { holder.anim = it }
        holder.from = null
        if (snap || !sameGeometry) {
            anim.snapTo(target)
        } else {
            val wait = delayUntil() - SystemClock.uptimeMillis()
            if (!previewing && wait > 0) delay(wait)
            anim.animateTo(target, if (previewing) ReorderSpring else SlideSpring)
        }
        if (holder.anim === anim && holder.lastTarget == target) holder.anim = null
    }
    // Đọc trong pha layout: `anim` là state nên gán / bỏ trong effect làm layout chạy lại; xong thì về đúng đích (không nhảy).
    return { (holder.anim?.value ?: holder.from ?: target) - base }
}

private class SlideHolder {
    var lastTarget: IntOffset? = null
    var geometryKey: Any? = null

    /** Chỗ xuất phát của lần trượt sắp chạy (ghi lúc dựng, effect lấy rồi xoá). */
    var from: IntOffset? = null

    /** Hoạt ảnh đang chạy; state để hàm dời đọc trong pha layout thấy khi effect gán / bỏ. */
    var anim by mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null)
}
