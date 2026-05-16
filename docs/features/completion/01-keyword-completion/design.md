---
folders:
  - "[[features/completion/01-keyword-completion/requirements|requirements]]"
title: Design
---

# Technical Design: COMP-01 Keyword Completion

## Overview

Keyword completion will be implemented using the standard IntelliJ `CompletionContributor` API. It will use the `PsiElement` context at the cursor position to determine the set of valid keywords to suggest.

## Architecture

### 1. LuaCompletionContributor
The main entry point for completion. It will register a `CompletionProvider` for the `COMPLETION` type.

### 2. Context Detection
We will use `PsiElement` patterns and the `LuaParser` state (if accessible) or a simple tree-walk to determine the context:
- **Statement Start**: Start of a block or after a semicolon/newline.
- **After Condition**: After an `if`, `elseif`, `while`, or `for` expression.
- **Inside Block**: After a complete statement within a block that requires closure.

### 3. Keyword Provider
A class that adds `LookupElement`s to the `CompletionResultSet`.
- Keywords: `and`, `break`, `do`, `else`, `elseif`, `end`, `false`, `for`, `function`, `if`, `in`, `local`, `nil`, `not`, `or`, `repeat`, `return`, `then`, `true`, `until`, `while`.
- Lua 5.2+: `goto`.

## Implementation Details

### Lookup Element Creation
Keywords will be wrapped in `LookupElementBuilder`.
- Use `withBoldness(true)` for keywords.
- Use `TailType` to insert spaces or move the caret (e.g., `TailType.SPACE` after `if`, `local`).

### Context Patterns
We will define `ElementPattern<PsiElement>` to match specific locations:
- `atStatementStart()`: Matches when the previous sibling is a newline or the parent is a block/file and we are at the start.
- `afterIfCondition()`: Matches after an expression that is part of an `if` statement.

## Data Models

None required beyond standard IntelliJ API classes.

## Integration

- **Parser**: Completion relies on the PSI tree produced by `LuaParser`. If the code is broken, we should still provide best-effort suggestions based on the last valid token.
- **Settings**: No specific settings for keyword completion (it's a core feature).
