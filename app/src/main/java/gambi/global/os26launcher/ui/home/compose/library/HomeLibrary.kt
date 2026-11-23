package gambi.global.os26launcher.ui.home.compose.library

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsSpinner
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.LibraryLayoutMetrics
import gambi.global.os26launcher.core.designsystem.compose.wallpaperSoftBlur
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.CategoryGroup
import gambi.global.os26launcher.domain.model.CategoryGroupApps
import gambi.global.os26launcher.domain.model.LibraryContent
import gambi.global.os26launcher.domain.model.ScreenRect
import gambi.global.os26launcher.ui.home.compose.HomeIcons
import gambi.global.os26launcher.ui.home.compose.HomeRemoveBadge
import gambi.global.os26launcher.ui.home.compose.badgeCenterForIcon
import gambi.global.os26launcher.ui.home.compose.homeJiggle
import gambi.global.os26launcher.ui.home.compose.rememberHomeIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * JS4 — App Library bằng Compose, thay `CategoryPageView` + `CategoryGroupPanelView` + nền `blurredWallpaperScrim` (View, JH3c). Vẽ ở Activity
 * phía trên `HomeScreen`; cử chỉ vuốt mở/đóng vẫn là `LibraryOverlayController` ở `dispatchTouchEvent` (không phải UI), tiến độ đi vào
 * [HomeLibraryState.swipeProgress].
 *
 * Lớp, từ dưới lên:
 * 1. Nền: ảnh nền làm mờ (bản thu nhỏ phóng lên, rẻ, mọi API, như bản mờ dùng chung của kính View) + lớp tối nhẹ; hiện dần theo tiến độ vuốt.
 *    Nền này được ghi làm lớp backdrop riêng của Library: ô nhóm là kính `CARD` thật lấy mẫu nó.
 * 2. Trang Library trượt vào từ phải (thanh tìm kiếm, lưới 2 cột ô nhóm hoặc danh sách A–Z + cột chữ cái).
 * 3. Màn nhóm: toàn màn, tiêu đề lớn + lưới 4 cột, phình ra từ ô nhóm được bấm (spring `AppLibraryMotion.GROUP_PANEL_SPRING`), Library phía
 *    sau co về 0,85 và mờ dần.
 */
@Composable
internal fun HomeLibrary(state: HomeLibraryState, icons: HomeIcons, backdrop: Bitmap?, modifier: Modifier = Modifier) {
    val swipe = state.swipeProgress.floatValue
    val group = state.openGroup
    val panel = remember { Animatable(0f) }
    var shownGroup by remember { mutableStateOf<CategoryGroupApps?>(null) }
    var origin by remember { mutableStateOf<ScreenRect?>(null) }
    // Đích của spring = có nhóm hay không; nội dung lúc đóng giữ tới khi spring dừng (như CategoryGroupPanelView).
    LaunchedEffect(group) {
        if (group != null) {
            if (shownGroup?.group != group.group) {
                if (panel.value > OPEN_EPS) panel.snapTo(0f) // đang đóng nhóm A mà mở nhóm B: dừng ở 0 rồi mở B
                origin = state.tileRects[group.group]
            }
            shownGroup = group
            panel.animateTo(1f, GROUP_SPRING)
        } else if (shownGroup != null) {
            panel.animateTo(0f, GROUP_SPRING)
            shownGroup = null
        }
    }
    val panelP = panel.value
    state.panelProgress = panelP
    if (swipe <= 0f && panelP <= 0f) return

    val night = state.night
    val blurred = remember(backdrop) { backdrop?.let(::shrinkForBlur) }
    val libBackdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        // 1. Nền mờ (ghi làm lớp backdrop của kính ô nhóm).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = swipe.coerceIn(0f, 1f) }
                .layerBackdrop(libBackdrop)
                .background(Color(if (night) SCRIM_FLAT_DARK else SCRIM_FLAT_LIGHT))
        ) {
            if (blurred != null) {
                Image(
                    blurred.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize().wallpaperSoftBlur()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (night) SCRIM_TINT_DARK else SCRIM_TINT_LIGHT)))
        }
        // 2. Trang Library: trượt vào từ phải; co + mờ khi màn nhóm mở ở trên.
        CompositionLocalProvider(LocalDsBackdrop provides libBackdrop) {
            LibraryPage(
                state = state,
                icons = icons,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * (1f - swipe)
                        val c = panelP.coerceIn(0f, 1f)
                        val behind = 1f - ramp(c, 0f, BEHIND_FADE_END)
                        alpha = behind
                        val s = 1f - (1f - BEHIND_END_SCALE) * c
                        scaleX = s
                        scaleY = s
                    }
                    .blockTouches(enabled = swipe > 0f && panelP <= OPEN_EPS)
                    // LD Task 2: chế độ sửa: chạm chỗ trống (không ô nào nhận cú chạm) thì thoát. Ô app / ô nhóm tiêu thụ cú chạm của chúng trước.
                    .then(if (state.editing) Modifier.pointerInput(Unit) { detectTapGestures { state.exitEditing() } } else Modifier)
            )
        }
        // 3. Màn nhóm.
        val g = shownGroup
        if (g != null && panelP > 0f) GroupPanel(g, panelP, origin, night, blurred, icons, state)
    }
}

