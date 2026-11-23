# JH3a — Compose Home: badges, prompts, back/Home key, search (step 3c of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19). Log per Task. Devices: Samsung SM-A176B (connected), Oppo PKD130. Commands for PowerShell. Language: English (Compose-chain exception).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-19 — owner approved. Open: folder badge sum, contacts permission, badge toggle (not run on the owner's phone), Home key (→ JH4).
**Devices:** Samsung SM-A176B · **Room schema change:** none

**Goal:** Close the small, Activity-scoped gaps of the Compose Home so it behaves like the View Home outside the big overlays: notification badges on icons and folders, the default-launcher and wallpaper prompts, Back and Home-key handling in the View Home's order, and the search overlay (pill tap and swipe down).

**Architecture:** Badge state is the same `combine(HomeScreenConfig, NotificationBadgeRepository.counts)` the View Home feeds into `NotificationBadges`; the Compose Home collects it in the Activity and passes it down. Prompts reuse `DefaultLauncherPromptController` (needs `AppCompatActivity`, so `ComposeHomeActivity` switches to it) and a copy of `BackdropController.showWallpaperDialog`. The search overlay keeps `SearchOverlayView` + `SearchViewModel` unchanged, hosted with `AndroidView` (the view is self-contained); its controller glue (adapter, contacts permission, lifecycle) is re-done in the Activity. Swipe-down detection joins the root `homeGestures`.

**Spec:** View behaviour mapped in the JH3 investigation (Log, Task 0): `NotificationBadgesController`/`NotificationBadges`/`IconCellView` badge geometry, `DefaultLauncherPromptController`, `BackdropController.showWallpaperDialog`, `HomeBackController`, `HomeActivity.onNewIntent`, `SearchOverlayController`, `SearchSwipeDetector`.

## Split of the old JH3

| Plan | Content | Why apart |
|---|---|---|
| **JH3a** (this) | badges, prompts, back/Home key, search | Activity-scoped wiring, no drag coupling |
| JH3b | icon long-press menu, folder panel | both hand drags to `HomeDragState`, share scrim and floating icon |
| JH3c | App Library, widget picker, app widgets | large pager-coupled layer; picker drags new widgets |

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Search stays the View `SearchOverlayView` in `AndroidView` until JS | Self-contained 1,800-line feature; rewrite belongs with the other screens |
| D2 | Badge count logic copied as a pure extension on `NotificationBadges.State` in `ui/home/compose/` | The View object keeps a registry of `IconCellView`s; not touching View code |
| D3 | Swipe-down: start zone = below status bar + 48 dp and above the dock top; trigger ≥ 64 dp down with dy ≥ 2·|dx|; disabled in edit mode, while dragging, when the toggle is off, or when search is open | Same numbers as `SearchOverlayController` / `SearchSwipeDetector` |
| D4 | Back order: search → edit mode → (JH3b: menu, folder) → (JH3c: group, library) → `finish()` only when not the default launcher. Home key (`onNewIntent`): close search, exit edit mode | `HomeBackController` LIFO and `HomeActivity.onNewIntent` |

## Global Constraints

- No change under `domain/`, `data/`, View Home. Strings reused.
- Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Badge on a folder** shows the sum of its apps (not per child).
2. **Swipe down starting on an icon** opens search and the icon does not launch.
3. **Back with search open in edit mode** closes search only.
4. **Contacts permission** request from search still works (Activity result registry).
5. **Badges toggled off in Settings** disappear without restarting the Activity.

---

### Task 1: Notification badges
- [x] Activity collects the combine → `NotificationBadges.State`, passes to `HomeScreen` → cells. Pill badge top-right of icon: height 0.38 × icon, centre inset 0.35 r, `ds_status_danger`, bold white text 0.62 h, padding 0.5 h, "99+"; DOT style 0.24 × icon. Folder = sum of `previewApps` keys (D2).

### Task 2: Prompts
- [x] `AppCompatActivity`; `DefaultLauncherPromptController` on resume/pause/destroy; wallpaper dialog when `showWallpaperPrompt`; Wallpaper/Menu/AppWidget events → toasts like `HomeActivity.handleEvent`.

### Task 3: Back and Home key
- [x] `BackHandler`s in D4 order; root back finishes only when not default; `onNewIntent` closes search and exits edit mode.

### Task 4: Search overlay
- [x] `AndroidView(SearchOverlayView)` above everything; adapter, callbacks, contacts permission launcher, lifecycle observer as in `SearchOverlayController`; pill tap → `open(SPOTLIGHT)` (not in edit mode/drag); swipe-down in `homeGestures` (D3).

### Task 5: Device check and close-out
- [x] Review Focus 1–5 on the Samsung; docs; commit.

## Log

- **Task 0 (2026-11-19):** overlays mapped by a read-only subagent; JH3 split into JH3a/b/c (table above).
- **Task 1–4 code (2026-11-19):** `HomeBadges.kt` (pure `countFor` on `NotificationBadges.State`, badge drawn like `IconCellView`), badges passed through `HomeEditContext` to app, folder and dock cells; compose Home types made `internal` (they now take the internal `NotificationBadges.State`). `ComposeHomeActivity` → `AppCompatActivity` with `DefaultLauncherPromptController`, wallpaper dialog, Home/Search event toasts, `BackHandler`s in `HomeBackController` order, `onNewIntent` Home key. Search: `SearchOverlayView` added with `addContentView` ABOVE the `ComposeView` (an `AndroidView` covering the screen would take touches from Home even when GONE); swipe-down in `homeGestures` (`HomeSearchSwipe`, D3). Compiles. **Not yet run:** the Samsung was locked.

- **Task 5 device run (2026-11-19, Samsung, debug build):** default-launcher prompt shows on resume ✔; badges Messages 2 / Discord 38 drawn like the View ✔; pill tap opens search (blurred backdrop, Recent, trending) ✔; first Back hides the keyboard, second Back closes search and stays on Home ✔; with search closed and not default, Back finishes the Activity ✔ (D4 root); swipe down starting on Discord opens search and Discord does not launch ✔. No layout data written (read-only checks, no backup needed).

## Review Focus evidence (filled in at close-out)

1. Folder badge = sum: code path (`countFor(previewApps keys)`); no folder with notifying apps on the owner's phone. **Open.**
2. Swipe down on an icon: ✔.
3. Back with search open in edit mode: not reachable — search cannot open in edit mode (pill and swipe are blocked there, same as View). Back order verified for search → Home and root.
4. Contacts permission: not run (would change a system permission on the owner's phone). Same registry code as `SearchOverlayController`. **Open.**
5. Badges toggled off: not run (owner's app setting). Live `combine` of the same flows. **Open.**
- Home key (`onNewIntent`): only reachable when this Activity is the HOME target → JH4.

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md` (Home Compose note), `project_structure.md` (JH3a files), `lessons/design-system.md` (JH3a), `knowledgements/JH3a-compose-home-wiring.md` (new).
