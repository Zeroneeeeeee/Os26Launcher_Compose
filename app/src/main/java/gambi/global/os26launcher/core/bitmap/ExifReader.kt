package gambi.global.os26launcher.core.bitmap

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.InputStream

/** FX1: đọc và áp hướng xoay EXIF (tách nguyên từ `PickedImageSource`, không đổi hành vi). */
object ExifReader {

    /** Hướng xoay trong EXIF của [stream]; thiếu thẻ thì [ExifInterface.ORIENTATION_NORMAL]. Có thể ném khi luồng không phải ảnh. */
    fun readOrientation(stream: InputStream): Int =
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

    /** Ảnh xoay 90/270 độ thì chiều rộng và cao đổi chỗ khi xoay xong. */
    fun swapsAxes(orientation: Int): Boolean =
        orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE

    /** Xoay/lật [bmp] theo [orientation]; recycle [bmp] khi tạo bản mới. */
    fun applyOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(-90f)
                m.postScale(-1f, 1f)
            }

            else -> return bmp
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }
}
