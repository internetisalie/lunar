---
id: "TYPE-09-P0-03-RESULTS"
title: "Recursive-Union Termination — Spike Results"
type: "design"
parent_id: "TYPE-09-P0"
status: "done"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-0-de-risking/requirements|requirements]]"
---

# TYPE-09-P0-03 — Recursive-Union Termination (Spike Results)

**Deliverable for:** `TYPE-09-P0-03` · **Test:** `LuaRecursiveUnionSpikeTest`
(`src/test/kotlin/net/internetisalie/lunar/lang/types/LuaRecursiveUnionSpikeTest.kt`)

## Question

Does flattening / conversion / compatibility terminate on a self-referential union, conceptually
`type T = T | number`, with no `StackOverflowError`?

## Method

Two complementary probes:

1. **PSI fixture probe** (mirrors `UnionAndGenericTest.testSelfReferencingClass`): a
   `---@class T` with a self-referencing field `---@field self T | number`, plus a
   `---@type T | number` variable. Resolved end-to-end through `LuaTypesSnapshot.forFile(...)`,
   then `getErrors()` is read to force full graph evaluation
   (`LuaGraphType.fromLuaType` + `checkTypes`).
2. **Direct-graph probe**: built a class table `T` whose member `self` is the union
   `T | number` (self-referential by construction), then exercised the three termination-sensitive
   paths directly:
   - `Union.getMembers()` (`LuaGraphType.kt:87`) — merges union members;
   - `VariableElement.resolveWrite`'s internal `flatten` (`LuaTypeNodes.kt:79`) — over the cyclic
     member;
   - `LuaTypeGraph.addEdge(value, use)` — compatibility of the recursive union against itself.

## Result

- **No `StackOverflowError`** on either probe. Both terminate.
- `Union.getMembers()` over the recursive union terminated and exposed the field:
  `members = [self]`.
- `resolveWrite` flatten terminated and resolved the member to **`T | number`** (the `number`
  alternative is retained, not swallowed).
- `addEdge(value(T|number), use(T|number))` completed without recursion blow-up.

Spike stdout:

```
SPIKE-03 recursive-union members=[self] resolvedWrite=T | number
```

## Why it terminates (mechanism)

Three independent cycle guards cover the recursive paths:

1. **`fromLuaType`** carries `visited: MutableMap<LuaType, LuaGraphType>` and records a placeholder
   before recursing into members (`LuaGraphType.kt:107, 182-187`), so a self-reference returns the
   in-progress node instead of recursing forever.
2. **`resolveWrite` / `resolveRead`** guard on `visited: MutableSet<VariableNode>`
   (`LuaTypeNodes.kt:74-76, 103-105`), returning `Undefined`/`Any` on re-entry.
3. **`isCompatible` / `checkCompatibility`** add each `(value, use)` type-pair to `visited` and
   return early on repeat (`LuaTypeGraph.kt:338, 258`).

## Result vs success criterion

| Criterion (design.md §3) | Outcome |
| :--- | :--- |
| No `StackOverflowError` | **PASS** |
| Union resolves sensibly (members include `number`) | **PASS** — resolved to `T | number`; member `self` present |

**TC-TYPE-09-P0-03: PASS.**

## Go / No-Go

**GO** — recursive-union flattening/conversion/compatibility terminates safely on the existing
`visited` guards. **P1 (flatten/canonicalize) must preserve all three guards** when it routes
construction through `LuaTypeAlgebra`; a canonicalization pass that rebuilds union sets must keep
the `fromLuaType` placeholder-before-recurse pattern (`LuaGraphType.kt:182-187`) or it will
reintroduce the SOE risk this spike just cleared.

## Status

`TYPE-09-P0-03` **DONE** — no SOE, union resolves to `{ T-ref, number }`, deliverable produced.
