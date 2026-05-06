# Specification: TYPE-07 External API Stubs

This document defines the requirements for supporting type definitions for external C-modules via LuaCATS stub files.

## 1. Scope

Lunar must allow users to provide `.lua` files containing only LuaCATS annotations (no logic) to describe the APIs of external libraries or the standard Lua library.

## 2. Technical Strategy

Stubs bridge indexed definitions and the active type graph.

- **Library Provider**: Bundled standard library stubs are contributed to the project scope.
- **Stub Indexing**: Exported symbols are indexed with their annotated types.
- **Graph Population**: encountering `require` triggers the injection of indexed `ValueNode`s into the local graph.

## 3. Stub Rules

### 3.1 Standard Library
Lunar provides built-in stubs for core modules (`math`, `io`, etc.) in `resources/platform/`.

### 3.2 User Libraries
Users can define stubs for native modules by creating a `.lua` file that returns a table of annotated functions.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `TYPE-07-01` | **Stdlib Stubs** | **S** | Bundle and load stubs for standard Lua libraries. |
| `TYPE-07-02` | **Platform Provider** | **S** | Implement indexing for external library stubs. |
| `TYPE-07-03` | **Require Resolution** | **S** | Resolve `require` calls to stub files when source is missing. |
| `TYPE-07-04` | **Stub Type Injection** | **S** | Inject annotated types from stubs into the active type graph. |

## 5. Examples

### 5.1 Native Library Stub
```lua
---@class MyNativeLib
local lib = {}

---@param data string
---@return boolean
function lib.process(data) end

return lib
```

### 5.2 Usage via Require
```lua
local mylib = require("mylib")
mylib.process(123) -- Warn: type mismatch for 'data'
```
