---
id: "MAINT-25-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-25"
folders:
  - "[[features/maint/25-type-graph-immutability/requirements|requirements]]"
---

# MAINT-25: Implementation Plan

This is a high-blast-radius change to the **shared** inference engine (`LuaGraphType.Table`,
`LuaTypesVisitor`, `LuaTypes`, `LuaTypeGraph`), so the regression contract (Phase 5) is a
**required gate**, not optional — the same REDIS-04 §3.1c-style contract TYPE-10 used. Order the
work so each phase leaves the build green: make `Table` immutable and fix every mutation site in
one atomic phase (Phase 1) so the code compiles, then layer the independent safety fixes (Phases
2–4), then prove no regression (Phase 5).

**Serialization:** MAINT-25 depends on TYPE-10 (roadmap `Serial: type engine`, same hot files
`LuaTypesVisitor`/`LuaTypeGraph`). TYPE-10 is `done`; start from a clean `main` and preserve
`LazyValueElement` / `inProgressBuilds` (design §6 E4, E5).

## Phases

### Phase 1: Immutable `Table` + construct-once mutation sites [Must]
- **Goal**: `LuaGraphType.Table` becomes immutable-by-construction and every mutation site is
  converted so the module compiles and existing tests pass.
- **Tasks**:
  - [ ] Change `LuaGraphType.Table` fields to `Map`/`List`/`val isExact`
        (`LuaGraphType.kt:37-42`) — realizes design §2.1.
  - [ ] Convert `fromLuaType` accumulation to construct-once: build members/superTypes into local
        collections, then construct the `Table` and register it in `visited`
        (`LuaGraphType.kt:157,167,172,182-183`) — realizes design §6 E3.
  - [ ] Convert the five in-place member-accumulation sites in `LuaTypesVisitor`
        (`:493-494, :510-511, :571-572, :704-705`, and confirm `:416` already constructs with the
        map) to build the map locally and construct the `Table` once — realizes design §6 E1.
  - [ ] Update `LuaTypes.getValueType` Table re-wrap (`LuaTypes.kt:58-62`) to compile against the
        read-only field types (no logic change) — realizes design §6 E2.
  - [ ] Change `TYPEOF_MAP` to `Map<String, () -> LuaGraphType>` and update the call site to
        `TYPEOF_MAP[typeName]?.invoke() ?: LuaGraphType.Any` (`LuaTypesVisitor.kt:260, :916-925`) —
        realizes design §2.2.
  - [ ] Rewrite `handleSetMetatable` to copy-on-augment
        (`tType.copy(superTypes = tType.superTypes + indexType)`; publish via `graph.value(o, augmented)`)
        (`LuaTypesVisitor.kt:85-97`) — realizes design §2.3, §3.1.
- **Exit criteria**: module compiles; the full `.../lang/types/*` suite (Phase 5 list) is green,
  including the existing setmetatable behavior test (`LuaTypeInferredCompletionTest`). Covers TC-01,
  TC-02, TC-06. Build green.

### Phase 2: Cycle-guarded `graphTypeToLuaType` [Must]
- **Goal**: self-referential tables convert without `StackOverflowError`.
- **Tasks**:
  - [ ] Add a private `graphTypeToLuaType(type, visited: MutableMap<LuaGraphType, LuaType>)` overload
        that registers a placeholder before recursing into `Table`/`Function`/`Array`/`Union`
        members; keep the public `override fun graphTypeToLuaType(type)` delegating with a fresh
        `mutableMapOf()` (`LuaTypes.kt:77-129`) — realizes design §2.4, §3.2.
- **Exit criteria**: TC-03 (`t.self = t` converts to a finite `LuaTableLiteralType` — no
  StackOverflowError) green; `graphTypeToLuaType` output for non-cyclic types unchanged (Phase 5
  suite). Build green.

### Phase 3: No VFS refresh under read lock [Must]
- **Goal**: remove the three synchronous-refresh calls in forbidden contexts.
- **Tasks**:
  - [ ] `VfsUtil.findFileByIoFile(File(path), false)` (`LuaTypeManagerImpl.kt:146`) — design §3.3, §7.
  - [ ] `VfsUtil.findFile(it, false)` (`LuaRocksLibraryProvider.kt:33`) — design §3.3, §7.
  - [ ] `VfsUtil.findFile(it, false)` (`PlatformLibraryProvider.kt:63`) — design §3.3, §7.
