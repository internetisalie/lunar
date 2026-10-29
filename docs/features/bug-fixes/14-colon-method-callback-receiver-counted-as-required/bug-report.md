---
id: "BUG-14"
title: "A colon method passed as a callback is reported as too few arguments when it has no required parameter"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-14: A colon method passed where `function` or `fun()` is declared reports *Too few arguments*

A regression from [[BUG-8]] (`18618d8f`). Found while planning [[BUG-10]].

## 1. Reproduction

```lua
---@class F
local M = {}

---@param opts? {wipe:boolean}
function M:close(opts)
  return opts
end

---@param fn function
function M:on_key(fn)
  fn()
end

function M:setup()
  self:on_key(self.close)
end
```

`function M:close` may also be in another file (§7, A7).

## 2. Expected vs Actual
- **Expected**: no arity diagnostic. `M:close` has no required parameter, so any callback convention can call it. This is what `2bfae0aa`, the commit before BUG-8, reports.
- **Actual** on `18618d8f`: WARNING *Too few arguments: expected at least 1, got 0* at the `fn` parameter (line 10) and at the `fn()` call (line 11). It fires the same way for `---@param fn fun()` and `---@param fn fun(opts?: table)`, and when `fn` is called as `fn(self)` (§7, A1–A3, A2b).

## 3. Root Cause

`LuaTypeGraph.minArity` (`lang/psi/types/LuaTypeGraph.kt:908-911`) adds BUG-8's receiver shift to the required-parameter count without any condition:

```kotlin
): Int = (value.params.count { !it.isOptional && !it.isVararg } + receiverDelta(value, use)).coerceAtLeast(0)
```

When a colon method meets a demand written without `:`, `receiverDelta` (`:903-906`) is +1. A demand without `:` is any function-typed slot (`function`, `fun()`, `fun(opts?)`) or a plain call `fn()`. The `+1` makes the receiver a required argument even when the method declares no required parameter. In the reproduction, `M:close` has `params = [opts?]` and `implicitSelf = true`, and the demand has `params = []` and `implicitSelf = false`. So `minArity = 0 + 1 = 1` and `provided = 0`. The WARNING tier comes from `value.declaredSignature` (`:869`), because `opts` has a `---@param` tag.

The trace probe recorded the pairs: `value=fun(opts?) vSelf=true vDecl=true use=fun() uSelf=false`, at `uEl='fn'` (the declared-parameter demand) and at `uEl='fn()'` (the call demand that reaches the value through `fn`).

This is a defect for two reasons:
- Lua fills a missing receiver with `nil`, like any other argument. The `:` declares a slot, not a requirement. Only a `---@param` tag makes an argument a contract ([[BUG-419]]; `LuaGraphType.Function.declaredSignature` KDoc, `LuaGraphType.kt:32-46`). Before BUG-8 the receiver was not counted at all, so a method with no required parameter never reported arity.
- The shift is still needed when a required parameter follows the receiver. `c.add("x")` against `function C:add(a)`, with a required `a`, cannot reach `a`. BUG-8 reports this case correctly (§7, G4), and the fix must keep it.

`isFunctionCompatible` (`:832-851`), the dry-run twin that picks a union member, calls the same `minArity` (`:838`). On `18618d8f`, `M:close` passed to `---@param fn string|fun()` fails the dry run and records the HYPOTHESIS *fun(opts?) is not assignable to union fun() | string*. `2bfae0aa` records no such error (§7, A8/A9).

### 3.1 Not a BUG-8 regression: a bare `function` type demands zero parameters
`LuaPrimitiveType.FUNCTION` becomes `Function(emptyList(), emptyList())` (`LuaGraphType.kt:262`). This is the same graph type as `fun()`, so a function with a required `---@param` that is passed where `function` is declared gets *Too few arguments: expected at least 1, got 0*. **This already happens on `2bfae0aa`** (§7, P1, a `local function f(a)`; A4, a colon method with a required `a`). BUG-8 changes only the count in A4's message, from 1 to 2. A bare `function` declares no signature and should probably impose no arity. That needs a marker that the graph `Function` does not have today, and the change is independent of this one. **It is out of scope here.** §8 records it as unfiled.

