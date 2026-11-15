---
id: "MAINT-61"
title: "61: Own-Build Cost of Slow Files"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-61: Own-Build Cost of Slow Files

## Overview
Some files have their own snapshot build at 250 ms or more, and the time is mostly spent in
`LuaTypeGraph.checkTypes` (risks DR-01). Every fixed-point pass after the first scans each variable's
whole (value, use) product again, and recomputes the visit's facts over its up-set, even when the
pass admits no new pair. This item scans only the pairs an earlier visit has not seen, and decides
convergence from an edge counter. Neither changes which pairs the checker admits or in what
order (DR-06). The visit facts stay as they are (risks Gap 2.4). DR-08 measured a probe close to this design (Risk 1.2). Filed by MAINT-60 Phase 3. MAINT-61 keeps the checker-cost passes of the MAINT-63 change
sweeps, the largest own-build shape. Fan-out passes moved to [[MAINT-67]] and visitor-cost passes to
[[MAINT-68]], by the rule in [files-over-target.md](files-over-target.md). [files-over-target.md](files-over-target.md) lists every pass (MAINT-60-02).
v0.21.21 is held on this item. Parent: [[features/maint/requirements|MAINT]].
Measurement feature: [[MAINT-60]].

## Scope
- In scope: the pair scan and the visit facts in `LuaTypeGraph.checkNodePairs`, the convergence test
  of `runFixedPointPass`, and index access on `OrderedSet`.
- Out of scope: the walks and pair volume (MAINT-64, risks Gap 2.1); edit fan-out (MAINT-67);
  visitor cost (MAINT-68); fallo `init.lua` (BUG-96); luals `script/parser/compile.lua` (BUG-97);
  the 5 s `checkTypes` cutoff (BUG-104); the verdict's attribution (risks Gap 2.2).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-61-01 | **Same pairs, same order** | M | Full | After a variable's visit, every (value, use) pair of its up-set and down-set, up to the sizes at the visit's start, is in `checkedPairs`. Admitted pairs are checked in the order the unmodified loop checks them, and convergence is decided as before (design §3). |
| MAINT-61-02 | **Findings unchanged** | M | Not Implemented | MAINT-63-04 (a), (b) and (c), applied to this item's base and change commits. |
| MAINT-61-03 | **Less checker work** | M | Full | A revisit scans only the pairs with a value or a use added since the variable's last visit (design §3.1) |
| MAINT-61-04 | **No more misses; table regenerated** | M | Not Implemented | For each bound, over this item's two base and two change sweeps, the higher change count is no more than the margin above the lower base count. The margin per bound is the larger of MAINT-63 risks DR-25's margin and the spread of this item's two base sweeps (risks, *Premises*). [files-over-target.md](files-over-target.md) is regenerated from the change sweeps. |
| MAINT-61-05 | **Faster table passes** | S | Not Implemented | Over the passes in [files-over-target.md](files-over-target.md) whose item is MAINT-61, the higher change sweep's summed read time is below the lower base sweep's. A pass that was not `ok` counts as 1 000 ms for `edit-250` and 60 000 ms for `cold-250`. |

## Test Cases
The base and change commits are defined in the implementation plan. TC 1 to TC 4 and TC 6 are unit tests
(design §4).

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-61-01 | `PairScanMark(up = 2, down = 1)`, `upSize = 3`, `downSize = 2` | enumerate `(d, v)` for `d` in `0 until 2` and `v` in `firstValueIndex(d, mark) until 3` | `[(0,2), (1,0), (1,1), (1,2)]`. With `mark = null`: all six pairs, in order `(0,0)…(1,2)` |
| 2 | MAINT-61-01 | each of design §4's six texts, in order | `LuaTypePairScanDifferential.start()`, read the snapshot, `stop()` | the snapshot's `scanComparisonCount()` is the text's full-scan pair count from DR-07 (`callback` 46, `callback2` 232, `method` 796, `fwd` 520, `chain` 506, `hof` 1 474), and `misses() == 0`. Second test: after `start()`, `observe(examined = false)` once makes `misses() == 1` |
| 3 | MAINT-61-03 | design §4's `method` text | read the snapshot, then `pairAttemptCount()` | `407` (DR-07). Unmodified loop: `796` |
| 4 | MAINT-61-01 | each of design §4's texts | read the snapshot | `edgeAddCount() == closureSize()`, and `scanMarksRetained() == false` |
| 6 | MAINT-61-01 | the existing `LuaTypePairWriteDifferentialTest`, `LuaTypeGraphRootResolutionBudgetTest`, `Type14InstantiationBudgetTest`, `LuaUnionDistributionTest` and `LuaMemberDemandHubTest` | run them, unmodified | all pass |
| 7 | MAINT-61-01 | a one-member sweep of druid, then blinkcmp, with `-PwithPairScanDifferential` (implementation plan) | read `verdict.txt` | no `pair scan:` check line |
| 8 | MAINT-61-02 | MAINT-63 TC 11's command, at base and change | compare as MAINT-63 TC 11 does, leaving out `coverage.unresolvedCallee` and `coverage.noTag` (BUG-106) | identical |
| 9 | MAINT-61-02 | the first base sweep and the first change sweep of TC 11 below | MAINT-63 TC 12's command, at base and change | every non-`luals` row `ok` in both runs, with equal `digest`. Zero such rows pass, and the PR records "0 candidates" |
| 10 | MAINT-61-02 | MAINT-63 TC 13's oracle run, at the change commit | read `verdict.txt` | no check line. Over budget, MAINT-63 implementation plan *Oracle over budget* applies |
| 11 | MAINT-61-04, -05 | two timing sweeps at base and two at change (`test -PwithSnapshotSweep --rerun --no-build-cache --tests '*SnapshotCostSweep*'`), each sampled by MAINT-63 implementation plan Phase 2 *Samples* | count misses per bound by `SnapshotCostVerdict`'s rule; sum the table's MAINT-61 passes | MAINT-61-04's rule holds and no `verdict.txt` holds a check line. MAINT-61-05: higher change sum < lower base sum |

### Mutation checks
TC 1 to TC 4 and TC 7 are falsifiers. TC 6 and TC 8 to TC 10 are regression checks, and TC 11 is a
performance check.

| # | Mutation | Why it turns red |
|---|----------|------------------|
| 1 | `firstValueIndex` returns `mark.up` for every `d` (M1) | TC 1: `(1,0)`, `(1,1)` missing. TC 7: druid `component.lua` drops 840 unexamined pairs (DR-06) |
| 2 | the loop starts `d` at `mark.down` (M2) | TC 7: 575 pairs on `component.lua` (DR-06). TC 1 stays green, because it enumerates through the helper |
| 3 | `firstValueIndex` uses `d > mark.down` | TC 1: `(1,0)`, `(1,1)` missing |
| 4 | the mark is never stored | TC 3: `796` |
| 5 | `onEdgeAdded` does not increment `edgeAdds` | TC 4: `0 != closureSize()` |
| 6 | the differential's post-visit check is removed | TC 2's first test: `scanComparisonCount() == 0` |
| 7 | `scanMarks` is cleared, not dropped, when `checkTypes` returns | TC 4: `scanMarksRetained() == true` |
| 8 | none for TC 6, TC 8, TC 9, TC 10 | regression checks |
