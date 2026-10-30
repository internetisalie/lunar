---
id: "MAINT-48-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-48"
folders:
  - "[[features/maint/48-unit-test-results-per-commit/requirements|requirements]]"
---

# MAINT-48: Risks & Gaps

## Critical Risks

### Risk 1.1: A loom outage reds every `build` run
- **Impact**: the store sentinel fails the routine gate on every push and PR until the store or the
  forge recovers (design §10).
- **Likelihood**: low. The `corpus` job has the same dependency on `main`.
- **Mitigation**: the `::error::` line names the store step, so the cause is unambiguous. If an
  outage lasts, setting the install step's failure to a warning is a one-line workflow change.

### Risk 1.2: The command-line include patterns are read through internal Gradle API
- **Impact**: a Gradle upgrade that renames `DefaultTestFilter.getCommandLineIncludePatterns` makes
  the safe cast or the call fail, and a `--tests` run would read as a full one.
- **Likelihood**: low per upgrade; the build fails to compile the script if the method is removed,
  which is loud, and the safe cast covers a type change. `DefaultTestFilter` is imported at the top
  of `build.gradle.kts`, so removing or moving the class breaks script compilation for every task,
  not only `test`.
- **Mitigation**: TC 1's (a) expectation fails on any such upgrade, and CI never passes `--tests`, so
  the stored records are unaffected.

## Design Gaps

None open. Each question from the filed scope is answered by a DR row below and folded into the design.

## Technical Debt & Future Work

- **Run the external-fixture classes in CI**: MAINT-49. Until it lands, those classes appear as
  `OUT_OF_SET after` in every builder-vs-CI diff.

## Pre-Implementation De-risking Tasks

Load-bearing premises: **read** = settled by source; **runtime** = needs execution. Every row is
`done`. DR-08, DR-12 and DR-13 moved to MAINT-50 with the skill gate.

