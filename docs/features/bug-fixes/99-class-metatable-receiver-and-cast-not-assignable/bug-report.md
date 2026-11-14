---
id: "BUG-99"
title: "A method call on a `@class` metatable value (`vm.generic`) is reported as `vm.generic | vm.generic is not assignable`, also after a `@cast`, and as an ERROR once the callee's type is resolved"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-99: A method call on a `@class` metatable value is reported as not assignable, also after `---@cast`

Found by MAINT-65's TC 7 diagnostic. In base these are HYPOTHESIS findings. The MAINT-65 lambda gate
resolves the callee's types fully, which promotes four of them to ERROR, so this bug blocks MAINT-65.

## 1. Reproduction
Type corpus member luals. Read `script/vm/compiler.lua` alone in a fresh fixture.

The class comes from `script/vm/generic.lua`:

```lua
---@class vm.generic
---@field sign  vm.sign
---@field proto vm.object
local mt = {}
mt.__index = mt
```

`vm.getGeneric` is declared `---@return vm.generic?`.

| `compiler.lua` line | Code | Finding |
|---|---|---|
| 2508 | `vm.getGeneric(ext):resolve(uri, source.signs)`, inside `if vm.getGeneric(ext) then` | `vm.generic \| vm.generic is not assignable to { ... }`, twice |
| 2798–2799 | `vm.compileByGlobal(source)` and `vm.compileByVariable(source)`, after `---@cast source parser.object` at 2796 | `vm.generic \| vm.generic is not assignable to parser.object` |
| 1685, 1703, 1974, 1976 | the result of `vm.getReturnOfFunction`, declared `(parser.object\|vm.generic)?`, used after a nil guard | `parser.object \| vm.generic \| nil is not assignable to parser.object` |

Base (1a9933cd) and change (1e3e5b8b) counts are in MAINT-65 risks-and-gaps, DR-11.

## 2. Expected
None of these findings should be reported:
- The receiver at line 2508 is a `vm.generic`, whose methods are defined on `mt`.
- At lines 2798–2799, `---@cast` narrows `source` to `parser.object`.
- At the guarded uses, the `nil` arm should be gone after the nil guard.

## 3. Actual
Base reports them as HYPOTHESIS. With MAINT-65's gate, the findings at 2508 and 2798–2799 become
ERROR, and the `| nil` family gains seven HYPOTHESIS findings.

Related: BUG-98 (a class and its constructor table split into two union arms).
