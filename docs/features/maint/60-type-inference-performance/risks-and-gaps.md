---
id: "MAINT-60-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-60"
folders:
  - "[[features/maint/60-type-inference-performance/requirements|requirements]]"
---

# MAINT-60: Risks & Gaps

## Premises

| Premise | Kind | Settled by |
|---|---|---|
| Caching `LuaGraphType.Table.hashCode` removes BUG-96's cost | runtime | DR-01 — **refuted**: within noise |
| The recursive walks over closed `upSet`/`downSet` are the main cost | runtime | DR-01, DR-02 — yes on fallo and luals; flattening them halves fallo |
| Flattening the walks leaves findings unchanged | runtime | DR-06 — **refuted** for `write`/`read` (81 files differ); holds for `declaredDemand` |
| Skipping already-checked pairs (delta) is exact and pays | runtime | DR-06 — exact; pays 12 % outside fallo and luals |
| The MAINT-54 `luacheck` audit and conformance catch a findings change | runtime | DR-03 — **refuted**: identical under every configuration, including ones the sweep shows change findings |
| TYPE-11 source frames can narrow edit invalidation for project files | runtime | DR-04 — **refuted**: 0 of 106 builds qualify after an edit |
| The 5 s cutoff bounds a build | runtime | DR-08 — **refuted**: it is checked between passes, and fallo's and `compile.lua`'s first pass runs past 45 s |
| A pair budget can replace the cutoff without changing results | runtime | DR-08 — **refuted**: pairs do not order builds by time |
| The checker's pair loop cannot be cancelled | runtime | DR-11 — **refuted**: PSI access inside it polls; a cancelled fallo build stops in 1–134 ms |
| The listed workloads give equal counts across two passes | runtime | DR-10 (probe harness: only after a prime), DR-12 (records harness: equal without one) |
| The prime is still needed under the records harness, which resets the workspace between passes | runtime | DR-12 — **refuted**: the workloads are equal across passes without it |
| The targets of MAINT-60-01/-06 are reachable with targeted changes | runtime | DR-05, DR-06 — **refuted** |

Constraints treated as chosen, not fixed: the sweep's selection and order are MAINT-56 DR-16's, so its
numbers compare with DR-16's; the after-edit edit is one appended `"\n"`, the smallest edit that bumps
`MODIFICATION_COUNT`; deadlines cap what an over-target file costs the run and never decide a verdict;
a member stops at its first cold timeout, as the DR-06 and DR-09 probes did, which bounds the run
without depending on whether a cancelled build is cached.

## Executed De-risking

All runs: 2026-11-10, libvirt builder, `53cfe4a9` plus throwaway probe code (since deleted), GoLand
2026.2.3 test platform, `builder-run libvirt "test --rerun --no-build-cache --tests '<probe>'"`. The
probe switched candidate changes on by flags:

| Flag | Change |
|---|---|
| `hash` | `LuaGraphType.Table.hashCode` over `className`, member count, supertype count, `isExact` |
| `hoist` | `checkNodePairs` reads each use node's `read` and `declaredDemand` once per visit |
| `flatWrite` / `flatRead` | `VariableElement.resolveWrite` / `resolveRead` skip `VariableElement`s in the closed set |
| `flatDemand` | `resolveDeclaredDemand` = any non-variable `UseNode` in `downSet` with `declaredDemand` |
| `delta` | per variable, remember `upSet`/`downSet` sizes at the last visit; for a use node below the old `downSet` size start the value loop at the old `upSet` size |
| `idmemo` | `CompatKey` equality by identity |

Every probe run except DR-11's without-poll runs also called `ProgressManager.checkCanceled()` once per use node in
`checkNodePairs`. DR-11 shows the call changes only cancellation latency, and DR-06 shows `base`'s
findings equal MAINT-56 DR-16's, taken without it, apart from DR-07's order-dependent files; so
`base` stands for `53cfe4a9` as it is.

### DR-01 — Leave-one-out on fallo and luals (executed)
**How:** `ProbeMaint60Test`; fallo `init.lua` read cold as a fixture file; luals `script/vm` copied with
the checkout's `script` on the source path; `compiler.lua` cold, then edits; 90 s deadline.

