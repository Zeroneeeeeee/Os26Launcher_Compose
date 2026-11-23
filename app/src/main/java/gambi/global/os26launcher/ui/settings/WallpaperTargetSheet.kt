package gambi.global.os26launcher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.bitmap.ExifReader
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsDialogHandle
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.dsDialogs
import gambi.global.os26launcher.domain.model.WallpaperTarget
import gambi.global.os26launcher.ui.settings.compose.WallpaperPreviewFrame
import gambi.global.os26launcher.ui.settings.compose.WallpaperPreviewMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

/**
 * P3-2 — bottom sheet "Đặt hình nền cho" theo Figma 2.2 popup `302:2090` (bản Compose của `WallpaperTargetSheet` View ở REF):
 * - Tiêu đề + nút ✕ (của [DsBottomSheet]).
 * - Hai khung xem trước [WallpaperPreviewFrame] (Màn hình chính, Màn hình khóa), mỗi khung có nhãn bên dưới.
 * - Ba nút đích: Màn hình chính, Màn hình khóa (phụ), Cả hai (chính).
 *
 * Ảnh nạp nền qua [loadImage] (luồng IO) sau khi sheet đã hiện, nên sheet mở ngay, ảnh hiện sau (đóng sheet thì hủy luôn lần nạp). Đóng
 * sheet (✕, vuốt, Back) mà chưa chọn thì gọi [onCancelled]; cờ `chosen` phân biệt với đóng do chọn. [onDismissed] chạy mỗi lần sheet đóng,
 * chọn hay hủy. Người gọi KHÔNG tự gắn dismiss listener lên handle (sẽ thay listener ở đây); chỉ gỡ (null) lúc `onDestroy`.
 */
object WallpaperTargetSheet {

    fun show(
        activity: ComponentActivity,
        loadImage: suspend () -> Bitmap?,
        onChosen: (WallpaperTarget) -> Unit,
        onCancelled: () -> Unit,
        onDismissed: () -> Unit = {}
    ): DsDialogHandle {
        var chosen = false
        val handle = activity.dsDialogs.show { h ->
            val image by produceState<Bitmap?>(null) {
                value = try {
                    withContext(Dispatchers.IO) { loadImage() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "preview load failed ${e.javaClass.simpleName}")
                    null
                }
            }
            DsBottomSheet(onDismiss = h::dismiss, title = stringResource(R.string.wallpaper_target_sheet_title)) {
                // Hai khung xem trước cạnh nhau, giữa sheet.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP, Alignment.CenterHorizontally)
                ) {
                    PreviewColumn(image, WallpaperPreviewMode.HOME, R.string.wallpaper_target_home)
                    PreviewColumn(image, WallpaperPreviewMode.LOCK, R.string.wallpaper_target_lock)
                }
                Spacer(Modifier.height(24.dp))
                val buttons = listOf(
                    Triple(WallpaperTarget.HOME, R.string.wallpaper_target_home, DsButtonVariant.Secondary),
                    Triple(WallpaperTarget.LOCK, R.string.wallpaper_target_lock, DsButtonVariant.Secondary),
                    Triple(WallpaperTarget.BOTH, R.string.wallpaper_target_both, DsButtonVariant.Primary)
                )
                buttons.forEachIndexed { i, (target, label, variant) ->
                    DsButton(
                        text = stringResource(label),
                        onClick = {
                            if (!chosen) {
                                chosen = true
                                h.dismiss()
                                onChosen(target)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 6.dp),
                        variant = variant
                    )
                }
            }
        }
        handle.setOnDismissListener {
            onDismissed() // luôn chạy (chọn hay hủy): người gọi dọn tham chiếu tới sheet
            if (!chosen) onCancelled()
        }
        return handle
    }

    /** Một cột: khung xem trước rộng [PREVIEW_WIDTH], nhãn chú thích bên dưới. */
    @Composable
    private fun PreviewColumn(image: Bitmap?, mode: WallpaperPreviewMode, label: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WallpaperPreviewFrame(image, mode, Modifier.width(PREVIEW_WIDTH))
            BasicText(
                stringResource(label),
                style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_secondary), textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    /** Ảnh người dùng vừa chọn, thu nhỏ cho khung xem trước, xoay theo EXIF. Gọi ở luồng nền. */
    fun decodeUri(context: Context, uri: Uri): Bitmap? = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight) }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val orientation = cr.openInputStream(uri)?.use { ExifReader.readOrientation(it) }
            if (bmp != null && orientation != null) ExifReader.applyOrientation(bmp, orientation) else bmp
        }
    } catch (e: IOException) {
        Log.w(TAG, "decodeUri failed", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "decodeUri denied", e)
        null
    }

    /** Ảnh cài sẵn (drawable bitmap), thu nhỏ cho khung xem trước. Gọi ở luồng nền. */
    fun decodeResource(context: Context, @DrawableRes res: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, res, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeResource(context.resources, res, opts)
    }

    /** inSampleSize (lũy thừa 2) để cạnh dài còn >= [TARGET_PX]. */
    private fun sampleFor(w: Int, h: Int): Int {
        var sample = 1
        while (max(w, h) / (sample * 2) >= TARGET_PX) sample *= 2
        return sample
    }

    private const val TAG = "WallpaperTargetSheet"

    /** Rộng mỗi khung xem trước. Figma 72 dp; tăng lên vì khung giả lập có icon và đồng hồ. CHỈNH TAY. */
    private val PREVIEW_WIDTH = 104.dp

    /** Khe giữa hai khung xem trước (REF: 2 × ds_space_12). */
    private val PREVIEW_GAP = 24.dp

    /** Cạnh dài tối thiểu của ảnh xem trước sau khi thu nhỏ. */
    private const val TARGET_PX = 640
}
