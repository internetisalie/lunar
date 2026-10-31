---
id: "MAINT-45"
title: "45: Performance Records Stored Per Commit"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
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
  conformance record for that commit (`loom-cli artifact put conformance`). Tag builds (`build-release`, on `act-ci`) and the
  GitHub workflow do not store: their hosts differ, and GitHub's runners are
  too small to be representative. The record needs a suite of its own (`--suite`), because
  [[MAINT-48]] stores the unit-test results as `a3`'s primary record.
- **Compare on PRs.** A PR run fetches the record of the nearest code-identical first-parent
  ancestor of its merge-base with `main`, as [[MAINT-48]] design §3.6 does (docs-only pushes store
  nothing, so the merge-base itself often has no record), and diffs against it. A count that rises
  fails the PR unless the change accepts it, once gating is on; the comparison ships report-only until
  `main`'s records are shown stable across hosts (MAINT-45-15, -16). A timing is reported, never gated,
  with large increases flagged.
- **Compare over time.** A small report walks first-parent `main`, or the release tags, resolves
  each commit's record and prints the series per workload, so drift that no single PR shows
  becomes visible.

## How these requirements were derived

Written from sources independent of Lunar's code, then checked against prototypes
([risks-and-gaps.md](risks-and-gaps.md), DR rows):

- **The CTRF 0.2.0 schema**: where a count may live (`extra`) and what a duration must be.
- **[[MAINT-48]] design §3.6 and the store contract**: baseline walk, keep-first storing, suites.
- **[[BUG-473]], [[BUG-474]], [[BUG-517]]**: what regressed (work re-derived, parser re-entry), and
  which instrument failed (wall clock, ratios).
- **An a-priori failure list**, with the requirement that answers each:

