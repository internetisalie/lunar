---
id: "ROCKS-16-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "ROCKS-16"
folders:
  - "[[features/rocks/16-package-browser-redesign/requirements|requirements]]"
---

# Verification Checklists: ROCKS-16 — Plugins-Style LuaRocks Package Browser Redesign

Run via the `verify-in-ide` VNC flow against the containerized GoLand with a LuaRocks project
(a project with a `lua_modules/` tree) open. UI phases (4, 5, 6) can only be confirmed here.

## 1. Canonical install target (load-bearing)

### Scenario 1.1: Browser install lands in the project tree
- **Setup**: A LuaRocks project with `lua_modules/`; `luarocks` bound via Toolchain settings.
- **Steps**:
  1. Open the LuaRocks Packages tool window, Marketplace tab, search `inspect`, select it, Install.
  2. Inspect `lua_modules/lib/luarocks/rocks-*/inspect/` on disk.
  3. Open the LuaRocks Dependencies tool window (ROCKS-03) and refresh.
- **Expected**: the rock appears under the project `lua_modules/` tree AND in the dependency tree /
  External Libraries — not in the binary's default global tree.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Two-tab surface & differentiation

### Scenario 2.1: Marketplace/Installed tabs and target-tree strip
- **Setup**: same project.
- **Steps**: open the browser; note the two tabs and the north strip showing the active tree path.
- **Expected**: Marketplace + Installed tabs render in the Plugins idiom; the strip shows
  `.../lua_modules`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Tool-window names are unambiguous (BUG-366)
- **Steps**: hover both tool-window stripes.
- **Expected**: browser stripe reads "LuaRocks Packages"; dependency stripe reads
  "LuaRocks Dependencies"; tooltips describe each role.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Detail pane parity (BUG-363/365/367/368)

### Scenario 3.1: Font, alignment, empty state, clickable deps
- **Steps**:
  1. Before selecting anything, view the detail pane (empty state).
  2. Select a package with dependencies.
  3. Click a dependency row.
- **Expected**: empty state is a centered `JBPanelWithEmptyText` ("No package selected"), not a
  `(no package selected)` label; selected detail uses the standard UI font on a consistent grid;
  dependencies render as a list; clicking a dependency selects/searches it.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Honest error state

### Scenario 4.1: Unresolved binary shows Configure link, not "No packages found"
- **Setup**: unbind/clear the `luarocks` tool in Toolchain settings.
- **Steps**: search any term.
- **Expected**: the panel shows an error state with the not-configured message and a Configure
  link that opens Settings → Languages & Frameworks → Lua → Toolchain. NOT "No packages found".
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Installed tab & in-place refresh

### Scenario 5.1: Zero-query Installed tab + inline uninstall
- **Steps**: open the Installed tab (no query); uninstall a rock inline.
- **Expected**: installed rocks list immediately; uninstall drops the row in place without a
  re-search, and the on-disk tree loses the rock.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.2: Install flips Marketplace row in place
- **Steps**: install a rock from the Marketplace tab and watch its row.
- **Expected**: the row's installed badge flips immediately (no manual re-search).
- **Result**: ⬜ Pass / ⬜ Fail

## 6. Update affordance (Should)

### Scenario 6.1: Update badge/button appears for an outdated rock
- **Setup**: install an older version of a rock that has a newer one available.
- **Steps**: view it in the Installed tab / detail pane.
- **Expected**: an Update badge + button surfaces; clicking Update installs the latest into the
  same tree.
- **Result**: ⬜ Pass / ⬜ Fail
