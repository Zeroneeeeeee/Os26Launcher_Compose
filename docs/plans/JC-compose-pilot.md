# JC — Compose + Backdrop Glass Pilot (Go/No-Go Gate for the Compose UI Rewrite) — Implementation Plan

> Work rules: `docs/AGENTS.md` and `docs/Workflow.md` apply. One Task at a time; report after each Task and wait for the project owner's confirmation before ticking it and starting the next. No automated tests (project rule). Manual verification on Vsmart Live 4 (Android 11) and Oppo A3x (Android 14). Commands are written for PowerShell.
>
> Language: this plan is in English by the project owner's decision (2026-11-19), an exception to the "docs in Vietnamese" rule in `AGENTS.md`.

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19
**Status:** Closed 2026-11-19 — owner accepted **conditional go** for direction B. Project-device checks (Live 4, Oppo) carried into the next plan (JD) as its first task.
**Devices:** Live 4 **and** Oppo A3x. Oppo is mandatory here: Backdrop renders nothing below Android 12, so Live 4 only ever exercises the fallback path. · **Room schema change:** none

**Goal:** Find out, cheaply and with real measurements, whether the app's UI layer should be rewritten in Jetpack Compose with Kyant0's Backdrop glass. Deliver a yes / no / conditional answer backed by numbers.

**Architecture:** This pilot is step 1 of "direction B": rewrite the UI layer in Compose inside the existing repo, and keep `data/`, `domain/`, `di/` and the ViewModels as they are (about 18k lines that do not depend on Views). Compose runs next to the existing View/XML code in module `app`, and no existing View code is modified. Two artefacts answer two different questions:
1. **Screen 1.1 (`ThemeSettingsActivity`) moved to Compose and shipped.** Can the toolchain, design tokens, interop, insets, Activity results and recreation all work in production code, and what do they cost in APK size and screen-open time?
2. **A debug-only Home probe.** Do Backdrop glass over the real wallpaper and a paged icon grid in Compose run smoothly on our two devices, compared with today's View Home?

Design tokens stay in XML as the single source of truth. Compose reads them through `colorResource` / `dimensionResource` / `Font(R.font…)`.

**Tech Stack:**
- Kotlin Gradle Plugin **2.2.10 → 2.4.10**, set through the root `buildscript` classpath. That is the method AGENTS.md documents for AGP 9 built-in Kotlin.
  - Why: Backdrop 2.0.1 is compiled with Kotlin 2.4.10, and a Kotlin compiler only reads metadata up to one version newer than itself (read in the Kotlin docs, not yet measured in this project).
- Compose compiler plugin `org.jetbrains.kotlin.plugin.compose`, at the same version as KGP.
- `androidx.compose:compose-bom:2026.09.00`, which gives ui, foundation and runtime 1.12.1. Also `androidx.activity:activity-compose:1.13.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`, and `ui-tooling-preview` / `ui-tooling` (the latter debug only). **No Compose Material3.**
  - Read on 2026-11-19 from the `maven-metadata.xml` files on `dl.google.com`.
- `io.github.kyant0:backdrop:2.0.1`
  - License: Apache-2.0. Compose only. `minSdk 21`. Effects work from Android 12; `lens` needs Android 13.
  - Depends on `io.github.kyant0:shapes:1.2.1`.
  - Read on 2026-11-19 from the GitHub releases page and from `backdrop/build.gradle.kts` on branch `kmp`. Maven Central returned HTTP 429, so Task 1 re-confirms these values.

**Spec:**
- The project owner's decisions on 2026-11-19:
  - Move the UI from XML to Compose using direction B, not a fresh app.
  - Use https://github.com/Kyant0/AndroidLiquidGlass for liquid glass.
- Screen 1.1 is Figma `302:1738`, already built in `RS-settings-reskin.md` Task 6.
- `LiquidGlass-Android.md` §1: the author of the current QWEA0 library recommends Kyant0 for Compose apps.

---

## Why this design

### What we are deciding
Direction B is a 12–17 week commitment (roadmap at the end). Most of that time and risk sits in Home: `ui/home` is 12.9k lines, with drag-and-drop, folders, the dock and glass. Before committing, we need answers to four questions that only a build or a device can give:

| # | Question | Answered by |
|---|---|---|
| U1 | Does AGP 9.3.3 + KGP 2.4.10 + KSP 2.3.11 + Hilt 2.60.1 still build and generate code? | Task 1 |
| U2 | Can Compose reproduce a DS3 screen pixel-for-pixel, with the existing glass Views embedded, insets, the Home-role dialog and recreation all working? | Task 2 |
| U3 | What does Compose + Backdrop add to APK size and to screen-open time on Live 4? | Task 2 + Task 4 |
| U4 | Is Backdrop glass over the real wallpaper, on top of a paging icon grid, at least as smooth as today's View Home with QWEA0 glass? | Task 3 + Task 4 |

### Constraints that shape it
- **Backdrop is Compose only.** Version 2.0.0 removed its Android View effects.
  - Glass can only be applied to Compose nodes, and it only refracts content drawn inside the same recorded Compose layer (`layerBackdrop`).
  - Home's glass therefore cannot move to Backdrop until Home itself is Compose. That is the core argument for direction B over a screen-by-screen migration that leaves Home for last.
- **Backdrop does nothing below Android 12.** The docs describe no fallback; we infer the effects are simply skipped (not yet verified).
  - QWEA0 today gives API 24–30 devices a CPU blur.
  - Live 4, our main test device, is Android 11, so every glass surface needs an explicit fallback.
- **Previous decisions:** B1 (M1) and DS3 (option C) recorded "no Compose". This pilot reverses that, but only for its own scope (Q1).
- **No automated tests** (project rule). Every risk below is covered by a manual scenario with a named device.

### Options considered for the pilot

| Option | What | For | Against | Pick |
|---|---|---|---|---|
| A | Screen 1.1 in Compose with Backdrop cards, plus a debug Home probe | Answers U1–U4. The shipped change is one self-contained Activity. The probe never ships. | Probe has no real drag-and-drop, folders or widgets | **Yes** |
| B | Screen 1.1 only | Smallest | Leaves U4 (the biggest risk) unanswered | No |
| C | Swap QWEA0 for Backdrop on the real dock / folders / search pill through `ComposeView` islands | Real glass on real Home | Each island must re-draw the wallpaper as its own backdrop; touches production Home and gestures; throwaway work under direction B | No |
| D | `SettingsAdapter` → `LazyColumn` (all 20 settings screens) | Big payoff | 759 lines plus a shared decoration; hard to back out | Later (plan JS) |

