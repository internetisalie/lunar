---
id: "BUG-38"
title: "Too many or too few arguments are not reported when the callee is a function declared in another file"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-38: Too many or too few arguments are not reported when the callee is a function declared in another file

> **Fixed with a narrowed scope:** argument counts are reported for callees declared in the project's own
> source only. Bundled stdlib stubs, definition libraries and `@overload`ed functions are not counted;
> stdlib arity is deferred to [[BUG-46]]. See §5.

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
-- caller.lua, bundled stdlib callees: no diagnostic at any tier
local a = math.floor()
local b = require()
for k in ipairs({}, true) do end
local c = os.getenv('A', true)
```
```lua
-- d.lua
---@param s string
function g1(s) end
U = {}
---@param s string
function U.f(s) end
-- caller.lua: no diagnostic at any tier for any of the four lines
g1()
g1('a', true)
U.f()
U.f('a', true)
```
Corpus sites (audit edit, silent): luacheck `src/luacheck/config.lua:326` `math.floor(x)` → `math.floor()`;
luacheck `spec/cli_spec.lua:43` `type(code51)` → `type(code51, true)`.

## 2. Expected vs Actual Behavior
- **Expected**: *Too few arguments* / *Too many arguments* at the call, as for a `local function` in the
  same file or a method in another file.
- **Actual**: nothing at any tier. In the same probe `U.f(true)` does report *boolean is not assignable to
  string*, so the callee is resolved and its parameter types are applied; only the count is not checked.
  Audit: 0 of 77 measured `arity.*` sites on bundled-stub functions reported, and 0 of 16 on
  `require`d-module members; `SAME_FILE` `LOCAL`/`METHOD` and `PROJECT_FILE` `METHOD` arity sites do report.

## 3. Context / Environment
- **Lua Version**: any (corpus members pinned to their manifest targets, Lua 5.1–5.4 and LuaJIT 2.1).
- **Relevant Files**: `LuaTypeGraph.checkFunctionCompatibility` emits the arity messages; its tier depends on
  `declaredSignature`. That is where the message is built, not an established cause.
- **Other Notes**: the "extra argument to a declared global" case left open by [[BUG-19]] §5 is one instance.
  Cells: `arity.tooFew`/`arity.tooMany` × `LIBRARY` `GLOBAL`/`MEMBER`, `PROJECT_FILE`/`CHECKOUT_FILE` `MEMBER`.

## 4. Root Cause
Established by a probe on `3fd3b5be` (a temporary `System.err` print in `applyDeclaredSignature` and
at the top of `visitFuncCall`, run through `LuaCrossFileArityTest`, then reverted). Line numbers are
at `3fd3b5be`. Two defects, both needed for the silence:

1. **A declaration-typed callee is never counted.** A free global (`g1`) or a free-global member
   (`U.f`, `math.floor`) is seeded from `LuaTypeManager.resolveGlobal` into `declarationTypedNodes`,
   and `LuaTypesVisitor.visitFuncCall` (`LuaTypesVisitor.kt:949`) routes its call to
   `applyDeclaredSignature` (`:1056`) and returns. That path seeds returns and checks parameter
   *types* per argument (BUG-425), but raises no call demand by design (BUG-397), so
   `LuaTypeGraph.checkFunctionCompatibility` — the only place arity is counted — never sees the call.
   The probe confirmed every one of `g1()`, `g1('a', true)`, `U.f()`, `U.f('a', true)` reaches
   `applyDeclaredSignature` and nothing else.
2. **The declared flag is lost on the way in.** In the declaring file the function's graph type has
   `declaredSignature = true` from its `---@param` tags (`LuaTypesVisitor.kt:1547`).
   `resolveGlobal` reads it back through `LuaTypesSnapshot.graphTypeToLuaType`
   (`LuaTypeManagerImpl.kt:260`), whose `functionToLuaType` (`LuaTypes.kt:248`) rebuilds the
   `LuaFunctionType` without the flag. The probe printed `declared=false` for the tagged `g1` and
   `U.f`, identical to an untagged global. So even a count would have been `HYPOTHESIS` tier
   (`LuaTypeGraph.kt:839`), which neither inspection shows. A cross-file *method* keeps the flag
   because it is built by `LuaTypeManagerImpl.methodType` (`:777`), which is why `PROJECT_FILE`
   `METHOD` cells report.

The arity count was withheld from this path because the engine models only an `@overload`ed
function's primary signature (`LuaSignatureCopier.positionsAreUnambiguous`, and
`FreeGlobalMemberTypingTest.testDeclarationTypedCalleeIsNotArityChecked` for
`redis.register_function`'s table form). That reason applies to overloaded callees only.

`math.floor` is not declared by the bundled **5.2–5.4** stubs at all: `runtime/standard/lua-5.4/math.lua`
(as `string.lua`, `os.lua`, `debug.lua`, `coroutine.lua`, `utf8.lua`) is a bare `---@class math` /
`math = {}`; the probe printed `math={ }`. On those levels `math.floor()` has no declared signature
to count against and stays silent after this fix. Only the 5.1 and LuaJIT stubs declare it. That is
a stub-content gap, separate from this defect ([[BUG-46]]).

## 5. Fix Strategy (narrowed scope)
**Scope:** arguments are counted for a callee declared with `---@param` in the project's own source
(project or checkout files). A callee declared in a bundled runtime stub (`src/main/resources/runtime/**`),
a definition library or a test-library stub (busted, luassert) is not counted: their signatures are
not precise enough (counting them, measured against a `3fd3b5be` control, put 39 new findings on
correct corpus code, such as `error()`, `debug.sethook()`, busted `pending("…")` and `load` with four arguments). Stdlib arity is
deferred to [[BUG-46]], since the 5.2–5.4 stubs declare no functions. A function with an `@overload`
is not counted, because the engine models only its primary signature.

- `LuaTypesVisitor.visitFunctionBody` marks a function `countsArguments` when it has `---@param` tags,
  no `@overload`, and its file is project content and not a library root (`ProjectFileIndex`).
- The flag is a new field on `LuaGraphType.Function` and `LuaFunctionType`, carried by
  `functionToLuaType` and `fromLuaType`. `declaredSignature` is **not** carried, so `fromLuaType` keeps
  building cross-file parameters with `memberNodeFor`, and type-golden rendering does not change.
- `LuaTypesVisitor.reportDeclaredArity`, on the declaration-typed path, counts the call's arguments
  when the callee has the flag, through `LuaTypeGraph.reportArity`: the arity half of
  `checkFunctionCompatibility`, extracted unchanged so both paths share `minArity`/`maxArity`
  (optional, vararg, BUG-8 receiver delta). It is reported at the same WARNING tier a declared
  signature gets in its own file.

`checkCompatibility`'s primitive-to-table leniency and the per-argument type check
(`positionsAreUnambiguous`) are not touched.

## 6. Test Strategy
`LuaCrossFileArityTest` (light `IndexedBasePlatformTestCase`, callee in a second file via
`addFileToProject`): too few, too many and exact for a global (`g1`) and a member (`U.f`); silent for an
`@overload`ed callee, for optional and vararg parameters, for an untagged global, and for bundled-stub
callees (`error()`, `tostring(1, 2)`, `type()`, `table.unpack()` and `table.unpack({}, 1, 2, 3)`).
Each part is mutation-proved: removing the count, dropping the flag in `functionToLuaType`, removing
the `@overload` guard, and removing the project-source guard each turn tests red. The corpus sweep
(`test -PwithCorpus`) is compared site by site with a `3fd3b5be` control.
