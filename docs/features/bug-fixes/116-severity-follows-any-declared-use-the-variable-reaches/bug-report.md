---
id: "BUG-116"
title: "A mismatch is an ERROR because its variable reaches some declared use, not because the failing demand is declared"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-116: a usage-derived failure is promoted to ERROR by an unrelated `@param` elsewhere in the variable's down set

## Reproduction
Light fixture; the BUG-115 fixture plus one function that declares a contract on the parameter's value:

```lua
---@class Plain
---@param obj Plain
local function createLocal(obj) return obj end

local function fin(n) return n.finish + 1 end
local function parseSimple(node)
    createLocal(node)
    if node.type == 'x' then return fin(node.node) end
    return fin(node.op)
end
-- parseName and f exactly as in BUG-115
```

Rows, identical on main (`269b57cb`) and on `bug-114-fix` (`883a3066`): `ERROR nil | { ... } is not assignable to Plain`
at `name` (×2) and `node`, plus HYPOTHESIS at `.node` and `.op`. Without `createLocal(node)` the same pairs are
HYPOTHESIS only (BUG-115 fixture). The argument `{ type, start, finish }` satisfies `Plain` (a class with no
fields); the arm fails only on `node` and `op`, members the code read.

## Expected vs Actual
- **Expected (BUG-419):** a conflict with a demand the engine synthesized from usage is a HYPOTHESIS; only a
  conflict with a declared demand is a diagnostic.
- **Actual:** the severity is a property of the variable's whole down set, so one declared use anywhere downstream
  turns every failure of that variable into an ERROR, including failures of members no annotation mentions.

## Root Cause
- `VariableElement.declaredDemand` (main `LuaTypeNodes.kt:236`, `reachesDeclaredDemand` `:274-279`) is true when
  any non-variable use in `downSet` is declared. `LuaTypeGraph` passes it as the pair's `declaredDemand`
  (main `LuaTypeGraph.kt:655`), and `reportIncompatible` takes `tier = if (declaredDemand) ERROR else HYPOTHESIS`
  (main `:407`). The flag is never asked about the demand that failed.
- On `compile.lua` the declared uses reached from `parseSimple`'s `node` are the `@param obj parser.object` of
  `createLocal` (offset 20224) and of the function at 19533 (read from a temporary print of the variable's declared
  down-set uses). The failing members (`node`, `op`, `attrs`, `extParent`) come from reads in other functions.
- Why the fix promotes and main hides: main checks the pair before the declared use is in the down set (BUG-114 §6,
  `declaredDemand = false` at check time); the fix judges against the final graph. So the fix did not add a wrong
  rule; it removed the accident that was hiding this one.

### Anchors explained
The ERROR (not HYPOTHESIS) status of all 36 anchors listed in BUG-115. Which pairs are false is BUG-115's cause;
this report is why they are ERROR.

## Fix Strategy
Carry provenance per member demand, not per variable: a pair is declared only when the arm fails a member whose
demand is declared (or the demand as a whole is). `memberDemandHubs` already splits demands by member
(BUG-110); the declared flag needs the same granularity. Until then, BUG-114's fix must not ship while BUG-115
leaves these pairs failing.

## Test Strategy
`LuaDeclaredProvenanceTest`: the fixture above gives HYPOTHESIS, not ERROR, at `name`/`node`; a fixture whose arm
fails a **declared** field (`---@class Strict` with `---@field id integer`, argument `{}`) still gives ERROR.
Assert on main and on the BUG-114 branch alike (the rows are schedule independent here).
