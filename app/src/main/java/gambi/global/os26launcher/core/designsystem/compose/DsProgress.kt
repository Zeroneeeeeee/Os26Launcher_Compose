package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R

/**
 * JS1 — vòng chờ DS3 (thay `CircularProgressIndicator` của Material ở khung Cài đặt): cung `ds_accent` 270° quay đều 1 vòng/giây, nét 4 dp
 * đầu tròn. [description] cho TalkBack.
 */
@Composable
fun DsSpinner(description: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val transition = rememberInfiniteTransition(label = "dsSpinner")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "dsSpinnerAngle"
    )
    val color = colorResource(R.color.ds_accent)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val stroke = 4.dp.toPx()
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
