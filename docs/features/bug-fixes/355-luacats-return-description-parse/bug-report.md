---
id: "BUG-355"
title: "EmmyLua @-description after @return/@param type is a hard parse error"
type: "bug"
status: "done"
priority: "high"
folders:
  - "[[features]]"
---

# BUG-355: EmmyLua `@`-description after `@return`/`@param` type is a hard parse error

## 1. Reproduction

Open a Lua file containing an EmmyLua-style `@return` whose description is introduced with `@`:

```lua
--- Retrieve the earliest expiry time of the timers.
---@return number|nil @The earliest expiry time.
function getNextTimerExpires() end
```

Observed live (containerized GoLand, Lunar loaded) on `rocks/ssdpd/lua/ssdpd/Cache.lua` lines 18, 25, 52 —
all `@return <type> @<description>`.

## 2. Expected vs Actual Behavior

- **Expected**: The trailing `@<text>` after the return type is a description and should be tolerated
  (rendered as the return's doc, or at worst ignored) — not a syntax error. A *space*-separated
  description on the same construct parses fine (e.g. `---@param udn string The UDN of the device.`
  on Cache.lua:51 is accepted).
- **Actual**: A hard **ERROR**-level parser diagnostic is raised on the `@`:
  `'#', <, DASHES, NAME, '[]' or '|' expected, got '@'`. The red error appears in the editor and the
  Problems panel, a false positive on otherwise-valid annotations.

## 3. Context / Environment

- **Dialect**: `---@return <type> @<comment>` is the **EmmyLua** description convention (LuaCATS/LuaLS use a
  bare trailing comment with no `@`). Lunar accepts the bare-text form but rejects the `@`-prefixed form.
- **Likely also affected**: `@param <name> <type> @<comment>` (same grammar shape; not directly reproduced).
- **Distinct from** `BUG-134` (@return *comma*-separated multiple types), which is a different `@return`
  grammar gap.
- **Relevant files**:
  - `src/main/grammar/lua.bnf` / `lua.flex` — LuaCATS tag grammar (return/param type + description).
  - Fix requires regenerating the parser/lexer (manual IDE handoff per `AGENTS.md`), not a Kotlin-only change.

## 4. Other Notes
- Surfaced during VNC verification of the `rocks` project; widespread across annotated rocks files using
  the EmmyLua description style.
