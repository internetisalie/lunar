package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Base for all nodes in the type graph. Every node is associated with the PSI element
 * that caused its creation (for error reporting and graph node identity).
 *
 * See: docs/requirements/spec/type/design/phase-1-api-contracts.md §3
 */
sealed interface TypeNode {
    val element: PsiElement
}

/**
 * A node that produces a value — the positive polarity side.
 * Holds the concrete type this node asserts.
 */
interface ValueNode : TypeNode {
    val write: LuaGraphType

    /**
     * True for values that state a DECLARATION rather than a runtime write — a `---@type`/`@param`
     * annotation, a cross-file declared-type seed. The certainty rule (BUG-416) counts reaching
     * definitions, and a declared bound is not one: `---@type string` + `local x = nil` has exactly
     * one reaching definition (the nil), and it must stay certain despite the annotation value.
     */
    val declaredOrigin: Boolean get() = false

    /**
     * [write], resolved while carrying the caller's cycle-guard set (BUG-390).
     *
     * A node that can re-enter the graph — [LazyValueElement], whose `compute` walks back to a
     * receiver — **must** override this and pass [visited] onward. Resolving through plain [write]
     * instead starts a fresh guard, so a cycle that routes through a lazy node never terminates.
     * The default is correct only for nodes that hold a type outright and cannot recurse.
     */
    fun writeWith(visited: MutableSet<VariableNode>): LuaGraphType = write
}

/**
 * A node that consumes a value — the negative polarity side.
 * Holds the type constraint this usage site demands.
 */
interface UseNode : TypeNode {
    val read: LuaGraphType

    /**
     * True for demands the USER declared — a `---@param`/`---@type` annotation, a stub signature.
     * False for demands the engine synthesized from usage: `frame:SetStatusText()` manufacturing a
     * `Table{SetStatusText}`, or `a .. b` manufacturing a `String`.
     *
     * This separates a contract from a guess (BUG-419). A value conflicting with a *declared* demand
     * is a diagnostic; a value conflicting with an *inferred* demand is two of the engine's own
     * guesses disagreeing — evidence the model is incomplete, not that the code is wrong. Measured
     * across four corpus members: 7 430 of 7 433 emissions were inferred-vs-inferred.
     *
     * Named apart from [ValueNode.declaredOrigin] deliberately: [VariableNode] is both a value and a
     * use, and the two provenances are independent.
     */
    val declaredDemand: Boolean get() = false
}

/**
 * A mutable variable binding. Simultaneously a [ValueNode] (its resolved type is the
 * union of all values written to it) and a [UseNode] (its demand is the intersection
 * of all reads from it).
 *
 * Implements the "wormhole" invariant: anything assigned to this variable (flowing into
 * [upSet]) must be compatible with anything read from it (flowing into [downSet]).
 */
interface VariableNode :
    ValueNode,
    UseNode {
    /** All upstream nodes that flow values *into* this variable. */
    val upSet: OrderedSet<TypeNode>

    /** All downstream nodes that draw values *from* this variable. */
    val downSet: OrderedSet<TypeNode>

    /**
     * True when this node was minted to bind a member name that Lua source DECLARES, rather than
     * one a use site demands. Set at the mint site (TYPE-13 design §3.2), never inferred from
     * [TypeNode.element]'s PSI class: `function t.m()` and `t.m = …` mint different classes, and a
     * `LuaIndexExpr` is a declaration or a use depending on its position.
     */
    val declaresMember: Boolean get() = false
}

// ---------------------------------------------------------------------------
// Concrete implementations — internal to the inference engine
// ---------------------------------------------------------------------------

/** Immutable typed value. Created by [LuaTypeGraph.value] and [LuaTypeGraph.nil]. */
internal class ValueElement(
    override val element: PsiElement,
    override val write: LuaGraphType,
    override val declaredOrigin: Boolean = false,
) : ValueNode

/**
 * TYPE-10 §3.4: a [ValueNode] whose [write] is computed lazily at read time. Used by
 * `seedSubscriptElement` so a subscript's element type is a projection over the receiver's
 * (lazy) `write`, resolved after the full traversal + `checkTypes()` — by which point a
 * later-added seed edge into the receiver is already visible. Created by [LuaTypeGraph.lazyValue].
 */
internal class LazyValueElement(
    override val element: PsiElement,
    private val compute: (MutableSet<VariableNode>) -> LuaGraphType,
) : ValueNode {
    override val write: LuaGraphType get() = compute(mutableSetOf())

    // BUG-390: forwarding the caller's guard is the whole point of this override. Without it the
    // hop through compute() restarts with an empty set and a variable → subscript → same-variable
    // cycle recurses until the stack is exhausted.
    override fun writeWith(visited: MutableSet<VariableNode>): LuaGraphType = compute(visited)
}

/** Immutable typed constraint. Created by [LuaTypeGraph.use]. */
internal class UseElement(
    override val element: PsiElement,
    override val read: LuaGraphType,
    override val declaredDemand: Boolean = false,
) : UseNode

