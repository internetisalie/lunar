# Code Completion Requirements (`COMP`)

Intelligent completion is the primary driver of productivity in the IDE.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `COMP-01` | **Keyword Completion** | **M** | **Full** | Suggest Lua keywords based on syntax context (e.g., `then` after `if`, `end` to close blocks). |
| `COMP-02` | **Basic Symbol Completion** | **M** | **Full** | Suggest local variables, parameters, and global symbols within the current scope. |
| `COMP-03` | **Cross-file Completion** | **M** | **Full** | Suggest symbols exported from other files via `require()` or global definitions. |
| `COMP-04` | **Type-Inferred Completion** | **S** | **Not Implemented** | Suggest members of a table or class based on its inferred type (via LuaCATS or assignment). |
| `COMP-05` | **Parameter Name Hints** | **S** | **Not Implemented** | Show inlay hints for parameter names when calling a function (e.g., `func(name: "val")`). |
| `COMP-06` | **Postfix Templates** | **C** | **Future Work** | Trigger code transformations after a dot (e.g., `myVar.if` -> `if myVar then ... end`). |
| `COMP-07` | **Live Templates** | **C** | **Future Work** | Standard IntelliJ snippets for common Lua patterns (loops, function headers). |
| `COMP-08` | **Auto-complete Enhancement** | **S** | **Not Implemented** | Intelligent auto-completion for common patterns (e.g., table methods, function calls). |

---

## Detailed Implementation Status

### COMP-01: Keyword Completion
- **Status**: **Implemented** (`LuaCompletionContributor`)

### COMP-02: Basic Symbol Completion
- **Status**: **Implemented** (`LuaCompletionContributor` via `LuaBindingsVisitor`)

### COMP-03: Cross-file Completion
- **Status**: **Implemented** (`LuaCompletionContributor` via `LuaFileBindingsIndex`)