| flags | fallo cold | compiler cold | compiler after edit | library.lua after edit | errors (compiler) |
|---|---|---|---|---|---|
| none | > 90 000 (cancelled; 4.76 M `resolveWrite` roots, 13 passes) | 67 899 (116 builds) | 69 884 | 74 160 | 702 |
| hash hoist flat | 29 024 | 21 771 | 20 763 | 20 176 | 688 |
| hoist flat | 25 461 | 20 218 | 19 046 | 17 916 | 688 |
| hash flat | 27 581 | 22 223 | 25 656 | 26 033 | 688 |
| hash hoist | > 90 000 | 65 671 | 66 839 | 72 634 | 702 |
| hash | > 90 000 | 66 851 | 66 214 | 75 636 | 702 |

(`flat` = `flatWrite flatRead`.) **Observed:** the flat walks are the lever — `resolveWrite` calls
4.76 M → 121 629 on fallo; `hash` and `hoist` are within noise. Of the compiler read's 67 899 ms at `none`,
51 199 ms is the self time of `test/corpus/luals/script/parser/compile.lua`, reached through the
source path (10 941 ms under `flat`). An edit to any `script/vm` file rebuilt 106–109 snapshots.
**Verdict:** the `hash` premise is refuted; the walks are the cost.

### DR-02 — Profile and the delta loop (executed)
**How:** as DR-01 with `-PjfrProfile`. **Observed:** `delta` alone: luals timed out at 75 s.
`delta hash hoist flatWrite`: compiler 58 727 ms, 702 errors; with `flatRead` instead: 38 406 ms,
696 errors; both flat walks: 25 958 ms, 696 errors; fallo 34 929 ms, 886 errors, cutoff hit. JFR,
self time inside Lunar on the both-flat run: `checkNodePairs` 23.8 %, `Table.equals` via `CompatKey`
15.6 %, `resolveDeclaredDemand` 11.3 %, `OrderedSet.add` 8.4 %, `getMembers` 4.5 %; allocation:
`LinkedHashMap$LinkedEntryIterator` 31 %, `Pair` 12.5 %. fallo admitted 1.34 M pairs, about 680 K with
a variable as the value and 670 K with a variable as the use. **Verdict:** after the walks, the pair
loop itself and the compatibility memo's structural keys are the cost.

### DR-03 — Are the cheap findings checks sensitive? (executed)
**How:** `ProbeMaint60FindingsTest` ran MAINT-54's `AuditMemberRun(myFixture, AuditRunMode.FALSE_NEGATIVE).run("luacheck")`
under `base`, `delta idmemo flatWrite flatDemand flatRead`, the same without `flatRead`, and `delta`;
`LuaCatsConformanceTest` under the full set. **Observed:** `luacheck.json` byte-identical in all four
(SHA-256 prefix `c8a255ebc0eebb13`); audit time 48 348 ms at `base`, 25 901 ms under the full set.
`build/conformance/results.json` identical to the committed baseline under the full set.
**Verdict:** both stay as MAINT-60-03's gate, but they do not detect the changes DR-06 does; a
findings claim about an engine change needs the sweep's per-file digests.

### DR-04 — TYPE-11 frames as edit dependencies (executed)
**How:** `LuaTypesSnapshot.dependenciesFor` replaced, when a frame had no absences, unreplayed warm
hits, in-progress hits or rescued globals, by `[psiFile, every frame URL's PsiFile, targetModificationTracker]`;
luals as DR-01. **Observed:** cold read: 11 of 116 builds qualified (rejected: absences 90,
in-progress hits 15). After an edit: 0 of 106. **Verdict:** refuted; edit invalidation needs a
different dependency (MAINT-63).

