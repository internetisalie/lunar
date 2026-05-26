---
id: "COMP-01"
title: "01: Keyword Completion"
type: "feature"
parent_id: "COMP"
status: "done"
priority: "high"
folders:
  - "[[features/completion/requirements|requirements]]"
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

---

# COMP-DR-01: Keyword Completion De-risking

## 1. Scope
Prototype and verify the basic infrastructure for Lua keyword completion. This task de-risks the core `CompletionContributor` implementation and context detection logic before full-scale implementation of all keywords.

### In Scope
- Verification of `LuaCompletionContributor` registration.
- Prototyping suggestions for a subset of keywords: `if`, `then`, `else`, `end`.
- Basic context detection (e.g., `then` only after `if`).
- Unit test suite for completion.

### Out of Scope
- Full list of all Lua keywords (reserved for `COMP-01`).
- Context-aware symbol completion (reserved for `COMP-02`).
- Advanced sorting and grouping (reserved for `COMP-DR-02`).

## 2. Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `COMP-DR-01-01` | **Contributor Registration** | **M** | **Full** | `LuaCompletionContributor` is registered in `plugin.xml`. |
| `COMP-DR-01-02` | **Basic Keyword Suggestions** | **M** | **Full** | Suggestions for `if`, `while`, `function`, etc. implemented. |
| `COMP-DR-01-03` | **Contextual 'then'** | **S** | **Full** | `then`, `else`, `elseif`, `end` suggested based on context. |
| `COMP-DR-01-04` | **Test Infrastructure** | **M** | **Full** | `LuaCompletionTest` base class and initial tests established. |

## 3. Test Cases

### TC-01: Statement Start
- **Input**: `<caret>`
- **Expected**: Suggestions include `if`, `while`, `function`, `local`.

### TC-02: After 'if'
- **Input**: `if true <caret>`
- **Expected**: Suggestions include `then`.

### TC-03: Inside 'if'
- **Input**: `if true then <caret>`
- **Expected**: Suggestions include `if`, `while`, etc., but NOT `then`.
