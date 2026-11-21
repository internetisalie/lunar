---
id: "BUG-97"
title: "Passes that depend on luals script/parser/compile.lua are over 250 ms because its own build takes up to 43 seconds"
type: "bug"
parent_id: "BUG"
status: "in_progress"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-97: Passes that depend on luals `script/parser/compile.lua` are over 250 ms because its own build takes up to 43 seconds

## Scope (MAINT-60 Phase 3)
MAINT-60's sweep (design 3.3, `attribute`) files this bug for one cause: **the own build of luals
`script/parser/compile.lua`**, the dominant dependency of the `script/vm` files (DR-01, DR-08: 354 039
checked pairs, cancelled in pass 1). A pass is filed here when its own build is under 250 ms and its
dominant dependency is that file with 250 ms or more of self time. That file's own time was 43 573 ms
in the first sweep and 1 440 ms in the second, where it was the edit-pass dependency of `script/vm/init.lua`
(filed here by that run). Across the three sweeps no pass is filed here by the majority rule, so
the table is empty; luals `script/vm/compiler.lua` (own self time 297 ms in the first sweep, at the
250 ms boundary) and the other `script/vm` files are filed under MAINT-61 and MAINT-63. A fix to
`compile.lua` moves the `script/vm` passes with it. Fixing it must keep MAINT-60-03: audit,
conformance and sweep digests unchanged or each difference recorded.

