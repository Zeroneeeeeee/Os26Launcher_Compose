package gambi.global.os26launcher.data.photo

import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.core.coroutines.IoDispatcher
import gambi.global.os26launcher.domain.model.photo.CropRect
import gambi.global.os26launcher.domain.model.photo.PhotoImportResult
import gambi.global.os26launcher.domain.model.photo.PhotoRef
import gambi.global.os26launcher.domain.model.photo.PhotoWidgetConfig
import gambi.global.os26launcher.domain.photo.PhotoSource
import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G3b Task 1 — xem [PhotoWidgetRepository]. Trạng thái trong RAM là nguồn thật (map bất biến thay nguyên khối); file JSON chỉ là bản lưu.
 * Mọi ghi đi qua một [mutex] (cả file ảnh lẫn JSON) và chạy ở [io]; trạng thái mới chỉ phát SAU khi file ảnh đã ở đúng chỗ.
 */
@Singleton
class PhotoWidgetRepositoryImpl @Inject constructor(
    private val store: PhotoWidgetStore,
    private val files: PhotoFileStore,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : PhotoWidgetRepository {

    private val _widgets = MutableStateFlow<Map<Long, PhotoWidgetConfig>>(emptyMap())
    override val widgets: StateFlow<Map<Long, PhotoWidgetConfig>> = _widgets

    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val stored = withContext(io) { store.load() }
                mutex.withLock { if (stored != null) _widgets.value = stored }
            } finally {
                loaded.complete(Unit) // luôn hoàn tất: lệnh sau không treo ở awaitLoaded()
            }
        }
    }

    override suspend fun awaitLoaded() = loaded.await()

    override fun config(itemId: Long): PhotoWidgetConfig = _widgets.value[itemId] ?: PhotoWidgetConfig()

    override fun photoFile(itemId: Long, photoId: String, staged: Boolean): File? =
        files.photoFile(itemId, photoId, staged)

    override suspend fun clearStaging(itemId: Long) {
        awaitLoaded()
        mutex.withLock { withContext(io) { files.clearStaging(itemId) } }
    }

    override suspend fun importPhoto(itemId: Long, source: PhotoSource): PhotoImportResult {
        if (itemId <= 0L) return PhotoImportResult.Failed
        awaitLoaded()
        return mutex.withLock {
            if (config(itemId).photos.size >= PhotoWidgetConfig.MAX_PHOTOS) PhotoImportResult.Full
            else withContext(io) { files.importToStaging(itemId, source) }
        }
    }

    override suspend fun commitCrop(itemId: Long, photoId: String, crop: CropRect, aspect: Float): Boolean {
        if (itemId <= 0L || !crop.isValid || !aspect.isFinite() || aspect <= 0f || !PhotoFileStore.isValidId(photoId)) return false
        awaitLoaded()
        return mutex.withLock {
            val current = config(itemId)
            val existing = current.photos.indexOfFirst { it.id == photoId }
            val next: List<PhotoRef>
            if (existing >= 0) {
                if (withContext(io) { files.photoFile(itemId, photoId, staged = false) } == null) return@withLock false
                val old = current.photos[existing]
                next = current.photos.toMutableList().also { it[existing] = PhotoRef(photoId, crop, aspect, old.version + 1) }
            } else {
                if (current.photos.size >= PhotoWidgetConfig.MAX_PHOTOS) return@withLock false
                if (!withContext(io) { files.commitStaged(itemId, photoId) }) return@withLock false
                next = current.photos + PhotoRef(photoId, crop, aspect, 0)
            }
            publish(itemId, PhotoWidgetConfig(next))
            true
        }
    }

    override suspend fun discardStaged(itemId: Long, photoId: String) {
        awaitLoaded()
        mutex.withLock { withContext(io) { files.deletePhoto(itemId, photoId, staged = true) } }
    }

    override suspend fun removePhoto(itemId: Long, photoId: String) {
        awaitLoaded()
        mutex.withLock {
            val current = config(itemId)
            if (current.photos.none { it.id == photoId }) return@withLock
            publish(itemId, PhotoWidgetConfig(current.photos.filterNot { it.id == photoId }))
            withContext(io) { files.deletePhoto(itemId, photoId, staged = false) }
        }
    }

    override suspend fun forget(itemId: Long) {
        awaitLoaded()
        mutex.withLock {
            if (_widgets.value.containsKey(itemId)) publish(itemId, null)
            withContext(io) { files.deleteWidget(itemId) }
        }
    }

    override suspend fun sweep(existingItemIds: Set<Long>) {
        awaitLoaded()
        mutex.withLock {
            // TV-3: widget Ảnh của Today View không có hàng bố cục (id trong dải TodayWidget.ID_BASE); Today tự dọn khi xóa
            // (RemoveTodayWidgetUseCase), quét mồ côi không được chạm.
            fun orphan(id: Long) = id !in existingItemIds && !gambi.global.os26launcher.domain.today.TodayWidget.isTodayId(id)
            val orphanDirs = withContext(io) { files.listWidgetIds() }.filter(::orphan)
            if (orphanDirs.isNotEmpty()) withContext(io) { orphanDirs.forEach(files::deleteWidget) }
            val orphanConfigs = _widgets.value.keys.filter(::orphan)
            if (orphanConfigs.isNotEmpty()) {
                val next = _widgets.value.filterKeys { !orphan(it) }
                _widgets.value = next
                withContext(io) { store.save(next) }
            }
        }
    }

    /** Gọi trong [mutex]. Ghi file JSON rồi phát map mới ([config] null = xóa mục). Phát cả khi ghi JSON lỗi: RAM là nguồn thật. */
    private suspend fun publish(itemId: Long, config: PhotoWidgetConfig?) {
        val next = _widgets.value.toMutableMap().also {
            if (config == null || config.photos.isEmpty()) it.remove(itemId) else it[itemId] = config
        }
        withContext(io) { store.save(next) }
        _widgets.value = next
    }
}
