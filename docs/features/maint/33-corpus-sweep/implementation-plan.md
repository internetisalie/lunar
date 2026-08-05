---
id: "MAINT-33-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-33"
folders:
  - "[[features/maint/33-corpus-sweep/requirements|requirements]]"
---

# MAINT-33: Implementation Plan

Phase 1 exists as a working prototype (built and run on the builder, 2 tests / 0 failures / 63.5 s,
baselines recorded). It is listed here as a phase because it still needs the full-suite gate and
the review that the prototype skipped — not because it is unwritten.

> **All six phases shipped (2026-08-03); the feature is `done`.** The task checkboxes below were
> never ticked as work proceeded and are deliberately left as authored rather than back-filled —
> ticking forty boxes retrospectively would assert a per-item verification that did not happen.
> What *was* verified, with evidence, is recorded in
> [human-verification-checklists.md](human-verification-checklists.md) (run 2026-08-03: 11 pass,
> 1 fail) and in the DR table in [risks-and-gaps.md](risks-and-gaps.md).
>
> Two Phase 5 tasks were **consciously dropped, not completed**: pinning KOReader and adding
> `testKoreaderCorpus`. KOReader was admitted, swept once and reverted on sweep time — see the
> KOReader entry in risks-and-gaps. TC 14 (KOReader sweep) is therefore not covered.

## Phases

### Phase 1: Provisioning + Defect Sweep + Ratchet [Must]
- **Goal**: luacheck and luarocks are swept and gated on the builder; `./gradlew test` is unchanged.
- **Tasks**:
  - [x] Create `tooling/corpus/corpus.tsv` (migrated to `corpus.json` by BUG-407) pinning luacheck `cc089e3f` and luarocks `990ec6ca` — realizes design §4.1
  - [x] Create `tooling/corpus/fetch-corpus.sh` implementing the §3.5 idempotency algorithm — realizes design §2.5
  - [x] Create `net.internetisalie.lunar.corpus.CorpusManifest` (+ `CorpusEntry`) — realizes design §2.1
  - [x] Create `net.internetisalie.lunar.corpus.CorpusSweep` implementing the §3.1 tally — realizes design §2.2
  - [x] Create `net.internetisalie.lunar.corpus.CorpusMetrics` / `CorpusBaseline` implementing §3.2 and the §4.2 format — realizes design §2.3
  - [x] Implement the §3.2 step-1 identity checks (`commit`, `files`, **`requires`**) in `LuaCorpusSweepTest.assertRatchet` — the `requires` check was missing from the prototype, which would have turned the BUG-389 fix into a misleading `unresolvedRequires` regression instead of a re-record prompt
  - [x] Create `net.internetisalie.lunar.corpus.LuaCorpusSweepTest` with the luacheck and luarocks tests — realizes design §2.4
  - [x] Add the `withCorpus` / `recordCorpusBaseline` filter block to `build.gradle.kts` — realizes design §7
  - [x] Record and commit `src/test/resources/corpus/{luacheck,luarocks}.baseline`
  - [ ] **Gate**: run the FULL unit suite on the builder without `-PwithCorpus` and confirm it is green and unchanged in count
  - [ ] **Gate**: re-run `--tests *Corpus* -PwithCorpus` (no record flag) and confirm the ratchet passes against the committed baselines
  - [ ] Run `ktlintFormat ktlintCheck` over the new sources
- **Exit criteria**: TC 1–5, TC 10, TC 11 pass; full suite green both with and without the flag. (TC 9 belongs to Phase 2 — no Phase 1 task exercises it.)

