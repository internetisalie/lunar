---
id: "BUG-485"
title: "A line breakpoint cannot be placed on the first statement of any block"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-485: A line breakpoint cannot be placed on the first statement of any block

Found 2026-10-10 by [[BUG-461]], whose build-time guard and `@Test` additions made previously
uncollected or vacuous tests actually run. Measured on `e762dc74`.

## Reproduction

`LuaLineBreakpointType.canPutAt` on line 1 of a file whose line 0 is a comment and line 1 is
`local x = 1` (followed by more statements) returns `false`. Line 2 and a later `print(x)` return `true`.

## Expected vs actual

- **Expected**: `true` — the line holds a statement.
- **Actual**: `false`. The parent walk climbs while the ancestor still starts on the queried line; the
  first statement shares its start line with its enclosing BLOCK, so the walk ends on the BLOCK, which is
  not a `LuaStatement`. Test: `TestLuaLineBreakpointType.testCanPutAtFirstStatementOfBlock`
  (`@Disabled("BUG-485 …")`). The file-start case is the one measured; whether a first statement inside a
  function body also fails is not established — the same walk suggests it does.

## Fix strategy

Stop the walk at the first `LuaStatement` ancestor instead of the last same-line ancestor. Un-ignore the test.
