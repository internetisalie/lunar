---
id: "TYPE-10-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TYPE-10"
folders:
  - "[[features/type/10-lambda-parameter-inference/requirements|requirements]]"
---

# TYPE-10: Risks & Gaps

## Critical Risks

### Risk 1.1: Shared-engine regression (high blast radius)
- **Impact**: `LuaTypesVisitor.visitFuncCall` runs for **every** function call in every Lua
  file. A propagation edge added at the wrong node — or a resolution that returns a wrong
  `LuaFunctionType` — could pollute inference for calls that pass a lambda, breaking inlay
  hints, the inferred-type annotator, completion, and the assignability/return-type
  inspections.
- **Likelihood**: medium.
- **Mitigation**: the regression contract below (mandatory gate). All new flow is *additive*
  (only new `value → paramNode` edges into lambda parameter nodes that were previously
  `Undefined`); the existing demand edge and arg-node construction are untouched. The
  precedence gate (`isAlreadyAnnotated`) guarantees direct `---@param` types are never
  overwritten. Negative test cases (TC 6/7/8) assert baseline-equivalence for the non-lambda,
  untyped-slot, and non-function-arg paths.

### Risk 1.2: Isolated `--tests` masks a full-suite failure
- **Impact**: a green `test --tests *LambdaParam*` can hide a full-suite JUnit failure
  (synthetic Kotlin lambda methods / shared-index interactions) — a documented project
  hazard.
- **Likelihood**: medium.
- **Mitigation**: the regression gate MUST be a **full** `.../lang/types/*` suite run via
  `gce-builder run test` (or `run build`), not an isolated `--tests` filter. Stated as an
  exit criterion in implementation-plan Phase 3 and requirements TYPE-10-06 / TC 10.

### Risk 1.2b: Intra-traversal ordering — subscript visited before its receiver is seeded
- **Impact**: the lambda **body** (including a `keys[1]` subscript) is visited *during*
  `super.visitFuncCall(o)` (`LuaTypesVisitor.kt:525` → `visitFunctionBody` → `visitBlock`,
  `LuaTypesVisitor.kt:837`), which is **before** `propagateExpectedLambdaParams` adds the seed
  edge (it runs after `super` returns, `LuaTypesVisitor.kt:571`). The current
  `seedSubscriptElement` reads `arrayElementType(receiverNode.write)` **eagerly** at body-visit
  time (`LuaTypesVisitor.kt:676-677`) and early-returns when the projection is null. At that
  instant `keys.node.write == Undefined`, so `elementNodes[keys[1]]` is never created; because
  `buildSnapshot` is a single traversal + `checkTypes()` with **no** second pass
  (`LuaTypesVisitor.kt:865-871`), the later seed never revives it. Left unfixed this silently
  breaks the headline TC 2 (`keys[1] → string`), the `keys[1]:` half of TC 9, and human
  scenario 1.1 — precisely the REDIS-05 TC-STUB-1 re-enablement that motivates the feature.
- **Likelihood**: certain (structural, not probabilistic) without the mitigation.
- **Mitigation**: design §3.4 makes the subscript element type **lazy** — `seedSubscriptElement`
  registers a `LazyValueElement` whose `write` defers `arrayElementType(receiverNode.write)` to
  read time. `VariableElement.write` is already a lazy getter (`LuaTypeNodes.kt:71`), and the
  snapshot is read only after the full traversal + `checkTypes()`, so by read time the seed edge
  exists and `keys.node.write == Array(String)` → the projection yields `String`. This removes
  the ordering dependency entirely (no re-visit / second pass needed).
- **Regression coverage**: the blast radius (ALL array subscripts) is pinned by the existing
  `ArraySubscriptTypeTest` (`src/test/kotlin/net/internetisalie/lunar/lang/types/ArraySubscriptTypeTest.kt`)
  — `testArraySubscriptInfersElementType_TC_IDX_1` (array → element), `testNonArrayBracketStaysUndefined_TC_IDX_2`
  (`t[1]` plain table → `undefined`), `testDottedAccessUnchanged_TC_IDX_3`, and
  `testLengthOverArrayHasNoAssignabilityError` must all stay green; TYPE-10 TC 2 is the new
  positive proof of the seeded path.

### Risk 1.3: Mid-visit reference resolution correctness
- **Impact**: `resolveCalleeType()` performs `reference.resolve()` and
  `LuaTypesSnapshot.forFile(declFile)` during graph construction. If the declaration snapshot
  is not yet available or recurses into the current file, it could stall or mis-resolve.
