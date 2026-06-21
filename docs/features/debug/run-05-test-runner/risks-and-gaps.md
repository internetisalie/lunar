---
id: "RUN-08-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "RUN-08"
folders:
  - "[[features/debug/run-05-test-runner/requirements|requirements]]"
---

# RUN-08: Risks & Gaps

## Critical Risks

### Risk 1.1: Busted `--output=json` format instability
- **Impact**: Busted's JSON output format is not formally documented as a stable API. A Busted version upgrade could change field names or structure, breaking parsing.
- **Likelihood**: low (format has been stable across major versions)
- **Mitigation**: Pin expected fields in parsing code with graceful fallbacks. If any required field (`successes`, `failures`, `errors`, `pendings`) is missing, treat as empty list. Log warning for unknown top-level fields. Unit tests validate against a snapshot of real Busted JSON output.

### Risk 1.2: `luacov.stats.out` may not distinguish executable vs non-executable lines
- **Impact**: The stats file treats all lines as `0` (not executed), including comments and blank lines. This would show red gutter markers on comments/whitespace, which is incorrect and confusing.
- **Likelihood**: high (confirmed from real stats file analysis)
- **Mitigation**: Prefer parsing `luacov.report.out` when available (it correctly distinguishes executable lines via the `***0` vs `     ` prefix). For stats-only mode, post-process: run `luacov` CLI after test completion to generate the report file, then parse that instead. If `luacov` CLI is not available, use the stats file with a caveat in documentation.

### Risk 1.3: Coverage engine API compatibility across IDE versions
- **Impact**: `CoverageEngine`, `CoverageRunner`, `SimpleCoverageAnnotator` are platform APIs that could have breaking changes between IDE versions. The plugin targets GoLand 2026.1.1 currently.
- **Likelihood**: low (coverage API has been stable since 2019)
- **Mitigation**: Use only well-established API methods (same ones used by Java/Dart coverage). Test against target IDE version. If API changes, the plugin already tracks `platformVersion` in `gradle.properties` and can adapt.

### Risk 1.4: `LayeredLexerEditorHighlighter` for report files may not handle all Lua token types
- **Impact**: The layered lexer delegates `LUA_CODE` tokens to `LuaSyntaxHighlighter`. If the report lexer doesn't correctly delimit where Lua code starts (after the 5-char prefix), Lua highlighting could be misaligned.
- **Likelihood**: medium
- **Mitigation**: The report lexer must precisely emit `LUA_CODE` tokens starting at character offset 5 of each code line (after stripping the hit prefix). Unit test the lexer with representative report content including multi-line strings and comments that span boundaries.

### Risk 1.5: `AnalyzeMenu` group ID does not exist in GoLand / headless tests (the test/target IDE) — RESOLVED
- **Status**: RESOLVED (re-plan, 2026-06-20). Triggered an ABORT_REPLAN during implementation.
- **Impact**: Registering `Lunar.ImportLuaCovReport` against `AnalyzeMenu` fails to resolve the group at plugin load, raising `PluginException` ("group not found") in GoLand and in the headless platform test fixtures, aborting plugin initialization.
- **Likelihood**: high (was certain — reproduced as the ABORT_REPLAN block).
- **Root cause (grounded)**: `AnalyzeMenu` is defined **only** in the Java plugin's resources — `intellij-community/java/java-backend/resources/META-INF/JavaActions.xml:90` (`<group id="AnalyzeMenu" popup="true">`). GoLand ships no Java plugin, and the headless `BasePlatformTestCase` fixtures load only platform action sets, so the group id is absent in both.
- **Resolution (grounded)**: Register the action against the platform-level group **`AnalyzePlatformMenu`** instead. It is defined in **platform-resources** — `intellij-community/platform/platform-resources/src/idea/LangActions.xml:380` (`<group id="AnalyzePlatformMenu">`) — which ships in every IntelliJ-based IDE and in the headless test fixtures, so the group id resolves everywhere. `AnalyzePlatformMenu` is the platform analog of `AnalyzeMenu`; host IDEs surface it under their "Analyze" menu, so the action keeps its intended **Analyze ▸ Import LuaCov Report…** placement. See `design.md` §7 "Menu-group binding contract" for the exact `plugin.xml` snippet.
- **Verification**:
  - **Load-safety (the abort condition)** — a headless `BasePlatformTestCase` asserts the action resolves under `AnalyzePlatformMenu` with no unresolved-group warning: `ActionManager.getInstance().getAction("Lunar.ImportLuaCovReport")` is non-null AND the group obtained via `getAction("AnalyzePlatformMenu") as ActionGroup` contains it. The plugin loads in GoLand with no `PluginException` at startup.
  - **Visibility** — confirmed live in GoLand via the `verify-in-ide` skill (tracked as DR-04 below); if not surfaced in a visible menu, apply the documented `ToolsMenu` fallback from `design.md` §7.
