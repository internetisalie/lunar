package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import net.internetisalie.lunar.lang.editor.LuaBlockStructure
import net.internetisalie.lunar.lang.editor.LuaIfBranch
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaIfStatement

/**
 * "Remove 'else' branch": drop the trailing `else`/`elseif` branch of an `if`, keeping the earlier bodies.
 * Implements EDITOR-06-02. Deletes the branch structurally — from the whitespace before its keyword through
 * its body block — so the retained branches keep their exact original indentation (no reformat, no reindent
 * drift). The removed branch body is reported for the preview highlight. Design §2.5 / §3.3.
 */
class LuaElseBranchRemover : LuaUnwrapper("Remove 'else' branch") {

    override fun isApplicableTo(e: PsiElement): Boolean =
        e is LuaIfStatement && LuaBlockStructure.hasElseOrElseIf(e)

    override fun doUnwrap(element: PsiElement, context: Context) {
        val ifStmt = element as? LuaIfStatement ?: return
        val branches = LuaBlockStructure.ifBranches(ifStmt)
        if (branches.size < 2) return
        if (!context.isEffective) {
            context.addElementToExtract(branches.last().body)
            return
        }
        deleteLastBranch(ifStmt, branches.last())
    }

    private fun deleteLastBranch(ifStmt: LuaIfStatement, branch: LuaIfBranch) {
        val keyword = ifStmt.node.getChildren(null).lastOrNull {
            it.elementType == LuaElementTypes.ELSEIF || it.elementType == LuaElementTypes.ELSE
        }?.psi ?: return
        val from = keyword.prevSibling as? PsiWhiteSpace ?: keyword
        ifStmt.deleteChildRange(from, branch.body)
    }
}
