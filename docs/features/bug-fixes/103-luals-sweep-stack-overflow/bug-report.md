---
id: "BUG-103"
title: "Analysing the luals corpus member intermittently fails with StackOverflowError"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-103: luals analysis intermittently overflows the stack

The overflow is in `LuaTypeGraph`'s transitive-closure walk, which recurses once for every pair it
adds. One edge in luals `script/parser/compile.lua` adds 8,099 pairs in a single walk. That fits in
the 1 MB test thread when the JIT has compiled the walk, and overflows it while the walk is still
interpreted. luals is the first member of the sweep, so whether it overflows depends on JIT timing.
The fix is surgical: run the same walk on an explicit stack, visiting pairs in the same order.

## 1. Reproduction

### 1.1 In the sweep (intermittent)
`test -PwithSnapshotSweep --tests '*SnapshotCostSweep*' --rerun --no-build-cache`. In about 2 of 8
sweeps (MAINT-65 `r2-p1-sweep1`, and the first MAINT-63 DR-17 sweep at `4035acf4`),
`testM00aLuals` failed with `java.lang.StackOverflowError at HashMap.java:671` and wrote no luals
rows. `CorpusSnapshotReads.readErrors` catches `Exception`, not `Error`, so the overflow ends the
test method.

### 1.2 Deterministic
Keep the two walk functions out of the JIT, so every frame is an interpreted frame:

```kotlin
// build.gradle.kts, inside tasks.test { }, temporary
jvmArgs(
    "-XX:CompileCommand=quiet",
    "-XX:CompileCommand=exclude,net.internetisalie.lunar.lang.psi.types.LuaTypeGraph::propagateDownward",
    "-XX:CompileCommand=exclude,net.internetisalie.lunar.lang.psi.types.LuaTypeGraph::propagateUpward",
)
```

A probe set up exactly as `LuaSnapshotCostSweepTest.measureMember` (luals target, definition
libraries, `sourcePath` on the on-disk checkout, `script/vm` copied) then reads the 21 selected files
in sweep order through `CorpusSnapshotReads.readErrors`. The test JVM has no `-Xss`, so its threads
get the JVM default of 1 MB.

| Thread | Base | Base + fix |
|---|---|---|
| Test thread (`AWT-EventQueue-0`, as the sweep) | `StackOverflowError` on all 21 files | 21 ok |
| Worker, 1 MB stack | `StackOverflowError` on all 21 files | 21 ok |
| Worker, 2 GB stack | 21 ok | 21 ok |

All 21 files fail on base because each one's first read reaches `script/parser/compile.lua` through
`require`, and the failed build is not cached.

With the JIT on, base overflows on a 400 KB worker and completes on the test thread and on a 2 GB
worker. With a depth counter added to the walk, which makes each frame larger, it also overflows at
512 KB and completes at 768 KB and 1 MB.

## 2. Expected
Analysing any file of the luals member completes on every run, whatever the JIT state.

## 3. Actual
The analysis fails whenever `compile.lua`'s snapshot is built before the JIT has compiled the walk
functions. In a live IDE the same overflow would abort that file's analysis and its highlighting.
The sweep has no luals rows on a failing run, so MAINT-63-03 and the release verdict have to count
around the member.

## 4. Root cause

### 4.1 The repeating frames
Captured on base by reading the luals files on a worker thread with a 400 KB stack (every overflow
trace of every file had the same shape):

```
java.base/java.util.HashMap.putVal(HashMap.java:664)
java.base/java.util.HashMap.put(HashMap.java:619)
java.base/java.util.HashSet.add(HashSet.java:230)
net.internetisalie.lunar.lang.psi.types.OrderedSet.add(LuaTypeNodes.kt:299)
net.internetisalie.lunar.lang.psi.types.LuaTypeGraph.propagateDownward(LuaTypeGraph.kt:994)
net.internetisalie.lunar.lang.psi.types.LuaTypeGraph.propagateDownward(LuaTypeGraph.kt:996)
net.internetisalie.lunar.lang.psi.types.LuaTypeGraph.propagateDownward(LuaTypeGraph.kt:996)
... propagateDownward at :996 or :999 to the end of the trace
```

