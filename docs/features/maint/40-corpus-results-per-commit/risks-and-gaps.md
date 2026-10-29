---
id: "MAINT-40-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-40"
folders:
  - "[[features/maint/40-corpus-results-per-commit/requirements|requirements]]"
---

# MAINT-40: Risks & Gaps

## Critical Risks

### Risk 1.1: A second put for one commit and check
- **Impact**: a CI rerun, or a concurrent `main` and tag run of one commit, may fail its store step.
  Or it may keep a record that the rerun disagrees with.
- **Likelihood**: medium. Reruns are routine. Byte-identity across runs is expected while
  `highlightFailures = 0` (every committed baseline today), but it is unmeasured for findings.
- **Mitigation**: `store` resolves first and never re-puts (design §3.5). Only the concurrent race
  reaches a second put. DR-02 measures what that put does.

### Risk 1.2: The executor's checkout does not derive the repository identity a developer's clone derives
- **Impact**: CI stores under a subject that `fetch` on a developer clone never resolves. The
  developer's `origin` is `https://gitea.internetisalie.net/lunar/lunar.git`. The executor runs
  `git remote add` with a URL nobody has recorded (`tooling/ci-image/Dockerfile:37-40`).
- **Likelihood**: medium.
- **Mitigation**: DR-04.

### Risk 1.3: The full-shape suite ignores the torture member
- **Impact**: `--tests '*Corpus*'` also runs `LuaTortureCorpusTest` in the sweeps' JVM. A torture
  re-pin that shifts cross-member state would not change the identity.
- **Likelihood**: low. Torture state is not in `corpus.json`.
- **Mitigation**: `diff` refuses on a `files`/`requires` mismatch (design §3.7). Any other shift
  shows as findings, which is what a reader investigates anyway.

## Design Gaps

### Gap 2.1: Which check, format and suite
- **Question**: where corpus findings are stored, and in what format.
- **Resolved**: check `a3`, CTRF, suite `corpus` (owner, 2026-10-29). Loom fixes each check's
  format (`loom-delivery/durable/artifact/identity.go:582-591`: `a2`/`a3`/`a13` CTRF), so every `a3`
  record is CTRF.
- **Resolved by**: DR-06.

### Gap 2.2: `--producer` and `--item` for CI
- **Question**: what CI passes. Read from loom LCL-226 (not executed): a conformance put requires
  exactly one of `--item` / `--no-item` and refuses `--item`. Producer is stored metadata, and
  differing metadata is an immutable conflict (loom-delivery v0.24.0 `service.go:491-516`).
- **Leaning**: `--no-item` and no `--producer`. A per-run producer would make every rerun conflict.
- **Resolved by**: DR-05.

### Gap 2.3: The suite segment in the key (loom LCL-241)
- **Question**: v0.89.0's put has no suite argument (`loom-cli artifact put conformance --help`), and
  loom-cli `b75424c` has none in source (`grep -rni suite loom-cli/cmd loom-cli/internal` matches no
  non-test line). A put today would occupy `a3`'s primary slot, `a3.ctrf.json` (DR-03). LCL-241
  (`status: backlog`) adds the key `<commit>/conformance/<check>[.<suite>].<format>.json`, giving
  `a3.corpus.ctrf.json`.
- **What LCL-241 leaves unstated**:
  - **The flag value.** Its acceptance criteria name a flag, `--suite`, with the value `a3.corpus`,
    but its description says a suite name contains no period, and a second criterion refuses one.
    The working assumption is `SUITE_ARGV = ("--suite", "corpus")`.
  - **resolve.** LCL-241 names put and get only. `store` and `fetch` both resolve first (design
    §3.5, §3.6); whether resolve takes a suite is not stated.
  - **get.** LCL-241 says get takes the suite; today's `get <key>` takes the key resolve returns.
- **Resolution**: nothing is stored until LCL-241 ships. Phases 1–3 do not depend on it. DR-10 records
  the shipped argv for resolve, put and get before Phase 0 begins, and sets `SUITE_ARGV`. If resolve
  takes no suite, Phase 4 does not start; the gap goes back to loom.manager.
- **Resolved by**: DR-10.

## Technical Debt & Future Work

- **TBD: the hand-run sidecar** (MAINT-40-10). It needs a second suite name beside `corpus`, and LCL-241.
- **TBD: MAINT-37 record-sequence edit**: replace S1/S2a's re-records with `fetch`.
- **TBD: torture member findings**. It has its own sweep and baseline shape.

## Pre-Implementation De-risking Tasks

Load-bearing premises: **read** = settled by source; **runtime** = needs execution. Phases 1–3
depend on no `todo` row. DR-04, DR-07 and DR-10 gate Phases 0 and 4 and need the CI pool or a
shipped LCL-241.

