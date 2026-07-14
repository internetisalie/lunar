package net.internetisalie.lunar.analysis.redis

import com.intellij.codeHighlighting.HighlightDisplayLevel
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
import net.internetisalie.lunar.lang.psi.LuaVar
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Flags references to APIs not available in the Redis/Valkey script sandbox (design §2.7, §3.7).
 *
 * Under Redis/Valkey targets: `io.*`, restricted `os.*` (only `os.time`/`os.clock` are
 * allowed per the target's `os.lua` stub), `require`, `dofile`, `loadfile`, and `print`
 * usages are flagged at WARNING level (RISK-R07; escalates to ERROR after live validation).
 *
 * The allowlist is derived from the target's bundled stub roots via [RedisSandboxAllowlist]
 * (§3.7 single source of truth): any stub file present = that root is accessible;
 * `os` members are further gated on what `os.lua` declares.
 *
 * Inspection is a no-op under non-Redis/Valkey targets (TC-SBX-3).
 * Suppressible via the normal `LuaInspectionSuppression` mechanism.
 */
class LuaRedisSandboxInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaRedisSandbox"

    override fun getGroupDisplayName(): String = "Lua"

    override fun getDisplayName(): String = "Redis script sandbox"

    override fun isEnabledByDefault(): Boolean = true

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitNameRef(o: LuaNameRef) {
                if (isDeclaration(o)) return
                val rootName = rootNameOf(o) ?: return
                val project = o.project
                val target = LuaProjectSettings.getInstance(project).state.getTarget()
                if (target.platform != LuaPlatform.REDIS && target.platform != LuaPlatform.VALKEY) return

                val allowlist = RedisSandboxAllowlist.forTarget(project, target)
                val problem = blockedMessage(rootName, o, allowlist) ?: return
                if (LuaInspectionSuppression.isSuppressed(o, rootName, DIAGNOSTIC_ID)) return
                holder.registerProblem(o, problem, ProblemHighlightType.WARNING)
            }
        }

    companion object {
        private const val DIAGNOSTIC_ID = "redis-sandbox"

        /**
         * Returns the blocked-API problem message for [ref] if it accesses a forbidden name,
         * or `null` when the access is allowed.
         *
         * For `os.*` member accesses: blocked only when the specific member is not in the
         * allowlist (TC-SBX-2: `os.time`/`os.clock` are allowed; `os.getenv` is blocked).
         * For all other blocked roots: blocked unconditionally (TC-SBX-1).
         */
        private fun blockedMessage(
            rootName: String,
            ref: LuaNameRef,
            allowlist: RedisSandboxAllowlist.Allowlist,
        ): String? {
            if (rootName == "os") {
                val member = osMemberOf(ref)
                return if (member != null && allowlist.isAllowedOsMember(member)) {
                    null
                } else if (member != null) {
                    "'os.$member' is not available in the Redis script sandbox"
                } else {
                    "'os' is not available in the Redis script sandbox"
                }
            }
            if (allowlist.isBlockedRoot(rootName)) {
                return "'$rootName' is not available in the Redis script sandbox"
            }
            return null
        }

        /**
         * Returns the root name for [ref] when it is the root name-ref of a [LuaVar] whose
         * name is a potential stdlib/blocked name, or `null` when [ref] is not a root name-ref.
         *
         * A root name-ref is one that is a direct `nameRef` child of a `LuaVar`; member-segment
         * name-refs (the `.read` in `io.read`) live in a `varSuffix/indexExpr` and are not
         * the `LuaVar.nameRef`.
         */
        private fun rootNameOf(ref: LuaNameRef): String? {
            val parent = ref.parent as? LuaVar ?: return null
            if (parent.nameRef != ref) return null
            return ref.identifier.text
        }

        /**
         * Returns the first dotted member name accessed on an `os.*` ref (e.g. `time` for
         * `os.time()`), or `null` when no member suffix is present (bare `os` usage).
         */
        private fun osMemberOf(ref: LuaNameRef): String? {
            val luaVar = ref.parent as? LuaVar ?: return null
            val suffix = luaVar.varSuffixList.firstOrNull() ?: return null
            return suffix.indexExpr.nameRef?.identifier?.text
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
