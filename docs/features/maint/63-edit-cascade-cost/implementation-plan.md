---
id: "MAINT-63-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-63"
folders:
  - "[[features/maint/63-edit-cascade-cost/requirements|requirements]]"
---

# MAINT-63: Implementation Plan

**Blocked.** Do not start this plan until risks DR-22 is done (risks Decision 2.1): as designed,
the change fails TC 13 (risks DR-20). Phase 0 can land on its own.

The base commit for TC 10 to TC 12 is this item's branch point on `main`. It already has
`LuaAloneDigestTest` and the `withAloneDigest` block. TC 13 needs Phase 0 at the base commit as
well, so the base commit for TC 10 and TC 13 is Phase 0's commit.

## Phases

### Phase 0: After-edit oracle in the sweep [Must]
- **Goal**: the sweep fails when a settled after-edit read differs from a full rebuild (MAINT-63-04 (c)).
- **Tasks**:
  - [ ] In `src/test/kotlin/net/internetisalie/lunar/corpus/SnapshotCostVerdict.kt`: `SweepRow`'s
    three new fields, `oracleMismatches`, `oracleCompared` and `oracleCounts` (design §2.6).
  - [ ] In `src/test/kotlin/net/internetisalie/lunar/corpus/LuaSnapshotCostSweepTest.kt`:
    `rebuildPass`, `forceFullRebuild`, `ORACLE_SKIPPED`, `FileResult.rebuild`, `toRow`, `toJson`,
    and `testZzVerdict`'s two oracle additions (design §2.6).
  - [ ] In `SnapshotCostVerdictTest`: `testO1OracleMismatchIsReported`,
    `testO2OracleSkipsUnreadRows`, `testO3OracleComparedCountsOkPairs`.
- **Exit criteria**:
  - O1 to O3 pass, and the existing `SnapshotCostVerdictTest` methods pass unmodified.
  - **O1 fails** with `oracleMismatches` returning an empty list. Run the mutant once, record the
    result in the PR, and restore.
  - The full `test --rerun --no-build-cache` suite has no new failures; `ktlintCheck` passes.

### Phase 1: Per-source trackers and tests [Must]
- **Goal**: unpinned snapshots depend on their recorded sources.
- **Tasks**:
  - [ ] In `LuaTypeSourceRecorder`, apply design §2.4: concurrent sets, `pendingFrames`,
    `inProgressFrames`, `recordingInto`, `recordingFor`, the pending step in
    `reportInProgressHit` (§3.2), and `isUnidentified`.
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaSourceChangeTracker.kt`
    holding `LuaSourceChangeTracker` (design §2.1, §3.1, §3.3) and `LuaSourceSetTracker` (§2.2, §3.3).
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaSourceChangePreprocessor.kt`
    (design §2.3, §3.4).
  - [ ] Register the `projectService` and the preprocessor in `src/main/resources/META-INF/plugin.xml`
    (design §7).
  - [ ] In `LuaTypes.kt`, switch `forFile` to `recordingFor` and change `churnDependencyFor`'s
    unpinned branch (design §2.4). Update both KDocs.
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/type/LuaSourceChangeTrackerTest.kt`
    (design §2.5) with TC 1 to TC 8, TC 14 (one method per fixture) and TC 15, using the
    requirements' fixture texts exactly.
- **Exit criteria**:
  - TC 1 to TC 8, TC 14 and TC 15 pass, and TC 9's tests pass unmodified.
  - Each mutant of requirements *Mutation checks* rows 1 to 11 turns its named test red: 1 (TC 1),
    2 (TC 2), 3 (TC 14 `gtab`), 4 (TC 14 `lclass0`), 5 in both forms (TC 14 `gfunc`, then
    `method`), 6 (TC 14 `mfield2`), 7 (TC 6), 8 (TC 5), 9 (TC 15), 10a and 10b (TC 7), and 11 in
    both forms (TC 8). Rows 3 to 6 are the per-index mutants. Run each mutant once, record the
    red test in the PR, and restore. A mutant that stays green is a failure of this phase.
  - The full `test --rerun --no-build-cache` suite has no new failures.
  - `ktlintCheck` passes.

### Phase 2: Acceptance runs [Must]
- **Goal**: evidence for MAINT-63-03 and MAINT-63-04, including TC 13's oracle.
- **Tasks**: run each step one at a time, each with `--rerun --no-build-cache`.
  - After every run, copy its output files on the builder VM, with `ssh builder cp`, to
    `/home/builder/maint-63-acceptance/<commit>-<run>/`, for example `base-sweep1/`. Copy on the
    VM because each sweep run deletes `build/snapshot-cost/`, and sync to the VM runs one way only.
    TC 12's `-PaloneDigest*` arguments are VM paths under that directory. Pull the directory back
    with `scp` for the PR record.
  - [ ] At the base commit: TC 11's command, then TC 10's sweep twice.
  - [ ] At the Phase 1 commit: the same three runs.
  - [ ] TC 12 at the base commit, then at the Phase 1 commit, with `-PaloneDigestBase` and
    `-PaloneDigestChange` set to the first sweep of each commit.
  - [ ] Compare as TC 10, TC 11, TC 12 and TC 13 specify. Record the four miss counts per bound,
    the members excluded by MAINT-63-03's all-four-sweeps rule, the candidate count (including
    "0 candidates"), the digest comparison, and each change sweep's `oracle compared=… rebuildTimeouts=…`
    line in the PR description.
  - [ ] Regenerate the requirements' *Files over target* table from the two change sweeps, by the
    rule stated above that table.
- **On slot overflow** (`testSlot00` fails with "candidates exceed 40 slots"): add `@Test` methods
  `testSlot40()` onward, up to the candidate count rounded up to the next multiple of ten. Raise
  `LuaAloneDigestTest`'s `SLOTS` constant to match. Re-run TC 12 at both commits. An
  overflow is not a failure.
- **On a failure of TC 10, TC 11, TC 12 or TC 13** (TC 12: a row differs, or is not `ok`, in
  either run; TC 13: any `oracle` check line):
  1. The requirement fails. Do not merge. Report the failing outputs to the supervisor: TC 10's
     four counts per bound, TC 11's two files, TC 12's differing rows, or TC 13's lines.
  2. Do not re-run any sweep, audit or alone run to get a passing result. Two sweeps per commit
     are the only allowance for variance.
- **Exit criteria**: TC 10 to TC 13 hold.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-63-01 | M | Phase 1 |
| MAINT-63-02 | M | Phase 1 |
| MAINT-63-03 | M | Phase 2 |
| MAINT-63-04 | M | Phase 0 (oracle), Phase 2 |

## Verification Tasks
- [ ] TC 1 to TC 8, TC 14, TC 15: `LuaSourceChangeTrackerTest`.
- [ ] TC 9: the existing tests it names, unmodified.
- [ ] TC 10 to TC 13: the Phase 2 runs.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: After-edit oracle in the sweep | blocked | Must |
| Phase 1: Per-source trackers and tests | blocked | Must |
| Phase 2: Acceptance runs | blocked | Must |
