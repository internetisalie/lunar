---
id: "BUG-8"
title: "A dot call of a colon method with an explicit self matches self to the first @param"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-8: A dot call of a colon method with an explicit `self` misaligns its arguments

Found while triaging [[BUG-566]]'s corpus run. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/core/handler/ft.lua:10`, inside `function M:add(plugin)`:

```lua
---@param plugin LazyPlugin
function M:add(plugin)          -- the parent's LazyHandler:add has the same signature
  self.super.add(self, plugin)  -- dot call, receiver passed explicitly
```

Minimal form (the method may be in the open file or in another file):

```lua
---@class C
local C = {}
---@param a string
function C:add(a) end

---@type C
local c
c.add(c, "x")   -- Too many arguments: expected at most 1, got 2
```

## 2. Expected vs Actual
- **Expected**: `self` fills the method's implicit `self` and `plugin` fills `@param plugin`. No arity or argument diagnostic.
- **Actual**: the arity check reports *Too many arguments: expected at most 1, got 2*. With [[BUG-566]]'s fix applied, `self` is also type-checked against `plugin: LazyPlugin` (3 hits in lazynvim: ft.lua:5, :9, :10).

## 3. Context
- Arity misfires on `main` today. The argument-type hits appear only with [[BUG-566]]'s fix, which promotes them from hypothesis to error.
- The defect is not specific to `super` chains. Any `C.m(obj, x)` call of a colon-declared method misfires, whether the method is in the open file or in another file (§7, P1 and P2).
- The reverse direction has the same cause. A colon call `c:ex("x")` of `function C.ex(self, a)`, a dot-declared function with an explicit `self` parameter, reports *Too few arguments: expected at least 2, got 1* on `main` (§7, P9).

## 4. Root Cause

### 4.1 The type engine has no notion of the receiver
Lua's `t:m(x)` is sugar for `t.m(t, x)`, and `function T:m(x)` is sugar for `function T.m(self, x)`. Lunar's type engine models both sides without the receiver, and records nowhere that the receiver was left out:

| Function source | Site | `params` it builds |
|---|---|---|
| In-file declaration `function C:add(a)` | `LuaTypesVisitor.visitFunctionBody` (`lang/psi/types/LuaTypesVisitor.kt:1562`) | `[a]`. `self` is bound in the body scope (`:1515-1521`), not as a parameter. |
| Class member from the index, `C:add` | `LuaTypeManagerImpl.funcTypeFromStub` (`lang/psi/types/LuaTypeManagerImpl.kt:601-635`, called at `:560`) | `[a]`, from `---@param` tags only. The caller knows the separator (`member.separator`, used at `:589`) but does not pass it. |
| `LuaFunctionType` → graph | `LuaGraphType.fromLuaType`, `is LuaFunctionType ->` (`lang/psi/types/LuaGraphType.kt:260-273`) | copies `params`. |
| Graph → `LuaFunctionType` | `LuaTypesSnapshot.functionToLuaType` (`lang/psi/types/LuaTypes.kt:228-245`) | copies `params`. |
| Generic instantiation | `LuaTypeGraph.doInstantiateGeneric` (`lang/psi/types/LuaTypeGraph.kt:258`) | copies `params`. |
| Call-site demand | `LuaTypesVisitor.visitFuncCall`, `callDemand` (`LuaTypesVisitor.kt:985-989`) | one parameter per argument written between the parentheses. For `c:add("x")` that is `["x"]`. For `c.add(c, "x")` it is `[c, "x"]`. |

Neither `LuaGraphType.Function` (`LuaGraphType.kt:29-55`) nor `LuaFunctionType` (`lang/psi/types/LuaStructuredTypes.kt:96-111`) has a field saying "the receiver is not in `params`".

### 4.2 Where the misalignment happens
`LuaTypeGraph.checkFunctionCompatibility` (`LuaTypeGraph.kt:849-892`) compares a callee value to a call demand by raw position:
- arity: `minParams`/`maxParams` come from `value.params` and `provided = use.params.size` (`:856-858`);
- argument flow: `addEdge(use.params[i].node, value.params[i].node)` for `i` in `0 until min(...)` (`:889-891`).

