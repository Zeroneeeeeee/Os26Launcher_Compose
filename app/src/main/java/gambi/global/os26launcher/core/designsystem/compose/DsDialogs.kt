package gambi.global.os26launcher.core.designsystem.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsCorners
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.DsSurfaceDrawable
import gambi.global.os26launcher.core.glass.compose.DialogWindowGlass
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.glassTintOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------------------------------------------
// Hộp thoại

/**
 * JD Task 7 — hộp thoại DS3 bằng Compose (bản View: `DsDialog` + `ds_dialog.xml`, Figma `312:2691`). Rộng `ds_dialog_width` (312), tối đa
 * bề rộng màn trừ hai lề 24; bề mặt `SHEET` bo theo vai `SHEET`, đệm 20/24/20/20, khe 12; tiêu đề `card` (tiêu đề cho TalkBack), lời
 * nhắn `body` màu `ds_text_secondary`, [content] tùy ý, rồi hàng nút: [negative] (phụ) + [positive] (chính) chia đôi, khe 10; chỉ một nút
 * thì nút đó chiếm cả hàng. Lớp mờ nền 60%.
 *
 * F6 (JD): hiện ra kiểu thông báo iOS: phóng từ 1,1 về 1 kèm hiện dần; tắt thì mờ dần rồi mới gọi [onDismiss]. Bấm nút: chạy hành động
 * của nút rồi tắt. [cancelable] = Back và chạm ra ngoài cũng tắt.
 *
 * Kính: hộp thoại nằm ở cửa sổ riêng, toạ độ lớp backdrop của màn chính không khớp, và nền `ds_surface_sheet` đặc 94% nên kính gần như
 * không thấy: vẽ bề mặt tĩnh ([LocalDsBackdrop] = null bên trong).
 */
