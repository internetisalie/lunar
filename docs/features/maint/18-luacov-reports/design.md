---
id: "MAINT-18-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-18"
folders:
  - "[[features/maint/18-luacov-reports/requirements|requirements]]"
---

# Technical Design: MAINT-18 — Test Coverage: LuaCov Reports

This is a **test map**, not a production design. No production class changes. Every
symbol below is grounded with a `path:line` citation into `src/main`.

## 1. Targets Under Test (grounded)

### Parsers (`net.internetisalie.lunar.coverage`)
| Class / member | Location | Behavior tested |
|---|---|---|
| `data class FileCoverage(filePath, lineHits)` | `coverage/LuaCovReportParser.kt:9` | Model asserted by both parsers. |
| `object LuaCovReportParser` | `coverage/LuaCovReportParser.kt:14` | Container. |
| `LuaCovReportParser.parse(File): List<FileCoverage>` | `coverage/LuaCovReportParser.kt:19` | 5-state machine (`SEARCH_HEADER`/`PARSE_PATH`/`EXPECT_BOUNDARY`/`PARSE_LINES`); `BOUNDARY=^={10,}$` (`:15`), `UNCOVERED=^\*+0\s` (`:16`), `COVERED=^\s*(\d+)\s` (`:17`). Non-executable (no prefix) lines still increment `lineNumber` but are not added. |
| `LuaCovReportParser.toProjectData(List, Project?)` | `coverage/LuaCovReportParser.kt:74` | Relative-path resolution vs `project.basePath` (`:77-87`); builds `LineData` arrays with 1-based `lineNumber` and `LineCoverage.FULL`/`NONE` status (`:91-96`). |
| `object LuaCovStatsParser` | `coverage/LuaCovStatsParser.kt:5` | Container. |
| `LuaCovStatsParser.parse(File): List<FileCoverage>` | `coverage/LuaCovStatsParser.kt:6` | `headerRegex=^(\d+):(.+)$` (`:10`); collects `totalLines` counts, wrapping across lines, skipping blanks, stopping at next header (`:23-41`). |

### Report editor stack (`net.internetisalie.lunar.coverage.report`)
| Class / member | Location | Behavior tested |
|---|---|---|
| `object LuaCovReportLanguage : Language("LuaCovReport")` | `report/LuaCovReportLanguage.kt:5` | Identity of the file type's language. |
| `object LuaCovReportFileType : LanguageFileType` | `report/LuaCovReportFileType.kt:7` | `name="LuaCov Report"` (`:8`), non-empty description (`:9`), language binding. |
| `class LuaCovReportLexer : LexerBase` | `report/LuaCovReportLexer.kt:6` | Token companions (`:22-29`); `start` unpacks `initialState` (`:39-40`); `getState` packs (`:47`); `advance` dispatch (`:60`). |
| `LuaCovReportLexer.HEADER_BOUNDARY/FILE_PATH/HIT_COVERED/HIT_UNCOVERED/HIT_NONE/LUA_CODE/NEWLINE/WHITESPACE` | `report/LuaCovReportLexer.kt:22-29` | Public token constants asserted directly. |
| `class LuaCovReportSyntaxHighlighter : SyntaxHighlighterBase` | `report/LuaCovReportSyntaxHighlighter.kt:8` | `getTokenHighlights` map (`:10-18`). |
| `object LuaCovReportHighlight` (`HEADER/FILE_PATH/COVERED/UNCOVERED`) | `report/LuaCovReportHighlight.kt:9,13,16,24,27` | Keys the highlighter maps to. |
| `class LuaCovReportEditorHighlighterProvider : EditorHighlighterProvider` | `report/LuaCovReportEditorHighlighterProvider.kt:13` | `getEditorHighlighter(...)` returns `LuaCovReportEditorHighlighter` (`:16`). |
| `class LuaCovReportEditorHighlighter : LayeredLexerEditorHighlighter` | `report/LuaCovReportEditorHighlighterProvider.kt:21` | Registers `LuaSyntaxHighlighter` layer for `LUA_CODE` (`:24-26`). |
| `class LuaCovReportNotificationProvider : EditorNotificationProvider, DumbAware` | `report/LuaCovReportNotificationProvider.kt:18` | `collectNotificationData` (`:24`): null for non-report / dismissed; panel text `"This is a LuaCov coverage report."` (`:33`); `DISMISSED_KEY` (`:21`). |
| Import action id `Lunar.ImportLuaCovReport` | `coverage/LuaCovReportImportAction.kt` (registered in `plugin.xml`) | Existence + `AnalyzePlatformMenu` membership. |

