package net.internetisalie.lunar.analysis.redis

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.analysis.inspections.LuaInspectionSuppression
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVisitor
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Validates `redis.call`/`redis.pcall` command-name string literals against the bundled
 * command spec (design §2.4, §3.4, §3.5, §3.9).
 *
 * Three checks per call site (at most one problem per unknown/arity pair, then independently
 * the determinism check):
 *  1. **Unknown command** (WARNING) — did-you-mean rename quick fix via §3.5.
 *  2. **Below-minimum arity** (WARNING) — only when the command is known (unknown short-circuits).
 *  3. **Nondeterministic before write** (WARNING, Redis 5/6 only) — §3.9; independent of 1 and 2.
 *
 * Dynamic (non-literal) first arguments are never flagged (TC-UNK-2).
 * No-ops when the project target is not Redis or Valkey.
 */
class LuaRedisCommandInspection : LocalInspectionTool() {

    override fun getShortName(): String = "LuaRedisCommand"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            override fun visitFuncCall(o: LuaFuncCall) {
                val site = RedisCallSiteMatcher.match(o) ?: return
                if (site.member != "call" && site.member != "pcall") return

                val name = site.commandName ?: return // dynamic → TC-UNK-2
                val literal = site.nameLiteral ?: return // bound explicitly — no !!

                val target = LuaProjectSettings.getInstance(o.project).state.getTarget()
                if (!isRedisOrValkey(target)) return

                val spec = RedisCommandSpecService.getInstance().specFor(target)
                if (spec === RedisCommandSpec.EMPTY) return

                val info = spec.lookup(name)
                val ctx = CheckContext(site, spec, holder, target, info)
                if (info == null) {
                    checkUnknown(ctx, name, literal)
                } else {
                    checkArity(ctx, name)
                    checkDeterminism(ctx, name, literal)
                }
            }
        }

    // -------------------------------------------------------------------------
    // §3.4 step 5a — unknown command: suppression + did-you-mean quick fixes
    // -------------------------------------------------------------------------
    private fun checkUnknown(ctx: CheckContext, name: String, literal: LuaTerminalExpr) {
        val rootRef = rootNameRefOf(ctx.site) ?: return
        if (LuaInspectionSuppression.isSuppressed(rootRef, name, UNKNOWN_DIAGNOSTIC_ID)) return
        val fixes = didYouMean(name, ctx.spec.names())
            .map { LuaRedisRenameCommandQuickFix(it) }
            .toTypedArray()
        ctx.holder.registerProblem(
            literal,
            "Unknown Redis command '$name'",
            ProblemHighlightType.WARNING,
            *fixes,
        )
    }

    // -------------------------------------------------------------------------
    // §3.4 step 5b — arity: minArgs counts the command token; subtract 1 for display
    // -------------------------------------------------------------------------
    private fun checkArity(ctx: CheckContext, name: String) {
        val info = ctx.info ?: return
        val minArgs = if (info.arity < 0) -info.arity else info.arity
        if (ctx.site.argCount >= minArgs) return
        val argListAnchor = ctx.site.funcCall.nameAndArgsList.firstOrNull() ?: return
        val found = ctx.site.argCount - 1
        val expected = minArgs - 1
        ctx.holder.registerProblem(
            argListAnchor,
            "Redis command '$name' expects at least $expected argument(s), found $found",
            ProblemHighlightType.WARNING,
        )
    }

    // -------------------------------------------------------------------------
    // §3.9 — determinism: Redis 5/6 only; requires the file-level site cache
    // -------------------------------------------------------------------------
    private fun checkDeterminism(ctx: CheckContext, name: String, literal: LuaTerminalExpr) {
        if (ctx.target.version.label != "5" && ctx.target.version.label != "6") return // TC-DET-3
        if (ctx.info == null || "nondeterministic" !in ctx.info.flags) return

        val file = literal.containingFile ?: return
        val fileInfo = fileSiteInfo(file, ctx.spec)

        val myOffset = ctx.site.funcCall.textOffset
        if (fileInfo.guardOffsets.any { it < myOffset }) return // TC-DET-2
        if (!fileInfo.writeOffsets.any { it > myOffset }) return // TC-DET-4

        ctx.holder.registerProblem(
            literal,
            "Nondeterministic command '$name' called before a write under verbatim replication " +
                "(Redis <7); call redis.replicate_commands() first",
            ProblemHighlightType.WARNING,
        )
    }

    // -------------------------------------------------------------------------
    // File-level site cache for determinism (design §3.9): collected once per
    // PSI modification cycle via CachedValuesManager (mirrors LuaInspectionSuppression).
    // -------------------------------------------------------------------------
    private fun fileSiteInfo(file: PsiFile, spec: RedisCommandSpec): FileSiteInfo {
        return CachedValuesManager.getManager(file.project).getCachedValue(file) {
            CachedValueProvider.Result.create(
                buildFileSiteInfo(file, spec),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    private fun buildFileSiteInfo(file: PsiFile, spec: RedisCommandSpec): FileSiteInfo {
        val funcCalls = PsiTreeUtil.collectElementsOfType(file, LuaFuncCall::class.java)
        val guardOffsets = mutableListOf<Int>()
        val writeOffsets = mutableListOf<Int>()
        for (call in funcCalls) {
            val s = RedisCallSiteMatcher.match(call) ?: continue
            when {
                isGuardCall(s) -> guardOffsets += call.textOffset
                isWriteCall(s, spec) -> writeOffsets += call.textOffset
            }
        }
        return FileSiteInfo(guardOffsets, writeOffsets)
    }

    private fun isGuardCall(site: RedisCallSite): Boolean =
        site.namespace == "redis" && site.member == "replicate_commands"

    private fun isWriteCall(site: RedisCallSite, spec: RedisCommandSpec): Boolean {
        val cmdName = site.commandName ?: return false
        return spec.lookup(cmdName)?.flags?.contains("write") == true
    }

    // -------------------------------------------------------------------------
    // Root name-ref for suppression (design §3.4 step 5a): `redis` in `redis.call(…)`.
    // Reads only containingFile + textOffset — exactly what isSuppressed consumes.
    // -------------------------------------------------------------------------
    private fun rootNameRefOf(site: RedisCallSite): LuaNameRef? =
        site.funcCall.varOrExp.`var`?.nameRef

    companion object {
        private const val UNKNOWN_DIAGNOSTIC_ID = "redis-unknown-command"
    }
}

/**
 * Per-invocation context bundling the resolved call site, spec, holder, target, and looked-up
 * command info (design §3.4). Keeps individual check-method arg counts at ≤3 (engineering contract).
 * [info] is `null` when the command is unknown; check methods branch on it.
 */
private data class CheckContext(
    val site: RedisCallSite,
    val spec: RedisCommandSpec,
    val holder: ProblemsHolder,
    val target: Target,
    val info: RedisCommandInfo?,
)

/** Memoized offsets of guard and write calls within one file (design §3.9). */
private data class FileSiteInfo(
    val guardOffsets: List<Int>,
    val writeOffsets: List<Int>,
)

/** Returns `true` when [target] is a Redis or Valkey platform. */
private fun isRedisOrValkey(target: Target): Boolean =
    target.platform == LuaPlatform.REDIS || target.platform == LuaPlatform.VALKEY
