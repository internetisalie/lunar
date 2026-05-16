---
folders:
  - "[[features/documentation/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "07: Parameter Info"
---
# Specification: DOC-07 Parameter Info

This document defines the requirements for providing Parameter Info support in the Lunar editor for Lua function calls.

## 1. Scope

Parameter Info (typically triggered by `Ctrl+P`) provides a small popup showing the expected parameters of a function while the user is typing a function call.

## 2. Information Displayed

The Parameter Info popup should show:
- The function's name and signature.
- A list of all parameters.
- The **active parameter** (the one the user is currently typing) should be highlighted.
- Types and short descriptions for each parameter (if available in LuaDoc/LuaCATS).

## 3. Editor Requirements

| ID | Requirement | Priority |
| :--- | :--- | :---: |
| `DOC-07-01` | **Popup Trigger** | **M** | Show the parameter info popup automatically after typing `(` or when explicitly requested. |
| `DOC-07-02` | **Active Parameter Tracking** | **M** | Correctly identify and highlight the current parameter based on the number of commas typed. |
| `DOC-07-03` | **Overload Support** | **S** | Display multiple signatures if the function has `@overload` annotations. |
| `DOC-07-04` | **Type Integration** | **M** | Display parameter types defined in LuaCATS within the popup. |
| `DOC-07-05` | **Vararg Support** | **S** | Correctly handle and display `...` (vararg) parameters. |

## 4. Examples

### Example: Basic Function Call
```lua
--- @param a number
--- @param b number
function add(a, b) end

add(10, |) -- | is the caret
```
**Expected Popup:**
`add(a: number, b: number)` (with `b: number` highlighted)

### Example: Function with Overloads
```lua
--- @overload fun(x: string): void
--- @param x number
function process(x) end

process(|)
```
**Expected Popup:**
- `process(x: number)`
- `process(x: string)`
