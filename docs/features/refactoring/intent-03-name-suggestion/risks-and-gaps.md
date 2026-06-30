---
id: INTENT-03-RISKS
title: Name Suggestion Risks & Gaps
type: risk
parent_id: INTENT-03
---

# INTENT-03: Risks & Gaps

## Critical Risks

### Risk 1.1: Prior-art duplication with `LuaIntroduceVariableHandler`
- **Impact**: `LuaIntroduceVariableHandler` already has its own private name heuristic
  (`baseNameFor`/`calleeName`/`propertyName` at
  `src/main/kotlin/net/internetisalie/lunar/refactoring/LuaIntroduceVariableHandler.kt:144-158`)
  and does NOT call any `NameSuggestionProvider`. Adding a provider naively re-implements that
  logic; the Rename path and the Introduce Variable path then drift (e.g. `getUser` stripped in
  one, not the other), and a future edit fixes only one.
- **Likelihood**: high (it is the default outcome if the skeleton is implemented literally).
- **Mitigation**: extract one `LuaNameDeriver.baseName` (design.md §5) consumed by BOTH the
  provider and the handler's `baseNameFor`. The handler keeps `uniquify` for collisions; the
  provider keeps none. Resolved/validated by INTENT-03-00-DR-01.

### Risk 1.2: Method-call callee parsing (`obj:getName()`)
- **Impact**: the existing `calleeName` (`:150-153`) only inspects `varOrExp`, so it misses
  method calls — the method name lives under `LuaNameAndArgs.getMethodExpr().getNameRef()`
  (`src/main/gen/.../psi/LuaNameAndArgs.java`, `LuaMethodExpr.java:8-12`). A derivation that
  copies `calleeName` verbatim yields the receiver name (`obj`) or nothing for `obj:getName()`.
- **Likelihood**: high.
- **Mitigation**: `LuaNameDeriver` checks `methodExpr` FIRST (design.md §3.1), then falls back
  to the last `LuaNameRef` under `varOrExp`. Covered by `LuaNameDeriverTest` (`obj:getName()` →
  `name`) and a new Introduce Variable method-call test (implementation-plan.md Phase 3).

### Risk 1.3: Reserved-name / collision output
- **Impact**: a derived name could be a Lua keyword (e.g. RHS `end()` → `end`) or collide with
  an in-scope name, producing an invalid or shadowing suggestion.
- **Likelihood**: low.
- **Mitigation**: for Rename, the **platform** validates/uniquifies suggestions. For Introduce
  Variable, the handler's `uniquify` (`:160-170`) already resolves collisions; keyword
  avoidance is a small follow-up guard in `LuaNameDeriver` (append nothing — let `uniquify`
  numeric-suffix, and skip stripping if the result is a keyword). Tracked as TBD below; not a
  blocker for the Must scope (the prefix algorithm never produces a keyword from a typical
  `getX`).

### Risk 1.4: Behavior change to existing Introduce Variable output
- **Impact**: routing the handler through `LuaNameDeriver` changes `getUser()` from
  `local getUser = ...` to `local user = ...`. If any existing test asserts the old name it
  breaks.
- **Likelihood**: low — current tests use non-prefixed names (`compute`, `result`); only the
  intended new behavior changes.
- **Mitigation**: audited `LuaIntroduceVariableTest` — `testCalleeNameSuggestion` uses
  `compute` (unaffected). Add explicit prefix-strip test rather than relying on incidental
  coverage.

## Design Gaps

### Gap 2.1: Rename `element` → RHS expression walk
- **Question**: when invoked from the **Rename** popup, what exact PSI shape is `element`
  (the rename target) and how do we reach the initializer `LuaExpr`? The Introduce Variable
  path hands the RHS expr straight to `LuaNameDeriver`, but the Rename path passes a name/
  declaration element.
- **Options / leaning**: lean toward: if `element`/`nameSuggestionContext` is a `LuaExpr`,
  use it; else find the enclosing local var decl and take its initializer. Confirm the exact
  decl/name PSI type and accessor.
- **Resolved by**: INTENT-03-00-DR-02; fold the confirmed walk into design.md §4.

## Technical Debt & Future Work
- **TBD: Multiple candidates** — provider currently emits one base candidate; offering both
  stripped and un-stripped (`user` AND `getUser`) is deferred to keep the popup focused.
- **TBD: Keyword guard in `LuaNameDeriver`** — explicit Lua-keyword avoidance (Risk 1.3) if
  real cases surface.
- **TBD: Type-informed names** — using inferred return type (the Lunar type engine) to suggest
  e.g. `cfg` for a `Config`-typed RHS is out of scope.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| INTENT-03-00-DR-01 | Confirm `LuaIntroduceVariableHandler` does NOT consult any `NameSuggestionProvider` and that routing `baseNameFor` through a shared `LuaNameDeriver` keeps `LuaIntroduceVariableTest` green (run the existing suite after the refactor). Confirms EXTEND-not-duplicate. | Risk 1.1 | done |
| INTENT-03-00-DR-02 | In a scratch test, invoke Rename on `local x = getUser()` and capture the `element`/`nameSuggestionContext` PSI types passed to `getSuggestedNames`; pin the walk to the initializer `LuaExpr`. | Gap 2.1 | done |
| INTENT-03-00-DR-03 | Verify `LuaNameAndArgs.getMethodExpr()`/`LuaMethodExpr.getNameRef()` yields `getName` for `obj:getName()` in a fixture (PSI traversal spike). | Risk 1.2 | done |

> **DR-02 implementation note (honest scope):** the rename element→RHS walk is implemented and
> unit-tested (`LuaNameSuggestionProviderTest.testRenameElementShape`): when the element resolves
> to a `LuaAttName` of an enclosing `LuaLocalVarDecl`, the provider derives from that decl's first
> initializer expression; otherwise it derives from the element-as-`LuaExpr`. This is validated by
> directly invoking `getSuggestedNames` with the declared-name `LuaNameRef`. Driving the live
> platform **Rename UI** end-to-end (to confirm the exact `element`/`nameSuggestionContext` the
> platform passes for non-local-decl rename sites) remains documented **future work**.

## Test Case Gaps
- No test yet for keyword-producing RHS (deferred with the keyword guard, Risk 1.3 / TBD).
- No test for the Rename popup surface specifically (covered indirectly via direct
  `getSuggestedNames` calls; full Rename-UI driving deferred unless DR-02 reveals a gap).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Implementation Plan: [implementation-plan.md](implementation-plan.md)
</content>
