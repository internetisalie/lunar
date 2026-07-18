---
id: "MAINT-30-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-30"
folders:
  - "[[features/maint/30-resolution-caching/requirements|requirements]]"
---

# MAINT-30: Risks & Gaps

## Critical Risks

### Risk 1.1: Cross-file resolution regression (blast radius)
- **Impact**: Go-to-Declaration, Find-Usages, cross-file completion, and type resolution all sit on
  the `LuaNameReference` / FileBindings / snapshot seams. A wrong declaration-only filter or a broken
  scope-walk collapse silently mis-resolves symbols project-wide.
- **Likelihood**: high (largest-surface change here).
- **Mitigation**: gate **every phase** on the full builder suite **including** the two external-fixture
  tests (`LuaRecursiveReferenceTest`, `LuaDescriptionIndexTest`) that CI skips — `tooling/gce-builder/gce-builder.sh
  run test`, expect 2123/0/1. §3.3 shows the scope-walk collapse is a **deliberate, correct** semantic
  change (the self-referential `local x = x` RHS now follows Lua scope rules); TC-01 regression-locks the
  declaration-only index and TC-02 regression-locks the scope-walk correction.

### Risk 1.2: Snapshot cache invalidation gap (target switch)
- **Impact**: if the re-keyed `CachedValuesManager` snapshot loses the target dependency, a REDIS↔Lua
  target switch serves a stale, wrongly-seeded snapshot (regression of REDIS-04 §3.1a).
- **Likelihood**: medium — hinges on `LuaProjectSettings` exposing an observable modification tracker.
- **Mitigation**: DR-01 confirms the tracker before Phase 3; §3.4 documents the fallback (explicit
  target re-check inside a `MODIFICATION_COUNT`-dependent provider) if no tracker exists. TC-04 locks it.

### Risk 1.3: Index-version bump forces a full re-index
- **Impact**: bumping `VERSION 2 → 3` triggers a one-time full rebuild of the FileBindings index on
  first open after the change — a visible background pass; on a large project it is not instant.
- **Likelihood**: high (certain) but low severity — one-time, correct-by-design (§3.2).
- **Mitigation**: none needed; documented honestly. No data migration is possible (record shape
  unchanged; binding *set* shrinks).

### Risk 1.4: TYPE-10 reentrancy broken by cache re-key
- **Impact**: if `getCachedValue` runs *before* `inProgressSnapshot`, a re-entrant `visitFuncCall →
  forFile` recurses into `buildSnapshot` and stack-overflows or double-builds.
- **Likelihood**: low — mitigated by design (§2.3 keeps `inProgressSnapshot` as the first statement).
- **Mitigation**: TC-06 asserts a re-entrant chain resolves without recursion; code review checks the
  statement order.

## Design Gaps

### Gap 2.1: LuaProjectSettings modification observability
- **Question**: does `LuaProjectSettings` bump a `ModificationTracker` (or equivalent) when the active
  target changes, so `CachedValuesManager` can depend on it?
- **Grounding**: as of `main` @ `828db71d`, `LuaProjectSettings` (`settings/LuaProjectSettings.kt:18`) is
  a `PersistentStateComponent` that does **not** implement `ModificationTracker` and exposes no mod-count.
  A text-free target switch does **not** bump `PsiModificationTracker.MODIFICATION_COUNT` (it mutates
  settings state, not PSI) — so a `MODIFICATION_COUNT`-only cache dependency would serve a stale snapshot
  across a target switch.
- **Options / leaning**: (a) DR-01 **adds** a `ModificationTracker` (mod-count bumped on target change) to
  `LuaProjectSettings` and passes it as a `Result.create` dependency (leaning — the clean fix); (b) no
  tracker is added → the design §3.4 **fallback** applies: `forFile` compares the
  `31 * textHash + target.hashCode()` key on **every** call and rebuilds on mismatch (the compare runs
  each invocation — cheap, but not free).
- **Resolved by**: DR-01; fold the concrete dependency object (or the "no tracker → per-call compare"
  decision) back into design §3.4.

### Gap 2.2: MAINT-25 / MAINT-30 merge order on the shared `forFile` seam
- **Question**: MAINT-25 (type-graph immutability) and MAINT-30 both edit `LuaTypes.kt` / `LuaTypesVisitor`
  `forFile`; which lands first?
- **Options / leaning**: roadmap marks MAINT-25 `Serial` on the type engine → **MAINT-25 first**, then
  MAINT-30 re-keys around the stabilized snapshot (leaning). If MAINT-30 lands first, MAINT-25 rebases
  its edits onto the `CachedValuesManager` form (mechanical).
