package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * BUG-112: the severity of an assignability mismatch is a property of the pair, not of the visit that
 * happened to examine it first.
 *
 * [LuaTypeGraph.checkNodePairs] examines each `(value, use)` pair once, in the first variable that
 * holds both, and reads `unknownProvenance` off that variable's up-set. A pair is held by every
 * variable between the value and the use, and those differ in whether a sibling write is unknown, so
 * the first visit decided. A claim records what a declared-demand mismatch would be worth as an
 * ERROR, and [resolve] settles it once the graph is final: HYPOTHESIS when any variable holding the
 * pair has an unknown write, ERROR otherwise.
 *
 * One diagnostic can be claimed by several pairs (it is keyed by element and message, and
 * [LuaTypeGraph.addError] keeps the first). It is an ERROR only when every pair that yields it is, so
 * the one that arrived first no longer decides.
 */
internal class LuaSeverityClaims {
    private class Claim(
        val valueNode: ValueNode,
        val useNode: UseNode,
        val element: PsiElement,
        val message: String,
        val inferredValueType: String?,
        var demoted: Boolean,
    )

    private val claims = mutableListOf<Claim>()
    private val byValue = HashMap<ValueNode, MutableList<Claim>>()

    /** The number of claims so far, to mark where one pair's claims begin. */
    val size: Int get() = claims.size

    /** A mismatch anchored at [element] with [message]; [inferredValueType] is what a hypothesis carries. */
    class Mismatch(
        val element: PsiElement,
        val message: String,
        val inferredValueType: String?,
    )

    /** [declaredDemand] false means the pair can never be better than a hypothesis, which it claims by being demoted. */
    fun record(
        pair: Pair<ValueNode, UseNode>,
        mismatch: Mismatch,
        declaredDemand: Boolean,
    ) = add(
        Claim(pair.first, pair.second, mismatch.element, mismatch.message, mismatch.inferredValueType, !declaredDemand),
    )

    /** Re-claims, at [site], what the pair's claims from [fromIndex] on anchored at [hub]. */
    fun replicate(
        fromIndex: Int,
        hub: PsiElement,
        site: PsiElement,
    ) {
        val anchored = claims.subList(fromIndex, claims.size).filter { it.element === hub }
        for (claim in anchored) {
            add(
                Claim(claim.valueNode, claim.useNode, site, claim.message, claim.inferredValueType, claim.demoted),
            )
        }
    }

    private fun add(claim: Claim) {
        claims += claim
        byValue.getOrPut(claim.valueNode) { mutableListOf() } += claim
    }

    /** What a claimed diagnostic settles to: [ErrorSeverity.ERROR], or a hypothesis carrying its inferred type. */
    class Verdict(
        val severity: ErrorSeverity,
        val inferredValueType: String?,
    )

    /**
     * Demotes every claim held by a variable with an unknown write in its up-set, and returns the
     * verdict for each claimed `(element, message)`: ERROR only while none of its claims is demoted.
     */
    fun resolve(
        variables: Sequence<VariableElement>,
        isUnknownWrite: (ValueNode) -> Boolean,
    ): Map<Pair<PsiElement, String>, Verdict> {
        for (variable in variables) demoteHeldBy(variable, isUnknownWrite)
        return claims.groupBy { it.element to it.message }.mapValues { (_, group) -> verdictOf(group) }
    }

    private fun verdictOf(group: List<Claim>): Verdict =
        if (group.none { it.demoted }) {
            Verdict(ErrorSeverity.ERROR, null)
        } else {
            Verdict(ErrorSeverity.HYPOTHESIS, group.first().inferredValueType)
        }

    private fun demoteHeldBy(
        variable: VariableElement,
        isUnknownWrite: (ValueNode) -> Boolean,
    ) {
        val candidates = variable.upSet.flatMap { node -> (node as? ValueNode)?.let { byValue[it] }.orEmpty() }
        if (candidates.isEmpty()) return
        if (variable.upSet.none { it is ValueNode && isUnknownWrite(it) }) return
        for (claim in candidates) if (claim.useNode in variable.downSet) claim.demoted = true
    }
}
