package gambi.global.os26launcher.core.designsystem.compose

import android.text.format.DateFormat
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * JS5 — giờ hiện tại "h:mm" / "H:mm" theo cài đặt 12/24 giờ của máy, tự đổi mỗi phút (thay `TextClock` View trong ảnh xem trước theme và
 * khung điện thoại của Onboarding). Chỉ trang trí: ẩn khỏi TalkBack như `TextClock` cũ.
 */
@Composable
fun DsClockText(style: TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val text by produceState(LocalTime.now().format(formatter), pattern) {
        while (true) {
            val now = LocalTime.now()
            value = now.format(formatter)
            delay((60 - now.second) * 1000L - now.nano / 1_000_000L + 50L) // tới đầu phút kế tiếp
        }
    }
    BasicText(text, style = style, modifier = modifier.clearAndSetSemantics { })
}
