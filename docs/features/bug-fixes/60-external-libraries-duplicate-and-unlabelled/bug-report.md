---
id: "BUG-60"
title: "External Libraries lists the standard library twice and the search trees as bare, mismatched version folders"
type: "bug"
parent_id: "BUG"
status: "todo"
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
- Keep one stdlib provider: `LuaLibraryProvider` (it also supplies `getRootsToWatch`). Drop
  `PlatformLibrary` from `PlatformLibraryProvider.getAdditionalProjectLibraries`; keep
  `PlatformLibraryIndex`, which other code uses. Label it with the target, e.g. *Lua 5.1 standard
  library*, located at the root's path.
- Give each search-path directory its own `SyntheticLibrary`, labelled with its full path, under a
  clearer name such as *Lua search path*.
- Derive the default source path's version from the target's language level instead of `5.4`.

## 5. Test Strategy
Light-fixture tests over `AdditionalLibraryRootsProvider.EP_NAME` extensions: the stdlib root
appears in exactly one library; two search-path directories with the same last segment give two
distinctly labelled libraries; a 5.1 target with no source path yields `5.1` default directories.
