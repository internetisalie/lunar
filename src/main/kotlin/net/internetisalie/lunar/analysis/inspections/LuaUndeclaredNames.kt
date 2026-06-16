package net.internetisalie.lunar.analysis.inspections

import com.intellij.psi.PsiPolyVariantReference
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Shared "is this name undeclared?" decision, extracted from [LuaUndeclaredVariableInspection]
 * (REFACT-06-00-DR-01) so the inspection and the create-from-usage intentions
 * (`LuaCreateLocalVariableIntention`, `LuaCreateFunctionIntention`) share a single verdict.
 *
 * Mirrors the inspection's resolve + exemption logic: a name is undeclared when its
 * `LuaNameReference` resolves to nothing and the name is not an exempt global
 * (standard global, allowlisted, or underscore-suppressed).
 */
object LuaUndeclaredNames {

    /** True iff [ref]'s name resolves to nothing and is not an exempt global. */
    fun isUnresolvedNonGlobal(ref: LuaNameRef): Boolean {
        val name = ref.identifier.text
        if (name == "_") return false
        if (isExemptGlobal(ref, name)) return false
        val reference = ref.reference as? PsiPolyVariantReference ?: return false
        return reference.multiResolve(false).isEmpty()
    }

    private fun isExemptGlobal(ref: LuaNameRef, name: String): Boolean {
        val settings = LuaProjectSettings.getInstance(ref.project)
        val level = settings.state.languageLevel
        if (LuaStandardGlobals.contains(name, level)) return true
        if (name in settings.state.additionalGlobals) return true
        if (settings.suppressUnderscorePrefixedGlobals && name.startsWith("_")) return true
        return false
    }
}
