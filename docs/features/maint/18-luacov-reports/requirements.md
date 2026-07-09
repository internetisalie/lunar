---
id: MAINT-18
title: "MAINT-18: Test Coverage - LuaCov Reports"
type: feature
parent_id: MAINT
status: done
vf_icon: ✅
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-18: Test Coverage - LuaCov Reports

## Overview
Increase unit-test coverage for the LuaCov coverage subsystem: the report/stats
parsers (`LuaCovReportParser`, `LuaCovStatsParser`) that read `luacov.report.out` /
`luacov.stats.out`, and the layered coverage-report editor stack (custom file type /
language registration, the `LuaCovReportLexer` token states, the layered syntax
highlighter, and the editor notification banner). This is a **coverage feature**: no
production behavior changes — only new/expanded tests against existing classes.

## Scope
* **In Scope**:
  * Report parser coverage: `LuaCovReportParser.parse` state machine edge cases and
    `toProjectData` path resolution / status assignment.
  * Stats parser coverage: `LuaCovStatsParser.parse` header/count wrapping and edge cases.
  * File-type / language registration: `.luacov.report.out` → `LuaCovReportFileType` /
    `LuaCovReportLanguage`.
  * `LuaCovReportLexer` token states (boundary, path, hit prefixes, code, newline/whitespace).
  * Highlighter token→attribute mapping in `LuaCovReportSyntaxHighlighter` and the
    layered `LuaCovReportEditorHighlighterProvider` (embedding `LuaSyntaxHighlighter`).
  * Editor banner + action registration in `LuaCovReportNotificationProvider` /
    `LuaCovReportImportAction`.
* **Out of Scope**:
  * Testing graphical IDE coverage gutter charts / live `CoverageDataManager` overlays.
  * Changing any production behavior; running the actual `luacov` CLI.

## Functional (Coverage-Goal) Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-18-01 | **Report File Types** | Must | Full | Verify a `luacov.report.out` virtual file resolves to `LuaCovReportFileType` and that `LuaCovReportFileType.language` is `LuaCovReportLanguage`. |
| MAINT-18-02 | **Report Parser States** | Must | Full | Verify `LuaCovReportParser.parse` extracts file paths and 1-indexed line hits across multiple file sections, skips non-executable lines, and handles variable-width `*+0` uncovered markers. |
| MAINT-18-03 | **Report → ProjectData** | Must | Full | Verify `toProjectData` resolves relative paths against `project.basePath`, sets 1-based `LineData.lineNumber`, and assigns `LineCoverage.FULL`/`NONE` status by hit count. |
| MAINT-18-04 | **Stats Parser** | Must | Full | Verify `LuaCovStatsParser.parse` reads `N:path` headers, maps hit counts to 1-indexed lines, and handles counts wrapped across multiple lines and blank lines. |
| MAINT-18-05 | **Coverage Lexing States** | Must | Full | Verify `LuaCovReportLexer` emits `HEADER_BOUNDARY`, `FILE_PATH`, `HIT_COVERED`, `HIT_UNCOVERED`, `HIT_NONE`, `LUA_CODE`, `NEWLINE` tokens in the correct sequence and that lexer state round-trips via `getState`/`start`. |
| MAINT-18-06 | **Highlighter Mapping** | Must | Full | Verify `LuaCovReportSyntaxHighlighter.getTokenHighlights` maps each token type to its `LuaCovReportHighlight` key and returns empty for `LUA_CODE`/`NEWLINE`. |
| MAINT-18-07 | **Layered Highlighting** | Should | Full | Verify `LuaCovReportEditorHighlighterProvider` returns a `LayeredLexerEditorHighlighter` that registers `LuaSyntaxHighlighter` for the `LUA_CODE` token. |
| MAINT-18-08 | **Editor Banner Notifications** | Must | Full | Verify `LuaCovReportNotificationProvider.collectNotificationData` returns a non-null panel factory (text `"This is a LuaCov coverage report."`) for report files and `null` for non-report / dismissed files. |
| MAINT-18-09 | **Import Action Registration** | Should | Full | Verify the `Lunar.ImportLuaCovReport` action exists and is a child of the `AnalyzePlatformMenu` group. |

