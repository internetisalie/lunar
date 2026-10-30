---
id: "MAINT-48"
title: "48: Unit Test Results Stored Per Commit"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-48: Unit Test Results Stored Per Commit

## The gap

A feature's gate needs to know which tests passed before the work started. Today the only way to
find out is to run the full unit suite on the builder, which takes about 14 minutes and produces a
count, not a result per test.

`main`'s CI already runs the full suite on every push (`build-plugin.yml`, the `build` job) and
then discards the per-test results. [[MAINT-40]] wired the loom conformance store into CI for the
corpus sweep, under check `a3` suite `corpus`, which leaves `a3`'s primary record free for the
commit's own test results.

## Scope

- **Emit CTRF from the unit suite.** Convert the `build` job's JUnit XML
  (`build/test-results/test/TEST-*.xml`) into one CTRF document, with one CTRF test per test
  method: its status (passed, failed, skipped), its duration, and the failure message.
- **Store from CI.** The `build` job puts that document as conformance check `a3`, no suite, for
  the commit it tested. Use the install step and store conventions that [[MAINT-40]] established.
  The store step runs even when tests fail, because a red commit's results are a baseline too.
- **Fetch a baseline.** A command that resolves the record for a given commit (normally the
  merge-base of a branch with `main`) and fetches it. Exit 3 means no record.
- **Compare by test.** A diff of two documents by test identity. It lists tests that newly fail,
  newly pass, appear, or disappear, and the total executed. A drop in the executed count is
  reported, because a run that executes nothing must not read as green.

## Relationship to neighbouring work

- **[[MAINT-40]]** is the template. Its loom-cli plumbing and install step move into shared homes
  (design §2.2, §2.5); its corpus record stays under suite `corpus`.
- **[[MAINT-45]]** stores performance records under `a3` with a suite of its own, because this
  feature takes `a3`'s primary record.
- **[[MAINT-50]]** makes the `supervise-implement` gate use this feature's `fetch` and `diff`.

## Out of Scope

- **Storing from the tag lane or GitHub CI.** Tag pushes never run the `build` job; the commit's
  record comes from its `main` push. GitHub has no store credentials.
- **Running the external-fixture classes in CI.** That is MAINT-49, which removes
  `-PexcludeExternalFixtureTests`. The sidecar records the filter that actually ran, so the record
  is correct before and after MAINT-49 lands, and no test case here depends on that flag.
- **Any change to the `supervise-implement` skill or another consumer of the record.** That is
  MAINT-50.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-48-01 | **Test-set sidecar** | M | Full | Every `test` task run writes `build/test-results/test-filter.json` holding the exclude, include and command-line include patterns it filtered on (design §2.1, §4.1). The file is an output of that task, so an UP-TO-DATE or FROM-CACHE `test` leaves it matching the XML beside it. |
| MAINT-48-02 | **One CTRF document** | M | Full | `junit_ctrf.py assemble` turns a JUnit XML directory and its sidecar into one CTRF 0.2.0 document with one test per `testcase`, carrying status, duration in milliseconds and failure message (design §4.3). The same inputs give the same bytes. |
| MAINT-48-03 | **Test identity** | M | Full | Each `testId` is `<class>.<method>`, with `#<n>` only for repeats, assigned independently of file and element order. The `testId` of a name that occurs once never depends on status, duration or message. Repeats of one name are ordered by those fields (design §3.2). |
| MAINT-48-04 | **Test-set identity** | M | Full | `environment.testEnvironment` is derived from the sidecar, and `environment.extra.filter` carries its lists. Nobody types it. |
| MAINT-48-05 | **Store from CI** | M | Full | The Gitea `build` job puts the document against `HEAD` as `a3`, no suite, after the unit tests, whether they passed or failed. An existing record is not an error. A partial (`--tests`) run or a run with no XML is refused. A store failure fails the job and never changes the test verdict. |
| MAINT-48-06 | **Fetch a baseline** | M | Full | `junit_ctrf.py fetch --commit C` returns the unit-test record of the nearest first-parent ancestor of `C` whose code is identical to `C`'s, and exits 3 when there is none (design §3.6). |
| MAINT-48-07 | **Compare by test** | M | Full | `junit_ctrf.py diff` lists newly failing, newly skipped, newly passing, added, removed and out-of-set tests and the executed counts over the tests both sets run. A test only one side's filter selects is never reported as added or removed. Exit 1 on a new failure or a passed test now skipped, 4 on an executed-count drop, 2 when not comparable (design §3.8). |
| MAINT-48-08 | **One home for store plumbing** | M | Full | `corpus_ctrf.py` and `junit_ctrf.py` share `tooling/ctrf/loom_store.py`; both jobs share `tooling/ci/install-loom-cli.sh`. MAINT-40's tests pass with no assertion changed. |

