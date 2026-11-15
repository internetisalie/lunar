---
id: "MAINT-61-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-61"
folders:
  - "[[features/maint/61-own-build-cost/requirements|requirements]]"
---

# MAINT-61: Risks & Gaps

## Critical Risks

### Risk 1.1: Lazy visit facts change what the checker sees
- **Impact**: different findings, if computing `VisitFacts` changes the graph revision (a new node or edge), because the change
  computes them on fewer visits (design §3.2). Either one bumps the graph revision.
- **Likelihood**: low. Over 19 hub files read 15 times each in DR-08, every file gave one digest.
  It is unproven that `isUnknown(write)` never runs a `LazyValueElement` compute that adds a node or an edge.
- **Mitigation**: TC 11, TC 12 and TC 13 (requirements). DR-09 measures it in Phase 0.

### Risk 1.2: The sweep gain is inside run-to-run noise
- **Impact**: MAINT-61-05 stays unestablished even though the change cuts checker time.
- **Likelihood**: medium. DR-05's `ctl1` (unmodified) and `p4both` (delta scan and edge counter)
  admit the same pairs, yet their miss counts differ by 8 per bound. The checker time fell by 30 % in DR-08's
  interleaved reads, but a sweep reads each file once. DR-08's probe also indexed the sets directly, while
  design §3.1 indexes the `toList()` copies of today, so DR-08 can overstate this design's gain by the
  copy's cost.
- **Mitigation**: MAINT-61-05 is a Should with its own measure. MAINT-61-03 is the deterministic
  Must.

## Design Gaps

### Gap 2.1: Checker cost this item leaves
DR-08 removes about 30 % of the hubs' checker time. The remainder is the walks (`resolveWrite`,
`resolveRead`) and the pair volume, which grows with node count (`druid_instance.lua`: 1 779 644
nodes, DR-01). The only measured lever for the walks is MAINT-64's flattenings, which change
findings (MAINT-60 DR-06). The rows of [files-over-target.md](files-over-target.md) still missing
after Phase 2 wait on MAINT-64's decision.

### Gap 2.2: The verdict files every own-build pass under MAINT-61
`SnapshotCostVerdict.OWN_BUILD_ITEM` is `"MAINT-61"`, and rule 4 of MAINT-60 design §3.3 files a
pass by its dominant dependency. So the verdict prints MAINT-61 for MAINT-67's and MAINT-68's passes
too. The split is DR-04's classification, and each item's rows are in
[files-over-target.md](files-over-target.md). Changing the verdict is not in scope.

### Gap 2.3: Hubs not profiled
DR-01 did not sample luals, gitsigns or telescope. Their rows (luals `script/vm/compiler.lua`, the
neovim definition files `uv/_meta.lua` and `vim/lsp.lua`, gitsigns `hunks.lua` and `actions.lua`)
stay with this item. DR-10 profiles them.

## Pre-Implementation De-risking Tasks

### Premises
| Premise | Kind | Settled by |
|---|---|---|
| The own build of the slow files is spent in the checker, not the visitor | runtime | DR-01: mostly, with exceptions that go to MAINT-68 |
| factoryplanner's misses are own-build cost | runtime | DR-02, DR-03: **refuted**. They come from edit fan-out (MAINT-67) |
| Skipping pairs examined on an earlier visit changes no pair the checker admits | runtime | DR-06: no skipped pair was unexamined |
| The skip changes the work on small fixtures | runtime | DR-07 |
| Delta scan and lazy facts cut checker time without changing findings | runtime | DR-05, DR-08 |
| The edge counter decides convergence as `edgeCount` does | read | design §3.3 |
| Sweep-to-sweep spread of one commit is within MAINT-61-04's margin | runtime | MAINT-63 DR-25 (four sweeps at `bceb8e3e`: edit 11, cold 4) and MAINT-63's Phase 2 base sweeps at `353974b7`, recorded in its PR (edit 266 and 277, cold 52 and 52). Neither is at an engine with MAINT-63's change, so MAINT-61-04 also takes this item's own base spread |
| Lazy facts change no graph revision | runtime | open: DR-09 |

