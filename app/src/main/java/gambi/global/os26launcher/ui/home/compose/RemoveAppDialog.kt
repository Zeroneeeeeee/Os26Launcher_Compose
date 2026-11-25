package gambi.global.os26launcher.ui.home.compose

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.glass.compose.glassCornerScale

/** RA2 (2026-11-24, chủ dự án "muốn liquid glass thật"): yêu cầu thẻ xác nhận đang chờ hiện — [RemoveAppOverlay] (trong cây Compose, lấy mẫu một
 * lớp backdrop) đọc giá trị này, KHÔNG mở `Dialog` cửa sổ riêng (chỉ làm mờ cửa sổ hệ thống, không khúc xạ / kính thật được). PD (2026-11-25):
 * mọi thẻ của lưới chính, Thư viện, Today đều đi đường này. */
private var removeAppRequest by mutableStateOf<RemoveAppRequest?>(null)

/** RA3 (2026-11-24): tiến độ hiện/ẩn [RemoveAppOverlay] (0..1, lò xo mở 700/0,9 - đóng 1400/1,0, như [HomeFolderPanel]'s folderProgress). */
private var removeOverlayProgress by mutableFloatStateOf(0f)

/**
 * PD (2026-11-25): thẻ đang hiện (kể cả lúc chạy hoạt ảnh đóng). `HomeActivity` đọc để ghi lớp kính cho thẻ và để `dispatchTouchEvent` bỏ qua bộ
 * vuốt Thư viện / Today (thẻ chặn MỌI tương tác phía dưới). Đọc trạng thái snapshot nên composition đọc nó tự cập nhật.
 */
internal val isRemoveDialogShown: Boolean get() = removeAppRequest != null || removeOverlayProgress > 0f

internal class RemoveAppRequest(val title: String, val message: String, val buttons: List<CardButton>)

/**
 * RA + JS3: hộp thoại xác nhận khi bấm dấu "−" xóa app (lưới, dock, thư mục) kiểu thẻ sáng bo 30 với các nút viên thuốc xếp dọc như iOS.
 * "Xóa ứng dụng" (gỡ cài đặt), "Xóa khỏi Màn hình chính" (ẩn khỏi Home, app vẫn ở Thư viện), "Hủy". Chạm ra ngoài / Back = Hủy.
 *
 * RA2 (2026-11-24): vẽ NGAY TRONG cây Compose ([RemoveAppOverlay]) để có khúc xạ / kính thật, không chỉ làm mờ cửa sổ hệ thống. PD (2026-11-25):
 * cùng một đường cho [showRemoveWidgetDialog] (Home, Today) và [showUninstallAppDialog] (Thư viện).
 */
