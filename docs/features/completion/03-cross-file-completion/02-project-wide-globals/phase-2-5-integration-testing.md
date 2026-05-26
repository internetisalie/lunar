---
id: "COMP-03-02-QA"
parent_id: "COMP-03-02"
folders:
  - "[[features/completion/03-cross-file-completion/02-project-wide-globals/requirements|requirements]]"
title: "COMP-03-02 Phase 2.5: Integration Testing & Validation"
type: "qa"
---

# COMP-03-02 Phase 2.5: Integration Testing & Validation

**Final Phase of COMP-03-02: Global Symbol Suggestions**

Comprehensive integration testing, acceptance criteria validation, and end-to-end verification of Phase 2.1-2.4 implementations.

## Scope (In-Scope)

### Integration Testing
- **Cross-file Symbol Resolution**: Verify global symbols resolve correctly from different files
- **Deduplication Correctness**: Confirm no duplicate suggestions (locals, imports, globals)
- **Ranking Behavior**: Validate proximity-based ranking (same module > same directory > different modules)
- **Visibility Filtering**: Test underscore-prefixed symbol suppression
- **Performance Validation**: Confirm <200ms completion at 5000+ symbols
- **Cache Behavior**: Verify cache invalidation after file edits
- **Circular Dependencies**: Ensure no crashes with circular imports

### Acceptance Criteria Validation
- **AC-06**: StubIndex queried with proper scope filtering
- **AC-07**: Underscore symbols suppressed (configurable)
- **AC-08**: Deduplication with local/imported symbols
- **AC-09**: Proximity-based ranking correct
- **AC-10**: Performance acceptable (<200ms)
- **AC-11**: Phase 1 integration preserved
- **AC-12**: Graceful degradation in dumb mode

### Test Case Coverage
All test cases from requirements (TC-02-01 through TC-02-10) must have corresponding integration tests.

## Out of Scope

- Auto-import functionality (Phase 3)
- Type-aware ranking (Phase 4)
- Usage frequency tracking (Phase 4)
- UI/UX manual testing (separate QA process)

## Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| **INT-01** | Cross-file symbol integration tests | M | Not Implemented | Test global symbol resolution across multiple files |
| **INT-02** | Deduplication validation tests | M | Not Implemented | Verify no duplicates in completion suggestions |
| **INT-03** | Ranking behavior integration tests | M | Not Implemented | Validate proximity-based ranking order |
| **INT-04** | Visibility filtering integration tests | M | Not Implemented | Test underscore-prefixed symbol suppression |
| **INT-05** | Performance validation tests | M | Not Implemented | Verify <200ms at 5000+ symbols |
| **INT-06** | Cache invalidation tests | M | Not Implemented | Test cache update after file edits |
| **INT-07** | Circular dependency edge cases | M | Not Implemented | Ensure no crashes with circular imports |
| **INT-08** | Acceptance criteria traceability | M | Not Implemented | Document AC fulfillment mapping |
| **INT-09** | Dumb mode graceful degradation | S | Not Implemented | Verify behavior when indexing disabled |
| **INT-10** | Settings UI integration tests | S | Not Implemented | Test visibility settings persistence |

## Test Cases

### Integration Test Suite: GlobalSymbolIntegrationTest

| ID | Scenario | Expected Result | AC Mapped |
| :--- | :--- | :--- | :--- |
| **IT-01** | Type global function name from unrelated file | Function in completion; proximity ranked by module | AC-06, AC-09 |
| **IT-02** | Global with `_` prefix exists; suppression enabled | Symbol NOT in completion | AC-07 |
| **IT-03** | Global with `_` prefix; suppression disabled | Symbol in completion | AC-07 |
| **IT-04** | Two functions same name, different files | Both appear; proximity tiebreaker applied | AC-09 |
| **IT-05** | Project with 5000 indexed symbols | Completion responds <200ms | AC-10 |
| **IT-06** | Global already in local scope | NOT in global suggestions | AC-08 |
| **IT-07** | Global from required module | NOT in global suggestions (Phase 1 handles) | AC-08, AC-11 |
| **IT-08** | Dumb mode (indexing disabled) | No global suggestions; graceful | AC-12 |
| **IT-09** | Edit source file with global | Suggestions update to reflect changes | Cache behavior |
| **IT-10** | Circular dependency (A→B→A) | No crash; completion still works | Edge case |
| **IT-11** | @class-decorated global | Appears with class icon; ranked with boost | AC-09 |
| **IT-12** | Class symbol with `_` prefix; suppressed | NOT in completion | AC-07 |

