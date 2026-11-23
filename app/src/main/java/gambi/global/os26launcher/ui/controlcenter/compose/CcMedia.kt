package gambi.global.os26launcher.ui.controlcenter.compose

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.drawDrawable
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.domain.controlcenter.MediaState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * JS7b: nút lùi/phát/tới (thay `ccButton` ImageView): icon trắng lùi vào 1/5 cạnh mỗi bên. Gợn sóng không viền của View được thay bằng
 * lớp sáng tròn lúc nhấn (Compose của dự án không có ripple Material).
 */
@Composable
private fun CcMediaButton(
    panel: ControlCenterPanel,
    @DrawableRes icon: Int,
    label: String,
    sizePx: Int?, // null = cỡ do cha đặt (khối nhạc nhỏ tính cỡ từ bề rộng khối)
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val painter = painterResource(icon)
    val tint = colorResource(R.color.cc_icon)
    Box(
        modifier
            .then(if (sizePx != null) Modifier.size(with(density) { sizePx.toDp() }) else Modifier)
            .graphicsLayer { this.alpha = alpha }
            .ccClaimTouch(panel)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .dsPressHighlight(interaction)
            .drawBehind {
                val s = size.width.toInt()
                val p = s / 5
                drawIconAt(painter, p, p, s - 2 * p, tint)
            }
    )
}

