---
id: "MAINT-50-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-50"
folders:
  - "[[features/maint/50-supervise-implement-per-test-gate/requirements|requirements]]"
---

# Technical Design: MAINT-50 — Supervise-Implement Per-Test Gate

No Kotlin, no `plugin.xml`, no threading context. The deliverables are a tracked script with its tests
and a CI step, then an edit to one gitignored skill file. Evidence is in `risks-and-gaps.md` by DR ID.

## 1. Architecture Overview

### Current State
`.agents/skills/supervise-implement/SKILL.md:28-33` runs `build`, `test` and `ktlintCheck` through
`| tail -20` and records integers. Step 6 item 3 (`:82`) repeats "the project's build and lint tools".

### Prior Art in This Repo
| Component | Relation |
|-----------|----------|
| `tooling/ctrf/junit_ctrf.py` `fetch`, `assemble`, `diff` (`:207`, `:164`, `:276`) | Used unchanged. |
| `test-filter.json` (`build.gradle.kts:438`) | Used unchanged. |
| `~/.cache/claude-scratch/lunar/bin/builder-run` | Shares its lock file. `unit_gate.sh` cannot call it: its lock ends with the run, and nesting it inside the same lock deadlocks. |
| `$BLD`, `$RSH` (MAINT-37 `implementation-plan.md`, "Copying results back from the builder") | Cited, not copied. |
| MAINT-37's "A read needs a completed run and a completed copy-back" | The rule `suite` enforces. |

### Target State
One baseline per feature, fetched or assembled into `build/unit-baseline/before.ctrf.json`. One gate
per phase: run and copy back under one lock, `diff`, judge the lines. The judge is a frozen copy.

## 2. `tooling/ctrf/unit_gate.sh`

Mode 755. Run from a checkout's root. Proven text: branch `proof/maint-50-gate` at `81fc4eda`, local
only, with `test_unit_gate.py` and `mutants_unit_gate.py` (DR-07).

### 2.1 Which copy runs
Baseline item 2 freezes `unit_gate.sh`, `junit_ctrf.py` and `loom_store.py` from `origin/main` into
`build/unit-baseline/tools/`, so a phase cannot edit its own judge and a branch older than the script
still has it. The script finds `junit_ctrf.py` beside itself. `tooling/gce-builder/gce-builder.sh` is
the checkout's, or `$GATE_BUILDER` if set (tests only).

### 2.2 Subcommands

| Subcommand | Effect | Output and exit |
|------------|--------|-----------------|
| `suite <backend> <dir> <name> [gradle args]` | Refuses a dirty tree: non-empty `git status --porcelain`, untracked files included, gitignored paths not. Then, under the lock: removes `<dir>/<name>.ctrf.json`, `<name>.head`, `test-filter.json`, `run.log`; writes `git rev-parse HEAD` to `<name>.head`; runs `test --rerun --no-build-cache [args]` into `run.log`; classes it; for `green`/`red` copies back with `rsync -az --delete -e "$RSH" "$BLD"/build/test-results/test/ <dir>/results/` and `rsync -az -e "$RSH" "$BLD"/build/test-results/test-filter.json <dir>/`; `assemble` to `<dir>/<name>.ctrf.json`. `$BLD`, `$RSH` required | Prints `class=<class>`. 0 green, 10 red, 20 anything else |
| `judge <before> <after> <planned> <diff>` | Requires all inputs and `<after minus .ctrf.json>.head` equal to `git rev-parse HEAD`; writes `diff` output to `<diff>`; `verdict` | As `verdict` |
| `verdict <planned> <diff>` | §3.1 | `PASS` 0; `FAIL` and the lines, 1; refused 2 |
| `class <log>` | §3.2 | Prints the class, 0 |
| `count <backend> <dir>` | Under the lock: `assemble testClasses integrationTestClasses redisIntegrationTestClasses lintDocs --rerun-tasks --no-build-cache` into `warnings.log`, then `ktlintCheck` into `ktlint.log` | `warnings=<n>`, `violations=<n>`, 0; 20 per §3.3 |
| `integration <backend> <dir>` | Under the lock: `integrationTest --rerun --no-build-cache` into `integration.log` | 0 on `^BUILD SUCCESSFUL`, else 20 |
| `failures <document>` | | One failed testId per line, 0 |
| `fresh <dir>` | Requires `before.ctrf.json` and `base_commit` in `<dir>`, and `base_commit` equal to `git merge-base HEAD origin/main` | 0 fresh, 1 not |
| `wait <backend>` | Polls `cd lunar && JAVA_HOME=/opt/jdk GRADLE_USER_HOME=/opt/cache/gradle ./gradlew --status` through `gce-builder.sh shell` every `${GATE_WAIT_POLL:-20}` s, up to `${GATE_WAIT_LIMIT:-1500}` s | 0 when no `BUSY` and `IDLE`, `STOPPED` or `No Gradle daemons are running` appears; 20 on `BUSY` at the limit, a non-zero `shell`, or other output |

