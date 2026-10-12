package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * TYPE-12: the graph-side record of which colon-call site members hang off which hub, and at which
 * PSI anchors each hub's sites sit.
 *
 * The first site member minted for a `(receiver node, method name)` pair is that pair's hub; only
 * the hub is ever wired bidirectionally into a class member, and every later site member hangs off
 * it by one forward edge. Without this, N call sites on one `---@class` receiver formed an N-way
 * clique through the class member and the fixed-point checker walked its Θ(n²) cross-product.
 *
 * Owned by one [LuaTypeGraph] and built on that graph's thread; holds only nodes and PSI the graph
 * already holds. Both maps are identity-keyed on purpose, so they stay correct even if a node type
 * ever overrides `equals`.
 */
internal class LuaMemberDemandHubs {
    private val hubOfSite = java.util.IdentityHashMap<TypeNode, VariableNode>()
    private val siteAnchors = java.util.IdentityHashMap<TypeNode, MutableList<PsiElement>>()

    /** Records [siteMember] as a later site of [hub]. Never called for the hub itself. */
    fun attachSite(
        hub: VariableNode,
        siteMember: VariableNode,
    ) {
        hubOfSite[siteMember] = hub
    }

    /** Appends [anchor] to [hub]'s site anchors. The hub's own anchor is recorded first. */
    fun recordSiteAnchor(
        hub: VariableNode,
        anchor: PsiElement,
    ) {
        siteAnchors.getOrPut(hub) { mutableListOf() }.add(anchor)
    }

    /** The node `checkTableCompatibility` wires in place of [memberNode]. */
    fun wiringTargetFor(memberNode: VariableNode): VariableNode = hubOfSite[memberNode] ?: memberNode

    /** Anchors of every site of [node] except its first; empty unless [node] is a hub with ≥ 2 sites. */
    fun replicaAnchorsFor(node: TypeNode): List<PsiElement> {
        val anchors = siteAnchors[node] ?: return emptyList()
        return if (anchors.size < 2) emptyList() else anchors.subList(1, anchors.size).toList()
    }
}