### 3.2 The second observation is correct and stays
BUG-10's planner saw *Too many arguments: expected at most 0, got 1* at a dot function `function M.close(n)` (with `---@param n? number`) and at the `M.close` argument. The case was passed as `fn` and called `fn(self)`. **It does not reproduce in isolation** on `18618d8f` (§7, D1–D8: no arity hit). It reproduces only when another file in the project declares the same class member with `:` (`---@class F` … `function M:close(opts)`), which is D9. The trace shows the pair: `value=fun(n?) vSelf=false use=fun(opts?) uSelf=true`, at `uEl='.close'`. The class member's declared signature, a `:` method taking `opts`, is a demand on the dot redefinition. `receiverDelta = −1`, so `maxArity = 1 − 1 = 0` against the one parameter `opts`.

That is the right answer. Callers of `F:close` write `x:close(opts)`, so `M.close` receives `x` as `n` and drops `opts`. Both declarations of `F.close` cannot hold. `2bfae0aa` was silent because it wired `opts` to `n` by position. The message counts written arguments, following BUG-8's convention. BUG-10's probe added the cases' files to one project, which is what put a colon `F:close` beside the dot one (inferred from its `CallbackShape` cases, all `---@class F`). This fix does not touch D9, and §6 pins it.

## 4. Fix Strategy

The change goes in one function, `LuaTypeGraph.minArity` (`LuaTypeGraph.kt:908-911`). It reads the same `value`/`use` pair and returns the same type. Count the receiver shift only when the value has a required parameter:

```kotlin
/**
 * BUG-14: an implicit receiver is required only when a required parameter follows it. Lua fills a
 * missing receiver with nil like any other argument, and the `:` declares a slot, not a
 * requirement — only a `---@param` tag is a contract (BUG-419). A dot call that omits the receiver
 * of `function C:add(a)` with a required `a` still falls short, because `a` cannot be reached.
 */
private fun minArity(
    value: LuaGraphType.Function,
    use: LuaGraphType.Function,
): Int {
    val required = value.params.count { !it.isOptional && !it.isVararg }
    if (required == 0) return 0
    return (required + receiverDelta(value, use)).coerceAtLeast(0)
}
```

- `delta = +1` with no required parameter: 0, as on `2bfae0aa`. This is the fix.
- `delta = +1` with a required parameter: unchanged. `c.add("x")` still gives *expected at least 2, got 1*.
- `delta = 0` or `−1`: unchanged. When `required == 0`, the old formula was already clamped to 0.

The function has 2 arguments and 3 logic lines, and its callers are unchanged. `maxArity`, `alignedParams` and `receiverDelta` stay as they are. The prototype is `~/.cache/claude-scratch/lunar/6726d4f8/bug-14/bug-14-prototype.diff` (it applies to `18618d8f` with `git apply --check`).

### 4.1 Consumers
`minArity` has exactly two callers (`grep -n 'minArity' LuaTypeGraph.kt`):

| Consumer | Site | Effect |
|---|---|---|
| `checkFunctionCompatibility` | `:860` | The reported *Too few arguments* bound. Fixes A1–A3, A2b and A7. |
| `isFunctionCompatible` (dry run) | `:838` | Union and trait member selection (`:541-608`, `:753`, `:770`). Fixes the A8/A9 union HYPOTHESIS. |

Nothing outside `LuaTypeGraph` reads the arity. `LuaFunctionType.isAssignableTo` (`LuaStructuredTypes.kt:133`) returns `true` for any function pair. No stub, index, grammar or `plugin.xml` change is needed.

