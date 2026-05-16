---
folders:
  - "[[features/completion/requirements|requirements]]"
title: "03: Cross-file Completion"
priority: high
status: planned
---

# COMP-03: Cross-file Completion Requirements

Suggest symbols exported from other files via `require()` or global definitions.

## Scope

### In Scope
- **Imported Symbols**: Suggest symbols defined in files that are explicitly imported via `require()`.
- **Global Symbols**: Suggest global functions, classes, and aliases from any file in the project (leveraging Stub Indices).
- **Auto-import Suggestions**: (Should) Suggest symbols not yet imported, and automatically insert the necessary `require()` statement upon selection.
- **Export Awareness**: Recognize different Lua export patterns (return table, module-level globals).

### Out of Scope
- Symbols from files not in the project or standard library (handled by `TARGET`).
- Deep member completion (e.g., `pkg.sub.field`) where the intermediate table isn't resolved (covered by `COMP-04`).

## Requirements Table

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `COMP-03-01` | **Imported Symbol Suggestions** | **M** | Suggest symbols from files listed in the current file's `require` statements. |
| `COMP-03-02` | **Global Symbol Suggestions** | **M** | Suggest global symbols (functions, classes) indexed across the entire project. |
| `COMP-03-03` | **Recursive Import Resolution** | **S** | Suggest symbols from transitively required files. |
| `COMP-03-04` | **Auto-import Completion** | **S** | Suggest non-imported global symbols and insert `local mod = require("path")` on selection using name heuristics. |
| `COMP-03-05` | **Export Filtering** | **M** | Only suggest symbols that are actually "exported" (returned or global) from the required file. |
| `COMP-03-06` | **Circular Dependency Handling** | **M** | Ensure recursive resolution terminates gracefully when cycles are detected in `require` calls. |
| `COMP-03-07` | **Visibility Suppression** | **C** | Optionally hide symbols with a `_` prefix in cross-file completion to reduce noise. |

## Test Cases

| ID | Action | Expected Output |
| :--- | :--- | :--- |
| `TC-01` | In `main.lua`, `require("utils")`. Type `ut` | Suggestions from `utils.lua` (e.g., `utils.helper`) appear. |
| `TC-02` | Type a global function name defined in `other.lua` | Function appears in completion list even if `other.lua` is not required. |
| `TC-03` | Select a global symbol from `TC-02` | `local other = require("other")` is added to the top of the file. |
| `TC-04` | File A requires B, B requires A. Type in A. | No stack overflow; completion works for direct dependencies. |
