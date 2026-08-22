---
id: "BUG-448"
title: "Hand-built settings panels, dialogs and tool windows diverge from the JetBrains Platform UX standard"
type: "bug"
parent_id: "BUG"
status: "in_progress"
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
- **Actual**: twenty-four measurable divergences, listed below. They cluster on **hand-assembled** panels.
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
| 11 | Run-config editors: **no label carries a colon**, while the platform's own `Name:` row in the same dialog does | **27/27** labelled rows across all four editors, 0 with a colon | `LuaRunConfiguration.kt` (8), `LuaTestRunConfiguration.kt` (7), `LuaRocksRunConfiguration.kt` (5), `LuaRedisRunConfiguration.kt` (7) — `FormBuilder.addLabeledComponent` does not append one |
| 12 | Dependency tool window: four bordered `JButton`s where the platform uses a flat `ActionToolbar` | 3 icon-only bordered buttons + a filter field that reads as a fourth, empty button | `DependencyTreePanel.kt:67-86` |
| 13 | Filter field has no `emptyText` and no search icon, so it renders as a blank bordered box | — | `DependencyTreePanel.kt:83` — `filterField.apply { columns = 16 }` |
| 14 | Empty state is an HTML italic string in a label, left-aligned at top | platform empty text is centred, dimmed, non-italic, no trailing period | `DependencyInspectorPanel.kt:27` — `"<html><body><i>Select a dependency.</i></body></html>"` |
| 15 | Raw enum names leak into combos as display values — `BUSTED`, `FILE` | 2 combos on one page | `LuaTestRunConfiguration.kt:301` (`ComboBox(LuaTestFramework.entries…)`, no renderer) and `:302` (`arrayOf("FILE","DIRECTORY","PATTERN")`) |
| 16 | Checkbox labels carry parenthetical implementation detail and a raw protocol keyword as the label | 3 checkboxes | `LuaRedisRunConfiguration.kt:321,325,326` — e.g. `REPLACE (overwrite existing library)` |
| 17 | Format hints live in the label rather than in `comment()`/`emptyText` | `KEYS (space-separated)`, `ARGV (space-separated)`, `Function name (FCALL)` | `LuaRedisRunConfiguration.kt` |
| 18 | `Connection` combo is 72px — the narrowest control on its page, and the only one that must display arbitrary text | 72px vs 360px siblings | `LuaRedisRunConfiguration.kt` |
| 19 | The same field is labelled inconsistently across editors | `Environment variables` (Lua, Lua Tests) vs `Environment` (LuaRocks) — native Go Build uses `Environment:` | all four editors |
| 20 | No mnemonics on any label | native Go Build underlines 10/10 (R̲un kind, F̲iles, O̲utput directory…); ours 0/27 | all four editors (`FormBuilder.addLabeledComponent` takes no mnemonic) |
| 21 | Matrix tool window shows its **raw internal id** as its title | `Lunar.LuaMatrix`, beside `LuaRocks Packages` / `LuaRocks Dependencies` / `Redis Functions` | `plugin.xml:759` — the id doubles as the display name and this one is dotted |
| 22 | Run status rendered as bare uppercase text with no icon | `FAIL` | `MatrixResultsToolWindow.kt` — same enum-leak family as #15 |
| 23 | Column header abbreviated | `Exit` for what is an exit code | `MatrixResultsToolWindow.kt` |
| 24 | Table columns evenly distributed regardless of content — **second instance of #8** | 231/230/230/229px; the `Exit` column holds one character and gets the same width as a rockspec filename | `MatrixResultsToolWindow.kt` — no column-width model, exactly as `LuaToolchainInventoryTable.kt` |

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
   `LuaRedisConnectionsConfigurable` (#4); give **both** tables a column-width model and let them
   fill (#8, #24 — one change applied twice); widen the two path fields (#7); give `LuaBatchProvisionDialog` a minimum width that
   admits its title (#6).
3. **Components** — swap the three `JButton`s for an `ActionToolbar` (#5).
4. **Text** — sentence-case the two bundle labels, add the missing colon, and settle group-title case
   in one pass (#1, #9, #10). `Advanced tools` is already the sentence-case form TOOLING-08 minted;
   make the rest match it, not the other way round.
5. **Run config editors** — add the colon to all 27 labelled rows (#11); give the framework and
   target-type combos a renderer so enum names stop reaching the user (#15); move parenthetical
   detail and format hints out of labels into `comment()`/`emptyText` (#16, #17); widen the
   `Connection` combo (#18); settle on one label for the environment field (#19); and add
   mnemonics (#20).
6. **Dependency tool window** — `ActionToolbar` for the three actions, `SearchTextField` (or
   `emptyText`) for the filter, and `JBPanelWithEmptyText` for the inspector's empty state
   (#12, #13, #14). Same shape as group 3.
7. **Matrix tool window** — give it a display name instead of `Lunar.LuaMatrix` (#21), render
   status as icon + sentence case rather than `FAIL` (#22), and spell out `Exit code` (#23).

Groups 1 and 2 are what a user actually notices. Group 4 is cheap and touches only strings.

### Delivery status

**Batch A — groups 1, 2 and 7 are done** (#2, #3, #4, #6, #7, #8, #21, #22, #23, #24). Groups 3, 4,
5 and 6 remain. One deviation from the strategy above: #21 is fixed by naming the stripe in
`MatrixResultsToolWindow.init`, not by changing the `<toolWindow>` id — the id is persisted layout
state and `RunMatrixAction`'s lookup key, and `ToolWindow.setStripeTitle` is what the platform
provides for exactly this (`EventWatcherToolWindowFactory` does the same).

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

- **Three findings were investigated and dropped**, recorded here so nobody re-opens them: our group
  indent is **pixel-identical** to the platform (+24 in both); our control column staggering
  across groups is **matched by the native Appearance page** (791/904/807/901); and the environment
  field's placeholder repeating its own label is **what native Go Build does too**, so only the
  inconsistent label survives as #19. Each was killed by measuring a native comparator rather than
  by reasoning about our code.
- The blank Rocks detail pane found in the same audit is a genuine Swing parenting bug and is filed
  separately as [[bug-report|BUG-449]].
- **Every Lua surface in the plugin was opened and measured** — settings pages, both provisioning
  dialogs, all four run-config editors, and all four tool windows. The Matrix results window was
  populated for real (native provisioner built `lua-5.5.0`; a deliberately failing `test` command in
  the fixture rockspec produced a `FAIL` row) rather than audited empty, which is what exposed #22
  and #24. Nothing on the surface list is outstanding.
- Two navigation notes for the next audit: the dependency tool window is registered as *"LuaRocks
  Dependencies"*, not *"LuaRocks"*; and the matrix window answers only to `Lunar.LuaMatrix` (#21).
