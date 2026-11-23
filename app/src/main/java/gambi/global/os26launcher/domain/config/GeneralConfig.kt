package gambi.global.os26launcher.domain.config

import gambi.global.os26launcher.domain.model.GridPreset

/** CF — nhóm "Chung" (màn 1.2) và khóa Ứng dụng ẩn (5.1). `docs/config-schema.md` mục 3.2. */
data class GeneralConfig(
    /** M8a: đổi qua `LayoutReconciler.changeGridPreset` (thứ tự ghi DataStore/Room), không `update` thẳng từ UI. */
    val gridPreset: GridPreset = GridPreset.DEFAULT,
    val navBarHidden: Boolean = false,
    val autoArrange: Boolean = true,
    val blurEffect: Boolean = true,
    val textFollowSystem: Boolean = true,
    val textScale: Int = 100,
    val hiddenAppsLock: Boolean = false
) {
    companion object {
        const val TEXT_SCALE_MIN = 80
        const val TEXT_SCALE_MAX = 130
    }
}
