---
id: "BUG-50"
title: "Opening a project runs every .rockspec in it with full standard-library access, without asking for project trust"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-50: Opening a project runs every .rockspec in it with full standard-library access, without asking for project trust

## 1. Reproduction
1. Configure a Lua runtime.
2. Open a project containing `x.rockspec` with `os.execute("touch /tmp/pwned")` as its first line,
   or `io.open(...)`.
3. Rockspec discovery runs `RockspecBridge.read` on the file: the command runs. No trust prompt is
   shown, and none is consulted.

Seen live (2026-11-08): opening `~/Documents/src/lua/test` evaluated luacheck's spec fixtures, such as
`corpus/luacheck/spec/samples/bad.rockspec`, with the project interpreter.

## 2. Expected vs Actual Behavior
- **Expected**: no project-supplied code runs until the project is trusted (the platform's
  `TrustedProjects`, as the Gradle and Maven importers use it). Even then, a rockspec is evaluated as
  declarative data: in an environment holding only what a rockspec legitimately needs, with no `os`,
  `io`, `require`, `load` or `dofile`.
- **Actual**: every `.rockspec` in project scope is executed on discovery. `src/main/resources/lua/rockspec.lua`
  is described as a sandbox, but its environment is `setmetatable({}, { __index = _G })`, so every
  global, `os.execute` and `io.open` included, is reachable.

## 3. Root Cause
- `src/main/resources/lua/rockspec.lua:18` lets reads fall through to `_G`.
- `RockspecBridge.read` (`src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt:36`)
  launches the interpreter without a trust check, and `git grep -i isTrusted -- src/main` finds none
  anywhere.
- `LuaRockspecDiscoveryService.kt:85` calls it for every included `.rockspec`. With the default empty
  include/exclude globs, that is all of them, test fixtures included.
- `LuaRocksBridgeFiles.ensureExtracted` copies the bridge scripts into the IDE's temp directory and
  skips any file already there, so an upgraded plugin kept running the previous version's
  `rockspec.lua`. A sandbox fix in the script alone would never have reached an existing install.

## 4. Fix Strategy
- `RockspecBridge.read` returns nothing while `TrustedProjects.isProjectTrusted` is false, and logs
  that once per project. `LuaRockspecTrustListener` (on `TrustedProjectsListener`) refreshes
  `RockspecSourcePathProvider` and the PSI caches when the project becomes trusted.
- `rockspec.lua` evaluates the rockspec in an empty table, as `luarocks.core.persist.load_into_table`
  does: no standard library is reachable and an unknown global reads as `nil`. Over the test
  project's rockspecs, every one that evaluated before evaluates to the same JSON, compared as
  parsed data.
- The first `ensureExtracted` of each IDE run overwrites every bridge script.

## 5. Test Strategy
- An untrusted project: the bridge is not run.
- A trusted project: a rockspec that calls `os.execute` or `io.open` fails to evaluate, and nothing is
  written.
- The corpus's real rockspecs still evaluate to the same package, version and dependencies (checked
  by comparing the old and new bridge's output; the existing rocks tests evaluate real rockspecs).
- `RockspecBridgeSandboxTest` covers the first two; `LuaRocksBridgeFilesTest` checks that a stale
  extracted script is replaced.
