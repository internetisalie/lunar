---
id: TYPE-06
title: "06: Return Type Checking"
type: feature
parent_id: TYPE
status: "done"
priority: "medium"
folders:
  - "[[features/type/requirements|requirements]]"
---
# Specification: TYPE-06 Return Type Checking

This document defines the requirements for validating that function implementations adhere to their declared return types.

## 1. Scope

Lunar must ensure that all `return` statements within a function body produce values compatible with the types declared in the function's `@return` tags.

## 2. Technical Strategy

Return checking is a consequence of **Polarized Flow**.

- **Exit Constraints**: `@return` tags inject `UseNode`s representing the function's required output types.
- **Value Sink**: Every `return` expression in the body flows into these exit nodes.
- **Validation**: Mismatches are identified by the engine's standard constraint solver.

## 3. Return Rules

### 3.1 Single Return
Validates that the expression in a `return` statement matches the `@return` type.

### 3.2 Multiple Returns
Validates each value in a multi-value return against the corresponding `@return` tag.

### 3.3 Implicit Returns
If no `@return` tags exist, the function's return type is inferred from the union of all return expressions.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-06-01` | **Return Value Match** | **S** | Warn if a returned expression is incompatible with `@return`. |
| `TYPE-06-02` | **Multi-return Validation** | **S** | Validate per-position types for functions with multiple return tags. |
| `TYPE-06-03` | **Return Arity Check** | **S** | Warn if the number of returned values doesn't match the signature. |
| `TYPE-06-04` | **Implementation Inference** | **S** | Infer return types for unannotated functions from their bodies. |

## 5. Examples

### 5.1 Simple Mismatch
```lua
---@return number
local function getAge()
    return "20" -- Warn: return type mismatch
end
```

### 5.2 Multiple Returns
```lua
---@return number, string
local function getInfo()
    return 1, 2 -- Warn: 2nd return value mismatch
end
```