### Phase 2: Ratchet Behaviour Tests [Must]
- **Goal**: the gate itself is proven to fail when it should — a ratchet that cannot fail is worthless.
- **Tasks**:
  - [ ] Add `net.internetisalie.lunar.corpus.BaselineRatchetTest` covering `render`/`parse` round-trip and `compare` — realizes design §3.2, §4.2. **Named without "Corpus" deliberately**: these assertions need no fixture and no fetched corpus, so they must run in the routine suite. `excludeTestsMatching("*Corpus*")` is case-sensitive and will not match the lowercase `…lunar.corpus.` package segment.
  - [ ] Change `CorpusManifest.entry` to distinguish "no entry" from "duplicate entry" — realizes design §4.1 failure handling. The shipped `CorpusManifest.kt:31-33` uses a bare `singleOrNull { } ?: error("No corpus entry named …")`, which reports a duplicate as absent.
  - [ ] Add `malformedManifestRowThrows` and `duplicateManifestNameThrows` — design §2.6
  - [ ] **Deferred — the formats do not exist yet.** In Phase 2 `renderParseRoundTrip` covers only the five scalars + `parseErrorFile`. The `inspection.*`/`unattributed` half moves to Phase 3; the `ballast.*` half **and** `ballastKeyInverseParse` (incl. the `.luacov` doubled-dot key) move to Phase 4. Both receiving phases carry their own checkbox.
  - [ ] Test: gated metric increase ⇒ non-empty `regressions` (TC 6)
  - [ ] Test: gated metric decrease ⇒ non-empty `improvements`, empty `regressions` (TC 7)
  - [ ] Test: baseline with a missing scalar key ⇒ throws, not silently passes
  - [ ] Extract `assertCorpusFetched` and `assertRatchet` from `LuaCorpusSweepTest`'s private members (`:47`, `:69`) into `internal object CorpusGuards` — realizes design §2.4a. Both are fixture-free, so this is a visibility change; add the `org.junit.Assert` imports and update the `[LuaCorpusSweepTest.assertRatchet]` KDoc link at `CorpusMetrics.kt:61`.
  - [ ] Add TC 9 (`absentCorpusFailsWithFetchInstruction`) and TC 8 (`divergentBaselineCommitFailsWithReRecordInstruction`) to `BaselineRatchetTest` — realizes design §2.6's arrange table. Both use a **synthetic `repoRoot`** / temp baseline file, never the real corpus, which is why they belong in the routine-suite class rather than a `*Corpus*`-named one.
- **Exit criteria**: TC 6, 7, 8, 9 all covered in `BaselineRatchetTest`, which runs in the **routine** suite (no `-PwithCorpus`). Each assertion verified to fail when inverted.

### Phase 3: Inspection-Hit Metric [Should]
- **Goal**: the corpus reports the false-positive floor per inspection.
- **Precondition**: DR-01 and DR-02 resolved.
- **Tasks**:
  - [ ] Add the `luaLevel` manifest column and `CorpusEntry.luaLevel`; apply it via `LuaProjectSettings` before sweeping — realizes design §3.3, §4.1 (TC 15)
  - [ ] Implement `CorpusSweep.inspectionHits` using `HighlightInfo.getInspectionToolId()` and the severity-filtered `doHighlighting` overload — realizes design §3.3
  - [ ] Extend `CorpusMetrics` with `inspectionHits` and the `inspection.*` baseline keys, incl. the `unattributed` reserved key — realizes design §2.3, §4.2
  - [ ] Enable all **ten** §3.3 tool instances (the single table — not "the eight") in `LuaCorpusSweepTest.setUp`
  - [ ] Extend `CorpusBaseline.compare` to gate every `inspection.*` key — realizes design §3.2 step 2
  - [x] ~~Widen `fetch-corpus.sh:45`'s `while IFS=$'\t' read` to take a sixth `luaLevel` variable~~ — **superseded by BUG-407 (2026-08-05)**. This task correctly described the defect (`prune` absorbing the level, and the spurious `rm -rf "$dest/LUA51"`) and was never done; it also under-diagnosed it, since widening the `read` would not have stopped IFS-whitespace collapsing. Fixed instead by dropping positional TSV: `corpus.json` + `fetch-corpus.py`, library-parsed on both sides
  - [ ] Extend `BaselineRatchetTest.renderParseRoundTrip` to cover the `inspection.*` and `unattributed` keys (deferred from Phase 2)
  - [ ] Re-record both baselines
- **Exit criteria**: TC 12 and TC 15 pass; sweep runtime recorded and within the §NFR budget.

### Phase 4: Ballast Inventory [Should]
- **Goal**: each corpus addition automatically surfaces integration candidates.
- **Precondition**: DR-05 resolved (otherwise the inventory is recorded but uninterpretable).
- **Tasks**:
  - [ ] Change `CorpusSweep.run` to the 3-arg form taking `checkoutDir`, and add `CorpusManifest.checkoutDir` — realizes design §2.1, §2.2
  - [ ] Implement `CorpusSweep.ballast` — the §3.4 complement rule (a file is ballast unless it is `.lua` **and** under a declared root), not a bare location test and not a bare extension test — realizes design §2.3, §3.4
  - [ ] Extend the baseline format with `ballast.<claimed|unclaimed>.<key>` **and its positional inverse parse** — realizes design §4.2
  - [ ] Confirm the inventory is reported but **not** gated (§3.2 rules)
  - [ ] Apply DR-05's ignore list when reporting integration candidates
  - [ ] Add `BaselineRatchetTest.ballastKeyInverseParse` and extend `renderParseRoundTrip` to the `ballast.*` keys incl. the doubled-dot `.luacov` case (both deferred from Phase 2) — design §2.6, §4.2
  - [ ] Re-record both baselines
