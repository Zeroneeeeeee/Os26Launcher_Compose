# JX — Remove the View UI layer (step 5 of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain. **Deleting files needs the owner's approval** (`AGENTS.md`): this plan lists every file; nothing is deleted before the owner approves the list. Devices: Samsung SM-A176B (data backed up first), Oppo PKD130 for the size/start numbers. Language: English (Compose-chain exception).

**Started:** 2026-11-20 · **Plan approved:** 2026-11-20 (owner: "Duyệt như khuyến nghị")
**Status:** Done (2026-11-20)
**Room schema change:** none

**Goal:** One UI stack. Delete the View Home, every View screen class and layout that JS replaced, the View design system, the QWEA0 glass library and JitPack, and the View-only dependencies (Material, RecyclerView, ViewPager2, ConstraintLayout, ViewBinding). Keep behaviour identical.

## How the list was made (measured 2026-11-20)

1. A static reference scan (comments stripped) from the manifest components and `data/`, `domain/`, `di/`.
2. **Proof build in a throw-away git worktree** (scratchpad, removed afterwards; the real checkout was not touched): the candidates below deleted, manifest entries removed, dependencies removed, theme parents switched. Result: `assembleDebug`, `lintDebug`, `testDebugUnitTest` all green after the small code edits listed in "Required edits".
3. Debug APK size: **26,672,871 B → 20,788,088 B (−5.9 MB, −22 %)**. Release not measured yet (Task 4).
4. `lintDebug` `UnusedResources` after the deletions: **114 resources** (18 color, 35 dimen, 22 drawable, 1 font, 1 layout, 20 string, 17 style). Removing them may reveal a second, smaller round.

## Deletion list (needs approval)

### A. Kotlin — old Home (View) — 47 files
- `ui/home/ViewHomeActivity.kt`, `ui/home/ComposeLibraryHost.kt`, `ui/home/HomePagerAdapter.kt`, `ui/home/PagerFlipAnimator.kt`, `ui/home/SearchSwipeDetector.kt`, `ui/home/TunedPagerSnapHelper.kt`
- `ui/home/view/` — all 28 files **except** `NotificationBadges.kt`, `PageDotsLayout.kt`, `AppLibraryMotion.kt` (still used by Compose; moved, see edits): `BackgroundGlass`, `BehindBlur`, `BlurredSnapshotScrim`, `BlurredWallpaperScrim`, `CategoryGroupPanelView`, `CategoryPageView`, `DockView`, `DragLayer`, `FolderPanelView`, `GapLongPressGesture`, `IconActionMenu`, `IconCellView`, `IconDragGesture`, `IconGridView`, `IconMenuBackdrop`, `IconMenuMotion`, `IconPress`, `Jiggler`, `LeavingIcons`, `LetterIndexView`, `LibraryListAdapter`, `PanelMotion`, `PressScale`, `RemoveAppDialog`, `SearchPillView`
- `ui/home/controller/`: `AppLibraryController`, `BackdropController` (after moving `WallpaperTarget.labelRes()` out), `DockController`, `FolderPanelController`, `HomeBackController`, `HomePagerController`, `HomeScreenState`, `HomeTogglesController`, `HomeTouchRouter`, `IconMenuController`, `NotificationBadgesController`, `SearchOverlayController`, `SystemBarsController`, `WidgetPickerController`, `WindowSizeRecovery`. **Kept:** `DefaultLauncherPromptController`, `IconMenuPlacement`, `IconMenuWidth`, `RecreateGuard`, `RemoveAppConfirm`.

### B. Kotlin — View screens replaced by JS — 21 files
- Search: `ui/search/SearchOverlayView.kt`, `SearchResultAdapter.kt`, `SpotlightCardDecoration.kt`, `SearchDismissGesture.kt`
- Settings: `ui/settings/SettingsAdapter.kt`, `SettingsCardDecoration.kt`, `GlassPreviewView.kt`; `ui/library/AppLibraryAdapter.kt`; `ui/wallpaper/WallpaperTileView.kt`
- Onboarding: `ui/onboarding/OnboardingPages.kt`, `ThemeCardView.kt` (incl. `RatioFrameLayout`)
- Photo: `ui/photo/PhotoGridAdapter.kt`, `PhotoCropView.kt`
- Widget picker: `ui/widget/WidgetPreviewView.kt`, `ui/widget/host/AppWidgetSection.kt`
- Control Center: `ui/controlcenter/CcForm.kt`, `ControlCenterView.kt`, `MediaViews.kt`, `PanelParts.kt`, `ControlSliderView.kt`, `ControlTileView.kt`, `ConnectivityBlockView.kt`. **Kept:** `CcGlassDrawable`, `PanelBackdrop` (used by the Compose panel), `EdgeTriggerView`, `NightShiftOverlay` (owner Q3).

