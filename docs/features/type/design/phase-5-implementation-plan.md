# Phase 5 Implementation: Union types, Generics & Cyclic Resolution

**Status**: ✅ IMPLEMENTED  
**Related Requirements**: [`TYPE-04`](../04-union-types.md), [`TYPE-05`](../05-generics-support.md)
**Date Completed**: 2026-05-07

---

## 1. Scope

Phase 5 completes the core Type Inference Engine by adding support for Union types, Generic resolution, and ensuring safety when resolving cyclic type structures.

### 1.1 Included
- **Union Types**: Parsing and graph representation of `A | B`.
- **Generic Resolution**: Let-polymorphism for functions with `@generic`.
- **Cyclic Safety**: visited-tracking in `fromLuaType` and `checkCompatibility`.
- **Distributive Compatibility**: Logic for checking unions against other types.

### 1.2 Excluded
- **Complex Generics**: Generic constraints (e.g., `T : string`) are deferred to Phase 6+.
- **Generic Classes**: Only function generics are supported in this phase.
- **Overload Resolution**: Deferred to Phase 6+.

---

## 2. Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| `TYPE-05-01` | **Union Graph Head** | **M** | **Implemented** | Implement `LuaGraphType.Union` and its display logic. |
| `TYPE-05-02` | **Union Compatibility** | **M** | **Implemented** | Implement distributive rules for Union flow checking. |
| `TYPE-05-03` | **Generic Graph Head** | **M** | **Implemented** | Implement `LuaGraphType.Generic` for template parameters. |
| `TYPE-05-04` | **Call-site Instantiation** | **M** | **Implemented** | Create fresh variable nodes for generics at call sites. |
| `TYPE-05-05` | **Cyclic Resolution Safety** | **M** | **Implemented** | Prevent StackOverflow in `fromLuaType` via recursion guards. |
| `TYPE-05-06` | **Recursive Compatibility** | **M** | **Implemented** | Prevent infinite loops in `checkCompatibility` for recursive structures. |

---

## 3. Implementation Strategy

### 3.1 `fromLuaType` Recursion Guard
Update `LuaGraphType.fromLuaType` to accept a `MutableMap<LuaType, LuaGraphType>` to track already-visited types. Structural types (Tables, Functions) must be registered in the map *before* resolving their internal components.

### 3.2 Distributive Union Flow
In `checkCompatibility`, implement the following logic:
- `Value(Union(A | B)) ≤ Use(T)`: Compatible if `A ≤ T` AND `B ≤ T`.
- `Value(T) ≤ Use(Union(A | B))`: Compatible if `T ≤ A` OR `T ≤ B`.

### 3.3 Generic Instantiation
When visiting a `LuaFuncCall`:
1. Check if the callee's `FunctionType` contains `Generic` parameters.
2. If so, create a mapping from `Generic` names to fresh `VariableNode`s.
3. Substitute these nodes in a cloned `FunctionType`.
4. Flow arguments into the substituted parameters.

---

## 4. Test Cases

### 4.1 Union Type Check
```lua
---@type string | number
local x = "hello" -- OK
x = 42            -- OK
x = true          -- Error: boolean not assignable to string | number
```

### 4.2 Generic Identity
```lua
---@generic T
---@param val T
---@return T
local function identity(val) return val end

local s = identity("hello") -- s should be string
local n = identity(42)      -- n should be number
```

### 4.3 Cyclic Resolution
```lua
---@class Node
---@field next Node
local Node = {}

---@type Node
local n = {}
local x = n.next -- Should not StackOverflow
```

---

## 5. Verification Plan

1. **Unit Tests**: Create `TestLuaTypeCheckPhase5.kt` covering the above scenarios.
2. **Regression**: Run all existing tests (Phases 1-4).
3. **Performance**: Verify that large union/generic sets do not cause noticeable lag.
