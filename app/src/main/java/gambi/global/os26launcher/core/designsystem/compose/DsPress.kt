package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * JD Task 3 — phản hồi nhấn kiểu iOS (F2), tách làm hai modifier vì đứng ở hai chỗ khác nhau trong chuỗi:
 *
 * - [dsPressable] đặt ĐẦU chuỗi: nhấn thì cả phần tử co còn [PRESS_SCALE], thả thì bật lại bằng lò xo (damping 0,7, stiffness 600).
 * - [dsPressHighlight] đặt SAU bề mặt (sau `dsGlass`, đã cắt theo hình bo): phủ trắng 8% (bề mặt tối) hoặc đen 8% ([darkPress], bề mặt
 *   sáng/màu) lúc nhấn, giống lớp nhấn của `DsSurfaceDrawable` bên View.
 *
 * Cả hai đọc cùng một [interactionSource] mà `clickable` / `toggleable` của phần tử phát ra.
 */
fun Modifier.dsPressable(interactionSource: InteractionSource, enabled: Boolean = true): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "dsPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Lớp phủ lúc nhấn; xem [dsPressable]. */
fun Modifier.dsPressHighlight(interactionSource: InteractionSource, darkPress: Boolean = false, enabled: Boolean = true): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val alpha by animateFloatAsState(
            targetValue = if (pressed && enabled) PRESS_DIM else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "dsPressDim"
        )
        val tint = if (darkPress) Color.Black else Color.White
        drawWithContent {
            drawContent()
            if (alpha > 0f) drawRect(tint.copy(alpha = alpha))
        }
    }

private const val PRESS_SCALE = 0.97f
private const val PRESS_DIM = 0.08f
private const val PRESS_DAMPING = 0.7f
private const val PRESS_STIFFNESS = 600f