The positional comparison is only correct when both sides leave the receiver out, as in a colon call to a colon method, or both include it, as in a dot call to a dot function. In the reproduction, `value.params = [plugin]` and `use.params = [self, plugin]`:
- `provided = 2 > maxParams = 1` gives *Too many arguments: expected at most 1, got 2*, at WARNING tier because the signature is declared (`:865`);
- `use.params[0]` (the `self` argument) is wired into `value.params[0]` (`plugin`). On `main` that parameter node carries the in-file `---@param` type, so the receiver is reported against it (P10's `K is not assignable to string`, and lazynvim's ft.lua:5 and :9 hits, §5.6). On [[BUG-566]]'s branch the stub-built parameter node also carries a declared demand (`declaredParamNodeFor`), which promotes the same edge to an ERROR at the argument.

`LuaTypeGraph.isFunctionCompatible` (`:827-847`), the dry-run twin, makes the same positional comparison (`:833-834`, `:842-844`).

### 4.3 Consumers that align arguments to parameters
They do not share a helper. Each one aligns on its own:

| Consumer | Site | How it handles the receiver | In scope |
|---|---|---|---|
| Type graph, arity and argument flow | `LuaTypeGraph.checkFunctionCompatibility` / `isFunctionCompatible` | Not at all (§4.2). This is the defect. | **Yes** |
| Declaration-typed callees (BUG-425, free globals and stdlib) | `LuaTypesVisitor.demandDeclaredParams` + `positionsAreUnambiguous` (`LuaTypesVisitor.kt:1048-1123`) | Requires `argExprs.size == declared.params.size`. A receiver mismatch changes the count, so the call is skipped. It under-reports and never misaligns. | No |
| Lambda seeding (TYPE-10) | `LuaTypesVisitor.propagateExpectedLambdaParams` (`:1150`) → `LuaExpectedCallbackResolver.expectedCallbackAt` (`LuaExpectedCallbackResolver.kt:38-43`) | `selfOffset = 1` only for a colon call whose first parameter is *named* `self`. A dot call with explicit `self` to a colon method looks one slot too far, so a lambda is seeded from the wrong parameter or not at all. Not executed. | No (§8) |
| Parameter inlay hints | `LuaParameterInlayHintsProvider.collectParameterHints` (`lang/insight/hint/LuaParameterInlayHintsProvider.kt:67-75`) | Drops `params[0]` for colon calls by name or count heuristics. For a dot call with explicit `self` to a colon method with ≥2 params, the hints are shifted by one. Not executed. | No (§8) |
| Parameter info popup | `LuaParameterInfoHandler` (`lang/insight/hint/LuaParameterInfoHandler.kt:136-188`, `:352-358`) | Reads the PSI declaration. It prepends `self` for a `:` declaration and hides it for a colon call, so a dot call shows `self` in slot 0. This is already correct. | No |

### 4.4 Type-engine invariants (AGENTS.md → Type engine) and BUG-566
- Methods reach a class as `LuaTypeMember`s through `materializeClass` → `collectMethodMembers` → `funcTypeFromStub`. This is the path used both when the method is in another file (the stub arm) and when it is in the open file (the AST arm), because the open file's class type is also materialized. Mutant M3 (§6.2) shows that the in-file value from `visitFunctionBody` and the materialized member meet at the declaration's `:add` site. **Every source in §4.1 must carry the flag, or a colon call to a colon method starts to misfire** (M3: *expected at most 0, got 1* on `c:add("x")`).
- `visitFuncCall` models only `nameAndArgsList.firstOrNull()`. The fix keys on that same `nameAndArgs.methodExpr`, so chained calls behave as before.
- **BUG-566 overlap.** [[BUG-566]] (`bug-566`, `30775259`) edits the same `is LuaFunctionType ->` branch of `LuaGraphType.fromLuaType`: it replaces the `params` mapping with `declaredParamNodeFor`. BUG-8 edits the two `Function(...)` constructor calls on either side of that mapping (`:261`, `:270`). The edits are textually adjacent, not overlapping. Measured: `git apply` of `30775259`'s `LuaGraphType.kt` hunk onto the prototype applies cleanly. With both applied, P7 reports exactly `number is not assignable to string` at the `1` argument, and P1 and P10 are clean (§7). The BUG-8 change itself lives in `LuaTypeGraph`, which BUG-566 does not touch.

