# lunar

Lua support for IntelliJ Platform.

### Requirements & Roadmap

For detailed specifications on all features, implementation status, and planned enhancements, see the [Requirements Documentation](docs/requirements.md).

The project covers the following areas:

- **[SYNTAX]** Syntax & Editor support (Lua 5.4, Luau, folding, highlighting, formatting)
- **[COMP]** Code Completion (keywords, symbols, cross-file, type inference)
- **[NAV]** Code Navigation (go to definition, find usages, structure view, markers, references)
- **[TYPE]** Type System (LuaCATS, type inference, function signatures)
- **[DOC]** Documentation (Quick Doc, LuaCATS/LuaDoc highlighting, parameter info)
- **[INSP]** Inspections & Diagnostics (undeclared variables, type mismatches, unused locals)
- **[ANALYSIS]** Static Analysis (Luacheck integration, external annotator)
- **[FORMAT]** Formatting (indentation, alignment, spacing, stylua compatibility)
- **[REFACT/INTENT]** Refactoring & Intentions (rename, labels, introduce variable, string conversions)
- **[DEBUG/RUN]** Debugging & Execution (breakpoints, stack frames, remote debugging, REPL)
- **[Non-Functional]** Technical requirements (Kotlin idiomaticity, performance, caching)
