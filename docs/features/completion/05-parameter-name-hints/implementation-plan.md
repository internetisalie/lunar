---
id: "COMP-05-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "COMP-05"
folders:
  - "[[features/completion/05-parameter-name-hints/requirements|requirements]]"
---

# COMP-05: Implementation Plan

## Phases

### Phase 1: Core Provider [Must] — DONE
- **Goal**: Register a declarative `InlayHintsProvider` that shows parameter
  names at call sites for direct function calls and colon method calls.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.insight.hint.LuaParameterInlayHintsProvider`
    — realizes design §2.1
  - [x] Register in `plugin.xml` at `codeInsight.declarativeInlayProvider` with
    `providerId="lua.parameter.hints"`, `group="PARAMETERS_GROUP"`,
    `enabledByDefault="true"` — realizes design §2.4
  - [x] Add bundle keys `lua.type.hints.parameter.name` / `.desc` to `LuaBundle.properties`
  - [x] Implement `createCollector()` with large-file gating via
    `LuaInlayHintsSettings.largeFileThreshold` — realizes design §3.5
  - [x] Implement `collectParameterHints()`:
    - Callee type resolution via `LuaTypesVisitor.getTypes` +
      `graphTypeToLuaType` + `resolveMember` (design §3.2 paths A, B)
    - Reference resolution fallback with `LuaTypesSnapshot.forFile(declFile)`
      (design §3.2 path C)
    - Colons-call self stripping (design §3.1)
    - Argument enumeration from `LuaArgs` (design §3.3)
    - Single-param gate (`effectiveParams.size <= 1`) (design §4.5, req COMP-05-05)
    - Hint emission via `InlineInlayPosition` + `HintFormat.default` (design §3.4)
- **Exit criteria**: `LuaParameterInlayHintsTest.testBasicParameterHints` passes —
  `move(10, 20)` → `move(posX:10, posY:20)`

### Phase 2: Suppression Heuristics [Must] — DONE
- **Goal**: Avoid redundant/useless hints via short-name, name-matching, and
  single-param suppression.
- **Tasks**:
  - [x] Implement `shouldShowHint(paramName, argExpr)` in
    `LuaTypeInlayHintProvider.Companion` (shared with parameter hints provider)
    — realizes design §2.2
  - [x] Short-name suppression: `paramName.length <= 1 || paramName in {"_", "p"}`
    (req COMP-05-06)
  - [x] Name-matching suppression: if argument is `LuaNameRef` with matching text
    (req COMP-05-04)
  - [x] Unwrapped expression matching: strip parens/whitespace from `LuaExpr`
    arg and re-check for matching `LuaNameRef`
  - [x] Single-param gate moved to this phase (already in core, confirmed)
    (req COMP-05-05)
- **Exit criteria**:
  - `testSuppressionWhenNameMatches` passes — `move(posX, posY)` with params
    named `posX`, `posY` → no hints
  - `testSuppressionForSingleParameter` passes — `log("hello")` with single
    param → no hint

### Phase 3: LuaCATS @param Integration [Must] — DONE
- **Goal**: When a function has `---@param <name>` annotations, use the
  annotated names for hints instead of the signature names.
- **Tasks**:
  - [x] Extend type engine to carry `@param` names into `LuaFunctionType.params`
    (done in the TYPE epic — the type engine already reads `@param` annotations
    when building function types)
  - [x] Confirm that `functionType.params[i].name` reflects the `@param` name
    when present
- **Exit criteria**: `testLuaCatsParameterNames` passes —
  `apply(10, 20)` → `apply(speed:10, force:20)` with `@param speed`, `@param force`

### Phase 4: Colon-Call Refinement & Edge Cases [Must] — DONE
- **Goal**: Correctly handle colon method calls with implicit `self`.
- **Tasks**:
  - [x] Implement `self` stripping algorithm (design §3.1):
    - Named `self` params always dropped
    - Heuristic drop when `params.size > argExprs.size`
  - [x] Verify single-param-after-self case is suppressed
  - [x] Verify multi-param-after-self case shows correct hints
  - [x] Handle string and table constructor args (req COMP-05-09)
- **Exit criteria**:
  - `testColonCallSuppressesSelf` passes — `obj:method(value)` with single
    explicit param → no hint
  - `testMultipleParametersColonCall` passes — `obj:move(10, 20)` →
    `obj:move(posX:10, posY:20)`

### Phase 5: Settings Integration [Should] — DONE
- **Goal**: Integrate with IDE inlay hints settings UI and provide performance
  threshold.
- **Tasks**:
  - [x] Create `LuaInlayHintsSettings` as `@Service(Service.Level.APP)` with
    `largeFileThreshold: Int = 10000`
  - [x] Register in `plugin.xml` as `<applicationService>`
  - [x] Create `LuaInlayHintsCustomSettingsProvider` with threshold field in
    the IDE's Inlay Hints settings panel
  - [x] Register in `plugin.xml` as
    `codeInsight.declarativeInlayProviderCustomSettingsProvider`
  - [x] Wire large-file check into `createCollector()`
- **Exit criteria**:
  - `testLargeFileThreshold` passes — file with lines > threshold → no hints
  - `testParameterHintsEnabled` / `testParameterHintsDisabled` pass —
    toggling provider in settings shows/hides hints

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| COMP-05-01 | M | Phase 1 |
| COMP-05-02 | M | Phase 4 |
| COMP-05-03 | M | Phase 3 |
| COMP-05-04 | M | Phase 2 |
| COMP-05-05 | M | Phase 1, Phase 2 |
| COMP-05-06 | M | Phase 2 |
| COMP-05-07 | S | Phase 5 |
| COMP-05-08 | M | Phase 5 |
| COMP-05-09 | S | Phase 4 |

## Verification Tasks

- [x] `LuaParameterInlayHintsTest.testBasicParameterHints` — covers TC-1 (COMP-05-01)
- [x] `LuaParameterInlayHintsTest.testColonCallSuppressesSelf` — covers TC-2 (COMP-05-02)
- [x] `LuaParameterInlayHintsTest.testMultipleParametersColonCall` — covers TC-3 (COMP-05-02)
- [x] `LuaParameterInlayHintsTest.testLuaCatsParameterNames` — covers TC-4 (COMP-05-03)
- [x] `LuaParameterInlayHintsTest.testSuppressionWhenNameMatches` — covers TC-5 (COMP-05-04)
- [x] `LuaParameterInlayHintsTest.testSuppressionForSingleParameter` — covers TC-6 (COMP-05-05)
- [x] `LuaInlayHintsSettingsTest.testLargeFileThreshold` — covers TC-7 (COMP-05-07)
- [x] `LuaInlayHintsSettingsTest.testParameterHintsEnabled` / `.testParameterHintsDisabled` — covers TC-8 (COMP-05-08)
- [x] `LuaInlayHintsTestCase` infrastructure ensures all three providers coexist without interference

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Core Provider | done | Must |
| Phase 2: Suppression Heuristics | done | Must |
| Phase 3: LuaCATS @param Integration | done | Must |
| Phase 4: Colon-Call Refinement & Edge Cases | done | Must |
| Phase 5: Settings Integration | done | Should |