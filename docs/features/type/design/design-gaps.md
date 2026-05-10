# Design Gaps — Type Inference Engine

**Date**: 2026-05-07
**Scope**: Identified during review of all 10 design documents and the current implementation

---

## 1. Implementation–Design Divergences

### 1.1 Union compatibility is oversimplified

**File**: `LuaTypeGraph.kt:239–258`
**Design docs**: `union-generic-design.md` §2.2, `phase-5-implementation-plan.md` §3.2

The design specifies full distributive checking:
- `Value(T) ≤ Use(Union(A | B))` → test each member with `checkCompatibility()`; success if any member is compatible
- `Value(Union(A | B)) ≤ Use(T)` → all members must be compatible with T

The implementation only checks for exact-match or same-head (`Table` vs `Table`), falling back to an error for all non-trivial cases. Structurally compatible union members (e.g. `{x: number} ≤ {x: number} | string`) will produce false positive errors.

**Severity**: High — causes incorrect error reporting for union types in all but trivial cases.

---

### 1.2 Table inheritance chain not traversed

**File**: `LuaTypeGraph.kt:312–314`
**Design docs**: `phase-4-5-design.md` §3, `type-system-integration-plan.md` §5

`checkTableCompatibility` checks `value.className == use.className` but never walks the `superTypes` chain via `LuaTypeManager`. The design requires resolving the parent chain and confirming compatibility if `use.className` is reachable. Without this, `@class B : A` will not satisfy a site expecting type `A`.

**Severity**: High — breaks nominal subtyping for all class hierarchies.

---

### 1.3 `LuaReturnTypeMismatchInspection` designed but not registered

**Design docs**: `type-system-integration-plan.md` §8.3, §11

The integration plan defines and file-maps a `LuaReturnTypeMismatchInspection`. Only `LuaTypeAssignabilityInspection` is registered in `plugin.xml`. The return-type mismatch inspection is absent from the IDE surface.

**Status**: FIXED in `a5241fa` (Registered in `plugin.xml`).

---

### 1.4 Mutability invariance not enforced for table fields

**File**: `LuaTypeGraph.kt:319`
**Design docs**: `phase-4-5-design.md` §2.2

`checkTableCompatibility` uses simple forward `addEdge()`. The design calls out that mutable table fields require invariant (bi-directional) flow to prevent unsound covariant widening. This is acknowledged in the design but not implemented.

**Status**: FIXED in `a5241fa` (Bi-directional edges added).

---

## 2. Undesigned Territory

### 2.1 `@overload` resolution

**Design docs**: `type-system-integration-plan.md` §4.8

Deferred past TYPE-05 (generics), which is now complete. No design exists for selecting amongst overloaded function signatures. The current fallback treats `@overload` as `AnyType`.

**Severity**: Low — `@overload` is a C-level requirement and relatively uncommon in LuaCATS usage.

---

### 2.2 Flow-sensitive type narrowing

**Design docs**: `type-inference-engine.md` §9, §11

`if type(x) == "string"` narrowing is listed as future work. No design document exists for branch-scoped type refinement, no `LuaScope.child()` contexts carry refined-type semantics, and the visitor has no branch-awareness in constraint propagation.

**Severity**: Medium — prevents accurate inference in idiomatic Lua patterns where variables are guarded by type checks.

---

### 2.3 Generic constraints and generic classes

**Design docs**: `phase-5-implementation-plan.md` §1.2

Both `@generic T : string` (bounded generics) and generic classes are deferred to "Phase 6+". Phase 6 design (`phase-6-design.md`) only covers cross-file inference and inlay hints. No design exists for either feature.

**Severity**: Medium — `@generic T : string` is an S-level requirement (TYPE-05). Generic classes are likely needed for key standard library patterns.

---

### 2.4 Trait system (`ORDERED`, `STRINGABLE`)

**Design docs**: `type-inference-engine.md` §4.1, §9, §11

Both relational operators and concatenation currently constrain operands to `NUMBER` and `STRING` respectively. The design defines `ORDERED` and `STRINGABLE` trait use-type heads but never specifies the trait propagation rules, the PSI elements that produce them, or how they interact with the `checkCompatibility` algorithm.

**Severity**: Low — correctness improvement rather than a current bug; restrictive constraints are sound, just overly strict.

---

### 2.5 Chained function calls (`f(1)(2)`)

**Design docs**: `phase-3-implementation.md` §6.2

Deferred from Phase 3 to Phase 4+, but Phase 4 specification does not cover chained calls. No design document exists.

**Severity**: Low — chained calls are a C-level requirement and can be handled gracefully (error on second call).

---

## 3. Design Ambiguities & Inconsistencies

### 3.1 `LuaTypeManagerImpl` cache invalidation status

