---
id: "MAINT-50"
title: "50: Supervise-Implement Per-Test Gate"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-50: Supervise-Implement Per-Test Gate

## Overview

Every feature's gate is judged against a baseline. Step 2 of the `supervise-implement` skill records
that baseline by running the full unit suite on the builder before work starts: 10 to 14 minutes, on
the same single-tenant VM the work then needs. The result is a count, so a phase that fixes one test
and breaks another compares equal. [[MAINT-48]] stores CI's per-test results for each commit and
provides `junit_ctrf.py fetch` and `diff`. This feature makes the skill use them.

## Scope

### In Scope
- `tooling/ctrf/unit_gate.sh`, its tests, and a CI step that runs them.
- The baseline is a fetch of MAINT-48's record for the branch's merge-base with `main`. The suite
  runs only when no record exists.
- Each phase is judged test by test against that one baseline, on a red run as well as a green one.
- The warning count comes from one task set that runs no tests, used by baseline and phases alike.
- The phase gate keeps exercising `:integrationTest`, by plain Gradle success.

### Out of Scope
- **The `supervisor` agent**: it has no baseline step (MAINT-48-00-DR-08).
- **MAINT-48's tooling** (`fetch`, `diff`, their line semantics and exit codes): used as is.
- **`.claude/agents/reviewer.md` and `implementer.md`**: not edited (design §7).
- **`:redisIntegrationTest`**: outside the gate until [[BUG-17]] is fixed.
- **Detecting deleted integration tests**: `integration` sees only Gradle's success, so a phase that
  deletes them is not caught.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-50-01 | **Baseline by fetch** | M | Full | Step 2 fetches the record for the merge-base and spends no builder time on the unit suite when one exists. |
| MAINT-50-02 | **Builder fallback** | M | Full | When `fetch` exits 3 the suite runs once on the builder and is assembled into the same baseline file, green or red. Any other non-zero exit stops the run. |
| MAINT-50-03 | **Run classification** | M | Full | Every builder suite run is classed from its log. A document is written for `green` and `red`, never otherwise, and no earlier document survives a failed step. A run and its copy-back hold one lock. |
| MAINT-50-04 | **Per-test phase verdict** | M | Full | A phase passes only if a real `diff` of a document measured at the HEAD under review, from a clean tree, has no new failure and no unaccounted loss of a test. A disputed failure is pre-existing only if that test, by name, fails at the merge-base. |
| MAINT-50-05 | **Planned removals accumulate** | M | Full | A loss is accounted for only if a phase up to and including the current one plans it. The baseline moves only when the merge-base does, and then it is rebuilt. |
| MAINT-50-06 | **One warning set** | M | Full | Baseline and phases count Kotlin warnings from one cold task set that compiles every source set `build` compiles and runs no test task. `ktlintCheck`'s violations are counted beside it. |
| MAINT-50-07 | **Skill edit applied, judge frozen** | M | Full | `SKILL.md` carries design §4 exactly, nothing else in it changes, and the gate runs the copy frozen from `origin/main`. |
| MAINT-50-08 | **Integration stays in the gate** | M | Full | Each phase runs `:integrationTest` once, with no `:test`, and stops unless Gradle succeeds. |
| MAINT-50-09 | **Tests run in CI** | M | Full | `build` on both platforms runs `test_unit_gate`. |

## Test Cases

Rows 3–18 and 23 are the `test_tcNN_*` methods of `tooling/ctrf/test_unit_gate.py` (run:
`cd tooling/ctrf && python3 -m unittest test_unit_gate`), which drive a fake builder with canned log
text. Each of those rows' mutants is a named entry in `mutants_unit_gate.py` on `proof/maint-50-gate`, and
every entry goes red (DR-07). Rows 19–22 and 24 run by hand or in CI, as stated in the plan.

