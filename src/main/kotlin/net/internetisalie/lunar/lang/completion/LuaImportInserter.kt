package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaExprStatement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaStatement

/** The three positions an auto-import may be inserted at; keeps `when` exhaustive. */
sealed class InsertionAnchor
data class AfterLastRequire(val element: PsiElement) : InsertionAnchor()
data class AfterHeaderComments(val element: PsiElement) : InsertionAnchor()
data object AtFileStart : InsertionAnchor()

/**
 * Finds the insertion anchor and mutates the document to add a `require` statement
 * (COMP-03-AC-01/05). Grouped after existing leading requires (TC-03-03), else after the
 * file header comments/shebang (TC-03-04), else at the very top. No-ops on a read-only
 * document. Must be called inside a WriteCommandAction on the EDT.
 */
object LuaImportInserter {

    fun insert(editor: Editor, file: LuaFile, importStatement: String) {
        val document = editor.document
        val documentManager = PsiDocumentManager.getInstance(file.project)
        // Reflect any text the completion framework already inserted before our handler ran.
        documentManager.commitDocument(document)

        if (!document.isWritable) {
            log.warn("Auto-import skipped: read-only document ${file.name}")
            return
        }

        val (offset, text) = positionFor(resolveAnchor(file), importStatement)
        document.insertString(offset, text)
        documentManager.commitDocument(document)
    }

    private fun positionFor(anchor: InsertionAnchor, statement: String): Pair<Int, String> = when (anchor) {
        is AfterLastRequire -> anchor.element.textRange.endOffset to "\n$statement"
        is AfterHeaderComments -> anchor.element.textRange.endOffset to "\n\n$statement"
        AtFileStart -> 0 to "$statement\n"
    }

    private fun resolveAnchor(file: LuaFile): InsertionAnchor {
        val statements = file.getBlockList().firstOrNull()?.statementList ?: emptyList()

        val leadingRequires = statements.takeWhile { it.isRequireStatement() }
        if (leadingRequires.isNotEmpty()) {
            return AfterLastRequire(leadingRequires.last())
        }

        val lastHeader = lastHeaderElement(file)
        return if (lastHeader != null) AfterHeaderComments(lastHeader) else AtFileStart
    }

    /**
     * The last leading comment/shebang leaf before the first real code, walking the leaf
     * stream (comments may nest inside the root block, so file-child iteration is not enough).
     * Returns null when the file has no header (treated as empty -> insert at start).
     */
    private fun lastHeaderElement(file: LuaFile): PsiElement? {
        var candidate: PsiElement? = null
        var leaf = PsiTreeUtil.getDeepestFirst(file)
        while (leaf !== file && leaf.isHeaderElement()) {
            if (leaf !is PsiWhiteSpace) candidate = leaf
            leaf = PsiTreeUtil.nextLeaf(leaf) ?: break
        }
        return candidate
    }

    private fun PsiElement.isHeaderElement(): Boolean =
        this is PsiComment || this is PsiWhiteSpace || elementType == LuaElementTypes.SHEBANG

    /**
     * True when a statement's value is a `require(...)` call, covering both the
     * `local x = require(...)` (local-var-decl) and bare `require(...)` (expr) forms.
     */
    private fun LuaStatement.isRequireStatement(): Boolean =
        valueExpressions().any { it.isRequireCall() }

    private fun LuaStatement.valueExpressions(): List<LuaExpr> = when (this) {
        is LuaExprStatement -> listOfNotNull(expr)
        is LuaLocalVarDecl -> exprList?.exprList ?: emptyList()
        is LuaAssignmentStatement -> exprList.exprList
        else -> emptyList()
    }

    private fun LuaExpr.isRequireCall(): Boolean {
        val call = this as? LuaFuncCall ?: return false
        return call.varOrExp?.`var`?.nameRef?.identifier?.text == "require"
    }

    private val log = logger<LuaImportInserter>()
}
