---
id: TYPE-09-DESIGN
title: "Technical Design"
type: design
parent_id: TYPE-09
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/requirements|requirements]]"
---

# TYPE-09: Union Distribution Logic - Technical Design

> **Current implementation status (reconciled 2026-06-13).** The union model and the core
> distributive checks described below are **already implemented** in the real engine, which
> uses **`LuaGraphType.Union(types: Set<LuaGraphType>)`** (`LuaGraphType.kt`) — **not** the
> `UnionTypeNode`/`TypeHead.UNION` named in early drafts (that model does not exist). Concretely
> already built:
> - **AND/OR distribution** — `LuaTypeGraph.isCompatible` (`LuaTypeGraph.kt:341-342`):
>   `value is Union → types.all { … }`, `use is Union → types.any { … }`; plus the
>   error-emitting `checkCompatibility` union branches (`:264`, `:270`, `:279-295`).
> - **Nested flattening** — `LuaTypeNodes.kt:79-93` (`flatten`).
> - **Cycle handling** — the `visited` set threaded through `isCompatible`.
>
> **Remaining (not yet built)** and the real subject of the phase docs: canonicalization /
> simplification (`T | any → any`, `T | T → T`, sort + collapse-to-single), the breadth (100) /
> depth (10) safety limits, context-keyed memoization, member-specific "closest-match" error
> messages, discriminant-based pruning, and the benchmark/test suite. The sections below are the
> target spec; map each to the real `LuaGraphType.Union` model, not `UnionTypeNode`.

## 1. Data Models

### Union representation (existing)
`net.internetisalie.lunar.lang.psi.types.LuaGraphType.Union(val types: Set<LuaGraphType>)`
— already the union node. `getMembers()` (`LuaGraphType.kt:87`) merges union members. New
algebra (flatten/simplify/canonicalize) is added as a `LuaTypeAlgebra` helper that the
`Union` construction path delegates to (Phase 1).

## 2. Services & Integration Points

### 2.1 LuaTypeGraph.kt (existing core)
The constraint solver. Distribution lives in `isCompatible(value, use, visited)` (`:329`) and
the error-reporting `checkCompatibility(...)` (`:246`). Phase 2 hardens these with the breadth/
depth limits and memoization; the OR/AND logic itself is already present (`:341-342`).

### 2.2 LuaTypesVisitor.kt
Graph construction. Union members arrive via `LuaGraphType.fromLuaType` (`LuaGraphType.kt:181`,
`is LuaUnionType`). Phase 1 routes construction through `LuaTypeAlgebra.canonicalize`.

### 2.3 LuaTypeNodes.kt
Hosts the existing `flatten` (`:79`); Phase 1 extends it into the `LuaTypeAlgebra` utility.

## 3. Implementation Specifics

### 2.1 Compatibility Algorithm
The `checkCompatibility(value, use)` function in `LuaTypeGraph.kt` will be updated to handle `UnionTypeNode` on either side:

**Case A: `use` is `UnionTypeNode` (OR-distribution)**
```kotlin
if (use is UnionTypeNode) {
    return use.members.any { member -> checkCompatibility(value, member) }
}
```

**Case B: `value` is `UnionTypeNode` (AND-distribution)**
```kotlin
if (value is UnionTypeNode) {
    return value.members.all { member -> checkCompatibility(member, use) }
}
```

### 2.2 Nested Union Flattening & Canonicalization
To prevent `LuaGraphType` from becoming unwieldy, algebraic logic (flattening, simplification, and canonicalization) is housed in a specialized `LuaTypeAlgebra` utility. The `LuaGraphType.Union` class utilizes a companion factory method `create()` that delegates to this algebra before instantiating the node.

#### 2.2.1 LuaTypeAlgebra
This internal utility performs the following:
- **Flattening**: Recursively unwraps nested `Union` instances.
- **Simplification**: Applies basic subtype reduction (e.g., `T | any` -> `any`, `T | T` -> `T`).
- **Canonicalization**: Returns a single type if the union collapses to one member, or a `Union` instance with a sorted/deduplicated set of members.

#### 2.2.2 Recursive Union Handling
The `LuaTypeAlgebra` handles recursive type construction in `fromLuaType` by using a resolution phase, ensuring that recursive members eventually point to the fully-canonicalized union set.

### 2.3 Algorithm Edge Cases and Limits
The `checkCompatibility` function recursively propagates structural constraints for all composite types, including `Table`, `Function`, `Array`, and `Generic` types.

#### 2.3.1 Recursion and Combinatorial Limits
The **load-bearing** bound against "Combinatorial Explosion" (TYPE-DR-01) is the existing
`visited: Set<Pair<value, use>>` guard (§2.3.3): because a `Union` is a `Set` and each type-pair
is visited at most once, distribution over distinct members is already bounded to the number of
distinct pairs. P2 **must preserve** this guard. The breadth/depth limits below are cheap,
deterministic **defense-in-depth** backstops for adversarial or machine-generated inputs (and for
future features that mint fresh per-node instances, e.g. generic instantiation or discriminant
expansion); on normal code they never trip. This is confirmed empirically by the P0 spike
(`phase-0-de-risking/results/union-perf.md`): even a width-8 × depth-14 pathological union stayed
sub-millisecond, ~400× under budget, with vs. without the limits effectively equal.

