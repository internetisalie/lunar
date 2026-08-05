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
  - [x] Verify on a **fresh** builder that `fetch-luac.py` produces working binaries with no system
        `luac` present — and that `requireBinary` never consults `PATH`. **Done 2026-08-05**, after
        the phase was first marked DONE with this unchecked: `LUNAR_LUAC_ROOT=/tmp/luac-fresh` on the
        builder built all five from source (5.1.5, 5.2.4, 5.3.6, 5.4.8, 5.5.1), each reporting its
        own version. `requireBinary` resolving without `PATH` is covered by
        `ParseOracleTest.testMissingBinaryNamesTheFetchScript`, which points at a tree holding only
        the manifest and asserts the throw names `test/luac/5.1.5` and `fetch-luac.py`.
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
  - [x] TC-9: `fetch-luac.py` refuses a checksum mismatch and installs nothing — now **automated**
        (`ParseOracleTest.testChecksumMismatchInstallsNothing` drives the real script through a
        `file://` entry, needing neither the network nor the real pins) rather than manual-only.
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

### Phase 5: Torture corpus [Should] — **DONE 2026-08-05**
- **Goal**: MAINT-35-06.
- **Tasks**:
  - [x] `tooling/corpus/torture.json` + `fetch-torture.py` with sha256 verification. **Python, not
        the `.sh` the design named**: BUG-407's root cause was a hand-rolled parser in a shell
        script, and reading a JSON manifest with `cut` would repeat it in a new format. Pinned to
        squeek502/fuzzing-lua **v0.2.0**, `fuzzing-lua-data.tar.gz`, 181 282 bytes,
        sha256 `608dbf84…4156e` (DR-02).
  - [x] `LuaTortureCorpusTest` — name contains `Corpus` so `-PwithCorpus` governs it with no build
        change (`build.gradle.kts:266` guard, `excludeTestsMatching` at `:270`); file name matches class name.
  - [x] Decode inputs ISO-8859-1, not UTF-8 (design §5). Total by construction, so a fuzz corpus's
        invalid UTF-8 cannot break the round-trip at the *decode* and masquerade as a lexer defect.
  - [x] `TortureMetrics`/`TortureBaseline` rather than reusing `CorpusMetrics` — design §5.1's
        reasoning holds: `assertIdentity` identity-checks `commit` and `requires`, and a torture
        member has neither. `parseErrors` is recorded but **not gated**: a fuzz corpus is mostly
        invalid Lua on purpose, so the count describes the corpus, not the parser.
  - [x] Record `torture-fuzzing-lua.baseline`, and prove the new ratchet can fail — six unit tests
        in `BaselineRatchetTest` (routine suite, no fixture) covering round-trip, each gated
        invariant separately, the ungated `parseErrors`, the pin identity check and the absent
        baseline.
- **Verification**: 1 696 inputs in **5.7 s** — against MAINT-33's 10-minute ceiling, this is the
  cheapest member by two orders of magnitude, so R7 does not bite. **0** round-trip failures, **0**
  unmerged tokens, **0** crashes, **0** timeouts.
- **What it found on the first run**: exactly one false reject — **BUG-411**, `\v` and `\f` missing
  from `lua.flex`'s whitespace set, where PUC dispatches on `isspace()`. Verified accepted by all
  five pinned oracles. Filed rather than fixed inline: the fix needs a lexer regeneration, which
  belongs in its own commit. The baseline records `oracleDisagreements=1` **with** that bug ID, as
  the DoD requires.

### Phase 6: Adversarial review remediation [Must] — **DONE 2026-08-05**
- **Goal**: close the 18 findings from the review of Phases 0–4. The review was run *after* the
  phases rather than per-phase, which is why this is its own phase and not an amendment.
