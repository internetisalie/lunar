---
id: "TYPE-11-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# TYPE-11: Implementation Plan

The de-risking already built and measured this change once (`design.md` §1). The phases below rebuild
it in a reviewable order; the measurement scaffold itself was reverted and is **not** the starting
point — Phase 1 starts from `main`.

**Standing rule for every phase**: the harnesses in `src/test/kotlin/net/internetisalie/lunar/type/`
are already committed and green on `main`. They must stay green at the end of every phase, and
`TypeElevenDr01ResidualTest` in particular must never be made to pass by relaxing an assertion.

## Phases

### Phase 1: Provenance and the recorder, wired to nothing [Must]

- **Goal**: both new components exist, are tested, and change no behaviour. `forFile` is untouched, so
  the suite result is bit-identical to `main`.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder` — realizes design §2.1
        and §3.1. `object`, `ThreadLocal<ArrayDeque<MutableSet<String>>>`, three functions:
        `recording`, `report`, `reportFile`. **`report` writes to every open frame, not just the
        innermost** (§3.1 step 3) — that is the whole correctness of nesting.
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaLibraryProvenance` — realizes design §2.2
        and §3.2. Light `@Service(Service.Level.PROJECT)`; **no `plugin.xml` entry** (design §7).
        Root list memoized via `CachedValuesManager.getManager(project).getCachedValue(project) { … }`
        with `ProjectRootModificationTracker` + `targetModificationTracker` as dependencies.
        Match on `psiFile.originalFile.virtualFile?.url` with the `url == root || url.startsWith("$root/")`
        prefix test.
  - [ ] Add `LuaLibraryProvenanceTest` asserting the five DR-02 facts against the production class
        (the DR-02 harness asserts them against a local copy of the predicate; this is the same
        assertions pointed at the real service).
- **Exit criteria**: full suite green with the same count as `main`; `LuaLibraryProvenanceTest` green;
  each of its assertions shown red by the mutation named for it in `risks-and-gaps.md`'s ledger.

### Phase 2: Report from the type manager [Must]

- **Goal**: every cross-file consumption is recorded and replayed from cache. Still no behaviour
  change — nothing reads the recorded set yet.
