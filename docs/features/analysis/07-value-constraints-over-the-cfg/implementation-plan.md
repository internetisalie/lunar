---
id: "ANALYSIS-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ANALYSIS-07"
folders:
  - "[[features/analysis/07-value-constraints-over-the-cfg/requirements|requirements]]"
---

# ANALYSIS-07: Implementation Plan

> **Hard gate before Phase 1: [[ANALYSIS-07-DESIGN|design.md]] §3.7 must have fired, and §5 must
> exist.** Phase 1 is deliberately unwritten below — it is *"implement whichever of A / B / C the
> spike selected"*, and writing tasks for it now would mean choosing a direction from a reading. The
> Phase-1 stub states what replanning owes instead, in the manner of [[COMP-09]]'s
> `ABORT_REPLAN` record: a task list that cannot be executed is worse than an honest gate.
>
> **Phase 0 is fully executable from the design as written.**

## Phases

### Phase 0: The de-risking spike [Must]

- **Goal**: produce the numbers that let design §3.7 select exactly one of D0–D4, and write the
  selection into design §4.9 and §5. **No production change SURVIVES this phase** — T0.9 deliberately
  patches one production file (`LuaTypeGraph.kt:352-353`) and T0.10 reverts it; `plugin.xml` and
  every other `src/main/` file are untouched throughout (design §1.3, §7.1). The invariant is
  enforced by T0.10 plus the `temporary-edits` skill and checked by exit criterion 4, **not** by
  never editing. `DX` is design §3.7's probe-invalid gate and is **not** a selectable outcome — if it
  fires, the probe is fixed and re-run, and this phase does not exit.
- **Preconditions**:
  - Corpus fetched: `tooling/corpus/fetch-corpus.py`. `CorpusGuards.assertCorpusFetched`
    (`CorpusGuards.kt:17-31`) refuses to measure an absent or drifted checkout, so this is verified
    rather than assumed.
  - Working tree clean at `99b45f92` or later, with the committed baselines intact — DR-12's
    before-list is only comparable against the tree the baselines were recorded on.
  - `temporary-edits` skill loaded **before** the first probe edit, not after.