Every subcommand exits 2 on too few arguments, and `suite` on an unset `$BLD`/`$RSH` or a dirty tree;
`class`, `judge`, `verdict` and `failures` on a missing input file; an unknown name likewise. Each
prints `REFUSED: …`. `fresh` exits 1, not 2, on a missing file: that is its "not fresh" answer.

### 2.3 Invariants

Each is broken by a named mutant in `mutants_unit_gate.py`.

- A run and its copy-back hold `~/.cache/claude-scratch/lunar/builder-<backend>.lock` throughout.
- The Gradle child does not inherit the lock's file descriptor (`9>&-`).
- No older document survives a failed step: `suite` removes outputs first, and each copy-back or
  `assemble` failure exits 20.
- `diff`'s exit code never decides alone: 0, 1 and 4 go to `verdict`, others are refused, and
  `verdict` refuses a file with no `^EXECUTED\t[1-9]` line.
- Any unrecognised outcome stops: §3.2's fall-through, `integration`'s and `wait`'s final branches.
- A document judged belongs to the HEAD under review (`<name>.head`), and that commit holds the code
  measured: the builder syncs the working tree, so `suite` refuses a dirty one before any run.

## 3. Parse Formats

### 3.1 Verdict
`diff`'s line format is MAINT-48 design §3.8.

- **Failure lines**, `^(NEWLY_FAILING\t|ADDED\tfailed\t|OUT_OF_SET\tafter\tfailed\t)`: never planned
  away. Only the dispute rule clears one.
- **Loss lines**, `^(REMOVED|NEWLY_SKIPPED|STATUS\t[a-z]+->skipped|OUT_OF_SET\tbefore)\t`. `OUT_OF_SET
  before` is a test a phase's filter change excluded: `diff` does not count it as removed, so a phase
  could otherwise hide a test by excluding it.
- A loss's testId is its last tab-separated field. It is accounted for if a line of `<planned>` equals
  it, or ends in `.` and is a prefix of it. An empty `<planned>` must work (awk `FILENAME==ARGV[1]`,
  not `NR==FNR`).
- `PASS` only if no failure and no unaccounted loss. `EXECUTED_DROP` is not judged: a drop can only
  come from a line already read.

### 3.2 Run class
First match wins (DR-02, DR-08):

1. `^BUILD SUCCESSFUL` → `green`
2. `^> There were failing tests` → `red`
3. no `Running on ` line → `infra-transport`
4. `^Execution failed for task ':compile` → `infra-compile`
5. otherwise → `infra-other`

The task name cannot mark red: a dead test JVM also says `':test'`. `green` and `red` mean Gradle
reached its normal end; a complete run is measured only for those (DR-01, DR-04).

### 3.3 Counts
- `warnings=` counts `^w: ` (DR-03; `--rerun-tasks` is required, a warm run counts what recompiled).
- `TEST_TASK` is `^> Task :(test|integrationTest|redisIntegrationTest)( |$)`: a match in `warnings.log`
  is a stop.
