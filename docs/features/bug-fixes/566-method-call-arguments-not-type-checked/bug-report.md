---
id: "BUG-566"
title: "A method call's argument types are never checked against the method's declared `@param` types"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-566: A method call's arguments are not type-checked against its declared parameters

Found while probing an explicit `nil` against an optional method parameter during [[BUG-554]].
Root cause and fix were established by executed probes (light fixture, full unit suite) against `72cd1a99`.

## 1. Reproduction

Light fixture (`IndexedBasePlatformTestCase`), `LuaTypeAssignabilityInspection` enabled,
`configureByText`, `doHighlighting()`, on the `bug-554` branch with the BUG-554 fix applied.

```lua
---@class C
local C = {}
---@param a string
---@param b? string
function C:m(a, b) end
---@type C
local c = C
c:m("x", 1)
c:m(1, "y")

---@param a string
---@param b? string
local function g(a, b) end
g("x", 1)
```
→ no hit on either `c:m` call; `g("x", 1)` reports `number is not assignable to`.

## 2. Expected vs Actual
- **Expected**: a call's argument that violates the method's declared `---@param` type is reported
  **at that argument**, as it is for a free global (`gg(1)` reports `number is not assignable to
  string` on `1`).
- **Actual**, measured on `72cd1a99` (probe tables in §4.3):
  - **Method declared in another file** (stub-backed, the library/corpus case): **no hit at any
    location** for colon (`c:mix(2, "x")`) and dot (`c.f(1)`) calls. The engine does
    find the mismatch, but only at `HYPOTHESIS` tier, which the inspection drops.
  - **Method declared in the same file** (the reproduction above): the mismatch IS reported, but
    only at the method's **declaration** (`a`, `b`, `function C:m(a, b) end`), never at the call's
    argument. The free local `g` in the same file is reported the same way (at its declaration
    `b`), so "no hit on either `c:m` call" is accurate for the call line; it is not an absence of
    any diagnostic.

## 3. Context
- Arity is checked on method calls ([[BUG-554]]); argument types are checked on cross-file free
  globals ([[BUG-425]], `demandDeclaredParams`). This bug is the remaining gap: argument types
  against a **member** signature the engine materializes from a `LuaType` (another file's
  `function C:m`/`C.f`, an inherited method).
- Out of scope, measured in §4.3 and not caused by this root cause (no argument diagnostic at
  **any** tier, so the declared-demand fix cannot reach them): `---@field m fun(a: string)`
  members called as `d.m(1)` (in-file and cross-file), and a colon call on a class obtained through
  `local C = require('defs')`. File these separately if wanted.

## 4. Root Cause

### 4.1 The chain
1. A cross-file method's `LuaFunctionType` comes from `funcTypeFromStub`
   (`LuaTypeManagerImpl.kt:560`, `:634`). Its params are the `---@param` tags only (no `self`),
   and `declaredSignature = params.isNotEmpty()`. A `fun(...)` type literal is
   `declaredSignature = true` (`TypeParser.kt:150`).
2. `LuaGraphType.fromLuaType` turns it into a graph `Function`, minting each parameter's node with
   `memberNodeFor` (`LuaGraphType.kt:260-266`). `memberNodeFor` attaches the type as a demand
   with **no `declaredDemand` flag** (`LuaGraphType.kt:394`:
   `memberNode.downSet.add(graph.use(memberNode.element, graphType))`). The flag is dropped even
   though the function type says its signature is declared.
3. At the call, `checkFunctionCompatibility` wires each argument variable into the parameter node
   (`LuaTypeGraph.kt:890`, `addEdge(use.params[i].node, value.params[i].node)`). The pair checked
   is `(argument value, parameter variable)`, and a variable's `declaredDemand` is "any downstream
   use is declared" (`LuaTypeNodes.kt:160`, `:186`), which is false here.
4. `reportIncompatible` therefore files the mismatch at `HYPOTHESIS` tier
   (`LuaTypeGraph.kt:329-330`), anchored at the argument (the parameter node's element is the
   file, so `isFileWide` falls back to the value element).
5. `LuaTypeAssignabilityInspection` skips every `HYPOTHESIS` error
   (`LuaTypeAssignabilityInspection.kt:32`, BUG-419). Nothing reaches the user.

An in-file method escapes step 2: its call reaches the AST function body, whose parameter nodes
carry the `---@param` demand with `declaredDemand = true` (`LuaTypeGraphBridge.kt:143`), so the
error is `ERROR`. The use element of that demand is the declaration, so that is where it is
anchored. The same applies to an in-file free function.

