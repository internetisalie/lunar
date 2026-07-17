---
id: "BUG-383"
title: "Rockspec version-conflict engine misses equal-version strict bounds (`>= 2.0` + `< 2.0` not flagged unsatisfiable)"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-383: Version-conflict engine misses equal-version strict bounds

> **RESOLVED 2026-07-17 (commit 113b9ef4)**: `flagUnsatisfiable` in `VersionConflictEngine` now also flags pairs where `lower.version.compareTo(upper.version) == 0` and at least one bound is exclusive (`GT` or `LT`). `>= 2.0 + <= 2.0` remains satisfiable. Added two tests to `VersionConflictEngineTest`.

*Source: codebase review [`docs/review.md`](../../../review.md) finding **#45** (still present
2026-07-17).*

## 1. Reproduction

1. In a multi-rock workspace, declare dependencies on the same rock with constraints
   `>= 2.0` in one rockspec and `< 2.0` in another.
2. Observe the workspace dependency diagnostics.

## 2. Expected vs Actual Behavior

- **Expected**: flagged as unsatisfiable — no version can satisfy `>= 2.0` and `< 2.0`.
- **Actual**: not flagged. `rocks/VersionConflictEngine.kt:51` only reports when
  `lower.version > upper.version`; equal bounds with at least one exclusive comparator slip
  through.

## 3. Notes

Fix per the review: also flag equal versions unless **both** bounds are inclusive
(`>= 2.0` + `<= 2.0` is satisfiable by exactly 2.0; any exclusive side is not).
