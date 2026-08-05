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
  - [x] **DR-01 — done 2026-08-05.** 419 files judged: **0 false rejects**, 2 false accepts (one by
        design — the level-agnostic parse; one real, filed as **BUG-409**). Recorded in
        `risks-and-gaps.md`; the design consequence (gate false rejects only) is folded into §2.3
        and MAINT-35-02.
  - [x] **DR-02 — done 2026-08-05.** `squeek502/fuzzing-lua` **v0.2.0** carries one asset,
        `fuzzing-lua-data.tar.gz` (181 282 bytes). Stable and checksummable; MAINT-35-06 stands.
  - [x] **DR-03 — done 2026-08-05.** All five tarballs downloaded, digests **matched against
        lua.org's published sha256** (not just self-computed), and each built a working `luac` on the
        builder with the C toolchain alone. Two guessed pins corrected: 5.4.7 → **5.4.8**, 5.5.x →
        **5.5.1**. Version discrimination re-verified on the *built* binaries: `1 // 2` rejected by
        5.1.5/5.2.4, accepted by 5.3.6/5.4.8/5.5.1. `luac -p -` reads stdin on every version.
  - [ ] *(moved to Phase 1 as MAINT-35-00 — provisioning is a requirement, not a de-risking chore)*
- **Verification**: all three answers written into `risks-and-gaps.md`. No production or test code
  kept — the tarballs and builds live in scratch, and Phase 1's `fetch-luac.py` recreates them from
  the recorded pins.

> **DR-01 already paid for Phase 0.** It corrected a `Must` requirement before any code was written
> (both directions gated → false rejects only), found BUG-409, and hit design risk R5 in its first
> five minutes — `luac`'s stderr carries non-UTF-8 bytes when it echoes an invalid token, so the
> harness needed `errors="replace"`. The real `ParseOracle` must decode `luac` output tolerantly for
> the same reason.

> **Phase 0 gates the plan.** If DR-01 returns a large disagreement count, those are defects to file
> before any baseline is recorded — see the DoD. Baselining a disagreement as expected converts a
> live bug into a permanent one.

### Phase 1: Own the dependency, then build the oracle [Must] — **DONE 2026-08-05**
- **Goal**: MAINT-35-00, -01, -03, -03a.
- **Tasks**:
  - [x] **MAINT-35-00 first**: `tooling/corpus/luac.json` (`<LEVEL>.version/.url/.sha256` — key/value, not TSV; see design §2.0 and BUG-407) and
        `tooling/corpus/fetch-luac.py` — download, **verify sha256**, build, cache
        `test/luac/<version>/luac`, stamp. Mirrors `fetch-corpus.py`. DR-03 records the exact point versions and checksums.
  - [x] Add **`build-essential`** to `builder-bootstrap.sh:11`, `startup-script.sh:20-22` and
        `.gitea/workflows/build-plugin.yml:116`. None has it today; `gcc`'s presence on the builder
        is as accidental as `luac5.1`'s was. This is the *only* system dependency.
  - [ ] Verify on a **fresh** builder that `fetch-luac.py` produces working binaries with no system
        `luac` present — and that `requireBinary` never consults `PATH`.
  - [x] `requireBinary` **throws** naming the expected path and `fetch-luac.py` (design §2.0).
  - [x] Add `ParseOracle` with `Verdict`, `judge`, `binaryFor` (design §2).
  - [x] Resolution is a `luac.json` lookup to one pinned path — never `PATH`, never a system binary.
  - [x] `luac -p -` over **stdin** (not a temp file — design §2.2), `ProcessBuilder`, 10 s timeout,
        `destroyForcibly()` → `NotJudged("timeout")`.
  - [x] Every `judge` call runs **off the EDT** via `executeOnPooledThread` (design §2.2a); `judge`
        asserts it is not on the EDT.
  - [x] Unit tests in **`net.internetisalie.lunar.corpus.ParseOracleTest`** — the name must **not**
        contain `Corpus`, or `-PwithCorpus` hides it from the routine suite. Covers TC-1, TC-2, TC-3,
        TC-4 (the 5.1-vs-5.4 `//` pair, which is what proves the version match is real), TC-8.
  - [x] **Revised.** The plan said CI would run `fetch-luac.py` so there was no skip logic. Building
        five Lua releases costs ~2 min on **every push**, so `ParseOracleTest` is excluded via
        `-PexcludeExternalFixtureTests` instead, beside the two tests with the same out-of-repo
        dependency. Trade-off recorded as **R9** rather than taken silently. TC-8 still asserts the
        **throw** using `LUA50`, which is pinned nowhere.
  - [x] TC-9: `fetch-luac.py` refuses a checksum mismatch and installs nothing.