### Files over target
Generated from the union of three MAINT-60 Phase 3 sweeps (libvirt builder, commit `9fb91435`). A pass
is here when it missed in at least one run and the item most of those runs filed it under is this
one (on a tie, the second run's). *Value* is the largest time measured, or the status when a run did
not complete the pass.

No pass is filed under this item by the rule above.

Found by MAINT-56's representative run. Its `luals` member had been running for 149 minutes without
finishing when the run was stopped. The same run measured gitsigns, 61 files, in 23.6 minutes.

## 1. Reproduction
The corpus member `luals` (`tooling/corpus/type-corpus.json`): `LuaLS/lua-language-server` at commit
`a58fc7b01e1746c46275c71b0e61bfed40a6296d`, fetched into `test/corpus/luals` by
`python3 tooling/corpus/fetch-corpus.py --manifest tooling/corpus/type-corpus.json`. Root `script/vm`
(21 files, 10 497 lines), `moduleRoot` `script` (source path `<checkout>/script/?.lua;<checkout>/script/?/init.lua`),
target Standard Lua 5.4, no definition libraries.

In a `BasePlatformTestCase` (light fixture, builder `libvirt`, GoLand 2026.2.3 test platform):
1. Pin the target and source path as above, and copy `test/corpus/luals/script/vm` into the fixture
   with `myFixture.copyDirectoryToProject`.
2. Visit the files smallest first. For each one, time `LuaTypesSnapshot.forFile(file).getErrors()`.
   Then append `"\n"` to the file's document in a `WriteCommandAction`, commit it, and time the same
   call again.

## 2. Expected vs Actual Behavior
- **Expected**: the snapshot of a 15-line file after appending a newline costs about what the same
  file costs on its own: milliseconds to seconds.
- **Actual** (one run):

| file | lines | first read (ms) | after the edit (ms) |
|---|---|---|---|
| `library.lua` | 15 | 68 329 | 65 497 |
| `init.lua` | 25 | 0 | 68 277 |
| `precompile.lua` | 20 | 0 | 58 147 |
| `field.lua` | 59 | 0 | 55 472 |
| `node.lua` | 537 | 0 | 56 837 |
| `type.lua` | 930 | 0 | 53 916 |
| `compiler.lua` | 2 807 | 0 | 65 369 |

  All 21 files cost 53–72 s after an edit, whatever their size. The first file read took 68 s. Each
  later first read took 0 ms, so the first read had already produced every file's snapshot. An edit
  to one file therefore costs what analysing the whole copied directory costs.
- In MAINT-54's audit harness (`AuditInjector`), every measured site edits a file and reads its
  snapshot up to three times, so each `luals` site costs about three minutes.
- EDT stack while `luals` ran in the representative run (two jstacks, 20 s apart): deep recursive
  `VariableElement.resolveRead`/`resolveWrite` chains (`LuaTypeNodes.kt:206-245`) and
  `LuaGraphType$Table.hashCode`, under `AuditInjector.measureOpened` → `apply` → `keys`
  (`AuditInjector.kt:92,144,161,212`).

## 3. Related
[[BUG-96]]: the same stack frames, inside one file. MAINT-56's `results.md` records `luals` as timed
out, so its rule weights are to be re-measured after this bug is fixed.

## 4. Profile
Measured 2026-11-10 on `d64890df`, builder `libvirt`, GoLand 2026.2.3 test platform, test JVM JBR
25.0.4. A throwaway light-fixture test, not committed, followed §1's setup: Standard Lua 5.4 pinned with
`CorpusTarget.pin`, the source path `<checkout>/script/?.lua;<checkout>/script/?/init.lua`, no definition
libraries, and `test/corpus/luals/script/vm` copied with `myFixture.copyDirectoryToProject`. On the EDT,
it timed one `LuaTypesSnapshot.forFile(file).getErrors()` on `library.lua` (cold). It then appended
`"\n"` to that file's document in a `WriteCommandAction`, committed it, and timed the call again (edit).
Run: `builder-run libvirt "test --rerun --no-build-cache --tests '*ProbeBug97*' -PjfrProfile"`.
`-PjfrProfile` is the build's Java Flight Recorder switch: `settings=profile`, `stackdepth=1024`,
`DebugNonSafepoints`.

| phase | ms | errors | samples analysed |
|---|---|---|---|
| cold | 65 532 | 8 | 6 009 |
| edit | 59 524 | 8 | 5 537 |

**Samples.** The analysis counts JFR execution samples taken between the probe's BEGIN and END
timestamps, on any thread whose stack has a `net.internetisalie` frame. All are on `AWT-EventQueue-0`.
Mean stack depth is 738 (cold) and 736 (edit), and 1 127 and 1 052 stacks reached the 1 024-frame
cap, about 19 %. A capped stack loses its outermost frames, so the inclusive figures below are lower
bounds for frames near the root.

**Nested snapshot builds.** The number of `LuaTypesVisitor.buildSnapshot` frames on one stack is 7 in
56.5 % of cold samples and 61.8 % of edit samples, and it reaches 16. Every nested build is entered
through `LuaTypeManagerImpl.resolveModule` → `getModuleType`. The edit phase shows the same nesting
as the cold phase. Which files were rebuilt is not recorded, because frames do not identify files.

Top frames by self time (the frame on top of the stack):

| cold samples | cold % | edit samples | edit % | frame |
|---|---|---|---|---|
| 1 433 | 23.8 | 1 403 | 25.3 | `java.util.HashMap.putVal` |
| 396 | 6.6 | 434 | 7.8 | `java.util.LinkedHashMap$LinkedHashIterator.nextNode` |
| 292 | 4.9 | 258 | 4.7 | `kotlin.sequences.TransformingSequence$iterator$1.next` |
| 284 | 4.7 | 292 | 5.3 | a `VariableElement` lambda (`invoke`) |
| 249 | 4.1 | 248 | 4.5 | `java.util.HashSet.add` |
| 246 | 4.1 | 222 | 4.0 | `VariableElement.resolveWrite` |
| 246 | 4.1 | 242 | 4.4 | `kotlin.jvm.internal.Intrinsics.areEqual` |
| 192 | 3.2 | 162 | 2.9 | `java.util.LinkedHashMap.afterNodeAccess` |
| 179 | 3.0 | 172 | 3.1 | `java.util.LinkedHashMap$LinkedKeyIterator.next` |
| 177 | 2.9 | 158 | 2.9 | `java.util.HashMap.hash` |
| 145 | 2.4 | 156 | 2.8 | `VariableElement.resolveDeclaredDemand` |
| 118 | 2.0 | 108 | 2.0 | `VariableElement.resolveRead` |
| 114 | 1.9 | 99 | 1.8 | `VariableElement.writeWith` |
| 99 | 1.6 | 93 | 1.7 | `java.util.HashMap$Node.getKey` |
| 96 | 1.6 | 87 | 1.6 | `LuaGraphType$Any.equals` |

Self time attributed to the topmost `net.internetisalie` frame:

| cold samples | cold % | edit samples | edit % | frame |
|---|---|---|---|---|
| 1 544 | 25.7 | 1 472 | 26.6 | `VariableElement.resolveRead` |
| 970 | 16.1 | 907 | 16.4 | `VariableElement.resolveWrite` |
| 616 | 10.3 | 650 | 11.7 | `VariableElement.resolveDeclaredDemand` |
| 490 | 8.2 | 427 | 7.7 | `LuaTypeGraph.checkNodePairs` |
| 284 | 4.7 | 292 | 5.3 | a `VariableElement` lambda (`invoke`), first |
| 241 | 4.0 | 225 | 4.1 | `OrderedSet.add` |
| 208 | 3.5 | 216 | 3.9 | `VariableElement.resolveWrite$flatten` |
| 192 | 3.2 | 126 | 2.3 | `LuaTypesVisitor.buildSnapshot` |
| 159 | 2.6 | 144 | 2.6 | `VariableElement.writeWith` |
| 96 | 1.6 | 87 | 1.6 | `LuaGraphType$Any.equals` |
| 92 | 1.5 | 83 | 1.5 | `VariableElement.resolveRead$lambda$0` |
| 91 | 1.5 | 78 | 1.4 | `LuaGraphType$Undefined.equals` |
| 90 | 1.5 | 100 | 1.8 | a `VariableElement` lambda (`invoke`), second |
| 85 | 1.4 | 90 | 1.6 | `VariableElement.resolveRead$lambda$1` |
| 56 | 0.9 | 51 | 0.9 | `OrderedSet.iterator` |

Inclusive time (samples with the method anywhere on the stack, counted once per sample). The list
excludes the probe, `forFile`, the recorder, `buildSnapshot`, PSI `accept` and visitor dispatch, and
lambdas:

| cold samples | cold % | edit samples | edit % | frame |
|---|---|---|---|---|
| 5 787 | 96.3 | 5 351 | 96.6 | `LuaTypeManagerImpl.recordInto` |
| 5 783 | 96.2 | 5 351 | 96.6 | `LuaTypeManagerImpl.doResolveModule` |
| 5 782 | 96.2 | 5 349 | 96.6 | `LuaTypeManagerImpl.getModuleType` |
| 5 781 | 96.2 | 5 350 | 96.6 | `LuaTypeManagerImpl.resolveModule` |
| 5 559 | 92.5 | 5 148 | 93.0 | `LuaExpectedCallbackResolver.resolveCalleeType` |
| 5 559 | 92.5 | 5 150 | 93.0 | `LuaExpectedCallbackResolver.resolveStandardCalleeType` |
| 5 559 | 92.5 | 5 148 | 93.0 | `LuaTypesVisitor.propagateExpectedLambdaParams` |
| 5 556 | 92.5 | 5 149 | 93.0 | `LuaExpectedCallbackResolver.functionTypeOf` |
| 5 029 | 83.7 | 4 842 | 87.4 | `LuaTypeGraph.runFixedPointPass` |
| 5 026 | 83.6 | 4 838 | 87.4 | `LuaTypeGraph.checkTypes` |
| 5 024 | 83.6 | 4 835 | 87.3 | `LuaTypeGraph.checkNodePairs` |
| 3 194 | 53.2 | 3 173 | 57.3 | `LuaTypeGraph.checkAdmittedPair` |
| 2 216 | 36.9 | 2 141 | 38.7 | `VariableElement.resolveRead` |
| 2 045 | 34.0 | 2 001 | 36.1 | `VariableElement.getRead` |
| 1 502 | 25.0 | 1 416 | 25.6 | `VariableElement.getWrite` |

**Allocation**: `jdk.ObjectAllocationSample` in each window. Cold has 9 258 samples, an estimated
6 336 MB. Edit has 7 567 samples, an estimated 5 625 MB.

| class | cold % | edit % | | topmost Lunar frame | cold % | edit % |
|---|---|---|---|---|---|---|
| `LinkedHashMap$Entry` | 26.9 | 24.5 | | `LuaTypesVisitor.buildSnapshot:1518` (`elementNodes.toMap()`, `globalNodes.toMap()`) | 17.3 | 17.2 |
| `kotlin.Pair` | 16.4 | 17.5 | | `LuaTypeGraph.checkNodePairs:406` (`checkedPairs.add(Pair(valueNode, useNode))`) | 16.7 | 17.6 |
| `LinkedHashMap$LinkedKeyIterator` | 11.0 | 10.1 | | `VariableElement.resolveWrite$flatten:206` | 7.1 | 7.0 |
| `HashMap$Node[]` | 10.1 | 13.6 | | `OrderedSet.iterator:306` | 6.2 | 6.4 |
| `Object[]` | 7.0 | 6.1 | | `VariableElement.resolveRead:245` | 5.6 | 5.5 |

Over the whole recording, `jfr view hot-methods` ranks `HashMap.putVal` (20.4 %),
`LinkedHashIterator.nextNode` (5.9 %) and a `VariableElement` lambda (4.1 %) first. `jfr view
allocation-by-class` ranks `LinkedHashMap$Entry` (17.8 %), `kotlin.Pair` (11.7 %) and `byte[]` (9.0 %)
first.

Not established: call counts, because sampling does not give them. Also not established: which of
`script/vm`'s files each nested build analyses.

## 5. Root cause
Measured on `3899ca5b`, builder `libvirt`, GoLand 2026.2.3 test platform, with temporary counters in
the engine and a light-fixture probe: Standard Lua 5.4, the source path of §1, and one cold
`LuaTypesSnapshot.forFile(file).getErrors()` on the checkout's `script/parser/compile.lua` (5 164
lines), with `LuaSnapshotBuildMeter` attributing time to each build. On this commit the read costs
30.2–31.2 s. `compile.lua`'s own build is 30.0–30.8 s of it, and its eight dependencies (`tokens.lua`,
`guide.lua` and six standard-library stubs) take under 250 ms together. It reports 716 errors, the
same list in all three runs.