@Composable
fun DsAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    positive: Pair<String, () -> Unit>?,
    message: String? = null,
    negative: Pair<String, () -> Unit>? = null,
    cancelable: Boolean = true,
    positiveEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) } // 0 = ẩn, 1 = hiện
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    fun close(action: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        action?.invoke()
        scope.launch {
            progress.animateTo(0f, tween(EXIT_MS))
            currentOnDismiss()
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }

    Dialog(
        onDismissRequest = { close() },
        // DW (chủ dự án 2026-11-25, cách 1): `usePlatformDefaultWidth = true` để cửa sổ hộp thoại ÔM VỪA thẻ. Kính của hộp thoại là blur nền
        // cửa sổ của hệ thống (DialogWindowGlass), vùng mờ = cả cửa sổ (chỉ bo góc theo nền); với `false` Compose cho cửa sổ rộng hết màn nên
        // lộ một khung mờ có tint lớn hơn thẻ. Thẻ rộng `ds_dialog_width` (312) > bề rộng tối thiểu của hộp thoại trên điện thoại (65 %).
        properties = DialogProperties(dismissOnBackPress = cancelable, dismissOnClickOutside = cancelable, usePlatformDefaultWidth = true)
    ) {
        // Lớp mờ 60% hiện/tắt theo hoạt ảnh. Phải THEO DÕI progress (snapshotFlow): SideEffect chỉ chạy lúc dựng lại nên đặt một lần
        // ở 0 rồi thôi (đo được trên Oppo: hộp thoại không có lớp mờ).
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        dialogWindow?.let { window ->
            LaunchedEffect(window) {
                snapshotFlow { progress.value.coerceIn(0f, 1f) }.collect { window.setDimAmount(DIM * it) }
            }
        }
        // 2026-11-23 (chủ dự án): nền kính (blur nền cửa sổ của hệ thống, xem DialogWindowGlass) thay bề mặt SHEET đặc; máy không blur xuyên
        // cửa sổ thì vẽ bề mặt SHEET như cũ. Bo theo vai SHEET (đã nhân "Bo góc").
        val sheetRadiusPx = DsCorners.radiusPx(LocalContext.current, DsCorner.Role(DsCornerRole.SHEET), 0, 0)
        val sheetColor = colorResource(R.color.ds_surface_sheet).toArgb()
        val glass = DialogWindowGlass(dialogWindow, sheetRadiusPx, glassTintOf(sheetColor, SHEET_GLASS_ALPHA), android.graphics.Color.TRANSPARENT) {
            progress.value
        }
        val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        val margin = dimensionResource(R.dimen.ds_space_24)
        val width = minOf(dimensionResource(R.dimen.ds_dialog_width), screenWidth - margin * 2)
        val primary = colorResource(R.color.ds_text_primary)
        CompositionLocalProvider(LocalDsBackdrop provides null) {
            Column(
                Modifier
                    .width(width)
                    .graphicsLayer {
                        val p = progress.value
                        val s = 1f + (ENTER_SCALE - 1f) * (1f - p.coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .dsSheetSurface(topCornersOnly = false, glass = glass)
                    .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(title, style = DsTextStyles.card.copy(color = primary), modifier = Modifier.fillMaxWidth().semantics { heading() })
                if (!message.isNullOrEmpty()) {
                    BasicText(message, style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)), modifier = Modifier.fillMaxWidth())
                }
                content?.invoke()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (negative != null) {
                        DsButton(negative.first, onClick = { close(negative.second) }, modifier = Modifier.weight(1f), variant = DsButtonVariant.Secondary)
                    }
                    if (positive != null) {
                        DsButton(positive.first, onClick = { close(positive.second) }, modifier = Modifier.weight(1f), enabled = positiveEnabled)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Bottom sheet

/**
 * JD Task 7 — bottom sheet DS3 bằng Compose (bản View: `DsBottomSheet` + `ds_bottom_sheet.xml`, Figma `312:2681`). Bề mặt `SHEET` chỉ bo
 * hai góc trên (bo theo vai `SHEET`), đệm 20/10/20/32 + inset thanh điều hướng; tay nắm 38 × 5 bo 3; hàng đầu: [title] chữ `card` + nút
 * đóng tròn 32 trong vùng chạm 48 (khi [showClose]); rồi [content]. Mở là bung hết cỡ nội dung.
 *
 * F5 (JD): trượt lên bằng lò xo lúc mở; kéo xuống để đóng (quá 25% chiều cao, hoặc vuốt nhanh hơn 1200 dp/s), thả chưa đủ thì bật về; lớp
 * mờ nền (60%) nhạt dần theo độ kéo. Back và chạm lớp mờ cũng đóng. Không dùng kính thật (cùng lý do với [DsAlertDialog]).
 */
@Composable
fun DsBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // PG2: thân sheet tách ra [DsBottomSheetLayer] (dùng được trong cửa sổ của màn, không cần Dialog). Bản Dialog giữ đúng hành vi cũ: Back và
    // chạm lớp mờ do lớp lo (đóng có hoạt ảnh), cửa sổ không tự đóng.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            SideEffect { window.setDimAmount(0f) } // lớp mờ tự vẽ để nhạt dần theo độ kéo
        }
        DsBottomSheetLayer(onDismiss = onDismiss, title = title, showClose = showClose, content = content)
    }
}

/**
 * PG2 — thân của [DsBottomSheet] không có `Dialog`: lớp mờ toàn vùng + khối sheet ở đáy (trượt lên khi mở, kéo xuống để đóng, tay nắm, tiêu đề,
 * nút đóng), Back đóng (có hoạt ảnh). Đặt trên cùng một lớp phủ toàn màn của màn đang hiện (vd bảng chọn widget của Home, để thẻ trong sheet kéo
 * được ra lưới cùng cửa sổ). [onDismiss] gọi khi hoạt ảnh đóng xong. Không dùng kính thật (`LocalDsBackdrop = null`), như bản Dialog.
 *
 * PD (2026-11-25): [maxHeightFraction] (null = theo nội dung) giới hạn CẢ khối bảng (tính cả tay nắm, lề, inset thanh điều hướng) theo phần cao cửa
 * sổ; khi có giới hạn, vùng [content] là `weight(1f, fill = false)` nên con dùng `Modifier.weight` để lấp / co theo chỗ còn lại. [retracted] = true:
 * bảng trượt xuống khỏi màn (lớp tối nhạt theo) nhưng VẪN trong composition (cử chỉ đang chạy trong bảng không bị huỷ), không kéo được; về false thì
 * trượt lên lại. Không gọi [onDismiss].
 */
@Composable
fun DsBottomSheetLayer(
    onDismiss: () -> Unit,
    title: String? = null,
    showClose: Boolean = true,
    maxHeightFraction: Float? = null,
    retracted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(Float.MAX_VALUE) } // px dời xuống; MAX trước khi đo
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            offset.animateTo(sheetHeight.coerceAtLeast(1f), tween(EXIT_MS))
            currentOnDismiss()
        }
    }

    LaunchedEffect(sheetHeight, retracted) {
        if (sheetHeight > 0f && !closing) {
            if (offset.value == Float.MAX_VALUE) offset.snapTo(sheetHeight)
            if (retracted) offset.animateTo(sheetHeight, tween(EXIT_MS)) // PD: trượt xuống, giữ trong composition
            else offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
        }
    }
    val maxSheetHeight = maxHeightFraction?.let { f -> with(density) { (LocalWindowInfo.current.containerSize.height * f).toDp() } }

    BackHandler { close() }
    run {
        val fraction = if (sheetHeight > 0f) (offset.value / sheetHeight).coerceIn(0f, 1f) else 1f
        CompositionLocalProvider(LocalDsBackdrop provides null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = DIM * (1f - fraction)))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (maxSheetHeight != null) Modifier.heightIn(max = maxSheetHeight) else Modifier)
                        .onSizeChanged { sheetHeight = it.height.toFloat() }
                        .offset { IntOffset(0, if (offset.value == Float.MAX_VALUE) 100_000 else offset.value.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = !retracted,
                            state = rememberDraggableState { delta ->
                                if (!closing) scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                            },
                            onDragStopped = { velocity ->
                                val fast = velocity > with(density) { FLING_DISMISS.toPx() }
                                if (fast || offset.value > sheetHeight * DRAG_DISMISS) {
                                    close()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                                }
                            }
                        )
                        .dsSheetSurface(topCornersOnly = true)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 32.dp)
                ) {
                    val control = colorResource(R.color.ds_surface_control)
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 38.dp, height = 5.dp)
                            .background(control, RoundedCornerShape(3.dp))
                    )
                    if (title != null || showClose) {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                title.orEmpty(),
                                style = DsTextStyles.card.copy(color = colorResource(R.color.ds_text_primary)),
                                modifier = Modifier.weight(1f).semantics { heading() }
                            )
                            if (showClose) {
                                val interaction = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .size(dimensionResource(R.dimen.ds_touch_min))
                                        .dsPressable(interaction)
                                        // Mô tả "Đóng" lấy từ contentDescription của icon, gộp vào nút.
                                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { close() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(32.dp).background(control, CircleShape), contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(R.drawable.ds_ic_close),
                                            contentDescription = stringResource(R.string.ds_close),
                                            colorFilter = ColorFilter.tint(colorResource(R.color.ds_text_primary)),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (maxSheetHeight != null) Modifier.weight(1f, fill = false) else Modifier)
                            .padding(top = 16.dp),
                        content = content
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------

/** Bề mặt `SHEET` tĩnh bo theo vai `SHEET` (đọc thanh "Bo góc"); [topCornersOnly] = chỉ bo hai góc trên (sheet). Cắt nội dung theo hình bo. */
private fun Modifier.dsSheetSurface(topCornersOnly: Boolean, glass: Boolean = false): Modifier = composed {
    val context = LocalContext.current
    val drawable = remember(context, topCornersOnly) {
        DsSurfaceDrawable(context, DsSurface.SHEET, DsCorner.Role(DsCornerRole.SHEET), topCornersOnly = topCornersOnly)
    }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val radiusPx = if (widthPx > 0f) DsCorners.radiusPx(context, DsCorner.Role(DsCornerRole.SHEET), widthPx.toInt(), heightPx.toInt()) else 0f
    val r = with(LocalDensity.current) { radiusPx.toDp() }
    val shape = if (topCornersOnly) RoundedCornerShape(topStart = r, topEnd = r) else RoundedCornerShape(r)
    onSizeChanged { widthPx = it.width.toFloat(); heightPx = it.height.toFloat() }
        .drawBehind {
            // [glass]: nền cửa sổ đã là kính (DialogWindowGlass), chỉ vẽ viền sáng mảnh.
            if (glass) {
                drawOutline(shape.createOutline(size, layoutDirection, this), SHEET_GLASS_BORDER, style = Stroke(1.dp.toPx()))
            } else {
                drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                drawIntoCanvas { drawable.draw(it.nativeCanvas) }
            }
        }
        .clip(shape)
}

/** 2026-11-23: độ đục tint kính của hộp thoại (màu `ds_surface_sheet` cũ ~94 %). */
private const val SHEET_GLASS_ALPHA = 0.6f
private val SHEET_GLASS_BORDER = Color(0x40FFFFFF)

private const val DIM = 0.6f
/** DW: phóng từ 0,95 lên 1 (trước 1,1 xuống 1): cửa sổ ôm vừa thẻ nên phần thẻ phóng to vượt cửa sổ sẽ bị cắt mép. */
private const val ENTER_SCALE = 0.95f
private const val EXIT_MS = 180
private const val DRAG_DISMISS = 0.25f
private val FLING_DISMISS = 1200.dp
