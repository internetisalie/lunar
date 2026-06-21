---
id: "ROCKS-09-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "ROCKS-09"
folders:
  - "[[features/rocks/09-workspace-discovery/requirements|requirements]]"
---

# Verification Checklists: ROCKS-09 — Multi-Rock Workspace Discovery

## 1. Multi-Rock Discovery in the IDE

### Scenario 1.1: Kernel/v0 forest in the dependency tool window
- **Setup**: open `~/Documents/Kernel/v0` (10 rocks under `rocks/<name>/`, no root rockspec) in
  GoLand with the Lunar plugin; configure a `lua` interpreter so the rockspec bridge runs.
- **Steps**:
  1. Open the Lua dependency tool window.
  2. Click Refresh; wait for "Resolving dependencies…" to clear.
- **Expected**: 10 root nodes appear under "Lua dependencies" — `adt`, `channels`, `cmd`,
  `meteor`, `pipe`, `platform`, `ramdisk`, `runtime`, `ssdpd`, `utils` — each expandable to its
  own dependencies. (Before ROCKS-09 the tree was empty.)
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Vendored / installed rockspecs are excluded
- **Setup**: in a single-rock project, run `luarocks make --tree=lua_modules` so
  `lua_modules/lib/luarocks/rocks-5.4/<pkg>/<ver>/<pkg>-<ver>.rockspec` exists; also add a
  `thirdparty/<lib>/<lib>-1.0-1.rockspec`.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: only the project's own source rock appears as a root; no node sourced from
  `lua_modules/`, `.luarocks/`, or `thirdparty/`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Missing dependency still flagged per root
- **Setup**: a rock whose rockspec depends on a package that is not installed.
- **Steps**:
  1. Refresh the tool window; expand that rock.
- **Expected**: the missing dependency node is flagged `MISSING_DEPENDENCY` (parity with the old
  single-root TC-ROCKS-03-05), now under the correct root in the forest.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Settings & Configuration

### Scenario 2.1: Override excludes a directory
- **Setup**: a project with `a/a-1.0-1.rockspec` and `vendor/v-1.0-1.rockspec`. Add
  `<rockspecExcludeGlobs><option value="vendor/**" /></rockspecExcludeGlobs>` to the
  `LuaProjectSettings` block in `.idea/lunar.xml`; reopen the project.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: only `a` appears as a root; `vendor/v` is not discovered.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Default needs no configuration
- **Setup**: a fresh multi-rock project with no `rockspec*Globs` entries in `lunar.xml`.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: all source rocks discovered with zero configuration (default = recursive minus
  built-in excludes).
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Scaffolding Removal

### Scenario 3.1: New-project generator has no Workspace option
- **Setup**: File ▸ New ▸ Project ▸ Lua/LuaRocks generator.
- **Steps**:
  1. Inspect the generator panel.
  2. Create a project.
- **Expected**: no "Workspace" radio / "Workspace name" / "Initial rocks" fields; the generator
  scaffolds a single rock (rockspec, `src/`, optional Makefile/busted) and writes **no**
  `workspace.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Performance / Responsiveness

### Scenario 4.1: No EDT freeze on a large multi-rock project
- **Setup**: `Kernel/v0` (or a 10+ rock project).
- **Steps**:
  1. Refresh the dependency tool window and immediately interact with the editor.
- **Expected**: the IDE stays responsive (resolution runs on a pooled thread with a status
  label); no "slow operation on EDT" / freeze report.
- **Result**: ⬜ Pass / ⬜ Fail
