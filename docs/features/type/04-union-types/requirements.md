---
id: TYPE-04
title: "04: Union Types"
type: feature
parent_id: TYPE
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/type/requirements|requirements]]"
---
# Specification: TYPE-04 Union Types

This document defines the requirements for supporting variables that can hold multiple types using LuaCATS union syntax.

## 1. Scope

Lunar must support union types (`type1 | type2`) to handle Lua's dynamic nature where a variable's type may vary depending on execution paths or initializations.

## 2. Technical Strategy

Union types are natively supported via **Graph Disjunctions**.

- **Disjunction Nodes**: The type graph creates nodes that receive flows from multiple distinct sources.
- **Lazy Materialization**: The actual union is computed only when needed (e.g., for display) by aggregating all value flows into the "wormhole".
- **Member Access**: Property access on a union type propagates constraints to all constituent members.

## 3. Union Rules

### 3.1 Explicit Unions
Variables can be explicitly annotated with `|` separated types in LuaCATS.

### 3.2 Merged Branches
Logic branches (like `if`) that result in different types for the same variable effectively create a union for subsequent usages.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-04-01` | **Union Tag Support** | **S** | Support `|` syntax in `@type`, `@param`, and `@return`. |
| `TYPE-04-02` | **Disjunction Flow** | **S** | Correctly flow multiple source types into a single variable node. |
| `TYPE-04-03` | **Logical OR Inference** | **S** | Infer union type from the result of `expr1 or expr2`. |
| `TYPE-04-04` | **Union Member Access** | **S** | Resolve properties that exist on any/all members of a union. |

## 5. Examples

### 5.1 Union Annotation
```lua
---@type string | number
local id

id = "A1" -- OK
id = 123  -- OK
id = true -- Warn: type mismatch
```

### 5.2 Logical OR
```lua
local x = "name" or 10
-- x: string | number
```
