---
id: "MAINT-45"
title: "45: Performance Records Stored Per Commit"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-45: Performance Records Stored Per Commit

## The gap

Performance is judged one change at a time, by a pass or a fail. The suites that time anything
assert a wall-clock budget and print the measured figure to stdout, where nothing keeps it. So:

- **No change can be compared with another.** A PR that makes completion 40 % slower passes if it
  stays under the budget, and nobody sees the figure move.
- **Slow drift is invisible.** Several changes that each cost a little never trip a budget set for
  a step change.
- **The one gate that cannot flake is a count, not a time.**
  `LuaTypeGraphRootResolutionBudgetTest` counts root resolutions and needs no host calibration;
  [[BUG-474]] removed a ratio-of-times gate because it was not host-portable. [[BUG-473]] and
  [[BUG-517]] were both linear work turning superlinear, which a count detects directly.

[[MAINT-38]] rejected a CI perf gate (its tier 3) because wall-clock budgets were flaky on the
shared runner. Both premises have changed: routine CI now runs on the `lunar-ci` pool, one
`n2-standard-4` (4 vCPU, 16 GB) VM per job, and this feature gates on counts, not times.

## Scope

- **Counter workloads.** Fixed, generated workloads at several sizes (parse, type snapshot,
  completion, resolve) that report the work done in Lunar's own terms, such as resolutions, stub
  lookups and parser node visits, alongside elapsed time.
- **Emit CTRF.** Each workload is one CTRF test: its elapsed time in `duration`, its counts in the
  test's `extra`. The run's environment metadata records the **CPU model** (from `/proc/cpuinfo`),
  because N2 VMs land on more than one CPU platform and a timing is comparable only between runs
  on the same one.
- **Store from CI.** The `build` job on the `lunar-ci` pool stores each push to `main` as a
  conformance record for that commit (`loom-cli artifact put conformance`). Tag builds on the
  shared pool and the GitHub workflow do not store: their hosts differ, and GitHub's runners are
  too small to be representative. The record needs a suite of its own (`--suite`), because
  [[MAINT-48]] stores the unit-test results as `a3`'s primary record.
- **Compare on PRs.** A PR run fetches the record of the nearest code-identical first-parent
  ancestor of its merge-base with `main`, as [[MAINT-48]] design §3.6 does (docs-only pushes store
  nothing, so the merge-base itself often has no record), and diffs against it. A count that rises fails
  the PR unless the change accepts it; a timing is reported, never gated, with large increases
  flagged.
- **Compare over time.** A small report walks first-parent `main`, or the release tags, resolves
  each commit's record and prints the series per workload, so drift that no single PR shows
  becomes visible.

## Open questions

- How a deliberate count increase is accepted on a PR, given that the reference is the stored
  record rather than a committed file.
- What `--producer` and `--item` are for a CI job, and whether a second put for the same commit and
  check is refused (asked of `loom.storage`). This decides whether a rerun can supersede a record.
- CI's credentials for the artifact store, expected to be an org secret.

## Relationship to neighbouring work

- **Depends on [[MAINT-40]]**, which wires the per-commit store into CI for the corpus sweep's
  findings (as CTRF) and fixes the environment-derived suite naming. This feature reuses both.
- **Answers [[MAINT-38]] tier 3** (MAINT-38-09) on the changed premises above. MAINT-38's JFR
  recipe stays the tool for diagnosing a regression this feature detects.
- The existing wall-clock tests under `performance/` can become workloads here, which would retire
  their budgets.
