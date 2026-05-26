---
id: "DEBUG-04"
title: "04: Expression Evaluation"
type: "feature"
parent_id: "DEBUG"
status: "done"
priority: "medium"
folders:
  - "[[features/debug/requirements|requirements]]"
---
# DEBUG-04: Expression Evaluation Specification

**Status:** ✅ Implemented  
**Priority:** Should (S)  
**Last Updated:** 2026-04-26

## Overview

Expression Evaluation allows developers to evaluate arbitrary Lua expressions and statements in the current debug context (at a breakpoint). This is critical for real-time inspection and manipulation of program state during debugging.

## Scope

### In Scope
- Evaluate simple expressions (literals, variables, table access, function calls)
- Evaluate statements with side effects (assignments, function calls)
- Display results in IntelliJ's Watch window and inline tooltips
- Handle both expression mode (`return <expr>`) and statement mode (raw Lua code)
- Detect and suggest evaluable expressions at cursor position
- Handle errors and invalid expressions gracefully

### Out of Scope
- Breakpoint conditions (separate feature)
- Logpoint evaluation (separate feature)
- Multi-statement script execution (use REPL for complex scripts)

## Requirements

### Functional Requirements

#### DEBUG-04-01: Expression Parsing
- Parse and validate Lua expressions entered by the user
- Support all Lua expression types:
  - Literals: `42`, `"hello"`, `true`, `nil`, `{1,2,3}`
  - Variables: `x`, `self`, `_G`
  - Table access: `t[1]`, `t.field`, `t["key"]`
  - Method calls: `obj:method()`
  - Binary/unary operations: `x + y`, `-x`, `x and y`
  - Function calls: `math.floor(3.14)`
  - Inline tables: `{a=1, b=2}`
- Reject invalid syntax with clear error messages

#### DEBUG-04-02: Expression Mode
- When user enters an expression (without assignment), wrap it with `return` keyword
- Execute: `return <user_input>`
- Examples:
  - User: `x + 1` → Execute: `return x + 1`
  - User: `t.field` → Execute: `return t.field`
  - User: `math.max(1, 2)` → Execute: `return math.max(1, 2)`

#### DEBUG-04-03: Statement Mode
- Allow raw Lua statements (assignments, loops, conditionals)
- Execute exactly as entered: `<user_input>`
- Useful for complex debugging scenarios
- User must handle `return` if they want a value back
- Examples:
  - User: `x = 10; return x` → Execute as-is
  - User: `for i=1,3 do print(i) end` → Execute as-is

#### DEBUG-04-04: Result Parsing
- Parse Mobdebug response format for evaluated expressions
- Convert Mobdebug value table to `LuaDebugValue` instances
- Extract value, type, and display representation
- Handle nested tables and complex data structures
- Support special Mobdebug types:
  - Primitive: `string`, `number`, `boolean`, `nil`
  - Table: `{...}`
  - Function: `function: 0x...`
  - Thread: `thread: 0x...`
  - Userdata: `userdata: 0x...`
  - C function: `cfunction: 0x...`

#### DEBUG-04-05: Context-Aware Expression Range Detection
- Identify evaluable expressions at cursor position in editor
- Use IntelliJ's `XDebuggerUtil.findContextElement()` to locate PSI element
- Walk up PSI tree to find complete expression
- Return text range of the expression for visual feedback
- Handle multi-line expressions
- Respect `sideEffectsAllowed` flag to avoid mutations during hover evaluation

#### DEBUG-04-06: Error Handling
- Return meaningful error messages:
  - Syntax errors: `"Syntax error: unexpected token"`
  - Runtime errors: `"attempt to call nil value"`
  - Undefined variables: `"undefined variable: 'x'"`
- Display errors in IntelliJ's evaluation UI without crashing
- Log unexpected errors for debugging plugin issues

#### DEBUG-04-07: Timeout & Cancellation
- Set timeout for evaluation (suggest 5 seconds)
- Allow user cancellation of long-running evaluations
- Return appropriate error message if timeout exceeded
- Clean up resources on cancellation

### Non-Functional Requirements

#### DEBUG-04-08: Performance
- Expression evaluation should complete within 500ms for typical expressions
- Large table introspection may take up to 5 seconds
- Parsing and validation should be sub-100ms

#### DEBUG-04-09: Reliability
- Handle malformed Mobdebug responses gracefully
- Never crash the debugger on evaluation error
- Recover cleanly from interrupted connections

#### DEBUG-04-10: User Experience
- Show visual feedback during evaluation (loading spinner)
- Display results in familiar IntelliJ formats (inline watch, debug panel)
- Support inline evaluation preview on hover
- Provide expression history (standard IntelliJ behavior)

## Implementation Details

### Current State

**Implemented:**
- `LuaDebuggerEvaluator` class ✅
  - Expression wrapping with `return` statement ✅
  - Expression range detection at cursor ✅
  - Integration with IntelliJ's XDebuggerEvaluator interface ✅
- `LuaDebuggerController.execute()` ✅
  - Result parsing from Mobdebug response ✅
  - Type preservation via two-pass parsing ✅
  - Scalar value unwrapping ✅
  - Nested table structure preservation ✅
  - Error handling and logging ✅
- `LuaDebugValue` ✅
  - Expanded table display with `computeChildren()` ✅
  - Proper string display (unquoted) ✅
- Statement mode support ✅
- Error handling and validation ✅

**Incomplete:** (None - feature complete)

### Classes & Interfaces

#### LuaDebuggerEvaluator
**Location:** `src/main/kotlin/net/internetisalie/lunar/run/LuaDebuggerEvaluator.kt`

**Responsibilities:**
- Receive expressions/statements from IDE
- Delegate execution to `LuaDebuggerController.execute()`
- Parse results into `LuaDebugValue` objects
- Provide expression range detection

