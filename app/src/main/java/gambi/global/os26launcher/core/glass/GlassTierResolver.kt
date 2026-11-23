package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassLevelOverride
import javax.inject.Inject

/**
 * KHÔNG tự đọc PowerManager/high-contrast ở đây: thuộc tính `accessibilityMode` (mặc định AUTO) của
 * chính LiquidGlassView đã tự tôn trọng high-contrast/tiết kiệm pin ở tầng thư viện (mục 7
 * LiquidGlass-Android.md) -- đọc lại ở lớp này là trùng lặp hai tầng logic.
 */
class GlassTierResolver @Inject constructor() {
    /**
     * Máy có đường Lens thật (AGSL + `RenderEffect`, API 33+) hay không, THEO API hiệu lực của thư viện, bất kể mức kính người
     * dùng chọn. Tier suy từ `FORCE_HIGH` là LENS dù máy API 30 chỉ có đường chụp bằng CPU: tier không phải là khả năng. Dùng
     * cho mọi thứ chỉ đo/đúng trên đường Lens (kính thật của panel, ngân sách 24 ô); cùng ngưỡng với nhánh AUTO bên dưới.
     */
    fun isRealGlassCapable(effectiveApiLevel: Int): Boolean = effectiveApiLevel >= LENS_API

    fun resolve(effectiveApiLevel: Int, override: GlassLevelOverride): GlassTier = when (override) {
        GlassLevelOverride.FORCE_LOW -> GlassTier.CLASSIC_CPU
        GlassLevelOverride.FORCE_HIGH -> GlassTier.LENS
        // GS: CUSTOM chỉ đổi cách quyết định bốn phần tử có công tắc; tier (ngân sách ô thư mục/ô nhóm) theo AUTO.
        GlassLevelOverride.AUTO, GlassLevelOverride.CUSTOM -> when {
            isRealGlassCapable(effectiveApiLevel) -> GlassTier.LENS
            effectiveApiLevel >= 31 -> GlassTier.CLASSIC_GPU
            else -> GlassTier.CLASSIC_CPU
        }
    }

    private companion object {
        const val LENS_API = 33
    }
}
