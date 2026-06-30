---
id: "TYPE-09-P1-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-09-P1"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-1-infrastructure-flattening/requirements|requirements]]"
---

# Technical Design: TYPE-09 P1 — Infrastructure & Flattening

## 1. Architecture Overview

### Current State
`LuaGraphType.Union(types: Set<LuaGraphType>)` exists; `flatten` (`LuaTypeNodes.kt:79-93`)
recursively unwraps nested `Union`s; `fromLuaType` (`LuaGraphType.kt:181`) builds a `Union` from
a `LuaUnionType` with a `visited` cycle guard. There is **no** simplification, dedupe/sort, or
single-member collapse, and `Union` construction sites do not funnel through a common factory.

### Target State
A `LuaTypeAlgebra` performs flatten → simplify → canonicalize; a `Union.create(...)` factory
delegates to it so every union is canonical. This is the parent design's §2.2 algebra mapped to
the real `LuaGraphType.Union` (not the never-built `UnionTypeNode`).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypeAlgebra` (new)
```kotlin
object LuaTypeAlgebra {
    /** Flatten nested unions, simplify, dedupe+sort, collapse single → canonical type. */
    fun canonicalize(members: Collection<LuaGraphType>): LuaGraphType   // §3.1
}
```

### 2.2 `LuaGraphType.Union.create` (add companion factory)
```kotlin
companion object { fun create(members: Collection<LuaGraphType>): LuaGraphType =
    LuaTypeAlgebra.canonicalize(members) }
```
Replace direct `Union(set)` constructions in `fromLuaType` and `LuaTypesVisitor` with
`Union.create(...)`.

## 3. Algorithms

### 3.1 `canonicalize(members)` — TYPE-09-P1-02/03/04
- **Steps**:
  1. **Flatten**: expand any member that is itself a `Union` (reuse the existing `flatten`),
     producing a flat list.
  2. **Simplify**: if any member is `Any` → return `Any` (TYPE-09-P1-03). Drop `Undefined`
     members unless it is the only one.
  3. **Dedupe**: collect into a `LinkedHashSet` using structural equality (data-class equality on
     `LuaGraphType`), giving `T|T → T`.
  4. **Sort**: order members by a stable key (`displayName()`) so equal unions compare equal.
  5. **Collapse**: if the set has exactly one member → return it; if empty → `Undefined`; else
     `Union(sortedSet)`.
- **Edge handling**: `nil` is a normal distinct member (not swallowed) — parent §2.3.2; a
  self-referential member is kept by reference (the `visited` guard in `fromLuaType` already
  prevents infinite expansion before `canonicalize` is called).

## 4. External Data & Parsing
None.

## 5. Data Flow
`fromLuaType(LuaUnionType[number, Union[string, number]])` → `Union.create` → flatten →
`{number, string, number}` → dedupe → `{number, string}` → sort → `Union{number, string}`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `Union{X}` (single) | collapses to `X`. |
| `Union{}` (empty) | `Undefined`. |
| `Union{string, nil}` (optional) | both kept (nil not dropped). |
| `Union{T, Any}` | `Any`. |

## 7. Integration Points
- No new EP — adds `LuaTypeAlgebra` + a `Union.create` factory in
  `net.internetisalie.lunar.lang.psi.types`. Reuses the existing `flatten`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|-------------|----------|----------------|
| TYPE-09-P1-01 Union node | M | existing `LuaGraphType.Union` |
| TYPE-09-P1-02 Flattening | M | §3.1 step 1 (existing `flatten`) |
| TYPE-09-P1-03 Simplification | S | §3.1 step 2 |
| TYPE-09-P1-04 Canonical form | S | §3.1 steps 3–5 |

## 9. Alternatives Considered
- **Factory `Union.create` vs scattered canonicalization**: a single factory guarantees every
  union is canonical, so P2's comparisons and P3's messages see a stable shape.

## 10. Open Questions

_None — feature has cleared the planning bar._
