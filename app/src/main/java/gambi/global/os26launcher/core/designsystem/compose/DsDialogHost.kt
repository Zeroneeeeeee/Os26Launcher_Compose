package gambi.global.os26launcher.core.designsystem.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import java.util.WeakHashMap

/**
 * JS1 — một hộp thoại Compose mở từ code không phải Compose (màn Cài đặt, controller của Home). Thay `AppCompatDialog` của `DsDialog`
 * View: chủ sở hữu giữ handle, gắn [setOnDismissListener] và gọi [dismiss] ở `onDestroy` như trước.
 *
 * Khác bản View: [dismiss] gọi listener NGAY (đồng bộ), nên mở hộp thoại thứ hai ngay sau khi đóng hộp đầu không bị listener của hộp
 * đầu xóa tham chiếu hộp thứ hai (bẫy `Dialog.dismiss()` ở AGENTS.md, RS Task 3).
 */
class DsDialogHandle internal constructor(
    private val host: DsDialogHost,
    internal val content: @Composable (DsDialogHandle) -> Unit
) {
    private var listener: (() -> Unit)? = null

    /** false sau khi đã đóng. */
    var isShowing: Boolean = true
        private set

    fun setOnDismissListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    /** Đóng (bỏ khỏi cây Compose, không chạy hoạt ảnh tắt) rồi gọi listener. Gọi lại khi đã đóng thì thôi. */
    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        host.remove(this)
        listener?.invoke()
    }
}

/** JS1 — danh sách hộp thoại đang mở của một Activity; Activity vẽ chúng bằng [Render] ở cuối `setContent`. */
class DsDialogHost {
    private val entries = mutableStateListOf<DsDialogHandle>()

    /** Mở hộp thoại; [content] nhận handle để tự đóng (vd chọn một hàng rồi đóng). */
    fun show(content: @Composable (DsDialogHandle) -> Unit): DsDialogHandle =
        DsDialogHandle(this, content).also { entries += it }

    /** WR / B21: đang có ít nhất một hộp mở (hộp "Đặt làm màn hình chính" không chồng lên hộp khác). */
    val hasOpen: Boolean get() = entries.isNotEmpty()

    internal fun remove(handle: DsDialogHandle) {
        entries.remove(handle)
    }

    @Composable
    fun Render() {
        entries.forEach { handle -> key(handle) { handle.content(handle) } }
    }
}

private val hosts = WeakHashMap<ComponentActivity, DsDialogHost>()

/** Host hộp thoại của Activity này (một cho mỗi instance; Activity dựng lại thì có host mới, hộp thoại cũ mất như hộp thoại View). */
val ComponentActivity.dsDialogs: DsDialogHost
    get() = hosts.getOrPut(this) { DsDialogHost() }