/** Ô ảnh album bo góc; chưa có ảnh thì là ô kính sáng hơn với nốt nhạc mờ (lùi 1/4 cạnh, alpha 90/255). Chạm mở app đang phát. */
@Composable
private fun CcMediaArt(panel: ControlCenterPanel, art: Bitmap?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val placeholder = rememberCcGlass(cornerRadius = 0f, fill = R.color.cc_glass_inner, rim = false)
    val music = painterResource(R.drawable.ic_cc_music)
    val tint = colorResource(R.color.cc_icon)
    val image = remember(art) { art?.asImageBitmap() }
    Box(
        modifier
            .clip(RoundedCornerShape(ART_CORNER_PERCENT)) // cắt cả nền placeholder theo góc bo (22% cạnh)
            .ccClaimTouch(panel)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .then(
                if (image != null) Modifier else Modifier.drawBehind {
                    drawDrawable(placeholder)
                    val a = size.width.toInt()
                    val p = a / 4
                    drawIconAt(music, p, p, a - 2 * p, tint, PLACEHOLDER_ALPHA / 255f)
                }
            )
    ) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * JS7b: khối nhạc nhỏ 2×2 của trang chính (thay `MediaTileView`, RE mục 5.1 `ktd`), dáng iOS 26: ảnh album ở góc trên trái (34% bề
 * rộng), nút thiết bị phát ra tròn ở góc trên phải (26%), tên bài/nghệ sĩ ở giữa, hàng lùi/phát/tới canh tâm ở ~84% chiều cao (nút
 * phát lớn hơn 1,25 lần). Nhấn giữ bất kỳ đâu sang trang Nhạc (RE mục 5.2).
 */
@Composable
internal fun CcMediaTile(panel: ControlCenterPanel, media: MediaState?, modifier: Modifier = Modifier) {
    val glass = rememberCcGlass()
    val buttonPx = with(LocalDensity.current) { dimensionResource(R.dimen.cc_media_button).roundToPx() }
    val title = if (media == null) stringResource(R.string.cc_media_idle) else media.title.ifEmpty { media.appLabel }
    val artist = when {
        media == null -> ""
        else -> media.artist.ifEmpty { stringResource(R.string.cc_media_unknown_artist) }
    }
    val description = stringResource(R.string.cc_block_media) + ", " + title
    val toMedia = { panel.showPage(ControlCenterPanel.PAGE_MEDIA) }
    val idle = media == null
    val playing = media?.isPlaying == true
    val listener = { panel.listener }
    Layout(
        content = {
            CcMediaArt(panel, media?.art) { listener()?.onOpenApp() }
            CcTile(
                panel, R.drawable.ic_cc_output, stringResource(R.string.cc_media_output), false, CC_NO_COLOR, CC_NO_COLOR,
                checkable = false
            ) { listener()?.onOutput() }
            CcText(title, 16f, Modifier.clearAndSetSemantics {}, bold = true, marquee = true)
            CcText(artist, 14f, Modifier.clearAndSetSemantics {}, alpha = 0.6f, marquee = true)
            // Cỡ nút do Layout bên dưới quyết định (phụ thuộc bề rộng khối).
            CcMediaButton(panel, R.drawable.ic_cc_previous, stringResource(R.string.cc_media_previous), null, alpha = if (idle) IDLE_ALPHA else 1f) {
                listener()?.onPrevious()
            }
            CcMediaButton(
                panel, if (playing) R.drawable.ic_cc_pause else R.drawable.ic_cc_play,
                stringResource(if (playing) R.string.cc_media_pause else R.string.cc_media_play), null
            ) { listener()?.onPlayPause() }
            CcMediaButton(panel, R.drawable.ic_cc_next, stringResource(R.string.cc_media_next), null, alpha = if (idle) IDLE_ALPHA else 1f) {
                listener()?.onNext()
            }
        },
        modifier = modifier
            .ccGlass(glass)
            .ccLongPressGroup(panel, toMedia)
            .ccLongClickSemantics(description, toMedia)
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val pad = (w * PAD).toInt()
        val a = (w * ART).toInt()
        val art = measurables[0].measure(Constraints.fixed(a, a))
        val os = (w * OUTPUT).toInt()
        val output = measurables[1].measure(Constraints.fixed(os, os))
        val textC = Constraints(minWidth = (w - 2 * pad).coerceAtLeast(0), maxWidth = (w - 2 * pad).coerceAtLeast(0), maxHeight = h)
        val titleP = measurables[2].measure(textC)
        val artistP = if (artist.isEmpty()) null else measurables[3].measure(textC)
        val b = min(buttonPx, (w - 2 * pad) / 3)
        val prev = measurables[4].measure(Constraints.fixed(b, b))
        val pb = min((b * PLAY_SCALE).toInt(), (w - 2 * pad) / 3)
        val play = measurables[5].measure(Constraints.fixed(pb, pb))
        val next = measurables[6].measure(Constraints.fixed(b, b))
        layout(w, h) {
            art.place(pad, pad)
            output.place(w - pad - os, pad)
            // Chữ nằm giữa đáy ảnh và hàng nút; hàng nút canh tâm ở ~84% chiều cao như ảnh iOS 26.
            val rowCenter = (h * ROW_CENTER).toInt().coerceAtMost(h - pad / 2 - play.height / 2)
            val textH = titleP.height + (artistP?.height ?: 0)
            val textTop = pad + a
            val textBottom = rowCenter - play.height / 2
            var y = (textTop + (textBottom - textTop - textH) / 2).coerceAtLeast(textTop)
            titleP.place(pad, y)
            y += titleP.height
            artistP?.place(pad, y)
            listOf(prev to PREV_X, play to 0.5f, next to NEXT_X).forEach { (p, fx) ->
                p.place((w * fx - p.width / 2f).toInt(), rowCenter - p.height / 2)
            }
        }
    }
}

/**
 * JS7b: trình phát lớn của trang Nhạc (thay `MediaPlayerView`, RE mục 5.1 `ntd`): tên app, ảnh album (42% bề rộng thẻ), tên
 * bài/nghệ sĩ chạy chữ, thanh tiến độ kèm giờ, lùi/phát/tới, thiết bị phát ra, thanh âm lượng ngang (dùng chung giá trị với thanh
 * âm lượng của trang chính). Tiến độ đếm mỗi giây khi đang phát và panel đang mở.
 */
@Composable
internal fun CcMediaPlayer(
    panel: ControlCenterPanel,
    media: MediaState?,
    widthPx: Int,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    artSizePx: Int = 0
) {
    val density = LocalDensity.current
    val glass = rememberCcGlass(cornerRadius = with(density) { dimensionResource(R.dimen.cc_block_radius).toPx() })
    val pad = dimensionResource(R.dimen.cc_pad)
    val b = dimensionResource(R.dimen.cc_media_button)
    val bPx = with(density) { b.roundToPx() }
    val artPx = (widthPx * ART_FRACTION).toInt()
    val listener = { panel.listener }
    val playing = media?.isPlaying == true

    val position = remember(media) { mutableLongStateOf(currentPosition(media)) }
    val attached = panel.attached
    LaunchedEffect(media, attached) {
        while (media?.isPlaying == true && attached) {
            delay(TICK_MS)
            position.longValue = currentPosition(media)
        }
    }
    var seeking by remember { mutableStateOf<Int?>(null) }
    val d = media?.durationMs ?: 0L
    val p = position.longValue
    val progress = seeking ?: if (d > 0) (p * SEEK_STEPS / d).toInt() else 0
    val elapsed = formatTime(seeking?.let { d * it / SEEK_STEPS } ?: p)
    val remaining = if (d > 0) "-" + formatTime(d - p) else ""

    val card = modifier
        .width(with(density) { widthPx.toDp() })
        .ccGlass(glass)
        .ccClaimTouch(panel) // chạm trên thẻ (chữ, lề) không rơi xuống nền và đóng panel
        .padding(pad)
    // P2-8: phần dưới ảnh bìa (tên bài -> âm lượng). Dọc: dưới ảnh bìa; ngang: cột bên phải ảnh bìa (như iOS), tên app lên đầu cột.
    val controls: @Composable ColumnScope.() -> Unit = {
        val title = if (media == null) stringResource(R.string.cc_media_idle) else media.title.ifEmpty { media.appLabel }
        val artist = if (media == null) "" else media.artist.ifEmpty { stringResource(R.string.cc_media_unknown_artist) }
        CcText(title, 18f, Modifier.fillMaxWidth().padding(top = pad / 2), bold = true, marquee = true)
        CcText(artist, 15f, Modifier.fillMaxWidth(), alpha = 0.6f, marquee = true)
        CcSeekBar(
            panel, progress / SEEK_STEPS.toFloat(), enabled = d > 0,
            modifier = Modifier.fillMaxWidth().padding(top = pad / 2),
            onChange = { seeking = it },
            onStop = {
                seeking = null
                listener()?.onSeek(d * it / SEEK_STEPS)
            }
        )
        Row(Modifier.fillMaxWidth()) {
            CcText(elapsed, 12f, Modifier.weight(1f), alpha = 0.6f)
            CcText(remaining, 12f, Modifier.weight(1f), alpha = 0.6f, align = TextAlign.End)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CcMediaButton(panel, R.drawable.ic_cc_previous, stringResource(R.string.cc_media_previous), bPx) { listener()?.onPrevious() }
            CcMediaButton(
                panel, if (playing) R.drawable.ic_cc_pause else R.drawable.ic_cc_play,
                stringResource(if (playing) R.string.cc_media_pause else R.string.cc_media_play), (bPx * 1.3f).toInt()
            ) { listener()?.onPlayPause() }
            CcMediaButton(panel, R.drawable.ic_cc_next, stringResource(R.string.cc_media_next), bPx) { listener()?.onNext() }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = pad / 2)
                .clickable(remember { MutableInteractionSource() }, indication = null) { listener()?.onOutput() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            CcIcon(R.drawable.ic_cc_output, with(density) { (bPx / 2).toDp() })
            CcText(
                media?.outputName ?: stringResource(R.string.cc_media_output_phone), 13f,
                Modifier.weight(1f).padding(start = pad / 2), alpha = 0.8f
            )
        }
        CcSlider(
            panel, panel.playerVolume, R.drawable.ic_cc_volume, stringResource(R.string.cc_volume),
            Modifier.padding(top = pad / 2).fillMaxWidth().height(dimensionResource(R.dimen.cc_media_volume_height)),
            horizontal = true
        ) { v, done -> panel.onPlayerVolume(v, done) }
    }
    val appName: @Composable () -> Unit = {
        CcText(media?.appLabel.orEmpty(), 13f, Modifier.fillMaxWidth(), alpha = 0.6f, marquee = true)
    }
    if (landscape) {
        Row(card, verticalAlignment = Alignment.CenterVertically) {
            CcPlayerArt(panel, media, Modifier.size(with(density) { artSizePx.toDp() })) { listener()?.onOpenApp() }
            Column(Modifier.weight(1f).padding(start = pad), horizontalAlignment = Alignment.CenterHorizontally) {
                appName()
                controls()
            }
        }
    } else {
        Column(card, horizontalAlignment = Alignment.CenterHorizontally) {
            appName()
            CcPlayerArt(panel, media, Modifier.padding(top = pad / 2).size(with(density) { artPx.toDp() })) { listener()?.onOpenApp() }
            controls()
        }
    }
}

/** Ảnh album của trình phát lớn: bo 6% cạnh; chưa có ảnh thì nốt nhạc trắng phủ ô (CENTER_CROP như ImageView cũ). */
@Composable
private fun CcPlayerArt(panel: ControlCenterPanel, media: MediaState?, modifier: Modifier, onClick: () -> Unit) {
    val image = remember(media?.art) { media?.art?.asImageBitmap() }
    Box(
        modifier
            .clip(RoundedCornerShape(PLAYER_ART_CORNER_PERCENT))
            .ccClaimTouch(panel)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(
                painterResource(R.drawable.ic_cc_music), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(colorResource(R.color.cc_icon))
            )
        }
    }
}

