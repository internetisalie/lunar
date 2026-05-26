---
id: "MAINT-04"
folders:
  - "[[features/maint/requirements|MAINT]]"
priority: medium
status: done
vf_icon: ✅
title: "04: Refactor Symbol Resolution"
type: "feature"
parent_id: "MAINT"
---
# Requirements: MAINT-04 — Refactor Symbol Resolution (PsiScopeProcessor)

## 1. Overview

This document defines the detailed functional and non-functional requirements for MAINT-04: the
replacement of the eager `LuaBindingsVisitor`-based symbol resolution system with a lazy
`PsiScopeProcessor`-based model conforming to IntelliJ Platform conventions.

The de-risking test suite is defined in `02-prep-symbol-resolution-tests.md` and its 54 active
tests serve as the behavioural contract this implementation must satisfy.

---

## 2. Scope

### In Scope
- Lazy local symbol resolution via `PsiScopeProcessor` and `PsiTreeUtil.treeWalkUp()`
- `processDeclarations()` on all scope-introducing PSI elements
- Refactored `LuaNameReference.multiResolve()` and `LuaNameReference.getVariants()`
- Preservation of external (global / require / `StubIndex`) resolution in `LuaNameReference`
- Preservation of `LuaBindingsVisitor` as an **indexer-only** helper for `LuaFileBindingsIndexer`
- Performance: elimination of full-file AST traversal on every keypress

### Out of Scope
- Label resolution refactoring (tracked separately as MAINT-02, unblocked by this work)
- Find Usages infrastructure (tracked as a future navigation feature)
- Completion engine changes (tracked as a future feature)
- LuaCATS type inference integration (TYPE-01, a dependent of this work)

---

