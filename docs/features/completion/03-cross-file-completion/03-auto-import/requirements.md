---
id: "COMP-03-03"
title: "COMP-03-03: Auto-import Completion"
type: "feature"
parent_id: "COMP-03"
status: "done"
priority: "high"
folders:
  - "[[features/completion/03-cross-file-completion/requirements|parent]]"
---

# COMP-03-03: Auto-import Completion

**Phase 3 of COMP-03: Cross-file Completion**

Automatically insert a `require` statement when a user selects a cross-file symbol that is not yet imported in the current file.

## 1. Scope (In-Scope)

- **Non-imported Symbol Suggestions**: Inclusion of global symbols (functions, classes, aliases) from the `StubIndex` that are not yet available in the current file's scope.
- **Automatic Insertion**: Execution of `LuaAutoImportInsertHandler` upon selection to insert the necessary `require` statement.
- **Local Assignment Support**: Detecting if a module returns a value (table/class) and generating `local name = require("path")`.
- **Pure Global Support**: Detecting if a module primarily defines globals and generating a standalone `require("path")`.
- **Path Normalization**: Mapping file system paths to Lua module dots (e.g., `src/net/http.lua` to `net.http`) and handling `init.lua` truncation.
- **Insertion Logic**: Smart placement of imports to maintain file structure.

## 2. Syntax & Behavior Rules

- **Naming Heuristic**:
    - If the target file has a `@class` annotation, use that name for the local variable.
    - Otherwise, use the filename (e.g., `json_utils.lua` → `json_utils`).
- **Insertion Point**:
    - If `require` statements exist: Add to the end of the existing `require` block.
    - If no `require` exists: Insert after the file header (top-level comments) but before the first line of code.
- **Path Resolution**: 
    - Use the project's source roots to calculate the module name.
    - `path/to/module/init.lua` must be required as `require("path.to.module")`.
- **Deduplication**: Do not insert a `require` if the module path is already required in the file, regardless of the local variable name used.

## 3. Test Cases

| ID | Scenario | Expected Result |
| :--- | :--- | :--- |
| **TC-03-01** | Complete a symbol from a module returning a table | `local <name> = require("<path>")` is inserted. |
| **TC-03-02** | Complete a project-wide global (no return) | Standalone `require("<path>")` is inserted. |
| **TC-03-03** | Auto-import with existing imports | New import is grouped with existing `require` calls. |
| **TC-03-04** | Auto-import into empty file | Import is placed at the very top (or after shebang/header). |
| **TC-03-05** | `init.lua` module completion | The inserted path is `dir.subdir` instead of `dir.subdir.init`. |
| **TC-03-06** | Conflict prevention | If `local m = require("mod")` exists, selecting another symbol from `mod` does not add a second import. |

## 4. Acceptance Criteria

| ID | Criteria |
| :--- | :--- |
| **COMP-03-AC-01** | Selecting a suggested cross-file symbol automatically modifies the document to include a `require` statement. |
| **COMP-03-AC-02** | The system correctly distinguishes between "return-style" modules and "global-style" modules to choose the correct template. |
| **COMP-03-AC-03** | Module path calculation accurately reflects project source roots and `init.lua` conventions. |
| **COMP-03-AC-04** | Variable naming for `local` assignments follows the `@class` name or the snake_case filename. |
| **COMP-03-AC-05** | Import insertion does not break existing code or duplicate existing imports. |
