package gambi.global.os26launcher.ui.home.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * JH2 — cử chỉ của Home Compose, đặt ở GỐC `HomeScreen` và đọc ở `PointerEventPass.Initial` (trước pager và các ô):
 *
 * - Chạm rồi nhấc nhanh trên vùng trống của lưới khi đang ở edit mode → thoát edit mode (M7 Task 4). Chạm thường khác: không đụng tới, để ô
 *   và pager tự xử lý.
 * - Giữ quá `longPressTimeout` mà chưa đi quá touch slop, trên ô hoặc vùng trống (lưới, và P2-5: phần ngoài lưới — hai bên pill, quanh dock,
 *   dải trên lưới; xem [HomeDragState.hitTest]) → rung, vào edit mode (JH2 D1). Từ đây mọi sự kiện
 *   của cử chỉ bị tiêu thụ ở Initial: ô không nhận cú bấm khi nhấc tay, pager không cuộn.
 * - Sau đó đi quá touch slop từ chỗ chạm, trên một item → bắt đầu kéo (D2); mỗi lần di → [HomeDragState.move]; nhấc ngón kéo → thả
 *   (C18: theo pointer id, ngón thứ hai bị bỏ qua); cử chỉ bị huỷ → huỷ kéo (G2c13).
 *
 * Đặt ở gốc vì trang nguồn có thể bị pager gỡ khỏi composition khi lật mép hai trang: `pointerInput` của ô sẽ bị huỷ giữa chừng (lý do
 * `HomeTouchRouter` của View giữ chạm ở Activity, Task 7).
 */
/**
 * JH3a D3 — vuốt xuống mở tìm kiếm (`SearchOverlayController` + `SearchSwipeDetector`): chạm xuống trong [startZone] (dưới thanh trạng thái
 * + 48 dp, trên đỉnh dock), đi xuống ≥ 64 dp với dy ≥ 2·|dx|. [enabled] đọc lúc chạm xuống (công tắc, edit mode, đang kéo, tìm kiếm đã mở).
 */
class HomeSearchSwipe(
    val enabled: () -> Boolean,
    val startZone: () -> ClosedFloatingPointRange<Float>,
    val onTrigger: () -> Unit
)

fun Modifier.homeGestures(
    drag: HomeDragState,
    isEditMode: () -> Boolean,
    haptic: HapticFeedback,
    search: HomeSearchSwipe? = null
): Modifier =
    pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (drag.session != null) drag.cancel() // phiên cũ còn sót (DOWN mới) → huỷ
            val hit = drag.hitTest(down.position)
            // PW (chủ dự án 2026-11-25, như app mẫu): menu nhấn giữ đang mở thì cử chỉ MỚI thuộc về menu, không về lưới. Cha này xét ở pass Initial
            // nên thấy cả chạm trên lớp phủ của menu: trước đây nhấn giữ icon khác (kể cả icon nằm dưới thẻ menu) mở menu mới. Giờ bỏ qua hết để
            // lớp phủ tự xử lý (chạm ngoài = đóng menu, chạm trong = hàng của menu); ngoại lệ (giữ như iOS): nhấn giữ lại CHÍNH icon đang có menu
            // → "Sửa Màn hình chính" (nhánh `onMenuEditHome` bên dưới), giữ rồi kéo tiếp thì kéo icon đó.
            drag.menuOwner?.let { owner ->
                val onOwner = hit is HomeHit.Item && hit.item is DragItem.App && hit.container <= 0L &&
                    owner.first == hit.item.itemId && owner.second == hit.container
                if (!onOwner) return@awaitEachGesture
            }
            val slop = viewConfiguration.touchSlop
            var moved = false
            var lifted = false
            val waited = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        lifted = true
                        break
                    }
                    if ((change.position - down.position).getDistance() > slop) {
                        moved = true
                        break
                    }
                }
            }
            if (waited != null) {
                if (lifted && !moved && hit == HomeHit.Gap && isEditMode()) drag.actions.exitEditMode()
                // P2-11: chạm bắt đầu trên pill dạng chấm là cử chỉ của pill, không phải vuốt mở tìm kiếm.
                if (moved && search != null && search.enabled() && down.position.y in search.startZone() && !drag.pillClaims(down.position)) {
                    trackSearchSwipe(down.id, down.position, search)
                }
                return@awaitEachGesture
            }
            if (hit == HomeHit.Outside) return@awaitEachGesture
            if (hit is HomeHit.Gap && (isEditMode() || drag.menuOwner != null)) {
                // GP / P2-5 (HomePagerController.onGapLongPress): đã ở edit mode hoặc menu đang mở thì nhấn giữ vùng trống không làm gì (không
                // rung, không nuốt chạm); thả tay mà không trôi ở edit mode vẫn là chạm → thoát edit mode, như View.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > slop) break
                    if (!change.pressed) {
                        if (isEditMode()) drag.actions.exitEditMode()
                        break
                    }
                }
                return@awaitEachGesture
            }

            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!isEditMode()) {
                // JH3b D1/D2: app → menu (hoặc "Sửa Màn hình chính" nếu đang giữ chính icon có menu); thư mục, widget, vùng trống → edit mode.
                // Vùng trống không vào edit mode khi menu đang mở (GapLongPressGesture: chỉ khi không có menu).
                val owner = drag.menuOwner
                when {
                    hit is HomeHit.Item && hit.item is DragItem.App -> {
                        val menuItem = if (hit.container > 0L) -1L else hit.item.itemId
                        if (owner != null && owner.first == menuItem && owner.second == hit.container && hit.container <= 0L) {
                            drag.actions.onMenuEditHome()
                        } else {
                            drag.actions.onIconLongPressed(menuItem, hit.container, hit.item.app.key, hit.rect)
                        }
                    }
                    else -> drag.actions.enterEditMode()
                }
            }
            var dropped = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || !change.pressed) {
                        if (drag.session != null) drag.drop()
                        dropped = true
                        break
                    }
                    if (drag.session == null) {
                        if (hit is HomeHit.Item && (change.position - down.position).getDistance() > slop) {
                            drag.start(hit, change.position, down.id)
                            drag.move(change.position)
                        }
                    } else {
                        drag.move(change.position)
                    }
                }
            } finally {
                if (!dropped && drag.session != null) drag.cancel()
            }
            // C18: ngón kéo đã nhấc mà còn ngón khác đặt trên màn: nuốt nốt tới khi nhấc hết.
            while (currentEvent.changes.any { it.pressed }) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

/**
 * Theo tiếp chuỗi chạm đã đi quá touch slop: còn đúng hướng xuống (dy ≥ 2·|dx|) thì chờ đủ 64 dp rồi mở tìm kiếm và nuốt phần còn lại (ô và
 * pager không nhận); lệch ngang hoặc đi lên thì bỏ, chạm thuộc về pager như thường.
 */
private suspend fun AwaitPointerEventScope.trackSearchSwipe(id: PointerId, start: Offset, search: HomeSearchSwipe) {
    val trigger = SEARCH_TRIGGER_DP * density
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        val d = change.position - start
        if (d.y <= 0f || d.y < 2f * abs(d.x)) return
        if (d.y >= trigger) {
            event.changes.forEach { it.consume() }
            search.onTrigger()
            while (currentEvent.changes.any { it.pressed }) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
            return
        }
    }
}

private const val SEARCH_TRIGGER_DP = 64f
