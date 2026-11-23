package gambi.global.os26launcher.ui.home.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.colorResource
import gambi.global.os26launcher.R
import gambi.global.os26launcher.domain.config.BadgeStyle
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.domain.repository.BadgeTarget

/**
 * JH3a D2 — số thông báo của một ô: 1 app, hoặc mọi app của thư mục (tổng, mỗi gói + hồ sơ tính một lần, bỏ app đã tắt huy hiệu). Cùng
 * phép tính với `NotificationBadges.countFor` của Home View, nhưng đọc từ [this] thay vì đối tượng toàn cục.
 */
internal fun NotificationBadges.State.countFor(apps: List<ComponentKey>): Int {
    if (!enabled || apps.isEmpty() || counts.isEmpty()) return 0
    var total = 0
    val seen = HashSet<BadgeTarget>(apps.size)
    for (key in apps) {
        val target = BadgeTarget(key.packageName, key.userSerial)
        if (!seen.add(target) || target.storageKey in disabledApps) continue
        total += counts[target] ?: 0
    }
    return total
}

/**
 * JH3a Task 1 — huy hiệu thông báo ở góc trên-phải icon (bản Compose của `IconCellView.drawNotificationBadge`). Vẽ trong khung của cả ô
 * (rộng [cellWidthPx]), icon cạnh [iconSizePx] căn giữa ở đỉnh ô. Số: viên tròn cao 0,38 × icon, tâm lùi 0,35 bán kính vào trong, dài theo
 * chữ ("99+" khi quá 99); chấm: tròn 0,24 × icon.
 */
@Composable
fun HomeNotificationBadge(count: Int, style: BadgeStyle, cellWidthPx: Int, iconSizePx: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val fill = colorResource(R.color.ds_status_danger)
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val iconRight = (cellWidthPx + iconSizePx) / 2f
        if (style == BadgeStyle.DOT) {
            val r = iconSizePx * DOT_RATIO / 2f
            drawCircle(fill, r, Offset(iconRight - r * INSET_RATIO, r * INSET_RATIO))
            return@Canvas
        }
        val h = iconSizePx * BADGE_RATIO
        val r = h / 2f
        val text = if (count > MAX) "$MAX+" else count.toString()
        textPaint.textSize = h * TEXT_RATIO
        val w = maxOf(h, textPaint.measureText(text) + h * PAD_RATIO)
        val right = iconRight + r - r * INSET_RATIO
        val top = r * INSET_RATIO - r
        drawRoundRect(fill, Offset(right - w, top), Size(w, h), CornerRadius(r, r))
        val fm = textPaint.fontMetrics
        drawIntoCanvas { it.nativeCanvas.drawText(text, right - w / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, textPaint) }
    }
}

// Cùng số với IconCellView (companion private).
private const val BADGE_RATIO = 0.38f
private const val INSET_RATIO = 0.5f // 2026-11-21: 0.35 -> 0.5, như iOS badge chồm ra ngoài góc icon nửa bán kính
private const val DOT_RATIO = 0.24f
private const val TEXT_RATIO = 0.62f
private const val PAD_RATIO = 0.5f
private const val MAX = 99
