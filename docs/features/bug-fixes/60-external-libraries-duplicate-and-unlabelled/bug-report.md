---
id: "BUG-60"
title: "External Libraries lists the standard library twice and the search trees as bare, mismatched version folders"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-60: External Libraries is confusing

## 1. Reproduction
Open a Lua project whose target is Lua 5.1 and whose source path is the default (seen 2026-11-08).
Expand *External Libraries* in the Project view.

## 2. Expected vs Actual Behavior
- **Expected**: one node for the standard library of the configured target, and one node per
  search-path directory, each identifiable by its path and matching the target's version.
- **Actual**:
  - *Lua External API Stubs — Lua Stubs* › `lua-5.1`
  - *Lua 5.1 — Lua Standard Library* › `lua-5.1` (the same directory again)
  - *Search Trees — Search Trees* › `5.4`, `5.4` (two different directories, same label, and a
    version that is not the target's)

## 3. Root Cause
- **Duplicate stdlib node.** Two `<additionalLibraryRootsProvider>` registrations
  (`plugin.xml:541-542`) return the same root, `RuntimeLibraryProvider.getLibraryRoot(target)`:
  `PlatformLibraryProvider.PlatformLibrary` (`project/PlatformLibraryProvider.kt`) and
  `LuaLibraryProvider.LuaLibrary` (`lang/library/LuaLibraryProvider.kt`). Neither `equals` sees
  the other's class, so both are shown.
- **Search Trees shows bare folder names.** `ExternalLibraries` puts every leading path of
  `PathConfiguration.getStaticSourcePathPatterns` into one library; the Project view labels each
  root by its last segment, so `/usr/local/share/lua/5.4` and `/usr/local/lib/lua/5.4` both read
  `5.4`. Its presentable text and location string are the same word.
- **Version mismatch.** With no configured source path the patterns fall back to
  `PathConfiguration.DEFAULT_SOURCE_PATH` (`lang/path/SourcePathPattern.kt:8`), hard-coded to
  `5.4` whatever the target's language level is.

## 4. Fix Strategy
- `PlatformLibraryProvider` no longer returns the target's standard library; `LuaLibraryProvider`
  is its only provider, titled by the target (`Lua 5.1`, or `Redis 7+` for a host target) with
  *standard library* as its location.
- Each search-path directory outside the project is its own `SearchPathLibrary`, titled by its
  path (home shown as `~`) with *Lua search path* as its location.
- `DEFAULT_SOURCE_PATH` writes the version as `{LUA_VERSION}` (`PathConfiguration.LUA_VERSION_MACRO`),
  which `LuaProjectSettings.State.expandLuaPath` replaces with the target's `major.minor`. It is not
  written `$LUA_VERSION$`, because the IDE reports any `$NAME$` in stored settings as an undefined
  path variable.
- Pointing the default path at the target's real tree exposed a resolution flaw. The module
  resolver's file-name fallback accepted any `util.lua` for a bare `require("util")`, so with
  busted installed it resolved to `luassert/util.lua`. Each search-path directory outside the
  project now gets TARGET-12's rule for the runtime root: a module resolves only to the file at its
  relative path (`PlatformLibraryIndex.searchPathRoots`, `resolveModuleCandidates`).

## 5. Test Strategy
`ExternalLibrariesPresentationTest`: across every `AdditionalLibraryRootsProvider`, the standard
library's root is in exactly one library; two search-path directories both named `5.4` give two
differently titled libraries; a 5.1 target with the default search path yields `5.1` directories and
no `5.4`. `SearchPathRequireTest`: in a search-path directory holding `luassert/util.lua`, a bare
`require("util")` does not reach it and `require("luassert.util")` does. It fails when the
search-path directories are not held to the exact-path rule.
