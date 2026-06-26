---
id: "BUG-357"
title: "Loose parameter name extraction from LuaCATS fun(...) signatures causes missing parameter inlay hints"
type: "bug"
parent_id: "BUG"
status: "cancelled"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

> **Resolution (2026-06-24): Cannot reproduce.** On `main` (HEAD `a75d9c7f`) parameter
> names from `fun(...)` `@type` signatures are extracted and used correctly at every layer
> (parser, graph round-trip, and call-site inlay hints) for both the plain and `| string`
> union cases — they do **not** collapse to `"p"`. The behavior was already resolved by
> `fe5b1000` (BUG-133). No code fix was applied; regression guards were added instead. See
> [risks-and-gaps.md](./risks-and-gaps.md) for the full evidence and disposition.

# BUG-357: Loose parameter name extraction from LuaCATS fun(...) signatures causes missing parameter inlay hints

## 1. Reproduction

Open a Lua file in the plugin environment and paste the following snippet:

```lua
---@type fun(paramA: number, paramB: string) | string
local union_var
union_var(123, "hello")

---@type fun(myParam1: number, myParam2: string)
local plain_func
plain_func(456, "world")
```

1. Observe the inlay hints on the variables `union_var` and `plain_func`.
2. Notice they display as type `: fun(p, p)` instead of using the declared parameter names.
3. Observe the calls `union_var(123, "hello")` and `plain_func(456, "world")`.
4. Notice that no parameter inlay hints (e.g., `paramA:`, `myParam1:`) are rendered.

## 2. Expected vs Actual Behavior

- **Expected**:
  - The parameter names (`paramA`, `paramB`, `myParam1`, `myParam2`) are extracted correctly from the `fun(...)` signature.
  - The inlay hints show the correct function type (e.g., `: fun(paramA: number, paramB: string)`).
  - The call sites render the parameter name inlay hints (since parameter names are longer than 1 character and do not equal `"p"`).
- **Actual**:
  - The parameter names resolve as `"p"`.
  - The inlay hints display the type as `: fun(p, p)`.
  - No parameter inlay hints are rendered at the call site due to the single-character name suppression rule (`paramName.length <= 1 || paramName == "p"`).

## 3. Context / Environment

- **Relevant Files**:
  - `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/TypeParser.kt` (extracts parameter names from `LuaCatsFunctionSignatureArgument` AST elements).
  - `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt` (`graphTypeToLuaType` parameter mapping and name resolution fallback logic).
  - `src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintProvider.kt` (suppression checks in `shouldShowHint`).

- **Other Notes**:
  - Standard AST-declared functions (e.g., `local function myFunc(alpha, beta)`) continue to show parameter inlay hints correctly because their parameter names are correctly fetched from the AST `LuaParList`.