## 5. Fix Strategy

### 5.1 The flag
Add `implicitSelf` as the **last** constructor parameter, default `false`, to both function types:
- `LuaGraphType.Function` (`LuaGraphType.kt:29-48`): `val implicitSelf: kotlin.Boolean = false`.
- `LuaFunctionType` (`LuaStructuredTypes.kt:96-111`): `val implicitSelf: Boolean = false`.

KDoc for both: *True when [params] leaves out a receiver that Lua passes first, because the function was declared with `:` (a value) or the call was written with `:` (a demand). [LuaTypeGraph] aligns a value with a demand by this flag (BUG-8).*

### 5.2 Set and carry it at every source in §4.1
| Site | Change |
|---|---|
| `LuaTypesVisitor.visitFunctionBody`, `:1562` | `LuaGraphType.Function(finalParams, returnNodes, catsParams.isNotEmpty(), selfBinding != null)`. `selfBinding` is non-null exactly when `visitFuncDecl` saw a `:` method name (`:880`, `:895-897`). |
| `LuaTypesVisitor.visitFuncCall`, `callDemand`, `:985-989` | add `implicitSelf = methodExpr != null` (the `methodExpr` local at `:960`). |
| `LuaTypeManagerImpl.addMethodsOf`, `:560` | `funcTypeFromStub(scan.className, decl, member.separator == LuaReceiverMember.Separator.COLON)`. |
| `LuaTypeManagerImpl.funcTypeFromStub`, `:601-635` | new third parameter `implicitSelf: Boolean`; return `LuaFunctionType(params, returnType, declaredSignature = params.isNotEmpty(), implicitSelf = implicitSelf)`. |
| `LuaGraphType.fromLuaType`, `:261` and `:270` | pass `type.implicitSelf` as the fourth argument of both `Function(...)` calls. |
| `LuaTypesSnapshot.functionToLuaType`, `LuaTypes.kt:244` | `LuaFunctionType(params, returnType, implicitSelf = type.implicitSelf)`. `declaredSignature` keeps its default, as its KDoc requires. |
| `LuaTypeGraph.doInstantiateGeneric`, `:258` | pass `template.implicitSelf` as the fourth argument. |

The remaining constructors build empty-parameter `function` primitives (`LuaGraphType.kt:256`, `LuaTypesVisitor.kt:1616`) or `fun(...)` annotation types (`TypeParser.kt:150`, which keeps its explicit `self` if written). They stay at the default.

No stub, index or grammar change. The separator is already in `LuaReceiverMember`, so no stub or index version bump is needed.

### 5.3 Align by the flag in `LuaTypeGraph`
Add one private helper next to `checkFunctionCompatibility`:

```kotlin
/** BUG-8: how many more leading arguments [value] expects than [use] wrote: +1 dot-calls a `:` method, −1 colon-calls a `.` one. */
private fun receiverDelta(
    value: LuaGraphType.Function,
    use: LuaGraphType.Function,
): Int = (if (value.implicitSelf) 1 else 0) - (if (use.implicitSelf) 1 else 0)
```

Value parameter `i` lines up with demand parameter `i + delta`. With `delta = +1`, `use.params[0]` is the explicit receiver and binds `self`, so it is wired to nothing. With `delta = −1`, `value.params[0]` receives the implicit receiver and no demand parameter is wired to it.

