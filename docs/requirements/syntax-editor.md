# Syntax & Editor Requirements (`SYNTAX`)

Lunar ensures high-fidelity representation of Lua code with support for modern language features.

## Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| [`SYNTAX-01`](spec/syntax/01-lua-54-attributes.md) | **Lua 5.4 Support** | **M** | **Full** | Full support for `<const>` and `<close>` variable attributes. |
| [`SYNTAX-02`](spec/syntax/02-semantic-highlighting.md) | **Semantic Highlighting** | **S** | **Full** | Differentiate between locals, globals, parameters, and upvalues. |
| [`SYNTAX-03`](spec/syntax/03-code-folding.md) | **Code Folding** | **M** | **Full** | Fold blocks, tables, strings, long comments, doc comments, and regions. |
| [`SYNTAX-04`](spec/syntax/04-brace-matching.md) | **Brace Matching** | **M** | **Full** | Highlight matching `( )`, `[ ]`, `{ }`, and keywords (`if`...`end`). |
| `SYNTAX-05` | **Method Separators** | **C** | **Future Work** | Draw lines between top-level function definitions. |
| [`SYNTAX-06`](spec/syntax/06-breadcrumbs.md) | **Breadcrumbs** | **S** | **Full** | Show current scope path (e.g., `module > class > function`). |
| [`SYNTAX-07`](spec/syntax/07-inlay-hints.md) | **Inlay Hints** | **S** | **Not Implemented** | Show implicit information, such as inferred types for `local` variables, parameter names at call sites, and inferred return types. |
| [`SYNTAX-08`](spec/syntax/08-string-escapes.md) | **String Escape Processing** | **M** | **Full** | Process Lua escape sequences in quoted strings. |
| `SYNTAX-09` | **Luau Syntax Support** | **S** | **Future Work** | Parse and highlight Luau-specific syntax (optional). |
| [`SYNTAX-10`](spec/syntax/10-luadoc-enter-handler.md) | **Enter Handler for Comments** | **S** | **Full** | Auto-continue LuaDOC comments (e.g., `---`) and manage generic comment blocks (e.g., `--`) when pressing Enter. |
| [`SYNTAX-11`](spec/syntax/11-numeric-literals.md) | **Numeric Literal Validation** | **M** | **Full** | Verify and highlight hexadecimal, float, and integer literals according to 5.4 specs. |
| [`SYNTAX-12`](spec/syntax/12-goto-scope.md) | **Label & Goto Scope Resolution** | **S** | **Full** | Flag unresolved `goto` statements and invalid scope jumps. |
| [`SYNTAX-13`](spec/syntax/13-standalone-expression.md) | **Standalone Expression Annotator** | **M** | **Full** | Flag expressions incorrectly used as statements. |
| [`SYNTAX-14`](spec/syntax/14-vararg-context.md) | **Vararg Context Annotator** | **M** | **Full** | Flag `...` used outside of vararg functions. |

---

## Detailed Implementation Status

### SYNTAX-01: Lua 5.4 Support
- **Status**: **Implemented** (`LuaAttribNameAnnotator`)

### SYNTAX-02: Semantic Highlighting
- **Status**: **Implemented** (`LuaLocalBindingsAnnotator`, `LuaGlobalBindingsAnnotator`)

### SYNTAX-03: Code Folding
- **Status**: **Implemented** (`LuaFoldingBuilder`)

### SYNTAX-04: Brace Matching
- **Status**: **Implemented** (`LuaPairedBraceMatcher`)

### SYNTAX-06: Breadcrumbs
- **Status**: **Implemented** (`LuaBreadcrumbsProvider`)

### SYNTAX-10: Enter Handler for Comments
- **Status**: **Implemented** (`LuaEnterHandlerDelegate`)

### SYNTAX-12: Label & Goto Scope Resolution
- **Status**: **Implemented** (`LuaGotoAnnotator`)

