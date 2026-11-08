---
id: "BUG-46"
title: "The bundled Lua 5.2–5.4 stdlib stubs for math, string, os, debug, coroutine and utf8 declare no functions"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-46: The bundled Lua 5.2–5.4 stdlib stubs for math, string, os, debug, coroutine and utf8 declare no functions

Found while fixing [[BUG-38]].

## 1. Reproduction
`src/main/resources/runtime/standard/lua-5.{2,3,4}/{math,string,os,debug,coroutine}.lua` (and `utf8.lua`
for 5.3/5.4) are 27 lines each: the licence header and an empty `---@class` table. `lua-5.1/math.lua`
is 180 lines and declares 28 functions. On language level 5.4, `math.floor()` resolves to no signature,
so its argument count and types cannot be checked. This was observed in a BUG-38 probe.

## 2. Expected vs Actual Behavior
- **Expected**: each version's stub declares that version's library functions with `---@param` /
  `---@return` (optional parameters marked `?`, varargs and `@overload`s where the manual has them).
- **Actual**: no function is declared for these modules on 5.2–5.4.

## 3. Context / Environment
- **Lua Version**: 5.2, 5.3, 5.4.
- **Other Notes**: not yet measured: what completion, hover and inlay hints show for `string.*` /
  `math.*` on these levels, and whether any fallback supplies them. The 5.5 and LuaJIT stubs were not
  inspected. Stub precision (optionals, overloads) is a prerequisite for counting arguments to stdlib
  callees (BUG-38).

## 4. Intended Approach
- Carry the 5.1 stubs (checked against the 5.1 API in BUG-513) forward to 5.2, 5.3 and 5.4, keeping their
  style, and apply each version's changes from the reference manual. Examples: `load(chunk, chunkname?,
  mode?, env?)` and `os.exit(code?, close?)` from 5.2; `math.tointeger` from 5.3; `math.random`'s 5.4
  changes.
- Use LuaLS's `meta/template` definitions (MIT) only to cross-check signatures, optionals and overloads,
  not as copied text.
- Before the stubs count toward stdlib arity (the scope deferred from [[BUG-38]]), separate calls valid
  only in some language versions from calls that run but break the declared signature
  (for example `error()` with no message).
