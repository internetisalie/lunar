---
id: "BUG-11"
title: "An optional union parameter type collapses to nil"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-11: An optional parameter whose declared type cannot be resolved demands `nil`

Found while triaging [[BUG-566]]'s corpus run. It blocks [[BUG-566]]. The root cause was established
by an executed probe (§7). The title's premise is partly wrong. The union and the table literal do
not matter. The cause is that the method's declared type does not resolve, and the parameter is
optional. The defect is in [[BUG-566]]'s own `declaredParamNodeFor`, so the fix lands on top of
BUG-566 (§5).

## 1. Reproduction

lazynvim, `lua/lazy/view/float.lua:222-225` declares

```lua
---@param opts? vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
function M:on(events, fn, opts)
```

and `view/init.lua:76-81` calls `self:on({ "User" }, function() … end, { pattern = pattern })`.
At that call, `self` is `---@cast self LazyView` (`init.lua:52`), and `LazyView: LazyFloat` (`init.lua:20`).

Minimal form (§6.1, `testOptionalParamOfUnresolvedTypeAcceptsATable`). The method is in another
file, and the receiver is a cast `self` of a subclass:

```lua
-- defs.lua
---@class C
local C = {}
---@param opts? vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
function C:on(opts) end
-- t.lua
---@class E : C
local E = {}
function E.create()
  local self = setmetatable({}, { __index = E })
  ---@cast self E
  self:on({ pattern = "x" })
end
```

## 2. Expected vs Actual
- **Expected**: `{ pattern = "x" }` is accepted. The declared type does not resolve, so the engine
  has no demand it can check. This is what already happens when the same parameter has no `?`.
- **Actual**: on `main`+[[BUG-566]], *{ ... } is not assignable to nil* is reported at ERROR, at the argument.
  On `main` alone there is no hit at any tier.

## 3. Context
- **Re-measured hit list** (lazynvim, `main` `8d8ab1b3` + BUG-566 `30775259` cherry-picked, with a
  temporary per-site dump in `CorpusSweep.accumulateHits`). Exactly **one**
  `… is not assignable to nil` hit remains: `lua/lazy/view/init.lua:81`, ERROR. The other 4 hits that
  BUG-566 §8 attributed to BUG-11 are gone. They were the `init.lua:23`/`:299` hits that
  [[BUG-10]] §8 traced to [[BUG-8]], which is now merged.
- The union, the table literal and the `false` literal type are irrelevant (§7: P3b, P3, P4 collapse
  the same way). Being cross-file matters only because that path never parses the type string (§4.1, step 1).

## 4. Root Cause

### 4.1 The chain (on `main`+BUG-566)
1. **The declared type is unresolved.** `LuaTypeManagerImpl.funcTypeFromStub`
   (`LuaTypeManagerImpl.kt:625`) builds each parameter as `LuaTypeReference(it.typeName, decl)`
   from the raw tag text. `resolveType` (`:117`) looks the whole string up as a primitive, then as
   a class or alias name only (`doResolveType`, `:318`). Any composite text resolves to `UNKNOWN`.
   That covers `string | number`, `{buffer: boolean}` and `A | {…}` (§7, `paramType` column).
   So does an unresolvable single name. `LuaGraphType.fromLuaType` maps `UNKNOWN` to `Undefined`
   (`LuaGraphType.kt:261`).
2. **The optional widening turns `Undefined` into `nil`.** BUG-566 adds
   `LuaGraphType.declaredParamNodeFor` (`LuaGraphType.kt:404` with `30775259` applied on `8d8ab1b3`; `:398` in `30775259` itself). Its line `:413`
   builds an optional parameter's declared demand as `Union.create(listOf(graphType, Nil))`.
   `LuaTypeAlgebra.simplify` (`LuaTypeAlgebra.kt:51-52`) drops `Undefined` from any union that
   has another member. So `Undefined | nil` canonicalizes to **`Nil`**. A declared demand of
   exactly `nil` is attached to the parameter.
3. **A required parameter escapes by accident.** Without `?`, the demand stays `Undefined`, and
   `LuaTypeGraph.checkCompatibility` returns early on an `Undefined` use (`LuaTypeGraph.kt:531`).
   That is why P2/P3c/S4 are silent and P1/P3b/S1 are not.
