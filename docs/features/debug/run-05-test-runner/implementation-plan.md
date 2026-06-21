---
id: "RUN-08-PLAN"
title: "Implementation Plan"
type: "plan"
status: "in_progress"
parent_id: "RUN-08"
folders:
  - "[[features/debug/run-05-test-runner/requirements|requirements]]"
---

# RUN-08: Implementation Plan

## Phases

### Phase 1: Tool Registration & Foundation [Must]
- **Goal**: Register `luacov` and `busted` in the tool inventory; create the test run configuration type with editor UI.
- **Tasks**:
  - [x] Add `LUACOV` and `BUSTED` to `LuaToolType` enum in [LuaToolDescriptor.kt](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/tool/LuaToolDescriptor.kt) — realizes design §7 tool registration table
  - [x] Add descriptors to `LuaToolDescriptor.DESCRIPTORS` — realizes design §7 tool registration table
  - [x] Add `inferType()` and `displayNameFor()` cases in [LuaToolManager.kt](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/tool/LuaToolManager.kt) — realizes design §7 tool registration table
  - [x] Add version patterns/flags in [LuaToolValidator.kt](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/tool/LuaToolValidator.kt) — realizes design §7 tool registration table
  - [x] Create `LuaTestRunConfigurationType` — realizes design §2.1
  - [x] Create `LuaTestRunConfigurationFactory` — realizes design §2.2
  - [x] Create `LuaTestRunConfigurationOptions` — realizes design §2.3
  - [x] Create `LuaTestRunConfiguration` — realizes design §2.4
  - [x] Create `LuaTestSettingsEditor` — realizes design §2.12
  - [x] Register `<configurationType>` in `plugin.xml` — realizes design §7
- **Exit criteria**: `./gradlew test` green; "Lua Tests" appears in Run Configurations dialog; user can fill in framework/target/interpreter fields and save; `busted` and `luacov` auto-discovered in Tool settings.

### Phase 2: Test Execution & SMTestRunner [Must]
- **Goal**: Execute Busted/Lunity tests and display results in the IntelliJ Test Results window.
- **Tasks**:
  - [x] Create `LuaTestCommandLineState` with `buildCommandLine()` — realizes design §2.5, §3.1
  - [x] Create `LuaTestConsoleProperties` — realizes design §2.6
  - [x] Create `LuaTestOutputToEventsConverter` with Busted JSON parsing — realizes design §2.7, §3.2
  - [x] Add Lunity JSON line parsing to `LuaTestOutputToEventsConverter` — realizes design §2.7, §3.3
  - [x] Create `LuaTestFramework` enum — realizes design §2.4
- **Exit criteria**: Running a Busted test file populates the Test Results tree with pass/fail/ignore nodes; Lunity JSON output similarly parsed; raw output falls back to console on parse failure. Covers TC #1, TC #2, TC #3, TC #10, TC #11, TC #12, TC #13, TC #14, TC #17, TC #18. Command-line construction validated per TC #15, TC #16.

### Phase 3: Test Navigation & Gutter Icons [Should]
- **Goal**: Navigate from test results to source; show gutter run icons on test declarations.
- **Tasks**:
  - [x] Create `LuaTestLocator` — realizes design §2.8, §3.4
  - [x] Create `LuaTestRunConfigurationProducer` — realizes design §2.9, §3.5
  - [x] Create `LuaTestRunLineMarkerProvider` — realizes design §2.10, §3.6
  - [x] Register `<runConfigurationProducer>` and `<runLineMarkerContributor>` in `plugin.xml` — realizes design §7
- **Exit criteria**: Clicking a test node navigates to source; right-clicking a test file offers "Run Lua Tests"; gutter Play icons appear next to `describe`/`it`/`test_` declarations. Covers TC #4, TC #5.

### Phase 4: Rerun Failed Tests [Should]
- **Goal**: Rerun only failed tests from the previous run.
- **Tasks**:
  - [x] Create `LuaRerunFailedTestsAction` — realizes design §2.11, §3.7
  - [x] Wire into `LuaTestConsoleProperties` via `createRerunFailedTestsAction()`
- **Exit criteria**: After a test run with failures, "Rerun Failed Tests" button re-executes only the failed test names.

