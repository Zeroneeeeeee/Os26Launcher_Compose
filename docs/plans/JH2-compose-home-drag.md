# JH2 — Compose Home: pager feel, edit mode, drag and drop (step 3b of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md` and `docs/Workflow.md` apply, with the continuous mode the owner chose for the Compose chain (2026-11-19): all Tasks in one go, stop at the end of the plan, or for a decision only the owner can make, or when a phone needs the owner. Log per Task. Devices: Oppo PKD130 and Samsung SM-A176B (owner switched to it 2026-11-19). Commands for PowerShell.
>
> Language: English (Compose-chain exception in `AGENTS.md`).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-19 — owner approved. Open: RF2 ghost-leave, RF3 two fingers, one unexplained missed swipe, Samsung per-frame paging cost (→ JH4).
**Devices:** Samsung SM-A176B (connected) · Oppo PKD130 when connected · **Room schema change:** none

**Goal:** Make the Compose Home (debug `ComposeHomeActivity`) usable for layout editing: pager that snaps like the View Home, edit mode (enter/exit, jiggle, "−" badge, toolbar), and drag and drop of apps, folders and widgets between grid pages and the dock, with the View Home's live preview, edge flipping and ghost page. Every write goes through the existing `HomeViewModel` functions; no domain or data change.

**Architecture:** One `HomeDragState` (plain state holder, `remember`ed in `HomeScreen`) owns the drag session. Touch is read by a single `pointerInput` on the `HomeScreen` root in `PointerEventPass.Initial`, so the gesture survives the source page being disposed by the pager (the View Home solves the same problem by letting the Activity own drag touches, Task 7). Items register their bounds (root coordinates) in the drag state; hit-testing the drop target uses `GridMetrics.Cells.cellAt` relative to the pager area and space-evenly slots for the dock. The floating icon is drawn on top of everything from a cached `ImageBitmap`. Grid items read a preview map from the drag state and animate an offset to the previewed cell.

**Tech Stack:** unchanged from JH1 (Compose BOM `2026.09.00`, Backdrop `2.0.1`). No new dependency.

**Spec:** current View behaviour as mapped in the JH2 investigation (summary in the Log, Task 0) — `IconGridView`, `DockView`, `DragHelper`, `HomeTouchRouter`, `HomePagerController`, `Jiggler`, `IconCellView` badge, `TunedPagerSnapHelper`, `PageMotion`; JH1 carry-overs (Samsung paging p90 +5 ms vs View).

---

## Why this design

- **Root-level gesture, not per-cell `pointerInput`.** A per-cell detector is cancelled when its page leaves composition (edge flip two pages away). The root sees every pointer first (Initial pass), lets the pager have normal swipes, and consumes only once a drag has started.
- **Reuse planners on the UI thread** (`GridInsertPlanner`, `FolderPlanner.isInCenterZone`), exactly as `IconGridView` does, so preview and DB write cannot disagree (planners are pinned by JH1 tests).
- **Reuse `RemoveAppDialog`** (View dialog, takes a `Context`) for the "−" badge; the Compose version belongs to JS with the other dialogs.

## iOS fidelity changes

| # | Change | Where |
|---|---|---|
| H4 | Lifted icon scales to 1.1 with a soft shadow (iOS lifts the icon; View Home draws it at 1× without shadow) | floating icon |
| H5 | Pager snap uses the View Home's easeOutQuint curve but as one tween for every release (Compose has no per-fling duration hook in `PagerDefaults.flingBehavior`) | `HomeScreen` pager |

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Long-press on an icon without moving enters edit mode directly; the icon menu (and drag from the menu) comes in JH3 | No menu UI yet; calling `onIconLongPressed` would set `iconMenu` with nothing drawn |
| D2 | Drag starts on the first move past touch slop after the long-press timeout (`ViewConfiguration`), like `IconDragGesture` | Same feel as View |
| D3 | Constants copied from the View code (private there): hover arm 100 ms, push arm 500 ms within 8 dp, push anchor hysteresis 8 dp, edge zone 24 dp, edge dwell 500 ms, edge rest 500 ms, flip 450 ms easeOutQuint, SLIDE spring 800/0.8, SETTLE 600/0.8, jiggle ±2° 160 ms reverse with random phase, widget jiggle × 0.5, badge 0.38 × icon | Parity |
| D4 | Drop calls: grid/dock → `onItemDropped(itemId, ContainerId.HOME/DOCK, pageId or PageId.NEW / null, cell)`; merge on app → `onFolderIntent`; merge on folder → `onAddToFolder`; no accepted target → `onDragCancelled` | Existing VM API, optimistic updates already inside |
| D5 | No `HomeViewModel` unit tests in JH2 | VM has 21 constructor dependencies (final use-case classes over repositories); faking them costs more than the risk, since JH2 does not change the VM. Planners stay pinned by JH1 tests |
| D6 | Add-widget pill hidden until JH3 (widget picker); Settings pill opens `SettingsActivity`; Done → `exitEditMode` | Picker not ported |
| D7 | Dragging a child out of a folder, dropping new widgets from the picker: JH3 | Need the folder panel / picker |

