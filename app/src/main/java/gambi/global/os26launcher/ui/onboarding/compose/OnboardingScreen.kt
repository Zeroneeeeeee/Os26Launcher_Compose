package gambi.global.os26launcher.ui.onboarding.compose

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.AuroraBackgroundDrawable
import gambi.global.os26launcher.core.designsystem.AuroraSpots
import gambi.global.os26launcher.core.designsystem.DsCorner
import gambi.global.os26launcher.core.designsystem.DsCornerRole
import gambi.global.os26launcher.core.designsystem.DsSurface
import gambi.global.os26launcher.core.designsystem.DsTileColor
import gambi.global.os26launcher.core.designsystem.compose.DsButton
import gambi.global.os26launcher.core.designsystem.compose.DsButtonVariant
import gambi.global.os26launcher.core.designsystem.compose.DsCardGroup
import gambi.global.os26launcher.core.designsystem.compose.DsClockText
import gambi.global.os26launcher.core.designsystem.compose.DsIconTile
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsTextStyles
import gambi.global.os26launcher.core.designsystem.compose.drawDrawable
import gambi.global.os26launcher.core.designsystem.compose.dsPressHighlight
import gambi.global.os26launcher.core.designsystem.compose.dsPressable
import gambi.global.os26launcher.core.glass.compose.LocalDsBackdrop
import gambi.global.os26launcher.core.glass.compose.dsGlass
import gambi.global.os26launcher.core.wallpaper.WallpaperPresets
import gambi.global.os26launcher.domain.config.ThemeId
import gambi.global.os26launcher.ui.onboarding.OnboardingStep
import gambi.global.os26launcher.ui.onboarding.ThemeCatalog
import gambi.global.os26launcher.ui.settings.ThemeCard
import gambi.global.os26launcher.ui.wallpaper.compose.CheckMark
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JS5 — dữ liệu và lệnh của màn Onboarding (đọc từ `OnboardingViewModel` ở Activity). */
internal class OnboardingUi(
    val step: OnboardingStep,
    val theme: ThemeId,
    val swatch: Int,
    val isDefaultLauncher: Boolean,
    val languageName: String
)

internal class OnboardingActions(
    val onBack: () -> Unit,
    val onSkip: () -> Unit,
    val onPrimary: () -> Unit,
    val onSecondary: () -> Unit,
    val onLanguage: () -> Unit,
    val onChooseTheme: (ThemeId) -> Unit,
    val onChooseSwatch: (Int) -> Unit,
    val onJump: (OnboardingStep) -> Unit
)

/**
 * JS5 — Onboarding 0.1–0.5 bằng Compose (thay `activity_onboarding.xml`, `onboarding_*.xml`, `ThemeCardView`, `DsOnboardingTopBar`,
 * `DsStepIndicatorView`). Phần CỐ ĐỊNH: nền aurora (mờ chéo 280 ms giữa bộ đốm của từng màn, ghi làm lớp backdrop cho kính thẻ), thanh trên,
 * chỉ báo bước, hàng nút. Chỉ phần giữa trượt ngang ngắn (30% bề rộng) + mờ dần: tới thì vào từ phải, lùi thì vào từ trái.
 */
@Composable
internal fun OnboardingScreen(ui: OnboardingUi, actions: OnboardingActions) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(colorResource(R.color.ds_bg_base))) {
        Crossfade(ui.step, animationSpec = tween(SLIDE_MS), label = "onbAurora", modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) { step ->
            val context = LocalContext.current
            val drawable = remember(step) { AuroraBackgroundDrawable(context, AuroraSpots.ONBOARDING.getOrElse(step.index) { AuroraSpots.DEFAULT }) }
            Box(Modifier.fillMaxSize().drawBehind { drawDrawable(drawable) })
        }
        CompositionLocalProvider(LocalDsBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                TopBar(ui, actions)
                AnimatedContent(
                    targetState = ui.step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { (it * SLIDE_FRACTION * dir).toInt() } + fadeIn(tween(SLIDE_MS)))
                            .togetherWith(slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { (-it * SLIDE_FRACTION * dir).toInt() } + fadeOut(tween(SLIDE_MS)))
                    },
                    label = "onbPage",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomePage()
                        OnboardingStep.THEME -> ThemePage(ui.theme, actions.onChooseTheme)
                        OnboardingStep.WALLPAPER -> WallpaperPage(ui.swatch, actions.onChooseSwatch)
                        OnboardingStep.DEFAULT_LAUNCHER -> DefaultLauncherPage()
                        OnboardingStep.DONE -> DonePage(ui, actions.onJump)
                    }
                }
                Footer(ui.step, actions)
            }
        }
    }
}

