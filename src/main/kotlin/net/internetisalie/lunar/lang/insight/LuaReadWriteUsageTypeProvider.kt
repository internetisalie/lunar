package net.internetisalie.lunar.lang.insight

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarList

/**
 * Classifies a Lua name usage as READ or WRITE.
 *
 * A usage is WRITE when the enclosing [LuaNameRef] is a bare assignment target:
 *   [LuaVar] with no varSuffixList entries inside a [LuaVarList] of a
 *   [LuaAssignmentStatement].  An index-base like `a` in `a.b = 1` has
 *   varSuffix entries so it remains READ.
 *
 * Reusable predicate: [isWriteTarget] — called by this provider and can be
 * used by NAV-10 inspection logic.
 */
class LuaReadWriteUsageTypeProvider : UsageTypeProvider {

    override fun getUsageType(element: PsiElement): UsageType? {
        val nameRef = PsiTreeUtil.getParentOfType(element, LuaNameRef::class.java, false) ?: return null
        return if (isWriteTarget(nameRef)) UsageType.WRITE else UsageType.READ
    }

    companion object {
        /**
         * Returns true when [nameRef] is the bare left-hand-side of an assignment:
         * its parent is a [LuaVar] with no suffix, that var is in a [LuaVarList]
         * of a [LuaAssignmentStatement].
         */
        fun isWriteTarget(nameRef: LuaNameRef): Boolean {
            val luaVar = nameRef.parent as? LuaVar ?: return false
            if (luaVar.varSuffixList.isNotEmpty()) return false
            val varList = luaVar.parent as? LuaVarList ?: return false
            return varList.parent is LuaAssignmentStatement
        }
    }
}
