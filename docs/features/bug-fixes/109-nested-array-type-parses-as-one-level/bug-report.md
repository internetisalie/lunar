---
id: "BUG-109"
title: "A nested array type `T[][]` parses as `T[]`, so a nested table constructor is reported as not assignable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-109: A nested array type `T[][]` parses as `T[]`

Found by the MAINT-55 conformance suite: case `assign-type-mismatch-046` (rule `assign.local`,
observed `false-report`). It was linked to BUG-481, whose fix covers one level of array and leaves
this case reporting.

## 1. Reproduction
```lua
---@type string|string[]|string[][]
local t = {{'a'}}
```

## 2. Expected vs Actual
- **Expected**: no report; `{{'a'}}` is a `string[][]`, an arm of the declared union (LuaLS agrees).
- **Actual**: `{ ... } is not assignable to union string | string[]`. The `string[][]` arm is missing
  from the union.

## 3. Root Cause
`luacats.bnf:179` is `arrayType ::= distinctType '[]'?`: at most one `[]` suffix. `string[][]` parses
as `string[]` and the second `[]` is not part of the type, so the declared union has no
two-level array arm.

## 4. Fix Strategy
Allow repeated suffixes (`distinctType '[]'*`) and build one `Array` level per suffix in the type
parser. A grammar change: regenerate with `generate-parser`, and check every reader of
`LuaCatsArrayType` for the single-suffix assumption.

## 5. Test Strategy
A light fixture asserting `{{'a'}}` is accepted for `string[][]` and `{{1}}` is still reported; the
conformance case moves to `agree`.