- **Tasks**:
  - [x] **`ParseOracle.run` rewritten.** Two defects in one method: stderr was read to EOF *before*
        `waitFor`, so a hung child blocked the read forever and the timeout branch was unreachable;
        and the whole source was written to a stdin pipe, which raises EPIPE when luac rejects early
        and exits (measured against the shipped 5.1.5 build: fine at 50 kB, broken at 100 kB and
        900 kB). Now: diagnostics redirected to a temp **file** so there is no pipe to drain, the
        stdin write is guarded, then `waitFor(timeout)`. Regression tests
        `testEarlyRejectionOfALargeSourceStillYieldsAVerdict` / `testLargeValidSourceIsAccepted`
        (~1.1 MB each, both directions).
  - [x] `judge` degrades a stuck pooled task to `NotJudged` instead of letting an uncaught
        `TimeoutException` abort the sweep and leak the thread.
  - [x] `ThreadingAssertions.softAssertBackgroundThread()` added — the plan claimed this assertion
        existed and it did not. Binary resolution memoised per (root, level): it was re-reading and
        re-parsing `luac.json` on the EDT once per file, 419× on the largest member.
  - [x] **`fetch-luac.py` verifies before installing.** `copy2`/`chmod`/`stamp.write_text` all ran
        *before* the `luac -v` self-check, so a failed check left a stamped — therefore trusted —
        binary that the next run skipped over. Also pins `extractall(filter="data")`.
  - [x] **Anti-vacuity is now enforced, not asserted in prose.** `ParseOracle.assertDiscriminates`
        judges a known-good and a known-bad snippet before any sweep; `requireBinary` alone cannot
        see an oracle that accepts everything. `LuaCorpusSweepTest.report` also prints
        `oracleDisagreements`/`oracleTimeouts`/`unmergedTokens` and the sites, which nothing did.
  - [x] **`LuaTokenTypes.LONGCOMMENT` added to `INTERNAL_TOKENS`** — the long-comment *body*, the
        analogue of `LONGSTRING` (the merged output is the different object
        `LuaElementTypes.LONGCOMMENT`). Mutation-proved with a new **unterminated** long-comment
        fixture: with a closing bracket present the truncated run also strands `LONGCOMMENT_END`,
        which was already listed, so the omission was invisible.
  - [x] `oracleSites` orders **falseReject before falseAccept** ahead of the 20-site cap. Alphabetic
        sorting put `falseAccept` first, so past the cap the gated direction was the part discarded.
  - [x] `CorpusSweep.run`/`sweepRoot` reduced to 3 args (engineering contract §3) via a private
        `SweepContext`; `checkoutDir` is derived rather than passed.
  - [x] `DescriptionRecord.concat` — the indexer still hand-wrote `"$existing|$new"`, so the format
        BUG-408 centralised still had a second home in production.
  - [x] `human-verification-checklists.md` rewritten: Scenario 2 described the withdrawn nullable
        metric and a `PATH` search this design forbids, Scenario 4.3 named retired apt binaries, and
        all four sign-off rows were blank. Five scenarios now, all signed with evidence.
  - [x] MAINT-33 docs corrected for BUG-407 (`fetch-corpus.sh` → `.py`, `corpus.tsv` → `.json`).
- **Verification**: full suite **2373 / 0** (1 skipped); corpus ratchet green on all four members;
  gate proved to fail via the BUG-392 reintroduction; ktlint clean; `lint_docs` + `lint_planning`
  0 errors.

## Definition of Done

- MAINT-35-00…-05 and -07 implemented (-06 and -03a are `Should`).
- A **fresh builder** runs `fetch-luac.py` + the corpus gate green with **no system `luac`
  installed** — the check that MAINT-35-00 actually landed and that `PATH` is never consulted.
  ☑ 2026-08-05.
- The gate is shown to **fail**, not merely to pass: BUG-392 reintroduced takes luarocks'
  `oracleDisagreements` 0 → 1 and names `falseReject:src/luarocks/cmd.lua`. ☑ 2026-08-05.
- The ratchet gates `oracleDisagreements`, `lexerRoundTripFailures` and `crashes`.
- **No disagreement recorded into a baseline without a filed bug ID.**
- Full suite green and corpus ratchet green, run separately; ktlint and doc linters clean.
