---
id: "MAINT-52"
title: "52: One Unknown Rule"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-52: One Unknown Rule

## The gap

"The engine cannot say" has several spellings. `LuaTypeAlgebra.simplify` drops `Undefined` from a union
and keeps `Any`, so callers convert one to the other: [[BUG-441]] in `LuaTypesVisitor`, [[BUG-16]] in
`LuaGraphType.fromLuaType`, and `declaredParamNodeFor` for [[BUG-11]].

## Scope

- One rule for how an unknown behaves in a union and in a demand, in one place.
- Remove the conversions at the call sites.

## Not established

- Whether one spelling can serve both, given that [[BUG-441]] showed the union algebra alone is not
  enough: unknown-ness is also a property of a variable's reaching definitions.