Each trace holds the 1,024 frames the JVM records, and 1,019 or 1,022 of them are
`propagateDownward` at `:996` or `:999`.

The cycle is one frame long: `LuaTypeGraph.propagateDownward` calling itself, at
`LuaTypeGraph.kt:996` (`for (upstream in from.upSet) propagateDownward(upstream, to)`) and `:999`
(`if (downstream is VariableElement) propagateDownward(from, downstream)`). `propagateUpward`
(`:1004-1016`) has the same shape at `:1010` and `:1013`. Both are reached from `addEdge` (`:168`)
directly or through `propagateBiEdge` (`:1018`).

The `HashMap` top frame is only where the stack ran out. Each frame starts with
`to.upSet.add(from)` (`:994`), which is `OrderedSet.add` (`LuaTypeNodes.kt:298-299`) and then
`LinkedHashSet.add`. It is not `hashCode` or `equals` recursion, and no `LuaGraphType` is involved.
The line inside `putVal` (664 here, 671 in the sweep) varies with which call in `putVal` hit the
guard page.

### 4.2 Deep and bounded, not a cycle
A frame recurses only after `upSet.add` (or `downSet.add`) returned `true`. Each frame on the stack
therefore stands for a distinct pair newly added to the closure, and the depth is bounded by the
number of pairs one `addEdge` call adds. The evidence that it terminates and is not order-dependent:

- With a 2 GB stack, base completes all 21 files in sorted, reversed and shuffled read orders.
- A temporary depth counter on the two functions measured the same maximum, **8,099**, in all three
  orders and on every thread that completed. The other luals files peak at 26 or below.
- The deepest frame is a member node of `script/parser/compile.lua`. Recorded sites include
  `finish = left + 2` (line 516), `finish = rtn.finish` (2651), `finish = op.finish` (3313) and
  `finish = name.finish` (4995). These are `finish` fields of the parser's AST-node table
  constructors. Which edges join them was not traced. The likely path is the two-way member edges
  at `LuaTypeGraph.kt:940-948` (`addEdge(valueNode, target)` and `addEdge(target, valueNode)`),
  which would join every `finish` member that meets one demand into one strongly connected set.

Nested snapshot builds are not the cause. They reach a depth of 10 builds and fewer than 650 frames
at the end of a build.

### 4.3 Why it is intermittent
The depth is fixed. What varies is the size of a frame, and an interpreted frame is larger than a
compiled one. 8,099 compiled frames fit in 1 MB: base completes at 768 KB with the JIT on, even with
the depth counter's extra locals. 8,099 interpreted frames do not: base fails at 1 MB with the walk
kept out of the JIT (§1.2). luals is the sweep's first member (`testM00aLuals`), so it runs in a
cold JVM, and whether `propagateDownward` has been compiled by the time `compile.lua` is built
depends on the JIT's background compile threads. A failure in 2 of 8 sweeps fits that. The JIT state
in a failing sweep was not observed directly. What the probes show is that the depth is the same on
every run and that frame size alone decides the outcome.

The fix is unaffected by BUG-102: the depth was measured at `1f5f43e4`, after it. DR-24's two
clean sweeps after `1f5f43e4`, and the 3 base sweeps of §5.2, which all passed, are consistent with
an intermittent rate.

## 5. Fix strategy

`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt` is the only production
file. Replace `propagateDownward` and `propagateUpward` (`:990-1016`) with the two functions below,
which loop over an explicit `ArrayDeque`. Append two private classes, `DownwardStep` and
`UpwardStep`, at the end of the file. Each holds one pair and the iterators of its two loops.
`addEdge`, `propagateBiEdge` and both signatures stay as they are.