## Out of scope

Icon menu, folder panel, App Library, search overlay, widget picker, notification badges (JH3) · default-launcher switch and View Home removal (JH4) · page transitions (`PageTransition` is not read by the View Home either).

## File map

| File | Responsibility |
|---|---|
| `ui/home/compose/HomeDragState.kt` | Session, registry of item bounds, hit-test, hover state machine (MERGE/PUSH), preview map, edge timer |
| `ui/home/compose/HomeDragGesture.kt` | Root `pointerInput`: long-press → edit mode, slop → drag, move/up/cancel |
| `ui/home/compose/HomeDragLayer.kt` | Floating icon + settle / fly-back animation |
| `ui/home/compose/HomeEditMode.kt` | Jiggle modifier, "−" badge, edit toolbar |
| `ui/home/compose/HomeScreen.kt`, `HomeGridPage.kt`, `HomeDock.kt` | Wire state, preview offsets, hidden source, ghost page, pager fling |
| `debug/ComposeHomeActivity.kt` | Edit-mode callbacks (Settings, remove dialog, back, onStop exit), status bar hide |

## Global Constraints

- No change under `domain/`, `data/`, or the View Home. VM functions used as-is.
- Glass policy JD D9 unchanged; edit mode adds no real-glass surface.
- Strings reuse the View Home's (`edit_done`, settings pill, remove dialog).
- Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Edge flip two pages away from the source** — gesture keeps going, drop lands on the target page (root gesture; Task 4).
2. **Drop on the ghost page** — new page created with the item, `onGhostPageLeft` when flipping back (Task 4).
3. **Second finger during a drag** — lifting the drag finger drops, the other finger does nothing until lifted (C18; Task 3).
4. **Dock full (4) and dragging from the grid** — no slot preview, drop rejected and icon flies back (Task 3).
5. **Swipe between pages in edit mode** — pager still scrolls when no drag is armed (Task 2/3).

---

### Task 1: Pager snap

- [x] `HorizontalPager(flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = tween(PageMotion.FULL_MS, easing = easeOutQuint)))`, `easeOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`.
- [x] Measure on the Samsung: 3 × 20 swipes, compare with JH1 (Compose p90 31–32 ms, ~750 frames/run).

### Task 2: Edit mode

- [x] Long-press on empty grid area or an icon (no move) → `enterEditMode()`; tap empty area → `exitEditMode()`; Done pill; back (`BackHandler`); Activity `onStop` (not changing configurations) → `exitEditMode()`.
- [x] Jiggle modifier (D3), badge on apps and widgets (not folders): app → `RemoveAppDialog` path (`confirmQuickRemove`), widget → confirm then `onRemoveWidget`.
- [x] Toolbar at the top: Settings, Done (D6); status bar hidden while in edit mode; pill shows dots in edit mode.

### Task 3: Drag core

- [x] Root gesture (D2), floating icon (H4), source hidden, drop on grid cell / dock slot (D4), reject → fly back to source rect (or vanish from dock), settle spring to the target rect and keep the landed item hidden until settled.
- [x] Multi-touch: track the drag pointer id; second finger ignored.

### Task 4: Live preview, edge flip, ghost page