4. **The tier is order-dependent.** The parameter node's up-set carries the `Undefined` declared
   value, so a check made on that node has `unknownProvenance` (`LuaTypeGraph.kt:449`) and files
   HYPOTHESIS (`:335`). A check made through the argument's own node, whose up-set is only the
   table, files ERROR. `addError` de-duplicates on element and message, not tier (`:280`). So
   whichever check runs first decides what the user sees. The `---@type C local c; c:on(…)` shape
   gets HYPOTHESIS (P1). The cast-`self` subclass shape, and the real `init.lua:81`, get ERROR
   (S1, R1). Which check runs first was not traced.

### 4.2 Not the cause (measured)
- **`TypeParser` for `opts?` with a union.** The stub path never reaches `TypeParser`
  (`LuaTypeGraphBridge.resolveTypeWithGenerics`, `LuaTypeGraphBridge.kt:41-45`, is the only
  parse route, and only the in-file visitor path uses it).
- **Union handling in `LuaGraphType.fromLuaType`.** That is not where this collapse happens:
  `fromLuaType` never sees a `LuaUnionType` here, only `UNKNOWN`. It does collapse a *parsed*
  union's unresolved arm on the in-file path. That is a separate defect on `main` (§9).
- **`main` alone.** `memberNodeFor` attaches no demand, so every P/S case is silent on `8d8ab1b3` (§7).

### 4.3 Consumers
`declaredParamNodeFor` is reached only from `fromLuaType`'s `LuaFunctionType` branch when
`declaredSignature` is true (`LuaGraphType.kt:273-274` with `30775259` applied on `8d8ab1b3`). There are two producers:
- `funcTypeFromStub` (`LuaTypeManagerImpl.kt:635`), for class methods with `---@param` tags. This
  is the corpus path.
- `TypeParser.parseFunctionSignature` (`TypeParser.kt:150`), for `fun(x?: T)` annotations.
  `fun(x?: Unres)` collapses the same way and is fixed by the same guard. It was not probed separately.

No other code changes. `LuaTypeGraphBridge.demandTypeFor` (`LuaTypeGraphBridge.kt:150-158`, `main`)
has the same `T | nil` construction. There, `T` is `Undefined` only when `TypeParser` returns
`UNKNOWN` (string or boolean literal types, L1/L3). The declared `Undefined` value always sits in
the same up-set there, so it is HYPOTHESIS-only and silent (§7). It is left unchanged.

## 5. Fix Strategy
**Where:** on top of BUG-566. The line exists only on branch `bug-566` (`30775259`), so deliver
BUG-11 there, as a separate commit after BUG-566's commit, or folded into BUG-566's resume. It
cannot land on `main` alone. `bug-566` does not contain BUG-8 (`18618d8f`). Rebase it onto
current `main` first. The only conflict is BUG-566's `bug-report.md`, a docs file.

**Change** (`LuaGraphType.declaredParamNodeFor`, `LuaGraphType.kt:413` after the rebase; `:407` in `30775259`): widen
with `nil` only when the declared type is known:

```kotlin
val demand = if (param.isOptional && graphType != Undefined) Union.create(listOf(graphType, Nil)) else graphType
```

An unresolvable optional parameter then gets the same `Undefined` demand as an unresolvable
required one, which `checkCompatibility` already treats as unchecked. Extend the function's KDoc
with one sentence naming BUG-11 and the `Undefined | nil → nil` canonicalization. The function
stays at 3 arguments and 7 logic lines.

Rejected: parsing composite `typeName`s in `funcTypeFromStub` through `TypeParser` (on `main`).
It does not fix this case. `vim.api.keyset.create_autocmd | {…}` would parse to a union whose
unresolved arm `simplify` drops, leaving the exact table `{buffer, win?} | nil`, which still
rejects `{ pattern = … }`. It would also start checking every composite-typed cross-file
parameter, which needs its own corpus triage (§9).

## 6. Test Plan

### 6.1 New cases in `net.internetisalie.lunar.analysis.LuaMethodArgumentTypeTest` (extend BUG-566's class; do not add a class)
Add to `defs`, directly before `---@class D : C`:

```lua
---@param opts? vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
function C:on(opts) end
---@param opts vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
function C:onRequired(opts) end
```

Add imports `net.internetisalie.lunar.lang.psi.LuaFile` and
`net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot`. Add two private helpers built on the
existing `assignabilityHits` (1 argument each):