### C. Kotlin — View design system and QWEA0 glass — 19 files
- `core/designsystem/`: `DsBadgeView`, `DsBottomSheet`, `DsButton`, `DsCardGroup`, `DsChipView`, `DsDialog`, `DsIconTileView`, `DsNavBar`, `DsOnboardingTopBar`, `DsRowView`, `DsSearchField`, `DsSegmentedControl`, `DsSliderRow`, `DsStepIndicatorView`, `DsSurfaceViews`. **Kept** (Compose draws through them): `DsSurfaceDrawable`, `DsCorner(s)`, `DsSurface`, `DsTileColor`, `DsTileDrawable`, `DsDividerDrawable`, `AuroraBackgroundDrawable`.
- `core/glass/`: `GlassSurfaceFactory`, `RealGlassSurface`, `StaticBlurSurface`, `GlassSurface`.

### D. Debug-only — 3 files + 2 layouts
- `debug/HomeModeDebugReceiver.kt` (JH4 fallback switch), `debug/DsCatalogActivity.kt` (View catalog; the Compose catalog stays), `debug/ComposeHomeProbeActivity.kt` (JC probe), `src/debug/res/layout/ds_catalog_buttons.xml`, `ds_catalog_controls.xml`, and their manifest entries.

### E. Layouts — all 54 files in `app/src/main/res/layout/`
`activity_*` (6), `dialog_*` (3), `ds_*` (6), `item_*` (31), `onboarding_*` (5), `view_*` (5) — the folder becomes empty.

### F. Resources found unused afterwards (lint) — 114, removed in Task 3 after a second lint pass; the exact list goes into the Log before deleting.

## Required edits (not deletions)

