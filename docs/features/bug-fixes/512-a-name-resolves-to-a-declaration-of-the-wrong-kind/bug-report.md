---
id: "BUG-512"
title: "A name resolves to a declaration of the wrong kind: a bare name to a member, a member to a bare variable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-512: a name resolves to a declaration of the wrong kind

A bare name can resolve to a table member, and a member access can resolve to a bare variable.
Three executed reproductions:

- **(A)** a bare `close()` resolves to `M:close` in a required module, so *Undeclared variable*
  never fires and Go to Declaration lands on the method.
- **(B)** a member access with no qualified key, `handlers[1].legacy`, resolves to a bare global
  `function legacy()`.
- **(C)** a member access `config.timeout` resolves **lexically** to a `local function timeout` /
  `local timeout` / 5.5 `global function timeout` / `global timeout` in scope.

Found in [[BUG-504]]'s review (round 1 m1, round 2 m1, round 3 a4, round 4 F1). All three
reproductions were executed. The root causes were read, not run.

## 1. Reproduction (executed)

### 1.1 (A) a bare name → a required module's colon method

Heavy, real-disk fixture (`require` resolution reads `LocalFileSystem` only, so a light fixture's
`temp://` module is never found and the defect does not appear). The source root is the temp dir,
and `sourcePath` = `<tempdir>/?.lua`.

```lua
-- mod.lua
local M = {}
function M:close() end
function M.open() end
return M
```

```lua
-- main.lua
local mod = require("mod")
close()
open()
never_declared()
```

Probe output, `LuaUndeclaredVariableInspection` enabled, `resolve()` per name
(scratch `~/.cache/claude-scratch/lunar/bug-504/xml/40-bug512-probe/`, run `40-bug512-probe`):

```
[probe:512] require close@27 resolve=mod.lua@24:'close'
[probe:512] require open@35 resolve=null
[probe:512] require never_declared@42 resolve=null
[probe:512] require undeclared=[Undeclared variable 'open', Undeclared variable 'never_declared']
```

Control, the same `main.lua` without the `require` line:

```
[probe:512] norequire close@0 resolve=null
[probe:512] norequire undeclared=[Undeclared variable 'close', Undeclared variable 'open', Undeclared variable 'never_declared']
```

The dotted `M.open` behaves correctly in both; only the colon method leaks.

### 1.2 (B) a member access with no qualified key → a bare global

Light fixture (`BasePlatformTestCase`), `LuaDeprecatedApiInspection` and
`LuaUndeclaredVariableInspection` enabled:

```lua
-- legacy_lib.lua
---@deprecated gone
function legacy() end
```

```lua
-- user.lua
local handlers = {{}}
local a = handlers[1].legacy
local b = handlers.legacy
return a, b
```

Probe output, `multiResolve` per `legacy` reference, then every highlight with a description
(run `50-r4-red`, `Bug512A4ProbeTest`, source kept in scratch):

```
[probe:a4] legacy@44 targets=[legacy_lib.lua@20:LuaFuncDeclImpl:'function legacy() end']
[probe:a4] legacy@70 targets=[]
[probe:a4] highlights=[44..50 Deprecated API: gone]
```

`handlers[1].legacy` resolves to the global function and carries its deprecation. The control
`handlers.legacy`, which has a qualified key, resolves to nothing, as it should.

### 1.3 (C) a member access → a bare variable in lexical scope

Light fixture. Each file is `local config = {}` / `---@deprecated gone` / `<decl>` /
`local function get() return config.timeout end` / `timeout(5)` / `return get`, with `<decl>` one of
the four forms below. Probe output, `multiResolve` per reference, with the target's offset and its
parent (run `71-f1-probe`, `Bug504F1ProbeTest`, source kept in scratch):

```
[probe:f1] localfunc timeout@124 parent=LuaIndexExprImpl targets=[@53:LeafPsiElement:parent=LuaNameRefImpl:'timeout']
[probe:f1] localvar timeout@126 parent=LuaIndexExprImpl targets=[@44:LeafPsiElement:parent=LuaNameRefImpl:'timeout']
[probe:f1] globalfunc timeout@125 parent=LuaIndexExprImpl targets=[@54:LeafPsiElement:parent=LuaNameRefImpl:'timeout']
[probe:f1] globalvar timeout@127 parent=LuaIndexExprImpl targets=[@45:LeafPsiElement:parent=LuaNameRefImpl:'timeout']
```