```kotlin
    /**
     * BUG-103: the closure walk keeps its frames on an explicit stack, not the thread's. Each frame
     * is one newly added pair, so its depth is the number of pairs one edge adds, which reached
     * 8,099 on luals `script/parser/compile.lua`. [DownwardStep] visits the pairs in the order the
     * recursive form did, so the sets' insertion order, and everything that iterates them, is
     * unchanged.
     */
    private fun propagateDownward(
        from: TypeNode,
        to: VariableElement,
    ) {
        if (!to.upSet.add(from)) return
        val steps = ArrayDeque<DownwardStep>()
        steps.addLast(DownwardStep(from, to))
        while (steps.isNotEmpty()) {
            val pair = steps.last().next()
            when {
                pair == null -> steps.removeLast()
                pair.second.upSet.add(pair.first) -> steps.addLast(DownwardStep(pair.first, pair.second))
            }
        }
    }

    /** BUG-103: [propagateDownward]'s mirror, on an explicit stack of [UpwardStep]s. */
    private fun propagateUpward(
        from: VariableElement,
        to: TypeNode,
    ) {
        if (!from.downSet.add(to)) return
        val steps = ArrayDeque<UpwardStep>()
        steps.addLast(UpwardStep(from, to))
        while (steps.isNotEmpty()) {
            val pair = steps.last().next()
            when {
                pair == null -> steps.removeLast()
                pair.first.downSet.add(pair.second) -> steps.addLast(UpwardStep(pair.first, pair.second))
            }
        }
    }
```

Append at the end of the file:

```kotlin
/**
 * BUG-103: one frame of [LuaTypeGraph.propagateDownward]'s walk: the pair it added, `from` into
 * `to.upSet`, and how far it has got through its two loops. The first iterator is opened when the
 * frame is, right after the add, and the second only when the first is exhausted, as in the
 * recursive form.
 */
private class DownwardStep(
    private val from: TypeNode,
    private val to: VariableElement,
) {
    private val upstreams: Iterator<TypeNode>? = (from as? VariableElement)?.upSet?.iterator()
    private var downstreams: Iterator<TypeNode>? = null

    /** The next pair to add, upstreams of [from] into [to] first, then [from] into each downstream of [to]. */
    fun next(): Pair<TypeNode, VariableElement>? {
        if (upstreams != null && upstreams.hasNext()) return upstreams.next() to to
        val pending = downstreams ?: to.downSet.iterator().also { downstreams = it }
        while (pending.hasNext()) {
            val downstream = pending.next()
            if (downstream is VariableElement) return from to downstream
        }
        return null
    }
}

/** BUG-103: [DownwardStep]'s mirror for [LuaTypeGraph.propagateUpward]: `to` into `from.downSet`. */
private class UpwardStep(
    private val from: VariableElement,
    private val to: TypeNode,
) {
    private val downstreams: Iterator<TypeNode>? = (to as? VariableElement)?.downSet?.iterator()
    private var upstreams: Iterator<TypeNode>? = null

    /** The next pair to add, [from] onto each downstream of [to] first, then each upstream of [from] onto [to]. */
    fun next(): Pair<VariableElement, TypeNode>? {
        if (downstreams != null && downstreams.hasNext()) return from to downstreams.next()
        val pending = upstreams ?: from.upSet.iterator().also { upstreams = it }
        while (pending.hasNext()) {
            val upstream = pending.next()
            if (upstream is VariableElement) return upstream to to
        }
        return null
    }
}
```

**The order is the contract.** `OrderedSet` iteration order feeds union member order, and through it
messages and digests, so the rewrite must add pairs in exactly the recursive order. It does so
because it makes the same iterator calls in the same sequence:

- Each step's first iterator is opened when the step is created, right after its pair was added.
  This is where the recursive frame opened it.
- The second iterator is opened only when the first is exhausted.
- A child step runs to completion before its parent calls `hasNext()` again.

Each function is under 30 logic lines and takes at most 3 arguments. Nothing holds a
`Project`, `PsiFile` or `Editor`, and the threading is unchanged: the walk runs inside the snapshot
build's read action, on whichever thread builds it.

### 5.1 Prototype results
The prototype is the code above, applied to `1f5f43e4`.

| Check | Base | Base + fix |
|---|---|---|
| §1.2 interpreted walk, test thread and 1 MB worker | overflow, 21/21 files | **21/21 ok** |
| `LuaTypeGraphPropagationDepthTest` (§6), 3 tests | 2 red (`StackOverflowError`), 1 green | **3 green** |
| Recursive vs iterative on 5,000 random graphs (2-29 variables, 0-7 values/uses, 1-119 `addEdge` calls), up/down set order compared | — | **0 mismatches** |
| luals findings, 21 files, `checkTypes` cutoff raised (§7.1), recursive vs iterative in one JVM | — | **21/21 identical** |
| `testM00aLuals` alone, 3 runs each | 3/3 pass, 21 rows | **3/3 pass, 21 rows** |
| Full unit suite (`test --rerun --no-build-cache`), regression test included | — | **3 788 tests, 0 failures, 0 errors, 4 skipped** |
| `ktlintCheck` with the fix and the §6 test | — | **green** |

