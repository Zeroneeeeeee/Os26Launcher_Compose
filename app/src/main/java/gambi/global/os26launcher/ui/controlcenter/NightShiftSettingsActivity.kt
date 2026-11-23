package gambi.global.os26launcher.ui.controlcenter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsRow
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.NightShiftConfig
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * G3d: Night Shift (RE mục 5.4, `NightShiftActivity`): bật tay, độ ấm, hẹn giờ từ/đến (mặc định 22:00 → 7:00). Lớp phủ do
 * `ControlCenterService` vẽ, nên cần quyền "Hiển thị trên ứng dụng khác" và "Truy cập thông báo" như dải mép.
 *
 * JS7: Compose (`DsScreen`, nhóm kính, công tắc / thanh trượt DS, chọn giờ bằng hộp thoại DS) thay `CcForm` + Material + `TimePickerDialog`.
 */
@AndroidEntryPoint
class NightShiftSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: ControlCenterPrefsRepository

    private var picker: DsDialogHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val config by prefs.config.collectAsStateWithLifecycle(initialValue = ControlCenterConfig())
            val n = config.nightShift
            DsScreen(title = stringResource(R.string.cc_tile_night_shift), onBack = ::finish) {
                CcFormColumn(Modifier.weight(1f)) {
                    CcGroup {
                        DsRow(
                            title = stringResource(R.string.cc_night_shift_manual),
                            onClick = { save { it.copy(enabled = !n.enabled) } },
                            trailing = DsRowTrailing.Toggle(n.enabled)
                        )
                        CcSlider(stringResource(R.string.cc_night_shift_warmth), n.warmth, 0f, 1f) { v -> save { it.copy(warmth = v) } }
                    }
                    CcHeader(stringResource(R.string.cc_night_shift_schedule))
                    CcGroup {
                        DsRow(
                            title = stringResource(R.string.cc_night_shift_scheduled),
                            onClick = { save { it.copy(scheduled = !n.scheduled) } },
                            trailing = DsRowTrailing.Toggle(n.scheduled)
                        )
                        val timeRow = if (n.scheduled) Modifier else Modifier.alpha(DISABLED_ALPHA)
                        DsRow(
                            title = stringResource(R.string.cc_night_shift_from),
                            onClick = if (n.scheduled) ({ pickTime(R.string.cc_night_shift_from, n.fromMinutes) { m -> save { it.copy(fromMinutes = m) } } }) else null,
                            modifier = timeRow,
                            trailing = DsRowTrailing.Value(formatMinutes(n.fromMinutes))
                        )
                        DsRow(
                            title = stringResource(R.string.cc_night_shift_to),
                            onClick = if (n.scheduled) ({ pickTime(R.string.cc_night_shift_to, n.toMinutes) { m -> save { it.copy(toMinutes = m) } } }) else null,
                            modifier = timeRow,
                            trailing = DsRowTrailing.Value(formatMinutes(n.toMinutes)),
                            showDivider = false
                        )
                    }
                    CcNote(stringResource(R.string.cc_night_shift_note))
                }
            }
            dsDialogs.Render()
        }
        reassertEdgeToEdge(window)
    }

    override fun onResume() {
        super.onResume()
        ControlCenterService.refresh(this)
    }

    override fun onDestroy() {
        picker?.dismiss()
        picker = null
        super.onDestroy()
    }

    private fun pickTime(title: Int, minutes: Int, onPick: (Int) -> Unit) {
        picker?.dismiss()
        picker = dsDialogs.show { handle ->
            var value by remember { mutableIntStateOf(minutes) }
            DsAlertDialog(
                onDismiss = handle::dismiss,
                title = getString(title),
                positive = getString(R.string.cc_time_done) to { onPick(value) },
                negative = getString(R.string.settings_cancel) to {}
            ) {
                CcTimeStepper(value) { value = it }
            }
        }.also { d -> d.setOnDismissListener { picker = null } }
    }

    private fun save(transform: (NightShiftConfig) -> NightShiftConfig) {
        lifecycleScope.launch {
            prefs.update { it.copy(nightShift = transform(it.nightShift)) }
            ControlCenterService.refresh(this@NightShiftSettingsActivity)
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}