/** Chặn chạm xuống Home bên dưới (lớp phủ toàn màn), như `isClickable = true` của View. */
private fun Modifier.blockTouches(enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun LibraryPage(state: HomeLibraryState, icons: HomeIcons, modifier: Modifier) {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val top = with(density) { insets.getTop(this).toDp() }
    val bottom = with(density) { insets.getBottom(this).toDp() }
    BoxWithConstraints(modifier.padding(top = top)) {
        val sz = librarySizes(maxWidth, maxHeight - bottom)
        Column(Modifier.fillMaxSize()) {
            LibrarySearchBar(sz, onClick = state.onSearchClick)
            Spacer(Modifier.height(sz.areaBelowBar))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    null -> DsSpinner(stringResource(R.string.settings_loading), Modifier.align(Alignment.Center))
                    is LibraryContent.Folders ->
                        if (content.groups.isEmpty()) EmptyText(Modifier.align(Alignment.Center))
                        else GroupGrid(content.groups, sz, bottom, icons, state)
                    is LibraryContent.Alphabetical ->
                        if (content.sections.isEmpty()) EmptyText(Modifier.align(Alignment.Center))
                        else AlphabeticalList(content, sz, bottom, icons, state)
                }
            }
        }
        NavFade(bottom, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun EmptyText(modifier: Modifier) {
    BasicText(stringResource(R.string.library_page_empty), modifier, style = TextStyle(color = Color.White, fontSize = 16.sp))
}

/** Dải tối dần ở đáy (AP Task 4): nội dung chạy dưới thanh điều hướng mà chữ ô cuối không cắt cụt. */
@Composable
private fun NavFade(bottom: Dp, modifier: Modifier) {
    if (bottom <= 0.dp || NAV_FADE_MAX_ALPHA <= 0f) return
    Box(
        modifier
            .fillMaxWidth()
            .height(bottom + NAV_FADE_EXTRA)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = NAV_FADE_MAX_ALPHA))))
    )
}

/** Cỡ theo cửa sổ (GL Task 10, `LibraryLayoutMetrics`): màn nhỏ co đều, không nhỏ hơn sàn. */
internal data class LibrarySizes(
    val scale: Float,
    val hInset: Dp,
    val gap: Dp,
    val tilePad: Dp,
    val miniGap: Dp,
    val mini: Dp,
    val labelTop: Dp,
    val radius: Dp,
    val barHeight: Dp,
    val barTop: Dp,
    val areaBelowBar: Dp,
    val barTextSp: Float,
    val labelSp: Float
)

