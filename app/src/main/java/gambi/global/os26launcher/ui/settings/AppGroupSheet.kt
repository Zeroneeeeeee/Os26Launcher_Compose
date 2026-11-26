package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.domain.layout.FolderPlanner
import gambi.global.os26launcher.domain.model.PlacedApp

/**
 * AG (2026-11-26) — bottom sheet xem trước thư mục sẽ gom: tiêu đề "Tên · N ứng dụng", thẻ lưới 3 × 3 mỗi trang (như bảng thư mục trên
 * Home, [FolderPlanner.PAGE_SIZE]), vuốt ngang giữa các trang (tối đa [FolderPlanner.MAX_PAGES]), chấm trang khi > 1 trang; "Hủy" và
 * "Gom nhóm" (thư mục đã có: "Thêm N ứng dụng"). Chỉ xem, không bỏ chọn từng app. Đóng sheet (Hủy, vuốt, Back) = không ghi gì.
 */
class AppGroupSheet(
    private val activity: ComponentActivity,
    private val viewModel: AppGroupsViewModel,
    private val state: AppGroupsViewModel.AppGroupState,
    /** Mỗi lần sheet đóng (gom hay hủy). */
    private val onDismissed: () -> Unit
) {
    var dialog: DsDialogHandle? = null
        private set

    fun show(): AppGroupSheet {
        dialog = activity.dsDialogs.show { handle -> Content(handle) }.also { d -> d.setOnDismissListener { onDismissed() } }
        return this
    }

    @Composable
    private fun Content(handle: DsDialogHandle) {
        val count = state.apps.size
        val pages = state.apps.chunked(FolderPlanner.PAGE_SIZE).take(FolderPlanner.MAX_PAGES)
        // Chuỗi số đếm đọc bằng resources của Activity (đúng ngôn ngữ app; khỏi phụ thuộc pluralStringResource).
        val res = activity.resources
        val title = state.title + " · " + res.getQuantityString(R.plurals.app_groups_count, count, count)
        DsBottomSheet(onDismiss = handle::dismiss, title = title) {
            val pager = rememberPagerState { pages.size }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CARD_RADIUS))
                    .background(colorResource(R.color.ds_surface_fill))
            ) {
                HorizontalPager(pager, Modifier.fillMaxWidth()) { p ->
                    PageGrid(pages[p])
                }
            }
            if (pages.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = GAP),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val active = colorResource(R.color.ds_text_primary)
                    val idle = colorResource(R.color.ds_text_tertiary)
                    for (i in pages.indices) {
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(DOT)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) active else idle)
                        )
                    }
                }
            }
            Spacer(Modifier.height(GAP * 2))
            Row(Modifier.fillMaxWidth()) {
                DsButton(
                    text = stringResource(R.string.settings_cancel),
                    onClick = handle::dismiss,
                    modifier = Modifier.weight(1f),
                    variant = DsButtonVariant.Secondary
                )
                Spacer(Modifier.width(GAP))
                DsButton(
                    text = if (state.folderId == null) {
                        stringResource(R.string.app_groups_apply)
                    } else {
                        res.getQuantityString(R.plurals.app_groups_add, count, count)
                    },
                    onClick = {
                        viewModel.apply(state)
                        handle.dismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.enabled
                )
            }
        }
    }

    /** Một trang: 3 hàng x 3 cột cố định (ô trống giữ chỗ) để thẻ không đổi cao khi vuốt sang trang cuối thiếu app. */
    @Composable
    private fun PageGrid(apps: List<PlacedApp>) {
        Column(Modifier.fillMaxWidth().padding(vertical = GAP)) {
            for (r in 0 until GRID) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until GRID) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            val app = apps.getOrNull(r * GRID + c)
                            if (app != null) AppCell(app) else Spacer(Modifier.height(CELL_HEIGHT))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppCell(app: PlacedApp) {
        val px = with(LocalDensity.current) { ICON.roundToPx() }
        val icon by produceState<Bitmap?>(null, app.app.iconKey, px) {
            value = null // produceState không gắn giá trị với khóa (bài học JH1)
            value = viewModel.loadIcon(app.app.key, px)
        }
        Column(
            Modifier.height(CELL_HEIGHT).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(ICON)) {
                icon?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
            BasicText(
                app.app.label,
                style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_primary), textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }

    private companion object {
        const val GRID = 3
        val ICON = 56.dp
        val CELL_HEIGHT = 88.dp
        val CARD_RADIUS = 24.dp
        val DOT = 7.dp
        val GAP = 12.dp
    }
}
