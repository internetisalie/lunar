---
id: "TYPE-09-P2-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TYPE-09-P2"
status: "in_progress"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-2-compatibility-logic/requirements|requirements]]"
---

# Technical Design: TYPE-09 P2 — Compatibility Logic

## 1. Architecture Overview

### Current State
`LuaTypeGraph.isCompatible(value, use, visited)` (`:329`) already distributes:
`value is LuaGraphType.Union -> value.types.all { isCompatible(it, use, visited) }` (`:341`),
`use is LuaGraphType.Union -> use.types.any { isCompatible(value, it, visited) }` (`:342`);
`checkCompatibility` (`:246`) drives error emission and recurses union members (`:270`,
`:279-295`). The `visited` set guards cycles. There is **no** breadth/depth cap and **no**
memoization, so pathological unions can be slow or deep.

### Target State
Add the parent design's §2.3.1 limits and §2.3.4 memoization to the existing distribution
(which stays as-is for the OR/AND logic).

## 2. Core Components

### 2.1 `LuaTypeGraph.isCompatible` (modify) — limits
- Add a `depth: Int = 0` parameter; on union branches increment it. Guard:
  ```kotlin
  if (depth > MAX_DISTRIBUTION_DEPTH) return false            // 10
  if (type is Union && type.types.size > MAX_UNION_BREADTH)    // 100
      return shallowHeadMatch(value, use)                      // compare type heads only
  ```
  Constants: `MAX_DISTRIBUTION_DEPTH = 10`, `MAX_UNION_BREADTH = 100`.

### 2.2 `LuaTypeGraph` memo (add)
- A `private val compatMemo = HashMap<CompatKey, Boolean>()` where
  `data class CompatKey(val value: LuaGraphType, val use: LuaGraphType, val subst: Map<String,
  LuaGraphType>)` — the substitution map captures the current generic context (parent §2.3.4).
  `isCompatible` checks/populates the memo; cleared per `checkTypes()` run (per-snapshot).

## 3. Algorithms

### 3.1 Bounded, memoized compatibility (TYPE-09-P2-04/05)
- **Steps** (wrapping the existing logic):
  1. `compatMemo[CompatKey(value, use, subst)]?.let { return it }`.
  2. If `depth > 10` → result `false`.
  3. If `value`/`use` is a `Union` with `>100` members → `shallowHeadMatch` (compare only the
     type constructors, ignoring members).
  4. Otherwise the **existing** OR/AND/structural logic (`:341-352`), passing `depth + 1` into
     union-member recursion.
  5. Store and return the result in the memo.
- **`shallowHeadMatch(value, use)`**: true iff some member of the (large) union shares the
  other's head kind (`Table`/`Function`/primitive), a cheap over-approximation to avoid blow-up
  — documented as a soundness/perf trade-off (parent §2.3.1).
- **Short-circuit**: `any`/`all` already short-circuit (Kotlin) — unchanged.

## 4. External Data & Parsing
None.

## 5. Data Flow
A `T <= A|B` check hits the memo (miss) → not over-breadth → OR branch `any { … }` →
short-circuits on the first compatible member → result cached under the current substitution.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Union `Any` member | OR succeeds immediately; AND treats `Any` as compatible (parent §2.3.2). |
| Cyclic `(value, use)` | existing `visited` returns `true` (assume-until-proven) (parent §2.3.3). |
| Differing generic context | distinct memo keys → no unsound reuse (validated by P0-02). |
| >100-member union | head-match fallback (logged once). |

## 7. Integration Points
- No new EP — edits `LuaTypeGraph` (`isCompatible`/`checkCompatibility`). The OR/AND distribution
  itself is already shipped.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|-------------|----------|----------------|
| TYPE-09-P2-01 OR | M | existing `:342` |
| TYPE-09-P2-02 AND | M | existing `:341` |
| TYPE-09-P2-03 Transitive | M | existing graph flow |
| TYPE-09-P2-04 Limits | S | §2.1, §3.1 |
| TYPE-09-P2-05 Memoization | S | §2.2, §3.1 |

## 9. Alternatives Considered
- **Per-snapshot memo vs global**: clearing the memo each `checkTypes()` avoids cross-file
  staleness; the snapshot is already rebuilt per document edit.

## 10. Open Questions

_None — feature has cleared the planning bar._
