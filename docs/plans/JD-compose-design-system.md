# JD — Compose Design System (step 2 of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md` and `docs/Workflow.md` apply. One Task at a time; report after each Task and wait for the project owner's confirmation before ticking it and starting the next. No automated tests (project rule). Manual checks on the owner's Oppo PKD130 (A3x line, Android 14, 720p — the low-end device) and Samsung SM-A176B (Android 16, when the owner allows it). The owner has no Live 4 (2026-11-19), so the API < 31 fallback is checked visually on an Android Studio emulator (API 30) and never measured there. Commands are written for PowerShell.
>
> Language: English, like JC (owner's choice for the Compose-rewrite plans; see the `AGENTS.md` exception).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19
**Status:** Closed 2026-11-19 — owner approved. Open items carried to the next plan: API < 31 fallback (no device), largest font + Bold text, TalkBack, rotation.
**Devices:** Oppo PKD130 (low-end, mandatory) + Samsung SM-A176B (high-end glass, when allowed) + emulator API 30 (visual fallback only; no perf numbers) · Live 4 not available · **Room schema change:** none

**Goal:** Close JC's open conditions on real project devices, then build the Compose versions of every design-system component the later plans need, moving from pixel parity to iOS 26 fidelity where Compose can do better.

**Architecture:** Everything lives in `core/designsystem/compose/` (components) and `core/glass/compose/` (glass), next to the View design system, which stays until plan JX. Tokens stay in XML. A `DsScreen` scaffold draws the aurora inside a `layerBackdrop` and provides it through `LocalDsBackdrop`, so components get glass without threading a parameter. Glass follows the user's Liquid Glass settings (`GlassLook`) through new pure mappers in `GlassTuning`. A debug-only Compose catalog is the verification surface, and screen 1.1 drops its `AndroidView` wrappers as the first real consumer.

**Tech Stack:** Kotlin 2.4.10, Compose BOM `2026.09.00` (ui, foundation, `animation` via foundation), `io.github.kyant0:backdrop:2.0.1`. **No new dependencies**, no Compose Material3.

**Spec:**
- JC verdict and conditions: `docs/knowledgements/JC-compose-pilot.md` §6 (conditions 1–4) and the JC roadmap row for JD.
- Owner direction 2026-11-19: Compose screens should get closer to iOS 26, not just match the XML (memory `ios-fidelity-goal`).
- Component specs: the View design system in `core/designsystem/*.kt` (DS3, `docs/plans/DS3-design-system-v3.md`) and tokens in `res/values/ds_*.xml`.

---

## Why this design

- **Two goals, kept apart.** JC proved the cost on one device only. Task 1 measures the Oppo (low-end) and the Samsung **before** any fidelity work, so later numbers are not confused with visual changes. After that, components may diverge from the XML where the change is listed in "iOS fidelity changes" below and approved (Q1).
- **`DsScreen` + `LocalDsBackdrop`.** JC's `dsGlass(backdrop, …)` made every screen thread a `Backdrop` and remember the `layerBackdrop` modifier order (a real bug in JC). A scaffold owns the order once.
- **Glass driven by `GlassLook`.** Today Compose glass ignores the Refraction/Blur/Gloss sliders. Mapping them in `GlassTuning` (the existing single conversion point) keeps View and Compose glass controlled by the same settings.
- **Only components that later plans use.** The list is the View DS minus onboarding-only pieces (`DsStepIndicatorView`, `DsOnboardingTopBar` → plan JS).

| Option for dialogs and sheets | For | Against | Pick |
|---|---|---|---|
| A. Compose `Dialog` window + custom foundation sheet | No new dependency; full control of iOS spring and drag | More code (~2 components) | **Yes** |
| B. Compose Material3 `ModalBottomSheet` / `AlertDialog` | Ready-made | Adds Material3 (excluded in JC D10); Material look and motion, not iOS | No |
| C. Keep View `DsDialog` / `DsBottomSheet` from Compose | Zero work | Content would be `AndroidView`; no glass from the screen's backdrop | No |

## iOS fidelity changes (beyond parity — need Q1)

| # | Change | Where |
|---|---|---|
| F1 | Glass rim highlight on (Backdrop `Highlight.Default`), strength from the Gloss slider | `dsGlass` |
| F2 | Press feedback: scale to 0.97 and dim 8 % with a spring (`dampingRatio = 0.7`, `stiffness = 600`), release springs back | `Modifier.dsPressable`, used by buttons, chips, rows, cards |
| F3 | Toggle looks and moves like iOS: track 51 × 31 dp, thumb 27 dp, thumb springs across | `DsToggle` |
| F4 | Segmented control: selected thumb slides between options with a spring (View version jumps) | `DsSegmented` |
| F5 | Bottom sheet: drag down to dismiss, spring open/close, dim fades with the drag | `DsBottomSheet` |
| F6 | Alert dialog: scale-in 1.1 → 1.0 with fade, like iOS alerts | `DsAlertDialog` |
| F7 | Text uses the DS line heights from `ds_type.xml` (Figma values), not the font's natural spacing that XML screens show (JC finding) | `DsTextStyles` |

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Tokens stay in XML; Compose reads them with `*Resource()` | Unchanged from JC |
| D2 | `DsScreen(title, onBack, content)` draws aurora → `layerBackdrop` → insets → `DsNavBar` → content, and provides `LocalDsBackdrop` | One place owns the modifier order |
| D3 | `Modifier.dsGlass(surface, corner, backdrop = LocalDsBackdrop.current)`; JC's explicit-backdrop call sites keep working | Probe and screen 1.1 compile unchanged until migrated |
| D4 | Mappers added to `GlassTuning`; at `GlassLook.DEFAULT` they return JC's values (blur 16 dp, lens 12/24 dp) | Default look must not change |
| D5 | `rememberGlassLook()` listens to `GlassEnvironment.addListener` and returns `State<GlassLook>` | Slider changes apply without reopening the screen (fixes JC scenario (o)) |
| D6 | Under API 31 glass stays the static `DsSurfaceDrawable` | JC D5; Q5 (CPU blur) belongs to JH |
| D7 | System "Bold text" behaviour follows Compose (every weight +300) | Matches iOS Bold Text; Q3 |
| D8 | Components are stateless (`value` + `onValueChange`), like Compose foundation | Screens own state, ViewModels unchanged |
| D9 | Real Backdrop glass only on `CARD` and `SHEET`; `CONTROL` surfaces (secondary button, chip, segmented, search field, nav back pill) draw the static `DsSurfaceDrawable` | Owner decision 2026-11-19 after Task 8 measurements on the Oppo: ~15 real-glass surfaces = 38 % janky scroll, card-only = 10 % |

## Questions for the project owner

| # | Question | Recommendation | Answer |
|---|---|---|---|
| Q1 | Approve the iOS fidelity changes F1–F7? (each can be refused separately) | Approve all; screen 1.1 is the first place they show (Task 9) | Approved as recommended (2026-11-19) |
| Q2 | Dialog and sheet approach | Option A (Compose window + custom sheet) | Approved as recommended (2026-11-19) |
| Q3 | Bold-text behaviour | Follow Compose/iOS (all text bolder) | Approved as recommended (2026-11-19) |
| Q4 | If Task 1 fails the Oppo gate (first open after profile > +100 ms vs XML) | Stop JD after Task 1 and report; do not start components | Approved as recommended (2026-11-19) |
| Q5 | Device set (changed 2026-11-19: owner has no Live 4) | Oppo PKD130 for low-end numbers, Samsung for high-end glass, emulator API 30 only to look at the < 31 fallback; the risk "no real Android 8–11 device" is recorded | Owner: no Live 4; Oppo connected (2026-11-19) |

## Out of scope

- Screens other than 1.1 (plan JS); Home (plan JH); onboarding-only components (JS).
- CPU/static blur glass for Android 8–11 (JC Q5 → JH).
- Deleting View DS components (JX).

## File map

| File | Change | Role |
|---|---|---|
| `app/src/main/baseline-prof.txt` | Modify | Add `com/kyant/**` rules (Task 1) |
| `core/glass/GlassTuning.kt` | Modify | `backdropBlurDp`, `backdropLensDp`, `backdropHighlightAlpha` |
| `core/glass/compose/DsGlass.kt` | Modify | Look-driven glass, highlight, `LocalDsBackdrop` default |
| `core/glass/compose/GlassLookState.kt` | Create | `rememberGlassLook()` |
| `core/designsystem/compose/DsText.kt` | Modify | All 11 text styles |
| `core/designsystem/compose/DsScreen.kt` | Create | `DsScreen`, `LocalDsBackdrop` |
| `core/designsystem/compose/DsPress.kt` | Create | `Modifier.dsPressable` |
| `core/designsystem/compose/DsButton.kt` | Create | `DsButton` (Compose) |
| `core/designsystem/compose/DsSmall.kt` | Create | `DsChip`, `DsBadge`, `DsIconTile` |
| `core/designsystem/compose/DsNavBar.kt` | Create | `DsNavBar` (Compose) |
| `core/designsystem/compose/DsSearchField.kt` | Create | `DsSearchField` (Compose) |
| `core/designsystem/compose/DsRow.kt` | Create | `DsRow`, `DsCardGroup`, `DsToggle` |
| `core/designsystem/compose/DsSegmented.kt` | Create | `DsSegmented` |
| `core/designsystem/compose/DsSlider.kt` | Create | `DsSliderRow` (Compose) |
| `core/designsystem/compose/DsDialogs.kt` | Create | `DsAlertDialog`, `DsBottomSheet` (Compose) |
| `core/designsystem/compose/DsViews.kt` | Delete (Task 9) | `AndroidView` wrappers no longer used |
| `ui/settings/ThemeSettingsContent.kt` | Modify | Use `DsScreen`, `DsButton`, `DsBadge` |
| `src/debug/.../DsComposeCatalogActivity.kt`, `src/debug/AndroidManifest.xml` | Create / Modify | Compose catalog |

Name clash note: Compose components share names with View classes (`DsButton`, `DsNavBar`…) but live in package `…designsystem.compose`; files that need both import one with an alias.

## Global Constraints

- `minSdk 26`, `compileSdk`/`targetSdk 37`, Kotlin 2.4.10, Compose BOM `2026.09.00`, Backdrop 2.0.1. No new dependencies; no Material3.
- Touch targets ≥ `ds_touch_min` (48 dp), as in the View DS.
- No new user-visible strings unless a component needs one; any new string goes into all 9 `strings.xml` files and passes `lintDebug` (`MissingTranslation`).
- At `GlassLook.DEFAULT`, glass must look exactly as at the end of JC except for F1 (if approved).
- No automated tests; every Task ends with a manual scenario naming its device.

## Review Focus

1. **Liquid Glass sliders while a Compose screen is open** (Refraction, Blur, Gloss, Corner at 0 and 100): glass updates live, no crash, lens never exceeds the corner radius. → Task 2 Step 6 (c)
2. **API < 31 (emulator API 30):** every component that uses `dsGlass` falls back to the static surface; nothing is transparent. → Task 8 Step 4 (b)
3. **Rapid and interrupted gestures:** tap during a press spring, drag a sheet and release halfway, flick the slider, tap segments quickly: final state is always consistent with `value`. → Task 5–7 scenarios
4. **TalkBack:** toggle, radio row, segmented, slider read role, label and state like the View versions. → Task 8 Step 4 (d)
5. **Largest font + Bold text:** components grow, nothing clips, touch targets stay ≥ 48 dp. → Task 8 Step 4 (c)

---

### Task 1: Device gate — finish JC's measurements on the Oppo (low-end) and the Samsung

**Manual check required:** yes — performance measurement that decides whether JD continues (`Workflow.md` §3; Q4).
**Files:** `app/src/main/baseline-prof.txt`; results into this plan's Log and `docs/knowledgements/JC-compose-pilot.md` §5.

- [x] **Step 1: Profile contents.** Add `HSPLcom/kyant/**;->**(**)**` and `Lcom/kyant/**;` to `baseline-prof.txt`. Build release; confirm the APK contains `assets/dexopt/baseline.prof` and that the merged profile under `app\build\intermediates\*_art_profile` lists `com/kyant` and `androidx/compose` classes (`Select-String` on the file).
- [x] **Step 2: Measure** on the Oppo, then the Samsung, with JC's method (`knowledgements/JC-compose-pilot.md` §5 and §8): APK both builds; 1.1 first/second open, fresh install and after `speed-profile`, 10 runs each; paging real Home vs probe ×20, 3 runs; scroll 1.1 at the largest font (Oppo).
- [ ] **Step 3: JC's open manual items** on the right device: Task 2 (a) fallback look on the emulator API 30 and (g) largest font on the Oppo; (c) recreation with "Don't keep activities"; (d)/(k) not-default flow; (o) corner slider on Oppo; TalkBack on one card; Task 3 (c) fallback and (e) rotation on the Oppo.
- [x] **Step 4: Gate.** Pass = Oppo first open after profile ≤ +100 ms and paging Janky ≤ +5 pts on the Oppo and the Samsung. Fail → stop and report (Q4). Record the result in JC's Review Focus evidence too.

### Task 2: Glass and screen foundation

**Manual check required:** yes — glass appearance and slider response need a device.
**Files:** `GlassTuning.kt`, `DsGlass.kt`, `GlassLookState.kt`, `DsText.kt`, `DsScreen.kt`.
**Interfaces — Produces:**
- `GlassTuning.backdropBlurDp(look: GlassLook): Float` (16 at `blur = 50`, 0 at 0, 32 at 100, linear on both halves), `GlassTuning.backdropLensDp(look: GlassLook): Pair<Float, Float>` (height 12 → 24, amount 24 → 64 across `refraction` 50 → 100; 6/12 at 0), `GlassTuning.backdropHighlightAlpha(look: GlassLook): Float` (0.5 at `gloss = 50`, linear 0 → 1).
- `@Composable fun rememberGlassLook(): State<GlassLook>`
- `val LocalDsBackdrop: ProvidableCompositionLocal<Backdrop?>` (default `null` → static surface)
- `fun Modifier.dsGlass(surface: DsSurface, corner: DsCorner, backdrop: Backdrop? = null): Modifier` — `null` reads `LocalDsBackdrop.current`.
- `@Composable fun DsScreen(title: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
- `object DsTextStyles` with `headingLarge, title, card, body, bodyStrong, value, caption, captionSmall, badge, display, sectionHeader` (fonts/sizes/line heights from `ds_type.xml`; F7 line heights if Q1 approves F7, else today's no-`lineHeight` styles).

- [x] **Step 1:** Mappers in `GlassTuning` with the values above; KDoc states that `DEFAULT` reproduces JC.
- [x] **Step 2:** `rememberGlassLook()` with `DisposableEffect(GlassEnvironment.from(context))` registering a listener that sets the state.
- [x] **Step 3:** `dsGlass` reads the look; effects use the mappers; `highlight = { Highlight.Default.copy(alpha = backdropHighlightAlpha(look)) }` (F1) or `null` if F1 refused; keep JC's state-read lambdas.
- [x] **Step 4:** `DsText.kt` full style set; `DsScreen` with `.layerBackdrop(b).dsAurora()` order and `CompositionLocalProvider(LocalDsBackdrop provides b)`.
- [x] **Step 5:** `assembleDebug`, `lintDebug` green.
- [ ] **Step 6: Scenarios (Oppo or Samsung):** (a) screen 1.1 at default look matches the JC screenshot (plus rim highlight if F1); (b) each slider at 0/50/100 changes the cards while 1.1 is open; (c) Corner 100 + Refraction 100: no seams at corners (RF1); (d) emulator API 30: 1.1 unchanged static look.

### Task 3: Press feedback, button, chip, badge, icon tile

**Manual check required:** yes — touch feel (`Workflow.md` §3, new gesture behaviour).
**Files:** `DsPress.kt`, `DsButton.kt`, `DsSmall.kt`.
**Interfaces — Produces:**
- `fun Modifier.dsPressable(interactionSource: MutableInteractionSource, enabled: Boolean = true): Modifier` (F2 values; no-op scale if F2 refused, dim only)
- `@Composable fun DsButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: DsButtonVariant = DsButtonVariant.Primary, size: DsButtonSize = DsButtonSize.L, enabled: Boolean = true)` — `enum DsButtonVariant { Primary, Secondary }`, `enum DsButtonSize(visibleDp, paddingHDp) { L(48, 20), M(40, 16), S(34, 14) }`; disabled alpha 0.4; Primary = `DsSurface.PRIMARY` with its glow (API ≥ 28), Secondary = `CONTROL` glass.
- `@Composable fun DsChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)` (34 visible / 48 touch, padding 14).
- `@Composable fun DsBadge(text: String, type: DsBadgeType = DsBadgeType.New, modifier: Modifier = Modifier)` — `enum DsBadgeType { Warning, Danger, New }`.
- `@Composable fun DsIconTile(@DrawableRes icon: Int, color: DsTileColor, modifier: Modifier = Modifier)` (`ds_tile_size`, icon 24, decorative).

- [x] **Step 1–3:** implement the three files to the View specs in `DsButton.kt`, `DsChipView.kt`, `DsBadgeView.kt`, `DsIconTileView.kt`.
- [x] **Step 4:** build + lint.
- [x] **Step 5: Scenarios (Oppo + Samsung, in the Task 8 catalog once it exists; until then a temporary `@Preview` + 1.1):** (a) press and hold: scale/dim, release springs back; (b) press then slide off: no click; (c) rapid double tap: two clicks, no stuck pressed state (RF3); (d) disabled: no feedback.

### Task 4: Nav bar and search field

**Manual check required:** no (static layout; checked in Task 8).
**Files:** `DsNavBar.kt`, `DsSearchField.kt`.
**Interfaces — Produces:**
- `@Composable fun DsNavBar(title: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier, backLabel: String = stringResource(R.string.ds_nav_back_default))` — back pill `CONTROL` glass, visible 32 in 48 touch, title `headingLarge` marked as heading; content description from `R.string.ds_nav_back_desc`.
- `@Composable fun DsSearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null)` — 44 visible / 48 min, `CONTROL` pill, magnifier `ds_text_secondary`, `BasicTextField` with `ImeAction.Search`.

- [x] **Step 1–2:** implement to `DsNavBar.kt` / `ds_nav_bar.xml` and `DsSearchField.kt` specs.
- [x] **Step 3:** build + lint; `DsScreen` switches from the `AndroidView` nav bar to this one.

### Task 5: Row, card group, toggle

**Manual check required:** yes — new toggle gesture and TalkBack roles.
**Files:** `DsRow.kt`.
**Interfaces — Produces:**
- `@Composable fun DsToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true)` (F3; `null` callback = display only, used inside rows).
- `sealed interface DsRowTrailing { data object Chevron; data class Value(val text: String); data class Toggle(val checked: Boolean); data class Radio(val selected: Boolean); data object None }`
- `@Composable fun DsRow(title: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, description: String? = null, leading: (@Composable () -> Unit)? = null, trailing: DsRowTrailing = DsRowTrailing.None, showDivider: Boolean = true)` — min height `ds_row_min_height`; whole row is one a11y node with role Switch / RadioButton per trailing, like `DsRowView`.
- `@Composable fun DsCardGroup(modifier: Modifier = Modifier, content: @Composable DsCardGroupScope.() -> Unit)` — `CARD` glass, clips content, hides the last row's divider (scope tracks row count).

- [x] **Step 1–2:** implement; divider starts at the text column like `ds_row.xml`.
- [x] **Step 3:** build + lint.
- [x] **Step 4: Scenarios (Oppo + Samsung):** (a) tap row toggles once; (b) drag the toggle thumb halfway and release → snaps to nearest side, `onCheckedChange` fires once (RF3); (c) TalkBack: "On/Off, switch", radio rows "selected" (RF4).

### Task 6: Segmented control and slider row

**Manual check required:** yes — slider drag and segment animation.
**Files:** `DsSegmented.kt`, `DsSlider.kt`.
**Interfaces — Produces:**
- `@Composable fun DsSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Boolean = true)` — 36 visible / 48 touch, padding 3, gap 2, option height 30, padding 14; selected thumb `ds_text_primary` pill sliding with spring (F4), selected text `ds_on_light` semibold.
- `@Composable fun DsSliderRow(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, onTrackingChange: ((Boolean) -> Unit)? = null)` — 0..100, value text "x%", touch height 48, a11y progress semantics with the label.

- [x] **Step 1–2:** implement to `DsSegmentedControl.kt` / `DsSliderRow.kt` specs.
- [x] **Step 3:** build + lint.
- [x] **Step 4: Scenarios (Oppo + Samsung):** (a) tap segments quickly: thumb ends on the last tapped option (RF3); (b) flick the slider past both ends: clamps 0/100; (c) tracking callbacks fire start/end once per drag.

### Task 7: Alert dialog and bottom sheet

**Manual check required:** yes — new drag gesture (F5) and window behaviour.
**Files:** `DsDialogs.kt`.
**Interfaces — Produces:**
- `@Composable fun DsAlertDialog(onDismiss: () -> Unit, title: String, message: String? = null, positive: Pair<String, () -> Unit>, negative: Pair<String, () -> Unit>? = null, content: (@Composable () -> Unit)? = null, cancelable: Boolean = true)` — width `ds_dialog_width` (312), max screen − 2 × 24 dp, `SHEET` surface with `DsCornerRole.SHEET`, scrim 60 %, F6 entry animation; a button tap calls its action then `onDismiss`.
- `@Composable fun DsBottomSheet(onDismiss: () -> Unit, title: String? = null, showClose: Boolean = true, content: @Composable ColumnScope.() -> Unit)` — `SHEET` top corners only, handle 38 × 5, close button 32 in 48, bottom padding + navigation inset, F5 drag-to-dismiss (dismiss past 25 % height or fling > 1200 dp/s).

- [x] **Step 1–2:** implement with `androidx.compose.ui.window.Dialog` (`usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false` for the sheet).
- [x] **Step 3:** build + lint.
- [x] **Step 4: Scenarios (Oppo + Samsung):** (a) back key / scrim tap dismiss when cancelable, not otherwise; (b) drag sheet 20 % and release → springs back; 30 % → dismisses; (c) rotate with the dialog open → still shown once, no leaked window in logcat; (d) gesture nav: sheet content above the nav bar.

### Task 8: Compose catalog and full-system check

**Manual check required:** yes — visual comparison against the View catalog.
**Files:** create `src/debug/java/gambi/global/os26launcher/debug/DsComposeCatalogActivity.kt`; modify `src/debug/AndroidManifest.xml` (exported + `permission=DUMP`, theme `Theme.Os26Launcher.Ds`).

- [x] **Step 1:** catalog inside `DsScreen`: text styles, buttons (all variants/sizes/disabled), chips, badges, icon tiles, nav bar, search field, card group with every row type, toggle, segmented (2 and 4 options), slider, buttons that open the dialog and the sheet. Open with `adb shell am start -n gambi.global.os26launcher/gambi.global.os26launcher.debug.DsComposeCatalogActivity`.
- [x] **Step 2:** build debug + release (catalog absent from release dex, as JC Task 3 Step 6).
- [x] **Step 3:** screenshots of `DsCatalogActivity` (View) and the Compose catalog on the Oppo and the Samsung, plus the emulator API 30 for the fallback.
- [x] **Step 4: Scenarios:** (a) each component matches its View twin except approved F-changes; (b) emulator API 30: all glass static, nothing transparent (RF2); (c) largest font + Bold text: no clipping, targets ≥ 48 dp (RF5); (d) TalkBack pass over every interactive component (RF4); (e) `gfxinfo` while scrolling the catalog 10 times on the Oppo: record Janky %.

### Task 9: Screen 1.1 on the native components

**Manual check required:** yes — first screen with the F-changes; re-measure open time.
**Files:** modify `ThemeSettingsContent.kt`; delete `core/designsystem/compose/DsViews.kt`; modify the probe only if it used the old `dsGlass` signature (it keeps the explicit-backdrop overload, D3).

- [x] **Step 1:** 1.1 uses `DsScreen`, `DsButton`, `DsBadge`, `dsPressable` on cards; `TextClock` stays an `AndroidView`.
- [x] **Step 2:** confirm no other caller of `DsNavBarView` / `DsButtonView` / `DsBadgeNewView`, then delete `DsViews.kt`.
- [x] **Step 3:** build + lint debug and release.
- [x] **Step 4: Scenarios:** rerun JC Task 2 Step 8 (a)–(o) on the Oppo + Samsung (fallback items on the emulator API 30), now expecting the approved F-changes; first-open median (release, after `speed-profile`, 10 runs) must stay within +20 ms of Task 1's Compose number.

---

## Full feature check (assembled at Workflow step 5)

Devices: Oppo, Samsung, emulator API 30 (fallback look only). Order: Task 1 results → catalog (Task 8 Step 4) → screen 1.1 (Task 9 Step 4). Regression: View screens using the View DS (Settings hub, Library, Control Center settings) unchanged; Home glass (QWEA0) unchanged; Liquid Glass sliders still drive View glass.
**Verification result (device, date):** Oppo PKD130 (Android 14), 2026-11-19, by Claude over adb at the owner's request: Tasks 1–9 scenarios as logged. Samsung SM-A176B used in JC only. Open: API < 31 fallback, largest font + Bold text, TalkBack, rotation, JC items needing system settings.

## Log

| Date | Task | Done | Notes |
|---|---|---|---|
| 2026-11-19 | — | Plan written | Awaiting approval; Q1–Q5 open |
| 2026-11-19 | — | Plan approved by owner; Q1–Q5 approved as recommended (F1–F7 accepted) | |
| 2026-11-19 | 1 | Step 1: added `HSPLcom/kyant/**;->**(**)**` + `Lcom/kyant/**;` to `baseline-prof.txt`. Release green. APK has `assets/dexopt/baseline.prof` (4,495 B) + `.profm` (527 B). Final merged profile (`combined_art_profile/release/.../baseline-prof.txt`, after R8 rewrite): 125 `com/kyant` lines, 5,732 `androidx/compose` lines, 5,408 app lines. Binary `baseline.prof` size is not proportional to rule count (JC build 4,524 B, XML build 3,002 B); not decoded (no `profgen` on this machine) | Measured. Steps 2–4 wait for Live 4 + Oppo (only the Samsung is connected) |
| 2026-11-19 | 1 | Owner has no Live 4. Device set changed to Oppo PKD130 (A3x line, Android 14, 720×1604, low-end) + Samsung SM-A176B + emulator API 30 (fallback look only); Q5 rewritten. Oppo has our app as a **debug build signed with a different debug key** (`6f7cc764`; this machine signs `322f44e3`), installed 12:49 today, so `adb install -r` will fail. App data backed up with `run-as … tar` (11 entries, 276,480 B, archive verified) | Reinstall needs uninstall = data wipe → waiting for owner approval; restore from the tar afterwards |
| 2026-11-19 | 1 | Owner approved the reinstall. Uninstalled the old-key build, installed ours (`322f44e3`), restored data from the tar (`run-as … tar -xf`; toybox needs the dash), Home layout came back. The **old build's crash log** showed two crashes in classes that are not in this repo (`core/glass/compose/LiquidGlassModifier.kt:51` lens on a non-`CornerBasedShape`; `ui/settings/compose/SettingsHubCompose.kt:123` → `DefaultLauncherAction` registered after STARTED) — another Compose branch exists on another machine | Reported to owner |
| 2026-11-19 | 1 | Step 2 on **Oppo PKD130** (release builds, 10 runs, medians): 1.1 first open XML 229.5 / Compose 473 ms fresh (+243.5), **218.5 / 265.5 ms after `speed-profile` (+47)**; second open 171.5 / 221.5 fresh, 162 / 179 profiled (+17). Paging ×20, debug, 3 runs: real Home 24.5–31.0 % janky (95th 48–57 ms, QWEA0 lens glass) vs probe 12.1–19.0 % (95th 31–85 ms). 1.1 scroll: Compose 1.73 % janky; XML not comparable (layout fits, `overScrollMode="never"` → 63 frames, no scroll) | Measured. Compose `verticalScroll` shows the stretch overscroll the XML screen disables → note for Task 9 |
| 2026-11-19 | 1 | Step 3: done — R8 release 1.1 renders the not-default state correctly on Oppo, no crash. Not done — fallback look (no emulator image ≤ API 30 installed; download needs owner OK), recreation, largest font, not-default flow, corner slider, TalkBack (each needs a system/app setting change on the owner's phone) | Carried to Task 8 Step 4 |
| 2026-11-19 | 1 | Step 4 gate: **PASS** — first open after profile +47 ms (Oppo) and +43 ms (Samsung, JC) ≤ +100 ms; paging Janky not worse than real Home on either device. Open risk: fresh-install first open is ~2× XML on the Oppo until the profile is compiled (sideload); Play installs ship the profile with the APK | Owner to confirm before Task 2 |
| 2026-11-19 | 1 | Owner confirmed Task 1 and moved to Task 2 (Step 3 leftovers carried to Task 8) | Owner decision |
| 2026-11-19 | 2 | Steps 1–4: `GlassTuning.backdropBlurDp/backdropLensDp/backdropHighlightAlpha/backdropHighlightWidthScale`; `GlassLookState.kt` (`rememberGlassLook`, listener on `GlassEnvironment`, main thread); `DsGlass.kt` (look read inside the `effects`/`highlight` lambdas so Backdrop re-runs them on change; `drawable.refresh()` on look change; F1 highlight; `LocalDsBackdrop`; old explicit-backdrop overload kept, D3); `DsText.kt` with all 11 styles + F7 line heights; `DsScreen.kt`; `DsNavBarView` gains `onBack: (() -> Unit)?` to hide back | — |
| 2026-11-19 | 2 | Deviations: (1) `GlassLook.DEFAULT.refraction` is **0**, not 50 → lens maps 0 → 12/24 dp (JC values), 100 → 24/64 dp. (2) Backdrop's `Highlight.Default.alpha` is already 1 (its style colour carries 0.5) → alpha = gloss/50 capped at 1, and width × gloss/50 above 50, so gloss 50 = library default. (3) `LocalDsBackdrop` lives in `core/glass/compose/DsGlass.kt`, not `DsScreen.kt`, to keep `core/glass` independent of `designsystem` | Plan values corrected in code; plan text left as written |
| 2026-11-19 | 2 | Step 5: `assembleDebug` + `lintDebug` green; no new lint finding in JD files | Measured |
| 2026-11-19 | 2 | Step 6 on Oppo (debug, owner's custom look 18/17/0/54): (a) 1.1 shows the F1 rim highlight and tighter F7 text, layout otherwise as before — pass; (b)(c) all four sliders set to 100 → reopen 1.1: rounder corners, brighter tint, thicker rim, visible edge refraction, no corner seams, no crash — pass; live update while 1.1 stays open not exercised (no navigation path from 1.1 to the slider screen; covered by the listener code); owner's settings file backed up and restored byte-identical (sliders back to 18/17/0/54); (d) API 30 emulator not available yet → Task 8 | Measured |
| 2026-11-19 | 2 | Owner confirmed Task 2 (Samsung check deferred to Task 8/9) and moved to Task 3 | Owner decision |
| 2026-11-19 | 3 | Steps 1–3: `DsPress.kt`, `DsButton.kt` (`DsButtonVariant`, `DsButtonSize`; PRIMARY glow via `Modifier.shadow` API ≥ 28; touch inset as padding outside the surface), `DsSmall.kt` (`DsChip` with `toggleable(role = Checkbox)`, `DsBadge`, `DsBadgeType`, `DsIconTile` with 4 dp black shadow on the TILE-role shape) | — |
| 2026-11-19 | 3 | Deviations: (1) F2 split into `dsPressable` (scale, first in the chain) + `dsPressHighlight` (8 % white/black overlay, after the clipped surface) — one modifier cannot both scale the whole node and stay inside the clip. (2) `dsGlass` now samples the backdrop only for `followsGloss` surfaces (CARD, CONTROL, SHEET); opaque ones (PRIMARY, badges, CONTROL_SELECTED) draw static. (3) Started `DsComposeCatalogActivity` (Task 8's file) now with Task 3's sections, so each Task has a device surface; later Tasks add sections | — |
| 2026-11-19 | 3 | Step 4: `assembleDebug` + `lintDebug` green, no new finding | Measured |
| 2026-11-19 | 3 | Step 5 on Oppo (catalog): all variants/sizes/disabled render; glow under PRIMARY; chips, badges, 10 tile colours correct. (a) hold → button scales to 0.97 (left edge 32 → 42 px) and darkens, release springs back, 1 click — pass; (b) press then slide off → no click — pass; (c) 2 then 4 rapid sequential taps → +2 / +4 clicks, no stuck pressed state — pass (a first try with two parallel `input tap` merged into one gesture: test artefact); (d) disabled tap → no click — pass. No crash | Measured |
| 2026-11-19 | 3 | Owner confirmed Task 3 and moved to Task 4 | Owner decision |
| 2026-11-19 | 4 | Steps 1–3: `DsNavBar.kt` (back pill: `CONTROL` glass, 32 visible in 48, chevron 24 + label `body`, F2 press, one a11y button "Quay lại, <label>"; title `heading()`), `DsSearchField.kt` (`BasicTextField` + decoration: 44 visible in 48, `CONTROL` pill, magnifier, hint, `ds_accent` cursor, `ImeAction.Search`); `DsScreen` now uses the Compose `DsNavBar` (shown when `title` or `onBack` is set); catalog gets a search section. Build + lint green | Deviation: the title uses `DsTextStyles.title` (20 sp), as the View `ds_nav_bar.xml` does, not `headingLarge` as the plan said |
| 2026-11-19 | 4 | Check on Oppo (catalog; no manual-check flag on this Task): nav bar and search render; TalkBack description "Quay lại, Cài đặt" on one node; typing "zalo" works, Enter fires `onSearch` once; keyboard does not cover the field; the back pill closes the screen. `uiautomator` does not dump the heading flag, so `heading()` is checked by code only. No crash. Oppo's app store (`com.heytap.market`) shows an "install recommend" screen after each `adb install` — unrelated | Measured |
| 2026-11-19 | 4 | Owner confirmed Task 4 and moved to Task 5 | Owner decision |
| 2026-11-19 | 5 | Steps 1–3: `DsRow.kt` — `DsToggle` (F3: 51 × 31 track `ds_accent`/`ds_surface_control`, 27 dp thumb with 30 % black ring offset 1 dp, spring position, thumb stretches 6 dp while pressed/dragged, `draggable` + `toggleable(role = Switch)`, RTL-aware), `DsRowTrailing`, `DsRow` (min 56, padding 12/12/10, gap 12, radio dot 22 dp drawn like `ds_radio.xml`, value capped at half the window width, 1 dp divider from the text block with 12 %/88 % fade, whole row one a11y node: `toggleable`/`selectable`/`clickable`, press = highlight only like iOS rows), `DsCardGroup` + `DsCardGroupScope` (`row(...)`, `item { showDivider -> }`). Catalog section added. Build + lint green (one `ConfigurationScreenWidthHeight` warning fixed by using `LocalWindowInfo.containerSize`) | Deviation: `DsCardGroup` takes a non-composable builder (`DsCardGroupScope.() -> Unit`, LazyColumn-style) instead of `@Composable DsCardGroupScope.() -> Unit`, so the group knows which row is last |
| 2026-11-19 | 5 | Step 4 on Oppo (catalog): render matches the View row (tile, chevron, value + chevron, toggle, radios). (a) tap toggle row → flips once (changes +1); tap Radio B → A deselected, B selected — pass. (b) drag the thumb +40 px → on, +1 change; drag −26 px (past slop, under half) → stays on, no change; drag −57 px → off, +1; drags under the touch slop act as taps (flip), as on iOS; disabled toggle ignores taps — pass. (c) every checkable node exposes `checkable`/`checked` correctly; role names are not visible in `uiautomator` dumps and TalkBack was not run — partial. Divider start was 12 px early (text position measured inside the row padding) → fixed; re-measured: starts at the text block (≈157 px with tile, ≈57 px without). No crash | Measured. TalkBack pass stays in Task 8 Step 4 (d) |
| 2026-11-19 | 5 | Owner confirmed Task 5 and moved to Task 6 | Owner decision |
| 2026-11-19 | 6 | Steps 1–3: `DsSegmented.kt` (36 visible in 48, `CONTROL` glass pill, pad 3, gap 2, options 30 high / 14 padding; selected pill `ds_text_primary`, text `ds_on_light` semibold; F4 thumb slides with a spring between measured option bounds, snaps on first layout; `selectableGroup` + `collectionInfo`, each option `selectable(role = RadioButton)` + `collectionItemInfo`; `fill` = equal widths or wrap), `DsSlider.kt` (`DsSliderRow`: label 104 / gap 12 / track 48 touch, 4 dp rail `ds_surface_control`, active `ds_accent`, 10 dp thumb with `ds_accent_light` ring and 30 % shadow, 6 dp edges, value "x%" 34 wide; a11y = one progress node with label + percent and `setProgress`; RTL-aware). Catalog section added. Build + lint green | Slider keeps the DS3 look: no F-item covered it |
| 2026-11-19 | 6 | **Bug found & fixed (RF3):** the first slider version set the value on pointer-down, so a vertical scroll starting on the slider changed it (measured 52 → 16 on the Oppo). Now a drag starts only after horizontal touch slop (`awaitHorizontalTouchSlopOrCancellation`), and a tap jumps on release via `detectTapGestures` (cancelled when the parent scrolls). Re-measured: vertical swipe leaves the value and tracking counters unchanged | Measured |
| 2026-11-19 | 6 | Step 4 on Oppo (catalog): (a) 4 rapid taps on the 4-option control → 4 selection callbacks, thumb ends on the last tapped option; each option is a 48 dp checkable node with the right `checked` state — pass; the slide itself could not be captured (`screencap` latency ~250 ms > animation; slowing animations needs a system setting) so F4 motion is checked by code only. (b) drag past right end → 100, past left → 0, tap mid-track → 52 — pass. (c) tracking start/end fire once per gesture (counters 1/1 → 2/2 → 3/3) — pass. Slider exposed to a11y as a SeekBar described "Độ mờ". No crash | Measured |
| 2026-11-19 | 6 | Owner confirmed Task 6 and moved to Task 7 | Owner decision |
| 2026-11-19 | 7 | Steps 1–3: `DsDialogs.kt` — `DsAlertDialog` (Compose `Dialog`, width min(312, window − 48), `SHEET` surface on the SHEET role, padding 20/24/20/20, gap 12, title `card` + `heading()`, message `body` secondary, optional content, buttons Secondary + Primary split with gap 10 or one full-width; F6: scale 1.1 → 1 + fade in with a spring, fade out 180 ms before `onDismiss`; window dim 60 % follows the animation) and `DsBottomSheet` (full-window `Dialog`, own scrim fading with the drag, `SHEET` top corners only via `DsSurfaceDrawable(topCornersOnly = true)`, handle 38 × 5, title `card` + close 32-in-48 described "Đóng", nav-bar inset; F5: spring in, drag down dismisses past 25 % or > 1200 dp/s, otherwise springs back). Both provide `LocalDsBackdrop = null` (separate window: backdrop coordinates do not match; `ds_surface_sheet` is ~94 % opaque). Catalog section added | Deviation: plan option A said a "custom foundation sheet"; it is a full-window Compose `Dialog` hosting a foundation-drawn sheet (same thing, named for clarity) |
| 2026-11-19 | 7 | Build issues fixed on the way: fully-qualified `androidx.compose.ui.composed` call does not compile (extension must be imported); lint `LocalContextResourcesRead` → window width from `LocalWindowInfo`. **Bug found & fixed:** dialog dim set in `SideEffect` read the animation once at 0 → no dim at all (measured); now `snapshotFlow { progress }` drives `window.setDimAmount` | Measured |
| 2026-11-19 | 7 | Step 4 on Oppo (catalog): (a) cancelable dialog closes on Back and on outside tap; locked dialog ignores both and closes only via its button, running its action; positive runs its action then closes — pass. (b) sheet dragged ~20 % springs back; ~32 % dismisses; close button, scrim tap and Back all close — pass. (c) rotation not run (needs system auto-rotate). (d) 3-button nav: sheet content sits above the nav bar — pass. Content behind the dialog/sheet is faintly visible through the ~94 % `SHEET` surface, same as the View `DsDialog` on this device. No crash | Measured |
| 2026-11-19 | 7 | Owner confirmed Task 7 and moved to Task 8 | Owner decision |
| 2026-11-19 | 8 | Step 1: catalog complete (text styles, dialogs/sheet, segmented, slider, rows/card group/toggles, search, buttons, chips, badges, tiles; nav bar via `DsScreen`); debug-only `--ez noGlass true` extra turns real glass off for measurements. Step 2: debug + release green, lint clean; release dex has 0 copies of `DsComposeCatalogActivity` (debug 1); release APK 8,015,534 B (JD components unused in release yet, R8 drops them) | Measured |
| 2026-11-19 | 8 | Step 3 on Oppo: View catalog (`DsCatalogActivity`) and Compose catalog screenshot sets captured; Compose components match their View twins apart from the approved F-changes (F1 rim, F2 press, F3 toggle, F4 segmented, F5/F6 dialogs, F7 line heights). A first capture run showed Recents and a jump to the top mid-run (Recents events in logcat, Oppo "tap status bar to scroll to top"): external input on the phone; a second run was clean | Screenshots in session scratchpad `oppo/cat/` |
| 2026-11-19 | 8 | Step 4 (e) **performance finding** — catalog scroll ×20, debug, Oppo, 2 runs each: Compose with glass 37–40 % janky (50th 36 ms, 90th 44–46 ms); View catalog 40 % (15 / 28 ms); Compose with `noGlass` **7.8 %** (17 / 24 ms); Compose with lens disabled 29 % (32 / 40 ms); Compose with real glass on CARD only (CONTROL static) **10.1–10.4 %** (22 / 28 ms). Cost is the number of real-glass surfaces (~15 on the catalog, each sampling the layer + highlight shader every frame), not Compose and not mainly the lens. All experiment edits reverted (`DsGlass.kt` restored from backup; no "TEMP" markers left) | Measured. Needs an owner decision (glass policy) before Task 9 / JH |
| 2026-11-19 | 8 | Owner chose "real glass on cards only" (D9) → `DsGlass.kt` gates real glass to `setOf(CARD, SHEET)`. Owner chose to skip (b) emulator API 30 fallback, (c) largest font + Bold text, (d) TalkBack for now → left open; (a) and (e) done. Steps 3–4 ticked as done on the Oppo with (b)(c)(d) recorded open | Owner decision |
| 2026-11-19 | 9 | Steps 1–3: `ThemeSettingsContent` rebuilt on `DsScreen` + `DsButton` + `DsBadge`; theme cards use `dsPressable`/`dsPressHighlight` (F2) and the `LocalDsBackdrop` glass; `TextClock` stays an `AndroidView`. No caller left for `DsNavBarView`/`DsButtonView`/`DsBadgeNewView` → `DsViews.kt` removed (`git rm -f`: it only had this session's own uncommitted edit). Debug + release green, lint clean. Release APK 8,031,918 B (+16,384 B now that the JD components ship) | — |
| 2026-11-19 | 9 | Step 4 on Oppo: 1.1 renders on the Compose DS (nav bar, glass cards with F1 rim, MỚI badge, primary glow, not-default note); back pill "Quay lại, Cài đặt"; card tap selects, re-tap previous restores; press-and-hold shows F2 (scale + 8 % light); press then slide off with move events selects nothing (a first try sent DOWN→UP with no MOVE, which Compose counts as a tap — test artefact); nav back closes. Release (R8) renders the same, no missing-class errors | Measured. Not re-run here: JC (c)(d)(k)(o) items that need system settings / default-launcher change, left open with Task 8 (b)(c)(d) |
| 2026-11-19 | 9 | Step 4 timing (release, Oppo, after `speed-profile`, 10 runs): first open median **278.5 ms** (Task 1 Compose 265.5 → +13 ms, limit +20 — pass); second open 172.5 ms (was 179) | Measured |

## Review Focus evidence (filled in at close-out)

| # | Review Focus item | Evidence | Result |
|---|---|---|---|
| 1 | Sliders live | Task 2 Step 6 (b)(c) | **Partial pass** — all four sliders at 0/50/100 reflected after reopening 1.1 on the Oppo, no corner seams; live update while 1.1 stays open has no navigation path, covered by the `GlassEnvironment` listener code |
| 2 | API < 31 fallback | Task 8 Step 4 (b) | **Open** — no Android 8–11 device; emulator API 30 skipped by owner (2026-11-19) |
| 3 | Interrupted gestures | Task 3 (c), Task 5 (b), Task 6 (a), Task 7 (b) | **Pass** on the Oppo; found and fixed: slider changed value on a vertical scroll (Task 6) |
| 4 | TalkBack | Task 5 (c), Task 8 (d) | **Partial** — every node exposes the right checkable/checked/description in `uiautomator`; real TalkBack run skipped by owner |
| 5 | Largest font + Bold text | Task 8 (c) | **Open** — needs a system font change; skipped by owner |

## Shared docs updated (filled in at Workflow step 7)

- `AGENTS.md`: Compose DS usage (`DsScreen`, `LocalDsBackdrop`, `dsPressable`, D9 glass policy) *(done)*
- `lessons/design-system.md`, `lessons/glass.md`: JD lessons *(done)*
- `project_structure.md`: "Tệp thêm ở JD" *(done)*
- `knowledgements/JC-compose-pilot.md` §5: Oppo numbers from Task 1 *(done in Task 1)*
- `knowledgements/JD-compose-design-system.md`: new *(done)*
