package gambi.global.os26launcher.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsRow
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs

/**
 * RS Task 3: hộp thoại dùng chung của Cài đặt. JS1: dựng bằng Compose (`DsAlertDialog` / `DsBottomSheet`) qua host hộp thoại của
 * Activity ([dsDialogs]); Activity phải vẽ `dsDialogs.Render()` trong `setContent`. Mọi hàm trả handle ĐÃ hiện; chủ sở hữu tự gắn
 * `setOnDismissListener` (gán trường về null) và dismiss ở `onDestroy` như trước.
 *
 * Khác bản View: `DsDialogHandle.dismiss()` gọi listener ngay (đồng bộ), nên mở hộp thứ hai ngay trong [singleChoice] không còn bị
 * listener của hộp đầu xóa tham chiếu.
 */
object SettingsDialogs {

    /** Chọn một: các hàng radio, chạm một hàng thì đóng rồi gọi [onChoose] với chỉ số. [checked] ngoài khoảng = không hàng nào chọn. */
    fun singleChoice(
        activity: ComponentActivity,
        title: CharSequence,
        labels: List<CharSequence>,
        checked: Int,
        onChoose: (Int) -> Unit
    ): DsDialogHandle = activity.dsDialogs.show { handle ->
        DsAlertDialog(
            onDismiss = handle::dismiss,
            title = title.toString(),
            positive = null,
            negative = activity.getString(R.string.settings_cancel) to {}
        ) {
            ScrollableHalfScreen {
                labels.forEachIndexed { i, label ->
                    DsRow(
                        title = label.toString(),
                        onClick = {
                            if (i != checked) {
                                handle.dismiss()
                                onChoose(i)
                            }
                        },
                        trailing = DsRowTrailing.Radio(i == checked),
                        showDivider = i < labels.lastIndex
                    )
                }
            }
        }
    }

    /**
     * Xác nhận: [message] tùy chọn, nút chính [positive] gọi [onPositive], nút phụ [negative] (mặc định "Hủy") gọi [onNegative].
     * [onCancel]: Back hoặc chạm ra ngoài (không bấm nút nào), như `setOnCancelListener` của bản View.
     */
    fun confirm(
        activity: ComponentActivity,
        title: CharSequence,
        message: CharSequence?,
        positive: CharSequence,
        onPositive: () -> Unit,
        negative: CharSequence? = activity.getString(R.string.settings_cancel),
        onNegative: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): DsDialogHandle = activity.dsDialogs.show { handle ->
        val acted = remember { booleanArrayOf(false) } // đã bấm một nút (không phải hủy)
        DsAlertDialog(
            onDismiss = {
                if (!acted[0]) onCancel?.invoke()
                handle.dismiss()
            },
            title = title.toString(),
            message = message?.toString(),
            positive = positive.toString() to { acted[0] = true; onPositive() },
            negative = negative?.let { it.toString() to { acted[0] = true; onNegative?.invoke(); Unit } }
        )
    }

    /**
     * Bottom sheet chọn một hành động (Figma 2.2 popup 302:2090): mỗi nhãn một hàng v3, chạm thì đóng rồi gọi [onChoose].
     * Đóng bằng nút đóng / vuốt / Back thì không gọi gì.
     */
    fun actionSheet(
        activity: ComponentActivity,
        title: CharSequence,
        labels: List<CharSequence>,
        onChoose: (Int) -> Unit
    ): DsDialogHandle = activity.dsDialogs.show { handle ->
        DsBottomSheet(onDismiss = handle::dismiss, title = title.toString()) {
            labels.forEachIndexed { i, label ->
                DsRow(
                    title = label.toString(),
                    onClick = {
                        handle.dismiss()
                        onChoose(i)
                    },
                    trailing = DsRowTrailing.Chevron,
                    showDivider = i < labels.lastIndex
                )
            }
        }
    }
}

/** Danh sách dài (vd nhiều gói icon): cao tối đa nửa màn, cuộn bên trong để nút của hộp thoại không bị đẩy khỏi màn. */
@Composable
internal fun ScrollableHalfScreen(content: @Composable () -> Unit) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height / 2).toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) { content() }
}