- **Tasks** (each names the file it creates and the design section it realizes):

  - [ ] **T0.0** Create `src/test/kotlin/net/internetisalie/lunar/analysis/AnalysisSevenFixtureBase.kt`
        — design **§2.0.1**, verbatim: the abstract, `@Test`-free base carrying `getTestDataPath`,
        the `VfsRootAccess` grant, `corpusFile(corpusName, root, relativePath)` and the private
        `applyModuleRoot`. **T0.2, T0.3 and T0.5 do not compile without it**, because those three
        read pinned corpus files and §7.3 places them outside the `*Corpus*` filter, so they carry
        their own fetch check, root grant, module root and language-level pin (§2.0). Not a harness
        — six files, five harnesses.
  - [ ] **T0.1** Create `…/AnalysisSevenReachingDefsSpikeTest.kt` — realizes design §2.1,
        implementing §3.1 (`reachingWrites`), §3.2 (`matchesByName`, `matchesByBinding`,
        `declarationOf`) and §3.3 (`ownersFor`). Eight fixtures exactly as tabulated in §2.1; output
        format §4.1, whose `unresolved = <targetPairs>/<allPairs>` field must show
        **`targetPairs = 0` on all eight** or design §3.7's `DX` gate fires (§3.2 step 2.3).
        Extends `BasePlatformTestCase` directly — it reads no corpus. Feeds DR-01, DR-03, DR-04,
        DR-05.
  - [ ] **T0.2** Create `…/AnalysisSevenCoverageSpikeTest.kt` — realizes design §2.2 + §2.2.1
        (`graphOf`) + §3.4. **Extends `AnalysisSevenFixtureBase`** (T0.0); obtains its two files
        with `corpusFile("penlight", "lua", "pl/stringx.lua")` then
        `corpusFile("luarocks", "src", "luarocks/fs/lua.lua")`, **not interleaved** (§2.0.4), and
        asserts §2.0.3's size-identity (26 156 / 40 093 chars) and non-vacuity guards first. Output
        format §4.2. Feeds DR-06.
  - [ ] **T0.3** Create `…/AnalysisSevenJoinSpikeTest.kt` — realizes design §2.3 + §3.5, including
        `lcsLength` exactly as specified in §3.5 step 4. **Extends `AnalysisSevenFixtureBase`**;
        same two files, same order, same two guards as T0.2. Output formats §4.3 and §4.4. Feeds
        DR-07, DR-09.
  - [ ] **T0.4** Create `…/AnalysisSevenCostSpikeTest.kt` — realizes design §2.4, medians of five
        cold samples. Extends `BasePlatformTestCase` directly. Output format §4.5. Feeds DR-08,
        DR-13, whose `worstOwnerBuild=` threshold is exactly `> 50 ms`.
  - [ ] **T0.5** Create `…/AnalysisSevenDescopeSpikeTest.kt` — realizes design §2.5. **Extends
        `AnalysisSevenFixtureBase`**: its DR-11 half calls `corpusFile("penlight", "lua",
        "pl/config.lua")` and `corpusFile("penlight", "lua", "pl/stringx.lua")`; its DR-10 half uses
        `configureByText` and touches no corpus. The two anchors are `config.lua:131`
        (`local function check_cnfg (var,def)`) and `stringx.lua:231`
        (`local function _find_all(s,sub,first,last,allow_overlap)`) — **declaration lines**;
        `local val = cnfg[var]` is `config.lua:132` and is not the anchor. Output formats §4.6 and
        §4.7. Feeds DR-10, DR-11.
  - [ ] **T0.6** Run T0.0–T0.5:
        `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*AnalysisSeven*'"`.
        **`-PwithCorpus` is deliberately absent and the corpus must still be fetched.** That flag
        controls only `build.gradle.kts:272-283`'s exclusion filter; no `AnalysisSeven*` name
        contains `Corpus`, so none is excluded either way (design §7.3) — but T0.2/T0.3/T0.5 read
        pinned corpus files and their `CorpusGuards.assertCorpusFetched` call
        (`CorpusGuards.kt:17-31`) fails loudly on an absent or drifted checkout. That is the
        precondition above, enforced rather than trusted.
        **Paste every §4.1–§4.7 block verbatim into design.md** — an outcome summarised in prose is
        not the evidence, it is a claim about the evidence.
  - [ ] **T0.7** Snapshot `src/test/kotlin/net/internetisalie/lunar/corpus/CorpusSweep.kt` with
        `temporary-edits`, then apply the §3.6 per-site dump patch to `accumulateHits`
        (`CorpusSweep.kt:263-287`).
  - [ ] **T0.8** **DR-12, the before-list.** Run
        `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus"` and
        capture the `[a07:site]` lines to `docs/features/analysis/07-value-constraints-over-the-cfg/`
        as a fenced block in `risks-and-gaps.md`'s DR-12 row (16 sites expected: 11 type + 5
        unreachable). In the same session run
        `… run "test --rerun --tests '*LuaInspectionParityTest*'"` **by name** and record
        `filesAtExactParity`.
  - [ ] **T0.9** **DR-02.** Add a throwaway `unknownProvenance` computed by §3.1 at
        `LuaTypeGraph.kt:352-353`. **This is a production file** — snapshot it with `temporary-edits`
        *before* the edit, exactly as T0.7 did for `CorpusSweep.kt`; T0.10 reverts both. Re-run
        T0.8's corpus command and `diff -u` the two `[a07:site]` dumps. Record **both** directions as
        the named inputs design §4.8 defines: **`RESTORED`** (the `+` lines) and **`NEW_SUPPRESSED`**
        (the `-` lines), each restricted to `LuaTypeAssignability` and `LuaReturnTypeMismatch`.

        **Expect this corpus run to go RED, and do not treat the red as a broken probe.** Restored
        sites *raise* `inspection.LuaTypeAssignability`, and more hits is a **regression**
        (`CorpusMetrics.kt:283-284`, `Triple(key, baseline, observed)` — `it.third > it.second` is
        the regression filter), so `CorpusGuards.assertRatchet` (`CorpusGuards.kt:52-55`) fails
        whenever `RESTORED > 0`. Nothing is lost: the `[a07:site]` lines are printed from inside
        `CorpusSweep.accumulateHits` during `CorpusSweep.run` (`LuaCorpusSweepTest.kt:93`), which
        completes **before** `assertRatchet` (`:101`), and each corpus is its own `@Test` member. The
        dump, not the verdict, is the output of this task. **Do not** re-record a baseline here —
        every edit is reverted in T0.10 and the tree must end byte-identical.

        Conversely, a **green** T0.9 is not a clean bill of health: falling counts print `IMPROVED`
        and assert nothing, which is exactly the `NEW_SUPPRESSED > 0` shape. Read the diff.
        **`NEW_SUPPRESSED > 0` fires design §3.7's `DX`**: quote the offending lines verbatim, fix
        the probe, re-run this task, and do **not** proceed to T0.11 — `COVERAGE`/`JOIN`/`LCS` from
        T0.2/T0.3 carry forward and are not re-measured.
  - [ ] **T0.10** **Revert everything.** Restore `CorpusSweep.kt` and `LuaTypeGraph.kt` via
        `temporary-edits`; delete T0.0–T0.5 (**six files**: the five harnesses and
        `AnalysisSevenFixtureBase.kt`). **Never** `git checkout` / `git restore` / `git stash` —
        they discard every uncommitted change under the path, not only the probe's. Verify with
        `git status --short` empty and `git diff` empty.
  - [ ] **T0.11** Apply design §3.7's ordered rules to the pasted output, **`DX` first** — it may
        not run while `NEW_SUPPRESSED > 0` or `UNRESOLVED_TARGET > 0`. Record the fired rule, the
        quoted numbers, and the §4.9 TYPE-08 verdict block in design.md. Append the same two lines to
        `docs/features/type/08-flow-sensitive/design.md` §9 (requirements TC-1b).
  - [ ] **T0.12** Act on the branch:
        - **D0** → set `ANALYSIS-07-02` to `cancelled` in requirements.md with the numbers quoted;
          file a BUG report + a `docs/roadmap.md` row for the residual finding; **do not** leave the
          requirement at `Not Implemented`.
        - **D1/D2/D3/D4** → write design §5 (and §6 if DR-10/DR-11 kept `-03`/`-04`), then set
          `ANALYSIS-07` front-matter to `planned` and replan Phase 1 below. §5 must contain
          everything design §5's "when written it must contain" paragraph lists, or the feature stays
          `todo`.
        - Either way → update `docs/roadmap.md`'s ANALYSIS-07 row and close DR-10/DR-11's requirements
          (`cancelled` + refiled, or kept with the dump quoted).

