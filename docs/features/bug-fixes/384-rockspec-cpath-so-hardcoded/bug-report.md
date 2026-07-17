---
id: "BUG-384"
title: "Rockspec run-path provider hardcodes `?.so` in LUA_CPATH (wrong on Windows) and reads the deprecated languageLevel"
type: "bug"
parent_id: "BUG"
priority: "low"
status: "done"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-384: Rockspec run-path provider hardcodes `?.so` and reads deprecated languageLevel

> **RESOLVED 2026-07-17 (commit 113b9ef4)**: `RockspecRunPathProvider.luaCPath` now uses `SystemInfo.isWindows` via `nativeModuleExtension()` to produce `?.dll` on Windows and `?.so` elsewhere, and derives the language level from `state.getTarget().getImplicitLanguageLevel().version` — the same source `LuaRocksLibraryProvider` uses. Added `testNativeModuleExtensionMatchesPlatform` and `testLuaCPathUsesNativeExtension` to `RockspecRunPathProviderTest`.

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#46** (still present
2026-07-17).*

## 1. Reproduction

1. On **Windows**, open a rockspec workspace with a native (C) dependency rock and run a script
   through a run configuration that uses the rockspec-derived paths.
2. Observe `LUA_CPATH` in the launched process environment.

## 2. Expected vs Actual Behavior

- **Expected**: `?.dll` extensions on Windows (`?.so` elsewhere), and the module-resolution
  language level derived from the active platform **target** — the same source
  `LuaRocksLibraryProvider` uses.
- **Actual**: `rocks/RockspecRunPathProvider.kt:19-21` hardcodes `?.so` and reads the deprecated
  `state.languageLevel`, so Windows native requires fail and the two providers can disagree on
  the Lua version.

## 3. Notes

Fix per the review: extension per `SystemInfo`; target-derived level in both providers. The
`win11` KVM VM is available for live Windows verification.
