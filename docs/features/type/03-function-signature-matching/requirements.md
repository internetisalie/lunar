---
id: TYPE-03
title: "03: Function Signature Matching"
type: feature
parent_id: TYPE
status: "done"
priority: "medium"
folders:
  - "[[features/type/requirements|requirements]]"
---

# Specification: TYPE-03 Function Signature Matching

This document defines the requirements for validating function call sites against their LuaCATS signatures.

## 1. Scope

Lunar must use LuaCATS `@param` and `@return` tags to validate that function calls are correct and to provide diagnostics for mismatched arguments or missing parameters.

## 2. Technical Strategy

Signatures are validated via **Polarized Flow** and **Contravariance**.

- **Call Constraints**: A function call emits a `UseNode` with a `FunctionType` head.
- **Argument Flow**: Call-site arguments flow *backwards* (contravariant) to the definition's parameter nodes.
- **Validation**: The engine's `checkTypes` mechanism identifies reachability failures (e.g., `number` literal reaching a `string` parameter).

## 3. Validation Rules

### 3.1 Parameter Compatibility
Each argument at a call site must be assignable to the corresponding parameter type.

### 3.2 Arity Checking
Warn if too many or too few arguments are passed, taking optional parameters into account.

### 3.3 Optional Parameters
Parameters marked with `?` are considered compatible with `nil` or being omitted.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-03-01` | **Parameter Type Check** | **M** | Warn on mismatched argument types in calls. |
| `TYPE-03-02` | **Arity Validation** | **M** | Warn on wrong number of arguments for fixed-signature functions. |
| `TYPE-03-03` | **Optional Support** | **M** | Correctly handle parameters with `?` suffix. |
| `TYPE-03-04` | **Vararg Matching** | **S** | Validate arguments against `...` parameters. |

## 5. Examples

### 5.1 Basic Validation
```lua
---@param name string
---@param age number
local function greet(name, age) end

greet("Lunar", 1)   -- OK
greet(1, "Lunar")   -- Warn: type mismatch
```

### 5.2 Optional Parameters
```lua
---@param name string
---@param age? number
local function greet(name, age) end

greet("Lunar") -- OK
```
