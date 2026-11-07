package net.internetisalie.lunar.corpus.audit

import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaNameAndArgs
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsCommentOwner
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag
import java.io.File

data class AuditScan(
    val sites: List<AuditSite>,
    val coverage: AuditCoverage,
)

/** Why a call yields no sites, or that it is eligible (design §3.2 steps 1–6). */
internal enum class CallVerdict {
    STRING_OR_TABLE_ARGS,
    UNRESOLVED_CALLEE,
    UNRESOLVED_METHOD,
    FORM_MISMATCH,
    NO_TAG,
    ARITY_GUARD,
    ELIGIBLE,
}

/** MAINT-54 design §3.2: every eligible injection site of one file, with the coverage counters. */
object AuditSiteFinder {
    private val LDOC_PARAM = Regex("""^\s*--(?!-)\s*@param\b""", RegexOption.MULTILINE)

    fun scan(
        file: LuaFile,
        checkoutDir: File,
    ): AuditScan {
        val scope = ScanScope(file, checkoutDir)
        val sites = mutableListOf<AuditSite>()
        val verdicts =
            PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java).map { call ->
                scanCall(call, scope, sites)
            }
        sites += AuditBodySites.returnSites(scope)
        val fields = AuditBodySites.fieldSites(scope)
        sites += fields.sites
        return AuditScan(sites, coverage(verdicts, fields.untagged, LDOC_PARAM.findAll(file.text).count()))
    }

    /** The `@field` tag behind [member]: the member's own source, or its host declaration's comment. */
    internal fun fieldTag(
        member: LuaTypeMember,
        fieldName: String,
    ): LuaCatsFieldTag? {
        val source = member.sourceElement
        if (source is LuaCatsFieldTag) return source
        return (source as? LuaCatsCommentOwner)
            ?.catsComment
            ?.fieldTagList
            ?.firstOrNull { it.fieldDescriptor.argName?.text == fieldName }
    }

    private fun coverage(
        verdicts: List<CallVerdict>,
        fieldUntagged: Int,
        ldocParam: Int,
    ): AuditCoverage {
        val counts = verdicts.groupingBy { it }.eachCount()

        fun count(verdict: CallVerdict): Int = counts[verdict] ?: 0
        return AuditCoverage(
            calls = verdicts.size,
            stringOrTableArgs = count(CallVerdict.STRING_OR_TABLE_ARGS),
            unresolvedCallee = count(CallVerdict.UNRESOLVED_CALLEE),
            unresolvedMethod = count(CallVerdict.UNRESOLVED_METHOD),
            formMismatch = count(CallVerdict.FORM_MISMATCH),
            noTag = count(CallVerdict.NO_TAG),
            arityGuard = count(CallVerdict.ARITY_GUARD),
            eligible = count(CallVerdict.ELIGIBLE),
            fieldUntagged = fieldUntagged,
            ldocParam = ldocParam,
        )
    }

    /** Only the first `nameAndArgs` of a chain is a site: `visitFuncCall` models only that one. */
    private fun scanCall(
        call: LuaFuncCall,
        scope: ScanScope,
        sites: MutableList<AuditSite>,
    ): CallVerdict {
        val nameAndArgs = call.nameAndArgsList.firstOrNull() ?: return CallVerdict.STRING_OR_TABLE_ARGS
        if (!nameAndArgs.args.text.startsWith("(")) return CallVerdict.STRING_OR_TABLE_ARGS
        val isMethodCall = nameAndArgs.methodExpr != null
        val callee =
            AuditCallResolution.resolve(call, nameAndArgs)
                ?: return if (isMethodCall) CallVerdict.UNRESOLVED_METHOD else CallVerdict.UNRESOLVED_CALLEE
        if (isMethodCall != (callee.form == CalleeForm.METHOD)) return CallVerdict.FORM_MISMATCH
        val comment = callee.decl.catsComment
        if (comment == null || comment.paramTagList.isEmpty()) return CallVerdict.NO_TAG
        val args =
            nameAndArgs.args.exprList
                ?.exprList
                .orEmpty()
        val params =
            callee.parList
                ?.nameList
                ?.nameRefList
                .orEmpty()
        if (isVariadic(callee) || args.size != params.size) return CallVerdict.ARITY_GUARD
        sites += callSites(EligibleCall(call, callee, scope), args, params)
        return CallVerdict.ELIGIBLE
    }

    private fun callSites(
        eligible: EligibleCall,
        args: List<LuaExpr>,
        params: List<LuaNameRef>,
    ): List<AuditSite> {
        val base = eligible.base()
        val comment = eligible.comment()
        val sameFile = base.path == CalleePath.SAME_FILE
        val argSites =
            params.indices.flatMap { index ->
                val tag = tagFor(comment, params[index]) ?: return@flatMap emptyList()
                val kind = declaredKind(tag, comment) ?: return@flatMap emptyList()
                val paramRange = if (sameFile) params[index].textRange else null
                AuditShapes.argSites(base, ArgSlot(args[index], kind, paramRange))
            }
        val lastTag = params.lastOrNull()?.let { tagFor(comment, it) }
        val lastRequired = lastTag != null && declaredKind(lastTag, comment)?.optional == false
        val callArgs =
            CallArgs(
                args,
                eligible.nameAndArgs.args.textRange,
                lastRequired,
                eligible.callee.parList
                    ?.text
                    .orEmpty(),
            )
        return argSites + AuditShapes.aritySites(base, callArgs)
    }

    private fun isVariadic(callee: Callee): Boolean =
        callee.parList
            ?.text
            .orEmpty()
            .contains("...")

    private fun tagFor(
        comment: LuaCatsComment,
        param: LuaNameRef,
    ): LuaCatsParamTag? = comment.paramTagList.firstOrNull { it.argName?.text == param.text }

    /** The tag's kind, optional also when the tag itself marks the parameter optional. */
    private fun declaredKind(
        tag: LuaCatsParamTag,
        comment: LuaCatsComment,
    ): DeclaredKind? {
        val kind = AuditKinds.classify(tag.argType.text, comment) ?: return null
        return if (LuaCatsDeclarations.isOptionalParam(tag)) kind.copy(optional = true) else kind
    }
}

/** A call that passed steps 1–6, with what its sites share. */
internal class EligibleCall(
    private val call: LuaFuncCall,
    val callee: Callee,
    private val scope: ScanScope,
) {
    val nameAndArgs: LuaNameAndArgs get() = call.nameAndArgsList.first()

    fun comment(): LuaCatsComment = requireNotNull(callee.decl.catsComment) { "eligible call without a comment" }

    fun base(): CallBase {
        val location = AuditPaths.locate(callee.decl, scope)
        return CallBase(
            path = location.path,
            form = callee.form,
            file = scope.relativePath,
            site = call.textRange,
            declFile = location.declFile,
            declRange = AuditPaths.declRange(location, callee.decl),
        )
    }
}
