---
id: "MAINT-61-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-61"
folders:
  - "[[features/maint/61-own-build-cost/requirements|requirements]]"
---

# Technical Design: MAINT-61 — Revisit-Free Pair Scan

## 1. Architecture Overview

### Current State
`LuaTypeGraph.checkTypes` (`LuaTypeGraph.kt:297`) runs passes until one adds no edge and admits no
pair. On every visit, `checkNodePairs` (`:377`):
- copies both sets with `toList()`;
- computes `VisitFacts`, which resolves `write` for every value in the up-set;
- tries `checkedPairs.add` for the whole product, where all but the newly added pairs fail.

Each pass also sums every variable's set sizes twice (`edgeCount`, `:346`). In the hubs these costs
are most of the pair loop's samples (risks DR-01).

### Prior Art in This Repo
| Component | file:line | This design |
|---|---|---|
| `checkNodePairs`, `runFixedPointPass`, `checkTypes` | `LuaTypeGraph.kt:377, 336, 297` | **edited** (§3) |
| `VisitFacts`, `AdmittedPair`, `checkAdmittedPair` | `LuaTypeGraph.kt:418, 424, 430` | **unchanged**, called from the new scan |
| `OrderedSet` | `LuaTypeNodes.kt:292` | **unchanged** |
| `VariableElement.upSet` / `downSet` hooks | `LuaTypeNodes.kt:132-133` | **edited**: `graph::onEdgeAdded` |
| `LuaTypePairWriteDifferential` and its test | `LuaTypeGraph.kt:1118`; `lang/types/LuaTypePairWriteDifferentialTest.kt` | **unchanged**, still called for every admitted pair. `LuaTypePairScanDifferential` follows its shape |
| `closureSize`, `rootResolutionCount` meters | `LuaTypeGraph.kt:1047, 1056`; `LuaTypes.kt:73, 115` | **unchanged**. Four meters are added beside them (§2.2) |
| MAINT-60 delta configuration (MAINT-60 DR-06) | probe only, never shipped | **replaced** by §3.1, which keeps the order of admitted pairs |

### Target State
The admitted pairs, their order and the convergence test are as today. A revisit scans only pairs
with a value or a use added since the variable's last visit (§3.1). The facts are computed at the
visit's start, as today (§3.2), and the convergence test reads a counter (§3.3). There are no
`plugin.xml` changes. Everything runs on the thread that builds the snapshot, inside its read action,
as today.

## 2. Core Components (all in `net.internetisalie.lunar.lang.psi.types`)

### 2.1 `VariableElement` (`LuaTypeNodes.kt`, edit)
`upSet` and `downSet` are built with `OrderedSet(graph::onEdgeAdded)` instead of
`OrderedSet(graph::bumpRevision)`.

### 2.2 `LuaTypeGraph` (`LuaTypeGraph.kt`, edit)
```kotlin
private var edgeAdds = 0L
private var pairAttempts = 0L
private var scanComparisons = 0L
private var scanMarks: java.util.IdentityHashMap<VariableElement, PairScanMark>? = null
internal fun onEdgeAdded()                              // edgeAdds++, then bumpRevision()
@TestOnly internal fun edgeAddCount(): Long = edgeAdds
@TestOnly internal fun pairAttemptCount(): Long = pairAttempts
@TestOnly internal fun scanComparisonCount(): Long = scanComparisons
@TestOnly internal fun scanMarksRetained(): Boolean = scanMarks != null
private fun checkNodePairs(node: VariableElement, checkedPairs: MutableSet<Pair<TypeNode, TypeNode>>)  // §3.1
private fun visitFactsOf(upSnapshot: List<TypeNode>): VisitFacts                                     // §3.2
private fun verifyScan(upSnapshot: List<TypeNode>, downSnapshot: List<TypeNode>, checkedPairs: Set<Pair<TypeNode, TypeNode>>)  // §3.4
private inner class VisitScan(val upSnapshot: List<TypeNode>, val checkedPairs: MutableSet<Pair<TypeNode, TypeNode>>) {
    val facts = visitFactsOf(upSnapshot)
    val writes = arrayOfNulls<LuaGraphType>(upSnapshot.size)
    fun scanUse(useNode: UseNode, firstValue: Int)      // §3.1 step 4
}
```
`checkTypes` sets `scanMarks = IdentityHashMap()` before its loop and wraps the loop in
`try { … } finally { scanMarks = null }`, so neither a mark nor the map's table outlives the call
(`IdentityHashMap.clear()` would keep the table at its peak size), including a call ended by a cutoff or an exception. `runFixedPointPass` reads
`edgeAdds` where it called `edgeCount()` (§3.3). `edgeCount()` stays, because `closureSize()` uses it.
The revision KDoc (`:47-62`) names `onEdgeAdded` as the hook.

