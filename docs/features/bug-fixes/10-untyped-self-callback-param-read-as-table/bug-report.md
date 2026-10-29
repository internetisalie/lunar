---
id: "BUG-10"
title: "An untyped fun(self) parameter is read as a table type"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-10: An untyped `fun(self)` callback parameter is read as a table type

Found while triaging [[BUG-566]]'s corpus run. It blocks [[BUG-566]]. Root cause established by an executed probe (§7): the cause is [[BUG-8]], which is merged (`18618d8f`). This report adds the regression test BUG-8 lacks for this shape. No production code changes.

## 1. Reproduction

lazynvim, `lua/lazy/view/float.lua:250-257`:

```lua
---@param fn fun(self?)
function M:on_key(key, fn, desc, mode)
  vim.keymap.set(mode or "n", key, function()
    fn(self)
```

Also `view/init.lua:297-319`, `---@param fallback? fun(self)` called as `fallback(self)`.

## 2. Expected vs Actual
- **Expected**: `fun(self)` declares one parameter named `self` with no type, so any argument is accepted.
- **Actual**: the argument is checked against a `{ ... }` table demand. 6 hits with [[BUG-566]]'s fix (float.lua:29, :255, :257; init.lua:23, :299, :319); hypotheses only on `main`.

## 3. Context
- The title's premise is wrong. The `{ ... }` demand does not come from `fun(self)`. It is the
  `opts` parameter of a **colon method passed as the callback**: `float.lua:169`
  `self:on_key(ViewConfig.keys.close, self.close, "Close")` (`M:close(opts)`,
  `---@param opts? {wipe:boolean}`), and `init.lua:222/227/232` pass `self.hover`, `self.diff`
  (`opts? {commit?:string, …}`) and `self.restore` (`opts? {commit:string}`) as `fallback`.
- The three hits per file are one mismatch filed at three anchors: the `self` argument
  (`:257`, `:319`), the method declaration that owns the parameter (`:255`, `:299`), and the
  value's origin `local M = {}` (`:29`, `:23`).

## 4. Root Cause

### 4.1 The chain (on `2bfae0aa`, before BUG-8)
1. `self:on_key(…, self.close, …)` flows `M:close`'s function value into `fn`. That value is
   built without the receiver: its params are `(opts)`.
2. `fn(self)` is a dot-style call. Its demand is `Function(params = [self])`
   (`LuaTypesVisitor.visitFuncCall`).
3. `LuaTypeGraph.checkFunctionCompatibility` (`LuaTypeGraph.kt:849`, `2bfae0aa`) paired
   parameters by raw position (`:889-891`, `addEdge(use.params[i].node, value.params[i].node)`).
   The `self` argument was wired into `opts`, against `{wipe:boolean}` / `{commit:string}`.
4. On `main` alone the demand on `opts` is inferred, so the error was `HYPOTHESIS` and invisible.
   [[BUG-566]] gives a declared signature's parameters a declared demand, which turned it into the
   six `ERROR`s. With a primitive `---@param n? number` on the method (§6, `T1`), the same edge is
   already user-visible on `2bfae0aa` without BUG-566.

This is BUG-8's defect, with the method reaching the call as a value instead of through `t.m`.
BUG-8 (`18618d8f`) records `implicitSelf` on both function values and call demands, and aligns
them by it in `alignedParams` (`LuaTypeGraph.kt:928`). `checkFunctionCompatibility` uses that at
`:893`. The colon method's `implicitSelf = true` (`LuaTypesVisitor.kt:1563`, and
`LuaTypeManagerImpl.kt:560` on the class-member path) against the call's `false` gives delta +1.
`self` binds the implicit receiver, and `opts` gets no argument.

### 4.2 Not the cause (measured or read)
- **`fun(self)` parsing.** `self` with no `:` matches the type-only alternative
  (`luacats.bnf:200`, `<<ArgType optionalType>>`). `TypeParser.parseFunctionArgument`
  (`TypeParser.kt:153`) then names the parameter `_` (`:158`), with type `LuaTypeReference("self")`.
  That resolves to the empty bundled `---@class self` (`runtime/standard/lua-5.4/builtin.lua:79`,
  the same in every `builtin.lua`). `LuaGraphType.fromLuaType` mints the node via `memberNodeFor`
  (`LuaGraphType.kt:272`). This differs from LuaLS, which reads a bare name as an untyped
  parameter **name**. But it produces no diagnostic on any tree: the `fun(self?)`/`fun(self)`
  cases with no method wired in are silent on `main`, BUG-8, BUG-566 and BUG-566+BUG-8 (§7, A/B/T4).
  `fn(1)` against `fun(self?)` is silent too (§7 F), which is what LuaLS's "untyped" reading
  also gives. So no change is needed for the corpus hits. See §8 for the latent part.
