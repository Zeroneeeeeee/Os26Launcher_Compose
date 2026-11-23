package gambi.global.os26launcher.ui.wallpaper.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsBadge
import gambi.global.os26launcher.core.designsystem.compose.DsBadgeType
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.DrawableBox

/**
 * JS1 — ô ảnh nền kiểu Figma 2.2 (`302:3232`), bản Compose của `WallpaperTileView`: tỉ lệ 112:178, bo 14, giờ "9:41" giữa trên, badge
 * "MỚI" góc dưới trái, khi chọn thì viền `ds_accent` 2 dp và dấu chọn tròn góc trên phải (hai lớp: tròn `onb_check_circle` 24 + dấu
 * tích `onb_ic_check` 14, bài học RS). Nền đen dưới ảnh lúc chưa vẽ xong.
 */
@Composable
fun WallpaperTile(
    @DrawableRes thumb: Int,
    label: String,
    isNew: Boolean,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(112f / 178f)
            .clip(shape)
            .background(Color.Black)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                selected = chosen
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
    ) {
        Image(painterResource(thumb), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        BasicText(
            stringResource(R.string.wallpaper_tile_clock),
            style = DsTextStyles.card.copy(color = colorResource(R.color.ds_text_primary)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )
        if (isNew) {
            DsBadge(stringResource(R.string.onboarding_badge_new), Modifier.align(Alignment.BottomStart).padding(8.dp), DsBadgeType.New)
        }
        if (chosen) {
            Box(Modifier.fillMaxSize().border(2.dp, colorResource(R.color.ds_accent), shape))
            CheckMark(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** Dấu chọn hai lớp của Figma (thẻ theme 0.2, ô ảnh nền 2.2): tròn `onb_check_circle` 24 + dấu tích trắng `onb_ic_check` 14. */
@Composable
fun CheckMark(modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        DrawableBox(R.drawable.onb_check_circle, Modifier.fillMaxSize()) // shape: painterResource không đọc được
        Image(painterResource(R.drawable.onb_ic_check), contentDescription = null, modifier = Modifier.size(14.dp))
    }
}
