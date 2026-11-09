---
id: "BUG-62"
title: "A bare require resolves to a nested file inside an installed library"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-62: A bare `require` resolves to a nested file inside an installed library

## 1. Reproduction
With busted installed in the project's rocks tree or on the search path, `require("util")` in project
code resolves to `luassert/util.lua` (seen on the builder's `/usr/local/share/lua/5.1`, 2026-11-09).

## 2. Expected vs Actual Behavior
- **Expected**: library code is loaded by Lua's own path rule, so `luassert/util.lua` is reachable
  only as `require("luassert.util")`.
- **Actual**: `resolveModuleCandidates` falls back to every `util.lua` the file index knows. It
  accepts any of them for a module name without a dot, whether it is project code or library code.

## 3. Root Cause
The fallback's lenient bare-name match was meant for project code, whose loading Lunar can only
guess (working directory, rockspec mapping, test runner). It applied to library files as well.
TARGET-12 had exempted only the target's runtime stubs (`runtimeRootVerdict`). The installed-rocks
library, definition libraries and search-path directories kept the lenient match.

## 4. Fix
- `LuaLibraryRoots` collects the source roots of every library a Lunar `AdditionalLibraryRootsProvider`
  adds, cached on `ProjectRootManager`.
- `libraryRootVerdict` replaces `runtimeRootVerdict`. A candidate under a library root (the deepest
  one holding it) matches only at `<module path>.lua` or `<module path>/init.lua` relative to that
  root. A project file keeps the lenient match.

## 5. Test
`LibraryRequireScopeTest` registers a library through a fake Lunar provider, so it covers any
library kind:
- a bare `require("util")` does not reach its `luassert/util.lua`, and `require("luassert.util")` does;
- a project `helpers/util.lua` still matches `util`;
- with the library rule disabled, the first case fails.

The TARGET-12 verdict cases in `LuaJitRuntimeStubsTest` and BUG-60's `SearchPathRequireTest` still
pass.
