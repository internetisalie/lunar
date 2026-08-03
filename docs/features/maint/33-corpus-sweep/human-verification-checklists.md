---
id: "MAINT-33-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "MAINT-33"
folders:
  - "[[features/maint/33-corpus-sweep/requirements|requirements]]"
---

# Verification Checklists: MAINT-33 — Corpus Sweep

This feature ships no UI, so these are operator checks rather than IDE scenarios: they confirm the
gate behaves correctly for a human running it, and that it is genuinely inert for everyone else.

> **Run 2026-08-03 — 12 Pass, 0 Fail** (initially 11/1; see 4.1). Evidence is recorded inline per
> scenario rather than asserted: counts come from `build/test-results/test/*.xml` on the builder,
> never from a `BUILD SUCCESSFUL` banner (Risk 1.3).
>
> - **2.3 and 2.4 were verified via their automated equivalents**, not the operator steps. Both are
>   `BaselineRatchetTest` cases asserting the same messages against a synthetic `repoRoot`; the
>   scenarios are marked accordingly rather than claimed as hand-run.
> - **3.2 was verified by inspection** of `build-plugin.yml`, not by pushing — nothing is pushed yet.
> - **4.1 failed on the first run and the code was fixed, not the expectation.** The inventory
>   reported `ballast.unclaimed.rockspec=54` where this checklist specified `claimed=53`. Root cause:
>   `spec/folder/rockspec` is extensionless, so `groupKey` filed it under the same key as the 53 real
>   `*.rockspec`, and the all-or-nothing `claimed` flag let one file hide all 53. `BallastGroup` now
>   carries per-member counts. The checklist's `53` was right; the implementation was wrong.

## 1. Provisioning

### Scenario 1.1: Clean fetch
- **Setup**: `rm -rf test/corpus`.
- **Steps**:
  1. Run `tooling/corpus/fetch-corpus.sh`.
  2. `ls -a test/corpus/luacheck` and `cat test/corpus/luacheck/.corpus-sha`.
  3. `du -sh test/corpus`.
- **Expected**: every manifest project present; no `.git` in any checkout; each `.corpus-sha`
  equals its manifest `commit`; total size within the rsync budget.
- **Result**: ☑ **Pass** (2026-08-03, operator-run) — `rm -rf test/corpus` then `fetch-corpus.sh`: 3 projects present, `find -name .git` → 0, every `.corpus-sha` equals its manifest `commit`, `du -sh` = 12 MB against the ~50 MB budget.

### Scenario 1.2: Warm fetch is a no-op
- **Setup**: corpus already fetched.
- **Steps**:
  1. Re-run `tooling/corpus/fetch-corpus.sh`.
- **Expected**: each project logged "already at <sha> — skipping"; no network activity; exit 0;
  no file modified (`ls -la` timestamps unchanged).
- **Result**: ☑ **Pass** (2026-08-03) — re-run emitted 3 `skipping` lines; file count 1174 before and after, byte-identical tree.

### Scenario 1.3: Pruning
- **Steps**:
  1. `ls test/corpus/luarocks`.
- **Expected**: `win32/` absent; `src/`, `spec/`, `docs/`, `*.rockspec`, `config.ld` all present —
  binaries pruned, ballast retained.
- **Result**: ☑ **Pass** (2026-08-03) — `luarocks/win32` and `zerobrane/bin` absent; `luarocks/src`, `luarocks/spec`, `zerobrane/src`, `zerobrane/api` present; `find -type l` → 0 (the rsync landmine guard holds).

## 2. The Gate

### Scenario 2.1: Gating run against committed baselines
- **Setup**: corpus fetched, baselines committed, builder VM running.
- **Steps**:
  1. `tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"`.
  2. Read `build/test-results/test/*.xml` on the builder for the actual test count.
- **Expected**: `BUILD SUCCESSFUL`, **and** the XML reports the expected number of tests with 0
  failures. A green build with 0 tests is a FAIL for this scenario (Risk 1.3).
