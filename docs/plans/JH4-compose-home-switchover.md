# JH4 — Switch the launcher Home to Compose (step 3g of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19). Log per Task. Devices: Samsung SM-A176B (owner's daily phone; not the default launcher there), Oppo PKD130 (test phone; making it the default launcher is a system setting → the owner does it). Language: English (Compose-chain exception).

**Started:** 2026-11-20 · **Plan approved:** — (continuous mode: reviewed at the end)
**Status:** Tasks done 2026-11-20 — waiting for the owner's review. Open: RF1 (default launcher kept) not run, picker card drag and Samsung paging (follow-up plan), Library-on-open and page-after-relaunch seen once.
**Devices:** Samsung SM-A176B, Oppo PKD130 · **Room schema change:** none

**Goal:** `.ui.home.HomeActivity` — the component the system stores as the default launcher — becomes the Compose Home. The View Home stays compiled as `ViewHomeActivity`, reachable only through a debug switch, until JX removes it.

**Owner decisions (2026-11-20):** Compose by default with the View Home kept as a fallback; switch now and optimise paging on the Samsung later (no performance gate in JH4).

**Architecture:** Keep the component name, move the code. The current `HomeActivity` class is renamed `ViewHomeActivity` (own manifest entry, no HOME filter, not exported). The debug `ComposeHomeActivity` moves to `main` as the new `HomeActivity`, and `ComposeLibraryHost` moves with it. The responsibilities the Compose host still lacks are ported from the View Home: onboarding start and cover, recreate on icon-style / appearance change, hidden navigation bar setting, wallpaper-change notification to View glass, app-widget feature check.

**Spec:** `HomeActivity` (View) lifecycle as of JH3d; JH1–JH3d knowledgements; owner decisions above.

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Component `.ui.home.HomeActivity` keeps its name and manifest attributes (`singleTask`, `stateNotNeeded`, `clearTaskOnLaunch`, `nosensor`, `configChanges`, HOME filter) | Renaming the HOME component would make the system forget the user's default launcher |
| D2 | View Home = `ViewHomeActivity` (`exported=false`, no HOME filter, same window attributes) | Fallback kept compiled until JX |
| D3 | Fallback switch is debug-only: a debug receiver writes a pref; `HomeActivity` reads it in `onCreate` and hands over to `ViewHomeActivity` | Release users get Compose only; the fallback is for diagnosing on the owner's phones |
| D4 | Ported: onboarding (`startOnboardingIfNeeded`, first-frame wait, cover/uncover, stale-task cleanup), `RecreateGuard` (icon style, appearance changes), nav-bar-hidden setting (+ reapply on resume / focus), `GlassEnvironment.notifyBackdropChanged` on wallpaper change, app-widget host only when `FEATURE_APP_WIDGETS`, icon menu closes on window resize | View Home behaviour the Compose host did not have yet |
| D5 | Not in JH4: dragging a card out of the widget picker (needs a touch bridge from the picker window into `HomeDragState`) and Samsung paging optimisation → own plan after JH4 | Owner: switch first, optimise later |

## Global Constraints

- No change under `domain/`, `data/`. Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Default launcher kept after the update** (Oppo, owner sets it once): Home key opens the Compose Home, no chooser.
2. **First run / onboarding**: fresh data → onboarding opens over a dark first frame, back to Home afterwards.
3. **Icon style or theme changed in Settings** → Home rebuilt once, new style shown.
4. **"Hide navigation bar" setting** → bar hidden, swipe shows it briefly; survives a dialog.
5. **Debug fallback** → View Home opens and works; switching back returns to Compose.

---

### Task 1: Rename the View Home
- [x] `git mv` `HomeActivity.kt` → `ViewHomeActivity.kt`, rename class and self-references; manifest entry D2; build.

### Task 2: Compose Home becomes `HomeActivity`
- [x] `git mv` debug `ComposeHomeActivity.kt` → `main/.../ui/home/HomeActivity.kt` and `ComposeLibraryHost.kt` → `main/.../ui/home/ComposeLibraryHost.kt`; package/class names; remove the debug manifest entry; port D4.

### Task 3: Debug fallback
- [x] Debug receiver + pref; check in `HomeActivity.onCreate`.

### Task 4: Device check and close-out
- [x] Review Focus 1–5 (1 and 2 with the owner on the Oppo); release build size; docs; commit.

## Log

- **Task 1–3 (2026-11-20):** `HomeActivity.kt` → `ViewHomeActivity.kt` (`git mv`, class renamed, own manifest entry: not exported, no HOME filter); debug `ComposeHomeActivity` → `main/.../ui/home/HomeActivity.kt` and `ComposeLibraryHost` → `main/.../ui/home/` (`git mv`); debug manifest entry removed. Ported (D4): onboarding start / first-frame wait / cover (`android.R.id.content`) / stale-task cleanup, `RecreateGuard` (icon style + `appearanceChanges`), nav-bar-hidden setting (+ reapply on resume and window focus), `GlassEnvironment.notifyBackdropChanged` on wallpaper change, app-widget host only with `FEATURE_APP_WIDGETS`, icon menu closes on window resize. Debug fallback (D3): `debug/HomeModeDebugReceiver` writes pref `jh4_debug/view_home`; `HomeActivity.onCreate` hands over to `ViewHomeActivity` only when the app is debuggable; a `redirected` flag makes every lifecycle callback skip the uninitialised `lateinit`s. Debug and release compile; lint clean; 101 unit tests pass. Release APK 8,334,838 B (JD: 8,031,918 B; +303 KB since JD for JH1–JH4).
- **Samsung check (2026-11-20, debug):** `.ui.home.HomeActivity` now opens the Compose Home (wallpaper, pages, badges, default-launcher prompt), no crash in logcat; fallback: `view=true` → top activity `ViewHomeActivity`, `view=false` → `HomeActivity` (RF5 ✔).

- **Task 4 device run (2026-11-20, Samsung only — owner choice; data backed up first, restored byte-identical after: DB, settings, theme back to LIQUID_GLASS_27, onboarding flag back to done).**

## Review Focus evidence (filled in at close-out)

1. Default launcher kept after the update: not run (owner chose Samsung only; the app is not the default launcher there). Component name and manifest attributes unchanged (D1). **Open — check on the first device where the app is the default launcher.**
2. Onboarding: ✔ flag reset with the debug receiver (`OB_RESET`), force-stop, open `HomeActivity` → `OnboardingActivity` on top; "Skip" continues to Settings as the existing flow does when the app is not the default launcher.
3. Theme / icon style change while Home is visible: ✔ `CF_APPLY_THEME CLASSIC_26` → logcat "Schedule relaunch activity … HomeActivity", icons redrawn in the Classic style.
4. Hide navigation bar: ✔ toggled in Settings → General, "Trải nghiệm" opens Home without the gesture bar; still hidden after Back and reopening.
5. Debug fallback: ✔ `view=true` → `ViewHomeActivity`, `view=false` → `HomeActivity`.
- Seen once, not reproduced: Home opened from "Trải nghiệm" straight into the App Library after the onboarding skip; Back / reopen afterwards showed the grid. After the theme relaunch the pager stood on another page than before. Both → follow-up plan.

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md` (HomeActivity is Compose; View Home = ViewHomeActivity, debug switch), `project_structure.md` (JH4), `lessons/design-system.md` (JH4), `knowledgements/JH4-compose-home-switchover.md` (new).
