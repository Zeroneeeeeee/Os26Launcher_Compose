package gambi.global.os26launcher.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.domain.usecase.ApplyThemeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * RS Task 6 — màn 1.1 "Màn hình chính mặc định" (Figma 302:1738). Lựa chọn khởi đầu = theme đang áp (chưa từng chọn thì Liquid
 * Glass 27, như onboarding 0.2); chạm thẻ chỉ đổi lựa chọn, theme chỉ ghi khi bấm nút. Lựa chọn sống qua dựng lại Activity (SavedStateHandle).
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val appearance: AppearanceRepository,
    private val applyTheme: ApplyThemeUseCase,
    private val saved: SavedStateHandle
) : ViewModel() {

    private val _theme = MutableStateFlow(saved.get<String>(KEY_THEME)?.let(::themeOf) ?: ThemeId.LIQUID_GLASS_27)
    val theme: StateFlow<ThemeId> = _theme.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    init {
        if (!saved.contains(KEY_THEME)) {
            viewModelScope.launch {
                appearance.config.first().themeId?.let { if (!saved.contains(KEY_THEME)) setTheme(it) }
            }
        }
    }

    fun choose(id: ThemeId) {
        setTheme(id)
    }

    /** Ghi theme đang chọn (một `update`, ApplyThemeUseCase) rồi gọi [then] trên main. Bấm lần nữa khi đang ghi thì bỏ qua. */
    fun apply(then: () -> Unit) {
        if (_applying.value) return
        _applying.value = true
        viewModelScope.launch {
            try {
                applyTheme(_theme.value)
            } finally {
                _applying.value = false
            }
            then()
        }
    }

    private fun setTheme(id: ThemeId) {
        _theme.value = id
        saved[KEY_THEME] = id.name
    }

    private fun themeOf(name: String): ThemeId? = ThemeId.entries.firstOrNull { it.name == name }

    private companion object {
        const val KEY_THEME = "theme"
    }
}
