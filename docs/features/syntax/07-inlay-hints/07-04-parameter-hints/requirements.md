---
folders:
  - "[[features/syntax/07-inlay-hints|hints]]"
title: "04: Parameter Name Hints"
priority: high
status: done
vf_icon: ✅
---
# Requirements: SYNTAX-07-04 Parameter Name Hints

## Overview
Show parameter names from function definitions as inline editor hints at call sites to improve readability of positional arguments.

## Scope

### In Scope
- Function calls with positional arguments.
- Resolving local and cross-file function definitions.
- Extracting names from PSI parameter lists and LuaCATS `---@param` tags.
- Applying suppression rules to reduce visual noise.

### Out of Scope
- Parameter hints for variadic arguments (`...`).
- Hints for arguments that are table constructors (often self-descriptive).
- Interactive hints (all hints are read-only).

## Requirements Table

| ID | Priority | Description | Status |
| :--- | :---: | :--- | :---: |
| **07-04-REQ-01** | [Must] | Identify `LuaCallExpr` nodes and resolve the target function. | **Full** |
| **07-04-REQ-02** | [Must] | Extract parameter names from the resolved function's PSI or LuaCATS annotations. | **Full** |
| **07-04-REQ-03** | [Must] | Render `<param>:` hints immediately before each argument expression. | **Full** |
| **07-04-REQ-04** | [Should] | Suppress hint if argument name exactly matches the parameter name. | **Full** |
| **07-04-REQ-05** | [Should] | Suppress hint if the function has only one parameter. | **Full** |
| **07-04-REQ-06** | [Must] | Suppress the implicit `self` parameter in colon calls (`obj:method()`). | **Full** |
| **07-04-REQ-07** | [Should] | Suppress hints for non-descriptive names (single characters or `_`). | **Full** |

## Test Cases (TC)

| ID | Input | Action | Expected Output |
| :--- | :--- | :--- | :--- |
| **TC-01** | `move(10, 20)` | Resolve `move(x, y)` | `move(x: 10, y: 20)` |
| **TC-02** | `move(x, y)` | Arg names match params | `move(x, y)` (Suppressed) |
| **TC-03** | `obj:method(5)` | Colon call | `obj:method(5)` (Suppressed `self`) |
| **TC-04** | `print(val)` | Single parameter | `print(val)` (Suppressed) |
| **TC-05** | `set(v)` | Single-char param | `set(v)` (Suppressed) |
