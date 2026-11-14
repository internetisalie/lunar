---
id: "MAINT-65-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-65"
folders:
  - "[[features/maint/65-cycle-aware-inference/requirements|requirements]]"
---

# MAINT-65: Risks & Gaps

**Setup.** All probe runs share the following:
- Workload: MAINT-63 DR-08's eight-member subset. That is 699 files, 338 MAINT-63 table passes
  ("T63") and 23 MAINT-61 table rows ("T61").
- Read sequence per file: a cold read (60 s deadline), then `"\n"` appended and a read with a 1 s
  deadline, then a settled re-read.
- Soundness pass: empty a dependency, re-read, then restore it.
- A miss is a read of 250 ms or more, or one that did not finish.
- Host: the libvirt builder.
- Variant names (base, svca, r10) are defined in MAINT-63's variants table.
- The probe code is not committed.

## Critical Risks

### Risk 1.1: The gate exposes findings that base hides by build order
- **Impact**: DR-05 found six cold digests that differ from base. Five of those files gain
  HYPOTHESIS findings, which `LuaTypeHypothesisAnnotator` shows to users. DR-08 shows that base
  reports the same findings whenever the file is built at top level, and that every added finding
  is BUG-98's false report.
- **Likelihood**: high. It was measured in two runs (DR-04) and reproduced on base (DR-08).
- **Mitigation**: Decision 2.1.

### Risk 1.2: The budget stays unmet after this item
- **Impact**: v0.21.21 goal 1 stays held. With the gate, 95 of the 338 T63 passes and 11 of the
  23 T61 rows still miss (DR-04).
- **Likelihood**: certain on the subset. The rest of the corpus is unmeasured until TC 6.
- **Mitigation**: none in this item. The remaining misses have not been attributed. That belongs to
  MAINT-63 and MAINT-61 once this lands. Per-file invalidation adds nothing on top of the gate
  (DR-04).

### Risk 1.3: A dropped dependency serves a stale snapshot
- **Impact**: a wrong type after an edit.
- **Likelihood**: low. The skipped build fed no edge into the caller's graph (design §3.1). Every
  soundness re-read rebuilt: 56/56 under the gate on base invalidation (DR-04).
- **Mitigation**: TC 4, plus the existing TYPE-11 soundness tests in the full suite.

## Design Gaps

### Decision 2.1: MAINT-65-02 compares alone-read digests, after BUG-98
- **Decision**: option (c), by the supervisor, 2026-11-14.
  - The false report is fixed first, as [[BUG-98]].
  - MAINT-65-02 compares each candidate file's digest when it is read alone, instead of
    sweep-order digests.
  - The requirement itself is in requirements.md.
- **Rejected**:
  - (a) Make the gate preserve base's digests. Base's digests come from building these files
    nested inside another file's build, so preserving them keeps the nested builds that the gate
    removes.
  - (b) Accept the additions as correct. They are false reports (DR-08).
- **Evidence**: DR-08. Two of the six files change sweep-order digest between identical base runs,
  so a sweep-order comparison can fail with no code change.

## Technical Debt & Future Work
- **TBD: building an import cycle as one unit.** Refuted (DR-03). Census: the largest static
  require SCCs are awesome 57, luals 45, tntmetrics 35 and tntcrud 19. Members with no module root
  resolve requires to the `/src` fixture copies, so their edited files sit inside cycles. Building
  the census cost 5 to 1 421 ms per member. Do not revisit without a new measurement.
- **TBD: MAINT-61's remaining group** (awesome, differ, luals) is outside the subset. The gate's
  effect there is unmeasured until TC 6.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Measure base on the subset | baseline | done: T63 **267/338** miss (MAINT-63 DR-01 measured 281, so run variance is about 5%). T61 18/23. Cold sum 34.1 s. Edit p50 71 ms. 173 edit reads timed out. Edit-phase guard hits: module 2 812, in-progress 22 863. Soundness 71/71 rebuilt. |
