package gambi.global.os26launcher.core.designsystem

/**
 * DS3 Task 2: các bề mặt v3. Bảng lớp vẽ của từng bề mặt ở `docs/plans/DS3-design-system-v3.md` Task 2 Step 1.
 *
 * [followsGloss] = theo thanh "Độ bóng" của kính (GT, quyết định 8): nền đặc qua `GlassTuning.tint`, viền sáng mép trên qua
 * `GlassTuning.border`. [darkPress] = lúc nhấn phủ đen 8% (bề mặt sáng/màu) thay cho trắng 8% (bề mặt tối).
 */
enum class DsSurface(val followsGloss: Boolean, val darkPress: Boolean) {
    CARD(followsGloss = true, darkPress = false),
    CONTROL(followsGloss = true, darkPress = false),
    CONTROL_SELECTED(followsGloss = false, darkPress = true),
    SHEET(followsGloss = true, darkPress = false),
    PRIMARY(followsGloss = false, darkPress = true),
    BADGE_WARNING(followsGloss = false, darkPress = false),
    BADGE_DANGER(followsGloss = false, darkPress = false),
    BADGE_NEW(followsGloss = false, darkPress = false)
}