| # | Req | Given | Then | Red under |
|---|-----|-------|------|-----------|
| 3 | 03 | Canned logs: green, red, compile failure, dead JVM, killed client, transport, empty | `class` prints `green`, `red`, `infra-compile`, `infra-other`, `infra-other`, `infra-transport`, `infra-transport` | red marker widened to `':test'` or to `^BUILD FAILED` |
| 4 | 04 | Each failure line alone; `ADDED passed` + `NEWLY_PASSING`; empty list | exit 1 quoting the line; exit 0 | any alternative dropped |
| 5 | 04 | `REMOVED`, `NEWLY_SKIPPED`, `STATUS failed->skipped`, `OUT_OF_SET before`; `OUT_OF_SET after passed`; empty list | exit 1 each; exit 0 | any alternative dropped; `OUT_OF_SET` either side |
| 6 | 05 | `NEWLY_SKIPPED p.A.keep`, `REMOVED p.B.one`, `REMOVED p.C.two`; lists `p.A.keep`+`p.B.`; those + `p.C.two`; `p.A.kee`…; `B.`… | exit 1 on `p.C.two` only; exit 0; exit 1 on `p.A.keep`; exit 1 on `p.B.one` | substring match; prefix without the dot |
| 7 | 04 | `after` missing, truncated, or lacking `results.environment`; zero tests on both sides; missing list | exit 2 each | `EXECUTED` check removed (PASS on a crash); `[1-9]` widened; list check removed |
| 8 | 04 | `after.head` naming an older commit than HEAD | exit 2, `not HEAD` | head check removed |
| 9 | 02 | Canned green, then red | exit 0, document and `.head` written; exit 10, document written; Gradle argv exact | `--rerun` dropped; head not recorded |
| 10 | 03 | An existing document, then compile failure, transport, killed client, dead results `rsync`, dead filter `rsync`, no XML | exit 20 and no document, each | `rm` removed; results `rsync` or `assemble` failure ignored. The filter `rsync`'s check has no mutant: `rm` plus `assemble` already catch it |
| 11 | 03 | A fake builder and `rsync` that try `flock -n` and test `/proc/self/fd/9` | Lock busy in both; fd 9 closed in the child | lock removed; freed before copy-back; fd inherited |
| 12 | 03 | `--status` output: none running; `BUSY` twice then none; dead `shell`; other text; `BUSY` at the limit | exit 0; exit 0 after three polls; 20; 20; 20 | each branch reads idle; `BUSY` ignored; deadline dropped |
| 13 | 06 | Canned warnings log; a log showing `> Task :test` | `warnings=2`, `violations=0`, argv has `--rerun-tasks --no-build-cache` and both integration class tasks; exit 20 | cold flags dropped; set narrowed; test-task check dropped |
| 14 | 08 | Canned green; red, killed, transport | exit 0 with argv exactly `run integrationTest --rerun --no-build-cache`; exit 20 each | `--rerun` dropped; red tolerated |
| 15 | 06 | ktlint log with a `/w/a.kt:8:48: …` line; a failed log with none | `violations=1`; exit 20 | tool failure tolerated |
| 16 | 04 | A document where `p.A.other` failed and `p.A.disputed` passed | `failures` prints only `p.A.other`, so the dispute lookup `grep -Fx p.A.disputed` finds nothing though a test in the class failed | `failures` lists everything |
| 17 | 05 | Baseline dir empty; `base_commit` only; both; then `origin/main` moved | `fresh` exits 1, 1, 0, 1 | merge-base or record ignored |
| 18 | 07 | No arguments; unknown name; too few; unset `$BLD`, `$RSH`; missing document; missing log | exit 2 each | `$BLD`/`$RSH` check removed; `class` log check removed |
| 19 | 05 | Walk-through at a live branch: rebase after a baseline; run Baseline from item 1 per design §6 | `fresh` 1 before, 0 after; `before.ctrf.json` is the new merge-base's; `planned-removals.txt` unchanged | `base_commit` written before item 5 (an interrupted baseline reads fresh) |
| 20 | 01, 02 | Credentials loaded | `fetch --commit c9eb7512` exit 0 `distance=1`; `fetch --commit cf6cdf15` exit 3 | each commit swapped |
| 21 | 07 | Snapshot of `SKILL.md` before the edit | `diff <snapshot> SKILL.md \| grep -E '^[0-9]' \| sed -E 's/[acd].*//' \| tr '\n' ' '` prints `11 29,33 55 65 67 77 82 `; each `text` block of design §4 is a substring; `grep -c -E 'test_failures\|tail -20\|run build' SKILL.md` prints `0` | an extra hunk; a block altered; the edit not applied |
| 22 | 09 | Phase 1's PR | The `Gate script tests` step passes in the `build` job's log | step absent |
| 23 | 04 | Tree with a modified tracked file; a staged one; an untracked one; only a gitignored one | exit 2, `REFUSED:`, no Gradle run, each of the first three; exit 0 on the last | check removed; untracked ignored; gitignored counted |
| 24 | 07 | Design §4.2 item 2's chain with `<file>` naming a path absent from `origin/main`; then with `git show` of an empty blob | non-zero, each; the prior frozen copy unchanged | `test -s` dropped; `&&` chain broken |

## Acceptance Criteria

- [x] Every test case above passes, and `mutants_unit_gate.py` reports `bad 0`.
- [x] `SKILL.md` matches design §4, and a tracked commit records the feature closed.

## Non-Functional Requirements

No builder time is spent on the unit suite at step 2 when a record exists. `count` and `integration`
cost about a minute and a half each (DR-03, DR-07, DR-09).

## Dependencies

- [[MAINT-48]] merged and storing records from CI.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
