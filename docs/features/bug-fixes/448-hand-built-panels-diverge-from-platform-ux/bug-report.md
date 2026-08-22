---
id: "BUG-448"
title: "Hand-built settings panels, dialogs and tool windows diverge from the JetBrains Platform UX standard"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-448: every surface the platform renders for us is right; every surface we hand-build drifts

Filed from a **live visual audit** on 2026-08-22 — GoLand 2026.1.3, Islands Dark (the 2026.1
default), 1920×1080, `lunar 0.18.0` in a `runIde` sandbox. Each finding below was measured from a
screenshot and paired against a native GoLand surface captured in the same session at the same size.
Raw captures and side-by-side composites: `~/.cache/claude-scratch/lunar/90c40f9b/shots/`.

## 1. Reproduction

Open any Lua settings page, provisioning dialog, or Lua tool window and compare against the
equivalent platform surface (*Appearance*, *Path Variables*, *Plugins*, *Problems*).

## 2. Expected vs actual

- **Expected**: Lua surfaces are visually indistinguishable in idiom from platform surfaces —
  sentence-case labels, aligned columns, panels that fill their container, flat action toolbars.
- **Actual**: fourteen measurable divergences, listed below. They cluster on **hand-assembled** panels.
  Two Lua pages the platform builds for us (*Editor ▸ Code Style ▸ Lua*, via `CustomCodeStyleSettings`;
  *Editor ▸ General ▸ Smart Keys ▸ Lua*, via `BeanConfigurable`) are **correct** — and both use colons
  and sentence case, which our hand-built pages do not. That split is the finding.

## 3. Findings

| # | Divergence | Measured | Root cause |
| :- | :- | :- | :- |
| 1 | Title-Case control labels | native 13/13 sentence case on one page; ours 2/2 Title Case | `LuaBundle.properties:71-72` — `Enable Type Inference`, `Add Additional Completions` |
| 2 | Ragged columns in a row list | **85px** spread over 4 rows; native Plugins list 0px | `LuaDefinitionLibrariesConfigurable.kt:45-70` |
| 3 | Ragged combo column in a dialog | **90px** over 6 combos, while the 3 labelled rows above align at x=921 | `LuaProvisionDialog.kt:171-179` |
| 4 | Panel does not fill the page | **35%** of content width vs native **95%** | `LuaRedisConnectionsConfigurable.kt:68-70` |
| 5 | Bordered push-buttons in a tool window | 3 bordered `JButton`s | `LuaRedisFunctionsPanel.kt:72-88` |
| 6 | Dialog narrower than its own title | renders `Provision …on Matrix` | `LuaBatchProvisionDialog.kt:34` + `:59-62` |
| 7 | Path fields too narrow to show a path | `ome/mini/uiaudit/.lua`, `/.lua-matrix` | both provision dialogs |
| 8 | Tools table stops short of the page | **235px** short of the group rules on the same page; `Path`/`Origin` elided to `/usr/loca…`, `Discover…` | `LuaToolchainInventoryTable.kt` — no column-width model |
| 9 | Label missing its colon | `Arguments` beside `Lua:`, `StyLua:`, `Busted:` | `LuaBundle.properties:86` |
| 10 | Group titles inconsistent within one page | `Platform Target` / `Toolchain Bindings` / `Resolved Runtime` vs `Advanced tools` | `LuaProjectConfigurable.kt:70-99` |
| 11 | Run-config editor: no colons; one `ExpandableTextField` at 160px beside two identical ones at 374px | 8/8 labels lack colons while the platform's own `Name:` row in the same dialog has one | `LuaRunConfiguration.kt` (`FormBuilder`) |
| 12 | Dependency tool window: four bordered `JButton`s where the platform uses a flat `ActionToolbar` | 3 icon-only bordered buttons + a filter field that reads as a fourth, empty button | `DependencyTreePanel.kt:67-86` |
| 13 | Filter field has no `emptyText` and no search icon, so it renders as a blank bordered box | — | `DependencyTreePanel.kt:83` — `filterField.apply { columns = 16 }` |
| 14 | Empty state is an HTML italic string in a label, left-aligned at top | platform empty text is centred, dimmed, non-italic, no trailing period | `DependencyInspectorPanel.kt:27` — `"<html><body><i>Select a dependency.</i></body></html>"` |

