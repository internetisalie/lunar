package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags Valkey-only API under a Redis project target (REDIS-03 AC-6, AC-7; design §2.7, §3.5):
 * the `server.*` alias namespace and the `SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM`
 * globals are not portable to Redis. Silent under a Valkey target or any non-Redis target.
 */
class LuaValkeyPortabilityInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaValkeyPortability"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Valkey-only API under Redis target"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val target = LuaProjectSettings.getInstance(holder.project).state.getTarget().platform
        if (target != LuaPlatform.REDIS) return PsiElementVisitor.EMPTY_VISITOR
        return object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) {
                val name = o.identifier.text
                if (name in SERVER_GLOBALS && o.parent !is LuaIndexExpr) {
                    holder.registerProblem(
                        o,
                        "`$name` is a Valkey-only global and is not portable to Redis",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                    return
                }
                if (name == SERVER_NAMESPACE && isServerAccessBase(o)) {
                    holder.registerProblem(
                        o,
                        "`server.*` is a Valkey-only namespace and is not portable to Redis",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        LuaValkeyToRedisQuickFix(),
                    )
                }
            }
        }
    }

    private fun isServerAccessBase(baseRef: LuaNameRef): Boolean {
        val luaVar = baseRef.parent as? LuaVar ?: return false
        if (luaVar.nameRef != baseRef) return false
        val firstSuffix = luaVar.varSuffixList.firstOrNull() ?: return false
        return firstSuffix.indexExpr.node.findChildByType(LuaElementTypes.DOT) != null
    }

    private companion object {
        const val SERVER_NAMESPACE = "server"
        val SERVER_GLOBALS = setOf("SERVER_NAME", "SERVER_VERSION", "SERVER_VERSION_NUM")
    }
}
