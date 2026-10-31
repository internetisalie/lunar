---
id: "MAINT-45-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-45"
folders:
  - "[[features/maint/45-performance-records-per-commit/requirements|requirements]]"
---

# MAINT-45: Risks & Gaps

## Critical Risks

### Risk 1.1: A count differs between hosts
- **Impact**: a false `RISE` reddens an innocent PR, or a `WOBBLE` reddens `main` and PRs alike.
- **Likelihood**: unknown. Counts held across JVMs, passes, suite positions and core counts on one
  builder (DR-01, DR-02, DR-11). **One `lunar-ci` record on one CPU model exists, and its 16 rows equal DR-16's (DR-16); no second model has been seen.** One
  concrete candidate is the saturated 800-size rows (DR-10).
- **Mitigation**: the comparison ships `report-only` (design §3.9), so neither can fail until `stability`
  prints `STABLE`. If a false rise appears afterwards, the same file is the recovery. A false failure of a
  workload check or of tooling is not covered by the switch, for the reason design §3.9 gives.
- **If it never clears**: `stability` can stay `NOT_YET` for any of these reasons. `lunar-ci` may only ever land on one CPU
  model, so no cross-model pair exists. Eligible pairs may be too rare inside `STABILITY_MAX_DEPTH`: at
  DR-21's rate and wait, a model share of about 5 % or less expects its first cross-model pair at or past the
  walk's end, and a stretch of source-heavy work earns none at all. Or counts really differ between models, which
  prints a `BREAK`. In each case Phase 5 stays open, MAINT-45-08 and -16 stay `Partial` and the feature
  stays `in_progress`. The records, the report-only diff and the series remain useful. A `BREAK` on a
  cross-model pair is the feature's answer to its premise, so the next step is a decision on the counter it
  names: drop that counter from the workload's counts, or fix the dependence. The condition is not
  weakened to close the feature.

### Risk 1.2: The stub counter reads a private platform field
- **Impact**: a platform upgrade that renames `stubEntriesLookupDurationsMsByIndexId` or changes its
  type throws in `StubLookupCounter`. One that keeps the field but stops recording leaves every stub
  counter at zero, and the required-counter check fails every row that requires one
  (`snapshot-annotated-*`, `completion-*`). Either way the job is red until fixed. A platform upgrade can also move stub counts legitimately.
- **Likelihood**: medium over a year of upgrades. Each upgrade PR runs the workloads, so it lands on
  the PR that caused it.
- **Mitigation**: the read is one function (design §3.1). Repoint it at the replacement. If the platform
  offers none, delete the `completion-*` rows, which read as `REMOVED` and are not compared, and drop
  `stub:lunar.global.decl` from `snapshot-annotated-*`'s required counters, which keeps `write`, `read` and
  `demand`. A row with no required counter is not allowed (MAINT-45-02).

### Risk 1.3: A PR with no baseline is not gated
- **Impact**: a rise merges with only a `::warning::`, which shows inside the step log and nowhere else.
  The merged value then becomes the next baseline, so nothing compares it again. This happens when a PR's
  merge-base has no record: its `build` is still running, was cancelled, or failed before storing. Every
  rise while `report-only` has the same outcome.
- **Likelihood**: medium for a PR pushed right after a merge; a later push or rerun gets the record.
- **Mitigation**: the warning says counts were not gated. Drift still shows in the series report, which
  is the only later view of it. Red here would fail PRs for a race they did not cause (design §10).

### Risk 1.4: A workload's edit can hide a rise
- **Impact**: `FIXTURE_CHANGED` skips the comparison for that workload. Renaming a workload does the same,
  reading as `ADDED` plus `REMOVED`.
- **Likelihood**: low, and either is a visible test-code edit in the PR.
- **Mitigation**: the lines print in the log. Accepted as the cost of MAINT-45-12.

### Risk 1.5: A PR can set its own gate mode
- **Impact**: `ci` reads the PR head's `gate-mode`, so a PR changing it to `report-only` passes its own
  rise.
