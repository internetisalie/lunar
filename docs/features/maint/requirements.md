---
id: "MAINT"
title: "MAINT: Maintenance & Refactoring"
type: "epic"
priority: low
status: planned
folders:
  - "[[features]]"
---

# Maintenance & Refactoring Requirements (`MAINT`)

Lunar prioritizes codebase health, performance, and alignment with modern IntelliJ Platform standards. The `MAINT` epic covers technical debt reduction, refactoring legacy components, and improving infrastructure.

## Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `MAINT-01` | **Kotlin Conversion** | **M** | **Todo** | Convert remaining legacy Java files to idiomatic Kotlin. |
| `MAINT-02` | **Label Refactoring** | **M** | **Todo** | Refactor Lua `goto` and label handling to use lazy resolution. |
| `MAINT-03` | **Deprecation Cleanup** | **L** | **Todo** | Remove usage of deprecated IntelliJ APIs and modernize platform integration. |
| [`MAINT-04`](04-refactor-symbol-resolution/requirements.md) | **Refactor Symbol Resolution** | **M** | **Done** | Replace eager `LuaBindingsVisitor` with lazy `PsiScopeProcessor`. |
| `MAINT-05` | **Type Engine Cleanup** | **M** | **Done** | Remove redundant type checks and unused parameters in the type engine. |
| `MAINT-06` | **LuaCATS Literal Highlighting** | **M** | **Todo** | Add color formatting for literal types in LuaCATS tags. |
| `MAINT-07` | **Interpreter Search Path Globs** | **M** | **Todo** | Add globbing support for interpreter search paths to improve module resolution. |
| `MAINT-08` | **LuaCheck UI Grouping** | **L** | **Todo** | Implement hierarchical grouping for LuaCheck inspection results. |
| `MAINT-14` | **Scope Reduction (Luau)** | **L** | **Done** | Remove Luau support references to focus on standard Lua (5.1-5.4). |
| `MAINT-15` | **Remove Legacy Annotators** | **L** | **Todo** | Remove `LuaLocalBindingsAnnotator` and related legacy components. |
| [`MAINT-09`](09-psi-stubs/requirements.md) | **Test Coverage: PSI & Stubs** | **M** | **Todo** | Increase unit test coverage for PSI walking, scopes, and stub serialization. |
| [`MAINT-10`](10-stub-indexes/requirements.md) | **Test Coverage: Stub Indexes** | **M** | **Todo** | Increase unit test coverage for stub and file-based indexes. |
| [`MAINT-11`](11-structure-view/requirements.md) | **Test Coverage: Structure View** | **M** | **Todo** | Increase unit test coverage for outline structure view tree nodes. |
| [`MAINT-12`](12-settings-ui/requirements.md) | **Test Coverage: Settings & UI** | **M** | **Todo** | Increase unit test coverage for configuration persistence and change listeners. |
| [`MAINT-13`](13-run-debugger/requirements.md) | **Test Coverage: Run & Debugger** | **M** | **Todo** | Increase unit test coverage for debugger controllers and interactive REPL. |
| [`MAINT-16`](16-luacats-syntax/requirements.md) | **Test Coverage: LuaCATS Syntax** | **M** | **Todo** | Increase unit test coverage for LuaCATS type comments, highlights, and docs. |
| [`MAINT-17`](17-utilities-commandline/requirements.md) | **Test Coverage: Utilities** | **M** | **Todo** | Increase unit test coverage for process runner, file, and thread utilities. |
| [`MAINT-18`](18-luacov-reports/requirements.md) | **Test Coverage: LuaCov Reports** | **M** | **Todo** | Increase unit test coverage for LuaCov report parsing and layered highlighting. |

---

## Detailed Implementation Status

### MAINT-14: Scope Reduction (Luau)
- **Status**: **✅ Completed**
- **Action**: All references to Luau support have been removed from the documentation, backlog, and requirements to ensure project focus on standard Lua 5.1-5.4, LuaJIT, and Redis.

### MAINT-04: Refactor Symbol Resolution
- **Status**: **✅ Implemented**
- **Strategy**: PSI Scope Processor (Lazy evaluation)
- **Detailed Specification**: [`04-refactor-symbol-resolution/requirements.md`](04-refactor-symbol-resolution/requirements.md)

### MAINT-05: Type Engine Cleanup
- **Status**: **✅ Implemented**
- **Changes**: Simplified `getValueType` logic and removed dead code in `LuaTypesVisitor`.
