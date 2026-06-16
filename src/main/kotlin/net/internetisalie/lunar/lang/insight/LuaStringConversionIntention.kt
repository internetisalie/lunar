package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.syntax.LuaStringForm
import net.internetisalie.lunar.lang.syntax.encodeLuaString
import net.internetisalie.lunar.lang.syntax.extractLuaString
import net.internetisalie.lunar.lang.syntax.getLuaStringDelimiterLength

class LuaStringConversionIntention : BaseIntentionAction() {
    private var actionText: String = "Convert string quotes"

    override fun getFamilyName(): String = "Convert string quotes"
    override fun getText(): String = actionText

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is LuaFile) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val leaf = stringLeafFor(element) ?: return false
        val form = currentForm(leaf.text)
        if (form == LuaStringForm.UNKNOWN) return false
        actionText = textForNext(form)
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val leaf = stringLeafFor(element) ?: return
        val target = nextForm(currentForm(leaf.text))
        if (target == LuaStringForm.UNKNOWN) return
        val newText = encodeLuaString(extractLuaString(leaf.text), target)
        editor.document.replaceString(leaf.textRange.startOffset, leaf.textRange.endOffset, newText)
    }

    private fun stringLeafFor(element: PsiElement): PsiElement? {
        if (element.node.elementType == LuaElementTypes.STRING) return element
        return PsiTreeUtil.getParentOfType(element, LuaTerminalExpr::class.java)?.string
    }

    private fun currentForm(raw: String): LuaStringForm =
        when {
            raw.startsWith("'") -> LuaStringForm.SINGLE
            raw.startsWith("\"") -> LuaStringForm.DOUBLE
            raw.startsWith("[") && getLuaStringDelimiterLength(raw) >= 2 -> LuaStringForm.LONG
            else -> LuaStringForm.UNKNOWN
        }

    private fun nextForm(form: LuaStringForm): LuaStringForm =
        when (form) {
            LuaStringForm.SINGLE -> LuaStringForm.DOUBLE
            LuaStringForm.DOUBLE -> LuaStringForm.LONG
            LuaStringForm.LONG -> LuaStringForm.SINGLE
            LuaStringForm.UNKNOWN -> LuaStringForm.UNKNOWN
        }

    private fun textForNext(form: LuaStringForm): String =
        when (nextForm(form)) {
            LuaStringForm.SINGLE -> "Convert to single-quoted string"
            LuaStringForm.DOUBLE -> "Convert to double-quoted string"
            LuaStringForm.LONG -> "Convert to long-bracket string"
            LuaStringForm.UNKNOWN -> "Convert string quotes"
        }
}
