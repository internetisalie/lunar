---
folders:
  - "[[features]]"
title: "SYNTAX: Syntax & Editor"
priority: medium
status: planned
---

# Syntax & Editor Requirements (`SYNTAX`)

Lunar ensures high-fidelity representation of Lua code with support for modern language features.

## Requirements & Implementation Status

| ID                                                | Requirement                         | Priority | Description                                                                                                                        |
| :------------------------------------------------ | :---------------------------------- | :------: | :--------------------------------------------------------------------------------------------------------------------------------- |
| [`SYNTAX-01`](./01-lua-54-attributes.md)          | **Lua 5.4 Support**                 |  **M**   | Full support for `<const>` and `<close>` variable attributes.                                                                      |
| [`SYNTAX-02`](./02-semantic-highlighting.md)      | **Semantic Highlighting**           |  **S**   | Differentiate between locals, globals, parameters, and upvalues.                                                                   |
| [`SYNTAX-03`](./03-code-folding.md)               | **Code Folding**                    |  **M**   | Fold blocks, tables, strings, long comments, doc comments, and regions.                                                            |
| [`SYNTAX-04`](./04-brace-matching.md)             | **Brace Matching**                  |  **M**   | Highlight matching `( )`, `[ ]`, `{ }`, and keywords (`if`...`end`).                                                               |
| `SYNTAX-05`                                       | **Method Separators**               |  **C**   | Draw lines between top-level function definitions.                                                                                 |
| [`SYNTAX-06`](./06-breadcrumbs.md)                | **Breadcrumbs**                     |  **S**   | Show current scope path (e.g., `module > class > function`).                                                                       |
| [`SYNTAX-07`](./07-inlay-hints.md)                | **Inlay Hints**                     |  **S**   | Show implicit information, such as inferred types for `local` variables, parameter names at call sites, and inferred return types. |
| [`SYNTAX-08`](./08-string-escapes.md)             | **String Escape Processing**        |  **M**   | Process Lua escape sequences in quoted strings.                                                                                    |
| `SYNTAX-09`                                       | **Lua 5.5 Support (Future)**        |  **C**   | Support for Lua 5.5 features when released.                                                                                        |
| [`SYNTAX-10`](./10-luadoc-enter-handler.md)       | **Enter Handler for Comments**      |  **S**   | Auto-continue LuaDOC comments (e.g., `---`) and manage generic comment blocks (e.g., `--`) when pressing Enter.                    |
| [`SYNTAX-11`](./11-numeric-literals.md)           | **Numeric Literal Validation**      |  **M**   | Verify and highlight hexadecimal, float, and integer literals according to 5.4 specs.                                              |
| [`SYNTAX-12`](./12-goto-scope.md)                 | **Label & Goto Scope Resolution**   |  **S**   | Flag unresolved `goto` statements and invalid scope jumps.                                                                         |
| [`SYNTAX-13`](./13-standalone-expression.md)      | **Standalone Expression Annotator** |  **M**   | Flag expressions incorrectly used as statements.                                                                                   |
| [`SYNTAX-14`](./14-vararg-context.md)             | **Vararg Context Annotator**        |  **M**   | Flag `...` used outside of vararg functions.                                                                                       |
| [`SYNTAX-15`](./15-lexer-optimization.md)         | **Lexer Pushback Optimization**     |  **C**   | Optimize `{luacats}` rule in lexer to pushback only newline.                                                                       |
| [`SYNTAX-16`](./16-language-level-enforcement.md) | **Language Level Enforcement**      |  **M**   | Validate code doesn't use version-specific syntax for configured Lua level (5.1-5.4).                                              |