**This is not BUG-96's cause.** The file has no class conversions: `classTable` is never called, and
`fromLuaType` runs 333 times. The graph is 19 382 nodes for 5 164 lines: 11 103 variables, 4 367
values, 582 lazy values and 3 330 uses, traversed in 2.3 s. What it shares with BUG-96 is the mechanism
that turns a cluster into time.

**The program's own position fields form one cluster.** `checkTableCompatibility` joins a value's
member to the matching demand member in both directions. `compile.lua` builds hundreds of syntax-tree
tables with `start` and `finish` fields, reads and writes `obj.start` and `obj.finish`, and passes
the tables to shared functions, so 23 068 joins in pass 1 connect those fields into one cluster. Its
largest up-set holds 857 nodes: 819 variables and 28 values. The variables are 290 index expressions
(201 `.finish`, 89 `.start`), 269 table fields such as `finish = lastRightPosition()`, 170 call results
of `getPosition` and `lastRightPosition`, and 63 locals. 961 variables have an up-set over 100 nodes
and 888 a down-set over 100; the largest down-set is 1 103. Since sets are transitively closed, the
joins add 457 938 edges and function wiring 132 405: 590 343 in pass 1, on top of 11 909 from
traversal.

**Every walk iterates the cluster's closure, and every edge invalidates its memo.** Each walk visits
every variable it reaches and iterates that variable's whole set, so a root in a cluster of k costs
O(k²). Pass 1 opens 21 006 `write`, 23 856 `read` and 22 459 `declaredDemand` walks that miss the root
memo, because an edge has moved the revision since the last. They make 229 539 571 `write` visits
over 246 713 031 up-set entries, 440 786 327 `resolveRead` calls over 441 327 580 down-set entries,
and 278 414 764 `resolveDeclaredDemand` calls.