- **Verification**: full suite green; TC-3 and TC-4 disagree with each other by level, as expected.

### Phase 2: `LexerInvariants` [Must] — **DONE 2026-08-05**
- **Goal**: MAINT-35-04 and -05.
- **Tasks**:
  - [x] `LexerInvariants.check` over `LuaLexer` (not `_LuaLexer` — design §3.1).
  - [x] Catch `Throwable`, record the class name only.
  - [x] Unit tests: TC-6, TC-7 (BUG-390's shape: 5 000-deep nesting), invalid-Lua and
        line-ending cases. 7 tests, all green.
  - [x] **Added `unmergedTokens`, which the plan did not have.** Mutation showed the round-trip
        does **not** catch BUG-392: reintroducing the defect (`while` → `if`) left every round-trip
        assertion green while `LuaLongStringBlankLineTest` failed, because the defect re-partitioned
        characters rather than losing them. Counting internal `LONGSTRING*`/`LONGCOMMENT*` tokens
        that escape the merge is BUG-392's actual signature, and it fails under the same mutation.
        The false claim is corrected in requirements and design.
- **Verification**: full suite green.

### Phase 3: Wire into the corpus [Must] — **DONE 2026-08-05**
- **Goal**: MAINT-35-02 and -07.
- **Tasks**:
  - [x] Add the **five** fields to `CorpusMetrics` (design §4.2) — `oracleDisagreements` (plain `Int`), `oracleTimeouts`,
        `oracleSites`, `lexerRoundTripFailures`, `crashes`.
  - [x] `CorpusBaseline.render`/`parse` per the key table in design §4.3, **including** the new
        `CRASH_PREFIX`/`ORACLE_SITE_KEY` entries in `parse`'s `filterNot` chain (`CorpusMetrics.kt:120-125`)
        and the 20-entry `oracleSites` cap **applied in `run`, not in `render`** (design §4.3) —
        a render-time cap breaks `renderParseRoundTrip`.
  - [x] `compare`: `oracleDisagreements` is an ordinary numeric delta (design §4.4), **plus** the
        gating table in §4.5 — `lexerRoundTripFailures` appended to `gated`,
        and `crashes` gated **per key** in the `inspectionHits` style (`CorpusMetrics.kt:187-195`).
  - [x] `oracleDisagreements` is a plain `Int`; a per-file timeout increments the diagnostic
        `oracleTimeouts` and is neither an agreement nor a disagreement (design §2.3).
  - [x] Plumb `FileTally`'s three new fields through `CorpusSweep.run` (design §4.1).
  - [x] Call the oracle and invariants from `CorpusSweep.run`.
  - [x] Extend `BaselineRatchetTest` for all five fields **and** TC-9, including the `crashes` map
        round-trip.
- **Verification**: full suite **2366 / 0**, ktlint clean. The corpus ratchet itself is **not yet
  re-recorded** — that is Phase 4, which must file what the oracle finds before baselining it.
- **Note**: `RockspecSourcePathProviderTest` failed once during this gate and passed three re-runs
  plus the next full suite unchanged. Filed as **BUG-410** rather than left as folklore; nothing in
  this phase touches `rocks/`.

### Phase 4: Record baselines, file what the oracle finds [Must] — **DONE 2026-08-05**
- **Goal**: turn Phase 0's findings into either fixes or filed defects.
- **Tasks**:
  - [x] **Nothing new to file.** The gated metric — false rejects — is **0 on all four members**,
        419 files. The two false accepts are diagnostic and already accounted for: one is the
        by-design level-superset parse, the other is **BUG-409** (filed during Phase 0).
  - [x] Baselines re-recorded. **No existing metric moved** — the diff is 18 added lines and zero
        changed ones, so the new checks were additive rather than a re-baseline of old numbers.
  - [x] Confirmed the oracle actually ran rather than passing vacuously: `oracleSites` records
        exactly the two known false accepts, reproduced independently by the shipped code against
        the pinned 5.1.5 build. An inert oracle would have left that list empty while every gated
        counter still read 0.
- **Verification**: corpus ratchet green **before** recording (the new counters were already clean)
  and again after; full suite 2366 / 0. No disagreement baselined without a bug ID.

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
