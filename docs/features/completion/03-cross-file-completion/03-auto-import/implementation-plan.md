---
id: "COMP-03-03-PLAN"
parent_id: "COMP-03-03"
folders:
  - "[[features/completion/03-cross-file-completion/03-auto-import/requirements|parent]]"
title: "COMP-03-03: Implementation Plan"
type: "plan"
status: "todo"
---

# COMP-03-03: Implementation Plan

**Phase 3 of COMP-03: Cross-file Completion**

## Overview

Phase 3 adds auto-import insertion to the cross-file completion flow. When a user selects a non-imported global symbol from the completion popup, the appropriate `require` statement is automatically inserted at the correct location in the current file.

**Tracker**: [[saga://task/345|Task 345]]  
**Estimated Effort**: 12–17 hours (de-risking + implementation + testing)  
**Critical Path**: DR-01 → DR-02 → Phase 3.1 → Phase 3.2 → Phase 3.5  
**Hard Prerequisite**: Phase 2 (Task 350) must pass all quality gates before Phase 3.1 begins.

---

## De-Risking Phase (3–5 hours)

All de-risking must complete before implementation.

### DR-01: Confirm InsertHandler Threading Model (1 hour)
**Deliverable**: Confirmed threading contract + prototype handler stub that compiles and runs without `SlowOperationsException`

1. Verify `InsertHandler.handleInsert` is called on the EDT in the target IDE version.
2. Confirm `runReadAction { }` is required for PSI reads invoked from the EDT (expected: yes).
3. Confirm `WriteCommandAction.runWriteCommandAction` is EDT-safe (expected: yes).
4. Verify `InsertionContext` exposes `project` and `file` directly.

**Blocker**: Unexpected threading constraint → revise component architecture.

---

### DR-02: Validate PSI/Document Insertion API (1–2 hours)
**Deliverable**: Confirmed insertion strategy + passing spike test

1. Compare `document.insertString(offset, text)` vs. `PsiFile.addBefore(element, anchor)` for robustness.
2. Confirm `PsiDocumentManager.commitDocument(document)` is mandatory after `insertString`.
3. Test anchor-finding via `PsiTreeUtil.findChildrenOfType` for `require` call detection in a two-file fixture.
4. Validate shebang (`#!`) detection pattern in the PSI lexer.
5. Confirm inserted text at an offset produces syntactically valid Lua (parse the result).

**Blocker**: `insertString` produces malformed PSI → switch to `PsiElement`-based insertion.

---

### DR-03: Verify Export Type Metadata Strategy (1 hour)
**Deliverable**: Confirmed detection approach + dumb-mode behaviour documented

1. Confirm `LuaFile.stub?.exportedTypeString` is non-null for annotated return-style modules and null for unannotated ones.
2. Validate the PSI-scan fallback via `LuaFinalStatement`: walk `file.getBlockList().firstOrNull()?.statementList` and check for a `return`-starting final statement.
3. Confirm dumb mode: `DumbService.isDumb(project)` returns `true`; default to `RETURN_STYLE`.
4. Document the exact detection priority: stub fast path → PSI scan → dumb mode default.

**Resolution**: Export type is read from `LuaFile.stub?.exportedTypeString` + PSI fallback. No new index required.

---

### DR-04: Validate Module Path Resolution API (1 hour)
**Deliverable**: `LuaModulePathResolver` spike working for nested and `init.lua` paths

1. Verify `PathConfiguration.getProjectSourcePathPatterns(project)` returns patterns with `leadingPath` usable for reverse path resolution.
2. Test longest-prefix matching for projects with multiple overlapping source roots.
3. Confirm `init.lua` normalisation (strip `.init` suffix) works for nested directories.
4. Test project-relative fallback when no source root is configured.

**Blocker**: Source root API unavailable → use `project.basePath` as sole fallback.

---

## Implementation Phases (7–9 hours)

### Phase 3.1: Core Components (3–4 hours) — CRITICAL PATH
**Dependencies**: All de-risking complete  
**New files**: `LuaAutoImportInsertHandler.kt`, `LuaModulePathResolver.kt`, `LuaExportStyleDetector.kt`, `LuaImportNameResolver.kt`, `LuaDeduplicationChecker.kt`, `LuaImportInserter.kt`  
**Target packages**: `lang/completion/` and `lang/path/`

**Tasks**:

1. **`LuaModulePathResolver`** — source root stripping, slash-to-dot, `init.lua` normalisation, project-relative fallback.
2. **`LuaExportStyleDetector`** — index lookup with dumb mode fallback to `RETURN_STYLE`.
3. **`LuaImportNameResolver`** — `@class` single-annotation check, filename fallback, keyword guard.
4. **`LuaDeduplicationChecker`** — top-level `require(...)` string-literal scan via `PsiTreeUtil`.
5. **`LuaImportInserter`** — three-branch anchor detection (after requires / after header / at offset 0) + `document.insertString` + `commitDocument`.
6. **`LuaAutoImportInsertHandler`** — orchestrate components with `runReadAction` guards and `WriteCommandAction` wrapping.

**Unit tests** (15–20):
- [ ] `LuaModulePathResolver`: nested path, `init.lua`, no source root, multiple roots, invalid `VirtualFile`
- [ ] `LuaExportStyleDetector`: TABLE_RETURN, GLOBAL_ONLY, null (→ GLOBAL_STYLE), dumb mode (→ RETURN_STYLE)
- [ ] `LuaImportNameResolver`: single @class, multiple @class (→ filename), keyword (`function.lua` → `function_module`), conflict suffix
- [ ] `LuaDeduplicationChecker`: path present, path absent, non-string-arg `require` ignored
- [ ] `LuaImportInserter`: after requires, after header comments, empty file, shebang line

**Success gate**: AC-13, AC-15, AC-16, AC-18, AC-19, AC-20 pass.

---

### Phase 3.2: Phase 2 Integration (1–2 hours)
**Dependencies**: Phase 3.1  
**Modified files**: `GlobalSymbolRankingService.kt`, `LuaCrossFileCompletionProvider.kt`

**Tasks**:

1. Add `sourceVirtualFile: VirtualFile` and `isImported: Boolean` fields to `GlobalSymbolCompletion` data class.
2. Populate `isImported = false` for Phase 2 non-imported items; `isImported = true` for Phase 1 re-exports (if surfaced through Phase 2 dedup path).
3. Modify `GlobalSymbolCompletion.toLookupElement(project)`:
   - Attach `LuaAutoImportInsertHandler` only when `isImported == false`.
   - Add `(auto-import)` tail text when `isImported == false` and `showAutoImportHints == true`.
4. Verify Phase 1 imported-symbol items never receive the handler.

**Integration tests** (5–8):
- [ ] Non-imported symbol triggers insert handler on selection
- [ ] Imported symbol (Phase 1 path) does NOT trigger insert handler
- [ ] Tail text present for non-imported items, absent for imported items
- [ ] Phase 1 + 2 combined completion: no regression

**Success gate**: AC-13, AC-22 pass.

---

### Phase 3.3: Naming & Template Refinement (1–2 hours)
**Dependencies**: Phase 3.1  
**Modified files**: `LuaImportNameResolver.kt`, `LuaAutoImportInsertHandler.kt`

**Tasks**:

1. Implement conflict-suffix incrementing (`name`, `name2`, `name3`, …) using a collected local-name set.
2. Implement Lua reserved keyword guard — validate against an exhaustive keyword list for Lua 5.1–5.4.
3. Implement `@class` name verbatim preservation (no case transformation; user's annotation is authoritative).
4. Handle `AutoImportStyle.FORCE_LOCAL_ASSIGN` and `FORCE_GLOBAL` settings overrides.

**Unit tests** (5–8):
- [ ] Conflict suffix: name → name2 → name3
- [ ] Keyword guard for all 22 Lua 5.4 reserved words as filenames
- [ ] Settings override: `FORCE_GLOBAL` always produces standalone `require`
- [ ] Settings override: `FORCE_LOCAL_ASSIGN` always produces `local x = require`

**Success gate**: AC-14, AC-17 pass; TC-03-11 through TC-03-14 pass.

---

### Phase 3.4: Edge Cases & Robustness (1–2 hours)
**Dependencies**: Phase 3.1, Phase 3.2  
**Modified files**: All Phase 3 components

**Tasks**:

1. Guard `targetFile.isValid` at handler entry; return early if false.
2. Handle read-only file: wrap document mutation in a `ReadonlyStatusHandler` check; show IDE notification on failure.
3. Handle `init.lua` at project root (would produce empty string module path): warn and use filename as fallback.
4. Handle file with only blank lines (no PSI children to anchor against): insert at offset 0.
5. Verify shebang classification in `findLastHeaderElement`.

**Tests** (5–7):
- [ ] Invalid `VirtualFile` → handler returns without crash
- [ ] Read-only file → user-visible notification; no exception propagated
- [ ] Root `init.lua` → fallback module path
- [ ] Blank-only file → insert at offset 0
- [ ] Shebang + no code → import inserted after shebang

**Success gate**: AC-21 passes; all edge-case TCs pass.

---

### Phase 3.5: Comprehensive Testing (2–3 hours)
**Dependencies**: All phases  
**New files**: `LuaAutoImportTest.kt`, `LuaAutoImportIntegrationTest.kt`

**Automated tests**:
- **22–28 unit tests** (isolated, mock `VirtualFile`/project): all components
- **18–22 integration tests** (`myFixture.configureByText` / multi-file fixture): TC-03-01 through TC-03-26
- Full regression run of COMP-03-01 (Phase 1) and COMP-03-02 (Phase 2) test suites

**Manual tests** (6):
- [ ] Auto-import with a real multi-module project; verify inserted path is correct
- [ ] Undo after auto-import: both symbol text and `require` revert correctly
- [ ] Large file (500+ statements): handler completes visibly < 100ms
- [ ] `showAutoImportHints = false`: tail text not shown
- [ ] `autoImportStyle = FORCE_GLOBAL`: always inserts bare `require(...)` regardless of module style
- [ ] Mixed Lua version project (5.1 pattern vs. 5.4 `@class`): correct template chosen

**Traceability**:
- AC-13 through AC-24 each covered by ≥ 1 test
- TC-03-01 through TC-03-26 each covered by ≥ 1 integration or unit test

**Success gate**: All automated tests pass; all 6 manual tests signed off.

---

## Effort Summary

| Phase | Estimated Hours | Dependencies |
| :--- | :--- | :--- |
| DR-01 | 1 | None |
| DR-02 | 1–2 | None |
| DR-03 | 1 | None |
| DR-04 | 1 | None |
| 3.1 | 3–4 | All de-risking |
| 3.2 | 1–2 | 3.1 |
| 3.3 | 1–2 | 3.1 |
| 3.4 | 1–2 | 3.1, 3.2 |
| 3.5 | 2–3 | All phases |
| **Total** | **12–17** | Phase 2 complete |

---

## Quality Gates (Pre-Merge)

- ✅ All automated tests pass (unit + integration)
- ✅ Phase 1 (Task 343) and Phase 2 (Task 350) test suites show zero regressions
- ✅ Undo works independently for auto-import insertion (verified in TC-03-19)
- ✅ Dumb mode: no crash; fallback template used (AC-21)
- ✅ Read-only file: graceful notification, no exception (edge case)
- ✅ Handler performance < 25ms for typical files; < 100ms for large files (TC-03-18)
- ✅ Code review approved (Kotlin naming, `val`-preference, max-30-line methods, no `!!`)
- ✅ Traceability matrix complete (all ACs and TCs mapped to tests)

---

## File Impact Summary

### New Files
| File | Package |
| :--- | :--- |
| `LuaAutoImportInsertHandler.kt` | `lang/completion/` |
| `LuaExportStyleDetector.kt` | `lang/completion/` |
| `LuaImportNameResolver.kt` | `lang/completion/` |
| `LuaDeduplicationChecker.kt` | `lang/completion/` |
| `LuaImportInserter.kt` | `lang/completion/` |
| `LuaModulePathResolver.kt` | `lang/path/` |
| `LuaAutoImportTest.kt` | `test/` |
| `LuaAutoImportIntegrationTest.kt` | `test/` |

### Modified Files
| File | Change |
| :--- | :--- |
| `GlobalSymbolRankingService.kt` | Add `sourceVirtualFile` and `isImported` to `GlobalSymbolCompletion` |
| `LuaCrossFileCompletionProvider.kt` | Attach handler + tail text in `toLookupElement` |
| `LuaProjectSettings.kt` | Add `autoImportStyle: AutoImportStyle` field (default `AUTO_DETECT`) |

### Unchanged (No Modification Required)
- `LuaClassNameIndex.kt`
- `PathConfiguration.kt` (read-only usage)
- `LuaExportedGlobalIndex.kt` (Phase 3 reads stubs directly; no new query method needed)
- Phase 1 completion logic

---

## Risk Mitigation Summary

| Risk | Mitigation | Gate |
| :--- | :--- | :--- |
| Phase 2 not complete | Hard block; Task 350 quality gates must pass first | Task 350 ✅ |
| EDT threading violation | DR-01 prototype; `runReadAction` everywhere | DR-01 ✅ |
| PSI insert corrupts file | DR-02 spike; `commitDocument` always called | DR-02 ✅ |
| Export index missing per-file type | DR-03 fallback (PSI scan) | DR-03 ✅ |
| Source root API absent | DR-04 fallback (`project.basePath`) | DR-04 ✅ |
| Phase 1/2 regression | Full regression suite in Phase 3.5 | All prior tests pass ✅ |

---

## See Also

- **Requirements**: [[requirements|Phase 3 Requirements]]
- **Design**: [[design|Technical Design]]
- **Risks & Gaps**: [[risks-and-gaps|Risks & Gaps]]
- **Phase 2 Plan**: [[../02-project-wide-globals/implementation-plan|Phase 2 Implementation Plan]]
- **Tracker**: [[saga://task/345|Task 345]]