| pass 1 of 30 899 ms | ms |
|---|---|
| `read` walks | 13 736 |
| `declaredDemand` walks | 5 612 |
| `write` walks | 5 135 |
| edge propagation | 4 203 |
| the rest of checking | about 2 200 |

Pass 1 admits 354 250 pairs of 20 906 140 tried.

**The 5 s limit stops checking after pass 1.** Pass 1 takes the whole build, so `checkTypes` never
starts pass 2, and the 716 errors are a single pass's. With the limit lifted, pass 2 takes 2 971 ms,
admits 2 357 new pairs, adds no edges and raises the errors to 820; pass 3 finds nothing new. Any
fix that brings pass 1 under 5 s therefore changes this file's findings from 716 to 820, as BUG-96
found for fallo.

## 6. Fix
Two of the three walks no longer re-walk the cluster. Both rely on the sets being transitively
closed, which `addEdge`'s propagation keeps, and give the same answer as before.

- **`declaredDemand` reads the variable's own down-set** (`reachesDeclaredDemand` in
  `LuaTypeNodes.kt`). Every use reachable through a chain of variables is already in the root's
  down-set, and the answer is whether any of them is declared, so visiting order does not matter.
  Pass 1's `declaredDemand` walks fall from 5 612 ms to 14 ms.
- **The `write` walk stops once it has met every node of the root's up-set.** The up-set holds every
  node the walk can reach, so after the last one is met each variable left is visited and each value
  is seen, and the rest of the walk adds nothing. A lazy value is resolved again at each encounter,
  with the guard as it stands, so a root whose up-set holds one is walked in full. Pass 1's up-set
  iterations fall from 246 713 031 to 108 061 997, and its `write` walks from 5 135 ms to about
  2 800 ms. The walk is still depth first in the old order, so the variables it meets before the
  closure is complete are still iterated in full.

A differential ran each new walk beside the old one at every walk root, across the full suite with
the corpus (3 886 tests): 2 595 629 `write` and 677 757 `declaredDemand` comparisons, no mismatch.
`LuaTypeGraphClosureWalkTest` asserts that a `declaredDemand` walk iterates no more than the root's
own down-set, that the `write` walks over a 40-variable cluster iterate under half of what walking
every up-set at every root would, and that both walks match the recursive walks at every root of a
chain and of a star-shaped cluster.

| `compile.lua` own build | run 1 | run 2 | run 3 | errors |
|---|---|---|---|---|
| before (`3899ca5b`) | 30 792 ms | 30 014 ms | 30 815 ms | 716 |
| after | 22 430 ms | 23 255 ms | 24 951 ms | 716 |

The sorted finding lists (offsets, severity and message) are identical in all six runs. The type
goldens, corpus baselines and LuaCATS conformance results are unchanged, and the `luacheck` type
audit's output is byte-identical to the base's.

## 7. Residual
The build is still about 23 s against a 250 ms target, so this bug stays open. What remains in pass
1 (24 878 ms), measured with both changes and the counters in place:

- **`read` walks, about 15 s.** The `read` walk has the same O(k²) shape, but neither shortcut
  applies. Its result is a nested fold: each variable folds its own list (first demand wins, table
  demands merge, a trait outranks tables), and its caller folds that result alongside the caller's
  own uses, in the caller's order. Which demand wins therefore depends on the walk's order, not only
  on the set it reaches.
