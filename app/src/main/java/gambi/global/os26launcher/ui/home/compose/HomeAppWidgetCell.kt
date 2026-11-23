package gambi.global.os26launcher.ui.home.compose

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.domain.model.PlacedAppWidget
import gambi.global.os26launcher.ui.widget.host.HostedWidgetViews

/** JH3c — sổ host view của widget app, do Activity cung cấp (start/stopListening theo vòng đời Activity, `clear()` ở onDestroy). */
val LocalHostedWidgets: ProvidableCompositionLocal<HostedWidgetViews?> = staticCompositionLocalOf { null }

/**
 * JH3c Task 2 — widget của app khác (bản Compose của `IconCellView.bindAppWidget`): cùng thẻ kính với widget tự vẽ (bo `min × 0.12`), nội dung là
 * `AppWidgetHostView` do [HostedWidgetViews] cấp (một view mỗi id, giữ qua các lần trang rời màn hình, plan D6), lùi vào `aw_card_inset`. Provider vắng
 * (gỡ, tắt, id vô hiệu) thì hiện chữ "không dùng được"; hàng không bị xóa (G3c D14).
 */
@Composable
internal fun HomeAppWidgetCell(widget: PlacedAppWidget) {
    val hosted = LocalHostedWidgets.current
    val density = LocalDensity.current
    val inset = dimensionResource(R.dimen.aw_card_inset)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val radius = widgetRadius(maxWidth, maxHeight)
        Box(Modifier.fillMaxSize().dsGlass(DsSurface.CARD, DsCorner.Fixed(radius.value)))
        val view = remember(hosted, widget.appWidgetId, widget.provider) { hosted?.obtain(widget) }
        if (view == null) {
            BasicText(
                stringResource(R.string.aw_unavailable),
                style = TextStyle(color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center).padding(inset)
            )
        } else {
            AndroidView(
                factory = { ctx -> FrameLayout(ctx) },
                update = { container ->
                    if (view.parent !== container) {
                        (view.parent as? ViewGroup)?.removeView(view) // một view mỗi id: gỡ khỏi ô cũ (trang dựng lại)
                        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .onSizeChanged { hosted?.onSized(widget.appWidgetId, view, it.width, it.height, density.density) }
            )
        }
    }
}
