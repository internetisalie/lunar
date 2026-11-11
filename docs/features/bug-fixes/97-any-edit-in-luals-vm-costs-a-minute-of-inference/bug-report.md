---
id: "BUG-97"
title: "Passes that depend on luals script/parser/compile.lua are over 250 ms because its own build takes up to 43 seconds"
type: "bug"
parent_id: "BUG"
status: "todo"
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
