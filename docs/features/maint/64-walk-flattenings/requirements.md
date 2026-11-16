---
id: "MAINT-64"
title: "64: Walk Flattenings That Change Findings"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-64: Walk Flattenings That Change Findings

## Overview
MAINT-60 measured candidate flattenings of the checker's read/write/demand walks (`flatDemand`,
`flatWrite`, `flatRead`). They cut the cold sweep's summed reads from 4.18 to 2.45 minutes (DR-06) but
change findings in 2 to 81 files, so they were not applied. This item decides which of those changes
are corrections and ships the flattenings whose differences are accepted. It holds no file of the
sweep's table today: the files over target are filed by cause under MAINT-61, MAINT-62 and
MAINT-63, and this item is a means of reducing them. Parent:
[[features/maint/requirements|MAINT]]. Measurement feature: [[MAINT-60]].

## Measured Cause
DR-06: per-file digests of the error strings differ from `base` for 2 files (`delta flatDemand`),
10 (`delta idmemo flatDemand`), 17 (`+ flatWrite`) and 81 (`+ flatRead`, all). Even `all` leaves 67 files
at or over 250 ms so this item alone does not reach
MAINT-60-01.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-64-01 | **Classify each digest difference** | M | Not Implemented | For each file whose digest differs under a flattening, record whether the new findings are corrections or regressions, with the cause. |
| MAINT-64-02 | **Ship accepted flattenings** | S | Not Implemented | Ship the flattenings whose differences are all corrections, with the MAINT-54 audit and MAINT-55 conformance results unchanged or recorded. |

## Files over target
None: no pass of the sweeps is filed under this item.
