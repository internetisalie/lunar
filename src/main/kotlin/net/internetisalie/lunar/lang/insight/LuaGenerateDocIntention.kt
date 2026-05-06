package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.*

class LuaGenerateDocIntention : BaseIntentionAction() {
    override fun getFamilyName(): String = "Lua"
    override fun getText(): String = "Generate LuaCATS documentation"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is LuaFile) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java) ?: return false

        // Only if it doesn't already have a doc comment (or it's empty)
        if (!LuaDocGenerator.isDocCommentEmpty(owner.catsComment)) return false

        return owner is LuaFuncDecl || owner is LuaLocalFuncDecl || isClassTable(owner)
    }

    private fun isClassTable(owner: LuaCommentOwner): Boolean {
        if (owner !is LuaLocalVarDecl) return false
        val names = owner.attNameList
        if (names.size != 1) return false
        val exprList = owner.exprList ?: return false
        if (exprList.exprList.size != 1) return false
        return exprList.exprList[0] is LuaTableConstructor
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java) ?: return

        val document = editor.document
        val offset = owner.textRange.startOffset
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        val indent = getLineIndent(document, lineStart)

        val template = LuaDocGenerator.createTemplate(project, owner, indent) ?: return

        editor.caretModel.moveToOffset(lineStart)
        TemplateManager.getInstance(project).startTemplate(editor, template)
    }

    private fun getLineIndent(document: com.intellij.openapi.editor.Document, lineStart: Int): String {
        val text = document.charsSequence
        var i = lineStart
        while (i < text.length && text[i].isWhitespace() && text[i] != '\n') {
            i++
        }
        return text.subSequence(lineStart, i).toString()
    }
}