- **TYPE-10 `selfOffset`** (`LuaTypesVisitor.kt:1151`, `propagateExpectedLambdaParams`). It only
  seeds a lambda argument's parameters. Every lambda passed at these sites is `function() … end`,
  so there is nothing to seed. Also, `fun(self?)` names its parameter `_`, so the
  `params.firstOrNull()?.name == "self"` test is false. Its effect was not separately mutated.
  The edge mutant M1 (§6.2) alone brings back every hit, and M2 alone removes none, so the hits
  depend only on the reporting-path alignment.
- **The `fun(...)` annotation on `fn`.** `---@param fn function` (§7 `T3`) gives the same
  misalignment on `main`. Only the wired method value matters.

### 4.3 Consumers
No consumer changes. Both alignment sites are BUG-8's `alignedParams`: the reporting
`checkFunctionCompatibility` (`LuaTypeGraph.kt:893`, which this test pins) and the dry-run
`isFunctionCompatible` (`:846`, already pinned by BUG-8's `testMethodMatchesUnionCallback*`).
`propagateExpectedLambdaParams` is untouched (§4.2).

## 5. Fix Strategy
No production change. BUG-8 (`18618d8f`) is the fix. Deliver a test-only change on a branch cut
from current `main` (the `bug-10` branch is at `2bfae0aa` and must be rebased onto `18618d8f`
first). Add §6.1's tests to the existing `LuaDotCallExplicitSelfTest`, then close BUG-10 as
`done`. Remove BUG-10 from BUG-566's *Depends on* list and from its §8 hold table, recording
the 6 as BUG-8's.

## 6. Test Plan

### 6.1 New cases in `net.internetisalie.lunar.analysis.LuaDotCallExplicitSelfTest` (extend; do not add a class)
Reuse its `hits(text)` helper (inspection enabled, `doHighlighting()`, descriptions containing
`arguments` or `not assignable`, distinct, sorted). Add one private fixture builder and two
`close` declarations. No `--` comment lines go between the tags and the declarations, and there
are no blank lines between a tag and its function (BUG-9):

```kotlin
private data class CallbackShape(val fnTag: String, val call: String, val close: String, val wire: String)

private fun callbackHits(shape: CallbackShape): List<String> =
    hits(
        "---@class F\nlocal M = {}\n\n${shape.close}\n\n---@param ${shape.fnTag}\nfunction M:on_key(fn)\n" +
            "  ${shape.call}\nend\n\nfunction M:setup()\n  ${shape.wire}\nend\n",
    )

private val colonClose = "---@param n? number\nfunction M:close(n)\n  return n\nend"
private val dotClose = "---@param n? number\nfunction M.close(n)\n  return n\nend"
```
A data class keeps the helper within the ≤3-argument cap. The generated text is exactly what was probed.

| Test | `fnTag` / `call` / `close` / `wire` | Assert | `2bfae0aa` (executed) | `18618d8f` (executed) |
|---|---|---|---|---|
| `testColonMethodValueCalledWithSelfIsSilent` (float.lua shape) | `fn fun(self?)` / `fn(self)` / `colonClose` / `self:on_key(self.close)` | `emptyList()` | red: 7 hits, e.g. `{ ... } \| F is not assignable to number` at `on_key`/`self`, `… nil \| number` at `n` | green, `[]` |
| `testOptionalColonMethodFallbackCalledWithSelfIsSilent` (init.lua shape) | `fn? fun(self)` / `if fn then fn(self) end` / `colonClose` / same | `emptyList()` | red: 5 hits | green |
| `testUnwiredSelfCallbackIsSilent` (control) | `fn fun(self?)` / `fn(self)` / `colonClose` / `self:on_key(function() end)` | `emptyList()` | green | green |
| `testDotFunctionValueCalledWithSelfStillReports` (positive control: `fn(self)` really passes `self` as `n`) | `fn fun(self?)` / `fn(self)` / `dotClose` / `self:on_key(M.close)` | `"{ ... } \| F is not assignable to nil \| number" in hits` (membership only, see §8) | green | green |

### 6.2 Mutation proof (executed on the probe, BUG-8 tree)
- **M1**: `LuaTypeGraph.kt:893` `alignedParams(value, use)` → `value.params.zip(use.params)`.
  The two shape tests go red with the same hits as `2bfae0aa`. The control and positive control
  stay green.
- **M2**: the same edit at `:846` (dry-run). All four stay green. This test pins the reporting
  path; BUG-8's union-callback tests pin the dry run. Record that in the KDoc.
- Implementer: repeat M1 on the real test (the `mutation-proof` skill) and restore.

### 6.3 Gates
Full suite `test --rerun --no-build-cache` via `builder-run`, plus `ktlintCheck`.
`LuaDotCallExplicitSelfTest` must be otherwise unchanged and green.

## 7. Phase 0 probe (executed)
Throwaway `Bug10ProbeTest` (`IndexedBasePlatformTestCase`, `LuaTypeAssignabilityInspection`,
`doHighlighting()` plus raw `LuaTypesSnapshot.getErrors()` at every tier), run on the libvirt builder with
`--rerun --no-build-cache` on four trees: `main` `2bfae0aa`, BUG-8 `d2b2224a` (code identical to
merged `18618d8f`), `main`+BUG-566 (`30775259` cherry-picked), and `main`+BUG-566+BUG-8. It also
ran on the real `lazynvim` `view/float.lua` and `view/init.lua` (with `float.lua` added to the
project). Removed after the runs.

| Case | main | +BUG-8 | +BUG-566 | +BUG-566+BUG-8 |
|---|---|---|---|---|
| lazynvim `float.lua` :29/:255/:257 | HYPOTHESIS only | none at any tier | **3 ERROR** `{ ... } \| LazyFloat is not assignable to { ... }` | none |
| lazynvim `init.lua` :23/:299/:319 | HYPOTHESIS only | none (one HYPOTHESIS at `:300`) | **ERROR** `{ ... } \| LazyView is not assignable to { ... }` (plus 5 `… to nil` hits at :23/:299) | none; only `:81` remains ([[BUG-11]]) |
| A/B `fun(self?)`/`fun(self)`, `fn(self)`, no method wired | silent | silent | silent | silent |
| F `fun(self?)`, `fn(1)` | silent | silent | silent | silent |
| D `fun(x?)`, `fn(self)` | silent | silent | HYPOTHESIS `… is not assignable to nil` | same |
| C `fun(self: F)`, `fn(self)` (typed control) | HYPOTHESIS `{ ... } \| F is not assignable to F` | same | **ERROR**, same message | **ERROR** (§8) |
| E `fun(n: number)`, `fn("s")` (genuine) | HYPOTHESIS | HYPOTHESIS | ERROR | ERROR |
| T1 / T2 / T3 / T4 / T5 | see §6.1 | see §6.1 | red | T1/T2/T4 silent, T5 hits |

## 8. Corpus Effect
- **This change:** none. It is test-only. The six hits never reached `main`'s baselines, because
  they are `HYPOTHESIS` without BUG-566.
- **On BUG-566's resume:** the six BUG-10 sites should be gone (fixture-measured on the real
  files, last column of §7). The corpus itself was **not run** on `main`+BUG-566+BUG-8.
