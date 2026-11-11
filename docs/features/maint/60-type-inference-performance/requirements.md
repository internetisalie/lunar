---
id: "MAINT-60"
title: "60: Type-Inference Performance"
type: "feature"
status: "done"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-60: Type-Inference Performance

## Overview
A file's type snapshot is rebuilt after every edit, so its build time is the delay before highlighting
and inspections catch up. Across the type corpus most files build in milliseconds, but a tail does
not: fallo's `init.lua` ([[BUG-96]]) and any edit in luals's `script/vm` ([[BUG-97]]) take a minute
or more. No targeted engine change reaches the targets below: every candidate was built and measured
([risks-and-gaps.md](risks-and-gaps.md), DR-05, DR-06). This feature therefore makes the targets
measurable, gives every file that misses one a tracked item for its measured cause, and records
inference in the per-commit performance records. It changes nothing the engine computes. The
v0.21.21 release waits on the targets themselves (MAINT-60-07), so the cause items filed by Phase 3
are that release's work.
Parent:
[[features/maint/requirements|MAINT]].

## Scope

### In Scope
- A committed, repeatable per-file sweep of cold and after-edit snapshot cost over the type corpus,
  with a verdict against the targets and a release signal that names every miss.
- Roadmap items, one per measured cause, naming every file that misses a 250 ms bound.
- Type-inference workloads in the MAINT-45 per-commit performance records.

### Out of Scope
Each of these is a filed item (design §3.3), not part of this feature:
- A redesign of the checker's (value, use) pair model, and incremental (sub-file) re-inference.
- Rebuilding, after an edit, only the snapshots whose inputs changed.
- Building a dependency's snapshot without running the full checker on it.
- The walk flattenings that changed findings when measured (DR-06).

## Functional Requirements

Every time below is measured on the libvirt builder by the sweep of MAINT-60-05, whose setup is
MAINT-56 DR-16's. *Cold* is a file's first read in the sweep, so it includes building every snapshot
the file reaches that is not yet built. *After an edit* is the same file read again after `"\n"` is
appended to it and committed. A *p99* is the nearest-rank 99th percentile over every swept file,
a file not measured counting as over it (design §3.2); it is one verdict line.

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-60-01 | **Cold target** | M | Every type-corpus file's cold read takes under 250 ms, and the cold p99 is under 100 ms. |
| MAINT-60-02 | **Every miss is named** | M | Every pass that missed a 250 ms bound of MAINT-60-01 or MAINT-60-06 in the Phase 3 sweeps is named in the file table of the roadmap item for its measured cause (design §3.3), with its value. The verdict's release lines name every miss, with that item, at release time. The targets are not loosened. |
| MAINT-60-03 | **Findings unchanged** | M | The MAINT-54 audit's `luacheck` member, the MAINT-55 conformance results and the sweep's per-file digests (over files read `ok` and not already built in both runs) are identical before and after this feature's change to `src/main`, or each difference is recorded as a correction with its cause. |
| MAINT-60-04 | **Inference workloads recorded** | M | MAINT-45's performance records gain type-inference workloads on luals `script/vm/compiler.lua`, a p99-cost file and a median-cost file, and the records test fails, whatever `tooling/perf/gate-mode` says, when any of these workloads' root counts (`write`, `read`, `demand`) differ between its two passes. fallo `init.lua`'s workload is added by the [[BUG-96]] fix. |
| MAINT-60-05 | **Sweep is a tool** | M | The per-file sweep is a committed, opt-in test that writes a row for every selected file of every `type-corpus.json` member and a ranked table, and fails, and its release signal reads `release HOLD`, when it runs 45 minutes or more from its first test method to its verdict. A file it does not measure (run budget spent, or its member stopped by an earlier file's timeout) has a row with that status, counts as over both 250 ms bounds, and is filed under the item that blocks measuring it. |
| MAINT-60-06 | **Edit target** | M | Every type-corpus file's after-edit read takes under 250 ms, luals `script/vm` included, and the after-edit p99 is under 100 ms. |
| MAINT-60-07 | **Release-ready signal** | M | The sweep's verdict prints `release READY` only when no swept pass misses its bound (status not `ok`, or 250 ms or more), the cold and after-edit p99 lines pass, and the tool-health checks (coverage, sweep errors, the 45-minute check) pass; otherwise `release HOLD`, followed by one `miss:` line per missing pass with its item. v0.21.21 is release-ready when that line reads `release READY` in the sweep run on the libvirt builder at the release candidate's commit, made by the supervisor before tagging. |

## Implementation Status

| ID | Status | Note |
|----|--------|------|
| MAINT-60-01 | Partial | The cold target is measured and every miss is named in its cause item's file table; the files themselves are not yet under 250 ms. |
| MAINT-60-02 | Full | Every miss of the three Phase 3 sweeps is in the file table of MAINT-61, MAINT-62, MAINT-63, BUG-96 or BUG-97, each with a roadmap row. |
| MAINT-60-03 | Full | Phase 1's before/after check. |
| MAINT-60-04 | Full | Phase 2. |
| MAINT-60-05 | Full | Phase 1. |
| MAINT-60-06 | Partial | The edit target is measured and every miss is named in its cause item's file table; the files themselves are not yet under 250 ms. |
| MAINT-60-07 | Full | `verdict.txt` reads `release HOLD` until the cause items land. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-60-01 | sweep rows | the verdict | a cold miss counts in `misses` and gets a `miss:` line; the cold p99 line is computed over all rows, unmeasured rows as over |
| 2 | MAINT-60-06 | sweep rows | the verdict | an after-edit miss counts in `misses` and gets a `miss:` line; the after-edit p99 line is computed from after-edit times |
| 3 | MAINT-60-02 | sweep rows | `attribute`; `ranked.tsv`; the release lines | each miss's item follows design §3.3 and is on its `miss:` line; in-band passes appear in `ranked.tsv` but are not named |
| 4 | MAINT-60-03 | the MAINT-54 audit (`luacheck`), `LuaCatsConformanceTest` and the sweep | run before and after the `src/main` change | `luacheck.json` and `results.json` byte-identical and the digests equal, or each difference recorded |
| 5 | MAINT-60-04 | two passes of a workload | the records test | a root count that differs between passes fails it in every gate mode; a `stub:*` difference does not; the design §2.3 workloads' records are written |
| 6 | MAINT-60-05 | the type corpus fetched | run the sweep | a row for every selected file of every member, and the run under 45 minutes, both asserted; a slow pass is reported, not asserted |
| 7 | MAINT-60-07 | sweep rows and the tool-health check count | the verdict | `release READY` only with no missing pass, both p99 lines passing and no health check failing; otherwise `release HOLD` and a `miss:` line per missing pass |

Design §8 maps each case to its test and the mutation that turns it red.

## Dependencies
- [[BUG-96]], [[BUG-97]]: the measured cases, re-scoped by Phase 3 to their causes (design §3.3).
- [[MAINT-45]]: the performance-records harness the workloads join.
- [[MAINT-56]] Phase 4's representative run stays paused until the cause items land; this feature
  measures and tracks the tail but does not remove it.