| What can go wrong | Answered by |
|---|---|
| Wrong baseline: a later `main`, or an ancestor with different code | MAINT-45-07 |
| Noisy count: a gate that reddens on nothing | MAINT-45-01, -02, -03 |
| Vacuous workload: a count that stays low because the work stopped happening | MAINT-45-02 |
| Missing record: a green PR that compared nothing, or a red one blamed on the store | MAINT-45-11 |
| CPU-platform mismatch: timings compared across hosts | MAINT-45-05, -10 |
| Immutable store: a rerun cannot replace a record | MAINT-45-06 |
| A deliberate increase has no way through | MAINT-45-09 |
| A workload edit reads as a regression | MAINT-45-12 |
| A count differs between hosts: a false rise on an innocent PR | MAINT-45-15, -16 |

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-45-01 | **Counted workloads** | M | Full | Generated workloads at several sizes for parse, type snapshot, resolve and completion each report counts of work plus elapsed time. No production file changes; counters are taken from test code and existing test-only accessors. |
| MAINT-45-02 | **Workloads check themselves** | M | Full | Each workload runs twice in the JVM, each run preceded by a reset that empties the source root and rebuilds the stub index, so a workload's counts depend on its own fixture alone. The run fails, in every gate mode, if the work did not happen: a required counter is zero, or an output is wrong (no lookup items, an unresolved call site). Every workload has at least one required counter. A difference between the two passes is recorded and printed as `WOBBLE`, and fails only in `gating` mode (MAINT-45-15). |
| MAINT-45-03 | **Own invocation** | M | Full | The workloads are excluded from the routine `test` run and run in a dedicated Gradle invocation, so no other test shares their JVM. |
| MAINT-45-04 | **CTRF document** | M | Full | One CTRF 0.2.0 document per run, one test per workload, elapsed milliseconds in `duration`, counts in `extra`. |
| MAINT-45-05 | **CPU model recorded** | M | Full | The document's environment carries the CPU model and vCPU count read from `/proc/cpuinfo`. |
| MAINT-45-06 | **Store from `main` pushes** | M | Full | The `build` job stores the document as `a3` suite `perf` on push events only, against `HEAD`, keeping the first record of a commit. PR runs, tag builds and GitHub never store, and in `gating` mode a run with a `WOBBLE` does not store. |
| MAINT-45-07 | **Baseline** | M | Full | A PR run fetches the record of the nearest code-identical first-parent ancestor of its merge-base with its base branch, never a later `main` and never an ancestor with different code. |
| MAINT-45-08 | **Count gate** | M | Partial | Any count above its baseline value, by any amount, is a `RISE` unless accepted, and in `gating` mode fails the PR's `build` job. A counter absent on one side reads as 0. |
| MAINT-45-09 | **Acceptance** | M | Full | A rise is accepted by an entry in the committed file `tooling/perf/accepted-increases.json` naming workload, counter, exact baseline value, maximum new value and a reason. An entry applies only on the PR that adds it, meaning it is absent from the merge-base's copy of the file. It shows in that PR's diff and cannot admit any other rise. |
| MAINT-45-10 | **Timings reported, never gated** | M | Full | The diff prints each workload's timing when both sides ran on the same CPU model, flags large increases, and states when the models differ. No timing changes an exit code. |
| MAINT-45-11 | **Missing baseline is loud, not red** | M | Full | With no baseline record the PR run emits a `::warning::` saying counts were not gated, and passes. A failure of the workloads or of the store tooling fails the job. |
| MAINT-45-12 | **Changed workloads are not compared** | S | Full | A workload whose generated fixture differs from the baseline's, or that exists on one side only, is reported and not gated. |
| MAINT-45-13 | **Series report** | S | Full | A command walks first-parent `main` or the release tags, resolves each commit's record and prints each workload's counts and timings in commit order. |
| MAINT-45-15 | **Gate switch** | M | Full | The committed file `tooling/perf/gate-mode` selects `report-only` or `gating`, is read by `ci`, and ships as `report-only`. In `report-only` a `RISE` or `WOBBLE` prints and cannot fail the job. Changing mode edits that file, never a workflow. A failed workload check or tooling failure fails the job in both modes. |
| MAINT-45-16 | **Stability condition** | M | Partial | The `stability` command walks consecutive records on first-parent `main`. A pair is eligible only when no path in `COUNT_PATHS` differs between its two commits, and a workload is compared on it only when its fixture is equal on both sides. A pair or workload that fails either test, or a commit with no record, is not evidence either way: it earns no credit and raises no `BREAK`. `STABLE` requires, for every workload in the newest record and on that record's fixture, at least `STABILITY_PAIRS` eligible pairs with identical counts, including at least `STABILITY_CROSS_MODEL_PAIRS` across two known, different CPU models. A workload short of either is named and the verdict is `NOT_YET`. Any eligible pair that differs, or a record whose own two passes differ, is a `BREAK` naming the workload, the commits and the counter. `gating` is set only after it prints `STABLE`. |
| MAINT-45-14 | **Retire the wall-clock suites** | W | Future Work | Converting `performance/` tests into workloads and deleting their budgets. See Out of Scope. |

## Out of Scope

- **Changing production code to count.** Completion therefore has no root-resolution count; see
  risks Gap 2.1.
- **The `performance/` suites and `LuaTypeGraphRootResolutionBudgetTest`.** Both stay. The budget
  test is an absolute bound in the routine loop, which a relative gate does not replace.
- **A PR comment or job-summary view.** The log carries the diff.
- **MAINT-38's JFR recipe** stays the way to diagnose a regression this feature detects.

## Test Cases

`Red under` names the mutant that must turn the case red; each is reachable from that case's own
fixture. TC 1–4, 30–34 and 51 are Kotlin or Gradle (`LuaPerfRecordWorkloadsTest`, `PerfRecordChecksTest`,
the sidecar); the rest are `tooling/ctrf/test_perf_ctrf.py` with the stub `loom-cli`. `w` is a workload
with equal fixtures on both sides unless stated. `diff` always takes `--accepted`, a file holding
`{"schema":1,"accepted":[]}` unless stated. `ci` always takes `--accepted` the same way and `--gate-mode`
naming a file holding `gating` unless stated. A record's `warm` equals its recorded values unless stated.