/** DS3 Task 7: thanh trên cao 56, đệm ngang 16: nút quay lại tròn 40 trong vùng chạm 48 ở trái (hoặc chip ngôn ngữ ở 0.1), "Bỏ qua" ở phải. */
@Composable
private fun TopBar(ui: OnboardingUi, actions: OnboardingActions) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.ds_onboarding_bar_height))
            .padding(horizontal = dimensionResource(R.dimen.ds_space_16))
    ) {
        AnimatedVisibility(ui.step.showBack, enter = fadeIn(tween(CHROME_MS)), exit = fadeOut(tween(CHROME_MS)), modifier = Modifier.align(Alignment.CenterStart)) {
            val back = stringResource(R.string.ds_back)
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(dimensionResource(R.dimen.ds_touch_min))
                    .semantics { contentDescription = back }
                    .clickable(interaction, indication = null, role = Role.Button, onClick = actions.onBack),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(40.dp).dsPressable(interaction).dsGlass(DsSurface.CONTROL, DsCorner.Pill).dsPressHighlight(interaction), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.ds_ic_chevron_left),
                        null,
                        colorFilter = ColorFilter.tint(colorResource(R.color.ds_text_primary)),
                        modifier = Modifier.size(dimensionResource(R.dimen.ds_icon_size))
                    )
                }
            }
        }
        // LG: chip ngôn ngữ chỉ ở 0.1 (Figma 302:3681): pill CONTROL cao 34, đệm 10/12, icon quả địa cầu 18 màu ds_glyph_sky, chữ 13.
        AnimatedVisibility(ui.step == OnboardingStep.WELCOME, enter = fadeIn(tween(CHROME_MS)), exit = fadeOut(tween(CHROME_MS)), modifier = Modifier.align(Alignment.CenterStart)) {
            val description = stringResource(R.string.lang_chip_desc, ui.languageName)
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .heightIn(min = dimensionResource(R.dimen.ds_touch_min))
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .clickable(interaction, indication = null, role = Role.Button, onClick = actions.onLanguage),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .height(34.dp)
                        .dsPressable(interaction)
                        .dsGlass(DsSurface.CONTROL, DsCorner.Pill)
                        .dsPressHighlight(interaction)
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painterResource(R.drawable.ds_ic_language),
                        null,
                        colorFilter = ColorFilter.tint(colorResource(R.color.ds_glyph_sky)),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(ui.languageName, style = DsTextStyles.value.copy(color = colorResource(R.color.ds_text_primary)))
                }
            }
        }
        AnimatedVisibility(ui.step.showSkip, enter = fadeIn(tween(CHROME_MS)), exit = fadeOut(tween(CHROME_MS)), modifier = Modifier.align(Alignment.CenterEnd)) {
            BasicText(
                stringResource(R.string.ds_onboarding_skip),
                style = DsTextStyles.value.copy(color = colorResource(R.color.ds_text_tertiary)),
                modifier = Modifier
                    .heightIn(min = dimensionResource(R.dimen.ds_touch_min))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = actions.onSkip)
                    .padding(horizontal = 8.dp, vertical = 15.dp)
            )
        }
    }
}

@Composable
private fun Footer(step: OnboardingStep, actions: OnboardingActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = dimensionResource(R.dimen.ds_footer_padding_start),
                top = 12.dp,
                end = dimensionResource(R.dimen.ds_footer_padding_end),
                bottom = dimensionResource(R.dimen.ds_footer_padding_bottom)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(step.index, OnboardingStep.entries.size)
        DsButton(stringResource(step.primaryLabel), onClick = actions.onPrimary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        // Nút phụ hiện/ẩn làm chân trang cao/thấp (Figma 0.4: 180 thay 124).
        AnimatedVisibility(step.secondaryLabel != null) {
            val label = step.secondaryLabel
            if (label != null) {
                DsButton(
                    stringResource(label),
                    onClick = actions.onSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = dimensionResource(R.dimen.ds_footer_gap)),
                    variant = DsButtonVariant.Secondary
                )
            }
        }
    }
}