internal fun showRemoveAppDialog(activity: ComponentActivity, appLabel: String, onRemoveFromHome: () -> Unit, onDeleteApp: () -> Unit) {
    removeAppRequest = RemoveAppRequest(
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
/** PD (2026-11-25): Home và Today cùng vẽ bằng [RemoveAppOverlay] (bỏ tham số `inHome` của RA3, trước Today dùng `Dialog` kiểu cũ). */
internal fun showRemoveWidgetDialog(
    activity: ComponentActivity,
    messageRes: Int = R.string.widget_remove_message,
    onRemove: () -> Unit
) {
    val title = activity.getString(R.string.widget_remove_title)
    val message = activity.getString(messageRes)
    val buttons = listOf(
        CardButton(R.string.widget_remove_confirm, destructive = true, bold = false, action = onRemove),
        CardButton(R.string.widget_remove_cancel, destructive = false, bold = true, action = null)
    )
    removeAppRequest = RemoveAppRequest(title, message, buttons)
}

/** LD Task 2: dấu "−" trên app ở chế độ sửa của Library: chỉ hỏi gỡ cài đặt (không có "Xóa khỏi MH chính"), cùng kiểu thẻ với xóa app. */
internal fun showUninstallAppDialog(activity: ComponentActivity, appLabel: String, onDeleteApp: () -> Unit) {
    removeAppRequest = RemoveAppRequest(
        title = activity.getString(R.string.remove_app_title, appLabel),
        message = activity.getString(R.string.library_uninstall_message),
        buttons = listOf(
            CardButton(R.string.remove_app_delete, destructive = true, bold = false, action = onDeleteApp),
            CardButton(R.string.remove_app_cancel, destructive = false, bold = true, action = null)
        )
    )
}

/** Đóng hộp đang hiện (nếu có), vd khi Activity bị hủy. */
internal fun dismissRemoveDialog() {
    removeAppRequest = null
}

internal class CardButton(val textRes: Int, val destructive: Boolean, val bold: Boolean, val action: (() -> Unit)?)

/**
 * RA2 (2026-11-24) — bản TRONG CÂY của các thẻ xác nhận. Thẻ kính thật căn giữa màn, chạm ra ngoài → Hủy — như [HomeIconMenu.MenuCard] nhưng
 * không neo theo icon.
 *
 * PD (2026-11-25): vẽ ở `HomeActivity`, là lớp TRÊN CÙNG (trên Home, Today, Thư viện, ảnh kéo, bảng chọn widget), [backdrop] = lớp Home + Today +
 * Thư viện chưa nhoè (`searchBackdrop`). Trước đây nằm trong `Box` gốc của `HomeScreen`, dưới `homeGestures` (pass Initial) nên chạm ngoài thẻ vừa
 * đóng thẻ vừa thoát edit mode / nhấn giữ icon. Giờ: chạm ngoài thẻ CHỈ đóng thẻ; chạm trong thẻ không trúng nút: không gì; Back đóng thẻ trước mọi
 * lớp khác. Tự vẽ lớp tối [OVERLAY_DIM] (RA4: chỉ tối, không nhoè) nên ba màn giống nhau.
 */
@Composable
internal fun RemoveAppOverlay(backdrop: Backdrop?) {
    val req = removeAppRequest
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(req) }
    // Ghi removeOverlayProgress TỪNG KHUNG trong khối animateTo: trước đây ghi bằng SideEffect, mà composable này không đọc progress.value lúc
    // compose nên không recompose theo hoạt ảnh → giá trị kẹt ở ~0, lớp Home phía sau không hề mờ.
    LaunchedEffect(req) {
        if (req != null) {
            shown = req
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 700f)) { removeOverlayProgress = value }
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1400f)) { removeOverlayProgress = value }
            removeOverlayProgress = 0f
            shown = null
        }
    }
    // Đăng ký lại mỗi lần một thẻ hiện: callback thêm SAU được hỏi trước, nên thắng cả BackHandler của bảng thư mục / bảng chọn mở sau lúc khởi động.
    if (req != null) androidx.compose.runtime.key(req) { BackHandler { removeAppRequest = null } }
    val r = shown ?: return
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
            .drawBehind { drawRect(Color.Black.copy(alpha = OVERLAY_DIM)) }
            .clickable(interactionSource = interaction, indication = null) { removeAppRequest = null },
        contentAlignment = Alignment.Center
    ) {
        RemoveCardContent(r.title, r.message, r.buttons, backdrop, onDismiss = { removeAppRequest = null })
    }
}

@Composable
private fun RemoveCardContent(
    title: String,
    message: String,
    buttons: List<CardButton>,
    backdrop: Backdrop?,
    onDismiss: () -> Unit
) {
    // RA4: nền kính tối đậm + chữ trắng (màu riêng `remove_app_glass_*`), không dùng tint sáng của menu — chủ dự án thấy khó đọc chữ.
    val text = colorResource(R.color.remove_app_glass_text)
    val destructive = colorResource(R.color.remove_app_glass_destructive)
    val buttonFill = colorResource(R.color.remove_app_glass_button)
    val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val corner = (CARD_RADIUS * glassCornerScale()).coerceAtMost(CARD_RADIUS * 2)
    val innerInteraction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(minOf(screen - 64.dp, 320.dp))
            // RA2: kính THẬT lấy mẫu lớp Home ([backdrop]), không còn `DialogWindowGlass` (làm mờ cửa sổ hệ thống, không khúc xạ). Mờ CỐ ĐỊNH
            // như menu nhấn giữ / panel thư mục: luôn đọc được chữ dù thanh "Độ mờ" kính đặt thấp.
            .dsGlass(DsSurface.CARD, DsCorner.Fixed(corner.value, glass = true), backdrop, fixedBlurDp = CARD_BLUR_DP, surfaceFill = false)
            .background(colorResource(R.color.remove_app_glass_tint))
            .clickable(interactionSource = innerInteraction, indication = null) {} // chạm trong thẻ không đóng
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
            PillButton(stringResource(b.textRes), if (b.destructive) destructive else text, bold = b.bold, fill = buttonFill) {
                onDismiss()
                b.action?.invoke()
            }
        }
    }
}

@Composable
private fun PillButton(label: String, color: Color, bold: Boolean, fill: Color? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(fill ?: colorResource(R.color.remove_app_button))
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

private val CARD_RADIUS = 30.dp
/** PD: lớp tối phía sau thẻ, bằng `FOLDER_DIM` của `HomeScreen` mà RA4 dùng cho thẻ (chỉ tối, không nhoè). */
private const val OVERLAY_DIM = 0.15f

/** RA2 (2026-11-24): độ mờ CỐ ĐỊNH của thẻ xóa app trong cây Compose (như [HomeIconMenu]'s MENU_BLUR_DP / panel thư mục FOLDER_MIN_BLUR_DP). */
private const val CARD_BLUR_DP = 18f