| ID | Premise (kind) | Action | Status |
|----|----------------|--------|--------|
| MAINT-48-00-DR-01 | CI keeps the JUnit XML of a run (runtime) | `GET /api/v1/repos/lunar/lunar/actions/artifacts?limit=5` with the `gitea-prod` token, 2026-10-30 | **done: refuted.** `total_count` 0 for the repository. The `build` job uploads nothing (`build-plugin.yml:7-11`, executor without artifact actions), so the prototype used builder runs (DR-02). |
| MAINT-48-00-DR-02 | What Gradle writes for a full run: names, statuses, stale files; and that the converter is deterministic, small enough and valid (runtime) | Run 1: `builder-run libvirt "test --rerun --no-build-cache"` at `cf6cdf15` (BUILD SUCCESSFUL, 13m47s). `rsync --delete` of `build/test-results/test/`. A throwaway converter implementing design §3.1–§3.4 run twice; `cmp`; `jsonschema` 4.19.2 against the pinned schema | **done: confirmed.** 529 `TEST-*.xml` files, 3447 `testcase` elements: passed 3443, skipped 4, failed 0. Names: JUnit 3 and 4 give the method (`testGotoAndLabel`, backticked names with spaces); Jupiter gives `name()` (988 of them, e.g. `testArgs()`); 434 contain spaces, 7 contain `#` (`… (BUG-448 #2)`), none contain `[` or `$`. No `class.method` repeats. Jupiter `@Disabled` is `<skipped/>`. Document 1,141,960 bytes; the two conversions are byte-identical; `valid`. Two corpus XMLs left on the builder by an earlier `-PwithCorpus` run were gone after this run: Gradle replaces the directory's reports, so stale files do not leak in. |
| MAINT-48-00-DR-03 | A `doFirst` on `test` sees the effective exclude patterns and, through `DefaultTestFilter.getCommandLineIncludePatterns`, the `--tests` patterns; the file survives the task (runtime) | Throwaway version of design §2.1 in `build.gradle.kts`, then three builder runs: run 2 `test --rerun --no-build-cache -PexcludeExternalFixtureTests`; run 3 `test --rerun -PexcludeExternalFixtureTests --tests '*LuaBundleCasingTest*'`; run 4 `test --rerun --tests '*LuaBundleCasingTest*'`. Copy back `build/test-results/test-filter.json` after each. `javap` of `DefaultTestFilter` in `gradle-testing-base-9.7.1.jar` | **done: confirmed.** Run 2: `excludePatterns` = `*AnnotatedFixtureSweepTest`, `*Benchmark*`, `*Corpus*`, `*DockerTest`, `*InspectionParityTest`, `*LuaDescriptionIndexTest`, `*LuaRecursiveReferenceTest`, `*LuaTypeGoldenMemberFilesTest`, `*ParseOracleTest`, `*Performance*`; `commandLineIncludePatterns` `[]`. Run 3: the same, `commandLineIncludePatterns` `["*LuaBundleCasingTest*"]`. Run 4 (the control): the external-fixture patterns absent, the same command-line list. `javap` lists `public java.util.Set<java.lang.String> getCommandLineIncludePatterns()`. Edit reverted and the builder tree re-synced. |
| MAINT-48-00-DR-04 | How a JUnit 3 synthetic `test…$lambda$N`, an assertion failure, a JUnit 4 exception and `@Ignore` appear (runtime) | In run 2, two throwaway classes: a `BasePlatformTestCase` with `testWithLambda` (a `Runnable` lambda) and `testFailing` (`assertEquals("expected", "actual")`); a JUnit 4 class with an `@Ignore` test and one that throws `IllegalStateException("maint48 boom")`. Deleted afterwards | **done.** The synthetic is `<testcase name="warning" classname="net.internetisalie.lunar.Maint48ProbeTest">` with `<failure message="junit.framework.AssertionFailedError: Test method isn't public: testWithLambda$lambda$0(net.internetisalie.lunar.Maint48ProbeTest)">`, beside a passing `testWithLambda`. The assertion: `<failure message="junit.framework.ComparisonFailure: expected:&lt;[expected]&gt; but was:&lt;[actual]&gt;">`. The JUnit 4 exception is `<failure>`, not `<error>`, message `java.lang.IllegalStateException: maint48 boom`. `@Ignore` is `<skipped/>`. No `<error>` element in run 1 or run 2. Gradle reported `3426 tests completed, 3 failed, 5 skipped`, matching the 3426 `testcase` elements. So no name needs normalising: a synthetic is its own failing `<class>.warning` case (design §3.2). |
| MAINT-48-00-DR-05 | CTRF 0.2.0 needs integer `duration` and refuses an `error` status; `rawStatus` is allowed; `environment` takes no new key (runtime) | Schema: `https://raw.githubusercontent.com/ctrf-io/ctrf/v0.2.0/schema/ctrf.schema.json`, sha256 `cb9bd075…6cf3` (re-downloaded, hash matched MAINT-40's). Command: `python3 -I validate.py ctrf.schema.json <document>`, where `validate.py` loads both files, calls `jsonschema.validate(doc, schema)` (`jsonschema` 4.19.2) and prints `valid`; a failure raises `ValidationError`, whose message is quoted below. Run on DR-02's document and on mutated copies | **done.** Real document `valid`. Controls that must fail did: status `error` → `'error' is not one of ['passed', 'failed', 'skipped', 'pending', 'other']`; no `duration` → `'duration' is a required property`; duration `1.5` → `1.5 is not of type 'integer'`; `environment.filter` → `Additional properties are not allowed ('filter' was unexpected)`. Hence §3.1 maps `error` to `failed` with `rawStatus`, durations are integer milliseconds, and the filter lives in `environment.extra`. |
| MAINT-48-00-DR-06 | Two runs of the same code agree test by test, and the filter-aware diff turns set differences into `OUT_OF_SET`, not removals (runtime) | The prototype's §3.8 diff of run 1 (builder set; sidecar built from run 4's `excludePatterns` with no command-line patterns, since run 1 predates the probe) against run 2 (CI set plus DR-04's classes). Control: the same diff with run 1's filter written into run 2's document | **done: confirmed.** 3421 shared tests, no status change on any of them. Output: 5 `ADDED` (DR-04's tests: 3 failed, 1 passed, 1 skipped) and 26 `OUT_OF_SET` (`ParseOracleTest` 14, `LuaDescriptionIndexTest` 6, `LuaRecursiveReferenceTest` 5, `LuaTypeGoldenMemberFilesTest` 1). Control: the 26 became `REMOVED`. Durations differed on 2950 of the 3421 shared tests, so a CI rerun's document always differs from the first (design §3.5). |
| MAINT-48-00-DR-07 | loom-cli's resolve/put/get contract as MAINT-40 measured it holds on the current release (read) | `loom-cli --version`; `artifact resolve --help`; `artifact put conformance --help`; `artifact get --help` | **done.** `v0.92.0 (rev 0aa2aa7e40a8)`: `put conformance <path>` positional, `--check`, `--commit`, `--no-item`, `--repo`, optional `--suite`; `get <key> --output`; resolve adds `--merge-base-with` (not used: it resolves one commit, while fetch must walk past docs-only commits, §3.6). |
| MAINT-48-00-DR-09 | A local shell can resolve and get records once credentials are loaded, a local clone derives CI's subject, and `get` does or does not create a missing parent (runtime) | Read-only, 2026-10-30, loom-cli v0.92.0: `loom-cli artifact resolve --commit 69084a72 --check a3 --repo . --json` without and then with `set -a; . ~/.config/loom/loom.artifacts.env; set +a`; the same with `--suite corpus`; `get <that key> --output` into a missing directory, an existing directory, and an existing file. Probe directory deleted afterwards | **done.** Without credentials: exit 1, `object-store credentials are unresolved … load ~/.config/loom/loom.artifacts.env`. With them: exit 3 (`a3` primary unmeasured). With `--suite corpus`: exit 0, key `v2/commit/gitea.internetisalie.net/lunar/lunar/69084a72…/conformance/a3.corpus.ctrf.json`, size 1654963, which is the subject CI stored under. `get` into a missing parent: exit 1, `create partial destination … no such file or directory`. Into an existing directory: exit 0, and the sha256 matched. Onto an existing file: exit 1, `path exists`. So a local `fetch` needs those credentials loaded, `fetch_first` creates the parent (§2.2), and `get`'s refusal of an existing path is why `_get` unlinks the part first. |
| MAINT-48-00-DR-10 | The sidecar cannot go stale beside the XML (runtime) | Design §2.1 with `outputs.file`, in `build.gradle.kts` temporarily, on the builder: run A `test --tests '*LuaBundleCasingTest*'`; run B the same again; then `rm` only the sidecar and run C the same. Then run D `-PexcludeExternalFixtureTests`, run E `--rerun`, run F `--rerun -PwithPerf`, each with the same `--tests`. Reverted afterwards | **done.** A: executed, sidecar written. B: `:test` UP-TO-DATE, sidecar untouched (same mtime). C: `:test` FROM-CACHE, sidecar restored with A's content. D: `:test` executed (a changed filter is a changed input); the sidecar gained `*LuaDescriptionIndexTest`, `*LuaRecursiveReferenceTest`, `*LuaTypeGoldenMemberFilesTest` and `*ParseOracleTest`. E: `*Benchmark*` and `*Performance*` present. F: both absent, `commandLineIncludePatterns` unchanged. E and F are TC 1 (a) and (b), and do not depend on `-PexcludeExternalFixtureTests` (MAINT-49). |
| MAINT-48-00-DR-11 | A loom-cli failure inside `store` exits non-zero, so it reaches the CI sentinel (runtime) | Read-only: `loom-cli artifact resolve --commit HEAD --check a3 --repo /nonexistent --json`, with credentials | **done.** Exit 1, `derive commit subject: resolve commit "HEAD": git rev-parse --verify: chdir /nonexistent: no such file or directory`. `lookup` turns that into `LoomError`, so `store` returns 1 (design §2.3). This is TC 9 (2)'s injected failure. |
| MAINT-48-00-DR-14 | Design §3.3's sorting, §3.8's skip/removal/tool-name behaviour do what the test cases claim (runtime) | A throwaway probe implements §3.3's `read_filter` and identity and §3.8's diff, runs TC 4's reversed list and TC 8 (b), (e) and (f), and repeats each under its mutant (`nosort`, `notool`, `noskip`). | **done.** Unmutated: reversed S1 gives S1's identity and `extra.filter` lists sorted; (b) exit 0 and prints `REMOVED\tpassed\tp.A.t4`; (e) exit 2; (f) exit 1 with `NEWLY_SKIPPED\tp.A.t3`, `EXECUTED\t4\t4`. `nosort`: identities differ. `notool`: (e) exit 1. `noskip`: (f) exit 0 with `STATUS\tpassed->skipped`. |

## Test Case Gaps

- The workflow halves of MAINT-48-05 (the store step runs on a red suite; a store failure reds the
  job) and MAINT-48-08 (both jobs call the install script) are covered only on CI, by TC 9 and
  TC 12: no unit test can reach the workflow.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