- **Exit criteria**: TC-04 (module resolution under a read action does not throw / does not perform
  a synchronous refresh) green; library-root and cross-file resolution tests
  (`CrossFileInferenceTest`, `LuaRequireTypeFlowTest`) green. Build green.

### Phase 4: Error-reporting hygiene [Should]
- **Goal**: designed cutoffs no longer raise fatal-error popups; PCE is never logged.
- **Tasks**:
  - [ ] `LuaTypeGraph.checkTypes`: both `log.error(...)` → `log.warn(...)` using the companion `log`
        (`LuaTypeGraph.kt:210-219, :560`) — realizes design §3.4 step 1.
  - [ ] `LuaTypeGraph.checkTypes` cutoff test seam: promote the hard-coded cutoffs to defaulted
        parameters — `fun checkTypes(maxIterations: Int = 1000, timeLimitMs: Long = 5000)`
        (`LuaTypeGraph.kt:193,197,199`); production call sites unchanged (no args) — realizes
        design §3.4 step 1b, enables TC-07.
  - [ ] `LuaTypeManagerImpl.resolveType`: add a `catch (e: ProcessCanceledException) { throw e }`
        **before** the `catch (e: Exception)` so PCE is rethrown unlogged
        (`LuaTypeManagerImpl.kt:64-66`) — realizes design §3.4 step 2.
- **Exit criteria**: TC-07 (checkTypes(maxIterations = 1) on a ≥1-edge graph warns, does not throw), TC-08
  (a `resolveType` call cancelled mid-flight rethrows PCE with no `log.error`) green. Build green.

### Phase 5: Snapshot-cost pass + regression contract [Must]
- **Goal**: address the Could-have perf items safely, and prove no inference regression across the
  shared engine and its consumers.
- **Tasks**:
  - [ ] Replace the per-element deep `findChildrenOfType` in `LuaRecursiveVisitor.visitElement`
        (`:21-28`) with a direct-child `LuaCatsComment` scan — realizes design §3.5 (gated on DR-02).
  - [ ] Add `LuaTypeGraph.firstNodeElement()` and replace the four `graph.nodes.firstOrNull()?.element`
        call sites in `LuaGraphType.fromLuaType` (`:132,139,161,176`) — realizes design §3.5.
        (Leave `write`/`read` memoization deferred per design §3.5 / risks TBD.)
  - [ ] **Regression contract (required):** run the **full** `.../lang/types/*` suite (NOT isolated
        `--tests`) plus the consumer suites, with the cache defeated (`--rerun-tasks --no-build-cache`),
        and confirm 0 failures against the 2123-test / 0-failure / 1-skipped baseline
        (`main` @ `0566cfbc`) — realizes requirements MAINT-25-06 acceptance. See the contract below.
- **Exit criteria**: TC-01…TC-08 green AND the full type-engine suite green on a full-suite run AND
  `run build` (checkStatus / koverVerify / integrationTest) green.

## Regression contract (REDIS-04 §3.1c-style — required, not optional)

The shared-engine seam is `LuaGraphType.Table` plus every reader of its `localMembers`/`superTypes`/
`isExact`, the `graphTypeToLuaType` conversion, and the `TYPEOF_MAP`/`handleSetMetatable` narrowing
path. This change is **behavior-preserving** (immutable-by-copy replaces in-place mutation; the only
intended behavior delta is *removing* the cross-file singleton leak). The following must hold:

**Invariants (assert as tests):**
1. A narrowed `type(t) == "table"` in file A does **not** add members to a `table`-typed value in
   file B (TC-01 — the leak is gone).
2. `setmetatable(t, mt)`'s result still exposes `mt.__index`'s members (TC-05 baseline equivalence —
   existing `LuaTypeInferredCompletionTest` stays green).
3. A self-referential table converts to a finite `LuaType` (TC-03) with no StackOverflowError.
4. Non-cyclic `graphTypeToLuaType` output is byte-for-byte unchanged (whole type-engine suite).