- **Edge propagation, about 4.5 s,** for 590 343 edges. This is the clique itself: k variables
  joined both ways hold k² entries between them.
- **`write` walks, about 2.8 s, and the rest of checking, about 2.3 s.**

**Removing the cause is a design change.** Variables joined both ways have the same closure, so one
node could stand for each such cycle (a union-find at `addEdge`), which would make the cluster's
edges and walks linear. It changes which node each walk meets first, so union order and first-wins
demands can change, and it would bring pass 1 under the 5 s limit, which runs passes 2 and 3 and
changes the findings from 716 to 820. Those 104 findings would each need classifying. Sharing class
conversions, BUG-96's option, does not apply: this file has none.

## 8. Part 2: one node for each cycle of variables
Measured on `009b0ac3` (base: BUG-111 and BUG-112 on `d07ba0af`) and the working tree on it (after), builder
`libvirt`, GoLand 2026.2.3 test platform. The probe is a throwaway light-fixture test: Standard Lua 5.4 pinned,
one cold `LuaTypesSnapshot.forFile(file).getErrors()` per run, `LuaSnapshotBuildMeter` for the file's own build.

**What changed.**
- `addEdge` merges variables that reach each other into one cluster, with a union-find over
  `VariableElement` (`LuaTypeNodes.kt`, `LuaTypeGraph.collapseCycle`). Members of a cycle hold the same
  closure, so the cluster keeps one up-set, one down-set, one set of walk memos and one walk root. A
  merge changes which node holds an entry and nothing a variable can reach. Since BUG-110 a variable's
  `read` is a function of the set of demands it reaches, so it does not depend on which member's set is
  folded.
- The checker still sees each member. A cluster is expanded back to its member variables when the pairs
  of a visit are listed, so each member is a value and a use of its own and a report anchors on its own
  element. Checking a cluster as one node lost real ERRORs in the prototype.
- MAINT-61's revisit skip is kept. Each representative has a `MemberLog` per set, listing members in the
  order they were first met and only ever appending, so the pairs below a visit's recorded sizes stay the
  product that visit examined. `LuaTypePairScanDifferential` reports 0 unexamined pairs on its six fixtures.