- **For BUG-11's triage:** with BUG-566, `init.lua:23` and `:299` also carry
  `… LazyView is not assignable to nil` hits, and BUG-8 removes them too. BUG-566 §8 assigned
  4 unconfirmed hits to BUG-11 "by target type". Some or all of these may be them. Not established.

## 9. Not established
- **No corpus run** on `main`+BUG-566+BUG-8. §8's claim is fixture-level.
- **TYPE-10 `selfOffset`** was ruled out by reading and by M1/M2, not by a direct mutant.
- **Latent `fun(self)` mis-parse** (§4.2): a bare name in a `fun(...)` is read as a type, where
  LuaLS reads it as a parameter name. It is harmless here only because `self` resolves to an empty
  class. `fun(x)` gives a parameter typed with an unresolved `x` (D: a nil-collapse
  hypothesis under BUG-566). Not filed.
- **Found while probing, outside BUG-10, on merged BUG-8 (`18618d8f`), unexplained:**
  - `T3` (`---@param fn function`, `self:on_key(self.close)`) newly reports the WARNING
    *Too few arguments: expected at least 1, got 0* at `fn`. It is absent on `2bfae0aa`. The
    bare `function` type becomes a zero-parameter demand (`LuaGraphType.kt:262`).
    `minArity`'s receiver delta (+1 for the colon method) now exceeds 0. Any colon method
    passed where `function` is declared would warn. BUG-8's corpus run showed no such hit.
  - `T5` (dot `M.close` passed and called `fn(self)`) newly reports the WARNING
    *Too many arguments: expected at most 0, got 1* at `function M.close`'s `.close` and at the
    `M.close` argument. Which colon-call demand meets `M.close` was not traced.
- **The typed control C** gives `{ ... } | F is not assignable to F` for a `---@class F` local
  passed to `fun(self: F)`. This is BUG-566 §7's unfiled spurious hit, and BUG-566 promotes it
  to a user-visible ERROR. It is not caused by BUG-10 or BUG-8.