### Results
All runs used the libvirt builder at `67508823` with the probe applied as a temporary edit. Each
command is `builder-run libvirt "test -PwithSnapshotSweep --rerun --no-build-cache <filters>"`.
*Twelve members* means one `--tests '*LuaSnapshotCostSweepTest.<method>'` filter each for druid,
opensmce, factoryplanner, prism, kristal, differ, ldoc, awesome, rampant, blinkcmp, snabb and
ljtk. The probe and its outputs are in `~/.cache/claude-scratch/maint-61-plan/` (`probe-final.diff`,
`probe-ab.diff`, and one directory per run). None of the probe code is committed.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Twelve members (`p1`). Phase clocks in `LuaTypesVisitor.buildSnapshot`, paused while a nested build runs: seed, visit (`file.accept`), check (`checkTypes`), snapshot. A daemon thread took a stack sample of the reading thread every 4 ms and charged it to the innermost build | where an own build spends its time | done. 4 774 builds. In the 157 with 250 ms or more of self time: visit 16 711 ms, check 59 504 ms, seed and snapshot 1 ms. Samples by innermost Lunar frame, hubs: druid `druid_instance.lua` pair loop 49 %, visitor 40 %; `component.lua` pair loop 30 %, visitor 34 %, compatibility 16 %; opensmce `SphereGroup.lua` walk and propagation 65 %; prism `color.lua` walk 62 %; `display.lua` `LuaGraphType$Table.hashCode` 59 %; factoryplanner `generator.lua` walk 52 %; ljtk `lang/parser.lua` walk 69 %; opensmce `RootScene.lua` visitor 95 %. Pair-loop samples by line: `checkedPairs.add` 3 408, `upSet`/`downSet.toList()` 1 504, `edgeCount` 731. `druid_instance.lua`'s graph held 1 779 644 nodes and 607 755 pairs after 3 passes. A visitor-heavy hub can be slow on its first build only: in DR-05's `ctl1`, `RootScene.lua`'s three builds spent 438, 58 and 61 ms in the visitor |
| DR-02 | druid, opensmce, factoryplanner (`p2`). `LuaSourceChangeTracker.trackerFor` logged, for each snapshot, the size of its recorded sources, whether they hold the file the sweep just edited, and the number of pending frames | why a hub is rebuilt after an edit | done. factoryplanner: 1 427 of 1 588 trackers carry pending frames, and `generator.lua`'s rebuilt snapshot records the edited file in 10 of 56 rebuilds. druid: `component.lua` records the edited file in 22 of 24, with no pending frames. opensmce: `SphereGroup.lua` reached `trackerFor` once, because its build ran past the 1 s edit deadline and was cancelled every other time |
| DR-03 | druid, opensmce, factoryplanner, awesome, with `SnapshotCostVerdict.EDIT_DEADLINE_MS` at 60 000 (`p3`) | whether the misses are fan-out or deadline thrash | done. Edit misses: factoryplanner 62 of 124, an average of 26 builds per edit read, and the six slowest reads 2 847 to 3 273 ms with 53 to 56 builds each. opensmce 14, druid 24, awesome 4. A factoryplanner edit costs the rebuild of most of the member, and no single build dominates it |
| DR-04 | `classify.py` and `table.py` over the change sweeps' `ranked.tsv`, applying the rule of [files-over-target.md](files-over-target.md). DR-01's largest sample bucket gave the hubs that rule lists | the split | done. Passes per sweep, 1 / 2: **MAINT-61** (checker) 65 / 62, plus 5 / 7 in hubs DR-01 did not sample; **MAINT-67** (fan-out) 63 / 62; **MAINT-68** (visitor) 21 / 18. Total 154 / 149 |
| DR-05 | Twelve members, three runs: unmodified (`ctl1`), delta scan plus edge counter (`p4both`), and the same with no revision bump on value and use creation (`p5`). The sampler was off | effect and noise | done. Cold and edit misses: `ctl1` 41 / 147, `p4both` 33 / 139, `p5` 34 / 144. With the sampler on (`p1`), unmodified: 36 / 139. Summed check time: 101 771, 85 971 and 95 501 ms. Cold digests of rows read `ok` and not prebuilt in both runs: `ctl1` against `p4both`, 546 equal and 0 different. druid cold misses: 15, 8, 9. The narrowed bump gains nothing, so it is not adopted |
| DR-06 | Twelve members with the delta scan (`p6inv`, `p7reach`). On each revisit, the probe counted the skipped pairs that were not in `checkedPairs`. It also counted the unexamined pairs that two wrong skips would drop: M1 skips old values for new uses, M2 skips new values for old uses. It also counted the passes that added edges but no pairs | exactness of the skip; reachability of its mutants | done. Unexamined skipped pairs: **0** over 28 563 456 revisits. M1 would drop 33 783 unexamined pairs in 773 builds, M2 29 402 in 684 (for example druid `component.lua`, 840 and 575). Passes with edges but no new pairs: 0, so the edge term never decides convergence alone on this corpus |
| DR-07 | Six light fixtures (`Probe61FixtureTest`, texts in `probe-final.diff`) with the delta scan. The probe counted revisits, revisits whose sets grew, and `checkedPairs.add` calls with and without the skip | TC 3 | done. Each fixture converges in 2 passes. Attempts with the skip / without: `callback` 23 / 46, `callback2` 122 / 232, `method` 407 / 796, `fwd` 296 / 520, `chain` 253 / 506, `hof` 737 / 1 474; the six without the skip sum to 3 574. `method` has 41 revisits, 1 of them with growth. M1 and M2 drop no pair on any of the fixtures |
| DR-08 | Twelve members (`p9ab`). After the cold pass, each of 19 hub files was rebuilt alone 5 times (differ `lua/differ/view.lua`; prism `engine/math/color.lua`, `engine/math/vector.lua`, `spectrum/display.lua`; opensmce `src/Configs/utils.lua`, `src/Game/Shooter.lua`, `src/Game/SphereGroup.lua`; factoryplanner `modfiles/backend/data/Machine.lua`, `modfiles/backend/handlers/generator.lua`; druid `druid/base/button.lua`, `druid/base/scroll.lua`, `druid/component.lua`, `druid/custom/rich_input/rich_input.lua`, `druid/druid.lua`, `druid/extended/input.lua`, `druid/system/druid_instance.lua`; ldoc `ldoc/markdown.lua`; snabb `src/lib/yang/schema.lua`; ljtk `lang/parser.lua`), with `putUserData(LuaTypesVisitor.KEY, null)` and a 60 s read. Each repetition cycled modes 0 (unmodified), 1 (delta scan plus edge counter) and 2 (mode 1 plus lazy facts), timing the check phase | the design's effect, interleaved | done. Sum of median check ms: **13 386, 10 465, 9 344** (mode 2 is 30 % below mode 0). Per file, mode 0 → 2: `SphereGroup.lua` 2 710 → 1 310, `druid_instance.lua` 1 963 → 1 116, `Machine.lua` 522 → 327, `generator.lua` 249 → 97, `Shooter.lua` 540 → 370, `color.lua` 1 062 → 948, `display.lua` 1 036 → 906, `lang/parser.lua` 1 435 → 1 302. All 285 reads `ok`; each file gave one digest across its 15 reads |
| DR-09 | Phase 0: on the twelve members, compare the graph's `revision` before and after every `VisitFacts` computation, and count the computations where it changed | Risk 1.1, MAINT-61-03's branch | open |
| DR-10 | Profile the luals, gitsigns and telescope hubs as DR-01 did | Gap 2.3 | open |
