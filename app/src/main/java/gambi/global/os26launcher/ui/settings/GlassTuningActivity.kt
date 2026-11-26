package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.dsAurora
import gambi.global.os26launcher.core.glass.compose.LocalGlassLookOverride
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.model.GlassLook
import gambi.global.os26launcher.domain.model.GlassLookParam
import gambi.global.os26launcher.ui.settings.compose.SettingsList
import gambi.global.os26launcher.ui.settings.compose.SettingsListActions
import kotlinx.coroutines.launch
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

/**
 * GT: màn "Tinh chỉnh kính" (mở từ hàng cùng tên trong Cài đặt). Khung xem trước chạy theo ngón tay khi kéo; giá trị chỉ được GHI khi
 * nhả tay (`SettingsViewModel.onGlassLookChanged`). Màn này không đụng Home: Home đọc thông số qua `GlassEnvironment`.
 *
 * JS1: Compose. Khung xem trước là một lát ảnh nền của Home với bốn mẫu kính Backdrop thật (thẻ, ô thư mục, pill, dock), cùng loại kính
 * Home Compose vẽ, nhận thông số đang kéo qua `LocalGlassLookOverride` (thay `GlassPreviewView` + kính QWEA0).
 */
@AndroidEntryPoint
class GlassTuningActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private var rows by mutableStateOf<List<SettingsRow>>(emptyList())
    private var resetKey by mutableIntStateOf(0)
    private val previewLook = mutableStateOf(GlassLook.DEFAULT)
    private var backdrop by mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false) // RS Task 4: nền aurora tràn dưới thanh hệ thống

        val actions = SettingsListActions(
            onValueClicked = { row -> if (row.id == SettingsRow.ID_GLASS_LOOK_RESET) viewModel.onGlassLookReset() },
            onSliderCommitted = { row, value -> paramOf(row.id)?.let { viewModel.onGlassLookChanged(it, value) } },
            onSliderMoved = { row, value -> paramOf(row.id)?.let { previewLook.value = previewLook.value.with(it, value) } }
        )
        setContent {
            DsScreen(title = stringResource(R.string.gt_title), onBack = ::finish) {
                GlassPreview(backdrop, previewLook)
                SettingsList(rows, actions, Modifier.weight(1f), resetKey = resetKey)
            }
        }
        reassertEdgeToEdge(window) // EF: Tecno Spark 40 (Android 15) mất tràn viền sau recreate()

        lifecycleScope.launch {
            val m = resources.displayMetrics
            backdrop = viewModel.loadBackdropForPreview(m.widthPixels, m.heightPixels)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: SettingsUiState) {
        if (state.loading) return
        val look = state.glassLook
        previewLook.value = look // giá trị đã lưu là nguồn thật: sau khi nhả tay (hoặc đặt lại) khung về đúng nó
        // PG1 F4: chỉ hiện thanh mà máy này có tác dụng, cùng mốc API với `DsGlass.kt` (thư viện Backdrop): khúc xạ (lens) từ API 33, làm mờ từ
        // API 31; Độ bóng (viền sáng, cả thẻ tĩnh `DsSurfaceDrawable` dưới API 31) và Bo góc (`DsCorners`) có tác dụng ở mọi máy. Máy API ≥ 33
        // giữ nguyên đủ 4 thanh như trước. Giá trị của thanh bị ẩn vẫn lưu nguyên (Đặt lại vẫn đưa cả bốn về mặc định).
        val sdk = Build.VERSION.SDK_INT
        rows = listOfNotNull(
            SettingsRow.Header("header_glass_look", R.string.gt_header),
            if (sdk >= Build.VERSION_CODES.TIRAMISU) {
                SettingsRow.Slider(SettingsRow.ID_GLASS_LOOK_REFRACTION, R.string.gt_refraction, look.refraction, R.string.gt_refraction_desc)
            } else null,
            SettingsRow.Slider(SettingsRow.ID_GLASS_LOOK_GLOSS, R.string.gt_gloss, look.gloss, R.string.gt_gloss_desc),
            if (sdk >= Build.VERSION_CODES.S) {
                SettingsRow.Slider(SettingsRow.ID_GLASS_LOOK_BLUR, R.string.gt_blur, look.blur, R.string.gt_blur_desc)
            } else null,
            SettingsRow.Slider(SettingsRow.ID_GLASS_LOOK_CORNER, R.string.gt_corner, look.corner, R.string.gt_corner_desc),
            SettingsRow.Header("header_glass_look_reset", R.string.gt_reset_header),
            SettingsRow.Value(SettingsRow.ID_GLASS_LOOK_RESET, R.string.gt_reset, "", enabled = look != GlassLook.DEFAULT)
        )
    }

    private fun handleEvent(event: SettingsEvent) {
        if (event !is SettingsEvent.Message) return
        Toast.makeText(this, getString(event.res, event.arg ?: getString(R.string.common_unknown)), Toast.LENGTH_LONG).show()
        // Ghi lỗi thì flow không đổi: đưa thanh trượt và khung xem trước về giá trị thật.
        render(viewModel.uiState.value)
        resetKey++
    }

    private fun paramOf(rowId: String): GlassLookParam? = when (rowId) {
        SettingsRow.ID_GLASS_LOOK_REFRACTION -> GlassLookParam.REFRACTION
        SettingsRow.ID_GLASS_LOOK_GLOSS -> GlassLookParam.GLOSS
        SettingsRow.ID_GLASS_LOOK_BLUR -> GlassLookParam.BLUR
        SettingsRow.ID_GLASS_LOOK_CORNER -> GlassLookParam.CORNER
        else -> null
    }
}