### 4.2 Not the cause (measured)
- **Implicit `self` offset**: correct. `funcTypeFromStub` has no `self` param and the colon call
  passes only explicit arguments; `c:mix("x", 2)` against `(a: string, b: number)` is silent,
  `c:mix(2, "x")` mismatches on both positions.
- **The BUG-425 path** (`LuaTypesVisitor.kt:950`, `demandDeclaredParams` `:1048`) handles only
  callees in `declarationTypedNodes` (free globals). A `---@type C` local receiver goes through the
  ordinary `methodSiteMember` / `graph.use(o, callDemand)` path (`LuaTypesVisitor.kt:964`, `:1005`).
- **Adding a separate declared demand at the call site is not enough on its own.** Prototyped in
  `checkFunctionCompatibility`: it fixed only the optional-parameter case. In every other case the
  new `ERROR` has the same element and message as the `HYPOTHESIS` already filed, and `addError`
  de-duplicates on `(element, message)` regardless of severity (`LuaTypeGraph.kt:275`), so the
  hypothesis shadows it. Routing the argument to the new demand **instead of** the parameter node
  turned 4 generic tests red (`UnionAndGenericTest.testGenericIdentity`,
  `TestLuaTypeEngineSafety.testGenericIsolationAndMemoization`,
  `LuaUnionDistributionTest.testGenericInstantiationsDoNotCollideUnderMemo`,
  `LuaTypeInlayHintsTest.testGenericFunction`), because generic binding flows through that edge.
  Do not take either route.

### 4.3 Executed evidence
Probe `Bug566ProbeTest` (light fixture, `IndexedBasePlatformTestCase`, inspection enabled,
`doHighlighting()` plus raw `LuaTypesSnapshot.getErrors()`); removed after the run. Cross-file arm:
`defs.lua` declares `---@class C` with `C:m(a: string, b?: string)`, `C.f(a: string)`,
`C:mix(a: string, b: number)`, `C:ins(pos?: integer, value?: string)`,
`C:each(cb: fun(x: string))`. The caller is `---@type C\nlocal c` plus the call.

| Case | Before (HEAD) | After (fix §5) |
|------|---------------|----------------|
| S1 `c:mix(2, "x")` | no hit; 2x HYPOTHESIS at `2`, `"x"` | ERROR at `2` and at `"x"` |
| S2 `c:mix("x", 2)` (self-offset control) | silent | silent |
| S3 `c:m("x", nil)` (explicit nil to `b?`) | no hit; HYPOTHESIS `nil value…` | silent |
| S4 `c:m("x", 1)` | no hit; HYPOTHESIS at `1` | ERROR `number is not assignable to union nil \| string` at `1` |
| S5 dot `c.f(1)` | no hit; HYPOTHESIS at `1` | ERROR at `1` |
| S6 `c:ins("v")` (omitted middle optional) | no hit; HYPOTHESIS at `"v"` | **ERROR** `string is not assignable to union nil \| number`: a false positive, see §5.3 |
| S7 `c:each(function(x) end)` | silent | silent |
| S11 free global `gg(1)` (BUG-425 control) | ERROR at `1` | ERROR at `1` |
| S12 `c:mix(2,"x")` + `c:mix("a",3)` | no hit | ERROR at `2`, `"x"` only |
| S9 `---@field m fun(a: string)`, `d.m(1)` | nothing at any tier | unchanged (out of scope) |
| S8 `local C = require('defs'); C:mix(2,"x")` | no argument error at any tier | unchanged (out of scope) |
| In-file reproduction (§1) | ERROR at the declaration `a`/`b` only | unchanged, plus ERROR at the call arguments `1` |
| In-file inherited `d:say(1)` (golden `inheritance`) | HYPOTHESIS at `1` | ERROR at `1` |

Full unit suite with the fix: 3396 tests, 3 failures besides the probe, all expected moves (§6.3).

## 5. Fix Strategy

### 5.1 The change
`LuaGraphType.kt`, `fromLuaType`, `is LuaFunctionType` arm (`:260-273`). When
`type.declaredSignature` is true, mint each parameter's node with a new private helper instead of
`memberNodeFor`. The helper does what `memberNodeFor` does, with two differences:
- its use node is `graph.use(node.element, demand, declaredDemand = true)`;
- `demand` is `Union.create(listOf(graphType, Nil))` when `p.isOptional`, else `graphType`. This is
  BUG-552's rule (`LuaTypesVisitor.demandTypeOf`, `:1063`), and without it S3 becomes a false
  positive.

When `declaredSignature` is false (inferred signature), keep `memberNodeFor`. The return node is
unchanged. The prototyped shape, which produced the §4.3 "After" column:

