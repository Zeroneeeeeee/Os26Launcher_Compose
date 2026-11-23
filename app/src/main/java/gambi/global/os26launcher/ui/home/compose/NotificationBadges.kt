package gambi.global.os26launcher.ui.home.compose

import gambi.global.os26launcher.domain.config.BadgeStyle
import gambi.global.os26launcher.domain.repository.BadgeTarget

/** BG: trạng thái huy hiệu thông báo (bật, kiểu, số theo app, app bị tắt riêng). JX: chỉ còn kiểu dữ liệu; Home Compose tự tính số (HomeBadges.kt). */
internal object NotificationBadges {

    data class State(
        val enabled: Boolean = false,
        val style: BadgeStyle = BadgeStyle.NUMBER,
        val counts: Map<BadgeTarget, Int> = emptyMap(),
        val disabledApps: Set<String> = emptySet()
    )
}
