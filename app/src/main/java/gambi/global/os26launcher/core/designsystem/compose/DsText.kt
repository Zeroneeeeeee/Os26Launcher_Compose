package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import gambi.global.os26launcher.R

/**
 * Kiểu chữ DS cho Compose, đủ 11 kiểu của `res/values/ds_type.xml` (`TextAppearance.Os26.Ds.*`): cỡ, độ đậm, line height lấy đúng
 * từ file đó (XML vẫn là nguồn; đổi số ở đó thì sửa cả ở đây). Không đặt màu: nơi dùng tự chọn token `ds_text_*`.
 *
 * - Họ font khai đủ 4 độ đậm và mỗi kiểu ghi `fontWeight`, để "Chữ đậm" của hệ thống làm đậm mọi chữ như iOS (JC, JD D7).
 * - JD F7: dùng line height của Figma (cột lineHeight trong `ds_type.xml`), căn giữa dòng, không đệm font. Các màn XML hiện tại
 *   không áp line height này (JC đo: phụ đề 1.1 cách 51 px thay vì 43 px), nên màn Compose sẽ gọn hơn màn XML: đó là chủ ý.
 */
object DsTextStyles {

    private val family = FontFamily(
        Font(R.font.be_vietnam_pro_light, FontWeight.Light),
        Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
        Font(R.font.be_vietnam_pro_bold, FontWeight.Bold)
    )

    private val lineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun style(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = 0.sp,
        lineHeightStyle = lineStyle
    )

    val headingLarge = style(FontWeight.Bold, 26.sp, 34.sp)
    val title = style(FontWeight.Bold, 20.sp, 24.sp)
    val card = style(FontWeight.Bold, 16.sp, 19.sp)
    val body = style(FontWeight.Normal, 14.sp, 17.sp)
    val bodyStrong = style(FontWeight.SemiBold, 14.sp, 17.sp)
    val value = style(FontWeight.Normal, 13.sp, 16.sp)
    val caption = style(FontWeight.Normal, 12.sp, 14.sp)
    val captionSmall = style(FontWeight.Normal, 11.sp, 13.sp)
    val badge = style(FontWeight.SemiBold, 11.sp, 13.sp)
    val display = style(FontWeight.Light, 40.sp, 40.sp)
    val sectionHeader = style(FontWeight.SemiBold, 14.sp, 17.sp)
}