### 2.3 `PairScanMark` and `LuaPairScan` (`LuaTypeGraph.kt`, new)
```kotlin
internal class PairScanMark(val up: Int, val down: Int)
internal object LuaPairScan {
    fun firstValueIndex(downIndex: Int, mark: PairScanMark?): Int =
        if (mark == null || downIndex >= mark.down) 0 else mark.up
}
```

### 2.4 `LuaTypePairScanDifferential` (`LuaTypeGraph.kt`, new, `@TestOnly internal object`)
It has the same shape as `LuaTypePairWriteDifferential`: `enabled` with a private setter,
`start()` (sets misses to 0 and `enabled = true`), `stop()`, `misses(): Long`, and
`observe(examined: Boolean)`, which adds 1 to misses when `examined` is false. It holds only that
counter. The number of comparisons is per graph (`scanComparisonCount`), so a test reads it for one
build however many builds ran while the differential was armed.

### 2.5 `LuaTypesSnapshot` (`LuaTypes.kt`, edit)
One `@TestOnly internal` delegate per meter of §2.2 (`edgeAddCount`, `pairAttemptCount`,
`scanComparisonCount`, `scanMarksRetained`), next to `closureSize()`.

### 2.6 Sweep switch (`build.gradle.kts`, `LuaSnapshotCostSweepTest`, `SnapshotCostVerdict`; edit)
- `build.gradle.kts`, inside the `withSnapshotSweep` block, next to the oracle line (`:466`):
  `systemProperty("lunar.pairScan.differential", project.hasProperty("withPairScanDifferential").toString())`.
- `LuaSnapshotCostSweepTest`: `PAIR_SCAN_ON = System.getProperty("lunar.pairScan.differential") == "true"`
  beside `ORACLE_ON`. `setUp` calls `LuaTypePairScanDifferential.start()` when `PAIR_SCAN_ON` and
  the differential is not enabled. This leaves it armed across the run's member methods.
  `testZzVerdict` adds `SnapshotCostVerdict.pairScanChecks(LuaTypePairScanDifferential.misses())`
  to `checks` when `PAIR_SCAN_ON`, then calls `stop()`.
- `SnapshotCostVerdict.pairScanChecks(misses: Long): List<String>` returns
  `["pair scan: <misses> unexamined pairs after a visit"]` when `misses > 0`, else `[]`.

## 3. Algorithms

### 3.1 `checkNodePairs`
1. `upSnapshot = node.upSet.toList()` and `downSnapshot = node.downSet.toList()`, as today. The sets
   only grow by appending, so an element's index in a snapshot is its insertion position, and is
   the same in every later snapshot.
2. `marks = checkNotNull(scanMarks)` (non-null inside `checkTypes`; `checkNodePairs` is called only from there);
   `mark = marks[node]`, then `marks[node] = PairScanMark(upSnapshot.size, downSnapshot.size)`.
3. `scan = VisitScan(upSnapshot, checkedPairs)`.
4. For `downIndex` in `downSnapshot.indices`, with `useNode = downSnapshot[downIndex]`: skip it unless it
   is a `UseNode`, else `scan.scanUse(useNode, LuaPairScan.firstValueIndex(downIndex, mark))`.
   `scanUse` does the following for `valueIndex` in `firstValue until upSnapshot.size`, with
   `valueNode = upSnapshot[valueIndex]`:
   - skip it unless it is a `ValueNode`;
   - `pairAttempts++`;
   - skip it if `!checkedPairs.add(Pair(valueNode, useNode))`;
   - take `write` from `writes`, filling the slot on first use as today, and call
     `LuaTypePairWriteDifferential` as today;
   - `checkAdmittedPair(AdmittedPair(valueNode, useNode, write), facts)`.
5. When `LuaTypePairScanDifferential.enabled`: `verifyScan(upSnapshot, downSnapshot, checkedPairs)`.

**Why the skipped pairs change nothing**, by induction over the visits of `node` in one
`checkTypes` call. Claim: after each visit, every pair of the snapshots' product is in
`checkedPairs`. On the first visit `mark` is null, `firstValue` is 0 for every use, and the whole
product is tried. On a later visit, the skipped pairs are `downIndex < mark.down` with
`valueIndex < mark.up`. By step 1 these are the product of the previous visit's snapshots, which by
the claim are in `checkedPairs`; it never shrinks. Every other pair is tried. The unmodified loop
meets the skipped pairs in the same positions and does nothing with them, because
`checkedPairs.add` fails. So the sequence of admitted pairs, and every effect of admitting them, is
unchanged. DR-06 found no skipped pair
outside `checkedPairs` over 28 563 456 revisits.

### 3.2 Visit facts
`visitFactsOf` holds today's two expressions (`certain`, `unknownProvenance`, `:395-398`),
computed over `upSnapshot` when `VisitScan` is built, at the visit's start as today. Computing them
only at the first admitted pair was dropped: it moved `write` root resolutions onto cross-file
call-site resolution, against NAV-13-06's guard (risks Gap 2.4).

