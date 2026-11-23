package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsTextField
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.AppItem
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.usecase.RenameAppUseCase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * RN — màn "Thay đổi tên ứng dụng" (5.3). Danh sách app ([SettingsRow.AppName]); bấm một hàng mở hộp thoại một ô nhập với Lưu / Khôi phục
 * (chỉ khi đã đổi tên) / Hủy. Tên chuẩn hóa và lưu bởi [RenameAppUseCase]; Home và mọi nơi khác tự cập nhật qua `AppRepository.apps`.
 * Một hộp mỗi lúc; `onDestroy` gỡ listener rồi `dismiss()` như các màn khác.
 */
class RenameAppsSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[RenameAppsViewModel::class.java]
    private var apps: List<AppItem> = emptyList()
    private var dialog: DsDialogHandle? = null

    override val titleRes: Int = R.string.settings_hub_rename_apps

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>(apps.size + 1)
        rows.add(SettingsRow.Header(ID_HEADER, R.string.settings_rename_header))
        apps.mapTo(rows) { SettingsRow.AppName(rowId(it.key), it) }
        return rows
    }

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) = Unit

    override fun onAppNameClicked(row: SettingsRow.AppName) = showRenameDialog(row.app)

    override val appIconLoader: (suspend (ComponentKey, Int) -> Bitmap?)? = viewModel::loadIcon

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.apps.collect { list ->
                    if (list != null) {
                        apps = list
                        ctx.rerender(false)
                    }
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

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun showRenameDialog(app: AppItem) {
        if (dialog != null) return // một hộp mỗi lúc (chống chạm đúp)
        // Đặt chữ TRƯỚC bộ lọc độ dài: tên gốc dài hơn giới hạn vẫn hiện nguyên; không đổi gì thì Lưu không làm gì (xem bên dưới).
        val initial = app.label
        val d = activity.dsDialogs.show { handle ->
            var input by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
            val focus = remember { FocusRequester() }
            // Lưu tắt khi tên rỗng sau chuẩn hóa.
            val canSave = RenameAppUseCase.sanitize(input.text).isNotEmpty()
            fun save() {
                if (input.text != initial) viewModel.rename(app, input.text)
            }
            DsAlertDialog(
                onDismiss = handle::dismiss,
                title = activity.getString(R.string.settings_rename_dialog_title),
                positive = activity.getString(R.string.settings_rename_save) to { save() },
                negative = activity.getString(R.string.settings_cancel) to {},
                positiveEnabled = canSave
            ) {
                Column {
                    DsTextField(
                        value = input,
                        // Bộ lọc độ dài chỉ chặn gõ thêm: tên dài sẵn vẫn xóa bớt được.
                        onValueChange = { v -> if (v.text.length <= RenameAppUseCase.MAX_LENGTH || v.text.length < input.text.length) input = v },
                        hint = activity.getString(R.string.settings_rename_hint),
                        helper = activity.getString(R.string.settings_rename_original, app.originalLabel),
                        maxLength = RenameAppUseCase.MAX_LENGTH,
                        // Bấm "Xong" trên bàn phím = Lưu.
                        onDone = {
                            if (canSave) {
                                save()
                                handle.dismiss()
                            }
                        },
                        modifier = Modifier.focusRequester(focus)
                    )
                    // RS: hộp thoại v3 chỉ có hai nút; "Khôi phục tên gốc" là nút phụ trong nội dung, chỉ hiện khi đã đổi tên.
                    if (app.label != app.originalLabel) {
                        DsButton(
                            activity.getString(R.string.settings_rename_restore),
                            onClick = {
                                viewModel.restore(app)
                                handle.dismiss()
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            variant = DsButtonVariant.Secondary,
                            size = DsButtonSize.S
                        )
                    }
                }
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        d.setOnDismissListener { dialog = null }
        dialog = d
    }

    private fun rowId(key: ComponentKey) = "rename_app:${key.packageName}/${key.className}#${key.userSerial}"

    private companion object {
        const val ID_HEADER = "rename_header"
    }
}