```kotlin
val node = if (type.declaredSignature) declaredParamNodeFor(p, graph, visited) else memberNodeFor(p.type, graph, visited)
Function.Parameter(node, p.name, p.isOptional, p.isVararg)

private fun declaredParamNodeFor(p: LuaParameter, graph: LuaTypeGraph, visited: MutableMap<LuaType, LuaGraphType>): VariableNode {
    val anchor = graph.firstNodeElement() ?: error("Graph must have at least one node")
    val node = graph.variable(anchor)
    val graphType = fromLuaType(p.type, graph, visited)
    node.upSet.add(graph.value(node.element, graphType))
    val demand = if (p.isOptional) Union.create(listOf(graphType, Nil)) else graphType
    node.downSet.add(graph.use(node.element, demand, declaredDemand = true))
    return node
}
```
Give the helper a KDoc that names BUG-566 and the dedup trap (§4.2), so nobody "simplifies" it
into a call-site demand. Do not change `addError`, `checkFunctionCompatibility` or
`LuaTypesVisitor`.

### 5.2 Why here
This is where the declared/inferred distinction is lost (§4.1 step 2). After the fix, every path
that wires an argument into a declared signature's parameter (colon, dot, inherited, local alias)
produces an `ERROR` from its existing edge, so there is no hypothesis left to shadow it. The flag
also covers `fun(...)` literals (`TypeParser.kt:150`); the full suite stayed green on them apart
from the §6.3 moves, and S7 (a declared callback slot) is silent.

### 5.3 Known false-positive class: omitted middle optional parameters
The fix applies no positional-alignment guard. S6 `c:ins("v")` against `(pos?: integer,
value?: string)` aligns `"v"` to `pos` and reports. This is the `table.insert` shape BUG-425
measured at 244 corpus false positives, which `positionsAreUnambiguous`
(`LuaTypesVisitor.kt:1120`) guards on the free-global path. In-file methods and functions have no
such guard today either, so the fix gives cross-file members the same behaviour as in-file ones.
The corpus triage (§6.4) decides whether that is acceptable:
- **If optional-middle misalignments are a material share of the new hits**, a guard is needed.
  It cannot go in `fromLuaType`, which never sees the call's argument count. The natural place is
  `checkFunctionCompatibility`: when the value is a declared signature whose parameter nodes came
  from the §5.1 helper and `use.params.size != value.params.size` with any optional parameter,
  skip the argument-to-parameter edges for the optional positions. **This guard is NOT
  prototyped.** It also changes generic inference only if a generic parameter is optional; re-run
  the four generic tests in §4.2 to confirm.
- Otherwise ship without it and record the count.

## 6. Test Strategy

### 6.1 Regression test: `net.internetisalie.lunar.analysis.LuaMethodArgumentTypeTest` (new)
`IndexedBasePlatformTestCase`, `LuaTypeAssignabilityInspection` enabled. Collect
`doHighlighting()` descriptions **with their highlighted text**, because the anchor is part of the
contract. Use a cross-file arm: `myFixture.addFileToProject("defs.lua", <class C as in §4.3>)`,
then `configureByText("t.lua", "---@type C\nlocal c\n<call>\n")`. Before highlighting, assert that
`C:mix` is stub-backed, as `LuaOptionalMethodParamArityTest.stubArmHits` does, so the test cannot
silently become in-file.

| Test | Call | Assert |
|------|------|--------|
| colon arg 1 and arg 2 | `c:mix(2, "x")` | exactly `number is not assignable to string`@`2` and `string is not assignable to number`@`"x"` |
| self offset (control) | `c:mix("x", 2)` | no `is not assignable` hit |
| optional wrong | `c:m("x", 1)` | `number is not assignable to union nil \| string`@`1` |
| optional explicit nil (control) | `c:m("x", nil)` | no hit (BUG-552) |
| optional omitted (control) | `c:m("x")` | no hit (and no arity hit, BUG-554) |
| dot member | `c.f(1)` | `number is not assignable to string`@`1` |
| inherited | `---@class D : C` in defs; `---@type D local d; d:mix(2, "x")` | hit @`2` |
| callback slot (control) | `c:each(function(x) end)` | no hit |
| free global (control) | `gg(1)`, `---@param a string function gg(a) end` in defs | still hits @`1` |

Mutation-proof it (the `mutation-proof` skill): revert `declaredDemand = true` in the helper and
confirm the colon, dot and inherited rows go red while every control stays green. Drop the
optional union and confirm the explicit-nil control goes red.

### 6.2 Existing tests that must stay green unchanged
`LuaOptionalMethodParamArityTest`, `LuaOptionalParamExplicitNilTest`,
`LuaDeclaredContractErrorTest`, `LuaCrossFileGlobalFunctionTest`, `ExpectedCallbackResolverTest`,
the four generic tests in §4.2, and `LuaCatsStubAstParityTest`.

