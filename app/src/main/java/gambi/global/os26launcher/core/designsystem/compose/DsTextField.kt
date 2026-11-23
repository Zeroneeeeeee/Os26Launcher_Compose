package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass

/**
 * JS1 — ô nhập một dòng DS3 (thay `TextInputLayout` Material của hộp thoại Đổi tên): bề mặt `CONTROL` bo 12, cao 48, chữ `body`;
 * [hint] khi trống; dưới ô: [helper] bên trái, bộ đếm "n/[maxLength]" bên phải khi [maxLength] > 0. Không tự cắt độ dài: chủ sở hữu
 * lọc trong [onValueChange] (giữ quy tắc "đặt chữ trước bộ lọc" của RN).
 */
@Composable
fun DsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    helper: String? = null,
    maxLength: Int = 0,
    onDone: (() -> Unit)? = null
) {
    val primary = colorResource(R.color.ds_text_primary)
    val secondary = colorResource(R.color.ds_text_secondary)
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = DsTextStyles.body.copy(color = primary),
            cursorBrush = SolidColor(colorResource(R.color.ds_accent)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .dsGlass(DsSurface.CONTROL, DsCorner.Fixed(12f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.text.isEmpty() && hint != null) BasicText(hint, style = DsTextStyles.body.copy(color = secondary), maxLines = 1)
                    inner()
                }
            }
        )
        if (helper != null || maxLength > 0) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
                BasicText(helper.orEmpty(), style = DsTextStyles.caption.copy(color = secondary), modifier = Modifier.weight(1f))
                if (maxLength > 0) {
                    Spacer(Modifier.width(8.dp))
                    BasicText("${value.text.length}/$maxLength", style = DsTextStyles.caption.copy(color = secondary))
                }
            }
        }
    }
}