For `stability`, the *base repo* is `STABILITY_PAIRS + 1` commits, each changing only `tooling/x` (outside
`COUNT_PATHS`) and each with a record of `w`, `marks` 100, fixture `F`, CPU models alternating `X`, `Y`
from the head. Every case runs `stability --first-parent HEAD` on it unless stated.

| # | Req | Given | When | Then | Red under |
|---|-----|-------|------|------|-----------|
| 1 | 01, 02 | the workload table (design §4.1) | `test --rerun -PwithPerfRecords --tests '*PerfRecord*'` | passes; `build/perf-records/` holds one file per workload; every parse file has `marks` and `advances` above 0 | delete `advances++` in `CountingPsiBuilder`: the required-counter check fails on `parse-flat-500` |
| 2 | 02 | `completion-crossfile-*` | same run | passes; `lookupItems` above 0 in both files | delete the `configureByText("globals.lua", …)` call: 0 items (DR-20), so the output check fails |
| 3 | 02 | `completion-member-200`, whose fixture ends `b.<caret>` | same run | `lookupItems` = 200 | end the fixture `b:<caret>`: 0 items (DR-10), so the output check fails |
| 4 | 03 | `test --rerun --tests '*LuaBundleCasingTest*'`, without and with `-PwithPerfRecords` | read `build/test-results/test-filter.json` after each | `excludePatterns` has `*PerfRecord*` without the property, lacks it with the property | delete the `excludeTestsMatching` line |
| 5 | 04, 05 | records `b.json`, `a.json`; a cpuinfo text with `model\t\t: 85` before `model name\t: X`, and two `processor` lines | `assemble --cpuinfo <file>` | `tests` in order `a`, `b`; `extra.counts` and `duration` as written; `environment.extra` = `{"cpuModel":"X","vcpus":2}` | a CPU pattern of `^model\b` reads `85` |
| 6 | 04 | an empty directory; a record with `schema` 2 | `assemble` | exit 2 each | delete either refusal |
| 7 | 06 | stub: `HEAD` unmeasured | `ci --event push` | one `put` whose argv has `--check a3 --no-item --suite perf` | `SUITE_ARGV = ()` |
| 8 | 06 | stub: `HEAD` has a record of other bytes | `ci --event push` | exit 0, a `WARNING`, no `put` | `ci`'s push branch calls `put` itself instead of `store_once` |
| 9 | 07 | a scratch repo `A` (code), `B` (docs), `C` (code), `D` (docs); stub record at `A` only | `fetch --commit B`, then `fetch --commit D` | `fetched <A> distance=1`; then exit 3 | walk `first_parents` unfiltered: `D` finds `A` and exits 0 |
| 10 | 08 | `marks` 100 → 101 | `diff` | exit 1; `RISE\tw\tmarks\t100\t101` | allow a tolerance of 1 |
| 11 | 08 | `marks` 100 → 99 | `diff` | exit 0; `FALL\tw\tmarks\t100\t99` | treat any change as a rise |
| 12 | 08 | `stub:x` absent before, 3 after | `diff` | exit 1; `RISE\tw\tstub:x\t0\t3` | compare only counters on both sides |
| 13 | 09 | 100 → 120; entry `from` 100, `to` 120, a reason | `diff` | exit 0; `ACCEPTED\tw\tmarks\t100\t120\t<reason>` | ignore the file |
| 14 | 09 | 100 → 121 with the same entry | `diff` | exit 1; `RISE` | drop the `to` check |
| 15 | 09 | 101 → 120 with the same entry | `diff` | exit 1; `RISE`, and `ACCEPTANCE_UNUSED\tw\tmarks\t100` | drop the `from` check |
| 16 | 09 | 100 → 120 on `advances`; the entry names `marks` | `diff` | exit 1 | match on workload alone |
| 17 | 09 | an entry with an empty `reason`; a missing file | `diff` | exit 2 each | delete either refusal |
| 18 | 10 | same CPU; `duration` 10 → 100000, counts equal | `diff` | exit 0; a `TIMING` line ending `FLAG` | a flag sets exit 1 |
| 19 | 10 | same CPU; 100 → 400; 100 → 399; 10 → 30; 1000 → 1500 | `diff` | only 100 → 400 ends `FLAG` | drop the `TIMING_FLOOR_MS` floor: 10 → 30 flags |
| 20 | 10, 05 | CPU `X` before, `Y` after | `diff` | `TIMING_NOT_COMPARABLE\tX\tY`; no `TIMING` line | delete the model check |
| 21 | 07, 08 | local origin: `M1`, branch `P` off it, cloned at depth 1, then `M2` on `main`. Stub records: `M1` `marks` 100, `M2` `marks` 200; this run 200 | `ci --event pull_request --base-ref main` | exit 1; `RISE\tw\tmarks\t100\t200`; no `put` | baseline `origin/main` instead of the merge-base: exit 0 |
| 22 | 07 | as TC 21 | same | the clone is no longer shallow after the run | delete the deepen call: `git merge-base` exits non-zero |
| 23 | 11 | as TC 21, stub has no records | same | exit 0; `compare` step 6's `::warning::` line, naming `M1` and `counts were not gated` | delete that line's `print`: exit 0, no warning. Make that step return 1: `gate` gets `moved` true, exit 1 |
| 24 | 11 | as TC 21, stub `resolve` exits 1 | same | exit 1 | catch `LoomError` as "no baseline" |
| 25 | 12 | 100 → 200 with differing `fixture` | `diff` | exit 0; `FIXTURE_CHANGED\tw`; no `RISE` | compare regardless |
| 26 | 12 | a workload on one side only | `diff` | exit 0; `ADDED\tw` or `REMOVED\tw` | treat `ADDED` as a rise |
| 27 | 04 | a document from another tool | `diff` | exit 2 | delete the tool check |
| 28 | 13 | scratch repo `A`, `B`, `C`; records at `A` and `C` | `series --first-parent C` | `A`'s line before `C`'s for each workload and counter; `unmeasured\t1` | keep `rev-list` order, newest first |
| 29 | 13 | tag `v1` on a docs-only commit after `A`; record at `A` | `series --tags` | `A`'s values labelled `v1` | look up the tag's commit alone: nothing found |
| 30 | 02 | `violations(checks, raw, outputs)`: `required` = `{stub:lunar.global.decl}`, `raw` = `{}`, `outputs` = `{lookupItems=200}` | call it | one violation naming the counter | delete the `required` clause |
| 31 | 02 | the workload table | as TC 1 | passes; every `completion-*` file has `stub:lunar.global.decl` above 0 | make `StubLookupCounter.drain` return `emptyMap()`: the run fails naming each completion row |
| 32 | 02 | `exactOutputs` = `{resolved=320}`, `raw` = `{write=1}`, `outputs` = `{resolved=319}` | `violations` | one violation naming `resolved` | delete the `exactOutputs` clause |
| 33 | 02 | `positiveOutputs` = `{lookupItems}`, `outputs` = `{lookupItems=0}` | `violations` | one violation naming `lookupItems` | delete the `positiveOutputs` clause |
| 34 | 02 | warm `Pass` with raw `{x=1, z=0}`; recorded `Pass` with raw `{x=2, z=0}` | `toRecord` | `warm.counts` = `{x=1}`; `counts` = `{x=2}`; no `z` | build `warm` from the recorded pass: `{x=2}` |
| 35 | 02, 15 | a record with `counts.marks` 101, `warm.counts.marks` 100; `HEAD` unmeasured | `ci --event push` | exit 1; `WOBBLE\tw\tmarks\t100\t101`; no `put` | drop the wobble check from the push branch |
| 36 | 15 | as TC 35, file holds `report-only` | same | exit 0; the `WOBBLE` line; one `put` | gate a wobble in every mode |
| 37 | 15 | a file holding `report_only`; a missing path | `ci --event push` | exit 2 each | default a missing or unknown file to `report-only` |
| 38 | 15 | the committed `tooling/perf/gate-mode` | `read_mode` | returns a value in `MODES` | misspell the committed value |
| 39 | 11 | `--event workflow_dispatch`, empty `--base-ref` | `ci` | exit 0; a `::warning::` naming the event; no `put` | delete step 5: with `accepted.json` present, `deepen` refuses the empty base ref, exit 2. A `pull_request` with an empty `--base-ref` is also exit 2 with `NOT COMPARABLE: no base ref`; deleting `deepen`'s refusal turns it into exit 1 |
| 40 | 08, 15 | as TC 21, file holds `report-only` | same as TC 21 | exit 0; the `RISE` line; a `::warning::`; no `::error::` | return `compare`'s code whatever the mode |
| 41 | 09 | local origin. `M1` record `marks` 100, empty entries. PR1 on `M1` adds `from` 100, `to` 120, run 120; merged as `M2`, record 120. PR2 on `M2`, run 100; merged as `M3`, record 100. PR3 on `M3`, no file change, run 118 | `ci --event pull_request --base-ref main` for each PR | PR1 exit 0, `ACCEPTED`; PR2 exit 0, `FALL`; PR3 exit 1, `RISE\tw\tmarks\t100\t118`, no `ACCEPTED`, no `ACCEPTANCE_UNUSED` | `own_entries` returns every current entry: PR3 prints `ACCEPTED` and exits 0 |
| 42 | 09 | as TC 21 with an entry `from` 100, `to` 200 on the branch, and no accepted file at `M1` | same as TC 21 | exit 0; `ACCEPTED` | treat a file absent at the merge-base as an error: `git show` exits 128 |
| 43 | 09 | `current` holds a retained entry and a copy of it with a new `reason`; `previous` holds the retained entry | `own_entries` | returns only the copy with the new `reason`; two entries absent from `previous` are both returned | compare by workload, counter and `from` alone: returns nothing. Return only the first entry: the second is lost |
| 44 | 08 | `lookupItems` 200 → 201, counts equal | `diff` | exit 0; `OUTPUT_CHANGED\tw\tlookupItems\t200\t201` | delete the `extra.outputs` loop |
| 45 | 16 | the base repo | `stability` | exit 0; `eligible\t{STABILITY_PAIRS}`; `cpuModels\tX,Y`; `STABLE` | require more pairs than `STABILITY_PAIRS`: `NOT_YET` |
| 46 | 16 | the base repo, all `X` | same | exit 1; `WAITING\tw\t{STABILITY_PAIRS}\t0`; `NOT_YET` | drop the `cross` clause of `waiting_lines` |
| 47 | 16 | the base repo, the commit two below head has `marks` 101, all `X` | same | exit 1; `BREAK\t<newer>\t<older>\tw\tmarks\t100\t101`; `NOT_YET` | drop `count_differences` from `walk_pair` |
| 48 | 16 | the base repo, the head's parent has `warm.counts.marks` 99 | same | exit 1; a `BREAK` line holding `WOBBLE`; `NOT_YET` | drop `wobble_lines` from the walk |
| 49 | 16 | the base repo, with an unmeasured docs-only commit between head and its parent | same | exit 0; `eligible\t{STABILITY_PAIRS}`; `STABLE` | stop the walk at an unmeasured commit |
| 50 | 02, 15 | as TC 21 with equal counts and `warm.counts.marks` differing | same as TC 21 | exit 1; a `WOBBLE` line; no `RISE` | judge a PR by `compare`'s code alone |
| 51 | 02 | the workload table | as TC 1; compare each file's `counts` and `outputs` with its `warm` | equal for every workload | delete `forceRebuild` from `resetWorkspace`: `completion-global-200` differs (DR-18) |
| 52 | 16 | ten commits: head fixture `F2`, `marks` 100, model `X`; the nine below fixture `F1`, `marks` 101, models alternating `Y`, `X` | same | exit 1; `WAITING\tw\t0\t0`; no `BREAK`; no `STABLE` | `credit_pair` compares the pair's fixtures with each other, not with head's: `STABLE` |
| 53 | 16 | ten commits: the newest five fixture `F2`, `marks` 100, model `X`; the oldest five fixture `F1`, `marks` 101, model `Y` | same | exit 1; `WAITING\tw\t4\t0`; no `BREAK`; no `STABLE` | drop both fixture conditions of `credit_pair`: the boundary pair earns the cross credit, `STABLE` |
| 54 | 16 | the base repo plus workload `n` in the newest three records only | same | exit 1; `WAITING\tn\t2\t2`; no `WAITING` line for `w` | one credit per pair instead of per workload: `STABLE` |
| 55 | 16 | the base repo, each commit also changing `src/main/x` and `marks` differing on every record | same | exit 1; `eligible\t0`; `ignored\t{STABILITY_PAIRS}`; no `BREAK` | `same_count_code` always true: `BREAK` |
| 56 | 16 | the base repo, the head's parent has `marks` 101 | same | exit 1; `BREAK\t<head>\t<parent>\tw\tmarks\t100\t101` | compare only same-model pairs: no `BREAK` |
| 57 | 16 | the base repo, the head commit also changing `gradle.properties` and its record `marks` 101 | same | exit 1; `ignored\t1`; no `BREAK` | `COUNT_PATHS = ("src/main",)`: `BREAK` |
| 58 | 16 | the base repo, models alternating `X`, `unknown` | same | exit 1; `WAITING\tw\t{STABILITY_PAIRS}\t0`; `NOT_YET` | count `unknown` as a model: `STABLE` |
| 59 | 16 | a depth-1 clone of the base repo | same | exit 2 | delete the shallow check: exit 1 with one record, no refusal |
| 60 | 11, 15 | as TC 24, file holds `report-only` | same as TC 21 | exit 1; a `loom-cli:` line; no `::warning::` | `ci` step 6 catches `LoomError` as `code = 1`: `gate` returns 0 in `report-only`, exit 0 (TC 24, in `gating`, stays green under it) |
| 61 | 06 | stub: `put` exits 1; then stub: `resolve` of `HEAD` exits 1; file holds `report-only` | `ci --event push` | exit 1 each; no `put` after the failed `resolve` | `ci`'s push branch discards `store_once`'s result: `store_once(...); return 0` |
| 62 | 02, 15 | a record with `outputs.lookupItems` 201, `warm.outputs.lookupItems` 200; file holds `gating` | `ci --event push` | exit 1; `WOBBLE\tw\tlookupItems\t200\t201`; no `put` | drop `("outputs", None)` from `WOBBLE_SECTIONS` |
| 63 | 15 | as TC 21's repository, a record with `counts` and `warm` differing, file holds `gating` | `ci --event pull_request --base-ref main` | exit 1; the `WOBBLE` line; no `put` | step 6 stores the document: a `put` appears. (Dropping `bool(wobbles)` from step 6's `gate` is TC 50's mutant, red here too) |
| 64 | 07 | stub `git` on `PATH` logging its environment and argv; `GITEA_TOKEN` set; TC 21's repository with `accepted.json` committed at the merge-base | `ci --event pull_request` | the `fetch` call ran with `GIT_CONFIG_COUNT` 1, key `http.extraHeader`, value `Authorization: token <token>` | `deepen` calls `_git` instead of `_remote_git`: the variables are empty |
| 65 | 09 | as TC 64, with `accepted.json` committed at the merge-base | same | the lazy `show` call carries the same three variables | `accepted_at` calls `_git` for the `show`: empty |
| 66 | 07 | as TC 65, `GITEA_TOKEN` unset; then empty; then set | same | the `fetch` and `show` calls carry none of the variables when it is unset or empty; the local `rev-parse`, `ls-tree` and `merge-base` calls never carry them | `git_env` sets them without checking for the token; checks `is None` instead of falsiness; `_git` is given `git_env()` |
| 67 | 07 | as TC 65; also a stub `git` that prints its header to stderr and exits 128 | same | the token is on no recorded argv and in no output; a failed fetch prints git's reason (`gone`) and not the token; git's stderr reaches the output with the token replaced by `***`; `_scrubbed` hides a token it is given | pass `-c http.extraHeader=…` on the argv; drop `failure.stderr` from `main` (the `gone` assertion); make `_scrubbed` return its text (its unit test); print `{failure}` and `{failure.stderr}` raw in `main`, or scrub only `str(failure)` (the stub-echo test) |
| 68 | 15 | a record with `counts` and `warm` differing, file holds `gating` | `ci --event workflow_dispatch` | exit 1; the `WOBBLE` line; no `put` | step 5 returns 0 whatever `gate` says |
| 69 | 07 | local origin: `M1` code (record), `D` docs-only (no record), branch `P` off `D`, then `M2` on `main` | `ci --event pull_request --base-ref main` | `baseline <M1> distance=1` | `compare` walks `[merge_base]` alone: the no-baseline warning |
| 70 | 16 | the base repo plus one older commit whose record has `marks` 101 | `stability` | exit 0; `STABLE`; no `BREAK` | remove the stop once `waiting_lines` is empty: the older pair is a `BREAK` |
| 71 | 09 | accepted text that is not JSON; `schema` 2; an entry with `to` ≤ `from`; an empty `workload`, a null `counter`, a string `from`, a float `to` | `parse_accepted` | `NotComparable` each; a well-formed file is read | delete the JSON refusal, the schema refusal, or the `to > from` comparison |
| 72 | 16 | the design's list | `COUNT_PATHS` | equals it | drop any entry |
| 73 | 07 | a perf-named record of another tool at the only candidate | `baseline` | `None` | `baseline` accepts any document |

