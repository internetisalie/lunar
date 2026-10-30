---
id: "MAINT-40"
title: "40: Corpus Sweep Results Stored Per Commit"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-40: Corpus Sweep Results Stored Per Commit

## The gap

The corpus sweep's reference results are committed files under `src/test/resources/corpus/`:
per-symbol **counts**, capped at ten symbols per inspection, compared by a ratchet that lets
improvements pass silently. Two consequences follow:

- **A green gate does not prove the baselines are current.** Improvements accumulate unrecorded,
  so any work that needs a trustworthy "before" has to re-record the full corpus first.
  [[MAINT-37]]'s record sequence carries exactly this cost.
- **Counts hide offsetting changes.** One false positive fixed and a new one introduced leave the
  count unchanged.

## Scope

The sweep emits its results as **findings** (rule, file, location, stable fingerprint) rather
than aggregated counts, published as one **CTRF** document per run with one failed test per
finding. Each run is stored against the commit it ran on, in loom's per-commit conformance store,
as check `a3` suite `corpus` (key `a3.corpus.ctrf.json`). A later run, or any tooling, fetches the
stored document for a commit, or for the nearest ancestor that has one, and compares against it.

The identity is derived from the run, never typed, and comparison runs in Lunar's own tooling
(such as [[MAINT-37]]'s attribution classifier), never the store's comparator.

## Relationship to neighbouring work

- **[[MAINT-37]]** does not depend on this. With MAINT-40 in place, MAINT-37's record sequence
  replaces its starting re-record and its old-pin reference run with fetches of stored results.
  That is an edit to a single procedure.
- **[[MAINT-39]]** gains an attributable "before" for the annotated corpus from the first run.
- Whether the committed baselines or the stored results are the gate's reference is decided in
  design §3.8.

## Out of Scope

- **The torture member** (`LuaTortureCorpusTest`, `TortureCorpus.kt`). It does not run through
  `CorpusSweep`, and its baseline has its own shape.
- **`LuaAnnotatedFixtureSweepTest`, `LuaInspectionParityTest`**: they are not sweeps of a manifest
  member.
- **Storing from GitHub CI, the tag lane, or the gce-builder.** Only the Gitea `corpus` job on
  `lunar-ci` stores.
- **Editing MAINT-37's record sequence** to fetch instead of re-record (risks, Technical Debt).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-40-01 | **Findings per hit** | M | Full | Every non-attribution sweep of a member writes `build/corpus-findings/<member>.json`, holding one finding per hit of every defect counter the baseline carries (design §4.1). It is written before the ratchet asserts. |
| MAINT-40-02 | **Counts derived from findings** | M | Full | Every counter in `CorpusMetrics` that counts hits is computed from the findings list. A record run reproduces every committed baseline byte for byte. |
| MAINT-40-03 | **One CTRF document** | M | Full | `corpus_ctrf.py assemble` turns one invocation's findings files into one CTRF 0.2.0 document holding one `failed` test per finding (design §4.2). A clean run has zero tests. The same inputs give the same bytes. |
| MAINT-40-04 | **Stable fingerprint** | M | Full | Each test's `testId` is its finding's fingerprint, and its `name` contains it. It is unchanged across Lunar commits when member, rule, path, offsets and symbol are unchanged, whatever the message text. |
| MAINT-40-05 | **Derived environment identity** | M | Full | `results.environment.testEnvironment` is computed from the sweep shape, each member's manifest comparability fields and its observed library pins (design §3.2). Nobody types it. Mixed or partial run sets are refused. |
| MAINT-40-06 | **Store from CI** | M | Partial | The Gitea `corpus` job puts the document against `HEAD` as check `a3` suite `corpus`. A record that is already stored is not an error. Any other store failure fails the job. The ratchet's verdict is unchanged. Needs loom-cli v0.90.0 or later (`--suite`, LCL-241). |
| MAINT-40-07 | **Fetch nearest ancestor** | M | Full | `corpus_ctrf.py fetch` returns the stored corpus document of the nearest first-parent ancestor that has one, optionally with a matching environment identity, within a depth bound. |
| MAINT-40-08 | **Finding-level compare** | M | Full | `corpus_ctrf.py diff` reports newly failing tests (new findings), absent tests (resolved findings) and count changes per rule. It refuses documents whose identities differ ("not comparable", never a regression). |
| MAINT-40-09 | **Baselines stay the gate** | M | Full | The ratchet keeps reading the committed `.baseline` files. Stored results are the attributable "before", never the gate's reference. |
| MAINT-40-10 | **Hand-run sidecar** | C | Future Work | A member-alone or hand run is stored under its own suite name. The store has one `corpus` key per commit, so until a second suite is chosen `store` refuses any run whose shape is not `full` (design §3.5). |

## Test Cases

Each case names the mutation that turns it red. Python cases live in
`tooling/corpus/test_corpus_ctrf.py`. Kotlin cases live in
`src/test/kotlin/net/internetisalie/lunar/corpus/SweepFindingCountsTest.kt`.

| # | Req | Given | When | Then | Red under |
|---|-----|-------|------|------|-----------|
| 1 | 01 | The pinned corpus at the phase commit, after the Phase 1 baseline control (implementation-plan) | `test -PwithCorpus --rerun --no-build-cache --tests '*Corpus*'`. Then count the luacheck file's findings per rule | `corpus.parseError` and `inspection.LuaUndeclaredVariable` equal `luacheck.baseline`'s `parseErrors` and `inspection.LuaUndeclaredVariable` after the control (3 and 35 at `72cd1a99`) | `tally` emits no parse-error findings (→ 0) |
| 2 | 02 | The TC 2 findings list below | `CorpusFindingCounts.derive` | every field equals the TC 2 expectation below | each TC 2 mutant below |
| 3 | 02 | Phase 1 commit, after the Phase 1 baseline control | record run (implementation-plan Gates), copy back, `git status --porcelain -- src/test/resources/corpus/` | prints nothing | unattributed counted without subtracting the file's parse errors (luacheck `inspection.unattributed` moves off 5) |
| 4 | 03 | Manifest with members `m1`, `m2`. Two findings files sharing one `jvmToken`, keys written in shuffled order; `m1`'s `libraries` is written `{"zeta": …, "alpha": …}` in that order; `m2`'s file has no findings | `assemble` twice, the second time with the files' JSON keys reordered | the two outputs are byte-identical, and equal `json.dumps(json.loads(out), sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode() + b"\n"`. `reportFormat` = `CTRF`, `specVersion` = `0.2.0`. Every test has `status` = `failed` and `suite` = `[m1]`; `summary.tests` = `summary.failed` = the number of `m1` findings and `summary.start` = `summary.stop` = 0; `environment.extra.members` has keys `m1`, `m2`. Every key outside an `extra` object is in the CTRF 0.2.0 allowed set (design §4.2) | `json.dumps` without `sort_keys` (`zeta` precedes `alpha`); a timestamp in `summary.start`; a key outside the allowed set |
| 5 | 04 | Document A has a finding with message `Unused local 'x'`. Document B has the same finding with message `Local 'x' is never used`. A third run holds two identical findings. A fourth holds one finding in `m1` and the same rule, path, offsets and symbol in `m2`. A fifth holds two findings with one key (design §3.3) and messages `m-a`, `m-b` | `assemble` each; the fifth twice, its findings list in each order | A's and B's `testId`s are equal; the two identical findings get distinct `testId`s; the `m1` and `m2` tests get distinct `testId`s. Each `name` ends with its `testId`. The fifth's two outputs are byte-identical | message included in the fingerprint; ordinal dropped; member dropped from the key; ordinal taken in input order (the fifth's outputs differ) |
| 6 | 05 | TC 4 fixture | `identity_of` on: (a) unchanged; (b) `m1` commit changed; (c) one library version changed; (d) the `$comment` and `url` changed; (e) one file only; (f) two `jvmToken`s; (g) TC 4's manifest plus a third member `m3`, with the `m1` and `m2` files | (a) = baseline identity; (b), (c) differ; (d) equal; (e) `member-m1-<16 hex>`; (f), (g) raise `NotComparable` | whole manifest hashed (d); libraries omitted (c); token check skipped (f) |
| 7 | 06, 10 | Stub `loom-cli` (design §4.3). Scenarios: resolve exit 3; resolve measured with the same sha256; measured with a different sha256; resolve exit 1; put exit 1; a member-alone findings dir | `store` | put called once with `["artifact","put","conformance",<doc>,"--commit","HEAD","--check","a3","--no-item","--repo",".",*SUITE_ARGV]`, exit 0; no put, exit 0; no put, `WARNING` line, exit 0; exit 1; exit 1; no loom-cli call at all, exit 2 | exit 3 treated as an error; a differing sha256 treated as a failure; `SUITE_ARGV` left off put; shape check dropped |
| 8 | 06 | The first push to `main` after Phase 4 merges | `loom-cli artifact resolve --commit <merge sha> --check a3 --json` plus `SUITE_ARGV` | exits 0, `status` = `measured`. The fetched document's `testEnvironment` starts `full-`. The same resolve without `SUITE_ARGV` exits 3 (a3's primary slot is untouched) | the store step never runs (exit 3); the put omits the suite (second resolve exits 0) |
| 9 | 07 | Temp repo `C1←C2←C3`, where C3 merges side commit S. Stub: C1 and S measured with corpus documents; C2 measured with a CTRF document whose `tool.name` is `other` | `fetch --commit C3`; then with `--like` naming another identity; then with `--max-depth 2` | C1's document, `distance=2`; exit 3; exit 3. Every resolve argv ends with `SUITE_ARGV` | `rev-list` without `--first-parent` (returns S); the tool-name check dropped (returns C2); `SUITE_ARGV` left off resolve |
| 10 | 08 | Same identity. `after` swaps one `inspection.LuaUnusedLocal` finding for another in the same rule (count unchanged) and adds one `corpus.parseError` | `diff before after` | one `NEW` line and one `RESOLVED` line for `LuaUnusedLocal`, a `COUNT corpus.parseError 0 1` line, exit 1. Identical inputs: no output, exit 0. Different `testEnvironment`s, or different `files`: `NOT COMPARABLE` on stderr, exit 2 | comparing counts only (no `NEW`/`RESOLVED` lines for the swap); identity check dropped (exit 1 on a differing identity) |

