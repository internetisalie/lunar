---
id: "BUG-105"
title: "Type snapshots of library files are lost when their PsiFile is garbage-collected, so they are rebuilt after an edit"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-105: Library type snapshots are lost to garbage collection

## 1. Reproduction
Run the MAINT-60 sweep's edit pass with MAINT-63's per-source invalidation prototype (V1). Among the
after-edit reads that still miss the 250 ms target, about half the rebuild time is spent on library
files whose `PsiFile`, or the cached value attached to it, has been collected since the cold pass.
The snapshot is rebuilt from scratch, although no source it read has changed. Holding those files
strongly (prototype V2) cut V1's edit misses on the measured subset from 43 to 25 (MAINT-63 risks
DR-11).

## 2. Expected
A library file's type snapshot survives for as long as no source it read has changed and memory
allows, so an edit elsewhere does not rebuild it.

## 3. Actual
The snapshot lives in a `CachedValue` on the `PsiFile`. Library `PsiFile`s are held softly by the
platform, and are collected under ordinary memory pressure, which discards the snapshot with them.

## 4. Constraint
`docs/engineering-contract.md` forbids hard references to `PsiFile`, so V2's strong holding is not a
fix. A fix needs a cache policy that keeps the snapshot (or enough of it to rebuild cheaply) without
pinning the PSI, for example a soft- or size-bounded cache keyed by `VirtualFile` URL and the
snapshot's dependency stamps.
