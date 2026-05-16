---
folders:
  - "[[features/completion/01-keyword-completion/requirements|requirements]]"
title: Implementation Plan
---

# Implementation Plan: COMP-01 Keyword Completion

This plan breaks down the implementation of keyword completion into three logical phases.

## Phase 1: Basic Scaffolding & Unconditional Keywords [Must]

Goal: Set up the completion contributor and suggest basic keywords in common contexts.

- [ ] Create `LuaCompletionContributor` and register it in `plugin.xml`.
- [ ] Implement a basic `CompletionProvider` that adds all Lua keywords to the result set.
- [ ] Add unit tests for basic keyword visibility at the start of a file.

## Phase 2: Context-Aware Suggestions [Must]

Goal: Limit and prioritize keywords based on the syntactic context.

- [ ] Implement `atStatementStart()` context pattern.
- [ ] Implement `afterCondition()` context pattern (for `then`, `do`).
- [ ] Implement `inGenericFor()` context pattern (for `in`).
- [ ] Add unit tests for context-specific suggestions (TC-02, TC-04).

## Phase 3: Block Closure & Priority [Should]

Goal: Suggest keywords that close blocks and refine the ranking.

- [ ] Implement logic to detect open blocks and suggest `end`, `until`, `else`, `elseif`.
- [ ] Refine `LookupElement` priorities to ensure keywords don't overshadow local variables when both are valid.
- [ ] Add unit tests for block closure (TC-03).

## Verification Tasks

### Unit Tests
- `LuaKeywordCompletionTest.kt`:
    - `testBasicKeywords()`
    - `testContextualKeywords()`
    - `testBlockClosure()`

### Manual Verification
- Verify that keywords appear in the suggestion list in an actual IDE instance.
- Verify that selecting a keyword like `if` inserts a trailing space.
