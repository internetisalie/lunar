---
id: "RUN-02-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "in_progress"
parent_id: "RUN-02"
folders:
  - "[[features/debug/run-02-run-configurations/requirements|requirements]]"
---

# RUN-02: Risks & Gaps

> The implementation is shipped, but this file records the **test-coverage gap** and the
> deliberately deferred capabilities so the planning record is honest.

## Critical Risks

### Risk 1.1: Command-line / debug logic is largely untested
- **Impact**: The execution path (`startProcess` §3.1), debug env injection (§3.2),
  `LUA_PATH` resolution (§3.3), and interpreter resolution (§3.4) have **no automated
  tests**. The only test, `TestLuaRunConfiguration.testOptionsPersistence`
  (`src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt`), exercises
  option round-tripping only. Regressions in argument ordering, the Debug-vs-Run env split,
  or the source-path fallback would not be caught.
- **Likelihood**: medium
- **Mitigation**: Add the unit tests enumerated in DR-01 below (TCs 2–9). The command line
  can be asserted without spawning a process by inspecting `GeneralCommandLine` parameters
  and environment built inside an extracted/visible builder, or by asserting the thrown
  `ExecutionException` messages.

## Design Gaps

### Gap 2.1: No `RunConfigurationProducer` for context creation
- **Question**: Should right-clicking a `.lua` file (or a `main`-like entry) auto-create a
  Lua run configuration, as `LuaTestRunConfigurationProducer` (`plugin.xml:410-411`) does
  for tests?
- **Options / leaning**: Implement a `LazyRunConfigurationProducer<LuaRunConfiguration>`
  that sets `scriptName` from the context file. Deferred — manual creation via the `+`
  dialog is the shipped surface and satisfies the RUN-02 requirements (none of which is a
  context-creation `Must`).
- **Resolved by**: tracked as future work; not blocking RUN-02 `done`.

### Gap 2.2: Anonymous `CommandLineState` is not independently reusable
- **Question**: The launch logic lives in an anonymous `CommandLineState` inside
  `getState`, so it cannot be unit-tested or reused by a future producer without
  extraction.
- **Options / leaning**: Extract a named `LuaCommandLineState` class if/when DR-01 testing
  or Gap 2.1 needs it. Low priority while the logic is small (§3.1).
- **Resolved by**: DR-01 (testing may force the extraction).

## Technical Debt & Future Work
- **TBD: Deprecation cleanup** — `FileChooserDescriptorFactory.createSingleLocalFileDescriptor()`
  is deprecated (see the TODO at `run/LuaRunConfiguration.kt:311`). Non-functional.
- **TBD: `applyEditorTo` omits `sourcePath`** — `resetEditorFrom` reads `sourcePath` into
  the field (`:335`) but `applyEditorTo` (`:341-348`) does not write it back from the
  editor field. Source path is still settable programmatically/persisted, but the editor's
  source-path field is effectively read-only on apply. Confirm whether this is intended
  before adding tests for editor-driven source path.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| RUN-00-DR-01 | Add `BasePlatformTestCase`/`BaseDocumentTest` unit tests for command-line assembly (TC 5–7), REPL fallback (TC 6), debug env injection (TC 8), `LUA_PATH` resolution (TC 9), interpreter resolution (TC 3–4), and type registration (TC 2) | Risk 1.1, Gap 2.2 | todo |
| RUN-00-DR-02 | Decide intent of `applyEditorTo` not writing `sourcePath`; fix or document | TBD (editor source path) | todo |

## Test Case Gaps
- TC 2, TC 3, TC 4, TC 5, TC 6, TC 7, TC 8, TC 9 have **no automated coverage** (only TC 1
  is covered). All are addressed by RUN-00-DR-01.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
</content>
