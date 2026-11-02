---
id: "BUG-25"
title: "A local's reaching definitions show `any` beside a scalar instead of `any`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-25: A local's reaching definitions show `any` beside a scalar instead of `any`

Found while planning [[MAINT-52]] (its Gap 2.1).

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), one file, no definition of `Unres` anywhere:

```lua
---@type Unres|string
local z = 1
```

Read `LuaTypesSnapshot.forFile(file).getValueType(<nameRef z>).displayName()`.

## 2. Expected vs Actual Behavior
- **Expected**: `any`. A union built by `LuaTypeAlgebra.canonicalize` lets an `any` arm absorb scalar
  arms, so `any | number` is `any` everywhere else in the engine.
- **Actual**: `number | any`. Executed on `79380bf0` and on `7ec1adb7` (MAINT-52 risks-and-gaps,
  case c14).

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `VariableElement.resolveWrite` in `lang/psi/types/LuaTypeNodes.kt` builds the
  union of a variable's reaching definitions.
- **Other Notes**: display only, as far as measured. No assignability error is reported for the
  fixture above.