## 5. Corpus Effect (measured)
On the libvirt builder, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` with the prototype on `18618d8f` gave BUILD SUCCESSFUL. `LuaCorpusSweepTest` ran 5 tests and `LuaTortureCorpusTest` ran 1, with 0 failures. Every `inspection.*` count matches the committed baselines for luacheck, luarocks, penlight, lazynvim and zerobrane, except zerobrane's `LuaSuspiciousConcatenation` (baseline 3, observed 1), which is the known flake ([[BUG-541]]) and is ignored. **No baseline moves.** The corpus has no visible-tier instance of the pattern, which matches BUG-8's own corpus run, where it did not appear either. No baseline is re-recorded.

## 6. Test Plan

Extend `src/test/kotlin/net/internetisalie/lunar/analysis/LuaDotCallExplicitSelfTest.kt`. Do not add a class. Reuse its `hits(text)` (inspection enabled, `doHighlighting()`, descriptions containing `arguments` or `not assignable`, distinct, sorted). [[BUG-10]]'s plan adds `CallbackShape`, `callbackHits`, `colonClose` and `dotClose` to the same class, so the names below avoid them. Add:

```kotlin
private val optionalOptsMethod = "---@param opts? {wipe:boolean}\nfunction M:close(opts)\n  return opts\nend"
private val requiredArgMethod = "---@param a string\nfunction M:close(a)\n  return a\nend"

private fun onKeyHits(close: String, fnTag: String, call: String): List<String> =
    hits(
        "---@class F\nlocal M = {}\n\n$close\n\n---@param $fnTag\nfunction M:on_key(fn)\n  $call\nend\n\n" +
            "function M:setup()\n  self:on_key(self.close)\nend\n",
    )

