package gambi.global.os26launcher.ui.home.compose

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import com.kyant.backdrop.Backdrop
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.glass.compose.DialogWindowGlass
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.glass.compose.glassCornerScale
import gambi.global.os26launcher.core.glass.compose.glassTintOf

private var current: DsDialogHandle? = null

/** RA2 (2026-11-24, chủ dự án "muốn liquid glass thật"): yêu cầu thẻ xóa app đang chờ hiện — [RemoveAppOverlay] (trong cây Compose của
 * HomeScreen, lấy mẫu [homeBackdrop] như [HomeIconMenu]) đọc giá trị này, KHÔNG còn mở `Dialog` cửa sổ riêng (chỉ làm mờ cửa sổ hệ thống,
 * không khúc xạ/kính thật được vì không có backdrop của Compose để lấy mẫu). */
private var removeAppRequest by mutableStateOf<RemoveAppRequest?>(null)

/** RA3 (2026-11-24): tiến độ hiện/ẩn [RemoveAppOverlay] (0..1, lò xo mở 700/0,9 - đóng 1400/1,0, như [HomeFolderPanel]'s folderProgress) —
 * HomeScreen đọc để blur + tối lớp Home phía sau giống hệt lúc mở thư mục (không chỉ tối như menu nhấn giữ icon). */
internal var removeOverlayProgress by mutableFloatStateOf(0f)

internal class RemoveAppRequest(val title: String, val message: String, val buttons: List<CardButton>)

/**
 * RA + JS3: hộp thoại xác nhận khi bấm dấu "−" xóa app (lưới, dock, thư mục) kiểu thẻ sáng bo 30 với các nút viên thuốc xếp dọc như iOS.
 * "Xóa ứng dụng" (gỡ cài đặt), "Xóa khỏi Màn hình chính" (ẩn khỏi Home, app vẫn ở Thư viện), "Hủy". Chạm ra ngoài / Back = Hủy.
 *
 * RA2 (2026-11-24): khác [showRemoveWidgetDialog]/[showUninstallAppDialog] (vẫn `Dialog` cửa sổ riêng — Library/Today không có backdrop Home
 * để lấy mẫu), đây CHỈ xảy ra từ lưới/dock/thư mục Home nên vẽ NGAY TRONG cây Compose của HomeScreen ([RemoveAppOverlay]), lấy mẫu
 * `homeBackdrop` (lớp Home CHƯA tối, như [HomeIconMenu]) để có khúc xạ/kính thật, không chỉ làm mờ cửa sổ hệ thống.
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
/** [inHome] (RA3): gọi từ lưới Home → vẽ bằng [RemoveAppOverlay] (kính thật + blur/tối nền như thư mục); Today vẫn dùng Dialog. */
internal fun showRemoveWidgetDialog(
    activity: ComponentActivity,
    messageRes: Int = R.string.widget_remove_message,
    inHome: Boolean = false,
    onRemove: () -> Unit
) {
    val title = activity.getString(R.string.widget_remove_title)
    val message = activity.getString(messageRes)
    val buttons = listOf(
        CardButton(R.string.widget_remove_confirm, destructive = true, bold = false, action = onRemove),
        CardButton(R.string.widget_remove_cancel, destructive = false, bold = true, action = null)
    )
    if (inHome) removeAppRequest = RemoveAppRequest(title, message, buttons) else showCard(activity, title, message, buttons)
}

/** LD Task 2: dấu "−" trên app ở chế độ sửa của Library: chỉ hỏi gỡ cài đặt (không có "Xóa khỏi MH chính"), cùng kiểu thẻ với xóa app. */
internal fun showUninstallAppDialog(activity: ComponentActivity, appLabel: String, onDeleteApp: () -> Unit) {
    showCard(
        activity,
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
    current?.dismiss()
}

internal class CardButton(val textRes: Int, val destructive: Boolean, val bold: Boolean, val action: (() -> Unit)?)

private fun showCard(activity: ComponentActivity, title: String, message: String, buttons: List<CardButton>) {
    current?.dismiss()
    current = activity.dsDialogs.show { handle ->
        RemoveCard(title, message, buttons, onDismiss = handle::dismiss)
    }.also { h -> h.setOnDismissListener { if (current === h) current = null } }
}

/**
 * RA2 (2026-11-24) — bản TRONG CÂY của [showRemoveAppDialog], đặt trong `HomeScreen` cạnh [HomeFolderPanel]/[HomeIconMenu] (cùng
 * [homeBackdrop] của Home, lớp CHƯA tối). Thẻ kính thật căn giữa màn, chạm ra ngoài → Hủy — như [HomeIconMenu.MenuCard] nhưng không neo
 * theo icon (thẻ xác nhận, không phải menu theo vị trí).
 *
 * RA3: KHÔNG tự vẽ lớp tối/mờ của mình (khác bản cũ) — xuất [removeOverlayProgress] cho HomeScreen tự blur + tối lớp Home phía sau, Y HỆT
 * lúc mở thư mục ([HomeFolderPanel], không chỉ tối như menu nhấn giữ icon).
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
    val r = shown ?: return
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
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
        val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        // 2026-11-23: thẻ là kính (blur nền cửa sổ của hệ thống, DialogWindowGlass), bo 30 dp nhân thanh "Bo góc" của kính.
        val corner = (CARD_RADIUS * glassCornerScale()).coerceAtMost(CARD_RADIUS * 2)
        val cornerPx = with(LocalDensity.current) { corner.toPx() }
        val card = colorResource(R.color.remove_app_card).toArgb()
        val glass = DialogWindowGlass((view.parent as? DialogWindowProvider)?.window, cornerPx, glassTintOf(card, CARD_GLASS_ALPHA), card)
        Column(
            Modifier
                .width(minOf(screen - 64.dp, 320.dp))
                .clip(RoundedCornerShape(corner))
                .then(
                    if (glass) Modifier.border(1.dp, colorResource(R.color.shortcut_menu_glass_border), RoundedCornerShape(corner))
                    else Modifier.background(Color(card))
                )
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val text = colorResource(R.color.shortcut_menu_text)
            val destructive = colorResource(R.color.shortcut_menu_destructive)
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
/** 2026-11-23: độ đục của tint kính thẻ (màu `remove_app_card` cũ ~94 %): đủ đọc chữ, vẫn thấy nền mờ phía sau. Dùng cho [RemoveCard] (Dialog
 * cửa sổ, Library/Today). */
private const val CARD_GLASS_ALPHA = 0.55f

/** RA2 (2026-11-24): độ mờ CỐ ĐỊNH của thẻ xóa app trong cây Compose (như [HomeIconMenu]'s MENU_BLUR_DP / panel thư mục FOLDER_MIN_BLUR_DP). */
private const val CARD_BLUR_DP = 18f