In **`checkFunctionCompatibility`**:
- `minParams = (value.params.count { !it.isOptional && !it.isVararg } + delta).coerceAtLeast(0)`;
- `maxParams = if (any vararg) Int.MAX_VALUE else (value.params.size + delta).coerceAtLeast(0)`;
- `provided = use.params.size`, unchanged. Messages therefore count the arguments written between the parentheses: `c.add(c, "x", "y")` reports *expected at most 2, got 3*. When `delta == 0` every message is byte-identical to today's.
- Replace the parameter-edge loop (`:889-891`) with a call to a new private helper `wireParams(value, use, delta)`: for each `i in value.params.indices`, let `j = i + delta`; skip if `j !in use.params.indices`; otherwise `addEdge(use.params[j].node, value.params[i].node)`. The helper keeps `checkFunctionCompatibility` under the 30-line tripwire.

In **`isFunctionCompatible`**: the same `delta`, the same clamped `minParams`, and the same `i` → `i + delta` index mapping in the contravariant parameter loop (`:842-844`).

The `coerceAtLeast(0)` clamp covers `delta = −1` against a function with no parameters, for example `h:f()` against `function H.f()`. Without it, `maxParams` would be −1 and the message would read *expected at most -1*. P13 and P14 pass with the clamp. They were not run without it (§8).

### 5.4 What changes for users
- Dot calls of colon methods that pass the receiver explicitly (`Base.init(self, …)`, `self.super.add(self, p)`) no longer report arity, and their arguments are checked against the right parameters.
- Colon calls of dot functions that declare `self` explicitly (`---@param self C` … `function C.ex(self, a)`, then `c:ex("x")`) no longer report *Too few arguments*.
- A colon method passed as a callback and invoked as `fn(obj, e)` (lazynvim `view/float.lua:170`, `self:on(…, self.close)`) is no longer reported.
- **New, and correct:** `c.add("x")` (a dot call that omits the receiver) reports *Too few arguments: expected at least 2, got 1*, because `a` is then `nil` against a required `---@param a string`. On `main` this is silent.

### 5.5 Out of scope
- `LuaExpectedCallbackResolver` / `propagateExpectedLambdaParams` and `LuaParameterInlayHintsProvider` each align by their own heuristics (§4.3). Once the flag exists they could key on `LuaFunctionType.implicitSelf` instead of on a parameter named `self`. That is a separate change with its own tests. Neither one produces an inspection hit.
- `demandDeclaredParams` (BUG-425) skips rather than misaligns. Untouched.