/**
 * DS3 Task 5: chỉ báo bước (Figma Page Indicator 302:3641): khung CONTROL bo PILL, đệm 7/10; chấm 6 × 6 `ds_surface_control` cách 6, chấm
 * hiện tại 18 × 6 gradient `ds_primary_start` → `ds_primary_end`; đổi bước thì chấm cũ co và chấm mới giãn trong 200 ms.
 */
@Composable
private fun StepIndicator(current: Int, count: Int) {
    val off = colorResource(R.color.ds_surface_control)
    val start = colorResource(R.color.ds_primary_start)
    val end = colorResource(R.color.ds_primary_end)
    val desc = stringResource(R.string.ds_step_indicator_desc, current + 1, count) // TalkBack đọc "Bước 1/5" như DsStepIndicatorView
    Row(
        Modifier.dsGlass(DsSurface.CONTROL, DsCorner.Pill).padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until count) {
            val w by animateDpAsState(if (i == current) 18.dp else 6.dp, tween(200), label = "onbDot")
            Canvas(Modifier.size(width = w, height = 6.dp)) {
                val r = CornerRadius(3.dp.toPx())
                if (i == current) drawRoundRect(Brush.horizontalGradient(listOf(start, end)), cornerRadius = r)
                else drawRoundRect(off, cornerRadius = r)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Trang

@Composable
private fun Title(text: String, center: Boolean, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = DsTextStyles.headingLarge.copy(color = colorResource(R.color.ds_text_primary), textAlign = if (center) TextAlign.Center else TextAlign.Start),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun Subtitle(text: String, center: Boolean, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_secondary), textAlign = if (center) TextAlign.Center else TextAlign.Start),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

/** 0.1 Chào mừng (Figma `302:3677`). */
@Composable
private fun WelcomePage() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.onb_hero), null, contentScale = ContentScale.Fit, modifier = Modifier.widthIn(max = 300.dp).heightIn(max = 375.dp))
        Title(stringResource(R.string.onboarding_welcome_title), center = true, modifier = Modifier.padding(top = 12.dp))
        Subtitle(stringResource(R.string.onboarding_welcome_subtitle), center = true)
    }
}

/** 0.2 Chọn kiểu giao diện (Figma `302:3695`): lưới 2 × 2 thẻ theme (dùng chung thẻ Compose của màn 1.1). Chạm thẻ chỉ đổi lựa chọn. */
@Composable
private fun ThemePage(chosen: ThemeId, onChoose: (ThemeId) -> Unit) {
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = margin, end = margin, top = 8.dp, bottom = 16.dp)) {
        Title(stringResource(R.string.onboarding_theme_title), center = false)
        Subtitle(stringResource(R.string.onboarding_theme_subtitle), center = false)
        Spacer(Modifier.height(20.dp))
        ThemeCatalog.list.chunked(2).forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { info -> ThemeCard(info, info.id == chosen, onClick = { onChoose(info.id) }, modifier = Modifier.weight(1f)) }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 0.3 Chọn hình nền (Figma `302:3737`): khung điện thoại 176 × 312 (ảnh nền, giờ, ngày, dock kính) + dải ô ảnh cài sẵn. */
@Composable
private fun WallpaperPage(swatch: Int, onChoose: (Int) -> Unit) {
    val presets = WallpaperPresets.list
    val chosen = swatch.coerceIn(0, presets.lastIndex)
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    val context = LocalContext.current
    val date = remember {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
        Title(stringResource(R.string.onboarding_wallpaper_title), center = false, modifier = Modifier.padding(horizontal = margin))
        Subtitle(stringResource(R.string.onboarding_wallpaper_subtitle), center = false, modifier = Modifier.padding(horizontal = margin))
        val phoneBackdrop = rememberLayerBackdrop()
        Box(
            Modifier
                .padding(top = 20.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 176.dp, height = 312.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            Image(
                painterResource(presets[chosen].thumb), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().layerBackdrop(phoneBackdrop)
            )
            DsClockText(
                DsTextStyles.display.copy(color = colorResource(R.color.ds_text_primary)),
                Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
            )
            BasicText(
                date,
                style = DsTextStyles.caption.copy(color = colorResource(R.color.ds_text_primary)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 82.dp)
            )
            CompositionLocalProvider(LocalDsBackdrop provides phoneBackdrop) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .height(46.dp)
                        .dsGlass(DsSurface.CARD, DsCorner.Fixed(20f))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DsIconTile(R.drawable.ds_ic_weather, DsTileColor.AMBER)
                    DsIconTile(R.drawable.ds_ic_search, DsTileColor.BLUE)
                    DsIconTile(R.drawable.ds_ic_wallpaper, DsTileColor.PINK)
                    DsIconTile(R.drawable.ds_ic_general, DsTileColor.SLATE)
                }
            }
        }
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEachIndexed { i, preset ->
                val selected = i == chosen
                val presetName = stringResource(preset.name)
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .size(width = 56.dp, height = 96.dp)
                        .clip(shape)
                        .background(Color.Black)
                        .semantics { contentDescription = presetName; this.selected = selected }
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { onChoose(i) }
                ) {
                    Image(painterResource(preset.thumb), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (selected) {
                        Box(Modifier.fillMaxSize().border(2.dp, colorResource(R.color.ds_text_primary), shape))
                        CheckMark(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
                    }
                }
            }
        }
    }
}

