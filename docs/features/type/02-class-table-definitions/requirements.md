---
id: TYPE-02
title: "02: Class/Table Definitions"
type: feature
parent_id: TYPE
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/type/requirements|requirements]]"
---
# Specification: TYPE-02 Class/Table Definitions

This document defines the requirements for supporting structured types via LuaCATS `@class`, `@alias`, and `@field` tags.

## 1. Scope

Lunar must support formal type definitions using LuaCATS to enable object-oriented patterns and complex table structures. This includes indexing, inheritance, and field discovery.

## 2. Technical Strategy

Structured types are integrated into the **Cubic Biunification** engine.

- **Structural Subtyping**: Compatibility is determined by graph reachability between value nodes and use-site requirements.
- **Indexing**: A `LuaTypeIndex` provides cross-file resolution for named classes and aliases.
- **Field Slots**: `TableType` nodes manage per-key `VariableNode` slots for properties.

## 3. Structured Type Rules

### 3.1 Class Definitions
`@class` tags define named table structures that can be referenced elsewhere.

### 3.2 Inheritance
Inheritance is modeled as a flow edge from the child class to the parent's structural constraints.

### 3.3 Aliases
`@alias` provides shortcuts for existing types or complex union definitions.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-02-01` | **Class Tag Parsing** | **M** | Resolve and index `@class` declarations. |
| `TYPE-02-02` | **Alias Tag Parsing** | **M** | Resolve and index `@alias` declarations. |
| `TYPE-02-03` | **Inheritance Resolution** | **M** | Resolve members from parent classes via `: Parent` syntax. |
| `TYPE-02-04` | **Field Tag Support** | **M** | Track members defined via `@field` tags. |
| `TYPE-02-05` | **Implicit Field Discovery** | **S** | Identify fields from assignments like `ClassName.field = val`. |

## 5. Examples

### 5.1 Class Inheritance
```lua
---@class Entity
---@field id number

---@class Player : Entity
---@field name string

local p = {} ---@type Player
-- p.id: number
-- p.name: string
```

### 5.2 Aliases
```lua
---@alias Color "red" | "green" | "blue"

---@type Color
local c = "red"
```

## 6. Test Cases

> Note: TYPE-02-01..04 are already implemented in `LuaTypeManagerImpl`; these are regression
> + the new implicit-field case.

### TC-TYPE-02-03: Inheritance (regression)
- **Input**: requirements §5.1 (`Player : Entity`).
- **Action**: `resolveType("Player", ctx).resolveMember("id")`.
- **Output**: resolves to `number` (inherited from `Entity`).

### TC-TYPE-02-02: Alias (regression)
- **Input**: requirements §5.2.
- **Action**: `resolveType("Color", ctx)`.
- **Output**: a union of the three string-literal types.

### TC-TYPE-02-05: Implicit Fields (NAV TYPE-02-05)
- **Input**:
  ```lua
  ---@class Player
  local Player = {}
  function Player:heal() self.hp = 1 end
  Player.maxHp = 100
  ```
- **Action**: `resolveType("Player", ctx).getMembers()`.
- **Output**: contains both `hp` (from `self.hp`) and `maxHp` (from `Player.maxHp`); an explicit
  `@field hp string` would take precedence over the implicit one.
