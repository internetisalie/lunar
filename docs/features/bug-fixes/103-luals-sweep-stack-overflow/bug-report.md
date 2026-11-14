---
id: "BUG-103"
title: "Analysing the luals corpus member intermittently fails with StackOverflowError"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-103: luals analysis intermittently overflows the stack

## 1. Reproduction
Run the full MAINT-60 sweep: `test -PwithSnapshotSweep --tests '*SnapshotCostSweep*'`. In two of
six sweeps of the same engine code (MAINT-65 `r2-p1-sweep1`, and the first MAINT-63 DR-17 sweep at
`4035acf4`), `testM00aLuals` failed with `java.lang.StackOverflowError at HashMap.java:671` and
produced no luals rows. The other four sweeps of that code completed luals.

## 2. Expected
Analysing any file of the luals member completes, on every run.

## 3. Actual
The run fails part of the time. The stack trace was not kept, so the recursing frames are not yet
known; the first step is to capture it, for example by keeping the sweep's JUnit XML report.
Recursion depth that varies between runs is consistent with build-order dependence (BUG-102).

## 4. Impact
- In a live IDE the same overflow would abort analysis of the file, and its highlighting.
- The sweep has no luals rows on a failing run, so MAINT-63-03 and the release verdict count around
  the member instead of measuring it.
