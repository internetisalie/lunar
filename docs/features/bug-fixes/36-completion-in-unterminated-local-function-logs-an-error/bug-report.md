---
id: "BUG-36"
title: "Typing inside a local function whose `end` is not yet matched logs a plugin error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-36: Typing inside a local function whose `end` is not yet matched logs a plugin error

Found during [[TYPE-14]]'s human checklist (GoLand 2026.2.3, libvirt builder). The scenario itself
was not the cause.

## 1. Reproduction
```lua
---@class Base
---@field k string
local Base = {}
---@generic T : Base
---@param p T
local function f(p)
  local m = p.k
end
f(1)
```
1. Use Ctrl+G to go to `7:end`.
2. Press Enter.
3. Type `p.`.
4. Invoke completion.

At some point while typing, the parser sees `local function f(p) ... p` with no block. The log
shows `Last Action: EditorEnter`.

## 2. Expected vs Actual Behavior
- **Expected**: no error in `idea.log`.
- **Actual**: two `SEVERE` reports, both blamed on Lunar:
  ```
  java.lang.Throwable: local function f
    at com.intellij.psi.impl.PsiElementBase.notNullChild(PsiElementBase.java:295)
    at net.internetisalie.lunar.lang.psi.impl.LuaLocalFuncDeclImpl.getBlock(LuaLocalFuncDeclImpl.java:41)
    at net.internetisalie.lunar.lang.psi.LuaFunctionExtKt.processDeclarations(LuaFunctionExt.kt:109)
    at net.internetisalie.lunar.lang.psi.LuaResolveUtil.scopeCrawlUp(LuaResolveUtil.kt:23)
    at net.internetisalie.lunar.lang.LuaNameReference.lexicalTargetOf(LuaNameReference.kt:220)
  ```
  Each is followed by `InspectionRunner - @NotNull method ... notNullChild must not return null`.
  Completion still offered `k: string`.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaFunctionExt.processDeclarations` and `LuaLocalFuncDeclImpl.getBlock`
  (generated, non-null accessor).
- **Other Notes**: probably not specific to generics. It is a resolve over a `local function`
  whose block is absent while the code is being edited. Not reproduced in a unit test.

## 4. Root Cause
The grammar's `localFuncDecl` and `funcDecl` rules are pinned (`pin = 2` / `pin = 1`) and `funcBody` is
unpinned. When the body fails to parse, as in `local function f(p)` followed by an unmatched line, the
declaration is kept with its name but no `LuaBlock` child (PSI dump: `LOCAL_FUNC_DECL` holds `local`,
`function`, `NAME_REF`, and an error element; the `(p)` is re-parsed as a sibling expression statement).
The generated `getBlock()` is `@NotNull` (`findNotNullChildByClass`) so it throws and logs. Kotlin
treats the result as non-null, so the `block != null` guard in `processDeclarations` never ran.
Resolving the function's own name walks up through the declaration and calls `processDeclarations`.

## 5. Fix
Read the block through the null-tolerant `getBlockList().firstOrNull()` (a `getChildrenOfType` lookup)
at every Kotlin call site of `getBlock()` on `LuaLocalFuncDecl` / `LuaFuncDecl` that can run on a
half-typed file: the three `processDeclarations` extensions in `LuaFunctionExt.kt` (`LuaFuncDef`, `LuaFuncDecl`, `LuaLocalFuncDecl`), the two structure-view
tree elements (`TreeElementUtils.getFuncBodyChildren` now takes `LuaBlock?`), and the two template
builders in `LuaDocGenerator.kt`. No grammar or generated-code change.

## 6. Test
`LuaBlocklessFunctionResolveTest`: resolves the name of a `local function` and a `function` whose
declaration has no block (asserted), and builds both structure-view elements' children. Red on base
with the `notNullChild` error, red again when `LuaFunctionExt.kt` alone is reverted, green with the fix.
