---
id: "MAINT-18-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-18"
folders:
  - "[[features/maint/18-luacov-reports/requirements|requirements]]"
---

# MAINT-18: Implementation Plan

Test-only feature. All work extends the two existing test classes under
`src/test/kotlin/net/internetisalie/lunar/coverage/`. No production files change.
Add `@Test` methods only; keep fixtures light. Verify each phase with the cited command.

## Phase 1 — Report & stats parser edge cases [Must]
- **File**: `src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageTest.kt` (extend).
- **Add methods**:
  - `testReportParserMultipleFileSections()` — TC-18-04: two boundary-delimited sections →
    two `FileCoverage` with correct paths/hit maps.
  - `testReportParserMalformedSectionResets()` — TC-18-06: a path line not followed by a
    second boundary emits no `FileCoverage` for that section.
  - `testStatsParserWrappedCountsAndBlankLines()` — TC-18-11: hit counts wrapped across two
    lines with an intervening blank line still map to `totalLines` 1-indexed entries; the
    following `N:path` header still parses.
- **Verify**:
  `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCoverageTest*"`

## Phase 2 — Lexer states & highlighter mapping [Must]
- **File**: `src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageReportTest.kt` (extend).
- **Add methods**:
  - `testLexerCoveredVsUncoveredPrefixes()` — TC-18-13: a `***0 x` line yields
    `HIT_UNCOVERED`+`LUA_CODE`; a `  10 y` line yields `HIT_COVERED`+`LUA_CODE`.
  - `testLexerStateRoundTrip()` — TC-18-14: capture `lexer.getState()` at an offset just
    before a `FILE_PATH` line, `start(text, offset, end, capturedState)` on a fresh lexer,
    assert the next token lexes as `FILE_PATH`.
  - `testSyntaxHighlighterTokenMap()` — TC-18-15: for `LuaCovReportSyntaxHighlighter()`,
    assert `getTokenHighlights(HEADER_BOUNDARY)[0] == LuaCovReportHighlight.HEADER`,
    `FILE_PATH→FILE_PATH`, `HIT_COVERED→COVERED`, `HIT_UNCOVERED→UNCOVERED`, and that
    `LUA_CODE`/`NEWLINE` return `TextAttributesKey.EMPTY_ARRAY` (size 0).
- **Verify**:
  `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCoverageReportTest*"`

## Phase 3 — File type, layered highlighter, banner, action [Must/Should]
- **File**: `src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageReportTest.kt` (extend).
- **Add methods**:
  - `testFileTypeIdentity()` — TC-18-02: `LuaCovReportFileType.language == LuaCovReportLanguage`,
    `.name == "LuaCov Report"`, `.description` non-empty. (TC-18-01 file-type resolution is
    already asserted in `testNotificationProvider`; do not duplicate.)
  - `testLayeredEditorHighlighter()` — TC-18-16: call
    `LuaCovReportEditorHighlighterProvider().getEditorHighlighter(project, LuaCovReportFileType, null, EditorColorsManager.getInstance().globalScheme)`
    and assert the result `is LayeredLexerEditorHighlighter` and
    `is LuaCovReportEditorHighlighter`.
  - `testNotificationNullForNonReportFile()` — TC-18-17: `dummy.txt` → `null`. (Already
    partially covered in `testNotificationProvider`; only add if isolating.)
  - `testNotificationNullWhenDismissed()` — TC-18-19: set `DISMISSED_KEY` via
    `virtualFile.putUserData` on a report file → `collectNotificationData` returns `null`.
- **Verify**:
  `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCoverageReportTest*"`

## Full-suite gate
After all phases:
`tooling/gce-builder/gce-builder.sh run "test --tests *Coverage*"`
then `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` before committing.

## Notes / grounding
- `DISMISSED_KEY` is `private` (`report/LuaCovReportNotificationProvider.kt:21`), so
  TC-18-19 sets user-data via a real dismissal flow if the key is not reachable: open the
  report, apply the panel, click the "Dismiss" action label, then re-query
  `collectNotificationData`. If reflection on the companion is preferred, keep it isolated
  to that one test.
- Layered-highlighter and banner tests require an initialized editor scheme → fonts; the
  gce-builder bootstrap already provisions `fontconfig` + `fonts-dejavu-core`
  (see `.agents/AGENTS.md`). Run these on the builder VM, not locally.
- All method bodies follow the existing `BasePlatformTestCase` + `File.createTempFile`
  patterns already in the two test files; ≤30 logic lines per method.
</content>
