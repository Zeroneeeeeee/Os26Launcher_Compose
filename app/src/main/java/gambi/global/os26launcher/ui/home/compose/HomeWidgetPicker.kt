package gambi.global.os26launcher.ui.home.compose

import android.graphics.Bitmap
import android.text.format.DateFormat
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.appwidget.AppWidgetAssets
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheetLayer
import gambi.global.os26launcher.core.designsystem.compose.DsSearchField
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.layout.AppWidgetSizePlanner
import gambi.global.os26launcher.domain.model.AppWidgetAppEntry
import gambi.global.os26launcher.domain.model.AppWidgetProviderEntry
import gambi.global.os26launcher.domain.model.WidgetKind
import gambi.global.os26launcher.domain.model.WidgetSize
import gambi.global.os26launcher.domain.repository.AppWidgetRepository
import gambi.global.os26launcher.domain.today.TodayWidget
import gambi.global.os26launcher.ui.home.HomeViewModel
import gambi.global.os26launcher.ui.home.compose.today.TodayRecentsCard
import gambi.global.os26launcher.ui.widget.WidgetRenderers
import gambi.global.os26launcher.ui.widget.host.AppWidgetAddRequest
import java.text.Normalizer
import java.time.ZonedDateTime
import kotlin.math.abs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P2-9 (REF a013dd8 `WidgetPickerController`) — bảng chọn widget kiểu iOS của Home Compose, qua host hộp thoại của Activity (bottom sheet DS3).
 *
 * Trang danh sách (cao 85% màn): ô tìm (lọc không phân biệt hoa thường / dấu), lưới gợi ý (mỗi loại widget tự vẽ một ô ở cỡ 2 × 2, xếp đôi; bảng của
 * Today thêm "App gần đây" 4 × 2 ở hàng dưới), rồi danh sách app có widget (A–Z, bỏ gói chỉ còn app đã ẩn — P3-7). Bấm một ô / một app mở trang
 * chi tiết (bottom sheet thứ hai đè lên): pager các cỡ (hoặc các widget của app) nghiêng 3D theo độ vuốt, chấm trang, nút "Thêm tiện ích" xanh hệ
 * thống. Thêm xong thì đóng cả hai bảng.
 *
 * TV-3: [show] với `forToday = true` (viên "+" của Today): có "App gần đây", KHÔNG có widget của app khác, "Thêm" ghi vào CUỐI Today.
 *
 * PG2 (F2, hướng B, chủ dự án 2026-11-23): bảng không còn là cửa sổ `Dialog` mà là lớp phủ vẽ bằng [Render] trong cây Compose của `HomeActivity`
 * (`DsBottomSheetLayer`), nên ở trang chi tiết, nhấn giữ khung xem trước của widget tự vẽ rồi kéo ([pickerCardDrag]) thì chính chuỗi chạm đó lái
 * phiên kéo "widget mới" của `HomeDragState` (REF `CardDragGesture` + `beginNewWidgetDrag`, G2c Task 13): bảng mờ đi, ảnh widget đúng cỡ ô theo
 * ngón tay, thả lên lưới thì `HomeViewModel.onNewWidgetDropped`; xong (nhận hay không) đóng cả hai bảng. Widget của app khác vẫn chỉ "Thêm tiện ích"
 * (như XML: luồng gắn / cấu hình của hệ thống).
 */
