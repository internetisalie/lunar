---
folders:
  - "[[features/completion/requirements|requirements]]"
title: "01: Keyword Completion"
---

# Keyword Completion Requirements (`COMP-01`)

Keyword completion provides context-aware suggestions for Lua reserved words.

## Scope

- **In Scope**:
    - Suggesting keywords based on the immediate syntactic context (e.g., `then` after `if`).
    - Suggesting keywords to close blocks (e.g., `end` after `do`).
    - Supporting all Lua 5.1-5.4 keywords.
- **Out of Scope**:
    - Live templates or snippets (covered by `COMP-07`).
    - Postfix templates (covered by `COMP-06`).

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-01-01` | **Basic Keyword Suggestions** | **M** | Suggest all Lua keywords when appropriate in a general context. |
| `COMP-01-02` | **Block Closure Suggestions** | **M** | Suggest `end`, `until`, `else`, `elseif` based on open blocks. |
| `COMP-01-03` | **Statement Contextual Suggestions** | **M** | Suggest `then` after `if`/`elseif`, `do` after `while`/`for`, `in` in generic `for`. |
| `COMP-01-04` | **Visibility & Priority** | **S** | Ensure keywords are prioritized appropriately against other symbols. |

## Test Cases

### TC-01: Statement Start
- **Input**: Cursor at start of line.
- **Action**: Press `Ctrl+Space`.
- **Output**: Suggestions include `local`, `function`, `if`, `while`, `for`, `repeat`, `do`, `return`, `break`.

### TC-02: After If Expression
- **Input**: `if true |`
- **Action**: Press `Ctrl+Space`.
- **Output**: `then` is the top suggestion.

### TC-03: Block Closure
- **Input**:
  ```lua
  if true then
    print("hi")
    |
  ```
- **Action**: Press `Ctrl+Space`.
- **Output**: `end`, `else`, `elseif` are suggested.

### TC-04: Generic For
- **Input**: `for k, v |`
- **Action**: Press `Ctrl+Space`.
- **Output**: `in` is suggested.
