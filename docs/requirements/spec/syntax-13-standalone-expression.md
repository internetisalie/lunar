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

## 3. Annotator Behavior

1. **Flagging**: The parser or annotator must identify when an expression that is not a function call or an assignment appears at the statement level.
2. **Error Message**: The element should be highlighted with a syntax error, such as: `Expression cannot be used as a statement`.
3. **Context**: This must gracefully handle the ambiguity around function calls, particularly ensuring that valid calls enclosed in parentheses (if supported syntactically in the context) or chained calls are recognized as statements.
