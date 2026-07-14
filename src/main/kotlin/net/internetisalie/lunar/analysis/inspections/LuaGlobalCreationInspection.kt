package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags assignment-position [LuaNameRef]s that implicitly create global variables (INSP-05).
 */
class LuaGlobalCreationInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaGlobalCreation"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Global creation"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitAssignmentStatement(o: LuaAssignmentStatement) {
                val varList = o.varList.varList
                for (variable in varList) {
                    if (variable.varSuffixList.isEmpty()) {
                        val nameRef = variable.nameRef ?: continue
                        val name = nameRef.identifier.text
                        if (name == "_") continue
                        if (isExemptGlobal(nameRef, name)) continue

                        val reference = nameRef.reference as? PsiPolyVariantReference ?: continue
                        val resolveResults = reference.multiResolve(false)
                        val validResolves = resolveResults.filter {
                            val target = it.element
                            target != nameRef && target != nameRef.identifier
                        }
                        if (validResolves.isEmpty()) {
                            if (LuaInspectionSuppression.isSuppressed(nameRef, name, DIAGNOSTIC_ID)) continue
                            val highlightType = if (isRedisTarget(nameRef.project)) {
                                ProblemHighlightType.ERROR
                            } else {
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            }
                            holder.registerProblem(
                                nameRef,
                                "Global creation '$name'",
                                highlightType,
                                LuaMakeLocalQuickFix(),
                                LuaAddToGlobalsQuickFix(name)
                            )
                        }
                    }
                }
            }
        }

    /** Returns `true` when the project target is a Redis or Valkey platform (design §3.8). */
    private fun isRedisTarget(project: Project): Boolean {
        val platform = LuaProjectSettings.getInstance(project).state.getTarget().platform
        return platform == LuaPlatform.REDIS || platform == LuaPlatform.VALKEY
    }

    private fun isExemptGlobal(ref: LuaNameRef, name: String): Boolean {
        val settings = LuaProjectSettings.getInstance(ref.project)
        val level = settings.state.languageLevel
        if (LuaStandardGlobals.contains(name, level)) return true
        if (name in settings.state.additionalGlobals) return true
        if (settings.suppressUnderscorePrefixedGlobals && name.startsWith("_")) return true
        return false
    }

    companion object {
        private const val DIAGNOSTIC_ID = "undefined-global"
    }
}

class LuaMakeLocalQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Make Local"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val assignStat = PsiTreeUtil.getParentOfType(element, LuaAssignmentStatement::class.java) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Make Local", null, {
            val text = "local " + assignStat.text
            val tempFile = LuaElementFactory.createFile(project, text)
            val newLocalVarDecl = PsiTreeUtil.findChildOfType(tempFile, LuaLocalVarDecl::class.java)
            if (newLocalVarDecl != null) {
                assignStat.replace(newLocalVarDecl)
            }
        })
    }
}