| # | Edit | Why |
|---|---|---|
| E1 | Move `NotificationBadges` (keep only `State`; drop the View cell registry and `countFor`), `PageDotsLayout` (pure math object), `AppLibraryMotion` (constants) from `ui/home/view` to `ui/home/compose` | Still used by Compose; the `view` package goes away |
| E2 | Move `WallpaperTarget.labelRes()` out of `BackdropController` into `ui/home/` | Used by `HomeActivity` |
| E3 | `HomeActivity`: remove the JH4 debug redirect to `ViewHomeActivity`, the `redirected` guard and the `jh4_debug` pref read | Fallback target is gone |
| E4 | Manifest: remove `ViewHomeActivity`; debug manifest: remove `HomeModeDebugReceiver`, `DsCatalogActivity`, `ComposeHomeProbeActivity` | Classes deleted |
| E5 | `app/build.gradle.kts`: drop `material`, `constraintlayout`, `viewpager2`, `recyclerview`, `com.github.QWEA0:liquidglass`, `viewBinding = true`; `settings.gradle.kts`: drop JitPack; `libs.versions.toml`: drop the unused aliases | No user left (proof build) |
| E6 | Themes: `Theme.Material3.DayNight.NoActionBar` → `Theme.AppCompat.DayNight.NoActionBar` (values + values-night), `Theme.Material3.Dark.NoActionBar` → `Theme.AppCompat.NoActionBar` (`ds_themes.xml`), drop Material-only items (`colorOnPrimary`, `colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `bottomSheetStyle`), drop styles `Widget.Os26.Ds.LinearProgress/CircularProgress/BottomSheet`, `TextAppearance.Os26.Ds.*` parent → `TextAppearance.AppCompat.Body1` | Material3 library removed. AppCompat stays (per-app language via `AppCompatDelegate` + `AppLocalesMetadataHolderService`) |
| E7 | `GlassEnvironment`: replace `LiquidGlassView(context).effectiveApiLevel` (QWEA0) with `Build.VERSION.SDK_INT` — see Q2 | QWEA0 removed |
| E8 | Keep rules: drop the QWEA0 JNI keep rule if it only served `liquidglass` (check `app/src/main/keepRules/rules.keep`) | Library removed |
| E9 | Docs: `AGENTS.md` (one UI stack, no ViewBinding/QWEA0/JitPack, Home View gone), `project_structure.md`, `LiquidGlass-Android.md` (QWEA0 history), `lessons/glass.md`, `config-schema.md` if Q3 removes keys | Shared docs |

## Questions for the project owner

| # | Question | Recommendation | Answer |
|---|---|---|---|
| Q1 | Approve the deletion list A–E (and F after the Log lists it)? | Yes | Approved as recommended (2026-11-20) |
| Q2 | QWEA0 decided the glass tier from `LiquidGlassView.effectiveApiLevel`. Compose glass (`dsGlass`) only checks API ≥ 31 and ignores the tier. Replace the probe with `Build.VERSION.SDK_INT` (same value on our devices unless QWEA0 had a device blacklist)? | Yes, and keep the tier logic for now (Q3 decides its future) | Approved as recommended (2026-11-20) |
| Q3 | The "Mức kính" (glass level) setting and the per-element switches (dock / panels / search bar / pill / icon menu, GS) only drove View glass. Compose glass ignores them. Remove those rows and their config keys (with `config-schema.md`), or keep and wire them to Compose in a later plan? | Keep for now, own follow-up plan (deciding which Compose surfaces honour them is a design question) | Approved as recommended (2026-11-20) |
| Q4 | JC said "keep the old Home for one release". No release has shipped the Compose Home; delete the View Home now? | Yes (JS Q9 already approved) | Approved as recommended (2026-11-20) |
| Q5 | Theme parents move from Material3 to AppCompat (E6). Splash, system bar colours and the few platform dialogs (time picker is gone; `Toast` unaffected) must look the same — check on device in Task 4. OK? | Yes | Approved as recommended (2026-11-20) |

## Tasks

1. **Code edits E1–E4, E7** in the real checkout (build green with the old files still present).
2. **Delete A–E** (after Q1), manifest entries, E5–E6, E8; `assembleDebug`, `lintDebug`, `testDebugUnitTest`.
3. **Resources F:** list in the Log, delete, second lint pass until `UnusedResources` is empty (or only intentional).
4. **Device check** (Samsung; Oppo for numbers): Home (grid, drag, folder, menu, library, search, widgets), Settings, Onboarding, CC panel + settings, splash/theme; release APK size and cold start vs. before.
5. Docs (E9), knowledgement JS+JX, lessons, commit.

## Log

| Date | Task | Work | Notes |
|---|---|---|---|
| 2026-11-20 | Plan | Static scan + proof build in a throw-away worktree; list A–F and edits E1–E9 written | Waiting for approval; nothing deleted in the real checkout |
| 2026-11-20 | 1 | E1 (moved `NotificationBadges` (State only), `PageDotsLayout`, `AppLibraryMotion` to `ui/home/compose`), E2 (`ui/home/WallpaperTargetLabels.kt`), E3 (HomeActivity: JH4 redirect, `redirected` guard, debug pref helpers removed), E7 (`GlassEnvironment` probe = `Build.VERSION.SDK_INT`) | Done in the real checkout |
| 2026-11-20 | 2 | `git rm` of lists A–E (146 files) + debug `ds_catalog_*` layouts; E4 manifest entries; E5 Gradle (Material, ConstraintLayout, ViewPager2, RecyclerView, QWEA0, ViewBinding, JitPack, toml aliases); E6 themes to AppCompat, Material-only items dropped, unused `Theme.Os26Launcher.Ds.BottomSheet` and `Widget.Os26.Ds.LinearProgress/CircularProgress/BottomSheet` removed; E8 QWEA0 JNI keep rule removed | `assembleDebug`, `lintDebug`, `testDebugUnitTest` green first try. Debug APK 20,787,988 B |
| 2026-11-20 | 3 | F: lint listed 113 unused (18 color, 35 dimen, 22 drawable, 1 font family XML `be_vietnam_pro`, 1 debug layout `activity_ds_catalog`, 20 string, 16 style). Checked `getIdentifier` uses: all look up other packages. Deleted 24 files + 250 value entries (with all locale copies) | Second lint pass: `UnusedResources` 0. Build and tests green. Debug APK 20,786,071 B |
| 2026-11-20 | 4 | Samsung (data backed up, restored after): Home + default-launcher dialog, Search (keyboard), icon menu, widgets, App Library, Settings (from app icon), CC settings, CC panel (main + media page, opened by the owner), status bar visible. Release APK (unsigned, same machine): **8,272,398 B (HEAD before JX) → 6,763,204 B (−1.5 MB, −18 %)** | No crash in logcat, airplane mode 0 after the panel. Not checked: Onboarding (needs a data wipe; JX did not touch its code), photo widget screen, rename/rate/city dialogs, Oppo cold start |
| 2026-11-20 | 5 | Docs E9: `AGENTS.md`, `project_structure.md`, `LiquidGlass-Android.md`, lessons `glass.md`, `design-system.md`, `permissions-and-device-testing.md`; knowledgement `knowledgements/JS-JX-compose-all-screens.md` | About 250 code comments still name deleted View classes as history ("replaces `DsButton`"); kept on purpose |
