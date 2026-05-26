---
id: "INSP-01"
title: "01: Undeclared Variable"
type: "feature"
parent_id: "INSP"
status: "planned"
priority: "medium"
folders:
  - "[[features/inspections/requirements|requirements]]"
---

# Undeclared Variable Requirements (`INSP-01`)

Highlights variables that are used but have no visible declaration in the current scope or project.

## Scope

- **In Scope**:
    - Highlighting identifiers in expressions that do not resolve to a local or global definition.
    - Recognition of standard Lua globals (e.g., `_G`, `string`, `print`).
    - Recognition of user-defined globals across different files in the project.
    - Handling of variables used before their `local` declaration.
- **Out of Scope**:
    - Symbols added dynamically to the global table (e.g., `_G["myVar"] = 1`).
    - Symbols provided by C-based host environments without Lua stubs.

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `INSP-01-01` | **Resolve to Local** | **M** | Variables must resolve to a valid local declaration in an accessible scope. |
| `INSP-01-02` | **Resolve to Global** | **M** | Variables not found in local scope must resolve to a global definition in the project. |
| `INSP-01-03` | **Standard Library Support** | **M** | Standard Lua globals (Lua 5.1-5.4) must not be highlighted as undeclared. |
| `INSP-01-04` | **Error Highlighting** | **M** | Unresolved variables should be highlighted with an Error or Warning severity. |
| `INSP-01-05` | **User Configuration** | **S** | Allow users to define a list of "Additional Globals" to ignore. |

## Test Cases

### TC-01: Simple Local Resolution
- **Input**:
  ```lua
  local x = 10
  print(x)
  ```
- **Action**: Run inspection.
- **Output**: No warning on `x`.

### TC-02: Undeclared Global
- **Input**:
  ```lua
  print(undeclaredVar)
  ```
- **Action**: Run inspection.
- **Output**: Warning on `undeclaredVar`: "Undeclared variable 'undeclaredVar'".

### TC-03: Used Before Local Declaration
- **Input**:
  ```lua
  print(x)
  local x = 10
  ```
- **Action**: Run inspection.
- **Output**: Warning on first `x`.

### TC-04: Standard Library Global
- **Input**:
  ```lua
  print(math.abs(-10))
  ```
- **Action**: Run inspection.
- **Output**: No warning on `print` or `math`.
