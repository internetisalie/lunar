package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaIfStatement

class LuaInvertIfIntention : BaseIntentionAction() {
    override fun getFamilyName(): String = "Lua"
    override fun getText(): String = "Invert 'if' statement"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is LuaFile) return false
        val ifStmt = ifStatementAt(file, editor) ?: return false
        if (ifStmt.exprList.size != 1) return false
        if (ifStmt.getBlockList().size != 2) return false
        if (ifStmt.node.findChildByType(LuaElementTypes.ELSE) == null) return false
        if (ifStmt.node.findChildByType(LuaElementTypes.ELSEIF) != null) return false
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val ifStmt = ifStatementAt(file, editor) ?: return
        val condition = ifStmt.exprList.firstOrNull() ?: return
        val blocks = ifStmt.getBlockList()
        if (blocks.size != 2) return

        val newCondText = LuaConditionInverter.invertedText(condition)
        val thenBody = blocks[0].text
        val elseBody = blocks[1].text
        val replacementText = "if $newCondText then\n$elseBody\nelse\n$thenBody\nend"

        WriteCommandAction.runWriteCommandAction(project) {
            val dummyFile = LuaElementFactory.createFile(project, replacementText)
            val newIf = PsiTreeUtil.findChildOfType(dummyFile, LuaIfStatement::class.java)
                ?: return@runWriteCommandAction
            val replaced = ifStmt.replace(newIf)
            CodeStyleManager.getInstance(project).reformat(replaced)
        }
    }

    private fun ifStatementAt(file: PsiFile, editor: Editor): LuaIfStatement? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, LuaIfStatement::class.java)
    }
}
