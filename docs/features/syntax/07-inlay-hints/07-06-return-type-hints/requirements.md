---
id: "SYNTAX-07-06"
title: "06: Return Type Hints"
type: "feature"
parent_id: "SYNTAX-07"
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/07-inlay-hints|hints]]"
---
# Requirements: SYNTAX-07-06 Return Type Hints

## Overview
Show inferred return types as inline editor hints at function definition sites (after the closing parenthesis of the parameter list) when explicit LuaCATS return annotations are missing. This improves code readability by making implicit return types visible.

## Scope

### In Scope
- Local, global, and method function definitions.
- Functions containing at least one `return` statement with a typed expression or literal value.
- Support for multiple return values (e.g., `: type1, type2`).
- Inference based on the internal type system (`LuaTypesVisitor`).
- Suppression when an explicit `---@return` annotation is present.

### Out of Scope
- Functions with no `return` statements or only empty `return` statements.
- Functions where the return expression(s) cannot be statically typed.
- Interactive hints (all hints are read-only).

## Requirements Table

| ID | Priority | Description | Status |
| :--- | :---: | :--- | :---: |
| **07-06-REQ-01** | [Must] | Identify function definition nodes (`LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaFuncDef`). | **Full** |
| **07-06-REQ-02** | [Must] | Analyze `return` statements within the function body to infer the return type(s). | **Full** |
| **07-06-REQ-03** | [Must] | Render `: <type>` hints immediately after the closing `)` of the function parameter list. | **Full** |
| **07-06-REQ-04** | [Must] | Render `: <type1>, <type2>` hints for multiple return types. | **Full** |
| **07-06-REQ-05** | [Must] | Suppress hint if an explicit `---@return` annotation is present on the function. | **Full** |
| **07-06-REQ-06** | [Should] | Suppress hint if the function has no `return` statement or only empty returns. | **Full** |
| **07-06-REQ-07** | [Should] | Suppress hint if the return type resolves to `unknown` or cannot be determined. | **Full** |

## Test Cases (TC)

| ID | Input | Action | Expected Output |
| :--- | :--- | :--- | :--- |
| **TC-01** | `local function double(n) return n * 2 end` | Infer literal math type | `local function double(n)` with `: number` hint after `(n)` |
| **TC-02** | `local function pair() return 1, "one" end` | Infer multiple return types | `local function pair()` with `: integer, string` hint after `()` |
| **TC-03** | `---@return number`<br>`local function get() return 5 end` | Explicit `@return` annotation | Hint suppressed |
| **TC-04** | `local function log(msg) print(msg) end` | No return statement | Hint suppressed |
| **TC-05** | `local function foo() return some_untyped() end` | Unknown return type | Hint suppressed |
