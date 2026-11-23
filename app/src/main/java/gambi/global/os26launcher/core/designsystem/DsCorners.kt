package gambi.global.os26launcher.core.designsystem

import android.content.Context
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.glass.GlassEnvironment
import gambi.global.os26launcher.core.glass.GlassTuning
import kotlin.math.min
import gambi.global.os26launcher.domain.model.GlassLook

/**
 * DS3 Task 1b: chỗ duy nhất quy [DsCorner] ra bán kính px cho thành phần v3.
 *
 * Vai kính đọc thanh "Bo góc" của kính qua [GlassEnvironment.look] (GT) và quy đổi bằng [GlassTuning.cornerRadius], nên bo của
 * card/sheet v3 đổi cùng lúc với dock, panel, thư mục. Không có khóa cài đặt riêng cho bo góc v3. Không cấp phát, gọi được
 * trong `Drawable.onBoundsChange`.
 */
object DsCorners {

    /** Bán kính gốc của [role] (px), lấy từ `ds_radius_*`. */
    fun basePx(context: Context, role: DsCornerRole): Float = context.resources.getDimension(
        when (role) {
            DsCornerRole.CARD -> R.dimen.ds_radius_card
            DsCornerRole.CARD_LARGE -> R.dimen.ds_radius_card_large
            DsCornerRole.SHEET -> R.dimen.ds_radius_sheet
            DsCornerRole.TILE -> R.dimen.ds_radius_tile
        }
    )

    /**
     * Bán kính px của [corner] cho khung [widthPx] × [heightPx]. Cỡ chưa biết thì truyền 0: `Pill` trả 0 (drawable tính lại ở
     * `onBoundsChange`), vai kính trả số đã nhân mà chưa kẹp nửa cạnh ngắn.
     */
    fun radiusPx(
        context: Context,
        corner: DsCorner,
        widthPx: Int,
        heightPx: Int,
        /** JS1: thông số đang xem trước (màn Tinh chỉnh kính); mặc định là thông số đã lưu. */
        look: GlassLook = GlassEnvironment.from(context).look
    ): Float = when (corner) {
        DsCorner.Pill -> if (widthPx > 0 && heightPx > 0) min(widthPx, heightPx) / 2f else 0f
        is DsCorner.Fixed -> {
            val base = corner.dp * context.resources.displayMetrics.density
            if (corner.glass) GlassTuning.cornerRadius(look, base, widthPx.toFloat(), heightPx.toFloat()) else base
        }
        is DsCorner.Role -> {
            val base = basePx(context, corner.role)
            if (corner.role.isGlass) {
                GlassTuning.cornerRadius(look, base, widthPx.toFloat(), heightPx.toFloat())
            } else {
                base
            }
        }
    }

    /**
     * Báo [listener] khi cấu hình kính đổi (thanh "Bo góc" và các thông số kính khác, mức kính). Chuyển tiếp sang
     * [GlassEnvironment.addListener]: gọi trên main thread, nhớ [removeListener] khi view rời cửa sổ.
     */
    fun addListener(context: Context, listener: () -> Unit) {
        GlassEnvironment.from(context).addListener(listener)
    }

    fun removeListener(context: Context, listener: () -> Unit) {
        GlassEnvironment.from(context).removeListener(listener)
    }
}
