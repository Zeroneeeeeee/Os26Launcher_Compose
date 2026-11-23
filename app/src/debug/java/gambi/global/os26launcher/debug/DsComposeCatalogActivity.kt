package gambi.global.os26launcher.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.core.designsystem.compose.DsAlertDialog
import gambi.global.os26launcher.core.designsystem.compose.DsBadge
import gambi.global.os26launcher.core.designsystem.compose.DsBottomSheet
import gambi.global.os26launcher.core.designsystem.compose.DsBadgeType
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonSize
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsCardGroup
import gambi.global.os26launcher.core.designsystem.compose.DsChip
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsSegmented
import gambi.global.os26launcher.core.designsystem.compose.DsSliderRow
import gambi.global.os26launcher.core.designsystem.compose.DsToggle
import gambi.global.os26launcher.core.designsystem.compose.DsIconTile
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.designsystem.compose.DsSearchField
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles

/**
 * JD — CHỈ BẢN DEBUG. Catalog design system bằng Compose, đặt cạnh `DsCatalogActivity` (bản View) để so. Mỗi Task của JD thêm phần của
 * mình. Chữ trong catalog là chữ thử, không dịch.
 *
 * Mở: `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.DsComposeCatalogActivity`.
 */
class DsComposeCatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Đo đạc (JD Task 8): `--ez noGlass true` tắt kính thật (mọi dsGlass vẽ bề mặt tĩnh) để tách chi phí kính khỏi chi phí Compose.
            val noGlass = intent.getBooleanExtra(EXTRA_NO_GLASS, false)
            DsScreen(title = "DS Compose", onBack = ::finish) {
                androidx.compose.runtime.CompositionLocalProvider(
                    gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop provides
                        if (noGlass) null else gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop.current
                ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextStylesSection()
                    DialogsSection()
                    SegmentedAndSliderSection()
                    RowsSection()
                    SearchSection()
                    ButtonsSection()
                    ChipsSection()
                    BadgesAndTilesSection()
                }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_NO_GLASS = "noGlass"
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, style = DsTextStyles.sectionHeader.copy(color = colorResource(R.color.ds_text_secondary)), modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TextStylesSection() {
    SectionTitle("Text styles")
    val primary = colorResource(R.color.ds_text_primary)
    listOf(
        "headingLarge 26/34" to DsTextStyles.headingLarge,
        "title 20/24" to DsTextStyles.title,
        "card 16/19" to DsTextStyles.card,
        "body 14/17" to DsTextStyles.body,
        "bodyStrong 14/17" to DsTextStyles.bodyStrong,
        "value 13/16" to DsTextStyles.value,
        "caption 12/14" to DsTextStyles.caption,
        "captionSmall 11/13" to DsTextStyles.captionSmall,
        "badge 11/13" to DsTextStyles.badge,
        "display 40/40" to DsTextStyles.display,
        "sectionHeader 14/17" to DsTextStyles.sectionHeader
    ).forEach { (name, style) -> BasicText("Aa Ăă Đđ — $name", style = style.copy(color = primary)) }
}

@Composable
private fun DialogsSection() {
    var dialog by remember { mutableStateOf(false) }
    var lockedDialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("-") }
    SectionTitle("Dialogs — last: $log")
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DsButton("Dialog", onClick = { dialog = true }, size = DsButtonSize.M)
        DsButton("Locked", onClick = { lockedDialog = true }, size = DsButtonSize.M, variant = DsButtonVariant.Secondary)
        DsButton("Sheet", onClick = { sheet = true }, size = DsButtonSize.M, variant = DsButtonVariant.Secondary)
    }
    if (dialog) {
        DsAlertDialog(
            onDismiss = { dialog = false; if (log == "-") log = "dismissed" },
            title = "Đặt làm màn hình chính?",
            message = "Hộp thoại thử, có thể đóng bằng Back hoặc chạm ra ngoài.",
            positive = "Đặt ngay" to { log = "positive" },
            negative = "Để sau" to { log = "negative" }
        )
    }
    if (lockedDialog) {
        DsAlertDialog(
            onDismiss = { lockedDialog = false },
            title = "Không thể bỏ qua",
            positive = "OK" to { log = "locked ok" },
            cancelable = false
        )
    }
    if (sheet) {
        DsBottomSheet(onDismiss = { sheet = false; log = "sheet closed" }, title = "Bottom sheet") {
            BasicText("Kéo xuống để đóng, hoặc chạm lớp mờ / nút đóng.", style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary)))
            DsButton("Nút trong sheet", onClick = { log = "sheet button" }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
private fun SegmentedAndSliderSection() {
    var seg2 by remember { mutableIntStateOf(0) }
    var seg4 by remember { mutableIntStateOf(1) }
    var selects by remember { mutableIntStateOf(0) }
    var slider by remember { mutableIntStateOf(50) }
    var trackStarts by remember { mutableIntStateOf(0) }
    var trackEnds by remember { mutableIntStateOf(0) }
    SectionTitle("Segmented — selects: $selects")
    DsSegmented(listOf("Thư mục", "Danh sách"), seg2, onSelect = { seg2 = it; selects++ })
    DsSegmented(listOf("Nhỏ", "Vừa", "Lớn", "Rất lớn"), seg4, onSelect = { seg4 = it; selects++ })
    DsSegmented(listOf("Số", "Chấm"), 0, onSelect = { selects++ }, fill = false)
    SectionTitle("Slider — value: $slider, tracking: $trackStarts/$trackEnds")
    DsSliderRow("Độ mờ", slider, onValueChange = { slider = it }, onTrackingChange = { if (it) trackStarts++ else trackEnds++ })
}

@Composable
private fun RowsSection() {
    var toggle by remember { mutableStateOf(true) }
    var radio by remember { mutableIntStateOf(0) }
    var standalone by remember { mutableStateOf(false) }
    var changes by remember { mutableIntStateOf(0) }
    var rowTaps by remember { mutableIntStateOf(0) }
    SectionTitle("Rows — toggle changes: $changes, row taps: $rowTaps")
    DsCardGroup {
        row("Chevron row", onClick = { rowTaps++ }, leading = { DsIconTile(R.drawable.ds_ic_app_library, DsTileColor.BLUE) }, trailing = DsRowTrailing.Chevron)
        row("Value row", onClick = { rowTaps++ }, description = "Mô tả ngắn dưới tiêu đề", trailing = DsRowTrailing.Value("4 × 6"))
        row("Toggle row", onClick = { toggle = !toggle; changes++ }, trailing = DsRowTrailing.Toggle(toggle))
        listOf("Radio A", "Radio B").forEachIndexed { i, label ->
            row(label, onClick = { radio = i }, trailing = DsRowTrailing.Radio(radio == i))
        }
    }
    SectionTitle("Standalone toggle")
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DsToggle(standalone, onCheckedChange = { standalone = it; changes++ })
        DsToggle(true, onCheckedChange = {}, enabled = false)
    }
}

