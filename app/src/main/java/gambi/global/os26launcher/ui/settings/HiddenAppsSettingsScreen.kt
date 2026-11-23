package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.search.AppSearchMatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * HL-1 — màn "Ứng dụng ẩn" (5.1, Figma `302:2648`), thay `AppLibraryActivity`:
 * - dòng mô tả;
 * - (HL-2) card công tắc "Khóa bằng vân tay / PIN" — CHỈ hiện khi máy có khóa màn hình;
 * - ô tìm ứng dụng;
 * - "Tất cả ứng dụng" + nhãn "N đang ẩn";
 * - mọi app kèm công tắc (bật = ẩn).
 *
 * Bỏ hàng "Cách mở danh sách — Vuốt 2 ngón" (chủ dự án 2026-11-20, Q6).
 *
 * HL-2 — cổng: khóa đang bật VÀ máy có khóa màn hình thì phải xác thực ([HiddenAppsAuthenticator]) trước khi vẽ bất cứ thứ gì ngoài dòng
 * mô tả; hủy thì đóng màn. Rời màn (onStop) là khóa lại, lần vào sau hỏi lại (Q4), trừ lúc chính hộp/màn xác thực đang mở. Máy không có
 * khóa màn hình: vào thẳng và ẩn công tắc (Q5); `hiddenAppsLock` vẫn giữ nên đặt lại khóa máy là khóa app tự có hiệu lực. Cửa sổ đặt
 * `FLAG_SECURE`: không chụp màn hình được, ảnh trong màn đa nhiệm bị che.
 */
class HiddenAppsSettingsScreen(private val ctx: SettingsScreenContext) : SettingsScreen {

    private val activity = ctx.activity
    private val viewModel = ViewModelProvider(activity)[HiddenAppsViewModel::class.java]
    private val auth = HiddenAppsAuthenticator(activity) // tạo lúc dựng màn: đăng ký ActivityResult trước STARTED
    private var entries: List<HiddenAppsViewModel.Entry> = emptyList()
    private var query = ""

    /** Đã xác thực trong lần vào màn này. */
    private var unlocked = false

    init {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // HL-2
    }

    override val titleRes: Int = R.string.settings_hidden_apps

    /** Đang phải che danh sách: chưa biết cấu hình khóa, hoặc khóa có hiệu lực mà chưa xác thực. */
    private fun gated(): Boolean {
        val lock = viewModel.lockEnabled.value ?: return true
        return lock && auth.isDeviceSecure() && !unlocked
    }

    override fun rows(state: SettingsUiState): List<SettingsRow> {
        val rows = ArrayList<SettingsRow>(entries.size + 5)
        rows.add(SettingsRow.Intro(ID_INTRO, activity.getString(R.string.hl_intro)))
        if (gated()) return rows // HL-2: chưa mở khóa -> không lộ gì ngoài dòng mô tả

        if (auth.isDeviceSecure()) { // Q5: máy không có khóa màn hình thì ẩn công tắc
            rows.add(
                SettingsRow.Switch(
                    ID_LOCK,
                    R.string.hl_lock,
                    viewModel.lockEnabled.value == true,
                    R.string.hl_lock_description,
                    SettingsTile(R.drawable.ds_ic_privacy, gambi.global.os26launcher.core.designsystem.DsTileColor.VIOLET)
                )
            )
        }
        val q = AppSearchMatcher.normalize(query.trim())
        val shown = if (q.isEmpty()) entries else entries.filter {
            AppSearchMatcher.normalize(it.app.label).contains(q) || AppSearchMatcher.normalize(it.app.originalLabel).contains(q)
        }
        val hiddenCount = entries.count { it.hidden }
        rows.add(SettingsRow.Search(ID_SEARCH, R.string.ci_search_hint))
        rows.add(
            SettingsRow.HeaderBadge(
                ID_HEADER,
                R.string.hl_all_apps,
                if (hiddenCount > 0) activity.resources.getQuantityString(R.plurals.hl_hidden_count, hiddenCount, hiddenCount) else null
            )
        )
        if (shown.isEmpty() && entries.isNotEmpty()) {
            rows.add(SettingsRow.Footer(ID_EMPTY, activity.getString(R.string.ci_no_results)))
        }
        shown.mapTo(rows) { SettingsRow.AppSwitch(rowId(it.app.key), it.app, it.hidden) }
        return rows
    }

    override fun onResume() = requestUnlockIfNeeded()

    override fun onStop() {
        // Q4: rời màn là khóa lại. Màn xác thực của hệ thống (API 26–29) cũng làm màn này onStop -> bỏ qua lúc đang xác thực.
        if (!auth.inProgress && unlocked) {
            unlocked = false
            ctx.rerender(true)
        }
    }

    private fun requestUnlockIfNeeded() {
        if (!gated() || viewModel.lockEnabled.value == null || auth.inProgress) return
        auth.authenticate(
            onSuccess = {
                unlocked = true
                ctx.rerender(true)
            },
            onCancel = { activity.finish() } // hủy = không xem, đóng màn
        )
    }

    override fun onSearchChanged(row: SettingsRow.Search, query: String) {
        if (query == this.query) return
        this.query = query
        ctx.rerender()
    }

    override fun onAppSwitchChanged(row: SettingsRow.AppSwitch, checked: Boolean) = viewModel.setHidden(row.app, checked)

    override fun onValueClicked(row: SettingsRow.Value) = Unit

    /** HL-2: bật hay tắt khóa đều phải xác thực trước (người cầm máy không tắt khóa hộ được). Hủy thì công tắc về như cũ. */
    override fun onSwitchChanged(row: SettingsRow.Switch, checked: Boolean) {
        if (row.id != ID_LOCK) return
        auth.authenticate(
            onSuccess = {
                unlocked = true // vừa xác thực: không hỏi lại ngay
                viewModel.setLockEnabled(checked)
            },
            onCancel = { ctx.rerender(true) }
        )
    }

    override val appIconLoader: (suspend (ComponentKey, Int) -> Bitmap?)? = viewModel::loadIcon

    override suspend fun observe() {
        coroutineScope {
            launch {
                viewModel.entries.collect { list ->
                    if (list != null) {
                        entries = list
                        ctx.rerender(false)
                    }
                }
            }
            launch {
                viewModel.lockEnabled.collect { lock ->
                    if (lock == null) return@collect
                    ctx.rerender(true)
                    requestUnlockIfNeeded() // cấu hình đọc xong sau onResume: hỏi ngay khi biết khóa đang bật
                }
            }
            launch { // lỗi ghi -> Toast và dựng lại hàng từ trạng thái thật
                viewModel.failures.collect { res ->
                    Toast.makeText(activity, res, Toast.LENGTH_LONG).show()
                    ctx.rerender(true)
                }
            }
        }
    }

    private fun rowId(key: ComponentKey) = "hl_app:${key.packageName}/${key.className}#${key.userSerial}"

    private companion object {
        const val ID_INTRO = "hl_intro"
        const val ID_LOCK = "hl_lock"
        const val ID_SEARCH = "hl_search"
        const val ID_HEADER = "hl_all_apps"
        const val ID_EMPTY = "hl_empty"
    }
}
