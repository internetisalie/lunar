---
id: "TYPE-16"
title: "16: @overload Signatures in the Type Engine"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-16: @overload Signatures in the Type Engine

## Overview
A function documented with `---@overload` has several valid call shapes. The type engine models only the
primary signature, so a call written for an overload is checked against the wrong parameters, or is
left unchecked to avoid that. With overloads modelled, a call that fits any signature is accepted, its
result type is the matching signature's return, and a call that fits none is reported.
Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@overload fun(list: table, value: any): nil
---@param list table
---@param pos? integer
---@param value? any
function table.insert(list, pos, value) end

table.insert(stack, "block start")   -- matches the overload: (list, value)
```
LuaLS chooses among the primary signature and the overloads by the call's arguments, so this call is
valid and typed by the overload.

## Current behaviour in Lunar
- `@overload` is parsed (`luacats.bnf` `overloadTag`, line 133; `LuaCatsLazyCommentImpl.getOverloadTagList`)
  but never converted to a type: "`@overload` never reaches the type engine"
  (`LuaSignatureCopier.kt:118-119`).
- Its only effect is a veto: a function with an `@overload` does not count arguments from other files
  (`LuaTypesVisitor.countsArguments`, lines 1093-1099, BUG-38).
- Argument positions are bound only when the argument count equals the parameter count with no vararg
  (`LuaSignatureCopier.positionsAreUnambiguous`, lines 126-129). The table.insert example above is the
  measured reason: a fits-the-arity rule gave 244 false positives (`LuaSignatureCopier.kt:104-114`).
- Prior work: [[BUG-91]] (PR 256, not yet on main) widens a function body's return check to accept
  `return false` where an `@overload` returns `boolean`, by widening the first return only. It does not
  model the overload's parameters or a call's result.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-16-01 | **Overloads become function types** | M | Not Implemented | Every `@overload fun(...)` tag on a declaration yields a signature the engine can read, with parameter names, optionality and return types. Input: the `table.insert` declaration above. Expected: the engine holds two signatures, `(list, pos?, value?)` and `(list, value): nil`. |
| TYPE-16-02 | **A call matching an overload is accepted** | M | Not Implemented | A call is accepted when it fits the primary signature or any overload. `table.insert(stack, "block start")` with `stack: table` gives no argument diagnostic. |
| TYPE-16-03 | **The call's result is the matching signature's return** | M | Not Implemented | Signatures are tried in declaration order, primary first; the first whose arity and argument types fit supplies the result. `---@overload fun(x: string): string` over `---@param x number` `---@return number`: `f("a")` is `string`, `f(1)` is `number`. |
| TYPE-16-04 | **A call matching no signature is reported** | M | Not Implemented | Signatures `(x: number)` and `(x: string, y: string)`; `f(true)` and `f("a", "b", "c")` each get a diagnostic on the call. |
| TYPE-16-05 | **Cross-file calls count arguments against the set** | M | Not Implemented | `countsArguments` no longer vetoes on `@overload`. A call from another file fitting one signature is not reported as too many arguments; one fitting none is. |
| TYPE-16-06 | **Optional and vararg parameters take part** | S | Not Implemented | `table.insert(stack, 2, "x")` (3 arguments) and `table.insert(stack, "x")` (2) both resolve without binding an argument to the wrong parameter. |
| TYPE-16-07 | **Return checks use every signature's returns** | S | Not Implemented | In the body of a function with `---@overload fun(): boolean` and `---@return number`, `return false` and `return 1` both pass; `return "x"` is reported. Subsumes the [[BUG-91]] widening. |

## Depends on / Related
- Related: [[BUG-91]] (partial prior work, PR 256), [[BUG-38]] (argument counting across files),
  [[TYPE-18]] (generic names in overloads, builds on this).
- Depends on: none.

## Out of scope
- `@generic` names inside an overload signature: [[TYPE-18]].
- Inline `fun<T>` types: [[TYPE-17]].
- Overload resolution for bundled stubs and definition libraries; argument counting stays limited to
  project source as in BUG-38.