### The dominant root cause (#2 and #3 are the same defect)

Both the ragged Definition-Libraries columns and the ragged provisioning combos come from the same
Kotlin UI DSL behaviour. A **labelled** row (`row("Name:") { … }`) participates in a shared label
grid; a **label-less** row (`row { cell(a); cell(b) }`) defaults to `RowLayout.INDEPENDENT` and sizes
itself alone. Every ragged surface is a stack of label-less `row { cell(…) cell(…) }`:

```kotlin
// LuaProvisionDialog.kt:171
row { cell(includeLuaRocksBox); cell(luaRocksVersionCombo) }
// LuaDefinitionLibrariesConfigurable.kt:45
row { cell(box); cell(status); cell(license); cell(link) }
```

Adding `.layout(RowLayout.PARENT_GRID)` to those rows makes them share one grid and fixes both. That
is the single highest-value change in this report.

## 4. Fix strategy

Grouped so each can land independently:

1. **Row alignment** — `.layout(RowLayout.PARENT_GRID)` on the label-less rows in
   `LuaProvisionDialog` and `LuaDefinitionLibrariesConfigurable` (#2, #3). For Definition Libraries a
   `JBTable` is the more idiomatic end state, but `PARENT_GRID` is the one-line fix.
2. **Fill and width** — replace `BorderLayout.WEST` with an `OnePixelSplitter` in
   `LuaRedisConnectionsConfigurable` (#4); give the toolchain table a column-width model and let it
   fill (#8); widen the two path fields (#7); give `LuaBatchProvisionDialog` a minimum width that
   admits its title (#6).
3. **Components** — swap the three `JButton`s for an `ActionToolbar` (#5).
4. **Text** — sentence-case the two bundle labels, add the missing colon, and settle group-title case
   in one pass (#1, #9, #10). `Advanced tools` is already the sentence-case form TOOLING-08 minted;
   make the rest match it, not the other way round.
5. **Run config** — add colons and size the three `ExpandableTextField`s alike (#11).
6. **Dependency tool window** — `ActionToolbar` for the three actions, `SearchTextField` (or
   `emptyText`) for the filter, and `JBPanelWithEmptyText` for the inspector's empty state
   (#12, #13, #14). Same shape as group 3.

Groups 1 and 2 are what a user actually notices. Group 4 is cheap and touches only strings.

## 5. Test strategy

Most of these are not unit-testable and **that is the point** — they survived because the suite
cannot see them. Two things are testable and worth adding:

- **Alignment**: assert the built `DialogPanel`'s rows report a shared grid — or, cheaply, assert the
  x-origin of the second cell is equal across the label-less rows.
- **Text conventions**: a bundle test asserting no `LuaBundle` control label matches
  `^(?:[A-Z][a-z]+ ){1,}[A-Z][a-z]+$` (Title Case) beyond an allow-list of product names
  (`LuaRocks`, `StyLua`, `LuaCov`, `Lua Language Server`). This catches #1 and future regressions,
  and is the only finding here a CI gate can hold.

For the rest, the gate is the `verify-in-ide` screenshot pass. Recommend adding a UI clause to
[`docs/engineering-contract.md`](../../../engineering-contract.md) — it currently has **no** UI
section at all, which is why this class of defect recurs (BUG-363/365/367/368/369 were all the same
theme).

## 6. Notes

- **Two findings were investigated and dropped**, recorded here so nobody re-opens them: our group
  indent is **pixel-identical** to the platform (+24 in both), and our control column staggering
  across groups is **matched by the native Appearance page** (791/904/807/901), so it is platform
  behaviour rather than our defect.
- The blank Rocks detail pane found in the same audit is a genuine Swing parenting bug and is filed
  separately as [[bug-report|BUG-449]].
- **Not exercised**, so unaudited: the Matrix results tool window (needs a matrix run), and the
  `Lua Tests` / `LuaRocks` / Redis run-config editors (only the `Lua` one was opened). The LuaRocks
  dependency tool window **was** audited on the second attempt — it is registered under the display
  name *"LuaRocks Dependencies"*, not *"LuaRocks"*, which is why the first lookup missed it.