Workflow behaviour (step order, sentinels) has no test case: see risks, Test Case Gaps. TC 7 and TC 21
cover what the step calls.

## Acceptance Criteria

- [ ] Every test case above passes and every `Red under` mutant was observed red.
- [ ] A `main` push stores `a3.perf.ctrf.json`, read back with `resolve --suite perf`.
- [ ] A PR run prints a diff against a stored baseline.
- [ ] `stability` prints `STABLE` over `main`'s records, and `tooling/perf/gate-mode` holds `gating`.
- [ ] Added CI time is measured on `lunar-ci` and recorded; the target is under two minutes.

## Non-Functional Requirements

- **Added CI time**: under two minutes per run of `build` (DR-04 gives the builder's figure).
- **Python** is stdlib-only, as the rest of `tooling/ctrf/`.
- **Production overhead**: none, because no production file changes.

## Dependencies

[[MAINT-40]] and [[MAINT-48]], both `done`: store credentials on `lunar-ci`, `loom_store.py`,
`tooling/ci/install-loom-cli.sh`, `code_identical_first_parents`. The questions filed with this
feature are answered: acceptance by MAINT-45-09 (design §3.5), `--producer`/`--item` and second puts
by DR-05, CI credentials by MAINT-40 DR-04.

## Relationship to neighbouring work

- **Depends on [[MAINT-40]]**, which wires the per-commit store into CI for the corpus sweep's
  findings (as CTRF) and fixes the environment-derived suite naming. This feature reuses both.
- **Answers [[MAINT-38]] tier 3** (MAINT-38-09) on the changed premises above.
- The existing wall-clock tests under `performance/` could become workloads later, which would
  retire their budgets (MAINT-45-14).

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