## Test Cases

Python cases TC 2–8, 13 and 14 live in `tooling/ctrf/test_junit_ctrf.py` (TC 11 in `test_corpus_ctrf.py`) and run with
`cd tooling/ctrf && python3 -m unittest test_junit_ctrf`. Each names the mutation that turns it red.

| # | Req | Given | When | Then | Red under |
|---|-----|-------|------|------|-----------|
| 1 | 01 | The Phase 1 tree on the builder | (a) `run "test --rerun --tests '*LuaBundleCasingTest*'"`; (b) `run "test --rerun -PwithPerf --tests '*LuaBundleCasingTest*'"`; (c) (b) without `--rerun`, then `rm` only the builder's `build/test-results/test-filter.json` and (b) without `--rerun` again. Copy back the sidecar after each run | (a) `excludePatterns` contains `*Performance*` and `*Benchmark*`; `commandLineIncludePatterns` = `["*LuaBundleCasingTest*"]`. (b) neither pattern; the same `commandLineIncludePatterns`. (c) `:test` is UP-TO-DATE, then FROM-CACHE; both sidecars are byte-identical to (b)'s | the command-line read dropped (`[]` in both); `excludePatterns` written from `includePatterns` (`*Performance*` absent in (a)); `outputs.file` dropped (no sidecar after the FROM-CACHE run) |
| 2 | 02 | `TEST-p.C.xml`: `testA` time `0.012`; `testB` `<failure message="expected:&lt;a&gt; but was:&lt;b&gt;">`; `testC` `<skipped/>`; `testL` failure whose message is 1500 × `x`. `TEST-p.D.xml`: `testE` time `1.5`, `<error message="">` with text `java.lang.IllegalStateException: boom` then a `\tat …` line. A copy of the directory with each file's `testcase` elements in reverse order. A sidecar | `assemble` both | byte-identical outputs, equal to their own `sort_keys` canonical form. Statuses `passed`, `failed`, `skipped`, `failed`, `failed`; `rawStatus` absent, `failure`, `skipped`, `failure`, `error`; `testA.duration` 12, `testE.duration` 1500; `testE.message` = `java.lang.IllegalStateException: boom`; `testL.message` is 1000 characters; `testA` has no `message`. `summary` tests 5, passed 1, failed 3, skipped 1, pending/other/start/stop 0. Every key outside `extra` is in design §4.3's allowed set | `error` mapped to `skipped`; an empty `message` attribute used as the message (`testE` has no `message`); duration in whole seconds (`testA` 0); no cap (`testL` 1500); no `sort_keys` |
| 3 | 03 | `TEST-a.xml` holds, in this order, `p.C.testX` failed with message `m`, `p.C.testX` passed, and `p.C.test[1]` passed. A second directory with those elements reversed. A third equal to the first except that the passed `testX` has time `9.0`. A fourth holding `p.C.testY` twice and a method literally named `testY#1` | `assemble` each | first: `testId` `p.C.testX` is the passed case (its empty message sorts first), `p.C.testX#1` the failed one, both with `name` `p.C.testX`; `p.C.test[1]` kept verbatim. Second: byte-identical to the first. Third: the same `testId`s on the same cases. Fourth: exit 2, `duplicate testId p.C.testY#1` | ordinal in element order (the second differs); duration in the id (third differs); brackets stripped; ordinal written into `name`; uniqueness check dropped (fourth exits 0) |
| 4 | 04 | Sidecar S1 with `excludePatterns` `["*ParseOracleTest", "*Benchmark*", "*Performance*"]`; S2 without `*ParseOracleTest`; S1 with that list's elements reversed; S1 with `schema` 2; a missing sidecar path. TC 2's directory | `test_set_identity(read_filter(S))`; `assemble` TC 2's directory with S1, and again with reversed S1 | S1 ≠ S2; S1 = reversed S1; `set-` + 16 hex; schema 2 and the missing path → `assemble` exits 2 (the latter with `no test-filter.json at`). The document built from reversed S1 has `extra.filter.excludePatterns` `["*Benchmark*", "*ParseOracleTest", "*Performance*"]`. The document's `environment.testEnvironment` = `test_set_identity(S1)` and `environment.extra.filter` = S1's three lists, sorted | identity over a constant (S1 = S2); `read_filter` does not sort (reversed S1 differs, DR-14); schema check dropped; `testEnvironment` or `extra.filter` not written into the document |
| 5 | 05 | MAINT-40's stub `loom-cli` (`loom_stub.STUB`). Scenarios: resolve exit 3; measured with the document's sha256; measured with another; resolve exit 1; put exit 1; a sidecar with `commandLineIncludePatterns` `["*X*"]`; an empty results directory | `store` | put once with exactly `["artifact","put","conformance",<out>,"--commit","HEAD","--check","a3","--no-item","--repo","."]`, exit 0; no put, `store: already stored, identical`, exit 0; no put, `WARNING store: HEAD already has a different a3 unit-test record; kept the first`, exit 0; exit 1; exit 1; no loom-cli call, exit 2; no loom-cli call, exit 2 | a `--suite` argument on put; partial check dropped; empty check dropped (the stub records a resolve) |
| 6 | 06 | Temp repo: C1 (adds `src/a.kt`) ← C2 (adds `docs/x.md`) ← C3 (adds `README.md`) ← C4 (edits `src/a.kt`). Stub: C1 measured with a unit document; C2 measured with a `lunar-corpus-sweep` document; C3, C4 unmeasured | `fetch --commit C3`; `fetch --commit C4`; `fetch --commit C3 --max-depth 2` | C1's document, `distance=2`; exit 3; exit 3. No resolve argv contains `--suite` | code-identity cut dropped (C4 returns C1); tool check dropped (C3 returns C2); `--suite` on resolve |
| 7 | 07 | Before (filter excludes `*ParseOracleTest`): `p.A.t1` passed, `t2` failed, `t3` passed, `t4` passed, `p.S.t5` skipped. After (filter without it): `t1` failed, `t2` passed, `t3` passed, `t5` passed, `p.A.t6` passed, `p.ParseOracleTest.tx` passed | `diff` | lines `ADDED\tpassed\tp.A.t6`, `NEWLY_FAILING\tp.A.t1\t<msg>`, `NEWLY_PASSING\tp.A.t2`, `OUT_OF_SET\tafter\tpassed\tp.ParseOracleTest.tx`, `REMOVED\tpassed\tp.A.t4`, `STATUS\tskipped->passed\tp.S.t5`, then `EXECUTED\t4\t5`; exit 1 | `tx` reported `ADDED` (filter ignored); skipped→passed not reported (no `STATUS` line) |
| 8 | 07 | TC 7 before. Afters: (b) TC 7's after with `t1` passed; (c) only `t1` passed and `tx` passed; (d) TC 7's after with `t1` passed and `tx` failed; (e) TC 7's after with `tool.name` `other`; (f) (b) with `t3` skipped; (g) (b) with `p.S.t5` failed, message `boom`; (h) (b) with `t6` failed; (i) (b) as before and TC 7's before as after | `diff` | (b) exit 0, and `REMOVED\tpassed\tp.A.t4` is printed; (c) `EXECUTED\t4\t1`, `EXECUTED_DROP\t3`, exit 4; (d) exit 1; (e) `NOT COMPARABLE` on stderr, exit 2; (f) `NEWLY_SKIPPED\tp.A.t3`, `EXECUTED\t4\t4`, exit 1; (g) `NEWLY_FAILING\tp.S.t5\tboom`, exit 1; (h) `ADDED\tfailed\tp.A.t6`, exit 1; (i) `OUT_OF_SET\tbefore\tpassed\tp.ParseOracleTest.tx` and no `REMOVED` line for it | executed counted without the other side's filter ((c) prints `2`); out-of-set failures ignored ((d) exits 0); drop not flagged ((c) exits 0); passed→skipped reported as `STATUS` ((f) exits 0); the tool-name check dropped ((e) exits 1); `NEWLY_FAILING` only from `passed` ((g) exits 0); `ADDED\tfailed` not a regression ((h) exits 0); the removal check reads the before filter ((i) prints `REMOVED`). DR-14 observed (b), (e) and (f), with the last two mutants |
| 9 | 05 | A throwaway PR branch after Phase 4: commit 1 adds a failing test; commit 2 removes it and adds `--repo /nonexistent` to the store step's command | the `build` run of each commit | (1) the Unit tests step fails, the store step logs a `store:` line, the job is red with the unit-test `::error::` and not the store one. (2) Unit tests pass; the store step logs `loom-cli:` and `chdir /nonexistent` (DR-11); the job is red with the store `::error::` and not the unit-test one | store step behind the test result (no `store:` line in (1)); the Report step ignores the store sentinel ((2) green) |
| 10 | 05 | The first `main` push after Phase 4 merges, at `<sha>`; a worktree at `<sha>` | resolve `--commit <sha> --check a3 --repo . --json` with credentials loaded (DR-09), then `get` its key. On the builder, from that worktree, `run "test --rerun <the -P flags of <sha>'s Unit tests step> --tests '*LuaBundleCasingTest*'"`; copy back the sidecar | exit 0; the document's `tool.name` = `lunar-unit-tests`; `environment.extra.filter.excludePatterns` equals the builder sidecar's `excludePatterns`; `commandLineIncludePatterns` is empty | the store step never runs (exit 3); the store step reads a sidecar from another invocation (lists differ) |
| 11 | 08 | Phase 2's refactor | `cd tooling/corpus && python3 -m unittest test_corpus_ctrf`; `git diff -- tooling/corpus/test_corpus_ctrf.py` | all pass; the diff only moves `STUB` and changes the patch target | the shared WARNING message drops the label (`test_TC_7_different_record_is_kept_with_a_warning` fails) |
| 12 | 08 | The Phase 4 PR | its `build` and `corpus` jobs | each job's log has the install script's `loom-cli v…` line; `corpus` is green; `grep -c releases/latest .gitea/workflows/build-plugin.yml` prints 0 | a job keeps its inline install (grep prints a non-zero count) or skips the script (no version line in its log) |
| 13 | 02 | `TEST-a.xml`: `p.C.testM` with `<failure message="junit.framework.ComparisonFailure: expected:&lt;[a&#10;  b]&gt; but was:&lt;[a&#13;&#10;&#10;c]&gt;"/>` | `assemble` | `testM.message` = `junit.framework.ComparisonFailure: expected:<[a b]> but was:<[a c]>` | line breaks kept (the message spans three lines) |
| 14 | 06 | TC 6's repo plus R1 (adds a 40-line `src/b.kt`) ← R2 (`git mv src/b.kt docs/b.md`). Stub: R1 measured with a unit document | `fetch --commit R2` | exit 3 | rename detection left on (`git diff` lists only `docs/b.md`; R2 returns R1, exit 0) |

## Acceptance Criteria

- [x] TC 1–8, 11, 13 and 14 pass, with each named mutant observed red.
- [x] The document assembled from a real full run validates against the pinned CTRF 0.2.0 schema.
- [x] TC 9, TC 10 and TC 12 hold on CI.

## Non-Functional Requirements

- Stdlib-only Python. The store step adds one resolve and one put to `build`; a full run's document
  is about 1.1 MB (risks DR-02).
- No plugin code changes; nothing runs in the IDE.

## Dependencies

- MAINT-40 merged (#122, #123): store env and org secrets, loom-cli `--suite` (v0.90.0 or later).
- The de-risking rows in [risks-and-gaps.md](risks-and-gaps.md).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
