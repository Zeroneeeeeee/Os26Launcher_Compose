package gambi.global.os26launcher.domain.usecase

import gambi.global.os26launcher.core.ui.GridMetrics
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.DockRole
import gambi.global.os26launcher.domain.repository.DefaultAppResolverPort
import javax.inject.Inject

/**
 * 4 app mặc định cho dock, theo thứ tự PHONE, MESSAGES, BROWSER, CAMERA. Mỗi vai trò lấy ứng
 * viên đầu tiên (theo [DefaultAppResolverPort.candidates]) có trong [apps] và chưa nằm trong dock;
 * hết ứng viên (app đã gỡ, bị ẩn, hoặc không tìm được) thì bỏ qua vai trò;
 * thiếu bao nhiêu ô bù bằng app đầu bảng chữ cái CHƯA nằm trong dock. Không bao giờ trả quá [slots]
 * phần tử, không trả trùng. (M3 Task 4 Bước 2)
 */
class PickDefaultDockUseCase @Inject constructor(
    private val resolver: DefaultAppResolverPort
) {
    operator fun invoke(apps: List<AppItem>, slots: Int = GridMetrics.DOCK_SLOTS): List<AppItem> {
        if (slots <= 0) return emptyList()

        // So theo packageName, KHÔNG theo cả ComponentKey: resolveActivity() cho VIEW/CAMERA thường
        // trả về một activity "trung gian" xử lý riêng intent đó (vd Chrome: IntentDispatcher), khác
        // với activity icon màn hình chính mà LauncherApps liệt kê. PHONE/MESSAGES tình cờ trùng class
        // nên trước đây tưởng nhầm là so key đầy đủ cũng đúng. (M3 Task 4, phát hiện trên Live 4 — xem
        // AGENTS.md)
        val byPackage = apps.groupBy { it.key.packageName }
        val dock = LinkedHashSet<AppItem>()

        for (role in ROLE_ORDER) {
            if (dock.size >= slots) break
            val app = resolver.candidates(role).firstNotNullOfOrNull { pkg ->
                byPackage[pkg]?.firstOrNull()?.takeIf { it !in dock }
            } ?: continue
            dock.add(app)
        }

        if (dock.size < slots) {
            // apps đã sắp theo nhãn (AppRepository.apps dùng Collator), nên đây đã là thứ tự
            // bảng chữ cái — chỉ cần lấy lần lượt, bỏ qua app đã có trong dock.
            for (app in apps) {
                if (dock.size >= slots) break
                if (app !in dock) dock.add(app)
            }
        }

        return dock.toList()
    }

    private companion object {
        val ROLE_ORDER =
            listOf(DockRole.PHONE, DockRole.MESSAGES, DockRole.BROWSER, DockRole.CAMERA)
    }
}
