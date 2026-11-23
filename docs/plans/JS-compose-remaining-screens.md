# JS — Remaining screens in Compose (step 4 of direction B) — Umbrella Plan, with JX sketch

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19: run every Task of a sub-plan, stop at the end of each sub-plan). Log per Task. Devices: Samsung SM-A176B (owner's daily phone; back up app data first, JH2 lesson) and Oppo PKD130 (low-end, perf numbers). Commands for PowerShell. Language: English (Compose-chain exception in `AGENTS.md`).

**Started:** 2026-11-20 · **Plan approved:** 2026-11-20 (owner: "chuyen het sang compose, chay het plan")
**Status:** In progress — JS1–JS6 and JS7a coded and committed (compile + lint + unit tests), **none device-checked yet (no device attached on 2026-11-20)**. JS7b (Control Center panel) and JX not started: waiting for a device check of the Home path first.
**Devices:** Samsung SM-A176B + Oppo PKD130 (every list screen is measured on the Oppo) · **Room schema change:** none

**Goal:** Every screen of OS26 Launcher that still draws with View/XML is drawn with Compose and the Compose design system (`core/designsystem/compose`), with Backdrop glass where JD D9 allows it. After JS, plan JX can delete the View UI layer: 54 layout XML files, the View Home, the View design system, QWEA0 glass and the View-only dependencies.

**Architecture:** Same recipe as JC/JD/JH: replace rendering only, keep `data/`, `domain/`, `di/` and the ViewModels unchanged where they are already UI-agnostic. Each screen moves in place: same Activity class and manifest entry (no component renames, JH4 lesson), `setContent { DsScreen(...) }` instead of `setContentView`. Screens that live inside Home (search, App Library, sheets) move **into the Home Compose tree**, so they share Home's `layerBackdrop` (real glass) and the `addContentView` / `dispatchTouchEvent` bridges of JH3a/JH3c go away.

**Tech Stack:** Kotlin 2.4.10, Compose BOM `2026.09.00`, Backdrop 2.0.1. **No new dependency** expected (`LazyColumn`, `LazyVerticalGrid`, `HorizontalPager` are in foundation). No Material3.

**Spec:** JC roadmap row "JS"; JD components; JH1–JH4 knowledgements; screens in `Figma-Draft.md` §3 where they exist; current behaviour of each View screen (read from code at the start of each sub-plan). Owner direction: closer to iOS 26 than the XML, not just parity (memory `ios-fidelity-goal`).

---

## Inventory (read from code 2026-11-20)

| Area | View code today | Layout XML | Lines (approx.) | Users |
|---|---|---|---|---|
| Settings frame | `SettingsActivity` + 20 `*SettingsScreen` (row model `SettingsRow`, 19 row types), `SettingsAdapter`, `SettingsCardDecoration`, `SettingsDialogs`, `RateAction`, `RenameAppsSettingsScreen` dialogs | `activity_settings`, 15 `item_settings_*`, `dialog_rate`, `dialog_rename_app` | 5.9k in `ui/settings` (screens/ViewModels stay) | Settings hub and every sub-screen |
| Settings frame, other hosts | `ControlCenterSettingsActivity` (same adapter), `GlassTuningActivity` (+ `GlassPreviewView`, QWEA0 preview) | `activity_glass_tuning` | 0.5k | CC settings, Liquid Glass tuning |
| Hidden apps | `AppLibraryActivity` + `AppLibraryAdapter` | `activity_app_library`, `item_app_library_*` | 0.3k | Settings → hidden apps |
| CC sub-settings | `CustomizeTilesActivity` (RecyclerView + `ItemTouchHelper` reorder), `EdgeSettingsActivity`, `NightShiftSettingsActivity`, `CcForm` — built in code with Material views | none | 0.7k | CC settings |
| Search | `SearchOverlayView`, `SearchResultAdapter`, `SpotlightCardDecoration`, `SearchDismissGesture` (`SearchViewModel` stays) | `view_search_overlay`, `item_search_*` | 0.9k | Home (`addContentView`) |
| App Library | `CategoryPageView`, `CategoryGroupPanelView`, `LibraryListAdapter`, `LetterIndexView`, `AppLibraryMotion`, `LibraryOverlayController`, `ComposeLibraryHost` | `view_category_page`, `item_category_group*`, `item_library_list_*` | 1.7k | Home (`addContentView` + touch bridge) |
| Home sheets and dialogs | `WidgetPickerController` + `AppWidgetSection` (picker sheet), `WeatherSheetController` + `CitySearchDialog`, Material dialogs in `HomeActivity` (wallpaper, remove widget) and `RemoveAppConfirm` | `view_weather_sheet`, `item_weather_*`, `dialog_city_search` | 1.1k | Home |
| Onboarding | `OnboardingActivity`, `OnboardingPages`, `ThemeCardView`, `DsStepIndicatorView`, `DsOnboardingTopBar` | `activity_onboarding`, `onboarding_*` (5), `view_onboarding_theme_card` | 0.7k | First run |
| Photo widget | `PhotoWidgetActivity`, `PhotoCropView` (custom crop gesture), `PhotoGridAdapter` | `activity_photo_widget`, `item_photo_widget_thumb` | 0.6k | Photo widget config |
| Control Center | `ui/controlcenter/*` panel built in code inside a `TYPE_APPLICATION_OVERLAY` window of `ControlCenterService` (CG glass, commit b664a06) | none | 3.2k (panel only) | Edge swipe overlay |
| Old Home (→ JX) | `ViewHomeActivity`, 12 controllers in `ui/home/controller`, `ui/home/view/*`, `IconActionMenu` | `activity_home`, `view_icon_action_menu_item` | 9k+ | Debug fallback only |

`ds_*` layouts (6) and `core/designsystem/*.kt` View classes (≈2k lines) are removed in JX once nothing uses them.

## Why this design

- **Settings is a row model, not a set of layouts.** All 20 settings screens already produce `List<SettingsRow>` and receive callbacks through the `SettingsScreen` interface; only `SettingsAdapter` + the decoration turn rows into Views. A Compose renderer for the 19 row types replaces 759 + 180 lines and moves every settings screen at once, without touching screen logic. Dialog code in the screens (DsDialog View) is the part that does need edits.
- **Screens inside Home go into the Home tree, not into Compose windows.** A Compose `Dialog`/sheet is its own window: no real glass (JD lesson) and a touch stream that cannot reach `HomeDragState` (JH4 D5, why picker cards cannot be dragged today). Drawn inside `HomeScreen` above the pager, search, App Library and the widget picker get real Backdrop glass over the live Home, and card drag from the picker becomes an ordinary root gesture.
- **One sub-plan per area**, each closed and committed before the next (continuous mode). Each sub-plan is small enough to review and to back out with `git revert`.
- **No per-screen debug fallback.** Only Home kept a View fallback (JH4) because it is the default launcher. Other screens are replaced in place; git history is the fallback (Q8).

| Option for the whole step | For | Against | Pick |
|---|---|---|---|
| A. Umbrella plan + sub-plans JS1–JS7, each written in detail when it starts | Detail is written against the code as it is then; matches JH1–JH4 practice | More plan files | **Yes** |
| B. One plan with every Task now | One approval | 60+ Tasks written before the code they depend on exists; stale by JS3 | No |
| C. Keep low-traffic View screens (photo widget, CC sub-settings) and stop at "most screens" | Less work | JX cannot remove View DS, Material, RecyclerView, ViewBinding; two UI stacks forever | No |

**Accepted trade-offs:** the Settings frame switches all 20 screens at once (bigger review, but one renderer); during JS the APK carries both stacks (removed in JX).

## Sub-plans (proposed order)

| # | Sub-plan | Scope | Key risks / checks | Estimate |
|---|---|---|---|---|
| JS1 | **Settings frame** | Compose `SettingsList` rendering all 19 `SettingsRow` types in `DsScreen` (`LazyColumn`, one `DsCardGroup` glass card per group, sticky nothing); `SettingsActivity`, `ControlCenterSettingsActivity`, `GlassTuningActivity` switch to it; `GlassPreviewView` → Compose preview with Backdrop; `SettingsDialogs`, rate and rename dialogs → `DsAlertDialog`; `AppLibraryActivity` (hidden apps) | Scroll janky on the Oppo for the longest lists (Rename 100+ apps, Language) vs today's RecyclerView (gate: ≤ +5 pts); `ActivityResult` launchers still registered in `onCreate` (G2d rule); TalkBack roles; largest font | 1–1.5 weeks |
| JS2 | **Search** | Search overlay as a Compose layer inside `HomeScreen` (pill → field, results list, Spotlight cards, web row, contacts, dismiss gesture), real glass from Home's backdrop; `addContentView` removed | IME + Back order (JH3a: first Back closes the keyboard); dismiss drag vs list scroll; first-frame latency on the Oppo | 1 week |
| JS3 | **Home sheets and dialogs** | Widget picker as an in-tree sheet with card drag into `HomeDragState` (closes JH4 D5); weather sheet + city search; Material dialogs → `DsAlertDialog` | Drag from sheet to grid (manual check required); sheet drag-to-dismiss vs inner scroll | 1 week |
| JS4 | **App Library** | Category page (`LazyVerticalGrid` of category groups, 2×2 mini grids), group panel, A–Z list with letter index, open/close motion and Back-swipe (LB), search inside Library; `ComposeLibraryHost`, `LibraryOverlayController` touch bridge removed | Largest View area left; motion fidelity (AP, LB plans); scroll janky with many glass group cards (D9: cards only); paging between Home and Library | 1.5–2 weeks |
| JS5 | **Onboarding** | 5 pages in a `HorizontalPager`, step indicator, top bar, theme card (reuse JC `ThemeCard`), wallpaper and default-launcher pages | First run path from Home (JH4 cover/uncover), role dialog result | 0.5–1 week |
| JS6 | **Photo widget** | Grid of picked photos, crop editor (pan/zoom gesture in Compose), photo picker routes | Crop gesture (manual check required); output bitmap identical to today's crop math (reuse the math, not re-derive) | 0.5–1 week |
| JS7 | **Control Center** | Panel drawn by a `ComposeView` inside the overlay window (own `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` bound to the service), tiles, sliders, media, connectivity block, detail sheets, open/close motion (CG); CC sub-settings (Customize tiles with drag reorder, Edge, Night Shift) as `DsScreen`s | Lifecycle of Compose in a service window (leaks when the window is removed); edge gesture stays a View (`EdgeTriggerView`, plain touch strip) or moves too (Q3); cross-window blur from CG kept; open-motion frame time on the Oppo | 1.5–2 weeks |

Total: **7–10 weeks**. JX after JS7 (≈1 week).

Order rationale: JS1 first because it touches no Home code and proves `LazyColumn` + card glass on long lists on the Oppo, which JS2/JS4 depend on. JS2 and JS3 remove the Home interop bridges in small steps before the big Library move (JS4). JS5–JS6 are isolated. JS7 last: it has its own window and lifecycle model, and CG just shipped on View.

## Decisions (proposed, need approval)

| # | Decision | Reason |
|---|---|---|
| D1 | Activities keep their class names and manifest entries; only their content changes | Shortcuts, intents and the HOME component keep working (JH4 lesson) |
| D2 | `SettingsScreen` / `SettingsRow` stay the contract; the renderer changes. Screens' dialog code moves to a small Compose dialog host the frame owns (state in the frame, not in View fields) | Reuses 20 screens unchanged except dialogs |
| D3 | Home-hosted UI (search, Library, picker, weather sheet, Home dialogs) is drawn inside `HomeScreen`, not in Compose windows | Real glass + one gesture root (JH2 lesson) |
| D4 | Glass policy JD D9 everywhere: real Backdrop only on `CARD`/`SHEET`; controls static | Oppo janky numbers (JD) |
| D5 | iOS fidelity changes beyond parity are listed per sub-plan and shown to the owner at its end | Owner direction; same as JD F1–F7 |
| D6 | No new user-visible strings unless a sub-plan says so; any new string goes into all 9 `strings.xml` + `lintDebug` | `AGENTS.md` LG rule |
| D7 | No change under `domain/` and `data/`; ViewModels change only where they hold View types | Direction B claim, kept since JC |
| D8 | View files that become unused during JS are **not** deleted in JS; JX lists them for approval | Project rule "no deleting files without approval" |

## Questions for the project owner

| # | Question | Recommendation | Answer |
|---|---|---|---|
| Q1 | Structure: umbrella plan + sub-plans JS1–JS7 written one at a time (option A)? | Yes | Approved as recommended (2026-11-20) |
| Q2 | Order JS1 Settings → JS2 Search → JS3 Home sheets → JS4 App Library → JS5 Onboarding → JS6 Photo → JS7 Control Center? | Yes; tell me if a screen matters more to you (e.g. Library first) | Approved as recommended (2026-11-20) |
| Q3 | Control Center: move to Compose (JS7, after CG just shipped on View), or keep it as View and leave `ui/controlcenter` out of JX? | Move it (JS7), last. Keep `EdgeTriggerView` as a plain View: it is a transparent touch strip with no UI | Owner: move everything to Compose, including CC (2026-11-20) |
| Q4 | Perf gate for list screens: Oppo scroll janky ≤ today's View screen + 5 pts (JC Q2 style). If a screen fails, stop that sub-plan and report? | Yes | Approved as recommended (2026-11-20) |
| Q5 | iOS fidelity changes (D5): allowed per sub-plan without asking first, reviewed at its end? (e.g. Settings rows with iOS inset-grouped spacing, search results with spring) | Yes, listed in each sub-plan's report; you can reject any | Approved as recommended (2026-11-20) |
| Q6 | Continuous mode: run each sub-plan end to end (plan, Tasks, device check, commit) and stop only at its end? Commits by me on `feat/compose-pilot`? | Yes, as in JH; I commit only if you say so in this approval | Owner: run the whole plan (2026-11-20); commit per sub-plan as in JH |
| Q7 | Devices: Samsung allowed for every sub-plan (data backed up and restored byte-identical, as in JH4)? Oppo for every janky number? | Yes | Approved as recommended (2026-11-20) |
| Q8 | No View fallback for non-Home screens (replace in place; git revert is the fallback)? | Yes | Approved as recommended (2026-11-20) |
| Q9 | JX: delete the View Home right after JS (app not published yet), or keep `ViewHomeActivity` for one release as the JC roadmap said? | Delete in JX: no release has shipped with the Compose Home, so "one release" has no users to protect | Approved as recommended (2026-11-20) |

## Out of scope

- Widget renderers (`ui/widget/*Renderers`) draw on Canvas and are already reused by Compose (`WidgetRenderer`); they stay.
- Hosted third-party app widgets stay `AppWidgetHostView` (a View by platform design), embedded with `AndroidView` (JH3c).
- Notifications (`ui/weather/notify`) use `RemoteViews`, not app UI.
- Samsung paging optimisation and the two "seen once" JH4 issues: own follow-up plan (JH4 D5), not JS — except picker card drag, which JS3 closes.
- CPU blur glass for API < 31 (JC Q5): unchanged; static surface.

## Global Constraints

- `minSdk 26`, `compileSdk`/`targetSdk 37`, Kotlin 2.4.10, Compose BOM `2026.09.00`, Backdrop 2.0.1; no Material3; no new dependency without asking.
- Touch targets ≥ `ds_touch_min` (48 dp). TalkBack roles like the View version. Largest font + Bold text do not clip.
- Every sub-plan: `gradlew.bat assembleDebug`, `lintDebug` clean, `:app:testDebugUnitTest` green, device check, Log line per Task, knowledgement at close (`Workflow.md` §6), lessons copied to `docs/lessons/`.
- Config keys: none expected; any change follows `config-schema.md` in the same commit.
- Commit messages in English, no Vietnamese diacritics.

## Review Focus (whole JS)

1. Every settings screen opens, scrolls, and every row type behaves as before (switch, radio, segmented, slider, value, search, wallpaper grid, permission card, app rows with icons). → JS1
2. Long-list scroll on the Oppo within the gate. → JS1, JS4
3. Search, Library, picker and sheets drawn inside Home: real glass, Back order unchanged (`HomeBackController`), no touch reaching Home under an open layer. → JS2–JS4
4. Widget picker card drag lands on the grid. → JS3
5. First run onboarding from a fresh install, ends on Home. → JS5
6. Control Center: open/close motion, every tile, no window or lifecycle leak after 20 open/close cycles (`dumpsys meminfo` views/activities count). → JS7

---

## JX sketch (cleanup, written in detail after JS7)

**Goal:** One UI stack. Remove everything View-only that JS left unused, verify the release build and size.

Candidate removals (each listed with its reason and approved before deletion, D8):
- Old Home: `ViewHomeActivity`, `ui/home/controller/*` (except pieces Compose still imports: `RecreateGuard`, `IconMenuPlacement`, `IconMenuWidth`, `DefaultLauncherPromptController`, `confirm*` helpers, `AppWidgetPickerDeps` — moved or kept), `ui/home/view/*`, `HomePagerAdapter`, `TunedPagerSnapHelper`, `PagerFlipAnimator`, `debug/HomeModeDebugReceiver` + pref, manifest entry.
- Layouts: all 54 files in `res/layout` (expected to reach zero); unused `drawable`/`dimen`/`color` resources found by `lintDebug` `UnusedResources`.
- View design system: `core/designsystem/*.kt` View classes and `ds_*` layouts. Tokens (`res/values/ds_*.xml`), `DsSurfaceDrawable`, `DsCorners`, `DsTileDrawable`, `AuroraBackgroundDrawable` stay if Compose still draws through them.
- QWEA0 glass: `GlassSurfaceFactory`, `RealGlassSurface`, `StaticBlurSurface` and the parts of `GlassEnvironment` only View glass uses; dependency `com.github.QWEA0:liquidglass` and the JitPack repository in `settings.gradle.kts`.
- Dependencies: `material`, `constraintlayout`, `viewpager2`, `recyclerview`, `viewBinding`. **Kept:** `appcompat` (per-app language through `AppCompatDelegate` + `AppLocalesMetadataHolderService`) unless JX finds a replacement. Theme parent `Theme.Material3.DayNight.NoActionBar` → an AppCompat or platform `NoActionBar` theme (checks: splash, system bar colours, dialogs).
- Debug probes that only served the migration: `ComposeHomeProbeActivity`, `DsCatalogActivity` (View catalog); `DsComposeCatalogActivity` stays.
- Docs: `AGENTS.md` build section (one stack), `project_structure.md`, `LiquidGlass-Android.md` (QWEA0 history).

JX checks: release build with R8 (keep rules for JNI of QWEA0 removed), APK size before/after, cold start on the Oppo (`startup-performance.md` method), full manual pass of Review Focus above on the Samsung, default-launcher check on the Oppo (JH4 RF1, still open).

## Log

| Date | Task | Work | Notes |
|---|---|---|---|
| 2026-11-20 | Plan | Inventory from code; umbrella plan JS1–JS7 + JX sketch written | Approved same day: move everything to Compose, run the whole plan |
| 2026-11-20 | JS1 | Settings frame in Compose: `ui/settings/compose/SettingsList.kt` (LazyColumn, one `dsGlass(CARD)` per visible group placed in the layout phase from `layoutInfo`, rows via `DsRow`), `SettingsCards.kt` (hero card, weather preview, wallpaper grid, permission card, badge/library style, transition preview, segmented, slider); `SettingsActivity`, `ControlCenterSettingsActivity`, `GlassTuningActivity` (Backdrop preview + `LocalGlassLookOverride`), `AppLibraryActivity` (hidden apps) use it. Dialogs: `core/designsystem/compose/DsDialogHost.kt` (`activity.dsDialogs`, handle with synchronous dismiss listener), `SettingsDialogs` rebuilt on `DsAlertDialog`/`DsBottomSheet` (positive button now optional), rename dialog on new `DsTextField`, rate dialog with Compose stars, CC background/recorder pickers. New: `DsSpinner`, `drawableBackground`/`DrawableBox` (XML shape drawables), `ui/wallpaper/compose/WallpaperTile`. `DsCorners.radiusPx` takes an optional `look`. | Compiles; no device attached (adb empty), device check batched for later. Detailed sub-plans are kept as Log entries in this umbrella file instead of separate plan files (less overhead). Order change: JS4 (Library) before JS2 (Search), because Library-mode search must draw above the Library: while the Library is a View on top of the ComposeView, a Compose search layer would sit under it. View Home fallback (`ViewHomeActivity`) no longer shows the default-launcher prompt (its dialogs need a Compose host); acceptable until JX removes it. |
| 2026-11-20 | JS4 | App Library in Compose: `ui/home/compose/library/HomeLibrary.kt` (blurred-wallpaper background recorded as the Library's own backdrop, 2-column group tiles with real `CARD` glass for the first 8, 2 × 2 big icons + mini cluster, A–Z list with letter index, full-screen group panel growing from the tapped tile with the AP spring, Library behind scales to 0.85 and fades) and `HomeLibraryState.kt` (replaces `ComposeLibraryHost`; keeps `LibraryOverlayController` for the swipe, called from `dispatchTouchEvent`). Drawn in `HomeActivity` above `HomeScreen`; the hidden `backdrop_image` View and the four `addContentView` Views are gone. | Compiles; no device yet. Background blur is a 48-px downscale drawn back up (cheap on every API) instead of `BlurredBackdropCache`. `ComposeLibraryHost.kt` is now unused (JX deletion list). |
| 2026-11-20 | JS2 | Search in Compose: `ui/search/compose/SearchOverlay.kt` (Spotlight: segments for headers / app grid / web rows, cards while typing, field above the IME, capacity via `SearchCapacity` from the measured list height, swipe up 300 px dismisses; Library mode: field on top, `LazyColumn`, keyboard hides on scroll; contact call/message and prompt dismiss buttons). Drawn last in `HomeActivity`'s Box; Home + Library behind get a `BlurEffect` (24 dp × progress, API 31+; below 31 the overlay draws a blurred wallpaper). `addContentView(searchView)` removed. | Compiles; no device yet. `SearchOverlayView`, `SearchResultAdapter`, `SpotlightCardDecoration`, `SearchDismissGesture` now unused (JX list). |
| 2026-11-20 | JS3 | Home sheets and dialogs in Compose: `ui/home/compose/HomeWidgetPicker.kt` (DsBottomSheet; built-in previews drawn with the real `WidgetRenderer` on a Compose canvas, app-widget section with collapsible rows, previews and size chips via `AppWidgetSizePlanner`), `ui/home/compose/RemoveAppDialog.kt` (light iOS card, blur behind on API 31+; `RemoveAppConfirm` now calls it), `WeatherSheetController` and `CitySearchDialog` rebuilt on `DsBottomSheet` / `DsAlertDialog` (radio card, manage button, attribution link; search field + button + result rows), wallpaper-target and remove-widget dialogs of `HomeActivity` on `dsDialogs`. | Compiles; no device. Not done: dragging a card from the picker onto the grid (needs a new-widget session in `HomeDragState`; JH4 D5 stays open, follow-up). The picker sheet no longer re-hides the status bar of its own window in edit mode (C19) — check on device. `WidgetPickerController`, `AppWidgetSection`, `WidgetPreviewView`, View `RemoveAppDialog` now only used by the View Home (JX). |
| 2026-11-20 | JS5 | Onboarding in Compose: `ui/onboarding/compose/OnboardingScreen.kt` (aurora cross-fade per step recorded as backdrop, top bar with back pill / language chip / skip, `AnimatedContent` slide 30 % + fade, step indicator with animated wide dot, footer buttons; pages 0.1–0.5 incl. theme cards reused from screen 1.1, phone mock with real glass dock over the chosen wallpaper, swatch strip, numbered steps card, summary card group). `OnboardingActivity` hosts it; taps are ignored for 280 ms after a step change (was: while the ValueAnimator ran). New `DsClockText` replaces `TextClock` (also in the 1.1 theme card preview). | Compiles; no device. `OnboardingPages.kt`, `ThemeCardView`, `DsOnboardingTopBar`, `DsStepIndicatorView` now unused (JX list). |
| 2026-11-20 | JS6 | Photo widget screen in Compose: `PhotoWidgetActivity` on `DsScreen` (3-column `LazyVerticalGrid` of glass thumbnails with remove buttons + "Add" cell, empty state with button, spinner while importing, rotate hint), crop panel with `ui/photo/compose/PhotoCropper.kt` (`PhotoCropState` keeps `PhotoCropView`'s matrix math verbatim; pan + pinch via pointer events; crop reported on lift), Material dialogs → `SettingsDialogs.confirm`. Thumbnail cache moved into the Activity. | Compiles; no device. Was a light Material3 screen, now DS3 dark like Settings (iOS fidelity). `PhotoGridAdapter`, `PhotoCropView` unused (JX list). |
| 2026-11-20 | JS7a | CC sub-settings in Compose: `CustomizeTilesActivity` (DsScreen + LazyColumn, "in use" rows on glass with drag-handle reorder that swaps past half a row height and saves on release, add/remove buttons, app icons), `EdgeSettingsActivity` (radio rows, sliders, colour swatches, offset group only for side strips), `NightShiftSettingsActivity` (toggles, warmth slider, from/to rows with a Compose hour/minute stepper dialog instead of `TimePickerDialog`); helpers in `CcSettingsCompose.kt`. New string `cc_time_done` in all 9 `strings.xml` (value copied from each locale's `ph_crop_done`). | Compiles; no device. `CcForm.kt` unused (JX list). Reorder needs a manual drag check (`Workflow.md` §3). |

## JX candidates found during JS (2026-11-20, not deleted — needs the owner's approval, Workflow rule)

No longer used by any Compose screen; still compiled because the View Home (`ViewHomeActivity`) or other View-only files reference them, or simply dead:
- Settings: `SettingsAdapter.kt`, `SettingsCardDecoration.kt`, `GlassPreviewView.kt`, layouts `activity_settings`, `activity_glass_tuning`, 15 × `item_settings_*`, `dialog_rate`, `dialog_rename_app`; `ui/library/AppLibraryAdapter.kt`, `activity_app_library`, `item_app_library_*`; `ui/wallpaper/WallpaperTileView.kt`.
- Library: `ui/home/ComposeLibraryHost.kt`; View Library classes stay until the View Home goes (`CategoryPageView`, `CategoryGroupPanelView`, `LibraryListAdapter`, `LetterIndexView`, `view_category_page`, `item_category_group*`, `item_library_list_*`).
- Search: `SearchOverlayView.kt`, `SearchResultAdapter.kt`, `SpotlightCardDecoration.kt`, `SearchDismissGesture.kt` (still used by the View Home's `SearchOverlayController`), `view_search_overlay`, `item_search_*`.
- Home sheets: `WidgetPickerController.kt`, `AppWidgetSection.kt`, `WidgetPreviewView.kt`, View `RemoveAppDialog.kt` (View Home only), `view_weather_sheet`, `item_weather_*`, `dialog_city_search`.
- Onboarding: `OnboardingPages.kt`, `ThemeCardView.kt` (+ `RatioFrameLayout`), `activity_onboarding`, `onboarding_*` (5), `view_onboarding_theme_card`; View DS `DsOnboardingTopBar`, `DsStepIndicatorView`.
- Photo: `PhotoGridAdapter.kt`, `PhotoCropView.kt`, `activity_photo_widget`, `item_photo_widget_thumb`.
- CC settings: `CcForm.kt`.
| 2026-11-20 | Device check (Samsung SM-A176B, debug, data backed up and restored byte-identical) | Passed: Settings hub / General / grid dialog / Liquid Glass / Glass tuning (preview follows the slider live), default-launcher prompt, Spotlight search (empty + typing, Back twice), App Library + group panel, Library-mode search, weather sheet, icon menu → remove-app dialog, widget picker (built-in previews + app-widget section with size chips), Onboarding 0.1–0.5, CC settings, tile reorder by handle drag, swipe strip, Night Shift. No crash in logcat. Fixed on device: (1) Library/group/search blurred wallpaper showed pixel blocks → 96 px downscale + `FilterQuality.High` + `wallpaperSoftBlur()` (24 dp `blur` on API 31+); (2) Library-mode search opened without the keyboard because `scrollToItem(0)` sets `isScrollInProgress` → hide only on `DragInteraction.Start`; (3) widget picker sheet let the status bar cover the edit-mode toolbar (C19) → hide status bars of the sheet's dialog window in edit mode. | Not checked: photo widget screen (no photo widget on the test Home), rename / rate dialogs, city search dialog, Oppo numbers. Note: Back on the Compose Home root finishes the Activity while the app is not the default launcher (by design, JH3a) — test scripts must not press Back there. |
| 2026-11-20 | JS7b | Control Center panel in Compose (ported by a subagent, reviewed and device-checked here): `ui/controlcenter/compose/` — `ControlCenterPanel` (state holder with the old `ControlCenterView` API; `Listener`/`DismissDrag` moved here; paging/drag logic; `CcBlockAnim` driven by ValueAnimator for `ControlCenterMotion`; `PanelOwner` = Lifecycle/SavedState/ViewModelStore owner; `PanelComposeView` = `AbstractComposeView` with own Recomposer + pausable frame clock so the composition survives the window being removed/added on every pull; RESUMED on attach, CREATED on detach, DESTROYED in `ControlCenterOverlay.destroy()` from the service teardown / locale rebuild), `ControlCenterScreen`, `CcControls`, `CcMedia`, `CcParts`; `PanelContract.kt` holds `MediaActions` + `CustomTileType` helpers. `EdgeTriggerView` and `NightShiftOverlay` stay Views (owner, Q3). Owner on device: background too transparent → panel backdrop blur 24 → 48 dp (API 31+), cross-window blur (background "None") `cc_blur_behind_radius` 36 → 56 dp; owner: "vừa rồi". | Samsung: main page, media page, network page, big brightness slider, timeout picker, vertical paging (slider drags do not page), Back order detail → main → close, re-open by the owner; no crash, no error in logcat. Approximations (subagent report): hand-drawn seek bar, press highlight instead of ripple on media buttons, `basicMarquee` speed, system-cancelled touch treated as release. Not checked: Oppo first-open cost, language rebuild leak. Old CC View files now unused (JX list: `ControlCenterView`, `PanelParts`, `MediaViews`, `ControlSliderView`, `ControlTileView`, `ConnectivityBlockView`, `CcGlassDrawable` is still used). A test tap of mine landed on the owner's Samsung Home (panel already closed); nothing changed, airplane mode verified off. |
