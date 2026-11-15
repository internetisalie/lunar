package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarList

/**
 * BUG-75: the assignments `m.x = ...` that write one member are separate sites of the type graph, but
 * the class's member node merges them, so a value written at one site also reaches the demand
 * another site declared. Each site is checked on its own as well, so a report that pairs a site's
 * demand with a value that arrived through that merge only repeats the finding on a line that
 * holds no fault.
 */
internal object LuaMemberAssignmentSites {
    /**
     * True when [useElement] is the target (or the whole statement) of a member assignment and
     * [valueElement] is not that assignment's own written value but a node the member merge made:
     * the class member (anchored on the file), or the target or value of another site of the member.
     */
    fun isMergedValue(
        useElement: PsiElement,
        valueElement: PsiElement,
    ): Boolean {
        val useSite = targetStatementOf(useElement) ?: return false
        val memberName = memberNameOf(useSite) ?: return false
        if (valueElement is PsiFile) return true
        if (valueElement is LuaIndexExpr && isTarget(valueElement)) return valueElement.nameRef?.text == memberName
        val valueSite = PsiTreeUtil.getParentOfType(valueElement, LuaAssignmentStatement::class.java, false)
        return valueSite != null && valueSite !== useSite && memberNameOf(valueSite) == memberName
    }

    /** True when [valueElement] is a value written directly to a member by an assignment. */
    fun isWrittenToMember(valueElement: PsiElement): Boolean {
        val statement = PsiTreeUtil.getParentOfType(valueElement, LuaAssignmentStatement::class.java) ?: return false
        if (memberNameOf(statement) == null) return false
        val operand = statement.exprList.exprList.firstOrNull { PsiTreeUtil.isAncestor(it, valueElement, false) }
        return operand != null && (operand === valueElement || operand.children.singleOrNull() === valueElement)
    }

    private fun isTarget(element: PsiElement): Boolean {
        val targetVar = PsiTreeUtil.getParentOfType(element, LuaVar::class.java, false) ?: return false
        return targetVar.parent is LuaVarList
    }

    private fun targetStatementOf(element: PsiElement): LuaAssignmentStatement? {
        if (element is LuaAssignmentStatement) return element
        if (!isTarget(element)) return null
        return PsiTreeUtil.getParentOfType(element, LuaAssignmentStatement::class.java)
    }

    private fun memberNameOf(statement: LuaAssignmentStatement): String? =
        statement.varList.varList
            .firstOrNull()
            ?.varSuffixList
            ?.lastOrNull()
            ?.indexExpr
            ?.nameRef
            ?.text
}
