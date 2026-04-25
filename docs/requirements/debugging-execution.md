# Debugging & Execution Requirements (`DEBUG`, `RUN`)

Lunar provides a seamless experience for running and debugging Lua applications directly from the IDE.

## Requirements Summary

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `DEBUG-01` | **Line Breakpoints** | **M** | Allow users to toggle breakpoints in the gutter and stop execution at specific lines. |
| `DEBUG-02` | **Stack Frames & Variables** | **M** | Display the call stack and local/upvalue variables when paused at a breakpoint. |
| `DEBUG-03` | **Step Over/Into/Out** | **M** | Standard execution flow control during a debug session. |
| `DEBUG-04` | **Expression Evaluation** | **S** | Allow users to evaluate arbitrary Lua expressions in the current context via a "Watch" or "Evaluate" window. |
| `DEBUG-05` | **Remote Debugging** | **S** | Support connecting to external Lua processes (e.g., via Mobdebug or a custom DAP bridge). |
| `DEBUG-06` | **Debug Target Configuration** | **S** | Validate debug configurations (script path, working directory, interpreter) before launching. |
| `RUN-01` | **Lua Interpreter SDK** | **M** | Allow configuring local Lua binaries (5.1-5.4, LuaJIT) as project SDKs. |
| `RUN-02` | **Run Configurations** | **M** | Create and save configurations for script execution, including arguments and environment variables. |
| `RUN-03` | **Interactive Console (REPL)** | **S** | Provide a Lua REPL console within the IDE using the selected project SDK. |
| `RUN-04` | **Run Configuration Validation** | **M** | Validate run configurations before execution (script name, interpreter path, working directory). |

## Implementation Status

### DEBUG Module

| ID | Status | Implementation Details | Notes |
| :--- | :--- | :--- | :--- |
| `DEBUG-01` | ✅ Implemented | `LuaLineBreakpointHandler`, `LuaLineBreakpointType` | Handles gutter breakpoints and line breakpoint validation |
| `DEBUG-02` | ✅ Implemented | `LuaRemoteStack`, `LuaStackFrame`, `LuaDebugVariable` | Parses Mobdebug response tables into stack frames and variables |
| `DEBUG-03` | ✅ Implemented | `LuaDebuggerController` | Step Over/Into/Out commands fully supported |
| `DEBUG-04` | 🟡 Partial | `LuaDebuggerEvaluator`, `LuaDebuggerController.execute` | Evaluator wired; result parsing incomplete (returns `TODO`) |
| `DEBUG-05` | ✅ Implemented | `LuaDebugConnection`, `LuaDebugProcess` | Mobdebug connection listener on port 8172 |

### RUN Module

| ID | Status | Implementation Details | Notes |
| :--- | :--- | :--- | :--- |
| `RUN-01` | ✅ Implemented | `LuaInterpreter`, `LuaInterpreterFamily`, `LuaApplicationSettings` | Full SDK configuration UI and management |
| `RUN-02` | ✅ Implemented | `LuaRunConfiguration` | Script path, working dir, env vars, interpreter args supported |
| `RUN-03` | 🟡 Partial | `LuaRunConfiguration` (interactive mode) | Runs `lua -v -i` but lacks deeper IDE console integration |

## Test Coverage

Recent test additions in `src/test/kotlin/net/internetisalie/lunar/run/`:

| Test Class | Coverage | Status |
| :--- | :--- | :--- |
| `TestLuaDebugValue` | Type checking (string, number, bool, table, error) | ✓ Passing |
| `TestLuaDebugVariable` | Variable creation and operations | ✓ Passing |
| `TestLuaValue` | Lua value parsing and table handling | ✓ Passing |
| `TestLuaExecutionStack` | Remote stack management | ✓ Passing |
| `TestLuaLineBreakpointType` | Breakpoint type validation | ✓ Passing |
| `TestLuaLineBreakpointHandler` | Breakpoint handler creation | ✓ Passing |
| `TestLuaStackFrame` | Stack frame structure | ✓ Passing |

## Known Limitations & Next Steps

### Expression Evaluation (`DEBUG-04`)
- **Current:** Evaluator is wired but `LuaDebuggerController.execute` returns `TODO` without parsing results
- **Next:** Implement result parsing to convert Mobdebug output into `LuaDebugValue` instances

### Interactive Console (`RUN-03`)
- **Current:** Basic functionality runs Lua REPL but limited IDE integration
- **Next:** Enhance with command history, code completion, and better formatting

### Source Mapping
- **Current:** `LuaPosition` maps debugger paths to local files
- **Next:** Verify cross-platform path mapping in various environments