**TC 2 fixture** (`CorpusFinding`s built by a test-local helper that sets unlisted offsets to 0, line and column to 0 and message to the rule; file-level findings use `fileFinding`):

| Path | Findings |
|---|---|
| `a.lua` | `PARSE_ERROR` line 3 message `e1`; 3 × `UNATTRIBUTED_HIGHLIGHT`; 2 × `inspection.LuaUnusedLocal` symbol `x`; `CRASH` `parse:IllegalStateException` |
| `b.lua` | `PARSE_ERROR` line 1 `e2`; `PARSE_ERROR` line 9 `e3`; `CRASH` `parse:IllegalStateException`; `CRASH` `lex:StackOverflowError`; `ORACLE_TIMEOUT` |
| `c.lua` | `UNMERGED_TOKENS` count 7; `LEXER_ROUND_TRIP`; `ORACLE_FALSE_REJECT`; `HIGHLIGHT_FAILURE` |
| `d.lua` | `UNMERGED_TOKENS` count 2; `ORACLE_FALSE_REJECT`; `LEXER_ROUND_TRIP`; 4 × `UNRESOLVED_REQUIRE`, symbols `"m"`, `"n"`, `"o"`, `"p"` |
| `e.lua` | `ORACLE_FALSE_ACCEPT` |
| `f.lua` | `ORACLE_TIMEOUT`; `LEXER_ROUND_TRIP` |

