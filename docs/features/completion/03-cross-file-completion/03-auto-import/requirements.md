---
folders:
  - "[[features/completion/03-cross-file-completion/requirements|parent]]"
title: "COMP-03-03: Auto-import Completion"
type: specification
---

# COMP-03-03: Auto-import Completion

**Phase 3 of COMP-03: Cross-file Completion**

Automatically insert a `require` statement when a user selects a cross-file symbol that is not yet imported in the current file.

> **Requirement Mapping Note**: The parent COMP-03 requirements table lists this feature as **COMP-03-04** ("Auto-import Completion"). The `03-` prefix on this directory reflects the implementation phase order (Phase 3). Throughout this document, the canonical ID is **COMP-03-03** per directory naming convention.

## Requirement Mapping

This phase implements **COMP-03-04** from the main requirements table:
- **COMP-03-04**: Auto-import Completion (S) — Suggest non-imported global symbols and insert `local mod = require("path")` on selection using name heuristics.

**Depends on**:
- **COMP-03-02**: Global Symbol Suggestions (Phase 2) — Phase 3's `LuaAutoImportInsertHandler` attaches to Phase 2's non-imported symbol lookup elements.
- **Phase 2 index contract**: Phase 3 reads `LuaFile.stub?.exportedTypeString` directly (populated by `LuaFileElementType` during indexing) rather than querying `LuaExportedGlobalIndex` per-file.

## Scope

### In Scope

- **Non-imported Symbol Decoration**: Mark Phase 2 completion items for symbols not yet imported with tail text (e.g., ` (auto-import)`) so users can identify them before selecting.
- **Automatic Require Insertion**: Upon selection of a non-imported symbol, execute `LuaAutoImportInsertHandler` to insert the appropriate `require` statement inside a `WriteCommandAction`.
- **Local Assignment Template**: For return-style modules (files ending with `return <value>`), generate `local <name> = require("<path>")`.
- **Pure Global Template**: For global-style modules (files that define globals without returning a value), generate a standalone `require("<path>")`.
- **Module Path Resolution**: Map a `VirtualFile` to a Lua module path string (e.g., `src/net/http.lua` → `"net.http"`) using the project's configured source roots.
- **`init.lua` Normalization**: Trim the trailing `.init` segment from module paths (e.g., `path/to/module/init.lua` → `"path.to.module"`).
- **Insertion Point Logic**: Place the new `require` statement at the correct location:
  - After the last existing `require` call if any are present.
  - After the file header (top-level comments / shebang) and before the first non-comment statement if no `require` exists.
  - At offset 0 if the file is empty.
- **Deduplication**: Suppress insertion if the target module path is already present in any top-level `require(...)` call in the current file, regardless of the local variable name used.
- **Name Heuristics**: Derive the local variable name for `local <name> = require(...)` by:
  1. Using the `@class` annotation name from the target file when exactly one file-level `@class` annotation exists.
  2. Falling back to the target file's base name (without `.lua` extension), with hyphens and spaces replaced by underscores and lowercased.
  3. Appending a numeric suffix if the derived name conflicts with an existing local declaration in the current file.

### Out of Scope

- **Interactive Path Disambiguation**: If multiple valid module paths exist for the same file, the system uses the longest-prefix source-root match without prompting. An interactive picker is deferred.
- **Editing Existing Imports**: Renaming or reorganising already-inserted `require` calls.
- **Interactive Style Override UI**: An `autoImportStyle` project setting (`AUTO_DETECT` | `FORCE_LOCAL_ASSIGN` | `FORCE_GLOBAL`) is in scope (see Behavior Rules). A style-picker popup is out of scope.
- **Recursive/Transitive Auto-import**: Automatically importing all transitive dependencies of a selected symbol (covered by Recursive Resolution, `COMP-03-03` in the parent requirements table).
- **Auto-import Quick-Fix Inspection**: Offering an intention action for existing unresolved references is a separate inspection feature.
- **Non-Lua File Types**: No auto-import for `.d.lua` stubs or LuaCATS definition files.

## Behavior Rules

### Module Style Detection

The auto-import template depends on the detected export style of the target module:

| Export Style | Detection Heuristic | Generated Template |
| :--- | :--- | :--- |
| Return-style | `LuaFile.stub?.exportedTypeString` is non-null, OR PSI scan finds a root `LuaFinalStatement` starting with `return` | `local <name> = require("<path>")` |
| Global-style | Stub present but `exportedTypeString` is null, and no root `return` found in PSI scan | `require("<path>")` |

