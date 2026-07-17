---
id: "MAINT-25-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-25"
folders:
  - "[[features/maint/25-type-graph-immutability/requirements|requirements]]"
---

# Technical Design: MAINT-25 — Type-Graph Immutability & Safety

## 1. Architecture Overview

### Current State

The type engine's graph-internal representation (`net.internetisalie.lunar.lang.psi.types.LuaGraphType`,
`LuaGraphType.kt`) carries a `data class Table` whose collections are **mutable** and whose
`isExact` flag is a `var`:

```kotlin
// LuaGraphType.kt:37-42
data class Table(
    val className: kotlin.String? = null,
    val localMembers: MutableMap<kotlin.String, VariableNode> = mutableMapOf(),
    val superTypes: MutableList<LuaGraphType> = mutableListOf(),
    var isExact: kotlin.Boolean = false,
) : LuaGraphType()
```

Four correctness defects (re-verified 2026-07-17 on `main` @ `0566cfbc`) trace to this and to
adjacent VFS/logging hygiene in the same subsystem:

1. **Shared-mutable singleton leak (review #1, verified).** `TYPEOF_MAP` (`LuaTypesVisitor.kt:916-925`)
   maps `"table"` to **one** `LuaGraphType.Table()` instance shared for the IDE session. TYPE-08
   narrowing injects it as a variable's write: `tryParseTypeofGuard` (`LuaTypesVisitor.kt:247-262`)
   reads `TYPEOF_MAP[typeName]` (`:260`) into `TypeGuard.narrowedType`, and
   `injectNarrowedBinding` (`:226-236`) wraps it in `graph.value(anchor, narrowedType)` — a
   `ValueNode` whose `.write` **is** the shared singleton. `handleSetMetatable`
   (`LuaTypesVisitor.kt:85-97`) then recovers that instance via `(firstNode(...) as? ValueNode)?.write
   as? LuaGraphType.Table` (`:90`) and executes `tType.superTypes.add(indexType)` (`:94`),
   mutating the JVM-global singleton and leaking `__index` members across every file for the session.
2. **No cycle guard in `graphTypeToLuaType` (review #2, verified).** `LuaTypesSnapshot.graphTypeToLuaType`
   (`LuaTypes.kt:77-129`) recurses through `type.getMembers()` (`:85`) with no visited set; a
   self-referential table (`t.self = t`) → `StackOverflowError` in inlay hints / inspections. The
   sibling `LuaGraphType.fromLuaType` (`LuaGraphType.kt:108-208`) **already** threads a
   `visited: MutableMap<LuaType, LuaGraphType>` (`:111`) — this design mirrors that pattern.
3. **Synchronous VFS refresh under a read lock, three sites (review #3, #47, verified).**
   `VfsUtil.findFileByIoFile(File(path), true)` (`LuaTypeManagerImpl.kt:146`, reached from
   `doResolveModule` → `findVirtualFile`), `VfsUtil.findFile(it, true)`
   (`LuaRocksLibraryProvider.kt:33`), and `VfsUtil.findFile(it, true)`
   (`PlatformLibraryProvider.kt:63`) each perform a synchronous refresh in a forbidden context
   (annotator/completion read action; roots-computation read action). `LuaRequireReference`
   (`LuaRequireReference.kt:24-25`) already uses `refreshIfNeeded = false` — the correct model.
4. **Error-reporting hygiene (review #14, verified).** `LuaTypeGraph.checkTypes`
   (`LuaTypeGraph.kt:210-219`) calls `log.error(...)` on the **designed** iteration/time cutoffs
   (max 1000 iterations / 5 s), producing "IDE fatal error" popups in production and throwing in
   tests. `LuaTypeManagerImpl.resolveType` (`:61-69`) catches `Exception` and calls `logError`
   (`:277-280` → `log.error`) **before** rethrowing — this logs `ProcessCanceledException` (PCE),
   which the platform forbids.

Two performance items (review §2.5.5, the Could-have) also live here:
`LuaRecursiveVisitor.visitElement` (`LuaRecursiveVisitor.kt:22`) calls
`PsiTreeUtil.findChildrenOfType(element, LuaCatsComment::class.java)` **per visited element** → the
type-graph build is O(n²) in comment-scan cost; `VariableElement.write`/`read`
(`LuaTypeNodes.kt:84-85`) are computed properties re-traversing the up/down sets on every access;
and `LuaTypeGraph.nodes` (`:42`) copies the whole `_nodes` list per access (`graph.nodes.firstOrNull()`
is called 4× in `LuaGraphType.fromLuaType`, `:132,139,161,176`).

### Prior Art in This Repo

- **Cycle-guarded graph conversion — EXTEND the existing pattern.** `LuaGraphType.fromLuaType`
  (`LuaGraphType.kt:108-208`) already threads a `visited: MutableMap<LuaType, LuaGraphType>`
  default-argument and registers a placeholder before recursing (`:113,129,145,157,167,189`).
  §3.2 mirrors this exact shape for `graphTypeToLuaType`; it does **not** invent a new mechanism.
- **`refreshIfNeeded = false` reference resolution — EXTEND the correct site.**
  `LuaRequireReference.resolve` (`LuaRequireReference.kt:24-25`) is the canonical no-refresh
  lookup; §3.3 replicates its flag at the three offending sites. The divergence between it and
  `LuaTypeManagerImpl.findVirtualFile` is itself review §2.5.3's "module-file resolution ×2 —
  already diverged on the refresh flag".
- **TYPE-10 laziness & reentrancy guard — PRESERVE, do not disturb.** `LazyValueElement`
  (`LuaTypeNodes.kt:63-68`) computes `write` at read time; `LuaTypeGraph.lazyValue`
  (`LuaTypeGraph.kt:66-70`) creates it; `inProgressBuilds` / `inProgressSnapshot`
  (`LuaTypesVisitor.kt:908-913`) guard re-entrant `forFile` during a build. The immutability change
  must remain compatible with both (see §6 Edge Cases E4, E5).
- **No existing "immutability" component** — searched `src/main/kotlin/.../lang/psi/types/*`;
  `LuaGraphType.Table` is the sole mutable-identity graph type. This feature is a refactor of
  existing types, not a new component.

### Target State

`LuaGraphType.Table` becomes **immutable by construction**: `localMembers` and `superTypes` become
read-only (`Map`/`List`), `isExact` becomes a `val`. Every build-time accumulation site constructs
a fully-populated `Table` in one shot (the map/list are assembled locally, then passed to the
constructor). The one site that augments an already-published instance — `handleSetMetatable` —
switches to **copy-on-augment** (`table.copy(superTypes = table.superTypes + indexType)`).
`TYPEOF_MAP` becomes `Map<String, () -> LuaGraphType>` so `"table"` and `"function"` yield a
**fresh** instance per use. `graphTypeToLuaType` threads a `visited` map (cycle guard). The three
`refreshIfNeeded = true` calls flip to `false`. The two cutoff logs and the PCE log downgrade to
`log.warn` / PCE-rethrow-without-log. The three perf items are addressed opportunistically (§3.5).

Component sketch (all in `net.internetisalie.lunar.lang.psi.types` unless noted):

```
LuaGraphType.Table            (immutable: Map/List/val isExact; auto data-class copy())
  ├─ fromLuaType              (build map/list locally → construct Table once)
  ├─ handleSetMetatable       (copy-on-augment; re-publish via graph.value edge)
  ├─ TYPEOF_MAP: Map<String, () -> LuaGraphType>   (fresh instances)
  └─ member-accumulation sites (LuaTypesVisitor :416,493-4,510-1,571-2,704-5) → construct-once
LuaTypesSnapshot.graphTypeToLuaType(type, visited)  (cycle guard)
LuaTypeManagerImpl.findVirtualFile / LuaRocksLibraryProvider / PlatformLibraryProvider (no refresh)
LuaTypeGraph.checkTypes / LuaTypeManagerImpl.resolveType (log hygiene)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaGraphType.Table`
- **Responsibility**: the immutable graph representation of a Lua table/class type.
- **Threading**: constructed and read under the read action that builds the snapshot; never
  mutated post-construction.
- **Collaborators**: `VariableNode` (`LuaTypeNodes.kt:39`), `LuaGraphType.getMembers`
  (`LuaGraphType.kt:84-101`), `LuaTypeGraph.isCompatible` (`LuaTypeGraph.kt:407,475,490,515`).
- **Key API** (change: `MutableMap`→`Map`, `MutableList`→`List`, `var isExact`→`val isExact`):
  ```kotlin
  data class Table(
      val className: kotlin.String? = null,
      val localMembers: Map<kotlin.String, VariableNode> = emptyMap(),
      val superTypes: List<LuaGraphType> = emptyList(),
      val isExact: kotlin.Boolean = false,
  ) : LuaGraphType()
  // copy(...) is the data-class synthetic copy — used by handleSetMetatable (§3.1).
  ```
  Remains a `data class` (structural `equals`/`hashCode` unchanged) — the memo/`visited` keys in
  `LuaTypeGraph` (`compatMemo`, `CompatKey`, `LuaTypeGraph.kt:30-37`) and the cross-frame `visited`
  set (`:22-23`) become stable because the fields no longer mutate after construction (resolves
  review #58). See §9 for why a data class (not identity) is retained.

### 2.2 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor.TYPEOF_MAP`
- **Responsibility**: map a `type()` return string to a **fresh** graph type per lookup.
- **Threading**: read during snapshot build (read action).
- **Collaborators**: `tryParseTypeofGuard` (`LuaTypesVisitor.kt:260`).
- **Key API**:
  ```kotlin
  // companion object of LuaTypesVisitor
  private val TYPEOF_MAP: Map<String, () -> LuaGraphType> = mapOf(
      "string" to { LuaGraphType.String },
      "number" to { LuaGraphType.Number },
      "boolean" to { LuaGraphType.Boolean },
      "nil" to { LuaGraphType.Nil },
      "table" to { LuaGraphType.Table() },
      "function" to { LuaGraphType.Function(emptyList(), emptyList()) },
      "thread" to { LuaGraphType.Any },
      "userdata" to { LuaGraphType.Any },
  )
  ```
  Call site becomes `val narrowedType = TYPEOF_MAP[typeName]?.invoke() ?: LuaGraphType.Any`
  (`LuaTypesVisitor.kt:260`). `data object` heads (`String`/`Number`/`Boolean`/`Nil`) and `Any` are
  already immutable singletons, so a lambda that returns them is safe; only `Table` and `Function`
  actually need freshness, but wrapping all eight uniformly keeps the type `() -> LuaGraphType`.

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor.handleSetMetatable`
- **Responsibility**: model `setmetatable(t, mt)` by exposing `mt.__index`'s members on `t` — via
  **copy**, not in-place mutation.
- **Threading**: snapshot build (read action).
- **Collaborators**: `indexTableOf` (`LuaTypesVisitor.kt:100-106`), `LuaTypeGraph.value`/`addEdge`
  (`LuaTypeGraph.kt:52-56,addEdge`).
- **Key API** (§3.1 algorithm):
  ```kotlin
  private fun handleSetMetatable(o: LuaFuncCall, resultNode: VariableNode): Boolean
  ```
  Replaces `tType.superTypes.add(indexType)` (`:94`) with an immutable copy that is re-published as
  a fresh value node (§3.1).

### 2.4 `net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot.graphTypeToLuaType`
- **Responsibility**: convert a graph type back to a display `LuaType`, cycle-safely.
- **Threading**: read action (called from inlay/inspection surfaces).
- **Collaborators**: `LuaTypeManager.getInstance(...).resolveType` (`LuaTypes.kt:93`),
  `LuaClassType`/`LuaTableLiteralType`/`LuaFunctionType` (`LuaStructuredTypes.kt`,
  `LuaComplexTypes.kt`).
- **Key API** (§3.2):
  ```kotlin
  override fun graphTypeToLuaType(type: LuaGraphType): LuaType   // public: threads to private overload
  private fun graphTypeToLuaType(type: LuaGraphType, visited: MutableMap<LuaGraphType, LuaType>): LuaType
  ```
  The public no-arg override (interface method, `LuaTypes.kt:31`) delegates to the private overload
  with a fresh `mutableMapOf()`, preserving the existing signature for all callers.

### 2.5 `net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl.findVirtualFile` + library providers
- **Responsibility**: resolve a path to a `VirtualFile` without a synchronous refresh.
- **Threading**: read action (module resolution) / roots-computation read action (providers).
- **Collaborators**: `LocalFileSystem.getInstance()`, `VfsUtil`.
- **Key API**: three flag flips — `VfsUtil.findFileByIoFile(File(path), false)`
  (`LuaTypeManagerImpl.kt:146`); `VfsUtil.findFile(it, false)` (`LuaRocksLibraryProvider.kt:33`);
  `VfsUtil.findFile(it, false)` (`PlatformLibraryProvider.kt:63`). No signature change.

### 2.6 `net.internetisalie.lunar.lang.psi.types.LuaTypeGraph.checkTypes` + `LuaTypeManagerImpl.resolveType`
- **Responsibility**: report designed cutoffs and resolution errors without fatal-error popups and
  without ever logging PCE.
- **Threading**: snapshot build / type resolution (read action).
- **Key API**: two `log.error(...)`→`log.warn(...)` edits (`LuaTypeGraph.kt:212,217`); the
  `resolveType` catch (`LuaTypeManagerImpl.kt:64-66`) becomes PCE-aware (§3.4).

## 3. Algorithms

### 3.1 Copy-on-augment for `setmetatable`
- **Input → Output**: `(o: LuaFuncCall, resultNode: VariableNode)` → `Boolean` (true when handled).
- **Steps** (replaces `LuaTypesVisitor.kt:85-97`):
  1. `val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return false`.
  2. `val argExprs = nameAndArgs.args.exprList?.exprList ?: return false`; `if (argExprs.size < 2) return false`.
  3. `val tType = (firstNode(unwrapExpression(argExprs[0])) as? ValueNode)?.write as? LuaGraphType.Table ?: return false`.
  4. `val mtType = (firstNode(unwrapExpression(argExprs[1])) as? ValueNode)?.write as? LuaGraphType.Table ?: return false`.
  5. `val indexType = indexTableOf(mtType) ?: return false`.
  6. **Copy instead of mutate**: `val augmented = tType.copy(superTypes = tType.superTypes + indexType)`.
     (`tType.superTypes` is now a read-only `List`; `+` allocates a fresh list. The source `tType`
     — which may be the aliased narrowing value or any published instance — is left untouched.)
  7. `graph.addEdge(graph.value(o, augmented), resultNode)` — publishes the augmented copy as the
     call's result. (Previously `graph.value(o, tType)` after in-place mutation; behavior for the
     `resultNode` is identical because `augmented` carries the same members plus the index super.)
  8. `return true`.
- **Rules / edge handling**: the receiver `t`'s own binding is **not** retroactively augmented (it
  was not before either — the pre-change code only published the mutated instance on `resultNode`,
  but that instance happened to alias `t`'s write; the copy makes the previously-accidental
  augmentation of `t` explicit and scoped to the result). TC-05 (COMP-04-08, existing
  `LuaTypeInferredCompletionTest`) asserts `getmetatable(t)`-exposed members surface on the call
  **result**, which step 7 preserves. Empty/None path: any `?: return false` falls through to
  normal call handling unchanged.
- **Complexity / bounds**: one list allocation of size `superTypes.size + 1`; O(members) unchanged.

### 3.2 Cycle-guarded `graphTypeToLuaType`
- **Input → Output**: `(type: LuaGraphType, visited: MutableMap<LuaGraphType, LuaType>)` → `LuaType`.
- **Steps** (rewrites `LuaTypes.kt:77-129`, mirroring `LuaGraphType.fromLuaType`
  `LuaGraphType.kt:108-208`):
  1. `visited[type]?.let { return it }` — return the placeholder if this graph type is already
     being converted (cycle hit).
  2. For the **structural** cases that recurse (`Table`, `Function`, `Array`, `Union`), register a
     mutable placeholder in `visited` **before** recursing into members, then fill it:
     - `Table` (className != null → `LuaClassType`, else `LuaTableLiteralType`): create the target
       with an **empty** member map, `visited[type] = placeholder`, then populate the map by
       recursing `graphTypeToLuaType(node.write, visited)` for each `getMembers()` entry. Because
       `LuaClassType`/`LuaTableLiteralType` hold their members in a map supplied at construction,
       the placeholder is a `LuaClassType`/`LuaTableLiteralType` backed by a `LinkedHashMap` that is
       populated in place after registration (the map reference is shared, so the cycle-back
       reference sees the same growing map). Class-name enrichment via `LuaTypeManager.resolveType`
       (`LuaTypes.kt:92-99`) is unchanged and runs after member population.
     - `Function`: register a placeholder `LuaFunctionType(emptyList(), VOID)`, then recurse params
       and return, then… (see edge note) — Functions in practice do not self-cycle through params;
       the guard still prevents runaway recursion.
     - `Array`: `visited[type] = placeholder`; recurse `elementType`.
     - `Union`: `visited[type] = placeholder`; recurse each member.
  3. For **scalar** heads (`Any`/`Undefined`/`Nil`/`Boolean`/`Number`/`String`/`Generic`), return
     the mapped primitive/`LuaGenericType` directly — no registration needed (they cannot cycle).
- **Rules / edge handling**: the cycle key is the `LuaGraphType` instance (now stable — §2.1). A
  self-referential `Table` (`t.self = t`, whose `localMembers["self"].write === the same Table`)
  hits step 1 on the recursive descent and returns the in-construction `LuaClassType`/
  `LuaTableLiteralType` placeholder, terminating the recursion. This is the same convergence
  `fromLuaType` relies on.
- **Complexity / bounds**: O(distinct graph types) — each converted once; previously unbounded on a
  cyclic graph (StackOverflowError).

**Implementation note (Table placeholder mechanics):** because `LuaClassType`/`LuaTableLiteralType`
take their member map as a constructor argument, register the placeholder as an instance built over
a `val members = LinkedHashMap<String, LuaTypeMember>()`, insert `visited[type] = placeholder`, then
fill `members` by recursion. `LuaClassType(className, superTypes, members)` / `LuaTableLiteralType(members)`
retain the `members` reference (verify at implementation: `LuaStructuredTypes.kt` /
`LuaComplexTypes.kt` store, not defensively-copy, the map) — this is the de-risking task DR-01.

### 3.3 No-refresh VFS lookup
- **Input → Output**: `path: String`/`Path` → `VirtualFile?`.
- **Steps**: at each of the three sites, pass `false` as the `refreshIfNeeded` argument. No other
  logic changes. Rationale per site is in §7 (Integration Points).
- **Rules / edge handling**: a file that exists on disk but is not yet in the VFS snapshot resolves
  to `null` on this pass; the async VFS refresh picks it up and re-triggers resolution — identical
  to `LuaRequireReference`'s existing behavior.

### 3.4 Error-reporting hygiene
- **Input → Output**: cutoff condition / caught exception → log record (or none).
- **Steps**:
  1. `LuaTypeGraph.checkTypes` (`:210-219`): replace both `log.error(...)` with `log.warn(...)`;
     `break` unchanged. Use the existing companion `log` (`LuaTypeGraph.kt:560`) rather than
     re-fetching a `Logger` inline.
  1b. **Test seam for the cutoffs (required by TC-07):** the cutoffs are currently hard-coded
     locals (`maxIterations = 1000`, `timeLimitMs = 5000` — `LuaTypeGraph.kt:197,199`) in a
     parameterless `checkTypes()`, so no test can trip them without a production seam. Promote
     them to defaulted parameters:
     `fun checkTypes(maxIterations: Int = 1000, timeLimitMs: Long = 5000)` — production callers
     (`LuaTypesVisitor`'s snapshot build) pass nothing and are behaviorally unchanged; the TC-07
     test calls `checkTypes(maxIterations = 1)` on a snapshot graph with at least one edge, which
     deterministically trips the iteration cutoff without a pathological input. No `@VisibleForTesting`
     annotation is needed for defaulted params, but the KDoc must state the params exist for the
     cutoff tests.
  2. `LuaTypeManagerImpl.resolveType` catch (`:64-66`): change
     ```kotlin
     } catch (e: Exception) {
         logError("Error resolving type $name", e)
         throw e
     }
     ```
     to rethrow PCE **without** logging:
     ```kotlin
     } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
         throw e
     } catch (e: Exception) {
         logError("Error resolving type $name", e)
         throw e
     }
     ```
     (PCE extends `RuntimeException`/`CancellationException`; the platform requires it be neither
     logged nor swallowed. Catching it first and rethrowing satisfies both.)
- **Rules / edge handling**: `logError` (`:280`) itself is unchanged (still `log.error` for
  genuine errors). Only PCE is exempted.

### 3.5 Snapshot-cost pass (Could — MAINT-25-05)
- **O(n²) cats-comment scan** (`LuaRecursiveVisitor.kt:22`): the per-element
  `PsiTreeUtil.findChildrenOfType(element, LuaCatsComment::class.java)` re-scans the whole subtree
  at every node. Replace with a **direct-child** scan: iterate `element.children` and recurse only
  into `LuaCatsComment` direct children not yet in `visitedLuaCatsComments`, matching the intent of
  the existing `element is LuaCatsComment` block (`:16-19`) but without the deep `findChildrenOfType`.
  Concretely: replace lines `:21-28` with a loop over `element.children.filterIsInstance<LuaCatsComment>()`.
  Correctness guard: DR-02 confirms no `LuaCatsComment` is nested more than one level below a
  visited element in real PSI (comments attach directly under statements).
- **`VariableElement.write/read` re-traversal** (`LuaTypeNodes.kt:84-85`): both are `get()`
  properties that call `resolveWrite`/`resolveRead` on every access. Cache each behind a `by lazy`
  computed **once the graph is frozen** — but the graph is mutated (edges added) throughout the
  build and `checkTypes` reads `write`/`read` **during** the fixed-point loop
  (`LuaTypeGraph.kt:222`, `isCompatible`), so a naive `by lazy` would freeze a stale value.
  **Scope for MAINT-25-05:** do **not** memoize `write`/`read` (unsafe mid-build); instead reduce
  the copy cost of the `nodes` accessor (below), which is the pure win. Memoizing `write`/`read`
  requires a build-complete flag and is deferred to a follow-up (risks-and-gaps TBD).
- **`nodes` list copy** (`LuaTypeGraph.kt:42`): `val nodes: List<TypeNode> get() = _nodes.toList()`
  copies the whole list per access; the only production callers (`LuaGraphType.fromLuaType`
  `:132,139,161,176`) use it solely as `graph.nodes.firstOrNull()?.element`. Add
  `fun firstNodeElement(): PsiElement? = _nodes.firstOrNull()?.element` on `LuaTypeGraph` and
  replace the four `graph.nodes.firstOrNull()?.element ?: error(...)` call sites with
  `graph.firstNodeElement() ?: error(...)`. Leave the public `nodes` accessor (it is a documented
  "immutable snapshot for callers") for any test/external reader.

## 4. External Data & Parsing

None. This feature consumes no CLI output, file contents, or network responses — it refactors
in-memory graph types and adjusts VFS/logging calls. (The VFS lookups in §3.3 read the platform's
`VirtualFile` model, not unstructured text.)

## 5. Data Flow

### Example 1: `if type(t) == "table" then setmetatable(t, mt) end` (review #1 fix)
1. `tryParseTypeofGuard` (`LuaTypesVisitor.kt:247`) matches; reads `TYPEOF_MAP["table"]?.invoke()`
   → a **fresh** `LuaGraphType.Table()` (§2.2). → `TypeGuard.narrowedType`.
2. `injectNarrowedBinding` (`:226`) wraps it in `graph.value(anchor, freshTable)`.
3. `handleSetMetatable` (§3.1) recovers `tType` = that fresh table, computes
   `augmented = tType.copy(superTypes = tType.superTypes + indexType)`, publishes `augmented` on the
   result node. The fresh table (and any other `Table` instance) is never mutated. No cross-file leak.

### Example 2: `local t = {}; t.self = t; hover(t)` (review #2 fix)
1. Snapshot build produces a `LuaGraphType.Table` whose `localMembers["self"].write === the same Table`.
2. An inlay/hover surface calls `graphTypeToLuaType(table, mutableMapOf())` (§3.2).
3. Step 2 registers the `LuaTableLiteralType` placeholder, recurses into `self`, hits step 1
   (already in `visited`), returns the placeholder → recursion terminates. No StackOverflowError.

### Example 3: module resolution during highlighting (review #3 fix)
1. Annotator read action → `LuaTypeManagerImpl.doResolveModule` (`:107`) → `findVirtualFile(path)`
   (`:144`) → `VfsUtil.findFileByIoFile(File(path), false)` (§3.3). No synchronous refresh under the
   read lock; no platform assertion. A not-yet-refreshed file resolves later via VFS events.

## 6. Edge Cases

- **E1 — non-narrowing `Table` mutation sites.** `LuaTypesVisitor.kt:416,493-494,510-511,571-572,704-705`
  create a fresh `LuaGraphType.Table()` then immediately `localMembers[x] = node`. These populate a
  **locally-owned, not-yet-published** instance. Convert each to construct-once:
  `LuaGraphType.Table(localMembers = mapOf(propName to memberNode))` (or `mutableMapOf(...)` built
  locally then passed). Because the map is finished before the `Table` is passed to `graph.use(...)`,
  no post-construction mutation remains. (`:416` already passes the map as an argument —
  `LuaGraphType.Table(null, localMembers)` — so only the `x[...] =` sites change.)
- **E2 — `LuaTypes.kt:62` Table re-wrap.** `getValueType` merges write+read Table members into a new
  `LuaGraphType.Table(..., mergedMembers, write.superTypes, write.isExact)` (`:58-62`). `mergedMembers`
  is a local `mutableMapOf` finished before the constructor call; `write.superTypes` is now a
  read-only `List` passed by reference (safe — never mutated). No change needed beyond the type
  compiling against the new `List`/`Map` field types.
- **E3 — `fromLuaType` accumulation.** `LuaGraphType.kt:167,182` `result.localMembers.putAll(members)`
  and `:183` `result.superTypes.addAll(...)` mutate a **freshly-created** `result` before returning
  it. Convert to build the map/list locally and pass to the constructor (or keep a local mutable
  builder and construct once). The `visited[type] = result` registration (`:157,172`) must register
  the **final** immutable instance; because members are needed for the cycle-back reference, build
  the members first into a local map, then `val result = Table(name, members, supers, isExact); visited[type] = result`.
  **Ordering subtlety:** `fromLuaType` currently registers the placeholder *before* recursing so a
  cyclic `LuaType` sees it. With an immutable `Table`, the placeholder cannot be filled in place.
  Mitigation: `fromLuaType` operates on `LuaType` (Layer-1) cycles, which in practice are broken by
  `LuaTypeReference`/`LuaAliasType` indirection (`:149-150`), and the existing tests
  (`TestLuaTypeEngineSafety.testGenericIsolationAndMemoization`, `UnionAndGenericTest`) cover the
  cyclic-`LuaType` paths — DR-03 confirms no regression; if a genuine `LuaType`-level cycle through
  a bare `Table` exists, keep `fromLuaType`'s `Table` construction using a locally-mutable map that
  is registered by reference (the map, not the record, carries the cycle) exactly as §3.2 does.
- **E4 — `LazyValueElement` compatibility (TYPE-10).** `LazyValueElement.write`
  (`LuaTypeNodes.kt:67`) computes `arrayElementType(receiverNode.write)` at read time. Immutability
  of `Table` does not affect this: the receiver's `write` is read, projected, and returned — no
  mutation. The lazy computed-write pattern is preserved verbatim.
- **E5 — reentrancy guard (TYPE-10).** `inProgressSnapshot` (`LuaTypesVisitor.kt:912`) returns a
  partially-built snapshot. Nothing in this feature adds re-entrant `forFile` calls; the
  copy-on-augment and TYPEOF freshness run inside `visitFuncCall`/`tryParseTypeofGuard`, already
  within the guarded build. No interaction.
- **E6 — memo stability (review #58).** With `Table` fields immutable, `hashCode()`/`equals()` are
  stable for a given instance's lifetime, so `compatMemo` (`LuaTypeGraph.kt:37`) keyed on `CompatKey(value, use)`
  and the `visited: Set<Pair<LuaGraphType, LuaGraphType>>` (`:23`) no longer corrupt when a Table is
  augmented — because augmentation now produces a **distinct** instance (§3.1). Resolved structurally.

## 7. Integration Points

**No `plugin.xml` change.** This feature edits existing classes only; it registers no new
extension, service, listener, action, or index. `LuaRocksLibraryProvider` and
`PlatformLibraryProvider` remain registered as `com.intellij.additionalLibraryRootsProvider`
(unchanged); `LuaTypeManager` remains the existing project service. The refresh-flag flips are
justified per site:

- **`LuaTypeManagerImpl.findVirtualFile` (`:146`)** — reached from `doResolveModule`, itself called
  under the annotator/completion read action during snapshot build. Synchronous refresh under a read
  lock is forbidden (review #3). `refreshIfNeeded = false` matches `LuaRequireReference.kt:25`.
- **`LuaRocksLibraryProvider.installedRoots` (`:33`)** — called from `getAdditionalProjectLibraries`
  (`:16`) and `getRootsToWatch` (`:21-22`), both roots-computation contexts. This provider **has** a
  `getRootsToWatch` override (`:21`), so watched roots keep the VFS current without a synchronous
  refresh (review #47).
- **`PlatformLibraryProvider.getExternalLibraries` (`:63`)** — called from
  `getAdditionalProjectLibraries` (`:42`), a roots-computation context. This provider has **no**
  `getRootsToWatch` override; a not-yet-refreshed root resolves `null` and is picked up on the next
  roots recomputation after an async VFS refresh. This is acceptable (matches the platform's own
  library-root recomputation cadence) and is preferable to asserting under the read lock. Adding a
  `getRootsToWatch` override is **out of scope** and noted as future work in risks-and-gaps.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-25-01 | M | §2.1, §2.2, §2.3, §3.1, §6 (E1, E2, E3, E6) |
| MAINT-25-02 | M | §2.4, §3.2 |
| MAINT-25-03 | M | §2.5, §3.3, §7 |
| MAINT-25-04 | S | §2.6, §3.4 |
| MAINT-25-05 | C | §3.5 |

## 9. Alternatives Considered

- **Copy-on-mutate wrapper vs. immutable-by-construction.** Considered keeping `MutableMap`/
  `MutableList` fields and adding a `copyOnWrite` helper. Rejected: the mutation surface is small
  (only `handleSetMetatable` mutates a *published* instance; every other site populates a fresh
  local), so immutable fields + one `copy()` are simpler and eliminate the whole class (review §2.5.1).
- **Identity-based memo keys vs. keeping the data class.** Considered switching `compatMemo`/`visited`
  to identity (`IdentityHashMap`) so mutation would not matter. Rejected: the graph relies on
  structural equality elsewhere (`addError` dedup by `element`+`message` is separate, but `Union`
  canonicalization and `Table` equality feed compatibility semantics); making types immutable keeps
  structural `equals`/`hashCode` correct and stable, which is the intended fix (review #58).
- **Removing `TYPEOF_MAP` narrowing entirely.** Rejected — TYPE-08 narrowing is a shipped feature;
  the defect is shared identity, not the feature. Fresh instances retain the behavior.

## 10. Open Questions

None.
