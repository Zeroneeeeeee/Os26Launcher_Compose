package gambi.global.os26launcher.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import gambi.global.os26launcher.domain.model.WallpaperTarget
import kotlinx.coroutines.flow.StateFlow

/**
 * Ảnh nền của launcher (phương án A: ảnh người dùng chọn, app giữ bản sao trong `filesDir`) và việc áp
 * ảnh đó làm wallpaper hệ thống.
 *
 * `Bitmap` và `Uri` là ngoại lệ có chủ ý với luật "domain không import android.*": ảnh nền là bitmap,
 * không có kiểu thuần Kotlin nào thay được mà không sao chép pixel.
 */
interface BackdropRepository {

    /**
     * CF: chờ đọc xong nguồn nền (`appearance_wallpaper_kind`, màu) lần đầu. Các hàm đồng bộ bên dưới đọc bộ đệm của lựa chọn này (chưa đọc
     * thì coi như AUTO), nên Home gọi hàm này trước lần nạp nền đầu tiên để khỏi nháy ảnh mặc định rồi mới sang nền màu.
     */
    suspend fun awaitWallpaperConfig()

    /**
     * Đã có bản sao ảnh nền được lưu từ lần chọn trước chưa (và đang dùng ảnh: CF, nền màu thì false). Chỉ kiểm tra file tồn tại, không
     * giải mã.
     */
    fun hasBackdrop(): Boolean

    /**
     * Dấu phiên bản của ảnh nền đã lưu (`lastModified` của file); chưa chọn ảnh thì là khóa ảnh mặc định (-1 sáng, -2 tối), 0 nếu không có gì. Màn Cài đặt đổi ảnh thì giá trị này
     * đổi; Home so với giá trị đã hiển thị ở onResume để biết có phải nạp lại. Độ phân giải theo hệ file (có máy chỉ
     * tới giây), đủ cho thao tác của người dùng.
     */
    fun backdropVersion(): Long

    /**
     * FX1 (C4): phát lại [backdropVersion] sau mỗi lần nhập ảnh thành công (kể cả khi màn Cài đặt đã đóng giữa chừng), để Home nạp ảnh
     * mới tại chỗ thay vì chỉ so ở onResume. Ảnh mặc định đổi sáng/tối KHÔNG phát qua đây (Home vẫn so ở onResume).
     */
    val backdropVersions: StateFlow<Long>

    /** WP: có ảnh nền mặc định (ảnh iOS 27 bản thử) để dùng khi chưa chọn ảnh không. CF: nền màu đặc cũng đi đường này. */
    fun hasDefaultBackdrop(): Boolean

    /** WP: nạp ảnh mặc định (sáng/tối theo hệ thống) ở cỡ màn. CF: nguồn nền là COLOR thì trả bitmap một màu. Trả null nếu không có. */
    suspend fun loadDefaultBackdrop(reqW: Int, reqH: Int): Bitmap?

    /** Nạp ảnh đã lưu, thu nhỏ vừa [reqW] x [reqH]. Trả null nếu chưa có ảnh hoặc ảnh không đọc được. */
    suspend fun loadBackdrop(reqW: Int, reqH: Int): Bitmap?

    /**
     * Nhập ảnh người dùng vừa chọn: lưu bản sao toàn ảnh (xoay theo EXIF, cắt viền trong suốt, có trần pixel; JPEG bình thường chép nguyên
     * byte). Việc ghi không bị hủy khi người gọi bị hủy. Trả bitmap đã xử lý (hoặc bản xem trước nhỏ), null nếu lỗi.
     */
    suspend fun importBackdrop(uri: Uri, reqW: Int, reqH: Int): Bitmap?

    /** Lý do máy này không đặt được wallpaper, null nếu đặt được. */
    fun wallpaperUnavailableReason(): String?

    /** Áp ảnh đã lưu làm wallpaper hệ thống. Không ném: mọi lỗi trả về qua [Result.failure]. */
    suspend fun applyAsWallpaper(target: WallpaperTarget, width: Int, height: Int): Result<Unit>

    /**
     * P2-4: áp ảnh cài sẵn [presetId] (`WallpaperPresets`) làm wallpaper hệ thống, đọc thẳng từ tài nguyên (không qua lựa chọn nguồn nền
     * đang lưu, nên gọi ngay sau khi vừa ghi lựa chọn vẫn đúng ảnh). [width]/[height] <= 0 = cỡ màn hình. Không ném.
     */
    suspend fun applyPresetAsWallpaper(presetId: String, target: WallpaperTarget, width: Int = 0, height: Int = 0): Result<Unit>
}
