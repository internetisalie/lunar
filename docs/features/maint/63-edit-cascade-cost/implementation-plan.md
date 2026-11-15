---
id: "MAINT-63-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-63"
folders:
  - "[[features/maint/63-edit-cascade-cost/requirements|requirements]]"
---

# MAINT-63: Implementation Plan

Every builder run uses `--rerun --no-build-cache`. The base commit for TC 10 to TC 13 is Phase 0's
commit, on this item's branch point on `main`.

**Reading a sweep.** A sweep's result is its `build/snapshot-cost/verdict.txt` (design §2.6): its
check lines start `check: `, and in an oracle run `oracle compared=` is the counts line. Never read it from the
console. Each run deletes `build/snapshot-cost/` and sync to the VM is one-way, so after every run
copy the directory on the VM (`ssh builder cp -r`) to `/home/builder/maint-63-acceptance/<commit>-<run>/`
(for example `base-sweep1/`), and pull it back with `scp` for the PR record.

**The oracle run** (TC 13) is `test -PwithSnapshotSweep -PwithSnapshotOracle --tests '*SnapshotCostSweep*'`;
a timing sweep (TC 10) is the same command without `-PwithSnapshotOracle` (design §2.6).

**A one-member oracle run** is
`test -PwithSnapshotSweep -PwithSnapshotOracle --tests '*LuaSnapshotCostSweepTest.<method>' --tests '*LuaSnapshotCostSweepTest.testZzVerdict'`,
where `<method>` is the member's `testM…` method in `LuaSnapshotCostSweepTest`. Its `verdict.txt`
holds a coverage line `check: <member>/<file>: no row` for every selected file of every other
member; those are expected. Its result is every other check line in it, and its `oracle compared=`
line. **The tntcrud run** (mutants 15 and 18) is the one-member oracle run of
`testM24Tntcrud`.

**Oracle over budget.** When an oracle run's `verdict.txt` holds `oracle rebuild pass over budget`,
every other check line in it stands: an `oracle: edit digest` line it holds fails TC 13 as the
*Check lines* table says, and is not replaced. For the rest, one one-member oracle run follows per
`testM…` method except `testM00aLuals`, in method order. TC 13 holds only when no run holds an
`oracle` line other than the split run's `oracle rebuild pass over budget`, or a `sweep took`, `sweep error` or coverage line other than a one-member run's expected
lines. A member whose own run still holds `oracle rebuild pass over budget` leaves TC 13 unestablished:
the item stops and reports to the supervisor; it does not pass.

**Re-runs.** No sweep, oracle, audit or alone-digest run is re-run to get a passing result. The
exceptions: a TC 10 sweep with a dirty sample (Phase 2) is discarded and re-run, an oracle run over
budget is split as above, and TC 12 is re-run after a slot overflow (Phase 2).

## Phases

### Phase 0: After-edit oracle in the sweep [Must]
- **Goal**: the oracle run fails when a settled after-edit read differs from a full rebuild, or when the
  rebuild pass did not run as designed (MAINT-63-04 (c)).
- **Tasks**:
  - [ ] Design §2.6 in `src/test/kotlin/net/internetisalie/lunar/corpus/`: `SnapshotCostVerdict.kt`
    (`SweepRow` fields, constants, `oracleChecks` and its parts, `oracleCompared`, `oracleCounts`),
    `LuaSnapshotCostSweepTest.kt` (visibility, `REBUILD_MS`, `budgetLeft()`, `rebuildPass`,
    `forceFullRebuild`, `FileResult.rebuild`, `toRow`, `toJson`, `ORACLE_ON`, `testZzVerdict`), O1
    to O8 in `SnapshotCostVerdictTest`, and the oracle switch in `build.gradle.kts`.
- **Exit criteria**:
  - O1 to O8 pass, and the existing `SnapshotCostVerdictTest` methods pass unmodified.
  - Every mutant in design §2.6's Tests table turns its test red, each form separately.
  - **Mutation 18**: with `rebuildPass` reading without `forceFullRebuild`, the tntcrud run's
    `verdict.txt` holds at least one `oracle: rebuild read built nothing` line. Unmutated, it holds
    no `oracle` line for a `tntcrud/` file; if it does, the premise behind `oracleUnforced` is false:
    stop and report.
  - **Oracle-run cost**: one oracle run at this phase's commit. Record its `oracle compared=` line in
    the PR. If its rebuild pass is over budget, split it as *Oracle over budget* says and record each
    run's line. An `oracle: edit digest` line here is a mismatch in base: the comparison is void, a bug
    is filed against base, and the item stops. Any other `oracle` line is handled by the *Check lines*
    table's harness row; a `sweep took`, `sweep error` or coverage line stops the phase and is reported.
  - Run each mutant once, record the result in the PR, and restore. A mutant that stays green is a
    failure of this phase.
  - The full `test` suite has no new failures; `ktlintCheck` passes.

### Phase 1: Per-source trackers and tests [Must]
- **Goal**: unpinned snapshots depend on their recorded sources.
- **Tasks**:
  - [ ] `LuaTypeSourceRecorder`: design §2.4.
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaSourceChangeTracker.kt`
    holding `LuaSourceChangeTracker` (design §2.1, §3.1, §3.3), `LuaSourceSetTracker` (§2.2, §3.3)
    and `LuaStampTotal` (§2.2a, §3.3).
  - [ ] Create `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaSourceChangePreprocessor.kt`
    (design §2.3, §3.4).
  - [ ] Register both in `src/main/resources/META-INF/plugin.xml` (design §6).
  - [ ] `LuaTypes.kt`: `forFile` uses `recordingFor`; `churnDependencyFor`'s unpinned branch and
    both KDocs (design §2.4).
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/type/LuaSourceChangeTrackerTest.kt`
    (design §2.5) with TC 1 to TC 8, TC 14 (one method per fixture), TC 15 and TC 16, using the
    requirements' fixture texts exactly.
