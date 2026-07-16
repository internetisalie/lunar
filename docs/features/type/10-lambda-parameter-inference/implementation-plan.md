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
- **Goal**: seed un-annotated lambda parameters from the expected callback type.
- **Tasks**:
  - [ ] Add private `propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)`,
        `seedLambdaParams(lambda, expected)`, `isAlreadyAnnotated(paramNode)` to
        `LuaTypesVisitor` (`LuaTypesVisitor.kt`) — realizes design §3.1. Each ≤30 logic lines,
        ≤3 args (exactly 3 on `propagateExpectedLambdaParams`; `LuaFuncCall` is not a
        `Project`/`Disposable`, so it counts — stays at the cap).
  - [ ] Call `propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)` in `visitFuncCall`
        after `argNodes` is built and before
        `graph.addEdge(calleeNode, graph.use(o, callDemand))` (`LuaTypesVisitor.kt:607`) —
        realizes design §2.1.
  - [ ] Implement the `isAlreadyAnnotated` precedence gate via `paramNode.write !=
        LuaGraphType.Undefined` — realizes design §3.1 step 4 / requirements TYPE-10-03.
  - [ ] Method-call `selfOffset` handling (design §6) — pass 1 when
        `nameAndArgs.methodExpr != null`, else 0.
- **Exit criteria**: TC 1, 2, 3, 4, 5, 6, 7, 8 green as engine-level
  `IndexedBasePlatformTestCase` tests. Build green.

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
        failures — realizes requirements TYPE-10-06.
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
- [ ] Add `LambdaParamInferenceTest` (TC 1–8) — covers the positive + negative engine cases.
- [ ] Add a completion/inlay surface test — covers TC 9.
- [ ] Full-suite regression run (not isolated `--tests`) — covers TC 10 / TYPE-10-06.
- [ ] Re-run REDIS-05 TC-STUB-1 to confirm Gap 2.4 is retired (design §5 Example 1).
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Expected-callback resolver | planned | Must |
| Phase 2: Propagation in visitFuncCall | planned | Must |
| Phase 3: Regression contract + surfacing | planned | Must |
