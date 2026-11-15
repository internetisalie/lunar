---
id: "TYPE-17"
title: "17: Inline Generic Function Types (fun<T>) in @field and @type"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-17: Inline Generic Function Types (fun<T>) in @field and @type

## Overview
A field or variable whose value is a generic function can be annotated in place, with the function's own
type parameters: `fun<T>(arg: T): T`. Calls through it infer `T` per call, and the annotation is not
a syntax error. Today the only way to describe a generic function is a `@generic` tag on its
declaration, which a field or a callback type cannot carry. Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@class Foo
---@field bar fun<T>(arg: T): T
local Foo = {}

---@type fun<T>(arg: T): T
local ident

local a = Foo.bar(1)     -- number
local b = ident("x")     -- string
```
LuaLS 3.17.0 added `fun<T>` in `@field` and `@type` ([#1170](https://github.com/LuaLS/lua-language-server/issues/1170));
before it the same line reported "`(` expected". The survey's Q3 asks for the same thing as a
method-level type parameter independent of the class's.

## Current behaviour in Lunar
- The grammar has no type-parameter list on a function type: `functionSignatureType ::= 'async'? 'fun'
  '(' ...` (`luacats.bnf:198`). `fun<T>(...)` does not match it.
- What the editor then shows (syntax error, unresolved type, or silence) is Not yet probed.
  Probe first: put the example in a `.lua` file in a light fixture, run highlighting, and record the
  diagnostics on the `@field` and `@type` lines.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-17-01 | **`fun<T>(...)` parses** | M | Not Implemented | `functionSignatureType` accepts `fun` `<` names `>` `(`. The example's two annotations produce no syntax error, and the PSI exposes the declared names. |
| TYPE-17-02 | **The names are in scope in the signature** | M | Not Implemented | `T` in `(arg: T): T` resolves to the inline declaration: no unresolved-type diagnostic on it, and it does not resolve to a class named `T`, even when one exists. |
| TYPE-17-03 | **A `@type` function instantiates per call** | M | Not Implemented | `ident("x")` is `string` and `ident(1)` is `number`, in the same file. |
| TYPE-17-04 | **A `@field` function instantiates per call** | M | Not Implemented | `Foo.bar(1)` is `number`; `foo:bar("x")` on a `Foo` instance, with the field declared `fun<T>(self: Foo, arg: T): T`, is `string`. |
| TYPE-17-05 | **Several parameters** | S | Not Implemented | `fun<K, V>(k: K, v: V): V` called `f("a", 1)` is `number`. |
| TYPE-17-06 | **The field is usable from another file** | S | Not Implemented | The same `Foo.bar(1)` from a file that requires the module is `number`. |

## Depends on / Related
- Depends on: [[TYPE-14]] (done; per-call instantiation of generic callees).
- Related: survey Q3 and issue #1170 in `docs/features/type/05-generics-support/design-luals-generics-super-issue.md`;
  [[BUG-31]] (a `.` call on a class is not instantiated, the same call path).

## Out of scope
- `fun<T>` inside `@overload`: [[TYPE-18]] covers generic names there, via `@generic`.
- Bounds on the inline names (`fun<T: string>`); upstream behaviour is not recorded.
- Renaming or find-usages on an inline type parameter.
