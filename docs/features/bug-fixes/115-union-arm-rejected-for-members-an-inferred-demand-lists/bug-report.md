---
id: "BUG-115"
title: "A union value's table arm is rejected for members an inferred demand lists, where a lone table is not"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-115: `nil | { ... } is not assignable to { ... }` is the table arm failing a member rule that only the union path applies

## Reproduction
Light fixture (`BasePlatformTestCase`, `LuaTypesSnapshot.forFile(file).getErrors()`), reduced from luals
`script/parser/compile.lua` 2787-2794 (`local name = parseName()` then `parseSimple(name, true)`):

```lua
local function fin(n) return n.finish + 1 end
local function parseSimple(node)
    if node.type == 'x' then return fin(node.node) end
    return fin(node.op)
end
local function peekWord() return nil end
local function parseName()
    local word = peekWord()
    if not word then return nil end
    return { type = 'name', start = 1, finish = 2 }
end
local function f()
    local name = parseName()
    if name then parseSimple(name) end   -- the same rows with the `if` removed, and with `if name ~= nil`
end
```

Rows on main (`269b57cb`) and on `bug-114-fix` (`883a3066`), identical: five HYPOTHESIS rows
`nil | { ... } is not assignable to { ... }` (at `name` twice, `node`, `.node`, `.op`).
Controls (run with a `---@param node Unresolved.Object` line above `parseSimple`, which changes nothing): replace the call with `parseSimple({ type = 'name', start = 1, finish = 2 })` and
there are **0 rows**; `if name ~= nil` (the one nil guard the engine narrows) gives 0 rows.

With one more function that declares a contract on the same parameter (`---@class Plain`,
`---@param obj Plain`, `local function createLocal(obj) end`, called as `createLocal(node)` inside
`parseSimple`) the rows become `ERROR nil | { ... } is not assignable to Plain` on main as well. That promotion
is BUG-116.

## Expected vs Actual
- **Expected:** a call argument `{ type, start, finish }` or `nil` is accepted or rejected by one rule, whether or
  not the nil arm is present, and an absent field of a demand the engine synthesized from usage is not an error
  (a missing field reads as `nil`).
- **Actual:** the lone table is accepted; `nil | table` is rejected, and the message names `nil` although `nil`
  is forgiven (BUG-416) and is not the arm that fails.

## Root Cause
Traced by a temporary print in the union branch of `checkCompatibility`, listing the informative arms that fail
`isCompatible` (not committed). On `compile.lua` every one of the 36 anchors below has a failing arm that is a
`Table`; none has a failing nil arm (0 anchors without a recorded arm).

1. `LuaTypeGraph.checkCompatibility`, union branch (main `LuaTypeGraph.kt:735-739`): the value union is rejected
   when `!informative.all { isCompatible(arm, use) }`. `nil` is already removed by `informativeArms`.
2. `isCompatible` → `isStructurallyCompatible` (main `LuaTypeGraph.kt:1029-1044`): a table arm fails when the use
   has a member the arm lacks and `!LuaTypeAlgebra.isOptional(useNode.read)`. A demand member read from usage is a
   one-member `Table`, never optional, so `node`/`op` (read through `fin(node.node)`) are required.
3. The path for a lone table, `checkTableCompatibility` (main `LuaTypeGraph.kt:1194-1222`), reports a missing member
   only when `use.isExact && !isMethodOnClass`. A use built from usage is not exact, so it tolerates the same
   absent members ("they might be assigned later"). The two paths disagree on one fact. Nil narrowing
   (`LuaTypesVisitor.visitIfStatement`, main `:492`, parses only `type(v) == "t"` and `v == nil`/`v ~= nil` guards,
   not truthiness or early return) is therefore beside the point: `if name then` and `if not name then return nil end`
   do not change the rows.

Experiment (temporary, reverted): making `isStructurallyCompatible` ignore a missing member when `!use.isExact`
removes all 36 anchors, and the fixture rows go to 0. The same edit removes 992 of the 1 150 rows of `compile.lua`
under the fix (HYPOTHESIS 947 → 148, ERROR 196 → 3), so it is evidence of the share, not a proposed fix.

### Anchors explained (`compile.lua`, 36 ERROR anchors that are new or promoted under BUG-114's fix)
All 36. Arm detail: 18 anchors fail on members the arm lacks (`node`, `op`, `attrs`, `extParent`); 18 also fail
on a present member `node` whose value is a union of tables that in turn lack `node`/`op` (the same rule, applied
recursively). Lines: 2156 (×2, `parseSimple` and its `node`), 2564 (×2, `resolveName`), 2787, 2789 (×2), 2792,
2794, 2805, 2813, 3133 (×2), 3139 (×3), 3165, 3169, 3182 (×2), 3191 (×2), 3196, 3200 (×3), 3204, 3232, 3239 (×3),
3241 (×2), 3356, 3357, 3385, 5008. Main shows the 17 that BUG-114 reports as promoted as HYPOTHESIS, and has no
row at all at the other 19 (not explained here: whether those pairs are checked on main is BUG-114's schedule).

## Fix Strategy
Make the union path apply the table path's rule for a missing member: tolerate it when the use is not exact, and
for a method on a class. One predicate, shared by `isStructurallyCompatible` and `checkTableCompatibility`.
Do not weaken it for an exact (declared) use. Measure against the corpus first: the experiment above removes
most of the `compile.lua` family, so the golden and baseline diffs need review row by row, not a bulk re-record.

## Test Strategy
`LuaUnionMemberRuleTest` (light fixture), the fixture above:
- `nil | table` and a lone table against the same usage-built demand give the same rows (both none);
- a union against an **exact** declared class that the arm lacks a required field of still reports;
- `if name then` and `if name ~= nil` give the same rows.
Corpus: `-PwithCorpus`, `compile.lua` row count before and after.