```kotlin
/** BUG-11: the caller shape that reaches the user as an ERROR (lazynvim `view/init.lua:81`). */
private fun onSubclassSelf(call: String) =
    assignabilityHits(
        "---@class E : C\nlocal E = {}\nfunction E.create()\n  local self = setmetatable({}, { __index = E })\n" +
            "  ---@cast self E\n  $call\nend\n",
    )

/** Every tier, HYPOTHESIS included: the `---@type C` receiver files its collapse there. */
private fun nilDemandsAnyTier(call: String): List<String> {
    assignabilityHits("---@type C\nlocal c\n$call\n")
    return LuaTypesSnapshot
        .forFile(myFixture.file as LuaFile)
        .getErrors()
        .map { "${it.severity}:${it.message}" }
        .filter { it.endsWith("is not assignable to nil") }
}
```

All results below were executed on `main` `8d8ab1b3` + `30775259`. M2 is §6.2's mutant.

| Test | Call | Assert | Before fix | After fix | M2 |
|---|---|---|---|---|---|
| `testOptionalParamOfUnresolvedTypeAcceptsATable` | `onSubclassSelf("self:on({ pattern = \"x\" })")` | `emptyList()` | **red**: `[{ ... } is not assignable to nil@{ pattern = "x" }]` | green | green |
| `testOptionalParamOfUnresolvedTypeDemandsNoNil` | `nilDemandsAnyTier("c:on({ pattern = \"x\" })")` | `emptyList()` | **red**: `[HYPOTHESIS:{ ... } is not assignable to nil]` | green | green |
| `testRequiredParamOfUnresolvedTypeAcceptsATable` (control: the escape in §4.1 step 3) | `onSubclassSelf("self:onRequired({ pattern = \"x\" })")` | `emptyList()` | green | green | green |
| `testWrongArgumentToSubclassOptionalStillReports` (positive control: the subclass shape does report) | `onSubclassSelf("self:m(\"x\", 1)")` | `listOf("number is not assignable to union nil \| string@1")` | green | green | **red**: `number is not assignable to string@1` |

The 9 existing tests in the class stay green after the fix. Under M2, `testExplicitNilToOptionalIsSilent`
and `testWrongOptionalArgumentIsReported` also go red.

### 6.2 Mutation proof (executed)
- **M1 = the pre-fix line** (remove `&& graphType != Undefined`). The two BUG-11 tests go red with
  the hits in the *Before fix* column. Both controls stay green.
- **M2 = over-fix** (`val demand = graphType`, no nil widening at all). The BUG-11 tests stay
  green. The new positive control and the two existing optional tests go red. This proves the
  guard must keep the widening for known types.
- Implementer: repeat M1 and M2 on the real tests (the `mutation-proof` skill) and restore.

### 6.3 Gates
Executed on the prototype (`main`+`30775259`+fix+§6.1): full suite
`test --rerun --no-build-cache` gave **3457 tests, 0 failures, 4 skipped**. No golden moved.
Implementer: repeat that gate on the rebased branch, run `ktlintCheck`, and run the corpus gate (§8).

## 7. Phase 0 probe (executed)
Throwaway `Bug11ProbeTest` (`IndexedBasePlatformTestCase`, `LuaTypeAssignabilityInspection`). The
method is declared in `defs.lua` as `---@class C` / `function C:on(opts)`, and the caller is in
`t.lua`. The probe ran `doHighlighting()`, then read `LuaTypesSnapshot.getErrors()` at every tier.
It also read the member's parameter type through `LuaTypeManager.resolveType("C").resolveMember("on")`.
It ran on the libvirt builder with `--rerun --no-build-cache` on `main` `8d8ab1b3`,
`main`+`30775259`, and `main`+`30775259`+fix. R1 used the real `view/float.lua` and
`view/init.lua`. The probe was removed afterwards.