| ID | Premise (kind) | Action | Status |
|----|----------------|--------|--------|
| MAINT-40-00-DR-01 | `--check` accepts a suite name (runtime) | `loom-cli artifact resolve --commit HEAD --check corpus-probe --json`. Worktree `72cd1a99`, loom-cli `v0.89.0 (rev a375bfaf114e)` | **done 2026-10-29: refuted.** Output: `loom-cli: exiting due to failure: parse check "corpus-probe": expected one of a2, a3, a4, a6, a9, a10, a13`. Control: `--check a4` passes check parsing and stops later on `object-store credentials are unresolved: set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY …`. The suite cannot be the check. It lives in the document (design §3.2, §4.2). |
| MAINT-40-00-DR-02 | A second put for one commit and check (runtime) | On a scratch commit of a scratch repo with the store env loaded: put document X; put X again; put a document Y that differs by one byte. Record each exit code and the verbatim stdout/stderr | **done 2026-10-29.** Scratch subject `scratch.invalid/lunar/maint40-probe`, loom-cli v0.89.0. Put X: `stored …`, exit 0. Put X again: `already_present …`, exit 0. Put Y (differs): `immutable conflict at "…/conformance/a3.ctrf.json": differing loom-sha256,size`, exit 1. Records removed with `mc rm`. |
| MAINT-40-00-DR-03 | resolve's JSON and exit codes, and `loom-sha256` = the content sha256 (runtime; read from `artifact_resolve.go:13-31` and loom-delivery `ListResult`) | After DR-02's first put: `resolve --json` on that commit (expect exit 0, `checks[0].record.key`, `record["loom-sha256"]` equal to `sha256sum X`); then on its parent (expect exit 3). Paste both outputs | **done 2026-10-29: confirmed.** HEAD: exit 0, `checks[0].status` = `measured`, `record.key`, `record["loom-sha256"]` = `sha256sum X`. Parent: exit 3, `status` = `unmeasured`, `record` = null. **New fact:** the key's format suffix comes from the check, not the document: SARIF put under `a3` is stored as `a3.ctrf.json`, under `a4` as `a4.sarif.json`. Content is not validated (`{"not":"either"}` was stored under `a3`). |
| MAINT-40-00-DR-04 | On the executor, `--commit HEAD --repo .` derives subject `gitea.internetisalie.net/lunar/lunar`, the store endpoint is reachable from `lunar-ci`, and the org secrets arrive as env (runtime) | A throwaway PR branch whose `corpus` job runs `git remote get-url origin \| sed 's#//[^@]*@#//#'`, `git rev-parse HEAD`, `env \| grep -c -E '^(AWS_ACCESS_KEY_ID\|AWS_SECRET_ACCESS_KEY\|LOOM_OBJECT_BUCKET\|LOOM_OBJECT_ENDPOINT)='` (expect 4), and `loom-cli artifact resolve --commit HEAD --check a3 --json; echo exit=$?` (expect `exit=3`, not a credential or network error). Runs after Phase 0's fetcher, which also proves the registry is reachable from the pool | todo |
| MAINT-40-00-DR-05 | `--no-item` with no `--producer` is accepted for a conformance put (runtime + owner) | Ask `loom.storage` through `loom-cli msg send` (Gap 2.2), and run DR-02 with exactly those flags | **done 2026-10-29: accepted.** `--no-item` with no `--producer` stores with `item=none producer=none`. |
| MAINT-40-00-DR-06 | Which check, format and suite carry corpus findings (owner) | Ask the maintainer (Gap 2.1). Record the answer and set `CHECK` | **done 2026-10-29: `a3`, CTRF, suite `corpus`.** Decided by the owner; matches loom's per-check format map. |
| MAINT-40-00-DR-07 | The install step resolves and fetches loom-cli on `lunar-ci` with `LOOM_MODULES_TOKEN` as env (runtime) | Part of DR-04's throwaway branch: run the design §2.8 step, then `loom-cli --version` | todo |
| MAINT-40-00-DR-09 | The design §4.2 document is valid CTRF 0.2.0, a zero-test document is valid, and `results.environment` admits no key but its listed ones (runtime) | `jsonschema` 4.19.2 `validate` of the §4.2 sample against `ctrf-io/ctrf` `v0.2.0` `schema/ctrf.schema.json` (sha256 `cb9bd075…6cf3`); then with `tests = []`, `summary.tests = summary.failed = 0`; then with mutations | **done 2026-10-29: confirmed.** `sample: valid`; `clean run (zero tests): valid`. Controls that must fail did: `environment.suite` → `Additional properties are not allowed ('suite' was unexpected)`; test without `duration` → `'duration' is a required property`; summary without `start` → `'start' is a required property`; `results.automationDetails` → `Additional properties are not allowed`. |
| MAINT-40-00-DR-10 | LCL-241 as shipped: the argv that selects suite `corpus` on resolve, put and get, and the stored key (runtime) | After LCL-241 releases: `loom-cli artifact put conformance --help`, `resolve --help`, `get --help`; then DR-02's scratch put with that argv, `resolve --json` with it (expect `record.key` ending `a3.corpus.ctrf.json`) and without it (expect exit 3). Set `SUITE_ARGV` | todo (blocked on LCL-241) |
| MAINT-40-00-DR-08 | Gradle exposes the `--tests` pattern to the build script (read, of the public API) | `javap -cp gradle-9.7.1/lib/plugins/gradle-testing-base-9.7.1.jar org.gradle.api.tasks.testing.TestFilter` | **done 2026-10-29: refuted.** The public interface has `getIncludePatterns`, `getExcludePatterns`, `includeTestsMatching`, `excludeTestsMatching`, `setIncludePatterns`, `setExcludePatterns`, `includeTest`, `excludeTest`, `setFailOnNoMatchingTests`, `isFailOnNoMatchingTests`, and no command-line accessor. The shape is read from the run set (design §3.2). |

## Test Case Gaps

- "Findings are written before the ratchet asserts" (MAINT-40-01) has no automated case. Reaching it
  needs a regressing sweep. It is checked by reading `sweepAndRatchet` at review.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
