---
id: "BUG-34"
title: "A generic function's own return expression adds its uninstantiated type to the call's result"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-34: A generic function's own return expression adds its uninstantiated type to the call's result

Found running [[TYPE-14]]'s human checklist, scenario 3.4 (GoLand 2026.2.3, libvirt builder).

## 1. Reproduction
```lua
---@class Box<T>
---@field value T
local Box = {}
---@generic T
---@param v T
---@return Box<T>
local function make(v) return { value = v } end
local made = make(1)
local mv = made.value
```

## 2. Expected vs Actual Behavior
- **Expected**: `mv` is `number`, and no arm of `made` holds `T`. `made` is
  `Box<number> | { ... }`, as a non-generic function's call is typed (below).
- **Actual**: the inlay hints read `made: Box<number> | { ... }` and `mv: number | T`. With the
  body changed to `local function make(v) end`, they read `Box<number>` and `number`, so the extra
  arms come from the function's `return { value = v }`. In that return, `v` is the template's `T`.

A non-generic function's call takes both its declared `@return` and its body's return. Measured on
`d121c7bb`:

| Callee | Call result | `.value` |
| :-- | :-- | :-- |
| `---@return Plain` (field `value: number`), body `return {}` | `Plain \| { ... }` | `number` |
| `---@param v number` `---@return Plain`, body `return { value = v }` | `Plain \| { ... }` | `number` |
| `---@return Box<number>`, body `return { value = 1 }` | `Box<number> \| { ... }` | `number` |
| `---@return number`, body `return "x"` | `number \| string` | — |
| generic `---@return Box<T>`, body `return { value = v }` | `Box<number> \| { ... }` | `number \| T` |
| generic `---@return Box<T>`, empty body | `Box<number>` | `number` |
| generic `---@return T[]`, body `return { v }` | `number[] \| { ... }` | — |

So the body's `{ ... }` arm matches what a non-generic call gives, and this fix keeps it. The defect
is the `T` inside that arm. Removing the body's arm from every call is a separate change, and it
would change non-generic calls too.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaGenericInstantiation.kt` (`Instantiator.instantiateReturn`,
  `BindingReader.substitute` / `prepareFunctions`).
- **Other Notes**: requirements case 22 and `LuaTypeInlayHintsTest` case 23 use an empty body, so
  they do not cover this. TYPE-14-15 says a substituted member never shows `T` where a binding exists.

## 4. Root Cause
A function's return node takes both its `---@return` type and its body's `return` expressions
(`LuaTypeGraphBridge.injectReturnAnnotations` and `LuaTypesVisitor.visitFinalStatement`). When
`make(1)` is visited, `Instantiator.instantiateReturn` (`LuaGenericInstantiation.kt`) reads the
template return node's write: `Box<T> | { value: T }`. It finds a generic in it, so the call gets a
lazy substitution of that union.

`BindingReader.substitute` handles a `Table` only through `substituteName`, which returns a table
with no `className` unchanged. That follows design §5.6: "`Table` without such a name … returned as
is". So `Box<T>` becomes `Box<number>`, but `{ value: T }` keeps the template's own `value` node,
whose write is `T`. `made.value` reads both arms and gets `number | T`.

`PathCollector.collectTable` already enters anonymous tables, so the instantiation knows `T`
occurs there. Only the substitution leaves it out.

## 5. Fix Strategy
Copy an anonymous table, as a generic function in a return is already copied. In `BindingReader`:
- `prepareFunctions` becomes `prepare`. It also copies every anonymous table (`className == null`)
  whose members mention a binding, when the call is instantiated. Each member gets a fresh node fed
  by a lazy substitution of the original member's write, through the existing `substitutedNode`.
- `substitute` returns the prepared copy for an anonymous table. A named table still goes through
  `substituteName`.

When the type is read: the table's member set is taken when the call is visited, which is when the
function copies are made. Member types are read lazily, when the result is read. Nothing is minted
on a read, so the BUG-473 meter does not move. A table's `superTypes` are kept as they are.

Non-generic calls do not go through this code, so they are unchanged. A generic call whose return
has no anonymous table holding a generic gets the same nodes as before.

## 6. Test Strategy
- `Type14GenericClassTest`: the reproduction with the body. `mv` is `number`, `made` is
  `Box<number> | { ... }`, and no arm of `made` holds `T`.
- `LuaTypeInlayHintsTest`: the same source with the body. The inlays read
  `made: Box<number> | { ... }` and `mv: number`.
- Both fail on base for the reported reason (`number | T`). Reverting the fix makes them fail again.
- `Type14*`, the instantiation budget test and the BUG-473 meter tests run unmodified and pass. Run
  the full suite with the corpus lane, and compare the corpus findings with base.
