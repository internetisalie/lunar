# Design: Type Engine Phase 4 & 5 (Tables, Inheritance, Multi-return)

**Status**: Draft (De-risking Phase 5)  
**Related Requirements**: [`TYPE-02`](../02-class-table-definitions.md), [`TYPE-06`](../06-return-type-checking.md)

---

## 1. Overview

This document de-risks the implementation of **Tables**, **Inheritance**, and **Multi-return values** in the cubic biunification type engine. These features transition the engine from basic primitives to a complete Lua-capable type system.

---

## 2. Table Types (TYPE-02)

### 2.1 Graph Representation

Tables are modeled with a dedicated `Table` head that tracks both nominal (class name) and structural (property slots) information.

```kotlin
// LuaGraphType.kt
data class Table(
    val className: String? = null,
    val members: MutableMap<String, VariableNode> = mutableMapOf()
) : LuaGraphType()
```

### 2.2 Compatibility & Structural Matching

The `checkCompatibility` algorithm (§5 of `integration-plan.md`) is refined for tables:

1.  **Nominal Check**: If both have `className`, check inheritance via `LuaTypeManager`.
2.  **Structural Flow**: 
    - When `Value(Table V) ≤ Use(Table U)`:
    - For each property `p` in `U.members`:
    - If `V.members[p]` exists, `flow(V.members[p], U.members[p])`.
    - If missing, report "Missing field 'p'".

**Risk Mitigation (Mutability Invariance)**:
Lua tables are mutable. To prevent unsoundness (e.g., adding a number to a table expected to only have strings), property flows should ideally be **invariant** (bi-directional flow).
*Decision*: Phase 5 will use **covariant** flow for property *reads* and **contravariant** flow for property *writes* if possible, or simplify to bi-directional flow for known mutable slots.

---

## 3. Inheritance (TYPE-02-03)

Inheritance is handled by Layer 1 (`LuaTypeManager`) providing the parent chain.

In `checkCompatibility(value: Table, use: Table)`:
1. If `value.className == use.className`, return OK.
2. Resolve `value.className` and walk up the `superTypes` list.
3. If `use.className` is found in the chain, return OK.
4. Otherwise, fallback to structural (duck-typing) matching if `use.className` is null.

---

## 4. Multi-Return & List Types (Phase 4)

Lua functions can return multiple values. This is modeled using a `List` type head.

### 4.1 `List` Head
```kotlin
data class List(val members: kotlin.collections.List<VariableNode>) : LuaGraphType()
```

### 4.2 Flattening Logic

The engine must handle "flattening" at assignment and call sites:
- `local x, y = f()`: `f()` produces `List(A, B, C)`. `x` receives `A`, `y` receives `B`.
- `local x = f()`: `x` receives only `A`.
- `g(f())`: If `f()` is the last argument, its results are appended to `g`'s argument list.

**Implementation**:
Update `LuaTypeGraph.flowList` to detect if the `from` node has a `List` head and "unpack" it into the `to` variables.

---

## 5. Implementation Roadmap for Phase 5

1.  **Expand `LuaGraphType`**: Add `Table`, `List`, `Union`, and `Generic` heads.
2.  **Update `LuaTypesVisitor`**:
    - `visitTableConstructor`: Create `Table` node with property slots.
    - `visitIndexExpr`: Resolve selector to a property slot in the receiver's `Table` node.
    - `visitReturnStatement`: Collect all expressions into a `List` node.
3.  **Update `LuaTypeGraph`**:
    - Implement `checkTableCompatibility` (structural + nominal).
    - Implement `checkListCompatibility` (element-wise flow).
4.  **Bridge Layer 1**:
    - Update `LuaTypeGraphBridge.typeToValueNode` to convert `LuaClassType` to `Table` head.

---

## 6. Risk Assessment

| Risk | Mitigation |
| :--- | :--- |
| **Recursive Tables**: `local t = {}; t.self = t`. | Transitive closure naturally handles cycles, but `displayName` and structural checks must avoid infinite recursion. |
| **Dynamic Keys**: `t[expr] = val`. | Fallback to a special `[KEY]` slot in the `Table` head that represents all dynamic keys, or treat the table as `Map<any, any>`. |
| **Inheritance Cycles**: `@class A : B`, `@class B : A`. | Layer 1 already validates inheritance cycles; Layer 2 can assume a DAG. |
