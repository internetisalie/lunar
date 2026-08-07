package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

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
) : UseNode

/** Mutable variable binding. Created by [LuaTypeGraph.variable]. */
internal class VariableElement(
    override val element: PsiElement,
) : VariableNode {
    override val upSet: OrderedSet<TypeNode> = OrderedSet()
    override val downSet: OrderedSet<TypeNode> = OrderedSet()

    override val write: LuaGraphType get() = resolveWrite(mutableSetOf())
    override val read: LuaGraphType get() = resolveRead(mutableSetOf())

    override fun writeWith(visited: MutableSet<VariableNode>): LuaGraphType = resolveWrite(visited)

    private fun resolveWrite(visited: MutableSet<VariableNode>): LuaGraphType {
        if (!visited.add(this)) return LuaGraphType.Undefined

        val types = mutableSetOf<LuaGraphType>()

        fun flatten(type: LuaGraphType) {
            if (type is LuaGraphType.Union) {
                type.types.forEach { flatten(it) }
            } else if (type != LuaGraphType.Undefined) {
                types.add(type)
            }
        }

        upSet.forEach {
            // One branch, deliberately: VariableElement overrides writeWith to continue the walk
            // with this guard, and every other ValueNode either forwards it (LazyValueElement) or
            // holds a type outright. Re-splitting this into a `is VariableElement` special case is
            // what let BUG-390's lazy hop escape the guard.
            val type = if (it is ValueNode) it.writeWith(visited) else LuaGraphType.Undefined
            flatten(type)
        }

        return when {
            types.isEmpty() -> LuaGraphType.Undefined
            types.size == 1 -> types.first()
            else -> LuaGraphType.Union(types)
        }
    }

    private fun resolveRead(visited: MutableSet<VariableNode>): LuaGraphType {
        if (!visited.add(this)) return LuaGraphType.Any

        val demands =
            downSet
                .asSequence()
                .map {
                    when (it) {
                        is VariableElement -> it.resolveRead(visited)
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
        if (tables.size > 1) return mergeTableDemands(tables)
        return demands.firstOrNull() ?: LuaGraphType.Any
    }

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
 * An ordered set: preserves insertion order for deterministic iteration
 * while providing O(1) membership testing. Required by the O(n³) reachability algorithm
 * to ensure edge propagation is deterministic across runs.
 */
class OrderedSet<T> : Iterable<T> {
    private val set = LinkedHashSet<T>()

    /** Returns true if [item] was newly added (false if already present). */
    fun add(item: T): Boolean = set.add(item)

    operator fun contains(item: T): Boolean = item in set

    override fun iterator(): Iterator<T> = set.iterator()

    val size: Int get() = set.size
}
