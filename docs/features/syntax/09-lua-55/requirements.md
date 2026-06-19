---
id: "SYNTAX-09"
title: "Lua 5.5 Support"
type: "feature"
status: "planned"
priority: "low"
parent_id: "SYNTAX"
folders:
  - "[[features/syntax/requirements|requirements]]"
---

# SYNTAX-09: Lua 5.5 Support

## Overview
Lua 5.5 introduces several syntactic enhancements, notably explicit `global` variable declarations, read-only for-loop variables, and `//` single-line comments. This feature implements parser support for the new syntax, updates the `LuaLanguageLevel` continuum, and integrates backward-compatibility inspections so users targeting older Lua versions are warned against using Lua 5.5 features.

## Scope

### In Scope
- Expanding the Lexer and Parser (`lua.flex` and `lua.bnf`) to support the `global` keyword and `globalVarDecl` statements.
- Supporting `//` as an alternative single-line comment initiator.
- Adding `LuaLanguageLevel.LUA55` to the platform settings and Target implicit mappings.
- Extending `LuaLanguageLevelInspection` to flag Lua 5.5 features when the project language level is < 5.5.

### Out of Scope
- Runtime semantic verification of Lua 5.5 specific memory models or bytecode format changes.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SYNTAX-09-01 | **Language Level Integration** | M | Add Lua 5.5 to `LuaLanguageLevel` and UI dropdowns. |
| SYNTAX-09-02 | **`global` Keyword Parsing** | M | Parse `global a = 5` successfully as a `LuaGlobalVarDecl`. |
| SYNTAX-09-03 | **`//` Comment Parsing** | M | Parse `// comment` identically to `-- comment`. |
| SYNTAX-09-04 | **Language Level Inspection** | M | Flag `global` declarations and `//` comments if `LanguageLevel < 5.5`. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 0 | SYNTAX-09-01 | UI is opened | The user opens the Project Settings | Lua 5.5 is visible and selectable |
| 1 | SYNTAX-09-02 | `global x = 10` | The user types the statement | It parses as a `LuaGlobalVarDecl` and creates a global variable reference. |
| 2 | SYNTAX-09-03 | `// hello world` | The user types the statement | It parses as a `SHORTCOMMENT` element. |
| 3 | SYNTAX-09-04 | `global x = 10` with Level 5.4 | The `LuaLanguageLevelInspection` runs | A warning states: "Global declarations are only available in Lua 5.5+". |

## Acceptance Criteria
- [ ] `LuaLanguageLevel.LUA55` exists and is selectable.
- [ ] `lua.bnf` parses `global` variable definitions.
- [ ] `lua.flex` successfully lexes `//` as a short comment.
- [ ] `LuaLanguageLevelInspection` warns on 5.5 features in < 5.5 contexts.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
