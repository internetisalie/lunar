# Debugging & Execution Requirements (`DEBUG`, `RUN`)

Lunar provides a seamless experience for running and debugging Lua applications directly from the IDE.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `DEBUG-01` | **Line Breakpoints** | **M** | Allow users to toggle breakpoints in the gutter and stop execution at specific lines. |
| `DEBUG-02` | **Stack Frames & Variables** | **M** | Display the call stack and local/upvalue variables when paused at a breakpoint. |
| `DEBUG-03` | **Step Over/Into/Out** | **M** | Standard execution flow control during a debug session. |
| `DEBUG-04` | **Expression Evaluation** | **S** | Allow users to evaluate arbitrary Lua expressions in the current context via a "Watch" or "Evaluate" window. |
| `DEBUG-05` | **Remote Debugging** | **S** | Support connecting to external Lua processes (e.g., via Mobdebug or a custom DAP bridge). |
| `RUN-01` | **Lua Interpreter SDK** | **M** | Allow configuring local Lua binaries (5.1-5.4, LuaJIT) as project SDKs. |
| `RUN-02` | **Run Configurations** | **M** | Create and save configurations for script execution, including arguments and environment variables. |
| `RUN-03` | **Interactive Console (REPL)** | **S** | Provide a Lua REPL console within the IDE using the selected project SDK. |
