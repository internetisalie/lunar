---
folders:
  - "[[features/type/requirements|requirements]]"
priority: high
status: done
vf_icon: ✅
title: "05: Generics Support"
---
# Specification: TYPE-05 Generics Support

This document defines the requirements for supporting polymorphic functions and classes using LuaCATS `@generic` tags.

## 1. Scope

Generics allow for the creation of reusable Lua components that maintain type safety by binding type variables at the call site.

## 2. Technical Strategy

Generics are implemented using **Let-Polymorphism**.

- **Generalization**: Function definitions with `@generic` tags create type templates.
- **Instantiation**: Each call site generates fresh nodes for generic parameters.
- **Binding**: Argument types at the call site flow into the instantiated nodes to "lock" the types for that specific invocation.

## 3. Generic Rules

### 3.1 Generic Parameters
Type variables defined in `@generic` can be used in `@param` and `@return` tags of the same block.

### 3.2 Type Inference
The return type of a generic function is derived from the actual types of the arguments passed to its generic parameters.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-05-01` | **Generic Tag Parsing** | **S** | Resolve `@generic` tags and their scope. |
| `TYPE-05-02` | **Template Generalization** | **S** | Model generic functions as templates in the type graph. |
| `TYPE-05-03` | **Call-site Instantiation** | **S** | Create fresh type nodes for generic variables at each call site. |
| `TYPE-05-04` | **Generic Classes** | **C** | Support generic type parameters on `@class` definitions. |

## 5. Examples

### 5.1 Generic Identity
```lua
---@generic T
---@param val T
---@return T
local function identity(val) return val end

local s = identity("hello") -- s: string
local n = identity(123)     -- n: number
```

### 5.2 Multi-type Generics
```lua
---@generic K, V
---@param key K
---@param value V
---@return table<K, V>
local function makePair(key, value) end
```

## 6. References
- [LuaLS Generics Super Issue Analysis](design/luals-generics-super-issue.md)

