# De-risking Plan: Nested Generics & LuaScope Architecture

**Status**: Planned  
**Related Tasks**: TYPE-08 (Nested Generics), Task 105 (O(n³) Performance), Task 103 (LuaScope Architecture)

## 1. Overview
Before fully implementing nested generic instantiation (TYPE-08), we need to address significant architectural risks related to infinite loops in the type graph and memory/shadowing issues in `LuaScope`. This document outlines the de-risking spikes required to ensure stability.

## 2. Risk 1: Generic Instantiation vs. Fixed-Point Propagation (Task 105 & TYPE-08)
### The Threat
In `LuaTypeGraph.kt`, the `checkTypes()` function uses a `do/while(changed)` loop that continues until no new edges or errors are produced. Implementing nested generic instantiation will create *new* `VariableNode` instances dynamically. A complex or recursive generic type (e.g., `type Tree<T> = { value: T, children: Tree<T>[] }`) could trigger continuous node and edge creation, leading to an infinite loop and an IDE freeze.

### De-risking Spike Plan
1. **Safety Thresholds**:
   - Implement hard metric collection in `checkTypes()` (track iteration counts and execution time).
   - Establish a strict fallback: if iterations exceed a safe limit (e.g., 1000), halt propagation, log a telemetry warning, and degrade the type to `Any`.
2. **Instantiation Memoization**:
   - Design a caching mechanism for `instantiateGeneric`.
   - Ensure that if the engine encounters the same generic substitution (e.g., `Tree<string>`) at the same call site/scope, it reuses previously instantiated graph nodes instead of allocating new ones.
3. **Stress Testing**:
   - Write a stress test with a highly recursive LuaCATS generic pattern.
   - Verify that graph construction terminates cleanly without `StackOverflowError` or timeouts.

## 3. Risk 2: LuaScope Architecture (Task 103)
### The Threat
The current `LuaScope` is implemented as a parent-chain tree rather than the journal-based rollback design defined in the Phase 1 specification. This introduces risks of memory leaks (function scopes not being garbage collected if `returnNodes` hold references) and currently fails to detect variable shadowing.

### De-risking Spike Plan
Before building more complex scope-aware inference that nested generics might rely on, we must make a final architectural decision:
1. **Evaluate Current vs. Spec**:
   - Compare the current parent-chain approach against the journal-based rollback design.
2. **Decision Path**:
   - *Option A*: Refactor `LuaScope` to match the spec's journal design.
   - *Option B*: Update the spec to accept the parent-chain approach, but patch in the missing shadowing logic and ensure memory safety.
3. **Implementation**:
   - Execute the chosen option and verify with scope and shadowing tests.