- **Exit criteria**:
  1. Design §4.1–§4.7 contain pasted probe output, not summaries.
  2. Design §4.9 states the TYPE-08 §9 verdict with numbers (requirements TC-1b), and TYPE-08's
     design carries the cross-reference.
  3. Exactly one of D0–D4 is recorded in **design §4.9** with the numbers that selected it
     (requirements TC-1a), and **design §3.7's `DX` gate is not firing** — `NEW_SUPPRESSED == 0` and
     `UNRESOLVED_TARGET == 0`. DX is not an outcome this criterion may be satisfied with. (§4.3 is
     the *join-census* output format; an earlier revision of this line pointed there.)
  4. `git status --short` is empty — no probe, patch or harness survives.
  5. **A post-revert corpus re-run is clean — checked by running it, not by looking at the tree.**
     After T0.10, run `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache
     -PwithCorpus"` once more and require **both**: the four `sweepAndRatchet` members green
     (`CorpusGuards.assertRatchet`, `CorpusGuards.kt:37-56`), **and zero `[corpus] IMPROVED` lines**
     in the output. Phase 0 changes no behaviour, so an `IMPROVED` line is a leaked probe exactly as
     a regression is — and the ratchet `println`s improvements (`:49-51`) while *asserting* only on
     `comparison.regressions.isEmpty()` (`:52-55`), so the improvement half must be read by a human
     or it is not a gate at all (NFR-3).

     **This replaces a criterion that could not fail.** The previous wording asked that
     `src/test/resources/corpus/*.baseline` be byte-identical to `99b45f92`'s. Baselines are written
     only when `lunar.corpus.record == true` (`LuaCorpusSweepTest.kt:98`), which is set only by
     `-PrecordCorpusBaseline` (`build.gradle.kts:286-288`), which **T0.8 and T0.9 do not pass** — so
     the files are byte-identical whether the sweep passed, regressed, or never ran.
     `docs/engineering-contract.md:80-81` names this exact trap: *"`git status` on
     `src/test/resources/corpus/` proves nothing"*. A green-and-silent re-run **can** go red; an
     unchanged baseline file cannot.

     **Mutation-proof, one per conjunct — and the two conjuncts fail through different mechanisms**
     (design §4.8a; `CorpusMetrics.kt:283-284` with `Triple(key, baseline, observed)`, so
     `it.third > it.second` = regression = **more** hits, `it.third < it.second` = improvement =
     **fewer**):
     - *the `assertRatchet` conjunct* — leave T0.9's throwaway `unknownProvenance` in place and run
       it. `RESTORED > 0` **raises** `inspection.LuaTypeAssignability`, which is a **regression**, so
       the four members go red at `CorpusGuards.kt:52-55`. That is the same edit T0.10 exists to
       revert. **It is not an `IMPROVED` line** — an earlier revision of this paragraph said it was,
       and had the ratchet's direction backwards.
     - *the zero-`IMPROVED` conjunct* — the leak that produces an `IMPROVED` line is a probe that
       **suppresses**, i.e. the `NEW_SUPPRESSED > 0` state design §3.7's `DX` exists for. That leak
       leaves `assertRatchet` **green**, which is precisely why the improvement half has to be read
       by a human (NFR-3) and why "green" alone would not close this criterion.

