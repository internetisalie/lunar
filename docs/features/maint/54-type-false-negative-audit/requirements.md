---
id: "MAINT-54"
title: "54: Type Checking False-Negative Audit"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-54: Type Checking False-Negative Audit

## The gap

Bug reports record wrong diagnostics, not missing ones, so nothing measures what the checker silently
skips. `detachedDemand` still skips table-typed parameters, and extra arguments to a declared global
are unreported ([[BUG-19]] §5).

## Scope

- Inject known type errors into copies of corpus call sites and count how many are reported.
- List each unchecked shape with its count.
- Report whether the result reopens the redesign question in the
  [type system survey](../../../type-system-survey.md).
