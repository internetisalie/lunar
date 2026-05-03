# Specification: NAV-04 Structure View

This document defines the expected behavior, tree hierarchy, and visual representation for the Lua Structure View in Lunar.

## 1. Core Hierarchy Rules
- The **Root Node** must always be the File node.
- **Top-level elements** (Global functions, local variables in the main chunk) are direct children of the File node.
- **Nested elements** (Local functions, parameters, local variables) appear as children of their containing block or function.

## 2. Functional Requirements

### 2.1 Implemented Features (Current)
| ID | Feature | Expected Representation | Icon |
| :--- | :--- | :--- | :--- |
| `NAV-04-01` | **File Node** | Filename (e.g., `main.lua`) | `LuaIcons.FILE` |
| `NAV-04-02` | **Global Function** | Function name (e.g., `myFunc`) | `AllIcons.Nodes.Function` |
| `NAV-04-03` | **Local Function** | Function name | `AllIcons.Nodes.Function` + (Local badge) |
| `NAV-04-04` | **Parameters** | Parameter name (nested under function) | `AllIcons.Nodes.Parameter` |
| `NAV-04-05` | **Local Variables** | Variable name (from `local x = ...`) | `AllIcons.Nodes.Variable` |
| `NAV-04-06` | **Labels** | Label name (e.g., `::loop::`) | `AllIcons.Nodes.Label` |
| `NAV-04-07` | **Return Stmt** | `return` keyword | `AllIcons.Actions.Exit` |

### 2.2 Planned Features (Missing)
| ID | Feature | Expected Representation | Priority |
| :--- | :--- | :--- | :---: |
| `NAV-04-08` | **Table Fields** | Field name (from `t = { key = value }`) | **M** |
| `NAV-04-09` | **Assigned Fields** | Field name (from `t.key = value`) | **M** |
| `NAV-04-10` | **Anon Functions** | Variable name (from `local f = function()`) | **M** |
| `NAV-04-11` | **LuaCATS Classes** | Class name (from `@class MyClass`) | **S** |
| `NAV-04-12` | **Method Selection** | Show `:method` vs `.function` distinction | **S** |

## 3. Expected Behaviors (Test Cases)

### Scenario: Nested Functions
**Input:**
```lua
function outer(p1)
    local function inner(p2)
        return p2
    end
end
```
**Expected Tree:**
- `outer` (Function)
    - `p1` (Parameter)
    - `inner` (Local Function)
        - `p2` (Parameter)
        - `return` (Return Statement)

### Scenario: Local Variable Discovery
**Input:**
```lua
local x, y = 1, 2
```
**Expected Tree:**
- `x` (Variable)
- `y` (Variable)

### Scenario: Table Structure (Planned)
**Input:**
```lua
local Config = {
    version = "1.0",
    init = function() end
}
```
**Expected Tree:**
- `Config` (Variable)
    - `version` (Field)
    - `init` (Function/Field)

## 4. Technical Constraints
- **Performance:** Tree must update incrementally to avoid UI freezes on large files (>5k lines).
- **Navigation:** Clicking an element in the tree must navigate the editor to the exact offset of the identifier.
- **Sorting:** Support alphabetical sorting and "Visibility" sorting (Global vs Local).