### Layer dependency (grounded)
`LuaSyntaxHighlighter` — `net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter`
(imported at `report/LuaCovReportEditorHighlighterProvider.kt:11`) — is the embedded layer.

## 2. Test Approach

- **Parsers** — pure JVM logic; feed fixture text via `File.createTempFile` +
  `writeText`, call `parse`/`toProjectData`, assert the returned `FileCoverage` /
  `ProjectData` model. `toProjectData(results, project)` path-resolution needs a
  `Project`, so those cases run under `BasePlatformTestCase` (use `project.basePath`).
  Pure-model cases (`toProjectData(coverages)` with no project) need no fixture but stay
  in the same class for simplicity.
- **Lexer** — instantiate `LuaCovReportLexer()` directly, `start(text, 0, text.length)`,
  loop `while (tokenType != null) { record; advance() }`. Assert the token-type sequence
  and the substring for each token. State round-trip: capture `getState()` at an offset,
  then `start(text, offset, end, capturedState)` on a fresh lexer and assert continuation.
  (Mirrors existing `LuaCoverageReportTest.testLexer`.)
- **Highlighter** — construct `LuaCovReportSyntaxHighlighter()` and call
  `getTokenHighlights(tokenType)` for each `LuaCovReportLexer` constant; assert the mapped
  `LuaCovReportHighlight` key (compare `[0]`), and `EMPTY_ARRAY` for `LUA_CODE`/`NEWLINE`.
  No fixture/editor needed.
- **Layered highlighter** — call
  `LuaCovReportEditorHighlighterProvider().getEditorHighlighter(project, LuaCovReportFileType, vf, EditorColorsManager.getInstance().globalScheme)` and assert the result is a
  `LayeredLexerEditorHighlighter` (and `LuaCovReportEditorHighlighter`). Runs under
  `BasePlatformTestCase`; editor-scheme init requires fonts (already provisioned by
  gce-builder per AGENTS.md "Headless editor/inlay tests need fonts").
- **Notification / file type / action** — light `BasePlatformTestCase` with
  `myFixture.configureByText("luacov.report.out", …)`; assert `virtualFile.fileType`,
  open via `FileEditorManager`, apply the returned `Function`, assert
  `EditorNotificationPanel.text`. Action via `ActionManager.getInstance()`.

## 3. Existing Coverage (do not duplicate)
`src/test/kotlin/net/internetisalie/lunar/coverage/LuaCoverageReportTest.kt` and
`LuaCoverageTest.kt` already cover: basic lexer sequence, asterisk-width uncovered,
parser integration + `toProjectData` path resolution, stats parse, `toProjectData`
line-number/status, notification provider happy path, and action registration. New tests
**extend** these classes with the currently-uncovered cases: multi-section parse
(TC-18-04), malformed-section reset (TC-18-06), stats wrapped-count/blank-line
(TC-18-11), lexer `HIT_COVERED`/`HIT_UNCOVERED` prefix + state round-trip (TC-18-13/14),
the full `getTokenHighlights` map (TC-18-15), the layered highlighter construction
(TC-18-16), and the dismissed/non-report null paths (TC-18-17/19). Do not re-add
already-passing assertions.

## 4. Open Questions
None.
