package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaFuncNameProperty
import net.internetisalie.lunar.lang.psi.LuaIndexExpr

/**
 * TYPE-13 design §3.5: the declaration handle for a structurally-resolved [LuaTypeMember]. Its only
 * production caller inside this feature is `LuaTypesSnapshot.putGraphMember` (design §4.3); Phase 2
 * exercises it directly, ahead of that wiring (design §3.5 — Phase 3 owns wiring it into
 * `tableToLuaType`).
 */
object LuaMemberDeclarations {
    private const val MAX_VISITED = 64

    /**
     * The nearest node bound to this member that was minted at a declaration site, or null.
     *
     * Reads [VariableNode.upSet] edges only — never `write`, `read` or `declaredDemand`, each of
     * which opens a resolution walk root and would be charged against BUG-473's budget.
     */
    internal fun declaringNodeOf(node: VariableNode): VariableNode? {
        val visited = mutableSetOf<TypeNode>()
        var frontier = listOf<TypeNode>(node)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<TypeNode>()
            for (candidate in frontier) {
                if (visited.size >= MAX_VISITED) return null
                if (!visited.add(candidate)) continue
                if (candidate is VariableNode) {
                    if (candidate.declaresMember) return candidate
                    candidate.upSet.forEach { next += it }
                }
            }
            frontier = next
        }
        return null
    }

    /**
     * The PSI a consumer should treat as this member's declaration, or null when it has none.
     * Requires a read action: this is a PSI parent walk.
     */
    fun declarationOf(member: LuaTypeMember): PsiElement? {
        val anchor = member.sourceElement ?: return null
        return when (anchor) {
            is LuaFuncNameMethod, is LuaFuncNameProperty ->
                PsiTreeUtil.getParentOfType(anchor, LuaFuncDecl::class.java, true)
            is LuaIndexExpr ->
                PsiTreeUtil.getParentOfType(anchor, LuaAssignmentStatement::class.java, true)
            else -> anchor
        }
    }
}