- **Result**: ☑ **Pass** (2026-08-03) — counts read from `build/test-results/test/*.xml` on the builder, not from the banner: 3 corpus tests, 0 failures, all three sweeps reporting files/parseErrors/requires. Not a zero-test green (Risk 1.3).

### Scenario 2.2: The ratchet can actually fail
- **Setup**: as above.
- **Steps**:
  1. Hand-edit a committed baseline to lower a gated metric by one (e.g. `parseErrors=2`).
  2. Re-run the gating command.
  3. Restore the baseline.
- **Expected**: the run FAILS, naming the metric with `baseline 2 → observed 3`. If it passes, the
  gate is inert and nothing it reports can be trusted.
- **Result**: ☑ **Pass** (2026-08-03, operator-run) — luacheck baseline hand-edited to `parseErrors=2`; the gating run went **BUILD FAILED**, 1 of 4 tests failing with exactly `parseErrors: baseline 2 → observed 3`. Baseline restored and re-verified clean. The gate is not inert.

### Scenario 2.3: Stale corpus is refused
- **Steps**:
  1. `echo deadbeef > test/corpus/luacheck/.corpus-sha`.
  2. Run the gating command.
  3. Re-run `fetch-corpus.sh` to restore.
- **Expected**: fails with the wrong-commit message naming `tooling/corpus/fetch-corpus.sh`; no
  metrics are compared.
- **Result**: ☑ **Pass — via the automated equivalent.** `BaselineRatchetTest.divergentBaselineCommitFailsWithReRecordInstruction` (TC 8) covers this against a synthetic repoRoot and passes. The operator form (editing `.corpus-sha` on the real corpus) was **not** run separately; the assertion is identical.

### Scenario 2.4: Missing corpus is refused
- **Steps**:
  1. `mv test/corpus/luacheck /tmp/` and run the gating command; restore afterwards.
- **Expected**: fails naming the fetch script. It must **not** skip, pass vacuously, or record a
  new baseline.
- **Result**: ☑ **Pass — via the automated equivalent.** `BaselineRatchetTest.absentCorpusFailsWithFetchInstruction` (TC 9) asserts the failure names `tooling/corpus/fetch-corpus.sh`, and passes. The operator form (moving the checkout aside) was **not** run separately.

## 3. Inertness for Everyone Else

### Scenario 3.1: The routine suite is unchanged
- **Steps**:
  1. Run the full unit suite on the builder with no corpus properties.
  2. Compare the test count and duration to the previous run on `main`.
- **Expected**: green; **no** `*Corpus*` test in the results; count differs only by tests this
  feature intentionally added to the routine suite (the Phase 2 `BaselineRatchetTest`).
- **Result**: ☑ **Pass** (2026-08-03) — full suite with no corpus properties: 348 suites, 2267 tests, 0 failures, 0 errors, nothing stale. No `*Corpus*` test present; the added routine-suite tests are the intended `BaselineRatchetTest` ones.

### Scenario 3.2: CI is unaffected
- **Steps**:
  1. Push the branch and open the Gitea Actions run for `build-plugin.yml`.
- **Expected**: the `build` job passes; no corpus test appears in the uploaded test report; no
  attempt to read `test/corpus` (which does not exist in a CI checkout).
- **Result**: ☑ **Pass** (2026-08-03, by inspection) — `.gitea/workflows/build-plugin.yml:128` runs `./gradlew test … -PexcludeExternalFixtureTests` with no `-PwithCorpus`, and `grep -rn corpus .gitea/workflows/` returns 0 matches, so CI cannot reach `test/corpus`. **Check `.gitea/workflows/`, not `.github/workflows/`** — both exist and Gitea prefers `.gitea/`, so the `.github/` copy is inert. This result was first recorded against the `.github/` file by mistake; re-verified against the executing one, same conclusion. Its other two Gradle invocations (`buildPlugin` at :123, `koverXmlReport -x test -x integrationTest` at :138) also pass no corpus flag.

