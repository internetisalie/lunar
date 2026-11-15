---
id: "TYPE-16"
title: "16: Generics Shipped by LuaLS Since TYPE-14"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|type]]"
---

# TYPE-16: Generics Shipped by LuaLS Since TYPE-14

## Overview
LuaLS has shipped generics behaviour that Lunar does not have. The
[LuaLS generics survey](../05-generics-support/design-luals-generics-super-issue.md) marks each
item; this feature covers the ones upstream implements:

- Inline `fun<T>(...)` function types in `@field` and `@type` (LuaLS #1170, survey Q3; LuaLS 3.17.0).
- `@generic` names inside `@overload` signatures (LuaLS #723; 3.17.0). Lunar's type engine does not
  model `@overload` at all yet, so this depends on that work.
- `---@param self T` returning the receiver's type (LuaLS #1000; 3.17.0). [[BUG-94]] covers the
  in-body half.
- A method typed `---@param self Lane<T>` called on an instance (LuaLS #2196).
- Bounded hover for a self-referential generic class (LuaLS #1853; 3.17.0) — probe first.
- Prefixed and suffixed captures (``Object.`T` ``, `` `T`.Cat? ``; LuaLS PR #3149, survey Q5).

Related open bugs: [[BUG-31]], [[BUG-32]], [[BUG-37]], [[BUG-33]], [[BUG-88]].

## Requirements
To be written with the `plan-feature` skill; split into separate features where the parts ship
independently.
