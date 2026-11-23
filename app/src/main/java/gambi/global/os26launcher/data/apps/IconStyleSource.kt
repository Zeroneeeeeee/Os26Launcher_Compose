package gambi.global.os26launcher.data.apps

import android.util.Log
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.model.IconStyle
import gambi.global.os26launcher.domain.model.IconStyleKey
import gambi.global.os26launcher.domain.model.IconTint
import gambi.global.os26launcher.domain.config.AppearanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * G2b — kiểu icon hiện tại, giữ sẵn trong bộ nhớ để [IconLoader] không đọc DataStore cho từng icon.
 *
 * Giá trị đầu là `null` (KHÔNG phải mặc định): [current] chờ DataStore phát giá trị thật đầu tiên, nên lần nạp đầu
 * sau khi process khởi động không bao giờ dùng nhầm kiểu mặc định. Khi kiểu đổi, xóa [IconCache] (khóa cache đã mang
 * kiểu nên icon cũ không bao giờ bị trả nhầm; xóa chỉ để giải phóng RAM).
 *
 * IP: khóa còn mang gói biểu tượng (`package@versionCode`). Gói đã chọn bị gỡ -> ghi lại "không dùng" vào DataStore; gói
 * cập nhật -> khóa đổi theo versionCode nên cache RAM và đĩa không trả icon của bản cũ.
 */
@Singleton
class IconStyleSource @Inject constructor(
    private val appearance: AppearanceRepository,
    private val iconCache: IconCache,
    private val iconPacks: IconPackManager,
    private val dataSource: LauncherAppsDataSource,
    @ApplicationScope private val scope: CoroutineScope
) {
    @Volatile
    private var previous: IconStyleKey? = null

    private data class Selection(val style: IconStyle, val tint: IconTint, val pack: String?, val bevel: Boolean, val corner: Int)

    @OptIn(ExperimentalCoroutinesApi::class)
    val config: StateFlow<IconStyleKey?> = appearance.config
        // Màu tô chỉ có nghĩa với TINTED: chuẩn hóa để đổi màu khi đang ở kiểu khác không xóa cache vô ích.
        .map {
            Selection(
                it.iconStyle,
                if (it.iconStyle == IconStyle.TINTED) it.iconTint else IconTint.DEFAULT,
                it.iconPack,
                IconStyleKey.bevelFor(it.iconStyle, it.iconBevel),
                it.glassLook.corner
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { sel ->
            if (sel.pack == null) {
                flowOf(IconStyleKey(sel.style, sel.tint, bevel = sel.bevel, corner = sel.corner))
            } else {
                // Phát lại khi chính gói đó đổi (cập nhật/gỡ); callback LauncherApps báo mọi package, không chỉ app launcher.
                dataSource.observeChanges()
                    .filter { sel.pack in it.packageNames }
                    .onEach { iconPacks.forget(sel.pack) }
                    .map { }
                    .onStart { emit(Unit) }
                    .map { IconStyleKey(sel.style, sel.tint, packKeyOrRevert(sel.pack), sel.bevel, sel.corner) }
            }
        }
        .distinctUntilChanged()
        .onEach { new ->
            if (previous != null && previous != new) iconCache.clear()
            // C11: đổi gói (hoặc bỏ gói) thì quên bản đã phân tích của gói trước, không giữ Resources/map ~3500 mục của mọi gói từng chọn.
            if (previous?.pack != new.pack) iconPacks.retainOnly(new.pack.ifEmpty { null })
            previous = new
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): IconStyleKey = config.value ?: config.filterNotNull().first()

    /** Khóa của gói; gói không còn cài thì ghi lại "không dùng" (DataStore phát lại, flow trên chuyển sang không gói). */
    private fun packKeyOrRevert(pack: String): String {
        val key = iconPacks.keyOf(pack)
        if (key == null) {
            Log.i("IP", "selected pack gone, revert to none")
            scope.launch {
                try {
                    appearance.update { it.copy(iconPack = null) }
                } catch (e: Exception) {
                    Log.w("IP", "revert icon pack failed", e)
                }
            }
        }
        return key ?: ""
    }
}