@Composable
private fun librarySizes(width: Dp, height: Dp): LibrarySizes {
    val pagePad = dimensionResource(R.dimen.category_page_padding)
    val baseGap = dimensionResource(R.dimen.category_tile_gap)
    val basePad = dimensionResource(R.dimen.category_tile_padding)
    val baseMiniGap = dimensionResource(R.dimen.category_mini_icon_gap)
    val baseBarH = dimensionResource(R.dimen.search_field_height)
    val baseBarTop = dimensionResource(R.dimen.search_field_margin_top)
    val baseArea = dimensionResource(R.dimen.category_search_area_height)
    val baseRadius = dimensionResource(R.dimen.category_tile_radius)
    return remember(width, height) {
        val s = if (width > 0.dp && height > 0.dp) LibraryLayoutMetrics.scale(width.value, height.value) else 1f
        fun sc(base: Dp, floor: Int): Dp = LibraryLayoutMetrics.scaled(base.value, s, floor.toFloat()).dp
        val content = width - pagePad * 2
        val gap = sc(baseGap, LibraryLayoutMetrics.MIN_TILE_GAP_DP)
        val minContent = (LibraryLayoutMetrics.MIN_TILE_DP.dp + gap * 2) * 2
        val contentEff = if (s >= 1f) content else (content * s).coerceAtLeast(minOf(content, minContent)).coerceAtMost(content)
        val tilePad = sc(basePad, LibraryLayoutMetrics.MIN_TILE_PADDING_DP)
        val miniGap = sc(baseMiniGap, LibraryLayoutMetrics.MIN_MINI_GAP_DP)
        val tile = contentEff / 2 - gap * 2
        val mini = ((tile - tilePad * 2 - miniGap * 4) / 2).coerceAtLeast(1.dp)
        val barH = sc(baseBarH, LibraryLayoutMetrics.MIN_BAR_HEIGHT_DP)
        val barTop = sc(baseBarTop, LibraryLayoutMetrics.MIN_BAR_TOP_DP)
        val barBottom = sc(baseArea - baseBarTop - baseBarH, LibraryLayoutMetrics.MIN_BAR_TOP_DP)
        LibrarySizes(
            scale = s,
            hInset = pagePad + (content - contentEff) / 2,
            gap = gap, tilePad = tilePad, miniGap = miniGap, mini = mini,
            labelTop = sc(LABEL_TOP, LibraryLayoutMetrics.MIN_BAR_TOP_DP),
            radius = sc(baseRadius, LibraryLayoutMetrics.MIN_RADIUS_DP),
            barHeight = barH, barTop = barTop, areaBelowBar = barBottom,
            barTextSp = LibraryLayoutMetrics.scaled(17f, s, LibraryLayoutMetrics.MIN_BAR_TEXT_SP),
            labelSp = LibraryLayoutMetrics.scaled(12f, s, LibraryLayoutMetrics.MIN_LABEL_SP)
        )
    }
}

/** Thanh tìm kiếm trên cùng (bấm mở tìm kiếm chế độ Library); bề mặt `CONTROL` tĩnh (JD D9), co nhẹ khi giữ (AN Task 6). */
@Composable
private fun LibrarySearchBar(sz: LibrarySizes, onClick: () -> Unit) {
    val hint = colorResource(R.color.search_hint_text)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(start = sz.hInset + sz.gap, end = sz.hInset + sz.gap, top = sz.barTop)
            .fillMaxWidth()
            .height(sz.barHeight)
            .dsPressable(interaction)
            .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .dsPressHighlight(interaction)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_search_pill_magnifier), null, colorFilter = ColorFilter.tint(hint), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.search_field_hint), style = TextStyle(color = hint, fontSize = sz.barTextSp.sp), maxLines = 1)
    }
}