## Test Cases (Given / When / Then)
| TC | Req | Given | When | Then |
|---|---|---|---|---|
| TC-18-01 | 18-01 | A file named `luacov.report.out` configured via `myFixture.configureByText` | Read `virtualFile.fileType` | It equals `LuaCovReportFileType`. |
| TC-18-02 | 18-01 | `LuaCovReportFileType` | Read `.language`, `.name`, `.description` | Language is `LuaCovReportLanguage`; name is `"LuaCov Report"`; description non-empty. |
| TC-18-03 | 18-02 | Report text with one file section, a comment line, a `1`-hit line, a `***0` line, and a `10`-hit line | `LuaCovReportParser.parse(tempFile)` | One `FileCoverage`; `filePath` matches; `lineHits[2]==1`, `[3]==0`, `[4]==10`; comment line skipped. |
| TC-18-04 | 18-02 | Report text with **two** boundary-delimited file sections | `parse` | Two `FileCoverage` entries with the correct paths and per-file hit maps. |
| TC-18-05 | 18-02 | Report lines with `***0` and `****0` (3- and 4-asterisk) uncovered markers plus a covered line | `parse` | Both uncovered lines present with `0` hits; covered line present with its count. |
| TC-18-06 | 18-02 | A malformed section where the path line is NOT followed by a second boundary | `parse` | State resets to `SEARCH_HEADER`; no spurious `FileCoverage` is emitted for the malformed section. |
| TC-18-07 | 18-03 | `FileCoverage("initrd/usr/bin/tests.lua", …)` and `project` | `toProjectData(results, project)` | `getClassData(File(basePath,"initrd/usr/bin/tests.lua").absolutePath)` is non-null. |
| TC-18-08 | 18-03 | `FileCoverage("src/bar.lua", {1→0, 2→5, 7→3})` | `toProjectData(coverages)` | `getLineData(1/2/7).lineNumber` equal 1/2/7; hits equal 0/5/3. |
| TC-18-09 | 18-03 | `FileCoverage("src/bar.lua", {1→0, 2→5})` | `toProjectData` | `getLineData(1).status == LineCoverage.NONE.toInt()`; `getLineData(2).status == FULL.toInt()`. |
| TC-18-10 | 18-04 | Stats text `5:a.lua\n0 1 0 2 0\n3:b.lua\n10 0 5` | `LuaCovStatsParser.parse` | Two `FileCoverage`; `a.lua` has 5 entries `{1→0,2→1,3→0,4→2,5→0}`; `b.lua` `{1→10,2→0,3→5}`. |
| TC-18-11 | 18-04 | Stats text whose hit counts for one file wrap across two lines and include a blank line | `parse` | All `totalLines` counts are collected into 1-indexed `lineHits`; blank line skipped; next header still parsed. |
| TC-18-12 | 18-05 | The 6-line report fixture (boundary/path/boundary/none/covered/uncovered) fed to `LuaCovReportLexer` | Iterate `advance()` collecting `tokenType` | Sequence starts `HEADER_BOUNDARY, NEWLINE, FILE_PATH, NEWLINE, HEADER_BOUNDARY, NEWLINE, HIT_NONE, LUA_CODE, …`. |
| TC-18-13 | 18-05 | Lines `***0 x` and `  10 y` in a code section | Lex | `***0`-prefixed line yields `HIT_UNCOVERED`; `  10`-prefixed line yields `HIT_COVERED`, each followed by `LUA_CODE`. |
| TC-18-14 | 18-05 | A lexer positioned mid-file (state captured via `getState`) | Restart a fresh `LuaCovReportLexer` at that offset with the captured `initialState` | Tokenization resumes correctly (e.g. a path line after a first boundary still lexes as `FILE_PATH`). |
| TC-18-15 | 18-06 | `LuaCovReportSyntaxHighlighter` | `getTokenHighlights(t)` for each token type | `HEADER_BOUNDARY→HEADER`, `FILE_PATH→FILE_PATH`, `HIT_COVERED→COVERED`, `HIT_UNCOVERED→UNCOVERED`; `LUA_CODE`/`NEWLINE`→`EMPTY_ARRAY`. |
| TC-18-16 | 18-07 | `LuaCovReportEditorHighlighterProvider` + a default `EditorColorsScheme` | `getEditorHighlighter(project, LuaCovReportFileType, vf, scheme)` | Returns a `LuaCovReportEditorHighlighter` (a `LayeredLexerEditorHighlighter`), non-null. |
| TC-18-17 | 18-08 | `LuaCovReportNotificationProvider` and a `dummy.txt` file | `collectNotificationData(project, txt.virtualFile)` | Returns `null`. |
| TC-18-18 | 18-08 | Provider and a `luacov.report.out` file opened in `FileEditorManager` | Apply the returned `Function` to the editor | Result is an `EditorNotificationPanel` with text `"This is a LuaCov coverage report."`. |
| TC-18-19 | 18-08 | Provider and a report file with `DISMISSED_KEY` user-data set true | `collectNotificationData` | Returns `null`. |
| TC-18-20 | 18-09 | `ActionManager` | `getAction("Lunar.ImportLuaCovReport")` and children of `AnalyzePlatformMenu` | Action is non-null and present in the group's children. |

## Acceptance Criteria
* **AC-18-01**: TC-18-01/02 pass — `luacov.report.out` resolves to `LuaCovReportFileType`.
* **AC-18-02**: TC-18-03..06 pass — parser state machine covered incl. malformed sections.
* **AC-18-03**: TC-18-07..09 pass — `toProjectData` path/line-number/status covered.
* **AC-18-04**: TC-18-10/11 pass — stats parser covered incl. wrapped counts.
* **AC-18-05**: TC-18-12..14 pass — lexer token sequence + state round-trip covered.
* **AC-18-06**: TC-18-15 passes — highlighter token→attribute map covered.
* **AC-18-07**: TC-18-16 passes — layered highlighter constructed.
* **AC-18-08**: TC-18-17..19 pass — banner shown/suppressed correctly.
* **AC-18-09**: TC-18-20 passes — import action registered under Analyze menu.