| Case | `@param` / call | main | +BUG-566 | +BUG-566+fix |
|---|---|---|---|---|
| P1 optional union with table literal (the bug) | `opts? Unres.ns \| {buffer: false, win?:boolean}` / `c:on({pattern="x"})` | silent | HYPOTHESIS `{ ... } is not assignable to nil` | silent |
| P1 in-file (same, one file) | same | silent | HYPOTHESIS, same | silent |
| S1 same type, cast-`self` subclass caller | same / `self:on({pattern="x"})` | silent | **ERROR** `… to nil` | silent |
| S2 same type, loop-variable field | same / `c:on({pattern=p})` | silent | HYPOTHESIS `… to nil` | silent |
| P2 non-optional variant | `opts Unres.ns \| {…}` | silent | silent | silent |
| S4 non-optional, subclass caller | same / `self:on(…)` | silent | silent | silent |
| P3 optional non-union | `opts? {buffer: boolean}` / `{buffer=true}` | silent | HYPOTHESIS `… to nil` | silent |
| P3b optional unresolved name | `opts? Unres.ns` | silent | HYPOTHESIS `… to nil` | silent |
| P3c required unresolved name | `opts Unres.ns` | silent | silent | silent |
| P4 optional union of named types | `opts? string \| number` / `"x"` | silent | HYPOTHESIS `string is not assignable to nil` | silent |
| P7 optional union, resolved arm with table literal | `opts? string \| {buffer: false, …}` | silent | HYPOTHESIS `… to nil` | silent |
| P6 genuine: optional primitive (must report) | `opts? string` / `{}` | HYPOTHESIS `… to string` | **ERROR** `… to union nil \| string` | **ERROR**, same |
| S3 genuine, subclass caller (must report) | `opts? string` / `self:on({})` | HYPOTHESIS | **ERROR** | **ERROR** |
| P5 genuine: optional union of named types | `opts? string \| number` / `{}` | silent | HYPOTHESIS `… to nil` (wrong demand) | silent (see §9) |
| R1 real `view/init.lua` + `view/float.lua` | `init.lua:81` | silent | **ERROR** `{ ... } is not assignable to nil` | no ERROR |

On every tree, the parameter type read through `resolveMember` was `ref'<whole tag text>'->unknown`,
which maps to graph `undefined`, for every case except P6/S3 (`string`).

## 8. Corpus Effect
- **lazynvim** (`main`+`30775259`+fix, per-site dump, executed): the fix removes exactly one hit,
  `view/init.lua:81` *{ ... } is not assignable to nil*, and adds none. The other 277 dumped
  hits are identical. With BUG-566 alone, `testLazynvimCorpus` failed its ratchet. With BUG-566
  and the fix, it **passed** against the committed baseline. That is the single-member invocation,
  which BUG-418 says can read ±2 off the full `*Corpus*` run.
- **`main` alone:** none. The change is on BUG-566's code.
- Implementer: run the full `-PwithCorpus --tests '*Corpus*'` gate on the rebased branch. If it
  records a lower baseline, re-record with `-PrecordCorpusBaseline`.
- **BUG-566 §8 hold table:** BUG-11's row is now 1 hit (`init.lua:81`), not 5. The other 4 were BUG-8's.

## 9. Not established
- **The other corpus members** were not run with BUG-566 plus the fix. The guard can only remove
  demands, so it should not add hits there, but this was not measured.
- **Why S1/R1 file at ERROR while P1 files HYPOTHESIS** (§4.1 step 4). Order-dependent dedup in
  `addError` is the likely mechanism. The check order was not traced.
- **The `fun(x?: Unres)` producer** (§4.3) is covered by reading only.
- **Found while probing, outside BUG-11, on `main` (not filed):**
  - **In-file union with an unresolved arm narrows the demand.** `---@param opts? Unres.ns | {buffer: boolean}`
    on a `local function f(opts)` with `f({ pattern = "x" })` reports **ERROR** *{ ... } is not
    assignable to union nil | { ... }* (L2). The required variant reports **ERROR** *Missing required
    field 'buffer'* (L4). Both are on `main` with no BUG-566. Here `TypeParser` parses the union, and
    `simplify` drops the `Undefined` arm (`LuaTypeAlgebra.kt:51-52`), so the declared demand is
    stricter than written. It has no lazynvim hit in the BUG-566 dump. A file-level count on
    other members was not taken. It should be filed as its own bug.
  - **Cross-file composite `@param` types are never checked.** Step 1 of §4.1 means
    `string | number`, `{…}`, `T[]` and `fun(...)` on a stub-backed method resolve to `undefined`.
    So a genuinely wrong argument goes unreported (P5). This is BUG-566's scope gap, not introduced
    by this fix: P5 is silent on `main` too, and its BUG-566 HYPOTHESIS named the wrong demand (`nil`).
