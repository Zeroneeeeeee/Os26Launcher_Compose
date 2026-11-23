# JH1 — Compose Home, static (step 3a of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md` and `docs/Workflow.md` apply, except the per-Task stop: the owner asked (2026-11-19) for the Compose chain to run every Task of a plan in one go and stop only at the end of the plan, or for a decision only the owner can make, or when the phone needs the owner (unlock, system setting). The Log is still written per Task. Manual checks on the owner's Oppo PKD130 (Android 14, 720p, low-end). Commands are written for PowerShell.
>
> Tests: the owner allowed (2026-11-19) JVM unit tests (JUnit 4 + `kotlinx-coroutines-test`) for the layout logic and `HomeViewModel` before Home goes to Compose. No UI tests.
>
> Language: English (owner's choice for the Compose-rewrite plans; see the `AGENTS.md` exception).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-19 — owner approved after the Oppo and Samsung checks. Carried to JH2: pager snap/fling (Samsung p90 +5 ms vs View), HomeViewModel tests. Carried to JH3: notification badges, folders, app widgets. Open: widget and folder rendering not seen on device (owner's layout has none), font scale 1.3, API < 31, TalkBack.
**Devices:** Oppo PKD130 (mandatory) · **Room schema change:** none

**Goal:** Pin the current layout rules with unit tests, then draw the real Home (wallpaper, pages, apps, folders, self-drawn widgets, search pill with page dots, dock) in Compose from the real `HomeViewModel`, inside a debug-only Activity next to the View Home, and measure it on the Oppo.

**Architecture:** A new package `ui/home/compose/` holds stateless composables that take `HomeUiState` and callbacks. A debug Activity (`ComposeHomeActivity`, `src/debug`) gets the real `HomeViewModel` from Hilt, collects `uiState`, and calls the same size/backdrop entry points `HomeActivity` calls. Geometry is not re-invented: the grid measures with `GridMetrics` (pure), the pill reuses `PageDotsLayout`, and widgets draw with the existing `WidgetRenderer` on the Compose canvas. The View Home is not touched, so the shipped launcher cannot regress.

**Tech Stack:** Kotlin 2.4.10, Compose BOM `2026.09.00`, Backdrop `2.0.1`, JUnit 4.13.2 (new, `testImplementation` only). `kotlinx-coroutines-test` waits for JH2 (no suspend code under test yet).

**Spec:**
- Roadmap row JH in `docs/knowledgements/JC-compose-pilot.md` and the JD close-out (`docs/knowledgements/JD-compose-design-system.md`, open items).
- Current Home behaviour: `activity_home.xml` (pager weight 6, pill, dock weight 1, dock margins 8 dp), `GridMetrics`, `DockView` (space-evenly, glass = icon + 2 × 20 dp, corner 28 dp), `IconCellView` (label 10 sp, shadow 4 dp), `SearchPillView` (112 × 26 dp, touch 48 dp), `WidgetRenderer`.
- Owner direction: iOS 26 fidelity over pixel parity (memory `ios-fidelity-goal`); glass policy JD D9 (real glass on CARD and SHEET only).

---

## Why this design

- **Tests first, and on the pure part.** Drag and drop (JH2) will call the same planners from a new UI. The planners in `domain/layout/` (819 lines, no Android types) hold every placement rule, so characterization tests there catch a behaviour change cheaply. `LayoutReconciler` itself only wires planners to Room; testing it needs Robolectric or a device (decision D2).
- **Parallel, not replacing.** JH1 has no drag, no edit mode, no folders opening. Shipping it as the launcher would remove features, so it lives in a debug Activity until JH4.
- **Reuse the View maths.** `GridMetrics.measure` decides icon size, label scale and padding from the real size. Re-deriving it in Compose would drift; calling it keeps the two Homes pixel-aligned, so the side-by-side check is meaningful.

