---
folders:
  - "[[features/documentation/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "04: Documentation Generation"
---
# Specification: DOC-04 Documentation Generation

This document defines the requirements for automatic generation of LuaCATS/LuaDoc documentation boilerplate in the Lunar editor.

## 1. Scope

Documentation generation helps developers quickly create standard documentation comments for functions, classes, and other symbols by extracting information from their signatures.

## 2. Triggering Mechanisms

### 2.1 Typing Trigger
Automatically insert a boilerplate when the user types `---` above a function or class and presses `Enter`.

### 2.2 Intention Action (Alt+Enter)
Provide a "Generate LuaCATS documentation" intention when the caret is on a function or class definition.

## 3. Boilerplate Content

The generated boilerplate should include:
- A placeholder for the description.
- `@param` tags for each parameter in the function signature.
- `@return` tag if the function is likely to return a value.
- `@class` or `@type` tags where appropriate.

## 4. Editor Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DOC-04-01` | **Boilerplate Insertion** | **M** | **Full** | Insert a template comment block above the target symbol. |
| `DOC-04-02` | **Parameter Extraction** | **M** | **Full** | Automatically include `@param` tags matching the function's parameter names. |
| `DOC-04-03` | **Template Editing** | **M** | **Full** | Use live templates or tab stops to allow the user to quickly fill in descriptions and types. |
| `DOC-04-04` | **Type Inference (Basic)** | **S** | **Full** | Attempt to infer parameter types based on usage or common patterns (e.g., `name` -> `string`, `count` -> `number`). |
| `DOC-04-05` | **Return Tag Detection** | **S** | **Full** | Include a `@return` tag if the function body contains `return` statements. |

## 5. Examples

### Example: Function Generation
**Input:**
```lua
function calculate_area(width, height)
    return width * height
end
```
**Trigger**: Type `---` and `Enter` above `function`.

**Output (Template):**
```lua
--- [Description]
--- @param width [type] [description]
--- @param height [type] [description]
--- @return [type] [description]
function calculate_area(width, height)
    return width * height
end
```

### Example: Class Generation (Local Table)
**Input:**
```lua
local Player = {}
```
**Trigger**: Intention action "Generate documentation".

**Output (Template):**
```lua
--- @class Player
local Player = {}
```