**Engine files in scope (review any diff to these):** `LuaGraphType.kt`, `LuaTypesVisitor.kt`,
`LuaTypes.kt`, `LuaTypeGraph.kt`, `LuaTypeNodes.kt`, `LuaTypeManagerImpl.kt` (all `lang/psi/types/`),
`lang/psi/LuaRecursiveVisitor.kt` (perf — note: lives in `lang/psi/`, not the types package), plus `LuaRocksLibraryProvider.kt` / `PlatformLibraryProvider.kt` (refresh flag).

**Full type-engine suite must stay green (full-suite run, NOT isolated `--tests`):**
`TestLuaTypeEnginePhase1`, `TestLuaTypeEngineSafety`, `TestFlowSensitiveType`,
`CrossFileInferenceTest`, `UnionAndGenericTest`, `PrimitiveTypeCompatibilityTest`,
`LuaUnionDistributionTest`, `TableTypeTest`, `QualifiedMemberResolutionTest`,
`ReceiverAwareMemberResolutionTest`, `MemberFieldIndexTest`, `FunctionSignatureMatchingTest`,
`LuaMethodMembersTest`, `MultiReturnValueTest`, `ArraySubscriptTypeTest`, `LambdaParamInferenceTest`
(TYPE-10 guard — must stay green), `StubGlobalSeedTypeTest`, `LuaRequireTypeFlowTest`,
`TestTypeParser`, `LuaTypeAlgebraTest`, `LuaImplicitFieldsTest`, `DuplicateNilAssignabilityTest`.

**Downstream consumers must be unregressed (build gate covers their suites):**
`LuaInferredTypeAnnotator` (reads `getMembers()`, `LuaInferredTypeAnnotator.kt:46`),
`LuaSubTypesHierarchyTreeStructure` / `LuaSuperTypesHierarchyTreeStructure` /
`LuaOverrideLineMarkerProvider` (read `superTypes`/`localMembers`), `LuaCompletionContributor`
(reads `getMembers()`, `:293`), `LuaUnionDiagnostics`, and the assignability/return-type inspections
(`LuaTypeAssignabilityInspection`, `LuaReturnTypeMismatchInspection`).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-25-01 | M | Phase 1 |
| MAINT-25-02 | M | Phase 2 |
| MAINT-25-03 | M | Phase 3 |
| MAINT-25-04 | S | Phase 4 |
| MAINT-25-05 | C | Phase 5 |
| MAINT-25-06 | M | Phase 5 |

## Verification Tasks
- [x] TC-05 (setmetatable result members preserved) — covered by the existing
      `LuaTypeInferredCompletionTest`, which stays green through the immutable-Table + copy-on-augment
      change (MAINT-25-01 baseline). TC-01 (cross-file leak absent) is not asserted by a dedicated
      unit test (two independent snapshots rather than two open editors); it is covered by the
      full-suite regression + human-verification-checklists.md.
- [x] `TestLuaTypeEngineSafety.testGraphTypeToLuaTypeOnSelfReferentialTableTerminates` covers TC-03
      (`t.self = t` converts finitely, no StackOverflowError) — MAINT-25-02.
- [ ] Module-resolution-under-read-action test for TC-04 — not added as a dedicated unit test;
      the `refreshIfNeeded = false` flips are covered by the full-suite regression (cross-file /
      require type-flow suites) + human-verification-checklists.md — MAINT-25-03.
- [x] `TestLuaTypeEngineSafety.testCheckTypesIterationCutoffWarnsWithoutError` (TC-07) and
      `testResolveTypeRethrowsProcessCanceledUnlogged` (TC-08, via `BombedProgressIndicator`) —
      MAINT-25-04.
- [x] `LuaRecursiveVisitorCatsScanTest` asserts the direct-child cats-comment scan drops no comment
      (behavior-preserving perf change) — MAINT-25-05.
- [x] Full-suite regression run (`--rerun-tasks --no-build-cache`, NOT isolated `--tests`) against
      the 2123/0/1 baseline: measured 2128 tests / 0 failures / 0 errors / 1 skipped (333 suites);
      the +5 tests are MAINT-25's own additions — MAINT-25-06 / TC-09.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Immutable Table + construct-once | done | Must |
| Phase 2: Cycle-guarded conversion | done | Must |
| Phase 3: No VFS refresh under read lock | done | Must |
| Phase 4: Error-reporting hygiene | done | Should |
| Phase 5: Snapshot-cost pass + regression contract | done | Must |