- **Exit criteria**, per project — the `lua` group differs between them, so do not merge these:
  - **luarocks** (TC 13): `ballast.unclaimed.tl=117`, `ballast.unclaimed.ld=1`,
    `ballast.claimed.lua=1` (`tlconfig.lua`).
  - **luacheck** (TC 13b): `ballast.claimed.rockspec=53`, `ballast.unclaimed..luacov=1`,
    `ballast.claimed..luacheckrc`, `ballast.claimed..busted`, `ballast.claimed.lua=3`
    (`bin/`, `build/bin/`, `scripts/`).
  - The `tl` and `rockspec` figures are whole-checkout counts — if the implementation yields 10 and
    48 it has applied a location-only rule and is wrong.

### Phase 5: Corpus Expansion [Should]
- **Goal**: four projects, covering LuaJIT/FFI and global-heavy 5.1 application shapes.
- **Tasks**:
  - [ ] Pin KOReader to a release tag; fetch **without submodules**; prune binary/thirdparty trees — realizes design §4.1
  - [ ] Pin ZeroBrane Studio to a release tag; declare its `src`/`packages` roots
  - [ ] Add `testKoreaderCorpus` and `testZerobraneCorpus` — realizes design §2.4
  - [ ] Record both baselines; confirm the corpus stays inside the rsync budget (Risk 1.2)
- **Exit criteria**: TC 14 passes; all four sweeps green; `du -sh test/corpus` under the budget.

### Phase 6: Documentation & Timing [Could]
- **Tasks**:
  - [ ] Print per-project wall-clock as an advisory, ungated line — realizes MAINT-33-09
  - [ ] Finalise `tooling/corpus/README.md` (usage, adding a project, ballast table)
  - [ ] Add MAINT-33 to `docs/roadmap.md` Wave 12
  - [ ] `CHANGELOG.md` — internal tooling entry only if judged user-facing
- **Exit criteria**: TC 16 passes; `python3 scripts/lint_docs.py docs` and `lint_planning.py` clean.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-33-01 Pinned Provisioning | M | Phase 1 |
| MAINT-33-02 Parse-Error Metric | M | Phase 1 |
| MAINT-33-03 Require-Resolution Metric | M | Phase 1 |
| MAINT-33-04 Ratchet Gate | M | Phase 1 (behaviour), Phase 2 (proof) |
| MAINT-33-05 Opt-In Execution | M | Phase 1 |
| MAINT-33-06 Inspection-Hit Metric | S | Phase 3 |
| MAINT-33-07 Ballast Inventory | S | Phase 4 |
| MAINT-33-08 Corpus Expansion | S | Phase 5 |
| MAINT-33-09 Index Timing | C | Phase 6 |

## Verification Tasks

- [ ] Full unit suite on the builder, no corpus flags — covers TC 10 (must be green *and* unchanged in test count)
- [ ] `--tests *Corpus* -PwithCorpus` gating run — covers TC 4, 5
- [ ] `--tests *Corpus* -PwithCorpus -PrecordCorpusBaseline` — covers TC 11
- [ ] Fetch-script runs from clean and from warm — covers TC 1, 2, 3
- [ ] Ratchet unit tests — covers TC 6, 7, 8, 9
- [ ] Inspection metric regression test — covers TC 12; language-level pinning — covers TC 15
- [ ] Ballast inventory assertions — cover TC 13, TC 13b
- [ ] KOReader sweep — covers TC 14
- [ ] Advisory timing output — covers TC 16
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md)

## Task Summary

| Phase | Priority | Status |
|-------|----------|--------|
| 1 — Provisioning + sweep + ratchet | Must | in_progress (prototyped; gates outstanding) |
| 2 — Ratchet behaviour tests | Must | todo |
| 3 — Inspection-hit metric | Should | todo |
| 4 — Ballast inventory | Should | todo |
| 5 — Corpus expansion | Should | todo |
| 6 — Documentation & timing | Could | todo |