### Phase 1: Reaching definitions as a service [Must] — **BLOCKED on Phase 0**

- **Goal**: `ANALYSIS-07-02`, implemented in the direction §3.7 selected.
- **Tasks**: *deliberately unwritten.* They are a function of design §5, which does not exist yet.
- **What replanning owes before this phase may be written** — the checklist a Phase-1 plan is
  measured against:

  | owed | discharged by |
  | :-- | :-- |
  | A fired §3.7 rule with quoted numbers | T0.11 |
  | Design §5: FQCN + signatures + threading for every new class | replanning |
  | Design §5: the exact `CachedValuesManager` key and dependency list for anything cached | replanning |
  | Design §5: the `(variable, use)` → `target` instruction mapping §3.1 needs | replanning |
  | Design §5: Gap 2.2's answer — fail open or fail closed when a use has no instruction | DR-05 + DR-07, then replanning |
  | Design §7.2: the `plugin.xml` delta, or an explicit "none" | replanning |
  | DR-13's verdict on whether `checkCanceled` must land first | T0.6 |

- **Exit criteria** (fixed now, so Phase 1 cannot lower its own bar): requirements TC-2a…TC-2e green
  and **mutation-proved** — TC-2a red when the query is forced to "all definitions", TC-2b red when
  forced to "nearest definition"; `LuaUnknownProvenanceTest`'s four cases unchanged;
  `LuaInspectionParityTest` at its DR-12 figure.

  **The corpus criterion is a re-record, NOT a green ratchet — a green corpus here would mean `-02`
  did nothing.** `-02`'s payoff is `RESTORED > 0`, restored sites *raise* the two gated inspection
  keys, and more hits is a **regression** (`CorpusMetrics.kt:283-284`,
  `Triple(key, baseline, observed)`; `CorpusGuards.kt:52-55` asserts on it). Design §4.8a is the
  mechanism; the criterion is its four steps, and all four must be discharged:
  1. The sweep is run and allowed to fail; every `Corpus regression:` line is captured
     (`"<key>: baseline <n> → observed <m>"`, `CorpusMetrics.kt:288-289`).
  2. Every restored `file:line` in the §4.8 dump diff is **attributed in writing**, one line of prose
     each, naming the reaching definition that became accountable. An unattributable restored site
     fails this phase — it is a defect in `-02`, not a baseline to move.
  3. **No `LuaUnreachableCode` movement in either direction** (explicit non-goal), and **no
     `IMPROVED` line at all**: `-02` may only restore, never suppress, so a falling gated count is
     the `NEW_SUPPRESSED` defect and blocks the phase.
  4. Baselines are then re-recorded deliberately —
     `… run "test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline"`
     (`build.gradle.kts:286-288` → `LuaCorpusSweepTest.kt:98-99`) — and committed **in the same
     commit** as the change, with the step-2 attribution in the message.

  An earlier revision of this line read *"corpus green with every `IMPROVED` line attributed"*, which
  is **unsatisfiable if `-02` works**: a working `-02` makes the corpus red and produces no `IMPROVED`
  line to attribute. Phase 3's `5 → 3` criterion below is the inverse case and is stated correctly;
  it is the model this one is now written against.

### Phase 2: Subgraph-scoped constraints [Should] — **GATED on DR-10, may be cancelled**

- **Goal**: `ANALYSIS-07-03`, requirements TC-3a.
- **Tasks**: unwritten. If DR-10's verdict is `NODE_REPLACED_BY_MEMBERLESS_TABLE`, **this phase does
  not exist** — the work is a one-site fix at `LuaTypesVisitor.injectNarrowedBinding`
  (`:464-478`), refiled as a bug, and the requirement is `cancelled` with the dump quoted.