### DR-05 — After-edit sweep under the fastest configuration (executed)
**How:** the sweep (DR-06's setup) with `delta idmemo flatWrite flatDemand flatRead`, each file read
cold, then `"\n"` appended and read again, interleaved; 60 s deadline. **Observed** (ms, 2 026 files):
after edit median 34, p90 1 352, p99 11 679, max 23 467; **509 files ≥ 250**. The edited file's own
build alone: median 12, p99 391, max 23 454; 40 files ≥ 250 (fallo 23 454, druid
`druid_instance.lua` 3 439, `druid.lua` 2 795, opensmce `SphereGroup.lua` 1 112, luals `compiler.lua`
1 069). Reads took 4.9 min cold + 16.6 min after edit. Per member:

| member | files | over 250 after edit | of which own build | max edit ms |
|---|---|---|---|---|
| factoryplanner | 124 | 83 | 2 | 3 907 |
| blinkcmp | 121 | 75 | 0 | 1 582 |
| telescope | 73 | 56 | 2 | 2 999 |
| gitsigns | 61 | 55 | 0 | 2 855 |
| tntcrud | 78 | 51 | 0 | 1 160 |
| differ | 79 | 40 | 2 | 3 332 |
| druid | 45 | 27 | 17 | 13 425 |
| awesome | 40 | 22 | 0 | 1 951 |
| luals | 21 | 21 | 2 | 14 784 |
| opensmce | 126 | 18 | 5 | 4 025 |
| tntmetrics | 42 | 16 | 0 | 350 |
| snabb | 14 | 13 | 0 | 1 846 |
| tntqueue | 14 | 10 | 0 | 359 |
| prism | 85 | 6 | 4 | 884 |
| ljtk | 16 | 3 | 1 | 639 |
| lde | 84 | 3 | 1 | 586 |
| kristal | 50 | 3 | 3 | 302 |
| restyhttp | 3 | 2 | 0 | 520 |
| kong | 260 | 2 | 0 | 334 |
| apisix | 50 | 2 | 0 | 417 |
| fallo | 1 | 1 | 1 | 23 467 |
| weakauras | 17 | 0 | 0 | 174 |
| somewm | 56 | 0 | 0 | 105 |
| rampant | 39 | 0 | 0 | 192 |
| pokisample | 68 | 0 | 0 | 205 |
| ouf | 33 | 0 | 0 | 37 |
| omniinv | 27 | 0 | 0 | 85 |
| monarch | 5 | 0 | 0 | 136 |
| lnko | 10 | 0 | 0 | 142 |
| ldoc | 31 | 0 | 0 | 163 |
| lain | 31 | 0 | 0 | 99 |
| inngest | 66 | 0 | 0 | 68 |
| facc | 115 | 0 | 0 | 213 |
| bullmq | 136 | 0 | 0 | 38 |
| beequeue | 5 | 0 | 0 | 15 |

**Verdict:** even this configuration, which changes findings (DR-06), misses MAINT-60-06 on 509
files, 469 of them through the rebuild of what they reach rather than their own build.

### DR-06 — Cold sweep per configuration, with per-file findings digests (executed)
**How:** `ProbeMaint60SweepTest`, MAINT-56 DR-16's setup (every type-corpus member: target pinned,
definition libraries provisioned, `moduleRoot` on the source path, roots copied, files read in path
order, once, cold), 60 s deadline, luals and fallo first, a member abandoned after a cold timeout. At
`base` that reads 2 006 of DR-16's 2 026 selected files: luals's other 20 follow `compiler.lua`'s
timeout. DR-05's configuration reads all 2 026, because `compiler.lua` finishes. Per file: time, the
read's builds and the root build's self time, and a digest of the sorted `offset:severity:message`
error strings. Findings are compared per file over rows read `ok` without a cutoff trip.

| config | files ≥ 250 | ≥ 100 | ≥ 1 000 | p99 | summed reads | fallo / compiler | files whose digest differs from `base` |
|---|---|---|---|---|---|---|---|
| MAINT-56 DR-16 (no `checkCanceled`) | 72 | 136 | 18 | 866 | 4.75 min | 121 987 / 48 675 | 2 |
| `base` | 77 | 141 | 22 | 1 036 | 4.18 min | 60 047 / 60 002, both cancelled | — |
| `delta` | 72 | 142 | 20 | 859 | 3.92 min | cancelled / cancelled | 3 |
| `delta flatDemand` | 73 | 126 | 19 | 923 | 3.90 min | cancelled / cancelled | 2 |
| `delta idmemo flatDemand` | 69 | 129 | 17 | 814 | 3.83 min | cancelled / 59 348 | 10 |
| `+ flatWrite` | 70 | 127 | 19 | 856 | 3.79 min | cancelled / 58 988 | 17 |
| `+ flatRead` (all) | 67 | 136 | 16 | 825 | 2.45 min | 27 782 / 17 100 | 81 |

Summed reads outside fallo and luals: `base` 130 643 ms, `delta` 115 411, `delta flatDemand` 113 835.
The 2 files that differ for DR-16 and for `delta flatDemand` are blinkcmp `completion/list.lua` and
`fuzzy/init.lua`; `delta` adds awesome `naughty/layout/legacy.lua`. All three were already built when
read (DR-07). Cancellation: cancelled reads ended 2–207 ms after the 60 s deadline in every
configuration; DR-16's, without the per-use-node call, ended 1 987 ms after its 120 s deadline.
Under `base`, 43 of the 77 files at or over 250 ms have an own build at or over 250 ms. Under `all`,
32 of 67 do.
**Verdict:** no configuration reaches MAINT-60-01. The exact ones move the distribution by less than
the run-to-run difference between DR-16 and `base`. The files at or over 250 ms after the chosen
change (`base`) are listed below.

