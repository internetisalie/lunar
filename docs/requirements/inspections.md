# Inspections & Diagnostics Requirements (`INSP`)

Lunar leverages its type engine and PSI tree to provide real-time feedback and catch errors before execution.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `INSP-01` | **Undeclared Variable** | **M** | Highlight variables that are used but never defined in the current or global scope. |
| `INSP-02` | **Unused Local/Parameter** | **M** | Warn when a local variable or function parameter is defined but never read. |
| `INSP-03` | **Type Mismatch** | **S** | (Requires `TYPE`) Warn when assigning a value to a variable with a conflicting LuaCATS `@type`. |
| `INSP-04` | **Unreachable Code** | **S** | Detect code following a `return`, `break`, or infinite loop that can never be executed. |
| `INSP-05` | **Global Creation Warning** | **S** | Warn when a variable is assigned without `local`, potentially polluting the global namespace. |
| `INSP-06` | **Shadowing Check** | **S** | Warn when a local variable shadows another local in a parent scope. |
| `INSP-07` | **Suspicious Concatenation** | **C** | Warn about string concatenation inside loops (performance diagnostic). |
| `INSP-08` | **Deprecated API Usage** | **C** | Highlight usage of functions marked with the `@deprecated` LuaCATS tag. |
| `INSP-09` | **Language Level Compliance** | **M** | Warn if using Lua 5.4 features (e.g., `<const>`) when the project is set to Lua 5.1. |