- **Exit criteria**: TC-3a — `Shadow.` inside `if type(Shadow) == "table" then` offers `fromLocal`,
  measured through `myFixture.completeBasic()` and not through a direct type query. BUG-435 was found
  by a completion probe and COMP-09's Phase 2 abort was caused by validating a query directly rather
  than through the contributor; the same mistake is not available here.

### Phase 3: Call-site sensitivity [Could] — **GATED on DR-11, may be cancelled**

- **Goal**: `ANALYSIS-07-04`, requirements TC-4a.
- **Tasks**: unwritten, gated as Phase 2.
- **Exit criteria**: both BUG-428 residual sites gone from the per-site dump, penlight's
  `LuaTypeAssignability` moving 5 → 3 with each removal attributed by `file:line`.

### Phase 4: Close out `-05` and the record [Must]

- **Goal**: `ANALYSIS-07-05` proved rather than reviewed, and the paper trail closed.
- **Tasks**:
  - [ ] Add `src/test/kotlin/net/internetisalie/lunar/analysis/LuaSingleFlowAnalysisTest.kt` —
        requirements TC-5a's structural assertion (a repo-wide source scan; the precedent for
        asserting a structural property rather than reviewing for it is
        `LuaReceiverMemberIndexTest.testEveryFileTypeRegistrationIsIndexed`).
  - [ ] Update `docs/features/analysis/requirements.md`'s ANALYSIS-07 status line and the
        `docs/roadmap.md` row — **delete the roadmap row on close**, do not mark-done-and-leave.
  - [ ] `CHANGELOG.md` only if a user-visible diagnostic changed; a tier change that removes an
        ERROR the user used to see **is** user-visible.
  - [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` before any commit.
- **Exit criteria**: TC-5a green; front-matter and roadmap consistent; full suite green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ANALYSIS-07-01 | M | **Phase 0** (T0.0–T0.11) |
| ANALYSIS-07-02 | M | Phase 1 — blocked on Phase 0; may be `cancelled` by branch D0 |
| ANALYSIS-07-03 | S | Phase 2 — gated on DR-10; may be `cancelled` |
| ANALYSIS-07-04 | C | Phase 3 — gated on DR-11; may be `cancelled` |
| ANALYSIS-07-05 | M | Phase 4 (TC-5a), enforced throughout by design §3.3 and §3.7's D2 clause |
| ANALYSIS-07-NFR-1 | M | Phase 0 (DR-08 measures the budget); Phase 1 (must meet it) |
| ANALYSIS-07-NFR-2 | M | Phase 0 (DR-13); a `checkCanceled` prerequisite lands before Phase 1 if it fires |
| ANALYSIS-07-NFR-3 | M | Phase 0 (DR-12 before-list), Phase 1–3 exit criteria |
| ANALYSIS-07-NFR-4 | M | Phase 0 (T0.8 records the figure), Phase 1–3 exit criteria |

## Verification Tasks

- [ ] **TC-1a / TC-1b** — Phase 0 T0.11. Verified by inspection of design §4.9 and TYPE-08 §9.
- [ ] **TC-2a…TC-2e** — `net.internetisalie.lunar.lang.types.LuaReachingDefinitionsTest`, new,
      **alongside** `LuaUnknownProvenanceTest` (extended, not replaced). Mutation-proof both
      directions per Phase 1's exit criteria.
- [ ] **TC-3a** — through `myFixture.completeBasic()`, not a direct type query.
- [ ] **TC-4a** — per-site corpus dump, attributed by `file:line`.
- [ ] **TC-5a** — `LuaSingleFlowAnalysisTest`, Phase 4.
- [ ] **Regression floor, every phase**: `LuaControlFlowTest` (9 cases, the CFG's own suite) and
      `LuaUnreachableCodeInspection`'s 5 corpus sites unchanged — the explicit non-goal is only real
      if it is measured.
- [ ] **Full-suite gate before any commit**, never an isolated `--tests` filter: a green
      `test --tests '*Foo*'` can hide a full-suite failure. Run it and read the count.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: The de-risking spike | todo | Must |
| Phase 1: Reaching definitions as a service | todo *(blocked on Phase 0)* | Must |
| Phase 2: Subgraph-scoped constraints | todo *(gated on DR-10)* | Should |
| Phase 3: Call-site sensitivity | todo *(gated on DR-11)* | Could |
| Phase 4: Close out `-05` and the record | todo | Must |