### DR-07 — Findings of a file built as a dependency depend on read order (executed)
**How:** the digest differences of DR-06 cross-checked against the `prebuilt` flag; DR-05's luals
read. **Observed:** every digest difference that is not a walk flattening's is in a file whose
snapshot was built while another file was being read. luals `compiler.lua` read as the root gave 704
errors and 351 when it was built nested. **Verdict:** compare digests over files not already built
when read; the sweep's `prebuilt` column exists for this.

### DR-08 — Each build's checker: pairs, passes, time (executed)
**How:** DR-06's sweep under `delta flatDemand`, recording for every `checkTypes` call
`checkedPairs.size`, the iteration count, its wall time and whether the 5 s limit fired; 3 052 builds.
**Observed:** no build fired the 5 s limit. Iterations: 1 for 104 builds, 2 for 2 432, 3 for 516; no
completed build needed more than 3. The builds over 1 s:

| checkTypes ms | pairs | iterations | file |
|---|---|---|---|
| 59 692 (cancelled in pass 1) | 970 215 | 1 | fallo `lua/fallo/init.lua` |
| 45 224 (cancelled in pass 1) | 354 039 | 1 | luals `script/parser/compile.lua` |
| 3 887 | 94 596 | 3 | luals `script/vm/compiler.lua` |
| 3 344 | 602 898 | 3 | druid `druid/system/druid_instance.lua` |
| 2 865 | 94 690 | 3 | opensmce `src/Game/SphereGroup.lua` |
| 2 380 | 52 753 | 2 | prism `engine/math/color.lua` |
| 1 732 | 42 953 | 3 | prism `spectrum/display.lua` |
| 1 545 | 43 957 | 2 | ljtk `lang/parser.lua` |
| 1 283 | 453 179 | 2 | druid `druid/druid.lua` |