internal class HomeWidgetPicker(
    private val activity: ComponentActivity,
    private val viewModel: HomeViewModel,
    private val pagerInfo: HomePagerInfo,
    private val repository: AppWidgetRepository,
    private val assets: AppWidgetAssets,
    private val icons: HomeIcons,
    private val startAppWidget: (AppWidgetAddRequest) -> Boolean,
    /** P3-7: gói chỉ còn app đã ẩn (packageName, userSerial): widget của chúng không được lộ trong bảng chọn. */
    private val hiddenOnlyPackages: suspend () -> Set<Pair<String, Long>> = { emptySet() }
) {
    // PG2: trạng thái của lớp phủ (trước đây là hai DsDialogHandle). Đọc trong [Render].
    private var open by mutableStateOf(false)
    private var detail by mutableStateOf<DetailContent?>(null)

    /** PG2: đang kéo một thẻ ra lưới: bảng mờ về 0 (Q4) nhưng vẫn ở trong composition để cử chỉ của thẻ không bị huỷ. */
    private var dragging by mutableStateOf(false)
    private var tempIdSeq = 0L

    /** TV-3: bảng mở từ viên "+" của Today View. */
    private var forToday = false

    /** PG2: nội dung trang chi tiết đang mở (trước đây dựng thẳng trong `dsDialogs.show`). */
    private class DetailContent(
        val name: @Composable () -> String,
        val icon: @Composable (Modifier) -> Unit,
        val pages: List<DetailPage>,
        val start: Int
    )

    /** Một ô gợi ý của bảng: [kind] null = widget "App gần đây" (TV-3). */
    private data class Featured(val kind: WidgetKind?, val size: WidgetSize)

    /** P2-9e: mục "Thêm tiện ích" của menu "Sửa" (Home); TV-3: viên "+" của Today ([forToday] = true). */
    fun show(forToday: Boolean = false) {
        if (open) return
        this.forToday = forToday
        detail = null
        dragging = false
        open = true
    }

    /**
     * PG2: vẽ bảng (gọi một lần trong `setContent` của `HomeActivity`, trên các lớp khác). Trang chi tiết đè lên trang danh sách; Back đóng lớp trên
     * cùng trước (BackHandler của lớp khai báo sau được hỏi trước). Cùng cửa sổ Home nên edit mode đã ẩn thanh trạng thái (C19 không cần xử lý riêng).
     */
    @Composable
    fun Render() {
        if (!open) return
        val fade by animateFloatAsState(if (dragging) 0f else 1f, tween(DRAG_FADE_MS), label = "pickerFade")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }) {
            DsBottomSheetLayer(onDismiss = ::closeAll, showClose = false) { Gallery() }
            val d = detail
            if (d != null) {
                androidx.compose.runtime.key(d) {
                    DsBottomSheetLayer(onDismiss = { if (detail === d) detail = null }, showClose = false) {
                        Detail(d.name, d.icon, d.pages, d.start.coerceIn(0, d.pages.lastIndex), onClose = { if (detail === d) detail = null })
                    }
                }
            }
            // PG2 (ngón thứ hai, chủ dự án 2026-11-23): đang kéo thì bảng chỉ mờ về 0 nhưng vẫn phủ màn và nhận chạm; ngón MỚI chạm lớp mờ /
            // thân sheet sẽ đóng bảng → cử chỉ của thẻ bị huỷ → widget mất. Lớp trên cùng này nuốt mọi ngón mới; ngón đang kéo đã gắn với khung
            // xem trước từ lúc nhấn xuống nên không đi qua đây.
            if (dragging) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                )
            }
        }
    }

    /** `HomeActivity`: thoát edit mode (của Home lẫn của Today) thì đóng bảng. */
    fun onEditMode(editMode: Boolean) {
        if (!editMode) closeIfOpen()
    }

    /** B10 (FX2): nút Home và thoát edit mode đóng bảng. An toàn khi gọi nhiều lần. P2-9: đóng cả bảng chi tiết. */
    fun closeIfOpen() {
        detail = null
        open = false
        dragging = false
    }

    /** Thêm xong: đóng cả hai bảng. */
    private fun closeAll() = closeIfOpen()

    /** Cỡ ô lưới hiện hành (dp) cho [AppWidgetSizePlanner]; null khi chưa đo được. */
    private fun grid(): AppWidgetSizePlanner.Grid? {
        val state = viewModel.uiState.value
        val density = activity.resources.displayMetrics.density
        val c = GridMetrics.from(state.grid, state.showLabels).measure(pagerInfo.pagerWidth, pagerInfo.pagerHeight, density) ?: return null
        if (c.cellWidth <= 0 || c.cellHeight <= 0 || density <= 0f) return null
        return AppWidgetSizePlanner.Grid(c.cellWidth / density, c.cellHeight / density, c.gutterX / density, c.gutterY / density)
    }

    // PG2: `HideStatusBarOfSheet` (C19) bỏ: bảng vẽ trong cửa sổ Home, edit mode của Home đã ẩn thanh trạng thái.

    // ---- P2-9a: trang danh sách (tìm kiếm, lưới gợi ý, danh sách nguồn) ----

    @Composable
    private fun Gallery() {
        val sheetH = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * SHEET_HEIGHT_FRACTION).toDp() }
        var query by remember { mutableStateOf("") }
        val q = normalize(query)
        val context = androidx.compose.ui.platform.LocalResources.current
        Column(Modifier.fillMaxWidth().height(sheetH).padding(top = 12.dp)) {
            DsSearchField(query, { query = it }, stringResource(R.string.wp_search_hint), Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = GAP)) {
                // Lưới gợi ý: ô 2×2 xếp đôi, ô 4×2 chiếm cả hàng; hàng không còn ô nào khớp chuỗi tìm thì ẩn cả hàng.
                val featured = if (forToday) FEATURED + FEATURED_TODAY_EXTRA else FEATURED
                val visible = featured.filter { f ->
                    q.isEmpty() || listOf(context.getString(kindNameRes(f.kind)), context.getString(variantTitleRes(f.kind, f.size)))
                        .any { normalize(it).contains(q) }
                }
                packFeatured(visible).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = GAP * 2), horizontalArrangement = Arrangement.spacedBy(GAP * 2)) {
                        row.forEach { f -> FeaturedCell(f, Modifier.weight(if (f.size.spanX >= 4) 2f else 1f)) }
                        if (row.size == 1 && row[0].size.spanX < 4) Spacer(Modifier.weight(1f)) // ô 2×2 lẻ: nửa phải trống
                    }
                }
                // Danh sách nguồn: app có widget (A–Z, tải nền). TV-3: Today chỉ nhận widget tự vẽ (chủ dự án 2026-11-20).
                if (!forToday && repository.isSupported) AppList(q)
            }
        }
    }

    /** Xếp ô gợi ý thành hàng như REF `buildGallery`: 4×2 một hàng riêng; 2×2 ghép đôi theo thứ tự. */
    private fun packFeatured(list: List<Featured>): List<List<Featured>> {
        val rows = ArrayList<List<Featured>>()
        var pending: Featured? = null
        for (f in list) {
            if (f.size.spanX >= 4) {
                pending?.let { rows.add(listOf(it)) }
                pending = null
                rows.add(listOf(f))
            } else if (pending == null) {
                pending = f
            } else {
                rows.add(listOf(pending, f))
                pending = null
            }
        }
        pending?.let { rows.add(listOf(it)) }
        return rows
    }

    /** Ô gợi ý: bản xem trước + tên loại + tên biến thể. Bấm -> trang chi tiết đúng cỡ đó. */
    @Composable
    private fun FeaturedCell(f: Featured, modifier: Modifier) {
        val name = stringResource(kindNameRes(f.kind))
        val variant = stringResource(variantTitleRes(f.kind, f.size))
        val primary = colorResource(R.color.ds_text_primary)
        Column(
            modifier
                .semantics(mergeDescendants = true) { contentDescription = "$name, $variant" }
                .clickable(role = Role.Button) {
                    openDetail(
                        if (f.kind == null) DetailSource.Recents(RECENTS_SIZES.indexOf(f.size).coerceAtLeast(0))
                        else DetailSource.Builtin(f.kind, f.kind.sizes.indexOf(f.size).coerceAtLeast(0))
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Preview(f.kind, f.size, Modifier.fillMaxWidth())
            BasicText(
                name,
                style = TextStyle(color = primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = GAP)
            )
            BasicText(variant, style = TextStyle(color = primary.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }

    @Composable
    private fun AppList(q: String) {
        val apps by produceState<List<AppWidgetAppEntry>?>(null) {
            val hidden = hiddenOnlyPackages() // P3-7
            value = repository.catalog()
                .filterNot { (it.packageName to it.userSerial) in hidden }
                .sortedBy { it.appLabel.lowercase() }
        }
        apps.orEmpty().filter { q.isEmpty() || normalize(it.appLabel).contains(q) }.forEach { app ->
            androidx.compose.runtime.key(app.packageName, app.userSerial) { ProviderRow(app) }
        }
    }

    /** Hàng nguồn: icon + tên + gạch dưới; cao ≥ 56 dp. */
    @Composable
    private fun ProviderRow(app: AppWidgetAppEntry) {
        val iconPx = with(LocalDensity.current) { ROW_ICON.roundToPx() }
        val icon by produceState<Bitmap?>(null, app.packageName) { value = assets.appIcon(app.packageName, iconPx) }
        val primary = colorResource(R.color.ds_text_primary)
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .semantics(mergeDescendants = true) { contentDescription = app.appLabel }
                    .clickable(role = Role.Button) { openDetail(DetailSource.App(app)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(ROW_ICON)) { icon?.let { Image(it.asImageBitmap(), null, modifier = Modifier.size(ROW_ICON)) } }
                Spacer(Modifier.width(GAP * 2))
                BasicText(
                    app.appLabel,
                    style = TextStyle(color = primary, fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.padding(start = ROW_ICON + GAP * 2).fillMaxWidth().height(0.5.dp).background(primary.copy(alpha = 0.12f)))
        }
    }

    /**
     * Bản xem trước trong bảng: widget Ảnh dùng một hình nền có sẵn của app làm ảnh mẫu; "App gần đây" vẽ bằng dữ liệu THẬT (app mở gần đây, nền phẳng
     * `widget_preview_bg`, không bấm được); loại khác vẽ bằng chính `WidgetRenderer` (dữ liệu mẫu). Cao theo tỉ lệ cỡ.
     */
    @Composable
    private fun Preview(kind: WidgetKind?, size: WidgetSize, modifier: Modifier) {
        val ratio = size.spanX.toFloat() / size.spanY
        val bg = colorResource(R.color.widget_preview_bg)
        when (kind) {
            null -> Box(modifier.aspectRatio(ratio)) {
                val apps by viewModel.todayApps.collectAsState()
                val ui by viewModel.uiState.collectAsState()
                TodayRecentsCard(
                    columns = if (size.spanX >= 4) 4 else 2,
                    apps = apps ?: emptyList(),
                    icons = icons,
                    showLabels = ui.showLabels,
                    interactive = false,
                    flatColor = bg
                )
            }

            WidgetKind.PHOTO -> Box(modifier.aspectRatio(ratio)) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    Image(
                        painterResource(R.drawable.default_wallpaper_ios27),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(minOf(maxWidth, maxHeight) * 0.12f))
                    )
                }
            }

            else -> RendererPreview(kind, size, modifier.aspectRatio(ratio))
        }
    }

    @Composable
    private fun RendererPreview(kind: WidgetKind, size: WidgetSize, modifier: Modifier) {
        val context = LocalContext.current
        val renderer = remember(kind, size) { WidgetRenderers.create(context, kind, size.spanX, size.spanY, sample = true) }
        val bg = colorResource(R.color.widget_preview_bg)
        val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
        var prepared by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        Canvas(modifier) {
            val w = this.size.width.toInt()
            val h = this.size.height.toInt()
            if (w > 0 && h > 0 && prepared != androidx.compose.ui.unit.IntSize(w, h)) {
                renderer.prepare(w, h, density, ZonedDateTime.now(), locale, DateFormat.is24HourFormat(context))
                prepared = androidx.compose.ui.unit.IntSize(w, h)
            }
            val r = minOf(this.size.width, this.size.height) * 0.12f
            drawRoundRect(bg, cornerRadius = CornerRadius(r, r))
            drawIntoCanvas { renderer.draw(it.nativeCanvas) }
        }
    }

    // ---- P2-9b/9c: trang chi tiết (bottom sheet thứ hai đè lên bảng danh sách) ----

    private sealed class DetailSource {
        class Builtin(val kind: WidgetKind, val startIndex: Int = 0) : DetailSource()
        class Recents(val startIndex: Int = 0) : DetailSource() // TV-3
        class App(val app: AppWidgetAppEntry) : DetailSource()
    }

    /** Một trang của pager chi tiết. [add] null = không thêm được (widget app quá lớn); trả true nếu đã thêm / luồng thêm đã bắt đầu. */
    private class DetailPage(
        val title: @Composable () -> String,
        val description: @Composable () -> String,
        val preview: @Composable (Modifier) -> Unit,
        /** Cỡ của widget tự vẽ (bề ngang khung xem trước theo cỡ); null = widget của app khác (ảnh xem trước tự đo). */
        val previewSize: WidgetSize?,
        val add: (() -> Boolean)?,
        /** PG2: loại widget tự vẽ kéo được ra lưới từ khung xem trước (cỡ = [previewSize]); null = không kéo (Today, "App gần đây", widget app). */
        val dragKind: WidgetKind? = null
    )

    private fun openDetail(source: DetailSource) {
        if (detail != null) return
        val start: Int
        val pages: List<DetailPage>
        val name: @Composable () -> String
        val icon: @Composable (Modifier) -> Unit
        when (source) {
            is DetailSource.Builtin -> {
                val kind = source.kind
                start = source.startIndex
                name = { stringResource(kindNameRes(kind)) }
                icon = { m -> Preview(kind, WidgetSize(2, 2), m) }
                pages = kind.sizes.map { size ->
                    DetailPage(
                        title = { stringResource(variantTitleRes(kind, size)) },
                        description = { stringResource(variantDescRes(kind, size)) },
                        preview = { m -> Preview(kind, size, m) },
                        previewSize = size,
                        dragKind = if (forToday) null else kind, // PG2 (QĐ 3): bảng của Today không kéo ra lưới
                        add = {
                            if (forToday) viewModel.addTodayWidget(kind.id, size.spanX >= 4) // TV-3: thêm vào cuối Today
                            else viewModel.onAddWidget(kind, size.spanX, size.spanY, pagerInfo.currentPage)
                            true
                        }
                    )
                }
            }

            is DetailSource.Recents -> {
                // TV-3: widget "App gần đây" (chỉ Today): hai cỡ 2 × 2 (4 app) và 4 × 2 (8 app), xem trước bằng dữ liệu thật.
                start = source.startIndex
                name = { stringResource(kindNameRes(null)) }
                icon = { m -> Preview(null, WidgetSize(2, 2), m) }
                pages = RECENTS_SIZES.map { size ->
                    DetailPage(
                        title = { stringResource(variantTitleRes(null, size)) },
                        description = { stringResource(variantDescRes(null, size)) },
                        preview = { m -> Preview(null, size, m) },
                        previewSize = size,
                        add = {
                            viewModel.addTodayWidget(TodayWidget.RECENTS, size.spanX >= 4)
                            true
                        }
                    )
                }
            }

            is DetailSource.App -> {
                val app = source.app
                start = 0
                name = { app.appLabel }
                icon = { m ->
                    val px = with(LocalDensity.current) { HEADER_ICON.roundToPx() }
                    val bmp by produceState<Bitmap?>(null, app.packageName) { value = assets.appIcon(app.packageName, px) }
                    Box(m) { bmp?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) } }
                }
                val g = grid()
                pages = app.providers.map { provider ->
                    val size = if (g != null) AppWidgetSizePlanner.plan(provider, g).firstOrNull() else null
                    DetailPage(
                        title = { app.appLabel },
                        description = {
                            if (size != null) stringResource(R.string.widget_size_label, size.spanX, size.spanY)
                            else stringResource(R.string.aw_too_large)
                        },
                        preview = { m -> AppPreview(provider, m) },
                        previewSize = null,
                        add = size?.let { sz -> { startAppWidget(provider, sz) } }
                    )
                }
            }
        }
        if (pages.isEmpty()) return
        detail = DetailContent(name, icon, pages, start)
    }

    @Composable
    private fun AppPreview(provider: AppWidgetProviderEntry, modifier: Modifier) {
        val density = LocalDensity.current
        val maxW = with(density) { PREVIEW_APP_MAX_W.roundToPx() }
        val maxH = with(density) { PREVIEW_APP_MAX_H.roundToPx() }
        val bmp by produceState<Bitmap?>(null, provider.ref) { value = assets.preview(provider.ref, maxW, maxH) }
        Box(modifier.widthIn(max = PREVIEW_APP_MAX_W).heightIn(max = PREVIEW_APP_MAX_H)) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit) }
        }
    }

    @Composable
    private fun Detail(
        name: @Composable () -> String,
        icon: @Composable (Modifier) -> Unit,
        pages: List<DetailPage>,
        start: Int,
        onClose: () -> Unit
    ) {
        val primary = colorResource(R.color.ds_text_primary)
        val pager = rememberPagerState(initialPage = start) { pages.size }
        // P2-9d: vào trang chi tiết, khung xem trước phóng nhẹ từ nhỏ ra.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(ENTER_MS, easing = DecelerateEasing)) }
        Column(Modifier.fillMaxWidth().padding(top = GAP)) {
            // Đầu trang: icon + tên + nút đóng (vùng chạm 48 dp, nút tròn 32 dp như DsBottomSheet).
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon(Modifier.size(HEADER_ICON))
                BasicText(
                    name(),
                    style = TextStyle(color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = GAP * 2)
                )
                val closeLabel = stringResource(R.string.wp_close)
                Box(
                    Modifier
                        .size(TOUCH)
                        .semantics { contentDescription = closeLabel }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(32.dp).background(colorResource(R.color.ds_surface_control), CircleShape), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ds_ic_close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(primary),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // Pager các cỡ: tiêu đề + mô tả trên, ảnh xem trước giữa; nghiêng 3D theo độ vuốt (P2-9b, chỉnh tay theo video).
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val previewW = maxWidth
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PAGER_HEIGHT)
                        .graphicsLayer {
                            val s = ENTER_SCALE + (1f - ENTER_SCALE) * enter.value
                            scaleX = s
                            scaleY = s
                            alpha = enter.value
                        }
                ) { index ->
                    val page = pages[index]
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = ((pager.currentPage - index) + pager.currentPageOffsetFraction).let { -it }.coerceIn(-1f, 1f)
                                cameraDistance = size.width * CAMERA_DISTANCE_FACTOR / density // View: px; Compose nhân với mật độ
                                transformOrigin = TransformOrigin(if (p < 0) 1f else 0f, 0.5f)
                                rotationY = -p * TILT_DEG
                                val sc = 1f - abs(p) * TILT_SCALE
                                scaleX = sc
                                scaleY = sc
                                alpha = 1f - abs(p) * TILT_FADE
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BasicText(
                            page.title(),
                            style = TextStyle(color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(top = GAP * 2, bottom = GAP / 2)
                        )
                        BasicText(
                            page.description(),
                            style = TextStyle(color = primary.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        )
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            val shown = page.previewSize
                            val m = if (shown != null) {
                                Modifier.width(previewW * if (shown.spanX >= 4) PREVIEW_WIDE_FRACTION else PREVIEW_SMALL_FRACTION)
                            } else Modifier
                            val kind = page.dragKind
                            page.preview(if (kind != null && shown != null) m.pickerCardDrag(kind, shown) else m)
                        }
                    }
                }
            }
            // Chấm trang (ẩn khi chỉ một trang).
            Row(
                Modifier.fillMaxWidth().height(24.dp).graphicsLayer { alpha = if (pages.size > 1) 1f else 0f },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(7.dp)
                            .background(primary.copy(alpha = if (i == pager.currentPage) 0.9f else 0.25f), CircleShape)
                    )
                }
            }
            // Nút thêm: luôn xanh hệ thống (chủ dự án chốt), cao 52 dp; chữ đậm 16 sp (chữ lớn WCAG).
            val action = pages[pager.currentPage].add
            Row(
                Modifier
                    .padding(top = GAP)
                    .fillMaxWidth()
                    .height(ADD_HEIGHT)
                    .graphicsLayer { alpha = if (action != null) 1f else 0.4f }
                    .clip(RoundedCornerShape(26.dp))
                    .background(colorResource(R.color.wp_add_blue))
                    .clickable(enabled = action != null, role = Role.Button) { if (action?.invoke() == true) closeAll() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.wp_ic_add), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicText(stringResource(R.string.wp_add_widget), style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    /**
     * PG2 — cử chỉ trên khung xem trước ở trang chi tiết (REF `CardDragGesture`): giữ đủ `longPressTimeout` trong touch slop thì rung và "arm"; từ đó
     * tiêu thụ mọi sự kiện (khung không cuộn pager / kéo sheet, nhả tay không di thì không làm gì). Di quá slop sau khi arm thì mở phiên kéo widget
     * mới ở `HomeDragState` (toạ độ đổi sang gốc cửa sổ, cùng hệ với lưới); mỗi lần di → `move`, nhấc ngón đó → `drop`, cử chỉ bị huỷ → `cancel`.
     * Kéo xong (nhận hay không) đóng cả hai bảng (Q3, như XML `onNewWidgetDragFinished`). Di quá slop TRƯỚC khi arm: không phải của ta (pager
     * chi tiết lật trang, sheet kéo xuống). Nút nhận cú nhấn xuống nhận tiếp mọi sự kiện kể cả khi ngón ra ngoài khung, nên bảng chỉ mờ đi, không gỡ.
     */
    private fun Modifier.pickerCardDrag(kind: WidgetKind, size: WidgetSize): Modifier = composed {
        val haptic = LocalHapticFeedback.current
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        this
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(kind, size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    // null = giữ đủ lâu trong slop (arm); true = nhấc tay / di quá slop / bị nút khác tiêu thụ trước đó.
                    val notArmed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed || change.isConsumed ||
                                (change.position - down.position).getDistance() > slop
                            ) break
                        }
                        true
                    }
                    if (notArmed != null) return@awaitEachGesture
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val drag = pagerInfo.drag ?: return@awaitEachGesture
                    var started = false
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            event.changes.forEach { it.consume() }
                            if (change == null || !change.pressed) {
                                if (started) drag.drop()
                                finished = true
                                break
                            }
                            val c = coords[0]?.takeIf { it.isAttached } ?: continue
                            val root = c.localToRoot(change.position)
                            if (!started) {
                                if ((change.position - down.position).getDistance() > slop) {
                                    started = drag.startNewWidget(kind, size, TEMP_ID_BASE - (++tempIdSeq), root)
                                    if (!started) {
                                        finished = true
                                        break
                                    }
                                    dragging = true
                                    drag.move(root)
                                }
                            } else {
                                drag.move(root)
                            }
                        }
                    } finally {
                        if (started && !finished) drag.cancel()
                        if (started) closeAll()
                    }
                }
            }
    }

    /** Bấm "Thêm" của widget app: dựng yêu cầu (cỡ dp của khung, trang đang xem) và bắt đầu luồng thêm. Trả true nếu luồng đã bắt đầu. */
    private fun startAppWidget(entry: AppWidgetProviderEntry, size: WidgetSize): Boolean {
        val g = grid() ?: return false
        return startAppWidget(
            AppWidgetAddRequest(
                provider = entry.ref,
                spanX = size.spanX,
                spanY = size.spanY,
                widthDp = AppWidgetSizePlanner.spanDp(size.spanX, g.cellWidthDp, g.gutterXDp),
                heightDp = AppWidgetSizePlanner.spanDp(size.spanY, g.cellHeightDp, g.gutterYDp),
                preferredPageIndex = pagerInfo.currentPage
            )
        )
    }

    /** So khớp không phân biệt hoa thường và dấu (tiếng Việt: "dong ho" khớp "Đồng hồ"). */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')

    private fun variantTitleRes(kind: WidgetKind?, size: WidgetSize): Int {
        val wide = size.spanX >= 4
        return when (kind) {
            null -> if (wide) R.string.today_recents_wide_title else R.string.today_recents_small_title // TV-3
            WidgetKind.CLOCK -> if (wide) R.string.wp_clock_wide_title else R.string.wp_clock_small_title
            WidgetKind.CALENDAR -> if (wide) R.string.wp_calendar_wide_title else R.string.wp_calendar_small_title
            WidgetKind.WEATHER -> if (wide) R.string.wp_weather_wide_title else R.string.wp_weather_small_title
            WidgetKind.PHOTO -> if (wide) R.string.wp_photo_wide_title else R.string.wp_photo_small_title
        }
    }

    private fun variantDescRes(kind: WidgetKind?, size: WidgetSize): Int {
        val wide = size.spanX >= 4
        return when (kind) {
            null -> if (wide) R.string.today_recents_wide_desc else R.string.today_recents_small_desc // TV-3
            WidgetKind.CLOCK -> if (wide) R.string.wp_clock_wide_desc else R.string.wp_clock_small_desc
            WidgetKind.CALENDAR -> if (wide) R.string.wp_calendar_wide_desc else R.string.wp_calendar_small_desc
            WidgetKind.WEATHER -> if (wide) R.string.wp_weather_wide_desc else R.string.wp_weather_small_desc
            WidgetKind.PHOTO -> if (wide) R.string.wp_photo_wide_desc else R.string.wp_photo_small_desc
        }
    }

    private fun kindNameRes(kind: WidgetKind?): Int = when (kind) {
        null -> R.string.today_recents_name // TV-3
        WidgetKind.CLOCK -> R.string.widget_name_clock
        WidgetKind.CALENDAR -> R.string.widget_name_calendar
        WidgetKind.WEATHER -> R.string.weather_name
        WidgetKind.PHOTO -> R.string.ph_name // G3b
    }

    private companion object {
        // P2-9: bảng chọn widget kiểu iOS (chỉnh tay theo video). TV-3 (chủ dự án 2026-11-20): Thời tiết và Ảnh 2×2 cùng hàng; bảng của Today
        // thêm "App gần đây" 4×2 ở hàng dưới.
        val FEATURED = listOf(
            Featured(WidgetKind.CLOCK, WidgetSize(2, 2)),
            Featured(WidgetKind.CALENDAR, WidgetSize(2, 2)),
            Featured(WidgetKind.WEATHER, WidgetSize(2, 2)),
            Featured(WidgetKind.PHOTO, WidgetSize(2, 2))
        )
        val FEATURED_TODAY_EXTRA = listOf(Featured(null, WidgetSize(4, 2)))

        /** TV-3: các cỡ của widget "App gần đây" ở trang chi tiết (nhỏ trước, như các loại khác). */
        val RECENTS_SIZES = listOf(WidgetSize(2, 2), WidgetSize(4, 2))

        val GAP: Dp = 6.dp // widget_picker_gap của REF
        val ROW_HEIGHT: Dp = 56.dp
        val ROW_ICON: Dp = 32.dp
        val HEADER_ICON: Dp = 32.dp
        val TOUCH: Dp = 48.dp
        val PAGER_HEIGHT: Dp = 360.dp
        val ADD_HEIGHT: Dp = 52.dp
        const val PREVIEW_SMALL_FRACTION = 0.45f
        const val PREVIEW_WIDE_FRACTION = 0.9f
        val PREVIEW_APP_MAX_W: Dp = 300.dp
        val PREVIEW_APP_MAX_H: Dp = 220.dp
        const val TILT_DEG = 28f
        const val TILT_SCALE = 0.12f
        const val TILT_FADE = 0.5f
        const val CAMERA_DISTANCE_FACTOR = 12f
        const val ENTER_SCALE = 0.85f
        const val ENTER_MS = 260
        const val SHEET_HEIGHT_FRACTION = 0.85f

        /** PG2 (Q4): bảng mờ về 0 trong chừng này ms khi bắt đầu kéo thẻ. */
        const val DRAG_FADE_MS = 150

        /** PG2: id tạm của widget mới đang kéo (âm, xa mọi id Room và các hằng âm khác: -1 app con thư mục, ContainerId, DOCK_GAP). */
        const val TEMP_ID_BASE = -(1L shl 40)
        val DecelerateEasing = Easing { DecelerateInterpolator().getInterpolation(it) }
    }
}