- `violations=` counts `VIOLATION`, `^/[^ ]*\.kts?:[0-9]+:[0-9]+: ` (DR-09). A failed `ktlintCheck`
  with no such line is a stop.
- A non-`^BUILD SUCCESSFUL` `warnings.log` is a stop.
- Each count compares `after <= baseline`. Sorted lines would hold line numbers, so any edit above a
  warning would read as a change.

## 4. The Skill Edit

The edit makes exactly these changes. "Replace" means the whole line or the quoted text.

### 4.1 State variable (`SKILL.md:11`)
Replace line 11 with:

```text
- Pre-run Baselines: { base_commit: <contents of <U>/base_commit>, unit_baseline: <fetched sha and distance, or "builder run">, build_warnings: X, lint_violations: Z }
```

### 4.2 Step 2 (`SKILL.md:29-33`)
Replace lines 29-33 with this block, verbatim:

```text
- Run this from the feature's checkout root, once per feature. <U> is the absolute path of build/unit-baseline there, <P> of build/unit-gate, and GATE is <U>/tools/unit_gate.sh. `<backend>` is the builder backend for this feature. `$BLD` and `$RSH` for it are in docs/features/maint/37-pinned-definition-libraries/implementation-plan.md, "Copying results back from the builder"; prefix each GATE suite call with both, since shell state does not persist between Bash calls.
- GATE suite, integration, count and wait are long: always call them with `run_in_background: true` and wait for the completion notification. A suite run outlasts the Bash tool's 10-minute foreground cap, and killing the client frees the lock while Gradle keeps running on the VM. Never pipe them.
- A GATE suite exit decides as follows. 0 green and 10 red: Gradle reached its normal end and the document is written. Exit 2 (`REFUSED:`): nothing ran; a dirty tree means uncommitted work, so a phase goes back to the implementer to commit it, and any other refusal is a stop. Other: no document exists; read the printed class:
  - `infra-compile`: a phase goes back to the implementer. At the baseline, stop and report.
  - `infra-transport`: retry once, then stop and report.
  - `infra-other`, or any stop with no class printed: `GATE wait <backend>`, retry once, then stop and report. wait's exit 20: stop and report.
- Baseline:
  0. `grep -q test-filter.json build.gradle.kts`. Exit 1 means the branch predates MAINT-48 Phase 1 and its runs write no sidecar: rebase it onto origin/main before starting, or stop and report. There is no fallback to a failure count.
  1. `git fetch origin main`.
  2. If GATE is missing, or you came here from a phase gate: `mkdir -p <U>/tools <P>`, then for each of unit_gate.sh, junit_ctrf.py and loom_store.py, `git show origin/main:tooling/ctrf/<file> > <U>/tools/<file>.new && test -s <U>/tools/<file>.new && mv <U>/tools/<file>.new <U>/tools/<file>`, then `chmod 755 <U>/tools/unit_gate.sh`. Any non-zero exit: stop and report.
  3. `GATE fresh <U>`. Exit 0: keep the recorded values and continue at §3 RESUME SAFETY ENGINE. Exit 1: go on.
  4. `rm -f <U>/before.ctrf.json <U>/base_commit`, then `touch <U>/planned-removals.txt`. `git merge-base HEAD origin/main` prints $BASE. If `git diff --name-only $BASE HEAD` prints anything, make a base worktree (below) and run items 6 and 8's GATE calls from it.
  5. `( set -a; . ~/.config/loom/loom.artifacts.env; set +a; python3 <U>/tools/junit_ctrf.py fetch --commit <$BASE> --out <U>/before.ctrf.json )`. Never print the env file's values.
     - exit 0: record its `fetched <sha> identity=<set> distance=<n>` line as unit_baseline.
     - exit 3 (no record): go on to item 6.
     - any other exit: stop and report the output; dispatch nothing.
  6. Only after exit 3: `GATE suite <backend> <U> before`, then record "builder run" as unit_baseline.
  7. `GATE failures <U>/before.ctrf.json`. List what it prints in your state block: those failures are pre-existing.
  8. `GATE count <backend> <U>`. Record `warnings=` as build_warnings and `violations=` as lint_violations. Exit 20: stop and report.
  9. Last, so an interrupted baseline is never fresh: `git merge-base HEAD origin/main > <U>/base_commit`.
- Base worktree: `git worktree add --detach ~/.cache/claude-worktrees/<feature>-base <$BASE>`, removed with `git worktree remove ~/.cache/claude-worktrees/<feature>-base` when done. There the tracked `test -> ../test` link resolves; anywhere else the external-fixture classes fail.
- Planned removals: before dispatching phase N, append to <U>/planned-removals.txt every test phase N's plan deletes or disables, one per line, as its full testId, or as `<class>.` for a whole class. Never remove a line: the baseline stays fixed for the feature, so an earlier phase's removals reappear at every later gate. If the file is lost on a resume, rebuild it from phases 1..N.
- Phase test gate:
  1. No subagent may be running. `GATE fresh <U>`. Exit 1 means the branch was rebased or merged main: run Baseline from item 1, which keeps planned-removals.txt, then go on.
  2. `GATE suite <backend> <P> after`. Never a cached or `--tests` run.
  3. `GATE judge <U>/before.ctrf.json <P>/after.ctrf.json <U>/planned-removals.txt <P>/diff.txt`. Its verdict comes from the lines, never from `diff`'s exit code. 0 PASS. 1 FAIL: quote every printed line. A loss line is remedied by restoring the test or by changing the plan, never by editing the list at the gate. 2: stop and report.
  4. A failure line the implementer disputes as environmental (the baseline ran on lunar-ci, the branch on the builder) is settled at $BASE from a base worktree. `GATE suite <backend> <P>/dispute class --tests '<class>'`, then `GATE failures <P>/dispute/class.ctrf.json | grep -Fx '<testId>'`. Match: pre-existing. No match: the failure may depend on test order (BUG-539), so `GATE suite <backend> <P>/dispute full`, then the same lookup on full.ctrf.json. Match: pre-existing. No match: the FAIL stands. Suite exit 20 follows the rules above.
- Phase warning gate: `GATE count <backend> <P>`, then `GATE integration <backend> <P>`. Neither count may exceed its baseline. integration exit 20 is a stop: `GATE class <P>/integration.log`, and read that log. `infra-transport`: retry once, then stop and report. Otherwise, after `GATE wait <backend>` if the class is `infra-other`, run `GATE integration <backend> <P>/dispute` from a base worktree. Exit 20 there means `:integrationTest` is red at $BASE: stop and report. Exit 0: FAIL, and the phase goes back to the implementer. A phase deleting integration tests is not caught here; the plan review owns that.
```

