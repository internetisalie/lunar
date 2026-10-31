---
id: "MAINT-50"
title: "50: Supervise-Implement Per-Test Gate"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-50: Supervise-Implement Per-Test Gate

## The gap

Every feature's gate is judged against a baseline. The `supervise-implement` skill's step 2 records
that baseline by running the full unit suite on the builder before work starts: about 14 minutes,
on the same single-tenant VM the work then needs. The result is a count, so a phase that fixes one
test and breaks another compares equal. [[MAINT-48]] stores CI's per-test results for each commit
and provides `junit_ctrf.py fetch` and `diff`. Nothing in the skill uses them yet.

## Scope

- **Baseline by fetch.** The skill's step 2 fetches MAINT-48's record for the branch's merge-base
  with `main`. It runs the suite only when no record exists.
- **Judge each phase per test.** The phase gate is `junit_ctrf.py diff` of the baseline against the
  phase's run. No test that passes in the baseline may fail, be skipped or disappear unless the
  phase's plan says so.
- **Stop paying for tests in the build baseline.** The step-2 warning count comes from a task set
  that runs no tests.

## Out of Scope

- **The `supervisor` agent**: it has no baseline step (DR-08 below).
- **MAINT-48's tooling** (`fetch`, `diff`, their line semantics and exit codes): used as is.

## Starting material

This draft of the skill edit was written while planning MAINT-48 and moved here unreviewed. Its last
review (MAINT-48 round 3) failed on the open questions below. Section numbers in it (§2.1, §3.8) are
MAINT-48 design's; "design §2.7" and "Phase 5" mean this draft and the phase that applies it. Its DR-12 and DR-13 are the evidence rows below.

`.agents/skills/supervise-implement/SKILL.md` is gitignored (`.gitignore:13`, `/.agents/`). Phase 5
makes these changes and no others:

- `SKILL.md:11`: `test_failures: Y` becomes `unit_baseline: <fetched sha and distance, or "builder run">`.
- `SKILL.md:30`: `tooling/gce-builder/gce-builder.sh run build 2>&1 | tail -20` becomes
  `tooling/gce-builder/gce-builder.sh run "assemble testClasses lintDocs"`. `build` also runs
  `:test`, `:integrationTest`, `:redisIntegrationTest` and kover (DR-13), so keeping it would keep
  the 14 minutes this feature removes. The new set compiles main and test sources and runs no test
  task (DR-13). The warnings it reports come from the same compile tasks.
- `SKILL.md:31`: the line `tooling/gce-builder/gce-builder.sh run test 2>&1 | tail -20` is deleted.
- `SKILL.md:32`: `| tail -20` is dropped, because it hides the exit status.
- `SKILL.md:33`: "the warning, failure, and violation integers" becomes "the warning and violation
  integers"; the unit-test baseline is the document below.
- Step 2 gains the block below, appended verbatim.

`$BLD`/`$RSH` are defined in MAINT-37's implementation plan ("Copying results back from the
builder"). The block names that section rather than copying it. Each numbered command is one Bash
call, because shell state does not persist between calls.

```text
- Unit-test baseline (MAINT-48), per test, not a count:
  0. `grep -q test-filter.json build.gradle.kts`. Exit 1 means the branch predates MAINT-48
     Phase 1 and its runs write no sidecar: rebase it onto origin/main before starting, or stop and
     report. There is no fallback to a failure count.
  1. `git fetch origin main` then `git merge-base HEAD origin/main`; call the printed sha $BASE.
  2. `( set -a; . ~/.config/loom/loom.artifacts.env; set +a; python3 tooling/ctrf/junit_ctrf.py fetch --commit <$BASE> --out build/unit-baseline/before.ctrf.json )`.
     Never print the env file's values.
     - exit 0: the baseline is that file. Record its `fetched <sha> identity=<set> distance=<n>` line.
     - exit 3 (no record for a code-identical ancestor): run the suite once on the builder,
       `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`; after BUILD SUCCESSFUL,
       `mkdir -p build/unit-baseline/results`, then
       `rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ build/unit-baseline/results/`, then
       `rsync -az -e "$RSH" "$BLD"/build/test-results/test-filter.json build/unit-baseline/test-filter.json`, then
       `python3 tooling/ctrf/junit_ctrf.py assemble --results build/unit-baseline/results --filter build/unit-baseline/test-filter.json --out build/unit-baseline/before.ctrf.json`.
     - any other exit: stop and report the output; dispatch nothing.
- Phase test gate: the phase's suite run is
  `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`, never a cached or
  `--tests` run. After BUILD SUCCESSFUL:
  `mkdir -p build/unit-gate/results` (rsync creates only the last missing directory, and nothing
  else creates build/unit-gate/), then
  `rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ build/unit-gate/results/`, then
  `rsync -az -e "$RSH" "$BLD"/build/test-results/test-filter.json build/unit-gate/test-filter.json`, then
  `python3 tooling/ctrf/junit_ctrf.py assemble --results build/unit-gate/results --filter build/unit-gate/test-filter.json --out build/unit-gate/after.ctrf.json`, then
  `python3 tooling/ctrf/junit_ctrf.py diff build/unit-baseline/before.ctrf.json build/unit-gate/after.ctrf.json`.
     - At exit 0, 1 and 4 alike, every REMOVED, NEWLY_SKIPPED and `STATUS\t…->skipped` line must
       name a test that the phase's plan deletes or disables. Any other such line is a FAIL.
     - exit 0: PASS unless the rule above fails.
     - exit 1: FAIL if there is any NEWLY_FAILING, `ADDED\tfailed` or `OUT_OF_SET\tafter\tfailed`
       line; quote them all. If NEWLY_SKIPPED lines are the only cause, the rule above decides.
       A line the implementer disputes as environmental (the baseline ran on lunar-ci, the branch
       on the builder), or one in a class on the list below, is settled by running that class at
       $BASE on the builder. Run it from a worktree at $BASE under ~/.cache/claude-worktrees/,
       where the tracked `test -> ../test` link resolves; anywhere else the link dangles and the
       external-fixture classes fail. The command is
       `run "test --rerun --no-build-cache --tests '<class>'"`. If the class fails there too, the
       failure is pre-existing; if it passes there, the FAIL stands.
       Classes known to differ between lunar-ci and the builder (DR-12): <none>.
     - exit 4: the executed count fell. The rule above decides.
     - exit 2: stop and report; the documents are not comparable.
```

