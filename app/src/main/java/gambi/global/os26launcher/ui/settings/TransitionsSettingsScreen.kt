package gambi.global.os26launcher.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.MotionConfig
import gambi.global.os26launcher.domain.config.MotionSpeed
import gambi.global.os26launcher.domain.config.TransitionStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * TR — màn "Hiệu ứng chuyển cảnh" (4.1, Figma 302:2522). Lưu ở `MotionConfig` (transitionStyle, transitionSpeed, reduceMotion);
 * thư mục, nhóm App Library, tìm kiếm, App Library và Cài đặt nhanh đọc qua `TransitionMotion` từ lần mở/đóng kế tiếp.
 * Card xem trước là hình tĩnh.
 */
class TransitionsSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[TransitionsSettingsViewModel::class.java]
    private var config = MotionConfig()

    override val titleRes: Int = R.string.settings_hub_transitions

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>(STYLES.size + 4)
        rows.add(SettingsRow.TransitionPreview(ID_PREVIEW))
        for (option in STYLES) {
            rows.add(
                SettingsRow.Radio(
                    ID_STYLE_PREFIX + option.style.name,
                    option.title,
                    config.transitionStyle == option.style,
                    option.description
                )
            )
        }
        rows.add(SettingsRow.CardBreak(ID_BREAK))
        rows.add(
            SettingsRow.Segmented(
                ID_SPEED,
                R.string.tr_speed,
                SPEEDS.map { activity.getString(it.second) },
                SPEEDS.indexOfFirst { it.first == config.transitionSpeed }
            )
        )
        rows.add(
            SettingsRow.Switch(ID_REDUCE_MOTION, R.string.tr_reduce_motion, config.reduceMotion, R.string.tr_reduce_motion_description)
        )
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    override fun onRadioClicked(row: SettingsRow.Radio) {
        val chosen = STYLES.firstOrNull { ID_STYLE_PREFIX + it.style.name == row.id }?.style ?: return
        if (chosen != config.transitionStyle) viewModel.setStyle(chosen)
    }

    override fun onSegmentChanged(row: SettingsRow.Segmented, index: Int) {
        if (row.id != ID_SPEED) return
        val chosen = SPEEDS.getOrNull(index)?.first ?: return
        if (chosen != config.transitionSpeed) viewModel.setSpeed(chosen)
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        if (row.id == ID_REDUCE_MOTION) viewModel.setReduceMotion(checked)
    }

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.config.collect {
                    config = it
                    ctx.rerender(false)
                }
            }
            launch { // lưu thất bại -> Toast và dựng lại hàng từ trạng thái thật
                viewModel.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
        }
    }

    private class StyleOption(val style: TransitionStyle, @StringRes val title: Int, @StringRes val description: Int = 0)

    private companion object {
        /** Thứ tự Figma: Mượt (Mặc định), Mờ dần, Trượt, Phóng to, Không có (Nhanh nhất). */
        val STYLES = listOf(
            StyleOption(TransitionStyle.SMOOTH, R.string.tr_style_smooth, R.string.tr_style_smooth_description),
            StyleOption(TransitionStyle.FADE, R.string.tr_style_fade),
            StyleOption(TransitionStyle.SLIDE, R.string.tr_style_slide),
            StyleOption(TransitionStyle.ZOOM, R.string.tr_style_zoom),
            StyleOption(TransitionStyle.NONE, R.string.tr_style_none, R.string.tr_style_none_description)
        )
        val SPEEDS = listOf(
            MotionSpeed.SLOW to R.string.tr_speed_slow,
            MotionSpeed.MEDIUM to R.string.tr_speed_medium,
            MotionSpeed.FAST to R.string.tr_speed_fast
        )
        const val ID_PREVIEW = "tr_preview"
        const val ID_STYLE_PREFIX = "tr_style:"
        const val ID_BREAK = "tr_break"
        const val ID_SPEED = "tr_speed"
        const val ID_REDUCE_MOTION = "tr_reduce_motion"
    }
}
