package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.psi.*

class LuaLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val elementType = element.node.elementType

        if (elementType == LuaElementTypes.IDENTIFIER) {
            val nameRef = element.parent as? LuaNameRef ?: return null
            val callVar = nameRef.parent as? LuaVar ?: return null
            val varOrExp = callVar.parent as? LuaVarOrExp ?: return null
            val funcCall = varOrExp.parent as? LuaFuncCall ?: return null

            // Only attach to the first part of the call (e.g., in f.g(), attach to f)
            if (funcCall.varOrExp != varOrExp) return null
            // And only to the first identifier of the var
            if (callVar.nameRef != nameRef) return null

            // Recursive call check
            val enclosingFunc = PsiTreeUtil.getParentOfType(funcCall,
                LuaFuncDecl::class.java,
                LuaLocalFuncDecl::class.java,
                LuaFuncDef::class.java
            )

            if (enclosingFunc != null) {
                val funcName = when (enclosingFunc) {
                    is LuaFuncDecl -> enclosingFunc.funcName.nameRef.identifier
                    is LuaLocalFuncDecl -> enclosingFunc.nameRef.identifier
                    else -> null // LuaFuncDef is anonymous
                }

                if (funcName != null && element.text == funcName.text) {
                    val reference = nameRef.reference?.resolve()
                    if (reference == funcName) {
                        return LineMarkerInfo(
                            element,
                            element.textRange,
                            AllIcons.Gutter.RecursiveMethod,
                            { LuaBundle.message("gutter.recursive.call") },
                            null,
                            GutterIconRenderer.Alignment.RIGHT,
                            { LuaBundle.message("gutter.recursive.call") }
                        )
                    }
                }
            }
        } else if (elementType == LuaElementTypes.RETURN) {
            val finalStatement = element.parent as? LuaFinalStatement ?: return null
            val exprList = finalStatement.exprList ?: return null
            if (exprList.exprList.size == 1) {
                val funcCall = exprList.exprList[0] as? LuaFuncCall ?: return null
                return LineMarkerInfo(
                    element,
                    element.textRange,
                    AllIcons.Actions.Forward,
                    { LuaBundle.message("gutter.tail.call") },
                    null,
                    GutterIconRenderer.Alignment.RIGHT,
                    { LuaBundle.message("gutter.tail.call") }
                )
            }
        }

        return null
    }
}
