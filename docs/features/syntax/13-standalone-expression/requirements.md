---
id: SYNTAX-13
title: "13: Standalone Expression Annotator"
type: feature
parent_id: SYNTAX
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-13 Standalone Expression Annotator

This document defines the requirements for identifying and flagging expressions that are incorrectly used as standalone statements in Lua.

## 1. Scope

This specification applies to expression statements at the block level.

## 2. Rules (Lua 5.4 Section 3.3.1 & 3.3.6)

In Lua, only certain expressions can act as statements. Specifically:
1. **Assignments**: `a = 1`, `t[k] = v`
2. **Function Calls**: `print("hello")`, `obj:method()`

Other expressions, such as arithmetic operations, logical operations, relational operations, or plain variable references, cannot be used as statements.

**Examples of Invalid Standalone Expressions**:
```lua
x + 1 -- Error: expression is not a statement
a == b -- Error: expression is not a statement
(print) -- Error: expression is not a statement (evaluates to a function, but doesn't call it)
```

**Examples of Valid Statements**:
```lua
x = x + 1 -- Assignment
math.random() -- Function call
```

## 3. Implementation Details

The requirement is implemented via `LuaStandaloneExpressionAnnotator`.

### 3.1. Annotator Logic
1. The annotator targets `LuaExprStatement` PSI elements.
2. If the expression within the statement is not a `LuaFuncCall`, it is flagged with an error.
3. `LuaAssignmentStatement` is handled separately by the parser and is not considered a `LuaExprStatement`.

### 3.2. Verification
Verified via `TestStandaloneExpression`, which covers:
- Valid assignments (no error)
- Valid function calls (no error)
- Invalid arithmetic expressions (error)
- Invalid variable references (error)
- Invalid parenthesized expressions (error)
