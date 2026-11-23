# JH3c — Compose Home: App Library and app widgets (step 3e of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19). Log per Task. Device: Samsung SM-A176B (app data backed up with `run-as tar` before any write). Language: English (Compose-chain exception).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-20 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-20 — owner approved. Next: JH3d (widget picker, add flow, weather sheet).
**Devices:** Samsung SM-A176B · **Room schema change:** none

**Goal:** The Compose Home opens the App Library by swiping past the last page (both styles, group panel, search bar) and shows real app widgets (`AppWidgetHostView`) instead of empty cards.

**Architecture:** The Library keeps its View implementation for now, as search did in JH3a: `CategoryPageView`, `CategoryGroupPanelView` and the blurred-wallpaper scrim are added with `addContentView` above the `ComposeView`. The swipe gesture reuses `LibraryOverlayController` unchanged; the Activity feeds it `MotionEvent`s first in `dispatchTouchEvent`, like `HomeTouchRouter`. Its progress drives the library views (as `AppLibraryController.applyLibraryProgress`) and a `libraryProgress` parameter that slides the Compose Home layer left. App widgets: `HostedWidgetViews` + `OsAppWidgetHost` (start/stop listening on start/stop) give one `AppWidgetHostView` per id, placed in the cell with `AndroidView` inside the same glass card as self-drawn widgets.

**Spec:** JH3 investigation (JH3a Log, Task 0) §3 and §5; `AppLibraryController`, `LibraryOverlayController`, `IconCellView.bindAppWidget`, `HostedWidgetViews`.

## Split change

The old JH3c is split again: **JH3c** (this) = Library + app-widget display; **JH3d** = widget picker (tap and drag to add), app-widget add flow (bind / configure), weather sheet. Reason: the picker needs a new-widget drag bridge into `HomeDragState` and the add flow needs Activity results; both are independent of the Library.

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Library views stay View-based in `addContentView` (rewrite in JS) | Same reasoning as JH3a D1; ~1,900 View lines |
| D2 | `LibraryOverlayController` gets touches before Compose; TAKEOVER → a fake `ACTION_CANCEL` to `super.dispatchTouchEvent`, then swallow | `HomeTouchRouter` contract |
| D3 | Open only when: library enabled, grid Ready, pages not empty, no ghost page, pager settled on the last real page at DOWN, not edit mode, no drag, no menu, no folder, no wallpaper prompt, search closed. Edit mode / drag / menu / folder / prompt while open → close | `AppLibraryController.canOpenLibrary` / `render` |
| D4 | Home layer (pages, pill, dock; not the wallpaper) slides `-W·p`; invisible at p = 1 | `applyLibraryProgress`, `HOME_SLIDE_FRACTION = 1` |
| D5 | Back: search → menu → folder → edit mode → group → library → root | `HomeBackController` |
| D6 | App widget host view is cached per id by `HostedWidgetViews` (not released when its page scrolls away); `clear()` in `onDestroy` | Same lifetime as the View Home |

## Global Constraints

- No change under `domain/`, `data/`, View Home. Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Swipe left on a middle page** never opens the Library (only on the last real page).
2. **Close the Library by swiping right** and by Back; Home is back in place with its dock glass.
3. **Group panel open, then Back** closes the panel only.
4. **App widget** renders and updates (e.g. a clock / music widget) and survives scrolling away and back.
5. **Library open, then long-press** — no menu or edit mode behind it.

---

### Task 1: Library host
- [x] Views + scrim + insets; content from `HomeViewModel.categoryGroups`; style toggle from `HomeScreenConfig.libraryEnabled`; open/close progress; group panel; search bar → `searchViewModel.open(LIBRARY, showAll = true)`; Back D5; Home key closes it.

### Task 2: App widgets
- [x] Host listening in `onStart`/`onStop`; `HostedWidgetViews` in the Activity; `HomeAppWidgetCell` (glass card, inset `aw_card_inset`, `AndroidView` with the host view, `onSized`), placeholder text when the provider is missing.

### Task 3: Device check and close-out
- [x] Review Focus 1–5 on the Samsung; docs; commit.

## Log

- **Task 1–2 code (2026-11-19/20):** `debug/ComposeLibraryHost.kt` (copy of `AppLibraryController` without `ActivityHomeBinding`: scrim, `CategoryPageView`, `CategoryGroupPanelView` added with `addContentView` under the search layer; `LibraryOverlayController` unchanged; Home slides through `libraryProgress`), Activity `dispatchTouchEvent` feeds the controller first (PASS / TAKEOVER with fake CANCEL / CONSUME), insets from `getInsetsIgnoringVisibility(systemBars)`, Back order search → menu → folder → edit → group → library → root, Home key closes the Library. `HomeScreen` takes `libraryProgress: () -> Float` (read in `graphicsLayer`, no recomposition per frame) and fills `HomePagerInfo` for the open condition. App widgets: `HomeAppWidgetCell` (`LocalHostedWidgets`, glass card, `AndroidView` hosting the `AppWidgetHostView`, `onSized`, "unavailable" text), host listening in `onStart`/`onStop`, `clear()` in `onDestroy`. Debug build compiles. **Not yet run:** no device connected (session interrupted, then adb lost the Samsung).

- **Task 3 device run (2026-11-20, Samsung, debug; read-only checks, no layout written):** one bug found and fixed: the Library background and group tiles had no wallpaper (flat grey) because `blurredWallpaperScrim` and the tile glass look up `R.id.backdrop_image`, which only the View Home has; `ComposeLibraryHost` now adds a hidden `ImageView` with that id fed from `HomeUiState.backdrop`. After the fix: blurred wallpaper background, glass tiles, search bar, group panel with 12 apps.

## Review Focus evidence (filled in at close-out)

1. Swipe left on pages 1–4: ✔ pages change; only the extra swipe on page 5 (last) opens the Library.
2. Close by Back and by swiping right: ✔ Home back in place with dock glass and badges.
3. Group panel then Back: ✔ closes the panel only; second Back closes the Library.
4. App widget: ✔ a weather app widget (3 × 2) shows live content (18°, place, time) and is still there after scrolling two pages away and back; no new `createView` in the log (view reused).
5. Long-press inside the open Library: ✔ no edit mode or menu behind (no "Xong" in the tree, Library stays open).

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md`, `project_structure.md`, `lessons/design-system.md` (JH3c), `knowledgements/JH3c-compose-home-library.md` (new).