/** Lưới 2 cột ô nhóm; ô đầu tới [GLASS_TILE_BUDGET] là kính thật, còn lại bề mặt tĩnh (ngân sách kính, như `GlassBudget`). */
@Composable
private fun GroupGrid(groups: List<CategoryGroupApps>, sz: LibrarySizes, bottom: Dp, icons: HomeIcons, state: HomeLibraryState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sz.hInset, end = sz.hInset, bottom = bottom)
    ) {
        itemsIndexed(groups, key = { _, g -> g.group.name }) { index, entry ->
            CompositionLocalProvider(LocalDsBackdrop provides if (index < GLASS_TILE_BUDGET) LocalDsBackdrop.current else null) {
                GroupTile(entry, sz, icons, state)
            }
        }
    }
}

/**
 * Một ô nhóm: khung 2 × 2 (tối đa 4 icon; nhóm > 4 app thì góc thứ tư là cụm 2 × 2 icon mini, không có "+N") và tên nhóm. Bấm icon lớn = mở
 * app ngay (AN Task 5); bấm cụm mini hoặc ô (nhóm ≥ 4 app) = mở màn nhóm. Vị trí khung báo về [HomeLibraryState.tileRects] để màn nhóm
 * phình ra từ đúng ô.
 */
@Composable
private fun GroupTile(entry: CategoryGroupApps, sz: LibrarySizes, icons: HomeIcons, state: HomeLibraryState) {
    val apps = entry.apps
    val label = stringResource(categoryGroupLabelRes(entry.group))
    val density = LocalDensity.current
    val miniPx = with(density) { sz.mini.roundToPx() }
    val bigCount = if (apps.size <= MAX_SLOTS) apps.size else MAX_SLOTS - 1
    val miniCount = (apps.size - bigCount).coerceIn(0, MAX_SLOTS)
    val interaction = remember { MutableInteractionSource() }
    val groupClick = if (apps.size >= MAX_SLOTS) ({ state.onGroupClick(entry.group) }) else null
    Column(
        Modifier
            .padding(sz.gap)
            .fillMaxWidth()
            .then(if (groupClick != null) Modifier.dsPressable(interaction).clickable(interaction, indication = null, onClick = groupClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { c ->
                    val b = c.boundsInRoot()
                    state.tileRects[entry.group] = ScreenRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                }
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(sz.radius.value))
                .padding(sz.tilePad)
        ) {
            for (row in 0 until 2) {
                Row {
                    for (col in 0 until 2) {
                        val slot = row * 2 + col
                        Box(Modifier.padding(sz.miniGap).size(sz.mini)) {
                            when {
                                slot < bigCount -> BigIcon(apps[slot], miniPx, icons, state)
                                slot == MAX_SLOTS - 1 && miniCount > 0 -> MiniCluster(apps.drop(bigCount).take(miniCount), sz, icons, label) {
                                    state.onGroupClick(entry.group)
                                }
                            }
                        }
                    }
                }
            }
        }
        BasicText(
            label,
            style = TextStyle(
                color = Color.White,
                fontSize = sz.labelSp.sp,
                textAlign = TextAlign.Center,
                shadow = Shadow(Color(LABEL_SHADOW), blurRadius = with(density) { dimensionResource(R.dimen.icon_label_shadow_radius).toPx() })
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = sz.labelTop).fillMaxWidth()
        )
    }
}

@Composable
private fun BigIcon(app: AppItem, sizePx: Int, icons: HomeIcons, state: HomeLibraryState) {
    val bitmap = rememberHomeIcon(icons, app, sizePx)
    var rect by remember { mutableStateOf<ScreenRect?>(null) }
    Box(Modifier.fillMaxSize().homeJiggle(state.editing)) { // LD Task 2: rung ở chế độ sửa
        val iconBounds = remember { android.graphics.RectF() }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { c ->
                    val b = c.boundsInRoot()
                    rect = ScreenRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    iconBounds.set(b.left, b.top, b.right, b.bottom)
                }
                .libraryHit(app, state, inGroup = false) { iconBounds }
                .semantics { contentDescription = app.a11yLabel.ifEmpty { app.label } } // C10
                .libraryAppClick(app, state) { rect }
        ) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        LibraryRemoveBadge(app, sizePx, state, Modifier.align(Alignment.TopStart))
    }
}

