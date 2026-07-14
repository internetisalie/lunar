package net.internetisalie.lunar.analysis.redis

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import net.internetisalie.lunar.analysis.inspections.LuaInspectionSuppression
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaFuncName
import net.internetisalie.lunar.lang.psi.LuaGenericForStatement
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameList
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaParList
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.redis.functions.LuaRedisFunctionLibrary
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags `KEYS` and `ARGV` name references inside a Redis Function library file (design §2.3, §3.4).
 *
 * These globals are EVAL-only; Function callbacks receive their keys and args via the
 * `(keys, args)` callback parameters instead. The inspection is a no-op:
 * - under non-Redis / non-Valkey targets (TC-KEYS-3),
 * - under Redis targets older than 7+ (Functions are Redis 7+ only),
 * - in non-library files — files without a `#!lua name=<lib>` shebang (TC-KEYS-2).
 *
 * Suppressible via the normal [LuaInspectionSuppression] mechanism.
 */
class LuaRedisFunctionKeysInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaRedisFunctionKeys"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val target = LuaProjectSettings.getInstance(holder.project).state.getTarget()
        if (!isSupportedTarget(target)) return PsiElementVisitor.EMPTY_VISITOR
        if (!LuaRedisFunctionLibrary.isLibrary(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return keysArgvVisitor(holder)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun keysArgvVisitor(holder: ProblemsHolder): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) {
                val name = o.identifier.text
                if (name != "KEYS" && name != "ARGV") return
                if (isDeclaration(o)) return
                if (LuaInspectionSuppression.isSuppressed(o, name, DIAGNOSTIC_ID)) return
                holder.registerProblem(
                    o,
                    "'$name' is not available in a Redis Function library; use the callback's 'keys'/'args' parameters",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }

    companion object {
        private const val DIAGNOSTIC_ID = "redis-function-keys"

        /**
         * Returns true when [target] is Redis 7+ or Valkey (both support Functions).
         *
         * Functions were introduced in Redis 7; pre-7 Redis targets and non-Redis targets
         * are excluded (TC-KEYS-3).
         */
        private fun isSupportedTarget(target: Target): Boolean {
            val platform = target.platform
            if (platform == LuaPlatform.VALKEY) return true
            if (platform != LuaPlatform.REDIS) return false
            return target.version.label == "7+"
        }

        /**
         * Mirrors [net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection]'s
         * `isDeclaration` check (`.../LuaDeprecatedApiInspection.kt:59`): skips name-refs in
         * declaration positions (local func, attname, func name, parList, for-each target).
         */
        private fun isDeclaration(ref: LuaNameRef): Boolean {
            val parent = ref.parent ?: return false
            if (parent is LuaLocalFuncDecl) return true
            if (parent is LuaAttName) return true
            if (parent is LuaFuncName) return true
            if (parent is LuaNameList) {
                val owner = parent.parent
                if (owner is LuaParList || owner is LuaGenericForStatement) return true
            }
            return false
        }
    }
}