**Design docs**: `type-system-integration-plan.md` §9, `phase-1-api-contracts.md` §10

Both docs prescribe wrapping the `ConcurrentHashMap` in `CachedValue` keyed on `PsiModificationTracker`. No completion report or file map update documents whether this was applied. The `ConcurrentHashMap` without cache invalidation would serve stale cross-file types indefinitely after edits.

**Severity**: High if not fixed — stale type data across file boundaries.

---

### 3.2 `@field` on non-local / assignment-based fields

**File**: `LuaLocalVarStubElementType.kt`, `LuaTypeManagerImpl.kt`
**Design docs**: `type-system-integration-plan.md` §12, question #2

Stubs only capture `@field` tags on `LuaLocalVarDecl`. Fields defined via `ClassName.field = val` (TYPE-02-05) or simply tagged above an assignment (e.g. `---@field x number; M.x = 1`) are not stub-indexed. `materializeClass` in `LuaTypeManagerImpl` only looks at the stubs of the class declaration itself.

**Severity**: Medium — prevents proper cross-file resolution of classes whose fields are defined through assignment rather than `@field` annotations on the class declaration.

### 3.3 NodeList last-element flattening status contradictory

**File**: `LuaTypesVisitor.kt:60-70`, `LuaTypesVisitor.kt:185-188`
**Design docs**: `phase-3-implementation.md` §6.2, `phase-4-specification.md` §3.1, `phase-6-design.md` §5 task 5

Three documents disagree on flattening status. The current implementation in `visitFuncCall` hardcodes 8 return nodes: `val callResultNodes = List(8) { graph.variable(o) }`. This arbitrary limit does not correctly handle Lua's vararg expansion or cases where more than 8 values are returned.

**Severity**: Medium — uncertainty and incomplete implementation of core Lua semantics (last-element expansion).

---

### 3.4 Dynamic key handling (`t[expr]`)

**Design docs**: `phase-4-5-design.md` §6

The design proposes two alternatives (special `[KEY]` slot vs `Map<any, any>`) but never resolves which was chosen. Phase 4 is marked implemented without documenting the selected approach.

**Severity**: Medium — non-literal index expressions are common in Lua; unknown what semantics apply.

---

## 4. Architectural Risks

### 4.1 `checkTypes()` fixed-point loop has no iteration bound

**File**: `LuaTypeGraph.kt:212`
**Design docs**: `union-generic-design.md` §6

The `do/while(changed)` loop re-checks until no new edges appear. While `checkedPairs` prevents re-checking the same `(ValueNode, UseNode)` pairs, if a structural mismatch continuously adds new edges between *newly created* variables (e.g. from generic instantiation), this could still theoretically loop.

**Status**: FIXED in `7b5f700` (Iteration limits added).

---

### 4.2 Thread-safety of `LuaTypeGraph` unresolved

**Design docs**: `type-system-integration-plan.md` §12, question #4

An open question asks whether `CachedValuesManager` guarantees no concurrent writes once the graph is cached. The assumption is single-threaded PSI visitor then read-only use, but this is not validated or documented with the IntelliJ caching contract.

**Severity**: Medium — potential for non-deterministic failures under concurrent access if the caching guarantee doesn't hold.

---

### 4.3 Incomplete Generic Instantiation

**File**: `LuaTypeGraph.kt:125-174`
**Design docs**: `phase-5-implementation-plan.md` §1.2

`instantiateGeneric` only handles top-level `Generic` heads in function parameters and returns. Nested generics (e.g. `Table<T>`, `Union<T | string>`, or generics inside function signatures) are ignored during substitution.

**Status**: WON'T FIX. The official LuaCATS specification considers generics to be "WIP" and does not yet define syntax for nested generic parameters. It has been officially ruled out of scope.

---

### 4.4 Missing Multi-Return Representation in Layer-1

**File**: `LuaType.kt`, `LuaTypes.kt:77-81`
**Design docs**: `phase-4-specification.md`

`LuaFunctionType` and the `graphTypeToLuaType` conversion only support a single return type (`LuaType`). Even though the graph handles multiple return nodes (up to 8), this information is lost when exposing types to the IDE (hovers, inlay hints).

**Severity**: Medium — degrades UX as users cannot see full function signatures.

---

### 4.5 `resolveModule` Performance and Caching

**File**: `LuaTypeManagerImpl.kt:63-100`
**Design docs**: `phase-6-design.md`

`resolveModule` iterates through source path patterns and performs file lookups on every call. Unlike `resolveType`, it lacks a `CachedValue` to store resolved module types. Frequent `require` calls during analysis of a large file could lead to significant performance degradation.

**Status**: FIXED in `a5241fa` (Added `moduleCache`).