### Phase 5: Coverage Engine [Should]
- **Goal**: Integrate with IntelliJ Coverage framework; parse `luacov` output; display gutter indicators.
- **Tasks**:
  - [x] Create `LuaCoverageEngine` — realizes design §2.13
  - [x] Create `LuaCoverageRunner` with stats/report parsing dispatch — realizes design §2.14, §3.8
  - [x] Create `LuaCoverageAnnotator` — realizes design §2.15
  - [x] Create `LuaCoverageProgramRunner` — realizes design §2.16, §3.9
  - [x] Create `LuaCovStatsParser` — realizes design §2.19, §3.13
  - [x] Create `LuaCovReportParser` with state machine — realizes design §2.18, §3.11, §3.12
  - [x] Register `<coverageEngine>`, `<coverageRunner>`, `<programRunner>`, `<projectService>` in `plugin.xml` — realizes design §7
- **Exit criteria**: "Run with Coverage" on a Lua test config collects `luacov` data and shows green/red gutter markers. Missing `luacov` triggers balloon notification. Covers TC #6, TC #7.
- **RUN-08-13 fix (2026-06-21, status → Full)**: live `verify-in-ide` (Gate 2b) found coverage loaded but **no gutter overlays / tree percentages rendered**. Root cause: `LuaCovReportParser.toProjectData` created `LineData(line - 1, …)` (0-based line number) but stored it at the 1-based array slot `line`, so the `LineData`'s own `getLineNumber()` disagreed with its slot. The platform convention (confirmed against `intellij-community`: `LcovSerializationUtils.java:88-94`, `JaCoCoCoverageRunner.java:156-166`, and `CoverageEditorAnnotatorImpl.java:277-279`, which does `getLineNumber() - 1` for the 0-based editor line) is **1-based `LineData` line numbers stored at the same 1-based array slot** (slot 0 unused). Fix: `LineData(line, null)` so number and slot agree; `getClassData(absPath).getLineData(L)` now returns the correct hits with a consistent `getLineNumber()`. Guarded by `LuaCoverageTest.testToProjectDataLineNumbersAreOneBasedAndConsistent` (fails pre-fix `expected:<1> but was:<0>`, passes post-fix). Live Gate 2b re-verification by the supervisor follows.

### Phase 6: Coverage Import & Report Viewer [Should/Could]
- **Goal**: Import existing report files; custom syntax highlighting and editor banner for `luacov.report.out`.
- **Tasks**:
  - [x] Create `LuaCovReportImportAction` — realizes design §2.17, §3.10
  - [x] Register action in `plugin.xml` under `AnalyzePlatformMenu` (NOT `AnalyzeMenu` — that group is Java-plugin-only and absent in GoLand/headless tests; see design §7 "Menu-group binding contract" and risks Risk 1.5) — realizes design §7
  - [x] Add a headless `BasePlatformTestCase` acceptance check: `ActionManager.getInstance().getAction("Lunar.ImportLuaCovReport")` is non-null AND `getAction("AnalyzePlatformMenu") as ActionGroup` contains it, with no unresolved-group warning at plugin load — resolves risks Risk 1.5
  - [x] Create `LuaCovReportLanguage` — realizes design §2.20
  - [x] Create `LuaCovReportFileType` — realizes design §2.21
  - [x] Create `LuaCovReportLexer` — realizes design §2.22
  - [x] Create `LuaCovReportHighlight` — realizes design §2.24
  - [x] Create `LuaCovReportSyntaxHighlighter` — realizes design §2.23
  - [x] Create `LuaCovReportSyntaxHighlighterFactory` — realizes design §2.27
  - [x] Create `LuaCovReportEditorHighlighterProvider` + `LuaCovReportEditorHighlighter` — realizes design §2.25
  - [x] Create `LuaCovReportNotificationProvider` — realizes design §2.26
  - [x] Register `<fileType>`, `<lang.syntaxHighlighterFactory>`, `<editorHighlighterProvider>`, `<editorNotificationProvider>` in `plugin.xml` — realizes design §7
