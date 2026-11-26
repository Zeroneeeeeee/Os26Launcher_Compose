package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.BuildConfig
import gambi.global.os26launcher.core.coroutines.DefaultDispatcher
import gambi.global.os26launcher.domain.category.AppGroupPlanner
import gambi.global.os26launcher.domain.category.AppGroupTitleProvider
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.AppGroupKind
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.model.DockApp
import gambi.global.os26launcher.domain.model.HomePage
import gambi.global.os26launcher.domain.model.PlacedApp
import gambi.global.os26launcher.domain.model.PlacedFolder
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AG (2026-11-26) — dữ liệu mục "Gom nhóm ứng dụng" của Cài đặt → Chung: với mỗi [AppGroupKind], app nằm phẳng trên lưới Home thuộc nhóm
 * ([AppGroupPlanner.candidates]) và thư mục trùng tên nhóm đã có (lưới rồi dock; so không phân biệt hoa thường). Tên nhóm lấy từ
 * [AppGroupTitleProvider] (mở lại 2026-11-26: cùng nguồn với bố cục lần đầu, nên ngay sau seed mọi nhóm đã gom hiện "Đã gom"). Ghi qua
 * [LayoutRepository.groupIntoFolder].
 */
@HiltViewModel
class AppGroupsViewModel @Inject constructor(
    private val layout: LayoutRepository,
    private val appRepository: AppRepository,
    private val titles: AppGroupTitleProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    /**
     * Một hàng nhóm. [apps] = app sẽ được gom (đã cắt theo chỗ còn của thư mục, tối đa [FolderPlanner.MAX_ITEMS]); [folderId] = thư mục
     * trùng tên đã có (null: gom sẽ tạo thư mục mới ở ô của app đầu).
     */
    data class AppGroupState(
        val kind: AppGroupKind,
        val title: String,
        val apps: List<PlacedApp>,
        val folderId: Long?
    ) {
        /** Thư mục mới cần ≥ 2 app; thêm vào thư mục có sẵn cần ≥ 1. */
        val enabled: Boolean get() = if (folderId == null) apps.size >= 2 else apps.isNotEmpty()
    }

    sealed interface GroupResult {
        data class Done(val count: Int, val title: String) : GroupResult
        data object Failed : GroupResult
    }

    /** Khóa ngôn ngữ của màn đang hiện ([onScreenLocale]); đổi thì đọc lại tên nhóm (ViewModel sống qua recreate khi đổi ngôn ngữ). */
    private val localeKey = MutableStateFlow("")

    /** null = đang nạp (chưa có bố cục). Tên đọc lại khi bố cục hoặc ngôn ngữ app đổi. */
    val groups: StateFlow<List<AppGroupState>?> = combine(layout.pages, layout.dock, localeKey) { pages, dock, _ ->
        AppGroupKind.entries.map { build(it, titles.title(it), pages, dock) }
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Màn gọi mỗi lần dựng (kể cả sau recreate do đổi ngôn ngữ), với khóa ngôn ngữ chuỗi của Activity. */
    fun onScreenLocale(key: String) {
        localeKey.value = key
    }

    private val _results = Channel<GroupResult>(Channel.BUFFERED)
    val results: Flow<GroupResult> = _results.receiveAsFlow()

    private var writing = false

    /** Bấm "Gom nhóm" / "Thêm N ứng dụng". Chặn chạm đúp: đang ghi thì bỏ qua. */
    fun apply(state: AppGroupState) {
        if (writing || !state.enabled) return
        writing = true
        viewModelScope.launch {
            try {
                val moved = layout.groupIntoFolder(state.title, state.apps.map { it.itemId }, state.folderId)
                _results.send(if (moved > 0) GroupResult.Done(moved, state.title) else GroupResult.Failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Lỗi DB (transaction đã rollback): báo không gom được thay vì crash.
                Log.w(TAG, "groupIntoFolder failed ${e.javaClass.name}")
                _results.send(GroupResult.Failed)
            } finally {
                writing = false
            }
        }
    }

    suspend fun loadIcon(key: ComponentKey, sizePx: Int): Bitmap? = appRepository.icon(key, sizePx)

    private fun build(kind: AppGroupKind, title: String, pages: List<HomePage>, dock: List<DockApp>): AppGroupState {
        val wanted = title.trim()
        val folder: PlacedFolder? = (pages.sortedBy { it.index }.flatMap { it.items.filterIsInstance<PlacedFolder>() } + dock.mapNotNull { it.folder })
            .firstOrNull { it.title.trim().equals(wanted, ignoreCase = true) }
        val room = FolderPlanner.MAX_ITEMS - (folder?.childIds?.size ?: 0)
        val apps = AppGroupPlanner.candidates(kind, pages, BuildConfig.APPLICATION_ID).take(room.coerceAtLeast(0))
        return AppGroupState(kind, title, apps, folder?.itemId)
    }

    private companion object {
        const val TAG = "AppGroupsViewModel"
    }
}