### 5.2 Cost
`testM00aLuals` alone on the libvirt builder (4 vCPU). No sim-app process was running during any
of these runs. The 1-minute load average was 0.9 to 1.7, all of it from these builds. Cold read of
`script/vm/compiler.lua`, which builds the member (112 or 113 nested builds); the other 20 cold
reads are under 60 ms.

| Run | Base: cold ms | Base: `compile.lua` self ms | Fix: cold ms | Fix: `compile.lua` self ms |
|---|---|---|---|---|
| 1 | 44 843 | 30 684 | 46 337 | 31 639 |
| 2 | 48 714 | 34 265 | 48 812 | 34 007 |
| 3 | 46 976 | 32 504 | 51 853 | 37 127 |
| Mean | 46 844 | 32 484 | 48 999 | 34 258 |

The fix's mean is 4.6% higher, and its range overlaps base's. A same-JVM comparison with the
`checkTypes` cutoff raised (§7.1) gives 54 049 ms recursive and 56 341 ms iterative on a 2 GB worker
(+4.2%), with the recursive read first. The likely source is the `Pair` and step allocated for each
pair. Treat about 4% on luals cold reads as the cost.

Findings digests, compared with base run 1:

| Run | Differing files |
|---|---|
| Base 2 | none |
| Base 3 | `compiler.lua` |
| Fix 1 | `infer.lua` |
| Fix 2 | `infer.lua` (a different value from fix 1) |
| Fix 3 | `compiler.lua` (the same value as base 3) |

Every sweep logged one `Type checking exceeded time limit (5000ms)`. These digests move with timing
on base as well (§7.1), so they cannot show whether the fix changes findings. The comparison in §5.1
with the cutoff out of play can: it gave 21 of 21 files identical. The edit pass is `timeout` on all
21 files in every run, base and fix: `EDIT_DEADLINE_MS` is 1 000 ms, and in base run 1 the top
dependency of all 21 edited reads is `compile.lua`, which the read starts to rebuild.

