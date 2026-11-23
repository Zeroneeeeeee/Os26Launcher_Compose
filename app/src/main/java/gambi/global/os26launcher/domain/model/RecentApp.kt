package gambi.global.os26launcher.domain.model

/** M8d Task 5 — một mục lịch sử mở app của chính launcher: số lần mở và lần mở gần nhất (epoch millis). */
data class RecentApp(
    val key: ComponentKey,
    val launchCount: Int,
    val lastLaunchedMillis: Long
)
