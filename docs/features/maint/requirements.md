---
folders:
  - "[[features]]"
title: "MAINT: Maintenance & Refactoring"
priority: low
status: planned
---

# Maintenance & Refactoring Requirements (`MAINT`)

Lunar prioritizes codebase health, performance, and alignment with modern IntelliJ Platform standards. The `MAINT` epic covers technical debt reduction, refactoring legacy components, and improving infrastructure.

## Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `MAINT-01` | **Kotlin Conversion** | **M** | **Todo** | Convert remaining legacy Java files to idiomatic Kotlin. |
| `MAINT-02` | **Label Refactoring** | **M** | **Todo** | Refactor Lua `goto` and label handling to use lazy resolution. |
| `MAINT-03` | **Deprecation Cleanup** | **L** | **Todo** | Remove usage of deprecated IntelliJ APIs and modernize platform integration. |
| [`MAINT-04`](04-refactor-symbol-resolution/03-requirements.md) | **Refactor Symbol Resolution** | **M** | **Done** | Replace eager `LuaBindingsVisitor` with lazy `PsiScopeProcessor`. |
| `MAINT-05` | **Type Engine Cleanup** | **M** | **Done** | Remove redundant type checks and unused parameters in the type engine. |
| `MAINT-06` | **LuaCATS Literal Highlighting** | **M** | **Todo** | Add color formatting for literal types in LuaCATS tags. |
| `MAINT-07` | **Interpreter Search Path Globs** | **M** | **Todo** | Add globbing support for interpreter search paths to improve module resolution. |
| `MAINT-08` | **LuaCheck UI Grouping** | **L** | **Todo** | Implement hierarchical grouping for LuaCheck inspection results. |
| `MAINT-14` | **Scope Reduction (Luau)** | **L** | **Todo** | Remove Luau support references to focus on standard Lua (5.1-5.4). |
| `MAINT-XX` | **Test Coverage Improvement** | **H** | **Todo** | Increase unit test coverage for legacy code across the codebase. |

---

## Detailed Implementation Status

### MAINT-04: Refactor Symbol Resolution
- **Status**: **✅ Implemented**
- **Strategy**: PSI Scope Processor (Lazy evaluation)
- **Detailed Specification**: [`04-refactor-symbol-resolution/03-requirements.md`](04-refactor-symbol-resolution/03-requirements.md)

### MAINT-05: Type Engine Cleanup
- **Status**: **✅ Implemented**
- **Changes**: Simplified `getValueType` logic and removed dead code in `LuaTypesVisitor`.