/** 0.4 Đặt làm màn hình chính (Figma `302:3772`): minh họa, tiêu đề, hai bước đánh số trong card. Hai nút ở chân trang cố định. */
@Composable
private fun DefaultLauncherPage() {
    val appName = stringResource(R.string.app_name)
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = margin, end = margin, top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.onb_default_launcher), null, modifier = Modifier.size(180.dp))
        Title(stringResource(R.string.onboarding_default_title, appName), center = true, modifier = Modifier.padding(top = 16.dp))
        Subtitle(stringResource(R.string.onboarding_default_subtitle), center = true)
        Column(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .dsGlass(DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            NumberedStep("1", stringResource(R.string.onboarding_default_step1), minHeight = 52)
            Box(Modifier.padding(start = 40.dp).fillMaxWidth().height(1.dp).background(colorResource(R.color.ds_divider)))
            NumberedStep("2", stringResource(R.string.onboarding_default_step2, appName), minHeight = 58)
        }
    }
}

@Composable
private fun NumberedStep(number: String, text: String, minHeight: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = minHeight.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).dsGlass(DsSurface.CONTROL, DsCorner.Pill), contentAlignment = Alignment.Center) {
            BasicText(number, style = DsTextStyles.bodyStrong.copy(color = colorResource(R.color.ds_text_primary)))
        }
        BasicText(text, style = DsTextStyles.body.copy(color = colorResource(R.color.ds_text_primary)), modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

/** 0.5 Hoàn tất (Figma `302:3796`): tóm tắt lựa chọn; chạm hàng về bước đó. */
@Composable
private fun DonePage(ui: OnboardingUi, onJump: (OnboardingStep) -> Unit) {
    val margin = dimensionResource(R.dimen.ds_screen_margin_h)
    val context = LocalContext.current
    val theme = stringResource(ThemeCatalog.of(ui.theme).name)
    val preset = WallpaperPresets.list.getOrNull(ui.swatch) ?: WallpaperPresets.list.first() // WI
    val wallpaper = stringResource(preset.name)
    val home = stringResource(if (ui.isDefaultLauncher) R.string.onboarding_done_home_set else R.string.onboarding_done_home_not_set)
    val themeTitle = stringResource(R.string.onboarding_done_theme)
    val wallpaperTitle = stringResource(R.string.onboarding_done_wallpaper)
    val homeTitle = stringResource(R.string.onboarding_done_home)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = margin, end = margin, top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.onb_done), null, modifier = Modifier.size(200.dp))
        Title(stringResource(R.string.onboarding_done_title), center = true, modifier = Modifier.padding(top = 20.dp))
        Subtitle(stringResource(R.string.onboarding_done_subtitle), center = true)
        DsCardGroup(Modifier.padding(top = 20.dp)) {
            row(themeTitle, { onJump(OnboardingStep.THEME) },
                leading = { DsIconTile(R.drawable.ds_ic_liquid_glass, DsTileColor.VIOLET) }, trailing = DsRowTrailing.Value(theme))
            row(wallpaperTitle, { onJump(OnboardingStep.WALLPAPER) },
                leading = { DsIconTile(R.drawable.ds_ic_wallpaper, DsTileColor.PINK) }, trailing = DsRowTrailing.Value(wallpaper))
            row(homeTitle, { onJump(OnboardingStep.DEFAULT_LAUNCHER) },
                leading = { DsIconTile(R.drawable.ds_ic_home, DsTileColor.BLUE) }, trailing = DsRowTrailing.Value(home))
        }
    }
}

private const val SLIDE_MS = 280
private const val CHROME_MS = 150
private const val SLIDE_FRACTION = 0.3f
