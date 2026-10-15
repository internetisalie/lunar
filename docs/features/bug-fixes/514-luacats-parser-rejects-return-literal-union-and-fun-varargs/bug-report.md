---
id: "BUG-514"
title: "Lunar's LuaCATS parser rejects a string-literal union in @return and a vararg parameter inside a fun() type"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-514: two valid LuaCATS forms produce parse errors

Found while planning [[TARGET-12]] and [[BUG-513]]. The `lua-5.1` stubs use both forms.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`). Each snippet is opened with `myFixture.configureByText`,
and then `PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` is collected. Executed
2026-10-15 on `c24bbbff` (source identical to `84fa67b0`), as TARGET-12 run `r19`:

```lua
---@return "nil"|"boolean"|"number"
function f1(v) end
```
→ `errors=[<type> expected, got '"']`

```lua
---@overload fun(repl: fun(...: any): string): string
function f4(r) end
```
→ `errors=[')' or NAME expected, got '.']`

Controls from the same run parse with no errors:
- a string literal as an `@overload` parameter type,
  `---@overload fun(opt: "stop"): nil`;
- `---@return any ...`;
- `---@type function[]`.

## 2. Expected vs Actual
- **Expected**: both snippets parse with no `PsiErrorElement`. A string-literal union is a valid
  LuaCATS return type, and `...: T` is a valid vararg parameter in a `fun()` type. LuaLS accepts
  both.
- **Actual**: each produces one `PsiErrorElement` inside the LuaCATS comment.

## 3. Context
- **Where it shows up**: `runtime/standard/lua-5.1/builtin.lua:92`, `coroutine.lua:48` and `io.lua:144`
  (string-literal unions in `@return`), and `string.lua:66` (vararg in `fun()`). TARGET-12's LuaJIT
  copies of those files inherit the errors.
- **Impact measured so far**: none of these errors stops a declaration from resolving
  (`TARGET-12-00-DR-14`). What else the parse failure costs, such as the return type of `type()` or
  `gsub`'s callback typing, is not established.
- **Not the same as**: TYPE-14's "Q4 vararg generics" (`docs/features/type/14-generics-baseline/risks-and-gaps.md`),
  which is about typing `...` positions, not this parse error.
