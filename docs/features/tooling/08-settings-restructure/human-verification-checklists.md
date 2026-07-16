---
id: "TOOLING-08-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TOOLING-08"
folders:
  - "[[features/tooling/08-settings-restructure/requirements|requirements]]"
---

# Verification Checklists: TOOLING-08 — Lua Settings Restructure

Run via the `verify-in-ide` VNC flow against GoLand on the builder VM. This is the DoD real-flow gate
for the appearance work that unit tests cannot cover.

## 1. Platform Target Discoverability (BUG-362)

### Scenario 1.1: Target control is visible and usable
- **Setup**: open a Lua project; discover a Standard-Lua interpreter via *Settings → Languages &
  Frameworks → Lua → Toolchain → Auto-Discover*.
- **Steps**:
  1. Open *Settings → Languages & Frameworks → Lua → Lua Project*.
  2. Locate the *Platform target* control at/near the top of the page.
  3. Change the platform combo from *Auto (from runtime)* to *Redis*.
  4. Confirm the version combo enables and lists `5`, `6`, `7+`.
  5. Pick `7+` and click *OK*.
- **Expected**: a `redis.*` / `KEYS` / `ARGV` reference now resolves in a Lua file; luacheck runs with
  `--std redis7`. Reopening the page shows platform=*Redis*, version=`7+`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Explicit target survives a re-probe
- **Setup**: from 1.1, platform pinned to *Redis*.
- **Steps**:
  1. Re-run Auto-Discover (or refresh the interpreter) so a `TOOL_UPDATED` event fires.
  2. Reopen *Lua Project*.
- **Expected**: platform is still *Redis* (not reverted to Standard).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Auto mode reflows to the runtime
- **Setup**: from 1.1.
- **Steps**:
  1. Set the platform combo back to *Auto (from runtime)*; click *OK*.
  2. Reopen the page.
- **Expected**: version combo is disabled and shows the runtime-derived version; language resolution
  follows the discovered runtime again.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Bindings Simplicity

### Scenario 2.1: Common vs advanced split
- **Setup**: registry with a `luacov` tool registered.
- **Steps**:
  1. Open *Lua Project* → *Toolchain Bindings* group.
  2. Confirm only the runtime + LuaRocks + luacheck + StyLua + Busted rows are visible.
  3. Expand the *Advanced tools* group.
- **Expected**: *Advanced tools* is collapsed by default and contains the `LuaCov` row; no
  `Redis Server` / `Valkey Server` row appears anywhere on the page.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Redis server still works despite eviction
- **Setup**: a Redis connection configured to launch a local `redis-server` binary.
- **Steps**:
  1. Trigger the Redis run/connection that resolves `redis-server`.
- **Expected**: the server launches (resolution unaffected by the UI eviction).
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Global Default Bindings

### Scenario 3.1: Global luacheck default
- **Setup**: app inventory has a `luacheck` tool; a project with no project-level luacheck binding.
- **Steps**:
  1. Open *Settings → Languages & Frameworks → Lua → Toolchain*.
  2. In *Global Default Bindings*, set luacheck to the inventory tool; click *OK*.
  3. In the project, run a luacheck-backed inspection.
- **Expected**: luacheck resolves to the globally-bound tool (banner/diagnostics show a
  global-binding source).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Layout Consistency (BUG-369)

### Scenario 4.1: Uniform vertical rhythm
- **Setup**: none.
- **Steps**:
  1. Visit each Lua settings page: *Lua*, *Toolchain*, *Lua Project*, *Redis Connections*, and the
     LuaRocks project-generator dialog.
  2. Screenshot each.
- **Expected**: row/section vertical spacing looks consistent across pages (no page noticeably tighter
  or looser). The app *Lua* page and the LuaRocks generator match the DSL pages.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Inherit Clarity

### Scenario 5.1: Explicit inherit placeholders
- **Setup**: app-level Luacheck arguments set to `--std max`.
- **Steps**:
  1. Open *Lua Project*; leave the project Luacheck-arguments field empty.
- **Expected**: the field's placeholder reads `Inherit (app default: --std max)`; the rocks-URL field
  reads `Inherit (luarocks.org)` or `Inherit (app default: <url>)`.
- **Result**: ⬜ Pass / ⬜ Fail
