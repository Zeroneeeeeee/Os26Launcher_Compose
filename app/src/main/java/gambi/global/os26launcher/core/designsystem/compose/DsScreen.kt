package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop

/**
 * JD Task 2 — khung chung của một màn DS3 bằng Compose: nền aurora được ghi làm lớp backdrop, inset thanh hệ thống và cắt màn, thanh
 * nav (khi có [title]), rồi [content]. Cung cấp [LocalDsBackdrop] nên mọi `dsGlass` bên trong tự có kính mà không cần truyền nền.
 *
 * Giữ ĐÚNG thứ tự `layerBackdrop` rồi mới `dsAurora`: `layerBackdrop` chỉ ghi phần vẽ nằm sau nó trong chuỗi (bài học JC, đo được).
 *
 * [onBack] `null` = ẩn nút quay lại; [title] và [onBack] cùng `null` = không có thanh nav. Thanh nav là [DsNavBar] (JD Task 4).
 */
@Composable
fun DsScreen(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .dsAurora()
        )
        CompositionLocalProvider(LocalDsBackdrop provides backdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            ) {
                if (title != null || onBack != null) DsNavBar(title = title, onBack = onBack, modifier = Modifier.fillMaxWidth())
                content()
            }
        }
    }
}
