package gambi.global.os26launcher.ui.controlcenter

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.CustomTile
import gambi.global.os26launcher.domain.model.controlcenter.CustomTileType
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.usecase.GetHiddenAppsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * G3d: tùy chỉnh ô của Trung tâm điều khiển (RE mục 5.3–5.4, `CustomControlActivity`), kiểu "Tùy chỉnh điều khiển" của iOS:
 * - **Đang dùng**: kéo tay nắm để đổi thứ tự, nút đỏ để bỏ;
 * - **Thêm điều khiển**: các ô dựng sẵn chưa dùng, nút xanh để thêm;
 * - **Ứng dụng**: lối tắt mở một app bất kỳ.
 * Tối đa [ControlCenterConfig.MAX_TILES] ô. Mỗi thay đổi ghi ngay (không có nút Lưu); đổi thứ tự ghi một lần khi thả.
 *
 * JS7: Compose (`DsScreen` + `LazyColumn`, nhóm "Đang dùng" trong card kính) thay MaterialToolbar + RecyclerView + ItemTouchHelper.
 */
@AndroidEntryPoint
class CustomizeTilesActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: ControlCenterPrefsRepository

    @Inject
    lateinit var apps: AppRepository

    @Inject
    lateinit var getHiddenApps: GetHiddenAppsUseCase

    /**
     * P3-7: ô app của app đã ẩn: KHÔNG hiện ở màn này nhưng giữ trong cấu hình, kèm chỉ số gốc. Lưu thì chèn lại đúng chỗ cũ (kẹp vào cuối nếu
     * danh sách đã ngắn hơn), nên bỏ ẩn app là ô về như trước.
     */
    private var hiddenTiles: List<IndexedValue<CustomTile>> = emptyList()

    private val included = mutableStateListOf<CustomTile>()
    private var allApps by mutableStateOf<List<AppItem>>(emptyList())
    private val iconCache = mutableStateMapOf<String, Bitmap?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DsScreen(title = stringResource(R.string.cc_customize_tiles), onBack = ::finish) {
                TileList(Modifier.weight(1f))
            }
        }
        reassertEdgeToEdge(window)
        lifecycleScope.launch {
            val hidden = getHiddenApps().first().mapTo(HashSet()) { it.key }
            val all = prefs.config.first().tiles
            hiddenTiles = all.withIndex().filter { (_, t) ->
                t is CustomTile.App && ComponentKey(t.packageName, t.className, t.userSerial) in hidden
            }
            val hiddenSet = hiddenTiles.mapTo(HashSet()) { it.index }
            included.clear()
            included += all.filterIndexed { i, _ -> i !in hiddenSet }
            allApps = apps.apps.first().filter { it.key !in hidden } // P3-7: "Thêm điều khiển" không lộ app đã ẩn
            loadIcons()
        }
    }

    private suspend fun loadIcons() {
        val size = resources.getDimensionPixelSize(R.dimen.cc_app_icon)
        for (app in allApps) {
            val k = key(app.key.packageName, app.key.className, app.key.userSerial)
            if (k in iconCache) continue
            iconCache[k] = apps.icon(app.key, size)
        }
    }

    private fun key(pkg: String, cls: String, user: Long) = "$pkg/$cls/$user"

    private fun save() {
        // P3-7: chèn lại ô của app đã ẩn vào chỗ cũ.
        val snapshot = included.toMutableList().also { list ->
            hiddenTiles.forEach { (i, t) -> list.add(i.coerceAtMost(list.size), t) }
        }.toList()
        lifecycleScope.launch {
            prefs.update { it.copy(tiles = snapshot) }
            ControlCenterService.refresh(this@CustomizeTilesActivity)
        }
    }

    private fun add(tile: CustomTile) {
        if (included.size + hiddenTiles.size >= ControlCenterConfig.MAX_TILES) {
            Toast.makeText(this, getString(R.string.cc_tiles_full, ControlCenterConfig.MAX_TILES), Toast.LENGTH_SHORT).show()
            return
        }
        included += tile
        save()
    }

    private fun remove(tile: CustomTile) {
        included.remove(tile)
        save()
    }

    private fun label(tile: CustomTile): String = when (tile) {
        is CustomTile.Builtin -> getString(tile.type.labelRes())
        is CustomTile.App -> allApps.firstOrNull { it.key.packageName == tile.packageName && it.key.className == tile.className }
            ?.label ?: tile.packageName
    }

    @Composable
    private fun TileList(modifier: Modifier) {
        val listState = rememberLazyListState()
        val available = CustomTileType.entries.map { CustomTile.Builtin(it) }.filter { it !in included }
        val appTiles = allApps.map { CustomTile.App(it.key.packageName, it.key.className, it.key.userSerial) }.filter { it !in included }
        // Kéo tay nắm: chỉ số hàng đang kéo trong [included] và độ dời dọc của nó.
        var dragging by remember { mutableIntStateOf(-1) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val margin = dimensionResource(R.dimen.ds_screen_margin_h)
        LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(start = margin, end = margin, bottom = 32.dp)) {
            item(key = "h_included") { Header(R.string.cc_tiles_included) }
            itemsIndexed(included, key = { _, t -> "in:" + tileKey(t) }) { index, tile ->
                val isDragged = index == dragging
                Box(
                    Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))
                ) {
                    TileRow(tile, included = true, onAction = { remove(tile) }) {
                        Modifier.pointerInput(tile) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = included.indexOf(tile)
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    dragging = -1
                                    dragOffset = 0f
                                    save() // ghi một lần khi thả, không ghi mỗi bước kéo
                                },
                                onDragCancel = {
                                    dragging = -1
                                    dragOffset = 0f
                                    save()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val i = dragging
                                    if (i < 0) return@detectDragGestures
                                    // Vượt nửa chiều cao hàng kế bên thì đổi chỗ, dời mốc theo đúng chiều cao đó.
                                    val info = listState.layoutInfo.visibleItemsInfo
                                    val me = info.firstOrNull { it.key == "in:" + tileKey(included[i]) } ?: return@detectDragGestures
                                    if (dragOffset > 0 && i < included.lastIndex) {
                                        val next = info.firstOrNull { it.key == "in:" + tileKey(included[i + 1]) }
                                        if (next != null && dragOffset > next.size / 2f) {
                                            included.add(i + 1, included.removeAt(i))
                                            dragging = i + 1
                                            dragOffset -= next.size
                                        }
                                    } else if (dragOffset < 0 && i > 0) {
                                        val prev = info.firstOrNull { it.key == "in:" + tileKey(included[i - 1]) }
                                        if (prev != null && -dragOffset > prev.size / 2f) {
                                            included.add(i - 1, included.removeAt(i))
                                            dragging = i - 1
                                            dragOffset += prev.size
                                        }
                                    }
                                    @Suppress("UNUSED_VARIABLE") val unused = me
                                }
                            )
                        }
                    }
                }
            }
            item(key = "h_more") { Header(R.string.cc_tiles_more) }
            itemsIndexed(available, key = { _, t -> "av:" + tileKey(t) }) { _, tile -> TileRow(tile, included = false, onAction = { add(tile) }, handle = null) }
            item(key = "h_apps") { Header(R.string.cc_tiles_apps) }
            itemsIndexed(appTiles, key = { _, t -> "app:" + tileKey(t) }) { _, tile -> TileRow(tile, included = false, onAction = { add(tile) }, handle = null) }
        }
    }

    private fun tileKey(tile: CustomTile): String = when (tile) {
        is CustomTile.Builtin -> "b:" + tile.type.name
        is CustomTile.App -> key(tile.packageName, tile.className, tile.userSerial)
    }

    @Composable
    private fun Header(text: Int) {
        BasicText(
            stringResource(text),
            style = DsTextStyles.sectionHeader.copy(color = colorResource(R.color.ds_text_secondary)),
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).semantics { heading() }
        )
    }

    /** Một hàng: nút thêm/bỏ tròn (xanh/đỏ), icon, tên, tay nắm kéo (chỉ hàng đang dùng; TalkBack đổi thứ tự bằng nút bỏ/thêm). */
    @Composable
    private fun TileRow(tile: CustomTile, included: Boolean, onAction: () -> Unit, handle: (() -> Modifier)?) {
        val name = label(tile)
        val iconSize = dimensionResource(R.dimen.cc_app_icon)
        val primary = colorResource(R.color.ds_text_primary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actionDesc = stringResource(if (included) R.string.cc_tiles_remove else R.string.cc_tiles_add, name)
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = actionDesc }
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(iconSize * 3 / 4).clip(CircleShape).background(colorResource(if (included) R.color.cc_red else R.color.cc_green)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(if (included) R.drawable.ic_cc_remove else R.drawable.ic_cc_add),
                        null,
                        colorFilter = ColorFilter.tint(colorResource(R.color.cc_icon))
                    )
                }
            }
            Box(Modifier.padding(start = 8.dp).size(iconSize), contentAlignment = Alignment.Center) {
                when (tile) {
                    is CustomTile.Builtin -> Image(painterResource(tile.type.iconRes()), null, colorFilter = ColorFilter.tint(primary))
                    is CustomTile.App -> {
                        val bmp = iconCache[key(tile.packageName, tile.className, tile.userSerial)]
                        if (bmp != null) Image(bmp.asImageBitmap(), null) else Image(painterResource(R.drawable.ic_cc_app), null, colorFilter = ColorFilter.tint(primary))
                    }
                }
            }
            BasicText(name, style = DsTextStyles.body.copy(color = primary), maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (handle != null) {
                val reorder = stringResource(R.string.cc_tiles_reorder)
                Box(Modifier.size(48.dp).then(handle()).semantics { contentDescription = reorder }, contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_cc_drag), null, colorFilter = ColorFilter.tint(colorResource(R.color.ds_text_secondary)))
                }
            }
        }
    }
}
