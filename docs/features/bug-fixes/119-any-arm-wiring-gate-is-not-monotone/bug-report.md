---
id: "BUG-119"
title: "A union with an Any arm wires an arm only when it is already compatible, so the closure depends on check order"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-119: the `gradual` arm filter in `checkCompatibility` makes wiring depend on what was wired before

## Reproduction
Light fixture; the shape of luarocks `src/luarocks/tools/zip.lua` line 1,
`local string = _tl_compat and _tl_compat.string or string`, with two stdlib tables:

```lua
local compat
if os.time() > 0 then local ok, m = pcall(require, 'compat53.module'); if ok then compat = m end end
local string = compat and compat.string or string
local table = compat and compat.table or table
local t = {}
local function f(b) table.insert(t, string.char(b)) end
return f
```

Built twice on the BUG-114 branch (`883a3066`), once normally and once with `LuaTypeGraph.scheduleReversed`:
the closure has 390 edges normally and 372 reversed, and `closureDigest()` differs. With only `string`, or with
`string` and `io`, the two schedules agree; the second table is what exposes it. `zip.lua` itself is 5 649 against
5 630 (BUG-114 §5/§6), and is the same on main.

## Expected vs Actual
- **Expected:** the closure is a function of the program.
- **Actual:** it depends on the order pairs are examined.

## Root Cause
`LuaTypeGraph.checkCompatibility`, union branch (main `LuaTypeGraph.kt:730` and `:765`): for a
value union that keeps an `Any` arm (`gradual`), each table or function arm is passed on only when
`isCompatible(arm, use)` is true now (`if (gradual && !isCompatible(member, useType, CompatContext())) continue`).
Passing an arm on adds the member edges that change what the member's `read` collects, and `isCompatible` reads that
`read`. So an arm that is incompatible when the pair is checked early is skipped, and stays skipped; examined later,
after other edges exist, it is compatible and is wired. Both end states are fixed points of a rule that is not
monotone in the graph.

Confirmed by experiment (temporary, reverted): removing the `isCompatible` condition from that `continue` (wiring
every arm) makes the fixture 390 / 390, and leaves the other fixtures and `string`/`io` unchanged.

## Fix Strategy
Wire a gradual union's arms by a test that only grows: the arm's head kind matches the use's (a table arm for a table
use, a function arm for a function use), not full `isCompatible`. The reported mismatch for the arm that does not
fit stays suppressed by the `gradual` rule on the report side. Expect more edges everywhere an `Any` arm exists;
measure the closure size and the corpus diff before landing.

## Test Strategy
`LuaScheduleIndependenceTest` (BUG-114) gains the fixture above: rows and `closureDigest` equal under
`scheduleReversed`. It is red on the current code (390 against 372).
