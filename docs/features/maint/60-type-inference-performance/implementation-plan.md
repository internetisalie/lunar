---
id: "MAINT-60-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-60"
folders:
  - "[[features/maint/60-type-inference-performance/requirements|requirements]]"
---

# MAINT-60: Implementation Plan

One PR per phase. Every phase ends with the full unit suite green
(`builder-run libvirt "test --rerun --no-build-cache"`) and `ktlintCheck` green. Phase 1 also runs
MAINT-60-03's check before and after its change: `-PwithTypeAudit --tests '*FalseNegativeAuditTest.testLuacheckMember'`
(the `luacheck` member's own method, `LuaTypeFalseNegativeAuditTest.kt:50`), and `LuaCatsConformanceTest`,
their outputs compared byte for byte, plus the sweep digest comparison below (TC 4).

## Phases

### Phase 1: The sweep and its verdict [Must]
- **Goal**: MAINT-60-05's tool, with the verdict of MAINT-60-01/-02/-06/-07.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.lang.psi.types.LuaSnapshotBuildMeter`, not yet called from `buildSnapshot` — design §2.2
  - [x] Create `net.internetisalie.lunar.corpus.SnapshotCostVerdict` (`SweepRow`, `Attribution`, `selectedFiles`, `sweepErrors`, `coverage`, `runTimeFailures`, `rankedLines`, `releaseLines`, `attribute`) — design §3.1–§3.3, §4
  - [x] Create `SnapshotCostVerdictTest` (routine, no fixture) with design §8's V, P, T, C, A and K cases — TC 1, 2, 3, 6, 7
  - [x] Create `net.internetisalie.lunar.corpus.LuaSnapshotCostSweepTest` — design §2.1, §3.1, §3.4
  - [x] Add the `withSnapshotSweep` block to `build.gradle.kts` — design §7
  - [x] Digest baseline (MAINT-60-03): run the sweep before the wrap below; on the builder, copy
        `build/snapshot-cost/sweep.jsonl` to `build/snapshot-cost-before.jsonl` (outside the directory
        the next sweep's `doFirst` deletes)
  - [x] Wrap `LuaTypesVisitor.buildSnapshot(file)` in `LuaSnapshotBuildMeter.measure` — design §2.2
  - [x] Digest comparison (MAINT-60-03), after the exit sweep run, on the builder: the command below
        prints the `member file` of each file read `ok` and not `prebuilt` in both runs whose
        `digest` differs. Empty output passes; each printed file is recorded in the PR as a
        correction with its cause.
        ```bash
        python3 -I -c 'import json,sys
        rows=lambda p:{(r["member"],r["file"]):r for r in map(json.loads,open(p))}
        a,b=rows(sys.argv[1]),rows(sys.argv[2])
        for k in sorted(a.keys()&b.keys()):
            x,y=a[k],b[k]
            if x["coldStatus"]==y["coldStatus"]=="ok" and not x["prebuilt"] and not y["prebuilt"] and x["digest"]!=y["digest"]: print(*k)' build/snapshot-cost-before.jsonl build/snapshot-cost/sweep.jsonl
        ```
  - [x] Add a "Snapshot cost sweep" paragraph to `tooling/corpus/README.md`: the command, the outputs, and that `release READY` in `verdict.txt` at the release candidate's commit is v0.21.21's release-ready signal (MAINT-60-07)
- **Exit criteria**: `SnapshotCostVerdictTest` green and each case red under its design §8 mutation;
  `builder-run libvirt "test --rerun --no-build-cache -PwithSnapshotSweep --tests '*SnapshotCostSweep*'"`
  writes `sweep.jsonl`, `ranked.tsv` and `verdict.txt`, reports no coverage violation and no
  `sweep took` failure (TC 6), and asserts tool health only; a routine
  `test` run does not run it. MAINT-60-03's before/after check passes: audit, conformance and the
  digest comparison.

### Phase 2: Inference workloads [Must]
- **Goal**: MAINT-60-04.
- **Tasks**:
  - [x] Add `--only` to `tooling/corpus/fetch-corpus.py` and `tooling/corpus/test_fetch_corpus_only.py` — design §2.4
  - [x] Add `equalPasses` to `WorkloadChecks` and `ROOT_COUNT_KEYS` and `passDisagreements` to `PerfRecordChecks.kt`, with the D cases in `PerfRecordChecksTest` — design §2.3, §8
  - [x] Add `inferenceWorkload` and the design §2.3 workloads to `LuaPerfRecordWorkloadsTest`, and the equal-passes failure to `recordEveryWorkload` — design §2.3
  - [x] Extend the `build` job's fixtures step in `.gitea/workflows/build-plugin.yml` — design §7
- **Exit criteria**: `python3 -I -m unittest tooling/corpus/test_fetch_corpus_only.py` green;
  each D case red under its mutation; `-PwithPerfRecords --tests '*PerfRecord*'` writes
  `build/perf-records/infer-*.json` and passes (TC 5); the same run with the prime removed from
  `inferenceWorkload` fails with `counts differ across passes` on `infer-luals-compiler`. If it stays
  green, the prime is not needed under this harness: delete it from design §2.3 and the code, record
  the run as a DR row, and prove the wiring instead: snapshot `LuaPerfRecordWorkloadsTest.kt` with the
  `temporary-edits` skill, change `recordEveryWorkload` so the recorded pass of `infer-luals-compiler`
  has its `write` count raised by 1
  (`recorded.mapIndexed { i, pass -> if (workloads[i].spec.name == "infer-luals-compiler") pass.copy(rawCounts = pass.rawCounts + ("write" to pass.rawCounts.getValue("write") + 1)) else pass }`
  in place of `recorded` in the failures computation), run the same command, see it fail with
  `infer-luals-compiler: counts differ across passes: write`, then restore the file from the snapshot
  (never `git checkout`/`git restore`) and confirm `git diff` shows only the phase's own edits. The
  CI run on the PR shows the fixtures step green.

### Phase 3: Name every file over target [Must]
- **Goal**: MAINT-60-02; the verdict's assertion green.
- **Tasks**:
  - [x] Run the sweep three times on the Phase 2 commit; keep each `ranked.tsv` (DR-13)
  - [x] Mint the next free `MAINT-` number for each item design §3.3's table marks new; set
        `OWN_BUILD_ITEM`, `REACH_ITEM` and `EDIT_CASCADE_ITEM` to the IDs of the items that table
        gives them; create each item as `docs/features/maint/<NN>-<slug>/requirements.md`
        (`status: todo`) with its cause, its DR evidence and its file table (member, file, bound,
        value, runs missed) from the union of the runs' `ranked.tsv` lines; add a roadmap row each
  - [x] Rewrite [[BUG-96]]'s and [[BUG-97]]'s bug reports to their causes in design §3.3, with their
        file tables from the same union; update their roadmap rows
  - [x] Remove the listing machinery: `exceptions.tsv`, `parseExceptions`, `staleExceptions`, the
        listing and roadmap-row checks, `parseRoadmapIds`; `verdict.txt` names each miss with its item
        (DR-13)
  - [x] Run the sweep once more: assertion passes, `release HOLD`
- **Exit criteria**: the routine `*SnapshotCostVerdict*` tests are green; one sweep run's assertion
  passes (tool health only: TC 6); `verdict.txt` reads `release HOLD` with the measured `misses` and
  p99 lines, which the items' table in the PR description quotes.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-60-01 | M | Phase 1 (verdict), Phase 3 (item tables); the target itself by the cause items |
| MAINT-60-02 | M | Phase 1 (verdict, attribution), Phase 3 |
| MAINT-60-03 | M | Phase 1's exit check (the only phase that edits `src/main`) |
| MAINT-60-04 | M | Phase 2 |
| MAINT-60-05 | M | Phase 1 |
| MAINT-60-06 | M | Phase 1 (verdict), Phase 3 (item tables); the target itself by the cause items |
| MAINT-60-07 | M | Phase 1 (`releaseLines`); read by the supervisor before tagging v0.21.21 |

## Verification Tasks
- [ ] `SnapshotCostVerdictTest` — TC 1, 2, 3, 6, 7
- [ ] `PerfRecordChecksTest`'s D cases and the workloads run — TC 5
- [ ] The sweep runs of Phases 1 and 3 — TC 1, 2, 3, 6
- [ ] `luacheck.json`, `results.json` and the sweep digests compared before and after Phase 1 — TC 4

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: The sweep and its verdict | done | Must |
| Phase 2: Inference workloads | done | Must |
| Phase 3: Name every file over target | done | Must |
