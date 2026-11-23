package gambi.global.os26launcher.data.backdrop

import android.graphics.Bitmap

/**
 * Nguồn ảnh nền cho kính.
 *
 * Kính chỉ khúc xạ được thứ nằm trong cây View của app, nên mọi nguồn đều phải đưa ảnh về dạng
 * [Bitmap] để đặt vào một `ImageView` đứng trước kính. Launcher chỉ làm việc với interface này,
 * không biết ảnh đến từ đâu, nhờ vậy đổi hoặc thêm nguồn không phải sửa launcher.
 *
 * Hiện có:
 * - [PickedImageSource]: ảnh người dùng chọn bằng Photo Picker (phương án A, đang dùng).
 * - [SystemWallpaperSource]: wallpaper thật của hệ thống (phương án B, tạm gác, xem ghi chú trong file).
 */
interface BackdropSource {

    /** Tên ngắn hiển thị trên dòng trạng thái. */
    val name: String

    /**
     * Nạp ảnh nền. Có thể chậm (đọc file, giải mã) nên chỉ gọi từ luồng nền.
     * Trả về null nếu chưa có ảnh hoặc không đọc được.
     *
     * [reqW] và [reqH] là kích thước tối thiểu cần dùng (thường là kích thước màn hình), để nguồn
     * không nạp ảnh lớn hơn mức cần thiết.
     */
    fun load(reqW: Int, reqH: Int): Bitmap?
}