### 3.3 Convergence by counter
`runFixedPointPass` sets `initialEdgeCount = edgeAdds` and returns
`edgeAdds > initialEdgeCount || checkedPairs.size > initialCheckedCount`. `edgeCount()` is the sum
of the set sizes of every `VariableElement` in `_nodes`. Every `VariableElement` is created by
`variable()`, which appends it to `_nodes` (`:151-152`). Its sets only grow, and every successful
add calls the hook. So `edgeCount() == edgeAdds` at every point, and both tests have the same truth
value. TC 4 asserts the equality.

### 3.4 `verifyScan`
For each `UseNode` `u` in `downSnapshot` and each `ValueNode` `v` in `upSnapshot`:
`scanComparisons++`, then `LuaTypePairScanDifferential.observe(Pair(v, u) in checkedPairs)`. Per
visit this is the number of pairs the unmodified loop tries, so a build's `scanComparisonCount` is
its full-scan pair count (requirements TC 2).

## 4. Test Fixtures and Files
- `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaPairScanTest.kt` (new, `BaseDocumentTest`,
  JUnit 5, `EdtTestUtil.runInEdtAndWait`, as `LuaTypePairWriteDifferentialTest`). It holds TC 1 to
  TC 4. TC 2 to TC 4 read, in this order and in one test method's project,
  `myFixture.configureByText("<name>.lua", text)` and then
  `LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot`. TC 2 calls
  `LuaTypePairScanDifferential.start()` before each read and `stop()` after it. TC 3 reads `method` as the
  third text of that sequence. The texts, in DR-07's order:
  - `callback`: `---@param cb fun(x: string)\nlocal function f(cb) end\nf(function(x) local y = x print(y) end)\n`
  - `callback2`: `---@param cb fun(x: string): number\nlocal function f(cb) return cb('a') end\nlocal r = f(function(x) return #x end)\nprint(r + 1)\n`
  - `method`: `---@class P61\nlocal P = {}\n---@param n number\nfunction P:set(n) self.n = n end\nlocal p = P\np:set(1)\nlocal q = p\nq:set('s')\n`
  - `fwd`: `local function g(a) return a end\nlocal function h(b) return g(b) end\nlocal z = h(1)\nlocal w = h('s')\nprint(z, w)\n`
  - `chain`: `local function id(v) return v end\nlocal t = {}\nt.a = id(t)\nt.b = id(t.a)\nlocal u = id(t.b)\nprint(u.a, u.b)\n`
  - `hof`: `---@param f fun(a: number): number\n---@return fun(a: number): number\nlocal function wrap(f) return function(a) return f(a) end end\nlocal k = wrap(function(a) return a * 2 end)\nlocal m = wrap(k)\nprint(m(1))\n`

  DR-07's counts are for this order, so the test keeps it.
- `SnapshotCostVerdictTest` gains `testPairScanChecks`: `pairScanChecks(0) == []`, and
  `pairScanChecks(3) == ["pair scan: 3 unexamined pairs after a visit"]`.

## 5. Contract Conformance
A cached snapshot keeps its graph (`LuaTypes.kt:56`), so a graph field is retained memory. This
design adds three `Long` counters and one nullable field per graph, and nothing per node or per
edge: `OrderedSet` is unchanged, and the snapshots are the transient copies of today. `scanMarks`
exists, with one entry per visited variable, only while `checkTypes` runs; the `finally` of §2.2
drops the map, so a retained graph holds `null`. TC 4 asserts `scanMarksRetained() == false` after
a build.
Each function stays within 30 logic lines and
3 arguments (`VisitScan` carries the per-visit state). There are no new threads, read actions or
write actions.

## 6. Requirement Coverage
| Requirement | Design |
|---|---|
| MAINT-61-01 | §3.1, §3.3, §3.4 |
| MAINT-61-02 | §3 (exactness); acceptance in the implementation plan, Phase 2 |
| MAINT-61-03 | §3.1 step 4 |
| MAINT-61-04, -05 | implementation plan, Phase 2 |

## 7. Alternatives Considered
- **No revision bump on value and use creation**: measured no gain (DR-05 `p5`).
- **Flattened walks** (MAINT-60 DR-06): change findings, so they stay with MAINT-64.
- **Index access through a second list in `OrderedSet`** (the DR-08 probe): saves the down-set
  copy, but retains one more reference per edge in every cached snapshot (`druid_instance.lua`'s
  graph holds 1 779 644 nodes). Rejected for §5. The copy is O(|down|) per visit against the
  O(|up|·|down|) scan, and DR-01 charged both copies 1 504 samples against 3 408 for
  `checkedPairs.add`; DR-08 did not measure that copy (Risk 1.2).
- **Skipping the whole visit when neither set grew**: the delta scan already scans no pair then, and
  only the two copies remain, which are kept for simplicity.

## 8. Open Questions
None. DR-09 and DR-10 are tracked in [risks-and-gaps.md](risks-and-gaps.md).
