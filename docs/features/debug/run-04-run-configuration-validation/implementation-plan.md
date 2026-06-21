---
id: "RUN-04-PLAN"
title: "Implementation Plan"
type: "plan"
status: "done"
parent_id: "RUN-04"
folders:
  - "[[features/debug/run-04-run-configuration-validation/requirements|requirements]]"
---

# RUN-04: Implementation Plan

<!-- Retrospective plan. The implementation phases are complete (code already shipped) and are
     checked [x]. Verification/test tasks that have NO corresponding automated test in the repo
     are left UNCHECKED — see risks-and-gaps.md. -->

## Phases

### Phase 1: Interpreter validation [Must] — DONE
- **Goal**: Abort launch when the interpreter is undefined or unresolvable.
- **Tasks**:
  - [x] Implement the interpreter-defined guard in `LuaRunConfiguration.getState()` →
    `startProcess()` (`run/LuaRunConfiguration.kt:229-230`) — realizes design §3.1 step 1.
  - [x] Implement the interpreter-resolvable guard via `newLuaInterpreterCommandLine`
    (`run/LuaRunConfiguration.kt:231-232`) — realizes design §3.1 step 2.
- **Exit criteria**: TC #1 and TC #2 conditions throw the expected `ExecutionException`.

### Phase 2: Debug preloader validation [Must] — DONE
- **Goal**: Under the Debug executor, abort when bundled debugger assets are missing.
- **Tasks**:
  - [x] Guard plugin `lua` directory + `debug.lua` preloader lookup
    (`run/LuaRunConfiguration.kt:250-254`) — realizes design §3.1 step 6.
- **Exit criteria**: TC #3 condition throws a locating `ExecutionException`; non-debug launch skips it.

### Phase 3: Fallback handling [Should] — DONE
- **Goal**: Treat empty script and empty source path as fallbacks, not errors.
- **Tasks**:
  - [x] Empty-script → `-v -i` interactive fallback (`run/LuaRunConfiguration.kt:237-239`) —
    realizes design §3.1 step 4.
  - [x] Empty-source-path → `LuaProjectSettings.expandSourcePath` fallback
    (`run/LuaRunConfiguration.kt:262-272`) — realizes design §3.1 step 7.
- **Exit criteria**: TC #4 launches interactively with no exception.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| RUN-04-01 | M | Phase 1 |
| RUN-04-02 | M | Phase 1 |
| RUN-04-03 | M | Phase 2 |
| RUN-04-04 | S | Phase 3 |
| RUN-04-05 | S | Phase 3 |
| RUN-04-06 | M | Phases 1–2 (all guards throw `ExecutionException`) |

## Verification Tasks

> No automated test currently exercises `LuaRunConfiguration` validation. The only existing test,
> `TestLuaRunConfiguration.testOptionsPersistence` (`src/test/.../run/TestLuaRunConfiguration.kt:9`),
> covers options round-tripping, **not** `startProcess()` validation. These tasks are therefore
> **unchecked** (see [risks-and-gaps.md](risks-and-gaps.md), Gap 2.1).

- [ ] Add a `startProcess()` validation test: no interpreter → `ExecutionException("Interpreter is not defined")` — covers TC #1.
- [ ] Add a test: unresolvable interpreter → `ExecutionException("Interpreter is not found")` — covers TC #2.
- [ ] Add a test (or VNC manual check): Debug launch without `debug.lua` → locating `ExecutionException` — covers TC #3.
- [ ] Add a test: empty script → command line contains `-v -i` — covers TC #4.
- [ ] Run [human-verification-checklists.md] (not yet authored) for the run-error dialog UX — covers TC #5/RUN-04-06.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Interpreter validation | done | Must |
| Phase 2: Debug preloader validation | done | Must |
| Phase 3: Fallback handling | done | Should |
| Verification: automated validation tests | todo | Must |
</content>