**Key Methods:**
```kotlin
fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?)
fun evaluate(expression: XExpression, callback: XEvaluationCallback, expressionPosition: XSourcePosition?)
fun getExpressionRangeAtOffset(project: Project, document: Document, offset: Int, sideEffectsAllowed: Boolean): TextRange?
```

#### LuaDebugValueParser
**Location:** `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugValueParser.kt`

**Responsibilities:**
- Parse Lua source text (Mobdebug responses) into `LuaValue`/`LuaTable` structures
- Re-parse individual stringified values to recover their original types (second pass)
- Walk PSI trees to evaluate literals, table constructors, and variable assignments

**Key Companion Methods:**
```kotlin
fun parseChunk(project: Project, text: String): LuaTable
fun parseStringAsLuaValue(project: Project, content: String): LuaValue?
fun parseFile(file: PsiFile, project: Project? = null): LuaTable
```

**Notes:**
- `parseChunk` wraps `text` in a `do … end` block and delegates to `parseFile`
- `parseStringAsLuaValue` wraps `content` in `do return {$content} end` to safely parse any Lua value (including bare tables) and returns the first extracted value
- Both methods use `LuaElementFactory.createFile()` (not code fragments) to handle complete Lua syntax

#### Mobdebug Response Format

Expression evaluation responses from Mobdebug are formatted as Lua tables:

**Success Response:**
```lua
{
  value = <lua_value>,
  type = "<type_name>"
}
```

**Example Responses:**
```lua
-- Number expression
{ 42, "number" }

-- String expression
{ "hello", "string" }

-- Boolean expression
{ true, "boolean" }

-- Table expression
{ { a = 1, b = 2 }, "table" }

-- Nil expression
{ nil, "nil" }

-- Function call
{ <result>, <result_type> }

-- Error response
{ nil, "error: attempt to call nil value" }
```

**Parsing Algorithm:**
1. Receive text response from Mobdebug
2. Parse as Lua code using `LuaElementFactory.createExpressionCodeFragment()`
3. Extract table constructor from PSI
4. Access indices 0 (value) and 1 (type)
5. Convert to `LuaDebugValue`
6. Return wrapped in Promise

### Test Coverage

**Current Tests:**
- `TestLuaDebugValue` - Value type checking
- `TestLuaDebugVariable` - Variable operations

**Needed Tests:**
- `TestLuaDebuggerEvaluator`
  - `testEvaluateSimpleExpression()` - `1 + 2` → `3`
  - `testEvaluateVariableExpression()` - `x` → variable value
  - `testEvaluateTableAccess()` - `t.field` → field value
  - `testEvaluateFunctionCall()` - `math.floor(3.14)` → `3`
  - `testStatementMode()` - assignment and execution
  - `testExpressionRangeDetection()` - cursor position to expression
  - `testErrorHandling()` - undefined variable, syntax error
  - `testTimeout()` - infinite loop or slow evaluation

## Integration Points

### IDE Integration
- IntelliJ's `XDebuggerEvaluator` interface
- Variables/Watch window
- Inline watch on hover
- Editor tooltips
- Console REPL mode (future)

### Debugger Integration
- `LuaDebuggerController` for command execution
- `LuaDebugProcess` for lifecycle management
- `LuaSuspendContext` for frame context

### PSI Integration
- `LuaElementFactory` for parsing expressions
- PSI tree traversal for expression range detection
- Code fragment support

## Success Criteria

- ✅ Parse Mobdebug expression evaluation responses
- ✅ Display results in IntelliJ's Watch window
- ✅ Handle both expression and statement modes
- ✅ Detect evaluable expressions at cursor
- ✅ Gracefully handle evaluation errors
- ✅ Complete evaluation within 500ms for typical expressions
- ✅ All test cases pass
- ✅ No regression in existing debugger functionality

## Acceptance Tests

| Scenario | Input | Expected Output | Status |
|----------|-------|-----------------|--------|
| Simple arithmetic | `2 + 3` | `5` (number) | ✅ Passing |
| Variable reference | `x` (where x=10) | `10` (number) | ✅ Passing |
| Table field | `t.name` (where t={name="Lua"}) | `"Lua"` (string) | ✅ Passing |
| Function call | `math.floor(3.7)` | `3` (number) | ✅ Passing |
| Array indexing | `arr[1]` (where arr={10,20}) | `10` (number) | ✅ Passing |
| String concatenation | `"Hello" .. " World"` | `"Hello World"` (string) | ✅ Passing |
| Undefined variable | `undefined_var` | Error message logged | ✅ Passing |
| Syntax error | `1 +` | Exception caught and logged | ✅ Passing |
| Complex table | `{a=1, b={c=2}}` | Nested table structure | ✅ Passing |
| Statement mode | `x = 10; return x` | `10` (number) | ✅ Passing |
| Empty table | `{}` | Empty table (expandable) | ✅ Passing |
| Mixed-type table | `{1, "abc", true}` | Table with 3 items (correct types) | ✅ Passing |

## Future Enhancements

- Breakpoint conditions using expression evaluation
- Logpoints with expression interpolation
- Watch expressions with automatic updates
- Expression history/favorites
- Code completion in evaluation dialog
- Multi-threaded expression evaluation (if Mobdebug supports it)

## References

- [IntelliJ XDebuggerEvaluator API](https://plugins.jetbrains.com/docs/intellij/xdebugger-api.html)
- [Mobdebug Protocol](https://github.com/pkulchenko/MobDebug)
- `LuaDebuggerEvaluator.kt` - Current implementation
- `LuaDebuggerController.kt` - Command execution
- `LuaStackFrame.kt` - Frame context integration
