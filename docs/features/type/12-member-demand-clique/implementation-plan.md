---
id: "TYPE-12-PLAN"
title: "12: Implementation plan"
type: "plan"
parent_id: "TYPE-12"
folders:
  - "[[features/type/12-member-demand-clique/requirements|requirements]]"
---

# TYPE-12 Implementation Plan

Phase 0 records today's engine as golden files, before anything changes. Phase 1 is the behaviour
change and its own tests, gated by those goldens. Phase 2 is the corpus gates that a type engine
change owes and that the routine loop does not run. All are Must: without Phase 0 the equivalence
requirements have no gate that can fail, and without Phase 2 the change is unverified against the
sweeps.

**Precondition.** Every risks-and-gaps de-risking task is `done` (DR-11 is the last), and the
requirements cases cite its results. Rebase the branch onto `main` before starting.

**The base moves under the measurements.** Every red set this plan expects was measured on the
`e762dc74` sources (DR-06 at `e762dc74`, DR-11 at `c06a71a4`, whose `src/` is the same). Since then
`main` changed three engine-reachable files, all BUG-476 (`2a447149`): `git diff --stat e762dc74
a90adac1 -- src/main` lists `LuaNameReference.kt`, `LuaScopeProcessor.kt` and `LuaFunctionExt.kt`.
They reach the type engine through `LuaExpectedCallbackResolver`, which `LuaTypesVisitor.kt:1105`
calls for every call. Phase 0 records the goldens on the rebased base, so they absorb the change;
Phase 1's mutation task re-measures every red set on that base and applies the branch it states.

Builder protocol for every gate below: from the worktree root,
`tooling/gce-builder/gce-builder.sh run "<gradle args>"`, output redirected to a log file, judged by
its text (`BUILD SUCCESSFUL`, the `tests completed` line), never piped through `tail`. `--rerun` on
every test invocation.

## Phase 0 — Record today's engine [Must]

- **Goal**: the golden set of requirements "The golden set", recorded from the implementation base,
  in a commit that touches no engine code.
