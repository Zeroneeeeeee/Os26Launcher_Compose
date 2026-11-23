package gambi.global.os26launcher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.coroutines.ApplicationScope
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.usecase.ApplyThemeUseCase
import gambi.global.os26launcher.domain.usecase.ObserveThemeStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * CF — CHỈ BẢN DEBUG (src/debug, không vào release). Áp theme bằng adb khi chưa có màn 1.1/onboarding; bảo vệ bằng
 * `android:permission=DUMP` như CP/WN.
 *
 * `adb shell am broadcast -a gambi.global.os26launcher.CF_APPLY_THEME --es id DARK_PRO -n gambi.global.os26launcher/gambi.global.os26launcher.debug.ThemeDebugReceiver`
 *
 * `id` = tên [ThemeId] để áp; `NONE` = bỏ `appearance_theme_id` (không đổi trường nào khác); `STATE` = chỉ in trạng thái;
 * `OB_RESET` = đặt lại cờ đã xem Onboarding (plan OB: kiểm lại luồng lần đầu không phải xóa dữ liệu; force-stop rồi bấm Home).
 * Kết quả in ở logcat tag `CF`: `ThemeState` sau khi áp (id, modified).
 */
class ThemeDebugReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun cfApplyTheme(): ApplyThemeUseCase

        fun cfObserveThemeState(): ObserveThemeStateUseCase

        fun cfAppearance(): AppearanceRepository

        fun cfAppState(): AppStateRepository

        @ApplicationScope
        fun cfAppScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_APPLY) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val arg = intent.getStringExtra(EXTRA_ID).orEmpty()
        val pending = goAsync()
        deps.cfAppScope().launch {
            try {
                when (arg) {
                    "STATE" -> Unit
                    "NONE" -> deps.cfAppearance().update { it.copy(themeId = null) }
                    "OB_RESET" -> deps.cfAppState().setOnboardingDone(false)
                    else -> {
                        val id = ThemeId.entries.firstOrNull { it.name == arg }
                        if (id == null) Log.w(TAG, "unknown theme id '$arg', expected ${ThemeId.entries.joinToString()}, NONE or STATE")
                        else deps.cfApplyTheme()(id)
                    }
                }
                val state = deps.cfObserveThemeState()().first()
                val c = deps.cfAppearance().config.first()
                val onboardingDone = deps.cfAppState().state.first().onboardingDone
                Log.i(TAG, "arg=$arg onboardingDone=$onboardingDone state=$state wallpaper=${c.wallpaperKind}/#${Integer.toHexString(c.wallpaperColor)} icon=${c.iconStyle}/${c.iconTint} labels=${c.showLabels} glass=${c.glassLevel} look=${c.glassLook}")
            } catch (e: Exception) {
                Log.e(TAG, "apply theme failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "CF"
        const val ACTION_APPLY = "gambi.global.os26launcher.CF_APPLY_THEME"
        const val EXTRA_ID = "id"
    }
}
