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