/**
 * GT + JS1: khung xem trước bo 20, cao `glass_preview_height`: ảnh nền (hoặc aurora khi chưa có) ghi làm lớp backdrop riêng, trên đó
 * thẻ 150 × 88 (vai widget), ô thư mục 72, pill tìm kiếm 132 × 36 và thanh dock cao 72, đều là kính `CARD` đọc [look].
 *
 * 2026-11-25 (chủ dự án): ảnh nền phủ khung kiểu center crop (tỉ lệ phủ kín, mở đầu ở giữa ảnh) và kéo được (có quán tính) trong phạm vi
 * phần ảnh bị cắt, để xem kính khúc xạ / mờ khi nền chạy phía sau. Vị trí chỉ đọc trong pha vẽ: kéo không dựng lại khung.
 */
@Composable
private fun GlassPreview(bitmap: Bitmap?, look: State<GlassLook>) {
    val layer = rememberLayerBackdrop()
    val description = stringResource(R.string.gt_preview)
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    // Cỡ ảnh sau khi phủ khung (center crop) và vị trí góc trên trái của ảnh trong khung, giới hạn [frame − ảnh, 0].
    val scaled = remember(image, frame) {
        if (image == null || frame == IntSize.Zero) Size.Zero
        else {
            val s = maxOf(frame.width / image.width.toFloat(), frame.height / image.height.toFloat())
            Size(image.width * s, image.height * s)
        }
    }
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(scaled) {
        if (scaled == Size.Zero) return@LaunchedEffect
        val lower = Offset(frame.width - scaled.width, frame.height - scaled.height)
        pan.updateBounds(lower, Offset.Zero)
        pan.snapTo(lower / 2f) // mở đầu ở giữa ảnh, như ContentScale.Crop
    }
    val decay = rememberSplineBasedDecay<Offset>()
    Box(
        Modifier
            .padding(horizontal = dimensionResource(R.dimen.settings_group_margin))
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.glass_preview_height))
            .clip(RoundedCornerShape(20.dp))
            .onSizeChanged { frame = it }
            .semantics { contentDescription = description }
            .pointerInput(scaled) {
                if (scaled == Size.Zero) return@pointerInput
                val tracker = VelocityTracker()
                coroutineScope {
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            launch { pan.stop() }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            launch { pan.snapTo(pan.value + amount) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            launch { pan.animateDecay(Offset(v.x, v.y), decay) }
                        }
                    )
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(layer)
                .dsAurora()
                .drawBehind {
                    if (image != null && scaled != Size.Zero) {
                        val p = pan.value
                        drawImage(
                            image,
                            dstOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt()),
                            dstSize = IntSize(scaled.width.roundToInt(), scaled.height.roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
        )
        CompositionLocalProvider(LocalGlassLookOverride provides look) {
            val card = DsCorner.Role(DsCornerRole.CARD)
            Box(Modifier.padding(16.dp).size(150.dp, 88.dp).align(Alignment.TopStart).dsGlass(DsSurface.CARD, card, layer))
            Box(Modifier.padding(16.dp).size(72.dp).align(Alignment.TopEnd).dsGlass(DsSurface.CARD, card, layer))
            Box(Modifier.offset(y = 120.dp).size(132.dp, 36.dp).align(Alignment.TopCenter).dsGlass(DsSurface.CARD, DsCorner.Pill, layer))
            Box(Modifier.padding(8.dp).fillMaxWidth().height(72.dp).align(Alignment.BottomCenter).dsGlass(DsSurface.CARD, card, layer))
        }
    }
}
