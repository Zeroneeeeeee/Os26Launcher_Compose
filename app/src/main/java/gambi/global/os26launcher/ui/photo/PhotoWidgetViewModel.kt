package gambi.global.os26launcher.ui.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.domain.model.photo.CropRect
import gambi.global.os26launcher.domain.model.photo.PhotoImportResult
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import gambi.global.os26launcher.domain.photo.PhotoSource
import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import gambi.global.os26launcher.ui.widget.PhotoWidgetHub
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phiên cắt đang mở. [staged] = ảnh vừa nhập (chưa thuộc widget); false = sửa vùng cắt của ảnh đã có. [position]/[total] đếm trong hàng đợi (chỉ ảnh mới).
 * [crop] là vùng cắt đang hiển thị (cập nhật mỗi lần nhấc tay) nên màn dựng lại vẫn giữ chỗ người dùng đã chỉnh.
 */
data class CropSession(
    val photoId: String,
    val staged: Boolean,
    val position: Int,
    val total: Int,
    val aspect: Float,
    val crop: CropRect
)

/** Trạng thái màn: [photos] theo thứ tự thêm; [importing] = đang chép ảnh vừa chọn; [crop] != null = đang ở màn cắt. */
data class PhotoWidgetUi(val photos: List<PhotoRef>, val importing: Boolean, val crop: CropSession?)

/** Thông báo một lần (Toast). Chữ ở `strings.xml`, ViewModel không biết chuỗi. */
sealed interface PhotoWidgetEvent {
    data object ImportFailed : PhotoWidgetEvent
    data class TooMany(val max: Int) : PhotoWidgetEvent
    data class Full(val max: Int) : PhotoWidgetEvent
}

/**
 * G3b Tasks 3-4 — trạng thái màn "Ảnh của tiện ích" cho một widget ([itemId] lấy từ extra của Intent).
 *
 * Luồng ảnh mới: chọn -> chép NGAY từng ảnh vào vùng nháp (URI chỉ tạm) -> hàng đợi cắt; mỗi ảnh "Xong" thì cam kết (ghi vào widget, Home đổi ngay), "Bỏ ảnh này"
 * thì xóa bản nháp. Back khi còn ảnh chưa cắt: Activity hỏi, rồi gọi [discardAll]. Ảnh đã cam kết luôn được giữ. Hàng đợi chỉ nằm trong RAM của ViewModel: process
 * chết giữa chừng thì lần mở sau [PhotoWidgetRepository.clearStaging] dọn nháp.
 */
