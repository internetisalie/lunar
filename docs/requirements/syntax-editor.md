# Syntax & Editor Requirements (`SYNTAX`)

Lunar ensures high-fidelity representation of Lua code with support for modern language features.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `SYNTAX-01` | **Lua 5.4 Support** | **M** | Full support for `<const>` and `<close>` variable attributes. |
| `SYNTAX-02` | **Semantic Highlighting** | **S** | Differentiate between locals, globals, parameters, and upvalues using distinct colors. |
| `SYNTAX-03` | **Code Folding** | **M** | Fold blocks (`function`, `do`, `if`, `while`, `repeat`) and multi-line comments/tables. |
| `SYNTAX-04` | **Brace Matching** | **M** | Highlight matching `(` `)`, `[` `]`, `{` `}`, and keywords (`if`...`end`). |
| `SYNTAX-05` | **Method Separators** | **C** | Draw lines between top-level function definitions for better visual structure. |
| `SYNTAX-06` | **Breadcrumbs** | **S** | Show the current scope path (e.g., `module > class > function`) at the bottom of the editor. |
| `SYNTAX-07` | **Inlay Hints** | **S** | Show implicit information, such as inferred types for `local` variables without annotations. |