### 4.3 Dispatch (`SKILL.md:55`)
Insert after line 55:

```text
- Before each dispatch, update Planned removals (step 2).
```

### 4.4 Review gate (`SKILL.md:65-67`)
Insert after line 65:

```text
- When the implementer returns, run step 2's phase test gate and phase warning gate before spawning the reviewer. After any remediation, run them again.
```

In line 67, replace `Feed the reviewer the implementer's report and the quoted invariants list.` with:

```text
Feed the reviewer the implementer's report, the quoted invariants list, the gate's verdict and the absolute paths of <U>/before.ctrf.json, <U>/planned-removals.txt, <P>/after.ctrf.json, <P>/diff.txt, <P>/run.log, <P>/warnings.log and <P>/ktlint.log. Tell it not to run `build` or the full suite, overriding its persona: `build` is red on `:redisIntegrationTest` until BUG-17 is closed, and its builder runs are `--tests` checks made while nothing else runs.
```

When BUG-17 closes, add `redisIntegrationTest` to `integration`'s Gradle arguments and delete that clause
from `SKILL.md`, in BUG-17's own change. The rest of the instruction stands.

### 4.5 Gate 7 and item 3 (`SKILL.md:77`, `:82`)
In line 77, replace `build/test/ktlint green with NO regression vs the recorded baseline.` with:

