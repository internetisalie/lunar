---
id: "MAINT-54-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-54"
folders:
  - "[[features/maint/54-type-false-negative-audit/requirements|requirements]]"
---

# MAINT-54: Implementation Plan

One PR. Every Gradle run goes through the builder with `--rerun --no-build-cache`. Nothing
under `src/main` changes; a diff there fails the phase.

### Phase 1: Harness [Must]
- **Tasks**:
  - [ ] Create `AuditModel.kt`, `AuditKinds`, `AuditSiteFinder`, `AuditSelection`, `AuditInjector`,
        `AuditOutput` in `src/test/kotlin/net/internetisalie/lunar/corpus/audit/` — design §2, §3.1–§3.4, §4.
  - [ ] Create `LuaTypeFalseNegativeAuditTest` — design §3.5.
  - [ ] Add the `withTypeAudit` filter to `build.gradle.kts` — design §5.
  - [ ] Create `TypeAuditHarnessTest` (TC-2 to TC-19) and the golden `tooling/corpus/testdata/type_audit_sample.json` — design §4.
  - [ ] Create `tooling/corpus/type_audit_summary.py` and `test_type_audit_summary.py` (TC-20) — design §3.6.
- **Exit criteria**: `run test` green with `TypeAuditHarnessTest` present; TC-1's first two runs hold;
  `python3 -m unittest discover -s tooling/corpus -p 'test_*.py'` green; `run ktlintCheck` green; each
  TC's named mutation applied by hand turns it red, then reverted; DR-02 run (lazynvim carries the cost) and its row filled in
  (with `CAP` or the NFR changed as that row says).

### Phase 2: Measure and report [Must]
- **Tasks**:
  - [ ] Run the design §5 command (every member method of the class); copy the output back as design §5 says; run
        `python3 tooling/corpus/type_audit_summary.py build/type-audit`.
  - [ ] Create `audit-results.md` (`type: "results"`, `parent_id: "MAINT-54"`): run command, commit SHA,
        the summary output verbatim, the verdict, and the cell → BUG table.
  - [ ] For every cell with `silent ≥ 1`, mint a BUG (`loom-cli id mint BUG`), write it with the
        `report-bug` skill citing the cell and two of its sites, and add a roadmap row. One BUG may cover
        several cells when the sites show one cause; the results table lists every cell either way.
  - [ ] Replace the survey's Pass 3 bullet "A false-negative audit of declared-demand coverage …" with
        one sentence giving the verdict and a link to `audit-results.md`.
  - [ ] Set this feature `done`; delete the MAINT-54 roadmap row.
- **Exit criteria**: TC-1's third run and TC-21 hold; `scripts/lint_docs.py docs` and
  `scripts/lint_planning.py` report 0 errors.

## Requirement → Phase Coverage
Phase 1 delivers MAINT-54-01 to -06, -09 and -10; Phase 2 delivers -07 and -08 (design §6).