- **Exit criteria**: User can import a `luacov.report.out` via Analyze menu; opening a `luacov.report.out` shows colored hit prefixes with embedded Lua highlighting and a banner offering to load coverage. Covers TC #8, TC #9.
- **RUN-08-17 fix (2026-06-21, status → Full)**: live `verify-in-ide` (Gate 2b) found the uncovered `***0` prefix rendered **orange**, not the red required by design §2.24 / TC #9. Root cause: `LuaCovReportHighlight.UNCOVERED` fell back to `DefaultLanguageHighlighterColors.KEYWORD` (orange in Darcula). Fix follows the design's "configured in color scheme" intent: bundled `<additionalTextAttributes>` color schemes (`resources/colorSchemes/LuaCovReportDefault.xml`, `resources/colorSchemes/LuaCovReportDarcula.xml`) define `LUACOV_UNCOVERED` red and `LUACOV_COVERED` green, registered declaratively in `plugin.xml`. The `UNCOVERED` key no longer carries the orange `KEYWORD` fallback; `COVERED` keeps the green `STRING` fallback. Live Gate 2b re-verification by the supervisor follows.

### Phase 7: Assertion Diff Viewer [Could]
- **Goal**: Parse assertion messages to show IntelliJ's comparative diff viewer.
- **Tasks**:
  - [x] Implement assertion diff parsing in `LuaTestOutputToEventsConverter` — realizes design §3.14
  - [x] Wire `testFailed` with `expected`/`actual` parameters when diff parsed
- **Exit criteria**: A Busted assertion failure `"Expected 4 but got 5"` shows the diff viewer button in the test results panel.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| RUN-08-01 | M | Phase 1 |
| RUN-08-02 | M | Phase 2 |
| RUN-08-03 | M | Phase 2 |
| RUN-08-04 | M | Phase 2 |
| RUN-08-05 | M | Phase 2 |
| RUN-08-06 | M | Phase 2 |
| RUN-08-07 | S | Phase 3 |
| RUN-08-08 | S | Phase 3 |
| RUN-08-09 | S | Phase 4 |
| RUN-08-10 | C | Phase 7 |
| RUN-08-11 | S | Phase 5 |
| RUN-08-12 | S | Phase 5 |
| RUN-08-13 | S | Phase 5 |
| RUN-08-14 | S | Phase 1 |
| RUN-08-15 | S | Phase 5 |
| RUN-08-16 | S | Phase 6 |
| RUN-08-17 | C | Phase 6 |
| RUN-08-18 | C | Phase 6 |

## Verification Tasks

- [x] Unit tests for `LuaCovStatsParser` — covers TC #6 (stats file parsing)
- [x] Unit tests for `LuaCovReportParser` — covers TC #8 (report file parsing)
- [x] Unit test for `LuaCovReportParser.toProjectData` line-number/slot consistency (`LuaCoverageTest.testToProjectDataLineNumbersAreOneBasedAndConsistent`) — regression guard for RUN-08-13 gutter-overlay defect
- [x] Unit tests for Lunity JSON line parsing — covers TC #1, TC #2, TC #10
- [x] Unit tests for Busted JSON output parsing — covers TC #11, TC #12
- [x] Unit tests for `LuaTestCommandLineState.buildCommandLine()` — covers TC #15, TC #16
- [x] Unit tests for `LuaTestOutputToEventsConverter` stream routing and fallback — covers TC #17, TC #18
- [ ] Unit tests for `LuaTestLocator` URL resolution — covers TC #4
- [ ] Unit tests for `LuaTestRunConfigurationProducer` context detection — covers TC #5
- [ ] Unit tests for `LuaTestRunLineMarkerProvider` gutter detection — covers TC #5
- [x] Unit tests for assertion diff parsing — covers RUN-08-10
- [ ] Integration test: create and execute a "Lua Tests" config — covers TC #3
- [ ] Manual verification: run `human-verification-checklists.md` scenarios

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Tool Registration & Foundation | done | Must |
| Phase 2: Test Execution & SMTestRunner | done | Must |
| Phase 3: Test Navigation & Gutter Icons | done | Should |
| Phase 4: Rerun Failed Tests | done | Should |
| Phase 5: Coverage Engine | done | Should |
| Phase 6: Coverage Import & Report Viewer | done | Should/Could |
| Phase 7: Assertion Diff Viewer | done | Could |
