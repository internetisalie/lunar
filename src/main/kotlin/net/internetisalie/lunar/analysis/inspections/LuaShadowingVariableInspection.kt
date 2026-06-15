package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.*

class LuaShadowingVariableInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaShadowingVariable"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Shadowing variable"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitLocalVarDecl(o: LuaLocalVarDecl) {
                o.attNameList.forEach { attName ->
                    val identifier = attName.nameRef.identifier
                    inspectIdentifier(identifier, holder)
                }
            }

            override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) {
                val identifier = o.nameRef.identifier
                inspectIdentifier(identifier, holder)
            }

            override fun visitParList(o: LuaParList) {
                o.nameList?.nameRefList?.forEach { nameRef ->
                    val identifier = nameRef.identifier
                    inspectIdentifier(identifier, holder)
                }
            }

            override fun visitNumericForStatement(o: LuaNumericForStatement) {
                val identifier = o.identifier
                if (identifier != null) {
                    inspectIdentifier(identifier, holder)
                }
            }

            override fun visitGenericForStatement(o: LuaGenericForStatement) {
                o.nameList.nameRefList.forEach { nameRef ->
                    val identifier = nameRef.identifier
                    inspectIdentifier(identifier, holder)
                }
            }
        }

    private fun inspectIdentifier(identifier: PsiElement, holder: ProblemsHolder) {
        val name = identifier.text
        if (name == "_" || name.isEmpty()) return

        val processor = ShadowingResolveProcessor(name, identifier)
        LuaResolveUtil.scopeCrawlUp(processor, identifier)

        if (processor.result != null) {
            holder.registerProblem(
                identifier,
                "Shadowing variable '$name'",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }
    }
}

private class ShadowingResolveProcessor(val name: String, val startElement: PsiElement) : PsiScopeProcessor {
    var result: PsiElement? = null
        private set
    private var found = false

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if (found) return false

        when (element) {
            is LuaLocalVarDecl -> {
                for (attName in element.attNameList) {
                    val identifier = attName.nameRef.identifier
                    if (identifier.textOffset >= startElement.textOffset) {
                        break
                    }
                    if (identifier.text == name) {
                        result = identifier
                        found = true
                        return false
                    }
                }
            }
            is LuaLocalFuncDecl -> {
                val identifier = element.nameRef.identifier
                if (identifier.textOffset >= startElement.textOffset) {
                    return true
                }
                if (identifier.text == name) {
                    result = identifier
                    found = true
                    return false
                }
            }
            is LuaParList -> {
                val nameList = element.nameList
                if (nameList != null) {
                    for (nameRef in nameList.nameRefList) {
                        val identifier = nameRef.identifier
                        if (identifier.textOffset >= startElement.textOffset) {
                            break
                        }
                        if (identifier.text == name) {
                            result = identifier
                            found = true
                            return false
                        }
                    }
                }
            }
            is LuaNumericForStatement -> {
                val identifier = element.identifier
                if (identifier != null) {
                    if (identifier.textOffset >= startElement.textOffset) {
                        return true
                    }
                    if (identifier.text == name) {
                        result = identifier
                        found = true
                        return false
                    }
                }
            }
            is LuaGenericForStatement -> {
                for (nameRef in element.nameList.nameRefList) {
                    val identifier = nameRef.identifier
                    if (identifier.textOffset >= startElement.textOffset) {
                        break
                    }
                    if (identifier.text == name) {
                        result = identifier
                        found = true
                        return false
                    }
                }
            }
        }
        return true
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}