- BUG-112's settle step reads the sets, which hold representatives, while a claim names the member the
  checker expanded to. `LuaSeverityClaims` therefore keys claims by `canonical(value)` and looks the use up by
  `canonical(use)`, and `settleSeverities` walks representatives only. Without it a claim on a non-representative
  member was never demoted by its cluster's unknown write: `LuaSeverityOrderTest`'s cluster case fails
  (an ERROR stays at the member's declaration) and passes with it.
- Pinned counts that moved with the merge: `LuaMemberDemandHubTest` (closures 1 123 / 2 203 / 4 363 to
  900 / 1 780 / 3 540, a single site 70 to 42), `LuaPairScanTest` (pair attempts 407 to 206, full scans 796
  to 394 and 1 474 to 670). `LuaTypeGraphPropagationDepthTest`'s order fixture had a five-variable cycle, whose
  members now share one set; the cycle edge is gone and the recorded orders are the base's, checked on both.
- `closureSize` counts representatives only, and `edgeAddCount` is additions less the entries a merge
  folded away, so the two stay equal.
- `LuaTypeGraphClusterCollapseTest` asserts that a 40-variable cluster holds fewer than 40 set entries
  (a quadratic cluster holds 3 120), that writes over it iterate under 320 entries, and that each member
  still anchors its own error, including one joined after a check.

| `compile.lua` own build | run 1 | run 2 | run 3 | errors |
|---|---|---|---|---|
| before (`009b0ac3`) | 10 925 ms | 9 575 ms | 9 635 ms | 727 |
| after | 2 104 ms | 1 417 ms | 1 429 ms | 831 |

The base takes 12 821-12 846 ms and reports 831 errors with the 5 s limit lifted; the collapsed build is under
the limit, runs passes 2 and 3, and reports the same 831. The fallo member's `init.lua`: 231 s and 1 405
errors before, 68 s and 441 after (541 s and 1 788 with the limit lifted on the base).

**Classification.** A *limit artifact* equals the base run with the 5 s limit lifted. Anything else is a
*semantic change*, traced below. The sorted corpus findings, the goldens, the LuaCATS conformance results and
the `luacheck` type audit were recorded for four states: base, base with the limit lifted, collapse, collapse
with the limit lifted. The collapse's findings do not depend on the limit on any corpus member.

| item | change | class |
|---|---|---|
| `compile.lua` findings | 727 to 831: +67 ERROR, +37 HYPOTHESIS; anchors and messages identical to the base with the limit lifted | limit artifact |
| zerobrane findings | 2 `LuaSuspiciousConcatenation` at `src/editor/menu_project.lua` (4619, 5899) gone; also gone from the base with the limit lifted | limit artifact |
| lazynvim findings | 13 `LuaTypeAssignability` rows gone, 0 anchors lost, 0 added | rendering, see below |
| fallo ERROR anchors | 43 to 40 against the base with the limit lifted; the 3 stay as HYPOTHESIS | semantic, base wrong, see below |
| `chain.golden`, `annotated1/3/20.golden`, `unionReceiver.golden` | rows with a different union rendering gone; no (severity, anchor) pair lost or added | rendering |
| `penlight-Date.golden` | 19 type renderings reordered (`nil \| number` to `number \| nil`) | rendering |
| `luarocks-zip.golden` | +1 HYPOTHESIS `File \| nil is not assignable to { ... }` at `:seek` (12127) | semantic, see below |
| conformance results, `luacheck` type audit | byte-identical | unchanged |
| luacheck, penlight, luarocks findings | identical | unchanged |

**Rendering changes.** Each of the 13 lazynvim rows (`core/loader.lua` 303, 8472, 8520, 8531, 8645, 9248;
`core/util.lua` 153, 434, 447, 509, 617; `core/config.lua` 9064, 9550) is a union rendering of a mismatch the
same anchor still reports under its single-type message (`LazyPlugin[] is not assignable to { ... }`,
`undefined[] is not assignable to { ... }`, `LazySpecLoader is not assignable to nil`). The base reported the
mismatch twice, once per rendering, because two visits met the pair's value as different unions. The
`chain.golden` ERROR `string | number is not assignable to string` at (60,94) is the same case: the anchor keeps
ERROR `number is not assignable to string`.

**fallo, 3 ERRORs now HYPOTHESIS** (`init.lua` 7006:7417, 7184:7266, 15014:15029). The base reports ERROR and
HYPOTHESIS rows for the same pair at each anchor. The sources are `return Result.err(err)` (line 487, declared
`---@return Result<T>`) and `return Result.err(Result.structure_error(...))` (lines 224-229, declared
`Result<T[], E>`). `Result.err` is `---@generic T, E` with `T` bound by nothing, so the value is
`Result<T, E>` against `Result<T>`, an arity mismatch of an unbound generic, not a type error. The
base's ERROR is a false report; the collapse's HYPOTHESIS is consistent with its other rows.

**luarocks `zip.lua`, +1 HYPOTHESIS.** The use is `zh:seek("set", cd_offset)` after `if not zh then return end`
(`:seek` at 12127, a demand `{ seek = fun(p, p) }`). The pair `File | nil` against that demand is examined once.
In the base it was examined while `isCompatible(File, demand)` was true; in the final graph the same call is
false on both (`ZP-FINAL`: `File:false` on the base and on the collapse). The collapse examines the pair later, at
the final state, and so reports a mismatch the base's examination order hid. The mismatch itself is not a true
error (`File:seek(whence, offset)` is valid), so it is a latent false report of `isCompatible` for a method
against a synthesized demand. It is a HYPOTHESIS, `luarocks` inspection findings are unchanged (242), and it is
not fixed here.

**What remains.**
- `isCompatible(File, { seek = fun(p, p) })` is false in the final graph (above). Any pair examined at the final
  state reports it; the collapse changes when a pair is examined, not the check.
- `compile.lua`'s own build is 1.4-2.1 s, against the 250 ms target of MAINT-60. §9 profiles it. The bug stays
  open.
- fallo's `init.lua` takes 68 s and still stops after pass 1 at the 5 s limit; its cost is a separate cluster
  shape and is not addressed here.

## 9. Profile after the collapse
Measured on `db80f6c0` (the collapse, §8, merged), builder `libvirt` (idle: no `~/sim-app` Gradle or other Java
process was running during any run), GoLand 2026.2.3 test platform. A throwaway light-fixture test, not committed,
followed §8's setup: Standard Lua 5.4 pinned, source path `<checkout>/script/?.lua;<checkout>/script/?/init.lua`,
one cold `LuaTypesSnapshot.forFile(file).getErrors()` on `script/parser/compile.lua` (5 164 lines),
`LuaSnapshotBuildMeter` for the file's own build. Three JVMs ran `test --rerun --no-build-cache --tests
'*ProbeBug97*'`; the first two with `-PjfrProfile`, the third without it and with temporary timers around the
build's phases and the fixed-point passes. Each JVM ran three cold reads (`run-1..3`) and then appended `"\n"` to
the file's document and read again (`loop-n`), so that the recording holds many builds.

| `compile.lua` own build | run 1 | run 2 | run 3 | errors |
|---|---|---|---|---|
| JFR job A | 2 737 ms | 1 488 ms | 1 550 ms | 831 |
| JFR job B | 2 673 ms | 2 467 ms | 1 785 ms | 831 |
| timers, no JFR | 2 169 ms | 1 292 ms | 1 379 ms | 831 |

The first read of a JVM includes JIT warm-up. After an edit, the 20 `loop` reads of job B take a median 1 155 ms
(943-1 559 ms). The collapse's 1.4-2.1 s is confirmed, and 250 ms is about one fifth of the steady state.

**Phase split** (timers; wall ms, own build only: a dependency build runs inside the visitor and is subtracted).

| phase | run 2 | run 3 | `loop-4` | `loop-6` |
|---|---|---|---|---|
| own build (`ownSelfMs`) | 1 292 | 1 379 | 1 019 | 996 |
| visitor traversal (graph construction) | 314 | 329 | 113 | 86 |
| `checkTypes`, pass 1 | 611 | 691 | 569 | 626 |
| `checkTypes`, pass 2 | 259 | 248 | 246 | 216 |
| `checkTypes`, pass 3 | 59 | 59 | 61 | 55 |
| `settleSeverities` | 15 | 16 | 14 | 12 |
| `seedAmbientGlobals`, snapshot materialization | 1 | 1 | 1 | 1 |

Checking is 73-91 % of the build, pass 1 alone 47-63 %. The graph has 19 382 nodes and a closure of 158 840 entries
(no edge is added after pass 1). Three passes make 2 064 383 pair attempts (958 333 in pass 1, 1 106 050 in pass 2, 0
in pass 3) and admit 356 607 (209 837 and 146 770): 83 % of attempts find the pair already examined from another
variable. Root resolutions are 13 439 `write`, 6 026 `read` and 4 733 `declaredDemand`. Pass 3 attempts nothing and
still takes 55-61 ms.

**JFR, the 17 `loop` reads of job B from `loop-4`** (1 850 execution samples, about 11 ms apart, mean stack depth 135,
none capped). By phase, from the outermost `LuaTypesVisitor.Companion.buildSnapshot` frame: `runFixedPointPass`
1 483 (80.2 %), dependency builds under `resolveModule` 205 (11.1 %), visitor traversal 134 (7.2 %),
`settleSeverities` 22 (1.2 %), materialization and the rest 6. Dependencies are not part of the own build, so the
shares below divide by the 1 645 other samples where a time in ms is given (1 sample is about 0.7 ms).

| samples | % | frame, topmost `net.internetisalie` frame (self) |
|---|---|---|
| 408 | 22.1 | `LuaTypeGraph$VisitScan.scanUse:601` (`checkedPairs.add(Pair(valueNode, useNode))`) |
| 131 | 7.1 | `MemberLog.append:1490` |
| 105 | 5.7 | `OrderedSet.add:467` |
| 59 | 3.2 | `LuaMemberAssignmentSites.isWrittenToMember:38` |
| 56 + 23 | 3.0 + 1.2 | `MemberLog.extendedBy` (`members.toList()`, the loop) |
| 49 | 2.6 | `LuaGraphType$Table.hashCode` |
| 36 | 1.9 | `LuaGraphType.getMembers:267` |
| 31 + 27 + 15 | 1.7 + 1.5 + 0.8 | `WriteWalk.collect:401,399,409` |
| 23 + 15 | 1.2 + 0.8 | `LuaTypeGraph.addError` (`_errors.any`) |

| samples | % | inclusive frame |
|---|---|---|
| 1 516 | 81.9 | `LuaTypeGraph.checkTypes` |
| 1 105 | 59.7 | `VisitScan.scanUse` |
| 634 | 34.3 | `checkAdmittedPair` (`checkCompatibility` 594, 32.1) |
| 261 | 14.1 | `addEdge` (`propagateBiEdge` 239, 12.9) |
| 253 | 13.7 | `checkTableCompatibility` |
| 224 | 12.1 | `MemberLog.extendedBy` |
| 205 | 11.1 | `LuaTypeManagerImpl.resolveModule` (dependency builds) |
| 161 | 8.7 | `VariableElement.getWrite` (`WriteWalk.collect` 147, 7.9) |
| 139 | 7.5 | `VisitScan.<init>` (`visitFactsOf`) |
| 129 | 7.0 | `checkFunctionCompatibility` |
| 66 | 3.6 | `isCompatible` |
| 70 | 3.8 | `LuaParser.parse` (the reparse after the edit, not the engine) |

Top JDK self frames: `HashMap.putVal` 20.3 %, `IdentityHashMap.put` 5.7 %, `Intrinsics.areEqual` 3.5 %,
`LinkedHashIterator.nextNode` 2.8 %, `HashMap.resize` 2.3 %. They are the set inserts above, not a separate cost.

**Allocation** (`jdk.ObjectAllocationSample`, 5 478 samples, an estimated 6 254 MB, about 370 MB a build). By class:
`kotlin.Pair` 24.4 %, `Object[]` 14.3 %, `LinkedHashMap$Entry` 11.2 %, `LinkedHashMap` 8.8 %, `HashMap$Node[]` 7.8 %,
`LinkedHashMap$LinkedKeyIterator` 5.9 %. By topmost Lunar frame: `scanUse:601` 17.1 %, `checkCompatibility:690` (the
`visited` default argument) 5.5 %, `checkCompatibility:716` 4.7 %, `OrderedSet.iterator:479` 4.3 %, `WriteWalk.collect:399`
4.2 %, `MemberLog.append:1490` 3.7 %, `DownwardStep.next:1554` 3.5 %, `UpwardStep.next:1578` 3.3 %,
`CompatContext.<init>:22` 3.0 %. `LuaRockspecDiscoveryService.enumerateIncluded:99` is 6.3 % and is outside the checker.

**Candidates**, ranked by estimated saving on the 1 155 ms steady-state build (line numbers on `db80f6c0`,
`LuaTypeGraph.kt` unless named). The estimates are shares of the samples above; they overlap and are not additive.

| # | candidate | where | est. saving |
|---|---|---|---|
| 1 | Reject an already examined pair without allocating or hashing a `Pair`: a per-use bit set over value ordinals, or an identity set per use. 1.7 M of the 2.06 M attempts only fail the insert. | `scanUse:598-601`, `checkedPairs` in `runFixedPointPass:493` | 150-250 ms |
| 2 | Make the `MemberLog` cursor-based: it re-walks the whole up- and down-set of every visited representative, re-expands every cluster and copies `members.toList()` on every visit. Keep an insertion-ordered list in `OrderedSet` and append only the entries past the log's cursor; return a size-bounded view. Order is unchanged. | `MemberLog:1477-1492`, called from `checkNodePairs:546-547`; `OrderedSet` in `LuaTypeNodes.kt:460-482` | 100-150 ms |
| 3 | Do not build a `VisitScan` when the visit has nothing new (`mark.up == upSnapshot.size && mark.down == downSnapshot.size`). Today every visit computes `visitFactsOf` (a `count` and an `any { isUnknown(it.write) }` over the up-set) first, which is all of pass 3 (55-61 ms) and part of pass 2. Computing the facts lazily inside the scan is not an option: MAINT-61 risks Gap 2.4 records that it moved `write` resolutions into cross-file resolution. | `checkNodePairs:542-558`, `VisitScan:586-590`, `visitFactsOf:575` | 60-90 ms |
| 4 | Allocate `visited` lazily in `checkCompatibility`: the default `mutableSetOf()` runs before the early returns, and `visited.add(valueType to useType)` hashes two structural `Table`s (`Table.hashCode`, `Union.hashCode`, `equals`: about 100 samples). Key it by identity, and allocate `CompatContext` once per pair. | `checkCompatibility:690,716`, `CompatContext:22` | 60-100 ms |
| 5 | In `checkTableCompatibility`, test `isRequired && !isOptional(readType)` before `isWrittenToMember` (a pure PSI parent walk, evaluated for every missing member today), and stop rebuilding two merged maps per pair in `getMembers`. | `:1202-1225`, `LuaGraphType.kt:259-270`, `LuaMemberAssignmentSites.kt:37-42` | 40-70 ms |
| 6 | `WriteWalk.collect`: 13 439 root walks, each flattening reachable writes into fresh sets. Cache a representative's `write` across revisions that did not touch its up-set, since pass 2 and 3 add no edge. | `LuaTypeNodes.kt:373-410` | 30-60 ms, low confidence |
| 7 | `addError` scans `_errors` linearly for each of the file's 831 errors and every re-report; use a hash set of `(element, message)`. | `:352` | about 25 ms |
| 8 | Return the next step of `DownwardStep` and `UpwardStep` through fields instead of a `Pair` per set entry (158 840 adds). | `:1554`, `:1578` | 20-30 ms |
| 9 | Structural: a `(value, use)` pair is attempted from every variable that holds both, so 83 % of attempts are repeats. Examine each pair once, at one representative per path. It removes the work behind candidates 1, 2 and 3 together. It changes which variable examines a pair first, which is what BUG-112's `settleSeverities` exists to make irrelevant, so it needs its own differential over the corpus findings. | `checkNodePairs:542`, `runFixedPointPass:493` | not estimated; bounded by the first three |

Candidates 1-8 together estimate 500-750 ms of 1 155 ms, which leaves 400-650 ms against the 250 ms target. The profile
does not support reaching 250 ms with 1-8 alone; it needs 9. The visitor phase (86-113 ms warm, 314-329 ms on the
second and third cold reads) and the dependency builds (about 11 % of a read, 100-220 ms) are the other parts of
the read and are untouched by all nine.

Not established: how the per-pass attempts split by representative (the counters are per graph), and whether
the candidates' savings add, because each removes part of the same allocation and cache pressure.
