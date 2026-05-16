---
folders:
  - "[[features]]"
title: "COMP: Code Completion"
priority: high
status: planned
---

# Code Completion Requirements (`COMP`)

Intelligent completion is the primary driver of productivity in the IDE.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-01` | **Keyword Completion** | **M** | Suggest Lua keywords based on syntax context (e.g., `then` after `if`, `end` to close blocks). [[COMP-01] Spec](features/completion/01-keyword-completion/requirements.md) |
| `COMP-02` | **Basic Symbol Completion** | **M** | Suggest local variables, parameters, and global symbols within the current scope. [[COMP-02] Spec](features/completion/02-symbol-completion/requirements.md) |
| `COMP-03` | **Cross-file Completion** | **M** | Suggest symbols exported from other files via `require()` or global definitions. [[COMP-03] Spec](features/completion/03-cross-file-completion/requirements.md) |
| `COMP-04` | **Type-Inferred Completion** | **S** | Suggest members of a table or class based on its inferred type (via LuaCATS or assignment). [[COMP-04] Spec](features/completion/04-type-inferred-completion/requirements.md) |
| `COMP-05` | **Parameter Name Hints** | **S** | Show inlay hints for parameter names when calling a function (e.g., `func(name: "val")`). |
| `COMP-06` | **Postfix Templates** | **C** | Trigger code transformations after a dot (e.g., `myVar.if` -> `if myVar then ... end`). |
| `COMP-07` | **Live Templates** | **C** | Standard IntelliJ snippets for common Lua patterns (loops, function headers). |
| `COMP-08` | **Auto-complete Enhancement** | **S** | Intelligent auto-completion for common patterns (e.g., table methods, function calls). |

---

## Detailed Implementation Status

### COMP-01: Keyword Completion
- **Status**: **Full** (`LuaCompletionContributor`)


### COMP-03: Cross-file Completion
- **Status**: **Planned**

