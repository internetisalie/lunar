---
folders:
  - "[[features/completion/03-cross-file-completion/requirements|requirements]]"
title: "Technical Design"
type: design
---

# COMP-03: Cross-file Completion Design

## Architecture

The cross-file completion system leverages the existing indexing infrastructure to provide high-performance suggestions without parsing external files on every trigger.

### Components

1.  **`LuaCrossFileCompletionProvider`**:
    *   Queries `LuaFileBindingsIndex` for symbols imported via `require()`.
    *   Queries `StubIndex` for global declarations (`LuaGlobalDeclarationIndex`, `LuaClassNameIndex`).
    *   Uses `CachedValuesManager` to cache resolved transitive bindings for the current file scope.
2.  **`LuaAutoImportInsertHandler`**:
    *   Handles the post-completion logic for non-imported symbols.
    *   Identifies the best `require` path and inserts it at the appropriate location.
    *   Determines if a local assignment is needed (e.g., `local name = require("path")`).

### Data Flow

1.  **Trigger**: User triggers completion in a reference context.
2.  **Imported Lookup**:
    *   Extract all `require` targets from the current file's `LuaFileBindingsIndex` record.
    *   Recursively fetch `bindings` from the index for all transitive dependencies.
    *   **Cycle Detection**: Maintain a `Set<VirtualFile>` of visited files during traversal to prevent infinite loops.
    *   Add as `LookupElement`.
3.  **Global Lookup**:
    *   Query `StubIndex.getInstance().processElements(...)` with prefix matching for high performance.
    *   Filter by name prefix.
    *   Identify which keys are already imported vs. need auto-import.
    *   **Ranking**: Prioritize symbols from files in the same directory or symbols that are already required in other parts of the project.

### Export Pattern Handling

- **Return Table**: If a required file ends with `return { ... }`, the indexer should have already captured these keys as `bindings`.
- **Module Globals**: Symbols declared as globals in a required file are treated as available in the requiring file's scope in most Lua environments.

## Auto-Import Logic

- **Insertion Point**: Find the first `require` in the file. If none, find the first line after initial comments.
- **Path Calculation**: Use `SourcePathPattern` and `LuaModulePathIndex` to convert the file path into a module name (e.g., `src/utils/math.lua` -> `utils.math`). Support `init.lua` normalization.
- **Template**:
    *   If the selected symbol is a field of a returned table: `local moduleName = require("path")`.
    *   If the selected symbol is a project-wide global: `require("path")`.
- **Name Heuristics**: Infer the local variable name from the module's filename or a `@class` annotation in the target file.

## Performance Optimizations

- **Transitive Caching**: Transitive bindings are cached at the file level and invalidated on any change to the module graph.
- **Bulk Processing**: Use `StubIndex.processElements` to avoid loading all keys into memory at once.
- **Visibility Filtering**: Skip symbols starting with `_` unless they are defined in the current file.
