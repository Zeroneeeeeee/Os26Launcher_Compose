package gambi.global.os26launcher.core.designsystem.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.glass.compose.dsGlass

/**
 * JD Task 4 — ô tìm DS3 bằng Compose (bản View: `DsSearchField` + `ds_search_field.xml`). Cao nhìn thấy 44 trong khung tối thiểu 48
 * (đệm dọc 2 nằm ngoài bề mặt), kính `CONTROL` bo PILL, đệm ngang 14, kính lúp 24 tô `ds_text_secondary`, khe 8, chữ `body` một dòng,
 * gợi ý [hint] màu `ds_text_secondary`, con trỏ `ds_accent`, nút tìm trên bàn phím gọi [onSearch]. Rộng theo [modifier].
 */
@Composable
fun DsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null
) {
    val secondary = colorResource(R.color.ds_text_secondary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_primary)),
        cursorBrush = SolidColor(colorResource(R.color.ds_accent)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier = modifier.defaultMinSize(minHeight = dimensionResource(R.dimen.ds_touch_min)),
        decorationBox = { inner ->
            Row(
                Modifier
                    .padding(vertical = 2.dp)
                    .height(VISIBLE)
                    .fillMaxWidth()
                    .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ds_ic_search_field),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(secondary),
                    modifier = Modifier.size(dimensionResource(R.dimen.ds_icon_size))
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) BasicText(hint, style = DsTextStyles.body.copy(color = secondary), maxLines = 1)
                    inner()
                }
            }
        }
    )
}

private val VISIBLE = 44.dp