### Scenario 3.3: Recording round-trip
- **Steps**:
  1. Run with `-PwithCorpus -PrecordCorpusBaseline`.
  2. Copy the echoed baseline text from the console into `src/test/resources/corpus/`.
  3. Re-run the gating command with no record flag.
- **Expected**: the recorded content is visible in the console (not only written on the builder),
  and the subsequent gating run passes against it unchanged.
- **Result**: ☑ **Pass** (2026-08-03) — recorded with `-PrecordCorpusBaseline`; the baseline text was echoed to the console *and* written on the builder, pulled back with rsync, and the subsequent no-flag gating run passed against it unchanged.

## 4. Findings Review

### Scenario 4.1: The ballast inventory is actionable
- **Setup**: Phase 4 complete.
- **Steps**:
  1. Read the `ballast.unclaimed.*` lines in each baseline.
- **Expected**: for **luarocks** — `ballast.unclaimed.tl=117` and `ballast.unclaimed.ld=1`
  (`config.ld`), with `tlconfig.lua` under `ballast.claimed.lua=1` — ballast because it is outside
  the roots, but claimed because `plugin.xml:99` registers the `lua` extension. Counts are
  whole-checkout: 107 of the 117 `.tl` sit inside `src/` and are ballast because they are not
  `.lua`. For **luacheck** — `ballast.claimed.lua=3` (its `bin/`, `build/bin/` and `scripts/`
  entry points; the `lua` group is per-project, 1 for luarocks and 3 here),
  `ballast.unclaimed..luacov=1`, and `ballast.claimed.rockspec=53`
  plus `ballast.claimed..luacheckrc` / `ballast.claimed..busted`. Groups on DR-05's ignore list
  (`md`, `yml`, … — unclaimed only because the fixture lacks the bundled plugins) are excluded from
  interpretation. Each remaining unclaimed group has either a filed feature or a written rationale
  for ignoring it.
- **Result**: ☑ **Pass** (2026-08-03, after fixing the ballast rule) — luarocks `ballast.unclaimed.tl=117`, `ballast.unclaimed.ld=1` (`config.ld`), `ballast.claimed.lua=1` (`tlconfig.lua`), `ballast.claimed.rockspec=31`; luacheck `ballast.claimed.lua=3`, `ballast.unclaimed..luacov=1`, `ballast.claimed..luacheckrc`/`ballast.claimed..busted`, and **`ballast.claimed.rockspec=53`** as specified. This scenario originally FAILED with `ballast.unclaimed.rockspec=54`: `spec/folder/rockspec` is extensionless, so `groupKey` filed it under the same key as the 53 real `*.rockspec`, and the all-or-nothing `claimed` flag let that one file hide all 53. `BallastGroup` now carries per-member counts, so the group reports `claimed=53` **and** `unclaimed=1`. The expected `53` in this checklist was correct all along.

### Scenario 4.2: Non-zero floors are triaged, not accepted
- **Steps**:
  1. For each gated metric with a non-zero baseline, confirm a `BUG-*` report exists or a note
     records why the floor is correct.
- **Expected**: luacheck's `parseErrors=3` is documented as intentional corpus samples;
  luarocks' `parseErrors=1` (`src/luarocks/cmd.lua`) and `unresolvedRequires=12` each have a
  filed report or an explicit triage note (Risk 1.4).
- **Result**: ☑ **Pass** (2026-08-03) — every non-zero gated floor is triaged. luacheck `parseErrors=3` is documented as its deliberately-malformed `spec/samples/`; luarocks `parseErrors` is now **0** (BUG-392 fixed this session); `unresolvedRequires` 12/3/19 are recorded as genuinely external modules (ZeroBrane's 19 are `wx`/`mobdebug`/`socket`/`lfs`/`copas`/`bit`, resolvable only by TARGET-08). The large `LuaUndeclaredVariable` floors are attributed: 937/954 luarocks and 570/615 luacheck are busted globals.