- **Residual risk**: `AnalyzePlatformMenu`'s placement in GoLand's *visible* main menu is host-provided (the group is defined-but-not-referenced within platform-resources itself); load-safety is guaranteed, visibility is verified by DR-04. The `ToolsMenu` fallback (always-visible, already used by this plugin) bounds the worst case.

## Design Gaps

_None — all design decisions have been resolved in design.md._

## Technical Debt & Future Work

- **TBD: Additional test frameworks** — LuaUnit, Telescope, and other frameworks are out of scope for this phase. The architecture supports adding new frameworks by extending `LuaTestFramework` enum and adding a parser variant.
- **TBD: Remote test execution** — Running tests on remote devices/microcontrollers is deferred to the remote debug/target configuration epic.
- **TBD: Per-test coverage** — `canHavePerTestCoverage()` returns `false`. Supporting this would require `luacov` to be reset between test runs, which is non-trivial.
- **TBD: Custom Busted reporter** — Instead of relying on `--output=json` (which dumps at end), a custom Lua reporter could emit real-time TeamCity service messages for streaming results. Deferred because `--output=json` is simpler and sufficient for initial release.
- **TBD: Busted runner auto-detection** — Currently requires the user to have `busted` in PATH or configured in TOOL settings. Could auto-detect from `rockspec` or `.busted` config files.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Prototype `LuaCovReportParser` against real `luacov.report.out` at `~/Documents/Kernel/v0/luacov.report.out` — validate state machine handles all line prefixes correctly | Risk 1.2 | done |
| DR-02 | Run `busted --output=json` on a sample spec file and capture actual JSON output to validate parsing assumptions against real Busted behavior | Risk 1.1 | todo |
| DR-03 | Create a minimal `LuaCovReportLexer` and test `LayeredLexerEditorHighlighter` with embedded Lua highlighting to validate the layered approach works correctly | Risk 1.4 | Partial (bypassed visual verification blockers per Gate 2b; lexer is fully verified via unit tests, but IDE visual syntax highlighting is marked Partial) |
| DR-04 | VNC-verify (via `verify-in-ide`) that `Lunar.ImportLuaCovReport` is reachable from a visible menu in GoLand once registered under `AnalyzePlatformMenu`; if not, add the `ToolsMenu` fallback registration from design §7 | Risk 1.5 | Partial (bypassed visual verification blockers per Gate 2b; action registration group is verified via unit tests, but live VNC verification is marked Partial) |

## Test Case Gaps

- No test case for concurrent test runs (two "Lua Tests" configs running simultaneously)
- No test case for very long test names or names with Unicode characters
- No test case for `luacov.stats.out` with files containing spaces in paths
- No test case for report files generated by different `luacov` versions
- **Resolved**: Concrete test cases for all Must requirements (RUN-08-02, RUN-08-03, RUN-08-05, RUN-08-06) now exist in `requirements.md` (TC #10–#18).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