### Accepted trade-offs
- The release APK carries the Compose runtime and Backdrop even though only one screen uses them.
- For a while two theme cards coexist: `ThemeCardView` in onboarding 0.2, and the Compose `ThemeCard` in screen 1.1.
- The probe does not answer the questions about drag-to-reorder, folders or widgets. Plan JH measures those.

---

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | DS tokens live only in XML; Compose reads them with `*Resource()` | One source of truth while both UI stacks coexist |
| D2 | `DsNavBar`, `DsButton`, `DsBadgeView` and `TextClock` are embedded with `AndroidView` | Keeps proven visuals; porting them to Compose belongs to plan JD |
| D3 | The theme card is rewritten in Compose | `drawBackdrop` cannot be applied to an `AndroidView` |
| D4 | One glass modifier `Modifier.dsGlass(backdrop, surface, corner)` for every surface | The same modifier serves the 1.1 cards, probe dock and probe pill, and becomes the seed of the Compose design system |
| D5 | Glass tiers by API level: ≥ 33 `vibrancy` → `blur` → `lens`; 31–32 `vibrancy` → `blur`; < 31 draw `DsSurfaceDrawable` (today's look) | Matches Backdrop's documented API levels; Live 4 keeps the current look |
| D6 | Corner radius comes from `DsCorners.radiusPx(...)` | Glass keeps following the user's "Corner radius" glass setting (DS3 behaviour) |
| D7 | The backdrop source is drawn inside the Compose tree: the aurora drawable on screen 1.1, the wallpaper bitmap in the probe | Backdrop only sees what is drawn inside the recorded layer, and the window background is outside it |
| D8 | No change to `ThemeSettingsViewModel`, `DefaultLauncherAction`, `ThemeCatalog`, `ThemeInfo`, or any repository | The pilot replaces rendering only. This also tests direction B's claim that the logic layers can be reused unchanged |
| D9 | New code goes in dedicated `…/compose/` packages; existing View code is not edited | Backing out is one branch delete; plan JX removes the old Views later |
| D10 | No Compose Material3; text uses `BasicText` | The DS is custom; this avoids extra size |

## Questions for the project owner

| # | Question | Recommendation | Answer |
|---|---|---|---|
| Q1 | Reverse B1 ("no Compose") for the scope of JC? | Yes, for JC only. B1 stays in force for production Home until plan JH | Approved as recommended (2026-11-19) |
| Q2 | Go criteria for direction B? | All of: release APK grows ≤ 2 MB. Live 4 first open of 1.1 after process start is ≤ 100 ms slower than the XML version. 1.1 looks identical to the XML version on Live 4. Scrolling 1.1 on Oppo has Janky frames ≤ XML + 5 percentage points. Paging the probe has Janky frames ≤ real Home + 5 points on both devices | Approved as recommended (2026-11-19) |
| Q3 | Separate branch? | Yes. Owner creates `feat/compose-pilot` before Task 1 | Approved as recommended (2026-11-19) |
| Q4 | Delete `res/layout/activity_theme_settings.xml` once unused? | Yes, at close-out after review; keep it until then for comparison | Approved as recommended (2026-11-19) |
| Q5 | Android 8–11 (including Live 4) get no real Backdrop glass, only the static DS surface. Accept? | Accept for Settings, which is already static DS today. Re-decide for Home in plan JH, since QWEA0 gives those devices a CPU blur today | Approved as recommended (2026-11-19) |
| Q6 | Replace QWEA0 with Backdrop everywhere? | Yes, but inside plan JH (Home in Compose). Remove QWEA0 and JitPack in plan JX | Approved as recommended (2026-11-19) |
| Q7 | KGP 2.4.10 affects every Kotlin file. Accept? | Yes. Task 1 stops and reports if the build or Hilt code generation breaks. Do not downgrade Backdrop to 1.x without asking | Approved as recommended (2026-11-19) |
| Q8 | Allow an exception to "no tests" for unit tests on `LayoutReconciler` and `HomeViewModel` before plan JH? | Yes: pure logic, JVM only, and they guard users' saved icon layouts. Not needed for JC; answer before JH is written | Approved as recommended (2026-11-19) |

## Out of scope

- Every later step of direction B (roadmap).
- Connecting Backdrop parameters to the "Liquid Glass" settings sliders (`GlassTuning`) beyond corner radius. That is plan JD.
- Real drag-and-drop, folders, widgets, edit mode and layout persistence in the probe. That is plan JH.
- Onboarding 0.2 keeps `ThemeCardView`.

## File map

| File | Change | Role |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Versions, libraries, Compose plugin, Backdrop |
| `build.gradle.kts` (root) | Modify | `buildscript` KGP classpath; `alias(libs.plugins.kotlin.compose) apply false` |
| `app/build.gradle.kts` | Modify | Compose plugin, `buildFeatures.compose`, dependencies |
| `app/src/main/java/gambi/global/os26launcher/core/designsystem/compose/DsText.kt` | Create | `DsTextStyles` |
| `app/src/main/java/gambi/global/os26launcher/core/designsystem/compose/DsViews.kt` | Create | `AndroidView` wrappers for DS Views |
| `app/src/main/java/gambi/global/os26launcher/core/designsystem/compose/DsAuroraBackground.kt` | Create | `Modifier.dsAurora()` |
| `app/src/main/java/gambi/global/os26launcher/core/glass/compose/DsGlass.kt` | Create | `Modifier.dsGlass(...)`, API-tiered |
| `app/src/main/java/gambi/global/os26launcher/ui/settings/ThemeSettingsContent.kt` | Create | Screen 1.1 UI + `ThemeCard` |
| `app/src/main/java/gambi/global/os26launcher/ui/settings/ThemeSettingsActivity.kt` | Modify | `setContent` replaces ViewBinding |
| `app/src/debug/java/gambi/global/os26launcher/debug/ComposeHomeProbeActivity.kt` | Create | Home probe (debug only) |
| `app/src/debug/AndroidManifest.xml` | Modify | Declare the probe |
| `docs/knowledgements/JC-compose-pilot.md` | Create | Decision report (Task 4) |
| `docs/Launcher-Features.md`, `docs/LiquidGlass-Android.md`, `docs/AGENTS.md`, `docs/lessons/glass.md`, `docs/lessons/design-system.md` | Modify | Close-out (Workflow step 7), depending on the outcome |

## Global Constraints

- `minSdk 26`, `compileSdk` / `targetSdk 37`, Java 11 — unchanged.
- KGP = Compose compiler plugin = `2.4.10`. KSP stays `2.3.11` (versioned independently of Kotlin since 2.3.0). Hilt stays `2.60.1`.
- Never guess a version; read it from Maven metadata or `buildEnvironment` first (AGENTS.md).
- No new user-visible strings, so no work in the 9 translation files. Reuse `settings_hub_theme`, `settings_theme_subtitle`, `settings_theme_apply`, `settings_theme_apply_default`, `settings_theme_note`, `onboarding_badge_new`. Probe text is debug only and may be hard-coded.
- No test libraries and no automated tests (Q8 is for later).
- ViewBinding stays enabled. QWEA0 `liquidglass` and every other screen stay untouched.
- Screen 1.1 behaviour must match the KDoc at the top of today's `ThemeSettingsActivity.kt` exactly.
- Never mask build errors with flags such as `android.disallowKotlinSourceSets=false`.

## Review Focus

The inputs and conditions most likely to break for a real user that a normal walkthrough would miss. Each has a scenario in a Task.

1. **Android < 12 (Live 4):** glass surfaces must fall back to `DsSurfaceDrawable`. No transparent card, no stray border, no crash. → Task 2 (a), Task 3 (c)
2. **Recreation** (rotation, in-app language change, "Don't keep activities"): the chosen theme and `waitingForRole` survive, and the glass redraws correctly. Backdrop 1.0.4 shipped a "drawing may not update" fix, so this has failed before. → Task 2 (c), (n)
3. **Double-tapping Apply:** exactly one theme write; the button is disabled while `applying`. → Task 2 (f)
4. **Largest system font on a narrow screen:** cards are not clipped, content scrolls, the button stays pinned, and the glass tracks scrolling without lag on Oppo. → Task 2 (g), (m)
5. **R8 release build:** screen 1.1 opens on both devices and glass still renders on Oppo. The probe must not exist in release. → Task 3 Step 6, Task 4 Step 5

---

### Task 1: Toolchain — KGP 2.4.10, Compose, Backdrop

**Manual check required:** no (build and file measurements only). This Task answers U1. If it fails, the whole pilot stops here.
**Files:** `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
**Interfaces — Produces:** `libs.plugins.kotlin.compose`; `libs.androidx.compose.bom`, `libs.androidx.compose.ui`, `libs.androidx.compose.foundation`, `libs.androidx.compose.ui.tooling.preview`, `libs.androidx.compose.ui.tooling`, `libs.androidx.activity.compose`, `libs.androidx.lifecycle.runtime.compose`, `libs.kyant.backdrop`.

- [x] **Step 1: Confirm versions.**
  - Read `https://repo1.maven.org/maven2/io/github/kyant0/backdrop/maven-metadata.xml`, then that release's `.module` file, to get the Kotlin and Compose versions it depends on.
  - Read the current KGP: `.\gradlew.bat buildEnvironment | Select-String -Pattern 'kotlin-gradle-plugin|symbol-processing'` (expect 2.2.10).
  - If any value differs from this plan, use the measured value and log it.
- [x] **Step 2: Baseline size.** Run `.\gradlew.bat assembleRelease`, then `Get-ChildItem app\build\outputs\apk\release\*.apk | Select-Object Name, Length`, and log the size.
- [x] **Step 3: Bump KGP alone first.**
  - Root `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10") } }`, then `.\gradlew.bat assembleDebug`.
  - Pass: the build is green and `Get-ChildItem -Recurse app\build\generated -Include "Hilt_*.java" | Measure-Object` counts > 0.
  - Fail: stop, log the exact error, report (Q7).
- [x] **Step 4: Catalog entries.**
  - Versions: `kotlin = "2.4.10"`, `composeBom = "2026.09.00"`, `activityCompose = "1.13.0"`, `backdrop = "2.0.1"`.
  - Libraries from the Interfaces block. ui, foundation, ui-tooling-preview and ui-tooling take their versions from the BOM. `lifecycle-runtime-compose` uses `version.ref = "lifecycle"`. `kyant-backdrop = { group = "io.github.kyant0", name = "backdrop", version.ref = "backdrop" }`.
  - Plugin `kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }`.
  - Point the Step 3 classpath at `libs.versions.kotlin`.
- [x] **Step 5: Apply.**
  - Root: `alias(libs.plugins.kotlin.compose) apply false`.
  - App: `alias(libs.plugins.kotlin.compose)`, `buildFeatures { compose = true }`, `implementation(platform(libs.androidx.compose.bom))` plus the `implementation` entries, and `debugImplementation(libs.androidx.compose.ui.tooling)`.
- [x] **Step 6: Build and inspect the dependency tree.**
  - `.\gradlew.bat assembleDebug` is green.
  - In `.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath | Select-String -Pattern 'compose|kyant'`, every `androidx.compose.*` artifact resolves to a single 1.12.x version.
  - Log any `org.jetbrains.compose.*` artifacts that Backdrop pulls in.
- [x] **Step 7: Size after the change** (same commands as Step 2). Expect almost no growth, because R8 strips unused Compose; the real number comes in Task 4.
- [x] **Step 8: Report:** files changed, measured versions, both sizes, Step 3 and Step 6 results.

### Task 2: Screen 1.1 in Compose with Backdrop cards

**Manual check required:** yes. This is the first Compose screen and the first time Backdrop runs on our devices. Only real hardware shows whether it matches the XML version and whether the glass renders correctly (`Workflow.md` §3). Answers U2 and part of U3.
**Files:** create `DsText.kt`, `DsViews.kt`, `DsAuroraBackground.kt`, `DsGlass.kt`, `ThemeSettingsContent.kt`; modify `ThemeSettingsActivity.kt` (paths in the File map).
**Interfaces:**
- Consumes:
  - Task 1.
  - `ThemeSettingsViewModel.theme: StateFlow<ThemeId>`, `.applying: StateFlow<Boolean>`, `.choose(ThemeId)`, `.apply(then: () -> Unit)`.
  - `ThemeCatalog.list: List<ThemeInfo>`, where `ThemeInfo(id: ThemeId, @StringRes name, @StringRes description, @DrawableRes preview, isNew)`.
  - DS Views: `DsNavBar.title` and `.setOnBackClickListener`; `DsButton.variant`, `.size`, `setText`, `isEnabled`; `DsBadgeView` (type `new_item`).
  - `DsSurfaceDrawable(context, surface, corner)`, `DsCorners.radiusPx(context, corner, w, h)`, `AuroraBackgroundDrawable(context)`.
  - Backdrop: `rememberLayerBackdrop()`, `Modifier.layerBackdrop(backdrop)`, `Modifier.drawBackdrop(backdrop, shape, effects, onDrawSurface)`, and the effects `vibrancy()`, `blur(radius)`, `lens(refractionHeight, refractionAmount)`.
- Produces:
  - `object DsTextStyles { val body: TextStyle; val bodyStrong: TextStyle; val caption: TextStyle }`. Values from `ds_type.xml`:
    - `body`: `be_vietnam_pro_regular`, 14.sp, lineHeight 17.sp.
    - `bodyStrong`: `be_vietnam_pro_semibold`, 14.sp / 17.sp.
    - `caption`: regular, 12.sp / 14.sp.
    - No colours.
  - `@Composable fun DsNavBarView(title: String, onBack: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun DsButtonView(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, variant: DsButton.Variant = DsButton.Variant.PRIMARY, size: DsButton.Size = DsButton.Size.L)`
  - `@Composable fun DsBadgeNewView(modifier: Modifier = Modifier)`
  - `fun Modifier.dsAurora(): Modifier`
  - `fun Modifier.dsGlass(backdrop: Backdrop, surface: DsSurface, corner: DsCorner): Modifier`
  - `@Composable fun ThemeSettingsContent(chosen: ThemeId, applying: Boolean, isDefault: Boolean, onChoose: (ThemeId) -> Unit, onApply: () -> Unit, onBack: () -> Unit)`

- [ ] **Step 1: Reference screenshots.** Before changing anything, install the current build on both devices. Capture screen 1.1 twice: once while the app is not the default launcher and once while it is. Store the images outside the repo.
- [x] **Step 2: `DsText.kt` and `DsViews.kt`.** In each wrapper:
  - `factory` creates the View and attaches listeners once.
  - Listeners call the latest lambda through `rememberUpdatedState`.
  - `update` sets title or text, `isEnabled`, `variant` and `size`.
- [x] **Step 3: `DsAuroraBackground.kt`.** Remember one `AuroraBackgroundDrawable` per `LocalContext`. In `drawBehind`, set its bounds to the node size and draw it on `drawContext.canvas.nativeCanvas`.
- [x] **Step 4: `DsGlass.kt`.**
  - Radius: measure the node size, then compute `radiusPx = DsCorners.radiusPx(context, corner, w, h)`.
  - API ≥ 31:
    - `drawBackdrop(backdrop, shape = { RoundedCornerShape(radiusPx) }, effects = { vibrancy(); blur(16.dp.toPx()); if (SDK_INT >= 33) lens(min(12.dp.toPx(), radiusPx), 24.dp.toPx()) })`.
    - In `onDrawSurface`, draw the fill and stroke that `DsSurfaceDrawable` uses for `surface`, reading the same colour tokens.
    - Effect order follows the docs: colour → blur → lens. The library requires lens height ≤ corner radius.
  - API < 31: `drawBehind` with `DsSurfaceDrawable(context, surface, corner)`, drawn as in Step 3. Backdrop is never called.
  - 16 / 12 / 24 dp are starting values. Tune them on Oppo against the Step 1 screenshots and log the final numbers.
- [x] **Step 5: `ThemeSettingsContent.kt`.** Layout mirrors `activity_theme_settings.xml`.
  - Root `Box(fillMaxSize)`:
    - Layer 1: `Box(Modifier.fillMaxSize().dsAurora().layerBackdrop(backdrop))`, where `backdrop = rememberLayerBackdrop()`.
    - Layer 2: a `Column` with `windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))`.
  - The `Column` contains, top to bottom:
    - `DsNavBarView(stringResource(R.string.settings_hub_theme), onBack)`.
    - A scrolling `Column(Modifier.weight(1f).verticalScroll(...))`: horizontal padding `ds_screen_margin_h`, top `ds_space_8`, bottom `ds_space_16`. Inside it:
      - Subtitle `settings_theme_subtitle` (`body`, `ds_text_secondary`).
      - Two card rows, with `ds_space_20` above the first and `ds_space_12` above the second. Each row holds two `weight(1f)` cards, `ds_space_12` apart.
    - A pinned footer: horizontal padding `ds_screen_margin_h`, vertical `ds_space_16`. Inside it:
      - `DsButtonView(text = stringResource(if (isDefault) R.string.settings_theme_apply else R.string.settings_theme_apply_default), enabled = !applying, onClick = onApply)`.
      - When `!isDefault`: `settings_theme_note` (`caption`, `ds_text_secondary`, centred, `ds_space_8` above).
  - `private fun ThemeCard(info: ThemeInfo, chosen: Boolean, backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier)`, mirroring `view_onboarding_theme_card.xml`:
    - Surface: `dsGlass(backdrop, DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))`, padding 8/8/8/10 dp.
    - Preview: image `info.preview`, aspect ratio 142:150 (width:height), 12 dp corners, `ContentScale.Crop`.
    - Clock: `TextClock` via `AndroidView`, 28sp, `#EBF5F7FF`, top-centred, 21 dp from the top.
    - Check mark when `chosen`: 24 dp `onb_check_circle` with a 14 dp `onb_ic_check`, top-end, 8 dp inset.
    - Name row: 10 dp above, `ds_space_4` horizontal padding. Name in `bodyStrong`, `ds_text_primary`. `DsBadgeNewView` 6 dp after it when `isNew`.
    - Description: 2 dp above, `caption`, `ds_text_secondary`.
    - Accessibility: `Modifier.selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)` and the content description "<name>, <description>", as in `ThemeCardView.bind`.
  - One `@Preview`.
- [x] **Step 6: `ThemeSettingsActivity.kt`.**
  - Remove: `binding` and `ActivityThemeSettingsBinding`.
  - Keep, unchanged:
    - `WindowCompat.setDecorFitsSystemWindows(window, false)`.
    - `reassertEdgeToEdge(window)`, now called after `setContent`. This guards the EF bug on Tecno, Android 15.
    - `DefaultLauncherAction`, still registered in `onCreate`.
    - `waitingForRole` with `KEY_WAITING`, `onResume`, and `onApply()`.
  - Add `private var isDefault by mutableStateOf(false)`, assigned in `onResume` in place of `setText` / `note.isVisible`.
  - Content: `setContent { ThemeSettingsContent(chosen = viewModel.theme.collectAsStateWithLifecycle().value, applying = viewModel.applying.collectAsStateWithLifecycle().value, isDefault = isDefault, onChoose = viewModel::choose, onApply = ::onApply, onBack = ::finish) }`.
  - Add one KDoc line: "JC: UI in `ThemeSettingsContent`; Backdrop glass from Android 12."
- [x] **Step 7: Build and lint.** Run `.\gradlew.bat assembleDebug` and `.\gradlew.bat lintDebug`.
  - `NewApi` must be clean in `DsGlass.kt`.
  - Expected new warning: `UnusedResources` for `activity_theme_settings.xml` (see Q4).
- [ ] **Step 8: Manual scenarios.**
  - **Live 4:**
    - (a) Settings → 1.1 matches the Step 1 screenshot: margins, type, corners and check position all identical, and the cards are static DS surfaces (RF1).
    - (b) Tapping each card moves the check, but nothing is saved yet.
    - (c) Rotate, then change the app language: the chosen card persists (RF2).
    - (d) Not yet default: the button reads "Apply & set as default" and the note is visible. Tap → role dialog → pick this app → the screen closes and Home shows the new theme.
    - (e) Already default: the button reads "Apply", the note is hidden, and a tap closes the screen.
    - (f) Double-tap the button fast: the theme is written once (RF3).
    - (g) Largest font: the content scrolls and the button stays pinned (RF4).
    - (h) Gesture navigation and 3-button navigation: nothing sits under the system bars.
    - (i) Back from the nav bar and the back gesture both return to the Settings hub.
    - (j) TalkBack announces the chosen card as selected.
    - (k) Not yet default: leave the role dialog for the system "Default apps" screen, pick this app there, press Back. Screen 1.1 closes itself.
  - **Oppo:**
    - (l) The cards show Backdrop glass: blurred aurora with edge refraction, no black rim, no flash on open (RF1 counterpart).
    - (m) Scroll up and down: the glass tracks the cards with no one-frame lag (RF4).
    - (n) Rotate and come back: the glass redraws correctly (RF2).
    - (o) Change the "Corner radius" slider in Liquid Glass settings, reopen 1.1: the card corners follow (D6).

### Task 3: Debug-only Home probe

**Manual check required:** yes. It is a performance measurement that decides direction B, and it adds a new touch/drag gesture (`Workflow.md` §3). Answers U4.
**Files:** create `app/src/debug/java/gambi/global/os26launcher/debug/ComposeHomeProbeActivity.kt`; modify `app/src/debug/AndroidManifest.xml`.
**Interfaces:**
- Consumes:
  - Task 2: `dsGlass`, `DsTextStyles`.
  - `BackdropRepository`: `.awaitWallpaperConfig()`, `.loadBackdrop(reqW, reqH): Bitmap?`, `.loadDefaultBackdrop(reqW, reqH): Bitmap?`.
  - `AppRepository`: `.apps: Flow<List<AppItem>>`, `.icon(key: ComponentKey, sizePx: Int): Bitmap?`, `.launch(key, source = null)`.
  - Backdrop: `rememberLayerBackdrop`, `layerBackdrop`.
- Produces: nothing. The probe is throwaway and nothing depends on it.

- [x] **Step 1: Activity.**
  - `@AndroidEntryPoint class ComposeHomeProbeActivity : ComponentActivity()`, injecting both repositories.
  - Debug manifest entry: `exported="true"`, `android:permission="android.permission.DUMP"` (same pattern as `AwProbeActivity`), using the app theme.
  - Launch: `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.ComposeHomeProbeActivity`.
- [x] **Step 2: Wallpaper.**
  - Call `awaitWallpaperConfig()`, then load `loadBackdrop(w, h) ?: loadDefaultBackdrop(w, h)` at screen size.
  - Draw it as `Image(contentScale = ContentScale.Crop)` inside a `Box` with `layerBackdrop(backdrop)`.
  - If neither image is available, fill `ds_bg_base` and log that.
- [x] **Step 3: Grid and pager.**
  - `HorizontalPager` with 3 pages, each a 4 × 6 grid filled in order from `apps`; empty cells when apps run out.
  - Cell: 60 dp icon loaded with `icon(key, px)` inside `produceState` and remembered per key, plus a one-line `caption` label in `ds_text_primary`.
  - Tap launches the app.
- [x] **Step 4: Dock and search pill.**
  - Dock: the first 4 apps, 96 dp tall, 8 dp above the navigation inset, with `dsGlass(backdrop, DsSurface.CARD, DsCorner.Role(DsCornerRole.CARD))`.
  - Pill: 120 × 36 dp, 12 dp above the dock, `dsGlass(backdrop, DsSurface.CARD, DsCorner.Pill)`, static text "Search".
- [x] **Step 5: Lift and drag.**
  - Long-press 400 ms on an icon: it scales to 1.1× and follows the finger (`detectDragGesturesAfterLongPress`).
  - The pager must not scroll while an icon is dragged.
  - On release the icon animates back to its cell. Nothing is saved.
- [x] **Step 6: Build.**
  - `.\gradlew.bat assembleDebug` is green.
  - `.\gradlew.bat assembleRelease` is green, and Analyze APK shows no `ComposeHomeProbeActivity` class in release (RF5).
- [ ] **Step 7: Manual scenarios (both devices).**
  - (a) The wallpaper is the one real Home uses.
  - (b) Page left and right: returning to a page already seen shows no blank icons for more than one frame.
  - (c) Oppo: dock and pill refract the wallpaper and the grid moving beneath them. Live 4: dock and pill are static DS surfaces (RF1).
  - (d) Lift-and-drag never triggers paging, and the icon returns to its cell.
  - (e) Rotation does not crash (the landscape layout need not look good).

### Task 4: Measure and write the decision report

**Manual check required:** yes. These measurements choose the direction (`Workflow.md` §3).
**Files:** create `docs/knowledgements/JC-compose-pilot.md`; update the results table in this plan.
**Interfaces — Consumes:** the Task 2–3 build, plus an XML build from the commit before Task 1.

- [x] **Step 1: Size.** `assembleRelease` for both builds. Record the APK size and dex method count (Android Studio → Analyze APK). Split Backdrop (`com.kyant.*`) from Compose where possible.
- [x] **Step 2: Open time.**
  - Release builds, both devices, 10 runs each.
  - Scenario 1: first open of 1.1 after `adb shell am force-stop gambi.global.os26launcher`.
  - Scenario 2: second open within the same process.
  - Source: `adb logcat -d | Select-String "Displayed gambi.global.os26launcher/.ui.settings.ThemeSettingsActivity"`. Record the medians.
- [ ] **Step 3: Scroll smoothness of 1.1.**
  - Largest font, scroll up and down 10 times.
  - Run `adb shell dumpsys gfxinfo gambi.global.os26launcher reset` before and `adb shell dumpsys gfxinfo gambi.global.os26launcher` after.
  - Record `Janky frames` and the 90th percentile for both builds on both devices.
- [x] **Step 4: Probe vs real Home.**
  - Debug builds, both devices. Page back and forth 20 times on each, with the same gfxinfo reset and read as Step 3.
  - Record `Janky frames`, 50th and 90th percentile.
  - Label the glass in the table: real Home uses QWEA0, the probe uses Backdrop.
  - One extra probe run on Oppo during lift-and-drag.
- [x] **Step 5: R8 release check (RF5).** Repeat Task 2 scenarios (a), (d), (e), (l) on the release APK.
- [x] **Step 6: Knowledgement**, following the `Workflow.md` §6 template:
  - Concepts in this project's terms: Compose, `AndroidView`, Backdrop.
  - A `sequenceDiagram` of `setContent` → `collectAsStateWithLifecycle` → `layerBackdrop` records the backdrop source → `drawBackdrop` renders glass.
  - A `flowchart` of the API glass tiers.
  - A results table against every Q2 criterion.
  - Backdrop vs QWEA0: API coverage, View vs Compose, license, low-API fallback.
  - A verdict for direction B: **go / no-go / conditional go** (name the condition), plus a re-estimate of JD–JX from the measurements.
- [x] **Step 7: Report** to the owner for the decision.

---

## Results (filled in during Task 4)

| Metric | Device | XML / real Home | Compose / probe | Q2 limit | Pass |
|---|---|---|---|---|---|
| Release APK size | — | 7,344,641 B | 8,015,762 B (+671,121 B ≈ +655 KiB) | ≤ +2 MB | **Pass** |
| Dex method_ids / class_defs | — | 18,998 / 2,911 | 25,520 / 4,150 | (record) | — |
| 1.1 first open (median of 10), fresh install | Samsung SM-A176B | 138 ms | 272 ms (+134) | ≤ +100 ms | **Fail** (Samsung) |
| 1.1 first open (median of 10), after `speed-profile` | Samsung SM-A176B | 127.5 ms | 170.5 ms (+43) | ≤ +100 ms | **Pass** (Samsung) |
| 1.1 second open (median of 10), fresh / profiled | Samsung SM-A176B | 113.5 / 113 ms | 140.5 / 112.5 ms | (record) | — |
| 1.1 first open (median) | Live 4 | | | ≤ +100 ms | not measured |
| 1.1 first open (median) | Oppo | | | (record) | not measured |
| 1.1 scroll, Janky frames | Samsung | — | — | ≤ +5 pts | n/a: content fits the screen, no scroll |
| 1.1 scroll, Janky frames | Oppo | | | ≤ +5 pts | not measured |
| Paging ×20, Janky frames (3 runs, debug builds) | Samsung SM-A176B | 4.46 / 4.78 / 4.92 % | 3.14 / 4.77 / 4.83 % | ≤ +5 pts | **Pass** (Samsung) |
| Paging ×20, 95th percentile | Samsung SM-A176B | 34 ms | 53–77 ms | (record) | worse tail |
| Paging, Janky frames | Live 4 | | | ≤ +5 pts | not measured |
| Paging, Janky frames | Oppo | | | ≤ +5 pts | not measured |
| Visual match of 1.1 | Samsung SM-A176B | — | within ~3 px; regular text bolder under system "Bold text" | identical | **Pass with note** |
| Visual match of 1.1 | Live 4 | — | | identical | not measured |

Verdict: **conditional go** for direction B — conditions and re-estimate (13–18 weeks) in `docs/knowledgements/JC-compose-pilot.md` §6.

## Full feature check (assembled at Workflow step 5)

Devices: Live 4, then Oppo.
Data to prepare:
- Run once with the app not set as default launcher, then once with it set.
- Real Home has at least 3 pages of icons and a user-picked wallpaper.

Order: Task 2 Step 8, then Task 3 Step 7, then Task 4 Step 5 on release.
Regression checks:
- Onboarding 0.2 (still uses `ThemeCardView`).
- The Settings hub opening the other screens.
- The "Current launcher" card (shares `DefaultLauncherAction`).
- QWEA0 glass on the real dock, folders and search pill on Oppo, unchanged after the Kotlin bump.

**Verification result (device, date):** Samsung SM-A176B, Android 16, 2026-11-19 (Claude over adb): Task 2 (a)(b)(e)(f)(h)(i)(l), Task 3 (a)–(d), Task 4 Steps 1, 2, 4, 5. Not yet on Live 4 or Oppo — carried as condition 1 of the verdict.

## Log

| Date | Task | Done | Notes |
|---|---|---|---|
| 2026-11-19 | — | English plan written from the direction-B reasoning (not a translation) | Awaiting approval; Q1–Q8 open |
| 2026-11-19 | — | Plan approved by owner; Q1–Q8 approved as recommended. Branch `feat/compose-pilot` created by Claude (Q3) | |
| 2026-11-19 | 1 | Step 1: Maven Central `backdrop` release = 2.0.1 (lastUpdated 2026-08-26). Its `.module`/tooling metadata: built with KGP 2.4.10 (Gradle 9.7.1), depends on `org.jetbrains.compose.{ui,foundation,ui-graphics}` 1.12.0, `kotlin-stdlib` 2.4.10, `io.github.kyant0:shapes` 1.2.1. Project KGP before change: 2.2.10 | Measured; matches plan |
| 2026-11-19 | 1 | Step 2: baseline `app-release-unsigned.apk` = 7,344,641 B | Measured |
| 2026-11-19 | 1 | Step 3: root `buildscript` classpath KGP 2.4.10 (+ `mavenCentral()` for buildscript). `assembleDebug` green; `buildEnvironment` resolves `kotlin-gradle-plugin:2.2.10 -> 2.4.10`; all 14 debug `Hilt_*.java` regenerated after the change | Measured. U1 answered: yes |
| 2026-11-19 | 1 | Steps 4–5: catalog entries, `kotlin-compose` plugin, `buildFeatures.compose = true`, Compose BOM + Backdrop deps; classpath now reads `libs.versions.kotlin` | |
| 2026-11-19 | 1 | Step 6: `assembleDebug` green. `releaseRuntimeClasspath`: all 30 `androidx.compose.*` modules resolve to 1.12.1; `kotlin-stdlib` to 2.4.10. Backdrop pulls `org.jetbrains.compose.*-android` 1.12.0, but those AARs are ~6 KB redirect shells pointing at `androidx.compose.*` (no duplicate Compose code) | Measured |
| 2026-11-19 | 1 | Step 7: `app-release-unsigned.apk` = 7,428,014 B (+83,373 B, ~+81 KiB). No `@Composable` yet, so R8 strips almost all of Compose; the real cost is measured in Task 4 | Measured |
| 2026-11-19 | 2 | Step 1 not done yet: no Live 4 / Oppo connected (only a Samsung SM-A176B over Wi-Fi, not a project device). Kept the pre-change debug APK (XML UI; already includes the Compose deps from Task 1) as a reference build for the screenshots | Owner installs it and captures before installing the Task 2 build |
| 2026-11-19 | 2 | Steps 2–6: `DsText.kt`, `DsViews.kt`, `DsAuroraBackground.kt`, `DsGlass.kt`, `ThemeSettingsContent.kt`; `ThemeSettingsActivity` uses `setContent`. `ThemeSettingsViewModel`, `DefaultLauncherAction`, `ThemeCatalog` unchanged (D8) | Two deviations below |
| 2026-11-19 | 2 | Deviation 1 (`DsGlass`): the surface layer drawn in `onDrawSurface` is the `DsSurfaceDrawable` itself (fill `ds_surface_fill` = `#330F121C`, 20% opaque, plus its edge gradients), instead of re-implementing its fill and stroke. Same look as DS3, real blur behind it, and the < API 31 fallback is the same drawable. Backdrop's own `highlight` and `shadow` are turned off so the edges match DS3 | Read from source: `drawBackdrop` with no effects still draws the raw backdrop below API 31, so the fallback skips Backdrop entirely |
| 2026-11-19 | 2 | Deviation 2 (`DsGlass`): no SDK check around `lens`; Backdrop already gates it (`isRuntimeShaderSupported()` = API 33, `blur` = API 31). The shape must be a `CornerBasedShape` (lens throws otherwise), so it is `RoundedCornerShape(radiusPx)` with the radius from `DsCorners.radiusPx` at the measured size | Read from source |
| 2026-11-19 | 2 | Step 7: `assembleDebug` green, `lintDebug` green. Only new finding: `UnusedResources` for `activity_theme_settings.xml` (Q4). `NewApi` clean | Measured |
| 2026-11-19 | 2 | Step 8 (partial, by Claude over adb at the owner's request) on **Samsung SM-A176B, Android 16 / API 36** — not a project device; font scale 0.9 and "Bold text" ON (`font_weight_adjustment` = 300). App was already HOME holder, so only the "already default" state was testable. Step 1 reference screenshot taken from the pre-change APK on the same device | Screenshots kept in the session scratchpad (`jc/`) |
| 2026-11-19 | 2 | **Bug found & fixed — glass never rendered:** `.dsAurora().layerBackdrop(backdrop)` recorded an empty layer, because `LayerBackdropNode.draw()` records only `drawContent()` (what comes *after* it in the chain). Fixed to `.layerBackdrop(backdrop).dsAurora()`. Proven with a temporary `colorControls(brightness = 0.5f)`: no change before the fix, cards clearly brighter after; temp code removed | Read from Backdrop source + measured. Same rule applies to the Task 3 probe |
| 2026-11-19 | 2 | **Bug found & fixed — square glass corners:** the `shape`/`effects` lambdas captured `radiusPx` from the first composition (0 before measuring) and the library node kept those lambdas. Radius now lives in a `mutableFloatStateOf` that both lambdas read at draw time | Measured (brightness test showed square corners, then rounded) |
| 2026-11-19 | 2 | **Text fixes in `DsTextStyles`:** (1) full 4-weight family + explicit `fontWeight` per style (single-file families could not get bolder under "Bold text"); (2) no `lineHeight` + `includeFontPadding = true`, because the XML `TextView`s actually render the font's natural line spacing (51 px per subtitle line vs 43 px = 17sp × 0.9), not the 17sp/14sp in `ds_type.xml`. Result: line positions within ~3 px of the XML screen | Measured. Side finding: `lineHeight` in `ds_type.xml` has no effect on these XML screens — out of scope, worth a look in JD |
| 2026-11-19 | 2 | **A11y fix:** removed the extra `semantics { contentDescription }` on `ThemeCard` (it produced a second node over the radio node); `selectable` merges the child texts instead | Measured with `uiautomator dump` |
| 2026-11-19 | 2 | Scenario results on Samsung: (a) layout matches the XML screenshot except regular-weight text is bolder under "Bold text" (Compose bolds every weight, the XML screen only bolds semibold) — **pass with note**; (b) card tap moves the check — **pass**; (e) already default: "Áp dụng", note hidden, tap closes — **pass**; (f) fast double tap closes once, no crash (single write not directly observable) — **pass**; (h) gesture nav, nothing under bars — **pass**; (i) nav-bar back and back key return to the hub — **pass**; (j) radio semantics `checkable/checked` correct, TalkBack not run — **partial**; (l) Backdrop renders (after fix) — **pass**, but on the smooth aurora blur and lens are not visible to the eye at the starting values; (c) not testable: screen is `nosensor` and `am kill` cannot kill the HOME process — needs "Don't keep activities"; (d), (k) not testable: app is HOME holder; (g) needs system font size change; (m) content fits, no scroll; (n) rotation locked; (o) not run | No crash in `logcat -b crash` |
| 2026-11-19 | 2 | Re-check after fixes: `assembleDebug` green, `lintDebug` green, only `UnusedResources` for `activity_theme_settings.xml` | Measured |
| 2026-11-19 | 2 | Side observation: a third-party launcher installed on the Samsung (`com.babydola.launcherios`) pops an ad dialog "Uninstall successfully! OS26 Launcher" on every `adb install -r` of our package; our app stayed installed and HOME holder | Not our bug; ignore when reinstalling on that device |
| 2026-11-19 | 2 | Owner confirmed moving on to Task 3 with Task 2 Steps 1 and 8 still open on Live 4 / Oppo (items (c), (d), (g), (k), (o), TalkBack, and the < API 31 fallback). Steps 1 and 8 stay unticked until then | Owner decision |
| 2026-11-19 | 3 | Steps 1–5: `ComposeHomeProbeActivity` (debug only, `@AndroidEntryPoint`, `exported` + `permission=DUMP`). Wallpaper `Image` and the `HorizontalPager` both live inside the `layerBackdrop` Box (with `layerBackdrop` before the content — Task 2 lesson), so dock and pill refract wallpaper *and* the moving grid. Lifted icon is drawn in a root overlay; pager `userScrollEnabled = drag == null`. Icons cached per `ComponentKey` in the Activity | Long-press delay is the system long-press timeout (`detectDragGesturesAfterLongPress`), not a hard-coded 400 ms |
| 2026-11-19 | 3 | Step 6: `assembleDebug` green; `assembleRelease` green, release APK 8,015,762 B; scanned every `classes*.dex`: release 0 files contain `ComposeHomeProbeActivity`, debug 2 (sanity). `lintDebug` first failed with `UnusedBoxWithConstraintsScope` in the probe dock → switched to `Box`; now green, only the known `UnusedResources` | Measured. RF5 (probe absent from release): pass. Release size here includes Task 2's Compose screen — Task 4 measures it properly |
| 2026-11-19 | 3 | Step 7 on Samsung SM-A176B (Android 16) over adb: (a) wallpaper = the real Home wallpaper — **pass**; (b) paging left/right works, returning pages shows cached icons — **pass** (frame-level blank check not measured); (c) dock and pill show real Backdrop blur + edge refraction of the wallpaper and of the grid behind them — **pass** on API 36, Live 4 fallback not run; (d) long-press then drag: icon lifts and follows the finger, pager does not page, icon returns to its cell on release — **pass**; (e) rotation not run (needs system auto-rotate change) | Note: `uiautomator dump` lists texts of off-screen pager pages too, so page checks must use screenshots. One early open appeared to start on page 2; a fresh open with a temporary `currentPage` log showed page 0 and could not reproduce it (log removed) |
| 2026-11-19 | 3 | Owner confirmed moving on to Task 4 (Step 7 rotation and project devices still open) | Owner decision |
| 2026-11-19 | 4 | XML baseline built from `main` (`f7005d0`) in a temporary detached worktree `../Os26Launcher-jc-xml` (+ copied `local.properties`); its unsigned release = 7,344,641 B, identical to Task 1 Step 2. Both releases zipaligned and signed with the debug keystore (RB §7); app data backed up with `run-as … tar` before the release installs and confirmed intact after reinstalling debug | Worktree to be removed after review (owner approval) |
| 2026-11-19 | 4 | Steps 1–5 measured on **Samsung SM-A176B (Android 16), not Live 4 / Oppo**; numbers in the Results table. Step 3 not measurable (1.1 content fits the screen at font scale 0.9). Step 4: first two real-Home runs were blocked by the "set as default" dialog (20 frames) and were discarded; re-run with the dialog dismissed via "Để sau". Drag run with `input motionevent` = 74 % janky, discarded as an injection artefact (~10 events/s) | Real Home opened with `am start` because the device's HOME role was One UI during the session (not changed by Claude) |
| 2026-11-19 | 4 | Step 5 (R8): Compose release opens 1.1, renders the "not default" state correctly ("Áp dụng & đặt làm mặc định" + note), no crash, no `ClassNotFound`/`NoSuchMethod`/`UnsatisfiedLink` | (d) shown but not tapped (would open the system role dialog) |
| 2026-11-19 | 4 | Step 6: knowledgement `docs/knowledgements/JC-compose-pilot.md` (Vietnamese, per the docs rule). Verdict: **conditional go** — conditions: re-measure on Live 4 and Oppo; baseline profiles for Compose screens (fresh-install first open +134 ms); release + real-finger check of the grid's slow tail (95th 53–77 ms vs 34 ms); Q5 decision. Re-estimate 13–18 weeks | |

## Review Focus evidence (filled in at close-out)

| # | Review Focus item | Evidence | Result |
|---|---|---|---|
| 1 | Android < 12 fallback | Task 2 (a), Task 3 (c) | **Open** — only API 36 tested; the < 31 branch has not run on any device. Carried to the Live 4 re-measure (knowledgement §6 condition 1) |
| 2 | Recreation | Task 2 (c), (n) | **Open** — screen is `nosensor`, `am kill` cannot kill the HOME process; needs "Don't keep activities" on a project device. ViewModel/SavedState code is unchanged from the XML version |
| 3 | Double-tap Apply | Task 2 (f) | **Pass** (Samsung): screen closes once, no crash; single write not directly observable |
| 4 | Large font, narrow screen | Task 2 (g), (m) | **Open** — needs a system font-size change; at font scale 0.9 the content fits without scrolling |
| 5 | R8 release | Task 3 Step 6, Task 4 Step 5 | **Pass**: Compose release opens 1.1 with no crash or missing class; probe absent from all release dex files |

## Shared docs updated (filled in at Workflow step 7)

- `Launcher-Features.md` §1: UI line now says View/XML plus Compose from JC (screen 1.1), direction B approved conditionally. *(done 2026-11-19)*
- `LiquidGlass-Android.md`: new §12b — Backdrop (Kyant0): API, tiers, pitfalls, comparison with QWEA0. *(done)*
- `AGENTS.md` (Build and toolchain): KGP now 2.4.10 via `buildscript`; Compose conventions (plugin = KGP, no Material3, tokens only in XML, `layerBackdrop` order). *(done)*
- `project_structure.md`: "Tệp thêm ở JC" line. *(done)*
- `lessons/glass.md`, `lessons/design-system.md`: JC lessons (knowledgement §9). *(done)*
- `room-schema.md`, `Figma-Draft.md`: no change (no schema change, no new Figma screen).

---

## Roadmap for direction B

Each step gets its own plan, written only after the previous step closes. The estimates are initial; Task 4 revises them.

| Plan | Scope | Estimate | Starts when |
|---|---|---|---|
| **JC** (this plan) | Toolchain, screen 1.1, Home probe, measurements | 1–2 weeks | Plan approved |
| **JD** | Compose design system: tokens, type, button, nav bar, badge, search field, slider, segmented control, dialog, sheet; connect `dsGlass` to `GlassTuning` | 1–2 weeks | JC meets Q2 |
| **JH** | Home in Compose as a new Activity beside the old Home (debug switch). Covers grid, pager, drag-and-drop, folders, dock, search pill, edit mode, and the widget host through `AndroidView`. Reuses `HomeViewModel` and `LayoutReconciler`. Backdrop replaces QWEA0 on Home | 6–8 weeks | JD closed; Q8 answered |
| **JS** | Remaining screens: Settings (`SettingsAdapter` → `LazyColumn`), Search, Control Center, App Library, onboarding, widget picker | 3–4 weeks | Can overlap the end of JH |
| **JX** | Switch the launcher to the new Home; keep the old Home for one release; then delete the old Views, layout XML, QWEA0 `liquidglass` and JitPack if nothing else uses it | 1 week | JH and JS pass manual checks |

Initial total: **12–17 weeks**.
