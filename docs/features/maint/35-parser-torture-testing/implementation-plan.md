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
  - [ ] *(moved to Phase 1 as MAINT-35-00 — provisioning is a requirement, not a de-risking chore)*
- **Verification**: both answers written into `risks-and-gaps.md`. No production or test code kept.

> **Phase 0 gates the plan.** If DR-01 returns a large disagreement count, those are defects to file
> before any baseline is recorded — see the DoD. Baselining a disagreement as expected converts a
> live bug into a permanent one.

### Phase 1: Own the dependency, then build the oracle [Must]
- **Goal**: MAINT-35-00, -01, -03, -03a.
- **Tasks**:
  - [ ] **MAINT-35-00 first**: `tooling/corpus/luac.json` (`<LEVEL>.version/.url/.sha256` — key/value, not TSV; see design §2.0 and BUG-407) and
        `tooling/corpus/fetch-luac.py` — download, **verify sha256**, build, cache
        `test/luac/<version>/luac`, stamp. Mirrors `fetch-corpus.py`. DR-03 records the exact point versions and checksums.
  - [ ] Add **`build-essential`** to `builder-bootstrap.sh:11`, `startup-script.sh:20-22` and
        `.gitea/workflows/build-plugin.yml:116`. None has it today; `gcc`'s presence on the builder
        is as accidental as `luac5.1`'s was. This is the *only* system dependency.
  - [ ] Verify on a **fresh** builder that `fetch-luac.py` produces working binaries with no system
        `luac` present — and that `requireBinary` never consults `PATH`.
  - [ ] `requireBinary` **throws** naming the expected path and `fetch-luac.py` (design §2.0).
  - [ ] Add `ParseOracle` with `Verdict`, `judge`, `binaryFor` (design §2).
  - [ ] Resolution is a `luac.json` lookup to one pinned path — never `PATH`, never a system binary.
  - [ ] `luac -p -` over **stdin** (not a temp file — design §2.2), `ProcessBuilder`, 10 s timeout,
        `destroyForcibly()` → `NotJudged("timeout")`.
  - [ ] Every `judge` call runs **off the EDT** via `executeOnPooledThread` (design §2.2a); `judge`
        asserts it is not on the EDT.
  - [ ] Unit tests in **`net.internetisalie.lunar.corpus.ParseOracleTest`** — the name must **not**
        contain `Corpus`, or `-PwithCorpus` hides it from the routine suite. Covers TC-1, TC-2, TC-3,
        TC-4 (the 5.1-vs-5.4 `//` pair, which is what proves the version match is real), TC-8.
  - [ ] No skip logic: CI runs `fetch-luac.py` like the builder does, so `ParseOracleTest` asserts
        real verdicts everywhere. TC-8 asserts the **throw** using `LUA50`, which is pinned nowhere.
  - [ ] TC-9: `fetch-luac.py` refuses a checksum mismatch and installs nothing.
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
  - [ ] Add the **five** fields to `CorpusMetrics` (design §4.2) — `oracleDisagreements` (plain `Int`), `oracleTimeouts`,
        `oracleSites`, `lexerRoundTripFailures`, `crashes`.
  - [ ] `CorpusBaseline.render`/`parse` per the key table in design §4.3, **including** the new
        `CRASH_PREFIX`/`ORACLE_SITE_KEY` entries in `parse`'s `filterNot` chain (`CorpusMetrics.kt:120-125`)
        and the 20-entry `oracleSites` cap **applied in `run`, not in `render`** (design §4.3) —
        a render-time cap breaks `renderParseRoundTrip`.
  - [ ] `compare`: `oracleDisagreements` is an ordinary numeric delta (design §4.4), **plus** the
        gating table in §4.5 — `lexerRoundTripFailures` appended to `gated`,
        and `crashes` gated **per key** in the `inspectionHits` style (`CorpusMetrics.kt:187-195`).
  - [ ] `oracleDisagreements` is a plain `Int`; a per-file timeout increments the diagnostic
        `oracleTimeouts` and is neither an agreement nor a disagreement (design §2.3).
  - [ ] Plumb `FileTally`'s three new fields through `CorpusSweep.run` (design §4.1).
  - [ ] Call the oracle and invariants from `CorpusSweep.run`.
  - [ ] Extend `BaselineRatchetTest` for all five fields **and** TC-9, including the `crashes` map
        round-trip.
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
  - [ ] `tooling/corpus/torture.json` + `fetch-torture.sh` with sha256 verification.
  - [ ] `LuaTortureCorpusTest` — name contains `Corpus` so `-PwithCorpus` governs it with no build
        change (`build.gradle.kts:266` guard, `excludeTestsMatching` at `:268`); file name matches class name.
  - [ ] Decode inputs ISO-8859-1, not UTF-8 (design §5).
  - [ ] Record `torture-<name>.baseline`.
- **Verification**: corpus ratchet green including the torture member; sweep time recorded against
  MAINT-33's 10-minute corpus ceiling.

## Definition of Done

- MAINT-35-00…-05 and -07 implemented (-06 and -03a are `Should`).
- A **fresh builder** runs `fetch-luac.py` + the corpus gate green with **no system `luac`
  installed** — the check that MAINT-35-00 actually landed and that `PATH` is never consulted.
- The ratchet gates `oracleDisagreements`, `lexerRoundTripFailures` and `crashes`.
- **No disagreement recorded into a baseline without a filed bug ID.**
- Full suite green and corpus ratchet green, run separately; ktlint and doc linters clean.
