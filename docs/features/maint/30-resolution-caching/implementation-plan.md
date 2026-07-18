---
id: "MAINT-30-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-30"
folders:
  - "[[features/maint/30-resolution-caching/requirements|requirements]]"
---

# MAINT-30: Implementation Plan

Sequenced so each phase leaves the build green and is independently gated on the **full builder
suite including the two external-fixture tests** (`LuaRecursiveReferenceTest`,
`LuaDescriptionIndexTest`) — these exercise cross-file resolution + the FileBindings index and are
CI-skipped, so the gate is `tooling/gce-builder/gce-builder.sh run test` on the builder, not CI.
Baseline to preserve: **2123 pass / 0 fail / 1 ignored** on `main` @ `828db71d`.

Order rationale: DR spikes first (retire the tracker/immutability unknowns), then the two `Must`
correctness fixes, then the `Should` dedup, then the `Could` idiom migration.

## Phases

### Phase 0: De-risking [Must]
- **Goal**: retire DR-01 (settings modification tracker) and DR-02 (MAINT-25 ordering) before touching
  the snapshot cache.
- **Tasks**:
  - [x] DR-01 — confirm whether `LuaProjectSettings` exposes/implements a `ModificationTracker` that
    bumps on target change; record the exact dependency object for §3.4 — realizes risks DR-01.
  - [x] DR-02 — confirm MAINT-25 merge order with the type-engine owner; record Serial decision —
    realizes risks DR-02.
- **Exit criteria**: DR-01/DR-02 marked done in `risks-and-gaps.md` with the concrete dependency object
  named; no code yet.

### Phase 1: Declaration-only index + version bump [Must]
- **Goal**: MAINT-30-01 — file-bindings index records only file-scope declarations; force re-index.
- **Tasks**:
  - [ ] Replace `extractBindingsFromStatement` in `net.internetisalie.lunar.lang.indexing.LuaFileBindingsIndex`
    with the container-matching filter — realizes design §3.1.
  - [ ] Bump `LuaFileBindingsIndex.VERSION` `2 → 3` — realizes design §3.2.
- **Exit criteria**: TC-01, TC-07 pass; `LuaRecursiveReferenceTest` + `LuaDescriptionIndexTest` green on
  the builder; a global usage no longer appears as a cross-file binding.

### Phase 2: ResolveCache in multiResolve [Must]
- **Goal**: MAINT-30-02 (part 1) — cache reference resolution.
- **Tasks**:
  - [ ] Move `multiResolve`'s body into `private fun doMultiResolve`; add the static
    `RESOLVER: ResolveCache.PolyVariantResolver<LuaNameReference>`; delegate `multiResolve` to
    `ResolveCache.getInstance(project).resolveWithCaching(this, RESOLVER, false, incompleteCode)` in
    `net.internetisalie.lunar.lang.LuaNameReference` — realizes design §2.2.
- **Exit criteria**: TC-03 (resolver body runs once per cache epoch via a call counter) passes; full
  builder suite still 2123/0/1 (resolution semantics unchanged).

### Phase 3: Snapshot cache re-key + delete FileUserData [Must]
- **Goal**: MAINT-30-02 (part 2) — `CachedValuesManager` replaces `FileUserData`.
- **Tasks**:
  - [ ] Re-key `LuaTypesSnapshot.forFile` onto `CachedValuesManager.getCachedValue` with the target
    dependency from DR-01; keep `inProgressSnapshot` as the first statement — realizes design §2.3, §3.4.
  - [ ] Change `LuaTypesVisitor.KEY` type to `Key<CachedValue<LuaTypes>>`; delete
    `net.internetisalie.lunar.lang.psi.FileUserData` and its imports — realizes design §2.3.
- **Exit criteria**: TC-04 (target switch invalidates), TC-05 (reparse invalidates), TC-06
  (reentrancy safe) pass; full builder suite green.

