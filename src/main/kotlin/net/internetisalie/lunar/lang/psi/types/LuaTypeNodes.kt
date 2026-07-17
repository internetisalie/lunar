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
interface VariableNode : ValueNode, UseNode {
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
) : ValueNode

/**
 * TYPE-10 §3.4: a [ValueNode] whose [write] is computed lazily at read time. Used by
 * `seedSubscriptElement` so a subscript's element type is a projection over the receiver's
 * (lazy) `write`, resolved after the full traversal + `checkTypes()` — by which point a
 * later-added seed edge into the receiver is already visible. Created by [LuaTypeGraph.lazyValue].
 */
internal class LazyValueElement(
    override val element: PsiElement,
    private val compute: () -> LuaGraphType,
) : ValueNode {
    override val write: LuaGraphType get() = compute()
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
            val type = when (it) {
                is VariableElement -> it.resolveWrite(visited)
                is ValueNode -> it.write
                else -> LuaGraphType.Undefined
            }
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

        return downSet.asSequence()
            .map {
                when (it) {
                    is VariableElement -> it.resolveRead(visited)
                    is UseNode -> it.read
                    else -> LuaGraphType.Any
                }
            }
            .filter { it != LuaGraphType.Any }
            .firstOrNull() ?: LuaGraphType.Any
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
