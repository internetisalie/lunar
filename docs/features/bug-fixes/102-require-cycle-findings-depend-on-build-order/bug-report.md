---
id: "BUG-102"
title: "Findings in a file inside a require cycle depend on which file of the cycle was analysed first"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-102: Findings inside a `require` cycle depend on build order

## 1. Reproduction
Run the type-corpus sweep (MAINT-60) on the tnt* members, whose `src/` modules `require` each other
in cycles. Compare a file's findings digest read 1 s after an edit with its cold digest: MAINT-65
risks DR-06 measured **30 of 526** differing (tntcrud 20, tntmetrics 2, tntqueue 8). Each such file
has the same contents in both reads; only the file the type engine started building from differs.

MAINT-63 risks DR-20 reproduces it from the other side. Keeping a snapshot that was built from a
different root than a forced full rebuild of the same state gives 21 differing files out of 265
(tntcrud 20, tntmetrics 1). Among them is `crud/stats/registry_utils.lua`, whose build records no
in-progress hit, re-entrancy guard hit or foreign hit (DR-21).

## 2. Expected
A file's findings are a function of the project's contents. They do not change with the order in
which files are opened, analysed or rebuilt, and do not change across an IDE restart.

## 3. Actual
Inside a `require` cycle, the answer for a module depends on which module of the cycle the build
started from. The under-construction guards (`LuaMemberResolution.kt:172`,
`LuaColonCallResolution.kt:56`, `LuaNameReference.kt:62`, `:74`) answer differently when they are
reached while the file they read is still being built. The clean-frame case above shows the
dependence also reaches files whose own build hits none of them.

## 4. Impact
- A user sees findings in a cycle member change on restart, or after opening a different file first.
- It blocks MAINT-63 (risks Decision 2.1, DR-22): per-source invalidation keeps snapshots rooted
  where they were first built, so it cannot pass the after-edit oracle until answers are
  root-independent, or the dependence is recorded in the snapshot's frame.

## 5. Evidence
Raw per-file rows: `~/.cache/claude-scratch/maint-63/r2probe/out-*` (`summary.tsv`, `rows-*.tsv`).