/**
 * LD Task 2 — bấm một app của Library: ngoài chế độ sửa = mở app; ở chế độ sửa không làm gì. Nhấn giữ KHÔNG ở đây: [LibraryDragGesture] nhận ở
 * Activity (Task 3) và gửi ACTION_CANCEL xuống nên cú bấm không thành.
 */
private fun Modifier.libraryAppClick(app: AppItem, state: HomeLibraryState, rect: () -> ScreenRect?): Modifier =
    clickable(role = Role.Button) {
        if (!state.editing) rect()?.let { state.onAppClick(app, it) }
    }

/**
 * LD Task 3 — ghi vùng chạm của ô app (toạ độ cửa sổ, `boundsInWindow` đã cắt theo khung cuộn nên ô bị cuộn khuất dưới thanh trên không nhận) vào
 * [HomeLibraryState] để [LibraryDragGesture] tra; gỡ khi ô rời composition (danh sách lười). [icon]: khung icon (ảnh nổi khi kéo ra bắt đầu từ đó).
 */
private fun Modifier.libraryHit(app: AppItem, state: HomeLibraryState, inGroup: Boolean, icon: () -> android.graphics.RectF): Modifier = composed {
    val token = remember { Any() }
    val latestApp by rememberUpdatedState(app)
    DisposableEffect(token) { onDispose { state.unregisterAppHit(token) } }
    onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        if (b.isEmpty) state.unregisterAppHit(token)
        else state.registerAppHit(token, HomeLibraryState.AppHit(latestApp, android.graphics.RectF(b.left, b.top, b.right, b.bottom), icon, inGroup))
    }
}

/** LD Task 2 — dấu "−" ở góc trên-trái icon cạnh [iconPx] (icon nằm ở góc trên-trái của cha), chỉ ở chế độ sửa; bấm = hỏi gỡ cài đặt. */
@Composable
private fun LibraryRemoveBadge(app: AppItem, iconPx: Int, state: HomeLibraryState, modifier: Modifier = Modifier) {
    if (!state.editing) return
    val c = badgeCenterForIcon(0f, 0f, iconPx)
    HomeRemoveBadge(iconPx, c.x, c.y, stringResource(R.string.remove_app_title, app.label), modifier) { state.onRemoveApp(app) }
}

/** Góc thứ tư: tối đa 4 icon mini 2 × 2; bấm = mở màn nhóm. TalkBack đọc tên nhóm. */
@Composable
private fun MiniCluster(apps: List<AppItem>, sz: LibrarySizes, icons: HomeIcons, label: String, onClick: () -> Unit) {
    val density = LocalDensity.current
    val half = sz.mini / 2
    val inset = (sz.miniGap / 2).coerceAtLeast(1.dp)
    val iconPx = with(density) { (half - inset * 2).coerceAtLeast(1.dp).roundToPx() }
    Column(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label }.clickable(onClick = onClick)) {
        for (row in 0 until 2) {
            Row {
                for (col in 0 until 2) {
                    val app = apps.getOrNull(row * 2 + col)
                    Box(Modifier.size(half).padding(inset)) {
                        if (app != null) {
                            val bitmap = rememberHomeIcon(icons, app, iconPx)
                            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** LO D4 + D10: danh sách A–Z (tiêu đề chữ cái + hàng app) và cột chữ cái kéo nhanh bên phải. */
@Composable
private fun AlphabeticalList(content: LibraryContent.Alphabetical, sz: LibrarySizes, bottom: Dp, icons: HomeIcons, state: HomeLibraryState) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Chỉ số hàng đầu của mỗi đoạn (tiêu đề chữ cái) trong danh sách phẳng.
    val starts = remember(content) {
        var i = 0
        content.sections.map { s -> i.also { i += 1 + s.apps.size } }
    }
    val indexWidth = dimensionResource(R.dimen.library_letter_index_width)
    val iconPx = with(LocalDensity.current) { dimensionResource(R.dimen.library_list_icon_size).roundToPx() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sz.hInset + sz.gap, end = sz.hInset + indexWidth, bottom = bottom)
        ) {
            content.sections.forEach { section ->
                item(key = "letter:${section.letter}", contentType = "letter") {
                    BasicText(
                        section.letter,
                        style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, shadow = LIST_SHADOW),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp).semantics { heading() }
                    )
                }
                items(section.apps, key = { "app:${it.key.packageName}/${it.key.className}#${it.key.userSerial}" }, contentType = { "app" }) { app ->
                    ListAppRow(app, iconPx, icons, state)
                }
            }
        }
        LetterIndex(
            letters = content.sections.map { it.letter },
            onSelect = { i -> starts.getOrNull(i)?.let { scope.launch { listState.scrollToItem(it) } } },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = sz.hInset, bottom = bottom).width(indexWidth).fillMaxSize()
        )
    }
}