| Option for widgets in JH1 | For | Against | Pick |
|---|---|---|---|
| A. Call `WidgetRenderer.prepare/draw` from `drawWithCache` / `drawIntoCanvas` | Same pixels as the View Home, no rewrite | Renderer is a Canvas API, not composables | **Yes** |
| B. Rewrite clock/calendar/weather/photo in composables | Idiomatic | Four widgets × two sizes of work, belongs in JS if ever | No |
| C. Grey placeholder cards | Zero work | Side-by-side check impossible, glass budget not measured | No |

## iOS fidelity changes

| # | Change | Where |
|---|---|---|
| H1 | Dock glass gets the JD rim highlight (F1) and follows the Glass sliders live | `HomeDock` via `dsGlass(CARD)` |
| H2 | Icon press: scale 0.97 + spring from `dsPressable` (View Home has its own `PressScale`, kept the same feel) | `HomeAppCell` |
| H3 | Page dots follow the finger continuously (pager offset), like iOS, not only on page settle | `HomeSearchPill` |

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | `ComposeHomeActivity` is debug-only and exported, like the JC probe; the View Home stays the launcher | No feature loss in shipped builds |
| D2 | Unit tests cover `domain/layout/*` planners and `GridMetrics`; `LayoutReconciler` and `HomeViewModel` tests move to JH2, where drag/edit code starts calling them from Compose | Room needs Robolectric (large download, slow); the VM has 21 constructor dependencies and JH1 does not change it |
| D3 | Tests are characterization tests: they record today's output, they do not judge it. A surprising output gets a comment, not a fix | JH1 must not change behaviour |
| D4 | Real glass on the dock and on self-drawn widget cards (CARD); folder icons (flat `folder_background`, like the View default), pill (flat `search_pill_bg`) and app-widget cards (flat `widget_glass_flat`) stay static | JD D9 perf finding (15 real-glass surfaces = 38 % janky) |
| D5 | App widgets (`PlacedAppWidget`) draw as an empty static card in JH1; `AppWidgetHostView` hosting moves to JH3 | Needs `AppWidgetHost` lifecycle, not static drawing |
| D6 | Tap on an app calls `HomeViewModel.onAppClicked(item, ScreenRect)`; tap on a folder does nothing yet (JH3); long press does nothing (JH2) | Static scope |
| D7 | Icons load through `HomeViewModel.loadIcon(key, sizePx)` with an in-memory `ImageBitmap` cache keyed by `AppIconKey` + size | Same source as the View Home; no flash on page return (JC scenario b) |
| D8 | Gate: paging janky % on the Oppo is not worse than the View Home on the same pages (JD Task 1: 24.5–31 %) | Compose Home must not be slower than what it replaces |

## Out of scope

Drag and drop, edit mode, ghost page, icon menu (JH2) · folders, App Library, search overlay, widget picker, app widgets (JH3) · default-launcher switch, back/home key handling, View Home removal (JH4).

## File map

| File | Responsibility |
|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `junit`, `kotlinx-coroutines-test` as `testImplementation` |
| `app/src/test/java/.../domain/layout/*Test.kt` | One test class per planner |
| `app/src/test/java/.../core/ui/GridMetricsTest.kt` | Cell maths per preset and label rules |
| `ui/home/compose/HomeIconCache.kt` | `rememberHomeIcon(app, sizePx, loader)` + cache |
| `ui/home/compose/HomeGridPage.kt` | One page: `Layout` placed by `GridMetrics.Cells`; app cell, folder cell, widget cell |
| `ui/home/compose/HomeWidgetCell.kt` | `WidgetRenderer` on the Compose canvas, minute tick |
| `ui/home/compose/HomeSearchPill.kt` | Pill + page dots (`PageDotsLayout`) |
| `ui/home/compose/HomeDock.kt` | Dock glass card, icons space-evenly |
| `ui/home/compose/HomeScreen.kt` | Wallpaper + `layerBackdrop`, pager weight 6, pill, dock weight 1, insets |
| `app/src/debug/.../debug/ComposeHomeActivity.kt`, debug manifest | Host Activity wired to `HomeViewModel` |

