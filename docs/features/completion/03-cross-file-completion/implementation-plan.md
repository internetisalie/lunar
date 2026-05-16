---
folders:
  - "[[features/completion/03-cross-file-completion/requirements|requirements]]"
title: "Implementation Plan"
type: plan
---

# COMP-03: Cross-file Completion Implementation Plan

## Phases

### Phase 1: Imported Symbols [Must]
- Implement `LuaCrossFileCompletionProvider` integration with `LuaFileBindingsIndex`.
- Support basic `require` resolution to fetch bindings.
- Implement **transitive caching** using `CachedValuesManager`.
- Implement **cycle detection** for recursive `require` resolution.
- **Verification**: Tests for completion of symbols from a `require`'d file.

### Phase 2: Project-wide Globals [Must]
- Integrate `StubIndex` lookups into the completion provider using `processElements`.
- Implement **ranking heuristics** (proximity, project usage).
- Implement visibility filtering for `_` prefixed symbols.
- Filter out symbols already provided by local or imported lookup.
- **Verification**: Tests for global functions appearing from non-required files.

### Phase 3: Auto-import [Should]
- Implement `LuaAutoImportInsertHandler`.
- Add logic for **local assignment templates** (`local x = require("...")`).
- Implement name heuristics for module assignments.
- Support `init.lua` path normalization.
- **Verification**: Selection test that inserts a `require` statement.

### Phase 4: Recursive & Advanced Patterns [Could]
- Support recursive `require` resolution.
- Refine export detection (e.g., table field assignment `M.func = ...`).

## Verification Tasks

- [ ] [Must] Implement `CrossFileCompletionTests`.
- [ ] [Must] Verify performance with a large number of indexed files.
- [ ] [Should] Test auto-import with various project structures.
- [ ] [Could] Verify transitive imports (A -> B -> C).
