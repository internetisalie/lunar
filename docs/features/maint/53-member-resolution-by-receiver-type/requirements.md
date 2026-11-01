---
id: "MAINT-53"
title: "53: Member Resolution by Receiver Type"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-53: Member Resolution by Receiver Type

## The gap

`LuaNameReference` resolves a member by the receiver's text, not its type. `io.stdout:close` looks for
that text, while the method is `File:close`. This is the cause of [[BUG-508]], [[BUG-512]] and
[[BUG-516]], and possibly of [[BUG-490]] and [[BUG-511]].

## Scope

- Resolve a member through the receiver's inferred type, falling back to text only where no type exists.

This is the one structural item from the [type system survey](../../../type-system-survey.md), and it
needs planning before work starts. Its cost has not been estimated.

## Not established

- The cost of type inference during reference resolution, which runs far more often than the checker.
- Whether [[BUG-490]] and [[BUG-511]] share this cause.
