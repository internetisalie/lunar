# Specification: DOC-01 Quick Documentation (Ctrl+Q)

This document defines the requirements for providing Quick Documentation support in the Lunar editor for Lua symbols.

## 1. Scope

Quick Documentation provides a popup window with formatted information about a symbol (variable, function, table, etc.) when the user invokes the "Quick Documentation" action (usually `Ctrl+Q` or `F1`).

## 2. Content Structure

The documentation popup should contain the following sections:

### 2.1 Definition Header
Displays the symbol's signature.
- **Functions**: `function name(param1: type1, param2: type2) -> return_type`
- **Variables**: `local name: type` or `global name: type`
- **Classes/Types**: `class Name` or `type Name`

### 2.2 Description
The main text extracted from LuaDoc or LuaCATS comments associated with the symbol.

### 2.3 Tag Sections
Formatted sections for specific documentation tags:
- **Parameters**: List of parameters with their names, types, and descriptions.
- **Return Values**: List of return values with their types and descriptions.
- **Fields**: (For tables/classes) List of fields with types and descriptions.
- **See Also**: Links to other symbols or external URLs.

## 3. Extraction Rules

### 3.1 Association
Documentation is associated with a symbol if the comment block immediately precedes the symbol's declaration.

### 3.2 Formats
- **LuaDoc**: Traditional `---` comments.
- **LuaCATS**: Modern type-annotated comments starting with `---`.

## 4. Editor Requirements

| ID | Requirement | Priority |
| :--- | :--- | :---: |
| `DOC-01-01` | **Popup Trigger** | **M** | Show the documentation popup when the user presses the Quick Documentation shortcut. |
| `DOC-01-02` | **Rich Text Rendering** | **M** | Render documentation using HTML/Markdown for formatting (bold, italics, code blocks). |
| `DOC-01-03` | **Symbol Resolution** | **M** | Correctly resolve the symbol at the caret to fetch its documentation. |
| `DOC-01-04` | **Type Formatting** | **M** | Display types defined in LuaCATS in a readable format. |
| `DOC-01-05` | **Interactive Links** | **S** | Allow clicking on types or "See Also" symbols to navigate to their definitions or documentation. |
| `DOC-01-06` | **Inherited Documentation** | **S** | Support displaying documentation inherited from base classes or interfaces. |

## 5. Examples

### Example: Function Documentation
```lua
--- Adds two numbers.
--- @param a number The first number.
--- @param b number The second number.
--- @return number The sum.
function add(a, b)
    return a + b
end
```
**Expected Popup:**
- **Signature**: `function add(a: number, b: number) -> number`
- **Description**: Adds two numbers.
- **Parameters**:
  - `a`: `number` - The first number.
  - `b`: `number` - The second number.
- **Returns**: `number` - The sum.

### Example: Variable Documentation
```lua
--- @type string The user's name.
local username = "anonymous"
```
**Expected Popup:**
- **Signature**: `local username: string`
- **Description**: The user's name.
