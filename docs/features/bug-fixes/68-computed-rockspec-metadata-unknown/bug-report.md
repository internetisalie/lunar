---
id: "BUG-68"
title: "A rock whose rockspec computes its fields shows no metadata"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-68: A rock whose rockspec computes its fields shows no metadata

Found in the ROCKS-16 live verification run (2026-11-09).

## 1. Reproduction
In the Marketplace, select `luacheck` (not installed).

## 2. Expected vs Actual Behavior
- **Expected**: Its license, homepage, description and dependencies.
- **Actual**: *License: (unknown)*, *Homepage: (unknown)*, no description or dependencies.

## 3. Root Cause
BUG-58's `RockspecMetadataReader` reads literal assignments only. `luacheck`'s rockspec builds `package` and `version` from locals with `..` and an `if`, as many rockspecs do.

## 4. Fix
`LuaRocksMetadataService.fromServer` falls back to evaluating the downloaded rockspec with `RockspecBridge.evaluate`. That runs in the bridge's empty environment (BUG-50) and is read by `RockspecJsonMetadata`. `RockspecBridge.read` keeps the project-trust check for project rockspecs.

## 5. Test
`RockspecJsonMetadataTest`: a computed rockspec the literal reader cannot follow is described through the bridge. Seen live: `luacheck` shows MIT, its homepage and three dependencies.
