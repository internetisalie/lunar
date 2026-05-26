---
id: "COMP-01-QA"
title: "Human Verification Checklist"
type: "qa"
parent_id: "COMP-01"
status: "done"
priority: "high"
folders:
  - "[[features/completion/01-keyword-completion/requirements|requirements]]"
---

# Human Verification Checklist: COMP-01 Keyword Completion

Use this checklist to manually verify the implementation of keyword completion in the IDE.

## Basic Functionality
- [x] Keywords appear when typing at the start of a line.
- [x] Keywords like `local`, `function`, `if` are suggested.
- [x] Selecting a keyword (e.g., `local`) inserts the word followed by a space.

## Contextual Logic
- [x] After `if true `, typing `t` suggests `then`.
- [x] After `for k, v `, typing `i` suggests `in`.
- [x] Inside an `if` block, typing `e` suggests `end`, `else`, and `elseif`.
- [x] Keywords are **bolded** in the suggestion list.

## Language Levels
- [x] `goto` is suggested when the project is set to Lua 5.2+.
- [x] `goto` is NOT suggested when the project is set to Lua 5.1.

## Performance
- [x] Completion list appears instantaneously (no visible lag).
