---
id: SYNTAX-14
title: "14: Vararg Context Annotator"
type: feature
parent_id: SYNTAX
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-14 Vararg Context Annotator

This document defines the requirements for verifying the valid contextual use of the vararg expression (`...`).

## 1. Scope

This specification applies to the usage of the vararg expression (`...`) within functions and the main chunk.

## 2. Rules (Lua 5.4 Section 3.4)

The vararg expression `...` can only be used when the enclosing function is explicitly defined to take a variable number of arguments (using `...` as the last parameter).

### 2.1 Main Chunk
The main chunk of a Lua script (the top-level file execution) is implicitly treated as a vararg function. Therefore, `...` is valid at the top level of a file.

### 2.2 Inside Functions
- If a function is defined as `function f(...)`, then `...` is valid inside `f`.
- If a function is defined as `function f(a, b)`, then `...` is **invalid** inside `f`.

**Examples**:
```lua
-- Valid at top level
local args = {...}

function my_vararg(...)
    print(...) -- Valid
end

function my_fixed(a)
    print(...) -- Error: cannot use '...' outside a vararg function
end

function nested(...)
    return function()
        print(...) -- Error: '...' is bound to the inner function, which is not vararg
    end
end
```

## 3. Annotator Behavior

1. **Context Resolution**: The annotator must walk up the PSI tree from a `...` expression to find the nearest enclosing function definition or the file root.
2. **Validation**: If the nearest enclosing function does not have `...` in its parameter list, an error should be reported.
3. **Error Message**: `Cannot use '...' outside a vararg function`.