**Detection strategy**: Use `LuaFile.stub?.exportedTypeString` as a fast path (non-null → return-style). If the stub exists but `exportedTypeString` is null (unannotated return or no return), fall back to a PSI scan: if any root-level `LuaFinalStatement` beginning with `return` is found, classify as return-style; otherwise global-style.

**Dumb Mode Fallback**: If the IDE is in dumb mode (indexing disabled), default to the return-style template (`local <name> = require(...)`) as it is the safer choice for most Lua modules.

### Naming Heuristics (Local Variable Name)

Applied only when the return-style template is used:

1. **`@class` Annotation**: If the target file contains exactly one top-level `@class` annotation, use that class name verbatim (e.g., `--- @class HttpClient` → local name `HttpClient`).
2. **Filename Fallback**: Convert the file's base name (without `.lua`) to a valid Lua identifier:
   - Replace hyphens and whitespace with underscores.
   - Lowercase the result.
   - Example: `json-utils.lua` → `json_utils`.
3. **Conflict Avoidance**: If the derived name is already declared as a local in the current file's scope, append an incrementing numeric suffix (`json_utils2`, `json_utils3`, …). This is best-effort; the user remains responsible for naming conflicts in complex scopes.
4. **Keyword Guard**: If the derived name is a Lua reserved keyword (e.g., a file named `function.lua`), append `_module` as a suffix (e.g., `function_module`).

### Module Style Override (`autoImportStyle`)

A project-level setting `autoImportStyle` (type `AutoImportStyle`, default `AUTO_DETECT`) can override export-style detection:

| Value | Behaviour |
| :--- | :--- |
| `AUTO_DETECT` | Export-style detection runs as normal; template follows the module's actual style |
| `FORCE_LOCAL_ASSIGN` | Always use `local <name> = require("<path>")` regardless of export style |
| `FORCE_GLOBAL` | Always use bare `require("<path>")` regardless of export style |

The override is read at handler invocation time (not at lookup-element creation time).

### Module Path Resolution

1. Retrieve the project's `PathConfiguration.getProjectSourcePathPatterns(project)` — a list of `SourcePathPattern` objects derived from the Lua `package.path`-style setting in `LuaProjectSettings`.
2. For each pattern, compute the **leading prefix** (`pattern.leadingPath`) and **trailing suffix** (everything after the `?` in `pattern.spec`, e.g., `.lua` or `/init.lua`).
3. If the target file's absolute path starts with the prefix and ends with the suffix, extract the middle segment and replace `/` with `.`.
4. `init.lua` normalisation is implicit: the `?/init.lua` pattern variant strips the `/init.lua` suffix automatically.
5. If no pattern matches, use the file path relative to the project root as a fallback (log a warning).

**Example**:

| Source Root | File | Resolved Path |
| :--- | :--- | :--- |
| `src/` | `src/net/http/init.lua` | `net.http` |
| `src/` | `src/data/parser/csv.lua` | `data.parser.csv` |
| *(none)* | `utils/strings.lua` | `utils.strings` |

### Insertion Point Algorithm

1. **Contiguous leading require block present**: Find the last `require(...)` statement that belongs to the **contiguous leading require block** — i.e., consecutive require-only statements at the top of the file (after any header comments), with no intervening non-require code. Insert the new statement immediately after it (new line, no blank line gap). Requires appearing later in the file (inside functions, after executable code) are ignored for positioning.
2. **No contiguous leading require block**: Find the end of the leading comment block (all contiguous `--` line comments, block comments `--[[...]]`, and/or `#!` shebang at the start of the file). Insert after the last such comment, followed by a blank line separator before the next code statement. If the file has no header comments, insert at offset 0 followed by a newline.
3. **Empty file**: Insert at offset 0.

**Shebang Handling**: A `#!/...` shebang on line 1 is treated as a header line and is never displaced.

### Deduplication

Before inserting, collect the string arguments of all `require(...)` and `require "..."` calls anywhere in the file (both parenthesised and string-suffix forms). If the resolved module path matches any collected value, abort the insertion silently. The completion selection itself still proceeds (the typed symbol is inserted).