| DR-02 | Attribute edit-phase cross-file in-progress hits by stack (telescope, gitsigns), measured under DR-03's unit-build variant (svca plus r10), not on base | the cause | done: **3 243 of 3 310** pass through `forFile` ← `LuaExpectedCallbackResolver.functionTypeOf` ← `resolveStandardCalleeType` ← `resolveCalleeType` ← `propagateExpectedLambdaParams`. 67 pass through `getModuleType` / `resolveModule`. |
| DR-03 | Prototype: build each static require SCC as one unit, under svca plus r10 | the cycle direction | done, **refuted**: T63 297/338, T61 17/23. Cold sum 35.6 s. Edit p50 57 ms. Edit-phase module hits fell to 149, and in-progress hits fell from 22 863 (DR-01) to 15 284. **26 cold digests differ** from base. blinkcmp took 110 s with 1 837 leaks. SCC lookup cost about 10.8 s in total. Soundness 69/69. |
| DR-04 | Prototype the gate (design §3.1), once under svca plus r10 and once on base invalidation | design choice | done. **svca plus r10 plus the gate**: T63 **110/338**. T61 10/23. Cold sum 19.2 s. Edit p50 44 ms. 7 cold digests differ. Soundness 57/57. **The gate on base invalidation**: T63 **95/338**, T61 11/23, cold sum 18.5 s, edit p50 45 ms, 6 cold digests differ, soundness 56/56. |
| DR-05 | Findings under the gate on base invalidation | MAINT-65-02 | done, **changed**. `luacheck.json` (`c8a255eb…`) and conformance `results.json` (`5634e7ab…`) are byte-identical to base. Cold digests differ in six files: gitsigns `debug.lua`, `deleted_preview.lua`, `hunks.lua`, `system.lua`; blinkcmp `completion/list.lua`, `fuzzy/init.lua`. The findings diff contains only additions: 13 HYPOTHESIS `<Class> \| { ... } is not assignable to { ... }` findings across the first five files. `fuzzy/init.lua` flickered between runs. |
| DR-06 | Base's 1 s after-edit digest against its cold digest (MAINT-63 Gap 2.2) | order dependence in base | done: **30 of 526** completed edit reads differ (tntcrud 20, tntmetrics 2, tntqueue 8). Every settled re-read equals the cold digest (699/699). Base's after-edit answer therefore depends on build order inside the tnt* `/src` require cycles. Under DR-03's unit build, 0 of 604 differ. |
| DR-07 | Find a TC 1 fixture that fails on base and passes under the gate. Each fixture was read at the parent commit and with design §3.1 applied, recording the files built (`LuaSnapshotBuildMeter`) and the frame's `inProgressHits` | MAINT-65-01's observable | done. See the DR-07 table. **Build records cannot observe the gate.** A free-global or global-table callee in `lib.lua` builds `lib.lua` through another route even with the gate. A method on a `---@param`-typed receiver resolves nothing, even with a lambda. **`inProgressHits` can observe it.** For the same-file callee (TC 1, TC 2), the call without a lambda records `main.lua` on base and nothing under the gate. TC 3's fixture seeds `x` as `string` under the gate. Under the gate, `LambdaParamInferenceTest` and `ExpectedCallbackResolverTest` pass (TC 4). |
| DR-08 | At the parent commit (no gate), read each of DR-05's six files in two ways. "Alone": the file is read first, in a fresh fixture. "Sweep": the member's selected files are read in order, recording which file's build built the target and at what depth. Sweep was run twice for gitsigns and three times for blinkcmp | Decision 2.1 | done. See the DR-08 table. **Every one of the six gate digests is reproduced on base by "alone".** The base digests appear only when the file was built nested inside another build. All 13 added findings are false reports, filed as BUG-98. |
| DR-09 | Run-to-run variance of base's miss counts, from the four existing full sweeps at commit 9fb91435 (2 026 rows each: r1 and r2 `sweep.jsonl`, r3 and r4 `ranked.tsv`, which list only the non-in-band rows and so give each bound's misses directly), counted as MAINT-65-03 counts. Effect size from DR-01 and DR-04's subset rows, counted the same way | MAINT-65-03's rule | done. `cold-250`: 84, 70, 90, 66, a spread of **24**. `edit-250`: 567, 585, 608, 587, a spread of **41**. r1 and r3 each lost 20 rows to `unmeasured`, so coverage alone moves the cold count by about 20. On the 699-file subset, the gate moved `edit-250` from 274 to 97 (−177) and `cold-250` from 20 to 15 (−5). **Conclusion:** an edit improvement far larger than the spread is expected, so MAINT-65-03 asks for one beyond the spread. A cold improvement of the subset's size is inside the noise, so `cold-250` is held to no regression beyond the spread. A plain "worse change below better base" rule with two runs each would pass a change with no effect about one time in six (the chance that the two change runs rank lowest of four), so the rule carries the margins instead. |
| DR-10 | TC 7's stability and size. Six DR-08 files read alone twice at the parent commit and twice with design §3.1 applied. Candidate count from the DR-09 sweep pair and from DR-04 | TC 7 | done. **All 24 alone reads give the DR-08 alone digest**: repeated reads agree, and the gate's reads equal base's. The DR-09 pair, two unchanged runs, yields **2 candidates**: blinkcmp `completion/list.lua` and `fuzzy/init.lua`, the DR-08 flicker files. DR-04 found 6 changed digests in the 699-file subset. Scaling that to 2 026 files estimates about 17 gate candidates plus 2 from flicker, about 20 in total. Forty slots leave twice that headroom. The scaling is an estimate, not a measurement. |
| DR-11 | Classify Phase 3's TC 7 mismatch on luals `script/vm/compiler.lua` (base 1a9933cd, change 1e3e5b8b): three alone reads per commit in fresh fixtures, one JVM each | BUG-99 | done: each commit is stable within its JVM (513 vs 520 findings), and the gate causes the difference. Four false `vm.generic \| vm.generic` HYPOTHESIS findings (lines 2508, 2798–2799) become ERROR, and seven false `parser.object \| vm.generic \| nil` HYPOTHESIS findings are added. Base's digest also differs across JVM runs (622378133, -269674459, 1270241985). In the same acceptance, TC 5 and TC 6 passed: edit-250 misses 476/480 → 304/303, cold-250 62/62 → 45/43. The gate is held on BUG-99 |