### 6.3 Expected test moves (measured with the fix; update deliberately, not by blind re-record)
- `LuaTypeGoldenShapesTest.everyShapeMatchesItsGolden`: `param`, `paramN20`, `selfCalls`,
  `twoReceivers`, `arity` and `chain` gain an `ERROR` at the offending call argument
  (`LuaTerminalExprImpl`), ahead of the existing declaration-anchored one. `inheritance` changes
  `HYPOTHESIS|number is not assignable to string|(136,137)` to `ERROR`; that is `d:say(1)` against
  the inherited `Base:say(s: string)`, a true positive. Re-record with `-PrecordTypeGolden`
  (output under `build/type-golden/recorded/`, copied back into
  `src/test/resources/types-golden/` by hand), then review every changed line. Expect only
  HYPOTHESIS to ERROR at call arguments, plus new call-argument ERRORs.
- `LuaAnnotatedClassDiagnosticsTest.declaredParamViolationKeepsItsFourErrorTierDiagnostics`
  (`lang/types`): the call-argument `number is not assignable to string` moves from HYPOTHESIS
  (1) into ERROR (2 to 3). Rename or re-describe the test as needed; "four" is no longer the
  count.
- `LuaMemberDemandHubTest.case04ReplicatedHypothesesKeepTheChainList`: same move in its
  `(severity, message, range)` list. Confirm the hub replication list is otherwise unchanged.

### 6.4 Gates and corpus
- Full suite: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` (or
  `builder-run libvirt …`). Never pipe it through `tail`.
- Corpus: `test --rerun --no-build-cache --stacktrace -PwithCorpus --tests '*Corpus*'`. Expect
  `inspection.LuaTypeAssignability` to rise. On `lazynvim` (baseline 306, `library.neovim`
  pinned) the rise has two sources: calls to annotated members from other files and the library,
  and in-file calls now also reported at the argument. Smaller rises are possible on `penlight`
  (5) and the other members. Triage before re-recording:
  1. Read the new `symbol.LuaTypeAssignability.*` lines from the failing ratchet message. For the
     uncapped per-symbol list, run the pinned attribution arm,
     `-PwithCorpus -PcorpusAttribution=pinned --tests '*Corpus*'`, which writes
     `build/corpus-attribution/pinned/<member>.tally`.
  2. Classify a sample of at least 20 new hits (all of them if fewer) as: true positive /
     omitted-middle-optional misalignment (§5.3) / `@overload` not modelled / receiver-type error
     (wrong class inferred) / other. Record the counts in §8 of this report.
  3. If misalignments or overloads dominate, add the §5.3 guard and re-measure. Do not
     re-baseline over them.
  4. Re-record with `-PrecordCorpusBaseline` only after triage, and commit the baseline diff with
     the triage table.

## 7. Not established
- Whether the §5.3 guard is needed, and its exact implementation. It is not prototyped. No corpus
  member has a hit of that shape (§8).
- Why `---@field m fun(...)` members and `require`-obtained class receivers produce no argument
  diagnostic at any tier (§3). This root cause does not explain it.
- The spurious `{ ... } | C is not assignable to C` at `---@type C\nlocal c = C` in the
  reproduction (present before and after the fix). It is unrelated; file separately if wanted.

## 8. Corpus triage

On `72cd1a99`, with the fix, lazynvim's `inspection.LuaTypeAssignability` goes from 306 to 323 (25 new hits, 8
gone) and `inspection.LuaReturnTypeMismatch` from 58 to 59. No other member moves. All 25 new hits
are false positives, and none is the omitted-middle-optional shape §5 guarded against. Each comes
from an existing modelling defect that the fix promotes from hypothesis to error:

| Hits | Cause |
|---|---|
| 13 | [[BUG-8]]: a dot call of a colon method with an explicit `self`, including 6 first filed as BUG-10 (a colon method passed as a callback and called with `self`) and 4 first attributed to BUG-11 (`init.lua:23`/`:299`) |
| 11 | [[BUG-9]]: a bare `---@class ProcessOpts` bound to the module table after a blank line (the 8 gone hits are its old *Missing required field* form) |
| 1 | [[BUG-11]]: an optional parameter of an unresolved declared type demands `nil` (`view/init.lua:81`) |

The fix was held until these were fixed. [[BUG-11]] is a defect in this fix's own optional-parameter
widening and lands with it. On `ba3eb9d7`, a per-site dump of every corpus member (luacheck, luarocks,
penlight, lazynvim, zerobrane) is identical with and without this fix plus [[BUG-11]]: no hit
added, none removed, and every committed baseline holds unchanged.