@Composable
private fun ListAppRow(app: AppItem, iconPx: Int, icons: HomeIcons, state: HomeLibraryState) {
    val bitmap = rememberHomeIcon(icons, app, iconPx)
    var rect by remember { mutableStateOf<ScreenRect?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val iconBounds = remember { android.graphics.RectF() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .libraryHit(app, state, inGroup = false) { iconBounds } // LD Task 3: cả hàng là vùng nhấn giữ
            .clickable(interaction, indication = null, role = Role.Button) {
                if (!state.editing) rect?.let { state.onAppClick(app, it) } // LD Task 2: ở chế độ sửa bấm không mở app
            }
            .dsPressHighlight(interaction)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(dimensionResource(R.dimen.library_list_icon_size))
                .homeJiggle(state.editing)
                .onGloballyPositioned { c ->
                    val b = c.boundsInRoot()
                    rect = ScreenRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    iconBounds.set(b.left, b.top, b.right, b.bottom)
                }
        ) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            LibraryRemoveBadge(app, iconPx, state, Modifier.align(Alignment.TopStart))
        }
        BasicText(
            app.label,
            style = TextStyle(color = Color.White, fontSize = 17.sp, shadow = LIST_SHADOW),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
    }
}

/**
 * LO D10 — cột chữ cái như danh bạ: chạm hoặc kéo dọc lên một chữ thì danh sách nhảy tới đoạn đó, rung nhẹ mỗi lần đổi chữ, chữ đang chọn
 * tô `ds_accent`. Chữ dàn đều, mỗi chữ tối đa 18 dp, cụm căn giữa. Không vào cây trợ năng (TalkBack cuộn bằng chính danh sách).
 */
