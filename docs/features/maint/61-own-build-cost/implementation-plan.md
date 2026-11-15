---
id: "MAINT-61-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-61"
folders:
  - "[[features/maint/61-own-build-cost/requirements|requirements]]"
---

# MAINT-61: Implementation Plan

Every builder run uses `--rerun --no-build-cache`. The **base commit** is this item's branch point
on `main`. The **change commit** is Phase 1's. *Reading a sweep*, *Oracle over budget*, *Re-runs*,
*Samples*, *Slot overflow* and *Check lines* are as in the
[MAINT-63 implementation plan](../63-edit-cascade-cost/implementation-plan.md). Copied outputs go to
`/home/builder/maint-61-acceptance/<commit>-<run>/`. MAINT-63's *Check lines* row for
`oracle: edit digest` reads here as: TC 10 fails, and the item stops and reports.

## Phases

### Phase 0: Lazy-facts premise [Must]
- **Goal**: settle DR-09 before the code depends on it.
- **Tasks**:
  - [ ] As a temporary edit (`temporary-edits` skill) at the base commit, read `revision` before and
    after the `VisitFacts` computation in `checkNodePairs`, and count the computations where it
    changed. Run the twelve members of risks *Results*, then restore.
  - [ ] Record the count as DR-09's outcome in `risks-and-gaps.md`.
- **Exit criteria**: DR-09 is `done`. If the count is above 0, take the branch MAINT-61-03 states:
  design §3.2 is not implemented (Phase 1 computes the facts at visit start, as today, and
  `factlessVisitCount` is not added), TC 5 and mutation 7 are removed, and the requirement's text is
  replaced by the branch text. Record this in DR-09 and report it.

### Phase 1: Delta scan, edge counter and lazy facts [Must]
- **Tasks**:
  - [ ] `LuaTypeNodes.kt`: design §2.1, and the `upSet`/`downSet` hooks (§2.2).
  - [ ] `LuaTypeGraph.kt`: design §2.2 to §2.4 and §3.
  - [ ] `LuaTypes.kt`: design §2.5.
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaPairScanTest.kt` with TC 1 to
    TC 5 (design §4).
  - [ ] Design §2.6: `build.gradle.kts`, `LuaSnapshotCostSweepTest`, `SnapshotCostVerdict`, and
    `testPairScanChecks` in `SnapshotCostVerdictTest`.
- **Exit criteria**:
  - TC 1 to TC 6 and `testPairScanChecks` pass.
  - TC 7: `test -PwithSnapshotSweep -PwithPairScanDifferential --tests '*LuaSnapshotCostSweepTest.<method>' --tests '*LuaSnapshotCostSweepTest.testZzVerdict'`,
    with `<method>` set to druid's and then blinkcmp's `testM…` method. Neither `verdict.txt` holds
    a `pair scan:` line. Coverage lines for other members are expected, as in MAINT-63's one-member
    runs.
  - Every mutation of requirements *Mutation checks* (rows 1 to 8) turns its named check red. Run each
    once, record the red check in the PR, and restore. Mutations 1 and 2 use TC 7's druid run. A
    mutant that stays green fails this phase.
  - The full `test` suite has no new failures; `ktlintCheck` passes.

### Phase 2: Acceptance runs [Must]
- **Tasks**, one run at a time:
  - [ ] At the base commit: TC 8's command, then two TC 11 sweeps.
  - [ ] At the change commit: the same runs, then TC 10's oracle run.
  - [ ] TC 9 at the base commit, then at the change commit, with `-PaloneDigestBase` and
    `-PaloneDigestChange` set to the copied `sweep.jsonl` of each commit's first sweep.
  - [ ] Record in the PR: every sweep's miss count per bound, the margin per bound (MAINT-61-04), MAINT-61-05's four sums, the candidate
    count, the digest comparison, the oracle's `oracle compared=` line, and every sweep's samples.
  - [ ] Regenerate [files-over-target.md](files-over-target.md) from the two change sweeps, by the
    rule above its table.
- **On a failure of TC 8, TC 9, TC 10 or MAINT-61-04's rule**: do not merge, and report the failing
  outputs to the supervisor. A MAINT-61-05 failure is recorded in the PR and does not block.
- **Exit criteria**: TC 8 to TC 11 hold.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-61-01 | M | Phase 1 |
| MAINT-61-02 | M | Phase 2 |
| MAINT-61-03 | M | Phase 0, Phase 1 |
| MAINT-61-04 | M | Phase 2 |
| MAINT-61-05 | S | Phase 2 |

## Verification Tasks
- [ ] TC 1 to TC 5 (`LuaPairScanTest`); TC 6's tests, unmodified; TC 7 (Phase 1 runs).
- [ ] TC 8 to TC 11 (Phase 2 runs).
