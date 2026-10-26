---
id: "BUG-532"
title: "LuaRocks new-project page: typing a project name does not clear \"Project name must not be empty\""
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-532: the LuaRocks wizard's name field never re-validates

Seen during BUG-530's live check, on GoLand 2026.1.3 and 2026.2.3.

## 1. Reproduction
1. Open File → New → Project… → LuaRocks.
2. Type a project name.

## 2. Expected vs Actual Behavior
- **Expected**: the "Project name must not be empty" error clears as soon as a name is typed, and
  Create becomes enabled.
- **Actual**: the error stays, and Create stays disabled, until another field the platform watches
  changes, such as Location. The name field is also very narrow.

## 3. Context / Environment
- **Relevant Files**: `src/main/kotlin/net/internetisalie/lunar/rocks/init/LuaRocksGeneratorPeer.kt`.
  The peer ignores the `checkValid` callback the platform passes it, so edits to its own fields
  never trigger re-validation. This comes from reading the code, not from running a fix.
- **Screenshots**: `~/.cache/claude-scratch/lunar/bug-530/` (`v7.png`–`v9.png`).
