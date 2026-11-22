package net.internetisalie.lunar.lang.psi.types

import java.util.IdentityHashMap

/**
 * BUG-114: whether a Nil is CERTAIN at a use (BUG-416), read off the final graph.
 *
 * A Nil among several reaching definitions is optionality, and flagging it was 959 false positives on
 * one corpus member; with exactly one it is certain. "Reaching definitions" is a fact about a variable's
 * up-set, and a pair is held by every variable between its value and its use, which differ. The first
 * holder in creation order decides, so the answer is a property of the graph and not of the order the
 * pairs were checked in.
 *
 * Built on first use, over the Nil values only: most files never ask.
 */
internal class LuaPairCertainty(
    private val nodes: List<TypeNode>,
) {
    private val holdersByNil: IdentityHashMap<TypeNode, MutableList<VariableElement>> by lazy { buildIndex() }
    private val certainHolders = IdentityHashMap<VariableElement, Boolean>()

    fun isCertain(
        value: ValueNode,
        use: UseNode,
    ): Boolean {
        val target = canonical(use)
        val holder = holdersByNil[value]?.firstOrNull { target in it.downSet } ?: return false
        return certainHolders.getOrPut(holder) { holdsOneDefinition(holder) }
    }

    private fun buildIndex(): IdentityHashMap<TypeNode, MutableList<VariableElement>> {
        val nilNodes = IdentityHashMap<TypeNode, Boolean>()
        for (node in nodes) if (node is ValueNode && node.write == LuaGraphType.Nil) nilNodes[node] = true
        val index = IdentityHashMap<TypeNode, MutableList<VariableElement>>()
        for (holder in nodes) {
            if (holder !is VariableElement || !holder.isRep) continue
            for (entry in holder.upSet) {
                val members = if (entry is VariableElement) entry.clusterMembers() else listOf(entry)
                for (member in members) if (member in nilNodes) index.getOrPut(member) { mutableListOf() } += holder
            }
        }
        return index
    }

    private fun holdsOneDefinition(holder: VariableElement): Boolean =
        expandedUps(holder).count { it is ValueNode && !it.declaredOrigin } == 1

    private fun expandedUps(holder: VariableElement): List<TypeNode> =
        holder.upSet.flatMap { if (it is VariableElement) it.clusterMembers() else listOf(it) }
}
