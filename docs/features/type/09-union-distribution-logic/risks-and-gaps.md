# TYPE-09: Union Distribution Logic - Risks and Gaps

## 1. Technical Risks

| ID | Risk | Mitigation |
| :--- | :--- | :--- |
| **TYPE-DR-01** | **Combinatorial Explosion**: Deeply nested unions could lead to exponential growth in compatibility checks. | Implement a maximum recursion depth for union distribution and memoize intermediate results. |
| **TYPE-DR-02** | **Infinite Recursion**: Recursive types involving unions might cause the engine to hang. | Use the existing `visited` set in `checkCompatibility` and ensure it properly handles union heads. |
| **TYPE-DR-03** | **Inaccurate Error Ranges**: Reporting an error on a union member that is deeply nested might be difficult to map back to the PSI. | Ensure the `TypeMismatchError` carries enough context to identify the specific failing member's PSI element. |

## 2. Design Gaps

- **Overload Interaction Mitigation**: Following the TypeScript model, Lunar adopts **Strict Overload Matching**. A union type `A | B` is compatible with an overloaded function only if it matches a **single** overload signature. Automatic distribution of union members across different overloads is not supported; users must provide an explicit union-based overload if needed.
- **Implicit Unions Mitigation**: Addressed in Technical Design (§2.5). Implicit optionality is formally mapped to `T | nil` unions, ensuring they benefit from the distributive matching logic. Verification is included in Phase 4 of the implementation plan.
- **Subtype Reduction & Canonicalization**: The design mentions "flattening" nested unions but omits **subtype reduction**. In Lua, `string | "literal"` should simplify to `string`, and `T | any` to `any`. Without this, the solver performs redundant distributive checks, and the IDE may display bloated type strings to users. The current distributive logic may perform redundant checks for large unions. Advanced algorithms like BDD-based semantic subtyping or transitive reduction should be investigated (see task TYPE-22).
- **Empty Unions (Never Type)**: There is no specification for an empty union or a `never` type. A `never` type is the identity for union operations (`T | never = T`) and is essential for representing impossible states (e.g., the return type of `error()`). Functions like `error()` are currently handled by returning empty lists (expanding to `nil`). Full support for a `never` type requires Control Flow Graph (CFG) integration.
- **Intersection Symmetry**: Addressed in Technical Design (§2.4). The distributive logic for Unions is now documented alongside its dual for Intersections to ensure the architecture supports future expansion to TYPE-14.

## 3. Technical Inconsistencies Mitigation

- **Flattening Timing & Location**: Addressed in Technical Design (§2.2). Flattening moved to the `LuaGraphType.Union` factory to ensure consistency across all creation paths.
- **Recursive Type Mutation**: Addressed in Technical Design (§2.2.1). The factory is now specified to handle recursive resolution to avoid stale "stub" references.
- **Structural Propagation**: Addressed in Technical Design (§2.3). `checkCompatibility` is now specified to walk all structural types (Array, Generic, etc.), not just Tables and Functions.

## 4. Technical Risks

- **Combinatorial Explosion (TYPE-DR-01)**: While identified as a risk, the mitigation (`distributionDepth`) is not yet implemented in the codebase. A union of 10 tables, each with 10 fields that are themselves unions, can cause the `checkTypes` fixed-point iteration to exceed its time limit (5000ms) or max iterations (1000).
- **Invariance Leakage**: Table fields in `LuaTypeGraph` use bi-directional flow (`addEdge(A, B)` and `addEdge(B, A)`) to enforce invariance for mutable properties. Distributive logic must be carefully verified to ensure it doesn't accidentally allow covariant widening of a mutable union field, which would be unsound.

## 5. Addressed Possible Improvements

- **Heuristic Error Reporting**: Addressed in Technical Design (§3). The reporter identifies the "closest match" in OR-distribution failures.
- **Discriminant-Based Pruning**: Addressed in Technical Design (§2.6.1). Tagged table unions are pruned early.
- **Memoization Keying**: Addressed in Technical Design (§2.3.4). Cache keys now include the full resolution context.