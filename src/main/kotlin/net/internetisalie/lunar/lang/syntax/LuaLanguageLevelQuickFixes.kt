package net.internetisalie.lunar.lang.syntax

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.LuaBinOpExpr
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaGotoStatement
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Quick fix that upgrades the project's configured language level to support the used feature.
 */
class UpgradeLanguageLevelFix(
    private val requiredLevel: LuaLanguageLevel
) : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Upgrade project to $requiredLevel"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val settings = LuaProjectSettings.getInstance(project)
        settings.state.languageLevel = requiredLevel
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that removes a goto statement.
 */
class RemoveGotoFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Remove goto statement"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        PsiTreeUtil.getParentOfType(element, LuaGotoStatement::class.java, false)?.delete()
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that removes a label.
 */
class RemoveLabelFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Remove label"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        PsiTreeUtil.getParentOfType(element, LuaLabel::class.java, false)?.delete()
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick fix that replaces integer division with regular division and floor.
 */
class ReplaceIntegerDivisionFix : BaseIntentionAction() {

    override fun getFamilyName(): String = "Lua Language Level"

    override fun getText(): String = "Replace // with / and math.floor()"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val binOpExpr = PsiTreeUtil.getParentOfType(element, LuaBinOpExpr::class.java, false) ?: return
        if (binOpExpr.binOp.firstChild?.elementType != LuaElementTypes.INTDIV) return
        val leftOperand = binOpExpr.left
        val rightOperand = binOpExpr.right ?: return
        val floorExpr = "math.floor(${leftOperand.text} / ${rightOperand.text})"
        val replacement = LuaElementFactory.createExpression(project, floorExpr) ?: return
        binOpExpr.replace(replacement)
    }

    override fun startInWriteAction(): Boolean = true
}
