---
id: "MAINT-40"
title: "40: Corpus Sweep Results Stored Per Commit"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-40: Corpus Sweep Results Stored Per Commit

## The gap

The corpus sweep's reference results are committed files under `src/test/resources/corpus/`:
per-symbol **counts**, capped at the top ten per symbol, compared by a ratchet that lets
improvements pass silently. Two consequences follow:

- **A green gate does not prove the baselines are current.** Improvements accumulate unrecorded,
  so any work that needs a trustworthy "before" has to re-record the full corpus first.
  [[MAINT-37]]'s record sequence carries exactly this cost.
- **Counts hide offsetting changes.** One false positive fixed and a new one introduced leave the
  count unchanged.

## Scope

The sweep emits its results as **SARIF findings** (rule, file, location, stable fingerprint)
rather than aggregated counts. Each run is stored against the commit it ran on, in a per-commit
results store that accepts SARIF keyed by commit, check and suite. A later run, or any tooling,
fetches the stored results for a commit, or for the nearest ancestor that has them, and compares
against them.

Lunar's side of this:

- **Emit findings.** `CorpusSweep` writes every hit as a SARIF result with a fingerprint that is
  stable across commits. Counts, where the ratchet still wants them, are derived from the findings.
- **Derive the suite name from the environment, never type it.** The name is computed from the
  inputs that decide comparability: the sweep shape (full or member-alone), the corpus manifest
  pin, and the definition-library pins ([[MAINT-37]]). Two runs share a suite name only if they
  are comparable.
- **Store from CI.** The CI `corpus` job puts its results against the commit it built. A run done
  by hand may be stored as an optional sidecar under the same naming rule.
- **Compare with our own tooling.** Fetching a stored result and running Lunar's own comparison,
  such as [[MAINT-37]]'s attribution classifier, is ordinary tooling. It does not depend on the
  store's own comparator.

## Relationship to neighbouring work

- **[[MAINT-37]]** does not depend on this. With MAINT-40 in place, MAINT-37's record sequence
  replaces its starting re-record and its old-pin reference run with fetches of stored results.
  That is an edit to a single procedure.
- **[[MAINT-39]]** gains an attributable "before" for the annotated corpus from the first run.
- Whether the committed count baselines stay as reviewable files in PR diffs, or the stored
  results become the reference, is a decision this feature's design must make explicitly.
