# TYPE-09: Union Distribution Logic - Technical Design

## 1. Data Models

### UnionTypeNode
Extends `LuaTypeNode` in `net.internetisalie.lunar.lang.psi.types`.
- `members: List<LuaTypeNode>`: A collection of types making up the union.
- `head: TypeHead.UNION`: New entry in `TypeHead` enum.

## 2. Services & Integration Points

### 2.1 LuaTypeGraph.kt
The core constraint solver. 
- **Method**: `checkCompatibility(value: LuaTypeNode, use: LuaTypeNode): Boolean`
- **Logic**: Updated to implement distributive matching as described in the Requirements Specification.

### 2.2 LuaTypesVisitor.kt
Responsible for graph construction.
- **Integration**: `visitUnionType()` will now call `flattenUnion()` before creating graph nodes.

### 2.3 LuaTypeNodes.kt
Type representation layer.
- **Integration**: Add `UnionTypeNode` implementation.

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
To prevent "Combinatorial Explosion" (TYPE-DR-01), the distributive check will implement the following limits:
- **Max Union Breadth**: Unions with more than 100 members will fallback to a shallow head-matching check to avoid performance degradation.
- **Max Distribution Depth**: The algorithm will track a `distributionDepth`. If the depth exceeds 10, the check will return `false` (incompatible) to prevent stack overflow or infinite loops.

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