### 5.6 Corpus effect (measured)
Measured with the fix, 2026-10-29, libvirt builder, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` on unfixed `main` (`2bfae0aa`, which includes [[BUG-9]]) and then with the fix: BUILD SUCCESSFUL both times. The unfixed run reproduced every committed baseline. A decrease does not fail the ratchet.

| Member | Key | Baseline | With fix |
|---|---|---|---|
| lazynvim | `inspection.LuaTypeAssignability` | 256 | 238 |
| lazynvim | `inspection.LuaReturnTypeMismatch` | 33 | 29 |
| zerobrane | `inspection.LuaSuspiciousConcatenation` | 3 | 1 |
| luacheck, luarocks, penlight | all keys | — | unchanged |

zerobrane's `LuaSuspiciousConcatenation` is the known flake recorded in BUG-9's report §8: 1 or 3 on identical code, with no LuaCATS on declarations. The two hits that went were at `src/editor/menu_project.lua:96` and `:136`, *operand of type 'nil'*. It is not attributable to this fix, and `zerobrane.baseline` keeps its committed value.

In `lazynvim.baseline`'s capped per-symbol list, `[1]` goes from 12 to 10 and `url` from 11 to 9. `name` drops to 2 and out of the list, and `_on` (3, unchanged) enters it.

A per-site comparison of lazynvim, main against the fix with a temporary site dump in `CorpusSweep.accumulateHits`, **removes 22 hits and adds none**. Every removed hit is a receiver misalignment:

| Site | Hits removed | Shape |
|---|---|---|
| `core/handler/ft.lua:10` `self.super.add(self, plugin)` | 2 × *Too many arguments: expected at most 1, got 2* (Assignability) | dot call, explicit `self`, of `LazyHandler:add` |
| `core/handler/ft.lua:5` `local M = {}` | 10 × *Missing required field* `'_'`, `'[1]'`, `'dir'`, `'name'`, `'url'` (2 each, Assignability) | the `self` argument (an `M`) wired into `plugin: LazyPlugin` |
| `core/handler/ft.lua:9` `function M:add(plugin)` | 2 × ReturnTypeMismatch, 1 × Assignability, `… LazyFiletypeHandler is not assignable to LazyPlugin` | same edge |
| `manage/process.lua:39` `Process.init(self, function() …)` | 2 × *Too many arguments: expected at most 1, got 2* | dot call, explicit `self`, of `Async:init(fn)` |
| `manage/task/init.lua:38` `Task.init(self, function() …)` | 2 × same | same |
| `view/float.lua:225` `function M:on(events, fn, opts)` and `:245` `fn(this, e)` | 1 × Assignability + 2 × ReturnTypeMismatch, *Too many arguments: expected at most 1, got 2* | `self.close` (colon `M:close(opts)`) passed as `fn` at `:170` and called `fn(this, e)` |

Assignability: 2 + 10 + 1 + 2 + 2 + 1 = 18. ReturnTypeMismatch: 2 + 2 = 4.

`lazynvim.baseline` was re-recorded with `-PrecordCorpusBaseline`. No other baseline changed.

## 6. Test Strategy

### 6.1 New test class
`src/test/kotlin/net/internetisalie/lunar/analysis/LuaDotCallExplicitSelfTest.kt` extends `IndexedBasePlatformTestCase`, `@RunWith(JUnit4::class)`, with `myFixture.enableInspections(LuaTypeAssignabilityInspection())`. It follows the shape of `LuaOptionalMethodParamArityTest`.

Shared `defs`:

```lua
---@class C
---@field super C
local C = {}
---@param a string
function C:add(a) end
---@param a string
---@param b string
function C.dot(a, b) end
---@param self C
---@param a string
function C.ex(self, a) end
return C
```

- `hits(text)`: `configureByText("t.lua", text)`, then `doHighlighting()`. Keep descriptions that contain `"arguments"` or `"not assignable"`, then `.distinct()` and `.sorted()`.
- **Stub arm:** `addFileToProject("defs.lua", defs)`. Before highlighting, assert that the `LuaGlobalDeclarationIndex` element `"C:add"` is stub-backed (`.single().stub` non-null, as in `LuaOptionalMethodParamArityTest.methodStubOrNull`). Then call `hits("---@type C\nlocal c\n$call\n")`.
- **AST arm:** `hits("$defs\n---@type C\nlocal c\n$call\n")` with the `return C` line removed. Use `---@type C local c`, not `local c = C`: the latter adds an unrelated `{ ... } | C is not assignable to C` hit (probe run 1).

Every expectation was executed (§7). The probe recorded `description@highlighted-text`. The test asserts distinct descriptions only, which folds the duplicate hit at the method name and at the call into one.

| # | Arm | Call | `main` | After fix |
|---|---|---|---|---|
| T1 | stub | `c.add(c, "x")` | `[Too many arguments: expected at most 1, got 2]` | `[]` |
| T2 | AST | `c.add(c, "x")` | same as T1 | `[]` |
| T3 | stub | `c:add("x")` (control) | `[]` | `[]` |
| T4 | AST | `c:add("x")` (control) | `[]` | `[]` |
| T5 | stub | `c.dot("x", "y")` (control) | `[]` | `[]` |
| T6 | AST | `c.dot("x", "y")` (control) | `[]` | `[]` |
| T7 | stub | `c.add(c, "x", "y")` (genuine) | `[Too many arguments: expected at most 1, got 3]` | `[Too many arguments: expected at most 2, got 3]` |
| T8 | AST | `c.add(c, "x", "y")` (genuine) | same as T7 | same as T7 |
| T9 | stub | `c:add("x", "y")` (genuine) | `[Too many arguments: expected at most 1, got 2]` | same |
| T10 | stub | `c.add("x")` (receiver omitted) | `[]` | `[Too few arguments: expected at least 2, got 1]` |
| T11 | AST | `c.add("x")` | `[]` | same as T10 |
| T12 | stub | `c:ex("x")` (reverse direction) | `[Too few arguments: expected at least 2, got 1]` | `[]` |
| T13 | stub | `c.ex(c, "x")` (control) | `[]` | `[]` |
| T14 | stub, caller `---@class K : C` / `local K = {}` / `---@param a string` / `function K:add(a)` / `  self.super.add(self, a)` / `end` | — | `[K is not assignable to string, Too many arguments: expected at most 1, got 2, { ... } is not assignable to string, { ... } \| K is not assignable to string]` | `[]` |
| T15 | caller-only file `---@class G` / `---@field f fun()` / `local G = {}` / `---@type G` / `local g` / `g:f()` / `g:f(1)` / `g.f()` | — | not run | `[]` |
| T16 | `h.lua`: `---@class H` / `local H = {}` / `---@return string` / `function H.f() end` / `return H`; caller `---@type H` / `local h` / `h:f()` / `h.f()` | — | not run | `[]` |

Three more cases pass a method value where a callback with an explicit `self` is declared. With `cb` typed as the union `string|fun(self: C, x: string)`, the union demand goes through the dry-run `isFunctionCompatible`:

| # | Arm | Caller | `main`-shaped (M4) | After fix |
|---|---|---|---|---|
| T17 | AST | `---@param cb string\|fun(self: C, x: string)` / `local function take(cb) end` / `take(c.add)` | `[fun(a) is not assignable to union fun(self, x) \| string]` | `[]` |
| T18 | stub | same as T17 | same as T17 | `[]` |
| T19 | AST | same, `cb` typed `fun(self: C, x: string)` (control, no union) | `[]` | `[]` |

T14 is the lazynvim shape. It is also the only `main`-only case where the misrouted *argument edge* is visible, which is why mutant M1 fails it.

### 6.2 Mutation proof (executed on the prototype)
| Mutant | Change | Cases that go red | Cases that stay green |
|---|---|---|---|
| M1 | arity uses `delta`, `wireParams` reverted to the positional loop | T14 (`K is not assignable to string`, `{ ... } is not assignable to string`, `{ ... } \| K …`) | all others |
| M2 | `receiverDelta` ignores `use.implicitSelf` | T3, T4 (*Too few arguments: expected at least 2, got 1*), T9 (`[]`), T12, T14, and T2, T6, T11 gain a hit at the `:add` declaration | T1, T5, T13, T15, T16 |
| M3 | `funcTypeFromStub` always passes `implicitSelf = false` | T1, T2, T3 and T4 (*expected at most 0, got 1*), T7, T8, T9, T14 | T5, T10 (stub), T12, T13, T15, T16 |

| M4 | `isFunctionCompatible` reverted to positional arity and parameters, the rest fixed | T17, T18 | all others |

M3 shows that the in-file AST arm also depends on the stub path (§4.4).

Measured on the committed test (distinct descriptions): M1 turns only T14 red. M2 turns T2, T3, T4, T6, T8, T9, T12, T14, T17 and T19 red. T11 stays green, because the hit M2 adds at the declaration has the description T11 already expects. M3 turns T1, T2, T3, T4, T6, T7, T8, T9, T10 (stub), T14, T17, T18 and T19 red. Each restored to green.

### 6.3 Existing tests
With the prototype, the full unit suite (`test --rerun --no-build-cache`) ran 3415 tests: 8 failed, 4 skipped. All 8 failures were the throwaway probe's placeholder expectations, so no existing test changed, including the `types-golden` arity goldens and `LuaOptionalMethodParamArityTest`. The real fix must reproduce this: full suite green, then the §5.6 corpus run.

## 7. Phase 0 probe (executed)
2026-10-29, worktree `bug-8` at `4d82fe19`, libvirt builder, `test --rerun --no-build-cache --tests '*Bug8ProbeTest*'`. The probe was a throwaway `IndexedBasePlatformTestCase` with `LuaTypeAssignabilityInspection`, in the T1–T16 shapes above. It has been removed. Results are distinct `description@text`, duplicates at the method name collapsed:

| # | Case | `main` | Prototype fix | `main` + BUG-566 | Fix + BUG-566 |
|---|---|---|---|---|---|
| P1 | stub `c.add(c, "x")` | Too many, at most 1, got 2 | clean | Too many + `C is not assignable to string` at `c` | clean |
| P2 | AST `c.add(c, "x")` | Too many, at most 1, got 2 | clean | same as P1 | clean |
| P3 | `c:add("x")`, both arms | clean | clean | clean | clean |
| P4 | `c.dot("x", "y")`, both arms | clean | clean | clean | clean |
| P5 | `c.add(c, "x", "y")`, both arms | at most 1, got 3 | at most 2, got 3 | at most 1, got 3 + `C is not assignable` | at most 2, got 3 |
| P6 | stub `c:add("x", "y")` | at most 1, got 2 | same | same | same |
| P7 | stub `c.add(c, 1)` | Too many, at most 1, got 2 | clean | Too many + `C is not assignable to string` at `c` | `number is not assignable to string` at `1` |
| P8 | stub `c:add(1)` | clean | clean | `number is not assignable to string` at `1` | same |
| P9 | stub `c:ex("x")` | Too few, at least 2, got 1 | clean | Too few | clean |
| P10 | `self.super.add(self, a)` | Too many + `K is not assignable to string` (+2 union variants) | clean | Too many + `K` / `{ ... }` assignability hits at `a`, `self`, `K`, `add`, `{}` | clean |
| P11 | `c.add("x")`, both arms | clean | Too few, at least 2, got 1 | clean | Too few, at least 2, got 1 |
| P12 | `M.add(M, "x")` via `require("mod")`, no `@class` | clean | clean | clean | clean |
| P13 | T15 (`fun()` field, colon call) | not run | clean | not run | not run |
| P14 | T16 (no-param dot function, colon call) | not run | clean | not run | not run |

P12 is clean on `main` because a module's function reaches the caller through `functionToLuaType`, which drops `declaredSignature`, so its arity stays at the hypothesis tier. It is not a control for this fix.

## 8. Not established
- **The BUG-566 corpus.** The table in §7 shows that BUG-566's argument-type hits on the probe shapes go away with this fix. The lazynvim corpus was not run with both changes applied, so it is not established that all three of BUG-566's ft.lua hits (ft.lua:5, :9, :10) vanish there. On `main`, the ft.lua:5 and :9 hits already exist and are removed by this fix (§5.6).
- **Whether the clamp is reachable at a visible tier.** P13 and P14 pass with the clamp and were not run without it. A `---@field f fun()` colon call does not appear to be arity-checked at all (P13 is clean, and so is a `fun(self: F, a: string)` field called both ways, which was probed). An undeclared no-param function stays at the hypothesis tier. The clamp prevents a nonsense `-1` bound and does not change any observed result.
- **The TYPE-10 lambda seeding and the parameter inlay hints** (§4.3). The misalignment for a dot call with explicit `self` is read from the code, not executed. These are candidates for a follow-up bug, not part of this fix.
- **A method value flowing into a declared callback type** (`---@param cb fun(x: string)` given `obj.method`). With the flag, `delta = +1` applies there too, which is semantically right: Lua passes `x` as `self`. The corpus moved only at `view/float.lua`, where the method is *called* as `fn(this, e)`. No case asserts the declared-callback-type direction.
