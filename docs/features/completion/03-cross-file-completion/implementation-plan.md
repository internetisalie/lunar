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
- **Tracker**: Task 343

### Phase 2: Project-wide Globals [Must]
- Integrate `StubIndex` lookups into the completion provider using `processElements`.
- Implement **ranking heuristics** (proximity, project usage).
- Implement visibility filtering for `_` prefixed symbols.
- Filter out symbols already provided by local or imported lookup.
- **Verification**: Tests for global functions appearing from non-required files.
- **Tracker**: Task 350

### Phase 3: [Auto-import](03-auto-import.md) [Should]
- Implement `LuaAutoImportInsertHandler`.
- Add logic for **local assignment templates** (`local x = require("...")`).
- Implement name heuristics for module assignments.
- Support `init.lua` path normalization.
- **Verification**: Selection test that inserts a `require` statement.
- **Tracker**: Task 345

### Phase 4: Recursive & Advanced Patterns [Must]
- Support recursive `require` resolution.
- Refine export detection (e.g., table field assignment `M.func = ...`).
- **Tracker**: Task 347

## Verification Tasks

- [ ] [Must] Implement `CrossFileCompletionTests`. **Tracker**: Task [346](saga://task/346)
- [ ] [Must] Verify performance with a large number of indexed files.
- [ ] [Should] Test auto-import with various project structures.
- [ ] [Must] Verify transitive imports (A -> B -> C).

## Task Summary

| Phase | Task | Status | Priority |
|-------|------|--------|----------|
| Phase 1: Imported Symbols | [343](saga://task/343) | ✅ Done | High |
| Phase 2: Project-wide Globals | [350](saga://task/350) | ⬜ Todo | High |
| Phase 3: Auto-import | [345](saga://task/345) | ⬜ Todo | Medium |
| Verification: Tests | [346](saga://task/346) | ⬜ Todo | High |
| Phase 4: Recursive Resolution | [347](saga://task/347) | ⬜ Todo | Low |
