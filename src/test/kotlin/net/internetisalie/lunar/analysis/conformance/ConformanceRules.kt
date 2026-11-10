package net.internetisalie.lunar.analysis.conformance

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.corpus.audit.AuditKinds
import net.internetisalie.lunar.corpus.audit.Kind
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsParamTag

/** MAINT-55 design §2.3 (the rules) and §3.3 (resolving `arg` to a MAINT-54 shape). */
object ConformanceRules {
    /** The header rule a parameter-mismatch case carries until [argRule] resolves it. */
    const val ARG = "arg"

    /** The rules that are MAINT-54 shape names, so MAINT-56's `rule-weights.json` applies unmapped. */
    val SHAPE_RULES: Set<String> =
        setOf(
            "arg.primitive",
            "arg.optional",
            "arg.table",
            "arg.record",
            "arg.class",
            "arg.array",
            "arg.union",
            "arg.function",
            "arg.generic",
            "arity.tooMany",
            "arity.tooFew",
            "return.mismatch",
            "field.assign",
        )

    val RULES: Set<String> =
        SHAPE_RULES +
            setOf(
                "arg.other",
                "assign.local",
                "field.missing",
                "field.undefined",
                "field.inject",
                "nil.check",
                "cast.local",
                "cast.mismatch",
                "return.count",
                "return.missing",
            )

    fun argRule(file: PsiFile): String =
        runReadAction {
            val tag = PsiTreeUtil.findChildrenOfType(file, LuaCatsParamTag::class.java).firstOrNull()
            val comment = tag?.let { PsiTreeUtil.getParentOfType(it, LuaCatsComment::class.java) }
            if (tag == null || comment == null) return@runReadAction "arg.other"
            val declared = AuditKinds.classify(tag.argType.text, comment) ?: return@runReadAction "arg.other"
            val optional = declared.optional || LuaCatsDeclarations.isOptionalParam(tag)
            if (declared.kind == Kind.PRIMITIVE && optional) "arg.optional" else "arg." + declared.kind.name.lowercase()
        }
}
