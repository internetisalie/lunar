# Debugging & Execution Requirements (`DEBUG`, `RUN`)

Lunar provides a seamless experience for running and debugging Lua applications directly from the IDE.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-01` | **Line Breakpoints** | **M** | **Full** | Allow users to toggle breakpoints in the gutter and stop execution at specific lines. |
| `DEBUG-02` | **Stack Frames & Variables** | **M** | **Full** | Display the call stack and local/upvalue variables when paused at a breakpoint. |
| `DEBUG-03` | **Step Over/Into/Out** | **M** | **Full** | Standard execution flow control during a debug session. |
| [`DEBUG-04`](./04-expression-evaluation.md) | **Expression Evaluation** | **S** | **Full** | Allow users to evaluate arbitrary Lua expressions in the current context. |
| `DEBUG-05` | **Remote Debugging** | **S** | **Full** | Support connecting to external Lua processes (e.g., via Mobdebug). |
| `DEBUG-06` | **Debug Target Configuration** | **S** | **Full** | Validate debug configurations before launching. |
| `DEBUG-07` | **Lazy Remote Stack Evaluation** | **S** | **Full** | Defer parsing of frame details until explicitly accessed. |
| `RUN-01` | **Lua Interpreter SDK** | **M** | **Full** | Allow configuring local Lua binaries (5.1-5.4, LuaJIT) as project SDKs. |
| `RUN-02` | **Run Configurations** | **M** | **Full** | Create and save configurations for script execution. |
| [`RUN-03`](../run/03-interactive-console.md) | **Interactive Console (REPL) Specification** | **S** | **Partial** | Provide a Lua REPL console within the IDE using the selected project SDK. |
| `RUN-04` | **Run Configuration Validation** | **M** | **Full** | Validate run configurations before execution. |

---

## Detailed Implementation Status

### DEBUG-01: Line Breakpoints
- **Status**: **Implemented** (`LuaLineBreakpointHandler`, `LuaLineBreakpointType`)

### DEBUG-02: Stack Frames & Variables
- **Status**: **Implemented** (`LuaRemoteStack`, `LuaStackFrame`, `LuaDebugVariable`)

### DEBUG-03: Step Over/Into/Out
- **Status**: **Implemented** (`LuaDebuggerController`)

### DEBUG-04: Expression Evaluation
- **Status**: **Implemented** (`LuaDebuggerEvaluator`)

### DEBUG-05: Remote Debugging
- **Status**: **Implemented** (`LuaDebugConnection` on port 8172)

### RUN-01: Lua Interpreter SDK
- **Status**: **Implemented** (`LuaInterpreter`, `LuaInterpreterFamily`)

### RUN-02: Run Configurations
- **Status**: **Implemented** (`LuaRunConfiguration`)

### RUN-03: Interactive Console (REPL)
- **Status**: **Partial** (Runs `lua -i` but lacks full IDE console integration)

---

## Test Coverage
- **`TestLuaDebugValue`**: Type checking (string, number, bool, table, error).
- **`TestLuaExecutionStack`**: Remote stack management.
- **`TestLuaStackFrame`**: Stack frame structure.

## Guides
- [Container Execution Guide](container-execution-guide.md)
