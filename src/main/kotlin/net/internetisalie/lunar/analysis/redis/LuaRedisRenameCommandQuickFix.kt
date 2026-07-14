package net.internetisalie.lunar.analysis.redis

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr

/**
 * Renames an unknown Redis command literal to the suggested valid command
 * (design §2.5, §3.5 did-you-mean). Preserves the original quote style.
 *
 * @param suggestion The valid command name to substitute (upper-cased, unquoted).
 */
class LuaRedisRenameCommandQuickFix(private val suggestion: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Change to '$suggestion'"

    /** Replaces the string-literal command name with [suggestion] under a write action. */
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val literal = element as? LuaTerminalExpr
            ?: PsiTreeUtil.getParentOfType(element, LuaTerminalExpr::class.java)
            ?: return
        val originalText = literal.text
        val quote = originalText.firstOrNull()?.takeIf { it == '"' || it == '\'' } ?: '"'
        val replacement = "$quote$suggestion$quote"
        WriteCommandAction.runWriteCommandAction(project, "Rename Redis command", null, {
            val tempFile = LuaElementFactory.createFile(project, "local _ = $replacement")
            val newLiteral = PsiTreeUtil.findChildOfType(tempFile, LuaTerminalExpr::class.java)
                ?: return@runWriteCommandAction
            literal.replace(newLiteral)
        })
    }
}

/**
 * Classic two-row Levenshtein edit-distance DP (design §3.5).
 *
 * @return the minimum edit distance between [a] and [b].
 */
internal fun levenshtein(a: String, b: String): Int {
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            curr[j] = if (a[i - 1] == b[j - 1]) {
                prev[j - 1]
            } else {
                minOf(prev[j - 1], prev[j], curr[j - 1]) + 1
            }
        }
        prev.indices.forEach { prev[it] = curr[it] }
    }
    return prev[b.length]
}

/**
 * Computes did-you-mean suggestions (design §3.5): candidates from [allNames] with
 * Levenshtein distance ≤ 2 from [unknown], sorted by distance then lexicographically,
 * returning at most 3. [unknown] must already be upper-cased.
 */
internal fun didYouMean(unknown: String, allNames: Set<String>): List<String> =
    allNames
        .mapNotNull { candidate ->
            val dist = levenshtein(unknown, candidate)
            if (dist <= 2) Pair(dist, candidate) else null
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(3)
        .map { it.second }
