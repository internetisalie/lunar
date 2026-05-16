---
folders:
  - "[[features/completion/01-keyword-completion/requirements|requirements]]"
title: Human Verification Checklist
---

# Human Verification Checklist: COMP-01 Keyword Completion

Use this checklist to manually verify the implementation of keyword completion in the IDE.

## Basic Functionality
- [ ] Keywords appear when typing at the start of a line.
- [ ] Keywords like `local`, `function`, `if` are suggested.
- [ ] Selecting a keyword (e.g., `local`) inserts the word followed by a space.

## Contextual Logic
- [ ] After `if true `, typing `t` suggests `then`.
- [ ] After `for k, v `, typing `i` suggests `in`.
- [ ] Inside an `if` block, typing `e` suggests `end`, `else`, and `elseif`.
- [ ] Keywords are **bolded** in the suggestion list.

## Language Levels
- [ ] `goto` is suggested when the project is set to Lua 5.2+.
- [ ] `goto` is NOT suggested when the project is set to Lua 5.1.

## Performance
- [ ] Completion list appears instantaneously (no visible lag).
