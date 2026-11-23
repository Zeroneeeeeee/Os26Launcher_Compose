package gambi.global.os26launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.BuildConfig
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.R

/**
 * AB 6.1 — hộp thoại "Đánh giá ứng dụng". Hàng sao chỉ để minh họa (quyết định AB3): "Gửi đánh giá" luôn mở trang Google Play của
 * app, bất kể số sao. Hộp thoại do hành động giữ và dismiss ở [onDestroy].
 */
class RateAction(private val ctx: SettingsScreenContext) : SettingsAction {

    private var dialog: DsDialogHandle? = null

    override fun run() {
        if (dialog != null) return
        val activity = ctx.activity
        dialog = activity.dsDialogs.show { handle ->
            DsAlertDialog(
                onDismiss = handle::dismiss,
                title = activity.getString(R.string.about_rate_title),
                message = activity.getString(R.string.about_rate_message),
                positive = activity.getString(R.string.about_rate_send) to { AboutLinks.openPlay(activity, BuildConfig.APPLICATION_ID) },
                negative = activity.getString(R.string.about_rate_later) to {}
            ) { RateStars() }
        }.also { d -> d.setOnDismissListener { dialog = null } }
    }

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }
}

/** AB3: hàng 5 sao chỉ để minh họa (số sao không đổi việc nút "Gửi đánh giá" làm), như RatingBar cũ của dialog_rate.xml. */
@Composable
private fun RateStars() {
    var rating by remember { mutableIntStateOf(0) }
    val on = colorResource(R.color.ds_status_warning)
    val off = colorResource(R.color.ds_text_tertiary)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            Box(Modifier.size(44.dp).clickable(interactionSource = null, indication = null) { rating = i }, contentAlignment = Alignment.Center) {
                BasicText("\u2605", style = DsTextStyles.title.copy(color = if (i <= rating) on else off, fontSize = 34.sp))
            }
        }
    }
}