> **Notes**:
> - Both `require("mod")` and `require 'mod'` and `require "mod"` forms are recognised.
> - `require(variableName)` (non-string-literal argument) is intentionally ignored; static resolution is not possible.
> - The deduplication check is path-exact: `"utils"` and `"utils "` are distinct (no normalisation).

### Threading & Write Safety

- Module path resolution, export style detection, name resolution, and deduplication scan must all execute inside a **`runReadAction`** block (PSI reads require read access even on the EDT).
- PSI/document mutation must be wrapped in `WriteCommandAction.runWriteCommandAction(project, "Auto-import <moduleName>") { ... }` to make the insertion **undoable** by the user.
- `ProgressManager.checkCanceled()` must be called before PSI traversal to respect cancellation.

## Test Cases

| ID | Scenario | Setup | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC-03-01** | Return-style module | `utils.lua` ends with `return M`; no existing `require`s in current file | `local utils = require("utils")` inserted at top |
| **TC-03-02** | Global-style module | `logger.lua` defines top-level `function log(...)` with no return statement | `require("logger")` inserted at top |
| **TC-03-03** | `@class` annotation naming | `http_client.lua` has `--- @class HttpClient`; return-style module | `local HttpClient = require("net.http_client")` inserted |
| **TC-03-04** | Append to existing require block | Current file has 2 existing `require` calls at top | New import appended after last existing `require`; no blank line gap |
| **TC-03-05** | Deduplication — exact path match | `local m = require("utils")` already exists | No second import inserted; completion text accepted |
| **TC-03-06** | Deduplication — different variable name | `local u = require("utils")` already exists | No second import inserted |
| **TC-03-07** | `init.lua` normalization | Target is `src/net/http/init.lua`; source path pattern `$PROJECT_DIR$/?.lua` | Inserted path is `net.http`, not `net.http.init` |
| **TC-03-08** | Nested module path | Source path pattern `$PROJECT_DIR$/?.lua`; file `src/data/parser/csv.lua` | Inserted path is `data.parser.csv` |
| **TC-03-09** | File with `--` header comments | File begins with `-- Copyright notice\n-- v2.0\n\nlocal x = ...` | Import inserted after comment block, separated by a blank line from the first code statement |
| **TC-03-10** | Empty file | Current file contains no content | Import inserted at line 1 |
| **TC-03-11** | Multiple `@class` annotations | Target file declares two `@class` definitions | Falls back to filename heuristic |
| **TC-03-12** | Filename with hyphens | Target file is `json-utils.lua` | Local variable named `json_utils` |
| **TC-03-13** | Name collision in scope | `local json_utils = 1` already declared in current file | Local variable named `json_utils2` |
| **TC-03-14** | Keyword guard | Target file is `function.lua` | Local variable named `function_module` |
| **TC-03-15** | Dumb mode | IDE indexing is disabled | Return-style template used as fallback; no crash |
| **TC-03-16** | No source path pattern matches | No source path configured for project directory | Require path uses project-relative path; no crash |
| **TC-03-17** | Shebang line at top | File starts with `#!/usr/bin/env lua` followed by code | Import inserted after shebang, not before it |
| **TC-03-18** | Performance — large file | Current file has 500 top-level statements | Dedup scan and insertion complete in < 100ms |
| **TC-03-19** | Undo after insertion | User triggers Undo after auto-import | The auto-import `WriteCommandAction` is undone independently of the completion text insertion |
| **TC-03-20** | Block comment header (`--[[...]]`) | File begins with `--[[ license block ]]` followed by code | Import inserted after block comment, blank line before first code statement |
| **TC-03-21** | `require "foo"` syntax (no parens) | Current file has `require "utils"` (string-suffix form) | Deduplication detects the existing require; no second import inserted |
| **TC-03-22** | Single-quoted require path | Current file has `require('utils')` | Deduplication detects it; no second import inserted |
| **TC-03-23** | Non-contiguous require later in file | File has `require("a")` at top, executable code, then `require("b")` inside function | New import appended after the contiguous leading require block only (after `require("a")`) |
| **TC-03-24** | `FORCE_GLOBAL` style override | `autoImportStyle = FORCE_GLOBAL`; target is return-style module | Standalone `require("path")` inserted regardless of export style |
| **TC-03-25** | `FORCE_LOCAL_ASSIGN` style override | `autoImportStyle = FORCE_LOCAL_ASSIGN`; target is global-style module | `local <name> = require("path")` inserted regardless of export style |
| **TC-03-26** | Read-only file | Current file is read-only (VCS lock or no permissions) | No insertion attempted; user-visible error notification shown; no exception thrown |

