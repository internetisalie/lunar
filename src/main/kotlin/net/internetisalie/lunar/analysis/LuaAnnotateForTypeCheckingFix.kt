package net.internetisalie.lunar.analysis

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaStatement

/**
 * Offers to declare the type the engine could only guess at (BUG-419).
 *
 * The conflict this hangs off is not a claim that the code is wrong — it is the engine noticing that
 * two of its own inferences disagree. The only thing that can settle it is the user saying what they
 * meant, so the whole intention is: scaffold the annotation, at the statement, with the type the
 * engine inferred pre-filled. Accepting it turns a guess into a contract, after which a genuine
 * mismatch *is* reported as an error.
 */
class LuaAnnotateForTypeCheckingFix(
    private val inferredType: String?,
) : IntentionAction {
    override fun getText(): String = "Annotate to enable type checking"

    override fun getFamilyName(): String = "Lua type annotations"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ): Boolean = file != null && editor != null && enclosingStatement(file, editor) != null

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ) {
        if (file == null || editor == null) return
        val statement = enclosingStatement(file, editor) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val lineStart = document.getLineStartOffset(document.getLineNumber(statement.textRange.startOffset))
        val indent =
            document
                .getText(TextRange(lineStart, statement.textRange.startOffset))
                .takeWhile { it == ' ' || it == '\t' }

        // `any` when the engine has nothing better: the point is the scaffold and the caret, not a
        // guess dressed up as a recommendation.
        document.insertString(lineStart, "$indent---@type ${inferredType ?: "any"}\n")
    }

    private fun enclosingStatement(
        file: PsiFile,
        editor: Editor,
    ): LuaStatement? =
        PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), LuaStatement::class.java)
}
