---
id: "COMP-03-03-RISKS"
parent_id: "COMP-03-03"
folders:
  - "[[features/completion/03-cross-file-completion/03-auto-import/requirements|parent]]"
title: "COMP-03-03: Risks & Gaps"
type: "risk"
---

# COMP-03-03: Risks & Gaps

**Phase 3 of COMP-03: Cross-file Completion**

## Critical Risks

### Risk 3.1: EDT Threading Violation
**Problem**: `InsertHandler.handleInsert` is invoked on the EDT. PSI reads still require explicit `runReadAction { }` even from the EDT. Any I/O, network, or disk access in this path will trigger `SlowOperationsException` and freeze the UI.  
**Impact**: Plugin crash or IDE hang; immediate user-visible defect.  
**Likelihood**: Medium — easy to introduce accidentally in the first implementation pass.  
**Mitigation**:
- [ ] DR-01: Prototype handler; run against full test suite to confirm no `SlowOperationsException`
- [ ] Code-review checklist: every PSI access in the handler must be inside `runReadAction { ... }`
- [ ] Code-review checklist: `WriteCommandAction.runWriteCommandAction` used for all document mutations
- [ ] No disk I/O, file reads, or index queries outside `runReadAction` guards

---

### Risk 3.2: Export Style Detection Is Incomplete for Unannotated Modules
**Problem**: `LuaFile.stub?.exportedTypeString` is only non-null when the root `return` expression has a LuaCATS type annotation. An unannotated `return M` produces `exportedTypeString = null`, requiring a PSI-scan fallback.  
**Impact**: Without the fallback, unannotated return-style modules are incorrectly classified as global-style.  
**Likelihood**: High — most real-world Lua modules do not use LuaCATS annotations.  
**Mitigation**:
- [ ] DR-03: Validate `exportedTypeString` behaviour for annotated and unannotated returns
- [ ] Implement PSI-scan fallback: walk `file.getBlockList().firstOrNull()?.statementList` and check for a `LuaFinalStatement` starting with `return`
- [ ] Default to `RETURN_STYLE` in dumb mode (safer than inserting a bare global-style require)

---

### Risk 3.3: Source Path Pattern Not Configured by User
**Problem**: `LuaModulePathResolver` relies on `PathConfiguration.getProjectSourcePathPatterns(project)`. New users or projects without explicit `package.path`-style source path configuration will receive incorrect module paths (project-relative fallback) in the inserted `require`.  
**Impact**: Inserted `require` path may be wrong; users must manually correct it.  
**Likelihood**: High — source path setup is a non-obvious configuration step for new users.  
**Mitigation**:
- [ ] Project-relative path fallback (better than an empty string or crash)
- [ ] Show a one-time IDE notification: "Configure Lua source paths in Project Settings for accurate auto-import paths."
- [ ] Document source path configuration in user-facing plugin documentation

---

### Risk 3.4: PSI Insert Produces Malformed Syntax
**Problem**: Inserting a string at a raw document offset may corrupt the PSI tree if: (a) the offset splits a multi-character token, (b) `PsiDocumentManager.commitDocument` is not called immediately after, or (c) the file is in an inconsistent state during a bulk operation.  
**Impact**: Broken Lua file; user must undo or manually repair.  
**Likelihood**: Low — `document.insertString` is a well-tested IntelliJ SDK operation — but the risk is non-zero for edge positions (e.g., inside comment tokens).  
**Mitigation**:
- [ ] DR-02: Validate insertion at all three anchor types (after requires, after header, at offset 0) with parse verification
- [ ] Always call `PsiDocumentManager.commitDocument(document)` immediately after `insertString`
- [ ] Integration test: after insertion, re-parse the file and assert zero `ERROR_ELEMENT` nodes

---

### Risk 3.5: Phase 2 Not Complete When Phase 3 Work Begins
**Problem**: Phase 3 requires `GlobalSymbolCompletion` to carry `sourceVirtualFile` and `isImported` fields. If Phase 2 ships without these fields, Phase 3 integration cannot proceed.  
**Impact**: Phase 3 is blocked; implementation stalls.  
**Likelihood**: High if phases are worked concurrently without coordination.  
**Mitigation**:
- [ ] Hard prerequisite: Task 350 (Phase 2) must pass all quality gates before Phase 3.1 starts
- [ ] Pre-coordinate with Phase 2 author: include `sourceVirtualFile` and `isImported` in the Phase 2 data model from the start, even if Phase 3 does not yet use them

---

## Design Gaps

### Gap 3.1: Module Path Disambiguation When Multiple Source Roots Match
**Problem**: If a file path is a valid suffix of two different source roots (unusual, but possible in projects with both `src/` and `src/main/lua/`), two valid module paths could be derived.  
**Impact**: Inserted require path may not match the convention used elsewhere in the project.  
**Status**: Accepted limitation for Phase 3.  
**Mitigation**:
- [ ] Use longest-prefix source root match (most specific wins)
- [ ] Log a warning when multiple roots match
- [ ] Interactive disambiguation UI deferred to a future enhancement

---

