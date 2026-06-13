---
id: "COMP-03-02-RISKS"
parent_id: "COMP-03-02"
folders:
  - "[[features/completion/03-cross-file-completion/02-project-wide-globals/requirements|requirements]]"
title: "COMP-03-02: Risks & Gaps"
type: "risk"
status: "done"
---

# COMP-03-02: Risks & Gaps

**Phase 2 of COMP-03: Cross-file Completion**

## Critical Risks

### Risk 1.1: No Reliable Export Detection
**Problem**: Current stubs/indices don't distinguish between local globals and exported globals.  
**Impact**: Phase 2 may suggest internal implementation details, creating noise and false suggestions.  
**Likelihood**: High (requires new indexing infrastructure)  
**Mitigation**:
- [ ] Create `LuaExportedGlobalIndex` that encodes export type (function, @class, table return)
- [ ] Analyze file end to detect `return` statements for table/module exports
- [ ] Parse `@class` annotations during indexing

### Risk 1.2: Performance Degradation with Large Projects
**Problem**: `ReferencesSearch` per candidate symbol is O(n*m) where n=symbols, m=files.  
**Impact**: Completion hangs on projects with >5k indexed symbols.  
**Likelihood**: Medium (large Roblox/game projects)  
**Mitigation**:
- [ ] **Remove usage frequency ranking from Phase 2** (defer to Phase 4)
- [ ] Use proximity-only ranking (fast, accurate enough)
- [ ] Add cancellation token support for slow completions
- [ ] Limit StubIndex queries to ~500 candidates before ranking

### Risk 1.3: Incomplete Deduplication Against Phase 1
**Problem**: If Phase 1 (imported symbols) relies on transitive require resolution, deduplication set may be incomplete.  
**Impact**: Same symbol appears twice (once from import, once from globals).  
**Likelihood**: Medium (depends on Phase 1 implementation)  
**Mitigation**:
- [ ] Verify Phase 1 completion includes all imported symbols before Phase 2 deduplication
- [ ] Use symbol PSI element identity, not just name, for deduplication
- [ ] Add test case: symbol from required module should not appear in globals

### Risk 1.4: Index Invalidation Conflicts
**Problem**: If Phase 1 and Phase 2 both cache via `CachedValuesManager`, modifying one might invalidate the other.  
**Impact**: Stale suggestions, incorrect completions after file edits.  
**Likelihood**: Low (CachedValuesManager is dependency-aware)  
**Mitigation**:
- [ ] Verify cache invalidation strategy across both phases
- [ ] Test: Edit a module, check that imports + globals both update

## Design Gaps

### Gap 2.1: Lua Version Compatibility
**Problem**: Export detection varies by Lua version (5.1 vs 5.4, Luau, LuaJIT).  
**Example**: `@class` is LuaCATS-only; standard Lua 5.1 uses module pattern.  
**Impact**: Suggestions may be incomplete or wrong for non-standard Lua versions.  
**Mitigation**:
- [ ] Document supported Lua versions/conventions for this feature
- [ ] Add version checks in export detection logic
- [ ] Consider fallback to simple heuristics for non-LuaCATS projects

### Gap 2.2: Dumb Mode Support
**Problem**: IDE may run in "dumb mode" (indexing disabled) during project load.  
**Impact**: Completions unavailable until indexing complete.  
**Mitigation**:
- [ ] Handle missing/incomplete StubIndex gracefully
- [ ] Provide fallback suggestions (local symbols only)
- [ ] No crashes or errors in dumb mode

### Gap 2.3: Configuration/Settings
**Problem**: Spec mentions "suppression configurable" but no UI/settings defined.  
**Impact**: Users can't customize visibility behavior.  
**Mitigation**:
- [ ] Define settings in `LuaSettings`
- [ ] Add UI panel for visibility rules (suppress `_`, suppress stdlib, etc.)
- [ ] Document setting persistence in project `.idea/lunar.xml`

### Gap 2.4: Symbol Resolution Order
**Problem**: Multiple symbols with same name (different files) — which one is "canonical"?  
**Impact**: Ranking inconsistency, user confusion.  
**Mitigation**:
- [ ] Use file modification time / proximity as tiebreaker
- [ ] Show file path context in suggestion UI
- [ ] Add test case for name collisions

## Technical Debt & Future Work

### TBD: Usage Frequency Ranking
- Currently deferred from Phase 2 (too expensive)
- Consider for Phase 4 if performance is addressed
- Alternative: Track in project metadata (lightweight)

### TBD: Type-aware Ranking
- Ranking by type signature (functions, classes, tables)
- Requires type inference (expensive)
- Defer to future work

### TBD: Project Metadata Cache
- Store symbol metadata (exports, usage) in project settings
- Faster than StubIndex queries
- Requires invalidation strategy

## Pre-Implementation De-risking Tasks

These tasks should be completed **before** Phase 2 full implementation:

| ID | Task | Effort | Goal |
|---|---|---|---|
| DR-01 | Prototype `LuaExportedGlobalIndex` | 2-4 hrs | Verify export detection feasibility |
| DR-02 | Benchmark StubIndex queries (100-5k symbols) | 1-2 hrs | Verify performance targets |
| DR-03 | Test Phase 1 + Phase 2 cache invalidation | 2-3 hrs | Verify no deduplication issues |
| DR-04 | Document Lua version compatibility matrix | 1 hr | Define supported Lua features |

### DR-02 Results

DR-02 benchmarking (recorded by the de-risking task) cleared the performance gates: combined Phase 1 + Phase 2 completion timing was measured at **~76 ms @ 1,000 symbols, ~154 ms @ 5,000, and ~242 ms @ 10,000**. This confirmed that proximity-only ranking with a 500-candidate cap (the Risk 1.2 mitigation) meets the <200 ms target for realistic project sizes, so usage-frequency ranking remained safely deferred to Phase 4. The benchmark harness lives in `GlobalSymbolCompletionPerformanceTest` / `GlobalSymbolPerformanceOptimizationTest`; the cap is `GlobalSymbolRankingService.MAX_CANDIDATES = 500`.

## Test Case Gaps

Current test cases do not cover:
- **TC-02-09**: Dumb mode (indexing disabled)
- **TC-02-10**: Multiple symbols with same name (ranking tiebreaker)
- **TC-02-11**: Cache invalidation after file edit
- **TC-02-12**: Large project performance (5000+ symbols)
- **TC-02-13**: Lua version compatibility (5.1 vs 5.4 export patterns)

## See Also

- **Requirements**: [[requirements|Phase 2 Requirements]]
- **Design**: [[design|Technical Design]]
- **Parent Epic**: [[../requirements|COMP-03 Main Requirements]]