## 6. Test strategy
- **Regression test.** Create
  `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaTypeGraphPropagationDepthTest.kt`, beside
  `LuaTypeGraphCycleGuardTest`, with exactly this content (it is the prototype's test):

  | Test | Base | After fix |
  |---|---|---|
  | `aValueReachesTheEndOfALongDownstreamChain` | `StackOverflowError` | green |
  | `aUseReachesTheStartOfALongUpstreamChain` | `StackOverflowError` | green |
  | `closureOrderIsTheRecursiveWalksOrder` | green | green |

  The chains are joined by direct set mutation, not `addEdge`, so they stay unclosed. One `addEdge`
  then walks all 200,000 pairs in a single walk while adding only 200,000 entries. The third test's
  expected value is the recursive walk's order, confirmed by running it on base. It is the test that
  fails if the rewrite visits pairs in another order, for example breadth-first.

```kotlin
package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.TypeNode
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-103: `LuaTypeGraph`'s closure walk must not use one thread-stack frame per pair it adds.
 *
 * luals `script/parser/compile.lua` adds 8,099 pairs in one walk, which overflowed the 1 MB test
 * thread whenever the walk ran before the JIT had compiled it.
 */
@RunWith(JUnit4::class)
class LuaTypeGraphPropagationDepthTest : BasePlatformTestCase() {
    @Test
    fun aValueReachesTheEndOfALongDownstreamChain() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("chain.lua", "local v = 1\n")
        val chain = chain(graph, anchor)
        val value = graph.value(anchor, LuaGraphType.String)

        graph.addEdge(value, chain.first())

        assertTrue("every variable in the chain receives the value", chain.all { value in it.upSet })
    }

    @Test
    fun aUseReachesTheStartOfALongUpstreamChain() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("chain.lua", "local v = 1\n")
        val chain = chain(graph, anchor)
        val use = graph.use(anchor, LuaGraphType.String)

        graph.addEdge(chain.last(), use)

        assertTrue("every variable in the chain constrains the use", chain.all { use in it.downSet })
    }

    @Test
    fun closureOrderIsTheRecursiveWalksOrder() {
        val graph = LuaTypeGraph()
        val anchor = myFixture.addFileToProject("order.lua", "local v = 1\n")
        val names = mutableMapOf<TypeNode, String>()
        val variables = "abcde".map { name -> graph.variable(anchor).also { names[it] = "$name" } }
        val (a, b, c, d, e) = variables
        val x = graph.value(anchor, LuaGraphType.String).also { names[it] = "x" }
        val y = graph.value(anchor, LuaGraphType.Number).also { names[it] = "y" }
        val u = graph.use(anchor, LuaGraphType.Any).also { names[it] = "u" }
        val w = graph.use(anchor, LuaGraphType.Any).also { names[it] = "w" }
        listOf(x to a, a to b, c to d, b to u, y to c, b to c, d to w, e to a, d to e)
            .forEach { (from, to) -> graph.addEdge(from, to) }

        val orders = variables.map { "${it.upSet.render(names)}/${it.downSet.render(names)}" }

        assertEquals(EXPECTED_ORDERS, orders)
    }

    private fun Iterable<TypeNode>.render(names: Map<TypeNode, String>): String =
        joinToString("") { names.getValue(it) }

    private fun chain(
        graph: LuaTypeGraph,
        anchor: PsiElement,
    ): List<VariableNode> {
        val chain = List(CHAIN_LENGTH) { graph.variable(anchor) }
        chain.zipWithNext { upstream, downstream ->
            upstream.downSet.add(downstream)
            downstream.upSet.add(upstream)
        }
        return chain
    }

    private companion object {
        const val CHAIN_LENGTH = 200_000

        /** `a` to `e`, each as `upSet/downSet`: the recursive walk's order, recorded on base `1f5f43e4`. */
        val EXPECTED_ORDERS =
            listOf("xeyabcd/budcwae", "axeybcd/ucdwbae", "ybaxecd/dwucbae", "cyxabed/weabucd", "dcybaxe/abucdwe")
    }
}
```

- **Mutation proof (done in the prototype).** On base, the two chain tests are red and the order
  test is green. With the fix, all three are green.
- **luals gate (one-off, not committed).** Run
  `test -PwithSnapshotSweep --tests '*LuaSnapshotCostSweepTest.testM00aLuals' --rerun --no-build-cache`
  three times. Expect 21 rows with `coldStatus` `ok` each time. Digests are not a gate here (§7.1).
- **Full unit suite** green with `test --rerun --no-build-cache`, then `ktlintCheck`.

## 7. Out of scope (found while reproducing)

### 7.1 `checkTypes`' wall-clock cutoff makes luals findings depend on speed
`LuaTypeGraph.checkTypes` (`:297-324`) stops its fixed-point loop after `timeLimitMs = 5000`. On
luals the cutoff decides the findings. With the limit raised to 600,000 ms, base and fix agree on
all 21 files (§5.1), and `compiler.lua` has 311 findings with digest `-890606056`. At the default
limit `compiler.lua` has given three answers: `-890606056` (base sweeps 1-2, fix sweeps 1-2),
`722665400`, also 311 findings (base sweep 3, fix sweep 3, and base with the walk kept out of the
JIT), and 302 findings (the fix with the walk kept out of the JIT, which logged `Type checking
exceeded time limit (5000ms)` 8 times). `infer.lua` moves the same way (§5.2). Any digest comparison on luals, such as MAINT-63's, can see a change
that comes from timing alone. This is a separate defect, filed as BUG-104, and is not changed here.

### 7.2 The next recursion limit is `VariableElement.resolveRead`
With the fix and a 256 KB stack, `compiler.lua` still overflows, now in
`VariableElement.resolveRead` (`LuaTypeNodes.kt:226-247`, recursing at `:234`). It completes at 1 MB
in every run above. This is not the sweep's failure, and its margin under an interpreted frame was
not measured.
