---
id: "ROCKS-09-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "done"
parent_id: "ROCKS-09"
folders:
  - "[[features/rocks/09-workspace-discovery/requirements|requirements]]"
---

# Verification Checklists: ROCKS-09 — Multi-Rock Workspace Discovery

> **VERIFICATION RUN — 2026-06-24 (containerized GoLand 2026.1.3, build 261.25134.147, plugin
> `lunar 1.0.0-SNAPSHOT`, trial-licensed; `verify-in-ide` skill over VNC).**
>
> **Final verdict: PASS (all 7 scenarios), after fixing one defect found during the run.**
>
> **Initial run found a blocking defect:** the LuaRocks **dependency tool window** threw on every
> refresh, so the multi-rock forest never rendered (scenarios 1.1, 1.2, 1.3, 2.1, 2.2, 4.1 all
> failed; 3.1 passed independently). Root cause:
> `LuaRockspecDiscoveryService.compute()` accessed the file index / VFS
> (`FilenameIndex.getAllFilesByExt`) on `DependencyTreePanel.refresh`'s pooled thread **without a
> read action**, throwing `Read access is allowed from inside read-action only`
> (`SEVERE … Plugin to blame: lunar … Last Action: ActivateLuaRocksToolWindow`). Reproduced on two
> projects. This violated engineering-contract §1 ("Read PSI/VFS in `runReadAction { }`"). The unit
> suite passed because the test harness supplies read access — exactly the gap this live gate exists
> to close.
>
> **Fix applied (implementer subagent, reviewer subagent PASS on all gates):** `compute()` was split
> so the index/VFS enumeration runs inside
> `ReadAction.nonBlocking { enumerateIncluded(...) }.expireWith(project).executeSynchronously()`,
> while the blocking `lua` bridge subprocesses (`RockspecBridge.read`, up to 10 s each ×N) run
> **outside** the read action — a short read action that never spans the subprocess loop.
> `./gradlew buildPlugin` green; 23/23 targeted unit tests green; reviewer 6/6 gates PASS.
>
> **Re-verified live** (hot-swapped the fixed jar, clean-relaunched per project): all six
> resolution-dependent scenarios now render correctly with **zero** new threading errors in the
> log. Per-scenario evidence below.

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
- **Result**: ☒ **PASS** (after fix) — opened `~/kernel-v0`; LuaRocks tool window → Refresh. All
  **10 roots** render under `Lua dependencies`: `adt, channels, cmd, meteor, pipe, platform,
  ramdisk, runtime, ssdpd, utils`, each expandable (each shows a `lua (missing)` child, expected
  since kernel-v0 has no installed `lua_modules` tree). Zero new threading errors in the log.
  *(Initial run before the fix: FAIL — empty tree, `Read access is allowed from inside
  read-action only` from `LuaRockspecDiscoveryService.compute:62` via `DependencyTreePanel.refresh`.)*

### Scenario 1.2: Vendored / installed rockspecs are excluded
- **Setup**: in a single-rock project, run `luarocks make --tree=lua_modules` so
  `lua_modules/lib/luarocks/rocks-5.4/<pkg>/<ver>/<pkg>-<ver>.rockspec` exists; also add a
  `thirdparty/<lib>/<lib>-1.0-1.rockspec`.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: only the project's own source rock appears as a root; no node sourced from
  `lua_modules/`, `.luarocks/`, or `thirdparty/`.
- **Result**: ☒ **PASS** (after fix) — opened `~/excl-vendored` (`mylib-1.0-1.rockspec` +
  `lua_modules/.../foo-1.0-1.rockspec` + `thirdparty/bar/bar-1.0-1.rockspec`); Refresh. Only
  **`mylib`** appears as a root; neither `foo` (under `lua_modules/`) nor `bar` (under
  `thirdparty/`) is discovered. Built-in directory-segment exclusion confirmed live.

### Scenario 1.3: Missing dependency still flagged per root
- **Setup**: a rock whose rockspec depends on a package that is not installed.
- **Steps**:
  1. Refresh the tool window; expand that rock.
- **Expected**: the missing dependency node is flagged `MISSING_DEPENDENCY` (parity with the old
  single-root TC-ROCKS-03-05), now under the correct root in the forest.
