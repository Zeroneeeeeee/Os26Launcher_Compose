package gambi.global.os26launcher.ui.photo

import android.util.LruCache
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.DsSpinner
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.ui.photo.compose.PhotoCropState
import gambi.global.os26launcher.ui.photo.compose.PhotoCropper
import gambi.global.os26launcher.ui.settings.SettingsDialogs
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.bitmap.BitmapScaler
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import gambi.global.os26launcher.domain.photo.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * G3b — màn "Ảnh của tiện ích": danh sách ảnh của MỘT widget Ảnh ([EXTRA_ITEM_ID]), thêm ảnh bằng bộ chọn ảnh hệ thống (không cần quyền đọc ảnh), xóa ảnh.
 * Task 3: ảnh được chép ngay khi chọn rồi cam kết với vùng giữa; Task 4 thêm màn cắt. Home biết kết quả qua `PhotoWidgetRepository`, không qua kết quả
 * của Activity (plan D6). Không bao giờ log URI/đường dẫn.
 */
@AndroidEntryPoint
class PhotoWidgetActivity : AppCompatActivity() {

    private val viewModel: PhotoWidgetViewModel by viewModels()
    private var backCallback: androidx.activity.OnBackPressedCallback? = null
    private var shownCropId: String? = null
    private lateinit var crop: PhotoCropState
    private var cropReady by mutableStateOf(false)
    private val thumbs = object : LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Đăng ký lúc dựng (trước STARTED). Nhiều ảnh dùng `PickMultipleVisualMedia(MAX)` rồi cắt kết quả xuống số chỗ còn lại (số tối đa cố định lúc tạo hợp đồng,
    // và đường rơi về ACTION_OPEN_DOCUMENT bỏ qua maxItems); còn đúng một chỗ thì dùng `PickVisualMedia` (hợp đồng nhiều ảnh đòi maxItems > 1).
    private val pickMultiple =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoWidgetConfig.MAX_PHOTOS)) { uris ->
            viewModel.onPicked(uris.map(::sourceOf))
        }
    private val pickSingle =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onPicked(listOf(sourceOf(uri)))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getLongExtra(EXTRA_ITEM_ID, 0L) <= 0L) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        crop = PhotoCropState(PhotoCropState.MARGIN_DP * resources.displayMetrics.density)
        // Màn cắt đang mở: Back không đóng Activity (xem [onCropBack]); không có phiên cắt thì callback tắt và Back đóng Activity như thường.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = onCropBack()
        }.also { backCallback = it })

        // JS6: giao diện Compose (DsScreen) thay activity_photo_widget.xml + PhotoGridAdapter + PhotoCropView.
        setContent {
            val ui by viewModel.state.collectAsStateWithLifecycle()
            val session = ui?.crop
            val title = when {
                session == null -> stringResource(R.string.ph_activity_title)
                session.staged -> stringResource(R.string.ph_crop_title, session.position, session.total)
                else -> stringResource(R.string.ph_crop_edit_title)
            }
            DsScreen(title = title, onBack = { onBackPressedDispatcher.onBackPressed() }) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val u = ui
                    if (u != null) PhotoGrid(u)
                    if (u == null || u.importing) DsSpinner(stringResource(R.string.ph_importing), Modifier.align(Alignment.Center))
                    if (session != null) CropPanel(session)
                }
            }
            dsDialogs.Render()
        }
        reassertEdgeToEdge(window)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { it?.let(::render) } }
                launch { viewModel.eventFlow.collect(::handleEvent) }
            }
        }
    }

    @Composable
    private fun PhotoGrid(ui: PhotoWidgetUi) {
        val photos = ui.photos
        val pad = dimensionResource(R.dimen.ph_grid_padding)
        val secondary = colorResource(R.color.ds_text_secondary)
        if (photos.isEmpty()) {
            if (!ui.importing) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    BasicText(stringResource(R.string.ph_empty_hint), style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_primary), textAlign = TextAlign.Center))
                    DsButton(stringResource(R.string.ph_choose_photo), onClick = ::launchPicker, modifier = Modifier.padding(top = 16.dp), size = DsButtonSize.M)
                }
            }
            return
        }
        Column(Modifier.fillMaxSize()) {
            if (photos.size >= 2) {
                BasicText(stringResource(R.string.ph_rotates), style = DsTextStyles.caption.copy(color = secondary), modifier = Modifier.padding(start = pad, end = pad, top = 4.dp, bottom = 8.dp))
            }
            LazyVerticalGrid(columns = GridCells.Fixed(COLUMNS), contentPadding = PaddingValues(pad), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(photos, key = { _, p -> p.id }) { i, ref -> PhotoCell(ref, i + 1) }
                if (photos.size < PhotoWidgetConfig.MAX_PHOTOS) item(key = "add") { AddCell() }
            }
        }
    }

    @Composable
    private fun PhotoCell(ref: PhotoRef, index: Int) {
        val thumb by produceState(thumbs.get(ref.id), ref.id) {
            if (value != null) return@produceState
            val file = viewModel.photoFile(ref) ?: return@produceState
            value = withContext(Dispatchers.IO) { decodeThumb(file) }?.also { thumbs.put(ref.id, it) }
        }
        val desc = stringResource(R.string.ph_thumb_desc, index)
        val removeDesc = stringResource(R.string.ph_remove_desc, index)
        Box(
            Modifier
                .padding(4.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(12f))
                .semantics { contentDescription = desc }
                .clickable(interactionSource = null, indication = null, role = Role.Button) { viewModel.startEdit(ref) } // Task 4: bấm ô = cắt lại ảnh này
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(dimensionResource(R.dimen.ph_remove_size))
                    .semantics { contentDescription = removeDesc }
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { confirmRemove(ref) },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    BasicText(stringResource(R.string.ph_remove_glyph), style = DsTextStyles.caption.copy(color = Color.White))
                }
            }
        }
    }

    @Composable
    private fun AddCell() {
        Box(
            Modifier
                .padding(4.dp)
                .aspectRatio(1f)
                .dsGlass(DsSurface.CARD, DsCorner.Fixed(12f))
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = ::launchPicker),
            contentAlignment = Alignment.Center
        ) {
            BasicText(stringResource(R.string.ph_add_photo), style = DsTextStyles.bodyStrong.copy(color = colorResource(R.color.ds_text_primary)))
        }
    }

    @Composable
    private fun CropPanel(session: CropSession) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colorResource(R.color.ds_bg_base))
                // Như cropPanel của bản View (che kín lưới): chạm vào khoảng trống của màn cắt không lọt xuống ô ảnh / nút xóa bên dưới.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
        ) {
            PhotoCropper(crop, stringResource(R.string.ph_crop_desc), viewModel::onCropChanged, Modifier.weight(1f).fillMaxWidth())
            BasicText(
                stringResource(R.string.ph_crop_hint),
                style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary), textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DsButton(
                    stringResource(if (session.staged) R.string.ph_crop_skip else R.string.ph_cancel),
                    onClick = viewModel::skip,
                    modifier = Modifier.weight(1f),
                    variant = DsButtonVariant.Secondary
                )
                DsButton(
                    stringResource(R.string.ph_crop_done),
                    onClick = { viewModel.commit(crop.currentCrop()) },
                    modifier = Modifier.weight(1f),
                    enabled = cropReady
                )
            }
        }
    }

    private fun confirmRemove(ref: PhotoRef) {
        SettingsDialogs.confirm(
            this, getString(R.string.ph_remove_title), getString(R.string.ph_remove_message), getString(R.string.ph_remove_confirm),
            onPositive = { viewModel.remove(ref.id) },
            negative = getString(R.string.ph_cancel)
        )
    }

    private fun render(ui: PhotoWidgetUi) {
        renderCrop(ui.crop)
        // Widget chưa có ảnh: mở bộ chọn ngay đúng một lần mỗi lần vào (cờ trong ViewModel, sống qua tạo lại Activity).
        if (ui.photos.isEmpty() && !ui.importing && ui.crop == null && viewModel.consumeAutoPick()) launchPicker()
    }

    /** Hiện/ẩn màn cắt. Ảnh chỉ được nạp lại khi `photoId` đổi (cập nhật `crop` sau mỗi lần nhấc tay không được đặt lại khung). */
    private fun renderCrop(session: CropSession?) {
        backCallback?.isEnabled = session != null
        if (session == null) {
            if (shownCropId != null) {
                shownCropId = null
                crop.clear()
            }
            return
        }
        if (shownCropId == session.photoId) return
        shownCropId = session.photoId
        cropReady = false
        val file = viewModel.fileOf(session)
        if (file == null) {
            viewModel.onImageUnreadable()
            return
        }
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeForCrop(file) }
            if (shownCropId != session.photoId) return@launch // phiên đã đổi trong lúc giải mã
            if (bmp == null) {
                viewModel.onImageUnreadable()
            } else {
                crop.setImage(bmp, session.aspect, session.crop)
                cropReady = true
            }
        }
    }

    /** Ảnh nguồn đã lưu (<= 2048 px) giải mã cỡ màn là đủ để chỉnh khung; vùng cắt lưu chuẩn hóa nên không phụ thuộc cỡ bitmap này. */
    private fun decodeForCrop(file: java.io.File): Bitmap? = decodeSampled(file, CROP_DECODE_PX)

    private fun decodeThumb(file: java.io.File): Bitmap? = decodeSampled(file, THUMB_PX)

    private fun decodeSampled(file: java.io.File, minSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val sample = BitmapScaler.sampleSizeFor(bounds.outWidth, bounds.outHeight, minSide, minSide, BitmapScaler.MAX_DECODE_PIXELS)
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Back khi đang ở màn cắt: sửa ảnh có sẵn thì thoát; còn ảnh mới chưa cắt thì hỏi "Bỏ N ảnh chưa cắt?" (ảnh đã cắt xong luôn được giữ). */
    private fun onCropBack() {
        val pending = viewModel.pendingCount()
        if (pending == 0) {
            viewModel.cancelEdit()
            return
        }
        SettingsDialogs.confirm(
            this, getString(R.string.ph_discard_title), getString(R.string.ph_discard_message, pending), getString(R.string.ph_discard_confirm),
            onPositive = { viewModel.discardAll() },
            negative = getString(R.string.ph_discard_keep)
        )
    }

    private fun launchPicker() {
        val room = viewModel.remaining()
        if (room <= 0) {
            viewModel.reportFull()
            return
        }
        // Chỉ ghi tuyến bộ chọn (không URI): để biết Live 4 (API 30) và Oppo (API 34) đi đường nào. Tên linh kiện bộ chọn lấy thêm bằng `dumpsys` (xem plan Task 3).
        val route = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) ROUTE_PHOTO_PICKER else ROUTE_OPEN_DOCUMENT
        Log.i(TAG, "pickerRoute=$route api=${Build.VERSION.SDK_INT} slots=$room mode=${if (room == 1) "single" else "multi"}")
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (room == 1) pickSingle.launch(request) else pickMultiple.launch(request)
    }

    private fun handleEvent(event: PhotoWidgetEvent) {
        val text = when (event) {
            PhotoWidgetEvent.ImportFailed -> getString(R.string.ph_import_failed)
            is PhotoWidgetEvent.TooMany -> getString(R.string.ph_too_many, event.max)
            is PhotoWidgetEvent.Full -> getString(R.string.ph_full, event.max)
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /** Bọc [uri] thành nguồn byte cho `domain` (không giữ `Uri` lâu hơn việc chép; lỗi mở = null). */
    private fun sourceOf(uri: Uri): PhotoSource = PhotoSource {
        try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        internal const val EXTRA_ITEM_ID = "photo_widget_item_id"
        private const val COLUMNS = 3
        /** CHỈNH TAY: cạnh tối thiểu của thumbnail giải mã (px); ô lưới ~110-130dp. */
        private const val THUMB_PX = 256
        /** CHỈNH TAY: trần cache thumbnail (byte). */
        private const val THUMB_CACHE_BYTES = 4 * 1024 * 1024

        /** CHỈNH TAY: cạnh tối thiểu của ảnh giải mã cho màn cắt (px); ảnh nguồn tối đa 2048 nên thường giữ nguyên cỡ. */
        private const val CROP_DECODE_PX = 1280
        private const val TAG = "PhotoWidget"
        private const val ROUTE_PHOTO_PICKER = "PHOTO_PICKER" // bộ chọn hệ thống hoặc bản backport Play services (`isPhotoPickerAvailable` gộp hai loại)
        private const val ROUTE_OPEN_DOCUMENT = "OPEN_DOCUMENT" // thư viện tự rơi về ACTION_OPEN_DOCUMENT

        /** Intent mở màn cho widget [itemId] (hàng `launcher_items`). */
        fun intent(context: Context, itemId: Long): Intent =
            Intent(context, PhotoWidgetActivity::class.java).putExtra(EXTRA_ITEM_ID, itemId)
    }
}
