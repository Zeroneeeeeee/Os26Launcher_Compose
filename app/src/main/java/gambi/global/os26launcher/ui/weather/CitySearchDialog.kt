package gambi.global.os26launcher.ui.weather

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsRow
import gambi.global.os26launcher.core.designsystem.compose.DsSearchField
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError

/**
 * WX Task 3 — hộp tìm thành phố dùng chung: ô nhập, nút Tìm, tối đa 5 kết quả "Tên, tỉnh, mã nước"; bấm một dòng thì gọi [onPicked] rồi đóng.
 * Một hộp mỗi lúc (chống chạm đúp). Chủ sở hữu (màn Cài đặt) gọi [dismiss] ở `onDestroy` để không rò cửa sổ. Không log truy vấn hay kết quả.
 */
class CitySearchDialog(
    private val activity: ComponentActivity,
    private val onPicked: (GeoPlace) -> Unit
) {
    private val viewModel = ViewModelProvider(activity)[CitySearchViewModel::class.java]
    private var dialog: DsDialogHandle? = null
    val isShowing: Boolean get() = dialog != null

    fun show(@StringRes title: Int) {
        if (dialog != null) return
        viewModel.reset()
        val enabled = viewModel.hasKey
        // JS3: hộp thoại Compose (thay DsDialog View + dialog_city_search.xml).
        dialog = activity.dsDialogs.show { handle ->
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            var query by remember { mutableStateOf("") }
            val focus = remember { FocusRequester() }
            val doSearch = { viewModel.search(query) }
            DsAlertDialog(
                onDismiss = handle::dismiss,
                title = activity.getString(title),
                positive = null,
                negative = activity.getString(R.string.settings_cancel) to {}
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!enabled) BasicText(activity.getString(R.string.weather_sheet_no_key), style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DsSearchField(
                            value = query,
                            onValueChange = { if (enabled) query = it },
                            hint = activity.getString(R.string.weather_sheet_hint),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            onSearch = { if (enabled) doSearch() } // phím Search của bàn phím mềm (và Enter của bàn phím cứng)
                        )
                        DsButton(
                            activity.getString(R.string.weather_sheet_search),
                            onClick = { doSearch() },
                            modifier = Modifier.padding(start = 8.dp),
                            size = DsButtonSize.M,
                            enabled = enabled
                        )
                    }
                    val status = statusText(ui.status)
                    if (status.isNotEmpty()) BasicText(status, style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary)))
                    ui.results.forEachIndexed { i, place ->
                        DsRow(
                            title = label(place),
                            onClick = {
                                onPicked(place)
                                handle.dismiss()
                            },
                            showDivider = i < ui.results.lastIndex
                        )
                    }
                }
                LaunchedEffect(Unit) { if (enabled) focus.requestFocus() }
            }
        }.also { d -> d.setOnDismissListener { dialog = null; viewModel.reset() } }
    }

    /** Gọi ở `onDestroy` của chủ sở hữu: gỡ listener (đã gắn trong [show]) rồi đóng. */
    fun dismiss() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        viewModel.reset()
    }

    private fun label(p: GeoPlace): String =
        listOfNotNull(p.displayName, p.admin, p.countryCode).joinToString(", ")

    private fun statusText(s: CitySearchStatus): String = when (s) {
        CitySearchStatus.Idle -> ""
        CitySearchStatus.Searching -> activity.getString(R.string.weather_sheet_searching)
        CitySearchStatus.NotFound -> activity.getString(R.string.weather_sheet_not_found)
        is CitySearchStatus.Failed -> activity.getString(
            when (s.error) {
                WeatherError.NETWORK -> R.string.weather_error_network
                WeatherError.INVALID_KEY -> R.string.weather_error_key
                WeatherError.RATE_LIMITED -> R.string.weather_error_rate
                WeatherError.NOT_FOUND -> R.string.weather_error_not_found
                else -> R.string.weather_error_generic
            }
        )
    }
}