```text
step 2's phase test gate and phase warning gate PASS against the recorded baseline.
```

In line 82, replace `Execute the project's build and lint tools via the gce-builder host. Verify that new failure metrics do not exceed your recorded baseline constraints.` with:

```text
Confirm that the phase gates ran on the commit under review and passed; if they did not, run them. For tests, the verdict is that gate, not a failure count.
```

## 5. CI

No workflow runs a Python tool test today. Phase 1 adds this step to `build` in
`.gitea/workflows/build-plugin.yml` before `Build plugin (compile + distributable zip)`, and to the
job in `.github/workflows/build-plugin.yml` before the step of the same name. It obeys `lunar-ci`'s
executor (no `uses:`, no step `if:`). It fails the job at once: a broken judge makes the run moot.

```yaml
      - name: Gate script tests
        run: |
          cd tooling/ctrf && python3 -m unittest test_unit_gate
```

`test_junit_ctrf` stays outside it, out of scope here. The tests use a fake builder and a fake `rsync`,
so CI needs only `bash`, `flock`, `git`, `awk`, `python3`.

## 6. Edge Cases

| Case | Handling |
|------|----------|
| Phase deletes a test its plan does not name | `verdict` prints it: FAIL. |
| Later phase restores a planned-away test | No line; the list entry is inert. |
| Test renamed | `REMOVED` plus `ADDED`: the plan must name the old testId. |
| A red test also crashes the JVM | `infra-other` (DR-02). |
| Baseline itself red (a builder run, or a fetched record with failures) | `GATE failures` lists them; `diff` reports them only if they change. |
| Rebase between phases | `fresh` exits 1 at the gate; item 2 re-freezes, item 4 removes the old baseline, items 5-9 rebuild it; `planned-removals.txt` is kept. |

## 7. Alternatives Considered

**Premises.** Each constraint below is a deliberate choice unless it says fixed.

- **Test failure as BUILD FAILED**: fixed unless `ignoreFailures` is set. Setting it would change every
  other caller's meaning of a green build, so the gate classifies instead.
- **Rolling baseline** (each passing phase's `after` becomes the next `before`): rejected. It adds a
  mutable file a resume must trust, and hides from the last gate what the feature lost against `main`.
- **`:redisIntegrationTest`** leaves the gate until BUG-17 is fixed: it is red with or without a change,
  so a comparison would need `FAILED`-line parsing for a suite that tells nothing.
- **`:koverVerify`**: not run, and this loses nothing. It has no rules (no `kover {}` block, and it
  passes on two tests, DR-06) so it cannot fail, and it depends on `:test` and the integration tasks.
- **`.claude/agents/reviewer.md` and `implementer.md`** are not edited. `reviewer.md:26` says `build`
  must be clean, which is false while BUG-17 stands; §4.4's payload overrides it. Their unlocked
  `gce-builder.sh run` commands cannot collide with the gate within one session, which runs one
  subagent at a time and the gate with none. Across sessions, Risk 1.3 applies.
- **`$BLD`/`$RSH` by hand**: `gce-builder.sh` has no pull command; MAINT-37's section is correct for
  both backends and is cited to keep one home.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-50-01 | M | §4.2 Baseline 1-5 |
| MAINT-50-02 | M | §4.2 Baseline 6; §2.2 `suite` |
| MAINT-50-03 | M | §2.2 `suite`; §2.3; §3.2 |
| MAINT-50-04 | M | §2.2 `judge`, `verdict`; §2.3; §3.1; §4.2; §4.4; §4.5 |
| MAINT-50-05 | M | §3.1; §4.2 Planned removals; §4.3 |
| MAINT-50-06 | M | §2.2 `count`; §3.3 |
| MAINT-50-07 | M | §2.1; §4 |
| MAINT-50-08 | M | §2.2 `integration`; §4.2 |
| MAINT-50-09 | M | §5 |

## 9. Open Questions

_None — every question is answered by a DR row in `risks-and-gaps.md`._