@HiltViewModel
class PhotoWidgetViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repo: PhotoWidgetRepository
) : ViewModel() {

    val itemId: Long = savedState.get<Long>(PhotoWidgetActivity.EXTRA_ITEM_ID) ?: 0L

    private data class Pending(val photoId: String, val width: Int, val height: Int)

    private val ready = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private val session = MutableStateFlow<CropSession?>(null)
    private var queue: List<Pending> = emptyList() // ảnh mới đang chờ cắt; phần tử đầu là ảnh của [session]
    private var queueTotal = 0
    private var queueAspect = 1f
    private val events = Channel<PhotoWidgetEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PhotoWidgetEvent> = events.receiveAsFlow()

    /** null cho tới khi kho ảnh đọc xong (tránh coi "chưa nạp" là "chưa có ảnh"). */
    val state: StateFlow<PhotoWidgetUi?> = combine(repo.widgets, ready, importing, session) { all, isReady, busy, crop ->
        if (!isReady) null else PhotoWidgetUi(all[itemId]?.photos.orEmpty(), busy, crop)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            repo.awaitLoaded()
            if (itemId > 0L) repo.clearStaging(itemId) // ảnh nháp của phiên cắt bỏ dở (ViewModel mới = chưa có phiên nào)
            ready.value = true
        }
    }

    /** Lần đầu vào mà widget chưa có ảnh thì tự mở bộ chọn đúng một lần (cờ sống qua tạo lại Activity). */
    fun consumeAutoPick(): Boolean {
        if (savedState.get<Boolean>(KEY_AUTO_PICKED) == true) return false
        savedState[KEY_AUTO_PICKED] = true
        return true
    }

    /** Số chỗ còn lại của widget. */
    fun remaining(): Int = PhotoWidgetConfig.MAX_PHOTOS - repo.config(itemId).photos.size

    /** File ảnh nguồn đã lưu của phiên cắt hiện tại (null nếu mất). */
    fun fileOf(s: CropSession) = repo.photoFile(itemId, s.photoId, staged = s.staged)

    fun photoFile(ref: PhotoRef) = repo.photoFile(itemId, ref.id)

    fun reportFull() {
        events.trySend(PhotoWidgetEvent.Full(PhotoWidgetConfig.MAX_PHOTOS))
    }

    /** Số ảnh mới chưa cam kết (kể cả ảnh đang cắt): để Activity hỏi khi Back. */
    fun pendingCount(): Int = queue.size

    /** Ảnh vừa chọn: cắt xuống số chỗ còn lại (bộ chọn có thể bỏ qua `maxItems`), chép từng ảnh vào nháp, rồi mở hàng đợi cắt. Lỗi một ảnh không dừng cả lô. */
    fun onPicked(sources: List<PhotoSource>) {
        if (sources.isEmpty() || itemId <= 0L || session.value != null) return
        viewModelScope.launch {
            importing.value = true
            try {
                val batch = sources.take(remaining().coerceAtLeast(0))
                if (sources.size > batch.size) events.trySend(PhotoWidgetEvent.TooMany(PhotoWidgetConfig.MAX_PHOTOS))
                var failed = 0
                val staged = ArrayList<Pending>()
                for (source in batch) {
                    when (val result = repo.importPhoto(itemId, source)) {
                        is PhotoImportResult.Staged -> staged += Pending(result.photoId, result.width, result.height)
                        PhotoImportResult.Failed -> failed++
                        PhotoImportResult.Full -> {
                            failed++
                            break
                        }
                    }
                }
                if (failed > 0) events.trySend(PhotoWidgetEvent.ImportFailed)
                if (staged.isNotEmpty()) {
                    queue = staged
                    queueTotal = staged.size
                    queueAspect = widgetAspect()
                    openFirst()
                }
            } finally {
                importing.value = false
            }
        }
    }

    /** Sửa vùng cắt của ảnh đã có (bấm ô thumbnail). */
    fun startEdit(ref: PhotoRef) {
        if (session.value != null) return
        session.value = CropSession(ref.id, staged = false, position = 1, total = 1, aspect = widgetAspect(), crop = ref.crop)
    }

    /** Người dùng nhấc tay: nhớ vùng cắt hiện tại của phiên (chỉ đổi `crop`; Activity không nạp lại ảnh khi `photoId` không đổi). */
    fun onCropChanged(crop: CropRect) {
        session.value = session.value?.copy(crop = crop)
    }

    /** "Xong": cam kết [crop] cho ảnh đang cắt rồi sang ảnh kế (hoặc về danh sách). */
    fun commit(crop: CropRect) {
        val s = session.value ?: return
        viewModelScope.launch {
            val ok = repo.commitCrop(itemId, s.photoId, crop, s.aspect)
            if (!ok) {
                if (s.staged) repo.discardStaged(itemId, s.photoId)
                events.trySend(PhotoWidgetEvent.ImportFailed)
            }
            advance(s)
        }
    }

    /** "Bỏ ảnh này" (ảnh mới: xóa bản nháp) hoặc "Hủy" (sửa ảnh có sẵn: không đổi gì). */
    fun skip() {
        val s = session.value ?: return
        viewModelScope.launch {
            if (s.staged) repo.discardStaged(itemId, s.photoId)
            advance(s)
        }
    }

    /** Ảnh của phiên không đọc được: bỏ như [skip] kèm báo lỗi. */
    fun onImageUnreadable() {
        events.trySend(PhotoWidgetEvent.ImportFailed)
        skip()
    }

    /** Back đã được xác nhận: bỏ mọi ảnh mới chưa cam kết và thoát màn cắt. Ảnh đã cam kết giữ nguyên. */
    fun discardAll() {
        val pending = queue
        queue = emptyList()
        queueTotal = 0
        session.value = null
        viewModelScope.launch { pending.forEach { repo.discardStaged(itemId, it.photoId) } }
    }

    /** Thoát màn cắt khi đang sửa ảnh có sẵn (không có ảnh nháp để dọn). */
    fun cancelEdit() {
        if (session.value?.staged == false) session.value = null
    }

    fun remove(photoId: String) {
        viewModelScope.launch { repo.removePhoto(itemId, photoId) }
    }

    private fun advance(done: CropSession) {
        if (!done.staged) {
            session.value = null
            return
        }
        queue = queue.filterNot { it.photoId == done.photoId }
        if (queue.isEmpty()) {
            queueTotal = 0
            session.value = null
        } else {
            openFirst()
        }
    }

    private fun openFirst() {
        val first = queue.first()
        session.value = CropSession(
            photoId = first.photoId,
            staged = true,
            position = queueTotal - queue.size + 1,
            total = queueTotal,
            aspect = queueAspect,
            crop = CropRect.centered(first.width, first.height, queueAspect)
        )
    }

    /** Tỉ lệ khung hiện tại của widget (rộng/cao px thật lần vẽ gần nhất); chưa biết thì 1. */
    private fun widgetAspect(): Float =
        PhotoWidgetHub.sizeOf(itemId)?.let { (w, h) -> w.toFloat() / h } ?: 1f

    private companion object {
        const val KEY_AUTO_PICKED = "auto_picked"
    }
}
