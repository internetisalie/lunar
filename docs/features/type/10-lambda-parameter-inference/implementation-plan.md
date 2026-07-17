---
id: "TYPE-10-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-10"
folders:
  - "[[features/type/10-lambda-parameter-inference/requirements|requirements]]"
---

# TYPE-10: Implementation Plan

This is a high-blast-radius change to the shared inference engine
(`LuaTypesVisitor.visitFuncCall`), so the regression contract (Phase 3) is a **required
gate**, not optional. Order: build the resolver in isolation (Phase 1), wire propagation
(Phase 2), then prove no regression + user-visible surfacing (Phase 3).

## Phases

### Phase 1: Expected-callback resolver [Must]
- **Goal**: obtain a callee's declared `LuaFunctionType` and its per-slot callback type,
  for both LuaCATS-local and bundled-stub callees, in isolation from the propagation.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaExpectedCallbackResolver`
        (`LuaExpectedCallbackResolver.kt`, ctor `(call: LuaFuncCall, calleeUnwrapped:
        PsiElement?)`) — realizes design §2.2, §3.2. Implement `resolveCalleeType()` (standard
        + method call; reference-resolution fallback mirroring
        `LuaParameterInlayHintsProvider.getDeclaration`) and
        `expectedCallbackAt(index, calleeType, selfOffset)`.
  - [ ] Port a private `extractFunctionType(type: LuaType): LuaFunctionType?` helper
        (design §3.2; mirror `LuaParameterInlayHintsProvider.kt:147`) — handles
        `LuaFunctionType` and first-`LuaFunctionType`-member of a `LuaUnionType`.
- **Exit criteria**: a focused unit test resolves `redis.register_function`'s
  `params[1]` to `fun(keys: string[], args: string[])` and `table.sort`'s `params[1]` to
  `fun(a: any, b: any): boolean` (feeds TC 1, 3). Build green.

### Phase 2: Propagation in visitFuncCall [Must]
- **Goal**: seed un-annotated lambda parameters from the expected callback type, and make the
  subscript path order-independent so a seeded receiver's `receiver[i]` resolves (design §3.4).
- **Tasks**:
  - [ ] **Lazy subscript seeding (design §3.4) — required for TC 2/9.** Add
        `LuaTypeGraph.lazyValue(element: PsiElement, compute: () -> LuaGraphType): ValueNode`
        (`LuaTypeGraph.kt`, mirrors `value`, `LuaTypeGraph.kt:52-57`) and
        `internal class LazyValueElement(element, compute) : ValueNode` with a computed
        `write` getter (`LuaTypeNodes.kt`, mirrors `ValueElement`, `LuaTypeNodes.kt:54-57`).
        Rewrite `seedSubscriptElement` (`LuaTypesVisitor.kt:674-679`) to register
        `elementNodes[o] = listOf(graph.lazyValue(o) { arrayElementType(receiverNode.write) ?:
        LuaGraphType.Undefined })` — no eager `arrayElementType` call, no early-return on a
        currently-`Undefined` receiver write. This closes the intra-traversal ordering hazard
        (the `keys[1]` subscript is visited during `super.visitFuncCall`, before the seed edge).
  - [ ] Add private `propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)`,
        `seedLambdaParams(lambda, expected)`, `isAlreadyAnnotated(paramNode)` to
        `LuaTypesVisitor` (`LuaTypesVisitor.kt`) — realizes design §3.1. Each ≤30 logic lines,
        ≤3 args (exactly 3 on `propagateExpectedLambdaParams`; `LuaFuncCall` is not a
        `Project`/`Disposable`, so it counts — stays at the cap). Its `argExprs` parameter is
        typed **`List<PsiElement>`** to match the visitor's real local
        (`LuaTypesVisitor.kt:571-576`, whose `args.string` branch is `PsiElement` per
        `LuaArgs.getString()`, `LuaArgs.java:17`); per-slot narrowing is
        `unwrapExpression(argExpr) as? LuaFuncDef` (design §3.1 step 2a).
  - [ ] Call `propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)` in `visitFuncCall`
        after `argNodes` is built and before
        `graph.addEdge(calleeNode, graph.use(o, callDemand))` (`LuaTypesVisitor.kt:607`) —
        realizes design §2.1.
  - [ ] Implement the `isAlreadyAnnotated` precedence gate via `paramNode.write !=
        LuaGraphType.Undefined` — realizes design §3.1 step 4 / requirements TYPE-10-03.
  - [ ] Method-call `selfOffset` handling (design §3.1 step 1 / §6) — pass 1 only when
        `nameAndArgs.methodExpr != null && calleeType.params.firstOrNull()?.name == "self"`
        (the mirrored inlay-provider guard, `LuaParameterInlayHintsProvider.kt:60-63`), else 0.
- **Exit criteria**: TC 1, 2, 3, 4, 5, 6, 7, 8 green as engine-level
  `IndexedBasePlatformTestCase` tests, AND the existing `ArraySubscriptTypeTest` (all 4 cases)
  stays green (guards the §3.4 lazy-subscript blast radius). Build green.

### Phase 3: Regression contract + user-visible surfacing [Must]
- **Goal**: prove no inference regression across the shared seam and its consumers, and
  surface the result through a user-facing feature.
- **Tasks**:
  - [ ] Add `LambdaParamInferenceTest` under
        `src/test/kotlin/net/internetisalie/lunar/lang/types/` (extends
        `IndexedBasePlatformTestCase`, `LuaTypesSnapshot.forFile`) covering TC 1–8, with the
        explicit negative cases (TC 6/7/8) asserting baseline-equivalence.
  - [ ] Add a user-visible surface test (TC 9): a `BasePlatformTestCase` completion test
        (`keys[1]:` offers `string` members) OR an inlay/hint assertion on `keys`.
  - [ ] Run the **full** `.../lang/types/*` suite (not isolated `--tests`) plus consumers per
        the regression contract (see risks-and-gaps §"Regression contract"): confirm 0
        failures — realizes requirements TYPE-10-06. This full suite includes
        `ArraySubscriptTypeTest` (all 4 cases), the guard for the §3.4 lazy-subscript change.
- **Exit criteria**: TC 1–10 green AND the full type-engine suite green on a full-suite run
  AND `run build` (checkStatus/koverVerify/integrationTest) green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TYPE-10-01 | M | Phase 2 |
| TYPE-10-02 | M | Phase 1 |
| TYPE-10-03 | M | Phase 2 |
| TYPE-10-04 | M | Phase 2 |
| TYPE-10-05 | S | Phase 3 |
| TYPE-10-06 | M | Phase 3 |

## Verification Tasks
- [x] Add `LambdaParamInferenceTest` (TC 1–8) — covers the positive + negative engine cases.
- [x] Add a completion/inlay surface test — covers TC 9 (`LambdaParamInferenceInlayTest`, type inlay on the seeded lambda param).
- [x] Full-suite regression run (not isolated `--tests`) — covers TC 10 / TYPE-10-06 (2011 tests / 0 failures, `--rerun-tasks --no-build-cache`).
- [x] Re-run REDIS-05 TC-STUB-1 to confirm Gap 2.4 is retired (`LambdaParamInferenceTest.testRegisterFunctionKeysInfersStringArray_TC1_TC2`; REDIS-05 risks Gap 2.4 marked RETIRED).
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) — live IDE surface (deferred to human verification per risks §Test Case Gaps).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Expected-callback resolver | done | Must |
| Phase 2: Propagation in visitFuncCall | done | Must |
| Phase 3: Regression contract + surfacing | done | Must |
