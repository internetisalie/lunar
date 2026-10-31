---
id: "MAINT-50-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-50"
folders:
  - "[[features/maint/50-supervise-implement-per-test-gate/requirements|requirements]]"
---

# MAINT-50: Risks & Gaps

## Critical Risks

### Risk 1.1: A test that fails on the builder but passes on `lunar-ci`
- **Impact**: the baseline comes from CI and the phase run from the builder, so an environment-only
  failure reads as `NEWLY_FAILING` and fails a phase that broke nothing.
- **Likelihood**: low at one commit (MAINT-48-00-DR-12). MAINT-48 DR-06 compared two *builder* runs,
  so it says nothing about the `lunar-ci` image (its JDK, fonts, Lua, `$HOME`). BUG-539 shows the two
  environments do differ in ways a green builder suite cannot see.
- **Mitigation**: the dispute rule (design §4.2) reruns the class at `$BASE`, then the full suite if
  the class passes alone, because a single-class run cannot settle an order-dependent failure (BUG-539).

### Risk 1.2: Gradle rewords the failure line
- **Impact**: a red run classes as `infra`, so the gate stops instead of judging it.
- **Likelihood**: low per upgrade. The line is Gradle 9.7.1's.
- **Mitigation**: the failure is loud and fails closed. Requirements TC 3 fixes the expected text,
  and a real red log shows the new wording.

### Risk 1.3: A builder collision reads as a red run
- **Impact**: two runs on one VM give mass spurious failures, which class `red` and fail the phase.
- **Likelihood**: low for sessions that take the lock.
- **Mitigation**: `unit_gate.sh` holds the per-backend lock file across the run and its copy-back, and
  `builder-run` takes the same file. A bare `gce-builder.sh run` from another session takes no lock
  and is not stopped.

### Risk 1.4: A killed client frees the lock while Gradle runs
- **Impact**: the next run collides with the orphan.
- **Likelihood**: low with `run_in_background`.
- **Mitigation**: `infra-other` routes through `wait` (DR-08).

## Design Gaps

None open. Each question from the filed scope is answered by a DR row below and folded into the design.

## Technical Debt & Future Work

- **`:redisIntegrationTest` returns to the gate when BUG-17 is fixed.** Until then, and for
  `:integrationTest` always, a phase that deletes such tests is not caught.

## Pre-Implementation De-risking Tasks

Load-bearing premises: **read** = settled by source; **runtime** = needs execution. Every row is
`done`. The `MAINT-48-00-*` rows were measured under MAINT-48 on 2026-10-30 at `cf6cdf15` unless the
row says otherwise. The `MAINT-50-00-*` rows were run on 2026-10-31 on the libvirt builder at
`c9eb7512` plus the throwaway probe edits named in each row, each reverted with `scratch_end`.

