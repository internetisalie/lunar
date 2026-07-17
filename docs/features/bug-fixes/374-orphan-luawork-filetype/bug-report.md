---
id: "BUG-374"
title: "Orphaned \"Lua Workspace\" (*.luawork) file type — dead code from the removed workspace concept"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-374: Orphaned "Lua Workspace" (*.luawork) file type — dead code from the removed workspace concept

> **RESOLVED 2026-07-17 (this commit)**: Deleted `LuaWorkFileType.kt` and the `plugin.xml` `<fileType>` registration block; no references remain in production code.

This is a **chore** (dead-code removal), not a user-visible defect.

## 1. Reproduction

1. Open *Settings → Editor → File Types* and search for "Lua Workspace" — the type is still
   registered, claiming the `*.luawork` extension.
2. `git grep -n luawork src/` — the only production references are the file type itself and its
   `plugin.xml` registration; nothing consumes `.luawork` files.

## 2. Expected vs Actual Behavior

- **Expected**: no registered file type for a concept the plugin no longer has. The workspace-file
  mechanism was deleted by commit `736c87ee` ("Remove Workspace" — `LuaWorkspace.kt`,
  `LuaWorkspaceAction.kt`, `LuaWorkspaceService.kt`, `src/main/lua/luawork.lua`), and multi-rock
  project structure is now handled by ROCKS-09's automatic recursive rockspec discovery
  (`docs/features/rocks/09-workspace-discovery/`).
- **Actual**: the file type survived the removal and is still registered:
  - `src/main/resources/META-INF/plugin.xml:102-107` — `<fileType name="Lua Workspace" …
    implementationClass="net.internetisalie.lunar.lang.LuaWorkFileType" … extensions="luawork"/>`
  - `src/main/kotlin/net/internetisalie/lunar/lang/LuaWorkFileType.kt` — the orphaned class.

## 3. Context / Environment

- **Confidence**: high — root-caused; reference audit done.
- **Reference audit** (`grep -rn luawork src/`, `grep -rn LuaWorkFileType src/`): exactly three
  hits — `LuaWorkFileType.kt` itself, the `plugin.xml:104` registration, and a historical mention
  in a test comment (`src/test/kotlin/net/internetisalie/lunar/lang/types/DuplicateNilAssignabilityTest.kt:12`,
  prose only). Two stale `.luawork` mentions also linger in NAV-12 docs
  (`docs/features/navigation/12-member-field-resolution/design.md:60`,
  `implementation-plan.md:43` — verify-in-ide notes, historical).

## 4. Other Notes

- **Chore scope**: delete `LuaWorkFileType.kt` and the `plugin.xml:102-107` block; leave the test
  comment (it describes a Lua pattern, not the file type). No settings migration needed — a
  removed file type simply falls back to plain-text association; no external install base exists.
