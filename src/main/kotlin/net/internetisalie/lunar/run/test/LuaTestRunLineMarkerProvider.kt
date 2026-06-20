package net.internetisalie.lunar.run.test

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarOrExp
import net.internetisalie.lunar.lang.psi.LuaFuncCall

class LuaTestRunLineMarkerProvider : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        val elementType = element.node.elementType
        if (elementType != LuaElementTypes.IDENTIFIER) return null

        val nameRef = element.parent as? LuaNameRef ?: return null
        val parent = nameRef.parent

        // 1. Lunity function declarations (starting with test_)
        if (parent is LuaFuncName || parent is LuaLocalFuncDecl) {
            if (element.text.startsWith("test_")) {
                return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
            }
        }

        // 2. Busted describe/it/context calls
        val callVar = parent as? LuaVar ?: return null
        val varOrExp = callVar.parent as? LuaVarOrExp ?: return null
        val funcCall = varOrExp.parent as? LuaFuncCall ?: return null

        // Only attach to the first part of the call
        if (funcCall.varOrExp != varOrExp) return null
        if (callVar.nameRef != nameRef) return null

        val calleeText = element.text
        if (calleeText == "describe" || calleeText == "it" || calleeText == "context") {
            return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
        }

        return null
    }
}
