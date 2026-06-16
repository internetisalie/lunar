---
id: "INSP-07"
title: "Suspicious Concatenation Requirements"
type: "feature"
status: "done"
parent_id: "INSP"
folders:
  - "[[features/inspections/07-suspicious-concatenation/design|design]]"
  - "[[features/inspections/07-suspicious-concatenation/implementation-plan|implementation-plan]]"
---

# Suspicious Concatenation Requirements

## Overview

A `<localInspection>` that flags operands of the Lua concatenation operator `..` whose
inferred type cannot be concatenated. Lua coerces only `string` and `number` operands; a
`table`, `boolean`, `nil`, or `function` operand to `..` is a runtime error. The inspection
reads the per-file type-inference snapshot (`LuaTypes`, the same engine consumed by INSP-03
Type Mismatch) and warns on each non-concatenable operand. Depends on TYPE-02 (type engine,
done).

## In Scope
- The binary `..` operator (`LuaBinOpExpr` whose `binOp` text is `..`), including chained
  concatenations (`a .. b .. c`, which the parser nests as right-associative `LuaBinOpExpr`s).
- Operands whose inferred graph type is a concrete non-concatenable kind: `Nil`, `Boolean`,
  `Table`, `Function`, `Array`.
- Union operands where **every** member is non-concatenable.

## Out of Scope
- Quick fixes (e.g. wrapping the operand in `tostring(...)`) — future work.
- Metatable `__concat` metamethods: the engine does not model `__concat`, so a `table`
  operand is reported even though a `__concat` metamethod could make it legal at runtime
  (documented limitation; conservative-by-kind keeps false positives off for `unknown`/`any`).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-07-01 | Non-concatenable operand detection | Must | Not Implemented | Warn on a `..` operand whose inferred type is a concrete non-concatenable kind (`table`, `boolean`, `nil`, `function`, array). |
| INSP-07-02 | Union handling | Must | Not Implemented | Warn on a union-typed operand only when **every** member of the union is non-concatenable; do not warn if any member is `string`/`number`/`any`/`unknown`. |
| INSP-07-03 | No false positives on un-inferable operands | Must | Not Implemented | Never warn when an operand's inferred type is `any`, `unknown`/`undefined`, a generic type parameter, `string`, or `number`. |
| INSP-07-04 | Highlight the offending operand | Should | Not Implemented | The warning range is the offending operand expression (`LuaExpr`), not the whole `..` expression. |

## Test Cases

Each case uses the real inspection flow: `myFixture.enableInspections(LuaSuspiciousConcatenationInspection())`,
`myFixture.configureByText("test.lua", <input>)`, then assert over `myFixture.doHighlighting()`
filtered by `description?.startsWith("Suspicious concatenation")`. Engine-snapshot-only tests
do **not** satisfy the Wave-4 DoD gate.

### Test Case 1 — table operand (INSP-07-01, INSP-07-04)
**Input:**
```lua
local t = {}
local s = "hello " .. t
```
**Action:** Run inspection.
**Expected Output:** Exactly one warning, on the right operand `t`, message
`Suspicious concatenation: operand of type '{ ... }' cannot be concatenated`.
(`local t = {}` infers `LuaGraphType.Table(className=null)`, whose `displayName()` is the
anonymous-table label `{ ... }` — `LuaGraphType.kt:70` — **not** the word `table`. The
message is built from `graphType.displayName()`, so the assertion must use `{ ... }`.)

### Test Case 2 — boolean operand on the left (INSP-07-01, INSP-07-04)
**Input:**
```lua
local b = true
local s = b .. " yes"
```
**Action:** Run inspection.
**Expected Output:** Exactly one warning, on the left operand `b`, message
`Suspicious concatenation: operand of type 'boolean' cannot be concatenated`.

### Test Case 3 — string and number operands (INSP-07-03)
**Input:**
```lua
local n = 1
local s = "x" .. n .. "y"
```
**Action:** Run inspection.
**Expected Output:** No warnings (both `string` and `number` are concatenable).

### Test Case 4 — un-inferable operand (INSP-07-03)
**Input:**
```lua
local s = "x" .. unknownGlobal
```
**Action:** Run inspection.
**Expected Output:** No warnings (`unknownGlobal` infers as `any`/`undefined`, so the
conservative predicate does not flag it).

### Test Case 5 — union with a concatenable member (INSP-07-02)
**Input:**
```lua
---@type string|nil
local maybe = nil
local s = "x" .. maybe
```
**Action:** Run inspection.
**Expected Output:** No warnings (the union `string | nil` has the concatenable member
`string`, so the all-members-non-concatenable rule does not fire).

### Test Case 6 — union with all members non-concatenable (INSP-07-02, INSP-07-01)
**Input:**
```lua
---@type boolean|nil
local flag = nil
local s = "x" .. flag
```
**Action:** Run inspection.
**Expected Output:** Exactly one warning on the operand `flag`, message
`Suspicious concatenation: operand of type 'boolean | nil' cannot be concatenated`.
