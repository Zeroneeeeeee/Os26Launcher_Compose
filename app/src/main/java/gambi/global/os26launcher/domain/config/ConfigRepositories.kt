package gambi.global.os26launcher.domain.config

import gambi.global.os26launcher.domain.model.GlassCustom
import kotlinx.coroutines.flow.Flow

/**
 * CF — hợp đồng chung của mọi nhóm config trong DataStore `launcher_settings` (`docs/config-schema.md` mục 1).
 * [config] đã `distinctUntilChanged`, lỗi đọc file trả mặc định. [update] = một `DataStore.edit`: đọc giá trị hiện tại, áp [transform],
 * chỉ ghi khóa có giá trị đổi (khóa chưa từng chỉnh vẫn vắng); trả về SAU KHI ghi xong.
 */
interface ConfigRepository<T> {
    val config: Flow<T>
    suspend fun update(transform: (T) -> T)
}

interface AppearanceRepository : ConfigRepository<AppearanceConfig> {
    /** GS D8: đã từng lưu công tắc kính tùy chỉnh (có ít nhất một khóa dock/panels/search_bar/pill trong file). */
    val glassCustomSaved: Flow<Boolean>

    /** GS: ghi CẢ năm công tắc kể cả khi trùng mặc định (gieo lúc chuyển sang Tùy chỉnh lần đầu), để [glassCustomSaved] thành true. */
    suspend fun seedGlassCustom(custom: GlassCustom)
}

interface GeneralConfigRepository : ConfigRepository<GeneralConfig>

interface HomeScreenConfigRepository : ConfigRepository<HomeScreenConfig>

interface SearchConfigRepository : ConfigRepository<SearchConfig>

interface MotionConfigRepository : ConfigRepository<MotionConfig>

interface AppStateRepository {
    val state: Flow<AppState>
    suspend fun setOnboardingDone(done: Boolean)
}
