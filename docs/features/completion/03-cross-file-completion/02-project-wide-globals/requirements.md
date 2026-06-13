---
id: "COMP-03-02"
parent_id: "COMP-03"
folders:
  - "[[features/completion/03-cross-file-completion/requirements|requirements]]"
title: "COMP-03-02: Global Symbol Suggestions"
type: "feature"
status: "done"
---

# COMP-03-02: Global Symbol Suggestions

**Phase 2 of COMP-03: Cross-file Completion**

Suggest global functions, classes, and aliases from across the project without requiring explicit imports.

## Requirement Mapping

This phase implements **COMP-03-02** from the main requirements:
- **COMP-03-02**: Global Symbol Suggestions (M) — Suggest global symbols (functions, classes) indexed across the entire project.

Related requirements:
- **COMP-03-05**: Export Filtering (M) — Only exported symbols
- **COMP-03-07**: Visibility Suppression (C) — Hide `_` prefix symbols

## Scope (In-Scope)

- **Global Symbol Index Lookup**: Query `StubIndex` for all global functions, classes, and aliases defined across the project.
- **Ranking Heuristics**: Rank suggestions by proximity (same module > same directory > different modules).
- **Visibility Filtering**: Suppress symbols with `_` prefix in cross-file completion to reduce noise.
- **Deduplication**: Filter out symbols already provided by local scope or imported scope (from required modules).
- **Performance**: Support large projects with thousands of indexed symbols without UI lag.

## Out of Scope

- Smart suggestion of non-global symbols (e.g., private/local symbols from other files) — only exported globals.
- Caching strategies beyond `CachedValuesManager` (framework-provided caching).
- Symbol ranking based on type information (type-aware ranking is future work).
- Usage frequency ranking (deferred to Phase 4 due to performance constraints).

## Syntax & Behavior Rules

### Symbol Sources
Only suggest **explicitly exported** global symbols:
- **Function Definitions**: Top-level `function name(...) end` (not inside blocks)
- **Class Definitions**: Functions decorated with `@class` LuaCATS annotations
- **Table/Module Returns**: Assignments in files that end with `return <name>` (e.g., `local M = {}; ... return M`)
- **Avoid**: Local/private symbols (those in function/block scopes)

**Index Requirement**: A dedicated `LuaExportedGlobalIndex` (or enhancement to existing index) must encode whether a symbol is exported and its export type (function, class, table return).

### Visibility Rules
- **Suppress `_` Prefix**: Do not suggest `_private()` or `_internal` globals (configurable)
- **Include Lua Stdlib**: Only if already indexed in project (handled by separate stdlib indexing)
- **Project Scope Only**: Do not suggest symbols from external libraries not in the project

### Ranking Algorithm
**Ranking Weight** (not purely score-based; use IDE's `PrioritizedLookupElement` for integration):

| Factor | Weight | Notes |
|--------|--------|-------|
| Same Module | 0.9 | Module structure proximity |
| Same Directory | 0.7 | File tree proximity |
| Different Module | 0.5 | Default for unrelated symbols |
| @class-annotated | +0.25 | Well-documented class types |

**Note**: Usage frequency calculation is deferred to Phase 4 due to performance constraints with large projects.

### Insertion Behavior
- Selecting a global symbol does **NOT** automatically insert a `require` statement (Phase 3 handles auto-import).
- Phase 2 only **suggests** the symbols; Phase 3 implements the insertion handler.

## Test Cases

| ID | Scenario | Expected Result |
| :--- | :--- | :--- |
| **TC-02-01** | Type a global function name from an unrelated file | Function appears in completion list (unimported) |
| **TC-02-02** | Global with `_` prefix exists in project | Symbol is NOT suggested in cross-file completion |
| **TC-02-03** | Two functions with same name in different files | Both appear with file context; proximity ranked first |
| **TC-02-04** | @class-annotated global | Appears with class-specific icon; ranks with boost |
| **TC-02-05** | Project with 5000+ indexed symbols | Completion responds in <200ms (performance baseline) |
| **TC-02-06** | Global already in local scope | Does NOT appear in global suggestions (deduped) |
| **TC-02-07** | Global from required module | Does NOT appear in global suggestions (already Phase 1) |
| **TC-02-08** | Dumb mode (indexing disabled) | No suggestions; graceful degradation |
| **TC-02-09** | Symbol name collisions (same name, different files) | Ranking tiebreaker by file modification time |
| **TC-02-10** | Cache invalidation after file edit | Suggestions update to reflect changes |

## Acceptance Criteria

| ID | Criteria |
| :--- | :--- |
| **COMP-03-AC-06** | `StubIndex` is queried using `processElements()` with proper scope filtering. |
| **COMP-03-AC-07** | Symbols with `_` prefix are suppressed in cross-file suggestions (configurable). |
| **COMP-03-AC-08** | Symbols from local or imported scope are deduplicated (not repeated). |
| **COMP-03-AC-09** | Ranking algorithm correctly prioritizes proximity by module/directory structure. |
| **COMP-03-AC-10** | Completion performance remains acceptable (< 200ms) with large symbol sets. |
| **COMP-03-AC-11** | Integration with Phase 1 does not break imported symbol suggestions. |
| **COMP-03-AC-12** | Graceful degradation in dumb mode (indexing disabled). |

## Technical Details

### StubIndex Integration
- Use `LuaExportedGlobalIndex.processElements()` to iterate all global symbols
- Filter by completion context (current file, module)
- Apply visibility rules in-process to avoid redundant lookups

### Ranking Implementation
- Calculate proximity dynamically based on completion context
- Use IDE's `PrioritizedLookupElement` for ranking integration
- Sort results by weight (descending)

### Performance Considerations
- Request-scope cache for `StubIndex` results (lifetime: single completion session)
- No persistent caching of results
- Limit to ~500 candidates before ranking to avoid memory bloat
- Support cancellation via `ProgressManager.checkCanceled()`

## See Also

- **Design**: [[02-project-wide-globals/design|Design Document]]
- **Risks & Gaps**: [[02-project-wide-globals/risks-and-gaps|Risk Assessment]]
- **Parent Epic**: [[requirements|COMP-03 Main Requirements]]