- **Tasks** (in order):
  - [ ] `LuaTypesSnapshot`: add `mappedElements()` and `declaredGlobalNames()` (design §4.4) — and
        nothing else in `src/main`.
  - [ ] Create `TypeGoldenFixtures.kt`, `TypeGolden.kt`, `LuaTypeGoldenShapesTest.kt`,
        `LuaTypeGoldenMemberFilesTest.kt` exactly as design §4.6, §5.5 and Appendix C.
  - [ ] `build.gradle.kts`: the `recordTypeGolden` property and the `excludeExternalFixtureTests`
        line (design §4.6), with a reason comment on each.
  - [ ] Record and pull back (design §5.5, "Recording and pulling back"). The recording lands in
        the builder's `build/type-golden/recorded/`, never under `src/`. Apply design §5.5's
        acceptance checks 1–3 before going on; check 4 is the next task.
  - [ ] Verify without the switch:
        `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaTypeGolden*'"` → green:
        `everyShapeMatchesItsGolden`, `noShapeDeclaresAStubTypeName` and
        `everyMemberFileMatchesItsGolden` all pass.
  - [ ] Mutation-prove case 11 (skill `mutation-proof`, restoring per skill `temporary-edits`), one
        mutation at a time, running the same command for each:
        (1) add the excluded `fileUnion` entry (design Appendix C) to `NAMED_SHAPES` → observe
            `noShapeDeclaresAStubTypeName` red with `shapes/fileUnion.golden: File`; restore.
        (2) in `stubTypeNames()`, replace `"src/main/resources/runtime"` with
            `"src/main/resources/no-such-runtime"` → observe `noShapeDeclaresAStubTypeName` red with
            `no stub type names found under src/main/resources/runtime`; restore. If it stays green,
            stop: the non-empty assertion is vacuous, and design §5.6 returns to planning.
        Record both failure messages in the Phase 0 commit message.
  - [ ] Verify inside the full ordinary suite:
        `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` → both golden tests
        green (the suite's other results as on the base).
  - [ ] Commit the test sources above, the build-script change, the snapshot accessors and every
        `.golden` file — one commit, before any Phase 1 edit.
- **Exit criteria**: the commit exists; `git diff <base>..HEAD -- src/main` shows only the snapshot
  accessors of the first task; both verification runs green.

## Phase 1 — The hub and the replication rule [Must]

- **Goal**: TYPE-12-01 … TYPE-12-04, with every requirements case naming those requirements green
  and each named mutation observed red. No `.golden` file is modified in this phase.
- **Tasks** (in order; each leaves the tree compiling):
  - [ ] Create `net.internetisalie.lunar.lang.psi.types.LuaMemberDemandHubs` exactly as design §4.1
        (the class body there is complete).
  - [ ] `LuaTypeGraph`: add `internal val memberDemandHubs = LuaMemberDemandHubs()` and the
        `@TestOnly internal fun closureSize(): Int = edgeCount()` meter (design §4.3).
  - [ ] `LuaTypesSnapshot`: add the `@TestOnly internal fun closureSize()` pass-through (design §4.4).
  - [ ] `LuaTypeGraph.checkTableCompatibility`: route both `addEdge` calls through
        `memberDemandHubs.wiringTargetFor(useNode)` (design §5.2).
  - [ ] `LuaTypeGraph.checkNodePairs`: add `VisitFacts` and `AdmittedPair`, replace the
        `checkCompatibility(...)` call with `checkAdmittedPair(AdmittedPair(valueNode, useNode,
        valueWrite), facts)`, and add `checkAdmittedPair` + `replicateHubAnchoredErrors`
        (design §4.3, §5.3). Keep the `LuaTypePairWriteDifferential` block where it is, before the
        new call.
  - [ ] `LuaTypesVisitor`: add `MethodDemandKey`, `methodDemandHubs` and `methodSiteMember`
        (design §4.2, §5.1 — follow the step order exactly), and replace the method branch at
        `:924-931` with the single `calleeNode = methodSiteMember(...)` line.
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaMemberDemandHubTest.kt`
        (`BaseDocumentTest`, `EdtTestUtil.runInEdtAndWait<RuntimeException>` around each body) with
        one `@Test` per requirements case that asserts one fixture's errors, types or closure
        (cases 1–7). Each method's name starts with `case` and the two-digit case number
        (`case01ClosureGrowsLinearly`), so a failure names its case.
        Fixture text comes from `TypeGoldenFixtures` (`annotated(n)`, `NAMED_SHAPES`). Cast
        `LuaTypesSnapshot.forFile(file) as LuaTypesSnapshot` for `closureSize()`, as
        `LuaTypeGraphRootResolutionBudgetTest.annotatedClassFixtureStaysWithinItsRootResolutionBudget`
        does (`:49` on `main` at `a90adac1`). Render an error as
        `"${it.severity}|${it.message}|(${it.element.textRange.startOffset},${it.element.textRange.endOffset})"`
        and compare **sorted lists**. Method names must not contain `Corpus` (`build.gradle.kts`
        silently skips those without `-PwithCorpus`).
  - [ ] Mutation-prove (skill `mutation-proof`), one at a time, snapshotting and restoring per skill
        `temporary-edits`. For each, run
        `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaMemberDemandHubTest*' --tests '*LuaTypeGolden*' --tests '*LuaAnnotatedClassDiagnosticsTest*'"`
        and record which test methods failed and, for the golden tests, which goldens their failure
        message names. The table below is the **exact** expected outcome: a test method red that the
        row does not list, a listed one green, a golden named that the row's golden set does not
        hold, or a listed golden not named, is a difference. Columns: the unit cases are
        `LuaMemberDemandHubTest.caseNN…`; case 9 is `everyShapeMatchesItsGolden`, case 10
        `everyMemberFileMatchesItsGolden`, case 11 `noShapeDeclaresAStubTypeName`; *P*, *T*, *F*
        are `LuaAnnotatedClassDiagnosticsTest`'s `declaredParamViolationKeepsItsFourErrorTierDiagnostics`,
        `twentyCallSitesEmitTheSameEightyThreeDiagnostics` and
        `fortyCallSitesEmitTheSameOneHundredAndSixtyThreeDiagnostics`. Evidence: DR-06 (unit cases,
        *P*, *T*, *F*) and DR-11 step 8 (golden sets).

        | mutation | variant | red, exactly | goldens named |
        | :-- | :-- | :-- | :-- |
        | (a) `methodSiteMember` → today's per-site minting | BASE | case 1 | none (the goldens are today's engine) |
        | (b) remove the `replicateHubAnchoredErrors` call | A6NF | cases 2, 3, 4, 9; *T*, *F* | DR-11 step 8 row A6NF |
        | (c) M-C (drop `addEdge(target, valueNode)` in `checkTableCompatibility`) | A6MC | cases 1, 2, 3, 4, 6, 7, 9, 10; *P*, *T*, *F* | DR-11 step 8 "A6MC's full set" |
        | (d) design §5.1 step 5 names `hub ?: siteMember` | A6TH | cases 5, 9, 10 | DR-11 step 8 row A6TH |
        | (e) delete design §5.1 step 7 (`addEdge(hub, siteMember)`) | A6NE | cases 1, 2, 3, 6, 9; *T*, *F* | DR-11 step 8 row A6NE |
        | (f) mint a separate hub node for a key's first site | A5 | cases 1, 7 | none |

        Case 11 is green under every row.

        **If an observed outcome differs from its row** (the rows were measured before the rebase;
        see "The base moves under the measurements"):
        - If every case the requirements table pairs with this mutation (its Mutation column) is
          still red, the difference is the base moving. Append the observed row to DR-11's result as
          "Re-measured after the rebase onto `<main SHA>`", with the failure messages verbatim, point
          this table's row at it, and name BUG-476 (`2a447149`) as the base change in the commit
          message. Continue.
        - If a case the requirements table pairs with this mutation is green — the set shrank below
          what the case requires — stop. That case can no longer fail; do not edit it. Phase 1 returns
          to planning.

        Record each observed failure message in the commit message.