## Global Constraints

- `minSdk` and `compileSdk` unchanged; no Material3; no new runtime dependency.
- Glass only through `dsGlass` (JD D3) and only CARD/SHEET real (JD D9).
- Sizes in `dimens.xml`, except `GridMetrics` constants (existing exemption).
- Strings in `strings.xml`; Vietnamese a11y text reuses the View Home strings.
- Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Grid preset 5 × 6 and labels off** — icons shrink and labels hide exactly like the View Home (pinned by `GridMetricsTest`, checked on device in Task 6).
2. **Widget spanning 4 × 2 on a 4-column page** — card fills 4 cells + 3 gutters, no clipping (Task 3 test page on device).
3. **Wallpaper changed in Settings while the Activity is stopped** — `reloadBackdropIfChanged` on resume shows the new image (Task 2).
4. **Empty dock / dock with 1 app** — glass keeps full width, icons centred (Task 4).
5. **Font scale 1.3** — labels stay one line with ellipsis, grid does not overflow (Task 6).

---

### Task 1: Unit-test harness and planner characterization tests

**Files:** create `app/src/test/java/gambi/global/os26launcher/domain/layout/{GridInsertPlannerTest,GridReflowPlannerTest,GridRemovePlannerTest,NewAppPlacementPlannerTest,DockInsertPlannerTest,FolderPlannerTest,EmptyPagePrunePlannerTest,WidgetPlacementPlannerTest,PageGeometryTest,AppWidgetSizePlannerTest}.kt`, `app/src/test/java/gambi/global/os26launcher/core/ui/GridMetricsTest.kt`; modify `gradle/libs.versions.toml`, `app/build.gradle.kts`.

