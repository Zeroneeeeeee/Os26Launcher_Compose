package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import gambi.global.os26launcher.core.designsystem.AuroraBackgroundDrawable

/**
 * JC — nền aurora của các màn DS3 (`AuroraBackgroundDrawable`), vẽ trong cây Compose. Phải nằm trong cây (không đặt làm nền cửa sổ) vì
 * kính Backdrop chỉ khúc xạ được thứ vẽ bên trong lớp `layerBackdrop` (quyết định D7 của plan JC).
 */
fun Modifier.dsAurora(): Modifier = composed {
    val context = LocalContext.current
    val drawable = remember(context) { AuroraBackgroundDrawable(context) }
    drawBehind {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