- **Likelihood**: low.
- **Mitigation**: the pattern is copied verbatim from the shipping, live
  `LuaParameterInlayHintsProvider` (`LuaParameterInlayHintsProvider.kt:100,117`), which does
  exactly this resolution against a *declaration-file* snapshot successfully. For a callee in
  the same file, `forFile(call.containingFile)` returns the in-progress/cached snapshot; the
  fallback path only uses declared `LuaFunctionType` param **types**, not the callee's
  fixpoint, so there is no dependence on the current file's not-yet-complete graph
  (mirrors REDIS-04 §3.1b seam-(b) stability).

## Design Gaps

### Gap 2.1: Return-type propagation not included
- **Question**: should the lambda's `return` type also be constrained by the expected
  callback's declared return (`fun(...): R`)?
- **Options / leaning**: leaning **defer**. Parameter typing is the user-visible win (typed
  `keys`/`args`); return-type back-pressure is a separate, lower-value change with its own
  blast radius (it would add a `use` constraint on the lambda's return nodes and could emit
  new "return type mismatch" diagnostics).
- **Resolved by**: scoped OUT in requirements ("Out of Scope"); tracked as future work below.
  Not required for TYPE-10 to clear the bar.

### Gap 2.2: Method-chain and `@overload`-table callback slots
- **Question**: `a:m1(fn):m2(fn)` (2nd chain segment) and the table-form
  `register_function({ callback = function… })` overload.
- **Options / leaning**: defer. The engine models only
  `nameAndArgsList.firstOrNull()` (per `.agents/AGENTS.md`), and the `@overload` table form is
  a structural argument, not a positional lambda slot.
- **Resolved by**: scoped OUT in requirements; future work below.

### Gap 2.3: `table.sort` comparator element-type narrowing
- **Question**: the roadmap brief lists "table.sort comparator" as a positive case, but the
  bundled stub declares `comp: fun(a: any, b: any): boolean`, so TYPE-10 propagates `any`,
  not the array element type of the sorted list.
- **Options / leaning**: TYPE-10 correctly propagates whatever the stub declares (`any`);
  narrowing `a`/`b` to the first argument's `Array(T)` element type requires a generic stub
  (`fun(a: T, b: T)`) plus flowing `T` from `arg0`'s `Array(T)` — a generics-instantiation
  change beyond expected-type propagation.
- **Resolved by**: requirements Out-of-Scope note; TC 3 asserts `any` (the honest ground
  truth), not the element type. Future work below.

### Gap 2.5: inline `---@param` on a *passed lambda* does not attach (discovered in implementation)
- **Question**: TYPE-10-03 / design N2 / original TC 5 assume an inline
  `run(---@param x number\nfunction(x) end)` annotation is injected onto the lambda parameter,
  so the precedence gate skips the expected-type seed and `x` infers `number`.
