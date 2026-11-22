package net.internetisalie.lunar.lang.psi.types

import java.util.Collections
import java.util.IdentityHashMap

/**
 * The `(value, use)` pairs a graph's checker has examined (BUG-97). Nodes compare by identity, so
 * each use holds an identity set of the values paired with it. Admitting a pair allocates nothing
 * beyond the set's own growth, where a `Pair` key allocated one object and hashed two for every
 * attempt, and 83 % of attempts on a large file find the pair already examined.
 */
internal class LuaCheckedPairs {
    private val valuesByUse = IdentityHashMap<TypeNode, MutableSet<TypeNode>>()

    /** The number of distinct pairs admitted so far. Only grows. */
    var size = 0
        private set

    /** The values already paired with [useNode], for an inner loop over one use. */
    fun forUse(useNode: UseNode): ExaminedValues = ExaminedValues(valuesByUse.getOrPut(useNode) { identitySet() })

    /**
     * Every admitted pair, uses and then their values in creation order ([creationOrder]), which is
     * also a node's index in [nodes]. The order the pairs were admitted in follows the checker's
     * schedule, and a caller that reports from them must not.
     */
    fun forEachInCreationOrder(
        nodes: List<TypeNode>,
        action: (ValueNode, UseNode) -> Unit,
    ) {
        val uses =
            valuesByUse.keys
                .filterIsInstance<UseNode>()
                .map { creationOrder(it) }
                .toIntArray()
        uses.sort()
        for (usePosition in uses) {
            val use = nodes[usePosition] as? UseNode ?: continue
            val values = valuesByUse[use] ?: continue
            val positions = IntArray(values.size)
            var count = 0
            for (value in values) positions[count++] = creationOrder(value)
            positions.sort()
            for (position in positions) (nodes[position] as? ValueNode)?.let { action(it, use) }
        }
    }

    fun contains(
        valueNode: ValueNode,
        useNode: UseNode,
    ): Boolean = valuesByUse[useNode]?.contains(valueNode) == true

    /** One use's examined values. */
    inner class ExaminedValues(
        private val values: MutableSet<TypeNode>,
    ) {
        /** Records the pair of [valueNode] and this use; false when it was already recorded. */
        fun admit(valueNode: ValueNode): Boolean {
            if (!values.add(valueNode)) return false
            size++
            return true
        }
    }

    private fun identitySet(): MutableSet<TypeNode> = Collections.newSetFromMap(IdentityHashMap())
}
