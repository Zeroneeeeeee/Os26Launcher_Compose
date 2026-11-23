package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.search.AppSearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * P3-4 (CI) — màn "Thay đổi biểu tượng" (5.2, Figma `302:3294`): ô tìm kiếm + danh sách MỌI app được hiện (không gồm app đã ẩn, P3-7),
 * hàng đã đổi icon có chữ "Đã đổi". Chạm một hàng mở [ChangeIconSheet]. Icon riêng không bị áp kiểu icon / gói icon (Q6).
 */
class ChangeIconSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[ChangeIconViewModel::class.java]
    private var apps: List<AppItem> = emptyList()
    private var customized: Set<ComponentKey> = emptySet()
    private var query = ""
    private var sheet: ChangeIconSheet? = null

    // Đăng ký lúc dựng màn (trước STARTED). Hủy chọn (uri null) = không làm gì.
    private val pickImage = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = sheet ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        activity.lifecycleScope.launch {
            val square = withContext(Dispatchers.IO) {
                WallpaperTargetSheet.decodeUri(activity, uri)?.let(::centerSquare)
            }
            if (square == null) {
                Toast.makeText(activity, R.string.ci_image_failed, Toast.LENGTH_LONG).show()
            } else if (sheet === target) {
                target.setPickedImage(square)
            }
        }
    }

    override val titleRes: Int = R.string.settings_hub_change_icon

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val changed = activity.getString(R.string.ci_changed)
        val q = AppSearchMatcher.normalize(query.trim())
        val shown = if (q.isEmpty()) apps else apps.filter {
            AppSearchMatcher.normalize(it.label).contains(q) || AppSearchMatcher.normalize(it.originalLabel).contains(q)
        }
        val rows = ArrayList<SettingsRow>(shown.size + 2)
        rows.add(SettingsRow.Search(ID_SEARCH, R.string.ci_search_hint))
        if (shown.isEmpty() && apps.isNotEmpty()) {
            rows.add(SettingsRow.Footer(ID_EMPTY, activity.getString(R.string.ci_no_results)))
        }
        shown.mapTo(rows) { SettingsRow.AppName(rowId(it.key), it, if (it.key in customized) changed else null) }
        return rows
    }

    override fun onSearchChanged(row: SettingsRow.Search, query: String) {
        if (query == this.query) return
        this.query = query
        ctx.rerender()
    }

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    override fun onAppNameClicked(row: SettingsRow.AppName) {
        if (sheet != null) return // một sheet mỗi lúc (chống chạm đúp)
        val app = row.app
        sheet = ChangeIconSheet(
            activity, viewModel, app,
            customized = app.key in customized,
            onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismissed = { sheet = null }
        ).show()
    }

    override val appIconLoader: (suspend (ComponentKey, Int) -> Bitmap?)? = viewModel::loadIcon

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.apps.collect { list ->
                    if (list != null) {
                        apps = list
                        ctx.rerender(false)
                    }
                }
            }
            launch {
                viewModel.customized.collect {
                    customized = it
                    ctx.rerender(false)
                }
            }
            launch {
                viewModel.failures.collect { res -> Toast.makeText(activity, res, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroy() {
        sheet?.dialog?.setOnDismissListener(null)
        sheet?.dialog?.dismiss()
        sheet = null
    }

    /** Cắt vuông giữa ảnh, thu về [ChangeIconViewModel.SAVE_SIZE_PX]. */
    private fun centerSquare(src: Bitmap): Bitmap {
        val side = min(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = if (side > ChangeIconViewModel.SAVE_SIZE_PX) {
            Bitmap.createScaledBitmap(cropped, ChangeIconViewModel.SAVE_SIZE_PX, ChangeIconViewModel.SAVE_SIZE_PX, true)
        } else {
            cropped
        }
        if (cropped !== src && cropped !== out) cropped.recycle()
        if (src !== out && src !== cropped) src.recycle()
        return out
    }

    private fun rowId(key: ComponentKey) = "ci_app:${key.packageName}/${key.className}#${key.userSerial}"

    private companion object {
        const val ID_SEARCH = "ci_search"
        const val ID_EMPTY = "ci_empty"
    }
}
