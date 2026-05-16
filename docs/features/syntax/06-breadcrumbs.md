---
folders:
  - "[[features/syntax/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "06: Breadcrumbs"
---
# Specification: SYNTAX-06 Breadcrumbs

This document defines the behavior and hierarchy for the Lua Breadcrumbs support in Lunar. Breadcrumbs provide a horizontal navigation bar at the top or bottom of the editor, showing the path from the file root to the current cursor position.

## 1. Scope
- **Supported Elements**: Files, Functions (Global, Local, Anonymous), and structural Table Assignments.
- **Hierarchy**: Reflects the nested lexical scope of the code.
- **Interactivity**: Clicking a breadcrumb navigates to the start of the corresponding PSI element.

## 2. Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `SYNTAX-06-01` | **File Breadcrumb** | **M** | Implemented | Show the filename as the root breadcrumb. |
| `SYNTAX-06-02` | **Function Breadcrumb** | **M** | Implemented | Show global and local functions in the path. |
| `SYNTAX-06-03` | **Method Breadcrumb** | **M** | Implemented | Show class-like methods (e.g., `Class:method`). |
| `SYNTAX-06-04` | **Assignment Breadcrumb** | **S** | Implemented | Show names for anonymous functions assigned to variables (`local f = function()`). |
| `SYNTAX-06-05` | **Table Field Breadcrumb** | **S** | Implemented | Show breadcrumbs for nested table fields if they contain functions. |
| `SYNTAX-06-06` | **Icons** | **S** | Implemented | Use appropriate icons for each element type (matching Structure View). |

## 3. Visual Representation & Icons
| Element Type | Icon | Text Label |
| :--- | :--- | :--- |
| File | `LuaIcons.FILE` | Filename |
| Global Function | `AllIcons.Nodes.Function` | Name |
| Local Function | `AllIcons.Nodes.Function` | Name |
| Method (`:`) | `AllIcons.Nodes.Method` | Name |
| Table Field | `AllIcons.Nodes.Field` | Name |

## 4. Test Cases

### Scenario 1: Nested Functions
**Input:**
```lua
function outer()
    local function inner()
        -- cursor here
    end
end
```
**Expected Breadcrumbs:**
`main.lua` > `outer` > `inner`

### Scenario 2: Object Methods
**Input:**
```lua
local MyClass = {}
function MyClass:init()
    -- cursor here
end
```
**Expected Breadcrumbs:**
`main.lua` > `MyClass:init`

### Scenario 3: Anonymous Function Assignment
**Input:**
```lua
local helper = function()
    -- cursor here
end
```
**Expected Breadcrumbs:**
`main.lua` > `helper`

## 5. Implementation Status
- **Status**: `Implemented`
- **Last Updated**: 2026-05-02
