# Syntax & Editor Requirements (`SYNTAX`)

Lunar ensures high-fidelity representation of Lua code with support for modern language features.

## Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description / Notes |
| :--- | :--- | :---: | :---: | :--- |
| [`SYNTAX-01`](spec/syntax-01-lua-54-attributes.md) | **Lua 5.4 Support** | **M** | Implemented | Full support for `<const>` and `<close>` variable attributes. |
| [`SYNTAX-02`](spec/syntax-02-semantic-highlighting.md) | **Semantic Highlighting** | **S** | Implemented | Differentiate between locals, globals, parameters, and upvalues. |
| [`SYNTAX-03`](spec/syntax-03-code-folding.md) | **Code Folding** | **M** | Implemented | Fold blocks, tables, strings, long comments, doc comments, and regions. |
| [`SYNTAX-04`](spec/syntax-04-brace-matching.md) | **Brace Matching** | **M** | Implemented | Highlight matching `( )`, `[ ]`, `{ }`, and keywords (`if`...`end`). |
| `SYNTAX-05` | **Method Separators** | **C** | Pending | Draw lines between top-level function definitions. |
| [`SYNTAX-06`](spec/syntax-06-breadcrumbs.md) | **Breadcrumbs** | **S** | Implemented | Show current scope path (e.g., `module > class > function`). |
| `SYNTAX-07` | **Inlay Hints** | **S** | Pending | Show implicit information, such as inferred types for `local` variables. |
| [`SYNTAX-08`](spec/syntax-08-string-escapes.md) | **String Escape Processing** | **M** | Implemented | Process Lua escape sequences in quoted strings. |
| `SYNTAX-09` | **Luau Syntax Support** | **S** | Pending | Parse and highlight Luau-specific syntax (optional). |
| `SYNTAX-10` | **Enter Handler for LuaDOC** | **S** | Pending | Auto-continue LuaDOC comments (e.g., `---`) when pressing Enter. |
| [`SYNTAX-11`](spec/syntax-11-numeric-literals.md) | **Numeric Literal Validation** | **M** | Implemented | Verify and highlight hexadecimal, float, and integer literals according to 5.4 specs. |
| [`SYNTAX-12`](spec/syntax-12-goto-scope.md) | **Label & Goto Scope Resolution** | **S** | Pending | Flag unresolved `goto` statements and invalid scope jumps. |
| [`SYNTAX-13`](spec/syntax-13-standalone-expression.md) | **Standalone Expression Annotator** | **M** | Pending | Flag expressions incorrectly used as statements. |
| [`SYNTAX-14`](spec/syntax-14-vararg-context.md) | **Vararg Context Annotator** | **M** | Pending | Flag `...` used outside of vararg functions. |
