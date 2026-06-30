---
id: "BUG-356"
title: "Concatenation of a non-concatenable value (boolean) is not flagged"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-356: Concatenation of a non-concatenable value (boolean) is not flagged

## 1. Reproduction

```lua
local x = true
local y = x .. "a"
```

Observed live (containerized GoLand, Lunar loaded): inlay hints show `x : boolean` and `y : string`, and
the editor shows **no diagnostic** (green inspection widget, no Problems entry).

## 2. Expected vs Actual Behavior

- **Expected**: `boolean .. string` is a runtime error in Lua (`attempt to concatenate a boolean value`).
  Either the type-assignability check (`boolean is not assignable to string` on the `..` operand) or the
  existing "Suspicious concatenation" inspection should flag it. (That inspection *does* fire elsewhere —
  it produced `Suspicious concatenation: operand of type 'boolean' cannot be concatenated` in the `rocks`
  sweep.)
- **Actual**: No diagnostic. The boolean operand of `..` is silently accepted.

## 3. Context / Environment

- **Confidence**: medium — observed directly but not root-caused. The concat handler adds a
  `use(operand, String)` edge (`LuaTypesVisitor` `".."` branch), so `boolean` flowing into a `String` use
  would be expected to fail assignability; why it doesn't here (vs. the rocks case that did flag) is unclear.
- **Relevant files**:
  - `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt` (`..` operand typing).
  - `src/main/kotlin/.../insight/LuaSuspiciousConcatenationInspection.kt`.

## 4. Other Notes
- Surfaced during VNC verification while constructing a forced type error (`verify4.lua`). May overlap with
  how the engine narrows/accepts operand types; needs a planning pass (`plan-bug`) to confirm scope.
