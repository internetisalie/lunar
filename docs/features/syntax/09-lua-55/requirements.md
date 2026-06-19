---
id: "SYNTAX-09"
title: "Lua 5.5 Support"
type: "feature"
status: "todo"
priority: "low"
parent_id: "SYNTAX"
folders:
  - "[[features/syntax/requirements|requirements]]"
---

# SYNTAX-09: Lua 5.5 Support

## Overview
Lua 5.5 introduces several syntactic enhancements, notably explicit `global` variable declarations and read-only for-loop variables. This feature implements parser support for the new syntax, updates the `LuaLanguageLevel` continuum, and integrates backward-compatibility inspections so users targeting older Lua versions are warned against using Lua 5.5 features. See [research.md](research.md) for findings.

## Scope

### In Scope
- Expanding the Lexer and Parser (`lua.flex` and `lua.bnf`) to support the `global` keyword and `globalVarDecl` statements.
- Adding `LuaLanguageLevel.LUA55` to the platform settings and Target implicit mappings.
- Extending `LuaLanguageLevelInspection` to flag Lua 5.5 features when the project language level is < 5.5.

### Out of Scope
- `//` comment syntax — Lua 5.5 does **not** introduce this; `//` remains the integer division operator.
- Runtime semantic verification of Lua 5.5 specific memory models or bytecode format changes.
- Named vararg tables (deferred pending syntax confirmation).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SYNTAX-09-01 | **Language Level Integration** | M | Add Lua 5.5 to `LuaLanguageLevel` and UI dropdowns. |
| SYNTAX-09-02 | **`global` Keyword Parsing** | M | Parse `global a = 5` successfully as a `LuaGlobalVarDecl`. |
| SYNTAX-09-04 | **Language Level Inspection** | M | Flag `global` declarations if `LanguageLevel < 5.5`. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 0 | SYNTAX-09-01 | UI is opened | The user opens the Project Settings | Lua 5.5 is visible and selectable |
| 1 | SYNTAX-09-02 | `global x = 10` | The user types the statement | It parses as a `LuaGlobalVarDecl` and creates a global variable reference. |
| 2 | SYNTAX-09-04 | `global x = 10` with Level 5.4 | The `LuaLanguageLevelInspection` runs | A warning states: "Global declarations are only available in Lua 5.5+". |

## Acceptance Criteria
- [ ] `LuaLanguageLevel.LUA55` exists and is selectable.
- [ ] `lua.bnf` parses `global` variable definitions.
- [ ] `LuaLanguageLevelInspection` warns on 5.5 features in < 5.5 contexts.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
