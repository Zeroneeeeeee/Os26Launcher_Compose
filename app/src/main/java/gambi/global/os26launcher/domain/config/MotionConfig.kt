package gambi.global.os26launcher.domain.config

/** CF — nhóm "Chuyển cảnh" (4.1, 4.2). TR: 3 trường đầu (4.1) đọc qua `TransitionMotion`; các trường 4.2 chưa ai đọc. `docs/config-schema.md` mục 3.5. */
data class MotionConfig(
    val transitionStyle: TransitionStyle = TransitionStyle.SMOOTH,
    val transitionSpeed: MotionSpeed = MotionSpeed.MEDIUM,
    val reduceMotion: Boolean = false,
    val appOpenEffect: AppOpenEffect = AppOpenEffect.ZOOM_FROM_ICON,
    val appOpenSpeed: Int = 55,
    val pressShrink: Boolean = false,
    val hapticOnOpen: Boolean = false
)