@Composable
private fun LetterIndex(letters: List<String>, onSelect: (Int) -> Unit, modifier: Modifier) {
    if (letters.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val accent = colorResource(R.color.ds_accent)
    var active by remember { mutableIntStateOf(-1) }
    val maxStepPx = with(LocalDensity.current) { 18.dp.toPx() }
    val textSizePx = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Canvas(
        modifier
            .clearAndSetSemantics { }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(y: Float) {
                        val step = minOf(size.height.toFloat() / letters.size, maxStepPx)
                        val top = (size.height - step * letters.size) / 2f
                        val i = ((y - top) / step).toInt().coerceIn(0, letters.lastIndex)
                        if (i != active) {
                            active = i
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        }
                    }
                    pick(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        pick(change.position.y)
                        change.consume()
                    }
                    active = -1
                }
            }
    ) {
        paint.textSize = textSizePx
        paint.setShadowLayer(4.dp.toPx(), 0f, 0f, LABEL_SHADOW)
        val step = minOf(size.height / letters.size, maxStepPx)
        val top = (size.height - step * letters.size) / 2f
        val fm = paint.fontMetrics
        drawIntoCanvasCompat { canvas ->
            letters.forEachIndexed { i, letter ->
                paint.color = if (i == active) android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt()) else LETTER_COLOR
                val cy = top + step * (i + 0.5f)
                canvas.drawText(letter, size.width / 2f, cy - (fm.ascent + fm.descent) / 2f, paint)
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasCompat(block: (android.graphics.Canvas) -> Unit) {
    block(drawContext.canvas.nativeCanvas)
}

/**
 * G2a B3 + AP vòng 2 — màn nhóm toàn màn (video iOS 27): nền ảnh nền mờ + tối nhẹ hiện theo [p]; tiêu đề lớn đậm góc trái và lưới 4 cột
 * (icon 56, tên 12sp) phình ra từ GIỮA màn hình (P2-3, trước đó từ khung 2 × 2 của ô được bấm), khối ôm nội dung căn giữa dọc; bấm app = mở và
 * đóng nhóm; bấm ngoài lưới = đóng.
 */
@Composable
private fun GroupPanel(
    group: CategoryGroupApps,
    p: Float,
    origin: ScreenRect?,
    night: Boolean,
    blurred: Bitmap?,
    icons: HomeIcons,
    state: HomeLibraryState
) {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val top = with(density) { insets.getTop(this).toDp() }
    val bottom = with(density) { insets.getBottom(this).toDp() }
    val side = dimensionResource(R.dimen.category_group_side_margin)
    val iconPx = with(density) { dimensionResource(R.dimen.category_group_icon_size).roundToPx() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.coerceIn(0f, 1f) }
            .background(Color(if (night) SCRIM_FLAT_DARK else SCRIM_FLAT_LIGHT))
    ) {
        if (blurred != null) {
            Image(blurred.asImageBitmap(), null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize().wallpaperSoftBlur())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (night) SCRIM_TINT_DARK else SCRIM_TINT_LIGHT)))
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .blockTouches(enabled = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.onCloseGroup() }
    ) {
        // P2-3 (ảnh app mẫu 2026-11-19): mép trái tiêu đề thẳng mép trái icon cột đầu. Icon nằm giữa cột của lưới GROUP_COLUMNS cột (lưới đệm
        // [side] hai bên), nên lề = side + nửa phần dư của cột (CategoryGroupPanelView.alignTitleWithIcons).
        val iconSize = dimensionResource(R.dimen.category_group_icon_size)
        val column = (maxWidth - side * 2) / GROUP_COLUMNS
        val titleStart = side + ((column - iconSize) / 2).coerceAtLeast(0.dp)
        val topPad = top + dimensionResource(R.dimen.category_group_title_margin_top)
        Column(
            Modifier
                .fillMaxWidth()
                // P2-3: khối tiêu đề + lưới ôm nội dung và căn GIỮA theo chiều dọc; nhóm nhiều app thì khối cao tới trần (lưới cuộn) nên vẫn bắt
                // đầu ngay dưới thanh trạng thái như trước.
                .heightIn(max = maxHeight)
                .align(Alignment.Center)
                .graphicsLayer {
                    // P2-3 (chủ dự án 2026-11-19): mở ra từ GIỮA màn hình và thu về giữa (trước P2-3: mọc từ ô nhóm, đóng về ô); [origin] không còn dùng.
                    val c = p.coerceIn(0f, 1.05f)
                    alpha = ramp(p, 0f, PANEL_FADE_END)
                    val s0 = GROUP_CENTER_START_SCALE
                    val s = s0 + (1f - s0) * c
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .padding(top = topPad)
        ) {
            BasicText(
                stringResource(categoryGroupLabelRes(group.group)),
                style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.padding(start = titleStart, end = titleStart).semantics { heading() }
            )
            Spacer(Modifier.height(dimensionResource(R.dimen.category_group_list_margin_top)))
            LazyVerticalGrid(
                columns = GridCells.Fixed(GROUP_COLUMNS),
                contentPadding = PaddingValues(start = side, end = side, bottom = bottom),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                itemsIndexed(group.apps, key = { _, a -> "${a.key.packageName}/${a.key.className}#${a.key.userSerial}" }) { _, app ->
                    GroupApp(app, iconPx, icons, state)
                }
            }
        }
    }
}