/**
 * JS7b: thanh tiến độ (thay `SeekBar`): kéo ngang quá slop thì tua theo ngón (panel không giành), vuốt dọc thì panel đổi trang;
 * chạm không kéo thì nhảy tới chỗ chạm (như SeekBar trong khung cuộn). Không có thời lượng thì tắt.
 */
@Composable
private fun CcSeekBar(
    panel: ControlCenterPanel,
    fraction: Float,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (Int) -> Unit,
    onStop: (Int) -> Unit
) {
    val density = LocalDensity.current
    val sidePad = with(density) { SEEK_SIDE_PAD.toPx() }
    val track = with(density) { SEEK_TRACK.toPx() }
    val thumb = with(density) { SEEK_THUMB_RADIUS.toPx() }
    val color = colorResource(R.color.cc_icon)
    val change by rememberUpdatedState(onChange)
    val stop by rememberUpdatedState(onStop)
    Box(
        modifier
            .height(SEEK_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else SEEK_DISABLED_ALPHA }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun progressAt(x: Float): Int {
                    val span = (size.width - 2 * sidePad).coerceAtLeast(1f)
                    return (((x - sidePad) / span).coerceIn(0f, 1f) * SEEK_STEPS).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var dragging = false
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (c == null) {
                            if (dragging) stop(progressAt(down.position.x))
                            break
                        }
                        if (!c.pressed) {
                            if (dragging || !c.isConsumed) stop(progressAt(c.position.x))
                            break
                        }
                        if (!dragging) {
                            if (c.isConsumed) break // panel đã giành để đổi trang
                            if (abs(c.position.x - down.position.x) > slop) {
                                dragging = true
                                panel.disallowIntercept = true
                            }
                        }
                        if (dragging) {
                            change(progressAt(c.position.x))
                            c.consume()
                        }
                    }
                }
            }
            .drawBehind {
                val cy = size.height / 2
                val left = sidePad
                val right = size.width - sidePad
                val x = left + (right - left) * fraction
                drawRoundRect(
                    color.copy(alpha = SEEK_TRACK_BG_ALPHA), Offset(left, cy - track / 2), Size(right - left, track),
                    CornerRadius(track / 2)
                )
                drawRoundRect(color, Offset(left, cy - track / 2), Size(max(0f, x - left), track), CornerRadius(track / 2))
                drawCircle(color, thumb, Offset(x, cy))
            }
    )
}

