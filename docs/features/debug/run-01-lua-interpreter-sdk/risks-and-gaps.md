---
id: "RUN-01-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "RUN-01"
folders:
  - "[[features/debug/run-01-lua-interpreter-sdk/requirements|requirements]]"
---

# RUN-01: Risks & Gaps

The RUN-01 production code is implemented and shipping. The gap captured here is **test
coverage**: an audit of `src/test/` (2026-06-21) found that the implementation plan previously
marked verification tasks as complete for tests that do not exist. Only `Banner.create`'s
success path is actually covered.

## Critical Risks

### Risk 1.1: Untested core behavior can regress silently
- **Impact**: Discovery, identification (failure path), version→level mapping, command-line
  construction (`.jar` branch), env-var substitution, Windows `.exe` suffixing, and both
  persistence round-trips have **no automated tests**. A refactor can break any of these
  without a failing build; the feature's "done" status overstates verification.
- **Likelihood**: medium
- **Mitigation**: Land the de-risking tasks DR-01…DR-06 below, which add unit tests for each
  uncovered test case (TC 1–3, 5, 6–16). Until then, treat these requirements as
  code-complete-but-unverified.

## Design Gaps
None. The design is fully specified; the gap is verification, not design.

## Technical Debt & Future Work
- **TBD: Inject the search-path/VFS provider for testable discovery** — `findInterpreters()`
  scans the real filesystem and spawns child processes, so it cannot be unit-tested as written
  without test interpreters on the path. DR-01 must either (a) introduce a seam (inject the
  path list + a directory resolver) or (b) drive discovery against a temp directory containing
  a fake `lua` script, deciding before writing the test.
- **TBD: Manual UI verification record** — the Settings ▸ Languages ▸ Lua add/edit/re-scan/
  delete flow has no recorded manual or `verify-in-ide` run; schedule one and link the evidence.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Add discovery + re-scan-merge tests (replace-by-path / append-new / preserve-user), choosing the test seam noted above | TC 13–16, Risk 1.1 | todo |
| DR-02 | Add `LuaInterpreter.valid`/`family`/`familyOrUnknown` and `LuaInterpreterFamily.find` resolution tests | TC 1–3, Risk 1.1 | todo |
| DR-03 | Add `Banner.create` null/garbage (non-matching banner) test | TC 5, Risk 1.1 | todo |
| DR-04 | Add `family.languageLevel` mapping (5.1–5.4) + fallback (LUA50 / LuaJIT→LUA51) tests | TC 6–7, Risk 1.1 | todo |
| DR-05 | Add `newLuaInterpreterCommandLine` `.jar`→`java -cp <jar> lua` test, plus `substituteEnvVars` and `platformExecutableName` tests | TC 8–10, Risk 1.1 | todo |
| DR-06 | Add persistence round-trip tests for `LuaApplicationSettings.interpreters` (+ `validInterpreters`/`findInterpreter`) and `LuaProjectSettings.interpreter` (+ `newProjectLuaInterpreterCommandLine`) | TC 11–12, Risk 1.1 | todo |

## Test Case Gaps
All requirements now carry test cases in `requirements.md` (TC 1–16). The gap is that **only
TC 4 is implemented** (`settings/TestBanner.kt`). TC 1–3, 5, 6–16 have no implementing test and
are tracked by DR-01…DR-06 above.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
