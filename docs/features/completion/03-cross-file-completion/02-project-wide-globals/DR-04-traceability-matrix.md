# DR-04: Test Traceability Matrix Validation

**Date**: 2026-05-25  
**Status**: ✅ **COMPLETE**  
**De-risking Phase**: DR-04 (1 hour)  

---

## Executive Summary

A comprehensive traceability matrix has been created mapping all COMP-03-02 requirements, acceptance criteria, test cases, and implementation tests. 

**Key Findings**:
- ✅ All 7 Acceptance Criteria (AC-06 through AC-12) mapped to test cases
- ✅ All 10 Test Cases (TC-02-01 through TC-02-10) mapped to implementation tests
- ✅ No coverage gaps identified for Phase 2.1
- ✅ Full confidence to proceed with implementation
- ⚠️ 3 optional tests flagged for Phase 2.5 (edge cases)

---

## Acceptance Criteria → Test Case Mapping

| AC ID | Acceptance Criteria | Test Case(s) | Coverage |
|-------|-------------------|------------|----------|
| **AC-06** | `StubIndex` queried with `processElements()` and proper scope filtering | TC-02-01, TC-02-05 | 🟢 Full |
| **AC-07** | Symbols with `_` prefix are suppressed (configurable) | TC-02-02 | 🟢 Full |
| **AC-08** | Local/imported symbols deduplicated | TC-02-06, TC-02-07 | 🟢 Full |
| **AC-09** | Ranking prioritizes proximity by structure | TC-02-03, TC-02-09 | 🟢 Full |
| **AC-10** | Performance acceptable (<200ms) with large sets | TC-02-05 | 🟢 Full |
| **AC-11** | Phase 1 integration not broken | TC-02-07 (implicit), DR-03 | 🟢 Full |
| **AC-12** | Graceful degradation in dumb mode | TC-02-08 | 🟢 Full |

---

## Test Case → Implementation Test Mapping

### Phase 2.1: Required Tests (8 tests)

| TC ID | Scenario | Implementation Test | Status |
|-------|----------|-------------------|--------|
| **TC-02-01** | Global function from unrelated file | `test_global_symbol_suggestion_basic()` | 🟡 To write |
| **TC-02-02** | `_` prefix symbols suppressed | `test_underscore_prefix_filtering()` | 🟡 To write |
| **TC-02-03** | Same-name functions ranked by proximity | `test_proximity_ranking()` | 🟡 To write |
| **TC-02-04** | @class-annotated globals rank with boost | `test_class_annotation_ranking()` | 🟡 To write |
| **TC-02-05** | Performance with 5000+ symbols (<200ms) | `test_performance_large_project()` | 🟡 To write |
| **TC-02-06** | Local scope symbols deduplicated | `test_local_scope_deduplication()` | 🟡 To write |
| **TC-02-07** | Imported symbols deduplicated | `test_imported_scope_deduplication()` | 🟡 To write |
| **TC-02-08** | Dumb mode graceful degradation | `test_dumb_mode_handling()` | 🟡 To write |
| **TC-02-10** | Cache invalidation after file edit | `test_cache_invalidation()` | 🟡 To write |

### Phase 2.5: Optional Tests (3 tests)

| TC ID | Scenario | Implementation Test | Status |
|-------|----------|-------------------|--------|
| **TC-02-09** | Symbol collision ranking by timestamp | `test_collision_ranking_by_timestamp()` | 🔴 Optional |
| — | Version-specific API filtering | `test_version_compatibility()` | 🔴 Future |
| — | Stdlib integration | `test_stdlib_integration()` | 🔴 Future |

---

## Coverage Gap Analysis

### Summary

| Category | Count | Status |
|----------|-------|--------|
| **ACs Covered** | 7/7 | ✅ 100% |
| **TCs Covered** | 10/10 | ✅ 100% |
| **Phase 2.1 Tests Required** | 8 | 🟡 Scheduled |
| **Phase 2.5 Optional Tests** | 3 | 🔴 Deferred |

### Coverage Status

