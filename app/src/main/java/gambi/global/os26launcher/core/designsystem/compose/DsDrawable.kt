package gambi.global.os26launcher.core.designsystem.compose

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

/**
 * JS1 — vẽ một drawable XML bất kỳ (shape, layer-list, gradient...) làm nền. `painterResource` chỉ đọc vector và ảnh, nên nền dạng
 * `<shape>` của các màn cũ (vd `bg_weather_preview`, `tr_preview_folder`) đi qua đây để giữ nguyên một nguồn tài nguyên.
 */
fun Modifier.drawableBackground(@DrawableRes id: Int): Modifier = composed {
    val context = LocalContext.current
    val drawable = remember(context, id) { AppCompatResources.getDrawable(context, id)?.mutate() }
    if (drawable == null) this else drawBehind { drawDrawable(drawable) }
}

/** Vẽ [drawable] phủ cả vùng vẽ hiện tại. */
fun DrawScope.drawDrawable(drawable: Drawable) {
    drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
    drawIntoCanvas { drawable.draw(it.nativeCanvas) }
}

/** Một khối chỉ có nền là drawable [id] (chấm, ô mẫu tĩnh). */
@Composable
fun DrawableBox(@DrawableRes id: Int, modifier: Modifier = Modifier) {
    Box(modifier.drawableBackground(id))
}

/**
 * JS (kiểm máy 2026-11-20): nền ảnh nền mờ dựng từ bản thu nhỏ bị lộ khối khi phóng lên. API 31+ nhoè thêm lúc vẽ (RenderEffect, cạnh
 * kẹp như hình chữ nhật) cho mịn; dưới 31 chỉ dựa vào bản thu nhỏ + lọc song tuyến.
 */
fun Modifier.wallpaperSoftBlur(): Modifier =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        this.then(Modifier.blur(24.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle))
    } else {
        this
    }
