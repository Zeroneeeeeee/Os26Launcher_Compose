package gambi.global.os26launcher.ui.home.compose

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight

private var current: DsDialogHandle? = null

/**
 * RA + JS3: hộp thoại xác nhận khi bấm dấu "−" xóa app (lưới, dock, thư mục) hoặc "Gỡ cài đặt" ở menu, kiểu thẻ sáng bo 30 với các nút viên thuốc
 * xếp dọc như iOS (bản Compose của `RemoveAppDialog` View). "Xóa ứng dụng" (gỡ cài đặt), "Xóa khỏi Màn hình chính" (ẩn khỏi Home, app vẫn ở
 * Thư viện), "Hủy". Chạm ra ngoài / Back = Hủy. Mỗi lúc một hộp; Android 12+ làm mờ phía sau (máy tắt blur cửa sổ thì hệ thống bỏ qua).
 */
internal fun showRemoveAppDialog(activity: ComponentActivity, appLabel: String, onRemoveFromHome: () -> Unit, onDeleteApp: () -> Unit) {
    showCard(
        activity,
        title = activity.getString(R.string.remove_app_title, appLabel),
        message = activity.getString(R.string.remove_app_message),
        buttons = listOf(
            CardButton(R.string.remove_app_delete, destructive = true, bold = false, action = onDeleteApp),
            CardButton(R.string.remove_app_from_home, destructive = false, bold = false, action = onRemoveFromHome),
            CardButton(R.string.remove_app_cancel, destructive = false, bold = true, action = null)
        )
    )
}

/** UI sync U3 (8ce2fdd): xác nhận xóa widget (dấu "−" trên widget) cùng kiểu thẻ với xóa app, thay hộp thoại xác nhận chung. */
/** [messageRes]: câu giải thích (TV-3: Today View dùng câu riêng, không nói "Màn hình chính"). */
internal fun showRemoveWidgetDialog(activity: ComponentActivity, messageRes: Int = R.string.widget_remove_message, onRemove: () -> Unit) {
    showCard(
        activity,
        title = activity.getString(R.string.widget_remove_title),
        message = activity.getString(messageRes),
        buttons = listOf(
            CardButton(R.string.widget_remove_confirm, destructive = true, bold = false, action = onRemove),
            CardButton(R.string.widget_remove_cancel, destructive = false, bold = true, action = null)
        )
    )
}

/** Đóng hộp đang hiện (nếu có), vd khi Activity bị hủy. */
internal fun dismissRemoveDialog() {
    current?.dismiss()
}

private class CardButton(val textRes: Int, val destructive: Boolean, val bold: Boolean, val action: (() -> Unit)?)

private fun showCard(activity: ComponentActivity, title: String, message: String, buttons: List<CardButton>) {
    current?.dismiss()
    current = activity.dsDialogs.show { handle ->
        RemoveCard(title, message, buttons, onDismiss = handle::dismiss)
    }.also { h -> h.setOnDismissListener { if (current === h) current = null } }
}

@Composable
private fun RemoveCard(title: String, message: String, buttons: List<CardButton>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        val blurPx = with(LocalDensity.current) { 24.dp.roundToPx() }
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setDimAmount(0.25f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = blurPx }
                }
            }
        }
        val text = colorResource(R.color.shortcut_menu_text)
        val destructive = colorResource(R.color.shortcut_menu_destructive)
        val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        Column(
            Modifier
                .width(minOf(screen - 64.dp, 320.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(colorResource(R.color.remove_app_card))
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                title,
                style = TextStyle(color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            BasicText(
                message,
                style = TextStyle(color = text, fontSize = 15.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 10.dp)
            )
            buttons.forEach { b ->
                PillButton(stringResource(b.textRes), if (b.destructive) destructive else text, bold = b.bold) {
                    onDismiss()
                    b.action?.invoke()
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, color: Color, bold: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(colorResource(R.color.remove_app_button))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .dsPressHighlight(interaction, darkPress = true),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            label,
            style = TextStyle(color = color, fontSize = 17.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1
        )
    }
}