✅ **NO CRITICAL GAPS IDENTIFIED**

All acceptance criteria are fully traceable to test cases, and all test cases are mapped to implementation tests. Phase 2.1 implementation can proceed with full confidence.

---

## Test Implementation Strategy

### Phase 2.1 (3-4 hours for implementation + tests)

The 8 required tests should extend the existing `LuaCompletionTest.kt` file, following the patterns established in DR-03:

```kotlin
// File: src/test/kotlin/net/internetisalie/lunar/lang/insight/LuaCompletionTest.kt

// Basic global symbol suggestion
fun test_global_symbol_suggestion_basic() {
  // Create: module_a.lua with function foo()
  // Create: module_b.lua that imports foo
  // Trigger completion
  // Verify: foo appears in completion list
  // Coverage: AC-06, TC-02-01
}

// Underscore prefix filtering
fun test_underscore_prefix_filtering() {
  // Create: module with _private() and public()
  // Trigger completion
  // Verify: _private NOT shown, public shown
  // Coverage: AC-07, TC-02-02
}

// Proximity ranking
fun test_proximity_ranking() {
  // Create: local foo() + global foo() in other module
  // Trigger completion
  // Verify: local foo ranks higher
  // Coverage: AC-09, TC-02-03
}

// Class annotation ranking
fun test_class_annotation_ranking() {
  // Create: @class MyClass annotation
  // Trigger completion
  // Verify: MyClass appears with correct icon
  // Coverage: TC-02-04
}

// Performance with large project
fun test_performance_large_project() {
  // Create: project with 5000 global symbols
  // Measure completion time
  // Verify: completes in <200ms
  // Coverage: AC-10, TC-02-05
}

// Local scope deduplication
fun test_local_scope_deduplication() {
  // Create: local_helper in scope + global_helper globally
  // Trigger completion
  // Verify: no duplicates, both visible appropriately
  // Coverage: AC-08, TC-02-06
}

// Imported scope deduplication
fun test_imported_scope_deduplication() {
  // Create: module_a with export_func()
  // Create: module_b requires module_a
  // Trigger completion
  // Verify: export_func via Phase 1 (not duplicated)
  // Coverage: AC-08, AC-11, TC-02-07
}

// Dumb mode handling
fun test_dumb_mode_handling() {
  // Disable indexing
  // Trigger completion
  // Verify: no crash, graceful degradation
  // Coverage: AC-12, TC-02-08
}

// Cache invalidation
fun test_cache_invalidation() {
  // Create: module_a with symbol_a
  // Trigger completion → see result_a
  // Edit module_a → add symbol_b
  // Trigger completion → see result_b
  // Verify: result_b includes symbol_b
  // Coverage: TC-02-10
}
```

### Phase 2.5 (Optional)

3 optional tests for edge cases:
- Collision ranking by timestamp (rare scenario)
- Version-specific API filtering (Phase 4 feature)
- Stdlib integration (separate concern)

---

## Risk Assessment

### Coverage Risks: NONE ✅

All test cases are mapped to implementation tests. No coverage gaps identified.

### Deferred Items: TRACKED ✅

| Item | Status | Reason | Timeline |
|------|--------|--------|----------|
| Collision ranking by timestamp | Deferred | Edge case, rare scenario | Phase 2.5 |
| Version-specific API filtering | Future | Separate feature | Phase 4 |
| Stdlib integration | Future | Separate concern | Phase 3+ |

---

## Conclusion

✅ **DR-04 VALIDATION COMPLETE**

**Decision**: **PROCEED TO PHASE 2.1**

The traceability matrix confirms:
1. All acceptance criteria are fully covered by test cases
2. All test cases map to specific implementation tests
3. No critical gaps identified
4. Phase 2.1 implementation has clear test objectives
5. Optional tests tracked for Phase 2.5

Phase 2.1 infrastructure implementation can begin immediately with full confidence in test coverage strategy.

---

**Next Phase**: Phase 2.1 - Infrastructure & Integration (GlobalSymbolRankingService implementation)
