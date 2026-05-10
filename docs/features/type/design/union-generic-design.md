# Design: Union & Generic Type Resolution (TYPE-04, TYPE-05)

**Status**: Draft (De-risking Task 20)  
**Related Requirements**: [`TYPE-04`](../04-union-types.md), [`TYPE-05`](../05-generics-support.md)

---

## 1. Overview

Task 20 extends the cubic biunification type engine to support **Union Types** (explicit disjunctions) and **Generic Types** (let-polymorphism). This design leverages the existing bipartite graph structure where multiple inputs to a node already represent an implicit union, and multiple outputs represent an implicit intersection.

---

## 2. Union Types (TYPE-04)

### 2.1 Graph Representation

While the graph handles implicit unions via multiple edges, explicit unions from LuaCATS annotations (`---@type A | B`) require a dedicated head to maintain structural information.

```kotlin
// LuaGraphType.kt
data class Union(val types: Set<LuaGraphType>) : LuaGraphType()
```

### 2.2 Compatibility Rules

The `checkCompatibility` method in `LuaTypeGraph.kt` must be updated with the following distributive rules:

| Flow Case | Condition for Compatibility |
| :--- | :--- |
| **Value(Union(A \| B)) ≤ Use(T)** | `Value(A) ≤ Use(T)` **AND** `Value(B) ≤ Use(T)` |
| **Value(T) ≤ Use(Union(A \| B))** | `Value(T) ≤ Use(A)` **OR** `Value(T) ≤ Use(B)` |

**Example**:
- `local x: string | number = "hi"`: `Value(string) ≤ Use(string | number)` is OK because `string ≤ string`.
- `local y: string = x`: `Value(string | number) ≤ Use(string)` is an ERROR because `number` is not assignable to `string`.

---

## 3. Generic Types (TYPE-05)

### 3.1 Template Generalization

Generic functions are modeled as **Type Templates**. When a function with `@generic T` is visited:

1.  A fresh `GenericNode` is created for each type variable.
2.  The function signature is stored with these symbolic nodes.
3.  The function body is checked using these symbolic nodes as fixed (but unknown) types.

### 3.2 Call-site Instantiation

When a generic function is *called*, the engine performs **instantiation**:

1.  **Fresh Variable Nodes**: Create a fresh `VariableNode` for each generic parameter (e.g., `T'`) for *this specific call site*.
2.  **Constraint Propagation**:
    - Arguments passed to parameters of type `T` flow into `T'`.
    - `T'` flows into the return site if the return type involves `T`.

```kotlin
// LuaTypeGraph.kt
fun instantiateGeneric(template: LuaGraphType.Function): LuaGraphType.Function {
    val mapping = template.generics.associateWith { variable(it.element) }
    // ... clone parameters and returns using the mapping ...
}
```

### 3.3 Example: Identity Function

```lua
---@generic T
---@param val T
---@return T
local function identity(val) return val end

local s = identity("hello")
```

1.  **Definition**: `identity` type is `fun(val: T) -> T`.
2.  **Call Site 1**: 
    - Fresh node `T1` created.
    - Argument `"hello"` (String) flows into `T1`.
    - `T1` flows into the result of the call.
    - Therefore, `s` receives `String`.

---

## 4. Proposed Implementation Steps

### 4.1 `LuaGraphType.kt`
- Add `data class Union(val types: Set<LuaGraphType>)`.
- Add `data class Generic(val name: String, val id: Int)`.
- Update `fromLuaType` to handle `LuaUnionType` and `LuaGenericType`.

### 4.2 `LuaTypeGraph.kt`
- Update `checkCompatibility` to handle `Union` types recursively.
- Implement `instantiateGeneric(function, callSite)` to create fresh nodes.

### 4.3 `LuaTypesVisitor.kt`
- Update `visitFuncCall` to detect if the callee is generic.
- If generic, call `graph.instantiateGeneric()` before flowing arguments.
- Update `visitFunctionDef` to capture `@generic` tags from the `TypeParser`.

---

## 5. Cyclic Type Resolution (Critical Fix)

Converting complex structural types (like self-referencing `@class` definitions) into graph nodes poses a `StackOverflowError` risk.

### 5.1 Problem
`LuaGraphType.fromLuaType` recursively resolves members. If `Node.next` refers back to `Node`, the recursion never terminates.

### 5.2 fromLuaType Mitigation
Update `fromLuaType` to accept a `context: MutableMap<LuaType, LuaGraphType>` to track already-visited structural types.

```kotlin
fun fromLuaType(
    type: LuaType, 
    graph: LuaTypeGraph, 
    visited: MutableMap<LuaType, LuaGraphType> = mutableMapOf()
): LuaGraphType {
    if (type in visited) return visited[type]!!
    
    // For structural types, pre-register the node before visiting children
    return when (type) {
        is LuaClassType -> {
            val node = Table(type.name)
            visited[type] = node
            // ... then populate members recursively ...
            node
        }
        // ...
    }
}
```

### 5.3 Compatibility Recursion
To prevent infinite loops when checking recursive type structures (e.g., deeply nested Unions), `checkCompatibility` must track visited type pairs during structural traversal.

```kotlin
private fun checkCompatibility(
    value: LuaGraphType, 
    use: LuaGraphType, 
    valueElement: PsiElement, 
    useElement: PsiElement,
    visited: MutableSet<Pair<LuaGraphType, LuaGraphType>> = mutableSetOf()
) {
    if (!visited.add(value to use)) return
    // ... logic ...
}
```

*Note*: The graph's fixed-point loop (`checkedPairs`) handles node-to-node recursion, but structural recursion (head-to-head) requires this additional guard.

---

## 6. Risk Assessment & Mitigation

| Risk | Mitigation |
| :--- | :--- |
| **StackOverflow**: Self-referencing classes/tables. | **[NEW]** Pre-register structural nodes in a `visited` map during `fromLuaType` recursion. |
| **Exponential Growth**: Nested generics/unions could explode the graph. | Implement a recursion depth limit in `checkCompatibility` and `instantiateGeneric`. |
| **Recursive Generics**: `@generic T: T[]` or similar. | Biunification handles cycles via transitive closure, but explicit checks are needed to prevent infinite instantiation. |
| **Performance**: O(n³) propagation might slow down with many call-site instantiations. | Only instantiate generic parameters that are actually used in the signature. Cache instantiations for identical call-site types. |