## Acceptance Criteria Fulfillment

### AC-06: StubIndex Queried with Proper Scope
**Validation**:
- Code review: Verify `GlobalSymbolRankingService.collectGlobalFunctions()` uses `StubIndex.getElements()` with `GlobalSearchScope.projectScope(project)`
- Test: Completion in multiple files returns same global symbols (scope correctly bound)

### AC-07: Underscore Symbol Suppression
**Validation**:
- Code review: Verify `LuaProjectSettings.suppressUnderscorePrefixedGlobals` filtering in collection methods
- Test: IT-02 and IT-03 verify behavior with setting on/off
- Settings test: Verify UI checkbox persists across IDE restart

### AC-08: Deduplication
**Validation**:
- Code review: Verify `DeduplicationService.deduplicateByPsiIdentity()` removes duplicates
- Test: IT-06 and IT-07 verify locals/imports not duplicated
- Test: Multiple symbols with same name from different files handled correctly

### AC-09: Proximity Ranking
**Validation**:
- Code review: Verify `ProximityCalculator.calculateWeight()` implements ranking formula
- Test: IT-01, IT-04, IT-11 verify ranking order
- Test: Same module symbols rank higher than different module

### AC-10: Performance
**Validation**:
- Test: IT-05 verifies <200ms at 5000 symbols
- Performance tests (Phase 2.4) validate 100-10000 symbol scales
- Profile under load to identify bottlenecks

### AC-11: Phase 1 Integration
**Validation**:
- Test: IT-07 verifies imported symbols handled correctly
- Test: No duplicates between Phase 1 (imported) and Phase 2 (globals)
- Integration test: Both providers work together

### AC-12: Dumb Mode Graceful Degradation
**Validation**:
- Test: IT-08 verifies no crash, no suggestions
- Test: Re-enabling indexing restores suggestions

## Test Coverage Matrix

| Requirement ID | Test Case(s) | Integration Test(s) | AC Covered |
| :--- | :--- | :--- | :--- |
| COMP-03-02 (general) | TC-02-01...10 | IT-01...12 | AC-06...12 |
| Phase 2.1 (Infrastructure) | TC-02-01, TC-02-05 | IT-01, IT-05 | AC-06, AC-10 |
| Phase 2.2 (Ranking) | TC-02-03, TC-02-04, TC-02-09 | IT-01, IT-04, IT-11 | AC-09 |
| Phase 2.3 (Settings) | TC-02-02 | IT-02, IT-03, IT-12 | AC-07 |
| Phase 2.4 (Performance) | TC-02-05 | IT-05 | AC-10 |

## Technical Details

### Test Infrastructure
- **Base Class**: `IndexedDocumentTest` (existing test framework)
- **Fixture Setup**: Create multi-file project with known symbols
- **Indexing**: Force stub index rebuild; wait for smart mode
- **Completion**: Use `myFixture.completeBasic()` to trigger completion

### Multi-File Test Project Structure
```
test-project/
  module_a.lua       # Exports: function globalA(), class GlobalClass_A
  module_b.lua       # Exports: function globalB(), local _privateBFunc()
  module_c.lua       # Requires A,B; has local localVar; completion point
```

### Validation Checklist
- [ ] All 12 integration tests passing
- [ ] All acceptance criteria linked to tests
- [ ] No regressions in Phase 1 tests
- [ ] Performance baseline <200ms verified
- [ ] Code review approval
- [ ] Engineering contract compliance verified
- [ ] Dumb mode tested
- [ ] Cache invalidation tested

## Success Criteria

- ✅ All 12 integration tests passing
- ✅ 100% acceptance criteria coverage
- ✅ No Phase 1 regression
- ✅ Performance targets met (<200ms at 5000 symbols)
- ✅ Code review approval
- ✅ Engineering contract compliant
- ✅ Ready for production merge

## Next Steps

1. Create `GlobalSymbolIntegrationTest.kt` with 12 test cases
2. Set up multi-file test project
3. Implement each integration test
4. Validate all AC coverage
5. Code review by specialist subagent
6. Address feedback
7. Create final commit
8. Document in plan.md

---

**Phase 2.5 Status**: Ready to implement  
**Target Completion**: Full validation and integration testing
**Dependency**: All Phase 2.1-2.4 implementations complete ✅
