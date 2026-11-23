package gambi.global.os26launcher.core.glass

import gambi.global.os26launcher.domain.model.GlassCustom
import gambi.global.os26launcher.domain.model.GlassLevelOverride
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * GS: cấu hình kính người dùng đặt = mức kính + công tắc từng phần tử. Công tắc chỉ có nghĩa ở [GlassLevelOverride.CUSTOM]; ở mức
 * khác [of] đặt chúng về "tắt hết" để hai cấu hình chỉ khác công tắc ngoài Tùy chỉnh so sánh bằng nhau (không dựng lại Home vô ích).
 *
 * GT: [look] = thông số kính dùng chung (khúc xạ, bóng, mờ, bo góc), có nghĩa ở MỌI mức kính. Nằm trong cấu hình để Home dựng lại
 * khi nó đổi, như mức kính.
 */
data class GlassConfig(val override: GlassLevelOverride, val custom: GlassCustom, val look: GlassLook = GlassLook.DEFAULT) {
    /** Công tắc đưa vào [GlassPolicy]: null trừ khi mức là Tùy chỉnh. */
    val activeCustom: GlassCustom? get() = if (override == GlassLevelOverride.CUSTOM) custom else null

    companion object {
        fun of(override: GlassLevelOverride, custom: GlassCustom, look: GlassLook = GlassLook.DEFAULT): GlassConfig =
            GlassConfig(
                override,
                if (override == GlassLevelOverride.CUSTOM) custom else GlassCustom.NONE,
                look
            )
    }
}