/** Every graph error at every tier, HYPOTHESIS included, after highlighting [text]. */
private fun graphMessages(text: String): List<String> {
    hits(text)
    return runReadAction { LuaTypesSnapshot.forFile(myFixture.file).getErrors().map { it.message } }.distinct()
}
```

The generated text is exactly the probe's (`Bug14ProbeTest.shape(close, fnTag, call, "self:on_key(self.close)")`). Every result below was executed in the probe (§7), filtered the way `hits` filters. M-B and M-C are the mutants in §6.1.

| Test | Input | Assert | `2bfae0aa` | `18618d8f` | Prototype | Mutant |
|---|---|---|---|---|---|---|
| `testOptionalParamMethodMatchesBareFunctionType` | `onKeyHits(optionalOptsMethod, "fn function", "fn()")` | `[]` | `[]` | `[Too few arguments: expected at least 1, got 0]` | `[]` | — |
| `testOptionalParamMethodMatchesEmptyFunType` | `onKeyHits(optionalOptsMethod, "fn fun()", "fn()")` | `[]` | `[]` | same as above | `[]` | — |
| `testOptionalParamMethodCalledWithSelfMatchesBareFunctionType` | `onKeyHits(optionalOptsMethod, "fn function", "fn(self)")` | `[]` | `[]` | same | `[]` | — |
| `testOptionalParamMethodMatchesOptionalParamFunType` | `onKeyHits(optionalOptsMethod, "fn fun(opts?: table)", "fn()")` | `[]` | `[]` | same | `[]` | — |
| `testOptionalParamMethodMatchesBareFunctionTypeCrossFile` | `addFileToProject("defs.lua", "---@class S\nlocal S = {}\n---@param opts? table\nfunction S:close(opts) end\nreturn S\n")`, then `hits("---@type S\nlocal s\n---@param fn function\nlocal function take(fn) end\ntake(s.close)\n")` | `[]` | `[]` | same | `[]` | — |
| `testRequiredParamMethodPassedToEmptyFunTypeIsTooFew` (positive control) | `onKeyHits(requiredArgMethod, "fn fun()", "fn()")` | `[Too few arguments: expected at least 2, got 0]` | `…at least 1, got 0` (red) | green | green | M-B red (`…at least 1, got 0`) |
| `testOptionalParamMethodMatchesUnionWithFunTypeInDryRun` | `graphMessages("---@class F\nlocal M = {}\n\n$optionalOptsMethod\n\n---@param fn string\|fun()\nfunction M:on_key(fn)\n  \nend\n\nfunction M:setup()\n  self:on_key(self.close)\nend\n")` | no message contains `is not assignable to union fun() \| string` | none | present (HYPOTHESIS) | none | M-C present (red) |
| `testDotRedefinitionOfColonMemberStillReports` (pins §3.2) | `addFileToProject("other.lua", <F text below>)`, then `hits(<G text below>)` | contains `Too many arguments: expected at most 0, got 1` | absent (red) | present | present | M-B, M-C present |

The texts for the last test, as Kotlin string literals:
- F, the colon declaration in the other file: `"---@class F\nlocal M = {}\n\n$optionalOptsMethod\n\n---@param fn fun(self?)\nfunction M:on_key(fn)\n  fn(self)\nend\n\nfunction M:setup()\n  self:on_key(self.close)\nend\n"`
- G, the open file: `"---@class F\nlocal M = {}\n\n---@param n? number\nfunction M.close(n)\n  return n\nend\n\n---@param fn fun(self?)\nfunction M:on_key(fn)\n  fn(self)\nend\n\nfunction M:setup()\n  self:on_key(M.close)\nend\n"`

In table cells, `\|` is the Markdown escape for `|`. The Kotlin text has a bare `|`.

The dry-run test asserts at every tier because the union mismatch is HYPOTHESIS, which no inspection shows. Its input has an empty call line (`"  "`), as in the probe.

### 6.1 Mutation proof (executed on the probe)
| Mutant | Change | Probe cases that go red | Stay green |
|---|---|---|---|
| — | `18618d8f` itself (fix absent) | A1, A2, A2b, A3, A7 (visible), A8, A9 (HYPOTHESIS) | all others |
| M-B | receiver never required: `(required + minOf(receiverDelta, 0)).coerceAtLeast(0)` | G4 `c.add("x")` (now `[]`, the existing `testDotCallOmittingReceiverIsTooFewInFile` shape), A4/A5 (count 2 → 1) | A1–A3, A7, A8, A9 |
| M-C | prototype `minArity`, but `isFunctionCompatible:838` keeps the old formula | A8, A9 (the union HYPOTHESIS returns) | every visible case |

The implementer repeats M-B and M-C on the committed tests (`mutation-proof` skill), then restores.

### 6.2 Gates
- The full unit suite, `test --rerun --no-build-cache`. It is green with the prototype (§7.1), so the real fix must reproduce that.
- `ktlintCheck`.
- The §5 corpus run. Expect no movement except the zerobrane flake.

## 7. Phase 0 probe (executed)
2026-10-29, libvirt builder, `test --rerun --no-build-cache --tests '*Bug14ProbeTest*'`. The probe was a throwaway `IndexedBasePlatformTestCase` with one `@Test` per case (a fresh project each) and `LuaTypeAssignabilityInspection`. It recorded `doHighlighting()` and `LuaTypesSnapshot.getErrors()` at every tier, plus a temporary `println` trace of each arity failure in `checkFunctionCompatibility`. It ran on `2bfae0aa` (a detached worktree, since removed), on `18618d8f`, on `18618d8f` + the prototype, and on the mutants. 30 tests passed on every tree. The probe and the trace are removed. Its source is `~/.cache/claude-scratch/lunar/6726d4f8/bug-14/Bug14ProbeTest.kt`.

Cases use the §6 `onKeyHits` text unless stated. Cells are the distinct visible hits; "n assign." counts assignability hits that are the same on every tree.

| # | Case | `2bfae0aa` | `18618d8f` | Prototype |
|---|---|---|---|---|
| A1 | optional-param method → `fun()`, `fn()` | `[]` | Too few, at least 1, got 0 | `[]` |
| A2 | → `function`, `fn()` | `[]` | Too few, at least 1, got 0 | `[]` |
| A2b | → `function`, `fn(self)` | `[]` | Too few, at least 1, got 0 | `[]` |
| A3 | → `fun(opts?: table)`, `fn()` | `[]` | Too few, at least 1, got 0 | `[]` |
| A7 | stub arm: `S:close(opts?)` in `defs.lua` → `function` | `[]` | Too few, at least 1, got 0 | `[]` |
| A8 | → `string\|function` (HYPOTHESIS tier) | none | `fun(opts?) is not assignable to union fun() \| string` | none |
| A9 | → `string\|fun()` (HYPOTHESIS tier) | none | same | none |
| A10 | required-param method → `string\|fun()` (HYPOTHESIS tier) | `fun(a) is not assignable to union …` | same | same |
| A4 | required-param method → `function`, `fn()` | Too few, at least 1, got 0 | Too few, at least 2, got 0 | at least 2, got 0 |
| A5 | required-param method → `fun()`, `fn()` | at least 1, got 0 | at least 2, got 0 | at least 2, got 0 |
| A6 | required-param method → `fun(s: F, x: string)`, `fn(self, "x")` | Too many, at most 1, got 2 (+4 assign.) | `[]` | `[]` |
| P1 | `local function f(a)` (required) → `function` | Too few, at least 1, got 0 | same | same |
| P2 | `local function f(a?)` → `function` | `[]` | `[]` | `[]` |
| P3 | `local function f(a)` → `fun()` | Too few, at least 1, got 0 | same | same |
| D1 | `M.close(n?)` → `fun(self?)`, `fn(self)`, wired `self:on_key(M.close)` (BUG-10's T5) | 6 assign. | 6 assign. | 6 assign. |
| D2 / D3 | same, `fn` typed `function` / `any` | 4 assign. | 4 assign. | 4 assign. |
| D4 / D5 | `function M.close()` (no params, ± `@return`) → `function`, `fn(self)` | `[]` | `[]` | `[]` |
| D6 | D1 through a local `h = M.close` | 6 assign. | 6 assign. | 6 assign. |
| D7 | `M.close(a)` (required) → `fun()`, `fn()` | Too few, at least 1, got 0 | same | same |
| D8 | D1 with a dot `M.on_key(fn)` and `M.on_key(M.close)` | 8 assign. | 8 assign. | 8 assign. |
| D9 | D1, plus `other.lua` declaring `F:close(opts?)` with `:` | 4 assign. | **Too many, at most 0, got 1** (+4 assign.) | same as `18618d8f` |
| D10 | colon `M:close`, plus `other.lua` declaring dot `M.close(n?)` | `[]` | `[]` | `[]` |
| G1 | `f(a, b)` called `f("x")` (genuine) | Too few, at least 2, got 1 | same | same |
| G2 | `g(a, b)` → `fun(x: string)` (genuine) | Too few, at least 2, got 1 | same | same |
| G3 | `c:add("x", "y")` (genuine) | Too many, at most 1, got 2 | same | same |
| G4 | `c.add("x")`, receiver omitted (genuine since BUG-8) | `[]` | Too few, at least 2, got 1 | same |
| C1 | `c:add("x")` (BUG-8 control) | `[]` | `[]` | `[]` |
| C2 | `c.add(c, "x")` (BUG-8 control) | Too many, at most 1, got 2 | `[]` | `[]` |

D1–D8's assignability hits (`{ ... } | F is not assignable to nil | number` and similar) are `fn(self)` passing `self` into `n: number?` and are genuine. They are also identical on all three trees.

### 7.1 Full suite with the prototype
`test --rerun --no-build-cache` with the prototype on `18618d8f` (libvirt builder, after the probe was removed) gave BUILD SUCCESSFUL in 13m 31s: 3420 tests, 0 failures, 4 skipped, summed from the builder's `build/test-results/test/TEST-*.xml` right after the run. No existing test changes its result, `LuaDotCallExplicitSelfTest` and the `LuaTypeGolden*` tests included.

## 8. Not established
- **Bare `function` arity (§3.1), unfiled.** It predates BUG-8 (P1, A4 on `2bfae0aa`). It needs an "unknown signature" marker on `LuaGraphType.Function`, set where `LuaPrimitiveType.FUNCTION` and `TYPEOF_MAP["function"]` (`LuaTypesVisitor.kt:1617`) build one, and kept through `functionToLuaType` (`LuaTypes.kt:228-245`). It was not prototyped, and no ID was minted for it.
- **D9 was not run against a mutant that breaks it.** Its only red result is `2bfae0aa`, where no alignment exists at all.
- **That BUG-10's probe shared a project between cases** is inferred from D9 reproducing its T5 hit and D1 not. BUG-10's probe source was not available.
- **The cross-file test does not assert that the method is stub-backed.** A7 used `addFileToProject` without the `C:add` stub assertion that BUG-8's `addStubBackedDefs` makes.
- **The dry-run test reads the errors after `doHighlighting()`**, as the probe did. It was not run reading the snapshot without highlighting first.
