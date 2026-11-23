package gambi.global.os26launcher.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.ui.weather.CitySearchDialog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * WX Task 4 — màn "Vị trí" (1.5): quyền vị trí, chế độ Tự động / Chọn thủ công, "Cập nhật vị trí" (Tự động) hoặc chọn thành phố (Thủ công).
 * Kết quả quyết định mục "Vị trí hiện tại" của danh sách địa điểm (Cài đặt → Thời tiết). Chỉ dùng vị trí gần đúng (`ACCESS_COARSE_LOCATION`),
 * lấy một lần mỗi lần bấm, không chạy nền; chế độ Thủ công không cần quyền.
 *
 * `registerForActivityResult` gọi lúc dựng (công thức G2d, màn xác định bởi extra nên thứ tự cố định). Hộp thoại và hộp tìm thành phố đóng ở `onDestroy`.
 */
class LocationSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[LocationSettingsViewModel::class.java]
    private var places = WeatherPlaces()
    private var locating = false
    private val citySearch = CitySearchDialog(activity) { viewModel.setManualPlace(it) }

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> if (places.mode == LocationMode.AUTO) viewModel.updateAutoPlace(fallbackName())

                // Không hiện hộp quyền nữa (đã từ chối "không hỏi lại"): chỉ còn cài đặt ứng dụng.
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                    Toast.makeText(activity, R.string.wx_loc_denied_settings, Toast.LENGTH_LONG).show()
                    openAppSettings()
                }

                else -> Toast.makeText(activity, R.string.wx_loc_denied, Toast.LENGTH_LONG).show()
            }
            ctx.rerender(false)
        }

    override val titleRes: Int = R.string.settings_hub_location

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>()
        rows.add(SettingsRow.Header(ID_PERMISSION_HEADER, R.string.wx_loc_perm_header))
        rows.add(
            SettingsRow.Value(
                SettingsRow.ID_LOCATION_PERMISSION,
                R.string.wx_loc_perm,
                activity.getString(if (viewModel.hasPermission()) R.string.wx_loc_perm_granted else R.string.wx_loc_perm_denied)
            )
        )
        rows.add(SettingsRow.Header(ID_MODE_HEADER, R.string.wx_loc_mode_header))
        // RS Task 3: Figma 1.5 vẽ hai hàng radio ngay trên màn (thay hộp chọn).
        rows.add(
            SettingsRow.Radio(ID_MODE_AUTO, R.string.wx_loc_mode_auto, places.mode == LocationMode.AUTO, R.string.wx_loc_mode_auto_desc)
        )
        rows.add(
            SettingsRow.Radio(
                ID_MODE_MANUAL, R.string.wx_loc_mode_manual, places.mode == LocationMode.MANUAL, R.string.wx_loc_mode_manual_desc
            )
        )
        if (places.mode == LocationMode.AUTO) {
            rows.add(
                SettingsRow.Value(
                    SettingsRow.ID_LOCATION_UPDATE,
                    R.string.wx_loc_update,
                    if (locating) activity.getString(R.string.wx_loc_updating)
                    else places.autoPlace?.displayName ?: activity.getString(R.string.wx_places_current_unset)
                )
            )
        } else {
            rows.add(
                SettingsRow.Value(
                    SettingsRow.ID_LOCATION_CITY,
                    R.string.wx_loc_city,
                    places.manualPlace?.displayName ?: activity.getString(R.string.wx_loc_city_unset)
                )
            )
        }
        rows.add(SettingsRow.Footer(ID_PRIVACY, activity.getString(R.string.wx_loc_privacy)))
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) {
        when (row.id) {
            SettingsRow.ID_LOCATION_PERMISSION ->
                if (viewModel.hasPermission()) openAppSettings() else requestPermission() // đã cấp: bấm để mở cài đặt ứng dụng (thu hồi)

            SettingsRow.ID_LOCATION_UPDATE ->
                if (viewModel.hasPermission()) viewModel.updateAutoPlace(fallbackName()) else requestPermission()

            SettingsRow.ID_LOCATION_CITY -> citySearch.show(R.string.wx_loc_pick_city)
        }
    }

    override fun onRadioClicked(row: SettingsRow.Radio) {
        val chosen = when (row.id) {
            ID_MODE_AUTO -> LocationMode.AUTO
            ID_MODE_MANUAL -> LocationMode.MANUAL
            else -> return
        }
        if (chosen != places.mode) viewModel.setMode(chosen, fallbackName())
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.places.collect {
                    places = it
                    ctx.rerender(false)
                }
            }
            launch {
                viewModel.locating.collect {
                    locating = it
                    ctx.rerender(false)
                }
            }
            launch {
                viewModel.messages.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
        }
    }

    override fun onDestroy() {
        citySearch.dismiss()
    }

    private fun requestPermission() {
        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun fallbackName(): String = activity.getString(R.string.wx_places_current)

    private fun openAppSettings() {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
            )
        } catch (e: ActivityNotFoundException) {
            // ROM không có màn này: bỏ qua
        }
    }

    private companion object {
        const val ID_MODE_AUTO = "location_mode_auto" // RS Task 3
        const val ID_MODE_MANUAL = "location_mode_manual"
        const val ID_PERMISSION_HEADER = "location_permission_header"
        const val ID_MODE_HEADER = "location_mode_header"
        const val ID_PRIVACY = "location_privacy"
    }
}
