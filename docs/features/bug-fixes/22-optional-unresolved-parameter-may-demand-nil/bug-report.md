---
id: "BUG-22"
title: "An optional parameter of an unresolved type may demand nil on the declared path"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-22: An optional parameter of an unresolved type may demand nil on the declared path

Found reading the code for the [type system survey](../../../type-system-survey.md). Not executed, so
it may not reproduce.

## 1. Reproduction

In another file from the call, on a global: `---@param x? Unresolved`, then pass a table.

## 2. Expected vs Actual
- **Expected**: no diagnostic, because the type is unknown.
- **Possible actual**: `LuaTypesVisitor.demandTypeOf` builds `Union.create(listOf(read, Nil))`, and
  `Undefined | nil` canonicalizes to `nil`. `declaredParamNodeFor` guards this case for [[BUG-11]];
  `demandTypeOf` does not.

## 3. Context
- If real, [[MAINT-51]] removes it by sharing one rule.