## 3. Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [MAINT-04-01](#maint-04-01-luascopeprocessor) | **LuaScopeProcessor** | M | **Not Implemented** | Implement a `PsiScopeProcessor` subclass that accepts named element declarations and stops when the target name is found. |
| [MAINT-04-02](#maint-04-02-luablock-processdeclarations) | **LuaBlock.processDeclarations()** | M | **Not Implemented** | Expose all locals and local functions declared *before* the stop element within the block. |
| [MAINT-04-03](#maint-04-03-luafile-processdeclarations) | **LuaFile.processDeclarations()** | M | **Not Implemented** | Expose file-level locals, delegating to the root block. |
| [MAINT-04-04](#maint-04-04-function-parameter-scope) | **Function Parameter Scope** | M | **Not Implemented** | `LuaFuncDef` and `LuaFuncDecl` must expose their `LuaParList` names via `processDeclarations()`. |
| [MAINT-04-05](#maint-04-05-for-loop-scope) | **For-loop Variable Scope** | M | **Not Implemented** | `LuaNumericForStatement` and `LuaGenericForStatement` must expose their loop variables via `processDeclarations()`. |
| [MAINT-04-06](#maint-04-06-namereference-refactoring) | **LuaNameReference Refactoring** | M | **Not Implemented** | `multiResolve()` must use `PsiTreeUtil.treeWalkUp()` + `LuaScopeProcessor` for local resolution, then fall back to external resolution. |
| [MAINT-04-07](#maint-04-07-preserve-luafilebindingsindex) | **Preserve LuaFileBindingsIndex** | M | **Not Implemented** | `LuaBindingsVisitor` must be retained but scoped to `LuaFileBindingsIndexer` only; must not be called during name resolution. |
| [MAINT-04-08](#maint-04-08-preserve-external-resolution) | **Preserve External Resolution** | M | **Not Implemented** | Global / platform-library / `StubIndex` resolution in `LuaNameReference` must continue to function correctly after local-resolution is migrated. |
| [MAINT-04-09](#maint-04-09-baseline-test-suite) | **Baseline Test Suite Passes** | M | **Not Implemented** | All 54 active MAINT-04-DR tests must pass with the new implementation. |
| [MAINT-04-10](#maint-04-10-performance) | **Performance — No Full-File Walk** | M | **Not Implemented** | `LuaBindingsVisitor.getBindings()` must not be invoked from `LuaNameReference` or any hot path triggered by typing. |

---

## 4. Detailed Specifications

### MAINT-04-01: LuaScopeProcessor

**File:** `src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt` (new)

**Behaviour:**
- Extend `BaseScopeProcessor` (IntelliJ SDK).
- Accept a `name: String` constructor parameter representing the symbol being searched.
- `execute(element: PsiElement, state: ResolveState): Boolean`
  - If `element` is a named declaration (`LuaLocalVarDecl`, `LuaLocalFuncDecl`, `LuaParList` name, loop variable) whose name matches, record it and return `false` to stop the walk.
  - Otherwise return `true` to continue.
- Expose `val result: PsiElement?` — `null` if not found.
- For completion (`getVariants`), a second variant `LuaCompletionScopeProcessor` collects all names without stopping.

**Acceptance Criteria:**
- Processor stops on first exact match for `multiResolve`.
- Processor collects all visible names for `getVariants`.
- Does not traverse into sibling function bodies (scope isolation).

---

### MAINT-04-02: LuaBlock.processDeclarations()

**File:** `LuaBlock` PSI element (generated or mixed-in via `LuaBlockMixin`)

**Behaviour:**
- Override `processDeclarations(processor, state, lastParent, place)`.
- Iterate over direct child statements in source order.
- Stop iterating when reaching `lastParent` (i.e., declarations after the usage site must not be visible — early-binding rule).
- Feed each `LuaLocalVarDecl` identifier and each `LuaLocalFuncDecl` identifier to the processor.
- Return `false` immediately if the processor returns `false`.

**Acceptance Criteria:**
- `testSimpleLocalVariable`, `testNestedBlockScoping`, `testMultipleDeclarationsInScope` pass.
- `testRedeclarationInSameScope` resolves to the *first* declaration visible before usage.
- Variables declared *after* the reference site are not visible.

---

### MAINT-04-03: LuaFile.processDeclarations()

**File:** `LuaFile.kt`

**Behaviour:**
- Delegate to `LuaBlock.processDeclarations()` of the root block.
- The walk terminates here for purely local resolution; `LuaNameReference` then starts external resolution.

**Acceptance Criteria:**
- File-level locals resolve correctly.
- Walk does not recurse into other files.

---

### MAINT-04-04: Function Parameter Scope

**Files:** `LuaFuncDef`, `LuaFuncDecl`, `LuaLocalFuncDecl` PSI mixins

**Behaviour:**
- `processDeclarations()` feeds all parameter names from `LuaParList` into the processor before delegating to the function body block.
- The implicit `self` parameter (for method declarations using `:`) must also be exposed.

**Acceptance Criteria:**
- `testFunctionParameterResolution`, `testMultipleParameters` pass.
- `testNestedFunctions` — inner function parameters do not bleed into outer scope.
- `self` resolves inside method bodies.

---

### MAINT-04-05: For-loop Variable Scope

**Files:** `LuaNumericForStatement`, `LuaGenericForStatement` PSI mixins

**Behaviour:**
- `LuaNumericForStatement.processDeclarations()` — exposes the loop counter identifier.
- `LuaGenericForStatement.processDeclarations()` — exposes all names from `LuaNameList`.
- Variables are scoped to the loop body; they must not be visible after the loop.

**Acceptance Criteria:**
- `testForLoopVariableResolution`, `testGenericForLoopMultipleVariables` pass.
- Loop variables are not visible in code following the loop statement.

---

### MAINT-04-06: LuaNameReference Refactoring

**File:** `LuaNameReference.kt`

**Behaviour — `multiResolve()`:**
1. Construct `LuaScopeProcessor(name)`.
2. Call `PsiTreeUtil.treeWalkUp(processor, element, element.containingFile, ResolveState.initial())`.
3. If `processor.result != null`, return it as the single local result.
4. Otherwise fall through to external resolution (unchanged from current step 2 of `multiResolve`):
   - Query `LuaFileBindingsIndex` via `queryFiles(platformQuery, requiresQuery)`.
   - Query `StubIndex` (`LuaClassNameIndex`, `LuaAliasIndex`, `LuaGlobalDeclarationIndex`).

**Behaviour — `getVariants()`:**
- Use `LuaCompletionScopeProcessor` to walk up the tree and collect all visible names.
- Replace the current `LuaBindingsVisitor.getBindings(element)` call.

**Acceptance Criteria:**
- All 54 MAINT-04-DR baseline tests pass.
- `LuaBindingsVisitor.getBindings()` is no longer called from `LuaNameReference`.
- External (global/require) resolution continues to work.
- `distinctBy { it.element }` de-duplication is preserved.

---

### MAINT-04-07: Preserve LuaFileBindingsIndex

**File:** `LuaFileBindingsIndexer.kt` — no change required.

**Constraint:**
- `LuaBindingsVisitor` continues to be used exclusively in `LuaFileBindingsIndexer.computeValue()`.
- Remove all other call sites of `LuaBindingsVisitor.getBindings()` and `LuaBindingsVisitor.getBindingsWithImports()` outside the indexer.
- The `bindingsKey` and `bindingsFullKey` `CachedValue` entries in `LuaBindingsVisitor.Companion` may be removed once no longer referenced.

**Acceptance Criteria:**
- `FileBasedIndex` still builds the `LuaFileBindings` index correctly.
- Cross-file global navigation continues to function.

---

### MAINT-04-08: Preserve External Resolution

**File:** `LuaNameReference.kt`

**Behaviour:**
- After local `treeWalkUp` produces no result, the existing external resolution pipeline runs unchanged:
  - `VirtualFilesQuery` for platform library lookups.
  - `RequiredFilesQuery` for `require()`-imported module exports.
  - `StubIndex` lookups for `LuaClassNameIndex`, `LuaAliasIndex`, `LuaGlobalDeclarationIndex`.
- The `LuaImports` object is no longer constructed during local resolution; it is built on-demand only when local resolution fails.

**Acceptance Criteria:**
- Global variable navigation works across files.
- `require()` module navigation resolves correctly.
- Platform library symbols resolve correctly.

---

### MAINT-04-09: Baseline Test Suite Passes

**Test files** (from MAINT-04-DR):
```
src/test/kotlin/net/internetisalie/lunar/lang/
├── LuaSymbolResolutionTest.kt     (10 active)
├── LuaGlobalResolutionTest.kt     (14 active)
├── LuaRequireResolutionTest.kt    (12 active)
├── LuaGotoDefinitionTest.kt       (18 active)
├── LuaLabelResolutionTest.kt      (0 active — all @Disabled)
├── LuaFindUsagesTest.kt           (0 active — all @Disabled)
└── LuaCompletionIntegrationTest.kt (0 active — all @Disabled)
```

**Acceptance Criteria:**
- `./gradlew test --tests "*.LuaSymbolResolutionTest"` — 10/10 pass.
- `./gradlew test --tests "*.LuaGlobalResolutionTest"` — 14/14 active pass.
- `./gradlew test --tests "*.LuaRequireResolutionTest"` — 12/12 active pass.
- `./gradlew test --tests "*.LuaGotoDefinitionTest"` — 18/18 active pass.
- Zero regressions in the full test suite (`./gradlew test`).

---

### MAINT-04-10: Performance — No Full-File Walk

**Constraint:**
- `LuaBindingsVisitor.getBindings(element)` and `getBindingsWithImports(element)` must not appear in any call path reachable from:
  - `LuaNameReference.multiResolve()`
  - `LuaNameReference.getVariants()`
  - Any `PsiReference` implementation triggered during editing.
- `PsiTreeUtil.treeWalkUp()` is inherently lazy and stops at the first match; no full-file traversal occurs for local resolution.

**Verification:**
- Code review: grep for `LuaBindingsVisitor.getBindings` outside `LuaFileBindingsIndexer`.
- Manual test: open a large Lua file (>500 lines), type a local variable reference, observe no noticeable latency spike in the IDE event log.

---

## 5. Non-Functional Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| NFR-01 | **Correctness** | M | All 54 MAINT-04-DR baseline tests pass with zero regressions. |
| NFR-02 | **Performance** | M | Name resolution for a local variable in a 1000-line file completes in <10ms (no full-file walk). |
| NFR-03 | **Maintainability** | S | `LuaBindingsVisitor` usage is isolated to `LuaFileBindingsIndexer`; all other callers removed. |
| NFR-04 | **Platform Conformance** | S | `processDeclarations()` and `PsiScopeProcessor` follow IntelliJ SDK conventions (see `JavaRecursiveElementVisitor` / Kotlin plugin as reference). |
| NFR-05 | **No Regressions** | M | Full `./gradlew test` suite passes; no pre-existing tests broken. |

---

## 6. Dependencies

| Dependency | Direction | Notes |
| :--- | :--- | :--- |
| **MAINT-04-DR** (Epic 19) | Prerequisite | 54-test baseline must be green before MAINT-04 merge. ✅ Complete. |
| **MAINT-02** (Label refactoring) | Dependent | MAINT-04 unblocks MAINT-02. Label resolution migration is out of scope here. |
| **TYPE-01** (Basic type inference) | Dependent | Requires lazy scope resolution as a foundation for control-flow-aware type narrowing. |

---

## 7. Risks

| Risk | Likelihood | Impact | Mitigation |
| :--- | :---: | :---: | :--- |
| `processDeclarations()` ordering differs from `LuaBindingsVisitor` (e.g., forward references) | Medium | High | MAINT-04-DR test suite catches ordering differences immediately. |
| External resolution broken when `LuaImports` construction is deferred | Low | High | MAINT-04-08 requirement explicitly preserves the pipeline; MAINT-04-DR includes require/global tests. |
| `LuaFileBindingsIndex` broken by removing `LuaBindingsVisitor` call sites | Low | High | MAINT-04-07 explicitly preserves the indexer; global navigation tests verify it post-refactoring. |
| Performance regression if `treeWalkUp` is inadvertently called too many times | Low | Medium | MAINT-04-10 mandates explicit verification; profile if needed. |

---

## 8. Acceptance Checklist

- [ ] `LuaScopeProcessor` implemented and unit-tested.
- [ ] `processDeclarations()` overridden on: `LuaBlock`, `LuaFile`, `LuaFuncDef`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaNumericForStatement`, `LuaGenericForStatement`.
- [ ] `LuaNameReference.multiResolve()` uses `treeWalkUp`; no `LuaBindingsVisitor.getBindings()` call.
- [ ] `LuaNameReference.getVariants()` uses scope processor; no `LuaBindingsVisitor.getBindings()` call.
- [ ] `LuaBindingsVisitor` call sites outside `LuaFileBindingsIndexer` removed.
- [ ] All 54 MAINT-04-DR active tests pass.
- [ ] Full `./gradlew test` suite passes.
- [ ] `./gradlew ktlintCheck` passes.
- [ ] Manual verification: goto definition and require navigation work in sandbox IDE.