- **Likelihood**: low, and it is a one-line edit in the PR's diff, the same trust acceptance rests on.
- **Mitigation**: none beyond review. Reading the merge-base's copy would stop a blocked PR carrying the
  recovery (design §10).

### Risk 1.6: Two PRs from one base can each pass
- **Impact**: each is compared with the shared merge-base, so a rise from their combination is not
  compared. `main` pushes store and never diff.
- **Likelihood**: low: the two must interact to move a count that neither moves alone.
- **Mitigation**: the series report shows the step. `stability` does not: the merges change `src/main`, so
  that pair is not eligible.

### Risk 1.7: Something else drains the platform recorders
- **Impact**: `getIntervalHistogram` resets, so another caller during a workload would lower a stub count.
- **Likelihood**: low, and **read, not executed**. The one other production reader is
  `IndexOperationAggregatesCollector.getMetrics`, reached from `FUStateUsagesLogger`, which stops unless
  `StatisticsUploadAssistant.isCollectAllowedOrForced`; that needs `isHeadlessStatisticsEnabled` in a
  headless run. `StatisticsStateCollectorsSchedulerProjectActivity` is not applicable in unit-test mode.
  The OTel reporter keeps histograms of its own.
- **Mitigation**: a drain would show as `WOBBLE` or a `stability` break before gating, and then as a false
  rise, which is Risk 1.1's recovery.

### Risk 1.8: A routine test named `*PerfRecord*` leaves the routine suite
- **Impact**: the exclusion silently drops it, as BUG-497 did for `*Corpus*`.
- **Likelihood**: low: the word is specific. `SweepNamingGuardTest` is written around one word and one
  exemption list, so a guard here is a change to that test, left out of this feature.
- **Mitigation**: design §2.1 states the rule.

## Design Gaps

### Gap 2.1: Scope items this plan narrows
- **Completion has no resolution count.** Scope lists resolutions, stub lookups and parser node visits
  as examples. Completion runs on a copy of the file, whose `LuaTypesSnapshot` the test cannot reach,
  so its counts are stub lookups only. A resolution count there needs a production hook, which the
  brief rules out. Parse has no stub counts, snapshot has both kinds, and resolve showed root
  resolutions only (DR-16).
- **The `performance/` suites do not become workloads.** They assert budgets for behaviour these
  workloads do not cover (candidate capping, cancellation), so retiring them is its own decision
  (MAINT-45-14).
- **A timing is weak evidence on one commit.** The same code on the same host varied widely
  (DR-09). Scope's flag is kept, set high enough to stay quiet on that spread. The series is the
  useful view of timings.

## Technical Debt & Future Work

- **TBD: a superlinearity check.** Each size's count gates on its own; nothing compares the
  ratio between sizes. A rise at any size already reds, so this adds only diagnosis.
- **TBD: colon completion on a bare receiver.** `b:<caret>` at end of file returned no items where
  `b.<caret>` returned them (DR-10). Not investigated and not filed; it may be a defect of its own.
- **TBD: stale keys in global completion.** `GlobalSymbolRankingService.funcKeyCache` takes `getAllKeys`,
  which keeps keys of overwritten content, and looks each one up (DR-18). A long session may therefore
  do more lookups per completion than its live globals need. Not measured outside these fixtures, and
  not filed.

## Pre-Implementation De-risking Tasks

Load-bearing premises: **read** = settled by source; **runtime** = needs execution. Probe code was
throwaway test source, on the libvirt builder (4 cores, Intel i7-7700), deleted after use. Commands below
omit the `builder-run libvirt` prefix. *The final probe* is `ZzMaint45ProbeTest` at `3654c261`, whose
production code is `960e67ff`'s: one test method running design §3.2's two passes over the fixtures and
order of design §4.1, with `resetWorkspace` before every row, no rebuild or teardown between passes, in
three JVMs (below, DR-01). Its command is `test --rerun --no-build-cache --tests '*ZzMaint45Probe*'`.