- **Max Union Breadth**: Unions with more than 100 members will fallback to a shallow head-matching check to avoid performance degradation.
- **Max Distribution Depth**: The algorithm will track a `distributionDepth`. If the depth exceeds 10, the check will **assume compatibility (return `true`)** — consistent with how the `visited` cycle-guard returns `true` — and emit a diagnostic/log rather than a user-facing error. Returning `false` here is a **soundness hazard** (TYPE-DR-04): it would produce false-positive type errors on legitimately deep but valid types. The 50 ms budget is enforced as a CI regression gate (the P0 spike), not a runtime timer.

#### 2.3.2 Interaction with Special Types
- **AnyType**: If a `UnionTypeNode` contains `Any`, OR-distribution (Case A) will immediately succeed. AND-distribution (Case B) will treat `Any` as compatible with any `use` type.
- **NilType**: Unions containing `nil` (e.g., `string | nil`) are common for optional values. The algorithm must ensure that `nil` is treated as a distinct member and not swallowed during flattening unless duplicate reduction is performed.

#### 2.3.3 Cyclic Types and Visited Set
The `checkCompatibility` function uses a `visited: Set<Pair<LuaTypeNode, LuaTypeNode>>` to handle recursive types. For unions:
- Before entering the `any`/`all` loops, the `(value, use)` pair is added to `visited`.
- If a recursive call encounters a pair already in `visited`, it returns `true` (assuming compatibility until proven otherwise) to allow the graph traversal to continue.

#### 2.3.4 Short-circuiting and Memoization
- **Short-circuiting**: OR-distribution (any) and AND-distribution (all) must use short-circuiting logic to minimize calls to `checkCompatibility`.
- **Memoization**: Results of `checkCompatibility` are cached at the `LuaTypeGraph` level. **Important**: Memoization keys must include the entire resolution context (e.g., current generic substitutions) to prevent unsound result reuse across different call sites.

#### 2.3.5 Non-returning Functions (error, etc.)
Functions that never return (e.g., `error()`) are treated as returning an empty list of types. During assignment, the graph's `flowList` logic will automatically expand this empty list to `nil` for any receiving variables. A proper `never` (bottom) type and reachability analysis are deferred until the implementation of Control Flow Graphs (CFGs).

### 2.4 Intersection Symmetry (Future Proofing)
Although Intersections (TYPE-14) are not part of the current implementation, the distributive logic is designed to be the dual of Union logic to ensure future consistency.

| Rule | Left (Value) | Relation | Right (Use) | Logic |
| :--- | :--- | :---: | :--- | :--- |
| **Union Distribution** | `T` | `<:` | `A \| B` | `T <: A` **OR** `T <: B` |
| **Union Distribution** | `A \| B` | `<:` | `T` | `A <: T` **AND** `B <: T` |
| **Intersection Distribution** | `T` | `<:` | `A & B` | `T <: A` **AND** `T <: B` |
| **Intersection Distribution** | `A & B` | `<:` | `T` | `A <: T` **OR** `B <: T` |

By maintaining this symmetry, the solver avoids "leakage" where a union on one side behaves differently than an intersection on the other in similar structural contexts.

### 2.5 Implicit Unions Mitigation
Implicit unions are automatically converted to explicit `UnionTypeNode` structures during graph construction to ensure distributive logic applies uniformly.

- **Optional Parameters**: A parameter marked as optional (`param?`) or with a default value is treated as `T | nil`.
- **Optional Table Fields**: A table field not explicitly required is treated as `T | nil` on its read-side.
- **Multi-Flow Variables**: Variable nodes naturally aggregate multiple incoming flows into a union via the `upSet` propagation logic.

### 2.6 Performance Optimizations

#### 2.6.1 Discriminant-Based Pruning
For unions of tables that act as tagged unions (e.g., `{type: "A", ...} | {type: "B", ...}`), the solver will identify "discriminant" fields (literal values) and prune branches that cannot match before performing expensive structural checks.

## 3. Error Reporting
Modified `TypeMismatchError` to include details about union failures.
- If OR-distribution fails: "Value of type 'T' is not assignable to any of 'A | B'."
- **Heuristic**: The error reporter will identify the "closest match" member (e.g., the table with the most overlapping fields) and surface its specific failures to help the user identify typos.
- If AND-distribution fails: "Union member 'A' is not assignable to type 'T'."

## 4. Performance
- **O(1) Overhead**: Memoization and short-circuiting ensure no noticeable latency for unions with ≤ 5 members.
- **Graceful Degradation**: For large unions (>20 members), discriminant-based pruning is prioritized to maintain stability.