- **Result**: ☒ **PASS** (after fix) — opened `~/missing-dep/app-1.0-1.rockspec` (depends on
  `nonexistent-lib >= 2.0`); Refresh. Root **`app`** expands to **`nonexistent-lib (missing)`** (and
  `lua (missing)`), both rendered with the warning icon — the uninstalled dependency is flagged
  under its correct root (MISSING_DEPENDENCY parity).

## 2. Settings & Configuration

### Scenario 2.1: Override excludes a directory
- **Setup**: a project with `a/a-1.0-1.rockspec` and `vendor/v-1.0-1.rockspec`. Add
  `<rockspecExcludeGlobs><option value="vendor/**" /></rockspecExcludeGlobs>` to the
  `LuaProjectSettings` block in `.idea/lunar.xml`; reopen the project.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: only `a` appears as a root; `vendor/v` is not discovered.
- **Result**: ☒ **PASS** (after fix) — opened `~/glob-override` (`a/a-1.0-1.rockspec`,
  `vendor/v-1.0-1.rockspec`, and `.idea/lunar.xml` carrying
  `<option name="rockspecExcludeGlobs"><list><option value="vendor/**" /></list></option>`);
  Refresh. Only **`a`** appears as a root; `vendor/v` is excluded by the override glob. Live
  override confirmed.

### Scenario 2.2: Default needs no configuration
- **Setup**: a fresh multi-rock project with no `rockspec*Globs` entries in `lunar.xml`.
- **Steps**:
  1. Refresh the dependency tool window.
- **Expected**: all source rocks discovered with zero configuration (default = recursive minus
  built-in excludes).
- **Result**: ☒ **PASS** (after fix) — `~/kernel-v0` is exactly this case (10 source rocks, no
  `rockspec*Globs` in `lunar.xml`). After the fix all 10 rocks are discovered with zero
  configuration (see 1.1). Default = recursive minus built-in excludes, confirmed.

## 3. Scaffolding Removal

### Scenario 3.1: New-project generator has no Workspace option
- **Setup**: File ▸ New ▸ Project ▸ Lua/LuaRocks generator.
- **Steps**:
  1. Inspect the generator panel.
  2. Create a project.
- **Expected**: no "Workspace" radio / "Workspace name" / "Initial rocks" fields; the generator
  scaffolds a single rock (rockspec, `src/`, optional Makefile/busted) and writes **no**
  `workspace.lua`.
- **Result**: ☒ **PASS** / ⬜ Fail — File ▸ New ▸ Project ▸ **LuaRocks** generator panel shows only
  **Project type: Library / Application** and **Options: Loader Setup / Busted Configuration /
  Makefile**. No "Workspace" radio, no "Workspace name", no "Initial rocks" field. Created
  `genrock` (Library, +Busted +Makefile) → scaffold: `genrock-scm-1.rockspec`, `src/genrock.lua`,
  `spec/genrock_spec.lua`, `Makefile`, `.gitignore`, `lua_modules/`. `find -iname workspace.lua` →
  **none**. Confirms ROCKS-09-06 (workspace scaffolding removed) live.

## 4. Performance / Responsiveness

### Scenario 4.1: No EDT freeze on a large multi-rock project
- **Setup**: `Kernel/v0` (or a 10+ rock project).
- **Steps**:
  1. Refresh the dependency tool window and immediately interact with the editor.
- **Expected**: the IDE stays responsive (resolution runs on a pooled thread with a status
  label); no "slow operation on EDT" / freeze report.
- **Result**: ☒ **PASS** (after fix), with scope caveat — refreshed `~/kernel-v0` (the scenario's
  "10+ rock" workload) and interacted with the editor/tree immediately; the IDE stayed responsive,
  no "slow operation on EDT" / freeze report. The freeze-safety guarantee is **structural**:
  resolution runs off the EDT via `executeOnPooledThread`, now holding only a *short* non-blocking
  read action for index enumeration with the blocking `lua` subprocesses outside it (reviewer-
  confirmed). Caveat: 10 small rockspecs resolve in ~tens of ms (prior spike measured the forest at
  ~42 ms), so this exercised the specified workload but was **not** a heavy-load stress test; a very
  large forest was not load-tested.