| ID | Premise (kind) | Action | Status |
|----|----------------|--------|--------|
| MAINT-48-00-DR-08 | Which agents run a baseline step (read) | `grep -n -i baseline .claude/agents/supervisor.md .agents/agents/supervisor.md` | **done.** No match; only `supervise-implement` step 2 records a baseline (`SKILL.md:28-33`). |
| MAINT-48-00-DR-12 | A test's status on `lunar-ci` matches its status on the builder (runtime) | At `831f3cb3`: `fetch` that commit; in a worktree at that sha, `test --rerun --no-build-cache`, copy back, `assemble`, `diff` | **done: confirmed for one commit.** `fetch` printed `distance=0`; the builder run was `BUILD SUCCESSFUL in 10m 31s`. `diff` exited 0 and printed only `EXECUTED	3447	3447`, so no class goes on the skill's "known to differ" list. |
| MAINT-48-00-DR-13 | Which tasks give the build baseline without running a test (runtime) | On the builder: `build --dry-run`; `build -x test -x integrationTest -x redisIntegrationTest -x koverVerify --dry-run`; then a real `assemble testClasses lintDocs` | **done.** `build` schedules `:test`, `:integrationTest`, `:redisIntegrationTest`, `:koverVerify`, `:buildPlugin` and `:lintDocs`. The `-x` form also drops `:compileTestKotlin`, so it is rejected. The `assemble` set runs no test task. **Superseded by MAINT-50-00-DR-03** for the task set. |
| MAINT-50-00-DR-01 | A red run still leaves complete, assemblable results, and a text marker identifies it (runtime) | Probe: `fail("maint-50 probe")` first in `LuaTaskUtilTest.testNewAppBackgroundTaskRunsAction`; `PopularListParserTest.kt` deleted; `@org.junit.Ignore` on `LuaCompilerProbeTest.remediationTextMatchesSpec`. `builder-run libvirt test --rerun --no-build-cache > log 2>&1`, both `rsync`s, `assemble`, `diff` against `fetch --commit c9eb7512` | **done: confirmed.** Run exit 1; log `3448 tests completed, 1 failed, 5 skipped`, `Execution failed for task ':test'.`, `> There were failing tests. See the report at: file:///home/builder/lunar/build/reports/tests/test/index.html`, `BUILD FAILED in 10m`. Both `rsync`s and `assemble` exit 0. `diff` exit 1: `NEWLY_FAILING` for the red test, `NEWLY_SKIPPED` for the ignored test, three `REMOVED	passed` lines for the deleted class, `EXECUTED	3447	3443`, `EXECUTED_DROP	4`. 3443 = 3448 − 5, so the suite ran to the end. The deleted class's XML was absent. |
| MAINT-50-00-DR-02 | Other failures can be told from a red run; the task-name line cannot do it (runtime) | Probes: (1) `val maint50Broken: Int = "x"` appended to `LuaTaskUtilTest.kt`, full `test`; (2) `Runtime.getRuntime().halt(7)` in one of its tests, `--tests '*LuaTaskUtilTest*'`; (3) (2) plus the DR-01 red test first, same filter; (4) `LUNAR_BUILDER_HOST=127.0.0.1`, full `test`. Then the classifier on every log | **done.** (1) exit 1, `Execution failed for task ':compileTestKotlin' …`, `BUILD FAILED in 2s`. (2) exit 1, `Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').`, `> Test process encountered an unexpected problem.`, `> Process 'Gradle Test Executor 5' finished with non-zero exit value 7`, and a `TEST-*.xml` for the class on the builder. (3) exit 1, `LuaTaskUtilTest > testNewAppBackgroundTaskRunsAction FAILED`, the same crash text, no failing-tests line; the builder's XML has `tests="2" skipped="1" failures="1"`, so the test that killed the JVM reads as skipped. (4) exit 255, `rsync error: unexplained error (code 255)`, no Gradle output. Classifier: DR-04's run `green`, DR-01's `red`, (1) `infra-compile`, (2) and (3) `infra-other`, (4) `infra-transport`. So `':test'` alone would class (2) as red, with partial XML; exit status alone cannot separate (1) from a red run. |
| MAINT-50-00-DR-03 | Which set counts every warning `build` would, at what cost, and whether warm counts mean anything (runtime) | `assemble testClasses integrationTestClasses redisIntegrationTestClasses lintDocs --rerun-tasks --no-build-cache` twice; the same warm; `buildPlugin …` in place of `assemble` cold; `grep -c '^w: '`. Mutant: `fun maint50Probe(): Int? = "a"?.length` appended to `src/integrationTest/…/IdeProductResolver.kt`, run under `assemble testClasses lintDocs` and under the wide set, both cold | **done.** Cold 672 twice (54 `src/main`, 618 `src/test`), `BUILD SUCCESSFUL in 1m 38s` and `in 50s`, identical sorted lines. No `.java` warning; `:compileTestJava`, `:compileIntegrationTestJava`, `:compileRedisIntegrationTestJava` are `NO-SOURCE`. Warm: **0**, `15 up-to-date`; warm with the mutant below **1**, warm reverted 0. With `buildPlugin`: 672, identical lines, and `:buildSearchableOptions SKIPPED` (`buildSearchableOptions = false`, `build.gradle.kts:123`). Mutant: narrow set 672, wide set 673 with `w: file:///home/builder/lunar/src/integrationTest/…/IdeProductResolver.kt:193:31 Unnecessary safe call …`. `val neverUsed = 1` raised neither set: an unused local is not a compiler warning here. |
| MAINT-50-00-DR-04 | An earlier phase's deliberate losses reappear at each later gate against one baseline, and the rule handles them (runtime) | Second full run from DR-01's tree against the same `before` document: red edit removed, `LuaRocksBridgeFilesTest.kt` also deleted. Design §3.1's loss rule with the phase 1 list, the phase 1 + 2 list, an empty list, and a list holding a truncated testId | **done.** `BUILD SUCCESSFUL in 10m 29s`, **`diff` exit 1** on a green run: `NEWLY_SKIPPED` for phase 1's ignored test, five `REMOVED	passed` lines, `EXECUTED	3447	3441`, `EXECUTED_DROP	6`. Losses: phase 1 list 2 lines (phase 2's deleted class), both lists 0, empty list 6, truncated testId still prints that test. The failure rule prints nothing here and the red test on DR-01's `diff`. |
| MAINT-50-00-DR-05 | `rsync` creates only the last missing directory (runtime) | 2026-10-30, rsync 3.4.1, into `w/results/` with and without `w/` | **done.** Exit 11 (`mkdir … failed: No such file or directory`) when `w/` is missing; exit 0 when `w/` exists or after `mkdir -p w/results`. Hence the `mkdir -p` in `unit_gate.sh suite`. |
| MAINT-50-00-DR-06 | Whether `:integrationTest` alone is green at `c9eb7512`, needs no `:test`, and what `:koverVerify` and `build` do (runtime) | `integrationTest redisIntegrationTest --dry-run`, the same with `koverVerify`; `builder-run libvirt integrationTest --rerun --no-build-cache`; `builder-run libvirt build`; `test --rerun --no-build-cache --tests '*LuaTaskUtilTest*' koverVerify -x integrationTest -x redisIntegrationTest` | **done.** The first dry run lists no `:test` and no kover verify task; adding `koverVerify` adds `:test`. `integrationTest` alone: **`BUILD SUCCESSFUL in 1m 7s`**, no `:test` task. `build`: `BUILD FAILED in 1m 19s` at `:redisIntegrationTest` (BUG-17), before `:test` or `:koverVerify`. `:koverVerify` with two tests of coverage: `BUILD SUCCESSFUL in 26s`; `build.gradle.kts` has no `kover {}` block. |
| MAINT-50-00-DR-07 | `unit_gate.sh` holds its invariants, and a crashing `diff` is caught (runtime) | `junit_ctrf.py diff` on a missing, a truncated and a no-`environment` document. `python3 -m unittest test_unit_gate` and `python3 tooling/ctrf/mutants_unit_gate.py` at `81fc4eda`; `suite` with an untracked file present. On the builder, a scratch run of the same script's logic: `suite … --tests '*LuaTaskUtilTest*'` green, red, then compile error, `LUNAR_BUILDER_HOST=127.0.0.1`, `BLD=builder@127.0.0.1:lunar` and an empty-results `BLD` over an existing document; an `RSH` wrapper running `flock -n`; a base worktree at `c9eb7512` running `LuaRecursiveReferenceTest`. At `462a4c9c` on the builder: `suite` green, `integration`, `count` | **done.** `diff`: exit 1, 0 bytes of stdout, `FileNotFoundError`, `JSONDecodeError`, `KeyError: 'environment'`. Tests `OK`; mutants `bad 0 of 42`, among them the `EXECUTED` check removed, which printed `PASS` on the missing document. Builder: green exit 0; red exit 10; each failure exit 20 with the document gone; wrapper `peer-blocked` for both copy-backs, `peer-got-lock` afterwards; base worktree add, green, remove all exit 0. `462a4c9c`, the script before the dirty-tree refusal: `class=green`, `integration` `BUILD SUCCESSFUL in 1m 11s`, `warnings=672`, `violations=0`. Dirty tree on the real worktree: `REFUSED: the working tree is dirty; …`, exit 2. A fresh base worktree prints no `git status --porcelain` line. |
| MAINT-50-00-DR-08 | A killed client leaves Gradle running, and `gradlew --status` sees it (runtime) | `timeout 75 unit_gate.sh suite libvirt <dir> kill --tests '*lang*'`, then the §2.2 status command, then `wait libvirt`; then `GATE_WAIT_LIMIT=0 LUNAR_BUILDER_HOST=127.0.0.1 unit_gate.sh wait libvirt` | **done.** Client exit 124. Status: `985854 BUSY     9.7.1`. `wait` exit 0 after 264 s. The log classes `infra-other`; no document. With no reachable builder: `STOP: builder unreachable`, exit 20. Idle sample: `985854 IDLE     9.7.1`. |
| MAINT-50-00-DR-09 | `ktlintCheck`'s output, green and with violations (runtime) | `ktlintCheck` on the tree; again with whitespace violations in `LuaTaskUtilTest.kt` | **done.** Green: `BUILD SUCCESSFUL in 33s`, no violation line. Red: exit 1, `BUILD FAILED in 18s`, lines of the form `/home/builder/lunar/src/test/…/LuaTaskUtilTest.kt:8:48: Unnecessary long whitespace (standard:no-multi-spaces)`. |

## Not Established

- **The whole sequence on a real feature.** Each step ran against probes, not through a supervised phase.
- **A full-length dispute run.** `suite` and `failures` are each exercised, not together at full length.
- **An SSH drop during a run, and the `gce` backend.** Only libvirt ran. `wait`'s command is the same on
  both by reading; `/opt/jdk` and `lunar` come from `config.sh`.
- **Test JVM and `gradlew --status` agreement at the moment a build ends.** `wait` was seen only to clear
  after one kill.
- **DR-12 beyond one commit.** Risk 1.1 covers later divergence.

## Test Case Gaps

TC 20 reads the real store, so a later record for `cf6cdf15` would move it. TC 19 is a walk-through,
not an automated test: `fresh` is tested alone (TC 17), and the skill text that follows it is prose.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