- **Resolved by**: DR-02; record the decision, then proceed in that order.

### Gap 2.3: resolveMember parity with the stub-index return names
- **Question**: can `LuaTypeManager.resolveType(class).resolveMember(method)` reproduce the multi-return
  name list the current stub-index path yields (`---@return A, B`), including the `self→receiverClass`
  substitution the method-chain provider does?
- **Options / leaning**: `resolveMember` returns a `LuaFunctionType` whose return `LuaType.name` should
  match; the `self` substitution already happens in `graphTypeToLuaType`. If a gap appears, keep the
  stub path as a documented fallback (MAINT-30-04 is priority C).
- **Resolved by**: DR-03; apply the outcome in Phase 5.

## Technical Debt & Future Work
- **TBD: indexer/consumer require-walk unification** — `fileRequires` (consumer, index-backed) and the
  indexer's own AST require walk (`LuaFileBindingsIndex.kt:285-340`) remain two functions by necessity
  (the index cannot read itself). Not debt, but noted so a future reviewer does not "re-dedup" it.
- **TBD: run-config + assignability-inspection duplication** — review §2.5.3 also named run-config
  boilerplate and assignability-inspection twins; both are outside the resolution/caching surface and
  belong to MAINT-13 / MAINT-29 respectively.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-30-00-DR-01 | Grep/read `LuaProjectSettings` for a `ModificationTracker`/mod-count bumped on target change; name the exact dependency object for design §3.4 | Gap 2.1 / Risk 1.2 | done |
| MAINT-30-00-DR-02 | Confirm MAINT-25 vs MAINT-30 merge order with the type-engine owner; record Serial decision | Gap 2.2 | done |
| MAINT-30-00-DR-03 | Spike `resolveType(class).resolveMember(method)` on the existing method-chain test fixtures; compare return-name output to the stub path | Gap 2.3 | done (Phase 5) |

### DR-01 outcome (2026-07-17)
Confirmed against `main` @ `f52a5e16`: `LuaProjectSettings` (`settings/LuaProjectSettings.kt:18`) is a
`PersistentStateComponent` that does **not** implement `ModificationTracker` and exposes no mod-count.
The active target is mutated exclusively through `State.setTarget(newTarget)` — reached directly by
`LuaRocksInterpreterInitializer` (`:37`) and the tests (`state.setTarget(...)`), and indirectly by
`setTargetAndNotify` (`:132`) and `State.getTarget()`'s lazy default (`:101`). **Decision: branch (a)** —
add a transient `SimpleModificationTracker` (`targetModificationTracker`) to `State`, bumped as the first
statement of `setTarget()`. The snapshot provider lists
`LuaProjectSettings.getInstance(project).state.targetModificationTracker` as a `Result.create`
dependency (fetched fresh at each build), alongside `psiFile` + `PsiModificationTracker.MODIFICATION_COUNT`.
It is `@get:Transient` so it never serializes; across a `loadState` swap the state re-reindexes anyway.
This is the concrete dependency object for design §3.4 (no per-call target-hash fallback needed).

### DR-02 outcome (2026-07-17)
MAINT-25 (type-graph immutability) is **done and merged to main** (per the roadmap Serial ordering and
the Wave-19 supervisor handoff). MAINT-30 therefore starts from the stabilized `forFile` seam and re-keys
the snapshot cache around it — the Serial-MAINT-25-first ordering is satisfied. No coordination pending.

### DR-03 outcome (Phase 5)
`LuaTypeManager.resolveType(class).resolveMember(method)` returns a `LuaFunctionType` whose return
`LuaType.name` reproduces the annotated single-return name and the `self→receiverClass` substitution
(via `graphTypeToLuaType`). The one gap: **multi-value `---@return A, B`** — `LuaFunctionType.returnType`
is a single `LuaType`, so `resolveMember` collapses to the first return only. The stub path's
`annotatedReturnNames`/`inferredReturnNames` (which read all `---@return` descriptors) is therefore kept
as the documented multi-return fallback (MAINT-30-04 is priority C); the provider now tries `resolveMember`
first and falls back to the stub path when it yields fewer names. TC-09 (existing chain-hint fixtures)
stays green.

## Test Case Gaps
- No fixture yet asserts a **usage** (not a declaration) is excluded from the FileBindings record —
  TC-01 adds it.
- No fixture asserts `multiResolve` is served from `ResolveCache` on repeat — TC-03 adds a call counter.
- No fixture asserts the module resolver's non-refreshing VFS behavior — TC-08 adds it.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
