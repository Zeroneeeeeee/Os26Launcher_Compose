package gambi.global.os26launcher.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationKind
import gambi.global.os26launcher.domain.model.weather.WeatherNotificationSettings
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.weather.AddPlaceResult
import gambi.global.os26launcher.domain.model.weather.GeoPlace
import gambi.global.os26launcher.domain.model.weather.LocationMode
import gambi.global.os26launcher.domain.model.weather.TemperatureUnit
import gambi.global.os26launcher.domain.model.weather.WeatherPlaces
import gambi.global.os26launcher.ui.weather.CitySearchDialog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import gambi.global.os26launcher.domain.model.weather.WeatherRefreshInterval

/**
 * WU Task 2 + WX Task 3 — màn "Thời tiết" (1.4): nhóm "Địa điểm theo dõi" (mục "Vị trí hiện tại" luôn có, tối đa 2 thành phố thêm, "+ Thêm địa điểm";
 * tổng tối đa [WeatherPlaces.MAX_PLACES]) rồi nhóm "Đơn vị" (hàng Đơn vị nhiệt độ của WU). Mỗi tiện ích thời tiết chọn địa điểm bằng cách bấm vào nó
 * ở Home (WX Task 5). Màn không gọi mạng ngoài việc tìm thành phố khi người dùng bấm Tìm.
 */
class WeatherSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val weatherSettings = ViewModelProvider(activity)[WeatherSettingsViewModel::class.java]
    private var unit = TemperatureUnit.DEFAULT
    private var refreshInterval = WeatherRefreshInterval.DEFAULT // WR
    private var intervalDialog: DsDialogHandle? = null
    private var places = WeatherPlaces()
    private var removeDialog: DsDialogHandle? = null
    private val citySearch = CitySearchDialog(activity) { weatherSettings.addPlace(it) }

    // WN: hai công tắc thông báo + luồng xin quyền POST_NOTIFICATIONS (API 33+). Launcher đăng ký lúc dựng, theo thứ tự cố định (công thức G2d).
    private var notifications = WeatherNotificationSettings()
    private var pendingKind: WeatherNotificationKind? = null

    private val notificationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val kind = pendingKind
            pendingKind = null
            when {
                granted -> if (kind != null) weatherSettings.setNotification(kind, true)

                // Không hiện hộp xin nữa (từ chối "không hỏi lại", hoặc đã từ chối trước đó): chỉ còn cài đặt ứng dụng.
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(activity, R.string.wn_toast_blocked, Toast.LENGTH_LONG).show()
                    openNotificationSettings()
                }

                else -> Toast.makeText(activity, R.string.wn_toast_denied, Toast.LENGTH_LONG).show()
            }
            ctx.rerender(true) // công tắc chỉ đổi theo dữ liệu thật
        }

    override val titleRes: Int = R.string.settings_hub_weather

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>()
        // RS Task 7: thứ tự theo Figma 1.4: card xem trước (dữ liệu mẫu), Đơn vị nhiệt độ (card riêng, không tiêu đề), Địa điểm, Thông báo.
        rows.add(WeatherPreviewMock.row(activity, ID_PREVIEW, unit, places.current?.displayName)) // tên = vị trí hiện tại (như hàng Vị trí hiện tại)
        // RS Task 3: Figma 1.4 vẽ segmented °C | °F ngay trên hàng (thay hộp chọn). Thứ tự = TemperatureUnit.entries.
        rows.add(
            SettingsRow.Segmented(
                SettingsRow.ID_WEATHER_UNIT,
                R.string.settings_weather_unit,
                TemperatureUnit.entries.map {
                    activity.getString(
                        if (it == TemperatureUnit.FAHRENHEIT) R.string.settings_weather_unit_value_f
                        else R.string.settings_weather_unit_value_c
                    )
                },
                TemperatureUnit.entries.indexOf(unit)
            )
        )
        rows.add(SettingsRow.Header(ID_PLACES_HEADER, R.string.wx_places_header))
        rows.add(currentRow())
        places.saved.forEach { rows.add(placeRow(it)) }
        rows.add(addRow())
        rows.add(SettingsRow.Footer(ID_PLACES_HINT, activity.getString(R.string.wx_places_hint)))
        // WR (chủ dự án 2026-11-27): nhóm "Làm mới": chu kỳ tự làm mới (thời tiết + vị trí hiện tại).
        rows.add(SettingsRow.Header(ID_REFRESH_HEADER, R.string.wx_refresh_header))
        rows.add(SettingsRow.Value(ID_REFRESH_INTERVAL, R.string.wx_refresh_interval, intervalLabel(refreshInterval)))
        rows.add(SettingsRow.Footer(ID_REFRESH_HINT, activity.getString(R.string.wx_refresh_hint)))
        addNotificationRows(rows)
        return rows
    }

    /** WN: nhóm "Thông báo". Không có khóa thì không có công tắc, chỉ có chú thích. */
    private fun addNotificationRows(rows: MutableList<SettingsRow>) {
        rows.add(SettingsRow.Header(ID_NOTIFY_HEADER, R.string.wn_settings_header))
        if (!weatherSettings.hasKey) {
            rows.add(SettingsRow.Footer(ID_NOTIFY_HINT, activity.getString(R.string.wn_footer_no_key)))
            return
        }
        rows.add(
            SettingsRow.Switch(
                SettingsRow.ID_WEATHER_NOTIFY_DAILY, R.string.wn_switch_daily, notifications.daily, R.string.wn_switch_daily_desc
            )
        )
        rows.add(
            SettingsRow.Switch(
                SettingsRow.ID_WEATHER_NOTIFY_TOMORROW, R.string.wn_switch_tomorrow, notifications.tomorrow, R.string.wn_switch_tomorrow_desc
            )
        )
        val lines = ArrayList<String>()
        lines.add(activity.getString(R.string.wn_footer_hint))
        if (places.current == null) lines.add(activity.getString(R.string.wn_footer_no_place))
        if (notifications.any && !notificationsAllowed()) lines.add(activity.getString(R.string.wn_footer_blocked))
        rows.add(SettingsRow.Footer(ID_NOTIFY_HINT, lines.joinToString("\n\n")))
    }

    /** Thông báo của app đang được hệ thống cho phép (API 33+: gồm quyền POST_NOTIFICATIONS; thấp hơn: người dùng chưa tắt). */
    private fun notificationsAllowed(): Boolean =
        activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun openNotificationSettings() {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            )
        } catch (e: ActivityNotFoundException) {
            // ROM không có màn này: thử cài đặt ứng dụng
            try {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            } catch (e2: ActivityNotFoundException) {
                // bỏ qua
            }
        }
    }

    private fun currentRow(): SettingsRow.Value {
        val name = places.current?.displayName
        val value = when {
            name == null -> activity.getString(R.string.wx_places_current_unset)
            places.mode == LocationMode.MANUAL -> activity.getString(R.string.wx_places_current_manual, name)
            else -> name
        }
        return SettingsRow.Value(SettingsRow.ID_WEATHER_CURRENT, R.string.wx_places_current, value)
    }

    private fun placeRow(place: GeoPlace): SettingsRow.Value = SettingsRow.Value(
        SettingsRow.ID_WEATHER_PLACE_PREFIX + WeatherPlaces.placeIdOf(place),
        0,
        listOfNotNull(place.admin, place.countryCode).joinToString(", "),
        titleText = place.displayName
    )

    private fun addRow(): SettingsRow.Value = SettingsRow.Value(
        SettingsRow.ID_WEATHER_ADD,
        R.string.wx_places_add,
        if (places.isFull) activity.getString(R.string.wx_places_full, WeatherPlaces.MAX_PLACES) else "",
        enabled = !places.isFull
    )

    /** WR: "3 giờ", "1 ngày"… */
    private fun intervalLabel(interval: WeatherRefreshInterval): String {
        val hours = (interval.millis / HOUR_MS).toInt()
        return if (hours % 24 == 0) activity.getString(R.string.wx_refresh_day)
        else activity.resources.getQuantityString(R.plurals.wx_refresh_hours, hours, hours)
    }

    private fun showIntervalDialog() {
        if (intervalDialog != null) return
        val options = WeatherRefreshInterval.entries
        intervalDialog = SettingsDialogs.singleChoice(
            activity, activity.getString(R.string.wx_refresh_interval), options.map { intervalLabel(it) }, options.indexOf(refreshInterval)
        ) { which ->
            val chosen = options[which]
            if (chosen != refreshInterval) weatherSettings.onRefreshIntervalChosen(chosen)
        }.also { d -> d.setOnDismissListener { intervalDialog = null } }
    }

    override fun onValueClicked(row: SettingsRow.Value) {
        when {
            row.id == ID_REFRESH_INTERVAL -> showIntervalDialog()
            row.id == SettingsRow.ID_WEATHER_ADD -> if (!places.isFull) citySearch.show(R.string.wx_search_add_title)
            row.id == SettingsRow.ID_WEATHER_CURRENT ->
                if (SettingsScreens.isRegistered(SettingsScreenId.LOCATION)) ctx.open(SettingsScreenId.LOCATION) // màn 1.5 làm ở WX Task 4
            row.id.startsWith(SettingsRow.ID_WEATHER_PLACE_PREFIX) -> {
                val placeId = row.id.removePrefix(SettingsRow.ID_WEATHER_PLACE_PREFIX)
                places.resolve(placeId)?.let { showRemoveDialog(placeId, it) }
            }
        }
    }

    override fun onSegmentChanged(row: SettingsRow.Segmented, index: Int) {
        if (row.id != SettingsRow.ID_WEATHER_UNIT) return
        val chosen = TemperatureUnit.entries.getOrNull(index) ?: return
        if (chosen != unit) weatherSettings.onUnitChosen(chosen)
    }

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        val kind = when (row.id) {
            SettingsRow.ID_WEATHER_NOTIFY_DAILY -> WeatherNotificationKind.DAILY
            SettingsRow.ID_WEATHER_NOTIFY_TOMORROW -> WeatherNotificationKind.TOMORROW
            else -> return
        }
        if (!checked) {
            weatherSettings.setNotification(kind, false)
            return
        }
        // Bật: cần quyền thông báo (API 33+) rồi thông báo của app không bị tắt ở hệ thống. Chưa đủ thì công tắc về tắt (dựng lại từ dữ liệu thật).
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> {
                pendingKind = kind
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                ctx.rerender(true)
            }

            !notificationsAllowed() -> {
                Toast.makeText(activity, R.string.wn_toast_blocked, Toast.LENGTH_LONG).show()
                openNotificationSettings()
                ctx.rerender(true)
            }

            else -> weatherSettings.setNotification(kind, true)
        }
    }

    override suspend fun observe() {
        coroutineScope {
            launch {
                weatherSettings.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
            launch {
                weatherSettings.unit.collect {
                    unit = it
                    ctx.rerender(false)
                }
            }
            launch {
                weatherSettings.refreshInterval.collect {
                    refreshInterval = it
                    ctx.rerender(false)
                }
            }
            launch {
                weatherSettings.places.collect {
                    places = it
                    ctx.rerender(false)
                }
            }
            launch {
                weatherSettings.notifications.collect {
                    notifications = it
                    ctx.rerender(false)
                }
            }
            launch {
                weatherSettings.addRejected.collect { result ->
                    val message = when (result) {
                        AddPlaceResult.FULL -> activity.getString(R.string.wx_places_full, WeatherPlaces.MAX_PLACES)
                        else -> activity.getString(R.string.wx_places_duplicate)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        intervalDialog?.setOnDismissListener(null)
        intervalDialog?.dismiss()
        intervalDialog = null
        removeDialog?.setOnDismissListener(null)
        removeDialog?.dismiss()
        removeDialog = null
        citySearch.dismiss()
    }

    private fun showRemoveDialog(placeId: String, place: GeoPlace) {
        if (removeDialog != null) return // một hộp mỗi lúc (chống chạm đúp)
        removeDialog = SettingsDialogs.confirm(
            activity,
            activity.getString(R.string.wx_places_remove_title),
            activity.getString(R.string.wx_places_remove_message, place.displayName),
            activity.getString(R.string.wx_places_remove_confirm),
            onPositive = { weatherSettings.removePlace(placeId) }
        ).also { it.setOnDismissListener { removeDialog = null } }
    }

    private companion object {
        const val ID_PREVIEW = "weather_preview" // RS Task 7
        const val ID_PLACES_HEADER = "weather_places_header"
        const val ID_PLACES_HINT = "weather_places_hint"
        const val ID_NOTIFY_HEADER = "weather_notify_header"
        const val ID_NOTIFY_HINT = "weather_notify_hint"
        const val ID_REFRESH_HEADER = "weather_refresh_header" // WR
        const val ID_REFRESH_INTERVAL = "weather_refresh_interval"
        const val ID_REFRESH_HINT = "weather_refresh_hint"
        const val HOUR_MS = 60 * 60_000L
    }
}
