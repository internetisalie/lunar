# Debugging & Execution Requirements (`DEBUG`, `RUN`)

Lunar provides a seamless experience for running and debugging Lua applications directly from the IDE.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-01` | **Line Breakpoints** | **M** | **Full** | Allow users to toggle breakpoints in the gutter and stop execution at specific lines. |
| `DEBUG-02` | **Stack Frames & Variables** | **M** | **Full** | Display the call stack and local/upvalue variables when paused at a breakpoint. |
| `DEBUG-03` | **Step Over/Into/Out** | **M** | **Full** | Standard execution flow control during a debug session. |
| [`DEBUG-04`](spec/debug/04-expression-evaluation.md) | **Expression Evaluation** | **S** | **Full** | Allow users to evaluate arbitrary Lua expressions in the current context. |
| `DEBUG-05` | **Remote Debugging** | **S** | **Full** | Support connecting to external Lua processes (e.g., via Mobdebug). |
| `DEBUG-06` | **Debug Target Configuration** | **S** | **Full** | Validate debug configurations before launching. |
| `DEBUG-07` | **Lazy Remote Stack Evaluation** | **S** | **Full** | Defer parsing of frame details until explicitly accessed. |
| `RUN-01` | **Lua Interpreter SDK** | **M** | **Full** | Allow configuring local Lua binaries (5.1-5.4, LuaJIT) as project SDKs. |
| `RUN-02` | **Run Configurations** | **M** | **Full** | Create and save configurations for script execution. |
| `RUN-03` | **Interactive Console (REPL)** | **S** | **Partial** | Provide a Lua REPL console within the IDE. |
| `RUN-04` | **Run Configuration Validation** | **M** | **Full** | Validate run configurations before execution. |

## Implementation Details

### DEBUG Module
- **Line Breakpoints**: `LuaLineBreakpointHandler`, `LuaLineBreakpointType`.
- **Stack & Variables**: `LuaRemoteStack`, `LuaStackFrame`, `LuaDebugVariable`.
- **Flow Control**: `LuaDebuggerController`.
- **Expression Evaluation**: `LuaDebuggerEvaluator` (two-pass parsing strategy).
- **Remote Debugging**: `LuaDebugConnection` on port 8172.

### RUN Module
- **Interpreter SDK**: `LuaInterpreter`, `LuaInterpreterFamily`.
- **Run Configurations**: `LuaRunConfiguration`.

## Test Coverage
- **`TestLuaDebugValue`**: Type checking (string, number, bool, table, error).
- **`TestLuaExecutionStack`**: Remote stack management.
- **`TestLuaStackFrame`**: Stack frame structure.
