---
id: "ROCKS-05-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "planned"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# Verification Checklists: ROCKS-05 — Rockspec Module Resolution

Manual, human-run scenarios in a real IDE (GoLand sandbox or the containerized IDE per the
`verify-in-ide` skill). Each is reproducible from a clean project state.

## 1. IDE require-resolution (consumer A)

### Scenario 1.1: require resolves to rockspec-mapped source
- **Setup**: project with `rocks/foo/foo-1.0-1.rockspec` declaring
  `build = { type = "builtin", modules = { ["foo.bar"] = "src/foo/bar.lua" } }`, and the file
  `rocks/foo/src/foo/bar.lua` present. A Lua interpreter is configured.
- **Steps**:
  1. Open a Lua file and type `require("foo.bar")`.
  2. Ctrl+click (or Cmd+click) the `"foo.bar"` string.
- **Expected**: navigates to `rocks/foo/src/foo/bar.lua` (no manual `LUA_PATH` configured).
- **Result**: Pass / Fail

### Scenario 1.2: editing the rockspec invalidates patterns
- **Setup**: as 1.1, after 1.1 resolved.
- **Steps**:
  1. Add `["foo.baz"] = "src/foo/baz.lua"` to the rockspec modules; create `src/foo/baz.lua`.
  2. Ctrl+click a new `require("foo.baz")`.
- **Expected**: resolves to `src/foo/baz.lua` without restarting the IDE (cache invalidated).
- **Result**: Pass / Fail

## 2. Run/Debug from source (consumer B)

### Scenario 2.1: set a breakpoint, run from source, confirm it binds
- **Setup**: as 1.1. A run configuration for a script that `require("foo.bar")` and calls into it.
  Leave the run config's own Source Path **empty** (so the `else` branch is exercised).
- **Steps**:
  1. Set a breakpoint inside `rocks/foo/src/foo/bar.lua` (e.g. on a function body line).
  2. Debug the run configuration.
- **Expected**: the process starts, `require("foo.bar")` loads `rocks/foo/src/foo/bar.lua` via the
  unioned `LUA_PATH` (local roots before installed), and the breakpoint **binds and hits**. The
  mobdebug preloader (`LUA_INIT`) still works — debugging is not broken by the path change.
- **Result**: Pass / Fail

### Scenario 2.2: local-before-installed ordering
- **Setup**: as 2.1, but also install a same-named module into the project tree
  (`lua_modules/.../foo/bar.lua`) with a distinguishable marker.
- **Steps**:
  1. Run (not debug) the script; observe which `foo.bar` is loaded (print a marker on load).
- **Expected**: the **local** `rocks/foo/src/foo/bar.lua` is loaded (local roots are prepended
  before the installed tree / trailing `;;`).
- **Result**: Pass / Fail

## 3. C-module CPATH

### Scenario 3.1: C-module rock contributes LUA_CPATH from the built tree
- **Setup**: a rock with `build = { type = "builtin", modules = { cjson = { "src/cjson.c" } } }`,
  built into the project tree (`lua_modules/lib/lua/5.4/cjson.so` present). Language level 5.4.
  A script that `require("cjson")`, run config Source Path empty.
- **Steps**:
  1. Run the script.
  2. (Optional) Inspect the launched process environment for `LUA_CPATH`.
- **Expected**: `LUA_CPATH` is `<tree>/lib/lua/5.4/?.so;;`; `require("cjson")` loads the built
  `.so`; no `src/cjson.c` appears in `LUA_PATH` or `LUA_CPATH`.
- **Result**: Pass / Fail