| ID | Premise (kind) | Action | Status |
|----|----------------|--------|--------|
| MAINT-45-00-DR-01 | Counts are deterministic across passes and JVMs (runtime) | The final probe in three JVMs: 4 cores with an existing `system-test`; 4 cores with `system-test` moved aside and `--no-daemon`, CI's cold state; `taskset -c 0`. `diff` the pass, workload, counts and outputs columns. Allocation and time from the earlier three-method probe at `960e67ff` | **done: confirmed for counts and outputs, refuted for allocation.** All three JVMs identical, and pass 1 identical to pass 2 in each. Values in DR-16. Control that must differ: DR-18's run without the rebuild. Not deterministic: allocated bytes (`parse-flat-500` 4107568 in pass 1, 3850520 in pass 3 of one JVM) and elapsed time. Neither gates. |
| MAINT-45-00-DR-02 | Test order does not move counts (runtime) | The final probe with a file `leak.lua` of 300 `function leaked_{i}() end` written back after each reset. Earlier, on the three-method probe at `960e67ff`: a throwaway first-run class and the same probe class in a full `test --rerun --no-build-cache` (BUILD SUCCESSFUL in 13m 55s; 3455 testcases in 532 classes) | **done: conditional.** Control that must move did: `completion-global-200`'s `stub:lunar.global.decl` 254 → 529 and `completion-global-800`'s `lookupItems` 488 → 379. So any file reaching the index moves counts, which `resetWorkspace` guards only for the source root. Full-suite counts equalled isolated counts as the first class to run (timestamp position 0 of 532) and at position 136, so counts carry no JVM warm-up. Hence a dedicated invocation (design §5). The full suite was not rerun on the final shape. |
| MAINT-45-00-DR-03 | Counters exist that touch no production code (runtime) | Read: `LuaParserNestingCostTest`, `LuaTypesSnapshot.rootResolutionCount` (`LuaTypes.kt:73`; the increment in `LuaTypeGraph.recordRootResolution` already exists, on memo misses only). Executed: `CountingPsiBuilder` and the reflective read of design §3.1 | **done: confirmed.** All counts in DR-01 came from test code. The aggregates are on by default and per index (`lunar.global.decl`, `lunar.luacats.class`). Their values scale with the fixture (DR-16). Completion's work happens on the file copy and other threads, so root-resolution counts do not reach it (Gap 2.1). |
| MAINT-45-00-DR-04 | Added CI time is small (runtime) | Whole-invocation wall clock of the final probe's two-pass method alone, `--no-daemon`, compile up to date: 4 cores from a cold `system-test`; `taskset -c 0`. Wall clock of the two passes | **done on the builder, and measured on `lunar-ci`.** `BUILD SUCCESSFUL in 1m 6s` at 4 cores, `2m 30s` at 1. The two passes took 30.4 s cold at 4 cores, 27.9 s with an existing `system-test`, 67.7 s at 1; rebuilding before completion rows only took 15.4 s. Expected on `n2-standard-4` (4 vCPU): about a minute, plus seconds for `ci`. On `lunar-ci`, Phase 2's merge run (`55c7f0a2`, Gitea run 31837, job 45247) logged the "Performance records" Gradle step at 1m 2s, and the PR run of that phase also 1m 2s; the step's `ci` part is not in that figure. |
| MAINT-45-00-DR-05 | A second put, `--producer`, `--item`, the suite name (read + earlier execution) | `loom-cli` v0.94.0 help; read `loom-delivery/durable/artifact/service.go` `compareExisting`, `identity.go` `ParseSuite`, `loom-cli/cmd/loom-cli/artifact.go:729`; read-only `resolve` of real records. No write | **done; no question for `loom.storage`.** A differing second put is an immutable conflict at exit 1, identical bytes and metadata are `already_present` at exit 0 (MAINT-40 DR-02 executed this on v0.89.0; `compareExisting` is unchanged in logic at loom `b2ede45d`). So a rerun cannot supersede a record, and `store_once` keeps the first. `--item` on a conformance put is refused (`artifact.go:729`), so `--no-item`. `--producer` is omitted: producer is stored metadata, so a per-run value would conflict every rerun. Real records for `72d43e13` show `item-attached: false`, `producer: null`, at `…/conformance/a3.ctrf.json` and `a3.corpus.ctrf.json`. `perf` passes `ParseSuite`, which rejects only an empty name, a period and bad segments, with no allow-list; `resolve --suite perf` exits 3, parsing the name. MAINT-40 DR-04's `lunar-ci-artifacts` policy covers `v2/commit/gitea.internetisalie.net/lunar/`, so it covers this key. The `put` itself is **not executed** for this suite. |
| MAINT-45-00-DR-06 | A depth-1 checkout can find its merge-base cheaply (runtime) | In a scratch repo, as the executor does: `git fetch -q --depth=1 origin 56b61762…` (PR #137's head), `git checkout -q FETCH_HEAD`; then `git fetch -q --unshallow --filter=blob:none origin 56b61762… '+refs/heads/main:refs/remotes/origin/main'`, with this host's `credential.helper store` | **done from this host, which has a stored credential; not run from a pod, and the anonymous reading was wrong.** 1.04 s, exit 0, `.git` 11 MB → 22 MB, not shallow, `git merge-base HEAD origin/main` = `5d5c84a5…`, the right commit. `git diff --no-renames --name-only` then runs without fetching blobs. The repository refuses anonymous access, so that run used the helper's credential, and the step's job has `GITEA_TOKEN` in its environment but no credential configured for git, which `git_env` supplies: see DR-22 for the probe on `lunar-ci`. The same form works against a `file://` origin, printing `warning: filtering not recognized by server, ignoring`, which is what TC 21 uses. |
| MAINT-45-00-DR-07 | The document is valid CTRF 0.2.0 (runtime) | The schema MAINT-40 design §4.2 pins; `jsonschema` 4.19.2 on a document built from a probe's output in the design §4.2 shape; then mutated copies | **done: confirmed.** `valid`, 4550 bytes for the design's workload set. Controls that must fail did: `environment.cpuModel` → `Additional properties are not allowed ('cpuModel' was unexpected)`; a test-level `counts` → `Additional properties are not allowed ('counts' was unexpected)`; duration `1.5` → `1.5 is not of type 'integer'`; `extra: [1]` → `[1] is not of type 'object'`. Hence `extra` on both levels. |
| MAINT-45-00-DR-08 | `GITHUB_EVENT_NAME` and `GITHUB_BASE_REF` reach a step (read) | `loom-gitea-runner/internal/app/run/runner_k8s.go` `githubContextEnv`; `gitea/services/actions/context.go:68,71`. API: `actions/runs` for PR #137 | **done by reading; not executed.** Scalars in the task context become `GITHUB_<KEY>`; `event_name` and `base_ref` are both strings in that context, as `sha` is, which the ingest step already uses. PR runs are at the PR head (`56b617620d`, `refs/pull/137/head`), not a merge commit. Phase 2's log shows the event. A wrong value takes the neither-stores-nor-compares path, which is green with one `::warning::` in the step log, so the check that catches it is Phase 2's `resolve` on the merge commit. |
| MAINT-45-00-DR-09 | Timing noise, to set the flag (runtime) | The second pass of four probe scenarios across two JVMs, same commit and host, per workload | **done.** Max/min from 1.0 to 3.73 (`parse-flat-500`, 26–97 ms). The largest absolute gap was 212 ms (`completion-global-200`, 148–360 ms), which a ×2 and +200 ms flag would fire on, so the flag is ×2 and +300 ms. First-pass times carry JIT cost: `snapshot-annotated-20` 1926 ms cold in pass 1, 51–105 ms in pass 2, so only the second pass is recorded. Noise on `n2-standard-4` is not measured. |
| MAINT-45-00-DR-10 | Completion fixtures produce items (runtime) | The final probe's completion rows, each control after a `resetWorkspace` | **done.** `completion-member-200` gave DR-16's items. `b:<caret>` in place of `b.<caret>`: 0, with `stub:lunar.global.decl` unchanged from DR-16's. Both the `local x = global_fu<caret>` and bare `global_fu<caret>` forms in `use.lua` gave DR-16's `lookupItems`. The 800-size `completion-global` and `completion-crossfile` rows saturate `GlobalSymbolRankingService.MAX_CANDIDATES` (500): which keys survive depends on `funcKeyCache`'s order, and their counts in DR-16 depend on that. |
| MAINT-45-00-DR-14 | The step's shape carries each `ci` outcome to the final step, and the switch stops a rise and a wobble without a workflow edit (runtime) | Under `/bin/sh -ec` (dash 0.5.12), the design §2.5 step and report line composed as the executor does, from `loom-gitea-runner` `steplog.go` at `c0ed40bb`: `( set -e` body `) \|\| RC=$?`, then `[ "$RC" = 0 ] \|\| exit "$RC"`. `gradlew` and `perf_ctrf.py` were stubs following the §3.6 exit contract; the real `ci` does not exist yet | **done: confirmed for the shape.** `report-only`: clean, a rise, a wobble: all `report: green`, job exit 0, with the `::warning::` printed. `gating`, rise and wobble: `::error::…`, job exit 1, step `rc=0` then final step `rc=1`. In `report-only`, `gradlew` exit 1, a tooling exit 1, and `gate-mode` holding `report_only` (exit 2): each `::error::…`, job exit 1. Control that must differ: with the `\|\| touch` deleted, a rise gives `step-done rc=1`, job exit 1 and no `::error::`, so the final step never ran. The same run shows why a step's body cannot rely on `set -e`: it sits left of `\|\|`. Not run on `lunar-ci`. |
| MAINT-45-00-DR-15 | The merge-base's accepted file can be read after the partial deepen (runtime) | In a scratch repo, DR-06's two fetches against `gitea.internetisalie.net` at `960e67ff`, with this host's `credential.helper store`. `git cat-file -p` of a blob from 40 first-parent commits back under `GIT_NO_LAZY_FETCH=1`, then `git show <that commit>:.gitea/workflows/build-plugin.yml`. `git ls-tree --name-only` and `git show` on `tooling/perf/accepted-increases.json`, absent there | **done from this host, with its stored credential; the lazy read from a pod is DR-22's.** The fetch set `remote.origin.promisor true` and `partialclonefilter blob:none`. Control: under `GIT_NO_LAZY_FETCH=1`, `fatal: Not a valid object name`, exit 128, so the blob was truly absent. `git show` then fetched it: 676 lines, exit 0. For the absent path, `ls-tree` printed nothing and `git show` exited 128 with `fatal: path … does not exist`, which is why `accepted_at` asks `ls-tree` first. |
| MAINT-45-00-DR-16 | Every row has a non-zero required counter (runtime) | The final probe's output, in all three JVMs of DR-01 | **done on one host.** Identical in both passes of each JVM. `parse-flat-{500,2000}`: `marks` 95123/380423, `advances` 10200/40800. `parse-callbacks-{8,12}`: `marks` 630/914, `advances` 59/87. `parse-parens-12`: `marks` 336, `advances` 29. `snapshot-annotated-{20,80,160}`: `write` 134/494/974, `read` 167/647/1287, `demand` 85/325/645, `stub:lunar.global.decl=1`, `stub:lunar.luacats.class=1`. `resolve-colon-{80,320}`: `write` 165/645, `read` 83/323, `demand=2`, `resolved` 80/320, no stub counters. `completion-member-{200,800}`: `lookupItems` 200/800, `stub:lunar.global.decl` 200/800. `completion-global-{200,800}` and `completion-crossfile-{200,800}`: `lookupItems` 200/488, `stub:lunar.global.decl` 254/521. Every completion row: `stub:lunar.luacats.class=1`. `flat(500)`, `flat(2000)`, `callbacks(8)` and `parens(12)` each parse with 0 `PsiErrorElement`s. Controls that must differ: `completion-member-200` without `local Builder = {}` reads 0 items and no stub counter (DR-19), and `completion-crossfile-*` without `globals.lua` reads `stub:lunar.global.decl=108` with 0 items at both sizes (DR-20), which is why that row's check is its output. **First `lunar-ci` record** (`55c7f0a2`, the merge run above), fetched read-only with `loom-cli artifact get` from `v2/commit/gitea.internetisalie.net/lunar/lunar/55c7f0a2e29fc5f03fd8bec4bdead1035edaa7b2/conformance/a3.perf.ctrf.json`: `environment.extra` is cpuModel `Intel(R) Xeon(R) CPU @ 2.80GHz`, vcpus 4, where the rows above were measured on an i7-7700. All 16 rows' `counts` and `outputs` equal the values above, and each row's `warm` equals its recorded values; `resolve-colon-80` has `write` 165, `read` 83, `demand` 2. That is one record on one other CPU model: it is not cross-host stability, which `stability` measures over many records. |
| MAINT-45-00-DR-11 | Counts do not depend on core count (runtime) | The final probe under `taskset -c 0`, compared with the 4-core output | **done: confirmed on one host.** Identical to the 4-core run. Control: DR-18's run without the rebuild shows the comparison can differ. Different CPU models are not tested, because the builder has one. MAINT-45-16 measures them on `main`. |
| MAINT-45-00-DR-18 | Two passes in one test method, with nothing between them, give equal counts (runtime) | These shapes, in design §4.1 order, each a test method after a fresh fixture: nothing between rows; `test.lua` emptied before each row; the source root emptied before each row; `resetWorkspace`. Then `resetWorkspace` before completion rows only | **done: refuted without the rebuild, confirmed with it.** Control: in every shape without the rebuild, `completion-global-200`'s `stub:lunar.global.decl` is 1055 in pass 1 and 1655 in pass 2, so every run would report `WOBBLE`. `GlobalSymbolRankingService.funcKeyCache` takes `StubIndex.getAllKeys` and looks up each key. With the source root emptied before the row, its live keys are the same as under `resetWorkspace`, where it reads 254, so the extra lookups are keys of content already gone. The pass difference of 600 equals the keys `global_func_200`…`799` that pass 1's next row introduces. With `resetWorkspace` pass 1 equals pass 2 on every row (DR-01), in 27.9 s; with the rebuild on completion rows only, the same counts in 15.4 s. A per-method rebuild, as `IndexedDocumentTest.before` does, leaves rows dependent on one another within a method. |
| MAINT-45-00-DR-19 | `members(n)`'s exact text produces its items (runtime) | The final probe's `completion-member-200` with and without `local Builder = {}` | **done.** With it, the text of design §4.1.1 gives DR-16's row values. Control without it: DR-16's control values. |
| MAINT-45-00-DR-20 | Row order is immaterial after `resetWorkspace`, and a crossfile row depends on its other file (runtime) | The final probe forward and reversed. Then `completion-crossfile-{200,800}` with and without `globals.lua`, and with `globals.lua` written by `addFileToProject`. And with `test.lua` holding `globals(800)` and no `globals.lua` in the source root | **done.** Reversed order equals forward on every row. Control that must differ: with no reset, row values depended on the preceding row (`completion-crossfile-200` 905 with no reset, 3310 with `test.lua` emptied alone). With `globals.lua`: DR-16's `lookupItems`. **Control that must change, did:** without it, DR-16's control values. With `test.lua` holding `globals(800)` and no `globals.lua`, the row returned the 800-size `lookupItems` of DR-16, so a crossfile row that follows a global row is measuring `test.lua`. `addFileToProject`: 0 items, the same as no file, because it writes to `temp:///root` (`LightTempDirTestFixtureImpl(false)`, `getTempDirPath`) outside the source root; the probe's exception read `File globals.lua already exists in /root`. Hence design §4.1's crossfile action. |
| MAINT-45-00-DR-21 | `stability` can be reached on real history (runtime) | Last 400 first-parent commits to `960e67ff` (2026-08-05 to 2026-10-30, 86 days), code commits by `DOCS_ONLY_PATH`. Measured commits are those with a successful `build-plugin.yml` push run on `main`, from `GET actions/runs`. A pair is eligible when no first-parent commit between them touches `COUNT_PATHS`. In a `--filter=blob:none` fetch of `main`, with `GIT_NO_LAZY_FETCH=1`: `git diff --no-renames --name-only -- <COUNT_PATHS>` and `git diff --quiet` the same way | **done on history; the CPU split is unknown.** 197 of the 400 are code commits. Of 196 adjacent code pairs, 66 leave `src/main` unchanged and 53 leave `COUNT_PATHS` unchanged, an upper bound, since only 91 code commits built successfully: one push builds its tip. On those, 19 of 90 pairs are eligible, 0.22 a day. Days to collect N eligible pairs, over every start in the history, median / p90 / max: N=3 1.3 / 38.7 / 49.2; N=5 14.0 / 49.7 / 53.3; N=10 54.1 / 69.2 / 69.2. Five pairs spanned up to 183 first-parent commits. Hence `STABILITY_PAIRS = 5` and `STABILITY_MAX_DEPTH = 400`. With a share `q` of jobs on one of two models, each job's model independent, and consecutive pairs sharing a record, the expected eligible pairs to a first cross-model pair is (1−q)/q + q/(1−q): 2 at q=0.5, 9.1 at 0.1, 19.1 at 0.05, 49 at 0.02, so about 23, 41, 87 and 223 days at this rate, the first floored by `STABILITY_PAIRS`. At q=0.05, 36 % of 20-record windows hold no model change. Warm-VM reuse probably makes adjacent jobs share a model, so these figures are optimistic. q=0 never clears. `--name-only` exited 0 with 73 paths on a pair 40 commits apart and none on `72d43e13`..`960e67ff`, with no blob fetched. Control: `--quiet` on that pair exited 128, `unable to read`, so the walk uses `--name-only`. The rate assumes future work resembles this window. These measurements used `COUNT_PATHS` without `settings.gradle.kts` and `.gitea/workflows/build-plugin.yml`, so the pair counts and the rate are upper bounds for the current list. |

| MAINT-45-00-DR-22 | A depth-1 checkout on `lunar-ci` can deepen and read lazily with `GITEA_TOKEN` (runtime) | A probe in PR 142, run 31846, job 45269, on the executor's depth-1 checkout, git 2.47.3, with `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=http.extraHeader GIT_CONFIG_VALUE_0="Authorization: token $GITEA_TOKEN"` in the environment (`GITEA_TOKEN` is in the job env, 40 characters) | **done on `lunar-ci`, by the supervisor.** `git ls-remote origin HEAD` 0; `git fetch -q --unshallow --filter=blob:none origin refs/heads/main` 0; `git merge-base FETCH_HEAD HEAD` 0; the lazy `git show FETCH_HEAD~5:tooling/ctrf/README.md` 0. The event is `pull_request` and the base ref `main`. `perf_ctrf.git_env` builds exactly that environment for the fetch and the lazy read, and TC 64-67 show it reaches git through the environment and never an argv. `loom-cli artifact get` from a pod is shown by DR-12's PR runs. |
| MAINT-45-00-DR-12 | A rise fails a real job in `gating`, and nothing does in `report-only` (runtime) | One CI mutant on PR 143: `gate-mode` set to `gating` and `builder.mark().drop()` added to `LuaParserUtil.parseSuffixedExpr` (`48d90746`), then reverted by commit (`578acff9`) | **done on `lunar-ci`, by the supervisor.** Mutant: run 31849, job 45278, failure; `perf: gate-mode gating`, `baseline 55c7f0a2… distance=0`, five `RISE` rows on `marks` (`parse-flat-500` 95123 → 99525, `parse-flat-2000` 380423 → 398025, `parse-callbacks-8` 630 → 658, `parse-callbacks-12` 914 → 954, `parse-parens-12` 336 → 353), and the final step's `::error::`. Reverted: run 31852, job 45284, success, `gate-mode report-only`, no `RISE`. Unmutated run 31847, job 45273, printed the same baseline line, so `artifact get` works from a pod. The mutant run also failed `LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall`, which passed in both other runs; whether the mutant caused that or the test is flaky is not established. |
| MAINT-45-00-DR-13 | `series` reads real records and prints them in commit order (runtime) | `perf_ctrf.py series --first-parent main` from this host with store credentials, at `main` = `3b4c819b`, with the default depth of 50. `LOOM_CLI` pointed at a wrapper that logged each call and refused all but `artifact resolve` and `artifact get` | **done on three records.** Exit 0, 178 lines, ending `unmeasured\t47`: the wrapper logged 50 `resolve` and 3 `get` calls on this run and no other verb. The head is below. Records at `55c7f0a2`, `a6131cdc` and `3b4c819b`, oldest first, all on `Intel(R) Xeon(R) CPU @ 2.80GHz`. None of the 43 count series moved across the 16 workloads, and no path in `COUNT_PATHS` differs between those commits. 14 of the 16 duration series moved; the largest spread was 40 ms (`completion-global-800`, 310, 270, 306). Three records on one CPU model say nothing about the cross-host premise, which `stability` measures. |

DR-13's output, head:

```text
completion-crossfile-200	duration	55c7f0a2e29fc5f03fd8bec4bdead1035edaa7b2	195	Intel(R) Xeon(R) CPU @ 2.80GHz
completion-crossfile-200	duration	a6131cdc649127db4f4e8fee29271d125fcbb17c	190	Intel(R) Xeon(R) CPU @ 2.80GHz
completion-crossfile-200	duration	3b4c819b9892428d4b9d370f5b4bbf3684de2a8f	187	Intel(R) Xeon(R) CPU @ 2.80GHz
completion-crossfile-200	stub:lunar.global.decl	55c7f0a2e29fc5f03fd8bec4bdead1035edaa7b2	254
completion-crossfile-200	stub:lunar.global.decl	a6131cdc649127db4f4e8fee29271d125fcbb17c	254
completion-crossfile-200	stub:lunar.global.decl	3b4c819b9892428d4b9d370f5b4bbf3684de2a8f	254
completion-crossfile-200	stub:lunar.luacats.class	55c7f0a2e29fc5f03fd8bec4bdead1035edaa7b2	1
completion-crossfile-200	stub:lunar.luacats.class	a6131cdc649127db4f4e8fee29271d125fcbb17c	1
completion-crossfile-200	stub:lunar.luacats.class	3b4c819b9892428d4b9d370f5b4bbf3684de2a8f	1
```

The plan fills **DR-17** during delivery, with the `stability` output that opens Phase 5. A figure that exists only
after a phase merges, such as DR-04's `lunar-ci` duration, goes in the next phase's PR.

## Test Case Gaps

- Step order and the sentinels in `build-plugin.yml` have no automated case: no unit test reaches a
  workflow file. Design §2.5 is checked by reading the step against it, plus Phase 3's single CI mutant.
  DR-14 executed the step's shape under the executor's guards.
- That a `put` under suite `perf` succeeds from `lunar-ci` is first shown by Phase 2's merge.
- `gating` on a real job is exercised only by Phase 3's CI mutant until Phase 5 turns it on.
- `accepted_at`'s lazy `git show` has not run from a pod: DR-15 ran it from this host, and DR-22's probe ran the same lazy read from a pod, but `accepted_at` itself has not run there. Phase 3 cannot reach
  it: that PR creates the file, so it is absent at its merge-base. The first PR after Phase 3's merge reaches
  it, and a failure there is a tooling failure, red in either mode.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
