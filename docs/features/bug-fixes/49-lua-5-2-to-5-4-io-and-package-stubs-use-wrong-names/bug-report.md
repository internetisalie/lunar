---
id: "BUG-49"
title: "The Lua 5.2–5.4 io and package stubs declare names the manuals do not have, and the 5.4 collectgarbage stub does not match its manual"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-49: The Lua 5.2–5.4 io and package stubs declare names the manuals do not have

Found while implementing [[TARGET-07]], which corrected these names in the 5.5 stubs only.

## 1. Reproduction
In `src/main/resources/runtime/standard/lua-5.{2,3,4}/`:
- `io.lua:120` declares `io.setvbuf(file, mode, size)`. The manuals have only the method `file:setvbuf(mode, size)`.
- `io.lua:69` declares `File:iseof()`, which no manual defines.
- `package.lua:55` declares `package.preloaded`. The manuals call it `package.preload`.
- `package.config` is missing, although every manual from 5.2 on defines it.
- The 5.4 `builtin.lua` `collectgarbage` stub does not list the 5.4 manual's options (`"incremental"`, `"generational"`).

## 2. Expected vs Actual Behavior
- **Expected**: each level's stubs declare exactly the names in that level's reference manual, as BUG-46 and TARGET-07 checked by script.
- **Actual**: completion offers `io.setvbuf` and `File:iseof`, which fail at run time; `package.preload` and `package.config` do not resolve.

## 3. Context / Environment
- **Lua Version**: 5.2, 5.3, 5.4. The 5.1 stubs are correct here (`File:setvbuf`, `package.config`).
- **Other Notes**: the fix is the name check TARGET-07 ran for 5.5 (its handoff has the script), run against each older manual.
