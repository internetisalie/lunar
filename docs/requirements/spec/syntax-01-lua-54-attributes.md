# Specification: SYNTAX-01 Lua 5.4 Variable Attributes

This document defines the requirements for supporting Lua 5.4 variable attributes (`<const>` and `<close>`) in the Lunar editor.

## 1. Scope

This specification applies to local variable declarations in Lua 5.4 and later.

## 2. Syntax

Variable attributes are defined using angle brackets immediately following the variable name in a `local` declaration.

**General Syntax:**
```lua
local name <attr> = expression
local name1 <attr1>, name2 <attr2> = expr1, expr2
```

### 2.1 `<const>` Attribute
Marks a variable as a constant.

- **Behavior**: The variable cannot be assigned a new value after its initial declaration.
- **Example**:
  ```lua
  local x <const> = 10
  x = 20 -- Error: attempt to assign to const variable
  ```

### 2.2 `<close>` Attribute
Marks a variable as "to-be-closed".

- **Behavior**: The value assigned to the variable must have a `__close` metamethod. When the variable goes out of scope (normal exit, `break`, `return`, or error), the metamethod is called.
- **Example**:
  ```lua
  local f <close> = io.open("test.txt")
  -- f will be closed automatically when the block ends
  ```

## 3. Constraints and Rules

### 3.1 Local Variables Only
Attributes are *only* valid for local variables. They cannot be used with global variables or table fields.
- `local x <const> = 1` (Valid)
- `x <const> = 1` (Invalid - syntax error)
- `t.x <const> = 1` (Invalid - syntax error)

### 3.2 Immediate Initialization
Variables with attributes *must* be initialized at the point of declaration.
- `local x <const> = 10` (Valid)
- `local x <const>` (Invalid - syntax error)

### 3.3 Single Attribute per Variable
A variable can have at most one attribute.
- `local x <const><close>` (Invalid - syntax error)

### 3.4 Whitespace and Formatting
Whitespace is allowed between the name and the attribute, and within the attribute brackets.
- `local x<const>` (Valid)
- `local x < const >` (Valid)

## 4. Editor Requirements

| ID | Requirement | Priority |
| :--- | :--- | :---: |
| `SYNTAX-01-01` | **Lexer Support** | **M** | Recognize `<` and `>` as attribute delimiters in the context of `local` declarations. |
| `SYNTAX-01-02` | **Parser Support** | **M** | Parse `local` declarations with optional attributes for each name. |
| `SYNTAX-01-03` | **Syntax Highlighting** | **M** | Highlight `<` and `>` as punctuation and the attribute name (`const`, `close`) as a keyword or modifier. |
| `SYNTAX-01-04` | **Validation: Const Assignment** | **S** | Provide a "Cannot assign to a constant variable" inspection/error. |
| `SYNTAX-01-05` | **Validation: To-be-closed Type** | **C** | (Optional) Warn if the assigned value is known to lack a `__close` metamethod. |
| `SYNTAX-01-06` | **Code Completion** | **S** | Suggest `const` and `close` when the cursor is inside `< >` after a local name. |

## 5. Test Cases

### Test Case: Valid Const
```lua
local pi <const> = 3.14
print(pi)
```

### Test Case: Valid Close
```lua
do
    local f <close> = setmetatable({}, { __close = function() print("closed") end })
end
-- Output: closed
```

### Test Case: Multiple Locals
```lua
local x <const>, y <close>, z = 1, my_resource, 3
```

### Test Case: Invalid Assignment (Const)
```lua
local x <const> = 10
x = 20 -- Should be flagged by static analysis
```

### Test Case: Invalid Syntax (No Initialization)
```lua
local x <const> -- Parser error
```