- **Tasks**:
  - [ ] Add `sourceCache` to `LuaTypeManagerImpl` immediately after `globalCache` — realizes design
        §3.6 step 1. Same `createCachedValue(…, false)` shape, same
        `PsiModificationTracker.getInstance(project)` dependency, same `synchronizedMap`.
  - [ ] Add the private helpers `recordUnder(key, body)` and `replaySources(key)` — design §3.6
        steps 3–4. Both are ≤ 4 lines; the ≤ 3-argument cap (engineering contract §3) is satisfied.
  - [ ] Wrap the three doors — `resolveType`, `resolveModule`, `resolveGlobal` — so a cache **miss**
        goes through `recordUnder("type:$name" | "module:$moduleName" | "global:$name")` and a cache
        **hit** calls `replaySources(...)` before returning. Do not move the early returns that
        precede each cache read.
  - [ ] Insert the six `reportFile` calls listed in design §3.5, at the stated lines. `typeOfGlobalIn`
        gets `.onEach { LuaTypeSourceRecorder.reportFile(it) }` **after** the existing
        `.filter { it != exclude }` — reporting every file visited, not only the one that yields a
        type (§3.5's over-approximation rule).
- **Exit criteria**: full suite green with the same count as Phase 1. Recording is inert while no
  frame is open, so an unchanged suite is the expected result and any movement is a defect.

### Phase 3: Make `forFile` conditional [Must]

- **Goal**: the feature. `TypeElevenDr04LatencyTest` arm B drops by an order of magnitude and
  `TypeElevenDr01ResidualTest` stays green.
- **Tasks**:
  - [ ] Edit `LuaTypesSnapshot.forFile` (`LuaTypes.kt:212-224`) — realizes design §2.3 and §3.3.
        Wrap `LuaTypesVisitor.buildSnapshot(psiFile)` in `LuaTypeSourceRecorder.recording { … }`,
        compute `pinnable` by the three short-circuiting tests in §3.3 steps 1–3, and select the churn
        dependency in step 5. The `inProgressSnapshot` reentrancy guard, the `psiFile` dependency and
        `targetModificationTracker` are unchanged.
  - [ ] Add `TypeElevenGenerationSignalTest` — covers TYPE-11-02. Three cases: (a) enabling a
        definition library re-provisions and invalidates; (b) `setTarget` invalidates a pinned
        snapshot; (c) a project-file edit does **not** invalidate a pinned snapshot.
  - [ ] Add `LuaTypeSourceRecorderCoverageTest` — mitigates `risks-and-gaps.md` Risk 1.1. Reads
        `LuaTypeManagerImpl.kt` as text and asserts the count of `PsiManager.getInstance(project).findFile`,
        `StubIndex.getElements` and `FileBasedIndex.getInstance().getContainingFiles` call sites
        against a recorded number, with a message naming design §3.5. A new site fails the build and
        forces the author to decide whether it needs a `reportFile`.
  - [ ] Add a library-`require`s-a-project-module case to `TypeElevenDr01ResidualTest` — closes the
        first item under `risks-and-gaps.md` "Test Case Gaps".
- **Exit criteria**:
  - `tooling/gce-builder/gce-builder.sh run "ktlintCheck lintDocs test --rerun --no-build-cache"` —
    BUILD SUCCESSFUL, 0 failures.
  - the **corpus sweep run explicitly** — `run "test -PwithCorpus --rerun --no-build-cache"` — green, and the six sweep classes present in `build/test-results/test/`. Reference on `69ad6b57`: 2 571 tests, 0 failures. (`git status --short src/test/resources/corpus/`
    empty).
  - `TypeElevenDr04LatencyTest` arm B median at least 5× below the `main` figure **measured in the
    same run** as its own arm A. No cross-run ratio is quotable (design §1.5).
  - Every new assertion shown red under a stated mutation, appended to the `risks-and-gaps.md` ledger.

### Phase 4: Close the negative de-risking results [Should]

- **Goal**: the two things Phase 3 ships without proof stop being unproven.
- **Tasks**:
  - [ ] TYPE-11-DR-06 — decide whether the dumb-mode guard is dead code or unprotected production
        behaviour, and either delete it or build a fixture that reproduces the staleness.
  - [ ] TYPE-11-DR-07 — probe the lazy `LuaTypeReference` escape (Risk 1.3).
- **Exit criteria**: `risks-and-gaps.md` Gap 2.1 and Risk 1.3 both closed with pasted output, or
  re-filed as bugs with roadmap rows.

### Phase 5: The remaining arm-B cost [Could]

- **Goal**: understand, not necessarily fix, the 3–5× that survives Phase 3.
- **Tasks**:
  - [ ] TYPE-11-DR-08 — profile `resolveGlobal` + `graphTypeToLuaType` over a 3 600-member table and
        decide whether a `visited`-map reuse or a member-set cache is a separate feature.
- **Exit criteria**: a measured recommendation in `risks-and-gaps.md`, or a new feature filed.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-- | :-- |
| TYPE-11-01 — a platform-library snapshot survives an unrelated edit | M | Phase 3 |
| TYPE-11-02 — every generation signal invalidates it | M | Phase 1 (the tracker composition) + Phase 3 (`TypeElevenGenerationSignalTest`) |
| TYPE-11-03 — identification is by provenance | M | Phase 1 |
| TYPE-11-04 — no new stale-type defect | M | Phase 2 (recording) + Phase 3 (the condition); gated by `TypeElevenDr01ResidualTest`, the full suite and the four corpus baselines |
| TYPE-11-05 — a dumb-mode build is never cached across the generation | M | Phase 3 (the guard, design §3.4) + Phase 4 (DR-06, because the guard currently has no reproducing test) |

## Verification Tasks

- [ ] `TypeElevenDr01ResidualTest` — 3 tests, covers TYPE-11-04. **Already committed and green on
      `main`**; it must stay green through every phase.
- [ ] `TypeElevenDr02ProvenanceTest` — 5 tests, covers TYPE-11-03. Already committed.
- [ ] `TypeElevenDr05DumbModeTest` — 2 tests, TYPE-11-05. Already committed, and **explicitly not a
      gate** (`risks-and-gaps.md` Gap 2.1).
- [ ] `TypeElevenDr04LatencyTest` — printing probe, no assertions. Read its numbers; do not treat a
      green run as a pass.
- [ ] `LuaLibraryProvenanceTest` (Phase 1) — the DR-02 assertions against the production service.
- [ ] `TypeElevenGenerationSignalTest` (Phase 3) — covers TYPE-11-02.
- [ ] `LuaTypeSourceRecorderCoverageTest` (Phase 3) — mitigates Risk 1.1.
- [ ] Run `human-verification-checklists.md` — the whole feature is a *cache lifetime* change, and the
      symptom it fixes (typing latency with a large library loaded) is not observable in a light
      fixture at all.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Provenance and the recorder, wired to nothing | todo | Must |
| Phase 2: Report from the type manager | todo | Must |
| Phase 3: Make `forFile` conditional | todo | Must |
| Phase 4: Close the negative de-risking results | todo | Should |
| Phase 5: The remaining arm-B cost | todo | Could |
