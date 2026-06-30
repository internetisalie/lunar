---
id: TYPE-09-PLAN
title: "Implementation Plan"
type: plan
parent_id: TYPE-09
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/requirements|requirements]]"
---

# TYPE-09: Union Distribution Logic - Implementation Plan

## Phase 0: Prep & De-Risking [Must]
- [ ] **0.1**: **Refactor**: Extract existing algebraic logic (flattening, generic substitution, type predicates) from `LuaTypeNodes` and `LuaTypeGraph` into `LuaTypeAlgebra`.
- [ ] **0.2**: **TYPE-09-DR-01**: Prototype stress test for combinatorial explosion; verify performance of `distributionDepth` and `Max Breadth` limits.
- [ ] **0.3**: **TYPE-09-DR-02**: Verify `visited` set logic with recursive union types (e.g., `type T = T | string`) to prevent infinite recursion.
- [ ] **0.4**: **TYPE-09-DR-03**: Validate diagnostic element attribution for nested union member failures to ensure accurate IDE underlining.

## Phase 1: Infrastructure & Flattening [Must]
- [ ] **1.1**: Implement `LuaTypeAlgebra` utility and `LuaGraphType.Union.create()` factory.
- [ ] **1.2**: Ensure all union creation paths (Visitor & Bridge) use the new factory.
- [ ] **1.3**: Add unit tests for union flattening and deduplication.

## Phase 2: Compatibility Logic [Must]
- [ ] **2.1**: Implement distributive matching in `checkCompatibility`.
- [ ] **2.2**: Extend structural propagation to `Array` and `Generic` types.
- [ ] **2.3**: Implement `distributionDepth` and `Max Union Breadth` limits.
- [ ] **2.4**: Verify logic with existing Phase 1 & 2 tests.

## Phase 3: Error Reporting & Diagnostics [Should]
- [ ] **3.1**: Enhance `TypeMismatchError` with member-specific context.
- [ ] **3.2**: Implement "closest match" heuristic for OR-distribution failures.
- [ ] **3.3**: Ensure correct error ranges in the IDE.

## Phase 4: Verification & Performance [Should]
- [ ] **4.1**: Implement discriminant-based pruning for table unions.
- [ ] **4.2**: Implement context-aware memoization for the solver.
- [ ] **4.3**: Verify implicit unions (optional parameters and fields) distribute correctly.
- [ ] **4.4**: Profile with large unions (>50 members) and implement memoization if needed.