### Phase 4: Single canonical helpers [Should]
- **Goal**: MAINT-30-03 — one scope walk, one require extractor, one module resolver, one luarocks command.
- **Tasks**:
  - [ ] Collapse `LuaNameReference` Phase-1 onto `LuaResolveUtil.scopeCrawlUp`; delete the inline walk
    — realizes design §2.1, §3.3. **This is a deliberate scope-walk correction** (§3.3): the RHS of a
    self-referential `local x = x` now correctly resolves to the outer/undeclared `x`, not the new local.
  - [ ] Create `lang/indexing/LuaRequireExtraction.kt::fileRequires`; delete `LuaNameReference`'s
    `extractRequires`/`extractRequiresFromStatement`; repoint `LuaCrossFileCompletionProvider` and
    `multiResolve` Phase-2 — realizes design §2.4, §3.5.
  - [ ] Create `lang/path/LuaModuleFileResolver.kt::resolveModuleCandidates(): Sequence<LuaFile>`
    (refresh=`false`); repoint `LuaRequireReference.resolve` (`.firstOrNull()`) and
    `LuaTypeManagerImpl.doResolveModule` (`.firstNotNullOfOrNull { getModuleType(it, context) }`,
    preserving its skip-untyped-and-try-next-pattern terminal) — realizes design §2.5, §3.6.
  - [ ] Add `LuaRocksEnvironment.command`; migrate the 5 inlined call sites (`LuaRocksInstalledService`,
    `LuaRocksInstallExecutor`, `WorkspaceBuildRunner`, `LuaRocksSearchService` ×2, `LuaRocksMetadataService`)
    — realizes design §2.6.
- **Exit criteria**: TC-01/TC-03 unchanged; TC-02 (self-referential-initializer scope correction)
  passes; a `require` refresh-flag regression test (TC-08) passes; full builder suite green.

### Phase 5: Method-chain idiom migration [Could]
- **Goal**: MAINT-30-04 — provider uses `resolveMember`.
- **Tasks**:
  - [ ] Replace `findMethodDecl`/`annotatedReturnNames`/`inferredReturnNames` with a
    `LuaTypeManager.resolveType(...).resolveMember(...)` lookup in
    `net.internetisalie.lunar.lang.insight.hint.LuaMethodChainInlayHintProvider` — realizes design §2.7.
- **Exit criteria**: TC-09 (existing chain-hint tests) still green; if `resolveMember` cannot match
  multi-return output, DR-03 outcome applied (keep stub fallback), documented.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-30-01 | M | Phase 1 |
| MAINT-30-02 | M | Phase 2, Phase 3 |
| MAINT-30-03 | S | Phase 4 |
| MAINT-30-04 | C | Phase 5 |

## Verification Tasks
- [ ] TC-01 declaration-only cross-file resolution (usage not recorded) — Phase 1.
- [ ] TC-02 self-referential-initializer scope correction (`local x = x` RHS ≠ new local) — Phase 4.
- [ ] TC-03 ResolveCache single-compute counter — Phase 2.
- [ ] TC-04/TC-05/TC-06 snapshot invalidation + reentrancy — Phase 3.
- [ ] TC-07 dotted-assignment not a binding — Phase 1.
- [ ] TC-08 module-resolver refresh flag — Phase 4.
- [ ] TC-09 method-chain hints unchanged — Phase 5.
- [ ] Full builder gate `tooling/gce-builder/gce-builder.sh run test` (incl. `LuaRecursiveReferenceTest`,
  `LuaDescriptionIndexTest`) at each phase; expect 2123/0/1.
- [ ] Run `human-verification-checklists.md`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risking | done | Must |
| Phase 1: Declaration-only index | todo | Must |
| Phase 2: ResolveCache in multiResolve | todo | Must |
| Phase 3: Snapshot cache re-key | todo | Must |
| Phase 4: Single canonical helpers | todo | Should |
| Phase 5: Method-chain idiom migration | todo | Could |
