# JH3b — Compose Home: icon menu and folder panel (step 3d of direction B) — Implementation Plan

> Work rules: `docs/AGENTS.md`, `docs/Workflow.md`, continuous mode for the Compose chain (owner, 2026-11-19). Log per Task. Device: Samsung SM-A176B (app data backed up with `run-as tar` before any write, restored after). Language: English (Compose-chain exception).

**Started:** 2026-11-19 · **Plan approved:** 2026-11-19 (continuous mode, reviewed at the end)
**Status:** Closed 2026-11-19 — owner approved. Open: folder `TransitionStyle`s other than SMOOTH (D6), menu drag for folder children (View also excludes it).
**Devices:** Samsung SM-A176B · **Room schema change:** none

**Goal:** The Compose Home gets the long-press icon menu (shortcuts, App info, Hide, Edit Home, Uninstall) and the folder panel (open from a folder, 3 × 3 grid, rename, launch, remove, drag an app out), both feeding the existing `HomeViewModel` API and the JH2 drag state.

**Architecture:** Both overlays are composables drawn by `HomeScreen` above the pages, driven only by `HomeUiState.iconMenu` / `openFolderId`. While one is open, the Home layer underneath is blurred (`Modifier.blur`, API 31+) and dimmed, the iOS look the View gets from `BlurredSnapshotScrim`. Placement of the menu reuses the pure `IconMenuPlacement.place` / `IconMenuWidth.fit`. The root gesture (`homeGestures`) stays the single owner of long-press and drag: outside edit mode a long-press on an app now opens the menu (replacing JH2 D1), a long-press on the floating icon while the menu is open means Edit Home + drag, and children of an open folder are hit-tested from geometry the panel registers in `HomeDragState`.

**Spec:** View behaviour mapped in the JH3 investigation (JH3a Log, Task 0): `IconMenuController`, `IconActionMenu`, `IconMenuPlacement`, `IconMenuMotion`, `IconMenuBackdrop`, `RemoveAppConfirm`, `FolderPanelController`, `FolderPanelView`, `PanelMotion`.

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Long-press on an app (grid, dock, folder child) outside edit mode → `onIconLongPressed(itemId, container, key, anchor, shortcutIconSizePx)`; moving past slop afterwards starts the drag (VM clears the menu in `onDragStarted`). Folders and widgets → `enterEditMode()` as in the View | Replaces JH2 D1 now that the menu exists |
| D2 | Long-press on the floating icon while the menu is open (only `container ≤ 0`) → `onIconMenuAction(EditHome)` then drag on move | View "drag from menu" |
| D3 | Menu glass: `dsGlass(CARD, Fixed(24))` (real glass, JD D9); rows 39 dp, icon 20 dp, text 15 sp, width `fit(natural, 200, 260 dp)`, gap 10 dp, margin 8 dp; Uninstall red; order reversed when above | `IconActionMenu` dimens |
| D4 | Menu motion: open spring 700/0.9 scale 0.6 → 1 from the corner nearest the icon, alpha full at 40 %; close 1400/1.0 | `IconMenuMotion` |
| D5 | Folder panel: side = min(360 dp, w − 2·24 − 2·16, …), radius 36 dp, `dsGlass(SHEET)`; title is a `BasicTextField` above the panel, left-aligned with the first column; rename commits on IME Done, focus loss, close, `onStop` via `renameFolder` | `FolderPanelView` |
| D6 | Folder motion: spring 400/0.85 from the folder icon square (SMOOTH style only; other `TransitionStyle`s later) | `PanelMotion`; other styles are a Settings option, JS |
| D7 | Drag out of a folder (edit mode): leaving the panel rect closes the folder and the drag continues on the grid; dropping inside the panel is rejected | View behaviour |
| D8 | Back order: search → menu → folder → edit mode → root | `HomeBackController` |

## Global Constraints

- No change under `domain/`, `data/`, View Home. Strings reused.
- Commit messages in English, no Vietnamese diacritics.

## Review Focus

1. **Menu near the right/bottom edge** (dock icon) opens above and right-aligned, inside the screen.
2. **Hold, menu opens, keep holding and move** → drag starts, menu closes, drop works.
3. **Rename then press Home/Back** → title saved.
4. **Drag the last-but-one app out of a 2-app folder** → folder dissolves (VM rule) without a crash.
5. **Menu "Hide app" on a folder child** → `hideAppInFolder`, panel closes when ≤ 1 app left.

---

### Task 1: Icon menu
- [x] Gesture D1/D2; `HomeIconMenu` (scrim tap closes, floating icon at the anchor, glass card, items, placement, motion); actions wired (Uninstall → `confirmMenuUninstall`).

### Task 2: Folder panel
- [x] Tap folder → `openFolder`; `HomeFolderPanel` (scrim, title field, glass panel, 3 × 3 `HomeAppCell`s with badges, jiggle and "−" in edit mode → `confirmFolderRemove`), motion, rename commits, launch closes.

### Task 3: Drag out of a folder
- [x] Panel registers child rects; `hitTest` sees them; source container = folder id; leaving the panel → `closeFolder`; drop inside → cancel.

### Task 4: Device check and close-out
- [x] Back up app data; Review Focus 1–5 on the Samsung; restore; docs; commit.

## Log

- **Task 1–3 code (2026-11-19):** `HomeIconMenu.kt` (scrim, floating icon, light glass card with `shortcut_menu_glass_tint`, rows, `IconMenuPlacement.place` in a `Layout`, open/close springs; last menu kept for the close animation), `HomeFolderPanel.kt` (scrim, `BasicTextField` title with commit on Done / focus loss / dispose / `ON_STOP`, SHEET glass panel, 3 × 3 `HomeAppCell`s, spring from the folder icon, geometry registered for gestures), folder cells clickable → `openFolder`. Root gesture: long-press app → `onIconLongPressed` (folder child: itemId −1, container = folder id), held icon with menu open → EditHome, folder/widget → edit mode; `hitTest` sees folder children first; drag from a folder closes it on leaving the panel, drop inside the panel is rejected. Home layer blurred (API 31+) and dimmed while an overlay is open. Back: search → menu → folder → edit mode → root. Compiles. App data backed up (`samsung_backup2.tar`); device test waiting for unlock.

- **Task 4 device run (2026-11-19, Samsung, debug; data backed up first, restored byte-identical after):** two bugs found and fixed: folder panel glass was the dark DS SHEET fill (now CARD + `folder_panel_glass_tint`, light frosted like the View); the icon menu of a folder child was drawn under the panel (z-order: panel first, menu above). Results below. Badges seen on more apps (YouTube 5, Zalo 99+, Zalopay 11).

## Review Focus evidence (filled in at close-out)

1. Dock icon menu: ✔ above the icon, right-aligned, order reversed.
2. Hold → menu → keep holding and move: ✔ drag starts in edit mode, menu closes; dropped back on its cell, nothing written.
3. Rename then Home: ✔ "X1" kept after a cold restart.
4. Drag the last-but-one app out of a 2-app folder: ✔ dropped on the grid, folder dissolved to the remaining app, no crash.
5. Menu "Hide app" on a folder child: ✔ (menu has no Edit Home), app hidden, folder kept 2 apps.
- Also checked: scrim tap closes the menu; Home layer blurred under menu and folder. Menu of the centre-column child is right-aligned because its centre equals half the screen (View rule `>=`).

## Shared docs updated (filled in at Workflow step 7)

`AGENTS.md`, `project_structure.md`, `lessons/design-system.md` (JH3b), `knowledgements/JH3b-compose-home-menu-folder.md` (new).
