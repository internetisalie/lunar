---
id: "MAINT-29-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-29"
folders:
  - "[[features/maint/29-controlflow-inspections/requirements|requirements]]"
---

# MAINT-29: Risks & Gaps

## Critical Risks

### Risk 1.1: CFG edge rewrite regresses `LuaUnreachableCodeInspection`
- **Impact**: `LuaUnreachableCodeInspection` is the **sole** CFG consumer (verified:
  `grep -rln "LuaControlFlow"` → only `LuaControlFlowBuilder.kt`, `LuaControlFlow.kt`,
  `ControlFlowCache.kt`, `LuaUnreachableCodeInspection.kt`). Any `prevInstruction`/pending/label
  change that mis-draws an edge flips a reachable statement to unreachable (or vice-versa) and is
  user-visible as a wrong/missing warning.
- **Likelihood**: medium
- **Mitigation**: `LuaControlFlowTest` + `LuaUnreachableCodeInspectionTest` are the regression
  bar and must stay green; add the four new CFG TCs (§plan) before touching edges. Change each
  §3.3/§3.4/§3.5 mechanic in its own commit so a regression bisects cleanly.

### Risk 1.2: `#33` condition instructions change unrelated inspection behavior
- **Impact**: emitting new READ `LuaReadWriteInstruction`s for condition names could, in
  principle, alter a consumer that reads the instruction list.
- **Likelihood**: low
- **Mitigation** / **blast-radius statement**: `LuaUnusedLocalInspection` does **not** consume the
  CFG — it resolves `LuaNameReference`s directly (`LuaUnusedLocalInspection.kt:129-133`; its own
  doc comment, lines 26-29, explicitly says a CFG "would miss captures"). So #33 does **not**
  affect unused-local. The only CFG consumer, `LuaUnreachableCodeInspection`, keys reachability on
  the presence of *any* reachable instruction per statement
  (`LuaUnreachableCodeInspection.kt:91-99`) — extra READ instructions inside an already-reachable
  condition statement do not change its reachability verdict. Net blast radius: contained to the
  CFG builder + the unreachable-code consumer, both under test.

## Design Gaps

### Gap 2.1: `LuaGraphType.Table` metamethod carriage (#68)
- **Question**: does `LuaGraphType.Table` carry `__concat` in a `fields` map for a `@class` that
  declares the metamethod, or does the metamethod live on a materialized class resolved via
  `LuaTypeManager`?
- **Options / leaning**: leaning `type.localMembers.containsKey("__concat")` (`LuaGraphType.kt:39`; there is NO `fields` accessor) vs `resolveMember("__concat") != null` on the materialized class type (`LuaComplexTypes.kt:105`) — DR-01 picks by evidence (design §3.8), consistent
  with the type-engine notes in `.agents/AGENTS.md` on `materializeClass`/member `fields`. Fallback:
  resolve the class name via `LuaTypeManager.resolveType(className)` and check its members.
- **Resolved by**: DR-01 — fold the confirmed accessor back into design §3.8 before Phase 4.

## Technical Debt & Future Work
- **TBD: split-based make-local (#9)** — a future intention could split multi-target assignments
  into per-target `local`s with RHS-order-preserving temp spilling. Deliberately out of scope
  (design §9 rationale); this feature only *withholds* the unsafe fix.
- **TBD: `while true` / `error()` heuristics** — the unreachable-code inspection is a pure CFG
  consumer and already declares these out of scope
  (`LuaUnreachableCodeInspection.kt:26-27`); not touched here.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-00-DR-01 | Read `lang/psi/types/LuaGraphType.kt` `Table` and confirm the `__concat` field accessor (fields map vs. materialized-class member lookup); pin the exact expression in design §3.8 | Gap 2.1 (#68) | done — no `fields` accessor; `Table` carries `localMembers` (+ supertypes via `getMembers()`). Fix uses `type.getMembers().containsKey("__concat")` |
| MAINT-00-DR-02 | Confirm `myFixture.findSingleIntention`/`launchAction`/`checkResult` semantics for the `ReplaceIntegerDivisionFix` (registered via `IntentionWrapper` at `LuaLanguageLevelInspection.kt:141`; the intention text `"Replace // with / and math.floor()"` survives the wrapper, so it surfaces for `findSingleIntention`) | Phase 1 test wiring | done — TC-01/02 pass via `findSingleIntention` + `launchAction` + `checkResult` |

## Test Case Gaps
- No existing test asserts the **sibling `::continue::` cross-wire** (#32c) — added as TC-06.
- No existing test asserts a **condition-name READ instruction** (#33) — added as TC-07.
- No existing test asserts make-local **withheld** on a multi-target assignment (#9) — added as
  TC-04.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