- [x] PUSH preview via `GridInsertPlanner.plan` on the current page, items spring to previewed cells; anchor hysteresis.
- [x] MERGE hover (100 ms) on 1×1 app/folder: app target shows a hover plate, folder zooms 1.12 unless full; drop → `onFolderIntent` / `onAddToFolder`.
- [x] Dock slot preview: space-evenly for remaining + 1, other icons slide.
- [x] Edge zones, dwell, rest; `onDragEdgeBeyondLastPage()` → ghost page appended and flipped to; `onGhostPageLeft()` when leaving it.

### Task 5: Device check and close-out

- [x] Review Focus 1–5 on the Samsung (and Oppo if connected); paging numbers again; knowledgement, lessons, docs, commit.

## Log

- **Task 0 (2026-11-19):** View behaviour mapped by a read-only subagent (edit mode triggers, `IconDragGesture` timing, `DragHelper` session, hover MERGE/PUSH constants, edge flip/ghost page, jiggle/badge/toolbar, `TunedPagerSnapHelper`, dock rules, `HomeTouchRouter` priority). Constants used are listed in D3. Notable: View lifts the icon at 1× without shadow; `PageTransition` is not read by the View Home.
- **Task 1–4 code (2026-11-19):** `HomeScreen` pager uses `tween(450, easeOutQuint)` snap. New `HomeDragState`, `HomeDragGesture` (root `pointerInput`, Initial pass), `HomeDragLayer`, `HomeEditMode` (jiggle, badge, toolbar); `HomeGridPage` items get `homeItemDecor` (hidden / preview offset / jiggle); `HomeDock` positions slots itself for the preview. Activity: `HomeDragActions` → VM, `confirmQuickRemove` for app badges, widget confirm dialog, `BackHandler`, status bar hidden in edit mode, `exitEditMode` in `onStop`. Compile, lint (0 findings in new files) and the 101 unit tests pass. **Not yet run on a device** (adb lost the Samsung after the build).

- **Task 5 device run (2026-11-19, Samsung SM-A176B, debug build, app data backed up with `run-as tar` first and restored byte-identical after):** two bugs found and fixed: dock icons sat left in their slots (the new `Box` around `HomeAppCell` needed `TopCenter`; label-less cells are icon-wide) and the "−" badge then moved to the top centre (badge now `align(TopStart)`). Results: long-press icon → edit mode with toolbar, badges, dots, jiggle ✔; drag 1.1.1.1 near a cell edge → push preview after the hold, drop writes the new order ✔; drag onto the centre of Canva → folder created (`onFolderIntent`) ✔ (90 px from the centre is still inside `FolderPlanner`'s zone, same rule as View); drag to the full dock → no slot preview, flies back ✔ (RF4); hold at the right edge → flips page after page with the drag alive, ghost page appears, drop makes "Trang 6 trên 6" / "Trang 7 trên 7" ✔ (RF1, RF2); tap empty area exits edit mode ✔; swipe pages in edit mode ✔ (RF5) — **once** a swipe right after a ghost-page drop did not move the pager; repeated twice afterwards (ghost drop then swipe; normal drop then swipe) and it worked both times. Not reproduced, kept as open. Widgets seen for the first time on a device: 4 × 2 clock (real glass) and weather render; app-widget placeholder card shows. Not covered: RF3 (second finger, adb injects one pointer only), `onGhostPageLeft` (flip back off the ghost page).

- **Task 1 measurement (2026-11-19, Samsung, release signed with the debug key, `speed-profile`, temp `src/release` copy removed after):** 20 swipes × 3: janky 3.99 / 3.65 / 3.87 % (JH1: 4.70 / 4.67 / 4.95 %), p90 32 / 31 / 32 ms (JH1 31–32), p99 73 ms (JH1 65), frames ~955 per run (JH1 ~750; View ~560). The 450 ms tween settles more slowly, so more frames are drawn; per-frame cost (p90) did not change. Paging cost on the Samsung is a per-frame problem (glass dock/widgets + pages), not a snap-curve problem: carried to JH4 (profile with the switchover).


## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md` (Home Compose note: edit mode/drag), `project_structure.md` (JH2 files), `lessons/design-system.md` (JH2), `knowledgements/JH2-compose-home-drag.md` (new).