**DR-07 results.** "Base" is the parent commit. "Gate" is the parent commit with design §3.1
applied. `x` is the lambda parameter's type under the gate; it is the same on base.

| Fixture (`main.lua`) | `lib.lua` built, base / gate | `inProgressHits`, base / gate | `x`, gate |
|---|---|---|---|
| free global `run_cb(1)` | yes / yes | `main.lua` / none | — |
| free global `run_cb(function(x) end)` | yes / yes | `main.lua` / `main.lua` | `string` |
| global table `Lib.run(1)` | yes / yes | `main.lua` / none | — |
| global table, no call (`local y = Lib`) | yes / yes | none / none | — |
| `r:run(1)`, `r` a `---@param Runner` | no / no | none / none | — |
| `r:run(function(x) end)` | no / no | none / none | `undefined` |
| same-file `run(1)` (TC 1) | no / no | `main.lua` / none | — |
| same-file `run(function(x) end)` (TC 2) | no / no | `main.lua` / `main.lua` | `string` |
| same-file, no call (`local y = run`) | no / no | none / none | — |
| `run2(1, function(x) end)` (TC 3) | no / no | `main.lua` / `main.lua` | `string` |

**DR-08 results.** Digests are the MAINT-60 errors digest. Gate digests are from DR-04.

| File | Base, alone | Base, sweep (built by, depth) | Gate (DR-04) | Base r1 (DR-01) |
|---|---|---|---|---|
| gitsigns `debug.lua` | 149648105 | 1 (`gitsigns.lua`, 7), both runs | 149648105 | 1 |
| gitsigns `deleted_preview.lua` | 416426393 | 416426393 (`gitsigns.lua`, 4), both runs | 416426393 | -1182535098 |
| gitsigns `hunks.lua` | 1551349424 | -1145956615 (`gitsigns.lua`, 10), both runs | 1551349424 | -1145956615 |
| gitsigns `system.lua` | 507555231 | 507555231 (`gitsigns.lua`, 6), both runs | 507555231 | 1 |
| blinkcmp `completion/list.lua` | -1365002103 | runs 1 and 3: -1790413118 (`blink-cmp.lua`, 7); run 2: -1336457114 (`blink-cmp.lua`, 11) | -1365002103 | -1336457114 |
| blinkcmp `fuzzy/init.lua` | -1150241783 | runs 1 and 3: -1150241783 (`completion/init.lua`, 2); run 2: -282898018 (`blink-cmp.lua`, 8) | -1150241783 | -282898018 |

- **`fuzzy/init.lua` flicker.** Built nested under `blink/cmp/completion/init.lua` at depth 2, it
  gets the alone digest, with three `blink.cmp.API | { ... }` findings. Built nested under
  `blink-cmp.lua` at depth 8, it gets r1's digest, without them. The read order was identical in
  all three runs, so which file builds it first is not determined by read order. The cause is not
  established.
- **The 13 added findings** ([[BUG-98]]): `Gitsigns.log` ×6 (`debug.lua` 2, `hunks.lua` 2,
  `system.lua` 2), `gitsigns.manager` ×4 (`deleted_preview.lua`) and `blink.cmp.API` ×3
  (`completion/list.lua`). The members used on those locals are all defined by the module's
  `@class` table: `dprint`, `eprintf`, `show` and `clear` in `debug/log.lua`; `on_detach`,
  `on_update` and `on_win` in `manager.lua`; `is_menu_visible` and `is_ghost_text_visible` in
  `blink/cmp/init.lua`. The check was member existence, not full signatures.

## Test Case Gaps
- No test pins which file builds a nested dependency first. DR-08's `fuzzy/init.lua` result
  changed between runs with the same read order, and nothing in the suite would notice.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
