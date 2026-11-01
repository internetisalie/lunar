---
id: "MAINT-51"
title: "51: One Declared Signature"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-51: One Declared Signature

## The gap

A declared parameter is checked by two paths, `LuaTypesVisitor.demandDeclaredParams` and
`LuaGraphType.declaredParamNodeFor`, each with its own copy of the optional rule. `isCompatible` also
lacks the `nil` forgiveness that `checkCompatibility` has. These disagreements caused [[BUG-11]],
[[BUG-19]], [[BUG-425]] and [[BUG-566]].

## Scope

- One function yields a declared signature's demand, used by both paths.
- One optional and `nil` rule, used by `checkCompatibility` and `isCompatible` alike.
- [[BUG-19]]'s `detachedDemand` and [[TYPE-14]]'s per-call copy (design §5.8) become one mechanism.

Do this before [[TYPE-14]] starts, which relies on the same path.

## Not established

- Whether table demands can be copied as cheaply as function demands.
