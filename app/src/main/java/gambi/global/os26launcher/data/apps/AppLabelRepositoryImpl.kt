package gambi.global.os26launcher.data.apps

import android.util.Log
import gambi.global.os26launcher.data.layout.db.AppOverrideDao
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.AppLabelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RN — tên riêng của từng app. CF: lưu ở cột `customLabel` của bảng Room `app_overrides` (trước CF: DataStore `app_labels`). Lỗi đọc thành
 * bản đồ rỗng: lỗi ở đây không được làm `AppRepository.apps` ngừng phát (ApplicationScope không có handler). `distinctUntilChanged` để
 * ghi không đổi nội dung (vd đổi trạng thái ẩn cùng bảng) không làm danh sách app phát lại.
 */
@Singleton
class AppLabelRepositoryImpl @Inject constructor(
    private val dao: AppOverrideDao
) : AppLabelRepository {

    override val customLabels: Flow<Map<ComponentKey, String>> = dao.observeLabels()
        .map { rows ->
            rows.filter { it.customLabel.isNotBlank() }
                .associate { ComponentKey(it.packageName, it.className, it.userSerial) to it.customLabel }
        }
        .catch { e ->
            Log.w(TAG, "custom labels unreadable, using none", e)
            emit(emptyMap())
        }
        .distinctUntilChanged()

    override suspend fun setCustomLabel(key: ComponentKey, label: String?) =
        dao.setLabel(key.packageName, key.className, key.userSerial, label?.takeIf { it.isNotBlank() })

    override suspend fun removePackage(packageName: String, userSerial: Long) = dao.clearLabelsOfPackage(packageName, userSerial)

    override suspend fun removeUser(userSerial: Long) = dao.clearLabelsOfUser(userSerial)

    private companion object {
        const val TAG = "AppLabelRepo"
    }
}
