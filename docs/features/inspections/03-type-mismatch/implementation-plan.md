---
id: "INSP-03-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "INSP-03"
folders:
  - "[[features/inspections/03-type-mismatch/requirements|requirements]]"
---

# INSP-03: Implementation Plan

> **Precondition met.** design.md has cleared the bar (Open Questions empty; every requirement
> mapped in §8). The verdict (design §1) is that INSP-03 is **already implemented** by
> `LuaTypeAssignabilityInspection` + `LuaReturnTypeMismatchInspection`. This plan therefore has
> **no production-code phase** — it closes the genuine delta: two regression tests for the
> currently-unproven cases, then a doc/status reconciliation. Build stays green throughout.

## Phases

### Phase 1: Lock down the unproven Must cases [Must]
- **Goal**: prove, through the real inspection flow, the two scenarios that currently lack a test
  (TC1 scalar mismatch is reported; TC2 union member-match is NOT reported).
- **Tasks**:
  - [ ] Add `testScalarTypeMismatchReported` to
        `src/test/kotlin/net/internetisalie/lunar/analysis/LuaTypeAssignabilityInspectionTest.kt`
        — realizes design §3.3(1). Input `---@type string` / `local x = 42`; assert a description
        contains `not assignable` and mentions both `number` and `string`. Reuse the existing
        `descriptions(text)` helper.
  - [ ] Add `testUnionMemberMatchNotReported` to the same test class — realizes design §3.3(2).
        Input `---@type string|number` / `local x = 42`; assert **no** description contains
        `not assignable`.
- **Exit criteria**: both new tests pass via `./gradlew test --tests "*LuaTypeAssignabilityInspectionTest*"`;
  covers TC1 and TC2.

### Phase 2: Reconcile docs & status [Must]
- **Goal**: docs reflect the shipped two-inspection reality and the feature rolls up as done.
- **Tasks**:
  - [ ] (Done in this planning pass) requirements.md statuses set to `Full`; design.md records
        the extend-vs-replace verdict; no invented `LuaTypeMismatchInspection`.
  - [ ] After Phase 1 lands, set requirements.md / design.md / this plan / checklist front-matter
        `status: done` and re-run `python3 scripts/gen_status.py` (per the `summary` skill) to
        refresh `docs/status.md`.
- **Exit criteria**: `python3 scripts/lint_docs.py docs` and `python3 scripts/lint_planning.py docs`
  report no new errors for `03-type-mismatch`.

## Requirement → Phase Coverage
| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| INSP-03-01 | M | Already shipped (engine + `LuaTypeAssignabilityInspection`); test in Phase 1 (TC1) |
| INSP-03-02 | M | Already shipped (TYPE-09 union distribution); test in Phase 1 (TC2); fail-path TC3 already covered |
| INSP-03-03 | S | Already shipped + tested (`LuaReturnTypeMismatchInspectionTest`, TC4/TC5) |
| INSP-03-04 | S | Already shipped + tested (`testArityTooFewReported` etc., TC6) |

## Verification Tasks
- [ ] New `testScalarTypeMismatchReported` — covers TC1.
- [ ] New `testUnionMemberMatchNotReported` — covers TC2.
- [ ] Confirm existing `LuaTypeAssignabilityInspectionTest` (TC3, TC6) and
      `LuaReturnTypeMismatchInspectionTest` (TC4, TC5) remain green.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) in a sandbox IDE.

## Task Summary
| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Lock down unproven Must cases | todo | Must |
| Phase 2: Reconcile docs & status | todo | Must |
