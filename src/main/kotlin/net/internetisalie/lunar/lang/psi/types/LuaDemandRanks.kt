package net.internetisalie.lunar.lang.psi.types

import java.util.Collections
import java.util.IdentityHashMap

/**
 * BUG-110: the rank of each demand of one graph, so a variable's `read` picks among the demands it
 * reaches by where the code makes them, never by the order a walk meets them in.
 *
 * A demand the visitor records ranks at its own place in creation order ([UseElement.order]), which is
 * the order the visitor walked the code in. A demand that stands for a converted declaration
 * ([LuaGraphType.fromLuaType]) is created whenever the type is first converted, which says nothing
 * about the code: `local append = table.insert` converts `table.insert`, and its `pos: integer`
 * demand, long before any call passes it an argument. Such a demand ranks at the site that brings the
 * declaration into the code, and ranks last until something does:
 *  - a converted signature's parameter or return ranks where the code is first wired to it — the
 *    walk position of that edge: a call, or a declaration meeting its converted signature. The
 *    variable at the other end is no guide: a function's own parameter and return variables are
 *    created before their `---@param` / `---@return` demands, and ranking there would put the
 *    converted copy of a signature ahead of the annotation it was converted from;
 *  - a converted class member ranks at the variable it is first wired to, the member's declaration or
 *    first access — that variable's creation order. The edge itself comes later, when a call site
 *    joins the member, and ranking there would put the declaration behind the very calls checked
 *    against it.
 *
 * Source offsets are not used: they reorder demands the visitor records out of text order, which moved
 * a luarocks local's display from `any[] | string | { ... }` to `{ ... }`.
 */
internal class LuaDemandRanks {
    private val converted = IdentityHashMap<TypeNode, UseElement>()
    private val convertedUses: MutableSet<UseElement> = Collections.newSetFromMap(IdentityHashMap())
    private val slots: MutableSet<UseElement> = Collections.newSetFromMap(IdentityHashMap())
    private val wiredAt = IdentityHashMap<UseElement, Int>()

    /** Registers [use] as the converted demand of [owner], a signature's parameter or return. */
    fun registerSlot(
        owner: VariableNode,
        use: UseElement,
    ) {
        slots += use
        registerMember(owner, use)
    }

    /** Registers [use] as the converted demand of [owner], a class member's declared type. */
    fun registerMember(
        owner: VariableNode,
        use: UseElement,
    ) {
        converted[owner] = use
        convertedUses += use
    }

    /** Records the direct edge [from] → [to], added at walk position [at]; true when a rank moved. */
    fun noteEdge(
        from: TypeNode,
        to: TypeNode,
        at: Int,
    ): Boolean {
        val movedTo = wire(converted[to], from, at)
        val movedFrom = wire(converted[from], to, at)
        return movedTo || movedFrom
    }

    /** Whether [use] is a class member's declared type, as opposed to a signature slot's or a usage demand. */
    fun isDeclaredMember(use: UseElement): Boolean = use in convertedUses && use !in slots

    /** [use]'s rank: its creation order, or for a converted demand where the code brings it in. */
    fun rankOf(use: UseElement): Int {
        if (use !in convertedUses) return use.order
        return wiredAt[use] ?: Int.MAX_VALUE
    }

    private fun wire(
        use: UseElement?,
        codeNode: TypeNode,
        at: Int,
    ): Boolean {
        if (use == null || codeNode !is VariableElement || codeNode in converted) return false
        val rank = if (use in slots) at else codeNode.order
        if (rank >= rankOf(use)) return false
        wiredAt[use] = rank
        return true
    }
}