- **Exit criteria**:
  - TC 1 to TC 8 and TC 14 to TC 16 pass, and TC 9's tests pass unmodified.
  - Every row of requirements *Mutation checks* numbered 1 to 12 or 16, except 10c, turns its named
    test red, each form separately: 5 in every form (`gfunc`, `method`, `localfn`, `newfile`), and
    11, 12 and 16 in each form. Run each once, record the red test in the PR, and restore. A mutant that
    stays green is a failure of this phase.
  - The full `test` suite has no new failures; `ktlintCheck` passes.

### Phase 2: Acceptance runs [Must]
- **Goal**: evidence for MAINT-63-03 and MAINT-63-04, including TC 13's oracle.
- **Tasks**, one run at a time:
  - [ ] **Mutation 15**: at the Phase 1 commit, revert BUG-102's resolver rule
    (`LuaModuleFileResolver.kt`, the `virtualFile.name != "init.lua"` term), run the tntcrud run,
    and restore. Its `verdict.txt` must hold at least one `oracle: edit digest` line for a
    `tntcrud/` file. If it holds none, the oracle is not wired to the engine: stop and report.
  - [ ] At the base commit: TC 11's command, then two TC 10 sweeps.
  - [ ] At the Phase 1 commit: the same runs, then TC 13's oracle run.
  - [ ] TC 12 at the base commit, then at the Phase 1 commit, with `-PaloneDigestBase` and
    `-PaloneDigestChange` set to the copied `sweep.jsonl` of each commit's first sweep.
  - [ ] Compare as TC 10 to TC 13 specify. Record in the PR: every sweep's miss count per bound, the
    candidate count (including "0 candidates"), the digest comparison, the oracle run's
    `oracle compared=` line (or each one-member run's), and the samples of every TC 10 sweep.
  - [ ] Regenerate [files-over-target.md](files-over-target.md) from the two change sweeps, by the
    rule stated above its table.
- **Samples.** The builder is shared with `/home/builder/builds/sim-app`. During every TC 10 sweep,
  sample once a minute: the number of processes whose cwd is under `/home/builder/builds/`; and, over
  every `java` process whose cwd is neither exactly `/home/builder/lunar` nor under
  `/opt/cache/gradle/daemon/` (lunar's test worker and Gradle daemon), their sorted pid list and
  summed CPU seconds. A sample is dirty when that number is not 0, or the pid list differs from the
  previous sample's, or the sum is higher than the previous sample's. A sweep with a dirty sample is
  discarded and re-run at the same commit. The rule detects another project's build through its client and
  test-worker cwds, and counts every Kotlin daemon, lunar's included, so it errs towards dirty. A
  daemon running under `/opt/cache/gradle/daemon/` is exempt whatever its project, and a non-JVM
  workload is not detected; both are accepted limits.
- **Slot overflow** (`testSlot00` fails with "candidates exceed 40 slots"): add `@Test` methods
  `testSlot40()` onward, up to the candidate count rounded up to the next multiple of ten, raise
  `LuaAloneDigestTest.SLOTS` to match, and re-run TC 12 at both commits. It is not a failure.
- **Check lines.** Every check line in a `verdict.txt` is handled by its kind:

  | Check line | Handling |
  |---|---|
  | `oracle: edit digest` | TC 13 fails; MAINT-63 is blocked on MAINT-66 (risks Risk 1.1). The line can equally be a V3 staleness defect (Risk 1.2); the outcome is the same, and the report names the file |
  | `oracle rebuild pass over budget` | *Oracle over budget*, above |
  | `oracle: rebuild read built nothing`, `oracle: rebuild read <status>`, `oracle compared no rows` | a harness or wiring defect: stop, fix the harness in Phase 0 (a new base commit), and restart Phase 2 |
  | `sweep took`, `sweep error`, and coverage (`no row`, `no selected files`, `row for an unselected file`, `duplicate row`) other than a one-member run's expected lines | the requirement of that run (TC 10 or TC 13) fails (below) |

- **On a failure of TC 10, TC 11, TC 12 or TC 13** (TC 12: a compared row differs, or is not `ok`,
  in either run): do not merge, and report the failing outputs to the supervisor (TC 10's counts
  per bound, TC 11's two files, TC 12's differing rows, or the check lines).
- **Exit criteria**: mutation 15's `verdict.txt` held an `oracle: edit digest` line, and TC 10 to
  TC 13 hold.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-63-01 | M | Phase 1 |
| MAINT-63-02 | M | Phase 1 |
| MAINT-63-03 | M | Phase 2 |
| MAINT-63-04 | M | Phase 0 (oracle), Phase 2 |

## Verification Tasks
- [ ] TC 1 to TC 8 and TC 14 to TC 16 (`LuaSourceChangeTrackerTest`); TC 9's tests, unmodified.
- [ ] O1 to O8 (`SnapshotCostVerdictTest`); TC 10 to TC 13 (the Phase 2 runs).