- **Exit criteria**: the mutation run's test set green on the unmutated code; every mutation in the
  table observed as its row states, or as its row re-measured under the first branch above; then the
  full ordinary suite
  `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` with 0 failures, both
  golden tests among the passes; `git diff <Phase 0 commit>..HEAD -- src/test/resources/types-golden`
  empty.

## Phase 2 — The corpus gates [Must]

- **Goal**: TYPE-12-05.
- **Tasks**:
  - [ ] Record `sha256sum src/test/resources/corpus/*.baseline` before the run.
  - [ ] `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"` → 0
        failures; confirm `LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `LuaInspectionParityTest` and
        `LuaAnnotatedFixtureSweepTest` result XMLs exist with this run's timestamps; no `IMPROVED` or
        `REGRESSED` line in the log; the baselines' hashes unchanged.
  - [ ] `tooling/gce-builder/gce-builder.sh run ktlintCheck` alone (never paired with
        `ktlintFormat`, BUG-445). If it fails, format on the VM and pull the result back per
        `.agents/AGENTS.md`, then re-check.
  - [ ] `CHANGELOG.md`: one user-visible line (skill `changelog`) — analysis of a file with many
        method calls on one annotated receiver is no longer quadratic.
  - [ ] Front-matter: `status: done` on `requirements.md`; delete the TYPE-12 row from
        `docs/roadmap.md` when closing.
- **Exit criteria**: all of the above observed in logs, not inferred.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TYPE-12-01 | M | Phase 1 |
| TYPE-12-02 | M | Phase 1 (case 1) |
| TYPE-12-03 | M | Phase 0 (golden recording, case 11), Phase 1 (cases 2, 3, 4, 5, 9, 10) |
| TYPE-12-04 | M | Phase 0 (golden recording, case 11), Phase 1 (cases 6, 9, 10) |
| TYPE-12-05 | M | Phase 1 (unit gates), Phase 2 (corpus gates) |
| TYPE-12-06 | S | evidence only — risks-and-gaps DR-02; no task |
| TYPE-12-07 | C | not done, by measurement (requirements) |

## Verification Tasks

- [ ] `LuaMemberDemandHubTest` — the requirements cases on single fixtures.
- [ ] `LuaTypeGoldenShapesTest`, `LuaTypeGoldenMemberFilesTest` — the golden cases.
- [ ] Existing BUG-473 gates, unmodified — case 8.
- [ ] Manual: [human-verification-checklists.md](human-verification-checklists.md).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Record today's engine | todo | Must |
| Phase 1: The hub and the replication rule | todo | Must |
| Phase 2: The corpus gates | todo | Must |
