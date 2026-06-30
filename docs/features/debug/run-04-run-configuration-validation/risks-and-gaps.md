---
id: "RUN-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "RUN-04"
folders:
  - "[[features/debug/run-04-run-configuration-validation/requirements|requirements]]"
---

# RUN-04: Risks & Gaps

<!-- Documents real gaps found while reverse-engineering the shipped implementation. The feature
     code is complete; these are honesty notes and de-risking tasks, primarily around the ABSENCE
     of test coverage and the divergence from the original brief. -->

## Critical Risks

### Risk 1.1: No automated test coverage for validation
- **Impact**: RUN-04's validation logic in `LuaRunConfiguration.startProcess()` is **untested**.
  A regression (e.g. reordering the guards, changing a message, or dropping the debug-only check)
  would not be caught by CI. The only test on this class,
  `TestLuaRunConfiguration.testOptionsPersistence` (`src/test/.../run/TestLuaRunConfiguration.kt:9`),
  exercises option getters/setters, not `startProcess()`.
- **Likelihood**: medium
- **Mitigation**: Add the four `startProcess()` tests listed in the implementation plan's
  Verification Tasks (DR-01). The test config already has a precedent worth mirroring:
  `LuaTestRunConfigurationTest.testValidationFailsWhenTargetEmpty`
  (`src/test/.../run/test/LuaTestRunConfigurationTest.kt:123`).

## Design Gaps

### Gap 2.1: Brief vs. reality — location and shape of validation
- **Question**: The feature brief said validation lives in
  `LuaRunConfiguration.checkConfiguration()` and checks that "the file exists".
- **Findings (grounded)**:
  - `LuaRunConfiguration` has **no** `checkConfiguration()` override (`grep checkConfiguration src`
    matches only `run/test/LuaTestRunConfiguration.kt:247` and its test). Validation is in
    `getState()` → `startProcess()` via `ExecutionException`.
  - There is **no script-file existence check**. An empty script name is intentionally treated as
    "launch interactive REPL" (`lua -v -i`, `run/LuaRunConfiguration.kt:237-239`); a non-existent
    non-empty path is passed straight to the interpreter, which fails at runtime, not at validation.
- **Options / leaning**: Documented the shipped behavior as the spec. Adding file-existence
  validation and/or an edit-time `checkConfiguration()` is a future enhancement, not a current bug.
- **Resolved by**: DR-02 (decision recorded here; design.md §9 captures the alternative).

## Technical Debt & Future Work
- **TBD: Edit-time `checkConfiguration()` for the main run config** — surface interpreter/script
  problems as a red banner in the Run/Debug Configurations dialog (platform-preferred UX), mirroring
  `LuaTestRunConfiguration.checkConfiguration()`. Deferred; out of RUN-04 scope.
- **TBD: Script-file existence validation** — verify a non-empty `scriptName` resolves to an
  existing file before launch, with a clear message. Deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Add `BasePlatformTestCase` tests invoking `getState(executor, env).startProcess()` for the no-interpreter and unresolvable-interpreter paths, asserting the exact `ExecutionException` messages | Risk 1.1 | todo |
| DR-02 | Confirm with maintainers whether file-existence + edit-time validation is in-scope for RUN-04 or a follow-up feature | Gap 2.1 | done (documented as future work) |

## Test Case Gaps
- TC #1–#4 from [requirements.md](requirements.md) have **no** corresponding automated test yet
  (tracked by DR-01).
- TC #5 (RUN-04-06 run-error dialog UX) has no human-verification checklist authored; would need a
  VNC pass per the `verify-in-ide` skill.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
</content>
