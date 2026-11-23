package gambi.global.os26launcher.data.notification

import android.app.Notification
import android.content.Context
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import gambi.global.os26launcher.domain.repository.BadgeTarget
import gambi.global.os26launcher.domain.repository.NotificationBadgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BG: kho số huy hiệu. Listener ([publish]) ghi cả ảnh chụp mỗi lần thông báo đổi; Home và Cài đặt chỉ đọc [counts].
 *
 * Lọc như Launcher3 (`NotificationListener.shouldBeFilteredOut`): bỏ thông báo tóm tắt nhóm, kênh tắt huy hiệu
 * (`Ranking.canShowBadge`), thông báo đang chạy của kênh mặc định, và thông báo không có tiêu đề lẫn nội dung.
 * Mỗi thông báo tính `Notification.number` nếu app đặt, không thì 1.
 */
@Singleton
class NotificationBadgeStore @Inject constructor(
    @ApplicationContext context: Context
) : NotificationBadgeRepository {

    private val userManager: UserManager = context.getSystemService(UserManager::class.java)

    private val _counts = MutableStateFlow<Map<BadgeTarget, Int>>(emptyMap())
    override val counts: StateFlow<Map<BadgeTarget, Int>> = _counts.asStateFlow()

    /** Đếm lại từ toàn bộ thông báo đang có. [ranking] null thì không lọc theo kênh. */
    fun publish(active: Array<StatusBarNotification>, ranking: NotificationListenerService.RankingMap?) {
        val out = HashMap<BadgeTarget, Int>()
        val r = NotificationListenerService.Ranking()
        for (sbn in active) {
            if (filteredOut(sbn, ranking, r)) continue
            val target = BadgeTarget(sbn.packageName, userManager.getSerialNumberForUser(sbn.user))
            val n = sbn.notification.number.takeIf { it > 0 } ?: 1
            out[target] = (out[target] ?: 0) + n
        }
        _counts.value = out
    }

    fun clear() {
        _counts.value = emptyMap()
    }

    private fun filteredOut(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap?,
        r: NotificationListenerService.Ranking
    ): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        if (ranking != null && ranking.getRanking(sbn.key, r)) {
            if (!r.canShowBadge()) return true
            if (r.channel?.id == DEFAULT_CHANNEL_ID && n.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        }
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)
        return title.isNullOrEmpty() && text.isNullOrEmpty()
    }

    private companion object {
        /** `NotificationChannel.DEFAULT_CHANNEL_ID` (ẩn API): kênh của app chưa khai báo kênh. */
        const val DEFAULT_CHANNEL_ID = "miscellaneous"
    }
}
