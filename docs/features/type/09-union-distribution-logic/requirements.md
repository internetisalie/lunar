---
id: "TYPE-09"
title: "09: Union Distribution Logic"
type: "feature"
parent_id: "TYPE"
status: "in_progress"
priority: "high"
folders:
  - "[[features/type/requirements|requirements]]"
---
# TYPE-09: Union Distribution Logic - Requirements Specification

> **Decomposition note (2026-06-13).** TYPE-09 is an **epic** decomposed into five
> sub-feature stories, P0–P4 — those are the planning/tracking unit, and the parent status is
> **derived** from them. Their true statuses reflect that the union-distribution core is already
> implemented in `LuaTypeGraph`: **P1 (Infrastructure & Flattening)** and **P2 (Compatibility
> Logic)** are `in_progress` (Union node, `flatten`, and AND/OR distribution exist; canonicalization,
> safety limits and memoization remain); **P3 (Error Reporting)** is `in_progress` (basic union
> errors exist; member-specific diagnostics remain); **P0 (De-risking)** and **P4 (Verification &
> Performance)** are `planned`. The parent is therefore `in_progress`. See each phase's
> `requirements.md`/`design.md` for the per-story bar, and the "Current implementation status"
> note in [design.md](./design.md).

**Task ID:** TYPE-09  
**Epic:** [TYPE: Type System](../../../status.md)  
**Status:** in_progress (derived from P0–P4 — core implemented, hardening/diagnostics/perf remain)  
**Related Documents:** [Technical Design](./design.md), [Implementation Plan](./implementation-plan.md), [Risks and Gaps](./risks-and-gaps.md)

## 1. Introduction
This Requirements Specification details the functional requirements for implementing distributive checking of union types.

### The "What"
Distributive checking of union types is a critical enhancement to the Lunar type inference engine. It allows the engine to correctly evaluate type compatibility for union types by distributing the check across all members of the union. 

Currently, the engine performs a shallow comparison, which fails for structural compatibility. For example, if a variable is typed as `string | number`, assigning a `number` literal should be valid. Distributive checking ensures that `Value(number)` is compared against both `string` and `number`, succeeding if any match is found.

### The "Why"
Lua is a dynamically typed language where unions are extremely common (e.g., functions returning a result or `nil`, variables that can be multiple types). Without distributive checking:
- Users get false-positive type errors for valid Lua code.
- Type inference for standard library functions (which often return unions) is inaccurate.
- Advanced LuaCATS features like `@overload` and `@generic` cannot be fully supported.

Implementing this feature improves the accuracy and reliability of the IDE's static analysis, making Lunar a more powerful tool for professional Lua development.
## 2. Scope

### In Scope
- **Distributive Left-to-Right Matching**: Evaluating a single type against a union (OR logic).
- **Distributive Right-to-Left Matching**: Evaluating a union against a single type (AND logic).
- **Nested Union Handling**: Recursive flattening or traversal of nested union structures.
- **Error Reporting**: Detailed diagnostics identifying which specific union member(s) caused a mismatch.

### Out of Scope
- **Flow-Sensitive Narrowing**: (Handled by TYPE-12).
- **Generic Constraints**: (Handled by TYPE-13).
- **Overload Resolution**: (Handled by TYPE-11).

## 3. Functional Requirements

| ID | Priority | Description |
| :--- | :--- | :--- |
| **TYPE-09-01** | [Must] | **OR-Distribution (Value to Union)**: `Value(T) <= Use(Union(A \| B))` must succeed if `T <= A` OR `T <= B`. |
| **TYPE-09-02** | [Must] | **AND-Distribution (Union to Value)**: `Value(Union(A \| B)) <= Use(T)` must succeed if `A <= T` AND `B <= T`. |
| **TYPE-09-03** | [Must] | **Transitive Distribution**: Distribution must work through multiple levels of the type graph (e.g., via variable assignments). |
| **TYPE-09-04** | [Should] | **Nested Union Flattening**: `Union(Union(A \| B) \| C)` should be treated identically to `Union(A \| B \| C)`. |
| **TYPE-09-05** | [Should] | **Member-Specific Error Messages**: When a union fails a check, the error message should specify which member(s) failed and why. |

## 4. Test Cases (TC)

### TC-09-01: OR-Distribution (Success)
- **Input**: 
  ```lua
  ---@type string|number
  local x = 42
  ```
- **Action**: Check compatibility of `42` with `string|number`.
- **Expected Output**: **Success**. `number <= (string|number)` succeeds via OR-distribution.

### TC-09-02: OR-Distribution (Failure)
- **Input**: 
  ```lua
  ---@type string|number
  local x = true
  ```
- **Action**: Check compatibility of `true` with `string|number`.
- **Expected Output**: **Failure**. `boolean` is not assignable to `string` or `number`.

### TC-09-03: AND-Distribution (Failure)
- **Input**: 
  ```lua
  ---@type number
  ---@param p string|number
  local x = p
  ```
- **Action**: Check compatibility of `p` (union) with `number` (value).
- **Expected Output**: **Failure**. `string` member of union is not assignable to `number`.

### TC-09-04: Union-to-Union Distribution
- **Input**: 
  ```lua
  ---@type string|number|boolean
  ---@param p string|number
  local x = p
  ```
- **Action**: Check compatibility of union `p` with union `x`.
- **Expected Output**: **Success**. All members of `p` (`string`, `number`) are present in `x`.

### TC-09-05: Nested Union Resolution
- **Input**: 
  ```lua
  ---@type string|(number|boolean)
  local x = 1.5
  ```
- **Action**: Check compatibility with nested union.
- **Expected Output**: **Success**. `number` matches nested member.

## 5. Performance Goals
- **O(1) Overhead**: No noticeable latency for unions with ≤ 5 members.
- **Graceful Degradation**: For large unions (>20 members), the engine should maintain stability even if accuracy is slightly deferred.
