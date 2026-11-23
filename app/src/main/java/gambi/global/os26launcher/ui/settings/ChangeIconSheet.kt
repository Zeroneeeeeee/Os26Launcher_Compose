package gambi.global.os26launcher.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.domain.model.AppItem

/**
 * P3-4 (CI) — bottom sheet đổi icon một app theo Figma 5.2 popup `302:2697` (bản Compose của `ChangeIconSheet` View ở REF):
 * - tiêu đề = tên app, nút ✕;
 * - hàng "icon cũ → icon mới · Chọn biểu tượng mới";
 * - lưới 10 icon mẫu (hình của app trên 10 nền màu, Q5), 5 cột x 2 hàng; ô đang chọn có viền;
 * - "Từ thư viện ảnh" | "Khôi phục" (chỉ bật khi app đang có icon riêng), rồi "Lưu" (chỉ bật khi đã chọn icon mới).
 *
 * Đóng sheet (✕, vuốt, Back) = không ghi gì. Ảnh từ thư viện do màn mở (cần ActivityResult), đưa vào bằng [setPickedImage].
 */
class ChangeIconSheet(
    private val activity: ComponentActivity,
    private val viewModel: ChangeIconViewModel,
    private val app: AppItem,
    private val customized: Boolean,
    private val onPickImage: () -> Unit,
    /** Mỗi lần sheet đóng (lưu, khôi phục hay hủy). */
    private val onDismissed: () -> Unit
) {
    /** Ảnh vuông từ thư viện (màn giải mã sẵn); null = chưa chọn ảnh. */
    private var picked by mutableStateOf<Bitmap?>(null)

    /** Chỉ số ô mẫu đang chọn; -1 = không ô nào (ảnh từ thư viện hoặc chưa chọn). */
    private var selected by mutableIntStateOf(-1)

    var dialog: DsDialogHandle? = null
        private set

    fun show(): ChangeIconSheet {
        dialog = activity.dsDialogs.show { handle -> Content(handle) }.also { d -> d.setOnDismissListener { onDismissed() } }
        return this
    }

    /** Ảnh vuông đã cắt từ thư viện. Thành icon mới đang chọn, bỏ chọn ô mẫu. */
    fun setPickedImage(square: Bitmap) {
        picked = square
        selected = -1
    }

    @Composable
    private fun Content(handle: DsDialogHandle) {
        val density = LocalDensity.current
        val iconPx = with(density) { SHEET_ICON.roundToPx() }
        val cellPx = with(density) { VARIANT_ICON.roundToPx() }

        val oldIcon by produceState<Bitmap?>(null) { value = viewModel.systemIcon(app.key, iconPx) }
        val currentIcon by produceState<Bitmap?>(null) { value = viewModel.loadIcon(app.key, iconPx) }
        // null = đang tạo; rỗng = không tạo được mẫu nào (app không còn, icon lỗi): ẩn cả lưới.
        val squares by produceState<List<Bitmap>?>(null) { value = viewModel.variants(app.key, ChangeIconViewModel.SAVE_SIZE_PX) }
        val cells by produceState<List<Bitmap>>(emptyList(), squares) {
            value = squares.orEmpty().map { viewModel.shape(it, cellPx) }
        }
        val pickedNow = picked
        val pickedShaped by produceState<Bitmap?>(null, pickedNow) { value = pickedNow?.let { viewModel.shape(it, iconPx) } }

        val chosen: Bitmap? = squares?.getOrNull(selected) ?: pickedNow.takeIf { selected < 0 }
        val newIcon = when {
            selected >= 0 -> cells.getOrNull(selected)
            pickedNow != null -> pickedShaped
            else -> currentIcon
        }

        DsBottomSheet(onDismiss = handle::dismiss, title = app.label) {
            // Hàng "cũ -> mới".
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconCell(oldIcon, SHEET_ICON, ring = false)
                Spacer(Modifier.width(GAP))
                Image(
                    painterResource(R.drawable.ds_ic_chevron_right),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorResource(R.color.ds_text_secondary)),
                    modifier = Modifier.size(GAP * 2)
                )
                Spacer(Modifier.width(GAP))
                IconCell(newIcon, SHEET_ICON, ring = true)
                BasicText(
                    stringResource(R.string.ci_choose_new),
                    style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_primary)),
                    modifier = Modifier.weight(1f).padding(start = GAP)
                )
            }

            // Lưới icon mẫu: 5 cột x 2 hàng, cột giãn đều theo chiều ngang.
            val count = squares?.size ?: ChangeIconViewModel.VARIANT_COLORS.size
            if (count > 0) {
                Column(Modifier.fillMaxWidth().padding(top = GAP * 2)) {
                    for (r in 0 until (count + COLUMNS - 1) / COLUMNS) {
                        Row(Modifier.fillMaxWidth().padding(top = if (r > 0) GAP else 0.dp)) {
                            for (c in 0 until COLUMNS) {
                                val i = r * COLUMNS + c
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (i < count) {
                                        val description = stringResource(R.string.ci_variant_description, i + 1)
                                        IconCell(
                                            cells.getOrNull(i),
                                            VARIANT_ICON,
                                            ring = selected == i,
                                            description = description,
                                            onClick = { if (squares?.getOrNull(i) != null) selected = i }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Từ thư viện ảnh" | "Khôi phục".
            Row(Modifier.fillMaxWidth().padding(top = GAP * 2)) {
                DsButton(
                    text = stringResource(R.string.ci_from_photos),
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f),
                    variant = DsButtonVariant.Secondary
                )
                Spacer(Modifier.width(GAP))
                DsButton(
                    text = stringResource(R.string.ci_restore),
                    onClick = {
                        viewModel.restore(app.key)
                        handle.dismiss()
                    },
                    modifier = Modifier.weight(1f),
                    variant = DsButtonVariant.Secondary,
                    enabled = customized
                )
            }
            Spacer(Modifier.height(GAP))
            DsButton(
                text = stringResource(R.string.ci_save),
                onClick = {
                    chosen?.let { viewModel.save(app.key, it) }
                    handle.dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = chosen != null
            )
        }
    }

    /** Ô icon bo góc; [ring] = viền xanh của ô đang chọn (Figma: viền #7AA2FF quanh icon mới). */
    @Composable
    private fun IconCell(
        bitmap: Bitmap?,
        size: Dp,
        ring: Boolean,
        description: String? = null,
        onClick: (() -> Unit)? = null
    ) {
        val shape = RoundedCornerShape(size * CORNER_RATIO)
        var m = Modifier.size(size).clip(shape)
        if (ring) m = m.border(2.dp, RING_COLOR, shape)
        if (onClick != null) m = m.clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
        Box(m.padding(size * RING_INSET)) {
            bitmap?.takeIf { !it.isRecycled }?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(size * CORNER_RATIO))
                )
            }
        }
    }

    private companion object {
        const val COLUMNS = 5
        const val CORNER_RATIO = 0.26f

        /** Icon thụt vào trong ô một chút để viền chọn không đè lên hình. */
        const val RING_INSET = 0.06f

        /** REF `ci_sheet_icon` / `ci_variant_icon`. */
        val SHEET_ICON = 56.dp
        val VARIANT_ICON = 52.dp
        val GAP = 12.dp // ds_space_12

        /** REF `ci_selected_ring` (Figma 302:2697). */
        val RING_COLOR = Color(0xFF7AA2FF)
    }
}