@Composable
private fun SearchSection() {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableIntStateOf(0) }
    SectionTitle("Search field — searched: $searched")
    DsSearchField(value = query, onValueChange = { query = it }, hint = "Tìm ứng dụng", modifier = Modifier.fillMaxWidth(), onSearch = { searched++ })
}

@Composable
private fun ColumnScope.ButtonsSection() {
    var taps by remember { mutableIntStateOf(0) }
    SectionTitle("Buttons — taps: $taps")
    DsButton("Primary L", onClick = { taps++ }, modifier = Modifier.fillMaxWidth())
    DsButton("Secondary L", onClick = { taps++ }, modifier = Modifier.fillMaxWidth(), variant = DsButtonVariant.Secondary)
    DsButton("Disabled", onClick = { taps++ }, modifier = Modifier.fillMaxWidth(), enabled = false)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DsButton("Primary M", onClick = { taps++ }, size = DsButtonSize.M)
        DsButton("Secondary S", onClick = { taps++ }, variant = DsButtonVariant.Secondary, size = DsButtonSize.S)
        DsButton("Primary S", onClick = { taps++ }, size = DsButtonSize.S)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSection() {
    var selected by remember { mutableStateOf(setOf(0)) }
    SectionTitle("Chips")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Tất cả", "Mạng xã hội", "Tiện ích", "Một tên chip rất dài để thử cắt chữ").forEachIndexed { i, label ->
            DsChip(label, selected = i in selected, onClick = { selected = if (i in selected) selected - i else selected + i })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesAndTilesSection() {
    SectionTitle("Badges & icon tiles")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DsBadge("MỚI")
        DsBadge("Cảnh báo", type = DsBadgeType.Warning)
        DsBadge("Lỗi", type = DsBadgeType.Danger)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DsTileColor.entries.forEach { DsIconTile(R.drawable.ds_ic_app_library, it) }
    }
}
