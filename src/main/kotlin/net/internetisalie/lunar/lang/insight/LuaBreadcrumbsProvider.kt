package net.internetisalie.lunar.lang.insight

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.lang.psi.*
import javax.swing.Icon

class LuaBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<LuaLanguage> = arrayOf(LuaLanguage)

    override fun acceptElement(element: PsiElement): Boolean {
        return element is LuaFuncDecl || element is LuaLocalFuncDecl || element is LuaFile || element is LuaFuncDef
    }

    override fun getElementInfo(element: PsiElement): String {
        return when (element) {
            is LuaFile -> element.name
            is LuaFuncDecl -> element.funcName.text
            is LuaLocalFuncDecl -> element.nameRef.text
            is LuaFuncDef -> getAssignedName(element) ?: "function"
            else -> ""
        }
    }

    override fun getIcon(element: PsiElement): Icon? {
        return when (element) {
            is LuaFile -> LuaIcons.FILE
            is LuaFuncDecl, is LuaLocalFuncDecl, is LuaFuncDef -> AllIcons.Nodes.Function
            else -> null
        }
    }

    private fun getAssignedName(funcDef: LuaFuncDef): String? {
        val parent = funcDef.parent
        if (parent is LuaField) {
            val identifier = parent.identifier
            if (identifier != null) return identifier.text

            val exprs = parent.exprList
            if (exprs.size >= 2) {
                // [key] = value
                return "[${exprs[0].text}]"
            }
        }

        val exprList = parent as? LuaExprList ?: return null
        val index = exprList.exprList.indexOf(funcDef)
        if (index == -1) return null

        return when (val grandParent = exprList.parent) {
            is LuaLocalVarDecl -> {
                val attNames = grandParent.attNameList
                if (index < attNames.size) attNames[index].nameRef.text else null
            }
            is LuaAssignmentStatement -> {
                val vars = grandParent.varList.varList
                if (index < vars.size) vars[index].text else null
            }
            else -> null
        }
    }
}