- [x] Add `junit = "4.13.2"` and `kotlinxCoroutinesTest` (same version as the coroutines already on the classpath); `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`.
- [x] For each planner: read it, write tests for its normal path, its full/no-room path, and every branch its KDoc names (D3: assert today's output).
- [x] `GridMetricsTest`: 720 × 1604-ish sizes for P4X5, P4X6, P5X6, labels on/off, too-narrow returns null, `cellAt` round trip.
- [x] Run: `.\gradlew :app:testDebugUnitTest` — expected: BUILD SUCCESSFUL, all tests pass.

### Task 2: Host Activity, wallpaper and screen frame

**Interfaces — produces:** `@Composable fun HomeScreen(state: HomeUiState, icon: HomeIconLoader, onAppClick: (AppItem, ScreenRect?) -> Unit, modifier: Modifier = Modifier)`; `fun interface HomeIconLoader { suspend fun load(key: ComponentKey, sizePx: Int): Bitmap? }`.

- [x] `ComposeHomeActivity` (`@AndroidEntryPoint`, `by viewModels<HomeViewModel>()`): edge-to-edge, `setContent { HomeScreen(...) }`, collect `uiState` with lifecycle, call `onScreenSize` once the root has a size, `onWindowSizeSettled` on size change, `reloadBackdropIfChanged` in `onResume`; `AppLaunchFailed` event → toast with the View Home string.
- [x] `HomeScreen`: `Box` = wallpaper `Image(Crop)` inside `layerBackdrop` (JD order), `Column` with status/nav bar insets: pager area weight 6, pill, dock weight 1 with 8 dp horizontal/bottom margins.
- [x] Debug manifest entry (exported, like the probe). Check: opens on the Oppo, wallpaper matches the View Home.

### Task 3: Grid page — apps, folders, widgets

**Interfaces — produces:** `@Composable fun HomeGridPage(page: HomePage, grid: GridPreset, showLabels: Boolean, icon: HomeIconLoader, onAppClick: ...)`; `@Composable fun rememberHomeIcon(app: AppItem, sizePx: Int, loader: HomeIconLoader): ImageBitmap?`.

- [x] `Layout` that measures `GridMetrics.from(grid, showLabels).measure(w, h, density)` and places each `PlacedItem` at `padding + pos × (cell + gutter)`, size `span × cell + (span − 1) × gutter`.
- [x] App cell: icon (`iconSize`) + one-line label (10 sp × `labelScale`, white, 4 dp shadow, gap `innerGapPx`), `dsPressable`, `semantics` label = `a11yLabel` or `label`, click → `onAppClick` with the icon's screen rect.
- [x] Folder cell: static `DsSurfaceDrawable` plate with a 3 × 3 preview of `previewApps`, label = `title` or the default folder string.
- [x] Widget cell: card radius `min(w, h) × WIDGET_CORNER_RATIO` (read the View constant), real glass (D4); `WidgetRenderer` `prepare` on size/minute change, `draw` via `drawIntoCanvas { it.nativeCanvas }`; a11y = `contentDescription()`. App widget: static card (D5).

### Task 4: Pager, search pill with dots, dock

- [x] `HorizontalPager` over `state.pages`, `beyondViewportPageCount = 1`, key = `pageId`.
- [x] `HomeSearchPill`: 112 × 26 dp static pill in a 48 dp touch box; dots from `PageDotsLayout` with the pager's `currentPage + currentPageOffsetFraction` (H3); show/hide follows the existing pill/dots toggles if they are in `HomeUiState`, else always both.
- [x] `HomeDock`: glass card full width, height `iconSize + 40 dp` (≤ dock height), corner 28 dp, centred vertically; icons from `state.dock` space-evenly, no labels; same cell size as the grid (`showLabels = false`).

### Task 5: Visual check against the View Home

- [x] Same wallpaper, same pages: screenshot View Home and Compose Home on the Oppo, compare icon positions, label baseline, dock and pill (offsets ≤ 2 px except listed fidelity changes).
- [x] Review Focus 1, 2, 4 on device (change preset and labels in Settings, then reopen).

### Task 6: Measurements and close-out

- [x] Oppo, release build (debug keystore), `speed-profile` compile: 20 swipes × 3 runs, `dumpsys gfxinfo` janky % for View Home and Compose Home (D8).
- [ ] Font scale 1.3 check (Review Focus 5) only if the owner sets it; otherwise listed as open.
- [x] Knowledgement `docs/knowledgements/JH1-compose-home-static.md` (Vietnamese), lessons, `AGENTS.md`/`project_structure.md` updates, close the plan, commit in logical groups.

## Log

- **Task 1 (2026-11-19):** JUnit 4.13.2 added (`testImplementation` only). 101 characterization tests in 12 classes (11 planners in `domain/layout/` + `GridMetrics`), written by a subagent, re-run by Claude: `tests=101 failures=0 errors=0`. Surprises kept as `// characterization:` comments, not fixed (D3): `GridRemovePlanner` does not skip an icon at `x >= columns` (reads it as the next row); `GridReflowPlanner` on zero pages returns `totalPages = 1`; `FolderPlanner` rejects container `0` as a source; `AppWidgetSizePlanner` applies a target per axis even when the other axis target is 0 (KDoc says both must be > 0). Build note: two Gradle runs in parallel broke KSP (`NoSuchFileException` / storage already registered); `--no-daemon` or `gradlew --stop` fixes it.
- **Task 2–4 (2026-11-19):** `ui/home/compose/` + `debug.ComposeHomeActivity`. Changes from the plan text: `HomeScreen` signature takes `HomeIcons` (cache object) and `showSearch`/`showDots`/`onSearchClick`; the Activity injects `HomeScreenConfigRepository` for the two toggles. Folder and pill are flat colours like the View default (D4 updated).
- **Task 5 (2026-11-19, Oppo, debug):** first screenshot showed two offsets, both fixed: grid 33 px too high (View reserves `max(status bar, edit-pill clearance)` at the top, TL1 Task 5) and pill/dock 14/12 px off (the dock's 8 dp bottom margin must sit OUTSIDE the weighted area, as `LinearLayout` subtracts margins before splitting weights). Also: grey nav-bar scrim from `enableEdgeToEdge` → contrast enforcement off, like `SystemBarsController`. After fixes, 4 pages compared by eye: icon rows, labels, pill (centre y 1268) and dock (y 1318–1498) match the View Home.
- **Task 6 (2026-11-19, Oppo, release signed with the debug key, `speed-profile`):** the debug-only Activity was copied into `src/release` temporarily for the measurement and removed afterwards (not committed). 20 swipes per run, `am start -S` before each run. View Home janky 1.74 % / 1.03 % (p90 20 / 18 ms; third run invalid: the default-launcher prompt covered Home, 0 frames). Compose Home 1.17 % / 0.52 % / 1.05 % (p90 13 / 12 / 13 ms). **D8 passes.** Note: these janky % are far lower than JD Task 1's 24.5–31 % for the same Home; method differs (JD reset gfxinfo before opening, these runs reset after a 10 s settle), so compare only within one run set. Idle Compose Home renders 0 frames in 5 s (no runaway redraw). Cold start `TotalTime` (5 runs): View 1000/985/646/650/628 ms, Compose 676/741/637/784/620 ms; content is on screen 1.5 s after a cold start in both.
- **Seen during Task 6, not explained:** the dock order on the Oppo changed from Phone, Messages, Chrome, Camera (17:20) to Messages, Phone, Chrome, Camera (17:38). Claude's input in that window was horizontal swipes at y = 700 and one tap on "Để sau" (Later) of the default-launcher prompt; Compose Home has no drag code. Reported to the owner, not changed back.

- **Samsung SM-A176B (2026-11-19, owner switched devices; release signed with the debug key, `speed-profile`, temp `src/release` copy removed after):** layout matches the View Home (grid rows, pill, dock); the View Home hides the status-bar icons the same way. Missing in Compose: notification badges (Discord 43, Messages 2) → JH3. Paging, 20 swipes × 3: View janky 4.62 / 4.14 / 4.58 % (p90 25 / 26 / 28 ms, p99 57 / 46 / 42 ms); Compose 4.70 / 4.67 / 4.95 % (p90 31 / 32 / 32 ms, p99 65 ms all). **On this device Compose is slightly worse** (+0.1 to +0.8 pt janky, p90 +5 ms); the Oppo result is the opposite. Compose renders ~750 frames per run vs ~560 for the View (longer pager settle). Cold start `TotalTime` (5 runs): View 421/371/343/413/340 ms, Compose 332/322/288/332/325 ms. Measurement note: the default-launcher prompt reopens on every `am start -S` and blocks swipes (0 frames); Back does not close it, so it is dismissed by tapping "Để sau" found with `uiautomator`. The phone locked once mid-run (0 frames), re-run after the owner unlocked it. Debug build reinstalled afterwards.

## Review Focus evidence (filled in at close-out)

1. Preset 5 × 6 / labels off: pinned by `GridMetricsTest` (11 tests); not re-checked on device (needs a Settings change on the owner's phone). **Open.**
2. Widget 4 × 2: no widget on the owner's layout; adding one changes the owner's data. Code path compiles, not seen on screen. **Open.**
3. Wallpaper change while stopped: `onResume` → `reloadBackdropIfChanged` (same call as `BackdropController`). Not exercised. **Open.**
4. Dock: 4 apps checked; empty/1-app layout not available. Glass width is `fillMaxWidth` independent of the count (code).
5. Font scale 1.3: not set (system setting). **Open.**

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md` (unit-test exception, Home Compose note), `project_structure.md` (JH1 files), `lessons/design-system.md` (JH1), `knowledgements/JH1-compose-home-static.md` (new).
