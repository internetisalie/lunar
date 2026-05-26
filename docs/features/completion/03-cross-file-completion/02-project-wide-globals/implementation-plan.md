---
id: "COMP-03-02-PLAN"
parent_id: "COMP-03-02"
folders:
  - "[[features/completion/03-cross-file-completion/02-project-wide-globals/requirements|requirements]]"
title: "COMP-03-02: Implementation Plan"
type: "plan"
status: "done"
---

# COMP-03-02: Implementation Plan

**Phase 2 of COMP-03: Cross-file Completion**

## Overview

Phase 2 implements project-wide global symbol suggestions by leveraging existing indexing (`LuaGlobalDeclarationIndex`, `LuaClassNameIndex`, file stubs) and integrating with the completion provider using proximity-based ranking.

**Tracker**: [[saga://task/350|Task 350]]  
**Estimated Effort**: 12-16 hours (de-risking + implementation + testing)  
**Critical Path**: DR-01 → DR-02 → Phase 2.1 → Phase 2.5

## Pre-Implementation Assumptions

- Phase 1 (Task 343) is complete: `LuaCrossFileCompletionProvider` has require-based completion
- `LuaClassNameIndex` indexes `@class` declarations correctly
- `PrioritizedLookupElement` available in IDE SDK (verified ✅)
- Index strategy (new `LuaExportedGlobalIndex` vs. enhance `LuaGlobalDeclarationIndex`) will be decided in DR-01

---

## De-Risking Phase (4-10 hours)

**MUST complete all de-risking before implementation.**

### DR-01: Verify Index Infrastructure (1-2 hours)
**Deliverable**: Index strategy decision + code spike

1. Inspect `LuaGlobalDeclarationIndex` and `LuaFileStub.exportedTypeString`
2. Determine: new index vs. enhance existing (new is safer if it avoids navigation/docs regression)
3. Verify `LuaClassNameIndex` handles `@class` correctly
4. Verify `PathConfiguration` can compute module proximity

**Blocker**: If export metadata unavailable → design must change.

### DR-02: Benchmark Performance Targets (2-3 hours)
**Deliverable**: Benchmark results table + profiling snapshot

1. Create test projects: 500, 1000, 5000 symbols
2. Measure Phase 1 (baseline), Phase 2, and combined times
3. Identify bottlenecks; profile hot paths
4. Verify targets: Phase 2 <100ms, Combined <250ms

**Targets**:
- 1000 symbols: Phase 2 <100ms, Combined <250ms
- 5000 symbols: Phase 2 <200ms, Combined <400ms
- 10000 symbols: Graceful degradation (cancellation works)

**Fallback**: If targets miss, implement function-only suggestions (skip tables) as MVP.

**Blocker**: If cancelled → design must change.

### DR-03: Verify Phase 1 + Phase 2 Integration (1-2 hours)
**Deliverable**: Test project + manual test steps

1. Test file C: requires A, global function G defined in B
2. In C, trigger completion → A's symbols (Phase 1), G (Phase 2)
3. Verify no duplicates
4. Verify cache invalidation works (edit A → suggestions update)
5. Test circular dependency (A → B → A) → no crash

**Blocker**: If deduplication or cache fails → design must change.

### DR-04: Validate Test Traceability (1 hour)
**Deliverable**: Traceability matrix (Requirements → AC → TC → Unit/Integration Tests)

1. Map TC-02-01 through TC-02-10 to AC-06 through AC-12
2. Ensure no gaps in coverage
3. Identify missing tests (dumb mode, version compatibility, etc.)

**Blocker**: If coverage gaps found → add tests to Phase 2.5.

---

## Implementation Phases (6-8 hours)

### Phase 2.1: Infrastructure & Integration (3-4 hours, CRITICAL PATH)
**Dependencies**: All de-risking complete  
**Files**: New/enhanced index, GlobalSymbolRankingService, LuaCompletionContributor

**Tasks**:
1. Index implementation (based on DR-01 decision)
   - New `LuaExportedGlobalIndex` OR enhance `LuaGlobalDeclarationIndex`
   - Verify export type metadata accessible
   - Ensure no regression in navigation/docs/parameter-info

2. Implement `GlobalSymbolRankingService` (project-scoped singleton)
   - `fun getProjectGlobalSymbols(context, localSymbols, importedSymbols): List<GlobalSymbolCompletion>`
   - Query index with `processElements()`
   - Check `DumbService.isDumb()` → return empty if true
   - Filter visibility (`_` prefix)
   - Deduplicate (local by name, imported by PSI identity)
   - Calculate proximity weights (0.9, 0.7, 0.5)
   - Return sorted list

3. Integrate into `LuaCrossFileCompletionProvider`
   - Add Phase 2 results with `PrioritizedLookupElement.withPriority()`
   - Add `ProgressManager.checkCanceled()` in loops
   - Catch `ProcessCanceledException` gracefully

**Tests** (15-20 unit tests):
- [ ] Index queries (3)
- [ ] Service logic (8)
- [ ] Dumb mode (1)
- [ ] Cancellation (1)
- [ ] No Phase 1 regression (1)

**Success**: AC-06, AC-12 pass; no Phase 1 regression.

---

### Phase 2.2: Ranking & Deduplication (1-2 hours)
**Dependencies**: Phase 2.1  
**Files**: GlobalSymbolRankingService (extend)

**Tasks**:
1. Implement `ProximityCalculator`
   - Weights: same module 0.9, same directory 0.7, different 0.5
   - Use `PathConfiguration` for module resolution

2. Implement deduplication by PSI element identity (not just name)

3. Integrate `PrioritizedLookupElement` in completion UI

**Tests** (12-15 unit/integration tests):
- [ ] Proximity calculation (8)
- [ ] Deduplication (4)
- [ ] Ranking order (1)

**Success**: AC-08, AC-09 pass; TC-02-01, TC-02-03, TC-02-06, TC-02-07 pass.

---

### Phase 2.3: Visibility & Settings (1-2 hours, upgraded to MUST)
**Dependencies**: Phase 2.1  
**Files**: GlobalSymbolRankingService, LuaSettings

**Tasks**:
1. Filter `_` prefixed symbols based on `LuaSettings.suppressUnderscorePrefixed` flag
2. Add settings (project-level, `.idea/lunar.xml`)
3. Document Lua version compatibility rules (5.1, 5.4, Luau)

**Tests** (5-8 unit tests):
- [ ] `_` prefix filtering (2)
- [ ] Settings persistence (1)
- [ ] Version-specific rules (2)

**Success**: AC-07 passes; TC-02-02 passes.

---

### Phase 2.4: Performance Tuning (1-2 hours)
**Dependencies**: Phase 2.1, Phase 2.2  
**Files**: GlobalSymbolRankingService, GlobalSymbolPerformanceTest.kt (new)

**Tasks**:
1. Implement cancellation in loops (`ProgressManager.checkCanceled()`)
2. Limit candidates to ~500 before ranking (heuristic)
3. Profile based on DR-02 results; optimize if needed
4. If targets miss, activate fallback: function-only suggestions

**Tests** (3-5 performance tests):
- [ ] Benchmark 1000 symbols
- [ ] Benchmark 5000 symbols
- [ ] Cancellation cleanup

**Success**: AC-10 passes (or fallback activated); TC-02-05 passes.

---

### Phase 2.5: Comprehensive Testing (2-3 hours)
**Dependencies**: All phases  
**Files**: LuaCompletionTest, GlobalSymbolIndexTest (if new index), GlobalSymbolRankingServiceTest

**Tests**:
- **20 fast unit tests** (mock index, no IDE): GlobalSymbolRankingService, proximity, dedup, visibility, dumb mode, cancellation
- **10 indexed integration tests** (real project): TC-02-01 through TC-02-10
- **7 manual tests** (sandbox IDE): completion UI, settings, large projects, version compatibility

**Traceability** (new, from DR-04):
- AC-06 through AC-12 each have ≥1 test
- TC-02-01 through TC-02-10 each have unit/integration test

**Success**: All 30 automated tests pass; manual tests signed off.

---

## Effort Summary

| Phase | Hours | Dependencies |
|-------|-------|--------------|
| DR-01 | 1-2 | None |
| DR-02 | 2-3 | None |
| DR-03 | 1-2 | None |
| DR-04 | 1 | None |
| 2.1 | 3-4 | All de-risking |
| 2.2 | 1-2 | 2.1 |
| 2.3 | 1-2 | 2.1 |
| 2.4 | 1-2 | 2.1, 2.2 |
| 2.5 | 2-3 | All phases |
| **Total** | **12-16** | — |

---

## Risk Mitigation

| Risk | Mitigation | Gate |
|------|-----------|------|
| Export detection infeasible | DR-01 prototype + design review | Index decision approved |
| Performance miss | DR-02 benchmark + fallback plan | Targets met or fallback activated |
| Duplicate suggestions | DR-03 integration test | Cache test passes |
| Version incompatibility | DR-04 + compatibility matrix | Version rules documented |
| Phase 1 regression | Run full COMP-03-01 suite in 2.5 | All Phase 1 tests still pass |
| Index breaks navigation | Design review + DR-01 decision | New index chosen if risky |

---

## Fallback Plans

**Fallback 1** (Performance): Restrict Phase 2 to function-only suggestions (no table/module exports)  
**Fallback 2** (Export Detection): Index only top-level functions; defer classes/tables to Phase 4  
**Fallback 3** (Index Risk): Create new `LuaExportedGlobalIndex` instead of enhancing existing

---

## Quality Gates (Pre-Merge)

- ✅ All 30 tests pass (20 unit + 10 integration)
- ✅ Phase 1 + 2 combined <250ms for 1000 symbols (or fallback activated)
- ✅ No Phase 1 regression (all COMP-03-01 tests still pass)
- ✅ Dumb mode graceful
- ✅ Cancellation verified (no resource leaks)
- ✅ Code review approved (Kotlin standards, naming, formatting)
- ✅ Settings persist across IDE restart
- ✅ Traceability matrix complete (all ACs/TCs covered)

---

## Non-Phase-2 File Impact

Expected modifications:
- `LuaGlobalDeclarationIndex.kt` (enhance) OR `LuaExportedGlobalIndex.kt` (new)
- `LuaCompletionContributor.kt` (Phase 2 integration point)
- `LuaSettings.kt` (visibility flags)

Expected reuse (no changes):
- `LuaClassNameIndex.kt`
- `PathConfiguration.kt`
- Settings UI (if already exists)

---

## See Also

- **Requirements**: [[requirements|Phase 2 Requirements]]
- **Design**: [[design|Technical Design]]
- **Risks & Gaps**: [[risks-and-gaps|Risk Assessment]]
- **Tracker**: [[saga://task/350|Task 350]]
- **Phase 1 Reference**: [[saga://task/343|Task 343]]
