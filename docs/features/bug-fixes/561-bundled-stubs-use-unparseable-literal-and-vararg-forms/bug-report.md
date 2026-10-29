---
id: "BUG-561"
title: "The bundled Lua 5.1 stubs spell literals `'\"nil\"'` and varargs `fun(...any)`, which no LuaCATS lexer state reads as intended"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-561: The bundled Lua 5.1 stubs spell literals `'"nil"'` and varargs `fun(...any)`, which no lexer state reads as intended

A deferred finding of the LuaCATS grammar batch ([[BUG-542-BATCH]] §8). A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BaseDocumentTest`), `configureByText`, then the comment's PSI read. Executed 2026-10-28 on `20e318c4` with a throwaway probe test (not committed).

```lua
---@return '"nil"'|'"boolean"'
function f(v) end

---@param f fun(...any)
function g(f) end
```

## 2. Expected vs Actual
- **Expected**: the stubs' types are read as written: a union of the string literals, and a vararg `fun`.
- **Actual**: the `@return` is a parse error (`<type> expected, got '''`); the `@param` parses with no error but types `f` as a class named `fun`, with `(...any)` as prose.

## 3. Context
- **Stub sites** ([[BUG-514]] §4): `runtime/standard/lua-5.1/builtin.lua:92`, `coroutine.lua:48`, `io.lua:141` (`'"…"'`); `string.lua:73`, `debug.lua:54` (`fun(...any)`). TARGET-12's LuaJIT copies inherit them.
- Either the stubs or the lexer's `STRINGS`/`ELLIPSIS` handling would have to change; which is not decided here.