private fun currentPosition(s: MediaState?): Long {
    s ?: return 0
    val p = if (s.isPlaying) s.positionMs + ((SystemClock.elapsedRealtime() - s.positionUpdatedAtElapsed) * s.speed).toLong()
    else s.positionMs
    return p.coerceIn(0, max(s.durationMs, 0))
}

/** "m:ss" (không qua String.format để khỏi phụ thuộc Locale mặc định). */
private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:" + (total % 60).toString().padStart(2, '0')
}

private const val PAD = 0.09f
private const val ART = 0.34f
private const val OUTPUT = 0.26f
private const val PLAY_SCALE = 1.25f
private const val ROW_CENTER = 0.84f
private const val PREV_X = 0.22f
private const val NEXT_X = 0.78f
private const val IDLE_ALPHA = 0.55f
private const val PLACEHOLDER_ALPHA = 90
private const val ART_CORNER_PERCENT = 22
private const val PLAYER_ART_CORNER_PERCENT = 6
private const val SEEK_STEPS = 1000
private const val TICK_MS = 1000L
private const val ART_FRACTION = 0.42f

// SeekBar Material của hệ thống: lề hai bên 16 dp; vạch và nút kéo vẽ xấp xỉ (JS7b, không có drawable SeekBar trong Compose).
private val SEEK_SIDE_PAD: Dp = 16.dp
private val SEEK_TRACK: Dp = 2.dp
private val SEEK_THUMB_RADIUS: Dp = 6.dp
private val SEEK_HEIGHT: Dp = 24.dp
private const val SEEK_TRACK_BG_ALPHA = 0.3f
private const val SEEK_DISABLED_ALPHA = 0.3f
