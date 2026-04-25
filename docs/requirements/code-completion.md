# Code Completion Requirements (`COMP`)

Intelligent completion is the primary driver of productivity in the IDE.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-01` | **Keyword Completion** | **M** | Suggest Lua keywords based on syntax context (e.g., `then` after `if`, `end` to close blocks). |
| `COMP-02` | **Basic Symbol Completion** | **M** | Suggest local variables, parameters, and global symbols within the current scope. |
| `COMP-03` | **Cross-file Completion** | **M** | Suggest symbols exported from other files via `require()` or global definitions. |
| `COMP-04` | **Type-Inferred Completion** | **S** | Suggest members of a table or class based on its inferred type (via LuaCATS or assignment). |
| `COMP-05` | **Parameter Name Hints** | **S** | Show inlay hints for parameter names when calling a function (e.g., `func(name: "val")`). |
| `COMP-06` | **Postfix Templates** | **C** | Trigger code transformations after a dot (e.g., `myVar.if` -> `if myVar then ... end`). |
| `COMP-07` | **Live Templates** | **C** | Standard IntelliJ snippets for common Lua patterns (loops, function headers). |
