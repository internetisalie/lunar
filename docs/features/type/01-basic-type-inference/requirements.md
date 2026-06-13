---
id: "TYPE-01"
title: "01: Basic Type Inference"
type: "feature"
parent_id: "TYPE"
status: "done"
priority: "medium"
folders:
  - "[[features/type/requirements|requirements]]"
---
# Specification: TYPE-01 Basic Type Inference

This document defines the requirements for inferring basic types from Lua source code in the Lunar plugin.

## 1. Scope

Lunar must be able to infer the type of variables and expressions from their assignments and usage, even without explicit LuaCATS annotations. This is the foundation for advanced features like code completion and type checking.

## 2. Technical Strategy

Basic type inference is implemented using the **Cubic Biunification** engine.

- **Type Graph**: Builds a `LuaTypeGraph` to handle transitive flow constraints.
- **Polarization**: Splits types into `ValueNode` (producers) and `UseNode` (consumers).
- **Flow Propagation**: Transitive closure ensures that types propagate through variable "wormholes".

## 3. Inference Rules

### 3.1 Literal Assignments
Types are inferred directly from literal values assigned to variables.

### 3.2 Propagation
When a typed variable is assigned to another, the type flow is propagated to the new binding.

### 3.3 Function Returns
Types are inferred from function calls based on the function's internal return expressions or its LuaCATS `@return` tags.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-01-01` | **Literal Inference** | **M** | Infer types from simple literals: `string`, `number`, `boolean`, `nil`. |
| `TYPE-01-02` | **Table Constructor Inference** | **M** | Identify table literals as `table` type. |
| `TYPE-01-03` | **Multiple Assignment Flow** | **M** | Correctly map multiple RHS values to LHS variables. |
| `TYPE-01-04` | **Local Propagation** | **M** | Flow types through local variable assignments. |
| `TYPE-01-05` | **Return Value Inference** | **M** | Infer call-site types from function return definitions. |

## 5. Examples

### 5.1 Literal Assignments
```lua
local x = "hello" -- type: string
local y = 42      -- type: number
```

### 5.2 Multiple Assignments
```lua
local a, b = 1, "two"
-- a: number
-- b: string
```

### 5.3 Function Return Values
```lua
---@return string
local function getName() return "Lunar" end

local name = getName()
-- name: string
```
