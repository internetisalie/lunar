---
id: "COMP-QA"
title: "Verification Checklists"
type: "qa"
parent_id: "COMP"
status: "in_progress"
priority: "high"
folders:
  - "[[features/completion/requirements|requirements]]"
---
# Completion Verification Checklists

This document provides checklists for human verification of code completion features.

## COMP-01: Keyword Completion
- [ ] Verify keywords are suggested at the start of a statement.
- [ ] Verify `then` is suggested after `if` and `elseif`.
- [ ] Verify `do` is suggested after `while` and `for`.
- [ ] Verify `end` is suggested to close blocks.

## COMP-G-01: Malformed PSI & LuaCATS (De-risked)
- [ ] **Inheritance**: Verify that inherited members (via `@class : Parent`) appear in completion.
- [ ] **Colon Filtering**: Verify that `:` completion suggests only methods, while `.` suggests all members.
- [ ] **Malformed PSI**: Verify completion works even after an unclosed block (e.g., `if true then` without `end`).
- [ ] **Type Display**: Verify that the completion list shows inferred types (e.g., `number`, `string`) for members.
