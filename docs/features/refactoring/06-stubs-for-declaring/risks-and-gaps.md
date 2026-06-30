---
id: REFACT-06-RISKS
title: "Risks & Gaps"
type: risk
parent_id: REFACT-06
priority: medium
folders:
  - "[[features/refactoring/06-stubs-for-declaring/requirements|requirements]]"
---

# REFACT-06: Risks & Gaps

Surface what could go wrong and what's undecided before building. Each risk has a mitigation;
each unknown a de-risking task run early. All design gaps below are resolved and folded into
`design.md` (its Open Questions section is empty).

## Critical Risks

### Risk 1.1: Undeclared-ness drifts from the inspection
- **Impact**: If the intentions re-implement "is this name undeclared?" independently, they can
  offer "Create function" on a name the inspection considers a valid global (or vice-versa),
  confusing users and producing dead declarations.
- **Likelihood**: medium
- **Mitigation**: Extract the inspection's resolve + `isExemptGlobal` logic
  (`LuaUndeclaredVariableInspection.kt:49–72`) into a shared `LuaUndeclaredNames.isUnresolvedNonGlobal`
  and have both the inspection and both intentions call it (design §3). Parity is locked by keeping
  the existing inspection test green (Phase 1). Resolved by **DR-01**.

### Risk 1.2: `local ` + `assignment.text` may not re-parse to a valid local decl
- **Impact**: For create-local, prepending `local ` to the assignment text and re-parsing could
  fail to yield a `LuaStatement` (or yield a malformed one) for unexpected LHS shapes, breaking the
  replace.
- **Likelihood**: low (gate restricts to a single simple-name target)
- **Mitigation**: The `isAvailable` write-target gate guarantees a single `LuaVar` with no
  `varSuffix` in a `LuaVarList`, so the text is always `name = exprList` → `local name = exprList`,
  a valid local declaration. DR-02 confirms the re-parse against `x = 1` and a multi-value RHS.

### Risk 1.3: Generated `arg1..argN` names collide with call arguments / outer scope
- **Impact**: `myFunc(arg1)` (a call literally passing a variable named `arg1`) would generate
  `local function myFunc(arg1)` whose param shadows the caller's `arg1` — harmless but surprising;
  worse if a future enhancement tried to wire args by name.
- **Likelihood**: low
- **Mitigation**: Params are positional placeholders only (no name wiring), so shadowing is inert.
  Out of scope for the stub; deferred (see Technical Debt). If desired later, uniquify against
  in-scope `LuaNameRef`s as `LuaIntroduceVariableHandler.uniquify` does
  (`LuaIntroduceVariableHandler.kt:160`).

### Risk 1.4: Call nested inside an expression has no clean top-level anchor
- **Impact**: `local v = myFunc(1)` — the call is inside a `local` decl; inserting the stub before
  the enclosing statement is still correct, but a call inside a multiline expression or a table
  constructor could anchor at a surprising statement.
- **Likelihood**: low
- **Mitigation**: Anchor on the nearest `LuaStatement` whose parent is a `LuaBlock` (design §5.3),
  the same robust pattern introduce-variable uses (`LuaIntroduceVariableHandler.kt:72`). The stub is
  always inserted at statement granularity, never mid-expression. Covered by an extra test
  (`local v = myFunc(1)` inserts the stub above the `local v` line).

### Risk 1.5: Offering create-local on a read
- **Impact**: Offering "Create local 'y'" on `print(y)` would insert `local y` with no initializer
  context, rarely the user's intent and overlapping confusingly with the inspection's read highlight.
- **Likelihood**: medium (without the gate)
- **Mitigation**: The write-target gate (design §4.1, mirroring the inspection's `isSimpleWriteTarget`
  at `:94`) makes 06-01 unavailable on reads (TC2). Reads remain the inspection's domain.

## Design Gaps

### Gap 2.1: Where the shared undeclared-ness helper lives
- **Question**: Put `LuaUndeclaredNames` in `analysis/inspections` (next to its origin) or
  `lang/insight` (next to the intentions)?
- **Options / leaning**: Lean `analysis/inspections` since the logic originates there and the
  inspection is the primary owner; intentions import it.
- **Resolved by**: DR-01 (folded into design §3 — `analysis.inspections.LuaUndeclaredNames`).

### Gap 2.2: Stateless `getText()` vs cached name
- **Question**: Recompute the name in `getText()` from the caret, or cache it from `isAvailable`?
- **Options / leaning**: Recompute (stateless, matches `LuaGenerateDocIntention`). Resolved in
  design §1.

## Technical Debt & Future Work
- **TBD: Param-name inference** — naming generated params from argument expressions / inferred
  types (e.g. `name`, `count`) instead of `arg1..argN`. Deferred; ties into the type engine.
- **TBD: Create global / create field** — analogous create-from-usage for `a.b = …` member writes
  and global declarations. Out of scope for REFACT-06.
- **TBD: Inline-rename of the new function name** after insertion (introduce-variable does this via
  `TemplateBuilderImpl`, `LuaIntroduceVariableHandler.kt:129`). Deferred; the stub is committed as-is.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| REFACT-06-00-DR-01 | Extract `LuaUndeclaredVariableInspection`'s resolve + `isExemptGlobal` (`:49–72`) into a shared `LuaUndeclaredNames.isUnresolvedNonGlobal`; confirm the existing inspection test stays green so intentions and inspection share one undeclared verdict. | Risk 1.1, Gap 2.1 | done |
| REFACT-06-00-DR-02 | Prototype the create-local re-parse: `LuaElementFactory.createFile(project, "local " + assignment.text)` then `findChildOfType(LuaStatement)`; verify it yields a valid `local x = 1` and a multi-value RHS (`x = f()`), and that `assignment.replace(decl)` produces clean text. | Risk 1.2 | done |
| REFACT-06-00-DR-03 | Confirm the arg-count chain on real PSI: `call.nameAndArgsList.firstOrNull()?.args?.exprList?.exprList?.size` returns 2 for `myFunc(1,2)`, 0 for `f()`, and that the single-name-callee gate rejects `obj.method(1)`. | Risk 1.4, TC4/TC5/TC7 | done |

## Test Case Gaps
- Add a positive variant for a **call nested in an expression** (`local v = myFunc(1)` →
  stub inserted above the `local v` line) covering Risk 1.4 — not yet a numbered TC in
  requirements.md; add during Phase 3.
- Add an inspection-parity assertion (DR-01): a name the inspection treats as a known global
  (e.g. `print`) yields neither create-from-usage intention.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