- **Ground truth (empirical, TYPE-10 impl)**: an inline `---@param` placed inside the argument
  list parents to the `LuaArgs` node — it is **not** a `prevSibling` of the `LuaFuncDef`. The
  visitor's `getAllCatsComments(funcDef)` walks the funcDef's prevSiblings, so the inline comment
  is never injected (`funcDef.prevSibling == null`; the comment's parent is `ARGS`). This is a
  **pre-existing** comment-attachment limitation in the PSI/visitor model, independent of
  TYPE-10 — no passed-lambda form attaches an inline annotation today.
- **Impact on TYPE-10-03**: the precedence gate `isAlreadyAnnotated` (`paramNode.write !=
  Undefined`) is implemented and **correct** — it defends any annotation that *does* attach — but
  because inline lambda-arg annotations don't attach in the baseline, the gate is effectively a
  defensive no-op for passed lambdas in practice. TC 5 was retargeted to assert the honest
  baseline (`testInlineParamDoesNotAttach_TC5`: the seed applies, `x` → `string`, no exception),
  and the gate remains as cheap correctness insurance.
- **Resolved by**: kept the gate (correct); documented the attachment limitation here. Making
  inline lambda-arg `---@param` attach requires extending comment-ownership to the `LuaArgs`
  seam — a broad, function-wide change out of TYPE-10 scope. Future work below.

## Technical Debt & Future Work
- **TBD: Inline lambda-arg `---@param` attachment** — teach the visitor's comment resolution to
  pick up a `---@param` parented to `LuaArgs` before a passed `LuaFuncDef`, so TYPE-10-03's
  precedence is exercisable for inline-annotated passed lambdas (Gap 2.5).
- **TBD: Return-type back-pressure** — constrain the lambda's return from `fun(...): R`
  (Gap 2.1).
- **TBD: Generic comparator stubs** — declare `table.sort`'s `comp` as `fun(a: T, b: T)` and
  flow `T` from the sorted array so `a`/`b` narrow to the element type (Gap 2.3).
- **TBD: Chain / overload-table callback slots** (Gap 2.2).
- **Roadmap note**: roadmap `TYPE-10` is already `planned`; this planning pass clears the bar.
  No roadmap edit is made here (the parent supervisor owns the roadmap row). Priority stays
  **C (Could)** per the roadmap.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TYPE-10-DR-01 | Confirm `resolveCalleeType()` resolves `redis.register_function` / `table.sort` stubs to a `LuaFunctionType` whose `params[K]` is a callback `fun(...)`, in a throwaway `IndexedBasePlatformTestCase` (Phase 1 exit criterion) | Risk 1.3, TYPE-10-02 | done — `ExpectedCallbackResolverTest` (commit `0e4483ed`) resolves both stubs to callback `fun(...)` param types |
| TYPE-10-DR-02 | Confirm an un-annotated lambda param node's `write == Undefined` and an annotated one's is non-`Undefined` at propagation time (validates the `isAlreadyAnnotated` gate) | TYPE-10-03 | done — `isAlreadyAnnotated` (`LuaTypesVisitor`, commit `38164600`) + `LambdaParamInferenceTest` TC5/TC6; NB Gap 2.5 documents where an inline `---@param` never attaches |
| TYPE-10-DR-03 | Confirm the §3.4 lazy `seedSubscriptElement` rewrite makes `keys[1]` resolve to `string` when `keys`'s param node is seeded *after* the subscript is visited (the ordering hazard), while `ArraySubscriptTypeTest`'s non-array/dotted cases stay `undefined`/unchanged | Risk 1.2b, TYPE-10-01, TC 2 | done — lazy `seedSubscriptElement` (commit `38164600`); `LambdaParamInferenceTest` TC2 green + `ArraySubscriptTypeTest` all 4 cases green in the full gate |

## Regression contract (REDIS-04 §3.1c-style — required, not optional)
The shared-engine seam is `LuaTypesVisitor.visitFuncCall` plus the reused resolution helpers.
This change is additive (new `value → lambda-paramNode` edges only). The following must hold:

**Invariants (assert as tests):**
1. A non-lambda argument slot is unchanged (TC 7).
2. A lambda whose matching parameter is untyped (`params[K]` not a `fun(...)`) stays
   `Undefined`/`any` — no spurious narrowing (TC 6, and `pcall` N3).
3. A lambda parameter with a direct `---@param` keeps its declared type (TC 5).
4. A non-lambda, non-function argument (`run(42)`) does not throw and is unchanged (TC 8).

**Engine files in scope (review any diff to these):**
`LuaTypesVisitor.kt` (incl. the §3.4 `seedSubscriptElement` lazy rewrite), `LuaTypeGraph.kt`
(new `lazyValue` factory), `LuaTypeNodes.kt` (new `LazyValueElement`), `LuaGraphType.kt`,
`LuaTypes.kt`, `LuaTypeGraphBridge.kt`, `LuaExpectedCallbackResolver.kt` (new).

**Full type-engine suite must stay green (full-suite run, NOT isolated `--tests`):**
`TestLuaTypeEnginePhase1`, `TestFlowSensitiveType`, `CrossFileInferenceTest`,
`UnionAndGenericTest`, `PrimitiveTypeCompatibilityTest`, `LuaUnionDistributionTest`,
`TableTypeTest`, `QualifiedMemberResolutionTest`, `ReceiverAwareMemberResolutionTest`,
`MemberFieldIndexTest`, `FunctionSignatureMatchingTest`, `LuaMethodMembersTest`,
`MultiReturnValueTest`, `ArraySubscriptTypeTest` (**the §3.4 lazy-subscript guard — must stay
green**), `StubGlobalSeedTypeTest`, `LuaRequireTypeFlowTest`, `TestTypeParser`.

**Downstream consumers must be unregressed (build gate covers their suites):**
`LuaParameterInlayHintsProvider`, `LuaMethodChainInlayHintProvider`,
`LuaInferredTypeAnnotator`, `LuaCompletionContributor`, the Redis command inspection under
`analysis/redis/`, and the assignability/return-type inspections
(`LuaTypeAssignabilityInspection`, `LuaReturnTypeMismatchInspection`).

## Test Case Gaps
- Live (IDE) verification of the completion/inlay surface (TC 9) is covered by
  [human-verification-checklists.md](human-verification-checklists.md); the automated test is
  a `BasePlatformTestCase` proxy, not the real IDE surface.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