In step 6, item 3's "Verify that new failure metrics do not exceed your recorded baseline
constraints" gains: "For tests, the verdict is the `diff` exit above, not a failure count."

Evidence measured on 2026-10-30 at `cf6cdf15`, under its MAINT-48 IDs:

| ID | Premise (kind) | Action | Status |
|----|----------------|--------|--------|
| MAINT-48-00-DR-08 | Which agents run a baseline step (read) | `grep -n -i baseline .claude/agents/supervisor.md .agents/agents/supervisor.md` | **done.** No match; only `supervise-implement` step 2 records a baseline (`SKILL.md:28-33`). |
| MAINT-48-00-DR-12 | A test's status on `lunar-ci` matches its status on the builder (runtime; the risk below) | After TC 10: `( set -a; . ~/.config/loom/loom.artifacts.env; set +a; python3 tooling/ctrf/junit_ctrf.py fetch --commit <TC 10's sha> --out build/unit-ci/before.ctrf.json )`. In a worktree at that sha under `~/.cache/claude-worktrees/`, `run "test --rerun --no-build-cache"`, copy back and `assemble` to `build/unit-ci/after.ctrf.json` (the design §2.7 gate commands with `unit-ci`), then `diff` | **done at `831f3cb3`: confirmed for one commit.** `fetch` printed `distance=0`; the builder run was `BUILD SUCCESSFUL in 10m 31s`. `diff` exited 0 and printed only `EXECUTED	3447	3447`, so no `STATUS`, `NEWLY_*` or `REMOVED` line and no class for the "Classes known to differ" list in design §2.7. |
| MAINT-48-00-DR-13 | Which tasks give the step-2 build baseline without running a test (runtime) | On the builder: `build --dry-run`; `build -x test -x integrationTest -x redisIntegrationTest -x koverVerify --dry-run`; then a real `assemble testClasses lintDocs` | **done.** `build` schedules `:test`, `:integrationTest`, `:redisIntegrationTest`, `:koverVerify`, `:buildPlugin` and `:lintDocs`. The `-x` form also drops `:compileTestKotlin`, so test-source warnings would no longer be counted, and it is rejected. `assemble testClasses lintDocs` ran `:compileKotlin`, `:compileJava`, `:compileTestKotlin`, `:testClasses` and `:lintDocs`, no test task, `BUILD SUCCESSFUL in 1s` (compiles up to date). |

rsync 3.4.1 into `w/results/` exits 11 (`mkdir … failed: No such file or directory`) when `w/` is
missing, and exits 0 when `w/` exists or after `mkdir -p w/results`. The draft's gate `mkdir -p`
rests on this.

The risk the draft was built around:

### Risk: a test that fails on the builder but passes on `lunar-ci`
- **Impact**: the baseline comes from CI and the phase run from the builder, so an environment-only
  failure reads as `NEWLY_FAILING` and fails a phase that broke nothing.
- **Likelihood**: low at one commit (DR-12). MAINT-48 DR-06 compared two *builder* runs, one with CI's
  filter, so it says nothing about the `lunar-ci` image (its JDK, fonts, Lua, `$HOME`). BUG-539
  shows the two environments do differ in ways a green builder suite cannot see.
- **Mitigation**: the draft's dispute rule reruns the class at the merge-base on the
  builder, which settles any single case.

## Not established

- **a. Copy-back after a red run.** The draft copies results back only after BUILD SUCCESSFUL, but
  any failing test ends in BUILD FAILED, because the suite does not set `ignoreFailures`. How to
  tell test failures from compile or transport failures, and copy back in the test-failure case.
- **b. One task set for warnings.** `assemble testClasses lintDocs` skips
  `:compileIntegrationTest*`, `:compileRedisIntegrationTest*` and `:buildSearchableOptions`, which
  `build` runs. Which task set both the baseline and each phase use for their warning counts.
- **c. Baseline per feature, gate per phase.** How REMOVED and skipped lines left by an earlier
  phase's deliberate deletions count at a later phase's gate.

## Dependencies

- [[MAINT-48]] merged and storing records from CI.