### Gap 3.2: Require String Quote Style Inconsistency
**Problem**: Lua accepts both `require("mod")` and `require('mod')`. Phase 3 always generates double-quoted strings, which may conflict with a project using single-quote conventions.  
**Impact**: Minor cosmetic inconsistency; no functional issue.  
**Status**: Accepted limitation for Phase 3.  
**Mitigation**:
- [ ] Default to double quotes (most common in Lua 5.1+ community)
- [ ] Future enhancement: detect existing quote style in the current file and match it

---

### Gap 3.3: No Auto-import for Existing Unresolved References
**Problem**: Phase 3 only triggers via completion selection. An already-typed symbol name that is unresolved does not receive an auto-import offer; the user must delete and re-type it to trigger completion.  
**Impact**: Degraded UX for users who type ahead of completion.  
**Status**: Known gap; out of scope for Phase 3.  
**Mitigation**:
- [ ] Document as known limitation
- [ ] Plan a separate Inspection + Intention Action feature (unresolved reference → offer auto-import) for a future phase

---

### Gap 3.4: Auto-import Hint Visibility
**Problem**: Without a visible signal in the completion popup, users may be surprised by code appearing in their file upon selecting a global symbol. Unexpected document modification can erode trust.  
**Impact**: User confusion; surprise edits.  
**Status**: Addressed by tail text in design, but toggle is settable.  
**Mitigation**:
- [ ] Default `showAutoImportHints = true`; tail text `(auto-import)` is shown
- [ ] `LuaProjectSettings` toggle allows users to disable if distracting
- [ ] Consider a balloon tooltip on first use (optional UX improvement, deferred)

---

### Gap 3.5: Undo Granularity
**Problem**: The completion framework inserts the selected symbol text as one document operation, and Phase 3 inserts the `require` as a second `WriteCommandAction`. Depending on how IntelliJ groups undo history, a single Ctrl+Z may revert both or only one of them.  
**Impact**: Unexpected undo behaviour; user may expect one Undo to remove both changes.  
**Status**: Partially mitigated; requires manual testing.  
**Mitigation**:
- [ ] Use a distinctly named `WriteCommandAction` (`"Auto-import <modulePath>"`) to ensure it is a separate undo step
- [ ] TC-03-19 explicitly tests undo behaviour
- [ ] If both revert together (grouped by IDE), document this as intended (single logical operation)

---

### Gap 3.6: `require` Alias Support
**Problem**: Some Lua projects re-assign `require` to a different name (e.g., `local req = require; req("mod")`). The deduplication checker scans only for direct `require("...")` calls and will miss these aliases, potentially producing duplicate imports.  
**Impact**: Duplicate imports in projects using `require` aliases.  
**Likelihood**: Low — uncommon pattern in practice.  
**Status**: Known limitation; accepted for Phase 3.  
**Mitigation**:
- [ ] Document as known limitation
- [ ] Deferred: alias resolution requires interprocedural analysis (future work)

---

## Pre-Implementation De-risking Tasks

| ID | Task | Effort | Goal |
| :--- | :--- | :--- | :--- |
| DR-01 | Prototype `InsertHandler` on EDT; validate threading model | 1 hr | No `SlowOperationsException`; confirm `runReadAction` requirements |
| DR-02 | Spike PSI/document insert API; verify syntax validity post-insert | 1–2 hrs | Confirm `insertString` + `commitDocument` produces clean PSI |
| DR-03 | Validate `LuaFile.stub?.exportedTypeString` + PSI-scan fallback | 1 hr | Confirm export style detection for annotated and unannotated returns |
| DR-04 | Validate `PathConfiguration.getProjectSourcePathPatterns` reverse-interpolation | 1 hr | Confirm path resolver approach works end-to-end for nested paths and `init.lua` |

---

## Test Case Gaps

TC-03-20 through TC-03-26 from the original gap analysis have been promoted into the canonical requirements test table. The following additional edge cases remain as implementation-time reminders for Phase 3.5:

| Gap ID | Scenario | Note |
| :--- | :--- | :--- |
| TC-03-27 | Multiple overlapping source path patterns | Longest leading prefix selected |
| TC-03-28 | `require(varName)` (non-string arg) | Dedup correctly ignores it |
| TC-03-29 | File at project root, no subdirectory | Single-segment module path (`"config"`) |
| TC-03-30 | Cancellation before handler completes | No resource leaks; no partial insertion |

---

## Technical Debt & Future Work

### TBD: Interactive Module Path Picker
- When multiple source roots match, show a chooser popup.
- Requires UI work; deferred to post-Phase 3.

### TBD: Quote Style Matching
- Detect project's preferred quote style and match it in inserted `require`.
- Minor quality-of-life improvement; deferred.

### TBD: Auto-import Quick-Fix Inspection
- Scan file for unresolved names; offer "Add require" as an Intention Action.
- Separate feature from completion-triggered auto-import; deferred.

### TBD: `require` Alias Handling
- Detect `local req = require` and include aliased calls in deduplication scan.
- Low priority; requires inter-statement analysis.

---

## See Also

- **Requirements**: [[requirements|Phase 3 Requirements]]
- **Design**: [[design|Technical Design]]
- **Implementation Plan**: [[implementation-plan|Implementation Plan]]
- **Phase 2 Risks**: [[../02-project-wide-globals/risks-and-gaps|Phase 2 Risk Assessment]]