/** Mutable variable binding. Created by [LuaTypeGraph.variable]. */
internal class VariableElement(
    override val element: PsiElement,
    private val graph: LuaTypeGraph,
    override val declaresMember: Boolean = false,
) : VariableNode {
    override val upSet: OrderedSet<TypeNode> = OrderedSet(graph::onEdgeAdded)
    override val downSet: OrderedSet<TypeNode> = OrderedSet(graph::onEdgeAdded)

    private val writeMemo = RootMemo<LuaGraphType>()
    private val readMemo = RootMemo<LuaGraphType>()
    private val declaredDemandMemo = RootMemo<Boolean>()

    override val write: LuaGraphType
        get() = atRoot(writeMemo, RootAccessor.WRITE) { resolveWrite(mutableSetOf()) }

    override val read: LuaGraphType
        get() = atRoot(readMemo, RootAccessor.READ) { resolveRead(mutableSetOf()) }

    // BUG-390 in reverse: this entry carries the CALLER's guard, so its result is walk-relative and
    // the memo must not be consulted here. Doing so would return a full type where the guard
    // requires the neutral element, and a cycle would resolve to a type instead of Undefined.
    override fun writeWith(visited: MutableSet<VariableNode>): LuaGraphType = resolveWrite(visited)

    /**
     * A variable's demand is the intersection of everything read from it, so it is DECLARED if any
     * of those reads is (BUG-419).
     *
     * Without this the flag was silently lost at exactly the case that must keep erroring. A call
     * argument does not meet the `@param` use node directly — `checkFunctionCompatibility` wires
     * argument-variable → parameter-variable, so the pair actually checked is (value, *variable*),
     * and [VariableNode] is itself a [UseNode]. Inheriting the `false` default demoted every
     * declared-contract violation reached through a call to a hypothesis.
     */
    override val declaredDemand: Boolean
        get() = atRoot(declaredDemandMemo, RootAccessor.DECLARED_DEMAND) { resolveDeclaredDemand(mutableSetOf()) }

    /**
     * BUG-473: the only place a [RootMemo] is read or written.
     *
     * [resolve] opens a walk with a freshly allocated, empty guard, so at this entry — and only at
     * this entry — the result is a pure function of the graph's node and edge state and stays valid
     * until [LuaTypeGraph.revision] moves. Every interior entry ([writeWith], and the recursions in
     * [resolveRead] / [resolveDeclaredDemand]) inherits a caller's guard and deliberately bypasses
     * this helper. The revision is read BEFORE resolving so a graph mutated mid-walk invalidates the
     * entry it produced rather than certifying it.
     */
    private inline fun <T : Any> atRoot(
        memo: RootMemo<T>,
        accessor: RootAccessor,
        resolve: () -> T,
    ): T {
        val revisionAtEntry = graph.revision
        memo.valueAt(revisionAtEntry)?.let { return it }
        graph.recordRootResolution(accessor)
        val resolved = resolve()
        memo.store(revisionAtEntry, resolved)
        return resolved
    }

    private fun resolveDeclaredDemand(visited: MutableSet<VariableNode>): Boolean {
        if (!visited.add(this)) return false
        return downSet.any {
            when (it) {
                is VariableElement -> it.resolveDeclaredDemand(visited)
                is UseNode -> it.declaredDemand
                else -> false
            }
        }
    }

    private fun resolveWrite(visited: MutableSet<VariableNode>): LuaGraphType {
        val walk = WriteWalk(visited)
        walk.collect(this)
        graph.recordWriteInsertions(walk.insertions)
        return walk.result()
    }

    private fun resolveRead(visited: MutableSet<VariableNode>): LuaGraphType {
        if (!visited.add(this)) return LuaGraphType.Any

        val demands =
            downSet
                .asSequence()
                .map {
                    when (it) {
                        is VariableElement -> it.resolveRead(visited)
                        // The trait is deliberately PRESERVED here. It is a demand, and `read` is
                        // what the checker examines for a value reaching an operator through a
                        // variable — projecting it to its primitive at this hop lost the metamethod
                        // arm for every such value (BUG-424), which is the same shape as BUG-419's
                        // declaredDemand defect. Projection happens at the presentation boundary
                        // instead: LuaTypes.typeOf and the inlay-hint providers.
                        is UseNode -> it.read
                        else -> LuaGraphType.Any
                    }
                }.filter { it != LuaGraphType.Any }
                .toList()

        // BUG-395: member demands accumulate. Each `x.f` records a separate one-member Table
        // constraint, so taking only the first would describe `x` by whichever member happened to be
        // written first — a stdlib stub declaring ten `function table.<name>` produced a `table` with
        // exactly one member. Every other kind of demand keeps first-wins.
        val tables = demands.filterIsInstance<LuaGraphType.Table>()
        // BUG-553: a member demand is a hypothesis about the value, an operator trait that admits no
        // table is evidence against it. `x:sub()` beside `"a" .. x` describes a string, so the trait
        // outranks every table demand, whichever came first and however many there are.
        if (tables.isNotEmpty()) tableExcludingTrait(demands)?.let { return it }
        if (tables.size > 1) return mergeTableDemands(tables)
        return demands.firstOrNull() ?: LuaGraphType.Any
    }

    private fun tableExcludingTrait(demands: List<LuaGraphType>): LuaGraphType.Trait? =
        demands
            .filterIsInstance<LuaGraphType.Trait>()
            .firstOrNull { trait -> trait.admits.none { it is LuaGraphType.Table } }

    private fun mergeTableDemands(tables: List<LuaGraphType.Table>): LuaGraphType.Table {
        val members = LinkedHashMap<String, VariableNode>()
        val superTypes = mutableListOf<LuaGraphType>()
        tables.forEach { table ->
            table.localMembers.forEach { (name, node) -> members.putIfAbsent(name, node) }
            table.superTypes.forEach { if (it !in superTypes) superTypes.add(it) }
        }
        return LuaGraphType.Table(
            className = tables.firstNotNullOfOrNull { it.className },
            localMembers = members,
            superTypes = superTypes,
            isExact = tables.all { it.isExact },
        )
    }
}

