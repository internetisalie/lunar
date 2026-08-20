---
id: "ANALYSIS-07-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ANALYSIS-07"
folders:
  - "[[features/analysis/07-value-constraints-over-the-cfg/requirements|requirements]]"
---

# ANALYSIS-07: Risks & Gaps

> **This file is the load-bearing artifact of the plan.** `ANALYSIS-07-01` is *"decide the direction
> from a measurement"*, so the thirteen de-risking tasks below are not preliminaries to the design —
> they **are** the first phase, and [[ANALYSIS-07-DESIGN|design.md]] §3.7 is the function that turns
> their output into the design's remaining sections. Two of these DRs can also declare **their own
> measurement invalid**: DR-02 through `NEW_SUPPRESSED` and DR-04 through `UNRESOLVED_TARGET`, both
> of which fire §3.7's **`DX`** gate — probe defect, fix and re-run, no direction selected and Phase 0
> does not exit. Format follows [[COMP-09]]'s
> `risks-and-gaps.md`, which ran DR-01…DR-25 the same way.

## Premises examined

| Constraint treated as fixed | Verdict |
| :-- | :-- |
| **"TYPE-08 §9 is right — CFG integration is a much larger refactor."** | **Named as the feature's central unmeasured premise, and it is NOT inherited.** It is an estimate written in 2026 against a scope (single-pattern guards) that is not this one, with no number behind it. `ANALYSIS-07-01` exists to measure it; design §3.7's D4 is the branch where it is *confirmed*, and §4.9 requires the verdict be written back into TYPE-08 §9 either way. **Neither inheriting nor contradicting it is permitted without the numbers.** |
| **"The corpus is the gate, so it can select the direction."** | **REMOVED by a measurement already in hand.** Read off the committed baselines at `99b45f92`: BUG-441 left **11** type-inspection sites across 419 corpus files (`LuaTypeAssignability` 10, `LuaReturnTypeMismatch` 1) and `LuaUnreachableCode` has 5. There is almost nothing left to move, so the corpus is demoted to a **guard** (NFR-3) and the selector becomes design §3.1's targeted probes. A plan that proposed "measure it on the corpus" would have produced a null result and called it a decision. |
| **"`ANALYSIS-07-02` is obviously worth building."** | **NOT assumed — it is DR-01 + DR-02, and design §3.7's D0 can `cancel` it.** BUG-441's gate already works; `-02`'s case is *precision*, i.e. that a reaching-definitions answer **restores** checking BUG-441 over-suppressed. If DR-01 finds no false negative and DR-02 restores zero sites, the honest outcome is to delete the requirement, not to build it because it is written down. |
| **"BUG-435 is a CFG problem, so it belongs to `-03`."** | **NOT assumed — DR-10 can descope it entirely.** BUG-435's own report says the mechanism is a *hypothesis*: *"the narrowed variable node carries the guard's type … Anyone taking this should confirm that by reading the node the guard installs, not by assuming this paragraph."* If the dump confirms it, `-03` is a one-site fix at `LuaTypesVisitor.kt:464-478` and has nothing to do with control flow. |
| **"BUG-428's residual 2 sites are call-site sensitivity."** | **NOT assumed — DR-11.** BUG-428's re-measurement describes them as *"the same call-site-union imprecision reported against the parameter itself"* — real, at `428-…/bug-report.md:89-90`, but **line-wrapped across the two**, so a single-line `grep -F` returns `0`; verify with `tr '\n' ' ' < <file> | tr -s ' ' | grep -o "<sentence>"` (design's header note lists all three such quotations). It is anchored on the function-declaration line, which is a different anchoring path. `-04` is a `Could` and the cheapest thing that can happen to it is being deleted on evidence. |
| **"A spike may widen a production modifier if it reverts it."** | **EXAMINED and rejected, with the asymmetry stated (design §2.2.1).** The repo *has* the seam convention (`LuaTypeGraph.compatMemoSize()` is `@TestOnly internal`), so copying it would be reuse-on-precedent. It loses here for one reason: a widened modifier that survives an incomplete revert **ships silently**, whereas a deleted test file cannot. One reflective field read is used instead. |
| **"A new corpus dump class is the natural probe."** | **EXAMINED and rejected (design §2.6, §9 alt. 5).** `build.gradle.kts:272-283`'s `excludeTestsMatching("*Corpus*")` would put such a class inside the sweeps' JVM, which is measured to shift counts (+12 on luacheck's `LuaTypeAssignability`, `LuaInspectionParityTest.kt:32-36`) — the probe would move the baseline it measures. A standalone class outside the filter re-implements `LuaCorpusSweepTest.setUp`, the drift `assertAnchored` exists to catch. BUG-428's temporary instrumentation of `CorpusSweep` is reused **because it is right here**, not because it is precedent. **What this row does not license is skipping that setup for the harnesses that DO read the corpus.** Three of the five (design §2.2, §2.3, §2.5's DR-11 half) read pinned corpus files from outside the filter, so they owe all four things this row names — and design **§2.0** now specifies them in full, in one abstract base, with every value read from `CorpusManifest` and two guards (size identity, non-vacuity) so a drifted setup fails instead of reporting a number. An earlier revision of the design named the four requirements in this row and specified none of them, which left those three harnesses unexecutable. What §2.6 still refuses is a fourth full **sweep** — a class re-implementing `CorpusSweep.run`'s per-file walk, oracle and hit attribution. Reading two named files is not that. |
| **"`ControlFlowUtil.iteratePrev` is a suitable engine for reaching definitions."** | **Chosen, and it is also a risk (Risk 1.2).** Chosen because a hand-rolled walk would be a third flow traversal (`ANALYSIS-07-05`) and would drop the `ProgressManager.checkCanceled()` the utility performs per pop (`ControlFlowUtil.java:104`) — the one thing the CFG package lacks (design F7). The risk is its **shared `visited[]`**; design §3.1 step 7 states the soundness argument *and* refuses to rest on it, pinning three fixtures that check it by execution. |
| **"The CFG models every read and write, so the query is already answerable."** | **REFUTED at planning time by grep, before any spike ran.** Two structural gaps: (F4) the builder keys on `nameRef.text` with **no** scope resolution, so shadowed bindings are conflated — incidence **at least 320 declaration sites over 419 corpus files**, from the baselines'
`inspection.LuaShadowingVariable` (10 + 26 + 65 + 219 — **not** the `symbol.*` rows, which are a
top-ten sample, `CorpusSweep.kt:29`); and (F5) the file-level CFG does not descend into function bodies, so an upvalue write lives in a different graph. The filing's own wording — *"the CFG already models exactly these reads and writes … and was never asked"* — is **too strong**, and the requirements now say so. |
| **"Lua 5.5 `global` declarations are irrelevant here."** | **Chosen, and stated so it is not silent.** `LuaControlFlowBuilder` has no `global`-declaration case, and SYNTAX-09 added the keyword. It changes which names are globals, not which instructions are emitted, so it is out of scope for Phase 0; if direction A repairs binding resolution (D2), the 5.5 form is part of that increment and is named there. |

## Critical Risks

### Risk 1.1: The spike answers a question the diagnostic path never asks

- **Impact**: the whole feature is mis-sized. This is [[BUG-441]]'s recorded failure verbatim — its
  attempt 1 threaded a correct `unknownProvenance` flag through `checkTypes` and *"every emission
  printed `REPORT unknownProvenance=false`"*, because the instrumentation and the emission were on
  different paths. Three iterations were lost to it.
- **Likelihood**: **medium**, and it is the highest-consequence risk here.
- **Mitigation**: the emission path is **already pinned** and is reused rather than re-derived —
  BUG-441 attempt 2's stack probe found one identical stack for every emission
  (`checkTypes:276 -> checkTypes:333 -> checkCompatibility$default -> checkCompatibility:514 ->
  reportIncompatible`) and ruled out `addEdge:135` for this shape.
  **⚠ Those line numbers are against `c4c958ce`, not this tree, and must be re-pinned before they are
  relied on** — the same caveat design F14 carries, restated here because this row is read on its
  own and an earlier revision re-quoted `checkCompatibility:514` bare. Re-grepped at `99b45f92`:
  `checkCompatibility` is `private fun` at `LuaTypeGraph.kt:387` and spans `:387-568`, with **four**
  `reportIncompatible` call sites inside it at `:426`, `:494`, `:548` and `:560` — so `:514` is not
  any of them, and `:560` is the terminal one F14 identifies. `reportIncompatible` itself is
  declared at `:257`. **A line number quoted across two commits is exactly the class of claim this
  plan refuses elsewhere; DR-01's job is to re-pin it, not to inherit it.** Design §2.7 therefore names
  **one** call site (`LuaTypeGraph.kt:352-353`) as the thing `-02` may replace, and DR-01's dump
  includes the `diagnostics` field (§4.1) so every probe is validated against what the inspection
  actually emitted, not against what the graph contains. **A DR-01 run whose `diagnostics` field is
  empty for every fixture including the control is vacuous** — the control `P2-live-unknown` must
  show its expected suppression *and* `LuaUnknownProvenanceTest`'s control must still error.

### Risk 1.2: `ControlFlowUtil.iterate`'s shared `visited[]` silently drops a definition

- **Impact**: `-02` under-reports reaching definitions, which is the **false-ERROR** direction — the
  opposite of BUG-441's defect and strictly worse than today's over-approximation, because it makes
  the engine assert things it cannot justify (BUG-419's rule).
- **Likelihood**: **medium**. The utility marks visitation in one `boolean[]` for the whole walk
  (`ControlFlowUtil.java:99-101, 119-123`), not per path, and it was written for a different
  question.
- **Mitigation**: DR-03. Design §3.1 step 7 states why the design believes it is sound for this
  closure and then declines to rely on the argument: `P5-diamond`, `P6-loop` and `P7-goto` carry
  **asserted** expected sets, and any disagreement blocks D1 outright. If it fires, the finding is
  that direction C needs its own walker — which is a **third analysis** and therefore selects D2
  (repair the CFG) rather than "write a walker".

### Risk 1.3: The 11-site corpus cannot detect a regression this feature causes

- **Impact**: `-02` ships a defect that no gate sees. This has already happened in this area, in
  precisely the "guard built for the wrong direction" shape: COMP-09's Risk 1.1 built every gate
  against a **superset** and the failure that shipped was a **subset**, invisible because the golden
  and the corpus were all `.lua`.
- **Likelihood**: **high**, and it is structural, not bad luck: 11 sites over 419 files is not a
  detector. The `LuaUnreachableCode` surface is 5 sites.
- **Mitigation**: **the unit fixtures are the primary gate for this feature, and the corpus is
  secondary** — an inversion of the usual order, adopted because of the measured headroom.
  Requirements TC-2a…TC-2e pin four behaviours the corpus provably cannot: the killed unknown, the
  live unknown, shadowing (320 corpus sites but **zero** current diagnostics) and the closure write.
  Each must be **mutation-proved** — TC-2a red when the query is forced to "all definitions", TC-2b
  red when it is forced to "nearest definition". Plus NFR-3's **both-directions** accounting and
  NFR-4's parity run. Note which half of NFR-3 does the work for `-02`, given the direction below:
  the ratchet's **hard fail** is what fires on `-02`'s restored sites (more hits = regression), and
  the `IMPROVED` read is what catches the *opposite* leak — a `-02` that silently **suppresses**, the
  `NEW_SUPPRESSED` shape, which leaves the sweep green and would otherwise pass unseen.

### Risk 1.4: Phase 0 grows into Phase 1

- **Impact**: a spike that is "nearly a fix" gets committed, and the direction is chosen by whatever
  the spike happened to prototype rather than by §3.7. COMP-09 Phase 2 records the cost of the
  reverse error (a change implemented to plan, measured, and thrown away — `ABORT_REPLAN`).
- **Likelihood**: medium.
- **Mitigation**: **no production change SURVIVES Phase 0** (design §1.3, §7.1) and its exit
  criterion is a filled §4.1–§4.9 plus a fired §3.7 rule, not a passing behaviour. Note the exact
  form of that invariant: Phase 0 *does* patch production source — T0.9 puts a throwaway
  `unknownProvenance` at `LuaTypeGraph.kt:352-353`, which is the whole of DR-02 — alongside the
  test-source patch to `CorpusSweep.accumulateHits`. **Both** are snapshotted with the
  `temporary-edits` skill *before* the edit and restored by T0.10, and the checked end-state is
  `git status --short` **and** `git diff` empty across the whole tree, not just
  `src/test/kotlin/net/internetisalie/lunar/corpus/`. **Never restore with `git checkout` /
  `git restore` / `git stash`** — they discard every uncommitted change under the path, not only the
  probe's. An earlier revision of this row (and of design §1.3 and §7.1) said Phase 0 *"touches no
  production file"*, which T0.9 contradicts; the honest invariant is survival, and it is enforced by
  T0.10 rather than by abstention.

### Risk 1.5: A green corpus run is read as "nothing changed"

- **Impact**: a movement of tens of sites goes unattributed. **This has already happened on this
  exact code path**, on 2026-08-20: BUG-441's landing note said *"no movement to attribute"* and the
  real figure was 89 sites removed, across seven unread `IMPROVED` lines.
- **Likelihood**: **certain** if not designed against — it is the default reading.
- **Mitigation**: `CorpusGuards.assertRatchet` asserts only `comparison.regressions.isEmpty()` and
  `println`s improvements (`CorpusGuards.kt:49-55`). NFR-3 makes reading the `IMPROVED` lines a
  requirement, and DR-12's recorded pre-change site list makes attribution a `diff -u` rather than a
  memory.
- **The direction has to be cited from the comparator, not from that assert — this is the sibling
  trap and it has already caught these artifacts once.** `assertRatchet` shows *that* improvements
  are printed and regressions asserted; it never shows *which is which*. That lives in
  `CorpusBaseline.compare`: `regressions = gated.filter { it.third > it.second }`,
  `improvements = gated.filter { it.third < it.second }` (`CorpusMetrics.kt:283-284`), over
  `Triple(key, baseline, observed)` (`:259`, `:271`, `:276-280`). **More hits = regression (hard
  fail); fewer hits = improvement (printed only).** Three sites in these artifacts asserted it
  backwards and concluded that `-02`'s restored sites would surface as `IMPROVED` lines and leave the
  corpus green; they would in fact hard-fail it. Design **§4.8a** now carries the statement and the
  re-record procedure it implies, and NFR-3, TC-2e and Phase 1's exit criteria cite it rather than
  restating it.

## Design Gaps

### Gap 2.1: `-02`'s home is unknown

- **Question**: which component computes the reaching-definitions answer, in which package, cached how?
- **Options / leaning**: none stated deliberately — it is a function of the direction.
- **Resolved by**: design §3.7 firing, then design §5 written against it. Contract already fixed
  (design §2.7) so the answer cannot quietly redefine the behaviour.

### Gap 2.2: What happens when a use has no CFG instruction

- **Question**: F5's closure case, and any element the CFG never emits an instruction for. Does
  `-02` fail **open** (fall back to today's `upSet.any`, keeping BUG-441's over-approximation) or
  **closed** (treat the provenance as accountable, permitting an ERROR)?
- **Options / leaning**: failing open is the BUG-419-safe direction — it never asserts more than the
  model knows. Failing closed is what produces requirements TC-2d's regression. **The leaning is
  fail-open**, but the frequency is unknown and decides whether fail-open leaves `-02` with any
  effect at all.
- **Resolved by**: DR-05 (does the write get lost?) and DR-07 (`JOIN`, how often is there no
  instruction?). Folded into design §5 when written.

### Gap 2.3: Whether direction A must reintroduce a condition-carrying instruction

- **Question**: `ConditionalInstruction`/`startConditionalNode` are unused (`grep -rn
  "ConditionalInstruction\|startConditionalNode\|TransparentInstruction" src/` → no matches) and
  `LuaBranchInstruction` was deleted by MAINT-31
  (`docs/features/maint/31-dead-code-sweep/design.md:40-41` — the sentence begins at `:40`, not
  `:39`, which is that bullet's `DebugCommandKind.EXIT` clause). Abstract interpretation needs to
  know which successor is the true branch.
- **Options / leaning**: unresolved on purpose — it only matters if D2 fires.
- **Resolved by**: design §3.7 selecting D2; then it is A's design, and MAINT-31's note (*"MAINT-29
  can reintroduce it if it uses it for condition nodes"*) is the standing permission.

### Gap 2.4: NFR-2's cancellation debt on the existing builder

- **Question**: `LuaControlFlowBuilder` has **no** `ProgressManager.checkCanceled()` (F7). Tolerable
  for one inspection; is it tolerable on the `forFile` path?
- **Options / leaning**: `iteratePrev` covers the *query* (`ControlFlowUtil.java:104`), so only the
  *build* is exposed, and it is bounded by file size.
- **Resolved by**: DR-13's `worstOwnerBuild` figure, against an **exact** threshold: **`> 50 ms`**,
  strictly greater, reported to one decimal — `50.0` passes, `50.1` does not. Above it, adding
  `ProgressManager.checkCanceled()` to `LuaControlFlowBuilder.visitBlock`'s statement loop
  (`LuaControlFlowBuilder.kt:96`) is a one-line prerequisite for any direction and is filed as such;
  at or below it the figure is recorded and left. **50 ms is derived, not judged**: `forFile` is on
  the completion path, where COMP-09's NFR-1 budgets **< 100 ms** to first result
  (`docs/features/completion/09-member-enumeration/requirements.md:143`), and 50 ms is half of it —
  the point at which one un-cancellable build costs as much as everything else completion may do.
  An earlier revision wrote `~50 ms`, which was the only judgement-admitting threshold in a plan
  whose design §3.7 claims *"Every threshold is stated; none is left to judgement."*

## Pre-Implementation De-risking Tasks

**Sanctioned invocations, and no others.** Unit harnesses:
`tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*AnalysisSeven*'"`. Corpus:
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache -PwithCorpus"`, then
`… run "test --rerun --tests '*LuaInspectionParityTest*'"` **by name** (it is deliberately outside
the `*Corpus*` filter — `LuaInspectionParityTest.kt:32-36`). Never `./gradlew` locally; never two
`run`s concurrently.

| ID | Question the DR answers | Probe / exact command | What each outcome selects | Status |
| :-- | :-- | :-- | :-- | :-- |
| ANALYSIS-07-00-DR-01 | **Does BUG-441's over-approximation produce a demonstrable false negative today?** Its gate reads the whole `upSet`, so an unknown that is *overwritten before the use* still suppresses | `AnalysisSevenReachingDefsSpikeTest.testReachingDefinitionsAgainstUpSet`, fixture `P1-killed-unknown` (design §2.1). Output §4.1; the `diagnostics` field is what decides, not the graph | `VERDICT=FALSE_NEGATIVE` → `FN=true`, `-02` has a reproducible defect and TC-2a is its acceptance test. `CORRECT` → `FN=false`, and D0 becomes live if DR-02 also returns 0 | todo |
| ANALYSIS-07-00-DR-02 | **How many corpus sites would a reaching-definitions query RESTORE?** `-02`'s only measurable payoff, given 11 sites of headroom | Temporary `CorpusSweep.accumulateHits` patch (design §2.6, format §4.8) run twice: once unmodified, once with `unknownProvenance` computed by §3.1. `diff -u` the two dumps | `RESTORED > 0` → `-02` has a number. `RESTORED == 0` **and** `FN == false` → **D0: cancel `-02`**, file the finding as a BUG + roadmap row. **The other direction of the same diff is the input `NEW_SUPPRESSED`** (design §4.8): a line present before and absent after means the throwaway query *silenced* a diagnostic BUG-441's gate emitted, which a strictly-more-precise answer cannot do — so it is a defect **in the probe**, it fires design §3.7's **`DX`** gate, and the destination is *"record the lines verbatim, fix the probe, re-run DR-02"*, **not** a fall-through to D2/D3/D4. It also blocks D1 by an explicit `NEW_SUPPRESSED == 0` conjunct in D1's own rule. An earlier revision of this row said only *"blocks D1 regardless"*, with no symbol, no term in D1 and no stated destination | todo |
| ANALYSIS-07-00-DR-03 | **Is §3.1's walk sound over the platform's shared `visited[]`?** The utility marks visitation once for the whole walk, not per path | `AnalysisSevenReachingDefsSpikeTest.testDiamondAndLoopSoundness` — `P3-shadowed`, `P5-diamond`, `P6-loop`, `P7-goto` with the asserted expected sets in design §3.1 step 7, **evaluated under `matchesByBinding`** | All four match → `RD_OK=true`, D1 is reachable. Any mismatch → `RD_OK=false`; C would then need either a CFG repair or its own walker, and a walker is a third analysis, so this selects **D2** | todo |
| ANALYSIS-07-00-DR-04 | **How wrong is by-name matching?** F4: the CFG resolves no bindings; `symbol.LuaShadowingVariable.*` totals 320 sites over 419 corpus files | `P3-shadowed` under both `matchesByName` and `matchesByBinding` (design §3.2). Output §4.1's `byName`/`byBinding`/`unresolved` fields | `byName != byBinding` → `NAME_DELTA=true`. This selects **no branch** — it makes §3.2's binding step **mandatory in design §5** rather than optional, and a §5 that keys on `variableName` is then a defect (design §3.7, D1's note). The *branch*-selecting half of this fixture is `P3` under `matchesByBinding`, which is DR-03's `RD_OK`. **The `unresolved` share now has a threshold and a consequence, which an earlier revision did not give it.** §4.1 prints it as `unresolved = <targetPairs>/<allPairs>`: the **denominator is diagnostic only and has no threshold** — it counts every (write, read) pair including the deliberately-undeclared globals `cond`/`c`/`e`/`wx` that §2.1's fixtures introduce precisely so they are not modelled, so a non-zero denominator is the expected state. The **numerator must be `0` on all eight fixtures**, because every fixture target (`d`, `x`, `p`) is a declared local on which `declarationOf` is total (design §3.2's first edge note). Their sum is the input **`UNRESOLVED_TARGET`**, and **`UNRESOLVED_TARGET > 0` fires design §3.7's `DX`** — `matchesByBinding` silently degraded to `matchesByName`, so `RD_OK` and `NAME_DELTA` would be measuring the name predicate twice. Fix the probe, re-run §2.1 only; `COVERAGE`/`JOIN`/`LCS` carry forward | todo |
| ANALYSIS-07-00-DR-05 | **Is an upvalue write lost?** F5: the file CFG does not descend into function bodies | `P4-closure`, run against the lexically-enclosing owner's CFG and, separately, against every owner's CFG in the file (design §3.3). The union is printed as `union=UNSUPPORTED` with per-owner sets — **the harness must not invent a cross-graph walk** | Write lost → `CLOSURE_LOST=true`, D1 is blocked and this selects **D2**. Not lost → C survives this test | todo |
| ANALYSIS-07-00-DR-06 | **How much of the type engine's value domain has no CFG instruction?** Sizes direction A. F11: the CFG has no `visitBinOpExpr`/`visitFuncCall`/`visitIndexExpr`/`visitTableConstructor`/`visitTerminalExpr`/`visitUnOpExpr` | `AnalysisSevenCoverageSpikeTest` on the two pinned files `penlight/lua/pl/stringx.lua` and `luarocks/src/luarocks/fs/lua.lua` (design §3.4). Output §4.2 | `COVERAGE ≥ 80 %` → A is an increment (D2 reachable). `< 80 %` → A is a rebuild; the decision falls to `LCS` (D3 vs D4) | todo |
| ANALYSIS-07-00-DR-07 | **Can a `VariableNode` be joined to a CFG instruction by PSI identity?** The join key both C and B depend on | `AnalysisSevenJoinSpikeTest.testEveryVariableNodeJoinsToAnInstruction`, same two files (design §3.5). Output §4.3 reports exact-identity **and** ancestor joins separately | `JOIN ≥ 99 %` → C's bridge is total (D1 term). `< 99 %` → the unjoined histogram says whether the residue is F5's closures (repairable, D2) or something structural | todo |
| ANALYSIS-07-00-DR-08 | **What does building every owner's CFG cost against `LuaTypesSnapshot.forFile`?** NFR-1: `forFile` is on the completion path, where [[COMP-09]] budgets 100 ms to first result | `AnalysisSevenCostSpikeTest`, **medians of five cold samples** (design §2.4 — single-shot timing produced −60 % spread and a flipped verdict on `COMP-09-00-DR-08`). Output §4.5 | **Shapes the design, selects no branch** (design §3.7's obligations table). `≤ 10 %` → §5 may compute on demand. `> 10 %` → §5 **must** cache per file through `CachedValuesManager` keyed on the PSI file, mirroring `ControlFlowCache.kt:6-12`, and re-measure against NFR-1 before landing | todo |
| ANALYSIS-07-00-DR-09 | **Is the CFG's instruction order a legal construction order for the type graph?** The question that decides whether direction B is an increment or a rewrite — i.e. TYPE-08 §9's actual content | `AnalysisSevenJoinSpikeTest.testInstructionOrderAgainstNodeCreationOrder`, LCS per design §3.5 step 4. Output §4.4 | With `COVERAGE < 80 %`: `LCS ≥ 90 %` → **D3, direction B**. `LCS < 90 %` → **D4, TYPE-08 §9 CONFIRMED**, recorded with the numbers in §4.9 | todo |
| ANALYSIS-07-00-DR-10 | **Is BUG-435 a CFG problem at all?** Its report calls its own mechanism a hypothesis and says to read the installed node | `AnalysisSevenDescopeSpikeTest.testWhatTheGuardInstalls` on BUG-435's fixture — print the `write` type and members of both the displaced binding (`scope.lookup`, `LuaTypesVisitor.kt:468`) and the installed one (`:475-478`). Output §4.6 | `NODE_REPLACED_BY_MEMBERLESS_TABLE` → **`-03` is `cancelled` here and refiled as a one-site bug fix**; it is not CFG work. `OTHER` → design §6 is written from what the dump showed | todo |
| ANALYSIS-07-00-DR-11 | **Are BUG-428's 2 residual sites really call-site sensitivity?** | `AnalysisSevenDescopeSpikeTest.testWhatTheBugFourTwoEightResidualIs` on `penlight/lua/pl/config.lua:131` and `stringx.lua:231`. **Both lines confirmed present in the fetched corpus**: `config.lua:131` is `local function check_cnfg (var,def)` and `stringx.lua:231` is `local function _find_all(s,sub,first,last,allow_overlap)` — i.e. both really are function-declaration lines, matching BUG-428's description of the anchor. Output §4.7 | `CALL_SITE_UNION` → `-04` stays a `Could`. Anything else → **`-04` is `cancelled`** and refiled | todo |
| ANALYSIS-07-00-DR-12 | **What is the pre-change site list?** Without it, no movement can be attributed — the failure that cost BUG-441's landing note its first version | The §2.6 patch, run once on clean `99b45f92`. Expected shape from the committed baselines: **11** type-inspection sites + **5** `LuaUnreachableCode` sites. Record `filesAtExactParity` from `LuaInspectionParityTest` in the same session | **Gate, not a selector.** No pre-change list → DR-02 cannot be read and no phase may land. Also fixes NFR-4's parity number as a concrete before-figure | todo |
| ANALYSIS-07-00-DR-13 | **Is the CFG builder's missing `checkCanceled` (F7) a real hazard on the `forFile` path?** | `AnalysisSevenCostSpikeTest`'s `worstOwnerBuild=` field (§4.5), taken over every owner of the two pinned files plus `test/corpus/zerobrane/api/lua/moai.lua` — **confirmed** the largest `.lua` under any
corpus root at 696 750 bytes (next: `love2d.lua` 353 094, `marmalade.lua` 183 877) | **Gate, with an exact threshold.** **`> 50 ms`** — strictly greater, figure reported to one decimal, so `50.0` passes and `50.1` does not → adding `ProgressManager.checkCanceled()` to `LuaControlFlowBuilder.visitBlock`'s statement loop (`:96`) is a prerequisite for every direction and is filed as its own task. **`≤ 50 ms`** → recorded and left. **50 ms is derived**: COMP-09's NFR-1 budgets **< 100 ms** to first completion result (`docs/features/completion/09-member-enumeration/requirements.md:143`) and `forFile` is on that path, so 50 ms is half the budget spent in one build the user cannot interrupt (F7). An earlier revision wrote `> ~50 ms` — the only judgement-admitting threshold in a plan whose design §3.7 claims none is left to judgement | todo |

## Technical Debt & Future Work

- **TBD: `INSP-04-C1` / `INSP-04-C2`** — `error()`/`os.exit()` as terminators and `while true` as
  non-terminating are CFG-builder changes that would ride along with direction A. Deliberately **not**
  bundled: neither justifies this feature and bundling them would confound DR-12's before/after site
  lists for `LuaUnreachableCode`.
- **TBD: `LuaControlFlowBuilder` has no Lua 5.5 `global` case.** Out of scope for Phase 0 (see
  Premises). Part of D2's binding-repair increment if it fires.
- **Observation, not this feature's to fix: `tooling/corpus/corpus.json`'s own `$comment` block is
  stale about `moduleRoot`.** It says *"Omit when requires are relative to the checkout root, as all
  four below are"*, and `CorpusEntry.moduleRoot`'s KDoc (`CorpusManifest.kt:19-25`) says *"Null means
  'resolve from the checkout root', which is what every currently-pinned project does."* **Penlight
  declares `"moduleRoot": "lua"`** — read from the manifest at `99b45f92`. Both comments contradict
  the data they document. Recorded here because design §2.0's fixture setup depends on `moduleRoot`
  being real for penlight and absent for luarocks, so the next reader must trust
  `entry.moduleRoot`, **not** the prose around it. Filing it as a chore is a separate call.
- **TBD: a permanent test seam on `LuaTypesSnapshot`.** Phase 0 uses one reflective read (design
  §2.2.1). If `-02`'s eventual tests need graph access routinely, an `@TestOnly internal` accessor in
  the manner of `LuaTypeGraph.compatMemoSize()` is the right shape — a §5 decision, not a Phase 0 one.

## Test Case Gaps

- **`P7-goto` has no counterpart in the corpus.** `goto` is Lua 5.2+ and rare in the four pinned
  projects; the fixture is synthetic and there is no real-world sample to cross-check `resolveGoto`'s
  block-scoped resolution (`LuaControlFlowBuilder.kt:86-93`) against. Recorded rather than papered
  over: a `P7` disagreement is evidence about the *fixture* as much as about the algorithm, and
  should be triaged against `LuaControlFlowTest`'s existing `goto` cases before it is read as
  selecting D2.
- **No requirement covers `repeat … until`.** The type engine has no `visitRepeatStatement` (F11) so
  it has no branch-specific behaviour to test, but the CFG does (`LuaControlFlowBuilder.kt:193-213`)
  and a `until` condition reads variables written in the body — an unusual scoping rule in Lua. If
  D2 or D3 fires, a `repeat` fixture belongs in §5's test list.
- **Nothing measures inlay/completion movement.** BUG-441 warns that changes here reach *"inlays and
  completion, not only diagnostics"*. The corpus sweep counts inspections only
  (`LuaCorpusSweepTest.kt:53-64`), so a `-02` that shifts an inferred type without changing a
  diagnostic is invisible to every gate named here. Not closed at planning time; it is design §5's
  obligation once the direction is known.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md) — §3.7 is the decision procedure these DRs feed
- Plan: [implementation-plan.md](implementation-plan.md)
- [[BUG-441]] (the emission path, pinned) · [[BUG-435]] (DR-10) · [[BUG-428]] (DR-11) ·
  [[BUG-417]] (parity criterion) · [[BUG-419]] / [[BUG-424]] (graph-vs-inspection incomparabilities)
- [[COMP-09]] `risks-and-gaps.md` — the DR format this follows


## Step 9 review record — round 3 (2026-08-20): PASS, with four non-gating observations

Three review rounds: FAIL (4/10 DoD, 11 defects) → FAIL (2/10, 4 defects) → **PASS (10/10)**. The
reviewer re-derived §3.7's totality from scratch each round and reproduced the brute force exactly;
the ~290 lines added by the second remediation introduced no false statement. Recorded here so the
observations are not lost between planning and Phase 0.

| # | Observation | Where it bites |
| :-- | :-- | :-- |
| M-1 | §3.1a's numeric-`for` latency is **not** extended to §3.2's `matchesByBinding`. A numeric-for WRITE anchors on a bare IDENTIFIER leaf (`LuaControlFlowBuilder.kt:219-220`) carrying no `PsiReference` — the only contributors are `LuaRequireReferenceContributor` and `LuaLabelReferenceContributor` — so `declarationOf(write)` returns null, step 2.4 returns `false`, and the miss is invisible to the `unresolved=` numerator, which counts only step 2.3. **Not gating for Phase 0**: none of §2.1's eight fixtures is a numeric `for`, so `UNRESOLVED_TARGET == 0` stays achievable. **§5 is instructed to reuse §3.1a — resolve it there.** |
| M-2 | §2.7 property 1 cites `LuaTypeGraph.kt:257-285` for "still wire member edges"; `reportIncompatible` wires none — the source comment at `:279-281` attributes that to "the surrounding checks". The normative rule ("may only ever change a tier, never skip work") is correct; only the citation is loose. |
| M-3 | `implementation-plan.md` exit criterion 5's first mutation-proof conjunct ("leave T0.9's probe in place and the members go red") holds only when `RESTORED > 0`. Under branch D0 (`RESTORED == 0`) that demonstration is void and a different proof is needed. |
| M-4 | `requirements.md` TC-2e bullet 4 says zero `IMPROVED` lines and zero `LuaUnreachableCode` movement, "**either** is the `NEW_SUPPRESSED` defect and fires DX". True of the first conjunct only — §4.8 states `LuaUnreachableCode` is not an input to §3.7. The observables required are identical either way; `implementation-plan.md` Phase 1 exit criterion 3 states the pair correctly. |

**Two supervisor-origin defects are worth carrying forward as process, not just as fixes.** Round 2's
gating defect — the ratchet direction asserted backwards — came from a dispatch that cited
`CorpusGuards` (which shows *that* improvements print) and never `CorpusMetrics.kt:283-284` (which
shows *which direction is which*). Round 1's TC-4a line error was inherited from [[BUG-428]]'s report,
written by the same supervisor hours earlier. In both cases the planner had no way to get it right.
**Cite the comparator, not the guard**, and treat an upstream bug report as a source to re-verify
rather than to quote.

## DR-10 — RESOLVED EARLY 2026-08-20, and it cancels `ANALYSIS-07-03`

DR-10 asked whether [[BUG-435]] is a wrong node at `LuaTypesVisitor.injectNarrowedBinding`
(`:464-478`) rather than a missing subgraph-constraint capability. **It is.** Probed inside that
method, on the report's own fixture:

```
originalWrite = Table(className=null, localMembers={fromLocal=…}, isExact=false)
guardNarrowed = Table(className=null, localMembers={},            isExact=false)
chosen        = Table(className=null, localMembers={},            isExact=false)
```

That is the `NODE_REPLACED_BY_MEMBERLESS_TABLE` verdict, which `implementation-plan.md` Phase 2
states in advance means **"this phase does not exist"** — the work being a one-site fix at exactly
that range, refiled as a bug, with the requirement `cancelled` and the dump quoted. All three
conditions are met: the fix is one site in that range, it shipped as BUG-435, and the dump is above.

Phase 2's own exit criterion TC-3a — *"`Shadow.` inside `if type(Shadow) == "table" then` offers
`fromLocal`, measured through `myFixture.completeBasic()` and not through a direct type query"* — is
satisfied by `LuaGuardNarrowingMembersTest.testMembersSurviveATypeTableGuard`, which drives
`completeBasic()` exactly as specified.

**Consequence:** `ANALYSIS-07-03` is `cancelled` and Phase 2 has no tasks. **Phase 2 keeps its
number**: `cancelled` is a terminal state this repo keeps visible, and renumbering would churn
cross-references across four artifacts and the roadmap on a plan that took three Step 9 rounds.
Phases 0, 1, 3 and 4 are unaffected — DR-10 was scoped to `-03` alone and decides nothing about the
direction, which is still Phase 0's job.
