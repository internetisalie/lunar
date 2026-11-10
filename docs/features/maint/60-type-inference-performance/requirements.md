---
id: "MAINT-60"
title: "60: Type-Inference Performance"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-60: Type-Inference Performance

## Overview
A file's type snapshot is rebuilt after every edit, so its build time is the delay before highlighting
and inspections catch up. Across the 35-member type corpus most files build in milliseconds (median
3 ms, p99 866 ms), but a tail does not: 18 files take a second or more, `compiler.lua` in luals takes
49 s and fallo's `init.lua` 120 s ([[BUG-96]], [[BUG-97]]; MAINT-56 risks-and-gaps DR-16). The JFR
profiles in both bug reports put the time in the fixed-point pass (`checkTypes` → `checkNodePairs`):
structural hashing of `LuaGraphType.Table`, `HashMap`/`LinkedHashMap` churn in
`VariableElement.resolveWrite`/`resolveRead`/`mergeTableDemands`, and, in luals, snapshot builds nested
7–16 deep through `resolveModule`. This feature fixes the tail and keeps it fixed. Parent:
[[features/maint/requirements|MAINT]].

## Scope

### In Scope
- Fixing [[BUG-96]] and [[BUG-97]] at their measured causes.
- Inference workloads in the MAINT-45 per-commit performance records.
- A repeatable per-file snapshot-timing sweep over the type corpus.

### Out of Scope
- Incremental (sub-file) re-inference.
- Changing what the checker reports: findings must not change except where a fix is shown to correct one.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-60-01 | **Tail fixed** | M | fallo `lua/fallo/init.lua` and luals `script/vm/compiler.lua` each build in under 2 s on the builder, cold. |
| MAINT-60-02 | **No file over 2 s** | S | No type-corpus file takes 2 s or more in the sweep, or each that does has its own bug. |
| MAINT-60-03 | **Findings unchanged** | M | The MAINT-54 audit and the MAINT-55 conformance results are unchanged by the fixes, or each change is a recorded correction. |
| MAINT-60-04 | **Inference workloads recorded** | M | MAINT-45's performance records gain type-inference workloads: fallo `init.lua`, luals `compiler.lua`, and a few median-cost corpus files. |
| MAINT-60-05 | **Sweep is a tool** | S | The per-file sweep (DR-16's probe) is a committed, opt-in test that writes a ranked table, runnable in under 15 minutes. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-60-01 | fallo `init.lua` at its pinned commit | build its snapshot cold | under 2 s |
| 2 | MAINT-60-01 | luals `script/vm` at its pinned commit | build `compiler.lua` cold, then after an edit | each under 2 s |
| 3 | MAINT-60-03 | the MAINT-54 audit and `LuaCatsConformanceTest` | run before and after | unchanged, or each difference recorded as a correction |
| 4 | MAINT-60-04 | a commit on main | CI's performance-records step | a record for each inference workload |
| 5 | MAINT-60-05 | the type corpus fetched | run the sweep | a ranked per-file table within 15 minutes |

## Dependencies
- [[BUG-96]], [[BUG-97]]: the measured cases.
- [[MAINT-45]]: the performance-records harness the workloads join.
- Unblocks [[MAINT-56]] Phase 4, whose representative run is paused until the tail is fixed.
