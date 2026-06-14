package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPolyVariantReference
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.lang.psi.LuaFuncNameProperty
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVarList
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags read-position [LuaNameRef]s whose name resolves to nothing and is not exempt
 * (standard global, allowlisted global, underscore-suppressed, or comment-suppressed).
 *
 * Resolution is delegated to the existing `LuaNameReference` via [PsiPolyVariantReference.multiResolve];
 * this inspection only adds classification (read vs. declaration vs. write) and exemption logic.
 */
class LuaUndeclaredVariableInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaUndeclaredVariable"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Undeclared variable"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) = inspectNameRef(o, holder)
        }

    private fun inspectNameRef(ref: LuaNameRef, holder: ProblemsHolder) {
        if (!isReadUse(ref)) return
        val name = ref.identifier.text
        if (name == "_") return
        if (isExemptGlobal(ref, name)) return
        val reference = ref.reference as? PsiPolyVariantReference ?: return
        if (reference.multiResolve(false).isNotEmpty()) return
        if (LuaInspectionSuppression.isSuppressed(ref, name, DIAGNOSTIC_ID)) return
        holder.registerProblem(
            ref,
            "Undeclared variable '$name'",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            LuaAddToGlobalsQuickFix(name),
        )
    }

    private fun isExemptGlobal(ref: LuaNameRef, name: String): Boolean {
        val settings = LuaProjectSettings.getInstance(ref.project)
        val level = settings.state.languageLevel
        if (LuaStandardGlobals.contains(name, level)) return true
        if (name in settings.state.additionalGlobals) return true
        if (settings.suppressUnderscorePrefixedGlobals && name.startsWith("_")) return true
        return false
    }

    private fun isReadUse(ref: LuaNameRef): Boolean {
        val parent = ref.parent
        if (parent is LuaAttName) return false
        if (parent is LuaNameList && isDeclarationNameList(parent)) return false
        if (isMemberName(parent)) return false
        if (parent is LuaFuncName) return false
        if (parent is LuaVar && isSimpleWriteTarget(parent)) return false
        return true
    }

    /** A name appearing after `.`/`:` is a member/field/method name, not a free variable. */
    private fun isMemberName(parent: PsiElement): Boolean =
        parent is LuaIndexExpr || parent is LuaMethodExpr ||
            parent is LuaFuncNameProperty || parent is LuaFuncNameMethod

    private fun isDeclarationNameList(nameList: LuaNameList): Boolean {
        val owner = nameList.parent
        return owner is LuaParList || owner is LuaGenericForStatement
    }

    private fun isSimpleWriteTarget(luaVar: LuaVar): Boolean =
        luaVar.varSuffixList.isEmpty() && luaVar.parent is LuaVarList

    companion object {
        private const val DIAGNOSTIC_ID = "undefined-global"
    }
}
