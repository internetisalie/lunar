# Inspections & Diagnostics Requirements (`INSP`)

Lunar leverages its type engine and PSI tree to provide real-time feedback and catch errors before execution.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `INSP-01` | **Undeclared Variable** | **M** | **Partial** | Highlight variables that are used but never defined. |
| `INSP-02` | **Unused Local/Parameter** | **M** | **Partial** | Warn when a local variable or function parameter is defined but never read. |
| `INSP-03` | **Type Mismatch** | **S** | **Not Implemented** | Warn when assigning a value with a conflicting LuaCATS `@type`. |
| `INSP-04` | **Unreachable Code** | **S** | **Not Implemented** | Detect code following a `return` or `break` that can never be executed. |
| `INSP-05` | **Global Creation Warning** | **S** | **Partial** | Warn when a variable is assigned without `local`. |
| `INSP-06` | **Shadowing Check** | **S** | **Not Implemented** | Warn when a local variable shadows another local in a parent scope. |
| `INSP-07` | **Suspicious Concatenation** | **C** | **Future Work** | Warn about string concatenation inside loops. |
| `INSP-08` | **Deprecated API Usage** | **C** | **Future Work** | Highlight usage of functions marked with `@deprecated`. |
| `INSP-09` | **Language Level Compliance** | **M** | **Full** | Warn if using features not supported by the selected Lua version. |