/**
 * One `write` walk (BUG-96): every value reaching the root, flattened in first-encounter order.
 *
 * The walk keeps one result set, not one per visited variable. Up-sets are transitively closed, so
 * in a cluster of variables wired both ways each up-set holds every value: a set per variable hashed
 * each value once per variable and again at every level it was returned through, and hashing a
 * [LuaGraphType.Table] walks its member map. A variable's set held its subtree's first encounters
 * in order, so its caller's set held the same types in the same order as this single set does. A
 * [ValueElement] already met holds the same type and is skipped. Variables are visited in the same
 * order as before, so a lazy node sees the same guard.
 *
 * Every branch carries [visited] on. A [LazyValueElement] gets it through `writeWith`, which keeps
 * BUG-390's lazy hop from starting a fresh guard; its own read is a separate walk, because it
 * returns a projection of the type it reads rather than that type.
 */
private class WriteWalk(
    private val visited: MutableSet<VariableNode>,
) {
    private val types = LinkedHashSet<LuaGraphType>()
    private val seenValues: MutableSet<ValueElement> = Collections.newSetFromMap(IdentityHashMap())

    /** How many types were offered to [types]; the meter for the regression test. */
    var insertions = 0
        private set

    fun collect(variable: VariableElement) {
        if (!visited.add(variable)) return
        for (node in variable.upSet) {
            when (node) {
                is VariableElement -> collect(node)
                is ValueElement -> if (seenValues.add(node)) flatten(node.write)
                is ValueNode -> flatten(node.writeWith(visited))
                else -> Unit
            }
        }
    }

    private fun flatten(type: LuaGraphType) {
        when (type) {
            is LuaGraphType.Union -> type.types.forEach { flatten(it) }
            LuaGraphType.Undefined -> Unit
            else -> {
                insertions++
                types.add(type)
            }
        }
    }

    fun result(): LuaGraphType =
        when {
            types.isEmpty() -> LuaGraphType.Undefined
            types.size == 1 -> types.first()
            else -> LuaGraphType.Union(types)
        }
}

/**
 * An ordered set: preserves insertion order for deterministic iteration
 * while providing O(1) membership testing. Required by the O(n³) reachability algorithm
 * to ensure edge propagation is deterministic across runs.
 *
 * [onAdd] fires on every successful insertion. `VariableElement` passes
 * [LuaTypeGraph.onEdgeAdded], which counts the edge and bumps the revision. That makes "an edge
 * addition invalidates the walk-root memos"
 * (BUG-473) a property of the set itself rather than a checklist of call sites — the up/down sets
 * are mutated directly, bypassing `addEdge`, by `LuaGraphType.memberNodeFor` and by graph-building
 * tests, and an enumerated key would have to name each one.
 */
class OrderedSet<T>(
    private val onAdd: () -> Unit = {},
) : Iterable<T> {
    private val set = LinkedHashSet<T>()

    /** Returns true if [item] was newly added (false if already present). */
    fun add(item: T): Boolean {
        if (!set.add(item)) return false
        onAdd()
        return true
    }

    operator fun contains(item: T): Boolean = item in set

    override fun iterator(): Iterator<T> = set.iterator()

    val size: Int get() = set.size
}

/**
 * The three [VariableNode] accessors that open a resolution walk. Names the meter
 * [LuaTypeGraph.rootResolutionCount] reports and the memo slot [VariableElement] validates.
 */
internal enum class RootAccessor { WRITE, READ, DECLARED_DEMAND }

/**
 * One-slot memo for a walk root, valid only at the [LuaTypeGraph.revision] it was computed under.
 *
 * The entry is a single reference to an immutable pair, so a concurrent reader sees either the
 * previous pair or the new one and never a torn half; a stale read costs one recomputation and
 * cannot yield a wrong answer. Splitting it into a revision field and a value field would break
 * that, and the snapshot is read from the annotator, completion and inlay-hint paths.
 */
internal class RootMemo<T : Any> {
    private var entry: Pair<Long, T>? = null

    fun valueAt(revision: Long): T? = entry?.takeIf { it.first == revision }?.second

    fun store(
        revision: Long,
        value: T,
    ) {
        entry = revision to value
    }
}
