---
id: MAINT-18
title: "MAINT-18: Test Coverage - LuaCov Reports"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-18: Test Coverage - LuaCov Reports

## Overview
Increase test coverage for coverage report custom files parsing, cover/uncover line token highlighting, and notification actions.

## Scope
* **In Scope**:
  * Unit tests for custom file type and language registration of `.luacov.report.out` files in `LuaCovReportFileType` and `LuaCovReportLanguage`.
  * Unit tests for tokenizer states (e.g. hit indicators, boundaries) in `LuaCovReportLexer`.
  * Unit tests for layered syntax highlighting (combining coverage highlights with source language color schemes) in `LuaCovReportSyntaxHighlighter` and `LuaCovReportEditorHighlighterProvider`.
  * Unit tests for the editor notification banner rendering and actions in `LuaCovReportNotificationProvider`.
* **Out of Scope**:
  * Testing graphical IDE coverage charts.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-18-01 | **Report File Types** | Must | planned | Verify that files ending in `.luacov.report.out` map to the correct coverage report language and file type. |
| MAINT-18-02 | **Coverage Lexing States** | Must | planned | Verify that `LuaCovReportLexer` parses report file header, summary, covered hit lines, and uncovered code lines. |
| MAINT-18-03 | **Layered Highlighting Integration** | Must | planned | Verify that `LuaCovReportSyntaxHighlighter` embeds standard `LuaSyntaxHighlighter` colors within code lines. |
| MAINT-18-04 | **Editor Banner Notifications** | Must | planned | Verify that opening a coverage report file adds a warning banner containing action link triggers. |

## Acceptance Criteria
* **AC-18-01**: A test case asserts that a file named `luacov.report.out` resolves to a `LuaCovReportFileType` instance.
* **AC-18-02**: A test case asserts that tokenizing `  3: print(1)` returns a hit count token of value `3` and a code block token.
* **AC-18-03**: A test case asserts that code sections inside a report file are highlighted with standard Lua keyword colors.
* **AC-18-04**: A test case asserts that the notification provider creates a non-null editor banner panel when given a valid report file.
