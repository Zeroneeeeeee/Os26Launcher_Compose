package gambi.global.os26launcher.ui.settings

import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.HomeScreenConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * HT — màn "Lướt màn hình" (3.1). Hôm nay chỉ có công tắc "Hiện chấm trang"; PT (hiệu ứng chuyển trang) thêm hàng vào đây bằng cách sửa
 * lớp này. Trạng thái và ghi do [HomeTogglesViewModel]; Home áp ngay qua `HomeTogglesController`, không cần dựng lại.
 */
class SwipeSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val toggles = ViewModelProvider(activity)[HomeTogglesViewModel::class.java]
    private var current = HomeScreenConfig()

    override val titleRes: Int = R.string.settings_hub_swipe

    override fun rows(state: SettingsUiState): List<SettingsRow> = listOf(
        SettingsRow.Switch(
            SettingsRow.ID_HT_PAGE_DOTS,
            R.string.ht_swipe_page_dots,
            current.pageDots,
            R.string.ht_swipe_page_dots_description
        ),
        SettingsRow.Switch( // TV (Q6)
            SettingsRow.ID_TV_TODAY,
            R.string.tv_swipe_today,
            current.todayEnabled,
            R.string.tv_swipe_today_description
        )
    )

    override fun onValueClicked(row: SettingsRow.Value) {}

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        when (row.id) {
            SettingsRow.ID_HT_PAGE_DOTS -> toggles.setPageDots(checked)
            SettingsRow.ID_TV_TODAY -> toggles.setTodayEnabled(checked)
        }
    }

    override suspend fun observe() {
        coroutineScope {
            launch { // lưu thất bại -> Toast và dựng lại hàng từ trạng thái thật
                toggles.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
            launch {
                toggles.uiState.collect {
                    current = it
                    ctx.rerender(false)
                }
            }
        }
    }
}
