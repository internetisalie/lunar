---
id: "MAINT-65"
title: "65: Cycle-Aware Inference"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-65: Cycle-Aware Inference

## Overview
A snapshot built while a module it reads is still being resolved on the same thread is provisional:
its answer depends on build order, so it cannot be invalidated per file and is rebuilt on every edit
anywhere. This blocks [[MAINT-63]]'s per-file edit invalidation. Parent:
[[features/maint/requirements|MAINT]]. Measurement feature: [[MAINT-60]].

## Measured Cause
MAINT-63 risks DR-08: under a sound provisional rule, only the module guard
(`LuaTypeManagerImpl.resolvingModules`) fired, and replay of its guarded module answers made
provisional builds outnumber per-file builds about 8 to 1 in the edit phase.

## Scope
Holds no file itself. Done when per-file edit invalidation becomes sound and effective.

Direction (not designed): build an import cycle's files as one unit, so that no snapshot is
provisional.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-65-01 | **Per-file edit invalidation beats base** | M | Not Implemented | With this item applied, a re-probe of MAINT-63's svca variant under the r10 provisional rule (MAINT-63 risks, variants table) on DR-08's eight-member subset misses fewer of MAINT-63's 338 subset table passes than base does in the same run, and every soundness re-read rebuilds. |
| MAINT-65-02 | **Findings unchanged** | M | Not Implemented | The MAINT-54 audit's `luacheck` member and the MAINT-55 conformance results are byte-identical before and after the change, and every swept file's cold digest is unchanged in the MAINT-60 sweep. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-65-01 | MAINT-63 DR-08's probe setup (eight members, 699 files) at this item's commit, run once with base and once with svca under r10 | count table passes whose 1 s after-edit read is at 250 ms or more or did not finish | svca count < base count; soundness re-reads all rebuilt |
| 2 | MAINT-65-02 | `test -PwithTypeAudit -PrecordConformance --tests '*FalseNegativeAudit*testLuacheckMember' --tests '*LuaCatsConformanceTest*'` and `test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'`, before and after | compare `build/type-audit/luacheck.json`, `build/conformance/results.json` and each row's `digest` in `build/snapshot-cost/sweep.jsonl` | byte-identical; every `digest` equal |