**TC 2 expectation:** `parseErrors` 3; `parseErrorFiles` `[a.lua:3:e1, b.lua:1:e2, b.lua:9:e3]`;
`unresolvedRequires` 4; `oracleDisagreements` 2; `oracleFalseAccepts` 1; `oracleTimeouts` 2;
`oracleSites` `[falseReject:c.lua, falseReject:d.lua, falseAccept:e.lua]`; `lexerRoundTripFailures` 3;
`unmergedTokens` 9; `crashes` `{parse:IllegalStateException=2, lex:StackOverflowError=1}`;
`inspectionHits` `{LuaUnusedLocal=2, unattributed=2, highlightFailures=1}`; `symbolHits` and
`allSymbolHits` `{LuaUnusedLocal.x=2}`.

**TC 2 mutants**, each red: unattributed netted over all files (key absent); the per-file floor
dropped (key absent); `unmergedTokens` counting findings instead of summing `count` (2);
`HIGHLIGHT_FAILURES` never set (key absent); crashes not grouped by message; `ORACLE_FALSE_REJECT`
routed to `oracleFalseAccepts` or `oracleTimeouts` (`oracleDisagreements` 0); symbols taken from
non-`inspection.*` findings (an extra key); `oracleSites` sorted plainly (`falseAccept` first);
any two of `unresolvedRequires`, `oracleFalseAccepts`, `oracleTimeouts`, `lexerRoundTripFailures`
swapped (their expected values 4, 1, 2, 3 are distinct).

