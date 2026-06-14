package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaNumericForStatement
import net.internetisalie.lunar.lang.psi.LuaParList

/**
 * Classifies Lua variable references as Read or Write for platform highlighting
 * and Find Usages grouping (NAV-10-01/02/03/04).
 *
 * Write accesses are:
 *  - bare LHS of an assignment: `x = 1` (delegated to [LuaReadWriteUsageTypeProvider.isWriteTarget])
 *  - declaration binding sites: local attName, parameter, loop variable
 *
 * Lua has no compound-assignment operators (`++`, `+=`), so [Access.ReadWrite] never arises.
 */
class LuaReadWriteAccessDetector : ReadWriteAccessDetector() {

    override fun isReadWriteAccessible(element: PsiElement): Boolean =
        element is LuaNameRef || element.elementType == LuaElementTypes.IDENTIFIER

    override fun isDeclarationWriteAccess(element: PsiElement): Boolean {
        // Normalize to a LuaNameRef regardless of whether the platform passed a leaf
        // IDENTIFIER token or the LuaNameRef node itself.
        val nameRef = element as? LuaNameRef
            ?: if (element.elementType == LuaElementTypes.IDENTIFIER) element.parent as? LuaNameRef
            else null
        if (nameRef != null) return isBindingSiteNameRef(nameRef)
        // numeric-for: IDENTIFIER hangs directly under LuaNumericForStatement (no LuaNameRef wrapper)
        val parent = element.parent ?: return false
        return parent is LuaNumericForStatement
    }

    override fun getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
        getExpressionAccess(reference.element)

    override fun getExpressionAccess(expression: PsiElement): Access {
        val ref = expression as? LuaNameRef
            ?: expression.parent as? LuaNameRef
            ?: return Access.Read
        return if (LuaReadWriteUsageTypeProvider.isWriteTarget(ref)) Access.Write else Access.Read
    }

    /**
     * Returns true when [nameRef] is at a declaration binding site:
     *  - local var:       LuaNameRef → LuaAttName
     *  - parameter:       LuaNameRef → LuaNameList → LuaParList
     *  - generic-for var: LuaNameRef → LuaNameList → LuaGenericForStatement
     */
    private fun isBindingSiteNameRef(nameRef: LuaNameRef): Boolean {
        val parent = nameRef.parent ?: return false
        if (parent is LuaAttName) return true
        val nameList = parent as? LuaNameList ?: return false
        val owner = nameList.parent
        return owner is LuaParList || owner is LuaGenericForStatement
    }
}