| `<decl>` | target |
| :-- | :-- |
| `local function timeout(ms) config.timeout = ms end` | `@53` |
| `local timeout = function(ms) config.timeout = ms end` | `@44` |
| `global function timeout(ms) config.timeout = ms end` | `@54` |
| `global timeout = function(ms) config.timeout = ms end` | `@45` |

In every form the member `timeout` of `config.timeout` (parent `LuaIndexExpr`) resolves to the
declared variable's own name leaf. With `---@deprecated` on that variable, `config.timeout` is
reported *Deprecated API*. That is observed in BUG-504's run `70-r5-red`, on BUG-504's round-4
commit, as `(124, 131)`, `(126, 133)`, `(125, 132)` and `(127, 134)`. Main's `local function` /
`local` branches compare by identity-or-text and so pass it too; that last point is reasoned, not run
on main.

## 2. Expected vs actual

- **Expected:** a bare name resolves only to variables, such as locals, parameters and bare globals.
  A member access resolves only to members of its receiver: nothing when the receiver is unknown,
  never to a bare global.
  - **(A):** *Undeclared variable 'close'* is reported, and Go to Declaration on `close` finds
    nothing.
  - **(B):** `handlers[1].legacy` resolves to nothing, and carries no deprecation.
  - **(C):** `config.timeout` resolves to the field `config.timeout` (or to nothing), never to a
    variable named `timeout`.
- **Actual:**
  - **(A):** `close` resolves to the method's name leaf in `mod.lua`. *Undeclared variable* does not
    fire for it, and Go to Declaration (which uses the same single `resolve()` result) lands on
    `M:close`.
  - **(B):** `handlers[1].legacy` resolves to `function legacy()`. It inherits that function's
    `---@deprecated`, and Go to Declaration on it lands there.
  - **(C):** `config.timeout` resolves to the `timeout` variable in scope and inherits its
    `---@deprecated`.

## 3. Root cause (read, not executed)

- **(A)** `LuaFileBindingsIndex.funcDeclNameLeaves` records, for a file-scope `LuaFuncDecl`, the bare
  head `nameRef` **and** the `funcNameMethod` leaf as file bindings. A method name is not a variable
  of the file. `LuaNameReference.doMultiResolve` phase 2 queries the bindings of every required file
  (`RequiredFilesQuery`) by name (`collectFileResults`), so a bare `close` matches the method's
  binding. `LuaUndeclaredVariableInspection` reports only names that `multiResolve` cannot resolve,
  so the spurious result silences it. The `M` head is recorded too, but [[BUG-509]] already keeps a
  `local` root out of the global index; whether the bindings index should also drop it is not
  established here.
- **(B)** `LuaNameReference.getQualifiedName` returns null for a member whose `var` chain is not all
  dotted names. For `t[1].x`, the loop stops at the bracket suffix. For `(e).x`, `luaVar.nameRef` is
  null. `doMultiResolve` then skips the qualified-key branch and runs the **bare-name** lookups with
  the member's short name. These are the `LuaGlobalDeclarationIndex` key `x`, which holds a bare
  `function x()` and the root occurrence of every `function x.<…>`. They also include the file
  bindings. A member name is therefore looked up as though it were a variable.
- **(C)** `LuaNameReference.doMultiResolve` phase 1 (`lexicalTargetOf` → the scope crawl) runs for a
  member name under `LuaIndexExpr` as it does for a bare name. A variable of the same name in scope
  wins before the qualified-key branch is reached. Read, not run: the probe shows the result, not
  which line produces it.

All three are the same defect in two directions: the lookup does not respect whether the reference
is a bare name or a member access.

## 4. Fix strategy

Not planned. Plan with `plan-bug`.

## 5. Related

- [[BUG-504]] made `LuaDeprecatedApiInspection` apply a deprecation only when the reference's name
  equals the declared name, and the reference is a member access whenever the declaration is a
  member. That removed every warning (A) caused, and the `handlers[1].log` → `log.should_log` form of
  (B). It also requires the resolved leaf to BE the declaration's name, not merely inside it. Only
  resolution can stop (B) and (C), because there the resolved element is the declaration's own name
  and the names match. That is why they are here.
- `LuaDeprecatedBareNameThroughRequireTest` asserts (A)'s resolution as a reach precondition. Fixing
  (A) will make that precondition fail by design; revisit that test then.