**MAINT-40-09 has no mutation-bearing case.** It is a decision about which file the ratchet reads,
and the ratchet's code does not change. Its existing tests (`BaselineRatchetTest`) are its evidence.
It is verified by inspection: no step of the `corpus` job reads the store before the ratchet.

## Implementation Status

Phases 0–4 are implemented. MAINT-40-06 is Partial until TC 8 holds on the first `main` push
after merge.

- **-01, -02**: `CorpusFinding.kt`, `CorpusFindingCounts.kt`, `CorpusFindingsFile.kt`, and
  `CorpusSweep` deriving every counter through `CorpusFindingCounts.derive`. TC 2 is
  `SweepFindingCountsTest`. TC 1 and TC 3 hold on the pinned corpus.
- **-03, -04, -05**: `tooling/corpus/corpus_ctrf.py` `assemble`; TC 4–6 in `test_corpus_ctrf.py`. The
  document assembled from a full run validates against the pinned CTRF 0.2.0 schema.
- **-07, -08**: `fetch` and `diff`; TC 9–10 against the design §4.3 stub `loom-cli`.
- **-06, -10's refusal**: `corpus_ctrf.py store` (TC 7, against the design §4.3 stub), `SUITE_ARGV`
  from DR-10, and the Gitea `corpus` job's install, store and report steps (design §2.8). TC 8 is
  run after merge.
- **-09**: The ratchet still reads the committed baselines (`CorpusGuards.assertRatchet` is
  unchanged). By inspection of `.gitea/workflows/build-plugin.yml`, no `corpus` job step reads the
  store before the ratchet: the only `loom-cli` call before it is `loom-cli --version`. The store
  step runs after the ratchet, and nothing it reads feeds the ratchet.

## Acceptance Criteria

- [x] TC 1–6 and 9–10 pass (the Phases 1–3 PR). TC 3 prints nothing.
- [x] TC 7 passes.
- [ ] TC 8 holds on the first `main` push after Phase 4 merges.
- [ ] The CI `corpus` job is green on the Phase 4 PR, and its log shows the store outcome line.

## Non-Functional Requirements

- Findings are collected on the sweep's existing EDT test thread. The sweep does no extra
  highlighting or PSI pass beyond the per-element positions it already visits.
- The Python tooling uses the stdlib only (precedent: `tooling/corpus/*.py`).

## Dependencies

- Org secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `LOOM_MODULES_TOKEN` (read scope, for the
  loom-cli download) and org variables `LOOM_OBJECT_BUCKET`, `LOOM_OBJECT_ENDPOINT`: present on Gitea
  org `lunar` (2026-10-29).
- `loom-cli` in the `corpus` job, fetched unpinned at job start (implementation-plan Phase 0).
- **Loom LCL-241** (a suite segment in the conformance key), for Phase 0 and Phase 4 only: met,
  shipped in loom-cli v0.90.0 (risks Gap 2.3, DR-10).
- The de-risking rows in [risks-and-gaps.md](risks-and-gaps.md): all `done`.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
