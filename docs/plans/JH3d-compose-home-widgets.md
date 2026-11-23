# JH3d — Compose Home: widget picker, app-widget add flow, widget taps (step 3f of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19). Log per Task. Device: Samsung SM-A176B (app data backed up with `run-as tar` before any write). Language: English (Compose-chain exception).

**Started:** 2026-11-20 · **Plan approved:** 2026-11-20 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-20 — owner approved. Open: picker card drag in Compose (→ JH4), configure flow and picker-closes-on-edit-exit not run.
**Devices:** Samsung SM-A176B · **Room schema change:** none

**Goal:** In the Compose Home, the edit-mode "Widgets" pill opens the existing widget picker (built-in widgets and app widgets), a tap adds the widget, the app-widget bind/configure flow works, and tapping a widget outside edit mode does what the View Home does (weather sheet, photo screen, clock/calendar app).

**Architecture:** Reuse, not rewrite. `WidgetPickerController` stops depending on `ActivityHomeBinding`: it gets a small `WidgetPickerHost` (context, current page index, pager size, whether card drag is supported) that `HomeActivity` and `ComposeHomeActivity` each implement. `AppWidgetFlowController` and `WeatherSheetController` are Activity-scoped and binding-free, so the Compose Activity builds them exactly like `HomeActivity`. Widget taps go through a new `onWidgetClick(kind, itemId)` callback from `HomeWidgetCell`.

**Spec:** JH3 investigation §5 (JH3a Log, Task 0); `WidgetPickerController`, `AppWidgetFlowController`, `WeatherSheetController`, `HomePagerController.onWidgetClick`.

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Refactor `WidgetPickerController(binding, …)` → `WidgetPickerController(host: WidgetPickerHost, …)`; `setup()` is replaced by the caller wiring its own button to the now-public `show()`. View Home behaviour unchanged | Only four binding uses (button, page index, pager size, context); smallest change that lets Compose reuse the picker |
| D2 | Card **drag** from the picker is off in the Compose Home (`supportsCardDrag = false`); tap-to-add only | The drag hands the touch stream to `DragHelper` (View drag), which has no targets in Compose; bridging the picker window's forwarded touches into `HomeDragState` is its own task → JH4 |
| D3 | `HomeEvent.WidgetAdded(pageId)` scrolls the Compose pager to that page | Same as `HomePagerController.scrollToPage` |
| D4 | Widget tap outside edit mode: WEATHER → `WeatherWidgetHub.tappedWidgetId = itemId` + weather sheet; PHOTO → `PhotoWidgetActivity`; others → `onWidgetTapped(kind)`; edit mode swallows the tap | `HomePagerController` 116-128, IGV 555 |
| D5 | `onStop` keeps edit mode while the app-widget flow is active; Home key aborts the flow and closes picker and weather sheet | `HomeActivity` 308-310, 354 |

## Global Constraints

- Only `WidgetPickerController` and its two call sites change outside `ui/home/compose/` and `debug/`. Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Tap a 2 × 2 clock in the picker** → widget lands on the current page (or the next with room) and the pager shows it.
2. **App widget with a configure activity** → configure, return, widget appears; cancel → no row, id released.
3. **Leave edit mode with the picker open** → picker closes.
4. **Tap the weather widget** → weather sheet for that widget.
5. **View Home picker still opens and drags** (refactor regression).

---

### Task 1: Picker host refactor
- [x] `WidgetPickerHost`; View Home wiring updated; build and check the View Home picker on device.

### Task 2: Compose wiring
- [x] Widgets pill in `HomeEditToolbar`; picker + `AppWidgetFlowController` + `WeatherSheetController` in the Activity (onCreate, onResume, onActivityResult, onStop/Home key/onDestroy); `WidgetAdded` scroll; widget taps.

### Task 3: Device check and close-out
- [x] Back up data; Review Focus 1–5; restore; docs; commit.

## Log

- **Task 1–2 code (2026-11-20):** `WidgetPickerController` takes `WidgetPickerHost` (context, current page, pager size, `supportsCardDrag`); `setup()` removed, `show()` public; `HomeActivity` implements the host from `binding.homePager` and wires `btnAddWidget` itself (behaviour unchanged). Compose: Widgets pill in `HomeEditToolbar`, `HomeWidgetCell(onClick)` outside edit mode, `pageScrollRequests` flow (waits up to 2 s for the page to exist, then flips), `HomePagerInfo.pagerWidth/Height`. Activity: picker host (no card drag), `AppWidgetFlowController` (onCreate, `onResume`, `onActivityResult`), `WeatherSheetController`, edit-mode exit closes the picker, `onStop` keeps edit mode during the flow, Home key aborts flow and closes picker / weather sheet, `onDestroy` closes them. Compiles. App data backed up (`samsung_backup3.tar`); device test waiting for unlock.

- **Task 3 device run (2026-11-20, Samsung, debug; data backed up first):** picker opens from the Compose Widgets pill (all built-in kinds and sizes); restore note: the first restore compared unequal because the app process restarts right after `force-stop` (the notification listener lives in it); retried restore → byte-identical, stable after 5 s and after another `force-stop`.

## Review Focus evidence (filled in at close-out)

1. Tap the 2 × 2 clock: ✔ added on page 5 (first page with room) and the pager flipped there.
2. App widget with a configure activity: not run (needs choosing a third-party provider that writes its own state). Same `AppWidgetFlowController` as the View Home. **Open.**
3. Leave edit mode with the picker open: not run (the picker window has focus; Back closes it first). Same `onEditMode` call as the View Home. **Open.**
4. Tap the weather widget: ✔ weather sheet for that widget.
5. View Home picker after the refactor: ✔ opens from the View edit toolbar.
- Seen: the pill a11y text read "Trang 1 trên 5" right after the flip to page 5 (uiautomator); dots were correct. Check in JH4.

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md`, `project_structure.md`, `lessons/settings-and-data.md` (restore note), `knowledgements/JH3d-compose-home-widgets.md` (new).