41 builds spent ≥ 250 ms in `checkTypes`; 106 builds had ≥ 250 ms of self time, so for 65 of them
the cost is outside the checker (the visitor's own inference). **Verdict:** the cutoff cannot bound a
build, because a single pass outlasts it; pairs per millisecond vary by more than 100× between
builds, so no pair budget reproduces today's results.

### DR-09 — After-edit sweep at `base` (executed)
**How:** DR-06's sweep at `base`; per file, the cold read, then `"\n"` appended and committed and the
file read again under a 10 s deadline, interleaved; run budget 28 min. **Observed:** every member
reached in 26 min 48 s of build; luals was read only for `compiler.lua`, because a member is abandoned
after a cold timeout (as in design §3.1). After edit, over the 2 002 files read `ok` (DR-06's 2 006
less fallo, luals `compiler.lua` and druid's two edit timeouts): median 37 ms, p90 1 854, p99 3 900, max 6 823. **524 files at or over 250 ms** (or
cancelled): 50 of them with an own build at or over 250 ms (fallo 59 905, opensmce
`SphereGroup.lua` 2 806, ljtk `lang/parser.lua` 2 318, druid `druid_instance.lua` 1 400, prism
`engine/math/color.lua` 1 386), 474 through the rebuild of what they reach. Summed after-edit reads:
936 s; capped at 1 s per file, 482 s. Members with any file over:

| member | files | edit status ok | over 250 after edit | of which own build | max edit ms |
|---|---|---|---|---|---|
| luals | 1 | 0 | 1 | 0 | 60006 |
| fallo | 1 | 0 | 1 | 1 | 60000 |
| telescope | 73 | 73 | 56 | 2 | 3275 |
| gitsigns | 61 | 61 | 55 | 1 | 2882 |
| blinkcmp | 121 | 121 | 74 | 0 | 2262 |
| differ | 79 | 79 | 40 | 1 | 3348 |
| weakauras | 17 | 17 | 1 | 0 | 308 |
| kristal | 50 | 50 | 4 | 4 | 375 |
| prism | 85 | 85 | 5 | 4 | 1429 |
| opensmce | 126 | 126 | 25 | 9 | 6823 |
| factoryplanner | 124 | 124 | 83 | 4 | 4979 |
| awesome | 40 | 40 | 23 | 0 | 2860 |
| kong | 260 | 260 | 5 | 0 | 520 |
| apisix | 50 | 50 | 8 | 0 | 611 |
| restyhttp | 3 | 3 | 2 | 0 | 528 |
| tntmetrics | 42 | 42 | 28 | 0 | 524 |
| tntcrud | 78 | 78 | 52 | 0 | 878 |
| tntqueue | 14 | 14 | 3 | 0 | 302 |
| druid | 45 | 43 | 27 | 19 | 10018 |
| ldoc | 31 | 31 | 1 | 1 | 461 |
| lde | 84 | 84 | 14 | 1 | 789 |
| snabb | 14 | 14 | 13 | 2 | 2809 |
| ljtk | 16 | 16 | 3 | 1 | 2354 |

The other 12 members had no file at or over 250 ms after an edit. **Verdict:** MAINT-60-06 is missed
on at least 524 files plus luals's other 20 (DR-05: all 21 over); 90 % of the misses are the edit
cascade (MAINT-63). A 1 s after-edit deadline keeps the sweep's edit pass near 8 min.

### DR-10 — The workloads' counts across two passes (executed)
**How:** `ProbeMaint60Tc7Test`, light fixture, member target pinned, each file's text added as a
project file with no source path, read twice in one project; root counts are
`rootResolutionCount(WRITE / READ / DECLARED_DEMAND)`. Run once reading each pass directly, once with
a prime (the same text read from another file first) before each pass. **Observed:**

| file | pass 1, unprimed | pass 2, unprimed | pass 1, primed | pass 2, primed | read ms | `checkTypes` ms (all builds of the read) |
|---|---|---|---|---|---|---|
| luals `script/vm/compiler.lua` | 9 984 / 3 673 / 1 875 | 9 650 / 3 637 / 1 875 | 9 650 / 3 637 / 1 875 | 9 650 / 3 637 / 1 875 | 2 287–4 325 | 1 294–2 094 |
| ldoc `ldoc/markdown.lua` | 4 486 / 1 864 / 1 530 | 4 483 / 1 863 / 1 530 | 4 483 / 1 863 / 1 530 | 4 483 / 1 863 / 1 530 | 525–722 | 295–407 |
| beequeue `lib/lua/raiseDelayedJobs.lua` | 25 / 8 / 8 | 25 / 8 / 8 | 25 / 8 / 8 | 25 / 8 / 8 | 25–101 | 0 |

Errors: 148, 16, 0 in every pass; the 5 s cutoff never fired. **Verdict:** counts are equal across
passes only when the library snapshots the file reaches are already built, so a probe reading each pass in
a fresh project primes (the records harness needs none, DR-12). The largest build is 2.4× under the cutoff.

### DR-12 — Equal passes under the records harness without a prime (executed)
**How:** Phase 2, `LuaPerfRecordWorkloadsTest.recordEveryWorkload`, libvirt builder,
`test --rerun --no-build-cache -PwithPerfRecords --tests '*PerfRecord*'`: once with the prime line (a
second file of the same text read first), once with it deleted. Then, with the prime deleted, the
`recorded` list handed to `passFailures` had `infer-luals-compiler`'s `write` raised by 1.
**Observed:** both runs passed. Primed counts (demand / read / write) were luals 1 875 / 3 637 / 9 650,
ldoc 1 530 / 1 863 / 4 483, beequeue 8 / 8 / 25, equal in both passes. Unprimed, luals read 3 673 and
wrote 9 984 in **both** passes (DR-10's unprimed pass-1 figure, 9 984), ldoc and beequeue unchanged. The
perturbation failed with `infer-luals-compiler: counts differ across passes: write 9984 -> 9985`.
Priming through `myFixture.addFileToProject` also failed the second pass (`File prime.lua already exists
in /root`: the harness's reset does not clear the fixture's temp root). **Verdict:** the harness's own
warm pass is the prime; the counts are equal across passes without one, though a primed file records
different counts (luals `write` 9 650 against 9 984), so the recorded numbers are those of the
library-cold state. The prime is removed from design §2.3 and the code; the equal-passes wiring is proven
by the perturbation.

### DR-11 — Cancellation latency with and without a poll in the pair loop (executed)
**How:** `ProbeMaint60Tc7Test`, fallo `init.lua`'s text alone, read under an `EmptyProgressIndicator`
cancelled by a scheduled task at 3, 8 and 15 s; once with `ProgressManager.checkCanceled()` per use
node in `checkNodePairs`, once without. Control: `annotatedCalls(160)` and `(640)` read on a pooled
thread, cancelled when a stack sample first shows `checkNodePairs`. **Observed:** with the poll the
read ended 7, 26 and 1 ms after the cancel, the exception's direct caller being
`LuaTypeGraph.checkNodePairs`; without it, 1, 134 and 6 ms, the caller being
`ProgressIndicatorProvider.checkCanceled` from `CompositeElement.getPsi`. The control behaved the same
way (0–3 ms with, 2–6 ms without). An indicator cancelled before `runProcess` starts is reset by
`start()` and cancels nothing. **Verdict:** the premise that the pair loop cannot be cancelled is
refuted; PSI access inside it already polls. MAINT-56 DR-16's 1 987 ms overrun is not explained by
this and is not established.

## Files Over Target After This Feature

This feature changes nothing the engine computes, so DR-06's `base` is the state after it.

**Cold, at or over 250 ms (MAINT-60-01): 77 files**, DR-06 `base`. *own ms* is the root build's self
time; *cause* is own build (own ms ≥ 250) or reach; the item each lands in follows design §3.3 from
the sweep's dependency columns, which these probes did not record. luals's other 20 `script/vm` files
were not read at `base` (DR-06); the sweep records them as unmeasured under `compiler.lua`'s item.

| member | file | cold ms | own ms | builds | cause |
|---|---|---|---|---|---|
| fallo | `lua/fallo/init.lua` | 60 047 (cancelled) | 59 982 | 7 | own build |
| luals | `script/vm/compiler.lua` | 60 002 (cancelled) | 205 | 111 | reach |
| druid | `druid/druid.lua` | 11 813 | 2 910 | 24 | own build |
| druid | `druid/system/druid_instance.lua` | 9 901 | 4 030 | 17 | own build |
| gitsigns | `lua/gitsigns.lua` | 6 789 | 198 | 125 | reach |
| differ | `lua/differ/command.lua` | 6 374 | 129 | 85 | reach |
| factoryplanner | `modfiles/backend/calculation/LUDecomposition.lua` | 5 683 | 493 | 101 | own build |
| opensmce | `src/Game/DummyLevel.lua` | 5 452 | 40 | 8 | reach |
| telescope | `lua/telescope/actions/generate.lua` | 4 883 | 92 | 78 | reach |
| blinkcmp | `lua/blink-cmp.lua` | 3 545 | 25 | 91 | reach |
| awesome | `lib/gears/color.lua` | 3 416 | 29 | 115 | reach |
| snabb | `src/lib/yang/alarms.lua` | 3 388 | 207 | 71 | reach |
| ljtk | `lang/bcsave.lua` | 2 597 | 75 | 16 | reach |
| opensmce | `src/Game/Game.lua` | 2 052 | 207 | 10 | reach |
| prism | `engine/math/color.lua` | 1 998 | 1 996 | 2 | own build |
| apisix | `apisix/core/config_etcd.lua` | 1 534 | 141 | 28 | reach |
| prism | `spectrum/display.lua` | 1 351 | 1 341 | 3 | own build |
| restyhttp | `lib/resty/http.lua` | 1 310 | 138 | 33 | reach |
| opensmce | `src/BootScreen/BootScreen.lua` | 1 183 | 35 | 17 | reach |
| druid | `druid/base/back_handler.lua` | 1 154 | 288 | 8 | own build |
| opensmce | `src/Debug.lua` | 1 046 | 194 | 8 | reach |
| druid | `druid/extended/container.lua` | 1 036 | 649 | 2 | own build |
| druid | `druid/base/scroll.lua` | 884 | 878 | 2 | own build |
| opensmce | `src/GameBase.lua` | 870 | 203 | 22 | reach |
| telescope | `lua/telescope/builtin/__lsp.lua` | 834 | 188 | 8 | reach |
| differ | `lua/differ/view.lua` | 786 | 752 | 6 | own build |
| druid | `druid/custom/rich_input/rich_input.lua` | 761 | 761 | 1 | own build |
| druid | `druid/extended/input.lua` | 754 | 546 | 2 | own build |
| ldoc | `ldoc/markdown.lua` | 690 | 690 | 1 | own build |
| prism | `engine/math/vector.lua` | 683 | 683 | 1 | own build |
| telescope | `lua/telescope/builtin/__diagnostics.lua` | 655 | 173 | 13 | reach |
| tntqueue | `queue/abstract.lua` | 597 | 294 | 14 | own build |
| tntcrud | `crud/atomic_batch.lua` | 590 | 15 | 31 | reach |
| differ | `lua/differ/merge/init.lua` | 588 | 421 | 5 | own build |
| weakauras | `WeakAuras/RegionTypes/AuraBar.lua` | 587 | 452 | 7 | own build |
| differ | `lua/differ/git/operation.lua` | 584 | 388 | 5 | own build |
| druid | `druid/color.lua` | 580 | 59 | 3 | reach |
| differ | `lua/differ/git/staging/frozen.lua` | 577 | 88 | 9 | reach |
| telescope | `lua/telescope/builtin/__internal.lua` | 568 | 434 | 2 | own build |
| telescope | `lua/telescope/builtin/__files.lua` | 563 | 173 | 6 | reach |
| tntmetrics | `metrics/api.lua` | 555 | 59 | 39 | reach |
| prism | `engine/core/level.lua` | 517 | 516 | 2 | own build |
| kristal | `src/engine/game/battle/arena.lua` | 511 | 171 | 7 | reach |
| kong | `kong/plugins/jwt/handler.lua` | 508 | 22 | 4 | reach |
| opensmce | `src/Configs/utils.lua` | 501 | 447 | 4 | own build |
| druid | `druid/base/text.lua` | 492 | 371 | 3 | own build |
| kong | `kong/clustering/init.lua` | 473 | 9 | 15 | reach |
| prism | `engine/algorithms/astar/astar.lua` | 448 | 272 | 11 | own build |
| druid | `druid/extended/slider.lua` | 440 | 439 | 1 | own build |
| druid | `druid/extended/timer.lua` | 439 | 439 | 1 | own build |
| druid | `druid/extended/layout.lua` | 426 | 426 | 1 | own build |
| pokisample | `game/modules/bootstrap/entry.lua` | 419 | 68 | 31 | reach |
| monarch | `monarch/monarch.lua` | 407 | 361 | 8 | own build |
| lde | `packages/lde-core/tests/build.test.lua` | 407 | 407 | 1 | own build |
| lde | `packages/lde-core/src/lockfile.lua` | 405 | 39 | 2 | reach |
| druid | `druid/extended/data_list.lua` | 384 | 384 | 1 | own build |
| lnko | `lnko/cli.lua` | 384 | 160 | 18 | reach |
| opensmce | `src/Network.lua` | 379 | 35 | 2 | reach |
| tntcrud | `crud/borders.lua` | 371 | 14 | 7 | reach |
| druid | `druid/base/button.lua` | 362 | 362 | 1 | own build |
| druid | `druid/base/static_grid.lua` | 356 | 356 | 1 | own build |
| telescope | `lua/telescope/actions/init.lua` | 354 | 349 | 2 | own build |
| kristal | `src/engine/game/battle/battlecutscene.lua` | 349 | 349 | 1 | own build |
| rampant | `prototypes/EnergyThief.lua` | 348 | 343 | 2 | own build |
| kristal | `src/engine/game/common/data/partymember.lua` | 347 | 339 | 2 | own build |
| kristal | `src/engine/game/common/inventory.lua` | 345 | 345 | 1 | own build |
| tntmetrics | `metrics/plugins/json.lua` | 312 | 36 | 2 | reach |
| kristal | `src/engine/game/battle/enemybattler.lua` | 306 | 300 | 2 | own build |
| kong | `kong/clustering/compat/checkers.lua` | 305 | 46 | 2 | reach |
| prism | `spectrum/camera.lua` | 302 | 96 | 2 | reach |
| prism | `spectrum/gamestates/levelstate.lua` | 302 | 302 | 1 | own build |
| lde | `packages/lde-core/src/compiler.lua` | 298 | 26 | 20 | reach |
| prism | `engine/core/system.lua` | 290 | 290 | 1 | own build |
| kristal | `src/engine/game/common/actorsprite.lua` | 284 | 283 | 1 | own build |
| kristal | `src/engine/game/common/data/item.lua` | 257 | 257 | 1 | own build |
| kristal | `src/engine/game/battle/ui/tensionbar.lua` | 252 | 252 | 1 | own build |
| druid | `druid/custom/rich_text/rich_text.lua` | 251 | 251 | 1 | own build |

**Cold p99 (MAINT-60-01).** Over the 2 026 selected files the nearest-rank p99 is under 100 ms only
when at most 20 files are at or over 100 ms or unmeasured. At `base`, 141 read files are at or over
100 ms (DR-06) and luals's 20 are unmeasured, so the cold p99 line fails; the 100–249 ms files carry no
exception and pass the per-file bound.

**After an edit, at or over 250 ms (MAINT-60-06): 524 files** at `base`, plus luals's 20 files
DR-09 did not read; per member in DR-09's table. 50 have an own build at or over 250 ms; the rest are
the edit cascade. The per-file list is the Phase 3 sweep's `ranked.tsv`, which the items carry. With
524 files over 250 ms, the after-edit p99 line fails as well.

## Risks

### Risk 1.1: Near-threshold files flip between runs
- **Impact**: the verdict reports a file at 240–260 ms as a new violation on one run and not the next
  (DR-06: DR-16 and `base` differ by 5 files at 250 ms on the same commit's engine).
- **Likelihood**: high
- **Mitigation**: a listing is stale only under 200 ms (design §3.2), so a listed pass that drops
  into 200–249 ms does not flip to a stale-exception failure. Phase 3 lists only passes that miss, so
  an unlisted pass that crosses 250 ms on a later run is a real miss of the target: it fails as
  `no exception` and is listed then under `attribute`'s item, whose roadmap row already exists.

### Risk 1.2: A workload's build near the 5 s cutoff
- **Impact**: the cutoff is wall-clock (DR-08); a runner slow enough to trip it on one pass and not
  the other records different counts.
- **Likelihood**: low: every workload build is well under the cutoff (DR-10).
- **Mitigation**: none is wanted: equal root counts are a workload-correctness check, not a
  performance wobble (design §2.3). A disagreement fails `recordEveryWorkload` in every gate mode with
  the `passDisagreements` message naming the workload and the key; no record is written and no
  `WOBBLE` line is printed, and the job goes red in its final step.

### Risk 1.3: The sweep's budget cuts members
- **Impact**: skipped files count as over both targets with no measured time.
- **Likelihood**: low: the expected run is well inside the budget (design §3.4).
- **Mitigation**: luals and fallo run first; skipped rows are filed under the item of the run's
  slowest read (design §3.3), and the run's length is asserted (MAINT-60-05).

### Risk 1.4: CI time for the workloads
- **Impact**: two passes, each a measured read, at DR-10's read times, plus fetching
  the type-corpus members named by `--only` (design §7).
- **Likelihood**: low.
- **Mitigation**: the workloads read the file's text only, so nothing else in the member is built.

## Design Gaps

### Gap 2.1: Does MAINT-60 close when its items are filed? (product)
- **Question**: MAINT-60-01 and -06 hold only once the cause items land.
- **Decision** (user, 2026-11-10): v0.21.21 is held until the targets are met (roadmap, v0.21.21
  goal 1). MAINT-60 closes when its phases' exit criteria hold, with -01 and -06 `Partial`, tracked by
  the cause items and gated by the release-ready signal (MAINT-60-07).

### Gap 2.2: Grouping exceptions by cause (product)
- **Question**: one item per missing file, or one per measured cause naming every file?
- **Decision** (supervisor, 2026-11-10): one item per cause (design §3.3).

### Gap 2.3: The findings-changing flattenings (product)
- **Question**: `flatWrite`/`flatRead`/`idmemo` halve fallo and luals but change findings (DR-06).
  Which side is right is not established.
- **Decision**: filed as MAINT-64 with DR-06's digests; not shipped here.

### Gap 2.4: fallo's workload (product)
- **Question**: fallo `init.lua`'s build does not finish within the sweep's deadline (DR-06), so two
  passes on every push would add minutes to CI.
- **Decision** (supervisor, 2026-11-10): its workload is added by [[BUG-96]]'s fix (MAINT-60-04).

## Technical Debt & Future Work
- **TBD: delta pair iteration and the flat declared-demand walk** — exact and measured (DR-06), 12–13 %
  less summed cold time outside fallo and luals; left for MAINT-61, whose redesign replaces the loop
  they optimise.
- **TBD: the 5 s cutoff** — it bounds nothing (DR-08). Replacing it is part of MAINT-61.

## Test Case Gaps
- MAINT-60-03 is a comparison of artifacts, with no mutation of its own: the gate is that the
  compared files differ when findings change, and DR-03 shows `luacheck.json` and `results.json` miss
  changes the sweep's digests catch. The digest column, compared over files read `ok` and not already
  built, is the stronger check; Phase 1 runs it before and after its `src/main` change
  (implementation-plan Phase 1).

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
