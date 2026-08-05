---
id: "MAINT-35-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-35"
folders:
  - "[[features/maint/35-parser-torture-testing/requirements|requirements]]"
---

# MAINT-35: Implementation Plan

Sequenced from [`design.md`](design.md). Baseline is `main` @ `c339f169` (2341 pass / 0 fail /
1 ignored; corpus ratchet green on four members).

**Run the corpus gate and the full suite as separate invocations.** `test -PwithCorpus
--rerun --no-build-cache` wedged the daemon on 2026-08-04 (two live workers, load 0.00, no output
for 20 min; recovered with `pkill -9 -f GradleDaemon`). Never gate on an isolated `--tests` pattern
either — the full suite is the gate (isolated-tests-masks-full-suite lesson).

## Phases

### Phase 0: De-risk [Must]
- **Goal**: find out what the oracle actually says before building anything around it.
- **Tasks**:
  - [ ] DR-01 — throwaway harness: run `luac5.1 -p` over every file in all four corpus members and
        diff against the recorded `parseErrors`. Record the disagreement count and a sample.
  - [ ] DR-02 — confirm squeek502's minimized corpus is published as a stable, checksummable release
        asset; record URL + sha256, or drop MAINT-35-06 to `Could`.
  - [ ] **Provision `lua5.1`** (which carries `luac5.1`) in all three paths — `builder-bootstrap.sh:11`,
        `startup-script.sh:20-22`, `.gitea/workflows/build-plugin.yml:116`. It is present on the
        builder today only as a transitive dependency of a hand-installed `lua-check`; a VM
        re-create would strip it and red the gate (R8). This is a task, not a confirmation.
- **Verification**: both answers written into `risks-and-gaps.md`. No production or test code kept.

> **Phase 0 gates the plan.** If DR-01 returns a large disagreement count, those are defects to file
> before any baseline is recorded — see the DoD. Baselining a disagreement as expected converts a
> live bug into a permanent one.

### Phase 1: `ParseOracle` [Must]
- **Goal**: MAINT-35-01 and -03.
- **Tasks**:
  - [ ] Add `ParseOracle` with `Verdict`, `judge`, `binaryFor` (design §2).
  - [ ] Versioned-name resolution only; never a bare `luac`.
  - [ ] `luac -p -` over **stdin** (not a temp file — design §2.2), `ProcessBuilder`, 10 s timeout,
        `destroyForcibly()` → `Unavailable("timeout")`.
  - [ ] Every `judge` call runs **off the EDT** via `executeOnPooledThread` (design §2.2a); `judge`
        asserts it is not on the EDT.
  - [ ] Unit tests in **`net.internetisalie.lunar.corpus.ParseOracleTest`** — the name must **not**
        contain `Corpus`, or `-PwithCorpus` hides it from the routine suite. Covers TC-1, TC-2, TC-3,
        TC-4 (the 5.1-vs-5.4 `//` pair, which is what proves the version match is real), TC-8.
  - [ ] **Skip-vs-fail rule, because CI has no `luac5.1`**: `.gitea/workflows/build-plugin.yml:116`
        installs `lua5.4` only, so TC-1…TC-3 would be `Unavailable` there. Each such test asserts on
        `Verdict` when the binary resolves and is **skipped with a printed reason** when it does not
        (`Assume`-style), so CI stays green while the builder — which Phase 0 provisions — really
        exercises them. TC-8 is the inverse and always runs: it asserts `Unavailable` for a level
        with no binary.
- **Verification**: full suite green; TC-3 and TC-4 disagree with each other by level, as expected.

### Phase 2: `LexerInvariants` [Must]
- **Goal**: MAINT-35-04 and -05.
- **Tasks**:
  - [ ] Add `LexerInvariants.check` over `LuaLexer` (not `_LuaLexer` — design §3.1).
  - [ ] Catch `Throwable`, record the class name only.
  - [ ] Unit tests: TC-6 (round-trip incl. long strings and `\z`), TC-7 (BUG-390's shape).
  - [ ] Regression assertion: BUG-392's fixture round-trips (TC-5's lexer half).
- **Verification**: full suite green.

### Phase 3: Wire into the corpus [Must]
- **Goal**: MAINT-35-02 and -07.
- **Tasks**:
  - [ ] Add the **four** fields to `CorpusMetrics` (design §4.2) — `oracleDisagreements` (`Int?`),
        `oracleSites`, `lexerRoundTripFailures`, `crashes`.
  - [ ] `CorpusBaseline.render`/`parse` per the key table in design §4.3, **including** the new
        `CRASH_PREFIX`/`ORACLE_SITE_KEY` entries in `parse`'s `filterNot` chain (`CorpusMetrics.kt:120-125`)
        and the 20-entry `oracleSites` cap **applied in `run`, not in `render`** (design §4.3) —
        a render-time cap breaks `renderParseRoundTrip`.
  - [ ] `compare`: the four-case null table in design §4.4 as a pre-check before the `Triple`
        pipeline, **plus** the gating table in §4.5 — `lexerRoundTripFailures` appended to `gated`,
        and `crashes` gated **per key** in the `inspectionHits` style (`CorpusMetrics.kt:187-195`).
  - [ ] `oracleDisagreements` is null **iff `binaryFor(level) == null`** (design §2.3); a per-file
        timeout increments the diagnostic `oracleTimeouts` and must never null the metric.
  - [ ] Plumb `FileTally`'s three new fields through `CorpusSweep.run` (design §4.1).
  - [ ] Call the oracle and invariants from `CorpusSweep.run`.
  - [ ] Extend `BaselineRatchetTest` for all four fields **and** TC-9, including the `crashes` map
        round-trip and the null→number IMPROVED row.
- **Verification**: full suite green; corpus ratchet run separately.

### Phase 4: Record baselines, file what the oracle finds [Must]
- **Goal**: turn Phase 0's findings into either fixes or filed defects.
- **Tasks**:
  - [ ] For every disagreement: file a BUG row with the file, the two verdicts and a reduction.
  - [ ] Only then re-record the four baselines with `-PrecordCorpusBaseline`.
  - [ ] State in the commit how many disagreements were baselined and under which bug IDs.
- **Verification**: corpus ratchet green; no disagreement baselined without a bug ID.

### Phase 5: Torture corpus [Should]
- **Goal**: MAINT-35-06.
- **Tasks**:
  - [ ] `tooling/corpus/torture.tsv` + `fetch-torture.sh` with sha256 verification.
  - [ ] `LuaTortureCorpusTest` — name contains `Corpus` so `-PwithCorpus` governs it with no build
        change (`build.gradle.kts:266` guard, `excludeTestsMatching` at `:268`); file name matches class name.
  - [ ] Decode inputs ISO-8859-1, not UTF-8 (design §5).
  - [ ] Record `torture-<name>.baseline`.
- **Verification**: corpus ratchet green including the torture member; sweep time recorded against
  MAINT-33's 10-minute corpus ceiling.

## Definition of Done

- MAINT-35-01…-05 and -07 implemented (-06 is `Should`).
- The ratchet gates `oracleDisagreements`, `lexerRoundTripFailures` and `crashes`.
- **No disagreement recorded into a baseline without a filed bug ID.**
- An unavailable oracle is absent from the baseline and printed, never `0` (TC-8, TC-9).
- Full suite green and corpus ratchet green, run separately; ktlint and doc linters clean.