## Acceptance Criteria

| ID | Criteria |
| :--- | :--- |
| **COMP-03-AC-13** | Selecting a non-imported global symbol from completion triggers `LuaAutoImportInsertHandler` and inserts a `require` statement into the file. |
| **COMP-03-AC-14** | Return-style modules produce `local <name> = require("<path>")` with the correct name heuristic applied (priority: `@class` name → filename). |
| **COMP-03-AC-15** | Global-style modules produce a standalone `require("<path>")` with no local assignment. |
| **COMP-03-AC-16** | Module paths are computed from the project's `PathConfiguration` source path patterns; `init.lua` trailing segments are stripped via the pattern matching. |
| **COMP-03-AC-17** | When exactly one file-level `@class` annotation exists, its name is used as the local variable name. When zero or two or more exist, the filename fallback applies. |
| **COMP-03-AC-18** | New imports are inserted after the last require in the contiguous leading require block, or after header comments (with a blank-line separator) if no requires exist, or at offset 0 for empty files. |
| **COMP-03-AC-19** | Deduplication prevents a second `require` for the same module path regardless of the variable name or string quote style already used. |
| **COMP-03-AC-20** | All PSI writes are performed inside a named `WriteCommandAction`, making the insertion individually undoable. |
| **COMP-03-AC-21** | The feature degrades gracefully in dumb mode: no crash; fallback return-style template used. |
| **COMP-03-AC-22** | No regression in Phase 1 (imported symbol suggestions) or Phase 2 (global symbol suggestions) as measured by all existing COMP-03-01 and COMP-03-02 tests continuing to pass. |
| **COMP-03-AC-23** | When `autoImportStyle` is `FORCE_GLOBAL` or `FORCE_LOCAL_ASSIGN`, the override takes precedence over export-style detection. |
| **COMP-03-AC-24** | Attempting to insert into a read-only file shows an error notification without throwing an exception. |

## Technical Notes

### Key New Components

| Component | Role | Target Package |
| :--- | :--- | :--- |
| `LuaAutoImportInsertHandler` | `InsertHandler<LookupElement>` — orchestrates auto-import on completion selection | `lang/completion/` |
| `LuaModulePathResolver` | Resolves `VirtualFile` → Lua module path string via `PathConfiguration.getProjectSourcePathPatterns` | `lang/path/` |
| `LuaExportStyleDetector` | Uses `LuaFile.stub?.exportedTypeString` + PSI fallback to determine template | `lang/completion/` |
| `LuaImportNameResolver` | Derives local variable name from `LuaCatsComment.getClassTagList()` or filename | `lang/completion/` |
| `LuaDeduplicationChecker` | Scans current file's PSI for `LuaFuncCall` require calls (all string forms) | `lang/completion/` |
| `LuaImportInserter` | Finds contiguous leading require block anchor and performs document mutation | `lang/completion/` |

### Phase 2 Integration Point

Phase 2's `GlobalSymbolRankingService` returns `GlobalSymbolCompletion` items. For each item that represents a **non-imported** symbol (i.e., it was not found in the current file's `require` closure), Phase 3 attaches `LuaAutoImportInsertHandler` when converting it to a `LookupElement`. Imported symbols (Phase 1 path) must **not** receive this handler.

This requires `GlobalSymbolCompletion` to carry:
- `sourceVirtualFile: VirtualFile` — the file defining the symbol
- `isImported: Boolean` — whether the symbol is already reachable via `require`

### Completion Tail Text

Non-imported completion items should display tail text to signal the pending import:

```
json_utils      (auto-import from json-utils.lua)
```

Controlled by `LuaProjectSettings.showAutoImportHints` (default: `true`).

## See Also

- **Phase 3 Source Spec**: [[03-auto-import|03-auto-import.md]]
- **Technical Design**: [[design|design.md]]
- **Implementation Plan**: [[implementation-plan|implementation-plan.md]]
- **Risks & Gaps**: [[risks-and-gaps|risks-and-gaps.md]]
- **Phase 2 Requirements**: [[../02-project-wide-globals/requirements|Phase 2 Requirements]]
- **Parent Epic**: [[../requirements|COMP-03 Main Requirements]]
- **Tracker**: [[saga://task/345|Task 345]]