@Composable
private fun GroupApp(app: AppItem, iconPx: Int, icons: HomeIcons, state: HomeLibraryState) {
    val bitmap = rememberHomeIcon(icons, app, iconPx)
    var rect by remember { mutableStateOf<ScreenRect?>(null) }
    val iconBounds = remember { android.graphics.RectF() }
    Column(
        Modifier
            .libraryHit(app, state, inGroup = true) { iconBounds } // LD Task 3
            .clickable(role = Role.Button) {
                if (!state.editing) rect?.let { // LD Task 2: ở chế độ sửa bấm không mở app
                    state.onAppClick(app, it)
                    state.onCloseGroup()
                }
            }
            .padding(dimensionResource(R.dimen.category_group_item_padding)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(dimensionResource(R.dimen.category_group_icon_size))
                .homeJiggle(state.editing)
                .onGloballyPositioned { c ->
                    val b = c.boundsInRoot()
                    rect = ScreenRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    iconBounds.set(b.left, b.top, b.right, b.bottom)
                }
        ) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            LibraryRemoveBadge(app, iconPx, state, Modifier.align(Alignment.TopStart))
        }
        BasicText(
            app.label,
            style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
        )
    }
}

/** Ảnh nền thu nhỏ để phóng lên thành nền mờ (rẻ, mọi API): cạnh dài ~48 px, lọc song tuyến khi thu và khi vẽ. TV: màn Today dùng chung. */
internal fun shrinkForBlur(src: Bitmap): Bitmap {
    val longSide = maxOf(src.width, src.height).coerceAtLeast(1)
    val f = BLUR_LONG_SIDE_PX.toFloat() / longSide
    if (f >= 1f) return src
    return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true)
}

private fun ramp(p: Float, start: Float, end: Float): Float =
    if (end <= start) (if (p >= end) 1f else 0f) else ((p - start) / (end - start)).coerceIn(0f, 1f)

private val GROUP_SPRING = spring<Float>(dampingRatio = 0.82f, stiffness = 450f) // AppLibraryMotion.GROUP_PANEL_SPRING
private const val OPEN_EPS = 0.001f
private const val PANEL_FADE_END = 0.3f // AppLibraryMotion.GROUP_PANEL_FADE_END
private const val GROUP_CENTER_START_SCALE = 0.6f // AppLibraryMotion.GROUP_PANEL_CENTER_START_SCALE (P2-3)
private const val BEHIND_END_SCALE = 0.85f // AppLibraryMotion.LIBRARY_BEHIND_END_SCALE
private const val BEHIND_FADE_END = 0.7f // AppLibraryMotion.LIBRARY_BEHIND_FADE_END
// TV: màn Today dùng chung bốn hằng nền này (cùng độ tối với Library).
internal const val SCRIM_TINT_DARK = 0.40f
internal const val SCRIM_TINT_LIGHT = 0.25f
internal const val SCRIM_FLAT_DARK = 0xFF1C1C1E
internal const val SCRIM_FLAT_LIGHT = 0xFF3A3A3C
private const val NAV_FADE_MAX_ALPHA = 0.6f
private val NAV_FADE_EXTRA = 24.dp
private val LABEL_TOP = 8.dp
private const val MAX_SLOTS = 4
private const val GROUP_COLUMNS = 4
private const val GLASS_TILE_BUDGET = 8
private const val BLUR_LONG_SIDE_PX = 96
private const val LABEL_SHADOW = 0x80000000.toInt()
private const val LETTER_COLOR = 0xCCFFFFFF.toInt()
private val LIST_SHADOW = Shadow(Color(0x80000000), Offset.Zero, 4f)
