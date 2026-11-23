package gambi.global.os26launcher.ui.weather

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsCardGroup
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherState
import gambi.global.os26launcher.ui.settings.SettingsActivity
import gambi.global.os26launcher.ui.settings.SettingsScreenId
import gambi.global.os26launcher.ui.widget.WeatherWidgetHub
import java.util.Date

/**
 * G3e Task 6b + WX Task 5 (D2) — bảng Thời tiết: bấm một widget thời tiết (ngoài edit mode) thì mở bảng này để CHỌN ĐỊA ĐIỂM cho widget đó
 * (radio: "Vị trí hiện tại" và các thành phố đã thêm), kèm giờ cập nhật, nút "Quản lý địa điểm" (mở Cài đặt → Thời tiết) và câu ghi công OpenWeather.
 * Widget được chọn lấy từ [WeatherWidgetHub.tappedWidgetId] lúc [show]. Logic ở [WeatherSheetViewModel].
 *
 * JS3: Compose (`DsBottomSheet` qua `dsDialogs`). Là cửa sổ riêng (`Dialog`) nên `recreate()` làm mất nó (chấp nhận như bảng "Tiện ích", G2c). Đóng ở [onDestroy] để không rò cửa sổ.
 * Không còn xin quyền vị trí ở đây (nay ở Cài đặt → Vị trí), nên không cần đăng ký launcher lúc dựng.
 */
class WeatherSheetController(private val activity: ComponentActivity) {
    private val viewModel = ViewModelProvider(activity)[WeatherSheetViewModel::class.java]
    private var dialog: DsDialogHandle? = null

    fun show() {
        if (dialog?.isShowing == true) return
        viewModel.bindWidget(WeatherWidgetHub.tappedWidgetId)
        // JS3: bottom sheet DS3 bằng Compose (thay BottomSheetDialog + view_weather_sheet.xml).
        dialog = activity.dsDialogs.show { handle ->
            DsBottomSheet(onDismiss = handle::dismiss, title = stringResource(R.string.weather_sheet_title)) {
                SheetContent(onClose = handle::dismiss)
            }
        }.also { d -> d.setOnDismissListener { dialog = null } }
    }

    /** B10 (FX2): nút Home đóng bảng (như Back). An toàn khi gọi nhiều lần. */
    fun closeIfOpen() {
        dialog?.dismiss()
    }

    /** Gọi ở `HomeActivity.onDestroy`: đóng bảng. */
    fun onDestroy() {
        dialog?.dismiss()
        dialog = null
    }

    @Composable
    private fun SheetContent(onClose: () -> Unit) {
        val ui by viewModel.ui.collectAsStateWithLifecycle()
        val primary = colorResource(R.color.ds_text_primary)
        val secondary = colorResource(R.color.ds_text_secondary)
        // Như ScrollView của view_weather_sheet.xml: nhiều địa điểm thì cuộn trong bảng, nút "Quản lý" và câu ghi công không bị đẩy khỏi màn.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!ui.hasKey) BasicText(stringResource(R.string.weather_sheet_no_key), style = DsTextStyles.body.copy(color = secondary))
            BasicText(stringResource(R.string.wx_sheet_for_widget), style = DsTextStyles.bodyStrong.copy(color = secondary))
            // Radio: "Vị trí hiện tại" và các thành phố đã thêm; chọn hàng chưa chọn thì ghi (đặt từ dữ liệu không tính là bấm).
            DsCardGroup {
                ui.options.forEach { option ->
                    row(
                        title = optionLabel(option),
                        onClick = { if (option.placeId != ui.selectedId) viewModel.select(option.placeId) },
                        trailing = DsRowTrailing.Radio(option.placeId == ui.selectedId)
                    )
                }
            }
            val status = statusText(ui)
            if (status.isNotEmpty()) BasicText(status, style = DsTextStyles.caption.copy(color = secondary))
            DsButton(
                stringResource(R.string.wx_sheet_manage),
                onClick = {
                    activity.startActivity(
                        Intent(activity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, SettingsScreenId.WEATHER.name)
                    )
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                variant = DsButtonVariant.Secondary
            )
            // Câu ghi công + liên kết tới openweathermap.org (Q4: chỉ chữ + liên kết ở bản đầu, logo kiểm trước khi phát hành).
            BasicText(
                stringResource(R.string.weather_attribution),
                style = DsTextStyles.caption.copy(color = primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.weather_attribution_url))))
                        } catch (e: Exception) {
                            // không có trình duyệt: bỏ qua
                        }
                    }
                    .padding(vertical = 14.dp)
            )
        }
    }

    private fun label(p: GeoPlace): String =
        listOfNotNull(p.displayName, p.admin, p.countryCode).joinToString(", ")

    private fun optionLabel(o: SheetOption): String = when {
        !o.isCurrent -> label(o.place!!)
        o.place == null -> activity.getString(R.string.wx_sheet_current_unset)
        else -> activity.getString(R.string.wx_sheet_current_named, label(o.place))
    }

    private fun statusText(ui: SheetUi): String = when (val w = ui.weather) {
        is WeatherState.Ready -> {
            val time = DateFormat.getTimeFormat(activity).format(Date(w.snapshot.fetchedAtMillis))
            val base = activity.getString(R.string.weather_updated_at, time) + "\n" +
                    activity.getString(R.string.weather_sheet_hi_lo_note)
            if (w.lastError != null) errorText(w.lastError) + "\n" + base else base
        }

        is WeatherState.Failed -> errorText(w.error)
        WeatherState.Loading -> activity.getString(R.string.weather_sheet_refreshing)
        WeatherState.NoLocation -> activity.getString(R.string.wx_sheet_no_location)
        WeatherState.NoKey -> ""
    }

    private fun errorText(e: WeatherError): String = activity.getString(
        when (e) {
            WeatherError.NETWORK -> R.string.weather_error_network
            WeatherError.INVALID_KEY -> R.string.weather_error_key
            WeatherError.RATE_LIMITED -> R.string.weather_error_rate
            WeatherError.NOT_FOUND -> R.string.weather_error_not_found
            else -> R.string.weather_error_generic
        }
    )
}
